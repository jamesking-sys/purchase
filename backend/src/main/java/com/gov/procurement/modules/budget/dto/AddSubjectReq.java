package com.gov.procurement.modules.budget.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 新增科目请求。{@code parentId} 为空表示新建根科目（level=1）；非空表示在该父节点下挂子级。
 *
 * @param parentId 父科目 id（可空=根科目）
 * @param name     科目名称（必填，≤128）
 * @param code     科目编码（必填，≤64，未删唯一）
 */
public record AddSubjectReq(
        Long parentId,
        @NotBlank(message = "科目名称不能为空") @Size(max = 128, message = "科目名称过长") String name,
        @NotBlank(message = "科目编码不能为空") @Size(max = 64, message = "科目编码过长") String code) {
}
