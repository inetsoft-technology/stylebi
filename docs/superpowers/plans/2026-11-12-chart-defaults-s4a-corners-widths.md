# Chart Defaults S4a — Corners and Widths Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** On a modern chart, draw bars, waterfall steps, marimekko segments and box plot boxes as the bundle draws them:
- a 0.08 corner with a 2px floor
- bar widths of 50% of the slot, clamped to 3–32px, with clustered bars kept touching
- waterfall steps at least 2px tall
- thin marimekko segments kept visible
- box plot boxes 30% of the slot (6–28px), at least 2px tall, with the bar corner

Classic charts stay pixel-identical and keep their XML. An author's bar corner from a released build is protected.

**Architecture:**
- **One seed.** The bar corner is the only stored value in S4a: the modern seed changes from 0.3 to 0.08, and `userBarCornerRadius` is derived when a file loads.
- **Drawing rules (M1).** Everything else is a drawing rule in the graph package, read from flags that `GraphGenerator` sets on the element under `vizContext.modern`:
  - `IntervalElement`: corner floor, width clamp, minimum step height
  - `MekkoElement`: thin-segment line
  - `SchemaElement`: box corner and box width rule
- **One helper sets them all.** A single generator helper, `applyModernMarkRules`, runs once per element in `initElement`, after the size frame is final.
- **Sizes are drawing rules too (D44).** The Gantt bar size and the interval width are modern branches in `GraphGenerator.fixSizeFrameValues` and `fixSizeFrame`.

**Tech Stack:** Java 21, JUnit 5, Mockito, Spring test context (`@SreeHome`), Maven module `core`. Angular 21 / Vitest is not touched (correction C1).

**Spec:** `docs/superpowers/specs/lookfeel/2026-11-06-chart-defaults-design.md`, amended in `6b0318fb74`. The relevant parts are:
- §3 M1–M9: M2's sizes note, M4's corner flags, M9
- §4 R11
- §5 S4: the two shaping facts, the S4-1 to S4-6 rows, "S4-1 corner flag", "S4-2 corner rule", "S4-3 box plot", "S4-4 bar width"
- §10 D44–D62
- §11 Sizes and Corners

**Builds on:** slice 3, PR #8081 (`bug-80455-s3` at `958852a11a`). From earlier slices this plan uses:
- `ChartVSAssemblyInfo.seedChromeDefaults(VizContext)`, `isUserBarCornerRadius()` / `setUserBarCornerRadius(boolean)`
- `DerivedUserFlag.parse/write`
- `ChartChromeSlots`' existing `userBarCornerRadius` slot (`:222-233`)
- `GraphGenerator.vizContext`

**Verified against:** `bug-80455-s4a` at `6b0318fb74` (`958852a11a` plus the spec amendment). Paths are under `core/src/main/java/inetsoft/` unless they start with `core/` or `web/`. Research notes with every line read: `E:/Temp/chart-defaults-s4-research.md`.

## Corrections to the spec

| # | Spec | Code at `6b0318fb74` | This plan |
|---|---|---|---|
| C1 | D52 / S4-2: the floor reaches the browser "through a new `ChartRegion` field" | The browser draws the outline from the region's `cornerRadius` fraction and the bar's own bounds. It uses `min(f·w, w/2, h)` (`chart-tool.ts:1741-1743`) and the same rule on the full stacked bar (`:1000-1040`), which are the server's own caps. | `GraphBuilder` sends the **effective** fraction, the floored arc divided by the bar's thickness, so the browser draws the same corner with no client change (Task 2). |
| C2 | S4-2: `max(f × s, min(2px, s / 4))` with s the short side, then per-branch caps | The free-end branch multiplies by the thickness, and round-all by the short side (`BarVO:1712-1721`). | One thickness everywhere. The effective fraction is `max(f·t, min(2, t/4)) / t`, with t the bar's thickness in screen px. The existing caps then apply unchanged, so a bar shorter than it is thick gets a smaller corner still, as the bundle's edge note says. |
| C3 | S4-5: the interval width "comes from the S4-4 rule" | `BarVO.getBarSize` returns `maxbar / 2` when a frame's `largest == smallest` (`:1238-1240`). The interval gets `smallest = largest − 1` (`GraphGenerator.fixSizeFrame:5230-5233`). | Under the mark, set `smallest = largest`. The interval is then 50% of the slot, and the S4-4 clamp applies (Task 3). |
| C4 | D44: "in both `fixSizeFrameValues`" | `ChangeChartProcessor.fixSizeFrame(ChartInfo)` has no mark, and its five callers pass a bare `ChartInfo`. Every type or binding change it serves is followed by a composer draw, which rewrites the frame through `GraphGenerator` (`info == origInfo`, `:6715`). | `GraphGenerator` only. `ChangeChartProcessor`'s Gantt value (20) is rewritten by the next draw. |
| C5 | S4-3: "radius `f × s`" | `BoxPainter:174-182` passes s/3 as the `RoundRectangle2D` arc **width**, which is a diameter. | The modern box passes `2 × radius`. |

**Known limitations:**
- **Older builds** draw classic corners with no floor, unclamped widths, a Gantt bar of size 20, bare waterfall slivers and white-eaten mekko slivers (M9).
- **Funnel, 3D and polar bars** take none of the bar rules.
- **A box plot whose author changed its size** keeps today's width formula, and so does a bar chart with a Size binding. An interval keeps the clamp: there, Size is the bar's length.
- **Dodged box plot boxes** are scaled about their slot centre. At the 28px cap they stay side by side; at the 6px floor with many series they overlap, as today's three-series spill does (D57).

## Global Constraints

- **Worktree:** `$WT` is the executor's worktree on branch `bug-80455-s4a`, at or after `6b0318fb74`. Never work in `E:/StyleBI/stylebi-enterprise/community`.
- **Git:**
  - Each git command is its own call, `git -C "$WT" ...`, with no `&&` anywhere in the command.
  - One commit per task, subject `Chart defaults S4, <what changed>`, last line `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
  - Subagents never run `git add`, `git commit` or `git stash` (a hook blocks them); the controller stages and commits after review. Never push.
- **Scope is the mark (M1).** Every new graph-package property defaults to off, and off draws today's shapes. A classic chart is pixel-identical, and its XML is byte-identical on load and save while no author edits it.
- **Values:**
  - **Bar corner seed:** `VSChartChromeDefaults.BAR_CORNER_RADIUS = 0.08` (modern). The legacy value stays 0.
  - **Corner floor:** `f_eff = max(f·t, min(2, t/4)) / t` when `f > 0` and `t > 0`; otherwise `f`.
  - **Bar width clamp:** the thickness is clamped to `[3, 32]` screen px. Each bar is scaled about its cluster's centre (the slot centre before dodge), so a cluster stays touching and centred.
  - **Waterfall step:** at least 2px along the value axis, grown from its start edge in the direction of its change. A step of exactly 0 draws nothing.
  - **Marimekko:** a segment under 2px along the value axis draws as a 1px line in its own colour, over the white separators.
  - **Box plot:**
    - width `0.30 × slot`, clamped to `[6, 28]` px
    - height at least 2px
    - corner radius `f_eff × shortSide`, where f is the chart's bar corner; an author's 0 gives square boxes
  - **Gantt bar size:** 15 under the mark (20 classic).
- **Comments:** a short clause each, with no ticket, PR, slice or design-doc names. No `.html` comments.
- **New files** start with the AGPL header of `SeedChromeDefaultsTest.java`, `Copyright (C) 2026  InetSoft Technology`.
- **Tests that reach Spring beans** use:
  - `@ExtendWith(SpringExtension.class)`
  - `@ContextConfiguration(classes = BaseTestConfiguration.class, initializers = ConfigurationContextInitializer.class)`
  - `@SreeHome`, `@Tag("core")`
  - In `@AfterEach`, clear `viewsheet.modernVisualization`, `viewsheet.darkMode` and `viewsheet.density`.

## Review Focus

1. **A classic chart is untouched.** Its bar corners, widths, waterfall steps, mekko seams and box plots draw as before, and its XML writes what it always wrote. Pinned by the `classic…Unchanged` cases in Tasks 2 to 6 and by `aClassicChartWritesWhatItAlwaysWrote` (Task 1).
2. **Hover and selection outlines match the drawn corner** on a modern bar, a stacked bar and a waterfall step. Pinned by `theRegionFractionDrawsTheSameCorner` (Task 2).
3. **A clustered bar chart on a wide card** keeps its bars touching and centred on the category label after the 32px cap. Pinned by `aClusterStaysTouchingAndCentred` (Task 3).
4. **A v1.1 author's bar radius** survives "Keep my formatting" and then Revert. Pinned by `keepAndRevertKeepAReleasedAuthorsRadius` (Task 1).
5. **A faceted modern bar chart** keeps every bar within 3–32px after the facet rescale (`VGraph:938-940`). Pinned by `aFacetedChartStaysClamped` (Task 3).

## File Structure

- **Task 1:** `uql/viewsheet/internal/VSChartChromeDefaults.java` (constant), `uql/viewsheet/internal/ChartVSAssemblyInfo.java` (seed, flag derivation).
- **Task 2:** `graph/element/IntervalElement.java` (corner floor), `graph/visual/BarVO.java` (effective fraction), `web/graph/GraphBuilder.java` (region fraction), `report/composition/graph/GraphGenerator.java` (`applyModernMarkRules`).
- **Task 3:** `IntervalElement.java` (width clamp), `BarVO.java` (cluster centre, clamp, facet rescale), `GraphGenerator.java` (clamp predicate, Gantt 15, interval smallest).
- **Task 4:** `IntervalElement.java` (minimum step, bridge form), `graph/guide/form/BridgeLineForm.java` (new), `graph/visual/BridgeLineFormVO.java` (new), `BarVO.java` (step floor), `GraphGenerator.java`.
- **Task 5:** `graph/element/MekkoElement.java`, `graph/visual/MekkoVO.java`, `GraphGenerator.java`.
- **Task 6:** `graph/element/SchemaElement.java`, `graph/schema/SchemaPainter.java`, `graph/schema/BoxPainter.java`, `graph/visual/SchemaVO.java`, `GraphGenerator.java`, `web/graph/model/dialog/ChartPlotOptionsPaneModel.java`.
- **Task 7:** none (whole-branch check and live-check handoff).

**Test harnesses:**
- **Seed and descriptor tests** copy the helpers of `ChartColorTypeSeedTest` (`newChart()`, `seed`, `modernize`, `revert`, `writeXml`) and of `SeedChromeDefaultsTest` (`parseInfo`).
- **Graph-package render tests** (no Spring) copy `AxisLabelGapShrinkTest.legendChart` (`:400-413`):
  - build an `EGraph`, then call `Plotter.getPlotter(graph).plotAndLayout(new DefaultDataSet(rows), 0, 0, 400, 240)`
  - collect the `BarVO`s with `vgraph.getVisualCount()` / `getVisual(i)`, and read `getPath()` and `getPreRoundingBounds()`
  - for a facet, build the `FacetCoord` as `RelationCoordMinSizeTest` does
- **Generator tests** copy `AxisLabelGapShrinkTest.modernBarChartMeasureLabelsStayInsideNarrowedAxis` (`:84-118`): build a `ChartVSAssemblyInfo` with `setVizMark(VizMark.MODERN_LIGHT)` (or none, for classic), then call `GraphGenerator.getGenerator(...).createEGraph()` and inspect `graph.getElement(i)`.

## How to run tests

From `$WT`:

```bash
./mvnw -q test -pl core "-Dtest=ClassA,ClassB" -Dsurefire.failIfNoSpecifiedTests=false
```

- Read `core/target/surefire-reports/*.txt` for counts.
- Run every new or changed class alone once as well (CLAUDE.md).
- If compilation fails with "Attempt to recreate … HeartBeatServiceProxy", delete the untracked `core/target/generated-test-sources`.

---

### Task 1: Bar corner seed 0.08 and the released-file flag

S4-1, D47, M4, M8.

**Files:**
- Modify: `uql/viewsheet/internal/VSChartChromeDefaults.java`: add the constant.
- Modify: `uql/viewsheet/internal/ChartVSAssemblyInfo.java`:
  - the seed at `:162-167`
  - the parse of `userBarCornerRadius`: remove it at `:1791-1795`, and add it after `userSmoothLines` at `:2078-2080`, where the plot has been parsed
  - keep the unconditional write at `:1739`
- Modify, for the 0.3 → 0.08 change:
  - `core/src/test/java/inetsoft/uql/viewsheet/internal/ChartVSAssemblyInfoBarRoundingTest.java` (`:74`, `:91`)
  - `SeedChromeDefaultsTest.java` (`:1019`, `:1805`)
  - `WizardSyncReseedTest.java` (`:90`)
  - `core/src/test/java/inetsoft/uql/viewsheet/BookmarkChromeResolutionTest.java` (`:87`)
  - Grep the test tree for any other `0.3` asserted against `getBarCornerRadius()`.
- Test: `core/src/test/java/inetsoft/uql/viewsheet/internal/BarCornerFlagTest.java` (new, Spring).

**Interfaces:**
- **Produces:**
  - `public static final double BAR_CORNER_RADIUS = 0.08` on `VSChartChromeDefaults`.
  - `private boolean derivedUserBarCornerRadius()` on `ChartVSAssemblyInfo`: true when the plot exists and `getBarCornerRadius() != 0`.

- [ ] **Step 1: Write the failing tests in `BarCornerFlagTest`.**
  - `aReleasedFilesRadiusIsTheAuthors`: parse a chart whose XML has no `userBarCornerRadius` attribute and whose plot has `barCornerRadius="0.25"`. Expect `isUserBarCornerRadius()` true.
  - `aReleasedFilesSquareBarsAreNoOpinion`: the same with `barCornerRadius="0.0"`. Expect false.
  - `aStoredFlagIsReadAsIs`: `userBarCornerRadius="false"` with `barCornerRadius="0.08"`. Expect false.
  - `keepAndRevertKeepAReleasedAuthorsRadius`: parse the 0.25 file, `modernize`, assert 0.25; `revert`, assert 0.25.
  - `aNewModernChartSeedsTheModernCorner`: a marked `newChart()` gives `BAR_CORNER_RADIUS`; `revert` gives 0.
  - `aClassicChartWritesWhatItAlwaysWrote`: an unmarked `newChart()` written, parsed and written again gives identical XML, and it still contains `userBarCornerRadius="false"`.
- [ ] **Step 2: Run them.** Expect the first and fourth to FAIL (the flag reads false), and the fifth to FAIL on 0.3.
- [ ] **Step 3: Implement.**
  - Change the seed to `VSChartChromeDefaults.BAR_CORNER_RADIUS`.
  - Move the parse to `userBarCornerRadius = DerivedUserFlag.parse(Tool.getAttribute(elem, "userBarCornerRadius"), derivedUserBarCornerRadius())`, next to `userSmoothLines`.
  - Replace the old comment with one clause: a missing attribute means a released build saved the chart, where any radius is the author's.
- [ ] **Step 4: Update the 0.3 assertions** in the test files listed above to `VSChartChromeDefaults.BAR_CORNER_RADIUS`.
- [ ] **Step 5: Run** `BarCornerFlagTest,ChartVSAssemblyInfoBarRoundingTest,SeedChromeDefaultsTest,WizardSyncReseedTest,BookmarkChromeResolutionTest,ChartChromeSlotsTest,VizModernizeUtilTest,PerTypeSeedSharedDescriptorTest,ChartAdvancedPaneModelDcShareTest`. Expect PASS.
- [ ] **Step 6: Commit** `Chart defaults S4, seed the 0.08 bar corner and protect a released author's radius`.

### Task 2: Corner floor

S4-2, D52, C1, C2.

**Files:**
- Modify: `graph/element/IntervalElement.java`: accessors after `setRoundAllCorners` (`:745`), the field, and `equalsContent` (`:786-791`).
- Modify: `graph/visual/BarVO.java`: `getPath0` (`:221-238`) and a new static helper next to `buildRoundedBarShape` (`:1705`).
- Modify: `web/graph/GraphBuilder.java:1364-1419`: both `cornerRadius = r` assignments, and the `r` passed to `computeArcZones`.
- Modify: `report/composition/graph/GraphGenerator.java`: add `applyModernMarkRules(GraphElement elem, int chartType)` and call it from `initElement` right after the size-frame block (after `elem.setSizeFrame(sizes)`, before the interval outline block at `:4892`).
- Test: `core/src/test/java/inetsoft/graph/visual/BarVOCornerFloorTest.java` (new, no Spring), `core/src/test/java/inetsoft/report/composition/graph/ModernMarkRulesTest.java` (new, Spring).

**Interfaces:**
- **Produces:**
  - `IntervalElement`: `boolean isCornerFloor()` and `void setCornerFloor(boolean)`, default false, in `equalsContent`.
  - `BarVO`: `static double flooredCornerFraction(double fraction, double thickness)`. It returns `max(f·t, min(2, t/4)) / t` when `f > 0 && t > 0`, and `f` otherwise.
  - `BarVO`: `public double getEffectiveCornerRadius()`. It returns the element's `getCornerRadius()` when `!isCornerFloor()`. Otherwise it returns `flooredCornerFraction(r, t)`, with t the thickness of `getPreRoundingBounds()`: width when `GTool.isHorizontal(getScreenTransform())`, else height.
  - `GraphGenerator`: `private void applyModernMarkRules(GraphElement elem, int chartType)`. Under `vizContext.modern` it sets `setCornerFloor(true)` on every `IntervalElement`. Tasks 3–6 add their flags here.

- [ ] **Step 1: Write the failing tests.**
  - **`BarVOCornerFloorTest`:**
    - `flooredFraction`: with f = 0.08 and t = 21, 32, 6, 3, `fraction × t` is 2.0, 2.56, 1.5 and 0.75 (±1e-9). f = 0.3 at t = 21 gives 6.3, so the author's larger corner wins. f = 0 gives 0.
    - `aModernBarDrawsTheFlooredCorner`: build a render graph with one bar per category at 400×240, `setCornerRadius(0.08)` and `setCornerFloor(true)`. For each `BarVO`, `new Area(getPath())` equals `new Area(BarVO.buildRoundedBarShape(getPreRoundingBounds(), getEffectiveCornerRadius(), openDir, false))`.
    - `aStackedBarUsesTheSameFraction`: a stack (`STACK_SYMMETRIC`) under the flag. The outer segment's path equals what `applyStackRounding` gives with `getEffectiveCornerRadius()`. Test through `getPath()` against an `Area` built from `computeFullBarBounds` + `buildRoundedBarShape` with that fraction.
    - `theRegionFractionDrawsTheSameCorner`: for a modern bar, `getEffectiveCornerRadius() × thickness` equals the floored arc, `min(max(0.08·t, min(2, t/4)), t/2)`. This is what `GraphBuilder` sends.
    - `classicCornersUnchanged`: the same graph with the flag off. `getEffectiveCornerRadius() == 0.08`, and the path equals `buildRoundedBarShape(bounds, 0.08, dir, false)`.
  - **`ModernMarkRulesTest`:**
    - `aMarkedBarChartFloorsItsCorners`: a marked `CHART_BAR` gives `IntervalElement.isCornerFloor()` true.
    - `aClassicBarChartDoesNot`: unmarked gives false.
    - `everyBarFamilyTypeIsFloored`: `CHART_PARETO`, `CHART_WATERFALL` (both elements), `CHART_INTERVAL` and `CHART_GANTT`, marked, give true on each `IntervalElement`.
- [ ] **Step 2: Run them.** Expect FAIL (missing methods).
- [ ] **Step 3: Implement.**
  - Add the flag and `flooredCornerFraction`.
  - In `getPath0`, replace `double r = ielem0.getCornerRadius()` with the effective fraction for this bar. Compute it from `path0`'s screen bounds before rounding, as `getEffectiveCornerRadius` does, without going through the cache.
  - In `GraphBuilder`, set both `cornerRadius` values and the `r` passed to `computeArcZones` from `barVO.getEffectiveCornerRadius()`.
  - Add `applyModernMarkRules` with its call.
- [ ] **Step 4: Run** `BarVOCornerFloorTest,ModernMarkRulesTest,BarVORoundedShapeTest,BarVOStackRoundingTest,BarVOMultiRowStackRoundingTest,BarVOAnchorFractionTest,IntervalElementStackOutermostTest`. Expect PASS.
- [ ] **Step 5: Run the portal specs that pin the outline:** from `$WT/web`, run `npx ng test portal --include="**/chart-tool-rounded-bar.spec.ts"`, then the same with `--include="**/chart-tool-stack-rounding.spec.ts"`. Expect PASS, unchanged (C1).
- [ ] **Step 6: Commit** `Chart defaults S4, floor the modern bar corner at 2px`.

### Task 3: Bar width clamp, Gantt bar and interval width

S4-4, S4-5, D44, D45, C3, C4.

**Files:**
- Modify: `graph/element/IntervalElement.java`: the width-clamp flag (accessors, field, `equalsContent`).
- Modify: `graph/visual/BarVO.java`:
  - `dodge` (`:780-892`): record the cluster centre
  - `getPath0`: the clamp, after `transformShape(path0, getScreenTransform())` and before `preRounding` is captured
  - `scaleToSizeFactor` (`:1189-1222`): clamp again
- Modify: `report/composition/graph/GraphGenerator.java`:
  - `applyModernMarkRules`: the clamp predicate
  - `fixSizeFrameValues`: the Gantt case (`:5376-5377`)
  - `fixSizeFrame`: the interval branch (`:5230-5233`)
- Test: `core/src/test/java/inetsoft/graph/visual/BarVOWidthClampTest.java` (new, no Spring), `ModernMarkRulesTest` (extend).

**Interfaces:**
- **Consumes:** Task 2's `applyModernMarkRules`.
- **Produces:**
  - `IntervalElement`: `boolean isWidthClamp()` and `void setWidthClamp(boolean)`, default false.
  - `BarVO`: `static final double MIN_BAR_PX = 3`, `MAX_BAR_PX = 32`.
  - `BarVO`: a `private Point2D clusterCenter`, in the shape's pre-screen coordinates. `dodge` sets it to `(cx, rect.getCenterY())` on every bar it moves; a bar never dodged uses its own bounds' centre.
  - The clamp applies when `isWidthClamp()`, the bar is not a `Bar3DVO`, not a funnel (`MOVE_MIDDLE`) and not polar (`getOuterArc(path0) == null`).
    - It scales the screen path along the thickness axis (x when `GTool.isHorizontal(getScreenTransform())`, else y), about the screen position of `clusterCenter`, by `clamp(t, 3, 32) / t`.
  - **Clamp predicate** in `applyModernMarkRules`, on an `IntervalElement` when `vizContext.modern`, all of:
    - not a funnel and not 3D
    - not `info.isSizeChanged(elem.getVars())`
    - `GraphTypes.isInterval(chartType)`, or the element's size frame is null or has no field
  - `fixSizeFrame`: under `vizContext.modern`, the interval branch sets `smallest = largest`. Classic keeps `largest − 1`.
  - `fixSizeFrameValues`: under `vizContext.modern`, the Gantt `IntervalElement` gets 15. Classic keeps 20, and the milestone keeps 9 (S4b).

- [ ] **Step 1: Write the failing tests.**
  - **`BarVOWidthClampTest`** (render graphs at 400×240 with `setWidthClamp(true)`):
    - `aWideSlotCapsAt32`: 3 categories. Each bar's thickness is 32 (±0.5), centred on its slot (±0.5).
    - `aNarrowSlotFloorsAt3`: 150 categories. Each thickness is ≥ 3 (−0.01).
    - `aClusterStaysTouchingAndCentred`: 3 categories, 2 colour series, `MOVE_DODGE | MOVE_CENTER`.
      - Each bar is 32 (±0.5).
      - The two bars of a category are adjacent: the gap is ≤ 1.5px, which allows for the 1pt paint reserve.
      - The pair's centre is the slot centre (±0.5).
    - `aHorizontalBarClampsItsHeight`: the same as `aWideSlotCapsAt32` on `coord.transpose()`, measured along y.
    - `aStackClampsEverySegmentAlike`: a 3-segment stack. Every segment has the same x and thickness (32).
    - `aFacetedChartStaysClamped`: a `FacetCoord` with 2 outer and 3 inner categories. Every bar thickness is in [3, 32].
    - `classicWidthsUnchanged`: the flag off at 3 categories. The thickness equals `getSizeFactor() × (1 + 29·15/30) / 30` (±0.5), today's 51.7%.
  - **`ModernMarkRulesTest`** (extend):
    - `theClampIsOnForAMarkedBarChart`.
    - `theClampIsOffWithASizeBinding`: a measure on Size.
    - `theClampIsOffWhenTheAuthorChangedTheSize`: `cinfo.getSizeFrameWrapper().setChanged(true)`, which `AbstractChartInfo.isSizeChanged` (`:2208-2224`) reads.
    - `anIntervalWithSizeBoundIsClamped`.
    - `aModernGanttBarIsSize15`: after `createEGraph()`, the stored Gantt bar's `StaticSizeFrame.getSize(null)` is 15. Unmarked gives 20.
    - `anAuthorsGanttSizeIsKept`: with `getSizeFrameWrapper().setChanged(true)` and a USER size of 25, a marked draw leaves 25.
    - `aModernIntervalIsHalfItsSlot`: the interval's size frame has `getSmallest() == getLargest()`. Unmarked gives `largest − 1`.
- [ ] **Step 2: Run them.** Expect FAIL.
- [ ] **Step 3: Implement** per the Interfaces block.
  - The clamp runs before Task 2's effective fraction is computed, so the corner follows the clamped thickness.
  - In `scaleToSizeFactor`, after scaling the cached path, apply the same clamp about the same centre when `isWidthClamp()`.
- [ ] **Step 4: Run** `BarVOWidthClampTest,ModernMarkRulesTest,BarVOCornerFloorTest,BarVOStackRoundingTest,BarVOMultiRowStackRoundingTest,BarVOAnchorFractionTest`. Expect PASS.
- [ ] **Step 5: Commit** `Chart defaults S4, clamp modern bar widths to 3-32px`.

### Task 4: Waterfall bridges and the 2px step

S4-4 (bridges), S4-6 (waterfall), D53, D59.

**Files:**
- Modify: `graph/element/IntervalElement.java`: the minimum-step field, and `addBridgeForm` (`:854-882`).
- Create: `graph/guide/form/BridgeLineForm.java`, `graph/visual/BridgeLineFormVO.java`.
- Modify: `graph/visual/BarVO.java`: `getPath0`, after rounding.
- Modify: `report/composition/graph/GraphGenerator.java`: `applyModernMarkRules`.
- Test: `core/src/test/java/inetsoft/graph/visual/WaterfallModernTest.java` (new, no Spring), `ModernMarkRulesTest` (extend).

**Interfaces:**
- **Consumes:** Task 3's `isWidthClamp()` and the clamp bounds.
- **Produces:**
  - `IntervalElement`: `double getMinStepPx()` and `void setMinStepPx(double)`, default 0 (off), in `equalsContent`. The generator sets 2 on both waterfall elements under the mark.
  - **`BridgeLineForm extends LineForm`.** Its constructor is `(double[] from, double[] to, double fromCenterX, double toCenterX, double fromHalf, double toHalf)`: the two endpoints as today, plus both bars' centres and slot half-widths. Its `createVisual(Point2D[])` returns a `BridgeLineFormVO`.
  - **`BridgeLineFormVO extends LineFormVO`.** After the screen transform, it moves each endpoint toward its bar by the pixels that bar's clamp removed from its half-width: `(2·half·slotPx − clamp(2·half·slotPx, 3, 32)) / 2`. slotPx is the screen distance of one x unit.
  - `addBridgeForm` builds a `BridgeLineForm` when `isWidthClamp()`, and a `LineForm` as today otherwise.
  - **The step floor in `getPath0`,** after rounding, when `getMinStepPx() > 0`, `geom.getInterval() != 0`, and the path's extent along the value axis is under the floor:
    - Rebuild the bar as a rectangle `getMinStepPx()` long.
    - It starts at the base edge (the end opposite `openDir`) and runs toward `openDir`.
    - Round it with the same effective fraction (`roundAll` true).
    - Store it as both the path and the pre-rounding bounds, so regions match.

- [ ] **Step 1: Write the failing tests in `WaterfallModernTest`.**
  - Build a waterfall the way `GraphGenerator` does: `bar` and `sumBar` with `setWaterfall(true)`, `STACK_SYMMETRIC`, a bridge line set. Use the steps `{+100, −0.01, 0, +50}` at 400×240, with the clamp and `setMinStepPx(2)`.
  - `aTinyStepIsTwoPixelsFromItsStart`: the −0.01 step is 2px (±0.01) along y. Its top edge is at its start value's screen y, and it grows downward.
  - `aZeroStepDrawsNoBar`: the 0 step's path is empty, or has 0 extent along y, as today.
  - `bridgesKeepTheirGapFromClampedBars`: for each bridge, the distance from its left end to the previous bar's right edge equals the unclamped gap, `(bridgeInset(half) − half) × slotPx` (±0.5).
  - `classicWaterfallUnchanged`: flags off. The steps and bridges equal today's: the −0.01 step is under 1px, and the bridges are `LineForm`s at today's x.
  - In `ModernMarkRulesTest`, add `aMarkedWaterfallSetsTheStepFloor`: both elements give `getMinStepPx() == 2`, and unmarked gives 0.
- [ ] **Step 2: Run them.** Expect FAIL.
- [ ] **Step 3: Implement** per the Interfaces block.
- [ ] **Step 4: Run** `WaterfallModernTest,ModernMarkRulesTest,BarVOWidthClampTest,BarVOStackRoundingTest`, plus every existing test that names `bridge` (grep the test tree for `Bridge`). Expect PASS.
- [ ] **Step 5: Commit** `Chart defaults S4, keep modern waterfall steps and bridges visible`.

### Task 5: Marimekko thin segments

S4-6 (marimekko), D50.

**Files:**
- Modify: `graph/element/MekkoElement.java`: the flag, and `equalsContent` if the class has one, otherwise its `clone`.
- Modify: `graph/visual/MekkoVO.java`: `paint` (`:80-140`).
- Modify: `report/composition/graph/GraphGenerator.java`: `applyModernMarkRules`.
- Test: `core/src/test/java/inetsoft/graph/visual/MekkoThinSegmentTest.java` (new, no Spring).

**Interfaces:**
- **Produces:**
  - `MekkoElement`: `boolean isThinSegmentLine()` and `void setThinSegmentLine(boolean)`, default false. The generator sets it under the mark.
  - **`MekkoVO.paint`:** when the flag is on and the segment's screen extent along the value axis is under 2px and above 0:
    - skip the separator stroke for this segment
    - draw a 1px line in the segment's colour (after `applyAlpha`) along its centre, across its width
    - raise its z-index by 1 so neighbours' separators do not paint over it

- [ ] **Step 1: Write the failing tests in `MekkoThinSegmentTest`.**
  - Render a 2-column mekko with one segment of 0.2% of its column to a 400×240 `BufferedImage`, through `Plotter.plotAndLayout`, then `vgraph.paint(g)`.
  - `aThinSegmentShowsItsColour`: with the flag on, the pixel at the segment's centre has the segment's colour (±8 per channel).
  - `classicThinSegmentUnchanged`: with the flag off, the same pixel is white, today's separator.
  - In `ModernMarkRulesTest`, add `aMarkedMekkoSetsTheThinLine`.
- [ ] **Step 2: Run them.** Expect FAIL.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run** `MekkoThinSegmentTest,ModernMarkRulesTest`. Expect PASS.
- [ ] **Step 5: Commit** `Chart defaults S4, keep thin modern marimekko segments visible`.

### Task 6: Box plot corner, width and height

S4-3, D46, D57, C5.

**Files:**
- Modify: `graph/element/SchemaElement.java`: two fields, accessors, and `equalsContent` (`:221-228`).
- Modify: `graph/schema/SchemaPainter.java`: a no-op `fitScreen` hook.
- Modify: `graph/schema/BoxPainter.java`: `init` (`:44-77`), a `fitScreen` override, and the box branch of `paint` (`:170-190`).
- Modify: `graph/visual/SchemaVO.java`: replace the three `clone()` + `transformScreen(getScreenTransform())` pairs (`:99-100`, `:221-223`, `:293-294`) with one `screenPainter()`.
- Modify: `report/composition/graph/GraphGenerator.java`: `applyModernMarkRules`.
- Modify: `web/graph/model/dialog/ChartPlotOptionsPaneModel.java:126-130`.
- Test:
  - `core/src/test/java/inetsoft/graph/schema/BoxPainterModernTest.java` (new, no Spring)
  - `ModernMarkRulesTest` (extend)
  - `core/src/test/java/inetsoft/web/graph/model/dialog/ChartPlotOptionsPaneModelTest.java` (extend)

**Interfaces:**
- **Consumes:** Task 2's `BarVO.flooredCornerFraction`.
- **Produces:**
  - **`SchemaElement`:**
    - `double getBoxCornerRadius()` and `void setBoxCornerRadius(double)`, default `-1`, meaning the painter's classic s/3 arc.
    - `boolean isBoxWidthRule()` and `void setBoxWidthRule(boolean)`, default false.
  - **`SchemaPainter`:** `public void fitScreen(AffineTransform screen)`, a no-op by default.
  - **`BoxPainter`:**
    - `static final double BOX_WIDTH_RATIO = 0.3`, `MIN_BOX_PX = 6`, `MAX_BOX_PX = 28`, `MIN_BOX_HEIGHT_PX = 2`.
    - Under `isBoxWidthRule()`, `init` uses `width = BOX_WIDTH_RATIO × coord.getMaxWidth()`. It keeps that slot width and the slot centre (`high.getX()`, before any dodge) in two fields that `clone` copies.
    - `fitScreen(screen)`, under the rule:
      - Scales every shape along the box's width axis, about the screen slot centre, by `clamp(boxPx, 6, 28) / boxPx`.
      - Then grows shape 3 (the box) about its centre to at least 2px along the value axis.
    - `static double boxArcDiameter(double radiusFraction, double shortSide)`: `2 × min(flooredCornerFraction(f, s) × s, s/2)` when `f > 0`, and 0 when `f == 0`.
    - `paint`: when `getBoxCornerRadius() >= 0`, use `boxArcDiameter(getBoxCornerRadius(), shortDim)` in place of `shortDim / 3.0`.
  - **`SchemaVO`:** `private SchemaPainter screenPainter()`: `clone`, then `transformScreen(getScreenTransform())`, then `fitScreen(getScreenTransform())`.
  - **`applyModernMarkRules`,** on a `SchemaElement` whose painter is a `BoxPainter`, under the mark:
    - `setBoxCornerRadius(desc.getPlotDescriptor().getBarCornerRadius())`
    - `setBoxWidthRule(!info.isSizeChanged(elem.getVars()))`
  - **`ChartPlotOptionsPaneModel`:** `barCornerRadiusVisible` is also true when `ctx.modern && GraphTypes.isBoxplot(ctype)`.

- [ ] **Step 1: Write the failing tests.**
  - **`BoxPainterModernTest`** (a `BoxPainter` with shapes set through `setShape`, and a `SchemaGeometry` mock whose element is a real `SchemaElement`):
    - `boxArcDiameter`: f = 0.08 at s = 20 gives 4.0 (radius 2). At s = 28 it gives 4.48. f = 0 gives 0.
    - `aWideSlotCapsTheBoxAt28`: with a 200px slot, the box after `fitScreen` is 28 (±0.5) wide, centred on the slot.
    - `aNarrowSlotFloorsTheBoxAt6`: with a 10px slot, the box is 6 wide.
    - `dodgedBoxesStaySideBySide`: two painters dodged as `SchemaVO.dodge` does, then `fitScreen`. Each box is 28 wide, with a gap ≤ 1.5px, and the pair is centred on the slot.
    - `aFlatBoxIsTwoPixelsTall`: identical quartiles give a box height of 2.
    - `classicBoxUnchanged`: the rule off. `fitScreen` changes nothing, and the arc is `shortDim / 3`.
  - **`ModernMarkRulesTest`:**
    - `aMarkedBoxPlotTakesTheBarCorner`: with the plot's bar corner set to 0.08, `getBoxCornerRadius() == 0.08` and `isBoxWidthRule()`.
    - `aClassicBoxPlotDoesNot`: −1 and false.
  - **`ChartPlotOptionsPaneModelTest`:**
    - `theCornerFieldShowsOnAMarkedBoxPlot`.
    - `andNotOnAClassicOne`.
- [ ] **Step 2: Run them.** Expect FAIL.
- [ ] **Step 3: Implement** per the Interfaces block.
- [ ] **Step 4: Run** `BoxPainterModernTest,ModernMarkRulesTest,ChartPlotOptionsPaneModelTest,ChartAdvancedPaneModelDcShareTest`. Expect PASS.
- [ ] **Step 5: Commit** `Chart defaults S4, draw modern box plots with the bar corner and a clamped width`.

### Task 7: Whole-branch check and live-check handoff

- [ ] **Step 1: Run every class named in Tasks 1–6, plus the neighbourhood:** `ChartChromeRenderTest,ChromeSeedClassificationTest,ModernVisualizationDefaultTest,VizModernizeResetTest,VizModernizeResidueTest,AxisLabelGapShrinkTest,PlotDescriptorXmlTest`. Expect 0 failures.
- [ ] **Step 2: Run the full core suite once:** `./mvnw -q test -pl core`. Record the totals. Any failure that also fails on `958852a11a` is pre-existing; note it and do not fix it here.
- [ ] **Step 3: Write `E:/Temp/chart-defaults-s4a-checks-handoff.md`** for a separate live-check session. For each check, give the fixture to build, the expected value and a result column:
  - **L1, corners:** marked bar, horizontal bar, pareto, waterfall, interval and Gantt. Measure the corner radius at 5, 8, 21 and 32px thickness (expect 1.25, 2, 2, 2.56). Check the hover and selection outline in the composer.
  - **L2, widths:**
    - bars with 3 and 30 categories, and 2-series clustered, on a 400×240 and a 900×500 card
    - a horizontal bar
    - a Gantt with 3 and 25 tasks: the bar is 51.7% of its row and at most 32px
    - an interval with Size bound: 50% of its slot
  - **L3, waterfall:** a step under 1px draws 2px from its start, a zero step draws nothing, and bridges keep their gap.
  - **L4, marimekko:** a sliver segment shows as a 1px coloured line.
  - **L5, box plot:**
    - 3, 5 and 20 regions: 28px, then 30% of the slot, then the 6px floor
    - 2 series side by side
    - a flat region at 2px
    - the corner field in Chart Properties > Advanced on a marked box plot only
  - **L6, round trip:**
    - Modernize then Revert of a classic bar chart and a box plot: pixel-identical
    - a v1.1-saved bar chart with radius 0.25: Keep, then Revert, keeps 0.25
  - **L7, exports:** the PDF and PNG of L1, L2 and L5 match the canvas.
- [ ] **Step 4:** No commit (nothing in the repo changed). Report the totals and the handoff path to the controller.
