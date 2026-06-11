package com.gov.procurement.modules.inbound.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.OffsetDateTime;

/**
 * 入库单（inbound_order）。一次验收入库生成一张，记录验收人与入库时间；同一采购单可多张（多次到货）。
 */
@TableName("inbound_order")
public class InboundOrder {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long purchaseOrderId;
    private Long receivedBy;
    private OffsetDateTime inboundAt;
    private OffsetDateTime createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getPurchaseOrderId() {
        return purchaseOrderId;
    }

    public void setPurchaseOrderId(Long purchaseOrderId) {
        this.purchaseOrderId = purchaseOrderId;
    }

    public Long getReceivedBy() {
        return receivedBy;
    }

    public void setReceivedBy(Long receivedBy) {
        this.receivedBy = receivedBy;
    }

    public OffsetDateTime getInboundAt() {
        return inboundAt;
    }

    public void setInboundAt(OffsetDateTime inboundAt) {
        this.inboundAt = inboundAt;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
