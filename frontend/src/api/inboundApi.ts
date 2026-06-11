import { apiClient, type RequestOpts } from './client';
import type { Page } from './page';

/** 采购单待收明细（对齐 U9 PendingItemVO）。 */
export interface PendingItemVO {
  purchaseItemId: number;
  materialName: string;
  qty: number;
  receivedQty: number;
  remaining: number;
}

/** 入库结果（对齐 U9 CreateInboundVO）。 */
export interface CreateInboundVO {
  inboundOrderId: number;
  purchaseOrderStatus: string;
  items: { purchaseItemId: number; receivedQtyTotal: number; stockItemId: number }[];
}

/** 入库记录明细行（对齐 U9 InboundOrderVO.InboundLineVO）。 */
export interface InboundLineVO {
  purchaseItemId: number;
  materialName: string;
  receivedQty: number;
  stockItemId: number;
}

/** 入库记录（对齐 U9 InboundOrderVO）。 */
export interface InboundOrderVO {
  inboundOrderId: number;
  purchaseOrderId: number;
  receivedBy: number;
  inboundAt: string;
  items: InboundLineVO[];
}

/** 验收入库接口（U9）：待收明细 / 入库 / 入库记录，均限仓管员。 */
export const inboundApi = {
  pendingItems(purchaseOrderId: number, opts?: RequestOpts): Promise<PendingItemVO[]> {
    return apiClient.get<PendingItemVO[]>(`/api/inbounds/pending-items?purchaseOrderId=${purchaseOrderId}`, opts);
  },
  create(
    purchaseOrderId: number,
    items: { purchaseItemId: number; receivedQty: number }[],
    opts?: RequestOpts,
  ): Promise<CreateInboundVO> {
    return apiClient.post<CreateInboundVO>('/api/inbounds', { purchaseOrderId, items }, opts);
  },
  records(purchaseOrderId: number, page = 1, size = 20, opts?: RequestOpts): Promise<Page<InboundOrderVO>> {
    return apiClient.get<Page<InboundOrderVO>>(
      `/api/inbounds?purchaseOrderId=${purchaseOrderId}&page=${page}&size=${size}`,
      opts,
    );
  },
};
