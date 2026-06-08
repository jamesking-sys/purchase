package com.gov.procurement.modules.auth.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.gov.procurement.common.Result;
import com.gov.procurement.modules.auth.dto.LoginReq;
import com.gov.procurement.modules.auth.dto.LoginResp;
import com.gov.procurement.modules.auth.dto.MeResp;
import com.gov.procurement.modules.auth.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证接口：登录 / 登出 / 当前用户。统一前缀 /api/auth。
 * 仅 /login 在 Sa-Token 白名单内免登录；/logout、/me 需登录态。
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /** 登录：账号 + 口令换取 token。 */
    @PostMapping("/login")
    public Result<LoginResp> login(@RequestBody @Valid LoginReq req) {
        return Result.ok(authService.login(req.account(), req.password()));
    }

    /** 登出：注销当前 token 对应登录态。 */
    @PostMapping("/logout")
    public Result<Void> logout() {
        StpUtil.logout();
        return Result.ok();
    }

    /** 当前登录用户及其角色 code 列表。 */
    @GetMapping("/me")
    public Result<MeResp> me() {
        return Result.ok(authService.currentUser());
    }
}
