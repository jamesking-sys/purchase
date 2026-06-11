package com.gov.procurement.modules.stocktake.constant;

/**
 * 盘点模块常量：盘点单状态机取值（对齐 DB stocktake.status 的 CHECK）与差异类型（对齐 stocktake_item.diff_type 的 CHECK）。
 * 盘点是 M5 库存的「账实校正」入口，确认时经 StockService.adjustTo 置数 + 记 gain/loss 流水（详设 U12）。
 */
public final class StocktakeConst {

    private StocktakeConst() {
    }

    /** 盘点中（发起即此态，可多次录入实盘）。 */
    public static final String ST_COUNTING = "counting";
    /** 已确认（终态，确认调整后不可再录入/再确认）。 */
    public static final String ST_CONFIRMED = "confirmed";

    /** 差异类型：盘盈（实盘 > 账面）。 */
    public static final String DIFF_GAIN = "gain";
    /** 差异类型：盘亏（实盘 < 账面）。 */
    public static final String DIFF_LOSS = "loss";
    /** 差异类型：无差异（实盘 == 账面，不调库存、不记流水）。 */
    public static final String DIFF_NONE = "none";
}
