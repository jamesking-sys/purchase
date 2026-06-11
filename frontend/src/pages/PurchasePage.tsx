import { useRef, useState } from 'react';
import { Button, Input, InputNumber, Modal, Select, Spin, Toast, Typography } from '@douyinfe/semi-ui';
import PageHeader from '../layout/PageHeader';
import { orgApi } from '../api/orgApi';
import {
  purchaseApi,
  type ItemReq,
  type PurchaseOrderDetailVO,
} from '../api/purchaseApi';
import { useAsync } from '../hooks/useAsync';

const { Text } = Typography;

const STATUS_LABEL: Record<string, string> = {
  executing: '执行中',
  inbounded: '已入库',
  void: '已作废',
};

const emptyItem = (): ItemReq => ({ subjectId: 0, materialName: '', qty: 1, amount: 0 });

/**
 * 采购执行（U14 AC-5，对接 U8）：选 approved 预算建采购单（含明细，预算非 approved 后端回 40903 Toast）；
 * 采购单列表 + 详情（明细 + 到货单上传/下载）。限编制人。
 */
export default function PurchasePage() {
  const { data: pgList } = useAsync(() => orgApi.listProjectGroups());
  const [budgetId, setBudgetId] = useState<number>();
  const [pgId, setPgId] = useState<number>();
  const [supplier, setSupplier] = useState('');
  const [contractNo, setContractNo] = useState('');
  const [items, setItems] = useState<ItemReq[]>([emptyItem()]);
  const [creating, setCreating] = useState(false);

  const [status, setStatus] = useState<string>();
  const { data: list, loading: listLoading, reload } = useAsync(() => purchaseApi.list(status, 1, 50), [status]);

  const [detail, setDetail] = useState<PurchaseOrderDetailVO>();
  const [detailLoading, setDetailLoading] = useState(false);
  const [uploading, setUploading] = useState(false);
  const noteRef = useRef<HTMLInputElement>(null);

  function setItem(idx: number, patch: Partial<ItemReq>) {
    setItems((prev) => prev.map((it, i) => (i === idx ? { ...it, ...patch } : it)));
  }

  async function create() {
    if (budgetId == null || pgId == null) {
      Toast.warning('请选择来源预算与项目组');
      return;
    }
    const valid = items.filter((it) => it.subjectId > 0 && it.materialName.trim() && it.qty > 0);
    if (valid.length === 0) {
      Toast.warning('请至少录入一条有效明细（科目/物料/数量）');
      return;
    }
    setCreating(true);
    try {
      const po = await purchaseApi.create({
        budgetId,
        projectGroupId: pgId,
        supplierName: supplier.trim() || undefined,
        contractNo: contractNo.trim() || undefined,
        items: valid,
      });
      Toast.success(`采购单 #${po.id} 已创建`);
      setItems([emptyItem()]);
      setSupplier('');
      setContractNo('');
      reload();
    } catch {
      /* apiClient 已 toast（40903 预算非已通过 / 40401 / 40001） */
    } finally {
      setCreating(false);
    }
  }

  async function openDetail(id: number) {
    setDetailLoading(true);
    try {
      setDetail(await purchaseApi.detail(id));
    } catch {
      /* toasted */
    } finally {
      setDetailLoading(false);
    }
  }

  async function uploadNotes(files: FileList) {
    if (!detail) {
      return;
    }
    setUploading(true);
    try {
      await purchaseApi.uploadNotes(detail.id, Array.from(files));
      Toast.success('到货单已上传');
      setDetail(await purchaseApi.detail(detail.id));
    } catch {
      /* toasted */
    } finally {
      setUploading(false);
    }
  }

  return (
    <>
      <PageHeader domainTitle="D · 采购入库与领用出库" title="采购执行" pill="建单" />

      <div className="pms-card">
        <h3>创建采购单</h3>
        <div style={{ display: 'flex', gap: 12, flexWrap: 'wrap', marginBottom: 12 }}>
          <InputNumber
            placeholder="来源预算 id（须已通过）"
            min={1}
            value={budgetId}
            onChange={(v) => setBudgetId(v ? Number(v) : undefined)}
            style={{ width: 200 }}
          />
          <Select
            placeholder="项目组"
            style={{ width: 200 }}
            value={pgId}
            onChange={(v) => setPgId(v as number)}
            optionList={(pgList ?? []).map((pg) => ({ label: `${pg.name}（${pg.code}）`, value: pg.id }))}
          />
          <Input placeholder="供应商（可选）" value={supplier} onChange={setSupplier} style={{ width: 180 }} />
          <Input placeholder="合同号（可选）" value={contractNo} onChange={setContractNo} style={{ width: 160 }} />
        </div>

        <table className="pms-table">
          <thead>
            <tr>
              <th style={{ width: 120 }}>科目 id</th>
              <th>物料名称</th>
              <th style={{ width: 120 }}>数量</th>
              <th style={{ width: 140 }}>金额</th>
              <th style={{ width: 60 }} />
            </tr>
          </thead>
          <tbody>
            {items.map((it, idx) => (
              <tr key={idx}>
                <td>
                  <InputNumber
                    min={1}
                    value={it.subjectId || undefined}
                    onChange={(v) => setItem(idx, { subjectId: v ? Number(v) : 0 })}
                    style={{ width: 100 }}
                  />
                </td>
                <td>
                  <Input value={it.materialName} onChange={(v) => setItem(idx, { materialName: v })} />
                </td>
                <td>
                  <InputNumber min={0} value={it.qty} onChange={(v) => setItem(idx, { qty: Number(v) })} style={{ width: 100 }} />
                </td>
                <td>
                  <InputNumber min={0} value={it.amount} onChange={(v) => setItem(idx, { amount: Number(v) })} style={{ width: 120 }} />
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
          <Button onClick={() => setItems((prev) => [...prev, emptyItem()])}>添加明细</Button>
          <Button theme="solid" loading={creating} onClick={create}>
            创建采购单
          </Button>
        </div>
      </div>

      <div className="pms-card">
        <h3>采购单列表</h3>
        <Select
          placeholder="全部状态"
          value={status}
          onChange={(v) => setStatus(v as string | undefined)}
          style={{ width: 160, marginBottom: 10 }}
          showClear
          optionList={[
            { label: '执行中', value: 'executing' },
            { label: '已入库', value: 'inbounded' },
          ]}
        />
        {listLoading ? (
          <Spin />
        ) : (list?.records ?? []).length === 0 ? (
          <div className="pms-empty">暂无采购单</div>
        ) : (
          <table className="pms-table">
            <thead>
              <tr>
                <th style={{ width: 80 }}>单号</th>
                <th>供应商</th>
                <th style={{ width: 100 }}>状态</th>
                <th style={{ width: 90 }}>操作</th>
              </tr>
            </thead>
            <tbody>
              {(list?.records ?? []).map((po) => (
                <tr key={po.id}>
                  <td>#{po.id}</td>
                  <td>{po.supplierName ?? '—'}</td>
                  <td>
                    <span className="pms-pill brand">{STATUS_LABEL[po.status] ?? po.status}</span>
                  </td>
                  <td>
                    <Button size="small" theme="borderless" onClick={() => openDetail(po.id)}>
                      详情
                    </Button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>

      <Modal
        title={detail ? `采购单 #${detail.id} 详情` : '采购单详情'}
        visible={detail != null || detailLoading}
        footer={null}
        onCancel={() => setDetail(undefined)}
        width={680}
      >
        {detailLoading || !detail ? (
          <Spin />
        ) : (
          <>
            <h4>采购明细</h4>
            <table className="pms-table">
              <thead>
                <tr>
                  <th>物料</th>
                  <th style={{ width: 90 }}>数量</th>
                  <th style={{ width: 90 }}>已收</th>
                  <th style={{ width: 120 }}>金额</th>
                </tr>
              </thead>
              <tbody>
                {(detail.items ?? []).map((it) => (
                  <tr key={it.id}>
                    <td>{it.materialName}</td>
                    <td>{it.qty}</td>
                    <td>{it.receivedQty}</td>
                    <td>¥{it.amount}</td>
                  </tr>
                ))}
              </tbody>
            </table>

            <h4 style={{ marginTop: 16 }}>到货单</h4>
            <input
              ref={noteRef}
              type="file"
              multiple
              hidden
              onChange={(e) => e.target.files?.length && void uploadNotes(e.target.files)}
            />
            <Button size="small" loading={uploading} onClick={() => noteRef.current?.click()}>
              上传到货单
            </Button>
            {(detail.deliveryNotes ?? []).length === 0 ? (
              <div className="pms-empty">暂无到货单</div>
            ) : (
              <table className="pms-table">
                <thead>
                  <tr>
                    <th>文件</th>
                    <th style={{ width: 120 }}>上传人</th>
                    <th style={{ width: 80 }}>下载</th>
                  </tr>
                </thead>
                <tbody>
                  {detail.deliveryNotes.map((n) => (
                    <tr key={n.id}>
                      <td>{n.fileName}</td>
                      <td>{n.uploadedByName}</td>
                      <td>
                        <Button size="small" theme="borderless" onClick={() => void purchaseApi.downloadNote(n)}>
                          下载
                        </Button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
            <Text type="tertiary" size="small">
              到货单作为采购履约留档；验收入库（实收）在「验收入库」页由仓管完成。
            </Text>
          </>
        )}
      </Modal>
    </>
  );
}
