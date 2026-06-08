import { useSyncExternalStore } from 'react';
import { authStore } from './authStore';
import type { MeResp } from '../api/types';

/**
 * 订阅当前用户态的 React Hook。返回 me 与 hasRole（admin 默认拥有全部角色）。
 */
export function useAuth(): { me: MeResp | null; hasRole: (code: string) => boolean } {
  const me = useSyncExternalStore(authStore.subscribe, authStore.getMe, authStore.getMe);
  return {
    me,
    hasRole: (code: string) => me != null && (me.roles.includes(code) || me.roles.includes('admin')),
  };
}
