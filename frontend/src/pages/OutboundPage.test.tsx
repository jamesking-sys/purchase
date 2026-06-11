import { beforeEach, describe, expect, it, vi, type Mock } from 'vitest';
import { render, screen } from '@testing-library/react';

vi.mock('../api/requisitionApi', () => ({
  requisitionApi: { todo: vi.fn(), approveOutbound: vi.fn(), reject: vi.fn() },
}));

import { requisitionApi } from '../api/requisitionApi';
import OutboundPage from './OutboundPage';

describe('OutboundPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('AC-8: 库存不足行标红并禁用审批出库；充足单可审批', async () => {
    (requisitionApi.todo as Mock).mockResolvedValue({
      total: 2,
      records: [
        {
          id: 1,
          projectGroupName: '组A',
          applicantName: '李四',
          items: [{ stockItemId: 9, materialName: '试剂盒', qty: 2, currentQuantity: 5, enough: true }],
        },
        {
          id: 2,
          projectGroupName: '组B',
          applicantName: '王五',
          items: [{ stockItemId: 8, materialName: 'GPU', qty: 10, currentQuantity: 2, enough: false }],
        },
      ],
    });

    render(<OutboundPage />);

    expect(await screen.findByText('库存不足，不可超发')).toBeInTheDocument();
    expect(screen.getByText('库存充足')).toBeInTheDocument();

    const approveButtons = screen.getAllByRole('button', { name: '审批出库' });
    expect(approveButtons).toHaveLength(2);
    expect(approveButtons[0]).toBeEnabled(); // 充足单可审批
    expect(approveButtons[1]).toBeDisabled(); // 不足单禁用（防超发前置）
  });

  it('空待办显示空态', async () => {
    (requisitionApi.todo as Mock).mockResolvedValue({ total: 0, records: [] });
    render(<OutboundPage />);
    expect(await screen.findByText('暂无待审批的领用单')).toBeInTheDocument();
  });
});
