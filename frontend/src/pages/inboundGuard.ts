import type { PendingItemVO } from '../api/inboundApi';

/** 入库前置核验结果。 */
export interface GuardResult {
  /** 本次实收 > 待收（累计超收）的明细 id 集合。 */
  overIds: number[];
  /** 已录入实收（>0）的明细数。 */
  enteredCount: number;
  /** 可提交：有录入且无超收。 */
  canSubmit: boolean;
}

/**
 * 入库前端核验（U14 AC-6）：逐明细比对本次实收与待收量，任一超收即不可提交（后端 42204 为权威兜底）。
 */
export function inboundGuard(pending: PendingItemVO[], received: Record<number, number>): GuardResult {
  const overIds = pending
    .filter((it) => (received[it.purchaseItemId] ?? 0) > it.remaining)
    .map((it) => it.purchaseItemId);
  const enteredCount = pending.filter((it) => (received[it.purchaseItemId] ?? 0) > 0).length;
  return { overIds, enteredCount, canSubmit: enteredCount > 0 && overIds.length === 0 };
}
