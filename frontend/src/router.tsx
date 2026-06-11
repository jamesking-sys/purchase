import type { ReactElement } from 'react';
import { Navigate, Route, Routes } from 'react-router-dom';
import AuthGuard from './auth/AuthGuard';
import LoginPage from './auth/LoginPage';
import AppLayout from './layout/AppLayout';
import Placeholder from './pages/Placeholder';
import NotFoundPage from './pages/NotFoundPage';
import DashboardPage from './pages/DashboardPage';
import StocktakePage from './pages/StocktakePage';
import OutboundPage from './pages/OutboundPage';
import ImportPage from './pages/ImportPage';
import ComparePage from './pages/ComparePage';
import SubjectPage from './pages/SubjectPage';
import ApprovalPage from './pages/ApprovalPage';
import PurchasePage from './pages/PurchasePage';
import InboundPage from './pages/InboundPage';
import OrgPage from './pages/OrgPage';
import RequisitionPage from './pages/RequisitionPage';
import { navConfig } from './layout/navConfig';

/**
 * 已落地的业务页（U14）：按路由路径覆盖占位页；未实现的屏仍走 Placeholder，保证导航全程可达。
 */
const PAGES: Record<string, ReactElement> = {
  '/dashboard': <DashboardPage />,
  '/org': <OrgPage />,
  '/budget/import': <ImportPage />,
  '/budget/compare': <ComparePage />,
  '/budget/subject': <SubjectPage />,
  '/approval': <ApprovalPage />,
  '/purchase': <PurchasePage />,
  '/inbound': <InboundPage />,
  '/requisition': <RequisitionPage />,
  '/stocktake': <StocktakePage />,
  '/outbound': <OutboundPage />,
};

/**
 * 路由表（U3 详设 §4.2）：/login 公共；其余受 AuthGuard 守卫，挂在 AppLayout 外壳内。
 * 受保护业务路由由 navConfig 驱动生成，保证路由表与导航/原型屏一一对应（INV-4）。
 */
export function AppRoutes() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route element={<AuthGuard />}>
        <Route element={<AppLayout />}>
          <Route path="/" element={<Navigate to="/dashboard" replace />} />
          {navConfig.map((item) => (
            <Route key={item.path} path={item.path} element={PAGES[item.path] ?? <Placeholder title={item.label} />} />
          ))}
          <Route path="*" element={<NotFoundPage />} />
        </Route>
      </Route>
    </Routes>
  );
}
