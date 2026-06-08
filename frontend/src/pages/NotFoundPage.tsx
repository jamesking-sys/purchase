import { Empty, Button } from '@douyinfe/semi-ui';
import { useNavigate } from 'react-router-dom';

/**
 * 404 占位（已登录时渲染；未登录由 AuthGuard 先行跳 /login）。
 */
export default function NotFoundPage() {
  const navigate = useNavigate();
  return (
    <div style={{ display: 'flex', justifyContent: 'center', marginTop: 64 }}>
      <Empty
        title="页面不存在"
        description="您访问的页面不存在或已移除。"
      >
        <Button theme="solid" onClick={() => navigate('/dashboard', { replace: true })}>返回工作台</Button>
      </Empty>
    </div>
  );
}
