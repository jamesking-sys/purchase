import { apiClient, type RequestOpts } from './client';
import type { Page } from './page';
import { downloadFile } from './download';

/** 采购明细行请求（对齐 U8 ItemReq）。 */
export interface ItemReq {
  subjectId: number;
  materialName: string;
  qty: number;
  amount: number;
}

/** 采购明细视图（对齐 U8 PurchaseItemVO）。 */
export interface PurchaseItemVO {
  id: number;
  subjectId: number;
  materialName: string;
  qty: number;
  amount: number;
  receivedQty: number;
}

/** 采购单视图 / 列表项（对齐 U8 PurchaseOrderVO）。 */
export interface PurchaseOrderVO {
  id: number;
  budgetId: number;
  projectGroupId: number;
  supplierName: string | null;
  contractNo: string | null;
  status: string;
  createdAt: string;
  items: PurchaseItemVO[] | null;
}

/** 到货单视图（对齐 U8 DeliveryNoteVO）。 */
export interface DeliveryNoteVO {
  id: number;
  fileName: string;
  uploadedBy: number;
  uploadedByName: string;
  uploadedAt: string;
  downloadUrl: string;
}

/** 采购单详情（对齐 U8 PurchaseOrderDetailVO）。 */
export interface PurchaseOrderDetailVO extends PurchaseOrderVO {
  deliveryNotes: DeliveryNoteVO[];
}

/** 采购执行接口（U8）：建单 / 详情 / 列表 / 到货单上传下载，均限编制人。 */
export const purchaseApi = {
  countExecuting(opts?: RequestOpts): Promise<Page<unknown>> {
    return apiClient.get<Page<unknown>>('/api/purchase/orders?status=executing&page=1&size=1', opts);
  },
  create(
    req: {
      budgetId: number;
      projectGroupId: number;
      supplierName?: string;
      contractNo?: string;
      items: ItemReq[];
    },
    opts?: RequestOpts,
  ): Promise<PurchaseOrderVO> {
    return apiClient.post<PurchaseOrderVO>('/api/purchase/orders', req, opts);
  },
  list(status: string | undefined, page = 1, size = 20, opts?: RequestOpts): Promise<Page<PurchaseOrderVO>> {
    const s = status ? `&status=${status}` : '';
    return apiClient.get<Page<PurchaseOrderVO>>(`/api/purchase/orders?page=${page}&size=${size}${s}`, opts);
  },
  detail(id: number, opts?: RequestOpts): Promise<PurchaseOrderDetailVO> {
    return apiClient.get<PurchaseOrderDetailVO>(`/api/purchase/orders/${id}`, opts);
  },
  uploadNotes(id: number, files: File[], opts?: RequestOpts): Promise<DeliveryNoteVO[]> {
    const fd = new FormData();
    files.forEach((f) => fd.append('files', f));
    return apiClient.post<DeliveryNoteVO[]>(`/api/purchase/orders/${id}/delivery-notes`, fd, opts);
  },
  listNotes(id: number, opts?: RequestOpts): Promise<DeliveryNoteVO[]> {
    return apiClient.get<DeliveryNoteVO[]>(`/api/purchase/orders/${id}/delivery-notes`, opts);
  },
  downloadNote(note: DeliveryNoteVO): Promise<void> {
    return downloadFile(note.downloadUrl, note.fileName);
  },
};
