package com.gov.procurement.modules.org.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 部门创建 / 更新请求。
 *
 * @param name 部门名称（必填，≤128）
 * @param code 部门编码（必填，≤64，未删唯一）
 */
public record DepartmentReq(
        @NotBlank(message = "部门名称不能为空") @Size(max = 128, message = "部门名称过长") String name,
        @NotBlank(message = "部门编码不能为空") @Size(max = 64, message = "部门编码过长") String code) {
}
