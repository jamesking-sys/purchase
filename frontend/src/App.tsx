import { useEffect, useState } from 'react';
import { Layout, Typography, Banner, Spin } from '@douyinfe/semi-ui';

const { Header, Content } = Layout;
const { Title, Text } = Typography;

type Health = { service: string; db: string };

/**
 * U1 骨架占位页：调用后端 /api/health 验证前后端联通。
 * 业务外壳（分组导航 / 登录守卫）在 U4 复刻原型信息架构时实现。
 */
export default function App() {
  const [health, setHealth] = useState<Health | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    fetch('/api/health')
      .then((r) => r.json())
      .then((res) => setHealth(res.data))
      .catch(() => setError('无法连接后端 /api/health'));
  }, []);

  return (
    <Layout style={{ minHeight: '100vh' }}>
      <Header style={{ padding: '16px 24px', background: 'var(--semi-color-bg-1)' }}>
        <Title heading={4} style={{ margin: 0 }}>
          采购与资产管理系统
        </Title>
      </Header>
      <Content style={{ padding: 24 }}>
        {error && <Banner type="danger" description={error} />}
        {!error && !health && <Spin tip="连接后端中…" />}
        {health && (
          <Banner
            type={health.db === 'ok' ? 'success' : 'warning'}
            description={
              <Text>
                服务：{health.service} · 数据库：{health.db}
              </Text>
            }
          />
        )}
      </Content>
    </Layout>
  );
}
