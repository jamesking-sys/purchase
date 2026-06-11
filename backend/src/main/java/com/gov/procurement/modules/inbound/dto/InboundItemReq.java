package com.gov.procurement.modules.inbound.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * 本次实收明细行。
 *
 * @param purchaseItemId 对应采购明细 id（须属本采购单）
 * @param receivedQty    本次实收数量（> 0）
 */
public record InboundItemReq(
        @NotNull(message = "采购明细 id 不能为空") Long purchaseItemId,
        @NotNull(message = "本次实收不能为空") @DecimalMin(value = "0.001", message = "本次实收须大于 0") BigDecimal receivedQty) {
}
