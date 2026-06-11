import React from 'react';
import ReactDOM from 'react-dom/client';
import App from './App';
// Semi v2.74 起组件按需自动导入各自样式（lib/es 共置 CSS），入口 index.js 亦引入 _base/base.css；
// 无需再手动引入已废弃、且 Vite 6 exports 解析无法命中的全局 dist/css/semi.min.css。
// 全局主题：对齐原型的设计令牌 / 阶段配色 / 页面骨架（在 Semi 样式之后引入以覆盖语义令牌）。
import './styles/theme.css';

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>
);
