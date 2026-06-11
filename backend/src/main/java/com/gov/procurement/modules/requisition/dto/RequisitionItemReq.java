package com.gov.procurement.modules.requisition.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * 领用明细行请求。
 *
 * @param stockItemId 库存项 id
 * @param qty         申请数量（> 0）
 */
public record RequisitionItemReq(
        @NotNull(message = "库存项 id 不能为空") Long stockItemId,
        @NotNull(message = "数量不能为空") @DecimalMin(value = "0.001", message = "数量须大于 0") BigDecimal qty) {
}
