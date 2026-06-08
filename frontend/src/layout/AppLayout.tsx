import { Layout, Nav, Button, Typography } from '@douyinfe/semi-ui';
import { Outlet, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/useAuth';
import { useLogout } from '../auth/useLogout';
import { NAV_DOMAINS, navConfig, isNavVisible } from './navConfig';

const { Sider, Header, Content } = Layout;
const { Title, Text } = Typography;

/**
 * 应用外壳：左侧按角色过滤的 4 域导航 + 顶部（当前用户 + 登出）+ 内容区路由出口（§4 / §5.4）。
 */
export default function AppLayout() {
  const navigate = useNavigate();
  const location = useLocation();
  const { me } = useAuth();
  const logout = useLogout();
  const roles = me?.roles ?? [];

  const navItems = NAV_DOMAINS.map((domain) => ({
    itemKey: domain.key,
    text: domain.title,
    items: navConfig
      .filter((item) => item.domain === domain.key && isNavVisible(item, roles))
      .map((item) => ({ itemKey: item.path, text: item.label })),
  })).filter((group) => group.items.length > 0);

  return (
    <Layout style={{ height: '100vh' }}>
      <Sider style={{ borderRight: '1px solid var(--semi-color-border)' }}>
        <div style={{ padding: '16px 20px' }}>
          <Title heading={5} style={{ margin: 0 }}>采购与资产管理</Title>
        </div>
        <Nav
          style={{ height: 'calc(100% - 60px)' }}
          items={navItems}
          selectedKeys={[location.pathname]}
          defaultOpenKeys={NAV_DOMAINS.map((d) => d.key)}
          onSelect={(data) => navigate(String(data.itemKey))}
        />
      </Sider>
      <Layout>
        <Header style={{ display: 'flex', justifyContent: 'flex-end', alignItems: 'center', gap: 12,
          padding: '0 24px', borderBottom: '1px solid var(--semi-color-border)' }}>
          <Text>{me ? `${me.name}（${me.account}）` : ''}</Text>
          <Button theme="borderless" type="tertiary" onClick={() => void logout()}>登出</Button>
        </Header>
        <Content style={{ padding: 24, overflow: 'auto' }}>
          <Outlet />
        </Content>
      </Layout>
    </Layout>
  );
}
