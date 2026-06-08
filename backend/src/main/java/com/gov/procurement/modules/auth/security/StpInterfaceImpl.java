package com.gov.procurement.modules.auth.security;

import cn.dev33.satoken.stp.StpInterface;
import com.gov.procurement.modules.auth.mapper.RoleMapper;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * Sa-Token 角色 / 权限数据源实现。轻量 RBAC：仅提供角色 code 列表，不使用细粒度权限点。
 * {@code @SaCheckRole} 与 {@code StpUtil.getRoleList()} 均经此回调。
 */
@Component
public class StpInterfaceImpl implements StpInterface {

    private final RoleMapper roleMapper;

    public StpInterfaceImpl(RoleMapper roleMapper) {
        this.roleMapper = roleMapper;
    }

    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        Long userId = Long.valueOf(loginId.toString());
        return roleMapper.selectRoleCodesByUserId(userId);
    }

    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        return Collections.emptyList();
    }
}
