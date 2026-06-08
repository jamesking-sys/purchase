# 采购与资产管理系统

政府/事业单位采购与资产全生命周期管理：**预算 → 科目对照 → 两级审批 → 采购执行/验收入库 → 领用/审批出库 → 盘点/资产归属**。

- 需求文档：[`docs/requirement/procurement-requirement.md`](docs/requirement/procurement-requirement.md)
- 产品需求（PRD）：[`docs/prd/procurement-prd.md`](docs/prd/procurement-prd.md)
- 数据库设计（PostgreSQL）：[`docs/design/db/procurement-db.md`](docs/design/db/procurement-db.md)
- 概要设计（含技术选型）：[`docs/design/general/procurement-general.md`](docs/design/general/procurement-general.md)
- 实现计划（U-ID/波次/关键路径）：[`docs/plan/procurement-plan.md`](docs/plan/procurement-plan.md)

## 技术栈

| 层 | 选型 |
|----|------|
| 后端 | Java 17 · Spring Boot 3.4 · MyBatis-Plus · Flyway · Sa-Token · Flowable · FastExcel |
| 前端 | React 18 · TypeScript 5 · Vite 6 · Semi Design |
| 数据库 | PostgreSQL 16 |

## 本地运行

### 前置

- JDK 17、Maven 3.6.3+
- Node 18+、npm
- Docker（起本地 PostgreSQL）

### 1. 启动数据库

```bash
docker compose up -d
```

### 2. 启动后端（端口 8080）

```bash
cd backend
mvn spring-boot:run
```

- 健康检查：http://localhost:8080/api/health
- 接口文档：http://localhost:8080/swagger-ui.html

数据源默认连 `localhost:5432/procurement`，可用环境变量 `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` 覆盖。

#### 停止后端与释放端口 8080

正常停止会触发**优雅停机**后释放端口（`application.yml`：`server.shutdown: graceful` + `spring.lifecycle.timeout-per-shutdown-phase: 10s`，避免在途/keep-alive 连接长时间占住端口）。可用环境变量调整：

- `SERVER_PORT`（默认 8080）
- `SERVER_SHUTDOWN=immediate`（停止时立即释放端口，不等 keep-alive 连接，开发更顺手）
- `SHUTDOWN_TIMEOUT`（优雅停机最长等待，默认 `10s`）

> **Windows 上 8080 未被释放（端口被占用）**：`mvn spring-boot:run` 是 `mvn.cmd → java` 多级进程，终端/任务被杀时子 JVM 可能成为**孤儿进程**继续占住 8080，且因未收到关闭信号，优雅停机也不会触发。两种应对：
>
> - **推荐**：在 IDEA 用 **Spring Boot 运行配置**（直接 `java` 启动；IDEA 的停止会直接结束该 JVM 并触发优雅停机释放端口），而非 Maven `spring-boot:run` 运行配置。
> - **兜底**：用脚本强制结束占用端口的进程并释放端口：
>
>   ```powershell
>   # Windows PowerShell
>   powershell -ExecutionPolicy Bypass -File backend\scripts\free-port.ps1 -Port 8080
>   ```
>
>   ```bash
>   # git-bash / Unix
>   bash backend/scripts/free-port.sh 8080
>   ```

### 3. 启动前端（端口 5173）

```bash
cd frontend
npm install
npm run dev
```

打开 http://localhost:5173，首页会调用 `/api/health`（经 Vite 代理到后端）显示服务与数据库连通状态。

## 测试

```bash
# 后端：JUnit5 + MockMvc + Testcontainers（需 Docker 运行）
cd backend && mvn test

# 前端：Vitest
cd frontend && npm test
```

## 工程布局

```text
backend/    Spring Boot 单工程（controller/service/mapper 分层，模块化 modules/*）
frontend/   React + Vite + Semi Design
docs/       requirement / prd / prototype / plan / design 各阶段文档与原型
docker-compose.yml   仅 PostgreSQL
```
