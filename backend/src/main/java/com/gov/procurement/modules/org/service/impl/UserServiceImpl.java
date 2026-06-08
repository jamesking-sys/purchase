package com.gov.procurement.modules.org.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gov.procurement.common.BizException;
import com.gov.procurement.common.ErrorCode;
import com.gov.procurement.modules.auth.domain.Role;
import com.gov.procurement.modules.auth.domain.SysUser;
import com.gov.procurement.modules.auth.mapper.RoleMapper;
import com.gov.procurement.modules.auth.mapper.SysUserMapper;
import com.gov.procurement.modules.org.domain.Department;
import com.gov.procurement.modules.org.domain.UserRole;
import com.gov.procurement.modules.org.dto.RoleVO;
import com.gov.procurement.modules.org.dto.SysUserVO;
import com.gov.procurement.modules.org.dto.UserCreateReq;
import com.gov.procurement.modules.org.dto.UserUpdateReq;
import com.gov.procurement.modules.org.mapper.DepartmentMapper;
import com.gov.procurement.modules.org.mapper.UserRoleMapper;
import com.gov.procurement.modules.org.service.UserService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 用户服务实现。复用 U2 的 SysUser/Role 实体与 BCryptPasswordEncoder；用户软删 + 清角色绑定；角色分配全量覆盖（单事务）。
 * 用户列表的角色按用户逐个加载（管理页量级可接受；如需可后续批量优化，详设 TBD-3）。
 */
@Service
public class UserServiceImpl implements UserService {

    private final SysUserMapper sysUserMapper;
    private final DepartmentMapper departmentMapper;
    private final RoleMapper roleMapper;
    private final UserRoleMapper userRoleMapper;
    private final BCryptPasswordEncoder passwordEncoder;

    public UserServiceImpl(SysUserMapper sysUserMapper, DepartmentMapper departmentMapper, RoleMapper roleMapper,
                           UserRoleMapper userRoleMapper, BCryptPasswordEncoder passwordEncoder) {
        this.sysUserMapper = sysUserMapper;
        this.departmentMapper = departmentMapper;
        this.roleMapper = roleMapper;
        this.userRoleMapper = userRoleMapper;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public List<SysUserVO> list(Long departmentId) {
        LambdaQueryWrapper<SysUser> w = new LambdaQueryWrapper<SysUser>().orderByAsc(SysUser::getId);
        if (departmentId != null) {
            w.eq(SysUser::getDepartmentId, departmentId);
        }
        List<SysUser> users = sysUserMapper.selectList(w);
        Map<Long, String> deptNames = loadDepartmentNames(users.stream().map(SysUser::getDepartmentId).toList());
        return users.stream()
                .map(u -> toVO(u, deptNames.get(u.getDepartmentId()), rolesOf(u.getId())))
                .toList();
    }

    @Override
    public SysUserVO get(Long id) {
        SysUser u = requireExisting(id);
        Department dept = departmentMapper.selectById(u.getDepartmentId());
        return toVO(u, dept == null ? null : dept.getName(), rolesOf(id));
    }

    @Override
    @Transactional
    public Long create(UserCreateReq req) {
        requireDepartment(req.departmentId());
        ensureAccountUnique(req.account(), null);
        SysUser u = new SysUser();
        u.setAccount(req.account());
        u.setName(req.name());
        u.setDepartmentId(req.departmentId());
        u.setPasswordHash(passwordEncoder.encode(req.password()));
        sysUserMapper.insert(u);
        return u.getId();
    }

    @Override
    @Transactional
    public void update(Long id, UserUpdateReq req) {
        SysUser u = requireExisting(id);
        requireDepartment(req.departmentId());
        u.setName(req.name());
        u.setDepartmentId(req.departmentId());
        sysUserMapper.updateById(u);
    }

    @Override
    @Transactional
    public void remove(Long id) {
        requireExisting(id);
        userRoleMapper.delete(new LambdaQueryWrapper<UserRole>().eq(UserRole::getUserId, id));
        sysUserMapper.deleteById(id);
    }

    @Override
    public List<RoleVO> getUserRoles(Long id) {
        requireExisting(id);
        return rolesOf(id);
    }

    @Override
    @Transactional
    public List<RoleVO> assignRoles(Long id, List<Long> roleIds) {
        requireExisting(id);
        List<Long> distinct = roleIds.stream().filter(Objects::nonNull).distinct().toList();
        if (!distinct.isEmpty()) {
            // selectBatchIds 含逻辑删除过滤：返回数 < 请求数 即有无效/已删角色
            long valid = roleMapper.selectBatchIds(distinct).size();
            if (valid != distinct.size()) {
                throw new BizException(ErrorCode.NOT_FOUND, "包含不存在的角色");
            }
        }
        userRoleMapper.delete(new LambdaQueryWrapper<UserRole>().eq(UserRole::getUserId, id));
        for (Long roleId : distinct) {
            userRoleMapper.insert(new UserRole(id, roleId));
        }
        return rolesOf(id);
    }

    @Override
    public List<RoleVO> listRoles() {
        return roleMapper.selectList(new LambdaQueryWrapper<Role>().orderByAsc(Role::getId)).stream()
                .map(this::toRoleVO)
                .toList();
    }

    private SysUser requireExisting(Long id) {
        SysUser u = sysUserMapper.selectById(id);
        if (u == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "用户不存在");
        }
        return u;
    }

    private void requireDepartment(Long departmentId) {
        if (departmentMapper.selectById(departmentId) == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "所属部门不存在");
        }
    }

    private void ensureAccountUnique(String account, Long excludeId) {
        LambdaQueryWrapper<SysUser> w = new LambdaQueryWrapper<SysUser>().eq(SysUser::getAccount, account);
        if (excludeId != null) {
            w.ne(SysUser::getId, excludeId);
        }
        if (sysUserMapper.selectCount(w) > 0) {
            throw new BizException(ErrorCode.CODE_DUPLICATE, "账号已存在");
        }
    }

    private List<RoleVO> rolesOf(Long userId) {
        return userRoleMapper.selectRolesByUserId(userId).stream().map(this::toRoleVO).toList();
    }

    private Map<Long, String> loadDepartmentNames(List<Long> departmentIds) {
        List<Long> distinct = departmentIds.stream().filter(Objects::nonNull).distinct().toList();
        if (distinct.isEmpty()) {
            return Map.of();
        }
        return departmentMapper.selectBatchIds(distinct).stream()
                .collect(Collectors.toMap(Department::getId, Department::getName, (a, b) -> a));
    }

    private SysUserVO toVO(SysUser u, String departmentName, List<RoleVO> roles) {
        return new SysUserVO(u.getId(), u.getAccount(), u.getName(), u.getDepartmentId(), departmentName, roles);
    }

    private RoleVO toRoleVO(Role r) {
        return new RoleVO(r.getId(), r.getCode(), r.getName());
    }
}
