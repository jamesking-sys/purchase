import { apiClient, type RequestOpts } from './client';

/** 科目树节点（对齐 U5 SubjectTreeNode）。lazy 取数时 children 为空，按 parentId 再拉单层。 */
export interface SubjectTreeNode {
  id: number;
  parentId: number | null;
  name: string;
  code: string;
  level: number;
  isLeaf: boolean;
  amount: number | null;
  children: SubjectTreeNode[];
}

/** 搜索命中项（对齐 U5 SubjectHit）。ancestorPath 为根→命中的面包屑。 */
export interface SubjectHit {
  id: number;
  name: string;
  code: string;
  level: number;
  isLeaf: boolean;
  ancestorPath: { id: number; name: string }[];
}

/** 比对结果（对齐 U5 CompareResult）。 */
export interface CompareResult {
  path: string[];
  status: 'EXISTS' | 'MISSING';
  subjectId: number | null;
  suggestedCode: string | null;
}

/** 预算科目树接口（U5）：查询登录可见；写（新增/确认新增/删除）限 editor。 */
export const subjectApi = {
  tree(budgetId?: number, lazy = false, parentId?: number, opts?: RequestOpts): Promise<SubjectTreeNode[]> {
    const q = new URLSearchParams();
    if (budgetId != null) q.set('budgetId', String(budgetId));
    if (lazy) q.set('lazy', 'true');
    if (parentId != null) q.set('parentId', String(parentId));
    const qs = q.toString();
    return apiClient.get<SubjectTreeNode[]>(`/api/subjects/tree${qs ? `?${qs}` : ''}`, opts);
  },
  search(keyword: string, limit?: number, opts?: RequestOpts): Promise<SubjectHit[]> {
    const q = new URLSearchParams({ keyword });
    if (limit != null) q.set('limit', String(limit));
    return apiClient.get<SubjectHit[]>(`/api/subjects/search?${q.toString()}`, opts);
  },
  addChild(parentId: number | null, name: string, code: string, opts?: RequestOpts): Promise<number> {
    return apiClient.post<number>('/api/subjects', { parentId, name, code }, opts);
  },
  compare(paths: string[][], opts?: RequestOpts): Promise<CompareResult[]> {
    return apiClient.post<CompareResult[]>('/api/subjects/compare', { paths }, opts);
  },
  confirmAdd(items: { path: string[]; codes?: string[] }[], opts?: RequestOpts): Promise<number[]> {
    return apiClient.post<number[]>('/api/subjects/confirm-add', { items }, opts);
  },
  remove(id: number, opts?: RequestOpts): Promise<void> {
    return apiClient.del<void>(`/api/subjects/${id}`, opts);
  },
};
