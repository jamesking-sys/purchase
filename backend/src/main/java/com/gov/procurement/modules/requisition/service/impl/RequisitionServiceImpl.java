package com.gov.procurement.modules.requisition.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gov.procurement.common.BizException;
import com.gov.procurement.common.ErrorCode;
import com.gov.procurement.modules.auth.domain.SysUser;
import com.gov.procurement.modules.auth.mapper.SysUserMapper;
import com.gov.procurement.modules.org.domain.ProjectGroup;
import com.gov.procurement.modules.org.mapper.ProjectGroupMapper;
import com.gov.procurement.modules.requisition.domain.OutboundItem;
import com.gov.procurement.modules.requisition.domain.OutboundOrder;
import com.gov.procurement.modules.requisition.domain.Requisition;
import com.gov.procurement.modules.requisition.domain.RequisitionItem;
import com.gov.procurement.modules.requisition.dto.ApproveOutboundVO;
import com.gov.procurement.modules.requisition.dto.CreateRequisitionCmd;
import com.gov.procurement.modules.requisition.dto.CreateRequisitionVO;
import com.gov.procurement.modules.requisition.dto.RejectReq;
import com.gov.procurement.modules.requisition.dto.RejectVO;
import com.gov.procurement.modules.requisition.dto.RequisitionDetailVO;
import com.gov.procurement.modules.requisition.dto.RequisitionDetailVO.DetailItemVO;
import com.gov.procurement.modules.requisition.dto.RequisitionDetailVO.OutboundVO;
import com.gov.procurement.modules.requisition.dto.RequisitionDetailVO.OutboundVO.OutboundLineVO;
import com.gov.procurement.modules.requisition.dto.RequisitionItemReq;
import com.gov.procurement.modules.requisition.dto.RequisitionTodoVO;
import com.gov.procurement.modules.requisition.dto.RequisitionTodoVO.TodoLineVO;
import com.gov.procurement.modules.requisition.dto.RequisitionVO;
import com.gov.procurement.modules.requisition.dto.StockOptionVO;
import com.gov.procurement.modules.requisition.mapper.OutboundItemMapper;
import com.gov.procurement.modules.requisition.mapper.OutboundOrderMapper;
import com.gov.procurement.modules.requisition.mapper.RequisitionItemMapper;
import com.gov.procurement.modules.requisition.mapper.RequisitionMapper;
import com.gov.procurement.modules.requisition.service.RequisitionService;
import com.gov.procurement.modules.stock.domain.StockItem;
import com.gov.procurement.modules.stock.mapper.StockItemMapper;
import com.gov.procurement.modules.stock.service.StockService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static com.gov.procurement.modules.requisition.constant.RequisitionConst.ST_OUTBOUND;
import static com.gov.procurement.modules.requisition.constant.RequisitionConst.ST_PENDING_WAREHOUSE;
import static com.gov.procurement.modules.requisition.constant.RequisitionConst.ST_REJECTED;

/**
 * 领用 + 审批出库实现。审批出库为单事务：领用单与库存项均加 FOR UPDATE 行锁，锁内校验库存充足后扣减、记流水、
 * 生成出库单/明细、推进状态，任一步失败整体回滚（零超发）。多明细按 stock_item_id 升序加锁以规避死锁。
 */
@Service
public class RequisitionServiceImpl implements RequisitionService {

    private static final Logger log = LoggerFactory.getLogger(RequisitionServiceImpl.class);

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final RequisitionMapper requisitionMapper;
    private final RequisitionItemMapper requisitionItemMapper;
    private final OutboundOrderMapper outboundOrderMapper;
    private final OutboundItemMapper outboundItemMapper;
    private final StockService stockService;
    private final StockItemMapper stockItemMapper;
    private final ProjectGroupMapper projectGroupMapper;
    private final SysUserMapper sysUserMapper;

    public RequisitionServiceImpl(RequisitionMapper requisitionMapper, RequisitionItemMapper requisitionItemMapper,
                                  OutboundOrderMapper outboundOrderMapper, OutboundItemMapper outboundItemMapper,
                                  StockService stockService, StockItemMapper stockItemMapper,
                                  ProjectGroupMapper projectGroupMapper, SysUserMapper sysUserMapper) {
        this.requisitionMapper = requisitionMapper;
        this.requisitionItemMapper = requisitionItemMapper;
        this.outboundOrderMapper = outboundOrderMapper;
        this.outboundItemMapper = outboundItemMapper;
        this.stockService = stockService;
        this.stockItemMapper = stockItemMapper;
        this.projectGroupMapper = projectGroupMapper;
        this.sysUserMapper = sysUserMapper;
    }

    @Override
    public List<StockOptionVO> stockOptions(Long projectGroupId) {
        if (projectGroupMapper.selectById(projectGroupId) == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "项目组不存在");
        }
        return stockItemMapper.selectList(new LambdaQueryWrapper<StockItem>()
                        .eq(StockItem::getProjectGroupId, projectGroupId)
                        .orderByAsc(StockItem::getMaterialName)).stream()
                .map(si -> new StockOptionVO(si.getId(), si.getMaterialName(), si.getQuantity()))
                .toList();
    }

    @Override
    @Transactional
    public CreateRequisitionVO create(CreateRequisitionCmd cmd) {
        long applicantId = StpUtil.getLoginIdAsLong();
        if (projectGroupMapper.selectById(cmd.projectGroupId()) == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "项目组不存在");
        }
        List<Long> stockIds = cmd.items().stream().map(RequisitionItemReq::stockItemId).distinct().toList();
        if (stockItemMapper.selectBatchIds(stockIds).size() < stockIds.size()) {
            throw new BizException(ErrorCode.NOT_FOUND, "存在不存在的库存项");
        }

        Requisition req = new Requisition();
        req.setProjectGroupId(cmd.projectGroupId());
        req.setApplicantId(applicantId);
        req.setPurpose(cmd.purpose());
        req.setStatus(ST_PENDING_WAREHOUSE);
        requisitionMapper.insert(req);

        List<RequisitionItem> items = cmd.items().stream().map(i -> {
            RequisitionItem ri = new RequisitionItem();
            ri.setRequisitionId(req.getId());
            ri.setStockItemId(i.stockItemId());
            ri.setQty(i.qty());
            return ri;
        }).toList();
        requisitionItemMapper.insertBatch(items);

        log.info("发起领用 requisitionId={}, applicantId={}, items={}", req.getId(), applicantId, items.size());
        return new CreateRequisitionVO(req.getId(), ST_PENDING_WAREHOUSE);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ApproveOutboundVO approveOutbound(Long requisitionId) {
        long operatorId = StpUtil.getLoginIdAsLong();
        // 锁领用单行：串行化并发审批/驳回，状态校验权威（避免重复出库）。
        Requisition req = requisitionMapper.selectOne(new LambdaQueryWrapper<Requisition>()
                .eq(Requisition::getId, requisitionId).last("FOR UPDATE"));
        if (req == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "领用单不存在");
        }
        if (!ST_PENDING_WAREHOUSE.equals(req.getStatus())) {
            throw new BizException(ErrorCode.STATE_CONFLICT, "领用单已处理，不可重复出库");
        }

        List<RequisitionItem> items = requisitionItemMapper.selectList(new LambdaQueryWrapper<RequisitionItem>()
                .eq(RequisitionItem::getRequisitionId, requisitionId));
        // 升序按 stock_item_id 加锁，规避多明细交叉死锁（TBD-3）。
        items.sort(Comparator.comparing(RequisitionItem::getStockItemId));

        OutboundOrder ob = new OutboundOrder();
        ob.setRequisitionId(requisitionId);
        ob.setApproverId(operatorId);
        outboundOrderMapper.insert(ob);

        for (RequisitionItem it : items) {
            // M5 锁内校验库存充足后扣减并记流水；不足抛 40904，整事务回滚（零超发）。
            stockService.deductStock(it.getStockItemId(), it.getQty(), ob.getId());
            OutboundItem oi = new OutboundItem();
            oi.setOutboundOrderId(ob.getId());
            oi.setStockItemId(it.getStockItemId());
            oi.setQty(it.getQty());
            outboundItemMapper.insert(oi);
        }

        requisitionMapper.update(null, new LambdaUpdateWrapper<Requisition>()
                .eq(Requisition::getId, requisitionId)
                .set(Requisition::getStatus, ST_OUTBOUND));

        log.info("审批出库 requisitionId={}, outboundOrderId={}, operatorId={}", requisitionId, ob.getId(), operatorId);
        return new ApproveOutboundVO(requisitionId, ob.getId(), ST_OUTBOUND);
    }

    @Override
    @Transactional
    public RejectVO reject(Long requisitionId, RejectReq req) {
        String opinion = req == null ? null : req.opinion();
        if (opinion == null || opinion.isBlank()) {
            throw new BizException(ErrorCode.REJECT_OPINION_REQUIRED);
        }
        Requisition r = requisitionMapper.selectOne(new LambdaQueryWrapper<Requisition>()
                .eq(Requisition::getId, requisitionId).last("FOR UPDATE"));
        if (r == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "领用单不存在");
        }
        if (!ST_PENDING_WAREHOUSE.equals(r.getStatus())) {
            throw new BizException(ErrorCode.STATE_CONFLICT, "领用单已处理，不可驳回");
        }
        requisitionMapper.update(null, new LambdaUpdateWrapper<Requisition>()
                .eq(Requisition::getId, requisitionId)
                .set(Requisition::getStatus, ST_REJECTED)
                .set(Requisition::getRejectOpinion, opinion));

        log.info("驳回领用 requisitionId={}", requisitionId);
        return new RejectVO(requisitionId, ST_REJECTED);
    }

    @Override
    public Page<RequisitionTodoVO> todo(Long projectGroupId, int pageNum, int size) {
        int current = pageNum < 1 ? 1 : pageNum;
        int limit = (size < 1 || size > MAX_PAGE_SIZE) ? DEFAULT_PAGE_SIZE : size;

        Page<Requisition> result = requisitionMapper.selectPage(new Page<>(current, limit),
                new LambdaQueryWrapper<Requisition>()
                        .eq(Requisition::getStatus, ST_PENDING_WAREHOUSE)
                        .eq(projectGroupId != null, Requisition::getProjectGroupId, projectGroupId)
                        .orderByDesc(Requisition::getId));

        List<Requisition> recs = result.getRecords();
        Map<Long, String> pgNames = projectGroupNames(
                recs.stream().map(Requisition::getProjectGroupId).collect(Collectors.toSet()));
        Map<Long, String> applicantNames = userNames(
                recs.stream().map(Requisition::getApplicantId).collect(Collectors.toSet()));

        List<RequisitionTodoVO> vos = recs.stream().map(r -> {
            List<TodoLineVO> lines = requisitionItemMapper.selectItemsWithStock(r.getId()).stream()
                    .map(row -> new TodoLineVO(row.getStockItemId(), row.getMaterialName(), row.getQty(),
                            row.getCurrentQuantity(), row.getCurrentQuantity().compareTo(row.getQty()) >= 0))
                    .toList();
            return new RequisitionTodoVO(r.getId(), pgNames.get(r.getProjectGroupId()),
                    applicantNames.get(r.getApplicantId()), r.getCreatedAt(), lines);
        }).toList();

        Page<RequisitionTodoVO> page = new Page<>(current, limit, result.getTotal());
        page.setRecords(vos);
        return page;
    }

    @Override
    public Page<RequisitionVO> list(String status, Long projectGroupId, Long applicantId, int pageNum, int size) {
        int current = pageNum < 1 ? 1 : pageNum;
        int limit = (size < 1 || size > MAX_PAGE_SIZE) ? DEFAULT_PAGE_SIZE : size;

        Page<Requisition> result = requisitionMapper.selectPage(new Page<>(current, limit),
                new LambdaQueryWrapper<Requisition>()
                        .eq(status != null && !status.isBlank(), Requisition::getStatus, status)
                        .eq(projectGroupId != null, Requisition::getProjectGroupId, projectGroupId)
                        .eq(applicantId != null, Requisition::getApplicantId, applicantId)
                        .orderByDesc(Requisition::getId));

        List<Requisition> recs = result.getRecords();
        Map<Long, String> applicantNames = userNames(
                recs.stream().map(Requisition::getApplicantId).collect(Collectors.toSet()));

        List<RequisitionVO> vos = recs.stream().map(r -> {
            int count = Math.toIntExact(requisitionItemMapper.selectCount(
                    new LambdaQueryWrapper<RequisitionItem>().eq(RequisitionItem::getRequisitionId, r.getId())));
            return new RequisitionVO(r.getId(), r.getStatus(), applicantNames.get(r.getApplicantId()),
                    r.getCreatedAt(), count);
        }).toList();

        Page<RequisitionVO> page = new Page<>(current, limit, result.getTotal());
        page.setRecords(vos);
        return page;
    }

    @Override
    public RequisitionDetailVO getDetail(Long requisitionId) {
        Requisition r = requisitionMapper.selectById(requisitionId);
        if (r == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "领用单不存在");
        }
        String applicantName = userNames(Set.of(r.getApplicantId())).get(r.getApplicantId());
        List<DetailItemVO> items = requisitionItemMapper.selectItemsWithStock(requisitionId).stream()
                .map(row -> new DetailItemVO(row.getStockItemId(), row.getMaterialName(), row.getQty()))
                .toList();

        OutboundVO outbound = null;
        if (ST_OUTBOUND.equals(r.getStatus())) {
            OutboundOrder ob = outboundOrderMapper.selectOne(new LambdaQueryWrapper<OutboundOrder>()
                    .eq(OutboundOrder::getRequisitionId, requisitionId));
            if (ob != null) {
                List<OutboundLineVO> lines = outboundItemMapper.selectRowsByOrder(ob.getId()).stream()
                        .map(row -> new OutboundLineVO(row.getStockItemId(), row.getMaterialName(), row.getQty()))
                        .toList();
                outbound = new OutboundVO(ob.getId(), ob.getApproverId(), ob.getOutboundAt(), lines);
            }
        }

        return new RequisitionDetailVO(r.getId(), r.getStatus(), r.getProjectGroupId(), r.getApplicantId(),
                applicantName, r.getPurpose(), r.getRejectOpinion(), r.getCreatedAt(), items, outbound);
    }

    private Map<Long, String> projectGroupNames(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return projectGroupMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(ProjectGroup::getId, ProjectGroup::getName));
    }

    private Map<Long, String> userNames(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return sysUserMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(SysUser::getId, SysUser::getName));
    }
}
