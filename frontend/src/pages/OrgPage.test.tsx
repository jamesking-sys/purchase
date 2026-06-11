import { beforeEach, describe, expect, it, vi, type Mock } from 'vitest';
import { render, screen } from '@testing-library/react';

vi.mock('../api/orgApi', () => ({
  orgApi: {
    listDepartments: vi.fn(),
    createDepartment: vi.fn(),
    updateDepartment: vi.fn(),
    removeDepartment: vi.fn(),
    listProjectGroups: vi.fn(),
    createProjectGroup: vi.fn(),
    updateProjectGroup: vi.fn(),
    removeProjectGroup: vi.fn(),
    listUsers: vi.fn(),
    createUser: vi.fn(),
    updateUser: vi.fn(),
    removeUser: vi.fn(),
    assignRoles: vi.fn(),
    listRoles: vi.fn(),
  },
}));

import { orgApi } from '../api/orgApi';
import OrgPage from './OrgPage';

describe('OrgPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    (orgApi.listProjectGroups as Mock).mockResolvedValue([]);
    (orgApi.listUsers as Mock).mockResolvedValue([]);
    (orgApi.listRoles as Mock).mockResolvedValue([]);
  });

  it('默认部门 tab 渲染部门列表与三个分页签', async () => {
    (orgApi.listDepartments as Mock).mockResolvedValue([{ id: 1, name: '财务部', code: 'FIN' }]);

    render(<OrgPage />);

    expect(await screen.findByText('财务部')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '新增部门' })).toBeInTheDocument();
    expect(screen.getByText('项目组')).toBeInTheDocument();
    expect(screen.getByText('用户与角色')).toBeInTheDocument();
  });
});
