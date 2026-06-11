package com.gov.procurement.modules.stock.constant;

/**
 * 库存模块常量：流水类型与来源类型（对齐 DB stock_txn 的 CHECK 与 ref_type 语义）。
 * 库存项是 BC5 的唯一 SoR，所有增减经 stock_txn 流水（CLAUDE.md / general §7）。
 */
public final class StockConst {

    private StockConst() {
    }

    /** 流水类型：入库（+）。 */
    public static final String TXN_INBOUND = "inbound";

    /** 流水类型：出库（-）。 */
    public static final String TXN_OUTBOUND = "outbound";

    /** 流水类型：盘盈（盘点实盘 > 账面，+）。 */
    public static final String TXN_GAIN = "gain";

    /** 流水类型：盘亏（盘点实盘 < 账面，-）。 */
    public static final String TXN_LOSS = "loss";

    /** 来源类型：入库单。 */
    public static final String REF_INBOUND_ORDER = "inbound_order";

    /** 来源类型：出库单。 */
    public static final String REF_OUTBOUND_ORDER = "outbound_order";

    /** 来源类型：盘点单（盘盈/盘亏校正记账）。 */
    public static final String REF_STOCKTAKE = "stocktake";
}
