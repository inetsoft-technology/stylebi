# Chart Defaults S5a — Label Rule and Compact Dates Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** On a modern chart, category labels thin before they turn, never past 45°, and end in an ellipsis at about 12 characters. Date labels stay horizontal and show the year only where it changes. Classic charts are unchanged.

**Architecture:**
- **Graph package.** Two new `AxisSpec` properties carry the modern rules to `DefaultAxis`, and both default to off:
  - a label length limit, which also switches on the ellipsis and strict thinning
  - compact dates, a small value object that picks a full and a brief date pattern
- **Generator.** `GraphGenerator.setDefaultRotation` gains a modern branch: it measures the labels, estimates the axis length and picks 0° or 45° (D79). A new `applyCompactDates` sets compact dates on every date axis of a marked chart.
- **Format pane.** `FormatPainterService.fixAxisDefaultFormat` asks the same helper for its default pattern, so the dialog shows the full compact pattern on marked charts (D82).
- Nothing is stored: every S5a item is a drawing rule, and no XML changes.

**Tech Stack:** Java 21, JUnit 5, Mockito, Spring test context (`@SreeHome`), Maven module `core`. No web change.

**Spec:** `docs/superpowers/specs/lookfeel/2026-11-06-chart-defaults-design.md`, amended for S5 in `4c2048d3d2`. The relevant parts are:
- §3 M1 and M9
- §5 S5: the shaping facts, the S5-1 and S5-2 rows, "S5-1 label rule", "S5-2 compact dates"
- §7's two S5 rows, §9's S5 items, §11 "Labels (S5)"
- §10 D69–D72, D78–D84, D87–D90

**Builds on:** S4b (`bug-80455-s4b` at `40f883e30b`). Nothing from S4 is reused; S5a only shares `GraphGenerator`.

**Verified against:** `bug-80455-s5a` at `4c2048d3d2`. Paths are under `core/src/main/java/inetsoft/` unless they start with `core/`.

## Corrections to the spec

| # | Spec | Code at `4c2048d3d2` | This plan |
|---|---|---|---|
| C1 | D79: the generator "estimates the axis length" | Only the X axis can turn; a Y category axis stays horizontal under every rule | The estimate is made for X axes only. A Y category axis gets the limit and no rotation |
| C2 | D88: "date-comparison axes" keep today's labels | `setupDateComparisonAxis` (`GraphGenerator:2656-2667`) runs on every X axis of a chart with date comparison applied, after `setDefaultRotation` | Every X axis of a chart with date comparison applied keeps today's path. Its Y axes follow S5a |
| C3 | D69: "never past 45°" | Classic CJK labels turn −90 so the characters read top-down (`setDefaultRotation:6739`, `:6769`) | A CJK label turns 45 like any other |
| C4 | D71: the author's rotation and truncate setting win | `setDefaultRotation` runs only when the label format has no rotation (`:2420`, `:2810`); truncate off makes labels wrap (`DefaultAxis:90-91`) | No modern label property is set on an axis with an author rotation or with truncate off. Those axes keep today's band and cut |
| C5 | §9: "which label format each date column reads" | No longer needed: D80 dropped the stored pattern | — |
| C6 | S5-2 row: "The Gantt time axis keeps the density padding beyond the last date" | This is plot padding (C19), not a label rule. `AxisSpec.maxPadding` only reserves room for the last label (`DefaultAxis:1819`); the last bar meets the scale's maximum | Moved to S6, beside S6-1's plot padding. S5a does not change the Gantt's extent |

**Known limitations:**
- **Older builds** draw today's labels (M9).
- **The axis length is an estimate** (D79). Near the every-other-label threshold a chart may turn to 45° where horizontal would just fit, or the reverse. The layout still removes any overlap.
- **Hour, minute and second levels** keep today's patterns (D84).

## Global Constraints

- **Worktree:** `$WT` is the executor's worktree on branch `bug-80455-s5a`, at or after `4c2048d3d2`. Never work in `E:/StyleBI/stylebi-enterprise/community`.
- **Git:**
  - Each git command is its own call, `git -C "$WT" ...`, with no `&&` anywhere in the command.
  - One commit per task, subject `Chart defaults S5, <what changed>`, last line `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
  - Subagents never run `git add`, `git commit` or `git stash` (a hook blocks them); the controller stages and commits after review. Never push.
- **Scope is the mark (M1).** Every new graph-package property defaults to off, and off draws today's labels. A classic chart is pixel-identical.
- **Values (all under the mark):**
  - **Length limit:** 66px for 10px text, scaled by the label font size (`66 × size / 10`). Labels longer than the limit end in "…" (U+2026), not "..".
  - **Y category column:** `max(limit, min(0.3 × graph width, 110 × size / 10))`.
  - **Horizontal step:** the smallest k with `k × slot ≥ widest label + 4`.
  - **45° spacing:** `(line height + 2) / sin 45°`, about 19.8px for 10px text.
  - **The turn:** date and number labels stay at 0° at any step. Text labels stay at 0° when k ≤ 2, otherwise 45°. Rotation value 45 (reads upward to the right).
  - **Compact patterns** (month-first locales; see Task 3 for the others): quarter `QQQ yyyy` / `QQQ`, month `MMM yyyy` / `MMM`, week and day `MMM d, yyyy` / `MMM d`. Year and finer than a day: none (today's pattern).
- **Comments:** a short clause each, with no ticket, PR, slice or design-doc names.
- **New files** start with the AGPL header of `SeedChromeDefaultsTest.java`, `Copyright (C) 2026  InetSoft Technology`.
- **Tests that reach Spring beans** use `@ExtendWith(SpringExtension.class)`, `@ContextConfiguration(classes = BaseTestConfiguration.class, initializers = ConfigurationContextInitializer.class)`, `@SreeHome`, `@Tag("core")`, and clear `viewsheet.modernVisualization`, `viewsheet.darkMode` and `viewsheet.density` in `@AfterEach`.

## Review Focus

1. **A classic chart's labels are untouched**: today's 0°/90° choice, the ".." cut, the 50px floor, the overlap a category axis tolerates, and today's date labels. Pinned by the `classic…` cases in every task.
2. **An author's settings on a marked chart** (a rotation, truncate off, a fixed axis size, a date format) keep today's behaviour. Pinned by `anAuthorsRotationKeepsTodaysLabels`, `truncateOffKeepsWrapping` (Task 2) and `anAuthorsDateFormatKeepsTodaysLabels` (Task 4).
3. **A thinned-away January** still leaves the year on the next shown label. Pinned by `theYearMovesToTheFirstShownLabel` (Task 3).
4. **A Top N or by-value date axis** shows the year on every label. Pinned by `aDateAxisOutOfOrderShowsTheYearOnEveryLabel` (Task 4).
5. **A date-comparison chart on a modern dashboard** keeps its 90° merged labels. Pinned by `aDateComparisonChartKeepsTodaysXLabels` (Task 2) and `aDateComparisonChartGetsNoCompactDates` (Task 4).

## File Structure

- **Task 1:** `graph/AxisSpec.java` (length limit), `graph/guide/VLabel.java` (ellipsis), `graph/guide/axis/DefaultAxis.java` (limit, strict thinning).
- **Task 2:** `report/composition/graph/ModernLabelRule.java` (new), `report/composition/graph/GraphGenerator.java` (`setDefaultRotation`).
- **Task 3:** `graph/guide/axis/CompactDates.java` (new), `graph/AxisSpec.java` (compact dates), `graph/guide/axis/DefaultAxis.java` (label text, year after thinning).
- **Task 4:** `report/composition/graph/CompactDateRule.java` (new), `GraphGenerator.java` (`applyCompactDates`), `web/composer/vs/controller/FormatPainterService.java` (`fixAxisDefaultFormat`).
- **Task 5:** none (whole-branch check and live-check handoff).

**Test harnesses:**
- **Graph-level tests** build an `EGraph` and lay it out as `core/src/test/java/inetsoft/graph/guide/axis/AxisLabelGapShrinkTest.java` does (a `CategoricalScale` on X, an `IntervalElement`, a `DefaultDataSet`, `Plotter.getPlotter(graph).plotAndLayout(...)`), then read the X axis's `DefaultAxis.getLabels()`; a hidden label has `getZIndex() < 0`.
- **Generator tests** copy `ModernMarkRulesTest`'s `Chart` record, `dim`, `agg` and `generator(...)`, but pass `new Dimension(width, height)` as the last argument of `GraphGenerator.getGenerator`, so `graphSize` is set. Read a rotation as `((RectCoord) graph.getCoordinate()).getXScale().getAxisSpec().getTextSpec().getRotation()`.

## How to run tests

From `$WT`:

```bash
./mvnw -q test -pl core "-Dtest=ClassA,ClassB" -Dsurefire.failIfNoSpecifiedTests=false
```

- Read `core/target/surefire-reports/*.txt` for counts.
- Run every new or changed class alone once as well (CLAUDE.md).
- If compilation fails with "Attempt to recreate … HeartBeatServiceProxy", delete the untracked `core/target/generated-test-sources`.

---

### Task 1: Length limit, ellipsis and strict thinning in the axis (S5-1, graph package)

**Files:**
- Modify: `graph/AxisSpec.java` (field, accessors, `equals`, `hashCode`)
- Modify: `graph/guide/VLabel.java` (`getDisplayLabel:360`, the ".." at `:451-453`, `:488`, `:499-503`)
- Modify: `graph/guide/axis/DefaultAxis.java` (label setup `:85-100`, `getMaxLabelSize:965-1020`, `removeOverlappedLabels:1503`, `oH`/`oW` at `:1610-1620`)
- Test: `core/src/test/java/inetsoft/graph/guide/axis/ModernAxisLabelTest.java` (new)

**Interfaces:**
- Produces:
  - `AxisSpec.setLabelLimit(double px)` / `double getLabelLimit()`: 0 (default) is off. Above 0 it is the longest label in screen px, the ellipsis is "…", and thinning tolerates no overlap.
  - `VLabel.setEllipsis(boolean)` / `boolean isEllipsis()`: when true, truncation appends "…" instead of "..".

- [ ] **Step 1: Write the failing tests** (`ModernAxisLabelTest`)
  - `aLimitedLabelEndsInAnEllipsisAt66px`: 8 categories, one 28 characters long, X axis `setLabelLimit(66)`, 800×300 → that label's display text ends in "…", its measured width ≤ 66, short labels uncut.
  - `theLimitHoldsOnASmallCard`: the same at 300×180 → the long label is still cut at 66, not at `max(50, half the coordinate)`.
  - `theLimitAppliesAt45Degrees`: text spec rotation 45 → each label's text width ≤ 66, and the axis height ≈ `(66 + line height) × sin 45°` ±2.
  - `limitedLabelsNeverOverlap`: 30 categories "Store 01" … "Store 30" at 0°, limit 66, 400×240 → no two shown labels' bounds intersect.
  - `shownLabelsKeepARegularStep`: the same → the indices of shown labels are evenly spaced.
  - `aFixedAxisSizeBeatsTheLimit`: limit 66 at 45° with `setAxisSize(30)` → the axis is 30px tall, below the band the limit alone would need (D70).
  - `classicLabelsKeepTwoDotsAndTheFloor`: no limit → the long label ends in "..", and on 300×180 its cut follows today's `max(50, …)`.
  - `classicCategoryAxisStillToleratesOverlap`: no limit, the 30-store case → the same shown labels as before the change (record them first, on the base build).

- [ ] **Step 2: Run them and see them fail**

Run: `./mvnw -q test -pl core "-Dtest=ModernAxisLabelTest" -Dsurefire.failIfNoSpecifiedTests=false`
Expected: compile failure on `setLabelLimit`; after stubs, the modern cases fail ("..", 50px floor, overlaps).

- [ ] **Step 3: Implement**
  - `DefaultAxis`, label setup: when `getLabelLimit() > 0`, call `setEllipsis(true)` on each label.
  - `getMaxLabelSize`: when the limit is on, cap the label along its text direction at the limit, instead of the ratio and the 50px floor.
  - `removeOverlappedLabels`: when the limit is on, use the continuous tolerances (`oH = 1`, `oW = 0`), so category labels thin until none overlap. Keep the existing alternate-label passes; steps of 2, 4 and 8 are acceptable (spec S5-1).
  - Add the field to `AxisSpec.equals` and `hashCode`.

- [ ] **Step 4: Run the tests and see them pass**, plus `AxisLabelGapShrinkTest` unchanged.

- [ ] **Step 5: Commit** — `Chart defaults S5, cut modern axis labels at a length limit and thin them without overlap`

---

### Task 2: The modern label rule in the generator (S5-1, D69–D71, D78, D79, D88, D89)

**Files:**
- Create: `report/composition/graph/ModernLabelRule.java`
- Modify: `report/composition/graph/GraphGenerator.java` (`setDefaultRotation:6724-6773`, its callers `:2420` and `:2810`)
- Test: `core/src/test/java/inetsoft/report/composition/graph/ModernLabelRuleTest.java` (new, no Spring), `core/src/test/java/inetsoft/report/composition/graph/ModernAxisLabelRuleTest.java` (new, Spring)

**Interfaces:**
- Consumes: Task 1's `AxisSpec.setLabelLimit`.
- Produces: `final class ModernLabelRule` (package-private) with
  - `static final double LIMIT_PX = 66`, `GAP_PX = 4`, `COLUMN_RATIO = 0.3`, `COLUMN_PX = 110`
  - `static double limitPx(Font font)`: `66 × font.getSize2D() / 10`
  - `static double columnLimitPx(Font font, double graphWidth)`: `max(limitPx, min(0.3 × graphWidth, 110 × size / 10))`
  - `static int step(double widestPx, double slotPx)`: the smallest k ≥ 1 with `k × slotPx ≥ widestPx + 4`
  - `static int rotation(boolean dateOrNumber, double widestPx, double slotPx)`: 0 when `dateOrNumber` or `step ≤ 2`, else 45

- [ ] **Step 1: Write the failing unit tests** (`ModernLabelRuleTest`)

```java
@Test void datesStayFlatAtAnyStep() {
   assertEquals(0, ModernLabelRule.rotation(true, 60, 3));
}
@Test void textStaysFlatAtEveryOtherLabel() {
   assertEquals(0, ModernLabelRule.rotation(false, 66, 44));   // step 2
}
@Test void textTurnsTo45PastEveryOtherLabel() {
   assertEquals(45, ModernLabelRule.rotation(false, 45, 12));  // step 5
}
@Test void theLimitScalesWithTheFont() {
   assertEquals(66, ModernLabelRule.limitPx(new Font("Roboto", Font.PLAIN, 10)), 0.01);
   assertEquals(79.2, ModernLabelRule.limitPx(new Font("Roboto", Font.PLAIN, 12)), 0.01);
}
@Test void theColumnLimitNeverDropsBelowTheMinimum() {
   Font f = new Font("Roboto", Font.PLAIN, 10);
   assertEquals(110, ModernLabelRule.columnLimitPx(f, 800), 0.01);
   assertEquals(90, ModernLabelRule.columnLimitPx(f, 300), 0.01);
   assertEquals(66, ModernLabelRule.columnLimitPx(f, 150), 0.01);
}
```

- [ ] **Step 2: Write the failing generator tests** (`ModernAxisLabelRuleTest`, 400×240 unless stated)
  - `eightShortNamesStayFlat`: bar, X "A" … "H" marked → rotation 0, `getLabelLimit()` 66.
  - `thirtyStoresTurnTo45`: bar, X "Store 01" … "Store 30" marked → rotation 45. Classic → 90 (`classicThirtyStoresStillTurn90`).
  - `dailyDatesStayFlat`: a date dimension at day level with 105 dates marked → rotation 0.
  - `aHorizontalBarsCategoriesGetTheColumnLimit`: measure on X, "Store …" on Y, marked, 400 wide → Y rotation 0, limit 110 (`max(66, min(120, 110))`).
  - `facetInnerLabelsFollowTheRule`: X Region (2 values) then State, marked → the inner State axis is not forced to 90; the outer Region axis has rotation 0 and `getLabelLimit() == 0` (D89). Classic → inner 90 (`classicFacetInnerStillTurns90`).
  - `cjkLabelsTurn45Too`: 30 Chinese store names marked → rotation 45, not −90 (C3).
  - `anAuthorsRotationKeepsTodaysLabels`: the label format's rotation set to 30 → rotation 30, limit 0.
  - `truncateOffKeepsWrapping`: the axis descriptor's truncate off → limit 0.
  - `aDateComparisonChartKeepsTodaysXLabels`: spy the `VSChartInfo` so `isAppliedDateComparison()` returns true → X limit 0 and today's rotation.

- [ ] **Step 3: Run them and see them fail**

Run: `./mvnw -q test -pl core "-Dtest=ModernLabelRuleTest,ModernAxisLabelRuleTest" -Dsurefire.failIfNoSpecifiedTests=false`
Expected: `ModernLabelRule` missing; after a stub, the marked cases get 90 and limit 0.

- [ ] **Step 4: Implement `ModernLabelRule` and the modern branch of `setDefaultRotation`**
  - The branch runs when `vizContext.modern`, the axis spec's truncate is on, and the chart has no date comparison applied for an X axis. Otherwise today's code runs unchanged.
  - **Date or number** is the field's type: `XDimensionRef.isDateTime()` or `XSchema.isNumericType(ref.getDataType())` of `info.getFieldByName(dim, true)`.
  - **Y axis** (the `:2810` caller): rotation 0 and `setLabelLimit(columnLimitPx(font, graphWidth))`.
  - **X axis:** measure the formatted, aliased labels with `GTool.stringWidth`, each capped at `limitPx`. `slot = estimateAxisLength() / panels / labels`, rotation from `ModernLabelRule.rotation`, and `setLabelLimit(limitPx(font))`.
  - **A facet's inner axis** (`dims.size() > 1`, the last dim): `panels` is the product of the distinct values of the outer X dims. The outer axis keeps today's rotation 0 and gets no limit (D89).
  - **`estimateAxisLength()`** (new private method):

```
graph width (1000 when graphSize is null)
  − chart padding left + right (VSDensityDefaults.chartPadding(vizContext))
  − widest formatted label of the primary Y scale, initialised on data (0 when its labels are hidden) − 8
  − 16 when the Y axis title is visible
  − 0.25 × graph width when a legend is visible on the left or right
```

- [ ] **Step 5: Run the tests and see them pass**, plus `ChartChromeRenderTest`, `ModernMarkRulesTest` and `AxisLabelGapShrinkTest` unchanged.

- [ ] **Step 6: Commit** — `Chart defaults S5, choose modern label rotation from measured labels`

---

### Task 3: Compact dates in the axis (S5-2, D72, D87, D90, graph package)

**Files:**
- Create: `graph/guide/axis/CompactDates.java`
- Modify: `graph/AxisSpec.java` (field, accessors, `equals`, `hashCode`)
- Modify: `graph/guide/axis/DefaultAxis.java` (label creation `:85-100` / `createLabel:118`, after `removeOverlappedLabels` at `:1407`)
- Test: `core/src/test/java/inetsoft/graph/guide/axis/CompactDatesTest.java` (new, no Spring), `ModernAxisLabelTest.java`

**Interfaces:**
- Produces:
  - `public final class CompactDates implements Serializable`, immutable, `equals`/`hashCode` on both fields:
    - `public static final int FROM_SCALE = -1`
    - `public CompactDates(int unit, Locale locale)`: `unit` is a `TimeScale` constant (`QUARTER`, `MONTH`, `WEEK`, `DAY`, …), or `FROM_SCALE` to take `TimeScale.getType()` at layout
    - `public static String[] patterns(int unit, Locale locale)`: `{ full, brief }`, or `null` for `YEAR`, `HOUR`, `MINUTE`, `SECOND` and anything else
    - `public Format full(Scale scale)` / `public Format brief(Scale scale)`: `ExtendedDateFormat`s of those patterns, or `null`
  - `AxisSpec.setCompactDates(CompactDates)` / `CompactDates getCompactDates()`; `null` (default) is off

- [ ] **Step 1: Write the failing unit tests** (`CompactDatesTest`)
  - `monthFirstLocales`: `Locale.US` → quarter `{ "QQQ yyyy", "QQQ" }`, month `{ "MMM yyyy", "MMM" }`, day and week `{ "MMM d, yyyy", "MMM d" }`.
  - `dayFirstLocales`: `Locale.GERMANY`, `Locale.UK` → day `{ "d MMM yyyy", "d MMM" }`, month as US.
  - `yearFirstLocales`: `Locale.JAPAN` → month `{ "yyyy MMM", "MMM" }`, day `{ "yyyy MMM d", "MMM d" }`, quarter `{ "yyyy QQQ", "QQQ" }`.
  - `yearAndTimeUnitsHaveNone`: `YEAR`, `HOUR`, `MINUTE`, `SECOND` → `null`.
  - `aQuarterFormats`: `full(null)` of `QUARTER` on 2025-04-01 → "Q2 2025".

- [ ] **Step 2: Write the failing axis tests** (`ModernAxisLabelTest`)
  - `monthsShowTheYearOnlyWhereItChanges`: 24 month-start dates 2025-01 … 2026-12 on a `CategoricalScale`, `CompactDates(MONTH, US)`, 1600 wide (no thinning) → "Jan 2025", "Feb", … "Dec", "Jan 2026", "Feb", ….
  - `daysShowTheMonthOnEveryLabel`: 10 days from 2025-12-29 → "Dec 29, 2025", "Dec 30", "Dec 31", "Jan 1, 2026", "Jan 2", ….
  - `theYearMovesToTheFirstShownLabel`: 24 months starting 2025-02 at 400 wide, where thinning hides "Jan 2026" → the first shown label of 2026 carries "2026", and no two shown labels overlap.
  - `aTimeScaleTakesItsTickUnit`: a `TimeScale` over 2026-01-01 … 2026-06-30 with month ticks, `CompactDates(FROM_SCALE, US)` → "Jan 2026", "Feb", "Mar", ….
  - `anAliasedValueKeepsItsAlias`: an alias on one month → that label shows the alias.
  - `classicDatesUnchanged`: no compact dates → today's labels ("2025 Jan", "2025 Feb", … on a category axis).

- [ ] **Step 3: Run them and see them fail**, `CompactDates` missing, then today's labels.

- [ ] **Step 4: Implement**
  - **`patterns`:** read the locale's order from `DateTimeFormatterBuilder.getLocalizedDateTimePattern(FormatStyle.MEDIUM, null, IsoChronology.INSTANCE, locale)`. Year-first when `y` comes before `M`; otherwise day-first when `d` comes before `M`; otherwise month-first.
  - **Label creation:** when `getCompactDates()` is set, the value is a `Date` and it has no alias, the label text is `full` for the first label and for any label whose calendar year differs from the previous value's, and `brief` otherwise. When both formats are `null`, today's path runs.
  - **After `removeOverlappedLabels`:** walk the shown labels (`getZIndex() >= 0`) in order. A label whose year differs from the previous shown label's, and which carries the brief text, gets the full text (`VLabel.setLabel`). If its new bounds overlap the next shown label, hide that label.
  - Add the field to `AxisSpec.equals` and `hashCode`.

- [ ] **Step 5: Run the tests and see them pass.**

- [ ] **Step 6: Commit** — `Chart defaults S5, draw compact dates with the year only where it changes`

---

### Task 4: Compact dates on marked charts and in the Format pane (S5-2, D72, D80, D82–D84, D87, D88)

**Files:**
- Create: `report/composition/graph/CompactDateRule.java`
- Modify: `report/composition/graph/GraphGenerator.java` (`fixCoordProperties:2359`, next to the `setAbbreviate` calls at `:2423` and `:2814`, and for the outer facet coords it builds at `:4226`)
- Modify: `web/composer/vs/controller/FormatPainterService.java` (`fixAxisDefaultFormat:1885-1899` and its callers `:1828`, `:1856`)
- Test: `core/src/test/java/inetsoft/report/composition/graph/CompactDateRuleTest.java` (new, Spring), `ModernAxisLabelRuleTest.java`

**Interfaces:**
- Consumes: Task 3's `CompactDates`, `AxisSpec.setCompactDates`.
- Produces: `public final class CompactDateRule` with
  - `static int unit(int dateLevel)`: the `DateRangeRef` level as a `TimeScale` unit (`YEAR_INTERVAL` and `YEAR_OF_FULL_WEEK` → `YEAR`; quarter, month, week, day, hour, minute and second intervals and their full-week forms likewise), or `-1` for part levels
  - `static boolean inDateOrder(XDimensionRef dim)`: the order is exactly `SORT_ASC` or `SORT_DESC`, and `getRankingOption() == XCondition.NONE`
  - `static boolean hasAuthorFormat(CompositeTextFormat fmt)`: a non-empty format in the USER or CSS tier
  - `public static Format paneDefault(VizContext ctx, ChartRef ref, Format today)`: on a marked chart, for a date dimension whose unit has patterns, the full compact format; otherwise `today`

- [ ] **Step 1: Write the failing tests**
  - `CompactDateRuleTest`:
    - `unitsMapFromDateLevels`, plus `partLevelsHaveNoUnit` (`MONTH_OF_YEAR_PART` → −1).
    - `ascendingAndDescendingAreDateOrder`.
    - `byValueSpecificAndTopNAreNot`: `SORT_VALUE_DESC`, `SORT_SPECIFIC`, and `SORT_ASC` with `TOP_N`.
    - `thePaneShowsTheFullPatternOnAMarkedChart`: modern context, month dim → `MMM yyyy`; classic context → `today`.
  - `ModernAxisLabelRuleTest`:
    - `aMonthlyBarGetsCompactDates`: month-level date on X, marked → the X axis's `getCompactDates()` is `CompactDates(MONTH, locale)`.
    - `classicDatesGetNone`: unmarked → `null`.
    - `anAuthorsDateFormatKeepsTodaysLabels`: a USER format on the column's label format → `null`, and today's text spec format.
    - `aDateAxisOutOfOrderShowsTheYearOnEveryLabel`: `SORT_VALUE_DESC` → `getCompactDates()` is `null`, and the text spec's format pattern is `MMM yyyy`.
    - `hourlyDatesDropARepeatedDate`: hour level, marked → `isAbbreviate()` true, no compact dates (D84).
    - `aTimeSeriesAxisTakesItsTickUnit`: a time-series date, marked → `CompactDates(FROM_SCALE, locale)`.
    - `aDrilledLevelDrawsItsOwnPattern`: the runtime dimension at day level while the design dimension is at month level → `CompactDates(DAY, locale)`.
    - `facetDatesAreCompactToo`: Year then Month on X, marked → both axes carry compact dates for their unit (the year axis: none, since `YEAR` has no patterns).
    - `aDateComparisonChartGetsNoCompactDates`: spy `isAppliedDateComparison()` true → X axes `null`.

- [ ] **Step 2: Run them and see them fail.**

- [ ] **Step 3: Implement**
  - **`applyCompactDates(Scale scale, AxisSpec axis, CompositeTextFormat format, String[] fields)`** in `GraphGenerator`. It is called for every X and Y axis whose field is a date dimension, inner and outer, on a marked chart. It does nothing when `hasAuthorFormat(format)`, or for an X axis of a chart with date comparison applied.
    - The level is read from the runtime dimension (`info.getFieldByName(field, true)`), so a drill draws the drilled level.
    - On a `TimeScale`: `new CompactDates(FROM_SCALE, locale)`.
    - On a categorical date axis whose unit has patterns: `CompactDates(unit, locale)` when `inDateOrder`; otherwise the full format goes on the text spec (D87).
    - For hour, minute and second: `axis.setAbbreviate(true)` (D84).
    - The locale is `Catalog.getCatalog().getLocale()`, else `Locale.getDefault()`, as `XUtil.getDefaultDateFormat` uses.
  - **`fixAxisDefaultFormat`** takes a `VizContext` (callers pass `VizContext.of` of the chart assembly's info) and uses `CompactDateRule.paneDefault(ctx, ref, defFormat)` for the value it writes into the DEFAULT tier.

- [ ] **Step 4: Run the tests and see them pass**, plus `FormatPainterService*` tests and `GraphTargetLabelFormatTest` unchanged.

- [ ] **Step 5: Commit** — `Chart defaults S5, use compact dates on modern date axes and in the Format pane`

---

### Task 5: Whole-branch check and live-check handoff

- [ ] **Step 1:** Run the full `core` suite. Expected: 0 failures. The known timing flakes (`XSwapperGCThrottleTest`, `XSwapperStaleSwapFileMapTest`) pass when run alone.
- [ ] **Step 2:** Write the live-check handoff `E:/Temp/chart-defaults-s5a-checks-handoff.md` in the format of `E:/Temp/chart-defaults-s4b-checks-handoff.md`. It covers:
  - **§7's two S5 rows**, run on the base build `40f883e30b` first.
  - **The mock scenarios**, on a marked and a classic dashboard, in the composer, the viewer and PNG/PDF export: 8 products, 30 stores, 8 warehouses, 24 months, 105 weeks, 105 campaigns, 30 and 120 candles, 20 regions, and the 300×180 and 800×480 cards.
  - **A horizontal bar** with long category names.
  - **A facet**: Region by State.
  - **A time-series line and a Gantt**, and a Top N by-value monthly bar.
  - **The Format pane** on a marked and a classic monthly bar.
  - **A date-comparison chart.**
  - **A German-locale viewer**, to check the day-first patterns.
  - **The hard requirement**: classic tiles, XML, PNG and PDF are identical across `40f883e30b` and the head.
- [ ] **Step 3:** Leave the commit to the controller (handoff files live outside the repo).
