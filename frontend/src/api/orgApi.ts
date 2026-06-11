import { apiClient, type RequestOpts } from './client';

/** 部门视图（对齐 U4 DepartmentVO）。 */
export interface DepartmentVO {
  id: number;
  name: string;
  code: string;
}

/** 项目组视图（对齐 U4 ProjectGroupVO）。 */
export interface ProjectGroupVO {
  id: number;
  name: string;
  code: string;
  departmentId: number;
  departmentName: string;
}

/** 角色视图（对齐 U4 RoleVO）。 */
export interface RoleVO {
  id: number;
  code: string;
  name: string;
}

/** 用户视图（对齐 U4 SysUserVO，绝不含口令）。 */
export interface SysUserVO {
  id: number;
  account: string;
  name: string;
  departmentId: number;
  departmentName: string;
  roles: RoleVO[];
}

/** 组织/角色接口（U4）：查询仅需登录态；增删改与授角限 admin。 */
export const orgApi = {
  // ---- 部门 ----
  listDepartments(opts?: RequestOpts): Promise<DepartmentVO[]> {
    return apiClient.get<DepartmentVO[]>('/api/org/departments', opts);
  },
  createDepartment(req: { name: string; code: string }, opts?: RequestOpts): Promise<number> {
    return apiClient.post<number>('/api/org/departments', req, opts);
  },
  updateDepartment(id: number, req: { name: string; code: string }, opts?: RequestOpts): Promise<void> {
    return apiClient.put<void>(`/api/org/departments/${id}`, req, opts);
  },
  removeDepartment(id: number, opts?: RequestOpts): Promise<void> {
    return apiClient.del<void>(`/api/org/departments/${id}`, opts);
  },

  // ---- 项目组 ----
  listProjectGroups(departmentId?: number, opts?: RequestOpts): Promise<ProjectGroupVO[]> {
    const q = departmentId != null ? `?departmentId=${departmentId}` : '';
    return apiClient.get<ProjectGroupVO[]>(`/api/org/project-groups${q}`, opts);
  },
  createProjectGroup(req: { name: string; code: string; departmentId: number }, opts?: RequestOpts): Promise<number> {
    return apiClient.post<number>('/api/org/project-groups', req, opts);
  },
  updateProjectGroup(
    id: number,
    req: { name: string; code: string; departmentId: number },
    opts?: RequestOpts,
  ): Promise<void> {
    return apiClient.put<void>(`/api/org/project-groups/${id}`, req, opts);
  },
  removeProjectGroup(id: number, opts?: RequestOpts): Promise<void> {
    return apiClient.del<void>(`/api/org/project-groups/${id}`, opts);
  },

  // ---- 用户 ----
  listUsers(departmentId?: number, opts?: RequestOpts): Promise<SysUserVO[]> {
    const q = departmentId != null ? `?departmentId=${departmentId}` : '';
    return apiClient.get<SysUserVO[]>(`/api/org/users${q}`, opts);
  },
  createUser(
    req: { account: string; name: string; password: string; departmentId: number },
    opts?: RequestOpts,
  ): Promise<number> {
    return apiClient.post<number>('/api/org/users', req, opts);
  },
  updateUser(id: number, req: { name: string; departmentId: number }, opts?: RequestOpts): Promise<void> {
    return apiClient.put<void>(`/api/org/users/${id}`, req, opts);
  },
  removeUser(id: number, opts?: RequestOpts): Promise<void> {
    return apiClient.del<void>(`/api/org/users/${id}`, opts);
  },
  assignRoles(id: number, roleIds: number[], opts?: RequestOpts): Promise<RoleVO[]> {
    return apiClient.put<RoleVO[]>(`/api/org/users/${id}/roles`, { roleIds }, opts);
  },

  // ---- 角色字典 ----
  listRoles(opts?: RequestOpts): Promise<RoleVO[]> {
    return apiClient.get<RoleVO[]>('/api/org/roles', opts);
  },
};
