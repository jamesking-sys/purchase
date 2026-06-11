package com.gov.procurement.modules.stock.dto;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import java.math.BigDecimal;

/**
 * 库存流水分页 + 可选对账汇总（详设 U10 §6.2 / §5.4）。
 * 对账不变量 INV-1：bookQty == txnSum（== stock_item.quantity == Σ qty_change），diff 恒为 0；非 0 表示记账被绕过。
 *
 * @param page    流水分页（按 created_at 倒序）
 * @param bookQty 该库存项 stock_item.quantity（账面）
 * @param txnSum  全部流水 qty_change 之和（应 == bookQty）
 */
public record StockTxnPageVO(
        Page<StockTxnVO> page,
        BigDecimal bookQty,
        BigDecimal txnSum) {
}
