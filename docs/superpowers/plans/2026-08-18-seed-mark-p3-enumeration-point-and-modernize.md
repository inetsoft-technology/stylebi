# Seed Mark P3 — The Enumeration Point and Modernize — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Extract the gate-dependent creation seeds into one virtual `seedChromeDefaults(VizContext)` hook that both creation and a new composer-only Modernize action call, so an existing dashboard has a route to modern chrome — without changing what anything renders until someone presses the button.

**Architecture:** Today five gate-dependent seed values are computed inline in `VSAssemblyInfo.setDefaultFormat` and three subclass overrides. They move, unchanged, into a virtual hook called as the last statement of `setDefaultFormat`'s three-argument body. Because the hook mutates the composites already installed rather than installing new ones, it is safe to call a second time on an assembly that already exists — which is what Modernize does, per dashboard, after stamping each unmarked assembly with `VizMark.fromGate()`. Reads stay on `VizContext.ofGate()` throughout, so nobody's rendering changes unless they run the action. The composer learns a dashboard is modernizable from one boolean already-refreshed on every `SetViewsheetInfoCommand`, and offers the action through a dismissable bar plus a permanent canvas context-menu entry.

**Tech Stack:** Java 21, JUnit 5 (`@Tag("core")`, Spring `@ContextConfiguration` + `@SreeHome`, Mockito), Maven via `./mvnw` from `community/`. Angular 21 standalone components, Vitest via `npx ng test portal`, testing-library specs via `npx ng run portal:test-tl`.

**Spec:** [`docs/superpowers/specs/2026-08-14-seed-mark-forward-half-design.md`](../specs/2026-08-14-seed-mark-forward-half-design.md) — §4 (the enumeration point and Modernize) and §5's P3. **§4 carries five corrections dated 2026-08-18 that supersede its original text; they are what this plan implements.** Product decisions: [`seeded-value-reversibility-decisions.md`](../specs/lookfeel/seeded-value-reversibility-decisions.md) decisions 3, 5 and 11.

## Global Constraints

- **Nothing reads the mark to decide rendering.** Every read stays `VizContext.ofGate()`. Flipping reads to `of(info)` is P4 and must not creep in here.
- **The hook never installs a composite.** No `fmtInfo.setFormat(path, new VSCompositeFormat())`, no `setFormat(new …)`. It mutates what is already installed at OBJECTPATH and TITLEPATH. Installing one drops an author's USER tier, which is the defect this phase exists to avoid.
- **Only gate-dependent values move into the hook.** The five in §4's table. Unconditional creation defaults — chart padding, `LegendsDescriptor.setRoundCorners(true)`, `setTableStyleValue(DEFAULT_STYLE)`, fonts, title background and alignment — stay in `setDefaultFormat`. Modernize re-running those would reset an author's choices.
- **Author-provenance flags are never touched:** `userTitleHeight`, `userDataRowHeight`, `userHeaderRowHeight`, `userCellHeight`. Modernize does not read or write them.
- **Modernize is composer-only, gate-on only, per-dashboard, one undo step, no bulk path** (decision 5). Never the viewer, never composer preview.
- **Modernize touches only unmarked assemblies**, so a second run is a no-op (decision 3).
- **Preserve the title-border ordering oddity.** The title border colour takes the pre-stylesheet `bcolors`; the `format.css` TableStyle override applies only to the object border and radius. This predates all of this work — preserve it, do not "fix" it.
- Mutate installed composites via `fmtInfo.getFormat(path)` (one-arg). **Never `getFormat(path, false)`** — with `shrink=false` that method may return a *new* merged `VSCompositeFormat` for a non-OBJECTPATH path (`FormatInfo.java:233-248`), so writes would land on a throwaway object.
- New i18n labels go in `core/src/main/resources/inetsoft/util/srinter.properties`, referenced as `_#(key)` in HTML and `_#(js:key)` in TypeScript.
- Copy the AGPL header from a sibling file into every new file.
- Java style in this tree: three-space indent, `if(cond) {` with no space before the paren.

---

## File Structure

**Created:**

| File | Responsibility |
|---|---|
| `core/src/test/java/inetsoft/uql/viewsheet/internal/SeedChromeDefaultsTest.java` | The characterization net for every creation seed, then the hook's own contract. The safety argument for Tasks 2 and 3 lives here. |
| `core/src/main/java/inetsoft/uql/viewsheet/internal/VizModernizeUtil.java` | The enumeration and the stamp-then-seed loop. Pure model, no Spring, no web types. Lives in `internal` because `seedChromeDefaults` is `protected`. |
| `core/src/test/java/inetsoft/uql/viewsheet/internal/VizModernizeUtilTest.java` | Mixed dashboards, idempotence, author flags, embedded children, gate-off no-op. |
| `core/src/main/java/inetsoft/web/composer/vs/controller/ModernizeViewsheetController.java` | One `@Undoable` STOMP endpoint, no payload. |
| `core/src/main/java/inetsoft/web/composer/vs/controller/ModernizeViewsheetService.java` | `@ClusterProxy` service: permission guard, gate guard, the util call, then refresh. |
| `core/src/test/java/inetsoft/web/composer/vs/controller/ModernizeViewsheetServiceTest.java` | The guards and the refresh, with mocked engine/rvs/dispatcher. |
| `web/projects/portal/src/app/composer/gui/vs/editor/modernize-bar.component.ts` / `.html` / `.scss` | The dismissable offer. Standalone, dumb: two inputs, two outputs, no services. |
| `web/projects/portal/src/app/composer/gui/vs/editor/modernize-bar.component.spec.ts` | Rendering, the action output, the dismiss output. |

**Modified:**

| File | Change |
|---|---|
| `core/src/main/java/inetsoft/uql/viewsheet/internal/VSAssemblyInfo.java` | Extract `seedChromeDefaults(VizContext)`; `setDefaultFormat` stops computing gate-dependent values. |
| `.../internal/ChartVSAssemblyInfo.java` | Hook override: card background + the four `PlotDescriptor` seeds. |
| `.../internal/TableDataVSAssemblyInfo.java` | Hook override: card background. |
| `.../internal/ViewsheetVSAssemblyInfo.java` | Hook override: page background. Its `setDefaultFormat(boolean)` override is deleted. |
| `core/src/main/java/inetsoft/web/viewsheet/service/CoreLifecycleService.java` | `infoMap.put("modernizable", …)` in `setViewsheetInfo`. |
| `core/src/main/resources/inetsoft/util/srinter.properties` | Four labels. |
| `web/projects/portal/src/app/composer/data/vs/viewsheet.ts` | `modernizable` and `modernizeBarDismissed`. |
| `.../composer/gui/vs/editor/viewsheet-pane.component.ts` | Read the flag, send the event, add the context-menu entry, import the bar. |
| `.../composer/gui/vs/editor/viewsheet-pane.component.html` | Render the bar. |
| `.../composer/gui/vs/editor/viewsheet-pane.component.interaction.tl.spec.ts` | Flag plumbing and menu-visibility tests. |
| `docs/superpowers/specs/2026-08-14-seed-mark-forward-half-design.md` | P3 marked built; open item 4 answered. |
| `docs/superpowers/specs/lookfeel/chart-card-roadmap.md` | Done row; re-derive "what to pick up next". |

**Generated, do not write by hand:** `ModernizeViewsheetServiceProxy` is produced by the `@ClusterProxy` annotation processor into `core/target/generated-sources/annotations/…`. It will not exist until the first build after Task 6's service is created, so the controller will not compile until then — that is expected, and it is why the controller and service land in the same task.

---

## Task 1: The characterization net

The refactor in Tasks 2 and 3 must not change a single created value. This task builds the net that proves it. **These tests pass against unmodified `HEAD` — that is their purpose.** If one fails here, the assertion is wrong about today's behaviour: fix the assertion, never the production code.

**Files:**
- Test: `core/src/test/java/inetsoft/uql/viewsheet/internal/SeedChromeDefaultsTest.java`

**Interfaces:**
- Consumes: `VizContext.ofGate()`, `VSObjectChromeDefaults.{objectBorderColor,cardCornerRadius,cardBackgroundCss,pageBackgroundCss}`, `VSAssemblyInfo.{DEFAULT_BORDER_COLOR,OBJECTPATH,TITLEPATH}` — all existing.
- Produces: nothing. Tasks 2 and 3 re-run this file unchanged.

- [ ] **Step 1: Write the characterization tests**

Create the file, copying the AGPL header from `core/src/test/java/inetsoft/uql/viewsheet/internal/VizContextTest.java`.

```java
package inetsoft.uql.viewsheet.internal;

import inetsoft.sree.SreeEnv;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.graph.PlotDescriptor;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.Color;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The creation seeds, pinned. Written before the seedChromeDefaults extraction as its safety net:
 * every assertion here describes behaviour that must survive the refactor unchanged.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class SeedChromeDefaultsTest {
   @AfterEach
   void reset() {
      SreeEnv.setProperty("viewsheet.modernVisualization", null);
      SreeEnv.setProperty("viewsheet.darkMode", null);
      SreeEnv.setProperty("viewsheet.density", null);
   }

   private void gateOn() {
      SreeEnv.setProperty("viewsheet.modernVisualization", "true");
   }

   private void gateOff() {
      SreeEnv.setProperty("viewsheet.modernVisualization", "false");
   }

   private static VSFormat objectDefault(VSAssemblyInfo info) {
      return info.getFormatInfo().getFormat(VSAssemblyInfo.OBJECTPATH).getDefaultFormat();
   }

   private static VSCompositeFormat titleFormat(VSAssemblyInfo info) {
      return info.getFormatInfo().getFormat(VSAssemblyInfo.TITLEPATH);
   }

   private TableVSAssemblyInfo newTable() {
      Viewsheet vs = new Viewsheet();
      TableVSAssembly table = new TableVSAssembly(vs, "Table1");
      table.getVSAssemblyInfo().initDefaultFormat();
      return (TableVSAssemblyInfo) table.getVSAssemblyInfo();
   }

   private ChartVSAssemblyInfo newChart() {
      Viewsheet vs = new Viewsheet();
      ChartVSAssembly chart = new ChartVSAssembly(vs, "Chart1");
      chart.getVSAssemblyInfo().initDefaultFormat();
      return (ChartVSAssemblyInfo) chart.getVSAssemblyInfo();
   }

   private TextVSAssemblyInfo newText() {
      Viewsheet vs = new Viewsheet();
      TextVSAssembly text = new TextVSAssembly(vs, "Text1");
      text.getVSAssemblyInfo().initDefaultFormat();
      return (TextVSAssemblyInfo) text.getVSAssemblyInfo();
   }

   private SelectionListVSAssemblyInfo newSelectionList() {
      Viewsheet vs = new Viewsheet();
      SelectionListVSAssembly list = new SelectionListVSAssembly(vs, "Selection1");
      list.getVSAssemblyInfo().initDefaultFormat();
      return (SelectionListVSAssemblyInfo) list.getVSAssemblyInfo();
   }

   // ---- object border colour and card radius -------------------------------------------------

   @Test
   void gateOffTableTakesTheLegacyBorderAndNoRadius() {
      gateOff();
      VSFormat fmt = objectDefault(newTable());
      assertEquals(VSAssemblyInfo.DEFAULT_BORDER_COLOR,
                   fmt.getBorderColorsValue().topColor,
                   "gate off keeps the legacy 0xDADADA object border");
      assertEquals(0, fmt.getRoundCornerValue(), "gate off seeds square corners");
   }

   @Test
   void gateOnTableTakesTheModernBorderAndTheCardRadius() {
      gateOn();
      VizContext ctx = VizContext.ofGate();
      VSFormat fmt = objectDefault(newTable());
      assertEquals(VSObjectChromeDefaults.objectBorderColor(ctx),
                   fmt.getBorderColorsValue().topColor);
      assertEquals(VSObjectChromeDefaults.cardCornerRadius(), fmt.getRoundCornerValue());
   }

   @Test
   void aTextAssemblyIsNotACornerSeedTargetEvenUnderTheGate() {
      gateOn();
      VizContext ctx = VizContext.ofGate();
      VSFormat fmt = objectDefault(newText());
      assertEquals(VSObjectChromeDefaults.objectBorderColor(ctx),
                   fmt.getBorderColorsValue().topColor,
                   "the border colour reaches every type");
      assertEquals(0, fmt.getRoundCornerValue(),
                   "isCornerSeedTarget() excludes outputs, so the radius does not");
   }

   @Test
   void aSelectionListIsACornerSeedTarget() {
      gateOn();
      assertEquals(VSObjectChromeDefaults.cardCornerRadius(),
                   objectDefault(newSelectionList()).getRoundCornerValue());
   }

   // ---- the title border, and who wins the title composite -----------------------------------

   @Test
   void aTableTitleTakesTheModernBorderColour() {
      gateOn();
      VizContext ctx = VizContext.ofGate();
      VSCompositeFormat title = titleFormat(newTable());
      assertNotNull(title, "a table is titled, so a TITLEPATH composite is installed");
      assertNotNull(title.getDefaultFormat().getBordersValue(),
                    "a table creates with border = true, so the title carries border insets");
      assertEquals(VSObjectChromeDefaults.objectBorderColor(ctx),
                   title.getDefaultFormat().getBorderColorsValue().topColor);
   }

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

   // ---- backgrounds ---------------------------------------------------------------------------

   @Test
   void aChartCardTakesTheCardBackground() {
      gateOn();
      assertEquals(VSObjectChromeDefaults.cardBackgroundCss(VizContext.ofGate()),
                   objectDefault(newChart()).getBackgroundValue());
   }

   @Test
   void aTableCardTakesTheCardBackground() {
      gateOn();
      assertEquals(VSObjectChromeDefaults.cardBackgroundCss(VizContext.ofGate()),
                   objectDefault(newTable()).getBackgroundValue());
   }

   @Test
   void theSheetTakesThePageBackgroundUnderTheGateAndTheLegacyGreyWithout() {
      gateOn();
      assertEquals(VSObjectChromeDefaults.pageBackgroundCss(VizContext.ofGate()),
                   objectDefault(new Viewsheet().getVSAssemblyInfo()).getBackgroundValue());

      gateOff();
      assertEquals("#f5f5f5",
                   objectDefault(new Viewsheet().getVSAssemblyInfo()).getBackgroundValue());
   }

   // ---- the chart's plot seeds -----------------------------------------------------------------

   @Test
   void gateOnChartTakesTheFourPlotSeeds() {
      // the *Value() accessors read the stored seed; getBarCornerRadius()/isSmoothLines() are
      // effective getters that consult the gate themselves, which is a P4 concern, not this one
      gateOn();
      PlotDescriptor plot = newChart().getChartDescriptor().getPlotDescriptor();
      assertEquals(0.3, plot.getBarCornerRadiusValue(), 0.0001);
      assertTrue(plot.isModernCornerSeed());
      assertTrue(plot.isSmoothLinesValue());
      assertTrue(plot.isModernSmoothSeed());
   }

   @Test
   void gateOffChartTakesNoPlotSeedsButKeepsTheUnconditionalOnes() {
      gateOff();
      ChartVSAssemblyInfo info = newChart();
      PlotDescriptor plot = info.getChartDescriptor().getPlotDescriptor();
      assertEquals(0.0, plot.getBarCornerRadiusValue(), 0.0001);
      assertFalse(plot.isModernCornerSeed());
      assertFalse(plot.isSmoothLinesValue());
      assertFalse(plot.isModernSmoothSeed());
      assertTrue(info.getChartDescriptor().getLegendsDescriptor().isRoundCorners(),
                 "round legend corners are unconditional and must stay in setDefaultFormat");
      assertEquals(10, info.getPadding().top,
                   "chart padding is unconditional and must stay in setDefaultFormat");
   }
}
```

- [ ] **Step 2: Run the net against unmodified code**

```bash
cd E:/StyleBI/stylebi-enterprise/community
./mvnw test -pl core -Dtest=SeedChromeDefaultsTest
```

Expected: **PASS**, all twelve. Any failure means an assertion mis-describes today's behaviour — correct the assertion, never the production code. The accessors used here were checked at `119bfdaac`: `BorderColors` exposes public `topColor`/`bottomColor`/`leftColor`/`rightColor` fields (`:181-184`), `getPadding()` returns `Insets` (`VSAssemblyInfo:1295`), and `getTableStyleValue()` lives on `TableDataVSAssemblyInfo:90`.

- [ ] **Step 3: Commit**

```bash
git add core/src/test/java/inetsoft/uql/viewsheet/internal/SeedChromeDefaultsTest.java
git commit -m "test(viewsheet): pin the gate-dependent creation seeds before extracting them"
```

---

## Task 2: Extract `seedChromeDefaults(VizContext)` in the base

**Files:**
- Modify: `core/src/main/java/inetsoft/uql/viewsheet/internal/VSAssemblyInfo.java:1184-1256` (`setDefaultFormat(boolean,boolean,boolean)`)
- Test: `core/src/test/java/inetsoft/uql/viewsheet/internal/SeedChromeDefaultsTest.java` (append)

**Interfaces:**
- Consumes: Task 1's net, unchanged.
- Produces: `protected void seedChromeDefaults(VizContext ctx)` on `VSAssemblyInfo` — mutates the composites installed at `OBJECTPATH` and `TITLEPATH`, installs nothing, returns nothing. Tasks 3 and 4 override and call it.

- [ ] **Step 1: Write the failing tests for the new capability**

Append to `SeedChromeDefaultsTest`:

```java
   // ---- the hook, called a second time on an assembly that already exists ---------------------

   @Test
   void theHookModernizesAnAssemblyCreatedUnderAClosedGate() {
      gateOff();
      TableVSAssemblyInfo info = newTable();
      assertEquals(0, objectDefault(info).getRoundCornerValue());

      gateOn();
      VizContext ctx = VizContext.ofGate();
      info.seedChromeDefaults(ctx);

      assertEquals(VSObjectChromeDefaults.objectBorderColor(ctx),
                   objectDefault(info).getBorderColorsValue().topColor);
      assertEquals(VSObjectChromeDefaults.cardCornerRadius(),
                   objectDefault(info).getRoundCornerValue());
      assertEquals(VSObjectChromeDefaults.objectBorderColor(ctx),
                   titleFormat(info).getDefaultFormat().getBorderColorsValue().topColor,
                   "the title border colour follows too");
   }

   @Test
   void theHookMutatesInPlaceAndLeavesTheUserTierAlone() {
      gateOff();
      TableVSAssemblyInfo info = newTable();
      VSCompositeFormat objBefore = info.getFormatInfo().getFormat(VSAssemblyInfo.OBJECTPATH);
      VSCompositeFormat titleBefore = info.getFormatInfo().getFormat(VSAssemblyInfo.TITLEPATH);
      objBefore.getUserDefinedFormat().setBackgroundValue("0x123456");
      titleBefore.getUserDefinedFormat().setForegroundValue("0x654321");

      gateOn();
      info.seedChromeDefaults(VizContext.ofGate());

      assertSame(objBefore, info.getFormatInfo().getFormat(VSAssemblyInfo.OBJECTPATH),
                 "the object composite is mutated, never replaced");
      assertSame(titleBefore, info.getFormatInfo().getFormat(VSAssemblyInfo.TITLEPATH),
                 "the title composite is mutated, never replaced");
      assertEquals("0x123456", objBefore.getUserDefinedFormat().getBackgroundValue(),
                   "an author's object background survives");
      assertEquals("0x654321", titleBefore.getUserDefinedFormat().getForegroundValue(),
                   "an author's title foreground survives");
   }

   @Test
   void theHookIsIdempotent() {
      gateOn();
      TableVSAssemblyInfo info = newTable();
      int radius = objectDefault(info).getRoundCornerValue();
      Color border = objectDefault(info).getBorderColorsValue().topColor;

      info.seedChromeDefaults(VizContext.ofGate());

      assertEquals(radius, objectDefault(info).getRoundCornerValue());
      assertEquals(border, objectDefault(info).getBorderColorsValue().topColor);
   }

   @Test
   void theHookLeavesAnUnborderedTitleUncoloured() {
      // a selection list creates with border = false, so no title border exists to colour
      gateOn();
      SelectionListVSAssemblyInfo info = newSelectionList();
      info.seedChromeDefaults(VizContext.ofGate());
      VSCompositeFormat title = titleFormat(info);
      assertNull(title.getDefaultFormat().getBordersValue());
      assertEquals(new Color(0xc0c0c0),
                   title.getDefaultFormat().getBorderColorsValue().bottomColor,
                   "the selection family's own title border colour is untouched");
   }
```

`getUserDefinedFormat()` is the USER tier accessor (`VSCompositeFormat:547`), `getDefaultFormat()` the DEFAULT one (`:533`). The USER tier is the whole point of the second test — do not weaken it to a DEFAULT-tier assertion if it proves awkward.

- [ ] **Step 2: Run to verify it fails**

```bash
./mvnw test -pl core -Dtest=SeedChromeDefaultsTest
```

Expected: **compilation failure** — `cannot find symbol: method seedChromeDefaults(VizContext)`.

- [ ] **Step 3: Extract the hook**

In `VSAssemblyInfo.java`, replace the body of `setDefaultFormat(boolean border, boolean setFormat, boolean fill)` so it no longer computes any gate-dependent value, and add the hook immediately after it. The four things that leave the old body: the `VizContext`/`defBorderColor`/`bcolors`/`borderRadius` computation, the `titlefmt.setBorderColorsValue(bcolors)` line, the two `format.css` override branches, and `objfmt.setBorderColorsValue`/`setRoundCornerValue`. Everything else stays exactly where it is.

```java
   protected void setDefaultFormat(boolean border, boolean setFormat, boolean fill) {
      VSCompositeFormat format = new VSCompositeFormat();
      VSCompositeFormat tformat = new VSCompositeFormat();
      VSFormat objfmt = format.getDefaultFormat();
      VSFormat titlefmt = tformat.getDefaultFormat();

      Insets borders = null;

      if(border) {
         borders = new Insets(StyleConstants.THIN_LINE,
                              StyleConstants.THIN_LINE,
                              StyleConstants.THIN_LINE,
                              StyleConstants.THIN_LINE);

         titlefmt.setBordersValue(borders);
      }

      if(setFormat) {
         objfmt.setBordersValue(borders);
         objfmt.setFontValue(getDefaultFont(Font.PLAIN, 11));

         if(fill) {
            objfmt.setBackgroundValue("0xffffff");
         }

         VSCSSFormat objCssFmt = format.getCSSFormat();

         if(getObjCSSType() != null) {
            objCssFmt.setCSSType(getObjCSSType());
         }

         setFormat(format);
      }

      if(this instanceof TitledVSAssemblyInfo) {
         titlefmt.setBackgroundValue(DEFAULT_TITLE_BG);
         titlefmt.setAlignmentValue(StyleConstants.H_LEFT | StyleConstants.V_CENTER);
         titlefmt.setFontValue(getTitleDefaultFont());
         VSCSSFormat cssfmt = tformat.getCSSFormat();

         if(getObjCSSType() != null) {
            cssfmt.setCSSType(getObjCSSType() + CSSConstants.TITLE);
         }

         fmtInfo.setFormat(TITLEPATH, tformat);
      }

      setCSSDefaults();
      // last, so subclass seeds that run after super() see a fully built format, and so the
      // stylesheet resize in setCSSDefaults() is never re-run by Modernize
      seedChromeDefaults(VizContext.ofGate());
   }

   /**
    * Seed the chrome values whose default depends on the modern-visualization context: the object
    * border colour and card radius here, the card or page background and the chart plot values in
    * the overrides. Called once at creation and again by Modernize on an assembly that already
    * exists, so it must mutate the composites already installed and never install one - installing
    * a fresh composite leaves the new one's USER tier empty and drops an author's formatting.
    *
    * Only gate-dependent values belong here. Unconditional creation defaults stay in
    * setDefaultFormat, or Modernize would reset an author's padding, table style and fonts.
    */
   protected void seedChromeDefaults(VizContext ctx) {
      Color defBorderColor = ctx.modern
         ? VSObjectChromeDefaults.objectBorderColor(ctx) : DEFAULT_BORDER_COLOR;
      BorderColors bcolors = new BorderColors(defBorderColor, defBorderColor,
                                              defBorderColor, defBorderColor);
      int borderRadius = ctx.modern && isCornerSeedTarget()
         ? VSObjectChromeDefaults.cardCornerRadius() : 0;
      VSCompositeFormat titleFormat = fmtInfo.getFormat(TITLEPATH);

      // the title border takes the pre-stylesheet colour: a table's stylesheet colour reaches the
      // object border and never the title border. Predates this work - preserved deliberately.
      if(titleFormat != null && titleFormat.getDefaultFormat().getBordersValue() != null) {
         titleFormat.getDefaultFormat().setBorderColorsValue(bcolors);
      }

      if(this instanceof TableDataVSAssemblyInfo) {
         CSSStyle style = CSSDictionary.getDictionary().getStyle(
            new CSSParameter("TableStyle", null, null, new CSSAttr("region", "Table")));

         if(style != null && style.isBorderColorDefined()) {
            bcolors = style.getBorderColors();
         }

         if(style != null && style.isBorderRadiusDefined()) {
            borderRadius = style.getBorderRadius();
         }
      }

      VSCompositeFormat objFormat = getFormat();

      if(objFormat != null) {
         objFormat.getDefaultFormat().setBorderColorsValue(bcolors);
         objFormat.getDefaultFormat().setRoundCornerValue(borderRadius);
      }
   }
```

Two equivalences this relies on, both checked at `119bfdaac` — state them in the commit message:

- The `format.css` branch was inside `if(border)`; here it is keyed on the type alone. Every `TableDataVSAssemblyInfo` subtype creates with `border` true — `TableVSAssemblyInfo:75`, `CrosstabVSAssemblyInfo:61`, `CalcTableVSAssemblyInfo:66`, `CrossBaseVSAssemblyInfo:57`, and `EmbeddedTableVSAssemblyInfo`, which inherits `TableVSAssemblyInfo`'s — so `table` implies `border` and the branch fires in exactly the same cases.
- The object border colour and radius were inside `if(setFormat)`. No caller anywhere in `core/src` passes `setFormat` false, so writing them through `getFormat()` reaches the same composite.

- [ ] **Step 4: Run the full file — net plus new tests**

```bash
./mvnw test -pl core -Dtest=SeedChromeDefaultsTest
```

Expected: **PASS**, all sixteen. A failure in one of Task 1's twelve means the extraction changed a created value — fix the extraction, not the net.

- [ ] **Step 5: Run the neighbouring suites the base class feeds**

```bash
./mvnw test -pl core -Dtest='VSAssemblyInfoVizMarkTest,VizContextTest,VSDensityDefaultsTest,VSObjectChromeDefaultsTest,TitleInfoTest'
```

Expected: PASS.

- [ ] **Step 6: Compile**

```bash
./mvnw clean install -pl core -DskipTests -o
```

Expected: BUILD SUCCESS.

- [ ] **Step 7: Commit**

```bash
git add core/src/main/java/inetsoft/uql/viewsheet/internal/VSAssemblyInfo.java \
        core/src/test/java/inetsoft/uql/viewsheet/internal/SeedChromeDefaultsTest.java
git commit -m "refactor(viewsheet): seed gate-dependent chrome from one virtual hook"
```

---

## Task 3: The three subclass hook overrides

**Files:**
- Modify: `core/src/main/java/inetsoft/uql/viewsheet/internal/ChartVSAssemblyInfo.java:86-108`
- Modify: `core/src/main/java/inetsoft/uql/viewsheet/internal/TableDataVSAssemblyInfo.java:1575-1587`
- Modify: `core/src/main/java/inetsoft/uql/viewsheet/internal/ViewsheetVSAssemblyInfo.java:237-243`
- Test: `core/src/test/java/inetsoft/uql/viewsheet/internal/SeedChromeDefaultsTest.java` (append)

**Interfaces:**
- Consumes: `VSAssemblyInfo.seedChromeDefaults(VizContext)` from Task 2.
- Produces: overrides of the same signature on the three types. `ViewsheetVSAssemblyInfo.setDefaultFormat(boolean)` no longer exists — nothing outside the class called it.

- [ ] **Step 1: Write the failing tests**

Append to `SeedChromeDefaultsTest`:

```java
   @Test
   void theHookModernizesALegacyChartCompletely() {
      gateOff();
      ChartVSAssemblyInfo info = newChart();
      Insets paddingBefore = info.getPadding();

      gateOn();
      VizContext ctx = VizContext.ofGate();
      info.seedChromeDefaults(ctx);

      assertEquals(VSObjectChromeDefaults.cardBackgroundCss(ctx),
                   objectDefault(info).getBackgroundValue());
      PlotDescriptor plot = info.getChartDescriptor().getPlotDescriptor();
      assertEquals(0.3, plot.getBarCornerRadiusValue(), 0.0001, "the plot seeds are reachable");
      assertTrue(plot.isModernCornerSeed(), "and are recorded as seeds, not author choices");
      assertTrue(plot.isSmoothLinesValue());
      assertTrue(plot.isModernSmoothSeed());
      assertEquals(paddingBefore, info.getPadding(),
                   "padding is not a gate-dependent seed and must not be re-applied");
   }

   @Test
   void theHookModernizesALegacyTableCard() {
      gateOff();
      TableVSAssemblyInfo info = newTable();
      String styleBefore = info.getTableStyleValue();

      gateOn();
      VizContext ctx = VizContext.ofGate();
      info.seedChromeDefaults(ctx);

      assertEquals(VSObjectChromeDefaults.cardBackgroundCss(ctx),
                   objectDefault(info).getBackgroundValue());
      assertEquals(styleBefore, info.getTableStyleValue(),
                   "the default table style is not a gate-dependent seed");
   }

   @Test
   void theHookModernizesALegacySheetBackground() {
      gateOff();
      Viewsheet vs = new Viewsheet();
      assertEquals("#f5f5f5", objectDefault(vs.getVSAssemblyInfo()).getBackgroundValue());

      gateOn();
      VizContext ctx = VizContext.ofGate();
      vs.getVSAssemblyInfo().seedChromeDefaults(ctx);

      assertEquals(VSObjectChromeDefaults.pageBackgroundCss(ctx),
                   objectDefault(vs.getVSAssemblyInfo()).getBackgroundValue());
   }

   @Test
   void theHookRevertsChromeWhenGivenALegacyContext() {
      // the ternaries live in the hook, so a legacy context writes the legacy values. Nothing
      // calls it that way in P3 - this pins the contract P4's flip will depend on.
      gateOn();
      TableVSAssemblyInfo info = newTable();

      info.seedChromeDefaults(VizContext.LEGACY);

      assertEquals(VSAssemblyInfo.DEFAULT_BORDER_COLOR,
                   objectDefault(info).getBorderColorsValue().topColor);
      assertEquals(0, objectDefault(info).getRoundCornerValue());
   }
```

- [ ] **Step 2: Run to verify it fails**

```bash
./mvnw test -pl core -Dtest=SeedChromeDefaultsTest
```

Expected: FAIL — `theHookModernizesALegacyChartCompletely` fails on the background or the plot values, `theHookModernizesALegacyTableCard` on the background, `theHookModernizesALegacySheetBackground` on the background. The seeds still live in `setDefaultFormat`, so the hook does not reach them yet.

- [ ] **Step 3: Move the chart's seeds into an override**

In `ChartVSAssemblyInfo.java`, the override keeps its unconditional work and loses the two gate-dependent pieces:

```java
   @Override
   protected void setDefaultFormat(boolean border, boolean setFormat, boolean fill) {
      setPadding(new Insets(10, 10, 10, 10));
      super.setDefaultFormat(border, setFormat, fill);
      // Enable round corners by default for newly created charts.
      // Existing charts loaded from XML default to false for backward compatibility.
      getChartDescriptor().getLegendsDescriptor().setRoundCorners(true);

      VSCompositeFormat tFormat = new VSCompositeFormat();
      tFormat.getCSSFormat().setCSSType(getObjCSSType() + CSSConstants.TITLE);
      tFormat.getDefaultFormat().setFontValue(getDefaultFont(Font.BOLD, 11));
      tFormat.getDefaultFormat().setAlignmentValue(StyleConstants.H_LEFT | StyleConstants.V_CENTER);
      getFormatInfo().setFormat(TITLEPATH, tFormat);
   }

   @Override
   protected void seedChromeDefaults(VizContext ctx) {
      super.seedChromeDefaults(ctx);
      getFormat().getDefaultFormat().setBackgroundValue(
         VSObjectChromeDefaults.cardBackgroundCss(ctx));

      if(ctx.modern) {
         PlotDescriptor plotDesc = getChartDescriptor().getPlotDescriptor();
         plotDesc.setBarCornerRadius(0.3);
         plotDesc.setModernCornerSeed(true);
         plotDesc.setSmoothLines(true);
         plotDesc.setModernSmoothSeed(true);
      }
   }
```

**The four plot statements must keep this order.** `setBarCornerRadius` and `setSmoothLines` each *clear* the matching seed flag — `this.modernCornerSeed = false` at `PlotDescriptor:1337`, `this.modernSmoothSeed = false` at `:651` — because an explicit write means an author chose the value and it should stop tracking the gate. So `setModernCornerSeed(true)` and `setModernSmoothSeed(true)` have to follow their value setters, not precede them. Reversed, the seeds would persist as author choices and the gate-off collapse in `getBarCornerRadius()`/`isSmoothLines()` would stop working. `gateOffChartTakesNoPlotSeedsButKeepsTheUnconditionalOnes` catches the reversal.

The fresh TITLEPATH composite still runs after `super`, so it still discards the base's title border colour — which is what `aChartTitleCarriesNoBorderColourAtAll` pins. Leave that alone.

- [ ] **Step 4: Move the table's seed into an override**

In `TableDataVSAssemblyInfo.java`:

```java
   @Override
   protected void setDefaultFormat(boolean border, boolean setFormat, boolean fill) {
      super.setDefaultFormat(border, setFormat, fill);

      // CSSDictionary.getDictionary() is for viewsheet ONLY
      if(LibManagerProvider.getInstance().getManager().getTableStyle(DEFAULT_STYLE) != null
         && !CSSDictionary.getDictionary().checkPresent("TableStyle"))
      {
         setTableStyleValue(DEFAULT_STYLE);
      }
   }

   @Override
   protected void seedChromeDefaults(VizContext ctx) {
      super.seedChromeDefaults(ctx);
      getFormat().getDefaultFormat().setBackgroundValue(
         VSObjectChromeDefaults.cardBackgroundCss(ctx));
   }
```

- [ ] **Step 5: Replace the sheet's override**

In `ViewsheetVSAssemblyInfo.java`, delete `setDefaultFormat(boolean border)` entirely and add:

```java
   @Override
   protected void seedChromeDefaults(VizContext ctx) {
      super.seedChromeDefaults(ctx);
      getFormat().getDefaultFormat().setBackgroundValue(
         ctx.modern ? VSObjectChromeDefaults.pageBackgroundCss(ctx) : "#f5f5f5");
   }
```

Then confirm nothing called the deleted override:

```bash
grep -rn "setDefaultFormat" core/src/main/java/inetsoft/uql/viewsheet/internal/ViewsheetVSAssemblyInfo.java
```

Expected: no hits.

- [ ] **Step 6: Run the file**

```bash
./mvnw test -pl core -Dtest=SeedChromeDefaultsTest
```

Expected: **PASS**, all twenty.

- [ ] **Step 7: Confirm creation no longer reads the gate outside the hook**

```bash
grep -rn "VizContext.ofGate()" core/src/main/java/inetsoft/uql/viewsheet/internal/VSAssemblyInfo.java \
   core/src/main/java/inetsoft/uql/viewsheet/internal/ChartVSAssemblyInfo.java \
   core/src/main/java/inetsoft/uql/viewsheet/internal/TableDataVSAssemblyInfo.java \
   core/src/main/java/inetsoft/uql/viewsheet/internal/ViewsheetVSAssemblyInfo.java
```

Expected: exactly one hit — the `seedChromeDefaults(VizContext.ofGate())` call in `VSAssemblyInfo.setDefaultFormat`. Four creation-path gate reads collapsed to one; P4 flips that one to `of(this)`.

- [ ] **Step 8: Run the wider suites these three classes feed, then compile**

```bash
./mvnw test -pl core -Dtest='SeedChromeDefaultsTest,VSAssemblyInfoVizMarkTest,ViewsheetVizMarkTest,AbstractVSAssemblyVizMarkTest,VSTableStructureDefaultsTest,VSChartChromeDefaultsTest'
./mvnw clean install -pl core -DskipTests -o
```

Expected: PASS, then BUILD SUCCESS.

- [ ] **Step 9: Commit**

```bash
git add core/src/main/java/inetsoft/uql/viewsheet/internal/ChartVSAssemblyInfo.java \
        core/src/main/java/inetsoft/uql/viewsheet/internal/TableDataVSAssemblyInfo.java \
        core/src/main/java/inetsoft/uql/viewsheet/internal/ViewsheetVSAssemblyInfo.java \
        core/src/test/java/inetsoft/uql/viewsheet/internal/SeedChromeDefaultsTest.java
git commit -m "refactor(viewsheet): move the per-type chrome seeds onto the hook"
```

---

## Task 4: `VizModernizeUtil` — the enumeration and the stamp-then-seed loop

**Files:**
- Create: `core/src/main/java/inetsoft/uql/viewsheet/internal/VizModernizeUtil.java`
- Test: `core/src/test/java/inetsoft/uql/viewsheet/internal/VizModernizeUtilTest.java`

**Interfaces:**
- Consumes: `seedChromeDefaults(VizContext)` (Tasks 2–3), `VizMark.fromGate()`, `VizContext.of(VizMark)`, `Viewsheet.getAssemblies(boolean)`, `VSAssemblyInfo.{getVizMark,setVizMark,isEmbedded}`.
- Produces:
  - `public static boolean hasUnmarked(Viewsheet vs)` — true when the sheet itself or any non-embedded assembly carries no mark.
  - `public static int modernize(Viewsheet vs)` — stamps and seeds every unmarked one, returns how many it touched; returns 0 and touches nothing when the gate is off.

- [ ] **Step 1: Write the failing test**

Create `core/src/test/java/inetsoft/uql/viewsheet/internal/VizModernizeUtilTest.java` with the AGPL header, then:

```java
package inetsoft.uql.viewsheet.internal;

import inetsoft.sree.SreeEnv;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.viewsheet.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class VizModernizeUtilTest {
   @AfterEach
   void reset() {
      SreeEnv.setProperty("viewsheet.modernVisualization", null);
      SreeEnv.setProperty("viewsheet.darkMode", null);
   }

   private void gateOn() {
      SreeEnv.setProperty("viewsheet.modernVisualization", "true");
   }

   private void gateOff() {
      SreeEnv.setProperty("viewsheet.modernVisualization", "false");
   }

   /** A sheet as a legacy asset would load: nothing marked anywhere. */
   private Viewsheet legacySheet() {
      gateOff();
      Viewsheet vs = new Viewsheet();
      vs.addAssembly(new TextVSAssembly(vs, "Text1"));
      vs.addAssembly(new TableVSAssembly(vs, "Table1"));
      return vs;
   }

   @Test
   void aLegacySheetHasUnmarkedContent() {
      assertTrue(VizModernizeUtil.hasUnmarked(legacySheet()));
   }

   @Test
   void aFullyModernSheetHasNone() {
      gateOn();
      Viewsheet vs = new Viewsheet();
      vs.addAssembly(new TextVSAssembly(vs, "Text1"));
      assertFalse(VizModernizeUtil.hasUnmarked(vs));
   }

   @Test
   void modernizeStampsEveryUnmarkedAssemblyAndTheSheet() {
      Viewsheet vs = legacySheet();
      gateOn();

      assertEquals(3, VizModernizeUtil.modernize(vs), "two assemblies plus the sheet itself");

      assertEquals(VizMark.MODERN_LIGHT, vs.getVSAssemblyInfo().getVizMark());

      for(Assembly assembly : vs.getAssemblies(true)) {
         assertEquals(VizMark.MODERN_LIGHT,
                      ((VSAssembly) assembly).getVSAssemblyInfo().getVizMark());
      }

      assertFalse(VizModernizeUtil.hasUnmarked(vs), "and the sheet is no longer modernizable");
   }

   @Test
   void modernizeAlsoAppliesTheSeeds() {
      Viewsheet vs = legacySheet();
      TableVSAssembly table = (TableVSAssembly) vs.getAssembly("Table1");
      assertEquals(0, table.getVSAssemblyInfo().getFormat().getDefaultFormat()
         .getRoundCornerValue(), "legacy: square");

      gateOn();
      VizModernizeUtil.modernize(vs);

      assertEquals(VSObjectChromeDefaults.cardCornerRadius(),
                   table.getVSAssemblyInfo().getFormat().getDefaultFormat().getRoundCornerValue());
   }

   @Test
   void modernizeIsANoOpWhenTheGateIsOff() {
      Viewsheet vs = legacySheet();
      gateOff();

      assertEquals(0, VizModernizeUtil.modernize(vs));
      assertNull(vs.getVSAssemblyInfo().getVizMark());
   }

   @Test
   void modernizeIsIdempotent() {
      Viewsheet vs = legacySheet();
      gateOn();

      assertEquals(3, VizModernizeUtil.modernize(vs));
      assertEquals(0, VizModernizeUtil.modernize(vs), "a second run touches nothing");
   }

   @Test
   void modernizeLeavesMarkedSiblingsAlone() {
      // decision 3's mixed dashboard: an already-modern assembly keeps the mark it has
      Viewsheet vs = legacySheet();
      gateOn();
      SreeEnv.setProperty("viewsheet.darkMode", "true");
      TextVSAssembly dark = new TextVSAssembly(vs, "Text2");
      vs.addAssembly(dark);
      assertEquals(VizMark.MODERN_DARK, dark.getVSAssemblyInfo().getVizMark());

      SreeEnv.setProperty("viewsheet.darkMode", "false");
      VizModernizeUtil.modernize(vs);

      assertEquals(VizMark.MODERN_DARK, dark.getVSAssemblyInfo().getVizMark(),
                   "an already-marked assembly is not re-stamped");
      assertEquals(VizMark.MODERN_LIGHT,
                   vs.getAssembly("Text1").getVSAssemblyInfo().getVizMark());
   }

   @Test
   void modernizeNeverTouchesAuthorProvenanceFlags() {
      Viewsheet vs = legacySheet();
      TableVSAssembly table = (TableVSAssembly) vs.getAssembly("Table1");
      TableVSAssemblyInfo info = (TableVSAssemblyInfo) table.getVSAssemblyInfo();
      info.setTitleHeightValue(44);
      info.setUserTitleHeight(true);

      gateOn();
      VizModernizeUtil.modernize(vs);

      assertTrue(info.isUserTitleHeight(), "userTitleHeight records an author's choice, not a mark");
      assertEquals(44, info.getTitleHeightValue(), "and the height they chose survives");
   }
}
```

- [ ] **Step 2: Run to verify it fails**

```bash
./mvnw test -pl core -Dtest=VizModernizeUtilTest
```

Expected: **compilation failure** — `cannot find symbol: class VizModernizeUtil`.

- [ ] **Step 3: Write the util**

Create `core/src/main/java/inetsoft/uql/viewsheet/internal/VizModernizeUtil.java` with the AGPL header, then:

```java
package inetsoft.uql.viewsheet.internal;

import inetsoft.uql.asset.Assembly;
import inetsoft.uql.viewsheet.VSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;

import java.util.ArrayList;
import java.util.List;

/**
 * Modernize: give an existing dashboard's unmarked content the chrome a freshly created dashboard
 * would have. It lives in this package because seedChromeDefaults is protected.
 *
 * Unmarked content is never touched automatically - this is the one route in, and something has to
 * ask for it. Marked content is left exactly as it is, so a mixed dashboard stays mixed.
 */
public final class VizModernizeUtil {
   private VizModernizeUtil() {
   }

   /**
    * Whether this sheet holds anything Modernize would act on: the sheet's own info, or any
    * assembly of its own, carrying no mark.
    */
   public static boolean hasUnmarked(Viewsheet vs) {
      return !unmarked(vs).isEmpty();
   }

   /**
    * Stamp and seed every unmarked assembly, and the sheet itself. Returns how many were touched.
    *
    * A no-op returning 0 when the gate is off: VizMark.fromGate() has no mark to give, and
    * modernizing into a legacy gate would produce content that reverts the moment the gate turns
    * on. Callers still gate explicitly - this is a floor, not the policy.
    */
   public static int modernize(Viewsheet vs) {
      VizMark mark = VizMark.fromGate();

      if(mark == null) {
         return 0;
      }

      VizContext ctx = VizContext.of(mark);
      List<VSAssemblyInfo> targets = unmarked(vs);

      for(VSAssemblyInfo info : targets) {
         info.setVizMark(mark);
         info.seedChromeDefaults(ctx);
      }

      return targets.size();
   }

   /**
    * The sheet's own info plus every unmarked assembly of its own. Assemblies belonging to an
    * embedded viewsheet are skipped: they are another asset's content, and this sheet has no
    * business writing to them.
    */
   private static List<VSAssemblyInfo> unmarked(Viewsheet vs) {
      List<VSAssemblyInfo> targets = new ArrayList<>();

      if(vs.getVSAssemblyInfo() != null && vs.getVSAssemblyInfo().getVizMark() == null) {
         targets.add(vs.getVSAssemblyInfo());
      }

      for(Assembly assembly : vs.getAssemblies(true)) {
         if(!(assembly instanceof VSAssembly)) {
            continue;
         }

         VSAssemblyInfo info = ((VSAssembly) assembly).getVSAssemblyInfo();

         if(info != null && info.getVizMark() == null && !info.isEmbedded()) {
            targets.add(info);
         }
      }

      return targets;
   }
}
```

- [ ] **Step 4: Run to verify it passes**

```bash
./mvnw test -pl core -Dtest=VizModernizeUtilTest
```

Expected: **PASS**, all eight. If `modernizeStampsEveryUnmarkedAssemblyAndTheSheet` reports a count other than 3, print what `unmarked()` collected — `getAssemblies(true)` may include an assembly the test did not add (a warning-text assembly, for instance). Adjust the expectation to the real membership and say so in the test name; do not filter types in production code to make a test number come out.

- [ ] **Step 5: Compile and commit**

```bash
./mvnw clean install -pl core -DskipTests -o
git add core/src/main/java/inetsoft/uql/viewsheet/internal/VizModernizeUtil.java \
        core/src/test/java/inetsoft/uql/viewsheet/internal/VizModernizeUtilTest.java
git commit -m "feat(viewsheet): modernize an existing dashboard's unmarked content"
```

---

## Task 5: Tell the composer a dashboard is modernizable

The bar and the menu entry both need one question answered: does this sheet hold unmarked content under an open gate? `setViewsheetInfo` already runs on open and on every refresh — including after undo — so a flag placed there is recomputed rather than latched, which is what makes the bar disappear on completion and come back on undo for free.

**Files:**
- Modify: `core/src/main/java/inetsoft/web/viewsheet/service/CoreLifecycleService.java:305-312`

**Interfaces:**
- Consumes: `VizModernizeUtil.hasUnmarked(Viewsheet)` (Task 4), `VSDensityDefaults.isModern()`.
- Produces: `info["modernizable"]` on `SetViewsheetInfoCommand` — `true` when the gate is on and the sheet holds unmarked content. It deliberately does **not** fold in a permission test; see the note below.

- [ ] **Step 1: Add the flag beside the gate flags it belongs with**

In `setViewsheetInfo`, immediately after the existing `darkMode` put:

```java
         infoMap.put("darkMode",
                     SreeEnv.getBooleanProperty("viewsheet.darkMode", false, true));
         // recomputed on every refresh, so the composer's Modernize affordance disappears when the
         // action completes and returns if the user undoes it
         infoMap.put("modernizable",
                     VSDensityDefaults.isModern() && VizModernizeUtil.hasUnmarked(vs));
```

Add the import for `inetsoft.uql.viewsheet.internal.VizModernizeUtil` if the file does not already import that package wholesale.

**Why no permission term here.** Decision 5 requires write permission to modernize, and this flag does not test it. The check is a scope-aware question — a private-scope dashboard is owned by its user and needs no grant, a global-scope one does — and `assetRepository.checkAssetPermission` answers it correctly where it matters, on the action itself, in Task 6. On the client the affordance is additionally gated by `!deployed`, which is how the neighbouring composer canvas actions express read-only (`viewsheet-pane.component.ts:412`). A user who could somehow see the bar without write permission gets a rejected action, not a modernized dashboard.

- [ ] **Step 2: Compile**

```bash
./mvnw clean install -pl core -DskipTests -o
```

Expected: BUILD SUCCESS. This step has no unit test of its own: the computation it adds is `hasUnmarked`, already covered in Task 4, and the surrounding method is a 100-line command builder whose only other verification in this tree is the client-side TL spec that Task 7 extends.

- [ ] **Step 3: Commit**

```bash
git add core/src/main/java/inetsoft/web/viewsheet/service/CoreLifecycleService.java
git commit -m "feat(viewsheet): report whether a dashboard has unmarked content"
```

---

## Task 6: The Modernize endpoint

**Files:**
- Create: `core/src/main/java/inetsoft/web/composer/vs/controller/ModernizeViewsheetController.java`
- Create: `core/src/main/java/inetsoft/web/composer/vs/controller/ModernizeViewsheetService.java`
- Test: `core/src/test/java/inetsoft/web/composer/vs/controller/ModernizeViewsheetServiceTest.java`

**Interfaces:**
- Consumes: `VizModernizeUtil.modernize(Viewsheet)` (Task 4), `CoreLifecycleService.{createList,setViewsheetInfo,refreshViewsheet}`, `AssetRepository.checkAssetPermission`.
- Produces: STOMP destination `composer/viewsheet/modernize`, no payload. `ModernizeViewsheetService.modernize(String runtimeId, Principal, CommandDispatcher, String linkUri)` returning `Void`.

- [ ] **Step 1: Write the failing test**

Create `core/src/test/java/inetsoft/web/composer/vs/controller/ModernizeViewsheetServiceTest.java` with the AGPL header, modelled on `FormatPainterServiceTest`:

```java
package inetsoft.web.composer.vs.controller;

import inetsoft.analytic.composition.ViewsheetEngine;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.sree.SreeEnv;
import inetsoft.test.*;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.VizMark;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import inetsoft.web.viewsheet.service.CoreLifecycleService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@ExtendWith(MockitoExtension.class)
@Tag("core")
class ModernizeViewsheetServiceTest {
   @BeforeEach
   void setup() throws Exception {
      service = new ModernizeViewsheetService(viewsheetEngine, coreLifecycleService,
                                             assetRepository);
      SreeEnv.setProperty("viewsheet.modernVisualization", "true");
      viewsheet = new Viewsheet();
      viewsheet.getVSAssemblyInfo().setVizMark(null);
      viewsheet.addAssembly(new TextVSAssembly(viewsheet, "Text1"));
      viewsheet.getAssembly("Text1").getVSAssemblyInfo().setVizMark(null);

      when(viewsheetEngine.getViewsheet(anyString(), nullable(Principal.class))).thenReturn(rvs);
      when(rvs.getViewsheet()).thenReturn(viewsheet);
      when(rvs.getEntry()).thenReturn(entry);
      when(rvs.getID()).thenReturn("rid");
   }

   @AfterEach
   void reset() {
      SreeEnv.setProperty("viewsheet.modernVisualization", null);
   }

   @Test
   void modernizeStampsAndRefreshes() throws Exception {
      service.modernize("rid", principal, dispatcher, "uri");

      assertEquals(VizMark.MODERN_LIGHT, viewsheet.getVSAssemblyInfo().getVizMark());
      assertEquals(VizMark.MODERN_LIGHT,
                   viewsheet.getAssembly("Text1").getVSAssemblyInfo().getVizMark());
      verify(assetRepository).checkAssetPermission(eq(principal), eq(entry), any());
      verify(coreLifecycleService).setViewsheetInfo(eq(rvs), eq("uri"), eq(dispatcher));
      verify(coreLifecycleService).refreshViewsheet(eq(rvs), eq("rid"), eq("uri"), eq(dispatcher),
                                                   eq(false), eq(false), eq(true), any());
   }

   @Test
   void modernizeDoesNothingWithTheGateOff() throws Exception {
      SreeEnv.setProperty("viewsheet.modernVisualization", "false");

      service.modernize("rid", principal, dispatcher, "uri");

      assertNull(viewsheet.getVSAssemblyInfo().getVizMark());
      verify(coreLifecycleService, never())
         .refreshViewsheet(any(), anyString(), anyString(), any(), anyBoolean(), anyBoolean(),
                           anyBoolean(), any());
   }

   @Test
   void modernizeRefusesWithoutWritePermission() throws Exception {
      doThrow(new SecurityException("denied"))
         .when(assetRepository).checkAssetPermission(any(), any(), any());

      assertThrows(SecurityException.class,
                   () -> service.modernize("rid", principal, dispatcher, "uri"));
      assertNull(viewsheet.getVSAssemblyInfo().getVizMark(),
                 "the permission check precedes any write");
   }

   private ModernizeViewsheetService service;
   private Viewsheet viewsheet;
   @Mock private ViewsheetEngine viewsheetEngine;
   @Mock private CoreLifecycleService coreLifecycleService;
   @Mock private AssetRepository assetRepository;
   @Mock private RuntimeViewsheet rvs;
   @Mock private AssetEntry entry;
   @Mock private Principal principal;
   @Mock private CommandDispatcher dispatcher;
}
```

- [ ] **Step 2: Run to verify it fails**

```bash
./mvnw test -pl core -Dtest=ModernizeViewsheetServiceTest
```

Expected: **compilation failure** — `cannot find symbol: class ModernizeViewsheetService`.

- [ ] **Step 3: Write the service**

Create `core/src/main/java/inetsoft/web/composer/vs/controller/ModernizeViewsheetService.java` with the AGPL header:

```java
package inetsoft.web.composer.vs.controller;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.cluster.*;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.WorksheetEngine;
import inetsoft.sree.security.ResourceAction;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.VizModernizeUtil;
import inetsoft.web.viewsheet.service.ChangedAssemblyList;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import inetsoft.web.viewsheet.service.CoreLifecycleService;
import org.springframework.stereotype.Service;

import java.security.Principal;

/**
 * Modernize the focused dashboard: stamp its unmarked assemblies and give them the chrome a newly
 * created dashboard would have. Composer only, gate-on only, write permission required.
 */
@Service
@ClusterProxy
public class ModernizeViewsheetService {
   public ModernizeViewsheetService(ViewsheetService viewsheetService,
                                    CoreLifecycleService coreLifecycleService,
                                    AssetRepository assetRepository)
   {
      this.viewsheetService = viewsheetService;
      this.coreLifecycleService = coreLifecycleService;
      this.assetRepository = assetRepository;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   @ClusterWriteMethod
   public Void modernize(@ClusterProxyKey String runtimeId, Principal principal,
                         CommandDispatcher dispatcher, String linkUri)
      throws Exception
   {
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(runtimeId, principal);
      // scope-aware: a private dashboard is its owner's, a global one needs the grant
      assetRepository.checkAssetPermission(principal, rvs.getEntry(), ResourceAction.WRITE);
      Viewsheet vs = rvs.getViewsheet();

      if(vs == null || VizModernizeUtil.modernize(vs) == 0) {
         return null;
      }

      ChangedAssemblyList clist =
         coreLifecycleService.createList(false, dispatcher, rvs, linkUri);
      coreLifecycleService.setViewsheetInfo(rvs, linkUri, dispatcher);
      coreLifecycleService.refreshViewsheet(rvs, rvs.getID(), linkUri, dispatcher, false, false,
                                           true, clist);
      return null;
   }

   private final ViewsheetService viewsheetService;
   private final CoreLifecycleService coreLifecycleService;
   private final AssetRepository assetRepository;
}
```

If the test's `viewsheetEngine` mock does not satisfy the `ViewsheetService` parameter, match `FormatPainterService`'s own declaration — it takes a `ViewsheetService` and the test mocks `ViewsheetEngine`, which implements it. Keep the production type as the interface.

- [ ] **Step 4: Write the controller**

Create `core/src/main/java/inetsoft/web/composer/vs/controller/ModernizeViewsheetController.java` with the AGPL header:

```java
package inetsoft.web.composer.vs.controller;

import inetsoft.web.viewsheet.*;
import inetsoft.web.viewsheet.model.RuntimeViewsheetRef;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;

import java.security.Principal;

/**
 * Modernize the focused dashboard. One undo step, per decision 5 - @Undoable snapshots after the
 * action returns, so Ctrl+Z restores the unmarked state and, with it, the offer to modernize.
 */
@Controller
public class ModernizeViewsheetController {
   @Autowired
   public ModernizeViewsheetController(RuntimeViewsheetRef runtimeViewsheetRef,
                                       ModernizeViewsheetServiceProxy modernizeViewsheetService)
   {
      this.runtimeViewsheetRef = runtimeViewsheetRef;
      this.modernizeViewsheetService = modernizeViewsheetService;
   }

   @Undoable
   @LoadingMask
   @HandleAssetExceptions
   @MessageMapping("composer/viewsheet/modernize")
   public void modernize(Principal principal, CommandDispatcher commandDispatcher,
                         @LinkUri String linkUri)
      throws Exception
   {
      modernizeViewsheetService.modernize(runtimeViewsheetRef.getRuntimeId(), principal,
                                         commandDispatcher, linkUri);
   }

   private final RuntimeViewsheetRef runtimeViewsheetRef;
   private final ModernizeViewsheetServiceProxy modernizeViewsheetService;
}
```

`ModernizeViewsheetServiceProxy` does not exist on disk — the `@ClusterProxy` annotation processor generates it. The controller will not compile until the service from Step 3 has been through one build, which the next step does.

- [ ] **Step 5: Build, then run the test**

```bash
./mvnw clean install -pl core -DskipTests -o
./mvnw test -pl core -Dtest=ModernizeViewsheetServiceTest
```

Expected: BUILD SUCCESS, then **PASS**, all three. If `refreshViewsheet`'s argument list does not match, read the overload at `CoreLifecycleService.java:498` and align both the call and the `verify`.

- [ ] **Step 6: Commit**

```bash
git add core/src/main/java/inetsoft/web/composer/vs/controller/ModernizeViewsheetController.java \
        core/src/main/java/inetsoft/web/composer/vs/controller/ModernizeViewsheetService.java \
        core/src/test/java/inetsoft/web/composer/vs/controller/ModernizeViewsheetServiceTest.java
git commit -m "feat(composer): add the Modernize action as one undoable step"
```

---

## Task 7: The client flag, the STOMP send, and the permanent menu entry

**Files:**
- Modify: `web/projects/portal/src/app/composer/data/vs/viewsheet.ts:29-53`
- Modify: `web/projects/portal/src/app/composer/gui/vs/editor/viewsheet-pane.component.ts:397-431` (`menuActions`) and `:791-826` (`processSetViewsheetInfoCommand`)
- Test: `web/projects/portal/src/app/composer/gui/vs/editor/viewsheet-pane.component.interaction.tl.spec.ts`
- Modify: `core/src/main/resources/inetsoft/util/srinter.properties`

**Interfaces:**
- Consumes: `info["modernizable"]` (Task 5), STOMP `composer/viewsheet/modernize` (Task 6).
- Produces on `Viewsheet`: `modernizable: boolean`, `modernizeBarDismissed: boolean`. On `VSPane`: `modernize(): void`, `modernizeOffered: boolean` getter. Task 8 binds both.

- [ ] **Step 1: Write the failing tests**

Append to the existing `describe("VSPane — processSetViewsheetInfoCommand")` block in `viewsheet-pane.component.interaction.tl.spec.ts`, following the `mocks.dispatchCommand` idiom already used there:

The file's helpers are `makeMocks()` and `renderComponent(mocks)` returning `{ comp }`, and its command payloads are complete objects; `AssemblyActionGroup` exposes a public `actions` array (`common/action/assembly-action-group.ts:42`). Follow both.

```typescript
   it("should store the modernizable flag on the sheet", async () => {
      const mocks = makeMocks();
      const { comp } = await renderComponent(mocks);

      mocks.dispatchCommand("SetViewsheetInfoCommand", {
         assemblyInfo: { name: "VS" },
         layouts: [],
         baseEntry: null,
         info: { viewsheetBackground: "#ffffff", statusText: null, snapGrid: 20,
                 templateWidth: 0, templateHeight: 0, templateEnabled: false, metadata: false,
                 messageLevels: null, modernizable: true },
         linkUri: null,
         hasScript: false,
         hideNotifications: false,
         annotation: false,
         annotated: false,
         formTable: false,
      });

      expect(comp.vs.modernizable).toBe(true);
   });

   it("should treat a missing modernizable flag as false", async () => {
      const mocks = makeMocks();
      const { comp } = await renderComponent(mocks);
      comp.vs.modernizable = true;

      mocks.dispatchCommand("SetViewsheetInfoCommand", {
         assemblyInfo: { name: "VS" },
         layouts: [],
         baseEntry: null,
         info: { viewsheetBackground: "#ffffff", statusText: null, snapGrid: 20,
                 templateWidth: 0, templateHeight: 0, templateEnabled: false, metadata: false,
                 messageLevels: null },
         linkUri: null,
         hasScript: false,
         hideNotifications: false,
         annotation: false,
         annotated: false,
         formTable: false,
      });

      expect(comp.vs.modernizable).toBe(false);
   });

   it("should offer Modernize in the canvas menu only where there is unmarked content", async () => {
      const mocks = makeMocks();
      const { comp } = await renderComponent(mocks);
      const entry = comp.menuActions
         .reduce((all, group) => all.concat(group.actions), [])
         .find(action => action.id() === "composer vspane modernize");

      comp.vs.modernizable = false;
      expect(entry.visible()).toBe(false);

      comp.vs.modernizable = true;
      expect(entry.visible()).toBe(true);
   });

   it("should keep the menu entry after the bar is dismissed", async () => {
      const mocks = makeMocks();
      const { comp } = await renderComponent(mocks);
      comp.vs.modernizable = true;
      comp.vs.modernizeBarDismissed = true;
      const entry = comp.menuActions
         .reduce((all, group) => all.concat(group.actions), [])
         .find(action => action.id() === "composer vspane modernize");

      expect(entry.visible()).toBe(true);
      expect(comp.modernizeOffered).toBe(false);
   });
```

- [ ] **Step 2: Run to verify it fails**

```bash
cd E:/StyleBI/stylebi-enterprise/community/web
npx ng run portal:test-tl --include="**/viewsheet-pane.component.interaction.tl.spec.ts"
```

Expected: the four new tests FAIL (`modernizable` undefined; no menu entry with that id). **Scope every TL run with `--include` — never run the whole TL suite.**

- [ ] **Step 3: Add the two fields to the client sheet**

In `viewsheet.ts`, beside the existing flags:

```typescript
   metadata: boolean;
   newGroup: boolean;
   /** Server-computed: the gate is on and this sheet holds unmarked content. */
   modernizable: boolean = false;
   /** Client-only, per open sheet: the offer bar was dismissed for this composer session. */
   modernizeBarDismissed: boolean = false;
```

- [ ] **Step 4: Read the flag, add the action and the menu entry**

In `viewsheet-pane.component.ts`, inside `processSetViewsheetInfoCommand`, beside the other `command.info[...]` reads:

```typescript
      this.vs.modernizable = !!command.info["modernizable"];
```

Add the offer getter and the action next to the component's other canvas commands:

```typescript
   /** The bar is shown only until it is dismissed; the menu entry outlives the dismissal. */
   get modernizeOffered(): boolean {
      return this.vs.modernizable && !this.deployed && !this.vs.modernizeBarDismissed
         && !this.vs.currentLayout;
   }

   dismissModernize(): void {
      this.vs.modernizeBarDismissed = true;
   }

   modernize(): void {
      this.vs.socketConnection.sendEvent("/events/composer/viewsheet/modernize");
   }
```

And a fourth entry in `menuActions`' first group, after `composer vspane options`:

```typescript
         {
            id: () => "composer vspane modernize",
            label: () => "_#(js:composer.vs.modernize.menu)",
            icon: () => "star-icon",
            enabled: () => true,
            visible: () => this.vs.modernizable && !this.deployed,
            action: () => this.modernize()
         },
```

`star-icon` is a class this tree already ships and uses (`widget/tree/tree-node.component.ts:96`, `vs-wizard/gui/object-wizard/object-type-pane.component.ts:91`).

- [ ] **Step 5: Add the labels**

In `core/src/main/resources/inetsoft/util/srinter.properties`, in the `composer.vs.` block (near `composer.vs.addDashboard`, keeping the file's alphabetical grouping):

```properties
composer.vs.modernize.menu=Modernize Dashboard
composer.vs.modernize.message=This dashboard uses the classic look. Modernizing adopts the current visual defaults for components that have not been updated.
composer.vs.modernize.action=Modernize
composer.vs.modernize.dismiss=Not now
```

- [ ] **Step 6: Run to verify it passes**

```bash
cd E:/StyleBI/stylebi-enterprise/community/web
npx ng run portal:test-tl --include="**/viewsheet-pane.component.interaction.tl.spec.ts"
```

Expected: PASS, including the file's pre-existing tests.

- [ ] **Step 7: Commit**

```bash
git add web/projects/portal/src/app/composer/data/vs/viewsheet.ts \
        web/projects/portal/src/app/composer/gui/vs/editor/viewsheet-pane.component.ts \
        web/projects/portal/src/app/composer/gui/vs/editor/viewsheet-pane.component.interaction.tl.spec.ts \
        core/src/main/resources/inetsoft/util/srinter.properties
git commit -m "feat(composer): offer Modernize from the canvas menu"
```

---

## Task 8: The offer bar

Answering the design's open item 4: **there is no existing banner mechanism to host this.** The composer's only notice surface is `<notifications>` (`composer-main.component.html:305`), a toast with `[timeout]="5000"` and no action slot — wrong for a persistent offer with two buttons. So: a small standalone component, rendered as an overlay at the top of the canvas rather than as a row above it, because `.vs-pane-container` positions the rulers off the height of what precedes the canvas (`viewsheet-pane.component.html:30-35`) and a new row would shift both the ruler origin and the canvas scroll geometry.

**Files:**
- Create: `web/projects/portal/src/app/composer/gui/vs/editor/modernize-bar.component.ts` / `.html` / `.scss`
- Test: `web/projects/portal/src/app/composer/gui/vs/editor/modernize-bar.component.spec.ts`
- Modify: `web/projects/portal/src/app/composer/gui/vs/editor/viewsheet-pane.component.html`
- Modify: `web/projects/portal/src/app/composer/gui/vs/editor/viewsheet-pane.component.ts` (`imports`)

**Interfaces:**
- Consumes: `modernizeOffered`, `modernize()`, `dismissModernize()` from Task 7.
- Produces: `<modernize-bar>` with `@Input() message: string` and `@Output() onModernize` / `onDismiss`.

- [ ] **Step 1: Write the failing test**

Create `modernize-bar.component.spec.ts` with the AGPL header:

```typescript
import { ComponentFixture, TestBed } from "@angular/core/testing";
import { ModernizeBarComponent } from "./modernize-bar.component";

describe("ModernizeBarComponent", () => {
   let fixture: ComponentFixture<ModernizeBarComponent>;

   beforeEach(async() => {
      await TestBed.configureTestingModule({
         imports: [ModernizeBarComponent]
      }).compileComponents();

      fixture = TestBed.createComponent(ModernizeBarComponent);
   });

   it("renders the message it is given", () => {
      fixture.componentInstance.message = "Classic look";
      fixture.detectChanges();
      expect(fixture.nativeElement.textContent).toContain("Classic look");
   });

   it("emits when the action is pressed", () => {
      const spy = vi.fn();
      fixture.componentInstance.onModernize.subscribe(spy);
      fixture.detectChanges();

      fixture.nativeElement.querySelector(".modernize-bar_action").click();

      expect(spy).toHaveBeenCalledTimes(1);
   });

   it("emits when dismissed", () => {
      const spy = vi.fn();
      fixture.componentInstance.onDismiss.subscribe(spy);
      fixture.detectChanges();

      fixture.nativeElement.querySelector(".modernize-bar_dismiss").click();

      expect(spy).toHaveBeenCalledTimes(1);
   });
});
```

- [ ] **Step 2: Run to verify it fails**

```bash
cd E:/StyleBI/stylebi-enterprise/community/web
npx ng test portal --include="**/modernize-bar.component.spec.ts"
```

Expected: FAIL — cannot resolve `./modernize-bar.component`.

- [ ] **Step 3: Write the component**

`modernize-bar.component.ts`:

```typescript
import { Component, EventEmitter, Input, Output } from "@angular/core";

@Component({
   selector: "modernize-bar",
   templateUrl: "modernize-bar.component.html",
   styleUrls: ["modernize-bar.component.scss"]
})
export class ModernizeBarComponent {
   @Input() message: string = "";
   @Output() onModernize = new EventEmitter<void>();
   @Output() onDismiss = new EventEmitter<void>();
}
```

`modernize-bar.component.html`:

```html
<div class="modernize-bar" role="status">
  <span class="modernize-bar_message">{{message}}</span>
  <button type="button" class="btn btn-sm btn-primary modernize-bar_action"
          (click)="onModernize.emit()">_#(composer.vs.modernize.action)</button>
  <button type="button" class="btn btn-sm btn-link modernize-bar_dismiss"
          (click)="onDismiss.emit()">_#(composer.vs.modernize.dismiss)</button>
</div>
```

`modernize-bar.component.scss`:

```scss
.modernize-bar {
  position: absolute;
  top: 8px;
  left: 50%;
  transform: translateX(-50%);
  z-index: 100;
  display: flex;
  align-items: center;
  gap: 8px;
  max-width: 640px;
  padding: 6px 10px;
  border: 1px solid var(--inet-border-color, #c7c6c1);
  border-radius: 4px;
  background-color: var(--inet-dialog-bg-color, #ffffff);
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.15);
}

.modernize-bar_message {
  flex: 1 1 auto;
}
```

Both tokens are established: `--inet-border-color` with the `#c7c6c1` fallback is the tree's own convention (`_bootstrap-override.scss:440`), and `--inet-dialog-bg-color` is what floating panels use. The bar sits inside `.vs-pane-container`, which is `position: relative`, so `position: absolute` anchors to the canvas rather than the viewport — confirm that when wiring it up and add the positioning context if it is missing.

- [ ] **Step 4: Run to verify it passes**

```bash
npx ng test portal --include="**/modernize-bar.component.spec.ts"
```

Expected: PASS, three tests.

- [ ] **Step 5: Render it in the pane**

In `viewsheet-pane.component.html`, inside `.vs-pane-container` and immediately before the `@if (vs) {` that opens `.vs-pane`:

```html
  @if (modernizeOffered) {
    <modernize-bar [message]="modernizeMessage"
      (onModernize)="modernize()" (onDismiss)="dismissModernize()">
    </modernize-bar>
  }
```

In `viewsheet-pane.component.ts`, add `ModernizeBarComponent` to the `@Component` `imports` array and the message field beside `modernizeOffered`:

```typescript
   readonly modernizeMessage: string = "_#(js:composer.vs.modernize.message)";
```

- [ ] **Step 6: Re-run both frontend specs and lint**

```bash
npx ng test portal --include="**/modernize-bar.component.spec.ts"
npx ng run portal:test-tl --include="**/viewsheet-pane.component.interaction.tl.spec.ts"
npx ng lint 2>&1 | tail -20
```

Expected: PASS, PASS, no new lint errors.

- [ ] **Step 7: Commit**

```bash
git add web/projects/portal/src/app/composer/gui/vs/editor/modernize-bar.component.ts \
        web/projects/portal/src/app/composer/gui/vs/editor/modernize-bar.component.html \
        web/projects/portal/src/app/composer/gui/vs/editor/modernize-bar.component.scss \
        web/projects/portal/src/app/composer/gui/vs/editor/modernize-bar.component.spec.ts \
        web/projects/portal/src/app/composer/gui/vs/editor/viewsheet-pane.component.html \
        web/projects/portal/src/app/composer/gui/vs/editor/viewsheet-pane.component.ts
git commit -m "feat(composer): offer Modernize in a dismissable canvas bar"
```

---

## Task 9: The manual pass, and the documents

The claim P3 makes is about persisted values, and two of its conditions cannot be asserted in a unit test: that a dashboard nobody modernizes renders exactly as it did, and that the bar's session behaviour matches decision 5.

**Files:**
- Modify: `docs/superpowers/specs/2026-08-14-seed-mark-forward-half-design.md`
- Modify: `docs/superpowers/specs/lookfeel/chart-card-roadmap.md`

- [ ] **Step 1: Run the whole `core` suite and compare with the pre-phase baseline**

```bash
cd E:/StyleBI/stylebi-enterprise/community
./mvnw test -pl core 2>&1 | tail -40
```

Record failures. The bar for this phase is *no worse than the baseline at `119bfdaac`* — capture that baseline first, on a clean checkout of `HEAD`, if it is not already recorded.

- [ ] **Step 2: The manual pass**

Build and start per `CLAUDE.md`, then with `viewsheet.modernVisualization` **on** in EM → Properties:

1. Open a dashboard saved before the mark existed. **The bar appears.** Nothing else about the dashboard has changed — chrome, radius, row heights all as before.
2. Dismiss it. It stays gone when you switch composer tabs away and back. Right-click the canvas: **Modernize Dashboard is still there.**
3. Close the dashboard and reopen it. **The bar is back.**
4. Press Modernize. Cards take the modern border, radius and background; charts take rounded bars and smooth lines. **The bar disappears** without a refresh of its own.
5. Ctrl+Z. Everything reverts and **the bar returns** — one undo step, not several.
6. Modernize again, then run it a second time. Nothing changes on the second run.
7. Set a title height by hand on one assembly, then Modernize: **that height survives.** Same for a table style and a hand-picked cell font.
8. Save, close, reopen: the modernized state persisted, and the bar does not return.
9. Create a new dashboard: **no bar** — new content is marked at creation.
10. Turn the gate **off** and reopen the legacy dashboard: **no bar**, and the canvas menu has no Modernize entry.
11. Open composer preview and the viewer on a legacy dashboard: **no bar in either.**

Record the result of each in the commit message. Any deviation is a defect in this phase, not a note for later.

- [ ] **Step 3: Update the design document**

In `2026-08-14-seed-mark-forward-half-design.md`:
- §5's P3: mark it built, with the commit range.
- Open item 4: close it — no existing banner mechanism; a standalone overlay component in the viewsheet pane, positioned absolutely so the ruler origin and canvas scroll geometry are untouched.
- §4: note that `seedChromeDefaults` shipped with the signature and contract the five corrections specified, and that the four creation-path `ofGate()` calls collapsed into one, which is what P4 flips.
- Record the one fact discovered while building: `ChartVSAssemblyInfo` replaces the whole TITLEPATH composite after `super` returns, so the base's title border colour never reaches a chart. Pre-existing, preserved, and pinned by a test — P4 should not "fix" it either.

- [ ] **Step 4: Update the roadmap**

In `chart-card-roadmap.md`:
- Add the Done rows for P3's commits.
- In the dependency picture, mark P3 SHIPPED and P4 STARTABLE.
- Re-derive "What to pick up next" from the picture rather than editing the old ranking: with P3 in, #1 becomes **M-P4**, and its two prerequisites are now recorded — the chart-pipeline threading pattern and the `ChartColorPaletteController` decision.

- [ ] **Step 5: Commit**

```bash
git add docs/superpowers/specs/2026-08-14-seed-mark-forward-half-design.md \
        docs/superpowers/specs/lookfeel/chart-card-roadmap.md
git commit -m "docs(chart-card): record what P3 built and re-derive the next step"
```

---

## Done when

- `seedChromeDefaults(VizContext)` exists on `VSAssemblyInfo` with overrides on `ChartVSAssemblyInfo`, `TableDataVSAssemblyInfo` and `ViewsheetVSAssemblyInfo`; `ViewsheetVSAssemblyInfo.setDefaultFormat(boolean)` is gone.
- `SeedChromeDefaultsTest`'s twelve characterization tests, written before the refactor, still pass after it.
- The hook applied to a gate-off-created assembly produces the same persisted values as a gate-on-created one, per type, including the four `PlotDescriptor` seeds and the TITLEPATH border colour — and a user-tier format set beforehand survives.
- `grep -rn "VizContext.ofGate()"` across the four seed-site classes returns exactly one hit.
- `VizModernizeUtil.modernize` stamps only unmarked content, is idempotent, is a no-op with the gate off, skips embedded children, and leaves all four author-provenance flags alone.
- The action is one `@Undoable` step, refuses without WRITE, and does nothing with the gate off.
- The bar appears only under the gate with unmarked content and an undismissed session; dismissal survives a tab switch and not a reopen; the menu entry outlives the dismissal and is absent, not disabled, when there is nothing to modernize.
- The eleven manual checks pass, and the `core` suite is no worse than its `119bfdaac` baseline.

**Not in P3, and do not let it creep in:** any read of the mark that changes rendering (`VizContext.of(info)` at a resolver), the `VSObjectModel` mark field, per-assembly `viz-modern`/`viz-dark` CSS scoping, the body-class rename, retiring `resolveSeededCorner` or the `PlotDescriptor` seed booleans, the revert sweep, and the bookmark path. Those are P4, P5 and the reverse half.
