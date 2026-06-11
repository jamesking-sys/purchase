package com.gov.procurement.modules.approval.dto;

/**
 * 待办列表的业务信息投影（MyBatis 按流程实例反查 approval/budget/project_group 的结果）。
 * 用类 + setter 承接，依赖 map-underscore-to-camel-case 完成列名→属性映射。
 */
public class TodoBizInfo {

    private Long approvalId;
    private String bizType;
    private Long bizId;
    private String budgetName;
    private String projectGroupName;

    public Long getApprovalId() {
        return approvalId;
    }

    public void setApprovalId(Long approvalId) {
        this.approvalId = approvalId;
    }

    public String getBizType() {
        return bizType;
    }

    public void setBizType(String bizType) {
        this.bizType = bizType;
    }

    public Long getBizId() {
        return bizId;
    }

    public void setBizId(Long bizId) {
        this.bizId = bizId;
    }

    public String getBudgetName() {
        return budgetName;
    }

    public void setBudgetName(String budgetName) {
        this.budgetName = budgetName;
    }

    public String getProjectGroupName() {
        return projectGroupName;
    }

    public void setProjectGroupName(String projectGroupName) {
        this.projectGroupName = projectGroupName;
    }
}
