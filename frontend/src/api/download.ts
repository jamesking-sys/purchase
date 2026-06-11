import { ApiError } from './client';
import { tokenStore } from '../auth/tokenStore';

/**
 * 通过 fetch 取二进制流并触发浏览器下载（带 Sa-Token 头；绕过 apiClient 的 Result 解包）。
 * 用于模板下载、到货单下载等 Content-Disposition: attachment 接口。
 */
export async function downloadFile(url: string, filename: string, method: 'GET' | 'POST' = 'GET'): Promise<void> {
  const token = tokenStore.get();
  const resp = await fetch(url, { method, headers: token ? { Authorization: `Bearer ${token}` } : {} });
  if (!resp.ok) {
    throw new ApiError(resp.status, '下载失败');
  }
  const blob = await resp.blob();
  const objUrl = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = objUrl;
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  a.remove();
  URL.revokeObjectURL(objUrl);
}
