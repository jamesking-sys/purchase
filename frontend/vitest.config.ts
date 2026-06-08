import { defineConfig, mergeConfig } from 'vitest/config';
import viteConfig from './vite.config';

// Vitest 配置：复用 vite.config.ts（含 React 插件），叠加 test 段。
// 本文件不纳入 tsconfig 类型检查——vite@6（应用）与 vitest 内置 vite@5 的类型不互通，
// 合并仅为运行时行为；类型安全由被检查的 src 与 vite.config.ts 保证。
export default mergeConfig(
  viteConfig,
  defineConfig({
    test: {
      globals: true,
      environment: 'jsdom',
      setupFiles: './src/test/setup.ts',
    },
  }),
);
