import { useState } from 'react';
import { Button, Modal, Spin, Toast, Typography } from '@douyinfe/semi-ui';
import { TextArea } from '@douyinfe/semi-ui';
import PageHeader from '../layout/PageHeader';
import { requisitionApi, type RequisitionTodoVO } from '../api/requisitionApi';
import { useAsync } from '../hooks/useAsync';

const { Text } = Typography;

/**
 * 仓管审批出库（U14 AC-8，对接 U11）：待办按项目组列出，逐明细比对当前库存与申请量——
 * 库存不足行标红「库存不足，不可超发」并禁用「审批出库」（前端前置防护）；后端行锁防超发为权威兜底。
 * 支持驳回（意见必填）。限仓管员。
 */
export default function OutboundPage() {
  const { data, loading, error, reload } = useAsync(() => requisitionApi.todo(undefined, 1, 50));
  const [approvingId, setApprovingId] = useState<number>();
  const [rejectId, setRejectId] = useState<number>();
  const [opinion, setOpinion] = useState('');
  const [rejecting, setRejecting] = useState(false);

  async function approve(id: number) {
    setApprovingId(id);
    try {
      await requisitionApi.approveOutbound(id);
      Toast.success('已审批出库');
      reload();
    } catch {
      /* apiClient 已 toast（含 40904 库存不足、40903 状态冲突） */
    } finally {
      setApprovingId(undefined);
    }
  }

  async function submitReject() {
    if (rejectId == null || !opinion.trim()) {
      Toast.warning('请填写驳回意见');
      return;
    }
    setRejecting(true);
    try {
      await requisitionApi.reject(rejectId, opinion.trim());
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

  const rows: RequisitionTodoVO[] = data?.records ?? [];

  return (
    <>
      <PageHeader domainTitle="D · 采购入库与领用出库" title="仓管审批出库" pill="待办" />

      {loading ? (
        <div className="pms-card" style={{ textAlign: 'center', padding: 32 }}>
          <Spin tip="加载待办…" />
        </div>
      ) : error ? (
        <div className="pms-card">
          <div className="pms-note err">加载失败。</div>
          <Button onClick={reload}>重试</Button>
        </div>
      ) : rows.length === 0 ? (
        <div className="pms-card">
          <div className="pms-empty">暂无待审批的领用单</div>
        </div>
      ) : (
        rows.map((req) => {
          const allEnough = req.items.every((it) => it.enough);
          return (
            <div className="pms-card" key={req.id}>
              <h3>
                领用单 #{req.id} · {req.projectGroupName} · {req.applicantName}
              </h3>
              <table className="pms-table">
                <thead>
                  <tr>
                    <th>物料</th>
                    <th style={{ width: 110 }}>申请数量</th>
                    <th style={{ width: 110 }}>当前库存</th>
                    <th style={{ width: 160 }}>核验</th>
                  </tr>
                </thead>
                <tbody>
                  {req.items.map((it) => (
                    <tr key={it.stockItemId}>
                      <td>{it.materialName}</td>
                      <td>{it.qty}</td>
                      <td>{it.currentQuantity}</td>
                      <td>
                        {it.enough ? (
                          <span className="pms-pill ok">库存充足</span>
                        ) : (
                          <span className="pms-pill danger">库存不足，不可超发</span>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
              <div style={{ display: 'flex', gap: 12, marginTop: 12 }}>
                <Button
                  theme="solid"
                  loading={approvingId === req.id}
                  disabled={!allEnough}
                  onClick={() => approve(req.id)}
                >
                  审批出库
                </Button>
                <Button type="danger" theme="borderless" onClick={() => setRejectId(req.id)}>
                  驳回
                </Button>
                {!allEnough && (
                  <Text type="danger" size="small" style={{ alignSelf: 'center' }}>
                    含库存不足明细，需先补库存方可出库
                  </Text>
                )}
              </div>
            </div>
          );
        })
      )}

      <Modal
        title="驳回领用单"
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
    </>
  );
}
