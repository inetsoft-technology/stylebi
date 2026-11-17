# Chart Defaults S2a — Palette Seeds Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give a marked (modern) chart the bundle's colours wherever a colour is chosen by the system, not the author:
- a chart with no colour field takes modern palette slot 1, and further measures follow the modern palette order
- relation edges draw in the chrome axis-line grey
- a map's regions draw in a pale slot 1 and its no-data regions in the grid colour
- the pareto line takes slot 2 at 1.5px, with its percent labels in the same colour
- a scatter contour gets the teal ramp
- size-legend symbols use the chrome label grey

Classic charts stay pixel-identical and keep their XML.

**Architecture:**
- Every colour here already lives in a field with tiers:
  - static colour frames keep default, CSS and user colours
  - `PlotDescriptor` colours are `CompositeValue`s
- So each value is a DEFAULT-tier seed written by `ChartVSAssemblyInfo` on both branches.
- **Measure colours.** They move between palettes by position (legacy slot *i* ↔ modern slot *i*), so Revert is exact. The places that pick or compare a measure colour learn the chart's mark through the `VizContext` they already carry or can reach.
- **The chart-level colour.** It serves several chart types (relation, map, radar, candle), so it is seeded per resolved type in S0's `seedTypeDefaults`.
- **Drawing rules.** Three things are drawing rules gated on `GraphGenerator.vizContext`:
  - the pareto width and percent labels
  - the region colour of a map with points
  - the size-legend symbols

**Tech Stack:** Java 21, JUnit 5, Mockito, Spring test context (`@SreeHome`), Maven module `core`. Angular 21 / Vitest for the two colour panes (Task 5).

**Spec:** `docs/superpowers/specs/lookfeel/2026-11-06-chart-defaults-design.md`. The relevant sections are:
- §3 M1–M9
- §5 S2 (S2-1 to S2-5, S2-8, and "S2-1 palette readers")
- §10 D22–D25
- §11

Where this plan and the spec differ, this plan wins (see Corrections). S2-6, S2-7 and S2-9 to S2-13 are slice S2b, planned separately once their rulings are made.

**Builds on:**
- S0 (`2026-11-06-chart-defaults-s0-defaults-travel.md`) and S1 (`2026-11-06-chart-defaults-s1-chart-chrome.md`), both open as PRs #7986 and #7987.
- Names used from their "Interfaces for later slices":
  - `PlotDescriptor.savedDefaults()` and `ChartDescriptorSavedDefaultsTest`
  - `seedTypeDefaults(VizContext, ResolvedChartType)` and `reseedIfResolvedTypeChanged()`
  - the `ResolvedChartType` record
- feature-80398's `ChartChromeSlots` (`PLOT_VALUES`, `frames`, `staticSlot`).

**Verified against:** `bug-80455-s1` at `13c54d60b6` (S0 + S1 on `epic-74519` `d8949937fe`). Paths are under `core/src/main/java/inetsoft/` unless they start with `core/` or `web/`.

## Owner rulings (2026-11-10)

| # | Ruling |
|---|---|
| O1 | The colour panes' Reset and Clear remove the author colour on **every** chart, so the seeded default shows. Today they write a palette colour into the user tier. |
| O2 | Relation edges are seeded grey through the chart-level colour. Root nodes, which read the same colour when a node colour field is bound, go grey with them. |
| O3 | The pareto line colour also colours the percent-axis labels, although `#FF5A35` labels sit at about 2.6:1 on the light card. |
| O4 | A server property an admin set (`map.default.color`, `map.empty.color`) still wins on a modern map. |

## Corrections to the spec

| # | Spec | Code at `13c54d60b6` | This plan |
|---|---|---|---|
| C1 | S2-1: "the pick at bind time uses the context" and four readers | The picks are `GraphUtil.fixStaticColorFrame:1049-1072` and `fixDuplicateColor:1074-1125`. Two more re-pick at render: `VSFrameVisitor:198` and `VSColorFrameStrategy.createCombinedFrame:102`. Image shapes compare with the legacy list (`GraphGenerator.isApplyColor:5492`, `:5503`). The binding model flags any colour off the legacy list as changed (`ChartInfoModelBuilder.fixColorChanged:277-298`); `changed` is saved, and it stops the 0.75 darkening of line, point and radar (`GraphGenerator:4729-4748`, `AbstractChartInfo.isColorChanged:2116-2142`). | All of these take the mark's palette (Tasks 3, 4). Modernize and Revert move each default colour by palette position (Task 2), which is exact; re-picking by order is not, because a classic measure's colour depends on drop history. |
| C2 | S2-1: "Candle (`Color.gray`)" | `CandleGraphGenerator:217-224` is the placeholder for an incomplete binding. Candle, stock, radar, relation and map draw the chart-level frame (`AbstractChartInfo.cFrame`, `MergedVSChartInfo.supportsColorFieldFrame:264`), darkened by 0.75 for candle, stock and radar. | S2a seeds the chart-level frame per resolved type (Task 6). S2b replaces the candle and stock colour. |
| C3 | S2-2: "use slot 2 when no author colour is set" | The line copies the bar colour unless a colour field is bound (`getParetoColorFrame:3383-3413`). `paretoLineColor` is not in `savedDefaults()`. The line has no size frame (`GraphTypeUtil.supportsFrame:1087`), so it draws 1px. The percent axis copies the y-axis text colour (`GraphDefault:86`). | Colour is V. Width and percent labels are D (Task 8). |
| C4 | S2-3: "the edge element's static frame" | The edges draw the chart-level frame (`RelationEdgeVO:85`), which root nodes also read when a node colour field is bound (`RelationGeometry.getColor:59-91`). A type change keeps the chart-level frame only within the same info class, relation to relation or map to map (`ChangeChartTypeProcessor.fixColorFrame:198-200`). A tree to radar change builds a fresh `RadarVSChartInfo`, whose frame is `#518DB9`. | Per-type seed (Task 6); roots go grey (O2). |
| C5 | S2-4: one region colour, written by `fixMapColorFrame`, whose palette check (`ChangeChartProcessor:917`) becomes mark-aware | A polygon-only map stores the region colour in the chart-level frame. A map with points, and a contour map, read `getMapDefaultColor()` at render (`MapGenerator:489-492`), so it is D there. `getMapEmptyColor:330-353` lets any stored tier beat `map.empty.color`. `fixMapColorFrame:906-930` writes only over legacy slot 1, or when `map.default.color` is set. | Task 6 seeds the polygon-only colour per type and draws the D case. `fixMapColorFrame` stays as it is: it never overwrites a modern value except under O4, which is the ruling. Task 7 seeds the no-data colour and reorders the lookup so the property beats the seed (O4). |
| C6 | S2-5: "New modern charts are already teal" | Only through the wizard. The type picker calls `copyToContour:1628-1635` before `fixColorFrame`, and it installs `BluesColorFrameWrapper` with no context. | Fix the picker (kind C) and seed the chart-level ramp on a mark transition (Task 9). |
| C7 | M5: the legacy pass writes the classic value | Static frames save their DEFAULT on classic charts too (`StaticColorFrameWrapper:96-110`). | The legacy pass of the chart-level frame writes only over a value a modern seed writes, so classic XML stays byte-identical. Measure colours need no guard: position mapping leaves legacy colours where they are. |
| C8 | S2-8: one symbol-colour property | `SizeLegendItem.paintSymbol:67` always draws the box in `SYMBOL_BORDER`. | Two `LegendSpec` properties: fill fallback and border (Task 10). |
| C9 | S2-4: "the modern card composite (`#ECEBE9` light, `#313034` dark)" | Those are the 15% card tint (`VSObjectChromeDefaults:127-129`) over the page (`#F8F7F4`, `#1C1B1F`). The page is not `#ECEBE9`. | `VSObjectChromeDefaults.cardComposite(ctx)` computes them (Task 1). |

**Known limitations, recorded and not built:**
- **Word clouds.** `GraphUtil.syncWorldCloudColor:4026-4044` copies the chart colour into the text field's user colour, so a word cloud keeps its copied colour through Modernize and Revert.
- **Darkening.** Lines, points and radar keep the 0.75 darkening, so slot 1 draws darker there. This is the same as the shipped modern categorical palette on lines.
- **An org that edits its Modern `ChartPalette` between Modernize and Revert.** A seeded colour that is no longer in either palette stays as it is.
- **An org `Modern` or `Modern Dark` `ChartPalette` in `format.css` that is shorter than 40 colours, or that shares a colour with another list.** Revert is inexact for the affected colours: position mapping wraps on the shorter list, and a shared colour maps by the first list that holds it.
- **A relation or radar whose classic chart-level colour was not legacy slot 1.** It comes back as slot 1 after Modernize then Revert.
- **A modern map while `map.empty.color` is set.** The plot dialog shows the seeded grid colour while the property's colour draws.

## Global Constraints

- **Worktree:** `$WT` is the executor's worktree on branch `bug-80455-s2`, cut from `origin/bug-80455-s1` (`13c54d60b6`). Never work in `E:/StyleBI/stylebi-enterprise/community`. Rebase onto `bug-80455-s1` when that branch moves.
- **Git:**
  - Each git command is its own call, `git -C "$WT" ...`, never chained with `&&`.
  - Run `git -C "$WT" branch --show-current` before every commit.
  - One commit per task, subject `Chart defaults S2a, <what changed>`, plain prose, last line `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
  - `git add` and `git commit` are separate calls. Never push.
- **Scope is the mark (M1, D22):** only an assembly with a modern `VizMark` changes. A classic chart renders pixel-identical, and its XML is byte-identical on load and save. The one exception is O1: after an explicit Reset or Clear, a classic chart's XML loses its `userColor`. Graph-package changes read a property the generator sets from `vizContext`; unset, they behave as today.
- **The author wins (M4):**
  - A static colour frame's user tier is the author's, and the seeds write only its default tier.
  - A `CompositeValue` seed writes only `Type.DEFAULT`.
- **Palette values:**
  - Modern slot *n* is `VSChartPaletteDefaults.activePalette(ctx)[n-1]`; slot 1 is `#0490FF` light and `#4FA5FF` dark, slot 2 is `#FF5A35` light and `#FF8367` dark.
  - The legacy list is the raw `CategoricalColorFrame.COLOR_PALETTE`, not `legacyPalette()`: every legacy pick uses the raw list.
- **Chrome values:**
  - `VSChartChromeDefaults.axisLineColor(ctx)` (`#85827A`, `#7E7B86`)
  - `gridlineColor(ctx)` (`#D6D2C8`, `#4A484F`)
  - `labelColor(ctx)` (`#6A685F`, `#CAC4D0`)
- **Registration (S0):** a `PlotDescriptor` field a seed writes joins `savedDefaults()`, `chromeValue(String)` and `ChartChromeSlots.PLOT_VALUES`, in the commit that first seeds it on both branches.
- **Comments:** a short clause each. Source comments name no ticket, PR, issue, slice or design doc. No `.html` file is touched.
- **New files** start with the AGPL header of `SeedChromeDefaultsTest.java`, `Copyright (C) 2026  InetSoft Technology`.
- **Java tests that reach Spring beans** use:
  - `@ExtendWith(SpringExtension.class)`
  - `@ContextConfiguration(classes = { BaseTestConfiguration.class, LibManagerTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)`
  - `@SreeHome` and `@Tag("core")`
  - In `@AfterEach`, clear `viewsheet.modernVisualization`, `viewsheet.darkMode`, `viewsheet.density`, `map.default.color` and `map.empty.color`, then call `VSChartPaletteDefaults.clearMemo()`. Close every `mockStatic`.

## Review Focus

1. **A classic two-measure chart whose remaining measure holds legacy slot 2** (the first was removed) comes back with legacy slot 2 after Modernize then Revert, not slot 1. Pinned in Task 2 by `aSecondSlotMeasureComesBackAfterRevert`.
2. **On a modern chart where the author coloured one measure, dropping another measure** picks the first modern colour that neither existing measure uses, and a render does not re-pick it from the legacy list. Pinned in Task 3 by `aDropSkipsTheAuthorsAndTheSeededColours` and in Task 4 by `aRenderDoesNotRepickAModernColour`.
3. **Reset in the colour pane on a modern chart, then Revert,** draws the legacy colours. Pinned in Task 5 by `resetThenRevertDrawsTheLegacyColour`.
4. **A modern polygon map that gains a point layer** draws its points in slot 1, not in the pale region tint, and loses the layer back to the tint. Pinned in Task 6 by `addingPointsMovesTheChartColourToSlotOne`.
5. **An admin who set `map.empty.color`** sees it on a modern map, although the seed wrote the grid colour. Pinned in Task 7 by `theServerPropertyBeatsTheSeededNoDataColour`.

## File Structure

- `uql/viewsheet/internal/VSChartPaletteDefaults.java`: the static palette, position mapping, map region tint, modern-value test.
- `uql/viewsheet/internal/VSObjectChromeDefaults.java`: card composite.
- `uql/viewsheet/internal/ChartVSAssemblyInfo.java`: measure colour seed, per-type chart colour seed, pareto, no-data and contour seeds.
- `uql/viewsheet/internal/ResolvedChartType.java`: polygon-only component.
- `uql/viewsheet/graph/PlotDescriptor.java`, `uql/CompositeValue.java`, `uql/viewsheet/internal/ChartChromeSlots.java`: registration and tier queries.
- `report/composition/graph/GraphUtil.java`, `VSFrameVisitor.java`, `VSColorFrameStrategy.java`, `GraphGenerator.java`, `GraphDefault.java`, `MapGenerator.java`: picks and drawing.
- `graph/LegendSpec.java`, `graph/guide/legend/Legend.java`, `graph/guide/legend/SizeLegendItem.java`: size-legend symbols.
- Bind paths: `web/binding/handler/ChartDndHandler.java`, `VSChartBindingHandler.java`, `web/binding/controller/ChangeChartRefService.java`, `report/internal/graph/ChangeChartTypeProcessor.java`, `web/vswizard/recommender/object/VSChartDefaultRecommendationFactory.java`, `web/binding/service/ChartInfoModelBuilder.java`, `web/binding/service/graph/aesthetic/ColorFrameModelFactory.java`, `web/graph/model/dialog/ChartPlotOptionsPaneModel.java`, `web/composer/model/vs/ChartAdvancedPaneModel.java`.
- Client: `web/projects/portal/src/app/binding/editor/chart/aesthetic/combined-color-pane.component.ts`, `static-color-pane.component.ts`.
- Tests:
  - new: `core/src/test/java/inetsoft/uql/viewsheet/internal/StaticColorSeedTest.java`, `.../internal/ChartColorTypeSeedTest.java`, `core/src/test/java/inetsoft/report/composition/graph/StaticColorPickTest.java`, `.../graph/ChartColorRenderTest.java`, `core/src/test/java/inetsoft/web/binding/service/graph/aesthetic/StaticColorFrameFactoryTest.java`
  - extended: `VSChartPaletteDefaultsTest`, `ChartChromeRenderTest`, `ChartChromeSlotsTest`, `combined-color-pane.spec.ts`

**Test harnesses.**
- **Seed tests** copy `ChartChromeTypeSeedTest`'s `newChart()`, `gateOn()`, `bind(...)`, `modernize(...)` and `revert(...)` (`:843-884`). A measure is `VSChartAggregateRef` with `setDataRef(new BaseField(name))` and `setFormulaValue("Sum")`; its colour is `((StaticColorFrameWrapper) ref.getColorFrameWrapper())`.
- **Render tests** build an `EGraph` as `ChartChromeRenderTest.paretoGraph` (`:326-360`) does:
  - `VizModernizeUtil.reseedAfterRestore(assemblyInfo)` stands for creation
  - then call `GraphGenerator.getGenerator(assemblyInfo, null, new DefaultDataSet(rows), new VariableTable(), null, XSourceInfo.NONE, null).createEGraph()`

## How to run tests

From `$WT`. `-q` hides the summary on success; read `core/target/surefire-reports/*.txt` for counts.

```bash
./mvnw -q test -pl core "-Dtest=ClassA,ClassB" -Dsurefire.failIfNoSpecifiedTests=false
```

Portal specs (Task 5) run from `$WT/web` after `npm ci` there; a junctioned `node_modules` breaks Vitest:

```bash
npx ng test portal --include="**/combined-color-pane.spec.ts"
```

Run every new or changed test class on its own at least once. Do not add `install`.

---

### Task 1: Palette helpers for static colours

S2-1, S2-4, C1, C9.

**Files:**
- Modify: `uql/viewsheet/internal/VSChartPaletteDefaults.java`, `uql/viewsheet/internal/VSObjectChromeDefaults.java`
- Test: `core/src/test/java/inetsoft/uql/viewsheet/internal/VSChartPaletteDefaultsTest.java`, `VSObjectChromeDefaultsTest.java`

**Interfaces:**
- Produces:
  - `VSObjectChromeDefaults.cardComposite(VizContext ctx): Color`: `cardTintOver(ctx.dark ? PAGE_BG_DARK : PAGE_BG)`.
  - `VSChartPaletteDefaults.staticPalette(VizContext ctx): Color[]`: `activePalette(ctx)` when `ctx != null && ctx.modern`, else a copy of `CategoricalColorFrame.COLOR_PALETTE`.
  - `VSChartPaletteDefaults.reseatStaticColor(Color color, VizContext ctx): Color`: the colour at the same index of `staticPalette(ctx)`. The index comes from the first list that contains `color`, searched in this order: `staticPalette(ctx)` (returns `color` itself), `COLOR_PALETTE`, `modernPalette()`, `darkPalette()`, `MODERN_FALLBACK`, `DARK_FALLBACK`. A colour in none of them, or `null`, is returned unchanged. An index past the target's length wraps (`% length`), as `getColorIndex` does.
  - `VSChartPaletteDefaults.mapRegionColor(VizContext ctx): Color`: `activePalette(ctx)[0]` at 30% (alpha 77), flattened with `VSObjectChromeDefaults.flattenOver` over `cardComposite(ctx)`.
  - `VSChartPaletteDefaults.isModernChartColor(Color color): boolean`: true for a member of `modernPalette()`, `darkPalette()`, `MODERN_FALLBACK` or `DARK_FALLBACK`, or for `VSChartChromeDefaults.axisLineColor` or `mapRegionColor` of `VizContext.of(VizMark.MODERN_LIGHT)` or `VizContext.of(VizMark.MODERN_DARK)`.

- [ ] **Step 1: Write the failing tests**

In `VSObjectChromeDefaultsTest`:
- `theCardCompositeIsTheTintOverThePage`: light `#ECEBE9`, dark `#313034`.

In `VSChartPaletteDefaultsTest`:
- `aClassicChartPicksFromTheRawLegacyList`: `staticPalette(VizContext.LEGACY)[0]` is `#518DB9` and `[1]` is `#B9DBF4`.
- `aModernChartPicksFromItsMarksPalette`: `[0]` is `#0490FF` (MODERN_LIGHT) and `#4FA5FF` (MODERN_DARK).
- `reseatKeepsThePalettePosition`: under MODERN_LIGHT, `#518DB9` gives `#0490FF` and `#B9DBF4` gives `#FF5A35`. Under LEGACY, `#FF5A35` gives `#B9DBF4` and `#4FA5FF` gives `#518DB9`. Under MODERN_DARK, `#0490FF` gives `#4FA5FF`.
- `reseatLeavesOtherColoursAlone`: `Color.RED`, `GraphUtil.OLD_COLOR_PALETTE_DEFAULT_COLOR`, `GraphUtil.MAP_DEFAULT_COLOR` and `null` come back unchanged under every context.
- `theThreeListsShareNoColour`: no colour is in two of `COLOR_PALETTE`, `MODERN_FALLBACK`, `DARK_FALLBACK`, and none repeats within one. The mapping relies on this.
- `theMapRegionTintIsSlotOneOverTheCard`: `#A6D0F0` light, `#3A5371` dark.
- `onlyModernSeedValuesCountAsModern`: true for `#0490FF`, `#4FA5FF`, `#85827A`, `#7E7B86`, `#A6D0F0` and `#3A5371`; false for `#518DB9`, `#FBD29A` and `Color.RED`.

- [ ] **Step 2: Run to verify they fail**

Run: `./mvnw -q test -pl core "-Dtest=VSChartPaletteDefaultsTest,VSObjectChromeDefaultsTest" -Dsurefire.failIfNoSpecifiedTests=false`
Expected: compile failure (`staticPalette` not found).

- [ ] **Step 3: Implement** the five methods as under Interfaces.

- [ ] **Step 4: Run to verify they pass.** Same command. Expected: all pass, including `cssMatchesTheJavaFallback`.

- [ ] **Step 5: Commit** both main files and both tests: `Chart defaults S2a, add the palette lookups a static chart colour moves between`.

---

### Task 2: Measure colours follow the mark on Modernize and Revert

S2-1, C1, Review Focus 1.

**Decision.**
- **Where it runs.** The measure seed moves each aggregate's default colour by palette position, on both branches. It lives in `seedColorPalette` (full seed), not in the per-type seed: it depends on the mark, not the type.
- **What it reaches.** It walks every `ChartAggregateRef` in `getBindingRefs(false)` and `getBindingRefs(true)`, which covers X, Y, group and the Gantt start, end and milestone fields. It reaches the colour frame only, not the waterfall summary frame (that is S2b).
- **The relation node frame** gets the same mapping.
- **The chart-level frame** is Task 6's.

**Files:**
- Modify: `uql/viewsheet/internal/ChartVSAssemblyInfo.java` (`seedColorPalette:266-288`)
- Test: `core/src/test/java/inetsoft/uql/viewsheet/internal/StaticColorSeedTest.java` (new)

**Interfaces:**
- Consumes: `reseatStaticColor` (Task 1).
- Produces: `private void seedMeasureColors(VizContext ctx)`, called from `seedColorPalette`. For each static colour frame, `wrapper.setDefaultColor(reseatStaticColor(wrapper.getDefaultColor(), ctx))`. It never touches `userColor`, `cssColor` or `changed`.

- [ ] **Step 1: Write the failing tests** in `StaticColorSeedTest` (Spring context):
- `aNewModernChartsMeasureTakesSlotOne`: gate on, bind BAR with one measure (default `#518DB9`), `seed`. Default `#0490FF`.
- `aSecondMeasureTakesSlotTwo`: two measures at `#518DB9` and `#B9DBF4`, Modernize. `#0490FF` and `#FF5A35`.
- `aSecondSlotMeasureComesBackAfterRevert`: a classic chart with one measure at `#B9DBF4`, Modernize (`#FF5A35`), then Revert. `#B9DBF4`.
- `anAuthorColourIsLeftAlone`: user colour `Color.RED`, default `#518DB9`, Modernize. User still `RED`, default `#0490FF`, `getColor()` `RED`.
- `aDarkMarkTakesTheDarkSlot`: Modernize with `VizMark.MODERN_DARK`. `#4FA5FF`.
- `runtimeAndGanttMeasuresMoveToo`: a runtime Y aggregate and a Gantt milestone measure are both mapped.
- `aClassicChartsXmlIsUnchanged`: gate off, two measures, `seed`. `writeXML` before equals after.
- `theSeededColourTravels`: Modernize, write the chart info XML, parse it into a fresh `DefaultVSChartInfo`. Default `#0490FF`.

- [ ] **Step 2: Run to verify they fail**

Run: `./mvnw -q test -pl core "-Dtest=StaticColorSeedTest" -Dsurefire.failIfNoSpecifiedTests=false`
Expected: the modern assertions fail (default still `#518DB9`).

- [ ] **Step 3: Implement** `seedMeasureColors` as under Interfaces, and call it from `seedColorPalette` before the aesthetic loop.

- [ ] **Step 4: Run to verify they pass**

Run: the Step 2 class plus `SeedChromeDefaultsTest,VizModernizeUtilTest,VizModernizeResidueTest,VizModernizeResetTest,ChartChromeSlotsTest`.
Expected: all pass. `VizModernizeUtilTest`'s P6 round trips still pass, because Revert maps back exactly.

- [ ] **Step 5: Commit**: `Chart defaults S2a, move a measure's default colour to the modern palette and back`.

---

### Task 3: Bind-time picks follow the mark

S2-1, C1, Review Focus 2.

**Files:**
- Modify: `report/composition/graph/GraphUtil.java:1049-1125`
- Modify: `web/binding/handler/ChartDndHandler.java`, i.e. `dropToXY:137`, `dropToChartView:352`, `dropToHighLow:563`, `replaceChartRef:1109`, `addChartRef:1144` and `fixStaticColorFrame:1193`
- Modify: `web/binding/handler/VSChartBindingHandler.java:218`, `:249`, `:285`
- Modify: `web/binding/controller/ChangeChartRefService.java:266-288`, `report/internal/graph/ChangeChartTypeProcessor.java:1034`, `web/vswizard/recommender/object/VSChartDefaultRecommendationFactory.java:113-118`
- Test: `core/src/test/java/inetsoft/report/composition/graph/StaticColorPickTest.java` (new)

**Interfaces:**
- Consumes: `staticPalette` (Task 1).
- Produces:
  - `GraphUtil.fixStaticColorFrame(Object ref, ChartInfo info, ChartAggregateRef movedRef, VizContext ctx)`
  - `GraphUtil.fixDuplicateColor(ChartAggregateRef ref, ChartRef[] arefs, ChartAggregateRef movedRef, boolean runtimeUpdate, VizContext ctx)`
  - Both pick from `staticPalette(ctx)`. The existing signatures delegate with `VizContext.LEGACY`, for the report paths.
  - `ChartDndHandler`'s public drop methods gain a `VizContext` overload (`dropToChartView` already has one), and the old signatures delegate with `LEGACY`.
  - `VSChartBindingHandler` passes `VizContext.of(info)`.
  - `ChangeChartRefService` passes the assembly's context, `ChangeChartTypeProcessor` its `vizContext` field, and the wizard factory the `ctx` it already holds (`:89`).

- [ ] **Step 1: Write the failing tests** in `StaticColorPickTest`:
- `aModernDropTakesTheNextModernColour`: an info with one measure at `#0490FF`; `fixStaticColorFrame(newRef, info, null, MODERN_LIGHT ctx)`. The new default is `#FF5A35`.
- `aDropSkipsTheAuthorsAndTheSeededColours`: measure 1 at default `#0490FF`, measure 2 with user colour `#FF5A35`; a third drop gets `#241C4F`.
- `aClassicDropIsUnchanged`: the same with `LEGACY` gives `#B9DBF4` after `#518DB9`.
- `theWizardSeedsTheMarksPalette`: `fixStaticColorFrame(List, null, null, MODERN_LIGHT)` over two measures gives `#0490FF` and `#FF5A35`.
- `theOldGreenStillShortCircuits`: a lone measure at `OLD_COLOR_PALETTE_DEFAULT_COLOR` is untouched under both contexts.

- [ ] **Step 2: Run to verify they fail**

Run: `./mvnw -q test -pl core "-Dtest=StaticColorPickTest" -Dsurefire.failIfNoSpecifiedTests=false`
Expected: compile failure (no 4-arg `fixStaticColorFrame`).

- [ ] **Step 3: Implement** as under Interfaces. The thread-through is mechanical. Keep the `OLD_COLOR_PALETTE_DEFAULT_COLOR` early return.

- [ ] **Step 4: Run to verify they pass**

Run: the Step 2 class plus `ChangeChartTypeCombinedTooltipTest,ChangeChartTypeProcessorMapTitleTest,SeededLinearFrameTest,ResolvedTypeReseedTest,VSChartBindingColorPalettesTest,WizardSeededLinearFrameTest`. No test class exists for `ChartDndHandler`, `VSChartBindingHandler` or `ChangeChartRefService`, and none is added: the picks are pinned through `GraphUtil`.
Expected: all pass.

- [ ] **Step 5: Commit**: `Chart defaults S2a, pick a dropped measure's colour from the chart's own palette`.

---

### Task 4: Render and model readers follow the mark

S2-1, C1, Review Focus 2.

**Files:**
- Modify: `report/composition/graph/VSColorFrameStrategy.java:96-140` and its constructor
- Modify: `report/composition/graph/VSFrameVisitor.java:196-199`
- Modify: `report/composition/graph/GraphGenerator.java:330` (strategy construction), `:5476-5506` (`isApplyColor`)
- Modify: `web/binding/service/ChartInfoModelBuilder.java:262-298`
- Test: `core/src/test/java/inetsoft/report/composition/graph/StaticColorPickTest.java`, `core/src/test/java/inetsoft/web/binding/service/ChartInfoModelBuilderColorChangedTest.java` (new)

**Interfaces:**
- Consumes: `staticPalette` (Task 1), the 5-arg `fixDuplicateColor` (Task 3).
- Produces:
  - `VSColorFrameStrategy(ChartInfo info, VizContext ctx)` and `getVizContext()`. The one-arg constructor uses `LEGACY`.
  - `createCombinedFrame` re-picks a duplicate from `staticPalette(ctx)`.
  - `GraphGenerator:330` passes `vizContext`, and `:483` (the legacy constructor) passes `VizContext.LEGACY`.
  - `VSFrameVisitor:198` passes the strategy's context.
  - `isApplyColor` compares with `staticPalette(vizContext)` at `[0]` and `[measureIdx % length]`.
  - `static void fixColorChanged(ChartBindingModel model, VizContext ctx)` (package-private) compares with `staticPalette(ctx)`. `createChartBinding` passes `getVizContext()`.

- [ ] **Step 1: Write the failing tests**
- In `StaticColorPickTest`:
  - `aRenderDoesNotRepickAModernColour`: two measures at `#0490FF` and `#FF5A35`; `createCombinedFrame` with a MODERN_LIGHT strategy keeps both.
  - `aRenderRepicksAModernDuplicateFromTheModernPalette`: both measures at `#0490FF` give `#0490FF` and `#FF5A35`.
- `ChartInfoModelBuilderColorChangedTest` (no Spring context beyond the palette lookup), calling `fixColorChanged` on a hand-built `ChartBindingModel` with `StaticColorModel`s:
  - `aSeededModernColourIsNotChanged`: `#0490FF` and `#FF5A35` under MODERN_LIGHT stay unchanged.
  - `anOffPaletteColourIsChanged`: `#123456` under MODERN_LIGHT is changed.
  - `aClassicLegacyColourIsNotChanged`: `#518DB9` under LEGACY stays unchanged.
- In `ChartChromeRenderTest`, a guard that passes before and after:
  - `aModernLineChartStillDarkensItsSeededColour`: a marked LINE with one measure. The element colour is `#0490FF` at brightness 0.75.

- [ ] **Step 2: Run to verify they fail.** Run the three classes. Expected: every modern assertion fails except the darkening guard.

- [ ] **Step 3: Implement** as under Interfaces.

- [ ] **Step 4: Run to verify they pass**

Run: the Step 2 classes plus `ImageShapesSharedTest,VGraphPairModernPaletteTest,CompanionBrushColorFrameTest,BrushedMarksTest` (those that exist).
Expected: all pass.

- [ ] **Step 5: Commit**: `Chart defaults S2a, compare and re-pick measure colours against the chart's own palette`.

---

### Task 5: Reset and Clear remove the author colour

O1, Review Focus 3.

**Decision.**
- **The server rule.** A static colour model that is not `changed` clears the user tier when its `color` equals the colour the frame shows without an author colour. That colour is the model's own `cssColor` when set, else its `defaultColor`, which are the values the server sent and the client sends back. The frame's default tier is left as the server holds it.
- **Why the rule needs both checks.**
  - A pick in the combined pane sets only `color` and never `changed`, so `changed` alone cannot mark a reset.
  - An author who picks exactly the default colour loses nothing that draws. 80398's residue rule treats that value the same way.
- **The client.**
  - The combined pane's Reset sends `cssColor || defaultColor` for every frame.
  - The single pane's Clear sends the same, in place of the org palette's first colour.

**Files:**
- Modify: `web/binding/service/graph/aesthetic/ColorFrameModelFactory.java:48-61`
- Modify: `web/projects/portal/src/app/binding/editor/chart/aesthetic/combined-color-pane.component.ts:44-57`, `static-color-pane.component.ts:38-42`
- Test: `core/src/test/java/inetsoft/web/binding/service/graph/aesthetic/StaticColorFrameFactoryTest.java` (new), `web/projects/portal/src/app/binding/editor/chart/aesthetic/combined-color-pane.spec.ts`, `static-color-pane.component.spec.ts` (new), `StaticColorSeedTest`

**Interfaces:**
- Produces: `StaticColorFrameFactory.updateVisualFrameWrapper0`:
  - When `!model.isChanged()` and `color` equals the model's non-empty `cssColor`, else its `defaultColor`, it calls `nwrapper.setUserColor(null)` and `setChanged(false)`. It does not write the default tier.
  - Otherwise it does what it does today.
  - The test follows the setup of `CategoricalColorFactoryResolvedDefaultGuardTest` in the same package.

- [ ] **Step 1: Write the failing tests**
- `StaticColorFrameFactoryTest`:
  - `aResetClearsTheAuthorColour`: wrapper with user `RED` and default `#518DB9`; model `color=#518DB9`, `defaultColor=#518DB9`, `changed=false`. The user colour becomes null.
  - `aResetHonoursTheStylesheetColour`: `cssColor=#123456`, model color `#123456`, not changed. The user colour is cleared.
  - `aPickIsStillTheAuthors`: model color `#00FF00`, not changed. User `#00FF00`.
  - `aPickedDefaultMarkedChangedStays`: model color `#518DB9`, changed. User `#518DB9`.
- In `StaticColorSeedTest`:
  - `resetThenRevertDrawsTheLegacyColour`: a marked chart, a measure with user `RED`; apply a reset model through the factory; Revert. `getColor()` is `#518DB9`.
- `combined-color-pane.spec.ts`: flip `should display refresh button on combined color pane`. Reset gives `#518db9` (the fixture's `defaultColor`) for the measure and `#7030a0` for the summary.
- New in `combined-color-pane.spec.ts`:
  - `reset prefers the stylesheet colour`: `cssColor: "#123456"` gives `#123456`.
- New spec `static-color-pane.component.spec.ts`:
  - `clear sends the default colour`: `clearColor("#0490ff")` with `defaultColor` `#518db9` sets `frame.color` to `#518db9` and `changed` to false.

- [ ] **Step 2: Run to verify they fail.** Run the Java classes and the portal spec. Expected: the clear and reset assertions fail.

- [ ] **Step 3: Implement** as under Decision and Interfaces.

- [ ] **Step 4: Run to verify they pass.** Same, plus `ChartChromeSlotsTest`. Expected: all pass.

- [ ] **Step 5: Commit** the Java and TypeScript changes together: `Chart defaults S2a, let Reset and Clear in the colour panes remove the author colour`.

---

### Task 6: The chart-level colour by resolved type

S2-1 (merged charts), S2-3, S2-4 (region), C2, C4, C5, C7, O2, O4, Review Focus 4.

**Decision.**
- **Where it runs.** The chart-level static frame (`getVSChartInfo().getColorFrameWrapper()`, only when it is a `StaticColorFrameWrapper`) is seeded in `seedTypeDefaults`, so a type or binding change re-runs it on a marked chart.
- **The polygon-only component.** `ResolvedChartType` gains `mapPolygonOnly`, so adding or removing a map's point layer re-runs the seed. A 5-arg constructor keeps existing callers, with `mapPolygonOnly = false`.
- **What `fixMapColorFrame` does around it.** It runs inside `VSMapInfo.update` without a context. It writes only when the colour is legacy slot 1 or the property is set, so it never undoes a modern value except under O4.
- **Map with points, and contour map.** Their region colour is drawn at render (C5): `MapGenerator` reads `GraphUtil.getMapDefaultColor(vizContext)`.

**Files:**
- Modify: `uql/viewsheet/internal/ResolvedChartType.java`, `ChartVSAssemblyInfo.java` (`seedTypeDefaults:3123-3130`)
- Modify: `report/composition/graph/GraphUtil.java:302-324`, `MapGenerator.java:489-492`
- Test: `core/src/test/java/inetsoft/uql/viewsheet/internal/ChartColorTypeSeedTest.java` (new), `core/src/test/java/inetsoft/report/composition/graph/ChartColorRenderTest.java` (new)

**Interfaces:**
- Consumes: Task 1's `reseatStaticColor`, `mapRegionColor`, `isModernChartColor`.
- Produces:
  - `ResolvedChartType.mapPolygonOnly()`: `GraphTypeUtil.isMap(info) && GraphUtil.containsMapPolygon(info) && !GraphUtil.containsMapPoint(info)`.
  - `GraphUtil.getMapDefaultColor(VizContext ctx)`: the property when set, else `mapRegionColor(ctx)` when `ctx.modern`, else `MAP_DEFAULT_COLOR`. The no-arg form stays `LEGACY`.
  - `private void seedChartColor(VizContext ctx, ResolvedChartType type)` in `ChartVSAssemblyInfo`, called from `seedTypeDefaults`. It writes the frame's default tier:

| Branch | Resolved type | Value written |
|---|---|---|
| modern | relation (`GraphTypes.isRelation`, i.e. tree, network, circular) | `VSChartChromeDefaults.axisLineColor(ctx)` |
| modern | `type.mapPolygonOnly()` | `GraphUtil.getMapDefaultColor(ctx)` |
| modern | any other | `reseatStaticColor(current, ctx)` when `current` is a palette member, else `activePalette(ctx)[0]` when `isModernChartColor(current)` or `current` equals `MAP_DEFAULT_COLOR`, else unchanged |
| legacy | any | only when `isModernChartColor(current)`: `GraphUtil.getMapDefaultColor()` for a polygon-only map; `reseatStaticColor(current, LEGACY)` for a palette member; `COLOR_PALETTE.get(0)` otherwise |

- [ ] **Step 1: Write the failing tests**
- In `ChartColorTypeSeedTest`:
  - `aModernTreeHasGreyEdges`: bind `CHART_TREE`, gate on, `seed`. Default `#85827A` (`#7E7B86` dark).
  - `aModernRadarTakesSlotOne`: `#0490FF`.
  - `aTreeConvertedToRadarTakesSlotOne`: marked tree, change type to RADAR, `reseedIfResolvedTypeChanged()`. `#0490FF`.
  - `aModernPolygonMapTakesTheTint`: `#A6D0F0`.
  - `addingPointsMovesTheChartColourToSlotOne`: add a point geo field, `reseedIfResolvedTypeChanged()`. `#0490FF`. Remove it: `#A6D0F0`.
  - `aSetMapPropertyBeatsTheTint`: `map.default.color` set to `#112233`. A polygon-only map seeds `#112233`.
  - `revertRestoresTheClassicColours`: a tree goes back to `#518DB9`; a polygon map goes to `#FBD29A`.
  - `anAuthorEdgeColourIsLeftAlone`: user `RED` on a tree, Modernize then Revert. User `RED`.
  - `aClassicMapsXmlIsUnchanged`: a classic polygon map whose default is `#518DB9` (an old file), gate off, `seed`. The XML is unchanged.
- In `ChartColorRenderTest`:
  - `aModernMapWithPointsDrawsTheTintedRegions`: the polygon `GeoColorFrame` default colour is `#A6D0F0`.
  - `aClassicMapWithPointsKeepsThePaleOrange`: `#FBD29A`.

- [ ] **Step 2: Run to verify they fail.** Run both classes. Expected: compile failure on `mapPolygonOnly`, then the colour assertions.

- [ ] **Step 3: Implement** as under Interfaces. `MapGenerator:489-492` calls `GraphUtil.getMapDefaultColor(vizContext)`.

- [ ] **Step 4: Run to verify they pass**

Run: the Step 2 classes plus `ResolvedTypeReseedTest,ChartChromeTypeSeedTest,VSChartTypeDefaultsTest,PerTypeSeedSharedDescriptorTest,WizardSyncReseedTest,VizModernizeUtilTest`.
Expected: all pass.

- [ ] **Step 5: Commit**: `Chart defaults S2a, seed the chart-level colour by type: grey relation edges, tinted map regions, slot one elsewhere`.

---

### Task 7: The map's no-data colour

S2-4 (unmatched), C5, O4, Review Focus 5.

**Files:**
- Modify: `uql/CompositeValue.java` (add `hasCssValue()`), `uql/viewsheet/graph/PlotDescriptor.java` (`savedDefaults:1918-1921`, `chromeValue:1875-1896`)
- Modify: `uql/viewsheet/internal/ChartChromeSlots.java:76-79`, `ChartVSAssemblyInfo.java` (`seedChromeDefaults`, both branches)
- Modify: `report/composition/graph/GraphUtil.java:330-353`
- Test: `ChartColorTypeSeedTest`, `ChartColorRenderTest`, `ChartChromeSlotsTest`; `ChartDescriptorSavedDefaultsTest` covers registration

**Interfaces:**
- Produces:
  - `CompositeValue.hasCssValue()`: returns `cssDefined`.
  - `PlotDescriptor.getAuthoredEmptyColor()`: `emptyColor.get(USER)` whenever `hasUserValue()`, even when that value is null; else `emptyColor.get(CSS)` when `hasCssValue()`; else null. The CSS tier is read only when the user tier is undefined.
  - `PlotDescriptor.getEmptyColor(CompositeValue.Type type)`: `emptyColor.get(type)`.
  - `"emptyColor"` in `chromeValue` and `PLOT_VALUES`; `emptyColor` in `savedDefaults()`.
  - The seed writes `setEmptyColor(gridlineColor(ctx), DEFAULT)` when modern and `setEmptyColor(null, DEFAULT)` on every legacy pass. Null is the constructor value, so the property lookup runs again at render.
  - `GraphUtil.getMapEmptyColor(PlotDescriptor)` reads in this order: `getAuthoredEmptyColor()`, then the `map.empty.color` property, then `emptyColor.get(DEFAULT)`, then `MAP_EMPTY_DATA_COLOR`. A classic chart's DEFAULT is null, so its order is today's.

- [ ] **Step 1: Write the failing tests**
- `aModernMapsNoDataColourIsTheGridColour`: the DEFAULT tier is `#D6D2C8`, `#4A484F` dark.
- `revertClearsTheSeededNoDataColour`: DEFAULT null.
- `theServerPropertyBeatsTheSeededNoDataColour`: in `ChartColorRenderTest`, `map.empty.color` set to `#123456` on a modern map gives `getMapEmptyColor` `#123456`.
- `anAuthorNoDataColourBeatsTheProperty`: user `RED` with the property set gives `RED`.
- `aClassicMapStillReadsTheProperty`: gate off, property set, gives the property colour.
- In `ChartChromeSlotsTest`: an author `emptyColor` equal to the classic render is residue.

- [ ] **Step 2: Run to verify they fail.** Expected: the modern seed and order assertions fail.

- [ ] **Step 3: Implement** as under Interfaces.

- [ ] **Step 4: Run to verify they pass.** Run the Step 2 classes plus `ChartDescriptorSavedDefaultsTest,ChartPlotOptionsPaneModelTest,SeedChromeDefaultsTest`. Expected: all pass. `emptyColor` is the first registered field whose legacy value is null. If `ChartDescriptorSavedDefaultsTest`'s generic round trip assumes a non-null legacy value, give that field its own case rather than changing the legacy value.

- [ ] **Step 5: Commit**: `Chart defaults S2a, draw a modern map's no-data regions in the grid colour unless the server sets one`.

---

### Task 8: The pareto line

S2-2, C3, O3.

**Files:**
- Modify: `uql/viewsheet/graph/PlotDescriptor.java` (registration), `uql/viewsheet/internal/ChartChromeSlots.java:76-79`, `ChartVSAssemblyInfo.java` (`seedChromeDefaults`)
- Modify: `report/composition/graph/GraphGenerator.java:3383-3413`, `:3753-3760`, `report/composition/graph/GraphDefault.java:78-104`
- Modify: `web/graph/model/dialog/ChartPlotOptionsPaneModel.java:45`, `:97`, `:146-148`, `web/composer/model/vs/ChartAdvancedPaneModel.java:64`
- Test: `ChartChromeRenderTest` (extend `paretoGraph`), `ChartColorTypeSeedTest`, `core/src/test/java/inetsoft/web/graph/model/dialog/ChartPlotOptionsPaneModelTest.java`

**Interfaces:**
- **Seed and registration.**
  - Modern writes `setParetoLineColor(activePalette(ctx)[1], DEFAULT)`. Legacy writes `CategoricalColorFrame.COLOR_PALETTE.get(0)`, the constructor value.
  - `"paretoLineColor"` joins `chromeValue` and `PLOT_VALUES`, and `paretoLineColor` joins `savedDefaults()`.
- **The line colour (`getParetoColorFrame`).** When `vizContext.modern` and the frame is not a brushing source or target, the `StaticColorFrame` and `MultiMeasureColorFrame` branches return `desc.getPlotDescriptor().getParetoLineColor()`.
- **The line width.** When `vizContext.modern`: `line.setSizeFrame(new StaticSizeFrame(2))`, which draws 1.5px through `LineVO.getLineSize:1014-1016`.
- **The percent labels.** In `GraphDefault.setScaleOptions`, when `gen.vizContext.modern`: `percentScale.getAxisSpec().getTextSpec().setColor(gen.desc.getPlotDescriptor().getParetoLineColor())`. This reads the descriptor rather than the built line, so it does not depend on whether the elements exist yet. `desc` is `protected` and in the same package.
- **The picker.** `ChartPlotOptionsPaneModel(ChartInfo, PlotDescriptor, VizContext)` and `isParetoLineColorVisible(ChartInfo, VizContext)` return `isPareto && (colorField != null || ctx.modern)`. The two-arg forms use `LEGACY`. `ChartAdvancedPaneModel` passes `VizContext.of(chartAssemblyInfo)`.

- [ ] **Step 1: Write the failing tests**
- In `ChartChromeRenderTest`:
  - `aModernParetoLineTakesSlotTwo`: the `_pareto_` line element's colour is `#FF5A35`.
  - `aModernParetoLineIsOneAndAHalfPixels`: its size frame is a `StaticSizeFrame` of 2.
  - `aModernPercentAxisTakesTheLineColour`: `yscale2`'s text colour is `#FF5A35`.
  - `anAuthorLineColourWins`: user `RED` gives `RED` on both.
  - `aClassicParetoLineStillCopiesTheBar`: the line colour equals the bar colour, there is no size frame, and the text colour is the y axis's.
- In `ChartColorTypeSeedTest`:
  - `theParetoLineColourTravelsAndReverts`: written at DEFAULT and saved on a marked chart; Revert gives `#518DB9`.
- In `ChartPlotOptionsPaneModelTest`:
  - `aModernParetoShowsTheLinePicker`, and `aClassicParetoWithoutAColourFieldHidesIt`.

- [ ] **Step 2: Run to verify they fail.**

- [ ] **Step 3: Implement** as under Interfaces.

- [ ] **Step 4: Run to verify they pass.** Run the Step 2 classes plus `ChartDescriptorSavedDefaultsTest,ChartChromeSlotsTest`. Expected: all pass; S1's pareto tick tests still pass.

- [ ] **Step 5: Commit**: `Chart defaults S2a, draw the modern pareto line in slot two at one and a half pixels`.

---

### Task 9: Scatter contour gets the teal ramp

S2-5, C6.

**Files:**
- Modify: `report/internal/graph/ChangeChartTypeProcessor.java:1628-1635`, `uql/viewsheet/internal/ChartVSAssemblyInfo.java` (`seedColorPalette:266-288`)
- Test: `core/src/test/java/inetsoft/report/internal/graph/SeededLinearFrameTest.java`, `ChartColorTypeSeedTest`

**Interfaces:**
- **The type picker.** `copyToContour` installs `vizContext.modern ? new TealColorFrameWrapper() : new BluesColorFrameWrapper()`, the wrapper of `defaultLinearFrame(vizContext)`. The branch that keeps an existing linear wrapper is unchanged.
- **The transition seed.** `seedColorPalette` calls `seedColorFrame`'s linear branch on the chart-level frame too. On `ctx.transition && isOtherMarkSeededLinearFrame(info.getColorFrame(), ctx)` it calls `info.setColorFrame(defaultLinearFrame(ctx))`.

- [ ] **Step 1: Write the failing tests**
- In `SeededLinearFrameTest`:
  - `aModernBarChangedToScatterContourIsTeal`
  - `aClassicOneIsBlues`
- In `ChartColorTypeSeedTest`:
  - `modernizeTurnsAContoursBluesTeal` and `revertTurnsItBack`
  - `anAuthorsSpectralContourIsKept`

- [ ] **Step 2: Run to verify they fail.**

- [ ] **Step 3: Implement** as under Interfaces.

- [ ] **Step 4: Run to verify they pass.** Run the Step 2 classes plus `WizardSeededLinearFrameTest,ChartVSAssemblyInfoSeedTest,ChartChromeSlotsTest`. Expected: all pass.

- [ ] **Step 5: Commit**: `Chart defaults S2a, give a modern scatter contour the teal ramp from the type picker and on Modernize`.

---

### Task 10: Size-legend symbols in the chrome neutral

S2-8, C8.

**Files:**
- Modify: `graph/LegendSpec.java` (fields, accessors, clone, `equals`/`hashCode` `:393-414`)
- Modify: `graph/guide/legend/Legend.java:702-707`, `graph/guide/legend/SizeLegendItem.java:67`, `report/composition/graph/GraphGenerator.java:1842-1877`
- Test: `ChartColorRenderTest`, `core/src/test/java/inetsoft/graph/guide/legend/SizeLegendSymbolTest.java` (new)

**Interfaces:**
- `LegendSpec.getSymbolFallbackColor()` / `setSymbolFallbackColor(Color)` and `getSymbolBorderColor()` / `setSymbolBorderColor(Color)`. Both default to null, which means today's behaviour.
- `Legend.createItems` uses the fallback when `itemColor == null` and the fallback is set.
- `SizeLegendItem.paintSymbol` draws its box in the border colour when it is set, else in `SYMBOL_BORDER`.
- `processSizeLegend`, when `vizContext.modern`, sets the fallback to `labelColor(vizContext)` and the border to `gridlineColor(vizContext)`. The border applies whether or not the element supplies a colour.

- [ ] **Step 1: Write the failing tests**
- `SizeLegendSymbolTest`:
  - `anUnsetSpecKeepsTheOldColours`: the item's symbol colour is `SYMBOL_COLOR`.
  - `aSetFallbackColoursTheSymbol`
  - `aSetBorderDrawsTheBox`: paint into a `BufferedImage` and sample the box edge.
- In `ChartColorRenderTest`:
  - `aModernSizeLegendNextToAColourFieldUsesTheLabelGrey`: a point chart with a categorical colour field and a size measure. The size `LegendSpec` fallback is `#6A685F` and the border `#D6D2C8`.
  - `aClassicOneIsUnset`

- [ ] **Step 2: Run to verify they fail.**

- [ ] **Step 3: Implement** as under Interfaces.

- [ ] **Step 4: Run to verify they pass.** Run the Step 2 classes plus `LegendContentInsetTest,LegendItemLabelAlignmentTest,VGraphPairLegendLayoutTest`. Expected: all pass.

- [ ] **Step 5: Commit**: `Chart defaults S2a, draw modern size-legend symbols in the chrome label grey`.

---

### Task 11: Whole-branch check

- [ ] **Step 1:** Run the core suite: `./mvnw -q test -pl core -Dsurefire.failIfNoSpecifiedTests=false`. Expected: 0 failures. Compare any failure against `bug-80455-s1` before blaming this branch.
- [ ] **Step 2:** Run the portal suite: `npx ng test portal` from `$WT/web`. Expected: the counts on `bug-80455-s1` plus this branch's new specs, and no new failures.
- [ ] **Step 3: Manual** (record in the PR description). On a built server, use the bundle's repro dashboards (`chart-defaults-design/repro/charts.md`) in light and dark:
  - a single-measure bar, line and point
  - a two-measure bar
  - a tree
  - a polygon map, and one with points
  - a pareto
  - a scatter contour from the type picker
  - a point chart with colour and size

  For each: Modernize, then Revert, then Modernize with the reset copy. Then Reset a measure colour in the binding pane and Revert.

## Interfaces for later slices

- **Static colours.**
  - `VSChartPaletteDefaults.staticPalette(ctx)` is the one list any measure pick uses.
  - `reseatStaticColor(color, ctx)` moves a default colour between marks.
  - `isModernChartColor(color)` tells the legacy pass whether a chart-level default is the seed's.
  - S2b's direction colours (candle, stock, waterfall) take over the chart-level frame for those types in `seedChartColor`, and the summary frame (`scframe`) for the waterfall Total.
- **Reset.** A not-`changed` static model whose colour equals its CSS-or-default colour clears the user tier. S2b's up and down colour models get this through the same factory.
- **Map region colour.** `GraphUtil.getMapDefaultColor(ctx)` is the one modern-aware source.
