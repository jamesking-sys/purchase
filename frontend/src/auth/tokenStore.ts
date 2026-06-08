const KEY = 'pmp_token';

/**
 * token 存储单一出入口（暂用 localStorage，刷新保活；最终选型见 U3 详设 TBD-1）。
 */
export const tokenStore = {
  get(): string | null {
    return localStorage.getItem(KEY);
  },
  set(token: string): void {
    localStorage.setItem(KEY, token);
  },
  clear(): void {
    localStorage.removeItem(KEY);
  },
};
