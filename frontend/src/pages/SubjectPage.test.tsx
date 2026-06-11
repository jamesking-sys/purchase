import { beforeEach, describe, expect, it, vi, type Mock } from 'vitest';
import { render, screen } from '@testing-library/react';

vi.mock('../api/subjectApi', () => ({
  subjectApi: {
    tree: vi.fn(),
    search: vi.fn(),
    addChild: vi.fn(),
    compare: vi.fn(),
    confirmAdd: vi.fn(),
    remove: vi.fn(),
  },
}));
vi.mock('../api/budgetVsActualApi', () => ({ budgetVsActualApi: { get: vi.fn() } }));

import { subjectApi } from '../api/subjectApi';
import SubjectPage from './SubjectPage';

describe('SubjectPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('渲染科目树与内嵌「预算 vs 实际」对比卡', async () => {
    (subjectApi.tree as Mock).mockResolvedValue([
      { id: 1, parentId: null, name: '耗材', code: 'C1', level: 1, isLeaf: false, amount: 1000, children: [] },
    ]);

    render(<SubjectPage />);

    expect(await screen.findByText(/耗材/)).toBeInTheDocument();
    expect(screen.getByText('预算 vs 实际（仅展示对比，不核减）')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '新增根科目' })).toBeInTheDocument();
  });
});
