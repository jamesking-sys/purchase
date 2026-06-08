package com.gov.procurement.modules.auth.service;

import com.gov.procurement.modules.auth.dto.LoginResp;
import com.gov.procurement.modules.auth.dto.MeResp;

/**
 * 认证服务：登录校验与当前用户加载。登录态由 Sa-Token 维护。
 */
public interface AuthService {

    /**
     * 账号 + 口令登录，成功则建立 Sa-Token 会话并返回 token。
     *
     * @param account     登录账号
     * @param rawPassword 明文口令
     * @return token 与用户 id
     * @throws com.gov.procurement.common.BizException 账号不存在或口令错误（40101）
     */
    LoginResp login(String account, String rawPassword);

    /**
     * 加载当前登录用户信息及其角色 code 列表。
     *
     * @return 当前用户与角色（不含口令）
     */
    MeResp currentUser();
}
