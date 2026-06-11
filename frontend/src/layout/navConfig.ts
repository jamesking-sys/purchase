/** 单条导航项：导航域、功能点编码、菜单标签、路由路径、原型屏 id、建议可见角色（前端体验层，权威鉴权在后端）。 */
export interface NavItem {
  domain: 'A' | 'B' | 'D' | 'F';
  ix: string;
  label: string;
  path: string;
  screenId: string;
  roles?: string[];
}

/** 4 个导航域（对齐原型左侧分组与概要 §6）。 */
export const NAV_DOMAINS: { key: NavItem['domain']; title: string }[] = [
  { key: 'A', title: 'A · 基础与权限' },
  { key: 'B', title: 'B · 预算科目与审批' },
  { key: 'D', title: 'D · 采购入库与领用出库' },
  { key: 'F', title: 'F · 盘点' },
];

/** 各导航域主色（对齐原型 `--stage`：A 蓝 / B 绿 / D 紫 / F 玫红）。 */
export const STAGE_COLORS: Record<NavItem['domain'], string> = {
  A: '#2b54e0',
  B: '#08a06f',
  D: '#6d34e8',
  F: '#e01e5a',
};

/**
 * 静态导航配置（U3 详设 §4.1）。screenId 与原型屏一一对应（INV-4）；ix=原型功能点编码；
 * roles 缺省=全部已登录可见；admin 默认可见全部（见 isNavVisible）。
 */
export const navConfig: NavItem[] = [
  { domain: 'A', ix: 'A1', label: '工作台', path: '/dashboard', screenId: 'dashboard' },
  { domain: 'A', ix: 'A2', label: '组织与权限', path: '/org', screenId: 'org', roles: ['admin'] },
  { domain: 'B', ix: 'B1', label: '预算导入', path: '/budget/import', screenId: 'import', roles: ['editor'] },
  { domain: 'B', ix: 'B2', label: '预算科目划分', path: '/budget/subject', screenId: 'subject', roles: ['editor'] },
  { domain: 'B', ix: 'B4', label: '科目比对·新增', path: '/budget/compare', screenId: 'compare', roles: ['editor'] },
  { domain: 'B', ix: 'C1', label: '两级审批', path: '/approval', screenId: 'approval', roles: ['purchase_mgr', 'dept_mgr'] },
  { domain: 'D', ix: 'D1', label: '采购执行', path: '/purchase', screenId: 'purchase', roles: ['editor', 'purchase_mgr'] },
  { domain: 'D', ix: 'D3', label: '验收入库', path: '/inbound', screenId: 'inbound', roles: ['warehouse'] },
  { domain: 'D', ix: 'E1', label: '领用申请', path: '/requisition', screenId: 'requisition', roles: ['requester'] },
  { domain: 'D', ix: 'E2', label: '仓管审批出库', path: '/outbound', screenId: 'outbound', roles: ['warehouse'] },
  { domain: 'F', ix: 'F1', label: '盘点与差异', path: '/stocktake', screenId: 'stocktake', roles: ['warehouse'] },
];

/** 域 key → 域标题（面包屑 chip 文案）。 */
export function domainTitleOf(domain: NavItem['domain']): string {
  return NAV_DOMAINS.find((d) => d.key === domain)?.title ?? domain;
}

/** 按路由路径查导航项（用于页面解析当前域/标题/主色）。 */
export function findNavItem(path: string): NavItem | undefined {
  return navConfig.find((item) => item.path === path);
}

/**
 * 菜单可见性（体验层）：无 roles 限定即可见；否则持任一所列角色，或为 admin。
 *
 * @param item    导航项
 * @param myRoles 当前用户角色 code
 */
export function isNavVisible(item: NavItem, myRoles: string[]): boolean {
  if (!item.roles) {
    return true;
  }
  return myRoles.includes('admin') || item.roles.some((r) => myRoles.includes(r));
}
