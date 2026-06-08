# 采购项目管理系统 · 数据库设计规格

> 阶段三·数据库设计产物 · 创建日期：2026-06-05 · 状态：草稿
> 上游：阶段三 `tkxm-general`（概要设计·E-R `docs/design/er/procurement-er.md`）、`tkxm-prototype`（`docs/prototype/index.html`）
> 下游：阶段三 `tkxm-detail`（详细设计）、阶段四 `tkxm-coding`（编码）
> 来源：E-R `docs/design/er/procurement-er.md`（6 限界上下文 BC1–BC6，23 实体）
> 配套：建表 DDL 独立成文 → `docs/design/db/procurement-ddl.md`（本文不内联 DDL）

---

## 1. 概述

- **数据库选型**：**PostgreSQL**（本机 `procurement` 库，与既有验证环境一致）。
- **字符集 / 排序**：UTF-8 / `en_US.UTF-8`（库级）。
- **命名规范**：表/字段 `snake_case`、表名**单数**；主键统一 `id`；外键 `〈表〉_id`；时间 `created_at`/`updated_at`/`deleted_at`。
- **通用约定**：
  - **主键**：`BIGINT GENERATED ALWAYS AS IDENTITY`，全表一致。
  - **金额**：`NUMERIC(18,2)`；**数量**：`NUMERIC(18,3)`（兼容“米/千克”等可拆分计量），均不用浮点。
  - **时间**：统一 `TIMESTAMPTZ`，默认 `now()`（UTC 存储）。
  - **`updated_at` 自动刷新**：PostgreSQL 无 `ON UPDATE`，用统一触发器 `set_updated_at()`（见 DDL 文件附录）。
  - **软删除**：仅**主数据表**（部门、项目组、用户、角色、预算科目、库存项）软删，**业务单据表**不软删、用 `status` 含“作废”。实现采用 `is_deleted SMALLINT(0/1)`（对齐脚手架 `application.yml` 的 MyBatis-Plus 全局逻辑删除 `logic-delete-field: isDeleted`），“未删唯一”用**部分唯一索引** `WHERE is_deleted = 0`。
  - **状态**：用 `VARCHAR(32)` 语义值 + `CHECK` 约束（稳定枚举，落代码常量；见 §5）。
  - **外键**：核心采用**物理外键**（PG 约束强、单库部署）；跨上下文大流水表（库存流水）用**逻辑外键**（应用层维护）以降并发耦合，见 §4。

---

## 2. 表清单总览

> 23 张表，对应 E-R 的 23 实体（BC1–BC6）。

| # | 表名 | 中文名 | 上下文/模块 | 说明 | 量级 |
|---|---|---|---|---|---|
| 1 | `department` | 部门 | BC1/M1 | 组织单元 | 百级 |
| 2 | `project_group` | 项目组（=项目） | BC1/M1 | 项目单元，归属部门 | 千级 |
| 3 | `sys_user` | 用户 | BC1/M1 | 系统用户（避保留字 user） | 千级 |
| 4 | `role` | 角色 | BC1/M1 | 权限角色 | 十级 |
| 5 | `user_role` | 用户角色关系 | BC1/M1 | 用户↔角色 多对多 | 万级 |
| 6 | `budget` | 预算 | BC2/M2 | 项目组的一次预算（聚合根） | 万级 |
| 7 | `budget_item` | 预算明细 | BC2/M2 | 叶子级科目预算金额 | 十万级 |
| 8 | `budget_subject` | 预算科目 | BC2/M2 | 多级科目树（自引用） | 万级 |
| 9 | `approval` | 审批单 | BC3/M3 | 通用审批（多业务类型，聚合根） | 万级 |
| 10 | `approval_record` | 审批记录 | BC3/M3 | 审批节点动作流水 | 十万级 |
| 11 | `purchase_order` | 采购单 | BC4/M4 | 采购执行主单（聚合根） | 万级 |
| 12 | `purchase_item` | 采购明细 | BC4/M4 | 采购物料行 | 十万级 |
| 13 | `delivery_note` | 到货单 | BC4/M4 | 到货凭证附件 | 十万级 |
| 14 | `inbound_order` | 入库单 | BC4/M4 | 验收入库主单（支持多次） | 十万级 |
| 15 | `inbound_item` | 入库明细 | BC4/M4 | 实收明细 | 十万级 |
| 16 | `stock_item` | 库存项 | BC5/M5 | 库存/资产 SoR（聚合根） | 十万级 |
| 17 | `stock_txn` | 库存流水 | BC5/M5 | 入库/出库/盘盈亏事件 | 百万级 |
| 18 | `stocktake` | 盘点单 | BC5/M5 | 盘点任务（聚合根） | 万级 |
| 19 | `stocktake_item` | 盘点明细 | BC5/M5 | 账实差异 | 十万级 |
| 20 | `requisition` | 领用单 | BC6/M6 | 领用申请（聚合根） | 万级 |
| 21 | `requisition_item` | 领用明细 | BC6/M6 | 领用物料行 | 十万级 |
| 22 | `outbound_order` | 出库单 | BC6/M6 | 仓管审批后出库 | 万级 |
| 23 | `outbound_item` | 出库明细 | BC6/M6 | 出库物料行 | 十万级 |

---

## 3. 表结构明细

> 每表省略通用字段说明：`id`(PK)、`created_at`/`updated_at`，主数据另含 `is_deleted`。仅列业务字段。可执行 DDL 见 `procurement-ddl.md`。

### 3.1 BC1 组织与权限

**`department` 部门**（主数据，软删）

| 字段 | 类型 | 允空 | 默认 | 主/外键 | 说明 |
|---|---|---|---|---|---|
| name | VARCHAR(128) | N | | | 部门名 |
| code | VARCHAR(64) | N | | | 部门编码（未删唯一） |

索引：`uk_department_code (code) WHERE is_deleted=0`。

**`project_group` 项目组（=项目）**（主数据，软删）

| 字段 | 类型 | 允空 | 默认 | 主/外键 | 说明 |
|---|---|---|---|---|---|
| name | VARCHAR(128) | N | | | 项目组名 |
| code | VARCHAR(64) | N | | | 编码（未删唯一） |
| department_id | BIGINT | N | | FK→department.id | 所属部门 |

索引：`uk_pg_code (code) WHERE is_deleted=0`、`idx_pg_dept (department_id)`。

**`sys_user` 用户**（主数据，软删）

| 字段 | 类型 | 允空 | 默认 | 主/外键 | 说明 |
|---|---|---|---|---|---|
| name | VARCHAR(64) | N | | | 姓名 |
| account | VARCHAR(64) | N | | | 登录账号（未删唯一） |
| password_hash | VARCHAR(128) | N | | | 口令哈希（BCrypt） |
| department_id | BIGINT | N | | FK→department.id | 所属部门 |

索引：`uk_user_account (account) WHERE is_deleted=0`、`idx_user_dept (department_id)`。

**`role` 角色**（主数据，软删）

| 字段 | 类型 | 允空 | 默认 | 说明 |
|---|---|---|---|---|
| name | VARCHAR(64) | N | | 角色名 |
| code | VARCHAR(64) | N | | 编码：editor/purchase_mgr/dept_mgr/warehouse/requester/admin（未删唯一） |

索引：`uk_role_code (code) WHERE is_deleted=0`。

**`user_role` 用户角色关系（多对多）**

| 字段 | 类型 | 允空 | 主/外键 | 说明 |
|---|---|---|---|---|
| user_id | BIGINT | N | FK→sys_user.id | |
| role_id | BIGINT | N | FK→role.id | |

索引：`uk_user_role (user_id, role_id)`、`idx_ur_role (role_id)`。

### 3.2 BC2 预算与科目

**`budget_subject` 预算科目（自引用树）**（主数据，软删）

| 字段 | 类型 | 允空 | 默认 | 主/外键 | 说明 |
|---|---|---|---|---|---|
| parent_id | BIGINT | Y | NULL | FK→budget_subject.id | 父科目，根为 NULL |
| name | VARCHAR(128) | N | | | 科目名 |
| code | VARCHAR(64) | N | | | 科目编码（未删唯一） |
| level | SMALLINT | N | 1 | | 层级，默认≤5、可向下扩展 |
| is_leaf | BOOLEAN | N | true | | 是否叶子（仅叶子可挂金额） |

索引：`uk_subject_code (code) WHERE is_deleted=0`、`idx_subject_parent (parent_id)`、`idx_subject_name_trgm`（`pg_trgm` GIN，模糊搜索）。

**`budget` 预算（聚合根）**

| 字段 | 类型 | 允空 | 默认 | 主/外键 | 说明 |
|---|---|---|---|---|---|
| project_group_id | BIGINT | N | | FK→project_group.id | 所属项目组 |
| name | VARCHAR(128) | N | | | 预算名称 |
| source_doc_path | VARCHAR(512) | Y | NULL | | 立项文档附件路径（留档） |
| version | INT | N | 1 | | 版本（预留变更） |
| status | VARCHAR(32) | N | 'draft' | | draft/submitted/approved/rejected |

索引：`idx_budget_pg (project_group_id)`、`idx_budget_status (status)`。

**`budget_item` 预算明细**

| 字段 | 类型 | 允空 | 主/外键 | 说明 |
|---|---|---|---|---|
| budget_id | BIGINT | N | FK→budget.id | |
| subject_id | BIGINT | N | FK→budget_subject.id | 叶子级科目 |
| amount | NUMERIC(18,2) | N | | 预算金额 |

索引：`uk_bi (budget_id, subject_id)`、`idx_bi_budget (budget_id)`、`idx_bi_subject (subject_id)`。

### 3.3 BC3 审批

**`approval` 审批单（通用，聚合根；Flowable 流程的业务投影/链接）**

> 审批由 Flowable BPMN 引擎驱动（流程定义 `budget_approval`）；`approval` 为业务侧链接表，冗余 `status`/`current_node` 便于查询；权威流转状态在引擎表 `ACT_RU_*`/`ACT_HI_*`。

| 字段 | 类型 | 允空 | 默认 | 说明 |
|---|---|---|---|---|
| biz_type | VARCHAR(32) | N | | 业务类型：budget（可扩展） |
| biz_id | BIGINT | N | | 关联业务单据 id（逻辑外键） |
| flow_code | VARCHAR(32) | Y | NULL | 流程定义 key（预留可配置审批流） |
| process_instance_id | VARCHAR(64) | Y | NULL | Flowable 流程实例 id |
| current_node | VARCHAR(32) | N | | 当前节点：purchase_mgr/dept_mgr |
| status | VARCHAR(32) | N | 'draft' | draft/pending_purchase_mgr/pending_dept_mgr/approved/rejected |

索引：`idx_approval_biz (biz_type, biz_id)`、`idx_approval_status (status)`、`idx_approval_pi (process_instance_id)`。

**`approval_record` 审批记录（业务投影，由 Flowable 任务完成事件写入）**

| 字段 | 类型 | 允空 | 主/外键 | 说明 |
|---|---|---|---|---|
| approval_id | BIGINT | N | FK→approval.id | |
| node_seq | SMALLINT | N | | 节点顺序（预留可配置节点数） |
| node | VARCHAR(32) | N | | purchase_mgr/dept_mgr |
| task_id | VARCHAR(64) | Y | | Flowable 任务 id |
| approver_id | BIGINT | N | FK→sys_user.id | 审批人 |
| action | VARCHAR(16) | N | | approve/reject |
| opinion | VARCHAR(512) | Y | | 意见（reject 必填，应用层校验） |
| acted_at | TIMESTAMPTZ | Y | | 处理时间 |

索引：`idx_ar_approval (approval_id)`。

### 3.4 BC4 采购与入库

**`purchase_order` 采购单（聚合根）**

| 字段 | 类型 | 允空 | 默认 | 主/外键 | 说明 |
|---|---|---|---|---|---|
| budget_id | BIGINT | N | | FK→budget.id | 来源已通过预算 |
| project_group_id | BIGINT | N | | FK→project_group.id | 归属 |
| supplier_name | VARCHAR(128) | Y | NULL | | 供应商（可选） |
| contract_no | VARCHAR(64) | Y | NULL | | 合同号（可选） |
| status | VARCHAR(32) | N | 'executing' | | executing/inbounded/void |

索引：`idx_po_budget (budget_id)`、`idx_po_pg (project_group_id)`、`idx_po_status (status)`。

**`purchase_item` 采购明细**

| 字段 | 类型 | 允空 | 默认 | 主/外键 | 说明 |
|---|---|---|---|---|---|
| purchase_order_id | BIGINT | N | | FK→purchase_order.id | |
| subject_id | BIGINT | N | | FK→budget_subject.id | 预算科目 |
| material_name | VARCHAR(128) | N | | | 物料名 |
| qty | NUMERIC(18,3) | N | | | 采购数量 |
| received_qty | NUMERIC(18,3) | N | 0 | | 累计已入库（CHECK ≤ qty，不超收） |
| amount | NUMERIC(18,2) | N | | | 金额 |

索引：`idx_pi_po (purchase_order_id)`、`idx_pi_subject (subject_id)`。约束：`CHECK (received_qty >= 0 AND received_qty <= qty)`。

**`delivery_note` 到货单**

| 字段 | 类型 | 允空 | 主/外键 | 说明 |
|---|---|---|---|---|
| purchase_order_id | BIGINT | N | FK→purchase_order.id | |
| file_path | VARCHAR(512) | N | | 附件路径 |
| uploaded_by | BIGINT | N | FK→sys_user.id | 上传人 |

索引：`idx_dn_po (purchase_order_id)`。

**`inbound_order` 入库单（一采购单可多张=多次到货）**

| 字段 | 类型 | 允空 | 主/外键 | 说明 |
|---|---|---|---|---|
| purchase_order_id | BIGINT | N | FK→purchase_order.id | |
| received_by | BIGINT | N | FK→sys_user.id | 验收人（仓管） |
| inbound_at | TIMESTAMPTZ | N | | 入库时间 |

索引：`idx_io_po (purchase_order_id)`。

**`inbound_item` 入库明细**

| 字段 | 类型 | 允空 | 主/外键 | 说明 |
|---|---|---|---|---|
| inbound_order_id | BIGINT | N | FK→inbound_order.id | |
| purchase_item_id | BIGINT | N | FK→purchase_item.id | 核对累计已收 |
| stock_item_id | BIGINT | N | FK→stock_item.id | 写入的库存项 |
| received_qty | NUMERIC(18,3) | N | | 本次实收 |
| project_group_id | BIGINT | N | FK→project_group.id | 归属 |

索引：`idx_ii_inbound (inbound_order_id)`、`idx_ii_pi (purchase_item_id)`、`idx_ii_stock (stock_item_id)`。

### 3.5 BC5 库存与资产

**`stock_item` 库存项（SoR，聚合根）**（主数据，软删）

| 字段 | 类型 | 允空 | 默认 | 主/外键 | 说明 |
|---|---|---|---|---|---|
| material_name | VARCHAR(128) | N | | | 物料/资产名 |
| project_group_id | BIGINT | N | | FK→project_group.id | 归属项目组 |
| department_id | BIGINT | N | | FK→department.id | 归属部门（冗余统计） |
| quantity | NUMERIC(18,3) | N | 0 | | 当前库存（=流水累计，CHECK≥0） |
| asset_no | VARCHAR(64) | Y | NULL | | 资产编号（预留折旧） |
| original_value | NUMERIC(18,2) | Y | NULL | | 原值（预留折旧） |
| life_status | VARCHAR(32) | Y | NULL | | 生命周期状态（预留折旧） |

索引：`uk_stock (material_name, project_group_id) WHERE is_deleted=0`（聚合粒度 TBD-1）、`idx_stock_pg (project_group_id)`。约束：`CHECK (quantity >= 0)`。

**`stock_txn` 库存流水（事件）**

| 字段 | 类型 | 允空 | 主/外键 | 说明 |
|---|---|---|---|---|
| stock_item_id | BIGINT | N | （逻辑 FK）→stock_item.id | |
| type | VARCHAR(16) | N | | inbound/outbound/gain/loss |
| qty_change | NUMERIC(18,3) | N | | 数量增减（带正负） |
| ref_type | VARCHAR(32) | N | | inbound_order/outbound_order/stocktake |
| ref_id | BIGINT | N | | 来源单 id（多态逻辑引用） |
| created_at | TIMESTAMPTZ | N | | 发生时间 |

索引：`idx_txn_stock (stock_item_id, created_at)`、`idx_txn_ref (ref_type, ref_id)`。

**`stocktake` 盘点单（聚合根）**

| 字段 | 类型 | 允空 | 默认 | 主/外键 | 说明 |
|---|---|---|---|---|---|
| scope_project_group_id | BIGINT | N | | FK→project_group.id | 盘点范围 |
| status | VARCHAR(32) | N | 'counting' | | counting/confirmed |

索引：`idx_st_pg (scope_project_group_id)`、`idx_st_status (status)`。

**`stocktake_item` 盘点明细**

| 字段 | 类型 | 允空 | 主/外键 | 说明 |
|---|---|---|---|---|
| stocktake_id | BIGINT | N | FK→stocktake.id | |
| stock_item_id | BIGINT | N | FK→stock_item.id | |
| book_qty | NUMERIC(18,3) | N | | 账面数 |
| actual_qty | NUMERIC(18,3) | N | | 实盘数 |
| diff | NUMERIC(18,3) | N | | 差异=实盘-账面 |
| diff_type | VARCHAR(16) | N | | gain/loss/none |

索引：`idx_sti_stocktake (stocktake_id)`、`idx_sti_stock (stock_item_id)`。

### 3.6 BC6 领用与出库

**`requisition` 领用单（聚合根）**

| 字段 | 类型 | 允空 | 默认 | 主/外键 | 说明 |
|---|---|---|---|---|---|
| project_group_id | BIGINT | N | | FK→project_group.id | |
| applicant_id | BIGINT | N | | FK→sys_user.id | 领用人 |
| purpose | VARCHAR(512) | Y | NULL | | 用途 |
| status | VARCHAR(32) | N | 'pending_warehouse' | | pending_warehouse/outbound/rejected |

索引：`idx_req_pg (project_group_id)`、`idx_req_applicant (applicant_id)`、`idx_req_status (status)`。

**`requisition_item` 领用明细**

| 字段 | 类型 | 允空 | 主/外键 | 说明 |
|---|---|---|---|---|
| requisition_id | BIGINT | N | FK→requisition.id | |
| stock_item_id | BIGINT | N | FK→stock_item.id | |
| qty | NUMERIC(18,3) | N | | 申请数量 |

索引：`idx_ri_req (requisition_id)`、`idx_ri_stock (stock_item_id)`。

**`outbound_order` 出库单（领用审批通过生成）**

| 字段 | 类型 | 允空 | 主/外键 | 说明 |
|---|---|---|---|---|
| requisition_id | BIGINT | N | FK→requisition.id | 唯一（一领用单一出库单） |
| approver_id | BIGINT | N | FK→sys_user.id | 审批出库的仓管 |
| outbound_at | TIMESTAMPTZ | N | | 出库时间 |

索引：`uk_ob_req (requisition_id)`。

**`outbound_item` 出库明细**

| 字段 | 类型 | 允空 | 主/外键 | 说明 |
|---|---|---|---|---|
| outbound_order_id | BIGINT | N | FK→outbound_order.id | |
| stock_item_id | BIGINT | N | FK→stock_item.id | |
| qty | NUMERIC(18,3) | N | | 出库数量 |

索引：`idx_oi_ob (outbound_order_id)`、`idx_oi_stock (stock_item_id)`。

---

## 4. 关系与外键策略

- **物理外键**：除库存流水外的所有 FK 采用 PostgreSQL 物理外键约束（单库部署，约束强、数据可靠）。
- **逻辑外键**：`stock_txn.stock_item_id`、`approval.biz_id` 用逻辑外键（应用层维护）——前者为高写入流水表降耦合，后者为多业务类型（biz_id 指向不同表）无法建物理 FK。
- **删除级联**：主-明细（`budget`→`budget_item`、`purchase_order`→`purchase_item` 等）用 `ON DELETE RESTRICT`（单据不物理删，走状态作废）；主数据用软删除不触发级联。
- **自引用**：`budget_subject.parent_id` → 自身，`ON DELETE RESTRICT`（有子级不可删）。

---

## 5. 枚举与字典

| 字段 | 取值 | 含义 |
|---|---|---|
| budget.status | draft/submitted/approved/rejected | 草稿/已提交/已通过/已驳回 |
| approval.status | draft/pending_purchase_mgr/pending_dept_mgr/approved/rejected | 编制中/采购主管待审/部门主管待审/已通过/已驳回 |
| approval.current_node / approval_record.node | purchase_mgr/dept_mgr | 采购主管/部门主管 |
| approval_record.action | approve/reject | 通过/驳回 |
| purchase_order.status | executing/inbounded/void | 执行中/已入库/作废 |
| stock_txn.type | inbound/outbound/gain/loss | 入库/出库/盘盈/盘亏 |
| stocktake.status / stocktake_item.diff_type | counting,confirmed / gain,loss,none | 进行中,已确认 / 盘盈,盘亏,无差异 |
| requisition.status | pending_warehouse/outbound/rejected | 待仓管审批/已出库/已驳回 |
| role.code | editor/purchase_mgr/dept_mgr/warehouse/requester/admin | 编制/采购主管/部门主管/仓管/领用人/管理员 |

> 均为稳定枚举，入代码常量并以 `CHECK` 约束护栏；不设字典表。

---

## 6. 索引与性能策略

| 查询场景 | 涉及表/字段 | 索引/对策 |
|---|---|---|
| 审批待办列表（按节点/状态） | approval(status) | idx_approval_status |
| 某预算的审批流转历史 | approval_record(approval_id) | idx_ar_approval |
| 科目树展开/搜索 | budget_subject(parent_id)、name/code 模糊 | idx_subject_parent；名称模糊用 `pg_trgm` GIN |
| 库存项当前量（按项目组） | stock_item(project_group_id) | idx_stock_pg |
| 库存流水回溯 | stock_txn(stock_item_id, created_at) | idx_txn_stock |
| 采购/领用按状态分页 | *_status | 各 status 索引 |

- **科目模糊搜索**（原型 B2/B3）：`CREATE EXTENSION pg_trgm` + `budget_subject.name` GIN 索引支持 `ILIKE '%kw%'`。
- 避免低区分度列单独索引；状态+时间的列表排序可按需升级为联合索引。

---

## 7. 数据量与扩展性

| 表 | 初始量级 | 年增长 | 扩展策略 |
|---|---|---|---|
| stock_txn | 十万 | 百万/年 | 暂单表；超千万按年做范围分区（PG 声明式分区） |
| approval_record / *_item | 十万 | 数十万/年 | 单表足够 |

- **预留扩展点**（对应 E-R §7）：
  - 资产折旧/消耗：`stock_item` 已留 `asset_no/original_value/life_status`；后续加 `depreciation_record`/`consumption_log` 挂库存项。
  - 预算变更/台账：`budget.version` 已留；后续加 `budget_change` 表；“预算 vs 实际”用视图/读模型（聚合 `purchase_item.amount` 按 `subject_id`）。
  - 可配置审批流：后续加 `approval_flow_def`/`approval_node_def`，由 `approval.flow_code` 引用。

---

## 8. 初始化与迁移注意事项

- **初始化数据**：6 个内置 `role`；默认管理员 `sys_user`；可选基础 `budget_subject` 根节点（见 `procurement-ddl.md` 附 seed）。
- **Flowable 引擎**：在同 `procurement` 库自动初始化 `ACT_*` 引擎表（`spring.flowable` 配置）；本期部署 BPMN 流程定义 `budget_approval`（采购主管 → 部门主管，驳回回退到起点）。业务 migration 与 Flowable 建表的执行顺序在编码实施阶段（`tkxm-coding`）确认。
- **迁移安全**：加列带默认值；大表（stock_txn）加索引用 `CREATE INDEX CONCURRENTLY`；分区改造灰度。
- 表结构变更须回溯更新 E-R（`docs/design/er/procurement-er.md`）与本文档，并提示 `tkxm-detail`/`tkxm-coding` 回看。

---

## 9. 待确认 (TBD)

| 编号 | 待确认项 | 现状/暂定 | 拍板人 |
|---|---|---|---|
| TBD-1 | 库存项聚合粒度（继承 E-R TBD-1） | 暂定 `uk_stock(material_name, project_group_id)`，不按批次/序列号 | 业务 |
| TBD-2 | 时间存储时区与前端展示 | 暂定 UTC 存储、展示层转本地 | 技术 |
| TBD-3 | 附件（立项文档/到货单）存储介质 | 暂定存路径，文件落本地/对象存储 | 技术 |

---

> 可执行建表 DDL（PostgreSQL）见同目录 `procurement-ddl.md`，本文不内联，避免两处重复。
