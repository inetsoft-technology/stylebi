# Palette Revert Follow-ups — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Close the two remaining ways a chart keeps modern colours after Revert — the colour pane's Reset button pinning the modern palette into the USER tier, and the value axis's per-column label formats keeping their modern colour.

**Architecture:** Two independent fixes, neither of which touches the palette seed that already landed. **Reset** stops writing a value and starts clearing the override, so the frame's existing user→css→default fall-through resolves the palette live. **The axis labels** need one gate removed: the loop's body already writes context-appropriate values, and the identical loop for per-ref axis descriptors thirty lines earlier is already ungated, so the fix makes the two consistent rather than inventing anything.

**Tech Stack:** Java 21 (core), JUnit 5, Angular 21 / TypeScript 5.9, Vitest + `@testing-library/angular` (MSW).

**Spec:** No design document prescribes these. Both were found by review of the chart-palette-revert work and are recorded, with their mechanisms traced, in `docs/superpowers/specs/2026-08-14-seed-mark-forward-half-design.md` — the Reset leak and the `VGraphPair:1367-1382` second forward-only mutator, both in the dated 2026-08-24 block. That block is the specification; read it before starting. The governing decision is `docs/superpowers/specs/lookfeel/seeded-value-reversibility-decisions.md` decision 13: Revert is the mirror of Modernize and they cannot drift apart.

**Verified against:** community `viz-updates`, working tree carrying the chart-palette-revert plan's changes uncommitted on top of `27ea5fdd5`. Every line cited below was read at that state.

## Global Constraints

- **Everything in this plan is committed together with the already-finished chart-palette-revert work, as ONE commit.** Do not commit per task. No task has a commit step; the final commit is Task 4, and it covers this plan's files plus the five files already sitting in the working tree from the previous plan.
- **Do not modify `VSChartPaletteDefaults.java` or `ChartVSAssemblyInfo.java`.** Both carry the previous plan's reviewed-and-approved changes. This plan does not touch the palette seed.
- **Do not modify `applyModernPalette` or its three call sites** (`VGraphPair.java:1300`, `ChangeChartProcessor.java:1893`, `CSSProcessor.java:474`). `CSSProcessor:474` passes `VizContext.LEGACY` and is a guaranteed no-op; it must stay one.
- **No design-doc, decision-record, ticket or plan-phase references in source comments.** State rules directly. Documentation files are exempt.
- **No comments in Angular HTML template (`.html`) files.**
- Java conventions: 3-space indent, `if(cond)` with no space after `if`, brace on the same line, comments kept to a short clause rather than full sentences.
- **Never run the full TL suite** — it exceeds the foreground window and orphans multi-GB workers. Scope every `*.tl.spec.ts` run with `--include`, naming the single spec file.
- `./mvnw` is slow (10-40 min). Allow up to 600000 ms per invocation. Use `-Dtest=<class>` while iterating; run the full `-pl core` suite once, in Task 4.
- Branch: `viz-updates`.

---

## File Structure

| File | Responsibility | Task |
|---|---|---|
| `core/src/main/java/inetsoft/web/binding/model/graph/aesthetic/CategoricalColorModel.java` | carries a `resetDefaults` flag from the pane to the factory | 1 |
| `core/src/main/java/inetsoft/web/binding/service/graph/aesthetic/ColorFrameModelFactory.java` | honours the flag by clearing the USER tier instead of writing values into it | 1 |
| `core/src/test/java/inetsoft/web/binding/service/graph/aesthetic/CategoricalColorFactoryResetTest.java` | new — proves the flag clears and that the normal path still writes | 1 |
| `web/projects/portal/src/app/common/data/visual-frame-model.ts` | declares `resetDefaults` on the TS model | 2 |
| `web/projects/portal/src/app/binding/editor/chart/aesthetic/categorical-color-pane.component.ts` | binding-pane Reset sets the flag | 2 |
| `web/projects/portal/src/app/widget/target/b-categorical-color-pane.component.ts` | target-lines Reset sets the flag — the same defect, same fix | 2 |
| `web/projects/portal/src/app/binding/editor/chart/aesthetic/categorical-color-pane.interaction.tl.spec.ts` | existing reset test extended to assert the flag | 2 |
| `core/src/main/java/inetsoft/report/composition/graph/VGraphPair.java:1367-1382` | remove the `ctx.modern` gate so the value axis matches the per-ref loop | 3 |
| `core/src/test/java/inetsoft/report/composition/graph/VGraphPairAxisLabelRevertTest.java` | new — proves the per-column label colour reverts | 3 |

Tasks 1 and 2 are the two halves of the Reset fix and share an interface (the `resetDefaults` field name). Task 3 is independent of both. Task 4 is verification and the single commit.

---

## Background: why Reset clears instead of writing

`CategoricalColorFrame.getColor` (`core/src/main/java/inetsoft/graph/aesthetic/CategoricalColorFrame.java:304-322`) resolves in three tiers:

```java
Color color = userColors.get(negative ? -(oindex + 1) : oindex);

if(parentParams != null && color == null) {
   color = cssColors.get(negative ? -(oindex + 1) : oindex);
}

if(color == null) {
   // ... negcolors, else defaultColors
}
```

user → css → default. The pane's `reset()` (`categorical-color-pane.component.ts:194-203`) computes `cssColors[i] || defaultColors[i]` — **which is exactly that fall-through**. So the value Reset displays is already what the frame produces with an empty USER tier.

Writing it into `userColors` therefore changes nothing visible today and freezes the chart tomorrow. On a marked chart it pins the modern palette, so Revert cannot undo it (the bug). Write the legacy palette instead and it pins legacy, so a later Modernize cannot take — the same bug reversed. It also pins against `format.css`: a customer editing their `ChartPalette` rules would find these charts ignoring it. Clearing the tier is display-identical and correct in all three directions, which is why "promote the gate's palette" was rejected.

`CategoricalColorFrameWrapper.clearUserColors()` already exists (`:166-169`). The missing piece is that `CategoricalColorModel` has no `userColors` field — it carries `colors[]`, `cssColors[]` and `defaultColors[]` only — so the pane currently has no way to express "no override here". Reset is all-or-nothing, so one boolean is sufficient; per-index provenance (adding `userColors[]` to the model) would be the upgrade if per-swatch reset is ever wanted, and is deliberately not built here.

## Background: why the axis fix is one gate

`VGraphPair.fixChartFormat` walks axis descriptors in three places. Two of them already behave correctly:

- **Per-ref descriptors** (X/Y/Group/binding refs), `:1235-1246`: loops `getColumnLabelTextFormatColumns()`, copies the axis-wide font when the per-column font is a default font, then calls `initDefaultFormat(colFmt, ctx)`. **Ungated.** Total write, so it self-corrects on the next render after a Revert.
- **Radar label descriptor**, `:1207-1216`: does neither the font copy nor `initDefaultFormat`, so it never writes a modern value and has nothing to revert.
- **Chart-wide descriptors** (`chartInfo.getAxisDescriptor()` / `getAxisDescriptor2()`, i.e. the value/measure axis), `:1367-1382`: the same loop as the per-ref one — but wrapped in `if(ctx.modern) {` with no `else`.

The body is already total. `VGraphPair`'s private `initDefaultFormat(CompositeTextFormat, VizContext)` (`:1399-1405`) writes `deffmt.setColor(ctx.modern ? VSChartChromeDefaults.labelColor(ctx) : GDefaults.DEFAULT_TEXT_COLOR)` — a ternary, both branches. The font copy reads `axisDesc.getAxisLabelTextFormat().getDefaultFormat().getFont()`, which `:1360`'s unconditional `axisDesc.initDefaultFormat(ctx)` has just set totally. So **only the outer gate makes this forward-only**, and removing it makes the value axis behave exactly like the per-ref axes already do.

Two facts that make removing the gate safe rather than a blast radius:

1. The write it enables on a gate-off render is `GDefaults.DEFAULT_TEXT_COLOR` (`GDefaults.java:129`, `0x4b4b4b`) — the legacy default, and the identical write the ungated per-ref loop has been performing on every gate-off render all along.
2. The font is **not** gate-dependent here. `AxisDescriptor.initDefaultFormat(ctx)` (`:66-71`) sets it as `ctx != VizContext.LEGACY ? VSAssemblyInfo.getDefaultFont(VSUtil.getDefaultFont()) : VSUtil.getDefaultFont()` — an **identity** comparison against the `VizContext.LEGACY` singleton. A mark-derived context is never that singleton, so a viewsheet chart render takes the same branch whether marked or not, and the font the loop copies is the same either way.

The one real behaviour change is that a gate-off render now touches the value axis's per-column DEFAULT tier where it previously left it alone. That is what Task 3's second test pins.

---

## Task 1: Carry a reset signal to the frame, and clear the USER tier

**Files:**
- Modify: `core/src/main/java/inetsoft/web/binding/model/graph/aesthetic/CategoricalColorModel.java` — add the field near the other flags at `:177-185`
- Modify: `core/src/main/java/inetsoft/web/binding/service/graph/aesthetic/ColorFrameModelFactory.java:86-101` — the `CategoricalColorFactory.updateVisualFrameWrapper0` colour loop
- Create: `core/src/test/java/inetsoft/web/binding/service/graph/aesthetic/CategoricalColorFactoryResetTest.java`

**Interfaces:**
- Produces: `CategoricalColorModel.isResetDefaults()` / `setResetDefaults(boolean)`, default `false`. Task 2's TypeScript model must use the JSON property name `resetDefaults` to match.
- Consumes: `CategoricalColorFrameWrapper.clearUserColors()` (`:166-169`), which sets the frame's user map to a fresh `HashMap`.

- [ ] **Step 1: Write the failing tests**

Create `core/src/test/java/inetsoft/web/binding/service/graph/aesthetic/CategoricalColorFactoryResetTest.java`. Two tests: the flag clears, and the ordinary path is unaffected.

```java
class CategoricalColorFactoryResetTest {
   @Test
   void resetDefaultsClearsTheUserTierRatherThanPinningTheCurrentPalette() {
      CategoricalColorFrameWrapper wrapper = new CategoricalColorFrameWrapper();
      wrapper.setColor(0, Color.BLACK);
      assertNotNull(wrapper.getUserColors().get(0), "precondition: index 0 carries an override");

      CategoricalColorModel model = new CategoricalColorModel(wrapper);
      model.setResetDefaults(true);
      new ColorFrameModelFactory.CategoricalColorFactory()
         .updateVisualFrameWrapper0(wrapper, model);

      assertTrue(wrapper.getUserColors().isEmpty(),
                 "a reset clears the overrides so the default tier resolves live");
   }

   @Test
   void aNormalApplyStillWritesTheUserTier() {
      CategoricalColorFrameWrapper wrapper = new CategoricalColorFrameWrapper();
      CategoricalColorModel model = new CategoricalColorModel(wrapper);
      String[] colors = model.getColors().clone();
      colors[0] = "#000000";
      model.setColors(colors);

      new ColorFrameModelFactory.CategoricalColorFactory()
         .updateVisualFrameWrapper0(wrapper, model);

      assertEquals(Color.BLACK, wrapper.getUserColors().get(0),
                   "without the flag an explicit pick is still an override");
   }
}
```

Confirm before writing: whether `CategoricalColorFactory` is a nested class you can instantiate as written, and whether `updateVisualFrameWrapper0` is reachable from the test's package. Read `ColorFrameModelFactory.java:78-101` and the class's declaration. If the nested factory is not directly instantiable, obtain it the way production does and say so in your report — do **not** widen production visibility to suit the test.

The wrapper's colour count comes from its frame's default palette, so `model.getColors()` is already populated; clone-and-edit rather than allocating a new array of the wrong length.

- [ ] **Step 2: Run them and confirm they fail**

`cd community && ./mvnw test -pl core -Dtest=CategoricalColorFactoryResetTest`

Expected: compile failure on `setResetDefaults` — the field does not exist yet. That is the correct RED for the first test. The second test should pass once it compiles, since it pins existing behaviour; if it fails, stop and report, because the premise about the ordinary path is wrong.

- [ ] **Step 3: Add the flag to the model**

In `CategoricalColorModel.java`, add a `private boolean resetDefaults;` beside the other flags at `:177-185`, with a getter and setter matching the file's existing accessor style. It defaults to `false`, so every existing caller and every serialized model keeps today's behaviour.

- [ ] **Step 4: Honour the flag in the factory**

In `ColorFrameModelFactory.CategoricalColorFactory.updateVisualFrameWrapper0` (`:86-101`), clear the user tier and skip the per-index write when the flag is set. The existing loop is:

```java
         for(int i = 0; i < colors.length; i++) {
            Color ncolor = Tool.getColorFromHexString(colors[i]);

            if(!Tool.equals(nwrapper.getColor(i), ncolor)) {
               nwrapper.setUserColor(i, ncolor);
            }
         }
```

Guard it so a reset clears instead. Keep the `colors == null || colors.length == 0` early return at `:92-94` ahead of whatever you add, or a reset on an empty model changes behaviour. Everything after the loop — `useGlobal`, the colour maps — is untouched: Reset does not clear dimension-keyed colours today and this plan does not change that.

Add a short-clause comment saying why clearing beats writing: the default tier resolves live, so pinning a value would freeze the palette against a later gate or CSS change.

- [ ] **Step 5: Confirm green**

`cd community && ./mvnw test -pl core -Dtest=CategoricalColorFactoryResetTest`

Expected: both PASS.

---

## Task 2: Make both Reset buttons send the signal

**Files:**
- Modify: `web/projects/portal/src/app/common/data/visual-frame-model.ts:44` area — the interface that declares `defaultColors`
- Modify: `web/projects/portal/src/app/binding/editor/chart/aesthetic/categorical-color-pane.component.ts:194-203` (`reset()`)
- Modify: `web/projects/portal/src/app/widget/target/b-categorical-color-pane.component.ts:93-104` (`reset()`)
- Modify: `web/projects/portal/src/app/binding/editor/chart/aesthetic/categorical-color-pane.interaction.tl.spec.ts:262-274` (the existing reset test)

**Interfaces:**
- Consumes: the JSON property `resetDefaults` produced by Task 1. The name must match exactly or the flag silently never arrives — a boolean absent from JSON deserializes to `false`, so a typo fails silently rather than loudly. This is the single highest-risk detail in the task.

- [ ] **Step 1: Extend the existing reset test to assert the flag**

`categorical-color-pane.interaction.tl.spec.ts:262-274` already has `"should restore colors from css/default and report reset state"`. Read it and the `renderPane()` helper first. Extend that test — do not add a duplicate — so it also asserts the flag reaches the model:

```typescript
      expect(fixture.componentInstance.frameModel.resetDefaults).toBe(true);
```

Keep its existing assertions: the swatch returns to `#aa0000` and the Reset button becomes `icon-disabled`. Those pin that clearing is display-identical, which is the whole argument for this approach, so losing them would lose the point.

- [ ] **Step 2: Run it and confirm it fails**

From `community/web`, scoped to the one file:

```bash
npx ng test portal --test-tl --include=**/categorical-color-pane.interaction.tl.spec.ts
```

Check `angular.json` for the exact target name (`test-tl`) and adjust the invocation if it differs; `npm run test:portal:tl` runs the whole TL project and **must not** be used. Expected: FAIL on the new assertion (`undefined` is not `true`), the older assertions still passing.

- [ ] **Step 3: Declare the field on the TS model**

Add `resetDefaults: boolean;` to the same interface that declares `defaultColors: string[]` in `visual-frame-model.ts` (around `:44`). Match the file's existing optionality convention — if its other flags are non-optional, follow that.

- [ ] **Step 4: Set it in both panes**

In `categorical-color-pane.component.ts`'s `reset()`, set `this.frameModel.resetDefaults = true;` alongside the existing loop. **Keep the loop.** It updates the swatches the user is looking at, and `isResetted()` (`:205-217`) reads `colors[]` to decide whether the Reset button is disabled — deleting the loop would break the button's own state. The loop is now display-only; the flag is what persists.

Do the same in `b-categorical-color-pane.component.ts`'s `reset()` (`:93-104`), which carries the identical expression at `:100`. Confirm while you are there that this pane's model is a `CategoricalColorModel` reaching the same factory — if target-line colours take a different backend path, the flag may not be honoured there. Report what you find rather than assuming; if it is a different path, say so and leave that pane's `reset()` alone.

Then find whoever clears or rebuilds `frameModel` between opens, and make sure a stale `resetDefaults: true` cannot survive into a later unrelated Apply. If the model is re-fetched per dialog open, nothing is needed; if it is reused, reset the flag after a successful apply. State which case holds in your report.

- [ ] **Step 5: Confirm green**

Re-run the scoped command from Step 2. Expected: PASS.

Then run the pane's other spec, since it also exercises reset paths:

```bash
npx ng test portal --test-tl --include=**/categorical-color-pane.risk.tl.spec.ts
```

- [ ] **Step 6: Lint**

`cd community/web && npm run lint`

Report any new findings in the files you touched. Pre-existing findings elsewhere are not yours.

---

## Task 3: Ungate the value axis's per-column label formats

**Files:**
- Modify: `core/src/main/java/inetsoft/report/composition/graph/VGraphPair.java:1367-1382`
- Create: `core/src/test/java/inetsoft/report/composition/graph/VGraphPairAxisLabelRevertTest.java`

**Interfaces:**
- Consumes: `AxisDescriptor.getColumnLabelTextFormatColumns()` (`:110`), `getColumnLabelTextFormat(String)` (`:118`), `setColumnLabelTextFormat(String, CompositeTextFormat)` (`:132`), `GDefaults.DEFAULT_TEXT_COLOR` (`:129`), `VSChartChromeDefaults.labelColor(VizContext)` (`:49-51`).
- Produces: nothing new. This task deletes a condition; no signature changes.

- [ ] **Step 1: Write the failing tests**

Create `core/src/test/java/inetsoft/report/composition/graph/VGraphPairAxisLabelRevertTest.java`. `fixChartFormat` is private, so first establish how to reach it — read `VGraphPair.java:1006` and find its caller chain, and check how `VGraphPairModernPaletteTest` (an existing sibling test in the same package) gets at this class. Follow that test's approach.

Two tests:

1. **The revert case.** Build a chart whose chart-wide axis descriptor carries a per-column `CompositeTextFormat`; drive the modern path so the per-column DEFAULT colour becomes `VSChartChromeDefaults.labelColor(...)`; then drive the same path with an unmarked context and assert the per-column DEFAULT colour is `GDefaults.DEFAULT_TEXT_COLOR`.
2. **The gate-off case, which pins the one real behaviour change.** With an unmarked context throughout, assert the per-column DEFAULT colour ends as `GDefaults.DEFAULT_TEXT_COLOR`. Before the fix this assertion's outcome depends on what the format was constructed with — establish that starting value first and pick a distinguishable pre-set colour (not `0x4b4b4b`) so the test proves a write happened rather than passing on a coincidence.

Assert the **colour** only. The font is not gate-dependent here (see "Background: why the axis fix is one gate"), so a font assertion would pin behaviour this change does not affect.

If reaching `fixChartFormat` requires test scaffolding the codebase does not already have, report BLOCKED with what you tried rather than making the method non-private or writing a test that asserts nothing.

- [ ] **Step 2: Run them and confirm the first fails**

`cd community && ./mvnw test -pl core -Dtest=VGraphPairAxisLabelRevertTest`

Expected: test 1 FAILS — the per-column format keeps the modern colour, because the gate stops the legacy write. Report test 2's pre-fix outcome, whichever way it goes; it documents what the gate-off path does today.

- [ ] **Step 3: Remove the gate**

At `VGraphPair.java:1367`, remove the `if(ctx.modern) {` wrapper and its closing brace, so the per-column loop runs unconditionally — matching the per-ref loop at `:1235-1246`. Re-indent the body.

Replace the now-stale comment at `:1365-1366` ("Gated so gate-off leaves the value-axis per-column formats exactly as today"). The new comment should say what the code now does and why it is safe: the loop writes the context's own values on both branches, matching what the per-ref axis descriptors already do, so a cleared mark restores the legacy label colour on the next render.

Change nothing else in the block. The `copyDefaultFormat(colFmt.getDefaultFormat(), objFmt)` and `setParentCSSParams(parentParams)` calls stay exactly as they are.

- [ ] **Step 4: Confirm green, then the guarding classes**

```bash
cd community && ./mvnw test -pl core -Dtest=VGraphPairAxisLabelRevertTest,VGraphPairModernPaletteTest,VSChartChromeDefaultsTest
```

Those last two are the existing coverage over this render path and the chrome constants. Drop from the list any class that does not exist and say which.

---

## Task 4: Verify everything together and make the single commit

This is where the whole change set — this plan's files plus the five already in the working tree from the chart-palette-revert plan — gets verified and committed as one commit.

**Files:**
- No source changes. Verification and commit only.

- [ ] **Step 1: Confirm the working tree holds exactly what it should**

```bash
cd community && git status --short
```

Expected tracked modifications: `VSChartPaletteDefaults.java`, `ChartVSAssemblyInfo.java`, `SeedChromeDefaultsTest.java`, `VSChartPaletteDefaultsTest.java` and `docs/superpowers/specs/2026-08-14-seed-mark-forward-half-design.md` from the previous plan; plus this plan's files from Tasks 1-3.

`CLAUDE.md` and `docs/superpowers/plans/*.md` also show as modified or untracked. **`CLAUDE.md` is unrelated pre-existing dirt and must not be staged.** The two plan files are part of the deliverable and should be. If anything else tracked appears, stop and report rather than staging it.

- [ ] **Step 2: Full core suite**

`cd community && ./mvnw test -pl core`

The last full run was **4911 run / 0 failures / 0 errors / 67 skipped**, measured *before* the previous plan's final two tests were added, so the true starting point is expected to be 4913 and is **unverified**. Report the actual numbers and reconcile the delta against every test added by both plans. A shortfall against that arithmetic means a test is not running — investigate rather than reporting the number.

- [ ] **Step 3: Portal unit suite**

`cd community/web && npm run test:portal`

This is the standard `*.spec.ts` project, which excludes the TL specs. It covers the model change from Task 2 for regressions. Do **not** run the full TL suite.

- [ ] **Step 4: Commit everything as one commit**

Stage precisely — `git add -A` would take `CLAUDE.md`:

```bash
cd community
git add core/src/main/java/inetsoft/uql/viewsheet/internal/VSChartPaletteDefaults.java \
        core/src/main/java/inetsoft/uql/viewsheet/internal/ChartVSAssemblyInfo.java \
        core/src/test/java/inetsoft/uql/viewsheet/internal/SeedChromeDefaultsTest.java \
        core/src/test/java/inetsoft/uql/viewsheet/internal/VSChartPaletteDefaultsTest.java \
        core/src/main/java/inetsoft/web/binding/model/graph/aesthetic/CategoricalColorModel.java \
        core/src/main/java/inetsoft/web/binding/service/graph/aesthetic/ColorFrameModelFactory.java \
        core/src/test/java/inetsoft/web/binding/service/graph/aesthetic/CategoricalColorFactoryResetTest.java \
        core/src/main/java/inetsoft/report/composition/graph/VGraphPair.java \
        core/src/test/java/inetsoft/report/composition/graph/VGraphPairAxisLabelRevertTest.java \
        web/projects/portal/src/app/common/data/visual-frame-model.ts \
        web/projects/portal/src/app/binding/editor/chart/aesthetic/categorical-color-pane.component.ts \
        web/projects/portal/src/app/binding/editor/chart/aesthetic/categorical-color-pane.interaction.tl.spec.ts \
        web/projects/portal/src/app/widget/target/b-categorical-color-pane.component.ts \
        docs/superpowers/specs/2026-08-14-seed-mark-forward-half-design.md \
        docs/superpowers/plans/2026-08-24-chart-palette-revert.md \
        docs/superpowers/plans/2026-08-24-palette-revert-followups.md
git status --short
```

Drop `b-categorical-color-pane.component.ts` from the list if Task 2 established that pane takes a different backend path and left it unchanged. Check `git status --short` shows nothing staged that you did not intend, then:

```bash
git commit -m "fix(viewsheet): revert chart colours with the assembly

Three ways a chart kept its modern colours after Revert.

The categorical palette was written onto a chart's colour frames at render
time and never written back, so clearing an assembly's mark left the modern
colours in place. It is now seeded in seedChromeDefaults beside the two plot
values that already write both branches, which is the method Revert and a
gate-off creation both call. The render-time applier stays forward-only:
making it total would have turned the CSS path's inert legacy call into a
writer that stamps the legacy palette onto every report chart on every pass.

The colour pane's Reset button copied the frame's current default palette
into the user colours, which persist separately and outrank the defaults, so
a Reset on a modern chart survived Revert. Reset now clears the overrides
instead. The value the pane was writing is what the frame already resolves
with an empty user tier, so nothing changes on screen - but the palette is no
longer pinned against a later gate change or a format.css edit.

The value axis's per-column label formats were seeded modern behind a gate
with no legacy branch, while the identical loop for the per-ref axis
descriptors thirty lines earlier has always been ungated. The gate is gone,
so both write the context's own values and the label colour follows the mark.

Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>"
```

- [ ] **Step 5: Confirm the commit**

```bash
cd community && git log --stat -1 && git status --short
```

Expected: the commit lists every intended file and nothing else; `CLAUDE.md` still shows as unstaged modified.

---

## Self-Review

**Spec coverage.** The spec is the dated 2026-08-24 block in `2026-08-14-seed-mark-forward-half-design.md`, which records three open items. Two are covered: the Reset leak by Tasks 1-2, `VGraphPair:1367-1382` by Task 3. The third — the multi-styles enumeration residual, where toggling multi-styles off, reverting, and toggling back on leaves stale modern colours on aggregate-level frames — is **deliberately not covered**, because closing it would require the palette seed to enumerate more frames than the render path writes, trading a documented residual for undocumented drift. It stays recorded. No task in this plan changes the design doc, so all three items remain on record as written.

**Placeholder scan.** No TBDs. Every code step carries the actual code or the actual edit. Three steps deliberately require the implementer to establish something before writing rather than handing them a value: how to instantiate the nested factory (Task 1 Step 1), how to reach the private `fixChartFormat` (Task 3 Step 1), and the per-column format's pre-fix starting colour (Task 3 Step 1). Each names what to read and what to do if the answer blocks the task, which is a verification instruction, not a placeholder. Task 2 Step 4 likewise requires establishing whether the target-lines pane shares the backend path, with an explicit branch for either answer.

**Type consistency.** `resetDefaults` is the single cross-task interface: declared as a Java field with `isResetDefaults()`/`setResetDefaults(boolean)` in Task 1, consumed as the JSON property `resetDefaults` in Task 2, and Task 2 Step 1's assertion reads it off `frameModel` under that exact name. Task 3 introduces no new signature — it deletes a condition. Every method cited (`clearUserColors`, `getColumnLabelTextFormatColumns`, `getColumnLabelTextFormat`, `labelColor`, `DEFAULT_TEXT_COLOR`, `isDefaultFont`) was read at its declaration and is cited to file:line.

**Sequencing.** Task 1 gates Task 2 (the flag must exist before the pane sets it). Task 3 is independent of both. Task 4 requires all three, and is the only task that commits — per the global constraint that this ships as one commit with the previous plan's work.
