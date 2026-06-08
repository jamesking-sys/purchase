import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { Toast } from '@douyinfe/semi-ui';
import { apiClient, ApiError } from './client';
import { tokenStore } from '../auth/tokenStore';
import { authStore } from '../auth/authStore';

function mockFetch(status: number, jsonBody: unknown) {
  return vi.fn((_url: string, _init?: RequestInit) =>
    Promise.resolve({
      status,
      ok: status >= 200 && status < 300,
      json: () => Promise.resolve(jsonBody),
    } as unknown as Response),
  );
}

describe('apiClient', () => {
  beforeEach(() => {
    vi.spyOn(Toast, 'error').mockImplementation(() => '');
    tokenStore.clear();
    authStore.clear();
  });

  afterEach(() => {
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
  });

  it('T-4: code=0 解包返回 data', async () => {
    vi.stubGlobal('fetch', mockFetch(200, { code: 0, message: 'ok', data: { id: 1 } }));
    const data = await apiClient.get<{ id: number }>('/api/x');
    expect(data).toEqual({ id: 1 });
  });

  it('T-5: code≠0 抛 ApiError 并 toast message', async () => {
    vi.stubGlobal('fetch', mockFetch(200, { code: 40001, message: '参数校验失败', data: null }));
    await expect(apiClient.get('/api/x')).rejects.toMatchObject({ code: 40001 });
    expect(Toast.error).toHaveBeenCalledWith('参数校验失败');
  });

  it('T-10: silent 时业务失败不弹 toast', async () => {
    vi.stubGlobal('fetch', mockFetch(200, { code: 40001, message: 'x', data: null }));
    await expect(apiClient.get('/api/x', { silent: true })).rejects.toBeInstanceOf(ApiError);
    expect(Toast.error).not.toHaveBeenCalled();
  });

  it('T-6: HTTP 401 清 token/会话态并 toast 一次', async () => {
    tokenStore.set('tk');
    authStore.setMe({ userId: 1, account: 'a', name: 'n', departmentId: 1, roles: ['admin'] });
    vi.stubGlobal('fetch', mockFetch(401, {}));
    await expect(apiClient.get('/api/x')).rejects.toMatchObject({ code: 40110 });
    expect(tokenStore.get()).toBeNull();
    expect(authStore.getMe()).toBeNull();
    expect(Toast.error).toHaveBeenCalledTimes(1);
  });

  it('携带 Authorization: Bearer 头（与 U2 对齐）', async () => {
    tokenStore.set('mytoken');
    const f = mockFetch(200, { code: 0, message: 'ok', data: null });
    vi.stubGlobal('fetch', f);
    await apiClient.get('/api/x');
    const init = f.mock.calls[0][1]!;
    expect((init.headers as Record<string, string>).Authorization).toBe('Bearer mytoken');
  });
});
