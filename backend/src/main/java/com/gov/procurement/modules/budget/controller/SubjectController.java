package com.gov.procurement.modules.budget.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import com.gov.procurement.common.Result;
import com.gov.procurement.modules.budget.dto.AddSubjectReq;
import com.gov.procurement.modules.budget.dto.CompareReq;
import com.gov.procurement.modules.budget.dto.CompareResult;
import com.gov.procurement.modules.budget.dto.ConfirmAddReq;
import com.gov.procurement.modules.budget.dto.SubjectHit;
import com.gov.procurement.modules.budget.dto.SubjectTreeNode;
import com.gov.procurement.modules.budget.service.BudgetSubjectService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 预算科目树接口。查询（树/搜索/比对）需登录态；写操作（新增子级/确认新增/删除）需 editor 角色。
 */
@RestController
@RequestMapping("/api/subjects")
public class SubjectController {

    private final BudgetSubjectService budgetSubjectService;

    public SubjectController(BudgetSubjectService budgetSubjectService) {
        this.budgetSubjectService = budgetSubjectService;
    }

    @GetMapping("/tree")
    public Result<List<SubjectTreeNode>> tree(@RequestParam(required = false) Long budgetId,
                                              @RequestParam(required = false, defaultValue = "false") boolean lazy,
                                              @RequestParam(required = false) Long parentId) {
        return Result.ok(budgetSubjectService.tree(budgetId, lazy, parentId));
    }

    @GetMapping("/search")
    public Result<List<SubjectHit>> search(@RequestParam(required = false) String keyword,
                                           @RequestParam(required = false) Integer limit) {
        return Result.ok(budgetSubjectService.search(keyword, limit));
    }

    @SaCheckRole("editor")
    @PostMapping
    public Result<Long> addChild(@RequestBody @Valid AddSubjectReq req) {
        return Result.ok(budgetSubjectService.addChild(req.parentId(), req.name(), req.code()));
    }

    @PostMapping("/compare")
    public Result<List<CompareResult>> compare(@RequestBody @Valid CompareReq req) {
        return Result.ok(budgetSubjectService.compare(req.paths()));
    }

    @SaCheckRole("editor")
    @PostMapping("/confirm-add")
    public Result<List<Long>> confirmAdd(@RequestBody @Valid ConfirmAddReq req) {
        return Result.ok(budgetSubjectService.confirmAdd(req.items()));
    }

    @SaCheckRole("editor")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        budgetSubjectService.delete(id);
        return Result.ok();
    }
}
