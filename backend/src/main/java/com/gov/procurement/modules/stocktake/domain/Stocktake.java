package com.gov.procurement.modules.stocktake.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.OffsetDateTime;

/**
 * 盘点单（stocktake）。某项目组某时点账实核对的聚合根，状态单向 counting → confirmed（终态不可逆，INV-4）。
 */
@TableName("stocktake")
public class Stocktake {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long scopeProjectGroupId;
    private String status;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getScopeProjectGroupId() {
        return scopeProjectGroupId;
    }

    public void setScopeProjectGroupId(Long scopeProjectGroupId) {
        this.scopeProjectGroupId = scopeProjectGroupId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
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
