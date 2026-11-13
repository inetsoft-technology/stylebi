# Chart Defaults S4b — The Other Marks Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** On a modern chart, draw the remaining marks as the bundle draws them:
- bubbles at most 20px across, relation edges at most 6px, Gantt milestones 10px, radar lines 2px
- tree nodes with a 0.08 corner
- a filled radar at 30% fill with a solid outline
- candle bodies at least 3px, stock ticks at least 3px each side
- no pixel floor (candle, stock, box plot) that makes marks overlap in a crowded slot

Classic charts stay pixel-identical and keep their XML. A tree saved by a released build keeps its square nodes.

**Architecture:**
- **Sizes are drawing rules (D44).** The bubble, edge, milestone and radar sizes are modern branches in `GraphGenerator.fixSizeFrameValues`, decided by one small helper, `ModernMarkSizes`. An author's size never reaches the switch (`isSizeChanged`).
- **Two seeded values.** The tree corner is a plain field guarded by a new author flag on `PlotDescriptor`; the filled-radar alpha is a tiered value seeded per resolved type. Both follow S4a's and S2's seed patterns.
- **Painter rules for the schema marks.** Candle and stock override `SchemaPainter.fitScreen`, as S4a's box plot does. `SchemaVO.dodge` tells each painter how many marks share its slot, so every schema floor stops at the mark's share of the slot (D63–D65).
- **One flag per element.** `GraphGenerator.applyModernMarkRules` sets the new element flags (`AreaElement` solid outline, `SchemaElement` width floor), as in S4a.

**Tech Stack:** Java 21, JUnit 5, Mockito, Spring test context (`@SreeHome`), Maven module `core`. No web change.

**Spec:** `docs/superpowers/specs/lookfeel/2026-11-06-chart-defaults-design.md`, amended for S4b in `8664eda3e5`. The relevant parts are:
- §3 M2's sizes note, M4 (the tree corner flag), M7, M9
- §5 S4: the two shaping facts, the S4-7 to S4-12 rows, "S4-3 box plot" (the D65 floor), "S4-11 tree corner"
- §10 D44, D48, D49, D54–D56, D58, D60–D62, D63–D66

**Builds on:** S4a, PR #8121 (`bug-80455-s4a` at `8ace3a50b0`). From it this plan uses:
- `GraphGenerator.applyModernMarkRules(GraphElement, int)`, `isAuthorSized(GraphElement)`, `isBoxWidthRule(SchemaElement)` (`:5191`)
- `SchemaPainter.fitScreen(AffineTransform)` (`:59`), `BoxPainter.fitScreen` (`:134`) and its constants `MIN_BOX_PX = 6`, `MAX_BOX_PX = 28`, `BOX_WIDTH_RATIO = 0.3`
- `VSChartChromeDefaults.BAR_CORNER_RADIUS`, the bar-corner seed in `ChartVSAssemblyInfo.seedChromeDefaults` (`:165-166`, `:194-195`)
- D63's bar rule in `BarVO.getMinBarPx` as the model for the slot share

**Verified against:** `bug-80455-s4b` at `8664eda3e5`. Paths are under `core/src/main/java/inetsoft/` unless they start with `core/`.

## Corrections to the spec

| # | Spec | Code at `8664eda3e5` | This plan |
|---|---|---|---|
| C1 | S4-8: "in both `fixSizeFrameValues`" | As S4a's C4: `ChangeChartProcessor` has no mark, and the next composer draw rewrites the frame through `GraphGenerator` | `GraphGenerator` only |
| C2 | S4-8: largest 8 "in the point case" | After the switch, a fake point chart resets `largest` to 15 (`GraphGenerator:5476-5484`) | The bubble cap applies in that branch too |
| C3 | S4-7: "a 1px wick" | The wick draws with the default 1px stroke when no line frame is set (`CandlePainter.paint`) | No change; Task 5 pins it |
| C4 | S4-11: the flag `userNodeCornerRadius` | The script property's setter is on `PlotDescriptor` (`ChartProcessor:253-256`), and the wizard clones the whole plot descriptor (`SyncChartHandler:201-204`) | The flag lives on `PlotDescriptor`, with `setAuthorNodeCornerRadius` as S3's `LegendsDescriptor.setAuthorLayout` does; it is not on `ChartVSAssemblyInfo` like the bar-corner flag |
| C5 | S4-11: "a missing `nodeCornerRadius` attribute … its flag is derived true" | The parse turns a missing attribute into 0.0 (`PlotDescriptor:1566-1567`) | Derive by value alone: `nodeCornerRadius != 0.3` is the author's |
| C6 | S4-12: "a solid outline needs an `AreaElement` hint" | The outline strokes at `min(1, alpha × 1.2)` when there is no border colour (`AreaVO:250-264`) | A flag field on `AreaElement` (S4a's element-flag style), in `equalsContent`; the outline then strokes at alpha 1 |
| C7 | S4-12: radar lines "as line charts draw" | Show-point markers get their own copy at size − 2 (`GraphGenerator:4851-4856`) | A size-3 radar line gives size-1 points, as line charts already do |

**Known limitations:**
- **Older builds** draw bubbles up to 34px, edges up to 15px, 22px milestones, 1px radar lines, a 0.8 filled radar with a faint outline, unfloored candles and ticks, and 6px boxes in any slot (M9). They keep a marked tree's 0.08 corner and read it as the author's on the next open (M9).
- **Node size** is not capped (D55). **Word clouds and contour charts** keep their sizes (D58).
- **An author-set or bound Size** keeps today's candle body width (D66).
- **Dodged schema marks** past three series still spill out of the slot at the 30% box width (D57); only the floors are capped.

## Global Constraints

- **Worktree:** `$WT` is the executor's worktree on branch `bug-80455-s4b`, at or after `8664eda3e5`. Never work in `E:/StyleBI/stylebi-enterprise/community`.
- **Git:**
  - Each git command is its own call, `git -C "$WT" ...`, with no `&&` anywhere in the command.
  - One commit per task, subject `Chart defaults S4, <what changed>`, last line `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
  - Subagents never run `git add`, `git commit` or `git stash` (a hook blocks them); the controller stages and commits after review. Never push.
- **Scope is the mark (M1).** Every new graph-package property defaults to off, and off draws today's shapes. A classic chart is pixel-identical, and its XML is byte-identical on load and save while no author edits it.
- **Values (all under the mark; an author-changed size frame is never touched):**
  - **Bubble:** largest size 8 (radius 10, 20px across) on point charts and map points; word clouds and contour charts keep 15.
  - **Relation edge:** largest size 6 on tree, network and circular charts.
  - **Gantt milestone:** size 3 (radius 5, a 10px diamond); classic 9.
  - **Radar line:** size 3 (2px) on radar and filled radar; classic 1.
  - **Tree corner:** `VSChartChromeDefaults.NODE_CORNER_RADIUS = 0.08` modern, `PlotDescriptor.DEFAULT_NODE_CORNER_RADIUS = 0.3` legacy; a value other than 0.3 with no stored flag is the author's.
  - **Filled radar:** plot alpha 0.3 at the DEFAULT tier (modern, filled radar only; −1 otherwise and on the legacy branch); its outline strokes at alpha 1.
  - **Schema floors**, with `n` the marks dodged into the slot and `slotPx` the slot width in screen px:
    - candle body width at least `min(3, slotPx / n)`
    - stock tick at least `min(3, slotPx / (2n))` on each side
    - box width at least `min(6, slotPx / n)`; the 28px cap and the 30% rule are unchanged
- **Comments:** a short clause each, with no ticket, PR, slice or design-doc names.
- **New files** start with the AGPL header of `SeedChromeDefaultsTest.java`, `Copyright (C) 2026  InetSoft Technology`.
- **Tests that reach Spring beans** use `@ExtendWith(SpringExtension.class)`, `@ContextConfiguration(classes = BaseTestConfiguration.class, initializers = ConfigurationContextInitializer.class)`, `@SreeHome`, `@Tag("core")`, and clear `viewsheet.modernVisualization`, `viewsheet.darkMode` and `viewsheet.density` in `@AfterEach`.

## Review Focus

1. **A classic chart is untouched:** bubble, edge, milestone and radar sizes, the tree's 0.3 corner, the filled radar's 0.8 fill and faint outline, candle, stock and box widths, and the XML it writes. Pinned by the `classic…` cases in every task.
2. **A dense candlestick chart** (a year of daily data in a 400px card, a 1.4px slot) draws touching bodies, none hiding another. Pinned by `aCrowdedSlotFloorsCandlesAtTheirShare` (Task 5).
3. **A tree from a released build** stays square on "Keep my formatting" and on Revert, and the reset copy rounds it to 0.08. Pinned by `keepAndRevertKeepAReleasedTreeSquare` and `theResetCopyRoundsAReleasedTree` (Task 2).
4. **An author's alpha on a bar chart** survives the reset copy, while a filled radar's is reset (D62). Pinned by `theResetCopyKeepsAnAuthorsAlphaOffRadar` (Task 3).
5. **A word cloud on a modern dashboard** keeps its font sizes (largest 15). Pinned by `aWordCloudKeepsItsSizes` (Task 1).

## File Structure

- **Task 1:** `report/composition/graph/ModernMarkSizes.java` (new), `report/composition/graph/GraphGenerator.java` (`fixSizeFrameValues`).
- **Task 2:** `uql/viewsheet/graph/PlotDescriptor.java` (flag, author setter, derivation, XML), `uql/viewsheet/internal/VSChartChromeDefaults.java`, `uql/viewsheet/internal/ChartVSAssemblyInfo.java` (seed), `web/composer/model/vs/ChartAdvancedPaneModel.java` (pane stamp), `report/script/ChartProcessor.java` (script setter), `uql/viewsheet/internal/ChartChromeSlots.java` (reset slot).
- **Task 3:** `uql/viewsheet/internal/VSChartTypeDefaults.java`, `ChartVSAssemblyInfo.java` (`seedPlotAlpha`), `PlotDescriptor.java` (`savedDefaults`, `chromeValue`), `ChartChromeSlots.java`, `graph/element/AreaElement.java`, `graph/visual/AreaVO.java`, `GraphGenerator.java` (`applyModernMarkRules`).
- **Task 4:** `graph/schema/SchemaPainter.java` (cluster size), `graph/visual/SchemaVO.java` (`dodge`), `graph/schema/BoxPainter.java` (floor).
- **Task 5:** `graph/element/SchemaElement.java` (width floor flag), `graph/schema/CandlePainter.java`, `graph/schema/StockPainter.java`, `GraphGenerator.java`.
- **Task 6:** none (whole-branch check and live-check handoff).

**Test harnesses:**
- **Generator tests** extend `core/src/test/java/inetsoft/report/composition/graph/ModernMarkRulesTest.java`: its `Chart` record, `generator(chart, mark, brushed)`, `plot(chart, mark)`, `sizedChart(type)` (a bound linear Size), `ganttChart()`, `dim`/`agg`. Build a tree as `ChartChromeRenderTest` does (`:340-366`) and a candle or stock chart with `DirectionColorRenderTest.candle(T, withOpen)` (`:306`).
- **Seed and flag tests** copy `core/src/test/java/inetsoft/uql/viewsheet/internal/BarCornerFlagTest.java` (`releasedXml`, `newChart`, `parseInfo`, `writeXml`, the Keep/Revert case) and the modernize/revert/reset helpers of `ChartColorTypeSeedTest`.
- **Painter tests** copy `core/src/test/java/inetsoft/graph/schema/BoxPainterModernTest.java`: a mocked `SchemaGeometry` and `RectCoord`, `screen(slotPx)`, `fitted(painter, screen)`, and its dodge replay in `dodgedBoxesStaySideBySide`.

## How to run tests

From `$WT`:

```bash
./mvnw -q test -pl core "-Dtest=ClassA,ClassB" -Dsurefire.failIfNoSpecifiedTests=false
```

- Read `core/target/surefire-reports/*.txt` for counts.
- Run every new or changed class alone once as well (CLAUDE.md).
- If compilation fails with "Attempt to recreate … HeartBeatServiceProxy", delete the untracked `core/target/generated-test-sources`.

---

### Task 1: Modern mark sizes (S4-8, S4-9, S4-10, S4-12 line)

**Files:**
- Create: `report/composition/graph/ModernMarkSizes.java`
- Modify: `report/composition/graph/GraphGenerator.java:5396-5507` (`fixSizeFrameValues`)
- Test: `core/src/test/java/inetsoft/report/composition/graph/ModernMarkSizesTest.java` (new, no Spring), `ModernMarkRulesTest.java`

**Interfaces:**
- Produces: `final class ModernMarkSizes` (package-private) with
  - `static final double BUBBLE_LARGEST = 8`, `EDGE_LARGEST = 6`, `MILESTONE_SIZE = 3`, `RADAR_LINE_SIZE = 3`
  - `static double largest(int chartType, boolean wordCloud, boolean contour, double classic)` — 8 for a point chart (`GraphTypes.isPoint`) that is neither a word cloud nor a contour, and for `CHART_MAP`; 6 for `CHART_TREE`, `CHART_NETWORK`, `CHART_CIRCULAR`; otherwise `classic`
  - `static double staticSize(int chartType, GraphElement elem, double classic)` — 3 for a `CHART_GANTT` element that is not an `IntervalElement`; 3 for `CHART_RADAR` and `CHART_FILL_RADAR`; otherwise `classic`

- [ ] **Step 1: Write the failing unit tests** (`ModernMarkSizesTest`)

```java
@Test void bubblesCapAt8() {
   assertEquals(8, ModernMarkSizes.largest(GraphTypes.CHART_POINT, false, false, 15));
   assertEquals(8, ModernMarkSizes.largest(GraphTypes.CHART_MAP, false, false, 15));
}
@Test void wordCloudsAndContoursKeepTheirs() {
   assertEquals(15, ModernMarkSizes.largest(GraphTypes.CHART_POINT, true, false, 15));
   assertEquals(15, ModernMarkSizes.largest(GraphTypes.CHART_POINT, false, true, 15));
}
@Test void relationEdgesCapAt6() { /* CHART_TREE, CHART_NETWORK, CHART_CIRCULAR -> 6 */ }
@Test void otherTypesKeepTheClassicLargest() { /* CHART_BAR 25 -> 25, CHART_LINE 15 -> 15 */ }
@Test void aMilestoneIs3AndAGanttBarIsUntouched() {
   assertEquals(3, ModernMarkSizes.staticSize(GraphTypes.CHART_GANTT, new PointElement(), 9));
   assertEquals(15, ModernMarkSizes.staticSize(GraphTypes.CHART_GANTT, new IntervalElement(), 15));
}
@Test void radarLinesAre3() { /* CHART_RADAR and CHART_FILL_RADAR with a LineElement / AreaElement -> 3 */ }
```

- [ ] **Step 2: Write the failing generator tests** (`ModernMarkRulesTest`)
  - `aModernBubbleIsAtMost20pxAcross`: `sizedChart(CHART_POINT)` marked → the `PointElement`'s `LinearSizeFrame.getLargest() == 8`; in `plot(chart, MODERN_LIGHT)` the point of the largest Size value (row TX, Qty 11) is 20px across (±1).
  - `aClassicBubbleKeeps15`: unmarked → largest 15.
  - `anAuthorsBubbleSizeIsKept`: mark the size frame changed (`setChanged(true)`) → largest stays as the author left it (15).
  - `aWordCloudKeepsItsSizes`: `sizedChart(CHART_POINT)` plus a text dimension (`setTextField` with a `VSAestheticRef` on `dim("State")`), marked → largest 15.
  - `aModernTreeEdgeIsAtMost6`: tree from the `ChartChromeRenderTest` builder plus a bound linear Size on the chart (`setSizeField`) → the `RelationElement`'s size frame largest 6; classic 15.
  - `aModernMilestoneIs3`: `ganttChart()` plus `setMilestoneField(agg(date))` → the milestone `PointElement`'s static size 3 marked, 9 classic.
  - `aModernRadarLineIs3`: a `RadarVSChartInfo` (CHART_RADAR, three measures) → the `LineElement`'s static size 3 marked, 1 classic; the same for CHART_FILL_RADAR's `AreaElement`.

- [ ] **Step 3: Run them and see them fail**

Run: `./mvnw -q test -pl core "-Dtest=ModernMarkSizesTest,ModernMarkRulesTest" -Dsurefire.failIfNoSpecifiedTests=false`
Expected: `ModernMarkSizesTest` does not compile (class missing); after a stub, the modern cases fail with the classic values (15, 15, 9, 1).

- [ ] **Step 4: Implement `ModernMarkSizes` and wire it into `fixSizeFrameValues`**
  - After the `switch`, when `vizContext.modern`: `max = ModernMarkSizes.largest(chartType, GraphTypeUtil.isWordCloud(info), GraphTypes.isContour(info.getChartType()), max)` and `nsize = ModernMarkSizes.staticSize(chartType, elem, nsize)`.
  - In the fake-point branch (`:5480-5484`), apply the same `largest` with `GraphTypes.CHART_POINT` after it sets 15 (C2).
  - The existing `isSizeChanged` early returns stay the only author guard.

- [ ] **Step 5: Run the tests and see them pass**, plus `SizeLegendSymbolTest` and `ChartChromeRenderTest` unchanged.

- [ ] **Step 6: Commit** — `Chart defaults S4, cap modern bubbles, edges, milestones and radar lines`

---

### Task 2: Tree node corner (S4-11)

**Files:**
- Modify: `uql/viewsheet/graph/PlotDescriptor.java` (field `:2020`, setter `:1336`, parse `:1566-1567`, write `:1724`, equals `:1882`)
- Modify: `uql/viewsheet/internal/VSChartChromeDefaults.java`, `uql/viewsheet/internal/ChartVSAssemblyInfo.java` (`seedChromeDefaults`, beside the bar corner at `:165-166` and `:194-195`)
- Modify: `web/composer/model/vs/ChartAdvancedPaneModel.java:168-181`, `report/script/ChartProcessor.java:253-256`, `uql/viewsheet/internal/ChartChromeSlots.java` (`flags()`, `:222-246`)
- Test: `core/src/test/java/inetsoft/uql/viewsheet/internal/NodeCornerFlagTest.java` (new)

**Interfaces:**
- Produces on `PlotDescriptor`: `public static final double DEFAULT_NODE_CORNER_RADIUS = 0.3` (made public), `boolean isUserNodeCornerRadius()`, `void setUserNodeCornerRadius(boolean)`, `void setAuthorNodeCornerRadius(double)` (sets the value and the flag), a private `derivedUserNodeCornerRadius()` = `nodeCornerRadius != DEFAULT_NODE_CORNER_RADIUS`.
- Produces: `VSChartChromeDefaults.NODE_CORNER_RADIUS = 0.08`; reset slot key `chart:nodeCornerRadius`.

- [ ] **Step 1: Write the failing tests** (`NodeCornerFlagTest`, modelled on `BarCornerFlagTest`)
  - `aReleasedFilesTreeIsTheAuthors`: plot XML with no `nodeCornerRadius` attribute → value 0, `isUserNodeCornerRadius()` true.
  - `aReleasedNonTreeChartIsFlaggedToo` (D60): the same on a bar chart's plot, so a later change into a tree keeps square nodes.
  - `aDefaultCornerIsNoOpinion`: `nodeCornerRadius="0.3"`, no flag attribute → flag false.
  - `anotherCornerIsTheAuthors`: `nodeCornerRadius="0.2"`, no flag → flag true.
  - `aStoredFlagIsReadAsIs`: `userNodeCornerRadius="false"` with 0.08 → false.
  - `aNewModernChartSeeds008` / `aNewClassicChartSeeds03`.
  - `keepAndRevertKeepAReleasedTreeSquare`: a released tree → Modernize ("Keep"), then Revert → value 0 both times.
  - `theResetCopyRoundsAReleasedTree`: the reset copy clears the flag and seeds 0.08.
  - `aClassicChartWritesWhatItAlwaysWrote`: a classic chart's plot XML has no `userNodeCornerRadius` attribute; a seeded modern one writes `userNodeCornerRadius="false"`.
  - `anApplyWithoutAChangeDoesNotStamp` / `aPaneChangeStamps`: `ChartAdvancedPaneModel.updateChartAdvancedPaneModel` with the corner unchanged leaves the flag false; with 0.2 sets it true.
  - `aScriptAssignmentStamps`: the `nodeCornerRadius` script property on a tree sets the flag.

- [ ] **Step 2: Run them and see them fail** (compile errors, then flags false).

- [ ] **Step 3: Implement**
  - `PlotDescriptor`: the flag field (default false), parse it with `DerivedUserFlag.parse(Tool.getAttribute(node, "userNodeCornerRadius"), derivedUserNodeCornerRadius())` after the corner's own parse, write it with `DerivedUserFlag.write`, and join it into `equals`.
  - `ChartVSAssemblyInfo.seedChromeDefaults`: while `!plotDesc.isUserNodeCornerRadius()`, write `NODE_CORNER_RADIUS` on the modern branch and `DEFAULT_NODE_CORNER_RADIUS` on the legacy branch.
  - `ChartAdvancedPaneModel`: capture the corner before `updateChartPlotOptionsPaneModel` and stamp `setUserNodeCornerRadius(true)` when it changed, as for the bar corner.
  - `ChartProcessor`: the tree's `nodeCornerRadius` property uses `setAuthorNodeCornerRadius`.
  - `ChartChromeSlots.flags()`: a `ChromeSlot.tiered("chart:nodeCornerRadius", …)` beside `chart:barCornerRadius`, reading `PLOT.then(PlotDescriptor::isUserNodeCornerRadius)` and clearing the plot's flag.

- [ ] **Step 4: Run** `NodeCornerFlagTest`, `BarCornerFlagTest`, `SeedChromeDefaultsTest`, `ChartChromeSlotsTest`, `ChartPlotOptionsPaneModelTest`, `WizardSyncReseedTest` — all pass.

- [ ] **Step 5: Commit** — `Chart defaults S4, seed the 0.08 tree corner and protect a released tree's square nodes`

---

### Task 3: Filled radar fill and outline (S4-12)

**Files:**
- Modify: `uql/viewsheet/internal/VSChartTypeDefaults.java`, `uql/viewsheet/internal/ChartVSAssemblyInfo.java` (`seedTypeDefaults`, `:3264-3279`), `uql/viewsheet/graph/PlotDescriptor.java` (`savedDefaults` `:1942-1945`, `chromeValue` `:1895`), `uql/viewsheet/internal/ChartChromeSlots.java` (plot block, `:154-164`)
- Modify: `graph/element/AreaElement.java` (field, `equalsContent` `:251`), `graph/visual/AreaVO.java:259`, `report/composition/graph/GraphGenerator.java` (`applyModernMarkRules`)
- Test: `core/src/test/java/inetsoft/uql/viewsheet/internal/RadarAlphaSeedTest.java` (new), `core/src/test/java/inetsoft/graph/visual/AreaVOSolidOutlineTest.java` (new), `ModernMarkRulesTest.java`

**Interfaces:**
- Produces: `static double VSChartTypeDefaults.plotAlpha(ResolvedChartType t)` — 0.3 for `CHART_FILL_RADAR`, −1 otherwise; `ChartVSAssemblyInfo.seedPlotAlpha(VizContext, ResolvedChartType)` (private), modelled on `seedXGrid` (`:3418-3431`); `AreaElement.isSolidOutline()` / `setSolidOutline(boolean)`.

- [ ] **Step 1: Write the failing tests**
  - `RadarAlphaSeedTest`:
    - `aModernFilledRadarSeeds03`: DEFAULT tier 0.3, and the saved XML carries it (it travels).
    - `aClassicFilledRadarKeepsAuto`: −1, nothing new in the XML.
    - `anAuthorsAlphaWins`: a USER-tier 0.5 survives a re-seed and a type change.
    - `aTypeChangeAwayFromFilledRadarDropsTheSeed`: marked filled radar → bar → DEFAULT −1.
    - `theResetCopyResetsAFilledRadarsAlpha` and `theResetCopyKeepsAnAuthorsAlphaOffRadar` (D62).
  - `AreaVOSolidOutlineTest`: a closed `AreaElement` with alpha hint 0.3 paints its outline at alpha 1 when `setSolidOutline(true)`, and at 0.36 when false (read the stroke colour's alpha from a recording `Graphics2D`, as `AreaVOPaintTest:168` does).
  - `ModernMarkRulesTest.aMarkedFilledRadarHasASolidOutline` (true marked, false classic) and `aMarkedFilledRadarDrawsAt30Percent` (the element's `HINT_ALPHA` is 0.3 marked; classic keeps 0.8).

- [ ] **Step 2: Run them and see them fail.**

- [ ] **Step 3: Implement**
  - `seedPlotAlpha`: on the modern branch write `plotAlpha(type)` at the DEFAULT tier; on the legacy branch write −1; call it from `seedTypeDefaults` beside `seedXGrid`.
  - Add `alpha` to `savedDefaults()` and to `chromeValue("alpha")`.
  - In `ChartChromeSlots`' plot block add `valueSlot("chart:plot:alpha", PLOT.then(p -> p.chromeValue("alpha")))` only when `ResolvedChartType.of(CHART.apply(source)).type() == GraphTypes.CHART_FILL_RADAR`.
  - `AreaElement`: a `solidOutline` field (default false) in `equalsContent`; `AreaVO` strokes its outline at 1 when it is set.
  - `applyModernMarkRules`: `area.setSolidOutline(true)` for an `AreaElement` on `CHART_FILL_RADAR`.

- [ ] **Step 4: Run** the three classes plus `SeedChromeDefaultsTest`, `ChartChromeSlotsTest`, `AreaVOPaintTest`, `ChartChromeTypeSeedTest`.

- [ ] **Step 5: Commit** — `Chart defaults S4, draw modern filled radars at 30% with a solid outline`

---

### Task 4: Slot share for schema marks (D65, box plots)

**Files:**
- Modify: `graph/schema/SchemaPainter.java` (slot and cluster size), `graph/visual/SchemaVO.java` (constructor `:60-69`, `dodge` `:377-415`), `graph/schema/BoxPainter.java` (`init`, `fitScreen` `:134-148`, `moveSlot`)
- Test: `core/src/test/java/inetsoft/graph/schema/BoxPainterModernTest.java`

**Interfaces:**
- Produces on `SchemaPainter` (moved up from `BoxPainter`, unchanged in behaviour): `protected double slotWidth` and `protected double slotCenter` (chart units; `slotWidth` 0 when no rule is on), `public void moveSlot(AffineTransform trans)`, and a helper `protected void slotScale(AffineTransform screen, double currentPx, double floorPx, double capPx)` that scales every shape along the mark's width about the slot centre by `clamp(currentPx, floorPx, capPx) / currentPx` (the body of today's `BoxPainter.fitScreen` scaling).
- Produces on `SchemaPainter`: `public void setClusterSize(int n)` and `protected int getClusterSize()` (default 1; `clone()` copies it); `protected static double minFloorPx(double floorPx, double slotPx, int n)` = `min(floorPx, slotPx / n)`.
- `SchemaVO`'s constructor calls `painter.moveSlot(trans)` for every painter (today only for a `BoxPainter`). `SchemaVO.dodge` sets `setClusterSize(vos.size() + 1)` on this painter and on every painter in `vos`, in the `MOVE_DODGE` branch.

- [ ] **Step 1: Write the failing tests** (`BoxPainterModernTest`)
  - `aSlotUnder6pxFloorsTheBoxAtTheSlot`: `screen(4)` → box width 4, centred.
  - `twoDodgedBoxesShareASlotUnder12px`: replay `dodgedBoxesStaySideBySide` at `screen(8)` with `setClusterSize(2)` on both → each box 4 wide, no overlap.
  - `aNarrowSlotFloorsTheBoxAt6` (existing, `screen(10)`) still passes: `min(6, 10) = 6`.

- [ ] **Step 2: Run them and see them fail** (6px against 4).

- [ ] **Step 3: Implement** the move of the slot fields and `moveSlot` into `SchemaPainter`, `slotScale`, the cluster size, the two `SchemaVO` calls, and `BoxPainter.fitScreen`'s floor `minFloorPx(MIN_BOX_PX, slotPx, getClusterSize())`, where `slotPx = slotWidth × |unit|`. Every existing `BoxPainterModernTest` case must still pass unchanged.

- [ ] **Step 4: Run** `BoxPainterModernTest`, `ModernMarkRulesTest`, `SchemaVO`-touching tests (`grep -rl SchemaVO core/src/test`).

- [ ] **Step 5: Commit** — `Chart defaults S4, floor a crowded box plot at its share of the slot`

---

### Task 5: Candle and stock floors (S4-7, D64, D66)

**Files:**
- Modify: `graph/element/SchemaElement.java` (flag, `equalsContent`), `graph/schema/CandlePainter.java` (`init` `:46-77`, new `fitScreen`), `graph/schema/StockPainter.java` (`init` `:46-66`, new `fitScreen`), `report/composition/graph/GraphGenerator.java` (`applyModernMarkRules`)
- Test: `core/src/test/java/inetsoft/graph/schema/CandleStockModernTest.java` (new), `ModernMarkRulesTest.java`

**Interfaces:**
- Consumes from Task 4: `SchemaPainter.slotWidth`, `slotCenter`, `slotScale(screen, currentPx, floorPx, capPx)`, `getClusterSize()`, `minFloorPx(floorPx, slotPx, n)`; `SchemaVO` already moves every painter's slot and sets its cluster size.
- Produces: `SchemaElement.isWidthFloor()` / `setWidthFloor(boolean)`; `CandlePainter.MIN_BODY_PX = 3`, `StockPainter.MIN_TICK_PX = 3`.

- [ ] **Step 1: Write the failing tests**
  - `CandleStockModernTest` (painter harness of `BoxPainterModernTest`; the candle's body is `getShape(1)`, the stock's ticks `getShape(1)` and `getShape(2)`):
    - `aThinCandleBodyFloorsAt3`: a slot where the natural body is under 3px → 3, centred on the wick.
    - `aWideCandleBodyIsUntouched`: natural body over 3px → unchanged.
    - `aCrowdedSlotFloorsCandlesAtTheirShare`: `screen(1.4)` (a year of days in 360px) → body 1.4, and two dodged candles in a 2px slot → 1 each, no overlap.
    - `theWickStays1px`: the wick is a line painted with a 1px stroke (C3).
    - `aShortStockTickFloorsAt3` / `aCrowdedSlotFloorsTicksAtHalfTheirShare` (`min(3, slotPx / (2n))` each side).
    - `classicCandleAndStockUnchanged`: with the flag off, shapes equal today's.
  - `ModernMarkRulesTest`: `aMarkedCandleSetsTheWidthFloor`, `anAuthorSizedCandleDoesNot` (D66), `aSizeBoundCandleDoesNot`, `aMarkedStockAlwaysSetsIt`, `aClassicCandleDoesNot` — candle and stock charts from `DirectionColorRenderTest.candle`.

- [ ] **Step 2: Run them and see them fail.**

- [ ] **Step 3: Implement**
  - `init()` sets `slotWidth = coord.getMaxWidth()` and `slotCenter = high.getX()` when `geometry.getElement()` is a `SchemaElement` with `isWidthFloor()` and `coord` is a `RectCoord`, as `BoxPainter.init` does.
  - `CandlePainter.fitScreen(screen)`: when the body is thinner than `minFloorPx(MIN_BODY_PX, slotPx, n)`, `slotScale` every shape up to it about the slot centre, so the wick stays centred on the body and dodged candles keep their spacing; the wick's stroke stays 1px.
  - `StockPainter.fitScreen(screen)`: when a tick is shorter than `minFloorPx(MIN_TICK_PX, slotPx, 2n)`, `slotScale` every shape so the tick reaches it.
  - No cap: candle and stock pass `Double.MAX_VALUE` as `capPx`.
  - `applyModernMarkRules`: for a `SchemaElement` whose painter is a `CandlePainter`, `setWidthFloor(!isAuthorSized(elem) && (size == null || size.getField() == null))`; for a `StockPainter`, `setWidthFloor(true)`.

- [ ] **Step 4: Run** `CandleStockModernTest`, `ModernMarkRulesTest`, `DirectionColorRenderTest`, `BoxPainterModernTest`.

- [ ] **Step 5: Commit** — `Chart defaults S4, keep modern candle bodies and stock ticks visible without overlap`

---

### Task 6: Whole-branch check and live-check handoff

- [ ] **Step 1:** Run every class named in Tasks 1–5 alone, then the full core suite: `./mvnw -q test -pl core`. Expected: 0 failures (a known timing flake, `XSwapperGCThrottleTest`, passes alone).
- [ ] **Step 2:** Write `E:/Temp/chart-defaults-s4b-checks-handoff.md` in the S4a handoff's shape: per item a fixture, the expected value and an empty Result column — bubbles (point and map), tree edges, Gantt milestone, radar and filled radar (fill, outline, line), tree corner (new, released file, Keep, Revert, reset copy), candle and stock at normal and a year of daily data, crowded box plot, classic identity across builds, exports.
- [ ] **Step 3:** No commit (the handoff is outside the repository).
