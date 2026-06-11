package com.gov.procurement.modules.stock.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 库存流水（stock_txn）。事件表（不软删），库存增减的天然审计与对账依据：quantity == Σ qty_change（INV-3）。
 */
@TableName("stock_txn")
public class StockTxn {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long stockItemId;
    private String type;
    private BigDecimal qtyChange;
    private String refType;
    private Long refId;
    private OffsetDateTime createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getStockItemId() {
        return stockItemId;
    }

    public void setStockItemId(Long stockItemId) {
        this.stockItemId = stockItemId;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public BigDecimal getQtyChange() {
        return qtyChange;
    }

    public void setQtyChange(BigDecimal qtyChange) {
        this.qtyChange = qtyChange;
    }

    public String getRefType() {
        return refType;
    }

    public void setRefType(String refType) {
        this.refType = refType;
    }

    public Long getRefId() {
        return refId;
    }

    public void setRefId(Long refId) {
        this.refId = refId;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
