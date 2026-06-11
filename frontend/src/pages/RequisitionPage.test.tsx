import { beforeEach, describe, expect, it, vi, type Mock } from 'vitest';
import { render, screen } from '@testing-library/react';

vi.mock('../auth/useAuth', () => ({ useAuth: vi.fn() }));
vi.mock('../api/orgApi', () => ({ orgApi: { listProjectGroups: vi.fn() } }));
vi.mock('../api/requisitionApi', () => ({
  requisitionApi: { stockOptions: vi.fn(), create: vi.fn(), listMine: vi.fn() },
}));

import { useAuth } from '../auth/useAuth';
import { orgApi } from '../api/orgApi';
import { requisitionApi } from '../api/requisitionApi';
import RequisitionPage from './RequisitionPage';

describe('RequisitionPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    (useAuth as Mock).mockReturnValue({ me: { userId: 5, roles: ['requester'] }, hasRole: () => true });
    (orgApi.listProjectGroups as Mock).mockResolvedValue([{ id: 1, name: '组1', code: 'PG1' }]);
  });

  it('AC-7: 未选项目组提示先选；我的领用展示本人单据状态', async () => {
    (requisitionApi.listMine as Mock).mockResolvedValue({
      total: 1,
      records: [{ id: 7, status: 'pending_warehouse', applicantName: '张三', createdAt: '', itemCount: 2 }],
    });

    render(<RequisitionPage />);

    expect(await screen.findByText('请先选择项目组以加载可领用库存')).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: '发起领用' })).toBeInTheDocument();
    // 我的领用列表（按 me.userId 拉取）
    expect(requisitionApi.listMine).toHaveBeenCalledWith(5);
    expect(await screen.findByText('#7')).toBeInTheDocument();
    expect(screen.getByText('待仓管审批')).toBeInTheDocument();
  });
});
