package com.gov.procurement.modules.org.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import com.gov.procurement.common.Result;
import com.gov.procurement.modules.org.dto.AssignRolesReq;
import com.gov.procurement.modules.org.dto.RoleVO;
import com.gov.procurement.modules.org.dto.SysUserVO;
import com.gov.procurement.modules.org.dto.UserCreateReq;
import com.gov.procurement.modules.org.dto.UserUpdateReq;
import com.gov.procurement.modules.org.service.UserService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 用户管理与用户↔角色分配接口。查询需登录态；增删改与授角需 admin 角色。
 */
@RestController
@RequestMapping("/api/org/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public Result<List<SysUserVO>> list(@RequestParam(required = false) Long departmentId) {
        return Result.ok(userService.list(departmentId));
    }

    @GetMapping("/{id}")
    public Result<SysUserVO> get(@PathVariable Long id) {
        return Result.ok(userService.get(id));
    }

    @SaCheckRole("admin")
    @PostMapping
    public Result<Long> create(@RequestBody @Valid UserCreateReq req) {
        return Result.ok(userService.create(req));
    }

    @SaCheckRole("admin")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody @Valid UserUpdateReq req) {
        userService.update(id, req);
        return Result.ok();
    }

    @SaCheckRole("admin")
    @DeleteMapping("/{id}")
    public Result<Void> remove(@PathVariable Long id) {
        userService.remove(id);
        return Result.ok();
    }

    @GetMapping("/{id}/roles")
    public Result<List<RoleVO>> getRoles(@PathVariable Long id) {
        return Result.ok(userService.getUserRoles(id));
    }

    @SaCheckRole("admin")
    @PutMapping("/{id}/roles")
    public Result<List<RoleVO>> assignRoles(@PathVariable Long id, @RequestBody @Valid AssignRolesReq req) {
        return Result.ok(userService.assignRoles(id, req.roleIds()));
    }
}
