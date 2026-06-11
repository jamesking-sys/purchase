package com.gov.procurement.modules.requisition.constant;

/**
 * 领用/出库模块常量：领用单状态机取值（对齐 DB requisition.status 的 CHECK）。
 */
public final class RequisitionConst {

    private RequisitionConst() {
    }

    /** 待仓管审批出库（创建即此态）。 */
    public static final String ST_PENDING_WAREHOUSE = "pending_warehouse";
    /** 已出库（终态，审批出库成功）。 */
    public static final String ST_OUTBOUND = "outbound";
    /** 已驳回（终态）。 */
    public static final String ST_REJECTED = "rejected";
}
