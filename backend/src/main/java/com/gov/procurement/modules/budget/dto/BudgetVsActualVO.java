package com.gov.procurement.modules.budget.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * 预算 vs 实际对比结果（详设 U13 §6.1）。只读读模型，按预算科目对比「预算 vs 已发生」，超支仅标识不拦截。
 *
 * @param budgetId       预算 id
 * @param budgetName     预算名称（卡片标题）
 * @param rows           按科目对比行（以预算侧科目为基准）
 * @param totalBudgeted  预算合计（Σ budgeted）
 * @param totalActual    已发生合计（Σ actual）
 * @param totalRemaining 差额合计（预算 − 已发生）
 */
public record BudgetVsActualVO(
        Long budgetId,
        String budgetName,
        List<RowVO> rows,
        BigDecimal totalBudgeted,
        BigDecimal totalActual,
        BigDecimal totalRemaining) {

    /**
     * 单科目对比行。
     *
     * @param subjectId   预算科目 id
     * @param subjectName 科目名（展示，缺可空）
     * @param subjectCode 科目编码（展示，缺可空）
     * @param budgeted    预算金额（budget_item.amount 按科目 SUM）
     * @param actual      已发生金额（purchase_item.amount 按科目 SUM，无采购=0.00）
     * @param remaining   差额 = budgeted − actual（负=超支）
     * @param overspent   是否超支（remaining < 0），仅标识不拦截
     */
    public record RowVO(
            Long subjectId,
            String subjectName,
            String subjectCode,
            BigDecimal budgeted,
            BigDecimal actual,
            BigDecimal remaining,
            boolean overspent) {
    }
}
