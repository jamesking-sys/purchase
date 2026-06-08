import { beforeEach, describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import AuthGuard from './AuthGuard';
import { tokenStore } from './tokenStore';
import { authStore } from './authStore';

describe('AuthGuard', () => {
  beforeEach(() => {
    tokenStore.clear();
    authStore.clear();
  });

  it('T-1: 无 token 访问受保护路由跳转 /login', () => {
    render(
      <MemoryRouter initialEntries={['/approval']}>
        <Routes>
          <Route element={<AuthGuard />}>
            <Route path="/approval" element={<div>受保护内容</div>} />
          </Route>
          <Route path="/login" element={<div>登录页</div>} />
        </Routes>
      </MemoryRouter>,
    );
    expect(screen.getByText('登录页')).toBeInTheDocument();
    expect(screen.queryByText('受保护内容')).not.toBeInTheDocument();
  });
});
