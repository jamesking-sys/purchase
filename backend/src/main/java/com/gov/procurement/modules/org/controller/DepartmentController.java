package com.gov.procurement.modules.org.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import com.gov.procurement.common.Result;
import com.gov.procurement.modules.org.dto.DepartmentReq;
import com.gov.procurement.modules.org.dto.DepartmentVO;
import com.gov.procurement.modules.org.service.DepartmentService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 部门管理接口。查询需登录态；增删改需 admin 角色。
 */
@RestController
@RequestMapping("/api/org/departments")
public class DepartmentController {

    private final DepartmentService departmentService;

    public DepartmentController(DepartmentService departmentService) {
        this.departmentService = departmentService;
    }

    @GetMapping
    public Result<List<DepartmentVO>> list() {
        return Result.ok(departmentService.list());
    }

    @GetMapping("/{id}")
    public Result<DepartmentVO> get(@PathVariable Long id) {
        return Result.ok(departmentService.get(id));
    }

    @SaCheckRole("admin")
    @PostMapping
    public Result<Long> create(@RequestBody @Valid DepartmentReq req) {
        return Result.ok(departmentService.create(req));
    }

    @SaCheckRole("admin")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody @Valid DepartmentReq req) {
        departmentService.update(id, req);
        return Result.ok();
    }

    @SaCheckRole("admin")
    @DeleteMapping("/{id}")
    public Result<Void> remove(@PathVariable Long id) {
        departmentService.remove(id);
        return Result.ok();
    }
}
