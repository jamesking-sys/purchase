import { describe, expect, it } from 'vitest';
import { inboundGuard } from './inboundGuard';
import type { PendingItemVO } from '../api/inboundApi';

const item = (id: number, remaining: number): PendingItemVO => ({
  purchaseItemId: id,
  materialName: `m${id}`,
  qty: 10,
  receivedQty: 10 - remaining,
  remaining,
});

describe('inboundGuard (AC-6 累计超收)', () => {
  it('有录入且不超收 → 可提交', () => {
    const r = inboundGuard([item(1, 7), item(2, 5)], { 1: 3, 2: 5 });
    expect(r).toEqual({ overIds: [], enteredCount: 2, canSubmit: true });
  });

  it('任一明细本次实收 > 待收 → 标记超收且不可提交', () => {
    const r = inboundGuard([item(1, 7), item(2, 5)], { 1: 8, 2: 2 });
    expect(r.overIds).toEqual([1]);
    expect(r.canSubmit).toBe(false);
  });

  it('未录入任何实收 → 不可提交', () => {
    const r = inboundGuard([item(1, 7)], {});
    expect(r).toEqual({ overIds: [], enteredCount: 0, canSubmit: false });
  });
});
