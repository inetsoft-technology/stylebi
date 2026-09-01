# Seeded Chrome Migration, Group 1 — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move four chrome values from read-time substitution to creation-time seeding, so each travels in an exported asset instead of being recomputed at every render.

**Architecture:** Each conversion writes its value into the stored `DEFAULT` tier from an assembly's `seedChromeDefaults` override — on both the modern and the legacy branch, because `VizModernizeUtil.revert` calls that hook and nothing else. The read-time resolver methods are then deleted, along with their call sites. No change to `bypassesBaseChrome()`: its guard sits inside the base implementation, so a subclass override runs regardless.

**Tech Stack:** Java 21, Maven (`./mvnw`), JUnit 5 + Spring test context. All production code is in `core/`, except three call sites in `utils/inetsoft-xml-formats`.

**Spec:** `docs/superpowers/specs/lookfeel/2026-09-01-seeded-chrome-migration-group1-design.md`

## Global Constraints

- Branch: `viz-updates` in the `community` submodule. Do not commit until the parallel calendar/selection-container session has finished; the working tree is shared.
- A seed writes the `DEFAULT` tier only. A USER-tier or CSS-tier value must always outrank it — never test for one, never clear one.
- Both branches of every seed write. The `!ctx.modern` branch is the Revert contract: if it does not write the legacy value, Revert strands the modern one.
- An unmarked assembly must stay **bit-for-bit** unchanged in the stored asset. When a legacy creation wrote nothing, the legacy branch writes nothing.
- Do not seed anything derived from `VSDensityDefaults.mode()`. It reads the live org property `viewsheet.density`; seeding a density-derived value freezes it at creation.
- `viewsheet.modernVisualization` now defaults to **true** (`c7790bbf0`). A test that wants legacy must set the property to `"false"` explicitly — unset no longer means legacy.
- Test commands assume Git Bash at the `community/` root. On PowerShell use `.\mvnw.cmd` in place of `./mvnw`.

---

## File Structure

**Modified — the seeds (`core/src/main/java/inetsoft/uql/viewsheet/internal/`)**

| File | Responsibility after this plan |
|---|---|
| `ChartVSAssemblyInfo.java` | Seeds the card inset in its existing `seedChromeDefaults` override; loses its `getPadding()` override |
| `SelectionBaseVSAssemblyInfo.java` | Seeds the detail cell's foreground in its existing override |
| `SliderVSAssemblyInfo.java` | **New** `seedChromeDefaults` override seeding the object foreground |
| `TextVSAssemblyInfo.java` | **New** `seedChromeDefaults` override seeding the value emphasis, plus a creation-time invoke |
| `VSObjectChromeDefaults.java` | Palette supplier only: gains `modernChartPadding()`, `legacyCellForegroundValue()`, a public `darkForegroundValue()`; loses `chartPadding`, `applyDarkForeground`, `applyDarkForegroundInPlace`, `textForegroundCss` |
| `VSOutputChromeDefaults.java` | Palette supplier only: loses `applyModernDefaults`, `applyModernDefaultsInPlace`; `valueForeground` / `valueBorderColor` gain their first production caller |

**Modified — the call sites**

| File | Change |
|---|---|
| `web/composer/vs/dialog/ChartPropertyDialogService.java` | "Follows default" re-runs the hook instead of storing the legacy inset |
| `web/viewsheet/model/SelectionListModel.java` | Drops the cell-foreground substitution |
| `web/viewsheet/model/VSSliderModel.java` | Drops the whole dark-foreground block |
| `web/viewsheet/model/VSTextModel.java` | `createFormatModel` drops the substitution, keeps `scaleFont` |
| `web/composer/vs/controller/FormatPainterService.java` | Drops the text and selection-cell branches, and `isPlainSelectionCell` |
| `report/io/viewsheet/VSSelectionListHelper.java` | `getValueFormat` loses its `VizContext` parameter and the substitution |
| `report/io/viewsheet/VSSelectionTreeHelper.java`, `pdf/PDFSelectionListHelper.java`, `svg/SVGSelectionListHelper.java` | Follow the narrowed signature |
| `report/io/viewsheet/AbstractVSExporter.java` | Drops the text substitution at `:1914` |
| `report/gui/viewsheet/VSSlider.java` | Reads the format directly |
| `report/io/viewsheet/html/HTMLSelectionListHelper.java`, `html/HTMLSelectionTreeHelper.java` | Read the format directly |
| `utils/inetsoft-xml-formats/.../excel/ExcelSelectionListHelper.java`, `excel/ExcelSelectionTreeHelper.java` | Substitute the legacy ink back — the inverted opt-out |
| `utils/inetsoft-xml-formats/.../ppt/PPTSelectionListHelper.java` | Follows the narrowed signature |

**Modified — tests**

Modified: `core/src/test/java/inetsoft/uql/viewsheet/internal/SeedChromeDefaultsTest.java`,
`VSObjectChromeDefaultsTest.java`, `VSOutputChromeDefaultsTest.java`,
`core/src/test/java/inetsoft/web/composer/vs/dialog/PaddingFollowDefaultTest.java`.

Created: `core/src/test/java/inetsoft/uql/viewsheet/internal/ChartInsetCssOverrideTest.java` — the CSS
guard needs a live `format.css` fixture, which `SeedChromeDefaultsTest` has no teardown for.

---

### Task 1: The chart's card inset

**Files:**
- Modify: `core/src/main/java/inetsoft/uql/viewsheet/internal/ChartVSAssemblyInfo.java` (add to `seedChromeDefaults` at `:105`; delete `getPadding()` at `:2874-2876`)
- Modify: `core/src/main/java/inetsoft/uql/viewsheet/internal/VSObjectChromeDefaults.java` (add `modernChartPadding()`; delete `chartPadding` at `:73-83`)
- Modify: `core/src/main/java/inetsoft/web/composer/vs/dialog/ChartPropertyDialogService.java:405-408`
- Test: `core/src/test/java/inetsoft/uql/viewsheet/internal/SeedChromeDefaultsTest.java`
- Test: `core/src/test/java/inetsoft/web/composer/vs/dialog/PaddingFollowDefaultTest.java`

**Interfaces:**
- Consumes: nothing from earlier tasks.
- Produces: `VSObjectChromeDefaults.modernChartPadding()` returning a fresh `java.awt.Insets` of 12 on all four edges. Later tasks do not use it.

**Context an implementer needs.** `ChartVSAssemblyInfo.setDefaultFormat` (`:86`) calls `setPadding(new Insets(10,10,10,10))` **first**, then `super.setDefaultFormat(...)` — which runs `setCSSDefaults()` and then `seedChromeDefaults` — and then re-invokes `seedChromeDefaults` after replacing the title composite. So the seed runs twice and must be idempotent, and by the time it runs a CSS padding has already overwritten the 10s. `isUserPadding()` (`:2826`) is the authorship record and is already serialized.

- [ ] **Step 1: Write the failing tests**

Add to `SeedChromeDefaultsTest`, after the existing chart tests. Two new imports are needed, `java.io.PrintWriter` and `java.io.StringWriter`:

```java
   // ---- the card inset, seeded rather than resolved ------------------------------------------

   @Test
   void aModernChartSeedsTheCardInset() {
      gateOn();
      assertEquals(new Insets(12, 12, 12, 12), newChart().getPadding(),
                   "the card inset is stored at creation, not resolved at read time");
   }

   @Test
   void aLegacyChartSeedsTheLegacyInset() {
      gateOff();
      assertEquals(new Insets(10, 10, 10, 10), newChart().getPadding());
   }

   @Test
   void revertingAChartRestoresTheLegacyInset() {
      gateOn();
      ChartVSAssemblyInfo info = newChart();
      assertEquals(new Insets(12, 12, 12, 12), info.getPadding());

      info.setVizMark(null);
      info.seedChromeDefaults(VizContext.of((VizMark) null));

      assertEquals(new Insets(10, 10, 10, 10), info.getPadding(),
                   "the legacy branch writes the inset back; nothing else would");
   }

   @Test
   void aModernChartsInsetTravelsInTheAsset() {
      // THE defect this conversion closes, and the only assertion here that fails before it:
      // writeAttributes serializes the padding FIELD, not getPadding(), so a resolved-only value
      // is absent from an exported asset and an older build renders the card mixed
      gateOn();
      ChartVSAssemblyInfo info = newChart();
      StringWriter buf = new StringWriter();
      PrintWriter writer = new PrintWriter(buf);

      info.writeXML(writer);
      writer.flush();

      assertTrue(buf.toString().contains("paddingTop=\"12\""),
                 "the card inset has to be in the serialized asset, not only in the getter");
   }

   @Test
   void anAuthorsInsetSurvivesModernize() {
      gateOff();
      ChartVSAssemblyInfo info = newChart();
      info.setUserPadding(true);
      info.setPadding(new Insets(3, 3, 3, 3));

      gateOn();
      info.seedChromeDefaults(VizContext.ofGate());

      assertEquals(new Insets(3, 3, 3, 3), info.getPadding(),
                   "isUserPadding is the authorship record and the seed must not overwrite it");
   }
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./mvnw test -pl core -Dtest=SeedChromeDefaultsTest`
Expected: **only `aModernChartsInsetTravelsInTheAsset` FAILS**, showing `paddingTop="10"` in the serialized output.

The other four PASS already, and knowing why matters before you touch anything. They read through `getPadding()`, which today resolves 12 from the stored 10 via the resolver step 5 deletes — so seeding and resolving are indistinguishable through that getter. That is the point of the conversion: the value's absence from the **asset** is the defect, so the asset assertion is the only one that can fail first. The other four become real discriminators the moment step 5 deletes the override: without a correct seed they would then return 10, and they are what catches a seed that writes the wrong branch. Do not "fix" them into failing now.

- [ ] **Step 3: Add the modern supplier**

In `VSObjectChromeDefaults.java`, beside the existing `legacyChartPadding()`:

```java
   /**
    * The modern card inset, seeded at creation. A fresh object every call: Insets is mutable and the
    * constant must not escape by reference.
    */
   public static Insets modernChartPadding() {
      return new Insets(MODERN_CARD_INSET, MODERN_CARD_INSET, MODERN_CARD_INSET,
                        MODERN_CARD_INSET);
   }
```

- [ ] **Step 4: Seed the inset**

In `ChartVSAssemblyInfo.seedChromeDefaults`, after the existing `objFormat` background write and before the title-lane block:

```java
      // the card inset. Seeded rather than resolved at read time so it travels in an exported
      // asset. isUserPadding is the author's opinion, and setCSSDefaults installs a CSS padding
      // just before this hook runs, so both are left alone
      if(!isUserPadding() && !isCssPaddingDefined()) {
         setPadding(ctx.modern ? VSObjectChromeDefaults.modernChartPadding()
                       : VSObjectChromeDefaults.legacyChartPadding());
      }
```

And add the private helper beside it:

```java
   /**
    * Whether a format.css class set this chart's padding. setCSSDefaults writes it before the seed
    * runs, and there is no tier to record it in, so the dictionary is asked directly.
    */
   private boolean isCssPaddingDefined() {
      VSCompositeFormat objFormat = getFormat();

      if(objFormat == null) {
         return false;
      }

      return CSSDictionary.getDictionary()
         .isPaddingDefined(objFormat.getCSSFormat().getCSSParam());
   }
```

`CSSDictionary` is already imported in this file (the base hook uses `CSSDictionary.getDictionary()`); if the import is missing, add `inetsoft.util.css.CSSDictionary`.

- [ ] **Step 5: Delete the resolver and the getter override**

Delete `VSObjectChromeDefaults.chartPadding` (`:73-83`) entirely, and delete `ChartVSAssemblyInfo.getPadding()` (`:2874-2876`) so the inherited getter returns the field. Leave `legacyChartPadding()` — the dialog still uses it. Leave `MODERN_CARD_INSET` and `LEGACY_CHART_PADDING`; both suppliers read them.

- [ ] **Step 6: Run the tests to verify they pass**

Run: `./mvnw test -pl core -Dtest=SeedChromeDefaultsTest`
Expected: the four new tests PASS. **Two existing tests in this class now fail or mislead — fix them in step 7 before moving on.**

- [ ] **Step 7: Update the two existing assertions the conversion invalidates**

`gateOffChartTakesNoPlotSeedsButKeepsTheUnconditionalOnes` still passes (gate-off seeds 10), but its comment is now false. Replace:

```java
      assertEquals(10, info.getPadding().top,
                   "chart padding is unconditional and must stay in setDefaultFormat");
```

with:

```java
      assertEquals(10, info.getPadding().top,
                   "the inset is a gate-dependent seed now, and the legacy branch writes 10");
```

`theHookModernizesALegacyChartCompletely` **fails**: it sets `Insets(3,3,3,3)` without `setUserPadding(true)`, and a plain `setPadding` is not an authorship record, so Modernize now writes the card inset over it. This is intended — the old exact-equality test protected any non-legacy value, and the flag supersedes it. Replace:

```java
      assertEquals(new Insets(3, 3, 3, 3), info.getPadding(),
                   "padding is not a gate-dependent seed and must not be re-applied");
```

with:

```java
      assertEquals(new Insets(12, 12, 12, 12), info.getPadding(),
                   "an unflagged padding is not an author opinion, so Modernize seeds over it; " +
                   "anAuthorsInsetSurvivesModernize covers the flagged case");
```

- [ ] **Step 8: Run the class again**

Run: `./mvnw test -pl core -Dtest=SeedChromeDefaultsTest`
Expected: PASS, all 60 tests.

- [ ] **Step 9: Change the dialog's follow-default write**

In `ChartPropertyDialogService.java`, the `else if(paddingFollowsDefault)` branch at `:405-408` currently stores the legacy inset and relies on the deleted resolver. Replace its body:

```java
      else if(paddingFollowsDefault) {
         // clear the opinion and let the seed decide, the same shape Revert uses. Storing the
         // legacy inset here would pin 10 now that the value is seeded rather than resolved
         assemblyInfo.setUserPadding(false);
         assemblyInfo.seedChromeDefaults(VizContext.of(assemblyInfo));
      }
```

**This requires widening the hook, and the widening is not optional.** `seedChromeDefaults` is `protected` at `VSAssemblyInfo:1248`. `VizModernizeUtil` can call it because it sits in the same package, `inetsoft.uql.viewsheet.internal`; `ChartPropertyDialogService` is in `inetsoft.web.composer.vs.dialog` and cannot. Change the declaration to `public` and extend its Javadoc with one line:

```java
   * Called once at creation, again by Modernize and Revert, and again by the padding pane when an
   * author hands the inset back to the default — hence public rather than protected.
```

- [ ] **Step 10: Update `PaddingFollowDefaultTest`**

Five of its tests build a chart with `new ChartVSAssemblyInfo()` (no viewsheet, so no creation stamp), call `initDefaultFormat()`, and only then `setVizMark(MODERN_LIGHT)` — so the seed ran unmarked and stored 10. They relied on read-time resolution. Add a helper and use it in every test that marks a chart:

```java
   /** A chart marked and seeded the way creation or Modernize leaves one. */
   private static ChartVSAssemblyInfo markedChart() {
      ChartVSAssemblyInfo info = new ChartVSAssemblyInfo();
      info.initDefaultFormat();
      info.setVizMark(VizMark.MODERN_LIGHT);
      info.seedChromeDefaults(VizContext.of(info));
      return info;
   }
```

Replace the three-line create-and-mark preamble with `ChartVSAssemblyInfo info = markedChart();` in `readSendsTheResolvedInsetAndTheFlag`, `anUnchangedApplyDoesNotStampTheResolvedValue`, `clearingTheCheckboxPinsWhatThePaneShows` and `tickingTheCheckboxRestoresTheLegacyStoredInsetAndResolvesAgain`. In `theFlagSurvivesTheDialogsMergeOntoTheLiveAssembly`, add `live.seedChromeDefaults(VizContext.of(live));` after its `setVizMark`. Leave `readSendsNoFlagForAnUnmarkedChart` and `anUnmarkedChartStillAcceptsAnEditedInset` alone — they never mark.

Then update the `apply` helper, which mirrors the service and must keep mirroring it:

```java
      else if(followsDefault) {
         info.setUserPadding(false);
         info.seedChromeDefaults(VizContext.of(info));
      }
```

Rename `tickingTheCheckboxRestoresTheLegacyStoredInsetAndResolvesAgain` to `tickingTheCheckboxReseedsTheCardInset` — it no longer restores a legacy stored inset.

- [ ] **Step 11: Run the dialog test**

Run: `./mvnw test -pl core -Dtest=PaddingFollowDefaultTest`
Expected: PASS, 7 tests.

- [ ] **Step 12: Prove the CSS guard, in its own class**

`SeedChromeDefaultsTest` has no CSS fixture machinery and its teardown only resets properties, so this goes in a new class modelled on `VSChartPaletteCssOverrideTest` — which is the precedent for a live `format.css` in a test. Create `core/src/test/java/inetsoft/uql/viewsheet/internal/ChartInsetCssOverrideTest.java`:

```java
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class ChartInsetCssOverrideTest {
   @AfterEach
   void cleanup() throws Exception {
      DataSpace space = DataSpace.getDataSpace();

      if(space.exists("portal", "format.css")) {
         space.delete("portal", "format.css");
      }

      SreeEnv.setProperty("viewsheet.modernVisualization", null);
      CSSDictionary.resetDictionaryCache();
   }

   @Test
   void aCssPaddingSurvivesTheCardInsetSeed() throws Exception {
      writeFormatCss("Chart { padding: 5px; }");
      CSSDictionary.resetDictionaryCache();
      SreeEnv.setProperty("viewsheet.modernVisualization", "true");

      Viewsheet vs = new Viewsheet();
      ChartVSAssembly chart = new ChartVSAssembly(vs, "Chart1");
      chart.getVSAssemblyInfo().initDefaultFormat();

      assertEquals(new Insets(5, 5, 5, 5),
                   chart.getVSAssemblyInfo().getPadding(),
                   "setCSSDefaults installs the CSS padding just before the seed runs, and the " +
                   "seed must leave it alone; the deleted equality test was providing this");
   }

   private void writeFormatCss(String content) throws IOException {
      DataSpace space = DataSpace.getDataSpace();

      space.withOutputStream("portal", "format.css",
                             out -> out.write(content.getBytes(StandardCharsets.UTF_8)));
   }
}
```

Copy the exact `writeFormatCss` body and any `forceReload()` helper from `VSChartPaletteCssOverrideTest:126-140` rather than the sketch above if the two disagree — that class is the working reference for this fixture.

Run: `./mvnw test -pl core -Dtest=ChartInsetCssOverrideTest`
Expected: PASS. If it fails with 12s, the guard is not being consulted; if it fails with 10s, `setCSSDefaults` is running after the seed rather than before, and the ordering claim in the spec is wrong.

- [ ] **Step 13: Commit**

```bash
git add core/src/main/java/inetsoft/uql/viewsheet/internal/ChartVSAssemblyInfo.java core/src/main/java/inetsoft/uql/viewsheet/internal/VSObjectChromeDefaults.java core/src/main/java/inetsoft/uql/viewsheet/internal/VSAssemblyInfo.java core/src/main/java/inetsoft/web/composer/vs/dialog/ChartPropertyDialogService.java core/src/test/java/inetsoft/uql/viewsheet/internal/SeedChromeDefaultsTest.java core/src/test/java/inetsoft/web/composer/vs/dialog/PaddingFollowDefaultTest.java core/src/test/java/inetsoft/uql/viewsheet/internal/ChartInsetCssOverrideTest.java
git commit -m "feat(viewsheet): seed the chart's card inset at creation"
```

---

### Task 2: The selection cell's foreground

**Files:**
- Modify: `core/src/main/java/inetsoft/uql/viewsheet/internal/SelectionBaseVSAssemblyInfo.java` (add to `seedChromeDefaults` at `:932`)
- Modify: `core/src/main/java/inetsoft/uql/viewsheet/internal/VSObjectChromeDefaults.java` (add `legacyCellForegroundValue()`, make `darkForegroundValue()` public)
- Modify: `core/src/main/java/inetsoft/report/io/viewsheet/VSSelectionListHelper.java:310-318`
- Modify: `core/src/main/java/inetsoft/report/io/viewsheet/VSSelectionTreeHelper.java:187`, `pdf/PDFSelectionListHelper.java:164`, `svg/SVGSelectionListHelper.java:158`
- Modify: `core/src/main/java/inetsoft/report/io/viewsheet/html/HTMLSelectionListHelper.java:183`, `html/HTMLSelectionTreeHelper.java:129`
- Modify: `core/src/main/java/inetsoft/web/viewsheet/model/SelectionListModel.java:71-85`
- Modify: `core/src/main/java/inetsoft/web/composer/vs/controller/FormatPainterService.java:226-231`, and delete `isPlainSelectionCell` at `:942-945`
- Modify: `utils/inetsoft-xml-formats/src/main/java/inetsoft/report/io/viewsheet/excel/ExcelSelectionListHelper.java:255-261`
- Modify: `utils/inetsoft-xml-formats/src/main/java/inetsoft/report/io/viewsheet/excel/ExcelSelectionTreeHelper.java:227-232`
- Modify: `utils/inetsoft-xml-formats/src/main/java/inetsoft/report/io/viewsheet/ppt/PPTSelectionListHelper.java:147`
- Test: `core/src/test/java/inetsoft/uql/viewsheet/internal/SeedChromeDefaultsTest.java`

**Interfaces:**
- Consumes: nothing from Task 1.
- Produces: `VSObjectChromeDefaults.darkForegroundValue()` and `VSObjectChromeDefaults.legacyCellForegroundValue()`, both `public static String` returning a `"0xrrggbb"` value string. Task 3 uses `darkForegroundValue()`.
- Produces: `VSSelectionListHelper.getValueFormat(SelectionValue value, VSCompositeFormat format, boolean hasSelected)` — the `VizContext` parameter is gone.

**Context an implementer needs.** `SelectionBaseVSAssemblyInfo.setDefaultFormat:883` writes `"0x2b2b2b"` onto the DETAIL composite as an unconditional creation default. That literal **stays**; the seed overwrites it afterwards, exactly as the title seed relates to the borders written at `:894-897`. The measure-bar composites live on their own paths (`getMeasureBarPath(i)`, `getMeasureNBarPath(i)`), so seeding the plain DETAIL composite cannot reach them — which is why `isPlainSelectionCell` is deleted rather than reimplemented. `getValueFormat`'s dimming writes the USER tier and keeps winning by tier precedence.

**The Excel inversion is the load-bearing part of this task.** Both Excel helpers keep the legacy near-black deliberately by passing a legacy `VizContext`. A seeded value is in the stored format, so that no longer works, and `ExcelSelectionTreeHelper:227` reads `sv.getFormat()` uncloned. Excel must now substitute the legacy value back onto a clone of its own. PPT keeps the modern ink; it paints the viewsheet background.

- [ ] **Step 1: Write the failing tests**

Add to `SeedChromeDefaultsTest`:

```java
   // ---- the selection cell's foreground, seeded rather than substituted -----------------------

   private static VSFormat cellDefault(VSAssemblyInfo info) {
      return info.getFormatInfo()
         .getFormat(new TableDataPath(-1, TableDataPath.DETAIL)).getDefaultFormat();
   }

   @Test
   void aDarkSelectionListSeedsTheLightCellForeground() {
      gateOn();
      SreeEnv.setProperty("viewsheet.darkMode", "true");
      assertEquals(VSObjectChromeDefaults.darkForegroundValue(),
                   cellDefault(newSelectionList()).getForegroundValue(),
                   "the light neutral is stored, not substituted at every render");
   }

   @Test
   void aLightSelectionListSeedsTheLegacyCellForeground() {
      gateOn();
      assertEquals(VSObjectChromeDefaults.legacyCellForegroundValue(),
                   cellDefault(newSelectionList()).getForegroundValue(),
                   "only dark moves the cell ink; light modern keeps the near-black");
   }

   @Test
   void aDarkSelectionTreeSeedsTheLightCellForeground() {
      gateOn();
      SreeEnv.setProperty("viewsheet.darkMode", "true");
      assertEquals(VSObjectChromeDefaults.darkForegroundValue(),
                   cellDefault(newSelectionTree()).getForegroundValue(),
                   "one seed on the shared base covers list and tree both");
   }

   @Test
   void revertingADarkSelectionListRestoresTheLegacyCellForeground() {
      gateOn();
      SreeEnv.setProperty("viewsheet.darkMode", "true");
      SelectionListVSAssemblyInfo info = newSelectionList();

      info.setVizMark(null);
      info.seedChromeDefaults(VizContext.of((VizMark) null));

      assertEquals(VSObjectChromeDefaults.legacyCellForegroundValue(),
                   cellDefault(info).getForegroundValue());
   }

   @Test
   void aMeasureBarKeepsItsOwnForegroundWhenTheCellIsSeeded() {
      // the bar's foreground IS the bar colour; the seed must not reach it. Structural now: the
      // measure composites are separate paths, which is why the old predicate could be deleted
      gateOn();
      SreeEnv.setProperty("viewsheet.darkMode", "true");
      SelectionListVSAssemblyInfo info = newSelectionList();
      VSCompositeFormat bar = info.getFormatInfo().getFormat(info.getMeasureBarPath(0));

      assertNotEquals(VSObjectChromeDefaults.darkForegroundValue(),
                      bar.getDefaultFormat().getForegroundValue());
   }
```

Add two more, covering the properties the deleted guards used to provide:

```java
   @Test
   void modernizingAnUnmarkedDarkSelectionListSeedsTheLightForeground() {
      gateOff();
      SelectionListVSAssemblyInfo info = newSelectionList();
      assertEquals(VSObjectChromeDefaults.legacyCellForegroundValue(),
                   cellDefault(info).getForegroundValue());

      gateOn();
      SreeEnv.setProperty("viewsheet.darkMode", "true");
      info.seedChromeDefaults(VizContext.ofGate());

      assertEquals(VSObjectChromeDefaults.darkForegroundValue(),
                   cellDefault(info).getForegroundValue());
   }

   @Test
   void anAuthorsCellForegroundOutranksTheSeed() {
      // the read-time resolver tested the USER and CSS tiers explicitly and skipped. The seed
      // writes the DEFAULT tier only, so the same property now rests on tier precedence — which is
      // why it needs asserting here rather than being assumed
      gateOn();
      SreeEnv.setProperty("viewsheet.darkMode", "true");
      SelectionListVSAssemblyInfo info = newSelectionList();
      VSCompositeFormat cell = info.getFormatInfo()
         .getFormat(new TableDataPath(-1, TableDataPath.DETAIL));
      cell.getUserDefinedFormat().setForegroundValue("0x123456");

      info.seedChromeDefaults(VizContext.of(info));

      assertEquals("0x123456", cell.getUserDefinedFormat().getForegroundValue(),
                   "the seed never touches the USER tier");
      assertEquals(new Color(0x123456), cell.getForeground(),
                   "and the composite still resolves the author's colour");
   }
```

`TableDataPath` is **not** reachable from this test file's existing imports — it is `inetsoft.report.TableDataPath`, which `SelectionBaseVSAssemblyInfo` imports explicitly at its `:23`. Add `import inetsoft.report.TableDataPath;` to the test.

- [ ] **Step 2: Run them to verify they fail**

Run: `./mvnw test -pl core -Dtest=SeedChromeDefaultsTest`
Expected: `aDarkSelectionListSeedsTheLightCellForeground`, `aDarkSelectionTreeSeedsTheLightCellForeground` FAIL — the stored value is the near-black; the light neutral exists only on read-time clones. Also a compile error on `darkForegroundValue()` and `legacyCellForegroundValue()`, which do not exist yet.

- [ ] **Step 3: Add the two suppliers**

In `VSObjectChromeDefaults.java`, change `darkForegroundValue()` from private to public and document why, and add the legacy supplier beside it:

```java
   /**
    * The dark-mode light text value. Public because the selection-cell and slider seeds write it
    * into a stored DEFAULT tier at creation rather than substituting it at read time.
    */
   public static String darkForegroundValue() {
      return String.format("0x%06x", TEXT_FG_DARK.getRGB() & 0xFFFFFF);
   }

   /**
    * The cell foreground a gate-off creation writes, and what the legacy branch of the seed
    * restores. Excel's list and tree exporters substitute it back over a seeded dark value: their
    * cells are unfilled white, so the light neutral would be invisible there.
    */
   public static String legacyCellForegroundValue() {
      return String.format("0x%06x", LEGACY_CELL_FG.getRGB() & 0xFFFFFF);
   }
```

and beside the other colour constants:

```java
   // the selection cell's creation default, and Excel's deliberate exception
   private static final Color LEGACY_CELL_FG = new Color(0x2b2b2b);
```

- [ ] **Step 4: Seed the cell foreground**

In `SelectionBaseVSAssemblyInfo.seedChromeDefaults`, after the existing title-lane block:

```java
      // the detail cell's foreground. Seeded rather than substituted at every render so it travels
      // in an exported asset. setDefaultFormat's unconditional near-black stays where it is and
      // this overwrites it; the measure-bar composites are separate paths and are not reached
      VSCompositeFormat cellFormat =
         getFormatInfo().getFormat(new TableDataPath(-1, TableDataPath.DETAIL));

      if(cellFormat != null) {
         cellFormat.getDefaultFormat().setForegroundValue(
            ctx.dark ? VSObjectChromeDefaults.darkForegroundValue()
               : VSObjectChromeDefaults.legacyCellForegroundValue());
      }
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `./mvnw test -pl core -Dtest=SeedChromeDefaultsTest`
Expected: PASS, all seven new tests.

- [ ] **Step 6: Narrow `getValueFormat` and remove the substitution**

In `VSSelectionListHelper.java`, drop the `VizContext ctx` parameter and the substitution line, keeping the rest of the method as it is:

```java
   public static VSCompositeFormat getValueFormat(SelectionValue value,
                                                  VSCompositeFormat format,
                                                  boolean hasSelected)
   {
      if(value != null) {
```

Delete the four-line comment above the removed `applyDarkForeground` call with it — it described the read-time ordering, which no longer exists.

- [ ] **Step 7: Fix the three in-module callers, then the two HTML sites**

`getValueFormat` has exactly **six** callers, verified by grep. Three are in this module and drop their trailing `VizContext.of(...)` argument here: `VSSelectionTreeHelper.java:187`, `pdf/PDFSelectionListHelper.java:164`, `svg/SVGSelectionListHelper.java:158`. The other three are in `utils/inetsoft-xml-formats` and are Step 9's business.

**There is no seventh caller inside `VSSelectionListHelper` itself.** An earlier draft of this plan told you to check `VSSelectionListHelper.java:318` for "the other call in the same file" — that line is the `applyDarkForeground` substitution *inside* `getValueFormat`, which Step 6 already deleted. Do not go looking for a call that is not there.

Then remove the substitution at `html/HTMLSelectionListHelper.java:183` and `html/HTMLSelectionTreeHelper.java:129`, so each reads `svalue.getFormat()` directly. These two are `applyDarkForeground` sites, not `getValueFormat` sites — which is why the resolver has five call sites while the method has six callers.

- [ ] **Step 8: Drop the browser and composer sites**

In `SelectionListModel.getFormats()`, remove the `VizContext ctx = VizContext.of(assemblyInfo);` line, the substitution, and the twelve-line comment above it, leaving:

```java
      for(Map.Entry<VSCompositeFormat, Integer> entry : formats.entrySet()) {
         VSFormatModel formatModel = new VSFormatModel(entry.getKey(), assemblyInfo);
```

In `FormatPainterService`, delete the `else if(info instanceof SelectionBaseVSAssemblyInfo && isPlainSelectionCell(dataPath))` branch with its comment, and delete `isPlainSelectionCell` at `:942-945`.

- [ ] **Step 9: Invert Excel's opt-out**

In `ExcelSelectionListHelper.java`, replace the comment and call at `:255-261` with:

```java
         // legacy ink on purpose. A spreadsheet has no page to paint and a selection list seeds no
         // dark card background, so its cells are unfilled white and the seeded light neutral would
         // be invisible on them. The value is in the stored format now, so this substitutes the
         // legacy one back onto a clone rather than declining a read-time substitution. PPT does
         // not do this: a slide takes the viewsheet background
         if(VizContext.of(info).dark) {
            format = format == null ? new VSCompositeFormat()
               : (VSCompositeFormat) format.clone();
            format.getDefaultFormat().setForegroundValue(
               VSObjectChromeDefaults.legacyCellForegroundValue());
         }

         format = VSSelectionListHelper.getValueFormat(value, format, hasSelected);
```

In `ExcelSelectionTreeHelper.java` at `:227-232`, the same, and **the clone is mandatory here**: that site reads `sv.getFormat()` uncloned, so writing in place would mutate the stored format.

```java
            format = sv.getFormat();

            // legacy ink on purpose: see ExcelSelectionListHelper for why Excel differs. Cloned
            // because sv.getFormat() is the stored format
            if(VizContext.of(info).dark) {
               format = format == null ? new VSCompositeFormat()
                  : (VSCompositeFormat) format.clone();
               format.getDefaultFormat().setForegroundValue(
                  VSObjectChromeDefaults.legacyCellForegroundValue());
            }

            format = VSSelectionListHelper.getValueFormat(sv, format, hasSelected);
```

In `PPTSelectionListHelper.java:147`, drop the trailing argument only; keep its comment, which is still true.

- [ ] **Step 10: Verify the cross-module change with a clean build**

Run: `./mvnw clean install -DskipTests`
Expected: BUILD SUCCESS. **A `clean` is required, not an incremental `install`.** This is the third cross-module signature change on this branch; the previous one reported SUCCESS from a 77-module incremental build while three callers in `utils/inetsoft-xml-formats` were stale, and only `clean` exposed them.

- [ ] **Step 11: Run the affected suites**

Run: `./mvnw test -pl core -Dtest='SeedChromeDefaultsTest+VSObjectChromeDefaultsTest'`
Expected: PASS. `VSObjectChromeDefaultsTest` will fail to compile on its `applyDarkForeground` cases if it references them — leave those tests alone here; Task 3 deletes the method and moves them.

- [ ] **Step 12: Commit**

```bash
git add core/src/main/java/inetsoft/uql/viewsheet/internal/SelectionBaseVSAssemblyInfo.java core/src/main/java/inetsoft/uql/viewsheet/internal/VSObjectChromeDefaults.java core/src/main/java/inetsoft/report/io/viewsheet core/src/main/java/inetsoft/web/viewsheet/model/SelectionListModel.java core/src/main/java/inetsoft/web/composer/vs/controller/FormatPainterService.java utils/inetsoft-xml-formats core/src/test/java/inetsoft/uql/viewsheet/internal/SeedChromeDefaultsTest.java
git commit -m "feat(viewsheet): seed a selection cell's dark foreground at creation"
```

---

### Task 3: The slider's foreground

**Files:**
- Modify: `core/src/main/java/inetsoft/uql/viewsheet/internal/SliderVSAssemblyInfo.java` (new override)
- Modify: `core/src/main/java/inetsoft/uql/viewsheet/internal/VSObjectChromeDefaults.java` (delete `applyDarkForeground`, `applyDarkForegroundInPlace`, `textForegroundCss`)
- Modify: `core/src/main/java/inetsoft/report/gui/viewsheet/VSSlider.java:67-70`
- Modify: `core/src/main/java/inetsoft/web/viewsheet/model/VSSliderModel.java:43-57`
- Test: `core/src/test/java/inetsoft/uql/viewsheet/internal/SeedChromeDefaultsTest.java`
- Test: `core/src/test/java/inetsoft/uql/viewsheet/internal/VSObjectChromeDefaultsTest.java`

**Interfaces:**
- Consumes: `VSObjectChromeDefaults.darkForegroundValue()` from Task 2.
- Produces: nothing later tasks use.

**Context an implementer needs.** `SliderVSAssemblyInfo` inherits the base `setDefaultFormat`, which writes **no foreground at all**, so the legacy state here is genuinely absent and the legacy branch must write `null`. It must null **both** `setForegroundValue(null)` and `setForeground(null)`, because `getForeground()` falls back to the `fg` field when the value yields nothing — the same reason the chart's title seed nulls both. The slider is **not** on `bypassesBaseChrome()`, so the base hook already runs for it and `super.seedChromeDefaults(ctx)` does real work; keep the call.

`VSSliderModel:43-57` is a **seventh** read-time site that the roadmap's tally of six missed: it lifts the same value through `textForegroundCss` rather than `applyDarkForeground`.

- [ ] **Step 1: Write the failing tests**

Add to `SeedChromeDefaultsTest`, with a slider factory beside the existing ones:

```java
   private SliderVSAssemblyInfo newSlider() {
      Viewsheet vs = new Viewsheet();
      SliderVSAssembly slider = new SliderVSAssembly(vs, "Slider1");
      slider.getVSAssemblyInfo().initDefaultFormat();
      return (SliderVSAssemblyInfo) slider.getVSAssemblyInfo();
   }

   // ---- the slider's object foreground -------------------------------------------------------

   @Test
   void aDarkSliderSeedsTheLightForeground() {
      gateOn();
      SreeEnv.setProperty("viewsheet.darkMode", "true");
      assertEquals(VSObjectChromeDefaults.darkForegroundValue(),
                   objectDefault(newSlider()).getForegroundValue());
   }

   @Test
   void aLightSliderSeedsNoForegroundAtAll() {
      gateOn();
      VSFormat fmt = objectDefault(newSlider());
      assertNull(fmt.getForegroundValue(),
                 "the base setDefaultFormat writes no foreground, so light modern writes none " +
                 "either and the asset stays byte-identical to a legacy one");
      assertNull(fmt.getForeground(), "the fg field is nulled too, or a runtime value survives");
   }

   @Test
   void modernizingAnUnmarkedDarkSliderSeedsTheLightForeground() {
      gateOff();
      SliderVSAssemblyInfo info = newSlider();
      assertNull(objectDefault(info).getForegroundValue());

      gateOn();
      SreeEnv.setProperty("viewsheet.darkMode", "true");
      info.seedChromeDefaults(VizContext.ofGate());

      assertEquals(VSObjectChromeDefaults.darkForegroundValue(),
                   objectDefault(info).getForegroundValue());
   }

   @Test
   void anAuthorsSliderForegroundOutranksTheSeed() {
      gateOn();
      SreeEnv.setProperty("viewsheet.darkMode", "true");
      SliderVSAssemblyInfo info = newSlider();
      info.getFormat().getUserDefinedFormat().setForegroundValue("0x123456");

      info.seedChromeDefaults(VizContext.of(info));

      assertEquals(new Color(0x123456), info.getFormat().getForeground(),
                   "the seed writes the DEFAULT tier, so the author's colour still resolves");
   }

   @Test
   void revertingADarkSliderClearsTheSeededForeground() {
      gateOn();
      SreeEnv.setProperty("viewsheet.darkMode", "true");
      SliderVSAssemblyInfo info = newSlider();
      assertEquals(VSObjectChromeDefaults.darkForegroundValue(),
                   objectDefault(info).getForegroundValue());

      info.setVizMark(null);
      info.seedChromeDefaults(VizContext.of((VizMark) null));

      assertNull(objectDefault(info).getForegroundValue());
      assertNull(objectDefault(info).getForeground());
   }
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./mvnw test -pl core -Dtest=SeedChromeDefaultsTest`
Expected: `aDarkSliderSeedsTheLightForeground` and the first assertion of `revertingADarkSliderClearsTheSeededForeground` FAIL — the stored foreground is null in every mode today. `aLightSliderSeedsNoForegroundAtAll` PASSES already; it is the byte-identical pin.

- [ ] **Step 3: Add the override**

In `SliderVSAssemblyInfo.java`, after the constructor:

```java
   @Override
   protected void seedChromeDefaults(VizContext ctx) {
      super.seedChromeDefaults(ctx);

      // the tick and value labels take the object foreground, whose default is absent and paints
      // black — unreadable on a dark card. Seeded so the painter, the browser model and every
      // export read one value. Both branches write, and the legacy one writes nothing at all
      // because a gate-off creation writes no foreground here
      VSCompositeFormat objFormat = getFormat();

      if(objFormat != null) {
         VSFormat def = objFormat.getDefaultFormat();
         def.setForegroundValue(ctx.dark ? VSObjectChromeDefaults.darkForegroundValue() : null);
         // getForeground() falls back to the fg field when the value yields nothing, so the legacy
         // branch has to null both or a runtime foreground survives the clear
         def.setForeground(null);
      }
   }
```

Add `import inetsoft.uql.viewsheet.VSCompositeFormat;` and `import inetsoft.uql.viewsheet.VSFormat;` — this file imports only `DynamicValue` from that package today.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./mvnw test -pl core -Dtest=SeedChromeDefaultsTest`
Expected: PASS.

- [ ] **Step 5: Drop the two slider read sites**

In `VSSlider.java:67-70`, replace the substitution with a direct read:

```java
         VSCompositeFormat format = info.getFormat();
         format = format == null ? new VSCompositeFormat() : format;
```

**Keep the `VizContext ctx = VizContext.of(info);` line above it.** The painter uses `ctx` four more times for its track, tick and handle colours — `sliderInactiveTrack` at `:122`, `sliderActiveTrack` at `:127`, `sliderTick` at `:152`, `sliderHandle` at `:265`. Those are painter colours, not format values, and are out of scope.

In `VSSliderModel.java`, delete the entire block at `:43-57` — the comment, the `textForegroundCss` call, and the guarded `getObjectFormat().setForeground(...)`. The seeded value now arrives through the normal format path.

- [ ] **Step 6: Delete the three resolver methods**

From `VSObjectChromeDefaults.java`, delete `applyDarkForeground`, `applyDarkForegroundInPlace` and `textForegroundCss`. Keep `darkForegroundValue()` (now public, and the seeds' supplier) and `TEXT_FG_DARK`.

- [ ] **Step 7: Move the resolver's tests onto the seed**

In `VSObjectChromeDefaultsTest`, delete the `applyDarkForeground`, `applyDarkForegroundInPlace` and `textForegroundCss` cases — the behaviour they pinned is now covered by the seed tests in `SeedChromeDefaultsTest` (Task 2's cell cases and Task 3's slider cases). Keep every test for the surviving suppliers, and keep `withGate` / `withDark`.

- [ ] **Step 8: Run both suites**

Run: `./mvnw test -pl core -Dtest='SeedChromeDefaultsTest+VSObjectChromeDefaultsTest'`
Expected: PASS.

- [ ] **Step 9: Commit**

```bash
git add core/src/main/java/inetsoft/uql/viewsheet/internal/SliderVSAssemblyInfo.java core/src/main/java/inetsoft/uql/viewsheet/internal/VSObjectChromeDefaults.java core/src/main/java/inetsoft/report/gui/viewsheet/VSSlider.java core/src/main/java/inetsoft/web/viewsheet/model/VSSliderModel.java core/src/test/java/inetsoft/uql/viewsheet/internal/SeedChromeDefaultsTest.java core/src/test/java/inetsoft/uql/viewsheet/internal/VSObjectChromeDefaultsTest.java
git commit -m "feat(viewsheet): seed the slider's dark foreground at creation"
```

---

### Task 4: The text assembly's value emphasis

**Files:**
- Modify: `core/src/main/java/inetsoft/uql/viewsheet/internal/TextVSAssemblyInfo.java` (new override plus a creation invoke at the end of `setDefaultFormat`)
- Modify: `core/src/main/java/inetsoft/uql/viewsheet/internal/VSOutputChromeDefaults.java` (delete `applyModernDefaults`, `applyModernDefaultsInPlace`)
- Modify: `core/src/main/java/inetsoft/report/io/viewsheet/AbstractVSExporter.java:1914`
- Modify: `core/src/main/java/inetsoft/web/composer/vs/controller/FormatPainterService.java:222-224`
- Modify: `core/src/main/java/inetsoft/web/viewsheet/model/VSTextModel.java:206-212`
- Test: `core/src/test/java/inetsoft/uql/viewsheet/internal/SeedChromeDefaultsTest.java`
- Test: `core/src/test/java/inetsoft/uql/viewsheet/internal/VSOutputChromeDefaultsTest.java`

**Interfaces:**
- Consumes: nothing from earlier tasks.
- Produces: nothing.

**Context an implementer needs.** `TextVSAssemblyInfo` is on `bypassesBaseChrome()`, and stays on it. Its override's `super.seedChromeDefaults(ctx)` returns immediately at that guard, which is exactly right — the base's object border and card radius must not reach a text assembly — and then the override writes the value emphasis. This is why the predicate needs no split.

`setDefaultFormat(boolean border)` writes border colours **only when `border` is true**. `initDefaultFormat` passes true, but other callers pass false, and writing a colour where creation wrote none would change the stored asset for an unmarked text assembly. So the seed writes border colours only when the DEFAULT tier already carries one — the same test the base hook uses at `VSAssemblyInfo:1263` before colouring a title border. Border colours without widths draw nothing, so this is invisible on screen; it is about not writing into an asset that lacked it.

- [ ] **Step 1: Write the failing tests**

Add to `SeedChromeDefaultsTest`:

```java
   // ---- the text assembly's value emphasis ---------------------------------------------------

   @Test
   void aModernTextAssemblySeedsTheValueEmphasis() {
      gateOn();
      VizContext ctx = VizContext.ofGate();
      VSFormat fmt = objectDefault(newText());
      assertEquals(String.format("0x%06x",
                                 VSOutputChromeDefaults.valueForeground(ctx).getRGB() & 0xFFFFFF),
                   fmt.getForegroundValue());
      assertEquals(VSOutputChromeDefaults.valueBorderColor(ctx),
                   fmt.getBorderColorsValue().topColor);
   }

   @Test
   void aLegacyTextAssemblySeedsTheLegacyEmphasis() {
      gateOff();
      VSFormat fmt = objectDefault(newText());
      assertEquals("0x2b2b2b", fmt.getForegroundValue());
      assertEquals(VSAssemblyInfo.DEFAULT_BORDER_COLOR, fmt.getBorderColorsValue().topColor);
   }

   @Test
   void revertingATextAssemblyRestoresTheLegacyEmphasis() {
      gateOn();
      TextVSAssemblyInfo info = newText();

      info.setVizMark(null);
      info.seedChromeDefaults(VizContext.of((VizMark) null));

      VSFormat fmt = objectDefault(info);
      assertEquals("0x2b2b2b", fmt.getForegroundValue());
      assertEquals(VSAssemblyInfo.DEFAULT_BORDER_COLOR, fmt.getBorderColorsValue().topColor);
   }

   @Test
   void aTextAssemblyWithNoBorderGainsNoBorderColour() {
      // setDefaultFormat(false) writes no border colours, and the seed must not invent one: an
      // unmarked asset has to stay bit-for-bit unchanged
      gateOn();
      TextVSAssemblyInfo info = new TextVSAssemblyInfo();
      info.setDefaultFormat(false);

      assertNull(objectDefault(info).getBorderColorsValue());
   }

   @Test
   void anAuthorsTextForegroundOutranksTheSeed() {
      gateOn();
      TextVSAssemblyInfo info = newText();
      info.getFormat().getUserDefinedFormat().setForegroundValue("0x123456");

      info.seedChromeDefaults(VizContext.ofGate());

      assertEquals(new Color(0x123456), info.getFormat().getForeground());
   }

   @Test
   void aRestoredStateReseedsAStaleValue() {
      // VizModernizeUtil.reseedAfterRestore delegates to the same hook, so one test covers the
      // mechanism for all four conversions rather than four near-identical ones. What it pins is
      // that a bookmark's stale DEFAULT tier is re-resolved rather than trusted
      gateOn();
      TextVSAssemblyInfo info = newText();
      info.getFormat().getDefaultFormat().setForegroundValue("0x2b2b2b");

      VizModernizeUtil.reseedAfterRestore(info);

      assertEquals(String.format("0x%06x",
                                 VSOutputChromeDefaults.valueForeground(VizContext.of(info))
                                    .getRGB() & 0xFFFFFF),
                   objectDefault(info).getForegroundValue(),
                   "restore re-resolves the seed against the live mark");
   }

   @Test
   void aTextAssemblyStillTakesNoCardRadiusUnderTheGate() {
      gateOn();
      assertEquals(0, objectDefault(newText()).getRoundCornerValue(),
                   "the override's super call returns at bypassesBaseChrome, so the base's radius " +
                   "and object border never reach a text assembly");
   }
```

`setDefaultFormat` is `protected`; `aTextAssemblyWithNoBorderGainsNoBorderColour` is in the same package as `TextVSAssemblyInfo`, so the call compiles.

- [ ] **Step 2: Run them to verify they fail**

Run: `./mvnw test -pl core -Dtest=SeedChromeDefaultsTest`
Expected: `aModernTextAssemblySeedsTheValueEmphasis` and `aRestoredStateReseedsAStaleValue` FAIL — the stored format carries the near-black and the legacy border, and the emphasis exists only on read-time clones. The other four PASS already and are the pins.

- [ ] **Step 3: Add the override and the creation invoke**

In `TextVSAssemblyInfo.java`, add at the very end of `setDefaultFormat`, after `setCSSDefaults()`:

```java
      // this type is on bypassesBaseChrome, so the base never seeded it and never called the hook
      // for it; invoke it here so a fresh text assembly carries its own gate-dependent values
      seedChromeDefaults(VizContext.of(this));
```

and add the override after that method:

```java
   @Override
   protected void seedChromeDefaults(VizContext ctx) {
      // returns at bypassesBaseChrome, which is correct: the base's object border and card radius
      // are not this type's, and its own setDefaultFormat owns them
      super.seedChromeDefaults(ctx);

      VSCompositeFormat objFormat = getFormat();

      if(objFormat == null) {
         return;
      }

      VSFormat def = objFormat.getDefaultFormat();
      def.setForegroundValue(ctx.modern
         ? String.format("0x%06x",
                         VSOutputChromeDefaults.valueForeground(ctx).getRGB() & 0xFFFFFF)
         : "0x2b2b2b");

      // only when creation wrote one: setDefaultFormat(false) writes no border colours, and
      // inventing one here would change the stored asset for an unmarked assembly
      if(def.getBorderColorsValue() != null) {
         Color border = ctx.modern ? VSOutputChromeDefaults.valueBorderColor(ctx)
            : DEFAULT_BORDER_COLOR;
         def.setBorderColorsValue(new BorderColors(border, border, border, border));
      }
   }
```

`java.awt.*` is already imported; `BorderColors` resolves through the existing `inetsoft.uql.viewsheet.*` import.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./mvnw test -pl core -Dtest=SeedChromeDefaultsTest`
Expected: PASS. **One existing test in this class now fails — fix it in step 5.**

- [ ] **Step 5: Update the existing text assertion the conversion invalidates**

`theHookDoesNothingForATypeThatInstallsItsOwnFormat` asserts that calling the hook on a gate-on text assembly leaves the object border colour legacy. That is no longer true: the type's own override writes the modern border colour, while the *base* seeds still do not reach it. Replace the test with:

```java
   @Test
   void theHookSeedsATextAssemblysOwnValuesAndNoneOfTheBases() {
      // Text builds its own object format, so the base seeds must not reach it — but its own
      // override does write the value emphasis, which is what the seed conversion moved here
      gateOn();
      TextVSAssemblyInfo info = newText();
      VizContext ctx = VizContext.ofGate();

      info.seedChromeDefaults(ctx);

      assertEquals(VSOutputChromeDefaults.valueBorderColor(ctx),
                   objectDefault(info).getBorderColorsValue().topColor,
                   "the type's own emphasis is seeded");
      assertEquals(0, objectDefault(info).getRoundCornerValue(),
                   "and the base's card radius still never reaches it");
   }
```

Also update `aTextAssemblyIsNotACornerSeedTargetEvenUnderTheGate`: its border-colour assertion is now the modern value. Keep its radius assertion, drop the border-colour assertion (covered above), and reword its comment so it no longer claims the type "never consults VizContext".

- [ ] **Step 6: Run the class again**

Run: `./mvnw test -pl core -Dtest=SeedChromeDefaultsTest`
Expected: PASS.

- [ ] **Step 7: Drop the three read sites**

In `AbstractVSExporter.java`, delete the `VSOutputChromeDefaults.applyModernDefaultsInPlace(fmt, ctx);` line at `:1914` and its two-line comment. Keep the `fmt = fmt.clone();` above it — the highlight writes below still need a copy.

In `FormatPainterService.java`, delete the `else if(assembly instanceof TextVSAssembly)` branch at `:222-224`.

In `VSTextModel.java`, reduce `createFormatModel` to:

```java
   @Override
   protected VSFormatModel createFormatModel(VSCompositeFormat compositeFormat,
                                             VSAssemblyInfo assemblyInfo)
   {
      return new VSFormatModel(compositeFormat, assemblyInfo, true);
   }
```

- [ ] **Step 8: Delete the resolver pair and move its tests**

From `VSOutputChromeDefaults.java`, delete `applyModernDefaults` and `applyModernDefaultsInPlace`, along with the private `applyTo`, `isForegroundCustomized` and `isBorderCustomized` helpers if nothing else uses them. Keep the four slider colour suppliers, `valueForeground` and `valueBorderColor`.

In `VSOutputChromeDefaultsTest`, delete the `applyModernDefaults` and `applyModernDefaultsInPlace` cases; the behaviour is now pinned by the seed tests. Keep the supplier cases.

- [ ] **Step 9: Run the affected suites and a clean build**

Run: `./mvnw test -pl core -Dtest='SeedChromeDefaultsTest+VSOutputChromeDefaultsTest'`
Expected: PASS.

Run: `./mvnw clean install -DskipTests`
Expected: BUILD SUCCESS.

- [ ] **Step 10: Commit**

```bash
git add core/src/main/java/inetsoft/uql/viewsheet/internal/TextVSAssemblyInfo.java core/src/main/java/inetsoft/uql/viewsheet/internal/VSOutputChromeDefaults.java core/src/main/java/inetsoft/report/io/viewsheet/AbstractVSExporter.java core/src/main/java/inetsoft/web/composer/vs/controller/FormatPainterService.java core/src/main/java/inetsoft/web/viewsheet/model/VSTextModel.java core/src/test/java/inetsoft/uql/viewsheet/internal/SeedChromeDefaultsTest.java core/src/test/java/inetsoft/uql/viewsheet/internal/VSOutputChromeDefaultsTest.java
git commit -m "feat(viewsheet): seed a text assembly's value emphasis at creation"
```

---

### Task 5: The verification gate

**Files:** none modified. This task produces evidence, not code.

**Interfaces:**
- Consumes: all four conversions.
- Produces: a pass/fail record for the twelve manual checks in the spec.

- [ ] **Step 1: Run the full core suite**

Run: `./mvnw test -pl core`
Expected: 0 failures, 0 errors. The baseline on this branch is ~5222 tests; a drop of more than the handful of resolver tests deleted in Tasks 3 and 4 means something was removed that should not have been.

- [ ] **Step 2: Run a clean cross-module build**

Run from the enterprise root: `./mvnw clean install -DskipTests -Pcommunity,enterprise`
Expected: BUILD SUCCESS. This is the gate for the `getValueFormat` narrowing reaching `utils/inetsoft-xml-formats`.

- [ ] **Step 3: Confirm no resolver call sites survive**

Run:

```bash
grep -rn "applyDarkForeground\|textForegroundCss\|VSObjectChromeDefaults.chartPadding\|VSOutputChromeDefaults.applyModernDefaults" --include=*.java core/src utils enterprise
```

Expected: no output. Any hit is a missed call site.

- [ ] **Step 4: Work the manual checks**

Run the twelve checks in the spec's "Manual checks" section against a running server. Two carry more weight than the rest:

- **Check 6** — a dark marked selection list and tree exported to Excel must keep the legacy near-black and stay readable on white cells. This is the check that fails if Task 2's inverted opt-out was missed, and it fails silently as white-on-white.
- **Check 12** — export a marked dashboard, import it, and confirm the four values arrive. This is the acceptance check for the item: it is the defect being closed, and it fails today.

- [ ] **Step 5: Record the outcome**

Append a dated section to the spec recording what the implementation found and what it left open, following the pattern of `2026-08-31-selection-family-title-lane-design.md` §9. Note any premise in this plan that turned out false, rather than dropping it quietly.

---

## Notes for whoever picks up group 2

Three things this group establishes that group 2 should not re-derive:

- **The bypass predicate needs no split.** A subclass `seedChromeDefaults` override runs even for a bypassing type, because the guard is inside the base implementation. Task 4 proves it for the text assembly; checkbox and radio button take the same shape.
- **Excel's opt-out inverts wherever a cell value is seeded.** Task 2 pays for this once for the selection cell. Any group 2 conversion that seeds a value Excel deliberately declines will need the same inversion.
- **A test that stamps the mark after `initDefaultFormat` no longer resolves anything.** It must re-run the hook. Task 1's `markedChart()` helper is the pattern.
