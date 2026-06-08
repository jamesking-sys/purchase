package com.gov.procurement.modules.org.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 项目组创建 / 更新请求。
 *
 * @param name         项目组名称（必填，≤128）
 * @param code         项目组编码（必填，≤64，未删唯一）
 * @param departmentId 所属部门 id（必填，须指向存在未删部门）
 */
public record ProjectGroupReq(
        @NotBlank(message = "项目组名称不能为空") @Size(max = 128, message = "项目组名称过长") String name,
        @NotBlank(message = "项目组编码不能为空") @Size(max = 64, message = "项目组编码过长") String code,
        @NotNull(message = "所属部门不能为空") Long departmentId) {
}
