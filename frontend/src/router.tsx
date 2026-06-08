import { Navigate, Route, Routes } from 'react-router-dom';
import AuthGuard from './auth/AuthGuard';
import LoginPage from './auth/LoginPage';
import AppLayout from './layout/AppLayout';
import Placeholder from './pages/Placeholder';
import NotFoundPage from './pages/NotFoundPage';
import { navConfig } from './layout/navConfig';

/**
 * 路由表（U3 详设 §4.2）：/login 公共；其余受 AuthGuard 守卫，挂在 AppLayout 外壳内。
 * 受保护业务路由由 navConfig 驱动生成，保证路由表与导航/原型屏一一对应（INV-4）。占位页待 U14 替换。
 */
export function AppRoutes() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route element={<AuthGuard />}>
        <Route element={<AppLayout />}>
          <Route path="/" element={<Navigate to="/dashboard" replace />} />
          {navConfig.map((item) => (
            <Route key={item.path} path={item.path} element={<Placeholder title={item.label} />} />
          ))}
          <Route path="*" element={<NotFoundPage />} />
        </Route>
      </Route>
    </Routes>
  );
}
