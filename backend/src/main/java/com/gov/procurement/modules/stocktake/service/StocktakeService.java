package com.gov.procurement.modules.stocktake.service;

import com.gov.procurement.modules.stocktake.dto.ConfirmStocktakeVO;
import com.gov.procurement.modules.stocktake.dto.CreateStocktakeCmd;
import com.gov.procurement.modules.stocktake.dto.CreateStocktakeVO;
import com.gov.procurement.modules.stocktake.dto.SaveActualsCmd;
import com.gov.procurement.modules.stocktake.dto.SaveActualsVO;

/**
 * 盘点能力（M5/BC5，详设 U12）：发起盘点（快照账面）→ 录入实盘（只录账，不调库存）→ 确认调整（单事务调库存 + 记 gain/loss 流水）。
 * 库存写入仍经唯一记账入口 {@link com.gov.procurement.modules.stock.service.StockService#adjustTo}，不绕过 SoR。
 */
public interface StocktakeService {

    /**
     * 发起盘点：单事务内快照该项目组下所有未删库存项的账面数，生成盘点单（counting）+ 逐条盘点明细。
     * 项目组不存在 → 40401。
     */
    CreateStocktakeVO createStocktake(CreateStocktakeCmd cmd);

    /**
     * 录入实盘：counting 态盘点单逐条更新 actual_qty 并重算 diff/diff_type，不触碰 stock_item。
     * 盘点单/明细不存在 → 40401；已确认 → 40903；实盘数为负 → 42205。
     */
    SaveActualsVO saveActuals(Long stocktakeId, SaveActualsCmd cmd);

    /**
     * 确认调整：单事务内锁盘点单（防并发重复确认），逐条对有差异明细把 stock_item 置数到实盘并记 gain/loss 流水，
     * 末置盘点单 confirmed；任一步失败整体回滚。盘点单不存在 → 40401；非 counting → 40903。
     */
    ConfirmStocktakeVO confirm(Long stocktakeId);
}
