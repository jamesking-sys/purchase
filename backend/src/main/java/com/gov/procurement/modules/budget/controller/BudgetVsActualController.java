package com.gov.procurement.modules.budget.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.annotation.SaMode;
import com.gov.procurement.common.Result;
import com.gov.procurement.modules.budget.dto.BudgetVsActualVO;
import com.gov.procurement.modules.budget.service.BudgetVsActualService;
import jakarta.validation.constraints.Positive;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 预算 vs 实际接口（U13）：只读查看，开放给编制/采购主管/部门主管/管理员（OR）。超支仅展示标识，不拦截。
 */
@RestController
@RequestMapping("/api/budgets")
@Validated
public class BudgetVsActualController {

    private final BudgetVsActualService budgetVsActualService;

    public BudgetVsActualController(BudgetVsActualService budgetVsActualService) {
        this.budgetVsActualService = budgetVsActualService;
    }

    @SaCheckRole(value = {"editor", "purchase_mgr", "dept_mgr", "admin"}, mode = SaMode.OR)
    @GetMapping("/{id}/vs-actual")
    public Result<BudgetVsActualVO> vsActual(@PathVariable @Positive(message = "预算 id 须为正整数") Long id) {
        return Result.ok(budgetVsActualService.getVsActual(id));
    }
}
