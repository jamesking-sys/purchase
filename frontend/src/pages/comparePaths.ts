/** 路径唯一键（用于选中集合 / React key）。 */
export const keyOf = (path: string[]): string => path.join('/');

/** 把多行文本解析为科目路径列表：每行一条，路径用「/」分级，空段剔除，并按 keyOf 去重（避免重复行致重复 React key / 选中联动）。 */
export function parsePaths(text: string): string[][] {
  const seen = new Set<string>();
  const result: string[][] = [];
  for (const line of text.split('\n')) {
    const path = line
      .trim()
      .split('/')
      .map((s) => s.trim())
      .filter(Boolean);
    if (path.length === 0) {
      continue;
    }
    const k = keyOf(path);
    if (seen.has(k)) {
      continue;
    }
    seen.add(k);
    result.push(path);
  }
  return result;
}
