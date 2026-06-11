import { useState } from 'react';
import { Banner, Button, InputNumber, Select, Spin, Toast, Typography } from '@douyinfe/semi-ui';
import PageHeader from '../layout/PageHeader';
import { orgApi } from '../api/orgApi';
import { stocktakeApi, type ConfirmStocktakeVO, type CreateStocktakeVO } from '../api/stocktakeApi';
import { useAsync } from '../hooks/useAsync';
import { computeDiff, DIFF_LABEL, type DiffType } from './stocktakeDiff';

const { Text } = Typography;

const PILL_CLASS: Record<DiffType, string> = { gain: 'ok', loss: 'danger', none: '' };

/**
 * 盘点（U14 AC-9，对接 U12）：选项目组发起盘点 → 快照账面 → 逐项录入实盘（实时算盘盈/盘亏差异）→
 * 保存实盘 → 确认差异并调整库存（确认前自动持久化最新实盘，避免漏存）。限仓管员。
 */
export default function StocktakePage() {
  const { data: pgList, loading: pgLoading } = useAsync(() => orgApi.listProjectGroups());
  const [scopePgId, setScopePgId] = useState<number>();
  const [stocktake, setStocktake] = useState<CreateStocktakeVO>();
  const [actuals, setActuals] = useState<Record<number, number>>({});
  const [confirmed, setConfirmed] = useState<ConfirmStocktakeVO>();
  const [starting, setStarting] = useState(false);
  const [saving, setSaving] = useState(false);
  const [confirming, setConfirming] = useState(false);

  async function handleStart() {
    if (scopePgId == null) {
      return;
    }
    setStarting(true);
    try {
      const st = await stocktakeApi.create(scopePgId);
      setStocktake(st);
      setActuals(Object.fromEntries(st.items.map((it) => [it.stocktakeItemId, it.bookQty])));
      setConfirmed(undefined);
    } catch {
      /* apiClient 已 toast */
    } finally {
      setStarting(false);
    }
  }

  function buildItems() {
    return (stocktake?.items ?? []).map((it) => ({
      stocktakeItemId: it.stocktakeItemId,
      actualQty: actuals[it.stocktakeItemId] ?? it.bookQty,
    }));
  }

  async function handleSave() {
    if (!stocktake) {
      return;
    }
    setSaving(true);
    try {
      await stocktakeApi.saveActuals(stocktake.stocktakeId, buildItems());
      Toast.success('实盘已保存');
    } catch {
      /* toasted */
    } finally {
      setSaving(false);
    }
  }

  async function handleConfirm() {
    if (!stocktake) {
      return;
    }
    setConfirming(true);
    try {
      await stocktakeApi.saveActuals(stocktake.stocktakeId, buildItems()); // 确认前持久化最新实盘
      const result = await stocktakeApi.confirm(stocktake.stocktakeId);
      setConfirmed(result);
      Toast.success(`已确认，调整 ${result.adjustedCount} 个库存项`);
    } catch {
      /* toasted */
    } finally {
      setConfirming(false);
    }
  }

  function reset() {
    setStocktake(undefined);
    setConfirmed(undefined);
    setActuals({});
    setScopePgId(undefined);
  }

  return (
    <>
      <PageHeader
        domainTitle="F · 盘点"
        title="盘点与差异"
        pill={confirmed ? '已确认' : stocktake ? '盘点中' : '发起'}
      />

      {!stocktake ? (
        <div className="pms-card">
          <h3>发起盘点</h3>
          {pgLoading ? (
            <Spin />
          ) : (
            <div style={{ display: 'flex', gap: 12, alignItems: 'center' }}>
              <Select
                placeholder="选择盘点范围（项目组）"
                style={{ width: 280 }}
                value={scopePgId}
                onChange={(v) => setScopePgId(v as number)}
                optionList={(pgList ?? []).map((pg) => ({ label: `${pg.name}（${pg.code}）`, value: pg.id }))}
              />
              <Button theme="solid" loading={starting} disabled={scopePgId == null} onClick={handleStart}>
                发起盘点（快照账面）
              </Button>
            </div>
          )}
        </div>
      ) : (
        <div className="pms-card">
          <h3>录入实盘 · 盘点单 #{stocktake.stocktakeId}</h3>
          {confirmed && (
            <Banner
              type="success"
              description={`盘点已确认，调整 ${confirmed.adjustedCount} 个库存项；账实差异已记盘盈/盘亏流水。`}
              style={{ marginBottom: 12 }}
            />
          )}
          {stocktake.items.length === 0 ? (
            <div className="pms-empty">该项目组暂无库存项可盘点</div>
          ) : (
            <table className="pms-table">
              <thead>
                <tr>
                  <th>物料</th>
                  <th style={{ width: 110 }}>账面数</th>
                  <th style={{ width: 150 }}>实盘数</th>
                  <th style={{ width: 110 }}>差异</th>
                  <th style={{ width: 90 }}>类型</th>
                </tr>
              </thead>
              <tbody>
                {stocktake.items.map((it) => {
                  const actual = actuals[it.stocktakeItemId] ?? it.bookQty;
                  const { diff, diffType } = computeDiff(it.bookQty, actual);
                  return (
                    <tr key={it.stocktakeItemId}>
                      <td>{it.materialName}</td>
                      <td>{it.bookQty}</td>
                      <td>
                        <InputNumber
                          min={0}
                          value={actual}
                          disabled={!!confirmed}
                          onChange={(v) =>
                            setActuals((prev) => ({ ...prev, [it.stocktakeItemId]: Number(v) }))
                          }
                          style={{ width: 130 }}
                        />
                      </td>
                      <td>{diff > 0 ? `+${diff}` : diff}</td>
                      <td>
                        <span className={`pms-pill ${PILL_CLASS[diffType]}`}>{DIFF_LABEL[diffType]}</span>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          )}

          <div style={{ display: 'flex', gap: 12, marginTop: 14 }}>
            {confirmed ? (
              <Button onClick={reset}>新建盘点</Button>
            ) : (
              <>
                <Button loading={saving} onClick={handleSave} disabled={stocktake.items.length === 0}>
                  保存实盘
                </Button>
                <Button
                  theme="solid"
                  type="danger"
                  loading={confirming}
                  onClick={handleConfirm}
                  disabled={stocktake.items.length === 0}
                >
                  确认差异并调整库存
                </Button>
                <Button theme="borderless" onClick={reset}>
                  取消
                </Button>
              </>
            )}
          </div>
          <Text type="tertiary" size="small">
            差异为前端实时估算，最终以确认时后端按调整时库内当前值记账为准（盘点确认不可逆）。
          </Text>
        </div>
      )}
    </>
  );
}
