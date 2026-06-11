package com.gov.procurement.modules.budget.mapper;

import com.gov.procurement.modules.budget.dto.SubjectAmountRow;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 预算 vs 实际只读聚合查询（U13 读模型，M2）。不绑定单表实体——两段聚合 SQL 经应用层合并（详设 U13 §5）。
 * 全只读：不写任何表、不开写事务。
 */
public interface BudgetVsActualMapper {

    /**
     * 预算侧：该预算下 budget_item 按 subject_id 汇总 amount（金额仅挂叶子，ER D-7）。命中 idx_bi_budget。
     *
     * @param budgetId 预算 id
     * @return 每科目预算金额
     */
    @Select("SELECT bi.subject_id, SUM(bi.amount) AS amount "
            + "FROM budget_item bi WHERE bi.budget_id = #{budgetId} GROUP BY bi.subject_id")
    List<SubjectAmountRow> aggregateBudgetBySubject(@Param("budgetId") Long budgetId);

    /**
     * 实际侧：关联同一预算的采购单，其 purchase_item 按 subject_id 汇总 amount（下单口径，详设 §5.2/TBD-1）。
     * 经 purchase_order.budget_id 锚定同一预算（AC-8 不串他预算）。命中 idx_po_budget + idx_pi_po。
     *
     * @param budgetId 预算 id
     * @return 每科目已发生金额
     */
    @Select("SELECT pi.subject_id, SUM(pi.amount) AS amount "
            + "FROM purchase_item pi JOIN purchase_order po ON po.id = pi.purchase_order_id "
            + "WHERE po.budget_id = #{budgetId} GROUP BY pi.subject_id")
    List<SubjectAmountRow> aggregateActualBySubject(@Param("budgetId") Long budgetId);
}
