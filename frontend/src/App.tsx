import { BrowserRouter } from 'react-router-dom';
import { AppRoutes } from './router';

/**
 * 应用根：挂载 Router（替代 U1 的 /api/health 占位页）。整体外壳与守卫见 router/AppLayout/AuthGuard。
 */
export default function App() {
  return (
    <BrowserRouter>
      <AppRoutes />
    </BrowserRouter>
  );
}
