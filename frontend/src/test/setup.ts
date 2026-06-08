import '@testing-library/jest-dom';
import { vi } from 'vitest';

// jsdom 不实现 <canvas>。Semi UI 间接依赖 lottie-web，其在模块导入时会做 canvas 特性检测
// （getContext('2d') 后 set fillStyle / fillRect）。提供最小 2D 上下文桩，避免组件导入即抛错。
HTMLCanvasElement.prototype.getContext = vi.fn(() => ({
  fillRect: () => {},
  clearRect: () => {},
  getImageData: () => ({ data: [] }),
  putImageData: () => {},
  createImageData: () => [],
  setTransform: () => {},
  drawImage: () => {},
  save: () => {},
  restore: () => {},
  beginPath: () => {},
  moveTo: () => {},
  lineTo: () => {},
  closePath: () => {},
  stroke: () => {},
  translate: () => {},
  scale: () => {},
  rotate: () => {},
  arc: () => {},
  fill: () => {},
  measureText: () => ({ width: 0 }),
  transform: () => {},
  rect: () => {},
  clip: () => {},
})) as unknown as typeof HTMLCanvasElement.prototype.getContext;
