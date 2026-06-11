package com.gov.procurement.modules.requisition.dto;

import java.math.BigDecimal;

/**
 * 出库明细查询投影（关联库存项物料名）。用类 + setter 承接，列名→属性靠 map-underscore-to-camel-case。
 */
public class OutboundItemRow {

    private Long stockItemId;
    private String materialName;
    private BigDecimal qty;

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
}
