package com.gov.procurement.modules.org.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import com.gov.procurement.common.Result;
import com.gov.procurement.modules.org.dto.ProjectGroupReq;
import com.gov.procurement.modules.org.dto.ProjectGroupVO;
import com.gov.procurement.modules.org.service.ProjectGroupService;
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
 * 项目组管理接口。查询需登录态；增删改需 admin 角色。
 */
@RestController
@RequestMapping("/api/org/project-groups")
public class ProjectGroupController {

    private final ProjectGroupService projectGroupService;

    public ProjectGroupController(ProjectGroupService projectGroupService) {
        this.projectGroupService = projectGroupService;
    }

    @GetMapping
    public Result<List<ProjectGroupVO>> list(@RequestParam(required = false) Long departmentId) {
        return Result.ok(projectGroupService.list(departmentId));
    }

    @GetMapping("/{id}")
    public Result<ProjectGroupVO> get(@PathVariable Long id) {
        return Result.ok(projectGroupService.get(id));
    }

    @SaCheckRole("admin")
    @PostMapping
    public Result<Long> create(@RequestBody @Valid ProjectGroupReq req) {
        return Result.ok(projectGroupService.create(req));
    }

    @SaCheckRole("admin")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody @Valid ProjectGroupReq req) {
        projectGroupService.update(id, req);
        return Result.ok();
    }

    @SaCheckRole("admin")
    @DeleteMapping("/{id}")
    public Result<Void> remove(@PathVariable Long id) {
        projectGroupService.remove(id);
        return Result.ok();
    }
}
