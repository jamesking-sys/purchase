package com.gov.procurement.modules.budget.dto;

/**
 * 导入成功响应。
 *
 * @param budgetId     生成的预算 id
 * @param importedRows 导入的明细行数
 */
public record ImportResultVO(Long budgetId, int importedRows) {
}
