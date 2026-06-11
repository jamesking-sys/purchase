/** 把多行文本解析为科目路径列表：每行一条，路径用「/」分级，空段剔除。 */
export function parsePaths(text: string): string[][] {
  return text
    .split('\n')
    .map((line) => line.trim())
    .filter(Boolean)
    .map((line) =>
      line
        .split('/')
        .map((s) => s.trim())
        .filter(Boolean),
    )
    .filter((path) => path.length > 0);
}

/** 路径唯一键（用于选中集合 / React key）。 */
export const keyOf = (path: string[]): string => path.join('/');
