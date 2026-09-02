# Chart Interior Seeded Dark Chrome — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Seed six chart-interior chrome values on `PlotDescriptor` at creation — the data-label ink and five line colours — so a modern-dark chart stops drawing near-white gridlines on a dark card, and so the composer's Chart Line pane and the canvas stop disagreeing.

**Architecture:** All six values seed in `ChartVSAssemblyInfo.seedChromeDefaults(VizContext)`, the hook that creation, Modernize, Revert and bookmark restore all call, using the `PlotDescriptor` handle it already resolves for `barCornerRadius`. Both branches always write, because Revert re-runs the same hook with an unmarked context. Nothing is resolved at render: the one place that currently overwrites a seeded value on every repaint is removed, and the ten now-redundant resolver calls in the composer dialog are deleted so one stored value feeds both the dialog and the canvas.

**Tech Stack:** Java 21, Maven (`./mvnw`), JUnit 5 + Spring test context, `inetsoft-core` module.

**Spec:** [../specs/lookfeel/2026-09-02-chart-interior-dark-chrome-design.md](../specs/lookfeel/2026-09-02-chart-interior-dark-chrome-design.md)

## Global Constraints

- **Both branches always write.** `VizModernizeUtil.revert` clears the mark and re-runs `seedChromeDefaults`; a value written only on the modern branch is stranded by Revert. Every seed in this plan has an `else`.
- **No customization guards.** A USER or CSS value outranks DEFAULT by construction, so never test whether an author set a value. Always write the DEFAULT tier via the `(Color, CompositeValue.Type.DEFAULT)` setter overload.
- **The legacy branch reproduces a gate-off creation exactly**, which means re-running each field's own constructed expression — not writing `#eeeeee` literally. Four of the five line colours go through a `format.css` lookup and one does not; Task 1 spells out which.
- **An unmarked chart must be byte-identical to pre-change** in both light and dark. This is the assertion to pin hardest.
- **Modern light changes too, deliberately**: gridlines `#eeeeee` → `#E8E5DE`, data labels `#4b4b4b` → `#35342F`. Do not "fix" this back.
- **New test classes need `@Tag("core")`** or surefire's hardcoded group filter means they never run.
- Run the suite with `./mvnw -q -pl community/core test -Dtest=<Class>` from `E:/StyleBI/stylebi-enterprise`.
- **Never chain git with `&&`** — a `PreToolUse` hook blocks `/&&\s*git\s+(add|commit|push)/`. Use `git -C <path> …` as separate commands.

---

### Task 1: Seed the five line colours

**Files:**
- Modify: `core/src/main/java/inetsoft/uql/viewsheet/internal/ChartVSAssemblyInfo.java:138-163`
- Test: `core/src/test/java/inetsoft/uql/viewsheet/internal/SeedChromeDefaultsTest.java`

**Interfaces:**
- Consumes: `VSChartChromeDefaults.gridlineColor(VizContext)` → `Color` (`#E8E5DE` light, `#3A383D` dark); `ChartLineColor.getPlotLineColor(Color defaultColor, String type)` → `Color`; `PlotDescriptor.setXGridColor/setYGridColor/setDiagonalColor/setQuadrantColor/setFacetGridColor(Color, CompositeValue.Type)`.
- Produces: seeded DEFAULT tiers on `PlotDescriptor.xGridColor`, `yGridColor`, `diagonalColor`, `quadrantColor`, `facetColor`. Task 3 depends on these being the stored values.

**Background the implementer needs.** These five fields are `CompositeValue<Color>` (`PlotDescriptor:1898-1915`), each constructed with its own default expression. Four run a `format.css` lookup keyed by a type string; **`facetColor` does not** — it takes `GDefaults.DEFAULT_LINE_COLOR` plain. The legacy branch must reproduce each exactly, or a customer whose `format.css` sets `ChartPlotLine` loses their value on Revert.

| Field | Constructed default |
|---|---|
| `xGridColor` | `ChartLineColor.getPlotLineColor(GDefaults.DEFAULT_GRIDLINE_COLOR, "x")` |
| `yGridColor` | `ChartLineColor.getPlotLineColor(GDefaults.DEFAULT_GRIDLINE_COLOR, "y")` |
| `diagonalColor` | `ChartLineColor.getPlotLineColor(GDefaults.DEFAULT_GRIDLINE_COLOR, "diagonal")` |
| `quadrantColor` | `ChartLineColor.getPlotLineColor(GDefaults.DEFAULT_GRIDLINE_COLOR, "quadrant")` |
| `facetColor` | `GDefaults.DEFAULT_LINE_COLOR` — no lookup |

- [ ] **Step 1: Write the failing tests**

Add to `SeedChromeDefaultsTest`. It already has `gateOn()`, `gateOff()`, `newChart()` and an `@AfterEach reset()` that clears the three properties — use them.

```java
   private static PlotDescriptor plot(ChartVSAssemblyInfo info) {
      return info.getChartDescriptor().getPlotDescriptor();
   }

   @Test
   void aModernChartSeedsTheWarmGridlineOnAllFiveLines() {
      gateOn();
      PlotDescriptor plot = plot(newChart());
      Color expected = new Color(0xE8E5DE);
      assertEquals(expected, plot.getXGridColor(), "x gridline");
      assertEquals(expected, plot.getYGridColor(), "y gridline");
      assertEquals(expected, plot.getFacetGridColor(), "facet lines");
      assertEquals(expected, plot.getDiagonalColor(), "scatter-matrix diagonal");
      assertEquals(expected, plot.getQuadrantColor(), "quadrant lines");
   }

   @Test
   void aDarkChartSeedsTheDarkGridlineOnAllFiveLines() {
      SreeEnv.setProperty("viewsheet.modernVisualization", "true");
      SreeEnv.setProperty("viewsheet.darkMode", "true");
      PlotDescriptor plot = plot(newChart());
      Color expected = new Color(0x3A383D);
      assertEquals(expected, plot.getXGridColor(), "x gridline");
      assertEquals(expected, plot.getYGridColor(), "y gridline");
      assertEquals(expected, plot.getFacetGridColor(), "facet lines");
      assertEquals(expected, plot.getDiagonalColor(), "scatter-matrix diagonal");
      assertEquals(expected, plot.getQuadrantColor(), "quadrant lines");
   }

   @Test
   void anUnmarkedChartKeepsTheLegacyGridlineOnAllFiveLines() {
      gateOff();
      PlotDescriptor plot = plot(newChart());
      // the four CSS-looked-up lines and the one plain line, each compared against its own
      // constructed expression rather than a literal - a format.css rule must still win
      assertEquals(ChartLineColor.getPlotLineColor(GDefaults.DEFAULT_GRIDLINE_COLOR, "x"),
                   plot.getXGridColor(), "x gridline");
      assertEquals(ChartLineColor.getPlotLineColor(GDefaults.DEFAULT_GRIDLINE_COLOR, "y"),
                   plot.getYGridColor(), "y gridline");
      assertEquals(ChartLineColor.getPlotLineColor(GDefaults.DEFAULT_GRIDLINE_COLOR, "diagonal"),
                   plot.getDiagonalColor(), "scatter-matrix diagonal");
      assertEquals(ChartLineColor.getPlotLineColor(GDefaults.DEFAULT_GRIDLINE_COLOR, "quadrant"),
                   plot.getQuadrantColor(), "quadrant lines");
      assertEquals(GDefaults.DEFAULT_LINE_COLOR, plot.getFacetGridColor(),
                   "facet lines take DEFAULT_LINE_COLOR with no css lookup");
   }

   @Test
   void anAuthorSetGridlineOutranksTheSeed() {
      gateOn();
      ChartVSAssemblyInfo info = newChart();
      plot(info).setYGridColor(Color.RED, CompositeValue.Type.USER);
      // re-run the hook the way Modernize does; the USER tier must still win
      info.initDefaultFormat();
      assertEquals(Color.RED, plot(info).getYGridColor(),
                   "a USER value outranks the seeded DEFAULT by construction");
   }
```

Add these imports to the test file:

```java
import inetsoft.graph.internal.GDefaults;
import inetsoft.uql.viewsheet.graph.ChartLineColor;
import inetsoft.uql.CompositeValue;
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./mvnw -q -pl community/core test -Dtest=SeedChromeDefaultsTest`
Expected: FAIL. `aModernChartSeedsTheWarmGridlineOnAllFiveLines` and `aDarkChartSeedsTheDarkGridlineOnAllFiveLines` report the legacy `#eeeeee` where a modern value was expected. `anUnmarkedChartKeepsTheLegacyGridlineOnAllFiveLines` and `anAuthorSetGridlineOutranksTheSeed` should already PASS — they pin behaviour that must not change.

- [ ] **Step 3: Seed the five values**

In `ChartVSAssemblyInfo.seedChromeDefaults`, the `plotDesc` handle already exists at `:138`. Add to the `if(ctx.modern)` branch, after the existing `smoothLines` block:

```java
         // the plot's structural lines. Seeded rather than resolved at render so they travel in
         // an exported asset, and so the composer pane and the canvas read one stored value
         Color gridline = VSChartChromeDefaults.gridlineColor(ctx);
         plotDesc.setXGridColor(gridline, CompositeValue.Type.DEFAULT);
         plotDesc.setYGridColor(gridline, CompositeValue.Type.DEFAULT);
         plotDesc.setFacetGridColor(gridline, CompositeValue.Type.DEFAULT);
         plotDesc.setDiagonalColor(gridline, CompositeValue.Type.DEFAULT);
         plotDesc.setQuadrantColor(gridline, CompositeValue.Type.DEFAULT);
```

And to the `else` branch, after the existing `smoothLines` block:

```java
         // each line restores its own constructed default, not a literal: four run a format.css
         // lookup and the facet does not, so a customer's ChartPlotLine rule survives a Revert
         plotDesc.setXGridColor(
            ChartLineColor.getPlotLineColor(GDefaults.DEFAULT_GRIDLINE_COLOR, "x"),
            CompositeValue.Type.DEFAULT);
         plotDesc.setYGridColor(
            ChartLineColor.getPlotLineColor(GDefaults.DEFAULT_GRIDLINE_COLOR, "y"),
            CompositeValue.Type.DEFAULT);
         plotDesc.setDiagonalColor(
            ChartLineColor.getPlotLineColor(GDefaults.DEFAULT_GRIDLINE_COLOR, "diagonal"),
            CompositeValue.Type.DEFAULT);
         plotDesc.setQuadrantColor(
            ChartLineColor.getPlotLineColor(GDefaults.DEFAULT_GRIDLINE_COLOR, "quadrant"),
            CompositeValue.Type.DEFAULT);
         plotDesc.setFacetGridColor(GDefaults.DEFAULT_LINE_COLOR, CompositeValue.Type.DEFAULT);
```

Add the imports the file needs: `inetsoft.graph.internal.GDefaults`, `inetsoft.uql.viewsheet.graph.ChartLineColor`, `inetsoft.util.css.CompositeValue`, and `java.awt.Color` if absent.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./mvnw -q -pl community/core test -Dtest=SeedChromeDefaultsTest`
Expected: PASS, all four.

- [ ] **Step 5: Run the full core suite for regressions**

Run: `./mvnw -q -pl community/core test`
Expected: 0 failures, 0 errors. The baseline at `93d922c91` is 5245 tests. If a `ChartLinePaneModel` or axis test fails here, stop — it means something reads these values in a way the design did not enumerate, and that is a spec gap rather than a test to adjust.

- [ ] **Step 6: Commit**

```bash
git -C E:/StyleBI/stylebi-enterprise/community add core/src/main/java/inetsoft/uql/viewsheet/internal/ChartVSAssemblyInfo.java core/src/test/java/inetsoft/uql/viewsheet/internal/SeedChromeDefaultsTest.java
```

```bash
git -C E:/StyleBI/stylebi-enterprise/community commit -m "feat(viewsheet): seed the plot's structural line colours"
```

---

### Task 2: Seed the data-label ink and delete the render-time write

**Files:**
- Modify: `core/src/main/java/inetsoft/uql/viewsheet/internal/ChartVSAssemblyInfo.java` (the same two branches)
- Modify: `core/src/main/java/inetsoft/uql/viewsheet/graph/PlotDescriptor.java:71-76`
- Test: `core/src/test/java/inetsoft/uql/viewsheet/internal/SeedChromeDefaultsTest.java`

**Interfaces:**
- Consumes: `VSChartChromeDefaults.titleColor(VizContext)` → `Color` (`#35342F` light, `#E6E0E9` dark); `PlotDescriptor.getTextFormat()` → `CompositeTextFormat`, whose `getDefaultFormat()` → `TextFormat` has `setColor(Color)`.
- Produces: a seeded DEFAULT-tier colour on `PlotDescriptor.fmt`, and a `PlotDescriptor.initDefaultFormat(VizContext)` that no longer writes a colour.

**Why this is one task and not two.** `PlotDescriptor.initDefaultFormat(VizContext)` sets `fmt.getDefaultFormat().setColor(GDefaults.DEFAULT_TEXT_COLOR)` unconditionally, and `VGraphPair:1097` calls it on **every render**. Seeding without removing that write is inert — the seed is overwritten on the first repaint. Removing it without seeding loses the ink. Ship together.

Removal is safe because `TextSpec.getColor()` returns `GDefaults.DEFAULT_TEXT_COLOR` when the colour is null (`TextSpec:56`), so the report path and any descriptor that never passes through the seed resolve the same ink they do now. **Verify that rather than trusting it** — Step 5 is what verifies it.

- [ ] **Step 1: Write the failing tests**

```java
   private static Color labelInk(ChartVSAssemblyInfo info) {
      return info.getChartDescriptor().getPlotDescriptor()
         .getTextFormat().getDefaultFormat().getColor();
   }

   @Test
   void aModernChartSeedsTheTitleTierInkOnItsDataLabels() {
      gateOn();
      assertEquals(new Color(0x35342F), labelInk(newChart()),
                   "data labels are primary content, so they take the title tier not the label tier");
   }

   @Test
   void aDarkChartSeedsLightInkOnItsDataLabels() {
      SreeEnv.setProperty("viewsheet.modernVisualization", "true");
      SreeEnv.setProperty("viewsheet.darkMode", "true");
      assertEquals(new Color(0xE6E0E9), labelInk(newChart()),
                   "near-black data labels are unreadable on the dark card");
   }

   @Test
   void anUnmarkedChartKeepsTheLegacyDataLabelInk() {
      gateOff();
      assertEquals(GDefaults.DEFAULT_TEXT_COLOR, labelInk(newChart()));
   }

   @Test
   void aSeededDataLabelInkSurvivesARepaint() {
      SreeEnv.setProperty("viewsheet.modernVisualization", "true");
      SreeEnv.setProperty("viewsheet.darkMode", "true");
      ChartVSAssemblyInfo info = newChart();
      PlotDescriptor plot = info.getChartDescriptor().getPlotDescriptor();
      // VGraphPair calls this on every render; before the fix it clobbered the seed
      plot.initDefaultFormat(VizContext.of(info));
      assertEquals(new Color(0xE6E0E9), labelInk(info),
                   "initDefaultFormat must not overwrite the seeded ink on repaint");
   }
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./mvnw -q -pl community/core test -Dtest=SeedChromeDefaultsTest`
Expected: FAIL. The two modern tests and `aSeededDataLabelInkSurvivesARepaint` report `#4b4b4b`. `anUnmarkedChartKeepsTheLegacyDataLabelInk` should already PASS.

- [ ] **Step 3: Seed the ink**

In `ChartVSAssemblyInfo.seedChromeDefaults`, add to the `if(ctx.modern)` branch:

```java
         // the data-label ink. Seeded, so PlotDescriptor.initDefaultFormat no longer writes a
         // colour at all - it ran on every render and overwrote this
         plotDesc.getTextFormat().getDefaultFormat()
            .setColor(VSChartChromeDefaults.titleColor(ctx));
```

And to the `else` branch:

```java
         plotDesc.getTextFormat().getDefaultFormat().setColor(GDefaults.DEFAULT_TEXT_COLOR);
```

- [ ] **Step 4: Delete the render-time write**

In `PlotDescriptor.initDefaultFormat(VizContext ctx)` (`:71-76`), remove the colour line and record why:

```java
   public void initDefaultFormat(VizContext ctx) {
      // no colour write here: the data-label ink is seeded at creation by
      // ChartVSAssemblyInfo.seedChromeDefaults, and this method runs on every render
      // (VGraphPair), so writing one here overwrote the seed on the first repaint. TextSpec
      // .getColor() falls back to DEFAULT_TEXT_COLOR when null, which is what the report path
      // and any descriptor that never reaches the seed resolve.
      // font follows "is a viewsheet chart", not the modern gate
      fmt.getDefaultFormat().setFont(ctx != VizContext.LEGACY ?
         VSAssemblyInfo.getDefaultFont(VSUtil.getDefaultFont()) : VSUtil.getDefaultFont());
   }
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `./mvnw -q -pl community/core test -Dtest=SeedChromeDefaultsTest`
Expected: PASS, all four new tests plus Task 1's four.

- [ ] **Step 6: Run the full core suite**

Run: `./mvnw -q -pl community/core test`
Expected: 0 failures, 0 errors.

This step is the real verification of Step 4's `TextSpec:56` fallback claim. **If a report-path or exporter test fails on a null colour, do not patch the test** — it means a render path reads `getColor()` off the `TextFormat` directly instead of through `TextSpec`, which is the format-path enumeration gap group 1's Critical came from. Report it and stop.

- [ ] **Step 7: Commit**

```bash
git -C E:/StyleBI/stylebi-enterprise/community add core/src/main/java/inetsoft/uql/viewsheet/internal/ChartVSAssemblyInfo.java core/src/main/java/inetsoft/uql/viewsheet/graph/PlotDescriptor.java core/src/test/java/inetsoft/uql/viewsheet/internal/SeedChromeDefaultsTest.java
```

```bash
git -C E:/StyleBI/stylebi-enterprise/community commit -m "feat(viewsheet): seed the chart's data-label ink instead of writing it per render"
```

---

### Task 3: Delete the ten redundant resolver calls in the composer pane

**Files:**
- Modify: `core/src/main/java/inetsoft/web/composer/model/vs/ChartLinePaneModel.java` — ten resolver calls plus four now-unused `VizContext` parameters
- Modify: `core/src/main/java/inetsoft/web/composer/vs/dialog/ChartPropertyDialogService.java:120,379`
- Test: `core/src/test/java/inetsoft/web/composer/model/vs/ChartLinePaneModelTest.java` (create)

**Interfaces:**
- Consumes: Task 1's seeded values — `plotDesc.getXGridColor()` and its four siblings now return the resolved colour directly.

**Why Task 1 can land before this one without breaking the dialog.** `resolveGridlineColor` substitutes
**only** when its argument still equals `GDefaults.DEFAULT_GRIDLINE_COLOR`
(`VSChartChromeDefaults:85-88`), so once Task 1 seeds `#E8E5DE` the resolver sees a non-legacy value and
returns it unchanged. It is idempotent against a seeded value, which is what makes the two tasks
independently shippable rather than a single atomic change. Note in passing that the facet colour is
constructed from `DEFAULT_LINE_COLOR` while the resolver compares against `DEFAULT_GRIDLINE_COLOR` —
both are `#eeeeee`, so it works by coincidence of value rather than by design. Do not rely on that
coincidence anywhere new.
- Produces: `ChartLinePaneModel(ChartInfo, PlotDescriptor)` and `updateChartLinePaneModel(ChartInfo, PlotDescriptor)` — both without the `VizContext` parameter.

**Why the read and write sides must change together.** The ten calls are two kinds, and they are coupled:

- **Five read-side**, in `getPlotXYGird` (`:228`, `:233`, `:242`, `:247`) and `getPlotFacetGrid` (`:185`) — they resolve a value on the way *out* to the dialog.
- **Five write-side**, in `updatePlotXYGird` (`:151`, `:157`, `:166`, `:172`) and `updateChartLinePaneModel` (`:70`) — they form a **change-detection guard**: `if(!Tool.equals(color, resolve(plotDesc.getXGridColor(), ctx))) { plotDesc.setXGridColor(color, false); }`, so the USER tier is only written when the submitted colour differs from the default.

Change one side only and the guard compares a resolved value against an unresolved one, so **every Apply writes a USER-tier colour** — the defect class the palette work spent five rounds closing. After Task 1 the stored value *is* the resolved value, so both sides simply drop the wrapper and the guard keeps working.

- [ ] **Step 1: Write the failing test**

Create `core/src/test/java/inetsoft/web/composer/model/vs/ChartLinePaneModelTest.java`:

```java
package inetsoft.web.composer.model.vs;

import inetsoft.sree.SreeEnv;
import inetsoft.test.*;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.internal.ChartVSAssemblyInfo;
import inetsoft.uql.CompositeValue;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.Color;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The Chart Line pane reads and writes one stored gridline colour. Before the values were seeded
 * it resolved them on both sides, so the pane displayed the modern colour while the canvas drew
 * the legacy one; the change-detection guard on the write side depended on that same resolution,
 * which is why both sides had to move together.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class ChartLinePaneModelTest {
   @AfterEach
   void reset() {
      SreeEnv.setProperty("viewsheet.modernVisualization", null);
      SreeEnv.setProperty("viewsheet.darkMode", null);
   }

   private ChartVSAssemblyInfo newChart() {
      Viewsheet vs = new Viewsheet();
      ChartVSAssembly chart = new ChartVSAssembly(vs, "Chart1");
      chart.getVSAssemblyInfo().initDefaultFormat();
      return (ChartVSAssemblyInfo) chart.getVSAssemblyInfo();
   }

   @Test
   void thePaneReportsTheSeededDarkGridlineTheCanvasWillDraw() {
      SreeEnv.setProperty("viewsheet.modernVisualization", "true");
      SreeEnv.setProperty("viewsheet.darkMode", "true");
      ChartVSAssemblyInfo info = newChart();
      PlotDescriptor plot = info.getChartDescriptor().getPlotDescriptor();
      ChartLinePaneModel model = new ChartLinePaneModel(info.getVSChartInfo(), plot);

      // the accessor really is getyGridLineColor - lowercase after "get", matching the field
      assertEquals("#3a383d", model.getyGridLineColor().toLowerCase(),
                   "the pane must report the stored value, which is what the canvas draws");
   }

   @Test
   void resubmittingTheReportedColourWritesNoUserTier() {
      SreeEnv.setProperty("viewsheet.modernVisualization", "true");
      SreeEnv.setProperty("viewsheet.darkMode", "true");
      ChartVSAssemblyInfo info = newChart();
      PlotDescriptor plot = info.getChartDescriptor().getPlotDescriptor();
      ChartLinePaneModel model = new ChartLinePaneModel(info.getVSChartInfo(), plot);

      // an Apply with nothing changed: the guard must not promote the default into the USER tier
      model.updateChartLinePaneModel(info.getVSChartInfo(), plot);

      // the discriminating probe: overwrite the DEFAULT tier and see whether it shows through.
      // A USER tier written by the Apply would mask this, so a stale green means the guard broke.
      // Do NOT probe by clearing USER instead - a USER copy of the same colour is indistinguishable
      // from no USER tier at all, so that version of this test passes vacuously either way.
      plot.setYGridColor(Color.GREEN, CompositeValue.Type.DEFAULT);
      assertEquals(Color.GREEN, plot.getYGridColor(),
                   "an unchanged Apply must not leave a USER tier masking the default");
   }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./mvnw -q -pl community/core test -Dtest=ChartLinePaneModelTest`
Expected: FAIL to **compile** — `ChartLinePaneModel` still takes three constructor arguments. That is the intended first failure; it pins the signature change.

- [ ] **Step 3: Drop the resolver from the five read-side calls**

In `ChartLinePaneModel.getPlotFacetGrid`, replace the resolved read with the raw getter and drop the parameter:

```java
   private void getPlotFacetGrid(PlotDescriptor plotDesc) {
      if(plotDesc.getFacetGridColor() != null) {
         facetGridColor = "#" + Tool.colorToHTMLString(plotDesc.getFacetGridColor());
      }

      facetGrid = plotDesc.isFacetGrid();
   }
```

In `getPlotXYGird`, do the same at all four sites — each
`Tool.colorToHTMLString(VSChartChromeDefaults.resolveGridlineColor(plotDesc.getXGridColor(), ctx))`
becomes `Tool.colorToHTMLString(plotDesc.getXGridColor())`, and likewise for `getYGridColor()`. Change its signature to `getPlotXYGird(ChartInfo cinfo, PlotDescriptor plotDesc)`.

- [ ] **Step 4: Drop the resolver from the five write-side guards**

In `updatePlotXYGird`, each guard loses only the wrapper — the comparison itself stays:

```java
         if(!Tool.equals(color, plotDesc.getXGridColor())) {
            plotDesc.setXGridColor(color, false);
         }
```

Apply at all four sites (x and y, in both the inverted and non-inverted branch) and change the signature to `updatePlotXYGird(ChartInfo cinfo, PlotDescriptor plotDesc)`. Then the facet guard at `:70`, inside `updateChartLinePaneModel`:

```java
      if(!Tool.equals(color, plotDesc.getFacetGridColor())) {
         plotDesc.setFacetGridColor(color, false);
      }
```

- [ ] **Step 5: Drop the now-unused parameters and update the two callers**

`ctx` is now unused in the constructor, `updateChartLinePaneModel`, `getPlotXYGird`, `getPlotFacetGrid` and `updatePlotXYGird`. Remove it from all five, and remove the `VSChartChromeDefaults` import if nothing else in the file uses it.

Then fix the only two callers, both in `ChartPropertyDialogService`:

```java
// :120
            new ChartLinePaneModel(vsChartInfo, chartDescriptor.getPlotDescriptor()),
```

```java
// :379
      chartLinePaneModel.updateChartLinePaneModel(vsChartInfo, chartDescriptor.getPlotDescriptor());
```

Delete whatever `VizContext` expression each call site was passing, and drop any import or local that becomes unused.

- [ ] **Step 6: Run the test to verify it passes**

Run: `./mvnw -q -pl community/core test -Dtest=ChartLinePaneModelTest`
Expected: PASS, both tests.

- [ ] **Step 7: Run the full core suite**

Run: `./mvnw -q -pl community/core test`
Expected: 0 failures, 0 errors.

- [ ] **Step 8: Commit**

```bash
git -C E:/StyleBI/stylebi-enterprise/community add core/src/main/java/inetsoft/web/composer/model/vs/ChartLinePaneModel.java core/src/main/java/inetsoft/web/composer/vs/dialog/ChartPropertyDialogService.java core/src/test/java/inetsoft/web/composer/model/vs/ChartLinePaneModelTest.java
```

```bash
git -C E:/StyleBI/stylebi-enterprise/community commit -m "refactor(composer): read the chart's gridline colour from one stored value"
```

---

### Task 4: Pin the reversibility and restore paths

**Files:**
- Test: `core/src/test/java/inetsoft/uql/viewsheet/internal/SeedChromeDefaultsTest.java`

**Interfaces:**
- Consumes: everything Tasks 1–3 produced. Adds no production code.

**Why this is its own task.** Tasks 1–3 prove the seed writes the right value. This proves it can be taken *back*, which is a different property and the one Revert depends on. It is separated so a reviewer can reject the reversibility story without rejecting the seed.

- [ ] **Step 1: Write the tests**

```java
   @Test
   void revertRestoresTheLegacyGridlineAndInk() {
      gateOn();
      ChartVSAssemblyInfo info = newChart();
      assertEquals(new Color(0xE8E5DE), plot(info).getYGridColor(), "seeded modern first");

      // Revert clears the mark and re-runs the same hook with an unmarked context
      info.setVizMark(null);
      info.initDefaultFormat();

      assertEquals(ChartLineColor.getPlotLineColor(GDefaults.DEFAULT_GRIDLINE_COLOR, "y"),
                   plot(info).getYGridColor(), "the legacy branch must write, not skip");
      assertEquals(GDefaults.DEFAULT_TEXT_COLOR, labelInk(info));
   }

   @Test
   void modernizeSeedsAnExistingUnmarkedChart() {
      gateOff();
      ChartVSAssemblyInfo info = newChart();
      assertEquals(GDefaults.DEFAULT_LINE_COLOR, plot(info).getFacetGridColor());

      gateOn();
      info.setVizMark(VizMark.MODERN_LIGHT);
      info.initDefaultFormat();

      assertEquals(new Color(0xE8E5DE), plot(info).getFacetGridColor());
   }

   @Test
   void aReSeedResolvesAgainstTheMarkNotTheGate() {
      // the bookmark-restore contract: parseState re-seeds, and the mark decides, so a chart
      // marked light in a dark org must not pick up dark values
      SreeEnv.setProperty("viewsheet.modernVisualization", "true");
      SreeEnv.setProperty("viewsheet.darkMode", "true");
      ChartVSAssemblyInfo info = newChart();
      assertEquals(new Color(0x3A383D), plot(info).getXGridColor(), "dark org, dark seed");

      info.setVizMark(VizMark.MODERN_LIGHT);
      info.initDefaultFormat();

      assertEquals(new Color(0xE8E5DE), plot(info).getXGridColor(),
                   "the mark decides, not the live org property");
   }

   @Test
   void theSeededPlotChromeTravelsInTheAsset() throws Exception {
      // the whole argument for seeding rather than resolving at render: a read-time value is not
      // in the asset, so an export into a build without this work renders the card mixed
      SreeEnv.setProperty("viewsheet.modernVisualization", "true");
      SreeEnv.setProperty("viewsheet.darkMode", "true");
      ChartVSAssemblyInfo restored = roundTrip(newChart());

      assertEquals(new Color(0x3A383D), plot(restored).getYGridColor(),
                   "the asset itself carries the seeded gridline");
      assertEquals(new Color(0xE6E0E9), labelInk(restored),
                   "the asset itself carries the seeded data-label ink");
   }
```

`writeXml(...)` and `roundTrip(...)` are existing private helpers in this class (near its foot) — reuse
them rather than writing new ones.

**One honest limitation of this task.** `aReSeedResolvesAgainstTheMarkNotTheGate` exercises the
mark-decides contract by calling `setVizMark` then `initDefaultFormat`, which is a **proxy** for
`AbstractVSAssembly.parseState`'s re-seed rather than the real bookmark path. It pins the property the
bookmark path depends on — that a re-seed follows the mark, not the live org property — but it does not
prove `parseState` reaches these six values. The round-trip test above covers asset XML, which is a
different serialisation from `<state_*>`. A real bookmark test needs a `RuntimeViewsheet` and a
bookmark write/restore, which is a bootstrapped-server test; manual check 5 in Task 5 is what covers it
in the meantime. Do not describe this task as full bookmark coverage.

`VizMark` is in the same package as the test, so it needs no import.

- [ ] **Step 2: Run the tests**

Run: `./mvnw -q -pl community/core test -Dtest=SeedChromeDefaultsTest`
Expected: PASS. These describe behaviour Tasks 1–2 already built; they are a net, not a driver.

If `aReSeedResolvesAgainstTheMarkNotTheGate` fails, the seed is reading `ctx` from the live property rather than from the mark — check that `VizContext.of(info)` is what reaches `seedChromeDefaults`, not `VizContext.ofGate()`.

- [ ] **Step 3: Commit**

```bash
git -C E:/StyleBI/stylebi-enterprise/community add core/src/test/java/inetsoft/uql/viewsheet/internal/SeedChromeDefaultsTest.java
```

```bash
git -C E:/StyleBI/stylebi-enterprise/community commit -m "test(viewsheet): pin the plot chrome's revert and re-seed paths"
```

---

### Task 5: Export parity and the manual pass

**Files:**
- No test file. Step 1 explains why; do not add one to match the shape of the other tasks.

**Interfaces:**
- Consumes: Tasks 1–4. Adds no production code.

- [ ] **Step 1: Establish, and record, that no automated Excel test applies here**

Group 2's Excel doctrine exists for **unfilled cells that paint no page**: `paintsPageBackground()` returns false on the abstract `ExcelVSExporter`, so light seeded ink would be invisible on a white cell and `applyDarkOptOutInPlace` puts the legacy ink back. **None of that reaches the six values in this plan.** A chart is rasterised into the sheet as an image with its own dark card painted behind it, so its gridlines and data labels are pixels inside that image rather than cell formats the exporter substitutes.

`ExporterDarkOptOutTest` is the natural-looking home and is the wrong one. It is a **pure unit test** — `@Tag("core")`, no Spring context — asserting directly against `AbstractVSExporter.applyDarkOptOutInPlace`. Its own class comment says it tests "at the substitution rather than through an exporter, which needs a bootstrapped server - so a mis-wiring at the call site is not caught here. Recorded, not hidden." Adding a chart-seed assertion there would need the Spring context that class deliberately avoids, and would assert a value the Excel path never touches — a test that looks like verification and is not.

**So export parity is verified by the manual pass in Step 3, and this says so rather than papering over it.** If you disagree after reading the code, the place to add coverage is a new `@ExtendWith(SpringExtension.class)` class that renders through a real exporter — a bootstrapped-server test, and its own piece of work rather than a step here.

- [ ] **Step 2: Cross-module build**

Run from `E:/StyleBI/stylebi-enterprise`: `./mvnw -q clean install -DskipTests "-Pcommunity,enterprise"`
Expected: BUILD SUCCESS.

Use `clean install`, not an incremental one. Two signature changes on this branch reached `utils/inetsoft-xml-formats`, and an incremental 77-module `install` reported SUCCESS over a break that only `clean` exposed. Task 3 changes two public signatures, so this applies.

- [ ] **Step 3: The manual matrix**

Automated tests prove the code and the tests agree with each other, not that a screen agrees with either. Run these in a browser and record the outcome in Task 6's commit body:

1. A **dark** dashboard, new chart, default settings — horizontal gridlines are a dark hairline, not near-white. This is the headline defect; if it still looks wrong, nothing else in this list matters.
2. The same chart's **data labels** turned on — light ink, readable on the card.
3. A **light** modern chart — gridlines are the warm `#E8E5DE`, a shade warmer than before, and nothing else moved.
4. The composer's **Chart Line pane** on both — the swatch matches what the canvas draws, in light and dark. This is the WYSIWYG break closing.
5. Set a gridline colour by hand, Apply, reopen the pane — the chosen colour persists and survives a **Revert**. Then Apply twice with nothing changed and confirm the colour still reads as a default rather than becoming sticky: that is the change-detection guard Task 3 preserved.
6. **Modernize** an old chart — gridlines and labels take the modern values.
7. A **faceted** chart in dark — facet lines match the gridlines.
8. **Export** the dark dashboard to PDF, PNG and Excel — gridlines and labels match the screen in all three. This is what stands in for the absent automated Excel test.
9. An **unmarked** chart in a gate-on org, light and dark — unchanged from before this work.

- [ ] **Step 4: No commit**

This task produces no files. Its output is the manual result, which Task 6 records.

---

### Task 6: Update the roadmap and the decisions record

**Files:**
- Modify: `docs/superpowers/specs/lookfeel/chart-card-roadmap.md`
- Modify: `docs/superpowers/specs/lookfeel/2026-09-02-chart-interior-dark-chrome-design.md`

**Interfaces:**
- Consumes: the outcome of Tasks 1–5. No code.

**Why the plan includes this.** Every feature commit on this branch has a paired docs commit, and the roadmap is the entry point CLAUDE.md points readers at. A shipped item that still reads "ranked #2, blocked on a design pass" is how the file went stale four times before.

- [ ] **Step 1: Add the Done-table row**

In the roadmap's `## Done` table, add a row. **Use the commit subject, not a hash** — the table's own preamble explains why, and this branch has been rebased four times:

```markdown
| **Chart interior dark chrome** — six `PlotDescriptor` values seeded at creation: the data-label ink and the x/y gridline, facet, diagonal and quadrant line colours. The roadmap had this as M-L design-first on the grounds that `GDefaults` has no dark branch; that is true and irrelevant, because `VSChartChromeDefaults` intercepts first and the dark palette already existed. **One of the six was a default-on defect** — `yGridStyle` defaults to `THIN_LINE`, so a modern-dark chart drew near-white `#eeeeee` gridlines on a `#252428` card. **And the composer had been disagreeing with the canvas in both modes**: the Chart Line pane resolved these values at ten sites while the render path took them raw at seven, so the pane displayed `#E8E5DE` while the canvas drew `#eeeeee` — invisible in light, glaring in dark. Seeding closed it by construction and made those ten calls deletable. Light mode changed too, deliberately. Design: [the dark chrome design](./2026-09-02-chart-interior-dark-chrome-design.md) | *"seed the plot's structural line colours"* + *"seed the chart's data-label ink instead of writing it per render"* + *"read the chart's gridline colour from one stored value"* |
```

- [ ] **Step 2: Strike the ranking row**

The ranking's row 2 reads **Chart interior dark palette** with "a design pass" in its Blocked-on column. Strike it in place rather than re-deriving the table — the file was re-derived on 2026-09-02 and a second re-derivation the same week loses more than it fixes:

```markdown
| 2 | ~~**Chart interior dark palette**~~ **DONE 2026-09-02** | it needed no design pass and no new colour: `VSChartChromeDefaults` already held the dark values and `GDefaults` having no dark branch was irrelevant. What it needed was six values the dark pass missed, one of them a default-on defect. See the Done table | — | — | full record in [the design](./2026-09-02-chart-interior-dark-chrome-design.md) |
```

- [ ] **Step 3: Add the "what the implementation found" section**

Append to the design doc, matching group 1's and group 2's convention. Write what execution actually discovered — **not** a restatement of the plan. If nothing surprising happened, say that in one line; a section that invents findings is worse than a short one.

Four questions worth answering explicitly, because each was a stated assumption this plan could not
verify in advance:

1. Did removing `initDefaultFormat`'s colour write break any render path — does anything read
   `getColor()` off the `TextFormat` directly rather than through `TextSpec`? (Task 2, Step 6.)
2. Did Task 3's change-detection guard keep working, or does an unchanged Apply now write a USER
   tier? (Task 5, manual check 5.)
3. Were the seven raw render sites the complete set, or did the manual pass find a gridline the
   design did not enumerate?
4. Was the light-mode shift actually imperceptible at `(6, 9, 16)` per channel, or is it visible
   enough to belong in release notes?

- [ ] **Step 4: Update the §4 divergence note if it changed**

§4 records that `VSChartChromeDefaults` was never part of the read-time migration, making the chart interior the only chrome area still computing at render, and names that as group 3. If Tasks 1–5 changed nothing about that, leave it. If the export pass found another read-time site, add it there.

- [ ] **Step 5: Commit**

```bash
git -C E:/StyleBI/stylebi-enterprise/community add docs/superpowers/specs/lookfeel/chart-card-roadmap.md docs/superpowers/specs/lookfeel/2026-09-02-chart-interior-dark-chrome-design.md
```

```bash
git -C E:/StyleBI/stylebi-enterprise/community commit -m "docs(lookfeel): record the chart interior's seeded dark chrome"
```

---

## Task Dependencies

```
Task 1 (five line colours) ──┬──→ Task 3 (delete the ten resolver calls)
                             │     needs the stored value to BE the resolved one
Task 2 (data-label ink) ─────┤
  independent of Task 1      │
                             └──→ Task 4 (reversibility) ──→ Task 5 (export + manual)
                                                                  │
                                                                  └──→ Task 6 (docs)
```

Tasks 1 and 2 are independent and may go in either order. Task 3 needs Task 1. Task 4 needs both 1 and 2. Task 5 adds no code and produces the manual result. Task 6 goes last, since it records what 1–5 found.
