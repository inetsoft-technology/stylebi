# Chart Defaults S8a — Zero Line, Radar Web and Gantt Ticks Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** On a modern (marked) chart:
- a value axis whose range crosses zero draws a zero line in the axis-line colour
- a radar draws polygon rings and spokes in the grid colour
- a Gantt's time axis puts a label on every tick, so its grid lines sit only at labelled ticks

Classic charts are unchanged.

**Architecture:** Every item is a drawing rule (kind D). Nothing is stored, no XML changes, and no seed, flag, reset slot or residue rule is involved. One dialog row is added.
- **Graph package.** New properties default to off, and off draws today's output (M1):
  - `AxisSpec.zeroLineColor` / `zeroLineCrossing`, drawn by `DefaultAxis.createZeroLine` from `RectCoord`
  - `AbstractParallelCoord.evenRings`, drawn by `DefaultAxis.createGridLinesAt`
  - `PolarCoord.sidesFromSpokes`
  - `TimeScale.labelFit`
- **Generators.**
  - `GraphGenerator.createAxisSpec` sets the zero line through a new `ModernZeroLine`.
  - The generator's parallel branch and `RadarGraphGenerator` set the radar's grid, spoke colour and web.
  - `GanttGraphGenerator.fixGanttCoord` sets the time scale's label fit and keeps labels on ticks.
- **Dialog.** `ChartLinePaneModel` shows the Y grid row on a marked radar; a new `xGridLineVisible` hides the X row there.

**Tech Stack:** Java 21, JUnit 5, Spring test context (`@SreeHome`), Maven module `core`; Angular 21 and Vitest for the one template change.

**Spec:** `docs/superpowers/specs/lookfeel/2026-11-06-chart-defaults-design.md` (S8 amended in `005c97e412`). The relevant parts are:
- §3 M1 and M9
- §5 S8: rows S8-1, S8-2 and S8-7, and the sections "S8-1 zero line", "S8-2 radar web" and "S8-7 Gantt ticks"
- §9's S8a items, and §11 "Family items (S8)" for S8-1, S8-2 and S8-7
- §10 D130, D131, D133 to D135 and D143

**Builds on:** S6 (`bug-80455-s6` at `5ff9c37362`). Uses `GanttGraphGenerator`'s S6-9 plot-width estimate (`fixGanttCoord`), `VSChartChromeDefaults.resolveSpokeColor` and `PolarCoord.isSameScale` (S5b).

**Verified against:** `bug-80455-s8` at `005c97e412`. Paths are under `core/src/main/java/inetsoft/` unless they start with `core/` or `web/`.

## Corrections to the spec

| # | Spec | Code at `005c97e412` | This plan |
|---|---|---|---|
| C1 | S8-1: "Where it draws, the darkened 0 gridline is left out" | A vertical chart's grid is created by the right axis toward the left one (`RectCoord:321-322`, `yaxis2.createGridLines(yaxis1, true)`), and `yaxis2` carries `yscale2` when a secondary scale exists. The x grid comes from `xaxis2` (`:243`) | The zero line lives on the primary axis (`xaxis1`, `yaxis1`). The grid axis leaves out its 0 line only when it shares the primary axis's scale; a separate secondary scale keeps its own 0 line |
| C2 | S8-1: "Each facet panel draws its own" | A facet's inner `RectCoord` skips grid lines (`facet == null` guard, `:242`, `:320`); `FacetCoord.createGridLines` draws them | The zero line is created outside that guard, so every inner panel gets its own |
| C3 | S8-1: "only the primary value axis draws one" | `createAxisSpec` already knows `secondary`. On a multi-style chart `info.getRTChartType()` is not the measure's type | The generator sets the zero line only when `!secondary`. The type is the axis's own measure's RT type on a multi-style chart (`info.getFieldByName(flds[0], true)`, with the `ElementVO.getBaseName` fallback as at `GraphGenerator:6887-6888`) |
| C4 | S8-2: "When every spoke has the same scale (S5-6's test)" | `PolarCoord.applySpokeLabelModes` runs after the spokes are transformed (`:547-548`), but the rings are built earlier, in `AbstractParallelCoord.createAxis:99-123` | `AbstractParallelCoord.createAxis` compares the spokes itself with `PolarCoord.isSameScale`, made package-private |
| C5 | S8-2: "The Lines tab offers the Y grid row on a marked radar" | One flag, `gridLineVisible`, gates both the X and the Y grid rows (`chart-line-pane.component.html:21`) | A new `xGridLineVisible` gates the X row. It is false on a marked radar and equals `gridLineVisible` elsewhere |
| C6 | S8-7: "the time scale picks a round unit from the span and the plot width" | The range and the data-spaced unit are known only after `TimeScale.init(DataSet)` (`:108`, type chosen at `:238`); the generator only has the plot width | The generator passes a pixel budget (`setLabelFit`). The scale picks the unit at the end of `init`, and `getTicks` steps by it |

**Design calls (§9 left these to the builder):**
- **Zero line visual:** a `GridLine` on the axis layer (`GDefaults.AXIS_Z_INDEX`), THIN, added straight to the `VGraph`, not through `Axis.addGridLine`, so the 4px crowding rule (`Axis:216-226`) cannot hide it.
- **Even rings:** four positions at a quarter, a half, three quarters and the whole of each spoke's length, the same fractions on every spoke.
- **Side count:** `PolarCoord.sidesFromSpokes` makes `getSidesCount` return the parallel coordinate's spoke count (`getParallelScales().length`).
- **Gantt candidates,** ascending: minute ×1, 5, 15, 30; hour ×1, 3, 6, 12; day ×1, 2; week ×1, 2; month ×1; quarter ×1, 2; year ×1, 2, 5, 10, 25, 50, 100. The first one at least as long as the data-spaced unit whose tick count fits wins; if none fits, the last.
- **Gantt label width:** the width of `"Sep 2026"` in the time axis font (`GTool.stringWidth`) plus 4px, the flat gap of D92. The tick budget is `max(2, floor(plotPx / labelPx))`.
- **Gantt extra last tick:** under the fit, `getTicks` adds no tick at the max (`:604-614`). The end-label slide (D93) handles the end.

**Known limitations:**
- **Older builds** draw no zero line, no rings and today's Gantt ticks (M9).
- **A dual-axis chart** draws the zero line of its primary axis only (D133).
- **A narrow Gantt** can still thin labels after the fit; the grid lines at those ticks stay (spec S8-7).

## Global Constraints

- **Worktree:** `$WT` is the executor's worktree on branch `bug-80455-s8`, at or after this plan's commit. Never work in `E:/StyleBI/stylebi-enterprise/community` or any other worktree.
- **Shared machine.** Other sessions may run servers and live checks from other worktrees, using the shared Maven repository and the shared asset data. So:
  - Never run `mvnw install`, `mvnw package -pl server`, `spring-boot:run`, or anything else that writes outside `$WT`.
  - Never start a server or bind or call ports 8080, 8081 or 5005.
  - Run only the test classes a step names (Task 7 names packages). The full `core` suite runs only when the controller says so.
- **Git:**
  - Each git command is its own call, `git -C "$WT" ...`, with no `&&` anywhere in the command.
  - One commit per task, subject `Chart defaults S8, <what changed>`, last line `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
  - Subagents never run `git add`, `git commit` or `git stash` (a hook blocks them); the controller stages and commits after review. Never push.
- **Scope is the mark (M1).** Every new graph-package property defaults to off, and off draws today's output. A classic chart is pixel-identical.
- **Values (all under the mark):**
  - **Zero line colour:** the value axis spec's resolved line colour (`spec.getLineColor()` after `setupAxisSpec`): `#85827A` light, `#7E7B86` dark, or the author's.
  - **Zero line types (D134):** `isBar`, `isInterval`, `isLine`, `isStep`, `isArea`, `isStepArea`, `isPoint`, `isScatteredContour`, `isWaterfall`, `isPareto`, `isCandle`, `isStock`, `isBoxplot`; never `is3DBar`, `isGantt`, `isFunnel`, `isMekko`, `isPolar`, `isTreemap`, `isRelation`, `isMap`. All are `GraphTypes` predicates.
  - **Zero line condition:** a `LinearScale` that is not a `LogScale`, with the mapped 0 strictly between the scale's min and max (either order). On an X value axis, the Y scale must pass the same test (`zeroLineCrossing`).
  - **Radar rings:** `plotdesc.getYGridStyle()` and `plotdesc.getYGridColor()` on each spoke; spoke line colour `VSChartChromeDefaults.resolveSpokeColor(axisD.getLineColor(), ctx)`; even rings at `{0.25, 0.5, 0.75, 1.0}` when any two spokes fail `isSameScale`.
  - **Gantt:** the fit is set only when `ctx.modern` and the time scale has no author increment (`getIncrement() == null`); then `setLabelBetween(false)`.
- **Comments:** a short clause each, with no ticket, PR, slice or design-doc names.
- **New files** start with the AGPL header of `SeedChromeDefaultsTest.java`, `Copyright (C) 2026  InetSoft Technology`.
- **Tests that reach Spring beans** use `@ExtendWith(SpringExtension.class)`, `@ContextConfiguration(classes = BaseTestConfiguration.class, initializers = ConfigurationContextInitializer.class)`, `@SreeHome`, `@Tag("core")`, and clear `viewsheet.modernVisualization`, `viewsheet.darkMode` and `viewsheet.density` in `@AfterEach`. Graph-level tests that build elements use the same annotations (the S4b review found `GDefaults → StyleFont → SreeEnv` reaching `ConfigurationContext`).

## Review Focus

1. **Classic charts are untouched:** no zero line, the darkened 0 gridline still drawn, no radar rings or web, Gantt labels between ticks. Pinned by the `classic…` cases in Tasks 1, 2, 3, 4 and 6.
2. **A dual-axis chart with its own secondary scale:** the zero line follows the primary axis, and the grid drawn from the secondary scale keeps its own 0 line. Pinned by `aSeparateSecondaryScaleKeepsItsZeroGridLine` (Task 1) and `onlyThePrimaryValueAxisGetsIt` (Task 2).
3. **A range that touches 0 without crossing** (all positive with min 0, all negative with max 0): no zero line, and the darkened 0 gridline stays. Pinned by `zeroAtTheEdgeDrawsNone` (Task 1).
4. **A filled radar with spokes of different scales:** closed quarter rings drawn under the fill. Pinned by `unequalSpokesGetQuarterRings` and `ringsDrawUnderTheSeries` (Task 3).
5. **A Gantt whose dates are all equal, or whose plot is too narrow:** a unit is still chosen, with no exception and at least the first tick. Pinned by `aZeroRangeOrTinyBudgetStillPicksAUnit` (Task 5).

## File Structure

- **Task 1:** `graph/AxisSpec.java` (two properties, `equals`, `hashCode`), `graph/guide/axis/DefaultAxis.java` (`createZeroLine`, `getZeroLine`, `setZeroGridLineHidden`, `createGridLines`), `graph/coord/RectCoord.java` (`createAxis`, `fit`, new private `createZeroLine`).
- **Task 2:** `report/composition/graph/ModernZeroLine.java` (new), `report/composition/graph/GraphGenerator.java` (`createAxisSpec`).
- **Task 3:** `graph/guide/axis/DefaultAxis.java` (`createGridLinesAt`), `graph/coord/AbstractParallelCoord.java` (`evenRings`, `createAxis`), `graph/coord/PolarCoord.java` (`sidesFromSpokes`, `getSidesCount`, `isSameScale` package-private, `equals`).
- **Task 4:** `report/composition/graph/GraphGenerator.java` (the `AbstractParallelCoord` branch at `:2513-2550`), `report/composition/graph/RadarGraphGenerator.java` (`:163-164`), `web/composer/model/vs/ChartLinePaneModel.java`, `web/composer/vs/dialog/ChartPropertyDialogService.java` (`:120`), `web/projects/portal/src/app/vsobjects/model/chart-line-pane-model.ts`, `web/projects/portal/src/app/vsobjects/dialog/graph/chart-line-pane.component.html`.
- **Task 5:** `graph/scale/TimeScale.java` (`labelFit`, `chooseFitUnit`, `init`, `getTicks`).
- **Task 6:** `report/composition/graph/GanttGraphGenerator.java` (`fixGanttCoord`).
- **Task 7:** none (whole-branch check and live-check handoff).

**Test harnesses:**
- **Graph-level tests** build an `EGraph` and lay it out as `core/src/test/java/inetsoft/graph/VGraphTopInsetTest.java` does (`Plotter.getPlotter(graph).plotAndLayout(new DefaultDataSet(rows), 0, 0, w, h)`). Graph space is y-up. Axes come from `RectCoord.getAxisAt(ICoordinate.LEFT_AXIS)` and friends.
- **Generator tests** copy `core/src/test/java/inetsoft/report/composition/graph/ModernRadarLabelTest.java`'s annotations and its private `graph(cinfo, rows, mark, desc)` (`VizMark.MODERN_LIGHT` against `null`). Copy the Gantt builder from `ModernGanttEndTest.gantt(int, VizMark, Dimension)`; it is private.

## How to run tests

From `$WT`:

```bash
./mvnw -q test -pl core "-Dtest=ClassA,ClassB" -Dsurefire.failIfNoSpecifiedTests=false
```

- Read `core/target/surefire-reports/*.txt` for counts.
- Run every new or changed class alone once as well (CLAUDE.md).
- If compilation fails with "Attempt to recreate … HeartBeatServiceProxy", delete the untracked `core/target/generated-test-sources`.
- Portal: from `$WT/web`, `npx ng test portal --include=src/app/vsobjects/dialog/graph/chart-line-pane.component.spec.ts` (path relative to the portal project root, `projects/portal`). `npm run test:portal | tail` misreports; read the summary lines.

---

### Task 1: The zero line in the graph package (S8-1, D131, D133)

**Files:**
- Modify: `graph/AxisSpec.java` (fields next to `gridColor` at `:746`; `equals:650`, `hashCode:678`)
- Modify: `graph/guide/axis/DefaultAxis.java` (`createGridLines:635-723`, new methods)
- Modify: `graph/coord/RectCoord.java` (`createAxis` around `:236-246` and `:316-323`; `fit` around `:560-566` and `:618-624`)
- Test: `core/src/test/java/inetsoft/graph/guide/axis/DefaultAxisZeroLineTest.java` (new, Spring)

**Interfaces:**
- Produces:
  - `AxisSpec.setZeroLineColor(Color)` / `Color getZeroLineColor()`: default null (off). `AxisSpec.setZeroLineCrossing(boolean)` / `boolean isZeroLineCrossing()`: default false. Both join `equals` and `hashCode`. No `@TernMethod`; javadoc `@hidden`.
  - `public boolean DefaultAxis.createZeroLine(DefaultAxis axis, Scale cross)`: removes this axis's previous zero line from the `VGraph`, then adds a new one when the spec has a colour and the condition in Global Constraints holds (`cross` is checked only when `isZeroLineCrossing()`). Returns whether it added one.
  - `public GridLine DefaultAxis.getZeroLine()`: the line, or null.
  - `public void DefaultAxis.setZeroGridLineHidden(boolean)` (`@hidden`; `RectCoord` is in another package): when true, `createGridLines` skips the line at value 0.

- [ ] **Step 1: Write the failing tests** (`DefaultAxisZeroLineTest`; an interval `EGraph` with a "State" category on X and "Quantity" on Y, 400×200, laid out as in `VGraphTopInsetTest`)
  - `aValueAxisCrossingZeroDrawsTheZeroLine`: values −50 to 100, colour `#85827A` on the Y spec → the left axis's `getZeroLine()` is not null, its colour is `#85827A`, its z-index is `GDefaults.AXIS_Z_INDEX`, and its shape is horizontal and strictly inside the plot's height.
  - `zeroAtTheEdgeDrawsNone`: values 0 to 100, and −100 to 0 → null both times, and the grid still has a darkened line at 0 (`GTool.darken(gridColor, 0.85)`).
  - `theZeroGridLineIsLeftOut`: values −50 to 100 → the grid axis has one grid line fewer than the same graph without the colour, and none at the zero line's position.
  - `itDrawsWithTheGridOff`: Y grid style `GraphConstants.NONE` → a zero line, and no grid lines.
  - `aLogScaleDrawsNone`: a `LogScale` on Y → null.
  - `aReversedScaleStillDraws`: `setReversed(true)` → a zero line.
  - `crossingRequiresTheOtherAxis`: a point `EGraph` with measures on X and Y and `setZeroLineCrossing(true)` on the X spec; X −10 to 10 with Y 5 to 20 → no X zero line; X −10 to 10 with Y −5 to 20 → an X zero line, vertical.
  - `aHorizontalChartDrawsItVertically`: the coordinate transposed (`coord.transpose()`) → the zero line is vertical.
  - `eachFacetPanelDrawsItsOwn`: a `FacetCoord` with an outer "Region" on X (two values) around the interval coord, values −50 to 100 in both → each of `getInnerCoordinates()`'s left axes has a zero line.
  - `aSeparateSecondaryScaleKeepsItsZeroGridLine`: a second Y scale (`setYScale2`) whose range −10 to 10 has a 0 tick, primary −50 to 100 with the colour → the primary has a zero line, and the grid (drawn from the secondary) still has its darkened line at the secondary's 0.
  - `classicDrawsNoZeroLine`: no colour, values −50 to 100 → no axis has a zero line, and the grid has exactly one line at 0, darkened (`GTool.darken(gridColor, 0.85)`).

- [ ] **Step 2: Run them and see them fail**

Run: `./mvnw -q test -pl core "-Dtest=DefaultAxisZeroLineTest" -Dsurefire.failIfNoSpecifiedTests=false`
Expected: compile failure on `setZeroLineColor` and `getZeroLine`.

- [ ] **Step 3: Implement**
  - **`AxisSpec`:** the two fields and accessors.
  - **`DefaultAxis.createZeroLine`:** the position is `getTickLocations(new double[] {getScale().map(0)})[0]`, transformed by this axis's and `axis`'s screen transforms as `createGridLines` does for one tick with `asc` true. The line is a `GridLine(shape, this, GDefaults.AXIS_Z_INDEX, true)` with `setColor` the spec's colour and `setStyle(GraphConstants.THIN_LINE)`, added with `vgraph.addVisual`.
  - **`DefaultAxis.createGridLines`:** when the hidden flag is set, skip the tick whose value is 0 (the same `svals[i]` test the darkening uses, without the `cnt > 3` and `i > 0` conditions).
  - **`RectCoord`:** a private `createZeroLine(DefaultAxis primary, DefaultAxis other, DefaultAxis gridAxis, Scale cross)` that calls `primary.createZeroLine(other, cross)` and then `gridAxis.setZeroGridLineHidden(drawn && gridAxis.getScale() == primary.getScale())`. Call it before each `createGridLines` site in `createAxis` and in `fit`, outside the `facet == null` guard: X with `(xaxis1, xaxis2, xaxis2, yscale)`, Y with `(yaxis1, yaxis2, yaxis2, xscale)`. Where the code falls back to `yaxis1.createGridLines(yaxis2, true)` (`:322`, `:622`), mark `yaxis1` the same way.

- [ ] **Step 4: Run the tests and see them pass**

Run: `./mvnw -q test -pl core "-Dtest=DefaultAxisZeroLineTest,RectCoordSecondaryAxisTest,RectCoordOppositeSideLabelsTest,VGraphTopInsetTest" -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS, 0 failures.

- [ ] **Step 5: Commit** (controller): `Chart defaults S8, draw a zero line on a value axis that crosses zero`

---

### Task 2: The generator turns the zero line on (S8-1, D133, D134)

**Files:**
- Create: `report/composition/graph/ModernZeroLine.java`
- Modify: `report/composition/graph/GraphGenerator.java` (`createAxisSpec:2875-2924`, after `setupAxisSpec`)
- Test: `core/src/test/java/inetsoft/report/composition/graph/ModernZeroLineTest.java` (new, Spring)

**Interfaces:**
- Consumes: `AxisSpec.setZeroLineColor`, `setZeroLineCrossing` (Task 1).
- Produces (package-private, `final class ModernZeroLine`):
  - `static boolean appliesTo(int rtChartType)`: the type list in Global Constraints.
  - `static void apply(AxisSpec spec, int rtChartType, Scale scale, boolean x, boolean secondary, VizContext ctx)`: when `ctx.modern`, `!secondary`, `scale instanceof LinearScale` and `appliesTo(rtChartType)`, sets the colour to `spec.getLineColor()`, and `setZeroLineCrossing(x)`. Otherwise leaves both off.

- [ ] **Step 1: Write the failing tests** (`ModernZeroLineTest`; generator charts built with the copied `graph(...)` helper; read the Y spec with `coord.getYScale().getAxisSpec()` on the `RectCoord`)
  - `typesFollowTheFamilyPages`: `appliesTo` is true for `CHART_BAR`, `CHART_BAR_STACK`, `CHART_INTERVAL`, `CHART_LINE`, `CHART_STEP`, `CHART_AREA`, `CHART_STEP_AREA`, `CHART_POINT`, `CHART_SCATTER_CONTOUR`, `CHART_WATERFALL`, `CHART_PARETO`, `CHART_CANDLE`, `CHART_STOCK`, `CHART_BOXPLOT`; false for `CHART_3D_BAR`, `CHART_GANTT`, `CHART_FUNNEL`, `CHART_MEKKO`, `CHART_RADAR`, `CHART_PIE`, `CHART_TREEMAP`, `CHART_TREE`, `CHART_MAP`.
  - `aMarkedBarGetsTheAxisLineColour`: a marked bar ("State", "Quantity") → the Y spec's zero line colour is `#85827A`; with `VizMark.MODERN_DARK` it is `#7E7B86`.
  - `anAuthorsAxisColourReachesIt`: the Y axis descriptor's line colour set to red as the author → red.
  - `theXOfAScatterNeedsBothAxes`: a marked point chart with measures on X and Y → the X spec has the colour and `isZeroLineCrossing()`; the Y spec has the colour and not crossing.
  - `onlyThePrimaryValueAxisGetsIt`: a marked bar with a second measure on the secondary axis → the secondary spec has no colour.
  - `aMultiStyleChartUsesTheMeasuresType`: a multi-style chart (`setMultiStyles(true)`) whose Y measure is a line. Premise: `ModernZeroLine.appliesTo(cinfo.getRTChartType())` is false. → the Y spec has the colour, because the measure's own type decides.
  - `classicChartsGetNone`: no mark → null on every spec.

- [ ] **Step 2: Run them and see them fail**

Run: `./mvnw -q test -pl core "-Dtest=ModernZeroLineTest" -Dsurefire.failIfNoSpecifiedTests=false`
Expected: compile failure on `ModernZeroLine`.

- [ ] **Step 3: Implement** `ModernZeroLine` and the call in `createAxisSpec` (type per C3).

- [ ] **Step 4: Run the tests and see them pass**

Run: `./mvnw -q test -pl core "-Dtest=ModernZeroLineTest,ModernAxisTitleTest,ModernMarkRulesTest" -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS, 0 failures.

- [ ] **Step 5: Commit** (controller): `Chart defaults S8, turn on the zero line on marked value axes`

---

### Task 3: Radar rings in the graph package (S8-2, D130, D135)

**Files:**
- Modify: `graph/guide/axis/DefaultAxis.java` (new `createGridLinesAt`)
- Modify: `graph/coord/AbstractParallelCoord.java` (`evenRings`, `createAxis:99-123`, `equals` if it has one)
- Modify: `graph/coord/PolarCoord.java` (`sidesFromSpokes` next to `webGrid` at `:1349`, `getSidesCount:1304`, `isSameScale:602` package-private, `equals:1270`)
- Test: `core/src/test/java/inetsoft/graph/coord/RadarWebTest.java` (new, Spring)

**Interfaces:**
- Produces:
  - `public boolean DefaultAxis.createGridLinesAt(DefaultAxis axis, double[] fractions)`: like `createGridLines(axis, true)` with lines at `fraction × getLength()` instead of the ticks, no zero darkening, nothing for grid style NONE.
  - `AbstractParallelCoord.setEvenRings(boolean)` / `isEvenRings()`: default false. When true and some spoke pair fails `PolarCoord.isSameScale`, `createAxis` draws every ring segment with `createGridLinesAt(next, {0.25, 0.5, 0.75, 1.0})`; otherwise as today.
  - `PolarCoord.setSidesFromSpokes(boolean)` / `isSidesFromSpokes()`: default false. When true and the inner coordinate is an `AbstractParallelCoord`, `getSidesCount()` returns `getParallelScales().length`.
  - `static boolean PolarCoord.isSameScale(Axis a, Axis b)`: package-private, unchanged body.

- [ ] **Step 1: Write the failing tests** (`RadarWebTest`; a `ParallelCoord` of three `LinearScale`s inside a `PolarCoord` with `setWebGrid(true)`, each spoke spec with grid style THIN, laid out at 400×400)
  - `equalSpokesRingAtTheirTicks`: three scales 0 to 100, `setEvenRings(true)` → each spoke's grid lines sit at its ticks (count equals the ticks drawn without `evenRings`).
  - `unequalSpokesGetQuarterRings`: scales 0 to 100, 0 to 7 and 0 to 2,000, `setEvenRings(true)` → every spoke has 4 ring segments, at 25%, 50%, 75% and 100% of the radius.
  - `unequalSpokesWithoutTheFlagKeepTodaysRings`: same scales, flag off → each spoke's segments follow its own ticks.
  - `aOneMeasureRadarDrawsPolygons`: a `OneVarParallelCoord` with 5 category values, `setSidesFromSpokes(true)` → no ring segment shape is an `Arc2D`; with the flag off they are arcs.
  - `ringsDrawUnderTheSeries`: every ring's z-index is `GDefaults.GRIDLINE_Z_INDEX`, below the area element's.
  - `classicRadarIsUnchanged`: no flags, spoke grid NONE → no grid lines at all.

- [ ] **Step 2: Run them and see them fail**

Run: `./mvnw -q test -pl core "-Dtest=RadarWebTest" -Dsurefire.failIfNoSpecifiedTests=false`
Expected: compile failure on `setEvenRings`.

- [ ] **Step 3: Implement** the three classes. Add both new flags to the coordinates' `equals` where the class compares its flags (`PolarCoord:1270` does).

- [ ] **Step 4: Run the tests and see them pass**

Run: `./mvnw -q test -pl core "-Dtest=RadarWebTest,PieDirectionTest,ModernRadarLabelTest" -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS, 0 failures.

- [ ] **Step 5: Commit** (controller): `Chart defaults S8, draw radar rings as a web with even steps on unequal spokes`

---

### Task 4: The radar's grid, spokes and dialog row on marked charts (S8-2, D135)

**Files:**
- Modify: `report/composition/graph/GraphGenerator.java` (`AbstractParallelCoord` branch, `:2513-2550`, after `setupAxisSpec`)
- Modify: `report/composition/graph/RadarGraphGenerator.java` (`:163-164`)
- Modify: `web/composer/model/vs/ChartLinePaneModel.java` (constructor `:34`, `initPlotComVisible:273-284`, new field), `web/composer/vs/dialog/ChartPropertyDialogService.java` (`:120`)
- Modify: `web/projects/portal/src/app/vsobjects/model/chart-line-pane-model.ts`, `web/projects/portal/src/app/vsobjects/dialog/graph/chart-line-pane.component.html` (`:21-43`)
- Test: `core/src/test/java/inetsoft/report/composition/graph/ModernRadarWebTest.java` (new, Spring), `core/src/test/java/inetsoft/web/composer/model/vs/ChartLinePaneModelRadarTest.java` (new), `web/projects/portal/src/app/vsobjects/dialog/graph/chart-line-pane.component.spec.ts` (new, or extend if one exists)

**Interfaces:**
- Consumes: `AbstractParallelCoord.setEvenRings`, `PolarCoord.setSidesFromSpokes` (Task 3).
- Produces:
  - `public ChartLinePaneModel(ChartInfo info, PlotDescriptor plotDesc, VizContext ctx)`, replacing the two-argument constructor (one caller, `ChartPropertyDialogService:120`, which passes `VizContext.of(chartAssemblyInfo)`).
  - `boolean isXGridLineVisible()` / `setXGridLineVisible(boolean)` on `ChartLinePaneModel`, serialized as `xGridLineVisible`; in TypeScript `xGridLineVisible?: boolean`.

- [ ] **Step 1: Write the failing generator tests** (`ModernRadarWebTest`; a three-measure radar as in `ModernRadarLabelTest.radar`)
  - `aMarkedRadarRingsInTheGridColour`: marked → every spoke spec has grid style THIN and grid colour `#D6D2C8`, and line colour `#D6D2C8`; dark mark → `#4A484F`.
  - `anAuthorsGridStyleWins`: `plotdesc.setYGridStyle(GraphConstants.NONE)` as the author → spoke grid NONE.
  - `anAuthorsSpokeColourWins`: a spoke axis descriptor's line colour red → red.
  - `theWebIsOnForMarkedRadars`: the `PolarCoord` has `isWebGrid()` and `isSidesFromSpokes()`, and the inner coordinate `isEvenRings()`.
  - `classicRadarsAreUnchanged`: no mark → spoke grid NONE, line colour as today (`GDefaults.DEFAULT_LINE_COLOR`), and all three flags false.

- [ ] **Step 2: Write the failing dialog tests**
  - `ChartLinePaneModelRadarTest`: a marked radar → `isGridLineVisible()` true and `isXGridLineVisible()` false; a classic radar → both false; a marked and a classic bar → both true; a marked pie → both false.
  - `chart-line-pane.component.spec.ts`: with `xGridLineVisible: false` and `gridLineVisible: true` the `[data-test="x-grid-line"]` row is absent and `[data-test="y-grid-line"]` is present; with `xGridLineVisible` undefined both are present.

- [ ] **Step 3: Run them and see them fail**

Run: `./mvnw -q test -pl core "-Dtest=ModernRadarWebTest,ChartLinePaneModelRadarTest" -Dsurefire.failIfNoSpecifiedTests=false`, then the portal spec.
Expected: failures on the grid style and on the missing constructor.

- [ ] **Step 4: Implement**
  - **Parallel branch:** when `vizContext.modern`, after `setupAxisSpec`: `axis.setGridStyle(plotdesc.getYGridStyle())`, `axis.setGridColor(plotdesc.getYGridColor())`, `axis.setLineColor(VSChartChromeDefaults.resolveSpokeColor(axisD.getLineColor(), vizContext))`.
  - **`RadarGraphGenerator`:** next to `setSpokeLabelRule`, when modern, `polarCoord.setWebGrid(true)`, `polarCoord.setSidesFromSpokes(true)` and `((AbstractParallelCoord) coord).setEvenRings(true)`.
  - **`ChartLinePaneModel.initPlotComVisible`:** a marked `GraphTypes.isRadar(cinfo.getRTChartType())` chart keeps `gridLineVisible` true and sets `xGridLineVisible` false; otherwise `xGridLineVisible = gridLineVisible`.
  - **Template:** wrap the X grid column in `@if (model.xGridLineVisible !== false)`. No comment in the template.

- [ ] **Step 5: Run the tests and see them pass**

Run: `./mvnw -q test -pl core "-Dtest=ModernRadarWebTest,ChartLinePaneModelRadarTest,ModernRadarLabelTest" -Dsurefire.failIfNoSpecifiedTests=false`, then the portal spec, then the dialog's testing-library spec on its own target: `npx ng run portal:test-tl --include=src/app/vsobjects/dialog/graph/chart-property-dialog.component.tl.spec.ts` (never the whole TL suite; `ng test portal` with a `.tl.spec` include matches nothing and exits 0).
Expected: PASS, 0 failures.

- [ ] **Step 6: Commit** (controller): `Chart defaults S8, ring marked radars in the grid colour and offer the Y grid row`

---

### Task 5: Round time units that fit their labels (S8-7, D143)

**Files:**
- Modify: `graph/scale/TimeScale.java` (fields near `:874`, end of `init` after `:238`, `getTicks:488-627`, `copy`/`clone` if it copies fields)
- Test: `core/src/test/java/inetsoft/graph/scale/TimeScaleTest.java` (extend)

**Interfaces:**
- Produces:
  - `TimeScale.setLabelFit(double plotPx, double labelPx)`: both 0 = off (default). `double getLabelFitPlotPx()`, `double getLabelFitLabelPx()`.
  - `static int[] TimeScale.chooseFitUnit(long rangeMs, long dataUnitMs, int maxTicks)` (package-private): `{type, step}` from the candidate list in the design calls, ascending by length; the first with length ≥ `dataUnitMs` and `floor(rangeMs / length) + 1 ≤ maxTicks`; if none, the last candidate.
  - `int getFitStep()`: 0 when the fit is off or did not run.

- [ ] **Step 1: Write the failing tests** (add to `TimeScaleTest`)
  - `aDailySpanOfFourMonthsGetsMonths`: `chooseFitUnit(120 days, 1 day, 7)` → `{MONTH, 1}`.
  - `aDailySpanOfThreeWeeksGetsDays`: `chooseFitUnit(21 days, 1 day, 30)` → `{DAY, 1}`; with budget 10 → `{WEEK, 1}`.
  - `anIntradaySpanKeepsHours`: `chooseFitUnit(10 hours, 1 hour, 7)` → `{HOUR, 3}`.
  - `neverFinerThanTheData`: `chooseFitUnit(3 days, 1 week, 50)` → `{WEEK, 1}`.
  - `aZeroRangeOrTinyBudgetStillPicksAUnit`: `chooseFitUnit(0, 1 day, 7)` → `{DAY, 1}`; `chooseFitUnit(500 years, 1 day, 2)` → `{YEAR, 100}`.
  - `theFitSetsTheTypeAndStep`: a scale over daily dates from 2026-01-01 to 2026-04-30, `setLabelFit(300, 45)`, `init` → `getType()` is `MONTH`, `getFitStep()` 1, the ticks are the first of each month and there is no tick at the max.
  - `anAuthorIncrementTurnsTheFitOff`: the same with `setIncrement(7)` → `getType()` is `DAY` and `getFitStep()` 0.
  - `anExplicitTypeTurnsTheFitOff`: `setType(WEEK)` → stays `WEEK`, `getFitStep()` 0.
  - `noFitIsTodaysTicks`: no `setLabelFit` → the ticks equal those of a scale built without it.

- [ ] **Step 2: Run them and see them fail**

Run: `./mvnw -q test -pl core "-Dtest=TimeScaleTest" -Dsurefire.failIfNoSpecifiedTests=false`
Expected: compile failure on `chooseFitUnit`.

- [ ] **Step 3: Implement**
  - **End of `init`:** when the fit is on, `increment == null` and `type0 == null`: `maxTicks = max(2, floor(plotPx / labelPx))`, `chooseFitUnit(cmax − cmin, length of type, maxTicks)`, then `type` and `fitStep` from it and `cacheSpec = null`.
  - **`getTicks`:** when `fitStep > 0`, step by `fitStep` units of `type`, skip the crowding loop and the duplicate-format step type, and add no tick at the max.

- [ ] **Step 4: Run the tests and see them pass**

Run: `./mvnw -q test -pl core "-Dtest=TimeScaleTest,ModernGanttEndTest" -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS, 0 failures.

- [ ] **Step 5: Commit** (controller): `Chart defaults S8, pick a round time unit that fits a label on every tick`

---

### Task 6: Marked Gantts fit their ticks and label them on the tick (S8-7, D143)

**Files:**
- Modify: `report/composition/graph/GanttGraphGenerator.java` (`fixGanttCoord:141-169`)
- Test: `core/src/test/java/inetsoft/report/composition/graph/ModernGanttTicksTest.java` (new, Spring)

**Interfaces:**
- Consumes: `TimeScale.setLabelFit`, `getFitStep` (Task 5); the existing plot estimate `plot` in `fixGanttCoord`.

- [ ] **Step 1: Write the failing tests** (`ModernGanttTicksTest`; the copied `gantt(count, mark, size)` builder, 25 daily tasks spread over January to April 2026, 400×240)
  - `aMarkedGanttLabelsEveryTick`: marked → the time scale's `getLabelFitPlotPx()` equals the plot estimate (positive) and `getLabelFitLabelPx()` is `GTool.stringWidth(new String[] {"Sep 2026"}, font) + 4`; its spec `isLabelBetween()` is false.
  - `gridLinesSitOnlyAtLabelledTicks`: marked, after `plotAndLayout` at 400×240 → every visible grid line (`zIndex >= 0`) of the time axis sits at a tick whose label is visible, and the shown labels are month names ("Jan", "Feb", …, the year once on the first).
  - `anAuthorIncrementKeepsTheClassicAxis`: the time axis descriptor's increment 7 → no fit (`getLabelFitPlotPx()` 0) and `isLabelBetween()` true.
  - `anIntradayGanttKeepsHourTicks`: tasks within one day, starts an hour apart → after layout the scale's `getType()` is `HOUR`.
  - `classicGanttsAreUnchanged`: no mark → no fit and `isLabelBetween()` true.

- [ ] **Step 2: Run them and see them fail**

Run: `./mvnw -q test -pl core "-Dtest=ModernGanttTicksTest" -Dsurefire.failIfNoSpecifiedTests=false`
Expected: failures on the fit and on `isLabelBetween()`.

- [ ] **Step 3: Implement** in `fixGanttCoord`: `setLabelBetween(true)` only when the fit is not applied. Apply the fit when `vizContext.modern`, the scale is a `TimeScale` and `getIncrement() == null`, with the existing `plot` and the label width from Global Constraints and the design calls (the time axis font is `yScale.getAxisSpec().getTextSpec().getFont()`).

- [ ] **Step 4: Run the tests and see them pass**

Run: `./mvnw -q test -pl core "-Dtest=ModernGanttTicksTest,ModernGanttEndTest,ModernAxisLabelRuleTest" -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS, 0 failures.

- [ ] **Step 5: Commit** (controller): `Chart defaults S8, label every tick of a marked Gantt's time axis`

---

### Task 7: Whole-branch check and live-check handoff

**Files:**
- Create: `E:/Temp/chart-defaults-s8a-checks-handoff.md` (outside the repo, not committed)

- [ ] **Step 1: Run the graph, generator and dialog packages**

Run: `./mvnw -q test -pl core "-Dtest=inetsoft.graph.**.*Test,inetsoft.report.composition.graph.**.*Test,inetsoft.web.composer.model.vs.**.*Test" -Dsurefire.failIfNoSpecifiedTests=false`
Expected: 0 failures. Report the counts from the surefire reports.

- [ ] **Step 2: Run the portal dialog specs**

Run: `npx ng test portal --include="src/app/vsobjects/dialog/graph/**/*.spec.ts"` from `$WT/web`, then the Task 4 TL command.
Expected: 0 failures.

- [ ] **Step 3: Write the handoff** for a separate live-check session, base `5ff9c37362` against this branch's head. One row per §11 S8a bullet, with the chart, the card size, what to look at and the expected result, plus:
  - classic identity: the same classic dashboard's tiles, PNG and PDF exports identical across the two builds
  - a 25-task and a 60-task Gantt at 400×240, light and dark, compared with the bundle's Gantt drawn card
  - a one-measure, a three-measure (unequal ranges) and a filled radar, compared with the bundle's radar drawn card
  - a bar and a line with negative values, a scatter crossing zero on one axis and on both, a faceted bar and a dual-axis bar
  - the Lines tab on a marked radar: only the Y grid row; setting it to none removes the rings
  - Results column empty, filled by the checking session.

- [ ] **Step 4: Report** (no commit): package counts, the portal result and the handoff path.
