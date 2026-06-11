package com.gov.procurement.modules.stocktake.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * 发起盘点结果（详设 U12 §6.1）。返回快照生成的盘点明细，账面数 book_qty 为发起时点快照。
 *
 * @param stocktakeId 新建盘点单 id
 * @param status      counting
 * @param items       盘点明细（每个未删库存项一条）
 */
public record CreateStocktakeVO(
        Long stocktakeId,
        String status,
        List<SnapshotItemVO> items) {

    /**
     * 盘点明细快照行。
     *
     * @param stocktakeItemId 盘点明细 id
     * @param stockItemId     库存项 id
     * @param materialName    物料名
     * @param bookQty         快照账面数
     */
    public record SnapshotItemVO(
            Long stocktakeItemId,
            Long stockItemId,
            String materialName,
            BigDecimal bookQty) {
    }
}
