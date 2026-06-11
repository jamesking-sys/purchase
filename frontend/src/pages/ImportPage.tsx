import { useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Banner, Button, Input, Select, Spin, Toast, Typography } from '@douyinfe/semi-ui';
import PageHeader from '../layout/PageHeader';
import { ApiError } from '../api/client';
import { orgApi } from '../api/orgApi';
import { importApi, type AttachmentVO, type ErrorRow, type ImportResultVO } from '../api/importApi';
import { useAsync } from '../hooks/useAsync';

const { Text } = Typography;

const TEMPLATE_ERROR_CODES = new Set([42201, 42202]);

/**
 * 预算导入（U14 AC-1/AC-2，对接 U6）：选项目组 + 预算名 → 下载模板 → （可选）上传立项附件得 path →
 * 上传明细模板导入。校验失败按 errorRows 逐行红条呈现（不跳转）；全通过 Toast 并可跳比对/审批。限编制人。
 */
export default function ImportPage() {
  const navigate = useNavigate();
  const { data: pgList, loading: pgLoading } = useAsync(() => orgApi.listProjectGroups());
  const [pgId, setPgId] = useState<number>();
  const [budgetName, setBudgetName] = useState('');
  const [attachment, setAttachment] = useState<AttachmentVO>();
  const [templateFile, setTemplateFile] = useState<File>();
  const [errorRows, setErrorRows] = useState<ErrorRow[]>();
  const [result, setResult] = useState<ImportResultVO>();
  const [importing, setImporting] = useState(false);
  const [attaching, setAttaching] = useState(false);

  const attachRef = useRef<HTMLInputElement>(null);
  const templateRef = useRef<HTMLInputElement>(null);

  async function onPickAttachment(file: File) {
    if (pgId == null) {
      Toast.warning('请先选择项目组');
      return;
    }
    setAttaching(true);
    try {
      setAttachment(await importApi.uploadAttachment(file, pgId));
    } catch {
      /* toasted */
    } finally {
      setAttaching(false);
    }
  }

  async function doImport() {
    if (pgId == null || !budgetName.trim() || !templateFile) {
      return;
    }
    setImporting(true);
    setErrorRows(undefined);
    setResult(undefined);
    try {
      const r = await importApi.importBudget(templateFile, pgId, budgetName.trim(), attachment?.path, {
        silent: true,
      });
      setResult(r);
      Toast.success(`导入成功，共 ${r.importedRows} 行`);
    } catch (e) {
      if (e instanceof ApiError && TEMPLATE_ERROR_CODES.has(e.code)) {
        const rows = (e.data as { errorRows?: ErrorRow[] } | undefined)?.errorRows ?? [];
        setErrorRows(rows);
        Toast.error('模板校验未通过，请按下方提示修正');
      } else if (e instanceof ApiError) {
        Toast.error(e.message);
      }
    } finally {
      setImporting(false);
    }
  }

  return (
    <>
      <PageHeader domainTitle="B · 预算科目与审批" title="预算导入" pill="①导入" />

      <div className="pms-card">
        <h3>① 选择项目组与预算</h3>
        {pgLoading ? (
          <Spin />
        ) : (
          <div style={{ display: 'flex', gap: 12, flexWrap: 'wrap', alignItems: 'center' }}>
            <Select
              placeholder="项目组"
              style={{ width: 240 }}
              value={pgId}
              onChange={(v) => setPgId(v as number)}
              optionList={(pgList ?? []).map((pg) => ({ label: `${pg.name}（${pg.code}）`, value: pg.id }))}
            />
            <Input
              placeholder="预算名称"
              style={{ width: 240 }}
              value={budgetName}
              onChange={setBudgetName}
            />
            <Button onClick={() => void importApi.downloadTemplate()}>下载模板</Button>
          </div>
        )}
      </div>

      <div className="pms-card">
        <h3>② 上传与导入</h3>
        <input
          ref={attachRef}
          type="file"
          hidden
          onChange={(e) => {
            const f = e.target.files?.[0];
            e.target.value = ''; // 重置以便重选同名文件仍触发 onChange
            if (f) {
              void onPickAttachment(f);
            }
          }}
        />
        <input
          ref={templateRef}
          type="file"
          accept=".xlsx"
          hidden
          onChange={(e) => {
            setTemplateFile(e.target.files?.[0]);
            e.target.value = ''; // 重置以便重选同名文件仍触发 onChange（File 已存入 state）
          }}
        />
        <div style={{ display: 'flex', gap: 12, flexWrap: 'wrap', alignItems: 'center' }}>
          <Button loading={attaching} onClick={() => attachRef.current?.click()}>
            上传立项附件（可选）
          </Button>
          {attachment && <Text type="success">附件：{attachment.fileName}</Text>}
          <Button onClick={() => templateRef.current?.click()}>选择明细模板（.xlsx）</Button>
          {templateFile && <Text type="tertiary">{templateFile.name}</Text>}
          <Button
            theme="solid"
            loading={importing}
            disabled={pgId == null || !budgetName.trim() || !templateFile}
            onClick={doImport}
          >
            导入并校验
          </Button>
        </div>

        {result && (
          <Banner
            type="success"
            style={{ marginTop: 14 }}
            description={
              <span>
                导入成功：预算 #{result.budgetId} · {result.importedRows} 行。
                <Button theme="borderless" onClick={() => navigate('/budget/compare')}>
                  去科目比对
                </Button>
                <Button theme="borderless" onClick={() => navigate('/approval')}>
                  去提交审批
                </Button>
              </span>
            }
          />
        )}

        {errorRows && (
          <div style={{ marginTop: 14 }}>
            <div className="pms-note err">导入校验未通过，共 {errorRows.length} 行错误，请修正后重新导入：</div>
            <table className="pms-table">
              <thead>
                <tr>
                  <th style={{ width: 70 }}>行号</th>
                  <th>科目路径 / 编码</th>
                  <th style={{ width: 120 }}>金额</th>
                  <th>错误原因</th>
                </tr>
              </thead>
              <tbody>
                {errorRows.map((row) => (
                  <tr key={row.rowNo} style={{ background: 'rgba(249,57,32,.06)' }}>
                    <td>{row.rowNo}</td>
                    <td>{row.subjectPath}</td>
                    <td>{row.amount}</td>
                    <td style={{ color: 'var(--pms-danger)' }}>{row.reason}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </>
  );
}
