import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';

vi.mock('../api/inboundApi', () => ({
  inboundApi: { pendingItems: vi.fn(), create: vi.fn(), records: vi.fn() },
}));

import InboundPage from './InboundPage';

describe('InboundPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('渲染采购单选择与查询入口', () => {
    render(<InboundPage />);
    expect(screen.getByText('选择采购单')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '查询待收' })).toBeInTheDocument();
  });
});
