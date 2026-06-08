import { useNavigate } from 'react-router-dom';
import { apiClient } from '../api/client';
import { tokenStore } from './tokenStore';
import { authStore } from './authStore';

/**
 * 登出钩子：调 /api/auth/logout（失败也继续本地登出），清 token 与会话态后跳 /login（§5.x / AC-7）。
 */
export function useLogout(): () => Promise<void> {
  const navigate = useNavigate();
  return async () => {
    try {
      await apiClient.post('/api/auth/logout', undefined, { silent: true });
    } catch {
      // 忽略登出接口失败，仍执行本地登出
    }
    tokenStore.clear();
    authStore.clear();
    navigate('/login', { replace: true });
  };
}
