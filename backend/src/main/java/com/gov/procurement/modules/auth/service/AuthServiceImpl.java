package com.gov.procurement.modules.auth.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gov.procurement.common.BizException;
import com.gov.procurement.common.ErrorCode;
import com.gov.procurement.modules.auth.domain.SysUser;
import com.gov.procurement.modules.auth.dto.LoginResp;
import com.gov.procurement.modules.auth.dto.MeResp;
import com.gov.procurement.modules.auth.mapper.SysUserMapper;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 认证服务实现：BCrypt 口令校验 + Sa-Token 登录态。仅只读 sys_user（逻辑删除自动过滤 is_deleted=0）。
 */
@Service
public class AuthServiceImpl implements AuthService {

    /**
     * 账号不存在时用于「恒定时延」比对的占位 BCrypt 哈希，避免通过响应耗时侧信道探测账号存在性（INV-4）。
     * 为一段合法 BCrypt 串，永不匹配任何真实口令。
     */
    private static final String DUMMY_HASH =
            "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";

    private final SysUserMapper sysUserMapper;
    private final BCryptPasswordEncoder passwordEncoder;

    public AuthServiceImpl(SysUserMapper sysUserMapper, BCryptPasswordEncoder passwordEncoder) {
        this.sysUserMapper = sysUserMapper;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public LoginResp login(String account, String rawPassword) {
        SysUser user = sysUserMapper.selectOne(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getAccount, account));
        if (user == null) {
            // 账号不存在也执行一次比对以抹平时延，再统一报错，不泄露账号存在性（INV-4）
            passwordEncoder.matches(rawPassword, DUMMY_HASH);
            throw new BizException(ErrorCode.LOGIN_FAILED);
        }
        if (!passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            throw new BizException(ErrorCode.LOGIN_FAILED);
        }
        StpUtil.login(user.getId());
        return new LoginResp(StpUtil.getTokenValue(), user.getId());
    }

    @Override
    public MeResp currentUser() {
        long userId = StpUtil.getLoginIdAsLong();
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            // 登录后用户被软删/不存在，视为登录态失效
            throw new BizException(ErrorCode.NOT_LOGIN);
        }
        List<String> roles = StpUtil.getRoleList();
        return new MeResp(user.getId(), user.getAccount(), user.getName(),
                user.getDepartmentId(), roles);
    }
}
