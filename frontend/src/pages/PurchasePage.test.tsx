import { beforeEach, describe, expect, it, vi, type Mock } from 'vitest';
import { fireEvent, render, screen } from '@testing-library/react';

vi.mock('../api/orgApi', () => ({ orgApi: { listProjectGroups: vi.fn() } }));
vi.mock('../api/purchaseApi', () => ({
  purchaseApi: { list: vi.fn(), create: vi.fn(), detail: vi.fn(), uploadNotes: vi.fn(), downloadNote: vi.fn() },
}));

import { orgApi } from '../api/orgApi';
import { purchaseApi } from '../api/purchaseApi';
import PurchasePage from './PurchasePage';

describe('PurchasePage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    (orgApi.listProjectGroups as Mock).mockResolvedValue([{ id: 1, name: '组1', code: 'PG1' }]);
    (purchaseApi.list as Mock).mockResolvedValue({ records: [], total: 0, size: 50, current: 1 });
  });

  it('渲染建单表单与列表；可添加/删除明细行', async () => {
    render(<PurchasePage />);

    expect(await screen.findByRole('heading', { name: '创建采购单' })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: '采购单列表' })).toBeInTheDocument();
    expect(screen.getByText('暂无采购单')).toBeInTheDocument();

    // 初始 1 行（无删除按钮），添加后 2 行（出现删除）
    expect(screen.queryByRole('button', { name: '删除' })).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '添加明细' }));
    expect(screen.getAllByRole('button', { name: '删除' })).toHaveLength(2);
  });
});
