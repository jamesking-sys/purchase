package com.gov.procurement.modules.approval.dto;

import java.time.OffsetDateTime;

/**
 * 流转历史项视图（详设 U7 §6.5）。MyBatis 按列名→属性映射填充，故用类 + setter 而非 record。
 */
public class HistoryItemVO {

    private Integer nodeSeq;
    private String node;
    private String approverName;
    private String action;
    private String opinion;
    private OffsetDateTime actedAt;

    public Integer getNodeSeq() {
        return nodeSeq;
    }

    public void setNodeSeq(Integer nodeSeq) {
        this.nodeSeq = nodeSeq;
    }

    public String getNode() {
        return node;
    }

    public void setNode(String node) {
        this.node = node;
    }

    public String getApproverName() {
        return approverName;
    }

    public void setApproverName(String approverName) {
        this.approverName = approverName;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getOpinion() {
        return opinion;
    }

    public void setOpinion(String opinion) {
        this.opinion = opinion;
    }

    public OffsetDateTime getActedAt() {
        return actedAt;
    }

    public void setActedAt(OffsetDateTime actedAt) {
        this.actedAt = actedAt;
    }
}
