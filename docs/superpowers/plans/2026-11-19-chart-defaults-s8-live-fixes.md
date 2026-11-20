# Chart defaults S8 live-check fixes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix the three S8 defects the live checks found. First, a fitted Gantt's time axis is widened to a calendar boundary. Second, marked radar rings sit at the wrong radius. Third, marked radar spokes and the outer ring draw over the fill.

**Architecture:**
- **Gantt (Task 1).** The fix stays inside `TimeScale.getTicks`: fitted ticks before the data min are dropped against the data min, not against the truncated first tick.
- **Radar (Tasks 2 and 3).** Both radar fixes sit behind a new hidden, modern-only `PolarCoord.modernWeb` flag, set only by `RadarGraphGenerator` under the mark, so classic and scripted `setWebGrid` radars keep today's code paths.
  - Task 2 gives the rings the same logic-to-screen mapping as the data.
  - Task 3 hides the spoke and outer-ring axis LINES (ticks and labels stay) and draws them as `GridLine`s at the grid z.

**Tech Stack:** Java 21, JUnit 5, the StyleBI graph engine (`inetsoft.graph`).

**Spec:** `docs/superpowers/specs/lookfeel/2026-11-06-chart-defaults-design.md`:
- S8-2 (Layer bullet, D130, D135, D145)
- S8-7 (D143)
- M1
- M8 (the RT-4 line)

**Root causes and evidence:** `E:/Temp/chart-defaults-s8-checks-controller.md`, section "Fix wave". The live results are in `E:/StyleBI/stylebi-enterprise/community/.superpowers/baselines/chart-defaults-s8/R/results.md`, `G/results.md` and `CI/results.md`.

## Global Constraints

- **Worktree:** `$WT` = `C:/Users/Franky/AppData/Local/Temp/claude/714db04ea36030c4/0b09fe39-182e-406d-b60a-0de2e8f038af/scratchpad/s8-src`, branch `bug-80455-s8`, at or after this plan's commit. Never work in `E:/StyleBI/stylebi-enterprise/community` or any other worktree.
- **Shared machine:**
  - Never run `mvnw install`, `mvnw package -pl server` or `spring-boot:run`, or anything else that writes outside `$WT`.
  - Never start a server, or bind or call ports 8080, 8081 or 5005.
  - Run only the test classes a step names. Task 4 names packages and the full core suite.
- **Git:**
  - Each git command is its own call, `git -C "$WT" ...`, with no `&&`.
  - One commit per task, with the subject `Chart defaults S8, <what changed>` and the last line `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
  - Subagents never run `git add`, `git commit` or `git stash`; the controller commits. Never push.
- **Scope is the mark (M1).** A classic chart is pixel-identical, and its XML is unchanged. That includes a classic radar with a script `setWebGrid(true)`, and classic or author-increment Gantts.
- **Gantt ruling** (S8a ledger, `E:/Temp/chart-defaults-s8a-sdd-ledger.md:99`): calendar alignment must not change the axis range. Ticks before the axis start are dropped, as the first classic tick is today.
- **Radar (D130, D135, D145):**
  - Rings sit at their fraction of the outer ring's radius after layout: four even steps when the scales differ, or at the shared ticks when they don't.
  - Rings, spokes and the outer ring draw at `GDefaults.GRIDLINE_Z_INDEX` (30), under the series (`VO_Z_INDEX` 60). Axis ticks and labels stay on their axes, on top.
  - The spokes take the measure axes' line colour and `lineVisible`. The outer ring takes the label axis's line colour and `lineVisible`.
  - The Y grid row, the script and `format.css` `line_y: NONE` turn off only the inner rings.
- **Comments:** a short clause each, with no ticket, PR, slice, live-check or design-doc names.
- **Tests that reach Spring beans** follow the existing class's setup. `ModernRadarWebTest` and `ModernGanttTicksTest` already have it.

## Review Focus

1. **A two-spoke marked radar** draws arcs, not polygons (`getSidesCount() < 3`). Its rings and the circular outer ring must land at the right radius. Task 2 and Task 3 each test it.
2. **Cards whose plot diameter is over 500 px** (e.g. 1200x1200), where today's rings overshoot the outer ring, and cards near 500 px, where the two errors cancel. Task 2 tests 400x240 and 1200x1200.
3. **An author who hides a measure axis line or the label axis line** (`lineVisible` false, or max mode with `maxModeLineVisible` false). That axis's web line is absent, and its labels stay. Task 3 tests it.
4. **A fitted Gantt whose data starts exactly on a unit boundary** (Jan 1 00:00) keeps its first tick at the min. A fitted Gantt with an author min keeps today's behaviour. Task 1 tests both.
5. **A dark marked radar.** The web lines take the dark grid colour, and an author's colours win in dark too. Task 3 tests it.

---

### Task 1: A fitted Gantt time axis keeps the data range

**Files:**
- Modify: `core/src/main/java/inetsoft/graph/scale/TimeScale.java:620-676` (`getTicks`: `alignFloor` and the drop rule)
- Test: `core/src/test/java/inetsoft/graph/scale/TimeScaleTest.java`
- Test: `core/src/test/java/inetsoft/report/composition/graph/ModernGanttTicksTest.java:128` (expected label change), plus one new test

**Interfaces:** no new API.

- [ ] **Step 1: Write the failing tests in `TimeScaleTest`.** Add a helper `private static DefaultDataSet dailyData(long start, int days)` (the existing `dailyData()` becomes `dailyData(date(2026, JANUARY, 1, 0, 0, 0), 120)`, or keep it and add the overload). Then add:
  - `aFittedAxisStartsAtTheDataMin`: `setLabelFit(300, 45)`, then `init(dailyData(date(2026, JANUARY, 5, 0, 0, 0), 91))`.
    - Premise: `getType() == MONTH` and `getFitStep() == 1`.
    - `assertEquals(start, scale.getMin(), 0)`
    - `assertEquals(date(2026, FEBRUARY, 1, 0, 0, 0), scale.getTicks()[0], 0)`
    - `assertEquals(start + 90 * DAY_MS, scale.getMax(), 0)`
  - `aFittedAxisBeforeNineteenSeventyStartsAtTheDataMin`: `setLabelFit(302, 49)`, then `init(dailyData(date(1965, FEBRUARY, 16, 0, 0, 0), 400))`.
    - Premise: `getType() == QUARTER`.
    - `assertEquals(date(1965, APRIL, 1, 0, 0, 0), scale.getTicks()[0], 0)`
    - `assertEquals(start, scale.getMin(), 0)`
  - `aFittedAxisStartingOnABoundaryKeepsThatTick`: `dailyScale(true)`, whose data starts Jan 1.
    - `getTicks()[0] == date(2026, JANUARY, 1, 0, 0, 0)` and `getMin()` is the same value.
  - `anAuthorMinKeepsTodaysTicks`: `setLabelFit(300, 45)`, `setMin(new Date(date(2026, JANUARY, 5, 0, 0, 0)))`, then `init(dailyData(...Jan 5..., 91))`.
    - `getTicks()[0]` equals Jan 5: an author min is never truncated.
- [ ] **Step 2: Run them and confirm the first two fail.**
  - Run: `./mvnw -q test -pl core "-Dtest=TimeScaleTest" -Dsurefire.failIfNoSpecifiedTests=false`
  - Expected: `aFittedAxisStartsAtTheDataMin` fails, with min = Jan 1 2026. The 1965 test fails, with first tick and min = Jan 1 1965. The other two pass.
- [ ] **Step 3: Implement in `TimeScale.getTicks`.**
  - Replace the `long alignFloor = -1` sentinel with `boolean align = false`, set true where `alignToStep` is called (`fitStep > 0`).
  - The drop rule then reads: `if(align && !nullValue && dates.stream().anyMatch(d -> d.getTime() >= min)) { dates.removeIf(d -> d.getTime() < min); }`. Here `min` is the data min local, which is never reassigned.
  - Update the rule's comment to a short clause.
- [ ] **Step 4: Update `ModernGanttTicksTest:128`, and add the scale-min test there.**
  - That fixture's data starts Jan 4, so its labels become Feb, Mar, Apr. Replace the `startsWith("Jan")` assertion with `startsWith("Apr")` and keep Feb and Mar.
  - Add `aMarkedGanttAxisStartsAtTheFirstTask`: `gantt(25, VizMark.MODERN_LIGHT, new Dimension(400, 240), false)`, then `plotAndLayout`. Read the time scale as `ModernGanttEndTest:70-77` does, and assert that `getMin()` equals the first task's start.
- [ ] **Step 5: Run and confirm the tests pass.**
  - Run: `./mvnw -q test -pl core "-Dtest=TimeScaleTest,ModernGanttTicksTest,ModernGanttEndTest,ModernAxisLabelRuleTest" -Dsurefire.failIfNoSpecifiedTests=false`
  - Expected: all pass. Read `core/target/surefire-reports/*.txt`.
- [ ] **Step 6: Commit** (controller): `Chart defaults S8, keep a fitted Gantt axis on its data range`

### Task 2: Marked radar rings sit at their radius

**Files:**
- Modify: `core/src/main/java/inetsoft/graph/coord/PolarCoord.java`. The gate goes next to `sidesFromSpokes` (`:1309-1317`, field `:1371`), plus `equalsContent :1272`, `transformLine :279`, a new helper beside `getR :325`, and `layoutGridLines :843-849`.
- Modify: `core/src/main/java/inetsoft/report/composition/graph/RadarGraphGenerator.java:166-171`
- Test: `core/src/test/java/inetsoft/graph/coord/RadarWebTest.java`, `core/src/test/java/inetsoft/report/composition/graph/ModernRadarWebTest.java`

**Interfaces:**
- Produces: `PolarCoord.setModernWeb(boolean)` and `PolarCoord.isModernWeb()`. Javadoc `@hidden`, no `@TernMethod`, default false, included in `equalsContent`. Task 3 gates on it.

- [ ] **Step 1: Write the failing tests in `RadarWebTest`.**
  - Give `vgraph(...)` a `modern` parameter that calls `polar.setModernWeb(true)`, and a size parameter for `plotAndLayout`. Existing callers keep `false` and 400x240.
  - Measure each painted ring after `plotAndLayout`:
    - centre `c = spoke.getGridLineTransform().transform(origin)`
    - `outerR = polarAxis.getWidth() / 2`, where the `PolarAxis` comes from `getAxes(false)`
    - a ring's radius = the distance from `c` to the first path point of `spoke.getGridLineTransform().createTransformedShape(gl.getShape())`
  - Tests:
    - `modernQuarterRingsSitAtTheirFraction`: max `{100, 7, 2000}`, THIN, modern. For every spoke, ring k (k = 0..2) is at `(k + 1) / 4 * outerR` ± 0.5. Run at 400x240 and at 1200x1200.
    - `modernSharedTickRingsSitAtTheirValue`: max `{100, 100, 100}`, modern. The ring at tick t is at `t / 100 * outerR` ± 0.5, at both sizes.
    - `aTwoSpokeModernRadarRingsSitAtTheirFraction`: max `{100, 2000}`, modern, 400x240. The arcs are at `(k + 1) / 4 * outerR` ± 0.5.
    - `classicRingsKeepTodaysRadius`: same data, modern off, webGrid on. Ring k is at `(k + 1) / 4 * 4 * outerR * outerR / 1000` ± 0.5, at 400x240. This pins today's classic result.
  - Avoid sizes whose plot diameter is near 500 px, where the two errors cancel.
- [ ] **Step 2: Run and confirm the modern tests fail.**
  - Run: `./mvnw -q test -pl core "-Dtest=RadarWebTest" -Dsurefire.failIfNoSpecifiedTests=false`
  - Expected: the modern tests fail, with rings at about 0.38x at 400x240 and above 1x at 1200x1200. The classic test passes.
- [ ] **Step 3: Implement.**
  - Add the flag.
  - In `transformLine`, use `double r = modernWeb ? dataRadius(p1.getY()) : getR(p1.getY());`. This covers both the polygon and the arc branches.
  - Add `private double dataRadius(double y)`, matching `transformPoint0`'s radius for the same `y`: `type == PLUS ? getR(y) : y * Math.min(GWIDTH, GHEIGHT) / 2 / GHEIGHT`.
  - In `layoutGridLines`, skip the `scale.createTransformedShape` loop when `modernWeb`, and keep the top-ring removal.
  - In `RadarGraphGenerator`, inside the existing `if(vizContext.modern)` block, add `polarCoord.setModernWeb(true);`.
- [ ] **Step 4: Add a flag test in `ModernRadarWebTest`.** `isModernWeb()` is true on the marked radar's `PolarCoord` and false on the classic one, in the same style as the existing `sidesFromSpokes` checks.
- [ ] **Step 5: Run and confirm the tests pass.**
  - Run: `./mvnw -q test -pl core "-Dtest=RadarWebTest,ModernRadarWebTest,RadarScaleLabelTest" -Dsurefire.failIfNoSpecifiedTests=false`
  - Expected: all pass.
- [ ] **Step 6: Commit** (controller): `Chart defaults S8, place marked radar rings at their radius`

### Task 3: Marked radar spokes and outer ring draw under the fill

**Files:**
- Modify: `core/src/main/java/inetsoft/graph/guide/axis/Axis.java`: a `lineAsGrid` field beside `lineVisible :572`, with a `@hidden` setter and getter. It is not serialized, and it is copied wherever `lineVisible` is copied.
- Modify: `core/src/main/java/inetsoft/graph/guide/axis/AxisLine.java:123` and `core/src/main/java/inetsoft/graph/guide/axis/PolarAxisLine.java:88`: the stroke is painted only when `isLineVisible() && !isLineAsGrid()`. Size and label offsets keep reading `isLineVisible()`, so layout does not move.
- Modify: `core/src/main/java/inetsoft/graph/coord/PolarCoord.java`:
  - `createAxis :469-537`: record the spoke `DefaultAxis` objects with their logic end points (`pos`, `pos2`), and the `PolarAxis axis2`.
  - a new `createWebLines`
  - a `weblines` field and a `@hidden getWebLines()`
  - the end of `fit`, and `clone(boolean) :992-997`
- Test: `RadarWebTest`, `ModernRadarWebTest`.

**Interfaces:**
- Consumes: `PolarCoord.isModernWeb()` (Task 2).
- Produces: `Axis.setLineAsGrid(boolean)`, `Axis.isLineAsGrid()`, and `PolarCoord.getWebLines()` (a `List<GridLine>`, for tests).

- [ ] **Step 1: Write the failing tests.**
  - In `RadarWebTest`, with modern on, at 400x240, max `{100, 7, 2000}`:
    - `modernWebLinesDrawUnderTheSeries`: every `getWebLines()` line has z `GDefaults.GRIDLINE_Z_INDEX`, which is below every VO's z.
      - The spoke axes keep `VO_Z_INDEX + 1` and the `PolarAxis` keeps `AXIS_Z_INDEX`.
      - Each of those axes has `isLineAsGrid()` true and `isLineVisible()` true.
    - `modernSpokesRunFromTheCentreToTheOuterRing`: exactly 3 spoke lines. Each painted spoke starts at `c` and ends at distance `outerR`, ± 0.5. Each end matches a vertex of the painted outer ring, ± 0.5. The outer ring has 3 vertices, all at `outerR`.
    - `modernYGridNoneKeepsSpokesAndOuterRing`: grid style NONE. There are no inner rings, but 3 spokes and the outer ring.
    - `aTwoSpokeModernRadarHasACircularOuterRing`: max `{100, 2000}`. The outer web line's painted bounds are a circle of radius `outerR`, ± 0.5.
    - `classicWebGridHasNoWebLines`: webGrid on, modern off. `getWebLines()` is empty, `isLineAsGrid()` is false everywhere, and the z values are 61 and 70.
  - In `ModernRadarWebTest`, built as today, then `Plotter.getPlotter(graph).plotAndLayout(data, 0, 0, 400, 240)`. Premise: VOs exist.
    - `anAuthorMeasureAxisColourReachesEverySpoke`: red line colour on every measure axis descriptor. Every spoke web line is red.
    - `anAuthorLabelAxisColourReachesTheOuterRing`: `getLabelAxisDescriptor().setLineColor(BLUE)`. The outer web line is blue, and the spokes keep the grid colour.
    - `webLinesDefaultToTheGridColour`: the light mark gives `#D6D2C8`, and the dark mark gives `#4A484F`, on every web line.
    - `theYGridColourReachesOnlyTheRings`: green Y grid colour. The inner rings are green, and the spokes and outer ring are not.
    - `aHiddenLabelAxisLineHasNoOuterRing`: label axis `lineVisible` false. There is no outer web line, and the spoke names still draw.
- [ ] **Step 2: Run and confirm the tests fail** (`getWebLines` or `isLineAsGrid` missing). Run `-Dtest=RadarWebTest,ModernRadarWebTest`.
- [ ] **Step 3: Implement.**
  - In `createAxis`, when `modernWeb`, call `createWebLines(vgraph, spokes, ends, outer)` after the axis loop.
    - For each spoke with `isLineVisible()` and a line colour: `new GridLine(new Line2D.Double(transformPoint0(p0), transformPoint0(p1)), spoke, GDefaults.GRIDLINE_Z_INDEX, true)`, colour `spoke.getLineColor()`, style `GraphConstants.THIN_LINE`. Then `vgraph.addVisual`, `spoke.setLineAsGrid(true)`.
    - For the outer ring: one closed `Path2D` through `transformPoint0(ctrans(x_i, GHEIGHT))`, with `x_i = (i + 0.5) * GWIDTH / n` and n = the parallel scale count. Use an `Ellipse2D` of radius maxr when `getSidesCount() < 3`. It is owned by the `PolarAxis`, with that axis's line colour, THIN_LINE, and `setLineAsGrid(true)`.
  - Do NOT use `axis.addGridLine`, which would expose the lines to grid removal, too-close pruning and `layoutGridLines`.
  - Painting needs no change in `fit`: `GridLine.paint` applies the owner's grid transform, which is the same mapping as the data.
  - At the end of `fit`, give a web line z -1 when its owner's z is below 0.
  - Reset `weblines` in `clone(boolean)`.
  - Colours need no generator change: the spoke specs already carry `resolveSpokeColor(axisD.getLineColor())` (`GraphGenerator` around `:2573`), and the label spec carries `resolveSpokeColor(xdesc.getLineColor())` (`RadarGraphGenerator:215`).
- [ ] **Step 4: Run and confirm the tests pass.**
  - Run: `./mvnw -q test -pl core "-Dtest=RadarWebTest,ModernRadarWebTest,RadarScaleLabelTest,ChartLinePaneModelRadarTest" -Dsurefire.failIfNoSpecifiedTests=false`
  - Expected: all pass.
- [ ] **Step 5: Commit** (controller): `Chart defaults S8, draw marked radar spokes and outer ring under the fill`

### Task 4: Package and full-core verification

**Files:** none. Do not change the code. Report failures.

- [ ] **Step 1: Run the packages.**
  - Run: `./mvnw -q test -pl core "-Dtest=inetsoft.graph.**.*Test,inetsoft.report.composition.graph.**.*Test" -Dsurefire.failIfNoSpecifiedTests=false`
  - Expected: no failures other than the known epic-base ones: `BoxPainterTest.borderColorWithoutColorFrameDoesNotThrow`, and `BaseTableCellModelFormatCacheTest` x2 if they are in scope.
- [ ] **Step 2: Run the full core suite.**
  - Run: `./mvnw test -pl core`. Record the totals.
  - Expected: only the 3 known epic-base failures, plus any timing flake that passes alone. Re-run a flake alone to prove it.
- [ ] **Step 3: Report** the counts and every failure with its class. No commit.

## After the plan (controller)

These are live re-runs on a rebuilt head server, using the `chart-defaults-s8` tooling:
- the radar rows R-2a to R-2h, R-2f and R-2k
- the Gantt rows G-7a, G-7i, G-7j, G-7k, G-7l and G-7s
- classic identity: capture `CD8 Dash C` and `CD8b Dash C` on the new head, and compare them with the phase C base captures (`cibase`)

Then fix the two handoffs: T-6i and RT-4 become PASS, with the reasons recorded in the controller notes.
