# Chart Defaults S1 — Chart Chrome Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give a chart on a marked (modern) assembly the bundle's chrome: no rule under its title, axis titles and a vertical grid chosen by resolved type, no value-axis line, a frameless legend, readable automatic labels on filled marks, a tickless pareto percent axis and straight lines. Classic charts stay pixel-identical and keep their XML.

**Architecture:** The per-type values live in S0's `seedTypeDefaults`, decided by one predicate each in a new `VSChartTypeDefaults`. Tiered values (title visibility, x grid style, legend border) are written at DEFAULT and registered in S0's saved-defaults lists so they travel. The value-axis line is a plain field, so it gets the first M4 user flag (`AxisDescriptor.userLineVisible`) and a reusable parse-time derivation (`DerivedUserFlag`). Max-mode copies follow the normal value at read time under modern. Drawing changes (pareto ticks, label colour, facet borders) are gated on the generator's `vizContext`.

**Tech Stack:** Java 21, JUnit 5, Mockito, Spring test context (`@SreeHome`), Maven module `core`. No frontend change.

**Spec:** `docs/superpowers/specs/lookfeel/2026-11-06-chart-defaults-design.md`: §3 M1–M9, §4 R1, R3, R8, R10, §5 S1 (S1-1 … S1-8, "S1-2 title rules", "S1-4 value axis line"), §7 (C13), §10 D22–D27, §11. Where this plan and the spec differ, this plan wins (see Corrections).

**Builds on:** `docs/superpowers/plans/2026-11-06-chart-defaults-s0-defaults-travel.md`, all four tasks merged. Names used from its "Interfaces for later slices": `PlotDescriptor/LegendsDescriptor/ChartDescriptor.savedDefaults()`, `ChartVSAssemblyInfo.syncSavedDefaults()`, `seedTypeDefaults(VizContext ctx, ResolvedChartType type)`, `reseedIfResolvedTypeChanged()` (runs the per-type seed on a **marked** chart only; an unmarked chart only records its key), `getSeededType()`, the `ResolvedChartType` record (`type()`, `measureOnX()`, `measureOnY()`, `xFields()`, `yFields()`), and the rule that a flagged plain field is written on every legacy pass while its flag is false.

**Verified against:** `chart-defaults-design` = `epic-74519` `5e4772a6cf` plus the spec commit, and `origin/feature-80398` `0625e0756f` for Task 12. Paths are under `core/src/main/java/inetsoft/` unless they start with `core/` or `web/`.

## Corrections to the spec

| # | Spec | Code at `5e4772a6cf` | This plan |
|---|---|---|---|
| C1 | S1-1: the `border.bottom 1px solid #d9d5cc` assertion in `VSFormatModel` tests flips | `VSFormatModelTest` asserts `1px solid #c7c4bb`, and only for a table, a selection container and a range slider (`:68-131`). No chart. TL §7's chart assertions are in `SeedChromeDefaultsTest:631-776`. | No `VSFormatModelTest` change. Task 1 lists the tests that flip. |
| C2 | S1-3: THIN when the type is point or scatter contour "and a measure is on X" | With a measure on X and none on Y the graph is inverted: the coordinate is `new RectCoord(yscale, xscale)` transposed (`DefaultGraphGenerator:369-377`), so the plot's x grid style draws on the Y dimension. `CSSChartStyles:178-190` swaps `line_x`/`line_y` for the same reason. | THIN only when there are measures on X **and** Y. A horizontal point chart already gets its vertical lines from `yGridStyle` THIN. |
| C3 | S1-2: the seed writes `false` only while the title has no custom text; wizard text does not count | The stored descriptor cannot tell the wizard's `setTitleValue` (`VSWizardBindingHandler:916-917`) from the dialog's (`TitleFormatDialogModel:90`). | New plain flag `TitleDescriptor.userTitle`, derived on parse from "has text" (M4), set by author text paths only. |
| C4 | M4/S1-2/S1-4: `maxModeVisible` and `maxModeLineVisible` follow `visible` and `lineVisible` | No seed writes either. The show-all services write them `true` (`VSChartTitlesVisibilityService:247-250`, `VSChartAxesVisibilityService:214`). | Read rule under modern: stored max-mode value AND normal value. A stored `false` is the author's, as M4 says. No max-mode flag. |
| C5 | S1-4: check the facet outer-border logic | `setAxisGridStyle` (`GraphGenerator:4259-4316`) draws panel separators only while an inner axis line is visible. A seeded-off value line removes them. | Task 4: a line off only because of the seed counts as visible for that decision. |
| C6 | S1-5: seed border NONE; an in-place legend keeps both (R2) | A layout change does not re-seed. The fill is written at render (`VGraphPair:1081-1104`, `:1169-1192`, `:1347`). | One resolver decides border and fill from mark and layout. The seed and `LegendsDescriptor.initDefaultFormat` both write the border's DEFAULT tier with it. |
| C7 | S1-8: seed `false` for line, step, jump, area, step area | The modern seed writes smooth for every type, circular network included (`ChartVSAssemblyInfo:163-165`). The wizard re-asserts smooth for area and circular on any chart (`VSWizardBindingHandler:862-877`), after the seed. | Modern smooths circular network only. The wizard skips marked charts. |
| C8 | M5: per-type seeds re-run on binding changes | The re-seed runs inside `update` after the runtime refs are cloned from the design refs (`ChartVSAssemblyInfo:2221-2223`, `VSChartInfo:867-904`). A stale runtime descriptor is what `ChangeChartTypeService:224-225` drops by hand. | The line seed also writes the runtime X/Y refs. A per-type re-seed drops the runtime chart descriptor (Task 2). |
| C9 | S1-7: copy tick visibility | `AxisDescriptor.ticksVisible` defaults to `false` (`:927`); the pareto percent axis is a new `AxisSpec` with ticks on (`AxisSpec:671`). | Copying gives a tickless axis unless the author turned ticks on. |

## Global Constraints

- **Worktree:** `$WT` is the executor's worktree on a branch cut from the S0 branch after its four commits. Never work in `E:/StyleBI/stylebi-enterprise/community`. All changes are in the community repo under `core/`.
- **Git:** each git command is its own call, `git -C "$WT" ...`, never chained with `&&`. Run `git -C "$WT" branch --show-current` before every commit. One commit per task, subject `Chart defaults S1, <what changed>`, plain prose, last line `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`. `git add` and `git commit` are separate calls. Never push.
- **Scope is the mark (M1, D22):** only an assembly with a modern `VizMark` changes. A classic chart renders pixel-identical and its XML is byte-identical on load and save. Graph-package code is untouched; drawing changes read `GraphGenerator.vizContext`.
- **The author wins (M4):** tiered values are written only at DEFAULT. A plain field is written only while its flag is false. A flag is stamped only by an author path, and only when the author changed the value or acted on it explicitly (a hide). Modernize then Revert renders identically (P6).
- **Every legacy pass writes the classic value (D24),** and only from the full `seedChromeDefaults`: `reseedIfResolvedTypeChanged()` never writes an unmarked chart. A classic value that varies by type stays on its existing path: classic smoothing stays in `legacySmoothLines()` (`ChartVSAssemblyInfo:299-313`) and `ChangeChartTypeService.applySmoothLinesTransition`; map title visibility stays in `ChangeChartTypeProcessor.changeToMap/changeFromMap`. Every other S1 classic value is one constant for all types: title visible `true`, x grid `NONE`, axis line `true`, legend border `THIN_LINE`.
- **Classic values:** title visible `true`; `xGridStyle` `StyleConstants.NONE`; axis line `true`; legend border `StyleConstants.THIN_LINE`; legend fill `Color.WHITE` at alpha 50. **Modern in-place legend fill:** `VSChartChromeDefaults.legendBackground(ctx)` (white light, `#3E3C43` dark) at alpha 50.
- **Registration (S0):** a tiered field joins its descriptor's `savedDefaults()` in the commit that first seeds it on both branches.
- **feature-80398 is not merged.** Tasks 1–11 must land without it. Keep new `ChartVSAssemblyInfo` methods after `anySmoothLinesAggregate` (`:315-328`), away from where 80398 adds `chromeSlots()` (after `defaultPadding`, `:217-220`).
- **Comments:** a short clause each. No ticket, PR, issue, slice or design-doc names in source. No `.html` file is touched.
- **New files** start with the AGPL header of `SeedChromeDefaultsTest.java`, `Copyright (C) 2026  InetSoft Technology`.
- **Java tests that reach Spring beans** use `@ExtendWith(SpringExtension.class)`, `@ContextConfiguration(classes = { BaseTestConfiguration.class, LibManagerTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)`, `@SreeHome`, `@Tag("core")`, and clear `viewsheet.modernVisualization`, `viewsheet.darkMode` and `viewsheet.density` in `@AfterEach` as `SeedChromeDefaultsTest.reset()` does. `VSChartTypeDefaultsTest` is pure and needs no context. Close every `mockStatic`.

## Review Focus

1. **An axis title the author typed before this change** (old file, no `userTitle` attribute) on a classic chart that is then Modernized with "Keep my formatting" stays visible, and Revert keeps it. Pinned in Task 5 by `aTypedTitleFromAnOldFileStaysVisibleThroughModernizeAndRevert`; the reset copy in Task 12.
2. **"Show All Titles", or ticking the axis line in the axis dialog, while a modern chart is maximized** makes what was asked for appear, although max mode follows normal visibility. Pinned in Task 6 by `showAllTitlesInMaxModeShowsThemOnAModernChart` and in Task 3 by `turningTheLineOnInModernMaxModeTurnsItOnEverywhere`.
3. **A faceted modern bar chart** keeps its panel separators although its value-axis line is off; an author-hidden line still removes them. Pinned in Task 4.
4. **A horizontal point chart** (measure on X, dimension on Y) gains no horizontal lines between categories. Pinned in Task 7 by `aHorizontalPointChartGetsNoCategoryLines`.
5. **"Show all axes" after hiding axes on a modern chart** brings them back in the modern look (no value-axis line), and Revert after a hide keeps the axis hidden. Pinned in Task 3 by `showAllOnAModernChartRestoresTheSeededLines` and `aRevertAfterHidingKeepsTheAxisHidden`.

## File Structure

- `uql/viewsheet/graph/DerivedUserFlag.java` (new): parse and write rule for an M4 flag.
- `uql/viewsheet/internal/VSChartTypeDefaults.java` (new): one predicate per per-type value.
- `uql/viewsheet/graph/AxisDescriptor.java`: line flag, max-mode read rule.
- `uql/viewsheet/graph/TitleDescriptor.java`, `TitlesDescriptor.java`, `ChartDescriptor.java`: title flag, stamping setters, registration.
- `uql/viewsheet/graph/LegendsDescriptor.java`, `LegendDescriptor.java`, `uql/viewsheet/internal/VSChartChromeDefaults.java`: legend frame resolver.
- `uql/viewsheet/internal/ChartVSAssemblyInfo.java`: seeds.
- `report/composition/graph/GraphGenerator.java`, `GraphDefault.java`, `VGraphPair.java`, `web/graph/GraphBuilder.java`: reads and drawing gates.
- Author paths: `web/graph/model/dialog/AxisPropertyDialogModel.java`, `TitleFormatDialogModel.java`, `web/viewsheet/controller/chart/VSChartAxesVisibilityService.java`, `VSChartTitlesVisibilityService.java`, `report/script/AxisScriptable.java`, `TitleScriptable.java`, `ChartProcessor.java`, `report/internal/graph/ChangeSeparateStatusProcessor.java`, `ChangeChartTypeProcessor.java`, `web/binding/controller/ChangeChartTypeService.java`, `web/vswizard/handler/VSWizardBindingHandler.java`.
- Tests (new): `core/src/test/java/inetsoft/uql/viewsheet/graph/AxisDescriptorLineFlagTest.java`, `.../graph/TitleDescriptorUserTitleTest.java`, `core/src/test/java/inetsoft/uql/viewsheet/internal/VSChartTypeDefaultsTest.java`, `.../internal/ChartChromeTypeSeedTest.java`, `core/src/test/java/inetsoft/report/composition/graph/ChartChromeRenderTest.java`, `core/src/test/java/inetsoft/web/viewsheet/controller/chart/VSChartAxesVisibilityServiceTest.java`, `.../chart/VSChartTitlesVisibilityServiceTest.java`, `core/src/test/java/inetsoft/web/graph/GraphBuilderTitleHiddenTest.java`, `core/src/test/java/inetsoft/report/internal/graph/ChangeSeparateStatusProcessorLineFlagTest.java`.

**Test harnesses.** `ChartChromeTypeSeedTest` has `newChart()` as `SeedChromeDefaultsTest:99-104`, `gateOn()`/`gateOff()`, and `bind(ChartVSAssemblyInfo info, int type, ChartRef[] x, ChartRef[] y)` which sets design fields and `setChartType(type)` (the runtime type follows at once, `AbstractChartInfo:611-615`). "Modernize" is `setVizMark(VizMark.MODERN_LIGHT)` then `seedChromeDefaults(VizContext.of(info))`; "Revert" is `setVizMark(null)` then the same call. Render-level tests copy `ResolvedTypeReseedTest`'s `columns()`, ref builders and `render` (S0 Task 4). `ChartChromeRenderTest` builds an `EGraph` as `AxisTitleGapTest:111-170` does: `GraphGenerator.getGenerator(assemblyInfo, null, new DefaultDataSet(rows), new VariableTable(), null, XSourceInfo.NONE, null).createEGraph()`.

## How to run tests

From `$WT`. `-q` hides the summary on success; read `core/target/surefire-reports/*.txt` for counts.

```bash
./mvnw -q test -pl core "-Dtest=ClassA,ClassB" -Dsurefire.failIfNoSpecifiedTests=false
```

Do not add `install`. Run every new or changed test class on its own at least once.

## Pre-flight check (spec §7, C13)

- [ ] On a built server with a repro dashboard from `chart-defaults-design/repro/charts.md`, open a marked bar chart with values shown at comfortable density. In the browser's devtools read the computed `font-size` of one axis tick label and one data label. Record "tick Npx, data Npx" in the PR description. C13 ships as is either way; no code changes.

---

### Task 1: No rule under a chart's title

S1-1, R1.

**Files:**
- Modify: `uql/viewsheet/internal/ChartVSAssemblyInfo.java:138-149`
- Modify: `uql/viewsheet/FormatInfo.java:251-256` (Javadoc of `ownsTitleBorder` only: "a chart's title keeps its own border colour")
- Test: `core/src/test/java/inetsoft/uql/viewsheet/internal/SeedChromeDefaultsTest.java:631-776`

- [ ] **Step 1: Change the tests**

These flip:
- `aModernChartTitleCarriesTheBottomRule` → `aModernChartTitleCarriesNoRule`: gate on; `getBordersValue()` and `getBorderColorsValue()` are null; background still null.
- `aUserObjectBorderColourDoesNotReachAChartTitleRule` → `aUserObjectBorderColourDoesNotReachAChartTitle`: `title.getBorders()` is null, and `title.getBorderColors()` is null or its bottom is not `Color.RED`.
- `aModernizedChartEqualsAFreshlyCreatedOne`: both borders values null, both border colours null.
- `revertingAChartTitleRemovesTheRule` → `aChartSeededWithTheRuleLosesItOnItsNextSeed`: write `VSTitleChromeDefaults.titleRuleBorders()` and `titleRuleColors(ctx)` onto a marked chart's title default (an asset seeded before this change), call `seedChromeDefaults(VizContext.of(info))`; both null. Then Revert; both null.

New: `tablesSelectionsAndCalendarsKeepTheirTitleRule`: gate on; `newTable()`, `newSelectionList()` and `newCalendar()` title defaults have `bottom == StyleConstants.THIN_LINE`.

- [ ] **Step 2: Run to verify they fail**

Run: `./mvnw -q test -pl core -Dtest=SeedChromeDefaultsTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: FAIL in the four changed tests; the new one passes.

- [ ] **Step 3: Implement.** The title block writes `setBordersValue(null)` and `setBorderColorsValue(null)` on both branches, so an asset seeded with the rule loses it at its next seed (M8). Foreground writes are unchanged. Reword the block's comment.

- [ ] **Step 4: Run to verify they pass**

Run: `./mvnw -q test -pl core "-Dtest=SeedChromeDefaultsTest,ChromeSeedClassificationTest,VizModernizeUtilTest,BookmarkChromeResolutionTest,VSFormatModelTest" -Dsurefire.failIfNoSpecifiedTests=false`
Expected: all pass.

- [ ] **Step 5: Commit**

```bash
git -C "$WT" add core/src/main/java/inetsoft/uql/viewsheet/internal/ChartVSAssemblyInfo.java core/src/main/java/inetsoft/uql/viewsheet/FormatInfo.java core/src/test/java/inetsoft/uql/viewsheet/internal/SeedChromeDefaultsTest.java
```
```bash
git -C "$WT" commit -m "Chart defaults S1, drop the rule under a modern chart's title" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 2: Value-axis line off, its author flag and the parse-time derivation

S1-4 (seed and flag), M4 derivation, C8.

**Files:**
- Create: `uql/viewsheet/graph/DerivedUserFlag.java`
- Create: `uql/viewsheet/internal/VSChartTypeDefaults.java`
- Modify: `uql/viewsheet/graph/AxisDescriptor.java` (accessors after `setMaxModeLineVisible` `:431-433`; parse after `:692`; write after `:806`; field after `:930`)
- Modify: `uql/viewsheet/internal/ChartVSAssemblyInfo.java` (`seedTypeDefaults`, `reseedIfResolvedTypeChanged`, new methods after `:328`)
- Modify: `report/composition/graph/GraphGenerator.java:2668`
- Modify: `web/graph/model/dialog/AxisPropertyDialogModel.java:70`
- Create: `core/src/test/java/inetsoft/uql/viewsheet/graph/AxisDescriptorLineFlagTest.java`, `core/src/test/java/inetsoft/uql/viewsheet/internal/VSChartTypeDefaultsTest.java`, `core/src/test/java/inetsoft/uql/viewsheet/internal/ChartChromeTypeSeedTest.java`
- Test: `core/src/test/java/inetsoft/uql/viewsheet/internal/SeedChromeDefaultsTest.java` (`aBookmarkTakenWhileModernAndRestoredAfterRevertDrawsClassic`)

**Interfaces:**
- Consumes: `seedTypeDefaults`, `reseedIfResolvedTypeChanged`, `ResolvedChartType` (S0).
- Produces:
  - `public final class DerivedUserFlag` (`inetsoft.uql.viewsheet.graph`), for every M4 flag. `derived` is "the value differs from every classic creation default of its field".
    - `public static boolean parse(String attribute, boolean derived)`: `attribute == null ? derived : "true".equals(attribute)`.
    - `public static void write(PrintWriter writer, String name, boolean flag, boolean derived)`: prints ` name="flag"` only when `flag != derived`. An unedited old file therefore writes what it always wrote, and a seeded value that would derive as the author's is written explicitly.
  - `AxisDescriptor`, attribute `userLineVisible`, derived `!lineVisible` (the radar label descriptor's `false`, `RadarVSChartInfo:43`, derives as the author's; no seed writes it):
    - `public boolean isUserLineVisible()`, `public void setUserLineVisible(boolean)`.
    - `public void setAuthorLineVisible(boolean lineVisible)`: sets the value and stamps the flag.
    - `public boolean isMaxModeLineVisible(VizContext ctx)`: `ctx.modern ? maxModeLineVisible && lineVisible : maxModeLineVisible`.
  - `final class VSChartTypeDefaults` (package-private, static only): `static boolean valueAxisLineVisible(ResolvedChartType t)` = `!GraphTypes.isRect(t.type()) || GraphTypes.isGantt(t.type()) || t.measureOnX() && t.measureOnY()`. Non-rect types and Gantt keep the classic line, so a type change back restores spokes and the Gantt time axis.
  - `ChartVSAssemblyInfo`:
    - `private void seedAxisLines(VizContext ctx, ResolvedChartType type)`, called from `seedTypeDefaults`. With `v = ctx.modern ? VSChartTypeDefaults.valueAxisLineVisible(type) : true`, while the descriptor's flag is false it writes `getAxisDescriptor()` and `getAxisDescriptor2()` ← `v`, and every ref of `getXFields()`, `getYFields()`, `getRTXFields()`, `getRTYFields()` ← `ref.isMeasure() ? v : true`. Null-safe. Not the radar label descriptor.
    - `public void reseedAxisLines()`: on a marked chart only, `seedAxisLines(VizContext.of(this), ResolvedChartType.of(getVSChartInfo()))`.
    - `reseedIfResolvedTypeChanged()`: right after it runs `seedTypeDefaults`, `setRTChartDescriptor(null)`, so the next render clones the seeded design descriptor (C8). Not on the record-only path.
  - `GraphGenerator:2668` reads `axisD.isMaxModeLineVisible(vizContext)`; `AxisPropertyDialogModel:70` shows `axisDesc.isMaxModeLineVisible(ctx)` in max mode.

- [ ] **Step 1: Write the failing tests**

`AxisDescriptorLineFlagTest` (standard context; `xml(AxisDescriptor)` and `parse(String)` helpers as `ChartGapPersistenceTest`):
- `anOldFilesLineOffIsTheAuthors`: a descriptor written with `setLineVisible(false)`, its `userLineVisible` attribute removed from the XML (an older build's file), parses with `isUserLineVisible()` true.
- `anOldFilesLineOnIsNoOpinion`: the same with `true`; flag false.
- `aPresentFlagIsReadAsItIs`: `lineVisible="false"` with `userLineVisible="false"` parses as false.
- `anUneditedOldFileWritesWhatItAlwaysWrote`: for `lineVisible` true and false, an old-file XML parsed and written again contains no `userLineVisible`.
- `aSeededLineOffKeepsItsFlagThroughASave`: `setLineVisible(false)` writes `userLineVisible="false"`; re-parsed, flag false.
- `anAuthorsLineOnIsWritten`: `setAuthorLineVisible(true)` writes `userLineVisible="true"`; re-parsed, flag true.
- `aCopyKeepsTheFlag`: `clone()` keeps it.
- `maxModeFollowsTheLineUnderModern`:

```java
AxisDescriptor d = new AxisDescriptor();
d.setLineVisible(false);
assertFalse(d.isMaxModeLineVisible(VizContext.of(VizMark.MODERN_LIGHT)));
assertTrue(d.isMaxModeLineVisible(VizContext.LEGACY), "classic reads the stored max-mode value");
d.setLineVisible(true);
d.setMaxModeLineVisible(false);
assertFalse(d.isMaxModeLineVisible(VizContext.of(VizMark.MODERN_LIGHT)), "a stored false is the author's");
```

`VSChartTypeDefaultsTest` (pure, `@Tag("core")`; `rt(type, x, y)` builds `new ResolvedChartType(type, x, y, List.of(), List.of())`):
- `oneContinuousAxisLosesItsLine`: false for `rt(t, false, true)` and `rt(t, true, false)` with t in BAR, BAR_STACK, LINE, AREA, STEP, INTERVAL, WATERFALL, PARETO, BOXPLOT, CANDLE, STOCK, MEKKO, FUNNEL, POINT, AUTO.
- `twoContinuousAxesKeepTheirLines`: true for POINT and SCATTER_CONTOUR with `(true, true)`.
- `nonRectTypesAndGanttKeepTheClassicLine`: true for PIE, DONUT, RADAR, FILL_RADAR, TREEMAP, SUNBURST, TREE, NETWORK, CIRCULAR, MAP, GANTT.

`ChartChromeTypeSeedTest` (standard context, harness above; `state` dimension, `Sum(v)`, `Sum(w)` measures):
- `aModernBarChartsValueAxisHasNoLine`: gate on, BAR, X `state`, Y `Sum(v)`, seed. Chart-level and secondary descriptors false; `state`'s true; `Sum(v)`'s own false; no flag set anywhere.
- `aHorizontalBarsValueAxisHasNoLine`: X `Sum(v)`, Y `state`: chart-level false, `state` true.
- `aSeparatedChartsMeasureAxesHaveNoLine`: `setSeparatedGraph(true)`, two Y measures: each measure's own descriptor false.
- `aScatterKeepsBothLines`: POINT, X `Sum(w)`, Y `Sum(v)`: chart-level and `Sum(w)`'s descriptor true.
- `aClassicChartKeepsEveryLine`: gate off, the bar above: every descriptor true, every flag false.
- `anAuthorsLinesSurviveModernizeAndRevert`: classic bar; `state`'s `setAuthorLineVisible(false)`, chart-level `setAuthorLineVisible(true)`; Modernize, then Revert: still false and true.
- `anOldFilesLineOffSurvivesModernizeAndRevert`: classic bar with `state`'s `setLineVisible(false)`, written, its `userLineVisible` attribute removed, parsed into a fresh info; Modernize, then Revert: false.
- `modernizeThenRevertRestoresEveryLine`: classic bar, Modernize, Revert: every descriptor true, every flag false.
- `aMeasureDroppedOnAMarkedChartIsSeeded` (M5, render harness): a marked, rendered bar; add `Sum(w)` to Y; render. `Sum(w)`'s design and runtime descriptors are false.
- `aTypeReseedDropsTheRuntimeDescriptor`: a marked bar with `setRTChartDescriptor(new ChartDescriptor())`; `cinfo.setChartType(CHART_LINE)`; `reseedIfResolvedTypeChanged()`; `getRTChartDescriptor()` is null. The same on an unmarked chart leaves it non-null.

In `SeedChromeDefaultsTest.aBookmarkTakenWhileModernAndRestoredAfterRevertDrawsClassic`, add `assertTrue(chart.getVSChartInfo().getAxisDescriptor().isLineVisible())`.

- [ ] **Step 2: Run to verify they fail**

Run: `./mvnw -q test -pl core "-Dtest=AxisDescriptorLineFlagTest,VSChartTypeDefaultsTest,ChartChromeTypeSeedTest,SeedChromeDefaultsTest" -Dsurefire.failIfNoSpecifiedTests=false`
Expected: test-compile failure (`DerivedUserFlag`, `VSChartTypeDefaults`, `isUserLineVisible`, `setAuthorLineVisible`, `isMaxModeLineVisible(VizContext)` not found).

- [ ] **Step 3: Implement** everything under Interfaces. The `userLineVisible` parse runs after `maxModeLineVisible` (`:685-692`), so `lineVisible` is known.

- [ ] **Step 4: Run to verify they pass**

Run: `./mvnw -q test -pl core "-Dtest=AxisDescriptorLineFlagTest,VSChartTypeDefaultsTest,ChartChromeTypeSeedTest,SeedChromeDefaultsTest,ChartDescriptorSavedDefaultsTest,ResolvedTypeReseedTest,AxisPropertyDialogModelTest,BookmarkChromeResolutionTest,VizModernizeUtilTest" -Dsurefire.failIfNoSpecifiedTests=false`
Expected: all pass. Then run `ChartChromeTypeSeedTest` alone.

- [ ] **Step 5: Commit**

```bash
git -C "$WT" add core/src/main/java/inetsoft/uql/viewsheet/graph/DerivedUserFlag.java core/src/main/java/inetsoft/uql/viewsheet/internal/VSChartTypeDefaults.java core/src/main/java/inetsoft/uql/viewsheet/graph/AxisDescriptor.java core/src/main/java/inetsoft/uql/viewsheet/internal/ChartVSAssemblyInfo.java core/src/main/java/inetsoft/report/composition/graph/GraphGenerator.java core/src/main/java/inetsoft/web/graph/model/dialog/AxisPropertyDialogModel.java core/src/test/java/inetsoft/uql/viewsheet/graph/AxisDescriptorLineFlagTest.java core/src/test/java/inetsoft/uql/viewsheet/internal/VSChartTypeDefaultsTest.java core/src/test/java/inetsoft/uql/viewsheet/internal/ChartChromeTypeSeedTest.java core/src/test/java/inetsoft/uql/viewsheet/internal/SeedChromeDefaultsTest.java
```
```bash
git -C "$WT" commit -m "Chart defaults S1, turn off the value-axis line on modern charts behind an author flag" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 3: Author paths stamp the line flag

S1-4 stamping paths.

**Files:**
- Modify: `web/graph/model/dialog/AxisPropertyDialogModel.java:267-272`
- Modify: `web/viewsheet/controller/chart/VSChartAxesVisibilityService.java` (`:140`, `:161-222`)
- Modify: `report/script/AxisScriptable.java:164-165`
- Modify: `report/internal/graph/ChangeSeparateStatusProcessor.java:314-316` (and `copyAxisAttributes` `:293` becomes package-private)
- Test: `core/src/test/java/inetsoft/web/graph/model/dialog/AxisPropertyDialogModelTest.java`, `core/src/test/java/inetsoft/report/script/AxisScriptableTest.java`
- Create: `core/src/test/java/inetsoft/web/viewsheet/controller/chart/VSChartAxesVisibilityServiceTest.java`, `core/src/test/java/inetsoft/report/internal/graph/ChangeSeparateStatusProcessorLineFlagTest.java`

**Interfaces:**
- Consumes: `setAuthorLineVisible`, `setUserLineVisible`, `isMaxModeLineVisible(VizContext)`, `reseedAxisLines()` (Task 2).
- Produces, in `VSChartAxesVisibilityService` (package-private static, so they test without the controller):
  - `static void setVisible(AxisDescriptor axis, boolean visible, boolean maxMode, boolean modern)`.
    - Max mode: writes `maxModeLineVisible` and `maxModeLabelVisible` as today.
    - Hide (normal mode): `setAuthorLineVisible(false)` and labels false, on both chart kinds.
    - Show (normal mode): classic as today. Modern: `setUserLineVisible(false)` and labels true; the line is left for the re-seed.
  - `static void showAllAxes(ChartVSAssemblyInfo info, VSChartInfo chartInfo, boolean maxMode)`: walks the same descriptors as today (`:185-210`), then calls `info.reseedAxisLines()` when `info.getVizMark() != null` and not max mode.
- Dialog, normal mode: set the line; stamp when it changed. Max mode, classic: as today. Max mode, modern: write only when the new value differs from the shown `isMaxModeLineVisible(ctx)`; turning it on while `lineVisible` is false also calls `setAuthorLineVisible(true)`, because max mode follows normal.
- Script: `lineVisible` is registered with setter `setAuthorLineVisible`. The property name is unchanged.
- `copyAxisAttributes` copies `isUserLineVisible()` with the line.

- [ ] **Step 1: Write the failing tests**

`AxisPropertyDialogModelTest`:
- `anUnchangedLineDoesNotStampTheFlag`: apply with the shown value; flag false.
- `turningTheLineOffStampsTheFlag`.
- `aModernMaxModeApplyThatChangesNothingWritesNothing`: modern ctx, line false, max true (shown false); apply false; max still true, flag false.
- `turningTheLineOnInModernMaxModeTurnsItOnEverywhere`: the same descriptor, apply true; line true, max true, flag true.
- `aClassicMaxModeApplyIsUnchanged`: legacy ctx; apply false; max false, line and flag untouched.

`VSChartAxesVisibilityServiceTest` (standard context, a marked or classic bound bar from `ChartChromeTypeSeedTest`'s builders, seeded):
- `hidingAnAxisStampsItsLine`: both chart kinds; `setVisible(d, false, false, modern)` gives line false, labels false, flag true.
- `hidingInMaxModeLeavesTheLineAndFlagAlone`.
- `showAllOnAModernChartRestoresTheSeededLines`: hide the value axis and `state`'s; `showAllAxes(info, cinfo, false)`. Value axis line false, `state` line true, labels true, flags false.
- `showAllOnAClassicChartTurnsEveryLineOn`: as today.
- `aRevertAfterHidingKeepsTheAxisHidden`: marked; hide the value axis; Revert. Line false and labels false.

`AxisScriptableTest.scriptLineVisibleStampsTheAuthorFlag`: set up as `testInitWithChartDimensionRef` (`:81-100`) but with a real `AxisDescriptor` on the ref; `putMember("lineVisible", false)`. The descriptor's line is false and its flag true.

`ChangeSeparateStatusProcessorLineFlagTest`: `new ChangeSeparateStatusProcessor(new DefaultVSChartInfo(), new ChartDescriptor()).copyAxisAttributes(target, source)` with a seeded source (line false, flag false) and with an authored one (flag true); the target's flag matches.

- [ ] **Step 2: Run to verify they fail**

Run: `./mvnw -q test -pl core "-Dtest=AxisPropertyDialogModelTest,VSChartAxesVisibilityServiceTest,AxisScriptableTest,ChangeSeparateStatusProcessorLineFlagTest" -Dsurefire.failIfNoSpecifiedTests=false`
Expected: test-compile failure (static `setVisible`/`showAllAxes`, package-private `copyAxisAttributes`), then assertion failures.

- [ ] **Step 3: Implement** as under Interfaces. The controller's private callers (`:140`, `:172-178`) pass `VizContext.of(info).modern` and `info`.

- [ ] **Step 4: Run to verify they pass**

Run: the Step 2 command, then each new class alone.
Expected: all pass.

- [ ] **Step 5: Commit**

```bash
git -C "$WT" add core/src/main/java/inetsoft/web/graph/model/dialog/AxisPropertyDialogModel.java core/src/main/java/inetsoft/web/viewsheet/controller/chart/VSChartAxesVisibilityService.java core/src/main/java/inetsoft/report/script/AxisScriptable.java core/src/main/java/inetsoft/report/internal/graph/ChangeSeparateStatusProcessor.java core/src/test/java/inetsoft/web/graph/model/dialog/AxisPropertyDialogModelTest.java core/src/test/java/inetsoft/report/script/AxisScriptableTest.java core/src/test/java/inetsoft/web/viewsheet/controller/chart/VSChartAxesVisibilityServiceTest.java core/src/test/java/inetsoft/report/internal/graph/ChangeSeparateStatusProcessorLineFlagTest.java
```
```bash
git -C "$WT" commit -m "Chart defaults S1, stamp the axis line flag on author edits" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 4: Facet panel separators survive a seeded-off line

S1-4 facet check, C5.

**Files:**
- Modify: `report/composition/graph/GraphGenerator.java` (`setupAxisSpec` `:2652-2668`; `setAxisGridStyle` `:4259-4316`; `getLineColor` `:4326-4329`; `isAxisLineVisible` `:4334-4361`; new field beside `vizContext` `:7656`)
- Create: `core/src/test/java/inetsoft/report/composition/graph/ChartChromeRenderTest.java`

**Interfaces:**
- Consumes: `isUserLineVisible()`, `isMaxModeLineVisible(VizContext)` (Task 2).
- Produces (generator-internal):
  - `private final Set<AxisSpec> seededOffLines`, an identity set.
  - `setupAxisSpec` adds `axis` when `vizContext.modern && !axisD.isUserLineVisible() && !axisD.isLineVisible() && (!maxMode || axisD.isMaxModeLineVisible())`, so it records a line off only because of the seed.
  - The three `isAxisLineVisible` methods become instance methods. The `Scale` one returns `spec.isLineVisible() || seededOffLines.contains(spec)`. `getLineColor` filters with the same predicate.
  - Classic: the set stays empty, so the decision is unchanged.

- [ ] **Step 1: Write the failing tests**

`ChartChromeRenderTest` (standard context). A faceted bar: X `Region`, `State` dimensions, Y `Sum(Total)`; `outerX(EGraph g)` is `((RectCoord) ((FacetCoord) g.getCoordinate()).getOuterCoordinate()).getXScale()`.
- `aModernFacetKeepsItsPanelSeparators`: marked; chart-level descriptor `setLineVisible(false)` (as the seed leaves it). `outerX(...).getAxisSpec().getGridStyle()` is `THIN_LINE`, the same as the unmarked chart with the line on.
- `anAuthorHiddenValueLineStillRemovesThem`: marked, `setAuthorLineVisible(false)`: `NONE`.
- `aClassicFacetIsUnchanged`: unmarked: line false gives `NONE`, line true gives `THIN_LINE`.

- [ ] **Step 2: Run to verify they fail**

Run: `./mvnw -q test -pl core -Dtest=ChartChromeRenderTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: FAIL in `aModernFacetKeepsItsPanelSeparators` (`NONE`).

- [ ] **Step 3: Implement** as under Interfaces. If the spec object recorded in `setupAxisSpec` is not the one the facet code reads, record it where it is installed on its scale. Do not loosen the test.

- [ ] **Step 4: Run to verify they pass**

Run: `./mvnw -q test -pl core "-Dtest=ChartChromeRenderTest,AxisTitleGapTest,LegendPaddingRenderTest" -Dsurefire.failIfNoSpecifiedTests=false`
Expected: all pass.

- [ ] **Step 5: Commit**

```bash
git -C "$WT" add core/src/main/java/inetsoft/report/composition/graph/GraphGenerator.java core/src/test/java/inetsoft/report/composition/graph/ChartChromeRenderTest.java
```
```bash
git -C "$WT" commit -m "Chart defaults S1, keep facet panel separators when the value-axis line is seeded off" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 5: Axis titles off by resolved type

S1-2 seed, registration, max-mode read, C3, C4.

**Files:**
- Modify: `uql/viewsheet/graph/TitleDescriptor.java` (accessors after `:154`; `writeXML` `:288-303`; `parseXML` `:331-334`; field after `:399`)
- Modify: `uql/viewsheet/graph/TitlesDescriptor.java`, `uql/viewsheet/graph/ChartDescriptor.java` (`savedDefaults`)
- Modify: `uql/viewsheet/internal/VSChartTypeDefaults.java`, `uql/viewsheet/internal/ChartVSAssemblyInfo.java`
- Modify: `report/composition/graph/GraphGenerator.java:6306`, `:6328`; `web/graph/GraphBuilder.java:1704-1757`
- Create: `core/src/test/java/inetsoft/uql/viewsheet/graph/TitleDescriptorUserTitleTest.java`, `core/src/test/java/inetsoft/web/graph/GraphBuilderTitleHiddenTest.java`
- Test: `VSChartTypeDefaultsTest`, `ChartChromeTypeSeedTest`, `SeedChromeDefaultsTest`

**Interfaces:**
- Consumes: `DerivedUserFlag` (Task 2); `ChartDescriptor.savedDefaults()` (S0).
- Produces:
  - `TitleDescriptor`:
    - `public boolean hasVisibleUserValue()`.
    - `public boolean isUserTitle()`, `public void setUserTitle(boolean)`: the custom text is the author's. Attribute `userTitle`; derived `getTitleValue()` non-empty; parsed in `parseXML` after `parseContents`. `setTitleValue` never touches it, so the wizard's text stays no opinion.
    - `public boolean isMaxModeVisible(VizContext ctx)`: `ctx.modern ? maxModeVisible && isVisible() : maxModeVisible`.
    - `List<CompositeValue<?>> savedDefaults()` (package-private): `List.of(visible)`. `TitlesDescriptor.savedDefaults()` concatenates x, x2, y, y2; `ChartDescriptor.savedDefaults()` appends it, null-safe.
  - `VSChartTypeDefaults.axisTitleVisible(int type, String axis)`, axis one of `"x"`, `"x2"`, `"y"`, `"y2"`: true for every axis when `GraphTypes.isPoint`, `isScatteredContour`, `isPareto` or `isMekko`; for `"y"` and `"y2"` when `isBoxplot`; false otherwise.
  - `ChartVSAssemblyInfo.seedAxisTitles(VizContext ctx, ResolvedChartType type)` (private, from `seedTypeDefaults`), for each of the four titles at DEFAULT: modern `axisTitleVisible(type.type(), axis) || title.isUserTitle()`; legacy `true`.
  - `GraphGenerator:6306`, `:6328` read `titleDesc.isMaxModeVisible(vizContext)`.
  - `GraphBuilder.isTitleHidden(ChartDescriptor, ChartInfo, boolean maxMode, VizContext ctx)` (`:1708`, no outside callers) passes `ctx` to the private overload (`:1750`), which reads `title.isMaxModeVisible(ctx)`. The instance caller (`:1704-1706`) passes `VizContext.of(assembly.getVSAssemblyInfo())`.

- [ ] **Step 1: Write the failing tests**

`TitleDescriptorUserTitleTest` (standard context):
- `anOldFilesTypedTitleIsTheAuthors`: `setTitleValue("Revenue")`, written, `userTitle="false"` removed from the XML, parsed: flag true.
- `aSystemTitleStaysNoOpinionThroughASave`: `setTitleValue("Frequency")` writes `userTitle="false"`; parsed, flag false.
- `aTitleWithoutTextWritesNoFlag`: a fresh descriptor's XML has no `userTitle`.
- `maxModeFollowsVisibilityUnderModern`: DEFAULT false: `isMaxModeVisible(modern)` false and `isMaxModeVisible(VizContext.LEGACY)` true. USER true: modern true. `setMaxModeVisible(false)`: false on both.

`VSChartTypeDefaultsTest.axisTitlesFollowTheType`: the on-list above (both axes for POINT, POINT_STACK, SCATTER_CONTOUR, PARETO, MEKKO; Y only for BOXPLOT); off for every axis of BAR, LINE, AREA, INTERVAL, WATERFALL, CANDLE, STOCK, PIE, DONUT, RADAR, FUNNEL, GANTT, TREEMAP, TREE, MAP, AUTO.

`ChartChromeTypeSeedTest`:
- `modernTitlesFollowTheResolvedType`: one type per family, bound and seeded under the gate. Each title's DEFAULT equals `axisTitleVisible`, and none has a USER value.
- `aClassicChartShowsEveryTitle`: gate off; the same types; all four true.
- `aTypedTitleFromAnOldFileStaysVisibleThroughModernizeAndRevert` (Review Focus 1): classic bar, x title `setTitleValue("Revenue")`, written, `userTitle="false"` removed, parsed into a fresh info. Modernize: x visible, y hidden. Revert: both visible.
- `wizardTextDoesNotKeepATitle`: marked bar; y title `setTitleValue("Frequency")`; seed; y hidden. `grep -n "setTitleValue" core/src/main/java/inetsoft/web/vswizard/handler/VSWizardBindingHandler.java` still shows `:916-917` using `setTitleValue`.
- `aTitleTheAuthorShowedSurvivesAReseed`: marked bar, x `setVisible(true)` (USER); `cinfo.setChartType(CHART_LINE)`; `reseedIfResolvedTypeChanged()`; x visible.
- `modernizeThenRevertRestoresEveryTitle`: classic bar, Modernize, Revert: all four DEFAULT true.
- `anAutoChartThatBecomesAPointChartGainsItsTitles` (§11, render harness): a marked AUTO chart with X `state`, Y `Sum(v)` is rendered with titles hidden. X becomes `Sum(w)`; render; x and y DEFAULT true.

`SeedChromeDefaultsTest`:
- `theSeededAxisTitleVisibilityTravelsInTheAsset`: gate on; `newChart()`; `roundTrip`; the x title's DEFAULT is false.
- In the modern-bookmark test: the x and y titles are visible after Revert.

`GraphBuilderTitleHiddenTest`: a chart descriptor whose four titles are DEFAULT false and whose max-mode values are stored true, with a chart info carrying an X and a Y field. `isTitleHidden(desc, cinfo, true, VizContext.of(VizMark.MODERN_LIGHT))` is true; with `VizContext.LEGACY` it is false.

- [ ] **Step 2: Run to verify they fail**

Run: `./mvnw -q test -pl core "-Dtest=TitleDescriptorUserTitleTest,VSChartTypeDefaultsTest,ChartChromeTypeSeedTest,SeedChromeDefaultsTest,GraphBuilderTitleHiddenTest" -Dsurefire.failIfNoSpecifiedTests=false`
Expected: test-compile failure (`isUserTitle`, `isMaxModeVisible(VizContext)`, `axisTitleVisible`, the four-argument `isTitleHidden`).

- [ ] **Step 3: Implement** as under Interfaces.

- [ ] **Step 4: Run to verify they pass**

Run: the Step 2 classes plus `ChartDescriptorSavedDefaultsTest,ResolvedTypeReseedTest,AxisTitleGapTest,BookmarkChromeResolutionTest,VizModernizeUtilTest,CSSChartStylesModernChromeTest`.
Expected: all pass. `ChartDescriptorSavedDefaultsTest` now covers the four title fields with no edit (S0).

- [ ] **Step 5: Commit**

```bash
git -C "$WT" add core/src/main/java/inetsoft/uql/viewsheet/graph/TitleDescriptor.java core/src/main/java/inetsoft/uql/viewsheet/graph/TitlesDescriptor.java core/src/main/java/inetsoft/uql/viewsheet/graph/ChartDescriptor.java core/src/main/java/inetsoft/uql/viewsheet/internal/VSChartTypeDefaults.java core/src/main/java/inetsoft/uql/viewsheet/internal/ChartVSAssemblyInfo.java core/src/main/java/inetsoft/report/composition/graph/GraphGenerator.java core/src/main/java/inetsoft/web/graph/GraphBuilder.java core/src/test/java/inetsoft/uql/viewsheet/graph/TitleDescriptorUserTitleTest.java core/src/test/java/inetsoft/web/graph/GraphBuilderTitleHiddenTest.java core/src/test/java/inetsoft/uql/viewsheet/internal/VSChartTypeDefaultsTest.java core/src/test/java/inetsoft/uql/viewsheet/internal/ChartChromeTypeSeedTest.java core/src/test/java/inetsoft/uql/viewsheet/internal/SeedChromeDefaultsTest.java
```
```bash
git -C "$WT" commit -m "Chart defaults S1, seed axis title visibility by resolved chart type" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 6: Author and system paths for axis titles

S1-2 typed-title rule, map titles, show-all in max mode.

**Files:**
- Modify: `uql/viewsheet/graph/TitleDescriptor.java`
- Modify: `web/graph/model/dialog/TitleFormatDialogModel.java:83-92`
- Modify: `report/script/TitleScriptable.java:60`, `report/script/ChartProcessor.java:310-317`
- Modify: `report/internal/graph/ChangeChartTypeProcessor.java:588-605`
- Modify: `web/viewsheet/controller/chart/VSChartTitlesVisibilityService.java:211-256`
- Test: `TitleDescriptorUserTitleTest`, `core/src/test/java/inetsoft/web/graph/model/dialog/TitleFormatDialogModelTest.java`
- Create: `core/src/test/java/inetsoft/web/viewsheet/controller/chart/VSChartTitlesVisibilityServiceTest.java`, `core/src/test/java/inetsoft/report/internal/graph/ChangeChartTypeProcessorMapTitleTest.java`

**Interfaces:**
- Consumes: `isUserTitle`, `hasVisibleUserValue` (Task 5).
- Produces:
  - `TitleDescriptor` (both setters stamp USER visible `true` only when the text is non-empty and the title is hidden by its seed alone: no USER value, no CSS value, DEFAULT false. A classic title's DEFAULT is always true, so classic visibility never moves):
    - `public void setAuthorTitleValue(String title)`: `setTitleValue(title)`, `userTitle` = non-empty, then the stamp.
    - `public void setAuthorTitle(String title)`: `setTitle(title)` (runtime), then the stamp. Scripts run on the runtime descriptor (`ChartVSAScriptable:716-729`), so nothing is saved.
  - `TitleFormatDialogModel.updateTitleProperties` calls `setAuthorTitleValue` at `:87` and `:90`.
  - The script `text` properties (`TitleScriptable:60`; `ChartProcessor` `xTitle.text` … `y2Title.text`, `:310-317`) use setter `setAuthorTitle`. Property names are unchanged.
  - `ChangeChartTypeProcessor`: `static void setMapTitlesVisible(ChartDescriptor desc, boolean visible, VizContext ctx)` (package-private). It writes the four titles at DEFAULT when `ctx.modern`, at USER otherwise; `changeToMap` and `changeFromMap` call it with the processor's `vizContext` (`:1654`). Under modern the per-type seed then decides.
  - `VSChartTitlesVisibilityService`: `showTitles` and `showDescriptorTitles` become package-private static. `showTitles(info, true)` on a marked chart also writes normal visibility (USER `true`), because max mode follows it under modern.

- [ ] **Step 1: Write the failing tests**

`TitleDescriptorUserTitleTest`:
- `typingATitleMarksItTheAuthors`: `setAuthorTitleValue("Revenue")`: flag true; the XML has no `userTitle` (derived); parsed, flag true.
- `typingOverASeededHiddenTitleShowsIt`: DEFAULT false; `setAuthorTitleValue("Revenue")`: visible, `hasVisibleUserValue()`.
- `typingNeverShowsATitleTheAuthorHid`: `setVisible(false)` (USER); typing leaves it false.
- `typingOnAClassicTitleWritesNoVisibility`: DEFAULT true; typing gives `hasVisibleUserValue()` false.
- `aStylesheetHiddenTitleStaysHidden`: DEFAULT false, CSS false; typing leaves it hidden with no USER value.
- `clearingTheTextClearsTheFlag`: `setAuthorTitleValue("")`.
- `scriptTextShowsASeededHiddenTitle`: DEFAULT false; `setAuthorTitle("abc")`: visible.

`TitleFormatDialogModelTest.typingATitleInTheDialogMarksItTheAuthors`: a modern-seeded hidden title; set the pane title and apply; flag true, visible.

`VSChartTitlesVisibilityServiceTest` (marked and classic bound bars from `ChartChromeTypeSeedTest`'s builders):
- `showAllTitlesInMaxModeShowsThemOnAModernChart` (Review Focus 2): `showTitles(info, true)`; `isMaxModeVisible(modern)` true for x and y.
- `showAllTitlesInMaxModeOnAClassicChartWritesMaxModeOnly`: no USER value is written.

`ChangeChartTypeProcessorMapTitleTest`: modern ctx, `setMapTitlesVisible(desc, false, ctx)`: four titles DEFAULT false, no USER value. Legacy ctx: USER false, as today.

- [ ] **Step 2: Run to verify they fail**

Run: `./mvnw -q test -pl core "-Dtest=TitleDescriptorUserTitleTest,TitleFormatDialogModelTest,VSChartTitlesVisibilityServiceTest,ChangeChartTypeProcessorMapTitleTest" -Dsurefire.failIfNoSpecifiedTests=false`
Expected: test-compile failure, then assertion failures.

- [ ] **Step 3: Implement** as under Interfaces.

- [ ] **Step 4: Run to verify they pass**

Run: the Step 2 classes plus `ChartChromeTypeSeedTest,ChartVSAScriptableTest,ChangeChartTypeServiceTest`.
Expected: all pass.

- [ ] **Step 5: Commit**

```bash
git -C "$WT" add core/src/main/java/inetsoft/uql/viewsheet/graph/TitleDescriptor.java core/src/main/java/inetsoft/web/graph/model/dialog/TitleFormatDialogModel.java core/src/main/java/inetsoft/report/script/TitleScriptable.java core/src/main/java/inetsoft/report/script/ChartProcessor.java core/src/main/java/inetsoft/report/internal/graph/ChangeChartTypeProcessor.java core/src/main/java/inetsoft/web/viewsheet/controller/chart/VSChartTitlesVisibilityService.java core/src/test/java/inetsoft/uql/viewsheet/graph/TitleDescriptorUserTitleTest.java core/src/test/java/inetsoft/web/graph/model/dialog/TitleFormatDialogModelTest.java core/src/test/java/inetsoft/web/viewsheet/controller/chart/VSChartTitlesVisibilityServiceTest.java core/src/test/java/inetsoft/report/internal/graph/ChangeChartTypeProcessorMapTitleTest.java
```
```bash
git -C "$WT" commit -m "Chart defaults S1, keep typed axis titles visible and route map titles through the seed" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 7: Vertical grid for continuous X

S1-3, C2.

**Files:**
- Modify: `uql/viewsheet/graph/PlotDescriptor.java` (`savedDefaults`: add `xGridStyle`)
- Modify: `uql/viewsheet/internal/VSChartTypeDefaults.java`, `uql/viewsheet/internal/ChartVSAssemblyInfo.java`
- Test: `VSChartTypeDefaultsTest`, `ChartChromeTypeSeedTest`, `SeedChromeDefaultsTest`

**Interfaces:**
- Consumes: `seedTypeDefaults`, `ResolvedChartType` (S0).
- Produces:
  - `VSChartTypeDefaults.xGridStyle(ResolvedChartType t)`: `StyleConstants.THIN_LINE` when `(GraphTypes.isPoint(t.type()) || GraphTypes.isScatteredContour(t.type())) && t.measureOnX() && t.measureOnY()`, else `StyleConstants.NONE`.
  - `ChartVSAssemblyInfo.seedXGrid(VizContext ctx, ResolvedChartType type)` (private, from `seedTypeDefaults`): `setXGridStyle(ctx.modern ? xGridStyle(type) : StyleConstants.NONE, CompositeValue.Type.DEFAULT)`. The format dialog shows the stored value (`ChartLinePaneModel:140-161`).

- [ ] **Step 1: Write the failing tests**

`VSChartTypeDefaultsTest.theXGridFollowsTwoContinuousAxes`: THIN for POINT, POINT_STACK and SCATTER_CONTOUR with `(true, true)`. NONE for POINT with `(false, true)` and `(true, false)`, and for BAR, LINE, FUNNEL and MAP with `(true, true)`.

`ChartChromeTypeSeedTest`:
- `aModernScatterGetsAVerticalGrid`: POINT, X `Sum(w)`, Y `Sum(v)`: THIN.
- `aPointChartWithADimensionOnXGetsNone`.
- `aHorizontalPointChartGetsNoCategoryLines` (Review Focus 4): X `Sum(v)`, Y `state`: NONE.
- `aClassicScatterKeepsNone`: gate off.
- `anAuthorsGridSurvivesAReseed`: marked bar; `setXGridStyle(THIN_LINE)` (USER); type to LINE; `reseedIfResolvedTypeChanged()`; still THIN.
- `modernizeThenRevertRestoresTheXGrid`: classic scatter, Modernize (THIN), Revert (NONE).
- Rename `anAutoChartThatBecomesAPointChartGainsItsTitles` to `...GainsItsTitlesAndXGrid`, and add that X **and** Y measures give THIN.

`SeedChromeDefaultsTest`:
- `theSeededXGridTravelsInTheAsset`: a marked scatter round trip keeps DEFAULT THIN.
- `aScatterBookmarkTakenWhileModernAndRestoredAfterRevertHasNoXGrid`: as the S0 bookmark test, with a scatter binding; NONE after restore.

- [ ] **Step 2: Run to verify they fail**

Run: `./mvnw -q test -pl core "-Dtest=VSChartTypeDefaultsTest,ChartChromeTypeSeedTest,SeedChromeDefaultsTest" -Dsurefire.failIfNoSpecifiedTests=false`
Expected: test-compile failure (`xGridStyle`).

- [ ] **Step 3: Implement** the predicate, the seed and the registration in this one change, since a registered field needs its legacy write (S0's `aLegacyPassRestoresEveryRegisteredDefault`).

- [ ] **Step 4: Run to verify they pass**

Run: the Step 2 classes plus `ChartDescriptorSavedDefaultsTest,CSSChartStylesModernChromeTest,VizModernizeUtilTest`.
Expected: all pass.

- [ ] **Step 5: Commit**

```bash
git -C "$WT" add core/src/main/java/inetsoft/uql/viewsheet/graph/PlotDescriptor.java core/src/main/java/inetsoft/uql/viewsheet/internal/VSChartTypeDefaults.java core/src/main/java/inetsoft/uql/viewsheet/internal/ChartVSAssemblyInfo.java core/src/test/java/inetsoft/uql/viewsheet/internal/VSChartTypeDefaultsTest.java core/src/test/java/inetsoft/uql/viewsheet/internal/ChartChromeTypeSeedTest.java core/src/test/java/inetsoft/uql/viewsheet/internal/SeedChromeDefaultsTest.java
```
```bash
git -C "$WT" commit -m "Chart defaults S1, seed a vertical grid for scatter charts" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 8: Straight lines by default

S1-8, R3, D27, C7.

**Decision.** The modern smoothing seed moves into `seedTypeDefaults`, because it depends on the resolved type and must re-run when the type changes. Classic smoothing varies by type, so it stays where it is: `legacySmoothLines()` in the legacy branch (`ChartVSAssemblyInfo:190-192`) and the gate-off branch of `applySmoothLinesTransition`. Under modern the transition writes nothing, so the per-type seed (run from `updateAssembly` at `ChangeChartTypeService:211`) decides, and `userSmoothLines` protects an author's choice.

**Files:**
- Modify: `uql/viewsheet/internal/ChartVSAssemblyInfo.java` (remove `:163-165`; new `seedSmoothLines`)
- Modify: `uql/viewsheet/internal/VSChartTypeDefaults.java`
- Modify: `web/binding/controller/ChangeChartTypeService.java:342-366` (Javadoc `:331-341`)
- Modify: `web/vswizard/handler/VSWizardBindingHandler.java:862-877` (and its Javadoc above)
- Test: `ChartChromeTypeSeedTest`, `VSChartTypeDefaultsTest`; flip `core/src/test/java/inetsoft/uql/viewsheet/internal/SeedChromeDefaultsTest.java:997-1004` and `:1310`, `core/src/test/java/inetsoft/uql/viewsheet/internal/ChartVSAssemblyInfoBarRoundingTest.java:105-128`, `core/src/test/java/inetsoft/web/binding/controller/ChangeChartTypeServiceSmoothLinesTransitionTest.java:197-238`, `core/src/test/java/inetsoft/web/vswizard/handler/VSWizardBindingHandlerSmoothLinesTest.java:87-134`

**Interfaces:**
- Consumes: `seedTypeDefaults` (S0).
- Produces:
  - `VSChartTypeDefaults.smoothLines(int type)`: `type == GraphTypes.CHART_CIRCULAR`. Modern line, step, jump, area and step area are straight; circular network keeps its curved chords.
  - `ChartVSAssemblyInfo.seedSmoothLines(VizContext ctx, ResolvedChartType type)` (private, from `seedTypeDefaults`): `if(ctx.modern && !isUserSmoothLines()) setSmoothLines(smoothLines(type.type()))`. No legacy write here.
  - `applySmoothLinesTransition(..., boolean modern)`: returns at once when `modern`. The gate-off matrix is unchanged.
  - `applyWizardSmoothLines`: returns when the chart's `getVizMark() != null`.

- [ ] **Step 1: Change the tests**

These flip:
- `SeedChromeDefaultsTest.gateOnChartTakesItsPlotSeeds` and `theHookModernizesALegacyChartCompletely`: smoothing false (an unbound chart resolves AUTO).
- `ChartVSAssemblyInfoBarRoundingTest`: `smoothLinesSeededUnderGate` becomes `smoothLinesNotSeededUnderGate` (false). `seededSmoothLinesSurviveTheGateTurningOff` binds `CHART_CIRCULAR` before creation, so the seed writes true.
- `ChangeChartTypeServiceSmoothLinesTransitionTest`: the four `*_underGate_*` tests that expect a write become `*_underGate_leavesSmoothingToTheSeed`; the value is unchanged.
- `VSWizardBindingHandlerSmoothLinesTest`: the gate-"true" halves assert a marked chart is left alone (forced false stays false). `wizardCreatedAreaChartEndsUpSmoothRegardlessOfWhichWriterDidIt` under the gate asserts false.

New, in `VSChartTypeDefaultsTest.smoothLinesOnlyForCircular` and `ChartChromeTypeSeedTest`:
- `modernLinesAndAreasAreStraight`: LINE, STEP, JUMP, AREA, STEP_AREA, AREA_STACK seeded: false.
- `aModernCircularNetworkStaysCurved`.
- `aClassicAreaStaysSmooth`: gate off, AREA set as the design type before creation: true.
- `anAuthorsSmoothingSurvivesATypeChange`: marked LINE; `setUserSmoothLines(true)`, `setSmoothLines(true)`; type to AREA; `reseedIfResolvedTypeChanged()`; true.
- `aModernAreaBookmarkRestoredAfterRevertIsSmooth`: as the S0 bookmark test with an AREA design type.

- [ ] **Step 2: Run to verify they fail**

Run: `./mvnw -q test -pl core "-Dtest=SeedChromeDefaultsTest,ChartVSAssemblyInfoBarRoundingTest,ChangeChartTypeServiceSmoothLinesTransitionTest,VSWizardBindingHandlerSmoothLinesTest,VSChartTypeDefaultsTest,ChartChromeTypeSeedTest" -Dsurefire.failIfNoSpecifiedTests=false`
Expected: FAIL in the flipped and new tests (`smoothLines` not found first).

- [ ] **Step 3: Implement** as under Interfaces. Update the Javadocs of `applySmoothLinesTransition` and `applyWizardSmoothLines` to say a marked chart is left to the seed.

- [ ] **Step 4: Run to verify they pass**

Run: the Step 2 classes plus `ChangeChartTypeServiceTest,VizModernizeUtilTest,BookmarkChromeResolutionTest,ChartPlotOptionsPaneModelTest,ChartVSAScriptableTest,PlotDescriptorXmlTest`.
Expected: all pass. `VizModernizeUtilTest:470-482` (classic area, Modernize, Revert) still passes.

- [ ] **Step 5: Commit**

```bash
git -C "$WT" add core/src/main/java/inetsoft/uql/viewsheet/internal/ChartVSAssemblyInfo.java core/src/main/java/inetsoft/uql/viewsheet/internal/VSChartTypeDefaults.java core/src/main/java/inetsoft/web/binding/controller/ChangeChartTypeService.java core/src/main/java/inetsoft/web/vswizard/handler/VSWizardBindingHandler.java core/src/test/java/inetsoft/uql/viewsheet/internal/SeedChromeDefaultsTest.java core/src/test/java/inetsoft/uql/viewsheet/internal/ChartVSAssemblyInfoBarRoundingTest.java core/src/test/java/inetsoft/web/binding/controller/ChangeChartTypeServiceSmoothLinesTransitionTest.java core/src/test/java/inetsoft/web/vswizard/handler/VSWizardBindingHandlerSmoothLinesTest.java core/src/test/java/inetsoft/uql/viewsheet/internal/VSChartTypeDefaultsTest.java core/src/test/java/inetsoft/uql/viewsheet/internal/ChartChromeTypeSeedTest.java
```
```bash
git -C "$WT" commit -m "Chart defaults S1, draw straight lines on modern charts unless the author smooths them" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 9: No legend border and no legend fill

S1-5, R2, C6.

**Files:**
- Modify: `uql/viewsheet/internal/VSChartChromeDefaults.java` (beside `legendBackground` `:64-66`)
- Modify: `uql/viewsheet/graph/LegendsDescriptor.java` (`initDefaultFormat` `:96-106`; `savedDefaults`: add `border`)
- Modify: `uql/viewsheet/graph/LegendDescriptor.java:70-79`
- Modify: `report/composition/graph/VGraphPair.java` (`:1088`, `:1096`, `:1104`, `:1176`, `:1184`, `:1192`, `:1347`)
- Modify: `uql/viewsheet/internal/ChartVSAssemblyInfo.java` (`seedChromeDefaults` body, both branches)
- Test: `core/src/test/java/inetsoft/uql/viewsheet/internal/VSChartChromeDefaultsTest.java`, `SeedChromeDefaultsTest`

**Interfaces:**
- Consumes: `LegendsDescriptor.savedDefaults()` (S0).
- Produces:
  - `VSChartChromeDefaults` (public static):
    - `boolean legendFramed(VizContext ctx, int layout)`: `!ctx.modern || layout == LegendsDescriptor.IN_PLACE`.
    - `int legendBorder(VizContext ctx, int layout)`: framed ? `THIN_LINE` : `NONE`.
    - `Color legendFill(VizContext ctx, int layout)`: framed ? `legendBackground(ctx)` : null.
  - `LegendsDescriptor.initDefaultFormat(ctx)`: background `legendFill(ctx, option)`; border DEFAULT `legendBorder(ctx, option)`; alpha 50 as today.
  - `LegendDescriptor.initDefaultFormat(VizContext ctx, int layout)`: background `legendFill(ctx, layout)`. The one-argument overload passes `LegendsDescriptor.RIGHT`.
  - `VGraphPair` passes the owning `LegendsDescriptor`'s `getLayout()` at the seven call sites; `:1347` uses the design descriptor's legends.
  - The seed writes `setBorder(legendBorder(ctx, legends.getLayout()), CompositeValue.Type.DEFAULT)` on both branches, so a re-seed without a render (Revert, the residue simulation) holds the right tier.
  - Client: no change. `GraphBuilder:654-662` sends `""` for a null background, and `VSCSSUtil.getBorderStyle` (`:279-284`) sends `0px none` for `NONE`; `chart-legend-container.component.html:35-36` binds both as they are. A gradient legend reads the same `LegendSpec` border and `TextSpec` background in `Legend.paint` (`:906-924`).

- [ ] **Step 1: Write the failing tests**

`VSChartChromeDefaultsTest.theLegendIsFramedOnlyWhenClassicOrInPlace`: modern RIGHT, TOP, BOTTOM, LEFT give NONE and null. Modern IN_PLACE gives THIN, and white or `#3E3C43` by dark. LEGACY gives THIN and white for every layout.

`SeedChromeDefaultsTest`:
- `aModernLegendHasNoBorderAndNoFill`: gate on, `newChart()`. The legends border is NONE; after `initDefaultFormat(ctx)` and the color descriptor's `initDefaultFormat(ctx, legends.getLayout())`, both default backgrounds are null. `GraphUtil.setLegendSpec(new LegendSpec(), legends)` gives border NONE.
- `aModernInPlaceLegendKeepsBoth`: `setLayout(IN_PLACE)`, re-init: THIN and `legendBackground(ctx)` at alpha 50. Dark gate: `#3E3C43`.
- `aClassicLegendIsUnchanged`: gate off: THIN, white, alpha 50.
- `revertRestoresTheLegendFrame`: gate on, Revert, re-init: THIN, white.
- `anAuthorLegendBorderOutranksTheSeed`: USER `DOUBLE_LINE` survives a re-seed.
- `theSeededLegendBorderTravelsInTheAsset`: a round trip keeps DEFAULT NONE.
- In the modern-bookmark test: legends border THIN after Revert.

- [ ] **Step 2: Run to verify they fail**

Run: `./mvnw -q test -pl core "-Dtest=VSChartChromeDefaultsTest,SeedChromeDefaultsTest" -Dsurefire.failIfNoSpecifiedTests=false`
Expected: test-compile failure (`legendFramed`, the two-argument `initDefaultFormat`).

- [ ] **Step 3: Implement** the resolver, both `initDefaultFormat` changes, the call sites, the seed write and the registration together.

- [ ] **Step 4: Run to verify they pass**

Run: the Step 2 classes plus `ChartDescriptorSavedDefaultsTest,LegendFormatDialogModelTest,LegendPaddingRenderTest,CSSChartStylesModernChromeTest,VizModernizeUtilTest,BookmarkChromeResolutionTest`.
Expected: all pass. `LegendFormatDialogModelTest` shows the dialog reads the stored border (`LegendFormatDialogModel:82`), and Apply writes USER only on a change (`:161`).

- [ ] **Step 5: Commit**

```bash
git -C "$WT" add core/src/main/java/inetsoft/uql/viewsheet/internal/VSChartChromeDefaults.java core/src/main/java/inetsoft/uql/viewsheet/graph/LegendsDescriptor.java core/src/main/java/inetsoft/uql/viewsheet/graph/LegendDescriptor.java core/src/main/java/inetsoft/report/composition/graph/VGraphPair.java core/src/main/java/inetsoft/uql/viewsheet/internal/ChartVSAssemblyInfo.java core/src/test/java/inetsoft/uql/viewsheet/internal/VSChartChromeDefaultsTest.java core/src/test/java/inetsoft/uql/viewsheet/internal/SeedChromeDefaultsTest.java
```
```bash
git -C "$WT" commit -m "Chart defaults S1, drop the legend border and fill on modern charts" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 10: Automatic label colour works under modern

S1-6, R10.

**Files:**
- Modify: `report/composition/graph/GraphGenerator.java:1160-1162` (treemap), `:1369-1413` and `:1546-1551` (relation)
- Test: `core/src/test/java/inetsoft/report/composition/graph/ChartChromeRenderTest.java`

**Interfaces:**
- Produces: the colour test that turns the automatic colour off becomes:
  - classic: `!GDefaults.DEFAULT_TEXT_COLOR.equals(textSpec.getColor())`, as today;
  - modern (`vizContext.modern`): the author set a colour, `fmt.getUserDefinedFormat().isColorDefined()`, on the format the spec came from (as `:1235` does).
  - Treemap: per tree dim, `fmt2`. Relation: the source format (`:1374`) or the target format (`:1399`); keep a reference to the source one before `fmt2` is reused.
  - Highlight conditions are unchanged. Funnel and waterfall (`:4484`) are not touched.

- [ ] **Step 1: Write the failing tests**

In `ChartChromeRenderTest`; a treemap is `DefaultVSChartInfo`, `CHART_TREEMAP`, group field `State`, size `Sum(Total)`; a tree is `RelationVSChartInfo`, `CHART_TREE`, source `Parent`, target `Child`:
- `aModernTreemapPicksItsLabelColour`: marked, no author colour: `isAutoTextColor()` true.
- `aModernTreemapKeepsAnAuthorColour`: `State`'s text format USER colour: false.
- `aClassicTreemapIsUnchanged`: unmarked, default: true. Unmarked with a USER colour other than `#4B4B4B`: false.
- `aModernTreePicksItsLabelColour` and `aModernTreeKeepsAnAuthorColour`: the same for the tree, the author colour on the target.

- [ ] **Step 2: Run to verify they fail**

Run: `./mvnw -q test -pl core -Dtest=ChartChromeRenderTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: FAIL in the two `...PicksItsLabelColour` tests (false today, because the modern ink is not `#4B4B4B`).

- [ ] **Step 3: Implement** as under Interfaces.

- [ ] **Step 4: Run to verify they pass**

Run: `./mvnw -q test -pl core "-Dtest=ChartChromeRenderTest,RelationVOInsideTextBoxTest" -Dsurefire.failIfNoSpecifiedTests=false`
Expected: all pass.

- [ ] **Step 5: Commit**

```bash
git -C "$WT" add core/src/main/java/inetsoft/report/composition/graph/GraphGenerator.java core/src/test/java/inetsoft/report/composition/graph/ChartChromeRenderTest.java
```
```bash
git -C "$WT" commit -m "Chart defaults S1, let modern treemap and tree labels pick their colour per fill" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 11: Pareto percent axis follows the value axis

S1-7, C9.

**Files:**
- Modify: `report/composition/graph/GraphDefault.java:78-97`
- Test: `core/src/test/java/inetsoft/report/composition/graph/ChartChromeRenderTest.java`

**Interfaces:**
- Consumes: the primary y `AxisSpec`, already set from its descriptor by `setupYScale` (`GraphGenerator:2408-2412`), max mode included.
- Produces: in the pareto branch, when `gen.vizContext.modern` (protected field, same package), the percent axis copies `isTickVisible()`, `isLineVisible()` and `isLabelVisible()` from `yaxis`. Classic keeps the new `AxisSpec`'s ticks on.

- [ ] **Step 1: Write the failing tests**

A pareto is `CHART_PARETO`, X `State`, Y `Sum(Total)`; `percent(g)` is `((RectCoord) g.getCoordinate()).getYScale2().getAxisSpec()`:
- `aModernParetoPercentAxisHasNoTicks`: marked; tick false; line and label equal the primary y spec's.
- `aModernParetoPercentAxisTakesTheAuthorsTicks`: chart-level descriptor `setTicksVisible(true)`: tick true.
- `aClassicParetoKeepsItsTicks`: unmarked: tick true, line true.

- [ ] **Step 2: Run to verify they fail**

Run: `./mvnw -q test -pl core -Dtest=ChartChromeRenderTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: FAIL in `aModernParetoPercentAxisHasNoTicks`.

- [ ] **Step 3: Implement** as under Interfaces.

- [ ] **Step 4: Run to verify they pass**

Run: the Step 2 command, then `ChartChromeRenderTest` alone.
Expected: all pass.

- [ ] **Step 5: Commit**

```bash
git -C "$WT" add core/src/main/java/inetsoft/report/composition/graph/GraphDefault.java core/src/test/java/inetsoft/report/composition/graph/ChartChromeRenderTest.java
```
```bash
git -C "$WT" commit -m "Chart defaults S1, give the modern pareto percent axis the value axis's ticks and line" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 12: Chrome slots for title visibility and the line flag — after PR #7817 merges into epic-74519

M7, R8. Start only after `origin/epic-74519` contains feature-80398 (`ChartChromeSlots`, `ChromeSlot`, `VizModernizeUtil.resetToModern`, `ChartChromeSlotsTest`, `VizModernizeResetTest`, `VizModernizeResidueTest`). Rebase this branch onto it first. If 80398 changed while in review, re-read `ChartChromeSlots.Walk` before editing.

Already on 80398's lists, so nothing to add: `xGridStyle` (`PLOT_VALUES`), the legend `border` (`LEGENDS_VALUES`), the legend content text formats (fill), and the `smoothLines` flag (`flags()`).

**Files:**
- Modify: `uql/viewsheet/graph/TitleDescriptor.java` (`chromeValue`)
- Modify: `uql/viewsheet/internal/ChartChromeSlots.java` (`Walk.descriptor()` after the four title text sites; `Walk.axis()` after the `":line"` slot)
- Test: `core/src/test/java/inetsoft/uql/viewsheet/internal/ChartChromeSlotsTest.java`, `VizModernizeResetTest.java`, `VizModernizeResidueTest.java`

**Interfaces:**
- Consumes: `hasVisibleUserValue`, `isUserTitle` (Task 5); `isUserLineVisible`, `setUserLineVisible` (Task 2).
- Produces:
  - `TitleDescriptor.chromeValue(String field)`: `"visible"` returns the `visible` value; anything else throws `IllegalArgumentException`, as `PlotDescriptor.chromeValue` does.
  - Four `valueSlot("chart:title:<axis>:visible", …)` entries, axis in `x`, `x2`, `y`, `y2`.
  - In `axis(key, loc)`: `ChromeSlot.tiered(key + ":lineVisible", false, true, flag set, info -> false, line value, line value, clear the flag, null)`. The reset's re-seed (`seedAll`) then writes the seeded line.
  - Title text and `userTitle` are not slots (MD §5.1), so a typed title stays visible on the reset copy through the seed.

- [ ] **Step 1: Write the failing tests**

`ChartChromeSlotsTest`:
- `authorTitleVisibilityAndLineFlagsClear`: USER visible on all four titles, `setAuthorLineVisible(true)` on the chart axis and on a ref axis; `resetAll(info, fresh(VizMark.MODERN_LIGHT))`; no USER visible, no flag.
- The key-uniqueness test (`:439-457`) passes with the new keys.

`VizModernizeResetTest`:
- `theResetCopyHidesTitlesAndValueLinesTheAuthorShowed`: a marked bar with USER-visible titles and the value line `setAuthorLineVisible(true)`; `resetToModern`; titles hidden, value line off.
- `aTypedTitleStaysVisibleOnTheResetCopy`: x `setAuthorTitleValue("Revenue")`; after the reset the text is kept and x is visible.

`VizModernizeResidueTest`:
- `anOldFilesLineOffIsNotResidue`: a classic bar parsed from an old file with `state`'s line off; Modernize (Keep); line off, flag set.
- `aShownTitleOnAClassicChartIsResidue`: classic bar, x `setVisible(true)` (USER); Modernize; no USER value, x hidden.
- `aHiddenTitleOnAClassicChartIsKept`: USER false survives.

- [ ] **Step 2: Run to verify they fail**

Run: `./mvnw -q test -pl core "-Dtest=ChartChromeSlotsTest,VizModernizeResetTest,VizModernizeResidueTest" -Dsurefire.failIfNoSpecifiedTests=false`
Expected: FAIL in the new tests.

- [ ] **Step 3: Implement** as under Interfaces.

- [ ] **Step 4: Run to verify they pass**

Run: the Step 2 classes plus `ChromeSlotTest,ModernCopyServiceTest,ChartChromeTypeSeedTest,SeedChromeDefaultsTest`.
Expected: all pass.

- [ ] **Step 5: Commit**

```bash
git -C "$WT" add core/src/main/java/inetsoft/uql/viewsheet/graph/TitleDescriptor.java core/src/main/java/inetsoft/uql/viewsheet/internal/ChartChromeSlots.java core/src/test/java/inetsoft/uql/viewsheet/internal/ChartChromeSlotsTest.java core/src/test/java/inetsoft/uql/viewsheet/internal/VizModernizeResetTest.java core/src/test/java/inetsoft/uql/viewsheet/internal/VizModernizeResidueTest.java
```
```bash
git -C "$WT" commit -m "Chart defaults S1, let the reset copy clear axis title and value-axis line visibility" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 13: Per-type values follow a shared or swapped descriptor

Controller amendment after the S0 review. Run it after Task 11 and before Task 12.

**Why.** The per-type key (`seededType`) lives on `ChartVSAssemblyInfo`, while the per-type values (title visibility, x grid, smoothing) live on its `ChartDescriptor`, and that descriptor is shared or swapped without its key:
- the wizard's temp chart shares the original's descriptor object (`VSWizardBindingService:261`, `VSWizardBindingHandler:1744`, `:1235`); on Cancel (`VSCloseObjectWizardService:113-114`) the original keeps the temp chart's per-type values while its own key is unchanged;
- on wizard Save, `SyncChartHandler:181-184` replaces the final chart's `PlotDescriptor` with a clone of the original's after the seeds ran;
- `copyViewInfo` copies `desc` but not the key (`ChartVSAssemblyInfo:1900-1903`).
After Task 8, a wizard Cancel can leave a bar chart with the temp point chart's titles or x grid, and nothing corrects it until the bar's own key changes.

Also, `SyncChartHandler` copies legend chrome with `set…(value, false)` calls (e.g. `:206` `setBorderColor(olegends.getBorderColor(), false)`), which compare against the target's resolved value and stamp USER when they differ. A marked wizard target now resolves to the modern seed, so syncing from a classic original stamps the classic value as if the author set it. Task 9 seeds the legend border width through the same pattern. Rule: when the target is marked, copy only the source's USER tier (an author value) and leave the target's DEFAULT tier to its seed; when unmarked, behave as today.

**Files:**
- Modify: `uql/viewsheet/internal/ChartVSAssemblyInfo.java` (beside `reseedIfResolvedTypeChanged`; `copyViewInfo`)
- Modify: the wizard close path (`web/vswizard/service/VSCloseObjectWizardService.java`, Cancel and Save) and `web/vswizard/handler/SyncChartHandler.java` where it swaps the plot descriptor
- Test: `core/src/test/java/inetsoft/uql/viewsheet/internal/ChartChromeTypeSeedTest.java` (or a new `PerTypeSeedSharedDescriptorTest` in the same package)

**Interfaces:**
- Consumes: `seedTypeDefaults`, `ResolvedChartType`, `getSeededType()` (S0).
- Produces: `public void reseedTypeDefaults()` on `ChartVSAssemblyInfo`. On a marked chart it runs `seedTypeDefaults(VizContext.of(this), ResolvedChartType.of(getVSChartInfo()))` for the current key and records it. On an unmarked chart it only records the key. It writes only DEFAULT tiers and unflagged plain fields, so calling it again is harmless.
- Called after every place a chart's descriptor is shared back or swapped in: wizard Cancel (on the original), wizard Save (on the final chart, after `SyncChartHandler` swaps the plot descriptor), and `copyViewInfo` (on the receiving info). **Never from a parse or load path** (M8: no seed on load). If `copyViewInfo` turns out to run on load or undo restore, call it from the callers that are edits instead, and say so in the report.

- [ ] **Step 1: Write the failing tests**
- `aCancelledWizardLeavesTheOriginalsPerTypeValues`: a marked bar chart (X `state`, Y `Sum(v)`) seeded; a second chart info that shares the bar's `ChartDescriptor` object is bound as a point chart with measures on X and Y and its per-type seed runs (as the wizard's temp chart does); then the cancel path runs on the bar. The bar's x title DEFAULT is false and its x grid DEFAULT is `NONE`, as for a bar.
- `aWizardSaveReseedsTheFinalChartForItsOwnType`: the final chart is a marked point chart whose plot descriptor is replaced with a clone of a bar's (as `SyncChartHandler` does); after the save path, its x grid DEFAULT is `THIN_LINE`.
- `copyViewInfoReseedsForTheReceivingChartsType`.
- `anUnmarkedChartIsNotWritten`: the same cancel path on an unmarked bar leaves every per-type value as it was.
- `authorValuesSurvive`: a USER title visibility and `userSmoothLines` on the bar survive the cancel path.
- `aClassicOriginalSyncedIntoAMarkedTargetStampsNothing`: a classic original's legend border colour and border width (DEFAULT only) synced by `SyncChartHandler` into a marked target leave the target with no USER value; an author (USER) colour on the original does reach the target as USER. An unmarked target behaves as today.

- [ ] **Step 2: Run to verify they fail**, **Step 3: Implement**, **Step 4: Run to verify they pass** (the Step 1 class plus `ChartChromeTypeSeedTest,ResolvedTypeReseedTest,ChartDescriptorSavedDefaultsTest`, and the existing wizard tests under `core/src/test/java/inetsoft/web/vswizard`).

- [ ] **Step 5: Commit** — subject `Chart defaults S1, re-seed per-type values when a chart's descriptor is shared back or swapped`.

---

## Interfaces for later slices

- **M4 flags.** `DerivedUserFlag.parse(attribute, derived)` and `write(writer, name, flag, derived)`, with `derived` = "differs from every classic creation default". Writing only when the flag differs from the derivation keeps unedited classic XML byte-identical, and makes a seeded value that would derive as the author's write its flag explicitly. Precedents: `AxisDescriptor.userLineVisible` (derived `!lineVisible`) and `TitleDescriptor.userTitle` (derived "has text").
- **Per-type predicates** live in `VSChartTypeDefaults`, one per value: `valueAxisLineVisible`, `axisTitleVisible`, `xGridStyle`, `smoothLines`. Each per-type seed is a private method called from `seedTypeDefaults`.
- **Max-mode copies** follow the normal value under modern at read time: `AxisDescriptor.isMaxModeLineVisible(VizContext)`, `TitleDescriptor.isMaxModeVisible(VizContext)`. S3's legend visibility follows the same pattern.
- **Legend frame.** `VSChartChromeDefaults.legendFramed/legendBorder/legendFill(ctx, layout)` take the layout. S3's top legend is not in-place, so it stays unframed.
- **Typed text.** `TitleDescriptor.setAuthorTitleValue` (design) and `setAuthorTitle` (script) are the author text paths. S5's generated-title rules apply only while `isUserTitle()` is false.
