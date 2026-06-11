import { useState } from 'react';
import { Button, InputNumber, Modal, Spin, TextArea, Toast, Typography } from '@douyinfe/semi-ui';
import PageHeader from '../layout/PageHeader';
import { useAuth } from '../auth/useAuth';
import { approvalApi, type HistoryItemVO } from '../api/approvalApi';
import { useAsync } from '../hooks/useAsync';
import type { Page } from '../api/page';

const { Text } = Typography;

const EMPTY_PAGE = { records: [], total: 0, size: 0, current: 1 };
const NODE_LABEL: Record<string, string> = { purchase_mgr: '采购主管', dept_mgr: '部门主管' };

/**
 * 两级审批（U14 AC-4，对接 U7）：编制人提交预算审批；采购主管/部门主管处理待办（通过/驳回意见必填/查看流转）。
 * 按角色显隐两段功能（前端前置防护）；权威鉴权与节点-角色比对在后端。
 */
export default function ApprovalPage() {
  const { hasRole } = useAuth();
  const isApprover = hasRole('purchase_mgr') || hasRole('dept_mgr');
  const isEditor = hasRole('editor');

  const { data, loading, reload } = useAsync<Page<import('../api/approvalApi').ApprovalTodoVO>>(
    () => (isApprover ? approvalApi.todo(1, 50) : Promise.resolve(EMPTY_PAGE)),
    [isApprover],
  );

  const [bizId, setBizId] = useState<number>();
  const [submitting, setSubmitting] = useState(false);
  const [actingId, setActingId] = useState<number>();
  const [rejectId, setRejectId] = useState<number>();
  const [opinion, setOpinion] = useState('');
  const [rejecting, setRejecting] = useState(false);
  const [history, setHistory] = useState<HistoryItemVO[]>();

  async function submit() {
    if (bizId == null) {
      Toast.warning('请输入预算 id');
      return;
    }
    setSubmitting(true);
    try {
      await approvalApi.submit('budget', bizId);
      Toast.success('已提交至采购主管');
      setBizId(undefined);
      reload();
    } catch {
      /* toasted */
    } finally {
      setSubmitting(false);
    }
  }

  async function approve(id: number) {
    setActingId(id);
    try {
      await approvalApi.approve(id);
      Toast.success('已通过');
      reload();
    } catch {
      /* toasted */
    } finally {
      setActingId(undefined);
    }
  }

  async function submitReject() {
    if (rejectId == null || !opinion.trim()) {
      Toast.warning('请填写驳回意见');
      return;
    }
    setRejecting(true);
    try {
      await approvalApi.reject(rejectId, opinion.trim());
      Toast.success('已驳回');
      setRejectId(undefined);
      setOpinion('');
      reload();
    } catch {
      /* toasted */
    } finally {
      setRejecting(false);
    }
  }

  async function showHistory(id: number) {
    try {
      setHistory(await approvalApi.history(id));
    } catch {
      /* toasted */
    }
  }

  const todos = data?.records ?? [];

  return (
    <>
      <PageHeader domainTitle="B · 预算科目与审批" title="两级审批" pill="③审批" />

      {isEditor && (
        <div className="pms-card">
          <h3>提交审批（编制人）</h3>
          <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
            <InputNumber
              placeholder="预算 id"
              min={1}
              value={bizId}
              onChange={(v) => setBizId(v ? Number(v) : undefined)}
              style={{ width: 160 }}
            />
            <Button theme="solid" loading={submitting} onClick={submit}>
              提交审批
            </Button>
            <Text type="tertiary" size="small">
              提交后进入「采购主管 → 部门主管」两级审批。
            </Text>
          </div>
        </div>
      )}

      {isApprover && (
        <div className="pms-card">
          <h3>我的审批待办</h3>
          {loading ? (
            <Spin />
          ) : todos.length === 0 ? (
            <div className="pms-empty">暂无待审批</div>
          ) : (
            <table className="pms-table">
              <thead>
                <tr>
                  <th>预算 / 项目组</th>
                  <th style={{ width: 110 }}>当前节点</th>
                  <th style={{ width: 240 }}>操作</th>
                </tr>
              </thead>
              <tbody>
                {todos.map((t) => (
                  <tr key={t.approvalId}>
                    <td>
                      {t.budgetName} · {t.projectGroupName}
                    </td>
                    <td>{NODE_LABEL[t.node] ?? t.node}</td>
                    <td style={{ display: 'flex', gap: 8 }}>
                      <Button
                        size="small"
                        theme="solid"
                        loading={actingId === t.approvalId}
                        onClick={() => approve(t.approvalId)}
                      >
                        通过
                      </Button>
                      <Button size="small" type="danger" onClick={() => setRejectId(t.approvalId)}>
                        驳回
                      </Button>
                      <Button size="small" theme="borderless" onClick={() => showHistory(t.approvalId)}>
                        流转
                      </Button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>
      )}

      {!isEditor && !isApprover && (
        <div className="pms-card">
          <div className="pms-empty">当前账号无审批相关角色（需编制人 / 采购主管 / 部门主管）。</div>
        </div>
      )}

      <Modal
        title="驳回审批"
        visible={rejectId != null}
        onOk={submitReject}
        confirmLoading={rejecting}
        onCancel={() => {
          setRejectId(undefined);
          setOpinion('');
        }}
        okText="确认驳回"
        cancelText="取消"
      >
        <TextArea
          placeholder="请填写驳回意见（必填）"
          value={opinion}
          onChange={setOpinion}
          maxCount={512}
          rows={3}
        />
      </Modal>

      <Modal
        title="审批流转历史"
        visible={history != null}
        footer={null}
        onCancel={() => setHistory(undefined)}
      >
        {(history ?? []).length === 0 ? (
          <div className="pms-empty">暂无流转记录</div>
        ) : (
          <table className="pms-table">
            <thead>
              <tr>
                <th style={{ width: 50 }}>序</th>
                <th>节点</th>
                <th>处理人</th>
                <th>动作</th>
                <th>意见</th>
              </tr>
            </thead>
            <tbody>
              {(history ?? []).map((h) => (
                <tr key={h.nodeSeq}>
                  <td>{h.nodeSeq}</td>
                  <td>{NODE_LABEL[h.node] ?? h.node}</td>
                  <td>{h.approverName}</td>
                  <td>{h.action}</td>
                  <td>{h.opinion ?? '—'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </Modal>
    </>
  );
}
