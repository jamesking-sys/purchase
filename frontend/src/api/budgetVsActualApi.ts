import { apiClient, type RequestOpts } from './client';

/** 预算 vs 实际单科目对比行（对齐 U13 RowVO）。 */
export interface VsActualRow {
  subjectId: number;
  subjectName: string | null;
  subjectCode: string | null;
  budgeted: number;
  actual: number;
  remaining: number;
  overspent: boolean;
}

/** 预算 vs 实际结果（对齐 U13 BudgetVsActualVO）。 */
export interface BudgetVsActualVO {
  budgetId: number;
  budgetName: string;
  rows: VsActualRow[];
  totalBudgeted: number;
  totalActual: number;
  totalRemaining: number;
}

/** 预算 vs 实际接口（U13）：只读对比，限 editor|purchase_mgr|dept_mgr|admin。 */
export const budgetVsActualApi = {
  get(budgetId: number, opts?: RequestOpts): Promise<BudgetVsActualVO> {
    return apiClient.get<BudgetVsActualVO>(`/api/budgets/${budgetId}/vs-actual`, opts);
  },
};
