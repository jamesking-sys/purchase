package com.gov.procurement.modules.stock.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gov.procurement.common.BizException;
import com.gov.procurement.common.ErrorCode;
import com.gov.procurement.modules.stock.domain.StockItem;
import com.gov.procurement.modules.stock.domain.StockTxn;
import com.gov.procurement.modules.stock.dto.StockItemVO;
import com.gov.procurement.modules.stock.dto.StockTxnPageVO;
import com.gov.procurement.modules.stock.dto.StockTxnVO;
import com.gov.procurement.modules.stock.mapper.StockItemMapper;
import com.gov.procurement.modules.stock.mapper.StockTxnMapper;
import com.gov.procurement.modules.stock.service.StockQueryService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 库存只读查询实现（详设 U10）。只读：{@code @Transactional(readOnly = true)} 提示禁写，不持锁、不写流水。
 * 分页越界显式抛 40001（详设 §5.1 拍板取「抛」而非归一），便于前端定位非法翻页参数。
 */
@Service
public class StockQueryServiceImpl implements StockQueryService {

    private static final int MAX_PAGE_SIZE = 200;

    private final StockItemMapper stockItemMapper;
    private final StockTxnMapper stockTxnMapper;

    public StockQueryServiceImpl(StockItemMapper stockItemMapper, StockTxnMapper stockTxnMapper) {
        this.stockItemMapper = stockItemMapper;
        this.stockTxnMapper = stockTxnMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<StockItemVO> pageStock(Long projectGroupId, Long departmentId, String materialName,
                                       int page, int size) {
        validatePaging(page, size);

        // is_deleted=0 由 MyBatis-Plus 逻辑删除自动追加；过滤命中 idx_stock_pg。
        // materialName 用参数化 ILIKE（大小写不敏感，详设 §5.2）；{0} 占位防注入。
        LambdaQueryWrapper<StockItem> qw = new LambdaQueryWrapper<StockItem>()
                .eq(projectGroupId != null, StockItem::getProjectGroupId, projectGroupId)
                .eq(departmentId != null, StockItem::getDepartmentId, departmentId)
                .apply(StringUtils.hasText(materialName), "material_name ILIKE {0}", "%" + materialName + "%")
                .orderByAsc(StockItem::getProjectGroupId, StockItem::getMaterialName);

        Page<StockItem> result = stockItemMapper.selectPage(new Page<>(page, size), qw);

        List<StockItemVO> records = result.getRecords().stream()
                .map(s -> new StockItemVO(s.getId(), s.getMaterialName(), s.getProjectGroupId(),
                        s.getDepartmentId(), s.getQuantity()))
                .toList();
        Page<StockItemVO> vo = new Page<>(page, size, result.getTotal());
        vo.setRecords(records);
        return vo;
    }

    @Override
    @Transactional(readOnly = true)
    public StockTxnPageVO pageTxn(Long stockItemId, int page, int size) {
        validatePaging(page, size);

        // 前置存在性校验：宿主库存项不存在 / 已删 → 40401（区别于流水为空的合法空集）。
        StockItem item = stockItemMapper.selectById(stockItemId);
        if (item == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "库存项不存在");
        }

        // created_at DESC 主排 + id DESC 作 tie-breaker，保证深翻页稳定不重复（命中 idx_txn_stock）。
        Page<StockTxn> result = stockTxnMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<StockTxn>()
                        .eq(StockTxn::getStockItemId, stockItemId)
                        .orderByDesc(StockTxn::getCreatedAt)
                        .orderByDesc(StockTxn::getId));

        List<StockTxnVO> records = result.getRecords().stream()
                .map(t -> new StockTxnVO(t.getId(), t.getType(), t.getQtyChange(),
                        t.getRefType(), t.getRefId(), t.getCreatedAt()))
                .toList();
        Page<StockTxnVO> txnPage = new Page<>(page, size, result.getTotal());
        txnPage.setRecords(records);

        return new StockTxnPageVO(txnPage, item.getQuantity(), stockTxnMapper.sumQtyChange(stockItemId));
    }

    /** 分页参数校验（详设 U10 §2 P2/P6、T-9）：page≥1、1≤size≤200，否则 40001。 */
    private void validatePaging(int page, int size) {
        if (page < 1 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "分页参数非法：page≥1，1≤size≤" + MAX_PAGE_SIZE);
        }
    }
}
