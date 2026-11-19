# Chart Defaults S2b-2 — Ramps, Level Steps and Funnel Order Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** On a modern chart, four colour behaviours change:
- A measure on Color that crosses zero draws the diverging ramp, centred on zero.
- A polygon map's ramp starts at 25%.
- Each deeper sunburst and icicle level steps its group colour toward the card.
- A funnel coloured by its stage takes its colours and legend in funnel order.

Automatic label ink also reaches 4.5:1 on every fill. Classic charts stay pixel-identical and keep their XML.

**Architecture:**
- **S2-9 and S2-10.** One render-only colour frame, `AutoDivergingColorFrame`, takes the place of a seeded Teal ramp when a marked chart draws.
  - It decides "diverging or not" each time its scale is initialised. It centres a diverging scale on zero.
  - It carries the map floor.
  - It is never stored. A new `authorRamp` flag on the stored ramp wrapper tells an author's Teal from the seeded one.
- **S2-11.** A level step is a property of `TreemapElement`, set by the generator under the mark. `TreemapVO` applies it to both the fill and the label ink.
- **S2-12.** Funnel order is a value order that the colour visitor applies to the stage's colour values. A graph-package legend rule stops the marked funnel's stage legend from being reversed.

**Tech Stack:** Java 21, JUnit 5, Mockito, Spring test context (`@SreeHome`), Maven module `core`. Angular 21 / Vitest testing-library for the colour pane (Task 2).

**Spec:** `docs/superpowers/specs/lookfeel/2026-11-06-chart-defaults-design.md`, as amended in `858c2ad03b`. The relevant sections are:
- §3 M1–M9
- §4 R12
- §5 S2: rows S2-9 to S2-12 and the notes blocks "S2b-2 rulings", "S2-9 diverging ramp", "S2-10 map ramp floor", "S2-11 level steps", "Automatic label ink (D152)" and "S2-12 funnel order"
- §7 "Funnel colour and legend order"
- §10 D150–D160
- §11 "Colour, S2-9 to S2-12"

Where this plan and the spec differ, the spec wins; report the difference.

**Verified against:** `bug-80455-s2b2` at `858c2ad03b` (epic `9e3ba9ca8e` + the spec amendment). Paths are under `core/src/main/java/inetsoft/` unless they start with `core/` or `web/`. Line numbers are approximate; find the code by the names given.

## Global Constraints

- **Worktree:** `$WT` is the executor's worktree on branch `bug-80455-s2b2`. Never work in `E:/StyleBI/stylebi-enterprise/community`.
- **Git:**
  - Each git command is its own call, `git -C "$WT" ...`.
  - One commit per task, subject `Chart defaults S2b-2, <what changed>`, last line `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
  - Subagents never run `git add`, `git commit` or `git stash` (a hook blocks them); the controller stages and commits after review. Never push.
- **Scope is the mark (M1).**
  - Only an assembly with a modern `VizMark` changes.
  - A classic chart renders pixel-identical, and its XML is byte-identical on load and save.
  - Graph-package code (`inetsoft.graph.*`) never reads a mark. It reads a property that `GraphGenerator` sets from `vizContext`.
- **The author wins (M4).** An author's ramp (flagged), an author's colour per value, an author's sort on a colour field and an author's text colour all win over these rules.
- **Values (from the spec):**

  | Item | Value |
  |---|---|
  | Diverging test | scale minimum < 0 < scale maximum; centred scale = −M…+M, M = max(\|min\|, \|max\|) |
  | Map floor | ratio r draws the ramp at `0.25 + 0.75 * r` |
  | Level step | node n levels below the base, n = min(n, 3), mixed toward the card in OKLab by `1 - Math.pow(0.62, n)` (38%, 62%, 76%) |
  | Card composite | `VSObjectChromeDefaults.cardComposite(ctx)`: `#ECEBE9` light, `#313034` dark |
  | Contrast ink | black when `(L + 0.05) / 0.05 >= 1.05 / (L + 0.05)`, L = `GTool.getLuminance(bg)`; else white |
  | Classic ink | today's `GTool.getTextColor`: black when L > 0.250 |
  | Funnel order | the reverse of the stage's ascending value order (largest total first) |

- **Comments:** each comment is a short clause, with no ticket, PR, slice or design-doc names. No `.html` file is touched.
- **New files** start with the AGPL header of `SeedChromeDefaultsTest.java`, `Copyright (C) 2026  InetSoft Technology`.
- **Tests that reach Spring beans** use:
  - `@ExtendWith(SpringExtension.class)`
  - `@ContextConfiguration(classes = { BaseTestConfiguration.class, LibManagerTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)`
  - `@SreeHome` and `@Tag("core")`
  - In `@AfterEach`, clear `viewsheet.modernVisualization`, `viewsheet.darkMode` and `viewsheet.density`.
  - Graph-package unit tests need no Spring.

**Known limitations (accepted in the spec):**
- **Older builds** ignore all four rules. They draw a stored Teal as Teal, with no floor, no steps and today's funnel order.
  - When such a build writes the asset, it drops `authorRamp` (M9). An author's Teal then counts as seeded here on the next open.
- **The colour pane** reads the composer's last draw, so it can lag one draw behind a binding change (D153).
- **A data tip or flyover view** may switch ramps on its own rows, just as its ramp already re-ranges.
- **A funnel with a partner chart** keeps the dashboard's shared picks (D150). Only its legend order changes.

## Review Focus

1. **An ordinary binding edit on a chart whose seeded Teal draws as Variance** must keep the stored Teal unflagged, or the chart freezes into a min-to-max Variance. Pinned in Task 2 by `anUnchangedVarianceEchoKeepsTheSeededTeal`.
2. **The legend's "include zero", "reversed" and logarithmic options** rebuild the colour scale after the visitor. A diverging chart must stay centred, and a logarithmic one must not diverge. Pinned in Task 3 by `aRebuiltScaleIsCentredAgain`, `aReversedScaleIsCentred` and `aLogScaleNeverDiverges`.
3. **A funnel that has already drawn in this session** holds derived colour picks in its stored frame. It must still take funnel order on its next draw. Pinned in Task 9 by `derivedPicksFromAnEarlierDrawGiveWay`.
4. **A brushed polygon map** builds its polygon frames in `applyBrushing`, not `initColorFrame`, and must keep the floor. Pinned in Task 6 by `aBrushedMapKeepsTheFloor`.
5. **A sunburst coloured by a field that is not one of its levels** (for example Region, over Division and Department) must get no steps. Pinned in Task 8 by `aColourFieldOutsideTheTreeGetsNoSteps`.

## File Structure

| Task | Files |
|---|---|
| 1 Author flag stored, honoured by seeds and reset | `uql/viewsheet/graph/aesthetic/LinearColorFrameWrapper.java`; `uql/viewsheet/internal/ChartVSAssemblyInfo.java` (`isOtherMarkSeededLinearFrame` and its 3 callers); `uql/viewsheet/internal/ChartChromeSlots.java` (ramp slot in `frames`) |
| 2 Author flag stamped | `web/projects/portal/src/app/binding/editor/chart/aesthetic/linear-color-pane.component.ts` (`switchColorModel`); `web/binding/model/graph/aesthetic/ColorFrameModel.java`; `web/binding/service/graph/aesthetic/VisualFrameModelFactoryService.java`; `report/script/AbstractChartBindingScriptable.java` (`setColorFrame`) |
| 3 The render-only frame | new `graph/aesthetic/AutoDivergingColorFrame.java` |
| 4 Drawing the diverging ramp | new `report/composition/graph/DivergingRamps.java`; `report/composition/graph/VSFrameVisitor.java` (`createVisualFrame`); `report/composition/graph/GraphGenerator.java` (`initRelationElement`) |
| 5 The pane shows what draws | `uql/viewsheet/graph/VSChartInfo.java` (runtime field); `report/composition/graph/VGraphPair.java` (after `plotAndLayout`); `web/binding/service/graph/AestheticRefModelFactory.java` (`createAestheticInfo`) |
| 6 Map floor | `report/composition/graph/MapGenerator.java` (`initColorFrame`, `applyBrushing`) |
| 7 Contrast ink | `graph/internal/GTool.java`; `graph/guide/VLabel.java`; `graph/element/GraphElement.java`; the five `setAutoBackground` callers (`graph/visual/BarVO.java`, `MekkoVO.java`, `PolygonVO.java`, `RelationVO.java`, `TreemapVO.java`); `GraphGenerator.applyModernMarkRules` |
| 8 Level steps | `graph/element/TreemapElement.java`; `graph/visual/TreemapVO.java`; `GraphGenerator.applyModernMarkRules` |
| 9 Funnel order | new `report/composition/graph/FunnelColorOrder.java`; `report/composition/graph/VSColorFrameStrategy.java`; `VSFrameVisitor.java` (`createVisualFrame`, `syncCategoricalFrame`, `syncColors`, `sortValues`); `graph/guide/legend/Legend.java` (`isReversed`); `GraphGenerator.java` (where `cstrategy` is built) |
| 10 Whole-branch check | — |

## How to run tests

From `$WT`:

```bash
./mvnw -q test -pl core "-Dtest=ClassA,ClassB" -Dsurefire.failIfNoSpecifiedTests=false
```

- Read `core/target/surefire-reports/*.txt` for the counts.
- If compilation fails with "Attempt to recreate … HeartBeatServiceProxy", delete the untracked `core/target/generated-test-sources`.
- Portal TL specs run from `$WT/web` after `npm ci`, one file only:

  ```bash
  npx ng run portal:test-tl --include='**/linear-color-pane.component.tl.spec.ts'
  ```

  `ng test portal` silently matches no TL file, and a junctioned `node_modules` breaks Vitest.

---

### Task 1: The author's ramp flag, stored and honoured

S2-9, D154. Review Focus 1 depends on this flag.

**Files:**
- Modify: `uql/viewsheet/graph/aesthetic/LinearColorFrameWrapper.java`
- Modify: `uql/viewsheet/internal/ChartVSAssemblyInfo.java`:
  - `isOtherMarkSeededLinearFrame` (~:383)
  - its callers in `seedColorPalette` (~:304) and `seedColorFrame` (~:365)
- Modify: `uql/viewsheet/internal/ChartChromeSlots.java`, the ramp slot inside `frames(...)` (~:472-478)
- Test:
  - new `core/src/test/java/inetsoft/uql/viewsheet/graph/aesthetic/LinearColorFrameWrapperAuthorRampTest.java` (no Spring)
  - extended `ChartColorTypeSeedTest` and `ChartChromeSlotsTest`

**Interfaces:**
- Produces on `LinearColorFrameWrapper`:
  - `boolean isAuthorRamp()` and `void setAuthorRamp(boolean)`, default false.
  - Written by overriding `writeAttributes` (call super, then append ` authorRamp="true"` only when true) and `parseAttributes` (call super, then read the attribute).
  - Included in `equals` and `equalsContent`. The clone copies it, because the field is primitive.
- Produces in `ChartVSAssemblyInfo`: `private static boolean isOtherMarkSeededLinearFrame(VisualFrameWrapper wrapper, VizContext ctx)`.
  - It returns false for a `LinearColorFrameWrapper` whose `isAuthorRamp()` is true. Otherwise it applies today's class test to `wrapper.getVisualFrame()`.
  - Callers pass `info.getColorFrameWrapper()` and `ref.getVisualFrameWrapper()`.
- Produces in `ChartChromeSlots`: the ramp slot also handles a frame whose class already matches the default ramp. When that frame's wrapper (`wrapper.on(info)`) is a flagged `LinearColorFrameWrapper`, the slot clears the flag.

- [ ] **Step 1: Write the failing tests.**
  - In `LinearColorFrameWrapperAuthorRampTest`:
    - `theFlagRoundTrips`:
      - A `TealColorFrameWrapper` with `setAuthorRamp(true)` writes its XML.
      - Parsing that XML with `VisualFrameWrapper.createVisualFrame(element)` gives a `TealColorFrameWrapper` whose `isAuthorRamp()` is true.
    - `anUnflaggedRampWritesNoAttribute`: the XML of a default `TealColorFrameWrapper` does not contain `authorRamp`.
    - `equalsContentSeesTheFlag`
    - `aCloneKeepsTheFlag`
  - In `ChartColorTypeSeedTest`:
    - `revertKeepsAnAuthorsTeal`: a marked chart whose colour field has a flagged Teal still holds a flagged `TealColorFrame` after `VizModernizeUtil.revert`.
    - `modernizeKeepsAnAuthorsBlues`: a classic chart's flagged Blues stays Blues after `modernize`.
    - `revertStillSwapsASeededTeal`: an unflagged Teal becomes `BluesColorFrame` on Revert, as today.
    - `revertKeepsAnAuthorsChartLevelTeal`: the chart-level frame (`info.setColorFrame`), flagged.
  - In `ChartChromeSlotsTest`:
    - `theResetCopyClearsTheAuthorRampFlag`: a flagged Teal on the colour field is an unflagged Teal on the reset copy.
    - `theResetCopyStillReplacesAnAuthorsVariance`: an author's Variance becomes Teal, as today.

- [ ] **Step 2: Run them to verify they fail.** Expected: compile failure (no `setAuthorRamp`).

- [ ] **Step 3: Implement** as under Interfaces.

- [ ] **Step 4: Run them to verify they pass,** plus `SeededLinearFrameTest,ChartVSAssemblyInfoSeedTest,VizModernizeUtilTest,VizModernizeResetTest,VizModernizeResidueTest`. Expected: all pass.

- [ ] **Step 5: Commit**: `Chart defaults S2b-2, store an author's ramp pick and keep it through Modernize and Revert`.

---

### Task 2: Stamp the flag on an author's pick

S2-9, D153, D154. Review Focus 1.

**Files:**
- Modify: `web/projects/portal/src/app/binding/editor/chart/aesthetic/linear-color-pane.component.ts`, `switchColorModel` (~:248-279)
- Modify: `web/binding/model/graph/aesthetic/ColorFrameModel.java` (the wrapper constructor)
- Modify: `web/binding/service/graph/aesthetic/VisualFrameModelFactoryService.java` (`updateVisualFrameWrapper`, `getVisualFrameWrapper`)
- Modify: `report/script/AbstractChartBindingScriptable.java`, `setColorFrame` (~:563)
- Test:
  - new `core/src/test/java/inetsoft/web/binding/service/graph/aesthetic/RampAuthorPickTest.java`, built like `CategoricalColorFactoryResolvedDefaultGuardTest`: `new VisualFrameModelFactoryService(factories)` with the `ColorFrameModelFactory` Teal, Variance and Blues factories
  - new `core/src/test/java/inetsoft/report/script/viewsheet/RampScriptAuthorTest.java`, with the harness of `LegendScriptAuthorTest`
  - extended `linear-color-pane.component.tl.spec.ts`

**Interfaces:**
- Consumes: Task 1 `isAuthorRamp` and `setAuthorRamp`.
- **Client.** In `switchColorModel`, the two branches that clone a ramp model (`this[val]` and `this[val.substring(...)]`) set `this.frame.changed = true` before emitting. The gradient and heat branches are unchanged.
- **`ColorFrameModel(ColorFrameWrapper wrapper)`.** `setChanged(wrapper.isChanged() || wrapper instanceof LinearColorFrameWrapper ramp && ramp.isAuthorRamp())`.
- **`VisualFrameModelFactoryService.getVisualFrameWrapper`.** First, when all of these hold, return `wrapper` unchanged:
  - `wrapper` is a `TealColorFrameWrapper` whose `isAuthorRamp()` is false
  - `model` is a `VarianceColorModel`
  - `!model.isChanged()`

  This is the automatic Variance coming back from the pane (D153).
- **`VisualFrameModelFactoryService.updateVisualFrameWrapper`.** After the factory update, when the result is a `LinearColorFrameWrapper` and `model.isChanged()`, call `setAuthorRamp(true)`. It never clears the flag.
- **`setColorFrame`.** After `ref.setVisualFrame(frame)`, when `ref.getVisualFrameWrapper()` is a `LinearColorFrameWrapper`, call `setAuthorRamp(true)`.

- [ ] **Step 1: Write the failing tests.**
  - In `RampAuthorPickTest`:
    - `aPickedRampIsStamped`: on an unflagged Teal wrapper, a `VarianceColorModel` with `changed = true` gives a `VarianceColorFrameWrapper` with `isAuthorRamp()`.
    - `aRePickedTealIsStamped`: a `TealColorModel` with `changed = true` on an unflagged Teal wrapper gives a flagged Teal.
    - `anOrdinaryApplyDoesNotStamp`: a `TealColorModel` with `changed = false` leaves the Teal unflagged.
    - `anUnchangedVarianceEchoKeepsTheSeededTeal`: a `VarianceColorModel` with `changed = false` on an unflagged Teal returns the same `TealColorFrameWrapper`, still unflagged.
    - `anUnchangedVarianceOnAnAuthorsTealReplacesIt`: on a flagged Teal, today's class switch still applies.
    - `aFlaggedRampSendsChanged`: `createVisualFrameModel` of a flagged Teal has `isChanged()` true; an unflagged one has false.
  - In `RampScriptAuthorTest`: `aScriptRampIsTheAuthors`. After the script `setColorFrame(new TealColorFrame())` on a chart whose colour field is a measure, the colour field's wrapper is a `TealColorFrameWrapper` with `isAuthorRamp()`.
  - In `linear-color-pane.component.tl.spec.ts`, in the `switchColorModel` group:
    - `"should mark a ramp model changed when the author picks it"`: switch to the Teal model's class; the emitted frame has `changed === true`.
    - `"should not mark the gradient model changed"`

- [ ] **Step 2: Run them to verify they fail.**

- [ ] **Step 3: Implement** as under Interfaces.

- [ ] **Step 4: Run them to verify they pass,** plus `StaticColorSeedTest,CategoricalColorFactoryResolvedDefaultGuardTest,LegendScriptAuthorTest` and the whole `linear-color-pane.component.tl.spec.ts`.

- [ ] **Step 5: Commit**: `Chart defaults S2b-2, record the colour pane's and a script's ramp pick as the author's`.

---

### Task 3: The render-only diverging Teal

S2-9, S2-10, D155, D156, D159. Review Focus 2.

**Files:**
- Create: `graph/aesthetic/AutoDivergingColorFrame.java`
- Test: `core/src/test/java/inetsoft/graph/aesthetic/AutoDivergingColorFrameTest.java` (no Spring)

**Interfaces:**
- Produces: `public class AutoDivergingColorFrame extends TealColorFrame`:
  - `public static AutoDivergingColorFrame from(TealColorFrame teal)`: copies the field, the legend spec, the scale option and the scale (when set).
  - `public boolean isDiverging()`
  - `public double getLowStop()`, `public void setLowStop(double lowStop)`, default 0
  - `@Override public void init(DataSet data)`: calls super, then decides:
    - Diverging when the scale is a `LinearScale` that is not a `LogScale`, and the sign test holds.
    - The sign test reads the raw minimum and maximum of the column `ElementVO.ALL_PREFIX + getField()` when `data.indexOfHeader` finds it. Otherwise it reads the scale's `getMin()` and `getMax()`.
    - When diverging, it sets the scale to `setMin(-m)` and `setMax(m)`, where m is the larger of the scale's own |min| and |max|, and switches the interpolated colours to Variance's stops.
    - When not diverging, it uses Teal's stops.
    - It decides again on every call, so a rebuilt scale is decided again.
  - `@Override public Color getColor(double ratio)`: while diverging, the ratio is used as given. Otherwise, with `lowStop > 0`, it is remapped to `lowStop + (1 - lowStop) * ratio`.
  - `equals` and `hashCode` add `diverging` and `lowStop` to the class test.
  - Variance's stops come from `new VarianceColorFrame().getColorRamps()` (same package); switch with the inherited protected `setColors`.

- [ ] **Step 1: Write the failing tests.** Compare colours with reference frames, not hex literals, because the ramps are B-splines:
  - `staysTealWhenTheRangeIsPositive`:
    - Values 1 to 5 → not diverging.
    - `getColor(0.5)` equals `new TealColorFrame().getColor(0.5)`.
  - `touchingZeroStaysTeal`: values 0 to 5 → not diverging.
  - `crossingZeroDrawsVarianceCentredOnZero`:
    - Values −2 and 6 → diverging.
    - The scale's minimum equals minus its maximum.
    - `getColor((Object) 0.0)` equals `new VarianceColorFrame().getColor(0.5)`.
    - The colour at the scale maximum equals Variance's `getColor(1.0)`.
  - `aRebuiltScaleIsCentredAgain`: after `setScaleOption(getScaleOption() | Scale.ZERO)`, `setScale(null)` and `init(data)` again, the frame is still diverging and centred.
  - `aReversedScaleIsCentred`: `setScale(new LinearScale())` with `setReversed(true)` and `setFields(field)`, then `init(data)` → still centred.
  - `aLogScaleNeverDiverges`: a `LogScale` → not diverging.
  - `theAllDataColumnDecides`: the plain column holds 1 to 3, and `"__all__" + field` holds −2 to 3 → diverging.
  - `aLaterPositiveRangeReturnsToTeal`: init on crossing data, then a new scale on positive data → not diverging, and the colours are Teal's again.
  - `lowStopRemapsOnlyTeal`:
    - lowStop 0.25 on positive data: `getColor(0.0)` equals Teal's `getColor(0.25)`.
    - lowStop 0.25 on crossing data: `getColor(0.5)` equals Variance's `getColor(0.5)`.
  - `fromCopiesTheTealsSettings`
  - `equalsSeesDivergingAndLowStop`
  - `aCloneKeepsItsState`

- [ ] **Step 2: Run it to verify it fails.**

- [ ] **Step 3: Implement** as under Interfaces.

- [ ] **Step 4: Run it to verify it passes,** plus `HouseRampTest,ChartRampDerivationTest`.

- [ ] **Step 5: Commit**: `Chart defaults S2b-2, add a Teal that draws the diverging ramp when its data crosses zero`.

---

### Task 4: Draw it on marked charts

S2-9, D155, D159.

**Files:**
- Create: `report/composition/graph/DivergingRamps.java`
- Modify: `report/composition/graph/VSFrameVisitor.java`, `createVisualFrame`, right after `frame = (VisualFrame) frame.clone();` (~:404)
- Modify: `report/composition/graph/GraphGenerator.java`, `initRelationElement`, after the node colour clone (~:1495)
- Test: new `core/src/test/java/inetsoft/report/composition/graph/DivergingRampRenderTest.java` (Spring; harness of `ChartColorRenderTest.sizeLegendSpec`)

**Interfaces:**
- Consumes: Task 1 `isAuthorRamp`; Task 3 `AutoDivergingColorFrame`.
- Produces: `final class DivergingRamps`:
  - `static VisualFrame substitute(VisualFrame frame, VisualFrameWrapper wrapper, VizContext ctx)`:
    - Returns `AutoDivergingColorFrame.from((TealColorFrame) frame)` when all of these hold:
      - `ctx != null && ctx.modern`
      - `frame.getClass() == TealColorFrame.class`
      - `wrapper` is not a flagged `LinearColorFrameWrapper`
    - Otherwise it returns `frame`.
  - `static Set<String> drawnFields(EGraph graph)`: the fields of every `AutoDivergingColorFrame` that `isDiverging()`. It looks among each element's colour frame (unwrapping `CompositeColorFrame` through `getFrameCount`/`getFrame`) and each `RelationElement`'s `getNodeColorFrame()`. Task 5 uses it.
- In `createVisualFrame`, when `strategy` is a `VSColorFrameStrategy`, use `DivergingRamps.substitute(frame, tref.getVisualFrameWrapper(), ((VSColorFrameStrategy) strategy).getVizContext())`.
- In `initRelationElement`, use `substitute(colorFrame, rinfo.getNodeColorField().getVisualFrameWrapper(), vizContext)`.

- [ ] **Step 1: Write the failing tests.** In `DivergingRampRenderTest`, build a point chart with a measure on Color through `GraphGenerator.getGenerator(...).createEGraph()` and read `graph.getElement(0).getColorFrame()`:
  - `aMarkedChartCrossingZeroDrawsVariance`: colour values −0.6 to 0.6 on `VizMark.MODERN_LIGHT` → an `AutoDivergingColorFrame` that is diverging, and the scale's minimum equals minus its maximum.
  - `aPositiveMeasureStaysTeal`: values 1 to 9 → `AutoDivergingColorFrame`, not diverging.
  - `aClassicChartIsNotSubstituted`: a classic chart whose colour field holds a `TealColorFrame` draws a frame whose class is exactly `TealColorFrame`.
  - `anAuthorsTealIsNotSubstituted`: flagged → the class is exactly `TealColorFrame`.
  - `anAuthorsVarianceIsNotCentred`: a flagged Variance on crossing data keeps its scale minimum −0.6 and maximum 0.6.
  - `aBrushedTargetDecidesOnAllData`: `adata` crosses zero while `data` holds only positive rows → diverging.
  - `aRelationNodeColourFollowsTheRule`: a marked tree whose node colour is a measure crossing zero. After `Plotter.getPlotter(graph).plot(data)`, the element's `getNodeColorFrame()` is a diverging `AutoDivergingColorFrame`.
  - `drawnFieldsNamesTheDivergingField`: `DivergingRamps.drawnFields(graph)` holds the colour field's name on the crossing chart and is empty on the positive one.

- [ ] **Step 2: Run it to verify it fails.**

- [ ] **Step 3: Implement** as under Interfaces.

- [ ] **Step 4: Run it to verify it passes,** plus `ChartColorRenderTest,ChartColorTypeSeedTest,GeneratedLegendOrderTest,VGraphPairModernPaletteTest`.

- [ ] **Step 5: Commit**: `Chart defaults S2b-2, draw a seeded Teal that crosses zero as the diverging ramp`.

---

### Task 5: The colour pane shows the drawn ramp

S2-9, D153.

**Files:**
- Modify: `uql/viewsheet/graph/VSChartInfo.java` (a runtime field beside `linkVarTable`)
- Modify: `report/composition/graph/VGraphPair.java`, right after `vgraph = plotter1.plotAndLayout(...)` (~:755)
- Modify: `web/binding/service/graph/AestheticRefModelFactory.java`, `createAestheticInfo` (~:39)
- Test: new `core/src/test/java/inetsoft/web/binding/service/graph/AestheticRefModelDivergingTest.java`

**Interfaces:**
- Consumes: Task 1 flag; Task 4 `DivergingRamps.drawnFields`.
- Produces on `VSChartInfo`: `Set<String> getDivergingColorFields()` and `void setDivergingColorFields(Set<String> fields)`.
  - Never null; the default is `Set.of()`; the setter stores `Set.copyOf`.
  - Not written, parsed or compared, like `linkVarTable`.
- **`VGraphPair`.** On a `Viewsheet.SHEET_DESIGN_MODE` box, store `DivergingRamps.drawnFields(egraph)` on `ainfo.getVSChartInfo()`.
- **`createAestheticInfo`.** Send a `new VarianceColorModel()` (with `setField(aref.getFullName())`, `changed` false) when all of these hold:
  - `cinfo` is a `VSChartInfo` whose `getDivergingColorFields()` contains `aref.getFullName()`
  - the ref's wrapper is an unflagged `TealColorFrameWrapper`

- [ ] **Step 1: Write the failing tests.** In `AestheticRefModelDivergingTest`, build the factory with a `VisualFrameModelFactoryService` as Task 2 does and a mocked `ChartRefModelFactoryService`:
  - `aDrawnDivergingTealIsSentAsVariance`
  - `aFlaggedTealIsSentAsTeal`
  - `aTealNotDrawnDivergingIsSentAsTeal`
  - `theRoundTripKeepsTheSeededTeal`: feed the sent model back through `pasteAestheticRef`; the ref still holds the same unflagged Teal wrapper (Task 2's rule).

- [ ] **Step 2: Run it to verify it fails.**

- [ ] **Step 3: Implement** as under Interfaces.

- [ ] **Step 4: Run it to verify it passes,** plus `RampAuthorPickTest,VGraphPairLegendLayoutTest`.

- [ ] **Step 5: Commit**: `Chart defaults S2b-2, show the drawn diverging ramp in the colour pane`.

---

### Task 6: The map ramp floor

S2-10, R12, D156. Review Focus 4.

**Files:**
- Modify: `report/composition/graph/MapGenerator.java`:
  - a new private `applyRampFloor()`
  - called at the start of `applyBrushing` (~:184) and of `initColorFrame` (~:494)
- Test: new `core/src/test/java/inetsoft/report/composition/graph/MapRampFloorTest.java` (Spring; harness of `ChartColorRenderTest.regionColor`, with a measure on Color)

**Interfaces:**
- Consumes: Task 3 `setLowStop`; Task 4 substitution.
- `applyRampFloor()` calls `setLowStop(0.25)` on `getColorFrame(null)` when all of these hold:
  - `vizContext.modern`
  - `GraphUtil.containsMapPolygon(info)`
  - `!GraphUtil.containsMapPoint(info)`
  - `!GraphTypes.isContour(getChartType())`
  - the frame is an `AutoDivergingColorFrame`

  It is idempotent. Brushing's clones carry the value.

- [ ] **Step 1: Write the failing tests.** For a polygon-only marked map with a measure on Color, find the polygon element's composite colour frame, and in it the `AutoDivergingColorFrame`:
  - `aMarkedPolygonMapFloorsItsRamp`: lowStop 0.25, and `getColor(0.0)` equals Teal's `getColor(0.25)`.
  - `aPointMapHasNoFloor`
  - `aContourMapHasNoFloor`
  - `anAuthorsRampHasNoFloor`: a flagged Teal is not substituted.
  - `aDivergingMapHasNoFloor`: on crossing data, `getColor(0.0)` equals Variance's `getColor(0.0)`.
  - `aBrushedMapKeepsTheFloor`: with `adata` set, the polygon frames built in `applyBrushing` hold an `AutoDivergingColorFrame` with lowStop 0.25.
  - `aClassicMapIsUnchanged`: the frame class is exactly the stored class, with no `AutoDivergingColorFrame`.

- [ ] **Step 2: Run it to verify it fails.**

- [ ] **Step 3: Implement** as under Interfaces.

- [ ] **Step 4: Run it to verify it passes,** plus `ChartColorRenderTest`.

- [ ] **Step 5: Commit**: `Chart defaults S2b-2, start a modern map's ramp at a quarter of its range`.

---

### Task 7: Labels on marks reach 4.5:1

D152, C21.

**Files:**
- Modify: `graph/internal/GTool.java`: an overload beside `getTextColor(Color)` (~:1260)
- Modify: `graph/guide/VLabel.java`: a field, its setter and getter, and `getColor()` (~:1271)
- Modify: `graph/element/GraphElement.java`: a field beside `autoTextColor` (~:901-911, `equalsContent` ~:1096)
- Modify: each `setAutoBackground` caller: `BarVO.java:~718`, `MekkoVO.java:~72`, `PolygonVO.java:~250`, `RelationVO.java:~68`, `TreemapVO.java:~111`
- Modify: `GraphGenerator.applyModernMarkRules` (~:5209)
- Test: new `core/src/test/java/inetsoft/graph/internal/GToolContrastInkTest.java` (no Spring); extended `ModernMarkRulesTest`

**Interfaces:**
- Produces:
  - `public static Color GTool.getTextColor(Color bg, boolean maxContrast)`:
    - when `maxContrast` is false, it is today's rule
    - when true, it applies the contrast-ink formula in Global Constraints and returns `Color.BLACK` or `Color.WHITE`
  - `VLabel.setContrastInk(boolean)` and `isContrastInk()`. `getColor()` calls `GTool.getTextColor(autoBG, contrastInk)`.
  - `GraphElement.setContrastInk(boolean)` and `isContrastInk()`, default false, in `equalsContent`.
- Each `setAutoBackground` caller adds `vtext.setContrastInk(elem.isContrastInk())`.
- `applyModernMarkRules` calls `elem.setContrastInk(true)` for every element, after the `vizContext.modern` early return and before the type branches.

- [ ] **Step 1: Write the failing tests.**
  - In `GToolContrastInkTest`:
    - `theHigherContrastInkWins`: `#E5197E` gives black with `maxContrast` and white without.
    - `everyGreyReaches4_58`: for each grey 0–255 with `maxContrast`, the chosen ink's contrast is ≥ 4.58, using the WCAG formula with `GTool.getLuminance`.
    - `aLabelUsesIt`: `new VLabel("x")` with `setAutoBackground(new Color(0xE5197E))`:
      - `setContrastInk(true)` → `getColor()` is `Color.BLACK`
      - `false` → `Color.WHITE`
  - In `ModernMarkRulesTest`: `markedElementsUseContrastInk`: a marked chart's elements are `isContrastInk()`; a classic chart's are not.

- [ ] **Step 2: Run them to verify they fail.**

- [ ] **Step 3: Implement** as under Interfaces.

- [ ] **Step 4: Run them to verify they pass,** plus every test class that `grep -rlE "getTextColor|AutoBackground|isAutoTextColor" core/src/test` lists. Expected: all pass. A classic expectation that changes means the flag leaked onto a classic chart.

- [ ] **Step 5: Commit**: `Chart defaults S2b-2, give labels on a modern mark the higher-contrast ink`.

---

### Task 8: Sunburst and icicle level steps

S2-11, D151, D157. Review Focus 5.

**Files:**
- Modify: `graph/element/TreemapElement.java`: fields, accessors, `equalsContent` (~:602) and `clone` (~:614)
- Modify: `graph/visual/TreemapVO.java`: the label auto background (~:110-112) and the fill (~:212)
- Modify: `GraphGenerator.applyModernMarkRules`: a `TreemapElement` branch
- Test:
  - new `core/src/test/java/inetsoft/graph/element/TreemapLevelStepTest.java` (no Spring)
  - new `core/src/test/java/inetsoft/report/composition/graph/LevelStepRenderTest.java` (Spring)

**Interfaces:**
- Consumes: Task 7 `GTool.getTextColor(bg, true)` and `isContrastInk`.
- Produces on `TreemapElement`:
  - `void setLevelStep(int base, Color card)`, `int getLevelStepBase()` and `Color getLevelStepCard()`. A base of −1 (the default) means no steps. Both values are in `equalsContent`.
  - `public Color getLevelColor(Color color, int k)`:
    - k is the node's tree-dim index (0 is the top level), as `TreemapVO` computes it: `getTreeDimCount() - level - 1`.
    - It returns `color` when the base is −1, the card is null, color is null, or `k <= base`.
    - Otherwise it mixes in OKLab toward the card by `1 - Math.pow(0.62, Math.min(k - base, 3))`: `OKLab.fromColor` on both colours, a linear mix, then `OKLab.toColorInGamut` on `toLCH` of the mix.
    - The colour's alpha is kept.
- **`TreemapVO`.** Both `obj.getColor(0)` at the label (~:111) and `gobj.getColor(0)` at the fill (~:212) go through `elem.getLevelColor(c, k)`. In `paint`, k = `elem.getTreeDimCount() - level - 1`.
- **The `applyModernMarkRules` branch.** For a `TreemapElement` whose `getMapType()` is `SUNBURST` or `ICICLE`, call `setLevelStep(base, VSObjectChromeDefaults.cardComposite(vizContext))`. The base is:
  - 0 when `info.getColorField()` is null
  - −1 when the colour field's frame is a `LinearColorFrame`
  - otherwise `treemap.getTreeDims().indexOf(colorField.getFullName())`, which is −1 when the field is not a level

- [ ] **Step 1: Write the failing tests.**
  - In `TreemapLevelStepTest`, with the expected colours from the spec (each channel within 1):
    - `noBaseNoStep`
    - `theBaseLevelKeepsItsColour`
    - `oneLevelDownMixes38Percent`: `#0490FF` on `#ECEBE9` → `#77B6FA`.
    - `threeLevelsDownMixes76Percent`: → `#C0D7F1`.
    - `deeperLevelsStayAtTheThirdStep`: k − base = 5 gives the same colour as 3.
    - `aDarkCardStepsDeeper`: `#4FA5FF` on `#313034`, one level → `#4877AC`.
    - `brightSlotsNeverTurnWhite`: `#FFB020`, three levels on the light card → `#F2DEC3`.
    - `alphaIsKept`: a colour with alpha 128 keeps alpha 128.
    - `everyHeadSlotKeepsItsLabelAtEveryStep`:
      - For the first 8 colours of `VSChartPaletteDefaults.staticPalette(ctx)`, light and dark, at steps 0 to 3 on that mark's card.
      - The contrast of `GTool.getTextColor(step, true)` against the step is ≥ 4.5.
    - `equalsContentAndCloneCoverTheStep`
  - In `LevelStepRenderTest`, a sunburst whose tree dims are Division then Department:
    - `colourByTheTopLevelStepsBelowIt`: base 0, and the card is `#ECEBE9`.
    - `colourByTheSecondLevel`: base 1.
    - `aStaticSunburstStepsFromTheTop`: no colour field → base 0.
    - `aMeasureOnColourGetsNoSteps`: −1.
    - `aColourFieldOutsideTheTreeGetsNoSteps`: coloured by Region → −1.
    - `anIcicleSteps`: base 0.
    - `aTreemapGetsNoSteps`
    - `aClassicSunburstGetsNoSteps`
    - `aDarkMarkUsesTheDarkCard`: `#313034`.
    - `theLabelInkReadsTheSteppedColour`: after `Plotter.getPlotter(graph).plot(data)`, a second-level node's label `getAutoBackground()` equals `elem.getLevelColor(groupColour, 1)`.

- [ ] **Step 2: Run them to verify they fail.**

- [ ] **Step 3: Implement** as under Interfaces.

- [ ] **Step 4: Run them to verify they pass,** plus `ModernMarkRulesTest` and any treemap test (`find core/src/test -iname "*Treemap*"`).

- [ ] **Step 5: Commit**: `Chart defaults S2b-2, step deeper sunburst and icicle levels toward the card`.

---

### Task 9: Funnel colours and legend in funnel order

S2-12, D150, D158, D159, D160. Review Focus 3.

**Pre-flight (record, don't block):** §7 asks whether `applyFunnelSort` also sorts a colour ref that names the stage's column. This task reads the **design** colour ref's order for D159 and builds funnel order from the runtime stage ref, so the answer does not change the code. Record what the Task 10 live checks show.

**Files:**
- Create: `report/composition/graph/FunnelColorOrder.java`
- Modify: `report/composition/graph/VSColorFrameStrategy.java`
- Modify: `report/composition/graph/VSFrameVisitor.java`:
  - `createVisualFrame` (~:393-531)
  - a `syncCategoricalFrame` overload (~:578)
  - `syncColors(CategoricalColorFrame, DataSet, …)` (~:696)
  - `sortValues` (~:960)
  - `resolveColumnName` (~:538) becomes `static String resolveColumnName(String col, AestheticRef tref, int sourceType)`, package-visible, and its one caller passes `sourceType`
- Modify: `report/composition/graph/GraphGenerator.java`, right after `cstrategy = new VSColorFrameStrategy(info, vizContext);` (~:332)
- Modify: `graph/guide/legend/Legend.java`, `isReversed` (~:665)
- Test:
  - new `core/src/test/java/inetsoft/report/composition/graph/FunnelColorOrderTest.java` (Spring)
  - new `core/src/test/java/inetsoft/report/composition/graph/FunnelColorOrderRenderTest.java` (Spring; harness of `GeneratedLegendOrderTest`)
  - extended `core/src/test/java/inetsoft/graph/guide/legend/StackedLegendOrderTest.java`

**Interfaces:**
- Produces: `final class FunnelColorOrder`:
  - `static XDimensionRef stage(VizContext ctx, VSChartInfo info)`: the runtime stage ref, or null. Non-null only when all of these hold:
    - `ctx != null && ctx.modern`
    - `GraphTypes.isFunnel(info.getRTChartType())`
    - `info.getColorField()` holds a `CategoricalColorFrame`, and its `getFullName()` equals the last of `info.getRTYFields()`, which is an `XDimensionRef`
    - the colour field's design ref (`getDataRef()`) is an `XDimensionRef` whose `getOrder() == XConstants.SORT_ASC` (D159)
  - `static boolean standalone(VSChartInfo info, Viewsheet vs, String chartName, int sourceType)`:
    - true when the colour frame's `isShareColors()` is false, or `vs` is null
    - true when no other `ChartVSAssembly` in `vs` (absolute name ≠ `chartName`) has a categorical colour ref with `isShareColors()` whose `VSFrameVisitor.resolveColumnName` matches this one's
    - Its refs are `AbstractChartInfo.getAestheticRefs(info)`, plus a `RelationChartInfo`'s node colour field.
  - `static Comparator<Object> topFirst(XDimensionRef stage, DataSet data)`:
    - `stage.createComparator(data)`, else `data.getComparator(stage.getFullName())`
    - wrapped with `DataSetComparator.getComparator(comp, data)` and reversed
    - null when neither exists
- Produces on `VSColorFrameStrategy`: `void setFunnelOrder(XDimensionRef stage, boolean standalone)`, `XDimensionRef getFunnelStage()` and `boolean isFunnelStandalone()`.
- **`GraphGenerator`.** When `FunnelColorOrder.stage(vizContext, info)` is non-null, call `cstrategy.setFunnelOrder(stage, FunnelColorOrder.standalone(info, chart.getViewsheet(), chart.getAbsoluteName(), sourceType))`.
- **`VSFrameVisitor.createVisualFrame`.** It applies only when the strategy's funnel stage is set, its full name equals `col`, and the frame is a `CategoricalColorFrame`. Then, with `order = FunnelColorOrder.topFirst(stage, this.data)`:
  - **Standalone:** call `clearDerivedColors()` on the render clone. Skip the shared-frame read and write (take the plain `syncCategoricalFrame` branch). Pass `order` to both sync calls.
  - **Both cases:** after the second sync, re-sort the scale values by `order` and `((CategoricalScale) frame.getScale()).init(vals)`. The legend then lists the top stage first, while colours already assigned stay with their values.
- **`syncCategoricalFrame` overload.** A trailing `Comparator<Object> valueOrder` parameter; the old signature passes null. It reaches `sortValues`, where a non-null `valueOrder` sorts the values in place of the comparator lookup.
- **`Legend.isReversed`.** Return false when all of these hold:
  - `inMarkOrder`
  - `isSingleSegment()`
  - `(elem.getCollisionModifier() & GraphElement.MOVE_MIDDLE) != 0`

  Comment: a marked funnel lists its stages from the top.

- [ ] **Step 1: Write the failing tests.** Funnel data: Stage = Visit 1000, Signup 600, Trial 400, Purchase 200, Renewal 100. The runtime Y stage is set as `applyFunnelSort` sets it: `setOrder(XConstants.SORT_VALUE_ASC)` and `setSortByColValue("Sum(Users)")`.
  - In `FunnelColorOrderTest`:
    - `aMarkedFunnelColouredByItsStage`: `stage` returns the stage.
    - `aClassicFunnel`: null.
    - `aColourFieldOtherThanTheStage`: null.
    - `anAuthorsDescendingOrderWins`: the design colour ref is `SORT_DESC` → null.
    - `aManualOrderWins`: null.
    - `topFirstIsTheReverseOfTheStageValueOrder`: Visit, Signup, Trial, Purchase, Renewal.
    - `aPartnerChartIsNotStandalone`: a `Viewsheet` with a second chart coloured by Stage → false.
    - `aPartnerWithShareColorsOffDoesNotCount`: true.
    - `ownShareColorsOffIsStandalone`: true even with a partner.
    - `noViewsheetIsStandalone`
  - In `FunnelColorOrderRenderTest`:
    - `aMarkedFunnelGivesSlotOneToTheTopStage`: Visit draws `VSChartPaletteDefaults.seedPalette(ctx)` colour 0, and Signup draws colour 1.
    - `itsLegendListsTheTopStageFirst`: the legend items read Visit, Signup, Trial, Purchase, Renewal.
    - `aClassicFunnelIsUnchanged`: Purchase draws the classic palette's colour 0. Its legend reads in today's order (record that order in the test as the classic baseline).
    - `derivedPicksFromAnEarlierDrawGiveWay`: give the stored frame derived colours in alphabetical order (`setDerivedColor`) → Visit still draws colour 0.
    - `anAuthorsColourForAStageWins`: `setColor("Visit", red)` → Visit is red.
    - `aFunnelWithAnOuterDimensionUsesStageTotals`: an outer Y dimension with two panels, where one panel's order differs → the slots follow the overall totals.
  - In `StackedLegendOrderTest`:
    - `aMarkedFunnelStageLegendIsNotReversed`
    - `aClassicFunnelLegendStillReverses`
    - `aMarkedFunnelColouredByAnotherFieldStillReverses`: the legend field is not one of the element's dimensions.

- [ ] **Step 2: Run them to verify they fail.**

- [ ] **Step 3: Implement** as under Interfaces.

- [ ] **Step 4: Run them to verify they pass,** plus `GeneratedLegendOrderTest,ModernTopDownOrderTest,BarVOStackRoundingTest,ChartColorTypeSeedTest`.

- [ ] **Step 5: Commit**: `Chart defaults S2b-2, colour a modern funnel and list its legend from the top stage`.

---

### Task 10: Whole-branch check

- [ ] **Step 1:** Run the core suite: `./mvnw -q test -pl core -Dsurefire.failIfNoSpecifiedTests=false`. Expected: 0 failures.
  - `XSwapperGCThrottleTest` and `XSwapperStaleSwapFileMapTest` are known timing flakes. Re-run any failing class alone and compare it with `origin/epic-74519`.
- [ ] **Step 2:** Run the changed portal TL spec (Task 2), and `npx ng test portal` when memory allows (`CI=true` caps workers).
- [ ] **Step 3: Live-check handoff.** Write `E:/Temp/chart-defaults-s2b2-checks-handoff.md` for a separate checking session. It covers, on a built server, in light and dark, in the composer, the viewer and PNG/PDF export:
  - S2-9:
    - a point chart with a measure crossing zero, and a positive one
    - the colour pane showing Variance, then picking Teal
    - an unrelated binding edit keeping the automatic Variance
    - an author's Variance
    - Modernize then Revert
    - the reset copy
    - a tree with node colour by a crossing measure
    - brushing
  - S2-10: a polygon map and its legend; a point map; a brushed map.
  - S2-11: a sunburst and an icicle with colour on each level, a single-colour sunburst, label ink on every step, and brushing a sunburst.
  - D152: the ink on a treemap's mid-tone cells, marked and classic.
  - S2-12:
    - the §7 pre-flight: classic and marked funnels coloured by the stage, alone and beside a second chart coloured by the stage
    - Share Colors off
    - an author's descending order
    - a funnel with an outer Y dimension
  - Classic copies of each: pixel-identical to `origin/epic-74519`.
- [ ] **Step 4: Decision numbers.** At PR time, renumber D150–D160 to follow the highest decision number on `epic-74519`, as was done for S7.
