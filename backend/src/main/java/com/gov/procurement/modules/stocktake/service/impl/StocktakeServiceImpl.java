package com.gov.procurement.modules.stocktake.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.gov.procurement.common.BizException;
import com.gov.procurement.common.ErrorCode;
import com.gov.procurement.modules.org.mapper.ProjectGroupMapper;
import com.gov.procurement.modules.stock.domain.StockItem;
import com.gov.procurement.modules.stock.mapper.StockItemMapper;
import com.gov.procurement.modules.stock.service.StockService;
import com.gov.procurement.modules.stocktake.domain.Stocktake;
import com.gov.procurement.modules.stocktake.domain.StocktakeItem;
import com.gov.procurement.modules.stocktake.dto.ConfirmStocktakeVO;
import com.gov.procurement.modules.stocktake.dto.ConfirmStocktakeVO.AdjustedItemVO;
import com.gov.procurement.modules.stocktake.dto.CreateStocktakeCmd;
import com.gov.procurement.modules.stocktake.dto.CreateStocktakeVO;
import com.gov.procurement.modules.stocktake.dto.CreateStocktakeVO.SnapshotItemVO;
import com.gov.procurement.modules.stocktake.dto.SaveActualsCmd;
import com.gov.procurement.modules.stocktake.dto.SaveActualsCmd.ActualItemReq;
import com.gov.procurement.modules.stocktake.dto.SaveActualsVO;
import com.gov.procurement.modules.stocktake.dto.SaveActualsVO.ActualResultVO;
import com.gov.procurement.modules.stocktake.mapper.StocktakeItemMapper;
import com.gov.procurement.modules.stocktake.mapper.StocktakeMapper;
import com.gov.procurement.modules.stocktake.service.StocktakeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.gov.procurement.modules.stocktake.constant.StocktakeConst.DIFF_GAIN;
import static com.gov.procurement.modules.stocktake.constant.StocktakeConst.DIFF_LOSS;
import static com.gov.procurement.modules.stocktake.constant.StocktakeConst.DIFF_NONE;
import static com.gov.procurement.modules.stocktake.constant.StocktakeConst.ST_CONFIRMED;
import static com.gov.procurement.modules.stocktake.constant.StocktakeConst.ST_COUNTING;

/**
 * 盘点实现。发起单事务快照账面 + 批量建明细；录入实盘仅写 stocktake_item（不触库存）；确认是核心事务边界：
 * 先 FOR UPDATE 锁盘点单校验 counting（防并发重复确认），再逐项经 M5 {@code adjustTo} 行锁置数 + 记 gain/loss 流水，
 * 末翻状态 confirmed，任一步异常整体回滚（全或无，不留部分调整）。多明细按 stock_item_id 升序加锁避死锁。
 */
@Service
public class StocktakeServiceImpl implements StocktakeService {

    private static final Logger log = LoggerFactory.getLogger(StocktakeServiceImpl.class);

    private final StocktakeMapper stocktakeMapper;
    private final StocktakeItemMapper stocktakeItemMapper;
    private final StockItemMapper stockItemMapper;
    private final StockService stockService;
    private final ProjectGroupMapper projectGroupMapper;

    public StocktakeServiceImpl(StocktakeMapper stocktakeMapper, StocktakeItemMapper stocktakeItemMapper,
                                StockItemMapper stockItemMapper, StockService stockService,
                                ProjectGroupMapper projectGroupMapper) {
        this.stocktakeMapper = stocktakeMapper;
        this.stocktakeItemMapper = stocktakeItemMapper;
        this.stockItemMapper = stockItemMapper;
        this.stockService = stockService;
        this.projectGroupMapper = projectGroupMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CreateStocktakeVO createStocktake(CreateStocktakeCmd cmd) {
        if (projectGroupMapper.selectById(cmd.scopeProjectGroupId()) == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "项目组不存在");
        }

        // 快照该项目组下所有未删库存项的账面数（is_deleted=0 由 MP 自动追加）。
        List<StockItem> stockItems = stockItemMapper.selectList(new LambdaQueryWrapper<StockItem>()
                .eq(StockItem::getProjectGroupId, cmd.scopeProjectGroupId())
                .orderByAsc(StockItem::getId));

        Stocktake st = new Stocktake();
        st.setScopeProjectGroupId(cmd.scopeProjectGroupId());
        st.setStatus(ST_COUNTING);
        stocktakeMapper.insert(st);

        List<SnapshotItemVO> items = new ArrayList<>(stockItems.size());
        for (StockItem si : stockItems) {
            StocktakeItem sti = new StocktakeItem();
            sti.setStocktakeId(st.getId());
            sti.setStockItemId(si.getId());
            sti.setBookQty(si.getQuantity());
            sti.setActualQty(si.getQuantity());     // 实盘初始化为账面，diff=0
            sti.setDiff(BigDecimal.ZERO);
            sti.setDiffType(DIFF_NONE);
            stocktakeItemMapper.insert(sti);         // 回填生成 id 供响应
            items.add(new SnapshotItemVO(sti.getId(), si.getId(), si.getMaterialName(), si.getQuantity()));
        }

        log.info("发起盘点 stocktakeId={}, scopePg={}, items={}", st.getId(), cmd.scopeProjectGroupId(), items.size());
        return new CreateStocktakeVO(st.getId(), ST_COUNTING, items);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SaveActualsVO saveActuals(Long stocktakeId, SaveActualsCmd cmd) {
        Stocktake st = stocktakeMapper.selectById(stocktakeId);
        if (st == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "盘点单不存在");
        }
        if (!ST_COUNTING.equals(st.getStatus())) {
            throw new BizException(ErrorCode.STATE_CONFLICT, "盘点单已确认，不可再录入实盘");
        }

        Map<Long, StocktakeItem> byId = stocktakeItemMapper.selectList(new LambdaQueryWrapper<StocktakeItem>()
                        .eq(StocktakeItem::getStocktakeId, stocktakeId)).stream()
                .collect(Collectors.toMap(StocktakeItem::getId, Function.identity()));

        // 先校验全部（属本单 + 实盘数非负），全过再落库——避免一坏行留半截写入。
        for (ActualItemReq it : cmd.items()) {
            if (!byId.containsKey(it.stocktakeItemId())) {
                throw new BizException(ErrorCode.NOT_FOUND, "盘点明细不属于该盘点单：" + it.stocktakeItemId());
            }
            if (it.actualQty().signum() < 0) {
                throw new BizException(ErrorCode.NEGATIVE_ACTUAL_QTY, "实盘数不能为负：明细 " + it.stocktakeItemId());
            }
        }

        List<ActualResultVO> results = new ArrayList<>(cmd.items().size());
        for (ActualItemReq it : cmd.items()) {
            StocktakeItem sti = byId.get(it.stocktakeItemId());
            BigDecimal diff = it.actualQty().subtract(sti.getBookQty());
            String diffType = diffType(diff);
            stocktakeItemMapper.update(null, new LambdaUpdateWrapper<StocktakeItem>()
                    .eq(StocktakeItem::getId, it.stocktakeItemId())
                    .set(StocktakeItem::getActualQty, it.actualQty())
                    .set(StocktakeItem::getDiff, diff)
                    .set(StocktakeItem::getDiffType, diffType));
            results.add(new ActualResultVO(it.stocktakeItemId(), diff, diffType));
        }

        return new SaveActualsVO(results);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ConfirmStocktakeVO confirm(Long stocktakeId) {
        // 锁盘点单行：串行化并发确认，落后者看到 confirmed 而抛 40903，杜绝重复调库存/重复流水（幂等）。
        Stocktake st = stocktakeMapper.selectOne(new LambdaQueryWrapper<Stocktake>()
                .eq(Stocktake::getId, stocktakeId).last("FOR UPDATE"));
        if (st == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "盘点单不存在");
        }
        if (!ST_COUNTING.equals(st.getStatus())) {
            throw new BizException(ErrorCode.STATE_CONFLICT, "盘点单已确认，不可重复确认");
        }

        List<StocktakeItem> items = stocktakeItemMapper.selectList(new LambdaQueryWrapper<StocktakeItem>()
                .eq(StocktakeItem::getStocktakeId, stocktakeId));
        // 按 stock_item_id 升序加锁，规避与入库/出库多明细交叉死锁。
        items.sort(Comparator.comparing(StocktakeItem::getStockItemId));

        List<AdjustedItemVO> adjusted = new ArrayList<>();
        for (StocktakeItem it : items) {
            if (DIFF_NONE.equals(it.getDiffType())) {
                continue;   // 无差异不调库存、不记流水
            }
            // M5 锁内置数到实盘 + 记 gain/loss 流水（qty_change 按调整时库内当前值算，INV-3 严格成立）。
            stockService.adjustTo(it.getStockItemId(), it.getActualQty(), it.getDiffType(), stocktakeId);
            adjusted.add(new AdjustedItemVO(it.getStockItemId(), it.getActualQty(), it.getDiffType()));
        }

        stocktakeMapper.update(null, new LambdaUpdateWrapper<Stocktake>()
                .eq(Stocktake::getId, stocktakeId)
                .set(Stocktake::getStatus, ST_CONFIRMED));

        log.info("确认盘点 stocktakeId={}, adjustedCount={}", stocktakeId, adjusted.size());
        return new ConfirmStocktakeVO(stocktakeId, ST_CONFIRMED, adjusted.size(), adjusted);
    }

    /** 差异类型：>0 盘盈 / <0 盘亏 / =0 无差异（用 signum 规避 BigDecimal 标度比较陷阱）。 */
    private String diffType(BigDecimal diff) {
        int sign = diff.signum();
        return sign > 0 ? DIFF_GAIN : (sign < 0 ? DIFF_LOSS : DIFF_NONE);
    }
}
