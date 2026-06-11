import { beforeEach, describe, expect, it, vi, type Mock } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';

vi.mock('../api/approvalApi', () => ({
  approvalApi: { todo: vi.fn() },
}));
vi.mock('../api/purchaseApi', () => ({
  purchaseApi: { countExecuting: vi.fn() },
}));
vi.mock('../api/requisitionApi', () => ({
  requisitionApi: { todo: vi.fn() },
}));
vi.mock('../api/orgApi', () => ({
  orgApi: { listProjectGroups: vi.fn() },
}));

import { approvalApi } from '../api/approvalApi';
import { purchaseApi } from '../api/purchaseApi';
import { requisitionApi } from '../api/requisitionApi';
import { orgApi } from '../api/orgApi';
import DashboardPage from './DashboardPage';

function renderPage() {
  return render(
    <MemoryRouter>
      <DashboardPage />
    </MemoryRouter>,
  );
}

describe('DashboardPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('AC-10: 聚合各数据源统计与待办', async () => {
    (approvalApi.todo as Mock).mockResolvedValue({
      total: 3,
      records: [{ approvalId: 11, node: 'purchase_mgr', budgetName: '预算A', projectGroupName: '组1' }],
    });
    (requisitionApi.todo as Mock).mockResolvedValue({
      total: 7,
      records: [{ id: 22, projectGroupName: '组2', applicantName: '张三', items: [] }],
    });
    (purchaseApi.countExecuting as Mock).mockResolvedValue({ total: 5, records: [] });
    (orgApi.listProjectGroups as Mock).mockResolvedValue([{ id: 1 }, { id: 2 }, { id: 3 }, { id: 4 }]);

    renderPage();

    expect(await screen.findByText('我的待办')).toBeInTheDocument();
    expect(screen.getByText('3')).toBeInTheDocument(); // 待我审批
    expect(screen.getByText('7')).toBeInTheDocument(); // 待出库领用
    expect(screen.getByText('5')).toBeInTheDocument(); // 执行中采购
    expect(screen.getByText('4')).toBeInTheDocument(); // 在管项目
    // 待办合并审批 + 出库两类
    expect(screen.getByText('去审批')).toBeInTheDocument();
    expect(screen.getByText('去出库')).toBeInTheDocument();
  });

  it('§5.4: 某数据源失败（如无权限）显示「—」，不阻断其余', async () => {
    (approvalApi.todo as Mock).mockRejectedValue(new Error('40301'));
    (requisitionApi.todo as Mock).mockResolvedValue({ total: 7, records: [] });
    (purchaseApi.countExecuting as Mock).mockRejectedValue(new Error('40301'));
    (orgApi.listProjectGroups as Mock).mockResolvedValue([{ id: 1 }]);

    renderPage();

    expect(await screen.findByText('我的待办')).toBeInTheDocument();
    expect(screen.getByText('7')).toBeInTheDocument(); // 可见的统计正常
    expect(screen.getAllByText('—').length).toBeGreaterThanOrEqual(2); // 两个失败源显示占位
  });
});
