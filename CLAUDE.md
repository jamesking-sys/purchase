# CLAUDE.md

采购与资产管理系统：**预算 → 科目对照 → 两级审批 → 采购执行/验收入库 → 领用/审批出库 → 盘点**。政府/事业单位内部 Web 系统，多项目、轻量 RBAC、全流程可追溯。

## 技术栈

| 层 | 选型 |
|---|---|
| 后端 | Java 17 · Spring Boot 3.4.1 · MyBatis-Plus 3.5.9 · Flyway · Sa-Token 1.40 · Flowable 7.1 · FastExcel 1.1 · springdoc-openapi 2.7 |
| 前端 | React 18 · TypeScript 5 · Vite 6 · Semi Design（`@douyinfe/semi-ui`）· Vitest |
| 数据库 | PostgreSQL（库名 `procurement`） |

## 工程布局

```text
backend/    Spring Boot 单工程，包根 com.gov.procurement；分层 common/config/modules/*
frontend/   React + Vite + Semi UI
docs/       研发 SOP 四阶段产物（见下「文档地图」）
docker-compose.yml   仅 PostgreSQL
```

## 研发 SOP 与文档地图

本项目按 `tkxm-sop`（用户级 skill）的四阶段流程推进。各阶段产物落盘：

| 阶段 | 产物 | 路径 |
|---|---|---|
| 需求 `tkxm-requirement` | 需求文档（+ 原始来源） | `docs/requirement/procurement-requirement.md` |
| 产品 `tkxm-prd` | PRD | `docs/prd/procurement-prd.md` |
| 产品 `tkxm-prototype` | 原型图 | `docs/prototype/index.html` |
| 产品 `tkxm-plan` | 计划看板（U-ID/波次/关键路径） | `docs/plan/procurement-plan.md` |
| 设计 `tkxm-general` | 概要设计 + E-R（DDD） | `docs/design/general/`、`docs/design/er/` |
| 设计 `tkxm-database` | 数据库设计 | `docs/design/db/procurement-db.md` |
| 设计 `tkxm-detail` | 详细设计（按 U-ID） | `docs/design/detail/U*.md` |
| 实施 `tkxm-coding`/`review`/`test` | 代码 + 单测 + 审查 + 测试 | `backend/`、`frontend/`、`docs/test/` |

> 命名约定：阶段目录用单数，文件 `procurement-<阶段>.md`；原型为 `index.html`；详细设计按功能点 `U<编号>-<名>.md`。
> 功能点 U-ID（U0–U14）定义见计划看板；关键路径 `U0→U1→U2→U5→U6→U7→U8→U9→U11`。

## 本地运行

```bash
docker compose up -d                      # 1) PostgreSQL
cd backend && mvn spring-boot:run         # 2) 后端 :8080（/api/health、/swagger-ui.html）
cd frontend && npm install && npm run dev # 3) 前端 :5173（Vite 代理 /api → 后端）
```

数据源默认 `localhost:5432/procurement`，可用 `DB_URL`/`DB_USERNAME`/`DB_PASSWORD` 覆盖。

## 测试

```bash
cd backend && mvn test     # JUnit5 + MockMvc + Testcontainers（需 Docker）
cd frontend && npm test    # Vitest
```

## 代码约定

- **统一响应**：`com.gov.procurement.common.Result<T>`（`code=0` 成功，非 0 为错误码）。
- **业务异常**：抛 `BizException(code, message)`，由 `GlobalExceptionHandler` 统一转 `Result`。
- **错误码**：详设各功能点定义，沿用 `40001`(参数)/`40301`(无权限)/`404xx`(不存在)/`409xx`(状态冲突)/`50000`(系统) 形态。
- **数据库**：`snake_case`、表名单数、主键 `id`（`BIGINT IDENTITY`）；逻辑删除 `is_deleted`（MyBatis-Plus 全局）；金额 `NUMERIC(18,2)`、数量 `NUMERIC(18,3)`；迁移走 Flyway（`backend/src/main/resources/db/migration/`）。
- **审批**：Flowable BPMN（流程定义 `budget_approval`，采购主管 → 部门主管），`approval`/`approval_record` 为业务投影。
- **库存**：唯一 SoR=`stock_item`，所有增减经 `stock_txn` 流水；出库 `SELECT ... FOR UPDATE` + `CHECK(quantity>=0)` 防超发。
