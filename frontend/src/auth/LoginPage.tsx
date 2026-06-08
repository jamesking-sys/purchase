import { useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { Button, Card, Form, Typography } from '@douyinfe/semi-ui';
import { apiClient } from '../api/client';
import { tokenStore } from './tokenStore';
import { authStore } from './authStore';
import type { LoginResp, MeResp } from '../api/types';

interface LoginForm {
  account: string;
  password: string;
}

const { Title, Text } = Typography;

/**
 * 登录页：提交 → POST /api/auth/login 存 token → GET /api/auth/me 填会话态 → 回跳原目标（或 /dashboard）。
 * 错误由 apiClient 统一 toast。
 */
export default function LoginPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const [loading, setLoading] = useState(false);
  const state = location.state as { from?: { pathname?: string } } | null;
  const from = state?.from?.pathname ?? '/dashboard';

  const handleSubmit = async (values: LoginForm) => {
    setLoading(true);
    try {
      const { token } = await apiClient.post<LoginResp>('/api/auth/login', values);
      tokenStore.set(token);
      const me = await apiClient.get<MeResp>('/api/auth/me');
      authStore.setMe(me);
      navigate(from, { replace: true });
    } catch {
      // 失败已由 apiClient toast
    } finally {
      setLoading(false);
    }
  };

  return (
    <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', height: '100vh',
      background: 'var(--semi-color-fill-0)' }}>
      <Card style={{ width: 360 }}>
        <Title heading={3} style={{ marginBottom: 4 }}>采购与资产管理系统</Title>
        <Text type="tertiary">请登录</Text>
        <Form onSubmit={(values) => handleSubmit(values as LoginForm)} style={{ marginTop: 16 }}>
          <Form.Input
            field="account"
            label="账号"
            placeholder="请输入账号"
            rules={[{ required: true, message: '请输入账号' }]}
          />
          <Form.Input
            field="password"
            label="口令"
            mode="password"
            placeholder="请输入口令"
            rules={[{ required: true, message: '请输入口令' }]}
          />
          <Button htmlType="submit" theme="solid" type="primary" loading={loading} block style={{ marginTop: 8 }}>
            登录
          </Button>
        </Form>
      </Card>
    </div>
  );
}
