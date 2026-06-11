/** 盘点差异类型（对齐后端 stocktake_item.diff_type）。 */
export type DiffType = 'gain' | 'loss' | 'none';

/** 差异类型中文标签。 */
export const DIFF_LABEL: Record<DiffType, string> = {
  gain: '盘盈',
  loss: '盘亏',
  none: '无差异',
};

/**
 * 前端实时差异计算（仅为即时反馈，权威以后端确认为准）。
 * diff = 实盘 − 账面；保留 3 位小数（对齐 NUMERIC(18,3)），按符号定盘盈/盘亏/无差异。
 */
export function computeDiff(bookQty: number, actualQty: number): { diff: number; diffType: DiffType } {
  const diff = Number((actualQty - bookQty).toFixed(3));
  const diffType: DiffType = diff > 0 ? 'gain' : diff < 0 ? 'loss' : 'none';
  return { diff, diffType };
}
