package com.gov.procurement.modules.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 登录请求体。account/password 均必填，由 @Valid 触发非空校验（失败→40001）。
 *
 * @param account  登录账号
 * @param password 明文口令（经 HTTPS 传输，服务端 BCrypt 比对哈希）
 */
public record LoginReq(
        @NotBlank(message = "账号不能为空") String account,
        @NotBlank(message = "口令不能为空") String password) {
}
