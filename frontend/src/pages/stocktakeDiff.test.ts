import { describe, expect, it } from 'vitest';
import { computeDiff, DIFF_LABEL } from './stocktakeDiff';

describe('computeDiff', () => {
  it('实盘 > 账面 → 盘盈 gain', () => {
    expect(computeDiff(100, 105)).toEqual({ diff: 5, diffType: 'gain' });
    expect(DIFF_LABEL.gain).toBe('盘盈');
  });

  it('实盘 < 账面 → 盘亏 loss', () => {
    expect(computeDiff(40, 38)).toEqual({ diff: -2, diffType: 'loss' });
    expect(DIFF_LABEL.loss).toBe('盘亏');
  });

  it('实盘 == 账面 → 无差异 none', () => {
    expect(computeDiff(40, 40)).toEqual({ diff: 0, diffType: 'none' });
    expect(DIFF_LABEL.none).toBe('无差异');
  });

  it('保留 3 位小数，规避浮点误差', () => {
    expect(computeDiff(0.1, 0.3)).toEqual({ diff: 0.2, diffType: 'gain' });
  });
});
