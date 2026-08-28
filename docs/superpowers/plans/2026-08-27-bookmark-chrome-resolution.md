# Bookmark Chrome Re-resolution Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make an assembly's seeded chrome re-resolve against its own seed mark after its state is parsed, so a bookmark taken before a Revert can no longer un-revert it — the last release-gate item on this branch.

**Architecture:** Two halves. A per-assembly re-seed at `AbstractVSAssembly.parseState`, the `final` chokepoint every restore passes through, delegating to a new `VizModernizeUtil.reseedAfterRestore`. Then a per-sheet cache clear at the end of `Viewsheet.parseState(Element, boolean)`, because a render prefers the sheet's shared colour frame over an assembly's own — without it the re-seeded palette loses to the stale shared frame, which is the defect `1b8eb3cea` spent five rounds closing.

**Tech Stack:** Java 21, JUnit 5 (`@Tag("core")`, Spring `@ContextConfiguration`).

**Spec:** `docs/superpowers/specs/2026-08-27-bookmark-chrome-resolution-design.md`. Read it first — §2 supersedes decision 10's property list, and §3 records an approach already assessed and rejected so it is not re-floated.

## Global Constraints

- **Nothing may change an unmarked assembly.** An unmarked assembly whose state is written and parsed back must be bit-for-bit unchanged. This is the branch's governing constraint and it outranks everything below.
- **Only DEFAULT tiers and the palette move.** `seedChromeDefaults` writes DEFAULT-tier format values and `CategoricalColorFrame.defaultColors`. A USER-tier format the state legitimately restored must survive untouched — that is what makes re-seeding safe rather than destructive.
- **The mark never travels in state.** It lives in `writeAttributes` (asset XML); `writeState` emits only the class, the name and `writeStateContent`. Do not add it to state, and do not read it from the parsed element — read it from the assembly.
- **Introduce no new opt-out predicate.** `bypassesBaseChrome()` (`VSAssemblyInfo:1320-1330`) and `isCornerSeedTarget()` already answer which types take chrome. The design adds no third mechanism.
- **Ordering is load-bearing.** The per-assembly re-seed must run *after* `parseStateContent` (a table's `setFormatInfo` replaces the object the seed writes into). The sheet clear must run after *both* of `Viewsheet.parseState`'s child passes, not just the first.
- **Java style:** 3-space indent, `if(` with no space before the paren, brace on the same line (wrapped conditions and signatures put the brace on its own line — this codebase's convention). Javadoc on every public method. No design-doc section references in source comments.
- **Test preamble:** every new test class carries `@Tag("core")` and the Spring preamble from `core/src/test/java/inetsoft/uql/viewsheet/TabVSAssemblyTest.java` — note that one includes `SwapperTestConfiguration` alongside `BaseTestConfiguration`, which a state round-trip needs. Copy its licence header verbatim, including the copyright year.

---

## File Structure

**Created**

| File | Responsibility |
|---|---|
| `core/src/test/java/inetsoft/uql/viewsheet/BookmarkChromeResolutionTest.java` | the whole behaviour: both defect directions, the table case, the shared-frame case, and the four guards |

**Modified**

| File | Change |
|---|---|
| `core/src/main/java/inetsoft/uql/viewsheet/internal/VizModernizeUtil.java` | add `reseedAfterRestore(VSAssemblyInfo)` beside `modernize` and `revert` |
| `core/src/main/java/inetsoft/uql/viewsheet/AbstractVSAssembly.java` | one call in the `final parseState(Element)` at `:641-643` |
| `core/src/main/java/inetsoft/uql/viewsheet/Viewsheet.java` | clear the two sheet caches at the end of `parseState(Element, boolean)` |

**Deliberately not modified:** `ChartVSAssembly`, `TableVSAssembly`, `CrosstabVSAssembly` — the whole point of the chokepoint placement is that no per-class restore path is touched. If you find yourself editing one, stop and re-read the spec's §4.3.

---

### Task 1: The helper, the per-assembly hook, and the guards

**Files:**
- Modify: `core/src/main/java/inetsoft/uql/viewsheet/internal/VizModernizeUtil.java`
- Modify: `core/src/main/java/inetsoft/uql/viewsheet/AbstractVSAssembly.java:641-643`
- Test: `core/src/test/java/inetsoft/uql/viewsheet/BookmarkChromeResolutionTest.java`

**Interfaces:**
- Consumes: `VSAssemblyInfo.seedChromeDefaults(VizContext)` (protected, same package as `VizModernizeUtil`), `VizContext.of(VSAssemblyInfo)`, `AbstractVSAssembly.getVSAssemblyInfo()` (`:178`).
- Produces: `public static void VizModernizeUtil.reseedAfterRestore(VSAssemblyInfo info)` — null-tolerant, no return value. Task 2 does not call it; Task 2's half is at the sheet level.

**Why the test must go through `parseState` and not call the helper directly.** The previous plan on this branch shipped a Critical that survived six clean reviews because its test mutated one object instead of crossing the clone-and-merge boundary the defect lived on. The same trap applies here: a test that calls `reseedAfterRestore(info)` proves the helper works and proves nothing about whether restore reaches it. Every test below round-trips through `writeState` / `parseState`.

- [ ] **Step 1: Write the failing tests**

Create `core/src/test/java/inetsoft/uql/viewsheet/BookmarkChromeResolutionTest.java`. Copy the licence header verbatim from `TabVSAssemblyTest.java` in the same package.

```java
package inetsoft.uql.viewsheet;

import inetsoft.test.*;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.internal.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.w3c.dom.*;

import javax.xml.parsers.DocumentBuilderFactory;
import java.awt.Color;
import java.io.*;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class BookmarkChromeResolutionTest {
   @Test
   void aBookmarkTakenWhileMarkedDoesNotUnRevertTheChart() throws Exception {
      // the defect: bookmark a modern chart, Revert the dashboard, restore the bookmark. The
      // assembly is unmarked but its chrome came back modern.
      Viewsheet vs = new Viewsheet();
      ChartVSAssembly chart = newChart(vs, VizMark.MODERN_LIGHT);
      String state = writeState(chart);

      // Revert: clear the mark and re-seed legacy, exactly as VizModernizeUtil.revert does
      ChartVSAssemblyInfo info = (ChartVSAssemblyInfo) chart.getVSAssemblyInfo();
      info.setVizMark(null);
      chart.parseState(parseXml(state));

      assertEquals(0.0, info.getChartDescriptor().getPlotDescriptor().getBarCornerRadius(), 0.001,
                   "the reverted chart must not get the modern bar corners back from the bookmark");
   }

   @Test
   void aBookmarkTakenWhileUnmarkedDoesNotLeaveLegacyChromeOnAMarkedChart() throws Exception {
      // the other direction: bookmark a legacy chart, Modernize, restore.
      Viewsheet vs = new Viewsheet();
      ChartVSAssembly chart = newChart(vs, null);
      String state = writeState(chart);

      ChartVSAssemblyInfo info = (ChartVSAssemblyInfo) chart.getVSAssemblyInfo();
      info.setVizMark(VizMark.MODERN_LIGHT);
      chart.parseState(parseXml(state));

      assertEquals(0.3, info.getChartDescriptor().getPlotDescriptor().getBarCornerRadius(), 0.001,
                   "the modernized chart must not keep the bookmark's legacy bar corners");
   }

   @Test
   void aBookmarkTakenWhileMarkedDoesNotUnRevertTheChartsPalette() throws Exception {
      // the palette is the value 1b8eb3cea spent five bug-fix rounds on, and it rides in
      // <state_info> rather than <state_descriptor>. Corners passing does not imply colour passing.
      Viewsheet vs = new Viewsheet();
      ChartVSAssembly chart = newChart(vs, VizMark.MODERN_LIGHT);
      bindColorAesthetic(chart);
      String state = writeState(chart);

      ChartVSAssemblyInfo info = (ChartVSAssemblyInfo) chart.getVSAssemblyInfo();
      info.setVizMark(null);
      chart.parseState(parseXml(state));

      assertEquals(legacyFirstColor(), firstAestheticColor(info),
                   "the reverted chart must not keep the bookmark's modern palette");
   }

   @Test
   void aTableGetsItsChromeBackAfterRestoreReplacesItsWholeFormatInfo() throws Exception {
      // the widest case: TableVSAssembly's parseStateContent calls setFormatInfo, replacing the
      // whole object the seed wrote into, so all three format values are exposed at once.
      Viewsheet vs = new Viewsheet();
      TableVSAssembly table = new TableVSAssembly(vs, "Table1");
      table.getVSAssemblyInfo().initDefaultFormat();
      TableVSAssemblyInfo info = (TableVSAssemblyInfo) table.getVSAssemblyInfo();
      info.setVizMark(VizMark.MODERN_LIGHT);
      info.seedChromeDefaults(VizContext.of(info));

      int modernRadius = info.getFormat().getDefaultFormat().getRoundCornerValue();
      assertNotEquals(0, modernRadius, "precondition: a marked table carries a card radius");

      String state = writeState(table);
      info.setVizMark(null);
      table.parseState(parseXml(state));

      assertEquals(0, info.getFormat().getDefaultFormat().getRoundCornerValue(),
                   "the card radius must follow the cleared mark, not the restored FormatInfo");
   }

   @Test
   void anUnmarkedAssemblyIsUnchangedByARoundTrip() throws Exception {
      // the governing constraint. Nothing may move on unmarked content.
      Viewsheet vs = new Viewsheet();
      ChartVSAssembly chart = newChart(vs, null);
      ChartVSAssemblyInfo info = (ChartVSAssemblyInfo) chart.getVSAssemblyInfo();
      double before = info.getChartDescriptor().getPlotDescriptor().getBarCornerRadius();
      int radiusBefore = info.getFormat().getDefaultFormat().getRoundCornerValue();

      chart.parseState(parseXml(writeState(chart)));

      assertEquals(before, info.getChartDescriptor().getPlotDescriptor().getBarCornerRadius(), 0.001);
      assertEquals(radiusBefore, info.getFormat().getDefaultFormat().getRoundCornerValue());
   }

   @Test
   void anAuthorsOwnFormatSurvivesTheReseed() throws Exception {
      // seedChromeDefaults writes DEFAULT tiers only, which is what makes re-seeding safe. An
      // author's border colour must not be touched.
      Viewsheet vs = new Viewsheet();
      ChartVSAssembly chart = newChart(vs, VizMark.MODERN_LIGHT);
      ChartVSAssemblyInfo info = (ChartVSAssemblyInfo) chart.getVSAssemblyInfo();
      BorderColors authors = new BorderColors(Color.RED, Color.RED, Color.RED, Color.RED);
      info.getFormat().getUserDefinedFormat().setBorderColors(authors);

      chart.parseState(parseXml(writeState(chart)));

      assertEquals(authors, info.getFormat().getUserDefinedFormat().getBorderColors(),
                   "the author's border colour is on the USER tier and must be left alone");
   }

   @Test
   void aTypeThatBypassesBaseChromeIsUnharmed() throws Exception {
      // TextVSAssemblyInfo is in bypassesBaseChrome(), so the re-seed is a no-op. It must not throw.
      Viewsheet vs = new Viewsheet();
      TextVSAssembly text = new TextVSAssembly(vs, "Text1");
      text.getVSAssemblyInfo().initDefaultFormat();
      text.getVSAssemblyInfo().setVizMark(VizMark.MODERN_LIGHT);

      assertDoesNotThrow(() -> text.parseState(parseXml(writeState(text))));
   }

   @Test
   void theHelperToleratesANullInfo() {
      assertDoesNotThrow(() -> VizModernizeUtil.reseedAfterRestore(null));
   }

   /**
    * Bind a categorical colour aesthetic so the chart has a CategoricalColorFrame for
    * seedColorPalette to write, then re-seed so the frame carries the mark's palette.
    *
    * seedColorPalette walks getAestheticRefs(runtime) looking for a CategoricalColorFrame; a chart
    * with no bound colour field has none, so the palette assertions need this.
    */
   private static void bindColorAesthetic(ChartVSAssembly chart) {
      ChartVSAssemblyInfo info = (ChartVSAssemblyInfo) chart.getVSAssemblyInfo();
      VSChartInfo cinfo = info.getVSChartInfo();
      VSChartDimensionRef colorDim = new VSChartDimensionRef(new BaseField("State"));
      VSAestheticRef colorRef = new VSAestheticRef();
      colorRef.setDataRef(colorDim);
      colorRef.setVisualFrame(new CategoricalColorFrame());
      cinfo.setColorField(colorRef);
      info.seedChromeDefaults(VizContext.of(info));
   }

   /**
    * The first colour the chart's bound colour frame would hand a render.
    *
    * CategoricalColorFrame.defaultColors is private with no getter, so this reads through the public
    * getColor(Object) instead. If that proves not to reflect defaultColors for an uninitialised
    * frame, call frame.init(new Object[]{"CA"}, ...) first, or assert on
    * VSChartPaletteDefaults.seedPalette(ctx) equality against a frame you set yourself - and say in
    * your report which you did. Do NOT add a getter to CategoricalColorFrame for the test's benefit.
    */
   private static Color firstAestheticColor(ChartVSAssemblyInfo info) {
      CategoricalColorFrame frame =
         (CategoricalColorFrame) info.getVSChartInfo().getColorField().getVisualFrame();
      return frame.getColor("CA");
   }

   /** The first colour of the legacy palette, i.e. what an unmarked chart must resolve to. */
   private static Color legacyFirstColor() {
      return VSChartPaletteDefaults.seedPalette(VizContext.of((VizMark) null))[0];
   }

   /** A separated bar chart with one x dimension and one y measure, seeded for the given mark. */
   private static ChartVSAssembly newChart(Viewsheet vs, VizMark mark) {
      ChartVSAssembly chart = new ChartVSAssembly(vs, "Chart1");
      ChartVSAssemblyInfo info = (ChartVSAssemblyInfo) chart.getVSAssemblyInfo();
      info.initDefaultFormat();
      info.setVizMark(mark);
      info.seedChromeDefaults(VizContext.of(info));
      return chart;
   }

   private static String writeState(VSAssembly assembly) {
      StringWriter sw = new StringWriter();
      assembly.writeState(new PrintWriter(sw), false);
      return sw.toString();
   }

   private static Element parseXml(String xml) throws Exception {
      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
      factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
      factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
      Document doc = factory.newDocumentBuilder()
         .parse(new ByteArrayInputStream(xml.getBytes()));
      return doc.getDocumentElement();
   }
}
```

**If a compile error says `seedChromeDefaults` is not visible from the test:** it is `protected` on `VSAssemblyInfo` in `inetsoft.uql.viewsheet.internal`, and this test is in `inetsoft.uql.viewsheet`. Do NOT widen its visibility. Instead build the seeded state through the public route the product uses — `VizModernizeUtil.modernize(vs)` after adding the assembly to the viewsheet, and `VizModernizeUtil.revert(vs)` for the legacy direction — and report in your report that you changed the harness and why. Those two are public (`:55`, `:102`).

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./mvnw.cmd test -pl core -Dtest=BookmarkChromeResolutionTest -DfailIfNoTests=false`
Expected: compile failure on `VizModernizeUtil.reseedAfterRestore`, which does not exist yet.

- [ ] **Step 3: Add the helper**

In `VizModernizeUtil.java`, after `revert` (which ends around `:121`):

```java
   /**
    * Re-resolve an assembly's seeded chrome after its state has been parsed. Restore replaces stored
    * formats and descriptors without consulting the mark, so the chrome that arrives can disagree
    * with the assembly it lands on: a bookmark taken before a Revert would otherwise un-revert it,
    * and one taken before Modernize would leave legacy chrome on a marked assembly.
    *
    * Safe because the mark never travels in state. It lives in writeAttributes, which is asset XML,
    * and writeState emits only the class, the name and writeStateContent - so the assembly still
    * knows what it should be whatever the blob said. seedChromeDefaults writes DEFAULT tiers and the
    * palette only, so a user format the restored state legitimately carried survives untouched.
    *
    * Per-assembly only. The sheet's shared colour frames are cleared once at the end of
    * Viewsheet.parseState, because a render prefers them over an assembly's own.
    *
    * Null-tolerant: a partially constructed assembly can have no info yet.
    */
   public static void reseedAfterRestore(VSAssemblyInfo info) {
      if(info != null) {
         info.seedChromeDefaults(VizContext.of(info));
      }
   }
```

- [ ] **Step 4: Add the hook**

In `AbstractVSAssembly.java`, replace the body at `:641-643`:

```java
   @Override
   public final void parseState(Element elem) throws Exception {
      parseStateContent(elem);
      VizModernizeUtil.reseedAfterRestore(getVSAssemblyInfo());
   }
```

The call must come **after** `parseStateContent`, not before — a table's `parseStateContent` calls `setFormatInfo`, replacing the object the seed writes into. Add `import inetsoft.uql.viewsheet.internal.VizModernizeUtil;` if the file does not already import it (check — it may import `inetsoft.uql.viewsheet.internal.*`).

- [ ] **Step 5: Run the tests to verify they pass**

Run: `./mvnw.cmd test -pl core -Dtest=BookmarkChromeResolutionTest -DfailIfNoTests=false`
Expected: PASS, 8 tests.

- [ ] **Step 6: Run the suites most likely to notice a new call on every restore**

Run: `./mvnw.cmd test -pl core -Dtest='TabVSAssembly*,SeedChromeDefaults*,VizModernizeUtil*,VizContext*,VSBookmark*,Viewsheet*' -DfailIfNoTests=false`
Expected: PASS. `TabVSAssemblyTest` is the existing state-round-trip test and the most direct canary. **If anything fails here, STOP and report BLOCKED with the output** — a failure means re-seeding on restore changed behaviour something depended on, which is exactly what this step exists to surface.

- [ ] **Step 7: Commit**

```bash
git add core/src/main/java/inetsoft/uql/viewsheet/internal/VizModernizeUtil.java core/src/main/java/inetsoft/uql/viewsheet/AbstractVSAssembly.java core/src/test/java/inetsoft/uql/viewsheet/BookmarkChromeResolutionTest.java
git commit -m "fix(viewsheet): re-resolve an assembly's chrome after its state is parsed"
```

---

### Task 2: The sheet-level cache clear, and the measurement it needs

**Files:**
- Modify: `core/src/main/java/inetsoft/uql/viewsheet/Viewsheet.java` — the end of `parseState(Element, boolean)`, declared at `:2111`
- Test: `core/src/test/java/inetsoft/uql/viewsheet/BookmarkChromeResolutionTest.java` (extend)

**Interfaces:**
- Consumes: Task 1's hook (the per-assembly re-seed must already run, or this task's test cannot distinguish a working clear from a working re-seed). `Viewsheet.clearSharedFrames()` (`:3748`) and `Viewsheet.clearDimensionColors()` (`:3758`).
- Produces: nothing new. This is the second half of one behaviour.

**Why this task exists at all, in the words of the code it copies.** `VizModernizeUtil.revert` does not stop at re-seeding each assembly. After its loop, `:113-118`:

```java
      if(!targets.isEmpty()) {
         // seeding rewrote chart colour frames, and a render clones the sheet's shared frame in
         // preference to an assembly's own, so the stale one has to go or the modern palette survives
         vs.clearSharedFrames();
         vs.clearDimensionColors();
      }
```

A render prefers the sheet's shared frame. So Task 1 alone re-seeds each chart's palette and the render then clones the stale shared one in preference — the palette stays wrong. The caches are per-sheet and `parseState` is per-assembly, so this cannot live at the assembly chokepoint.

- [ ] **Step 1: Write the failing test**

Append to `BookmarkChromeResolutionTest`:

```java
   @Test
   void restoringASheetClearsTheSharedColourFramesTheRenderWouldPrefer() throws Exception {
      // Task 1's per-assembly reseed writes the assembly's own frame, but a render clones the
      // sheet's shared frame in preference. Without the sheet-level clear the stale shared frame
      // survives and the palette stays wrong - the defect 1b8eb3cea closed for Revert.
      Viewsheet vs = new Viewsheet();
      ChartVSAssembly chart = newChart(vs, VizMark.MODERN_LIGHT);
      vs.addAssembly(chart);

      String state = writeState(vs);
      vs.getSharedFrames();                       // force the shared-frame cache to populate
      ((ChartVSAssemblyInfo) chart.getVSAssemblyInfo()).setVizMark(null);
      vs.parseState(parseXml(state));

      assertTrue(vs.getSharedFrames().isEmpty(),
                 "the sheet's shared frames must be cleared on restore, or the render clones a "
                 + "stale frame in preference to the assembly's re-seeded one");
   }
```

**On `getSharedFrames()` and `isEmpty()`:** read `Viewsheet.clearSharedFrames()` at `:3748` and whatever field it clears, and assert against that field's accessor with whatever emptiness check it actually supports. If the cache has no public accessor, assert the observable instead — re-read the chart's colour frame after the round-trip and assert it carries the legacy palette — and say in your report which you did. Do NOT add a public accessor to `Viewsheet` just to make the test convenient.

- [ ] **Step 2: Run the test to verify it fails**

Run: `./mvnw.cmd test -pl core -Dtest=BookmarkChromeResolutionTest -DfailIfNoTests=false`
Expected: the eight Task 1 tests PASS and this one FAILS — the shared frames survive the restore. If it passes before you make the change, the assertion is not reaching the cache; fix the assertion before proceeding, because a green-on-arrival test here proves nothing.

- [ ] **Step 3: Add the clear**

`Viewsheet.parseState(Element, boolean)` is declared at `:2111`. Find its closing brace — it is the method that ends just before the `getSheet()` javadoc, around `:2314` — and add immediately before the closing brace:

```java
      // a render clones the sheet's shared frame in preference to an assembly's own, so the
      // per-assembly reseed above is not enough on its own: the stale shared frame has to go or a
      // restored palette keeps rendering. Unconditional because restore has no equivalent of
      // revert's target list to test, and both caches rebuild lazily on the next render.
      clearSharedFrames();
      clearDimensionColors();
```

It must be after **both** child passes — the main loop around `:2211-2216` and the second pass over `clist` around `:2254`/`:2266`. Placing it before either leaves the later-parsed assemblies' frames stale.

- [ ] **Step 4: Run the test to verify it passes**

Run: `./mvnw.cmd test -pl core -Dtest=BookmarkChromeResolutionTest -DfailIfNoTests=false`
Expected: PASS, 9 tests.

- [ ] **Step 5: Measure the cost the spec refuses to assume**

The spec requires this rather than permitting it: the clear now runs on every bookmark open, every viewsheet refresh and every embedded-viewsheet refresh.

Time `Viewsheet.parseState` with and without the two clear calls, on a sheet holding at least eight charts with bound colour aesthetics. A JUnit timing harness is acceptable — build the sheet, round-trip its state 50 times, report the wall-clock difference.

Record the numbers in your report. Then judge, and say which:
- **Under a few milliseconds per restore:** the unconditional clear stands. Say so and move on.
- **Materially more:** report DONE_WITH_CONCERNS and recommend the fallback the spec names — have `reseedAfterRestore` return a boolean saying whether it touched anything, accumulate that across the children in `Viewsheet.parseState`, and clear only when true. Do NOT implement the fallback in this task; it changes Task 1's produced interface and belongs in its own task.

- [ ] **Step 6: Run the full core suite**

Run: `./mvnw.cmd test -pl core`
Expected: PASS. The baseline before this plan is **5154 run, 0 failures, 0 errors, 67 skipped**; expect that plus your new tests. Compare against the baseline rather than against zero failures, and report the actual counts. **Run it alone** — running it concurrently with the frontend suite produced a spurious 136-error `SecurityEngine`/`PropertiesEngine` cascade earlier in this work.

**If the suite regresses, STOP and report BLOCKED with the failing names.** Do not adjust a test.

- [ ] **Step 7: Commit**

```bash
git add core/src/main/java/inetsoft/uql/viewsheet/Viewsheet.java core/src/test/java/inetsoft/uql/viewsheet/BookmarkChromeResolutionTest.java
git commit -m "fix(viewsheet): clear the sheet's shared colour frames when its state is restored"
```

---

### Task 3: The manual pass

**Files:** none modified. This task is the gate.

**Interfaces:**
- Consumes: Tasks 1 and 2.
- Produces: a signed-off change set.

Every value here is server-rendered and the automated gates prove the code and the tests agree with each other, not that a screen agrees with either. Two of these rows cannot be unit-tested at all — the refresh paths need a running server.

- [ ] **Step 1: Clean cross-module build**

Run: `./mvnw.cmd clean install -DskipTests "-Pcommunity,enterprise"`
Expected: BUILD SUCCESS. `clean` rather than incremental: a 77-module incremental `install` reported SUCCESS on a cross-module signature break earlier in this work and only `clean` exposed it.

- [ ] **Step 2: The render checks**

Start the server and work through the table. A row that cannot be produced is a finding, not a skip.

| # | Setup | Expect |
|---|---|---|
| B1 | Mark a dashboard's charts, take a bookmark, **Revert** the dashboard, restore the bookmark | chrome stays legacy — square corners, legacy palette, no bar-corner rounding. **This is the defect; if it fails, nothing else matters** |
| B2 | Take a bookmark on an **unmarked** dashboard, **Modernize**, restore the bookmark | chrome is modern — card radius, modern palette, rounded bar corners |
| B3 | B1's restored dashboard, checked specifically on **colour** | the palette is legacy. This is the row Task 2 exists for; a per-assembly-only fix passes B1's corners and fails here |
| B4 | An **unmarked** dashboard: bookmark, restore, no Modernize or Revert in between | pixel-identical. The governing constraint |
| B5 | A dashboard where an author set their **own** border colour, bookmarked and restored on a marked assembly | the author's colour survives |
| B6 | A **table** and a **crosstab**, marked, bookmarked, Reverted, restored | border colour, card radius and background all legacy. These are the widest case — `setFormatInfo` replaces the whole object |
| B7 | A bookmark taken **before this branch existed** at all, restored now | chrome follows the assembly's current mark. The most common real-world case at release |
| B8 | Refresh a dashboard holding one marked and one reverted chart (`VSEventUtil.updateViewsheet` path) | neither changes appearance. Not in decision 10, fixed here as a side effect |
| B9 | A dashboard containing an **embedded viewsheet**, refreshed | the embedded sheet's charts keep their own marks' chrome |
| B10 | Open a bookmark on a chart-heavy dashboard and note whether it feels slower | no perceptible delay. The subjective companion to Task 2's measurement |

- [ ] **Step 3: Record the outcome**

Add a dated line to the spec (`docs/superpowers/specs/2026-08-27-bookmark-chrome-resolution-design.md`) stating which rows passed. If B10 or Task 2's measurement suggests a real cost, record it there as the trigger for the accumulate-a-flag fallback rather than acting on it here.

- [ ] **Step 4: Commit the record**

```bash
git add docs/superpowers/specs/2026-08-27-bookmark-chrome-resolution-design.md
git commit -m "docs(bookmark): record the chrome-resolution render pass"
```

---

## Out of scope, and why

- **Converting the seeded values to read-time resolution.** Assessed per value and rejected in the spec's §3. The palette is copied into several stores and the format values are read at dozens of points. Do not re-open it inside this plan.
- **The unconditional-clear fallback** (accumulate a flag from `reseedAfterRestore`). Only if Task 2's measurement says so, and then as its own task, because it changes Task 1's produced interface.
- **The two accepted gate-read costs**, `AbstractChartInfo.getTooltipStyle` and `VSChartInteractionDefaults.isInlineSvg`. Neither is seeded, so neither is in state.
- **Unconditional creation defaults.** `seedChromeDefaults` is documented never to touch them (`VSAssemblyInfo:1245-1246`), so restore cannot corrupt them and re-seeding cannot fix them.
