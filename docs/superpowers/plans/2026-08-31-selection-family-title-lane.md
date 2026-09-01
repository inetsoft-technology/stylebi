# Selection Family Title Lane — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A modern selection list, selection tree, selection container and range slider draw an unfilled title lane with a single `#D9D5CC` hairline, with the values written into the assembly's stored format at creation so they travel in an exported asset.

**Architecture:** The same mechanism the chart and table family already use — `seedChromeDefaults` writes both branches, `VizModernizeUtil.revert` re-runs it, and the read-time substitution in `VSTitleChromeDefaults` early-returns for a converted type. Three of the four types already draw an unfilled bottom-only rule in legacy, so for them this is a colour change; only the selection container is structurally the table's case.

**Tech Stack:** Java 21, Maven, JUnit 5 + Spring test context. No frontend change — `vs-title.component.html:32-37` already binds all four borders and the background from the model.

**Spec:** [`docs/superpowers/specs/lookfeel/2026-08-31-selection-family-title-lane-design.md`](../specs/lookfeel/2026-08-31-selection-family-title-lane-design.md)

## Global Constraints

- **Branch:** work directly on `viz-updates` — do not create a working branch, and do not create a worktree. Never commit to `main`, `v1.0.x` or `v1.1.x`.
- **The gate now defaults to ON.** `c7790bbf0` landed `viewsheet.modernvisualization=true` and `viewsheet.density=compact` in `defaults.properties`. Two consequences: `SreeEnv.setProperty("viewsheet.modernVisualization", null)` means *modern*, not *legacy*, so a test that needs the legacy branch must set `"false"` explicitly — `SeedChromeDefaultsTest`'s `gateOff()` already does, which is why that class needed no reconciliation. And every gate-off verification in this plan must set the property to `false` rather than unsetting it.
- **Gate-off output must stay byte-identical to a gate-off build without this change.** Every value that differs between branches sits inside a `ctx.modern` ternary whose legacy branch reproduces what a gate-off creation writes.
- **The legacy branch is the Revert contract.** `VizModernizeUtil.revert:102-111` clears the mark and re-runs `seedChromeDefaults`; there is no separate reverser. A legacy branch that only *nearly* matches a never-modernized assembly is a defect.
- **The three legacy branches are different and must not be unified.** Selection list/tree and range slider restore `0xc0c0c0` and no background; the container restores `DEFAULT_TITLE_BG` and the four-side box. See each task.
- **Rule colour:** `VSTitleChromeDefaults.titleRuleColors(ctx)` — `TITLE_BORDER 0xD9D5CC`, dark `TITLE_BORDER_DARK 0x49454F`. **Add no new colour constant** other than the legacy `0xc0c0c0` extractions named in Tasks 1 and 2.
- **Rule shape:** `VSTitleChromeDefaults.titleRuleBorders()` — `Insets(NONE, NONE, THIN_LINE, NONE)`. `StyleConstants.THIN_LINE` is declared as `GraphConstants.THIN_LINE` (`StyleConstants:114`) and both `NONE` are `0`, so this is value-identical to the `GraphConstants` insets the selection classes write today. The insets write is therefore a no-op on the legacy branch, deliberately — writing it unconditionally keeps the two branches from drifting.
- **Never write a per-side border palette on a title format.** `TextBoxElementDef.setBorderColors:209-210` keeps only `topColor`; one colour four times is what makes that setter lossless.
- **Clearing a colour needs both setters.** `VSFormat.getBackground():276` returns the `bg` field when `bgval` yields nothing, and `getForeground()` has the same fallback. Any clear must null both the value and the field, or a runtime colour survives it.
- **Density is out of scope.** Seed nothing derived from `VSDensityDefaults.mode()` — it reads a live org property.
- **Comments:** short clauses. No ticket or PR numbers, no references to this plan or the spec inside source comments.
- **Test command (from `community/`):** `./mvnw test -pl core -Dtest=<ClassName>` (PowerShell: `.\mvnw.cmd test -pl core "-Dtest=<ClassName>"`). Surefire is configured with `<groups>core</groups>`, so a test class needs `@Tag("core")` — every class touched here already has it. Multiple classes are **comma** separated; joining with `+` fails with "No tests matching pattern".

---

## File Structure

| File | Responsibility | Task |
|---|---|---|
| `core/src/main/java/inetsoft/uql/viewsheet/internal/SelectionBaseVSAssemblyInfo.java` | The list/tree seed, and the re-run after it installs its own title composite | 1 |
| `core/src/main/java/inetsoft/uql/viewsheet/internal/TimeSliderVSAssemblyInfo.java` | The range slider seed, and the re-run after it overwrites the title composite | 2 |
| `core/src/main/java/inetsoft/uql/viewsheet/internal/CurrentSelectionVSAssemblyInfo.java` | The container seed — the table's shape | 3 |
| `core/src/main/java/inetsoft/uql/viewsheet/internal/VSTitleChromeDefaults.java` | `isSeededTitle` widens to seven classes' worth of types | 4 |
| `core/src/main/java/inetsoft/web/viewsheet/model/VSCompositeModel.java` | Pass the info — list, tree and container in one | 4 |
| `core/src/main/java/inetsoft/web/viewsheet/model/VSRangeSliderModel.java` | Pass the info | 4 |
| `core/src/main/java/inetsoft/report/io/viewsheet/VSSelectionListHelper.java` | Pass the info | 5 |
| `core/src/main/java/inetsoft/report/io/viewsheet/VSSelectionTreeHelper.java` | Pass the info | 5 |
| `core/src/main/java/inetsoft/report/io/viewsheet/ExportUtil.java` | The slider-in-container background: widen the signature so it can pass the info | 5 |
| `core/src/main/java/inetsoft/report/io/viewsheet/pdf/PDFVSExporter.java` | Caller of the widened signature | 5 |
| `core/src/main/java/inetsoft/report/io/viewsheet/svg/SVGVSExporter.java` | Caller of the widened signature | 5 |
| `core/src/test/java/inetsoft/uql/viewsheet/internal/SeedChromeDefaultsTest.java` | Each seed's contract, both branches | 1, 2, 3 |
| `core/src/test/java/inetsoft/uql/viewsheet/internal/VSTitleChromeDefaultsTest.java` | The widened `isSeededTitle` | 4 |
| `core/src/test/java/inetsoft/web/viewsheet/model/VSFormatModelTest.java` | What the browser receives | 4 |
| `core/src/test/java/inetsoft/uql/viewsheet/BookmarkChromeResolutionTest.java` | The guard that this family carries no format in state | 5 |

**Intermediate states, so a reviewer is not surprised.** After Tasks 1–3 a modern selection assembly draws the `#F1EFEA` fill *and* the new `#D9D5CC` rule — the read-time substitution has not been switched off yet. Task 4 removes the fill and is the point at which both the browser and the two umbrella server sites go final. This is expected; do not "fix" it inside Tasks 1–3.

**Two already-converted umbrella sites come along for free in Task 4**, because they already hand the resolver an info and were waiting on `isSeededTitle`: `AbstractVSExporter:1802-1804` (every titled assembly's export title, in place) and `FormatPainterService:220` (the composer's format painter).

**Task 5 then has three sites, not two.** Beyond the two selection export helpers, `ExportUtil.getBackGroundColor` calls the *two-argument* form — so it passes `info = null`, and `isSeededTitle(null)` is false however wide the predicate grows. Widening the predicate does not reach it and neither does any other task. Left alone it repaints a range slider's export title inside a selection container.

---

## Task 1: Selection list and tree

**Files:**
- Modify: `core/src/main/java/inetsoft/uql/viewsheet/internal/SelectionBaseVSAssemblyInfo.java:873-927`
- Test: `core/src/test/java/inetsoft/uql/viewsheet/internal/SeedChromeDefaultsTest.java`

**Interfaces:**
- Consumes: `VSTitleChromeDefaults.titleRuleBorders()`, `.titleRuleColors(VizContext)`, `.titleForegroundValue(VizContext)` — all three already exist and are already used by `TableDataVSAssemblyInfo` and `ChartVSAssemblyInfo`.
- Produces: `SelectionBaseVSAssemblyInfo.seedChromeDefaults(VizContext)`, a `protected void` override; and a `private static final BorderColors LEGACY_TITLE_RULE_COLORS` used by both `setDefaultFormat` and the new override.

**Why this exists:** `SelectionBaseVSAssemblyInfo:889-901` builds a **fresh** TITLEPATH composite and installs it, discarding whatever `super.setDefaultFormat(border)` seeded. Its title is already unfilled with a bottom-only rule, so the only values that move are the rule's colour and the text colour.

- [ ] **Step 1: Write the failing tests**

Add to `SeedChromeDefaultsTest`. The fixture `newSelectionList()` already exists at `:97`; add `newSelectionTree()` beside it:

```java
   private SelectionTreeVSAssemblyInfo newSelectionTree() {
      Viewsheet vs = new Viewsheet();
      SelectionTreeVSAssembly tree = new SelectionTreeVSAssembly(vs, "SelectionTree1");
      tree.getVSAssemblyInfo().initDefaultFormat();
      return (SelectionTreeVSAssemblyInfo) tree.getVSAssemblyInfo();
   }
```

Then four tests:

```java
   @Test
   void aModernSelectionListTitleTakesTheRuleColour() {
      gateOn();
      VizContext ctx = VizContext.ofGate();
      VSFormat def = titleFormat(newSelectionList()).getDefaultFormat();

      assertNull(def.getBackgroundValue(), "this type's title has never carried a fill");

      Insets borders = def.getBordersValue();
      assertEquals(StyleConstants.NONE, borders.top, "no top rule");
      assertEquals(StyleConstants.NONE, borders.left, "no left rule");
      assertEquals(StyleConstants.NONE, borders.right, "no right rule");
      assertEquals(StyleConstants.THIN_LINE, borders.bottom, "a bottom rule only");

      BorderColors colors = def.getBorderColorsValue();
      assertEquals(VSTitleChromeDefaults.titleBorderColor(ctx), colors.bottomColor);
      assertEquals(colors.bottomColor, colors.topColor,
                   "all four sides carry the rule colour; report text boxes keep only one");
   }

   @Test
   void aModernSelectionTreeTitleTakesTheRuleColour() {
      gateOn();
      VizContext ctx = VizContext.ofGate();
      VSFormat def = titleFormat(newSelectionTree()).getDefaultFormat();

      assertEquals(VSTitleChromeDefaults.titleBorderColor(ctx),
                   def.getBorderColorsValue().bottomColor,
                   "the tree takes the same seed through the shared base");
   }

   @Test
   void aLegacySelectionListTitleKeepsTheGreyRule() {
      gateOff();
      VSFormat def = titleFormat(newSelectionList()).getDefaultFormat();

      assertEquals(new Color(0xc0c0c0), def.getBorderColorsValue().bottomColor);
      assertNull(def.getForegroundValue(), "and no seeded text colour");
      assertNull(def.getBackgroundValue(), "and still no fill");
   }

   @Test
   void revertingASelectionListTitleRestoresAGateOffCreation() {
      gateOff();
      VSFormat expected = titleFormat(newSelectionList()).getDefaultFormat();
      BorderColors expectedColors = expected.getBorderColorsValue();
      Insets expectedBorders = expected.getBordersValue();

      gateOn();
      SelectionListVSAssemblyInfo info = newSelectionList();
      info.setVizMark(VizMark.fromGate());
      info.seedChromeDefaults(VizContext.of(info));

      info.setVizMark(null);
      info.seedChromeDefaults(VizContext.of((VizMark) null));

      VSFormat reverted = titleFormat(info).getDefaultFormat();
      assertEquals(expectedColors.bottomColor, reverted.getBorderColorsValue().bottomColor,
                   "Revert must match a list that was never modernized, not almost match it");
      assertEquals(expectedBorders, reverted.getBordersValue());
      assertNull(reverted.getForegroundValue());
      assertNull(reverted.getBackgroundValue());
   }
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./mvnw test -pl core -Dtest=SeedChromeDefaultsTest`
Expected: `aModernSelectionListTitleTakesTheRuleColour` and `aModernSelectionTreeTitleTakesTheRuleColour` FAIL — the observed bottom colour is `0xc0c0c0`, not `0xD9D5CC`. The two legacy tests PASS already; that is correct and is what pins the branch you must not disturb.

- [ ] **Step 3: Extract the legacy rule colour**

In `SelectionBaseVSAssemblyInfo`, add the constant near the bottom of the class beside its other private statics, and use it in `setDefaultFormat`:

```java
   // the title rule this type has always drawn; the seed's legacy branch restores it
   private static final BorderColors LEGACY_TITLE_RULE_COLORS = new BorderColors(
      new Color(0xc0c0c0), new Color(0xc0c0c0), new Color(0xc0c0c0), new Color(0xc0c0c0));
```

Replace `:897-899` with:

```java
      format.getDefaultFormat().setBorderColorsValue(LEGACY_TITLE_RULE_COLORS);
```

**No new imports.** `inetsoft.uql.viewsheet.*` at `:27` supplies `BorderColors`, `VSCompositeFormat` and `VSFormat`; `java.awt.*` at `:35` supplies `Color` and `Insets`; and `VizContext` and `VSTitleChromeDefaults` are in this same package.

- [ ] **Step 4: Add the seed override**

Add after `setDefaultFormat`, i.e. after `:927`:

```java
   @Override
   protected void seedChromeDefaults(VizContext ctx) {
      super.seedChromeDefaults(ctx);

      // the title lane: this type installs its own title composite, which has never carried a
      // fill, so only the rule's colour and the text colour move. Both branches write, because
      // the legacy one is what Revert relies on to restore a never-modernized list
      VSCompositeFormat titleFormat = getFormatInfo().getFormat(TITLEPATH);

      if(titleFormat != null) {
         VSFormat def = titleFormat.getDefaultFormat();
         def.setBordersValue(VSTitleChromeDefaults.titleRuleBorders());
         def.setBorderColorsValue(ctx.modern
            ? VSTitleChromeDefaults.titleRuleColors(ctx) : LEGACY_TITLE_RULE_COLORS);
         def.setForegroundValue(
            ctx.modern ? VSTitleChromeDefaults.titleForegroundValue(ctx) : null);
         // getForeground() falls back to the fg field when fgval yields nothing, so the legacy
         // branch has to null both or a runtime foreground survives the clear
         def.setForeground(null);
      }
   }
```

- [ ] **Step 5: Re-run the hook after the composite is installed**

`super.setDefaultFormat(border)` calls the hook as its last act, and this method then replaces the title composite, so the seed lands on an object that is thrown away. Add as the final statement of `setDefaultFormat`, after the `for` loop closes at `:927`:

```java
      // super seeded the title composite this method just replaced; re-run against the real one.
      // The hook is a set of unconditional writes, so running it twice changes nothing else
      seedChromeDefaults(VizContext.of(this));
   }
```

- [ ] **Step 6: Run the tests to verify they pass**

Run: `./mvnw test -pl core -Dtest=SeedChromeDefaultsTest`
Expected: PASS, all four, and no previously-passing test in the class regressed.

- [ ] **Step 7: Commit**

```bash
git add core/src/main/java/inetsoft/uql/viewsheet/internal/SelectionBaseVSAssemblyInfo.java \
        core/src/test/java/inetsoft/uql/viewsheet/internal/SeedChromeDefaultsTest.java
git commit -m "feat(viewsheet): seed the selection list and tree title rule"
```

---

## Task 2: The range slider

**Files:**
- Modify: `core/src/main/java/inetsoft/uql/viewsheet/internal/TimeSliderVSAssemblyInfo.java:701-716`
- Test: `core/src/test/java/inetsoft/uql/viewsheet/internal/SeedChromeDefaultsTest.java:255-270`

**Interfaces:**
- Consumes: the same three `VSTitleChromeDefaults` helpers as Task 1.
- Produces: `TimeSliderVSAssemblyInfo.seedChromeDefaults(VizContext)`, and its own `private static final BorderColors LEGACY_TITLE_RULE_COLORS`.

**Why this exists, and why it is a separate task:** `TimeSliderVSAssemblyInfo` is a **sibling** of `SelectionBaseVSAssemblyInfo`, not a subclass — both extend `MaxModeSelectionVSAssemblyInfo` — so Task 1's override does not reach it. And its trap is the opposite one: it *mutates* the existing composite after `super` rather than replacing it, so the seed survives and is then silently overwritten field by field. The symptom looks like "the seed never ran" when it ran and was clobbered.

**The constant is duplicated rather than hoisted.** Both classes inline `0xc0c0c0` independently today; hoisting it to `MaxModeSelectionVSAssemblyInfo` would widen this change into a third class for two usages. Keep one private constant per class.

- [ ] **Step 1: Invert the existing test**

`SeedChromeDefaultsTest:255-270` currently pins the opposite behaviour and its name says so. Replace `theHookLeavesATimeSlidersTitleBorderAlone` entirely with:

```java
   @Test
   void aModernRangeSliderTitleTakesTheRuleColour() {
      gateOn();
      VizContext ctx = VizContext.ofGate();
      TimeSliderVSAssemblyInfo info = newTimeSlider();
      VSFormat def = titleFormat(info).getDefaultFormat();

      assertEquals(VSTitleChromeDefaults.titleBorderColor(ctx),
                   def.getBorderColorsValue().bottomColor,
                   "the hook now owns this title composite");
      assertNull(def.getBackgroundValue(),
                 "the slider clears its title fill deliberately; modern keeps it cleared");
   }

   @Test
   void aLegacyRangeSliderTitleKeepsTheGreyRuleAndNoFill() {
      gateOff();
      VSFormat def = titleFormat(newTimeSlider()).getDefaultFormat();

      assertEquals(new Color(0xc0c0c0), def.getBorderColorsValue().bottomColor);
      assertNull(def.getBackgroundValue(),
                 "not DEFAULT_TITLE_BG: this type has always cleared its title fill");
      assertNull(def.getForegroundValue());
   }

   @Test
   void revertingARangeSliderTitleRestoresAGateOffCreation() {
      gateOff();
      VSFormat expected = titleFormat(newTimeSlider()).getDefaultFormat();
      BorderColors expectedColors = expected.getBorderColorsValue();

      gateOn();
      TimeSliderVSAssemblyInfo info = newTimeSlider();
      info.setVizMark(VizMark.fromGate());
      info.seedChromeDefaults(VizContext.of(info));

      info.setVizMark(null);
      info.seedChromeDefaults(VizContext.of((VizMark) null));

      VSFormat reverted = titleFormat(info).getDefaultFormat();
      assertEquals(expectedColors.bottomColor, reverted.getBorderColorsValue().bottomColor);
      assertNull(reverted.getBackgroundValue(),
                 "Revert must not hand the slider a fill it never had");
      assertNull(reverted.getForegroundValue());
   }
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./mvnw test -pl core -Dtest=SeedChromeDefaultsTest`
Expected: `aModernRangeSliderTitleTakesTheRuleColour` FAILS — observed `0xc0c0c0`. The two legacy tests PASS already.

- [ ] **Step 3: Extract the legacy rule colour**

In `TimeSliderVSAssemblyInfo`, add beside its other private statics:

```java
   // the title rule this type has always drawn; the seed's legacy branch restores it
   private static final BorderColors LEGACY_TITLE_RULE_COLORS = new BorderColors(
      new Color(0xc0c0c0), new Color(0xc0c0c0), new Color(0xc0c0c0), new Color(0xc0c0c0));
```

and replace `:712-715` with:

```java
         titleFormat.getDefaultFormat().setBorderColorsValue(LEGACY_TITLE_RULE_COLORS);
```

- [ ] **Step 4: Add the seed override**

Add after `setDefaultFormat`, i.e. after `:717`:

```java
   @Override
   protected void seedChromeDefaults(VizContext ctx) {
      super.seedChromeDefaults(ctx);

      // the title lane: this type clears its own title fill at creation and keeps it cleared on
      // both branches, so only the rule's colour and the text colour move. The legacy branch is
      // what Revert relies on to restore a never-modernized slider
      VSCompositeFormat titleFormat = getFormatInfo().getFormat(TITLEPATH);

      if(titleFormat != null) {
         VSFormat def = titleFormat.getDefaultFormat();
         def.setBordersValue(VSTitleChromeDefaults.titleRuleBorders());
         def.setBorderColorsValue(ctx.modern
            ? VSTitleChromeDefaults.titleRuleColors(ctx) : LEGACY_TITLE_RULE_COLORS);
         def.setForegroundValue(
            ctx.modern ? VSTitleChromeDefaults.titleForegroundValue(ctx) : null);
         // getForeground() falls back to the fg field when fgval yields nothing, so the legacy
         // branch has to null both or a runtime foreground survives the clear
         def.setForeground(null);
      }
   }
```

**No new imports.** `inetsoft.uql.viewsheet.*` at `:25` supplies `BorderColors`, `VSCompositeFormat` and `VSFormat`; `java.awt.*` at `:33` supplies `Color` and `Insets`; `VizContext` and `VSTitleChromeDefaults` are same-package.

- [ ] **Step 5: Re-run the hook after the composite is overwritten**

Add as the final statement of `setDefaultFormat`, after the `if(titleFormat != null)` block closes at `:716`:

```java
      // super seeded the title composite this method then overwrote; re-run against the values
      // that should stand. The hook is a set of unconditional writes, so a second run is free
      seedChromeDefaults(VizContext.of(this));
   }
```

- [ ] **Step 6: Run the tests to verify they pass**

Run: `./mvnw test -pl core -Dtest=SeedChromeDefaultsTest`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add core/src/main/java/inetsoft/uql/viewsheet/internal/TimeSliderVSAssemblyInfo.java \
        core/src/test/java/inetsoft/uql/viewsheet/internal/SeedChromeDefaultsTest.java
git commit -m "feat(viewsheet): seed the range slider title rule"
```

---

## Task 3: The selection container

**Files:**
- Modify: `core/src/main/java/inetsoft/uql/viewsheet/internal/CurrentSelectionVSAssemblyInfo.java`
- Test: `core/src/test/java/inetsoft/uql/viewsheet/internal/SeedChromeDefaultsTest.java`

**Interfaces:**
- Consumes: `VSTitleChromeDefaults.titleRuleBorders()`, `.titleForegroundValue(VizContext)`, and `VSAssemblyInfo.DEFAULT_TITLE_BG`.
- Produces: `CurrentSelectionVSAssemblyInfo.seedChromeDefaults(VizContext)`.

**Why this exists:** this is the only one of the four that is structurally the table's case. It does not override `setDefaultFormat`, so it takes the base's filled `DEFAULT_TITLE_BG` band and — because `initDefaultFormat:69` passes `border = true` through `setDefaultFormat(true, true, false)` — a four-side `THIN_LINE` box. Both have to move.

**Two preconditions, both verified, because the override depends on them.** The base installs a TITLEPATH composite only for a `TitledVSAssemblyInfo` (`VSAssemblyInfo:1218-1227`), and this type qualifies through `CompositeVSAssemblyInfo extends TitledVSAssemblyInfo` — so `getFormatInfo().getFormat(TITLEPATH)` is non-null. And this type does **not** override `setDefaultFormat`, so unlike Tasks 1 and 2 there is no ordering trap and no re-run to add.

**It writes no border colours, on either branch.** This type is not on `installsOwnTitleFormat()`, so the base hook's own block (`VSAssemblyInfo:1263-1267`) already writes `bcolors` onto the title border: `VSObjectChromeDefaults.objectBorderColor(ctx)` when modern, `DEFAULT_BORDER_COLOR` when not. And `OBJECT_BORDER` is `0xD9D5CC` with `OBJECT_BORDER_DARK` at `0x49454F` — **the same two values** `titleRuleColors(ctx)` produces. The colour is already correct in both modes from the base. Writing it again here would be duplicate authority over one value, and the legacy branch would then have to reproduce `DEFAULT_BORDER_COLOR` by hand. Leave it to the base.

- [ ] **Step 1: Write the failing tests**

Add the fixture beside the others in `SeedChromeDefaultsTest`:

```java
   private CurrentSelectionVSAssemblyInfo newCurrentSelection() {
      Viewsheet vs = new Viewsheet();
      CurrentSelectionVSAssembly container = new CurrentSelectionVSAssembly(vs, "Container1");
      container.getVSAssemblyInfo().initDefaultFormat();
      return (CurrentSelectionVSAssemblyInfo) container.getVSAssemblyInfo();
   }
```

Then:

```java
   @Test
   void aModernSelectionContainerTitleIsUnfilledWithABottomRule() {
      gateOn();
      VizContext ctx = VizContext.ofGate();
      VSFormat def = titleFormat(newCurrentSelection()).getDefaultFormat();

      assertNull(def.getBackgroundValue(), "the modern title lane carries no fill");
      assertNull(def.getBackground(), "and no runtime background behind it either");

      Insets borders = def.getBordersValue();
      assertEquals(StyleConstants.NONE, borders.top, "no top rule");
      assertEquals(StyleConstants.NONE, borders.left, "no left rule");
      assertEquals(StyleConstants.NONE, borders.right, "no right rule");
      assertEquals(StyleConstants.THIN_LINE, borders.bottom, "a bottom rule only");

      assertEquals(VSTitleChromeDefaults.titleBorderColor(ctx),
                   def.getBorderColorsValue().bottomColor,
                   "the base's object border colour is already the rule colour");
   }

   @Test
   void aLegacySelectionContainerTitleKeepsTheFilledBandAndTheFourSideBox() {
      gateOff();
      VSFormat def = titleFormat(newCurrentSelection()).getDefaultFormat();

      assertEquals(VSAssemblyInfo.DEFAULT_TITLE_BG, def.getBackgroundValue());

      Insets borders = def.getBordersValue();
      assertEquals(StyleConstants.THIN_LINE, borders.top);
      assertEquals(StyleConstants.THIN_LINE, borders.left);
      assertEquals(StyleConstants.THIN_LINE, borders.right);
      assertEquals(StyleConstants.THIN_LINE, borders.bottom);
   }

   @Test
   void revertingASelectionContainerTitleRestoresAGateOffCreation() {
      gateOff();
      VSFormat expected = titleFormat(newCurrentSelection()).getDefaultFormat();
      String expectedBg = expected.getBackgroundValue();
      Insets expectedBorders = expected.getBordersValue();

      gateOn();
      CurrentSelectionVSAssemblyInfo info = newCurrentSelection();
      info.setVizMark(VizMark.fromGate());
      info.seedChromeDefaults(VizContext.of(info));

      info.setVizMark(null);
      info.seedChromeDefaults(VizContext.of((VizMark) null));

      VSFormat reverted = titleFormat(info).getDefaultFormat();
      assertEquals(expectedBg, reverted.getBackgroundValue(),
                   "Revert must match a container that was never modernized");
      assertEquals(expectedBorders, reverted.getBordersValue());
   }
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./mvnw test -pl core -Dtest=SeedChromeDefaultsTest`
Expected: `aModernSelectionContainerTitleIsUnfilledWithABottomRule` FAILS on the background assertion — the observed value is `0xf5f5f5`. The other two PASS.

- [ ] **Step 3: Add the seed override**

Add to `CurrentSelectionVSAssemblyInfo`, after `initDefaultFormat` at `:70`:

```java
   @Override
   protected void seedChromeDefaults(VizContext ctx) {
      super.seedChromeDefaults(ctx);

      // the title lane: modern is unfilled with a bottom rule, legacy is the filled band and the
      // four-side box the base writes for a bordered titled assembly. Both branches write, because
      // the legacy one is what Revert relies on. The border colours are left to the base's own
      // block, whose object-border value is already the rule colour in both modes
      VSCompositeFormat titleFormat = getFormatInfo().getFormat(TITLEPATH);

      if(titleFormat != null) {
         VSFormat def = titleFormat.getDefaultFormat();

         if(ctx.modern) {
            def.setBackgroundValue(null);
            // getBackground() falls back to the bg field when bgval yields nothing, so a clear
            // has to null both or a runtime background survives it
            def.setBackground(null);
            def.setBordersValue(VSTitleChromeDefaults.titleRuleBorders());
            def.setForegroundValue(VSTitleChromeDefaults.titleForegroundValue(ctx));
         }
         else {
            def.setBackgroundValue(DEFAULT_TITLE_BG);
            def.setBordersValue(new Insets(StyleConstants.THIN_LINE, StyleConstants.THIN_LINE,
                                           StyleConstants.THIN_LINE, StyleConstants.THIN_LINE));
            def.setForegroundValue(null);
         }

         // getForeground() has the same field fallback getBackground() has
         def.setForeground(null);
      }
   }
```

The class currently imports `inetsoft.uql.viewsheet.DynamicValue` and `Viewsheet` individually (`:23-24`) and `java.awt.*` (`:32`). Add `inetsoft.report.StyleConstants`, `inetsoft.uql.viewsheet.VSCompositeFormat` and `inetsoft.uql.viewsheet.VSFormat`; `Insets` comes from the existing `java.awt.*`.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./mvnw test -pl core -Dtest=SeedChromeDefaultsTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/inetsoft/uql/viewsheet/internal/CurrentSelectionVSAssemblyInfo.java \
        core/src/test/java/inetsoft/uql/viewsheet/internal/SeedChromeDefaultsTest.java
git commit -m "feat(viewsheet): seed the selection container title lane unfilled"
```

---

## Task 4: Switch the substitution off — the browser is final here

**Files:**
- Modify: `core/src/main/java/inetsoft/uql/viewsheet/internal/VSTitleChromeDefaults.java:154-159`
- Modify: `core/src/main/java/inetsoft/web/viewsheet/model/VSCompositeModel.java:38-41`
- Modify: `core/src/main/java/inetsoft/web/viewsheet/model/VSRangeSliderModel.java:107-109`
- Test: `core/src/test/java/inetsoft/uql/viewsheet/internal/VSTitleChromeDefaultsTest.java`
- Test: `core/src/test/java/inetsoft/web/viewsheet/model/VSFormatModelTest.java`

**Interfaces:**
- Consumes: `VSTitleChromeDefaults.applyModernDefaults(VSCompositeFormat, VizContext, VSAssemblyInfo)` — the three-argument form, which already exists.
- Produces: `isSeededTitle` returning true for `SelectionBaseVSAssemblyInfo`, `TimeSliderVSAssemblyInfo` and `CurrentSelectionVSAssemblyInfo` in addition to the two it already matches.

**Why this exists:** until both halves land — the type in `isSeededTitle` *and* the call site passing the info — the substitution keeps painting `#F1EFEA` over the seeded lane. This task lands both for the browser, and by widening the predicate it also completes the two umbrella server sites that already pass an info and were waiting on it.

- [ ] **Step 1: Write the failing tests**

In `VSTitleChromeDefaultsTest`, replace `aDeferredTypeIsStillSubstituted` at `:215-226` — its subject is now a converted type — with:

```java
   @Test
   void theSelectionFamilyIsNotSubstituted() {
      withProperty("viewsheet.modernVisualization", "true", () -> {
         VizContext ctx = VizContext.ofGate();
         VSCompositeFormat fmt = new VSCompositeFormat();

         assertSame(fmt, VSTitleChromeDefaults.applyModernDefaults(
                       fmt, ctx, new SelectionListVSAssemblyInfo()),
                    "a selection list carries its title chrome in the stored format");
         assertSame(fmt, VSTitleChromeDefaults.applyModernDefaults(
                       fmt, ctx, new SelectionTreeVSAssemblyInfo()), "so does a tree");
         assertSame(fmt, VSTitleChromeDefaults.applyModernDefaults(
                       fmt, ctx, new CurrentSelectionVSAssemblyInfo()), "so does a container");
         assertSame(fmt, VSTitleChromeDefaults.applyModernDefaults(
                       fmt, ctx, new TimeSliderVSAssemblyInfo()),
                    "and so does a range slider, which is a sibling of the selection base "
                       + "rather than a subclass and needs its own branch");
      });
   }

   @Test
   void aDeferredTypeIsStillSubstituted() {
      withProperty("viewsheet.modernVisualization", "true", () -> {
         VizContext ctx = VizContext.ofGate();
         VSCompositeFormat fmt = new VSCompositeFormat();
         CheckBoxVSAssemblyInfo box = new CheckBoxVSAssemblyInfo();

         assertEquals(0xF1EFEA,
                      rgb(VSTitleChromeDefaults.applyModernDefaults(fmt, ctx, box).getBackground()),
                      "checkbox, radio and calendar have not converted yet");
      });
   }
```

In `VSFormatModelTest`, pin what the browser actually receives for a container. It already imports `SreeEnv`, `VSCompositeFormat`, `Viewsheet` and `VSAssemblyInfo`; **add** `inetsoft.uql.viewsheet.CurrentSelectionVSAssembly`, `inetsoft.uql.viewsheet.internal.VSTitleChromeDefaults`, `inetsoft.uql.viewsheet.internal.VizContext` and `static org.junit.jupiter.api.Assertions.assertNull` (the class imports assertions individually, not by wildcard).

```java
   @Test
   void aModernSelectionContainerTitleReachesTheBrowserUnfilled() {
      SreeEnv.setProperty("viewsheet.modernVisualization", "true");

      try {
         Viewsheet vs = new Viewsheet();
         CurrentSelectionVSAssembly container = new CurrentSelectionVSAssembly(vs, "Container1");
         container.getVSAssemblyInfo().initDefaultFormat();
         vs.addAssembly(container);

         VSCompositeFormat title = container.getVSAssemblyInfo().getFormatInfo()
            .getFormat(VSAssemblyInfo.TITLEPATH);

         assertNull(VSTitleChromeDefaults.applyModernDefaults(
                       title, VizContext.of(container.getVSAssemblyInfo()),
                       container.getVSAssemblyInfo()).getBackground(),
                    "the substitution must not refill the lane the seed just cleared");
      }
      finally {
         SreeEnv.setProperty("viewsheet.modernVisualization", null);
      }
   }
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./mvnw test -pl core -Dtest=VSTitleChromeDefaultsTest,VSFormatModelTest`
Expected: `theSelectionFamilyIsNotSubstituted` FAILS on the first `assertSame` — the substitution still clones and fills. `aModernSelectionContainerTitleReachesTheBrowserUnfilled` FAILS — the observed background is `#F1EFEA`.

- [ ] **Step 3: Widen the predicate**

`VSTitleChromeDefaults:157-159` becomes:

```java
   private static boolean isSeededTitle(VSAssemblyInfo info) {
      // TimeSliderVSAssemblyInfo is a sibling of SelectionBaseVSAssemblyInfo, not a subclass, so
      // it needs its own branch; CurrentSelectionVSAssemblyInfo is a container and shares neither
      return info instanceof ChartVSAssemblyInfo
         || info instanceof TableDataVSAssemblyInfo
         || info instanceof SelectionBaseVSAssemblyInfo
         || info instanceof TimeSliderVSAssemblyInfo
         || info instanceof CurrentSelectionVSAssemblyInfo;
   }
```

Update the comment above it at `:154-156` — it says "the rest are still substituted here", which stays true, but name the three that remain so the next converter knows the finish line: checkbox, radio button and calendar.

- [ ] **Step 4: Convert the two browser call sites**

`VSCompositeModel:38-41` — the info is already in scope as `assemblyInfo`:

```java
      titleFormat = new VSFormatModel(VSTitleChromeDefaults.applyModernDefaults(
                                         finfo.getFormat(VSAssemblyInfo.TITLEPATH, false),
                                         VizContext.of((VSAssemblyInfo) assemblyInfo),
                                         (VSAssemblyInfo) assemblyInfo),
                                      (VSAssemblyInfo) assemblyInfo);
```

`VSRangeSliderModel:107-109`:

```java
      VSCompositeFormat compositeTitleFormat =
         VSTitleChromeDefaults.applyModernDefaults(
            assemblyInfo.getFormatInfo().getFormat(titlepath, false),
            VizContext.of(assemblyInfo), assemblyInfo);
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `./mvnw test -pl core -Dtest=VSTitleChromeDefaultsTest,VSFormatModelTest,SeedChromeDefaultsTest`
Expected: PASS.

- [ ] **Step 6: Compile the module and run the neighbouring suites**

Run: `./mvnw clean install -pl core -am -DskipTests` then `./mvnw test -pl core -Dtest=VSObjectModelVizContextTest,VizModernizeUtilTest,RevertViewsheetServiceTest,ModernizeViewsheetServiceTest`
Expected: BUILD SUCCESS and PASS. **Use `clean`** — a 77-module incremental `install` has reported SUCCESS over a real break on this branch before.

- [ ] **Step 7: Commit**

```bash
git add core/src/main/java/inetsoft/uql/viewsheet/internal/VSTitleChromeDefaults.java \
        core/src/main/java/inetsoft/web/viewsheet/model/VSCompositeModel.java \
        core/src/main/java/inetsoft/web/viewsheet/model/VSRangeSliderModel.java \
        core/src/test/java/inetsoft/uql/viewsheet/internal/VSTitleChromeDefaultsTest.java \
        core/src/test/java/inetsoft/web/viewsheet/model/VSFormatModelTest.java
git commit -m "feat(viewsheet): skip the read-time title fill for the selection family"
```

State in the commit body that widening the predicate also completes `AbstractVSExporter:1802-1804` and `FormatPainterService:220`, which already passed an info.

---

## Task 5: The three export call sites

**Files:**
- Modify: `core/src/main/java/inetsoft/report/io/viewsheet/VSSelectionListHelper.java:75-77`
- Modify: `core/src/main/java/inetsoft/report/io/viewsheet/VSSelectionTreeHelper.java:267-269`
- Modify: `core/src/main/java/inetsoft/report/io/viewsheet/ExportUtil.java:109-115`
- Modify: `core/src/main/java/inetsoft/report/io/viewsheet/pdf/PDFVSExporter.java:653`
- Modify: `core/src/main/java/inetsoft/report/io/viewsheet/svg/SVGVSExporter.java:374`
- Test: `core/src/test/java/inetsoft/uql/viewsheet/BookmarkChromeResolutionTest.java`

**Interfaces:**
- Consumes: the three-argument `applyModernDefaults`, and `isSeededTitle` as widened in Task 4.
- Produces: `ExportUtil.getBackGroundColor(VSCompositeFormat, VSCompositeFormat, VizContext, VSAssemblyInfo)` — a widened signature, replacing the three-argument form. Both of its callers are converted in the same task.

- [ ] **Step 1: Convert the two helpers**

`VSSelectionListHelper:75-77` — `info` is in scope:

```java
            format = VSTitleChromeDefaults.applyModernDefaults(finfo.getFormat(
               new TableDataPath(-1, TableDataPath.TITLE), false), VizContext.of(info), info);
```

`VSSelectionTreeHelper:267-269`:

```java
      VSCompositeFormat format = VSTitleChromeDefaults.applyModernDefaults(
         finfo.getFormat(new TableDataPath(-1, TableDataPath.TITLE), false),
         VizContext.of(info), info);
```

- [ ] **Step 2: Add the bookmark-state guard**

**This family carries no format in its bookmark state — verified, not assumed.** `SelectionVSAssemblyInfo.writeStateContent:296-311` writes only `state_selectionStyle`, `state_mixedSingleSelection` and `state_singleSelectionLevels`; the selection tree's `<state_info>` wrapper (`SelectionTreeVSAssembly:1037-1041`) delegates to that same method, and the container writes only `<oneAssembly>` entries. So the defect `f47c59304` fixed for tables and charts cannot arise here, and no resolution test is needed.

What *is* worth one cheap test is a guard, so that if someone later adds a format to this state the failure names the reason. Add to `BookmarkChromeResolutionTest`:

```java
   @Test
   void theSelectionFamilyCarriesNoFormatInItsBookmarkState() throws Exception {
      // the seeded title lane needs no restore-side resolution for these types because their
      // state carries no format at all. If this fails, a format was added to the state and the
      // re-seed at parseState now has to be checked for them the way it is for a table
      Viewsheet vs = new Viewsheet();
      SelectionListVSAssembly list = new SelectionListVSAssembly(vs, "Selection1");
      list.getVSAssemblyInfo().initDefaultFormat();
      vs.addAssembly(list);
      modernizeViaGate(vs);

      String state = writeState(list);

      assertFalse(state.contains("<state_format>"), "no object format in a selection's state");
      assertFalse(state.contains("VSCompositeFormat"), "and no format anywhere inside it");
   }
```

Reuse the class's existing `private static` helpers `modernizeViaGate` (`:477`) and `writeState` (`:502`) — the pattern is `aStaleBookmarkDoesNotUnRevertTheTitleLane:151-173`. No new imports: `assertFalse` comes from the wildcard `import static org.junit.jupiter.api.Assertions.*` at `:44`, and `SelectionListVSAssembly` is in this test's own package.

- [ ] **Step 3: Run the tests**

Run: `./mvnw test -pl core -Dtest=BookmarkChromeResolutionTest,SeedChromeDefaultsTest,VSTitleChromeDefaultsTest`
Expected: PASS.

- [ ] **Step 4: Convert `ExportUtil.getBackGroundColor` — it is a third call site, not a bystander**

**This one is easy to miss and Tasks 1–4 do not fix it.** `ExportUtil.getBackGroundColor:109-115` calls the **two-argument** `applyModernDefaults`, which delegates with `info = null`, and `isSeededTitle(null)` is false however wide the predicate grows. So without this step a range slider inside a selection container still gets `#F1EFEA` painted onto its title format at export, and — because the method resolves *title background, else object background* — that stale fill wins the conditional and lands on the exported title as a `UserDefinedFormat` background.

Widen the signature to take the info, matching the shape Task 4 used elsewhere:

```java
   public static Color getBackGroundColor(VSCompositeFormat titleFormat,
                                          VSCompositeFormat objectFormat, VizContext ctx,
                                          VSAssemblyInfo info)
   {
      titleFormat = VSTitleChromeDefaults.applyModernDefaults(titleFormat, ctx, info);
      return titleFormat != null && titleFormat.getBackground() != null ?
         titleFormat.getBackground() : objectFormat != null &&
         objectFormat.getBackground() != null ? objectFormat.getBackground() : null;
   }
```

No new imports — `ExportUtil` already has `inetsoft.uql.viewsheet.internal.*` at `:40`.

Both callers are inside `writeTimeSlider(TimeSliderVSAssembly)` and already hold `info`, a `TimeSliderVSAssemblyInfo`. Change `PDFVSExporter:653` and `SVGVSExporter:374` identically:

```java
         format.getUserDefinedFormat().setBackground(
            ExportUtil.getBackGroundColor(format, info.getFormat(), VizContext.of(info), info));
```

**Widen the signature rather than adding an overload.** Two callers, both converted here; an overload would leave a two-arg form that silently substitutes and is exactly the trap this step exists to close.

**Then find any caller the grep above missed:** `grep -rn "getBackGroundColor" --include=*.java core enterprise`. A missed one is a compile error rather than a silent bug, which is why widening is safer — but check the enterprise modules too, because a 77-module incremental `install` has reported SUCCESS over a cross-module signature break on this branch before.

- [ ] **Step 5: Verify the slider's export background by exporting**

With Step 4 in, the title background is null and the expression falls through to `objectFormat.getBackground()` — the container's card background. That is the correct outcome for an unfilled lane, and it is the first place in this work where *removing* a value changes which arm of an existing conditional runs, so it is checked by export rather than by reasoning.

Build, start (`docker/target/docker-test`, `docker compose up -d`), and export a viewsheet holding a selection container with a child range slider to **PDF** (`PDFVSExporter:653`) and **PNG** (`SVGVSExporter:374`). Confirm the slider's title lane is unfilled and carries the rule, on both. Then repeat with the gate off and confirm it is unchanged from before this work.

**Do not improvise a fix if it looks wrong.** Record what you observe and stop — a change here reaches every current-selection export.

- [ ] **Step 6: Build and run the full core suite**

Run: `./mvnw clean install -pl core -am -DskipTests` then `./mvnw test -pl core`
Expected: BUILD SUCCESS, and no new failures against the branch baseline.

- [ ] **Step 7: Commit**

```bash
git add core/src/main/java/inetsoft/report/io/viewsheet/VSSelectionListHelper.java \
        core/src/main/java/inetsoft/report/io/viewsheet/VSSelectionTreeHelper.java \
        core/src/main/java/inetsoft/report/io/viewsheet/ExportUtil.java \
        core/src/main/java/inetsoft/report/io/viewsheet/pdf/PDFVSExporter.java \
        core/src/main/java/inetsoft/report/io/viewsheet/svg/SVGVSExporter.java \
        utils/inetsoft-xml-formats/src/main/java/inetsoft/report/io/viewsheet/ppt/PPTVSExporter.java \
        core/src/test/java/inetsoft/uql/viewsheet/BookmarkChromeResolutionTest.java
git commit -m "feat(viewsheet): pass the assembly to the selection export title resolvers"
```

**Every file the widening touches goes in one commit.** Staging only the two helpers and the test — as an earlier revision of this list did — leaves a widened `getBackGroundColor` committed with its callers still on the old arity: a broken build in history. `PPTVSExporter` lives in a *different Maven module* (`utils/inetsoft-xml-formats`) and was not in this plan's original file list; it was found during execution by the `grep -rn "getBackGroundColor"` sweep Step 4 mandates. That is the second time a signature change on this branch has reached that module — the title lane design records the same surprise for `getValueFormat` (Excel list, Excel tree, PPT) — so treat the sweep as load-bearing, not a formality.

Record the PDF and PNG observation from Step 4 in the commit body.

---

## Final Verification

- [ ] **Full core suite:** `./mvnw test -pl core` — no new failures against the branch baseline.
- [ ] **Cross-module build:** `./mvnw clean install -DskipTests -Pcommunity,enterprise` — BUILD SUCCESS.
- [ ] **Standalone matrix:** selection list, selection tree, selection container and range slider, each standalone, at dense / compact / comfortable, in the viewer. Each shows an unfilled lane with one `#D9D5CC` hairline.
- [ ] **Dark:** the same four with `viewsheet.darkMode` on — the rule is `#49454F`.
- [ ] **The ladder check.** A populated selection container holding a child selection list, a child selection tree and a child range slider, plus one collapsed outer-selection row. The container's own title and each child's title each draw one rule; the outer-selection row is unchanged, because it reads the container's *object* format rather than any title format. **This is the check the design deferred a decision to** — if the stack of hairlines reads badly, record what you see and raise it rather than fixing it here.
- [ ] **Export the same container** to PDF, PNG and Excel, and confirm each matches the viewer. Task 5 Step 4's slider background is the specific case.
- [ ] **Author's format survives:** set a title background on a selection list through the composer's format pane. It stays, and the pane shows what the canvas draws — that last half is `FormatPainterService:220`, which Task 4 completed.
- [ ] **Revert parity, per type.** Revert a dashboard holding all four and compare each against one that was never modernized. The three legacy branches differ; check each rather than checking one and generalising.
- [ ] **Deferred types unmoved:** checkbox, radio button and calendar — identical to before this change, still filled `#F1EFEA` at read time.
- [ ] **Gate off is byte-identical:** the whole matrix with `viewsheet.modernVisualization` set explicitly to **`false`**, against a build from before this work. Unsetting it no longer means off — `c7790bbf0` made the shipped default true.
- [ ] **Density is compact by default now**, per `c7790bbf0`, so the default-path manual checks run at compact rather than dense. Check dense explicitly rather than assuming it is what you get.
- [ ] **The portability case, which is why this is seeded:** export a viewsheet holding all four to a deployment JAR, import it into a second server, and confirm all four title lanes arrive unfilled with their rule.
