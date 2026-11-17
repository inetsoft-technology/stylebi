# Chart Defaults S6a — Top Gap, Pie, Contour Map and Gantt End Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** On a modern (marked) chart:
- a titled chart keeps its top padding between the title lane and the plot
- a pie or donut is at most 80% of the plot's shorter side, and starts at 12 o'clock and runs clockwise
- a contour map zooms to its points, and the nav bar can zoom out to the world
- a Gantt keeps the density padding past its last date

Classic charts are unchanged.

**Architecture:** Every item is a drawing rule (kind D). Nothing is stored, no XML changes, and no seed, flag, reset slot or residue rule is involved.
- **Graph package.** New properties default to off, and off draws today's output (M1):
  - `EGraph.topInset`, read by the top-level `VGraph` in `layout`
  - `PolarCoord.pieCapRatio` and `PolarCoord.clockwiseFromTop`
  - `GeoCoord.zoomOutToLayer`
  - `TimeScale.endPadRatio`
- **Generators.**
  - `VGraphPair` sets the top inset from the assembly.
  - `GraphGenerator.createPolarCoord` sets the two pie properties.
  - `MapGenerator` fits a marked contour map and pins its stored extent.
  - `GanttGraphGenerator.fixGanttCoord` sets the end pad.

**Tech Stack:** Java 21, JUnit 5, Spring test context (`@SreeHome`), Maven module `core`. No web change.

**Spec:** `docs/superpowers/specs/lookfeel/2026-11-06-chart-defaults-design.md` (S6 amended in `bab561703f`). The relevant parts are:
- §3 M1 and M9
- §5 S6: the shaping facts, rows S6-1 to S6-3 and S6-7 to S6-9, and the sections "S6-1 top gap", "S6-2 pie cap", "S6-7 contour map", "S6-8 pie start" and "S6-9 Gantt end"
- §9's S6 items, and §11 "Padding and sizing (S6)"
- §10 D94 to D96 and D99 to D105

**Builds on:** S5 (`bug-80455-s5` at `2bd6b1d681`). Uses `ModernLabelRule.columnLimitPx(Font, double)` (package-private, `report/composition/graph`) and `VSDensityDefaults.chartPadding(VizContext)`.

**Verified against:** `bug-80455-s6` at `bab561703f`. Paths are under `core/src/main/java/inetsoft/` unless they start with `core/`.

## Corrections to the spec

| # | Spec | Code at `bab561703f` | This plan |
|---|---|---|---|
| C1 | S6-1: "The band is inside the graph area" | `VGraphPair.initGraph0` lays the graph out in `width − left − right` by `height − top − bottom` (`:675-677`). `VGraph.layout(x, y, w, h)` (`:606`) hands `layout0` the content box, and graph space is y-up, so the top is `y + h`. Facet cells are `VGraph`s of the same `EGraph` with a parent coordinate (`VGraph.java:166`) | The top-level `VGraph` lays out in `h − topInset` and leaves the band at the top. Sub-graphs ignore the inset. `VGraphPair` sets it on `egraph` and `egraph2`, before both plots |
| C2 | S6-8: "the full-circle and explode tests in `BarVO`" | A mirrored slice sweeps the other way, so its `Arc2D` extent changes sign after the fit transform (`BarVO.transformShape:1445-1470` recomputes the angles). The label's extent goes to `ArcVOText` at `BarVO:652`, and `ArcHelper:34-39` builds its quadrant range from it. The explode's mid angle (`:1540-1566`) does not depend on the sign; its `extent == 360` check does | Keep the label code on the classic sign: negate the extent handed to `ArcVOText` when the coordinate draws clockwise. Compare the absolute extent with 360 in the full-circle checks (`:166`, `:1551-1553`); a classic extent is never negative there, so classic is unchanged |
| C3 | S6-7: "polygon layers taken out of the plot fit" for an untouched map | A pinned `lonlat` (`setExtent`) is overridden too if the polygons stay in the plot fit: `GeoCoord.fit` shrinks the map until every World polygon fits. The data-zoom path sets `elem.setInPlot(false)` for that reason (`MapGenerator:1209-1211`) | On every marked non-web, non-facet contour map, fitted or pinned, polygons are out of the plot fit |
| C4 | S6-7: "the nav bar may zoom out below 1 … up to the whole layer" | `GeoCoord.init` clamps a non-web `zoom` to at least 1 (`:398-401`) and writes the field. `VSChartMapService.applyZoomPan:152-217` sets the zoom on the coordinate, replots, and stores the coordinate's clamped zoom (`:213-215`). With `fullMap` off, `init` walks only the data rows (`:278-283`), so the layer's bounds are not known | Under `zoomOutToLayer`, `init` measures the whole layer (every `GeoShape` up to `getFullRowCount()`) and clamps `zoom` to `max(zoom, max(rx / layerRx, ry / layerRy))` instead of 1. The service needs no change |
| C5 | S6-9: "converted from pixels with the estimated plot width" | `graphSize` in the generator is the content size, padding included (`VGraphPair.initSize:268`). The Gantt's time axis is the transposed coordinate's Y scale (`GanttGraphGenerator.fixGanttCoord:140-155`) | `plotPx = graphWidth − padding.left − padding.right − columnLimitPx(task label font, graphWidth)`. The ratio `p / (plotPx − p)` makes the pad `p` pixels on an axis `plotPx` long; it is 0 when `plotPx ≤ 2p` |

**Design calls (§9 left these to the builder):**
- **S6-1 band in a scrolling chart:** both `egraph` and `egraph2` carry the inset, so the expanded graph's plot tiles line up with the real-size graph's.
- **S6-8 script transforms:** "a script set it" means `getCoordTransform()` of the `PolarCoord` is not the identity when the shape is mapped. The generator only transforms the inner coordinate (`GraphGenerator:2586`).
- **S6-8 labels:** `ArcHelper` is left as it is unless Task 3's label tests fail. If one fails, make the range direction-agnostic only behind `clockwiseFromTop` (C2).
- **S6-7 layer bounds with no shapes:** the projection's own extent (`projection.getXMin()` … `getYMax()`).
- **S6-9 author maximum:** `TimeScale.setMax` (`umax != null`) turns the pad off. An author minimum does not.

**Known limitations:**
- **Older builds** draw today's layout, pie, map and Gantt (M9).
- **The Gantt pad is an estimate.** On a 400×240 card with short task names it comes out at about 15px rather than 12.
- **A contour map zoomed while marked** draws that zoom against the world after Revert or in an older build (D105).
- **The top gap costs plot height:** 12px at compact on every titled marked chart except the contour map.

## Global Constraints

- **Worktree:** `$WT` is the executor's worktree on branch `bug-80455-s6`, at or after this plan's commit. Never work in `E:/StyleBI/stylebi-enterprise/community` or any other worktree.
- **Shared machine.** Other sessions may run servers and live checks from other worktrees, using the shared Maven repository and the shared asset data. So:
  - Never run `mvnw install`, `mvnw package -pl server`, `spring-boot:run`, or anything else that writes outside `$WT`.
  - Never start a server or bind or call ports 8080, 8081 or 5005.
  - Run only the test classes a step names (Task 6 names packages). The full `core` suite runs only when the controller says so.
- **Git:**
  - Each git command is its own call, `git -C "$WT" ...`, with no `&&` anywhere in the command.
  - One commit per task, subject `Chart defaults S6, <what changed>`, last line `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
  - Subagents never run `git add`, `git commit` or `git stash` (a hook blocks them); the controller stages and commits after review. Never push.
- **Scope is the mark (M1).** Every new graph-package property defaults to off, and off draws today's output. A classic chart is pixel-identical.
- **Values (all under the mark):**
  - **Top gap:** the chart's own `ChartVSAssemblyInfo.getPadding().top`, only when `isTitleVisible()`, and never on a contour map (`GraphTypes.isMapContour`).
  - **Pie cap:** `0.8` of `min(w, h)` of the coordinate bounds, as a diameter. Pie types: `GraphTypes.isPie` (pie, 3D pie, donut, multi-style donut).
  - **Pie start:** 12 o'clock, clockwise. Every `isPie` type except `CHART_3D_PIE`.
  - **Contour fit:** margin `0.1`. Applies when `GraphTypes.isMapContour(info.getRTChartType())`, the map is not a web map, it is not faceted, there is no data zoom (`isZooming()`), and `!plot.hasPanZoom() && plot.getLonLat() == null`.
  - **Gantt pad:** `VSDensityDefaults.chartPadding(vizContext).right` pixels (8, 12 or 16).
- **Comments:** a short clause each, with no ticket, PR, slice or design-doc names.
- **New files** start with the AGPL header of `SeedChromeDefaultsTest.java`, `Copyright (C) 2026  InetSoft Technology`.
- **Tests that reach Spring beans** use `@ExtendWith(SpringExtension.class)`, `@ContextConfiguration(classes = BaseTestConfiguration.class, initializers = ConfigurationContextInitializer.class)`, `@SreeHome`, `@Tag("core")`, and clear `viewsheet.modernVisualization`, `viewsheet.darkMode` and `viewsheet.density` in `@AfterEach`.

## Review Focus

1. **Classic charts are untouched**: the graph's layout box, a pie's size, start and label layout, a contour map's world view, the Gantt's last date. Pinned by the `classic…` cases in every task.
2. **Outside labels on a clockwise pie sit beside their slices without overlapping**, including when the labels are moveable (`pieRatio > 0`). Pinned by `outsideLabelsSitBesideTheirSlices` (Task 3).
3. **A scripted rotation and a 3D pie keep the classic start; a donut turns with the pie.** Pinned by `aRotatedCoordKeepsTheClassicStart` and `aDonutAndNot3DPie` (Task 3).
4. **A marked polygon map and a web map keep today's extent rules; a data zoom on a contour map still reframes it.** Pinned by `onlyMarkedNonWebContourMapsFit` (Task 4).
5. **A Gantt whose dates are all equal, or whose plot is too narrow, gets no pad and no NaN.** Pinned by `aZeroRangeOrNarrowPlotGetsNoPad` (Task 5).

## File Structure

- **Task 1:** `graph/EGraph.java` (`topInset`), `graph/VGraph.java` (`createVisuals`, `layout`), `report/composition/graph/VGraphPair.java` (`titleGap`, `initGraph0`).
- **Task 2:** `graph/coord/PolarCoord.java` (`pieCapRatio`, `getRadius`), `report/composition/graph/GraphGenerator.java` (`createPolarCoord`).
- **Task 3:** `graph/coord/PolarCoord.java` (`clockwiseFromTop`, `transformPoint0`, `transformRectangle`, `createShape`, `transformLine`), `graph/visual/BarVO.java` (`:166`, `:652`, `:1551-1553`), maybe `graph/internal/text/ArcHelper.java`, `GraphGenerator.createPolarCoord`.
- **Task 4:** `graph/coord/GeoCoord.java` (`zoomOutToLayer`, `init`), `report/composition/graph/MapGenerator.java` (`createCoord` at `:397-430`, the element loop at `:1209`, after `applyWebMap` at `:437`).
- **Task 5:** `graph/scale/TimeScale.java` (`endPadRatio`, `getMax`), `report/composition/graph/GanttGraphGenerator.java` (`fixGanttCoord`).
- **Task 6:** none (whole-branch check and live-check handoff, with S6-3).

**Test harnesses:**
- **Graph-level tests** build an `EGraph` and lay it out as `core/src/test/java/inetsoft/graph/VGraphTitleHideTest.java` does (`Plotter.getPlotter(graph).plotAndLayout(new DefaultDataSet(rows), 0, 0, w, h)`). Graph space is y-up: a greater `getMaxY()` is higher.
- **Generator tests** copy `core/src/test/java/inetsoft/report/composition/graph/ModernRadarLabelTest.java`'s annotations and `graph(cinfo, rows, mark, desc)` (`VizMark.MODERN_LIGHT` against `null`). Copy the Gantt builder from `ModernAxisLabelRuleTest.ganttChart(int, boolean)`; it is private.

## How to run tests

From `$WT`:

```bash
./mvnw -q test -pl core "-Dtest=ClassA,ClassB" -Dsurefire.failIfNoSpecifiedTests=false
```

- Read `core/target/surefire-reports/*.txt` for counts.
- Run every new or changed class alone once as well (CLAUDE.md).
- If compilation fails with "Attempt to recreate … HeartBeatServiceProxy", delete the untracked `core/target/generated-test-sources`.

---

### Task 1: The top padding between the title lane and the plot (S6-1, D94, D100)

**Files:**
- Modify: `graph/EGraph.java` (field, accessors, `clear()` at `:590-612`)
- Modify: `graph/VGraph.java` (`createVisuals:124-178`, `layout:606-612`)
- Modify: `report/composition/graph/VGraphPair.java` (new `titleGap`, `initGraph0` before `// get size` at about `:674`)
- Test: `core/src/test/java/inetsoft/graph/VGraphTopInsetTest.java` (new, Spring), `core/src/test/java/inetsoft/report/composition/graph/ChartTitleGapTest.java` (new, Spring)

**Interfaces:**
- Produces:
  - `EGraph.setTopInset(double px)` / `double getTopInset()`: default 0, reset to 0 by `clear()`. No `@TernMethod`; javadoc `@hidden`.
  - `static int VGraphPair.titleGap(ChartVSAssemblyInfo ainfo)` (package-private): `ainfo.getPadding().top` when `VizContext.of(ainfo).modern`, `ainfo.isTitleVisible()` and `!GraphTypes.isMapContour(chart info's getRTChartType())`; otherwise 0. A null chart info counts as not a contour map.

- [ ] **Step 1: Write the failing graph tests** (`VGraphTopInsetTest`; a bar `EGraph` as in `VGraphTitleHideTest.graph`, 400×200)
  - `theInsetLowersThePlotTop`: inset 12 → `getPlotBounds().getMaxY()` is 12 below the no-inset graph's, and `getMinY()` is equal.
  - `noInsetIsTodaysLayout`: inset 0 → plot bounds equal a graph that never set it.
  - `aTopLegendMovesDownWithTheContent`: a colour frame on "State" with `setLegendLayout(GraphConstants.TOP)`, inset 12 → the legend's `getBounds().getMaxY()` is 12 lower.
  - `facetCellsIgnoreTheInset`: a `FacetCoord` with an outer "Region" on Y (two values) around the bar coord, inset 12 → each of `getInnerCoordinates()`'s cells is about 6px shorter (12 over two rows, ±1.5) than without the inset, never 12.
  - `theInsetIsClearedWithTheGraph`: set 12, `clear()` → 0.

- [ ] **Step 2: Write the failing helper tests** (`ChartTitleGapTest`, `ChartVSAssemblyInfo` with a `VSChartInfo` bar, padding `new Insets(12, 12, 12, 12)`)
  - `aMarkedTitledChartGapsByItsTopPadding`: `VizMark.MODERN_LIGHT`, title visible → 12.
  - `anAuthorsPaddingIsTheGap`: padding top 20 → 20; top 0 → 0.
  - `aHiddenTitleAddsNoGap`: `setTitleVisible(false)` → 0.
  - `aContourMapHasNoGap`: `VSMapInfo` with RT type `CHART_MAP_CONTOUR` → 0; with `CHART_MAP` → 12.
  - `classicChartsHaveNoGap`: no mark → 0.

- [ ] **Step 3: Run them and see them fail**

Run: `./mvnw -q test -pl core "-Dtest=VGraphTopInsetTest,ChartTitleGapTest" -Dsurefire.failIfNoSpecifiedTests=false`
Expected: compile failure on `setTopInset` and `titleGap`.

- [ ] **Step 4: Implement**
  - **`EGraph`:** the field and accessors next to `legendInMarkOrder`; `clear()` resets it.
  - **`VGraph.createVisuals`:** inside the existing `if(coord.getParentCoordinate() == null)` block, `topInset = egraph.getTopInset()`. Sub-graphs keep 0.
  - **`VGraph.layout`:** after `w--; h--;`, call `layout0(x, y, w, Math.max(0, h − topInset))`. `setBounds` keeps the full box.
  - **`VGraphPair.initGraph0`:** before `// get size`, `int gap = titleGap(ainfo)`; `egraph.setTopInset(gap)`; the same on `egraph2` when it is not null.

- [ ] **Step 5: Run the tests and see them pass**, plus `VGraphTitleHideTest`, `ModernAxisLabelTest`, `ModernAxisLabelRuleTest` and `ChartChromeRenderTest` unchanged.

- [ ] **Step 6: Commit** — `Chart defaults S6, keep the top padding between the title lane and the plot`

---

### Task 2: Cap an automatic pie at 80% of the plot's shorter side (S6-2, D95, D103)

**Files:**
- Modify: `graph/coord/PolarCoord.java` (field after `pieRatio` at `:1277`, accessors near `setPieRatio:1154`, `getRadius:1085-1124`, `equalsContent:1194-1203`)
- Modify: `report/composition/graph/GraphGenerator.java` (`createPolarCoord:933-950`)
- Test: `core/src/test/java/inetsoft/graph/coord/PieCapTest.java` (new, Spring), `core/src/test/java/inetsoft/report/composition/graph/ModernPieTest.java` (new, Spring)

**Interfaces:**
- Produces: `PolarCoord.setPieCapRatio(double)` / `double getPieCapRatio()`: default 0 (none), `@hidden`, part of `equalsContent`. When above 0 and `pieRatio == 0`, `getRadius` takes `min(automatic radius, min(w, h) × cap / 2)` before its final "leave gap" line.

- [ ] **Step 1: Write the failing graph tests** (`PieCapTest`; an `IntervalElement("State", "Sales")` stacked on a `PolarCoord` of type `THETA`, rows A/B/C, no text frame)
  - `aCappedPieIsAtMost80PercentOfTheShorterSide`: cap 0.8 at 400×200 → `2 × getRadius() ≤ 0.8 × min(coord bounds w, h)`.
  - `aTallPlotCapsByItsWidth`: cap 0.8 at 180×400 → the same bound against the width.
  - `withoutTheCapThePieIsTodays`: cap 0 → `getRadius()` equals a coord that never set the cap, and with no labels it is above `0.8 × min(w, h) / 2` (premise: the cap binds a plain pie).
  - `anAuthorRatioWinsOverTheCap`: `setPieRatio(0.6)`, cap 0.8 → `getRadius() == min(w, h) × 0.6 / 2 − 1`.
  - `aPieThatLabelsShrinkIsUnchanged`: a text frame on "State" (outside labels) at 300×150 with long category names → `getRadius()` equals the uncapped graph's.
  - `theCapLeavesTheLabelModeAlone`: cap 0.8, no labels → `isLabelMoveable()` false.
  - `theCapTakesPartInEquality`: two coords differing only in the cap are not `equalsContent`.

- [ ] **Step 2: Write the failing generator tests** (`ModernPieTest`, a pie with "State" on colour and `Sum(Sales)`)
  - `aMarkedPieDonutAnd3DPieAreCapped`: marked `CHART_PIE`, `CHART_DONUT`, `CHART_3D_PIE` → the `PolarCoord`'s `getPieCapRatio() == 0.8`.
  - `classicPiesAreNotCapped`: unmarked → 0.
  - `anAuthorRatioStillReachesTheCoord`: plot `setPieRatio(0.5)`, marked → `getPieRatio() == 0.5`.

- [ ] **Step 3: Run them and see them fail**

Run: `./mvnw -q test -pl core "-Dtest=PieCapTest,ModernPieTest" -Dsurefire.failIfNoSpecifiedTests=false`
Expected: compile failure on `setPieCapRatio`.

- [ ] **Step 4: Implement** the field, the `getRadius` branch, and in `createPolarCoord` inside the existing `isPolar(info, true)` block: `coord.setPieCapRatio(vizContext.modern && GraphTypes.isPie(type) ? 0.8 : 0)`.

- [ ] **Step 5: Run the tests and see them pass**, plus `RadarScaleLabelTest` and `ModernRadarLabelTest` unchanged.

- [ ] **Step 6: Commit** — `Chart defaults S6, cap an automatic modern pie at 80% of the plot`

---

### Task 3: Start a pie at 12 o'clock and run clockwise (S6-8, D102, D103)

**Files:**
- Modify: `graph/coord/PolarCoord.java` (`transformPoint0:398`, `transformRectangle`, `createShape`, the horizontal branch of `transformLine`, field, accessors, `equalsContent`)
- Modify: `graph/visual/BarVO.java` (`transform:166`, the `ArcVOText` extent at `:652`, explode at `:1551-1553`)
- Maybe modify: `graph/internal/text/ArcHelper.java` (constructor `:31-39`), see the design call
- Modify: `report/composition/graph/GraphGenerator.java` (`createPolarCoord`)
- Test: `core/src/test/java/inetsoft/graph/coord/PieDirectionTest.java` (new, Spring), `ModernPieTest`

**Interfaces:**
- Consumes: Task 2's `ModernPieTest` builder.
- Produces:
  - `PolarCoord.setClockwiseFromTop(boolean)` / `boolean isClockwiseFromTop()`: default false, `@hidden`, part of `equalsContent`.
  - `boolean PolarCoord.drawsClockwise()`: the flag and `getCoordTransform().isIdentity()`. `BarVO` and `ArcHelper` read this, not the flag.
  - The mapping, when `drawsClockwise()`: a classic angle θ (counter-clockwise from 3 o'clock) draws at `π/2 − θ`; a classic slice from `start` with `extent` (degrees) draws from `90 − start` with `−extent`, so each slice sweeps clockwise from its 12 o'clock side.

- [ ] **Step 1: Write the failing graph tests** (`PieDirectionTest`; the Task 2 pie at 400×400 with equal values, graph space y-up, centre `c` = the coord bounds' centre, angles from `atan2` of each slice VO's bounds centre)
  - `twoSlicesSplitLeftAndRight`: flag on, slices A, B → A's centre is right of `c` (`x > c.x`, `|y − c.y| < 2`); flag off → A is above `c`.
  - `slicesRunClockwiseFromTwelve`: three equal slices, flag on → A's centre angle is 30° (±2°), B's −90°, C's 150°: each slice's angle is less than the one before, modulo 360.
  - `eachSliceSweepsClockwise`: flag on → `BarVO.getOuterArc(firstSliceVO.getShape())` has the opposite `getAngleExtent()` sign to the same slice with the flag off (the inline-SVG sweep reads it).
  - `outsideLabelsSitBesideTheirSlices`: a text frame on "State", six slices, at 400×300, flag on, for `pieRatio` 0 and 0.7 (moveable labels) → no two `ArcVOText` bounds intersect, and each label's angle from `c` is within 45° of its slice's centre angle.
  - `anExplodedSliceMovesAlongItsMiddle`: `elem.setHint(GraphElement.HINT_EXPLODED, "true")`, three slices, flag on → each slice's centre is farther from `c` than without the hint, at the same centre angle (±2°).
  - `aSingleSliceIsAWholeCircle`: one row, flag on → one slice whose bounds equal the full circle and no exception (the `:166` path).
  - `aRotatedCoordKeepsTheClassicStart`: flag on and `coord.rotate(30)` → the two-slice split matches the flag-off graph rotated by 30.
  - `theFlagTakesPartInEquality`.

- [ ] **Step 2: Write the failing generator tests** (`ModernPieTest`)
  - `aDonutAndNot3DPie`: marked `CHART_PIE`, `CHART_DONUT` → `isClockwiseFromTop()` true; `CHART_3D_PIE` → false.
  - `classicPiesKeepTheClassicStart`: unmarked → false.
  - `aRadarIsNotTurned`: marked `CHART_RADAR` → false.

- [ ] **Step 3: Run them and see them fail**

Run: `./mvnw -q test -pl core "-Dtest=PieDirectionTest,ModernPieTest" -Dsurefire.failIfNoSpecifiedTests=false`

- [ ] **Step 4: Implement**
  - **`PolarCoord`:** when `drawsClockwise()`:
    - `transformPoint0` (all four types) maps its θ to `π/2 − θ`.
    - `createShape` builds `Arc2D(start − 90, extent)` and the `Donut` with the same two angles, in place of `(-start, -extent)`. That is the Interfaces mapping in `Arc2D`'s sign convention. The `Ellipse2D` full-circle branch is unchanged.
    - The horizontal-arc branch of `transformLine` uses the same pair.
  - **`BarVO`:** at `:652` hand `ArcVOText` `−extent` when the coordinate `drawsClockwise()`; at `:166` and `:1551-1553` compare `Math.abs(extent)` with 360 and accept any start when it `drawsClockwise()`.
  - **`ArcHelper`:** only if `outsideLabelsSitBesideTheirSlices` fails after the `BarVO` change.
  - **`createPolarCoord`:** `coord.setClockwiseFromTop(vizContext.modern && GraphTypes.isPie(type) && type != CHART_3D_PIE)`.

- [ ] **Step 5: Run the tests and see them pass**, plus `PieCapTest`, `RadarScaleLabelTest`, `RadarSpokeLabelTest` and `ModernRadarLabelTest` unchanged.

- [ ] **Step 6: Commit** — `Chart defaults S6, start a modern pie at twelve o'clock and run clockwise`

---

### Task 4: Fit a contour map to its points, and let the nav bar zoom out to the world (S6-7, D96, D105)

**Files:**
- Modify: `graph/coord/GeoCoord.java` (field near `:949`, accessors, `init:273-440`, `equalsContent:901`)
- Modify: `report/composition/graph/MapGenerator.java` (new `fitsToData` and `isModernContour`, `createCoord:404-423`, the polygon `setInPlot` at `:1209-1211`, after `applyWebMap(graphCoord)` at `:437`)
- Test: `core/src/test/java/inetsoft/graph/coord/GeoCoordZoomOutTest.java` (new, Spring), `core/src/test/java/inetsoft/report/composition/graph/ModernContourMapTest.java` (new, Spring)

**Interfaces:**
- Produces:
  - `GeoCoord.setZoomOutToLayer(boolean)` / `boolean isZoomOutToLayer()`: default false, `@hidden`, part of `equalsContent`. When true on a non-web map, `init` clamps `zoom` to `max(zoom, max(rx / layerRx, ry / layerRy))` in projected space instead of to 1. `layerRx` and `layerRy` are half the projected bounds of every `GeoShape` up to `getFullRowCount()` and of the lat/lon points, or of the projection's extent when there are none.
  - `static boolean MapGenerator.isModernContour(boolean modern, int rtType, boolean webMap, boolean facet)`: `modern && GraphTypes.isMapContour(rtType) && !webMap && !facet`.
  - `static boolean MapGenerator.fitsToData(boolean modernContour, boolean zooming, PlotDescriptor plot)`: `modernContour && !zooming && !plot.hasPanZoom() && plot.getLonLat() == null`.

- [ ] **Step 1: Write the failing graph tests** (`GeoCoordZoomOutTest`; a `GeoCoord` on `LinearScale("Lon")` and `LinearScale("Lat")` over points in one country, as `MapGenerator` builds it, `setFullMap(false)`, `setPadding(0.1)`)
  - `aFittedMapFramesItsPoints`: the laid-out `getBBox()` contains every point and is no wider than the points' range × 1.3.
  - `withoutTheFlagZoomStaysAtLeastOne`: `setZoom(0.5)` → `getZoom() == 1` after `init` (today).
  - `withTheFlagZoomGoesBelowOne`: flag on, `setZoom(0.5)` → `getZoom() == 0.5`, and the box is wider than at zoom 1.
  - `zoomOutStopsAtTheLayer`: flag on, `setZoom(0.001)` → the box is no wider than the layer's (here the projection's) extent.
  - `theFlagTakesPartInEquality`.

- [ ] **Step 2: Write the failing generator tests** (`ModernContourMapTest`, on the static predicates and a `PlotDescriptor`)
  - `onlyMarkedNonWebContourMapsFit`: `isModernContour` true for `(true, CHART_MAP_CONTOUR, false, false)`; false for classic, `CHART_MAP`, a web map, a facet.
  - `anUntouchedMapFits`: `fitsToData(true, false, new PlotDescriptor())` true.
  - `aTouchedMapIsPinnedNotFitted`: zoom 1.2, pan 5, or a `lonlat` → false; a data zoom (`zooming` true) → false.

- [ ] **Step 3: Run them and see them fail**

Run: `./mvnw -q test -pl core "-Dtest=GeoCoordZoomOutTest,ModernContourMapTest" -Dsurefire.failIfNoSpecifiedTests=false`

- [ ] **Step 4: Implement**
  - **`GeoCoord`:** the field; in `init`, measure the layer only when the flag is on and the map is not web; replace the `Math.max(1, zoom)` clamp with the layer clamp under the flag.
  - **`MapGenerator.createCoord`:** compute `modernContour` once (`vizContext.modern`, `info.getRTChartType()`, `isWebMap()`, `info.isFacet()`). When `fitsToData(...)`, `coord.setFullMap(false)` and `coord.setPadding(0.1)`.
  - **The element loop:** `if((isZooming() || modernContour) && elem instanceof PolygonElement) elem.setInPlot(false)` (C3).
  - **After `applyWebMap`:** when `modernContour`, `setZoomOutToLayer(true)`, and if `plot.getLonLat() != null`, `setExtent(lonlat.getMinX(), lonlat.getMinY(), lonlat.getMaxX(), lonlat.getMaxY())`, as `applyWebMap:1299-1302` does.

- [ ] **Step 5: Run the tests and see them pass**, plus `ChartColorRenderTest` and `ModernMarkSizesTest` unchanged.

- [ ] **Step 6: Commit** — `Chart defaults S6, fit a modern contour map to its points and let it zoom out`

---

### Task 5: Pad the Gantt past its last date (S6-9, D104)

**Files:**
- Modify: `graph/scale/TimeScale.java` (field near `fill:807`, accessors, `getMax:338`)
- Modify: `report/composition/graph/GanttGraphGenerator.java` (`fixGanttCoord:140-155`)
- Test: `core/src/test/java/inetsoft/graph/scale/TimeScaleTest.java`, `core/src/test/java/inetsoft/report/composition/graph/ModernGanttEndTest.java` (new, Spring)

**Interfaces:**
- Produces:
  - `TimeScale.setEndPadRatio(double)` / `double getEndPadRatio()`: default 0, `@hidden`. `getMax()` adds `(getMax0() − getMin0()) × endPadRatio` when `umax == null`. `getMin()` and the ticks are unchanged.
  - `static double GanttGraphGenerator.endPadRatio(double padPx, double plotPx)` (package-private): `padPx / (plotPx − padPx)` when `plotPx > 2 × padPx`, else 0.

- [ ] **Step 1: Write the failing tests**
  - `TimeScaleTest.anEndPadExtendsOnlyTheMax`: a `TimeScale("Date")` with `setFill(true)`, `init` over Jan 1 and Jan 31 rows, ratio 0.1 → `getMax()` is Jan 31 + 3 days, `getMin()` and `getTicks()` equal the unpadded scale's.
  - `TimeScaleTest.anAuthorMaxTurnsThePadOff`: `setMax(date)` → `getMax() == date`.
  - `TimeScaleTest.aZeroRangeOrNarrowPlotGetsNoPad`: rows of one date → `getMax()` is finite and equals the unpadded scale's; `GanttGraphGenerator.endPadRatio(12, 20) == 0`.
  - `ModernGanttEndTest.aMarkedGanttEndsAboutOnePaddingBeforeTheEdge`: an 8-task Gantt (copy `ModernAxisLabelRuleTest`'s private `ganttChart(int, boolean)` builder), marked, at 400×240, laid out → the last bar's right edge is between `p` and `1.5 × p` from the plot's right edge, where `p = VSDensityDefaults.chartPadding(VizContext.of(assemblyInfo)).right`.
  - `ModernGanttEndTest.classicAndAuthorMaxGanttsTouchTheEdge`: unmarked → `getEndPadRatio() == 0`; marked with an author maximum on the time axis's descriptor (the one `GraphGenerator:2784-2795` reads) → the scale's `getMax()` is that maximum.

- [ ] **Step 2: Run them and see them fail**

Run: `./mvnw -q test -pl core "-Dtest=TimeScaleTest,ModernGanttEndTest" -Dsurefire.failIfNoSpecifiedTests=false`

- [ ] **Step 3: Implement** the field and `getMax`; in `fixGanttCoord`, when `vizContext.modern` and the Y scale is a `TimeScale`: `p = VSDensityDefaults.chartPadding(vizContext).right`; `w = graphSize != null ? graphSize.width : 1000`; `plotPx = w − padding.left − padding.right − ModernLabelRule.columnLimitPx(<the X (task) scale's axis label font>, w)`; `setEndPadRatio(endPadRatio(p, plotPx))`.

- [ ] **Step 4: Run the tests and see them pass**, plus `ModernAxisLabelRuleTest` (its Gantt cases) and `ModernMarkRulesTest` unchanged.

- [ ] **Step 5: Commit** — `Chart defaults S6, keep the density padding past a modern Gantt's last date`

---

### Task 6: Whole-branch check and live-check handoff (all S6a, with S6-3)

**Files:** none in the product. Handoff: `E:/Temp/chart-defaults-s6a-checks-handoff.md`.

- [ ] **Step 1: Run the graph and generator packages**

Run: `./mvnw -q test -pl core "-Dtest=inetsoft.graph.**.*Test,inetsoft.report.composition.graph.**.*Test" -Dsurefire.failIfNoSpecifiedTests=false`
Expected: 0 failures. Report the counts from `core/target/surefire-reports`.

- [ ] **Step 2: Write the live-check handoff**, in the format of `E:/Temp/chart-defaults-s5b-checks-handoff.md` (base build `2bd6b1d681`, head build this branch, a Result column). Rows:
  - **S6-1:** a titled marked bar, line, pie and radar at dense, compact and comfortable: the gap from the lane bottom to the plot's top content is the top padding (8/12/16, ±2); a hidden title gives the base build's layout; an author padding of 20 gives 20; a scrolling bar's tiles line up; a contour map is unchanged; PDF and PNG exports show the gap.
  - **S6-2:** a marked pie and donut at 400×240 and at 200×400 are at most 80% of the shorter side; a pie with Text labels draws as the base build; an author ratio of 0.5 wins.
  - **S6-3:** a marked radar at the three densities keeps the padding from the title, the card edges and the legend (D101, nothing built).
  - **S6-8:** marked pie and donut slices start at 12 o'clock and run clockwise; labels, hover, tooltips and the brush outline sit on their slices; the inline-SVG sweep (`graph.svg.inline`) wipes clockwise; a 3D pie and a script `rotate(90)` pie keep the classic start; PDF and PNG agree.
  - **S6-7:** an untouched marked contour map frames its points; nav zoom-out reaches the world, the view stays after save and reopen, and clear returns to the fit; a filter refits an untouched map; Revert then shows the classic world (D105); a marked polygon map's data zoom reframes as on the base build.
  - **S6-9:** a marked Gantt's last bar ends about one padding from the plot edge; an author axis maximum removes the pad.
  - **Classic identity:** a classic dashboard with each type (tiles, Save As XML, PNG and PDF) is identical across the two builds.

- [ ] **Step 3: Report** the counts, the handoff path and anything left for the owner. No commit unless a test fix was needed (then `Chart defaults S6, <fix>`).
