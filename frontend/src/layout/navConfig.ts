/** 单条导航项：导航域、菜单标签、路由路径、原型屏 id、建议可见角色（前端体验层，权威鉴权在后端）。 */
export interface NavItem {
  domain: 'A' | 'B' | 'D' | 'F';
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

/**
 * 静态导航配置（U3 详设 §4.1）。screenId 与原型屏一一对应（INV-4）；
 * roles 缺省=全部已登录可见；admin 默认可见全部（见 isNavVisible）。
 */
export const navConfig: NavItem[] = [
  { domain: 'A', label: '工作台', path: '/dashboard', screenId: 'dashboard' },
  { domain: 'A', label: '组织与权限', path: '/org', screenId: 'org', roles: ['admin'] },
  { domain: 'B', label: '预算导入', path: '/budget/import', screenId: 'import', roles: ['editor'] },
  { domain: 'B', label: '预算科目划分', path: '/budget/subject', screenId: 'subject', roles: ['editor'] },
  { domain: 'B', label: '科目比对·新增', path: '/budget/compare', screenId: 'compare', roles: ['editor'] },
  { domain: 'B', label: '两级审批', path: '/approval', screenId: 'approval', roles: ['purchase_mgr', 'dept_mgr'] },
  { domain: 'D', label: '采购执行', path: '/purchase', screenId: 'purchase', roles: ['editor', 'purchase_mgr'] },
  { domain: 'D', label: '验收入库', path: '/inbound', screenId: 'inbound', roles: ['warehouse'] },
  { domain: 'D', label: '领用申请', path: '/requisition', screenId: 'requisition', roles: ['requester'] },
  { domain: 'D', label: '仓管审批出库', path: '/outbound', screenId: 'outbound', roles: ['warehouse'] },
  { domain: 'F', label: '盘点与差异', path: '/stocktake', screenId: 'stocktake', roles: ['warehouse'] },
];

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
