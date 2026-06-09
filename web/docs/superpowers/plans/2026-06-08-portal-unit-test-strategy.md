# Portal 前端页面单元测试 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为 Portal 应用所有用户可访问路由下的前端页面组件建立高质量 ATL+MSW 单元测试，按 /composer → /viewer → /portal → /embed 顺序推进。

**Architecture:** 每个路由三步走：(1) 枚举 1-2 层候选组件；(2) 运行 pre-scan workflow 做单 pass / 多 pass 分类；(3) 对审核通过的组件逐个用 risk-driven prompt 生成 `.tl.spec.ts` 测试文件，旧 `.spec.ts` 在新测试通过后同步删除。

**Tech Stack:** Angular Testing Library (`@testing-library/angular`), MSW (`msw`), Vitest, Angular 21 standalone components, TypeScript

**Working directory for all commands:** `web/`

---

## File Structure

| 动作 | 路径 | 职责 |
|------|------|------|
| **Create** | `.claude/workflows/portal-prescan.js` | Pre-scan workflow：批量计算 S/M/L 分类，输出报告 |
| **Create** | `docs/superpowers/specs/test-prescan/composer-candidates.txt` | /composer 候选组件路径清单 |
| **Create** | `docs/superpowers/specs/test-prescan/composer-prescan-YYYY-MM-DD.md` | /composer pre-scan 报告 |
| **Expand** | `mocks/handlers/composer.handlers.ts` | 已存在，按需添加 Layer 1 默认 handler |
| **Create** | `mocks/handlers/viewer.handlers.ts` | /viewer 路由的 Layer 1 默认 handler |
| **Per-component** | `projects/portal/src/app/**/ComponentName.tl.spec.ts` | 由 risk-driven prompt 生成 |
| **Delete** | `projects/portal/src/app/**/ComponentName.spec.ts` | 旧测试，新测试验证通过后删除 |

---

## Task 1: 创建 Pre-scan Workflow 脚本

**Files:**
- Create: `.claude/workflows/portal-prescan.js`

- [ ] **Step 1: 创建 workflow 目录**

```bash
mkdir -p .claude/workflows
```

- [ ] **Step 2: 写入 workflow 脚本**

创建 `.claude/workflows/portal-prescan.js`，内容如下：

```js
export const meta = {
  name: 'portal-prescan',
  description: 'Batch pre-scan Angular components: compute S/M/L classification and generate pass plans',
  phases: [
    { title: 'Scan', detail: 'Read each component and compute logic_lines, dispatch_points, async_zones' },
    { title: 'Report', detail: 'Aggregate into Markdown pre-scan report' },
  ],
}

const COMPONENT_SCHEMA = {
  type: 'object',
  required: ['filePath', 'componentName', 'logicLines', 'dispatchPoints', 'asyncZones', 'classification', 'recommendation'],
  properties: {
    filePath: { type: 'string' },
    componentName: { type: 'string' },
    logicLines: { type: 'number' },
    dispatchPoints: { type: 'number' },
    asyncZones: { type: 'number' },
    classification: { type: 'string', enum: ['single-pass', 'multi-pass'] },
    recommendation: { type: 'string', enum: ['proceed', 'skip'] },
    skipReason: { type: 'string' },
    passPlan: {
      type: 'array',
      items: {
        type: 'object',
        required: ['pass', 'fileName', 'methodsInScope', 'reason'],
        properties: {
          pass: { type: 'number' },
          fileName: { type: 'string' },
          methodsInScope: { type: 'string' },
          reason: { type: 'string' },
        },
      },
    },
    hasExistingSpec: { type: 'boolean' },
    existingSpecPath: { type: 'string' },
    oldSpecNotes: { type: 'string' },
  },
}

phase('Scan')
const results = await pipeline(
  args.candidates,
  async (filePath) => agent(
    `Pre-scan this Angular component file for test planning.

File to read: ${filePath}

Step 1 — Read the file completely.

Step 2 — Compute metrics:
- logicLines: total non-blank lines MINUS (import lines + comment-only lines + lines containing only HTML/template strings)
- dispatchPoints: count of methods where a single field (e.g. item.type, this.mode) drives 3+ distinct code paths via switch/if-else chains
- asyncZones: sum of (HttpClient method calls) + (this.*.subscribe() calls) + (WebSocket onmessage/subscribe handlers) + (setInterval/setTimeout used for polling)

Step 3 — Classify:
- single-pass: logicLines ≤ 500 AND dispatchPoints ≤ 2 AND asyncZones ≤ 5
- multi-pass: logicLines > 500 OR dispatchPoints ≥ 3 OR asyncZones > 5

Step 4 — Determine recommendation:
Set recommendation = "skip" if ANY of these conditions hold:
  a) logicLines < 50 AND asyncZones = 0 AND dispatchPoints = 0
  b) Classification is single-pass AND a *.spec.ts or *.tl.spec.ts exists in the same directory AND that file appears to cover core functionality (happy path + main error path)
Otherwise set recommendation = "proceed". If skipping, fill in skipReason.

Step 5 — If hasExistingSpec: briefly read the existing spec and note any test cases NOT easily derivable from the component source (e.g. specific bug regression tests). Put a one-line summary in oldSpecNotes, or "none" if nothing notable.

Step 6 — For multi-pass only: build passPlan by assigning EVERY method to exactly one pass.
  Pass 1 (interaction) — always required: router navigation, HTTP data loading, ngOnInit/ngOnDestroy lifecycle, user-triggered flows (click, search, drag)
  Pass 2 (risk) — ONLY if asyncZones ≥ 3: async race conditions, sync-overrides-async, batch ops, state inconsistency across await, destructive actions (delete/move/overwrite)
  Pass 3 (display) — ONLY if dispatchPoints ≥ 3: label computation, icon selection, pure conditional display with no side effects, boundary inputs
  For each pass: fileName = ComponentName.interaction.tl.spec.ts / .risk.tl.spec.ts / .display.tl.spec.ts

Step 7 — Check if ComponentName.spec.ts or ComponentName.tl.spec.ts exists in the same directory as the input file.
Set hasExistingSpec = true and existingSpecPath = that path if found.

Return the complete structured result.`,
    {
      label: `scan:${filePath.split(/[\\/]/).pop().replace('.ts', '')}`,
      phase: 'Scan',
      schema: COMPONENT_SCHEMA,
    }
  )
)

phase('Report')
const valid = results.filter(Boolean)
const routeName = args.route || 'unknown'

const tableRows = valid.map(r => {
  const passInfo = r.passPlan && r.passPlan.length > 0
    ? r.passPlan.map(p => `P${p.pass}: ${p.fileName}`).join('<br>')
    : r.classification === 'multi-pass' ? '—' : 'single pass'
  const status = r.recommendation === 'skip' ? '⏭ 跳过' : '✅ 推进'
  const oldSpec = r.hasExistingSpec ? `⚠️ ${r.existingSpecPath.split(/[\\/]/).pop()}` : ''
  const notes = r.oldSpecNotes && r.oldSpecNotes !== 'none' ? r.oldSpecNotes : ''
  return `| 待审核 | ${r.componentName} | ${r.logicLines} | ${r.dispatchPoints} | ${r.asyncZones} | **${r.classification}** | ${status} | ${oldSpec} | ${notes} | ${passInfo} |`
}).join('\n')

const proceed = valid.filter(r => r.recommendation === 'proceed').length
const skipped = valid.filter(r => r.recommendation === 'skip').length
const multiPass = valid.filter(r => r.classification === 'multi-pass').length

const multiPassDetail = valid
  .filter(r => r.classification === 'multi-pass' && r.passPlan && r.passPlan.length > 0)
  .map(r =>
    `### ${r.componentName}\n\n` +
    r.passPlan.map(p =>
      `**Pass ${p.pass}** (\`${p.fileName}\`)\n- Methods: ${p.methodsInScope}\n- Reason: ${p.reason}`
    ).join('\n\n')
  ).join('\n\n')

const report = `# ${routeName} Route Pre-scan Report

**候选组件数**: ${valid.length} | **建议推进**: ${proceed} | **建议跳过**: ${skipped} | **多 pass 组件**: ${multiPass}

## 状态说明
- 第一列「状态」初始为「待审核」，人工审核后改为 ✅已测试 / ⏭已跳过
- ⚠️ 有旧spec — 新测试通过后在同 PR 内删除旧 .spec.ts

## 候选组件清单

| 状态 | 组件 | logic_lines | dispatch | async_zones | 分类 | 建议 | 旧 spec | 旧 spec 备注 | Pass 计划 |
|------|------|-------------|----------|-------------|------|------|---------|-------------|-----------|
${tableRows}

## 多 pass 组件详情

${multiPassDetail || '— 无多 pass 组件 —'}
`

return { report, results: valid }
```

- [ ] **Step 3: 验证脚本语法（无 TypeScript，纯 JS）**

确认脚本：
- 无 TypeScript 类型注解（`: string`, `interface`, `<T>`）
- `meta` 对象为纯字面量（无变量、无模板字符串）
- 使用 `pipeline` 而非 `parallel`（无需跨组件聚合）

---

## Task 2: 枚举 /composer 候选组件

> 详细执行规则参考：`docs/superpowers/specs/2026-06-08-composer-route-spec.md`

**Files:**
- Create: `docs/superpowers/specs/test-prescan/composer-candidates.txt`

- [ ] **Step 1: 读取路由文件，找顶层组件**

读取 `projects/portal/src/app/composer/composer.routes.ts`，记录 `component:` 指向的类名及其文件路径（通常为 `composer-app.component.ts`）。

- [ ] **Step 2: 展开顶层组件的直接子组件（第 1 层）**

读取顶层路由组件文件，收集：
- `@Component.imports` 数组中的自定义组件（排除 Angular Material、AsyncPipe、NgIf 等框架内置）
- 模板中使用的非内置选择器对应的组件文件

对每个候选，确认存在对应 `.component.ts` 文件。

- [ ] **Step 3: 展开第 2 层（直接子组件的子组件）**

对第 1 层中 **逻辑行数估计 > 100 行** 的组件，重复 Step 2，再展开一层。纯展示组件（仅有 `@Input` 绑定、无服务注入、无方法逻辑）到此截止。

- [ ] **Step 4: 应用排除规则，写入候选清单**

排除以下内容：
- 纯 pipe 文件（`*.pipe.ts`）
- 纯 directive（`*.directive.ts`，无模板，logic_lines 预估 < 50）
- Angular Material 组件（`mat-*`）
- `logic_lines < 50 AND async_zones = 0 AND dispatch_points = 0` 的纯展示组件

将每个候选组件的**相对于 `web/` 的路径**写入文件，每行一个：

```
# /composer route candidates — YYYY-MM-DD
projects/portal/src/app/composer/composer-app.component.ts
projects/portal/src/app/composer/toolbar/composer-toolbar.component.ts
projects/portal/src/app/composer/dialog/composer-dialog.component.ts
# ... 其余组件
```

---

## Task 3: 运行 /composer Pre-scan Workflow

**Files:**
- Create: `docs/superpowers/specs/test-prescan/composer-prescan-YYYY-MM-DD.md`（由 workflow 输出）

- [ ] **Step 1: 读取候选清单，构造 args**

读取 `docs/superpowers/specs/test-prescan/composer-candidates.txt`，将非注释行收集为数组。

- [ ] **Step 2: 调用 Workflow 工具**

使用以下参数调用 Workflow 工具：

```json
{
  "name": "portal-prescan",
  "args": {
    "route": "composer",
    "date": "YYYY-MM-DD",
    "candidates": [
      "projects/portal/src/app/composer/composer-app.component.ts",
      "..."
    ]
  }
}
```

- [ ] **Step 3: 将报告写入文件**

Workflow 返回 `result.report`（Markdown 字符串）。将其写入：

```
docs/superpowers/specs/test-prescan/composer-prescan-YYYY-MM-DD.md
```

---

## Task 4: 人工审核 /composer Pre-scan 报告

**Files:**
- Modify: `docs/superpowers/specs/test-prescan/composer-prescan-YYYY-MM-DD.md`

- [ ] **Step 1: 逐行审核，更新「状态」列**

打开报告，对每一行作出决策，将第一列「待审核」改为：
- `✅ 推进` — 按建议推进测试生成
- `⏭ 跳过` — 确认跳过（可覆盖 workflow 的建议）
- `🔄 调整` — 推进，但修改 pass 计划（在备注列说明）

审核时重点关注：
- `async_zones > 0` 的组件优先推进（时序 bug 高发区）
- 多 pass 组件先确认 Pass 1（Interaction）的方法分配合理
- 已有旧 spec 且单 pass 类的组件：检查旧 spec 是否真的覆盖了核心功能路径（happy path + 主要 error path）；若未覆盖，改为「✅ 推进」

- [ ] **Step 2: 确定首批生成顺序**

在报告顶部添加「首批推进顺序」列表（按 async_zones 降序，多 pass 优先）：

```markdown
## 首批推进顺序（按风险降序）
1. ComponentA — 多 pass，async_zones=7
2. ComponentB — 单 pass，async_zones=4
3. ComponentC — 单 pass，async_zones=3
...
```

---

## Task 5: /composer 组件测试生成循环

> 对报告中每个「✅ 推进」组件重复以下步骤。从首批推进顺序列表顶部开始。

**Files:**
- Possibly modify: `mocks/handlers/composer.handlers.ts`
- Create: `projects/portal/src/app/composer/**/ComponentName.tl.spec.ts`（或 `.risk.tl.spec.ts` / `.interaction.tl.spec.ts`）
- Possibly delete: `projects/portal/src/app/composer/**/ComponentName.spec.ts`

- [ ] **Step 1: 检查并补充 Layer 1 handler**

读取目标组件源码，列出其所有 HTTP 调用的 URL 模式（如 `this.http.get('/api/composer/...')`）。

打开 `mocks/handlers/composer.handlers.ts`，对其中**尚未覆盖**的 URL 添加合理默认 handler：

```ts
// 示例：添加一个新的 Layer 1 默认 handler
http.get("*/api/composer/viewsheet/list", () =>
  HttpResponse.json({ viewsheets: [] })
),
```

不要添加会改变现有 handler 行为的代码——只添加新 URL 的默认值。

- [ ] **Step 2: 调用 risk-driven prompt 生成测试文件**

以如下格式向 Claude Code 发送请求（将 `<COMPONENT_PATH>` 替换为实际路径）：

```
Follow the instructions in docs/superpowers/prompts/risk-driven-test-generation.md

测试范围: <COMPONENT_PATH>
Example: projects/portal/src/app/composer/toolbar/composer-toolbar.component.ts

参考测试文件: projects/em/src/app/auditing/audit-bookmark-history/audit-bookmark-history.component.tl.spec.ts

Prescan 数据: [选项 A — 粘贴该组件在 pre-scan 报告中的表格行（multi-pass 时一并附上详情段落）/ 选项 B — pre-scan 文件路径，如 docs/superpowers/specs/test-prescan/portal-route-prescan-2026-06-08.md]
当前 Pass: [single-pass 时省略 / Pass 1 / Pass 2 / Pass 3 — 按 pre-scan 报告中的分类填写]
```

等待阶段 1 确认输出，核实分类与 Pass 计划与 pre-scan 报告一致；multi-pass 组件需指定当前 Pass 后再继续。

- [ ] **Step 3: 保存生成的测试文件**

生成的测试代码保存至与组件同目录：
- 单 pass 类 → `ComponentName.tl.spec.ts`
- 多 pass Pass 1（必须） → `ComponentName.interaction.tl.spec.ts`
- 多 pass Pass 2（async ≥ 3） → `ComponentName.risk.tl.spec.ts`
- 多 pass Pass 3（dispatch ≥ 3） → `ComponentName.display.tl.spec.ts`

- [ ] **Step 4: 运行 vitest 验证**

```bash
npx vitest run projects/portal/src/app/composer/path/to/ComponentName.tl.spec.ts
```

预期输出：所有测试 PASS，无编译错误，无 `Unhandled request` MSW 警告。

常见错误处理：

| 错误 | 修复方法 |
|------|---------|
| `Cannot find module '...mocks/server'` | 检查 import 路径中的 `../../../../../../mocks/server` 层数是否与文件深度匹配 |
| `Unhandled request: GET */api/...` | 在 `mocks/handlers/composer.handlers.ts` 中补充对应的 Layer 1 handler |
| `NullInjectorError: No provider for XxxService` | 在 `renderComponent()` 的 `providers` 数组中添加 `{ provide: XxxService, useValue: { method: vi.fn() } }` |
| `NG0100: ExpressionChangedAfterItHasBeenChecked` | 已由 `vitest-setup.ts` 抑制，若仍出现检查是否有 `setTimeout` 导致的同步检测问题 |

- [ ] **Step 5: 删除旧 spec（如有）**

如果同目录存在 `ComponentName.spec.ts`（注意：不是 `.tl.spec.ts`），且 Step 4 全部通过，则删除旧文件：

```bash
rm "projects/portal/src/app/composer/path/to/ComponentName.spec.ts"
```

- [ ] **Step 6: 更新 pre-scan 报告状态**

将 `composer-prescan-YYYY-MM-DD.md` 中该组件的「状态」列改为 `✅已测试`。

- [ ] **Step 7: 对下一个「✅ 推进」组件重复 Step 1–6**

---

## Task 6: 枚举 + Pre-scan /viewer 路由

> 详细执行规则参考：`docs/superpowers/specs/2026-06-08-viewer-route-spec.md`

**Files:**
- Create: `mocks/handlers/viewer.handlers.ts`
- Create: `docs/superpowers/specs/test-prescan/viewer-candidates.txt`
- Create: `docs/superpowers/specs/test-prescan/viewer-prescan-YYYY-MM-DD.md`
- Modify: `mocks/server.ts`

- [ ] **Step 1: 枚举 /viewer 候选组件**

与 Task 2 相同步骤，入口文件改为 `projects/portal/src/app/viewer/viewer.routes.ts`。

- [ ] **Step 2: 创建 viewer.handlers.ts**

```ts
// mocks/handlers/viewer.handlers.ts
import { http, HttpResponse } from "msw";

export const viewerHandlers = [
  // Layer 1 默认 handler — 在 viewer 组件测试生成过程中按需添加
];
```

- [ ] **Step 3: 注册到 mocks/server.ts**

打开 `mocks/server.ts`，添加 viewerHandlers 导入和注册：

```ts
import { viewerHandlers } from "./handlers/viewer.handlers";
// 在 setupServer(...) 调用中添加 ...viewerHandlers
```

- [ ] **Step 4: 运行 pre-scan workflow**

与 Task 3 相同，`args.route = "viewer"`，候选列表来自 `viewer-candidates.txt`。

- [ ] **Step 5: 人工审核报告**

与 Task 4 相同。

- [ ] **Step 6: 逐组件生成测试**

与 Task 5 相同，handler 文件改为 `mocks/handlers/viewer.handlers.ts`。

---

## Task 7: 枚举 + Pre-scan /portal 路由

> 详细执行规则参考：`docs/superpowers/specs/2026-06-08-portal-route-spec.md`

**Files:**
- Create: `docs/superpowers/specs/test-prescan/portal-candidates.txt`
- Create: `docs/superpowers/specs/test-prescan/portal-prescan-YYYY-MM-DD.md`

- [ ] **Step 1: 枚举候选组件**

入口文件：`projects/portal/src/app/portal/portal.routes.ts`。其余步骤同 Task 2。

注意：`portal.handlers.ts` 已存在（615 行），大部分 /portal API 已有默认 handler，Step 1 of Task 5 的 handler 补充工作量较少。

- [ ] **Step 2: 运行 pre-scan workflow**

`args.route = "portal"`。其余步骤同 Task 3–4。

- [ ] **Step 3: 逐组件生成测试**

同 Task 5，handler 文件使用已有的 `mocks/handlers/portal.handlers.ts`。

---

## Task 8: 枚举 + Pre-scan /embed 路由

> 详细执行规则参考：`docs/superpowers/specs/2026-06-08-embed-route-spec.md`

**Files:**
- Create: `docs/superpowers/specs/test-prescan/embed-candidates.txt`
- Create: `docs/superpowers/specs/test-prescan/embed-prescan-YYYY-MM-DD.md`

- [ ] **Step 1: 枚举候选组件**

入口文件：`projects/portal/src/app/embed/chart/embed-chart.routes.ts`。预估组件数 5–10，工作量最小。

- [ ] **Step 2: 运行 pre-scan workflow 并审核**

`args.route = "embed"`。其余步骤同 Task 3–4。

- [ ] **Step 3: 逐组件生成测试**

同 Task 5。embed 路由可能复用 `portal.handlers.ts` 中的 handler，无需新建 handler 文件。

---

## 参考资料

| 资源 | 路径 |
|------|------|
| Risk-driven 测试生成 prompt | `docs/superpowers/prompts/risk-driven-test-generation.md` |
| 测试技术补充说明 | `docs/superpowers/prompts/test-techniques-reference.md` |
| em 项目参考测试（ATL+MSW 范例） | `projects/em/src/app/auditing/audit-bookmark-history/audit-bookmark-history.component.tl.spec.ts` |
| MSW server 入口 | `mocks/server.ts` |
| Portal vitest TL 配置 | `projects/portal/src/vitest-setup-tl.ts` |
| 设计文档 | `docs/superpowers/specs/2026-06-08-portal-unit-test-strategy-design.md` |
