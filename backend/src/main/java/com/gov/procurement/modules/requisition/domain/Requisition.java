package com.gov.procurement.modules.requisition.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.OffsetDateTime;

/**
 * 领用单（requisition）。领用人发起即 status=pending_warehouse；不软删，用 status 含驳回。
 * 状态单调：pending_warehouse → outbound | rejected（终态不可逆，INV-5）。
 */
@TableName("requisition")
public class Requisition {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectGroupId;
    private Long applicantId;
    private String purpose;
    private String status;
    private String rejectOpinion;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getProjectGroupId() {
        return projectGroupId;
    }

    public void setProjectGroupId(Long projectGroupId) {
        this.projectGroupId = projectGroupId;
    }

    public Long getApplicantId() {
        return applicantId;
    }

    public void setApplicantId(Long applicantId) {
        this.applicantId = applicantId;
    }

    public String getPurpose() {
        return purpose;
    }

    public void setPurpose(String purpose) {
        this.purpose = purpose;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getRejectOpinion() {
        return rejectOpinion;
    }

    public void setRejectOpinion(String rejectOpinion) {
        this.rejectOpinion = rejectOpinion;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
