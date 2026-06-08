import { describe, expect, it } from 'vitest';
import { isNavVisible, navConfig } from './navConfig';

describe('navConfig', () => {
  it('T-11: screenId 与原型屏集合一一对应（无缺漏/无多余）', () => {
    const expected = [
      'dashboard', 'org', 'import', 'subject', 'compare', 'approval',
      'purchase', 'inbound', 'requisition', 'outbound', 'stocktake',
    ].sort();
    const actual = navConfig.map((n) => n.screenId).sort();
    expect(actual).toEqual(expected);
  });

  it('路由路径唯一', () => {
    const paths = navConfig.map((n) => n.path);
    expect(new Set(paths).size).toBe(paths.length);
  });

  it('T-3: editor 仅见对应项，不见 admin/warehouse/审批 专属项', () => {
    const visible = navConfig.filter((n) => isNavVisible(n, ['editor'])).map((n) => n.screenId);
    expect(visible).toContain('dashboard'); // 无 roles 限定，全员可见
    expect(visible).toContain('import');
    expect(visible).toContain('subject');
    expect(visible).not.toContain('org'); // admin 专属
    expect(visible).not.toContain('inbound'); // warehouse 专属
    expect(visible).not.toContain('approval'); // purchase_mgr/dept_mgr 专属
  });

  it('admin 可见全部菜单项', () => {
    const visible = navConfig.filter((n) => isNavVisible(n, ['admin']));
    expect(visible.length).toBe(navConfig.length);
  });
});
