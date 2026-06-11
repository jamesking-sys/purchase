import { Toast } from '@douyinfe/semi-ui';
import { tokenStore } from '../auth/tokenStore';
import { authStore } from '../auth/authStore';
import type { Result } from './types';

/**
 * 统一 API 客户端：注入 token、解包 Result<T>、401/40110 触发登出、错误 toast。
 * token 头与 U2 后端一致：Authorization: Bearer <token>（application.yml 的 sa-token.token-name/token-prefix）。
 */

/** Sa-Token 请求头与前缀（与 U2 application.yml 对齐）。 */
const TOKEN_HEADER = 'Authorization';
const TOKEN_PREFIX = 'Bearer ';
/** 未登录/失效业务码（与 U2 ErrorCode.NOT_LOGIN 对齐）。 */
const NOT_LOGIN_CODE = 40110;
/** 401 登出 toast 去抖窗口（ms），避免并发请求同时 401 时 toast 风暴。 */
const UNAUTHORIZED_DEBOUNCE_MS = 3000;

export class ApiError extends Error {
  readonly code: number;
  /** 业务错误时透传的 Result.data（如导入校验失败的 errorRows），供页面结构化呈现。 */
  readonly data: unknown;

  constructor(code: number, message: string, data?: unknown) {
    super(message);
    this.name = 'ApiError';
    this.code = code;
    this.data = data;
  }
}

export interface RequestOpts {
  /** 静默：业务失败不弹 toast（调用方自定义处理）。 */
  silent?: boolean;
}

let lastUnauthorizedAt = 0;

/** 统一登出：清 token + 清会话态；AuthGuard 订阅会话态后会反应式跳转 /login。去抖 toast 一次。 */
function handleUnauthorized(): void {
  tokenStore.clear();
  authStore.clear();
  const now = Date.now();
  if (now - lastUnauthorizedAt > UNAUTHORIZED_DEBOUNCE_MS) {
    lastUnauthorizedAt = now;
    Toast.error('登录已失效，请重新登录');
  }
}

async function request<T>(method: string, url: string, body?: unknown, opts: RequestOpts = {}): Promise<T> {
  const headers: Record<string, string> = {};
  const isForm = body instanceof FormData;
  if (body !== undefined && !isForm) {
    headers['Content-Type'] = 'application/json';
  }
  const token = tokenStore.get();
  if (token) {
    headers[TOKEN_HEADER] = TOKEN_PREFIX + token;
  }

  let resp: Response;
  try {
    resp = await fetch(url, {
      method,
      headers,
      body: body === undefined ? undefined : isForm ? (body as FormData) : JSON.stringify(body),
    });
  } catch {
    if (!opts.silent) {
      Toast.error('网络异常，请检查连接');
    }
    throw new ApiError(-1, '网络异常');
  }

  if (resp.status === 401) {
    handleUnauthorized();
    throw new ApiError(NOT_LOGIN_CODE, '登录已失效');
  }

  let result: Result<T>;
  try {
    result = (await resp.json()) as Result<T>;
  } catch {
    if (!opts.silent) {
      Toast.error('服务异常，请稍后重试');
    }
    throw new ApiError(resp.status, '响应解析失败');
  }

  if (result.code === 0) {
    return result.data;
  }
  if (result.code === NOT_LOGIN_CODE) {
    handleUnauthorized();
  } else if (!opts.silent) {
    Toast.error(result.message || '操作失败');
  }
  throw new ApiError(result.code, result.message || '操作失败', result.data);
}

export const apiClient = {
  get: <T>(url: string, opts?: RequestOpts): Promise<T> => request<T>('GET', url, undefined, opts),
  post: <T>(url: string, body?: unknown, opts?: RequestOpts): Promise<T> => request<T>('POST', url, body, opts),
  put: <T>(url: string, body?: unknown, opts?: RequestOpts): Promise<T> => request<T>('PUT', url, body, opts),
  del: <T>(url: string, opts?: RequestOpts): Promise<T> => request<T>('DELETE', url, undefined, opts),
};
