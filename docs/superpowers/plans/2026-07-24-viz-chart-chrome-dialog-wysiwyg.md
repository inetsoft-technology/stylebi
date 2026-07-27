# Chart-Chrome Dialog WYSIWYG Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Under the modern chart-chrome gate, make the chart property dialogs (axis line, gridlines, legend border) **display** the modern colors that already render — completing the design-time WYSIWYG rule for the chart-internal chrome that Phase 7 explicitly deferred — **without persisting** anything (gate-off byte-identical, no descriptor dirtied).

**Architecture:** Mirror Phase 7's `FormatPainterService` WYSIWYG fix, adapted to the chart dialog subsystem. Two new pure resolvers on `VSChartChromeDefaults` (`resolveGridlineColor`, `resolveLegendBorderColor`) join the shipped `resolveAxisLineColor`; each substitutes the modern neutral only when the gate is on and the current value is the legacy default. Each dialog model applies the resolver on **read** (so the picker shows modern) and, on **write-back**, skips the descriptor setter when the incoming color equals the resolved-display value (so an untouched modern value is never persisted). The Phase 6 render overlays (`CSSChartStyles`, the `resolveAxisLineColor` calls in the graph engine) are unchanged — this only makes the editors match the render.

**Tech Stack:** Java 21 (core `web` binding/composer dialog models + the `uql.viewsheet.internal` chrome-defaults class), JUnit 5 (`@Tag("core")`, Spring `@SreeHome`). No Angular change — the dialog models are server-populated; the modern hex strings flow to the existing color pickers unchanged.

## Global Constraints

Copied from the Phase 3 WYSIWYG rule, the Phase 7 grounding, and the shipped `VSChartChromeDefaults`; every task's requirements implicitly include this section.

- **WYSIWYG rule (Phase 3 / Phase 7).** The composer must match the viewer. The chart *render* is already modern (Phase 6: `CSSChartStyles` writes the CSS tier for gridline/legend; `resolveAxisLineColor` substitutes the axis line at `GraphGenerator`/`RadarGraphGenerator`). This plan makes the property **dialogs** show the same modern colors.
- **Non-persisted / gate-off byte-identical.** Nothing here may write a value to the descriptor when the user did not change it. Gridline/legend already skip a same-value USER-tier write (`setXGridColor(c, false)` / `setBorderColor(c, false)` compare against the current value); the axis line is a plain field with an **unconditional** overwrite, so it needs an explicit guard. With the gate off, every resolver returns its input unchanged, so read == legacy and write is a no-op.
- **Defaults-only.** A resolver substitutes the modern neutral **only** when the current color is the exact legacy default (`GDefaults.DEFAULT_GRIDLINE_COLOR` / `GDefaults.DEFAULT_LINE_COLOR`, both `#EEEEEE`). A user-picked or `format.css`-resolved color (anything else) is shown and round-tripped unchanged.
- **Gate.** `VSChartChromeDefaults.isModern()` (= `VSDensityDefaults.isModern()` on `viewsheet.modernVisualization`, org-scoped, **and** `!"false".equals(SreeEnv.getProperty("viewsheet.modernChartChrome", false, true))`). No new property.
- **Viewsheet-scoped, no report-chart risk.** All three dialogs are viewsheet composer dialogs; report charts do not use them. The change lives entirely in the dialog models, so report-chart defaults are untouched (unlike a descriptor-constructor seed would be).
- **Colors.** Modern gridline / axis-line / legend-border all = `VSChartChromeDefaults.gridlineColor()` = `legendBorderColor()` = `#E8E5DE`. Legacy = `#EEEEEE`.

---

## Grounding (verified against current code, branch `viz-updates`, 2026-07-24)

### The deferred gap this closes

`visualization-phase7-implementation-plan.md:396-398`: *"Remaining picker gap: chart internal chrome — gridline/axis/legend colors from Phase 6 — is edited via the chart-specific format dialogs / `ChartDescriptor`, a separate subsystem from `FormatPainterService`, not covered here."*

### The chrome-defaults class

`core/src/main/java/inetsoft/uql/viewsheet/internal/VSChartChromeDefaults.java`:

| Anchor | Location | Note |
|---|---|---|
| `gridlineColor()` / `legendBorderColor()` | `:52-59` | both return `GRIDLINE` = `new Color(0xE8E5DE)` (`:83`). |
| `resolveAxisLineColor(Color)` | `:77-79` | `isModern() && GDefaults.DEFAULT_LINE_COLOR.equals(current) ? GRIDLINE : current`. The template for the two new resolvers. |
| `isModern()` | `:44-49` | `VSDensityDefaults.isModern()` + `viewsheet.modernChartChrome`. |

`GDefaults`: `DEFAULT_GRIDLINE_COLOR = new Color(0xeeeeee)` (`:137`), `DEFAULT_LINE_COLOR = new Color(0xeeeeee)` (`:141`).

### The three dialog seams

| Dialog | Read (populate model) | Write (apply to descriptor) | Write-back protection today |
|---|---|---|---|
| **Axis line** — `core/src/main/java/inetsoft/web/graph/model/dialog/AxisPropertyDialogModel.java` | `:76-79` `axisLinePaneModel.setLineColor("#" + Tool.colorToHTMLString(axisDesc.getLineColor()))` | `:256-257` `Color color = Tool.getColorFromHexString(...); axisDesc.setLineColor(color);` | **NONE** — plain field, unconditional overwrite, serialized when non-null. Needs a guard. |
| **Gridline** — `core/src/main/java/inetsoft/web/composer/model/vs/ChartLinePaneModel.java` | `getPlotXYGird` `:206-224` (X/Y, inverted-aware) + `getPlotFacetGrid` `:164-166` | `updatePlotXYGird` `:141-156` (`setXGridColor/setYGridColor(color, false)`) + facet `:65-67` (`setFacetGridColor(color, false)`) | `force=false` skips a same-value USER write vs the **current** value — but the current value is the legacy default, so a *different* modern display WOULD persist. Needs a guard vs the resolved-display value. |
| **Legend border** — `core/src/main/java/inetsoft/web/graph/model/dialog/LegendFormatDialogModel.java` | `:63-66` `generalPaneModel.setFillColor("#" + Tool.colorToHTMLString(legendsDesc.getBorderColor()))` | `:123-124` `Color color = Tool.getColorFromHexString(...); legendsDesc.setBorderColor(color, false);` | Same as gridline. Needs a guard. |

`Tool.colorToHTMLString`, `Tool.getColorFromHexString`, and `Tool.equals` are all already imported and used in each of the three files.

### Why the guard compares against `resolve...(currentDescriptorValue)`

On write-back the descriptor still holds its pre-apply value (the setter is the line being guarded). `resolveX(descriptor.getX())` therefore reproduces exactly the string the model was populated with on read. If the incoming color equals it, the user did not touch the field → skip the setter → nothing persists. If the user picked a different color, it differs → the setter runs → the user's choice persists (USER tier / the plain field). This is the same "compare against the echoed original" idea as `FormatPainterService.setVOFormat` (`:898-911`), with the resolver standing in for the echo.

---

## Decisions

- **R1 — Display substitution + write-back guard, not descriptor seeds.** Seeding the descriptor default would persist (axis line) or risk modernizing report charts (constructor seeds have no viewsheet context). The dialog-model approach is non-persisted, gate-off byte-identical, and viewsheet-scoped — matching how Phase 7 fixed the text/title pickers.
- **R2 — Keep the Phase 6 render overlays.** `CSSChartStyles` (gridline/legend CSS tier) and the `resolveAxisLineColor` calls in the graph engine stay; the render is already correct. This plan only aligns the dialogs, so render and dialog agree.
- **R3 — Value-equality against the exact legacy default.** Each resolver substitutes only when `current` equals the legacy constant for that field, so a user/`format.css` color is preserved — identical to the shipped `resolveAxisLineColor` contract.
- **R4 — Guard vs the resolved-display value in every write-back, including the already-"protected" gridline/legend.** Their `force=false` guard compares against the raw current value (legacy), which differs from the modern display, so without the added guard an untouched modern display would persist. The guard closes that.

---

## Task 1: Add `resolveGridlineColor` + `resolveLegendBorderColor` to `VSChartChromeDefaults` (+ unit tests)

**Files:**
- Modify: `core/src/main/java/inetsoft/uql/viewsheet/internal/VSChartChromeDefaults.java` (add two methods after `resolveAxisLineColor` at `:79`)
- Test: `core/src/test/java/inetsoft/uql/viewsheet/graph/CSSChartStylesModernChromeTest.java` (add two tests next to the axis-line resolver tests at `:151-182`)

**Interfaces:**
- Consumes: `isModern()`, `gridlineColor()`, `legendBorderColor()` (existing); `GDefaults.DEFAULT_GRIDLINE_COLOR`, `GDefaults.DEFAULT_LINE_COLOR` (already imported — `GDefaults` is used at `:78,83`).
- Produces (used by Tasks 2–4):
  - `public static Color resolveGridlineColor(Color current)` — modern gridline iff gate on and `current` is `#EEEEEE`; else `current` (null-safe).
  - `public static Color resolveLegendBorderColor(Color current)` — modern legend border iff gate on and `current` is `#EEEEEE`; else `current` (null-safe).

- [ ] **Step 1: Write the failing tests**

Append these two methods to `CSSChartStylesModernChromeTest` (it already has `axisLineResolvesToModernWhenGateOnAndDefault` / `axisLineUnchangedWhenGateOff`; reuse their `withGate`-style toggling of `viewsheet.modernVisualization`):

```java
   @Test
   void gridlineResolvesToModernWhenGateOnAndDefault() {
      String saved = SreeEnv.getProperty("viewsheet.modernVisualization");

      try {
         SreeEnv.setProperty("viewsheet.modernVisualization", "true");
         assertEquals(VSChartChromeDefaults.gridlineColor(),
                      VSChartChromeDefaults.resolveGridlineColor(GDefaults.DEFAULT_GRIDLINE_COLOR),
                      "legacy-default gridline shows the modern neutral under the gate");
         assertEquals(Color.RED, VSChartChromeDefaults.resolveGridlineColor(Color.RED),
                      "a user/customer gridline color is preserved");
         assertNull(VSChartChromeDefaults.resolveGridlineColor(null),
                    "null (no gridline color) stays null");
         assertEquals(VSChartChromeDefaults.legendBorderColor(),
                      VSChartChromeDefaults.resolveLegendBorderColor(GDefaults.DEFAULT_LINE_COLOR),
                      "legacy-default legend border shows the modern neutral under the gate");
         assertEquals(Color.RED, VSChartChromeDefaults.resolveLegendBorderColor(Color.RED),
                      "a user/customer legend border color is preserved");
      }
      finally {
         SreeEnv.setProperty("viewsheet.modernVisualization", saved);
      }
   }

   @Test
   void gridlineAndLegendUnchangedWhenGateOff() {
      String saved = SreeEnv.getProperty("viewsheet.modernVisualization");

      try {
         SreeEnv.setProperty("viewsheet.modernVisualization", "false");
         assertEquals(GDefaults.DEFAULT_GRIDLINE_COLOR,
                      VSChartChromeDefaults.resolveGridlineColor(GDefaults.DEFAULT_GRIDLINE_COLOR),
                      "gate off leaves the legacy gridline color unchanged");
         assertEquals(GDefaults.DEFAULT_LINE_COLOR,
                      VSChartChromeDefaults.resolveLegendBorderColor(GDefaults.DEFAULT_LINE_COLOR),
                      "gate off leaves the legacy legend border color unchanged");
      }
      finally {
         SreeEnv.setProperty("viewsheet.modernVisualization", saved);
      }
   }
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./mvnw -q -pl core test -Dtest=CSSChartStylesModernChromeTest -DfailIfNoTests=false`
Expected: FAIL — `resolveGridlineColor` / `resolveLegendBorderColor` do not exist (compilation error).

- [ ] **Step 3: Add the resolvers**

In `VSChartChromeDefaults.java`, directly after `resolveAxisLineColor` (`:79`), add:

```java
   /**
    * Resolve a gridline color for display: when the gate is on and the color is still the legacy
    * default, substitute the modern gridline neutral; otherwise (a customer/user color, or gate off)
    * leave it unchanged. Compared against the hardcoded fallback so a format.css or user-picker color
    * is preserved.
    */
   public static Color resolveGridlineColor(Color current) {
      return isModern() && GDefaults.DEFAULT_GRIDLINE_COLOR.equals(current) ? GRIDLINE : current;
   }

   /**
    * Resolve a legend-border color for display: modern neutral iff the gate is on and the color is
    * still the legacy default; otherwise unchanged.
    */
   public static Color resolveLegendBorderColor(Color current) {
      return isModern() && GDefaults.DEFAULT_LINE_COLOR.equals(current) ? GRIDLINE : current;
   }
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./mvnw -q -pl core test -Dtest=CSSChartStylesModernChromeTest -DfailIfNoTests=false`
Expected: PASS (existing tests plus the two new ones).

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/inetsoft/uql/viewsheet/internal/VSChartChromeDefaults.java \
        core/src/test/java/inetsoft/uql/viewsheet/graph/CSSChartStylesModernChromeTest.java
git commit -m "Chart-chrome dialog WYSIWYG: add gridline + legend-border display resolvers"
```

---

## Task 2: Axis-line dialog — show + non-persist the modern axis line

**Files:**
- Modify: `core/src/main/java/inetsoft/web/graph/model/dialog/AxisPropertyDialogModel.java` (read `:76-79`, write `:256-257`, + import)

**Interfaces:**
- Consumes: `VSChartChromeDefaults.resolveAxisLineColor(Color)` (shipped).
- Produces: the Axis Properties "Axis Line & Tick Color" field shows `#E8E5DE` for a new chart under the gate; clicking OK without changing it does not persist a line color.

- [ ] **Step 1: Add the import**

At the top of `AxisPropertyDialogModel.java`, add near the other `inetsoft.uql.viewsheet.internal` imports (add only if a wildcard `inetsoft.uql.viewsheet.internal.*` is not already present — check first):

```java
import inetsoft.uql.viewsheet.internal.VSChartChromeDefaults;
```

- [ ] **Step 2: Substitute on read**

At `AxisPropertyDialogModel.java:76-79`, change:

```java
      if(axisDesc.getLineColor() != null) {
         axisLinePaneModel.setLineColor(
            "#" + Tool.colorToHTMLString(axisDesc.getLineColor()));
      }
```

to:

```java
      if(axisDesc.getLineColor() != null) {
         axisLinePaneModel.setLineColor(
            "#" + Tool.colorToHTMLString(
               VSChartChromeDefaults.resolveAxisLineColor(axisDesc.getLineColor())));
      }
```

- [ ] **Step 3: Guard the write-back**

At `AxisPropertyDialogModel.java:256-257`, change:

```java
      Color color = Tool.getColorFromHexString(axisLinePaneModel.getLineColor());
      axisDesc.setLineColor(color);
```

to:

```java
      Color color = Tool.getColorFromHexString(axisLinePaneModel.getLineColor());

      // WYSIWYG: the panel shows the modern-resolved line color; skip persisting an unchanged
      // (modern-display) value so gate-off stays byte-identical and no descriptor is dirtied
      if(!Tool.equals(color, VSChartChromeDefaults.resolveAxisLineColor(axisDesc.getLineColor()))) {
         axisDesc.setLineColor(color);
      }
```

- [ ] **Step 4: Compile**

Run: `./mvnw -q -pl core test-compile`
Expected: `core` compiles clean.

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/inetsoft/web/graph/model/dialog/AxisPropertyDialogModel.java
git commit -m "Chart-chrome dialog WYSIWYG: axis line shows modern, unchanged value not persisted"
```

> Render-path-adjacent wiring; verified end-to-end in Task 5. The resolver is unit-tested (shipped); the dialog read/write is validated manually (the model needs a full chart info + descriptor to construct, matching the B3 precedent).

---

## Task 3: Gridline dialog — show + non-persist the modern gridlines (X/Y + facet)

**Files:**
- Modify: `core/src/main/java/inetsoft/web/composer/model/vs/ChartLinePaneModel.java` (reads `:206-224`, `:164-166`; writes `:141-156`, `:65-67`; + import)

**Interfaces:**
- Consumes: `VSChartChromeDefaults.resolveGridlineColor(Color)` (Task 1).
- Produces: the chart Line pane's X/Y and facet gridline color fields show `#E8E5DE` for a new chart under the gate; OK-without-change persists nothing.

- [ ] **Step 1: Add the import**

At the top of `ChartLinePaneModel.java`, add near the other `inetsoft.uql.viewsheet.internal` imports (only if no `inetsoft.uql.viewsheet.internal.*` wildcard is present):

```java
import inetsoft.uql.viewsheet.internal.VSChartChromeDefaults;
```

- [ ] **Step 2: Substitute on read — X/Y grid (`getPlotXYGird`, `:206-224`)**

Wrap each grid-color getter with the resolver. Change the four assignments:

```java
         if(plotDesc.getXGridColor() != null) {
            xGridLineColor = "#"+ Tool.colorToHTMLString(plotDesc.getYGridColor());
         }

         if(plotDesc.getYGridColor() != null) {
            yGridLineColor = "#"+ Tool.colorToHTMLString(plotDesc.getXGridColor());
         }
```
(the inverted branch, `:206-212`) to:

```java
         if(plotDesc.getXGridColor() != null) {
            xGridLineColor = "#"+ Tool.colorToHTMLString(
               VSChartChromeDefaults.resolveGridlineColor(plotDesc.getYGridColor()));
         }

         if(plotDesc.getYGridColor() != null) {
            yGridLineColor = "#"+ Tool.colorToHTMLString(
               VSChartChromeDefaults.resolveGridlineColor(plotDesc.getXGridColor()));
         }
```

and the non-inverted branch (`:218-224`):

```java
         if(plotDesc.getXGridColor() != null) {
            xGridLineColor = "#" + Tool.colorToHTMLString(plotDesc.getXGridColor());
         }

         if(plotDesc.getYGridColor() != null) {
            yGridLineColor = "#" + Tool.colorToHTMLString(plotDesc.getYGridColor());
         }
```
to:

```java
         if(plotDesc.getXGridColor() != null) {
            xGridLineColor = "#" + Tool.colorToHTMLString(
               VSChartChromeDefaults.resolveGridlineColor(plotDesc.getXGridColor()));
         }

         if(plotDesc.getYGridColor() != null) {
            yGridLineColor = "#" + Tool.colorToHTMLString(
               VSChartChromeDefaults.resolveGridlineColor(plotDesc.getYGridColor()));
         }
```

- [ ] **Step 3: Substitute on read — facet grid (`getPlotFacetGrid`, `:164-166`)**

Change:

```java
      if(plotDesc.getFacetGridColor() != null) {
         facetGridColor = "#" + Tool.colorToHTMLString(plotDesc.getFacetGridColor());
      }
```

to:

```java
      if(plotDesc.getFacetGridColor() != null) {
         facetGridColor = "#" + Tool.colorToHTMLString(
            VSChartChromeDefaults.resolveGridlineColor(plotDesc.getFacetGridColor()));
      }
```

- [ ] **Step 4: Guard the write-backs — X/Y grid (`updatePlotXYGird`, `:141-156`)**

Guard each `setXGridColor`/`setYGridColor(color, false)` against the resolved-display value. Change the inverted branch (`:144-147`):

```java
         color = Tool.getColorFromHexString(yGridLineColor);
         plotDesc.setXGridColor(color, false);
         color = Tool.getColorFromHexString(xGridLineColor);
         plotDesc.setYGridColor(color, false);
```
to:

```java
         color = Tool.getColorFromHexString(yGridLineColor);

         if(!Tool.equals(color, VSChartChromeDefaults.resolveGridlineColor(plotDesc.getXGridColor()))) {
            plotDesc.setXGridColor(color, false);
         }

         color = Tool.getColorFromHexString(xGridLineColor);

         if(!Tool.equals(color, VSChartChromeDefaults.resolveGridlineColor(plotDesc.getYGridColor()))) {
            plotDesc.setYGridColor(color, false);
         }
```

and the non-inverted branch (`:152-155`):

```java
         color = Tool.getColorFromHexString(xGridLineColor);
         plotDesc.setXGridColor(color, false);
         color = Tool.getColorFromHexString(yGridLineColor);
         plotDesc.setYGridColor(color, false);
```
to:

```java
         color = Tool.getColorFromHexString(xGridLineColor);

         if(!Tool.equals(color, VSChartChromeDefaults.resolveGridlineColor(plotDesc.getXGridColor()))) {
            plotDesc.setXGridColor(color, false);
         }

         color = Tool.getColorFromHexString(yGridLineColor);

         if(!Tool.equals(color, VSChartChromeDefaults.resolveGridlineColor(plotDesc.getYGridColor()))) {
            plotDesc.setYGridColor(color, false);
         }
```

- [ ] **Step 5: Guard the write-back — facet grid (`:65-67`)**

Change:

```java
      plotDesc.setFacetGrid(facetGrid, false);
      color = Tool.getColorFromHexString(facetGridColor);
      plotDesc.setFacetGridColor(color, false);
```

to:

```java
      plotDesc.setFacetGrid(facetGrid, false);
      color = Tool.getColorFromHexString(facetGridColor);

      if(!Tool.equals(color, VSChartChromeDefaults.resolveGridlineColor(plotDesc.getFacetGridColor()))) {
         plotDesc.setFacetGridColor(color, false);
      }
```

- [ ] **Step 6: Compile**

Run: `./mvnw -q -pl core test-compile`
Expected: `core` compiles clean.

- [ ] **Step 7: Commit**

```bash
git add core/src/main/java/inetsoft/web/composer/model/vs/ChartLinePaneModel.java
git commit -m "Chart-chrome dialog WYSIWYG: gridlines show modern, unchanged values not persisted"
```

---

## Task 4: Legend-border dialog — show + non-persist the modern legend border

**Files:**
- Modify: `core/src/main/java/inetsoft/web/graph/model/dialog/LegendFormatDialogModel.java` (read `:63-66`, write `:123-124`, + import)

**Interfaces:**
- Consumes: `VSChartChromeDefaults.resolveLegendBorderColor(Color)` (Task 1).
- Produces: the legend format dialog's border ("fill") color field shows `#E8E5DE` for a new chart under the gate; OK-without-change persists nothing.

- [ ] **Step 1: Add the import**

At the top of `LegendFormatDialogModel.java`, add near the other `inetsoft.uql.viewsheet.internal` imports (only if no `inetsoft.uql.viewsheet.internal.*` wildcard is present):

```java
import inetsoft.uql.viewsheet.internal.VSChartChromeDefaults;
```

- [ ] **Step 2: Substitute on read**

At `LegendFormatDialogModel.java:63-66`, change:

```java
      if(legendsDesc.getBorderColor() != null) {
         generalPaneModel.setFillColor(
            "#" + Tool.colorToHTMLString(legendsDesc.getBorderColor()));
      }
```

to:

```java
      if(legendsDesc.getBorderColor() != null) {
         generalPaneModel.setFillColor(
            "#" + Tool.colorToHTMLString(
               VSChartChromeDefaults.resolveLegendBorderColor(legendsDesc.getBorderColor())));
      }
```

- [ ] **Step 3: Guard the write-back**

At `LegendFormatDialogModel.java:123-124`, change:

```java
      Color color = Tool.getColorFromHexString(generalPaneModel.getFillColor());
      legendsDesc.setBorderColor(color, false);
```

to:

```java
      Color color = Tool.getColorFromHexString(generalPaneModel.getFillColor());

      if(!Tool.equals(color, VSChartChromeDefaults.resolveLegendBorderColor(legendsDesc.getBorderColor()))) {
         legendsDesc.setBorderColor(color, false);
      }
```

- [ ] **Step 4: Compile**

Run: `./mvnw -q -pl core test-compile`
Expected: `core` compiles clean.

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/inetsoft/web/graph/model/dialog/LegendFormatDialogModel.java
git commit -m "Chart-chrome dialog WYSIWYG: legend border shows modern, unchanged value not persisted"
```

---

## Task 5: Validation — dialogs show modern, nothing persists, user picks survive, gate-off unchanged

**Files:** none (verification task). Requires a built/running server (`docker compose up` from `docker/target/docker-test`), an org with `viewsheet.modernVisualization` on, and a **new** viewsheet chart (a plain bar/line for axis+gridline; add a legend/aesthetic for the legend border; a **radar** to re-check the axis on that type; a **faceted** chart for the facet gridline).

- [ ] **Step 1: Gate-on — dialogs show modern**

With the gate on, on a new chart open: Axis Properties → Line → "Axis Line & Tick Color" shows `#E8E5DE` (was `#eeeeee`); the chart Line pane → X/Y gridline color and facet gridline color show `#E8E5DE`; Legend format dialog → border color shows `#E8E5DE`. Confirm the chart *render* already matches (it did before this change).

- [ ] **Step 2: Non-persistence — OK without change writes nothing**

For each dialog, open it and click OK/Apply **without changing the color**. Then save the viewsheet and inspect the saved asset XML (or reopen and diff): confirm **no** `lineColor`, `xGridColor`/`yGridColor`/`facetGridColor` (USER value), or legend `borderColor` (USER value) was written — i.e. the chart is byte-identical to before opening the dialog. (Axis line: confirm the descriptor `lineColor` is still the legacy `#eeeeee` in XML, not `#e8e5de`.)

- [ ] **Step 3: User picks still persist**

In each dialog, pick a distinct color (e.g. red) and Apply. Confirm it renders, shows red on reopen, and IS written to the saved XML (USER value / the plain field). Clearing back to the modern default: the field shows `#E8E5DE` again and, on OK-without-further-change, does not persist.

- [ ] **Step 4: Gate-off byte-identical**

Turn `viewsheet.modernVisualization` off. Reopen the same dialogs on a new chart: all three fields show the legacy `#eeeeee`; OK-without-change persists nothing; the saved XML is identical to pre-plan. (Resolvers return their input unchanged when the gate is off.)

- [ ] **Step 5: Boundary checks**

- A chart where the user previously set a gridline/axis/legend color (USER value / non-default field): the dialog shows that user color unchanged (not the modern neutral), in both gate states.
- `git diff --stat` confirms the change is core Java only — `VSChartChromeDefaults`, the three dialog models, one test — no Angular, no new `SreeEnv` property, and no change to `CSSChartStyles`, `GraphGenerator`, `AxisDescriptor`, `PlotDescriptor`, or `LegendsDescriptor` (the render + descriptors are untouched).

---

## Task 6: Close the Phase 7 chart-internal WYSIWYG gap in the docs

**Files:**
- Modify: `docs/superpowers/specs/lookfeel/visualization-phase7-implementation-plan.md` (the deferred-gap note at `:396-398`)

(Deliberately does **not** touch `visualization-implementation-roadmap.md` — Commit 1 already modifies it, and Commit 2 stays off shared files to keep the two commits cleanly separable.)

- [ ] **Step 1: Mark the gap closed in Phase 7**

At `visualization-phase7-implementation-plan.md:396-398`, append to the "Remaining picker gap" note: "**Closed 2026-07-24:** chart axis-line / gridline / legend-border dialogs now display the modern colors via `VSChartChromeDefaults.resolve{AxisLine,Gridline,LegendBorder}Color` applied in `AxisPropertyDialogModel` / `ChartLinePaneModel` / `LegendFormatDialogModel` on read, with a write-back guard so an unchanged modern-display value is never persisted (gate-off byte-identical). The Phase 6 render overlays are unchanged; this only aligns the editors with the render."

- [ ] **Step 2: Commit**

```bash
git add docs/superpowers/specs/lookfeel/visualization-phase7-implementation-plan.md
git commit -m "Chart-chrome dialog WYSIWYG: record the closed Phase 7 chart-internal picker gap"
```

---

## Validation summary

1. **Build/compile:** `./mvnw -q -pl core test-compile` clean; `git diff --name-only` shows only `VSChartChromeDefaults`, the three dialog models, one test file, and docs.
2. **Unit:** `CSSChartStylesModernChromeTest` green — the two new resolver tests (gate-on default→modern, custom/null preserved; gate-off unchanged) plus the existing axis-line tests.
3. **Gate-on dialogs:** axis line, X/Y + facet gridlines, and legend border all show `#E8E5DE` on a new chart (Task 5.1).
4. **Non-persistence:** OK-without-change writes nothing for all three — saved XML byte-identical, axis `lineColor` still `#eeeeee` (Task 5.2).
5. **User picks:** a chosen color renders, reopens, and persists; reverting to the modern default does not re-persist (Task 5.3).
6. **Gate-off:** dialogs show legacy `#eeeeee`, nothing persists, byte-identical (Task 5.4).
7. **Boundaries:** a pre-set user color is shown unchanged; render/descriptors/`CSSChartStyles` untouched (Task 5.5).

---

## Branching (per CLAUDE.md)

Community-only core Java (two new resolvers + tests, three dialog-model read/write edits, docs), continuing the visualization work on `viz-updates`. This is **Commit 2** — separate from the item-5 axis/border/page commit. Nothing on `main` or a `v1.0.x`/`v1.1.x` release branch; nothing pushed/PR'd without explicit approval. An enterprise submodule-pointer bump only at PR time.

---

## Related

- [visualization-phase7-implementation-plan.md](../specs/lookfeel/visualization-phase7-implementation-plan.md) — the Design-time WYSIWYG rule and the chart-internal gap this closes (`:384-398`)
- [visualization-phase6-implementation-plan.md](../specs/lookfeel/visualization-phase6-implementation-plan.md) — Part B (the render-time chart chrome this makes WYSIWYG in the editors)
- [2026-07-24-viz-phase9c-item5-chrome-border-axis-completion.md](2026-07-24-viz-phase9c-item5-chrome-border-axis-completion.md) — Commit 1 (axis coverage + border/page seeds); `VSChartChromeDefaults.resolveAxisLineColor` is shared
