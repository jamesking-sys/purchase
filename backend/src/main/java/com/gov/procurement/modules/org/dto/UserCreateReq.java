package com.gov.procurement.modules.org.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 用户创建请求。初始口令为明文初值，服务端经 BCrypt 编码入库，明文不落库。
 *
 * @param account      登录账号（必填，≤64，未删唯一）
 * @param name         姓名（必填，≤64）
 * @param password     初始口令明文（必填）
 * @param departmentId 所属部门 id（必填，须存在未删部门）
 */
public record UserCreateReq(
        @NotBlank(message = "账号不能为空") @Size(max = 64, message = "账号过长") String account,
        @NotBlank(message = "姓名不能为空") @Size(max = 64, message = "姓名过长") String name,
        @NotBlank(message = "初始口令不能为空") String password,
        @NotNull(message = "所属部门不能为空") Long departmentId) {
}
