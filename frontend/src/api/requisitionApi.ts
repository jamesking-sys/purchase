import { apiClient, type RequestOpts } from './client';
import type { Page } from './page';

/** 仓管待办明细行（对齐 U11 TodoLineVO，含库存核验）。 */
export interface TodoLineVO {
  stockItemId: number;
  materialName: string;
  qty: number;
  currentQuantity: number;
  enough: boolean;
}

/** 仓管待办（对齐 U11 RequisitionTodoVO）。 */
export interface RequisitionTodoVO {
  id: number;
  projectGroupName: string;
  applicantName: string;
  createdAt: string;
  items: TodoLineVO[];
}

/** 审批出库结果（对齐 U11 ApproveOutboundVO）。 */
export interface ApproveOutboundVO {
  requisitionId: number;
  outboundOrderId: number;
  status: string;
}

/** 驳回结果（对齐 U11 RejectVO）。 */
export interface RejectVO {
  requisitionId: number;
  status: string;
}

/** 可领用库存项（对齐 U11 StockOptionVO）。 */
export interface StockOptionVO {
  stockItemId: number;
  materialName: string;
  quantity: number;
}

/** 发起领用结果（对齐 U11 CreateRequisitionVO）。 */
export interface CreateRequisitionVO {
  id: number;
  status: string;
}

/** 领用单列表摘要（对齐 U11 RequisitionVO）。 */
export interface RequisitionVO {
  id: number;
  status: string;
  applicantName: string;
  createdAt: string;
  itemCount: number;
}

/** 领用/出库接口（U11）：领用人发起 / 我的领用；仓管待办 / 审批出库 / 驳回。 */
export const requisitionApi = {
  stockOptions(projectGroupId: number, opts?: RequestOpts): Promise<StockOptionVO[]> {
    return apiClient.get<StockOptionVO[]>(`/api/requisitions/stock-options?projectGroupId=${projectGroupId}`, opts);
  },
  create(
    projectGroupId: number,
    purpose: string | undefined,
    items: { stockItemId: number; qty: number }[],
    opts?: RequestOpts,
  ): Promise<CreateRequisitionVO> {
    return apiClient.post<CreateRequisitionVO>('/api/requisitions', { projectGroupId, purpose, items }, opts);
  },
  listMine(applicantId: number, page = 1, size = 50, opts?: RequestOpts): Promise<Page<RequisitionVO>> {
    return apiClient.get<Page<RequisitionVO>>(
      `/api/requisitions?applicantId=${applicantId}&page=${page}&size=${size}`,
      opts,
    );
  },
  todo(projectGroupId?: number, page = 1, size = 20, opts?: RequestOpts): Promise<Page<RequisitionTodoVO>> {
    const pg = projectGroupId != null ? `&projectGroupId=${projectGroupId}` : '';
    return apiClient.get<Page<RequisitionTodoVO>>(`/api/requisitions/todo?page=${page}&size=${size}${pg}`, opts);
  },
  approveOutbound(id: number, opts?: RequestOpts): Promise<ApproveOutboundVO> {
    return apiClient.post<ApproveOutboundVO>(`/api/requisitions/${id}/approve-outbound`, undefined, opts);
  },
  reject(id: number, opinion: string, opts?: RequestOpts): Promise<RejectVO> {
    return apiClient.post<RejectVO>(`/api/requisitions/${id}/reject`, { opinion }, opts);
  },
};
