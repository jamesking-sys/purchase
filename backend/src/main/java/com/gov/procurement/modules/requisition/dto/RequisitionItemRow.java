package com.gov.procurement.modules.requisition.dto;

import java.math.BigDecimal;

/**
 * 领用明细查询投影（关联库存项物料名与当前库存）。用类 + setter 承接，列名→属性靠 map-underscore-to-camel-case。
 */
public class RequisitionItemRow {

    private Long stockItemId;
    private String materialName;
    private BigDecimal qty;
    private BigDecimal currentQuantity;

    public Long getStockItemId() {
        return stockItemId;
    }

    public void setStockItemId(Long stockItemId) {
        this.stockItemId = stockItemId;
    }

    public String getMaterialName() {
        return materialName;
    }

    public void setMaterialName(String materialName) {
        this.materialName = materialName;
    }

    public BigDecimal getQty() {
        return qty;
    }

    public void setQty(BigDecimal qty) {
        this.qty = qty;
    }

    public BigDecimal getCurrentQuantity() {
        return currentQuantity;
    }

    public void setCurrentQuantity(BigDecimal currentQuantity) {
        this.currentQuantity = currentQuantity;
    }
}
