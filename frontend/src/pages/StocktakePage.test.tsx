import { beforeEach, describe, expect, it, vi, type Mock } from 'vitest';
import { render, screen } from '@testing-library/react';

vi.mock('../api/orgApi', () => ({
  orgApi: { listProjectGroups: vi.fn() },
}));
vi.mock('../api/stocktakeApi', () => ({
  stocktakeApi: { create: vi.fn(), saveActuals: vi.fn(), confirm: vi.fn() },
}));

import { orgApi } from '../api/orgApi';
import StocktakePage from './StocktakePage';

describe('StocktakePage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('渲染发起盘点表单：未选范围时发起按钮禁用', async () => {
    (orgApi.listProjectGroups as Mock).mockResolvedValue([{ id: 1, name: '组1', code: 'PG1' }]);
    render(<StocktakePage />);

    expect(await screen.findByText('发起盘点')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /发起盘点（快照账面）/ })).toBeDisabled();
  });
});
