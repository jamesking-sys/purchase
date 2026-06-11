import { apiClient, type RequestOpts } from './client';

/** 盘点明细快照（对齐 U12 CreateStocktakeVO.SnapshotItemVO）。 */
export interface SnapshotItemVO {
  stocktakeItemId: number;
  stockItemId: number;
  materialName: string;
  bookQty: number;
}

/** 发起盘点结果（对齐 U12 CreateStocktakeVO）。 */
export interface CreateStocktakeVO {
  stocktakeId: number;
  status: string;
  items: SnapshotItemVO[];
}

/** 实盘录入项（对齐 U12 SaveActualsCmd.ActualItemReq）。 */
export interface ActualItemReq {
  stocktakeItemId: number;
  actualQty: number;
}

/** 实盘差异结果（对齐 U12 SaveActualsVO.ActualResultVO）。 */
export interface ActualResultVO {
  stocktakeItemId: number;
  diff: number;
  diffType: string;
}

/** 确认调整结果（对齐 U12 ConfirmStocktakeVO）。 */
export interface ConfirmStocktakeVO {
  stocktakeId: number;
  status: string;
  adjustedCount: number;
  items: { stockItemId: number; quantity: number; txnType: string }[];
}

/** 盘点接口（U12）：发起 / 录入实盘 / 确认调整，限仓管员。 */
export const stocktakeApi = {
  create(scopeProjectGroupId: number, opts?: RequestOpts): Promise<CreateStocktakeVO> {
    return apiClient.post<CreateStocktakeVO>('/api/stocktakes', { scopeProjectGroupId }, opts);
  },
  saveActuals(id: number, items: ActualItemReq[], opts?: RequestOpts): Promise<{ items: ActualResultVO[] }> {
    return apiClient.put<{ items: ActualResultVO[] }>(`/api/stocktakes/${id}/items`, { items }, opts);
  },
  confirm(id: number, opts?: RequestOpts): Promise<ConfirmStocktakeVO> {
    return apiClient.post<ConfirmStocktakeVO>(`/api/stocktakes/${id}/confirm`, undefined, opts);
  },
};
