package com.gov.procurement.modules.org.controller;

import com.gov.procurement.common.Result;
import com.gov.procurement.modules.org.dto.RoleVO;
import com.gov.procurement.modules.org.service.UserService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 角色字典接口（只读）：供用户授角 UI 下拉。不提供角色增删改（角色由 seed 初始化）。
 */
@RestController
@RequestMapping("/api/org/roles")
public class RoleController {

    private final UserService userService;

    public RoleController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public Result<List<RoleVO>> list() {
        return Result.ok(userService.listRoles());
    }
}
