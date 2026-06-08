import { render, screen } from '@testing-library/react';
import { beforeEach, describe, expect, it } from 'vitest';
import App from './App';
import { tokenStore } from './auth/tokenStore';
import { authStore } from './auth/authStore';

/**
 * U3 外壳冒烟：未登录访问根路径，AuthGuard 守卫跳转到 /login，渲染登录页。
 */
describe('App 外壳', () => {
  beforeEach(() => {
    tokenStore.clear();
    authStore.clear();
    window.history.pushState({}, '', '/');
  });

  it('未登录渲染登录页', () => {
    render(<App />);
    expect(screen.getByText('请登录')).toBeInTheDocument();
  });
});
