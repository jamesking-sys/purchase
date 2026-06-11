package com.gov.procurement.modules.stocktake.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 盘点明细（stocktake_item）。account-quantity 快照与实盘录入：book_qty 为发起时点账面快照（不随库存刷新），
 * actual_qty 为实盘数；diff == actual_qty - book_qty 且 diff_type 与 diff 符号一致（INV-1）。
 */
@TableName("stocktake_item")
public class StocktakeItem {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long stocktakeId;
    private Long stockItemId;
    private BigDecimal bookQty;
    private BigDecimal actualQty;
    private BigDecimal diff;
    private String diffType;
    private OffsetDateTime createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getStocktakeId() {
        return stocktakeId;
    }

    public void setStocktakeId(Long stocktakeId) {
        this.stocktakeId = stocktakeId;
    }

    public Long getStockItemId() {
        return stockItemId;
    }

    public void setStockItemId(Long stockItemId) {
        this.stockItemId = stockItemId;
    }

    public BigDecimal getBookQty() {
        return bookQty;
    }

    public void setBookQty(BigDecimal bookQty) {
        this.bookQty = bookQty;
    }

    public BigDecimal getActualQty() {
        return actualQty;
    }

    public void setActualQty(BigDecimal actualQty) {
        this.actualQty = actualQty;
    }

    public BigDecimal getDiff() {
        return diff;
    }

    public void setDiff(BigDecimal diff) {
        this.diff = diff;
    }

    public String getDiffType() {
        return diffType;
    }

    public void setDiffType(String diffType) {
        this.diffType = diffType;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
