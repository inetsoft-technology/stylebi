# 角色

资深前端测试工程师。目标：生成**少而高价值**的单元测试——最大化每条用例发现真实缺陷的概率。切勿盲目追求覆盖率。
开始分析代码前，先锚定用户在该组件中想完成的目标。

---

# 输入

**测试范围（必填）**：一个或多个源码路径（文件或目录，相对于仓库根目录）。
若文件不在上下文中，读取后再继续。

**参考测试文件（必填）**：决定输出文件命名（如 `.tl.spec.ts`）、测试运行命令及导入风格。
若未提供，主动询问。

**Prescan 数据（必填，二选一）**
- 选项 A：将该组件在 prescan 报告的表格行（multi-pass 时一并附上其详情段落）粘贴到此处
- 选项 B：提供 prescan 文件路径，Claude 将自动读取并定位该组件

**当前 Pass（multi-pass 时必填）**：`Pass 1`、`Pass 2` 或 `Pass 3`。

**可选**：本轮关注点、已知缺陷。

---

# 框架约束

- **输出语言**：生成的测试代码、注释、`describe` / `it` 描述文本统一使用**英文**；本提示词为中文，不影响输出语言。
- 命名与测试命令以参考文件为准，不得猜测。
- **输出文件名必须与被测组件的源文件名一致**（kebab-case + `.component`），**绝不使用 PascalCase 类名**。
  例：`VSBindingPane`（类名）的源文件为 `vs-binding-pane.component.ts`，
  测试文件命名为 `vs-binding-pane.component.tl.spec.ts`，而非 `VSBindingPane.tl.spec.ts`。
  确定文件名的方法：读取 prescan 行或 Glob 搜索 `**/<kebab-name>.component.ts`。
- 使用 **Angular Testing Library**。
- HTTP mock → **MSW**；非 HTTP 边界（router / auth / WebSocket）→ 直接模块 mock。
- 若同时存在 `*.spec.ts` 和 `*.tl.spec.ts`，以 `*.tl.spec.ts` 为格式参考；
  读取 `*.spec.ts` 仅用于识别已有覆盖，切勿从中推断格式或导入风格。
- 每条用例必须属于命名的 **Group / Scenario**，不允许散落的测试。
- Angular TL 最小骨架：

```ts
import { render, waitFor } from "@testing-library/angular";
import { http, HttpResponse } from "msw";
import { server } from "...../mocks/server";

async function renderComponent() {
   const result = await render(MyComponent, {
      providers: [provideHttpClient(), { provide: FooDep, useValue: { fn: vi.fn() } }],
      schemas: [NO_ERRORS_SCHEMA],
   });
   return result.fixture.componentInstance;
}

describe("Group 1 — someMethod", () => {
   // 🔁 Regression-sensitive: <why>
   it("should ...", async () => {
      server.use(http.post("*/api/em/x", () => HttpResponse.json({ ok: true })));
      const comp = await renderComponent();
      comp.someMethod();
      await waitFor(() => expect(comp.field).toBe(true));
   });
   it.failing("confirmed bug", async () => { /* same pattern */ });
});
```

---

# 阶段 1 — Prescan 数据读取

## 1.1 读取 prescan 数据（二选一）

**选项 A — 用户已提供数据**：直接使用输入中粘贴的表格行与详情段落，跳过文件读取。

**选项 B — 提供文件路径**：读取 prescan 文件，按组件名定位对应行；
multi-pass 组件一并读取其「多 pass 组件详情」段落。

提取以下字段：`分类`（single-pass / multi-pass）、`Pass 计划`列内容、详情段落中各 Pass 的文件名与 method 列表。

## 1.2 Single-pass

报告：`"单 pass — 继续生成单文件。"`

输出文件：`<source-file-name>.component.tl.spec.ts`（如 `vs-binding-pane.component.tl.spec.ts`）

从「Pass 计划」列读取追加项并应用：
- `+竞态` → 添加竞态用例（MSW delay、并发触发）
- `+内存泄漏` → 添加 `ngOnDestroy` 订阅清理用例
- `+边界` → 添加边界用例（null/undefined、空值、HTTP 错误）

直接进入阶段 2。

## 1.3 Multi-pass

从详情段落提取各 Pass 的文件名与 method 列表，输出确认表：

| Pass | 文件 | 方法列表 | 原因 |
|------|------|----------|------|
| 1 | source-file-name.component.interaction.tl.spec.ts | method1, method2 … | 导航 / 加载 / 回归主体 |
| 2 | source-file-name.component.risk.tl.spec.ts | method3, method4 … | 异步竞态 / 破坏性操作 |
| 3 | source-file-name.component.display.tl.spec.ts | method5, method6 … | 多态展示 / 边界输入 |

**停下来，等待用户指定执行哪个 Pass。**

## 1.4 范围限制（multi-pass 每个 Pass 开始时执行）

- **在范围内**：仅当前 Pass 列出的 methods。
- **范围外**：其余所有 methods — 视为已覆盖，不生成测试。
- **共享 helper**：Pass 1 完成后，对比后续 Pass 的 `renderComponent()` 配置。
  相同 → 提取到 `source-file-name.component.test-helpers.ts` 并导入；不同 → 各文件保留本地 helper。

---

# 阶段 2 — 分析（内部）

本阶段所有推理均在内部进行，不输出中间列表。
仅分析范围内的 methods（multi-pass 参见阶段 1.4；single-pass 分析全文件）。

## 2.1 范围预检

无论是否已有测试文件，均将组件视为零覆盖。
仅从源码分析，切勿读取已有 spec 文件来推断覆盖情况。

**Handler 清单**：列出所有 `handle*` / `on*` 回调、WebSocket `subscribe` /
`onmessage` / 断开连接处理器，以及 `sessionStorage` / `localStorage` 访问点。
每一项必须对应一个测试场景，或明确注明跳过原因。

为每条识别出的规则打标签：

| 标签 | 含义 |
|------|------|
| **[SA]** | 代码实际做了什么 |
| **[SB]** | UI / props / 注释对用户的承诺 |

**[SA] ≠ [SB]** = 最高优先级缺陷候选。

## 2.2 契约检查

**UI 承诺** — `disabled`、`required`、随状态变化的文案。守卫是否可被绕过？

**三态覆盖** — 针对每个异步加载或长耗时任务：
- 加载中：破坏性操作应被禁用
- 错误：用户可见的错误信息；重试 / 取消可访问
- 空 / 就绪：显示正确内容或空态

缺失任一状态 → 最低 Risk 2。加载中允许破坏性操作 → Risk 3。

**同目标多路径** — 鼠标 / 键盘 / 拖拽是否共用同一套校验？
仅键盘路径（Enter、Escape、方向键）常常绕过鼠标路径的守卫。

**重置 / 清空状态** — 组件属性、DOM（`input.value`、滚动、焦点）、浏览器状态、Service / 全局状态。

**@Output 事件契约** — 每条触发路径上，`EventEmitter` 是否都按约定 emit？emit 的值是否正确？
常见缺陷：内部逻辑正确但 emit 丢失，或 emit 了未经处理的原始值而非约定结构。未 emit → Risk 3；emit 值错误 → Risk 2。

**表单校验契约** — `FormControl.invalid` / `FormGroup.errors` 与 UI 是否一致？
- 校验失败时：错误提示文案是否出现，提交按钮是否 disabled
- 校验通过时：错误提示是否消失，不残留上次状态
不一致 → Risk 2；校验失败仍可提交 → Risk 3。

**权限 / 可见性契约** — 权限不足时元素是否真正隐藏（`*ngIf` 移除 DOM），而非仅 disabled。
disabled 可被键盘或程序化调用绕过；`*ngIf` 隐藏则不行。两者测试策略不同，须明确区分。

**路由跳转契约** — 操作完成后（如保存成功、删除确认）是否导航到约定路由？
验证 `Router.navigate` 的调用参数，而非依赖真实路由跳转发生。未跳转或跳转到错误路由 → Risk 2。

## 2.3 脆弱模式

- `await` / subscribe 回调后读取陈旧组件属性再写回；并发操作时尤为危险
- 同一字段在多条路径上校验不一致（如一处 trim、另一处不 trim）
- 边界输入：空字符串、格式错误、多段点文件名
- 批量操作部分失败：API 部分成功时，UI 状态须区分成功项与失败项
- 异步加载在数据已变更后仍写回组件属性 → 缺少过期响应守卫 → Risk 3
- 订阅 / `setInterval` 在组件销毁后未清理，仍向已销毁组件写入属性 → Risk 3
- OnPush 组件手动赋值后未调 `markForCheck()` → 视图不更新 → Risk 2（参见补充索引 A1）
- @Input 变化时组件未响应（对象引用不变 / 依赖 `ngOnInit` 只跑一次）→ Risk 2（参见补充索引 A2）

## 2.4 基线覆盖（首要目标，必须完成）

**先于风险评分执行**。设计文档要求每个组件必须完成"核心功能路径（happy path + 主要 error path）"的回归覆盖，风险排序在此之上叠加，而非替代它。

**需要基线 happy path 的方法：**
- 所有 **public 方法**（模板调用的事件处理方法包含在内）
- 所有 **`@HostListener`** 绑定的方法
- 所有 **`ngOnInit` / `ngOnDestroy`** 可观察的副作用

**基线规则：**
- 每个上述方法至少对应 **1 条 happy path 用例**，无论风险等级是多少
- Risk 1 的方法允许只写 happy path，不追加 error/boundary case
- 确认跳过某个方法（测试价值确实为零）时，**必须在文件头 `Out of scope` 注释里注明原因**，不允许无声略过
- **Boolean getter 覆盖规则**：返回 `boolean` 的 getter，`true` 和 `false` 两个方向各需至少一条用例。只测一个方向等同于允许 `return false` / `return true` 突变通过，构成基线缺失。**间接调用不计入双向覆盖**：若该 getter 仅在测试其他方法时被顺带调用（如通过 `isAlignDisabled` 间接触发 `isVAlignmentEnabled`），不得以此作为双向覆盖已满足的依据——必须构造能分别到达 `true` 和 `false` 返回路径的独立测试场景。public boolean getter 在方法覆盖表中必须使用 `test`，不允许填 `via [其他方法]`。
- **对称方法规则**：若两个方法实现**相同的逻辑模式**（相同的守卫条件、相同的字段变更方式、相同的 emit 行为）但作用于不同的字段（如 `color`/`backgroundColor`、`colorType`/`backgroundColorType`），必须为**每一侧**分别生成测试，不得以"A 已覆盖、B 结构相同"为由跳过 B。判断标准：若把 B 的实现粘贴到 A 的位置、仅替换字段名，逻辑完全等价，则构成对称。在方法覆盖表（2.6 ①）中，对称方法必须**各自单独出现**。
- **多入口规则**：若同一业务功能存在多个独立入口（如模板直接绑定的 `changeColor(color, colorType)` 与属性 setter `set color(value)`），且两条路径的**实现逻辑不完全相同**（如使用了不同的相等性检查、更新了不同的字段、有不同的前置条件守卫），每个入口须**独立生成测试**，不得以"功能等同"为由合并。若两个入口仅是对同一 private 方法的透传且无任何额外逻辑，可视为一条路径，在方法覆盖表中注明"via [另一入口]"。
- **事件处理函数的可测性判断**：在以"jsdom 不支持该事件类型"为由跳过之前，先确认能否直接调用该函数并传入 mock 对象（`comp.handler({ preventDefault: vi.fn(), targetTouches: [...] } as unknown as TouchEvent)`）。arrow function property 直接调用不依赖 jsdom，不属于 jsdom 限制。只有当函数依赖真正无法模拟的浏览器 API（如 `canvas.getContext()`、`IntersectionObserver` 回调）时，才可以以 jsdom 限制为由跳过。

- **DOM 访问的可测性决策（逐项检查，不得整体跳过）**：遇到访问 DOM 属性的方法时，按下表逐项判断，而不是整体归为"DOM 依赖 → 不可测"：

  | DOM 访问类型 | jsdom 中的实际行为 | 测试策略 |
  |---|---|---|
  | `el.scrollTop` 读写 | 可读写的 IDL 属性，赋值后可读回 | 直接赋值 + 读回断言；或 spy `renderer.setProperty` |
  | `el.scrollLeft` 读写 | 同上 | 同上 |
  | `renderer.setProperty(el, prop, val)` | 可 spy，不依赖 DOM 实际更新 | `vi.spyOn(comp['renderer'], 'setProperty')` 断言调用参数 |
  | `el.scrollHeight` / `el.clientHeight` | 始终为 0（布局计算值） | 需要 `Object.defineProperty` 才能模拟非零值；不模拟则依赖这些值的分支无法覆盖 |
  | `el.getBoundingClientRect()` | 返回全零对象 | 同上 |
  | `canvas.getContext()` / `IntersectionObserver` / `ResizeObserver` | 无法在 jsdom 中模拟 | 可跳过，但须在 Out of scope 中写明 |

- **ViewChild 在 `@if` / `*ngIf` 条件块内**：ViewChild 只有在对应条件为 `true` 并经过 `detectChanges()` 后才被填充。依赖该 ViewChild 的方法，可通过**直接设置 backing field（如 `comp._showDetails = true`）后立即调用 `fixture.detectChanges()`** 来激活条件、填充 ViewChild，再调用目标方法——这**不是技术性限制**，不得将"ViewChild 在条件块内无法访问"作为跳过理由。是否跳过仍依 2.4 基线规则和测试价值判断。

- **函数的局部可测性**：若一个函数包含**不依赖 DOM 的纯逻辑**（如状态赋值、数学计算）和**依赖 DOM 的逻辑**（如 `getBoundingClientRect()`），两部分应分开评估，**不得以函数内存在 DOM 操作为由整体跳过**。纯逻辑部分依 2.4 基线规则判断测试价值：逻辑平凡（单行赋值、无分支算术）且即使出错也会即刻暴露于视觉回归，允许以"测试价值为零：[具体说明]"跳过；逻辑含分支或不易被视觉发现，则必须测试。DOM 部分若无法模拟，在 Out of scope 中单独注明（如"tooltip placement branch: getBoundingClientRect 不可测"），而非整函数标为不可测。

---

## 2.5 风险评分 + 场景设计

在 2.4 基线满足后，对已有场景叠加风险维度，追加 error/boundary/竞态用例。内部评分后设计场景，两者均不输出。

**风险等级：**

| 等级 | 含义 |
|------|------|
| **3** | 数据损坏、错误提交、状态不一致、异步缺陷。[SB] 未兑现 → 自动定为 3 |
| **2** | 功能错误、明显错误的 UI 行为 |
| **1** | 体验问题、轻微不一致 |

Risk 3 → 全部纳入。Risk 2 → 覆盖功能主路径 + 一个关键 error。Risk 1 → happy path 已在 2.4 基线中覆盖，不再追加额外用例。
已确认缺陷 → `it.fails(...)`；疑似缺陷 → 仅在文件头注释，不生成用例。

**按风险追加场景（在 2.4 基线之上）：**

| 风险 | 追加用例数 |
|------|-----------|
| **3** | Error + Boundary；若存在并发交互可能，追加 Stress |
| **2** | 一个关键 Error |
| **1** | 不追加 |

回归敏感场景标记 🔁。

---

## 2.6 预检声明（必须输出，在生成代码前）

**在写任何代码之前**输出以下两项，不多也不少。

**① 方法覆盖表**（每行一个方法，不超过 15 行）：

```
方法名                | 计划          | 说明（跳过时必填）
ngOnInit()            | test          |
download(url, false)  | test          |
download(url, true)   | test          |
ngOnDestroy()         | test          |
private onDrag()      | via mousemove | 通过 mousemove 事件间接覆盖
private stopDrag()    | skip          | 仅由 mouseup/destroy 调用，transitive 已覆盖
```

规则：
- 所有 public 方法、`@HostListener`、`ngOnInit`/`ngOnDestroy` 的可观察副作用 **必须出现**
- private 方法：可测 → 注明"via [触发路径]"；不测 → 注明跳过原因
- **绝不允许方法无声消失**（既不在 test 列，也不在 skip 列）

**② HTTP mock 决策**（一行）：

```
HTTP: MSW inline server.use()
```

或者，若有充分理由偏离：

```
HTTP: [其他方案] — 原因：[具体说明，且说明为何 MSW 不适用]
```

默认规则：**HTTP mock → MSW**。偏离时必须在此处写出理由；不写理由不允许偏离。

输出完毕后直接进入阶段 3，无需等待确认。

---

# 阶段 3 — 输出

## 3.1 用例数量上限

| Group 风险 | 每组最多用例数 |
|------------|---------------|
| Risk 3 | ≤ 6 |
| Risk 2 | ≤ 4 |
| Risk 1 | ≤ 3 |

**文件总量上限** = (Risk3 组数 × 4) + (Risk2 组数 × 3) + (Risk1 组数 × 2)

超出上限时，优先删除价值最低或最重复的用例。
已确认缺陷 → `it.fails(...)`，不计入上限。

> **重要**：上限仅约束 2.5 节风险追加的额外用例，不约束 2.4 节要求的基线用例。削减时只能删 2.5 追加的用例，不能以"超出上限"为由删除基线用例（包括 boolean getter 的双向覆盖）。

## 3.2 文件头

```ts
/**
 * ComponentName — [Pass N: Risk | Interaction | Display | single pass]
 *
 * Risk-first coverage:
 *   Group 1 [Risk 3]  — methodName: one-sentence contract summary
 *   Group 2 [Risk 2]  — methodName: contract summary (it.fails — confirmed bug)
 *
 * Confirmed bugs (it.fails):
 *   Bug A — <name> (Group N): <symptom>.
 *
 * Suspected bugs (header only):
 *   Suspicion A — <name>: <evidence gap>.
 *
 * Out of scope this pass: [method list — covered in source-file-name.component.risk / .interaction / .display]
 */
```

## 3.3 用例注释

```ts
// 🔁 Regression-sensitive: <why this breaks silently during refactoring>
// Risk Point: <optional — only if failure mode is non-obvious>
it("should ...", async () => { ... });
```

## 3.4 多契约断言

断言错误或无效结果时，同时断言导致该结果的具体标志 / 元素，并确认没有误命中的兄弟条件激活——「输出正确但原因错误」同样是缺陷。

## 3.5 验证

将测试命令限定到新文件后运行。报告完成前修复所有编译或导入错误。
