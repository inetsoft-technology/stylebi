# Portal 前端页面单元测试策略设计

**日期**: 2026-06-08  
**作者**: bonnieshi  
**范围**: `community/web/projects/portal/` — 所有用户可访问路由下的前端页面组件

---

## 1. 目标

**首要目标（每个组件必须完成）：**

- **回归覆盖**：覆盖每个页面组件的核心功能路径（happy path + 主要 error path），确保功能正确、未来改动不引入回归

**按需目标（由 pre-scan 指标触发时才添加）：**

| 触发条件 | 扩展目标 |
|---------|---------|
| `async_zones ≥ 3` | 捕获时序竞态、并发状态不一致等手动测试不易复现的问题 |
| `async_zones ≥ 1` | 验证内存泄漏、`ngOnDestroy` 订阅清理 |
| `dispatch_points ≥ 3` | 覆盖空数据、null/undefined、超大数据量、网络错误、极限输入等边界场景 |

---

## 2. 技术栈

| 层 | 技术 |
|----|------|
| 测试框架 | Vitest (ESM worker mode) |
| 组件渲染 | Angular Testing Library (`@testing-library/angular`) |
| HTTP mock | MSW (`msw`) |
| 断言 | `@testing-library/jest-dom` |
| 类型覆盖 | Vitest globals (`describe`, `it`, `expect`, `vi.fn`) |

所有新测试文件使用 `*.tl.spec.ts` 后缀，与 em 项目 PR #3858 建立的风格保持一致。

---

## 3. 路由优先级

按风险高低排序（异步逻辑最多、状态最复杂的优先）：

| 顺序 | 路由 | 枚举入口 | 风险原因 |
|------|------|---------|---------|
| 1 | `/composer` | `composer/composer.routes.ts` | 最多异步交互、拖拽/WebSocket/状态机 |
| 2 | `/viewer` | `viewer/viewer.routes.ts` | 实时数据加载、选择关联、钻取流程 |
| 3 | `/portal` | `portal/portal.routes.ts` | 导航/权限/dashboard 加载 |
| 4 | `/embed` | `embed/chart/embed-chart.routes.ts` | 范围最小，风险最低 |

---

## 4. 整体流程（四阶段）

```
Phase 1: 枚举候选组件
  └─ 读路由文件 → 找顶层路由组件 → 展开 1-2 层子组件 → 输出候选清单

Phase 2: Workflow 批量 Pre-scan
  └─ 并行读取每个候选组件 → 计算三项指标 → 单 pass / 多 pass 分类
     → 若组件有旧 .spec.ts，同时扫旧 spec，摘要有价值 case 写入报告备注列
     → 输出 pre-scan 报告（含跳过建议、多 pass 计划、旧 spec 备注）

Phase 3: 人工审核
  └─ 审核报告 → 标记推进 / 跳过 / 调整 pass 计划

Phase 4: 逐组件测试生成
  └─ 用 component-risk-driven-generation-prompt.md 生成测试
     → vitest 验证 → 删除旧 spec（如有）→ 提交
```

---

## 5. 候选组件枚举规则

### 纳入范围
- 路由文件直接映射的顶层路由组件
- 顶层组件的 `@Component.imports` 或模板中直接使用的、有独立 `.ts` 逻辑文件的子组件（1-2 层深）

### 排除规则
- 纯 pipe（无模板逻辑）
- 纯 directive（无模板，仅 DOM 操作且 logic_lines < 50）
- 第三方 Angular Material 组件
- logic_lines < 50 AND async_zones = 0 AND dispatch_points = 0 的纯展示组件

### widget/ 目录特殊规则
widget/ 中已有 `.spec.ts` 的组件**不自动跳过**，按如下逻辑处理：

- **单 pass 类 且 旧 spec 已完整覆盖核心功能路径（happy path + 主要 error path）** → 自动标记 ⏭ 跳过（人工可覆盖）
- **其余情况** → 照常推进；pre-scan 阶段同时扫旧 `.spec.ts`，将有价值的 case 摘要写入报告备注列；生成新 `.tl.spec.ts` 时只读源码 + 报告备注，验证通过后直接删除旧 `.spec.ts`，不保留旧 case

原因：现有 widget/ spec 质量和覆盖率不满足要求，后续逐步替换。

---

## 6. Pre-scan 分类标准

来源：`.claude/component-risk-driven-generation-prompt.md` Stage 1

| 指标 | 定义 |
|------|------|
| `logic_lines` | 总行数减去 import、注释、纯模板字符串 |
| `dispatch_points` | 单字段驱动 3+ 条不同代码路径的方法数 |
| `async_zones` | HTTP 调用数 + WebSocket 订阅数 + 事件总线订阅数 |

| 分类 | 条件 | 测试策略 |
|------|------|---------|
| **单 pass** | logic_lines ≤ 500 AND dispatch ≤ 2 AND async ≤ 5 | 单文件 `ComponentName.tl.spec.ts`（按需在同文件内加竞态/边界 case） |
| **多 pass** | logic_lines > 500 OR dispatch ≥ 3 OR async > 5 | 多文件，见 Pass 计划 |

**旧 spec 扫描（仅适用于 widget/ 中有旧 `.spec.ts` 的组件）：**

pre-scan 阶段读取旧 `.spec.ts`，识别其中**不容易从源码推断的 case**（如针对特定 bug 的回归测试），摘要写入报告备注列。示例格式：

| 组件 | 分类 | 旧 spec 有价值 case | 决策 |
|------|------|-------------------|------|
| FooComponent | 单 pass | 无 | ✅ 推进，从源码生成 |
| BarComponent | 单 pass | 有：测试空 label 时不报错（bug 回归） | ✅ 推进，生成时附带备注 |
| BazComponent | 单 pass | 无，且已完整覆盖核心路径 | ⏭ 跳过 |

生成新 `.tl.spec.ts` 时只读**源码 + 报告备注**，不重新读旧 spec，保持上下文精简。

### 单 pass — 按需追加规则

核心回归 case 完成后，按以下条件决定是否在同一文件内追加额外 case：

| 触发条件 | 目标 | 技术手段 |
|---------|------|---------|
| `async_zones ≥ 3` | **时序/竞态** | MSW handler 内 `await delay(ms)` 模拟慢响应；`waitFor()` + timeout 验证竞态；测试"慢响应期间触发第二次操作"场景 |
| `async_zones ≥ 1` | **内存泄漏** | 测试 `ngOnDestroy` 是否清理所有订阅（有订阅就有泄漏风险，与文件大小无关） |
| `dispatch_points ≥ 3` | **边界情况** | 空数组、null/undefined、超长字符串、特殊字符、并发请求、HTTP 500/404/网络断开、partial API success |

若三项条件均未触发，仅写核心回归 case 即可。

### 多 pass — Pass 计划

| Pass | 文件名 | 覆盖方法类型 | 是否必须 |
|------|--------|------------|---------|
| Pass 1 — Interaction | `ComponentName.interaction.tl.spec.ts` | 路由导航、HTTP 数据加载、生命周期钩子、用户触发流程（**回归主体**） | **必须** |
| Pass 2 — Risk | `ComponentName.risk.tl.spec.ts` | 异步竞态、sync 覆盖 async、批量操作、await 前后状态不一致、破坏性操作 | 仅当 `async_zones ≥ 3` |
| Pass 3 — Display | `ComponentName.display.tl.spec.ts` | 标签计算、图标选择、纯条件展示逻辑、边界输入 | 仅当 `dispatch_points ≥ 3` |

---

## 7. 测试文件约定

### 文件命名

| 场景 | 文件名 |
|------|--------|
| 单 pass 类 | `ComponentName.tl.spec.ts` |
| L 类 Pass 1（必须） | `ComponentName.interaction.tl.spec.ts` |
| L 类 Pass 2（按需） | `ComponentName.risk.tl.spec.ts` |
| L 类 Pass 3（按需） | `ComponentName.display.tl.spec.ts` |
| L 类共享 render helper | `ComponentName.test-helpers.ts` |

所有文件与被测组件同目录。

### 最小骨架

```ts
import { it } from "@jest/globals";
import { render, waitFor } from "@testing-library/angular";
import { http, HttpResponse } from "msw";
import { server } from "../../../../../../mocks/server";

async function renderComponent() {
  const result = await render(MyComponent, {
    providers: [provideHttpClient(), { provide: FooDep, useValue: { fn: vi.fn() } }],
    schemas: [NO_ERRORS_SCHEMA],
  });
  return result.fixture.componentInstance;
}

describe("Group 1 — someMethod [Risk 3]", () => {
  // 🔁 Regression-sensitive: <why>
  it("should ...", async () => {
    server.use(http.post("*/api/composer/x", () => HttpResponse.json({ ok: true })));
    const comp = await renderComponent();
    comp.someMethod();
    await waitFor(() => expect(comp.field).toBe(true));
  });
});
```

---

## 8. MSW Handler 分层策略

### Handler 文件结构

```
mocks/handlers/
  portal.handlers.ts    ← 已存在
  em.handlers.ts        ← 已存在（PR #3858 参考）
  composer.handlers.ts  ← 需新建（/composer 路由开始前创建）
  viewer.handlers.ts    ← 需新建（/viewer 路由开始前创建）
```

### 三层覆盖模型

| 层 | 位置 | 用途 |
|----|------|------|
| **Layer 1 — 全局默认** | `mocks/handlers/composer.handlers.ts` | 整个路由下最常见 API 的合理默认值，保证大多数组件 render 不报错 |
| **Layer 2 — 组件级覆盖** | spec 文件 `beforeEach` 内 `server.use(...)` | 该组件专属接口或特定场景数据 |
| **Layer 3 — 单测级覆盖** | 单个 `it` 内 `server.use(...)` | 错误、超时、边界数据等特殊场景 |

### vitest-setup-tl.ts 配置（保持不变）

```ts
beforeAll(() => server.listen({ onUnhandledRequest: "error" }))
afterEach(() => server.resetHandlers())
afterAll(() => server.close())
```

`onUnhandledRequest: "error"` 强制每个测试显式声明所有 HTTP 调用，防止遗漏 mock 导致静默错误。

---

## 9. /composer 路由首轮执行步骤

1. **枚举候选组件**：读 `composer/composer.routes.ts` + 展开 1-2 层，写入 `docs/superpowers/specs/test-prescan/composer-candidates.txt`
2. **运行 Pre-scan Workflow**：输入候选列表，输出 `docs/superpowers/specs/test-prescan/composer-prescan-YYYY-MM-DD.md`
3. **人工审核报告**：标注每行决策（✅推进 / ⏭跳过 / 🔄调整 pass）
4. **逐组件生成测试**：
   - 确认 `mocks/handlers/composer.handlers.ts` 有所需默认 handler
   - 用 `component-risk-driven-generation-prompt.md` 生成测试文件
   - 运行 `npx vitest run path/to/spec` 验证
   - 旧 `.spec.ts` 验证通过后同 PR 删除
   - 提交，进入下一个组件

### 进度追踪

pre-scan 报告本身即为进度看板，状态列：`待审核` → `✅已测试` → `⏭已跳过`。

### 四路由规模（已按实际预扫描更新）

| 路由 | 候选组件数 | 推进数 | 数据来源 |
|------|----------|-------|---------|
| `/composer` | 80 | 78 | composer-prescan-2026-06-08.md |
| `/viewer` | 30–45 | 20–30 | 预估（待预扫描） |
| `/portal` | 77 | 77 | portal-prescan-2026-06-08.md |
| `/embed` | 5–10 | 3–6 | 预估（待预扫描） |

---

## 10. 参考资料

- 测试生成 prompt：`community/web/.claude/component-risk-driven-generation-prompt.md`
- 补充技术说明：`community/web/.claude/unit-test-techniques-supplement.md`
- em 项目参考 PR：`https://github.com/inetsoft-technology/stylebi/pull/3858`
- MSW 服务端：`community/web/mocks/server.ts`
- Portal vitest 配置：`community/web/projects/portal/src/vitest-setup-tl.ts`
