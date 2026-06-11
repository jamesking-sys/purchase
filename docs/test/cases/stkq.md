# STKQ · 库存查询与流水 · 测试用例（TC-STKQ）

> 阶段四·测试用例（按模块拆分）· 创建 2026-06-11 · 状态：草稿
> 上游：原型 `inbound`/`outbound` 内嵌库存面板、详设 `U10-stock-query.md` §6 · 覆盖功能点 **U10**（限 warehouse|admin）
> 总览与编号/优先级规范见 `../procurement-test-case.md`。

| 用例数 | P0 | P1 | P2 | P3 |
|---|---|---|---|---|
| 6 | 1 | 3 | 2 | 0 |

## 用例明细

| 用例编号 | 标题 | 优先级 | 类型 | 前置条件 | 测试步骤 | 测试数据 | 预期结果 | 关联 | 结果 |
|---|---|---|---|---|---|---|---|---|---|
| TC-STKQ-001 | 库存分页查询 | P0 | 正常 | 存在库存项 | 调 `GET /api/stocks` 分页 | page/size | 返回当前页 records+total，按项目组+物料名升序 | U10 | ⬜ |
| TC-STKQ-002 | 项目组/部门/物料名过滤 | P1 | 正常 | 多项目组库存 | 传 projectGroupId+materialName 过滤 | 组合条件 | 返回交集，物料名 ILIKE 大小写不敏感 | U10 | ⬜ |
| TC-STKQ-003 | 流水倒序+对账汇总 | P1 | 数据一致 | 库存项有流水 | 查 `/stocks/{id}/txns` | — | 按 created_at 倒序；bookQty==txnSum==quantity（INV-1） | U10 | ⬜ |
| TC-STKQ-004 | 库存项不存在 | P1 | 异常 | — | 查不存在库存项流水 | id=9e9 | 返回 40401 | U10 | ⬜ |
| TC-STKQ-005 | 分页参数越界 | P2 | 边界 | — | page=0 / size=0 / size=1000 | 越界值 | 返回 40001 | U10 | ⬜ |
| TC-STKQ-006 | 非 warehouse/admin 被拒 | P2 | 权限 | editor 调用 | `GET /api/stocks` | — | 40301 | U10 | ⬜ |

> 结果列：⬜ 未执行 / ✅ 通过 / ❌ 失败 / ⚠ 阻塞 / ⏭ 跳过。失败关联缺陷编号。
