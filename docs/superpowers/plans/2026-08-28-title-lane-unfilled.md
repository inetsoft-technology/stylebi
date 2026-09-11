# Title Lane Unfilled — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A modern chart / table / crosstab / calc-table title lane draws no fill and a single 1px bottom rule, with the value written into the assembly's stored format at creation so it travels in an exported asset.

**Architecture:** The values are seeded by `seedChromeDefaults`, the hook that already writes the object border and card radius, so Revert and bookmark-restore resolve them for free through the machinery that exists. `VSTitleChromeDefaults` keeps the palette, gains two shape helpers, and its read-time substitution becomes a shim that early-returns for seeded types and is deleted when the last type converts.

**Tech Stack:** Java 21, Maven, JUnit 5 + Spring test context. No frontend change — `vs-title.component.html:35` already binds `border.bottom` from the model.

**Spec:** [`docs/superpowers/specs/lookfeel/2026-08-28-title-lane-unfilled-design.md`](../specs/lookfeel/2026-08-28-title-lane-unfilled-design.md)

## Global Constraints

- **Branch:** `viz-updates`. Never commit to `main`, `v1.0.x` or `v1.1.x`.
- **Gate-off output must stay byte-identical to a gate-off build without this change.** Every write is inside a `ctx.modern` ternary whose legacy branch reproduces what a gate-off creation writes.
- **The legacy branch is the Revert contract.** `VizModernizeUtil.revert:102-111` clears the mark and re-runs `seedChromeDefaults`; there is no separate reverser. A legacy branch that only *nearly* matches a never-modernized assembly is a defect.
- **Rule colour:** `TITLE_BORDER = 0xD9D5CC`, dark `TITLE_BORDER_DARK = 0x49454F`. Both constants already exist in `VSTitleChromeDefaults`. Add no new colour.
- **Rule shape:** `Insets(NONE, NONE, THIN_LINE, NONE)` — bottom only. All four border *colours* are written to the same value; `TextBoxElementDef.setBorderColors:209-210` keeps only `topColor`, and writing one colour four times is what makes that lossy setter harmless.
- **Never write a per-side border palette on a title format.** See the line above.
- **Density is out of scope.** Do not seed anything derived from `VSDensityDefaults.mode()` — it reads a live org property.
- **Comments:** short clauses, no ticket or PR numbers, no references to this plan or the spec inside source comments.
- **Test command (from `community/`):** `./mvnw test -pl core -Dtest=<ClassName>` (PowerShell: `.\mvnw.cmd test -pl core "-Dtest=<ClassName>"`). Surefire is configured with `<groups>core</groups>`, so test classes need `@Tag("core")` — the three classes touched here already have it.

---

## File Structure

| File | Responsibility | Task |
|---|---|---|
| `core/src/main/java/inetsoft/uql/viewsheet/internal/VSTitleChromeDefaults.java` | Palette + the rule's shape; later the seeded-type shim | 1, 3 |
| `core/src/main/java/inetsoft/uql/viewsheet/internal/TableDataVSAssemblyInfo.java` | The table family's seed | 1 |
| `core/src/main/java/inetsoft/uql/viewsheet/internal/ChartVSAssemblyInfo.java` | The chart's seed + the creation-ordering fix | 2 |
| `core/src/main/java/inetsoft/report/io/viewsheet/AbstractVSExporter.java` | Carry the border onto the synthetic export title | 4, 5 |
| `core/src/main/java/inetsoft/report/io/viewsheet/VSTableDataHelper.java` | Pass the info | 4 |
| `core/src/main/java/inetsoft/uql/viewsheet/internal/VsToReportConverter.java` | Pass the info | 4 |
| `core/src/main/java/inetsoft/web/composer/vs/controller/FormatPainterService.java` | Pass the info | 4 |
| `core/src/main/java/inetsoft/web/viewsheet/model/chart/VSChartModel.java` | Pass the info | 3 |
| `core/src/main/java/inetsoft/web/viewsheet/model/table/BaseTableModel.java` | Pass the info | 3 |
| `core/src/test/java/inetsoft/uql/viewsheet/internal/SeedChromeDefaultsTest.java` | The seed's contract, both branches | 1, 2 |
| `core/src/test/java/inetsoft/uql/viewsheet/internal/VSTitleChromeDefaultsTest.java` | The shim's contract | 3 |
| `core/src/test/java/inetsoft/uql/viewsheet/BookmarkChromeResolutionTest.java` | Proof that restore resolves the new seed | 6 |

**Intermediate states, so a reviewer is not surprised.** After Task 1 and Task 2 a modern table and chart show the fill *and* the new rule — the read-time substitution has not been switched off yet. Task 3 removes the fill and is the point at which the browser is final. This is expected; do not "fix" it inside Tasks 1–2.

---

## Task 1: The rule's shape, and the table family's seed

**Files:**
- Modify: `core/src/main/java/inetsoft/uql/viewsheet/internal/VSTitleChromeDefaults.java`
- Modify: `core/src/main/java/inetsoft/uql/viewsheet/internal/TableDataVSAssemblyInfo.java:1587-1601`
- Test: `core/src/test/java/inetsoft/uql/viewsheet/internal/SeedChromeDefaultsTest.java`

**Interfaces:**
- Consumes: nothing from earlier tasks.
- Produces: `VSTitleChromeDefaults.titleRuleBorders()` → `java.awt.Insets`; `VSTitleChromeDefaults.titleRuleColors(VizContext)` → `inetsoft.uql.viewsheet.BorderColors`. Tasks 2 and 3 both use these.

- [ ] **Step 1: Write the failing tests**

Add to `SeedChromeDefaultsTest`, after the existing `aTableTitleTakesTheModernBorderColour` test. The class already provides `gateOn()`, `gateOff()`, `newTable()` and `titleFormat(info)` — use them, do not add new helpers.

```java
   @Test
   void aModernTableTitleIsUnfilledWithABottomRule() {
      gateOn();
      VizContext ctx = VizContext.ofGate();
      VSFormat def = titleFormat(newTable()).getDefaultFormat();

      assertNull(def.getBackgroundValue(), "the modern title lane carries no fill");
      assertNull(def.getBackground(), "and no runtime background behind it either");

      Insets borders = def.getBordersValue();
      assertNotNull(borders);
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
   void aLegacyTableTitleKeepsTheFilledBandAndTheFourSideBox() {
      gateOff();
      VSFormat def = titleFormat(newTable()).getDefaultFormat();

      assertEquals(VSAssemblyInfo.DEFAULT_TITLE_BG, def.getBackgroundValue());

      Insets borders = def.getBordersValue();
      assertEquals(StyleConstants.THIN_LINE, borders.top);
      assertEquals(StyleConstants.THIN_LINE, borders.left);
      assertEquals(StyleConstants.THIN_LINE, borders.right);
      assertEquals(StyleConstants.THIN_LINE, borders.bottom);
   }

   @Test
   void revertingATableTitleRestoresAGateOffCreation() {
      gateOff();
      VSFormat expected = titleFormat(newTable()).getDefaultFormat();
      String expectedBg = expected.getBackgroundValue();
      Insets expectedBorders = expected.getBordersValue();

      gateOn();
      TableVSAssemblyInfo info = newTable();
      info.setVizMark(VizMark.fromGate());
      info.seedChromeDefaults(VizContext.of(info));

      info.setVizMark(null);
      info.seedChromeDefaults(VizContext.of((VizMark) null));

      VSFormat reverted = titleFormat(info).getDefaultFormat();
      assertEquals(expectedBg, reverted.getBackgroundValue(),
                   "Revert must match a table that was never modernized, not almost match it");
      assertEquals(expectedBorders, reverted.getBordersValue());
   }
```

The class already imports `java.awt.Color`, `java.awt.Insets` and `inetsoft.uql.viewsheet.*` (which covers `VSFormat` and `BorderColors`). Add one import for `StyleConstants`:

```java
import inetsoft.report.StyleConstants;
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./mvnw test -pl core -Dtest=SeedChromeDefaultsTest`
Expected: FAIL. `aModernTableTitleIsUnfilledWithABottomRule` fails on the background assertion (it is `0xf5f5f5` today); `aLegacyTableTitleKeepsTheFilledBandAndTheFourSideBox` PASSES already (it pins current behaviour — that is intentional, it is the regression guard); `revertingATableTitleRestoresAGateOffCreation` passes today and must keep passing.

- [ ] **Step 3: Add the shape helpers to `VSTitleChromeDefaults`**

Change the imports at the top of the file from:

```java
import inetsoft.uql.viewsheet.VSCompositeFormat;
import inetsoft.uql.viewsheet.VSFormat;

import java.awt.Color;
```

to:

```java
import inetsoft.report.StyleConstants;
import inetsoft.uql.viewsheet.BorderColors;
import inetsoft.uql.viewsheet.VSCompositeFormat;
import inetsoft.uql.viewsheet.VSFormat;

import java.awt.Color;
import java.awt.Insets;
```

Then add these two methods immediately after `titleBorderColor`:

```java
   /**
    * The modern title lane's borders: a bottom rule only. A fresh Insets every call, because
    * Insets is mutable and the caller installs it on a format.
    */
   public static Insets titleRuleBorders() {
      return new Insets(StyleConstants.NONE, StyleConstants.NONE,
                        StyleConstants.THIN_LINE, StyleConstants.NONE);
   }

   /**
    * The rule's colour on all four sides, though only the bottom carries a width. A report text
    * box keeps one border colour and discards the rest, so one colour four times is what makes
    * that setter lossless here.
    */
   public static BorderColors titleRuleColors(VizContext ctx) {
      Color c = titleBorderColor(ctx);
      return new BorderColors(c, c, c, c);
   }
```

- [ ] **Step 4: Seed the table family's title**

In `TableDataVSAssemblyInfo.seedChromeDefaults`, append to the existing method body (after the `objFormat` block, before the closing brace):

```java
      // the title lane: modern is unfilled with a bottom rule, legacy is the filled band and the
      // four-side box setDefaultFormat(border = true) writes. Both branches write, because the
      // legacy one is what Revert relies on to restore a never-modernized table
      VSCompositeFormat titleFormat = getFormatInfo().getFormat(TITLEPATH);

      if(titleFormat != null) {
         VSFormat def = titleFormat.getDefaultFormat();

         if(ctx.modern) {
            def.setBackgroundValue(null);
            // getBackground() falls back to the bg field when bgval yields nothing, so a clear
            // has to null both or a runtime background survives it
            def.setBackground(null);
            def.setBordersValue(VSTitleChromeDefaults.titleRuleBorders());
            def.setBorderColorsValue(VSTitleChromeDefaults.titleRuleColors(ctx));
         }
         else {
            def.setBackgroundValue(DEFAULT_TITLE_BG);
            def.setBordersValue(new Insets(StyleConstants.THIN_LINE, StyleConstants.THIN_LINE,
                                           StyleConstants.THIN_LINE, StyleConstants.THIN_LINE));
         }
      }
```

The legacy branch writes no border *colours*: `super.seedChromeDefaults(ctx)` has already set them to `DEFAULT_BORDER_COLOR` at `VSAssemblyInfo:1263-1267`, and its guard (`getBordersValue() != null`) holds on both the creation and the Revert path.

Add whatever of these imports the file lacks:

```java
import inetsoft.report.StyleConstants;
import inetsoft.uql.viewsheet.VSFormat;

import java.awt.Insets;
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `./mvnw test -pl core -Dtest=SeedChromeDefaultsTest`
Expected: PASS, all tests in the class including the pre-existing ones. If `aTableTitleTakesTheModernBorderColour` fails, the rule colour and the object border colour have diverged — they must both be `#D9D5CC`.

- [ ] **Step 6: Commit**

```bash
git add core/src/main/java/inetsoft/uql/viewsheet/internal/VSTitleChromeDefaults.java \
        core/src/main/java/inetsoft/uql/viewsheet/internal/TableDataVSAssemblyInfo.java \
        core/src/test/java/inetsoft/uql/viewsheet/internal/SeedChromeDefaultsTest.java
git commit -m "feat(viewsheet): seed the table title lane unfilled with a bottom rule

The modern title lane stops being a filled band. The value is written at
creation rather than substituted at render, so it travels in an exported
asset and resolves on bookmark restore through the hook that already
handles the object border and card radius.

The legacy branch restores the filled band and the four-side box a
gate-off creation writes, which is what Revert relies on."
```

---

## Task 2: The chart's seed, and the creation-ordering fix

**Files:**
- Modify: `core/src/main/java/inetsoft/uql/viewsheet/internal/ChartVSAssemblyInfo.java:86-99` and `:101-140`
- Test: `core/src/test/java/inetsoft/uql/viewsheet/internal/SeedChromeDefaultsTest.java`

**Interfaces:**
- Consumes: `VSTitleChromeDefaults.titleRuleBorders()`, `VSTitleChromeDefaults.titleRuleColors(VizContext)` from Task 1.
- Produces: nothing new. Task 5 relies on a modern chart's title format having a non-null `getBorders()`.

**Why this task is separate from Task 1:** the chart has an ordering problem the table does not. `VSAssemblyInfo.setDefaultFormat` calls `seedChromeDefaults` as its last act (`:1235`), but `ChartVSAssemblyInfo.setDefaultFormat` calls `super` at `:89` and then **replaces the whole TITLEPATH composite at `:98`**, discarding whatever the hook wrote. The fix is to re-invoke the hook after the install.

- [ ] **Step 1: Update the existing test that pins the old ordering**

`SeedChromeDefaultsTest` currently contains this test, which documents exactly the behaviour this task changes. **Replace it in full** — do not add a second test alongside it.

Existing (delete):

```java
   @Test
   void aChartTitleCarriesNoBorderColourAtAll() {
      // Documents an ordering fact the extraction must not disturb: the base seeds a title border
      // colour, then ChartVSAssemblyInfo replaces the whole TITLEPATH composite, discarding it.
      gateOn();
      VSCompositeFormat title = titleFormat(newChart());
      assertNotNull(title);
      assertNull(title.getDefaultFormat().getBorderColorsValue(),
                 "the chart's own title composite wins and sets no borders");
   }
```

Replacement:

```java
   @Test
   void aModernChartTitleCarriesTheBottomRule() {
      // The base seeds a title composite that ChartVSAssemblyInfo then replaces, so the chart
      // re-invokes the hook after its own install; without that the rule is written to a
      // composite that is thrown away.
      gateOn();
      VizContext ctx = VizContext.ofGate();
      VSFormat def = titleFormat(newChart()).getDefaultFormat();

      assertNull(def.getBackgroundValue(),
                 "the chart's title composite has never carried a fill, on either branch");

      Insets borders = def.getBordersValue();
      assertNotNull(borders, "the rule reaches the composite the chart actually keeps");
      assertEquals(StyleConstants.NONE, borders.top);
      assertEquals(StyleConstants.THIN_LINE, borders.bottom);
      assertEquals(VSTitleChromeDefaults.titleBorderColor(ctx),
                   def.getBorderColorsValue().bottomColor);
   }

   @Test
   void aLegacyChartTitleCarriesNoBorderAtAll() {
      gateOff();
      VSFormat def = titleFormat(newChart()).getDefaultFormat();
      assertNull(def.getBordersValue(), "a gate-off chart title has no rule");
      assertNull(def.getBackgroundValue(), "and no fill");
   }

   @Test
   void aModernizedChartEqualsAFreshlyCreatedOne() {
      // the equality the re-invocation must preserve: creation and Modernize run the same hook
      // against the same composite, so they cannot diverge
      gateOn();
      VizContext ctx = VizContext.ofGate();
      VSFormat fresh = titleFormat(newChart()).getDefaultFormat();

      gateOff();
      ChartVSAssemblyInfo modernized = newChart();
      gateOn();
      modernized.setVizMark(VizMark.fromGate());
      modernized.seedChromeDefaults(VizContext.of(modernized));
      VSFormat after = titleFormat(modernized).getDefaultFormat();

      assertEquals(fresh.getBordersValue(), after.getBordersValue());
      assertEquals(fresh.getBorderColorsValue().bottomColor,
                   after.getBorderColorsValue().bottomColor);
      assertEquals(VSTitleChromeDefaults.titleBorderColor(ctx),
                   after.getBorderColorsValue().bottomColor);
   }

   @Test
   void revertingAChartTitleRemovesTheRule() {
      gateOn();
      ChartVSAssemblyInfo info = newChart();
      info.setVizMark(VizMark.fromGate());
      info.seedChromeDefaults(VizContext.of(info));
      assertNotNull(titleFormat(info).getDefaultFormat().getBordersValue(),
                    "precondition: the modern chart has the rule");

      info.setVizMark(null);
      info.seedChromeDefaults(VizContext.of((VizMark) null));

      assertNull(titleFormat(info).getDefaultFormat().getBordersValue(),
                 "Revert restores a chart title with no rule");
   }
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./mvnw test -pl core -Dtest=SeedChromeDefaultsTest`
Expected: FAIL. `aModernChartTitleCarriesTheBottomRule` fails with a null `getBordersValue()`, and `aModernizedChartEqualsAFreshlyCreatedOne` fails on the same. `aLegacyChartTitleCarriesNoBorderAtAll` and `revertingAChartTitleRemovesTheRule`'s precondition also fail — all four are the same missing seed.

- [ ] **Step 3: Seed the chart's title in its hook override**

In `ChartVSAssemblyInfo.seedChromeDefaults`, insert after the `objFormat` block and before `PlotDescriptor plotDesc = ...`:

```java
      // the title lane's rule. No background write on either branch: this type's own title
      // composite has never carried one, so the modern lane is unfilled by construction and the
      // legacy branch has nothing to restore
      VSCompositeFormat titleFormat = getFormatInfo().getFormat(TITLEPATH);

      if(titleFormat != null) {
         VSFormat def = titleFormat.getDefaultFormat();
         def.setBordersValue(ctx.modern ? VSTitleChromeDefaults.titleRuleBorders() : null);
         def.setBorderColorsValue(ctx.modern ? VSTitleChromeDefaults.titleRuleColors(ctx) : null);
      }
```

Add `import inetsoft.uql.viewsheet.VSFormat;` if the file lacks it.

- [ ] **Step 4: Fix the creation ordering**

In `ChartVSAssemblyInfo.setDefaultFormat`, append after the existing `getFormatInfo().setFormat(TITLEPATH, tFormat);`:

```java
      // super seeded the title composite this method just replaced; re-run against the real one.
      // The hook is a set of unconditional writes, so running it twice changes nothing else
      seedChromeDefaults(VizContext.of(this));
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `./mvnw test -pl core -Dtest=SeedChromeDefaultsTest`
Expected: PASS, whole class. In particular `gateOffChartTakesNoPlotSeedsButKeepsTheUnconditionalOnes` and `gateOnChartTakesItsPlotSeeds` must still pass — they prove the second hook invocation did not disturb the plot seeds or the unconditional creation values.

- [ ] **Step 6: Run the neighbouring suites for regressions**

Run: `./mvnw test -pl core -Dtest=VizModernizeUtilTest`
Run: `./mvnw test -pl core -Dtest=BookmarkChromeResolutionTest`
Expected: PASS both. These exercise Modernize, Revert and restore against the chart, which the re-invocation touches.

- [ ] **Step 7: Commit**

```bash
git add core/src/main/java/inetsoft/uql/viewsheet/internal/ChartVSAssemblyInfo.java \
        core/src/test/java/inetsoft/uql/viewsheet/internal/SeedChromeDefaultsTest.java
git commit -m "feat(chart): seed the chart title lane's bottom rule

The chart replaces its whole title composite after calling super, so the
seed the base hook writes at creation is discarded. Re-invoking the hook
after that install is what lets a freshly created chart and a modernized
one resolve the same title chrome.

A chart's title composite has never carried a background, so the modern
lane is unfilled by construction and the legacy branch has nothing to
restore. Only the rule is added."
```

---

## Task 3: Stop the read-time fill double-applying — the browser is final here

**Files:**
- Modify: `core/src/main/java/inetsoft/uql/viewsheet/internal/VSTitleChromeDefaults.java:66-97`
- Modify: `core/src/main/java/inetsoft/web/viewsheet/model/chart/VSChartModel.java:59-60`
- Modify: `core/src/main/java/inetsoft/web/viewsheet/model/table/BaseTableModel.java:40-41`
- Test: `core/src/test/java/inetsoft/uql/viewsheet/internal/VSTitleChromeDefaultsTest.java`

**Interfaces:**
- Consumes: the seeds from Tasks 1 and 2.
- Produces: `VSTitleChromeDefaults.applyModernDefaults(VSCompositeFormat, VizContext, VSAssemblyInfo)` and `applyModernDefaultsInPlace(VSCompositeFormat, VizContext, VSAssemblyInfo)`. Task 4 calls both three-arg forms.

**Why this matters:** the substitution keys on the USER and CSS tiers. A seeded modern table has neither set, so `TITLE_BG` would land straight back on top of the fill Task 1 just cleared.

- [ ] **Step 1: Write the failing tests**

Add to `VSTitleChromeDefaultsTest`. It already has `withProperty` and `rgb` helpers — use them.

```java
   @Test
   void aSeededTypeIsNotSubstituted() {
      withProperty("viewsheet.modernVisualization", "true", () -> {
         VizContext ctx = VizContext.ofGate();
         VSCompositeFormat fmt = new VSCompositeFormat();
         ChartVSAssemblyInfo chart = new ChartVSAssemblyInfo();
         TableVSAssemblyInfo table = new TableVSAssemblyInfo();

         assertSame(fmt, VSTitleChromeDefaults.applyModernDefaults(fmt, ctx, chart),
                    "a chart carries its title chrome in the stored format");
         assertSame(fmt, VSTitleChromeDefaults.applyModernDefaults(fmt, ctx, table),
                    "so does a table");
      });
   }

   @Test
   void aDeferredTypeIsStillSubstituted() {
      withProperty("viewsheet.modernVisualization", "true", () -> {
         VizContext ctx = VizContext.ofGate();
         VSCompositeFormat fmt = new VSCompositeFormat();
         SelectionListVSAssemblyInfo list = new SelectionListVSAssemblyInfo();

         assertEquals(0xF1EFEA,
                      rgb(VSTitleChromeDefaults.applyModernDefaults(fmt, ctx, list).getBackground()),
                      "the selection and input family has not converted yet");
      });
   }

   @Test
   void aNullInfoKeepsTheOldBehaviour() {
      withProperty("viewsheet.modernVisualization", "true", () -> {
         VizContext ctx = VizContext.ofGate();
         VSCompositeFormat fmt = new VSCompositeFormat();

         assertEquals(0xF1EFEA, rgb(VSTitleChromeDefaults.applyModernDefaults(fmt, ctx).getBackground()),
                      "an unconverted call site keeps substituting");
      });
   }

   @Test
   void theInPlaceVariantSkipsASeededTypeToo() {
      withProperty("viewsheet.modernVisualization", "true", () -> {
         VizContext ctx = VizContext.ofGate();
         VSCompositeFormat fmt = new VSCompositeFormat();

         VSTitleChromeDefaults.applyModernDefaultsInPlace(fmt, ctx, new ChartVSAssemblyInfo());

         assertNull(fmt.getDefaultFormat().getBackgroundValue(),
                    "nothing is written onto a seeded type's format");
      });
   }
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./mvnw test -pl core -Dtest=VSTitleChromeDefaultsTest`
Expected: FAIL to compile — the three-arg overloads do not exist yet.

- [ ] **Step 3: Add the overloads and the predicate**

In `VSTitleChromeDefaults`, replace the signature line of `applyModernDefaults` and add a delegating two-arg form. The existing body is unchanged apart from the widened guard:

```java
   public static VSCompositeFormat applyModernDefaults(VSCompositeFormat titleFmt, VizContext ctx) {
      return applyModernDefaults(titleFmt, ctx, null);
   }

   public static VSCompositeFormat applyModernDefaults(VSCompositeFormat titleFmt, VizContext ctx,
                                                       VSAssemblyInfo info)
   {
      if(!ctx.modern || titleFmt == null || isSeededTitle(info)) {
         return titleFmt;
      }

      boolean bg = !isBackgroundCustomized(titleFmt);
      boolean fg = !isForegroundCustomized(titleFmt);

      if(!bg && !fg) {
         return titleFmt;
      }

      VSCompositeFormat clone = titleFmt.clone();
      applyTo(clone.getDefaultFormat(), bg, fg, ctx);
      return clone;
   }
```

Do the same to the in-place variant:

```java
   public static void applyModernDefaultsInPlace(VSCompositeFormat titleFmt, VizContext ctx) {
      applyModernDefaultsInPlace(titleFmt, ctx, null);
   }

   public static void applyModernDefaultsInPlace(VSCompositeFormat titleFmt, VizContext ctx,
                                                 VSAssemblyInfo info)
   {
      if(!ctx.modern || titleFmt == null || isSeededTitle(info)) {
         return;
      }

      applyTo(titleFmt.getDefaultFormat(),
              !isBackgroundCustomized(titleFmt), !isForegroundCustomized(titleFmt), ctx);
   }
```

Add the predicate next to the other private helpers:

```java
   // these types carry their title chrome in the stored format, written by seedChromeDefaults at
   // creation; the rest are still substituted here. When the last one converts this is true for
   // every titled type and both entry points, with all their call sites, can go
   private static boolean isSeededTitle(VSAssemblyInfo info) {
      return info instanceof ChartVSAssemblyInfo || info instanceof TableDataVSAssemblyInfo;
   }
```

Update the class javadoc's second paragraph, which describes the read-time mechanism as the whole story. Replace the sentence beginning *"Instead applyModernDefaults substitutes"* with:

```
    * Instead applyModernDefaults substitutes the modern neutral on the DEFAULT tier at read time,
    * for the types that have not yet converted to seeding their title chrome at creation. A
    * converted type is skipped here and carries its values in the stored format instead, so they
    * survive an asset export and resolve on bookmark restore.
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./mvnw test -pl core -Dtest=VSTitleChromeDefaultsTest`
Expected: PASS, whole class. The pre-existing `gateOnModernizesAnyDefaultButPreservesUser` and `aLegacyContextLeavesATitleFormatAlone` must still pass — they call the two-arg form, which now delegates with a null info.

- [ ] **Step 5: Convert the two browser call sites**

`VSChartModel.java:59-60` — change:

```java
      VSCompositeFormat compositeFormat = VSTitleChromeDefaults.applyModernDefaults(
         info.getFormatInfo().getFormat(titlePath, false), VizContext.of(info));
```

to:

```java
      VSCompositeFormat compositeFormat = VSTitleChromeDefaults.applyModernDefaults(
         info.getFormatInfo().getFormat(titlePath, false), VizContext.of(info), info);
```

`BaseTableModel.java:40-41` — change:

```java
      VSCompositeFormat compositeFormat = VSTitleChromeDefaults.applyModernDefaults(
         info.getFormatInfo().getFormat(titlePath, false), VizContext.of(info));
```

to:

```java
      VSCompositeFormat compositeFormat = VSTitleChromeDefaults.applyModernDefaults(
         info.getFormatInfo().getFormat(titlePath, false), VizContext.of(info), info);
```

- [ ] **Step 6: Pin what the browser actually receives**

The class test proves the seed; this proves it survives into the model the browser binds. Add to `core/src/test/java/inetsoft/web/viewsheet/model/VSFormatModelTest.java`:

```java
   @Test
   void aSeededTitleSerialisesTheBottomRuleOnly() {
      SreeEnv.setProperty("viewsheet.modernVisualization", "true");

      try {
         Viewsheet vs = new Viewsheet();
         TableVSAssembly table = new TableVSAssembly(vs, "Table1");
         table.getVSAssemblyInfo().initDefaultFormat();
         VSAssemblyInfo info = table.getVSAssemblyInfo();
         VSCompositeFormat title = info.getFormatInfo().getFormat(VSAssemblyInfo.TITLEPATH);

         VSFormatModel model = new VSFormatModel(title, info);

         assertEquals("1px solid #d9d5cc", model.getBorder().getBottom());
         assertTrue(model.getBorder().getTop().startsWith("0px"),
                    "a zero-width side draws nothing");
         assertNull(model.getBackground(), "the modern title lane serialises no fill");
      }
      finally {
         SreeEnv.setProperty("viewsheet.modernVisualization", null);
      }
   }
```

Add the imports the class lacks:

```java
import inetsoft.sree.SreeEnv;
import inetsoft.uql.viewsheet.TableVSAssembly;
import inetsoft.uql.viewsheet.VSCompositeFormat;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.VSAssemblyInfo;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
```

The colour string is lowercase because `Tool.colorToHTMLString:1505-1509` builds it with `Integer.toString(rgb, 16)`.

- [ ] **Step 7: Compile the module and run the four suites**

Run: `./mvnw test -pl core -Dtest='VSTitleChromeDefaultsTest,SeedChromeDefaultsTest,BookmarkChromeResolutionTest,VSFormatModelTest'`
Expected: PASS all four.

- [ ] **Step 8: Commit**

```bash
git add core/src/main/java/inetsoft/uql/viewsheet/internal/VSTitleChromeDefaults.java \
        core/src/main/java/inetsoft/web/viewsheet/model/chart/VSChartModel.java \
        core/src/main/java/inetsoft/web/viewsheet/model/table/BaseTableModel.java \
        core/src/test/java/inetsoft/uql/viewsheet/internal/VSTitleChromeDefaultsTest.java \
        core/src/test/java/inetsoft/web/viewsheet/model/VSFormatModelTest.java
git commit -m "feat(viewsheet): skip the read-time title fill for seeded types

The substitution keys on the user and css tiers, so a seeded title whose
fill was deliberately cleared has neither set and would be filled again
at every read. Both entry points now take the assembly info and return
early for a type that carries its title chrome in the stored format.

The two-argument forms delegate with a null info, so every call site not
yet converted keeps today's behaviour. When the last titled type
converts, the predicate is true for all of them and the substitution can
be deleted outright."
```

**CHECKPOINT — verify in a browser before continuing.** Build and run the app. A modern chart and a modern table must each show an unfilled title lane with a single hairline under the title, at all three densities. A selection list, a range slider and a calendar must be unchanged. Do not start Task 4 until this has been seen.

---

## Task 4: The seven remaining call sites

**Files:**
- Modify: `core/src/main/java/inetsoft/report/io/viewsheet/VSTableDataHelper.java:401-402`
- Modify: `core/src/main/java/inetsoft/report/io/viewsheet/AbstractVSExporter.java:1411-1412`, `:1803-1804`, `:1901-1902`
- Modify: `core/src/main/java/inetsoft/uql/viewsheet/internal/VsToReportConverter.java:1375-1377`, `:1516-1517`
- Modify: `core/src/main/java/inetsoft/web/composer/vs/controller/FormatPainterService.java:220`

**Interfaces:**
- Consumes: the three-arg overloads from Task 3.
- Produces: nothing new.

Three of these are umbrella calls reached by every titled type. They pass the info and let `isSeededTitle` decide — an umbrella caller cannot express the scoping itself.

- [ ] **Step 1: Convert `VSTableDataHelper:401`**

Change:

```java
      VSCompositeFormat format =
         VSTitleChromeDefaults.applyModernDefaults(
            finfo.getFormat(new TableDataPath(-1, TableDataPath.TITLE), false), VizContext.of(info));
```

to:

```java
      VSCompositeFormat format =
         VSTitleChromeDefaults.applyModernDefaults(
            finfo.getFormat(new TableDataPath(-1, TableDataPath.TITLE), false), VizContext.of(info),
            info);
```

- [ ] **Step 2: Convert the three `AbstractVSExporter` sites**

`:1411` — change `VizContext.of(info), ctx` usage to add the info. The call currently reads:

```java
                        VSCompositeFormat titleFmt = VSTitleChromeDefaults.applyModernDefaults(
                           info.getFormatInfo().getFormat(tpath, false), ctx);
```

to:

```java
                        VSCompositeFormat titleFmt = VSTitleChromeDefaults.applyModernDefaults(
                           info.getFormatInfo().getFormat(tpath, false), ctx, info);
```

`:1803` (the `prepareAssembly` umbrella) — change:

```java
         VSTitleChromeDefaults.applyModernDefaultsInPlace(
            vinfo.getFormatInfo().getFormat(VSAssemblyInfo.TITLEPATH), VizContext.of(vinfo));
```

to:

```java
         VSTitleChromeDefaults.applyModernDefaultsInPlace(
            vinfo.getFormatInfo().getFormat(VSAssemblyInfo.TITLEPATH), VizContext.of(vinfo), vinfo);
```

`:1901` (`getTextFormat`) — change:

```java
            chartTitleFormat = VSTitleChromeDefaults.applyModernDefaults(
               formatInfo.getFormat(tpath, false), ctx);
```

to:

```java
            chartTitleFormat = VSTitleChromeDefaults.applyModernDefaults(
               formatInfo.getFormat(tpath, false), ctx, info);
```

- [ ] **Step 3: Convert the two `VsToReportConverter` sites**

`:1375` (the umbrella) — change:

```java
         detailfmt = VSTitleChromeDefaults.applyModernDefaults(
            finfo.getFormat(new TableDataPath(-1, TableDataPath.TITLE), false),
            VizContext.of(assembly.getVSAssemblyInfo()));
```

to:

```java
         detailfmt = VSTitleChromeDefaults.applyModernDefaults(
            finfo.getFormat(new TableDataPath(-1, TableDataPath.TITLE), false),
            VizContext.of(assembly.getVSAssemblyInfo()), assembly.getVSAssemblyInfo());
```

`:1516` (print-layout chart title) — change:

```java
         VSTitleChromeDefaults.applyModernDefaultsInPlace(
            cinfo.getFormatInfo().getFormat(tpath), VizContext.of(assembly.getVSAssemblyInfo()));
```

to:

```java
         VSTitleChromeDefaults.applyModernDefaultsInPlace(
            cinfo.getFormatInfo().getFormat(tpath), VizContext.of(assembly.getVSAssemblyInfo()),
            assembly.getVSAssemblyInfo());
```

- [ ] **Step 4: Convert `FormatPainterService:220`**

Change:

```java
         VSTitleChromeDefaults.applyModernDefaultsInPlace(format, ctx);
```

to:

```java
         VSTitleChromeDefaults.applyModernDefaultsInPlace(format, ctx, info);
```

`info` is already in scope — `VizContext ctx = VizContext.of(info);` is the line above the enclosing `if`.

- [ ] **Step 5: Build the module and run the suites**

Run: `./mvnw clean install -pl core -am -DskipTests`
Expected: BUILD SUCCESS. Use `clean` — an incremental build has previously reported SUCCESS while leaving stale callers of a changed signature in a sibling module.

Run: `./mvnw test -pl core -Dtest='VSTitleChromeDefaultsTest,SeedChromeDefaultsTest,BookmarkChromeResolutionTest,VizModernizeUtilTest'`
Expected: PASS all four.

- [ ] **Step 6: Commit**

```bash
git add core/src/main/java/inetsoft/report/io/viewsheet/VSTableDataHelper.java \
        core/src/main/java/inetsoft/report/io/viewsheet/AbstractVSExporter.java \
        core/src/main/java/inetsoft/uql/viewsheet/internal/VsToReportConverter.java \
        core/src/main/java/inetsoft/web/composer/vs/controller/FormatPainterService.java
git commit -m "feat(viewsheet): pass the assembly to the title chrome resolver

Seven export, print-layout and composer call sites now hand the resolver
the assembly whose title they are resolving, so a seeded type is skipped
there the way it already is in the browser model.

Three of them are reached by every titled type, which is why the scoping
lives in the resolver rather than at the call sites: an umbrella caller
has no way to express it."
```

---

## Task 5: Carry the border onto the synthetic export title

> **RESOLVED TO NO CHANGE during execution.** This task's premise was wrong. The block was written,
> traced and reverted: nothing draws from the synthetic title assembly's object format, because every
> exporter's `writeText` resolves through `getTextFormat(info)`, which takes the CSS-type-`Chart`
> branch and reads the chart's TITLEPATH directly. The seeded border already reaches chart export
> with no change. See the spec's §4, corrected. Do not re-execute this task.

**Files:**
- Modify: `core/src/main/java/inetsoft/report/io/viewsheet/AbstractVSExporter.java:1416-1429`

**Interfaces:**
- Consumes: a modern chart's title format having a non-null `getBorders()`, from Task 2.
- Produces: nothing new.

**Why this exists:** a chart's export title is drawn as a synthetic `TextVSAssembly` whose *object* format receives the resolved title chrome one property at a time — background at `:1418`, foreground at `:1423`, nothing else. Without a third block, the rule reaches the browser and every table export but not a chart's PDF, SVG, PNG or Excel.

- [ ] **Step 1: Check what the box draws today, before changing it**

The synthetic assembly's `FormatInfo` is the chart's own (`:1402`), so its OBJECTPATH is the chart's object format and carries the card's four-side border. Export one modern chart to PDF and note whether a box is drawn around the title. Record the answer in the commit message — the new block *replaces* those borders, so this determines whether the change also removes an existing artifact or merely adds the rule.

- [ ] **Step 2: Add the border copy**

Inside the existing `if(ctx.modern) { ... }` block, after the foreground copy and before `titleFmtInfo.setFormat(VSAssemblyInfo.OBJECTPATH, objFmt);`:

```java
                        if(titleFmt != null && titleFmt.getBorders() != null) {
                           objFmt.getDefaultFormat().setBordersValue(titleFmt.getBorders());
                           objFmt.getDefaultFormat().setBorderColorsValue(
                              titleFmt.getBorderColors());
                        }
```

**Keep it inside the gate.** The values are now stored for both gate states, but ungating this would push a legacy chart's title box from the card border to no border at all — a change to gate-off output.

- [ ] **Step 3: Verify by export**

Build and run the app. Export a viewsheet holding one modern chart and one modern table to PDF, then to PNG, then to Excel.
Expected: in all three, the chart's title lane shows a single hairline beneath the title, inset to the card padding, in the same grey as the card border; the table's shows the same hairline running the full object width. Neither shows a filled band.

Export the same viewsheet with `viewsheet.modernVisualization` off.
Expected: identical to a build without this change — a filled `#f5f5f5` table title with its four-side box, and a chart title with no rule.

- [ ] **Step 4: Commit**

```bash
git add core/src/main/java/inetsoft/report/io/viewsheet/AbstractVSExporter.java
git commit -m "fix(export): carry the chart title's border onto the export title box

A chart's export title is a synthetic text assembly whose object format
receives the resolved title chrome one property at a time, and the copy
stopped at foreground. The rule therefore reached the browser and every
table export but no chart export.

The copy stays inside the gate even though the values are now stored for
both states: the synthetic assembly's format info is the chart's own, so
its object format carries the card border, and copying unconditionally
would take a legacy chart's title box from that border to none."
```

---

## Task 6: Prove the seed resolves on bookmark restore

**Files:**
- Test: `core/src/test/java/inetsoft/uql/viewsheet/BookmarkChromeResolutionTest.java`

**Interfaces:**
- Consumes: the seeds from Tasks 1 and 2.
- Produces: nothing.

**Why this exists as its own task:** the whole case for seeding over read-time substitution rests on `AbstractVSAssembly.parseState` calling `VizModernizeUtil.reseedAfterRestore`, which re-runs the hook against the live mark. This should pass with **no production change**. The test exists to prove that claim rather than to drive one — if it fails, the seeding decision needs revisiting, not the test.

- [ ] **Step 1: Write the test**

Add to `BookmarkChromeResolutionTest`. **This class is in package `inetsoft.uql.viewsheet`, not `.internal`, so `seedChromeDefaults` is not visible from it** — that is why the existing tests go through `modernizeViaGate(vs)` and `VizModernizeUtil.revert(vs)` instead. Use the class's existing `modernizeViaGate(Viewsheet)`, `writeState(VSAssembly)` and `parseXml(String)` helpers; there is no table helper, so build the table inline the way `newChart` builds its chart.

```java
   @Test
   void aStaleBookmarkDoesNotUnRevertTheTitleLane() throws Exception {
      // the title chrome rides in state_tableformat, which parseStateContent installs wholesale;
      // only the re-seed that follows parseState resolves it against the live mark
      Viewsheet vs = new Viewsheet();
      TableVSAssembly table = new TableVSAssembly(vs, "Table1");
      table.getVSAssemblyInfo().initDefaultFormat();
      vs.addAssembly(table);
      modernizeViaGate(vs);

      String state = writeState(table);
      assertTrue(state.contains("<state_tableformat>"),
                 "precondition: the bookmark actually carries the title format");

      VizModernizeUtil.revert(vs);
      table.parseState(parseXml(state));

      VSFormat def = table.getVSAssemblyInfo().getFormatInfo()
         .getFormat(VSAssemblyInfo.TITLEPATH).getDefaultFormat();
      assertEquals(VSAssemblyInfo.DEFAULT_TITLE_BG, def.getBackgroundValue(),
                   "the restored title lane is filled again, as a classic table's is");
      assertEquals(StyleConstants.THIN_LINE, def.getBordersValue().top,
                   "and carries the four-side box, not the modern rule");
   }
```

Add one import; the class already has `inetsoft.uql.viewsheet.internal.*` and is in the package that makes `VSFormat` and `TableVSAssembly` unqualified:

```java
import inetsoft.report.StyleConstants;
```

- [ ] **Step 2: Run the test**

Run: `./mvnw test -pl core -Dtest=BookmarkChromeResolutionTest`
Expected: PASS with no production change. If it FAILS, stop and report — the seeding decision assumed this path already covers any value the hook writes, and a failure means that assumption is wrong.

- [ ] **Step 3: Commit**

```bash
git add core/src/test/java/inetsoft/uql/viewsheet/BookmarkChromeResolutionTest.java
git commit -m "test(viewsheet): pin title lane resolution on bookmark restore

The case for seeding the title chrome rather than substituting it at
render rests on the re-seed that runs after parseState, which resolves
any hook-written value against the live mark. This proves that covers
the title lane too, and passes with no production change."
```

---

## Final Verification

- [ ] **Full core suite:** `./mvnw clean install -pl core -am` — no new failures against the branch baseline.
- [ ] **Manual matrix:** chart, table, crosstab, calc table × dense / compact / comfortable, in the viewer and in PDF, PNG, Excel and print layout. Each shows an unfilled lane with one hairline.
- [ ] **Author's format survives:** set a title background on a chart through the composer's format pane; it stays, and the pane shows what the canvas draws.
- [ ] **Deferred types unmoved:** selection list, selection tree, selection container, range slider, checkbox, radio button, calendar — all identical to before this branch's changes.
- [ ] **Gate off is byte-identical:** the whole matrix with `viewsheet.modernVisualization` unset, compared against a build from before this work.
- [ ] **The portability case, which is why this is seeded:** export a viewsheet holding one modern chart and one modern table to a deployment JAR; import it into a second server; confirm both title lanes arrive unfilled with their rule.
- [ ] **Show the sibling project.** This breaks the title-bar / table-header equality their §05 endorses, on all three table types. Raise it before merging.
