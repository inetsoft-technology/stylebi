# /embed 路由测试执行 Spec

**所属设计文档**: `docs/superpowers/specs/2026-06-08-portal-unit-test-strategy-design.md`
**路由入口**: `projects/portal/src/app/embed/chart/embed-chart.routes.ts`
**预估候选组件数**: 5–10 | **预估推进数**: 3–6

---

## 1. 前置准备

| 项目 | 状态 | 说明 |
|------|------|------|
| Pre-scan workflow | 需已存在 | `.claude/workflows/portal-prescan.js`（由 /composer spec 的 Step 0 创建） |
| Layer 1 handler 文件 | 复用已有 | `/embed` 路由 API 较少，优先复用 `mocks/handlers/portal.handlers.ts`；若有专属 API 则新建 `embed.handlers.ts` |
| vitest-setup-tl.ts | 已存在 | `projects/portal/src/vitest-setup-tl.ts` |

---

## 2. 执行步骤

### Step 1 — 枚举候选组件

读取 `projects/portal/src/app/embed/chart/embed-chart.routes.ts`，展开 1–2 层子组件，写入候选清单：

```
docs/superpowers/specs/test-prescan/embed-candidates.txt
```

**纳入范围**（参考 design §5）：
- 路由直接映射的顶层路由组件
- 顶层组件 `@Component.imports` / 模板中使用的、有独立 `.ts` 逻辑的子组件（1–2 层）

**排除**：纯 pipe、纯 directive（logic_lines < 50）、Angular Material 组件、logic_lines < 50 AND async_zones = 0 AND dispatch_points = 0 的纯展示组件

> /embed 范围最小，预估候选 5–10 个，排除后推进数约 3–6 个。

**widget/ 特殊规则**：有旧 `.spec.ts` 的 widget 组件不自动跳过；pre-scan 阶段同时扫旧 spec，摘要有价值 case 写入报告备注列（参考 design §6）。

---

### Step 2 — 运行 Pre-scan Workflow

输入 `embed-candidates.txt`，调用 `portal-prescan` workflow，输出报告：

```
docs/superpowers/specs/test-prescan/embed-prescan-YYYY-MM-DD.md
```

报告列结构：`状态 | 组件 | logic_lines | dispatch | async_zones | 分类 | 建议 | 旧 spec | 旧 spec 备注 | Pass 计划`

**分类规则**（参考 design §6）：
- **单 pass**：logic_lines ≤ 500 AND dispatch ≤ 2 AND async ≤ 5 → 单文件 `ComponentName.tl.spec.ts`
- **多 pass**：logic_lines > 500 OR dispatch ≥ 3 OR async > 5 → 多文件，见 Pass 计划

---

### Step 3 — 人工审核报告

标注每行决策：`✅推进` / `⏭跳过` / `🔄调整 pass`

审核重点：
- /embed 组件数少，建议尽量全部推进
- 多 pass 组件确认 Pass 1（Interaction）方法分配合理
- 单 pass 类有旧 spec 的组件：旧 spec 是否覆盖核心功能路径（happy path + 主要 error path）？

在报告顶部添加首批推进顺序。

---

### Step 4 — 逐组件生成测试

对每个「✅推进」组件按顺序执行：

**4a. 检查并补充 Layer 1 handler**

读取组件源码中所有 HTTP URL：
- 若 URL 已在 `portal.handlers.ts` 中覆盖 → 直接复用，无需新建文件
- 若有 /embed 专属 URL → 新建 `mocks/handlers/embed.handlers.ts` 并注册到 `mocks/server.ts`

**4b. 调用 risk-driven prompt 生成测试**

```
Follow the instructions in docs/superpowers/prompts/risk-driven-generation-prompt.md

Scope: <COMPONENT_PATH>
Reference test file: projects/em/src/app/auditing/audit-bookmark-history/audit-bookmark-history.component.tl.spec.ts
Current pass: [single pass / Pass 1 / Pass 2 — 按报告填写]
Old spec notes: [报告「旧 spec 备注」列内容，若有]
```

**4c. 文件命名**（参考 design §7）：

| 场景 | 文件名 |
|------|--------|
| 单 pass 类 | `ComponentName.tl.spec.ts` |
| 多 pass Pass 1（必须） | `ComponentName.interaction.tl.spec.ts` |
| 多 pass Pass 2（async ≥ 3） | `ComponentName.risk.tl.spec.ts` |
| 多 pass Pass 3（dispatch ≥ 3） | `ComponentName.display.tl.spec.ts` |
| 多 pass 共享 render helper | `ComponentName.test-helpers.ts` |

**单 pass 按需追加**（参考 design §6 单 pass 子节）：

| 触发条件 | 追加内容 |
|---------|---------|
| `async_zones ≥ 3` | 竞态/时序 case（MSW delay、并发触发） |
| `async_zones ≥ 1` | `ngOnDestroy` 订阅清理 case |
| `dispatch_points ≥ 3` | 边界 case（null/undefined、HTTP 错误） |

**4d. 运行 vitest 验证**

```bash
npx vitest run projects/portal/src/app/embed/path/to/ComponentName.tl.spec.ts
```

预期：所有测试 PASS，无编译错误，无 `Unhandled request` MSW 警告。

常见错误处理：

| 错误 | 修复方法 |
|------|---------|
| `Cannot find module '...mocks/server'` | 检查 import 路径层数与文件深度是否匹配 |
| `Unhandled request: GET */api/...` | 补充对应 Layer 1 handler（portal.handlers.ts 或新建 embed.handlers.ts） |
| `NullInjectorError: No provider for XxxService` | 在 `renderComponent()` 的 `providers` 中添加 mock provider |

**4e. 删除旧 spec**（验证全 PASS 后）

若旧 `*.spec.ts` 存在，删除前先做缺口检查：

1. 读取旧 spec，提取每个 `it` 的测试意图
2. 判断是否有旧 spec 中已覆盖、但新文件中遗漏的有价值测试点
3. 若有遗漏且有价值：补充到新文件并重新跑 4d；若无遗漏或遗漏无价值：直接删除

```bash
rm projects/portal/src/app/embed/path/to/ComponentName.spec.ts
```

**4f. 更新报告状态**：将该组件「状态」列改为 `✅已测试`，提交，进入下一个组件。

---

## 3. 进度追踪

`embed-prescan-YYYY-MM-DD.md` 本身即为进度看板：

`待审核` → `✅已测试` / `⏭已跳过`

---

## 4. 参考资料

| 资源 | 路径 |
|------|------|
| 整体设计文档 | `docs/superpowers/specs/2026-06-08-portal-unit-test-strategy-design.md` |
| Risk-driven 生成 prompt | `docs/superpowers/prompts/risk-driven-generation-prompt.md` |
| Pre-scan workflow | `.claude/workflows/portal-prescan.js` |
| 复用 handler | `mocks/handlers/portal.handlers.ts` |
| MSW server 入口 | `mocks/server.ts` |
| vitest TL 配置 | `projects/portal/src/vitest-setup-tl.ts` |
| em 参考测试文件 | `projects/em/src/app/auditing/audit-bookmark-history/audit-bookmark-history.component.tl.spec.ts` |
