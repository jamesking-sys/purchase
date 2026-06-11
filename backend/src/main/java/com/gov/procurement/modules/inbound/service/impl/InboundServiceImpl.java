package com.gov.procurement.modules.inbound.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gov.procurement.common.BizException;
import com.gov.procurement.common.ErrorCode;
import com.gov.procurement.modules.inbound.domain.InboundItem;
import com.gov.procurement.modules.inbound.domain.InboundOrder;
import com.gov.procurement.modules.inbound.dto.CreateInboundCmd;
import com.gov.procurement.modules.inbound.dto.CreateInboundVO;
import com.gov.procurement.modules.inbound.dto.CreateInboundVO.CreateInboundLineVO;
import com.gov.procurement.modules.inbound.dto.InboundItemReq;
import com.gov.procurement.modules.inbound.dto.InboundOrderVO;
import com.gov.procurement.modules.inbound.dto.InboundOrderVO.InboundLineVO;
import com.gov.procurement.modules.inbound.dto.PendingItemVO;
import com.gov.procurement.modules.inbound.mapper.InboundItemMapper;
import com.gov.procurement.modules.inbound.mapper.InboundOrderMapper;
import com.gov.procurement.modules.inbound.service.InboundService;
import com.gov.procurement.modules.org.domain.ProjectGroup;
import com.gov.procurement.modules.org.mapper.ProjectGroupMapper;
import com.gov.procurement.modules.purchase.domain.PurchaseItem;
import com.gov.procurement.modules.purchase.domain.PurchaseOrder;
import com.gov.procurement.modules.purchase.mapper.PurchaseItemMapper;
import com.gov.procurement.modules.purchase.mapper.PurchaseOrderMapper;
import com.gov.procurement.modules.stock.service.StockService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.gov.procurement.modules.purchase.constant.PurchaseConst.ST_EXECUTING;
import static com.gov.procurement.modules.purchase.constant.PurchaseConst.ST_INBOUNDED;

/**
 * 多次验收入库实现。单事务：FOR UPDATE 锁采购明细串行化并发入库 → 不超收校验 → 写入库单/明细 + 库存记账 + 累加已收，
 * 全部明细满量则采购单转 inbounded。任一步失败整体回滚（不留半张入库单、不虚增库存）。
 */
@Service
public class InboundServiceImpl implements InboundService {

    private static final Logger log = LoggerFactory.getLogger(InboundServiceImpl.class);

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final InboundOrderMapper inboundOrderMapper;
    private final InboundItemMapper inboundItemMapper;
    private final PurchaseOrderMapper purchaseOrderMapper;
    private final PurchaseItemMapper purchaseItemMapper;
    private final ProjectGroupMapper projectGroupMapper;
    private final StockService stockService;

    public InboundServiceImpl(InboundOrderMapper inboundOrderMapper, InboundItemMapper inboundItemMapper,
                              PurchaseOrderMapper purchaseOrderMapper, PurchaseItemMapper purchaseItemMapper,
                              ProjectGroupMapper projectGroupMapper, StockService stockService) {
        this.inboundOrderMapper = inboundOrderMapper;
        this.inboundItemMapper = inboundItemMapper;
        this.purchaseOrderMapper = purchaseOrderMapper;
        this.purchaseItemMapper = purchaseItemMapper;
        this.projectGroupMapper = projectGroupMapper;
        this.stockService = stockService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CreateInboundVO createInbound(CreateInboundCmd cmd) {
        long userId = StpUtil.getLoginIdAsLong();

        PurchaseOrder po = purchaseOrderMapper.selectById(cmd.purchaseOrderId());
        if (po == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "采购单不存在");
        }
        if (!ST_EXECUTING.equals(po.getStatus())) {
            throw new BizException(ErrorCode.STATE_CONFLICT, "采购单非执行中，不可入库");
        }
        Long pgId = cmd.projectGroupId() != null ? cmd.projectGroupId() : po.getProjectGroupId();
        ProjectGroup pg = projectGroupMapper.selectById(pgId);
        if (pg == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "项目组不存在");
        }
        Long deptId = pg.getDepartmentId();

        // 锁定本采购单全部明细：串行化同一采购单/明细的并发入库（AC-7），读到的 received_qty 为权威当前值。
        List<PurchaseItem> locked = purchaseItemMapper.selectList(new LambdaQueryWrapper<PurchaseItem>()
                .eq(PurchaseItem::getPurchaseOrderId, po.getId())
                .orderByAsc(PurchaseItem::getId)
                .last("FOR UPDATE"));
        Map<Long, PurchaseItem> byId = locked.stream()
                .collect(Collectors.toMap(PurchaseItem::getId, Function.identity()));

        // 同请求同明细多行先合计，避免单行各自通过、合计超收（AC-3 / T-3b）。
        Map<Long, BigDecimal> reqTotal = new LinkedHashMap<>();
        for (InboundItemReq it : cmd.items()) {
            PurchaseItem pi = byId.get(it.purchaseItemId());
            if (pi == null) {
                throw new BizException(ErrorCode.NOT_FOUND, "采购明细不属于该采购单：" + it.purchaseItemId());
            }
            reqTotal.merge(it.purchaseItemId(), it.receivedQty(), BigDecimal::add);
        }
        for (Map.Entry<Long, BigDecimal> entry : reqTotal.entrySet()) {
            PurchaseItem pi = byId.get(entry.getKey());
            if (pi.getReceivedQty().add(entry.getValue()).compareTo(pi.getQty()) > 0) {
                throw new BizException(ErrorCode.OVER_RECEIVE, "累计超收：明细 " + entry.getKey());
            }
        }

        InboundOrder order = new InboundOrder();
        order.setPurchaseOrderId(po.getId());
        order.setReceivedBy(userId);
        inboundOrderMapper.insert(order);

        // 逐行：库存 upsert + 流水（M5）→ 入库明细 → 累加已收。
        Map<Long, Long> stockIdByItem = new HashMap<>();
        for (InboundItemReq it : cmd.items()) {
            PurchaseItem pi = byId.get(it.purchaseItemId());
            Long stockItemId = stockService.addStock(pi.getMaterialName(), pgId, deptId, it.receivedQty(), order.getId());
            stockIdByItem.put(it.purchaseItemId(), stockItemId);

            InboundItem ii = new InboundItem();
            ii.setInboundOrderId(order.getId());
            ii.setPurchaseItemId(it.purchaseItemId());
            ii.setStockItemId(stockItemId);
            ii.setReceivedQty(it.receivedQty());
            ii.setProjectGroupId(pgId);
            inboundItemMapper.insert(ii);

            purchaseItemMapper.addReceivedQty(it.purchaseItemId(), it.receivedQty());
        }

        // 全部明细满量 → 采购单转 inbounded（用锁内 before + 本次合计判定，避免再查）。
        boolean allReceived = byId.values().stream().allMatch(pi -> {
            BigDecimal after = pi.getReceivedQty().add(reqTotal.getOrDefault(pi.getId(), BigDecimal.ZERO));
            return after.compareTo(pi.getQty()) == 0;
        });
        String poStatus = po.getStatus();
        if (allReceived) {
            purchaseOrderMapper.update(null, new LambdaUpdateWrapper<PurchaseOrder>()
                    .eq(PurchaseOrder::getId, po.getId())
                    .set(PurchaseOrder::getStatus, ST_INBOUNDED));
            poStatus = ST_INBOUNDED;
        }

        List<CreateInboundLineVO> lines = reqTotal.entrySet().stream()
                .map(entry -> {
                    PurchaseItem pi = byId.get(entry.getKey());
                    BigDecimal total = pi.getReceivedQty().add(entry.getValue());
                    return new CreateInboundLineVO(entry.getKey(), total, stockIdByItem.get(entry.getKey()));
                })
                .toList();

        log.info("入库 inboundOrderId={}, poId={}, lines={}, poStatus={}",
                order.getId(), po.getId(), cmd.items().size(), poStatus);
        return new CreateInboundVO(order.getId(), poStatus, lines);
    }

    @Override
    public List<PendingItemVO> pendingItems(Long purchaseOrderId) {
        if (purchaseOrderMapper.selectById(purchaseOrderId) == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "采购单不存在");
        }
        return purchaseItemMapper.selectList(new LambdaQueryWrapper<PurchaseItem>()
                        .eq(PurchaseItem::getPurchaseOrderId, purchaseOrderId)
                        .orderByAsc(PurchaseItem::getId)).stream()
                .map(pi -> new PendingItemVO(pi.getId(), pi.getMaterialName(), pi.getQty(), pi.getReceivedQty(),
                        pi.getQty().subtract(pi.getReceivedQty())))
                .toList();
    }

    @Override
    public Page<InboundOrderVO> listByPurchaseOrder(Long purchaseOrderId, int pageNum, int size) {
        if (purchaseOrderMapper.selectById(purchaseOrderId) == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "采购单不存在");
        }
        int current = pageNum < 1 ? 1 : pageNum;
        int limit = (size < 1 || size > MAX_PAGE_SIZE) ? DEFAULT_PAGE_SIZE : size;

        Page<InboundOrder> result = inboundOrderMapper.selectPage(new Page<>(current, limit),
                new LambdaQueryWrapper<InboundOrder>()
                        .eq(InboundOrder::getPurchaseOrderId, purchaseOrderId)
                        .orderByDesc(InboundOrder::getId));

        List<InboundOrderVO> records = result.getRecords().stream().map(this::toOrderVO).toList();
        Page<InboundOrderVO> page = new Page<>(current, limit, result.getTotal());
        page.setRecords(records);
        return page;
    }

    private InboundOrderVO toOrderVO(InboundOrder order) {
        List<InboundLineVO> lines = inboundItemMapper.selectRowsByOrder(order.getId()).stream()
                .map(r -> new InboundLineVO(r.getPurchaseItemId(), r.getMaterialName(),
                        r.getReceivedQty(), r.getStockItemId()))
                .toList();
        return new InboundOrderVO(order.getId(), order.getPurchaseOrderId(), order.getReceivedBy(),
                order.getInboundAt(), lines);
    }
}
