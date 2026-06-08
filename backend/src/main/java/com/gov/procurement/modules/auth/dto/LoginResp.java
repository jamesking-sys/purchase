package com.gov.procurement.modules.auth.dto;

/**
 * 登录响应：Sa-Token token 与登录用户 id。
 *
 * @param token  Sa-Token tokenValue，后续请求经 Authorization 头携带
 * @param userId 登录用户 id
 */
public record LoginResp(String token, Long userId) {
}
