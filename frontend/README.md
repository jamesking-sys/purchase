# 采购与资产管理系统 · 前端

React 18 + TypeScript 5 + Vite 6 + Semi Design（`@douyinfe/semi-ui`）+ React Router v6 + Vitest。
本目录是 **U3 前端外壳**：统一布局、侧边导航、路由、登录页与守卫、API 客户端；各业务页（U14）在此外壳的路由槽位内填充。

---

## 目录结构

```text
frontend/
├── index.html              # Vite 入口 HTML，挂载 #root
├── package.json            # 依赖与脚本（dev / build / test / type-check）
├── vite.config.ts          # Vite：/api 代理 → 后端 :8080，dev 端口 5173
├── vitest.config.ts        # Vitest：合并 vite 配置 + test 段（jsdom + setup）
├── tsconfig.json           # TS 解决方案文件（引用 app / node 子配置）
├── tsconfig.app.json       #   └ 应用源码（src/，strict）
├── tsconfig.node.json      #   └ 构建脚本（vite.config.ts）
│
└── src/
    ├── main.tsx            # 渲染入口：createRoot + StrictMode + <App/>，引入 Semi 基样式
    ├── App.tsx             # 应用根：<BrowserRouter> + <AppRoutes>
    ├── router.tsx          # 路由表：/login 公共；其余受 AuthGuard 守卫，挂 AppLayout 内（由 navConfig 驱动生成）
    ├── vite-env.d.ts       # Vite 环境类型声明
    │
    ├── api/                # ── 统一 API 层 ──
    │   ├── types.ts        # Result<T> / MeResp / LoginResp（对齐后端 com.gov.procurement.common.Result）
    │   ├── client.ts       # apiClient(get/post/put/del)：注入 Bearer token、解包 Result、401 反应式登出、错误 toast、ApiError
    │   └── client.test.ts  # 单测：解包 / 业务错误+toast / silent / 401 清态 / Bearer 头
    │
    ├── auth/               # ── 认证与会话 ──
    │   ├── tokenStore.ts   # token 存取（localStorage 键 'pmp_token'）
    │   ├── authStore.ts    # 当前用户内存态（发布订阅）+ hasRole（admin 默认全角色）
    │   ├── useAuth.ts      # useSyncExternalStore 订阅会话态的 Hook（me / hasRole）
    │   ├── AuthGuard.tsx    # 路由守卫：无 token → /login（记录 from）；有 token 缺 me → 拉 /api/auth/me
    │   ├── AuthGuard.test.tsx
    │   ├── LoginPage.tsx    # 登录表单 → POST /api/auth/login 存 token → GET /api/auth/me 填会话 → 回跳原目标
    │   └── useLogout.ts     # 登出：POST /api/auth/logout（忽略失败）+ 清 token/会话 + 跳 /login
    │
    ├── layout/             # ── 应用外壳与导航 ──
    │   ├── navConfig.ts     # 4 导航域(A/B/D/F) + 11 屏配置（path/screenId/roles）+ isNavVisible 角色可见性
    │   ├── navConfig.test.ts
    │   └── AppLayout.tsx    # Semi Layout：左侧角色过滤导航 + 顶部(用户名/登出) + <Outlet> 内容区
    │
    ├── pages/              # ── 业务页占位（待 U14 填充）──
    │   ├── Placeholder.tsx  # 通用占位页（标题 + “待 U14 实现”提示）
    │   └── NotFoundPage.tsx # 404
    │
    └── test/
        └── setup.ts        # Vitest 全局 setup：@testing-library/jest-dom + canvas 桩（Semi→lottie 在 jsdom 的兼容）
```

> 测试文件就近放置（`*.test.ts(x)` 与被测文件同目录）。`node_modules/`、`dist/` 为产物，已被 `.gitignore` 忽略。

---

## 脚本

```bash
npm install        # 安装依赖
npm run dev        # 启动 dev server（:5173，/api 代理到后端 :8080）
npm run build      # 类型检查(tsc -b) + 生产构建 → dist/
npm test           # Vitest 一次性运行
npm run test:watch # Vitest 监听模式
npm run type-check # tsc -b 严格类型检查（等同 build 的检查段）
```

> 联调需后端在 `:8080`（见根 `README.md` / `docs/`）。前端只走 `/api/*` 相对路径，dev 由 Vite 代理转发，生产同源部署。

---

## 关键约定

- **统一走 apiClient**：业务请求一律用 `api/client.ts` 的 `apiClient.get/post/put/del`，不裸调 `fetch`。它自动：
  - 注入 `Authorization: Bearer <token>`（与后端 Sa-Token 配置一致）；
  - 解包 `Result<T>`：`code=0` 返回 `data`，否则抛 `ApiError{code,message}` 并 `Toast.error`；
  - `code≠0` 时可传 `{ silent: true }` 关闭默认 toast，自行处理错误。
- **401 / 40110 反应式登出**：apiClient 命中即清 `tokenStore` + `authStore`；`AuthGuard` 订阅会话态后自动跳 `/login`（无需命令式导航）。
- **登录态权威在后端**：前端菜单/路由可见性仅为体验层（`navConfig` 的 `roles`），最终鉴权恒由后端 `@SaCheckRole` 决定。
- **新增一个业务页**：在 `layout/navConfig.ts` 增一条（`domain/label/path/screenId/roles`），`router.tsx` 会据此自动生成受保护路由；随后把 `pages/` 占位替换为真实页（U14）。这样路由表与导航/原型屏保持一一对应。

---

## 相关设计文档

- 前端外壳详细设计：`docs/design/detail/U3-frontend-shell.md`
- 认证契约（登录/me/登出）：`docs/design/detail/U2-auth-permission.md`
- 原型与信息架构：`docs/prototype/index.html`
- 计划看板（U-ID/波次/关键路径）：`docs/plan/procurement-plan.md`
