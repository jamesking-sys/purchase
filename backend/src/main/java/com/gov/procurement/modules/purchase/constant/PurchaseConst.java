package com.gov.procurement.modules.purchase.constant;

import java.util.Set;

/**
 * 采购模块常量：来源预算放行状态、采购单状态机取值、到货单下载地址模板与文件约束。
 * 状态以字符串持久化（与 DB CHECK 约束一致），集中命名以杜绝魔法值。
 */
public final class PurchaseConst {

    private PurchaseConst() {
    }

    /** 来源预算须为该状态方可执行采购（M2 budget.status）。 */
    public static final String BUDGET_APPROVED = "approved";

    /** purchase_order.status：执行中（创建即此态）。 */
    public static final String ST_EXECUTING = "executing";
    /** purchase_order.status：已入库（U9 累计已收=采购量时推进）。 */
    public static final String ST_INBOUNDED = "inbounded";
    /** purchase_order.status：作废（预留）。 */
    public static final String ST_VOID = "void";

    /** 到货单下载地址模板（%d=delivery_note.id）。 */
    public static final String DOWNLOAD_URL_TEMPLATE = "/api/purchase/delivery-notes/%d/download";

    /** 到货单子目录前缀（%d=purchase_order.id）。 */
    public static final String DELIVERY_SUBDIR_TEMPLATE = "purchase/%d/delivery";

    /** 单个到货单文件大小上限：20MB。 */
    public static final long MAX_FILE_BYTES = 20L * 1024 * 1024;

    /** 到货单允许的文件扩展名（小写）。 */
    public static final Set<String> ALLOWED_EXTENSIONS =
            Set.of("pdf", "doc", "docx", "xls", "xlsx", "png", "jpg", "jpeg");
}
