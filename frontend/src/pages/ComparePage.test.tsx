import { beforeEach, describe, expect, it, vi, type Mock } from 'vitest';
import { fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';

vi.mock('../api/subjectApi', () => ({
  subjectApi: { compare: vi.fn(), confirmAdd: vi.fn() },
}));

import { subjectApi } from '../api/subjectApi';
import ComparePage from './ComparePage';

describe('ComparePage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('AC-3: 比对后渲染已存在/缺失，缺失默认勾选', async () => {
    (subjectApi.compare as Mock).mockResolvedValue([
      { path: ['耗材', '试剂盒'], status: 'MISSING', subjectId: null, suggestedCode: 'SB-001' },
      { path: ['设备'], status: 'EXISTS', subjectId: 7, suggestedCode: null },
    ]);

    render(
      <MemoryRouter>
        <ComparePage />
      </MemoryRouter>,
    );

    fireEvent.change(screen.getByPlaceholderText(/耗材/), {
      target: { value: '耗材/试剂盒\n设备' },
    });
    fireEvent.click(screen.getByRole('button', { name: '比对' }));

    expect(await screen.findByText('缺失')).toBeInTheDocument();
    expect(screen.getByText('已存在')).toBeInTheDocument();
    expect(screen.getByText('耗材 / 试剂盒')).toBeInTheDocument();
    // 1 个缺失默认勾选 → 确认新增按钮计数为 1
    expect(screen.getByRole('button', { name: /确认新增（1）/ })).toBeEnabled();
  });
});
