import type { MeResp } from '../api/types';

/**
 * 当前用户与角色内存态（不持久化敏感信息，刷新后由 AuthGuard 重新拉取，INV-3）。
 * 用极简发布订阅实现，便于 React 组件（useSyncExternalStore）与非 React 模块（apiClient）共同消费。
 */
let me: MeResp | null = null;
const listeners = new Set<() => void>();

function emit(): void {
  listeners.forEach((listener) => listener());
}

export const authStore = {
  getMe(): MeResp | null {
    return me;
  },
  setMe(next: MeResp | null): void {
    me = next;
    emit();
  },
  clear(): void {
    me = null;
    emit();
  },
  subscribe(listener: () => void): () => void {
    listeners.add(listener);
    return () => {
      listeners.delete(listener);
    };
  },
  hasRole(code: string): boolean {
    return me != null && (me.roles.includes(code) || me.roles.includes('admin'));
  },
};
