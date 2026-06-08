import { useEffect } from 'react';
import { Navigate, Outlet, useLocation } from 'react-router-dom';
import { Spin } from '@douyinfe/semi-ui';
import { apiClient } from '../api/client';
import { authStore } from './authStore';
import { tokenStore } from './tokenStore';
import { useAuth } from './useAuth';
import type { MeResp } from '../api/types';

const CENTER: React.CSSProperties = {
  display: 'flex',
  alignItems: 'center',
  justifyContent: 'center',
  height: '100vh',
};

/**
 * 受保护路由守卫：有 token 且 me 已加载 → 放行渲染子路由（Outlet → AppLayout）。
 * 有 token 但 me 未加载（如刷新后内存态丢失）→ 拉一次 /api/auth/me，期间显示 Spin。
 * 无 token → 跳 /login 并记录原目标用于回跳（§5.3）。
 */
export default function AuthGuard() {
  const location = useLocation();
  const { me } = useAuth();
  const token = tokenStore.get();

  useEffect(() => {
    if (!token || authStore.getMe()) {
      return;
    }
    apiClient
      .get<MeResp>('/api/auth/me')
      .then((data) => authStore.setMe(data))
      .catch(() => {
        // 401 已由 apiClient 清除 token，会话态变更触发本组件重渲染并跳转 /login
      });
  }, [token]);

  if (!token) {
    return <Navigate to="/login" state={{ from: location }} replace />;
  }
  if (me) {
    return <Outlet />;
  }
  return (
    <div style={CENTER}>
      <Spin tip="加载中…" size="large" />
    </div>
  );
}
