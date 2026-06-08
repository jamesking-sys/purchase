package com.gov.procurement.modules.budget.dto;

import java.util.List;

/**
 * 导入服务内部结果（非 HTTP 响应）。错误清单为空 → 成功（budgetId/importedRows 有值）；
 * 否则失败（errorCode 为 42201/42202，errorRows 为错误行清单）。控制器据此组装 Result 与 HTTP 状态。
 *
 * @param budgetId     成功时的预算 id
 * @param importedRows 成功时导入行数
 * @param errorCode    失败时的错误码（42201/42202）
 * @param errorRows    失败时的错误行清单
 */
public record ImportResult(Long budgetId, int importedRows, Integer errorCode, List<ErrorRow> errorRows) {

    public boolean hasErrors() {
        return errorRows != null && !errorRows.isEmpty();
    }

    public static ImportResult ok(Long budgetId, int importedRows) {
        return new ImportResult(budgetId, importedRows, null, null);
    }

    public static ImportResult failed(int errorCode, List<ErrorRow> errorRows) {
        return new ImportResult(null, 0, errorCode, errorRows);
    }
}
