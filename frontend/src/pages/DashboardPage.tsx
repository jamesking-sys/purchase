import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Button, Spin, Typography } from '@douyinfe/semi-ui';
import PageHeader from '../layout/PageHeader';
import { approvalApi, type ApprovalTodoVO } from '../api/approvalApi';
import { purchaseApi } from '../api/purchaseApi';
import { requisitionApi, type RequisitionTodoVO } from '../api/requisitionApi';
import { orgApi } from '../api/orgApi';
import type { Page } from '../api/page';

const { Text } = Typography;

/** 把 allSettled 结果取 fulfilled 值，rejected（含 403）归 null —— 失败卡显示「—」，不阻断其余（U14 §5.4）。 */
function settled<T>(r: PromiseSettledResult<T>): T | null {
  return r.status === 'fulfilled' ? r.value : null;
}

interface DashboardData {
  approvalTodo: Page<ApprovalTodoVO> | null;
  requisitionTodo: Page<RequisitionTodoVO> | null;
  executing: number | null;
  projectGroups: number | null;
}

interface TodoRow {
  key: string;
  kind: string;
  summary: string;
  target: string;
  bizId: number;
}

/**
 * 工作台（U14 AC-10）：4 项统计卡 + 我的待办。各数据源并行 allSettled 拉取，按当前角色可见性容错——
 * 无权限的接口（如非审批角色取审批待办）静默归「—」，不影响其余卡片。待办行可跳转对应处理页并带单据 id。
 */
export default function DashboardPage() {
  const navigate = useNavigate();
  const [data, setData] = useState<DashboardData>();
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let alive = true;
    Promise.allSettled([
      approvalApi.todo(1, 20, { silent: true }),
      requisitionApi.todo(undefined, 1, 20, { silent: true }),
      purchaseApi.countExecuting({ silent: true }),
      orgApi.listProjectGroups(undefined, { silent: true }),
    ]).then(([a, r, p, g]) => {
      if (!alive) {
        return;
      }
      setData({
        approvalTodo: settled(a),
        requisitionTodo: settled(r),
        executing: settled(p)?.total ?? null,
        projectGroups: settled(g)?.length ?? null,
      });
      setLoading(false);
    });
    return () => {
      alive = false;
    };
  }, []);

  const todoRows: TodoRow[] = [];
  for (const t of data?.approvalTodo?.records ?? []) {
    todoRows.push({
      key: `ap-${t.approvalId}`,
      kind: '审批',
      summary: `${t.budgetName ?? '预算'} · ${t.projectGroupName ?? ''}（节点 ${t.node}）`,
      target: '/approval',
      bizId: t.approvalId,
    });
  }
  for (const t of data?.requisitionTodo?.records ?? []) {
    todoRows.push({
      key: `rq-${t.id}`,
      kind: '出库',
      summary: `${t.projectGroupName ?? ''} · ${t.applicantName ?? ''} 申请领用`,
      target: '/outbound',
      bizId: t.id,
    });
  }

  return (
    <>
      <PageHeader domainTitle="A · 基础与权限" title="工作台" pill="聚合" />

      {loading ? (
        <div className="pms-card" style={{ textAlign: 'center', padding: 32 }}>
          <Spin tip="加载中…" />
        </div>
      ) : (
        <>
          <div className="pms-grid4" style={{ marginBottom: 16 }}>
            <Stat label="待我审批" value={data?.approvalTodo?.total} />
            <Stat label="待出库领用" value={data?.requisitionTodo?.total} />
            <Stat label="执行中采购" value={data?.executing ?? undefined} />
            <Stat label="在管项目" value={data?.projectGroups ?? undefined} />
          </div>

          <div className="pms-card">
            <h3>我的待办</h3>
            {todoRows.length === 0 ? (
              <div className="pms-empty">暂无待办</div>
            ) : (
              <table className="pms-table">
                <thead>
                  <tr>
                    <th style={{ width: 80 }}>类型</th>
                    <th>摘要</th>
                    <th style={{ width: 110 }}>操作</th>
                  </tr>
                </thead>
                <tbody>
                  {todoRows.map((row) => (
                    <tr key={row.key}>
                      <td>{row.kind}</td>
                      <td>{row.summary}</td>
                      <td>
                        <Button
                          size="small"
                          theme="borderless"
                          onClick={() => navigate(row.target, { state: { bizId: row.bizId } })}
                        >
                          {row.kind === '审批' ? '去审批' : '去出库'}
                        </Button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
            <Text type="tertiary" size="small">
              统计与待办按当前账号角色可见范围聚合；无权限的数据源显示「—」。
            </Text>
          </div>
        </>
      )}
    </>
  );
}

/** 统计卡：无权限/失败显示「—」。 */
function Stat({ label, value }: { label: string; value: number | undefined }) {
  return (
    <div className="pms-stat">
      <div className="l">{label}</div>
      <div className="n">{value ?? '—'}</div>
    </div>
  );
}
