package com.gov.procurement.modules.stock.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gov.procurement.common.BizException;
import com.gov.procurement.common.ErrorCode;
import com.gov.procurement.modules.stock.domain.StockItem;
import com.gov.procurement.modules.stock.domain.StockTxn;
import com.gov.procurement.modules.stock.mapper.StockItemMapper;
import com.gov.procurement.modules.stock.mapper.StockTxnMapper;
import com.gov.procurement.modules.stock.service.StockService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

import static com.gov.procurement.modules.stock.constant.StockConst.REF_INBOUND_ORDER;
import static com.gov.procurement.modules.stock.constant.StockConst.REF_OUTBOUND_ORDER;
import static com.gov.procurement.modules.stock.constant.StockConst.REF_STOCKTAKE;
import static com.gov.procurement.modules.stock.constant.StockConst.TXN_INBOUND;
import static com.gov.procurement.modules.stock.constant.StockConst.TXN_OUTBOUND;

/**
 * 库存能力实现。库存增减与写流水紧邻、同事务，保证「库存 == 流水累计」（INV-3）。
 * 不自开事务：由调用方（InboundService / RequisitionService）的 {@code @Transactional} 以 REQUIRED 传播覆盖。
 */
@Service
public class StockServiceImpl implements StockService {

    private final StockItemMapper stockItemMapper;
    private final StockTxnMapper stockTxnMapper;

    public StockServiceImpl(StockItemMapper stockItemMapper, StockTxnMapper stockTxnMapper) {
        this.stockItemMapper = stockItemMapper;
        this.stockTxnMapper = stockTxnMapper;
    }

    @Override
    public Long addStock(String materialName, Long projectGroupId, Long departmentId, BigDecimal qty,
                         Long inboundOrderId) {
        Long stockItemId = stockItemMapper.upsertAddQuantity(materialName, projectGroupId, departmentId, qty);
        writeTxn(stockItemId, TXN_INBOUND, qty, REF_INBOUND_ORDER, inboundOrderId);
        return stockItemId;
    }

    @Override
    public void deductStock(Long stockItemId, BigDecimal qty, Long outboundOrderId) {
        // 行级悲观锁：锁内取最新库存再比对，杜绝并发超发（INV-2）。
        StockItem item = stockItemMapper.selectOne(new LambdaQueryWrapper<StockItem>()
                .eq(StockItem::getId, stockItemId)
                .last("FOR UPDATE"));
        if (item == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "库存项不存在：" + stockItemId);
        }
        if (item.getQuantity().compareTo(qty) < 0) {
            throw new BizException(ErrorCode.INSUFFICIENT_STOCK, "库存不足，不可超发：库存项 " + stockItemId);
        }
        stockItemMapper.deductQuantity(stockItemId, qty);
        writeTxn(stockItemId, TXN_OUTBOUND, qty.negate(), REF_OUTBOUND_ORDER, outboundOrderId);
    }

    @Override
    public BigDecimal adjustTo(Long stockItemId, BigDecimal targetQty, String type, Long stocktakeId) {
        // 行级悲观锁：与入库/出库对同一库存项的并发写串行化，锁内按当前值算校正量（INV-3 严格成立）。
        StockItem item = stockItemMapper.selectOne(new LambdaQueryWrapper<StockItem>()
                .eq(StockItem::getId, stockItemId)
                .last("FOR UPDATE"));
        if (item == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "库存项不存在：" + stockItemId);
        }
        BigDecimal change = targetQty.subtract(item.getQuantity());
        stockItemMapper.setQuantity(stockItemId, targetQty);   // 绝对值置数，CHECK(quantity>=0) 兜底
        writeTxn(stockItemId, type, change, REF_STOCKTAKE, stocktakeId);
        return change;
    }

    private void writeTxn(Long stockItemId, String type, BigDecimal qtyChange, String refType, Long refId) {
        StockTxn txn = new StockTxn();
        txn.setStockItemId(stockItemId);
        txn.setType(type);
        txn.setQtyChange(qtyChange);
        txn.setRefType(refType);
        txn.setRefId(refId);
        stockTxnMapper.insert(txn);
    }
}
