package com.gov.procurement.modules.stock.dto;

import java.math.BigDecimal;

/**
 * 库存项视图（详设 U10 §6.1）。当前库存量 quantity == Σ stock_txn.qty_change（INV-1）。
 *
 * @param stockItemId    库存项 id
 * @param materialName   物料 / 资产名
 * @param projectGroupId 归属项目组 id
 * @param departmentId   归属部门 id
 * @param quantity       当前库存量
 */
public record StockItemVO(
        Long stockItemId,
        String materialName,
        Long projectGroupId,
        Long departmentId,
        BigDecimal quantity) {
}
