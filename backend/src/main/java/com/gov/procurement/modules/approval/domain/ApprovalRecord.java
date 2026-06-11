package com.gov.procurement.modules.approval.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.OffsetDateTime;

/**
 * 审批流转记录（approval_record）。每次通过/驳回写一条，按 approval_id 累积，含多轮重提全量历史（详设 U7 AC-10）。
 */
@TableName("approval_record")
public class ApprovalRecord {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long approvalId;
    private Integer nodeSeq;
    private String node;
    private String taskId;
    private Long approverId;
    private String action;
    private String opinion;
    private OffsetDateTime actedAt;
    private OffsetDateTime createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getApprovalId() {
        return approvalId;
    }

    public void setApprovalId(Long approvalId) {
        this.approvalId = approvalId;
    }

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

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public Long getApproverId() {
        return approverId;
    }

    public void setApproverId(Long approverId) {
        this.approverId = approverId;
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

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
