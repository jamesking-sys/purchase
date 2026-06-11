package com.gov.procurement.common;

/**
 * 统一错误码枚举。约定：错误码前三位与 HTTP 状态一致（40001→400、40101/40110→401、40301→403、50000→500），
 * 由 {@link GlobalExceptionHandler} 据此派生响应状态。沿用 CLAUDE.md 的错误码形态，集中维护码与默认文案。
 */
public enum ErrorCode {

    /** 参数校验失败（@Validated / @Valid 触发）。 */
    PARAM_INVALID(40001, "参数校验失败"),
    /** 账号不存在或口令错误（对外统一返回，不泄露账号是否存在）。 */
    LOGIN_FAILED(40101, "账号或口令错误"),
    /** 未登录或登录态已失效。 */
    NOT_LOGIN(40110, "未登录或登录已失效"),
    /** 已登录但缺少所需角色 / 权限。 */
    NO_PERMISSION(40301, "无权限执行该操作"),
    /** 资源不存在（id / 外键指向不存在或已删记录）。 */
    NOT_FOUND(40401, "资源不存在"),
    /** 删除受限：存在子级或被业务引用（RESTRICT）。 */
    DELETE_RESTRICTED(40901, "存在关联数据，不可删除"),
    /** 编码 / 账号在未删范围内重复。 */
    CODE_DUPLICATE(40902, "编码或账号已存在"),
    /** 审批/单据状态冲突：状态不符、任务已处理、重复提交、引擎乐观锁冲突（详设 U7 §8，与 40901/40902 同属 409 段）。 */
    STATE_CONFLICT(40903, "状态冲突，操作不被允许"),
    /** 库存不足，不可超发（出库审批锁内校验，详设 U11 INV-2 / AC-4）。 */
    INSUFFICIENT_STOCK(40904, "库存不足，不可超发"),
    /** 导入模板校验失败（缺列 / 格式 / 必填 / 科目不存在等），data 返回错误行清单。 */
    TEMPLATE_INVALID(42201, "模板校验失败，请修正后重新导入"),
    /** 金额录在非叶子级科目（金额只挂叶子），data 返回错误行清单。 */
    AMOUNT_NOT_LEAF(42202, "金额只能录在叶子级科目"),
    /** 审批驳回未填写意见（驳回意见必填，详设 U7 AC-5）。 */
    REJECT_OPINION_REQUIRED(42203, "驳回意见不能为空"),
    /** 入库累计超收：purchase_item.received_qty + 本次 > qty（详设 U9 AC-3）。 */
    OVER_RECEIVE(42204, "累计超收，本次实收超过采购数量"),
    /** 盘点实盘数为负：actualQty &lt; 0（详设 U12 AC-7；42204 已被 U9 超收占用，归一另取 42205）。 */
    NEGATIVE_ACTUAL_QTY(42205, "实盘数不能为负"),
    /** 系统内部错误（兜底，不外泄堆栈）。 */
    SYSTEM_ERROR(50000, "服务器内部错误");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int code() {
        return code;
    }

    public String message() {
        return message;
    }
}
