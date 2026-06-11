import { useState } from 'react';
import { Banner, Button, InputNumber, Spin, Toast, Typography } from '@douyinfe/semi-ui';
import PageHeader from '../layout/PageHeader';
import {
  inboundApi,
  type CreateInboundVO,
  type InboundOrderVO,
  type PendingItemVO,
} from '../api/inboundApi';
import { inboundGuard } from './inboundGuard';

const { Text } = Typography;

/**
 * 验收入库（U14 AC-6，对接 U9）：输入采购单号查待收明细 → 逐项录本次实收（前端实时核「累计超收」：本次 > 待收即标红禁用提交）→
 * 入库（后端 42204 超收兜底）→ 展示入库记录。限仓管员。
 */
export default function InboundPage() {
  const [poId, setPoId] = useState<number>();
  const [pending, setPending] = useState<PendingItemVO[]>();
  const [records, setRecords] = useState<InboundOrderVO[]>();
  const [received, setReceived] = useState<Record<number, number>>({});
  const [result, setResult] = useState<CreateInboundVO>();
  const [loading, setLoading] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  async function load(id: number) {
    setLoading(true);
    setResult(undefined);
    try {
      const [p, r] = await Promise.all([inboundApi.pendingItems(id), inboundApi.records(id, 1, 50)]);
      setPending(p);
      setRecords(r.records);
      setReceived({});
    } catch {
      setPending(undefined);
      setRecords(undefined);
    } finally {
      setLoading(false);
    }
  }

  function query() {
    if (poId == null) {
      Toast.warning('请输入采购单号');
      return;
    }
    void load(poId);
  }

  const guard = inboundGuard(pending ?? [], received);
  const overSet = new Set(guard.overIds);
  const enteredRows = (pending ?? []).filter((it) => (received[it.purchaseItemId] ?? 0) > 0);
  const canSubmit = poId != null && guard.canSubmit;

  async function submit() {
    if (!canSubmit) {
      return;
    }
    setSubmitting(true);
    try {
      const r = await inboundApi.create(
        poId,
        enteredRows.map((it) => ({ purchaseItemId: it.purchaseItemId, receivedQty: received[it.purchaseItemId] })),
      );
      setResult(r);
      Toast.success(`已入库，采购单状态：${r.purchaseOrderStatus === 'inbounded' ? '已入库' : '执行中'}`);
      await load(poId);
    } catch {
      /* apiClient 已 toast（含 42204 累计超收） */
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <>
      <PageHeader domainTitle="D · 采购入库与领用出库" title="验收入库" pill="入库" />

      <div className="pms-card">
        <h3>选择采购单</h3>
        <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
          <InputNumber
            placeholder="采购单号"
            min={1}
            value={poId}
            onChange={(v) => setPoId(v ? Number(v) : undefined)}
            style={{ width: 180 }}
          />
          <Button theme="solid" loading={loading} onClick={query}>
            查询待收
          </Button>
        </div>
      </div>

      {result && (
        <Banner
          type="success"
          style={{ marginBottom: 16 }}
          description={`入库单 #${result.inboundOrderId} 已生成，写入 ${result.items.length} 项库存。`}
        />
      )}

      {loading ? (
        <div className="pms-card" style={{ textAlign: 'center', padding: 24 }}>
          <Spin />
        </div>
      ) : (
        pending && (
          <div className="pms-card">
            <h3>待收明细 · 录入本次实收</h3>
            {pending.length === 0 ? (
              <div className="pms-empty">该采购单无明细</div>
            ) : (
              <>
                <table className="pms-table">
                  <thead>
                    <tr>
                      <th>物料</th>
                      <th style={{ width: 90 }}>采购量</th>
                      <th style={{ width: 90 }}>已收</th>
                      <th style={{ width: 90 }}>待收</th>
                      <th style={{ width: 160 }}>本次实收</th>
                    </tr>
                  </thead>
                  <tbody>
                    {pending.map((it) => {
                      const val = received[it.purchaseItemId] ?? 0;
                      const over = overSet.has(it.purchaseItemId);
                      return (
                        <tr key={it.purchaseItemId} style={over ? { background: 'rgba(249,57,32,.06)' } : undefined}>
                          <td>{it.materialName}</td>
                          <td>{it.qty}</td>
                          <td>{it.receivedQty}</td>
                          <td>{it.remaining}</td>
                          <td>
                            <InputNumber
                              min={0}
                              value={val}
                              onChange={(v) =>
                                setReceived((prev) => ({ ...prev, [it.purchaseItemId]: Number(v) }))
                              }
                              style={{ width: 120 }}
                            />
                            {over && (
                              <Text type="danger" size="small" style={{ marginLeft: 8 }}>
                                累计超收
                              </Text>
                            )}
                          </td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
                <div style={{ marginTop: 12 }}>
                  <Button theme="solid" loading={submitting} disabled={!canSubmit} onClick={submit}>
                    入库
                  </Button>
                  {guard.overIds.length > 0 && (
                    <Text type="danger" size="small" style={{ marginLeft: 12 }}>
                      存在超收明细，请修正后再入库
                    </Text>
                  )}
                </div>
              </>
            )}
          </div>
        )
      )}

      {records && records.length > 0 && (
        <div className="pms-card">
          <h3>入库记录</h3>
          <table className="pms-table">
            <thead>
              <tr>
                <th style={{ width: 100 }}>入库单</th>
                <th>明细（物料 × 实收）</th>
              </tr>
            </thead>
            <tbody>
              {records.map((rec) => (
                <tr key={rec.inboundOrderId}>
                  <td>#{rec.inboundOrderId}</td>
                  <td>{rec.items.map((i) => `${i.materialName}×${i.receivedQty}`).join('、')}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </>
  );
}
