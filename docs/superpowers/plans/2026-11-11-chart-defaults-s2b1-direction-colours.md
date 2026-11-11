# Chart Defaults S2b-1 — Direction Colours Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** On a modern candle, stock or waterfall, draw rising and falling marks in dedicated direction colours, teal up and amber down, and the waterfall Total in a neutral grey. Store the colours with the chart so an author can edit them in the colour pane. Classic charts stay pixel-identical and keep their XML.

**Architecture:**
- **Storage.** Two new stored colour frames on the chart, up and down. Each is a `StaticColorFrameWrapper`, so it has the default, CSS and user tiers. They are seeded per resolved type in S0's `seedTypeDefaults`; the legacy pass removes frames that hold only a seed.
- **Drawing.** The renderer installs one new base colour frame, `DirectionColorFrame`, before highlight and brushing wrap it. The frame picks up or down per row: close against open for candle and stock, the sign of the step for a waterfall. A single rule, `VSChartDirectionColors.draws`, decides when this applies, and both the renderer and the binding editor use it.
- **Waterfall Total.** Its existing summary frame is seeded with the neutral token.

**Tech Stack:** Java 21, JUnit 5, Mockito, Spring test context (`@SreeHome`), Maven module `core`. Angular 21 / Vitest for the colour field (Task 5).

**Spec:** `docs/superpowers/specs/lookfeel/2026-11-06-chart-defaults-design.md`. The relevant sections are:
- §3 M1–M9
- §4 R9
- §5 S2 (S2-6, S2-7, S2-13 and "S2-6 direction tokens")
- §10 D8

**Owner rulings:** `E:/Temp/chart-defaults-s2b-research.md`, the "Review and recommendations" section and its "Owner rulings (2026-11-11)" table. They are summarised below. Where this plan and the spec differ, this plan wins (see Corrections). S2-9 to S2-12 are slice S2b-2, planned separately.

**Builds on:**
- S0 and S1, open as PRs #7986 and #7987.
- S2a, the local branch `bug-80455-s2` at `f38dbd2652` (S2a plus its node-colour fix from the S2a checks), plan `docs/superpowers/plans/2026-11-10-chart-defaults-s2a-palette-seeds.md`. From its "Interfaces for later slices" this plan uses:
  - `VSChartPaletteDefaults.activePalette`, `staticPalette` and `isModernChartColor`
  - `VSObjectChromeDefaults.cardComposite(ctx)`
  - the colour-pane rule that a not-`changed` model whose colour equals its CSS-or-default colour clears the user tier (`StaticColorFrameFactory`)
  - `ChartChromeSlots.frames`
  - `seedTypeDefaults(VizContext, ResolvedChartType)` and `reseedIfResolvedTypeChanged()`

**Verified against:** `bug-80455-s2` at `f38dbd2652` (S2a plus its node-colour fix from the S2a checks). Paths are under `core/src/main/java/inetsoft/` unless they start with `core/` or `web/`.

## Owner rulings (2026-11-11)

| # | Ruling |
|---|---|
| Q1 | A **stock** chart with no Open bound draws S2a's seed, palette slot 1. Direction colours apply only when Open is bound. |
| Q2 | An author's (USER) or stylesheet (CSS) **single colour** on a candle, stock or waterfall stays a single colour. Direction colours draw only while that single colour has neither tier. The reset copy clears `userColor` (80398), so it draws direction colours. |
| Q3 | A **waterfall with a colour dimension** keeps the dimension's colours. It is a visual stack (`GraphGenerator:3826-3831`). |
| Q4 | A modern **candle that already has a colour dimension** keeps series colours, permanently. |
| Q5 | **Up and down are two new stored frames on the chart**, seeded and saved, and written only when non-null. The legacy pass removes a frame that holds only a seeded DEFAULT. They draw through one base frame installed before highlight and brushing, exempt from the 0.75 darkening. The colour pane shows `[Up, Down]` (candle, stock) or `[Increase, Decrease, Total]` (waterfall) only while direction colours draw. |
| R9 | The token set follows the mark (light or dark), not the page behind the card. |

**One rule:** direction colours draw only on a **marked** chart whose resolved type is candle, stock or waterfall, and only when all of these hold:
- no colour field is bound
- the chart's single colour has no USER or CSS tier
- for stock, Open is bound

## Corrections to the spec

| # | Spec | Code at `0752422d66` | This plan |
|---|---|---|---|
| C1 | S2-6: candle colour is `CandleGraphGenerator:217-224` (`Color.gray`), and `CandlePainter` "draws one colour (there is no darkening)" | `Color.gray` is only the placeholder for an incomplete binding (`createFakeEGraph`). Candles draw the chart-level frame (S2a seeds it slot 1), and `GraphGenerator.initElement` (~:4757-4780) darkens it by 0.75. | Direction colours replace that base frame and are exempt from the darkening (Task 4). |
| C2 | S2-6 waterfall: "reuse the sparkline's negative-colour pattern if it has a stored property (then kind V)" | `StaticColorFrame.negcolor` is set at render only; `StaticColorFrameWrapper` does not save it. | Kind N, the two new frames (Task 2). |
| C3 | S2-6: "The Color field on a candle edits the two colours" | The colour field falls back to the single pane for candle and stock (`ColorFieldMc.getFrames:71-122`). The binding model carries no mark. | The server sends the up and down frame models only while `draws` holds. The client shows them when present (Task 5). |
| C4 | S2-7: the summary colour is tiered and saved at `GraphGenerator:4509-4515` | Applied at ~:4539; saved as `<summaryColorVisualFrame>`. `ChartAggregateInfoFactory.updateVisualFrames:211-226` writes `#7030A0` into its DEFAULT on every binding Apply. `ChartChromeSlots` does not walk it. | Seed it (Task 3). Apply keeps the model's default (Task 5). Add a chrome slot (Task 3). |
| C5 | S2-13: contrast check, blocked | The card shipped (#7750). Measured on the composite (`#ECEBE9` / `#313034`): light up 3.76, down 3.92, neutral 3.81; dark 5.81, 5.51, 5.40. | Pinned by a test (Task 1). No token change. |

**Known limitations:**
- **Up and down are hue only** (1.04:1 light, 1.05:1 dark), as the spec accepts. Candle keeps hollow for up and filled for down. A waterfall relies on values on the bars (S8-3). Stock has no second cue.
- **A multi-style chart with a waterfall measure** shares the chart's pair.
- **Older builds** ignore the new frames and draw the chart's single colour (slot 1, seeded by S2a). The Total colour travels, because the summary frame is already saved.

## Global Constraints

- **Worktree:** `$WT` is the executor's worktree on branch `bug-80455-s2b1`, cut from `bug-80455-s2` (`f38dbd2652`). Never work in `E:/StyleBI/stylebi-enterprise/community`.
- **Git:**
  - Each git command is its own call, `git -C "$WT" ...`.
  - One commit per task, subject `Chart defaults S2b-1, <what changed>`, last line `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
  - Subagents never run `git add`, `git commit` or `git stash` (a hook blocks them); the controller stages and commits after review. Never push.
- **Scope is the mark (M1):** only an assembly with a modern `VizMark` changes. A classic chart renders pixel-identical, and its XML is byte-identical on load and save. Graph-package code reads a property the generator sets from `vizContext`.
- **The author wins (M4):** seeds write only DEFAULT tiers. The user and CSS tiers are the author's and the stylesheet's.
- **Tokens (R9, by mark):**

  | Token | Light | Dark |
  |---|---|---|
  | up | `#1C857B` | `#3FBFB2` |
  | down | `#AD6119` | `#E8954A` |
  | neutral | `#767676` | `#A8A5AD` |

  Classic waterfall Total: `#7030A0`.
- **Up/down test:**
  - Candle and stock: up when close ≥ open, so a flat day counts as up, matching `CandlePainter`'s hollow rule.
  - Waterfall: down when the bar's value is < 0.
  - A missing value on either side counts as up.
- **Comments:** a short clause each, no ticket, PR, slice or design-doc names. No `.html` file is touched.
- **New files** start with the AGPL header of `SeedChromeDefaultsTest.java`, `Copyright (C) 2026  InetSoft Technology`.
- **Tests that reach Spring beans** use:
  - `@ExtendWith(SpringExtension.class)`
  - `@ContextConfiguration(classes = { BaseTestConfiguration.class, LibManagerTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)`
  - `@SreeHome`, `@Tag("core")`
  - In `@AfterEach`, clear `viewsheet.modernVisualization`, `viewsheet.darkMode` and `viewsheet.density`. Call `VSChartPaletteDefaults.clearMemo()` only from `inetsoft.uql.viewsheet.internal`, since it is package-private.

## Review Focus

1. **Keep my formatting on a classic candle whose author set a single colour** keeps that colour, and the reset copy draws direction colours. Pinned in Task 4 by `anAuthorSingleColourWins`, and in Task 3 by `theResetCopyClearsAnAuthorSingleColour`.
2. **A stock chart with no Open** draws slot 1 (darkened), not direction colours. Pinned in Task 4 by `aStockWithoutOpenDrawsSlotOne`.
3. **Modernize then Revert of a classic candle and a classic waterfall** leaves their XML byte-identical: no new frames, Total back to `#7030A0`. Pinned in Task 3 by `modernizeThenRevertKeepsTheXml`.
4. **An ordinary binding Apply on a modern waterfall** keeps the Total neutral. Pinned in Task 5 by `anApplyKeepsTheSeededSummaryColour`.
5. **A brushed modern candle** keeps its direction colours on the brushed rows. Pinned in Task 4 by `brushedRowsKeepTheirDirection`.

## File Structure

- **Task 1:** `uql/viewsheet/internal/VSChartPaletteDefaults.java` (tokens); `uql/viewsheet/internal/VSChartDirectionColors.java` (new, the `draws` rule).
- **Task 2:** `uql/viewsheet/graph/AbstractChartInfo.java` (the stored up and down frames).
- **Task 3:** `uql/viewsheet/internal/ChartVSAssemblyInfo.java` (seeds); `uql/viewsheet/internal/ChartChromeSlots.java` (slots).
- **Task 4:** `graph/aesthetic/DirectionColorFrame.java` (new); `report/composition/graph/GraphGenerator.java` (install, darkening).
- **Task 5:**
  - `web/binding/model/ChartBindingModel.java` and `web/binding/service/ChartInfoModelBuilder.java` (the editor's frames)
  - `web/binding/service/graph/ChartAggregateInfoFactory.java` (Total on Apply)
  - `web/projects/portal/src/app/binding/data/chart/chart-binding-model.ts`, `binding/util/graph-util.ts`, `binding/editor/chart/aesthetic/color-field-mc.component.ts`
- **Tests:**
  - new: `core/src/test/java/inetsoft/uql/viewsheet/internal/VSChartDirectionColorsTest.java`, `core/src/test/java/inetsoft/uql/viewsheet/graph/ChartInfoDirectionFramesTest.java`, `core/src/test/java/inetsoft/uql/viewsheet/internal/DirectionColorSeedTest.java`, `core/src/test/java/inetsoft/graph/aesthetic/DirectionColorFrameTest.java`, `core/src/test/java/inetsoft/report/composition/graph/DirectionColorRenderTest.java`, `core/src/test/java/inetsoft/web/binding/service/ChartInfoModelBuilderDirectionTest.java`, `web/projects/portal/src/app/binding/util/graph-util.spec.ts`
  - extended: `VSChartPaletteDefaultsTest`, `ChartChromeSlotsTest`, `StaticColorFrameFactoryTest`, `color-field-mc.component.tl.spec.ts`

**Test harnesses:**
- **Seed tests** copy `ChartColorTypeSeedTest`'s helpers: `newChart()`, `gateOn()`, `modernize`, `revert`, and its `VizModernizeUtil.modernize`/`revert` P6 test.
- **A candle info** is `new CandleVSChartInfo()` with `setHighField`/`setLowField`/`setOpenField`/`setCloseField` set to `VSChartAggregateRef`s, a date dimension on X, and `setChartType(GraphTypes.CHART_CANDLE)`. Stock is `StockVSChartInfo`. A waterfall is `DefaultVSChartInfo` with `CHART_WATERFALL`, a dimension on X and one measure on Y.
- **Render tests** follow `ChartColorRenderTest` / `ChartChromeRenderTest.paretoGraph`: `VizModernizeUtil.reseedAfterRestore(assemblyInfo)`, then `GraphGenerator.getGenerator(...).createEGraph()`. Read the element's colour frame and call `getColor(dataSet, var, row)`.

## How to run tests

From `$WT`:

```bash
./mvnw -q test -pl core "-Dtest=ClassA,ClassB" -Dsurefire.failIfNoSpecifiedTests=false
```

Read `core/target/surefire-reports/*.txt` for counts. If compilation fails with "Attempt to recreate … HeartBeatServiceProxy", delete the untracked `core/target/generated-test-sources`.

Portal tests run from `$WT/web` after `npm ci`; a junctioned `node_modules` breaks Vitest. Use `npx ng test portal --include="**/graph-util.spec.ts"` for unit specs. For the testing-library spec use `npx ng run portal:test-tl --include='**/color-field-mc.component.tl.spec.ts'`, one file only: `ng test portal` silently matches no TL file.

---

### Task 1: Tokens, the "draws" rule, and the contrast check

S2-6, S2-13, R9, Q1–Q4, C5.

**Files:**
- Modify: `uql/viewsheet/internal/VSChartPaletteDefaults.java`
- Create: `uql/viewsheet/internal/VSChartDirectionColors.java`
- Test: `VSChartPaletteDefaultsTest`, `VSChartDirectionColorsTest` (new)

**Interfaces:**
- Produces:
  - `VSChartPaletteDefaults.directionUp(VizContext ctx)`, `directionDown(VizContext ctx)` and `directionNeutral(VizContext ctx)`, each returning a `Color`. Each follows `ctx.dark`, with the token values from Global Constraints.
  - `VSChartPaletteDefaults.isNeutralToken(Color c): boolean`, true for either neutral.
  - `public final class VSChartDirectionColors`:
    - `static boolean isDirectionType(int type)`: candle, stock or waterfall (`GraphTypes.isCandle`, `isStock`, `isWaterfall`).
    - `static boolean draws(VizContext ctx, ChartInfo info)`: true when all of these hold:
      - `ctx != null && ctx.modern`
      - `isDirectionType(ResolvedChartType.of(info).type())`
      - `info.getColorField() == null`, and for a waterfall its single measure's `getColorField() == null`
      - the single colour has a null user colour and a null CSS colour. The single colour is the chart-level `getColorFrameWrapper()` for candle and stock, and the waterfall measure's `getColorFrameWrapper()`. Either must be a `StaticColorFrameWrapper`; otherwise the rule is false.
      - for stock, `((CandleChartInfo) info).getOpenField() != null`
    - The waterfall measure is the first `ChartAggregateRef` in `info.getModelRefs(false)`.

- [ ] **Step 1: Write the failing tests.**
  - In `VSChartPaletteDefaultsTest`:
    - `directionTokensFollowTheMark`: the six values.
    - `directionTokensClearThreeToOneOnTheCard`: with a WCAG relative-luminance helper in the test, each token against `VSObjectChromeDefaults.cardComposite(ctx)` is ≥ 3.0. Assert the light values 3.76, 3.92 and 3.81 and the dark values 5.81, 5.51 and 5.40, each within 0.01.
  - In `VSChartDirectionColorsTest` (Spring):
    - `aModernCandleDraws`, `aModernWaterfallDraws`, `aModernStockWithOpenDraws`
    - `aStockWithoutOpenDoesNot`
    - `aClassicCandleDoesNot`, `aBarDoesNot`
    - `aColourFieldStopsIt`
    - `anAuthorSingleColourStopsIt`: user colour set on the chart-level wrapper.
    - `aStylesheetSingleColourStopsIt`: CSS colour set on the frame.
    - `aWaterfallMeasureColourStopsIt`

- [ ] **Step 2: Run them to verify they fail.** Compile failure.

- [ ] **Step 3: Implement** as under Interfaces.

- [ ] **Step 4: Run them to verify they pass.** Run both classes. Expected: all pass, and the existing `VSChartPaletteDefaultsTest` cases still pass.

- [ ] **Step 5: Commit**: `Chart defaults S2b-1, add the direction tokens and the rule for when they draw`.

---

### Task 2: Up and down colours stored on the chart

S2-6, Q5, C2.

**Files:**
- Modify: `uql/viewsheet/graph/AbstractChartInfo.java`:
  - accessors near `getColorFrameWrapper` (:96-107)
  - clone (:2309)
  - `equalsContent` (:2430)
  - write (:2635-2638)
  - parse (:2931-2936)
  - fields (:4063)
- Test: `core/src/test/java/inetsoft/uql/viewsheet/graph/ChartInfoDirectionFramesTest.java` (new)

**Interfaces:**
- Produces: `StaticColorFrameWrapper getUpColorFrameWrapper()`, `void setUpColorFrameWrapper(StaticColorFrameWrapper wrapper)`, and the same for `Down`.
  - Both default to null.
  - They are written as `<upColorVisualFrame>` and `<downColorVisualFrame>` right after `<colorVisualFrame>`, only when non-null.
  - They are parsed with `VisualFrameWrapper.createVisualFrame`, cloned deeply, and compared in `equalsContent`.

- [ ] **Step 1: Write the failing tests:**
  - `theFramesRoundTrip`: up with default `#1C857B` and user `#00AA00`, down with default `#AD6119`. Write, parse into a fresh `DefaultVSChartInfo`; the tiers are equal.
  - `aChartWithoutThemWritesNothingNew`: the XML contains neither tag.
  - `aCloneDoesNotShareThem`
  - `equalsContentSeesThem`

- [ ] **Step 2: Run them to verify they fail.**

- [ ] **Step 3: Implement.**

- [ ] **Step 4: Run them to verify they pass,** plus `ChartDescriptorSavedDefaultsTest` and `ChartColorTypeSeedTest`.

- [ ] **Step 5: Commit**: `Chart defaults S2b-1, store up and down colours on the chart`.

---

### Task 3: Seed the direction colours and the waterfall Total

S2-6, S2-7, Q2, Q5, C4, Review Focus 1, 3.

**Decision.**
- **Where it runs.** Both seeds run from `seedTypeDefaults` on both branches. So a type or binding change re-runs them on a marked chart (`reseedIfResolvedTypeChanged`), and Modernize, Revert and restore run them through the full seed.
- **Up and down frames.** They are seeded by resolved type only. Whether they *draw* (colour field, single colour, stock Open) is decided at draw time by `draws`, so a binding change never has to create or remove them.
- **The Total.** It is seeded on every marked chart's aggregates, so a measure that later becomes a waterfall already holds the neutral.

**Files:**
- Modify: `uql/viewsheet/internal/ChartVSAssemblyInfo.java` (`seedTypeDefaults`, about :3123-3135 plus the S2a additions; new private methods after `seedChartColor`)
- Modify: `uql/viewsheet/internal/ChartChromeSlots.java` (:194-197 chart-level frames; :266 aggregate frames)
- Test: `DirectionColorSeedTest` (new), `ChartChromeSlotsTest`

**Interfaces:**
- Consumes: Task 1 tokens and `isDirectionType`; Task 2 accessors.
- Produces:
  - `private void seedDirectionColors(VizContext ctx, ResolvedChartType type)`.
    - When `ctx.modern && isDirectionType(type.type())`: create a missing wrapper (`new StaticColorFrameWrapper()`) and write `setDefaultColor(directionUp(ctx))` / `directionDown(ctx)`.
    - Otherwise: set each wrapper to null when its user colour and CSS colour are both null.
  - `private void seedSummaryColors(VizContext ctx)`: over every `ChartAggregateRef` in `getBindingRefs(false)` and `getBindingRefs(true)` whose `getSummaryColorFrameWrapper()` is a `StaticColorFrameWrapper`:
    - modern: write `directionNeutral(ctx)` when the default is `#7030A0` or `isNeutralToken`
    - legacy: write `#7030A0` when `isNeutralToken(default)`
    - the user tier is never touched
  - In `ChartChromeSlots`:
    - `frames("chart:up", CHART.then(VSChartInfo::getUpColorFrameWrapper), null)`
    - `frames("chart:down", CHART.then(VSChartInfo::getDownColorFrameWrapper), null)`
    - in the aggregate branch, `frames(key + ":summary", aggr.then(ChartAggregateRef::getSummaryColorFrameWrapper), null)`

- [ ] **Step 1: Write the failing tests.**
  - In `DirectionColorSeedTest` (Spring):
    - `aModernCandleGetsTheTokens`: up `#1C857B`, down `#AD6119`. Dark mark: `#3FBFB2` and `#E8954A`. Stock and waterfall the same.
    - `aModernBarGetsNoFrames`
    - `aCandleChangedToBarDropsSeedOnlyFrames`: through `reseedIfResolvedTypeChanged()`.
    - `revertDropsSeedOnlyFramesAndKeepsAnAuthorsUp`: user up `#00AA00` survives; the down frame is gone.
    - `theWaterfallTotalTurnsNeutralAndBack`: `#767676`, then `#7030A0` after Revert.
    - `anAuthorTotalColourIsLeftAlone`
    - `aLightToDarkSwitchMovesTheTokens`
    - `aClassicCandlesXmlIsUnchanged`: gate off, `seed`; the chart-info XML is equal before and after.
    - `modernizeThenRevertKeepsTheXml`: parameterised over a classic candle, stock and waterfall, as `ChartColorTypeSeedTest.modernizeThenRevertKeepsTheChartXml`.
  - In `ChartChromeSlotsTest`:
    - `theResetCopyClearsAuthorDirectionAndTotalColours`
    - `theResetCopyClearsAnAuthorSingleColour`: the chart-level `chart:frame:static` slot on a candle, which already exists; this pins Q2's reset path.

- [ ] **Step 2: Run them to verify they fail.**

- [ ] **Step 3: Implement** as under Interfaces.

- [ ] **Step 4: Run them to verify they pass**, plus `ChartColorTypeSeedTest,StaticColorSeedTest,SeedChromeDefaultsTest,VizModernizeUtilTest,VizModernizeResidueTest,VizModernizeResetTest,ResolvedTypeReseedTest`.

- [ ] **Step 5: Commit**: `Chart defaults S2b-1, seed direction colours and a neutral waterfall Total on modern charts`.

---

### Task 4: Draw the direction colours

S2-6, Q1–Q5, C1, Review Focus 1, 2, 5.

**Files:**
- Create: `graph/aesthetic/DirectionColorFrame.java`
- Modify: `report/composition/graph/GraphGenerator.java`:
  - install before `applyHighlight()` (:341)
  - a new field and a private factory method
  - darkening switch (~:4757-4780)
- Test: `core/src/test/java/inetsoft/graph/aesthetic/DirectionColorFrameTest.java` (new, no Spring), `core/src/test/java/inetsoft/report/composition/graph/DirectionColorRenderTest.java` (new, Spring)

**Interfaces:**
- Consumes: Task 1 `draws` and tokens; Task 2 accessors.
- Produces: `public class DirectionColorFrame extends StaticColorFrame`:
  - Constructors:
    - `DirectionColorFrame(Color up, Color down, String openField, String closeField)` for candle and stock
    - `DirectionColorFrame(Color up, Color down)` for a waterfall
  - `getUpColor()`, `getDownColor()`
  - `getColor(DataSet data, String col, int row)` applies the up/down test from Global Constraints:
    - candle and stock compare the numbers in `openField` and `closeField`
    - a waterfall reads `data.getData(col, row)`
    - a column is read under its plain name when `data.indexOfHeader(name) >= 0`, else under `ElementVO.ALL_PREFIX + name` (the brushed "all" copy)
  - `getColor(Object)` returns up.
  - `isVisible()` stays false (inherited), so there is no legend.
  - `equals`, `hashCode` and `clone` cover the four fields.
- In `GraphGenerator`:
  - `private boolean directionColors`.
  - In the viewsheet constructor, after `cvisitor` is built and before `applyHighlight()`: when `VSChartDirectionColors.draws(vizContext, info)`, call `setColorFrame(createDirectionColorFrame())` and set `directionColors = true`.
  - `createDirectionColorFrame()`:
    - reads `getColor()` of the chart's up and down wrappers, falling back to the token when a wrapper is null
    - for candle and stock, passes `GraphUtil.getName(((CandleChartInfo) info).getRTOpenField())` and `getRTCloseField()`
  - The darkening `if` adds `!directionColors`.

- [ ] **Step 1: Write the failing tests.**
  - In `DirectionColorFrameTest`:
    - `closeAboveOpenIsUp`, `closeBelowOpenIsDown`, `aFlatDayIsUp`
    - `aMissingValueIsUp`
    - `theBrushedCopyIsReadWhenThePlainColumnIsAbsent`
    - `aNegativeStepIsDown`, `aPositiveStepIsUp`
    - `equalsAndCloneCoverTheColours`
  - In `DirectionColorRenderTest`, rows with one rising and one falling day:
    - `aModernCandleDrawsUpAndDown`: `#1C857B` and `#AD6119`, not darkened.
    - `aDarkCandleUsesTheDarkTokens`
    - `aClassicCandleIsUnchanged`: `#3C698A`, the legacy blue darkened.
    - `aColourFieldWins`: series colours.
    - `anAuthorSingleColourWins`: the author's colour, darkened as today.
    - `aStockWithOpenDrawsDirection`
    - `aStockWithoutOpenDrawsSlotOne`: `#036CBF`.
    - `aModernWaterfallDrawsIncreaseDecreaseAndTotal`: a positive step `#1C857B`, a negative step `#AD6119`, the Total bar `#767676`.
    - `brushedRowsKeepTheirDirection`: a brushed modern candle; the brushed rows read through the brushing frame keep teal/amber by direction.

- [ ] **Step 2: Run them to verify they fail.**

- [ ] **Step 3: Implement** as under Interfaces.

- [ ] **Step 4: Run them to verify they pass**, plus `ChartColorRenderTest,ChartChromeRenderTest,VGraphPairModernPaletteTest,BrushedMarksTest,CompanionBrushColorFrameTest`. Expected: all pass. Classic charts are unchanged.

- [ ] **Step 5: Commit**: `Chart defaults S2b-1, draw modern candles, stock and waterfalls in direction colours`.

---

### Task 5: The colour pane edits up, down and the Total

S2-6, S2-7, Q5, C3, C4, Review Focus 4.

**Decision.**
- **What the server sends.** The up and down frame models only while `draws` holds, from `ChartInfoModelBuilder`, which has `getVizContext()`.
- **What the client shows.** When both are present, the colour pane lists them in place of the single colour. For a waterfall it also lists the measure's summary frame, labelled "Total" (owner ruling Q5; the classic pane keeps "Summary").
- **Labels.** "Up" and "Down", or "Increase" and "Decrease" on a waterfall. They are plain literals, as "Summary" is today. The waterfall summary entry reads "Total" (`graph-util.ts:977`).
- **Reset.** It reuses S2a's rule unchanged.

**Files:**
- Modify: `web/binding/model/ChartBindingModel.java` (next to `colorFrame` :318-325, :630)
- Modify: `web/binding/service/ChartInfoModelBuilder.java`: in `createChartBinding` (:59) after the aesthetic model is filled; in `updateChartInfo` (:305) after `aesService.updateVisualFrames` (:383). Use `visualService` (:45-50).
- Modify: `web/binding/service/graph/ChartAggregateInfoFactory.java:211-226`
- Modify: `web/projects/portal/src/app/binding/data/chart/chart-binding-model.ts` (:42, :58), `web/projects/portal/src/app/binding/util/graph-util.ts` (`getVisualFrames` :948-986), `web/projects/portal/src/app/binding/editor/chart/aesthetic/color-field-mc.component.ts` (`getFrames` :71-122)
- Test: `ChartInfoModelBuilderDirectionTest` (new), `StaticColorFrameFactoryTest`, `graph-util.spec.ts` (new), `color-field-mc.component.tl.spec.ts`

**Interfaces:**
- **Java model.** `ChartBindingModel.getUpColorFrame()` / `setUpColorFrame(ColorFrameModel)`, and the same for `Down`.
- **TS model.** Optional `upColorFrame?: ColorFrameModel` and `downColorFrame?: ColorFrameModel` on both `ChartBindingModel` declarations in `chart-binding-model.ts`.
- **`createChartBinding`.** When `VSChartDirectionColors.draws(getVizContext(), cinfo)` and both wrappers are non-null, set both models from `visualService.createVisualFrameModel(wrapper)`.
- **`updateChartInfo`.** When the model carries them, write back with `visualService.updateVisualFrameWrapper(info.getUpColorFrameWrapper(), model.getUpColorFrame())`, and the same for down.
- **`ChartAggregateInfoFactory.updateVisualFrames`.** Write the model's `defaultColor` (parsed) into the summary wrapper when the model carries one; otherwise `#7030A0`, as today. This replaces the unconditional `#7030A0`.
- **`GraphUtil.getVisualFrames("colorFrame", …)`.** When `bindingModel.upColorFrame && bindingModel.downColorFrame`, return:
  - `{name: "Up"|"Increase", frame: up}`
  - `{name: "Down"|"Decrease", frame: down}`
  - for a waterfall with a measure, `{name: "Total", summary: true, frame: aref.summaryColorFrame}`
- **`ColorFieldMc.getFrames`.** Gets the same early branch after the `colorField` check.

- [ ] **Step 1: Write the failing tests.**
  - In `ChartInfoModelBuilderDirectionTest` (Spring; build the builder as `ChartInfoModelBuilderColorChangedTest` does, or with the real Spring beans):
    - `aModernCandleSendsUpAndDown`
    - `aClassicCandleDoesNot`
    - `aColourFieldStopsThem`
    - `anAuthorSingleColourStopsThem`
    - `anEditWritesTheAuthorsUp`: model up colour `#00AA00`, `changed` true; after `updateChartInfo`, the user colour is `#00AA00`.
    - `aResetClearsTheAuthorsUp`: S2a's rule.
  - In `StaticColorFrameFactoryTest`, or a new `ChartAggregateInfoFactorySummaryTest`:
    - `anApplyKeepsTheSeededSummaryColour`: summary model default `#767676`; the wrapper keeps `#767676`.
    - `aClassicApplyStillWritesPurple`: model default `#7030A0`, or none.
  - In `graph-util.spec.ts`:
    - `getVisualFrames lists Up and Down for a candle with direction frames`
    - `getVisualFrames lists Increase, Decrease and Total for a waterfall`
    - `getVisualFrames is unchanged without them`
  - In `color-field-mc.component.tl.spec.ts`: `a modern candle's colour field opens the Up and Down pane`.

- [ ] **Step 2: Run them to verify they fail.**

- [ ] **Step 3: Implement** as under Interfaces.

- [ ] **Step 4: Run them to verify they pass**, plus `ChartInfoModelBuilderColorChangedTest`, `StaticColorFrameFactoryTest`, `combined-color-pane.spec.ts` and `static-color-pane.component.spec.ts`.

- [ ] **Step 5: Commit**: `Chart defaults S2b-1, let the colour pane edit up, down and the waterfall Total`.

---

### Task 6: Whole-branch check

- [ ] **Step 1:** Run the core suite: `./mvnw -q test -pl core -Dsurefire.failIfNoSpecifiedTests=false`. Expected: 0 failures. `XSwapperGCThrottleTest` is a known timing flake; re-run any failing class alone and compare with `bug-80455-s2`.
- [ ] **Step 2:** Run the portal suite, `npx ng test portal`, when memory allows. Compare the failing specs with `bug-80455-s2`.
- [ ] **Step 3: Manual checks.** Add a §B8 to `E:/Temp/chart-defaults-s2a-checks-handoff.md`, or start a new handoff file, covering on a built server, in light and dark:
  - a modern candle, stock (with and without Open) and waterfall
  - a classic copy of each
  - an author single colour
  - a colour dimension
  - the reset copy
  - the colour pane's Up/Down and Increase/Decrease/Total, and Reset there
  - PNG and PDF exports
  - brushing a candle

## Interfaces for later slices

- **`VSChartDirectionColors.draws(ctx, info)`** is the one rule for direction colours. S7-7 (a candle's Color field takes no dimension) changes binding, not this rule.
- **The neutral token** is `VSChartPaletteDefaults.directionNeutral(ctx)`. S8-3's value labels on waterfall bars can reuse the label-ink rules S1-6 set up.
