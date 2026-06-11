import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Button, Checkbox, TextArea, Toast, Typography } from '@douyinfe/semi-ui';
import PageHeader from '../layout/PageHeader';
import { subjectApi, type CompareResult } from '../api/subjectApi';
import { keyOf, parsePaths } from './comparePaths';

const { Text } = Typography;

/**
 * 科目比对·新增（U14 AC-3，对接 U5）：录入科目路径比对库中已存在/缺失，勾选缺失项确认新增（自上而下补建各级）。限编制人。
 */
export default function ComparePage() {
  const navigate = useNavigate();
  const [text, setText] = useState('');
  const [results, setResults] = useState<CompareResult[]>();
  const [selected, setSelected] = useState<Set<string>>(new Set());
  const [comparing, setComparing] = useState(false);
  const [confirming, setConfirming] = useState(false);

  async function doCompare() {
    const paths = parsePaths(text);
    if (paths.length === 0) {
      Toast.warning('请先录入至少一条科目路径');
      return;
    }
    setComparing(true);
    try {
      const r = await subjectApi.compare(paths);
      setResults(r);
      setSelected(new Set(r.filter((x) => x.status === 'MISSING').map((x) => keyOf(x.path)))); // 默认全选缺失
    } catch {
      /* toasted */
    } finally {
      setComparing(false);
    }
  }

  function toggle(path: string[]) {
    const k = keyOf(path);
    setSelected((prev) => {
      const next = new Set(prev);
      if (next.has(k)) {
        next.delete(k);
      } else {
        next.add(k);
      }
      return next;
    });
  }

  async function doConfirm() {
    const items = (results ?? [])
      .filter((r) => r.status === 'MISSING' && selected.has(keyOf(r.path)))
      .map((r) => ({ path: r.path }));
    if (items.length === 0) {
      Toast.warning('请勾选要新增的缺失科目');
      return;
    }
    setConfirming(true);
    try {
      const ids = await subjectApi.confirmAdd(items);
      Toast.success(`已新增 ${ids.length} 个科目`);
      await doCompare(); // 刷新比对结果（新增项应转为 EXISTS）
    } catch {
      /* toasted */
    } finally {
      setConfirming(false);
    }
  }

  const missingCount = (results ?? []).filter((r) => r.status === 'MISSING').length;

  return (
    <>
      <PageHeader domainTitle="B · 预算科目与审批" title="科目比对·新增" pill="②比对" />

      <div className="pms-card">
        <h3>录入待比对科目路径</h3>
        <Text type="tertiary" size="small">
          每行一条，路径用「/」分级，例如：耗材/试剂盒
        </Text>
        <TextArea
          rows={5}
          placeholder={'耗材/试剂盒\n设备/GPU 服务器'}
          value={text}
          onChange={setText}
          style={{ marginTop: 8 }}
        />
        <div style={{ marginTop: 12 }}>
          <Button theme="solid" loading={comparing} onClick={doCompare}>
            比对
          </Button>
        </div>
      </div>

      {results && (
        <div className="pms-card">
          <h3>
            比对结果（缺失 {missingCount} 项）
          </h3>
          {results.length === 0 ? (
            <div className="pms-empty">无比对结果</div>
          ) : (
            <table className="pms-table">
              <thead>
                <tr>
                  <th style={{ width: 50 }}>选</th>
                  <th>科目路径</th>
                  <th style={{ width: 110 }}>状态</th>
                  <th style={{ width: 140 }}>建议编码</th>
                </tr>
              </thead>
              <tbody>
                {results.map((r) => {
                  const missing = r.status === 'MISSING';
                  return (
                    <tr key={keyOf(r.path)}>
                      <td>
                        {missing && (
                          <Checkbox
                            checked={selected.has(keyOf(r.path))}
                            onChange={() => toggle(r.path)}
                            aria-label={`选择 ${keyOf(r.path)}`}
                          />
                        )}
                      </td>
                      <td>{r.path.join(' / ')}</td>
                      <td>
                        <span className={`pms-pill ${missing ? 'danger' : 'ok'}`}>
                          {missing ? '缺失' : '已存在'}
                        </span>
                      </td>
                      <td>{r.suggestedCode ?? '—'}</td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          )}
          <div style={{ display: 'flex', gap: 12, marginTop: 12 }}>
            <Button
              theme="solid"
              loading={confirming}
              disabled={selected.size === 0}
              onClick={doConfirm}
            >
              确认新增（{selected.size}）
            </Button>
            <Button theme="borderless" onClick={() => navigate('/approval')}>
              去提交审批
            </Button>
          </div>
        </div>
      )}
    </>
  );
}
