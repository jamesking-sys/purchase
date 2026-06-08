package com.gov.procurement.modules.org.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 用户更新请求。仅改姓名与所属部门，不改账号 / 口令（口令重置见详设 TBD-2）。
 *
 * @param name         姓名（必填，≤64）
 * @param departmentId 所属部门 id（必填，须存在未删部门）
 */
public record UserUpdateReq(
        @NotBlank(message = "姓名不能为空") @Size(max = 64, message = "姓名过长") String name,
        @NotNull(message = "所属部门不能为空") Long departmentId) {
}
