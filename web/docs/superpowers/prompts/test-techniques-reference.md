# 单元测试：Technique 补充索引（配合主提示词使用）

## 文档关系

| 文档 | 角色 |
|------|------|
| **`risk-driven-test-generation.md`**（主提示词） | **主流程**：Prescan 读取 → 分析 → 风险评分 → 测试输出。日常生成 unit case **以该文件为准**。 |
| **本文** | **技术补充层**：按优先级 + 分类检索 Technique；将 ID 或详述中的表格/清单追加进对话或人工复核。 |

**原则**：分组上限、文件总量 cap 与阶段定义**以主提示词为准**；本文只提供技术层面的 Technique、反例代码与核对清单，不替代主流程。

### 配套使用（三步）

1. **只用主提示词全文**（`risk-driven-test-generation.md`）走完整流程。
2. **对照第二节「何时打开本文」**或第一节 P0 速查表，看当前组件是否命中。
3. **在对话末尾追加**一句指令或节选相关表格，例如：
   - 「请按 **T2、T3、M5** 检查多路径、file input 与请求竞态。」
   - 「请按 **T4b、M4** 补异步 handler 与订阅的陈旧状态/清理。」
   - 「列表可编辑，请按 **M6** 考虑 `key` 与行状态。」
   - 「组件用 OnPush，请按 **A1** 补 `markForCheck` 遗漏场景。」

   **勿**把本文全文粘进提示词。

---

## 优先级说明

| 级别 | 含义 | 使用建议 |
|------|------|----------|
| **P0** | 易导致**错数据、错提交、契约违背、路径不一致、并发错序** | 优先补规则与用例 |
| **P1** | **常见实现缺陷**（边界、可变、变更检测、effect、列表协调） | 第二轮补强或代码审查时扫一遍 |
| **P2** | **元信息、体验与按项目选用**（标签口径、a11y、浮层等） | 按需，不强行挤占分组上限与文件总量 cap |

---

## 一、全量速查表（按优先级 → 分类 → ID）

### P0

| 分类 | ID | 名称 | 典型缺口 |
|------|-----|------|----------|
| A 契约与可见行为 | **T1** | UI 承诺与可执行契约 | `accept`、`disabled`、`required`、状态文案；守卫可被绕过 |
| B 交互路径 | **T2** | 同目标多路径对称 | 一路严一路松；只测鼠标不测键盘/ref |
| C 状态与重置 | **T3** | 全栈状态与重置 | 只清组件属性，未清 DOM input、滚动、Service 状态 |
| D 异步与并发 | **T4b** | 异步与陈旧闭包 | `await` / subscribe 回调内读取旧状态再赋值 |
| D 异步与并发 | **M5** | 请求竞态与过时响应 | 多请求乱序返回；后发先至覆盖界面 |
| E 校验与数据 | **T4c** | 同一字段多处校验一致性 | `trim` 与裸值两套；需结合**可触达性**评 Risk |

### P1

| 分类 | ID | 名称 | 典型缺口 |
|------|-----|------|----------|
| E 校验与数据 | **T4d** | 纯函数 / 正则 / 边界输入 | 无扩展名、多点、空串、异常格式 |
| E 校验与数据 | **M1** | 控制模式与展示/实际值 | 受控/非受控混用；展示值与提交值不一致 |
| E 校验与数据 | **M7** | 加载 / 错误 / 空态契约 | loading 可误点、错误文案与按钮状态与 T1 不一致 |
| F Angular 变更检测 | **A1** | OnPush 变更检测遗漏 | 手动赋值后未调 `markForCheck()`，视图不更新 |
| F Angular 变更检测 | **T4e** | 对象原地修改 | `obj.field = x` 引用未变，OnPush 不触发检测 |
| F Angular 变更检测 | **M4** | 订阅与定时器清理 | 未用 `takeUntilDestroyed` / `ngOnDestroy`；卸载后仍赋值 |
| F Angular 变更检测 | **A2** | @Input 变化未触发响应 | 对象引用不变时 `ngOnChanges` 不触发；依赖 `ngOnInit` 读 Input |
| D 异步与时序 | **M2** | 时序、防抖与副作用顺序 | 防抖合并、连续点击、慢请求与 M5 叠加 |
| G 列表与协调 | **M6** | 列表 `trackBy` 与行级状态 | 缺 `trackBy` 导致编辑/展开行状态错乱 |

### P2

| 分类 | ID | 名称 | 典型缺口 |
|------|-----|------|----------|
| H 元信息 | **M3** | `[SA]` / `[SB]` 标签口径 | 实现问题误标全 `[SB]`；实现扫描类多应标 `[SA]` |
| I 可选进阶 | **—** | 无障碍（a11y） | 仅测鼠标路径；复杂组件需键盘/焦点/`aria` |
| I 可选进阶 | **—** | CDK Overlay / 嵌套浮层 | 焦点陷阱、事件穿透与文档流差异 |

---

## 二、何时打开本文（信号）

- **多路径**只测一条 → **T2**
- **reset** 只断言组件属性 → **T3**
- **async subscribe / 列表增删**无并发场景 → **T4b**、**M5**
- **多处校验** / `trim` 嫌疑 → **T4c**
- **正则/格式化**无边界 → **T4d**
- **OnPush 组件**手动赋值后视图不更新 → **A1**
- **对象属性直接赋值**不走 `markForCheck` → **T4e**
- **subscribe / interval** 未清理 → **M4**
- **@Input 变化**组件未响应 → **A2**
- **可编辑列表**、展开态在行上 → **M6**
- **受控/展示/提交**不一致 → **M1**
- **Loading/Error** → **M7**（可与 T1 合并思考）
- 模型乱标 **[SB]** → **M3**

---

## 三、按分类详述（类内：P0 → P1 → P2）

### A. 契约与可见行为

#### T1（P0）— UI 承诺与可执行契约

**何时用**：可见属性/文案/注释承诺了行为，某路径可能未落实。

**关键问**：代码是否在**每一条路径**上都落实了 UI 承诺？

**契约信号表**（扫描时对照）：

| 信号 | 示例 | 隐含规则 |
|------|------|----------|
| `[accept]="..."` | `accept=".pdf,.docx"` | 仅这些格式应被接受——**所有路径**（含拖拽）须一致 |
| `[disabled]` | `[disabled]="!isFormValid()"` | 守卫须严密；需考虑各种触发方式下是否仍可绕过 |
| 随状态变化的文案 | `{{ file ? '已选：…' : '拖拽文件' }}` | 所有设置 `file` 的路径须能更新该文案 |
| `required` | `<input required>` | 空字段时须阻止提交 |
| 注释「// 重置表单」 | `this.file = null; this.form.reset()` | **各层状态**都须重置，而非仅组件属性 |

#### M7（P1）— 加载 / 错误 / 空态契约

**何时用**：存在请求、提交、列表为空等三态。

**检查要点**：loading 时提交/删除是否应禁用；错误时文案与可重试路径；空态与 T1 文案是否一致。

---

### B. 交互路径

#### T2（P0）— 同目标多路径对称

**何时用**：同一用户目标有多条路径（鼠标 / 键盘 / 拖拽 / 父组件调用等）。

**路径枚举示例**：

```text
目标：选择文件
  路径 A → <input type="file"> change 事件
  路径 B → 拖拽（drop 事件）

目标：提交表单
  路径 A → 点击提交按钮
  路径 B → 键盘 Enter / 程序化调用（若存在）
```

**关键问**：**所有路径是否走同一套校验与业务逻辑？** 若一路绕过另一路的检查 → 高度可疑缺陷。

---

### C. 状态与重置

#### T3（P0）— 全栈状态与重置

**何时用**：重置、清空、恢复、重开对话框等。

| 层级 | 示例 | 仅靠组件属性赋值能否复位？ |
|------|------|--------------------------|
| 组件属性 | `this.items = []`、`this.form.reset()` | 能 |
| DOM | `<input type="file">.value`、滚动位置 | 否——需 `ViewChild` 手动清 |
| 浏览器 | file input 上传后的值等 | 否 |
| 服务 / 全局 | `BehaviorSubject`、NgRx store | 视实现而定 |

**关键问**：这次「重置」是**上述各层都清干净**，还是只清了组件属性？

---

### D. 异步与时序

#### T4b（P0）— 异步回调与陈旧状态

**何时用**：`async/await` 或 `subscribe` 回调在等待期间读取组件属性再写回。

**红旗代码**（示例）：

```typescript
async deleteItem(id: string) {
  await firstValueFrom(this.service.delete(id));
  // 等待期间 this.items 可能已被其他操作更改，此处读到的是陈旧快照
  this.items = this.items.filter(x => x.id !== id);
}
// 更稳妥：await 后重新从 service 获取列表，或通过 BehaviorSubject 流式更新
```

**关键问**：在等待结束到写回之间，是否可能有**其他操作**已改变同一份数据？若可能 → 陈旧状态候选，通常对应高风险。

#### M5（P0）— 请求竞态与过时响应

**何时用**：同一数据源上连续多次请求。

**关键问**：慢请求后返回是否会**覆盖**更新、正确的结果？是否需 `switchMap`、`takeUntil` 取消前一请求，或「只采纳最新一次」？

#### M2（P1）— 时序、防抖与副作用顺序

**何时用**：搜索防抖、连续点击、依赖 API 返回顺序。

**检查要点**：防抖与 M5 叠加时，**最终展示**是否正确。

---

### E. 校验与数据变换

#### T4c（P0）— 同一字段多处校验一致性

**何时用**：同一字段在「可提交」「上传」「错误高亮」等多处被判断。

**红旗代码**（示例）：

```typescript
// 两处守卫语义不一致：
isFormValid(): this.title.trim() !== ''  // 去空白
upload():      !this.formData.title       // 未 trim
```

**关键问**：对字段 X 的规范化（如 `trim`）是否处处一致？不一致 → 至少一处错误；Risk 2 或 3 可结合**用户是否总能触达**错误路径（可触达性）判断。

#### T4d（P1）— 纯函数、正则与边界输入

**何时用**：内联 `replace`、正则、`split`、日期/数字解析。

**边界示例**（需在脑中过一遍）：

| 变换 | 正常输入 | 建议考虑的边界 |
|------|----------|----------------|
| `name.replace(/\.[^/.]+$/, '')` | `"report.pdf"` | 无扩展名、`".hidden"`、多段点如 `"a.b.c.pdf"` |
| 日期/数字格式 | `"2024-01-01"` | 空串、`null`、非预期格式 |

**关键问**：每种边界下，**返回值是否仍符合调用方预期**？

#### M1（P1）— 控制模式与展示/实际值一致

**何时用**：表单、mask、只读展示。

**检查要点**：**界面显示**与 **`onSubmit` / 回调传出值**是否一致（格式化、trim、单位）。

---

### F. Angular 变更检测与生命周期

#### A1（P1）— OnPush 变更检测遗漏

**何时用**：组件使用 `ChangeDetectionStrategy.OnPush`，但通过 subscribe 或外部调用手动赋值组件属性。

**红旗代码**（示例）：

```typescript
@Component({ changeDetection: ChangeDetectionStrategy.OnPush })
export class MyComponent {
  items: Item[] = [];

  ngOnInit() {
    this.service.items$.subscribe(items => {
      this.items = items; // ⚠️ OnPush 下引用已变，但 CD 不会自动运行
      // 需加：this.cdr.markForCheck();
    });
  }
}
```

**关键问**：subscribe 回调或外部方法赋值后，是否调用了 `markForCheck()` 或 `detectChanges()`？使用 `async pipe` 可自动处理。

#### T4e（P1）— 对象原地修改导致视图不更新

**何时用**：对组件属性、@Input 对象或 Service 返回值直接修改属性，而非替换引用。

**红旗代码**（示例）：

```typescript
updateItem(item: Item) {
  item.name = 'new'; // 对象引用未变
  // OnPush 下不触发检测；Default CD 下可能触发，但依赖脏检查时序
}
// 正确做法：this.items = this.items.map(i => i.id === item.id ? { ...i, name: 'new' } : i);
// 或：item.name = 'new'; this.cdr.markForCheck();
```

**关键问**：是否存在 `obj.field = value` 却未替换引用、也未调 `markForCheck()` 的路径？

#### M4（P1）— 订阅与定时器清理

**何时用**：组件内有 `subscribe`、`setInterval`、`fromEvent` 等异步资源。

**推荐清理方式**（按优先级）：
1. `async pipe` — 自动清理，优先选用
2. `takeUntilDestroyed(this.destroyRef)` — Angular 16+，声明式
3. `ngOnDestroy` + `subscription.unsubscribe()` — 兜底

**检查要点**：组件销毁后是否仍有回调在运行并写入属性（等效于卸载后 setState）？

#### A2（P1）— @Input 变化未触发响应

**何时用**：父组件更新 @Input，子组件未响应（视图未变或逻辑未重跑）。

**两类场景**：

| 场景 | 原因 | 修复 |
|------|------|------|
| 对象引用不变、内容变化 | Angular 比较引用，`ngOnChanges` 不触发 | 父组件传不可变新对象；或子组件用 `DoCheck` |
| 依赖 `ngOnInit` 读 @Input 值 | `ngOnInit` 只运行一次 | 移至 `ngOnChanges`；或用 `input()` 信号 |

**关键问**：子组件的初始化逻辑是否只在 `ngOnInit` 中运行？@Input 后续变化时是否有对应响应路径？

---

### G. 列表与协调

#### M6（P1）— `trackBy` 与行级状态

**何时用**：可编辑行、展开行、行内表单、重排/过滤列表。

**关键问**：`*ngFor` 是否缺少 `trackBy`，导致重排后 DOM 全量重建、行级状态（展开、编辑中）丢失或错位？应使用**稳定 id** 作为 track key。

---

### H. 元信息

#### M3（P2）— `[SA]` / `[SB]` 标签口径

| 标签 | 侧重 |
|------|------|
| **[SB]** | UI/文案/属性**承诺**，以及多路径是否一致、重置是否覆盖各层等**用户可见契约** |
| **[SA]** | 代码**实际行为**；实现扫描（陈旧状态、校验分叉、边界、原地修改、变更检测遗漏）**多数标 [SA]**，勿强行全部写成 `[SB]` |

**风险**：任意 [SB] 规则若代码**未兑现承诺** → 通常对应 Risk 3 优先处理。

---

### I. 可选进阶（P2）

**无障碍（a11y）**：仅测鼠标路径；复杂组件需键盘 / 焦点 / `aria` 验证。

**CDK Overlay / 嵌套浮层**：焦点陷阱、事件穿透、`OverlayContainer` 在测试中的挂载点差异。

---

## 四、规则维度核对（写规则阶段用）

列出 [SA]/[SB] 短句后，按维度扫一眼是否有遗漏（不必每条展开成长文）：

- **输入规则**
- **状态规则**（区分组件属性 / DOM / Service / 全局）
- **交互规则**（每个用户目标下的**全部**路径）
- **副作用**（HTTP 请求、WebSocket、回调）
- **时序**（防抖、异步、竞态）
- **数据一致性**（展示值 vs 实际提交/传出值）

**重点**：SA 与 SB 不一致处，最可能是真实缺陷，须**显式标出**。

---

## 五、[SA]/[SB] 规则短句示例（阶段分析结束前可对照）

一条一行，便于粘贴进提示词或评审：

```text
[SB / T1] accept 声明了格式 → 拖拽路径也必须校验同规则
[SB / T2] 选文件有「点击 + 拖拽」两路 → 须共用同一套校验
[SB / T3] 注释写「重置表单」→ 除组件属性外须清空 <input type="file"> 等 DOM 状态
[SA / T4b] deleteItem 在 await 后仍用 this.items 快照 → 并发删除时可能陈旧
[SA / T4c] isFormValid 对 title 做 trim，upload 未 trim → 空白字符语义不一致
[SA / T4e] updateItem 对 item 原地赋值 → OnPush 下不触发检测，界面陈旧
[SA / A1] subscribe 回调赋值后未调 markForCheck → OnPush 组件视图不更新
[SA / A2] 子组件在 ngOnInit 读 @Input → 父组件后续更新 Input 时逻辑不重跑
```

---

## 六、提交前自检

- [ ] 无重复场景；聚焦「**容易真坏**」，而非「理论上可能」
- [ ] 覆盖维度是否点到：**数据一致性**、**副作用/API**、**时序**、**变更检测**（若有 OnPush）

---

## 七、黄金原则

不要追求**穷举**。

而应：**在每条用例上最大化「发现真实缺陷」的概率**。

---

## 八、Critical Implementation Rules

违反以下规则会导致测试挂起、静默污染或根本不运行，每条用例都必须遵守。

| # | 规则 | 违规后果 |
|---|------|----------|
| **R1** | 运行单文件用 `npx ng run portal:test-tl --include="**/<file>.tl.spec.ts"`，**不用** `npx vitest run` | ATL 环境初始化错误，测试不执行 |
| **R2** | 文件级必须有 `afterEach(() => vi.restoreAllMocks())` | spy 跨 test 污染，报错方向错乱 |
| **R3** | **正向** async 断言用 `waitFor`；**负向**（`not.toHaveBeenCalled`）才允许 `await Promise.resolve()` | 用 `setTimeout` 偶发挂起 5 s |
| **R4** | 共享 mock 的单次覆盖用 `mockReturnValueOnce()`，**禁止**直接属性重赋值 `MOCK.fn = vi.fn(...)` | `beforeEach` 的 `mockClear` 清旧引用，静默污染后续 test |
| **R5** | `vi.spyOn` 不自动屏蔽原实现——若不需要原方法执行，必须加 `.mockImplementation(() => {})` | 原方法内的依赖未 setup → 抛错 |
| **R6** | 组件构造函数直接注入 `HttpClient` 时，`render()` 的 `providers` 必须含 `provideHttpClient()` | 运行时 "No provider for HttpClient" |
| **R7** | HTTP → `.subscribe(cb)` → spy 链**不能用 `waitFor`**，应 stub 私有 HTTP 方法使 subscribe 同步执行 | `waitFor` 永久挂住，不超时失败 |

```typescript
// R3 ✅ 正向用 waitFor
comp.save();
await waitFor(() => expect(ROUTER_MOCK.navigate).toHaveBeenCalled());

// R4 ✅ 单次覆盖
MODEL_MOCK.getModel.mockReturnValueOnce(of(updatedModel)); // ✅
MODEL_MOCK.getModel = vi.fn().mockReturnValue(of(updatedModel)); // ❌

// R7 ✅ stub 私有方法使 subscribe 同步
vi.spyOn(comp as any, "callHttpEndpoint").mockReturnValue(of(response));
comp.submit();
expect(spy).toHaveBeenCalled(); // 同步断言，无需 waitFor
```
