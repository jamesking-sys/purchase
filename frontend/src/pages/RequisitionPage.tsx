import { useState } from 'react';
import { Button, Input, InputNumber, Select, Spin, Tag, Toast } from '@douyinfe/semi-ui';
import PageHeader from '../layout/PageHeader';
import { useAuth } from '../auth/useAuth';
import { orgApi } from '../api/orgApi';
import { requisitionApi, type StockOptionVO } from '../api/requisitionApi';
import { useAsync } from '../hooks/useAsync';
import type { Page } from '../api/page';

const STATUS: Record<string, { label: string; cls: string }> = {
  pending_warehouse: { label: '待仓管审批', cls: 'brand' },
  outbound: { label: '已出库', cls: 'ok' },
  rejected: { label: '已驳回', cls: 'danger' },
};

interface ItemRow {
  stockItemId?: number;
  qty: number;
}

/**
 * 领用申请（U14 AC-7，对接 U11）：选项目组拉取可领用库存（requester 可见的 stock-options）→ 逐项选物料 + 数量 + 用途
 * 发起领用（前端校验 qty>0），提交后转待仓管审批；下方「我的领用」展示本人各单状态。限领用人。
 */
export default function RequisitionPage() {
  const { me } = useAuth();
  const { data: pgList } = useAsync(() => orgApi.listProjectGroups());
  const [pgId, setPgId] = useState<number>();
  const [options, setOptions] = useState<StockOptionVO[]>();
  const [optLoading, setOptLoading] = useState(false);
  const [purpose, setPurpose] = useState('');
  const [items, setItems] = useState<ItemRow[]>([{ qty: 1 }]);
  const [creating, setCreating] = useState(false);

  const userId = me?.userId;
  const { data: mine, loading: mineLoading, reload } = useAsync<Page<import('../api/requisitionApi').RequisitionVO>>(
    () =>
      userId != null
        ? requisitionApi.listMine(userId)
        : Promise.resolve({ records: [], total: 0, size: 0, current: 1 }),
    [userId],
  );

  async function pickPg(id: number) {
    setPgId(id);
    setItems([{ qty: 1 }]);
    setOptLoading(true);
    try {
      setOptions(await requisitionApi.stockOptions(id));
    } catch {
      setOptions(undefined);
    } finally {
      setOptLoading(false);
    }
  }

  function setItem(idx: number, patch: Partial<ItemRow>) {
    setItems((prev) => prev.map((it, i) => (i === idx ? { ...it, ...patch } : it)));
  }

  async function submit() {
    if (pgId == null) {
      Toast.warning('请选择项目组');
      return;
    }
    const valid = items.filter((it) => it.stockItemId != null && it.qty > 0);
    if (valid.length === 0) {
      Toast.warning('请至少选择一项物料并填写数量（>0）');
      return;
    }
    setCreating(true);
    try {
      await requisitionApi.create(
        pgId,
        purpose.trim() || undefined,
        valid.map((it) => ({ stockItemId: it.stockItemId as number, qty: it.qty })),
      );
      Toast.success('领用已提交，待仓管审批');
      setItems([{ qty: 1 }]);
      setPurpose('');
      reload();
    } catch {
      /* toasted */
    } finally {
      setCreating(false);
    }
  }

  const optionList = (options ?? []).map((o) => ({
    label: `${o.materialName}（库存 ${o.quantity}）`,
    value: o.stockItemId,
  }));

  return (
    <>
      <PageHeader domainTitle="D · 采购入库与领用出库" title="领用申请" pill="领用" />

      <div className="pms-card">
        <h3>发起领用</h3>
        <div style={{ display: 'flex', gap: 12, flexWrap: 'wrap', marginBottom: 12 }}>
          <Select
            placeholder="项目组"
            style={{ width: 220 }}
            value={pgId}
            onChange={(v) => void pickPg(v as number)}
            optionList={(pgList ?? []).map((pg) => ({ label: `${pg.name}（${pg.code}）`, value: pg.id }))}
          />
          <Input placeholder="用途（可选）" value={purpose} onChange={setPurpose} style={{ width: 280 }} />
        </div>

        {pgId == null ? (
          <div className="pms-empty">请先选择项目组以加载可领用库存</div>
        ) : optLoading ? (
          <Spin />
        ) : (options ?? []).length === 0 ? (
          <div className="pms-empty">该项目组暂无可领用库存</div>
        ) : (
          <>
            <table className="pms-table">
              <thead>
                <tr>
                  <th>物料（含当前库存）</th>
                  <th style={{ width: 140 }}>领用数量</th>
                  <th style={{ width: 60 }} />
                </tr>
              </thead>
              <tbody>
                {items.map((it, idx) => (
                  <tr key={idx}>
                    <td>
                      <Select
                        placeholder="选择物料"
                        style={{ width: 280 }}
                        value={it.stockItemId}
                        onChange={(v) => setItem(idx, { stockItemId: v as number })}
                        optionList={optionList}
                      />
                    </td>
                    <td>
                      <InputNumber
                        min={0}
                        value={it.qty}
                        onChange={(v) => setItem(idx, { qty: Number(v) })}
                        style={{ width: 120 }}
                      />
                    </td>
                    <td>
                      {items.length > 1 && (
                        <Button
                          size="small"
                          theme="borderless"
                          type="danger"
                          onClick={() => setItems((prev) => prev.filter((_, i) => i !== idx))}
                        >
                          删除
                        </Button>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
            <div style={{ display: 'flex', gap: 12, marginTop: 12 }}>
              <Button onClick={() => setItems((prev) => [...prev, { qty: 1 }])}>添加物料</Button>
              <Button theme="solid" loading={creating} onClick={submit}>
                提交领用
              </Button>
            </div>
          </>
        )}
      </div>

      <div className="pms-card">
        <h3>我的领用</h3>
        {mineLoading ? (
          <Spin />
        ) : (mine?.records ?? []).length === 0 ? (
          <div className="pms-empty">暂无领用单</div>
        ) : (
          <table className="pms-table">
            <thead>
              <tr>
                <th style={{ width: 80 }}>单号</th>
                <th style={{ width: 120 }}>状态</th>
                <th style={{ width: 90 }}>明细数</th>
              </tr>
            </thead>
            <tbody>
              {(mine?.records ?? []).map((r) => {
                const st = STATUS[r.status] ?? { label: r.status, cls: '' };
                return (
                  <tr key={r.id}>
                    <td>#{r.id}</td>
                    <td>
                      <Tag color={st.cls === 'danger' ? 'red' : st.cls === 'ok' ? 'green' : 'blue'}>{st.label}</Tag>
                    </td>
                    <td>{r.itemCount}</td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        )}
      </div>
    </>
  );
}
