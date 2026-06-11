package com.gov.procurement.modules.requisition.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.OffsetDateTime;

/**
 * 出库单（outbound_order）。审批出库成功生成；requisition_id 唯一（uk_ob_req，一领用单一出库单，INV-4）。
 */
@TableName("outbound_order")
public class OutboundOrder {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long requisitionId;
    private Long approverId;
    private OffsetDateTime outboundAt;
    private OffsetDateTime createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getRequisitionId() {
        return requisitionId;
    }

    public void setRequisitionId(Long requisitionId) {
        this.requisitionId = requisitionId;
    }

    public Long getApproverId() {
        return approverId;
    }

    public void setApproverId(Long approverId) {
        this.approverId = approverId;
    }

    public OffsetDateTime getOutboundAt() {
        return outboundAt;
    }

    public void setOutboundAt(OffsetDateTime outboundAt) {
        this.outboundAt = outboundAt;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
