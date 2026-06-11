package com.gov.procurement.modules.inbound.dto;

import java.math.BigDecimal;

/**
 * 入库明细查询投影（MyBatis 关联物料名的结果）。用类 + setter 承接，列名→属性靠 map-underscore-to-camel-case。
 */
public class InboundItemRow {

    private Long inboundOrderId;
    private Long purchaseItemId;
    private String materialName;
    private BigDecimal receivedQty;
    private Long stockItemId;

    public Long getInboundOrderId() {
        return inboundOrderId;
    }

    public void setInboundOrderId(Long inboundOrderId) {
        this.inboundOrderId = inboundOrderId;
    }

    public Long getPurchaseItemId() {
        return purchaseItemId;
    }

    public void setPurchaseItemId(Long purchaseItemId) {
        this.purchaseItemId = purchaseItemId;
    }

    public String getMaterialName() {
        return materialName;
    }

    public void setMaterialName(String materialName) {
        this.materialName = materialName;
    }

    public BigDecimal getReceivedQty() {
        return receivedQty;
    }

    public void setReceivedQty(BigDecimal receivedQty) {
        this.receivedQty = receivedQty;
    }

    public Long getStockItemId() {
        return stockItemId;
    }

    public void setStockItemId(Long stockItemId) {
        this.stockItemId = stockItemId;
    }
}
