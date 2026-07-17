# Core 模块未打 `@Tag("core")` 测试分析与 CI 接入建议

## 处理进度统计

> 处理方式：对每个 Test 类，定位其对应的生产类，用 `docs/superpowers/prompts/java-unit-test-generation-prompt.md` 的流程分析并按需补充高价值单元测试；完成后（P0/P1/P2）给该 Test 类打 `@Tag("core")`，（P3）保留 `@Tag("slow")` 不打 `core`。每完成一个类，下面「完整类清单」里对应复选框勾选为 `[x]`，本表数字随之更新。**执行时必须遵守下面「执行流程约定」的两阶段关卡，且每次只处理用户指定的类——不允许自行选择下一个要处理的类。**

| 批次 | 总数 | 已完成 | 待处理 | 完成率 |
|------|------|--------|--------|--------|
| P0-a | 15 | 8 | 7 | 53% |
| P0-b | 20 | 0 | 20 | 0% |
| P0-c | 8 | 0 | 8 | 0% |
| P1-a | 7 | 0 | 7 | 0% |
| P1-b | 5 | 0 | 5 | 0% |
| P1-c | 3 | 0 | 3 | 0% |
| P1-d | 3 | 0 | 3 | 0% |
| P2-a | 8 | 0 | 8 | 0% |
| P2-b | 7 | 0 | 7 | 0% |
| P2-c | 9 | 0 | 9 | 0% |
| P2-d | 5 | 0 | 5 | 0% |
| P2-e | 12 | 0 | 12 | 0% |
| P3 | 2 | 0 | 2 | 0% |
| **合计** | **104** | **8** | **96** | **8%** |

---

## 执行流程约定（两阶段强制关卡）

> 背景：批量处理这 104 个类时，容易退化成"直接在原 Test 文件上打补丁 + 加 `@Tag("core")`"，跳过 `java-unit-test-generation-prompt.md` 的 Step 0-2 分析。已完成的 5 个 P0-a 类（`AbstractConditionTest`/`ColumnSelectionTest`/`ConditionItemTest`/`ConditionTest`/`DrillPathTest`）核对过 diff，确认是真正走了分析流程（deferred 块、risk 取舍依据都在），以此为基准，后续每个类都必须留下同等的可核查痕迹。

**类的选择由用户决定，不由处理方进程自行推进。** 每次只针对用户明确指定的一个（或一批）类执行下面两个阶段；未收到指定前不要擅自挑选下一个类开始。

### 阶段 1：仅分析，不改代码

- 按 `java-unit-test-generation-prompt.md` 的 Step 0（Scope）→ Step 1（Method Map，含 no-op/logic/tier 判断）→ Step 2（Risk 表）执行。
- 本阶段**不允许修改任何 Test 文件**，只输出：
  - Scope block（Input/Classes/Existing tests/Related files read）
  - Method Map 表
  - 若命中 Step 1 的"≥2 tier 且每 tier ≥3 个 logic method"条件，输出 split plan 并停下
  - 每个 logic method 的 Risk 表（含 Suspect/Bypass/Basic 等标注）
- 输出后停下，等待用户确认（确认范围、确认是否要补的 Suspect/Deferred 是否合理），不得自动进入阶段 2。

### 阶段 2：确认后再生成代码

- 只能在用户对阶段 1 输出明确表示确认后开始。
- 按 Step 3 生成/修改测试代码，必须包含：
  - 有内容才写的 `Intent vs implementation suspects` 块
  - 有内容才写的 `Cases deferred` 块
  - 私有逻辑测试前的 `// via: publicFoo() -> privateBar()` 注释
  - 确认过的 Risk 表中每个测试点都能在代码里对应到一个 `@Test`/`@Disabled`/deferred 说明，三者之一
- 生成完成后打 `@Tag("core")`（或 P3 类维持 `@Tag("slow")`），再把「完整类清单」里对应复选框改成 `[x]`。

### 勾选复选框的前提条件

打勾前必须同时满足：

1. diff 里能看到阶段 1 产出对应的分析痕迹（Suspect/Deferred 块，或明确"无 suspect/deferred，故省略"的说明）——不能只有 `@Tag("core")` 和几个新增/挪动的 `@Test`。
2. 阶段 1 的输出已经过用户确认，不是处理方自行决定跳过确认直接生成代码。

如果某个类在阶段 1 分析后发现没有 Suspect、没有需要 defer 的用例（纯粹查漏补缺），也需要在生成代码时用一句话说明"分析后未发现 suspect/deferred 项"，而不是干脆不留痕迹。

---

## 背景

`community/core` 模块共有约 **522** 个测试类，其中：

| 指标 | 数量 |
|------|------|
| 测试类总数 | 522 |
| 已打 `@Tag("core")` | 418 |
| **未打 `@Tag("core")`** | **104** |
| 未打标签类中的 `@Test` 约 | ~2436 |

`community/core/pom.xml`（第 996 行）中 Surefire 配置为：

```xml
<groups>core</groups>
```

因此 **`mvn test -pl core` 默认只跑带 `@Tag("core")` 的测试**。未打该标签的测试类写了但不会进入默认 CI。已用 `grep -rl '@Test' | xargs grep -L '@Tag("core")'` 对 104 类清单逐一核对，全部命中，方法论可信。

未打标签类中仅有 2 个带其它标签：`@Tag("slow")`（LDAP / DB 认证，见 P3）。其余几乎都是无 Spring 的纯单元测试；少数 web/composer 测试使用了 `@SreeHome`。

---

## 未打标签测试按主题分类

### 1. UQL 核心模型（25 类，~723 测例）

条件、变量、列选择、drill、格式信息；asset 侧 Date/Ranking/Sort；ERM（AutoAlias、HiddenColumns）；JDBC 字段/条件列表；schema `XValueNode`；viewsheet（Bookmark、CalendarUtil、StandardPeriods、Tab 布局/z-index 等）。

代表类：`ConditionTest`、`VariableTableTest`、`DateConditionTest`、`CalendarUtilTest`

主要路径：

- `inetsoft/uql/AbstractConditionTest.java`
- `inetsoft/uql/ColumnSelectionTest.java`
- `inetsoft/uql/ConditionItemTest.java`
- `inetsoft/uql/ConditionTest.java`
- `inetsoft/uql/DrillPathTest.java`
- `inetsoft/uql/VariableTableTest.java`
- `inetsoft/uql/XConditionGroupTest.java`
- `inetsoft/uql/XFormatInfoTest.java`
- `inetsoft/uql/asset/AssetConditionTest.java`
- `inetsoft/uql/asset/DateConditionTest.java`
- `inetsoft/uql/asset/RankingConditionTest.java`
- `inetsoft/uql/asset/SortInfoTest.java`
- `inetsoft/uql/asset/sync/TaskAssetDependencyTransformerTest.java`
- `inetsoft/uql/erm/AutoAliasTest.java`
- `inetsoft/uql/erm/HiddenColumnsTest.java`
- `inetsoft/uql/jdbc/XFieldTest.java`
- `inetsoft/uql/jdbc/util/ConditionListHandlerTest.java`
- `inetsoft/uql/schema/XValueNodeTest.java`
- `inetsoft/uql/viewsheet/CellRefTest.java`
- `inetsoft/uql/viewsheet/VSBookmarkTest.java`
- `inetsoft/uql/viewsheet/internal/CalendarUtilTest.java`
- `inetsoft/uql/viewsheet/internal/LabelInfoTest.java`
- `inetsoft/uql/viewsheet/internal/StandardPeriodsTest.java`
- `inetsoft/uql/viewsheet/internal/TabVSAssemblyInfoTest.java`
- `inetsoft/uql/viewsheet/internal/VSUtilZIndexTest.java`

### 2. 聚合 Formula（20 类，~610 测例）

`report.filter` 下一整套：Sum/Avg/Count/Min/Max/Variance/StdDev/Nth/Percentile/First/Last/Concat/CubeMeasure/CalcField 等公式的 `addValue` / `isNull` / `getResult`。

- `inetsoft/report/filter/AverageFormulaTest.java`
- `inetsoft/report/filter/CalcFieldFormulaTest.java`
- `inetsoft/report/filter/ConcatFormulaTest.java`
- `inetsoft/report/filter/CountFormulaTest.java`
- `inetsoft/report/filter/CubeMeasureFormulaTest.java`
- `inetsoft/report/filter/DefaultFormulaTest.java`
- `inetsoft/report/filter/FirstFormulaTest.java`
- `inetsoft/report/filter/LastFormulaTest.java`
- `inetsoft/report/filter/MaxFormulaTest.java`
- `inetsoft/report/filter/MinFormulaTest.java`
- `inetsoft/report/filter/NoneFormulaTest.java`
- `inetsoft/report/filter/NthLargestFormulaTest.java`
- `inetsoft/report/filter/NthSmallestFormulaTest.java`
- `inetsoft/report/filter/NullFormulaTest.java`
- `inetsoft/report/filter/PopulationVarianceFormulaTest.java`
- `inetsoft/report/filter/ProductFormulaTest.java`
- `inetsoft/report/filter/PthPercentileFormulaTest.java`
- `inetsoft/report/filter/StandardDeviationFormulaTest.java`
- `inetsoft/report/filter/SumFormulaTest.java`
- `inetsoft/report/filter/VarianceFormulaTest.java`

### 3. Chart/Graph 引擎（15 类，~222 测例）

DataSet（Box、Index）、刻度（Linear/Log/Stack）、趋势线方程（Linear/Poly/Exp/Log/Power）、比较器、Contour、柱状/区间堆叠与圆角等。

- `inetsoft/graph/data/BoxDataSetTest.java`
- `inetsoft/graph/data/DataSetIndexTest.java`
- `inetsoft/graph/element/IntervalElementStackOutermostTest.java`
- `inetsoft/graph/guide/form/ExponentialLineEquationTest.java`
- `inetsoft/graph/guide/form/LogarithmicLineEquationTest.java`
- `inetsoft/graph/guide/form/PolynomialLineEquationTest.java`
- `inetsoft/graph/guide/form/PowerLineEquationTest.java`
- `inetsoft/graph/internal/ContourCellTest.java`
- `inetsoft/graph/internal/FirstDayComparatorTest.java`
- `inetsoft/graph/internal/ManualOrderComparerTest.java`
- `inetsoft/graph/internal/MixedComparatorTest.java`
- `inetsoft/graph/scale/LinearRangeTest.java`
- `inetsoft/graph/scale/LogScaleTest.java`
- `inetsoft/graph/scale/StackRangeTest.java`
- `inetsoft/graph/visual/BarVOStackRoundingTest.java`

### 4. 工具类（9 类，~197 测例）

`DataComparer`、`DefaultComparator`、`DurationFormat`、`RoundDecimalFormat`、`SparseBitSet`、`TimeZoneUtil`、文档 URL、`BidiMap`、`UndirectedGraph`。

- `inetsoft/util/DataComparerTest.java`
- `inetsoft/util/DefaultComparatorTest.java`
- `inetsoft/util/DurationFormatTest.java`
- `inetsoft/util/InetsoftUserDocumentationTest.java`
- `inetsoft/util/RoundDecimalFormatTest.java`
- `inetsoft/util/SparseBitSetTest.java`
- `inetsoft/util/TimeZoneUtilTest.java`
- `inetsoft/util/algo/BidiMapTest.java`
- `inetsoft/util/algo/UndirectedGraphTest.java`

### 5. UQL path 类型格式化（7 类，~112 测例）

Boolean/Byte/Short/Int/Long/Float/Double 的 format 行为。

- `inetsoft/uql/path/BooleanFormatTest.java`
- `inetsoft/uql/path/ByteFormatTest.java`
- `inetsoft/uql/path/DoubleFormatTest.java`
- `inetsoft/uql/path/FloatFormatTest.java`
- `inetsoft/uql/path/IntegerFormatTest.java`
- `inetsoft/uql/path/LongFormatTest.java`
- `inetsoft/uql/path/ShortFormatTest.java`

### 6. Report 几何 / PDF（4 类，~108 测例）

- `inetsoft/report/MatrixOperationTest.java`
- `inetsoft/report/PDFPrinterRoundRectTest.java`
- `inetsoft/report/PositionTest.java`
- `inetsoft/report/SizeTest.java`

### 7. Table filter 辅助（3 类，~127 测例）

比较器 / SortOrder（非 formula）。

- `inetsoft/report/filter/DefaultComparerTest.java`
- `inetsoft/report/filter/SortOrderTest.java`
- `inetsoft/report/filter/TextComparerTest.java`

### 8. Sree 安全 / 同步 / 内部（6 类，~111 测例）

`IdentityID`、拓扑排序图、部署/依赖查找；以及 `@Tag("slow")` 的 LDAP、DB AuthenticationProvider（需要 Spring 上下文）。

- `inetsoft/sree/internal/DeployEmbeddedTableImportTest.java`
- `inetsoft/sree/internal/TopologicalSortGraphTest.java`
- `inetsoft/sree/internal/sync/QueryDependenciesFinderTest.java`
- `inetsoft/sree/security/IdentityIDTest.java`
- `inetsoft/sree/security/db/DatabaseAuthenticationProviderTests.java` — `@Tag("slow")`
- `inetsoft/sree/security/ldap/GenericLdapAuthenticationProviderTest.java` — `@Tag("slow")`

### 9. Web / Composer（5 类，~26 测例）

偏局部逻辑：bottom tabs 布局、分组、日历/SelectionList 属性、图表 Plot Options 可见性——不是全套控制器集成测。

- `inetsoft/web/composer/vs/controller/VSLayoutServiceTest.java`
- `inetsoft/web/composer/vs/dialog/CalendarPropertyDialogServiceTest.java`
- `inetsoft/web/composer/vs/dialog/SelectionListPropertyDialogServiceTest.java`
- `inetsoft/web/composer/vs/objects/controller/GroupingServiceTest.java`
- `inetsoft/web/graph/model/dialog/ChartPlotOptionsPaneModelTest.java`

### 10. 其余

| 子主题 | 类 |
|--------|-----|
| Analytic CSS | `CSSUtilTest`、`VSCSSUtilTest` |
| MV | `BitSetTest`、`CompactStringTest`、`MVRuleTest` |
| Setup / Storage | `PropertiesServiceTest`、`JsonXmlTranscoderTest` |
| Report 其它 | `GraphTypeUtilCheckTypeTest`、`BoundsTest`、`VSTableDataHelperTest` |

---

## 特征归纳

1. **几乎全是“新风格”纯单元测**：直接 `new` 被测类 + `@Test`，不依赖旧集成套件。
2. **默认 Maven / CI 不跑它们**；要进 CI 需补上 `@Tag("core")`（或改掉 Surefire 的 `<groups>core</groups>`）。
3. **明显遗漏打标的候选**：bottomTabs 相关 web 测试、大部分 formula/UQL/graph 单元测——内容都是 core 能力，更像漏标，而非故意排除。抽样核对了 5 个跨类别代表类的 git 历史，最近改动集中在 2026-05，说明这批是较新写的测试忘了打标，不是陈年遗留代码。
4. **故意不入默认套件的更可能只有**：`@Tag("slow")` 的 LDAP/DB auth 测试——这类是有意不跑在默认 `core` job 里，不要一起打包补 `@Tag("core")`。
5. **surefire 并发风险低于预期**：`core/pom.xml` 未配置 `forkCount`/`parallel`，默认单 fork 顺序执行；且已存在 `SreeHomeExtension`（`junit.jupiter.extensions.autodetection.include`）自动探测机制并指向共享 `${project.build.directory}/test-workdir`。这意味着 P1-b 里几个用 `@SreeHome` 的 Composer 测试，接入现有 CI 基础设施的准备已经就绪，实际 flaky 风险比"偏局部逻辑但要小心"更低。

---

## CI 接入优先级建议

按「进 CI 价值 / 风险 / 跑通成本」排序。默认 Surefire 已是 `<groups>core</groups>`，所以**建议打 `@Tag("core")` 才能进现有 CI**；`@Tag("slow")` 应进单独 job，不要和默认 `core` 混跑。

### P0 — 立刻补 `@Tag("core")`，进默认 CI

覆盖面最大、逻辑最核心，且几乎全是纯单元测，误伤风险最低。

| 批次 | 范围 | 规模 | 理由 |
|------|------|------|------|
| P0-a | **UQL 条件/变量/列选/asset 条件** | ~15 类（`Condition*`、`VariableTable`、`ColumnSelection`、`Asset/Date/RankingCondition`、`SortInfo`、`XConditionGroup`、`XFormatInfo`、`DrillPath`、`AbstractCondition`、`XValueNode`、`ConditionListHandler`、`XField`） | 筛选/绑定/参数核心路径；漏跑等于 CI 看不见条件引擎回归 |
| P0-b | **聚合 Formula 全家桶** | 20 类（`report/filter/*FormulaTest`） | ~610 测例；表格/交叉表/MV 汇总共用，ROI 最高 |
| P0-c | **Chart/Graph 刻度·数据集·堆叠** | ~8 类优先（`LinearRange`/`LogScale`/`StackRange`/`DataSetIndex`/`BoxDataSet`/`BarVOStackRounding`/`IntervalElementStackOutermost`/`GraphTypeUtilCheckType`） | 图表渲染核心；堆叠/刻度 bug 多发 |

**CI 动作：** 打上 `@Tag("core")` 即可，无需改 Surefire groups。建议先加再本地跑对应包确认无 flaky。

#### P0 完整类清单

**P0-a — UQL 条件/变量/列选/asset 条件**

- [x] `inetsoft/uql/AbstractConditionTest.java`
- [x] `inetsoft/uql/ColumnSelectionTest.java`
- [x] `inetsoft/uql/ConditionItemTest.java`
- [x] `inetsoft/uql/ConditionTest.java`
- [x] `inetsoft/uql/DrillPathTest.java`
- [x] `inetsoft/uql/VariableTableTest.java`
- [x] `inetsoft/uql/XConditionGroupTest.java`
- [x] `inetsoft/uql/XFormatInfoTest.java`
- [ ] `inetsoft/uql/asset/AssetConditionTest.java`
- [ ] `inetsoft/uql/asset/DateConditionTest.java`
- [ ] `inetsoft/uql/asset/RankingConditionTest.java`
- [ ] `inetsoft/uql/asset/SortInfoTest.java`
- [ ] `inetsoft/uql/schema/XValueNodeTest.java`
- [ ] `inetsoft/uql/jdbc/XFieldTest.java`
- [ ] `inetsoft/uql/jdbc/util/ConditionListHandlerTest.java`

**P0-b — 聚合 Formula**

- [ ] `inetsoft/report/filter/AverageFormulaTest.java`
- [ ] `inetsoft/report/filter/CalcFieldFormulaTest.java`
- [ ] `inetsoft/report/filter/ConcatFormulaTest.java`
- [ ] `inetsoft/report/filter/CountFormulaTest.java`
- [ ] `inetsoft/report/filter/CubeMeasureFormulaTest.java`
- [ ] `inetsoft/report/filter/DefaultFormulaTest.java`
- [ ] `inetsoft/report/filter/FirstFormulaTest.java`
- [ ] `inetsoft/report/filter/LastFormulaTest.java`
- [ ] `inetsoft/report/filter/MaxFormulaTest.java`
- [ ] `inetsoft/report/filter/MinFormulaTest.java`
- [ ] `inetsoft/report/filter/NoneFormulaTest.java`
- [ ] `inetsoft/report/filter/NthLargestFormulaTest.java`
- [ ] `inetsoft/report/filter/NthSmallestFormulaTest.java`
- [ ] `inetsoft/report/filter/NullFormulaTest.java`
- [ ] `inetsoft/report/filter/PopulationVarianceFormulaTest.java`
- [ ] `inetsoft/report/filter/ProductFormulaTest.java`
- [ ] `inetsoft/report/filter/PthPercentileFormulaTest.java`
- [ ] `inetsoft/report/filter/StandardDeviationFormulaTest.java`
- [ ] `inetsoft/report/filter/SumFormulaTest.java`
- [ ] `inetsoft/report/filter/VarianceFormulaTest.java`

**P0-c — Chart/Graph 刻度·数据集·堆叠**

- [ ] `inetsoft/graph/scale/LinearRangeTest.java`
- [ ] `inetsoft/graph/scale/LogScaleTest.java`
- [ ] `inetsoft/graph/scale/StackRangeTest.java`
- [ ] `inetsoft/graph/data/DataSetIndexTest.java`
- [ ] `inetsoft/graph/data/BoxDataSetTest.java`
- [ ] `inetsoft/graph/visual/BarVOStackRoundingTest.java`
- [ ] `inetsoft/graph/element/IntervalElementStackOutermostTest.java`
- [ ] `inetsoft/report/composition/graph/GraphTypeUtilCheckTypeTest.java`

---

### P1 — 尽快补 `@Tag("core")`，跟进默认 CI

偏“近期易回归 / 用户可见行为”，规模小、好验证。

| 批次 | 范围 | 规模 | 理由 |
|------|------|------|------|
| P1-a | **Viewsheet 布局/日历/书签/z-index** | `TabVSAssemblyInfo`、`VSUtilZIndex`、`CalendarUtil`、`LabelInfo`、`StandardPeriods`、`VSBookmark`、`CellRef` | 日历/DC 周期/Tab 布局易回归；与产品近期改动高度相关 |
| P1-b | **Composer bottom-tabs 相关** | `VSLayoutService`、`GroupingService`、`CalendarPropertyDialogService`、`SelectionListPropertyDialogService` | 仅 ~13 测例；`@SreeHome` 但测点集中，进 CI 成本低、防布局回退价值高 |
| P1-c | **ERM / 依赖变换** | `AutoAlias`、`HiddenColumns`、`TaskAssetDependencyTransformer` | 安全和部署依赖路径，漏测代价高 |
| P1-d | **Filter 比较器 / SortOrder** | `DefaultComparer`、`TextComparer`、`SortOrder` | 排序/关联显示基础件，紧挨 Formula 一批加即可 |

#### P1 完整类清单

**P1-a**

- [ ] `inetsoft/uql/viewsheet/internal/TabVSAssemblyInfoTest.java`
- [ ] `inetsoft/uql/viewsheet/internal/VSUtilZIndexTest.java`
- [ ] `inetsoft/uql/viewsheet/internal/CalendarUtilTest.java`
- [ ] `inetsoft/uql/viewsheet/internal/LabelInfoTest.java`
- [ ] `inetsoft/uql/viewsheet/internal/StandardPeriodsTest.java`
- [ ] `inetsoft/uql/viewsheet/VSBookmarkTest.java`
- [ ] `inetsoft/uql/viewsheet/CellRefTest.java`

**P1-b**

- [ ] `inetsoft/web/composer/vs/controller/VSLayoutServiceTest.java`
- [ ] `inetsoft/web/composer/vs/objects/controller/GroupingServiceTest.java`
- [ ] `inetsoft/web/composer/vs/dialog/CalendarPropertyDialogServiceTest.java`
- [ ] `inetsoft/web/composer/vs/dialog/SelectionListPropertyDialogServiceTest.java`
- [ ] （可选同批）`inetsoft/web/graph/model/dialog/ChartPlotOptionsPaneModelTest.java`

**P1-c**

- [ ] `inetsoft/uql/erm/AutoAliasTest.java`
- [ ] `inetsoft/uql/erm/HiddenColumnsTest.java`
- [ ] `inetsoft/uql/asset/sync/TaskAssetDependencyTransformerTest.java`

**P1-d**

- [ ] `inetsoft/report/filter/DefaultComparerTest.java`
- [ ] `inetsoft/report/filter/TextComparerTest.java`
- [ ] `inetsoft/report/filter/SortOrderTest.java`

---

### P2 — 应打 `@Tag("core")`，可排进同一 PR 或下周迭代

价值明确，但相对“基础设施/辅助”，紧急度低于 P0/P1。

| 批次 | 范围 | 规模 | 理由 |
|------|------|------|------|
| P2-a | **Graph 辅助**（趋势线方程、比较器、Contour） | `*LineEquation`、`ManualOrderComparer`、`MixedComparator`、`FirstDayComparator`、`ContourCell` | 纯数学/比较逻辑，稳定；补全 graph 覆盖 |
| P2-b | **UQL path Format 七件套** | Boolean/Byte/…/Double Format | ~112 测例；偏格式边界，适合批量加 tag |
| P2-c | **util** | `DataComparer`、`DefaultComparator`、格式化、`SparseBitSet`、`TimeZoneUtil`、`BidiMap`、`UndirectedGraph`、文档 URL | 通用基础；`TimeZoneUtil`/`DurationFormat` 注意时区/locale（当前 Surefire 已固定 en-US + NY） |
| P2-d | **Report 几何/PDF/Bounds** | `Position`/`Size`/`MatrixOperation`/`PDFPrinterRoundRect`/`Bounds` | 低风险单元测 |
| P2-e | **Analytic CSS / MV / Setup / Storage / 其它** | `CSSUtil`/`VSCSSUtil`、`BitSet`/`CompactString`/`MVRule`、`PropertiesService`/`JsonXmlTranscoder`、`VSTableDataHelper`、`IdentityID`、拓扑图/QueryDependencies/DeployEmbedded | 可一批打标；若偶发依赖环境再单拆 |

**CI 动作：** 仍打 `@Tag("core")`；若一次 PR 过大会动到 Surefire 时间预算，可按 P2-a→e 拆 2～3 个 PR。

#### P2 完整类清单

**P2-a — Graph 辅助**

- [ ] `inetsoft/graph/guide/form/ExponentialLineEquationTest.java`
- [ ] `inetsoft/graph/guide/form/LogarithmicLineEquationTest.java`
- [ ] `inetsoft/graph/guide/form/PolynomialLineEquationTest.java`
- [ ] `inetsoft/graph/guide/form/PowerLineEquationTest.java`
- [ ] `inetsoft/graph/internal/ManualOrderComparerTest.java`
- [ ] `inetsoft/graph/internal/MixedComparatorTest.java`
- [ ] `inetsoft/graph/internal/FirstDayComparatorTest.java`
- [ ] `inetsoft/graph/internal/ContourCellTest.java`

**P2-b — UQL path Format**

- [ ] `inetsoft/uql/path/BooleanFormatTest.java`
- [ ] `inetsoft/uql/path/ByteFormatTest.java`
- [ ] `inetsoft/uql/path/DoubleFormatTest.java`
- [ ] `inetsoft/uql/path/FloatFormatTest.java`
- [ ] `inetsoft/uql/path/IntegerFormatTest.java`
- [ ] `inetsoft/uql/path/LongFormatTest.java`
- [ ] `inetsoft/uql/path/ShortFormatTest.java`

**P2-c — util**

- [ ] `inetsoft/util/DataComparerTest.java`
- [ ] `inetsoft/util/DefaultComparatorTest.java`
- [ ] `inetsoft/util/DurationFormatTest.java`
- [ ] `inetsoft/util/InetsoftUserDocumentationTest.java`
- [ ] `inetsoft/util/RoundDecimalFormatTest.java`
- [ ] `inetsoft/util/SparseBitSetTest.java`
- [ ] `inetsoft/util/TimeZoneUtilTest.java`
- [ ] `inetsoft/util/algo/BidiMapTest.java`
- [ ] `inetsoft/util/algo/UndirectedGraphTest.java`

**P2-d — Report 几何/PDF/Bounds**

- [ ] `inetsoft/report/PositionTest.java`
- [ ] `inetsoft/report/SizeTest.java`
- [ ] `inetsoft/report/MatrixOperationTest.java`
- [ ] `inetsoft/report/PDFPrinterRoundRectTest.java`
- [ ] `inetsoft/report/internal/BoundsTest.java`

**P2-e — 其余**

- [ ] `inetsoft/analytic/composition/CSSUtilTest.java`
- [ ] `inetsoft/analytic/composition/VSCSSUtilTest.java`
- [ ] `inetsoft/mv/data/BitSetTest.java`
- [ ] `inetsoft/mv/data/CompactStringTest.java`
- [ ] `inetsoft/mv/util/MVRuleTest.java`
- [ ] `inetsoft/setup/PropertiesServiceTest.java`
- [ ] `inetsoft/storage/JsonXmlTranscoderTest.java`
- [ ] `inetsoft/report/io/viewsheet/VSTableDataHelperTest.java`
- [ ] `inetsoft/sree/security/IdentityIDTest.java`
- [ ] `inetsoft/sree/internal/TopologicalSortGraphTest.java`
- [ ] `inetsoft/sree/internal/sync/QueryDependenciesFinderTest.java`
- [ ] `inetsoft/sree/internal/DeployEmbeddedTableImportTest.java`

---

### P3 — 不要简单打 `core` 塞进默认 CI

| 类 | 现有标签 | 建议 | 状态 |
|----|----------|------|------|
| `GenericLdapAuthenticationProviderTest` | `@Tag("slow")` | 新 CI job：`groups=slow` 或夜间/手动；默认 job 继续只跑 `core` | 待处理 |
| `DatabaseAuthenticationProviderTests` | `@Tag("slow")` | 同上；需 Spring + 外部/嵌入式 DB，易拖慢主干 | 待处理 |

可选后续：在这些类上改成 `@Tag("core")` + `@Tag("slow")`，并改 Surefire 默认 `excludedGroups=slow`（像 server/connectors），这样“语义是 core，但主干排除慢测”。

**注意：** 当前 core 模块是 **include `core`**，不是 exclude `slow`，所以只打 `slow` 不会进默认 CI（现状合理）。

---

## 建议落地顺序

1. **本周：** P0-a + P0-b + P0-c（条件引擎 + Formula + 图表刻度/数据集，均是最低风险的纯单元测）→ 观察 CI 时长涨幅
2. **紧接着：** P1 全家（布局/日历/书签 + Composer bottom-tabs + ERM + 比较器/SortOrder）→ 用户可见行为回归
3. **下一迭代：** P2 批量补 tag
4. **单开：** P3 `slow` job（夜间即可），不要混进 PR 必跑

（原文把 P0-c 和 P1 混在同一步骤，但批次表里 P0-c 明确属于 P0——都是低风险纯单元测，应该跟 P0-a/b 一起最先落地，不需要等到和 Composer 测试一起验证。）

粗估：

| 优先级 | 约略规模 |
|--------|----------|
| P0 + P1 | ~70+ 类 / ~1800+ 测例 |
| P2 | ~30 类 |
| P3 | 2 类进独立流水线 |

---

## 补充考虑

以下几点原文未覆盖，建议在实际执行前补上：

1. **CI 时长预算需要实测，不能只看"本地跑通"**。`community/.github/workflows/pr-build.yml` 的验证构建 job 是 `timeout-minutes: 45`，跑的是 `./mvnw clean install`（全 monorepo，不只是 core 测试），默认走的正是这份文档要改的 `<groups>core</groups>` 过滤器。P0-a+P0-b+P0-c 一次性会新增约 1500+ 测例，建议合入后立刻看一次实际 CI 总时长，确认离 45 分钟上限还有多少余量，再决定 P1/P2 能否继续按整批合并，还是要拆更小的 PR。

2. **打标后要有一个"观察窗口 + 回退方式"，不能假设一次性零 flaky**。尤其是 P0-b 聚合 Formula 这批（610 测例，单批最大、历史上易有可变对象复用导致的状态污染问题）：建议打标后连续观察若干次 CI 构建，出现偶发失败时优先排查 `@BeforeEach`/静态字段复用，而不是直接把标签摘掉了事；每批打标的 PR 里应注明"如失败排查方向"，方便非作者的人接手。

3. **PR 提交范围：这批改动全部在 `community/core`（子模块）里，只需要在 community 仓库开 PR，不涉及 enterprise 仓库**——根 `CLAUDE.md` 的 PR 规则里已经写明"改动只在 community/ → 只在 community 仓库开 PR"，执行时按此执行即可，不用额外在 enterprise 侧补 PR 或提交子模块指针更新（除非 enterprise 侧有依赖这批测试的场景，目前没有迹象）。

4. **只加 `@Tag`，不改 Surefire 并发配置，风险确实可控**：`core/pom.xml` 目前没有配置 `forkCount`/`parallel`（默认单 fork 顺序执行），所以新增测试不会引入"并行执行下的资源竞争/静态状态污染"这类额外风险维度，原方案"先加再本地跑确认无 flaky"这个验证步骤是够用的，不需要额外做并发压测。

5. **`inetsoft.uql.Condition`（以及任何走同一构造路径的类）无法单独用 `-Dtest=ClassName` 在本地验证**：`Condition()` 构造函数调用 `Tool.isCaseSensitive()` → `SreeEnv.getProperty()` → `ConfigurationContext.getSpringBean()`，要求 `ConfigurationContext` 持有一个可用的 Spring `ApplicationContext`。`SreeHomeExtension`（全局自动探测）本身不引导真正的 Spring 上下文——它只是把 `SpringExtension.getApplicationContext(context)` 的结果（无 `@ExtendWith(SpringExtension.class)` 时为空/不可用）写入 `ConfigurationContext`，且在每个测试类的 `afterAll` 里会把它重置为 `null`。因此任何单独或组合运行 `-Dtest=ConditionTest`（不管是否搭配一个真正 `@SpringBootTest`/`@ExtendWith(SpringExtension.class)` 的类）都会在 `new Condition()` 处抛出 `ShutdownException: Spring application context is not available`——这在改动前的原始 `ConditionTest.java` 上同样会复现，属于本地单类验证的固有限制，不是新增测试引入的缺陷。已确认 `DrillPathTest` 不受影响（`DrillPath` 构造不经过 `SreeEnv`）。后续处理 `VariableTableTest`、`XConditionGroupTest`、`AssetConditionTest`、`DateConditionTest`、`RankingConditionTest`、`SortInfoTest` 等同样会 `new Condition`/`new AbstractCondition` 子类的用例时，预期会遇到同样的本地验证限制，需要放到完整 `mvn test -pl core`（或至少完整 `core` reactor）里跑才能验证通过，不要用单类 `-Dtest` 误判为回归。

**已确认这个限制不止 `Condition`一类**：处理 `XConditionGroupTest`/`XFormatInfoTest` 时发现同一根因（`ConfigurationContext` 没有可用的 Spring `ApplicationContext`）还会通过 `TableFormat.getFormat(...)`（`XFormatInfo.toString()` 内部调用，触发 `TableFormat.<clinit>` → `PropertiesEngine.getInstance()`）命中——而且这是**原本就存在**的限制：`XFormatInfoTest` 改动前的 `toString_*` 系列用例已经会走到这条路径，不是这次补测试引入的新问题。用 `-Dtest=XConditionGroupTest,XFormatInfoTest` 单独跑验证过：所有失败都归到同一个 `ShutdownException`/`NoClassDefFoundError: TableFormat` 根因，没有出现其它独立的逻辑错误。`copyParameters(XPrincipal)` 的新增用例改用 `Mockito.mock(XPrincipal.class)`（绕开 `new XPrincipal(...)` 内部的 `XSessionService.getService()` → 同一个 Spring 依赖），因此保持了真正的 `[unit]` tier，可以单类本地验证。

## Tag 写法约定

```java
@Tag("core")                    // 进默认 CI
@Tag("slow")                    // 仅慢 job（现状）
@Tag("core") @Tag("slow")       // 仅当同时改成 excludedGroups=slow 时再用
```

示例：

```java
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("core")
public class ConditionTest {
   @Test
   void equalToString_matches() { /* ... */ }
}
```

---

## 相关配置位置

- Surefire groups：`community/core/pom.xml`（`<groups>core</groups>`）
- 测试根目录：`community/core/src/test/java/`
