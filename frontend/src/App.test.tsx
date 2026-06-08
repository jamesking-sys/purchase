import { render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import App from './App';

/**
 * U0/U1 前端骨架冒烟：App 渲染系统标题，并在拉取 /api/health 后展示服务与数据库状态。
 * fetch 被 mock，不依赖真实后端。
 */
describe('App 骨架页', () => {
  beforeEach(() => {
    vi.stubGlobal(
      'fetch',
      vi.fn(() =>
        Promise.resolve({
          json: () => Promise.resolve({ code: 0, data: { service: 'up', db: 'ok' } }),
        } as unknown as Response),
      ),
    );
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
  });

  it('渲染系统标题', async () => {
    render(<App />);
    expect(screen.getByText('采购与资产管理系统')).toBeInTheDocument();
    await waitFor(() => expect(fetch).toHaveBeenCalledWith('/api/health'));
  });

  it('拉取 /api/health 后展示服务与数据库状态', async () => {
    render(<App />);
    await waitFor(() => expect(document.body.textContent).toContain('服务：up'));
    expect(document.body.textContent).toContain('数据库：ok');
    expect(fetch).toHaveBeenCalledWith('/api/health');
  });
});
