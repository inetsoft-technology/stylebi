# Chart Defaults S5b — Axis Titles, Radar Labels and Horizontal Order Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** On a modern chart, a generated axis title that is longer than its axis is hidden and gives its space to the plot. Point charts title a summed number field by the field name. Radar spoke names drop the default Sum and end in an ellipsis at the S5a limit. Radar scale labels stop piling up in the centre. Horizontal bars list the first category at the top. Classic charts are unchanged.

**Architecture:** Every item is a drawing rule (kind D). Nothing is stored, no XML changes, and no seed, reset slot or residue rule is involved.
- **Graph package.** New properties default to off and are set by the generator from `vizContext.modern` (M1):
  - `TitleSpec.hideWhenLonger`, read in `VGraph.layout0`
  - `AxisSpec.displayTexts`, read in `Axis.getValueLabels`
  - the S5a `AxisSpec.labelLimit`, now also read in `PolarAxis.layout`
  - `PolarCoord.spokeLabelRule`, resolved per spoke into `DefaultAxis.labelMode`
  - `IntervalElement.dodgeReversed`, read in `BarVO.dodge`
  - the existing `CategoricalScale.setReversed`
- **Generator.** `GraphGenerator.getTitleSpec` sets the title flag and the point-chart title text. `RadarGraphGenerator.fixParallelCoord` sets the spoke text, the limit and the spoke rule. `GraphGenerator.fixCoordProperties` and `createCoord` reverse the category scales of horizontal bars.

**Tech Stack:** Java 21, JUnit 5, Mockito, Spring test context (`@SreeHome`), Maven module `core`. No web change.

**Spec:** `docs/superpowers/specs/lookfeel/2026-11-06-chart-defaults-design.md` (S5 amended in `4c2048d3d2`). The relevant parts are:
- §3 M1 and M9
- §5 S5: the shaping facts, rows S5-3 to S5-7, "S5-6 radar scale labels", "S5-7 horizontal order"
- §9's S5-6 item, §11 "Labels (S5)"
- §10 D16, D73–D77, D85, D86

**Builds on:** S5a (`bug-80455-s5a` at `95c11537ba`): `AxisSpec.setLabelLimit/getLabelLimit`, `VLabel.setEllipsis`, `ModernLabelRule.limitPx(Font)` (package-private, `report/composition/graph`), `CompactDates` and `DefaultAxis.showCompactYears`.

**Verified against:** `bug-80455-s5b` at `95c11537ba`. Paths are under `core/src/main/java/inetsoft/` unless they start with `core/`.

## Corrections to the spec

| # | Spec | Code at `95c11537ba` | This plan |
|---|---|---|---|
| C1 | S5-3: "cut in `VLabel.getDisplayLabel`"; bounds in `layout0` | The plot is known only after `VGraph.fitCoord` (called at `VGraph.java:764`, defined at `:914`), which runs once; title sizes are reserved before it (`:746-755`). A scrolling chart lays out a second, expanded `VGraph` from `egraph2` (`VGraphPair.createEVGraph:934-965`) that supplies the plot and axis tiles, while titles draw from the real-size graph | The hide decision runs in `layout0` after `coord.setCoordBounds` (`:743`) and before the size calls (`:746`), against an estimated axis length. The expanded graph follows the real-size graph's decision instead of making its own. `getDisplayLabel` is unchanged |
| C2 | S5-4: the path is `getTitleSpec` → `getRefLabel` → `GraphUtil.getLabel` | The drawn text is the alias-map value (`xmeasurelabels`, `ymeasurelabels`, `GraphGenerator:3098-3236`). The map is keyed by `GraphUtil.getName(ref)` and looked up with `getRefLabel`; its value is `GraphUtil.getCaption(ref)`, which falls back to the `toView` string. Changing `getRefLabel` would make an author caption miss the map | The rule replaces the four alias lookups in `getTitleSpec` (`:6450`, `:6482`, `:6510`, `:6532`). `getRefLabel`, `GraphUtil.getLabel` and `getCaption` are untouched |
| C3 | S5-5: spoke names go through the alias `TextFrame`; the S5-1 limit cuts them | `PolarCoord.createAxis` replaces the spoke-name `DefaultAxis` with a `PolarAxis` (`PolarCoord.java:466-495`). The radar's radius leaves room for each spoke label's preferred width (`PolarCoord.getRadius` → `PolarUtil.getLabelPieRadius`, `PolarUtil.java:167-191`) before `PolarAxis.layout` sizes it (`PolarAxis.java:261-266`). S5a's limit lives in `DefaultAxis` only. Text placed in the alias frame would count as the author's alias, which D76 exempts | The generated text goes in a new `AxisSpec.displayTexts`. `PolarAxis.createVisuals` caps each non-aliased label's preferred width at the limit, so the radius and the layout both see the cut width |
| C4 | S5-6: "a new `AxisSpec` label mode carries it" | Spoke ranges are final only at plot time: `PolarCoord.init` resets and re-initialises every spoke scale (`:754-765`). The generator initialises a `OneVarParallelCoord` early but never a `ParallelCoord` | The generator sets a switch on `PolarCoord`; `PolarCoord.createAxis` resolves a mode per spoke into `DefaultAxis.labelMode` |
| C5 | S5-6 / D16: "the spoke nearest 12 o'clock" | Spoke i sits at `(i + 0.5) × 360° / n`, counter-clockwise from 3 o'clock (`AbstractParallelCoord.java:87`, `PolarCoord.java:423-429`), so the nearest is `round(n / 4 − 0.5)`, not spoke 0; n divisible by 4 gives a tie | Nearest by angle; a tie takes the lower index |
| C6 | S5-7: "Their Y category scales are reversed"; S4-4 and D63 "must agree with the flipped order" | A horizontal bar's category axis is the X scale of the transposed `RectCoord` (`DefaultGraphGenerator.java:356-377`); its Y scale is the measure. `clusterSize`, the slot-share floor and the re-pack about `clusterCenter` do not depend on order (`BarVO.java:875-904`, `:1679-1705`) | Reverse the transposed X scale and the outer facet Y scales. Only the placement loop of `BarVO.dodge` changes |
| C7 | S5-7: "Check the date-comparison `setReverseLines`" | `setupDateComparisonAxis` forces `setReverseLines(true)` because "bars are displayed from bottom up" (`GraphGenerator.java:2676-2680`), inside `if(multiLine)` | Lines follow the bars: `setReverseLines(reverseDcLines())`, where `reverseDcLines()` is `!topDownApplied` |
| C8 | S5-2 "the year on the first shown label", with S5-7 | Compact-date years and S5a's strict thinning walk in label-index order (`DefaultAxis:91`, `:100`, `showCompactYears`, `removeOverlappedLabels:1892`); on a reversed vertical axis index 0 is the bottom | On a reversed `CategoricalScale` both walk from the top (Task 6) |

**Design calls (§9 left these to the builder, or the spec is silent):**
- **S5-3 facets:** facet outer titles (x2 with outer dims, outer Y dims in y) are generated titles and hide too.
- **S5-3 rotation:** a title is checked only when its text runs along its axis, tested on the rotation modulo 180: X and X2 need 0, Y and Y2 need 90 (so −90 and 270 count). Any other rotation keeps today's behaviour; a non-multiple of 90 is never cut today.
- **S5-3 scrolling charts:** the expanded graph hides exactly the titles the real-size graph hid, so tiles and regions line up.
- **S5-4 mixed charts:** the point test is per measure (`isPoint(GraphTypeUtil.getChartType(measure, info))`). On a multi-style chart the point measure reads "Price" while a bar measure keeps "Sum(Quantity)".
- **S5-4 typed titles:** a typed title's `{0}` substitution keeps today's labels (D74: typed titles as today).
- **S5-5 one-measure radar:** its spokes are dimension values, so only the limit applies. An author alias on any spoke shows in full; "in full" means free of the 66px limit, while the pie-label width that cuts it today still applies.
- **S5-6 same scale:** same scale class, reversal, log base and power exponent; tick arrays of equal length within `ε = 1e-9 × max(1, |max − min|)`; and identical drawn label texts, so two measures formatted differently each keep their own labels. Only spokes whose labels are visible are compared, against the first visible spoke.
- **Author alias (S5-5):** a value is author-aliased when the spec's text frame's `getKeys()` contains `GTool.toString(value)`, the rule `DefaultAxis` already uses (`DefaultAxis.java:81-90`). `TextFrame.getText` returns the value itself when nothing is mapped, so it cannot tell.
- **S5-6 hidden top spoke:** when the author hid the top spoke's labels on an equal-scale radar, the labels go to the nearest spoke whose labels are visible (same tie rule). This shows no label the author hid.
- **S5-7 types:** every measure's runtime type is `CHART_BAR`, `CHART_BAR_STACK` or `CHART_INTERVAL` (`isGraphType`). 3D bar, funnel, waterfall, pareto, Gantt and mixed multi-style charts keep today's order (waterfall bridges need ascending positions, `IntervalElement.java:259`, `:389`). 3D bar is excluded because it draws on a `Rect25Coord`, with `Bar3DVO` and the paint order of `VGraph.sort3DBar`; S4's width clamp already excludes it (`GraphGenerator.java:5235-5240`).
- **S5-7 date comparison:** a value-only DC horizontal bar reverses like any other, and its label lines follow (C7).
- **S5-7 thinning:** on a reversed category axis with a label limit, thinning keeps the top label. This also applies to a marked Gantt's task axis. The reversed walks run only when the generator set a modern axis property (`getLabelLimit() > 0` or `getCompactDates() != null`), so a classic Gantt's task axis keeps today's walk.

**Known limitations:**
- **Older builds** draw today's titles, spokes and order (M9).
- **The title's axis length is an estimate** made before the plot is fitted. Near the threshold a title may hide where it would just fit, or the reverse (as D79).
- **The S5a rotation estimate always subtracts the Y title**, even when Task 1 later hides it. On a small card the X slot is then about 16px short, so a label near the threshold may turn to 45° or thin where it would just fit (within D79's accepted error).
- **A hidden title cannot be clicked** to format it in the composer, as with an author-hidden title today.
- **The wizard's histogram titles** ("Frequency" and the binned field) are written as title text, so they count as typed and are cut, not hidden.
- **A time-series date on Y** (`TimeScale`) cannot reverse; that horizontal bar stays bottom-up and gets no dodge flag.
- **Facets of more than `graph.subgraph.maxcount` (1000) panels** are truncated by `RectCoord.setMaxCount` (`:2140-2151`), which reverses twice. The same bug exists for the Gantt today.

## Global Constraints

- **Worktree:** `$WT` is the executor's worktree on branch `bug-80455-s5b`, at or after this plan's commit. Never work in `E:/StyleBI/stylebi-enterprise/community` or any other worktree.
- **Another session is running live checks** on a server built from a different worktree, using the shared Maven repository and the shared asset data. So:
  - Never run `mvnw install`, `mvnw package -pl server`, `spring-boot:run`, or anything else that writes outside `$WT`.
  - Never start a server or bind or call ports 8080, 8081 or 5005.
  - Run only the test classes a step names (Task 7 names packages). The full `core` suite runs only when the controller says the checks are finished.
- **Git:**
  - Each git command is its own call, `git -C "$WT" ...`, with no `&&` anywhere in the command.
  - One commit per task, subject `Chart defaults S5, <what changed>`, last line `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
  - Subagents never run `git add`, `git commit` or `git stash` (a hook blocks them); the controller stages and commits after review. Never push.
- **Scope is the mark (M1).** Every new graph-package property defaults to off, and off draws today's output. A classic chart is pixel-identical.
- **Values (all under the mark):**
  - **Spoke label limit:** S5a's `ModernLabelRule.limitPx(font)`, 66px at 10px, ellipsis "…" (U+2026).
  - **Point title drop (D75):** only when the formula is `AggregateFormula.SUM` and `AggregateFormula.getDefaultFormula(originalType) == SUM` (a number field), with no calculator, `getPercentageOption() == XConstants.PERCENTAGE_NONE`, aggregation enabled, a non-null data ref and no aggregate calc. The text is `Tool.localize(agg.getDataRef().toView())`.
  - **Point types:** `GraphTypes.isPoint` (`CHART_POINT`, `CHART_POINT_STACK`). Scatter contour and radar are not point.
  - **Spoke modes:** `DefaultAxis.LABELS_ALL = 0`, `LABELS_NONE = 1`, `LABELS_OUTERMOST = 2`.
- **Never write `AxisSpec.setLabelVisible(false)`** to hide radar labels: `PlotArea.addRadarAxesArea` (`PlotArea.java:3393`) builds the spoke's region from it.
- **Comments:** a short clause each, with no ticket, PR, slice or design-doc names.
- **New files** start with the AGPL header of `SeedChromeDefaultsTest.java`, `Copyright (C) 2026  InetSoft Technology`.
- **Tests that reach Spring beans** use `@ExtendWith(SpringExtension.class)`, `@ContextConfiguration(classes = BaseTestConfiguration.class, initializers = ConfigurationContextInitializer.class)`, `@SreeHome`, `@Tag("core")`, and clear `viewsheet.modernVisualization`, `viewsheet.darkMode` and `viewsheet.density` in `@AfterEach`.

## Review Focus

1. **Classic charts are untouched**: titles cut with "..", every radar spoke's labels, spoke names, bottom-up horizontal bars and series. Pinned by the `classic…` cases in every task.
2. **A typed title on a marked chart is cut, never hidden, and its `{0}` keeps "Sum(Price)".** Pinned by `aTypedTitleIsCutAsToday` (Task 1) and `aTypedTitlePatternKeepsTodaysText` (Task 2).
3. **A Gantt is reversed once and gets no dodge flag; waterfall, funnel, 3D bar, pareto and horizontal point, line and box plot keep today's order.** Pinned by `aMarkedGanttIsReversedOnceWithoutTheFlag` and `otherHorizontalChartsKeepTodaysOrder` (Task 5).
4. **A hidden title leaves no title area and no exception** in `ChartArea`, `TitleArea` and `GraphBounds`, with and without a facet border. Pinned by `aHiddenTitleLeavesNoTitleArea` (Task 1).
5. **Radar spoke regions survive the scale rule, and an author-hidden spoke stays hidden.** Pinned by `theRuleLeavesTheSpecAlone` and `anAuthorHiddenSpokeStaysHidden` (Task 4).

## File Structure

- **Task 1:** `graph/TitleSpec.java` (flag), `graph/VGraph.java` (`createTitle`, `layout0`), `report/composition/graph/GraphGenerator.java` (`getTitleSpec`).
- **Task 2:** `report/composition/graph/ModernLabelRule.java` (`dropsDefaultSum`, `fieldText`), `GraphGenerator.java` (`getTitleSpec`).
- **Task 3:** `graph/AxisSpec.java` (`displayTexts`), `graph/guide/axis/Axis.java` (`getValueLabels`), `graph/guide/axis/PolarAxis.java` (`layout`), `report/composition/graph/RadarGraphGenerator.java` (`fixParallelCoord`).
- **Task 4:** `graph/guide/axis/DefaultAxis.java` (label mode), `graph/coord/PolarCoord.java` (spoke rule), `RadarGraphGenerator.java`.
- **Task 5:** `GraphGenerator.java` (`isTopDownBars`, `fixCoordProperties`, `createCoord`, `createEGraph`, `setupDateComparisonAxis`), `graph/element/IntervalElement.java` (`dodgeReversed`), `graph/visual/BarVO.java` (`dodge`).
- **Task 6:** `graph/guide/axis/DefaultAxis.java` (reading order on a reversed category axis).
- **Task 7:** none (whole-branch check and live-check handoff).

**Test harnesses:**
- **Graph-level tests** build an `EGraph` and lay it out as `core/src/test/java/inetsoft/graph/guide/axis/ModernAxisLabelTest.java` and `core/src/test/java/inetsoft/graph/visual/BarVOWidthClampTest.java` do (`Plotter.getPlotter(graph).plotAndLayout(new DefaultDataSet(rows), 0, 0, w, h)`). A hidden label has `getZIndex() < 0`. Graph space is y-up: a greater `getCenterY()` is higher.
- **Generator tests** copy `core/src/test/java/inetsoft/report/composition/graph/ModernAxisLabelRuleTest.java`'s annotations and `generator(chart, mark, size)` (`VizMark.MODERN_LIGHT` against `null`), and `ModernMarkRulesTest`'s radar binding (`radarElement`).

## How to run tests

From `$WT`:

```bash
./mvnw -q test -pl core "-Dtest=ClassA,ClassB" -Dsurefire.failIfNoSpecifiedTests=false
```

- Read `core/target/surefire-reports/*.txt` for counts.
- Run every new or changed class alone once as well (CLAUDE.md).
- If compilation fails with "Attempt to recreate … HeartBeatServiceProxy", delete the untracked `core/target/generated-test-sources`.

---

### Task 1: Hide a generated axis title that is longer than its axis (S5-3, D74)

**Files:**
- Modify: `graph/TitleSpec.java` (field, accessors, `equals`)
- Modify: `graph/VGraph.java` (`createTitle:328-364`, `layout0` between `:743` and `:746`)
- Modify: `report/composition/graph/VGraphPair.java` (`createEVGraph:934-965`, between `plotter2.plot(...)` and `evgraph.layout(...)`)
- Modify: `report/composition/graph/GraphGenerator.java` (`getTitleSpec`, the generated branch `if(titleOK && …)`, about `:6564`)
- Test: `core/src/test/java/inetsoft/graph/VGraphTitleHideTest.java` (new, no Spring), `core/src/test/java/inetsoft/report/composition/graph/ModernAxisTitleTest.java` (new, Spring)

**Interfaces:**
- Produces:
  - `TitleSpec.setHideWhenLonger(boolean)` / `boolean isHideWhenLonger()`: default false. When true, `VGraph` drops the title for that layout if its text is longer than its axis. No `@TernMethod` (not script-visible).
  - `VGraph.getDroppedTitles()` → `Set<String>` of `"x"`, `"x2"`, `"y"`, `"y2"` hidden by the last layout; `VGraph.followTitles(VGraph source)`: this graph hides exactly the source's dropped titles at its next layout and makes no estimate of its own. Both `@hidden`.

- [ ] **Step 1: Write the failing graph tests** (`VGraphTitleHideTest`; a bar `EGraph` with a 60-character X or Y title spec)
  - `aLongGeneratedXTitleIsHidden`: flag on, 300×200 → `vgraph.getXTitle()` is null, and the plot height equals the same graph's with no X title.
  - `aTitleThatFitsStays`: flag on, 1200×400 → `getXTitle()` not null, its display label is the full text.
  - `aLongYTitleIsHiddenAndThePlotWidens`: Y title at rotation 90, flag on, 400×150 → `getYTitle()` null, plot width equals the no-title graph's.
  - `aYTitleAtMinus90CountsAsAlongItsAxis`: the same with rotation −90 → hidden.
  - `withoutTheFlagTheTitleIsCut`: flag off, 300×200 → the X title is kept and its display label ends in "..".
  - `aTitleNotAlongItsAxisIsNeverHidden`: X title at rotation 45, flag on, 300×200 → kept.
  - `aHiddenTitleReturnsAtALargerLayout`: `Plotter.getPlotter(graph).plotAndLayout(...)` twice on one `EGraph`, at 300 and at 1200 wide → hidden, then shown.
  - `aFollowingGraphHidesTheSourcesTitles`: a source laid out at 300 wide hides the X title; a second graph of the same `EGraph` that `followTitles(source)` and is laid out at 1200 wide → its X title is hidden too; following a source that hid nothing → shown.
  - `theFlagTakesPartInEquality`: two `TitleSpec`s differing only in the flag are not equal.

- [ ] **Step 2: Write the failing generator tests** (`ModernAxisTitleTest`, bar chart, 400×240)
  - `aGeneratedTitleHidesWhenLongerOnAMarkedChart`: marked → `graph.getYTitleSpec().isHideWhenLonger()` and the X one are true.
  - `aTypedTitleIsCutAsToday`: Y title descriptor text "Revenue by region" → false.
  - `classicTitlesNeverHide`: unmarked → false.
  - `anAliasTitleIsGenerated`: a measure caption "Revenue" (from the alias map) → true.
  - `aHiddenTitleLeavesNoTitleArea`: marked vertical bar whose 60-character measure name is the Y title, at 300×180; plot the generated `EGraph` and build `new ChartArea(vgraph, egraph, data, null, info)` (`ChartArea.java:131`) → `getYTitleArea()` is null, no exception; and with a facet (Region outer on X) → no exception.

- [ ] **Step 3: Run them and see them fail**

Run: `./mvnw -q test -pl core "-Dtest=VGraphTitleHideTest,ModernAxisTitleTest" -Dsurefire.failIfNoSpecifiedTests=false`
Expected: compile failure on `setHideWhenLonger`; after a stub, the hide cases fail.

- [ ] **Step 4: Implement**
  - **`getTitleSpec`:** inside the generated branch, after `setLabel`, `tSpec.setHideWhenLonger(vizContext.modern)`. The typed branch is unchanged.
  - **`createTitle`:** remember, per title, the `VLabel` it made and the spec's flag.
  - **`layout0`, new private `dropLongTitles(double contentw, double contenth)`**, called after `coord.setCoordBounds(...)` and before the `SizeManager` calls. It first restores every title `createTitle` made. It returns at once when no title is flagged and the graph follows no source, so a classic layout cannot change (M1). Otherwise:
    - A following graph drops exactly its source's `getDroppedTitles()` and makes no estimate.
    - `xLen = contentw − getLeftAxisPreferredWidth() − getRightAxisPreferredWidth() − the preferred widths of the Y and Y2 titles present`
    - X and X2 are decided against `xLen` first; then `yLen = contenth − getBottomAxisPreferredHeight() − getTopAxisPreferredHeight() − the preferred heights of the X and X2 titles still present`, and Y and Y2 against it.
    - The text length is uncapped: `VLabel.getPreferredWidth(String...)` / `getPreferredHeight(String...)` of the title's lines (`getPreferredWidth()` is capped at a third of the graph).
    - A flagged title whose length exceeds its estimate, and whose text runs along its axis, is set to null for this layout and recorded in the dropped set. A null title already counts as 0 in `SizeManager`, and the bounds code is null-guarded.
  - **`VGraphPair.createEVGraph`:** call `evgraph.followTitles(vgraph)` before `evgraph.layout(...)`.

- [ ] **Step 5: Run the tests and see them pass**, plus `ChartChromeRenderTest`, `ModernMarkRulesTest` and `ModernAxisLabelRuleTest` unchanged.

- [ ] **Step 6: Commit** — `Chart defaults S5, hide a generated axis title longer than its axis`

---

### Task 2: Point axis titles drop the default formula (S5-4, D75)

**Files:**
- Modify: `report/composition/graph/ModernLabelRule.java`
- Modify: `report/composition/graph/GraphGenerator.java` (`getTitleSpec`, the four measure lookups `:6450`, `:6482`, `:6510-6511`, `:6532`)
- Test: `core/src/test/java/inetsoft/report/composition/graph/ModernAxisTitleTest.java`

**Interfaces:**
- Produces (package-private, static, reused by Task 3):
  - `boolean ModernLabelRule.dropsDefaultSum(ChartAggregateRef agg)`: the Global Constraints' drop test; false for null.
  - `String ModernLabelRule.fieldText(ChartAggregateRef agg)`: `Tool.localize(agg.getDataRef().toView())`.

- [ ] **Step 1: Write the failing tests** (`ModernAxisTitleTest`, marked point chart, X `Sum(Quantity)`, Y `Sum(Price)`, both number fields: the test refs need a number data type on their `BaseField`, because `getOriginalDataType()` reads `ref.getDataType()`)
  - `aPointChartDropsSumOnANumber`: Y title "Price", X title "Quantity".
  - `anAutoChartResolvedToPointDropsSum`: `CHART_AUTO` with a measure on X and on Y, marked → "Price" and "Quantity" (resolved type, §11).
  - `otherFormulasStay`: Y `Avg(Price)`, `Max(Price)`, and `Count(Order)` on a string field → unchanged.
  - `twoColumnPercentageAndCalculatorStay`: `Correlation(Price, Quantity)`; `Sum(Price)` with `PERCENTAGE_OF_GRANDTOTAL`; `Sum(Price)` with a running-total calculator → unchanged.
  - `anAuthorCaptionWins`: caption "Revenue" on `Sum(Price)` → "Revenue".
  - `onlyPointMeasuresDrop`: a marked bar chart → "Sum(Price)"; `CHART_POINT_STACK` → "Price"; a multi-style chart with a point `Sum(Price)` and a bar `Sum(Quantity)` on Y → the title holds "Price" and "Sum(Quantity)".
  - `aTypedTitlePatternKeepsTodaysText`: Y title text "By {0}" → "By Sum(Price)".
  - `classicPointChartKeepsTheFormula`: unmarked → "Sum(Price)".

- [ ] **Step 2: Run them and see them fail**

Run: `./mvnw -q test -pl core "-Dtest=ModernAxisTitleTest" -Dsurefire.failIfNoSpecifiedTests=false`

- [ ] **Step 3: Implement**
  - **`getTitleSpec`:** build a second list beside `labels` with the same entries, except that each measure entry comes from a new private `String generatedMeasureTitle(String measure, String refLabel, Map<String, String> aliases)`:
    - the alias-map value, or `refLabel` when the map has none (today's text);
    - replaced by `ModernLabelRule.fieldText(agg)` when `vizContext.modern`, the measure's own runtime type `isPoint`, the ref has no caption (`!(agg instanceof VSAggregateRef v) || v.getCaption() == null`; `ChartAggregateRef` has no `getCaption`), and `dropsDefaultSum(agg)`.
  - The generated branch joins the second list; the typed branch's `MessageFormat` keeps `labels`. `isHiddenTitleLabel` still tests `refLabel`.

- [ ] **Step 4: Run the tests and see them pass**, plus Task 1's tests.

- [ ] **Step 5: Commit** — `Chart defaults S5, title a point chart's summed number field by its name`

---

### Task 3: Radar spoke names, the S5-4 text and the limit (S5-5, D76)

**Files:**
- Modify: `graph/AxisSpec.java` (field, accessors, `clone`, `equals`, `hashCode`)
- Modify: `graph/guide/axis/Axis.java` (`getValueLabels:494`)
- Modify: `graph/guide/axis/PolarAxis.java` (`createVisuals:184-212`)
- Modify: `report/composition/graph/RadarGraphGenerator.java` (`fixParallelCoord:169-212`)
- Test: `core/src/test/java/inetsoft/graph/guide/axis/RadarSpokeLabelTest.java` (new, no Spring), `core/src/test/java/inetsoft/report/composition/graph/ModernRadarLabelTest.java` (new, Spring)

**Interfaces:**
- Consumes: Task 2's `dropsDefaultSum`, `fieldText`; S5a's `AxisSpec.setLabelLimit`, `VLabel.setEllipsis`, `ModernLabelRule.limitPx`.
- Produces:
  - `AxisSpec.setDisplayTexts(Map<Object, String>)` / `Map<Object, String> getDisplayTexts()`: default null. The text drawn for a value the author's text frame does not map; the value itself (the key for aliases, formats and selection) is unchanged.
  - `PolarAxis.createVisuals`: when `getLabelLimit() > 0`, a label whose value is not author-aliased (design call) gets `setEllipsis(true)` and `setMaxSize(new DimensionD(limit, Double.MAX_VALUE))`. `VLabel.getPreferredWidth0` already returns `min(maxSize.width, text width)`, so the radius (`PolarUtil.getLabelPieRadius`) and `PolarAxis.layout` (`getPieLabelWidth`) both see the cut width; `layout` needs no change.

- [ ] **Step 1: Write the failing graph tests** (`RadarSpokeLabelTest`; `PolarCoord` over a `ParallelCoord` of three measures, one named with 30 characters, 400×400)
  - `aLongSpokeNameEndsInAnEllipsisAtTheLimit`: limit 66 → that label's display text ends in "…", width ≤ 66.
  - `aCutSpokeNameGivesTheRadarItsRoom`: at limit 66, the laid-out `PolarAxis.getWidth()` with a 30-character spoke name equals the width with a 10-character one, and is larger than with no limit.
  - `anAuthorAliasShowsInFull`: limit 66 and an author `TextFrame` mapping that value to a 30-character alias → the alias, uncut by the limit.
  - `displayTextsChangeOnlyTheText`: `displayTexts` maps "Sum(Price)" → "Price" → the label reads "Price", and its `VDimensionLabel.getValue()` is "Sum(Price)".
  - `anAuthorAliasBeatsTheDisplayText`: both set for one value → the author's alias.
  - `classicSpokeNamesUnchanged`: no limit, no display texts → today's text and width.

- [ ] **Step 2: Write the failing generator tests** (`ModernRadarLabelTest`, marked radar of `Sum(Price)`, `Avg(Quantity)`, `Sum(Discount)`)
  - `aMarkedRadarDropsSumOnItsSpokes`: the label axis spec's `getDisplayTexts()` is `{Sum(Price)=Price, Sum(Discount)=Discount}`, and `getLabelLimit()` is 66.
  - `anAuthorSpokeAliasGetsNoDisplayText`: an alias on `Sum(Price)` in the label axis descriptor → no entry for it.
  - `aFilledRadarDoesTheSame`: `CHART_FILL_RADAR` → as above.
  - `aOneMeasureRadarTakesTheLimitOnly`: one measure by a dimension → limit 66, display texts null.
  - `truncateOffKeepsTodaysCut`: the label descriptor's truncate off → limit 0.
  - `classicRadarUnchanged`: unmarked → display texts null, limit 0.

- [ ] **Step 3: Run them and see them fail.**

- [ ] **Step 4: Implement**
  - **`Axis.getValueLabels`:** an author-aliased value (design call) keeps the frame's text; otherwise its `displayTexts` entry, if any; otherwise today's text.
  - **`PolarAxis.createVisuals`:** apply the cap as in Produces. An author-aliased value keeps today's pie-label width.
  - **`RadarGraphGenerator.fixParallelCoord`**, under `vizContext.modern`:
    - when `xdesc.isTruncate()`, `spec.setLabelLimit(ModernLabelRule.limitPx(spec.getTextSpec().getFont()))`, after the text spec is set, as S5a's `setModernRotation` reads `spec.getFont()`;
    - on a radar of several measures (`GraphTypes.isRadarN(info)`), for each label-scale value `v` with `xdesc.getLabelAlias(v) == null` whose `ChartAggregateRef` (`info.getFieldByName(v, true)`) passes `dropsDefaultSum`, map `v` to `fieldText(agg)`; set the map only when it is not empty.

- [ ] **Step 5: Run the tests and see them pass**, plus `ModernMarkRulesTest`.

- [ ] **Step 6: Commit** — `Chart defaults S5, shorten and cut modern radar spoke names`

---

### Task 4: Radar scale labels on one spoke, or the outermost only (S5-6, D73, D16)

**Files:**
- Modify: `graph/guide/axis/DefaultAxis.java` (mode field; the min/max tick hide in `layout`, about `:1216-1223`)
- Modify: `graph/coord/PolarCoord.java` (field, accessors, `equalsContent:1088-1096`; `createAxis` after the min-tick block `:517-526`)
- Modify: `report/composition/graph/RadarGraphGenerator.java` (where the `PolarCoord` is made, `:160`)
- Test: `core/src/test/java/inetsoft/graph/coord/RadarScaleLabelTest.java` (new, no Spring), `ModernRadarLabelTest.java`

**Interfaces:**
- Produces:
  - `DefaultAxis.LABELS_ALL = 0`, `LABELS_NONE = 1`, `LABELS_OUTERMOST = 2`; `DefaultAxis.setLabelMode(int)` / `int getLabelMode()`, default `LABELS_ALL`. `NONE` hides every label and `OUTERMOST` every label but the last index (the rim, also on a reversed spoke), at layout, as zIndex −1. It never shows a label.
  - `PolarCoord.setSpokeLabelRule(boolean)` / `boolean isSpokeLabelRule()`: default false; marked `@hidden`, no `@TernMethod`.

- [ ] **Step 1: Write the failing graph tests** (`RadarScaleLabelTest`; `PolarCoord` over `ParallelCoord` or `OneVarParallelCoord`, rule on, 400×400; collect each spoke's shown label indices after layout from `PolarCoord`'s vertical axes)
  - `equalScalesShowLabelsOnTheTopSpokeOnly`: 6 spokes, each scale 0–100 → only spoke 1 shows labels (every index but 0, as today); the others none.
  - `aOneVarRadarAlwaysQualifies`: one measure over 5 categories → only spoke 1 shows labels.
  - `theSpokeNearestTwelve`: n = 3, 4, 5, 6, 7, 8 → top spoke 0, 0, 1, 1, 1, 1.
  - `differentScalesShowOnlyTheOutermostLabel`: ranges 0–10, 0–1000, 0–50 → each spoke shows only its last index, the value `getTicks()[last]`.
  - `aReversedSpokeShowsItsSmallestValueAtTheRim`: one spoke reversed → its only shown label is its minimum.
  - `differentFormatsAreDifferentScales`: equal ranges, one spoke formatted as percent → outermost on each.
  - `logAndLinearDoNotMatch`: equal min and max, `LogScale` and `LinearScale` → outermost on each.
  - `anAuthorHiddenSpokeStaysHidden`: one spoke's spec `setLabelVisible(false)` → it shows nothing, equal scales or not; when that spoke is the top one on equal scales, the labels go to the next nearest spoke.
  - `theRuleLeavesTheSpecAlone`: tick and line visibility unchanged and every spoke's `AxisSpec.isLabelVisible()` still true.
  - `classicRadarUnchanged`: rule off → every spoke shows every index but 0.

- [ ] **Step 2: Write the failing generator tests** (`ModernRadarLabelTest`)
  - `aMarkedRadarTurnsOnTheSpokeRule`: `isSpokeLabelRule()` true for the three-measure and the one-measure radars; false unmarked.
  - `authorMinAndMaxMakeSpokesShareLabels`: min 0 and max 100 on each measure's axis descriptor, plot the generated graph → only the top spoke shows labels.

- [ ] **Step 3: Run them and see them fail.**

- [ ] **Step 4: Implement**
  - **`DefaultAxis.layout`:** extend the min/max tick hide with the mode.
  - **`PolarCoord.createAxis`:** record each vertical axis's `theta`. When `spokeLabelRule`, the base coord is an `AbstractParallelCoord`, there is more than one spoke and `type != PLUS`:
    - `same` = the design call's "same scale" test of every spoke whose labels are visible against the first such spoke; labels compared as each axis's drawn texts;
    - `top` = the spoke whose `theta` is nearest π/2, ties to the lower index, skipping spokes whose labels are not visible;
    - each spoke's mode: `same ? (k == top ? LABELS_ALL : LABELS_NONE) : LABELS_OUTERMOST`.
  - **`RadarGraphGenerator`:** `setSpokeLabelRule(vizContext.modern)` on the radar's `PolarCoord` (radar and filled radar).

- [ ] **Step 5: Run the tests and see them pass**, plus Task 3's tests and `ModernMarkRulesTest`.

- [ ] **Step 6: Commit** — `Chart defaults S5, show modern radar scale labels without the pile-up`

---

### Task 5: Horizontal bars list the first category at the top (S5-7, D77, D85, D86)

**Files:**
- Modify: `report/composition/graph/GraphGenerator.java` (new predicate; `createEGraph:911`; the X branch of `fixCoordProperties`, beside S5a's `isTransposedCategories` use, about `:2416`; `createCoord:4227`; `setupDateComparisonAxis:2680`)
- Modify: `graph/element/IntervalElement.java` (field, accessors, `equalsContent:827-843`)
- Modify: `graph/visual/BarVO.java` (`dodge`, the placement loop `:878-904`)
- Test: `core/src/test/java/inetsoft/report/composition/graph/ModernTopDownOrderTest.java` (new, Spring), `core/src/test/java/inetsoft/graph/visual/BarVODodgeOrderTest.java` (new, no Spring)

**Interfaces:**
- Produces:
  - `boolean GraphGenerator.isTopDownBars()` (package-private): `vizContext.modern && !fake && !xmeasures.isEmpty() && ymeasures.isEmpty() && !ydims.isEmpty() && isGraphType(CHART_BAR, CHART_BAR_STACK, CHART_INTERVAL)`.
  - `boolean GraphGenerator.reverseDcLines()` (package-private): `!topDownApplied`; whether date-comparison label lines read bottom-up.
  - `IntervalElement.setDodgeReversed(boolean)` / `boolean isDodgeReversed()`: default false; places the series of each dodged cluster in reverse order.

- [ ] **Step 1: Write the failing graph tests** (`BarVODodgeOrderTest`, the `BarVOWidthClampTest` harness, transposed coord)
  - `aReversedScaleListsTheFirstCategoryAtTheTop`: `CategoricalScale` reversed → the first category's bar has the greatest `getCenterY()`.
  - `aReversedDodgePutsTheFirstSeriesOnTop`: a two-series cluster with `setDodgeReversed(true)` → in each category the first series' `getCenterY()` is greater; flag off → the reverse (today).
  - `aReversedDodgeKeepsTheClusterTouchingAndCentred`: the crowded-cluster case of `BarVOWidthClampTest` with the flag → same thickness, touching, centred on the slot.
  - `aReversedDodgeKeepsBrushedBarsInsideTheirBase`: the brushed case of `BarVOWidthClampTest` with the flag.
  - `aStackKeepsItsSegmentOrder`: stacked, transposed, reversed scale → in each bar the first series' segment starts at the base.
  - `theFlagOffChangesNothing`: shapes equal today's.

- [ ] **Step 2: Write the failing generator tests** (`ModernTopDownOrderTest`, Y Store, X Total, 400×240)
  - `aMarkedHorizontalBarReversesItsCategories`: the coord's X scale is a reversed `CategoricalScale`, and every `IntervalElement` has `isDodgeReversed()`.
  - `anAscendingSortPutsAAtTheTop` and `aDescendingTopNPutsTheLargestAtTheTop`: plot the generated graph → the bar of the sort's first value has the greatest `getCenterY()`.
  - `aStackedBarAndAnIntervalReverse`: `CHART_BAR_STACK` with a colour dimension, and `CHART_INTERVAL` → reversed.
  - `aClassicHorizontalBarKeepsItsOrder`, `aMarkedVerticalBarKeepsItsOrder`: not reversed, no flag.
  - `otherHorizontalChartsKeepTodaysOrder` (D85): point, line, box plot, 3D bar, waterfall, pareto, funnel, and a bar plus line multi-style chart, measure on X → not reversed, no flag.
  - `aMarkedGanttIsReversedOnceWithoutTheFlag`: tasks reversed, no `IntervalElement` flagged.
  - `facetOuterYDimensionsReverse`: Y Region, Store → both scales reversed; classic → neither. `facetOuterXDimensionsDoNot`: X Region, Total; Y Store → Region not reversed, Store reversed.
  - `aSeparatedChartReversesItsSharedScale`: two separated bar measures on X → every inner coord's X scale is the same reversed object.
  - `aBrushedChartFlagsEveryBar`: with brushing → every `IntervalElement`, the all-data ones included, has the flag.
  - `aClusteredBarsLegendKeepsItsOrder`: a marked clustered horizontal bar (colour dimension P0, P1) → the legend lists P0 first, and after plotting P0's bar is the top one in each cluster (D86, "in the legend's order").
  - `aTimeSeriesYDateStaysBottomUp`: the inner X is a `TimeScale` → no flag.
  - `dcLinesFollowTheBars`: generate a marked and a classic horizontal bar with `createEGraph()` → `reverseDcLines()` false when marked, true when classic; and a marked horizontal bar on a `TimeScale` → true. (A spied date comparison cannot reach `setupDateComparisonAxis` in this harness: its `dateComparison` is null. The live check covers the drawn lines.)
  - `theTopLabelRegionIsTheFirstCategory`: plot a marked horizontal bar and build `new ChartArea(vgraph, egraph, data, null, info)` → the topmost Y label area's `VDimensionLabel.getValue()` is the sort's first category (regions follow the scale).

- [ ] **Step 3: Run them and see them fail.**

- [ ] **Step 4: Implement**
  - **`createEGraph`:** reset a private `topDownApplied` to false before `createEGraph0()`. After it, when `topDownApplied`, call `setDodgeReversed(true)` on every `IntervalElement` of `graph`.
  - **`fixCoordProperties`, X branch:** when `isTopDownBars() && isTransposedCategories(rect, xscale)`, `((CategoricalScale) xscale).setReversed(true)` and set `topDownApplied`. Independent of rotation and of S5a's label limit.
  - **`createCoord`:** reverse an outer Y `CategoricalScale` when `isGantt(chartType) || topDownApplied`.
  - **`setupDateComparisonAxis`:** `setReverseLines(reverseDcLines())`. The reversal runs earlier in the same `fixCoordProperties` call (`:2421` against `:2430`), so `topDownApplied` is already set.
  - **`BarVO.dodge`:** when the element is an `IntervalElement` with `isDodgeReversed()`, place `stacks` in reverse order (reverse a copy before the loop). Centring, `clusterSize`, `clusterCenter` and the brushing overlay copy are unchanged.

- [ ] **Step 5: Run the tests and see them pass**, plus `BarVOWidthClampTest`, `ModernMarkRulesTest` and `ModernAxisLabelRuleTest`.

- [ ] **Step 6: Commit** — `Chart defaults S5, list a modern horizontal bar's first category at the top`

---

### Task 6: Reading order on a reversed category axis (S5-7 with S5-2 and S5-1)

**When:** after S5a's live-check fixes land on `bug-80455-s5a` and this branch is rebased onto them. Amended 2026-11-16 after the first fix landed (`0f639fe0fa`, D91, D92): every axis with a label limit, flat or stacked down the chart, now thins in `DefaultAxis.thinInOneStep`, which shows every k-th label from the first that fits and makes the year text part of the step choice; the old `removeOverlappedLabels` anchor and `showCompactYears` now run only on axes without a limit. If more S5a fixes land, rebase again first.

**Files:**
- Modify: `graph/guide/axis/DefaultAxis.java`:
  - `createVisuals`, the label-creation walk (`prevLabel` at about `:104`, `compactTexts`);
  - `thinInOneStep` (about `:1649`): its candidate order and its "first that fits";
  - `showCompactYears` and `updateAnchor`, only where they run on a reversed modern axis.
- Test: `core/src/test/java/inetsoft/graph/guide/axis/ModernAxisLabelTest.java`

**Interfaces:**
- Consumes: S5a's `CompactDates`, `thinInOneStep`, `showCompactYears`, `getLabelLimit`.
- Produces: on an axis whose scale is a reversed `CategoricalScale` and that carries a modern property (`getLabelLimit() > 0` or `getCompactDates() != null`), every walk reads in date order, from the last index (the top of a transposed axis):
  - the compact-date year and the hour-level repeated date set when labels are created;
  - `thinInOneStep`'s step: it starts at the topmost label that fits, and its year texts are judged top-down.

  Every other axis is unchanged, the classic Gantt's task axis included.

- [ ] **Step 1: Write the failing tests** (`ModernAxisLabelTest`, transposed coord, `CategoricalScale` reversed)
  - `aReversedMonthlyAxisPutsTheYearOnTheTopLabel`: Jan 2025 … Dec 2026 with `CompactDates(MONTH, US)`, tall enough not to thin → the top label is "Jan 2025", then "Feb" … "Dec", "Jan 2026".
  - `aReversedAxisMovesTheYearAfterThinning`: the same, short enough to thin → the topmost shown label of each year carries its year.
  - `aReversedLimitedAxisStepsFromItsFirstCategory`: 30 stores, limit 66, thinning to a step of 2 → the step runs from the first store at the top; the first store is shown, or (D93) hidden when, slid inside the axis, it crowds the next label in step.
  - `aReversedHourlyAxisCarriesTheDateFromTheTop`: hourly values over two days, abbreviation on, limit 66, reversed → the top label carries its date and the first label of the second day carries its date; the others drop it.
  - `aReversedClassicAxisUnchanged`: no limit, no compact dates → the same shown labels as before the change.

- [ ] **Step 2: Run them and see them fail.**

- [ ] **Step 3: Implement** the reversed walks named in Produces.
  - Prefer one reading-order switch in the walks over a parallel copy of `thinInOneStep`; for example, order `cands` and the year comparison by reading order.
  - `updateAnchor` moves a hidden anchor's date toward higher indices only; a reversed walk moves it toward lower indices.

- [ ] **Step 4: Run the tests and see them pass**, plus `CompactDatesTest`, `ModernAxisLabelRuleTest`, `ModernTopDownOrderTest`, and S5a's one-step thinning cases in `ModernAxisLabelTest`, which must stay green on non-reversed axes.

- [ ] **Step 5: Commit** — `Chart defaults S5, read a reversed category axis from the top`

---

### Task 7: Whole-branch check and live-check handoff

- [ ] **Step 1:** Run the packages the branch touches: `inetsoft.graph.**`, `inetsoft.report.composition.graph.**` and `inetsoft.report.composition.region.**`, with `-Dtest='inetsoft.graph.**.*Test,inetsoft.report.composition.graph.**.*Test,inetsoft.report.composition.region.**.*Test'`. Expected: 0 failures.
- [ ] **Step 2:** The full `core` suite runs when the controller reports the S5a checks finished. Expected: 0 failures; the known timing flakes (`XSwapperGCThrottleTest`, `XSwapperStaleSwapFileMapTest`) pass when run alone.
- [ ] **Step 3:** Write the live-check handoff `E:/Temp/chart-defaults-s5b-checks-handoff.md` in the format of `E:/Temp/chart-defaults-s5a-checks-handoff.md`. Base build `95c11537ba`. It covers:
  - **S5-3:** a marked bar whose measure title is longer than a 300×180 card (hidden, plot wider), the same at 800×480 (shown); a typed long title (cut); a facet outer title; the composer's title region after a hide; a marked vertical bar of 100 products on a 300×180 card with a long X title, which scrolls horizontally (title hidden, plot and axis tiles and composer regions line up).
  - **S5-4:** a marked scatter of `Sum` measures, one `Avg`, one with a caption; a multi-style point and bar.
  - **S5-5 and S5-6:** a three-measure radar with a long measure name, with an author alias, equal author min and max, different ranges, a one-measure radar, a filled radar; spoke regions selectable and formattable.
  - **S5-7:** horizontal bar ascending, descending Top N, stacked, clustered (first series on top, legend order unchanged), interval, faceted by Region, a date-comparison horizontal bar, a monthly date on Y with compact dates; brushing, selection, drill and the vertical scroll position; a horizontal waterfall, Gantt and point chart unchanged.
  - **Composer, viewer, PNG and PDF**, light and dark.
  - **The hard requirement:** classic tiles, XML, PNG and PDF identical across `95c11537ba` and the head.
- [ ] **Step 4:** Leave the commit to the controller (the handoff lives outside the repo).
