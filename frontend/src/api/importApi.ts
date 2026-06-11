import { apiClient, type RequestOpts } from './client';
import { downloadFile } from './download';

/** 附件留档响应（对齐 U6 AttachmentVO）。 */
export interface AttachmentVO {
  path: string;
  fileName: string;
  size: number;
}

/** 导入成功响应（对齐 U6 ImportResultVO）。 */
export interface ImportResultVO {
  budgetId: number;
  importedRows: number;
}

/** 导入错误行（对齐 U6 ErrorRow），校验失败时随 ApiError.data.errorRows 返回。 */
export interface ErrorRow {
  rowNo: number;
  subjectPath: string;
  amount: string;
  reason: string;
}

/** 预算导入接口（U6）：模板下载 / 附件留档 / 导入。均限 editor。 */
export const importApi = {
  /** 下载 xlsx 模板（二进制流，绕过 Result 解包，直接触发浏览器下载）。 */
  downloadTemplate(): Promise<void> {
    return downloadFile('/api/budget/template/download', 'budget-template.xlsx', 'POST');
  },

  uploadAttachment(file: File, projectGroupId: number, opts?: RequestOpts): Promise<AttachmentVO> {
    const fd = new FormData();
    fd.append('file', file);
    fd.append('projectGroupId', String(projectGroupId));
    return apiClient.post<AttachmentVO>('/api/budget/attachment', fd, opts);
  },

  /**
   * 导入预算。校验失败时后端返回 42201/42202 + data.errorRows，apiClient 透传到 ApiError.data，
   * 调用方用 silent 自行结构化呈现错误行（详设 U6 / U14 AC-1）。
   */
  importBudget(
    file: File,
    projectGroupId: number,
    name: string,
    sourceDocPath: string | undefined,
    opts?: RequestOpts,
  ): Promise<ImportResultVO> {
    const fd = new FormData();
    fd.append('file', file);
    fd.append('projectGroupId', String(projectGroupId));
    fd.append('name', name);
    if (sourceDocPath) {
      fd.append('sourceDocPath', sourceDocPath);
    }
    return apiClient.post<ImportResultVO>('/api/budget/import', fd, opts);
  },
};
