package com.gov.procurement.modules.auth.service;

import com.gov.procurement.common.BizException;
import com.gov.procurement.common.ErrorCode;
import com.gov.procurement.modules.auth.domain.SysUser;
import com.gov.procurement.modules.auth.mapper.SysUserMapper;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AuthServiceImpl 登录失败路径单测（纯单元、不触达 StpUtil 静态会话，无 Docker，可重复）。
 * 覆盖详设 §7：T-2（错误口令）、T-3（账号不存在）、T-10（BCrypt 比对），均返回 40101。
 */
class AuthServiceImplTest {

    private final SysUserMapper sysUserMapper = mock(SysUserMapper.class);
    private final BCryptPasswordEncoder passwordEncoder = mock(BCryptPasswordEncoder.class);
    private final AuthServiceImpl authService = new AuthServiceImpl(sysUserMapper, passwordEncoder);

    @Test
    void login_wrongPassword_throwsLoginFailed() {
        SysUser user = new SysUser();
        user.setId(1L);
        user.setAccount("admin");
        user.setPasswordHash("$2a$10$irrelevanthashvalueforunittestxxxxxxxxxxxxxxxxxxxxxxxx");
        when(sysUserMapper.selectOne(any())).thenReturn(user);
        when(passwordEncoder.matches(eq("wrong"), any())).thenReturn(false);

        BizException ex = assertThrows(BizException.class, () -> authService.login("admin", "wrong"));
        assertEquals(ErrorCode.LOGIN_FAILED.code(), ex.getCode());
    }

    @Test
    void login_accountNotFound_throwsLoginFailedAndKeepsConstantTime() {
        when(sysUserMapper.selectOne(any())).thenReturn(null);

        BizException ex = assertThrows(BizException.class, () -> authService.login("ghost", "pw"));
        assertEquals(ErrorCode.LOGIN_FAILED.code(), ex.getCode());
        // INV-4 恒定时延：账号不存在也执行一次比对
        verify(passwordEncoder).matches(eq("pw"), any());
    }
}
