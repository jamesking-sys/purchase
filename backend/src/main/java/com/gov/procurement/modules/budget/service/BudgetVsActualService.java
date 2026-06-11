package com.gov.procurement.modules.budget.service;

import com.gov.procurement.modules.budget.dto.BudgetVsActualVO;

/**
 * 预算 vs 实际只读读模型（U13，M2/BC2）。按预算科目聚合「预算金额 vs 已发生金额」对比，超支仅标识不拦截。
 * 纯查询、零副作用：不写任何表、不开写事务、不改预算状态、不占用/核减预算。
 */
public interface BudgetVsActualService {

    /**
     * 查某预算的预算 vs 实际对比：以预算侧科目为基准左连接实际侧（无采购→actual=0），逐行算差额/超支并汇总合计。
     * 预算不存在 → 40401。
     *
     * @param budgetId 预算 id
     * @return 对比结果（含超支行，不拦截）
     */
    BudgetVsActualVO getVsActual(Long budgetId);
}
