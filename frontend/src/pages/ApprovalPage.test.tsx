import { beforeEach, describe, expect, it, vi, type Mock } from 'vitest';
import { render, screen } from '@testing-library/react';

vi.mock('../auth/useAuth', () => ({ useAuth: vi.fn() }));
vi.mock('../api/approvalApi', () => ({
  approvalApi: { todo: vi.fn(), submit: vi.fn(), approve: vi.fn(), reject: vi.fn(), history: vi.fn() },
}));

import { useAuth } from '../auth/useAuth';
import { approvalApi } from '../api/approvalApi';
import ApprovalPage from './ApprovalPage';

function mockRoles(...roles: string[]) {
  (useAuth as Mock).mockReturnValue({
    me: { roles },
    hasRole: (r: string) => roles.includes(r) || roles.includes('admin'),
  });
}

describe('ApprovalPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('AC-4: 审批人看到待办与通过/驳回', async () => {
    mockRoles('purchase_mgr');
    (approvalApi.todo as Mock).mockResolvedValue({
      total: 1,
      records: [{ approvalId: 1, budgetName: '预算A', projectGroupName: '组1', node: 'purchase_mgr' }],
    });

    render(<ApprovalPage />);

    expect(await screen.findByText('我的审批待办')).toBeInTheDocument();
    expect(screen.getByText('预算A · 组1')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '通过' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '驳回' })).toBeInTheDocument();
  });

  it('编制人看到提交审批入口（不调待办）', async () => {
    mockRoles('editor');
    render(<ApprovalPage />);

    expect(await screen.findByText('提交审批（编制人）')).toBeInTheDocument();
    expect(approvalApi.todo).not.toHaveBeenCalled();
  });
});
