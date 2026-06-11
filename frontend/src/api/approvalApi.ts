import { apiClient, type RequestOpts } from './client';
import type { Page } from './page';

/** 审批待办项（对齐 U7 TodoItemVO）。 */
export interface ApprovalTodoVO {
  approvalId: number;
  taskId: string;
  bizType: string;
  bizId: number;
  node: string;
  budgetName: string;
  projectGroupName: string;
  createdAt: string;
}

/** 流转历史项（对齐 U7 HistoryItemVO）。 */
export interface HistoryItemVO {
  nodeSeq: number;
  node: string;
  approverName: string;
  action: string;
  opinion: string | null;
  actedAt: string;
}

/** 通用审批接口（U7）：提交（editor）/ 待办、通过、驳回（采购主管|部门主管）/ 历史（登录）。 */
export const approvalApi = {
  todo(page = 1, size = 20, opts?: RequestOpts): Promise<Page<ApprovalTodoVO>> {
    return apiClient.get<Page<ApprovalTodoVO>>(`/api/approvals/todo?page=${page}&size=${size}`, opts);
  },
  submit(bizType: string, bizId: number, opts?: RequestOpts): Promise<number> {
    return apiClient.post<number>('/api/approvals', { bizType, bizId }, opts);
  },
  approve(id: number, opts?: RequestOpts): Promise<void> {
    return apiClient.post<void>(`/api/approvals/${id}/approve`, undefined, opts);
  },
  reject(id: number, opinion: string, opts?: RequestOpts): Promise<void> {
    return apiClient.post<void>(`/api/approvals/${id}/reject`, { opinion }, opts);
  },
  history(id: number, opts?: RequestOpts): Promise<HistoryItemVO[]> {
    return apiClient.get<HistoryItemVO[]>(`/api/approvals/${id}/history`, opts);
  },
};
