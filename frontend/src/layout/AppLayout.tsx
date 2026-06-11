import type { CSSProperties } from 'react';
import { Button } from '@douyinfe/semi-ui';
import { Outlet, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/useAuth';
import { useLogout } from '../auth/useLogout';
import { NAV_DOMAINS, STAGE_COLORS, navConfig, findNavItem, isNavVisible } from './navConfig';

/**
 * 应用外壳：左侧按角色过滤的 4 域阶段化导航 + 当前用户/登出 + 内容区路由出口（§4 / §5.4）。
 * 外观对齐原型（docs/prototype/index.html）：阶段分组配色、主区随当前域着色，详见 styles/theme.css。
 */
export default function AppLayout() {
  const navigate = useNavigate();
  const location = useLocation();
  const { me } = useAuth();
  const logout = useLogout();
  const roles = me?.roles ?? [];

  const current = findNavItem(location.pathname);
  const activeStage = current ? STAGE_COLORS[current.domain] : 'var(--pms-brand)';

  const groups = NAV_DOMAINS.map((domain) => ({
    ...domain,
    items: navConfig.filter((item) => item.domain === domain.key && isNavVisible(item, roles)),
  })).filter((group) => group.items.length > 0);

  return (
    <div className="pms-shell">
      <nav className="pms-nav">
        <div className="pms-brand">采购与资产管理</div>
        <div className="pms-sub">多项目 · 轻量角色</div>

        {groups.map((group) => (
          <div key={group.key} className="pms-stage" style={{ '--stage': STAGE_COLORS[group.key] } as CSSProperties}>
            <div className="pms-group">{group.title}</div>
            {group.items.map((item) => (
              <button
                key={item.path}
                type="button"
                className={`pms-navbtn${location.pathname === item.path ? ' active' : ''}`}
                onClick={() => navigate(item.path)}
              >
                <span className="pms-ix">{item.ix}</span>
                {item.label}
              </button>
            ))}
          </div>
        ))}

        <div className="pms-nav-spacer" />
        <div className="pms-nav-footer">
          <span className="pms-nav-user" title={me ? `${me.name}（${me.account}）` : ''}>
            {me ? `${me.name}（${me.account}）` : ''}
          </span>
          <Button size="small" theme="borderless" type="tertiary" onClick={() => void logout()}>
            登出
          </Button>
        </div>
      </nav>

      <main className="pms-main" style={{ '--active': activeStage } as CSSProperties}>
        <div className="pms-content" style={{ '--stage': activeStage } as CSSProperties}>
          <Outlet />
        </div>
      </main>
    </div>
  );
}
