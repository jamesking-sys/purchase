package com.gov.procurement.modules.purchase.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 创建采购单请求（含明细）。供应商/合同号为可选轻量字段，不阻断。
 *
 * @param budgetId       来源预算 id（非空、须 approved）
 * @param projectGroupId 归属项目组 id（非空）
 * @param supplierName   供应商名称（可选，≤128）
 * @param contractNo     合同号（可选，≤64）
 * @param items          采购明细（≥1 行）
 */
public record CreatePurchaseOrderReq(
        @NotNull(message = "来源预算 id 不能为空") Long budgetId,
        @NotNull(message = "项目组 id 不能为空") Long projectGroupId,
        @Size(max = 128, message = "供应商名称不超过 128 字") String supplierName,
        @Size(max = 64, message = "合同号不超过 64 字") String contractNo,
        @NotEmpty(message = "采购明细不能为空") @Valid List<ItemReq> items) {
}
