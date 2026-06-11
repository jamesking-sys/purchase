import { useState } from 'react';
import { Button, Input, InputNumber, Modal, Spin, Toast, Tree, Typography } from '@douyinfe/semi-ui';
import PageHeader from '../layout/PageHeader';
import { subjectApi, type SubjectHit, type SubjectTreeNode } from '../api/subjectApi';
import { budgetVsActualApi, type BudgetVsActualVO } from '../api/budgetVsActualApi';
import { useAsync } from '../hooks/useAsync';

const { Text } = Typography;

interface TreeNodeData {
  key: string;
  label: string;
  children?: TreeNodeData[];
}

/** 后端嵌套科目树 → Semi Tree treeData（label 含名称/编码/金额）。 */
function toTreeData(nodes: SubjectTreeNode[]): TreeNodeData[] {
  return nodes.map((n) => ({
    key: String(n.id),
    label: `${n.name} · ${n.code}${n.amount != null ? ` · ¥${n.amount}` : ''}`,
    children: n.children?.length ? toTreeData(n.children) : undefined,
  }));
}

/**
 * 预算科目划分（U14 AC-3，对接 U5 + U13）：科目树浏览 / 模糊搜索（祖先路径面包屑）/ 新增子级 / 删除（editor），
 * 内嵌「预算 vs 实际」对比卡（U13，超支仅标识不拦截）。
 */
export default function SubjectPage() {
  const { data: tree, loading, reload } = useAsync(() => subjectApi.tree());
  const [selectedId, setSelectedId] = useState<number>();
  const [keyword, setKeyword] = useState('');
  const [hits, setHits] = useState<SubjectHit[]>();
  const [addParent, setAddParent] = useState<number | null | undefined>(undefined); // undefined=关闭, null=根
  const [name, setName] = useState('');
  const [code, setCode] = useState('');
  const [saving, setSaving] = useState(false);

  async function doSearch() {
    if (!keyword.trim()) {
      Toast.warning('请输入搜索关键字');
      return;
    }
    try {
      setHits(await subjectApi.search(keyword.trim(), 20));
    } catch {
      /* toasted */
    }
  }

  async function submitAdd() {
    if (!name.trim() || !code.trim()) {
      Toast.warning('名称与编码必填');
      return;
    }
    setSaving(true);
    try {
      await subjectApi.addChild(addParent ?? null, name.trim(), code.trim());
      Toast.success('新增成功');
      setAddParent(undefined);
      setName('');
      setCode('');
      reload();
    } catch {
      /* toasted */
    } finally {
      setSaving(false);
    }
  }

  function doDelete() {
    if (selectedId == null) {
      return;
    }
    Modal.confirm({
      title: '删除科目',
      content: '确认删除选中科目？存在子级或被引用将被拒绝（40901）。',
      onOk: async () => {
        try {
          await subjectApi.remove(selectedId);
          Toast.success('已删除');
          setSelectedId(undefined);
          reload();
        } catch {
          /* toasted */
        }
      },
    });
  }

  return (
    <>
      <PageHeader domainTitle="B · 预算科目与审批" title="预算科目划分" pill="科目树" />

      <div className="pms-row">
        <div className="pms-card">
          <h3>科目树</h3>
          <div style={{ display: 'flex', gap: 8, marginBottom: 10 }}>
            <Button size="small" onClick={() => setAddParent(null)}>
              新增根科目
            </Button>
            <Button size="small" disabled={selectedId == null} onClick={() => setAddParent(selectedId)}>
              新增子级
            </Button>
            <Button size="small" type="danger" disabled={selectedId == null} onClick={doDelete}>
              删除选中
            </Button>
          </div>
          {loading ? (
            <Spin />
          ) : (tree ?? []).length === 0 ? (
            <div className="pms-empty">暂无科目，请先新增根科目或导入预算</div>
          ) : (
            <Tree
              treeData={toTreeData(tree ?? [])}
              onSelect={(key) => setSelectedId(key ? Number(key) : undefined)}
              style={{ maxHeight: 360, overflow: 'auto' }}
            />
          )}
        </div>

        <div className="pms-card">
          <h3>模糊搜索</h3>
          <div style={{ display: 'flex', gap: 8 }}>
            <Input placeholder="科目名/编码关键字" value={keyword} onChange={setKeyword} />
            <Button onClick={doSearch}>搜索</Button>
          </div>
          {hits && (
            <div style={{ marginTop: 10 }}>
              {hits.length === 0 ? (
                <div className="pms-empty">无命中</div>
              ) : (
                hits.map((h) => (
                  <div key={h.id} style={{ padding: '6px 0', borderBottom: '1px solid var(--pms-line)' }}>
                    <div>
                      {h.name} · {h.code}
                    </div>
                    <Text type="tertiary" size="small">
                      {h.ancestorPath.map((a) => a.name).join(' / ')}
                    </Text>
                  </div>
                ))
              )}
            </div>
          )}
        </div>
      </div>

      <VsActualCard />

      <Modal
        title={addParent == null ? '新增根科目' : '新增子级'}
        visible={addParent !== undefined}
        onOk={submitAdd}
        confirmLoading={saving}
        onCancel={() => setAddParent(undefined)}
        okText="新增"
        cancelText="取消"
      >
        <Input placeholder="科目名称" value={name} onChange={setName} style={{ marginBottom: 10 }} />
        <Input placeholder="科目编码" value={code} onChange={setCode} />
      </Modal>
    </>
  );
}

/** 预算 vs 实际对比卡（U13）：输入预算 id 查询，按科目展示预算/已发生/差额，超支红色标识（不拦截）。 */
function VsActualCard() {
  const [budgetId, setBudgetId] = useState<number>();
  const [data, setData] = useState<BudgetVsActualVO>();
  const [loading, setLoading] = useState(false);

  async function query() {
    if (budgetId == null) {
      Toast.warning('请输入预算 id');
      return;
    }
    setLoading(true);
    try {
      setData(await budgetVsActualApi.get(budgetId));
    } catch {
      /* toasted */
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="pms-card">
      <h3>预算 vs 实际（仅展示对比，不核减）</h3>
      <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
        <InputNumber
          placeholder="预算 id"
          min={1}
          value={budgetId}
          onChange={(v) => setBudgetId(v ? Number(v) : undefined)}
          style={{ width: 160 }}
        />
        <Button loading={loading} onClick={query}>
          查询对比
        </Button>
        {data && (
          <Text type="tertiary">
            {data.budgetName} · 预算 ¥{data.totalBudgeted} / 已发生 ¥{data.totalActual} / 差额 ¥
            {data.totalRemaining}
          </Text>
        )}
      </div>
      {data && (
        <table className="pms-table">
          <thead>
            <tr>
              <th>科目</th>
              <th style={{ width: 120 }}>预算</th>
              <th style={{ width: 120 }}>已发生</th>
              <th style={{ width: 120 }}>差额</th>
              <th style={{ width: 90 }}>状态</th>
            </tr>
          </thead>
          <tbody>
            {data.rows.map((r) => (
              <tr key={r.subjectId}>
                <td>{r.subjectName ?? `#${r.subjectId}`}</td>
                <td>¥{r.budgeted}</td>
                <td>¥{r.actual}</td>
                <td style={{ color: r.overspent ? 'var(--pms-danger)' : undefined }}>¥{r.remaining}</td>
                <td>
                  <span className={`pms-pill ${r.overspent ? 'danger' : 'ok'}`}>
                    {r.overspent ? '超支' : '正常'}
                  </span>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  );
}
