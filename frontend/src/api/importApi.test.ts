import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiError } from './client';
import { importApi } from './importApi';

/** 用受控 fetch 桩验证导入失败路径：HTTP 400 + Result(42201, {errorRows}) → ApiError 透传 code 与 data.errorRows（AC-1）。 */
describe('importApi.importBudget', () => {
  beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn());
  });
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('校验失败：抛 ApiError，code=42201 且 data.errorRows 透传', async () => {
    const errorRows = [
      { rowNo: 2, subjectPath: '耗材/试剂', amount: 'abc', reason: '金额非法' },
      { rowNo: 5, subjectPath: '设备', amount: '100', reason: '非叶子科目' },
    ];
    (fetch as ReturnType<typeof vi.fn>).mockResolvedValue({
      status: 400,
      json: async () => ({ code: 42201, message: '模板校验失败', data: { errorRows } }),
    });

    const file = new File(['x'], 'b.xlsx');
    await expect(importApi.importBudget(file, 1, '预算A', undefined, { silent: true })).rejects.toMatchObject({
      code: 42201,
    });

    try {
      await importApi.importBudget(file, 1, '预算A', undefined, { silent: true });
    } catch (e) {
      expect(e).toBeInstanceOf(ApiError);
      expect((e as ApiError).data).toEqual({ errorRows });
    }
  });

  it('成功：返回 ImportResultVO', async () => {
    (fetch as ReturnType<typeof vi.fn>).mockResolvedValue({
      status: 200,
      json: async () => ({ code: 0, message: 'ok', data: { budgetId: 9, importedRows: 12 } }),
    });
    const file = new File(['x'], 'b.xlsx');
    await expect(importApi.importBudget(file, 1, '预算A', '/path/a.pdf', { silent: true })).resolves.toEqual({
      budgetId: 9,
      importedRows: 12,
    });
  });
});
