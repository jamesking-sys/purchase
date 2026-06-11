package com.gov.procurement.modules.stocktake.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * 确认调整结果（详设 U12 §6.3）。仅列出实际调整的库存项（diff_type != none）。
 *
 * @param stocktakeId   盘点单 id
 * @param status        confirmed
 * @param adjustedCount 实际调整的库存项数（diff_type != none）
 * @param items         被调整库存项明细
 */
public record ConfirmStocktakeVO(
        Long stocktakeId,
        String status,
        int adjustedCount,
        List<AdjustedItemVO> items) {

    /**
     * 被调整库存项行。
     *
     * @param stockItemId 库存项 id
     * @param quantity    调整后库存（= actual_qty）
     * @param txnType     gain/loss
     */
    public record AdjustedItemVO(
            Long stockItemId,
            BigDecimal quantity,
            String txnType) {
    }
}
