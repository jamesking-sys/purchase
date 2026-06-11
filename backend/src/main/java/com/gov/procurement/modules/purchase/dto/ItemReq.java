package com.gov.procurement.modules.purchase.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * 采购明细行请求。subject_id 须命中已有叶子级预算科目（业务层校验）；金额/数量用 BigDecimal。
 *
 * @param subjectId    预算科目 id（非空）
 * @param materialName 物料名称（非空、≤128）
 * @param qty          采购数量（>0）
 * @param amount       金额（≥0）
 */
public record ItemReq(
        @NotNull(message = "科目 id 不能为空") Long subjectId,
        @NotBlank(message = "物料名称不能为空") @Size(max = 128, message = "物料名称不超过 128 字") String materialName,
        @NotNull(message = "数量不能为空") @DecimalMin(value = "0.001", message = "数量须大于 0") BigDecimal qty,
        @NotNull(message = "金额不能为空") @DecimalMin(value = "0.00", message = "金额不能为负") BigDecimal amount) {
}
