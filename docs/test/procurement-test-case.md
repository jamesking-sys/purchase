# 采购与资产管理系统 · 功能测试用例（索引）

> 阶段四·测试用例产物（按模块拆分）· 创建 2026-06-11 · 更新 2026-06-11 · 状态：草稿
> 上游：`tkxm-prototype`（原型 11 屏）、`tkxm-detail`（详设 §6/§7 测试点 T-x、AC-x）、`tkxm-prd`
> 编号规则见 skill `test-spec.md`：`TC-〈模块〉-〈三位序号〉`，优先级 P0~P3。被测版本：分支 `dev`。
> **本文为索引**：用例明细按模块拆分到 `cases/`，便于多人分配执行与维护。结果列：⬜ 未执行 / ✅ 通过 / ❌ 失败 / ⚠ 阻塞 / ⏭ 跳过。

---

## 用例总览（点模块名进各分册）

| 模块（缩写） | 分册 | 屏/功能点 | 用例数 | P0 | P1 | P2 | P3 | 覆盖功能点 |
|---|---|---|---|---|---|---|---|---|
| AUTH 认证与可见性 | [cases/auth.md](cases/auth.md) | 登录 / 角色菜单 | 6 | 2 | 3 | 1 | 0 | U2,U3 |
| ORG 组织与权限 | [cases/org.md](cases/org.md) | `org` | 9 | 1 | 5 | 3 | 0 | U4 |
| SUBJ 科目树与比对 | [cases/subj.md](cases/subj.md) | `subject` `compare` | 8 | 1 | 4 | 3 | 0 | U5 |
| IMP 预算导入 | [cases/imp.md](cases/imp.md) | `import` | 7 | 1 | 4 | 2 | 0 | U6 |
| APPR 两级审批 | [cases/appr.md](cases/appr.md) | `approval` | 9 | 2 | 5 | 2 | 0 | U7 |
| PUR 采购执行 | [cases/pur.md](cases/pur.md) | `purchase` | 8 | 1 | 4 | 3 | 0 | U8 |
| INB 验收入库 | [cases/inb.md](cases/inb.md) | `inbound` | 8 | 1 | 4 | 3 | 0 | U9 |
| STKQ 库存查询 | [cases/stkq.md](cases/stkq.md) | `inbound`/`outbound` 内嵌 | 6 | 1 | 3 | 2 | 0 | U10 |
| REQ 领用申请 | [cases/req.md](cases/req.md) | `requisition` | 6 | 1 | 3 | 2 | 0 | U11 |
| OUT 仓管审批出库 | [cases/out.md](cases/out.md) | `outbound` | 8 | 2 | 4 | 2 | 0 | U11 |
| STKT 盘点 | [cases/stkt.md](cases/stkt.md) | `stocktake` | 9 | 1 | 5 | 3 | 0 | U12 |
| VSA 预算 vs 实际 | [cases/vsa.md](cases/vsa.md) | `subject` 内卡 | 6 | 1 | 3 | 2 | 0 | U13 |
| DASH 工作台 | [cases/dash.md](cases/dash.md) | `dashboard` | 4 | 1 | 2 | 1 | 0 | U14 |
| GLB 全局异常 | [cases/glb.md](cases/glb.md) | 横切 | 5 | 1 | 3 | 1 | 0 | 全局 |
| **合计** | — | | **99** | **17** | **52** | **30** | **0** | U2–U14 |

> 冒烟用例（端到端主线 13 条）见 [`procurement-smoke-test.md`](procurement-smoke-test.md)；测试方案见 [`procurement-test-plan.md`](procurement-test-plan.md)。

---

## 执行进度（每轮回填）

> 各分册 `结果` 列回填后，在此汇总本轮通过率（执行率 = 已执行/99；通过率 = 通过/已执行）。

| 轮次 | 版本 | 执行率 | 通过率 | P0/P1 遗留缺陷 | 结论 |
|---|---|---|---|---|---|
| 第 1 轮 | dev@〈构建号〉 | —% | —% | — | 〈待执行〉 |

---

## 用例设计说明（覆盖度自检）

> 用例从原型 11 屏交互 + 各详设 §6/§7 测试点（T-x、AC-x）双源推导，按维度自检：

- [x] **正常流**：每功能点至少一条 happy path（P0），覆盖关键路径 U2→U5→U6→U7→U8→U9→U11 与 U12/U13。
- [x] **边界值**：金额 0.00/0.001、分页越界、实盘=账面、空待办、清空输入。
- [x] **异常流**：非法输入、依赖失效（预算非 approved）、不存在对象（40401）、累计超收（42204）、库存不足（40904）。
- [x] **权限**：各 `@SaCheckRole` 角色可见/可操作差异，越权被拒（40301）；节点-角色比对（审批）。
- [x] **状态机**：审批两级流转/驳回退回；采购 executing→inbounded；领用 pending→outbound/rejected；盘点 counting→confirmed（终态幂等）。
- [x] **并发**：出库防超发、入库不超收、盘点重复确认/录入幂等（行锁串行化）。
- [x] **数据校验**：必填、编码/账号唯一（40902）、驳回意见必填（42203）、实盘非负（42205）、金额挂叶子（42202）。
- [x] **数据一致性**：库存==流水累计（INV-1/3）、聚合键合并、预算vs实际仅同预算、口令不回显。
- [x] **UI/交互**：逐行红条、不足行禁用、超收行内报错、空态/加载态、文件重选触发、统计卡容错「—」。
- [ ] **兼容性/性能**：按需，列表 P99/百万级流水分页抽样（环境具备时补）。

---

## 与开发单测的分工

- **本用例（黑盒/验收）**：用户视角走界面与接口，覆盖业务场景、错误码呈现与交互。
- **开发集成/单测（白盒）**：后端 14 个 `*IntegrationTest`（真实 PostgreSQL，覆盖事务/行锁/并发/对账等内部约束）、前端 18 个 Vitest 文件（组件/纯函数）；均映射详设 §6 测试点，视角互补不重复。
- 失败用例须关联缺陷编号并跟踪闭环；需求/详设变更同步维护受影响用例。
