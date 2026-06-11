import { useState } from 'react';
import { Button, Checkbox, Input, Modal, Select, Spin, Tabs, TabPane, Tag, Toast } from '@douyinfe/semi-ui';
import PageHeader from '../layout/PageHeader';
import {
  orgApi,
  type DepartmentVO,
  type ProjectGroupVO,
  type SysUserVO,
} from '../api/orgApi';
import { useAsync } from '../hooks/useAsync';

/**
 * 组织与权限（U14，对接 U4）：部门 / 项目组 / 用户与角色分配三视图。写操作限 admin（前端入口已按角色显隐，
 * 后端权威鉴权；删除受限回 40901、编码重复 40902）。
 */
export default function OrgPage() {
  return (
    <>
      <PageHeader domainTitle="A · 基础与权限" title="组织与权限" pill="RBAC" />
      <div className="pms-card">
        <Tabs type="line">
          <TabPane tab="部门" itemKey="dept">
            <DepartmentsTab />
          </TabPane>
          <TabPane tab="项目组" itemKey="pg">
            <ProjectGroupsTab />
          </TabPane>
          <TabPane tab="用户与角色" itemKey="user">
            <UsersTab />
          </TabPane>
        </Tabs>
      </div>
    </>
  );
}

function DepartmentsTab() {
  const { data, loading, reload } = useAsync(() => orgApi.listDepartments());
  const [editing, setEditing] = useState<DepartmentVO | 'new' | null>(null);
  const [name, setName] = useState('');
  const [code, setCode] = useState('');
  const [saving, setSaving] = useState(false);

  async function save() {
    if (!name.trim() || !code.trim()) {
      Toast.warning('名称与编码必填');
      return;
    }
    setSaving(true);
    try {
      if (editing === 'new') {
        await orgApi.createDepartment({ name: name.trim(), code: code.trim() });
      } else if (editing) {
        await orgApi.updateDepartment(editing.id, { name: name.trim(), code: code.trim() });
      }
      Toast.success('已保存');
      setEditing(null);
      reload();
    } catch {
      /* toasted */
    } finally {
      setSaving(false);
    }
  }

  function del(d: DepartmentVO) {
    Modal.confirm({
      title: '删除部门',
      content: `确认删除「${d.name}」？存在子级或被引用将被拒绝（40901）。`,
      onOk: async () => {
        try {
          await orgApi.removeDepartment(d.id);
          Toast.success('已删除');
          reload();
        } catch {
          /* toasted */
        }
      },
    });
  }

  return (
    <>
      <Button
        theme="solid"
        style={{ marginBottom: 10 }}
        onClick={() => {
          setName('');
          setCode('');
          setEditing('new');
        }}
      >
        新增部门
      </Button>
      {loading ? (
        <Spin />
      ) : (
        <table className="pms-table">
          <thead>
            <tr>
              <th>名称</th>
              <th>编码</th>
              <th style={{ width: 140 }}>操作</th>
            </tr>
          </thead>
          <tbody>
            {(data ?? []).map((d) => (
              <tr key={d.id}>
                <td>{d.name}</td>
                <td>{d.code}</td>
                <td>
                  <Button
                    size="small"
                    theme="borderless"
                    onClick={() => {
                      setName(d.name);
                      setCode(d.code);
                      setEditing(d);
                    }}
                  >
                    编辑
                  </Button>
                  <Button size="small" theme="borderless" type="danger" onClick={() => del(d)}>
                    删除
                  </Button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
      <Modal
        title={editing === 'new' ? '新增部门' : '编辑部门'}
        visible={editing != null}
        onOk={save}
        confirmLoading={saving}
        onCancel={() => setEditing(null)}
        okText="保存"
        cancelText="取消"
      >
        <Input placeholder="部门名称" value={name} onChange={setName} style={{ marginBottom: 10 }} />
        <Input placeholder="部门编码" value={code} onChange={setCode} />
      </Modal>
    </>
  );
}

function ProjectGroupsTab() {
  const { data, loading, reload } = useAsync(() => orgApi.listProjectGroups());
  const { data: depts } = useAsync(() => orgApi.listDepartments());
  const [editing, setEditing] = useState<ProjectGroupVO | 'new' | null>(null);
  const [name, setName] = useState('');
  const [code, setCode] = useState('');
  const [deptId, setDeptId] = useState<number>();
  const [saving, setSaving] = useState(false);

  async function save() {
    if (!name.trim() || !code.trim() || deptId == null) {
      Toast.warning('名称、编码、所属部门必填');
      return;
    }
    setSaving(true);
    try {
      const req = { name: name.trim(), code: code.trim(), departmentId: deptId };
      if (editing === 'new') {
        await orgApi.createProjectGroup(req);
      } else if (editing) {
        await orgApi.updateProjectGroup(editing.id, req);
      }
      Toast.success('已保存');
      setEditing(null);
      reload();
    } catch {
      /* toasted */
    } finally {
      setSaving(false);
    }
  }

  function del(pg: ProjectGroupVO) {
    Modal.confirm({
      title: '删除项目组',
      content: `确认删除「${pg.name}」？被引用将被拒绝（40901）。`,
      onOk: async () => {
        try {
          await orgApi.removeProjectGroup(pg.id);
          Toast.success('已删除');
          reload();
        } catch {
          /* toasted */
        }
      },
    });
  }

  return (
    <>
      <Button
        theme="solid"
        style={{ marginBottom: 10 }}
        onClick={() => {
          setName('');
          setCode('');
          setDeptId(undefined);
          setEditing('new');
        }}
      >
        新增项目组
      </Button>
      {loading ? (
        <Spin />
      ) : (
        <table className="pms-table">
          <thead>
            <tr>
              <th>名称</th>
              <th>编码</th>
              <th>所属部门</th>
              <th style={{ width: 140 }}>操作</th>
            </tr>
          </thead>
          <tbody>
            {(data ?? []).map((pg) => (
              <tr key={pg.id}>
                <td>{pg.name}</td>
                <td>{pg.code}</td>
                <td>{pg.departmentName}</td>
                <td>
                  <Button
                    size="small"
                    theme="borderless"
                    onClick={() => {
                      setName(pg.name);
                      setCode(pg.code);
                      setDeptId(pg.departmentId);
                      setEditing(pg);
                    }}
                  >
                    编辑
                  </Button>
                  <Button size="small" theme="borderless" type="danger" onClick={() => del(pg)}>
                    删除
                  </Button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
      <Modal
        title={editing === 'new' ? '新增项目组' : '编辑项目组'}
        visible={editing != null}
        onOk={save}
        confirmLoading={saving}
        onCancel={() => setEditing(null)}
        okText="保存"
        cancelText="取消"
      >
        <Input placeholder="项目组名称" value={name} onChange={setName} style={{ marginBottom: 10 }} />
        <Input placeholder="项目组编码" value={code} onChange={setCode} style={{ marginBottom: 10 }} />
        <Select
          placeholder="所属部门"
          style={{ width: '100%' }}
          value={deptId}
          onChange={(v) => setDeptId(v as number)}
          optionList={(depts ?? []).map((d) => ({ label: `${d.name}（${d.code}）`, value: d.id }))}
        />
      </Modal>
    </>
  );
}

function UsersTab() {
  const { data, loading, reload } = useAsync(() => orgApi.listUsers());
  const { data: depts } = useAsync(() => orgApi.listDepartments());
  const { data: roles } = useAsync(() => orgApi.listRoles());
  const [editing, setEditing] = useState<SysUserVO | 'new' | null>(null);
  const [account, setAccount] = useState('');
  const [name, setName] = useState('');
  const [password, setPassword] = useState('');
  const [deptId, setDeptId] = useState<number>();
  const [saving, setSaving] = useState(false);
  const [roleUser, setRoleUser] = useState<SysUserVO>();
  const [roleIds, setRoleIds] = useState<number[]>([]);

  async function save() {
    if (!name.trim() || deptId == null) {
      Toast.warning('姓名与部门必填');
      return;
    }
    setSaving(true);
    try {
      if (editing === 'new') {
        if (!account.trim() || !password.trim()) {
          Toast.warning('账号与初始口令必填');
          setSaving(false);
          return;
        }
        await orgApi.createUser({ account: account.trim(), name: name.trim(), password, departmentId: deptId });
      } else if (editing) {
        await orgApi.updateUser(editing.id, { name: name.trim(), departmentId: deptId });
      }
      Toast.success('已保存');
      setEditing(null);
      reload();
    } catch {
      /* toasted */
    } finally {
      setSaving(false);
    }
  }

  function del(u: SysUserVO) {
    Modal.confirm({
      title: '删除用户',
      content: `确认删除「${u.name}（${u.account}）」？`,
      onOk: async () => {
        try {
          await orgApi.removeUser(u.id);
          Toast.success('已删除');
          reload();
        } catch {
          /* toasted */
        }
      },
    });
  }

  async function saveRoles() {
    if (!roleUser) {
      return;
    }
    try {
      await orgApi.assignRoles(roleUser.id, roleIds);
      Toast.success('角色已更新');
      setRoleUser(undefined);
      reload();
    } catch {
      /* toasted */
    }
  }

  return (
    <>
      <Button
        theme="solid"
        style={{ marginBottom: 10 }}
        onClick={() => {
          setAccount('');
          setName('');
          setPassword('');
          setDeptId(undefined);
          setEditing('new');
        }}
      >
        新增用户
      </Button>
      {loading ? (
        <Spin />
      ) : (
        <table className="pms-table">
          <thead>
            <tr>
              <th>账号</th>
              <th>姓名</th>
              <th>部门</th>
              <th>角色</th>
              <th style={{ width: 200 }}>操作</th>
            </tr>
          </thead>
          <tbody>
            {(data ?? []).map((u) => (
              <tr key={u.id}>
                <td>{u.account}</td>
                <td>{u.name}</td>
                <td>{u.departmentName}</td>
                <td>
                  {u.roles.map((r) => (
                    <Tag key={r.id} size="small" style={{ marginRight: 4 }}>
                      {r.name}
                    </Tag>
                  ))}
                </td>
                <td>
                  <Button
                    size="small"
                    theme="borderless"
                    onClick={() => {
                      setName(u.name);
                      setDeptId(u.departmentId);
                      setEditing(u);
                    }}
                  >
                    编辑
                  </Button>
                  <Button
                    size="small"
                    theme="borderless"
                    onClick={() => {
                      setRoleUser(u);
                      setRoleIds(u.roles.map((r) => r.id));
                    }}
                  >
                    角色
                  </Button>
                  <Button size="small" theme="borderless" type="danger" onClick={() => del(u)}>
                    删除
                  </Button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}

      <Modal
        title={editing === 'new' ? '新增用户' : '编辑用户'}
        visible={editing != null}
        onOk={save}
        confirmLoading={saving}
        onCancel={() => setEditing(null)}
        okText="保存"
        cancelText="取消"
      >
        {editing === 'new' && (
          <>
            <Input placeholder="登录账号" value={account} onChange={setAccount} style={{ marginBottom: 10 }} />
            <Input
              mode="password"
              placeholder="初始口令"
              value={password}
              onChange={setPassword}
              style={{ marginBottom: 10 }}
            />
          </>
        )}
        <Input placeholder="姓名" value={name} onChange={setName} style={{ marginBottom: 10 }} />
        <Select
          placeholder="所属部门"
          style={{ width: '100%' }}
          value={deptId}
          onChange={(v) => setDeptId(v as number)}
          optionList={(depts ?? []).map((d) => ({ label: `${d.name}（${d.code}）`, value: d.id }))}
        />
      </Modal>

      <Modal
        title={roleUser ? `分配角色 · ${roleUser.name}` : '分配角色'}
        visible={roleUser != null}
        onOk={saveRoles}
        onCancel={() => setRoleUser(undefined)}
        okText="保存"
        cancelText="取消"
      >
        <Checkbox.Group value={roleIds} onChange={(v) => setRoleIds(v as number[])}>
          {(roles ?? []).map((r) => (
            <Checkbox key={r.id} value={r.id} style={{ display: 'block', marginBottom: 6 }}>
              {r.name}（{r.code}）
            </Checkbox>
          ))}
        </Checkbox.Group>
      </Modal>
    </>
  );
}
