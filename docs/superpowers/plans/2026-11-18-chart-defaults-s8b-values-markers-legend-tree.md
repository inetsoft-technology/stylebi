# Chart Defaults S8b — Values, Filled Markers, Size Legend and Tree Boxes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** On a modern (marked) chart:
- funnel stages and waterfall bars show their values inside the mark, only where they fit, and the funnel draws no grid
- shape encodings use filled markers
- the treemap family hides its size legend
- tree boxes are a light tint of the node colour with a full-colour outline and the title ink

Classic charts are unchanged.

**Architecture:** Two stored defaults with author flags (values shown, size legend visibility), one stored list chosen by comparison (shape lists), and drawing rules in the graph package (value fit, tree tint).
- **Seeds** (`ChartVSAssemblyInfo.seedTypeDefaults` and `seedChromeDefaults`): `seedValuesVisible`, `seedYGrid`, `seedSizeLegend`, `seedShapeLists`.
- **Flags:** `PlotDescriptor.userValuesVisible` and `LegendDescriptor.userVisible`, both through `DerivedUserFlag`, stamped by author setters.
- **Graph package:** `GraphElement.valueFitInside` (read by `BarVO`), `RelationElement` box tint and ink (read by `RelationVO`).
- **Generators:** `GraphGenerator` turns on the value fit, placement and automatic colour for marked funnels and waterfalls, the shape legend's fallback symbol colour, and the tree tint.
- **Editors:** the Shape pane's Reset follows the mark through `ChartBindingModel.vizModern`; the wizard writes a filled point on marked dashboards.

**Tech Stack:** Java 21, JUnit 5, Spring test context (`@SreeHome`), Maven module `core`; Angular 21 and Vitest for the Shape pane.

**Spec:** `docs/superpowers/specs/lookfeel/2026-11-06-chart-defaults-design.md` (S8 amended in `005c97e412`). The relevant parts are:
- §3 M2 to M5 (kinds, travel, the author wins, one hook) and M9
- §5 S8: rows S8-3 to S8-6 and the sections "S8-3 funnel and waterfall values", "S8-4 filled markers", "S8-5 size legend" and "S8-6 tree boxes"
- §9's S8b items, and §11 "Family items (S8)" for S8-3 to S8-6
- §10 D129, D136 to D142

**Builds on:** S8a (`bug-80455-s8` at `073b761682`). Uses `VSChartChromeDefaults.titleColor(VizContext)`, `labelColor(VizContext)`, `gridlineColor(VizContext)`, `VSObjectChromeDefaults.cardComposite(VizContext)`, `LegendSpec.setSymbolFallbackColor` (S2-8), `DerivedUserFlag`, `VSChartTypeDefaults`, `ResolvedChartType`.

**Verified against:** `bug-80455-s8` at `073b761682`. Paths are under `core/src/main/java/inetsoft/` unless they start with `core/` or `web/`.

## Corrections to the spec

| # | Spec | Code at `073b761682` | This plan |
|---|---|---|---|
| C1 | S8-3: the values seed "re-runs when a Text binding changes" | `reseedIfResolvedTypeChanged` (`ChartVSAssemblyInfo:3561-3584`) already re-runs the legend seeds when `VSChartTypeDefaults.legendBindingKey` changes, and that key includes the Text field | `seedValuesVisible` joins that binding-key branch; no new hook |
| C2 | S8-3: "The funnel's value-axis grid (`yGridStyle`) is seeded NONE in the DEFAULT tier" | `PlotDescriptor.savedDefaults()` (`:1974-1977`) lists `xGridStyle` but not `yGridStyle`, so a DEFAULT-tier `yGridStyle` would not travel (M3) | `yGridStyle` joins `savedDefaults()`; the seed mirrors `seedXGrid` (`:3432-3446`) |
| C3 | S8-4: "The Shape pane's Reset gives the modern list on a marked chart" | The pane resets to `ChartConfig.SHAPE_STYLES.concat(ChartConfig.IMAGE_SHAPES)` (`categorical-shape-pane.component.ts:52-56`), which is not the server's 30-entry default (`CategoricalShapeFrame()`, `:46-57`) | Each side has two recognised defaults: the server default and the pane's Reset list. The seed swaps classic server ↔ modern server and classic Reset ↔ modern Reset |
| C4 | S8-4 / §9: "how the client learns the mark for the Shape pane's Reset" | `ChartBindingModel` has no mark on this branch; S7 (`95c54a4e6b`, not merged here) adds `vizModern` to it, set in `VSChartBindingFactory` with `VizContext.of(info).modern`, and `vizModern?: boolean` in `chart-binding-model.ts` | Add the same field with the same lines, so the later merge with S7 sees identical changes |
| C5 | S8-5: "Hide Legend, Show Legend and Show All Legends … stamp the flag" | `VSChartLegendsVisibilityService.hideDescriptorLegends` (`:186-193`) and `showAllDescriptorLegends` (`:256-305`) call `setVisible` / `setMaxModeVisible` directly | New `LegendDescriptor.setAuthorVisible` / `setAuthorMaxModeVisible` stamp the flag; the services call them |
| C6 | S8-4: "the wizard writes a filled circle for an unbound point" | `ChartTypeFilter.setVizContext` (`:78`) already gives the wizard filters the mark; `PointChartFilter:98` and `ScatterChartFilter:97` pick `GShape.CIRCLE` | They pick `GShape.FILLED_CIRCLE` when the context is modern |

**Design calls (spec silent or left to the builder):**
- **Reset copy and the size legend:** the reset copy clears the size-legend flag, as R8 and D37 clear other visibility flags. The values flag is kept (D136).
- **Size-legend stampers:** the date-comparison sync (`ChartDcProcessor.syncLegendVisible`) and "no legend" (`LegendsDescriptor` layout) do not stamp the size-legend flag; they are not author edits of that legend.
- **Value fit inset:** 3px on each side of the label box, in screen pixels.
- **Tint arithmetic:** `tint = c × 0.15 + composite × 0.85` per channel, opaque.
- **Highlighted tree nodes:** a node whose colour comes from a highlight draws solid with the automatic ink (§9 leaves how the VO knows to the builder).

**Known limitations:**
- **Older builds** draw values off, outlined shapes, the size legend and solid tree boxes, except that `valuesVisible`, the size legend's `visible` and the shape list are stored and travel (M9).
- **A shape list the author picked that happens to equal a default** is swapped like a default.
- **Funnel stages share one height**, so the 18px height test passes or fails for the whole funnel (spec S8-3).

## Global Constraints

- **Worktree:** `$WT` is the executor's worktree on branch `bug-80455-s8`, at or after this plan's commit. Never work in `E:/StyleBI/stylebi-enterprise/community` or any other worktree.
- **Shared machine.** Never run `mvnw install`, `mvnw package -pl server`, `spring-boot:run`, or anything else that writes outside `$WT`. Never start a server or bind or call ports 8080, 8081 or 5005. Run only the test classes a step names (Task 7 names packages).
- **Git:** each git command is its own call, `git -C "$WT" ...`, with no `&&`. One commit per task, subject `Chart defaults S8, <what changed>`, last line `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`. Subagents never run `git add`, `git commit` or `git stash`; the controller commits. Never push.
- **Scope is the mark (M1).** A classic chart is pixel-identical, and its XML is unchanged.
- **The author wins (M4).** A seed writes a plain field only while its flag is false; a flag is stamped only by a real author change.
- **Values (all under the mark):**
  - **Values seed (D136):** `valuesVisible` true for funnel and waterfall (`GraphTypes.isFunnel`, `isWaterfall` on the resolved type) while `info.getTextField() == null`; false for every other type, for a funnel or waterfall with a Text field, and on the legacy branch.
  - **Values flag:** `PlotDescriptor.userValuesVisible`; derived on parse as `valuesVisible == true`.
  - **Value fit (D129, D137):** a label shows only when its box plus 3px on each side fits inside its mark in both directions; values centred inside (waterfall Total included); colour by `GTool.getTextColor` on the fill; an author's text colour, a CSS colour or a highlight colour wins. Not applied while a Text field is bound.
  - **Funnel grid (D138):** `yGridStyle` NONE in the DEFAULT tier for the funnel; `StyleConstants.THIN_LINE` for every other type and on the legacy branch.
  - **Filled list (D139):** `FILLED_CIRCLE, FILLED_TRIANGLE, FILLED_SQUARE, FILLED_DIAMOND, CIRCLE, TRIANGLE, SQUARE, CROSS, STAR, DIAMOND, XSHAPE`, repeating to 30 entries (all `GShape`).
  - **Shape legend (D140):** the same symbol fallback colour and border S2-8 gives the size legend.
  - **Size legend (D141):** hidden on `GraphTypes.isTreemap` resolved types (treemap, sunburst, circle packing, icicle), shown on every other type, both `visible` and `maxModeVisible`; derived on parse as `visible == false || maxModeVisible == false`.
  - **Tree boxes (D142):** marked `CHART_TREE` only; alpha `0.15` over `VSObjectChromeDefaults.cardComposite(ctx)` (`#ECEBE9` light, `#313034` dark); 1px outline in the node colour; ink `VSChartChromeDefaults.titleColor(ctx)` (`#35342F` light, `#E6E0E9` dark) unless the author set a text colour. A measure on node colour (`LinearColorFrame`) and highlighted nodes stay solid.
- **Comments:** a short clause each, with no ticket, PR, slice or design-doc names. No comments in Angular templates.
- **New files** start with the AGPL header of `SeedChromeDefaultsTest.java`, `Copyright (C) 2026  InetSoft Technology`.
- **Tests that reach Spring beans** use `@ExtendWith(SpringExtension.class)`, `@ContextConfiguration(classes = BaseTestConfiguration.class, initializers = ConfigurationContextInitializer.class)`, `@SreeHome`, `@Tag("core")`, and clear `viewsheet.modernVisualization`, `viewsheet.darkMode` and `viewsheet.density` in `@AfterEach`.

## Review Focus

1. **Classic charts are untouched:** values stay off, no value fit, outlined shapes, the size legend shown, solid tree boxes, unchanged XML. Pinned by the `classic…` cases in every task.
2. **A funnel or waterfall with a Text field:** no seeded values, and its text labels keep today's placement and colour on narrow bars. Pinned by `aTextFieldGetsNoSeededValues` (Task 1) and `textLabelsKeepTodaysPlacement` (Task 2).
3. **An author's choice survives:** Show Values off through Modernize, Revert and the reset copy; Show Legend on a treemap through a re-seed. Pinned by `anAuthorsChoiceSurvives` (Task 1) and `anAuthorsShownSizeLegendSurvives` (Task 5).
4. **An author's shape list is never swapped, and the pane's Reset round-trips:** pinned by `anAuthorsListIsKept` and `thePanesResetListSwapsBothWays` (Task 3).
5. **A tree coloured by a measure or a highlight stays solid:** pinned by `aMeasureOrHighlightStaysSolid` (Task 6).

## File Structure

- **Task 1:** `uql/viewsheet/graph/PlotDescriptor.java` (flag, author setter, parse/write, `equals`, `savedDefaults`), `uql/viewsheet/internal/VSChartTypeDefaults.java` (`valuesVisible`, `yGridStyle`), `uql/viewsheet/internal/ChartVSAssemblyInfo.java` (`seedValuesVisible`, `seedYGrid`, `reseedIfResolvedTypeChanged`), `web/graph/model/dialog/ChartPlotOptionsPaneModel.java` (`:202`), `report/script/ChartProcessor.java` (`:139-140`, `:420-421`).
- **Task 2:** `graph/GraphElement.java` (`valueFitInside`), `graph/visual/BarVO.java` (`layoutText0`, about `:667-740`), `report/composition/graph/GraphGenerator.java` (funnel at `:4049-4054`, the waterfall element, `setAutoTextColor` at `:4621`).
- **Task 3:** `report/composition/graph/ModernShapes.java` (new, package `inetsoft.report.composition.graph` or `uql.viewsheet.internal`, the implementer's choice by import direction), `uql/viewsheet/internal/ChartVSAssemblyInfo.java` (`seedShapeLists` in `seedChromeDefaults` next to `seedColorPalette`), `report/composition/graph/GraphUtil.java` (`fixVisualFrame` shape branch, `:985-991`), `web/vswizard/recommender/chart/PointChartFilter.java` (`:98`), `ScatterChartFilter.java` (`:97`), `report/composition/graph/GraphGenerator.java` (shape legend spec).
- **Task 4:** `web/binding/model/ChartBindingModel.java`, `web/binding/service/VSChartBindingFactory.java`, `web/projects/portal/src/app/binding/data/chart/chart-binding-model.ts`, `web/projects/portal/src/app/binding/editor/chart/aesthetic/categorical-shape-pane.component.ts`, `web/projects/portal/src/app/common/util/chart-config.ts` (or wherever `ChartConfig.SHAPE_STYLES` lives).
- **Task 5:** `uql/viewsheet/graph/LegendDescriptor.java` (flag, author setters, parse/write, `equals`), `uql/viewsheet/internal/ChartVSAssemblyInfo.java` (`seedSizeLegend`), `web/viewsheet/controller/chart/VSChartLegendsVisibilityService.java`, the `ChartVSAScriptable` "Size Legend" action (`:510-528`), `web/graph/GraphBuilder.java` (`isLegendHidden:1843`), `web/vswizard/handler/VSWizardBindingHandler.java` (`reloadLegendVisible:1298`), the reset-copy slot list (`ChartChromeSlots`).
- **Task 6:** `graph/element/RelationElement.java` (tint and ink, `equalsContent`), `graph/visual/RelationVO.java` (`paint`, about `:120-175`; label colour, about `:60-70`), `report/composition/graph/GraphGenerator.java` (the relation element setup, about `:1432-1600`).
- **Task 7:** none (whole-branch check and live-check handoff).

**Test harnesses:** as in the S8a plan. Seed tests copy `core/src/test/java/inetsoft/uql/viewsheet/internal/SeedChromeDefaultsTest.java` (seed a `ChartVSAssemblyInfo` with a `VizMark`); generator tests copy `ModernRadarLabelTest.graph(...)`; graph tests lay out with `Plotter.getPlotter(graph).plotAndLayout(...)`.

## How to run tests

From `$WT`: `./mvnw -q test -pl core "-Dtest=ClassA,ClassB" -Dsurefire.failIfNoSpecifiedTests=false`; read `core/target/surefire-reports/*.txt`; run every new or changed class alone once. Portal, from `$WT/web`: `npx ng test portal --include=projects/portal/src/app/<path>.spec.ts` (the `projects/portal/` prefix is required).

---

### Task 1: Values shown and the funnel grid as stored defaults (S8-3, D136, D138)

**Files:**
- Modify: `uql/viewsheet/graph/PlotDescriptor.java`, `uql/viewsheet/internal/VSChartTypeDefaults.java`, `uql/viewsheet/internal/ChartVSAssemblyInfo.java`, `web/graph/model/dialog/ChartPlotOptionsPaneModel.java`, `report/script/ChartProcessor.java` (`:139-140`, `:420-421`)
- Test: `core/src/test/java/inetsoft/uql/viewsheet/internal/ValuesVisibleSeedTest.java` (new, Spring)

**Interfaces:**
- Produces:
  - `PlotDescriptor.setAuthorValuesVisible(boolean)` (sets the value and the flag), `isUserValuesVisible()` / `setUserValuesVisible(boolean)`; parse/write through `DerivedUserFlag` as `userValuesVisible` (precedent: `userNodeCornerRadius`, `:1332-1360`, `:1593`, `:1752`); joins `equals`.
  - `PlotDescriptor.savedDefaults()` gains `yGridStyle`.
  - `static boolean VSChartTypeDefaults.valuesVisible(ResolvedChartType t, boolean textBound)`; `static int VSChartTypeDefaults.yGridStyle(ResolvedChartType t)`.
  - Private `ChartVSAssemblyInfo.seedValuesVisible(VizContext, ResolvedChartType)` and `seedYGrid(VizContext, ResolvedChartType)`, both writing the design and runtime descriptors as `seedXGrid` / `seedSmoothLines` do.

- [ ] **Step 1: Write the failing tests** (`ValuesVisibleSeedTest`)
  - `aMarkedFunnelAndWaterfallShowValues`: seeded with `VizMark.MODERN_LIGHT` → `isValuesVisible()` true on a funnel and a waterfall; false on a bar.
  - `aTextFieldGetsNoSeededValues`: a marked waterfall with a Text field → false; removing the Text field and calling `reseedIfResolvedTypeChanged()` → true.
  - `aTypeChangeFollows`: a marked funnel changed to a bar → false.
  - `anAuthorsChoiceSurvives`: `setAuthorValuesVisible(false)` on a marked waterfall, then Modernize-style re-seed, the legacy seed (Revert) and the modern seed again → still false; and the reset copy (as `VizModernizeResetTest` drives it) → still false with the flag kept.
  - `aCloneKeepsTheFlag`: `PlotDescriptor.clone()` (what `SyncChartHandler` copies) keeps `isUserValuesVisible()`.
  - `theLegacyBranchTurnsSeededValuesOff`: a marked funnel seeded on the legacy branch → false.
  - `aStoredTrueIsTheAuthors`: parse a plot descriptor XML with `valuesVisible` true and no `userValuesVisible` attribute → `isUserValuesVisible()` true; with false → false.
  - `thePaneStampsOnlyAChange`: `ChartPlotOptionsPaneModel` applied with the current value → flag stays false; with a different value → flag true.
  - `theScriptStamps`: the script properties `valueVisible` and `value.visible` set the flag.
  - `theFunnelGridIsOff`: a marked funnel → `getYGridStyle()` NONE, a marked bar → THIN; the DEFAULT tier is written and saved on the marked chart (`savedDefaults` includes it), and a classic chart's XML has no `yGridStyle` default written.
  - `classicChartsAreUnchanged`: no mark → `valuesVisible` false, `yGridStyle` THIN, the flag false, and the XML equal to a descriptor never seeded.

- [ ] **Step 2: Run them and see them fail**

Run: `./mvnw -q test -pl core "-Dtest=ValuesVisibleSeedTest" -Dsurefire.failIfNoSpecifiedTests=false`
Expected: compile failure on `setAuthorValuesVisible`.

- [ ] **Step 3: Implement**
  - **Seeds:** call both from `seedTypeDefaults`; call `seedValuesVisible` also in the binding-key branch of `reseedIfResolvedTypeChanged` (C1).
  - **Pane:** `ChartPlotOptionsPaneModel:202` calls `setAuthorValuesVisible(showValues)` only when it differs from `isValuesVisible()`; otherwise leaves it.
  - **Script:** the two `addProperty` calls name `setAuthorValuesVisible` as their setter, as `ChartProcessor:254` does for the node corner.
  - **Legacy branch:** the seed writes false only while the flag is false.

- [ ] **Step 4: Run the tests and see them pass**

Run: `./mvnw -q test -pl core "-Dtest=ValuesVisibleSeedTest,SeedChromeDefaultsTest,ChromeSeedClassificationTest,VizModernizeUtilTest,VizModernizeResetTest" -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS, 0 failures.

- [ ] **Step 5: Commit** (controller): `Chart defaults S8, seed values on marked funnels and waterfalls and turn the funnel grid off`

---

### Task 2: Values inside the mark, only where they fit (S8-3, D129, D137)

**Files:**
- Modify: `graph/GraphElement.java`, `graph/visual/BarVO.java`, `report/composition/graph/GraphGenerator.java`
- Test: `core/src/test/java/inetsoft/graph/visual/BarVOValueFitTest.java` (new, Spring), `core/src/test/java/inetsoft/report/composition/graph/ModernFunnelWaterfallValuesTest.java` (new, Spring)

**Interfaces:**
- Produces: `GraphElement.setValueFitInside(boolean)` / `isValueFitInside()`: default false; joins the element's `equalsContent` if it compares label settings. When true, `BarVO` hides (`zIndex −1`) a value label whose box plus 3px on each side does not fit inside its bar box in both directions, and shows it again when a later layout fits it. Placement and colour are the generator's.

- [ ] **Step 1: Write the failing graph tests** (`BarVOValueFitTest`; an interval `EGraph` with values on, CENTER placement, 400×240)
  - `aValueThatFitsShows`: a bar 60px tall and 40px wide with label "520" → visible.
  - `aShortBarHidesItsValue`: a bar 12px tall → hidden.
  - `aNarrowBarHidesItsValue`: a bar 60px tall and 8px wide with "-1,250" → hidden.
  - `aRelayoutRestoresAFittingLabel`: lay out at 100×240 (hidden) then 400×240 → visible.
  - `withoutTheFlagNothingIsHidden`: the 8px case with the flag off → today's label (visible).

- [ ] **Step 2: Write the failing generator tests** (`ModernFunnelWaterfallValuesTest`; marked charts with values on)
  - `waterfallValuesSitInsideTheirBars`: a marked waterfall → the element's label placement is CENTER, `isValueFitInside()` true, auto text colour on; the Total element too.
  - `funnelValuesFitTheirStages`: a marked funnel → `isValueFitInside()` true and auto text colour on.
  - `directionFillsTakeWhiteInLightAndBlackInDark`: after layout, a shown label on the up colour `#1C857B` is white in light mode; on `#3FBFB2` black in dark mode.
  - `anAuthorsTextColourWins`: an author text colour on the measure → auto text colour off.
  - `aHighlightColourWins`: a highlight on the measure's labels → the highlighted label keeps the highlight colour.
  - `textLabelsKeepTodaysPlacement`: a marked waterfall with a Text field and values on → placement and fit as a classic chart (AUTO, flag false).
  - `classicChartsAreUnchanged`: no mark → AUTO placement on the waterfall, flag false, auto colour off.

- [ ] **Step 3: Run them and see them fail**

Run: `./mvnw -q test -pl core "-Dtest=BarVOValueFitTest,ModernFunnelWaterfallValuesTest" -Dsurefire.failIfNoSpecifiedTests=false`
Expected: compile failure on `setValueFitInside`.

- [ ] **Step 4: Implement**
  - **`BarVO.layoutText0`:** the fit test where `prefw`, `prefh` and the box are known (precedent: `TreemapVO:470-476`); restore the label's z-index when it fits.
  - **`GraphGenerator`:** on a marked funnel or waterfall without a Text field: `setLabelPlacement(GraphConstants.CENTER)` (the funnel already has it), `setValueFitInside(true)`, and the auto text colour on unless `isTextColorSet` (user or CSS). Make the generic text-colour path (about `:1256-1258`) leave a highlight colour in place on these elements.

- [ ] **Step 5: Run the tests and see them pass**

Run: `./mvnw -q test -pl core "-Dtest=BarVOValueFitTest,ModernFunnelWaterfallValuesTest,ModernMarkRulesTest,ModernZeroLineTest" -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS, 0 failures.

- [ ] **Step 6: Commit** (controller): `Chart defaults S8, show funnel and waterfall values inside the mark where they fit`

---

### Task 3: Filled markers by list comparison (S8-4, D139, D140)

**Files:**
- Create: `ModernShapes.java` (see File Structure)
- Modify: `uql/viewsheet/internal/ChartVSAssemblyInfo.java`, `report/composition/graph/GraphUtil.java`, `web/vswizard/recommender/chart/PointChartFilter.java`, `ScatterChartFilter.java`, `report/composition/graph/GraphGenerator.java` (shape legend)
- Test: `core/src/test/java/inetsoft/uql/viewsheet/internal/ShapeListSeedTest.java` (new, Spring), `core/src/test/java/inetsoft/report/composition/graph/ModernShapeLegendTest.java` (new, Spring)

**Interfaces:**
- Produces (in `ModernShapes`):
  - `static GShape[] classicDefault()` (equals `new CategoricalShapeFrame()`'s 30 entries), `static GShape[] modernDefault()` (the filled list in Global Constraints), `static GShape[] classicReset()` and `static GShape[] modernReset()` (the server frames the Shape pane's classic and modern Reset produce, C3; `modernReset` is `classicReset` with its first 11 entries reordered as the filled list).
  - `static boolean swap(CategoricalShapeFrame frame, boolean modern)`: when `modern`, a frame whose list equals `classicDefault` or `classicReset` takes `modernDefault` or `modernReset`; otherwise the reverse. Returns whether it changed. Never touches the frame's per-value static shapes or its `changed` flag.
  - `static CategoricalShapeFrame newFrame(VizContext ctx)`: a frame with the mark's default list.
- Consumes in Task 4: `modernReset` / `classicReset` define the client lists.

- [ ] **Step 1: Write the failing tests**
  - `ShapeListSeedTest`:
    - `modernizeFillsTheDefaultList`: a classic point chart with a Shape field (frame from `new CategoricalShapeFrame()`), seeded modern → the frame's 30 entries equal `modernDefault()`.
    - `revertRestoresTheClassicList`: then seeded legacy → equal to `classicDefault()`.
    - `anAuthorsListIsKept`: a frame with entry 0 set to `GShape.STAR` → unchanged by both seeds.
    - `thePanesResetListSwapsBothWays`: a frame holding `classicReset()` → `modernReset()` on Modernize and back on Revert.
    - `everyFrameIsReached`: a multi-style chart with Shape on an aggregate, a point chart with Show Lines, a map point chart and a radar with Show Points → each categorical shape frame swaps.
    - `aNewFrameOnAMarkedChartIsFilled`: `GraphUtil.fixVisualFrame` for a Shape drop on a marked point chart → `modernDefault()`; on a classic chart → `classicDefault()`.
    - `theWizardPointIsFilled`: `PointChartFilter` and `ScatterChartFilter` with a modern `VizContext` → the static shape is `FILLED_CIRCLE`; classic → `CIRCLE`.
    - `theSeedNeverMarksTheFrameChanged`: after a swap the wrapper's `changed` is unchanged.
  - `ModernShapeLegendTest`: a marked point chart with Shape and a colour dimension → the shape legend's `LegendSpec.getSymbolFallbackColor()` equals the size legend's modern fallback (S2-8); classic → null.

- [ ] **Step 2: Run them and see them fail**

Run: `./mvnw -q test -pl core "-Dtest=ShapeListSeedTest,ModernShapeLegendTest" -Dsurefire.failIfNoSpecifiedTests=false`
Expected: compile failure on `ModernShapes`.

- [ ] **Step 3: Implement**
  - **`seedShapeLists(VizContext)`** in `seedChromeDefaults`, next to `seedColorPalette`: walk `info.getAestheticRefs(runtime)` for both values of `runtime`, and each `ChartAggregateRef`'s shape field from `info.getBindingRefs(runtime)`, calling `ModernShapes.swap` on every `CategoricalShapeFrame`.
  - **`GraphUtil.fixVisualFrame`:** the shape branch builds `ModernShapes.newFrame(ctx)` (the context is in scope).
  - **Wizard:** `FILLED_CIRCLE` when the filter's `VizContext` is modern (C6).
  - **Shape legend:** in the generator's shape-legend setup, set the same symbol fallback colour and border S2-8 sets in `processSizeLegend`, on marked charts only.

- [ ] **Step 4: Run the tests and see them pass**

Run: `./mvnw -q test -pl core "-Dtest=ShapeListSeedTest,ModernShapeLegendTest,SeedChromeDefaultsTest,VizModernizeUtilTest" -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS, 0 failures.

- [ ] **Step 5: Commit** (controller): `Chart defaults S8, fill shape markers on marked charts`

---

### Task 4: The Shape pane's Reset follows the mark (S8-4, D140)

**Files:**
- Modify: `web/binding/model/ChartBindingModel.java`, `web/binding/service/VSChartBindingFactory.java`, `web/projects/portal/src/app/binding/data/chart/chart-binding-model.ts`, `web/projects/portal/src/app/binding/editor/chart/aesthetic/categorical-shape-pane.component.ts`, the file defining `ChartConfig.SHAPE_STYLES`
- Test: `core/src/test/java/inetsoft/web/binding/service/VSChartBindingFactoryVizModernTest.java` (new, Spring; the same class S7 adds — if S7 is already on the base, extend it instead), `web/projects/portal/src/app/binding/editor/chart/aesthetic/categorical-shape-pane.component.spec.ts` (new or extend)

**Interfaces:**
- Consumes: `ModernShapes.modernReset()` / `classicReset()` (Task 3) — the client lists must produce exactly these frames.
- Produces: `ChartBindingModel.isVizModern()` / `setVizModern(boolean)`, set in `VSChartBindingFactory` as `chartBinding.setVizModern(VizContext.of(info).modern)` next to the other binding fields; `vizModern?: boolean` in `chart-binding-model.ts`. Same lines as S7's `95c54a4e6b` (C4). `ChartConfig.MODERN_SHAPE_STYLES` (the filled list's ids, `StyleConstants.FILLED_CIRCLE + ""` first).

- [ ] **Step 1: Write the failing tests**
  - `VSChartBindingFactoryVizModernTest`: a marked chart's binding model has `isVizModern()` true; a classic one false.
  - `categorical-shape-pane.component.spec.ts`: with `vizModern` true, `reset()` sets `shapes` to `ChartConfig.MODERN_SHAPE_STYLES.concat(ChartConfig.IMAGE_SHAPES)`; with false or undefined, to today's `SHAPE_STYLES.concat(IMAGE_SHAPES)`.
  - A Java round trip in `ShapeListSeedTest` (Task 3's class): the shapes the client's modern Reset sends, applied through `CategoricalShapeModel.createVisualFrame`, equal `ModernShapes.modernReset()`; the classic Reset's equal `classicReset()`.

- [ ] **Step 2: Run them and see them fail** (Java with `-Dtest=VSChartBindingFactoryVizModernTest,ShapeListSeedTest`; the portal spec with the command in "How to run tests").
Expected: compile failure on `isVizModern`; the portal spec fails on `MODERN_SHAPE_STYLES`.

- [ ] **Step 3: Implement** the field (C4), the client constant, and the pane's `reset()` reading the mark from the chart binding model it already has (or receives through its parent).

- [ ] **Step 4: Run the tests and see them pass** (same commands). Expected: PASS, 0 failures.

- [ ] **Step 5: Commit** (controller): `Chart defaults S8, reset a marked chart's shapes to the filled list`

---

### Task 5: The size legend on the treemap family (S8-5, D141)

**Files:**
- Modify: `uql/viewsheet/graph/LegendDescriptor.java`, `uql/viewsheet/internal/ChartVSAssemblyInfo.java`, `web/viewsheet/controller/chart/VSChartLegendsVisibilityService.java`, the `ChartVSAScriptable` "Size Legend" action, `web/graph/GraphBuilder.java`, `web/vswizard/handler/VSWizardBindingHandler.java`, `ChartChromeSlots` (reset copy)
- Test: `core/src/test/java/inetsoft/uql/viewsheet/internal/SizeLegendSeedTest.java` (new, Spring), `core/src/test/java/inetsoft/web/graph/SizeLegendMenuTest.java` (new, Spring)

**Interfaces:**
- Produces:
  - `LegendDescriptor.setAuthorVisible(boolean)` and `setAuthorMaxModeVisible(boolean)` (set the value and the flag), `isUserVisible()` / `setUserVisible(boolean)`; parse/write through `DerivedUserFlag` as `userVisible` (precedent: `userTitleVisible`, `:131-180`, `:574`); joins `equals`.
  - Private `ChartVSAssemblyInfo.seedSizeLegend(VizContext, ResolvedChartType)` in `seedTypeDefaults`, writing `getSizeLegendDescriptor()` of the design and runtime `LegendsDescriptor`, both copies, while unflagged.

- [ ] **Step 1: Write the failing tests**
  - `SizeLegendSeedTest`:
    - `theTreemapFamilyHidesIt`: marked treemap, sunburst, circle packing and icicle → `visible` and `maxModeVisible` false, also with no colour field (the only legend).
    - `otherTypesShowIt`: a marked treemap changed to a bubble (point with Size) → both true.
    - `anAuthorsShownSizeLegendSurvives`: `setAuthorVisible(true)` on a marked treemap, then re-seed → true.
    - `theLegendServicesStamp`: `VSChartLegendsVisibilityService` hide on the size legend → flag true; `showAllDescriptorLegends` → `visible` true and flag true.
    - `theScriptActionStamps`: the "Size Legend" visibility action → flag true.
    - `aStoredFalseIsTheAuthors`: parse with `visible` false and no `userVisible` → flag true.
    - `theResetCopyClearsTheFlag`: the reset copy on a marked treemap whose author showed the size legend → flag false and the legend hidden.
    - `classicChartsAreUnchanged`: no mark → both true, flag false, XML unchanged.
  - `SizeLegendMenuTest`:
    - `noSizeFieldOffersNoShowLegend`: a marked treemap without Size → `GraphBuilder.isLegendHidden(...)` false.
    - `aSeededHiddenSizeLegendStillOffersShowLegend`: with Size bound → true.
    - `theWizardKeepsTheColourLegend`: `VSWizardBindingHandler.reloadLegendVisible` on a marked treemap with Color and Size → `showLegend` true, so finishing does not call `hideAllLegend`.

- [ ] **Step 2: Run them and see them fail**

Run: `./mvnw -q test -pl core "-Dtest=SizeLegendSeedTest,SizeLegendMenuTest" -Dsurefire.failIfNoSpecifiedTests=false`
Expected: compile failure on `setAuthorVisible`.

- [ ] **Step 3: Implement** the flag, the seed, the stampers (the services and the script action call the author setters; `ChartDcProcessor.syncLegendVisible` does not), `isLegendHidden` skipping the size descriptor while `cinfo.getSizeField() == null`, the wizard check ignoring an unflagged hidden size legend on `isTreemap` charts, and the reset-copy slot clearing the flag (precedent: the legend-title flag slot, D37).

- [ ] **Step 4: Run the tests and see them pass**

Run: `./mvnw -q test -pl core "-Dtest=SizeLegendSeedTest,SizeLegendMenuTest,LegendTitleFlagTest,ChartChromeSlotsTest" -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS, 0 failures.

- [ ] **Step 5: Commit** (controller): `Chart defaults S8, hide the size legend on marked treemap charts`

---

### Task 6: Tinted tree boxes (S8-6, D142)

**Files:**
- Modify: `graph/element/RelationElement.java`, `graph/visual/RelationVO.java`, `report/composition/graph/GraphGenerator.java`
- Test: `core/src/test/java/inetsoft/graph/visual/RelationVOTintTest.java` (new, Spring), `core/src/test/java/inetsoft/report/composition/graph/ModernTreeBoxTest.java` (new, Spring)

**Interfaces:**
- Produces: `RelationElement.setBoxTint(double alpha, Color backdrop)`, `double getBoxTintAlpha()` (0 = off), `Color getBoxTintBackdrop()`, `setBoxInk(Color)` / `getBoxInk()`; all join `equalsContent`. When the alpha is above 0, `RelationVO` fills each node with its own colour blended over the backdrop (design call), outlines it 1px in the full colour, draws with anti-aliasing, and draws inside labels in the ink when one is set.

- [ ] **Step 1: Write the failing graph tests** (`RelationVOTintTest`; a tree element with a static node colour `#0490FF`)
  - `aTintedBoxFillsLightAndOutlinesFull`: tint 0.15 over `#ECEBE9` → fill `≈ #C9DDEC` (±2 per channel), outline `#0490FF`.
  - `theInkDrawsInsideLabels`: ink `#35342F` → an inside label's colour is `#35342F`.
  - `aDimensionColourTintsEachNode`: a categorical node colour frame → each node's fill is its own colour's tint.
  - `withoutTheTintNothingChanges`: alpha 0 → today's solid fill, no outline.

- [ ] **Step 2: Write the failing generator tests** (`ModernTreeBoxTest`)
  - `aMarkedTreeIsTinted`: light → alpha 0.15, backdrop `#ECEBE9`, ink `#35342F`; dark → `#313034`, ink `#E6E0E9`.
  - `anAuthorsTextColourWins`: an author node text colour → no ink set (the author's colour draws).
  - `aMeasureOrHighlightStaysSolid`: a measure on node colour → alpha 0; a highlighted node (graph level) → drawn solid with the automatic ink.
  - `networkAndCircularStaySolid`: a marked network and circular network → alpha 0.
  - `classicTreesAreUnchanged`: no mark → alpha 0, no ink.

- [ ] **Step 3: Run them and see them fail**

Run: `./mvnw -q test -pl core "-Dtest=RelationVOTintTest,ModernTreeBoxTest" -Dsurefire.failIfNoSpecifiedTests=false`
Expected: compile failure on `setBoxTint`.

- [ ] **Step 4: Implement** the element properties, the VO painting (the fill branch at `RelationVO:155-160` generalised to the tint, per node; anti-aliasing on for tinted boxes), and the generator setting them on a marked `CHART_TREE` whose node colour frame is not a `LinearColorFrame`, with `cardComposite(ctx)` and `titleColor(ctx)`, the ink skipped when `isTextColorSet` is true for the node text.

- [ ] **Step 5: Run the tests and see them pass**

Run: `./mvnw -q test -pl core "-Dtest=RelationVOTintTest,ModernTreeBoxTest,VSChartRelationNodeLegendTest" -Dsurefire.failIfNoSpecifiedTests=false -Dsurefire.groups=core,slow`
Expected: PASS, 0 failures.

- [ ] **Step 6: Commit** (controller): `Chart defaults S8, tint marked tree boxes with an outline and the title ink`

---

### Task 7: Whole-branch check and live-check handoff

**Files:**
- Create: `E:/Temp/chart-defaults-s8b-checks-handoff.md` (outside the repo, not committed)

- [ ] **Step 1: Run the packages**

Run: `./mvnw -q test -pl core "-Dtest=inetsoft.graph.**.*Test,inetsoft.report.composition.graph.**.*Test,inetsoft.uql.viewsheet.**.*Test,inetsoft.web.graph.**.*Test,inetsoft.web.binding.**.*Test,inetsoft.web.viewsheet.controller.chart.**.*Test,inetsoft.web.vswizard.**.*Test" -Dsurefire.failIfNoSpecifiedTests=false`
Expected: 0 failures apart from the epic-base `BoxPainterTest.borderColorWithoutColorFrameDoesNotThrow` (fails on the base too). Report the counts.

- [ ] **Step 2: Run the portal specs** for `binding/editor/chart/aesthetic` (`npx ng test portal --include="projects/portal/src/app/binding/editor/chart/aesthetic/**/*.spec.ts"`). Expected: 0 failures.

- [ ] **Step 3: Write the handoff** for a separate live-check session, base `073b761682` (S8a) against this branch's head, one row per §11 S8-3 to S8-6 bullet, plus:
  - classic identity: the same classic dashboard's tiles, XML, PNG and PDF exports identical across the two builds
  - the canonical 8-item and 20-item waterfall and the 5-stage and 8-stage funnel at 400×240 and 300×180, light and dark, against the bundle's funnel and waterfall drawn cards
  - a point chart with a shape field and a colour field, the Shape pane's Reset on a marked and a classic chart, and the wizard's plain point
  - the four hierarchy types with and without a colour field; Show Legend from the chart menu; finishing the wizard
  - the 15-node tree with no colour field, a dimension and a measure on node colour, light and dark, against the bundle's relation drawn card
  - Modernize → Revert → Modernize on each, and an older build opening the saved file (recorded)
  - Results column empty.

- [ ] **Step 4: Report** (no commit): package counts, the portal result and the handoff path.
