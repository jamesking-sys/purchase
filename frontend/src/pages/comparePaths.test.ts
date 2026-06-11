import { describe, expect, it } from 'vitest';
import { keyOf, parsePaths } from './comparePaths';

describe('parsePaths', () => {
  it('多行 + 「/」分级解析，剔除空白', () => {
    expect(parsePaths('耗材/试剂盒\n  设备 / GPU 服务器 \n\n')).toEqual([
      ['耗材', '试剂盒'],
      ['设备', 'GPU 服务器'],
    ]);
  });

  it('全空白返回空列表', () => {
    expect(parsePaths('\n  \n')).toEqual([]);
  });

  it('重复行按 keyOf 去重', () => {
    expect(parsePaths('耗材/试剂盒\n耗材/试剂盒\n耗材 / 试剂盒')).toEqual([['耗材', '试剂盒']]);
  });

  it('keyOf 用「/」连接', () => {
    expect(keyOf(['a', 'b'])).toBe('a/b');
  });
});
