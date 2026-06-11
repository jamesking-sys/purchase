package com.gov.procurement.modules.stock.service;

import java.math.BigDecimal;

/**
 * 库存能力（M5/BC5）：库存增减的唯一记账入口。所有增减经此处 upsert 库存项 + 写流水成对完成（INV-3）。
 * 本期仅入库（+）；出库（-，FOR UPDATE + CHECK 防超发）、盘盈/盘亏由 U11/U12 引入。
 */
public interface StockService {

    /**
     * 入库记账：按聚合键累加库存项并写一条 inbound 流水，返回库存项 id。运行在调用方事务内（REQUIRED）。
     *
     * @param materialName   物料名（聚合键）
     * @param projectGroupId 项目组 id（聚合键）
     * @param departmentId   部门 id（新建库存项时冗余写入）
     * @param qty            本次实收（> 0）
     * @param inboundOrderId 来源入库单 id（流水 ref_id）
     * @return 库存项 id
     */
    Long addStock(String materialName, Long projectGroupId, Long departmentId, BigDecimal qty, Long inboundOrderId);

    /**
     * 出库扣减：对库存项加行级悲观锁（FOR UPDATE）后校验库存充足，扣减并写一条 outbound 流水（INV-1/2/3）。
     * 库存项不存在 → 40401；库存不足 → 40904（防超发）。运行在调用方事务内（REQUIRED）。
     *
     * @param stockItemId     库存项 id
     * @param qty             出库量（> 0）
     * @param outboundOrderId 来源出库单 id（流水 ref_id）
     */
    void deductStock(Long stockItemId, BigDecimal qty, Long outboundOrderId);

    /**
     * 盘点差异校正：对库存项加 FOR UPDATE 行锁后把 quantity 绝对值置为 targetQty（实盘数），并写一条
     * gain/loss 流水，qty_change = targetQty - 调整时库内当前 quantity（带正负，保证 INV-3：库存==流水累计）。
     * 库存项不存在 → 40401。运行在调用方事务内（REQUIRED）。
     *
     * @param stockItemId 库存项 id
     * @param targetQty   目标库存量（= 实盘数，≥ 0）
     * @param type        流水类型（gain/loss，取盘点明细 diff_type）
     * @param stocktakeId 来源盘点单 id（流水 ref_id）
     * @return 本次校正增减量（qty_change，带正负）
     */
    BigDecimal adjustTo(Long stockItemId, BigDecimal targetQty, String type, Long stocktakeId);
}
