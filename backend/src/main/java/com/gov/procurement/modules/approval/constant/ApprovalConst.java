package com.gov.procurement.modules.approval.constant;

/**
 * 审批模块常量：流程 key、BPMN 节点（taskDefinitionKey）、审批/预算状态、动作、流程变量名、节点序号。
 * 集中定义以杜绝魔法值（编码规范 §2）；状态以字符串持久化（与 budget 模块一致），故用命名常量而非持久化枚举。
 */
public final class ApprovalConst {

    private ApprovalConst() {
    }

    /** 业务类型：预算（本期仅支持）。 */
    public static final String BIZ_BUDGET = "budget";

    /** 流程定义 key（对应 processes/budget_approval.bpmn20.xml）。 */
    public static final String FLOW_BUDGET = "budget_approval";

    /** BPMN UserTask 的 taskDefinitionKey 前缀（节点 code = key 去前缀）。 */
    public static final String TASK_KEY_PREFIX = "node_";
    /** 采购主管节点 taskDefinitionKey。 */
    public static final String TASK_KEY_PURCHASE_MGR = "node_purchase_mgr";
    /** 部门主管节点 taskDefinitionKey。 */
    public static final String TASK_KEY_DEPT_MGR = "node_dept_mgr";

    /** 节点 / candidateGroup code：采购主管。 */
    public static final String NODE_PURCHASE_MGR = "purchase_mgr";
    /** 节点 / candidateGroup code：部门主管。 */
    public static final String NODE_DEPT_MGR = "dept_mgr";

    /** 节点序号：采购主管（节点一）。 */
    public static final int SEQ_PURCHASE_MGR = 1;
    /** 节点序号：部门主管（节点二）。 */
    public static final int SEQ_DEPT_MGR = 2;

    /** approval.status：编制态（创建 / 驳回退回）。 */
    public static final String ST_DRAFT = "draft";
    /** approval.status：待采购主管审批。 */
    public static final String ST_PENDING_PURCHASE_MGR = "pending_purchase_mgr";
    /** approval.status：待部门主管审批。 */
    public static final String ST_PENDING_DEPT_MGR = "pending_dept_mgr";
    /** approval.status：终审通过。 */
    public static final String ST_APPROVED = "approved";

    /** budget.status：编制态。 */
    public static final String BUDGET_DRAFT = "draft";
    /** budget.status：审批中。 */
    public static final String BUDGET_SUBMITTED = "submitted";
    /** budget.status：审批通过（放行采购 U8）。 */
    public static final String BUDGET_APPROVED = "approved";

    /** 审批动作：通过。 */
    public static final String ACTION_APPROVE = "approve";
    /** 审批动作：驳回。 */
    public static final String ACTION_REJECT = "reject";

    /** 流程变量：业务投影 approval 主键。 */
    public static final String VAR_APPROVAL_ID = "approvalId";
    /** 流程变量：业务单据 id。 */
    public static final String VAR_BIZ_ID = "bizId";
    /** 流程变量：本次处理动作。 */
    public static final String VAR_ACTION = "action";
    /** 流程变量：本次处理人用户 id。 */
    public static final String VAR_APPROVER_ID = "approverId";
}
