# Chart Defaults S3 — Legend Layout Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** On a modern chart, put the legend where the bundle draws it and keep it there:
- top for cartesian charts
- right for part-to-whole, polar, relation and map charts, colour ramps, and legends with more than 5 entries
- a larger gap above a top or bottom legend, and no title on it
- a size cap
- start alignment at the plot's left edge
- entries in stack order

Classic charts stay pixel-identical and keep their XML.

**Architecture:**
- **Position.** It is a saved value, seeded per resolved type in S0's `seedTypeDefaults`. It depends on three things: the type, whether a measure is on Color, and a saved "crowded" verdict. A new author flag on `LegendsDescriptor` protects the position.
- **The count.** The verdict comes from counting legend entries once, in `VGraphPair` on a design-mode (composer) draw, after a seed asked for it. Every other re-seed, a viewer bookmark restore included, reuses the saved verdict and never counts.
- **What follows the saved position:** the legend gap (a resolver input), the legend title (a seed behind a new author flag on `LegendDescriptor`), and three new `EGraph` properties that `GraphGenerator` sets from `vizContext` (size cap, alignment, stack order).

**Tech Stack:** Java 21, JUnit 5, Mockito, Spring test context (`@SreeHome`), Maven module `core`. Angular 21 / Vitest for the Legend Format dialog (Task 9).

**Spec:** `docs/superpowers/specs/lookfeel/2026-11-06-chart-defaults-design.md`, amended in `0fe2a2b1ab`. The relevant parts are:
- §3 M1–M9, with M2's legend note and M4's flag list
- §5 S3 (S3-1 to S3-6 and their sections)
- §7 (stacked-legend order, legend overflow)
- §9
- §10 D24, D28–D43

**Builds on:** slice 2, PR #8038 (`bug-80455-s2` at `e600339878`). From earlier slices this plan uses:
- `DerivedUserFlag.parse/write`
- `seedTypeDefaults(VizContext, ResolvedChartType)`, `reseedIfResolvedTypeChanged()`, `reseedTypeDefaults(boolean)`
- `ChartVSAssemblyInfo.syncSavedDefaults()`, `LegendsDescriptor.savedDefaults()`, `chromeValue(String)`
- `VSChartChromeDefaults.legendFramed/legendBorder/legendFill(ctx, layout)`
- `ChartChromeSlots`' `valueSlot`, `ChromeSlot.plain`, `copy(...)`

**Verified against:** `bug-80455-s3` at `0fe2a2b1ab` (`e600339878` plus the spec amendment). Paths are under `core/src/main/java/inetsoft/` unless they start with `core/` or `web/`.

## Corrections to the spec

| # | Spec | Code at `0fe2a2b1ab` | This plan |
|---|---|---|---|
| C1 | S3-3: alignment goes in a `LegendSpec` field | `LegendSpec` is per frame and can be a cached copy (`VisualFrame.getLegendSpec`, `legendSpec0`). The layout itself is one `EGraph` property (`EGraph:221-252`). | Alignment, cap and order are `EGraph` properties (Tasks 7, 8, 10). |
| C2 | S3-4: "extend the rule to horizontal layouts" | A vertical stack puts the first value at the bottom (`IntervalElement:329-381`, `StackableElement.sortData`). `Legend.isReversed` already lists a side legend top segment first. A top legend with more than one column is not reversed (`Legend:658-665`), so on a horizontal stack it already reads like the segments (D31) and a side legend there puts the outermost segment first (D36). | The only modern change is reversing a multi-column top or bottom legend on a vertical stack. The pre-flight confirms the reading before Task 10. |
| C3 | S3-1 / D43: `VSChartDataHandler.updatePreferredSize` keeps the size current | Only `GET /api/composer/refresh` reaches it, and no client calls that. | No change. The derivation reads `preferredSize` as stored. |
| C4 | §9: the composer draw that takes the count | The draw is `/events/vschart/areas` → `ViewsheetSandbox.getVGraphPair` → `VGraphPair.initGraph0`, in every mode. `box.getMode()` tells design from runtime. A viewer's chart editor is a runtime-mode sheet (`VSBindingService.createRuntimeSheet` opens it with the viewer flag). The draw runs after the action's undo checkpoint. | The count runs in `initGraph0` when `box.getMode() == Viewsheet.SHEET_DESIGN_MODE` (Task 4). The pending marker is a field on the info, so a checkpoint holds it: undo and redo redraw and count again to the same verdict. |
| C5 | D35: "show all legends" from no legend | Hiding never writes `NO_LEGEND`; it sets each legend's `visible`. `NO_LEGEND` comes only from a script or an old file. | D35 stands for that case (Task 5). |
| C6 | S3-1: the dialog writes the position on every Apply | `LegendFormatDialogModel:167` also throws on a null position (layout 0): `getIndexByName` calls `name.equals`. | The rewritten write compares against the shown position and skips a null (Task 5). |

**Known limitations:**
- **A script that moves the legend does not re-seed its titles.** Script writes reach only the runtime descriptor (`ChartProcessor:77`, `ChartVSAScriptable:322-331`), which has no mark. A script that moves a legend to the top sets `colorLegend.titleVisible` itself.
- **Older builds** ignore `alignment` and draw today's layout. A writer from an older build drops the following, which the next open in this build then derives (M9):
  - `userLayout` and `userTitleVisible`, so a seeded top legend counts as the author's
  - `legendCrowded`, so the next re-seed of a crowded chart that build saved puts it back at the top, until a composer count
- **A stacked-measures legend split into member frames** may have no element (`Legend.findElement`), so it keeps its natural order.
- **The pending-count marker is not saved.** It is lost when an undo checkpoint is swapped out or a runtime sheet is rebuilt on another node. That draw then keeps the seeded position until the next count (D39).

## Global Constraints

- **Worktree:** `$WT` is the executor's worktree on branch `bug-80455-s3` (spec `0fe2a2b1ab` on `e600339878`). Never work in `E:/StyleBI/stylebi-enterprise/community`.
- **Git:**
  - Each git command is its own call, `git -C "$WT" ...`.
  - One commit per task, subject `Chart defaults S3, <what changed>`, last line `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
  - Subagents never run `git add`, `git commit` or `git stash` (a hook blocks them); the controller stages and commits after review. Never push.
- **Scope is the mark (M1).** Only an assembly with a modern `VizMark` changes. A classic chart renders pixel-identical, and its XML is byte-identical on load and save while no author edits it. Graph-package code reads an `EGraph` property that `GraphGenerator` sets from `vizContext`; unset means today's behaviour.
- **The author wins (M4).**
  - A flagged layout or legend title is never seeded.
  - A seed writes plain fields only while their flag is false, and tiered values only at DEFAULT.
  - Flags are stamped only on a real edit, never on an unchanged Apply.
- **Values:**
  - **Position by type:**
    - `RIGHT` when `GraphTypes.isPie`, `isTreemap`, `isFunnel`, `isMekko`, `isRadar`, `isRelation`, `isGeo`, `isPolygon` or `isContour` holds for the resolved type.
    - `TOP` for every other type, `CHART_AUTO` included.
  - **Crowded:** the entry count is more than 5. A crowded legend or a colour ramp goes `RIGHT`.
  - **Gap, descriptor value:** top and bottom 14, 18, 22 (dense, compact, comfortable); side and in-place 10, 14, 18. On screen each is 2px more.
  - **Caps:** a side legend takes at most 0.3 of the width, a top or bottom one at most 0.25 of the height. They apply only on modern charts whose legend has no author size.
  - **Alignment:** stored as `0` (unset: today's layout), `GraphConstants.LEFT_ALIGNMENT` (start), `CENTER_ALIGNMENT` or `RIGHT_ALIGNMENT` (end). The modern DEFAULT tier is `LEFT_ALIGNMENT`; the legacy one is `0`.
- **Comments:** a short clause each, with no ticket, PR, slice or design-doc names. No `.html` comments.
- **New files** start with the AGPL header of `SeedChromeDefaultsTest.java`, `Copyright (C) 2026  InetSoft Technology`.
- **Tests that reach Spring beans:**
  - `@ExtendWith(SpringExtension.class)`
  - `@ContextConfiguration(classes = { BaseTestConfiguration.class, LibManagerTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)`
  - `@SreeHome`, `@Tag("core")`
  - In `@AfterEach`, clear `viewsheet.modernVisualization`, `viewsheet.darkMode` and `viewsheet.density`.

## Review Focus

1. **A classic chart opened and saved writes no new attribute:** no `userLayout`, `userTitleVisible`, `legendCrowded` or `alignment`. Its legend bounds also stay the same. Pinned by `aClassicChartWritesWhatItAlwaysWrote` (Task 1), `aClassicLegendTitleWritesNoFlag` (Task 3) and the classic-identity cases in Tasks 7, 8 and 10.
2. **A viewer filtering or restoring a bookmark never moves a counted legend.** Pinned by `theViewerNeverCounts` (Task 4) and `aBookmarkRestoreKeepsTheCountedPosition` (Task 2).
3. **"Keep my formatting" on a classic chart:**
   - leaves a resized right legend on the right (D43)
   - leaves an author's bottom legend at the bottom
   - moves an untouched right legend to the top

   Pinned by `keepLeavesAResizedLegend`, `keepLeavesAnAuthorsPosition` and `keepMovesAnUntouchedLegendToTheTop` (Task 2).
4. **A typed legend title on a top legend stays visible,** on a new chart, on Keep and on the reset copy (D42). Pinned by `aTypedTitleStaysOnATopLegend` (Task 3) and `theResetCopyKeepsATypedLegendTitle` (Task 11).
5. **Pressing OK in the Legend Format dialog without changes** stamps no flag and moves nothing, including on a chart whose layout is `NO_LEGEND`. Pinned by `anUnchangedApplyStampsNothing` and `aChartWithNoLegendLayoutAppliesWithoutError` (Task 5).

## File Structure

- **Task 1:** `uql/viewsheet/graph/LegendsDescriptor.java` (layout flag), `uql/viewsheet/internal/ChartVSAssemblyInfo.java` (verdict, pending marker, legend key).
- **Task 2:** `uql/viewsheet/internal/VSChartTypeDefaults.java`, `ChartVSAssemblyInfo.java` (placement seed, triggers), `uql/viewsheet/internal/VizModernizeUtil.java` (Modernize asks for a count).
- **Task 3:** `uql/viewsheet/graph/LegendDescriptor.java` (title flag), `report/internal/AllLegendDescriptor.java`, `ChartVSAssemblyInfo.java` (title seed).
- **Task 4:** `report/composition/graph/LegendEntryCount.java` (new), `ChartVSAssemblyInfo.java` (`applyLegendCount`), `report/composition/graph/VGraphPair.java` (hook).
- **Task 5:** the author paths: `web/viewsheet/controller/chart/VSChartMoveLegendService.java`, `VSChartResizeLegendService.java`, `VSChartLegendsVisibilityService.java`, `web/graph/model/dialog/LegendFormatDialogModel.java`, `web/composer/vs/dialog/RegionPropertyDialogService.java`, `report/script/ChartProcessor.java`, `report/script/LegendScriptable.java`, `web/vswizard/handler/SyncChartHandler.java`.
- **Task 6:** `uql/viewsheet/internal/VSChartChromeDefaults.java` and the gap's six callers.
- **Task 7:** `graph/EGraph.java`, `graph/VGraph.java`, `graph/guide/legend/LegendGroup.java`, `report/composition/graph/GraphGenerator.java` (caps).
- **Task 8:** `LegendsDescriptor.java` (alignment), `EGraph.java`, `LegendGroup.java`, `VGraph.java`, `graph/guide/legend/Legend.java` (item cache), `GraphGenerator.java`, `ChartVSAssemblyInfo.java`, `uql/viewsheet/internal/ChartChromeSlots.java`.
- **Task 9:** `web/graph/model/dialog/LegendFormatGeneralPaneModel.java`, `LegendFormatDialogModel.java`; `web/projects/portal/src/app/graph/model/dialog/legend-format-general-pane-model.ts`, `graph/dialog/legend-format-general-pane.component.ts` and `.html`; `ChartProcessor.java`; `core/src/main/resources/inetsoft/web/binding/property-type.properties`.
- **Task 10:** `EGraph.java`, `Legend.java`, `GraphGenerator.java` (order).
- **Task 11:** `ChartChromeSlots.java`, `VizModernizeUtil.java` (reset).

**Test harnesses:**
- **Seed and descriptor tests** copy the helpers of `ChartColorTypeSeedTest` (`newChart()`, `seed`, `modernize`, `revert`, `writeXml`, `dim`, `agg`) and of `SeedChromeDefaultsTest` (`writeState`, `restoreState`, `chartOn`, `parseInfo`).
- **Graph-package tests** copy `AxisLabelGapShrinkTest.legendChart(int layout, int gap)` (:400-413): build an `EGraph`, then `Plotter.getPlotter(graph).plotAndLayout(new DefaultDataSet(rows), 0, 0, 400, 240)`, and read `vgraph.getPlotBounds()` and `getLegendGroup().getLegend(i).getBounds()`.
- **Generator tests** copy the same class's modern path (:85-118): `GraphGenerator.getGenerator(assemblyInfo, ...).createEGraph()` on a `ChartVSAssemblyInfo` with `setVizMark(VizMark.MODERN_LIGHT)`.
- **The design-mode draw test** copies `ChartOthersRunningTotalViewsheetTest`:
  - `RuntimeViewsheetExtension` over `EmbeddedVS1.zip`, then `box.getVGraphPair(name, true, null)`
  - `event.setViewer(false)` gives a design-mode sheet, `true` a viewer one
  - tagged `core` and `integration`

## How to run tests

From `$WT`:

```bash
./mvnw -q test -pl core "-Dtest=ClassA,ClassB" -Dsurefire.failIfNoSpecifiedTests=false
```

- Read `core/target/surefire-reports/*.txt` for counts.
- Run every new or changed class alone once as well (CLAUDE.md).
- If compilation fails with "Attempt to recreate … HeartBeatServiceProxy", delete the untracked `core/target/generated-test-sources`.
- Portal tests run from `$WT/web` after `npm ci`: `npx ng test portal --include="**/legend-format-general-pane.spec.ts"`.

## Pre-flight check (spec §7)

No commit. Write a throwaway test in `$WT` and delete it afterwards. Record the results in the SDD ledger.

- [ ] **Stacked-legend order.**
  - Build a graph from `AxisLabelGapShrinkTest.legendChart`, changed to:
    - a stacked `IntervalElement` (`setCollisionModifier(GraphElement.MOVE_STACK)`)
    - three colour values `A`, `B`, `C` with positive values
  - Render it with `RIGHT` and with `TOP`, on a vertical `RectCoord` and on one with `coord.transpose()`.
  - For each case, record the order of the legend items (top to bottom, or left to right) and the order of the bar segments, from the baseline outward.
  - Expected from the code: the side legend lists `C, B, A` with `A` at the baseline. The top legend lists `A, B, C` on both coordinates.
  - If the side legend does not match its stack, stop and report to the controller before Task 10.
- [ ] **Legend overflow.** Render a `RIGHT` legend with 40 entries at 400×240.
  - Record the group width as a fraction of the chart width.
  - Record how many `LegendItem`s have non-empty bounds (are any dropped?).
  - This is the "as today" behaviour that Task 7's strict cap keeps.

---

### Task 1: Saved placement state

S3-1, D41, D43, M4.

**Files:**
- Modify: `uql/viewsheet/graph/LegendsDescriptor.java`:
  - accessors after `setLayout` (:206-215)
  - write after `preferredSize` (:510)
  - parse after `preferredSize` (:579-587)
  - `equalsContent` (:483-484)
  - field after `:690`
- Modify: `uql/viewsheet/internal/ChartVSAssemblyInfo.java`:
  - attributes beside `userSmoothLines`: write :1720-1722, parse :1770-1771
  - `copyViewInfo` beside `seededType` (:2069-2073)
  - fields after `:3624`
- Test: `core/src/test/java/inetsoft/uql/viewsheet/graph/LegendsDescriptorLayoutFlagTest.java` (new), `core/src/test/java/inetsoft/uql/viewsheet/internal/LegendPlacementStateTest.java` (new)

**Interfaces:**
- **Produces, `LegendsDescriptor`:**
  - `boolean isUserLayout()` and `void setUserLayout(boolean)`.
  - `void setAuthorLayout(int layout)`: sets the layout and stamps the flag.
  - `void setAuthorPreferredSize(Dimension2D size)`: sets the size and stamps the flag (D43).
  - Attribute `userLayout`, through `DerivedUserFlag`, with derived = `option != RIGHT || preferredSize != null`. It is parsed after both, and copied by `clone` (a primitive).
- **Produces, `ChartVSAssemblyInfo`:**
  - `boolean isLegendCrowded()` and `void setLegendCrowded(boolean)`. Written as ` legendCrowded="true"` only when true, and parsed back. A bookmark state never contains it (`ChartVSAssembly.writeStateContent` writes no info attributes).
  - `void requestLegendCount()` and `boolean isLegendCountPending()`. Pending is a field that is not written to XML.
  - `boolean needsLegendCount()`: true when all of these hold:
    - a count is pending
    - `getVizMark() != null`
    - the design `LegendsDescriptor` exists and `!isUserLayout()`
  - A `private List<String> seededLegendKey`, not written to XML.
  - `copyViewInfo` carries `legendCrowded`, the pending marker and `seededLegendKey`, as it carries `seededType`.

- [ ] **Step 1: Write the failing tests.**
  - **`LegendsDescriptorLayoutFlagTest`** (no Spring; `xml`/`parse` helpers as `AxisDescriptorLineFlagTest`):
    - `anOldFilesRightLegendIsNoOpinion`: `option="2"` with no `userLayout` attribute parses with the flag false.
    - `anOldFilesOtherPositionIsTheAuthors`: each of `0`, `1`, `3`, `4`, `5` parses with the flag true.
    - `anOldFilesResizedLegendIsTheAuthors`: `option="2"` with `preferredSize="0.2,1.0"` parses with the flag true.
    - `anUneditedOldFileWritesWhatItAlwaysWrote`: for `RIGHT` and for `TOP`, parsing and writing again adds no `userLayout`.
    - `aSeededTopWritesItsFlag`: `setLayout(TOP)` writes `userLayout="false"`, and re-parsed the flag is false.
    - `authorSettersStampTheFlag`: `setAuthorLayout(BOTTOM)` and `setAuthorPreferredSize(new DimensionD(0.2, 1))` each give the flag true.
    - `aCopyKeepsTheFlag`, `equalsContentSeesTheFlag`.
  - **`LegendPlacementStateTest`** (Spring):
    - `theVerdictRoundTrips`: `setLegendCrowded(true)`, write, parse into a fresh info: true.
    - `aClassicChartWritesWhatItAlwaysWrote`: an unmarked `newChart()` XML contains neither `legendCrowded` nor `userLayout`.
    - `theVerdictIsNotInABookmark`: the chart's `writeState` output does not contain `legendCrowded`.
    - `copyViewInfoCarriesTheVerdictAndThePendingCount`.
    - `needsLegendCountOnlyOnAMarkedUnflaggedChart`: four cases. Marked and pending gives true. Unmarked, flagged and not pending each give false.

- [ ] **Step 2: Run them to verify they fail.** Expected: test-compile failure.

- [ ] **Step 3: Implement** as under Interfaces.

- [ ] **Step 4: Run them to verify they pass,** plus `ChartDescriptorSavedDefaultsTest`, `SeedChromeDefaultsTest` and `ChartChromeSlotsTest`.

- [ ] **Step 5: Commit**: `Chart defaults S3, store the legend position flag and the crowded verdict`.

---

### Task 2: Seed the legend position

S3-1, D28, D29, D32, D40, D41, D43, Review Focus 2, 3.

**Decision.**
- **One seed** writes the design and the runtime `LegendsDescriptor` while its flag is false:
  - modern: `legendLayout(type, colorRamp(info), isLegendCrowded())`
  - legacy: `RIGHT`
- **It runs from `seedTypeDefaults`,** so creation, a type change, Modernize, Revert, restores and density changes all reproduce the position from the saved verdict.
- **What asks for a count:**
  - `setDefaultFormat` (creation)
  - a resolved-type re-seed
  - a change of the legend binding key
  - Modernize

  Restores, density changes and Revert do not.

**Files:**
- Modify: `uql/viewsheet/internal/VSChartTypeDefaults.java`
- Modify: `uql/viewsheet/internal/ChartVSAssemblyInfo.java`:
  - `setDefaultFormat` (:102-117)
  - `seedTypeDefaults` (:3170-3183)
  - `reseedIfResolvedTypeChanged` (:3362-3370)
  - `reseedTypeDefaults` (:3375-3386)
  - new methods after `seedSmoothLines`
- Modify: `uql/viewsheet/internal/VizModernizeUtil.java` (`moveMark` :184-202)
- Test: `core/src/test/java/inetsoft/uql/viewsheet/internal/VSChartTypeDefaultsTest.java` (extend), `core/src/test/java/inetsoft/uql/viewsheet/internal/LegendPlacementSeedTest.java` (new), `SeedChromeDefaultsTest`

**Interfaces:**
- **Consumes:** Task 1.
- **Produces, `VSChartTypeDefaults`:**
  - `static int legendLayout(ResolvedChartType t, boolean colorRamp, boolean crowded)`: `RIGHT` when `colorRamp || crowded` or the type is in the Global Constraints right list; otherwise `TOP`.
  - `static boolean colorRamp(ChartInfo info)`: true when a measure is on Color, in any of these places:
    - `info.getColorField()`
    - for a multi-style chart, the colour field of any `ChartAggregateRef` in `info.getModelRefs(false)`
    - for a `RelationChartInfo`, `getNodeColorField()`

    In each case the test is `aref.isMeasure()`.
  - `static List<String> legendBindingKey(ChartInfo info)`: the `getFullName()` of every ref in `info.getAestheticRefs(false)`, prefixed by its role (`color:`, `shape:`, `size:` and so on) where the accessor gives it, in a stable order. An empty list when there are none.
- **Produces, `ChartVSAssemblyInfo`:**
  - `private void seedLegendPlacement(VizContext ctx, ResolvedChartType type)`, called first in `seedTypeDefaults`.
  - `public void reseedLegendPlacement()`: on a marked chart only, re-runs it for the current resolved type.
  - **`reseedIfResolvedTypeChanged`, on a marked chart with a key already recorded:**
    - a resolved-type change re-seeds as today, and also calls `requestLegendCount()`
    - otherwise, a changed `legendBindingKey` runs `seedLegendPlacement` and calls `requestLegendCount()`. Task 3 adds `seedLegendTitles` to this branch.
    - either way it records both keys
  - `reseedTypeDefaults` records the legend key too.
  - `setDefaultFormat` calls `requestLegendCount()`.
  - In `VizModernizeUtil.moveMark`, when the mark is non-null, each target `ChartVSAssemblyInfo` gets `requestLegendCount()` before `seedAll`.

- [ ] **Step 1: Write the failing tests.**
  - **`VSChartTypeDefaultsTest`:**
    - `legendGoesTopOnCartesianTypes`: `TOP` for BAR, BAR_STACK, INTERVAL, LINE, STEP, JUMP, AREA, STEP_AREA, POINT, BOXPLOT, CANDLE, STOCK, GANTT, WATERFALL, PARETO and AUTO.
    - `legendGoesRightOnTheOthers`: `RIGHT` for PIE, DONUT, TREEMAP, SUNBURST, CIRCLE_PACKING, ICICLE, FUNNEL, MEKKO, RADAR, FILL_RADAR, TREE, NETWORK, CIRCULAR, MAP, POLYGON, MAP_CONTOUR and SCATTER_CONTOUR.
    - `aRampOrACrowdedLegendGoesRight`: BAR with `colorRamp` gives `RIGHT`, and BAR with `crowded` gives `RIGHT`.
  - **`LegendPlacementSeedTest`** (Spring; a bar bound to `state` on X, `Sum(v)` on Y and `region` on Color):
    - `aNewModernBarHasATopLegend`: gate on, bind, seed. Design and runtime layouts are `TOP`, the flag is false, and `isLegendCountPending()` is true.
    - `aNewModernPieHasARightLegend`.
    - `aMeasureOnColorGoesRight`: Color bound to `Sum(w)` gives `RIGHT`.
    - `theSavedVerdictMovesItRight`: `setLegendCrowded(true)` then a re-seed gives `RIGHT`.
    - `aClassicChartKeepsRight`: gate off, bar: `RIGHT`, and nothing is pending.
    - `anAuthorsPositionSurvivesEveryReseed`: `setAuthorLayout(BOTTOM)`, then a type change to LINE, Revert and Modernize: still `BOTTOM`.
    - `changingTheColorFieldReseedsAndAsksForACount`:
      - Setup: a marked, seeded bar with `seededLegendKey` recorded; clear the pending marker, then bind Color to `Sum(w)`.
      - Run `reseedIfResolvedTypeChanged()`.
      - Expect `RIGHT`, with a count pending.
    - `aViewerTypeChangeUsesTheVerdict`: marked bar, `setLegendCrowded(true)`, change the type to LINE through `reseedIfResolvedTypeChanged()`: `RIGHT`.
    - `aBookmarkRestoreKeepsTheCountedPosition`:
      - Setup: a marked bar on a sheet (`chartOn`), seeded at `TOP`. Take `writeState`. Then `setLegendCrowded(true)` and re-seed, giving `RIGHT`.
      - Run `restoreState` with the state taken at `TOP`.
      - Expect `RIGHT`, with no count pending.
    - `keepMovesAnUntouchedLegendToTheTop`: a classic sheet with a bar at `RIGHT`, then `VizModernizeUtil.modernize(vs)`: `TOP`, with a count pending.
    - `keepLeavesAnAuthorsPosition`: classic `BOTTOM` parsed from an old file (no flag attribute), then modernize: `BOTTOM`.
    - `keepLeavesAResizedLegend`: classic `RIGHT` with `preferredSize` `(0.2, 1.0)` parsed from an old file, then modernize: `RIGHT`, and the size is unchanged.
    - `revertPutsAnUntouchedLegendBackRight`: modernize then revert gives `RIGHT`, flag false.
  - **`SeedChromeDefaultsTest.aBookmarkTakenWhileModernAndRestoredAfterRevertDrawsClassic`:** bind a colour field, and also assert the layout is `RIGHT`.

- [ ] **Step 2: Run them to verify they fail.**

- [ ] **Step 3: Implement** as under Interfaces.

- [ ] **Step 4: Run them to verify they pass,** plus `ChartChromeTypeSeedTest`, `ChartColorTypeSeedTest`, `ResolvedTypeReseedTest`, `PerTypeSeedSharedDescriptorTest`, `BookmarkChromeResolutionTest`, `VizModernizeUtilTest`, `VizModernizeResidueTest` and `ChartChromeSlotsTest`.
  - `ChartChromeSlotsTest.legendPositionResetsToTheFreshLayout` may now expect the fresh chart's seeded `TOP`. Update its expectation only; Task 11 owns the reset.

- [ ] **Step 5: Commit**: `Chart defaults S3, seed the legend position by type, colour ramp and crowded verdict`.

---

### Task 3: Legend title follows the position

S3-6, D29, D37 (seed side), D42, Review Focus 1, 4.

**Files:**
- Modify: `uql/viewsheet/graph/LegendDescriptor.java`:
  - accessors after `setTitleVisible` (:117-126)
  - `parseXML` (:570-573)
  - write after `:518`
  - `equalsContent` (:478)
  - field after `:957`
- Modify: `report/internal/AllLegendDescriptor.java` (beside `setTitleVisible` :86-94)
- Modify: `uql/viewsheet/internal/ChartVSAssemblyInfo.java` (`seedTypeDefaults`; new methods)
- Test: `core/src/test/java/inetsoft/uql/viewsheet/graph/LegendTitleFlagTest.java` (new), `LegendPlacementSeedTest`

**Interfaces:**
- **Produces, `LegendDescriptor`:**
  - `boolean isUserTitleVisible()` and `void setUserTitleVisible(boolean)`.
  - `void setAuthorTitleVisible(boolean)`: sets the value and stamps the flag.
  - `boolean hasTitleText()`: `!Tool.isEmptyString(getTitleValue())`.
  - `void setAuthorTitleValue(String)`: the design text. When the text is non-empty, it also sets the title visible and stamps the flag.
  - `void setAuthorTitle(String)`: the runtime text, for scripts. Same rule.
  - Attribute `userTitleVisible`, through `DerivedUserFlag`, with derived = `!titleVisible || hasTitleText()`. It is parsed in `parseXML` after `parseContents`, because the derivation reads the text.
- **Produces, `AllLegendDescriptor`:** overrides `setAuthorTitleVisible` and `setAuthorTitle`, fanning out through `applySet` as `setTitleVisible` does.
- **Produces, `ChartVSAssemblyInfo`:**
  - `private void seedLegendTitles(VizContext ctx)`. It runs in `seedTypeDefaults` right after `seedLegendPlacement`. Add a call to it in Task 2's key-change branch of `reseedIfResolvedTypeChanged`, after `seedLegendPlacement`.
    - **Which descriptors:** for the design and the runtime descriptor, every `LegendDescriptor` it can show. That is the three on its `LegendsDescriptor`, plus `aref.getLegendDescriptor()` of every ref in `getVSChartInfo().getAestheticRefs(false)` and `(true)`.
    - **What it writes,** with `L` the design descriptor's layout: each descriptor whose flag is false gets `setTitleVisible(!ctx.modern || (L != TOP && L != BOTTOM))`.
  - `public void reseedLegendTitles()`: on a marked chart only.
- **Before implementing,** list the callers of `LegendDescriptor.setTitleValue`. If anything other than the dialog, `SyncChartHandler` and the parse writes it, report it to the controller first: the derivation treats stored text as the author's.

- [ ] **Step 1: Write the failing tests.**
  - **`LegendTitleFlagTest`** (no Spring):
    - `anOldFilesHiddenTitleIsTheAuthors`
    - `anOldFilesTypedTitleIsTheAuthors`
    - `anOldFilesPlainTitleIsNoOpinion`
    - `aClassicLegendTitleWritesNoFlag`: titles shown or hidden, with text or without, written and parsed again, add no `userTitleVisible`.
    - `aSeededHiddenTitleWritesItsFlag`
    - `typingATitleShowsItAndStampsTheFlag`: `setTitleVisible(false)`, then `setAuthorTitleValue("Brands")`: visible and flagged.
    - `clearingTheTextStampsNothing`
    - `allLegendDescriptorFansOut`
  - **`LegendPlacementSeedTest`:**
    - `aTopLegendHasNoTitle`: a marked bar with Color bound. The colour legend descriptor, design and runtime, is not visible.
    - `aRightLegendKeepsItsTitle`: a pie.
    - `aCrowdedLegendKeepsItsTitle`: `setLegendCrowded(true)`, re-seed: visible.
    - `aTypedTitleStaysOnATopLegend`: `setAuthorTitleValue("Brands")` on a marked bar, re-seed: visible.
    - `anAuthorsHiddenTitleStaysHiddenOnTheRight`
    - `aMultiStyleMeasuresLegendLosesItsTitle`: `setMultiStyles(true)`, two Y measures each with its own colour field, marked: each `aref.getLegendDescriptor()` is not visible.
    - `revertShowsEveryTitle`

- [ ] **Step 2: Run them to verify they fail.**

- [ ] **Step 3: Implement** as under Interfaces.

- [ ] **Step 4: Run them to verify they pass,** plus Task 2's list.

- [ ] **Step 5: Commit**: `Chart defaults S3, hide the title of a modern top or bottom legend behind an author flag`.

---

### Task 4: Count the legend once, in the composer

S3-1 count, D28, D33, D39, C4, Review Focus 2.

**Decision.**
- **Where:** `VGraphPair.initGraph0` counts after the graph is final (after the script and no-script branches, before the layout-size block at about :650). It counts only on a design-mode sheet, and only when `ainfo.needsLegendCount()`.
- **Rebuilding:** when the count changes the saved layout, `initGraph0` returns at once with a field `legendRecount` set. `initGraph` (:206-249) then calls `initGraph0` once more. That second run sees no pending count, and its generator reads the new layout, gap, title and caps.

**Files:**
- Create: `report/composition/graph/LegendEntryCount.java`
- Modify: `uql/viewsheet/internal/ChartVSAssemblyInfo.java`
- Modify: `report/composition/graph/VGraphPair.java` (`initGraph` :206-249, `initGraph0` near :640-650)
- Test: `core/src/test/java/inetsoft/report/composition/graph/LegendEntryCountTest.java` (new), `LegendPlacementSeedTest`, `core/src/test/java/inetsoft/report/composition/graph/LegendCountViewsheetTest.java` (new, integration)

**Interfaces:**
- **Consumes:** Tasks 1–3.
- **Produces, `public final class LegendEntryCount`:**
  - `static final int CROWDED_ABOVE = 5`.
  - `static int count(EGraph graph)`: the sum over `graph.getVisualFrames()`, skipping any `LinearColorFrame` (a ramp is decided by the binding).
    - For each frame, count the indexes `i` below `min(labels.length, graph.legend.maxcount)` where `frame.getLegendSpec().isVisible(label)`.
    - `label` is `labels[i]`, or `values[i]` when that is null, mirroring `Legend.createItems` (:670-723).
  - `static boolean crowded(EGraph graph)`: `count(graph) > CROWDED_ABOVE`.
- **Produces, `ChartVSAssemblyInfo`:** `boolean applyLegendCount(boolean crowded)`.
  - Sets the verdict and clears the pending marker.
  - Then runs `seedLegendPlacement` and `seedLegendTitles` with `VizContext.of(this)` and the current resolved type.
  - Returns true when the design layout changed.

- [ ] **Step 1: Write the failing tests.**
  - **`LegendEntryCountTest`** (graph package only, as `LegendContentInsetTest`):
    - `sixColourValuesAreSix`
    - `aHiddenValueIsNotCounted`: one value through `getLegendSpec().setVisible(value, false)`.
    - `colourAndShapeAdd`: 3 colour values plus 3 shape values give 6, and `crowded` is true.
    - `aRampCountsNothing`
    - `fiveIsNotCrowded`
  - **`LegendPlacementSeedTest`:**
    - `aCrowdedCountMovesAnUnflaggedLegendRight`: a marked bar at `TOP`, `applyLegendCount(true)` returns true. The result is `RIGHT`, titles visible, nothing pending.
    - `aCountThatChangesNothingReturnsFalse`
  - **`LegendCountViewsheetTest`** (integration; copy `ChartOthersRunningTotalViewsheetTest`). Read `TableView1`'s data first, then pick one column with more than 5 distinct values and one with 2 to 5.
    - `theComposerCountsOnce`:
      - Setup: a design-mode sheet (`setViewer(false)`); add a marked bar (`setVizMark(MODERN_LIGHT)`, then `initDefaultFormat()`) with the wide column on Color.
      - Run `box.getVGraphPair`.
      - Expect: the saved layout is `RIGHT`, `isLegendCrowded()` is true, nothing is pending, and the pair's `getEGraph().getLegendLayout()` is `RIGHT`.
    - `aNarrowLegendStaysTop`
    - `theViewerNeverCounts`: the same chart on a viewer sheet (`setViewer(true)`) stays `TOP`, and its count stays pending.
    - `noCountWithoutARequest`: setup as `theComposerCountsOnce`, but clear the pending marker before the draw: the chart stays `TOP`.

- [ ] **Step 2: Run them to verify they fail.**

- [ ] **Step 3: Implement** as under Interfaces and Decision. The mode check is `box.getMode() == Viewsheet.SHEET_DESIGN_MODE`.

- [ ] **Step 4: Run them to verify they pass,** plus `ViewsheetSandboxGraphPairFailureTest`, `ExportGraphPairInitFailureTest` and `ChartOthersRunningTotalViewsheetTest`.

- [ ] **Step 5: Commit**: `Chart defaults S3, count a modern legend's entries once in the composer and save the verdict`.

---

### Task 5: Author paths for the position and the legend title

S3-1 and S3-6 stamping, D34, D35, D43, C5, C6, Review Focus 5.

**Files:**
- Modify:
  - `web/viewsheet/controller/chart/VSChartMoveLegendService.java:65-86`
  - `web/viewsheet/controller/chart/VSChartResizeLegendService.java:89-90`
  - `web/viewsheet/controller/chart/VSChartLegendsVisibilityService.java:212-216`
  - `web/graph/model/dialog/LegendFormatDialogModel.java` (`:151-160`, `:167`)
  - `web/composer/vs/dialog/RegionPropertyDialogService.java` (between the dialog apply, about :229, and `editObjectProperty`, :233-234)
  - `report/script/ChartProcessor.java` (`:272-273`, `:351-352`, `:358-361`, `:374-377`, `:390-393`)
  - `report/script/LegendScriptable.java:54-57`
  - `web/vswizard/handler/SyncChartHandler.java` (`:243`, `:268-269`, and `syncChart` :79-81 for the verdict)
- Test: `LegendFormatDialogModelTest` (extend), `core/src/test/java/inetsoft/web/viewsheet/controller/chart/LegendAuthorPathsTest.java` (new), `core/src/test/java/inetsoft/report/script/LegendScriptAuthorTest.java` (new), `core/src/test/java/inetsoft/uql/viewsheet/internal/WizardSyncLegendTest.java` (new, beside `WizardSyncReseedTest`, whose harness it copies)

**Interfaces:**
- **Consumes:** `setAuthorLayout`, `setAuthorPreferredSize`, `setUserLayout`, `setAuthorTitleVisible`, `setAuthorTitleValue`, `setAuthorTitle`, `setUserTitleVisible`, `reseedLegendPlacement`, `reseedLegendTitles`, `requestLegendCount`, `setLegendCrowded`.
- **Move:** when the new layout differs, call `setAuthorLayout(newLayout)`, then `chartState.getChartAssemblyInfo().reseedLegendTitles()`. Dropping the legend where it already is stamps nothing.
- **Resize:** `setAuthorPreferredSize` for docked legends. In-place per-legend sizes are unchanged.
- **Show all legends, from `NO_LEGEND`:** on a marked chart, call `setUserLayout(false)`, `reseedLegendPlacement()`, `reseedLegendTitles()` and `requestLegendCount()` on the cloned info before `editObjectProperty`. A classic chart sets `RIGHT` as today.
- **Dialog Apply:**
  - Read `isTitleVisible()` before applying the text.
  - The text goes through `setAuthorTitleValue` under today's condition (`:151-158`).
  - The visibility goes through `setAuthorTitleVisible` only when the pane's value differs from the value read first.
  - The position goes through `setAuthorLayout` only when the pane's position is non-null and its index differs from the stored layout.
  - On a marked chart, `RegionPropertyDialogService` then calls `info.reseedLegendTitles()` on the info it passes to `editObjectProperty`.
- **Script:** `legendPosition` and `legend.position` use setter `setAuthorLayout`. `colorLegend.title`, `shapeLegend.title` and `sizeLegend.title` use `setAuthorTitle`; the three `*.titleVisible` properties use `setAuthorTitleVisible`. `LegendScriptable` maps `title` and `titleVisible` the same way. Property names are unchanged.
- **Wizard:**
  - `syncLegendsProperties` copies `setUserLayout(o.isUserLayout())` with the layout.
  - `syncLegendProperties` copies `setUserTitleVisible(o.isUserTitleVisible())` with the title.
  - `syncChart` copies `setLegendCrowded(from.isLegendCrowded())` when `syncLegend` is on.
  - None of these stamps a flag.

- [ ] **Step 1: Write the failing tests.**
  - **`LegendFormatDialogModelTest`:**
    - `anUnchangedApplyStampsNothing`: construct from a seeded marked chart, apply as is. Both flags are false and the layout is unchanged.
    - `changingThePositionStampsTheFlag`
    - `typingATitleOnATopLegendShowsIt`
    - `unTickingTheTitleStampsIt`
    - `aChartWithNoLegendLayoutAppliesWithoutError`: layout 0, apply, no exception, still 0.
  - **`LegendAuthorPathsTest`** (Spring; the services' logic through package-private static helpers where the controller plumbing is heavy, as S1's `VSChartAxesVisibilityService.setVisible`):
    - `movingToAnotherEdgeStampsAndReseedsTheTitles`: a marked bar at `TOP` moved to `RIGHT`: flagged, and the colour legend title is visible.
    - `droppingInPlaceOnTheSameEdgeStampsNothing`
    - `resizingStampsTheFlag`
    - `showAllOnAModernChartUsesTheTypeDefault`: a marked pie at `NO_LEGEND`, flagged: `RIGHT`, flag false, a count pending. A marked bar gives `TOP`.
    - `showAllOnAClassicChartIsRight`
  - **`LegendScriptAuthorTest`:** set `legendPosition` and `colorLegend.title` through `ChartProcessor`'s scriptable on a chart's runtime descriptor (as `AxisScriptableTest` drives a scriptable). Both flags are stamped on the runtime descriptors.
  - **`WizardSyncLegendTest`:**
    - `aSeededTopLegendSyncedIntoAMarkedTargetIsNotTheAuthors`
    - `anAuthorsLayoutTravelsWithItsFlag`
    - `theVerdictTravels`

- [ ] **Step 2: Run them to verify they fail.**

- [ ] **Step 3: Implement** as under Interfaces.

- [ ] **Step 4: Run them to verify they pass,** plus `WizardSyncReseedTest`, `RegionPropertyDialogServiceTest`, `AxisScriptableTest` and Task 2's list.

- [ ] **Step 5: Commit**: `Chart defaults S3, stamp the legend position and title flags on author edits`.

---

### Task 6: Top and bottom legend gap

S3-5, R5, D10.

**Files:**
- Modify: `uql/viewsheet/internal/VSChartChromeDefaults.java:176-178`
- Modify, the callers:
  - `report/composition/graph/GraphGenerator.java:1760`, `:1841`, `:1878`
  - `web/graph/model/dialog/LegendFormatDialogModel.java:86`, `:177`
  - `web/viewsheet/controller/chart/VSChartResizeLegendService.java:78`
- Test: `VSChartChromeDefaultsTest`, `LegendFormatDialogModelTest`, `LegendPaddingRenderTest`

**Interfaces:**
- **Produces:** `static int resolveLegendGap(int current, boolean hasOpinion, VizContext ctx, int layout)`.
  - Modern without an opinion: `legendGapForMode(density)`, plus 4 when `layout` is `TOP` or `BOTTOM`.
  - Otherwise: `current`.
  - The three-argument overload is removed, so every caller passes a layout.
- **What each caller passes:**
  - The generator passes its `legends.getLayout()`.
  - The dialog's constructor passes the stored layout.
  - Its Apply passes the layout being applied (`:177`).
  - The resize service passes `layout`.

- [ ] **Step 1: Write the failing tests.**
  - **`VSChartChromeDefaultsTest.aTopOrBottomLegendGapIsOneStepLarger`:**
    - Modern, no opinion: `TOP` and `BOTTOM` give 14, 18, 22 for dense, compact and comfortable; `RIGHT`, `LEFT` and `IN_PLACE` give 10, 14, 18.
    - An opinion or a legacy context returns `current`.
  - **`LegendFormatDialogModelTest.theDialogShowsTheTopGap`:** a marked, compact chart at `TOP` shows 18.
  - **`LegendPaddingRenderTest.aModernTopLegendSpecCarriesTheLargerGap`:** at compact, the colour frame's `LegendSpec.getGap()` is 18.

- [ ] **Step 2: Run them to verify they fail.**
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run them to verify they pass,** plus `LegendAuthorPathsTest` (its resize case) and `AxisLabelGapShrinkTest`.
- [ ] **Step 5: Commit**: `Chart defaults S3, give a modern top or bottom legend the larger gap`.

---

### Task 7: Legend size caps

S3-2, D30.

**Files:**
- Modify: `graph/EGraph.java` (accessors beside `getLegendLayout` :221-252; field :656-657; reset in `clear()` :557-558)
- Modify: `graph/VGraph.java` (:627-642 layout size, :677-705 group size)
- Modify: `graph/guide/legend/LegendGroup.java:222-223`
- Modify: `report/composition/graph/GraphGenerator.java:1266-1270`
- Test: `core/src/test/java/inetsoft/graph/guide/legend/LegendSizeCapTest.java` (new), `LegendPaddingRenderTest`

**Interfaces:**
- **Produces, `EGraph`:** `double getLegendMaxRatio()` and `void setLegendMaxRatio(double)`.
  - `0` (default): today's behaviour, a 0.5 cap that the legend's minimum size can exceed.
  - `> 0`: a strict cap at that ratio. It replaces 0.5 in the layout size and in `refixRatios`, and `Math.max(…, getMinHeight/Width())` is skipped.
- **The generator** sets it after `setLegendOptions` when `vizContext.modern` and `legends.getPreferredSize() == null`:
  - `RIGHT`/`LEFT`: 0.3
  - `TOP`/`BOTTOM`: 0.25
  - anything else: unset
- **Do not call `setLayoutSize` a third time.** `LegendGroup.initSizes` accumulates min sizes on every call.

- [ ] **Step 1: Write the failing tests.**
  - **`LegendSizeCapTest`** (as `AxisLabelGapShrinkTest.legendChart`, 12 colour values with long labels, 400×240):
    - `aStrictSideCapHoldsAtThirtyPercent`: `RIGHT` with ratio 0.3: group width ≤ 120.
    - `aStrictTopCapHoldsAtTwentyFivePercent`: `TOP` with 0.25: group height ≤ 60.
    - `anUnsetCapIsToday`: ratio 0 gives the same group and plot bounds as a graph that never had the property set, for all four layouts.
    - `aStrictCapBeatsTheMinimumSize`: a legend whose `getMinWidth()` exceeds 0.3 × 400 is still ≤ 120 wide.
  - **`LegendPaddingRenderTest`:**
    - `aModernUnsizedLegendGetsTheCap`: `getLegendMaxRatio()` is 0.3 at `RIGHT`.
    - `anAuthorSizedLegendKeepsTodaysCap`: with a preferred size, 0.
    - `aClassicLegendKeepsTodaysCap`: 0.

- [ ] **Step 2: Run them to verify they fail.**
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run them to verify they pass,** plus `AxisLabelGapShrinkTest`, `LegendContentInsetTest` and `GraphRenderTest`.
- [ ] **Step 5: Commit**: `Chart defaults S3, cap an unsized modern legend at 30 percent of the width or 25 percent of the height`.

---

### Task 8: Legend alignment — stored value, seed and layout

S3-3, D30, D38, C1.

**Files:**
- Modify: `uql/viewsheet/graph/LegendsDescriptor.java`:
  - field beside `gap` (:692)
  - accessors
  - write (:505-519); parse (:589-597)
  - clone (:443-446); `equalsContent`
  - `savedDefaults` (:676-679), `chromeValue` (:654-667), `resetCompositeValues`
- Modify: `uql/viewsheet/internal/ChartVSAssemblyInfo.java`: both branches of `seedChromeDefaults`, beside `seedLegendBorder` (:228-231).
- Modify: `uql/viewsheet/internal/ChartChromeSlots.java:81` (`LEGENDS_VALUES` gains `"alignment"`)
- Modify: `graph/EGraph.java`, `graph/guide/legend/LegendGroup.java` (`layoutTB` :165-205), `graph/VGraph.java` (after `fitCoord` :756), `graph/guide/legend/Legend.java` (`layoutItems` :491)
- Modify: `report/composition/graph/GraphGenerator.java:1266-1270`
- Test: `core/src/test/java/inetsoft/graph/guide/legend/LegendAlignmentTest.java` (new), `SeedChromeDefaultsTest`, `ChartDescriptorSavedDefaultsTest` (automatic), `ChartChromeSlotsTest`

**Interfaces:**
- **Produces, `LegendsDescriptor`:**
  - A tiered `CompositeValue<Integer> alignment`, default `0`.
  - `int getAlignment()`, `void setAlignment(int)` (USER), `void setAlignment(int, CompositeValue.Type)` and `boolean hasAlignmentUserValue()`.
  - Written as ` alignment="…"` only when the value's string form is non-empty, so an unmarked chart with no author value writes nothing.
  - Registered in `savedDefaults()` and `chromeValue("alignment")`.
- **The seed** writes DEFAULT `GraphConstants.LEFT_ALIGNMENT` on the modern branch and `0` on the legacy branch, on the design and runtime descriptors.
- **Produces, `EGraph`:** `int getLegendAlignment()` and `void setLegendAlignment(int)`, default `0` and reset in `clear()`. The generator sets it from `legends.getAlignment()` when the layout is `TOP` or `BOTTOM`; otherwise `0`.
- **Produces, `LegendGroup`:** `void setAlignRange(double x0, double x1)`.
  - In `layoutTB`, when the graph's alignment is non-zero and a range is set, each legend's width is `min(w * ratio, legend.getPreferredWidth(h, w))`.
  - When their total fits the range:
    - start places them from `x0`
    - end places them so the last one ends at `x1`
    - centre centres them in the range
  - Otherwise, today's code runs.
- **`VGraph.layout0`:** after `fitCoord`, for a `TOP` or `BOTTOM` legend with a non-zero alignment, call `vlegends.setAlignRange(plotx, plotx + plotw)` and then `vlegends.layout()`. Do not call `setLayoutSize` again.
- **`Legend.layoutItems`:** clears the cached `itemsArr` before laying out, because the second pass can change `ncol`.

- [ ] **Step 1: Write the failing tests.**
  - **`LegendAlignmentTest`** (as `AxisLabelGapShrinkTest.legendChart`, 3 colour values, 400×240):
    - `startPutsTheLegendAtThePlotsLeftEdge`: `TOP`, `LEFT_ALIGNMENT`: `getLegend(0).getBounds().getX()` equals `getPlotBounds().getX()` within 0.5.
    - `endPutsItAtThePlotsRightEdge`: the legend's max x equals the plot's max x within 0.5.
    - `centreCentresItOnThePlot`
    - `twoLegendsSitSideBySideFromThePlotEdge`: colour plus shape. The second legend's x is the first's max x; neither fills the width.
    - `bottomFollowsTheSameRule`
    - `unsetIsToday`: for `TOP` and `BOTTOM` with one legend and with two, the bounds equal those of a graph that never had the property set.
    - `aSideLegendIgnoresIt`: `RIGHT` with `LEFT_ALIGNMENT` equals unset.
  - **`SeedChromeDefaultsTest`:**
    - `aModernLegendStartsAtThePlot`: DEFAULT `LEFT_ALIGNMENT`.
    - `aClassicLegendHasNoAlignment`: 0, and the chart XML has no `alignment`.
    - `anAuthorAlignmentOutranksTheSeed`
    - `theSeededAlignmentTravelsInTheAsset`
  - **`ChartChromeSlotsTest.alignmentIsATieredLegendSlot`:** `chart:legends:alignment` exists, and its reset clears USER.

- [ ] **Step 2: Run them to verify they fail.**
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run them to verify they pass,** plus `ChartDescriptorSavedDefaultsTest`, `AxisLabelGapShrinkTest`, `LegendContentInsetTest`, `VizModernizeResidueTest` and `VizModernizeResetTest`.
- [ ] **Step 5: Commit**: `Chart defaults S3, align a modern top or bottom legend to the plot's left edge`.

---

### Task 9: Alignment in the dialog and in script

S3-3, D30.

**Files:**
- Modify: `web/graph/model/dialog/LegendFormatGeneralPaneModel.java` (field and accessors beside `position` :58-64, :161)
- Modify: `web/graph/model/dialog/LegendFormatDialogModel.java` (constructor near :106-108; Apply near :167)
- Modify: `web/projects/portal/src/app/graph/model/dialog/legend-format-general-pane-model.ts`
- Modify: `web/projects/portal/src/app/graph/dialog/legend-format-general-pane.component.ts` (beside `positionChoices` :135-143) and `.html` (after the Position row :43-71)
- Modify: `report/script/ChartProcessor.java` (beside `legendGap` :300-301)
- Modify: `core/src/main/resources/inetsoft/web/binding/property-type.properties` (legend block :249-255)
- Test: `LegendFormatDialogModelTest`, `web/projects/portal/src/app/graph/dialog/legend-format-general-pane.spec.ts`

**Interfaces:**
- **The pane model** gets `String alignment`: `"Start"`, `"Center"` or `"End"`. The dialog shows `"Center"` for `0`.
- **Apply** writes `setAlignment(int)` (USER) only when the pane's value maps to a different number from what was shown.
- **The pane** shows an Alignment select (`data-test="legend-alignment"`, options Start, Center, End) only while the selected position is Top or Bottom. The labels go through `_#(...)` as the position labels do.
- **Script property** `legendAlignment`: getter `getAlignment`, setter `setAlignment`, `int.class`. Its doc line is `Chart.legendAlignment=number`, in alphabetical order.

- [ ] **Step 1: Write the failing tests.**
  - **`LegendFormatDialogModelTest`:**
    - `aModernTopLegendShowsStart`
    - `aClassicLegendShowsCenterAndApplyWritesNothing`
    - `pickingEndWritesTheAuthorValue`
  - **`legend-format-general-pane.spec.ts`:**
    - `shows the alignment select for a top legend`
    - `hides it for a right legend`
    - `writes the chosen alignment to the model`

- [ ] **Step 2: Run them to verify they fail.**
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run them to verify they pass:** the Java class, then `npx ng test portal --include="**/legend-format-general-pane.spec.ts"`. Expected: the existing pane cases still pass.
- [ ] **Step 5: Commit**: `Chart defaults S3, let an author set the legend alignment in the dialog and in script`.

---

### Task 10: Stacked legends in mark order

S3-4, D31, D36, C2. Run only after the pre-flight confirmed the code reading.

**Files:**
- Modify: `graph/EGraph.java`, `graph/guide/legend/Legend.java` (`isReversed` :658-665; `layoutItems` :546-577)
- Modify: `report/composition/graph/GraphGenerator.java:1266-1270`
- Test: `core/src/test/java/inetsoft/graph/guide/legend/StackedLegendOrderTest.java` (new)

**Interfaces:**
- **Produces, `EGraph`:** `boolean isLegendInMarkOrder()` and `void setLegendInMarkOrder(boolean)`. Default false, reset in `clear()`. The generator sets it to `vizContext.modern`.
- **`Legend.isReversed()`:** today's rule, or, when the property is set, all of these:
  - the frame test
  - `!verticalLayout && ncol > 1`
  - a stacked element
  - a non-polar coordinate
  - a coordinate that is not rotated

  "Rotated" means `GTool.getRotation(coord.getCoordTransform()) != 0`. For a `FacetCoord`, it is tested on its first inner coordinate (`getInnerCoordinates()`).
- **`layoutItems`, with the property set:** an empty slot in a reversed row still advances `itemx`, so no item shifts a column. Classic charts keep today's arithmetic.

- [ ] **Step 1: Write the failing tests** (`StackedLegendOrderTest`, the pre-flight's graph with values `A`, `B`, `C`, stacked).
  - `aModernTopLegendOnAVerticalStackListsTheTopSegmentFirst`: leftmost item `C`.
  - `aModernTopLegendOnAHorizontalStackReadsLikeTheSegments`: leftmost `A`.
  - `aSideLegendIsUnchanged`: `RIGHT`, vertical and horizontal, with and without the property: top item `C`.
  - `aClassicTopLegendIsUnchanged`: no property: leftmost `A`.
  - `aClusteredLegendIsUnchanged`: no stack: leftmost `A`.
  - `aWrappedReversedRowKeepsItsColumns`: 5 values, `TOP`, a width that gives 3 columns, the property set. Each row's items sit in distinct columns, from the right.

- [ ] **Step 2: Run them to verify they fail.**
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run them to verify they pass,** plus `LegendContentInsetTest` and `GraphRenderTest`.
- [ ] **Step 5: Commit**: `Chart defaults S3, list a modern stacked legend in mark order`.

---

### Task 11: Reset copy and chrome slots

M7, R6, D37, D42, D43, Review Focus 4.

**Files:**
- Modify: `uql/viewsheet/internal/ChartChromeSlots.java`:
  - `chart:legends:layout` (:105-106)
  - placement slots (:110-115)
  - `ref()` legend slots (:273-278)
- Test: `ChartChromeSlotsTest`, `VizModernizeResetTest`

**Interfaces:**
- **`chart:legends:layout`:** its reset copies the fresh layout, calls `setUserLayout(false)`, and calls `requestLegendCount()` on the target info. `resetToModern`'s `seedAll` then seeds the target's own type default.
- **New plain slots:**
  - `chart:legends:{color,shape,size}:titleVisible`
  - `<ref key>:legend:titleVisible`

  The reset clears `userTitleVisible` unless `hasTitleText()`.
- **`chart:legends:size`** stays as it is: the fresh size is null (D43).

- [ ] **Step 1: Write the failing tests.**
  - **`ChartChromeSlotsTest`:**
    - `theLayoutResetClearsTheFlagAndAsksForACount`
    - `aLegendTitleFlagClearsOnResetUnlessTyped`
  - **`VizModernizeResetTest`:**
    - `theResetCopyPutsTheLegendAtTheTypeDefault`: a classic bar with an author `BOTTOM` gives `TOP`; a classic pie gives `RIGHT`. The flags are false and a count is pending.
    - `theResetCopyDropsAResizedLegendsSize`: `preferredSize` is null and the layout is `TOP`.
    - `theResetCopyClearsAnAuthorsHiddenLegendTitle`: a modern bar's colour legend title is hidden by the seed again, and its flag is false.
    - `theResetCopyKeepsATypedLegendTitle`: still visible and flagged.

- [ ] **Step 2: Run them to verify they fail.**
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run them to verify they pass,** plus `VizModernizeResidueTest`, `VizModernizeUtilTest` and Task 2's list.
- [ ] **Step 5: Commit**: `Chart defaults S3, reset the legend position and title flags on the reset copy`.

---

### Task 12: Whole-branch check

- [ ] **Step 1:** Run the core suite: `./mvnw -q test -pl core -Dsurefire.failIfNoSpecifiedTests=false`. Expected: 0 failures. Re-run any failing class alone and compare with `bug-80455-s2` before calling it a flake.
- [ ] **Step 2:** Run the portal suite, `npx ng test portal`, and compare its failing specs with `bug-80455-s2`.
- [ ] **Step 3: Manual checks.** Write `E:/Temp/chart-defaults-s3-checks-handoff.md`. On a built server, in light and dark and at the three densities, cover:
  - **Placement:** a new modern bar with 3, 5 and 8 colour values; a pie; a measure on Color; a classic copy of each.
  - **Behaviour after the first draw:**
    - filtering the 8-value bar in the viewer to 4 values: the legend stays on the right
    - a bookmark restore
    - Undo and Redo after the binding change that crowded it
  - **Author edits:** a legend dragged to the bottom, then Modernize and Revert; a resized legend; a typed legend title on a top legend.
  - **Alignment:** start, centre and end in the dialog; two legends at the top.
  - **Stacked order:** a stacked bar and a stacked horizontal bar with a top legend.
  - **Overflow:** a 40-entry legend on screen (does it scroll?) and in a PDF export.
  - **Reset copy and Revert.**
  - **Exports:** PNG and PDF.

## Interfaces for later slices

- **Position:** `VSChartTypeDefaults.legendLayout(type, colorRamp, crowded)` is the one placement rule. S8-5, which hides the treemap size legend, changes visibility, not placement.
- **The crowded verdict:** `ChartVSAssemblyInfo.isLegendCrowded()`, taken only by `VGraphPair` on a design-mode draw. Any later rule that depends on drawn data and must not move in the viewer can follow the same pattern: request a count, then save the verdict.
- **Legend flags:** `LegendsDescriptor.userLayout` and `LegendDescriptor.userTitleVisible`, with the author setters `setAuthorLayout`, `setAuthorPreferredSize`, `setAuthorTitleVisible`, `setAuthorTitleValue` and `setAuthorTitle`.
- **`EGraph` legend properties:** `legendMaxRatio`, `legendAlignment` and `legendInMarkOrder`, each unset by default, which keeps today's behaviour.
