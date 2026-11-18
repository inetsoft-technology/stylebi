# Chart Defaults S7 — Creation-Time Defaults Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** On a marked chart:
- a new date on X or Y groups by month
- picking Area or Step Area gives the stacked type
- pie slices run largest first
- a waterfall keeps its data order
- the treemap family is coloured by its top level, and a marimekko by its Break By
- a candle's colour dimension becomes panels, and its Color slot refuses a dimension

These defaults apply whether the author picks the style first or drops the fields first. Classic charts, scripts, Modernize and Revert are unchanged.

**Architecture:** One rule class, `ChartCreationDefaults`, holds the seven rules. Each rule is gated on the chart's `VizContext`.
- Four entry points call it: the binding editor's type change (`ChangeChartTypeService`), its drop (`VSChartBindingHandler`), the wizard (`VSWizardBindingHandler`, `ChartRecommenderUtil`) and the reset copy (`VizModernizeUtil.resetToModern`, called by `ModernCopyService`).
- The waterfall order reads the category's values in source order through a small reader, `ChartSourceOrder`. It scans the chart's worksheet table and saves the values as a manual order.
- The client learns the mark through a new `vizModern` flag on the chart binding model, so it can refuse the candle Color drop.
- Every change is ordinary binding (kind C). Nothing new is stored, and no XML attribute is added.

**Tech Stack:** Java 21, JUnit 5, Mockito, Spring test context (`@SreeHome`), Maven module `core`. Angular 21 / Vitest for the drop check (Task 8).

**Spec:** `docs/superpowers/specs/lookfeel/2026-11-06-chart-defaults-design.md`, amended for S7 in `524f098374`. The relevant parts are:
- §3 M1, M6 (with D116), M8 and M9
- §4 R12
- §5 S7: the shaping facts, the entry-point table and rows S7-1 to S7-7
- §8's candle and stock line
- §9's S7 items and §11 "Creation-time defaults (S7)"
- §10 D4, D5, D19 and D116 to D127

**Verified against:** `bug-80455-s7` at `524f098374`, which is `epic-74519` at `fe589c28a9` plus the spec commit. Paths are under `core/src/main/java/inetsoft/` unless they start with `core/` or `web/`.

## Corrections to the spec

| # | Spec | Code at `524f098374` | This plan |
|---|---|---|---|
| C1 | §11: "The candle Color drop is refused in the portal (TL test)" | `VSChartEditorService.isDropPaneAccept` is synchronous logic with no HTTP | A plain `*.spec.ts` (CLAUDE.md: use `*.spec.ts` by default) |
| C2 | S7-1 lists `ChartDndHandler.fixChartRef:796-801` and `BDimensionRefModel:97` | `fixChartRef` re-levels only a field moved between shelves whose level is 0 or less. `BDimensionRefModel` displays year for a field with no level, which draws year | Neither changes. S7-1 is the base level in `VSChartBindingHandler.setDefaultDateLevel:471` and the wizard fallback |
| C3 | §9: "whether a drop on the plot that lands on X or Y counts for S7-1" | A drop on an axis of the chart view carries the X or Y region. A drop on the plot body goes to an aesthetic (`ChartDndHandler.dropToChartView:359-376`, `getAestheticDropType`) | `createChartRef`'s drop type already decides S7-1; no extra case. The candle refusal must also cover the plot-body route, whose aesthetic is resolved by `getAestheticDropType` |
| C4 | §9: "how the wizard's temporary chart's mark is read" | `addChartVSAssembly` already reads `VizContext.of(info)` (for `fixSizeFrame`, `VSWizardBindingHandler:930-932`). The wizard's field creation has `VSTemporaryInfo.getTempChart()` | Rules use those two contexts |
| C5 | Entry-point table: the reset copy calls the rules "under the mark being given" | `VizModernizeUtil` has no sandbox, so it cannot read a waterfall's source | `resetToModern` gains an overload that takes a reader per chart; `ModernCopyService` passes `ChartSourceOrder`. The one-argument form reads nothing |

**Design calls (§9 left these to the builder, or the spec is silent):**
- **Entering a family.** The type-change rules run only when the chart enters the family, as the processor's own `copyTo*` calls do: `!isPie(old) && isPie(new)`, and likewise for the waterfall, the treemap family, the marimekko and the candle. So pie → donut and treemap → sunburst leave the binding alone.
- **Stacked style permission.** If the user has no permission for the stacked style, the plain area they asked for stays.
- **The waterfall read (D127).**
  - **Table:** the chart's worksheet table (`SourceInfo.getSource()`), read in `AssetQuerySandbox.RUNTIME_MODE` from a copy of it in a `WorksheetWrapper`. The worksheet's own conditions and row security still apply, so no value from a hidden row is saved. The copy's pre and post runtime condition lists are cleared, so dashboard selections do not apply and the order does not depend on what a viewer had selected. The copy's `AssetQuery` runs directly, so neither the worksheet nor the sandbox's cached results change.
  - **Limits:** the query is limited to `ChartSourceOrder.MAX_ROWS = 10_000` rows: the copy gets `setMaxRows(MAX_ROWS)`, or keeps the worksheet's own limit when that is smaller. More than `ChartCreationDefaults.MAX_ORDERED_VALUES = 100` distinct values means no order.
  - **No order** for a cube field, a named group or a column that is not in the table. A date is excluded by the rule (D123). A detail calc field is in the table, because the query adds it to the runtime worksheet table, so it is read and ordered like any column (D127, owner ruling after the live checks).
  - **Stored values:** `Tool.getDataString(value)`, with nulls skipped.
- **The wizard's date fallback** is month on a marked chart, whatever the shelf. The wizard has not placed the field when it creates it, and it puts dates on X.
- **The pie measure** is the `getFullName()` of the first aggregate on Y, else on X. With multiple styles, it is each pie aggregate's own name, sorting that aggregate's own slice dimension.
- **Binding Color.** A clone of the dimension goes in a new `VSAestheticRef` with `new CategoricalColorFrame()`, then `GraphUtil.fixVisualFrames(info, ctx)` runs, as `copyToMekko` and `process()` do.
- **A removed pie measure** keeps today's value-sort fallback.

**Known limitations:**
- The waterfall read runs the worksheet table's query once when the rule fires, limited in the query to 10,000 rows. It ignores dashboard selections and keeps the worksheet's conditions and row security.
- A value that first appears after the first 10,000 rows goes after the saved list, ascending.
- An author's explicit Ascending is replaced: on a pie when its measure is rebound (D124), and on a pie or waterfall at a type change or on the reset copy (D122).
- **Older builds** read these as ordinary bindings (M9). Charts marked before S7 change only at a later drop or type change (M8).

## Global Constraints

- **Worktree:** `$WT` is the executor's worktree on branch `bug-80455-s7`, at or after this plan's commit. Never work in `E:/StyleBI/stylebi-enterprise/community` or any other worktree.
- **Other sessions run live checks** on servers built from other worktrees, using the shared Maven repository and the shared asset data. So:
  - Never run `mvnw install`, `mvnw package -pl server`, `spring-boot:run`, or anything else that writes outside `$WT`.
  - Never start a server or bind or call ports 8080, 8081 or 5005.
  - Run only the test classes a step names (Task 9 names packages). The full `core` suite runs only when the controller says so.
- **Git:**
  - Each git command is its own call, `git -C "$WT" ...`, with no `&&` anywhere in the command.
  - One commit per task, subject `Chart defaults S7, <what changed>`, with last line `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
  - Subagents never run `git add`, `git commit` or `git stash` (a hook blocks them); the controller stages and commits after review. Never push.
- **Scope is the mark (M1).** Every rule returns at once unless `ctx.modern`. An unmarked chart's binding and XML are unchanged at every entry point.
- **Scripts are untouched (D126).** Nothing is added to `ChangeChartTypeProcessor.process()` or `ChartVSAssemblyInfo.setChartStyle`.
- **Values:**
  - Month = `DateRangeRef.MONTH_INTERVAL`, year = `YEAR_INTERVAL`, hour = `HOUR_INTERVAL` (time-only fields, `XSchema.TIME`).
  - Axis shelves = `ChartConstants.DROP_REGION_X` (2), `Y` (3), `X2` (4), `Y2` (5). Color = `DROP_REGION_COLOR` (6), Shape and Size are their constants, the group shelf (T and Break By) = `DROP_REGION_GROUP` (14).
  - Default order = `getOrder() == XConstants.SORT_ASC` with an empty or null manual order list.
  - Largest first = `XConstants.SORT_VALUE_DESC` with `setSortByColValue(<measure full name>)`.
  - Data order = `XConstants.SORT_SPECIFIC` with `setManualOrderList(<strings>)`.
  - Area types = `GraphTypes.isArea`. Stacked counterparts: `CHART_AREA` → `CHART_AREA_STACK`, `CHART_STEP_AREA` → `CHART_STEP_AREA_STACK`.
- **Comments:** a short clause each, with no ticket, PR, slice or design-doc names.
- **New files** start with the AGPL header of `core/src/test/java/inetsoft/report/internal/graph/ChangeChartTypeProcessorMapTitleTest.java`, `Copyright (C) 2026  InetSoft Technology`.
- **Java tests** use `@ExtendWith(SpringExtension.class)`, `@ContextConfiguration(classes = { BaseTestConfiguration.class, LibManagerTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)`, `@SreeHome` and `@Tag("core")`. They clear `viewsheet.modernVisualization`, `viewsheet.darkMode` and `viewsheet.density` in `@AfterEach`. Mark a chart with `info.setVizMark(VizMark.MODERN_LIGHT)`, or use `VizContext.of(VizMark.MODERN_LIGHT)` against `VizContext.LEGACY`.

## Review Focus

1. **Classic charts are untouched at every entry point**: drop, type change, wizard and reset copy leave an unmarked chart's binding as today. Pinned by the `classic…` cases in Tasks 2 to 7.
2. **A script that sets `chartStyle` on a marked chart applies none of the defaults** (D126). Pinned by `aScriptChartStyleAppliesNoDefault` (Task 4).
3. **A waterfall whose source cannot be read keeps today's order without an exception**: no sandbox, a cube field, a named group, a calc field, more than 100 values, a failing query. Pinned by Task 3's `…ReadsNothing` cases and `anUnreadableSourceKeepsTheOrder` (Task 1).
4. **An author's choice survives later drops**: Stack turned off on an area, a sort the author set, and a cleared Color on a treemap. Pinned by `anAuthorSortSurvivesALaterDrop`, `aClearedColourStaysClearedOnASecondLevel` (Task 2) and `turningStackOffOnAMarkedAreaSticks` (Task 4).
5. **The candle refusal covers every route a dimension can take to Color**: from the tree, from another shelf, and dropped on the plot body. Pinned by the three refusal cases in Task 5.

## File Structure

- **Task 1:** create `report/internal/graph/ChartCreationDefaults.java` (the seven rules and `SourceOrder`).
- **Task 2:** `ChartCreationDefaults.java` (the four entry methods).
- **Task 3:** create `web/binding/handler/ChartSourceOrder.java`.
- **Task 4:** `web/binding/controller/ChangeChartTypeService.java`, and a generator check of D121's premise.
- **Task 5:** `web/binding/handler/VSChartBindingHandler.java`.
- **Task 6:** `web/vswizard/recommender/WizardRecommenderUtil.java`, `web/vswizard/recommender/ChartRecommenderUtil.java`, `web/vswizard/handler/VSWizardBindingHandler.java`.
- **Task 7:** `uql/viewsheet/internal/VizModernizeUtil.java`, `web/composer/vs/controller/ModernCopyService.java`.
- **Task 8:** `web/binding/model/ChartBindingModel.java`, `web/binding/service/VSChartBindingFactory.java`, `web/projects/portal/src/app/binding/data/chart/chart-binding-model.ts`, `web/projects/portal/src/app/binding/services/chart/vs-chart-editor.service.ts`.
- **Task 9:** none (whole-branch check and live-check handoff).

## How to run tests

From `$WT`:

```bash
./mvnw -q test -pl core "-Dtest=ClassA,ClassB" -Dsurefire.failIfNoSpecifiedTests=false
```

- Read `core/target/surefire-reports/*.txt` for the counts.
- Also run every new or changed class alone once (CLAUDE.md).
- If compilation fails with "Attempt to recreate … HeartBeatServiceProxy", delete the untracked `core/target/generated-test-sources`.
- **Portal tests** run from `$WT/web` after `npm ci`, because a junctioned `node_modules` breaks Vitest: `npx ng test portal --include="**/vs-chart-editor.service.spec.ts"`.

---

### Task 1: The seven rules (S7-1 to S7-7)

**Files:**
- Create: `core/src/main/java/inetsoft/report/internal/graph/ChartCreationDefaults.java`
- Test: `core/src/test/java/inetsoft/report/internal/graph/ChartCreationDefaultsTest.java`

**Interfaces:**
- Produces (all `public static`; `final` class, private constructor):
  - `interface SourceOrder { List<Object> valuesInSourceOrder(VSDimensionRef dim); SourceOrder NONE = dim -> null; }`. It is `@FunctionalInterface`, nested, and returns null when the values cannot be read.
  - `int MAX_ORDERED_VALUES = 100`
  - `int newDateLevel(String dataType, boolean axisShelf, VizContext ctx)`. It returns hour for `XSchema.TIME`; otherwise month when `ctx.modern && axisShelf`; otherwise year.
  - `int stackedAreaType(int oldType, int newType, VizContext ctx)`. It returns the stacked counterpart when `ctx.modern`, `newType` is `CHART_AREA` or `CHART_STEP_AREA`, and `!GraphTypes.isArea(oldType)`; otherwise `newType`.
  - `int stackedCounterpart(int type)`: area → area stack, step area → step area stack, any other type unchanged.
  - `boolean refusesColorDimension(ChartInfo info, int dropRegion, DataRef ref, VizContext ctx)`. It is true when `ctx.modern`, `GraphTypes.isCandle(info.getChartType())`, `dropRegion == DROP_REGION_COLOR` and `GraphUtil.isDimension(ref)`.
  - `void applyPieOrder(ChartInfo info, VizContext ctx)` (S7-3, D122 to D124)
  - `void applyWaterfallOrder(ChartInfo info, VizContext ctx, SourceOrder order)` (S7-4, D122, D123, D127)
  - `void applyTopLevelColor(ChartInfo info, VizContext ctx)` (S7-5, D125)
  - `void applyBreakByColor(ChartInfo info, VizContext ctx)` (S7-6, D125)
  - `void moveColorDimensionToPanels(ChartInfo info, VizContext ctx)` (S7-7, D117)

- [ ] **Step 1: Write the failing tests.** Charts are `DefaultVSChartInfo` (`CandleVSChartInfo` for the candle) with `setChartType`, `VSChartDimensionRef` and `VSChartAggregateRef` fields, and `modern()` / `VizContext.LEGACY`.
  - `newDateLevelIsMonthOnAMarkedAxis`: `XSchema.DATE` and `TIME_INSTANT` on the axis, modern → `MONTH_INTERVAL`. The same off the axis → `YEAR_INTERVAL`. The same with LEGACY → `YEAR_INTERVAL`. `XSchema.TIME`, modern, on the axis → `HOUR_INTERVAL`.
  - `pickingAreaGivesTheStackedType`: (BAR → AREA) → `CHART_AREA_STACK`. (LINE → STEP_AREA) → `CHART_STEP_AREA_STACK`. (AUTO → AREA) → `CHART_AREA_STACK`. (AREA_STACK → AREA) → `CHART_AREA`. (STEP_AREA → AREA) → `CHART_AREA`. (BAR → AREA) with LEGACY → `CHART_AREA`.
  - `aMarkedCandleRefusesAColourDimension`: candle, COLOR, dimension → true. Candle, COLOR, measure → false. Candle, SHAPE, dimension → false. Stock, COLOR, dimension → false. Candle with LEGACY → false.
  - `aPieSortsItsSliceLargestFirst`: pie, Y `Sum(Sales)`, Color `Segment` on the default order → `SORT_VALUE_DESC`, sort-by `Sum(Sales)`.
  - `aPieSliceOnShapeSortsWhenColorIsEmpty`: the same with `Segment` on Shape.
  - `aPieKeepsAnAuthorSortADateAndNoMeasure`: `SORT_DESC` stays. `SORT_SPECIFIC` with a list stays. A date slice (`isDateTime()`) stays `SORT_ASC`. No measure leaves `SORT_ASC`. LEGACY leaves `SORT_ASC`.
  - `aMultiStylePieSortsEachSliceByItsOwnMeasure`: two aggregates typed `CHART_PIE`, each with its own Color dimension. Each sorts by its own full name.
  - `aWaterfallSavesTheSourceOrder`: waterfall, X `Item` on the default order, a reader returning `Revenue, Returns, Cost of goods` → `SORT_SPECIFIC` and a manual list equal to those strings.
  - `anInvertedWaterfallOrdersItsYCategory`: `setInvertedGraph(true)`, `Item` on Y → ordered.
  - `anUnreadableSourceKeepsTheOrder`: the reader returns null → `SORT_ASC`, no manual list. A date category, an author `SORT_DESC`, a named group and `SourceOrder.NONE` each leave the order unchanged.
  - `aTooLongSourceKeepsTheOrder`: a reader returning 101 values → `SORT_ASC`.
  - `aTreemapColoursByItsTopLevel`: treemap, group fields `Division`, `Department`, Color null → the Color field's data ref is named `Division`, is not the same instance as group field 0, and has a `CategoricalColorFrame`. Sunburst, icicle and circle packing give the same.
  - `aTreemapKeepsAColourItAlreadyHas`: Color holding the measure `Sum(Margin)` stays. LEGACY binds nothing.
  - `aMarimekkoColoursByItsBreakBy`: mekko, X `Region`, group field `Product`, Color null → Color `Product`. No group field → Color null.
  - `aCandleColourDimensionBecomesAPanel`: candle, Y holding `Market`, Color `Ticker` → Y is `Market, Ticker`, Color null. Color holding a measure stays. LEGACY stays.
- [ ] **Step 2: Run them and see them fail** (`ChartCreationDefaultsTest`; compile errors count).
- [ ] **Step 3: Implement the produced methods.**
  - **The slice dimension** is the bindable's Color field when it holds a dimension, else Shape, else Size, as `fixPieDimensions:436-443` tests.
  - **The waterfall category** is the last dimension in X, or in Y when `info.isInvertedGraph()`.
  - **The data order** is written only when the reader returns 1 to `MAX_ORDERED_VALUES` values. Each non-null value is stored as `Tool.getDataString(v)`. A named-group category (`isNameGroup()`) is skipped here as well as in the reader, because its group names are not source values.
  - **Colour binding** follows the design call above.
- [ ] **Step 4: Run the tests and see them pass**, plus `ChangeChartTypeProcessorMapTitleTest`.
- [ ] **Step 5: Commit** with the subject `Chart defaults S7, add the creation-time default rules`.

---

### Task 2: The four entry methods

**Files:**
- Modify: `core/src/main/java/inetsoft/report/internal/graph/ChartCreationDefaults.java`
- Test: `core/src/test/java/inetsoft/report/internal/graph/ChartCreationDefaultsTest.java`

**Interfaces:**
- Consumes: Task 1's rules.
- Produces (`public static void`; each returns at once unless `ctx.modern`):
  - `afterTypeChange(ChartInfo info, int oldType, int newType, VizContext ctx, SourceOrder order)`: the rule of each family the chart enters (design call "Entering a family"). The area type is not set here (Task 4 maps it before the processor).
  - `afterDrop(ChartInfo info, int dropRegion, List<? extends DataRef> landed, VizContext ctx, SourceOrder order)`:
    - **Pie (any pie type, or a multi-style aggregate typed pie):** the pie order runs when a dimension lands on Color, Shape or Size, or a measure lands on any shelf (D124).
    - **Waterfall:** the order runs when a dimension lands on the category shelf: X or X2, or Y or Y2 when the chart is inverted.
    - **Treemap family:** the top-level colour runs when a dimension lands on the group shelf and is now group field 0 (matched by `getName()`).
    - **Marimekko:** the Break By colour runs when a dimension lands on the group shelf.
  - `afterWizard(ChartInfo info, VizContext ctx, SourceOrder order)`: pie order and waterfall order. The wizard already does S7-2, S7-5 and S7-6.
  - `forResetCopy(ChartInfo info, VizContext ctx, SourceOrder order)`:
    - the chart type and every multi-style aggregate's type go through `stackedCounterpart`
    - then the pie order, the waterfall order, the top-level colour (treemap family), the Break By colour (marimekko) and the candle panels
    - the date level is never touched (D5)

- [ ] **Step 1: Write the failing tests.**
  - `enteringAPieSortsButPieToDonutDoesNot`: `afterTypeChange` BAR → PIE sorts. PIE → DONUT leaves a slice on `SORT_ASC`.
  - `enteringATreemapColoursButTreemapToSunburstDoesNot`: the same pattern.
  - `enteringACandleMakesPanels`, and `enteringAWaterfallSavesTheOrder` (reader as in Task 1).
  - `aSliceDroppedOnColorSortsButOneDroppedOnTextDoesNot`: `afterDrop` with `DROP_REGION_COLOR` sorts. `DROP_REGION_TEXT` leaves `SORT_ASC`.
  - `aMeasureDroppedOnAPieSortsItsSlice`: the slice was dropped first while no measure was bound (still `SORT_ASC`); then a measure lands on Y → sorted.
  - `anAuthorSortSurvivesALaterDrop`: a pie slice on `SORT_DESC` stays `SORT_DESC` after a measure lands. A waterfall category on `SORT_DESC` stays after another dimension lands on X.
  - `aClearedColourStaysClearedOnASecondLevel`: the first group field lands → Color bound. Color is then cleared, and a second group field lands at index 1 → Color stays null.
  - `aBreakByDropColoursAMarimekko`, and `aWaterfallCategoryDropSavesTheOrder` (X).
  - `aMeasureDropOnAWaterfallReadsNothing`: a reader that fails the test when it is called; a measure lands on Y.
  - `theResetCopyRulesSkipTheDateLevel`: `forResetCopy` on an AREA chart with X `Date` at `YEAR_INTERVAL` → `CHART_AREA_STACK`, with the level still year.
  - `everyEntryIsANoOpOnAClassicChart`: each entry method with LEGACY on a pie, waterfall, treemap, mekko, candle and area. Each chart's `writeXML(PrintWriter)` output, written to a `StringWriter`, is equal before and after.
- [ ] **Step 2: Run them and see them fail.**
- [ ] **Step 3: Implement the four methods.**
- [ ] **Step 4: Run the tests and see them pass.**
- [ ] **Step 5: Commit** with the subject `Chart defaults S7, add the entry points of the creation-time defaults`.

---

### Task 3: Read a category in source order

**Files:**
- Create: `core/src/main/java/inetsoft/web/binding/handler/ChartSourceOrder.java`
- Test: `core/src/test/java/inetsoft/web/binding/handler/ChartSourceOrderTest.java`

**Interfaces:**
- Consumes: `ChartCreationDefaults.SourceOrder`, `MAX_ORDERED_VALUES`.
- Produces: `public final class ChartSourceOrder implements ChartCreationDefaults.SourceOrder`, with the constructor `ChartSourceOrder(ViewsheetSandbox box, SourceInfo source)` and `public static final int MAX_ROWS = 10_000`.
  - `valuesInSourceOrder` returns the distinct values of `dim.getGroupColumnValue()` in first-appearance order.
  - It copies the worksheet table (`source.getSource()`) into a `WorksheetWrapper`, clears the copy's pre and post runtime condition lists, and sets its max rows to `MAX_ROWS` (or the worksheet's smaller limit). It runs the copy's `AssetQuery` in `AssetQuerySandbox.RUNTIME_MODE` without the sandbox's cache, and reads the result row by row with `moreRows`, up to `MAX_ROWS` data rows.
  - It returns null for:
    - a null box, source or source name
    - `(dim.getRefType() & DataRef.CUBE) != 0`
    - `dim.isNameGroup()`
    - a column `Util.findColumn` cannot find
    - more than `MAX_ORDERED_VALUES` distinct values
    - any exception, logged at debug

- [ ] **Step 1: Write the failing tests.** Mock `ViewsheetSandbox` over a real `AssetQuerySandbox`. The table is the embedded worksheet table `Orders` with the columns `Item, Amount`.
  - `readsTheValuesInTheOrderTheyFirstAppear`: the rows of `waterfall_8.csv` (Revenue 520, Returns −35, Cost of goods −210, Gross profit adj 40, Salaries −120, Marketing −45, Other income 25, Taxes −38), with Revenue repeated at the end → the eight items in file order.
  - `stopsAtTheRowLimit`: a value that first appears at data row 10,001 → absent.
  - `aTooLongColumnReadsNothing`: 101 distinct values → null.
  - `aMissingColumnReadsNothing`, `aCubeFieldReadsNothing`, `aNamedGroupReadsNothing`, `noSandboxReadsNothing` → null.
  - `aFailingQueryReadsNothing`: `AssetQuery.createAssetQuery` throws → null, and no exception escapes.
  - `theQueryCarriesTheRowLimit`, `aSmallerWorksheetRowLimitStays`, `aSelectionOnTheTableDoesNotFilterTheRead`, `aWorksheetConditionStillApplies`, `theReadLeavesTheWorksheetAndTheSandboxAsTheyWere`, `aComposedTableIsReadWithoutChangingTheWorksheet`.
- [ ] **Step 2: Run them and see them fail.**
- [ ] **Step 3: Implement `ChartSourceOrder`.**
- [ ] **Step 4: Run the tests and see them pass.**
- [ ] **Step 5: Commit** with the subject `Chart defaults S7, read a chart category in source order`.

---

### Task 4: The type-change entry point

**Files:**
- Modify: `core/src/main/java/inetsoft/web/binding/controller/ChangeChartTypeService.java` (after the ref block that sets `oldType`, about `:150-160` and before `if(oldType == newType)` at `:169`; and after `process()` at `:188-190`)
- Test: `core/src/test/java/inetsoft/web/binding/controller/ChangeChartTypeServiceCreationDefaultsTest.java` (copy the setup of `ChangeChartTypeServiceTest`, with its `MockedStatic<GraphTypeUtil>` permission stub)
- Test: `core/src/test/java/inetsoft/report/composition/graph/StackedAreaSingleSeriesTest.java` (copy `ModernAxisLabelRuleTest`'s annotations and its `generator(chart, mark, size)` helper)

**Interfaces:**
- Consumes: `stackedAreaType`, `afterTypeChange`, `ChartSourceOrder`.
- Changes:
  - **Before the processor:** `int stacked = ChartCreationDefaults.stackedAreaType(oldType, newType, VizContext.of(ninfo))`. When `stacked != newType` and `GraphTypeUtil.checkChartStylePermission(stacked)` is true, `newType = stacked`; a `MessageException` from the check keeps `newType`. Every later use of `newType` then sees the stacked type, including the processor, `applySmoothLinesTransition` and the binding sent back.
  - **Right after `process()` returns:** `ChartCreationDefaults.afterTypeChange(cinfo, oldType, newType, VizContext.of(ninfo), new ChartSourceOrder(box.orElse(null), ninfo.getSourceInfo()))`. This runs before `ninfo.setVSChartInfo(cinfo)`, so the map branch and `updateAssembly` see it.

- [ ] **Step 0: Check D121's premise first.** Write `StackedAreaSingleSeriesTest`, which compares `CHART_AREA` with `CHART_AREA_STACK` on a marked chart.
  - `oneMeasureDrawsTheSame`: X `Month` (6 values), Y `Sum(Sales)`. The area visual objects' shapes (`getShape()` bounds and path points) are equal, and so are the Y scale's min and max.
  - `twoMeasuresWithoutStackMeasuresDrawTheSame`: Y `Sum(Sales)` and `Sum(Cost)`, `PlotDescriptor.setStackMeasures(false)`. The same comparison per measure.
  - `valuesShownDrawTheSame`: `setValuesVisible(true)`. The value labels' texts and positions are equal.

  Run it. If any case fails, stop and report to the controller before Step 1: a stacked type would then change a chart with no series dimension, which D121 assumes it does not.
- [ ] **Step 1: Write the failing tests.** The chart is marked with `chart.getVSAssemblyInfo().setVizMark(VizMark.MODERN_LIGHT)`, except in the classic cases.
  - `pickingAreaOnAMarkedBarGivesStackedArea`: BAR, event `CHART_AREA` → `getChartType() == CHART_AREA_STACK`.
  - `turningStackOffOnAMarkedAreaSticks`: AREA_STACK, event `CHART_AREA` → `CHART_AREA`.
  - `aDeniedStackedStyleKeepsThePlainArea`: the permission stub is false for `CHART_AREA_STACK` and true otherwise → `CHART_AREA`.
  - `classicAreaStaysPlain`: an unmarked BAR, event `CHART_AREA` → `CHART_AREA`.
  - `switchingAMarkedBarToPieSortsItsSlice`: X `Segment`, Y `Sum(Sales)` → the Color dimension `Segment` is `SORT_VALUE_DESC` by `Sum(Sales)`.
  - `switchingAMarkedBarToTreemapColoursByTheTopLevel`: X `Division`, `Department`, Y `Sum(Sales)` → Color `Division`.
  - `switchingAMarkedBarWithTickersToCandleMakesPanels`: Color `Ticker` → Y holds `Ticker`, Color null.
  - `switchingToWaterfallSavesTheSourceOrder`: `chart.setSourceInfo(new SourceInfo(SourceInfo.ASSET, null, "Funnel"))`. The mocked sandbox's `getAssetQuerySandbox()` returns a real sandbox over a worksheet whose embedded table `Funnel` holds Task 3's rows → `Item` is `SORT_SPECIFIC` in file order.
  - `classicSwitchesApplyNoDefault`: the pie, treemap and candle cases unmarked → today's binding (slice `SORT_ASC`, Color null, Color still `Ticker`).
  - `aScriptChartStyleAppliesNoDefault`: a marked `ChartVSAssemblyInfo` whose X holds `Division` and `Department` and Y holds `Sum(Sales)`; `setChartStyle(CHART_TREEMAP)` → Color null (D126).
- [ ] **Step 2: Run them and see them fail.**
- [ ] **Step 3: Implement the two changes.**
- [ ] **Step 4: Run the tests and see them pass**, plus `StackedAreaSingleSeriesTest`, `ChangeChartTypeServiceTest` and `ChangeChartTypeServiceSmoothLinesTransitionTest`.
- [ ] **Step 5: Commit** with the subject `Chart defaults S7, apply the creation-time defaults on a type change`.

---

### Task 5: The drop entry point

**Files:**
- Modify: `core/src/main/java/inetsoft/web/binding/handler/VSChartBindingHandler.java`:
  - `addRemoveColumns:68-81`, `addColumns:89-117`, `dropToChartEditor:186-209`, `dropToChartView:281-295`
  - `createChartRef:319` and `setDefaultDateLevel:471-480`
- Test: `core/src/test/java/inetsoft/web/binding/handler/VSChartBindingHandlerCreationDefaultsTest.java`

**Interfaces:**
- Consumes: `newDateLevel`, `refusesColorDimension`, `afterDrop`, `ChartSourceOrder`.
- Changes:
  - **The date level:** `setDefaultDateLevel` gains a `VizContext ctx` parameter (from `assembly.getChartInfo()` in `createChartRef`). Its base level is `ChartCreationDefaults.newDateLevel(dim.getDataType(), dndHandler.isXYRegion(dropType), ctx)`. The second-drop adjustment (`getNextDateLevelValue`) is unchanged.
  - **The candle refusal:**
    - In `dropToChartEditor`'s aesthetic branch, the drop is a no-op when `refusesColorDimension(cinfo, dropRegion, refs.get(0), ctx)` is true: nothing is added, and the dragged field stays on its source shelf.
    - In `dropToChartView`, the same check runs against the region the field would take: the target's own region, or `dndHandler.getAestheticDropType(ref, cinfo)` for `DROP_REGION_PLOT`.
  - **The other rules:** after the drop in `addColumns` and `addRemoveColumns`, call `ChartCreationDefaults.afterDrop(vinfo, dropRegion, refs, VizContext.of(info), new ChartSourceOrder(rvs.getViewsheetSandbox().orElse(null), info.getSourceInfo()))`.
    - A chart-view drop passes the region the field took, as above.
    - A refused drop does not call it.
    - A drop type that is not a number calls nothing.

- [ ] **Step 1: Write the failing tests.**
  - **Handler:** `new VSChartBindingHandler(new ChartDndHandler(mock(ChartRefModelFactoryService.class)), mock(VSDrillHandler.class), mock(VSChartDataHandler.class), mock(AnalyticRepository.class))`.
  - **Runtime:** a mocked `RuntimeViewsheet` whose `getViewsheetSandbox()` returns `Optional.empty()`.
  - **Chart:** a `ChartVSAssembly` on a `new Viewsheet()`.
  - **Entries:** built the way `createChartRef` reads them: the `assembly`, `dtype` and `refType` properties, plus what `isDimension(entry)` and `getColumnValue(entry)` read.
  - **Targets:** `BindingDropTarget` or `ChartAestheticDropTarget` with `setDropType("2")` and so on.
  - Cases:
    - `aDateOnTheXShelfOfAMarkedChartGroupsByMonth`: a `date` entry on "2" → the X field's level is `MONTH_INTERVAL`.
    - `aDateOnColorKeepsYear`: "6" → `YEAR_INTERVAL`.
    - `aDateOnAClassicChartKeepsYear`.
    - `aSecondDropOfTheSameDateTakesTheNextLevel`: `Date` on X at month, then `Date` again → `DAY_OF_MONTH_PART`.
    - `aSliceOnAMarkedPieSortsLargestFirst`: pie with Y `Sum(Sales)`; `Segment` on "6" → `SORT_VALUE_DESC`.
    - `aFirstTreeLevelOnAMarkedTreemapAlsoColours`: "14" → Color `Division`.
    - `aBreakByOnAMarkedMarimekkoAlsoColours`.
    - `classicDropsApplyNoDefault`: the pie, treemap and marimekko cases unmarked.
    - `aDimensionFromTheTreeOnAMarkedCandlesColorIsRefused`: Color null after `addColumns`.
    - `aDimensionMovedFromXToAMarkedCandlesColorIsRefused`: `addRemoveColumns` with a `ChartTransfer` from X → Color null, and X still holds the field.
    - `aDimensionDroppedOnAMarkedCandlesPlotIsRefused`: `ChartViewDropTarget` with `DROP_REGION_PLOT`, where `getAestheticDropType` resolves to Color → Color null.
    - `aMeasureOnAMarkedCandlesColorIsAccepted`.
- [ ] **Step 2: Run them and see them fail.**
- [ ] **Step 3: Implement the changes.**
- [ ] **Step 4: Run the tests and see them pass.**
- [ ] **Step 5: Commit** with the subject `Chart defaults S7, apply the creation-time defaults on a drop`.

---

### Task 6: The wizard entry point

**Files:**
- Modify: `core/src/main/java/inetsoft/web/vswizard/recommender/WizardRecommenderUtil.java` (`setDefaultDateLevel:336-349`)
- Modify: `core/src/main/java/inetsoft/web/vswizard/recommender/ChartRecommenderUtil.java` (the call at `:76`)
- Modify: `core/src/main/java/inetsoft/web/vswizard/handler/VSWizardBindingHandler.java` (`addChartVSAssembly`, after `fixSizeFrame` at `:930-932` and before `changeChartData`)
- Test: `core/src/test/java/inetsoft/web/vswizard/recommender/WizardDateLevelTest.java`

**Interfaces:**
- Consumes: `newDateLevel`, `afterWizard`, `ChartSourceOrder`.
- Changes:
  - **The date fallback:** a new `public static void setDefaultDateLevel(VSDimensionRef dim, AssetEntry entry, VizContext ctx)`. Its fallback, when no interval was found, is `newDateLevel(dim.getDataType(), true, ctx)`. The two-argument form delegates with `VizContext.LEGACY`, so the crosstab caller (`CrosstabRecommenderUtil:66`) is unchanged.
  - **The chart's field creation:** `ChartRecommenderUtil.createChartRef` passes `VizContext.of(tempInfo == null || tempInfo.getTempChart() == null ? null : tempInfo.getTempChart().getVSAssemblyInfo())`.
  - **The chosen chart:** `addChartVSAssembly` calls `ChartCreationDefaults.afterWizard(info.getVSChartInfo(), VizContext.of(info), new ChartSourceOrder(rvs.getViewsheetSandbox().orElse(null), source))` when the chart info is not null.

- [ ] **Step 1: Write the failing tests** (`WizardDateLevelTest`):
  - `aMarkedWizardFallsBackToMonth`: an entry with no `interval` property, modern → `MONTH_INTERVAL`.
  - `aMarkedWizardKeepsAFoundInterval`: `interval` = `YEAR_INTERVAL`, modern → year (D120).
  - `theTwoArgumentFormKeepsYear`: no interval → `YEAR_INTERVAL`.
  - `aTimeFieldFallsBackToHour`: an `XSchema.TIME` field, modern → `HOUR_INTERVAL`.

  The `addChartVSAssembly` hook is covered by Task 2's `afterWizard` tests and by the live checks (Task 9).
- [ ] **Step 2: Run them and see them fail.**
- [ ] **Step 3: Implement the changes.**
- [ ] **Step 4: Run the tests and see them pass**, plus `WizardRecommenderUtilRepointAdhocFilterTest`.
- [ ] **Step 5: Commit** with the subject `Chart defaults S7, apply the creation-time defaults in the wizard`.

---

### Task 7: The reset-copy entry point

**Files:**
- Modify: `core/src/main/java/inetsoft/uql/viewsheet/internal/VizModernizeUtil.java` (`resetToModern:118-139`, `resetChrome:148-167`)
- Modify: `core/src/main/java/inetsoft/web/composer/vs/controller/ModernCopyService.java` (`:149`)
- Test: `core/src/test/java/inetsoft/uql/viewsheet/internal/ResetCopyCreationDefaultsTest.java` (dashboard set-up as in `VizModernizeResetTest`)

**Interfaces:**
- Consumes: `forResetCopy`, `ChartSourceOrder`.
- Changes:
  - **The new overload:** `public static int resetToModern(Viewsheet vs, Function<ChartVSAssemblyInfo, ChartCreationDefaults.SourceOrder> orders)`. The one-argument form delegates with `info -> ChartCreationDefaults.SourceOrder.NONE`.
  - **In `resetChrome`, for a chart:** after `resetSlots(ChartChromeSlots.of(chart), …)`, call `ChartCreationDefaults.forResetCopy(chart.getVSChartInfo(), VizContext.of(mark), orders.apply(chart))`. This runs before `seedAll`, so the per-type seeds see the new types. The chart's runtime is not cleared: `ModernCopyService.countLegends` draws the sandbox's cached data with it before the copy is saved, and a cleared runtime has no Color legend to count.
  - **`ModernCopyService`:** passes `info -> new ChartSourceOrder(rvs.getViewsheetSandbox().orElse(null), info.getSourceInfo())`.

- [ ] **Step 1: Write the failing tests:**
  - `theResetCopyStacksAnArea`: AREA → `CHART_AREA_STACK`. STEP_AREA → `CHART_STEP_AREA_STACK`.
  - `theResetCopyKeepsTheDateLevel`: a bar with X `Date` at `YEAR_INTERVAL` → still year.
  - `theResetCopySortsAPieAndColoursATreemapAndAMarimekko`, both on classic charts and on charts that were already marked.
  - `theResetCopyMovesACandleColourDimensionToPanels`.
  - `theResetCopySavesAWaterfallsSourceOrder`: `resetToModern(vs, info -> dim -> List.<Object>of("Revenue", "Returns"))` → `SORT_SPECIFIC`, `Revenue, Returns`.
  - `theOneArgumentResetReadsNoSourceOrder`: the waterfall stays `SORT_ASC`.
  - `modernizeAndRevertApplyNoDefault`: `VizModernizeUtil.modernize` then `revert` on a classic AREA pie dashboard leave the type and the slice order as they were (M6).
- [ ] **Step 2: Run them and see them fail.**
- [ ] **Step 3: Implement the changes.**
- [ ] **Step 4: Run the tests and see them pass**, plus `VizModernizeResetTest`, `VizModernizeUtilTest` and `ModernCopyServiceTest`.
- [ ] **Step 5: Commit** with the subject `Chart defaults S7, apply the creation-time defaults on the reset copy`.

---

### Task 8: The client learns the mark and refuses the candle Color drop

**Files:**
- Modify: `core/src/main/java/inetsoft/web/binding/model/ChartBindingModel.java` (field and accessors beside `hasDateComparison`, `:594`, `:642`)
- Modify: `core/src/main/java/inetsoft/web/binding/service/VSChartBindingFactory.java` (`createModel:68-83`)
- Modify: `web/projects/portal/src/app/binding/data/chart/chart-binding-model.ts` (beside `hasDateComparison`, `:91`)
- Modify: `web/projects/portal/src/app/binding/services/chart/vs-chart-editor.service.ts` (`isFieldAcceptable:177`)
- Test: `core/src/test/java/inetsoft/web/binding/service/VSChartBindingFactoryVizModernTest.java`
- Test: `web/projects/portal/src/app/binding/services/chart/vs-chart-editor.service.spec.ts` (new)

**Interfaces:**
- Produces:
  - Java `boolean isVizModern()` / `void setVizModern(boolean)`, set in `createModel` from `VizContext.of(info).modern`.
  - TypeScript `vizModern?: boolean` on `ChartBindingModel`.
- Changes: in `isFieldAcceptable`, before the per-shelf checks, a marked candle refuses a dimension on Color: `if(binding.vizModern && GraphTypes.isCandle(chartType) && dropType == "color") { return !isDimension; }`.

- [ ] **Step 1: Write the failing tests.**
  - **`VSChartBindingFactoryVizModernTest`:** a factory with Mockito mocks of its four services, and an empty `CandleVSChartInfo` chart → `isVizModern()` is true when the chart is marked and false when it is not.
  - **`vs-chart-editor.service.spec.ts`:** `new VSChartEditorService(<BindingService stub>, <ModelService stub>, <ViewsheetClientService stub>, <UIContextService stub>)`, and a `DndService` stub whose `getTransfer()` returns `{ column: [<entry>] }`. A dimension entry has a path containing `_#(js:Dimensions)`; a measure entry has `properties.refType` set to a measure type.
    - `refuses a dimension on a marked candle's color`: `isDropPaneAccept(dnd, { chartType: GraphTypes.CHART_CANDLE, vizModern: true }, "color")` → false.
    - `accepts a measure on a marked candle's color` → true.
    - `accepts a dimension on a classic candle's color`: `vizModern: false` → true.
    - `accepts a dimension on a marked stock's color` → true.
- [ ] **Step 2: Run them and see them fail.**
- [ ] **Step 3: Implement the changes.**
- [ ] **Step 4: Run the tests and see them pass**, and run `npx ng test portal --include="**/graph-util.spec.ts"` as a neighbour check.
- [ ] **Step 5: Commit** with the subject `Chart defaults S7, refuse a dimension on a marked candle's colour in the editor`.

---

### Task 9: Whole-branch check and live-check handoff

- [ ] **Step 1:** Run the packages the branch touches. Expected: 0 failures.

  ```bash
  ./mvnw -q test -pl core "-Dtest=inetsoft.report.internal.graph.*Test,inetsoft.web.binding.**.*Test,inetsoft.web.vswizard.**.*Test,inetsoft.uql.viewsheet.internal.*Test,inetsoft.web.composer.vs.controller.ModernCopyService*Test" -Dsurefire.failIfNoSpecifiedTests=false
  ```

- [ ] **Step 2:** The full `core` suite runs when the controller says so. Expected: 0 failures. The known timing flakes (`XSwapperGCThrottleTest`, `XSwapperStaleSwapFileMapTest`) pass when run alone.
- [ ] **Step 3:** Write the live-check handoff `E:/Temp/chart-defaults-s7-checks-handoff.md` in the format of `E:/Temp/chart-defaults-s6a-checks-handoff.md`. The base build is `fe589c28a9`. It covers:
  - **Both build orders (D116):** on a modern dashboard, build each of the bundle's repro charts by picking the style first (`repro/scripts/build_f1.js`) and again with the fields first: area (3 series), pie, donut, waterfall (`waterfall_8.csv`), treemap (2 and 3 levels), sunburst, marimekko (4×4). Each shows the default.
  - **Dates:** a raw date on X and on Y groups by month. On Color it groups by year. The wizard's two-year data gives month, and ten years gives year.
  - **Waterfall:** the sort dialog shows the manual order. A refresh keeps it. A database-backed table gives the same order on two opens.
  - **Candle:** the editor refuses a Ticker drop on Color from the tree, from X and onto the plot. Switching a bar with Ticker on Color to candle shows two panels with each ticker's own prices.
  - **Author choices survive:** Stack turned off, a sort the author picked, and a cleared treemap Color after adding a second level.
  - **Reset copy (Modernize, option 2) of a classic dashboard** with an area, a pie, a waterfall, a treemap, a marimekko, a two-ticker candle and a yearly bar: S7-2 to S7-7 applied, the year kept.
  - **Untouched:** Modernize "Keep" and Revert change no binding. A dashboard script setting `chartStyle` changes nothing at run time.
  - **The hard requirement:** on a classic dashboard every action above gives today's binding, and classic tiles, XML, PNG and PDF are identical across `fe589c28a9` and the head.
- [ ] **Step 4:** Leave the commit to the controller (the handoff lives outside the repo).
