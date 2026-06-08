import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// 开发服务器默认 5173；/api 代理到后端 8080，避免本地跨域。
// 测试（Vitest）配置见 vitest.config.ts（合并本配置 + test 段）。
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
});
