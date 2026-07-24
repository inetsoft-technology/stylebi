# Phase 9C Item 5 (Broadened) — Chrome Hairline Completion: Object-Frame Border, Warm Page, + Axis-Line Coverage Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Complete the modern warm-neutral surface pass under the org modern-visualization gate: close the two remaining chart axis-line coverage gaps (radar label axis, funnel y-axis) that bypass the shipped B3 resolver; modernize the ungated legacy object-frame border (`#DADADA` → `#D9D5CC`); and warm the viewsheet page/canvas from its legacy fallback (cool gray in the viewer, white in export) to `#F8F7F4` while keeping white (`#FFFFFF`) object cards — so the warm title bar, frame, gridlines, and page read as one system instead of a stark white card on a cool page. All defaults-only, export-consistent, and byte-identical when the gate is off.

**Architecture:** Three server-side seams (System B, export-visible), each mirroring an already-shipped pattern. (1) **Axis lines:** the B3 resolver `VSChartChromeDefaults.resolveAxisLineColor(Color)` already substitutes legacy `#EEEEEE` for `#E8E5DE` at `GraphGenerator.setupAxisSpec`; two render paths build their axis spec's line color *manually* and never call it — this plan wraps those two sites. (2) **Object frame border:** a new `VSObjectChromeDefaults` (mirroring `VSTitleChromeDefaults`/`VSOutputChromeDefaults`) substitutes the legacy `#DADADA` frame for `#D9D5CC` on the DEFAULT tier at read time — at the central live-model seam (`VSObjectModel.createFormatModel`, all object types) and in-place on the export-cloned viewsheet (`AbstractVSExporter`) so every per-type export draw resolves it. (3) **Page background:** the viewsheet's own OBJECT-path `VSCompositeFormat` is the single source of truth read by both the DOM viewer (`CoreLifecycleService`) and the export page-fill (PDF/PNG/SVG); its legacy value is a *fallback* (gray in the viewer, white in export) that only fires when the sheet background is unset, so a gated modern `#F8F7F4` — the same `VSObjectChromeDefaults` — wins over it while any user/`format.css` sheet background still wins over the modern default.

**Tech Stack:** Java 21 (core graph engine, the viewsheet object-model, the live-info lifecycle service, and the export layer), JUnit 5 (`@Tag("core")`, Spring `@SreeHome` for the SreeEnv-gated resolver tests). No Angular/CSS/frontend change — the viewer background is fed inline from the server-computed `viewsheetBackground`; axis lines, object borders, and the export page-fill are all server-rendered.

## Global Constraints

Copied verbatim from the Phase 6 B3 grounding, the Phase 9C spec, and the sibling chrome-defaults classes; every task's requirements implicitly include this section.

- **Render-location rule.** Chart axis lines, object-frame borders, and the page background are server-rendered / server-fed and appear in export; browser `--inet-viz-*` CSS cannot color them. Every change here is server-side, at the same seams the shipped chrome defaults already ride, so the live model and every export format agree.
- **Gate is server-side and org-scoped.** Axis lines use `VSChartChromeDefaults.isModern()` (`VSDensityDefaults.isModern()` — reads `SreeEnv.getBooleanProperty("viewsheet.modernVisualization", false, true)`, 3rd arg = org-scoped — **and** `!"false".equals(SreeEnv.getProperty("viewsheet.modernChartChrome", false, true))`). Object border and page background use `VSObjectChromeDefaults.isModern()` = `VSDensityDefaults.isModern()` **and** `!"false".equals(SreeEnv.getProperty("viewsheet.modernObjectChrome", false, true))` — the same toggle `VSTitleChromeDefaults`/`VSOutputChromeDefaults` share. **No new property.**
- **Defaults-only.** Every substitution lands on the DEFAULT tier (or an `else`/fallback branch) and is skipped when the USER tier (picker) or CSS tier (`format.css`) has set the value, so a user color and a customer `format.css` color always win. Axis lines use value-equality against the legacy default; the object border uses not-customized **plus** value-equality against `VSAssemblyInfo.DEFAULT_BORDER_COLOR`; the page background uses not-customized (the legacy gray/white is a fallback, not a composite tier).
- **Never mutate a persisted format/descriptor.** The live paths substitute on a `clone()` or compute inline; the export paths substitute in-place only on the already-cloned export viewsheet. No saved sheet XML changes.
- **Gate-off byte-identical.** With the gate off, every resolver returns its input unchanged and every seam is a no-op; live + PDF/PNG/SVG/Excel/HTML/PPT render pixel-identical to today (viewer keeps the legacy gray fallback, export keeps the white fallback).
- **Light mode only.** All values are the light-mode warm neutrals; dark variants ride the Phase 9B dark pass.
- **Color values.** Axis line `#E8E5DE` (`VSChartChromeDefaults.gridlineColor()`, shipped). Object-frame border `#D9D5CC` (= `--border-default`, = `VSTitleChromeDefaults.TITLE_BORDER` = `VSOutputChromeDefaults.VALUE_BORDER`). Page background `#F8F7F4` (= `--surface-canvas`). Cards stay `#FFFFFF` (= `--surface-default`, unchanged). Legacy object border `#DADADA` = `VSAssemblyInfo.DEFAULT_BORDER_COLOR`.

---

## Review Findings (verified against current code, branch `viz-updates`, 2026-07-24)

### Already shipped (Phase 6 B3, commit `fa6ef34b6`) — the baseline this plan extends

The axis-line polish named by Phase 9C item 5 is *already implemented*: `VSChartChromeDefaults.resolveAxisLineColor(Color)` (`VSChartChromeDefaults.java:77-79`) and its call at `GraphGenerator.setupAxisSpec` (`GraphGenerator.java:2579`), with two committed unit tests in `CSSChartStylesModernChromeTest` (`:151-182`). Most axes are covered directly (2579) or transitively (call sites 2661/4150/4191/4213 copy from an already-resolved `AxisSpec`). This plan closes the residue B3 did not reach and extends the same warm-neutral treatment to the object frame and the page — the "net-new work" the broadened item 5 asks for.

### Background — CORRECTED FINDING (was mis-reported earlier; now a scheduled item)

An earlier pass mis-described object/page backgrounds as "transparent by design, on-scheme, no change." That was wrong. The verified facts:

- **Chart card is opaque white, ungated.** `ChartVSAssemblyInfo.java:89` sets the chart assembly background to `#ffffff` on the DEFAULT tier, with no modern gate. The plot area is transparent (`PlotDescriptor.java:1929`), so that white shows through the entire chart body. **This is correct and stays** — `#FFFFFF` is the modern `--surface-default` card token; the decision (below) is to keep white cards.
- **Table card is transparent.** `TableVSAssemblyInfo.java:74-79` deliberately leaves the assembly background null (billh's comment: so the table style paints), and the modern table gate sets gridline/header/total colors but **no body-cell background** (`DataVSAQuery.java:241-245`). A table body is genuinely transparent and shows whatever page is behind it.
- **Page/canvas is a single source of truth with a legacy fallback.** Both the DOM viewer and the export page-fill read the viewsheet's own OBJECT-path composite `vs.getFormatInfo().getFormat(new TableDataPath(-1, TableDataPath.OBJECT))` (a `VSCompositeFormat`, USER/CSS/DEFAULT tiers). When that composite background is unset (the default), the **viewer** falls back to the CSS `Viewsheet` class → cool gray (`CoreLifecycleService.java:263-272` → `Tool.getVSCSSBgColorHexString()`), while **export** falls back to `Color.WHITE` (`PDFVSExporter.java:1062`, `SVGCoordinateHelper.java:134`). So today there is already a **live/export mismatch** (gray viewer, white export), and the object cards read as stark white on both.

Net: the image shows a half-modernized object — warm title bar (`#F1EFEA`, modern chrome on) over a stark white body on a cool-gray page. **Decision (user-confirmed 2026-07-24): warm the page to `#F8F7F4` and keep white cards** — the standard modern two-layer model (`--surface-default #FFFFFF` cards on `--surface-canvas #F8F7F4` page). This fixes charts and tables at once (tables are transparent, so they inherit the warm page) and unifies viewer with export. Because the legacy gray/white is a *fallback* (fires only when the sheet background is unset), a gated modern `#F8F7F4` substituted where that fallback is chosen wins over it, while a genuine user/`format.css` sheet background still wins.

### Border — REVIEWED, WARRANTED (planned below)

The object-frame border is a `THIN_LINE` in `VSAssemblyInfo.DEFAULT_BORDER_COLOR = new Color(0xDADADA)` (`VSAssemblyInfo.java:1545`), seeded on the OBJECT format's DEFAULT tier in `setDefaultFormat` (`:1162-1165,1192`), completely ungated. The modern structural border is `#D9D5CC` (`--border-default`), already used by the title→body rule and the KPI value border. `#DADADA`→`#D9D5CC` is a near-invisible warm shift that removes the last cool-gray frame from an otherwise warm-neutral modern object. Low value, low risk; ready and cheap.

### The axis-line coverage gaps (net-new)

| Site | Code today | Status |
|---|---|---|
| `RadarGraphGenerator.java:201` | `spec.setLineColor(xdesc.getLineColor());` | **REAL GAP.** `fixParallelCoord` builds the radar outer **label axis** spec manually (`new AxisSpec()` `:169`) and never calls `setupAxisSpec`, so the resolver never runs — a default radar leaves the label axis at legacy `#EEEEEE` while its value axes are modernized. Task 1. |
| `GraphGenerator.java:2345` | `yscale.getAxisSpec().setLineColor(xdesc.getLineColor());` | **PARTIAL GAP.** Funnel y-axis copies the raw x **descriptor** color, not the resolved spec. Often masked by `setFunnelAxisColor` (`:4150`); wrapping it is a cheap, idempotent safety fix. Task 2. |
| `RectCoord.java:1025` else-branch | `... : GDefaults.DEFAULT_LINE_COLOR;` | **MOOT — no task.** Coordinate/plot border with no backing axis; B3 established the plot border default "is never drawn," and wrapping it would invert the graph→viewsheet layering. Left as-is. |
| `AxisSpec.java:665` field default `#EEEEEE` | `private Color lineColor = GDefaults.DEFAULT_LINE_COLOR;` | **LEAVE.** Ungated product-wide fallback (report engine), also governs tick color (ticks reuse the line color). Out of scope for a gated polish. |

Ticks have **no separate color** — `AxisLine.java:121,212` paint both the axis line and tick marks from `axis.getLineColor()`, so ticks are covered exactly where the line is.

---

## Grounding (verified anchors)

| Anchor | Location | Note |
|---|---|---|
| Axis-line resolver (shipped) | `VSChartChromeDefaults.java:77-79` | `resolveAxisLineColor(Color current)` → `isModern() && GDefaults.DEFAULT_LINE_COLOR.equals(current) ? GRIDLINE : current`. `GRIDLINE = 0xE8E5DE`. |
| Radar label-axis line set | `RadarGraphGenerator.java:201` | `spec.setLineColor(xdesc.getLineColor());` — manual spec, bypasses the resolver. `VSChartChromeDefaults` not yet imported here. |
| Funnel y-axis line set | `GraphGenerator.java:2344-2346` | `yscale.getAxisSpec().setLineColor(xdesc.getLineColor());`. `VSChartChromeDefaults` **already imported** (used at `:2579`). |
| Object-border seed | `VSAssemblyInfo.java:1162-1165,1192,1545` | `DEFAULT_BORDER_COLOR = new Color(0xDADADA)` written to `objfmt.setBorderColorsValue(new BorderColors(DEFAULT_BORDER_COLOR ×4))` on the OBJECT format's DEFAULT tier. |
| Object composite = OBJECTPATH | `VSAssemblyInfo.java:281-282,1539` | `getFormat()` returns `fmtInfo.getFormat(OBJECTPATH)`, `OBJECTPATH = new TableDataPath(-1, TableDataPath.OBJECT)`. The viewsheet's own OBJECT-path format is the page background. |
| Live-model central seam (border) | `VSObjectModel.java:91,99,242-246` | `compositeFormat = assemblyInfo.getFormat()` → `createFormatModel(compositeFormat, assemblyInfo)` → base returns `new VSFormatModel(compositeFormat, assemblyInfo)`. Every object type flows through here; only `VSTextModel:207-212` overrides it (to apply `VSOutputChromeDefaults`, which sets the same `#D9D5CC` border). |
| Border read → model | `VSFormatModel.java:95-98` → `VSCSSUtil.java:102` | `VSFormatModel` reads the border via `VSCSSUtil.getBorder(compositeFormat, side)` → `format.getBorderColors()`. |
| Tier resolution | `VSCompositeFormat.java:110-115` | `getBorderColors()`/`getBackground()` resolve USER → CSS → DEFAULT. Substituting the DEFAULT tier flows through iff USER/CSS are unset. |
| Live page-bg seam | `CoreLifecycleService.java:263-272` | Reads the sheet OBJECT composite; `getBackgroundRGBA(format)` returns `""` when the composite bg is null → falls back to `Tool.getVSCSSBgColorHexString()` (CSS `Viewsheet` gray → `#ffffff`). The `else` branch is where the modern warm page is injected. |
| Export page-fill (PDF) | `PDFVSExporter.java:1053-1066` | Reads `viewsheet.getFormatInfo().getFormat(OBJECT).getBackground()`; fills `background != null ? background : Color.WHITE`. |
| Export page-fill (PNG/SVG) | `SVGCoordinateHelper.java:119-137` | Same OBJECT composite `getBackground()`; `bg == null → Color.WHITE`. `PNGVSExporter` subclasses `SVGVSExporter`. |
| Export clone + assembly loop | `AbstractVSExporter.java:1135-1140,1150` | `if(cloneVs) viewsheet = viewsheet.clone();` then `this.viewsheet = viewsheet;`. `prepareSheet` (which reads the page bg) runs later at `:1279`, so an in-place substitution here is seen by every export page-fill and border draw. |
| Border-substitution precedent | `VSOutputChromeDefaults.java:111-152,170` | `applyModernDefaults` (clone) + `applyModernDefaultsInPlace` set `def.setBorderColorsValue(new BorderColors(VALUE_BORDER ×4))`; `isBorderCustomized`/`isForegroundCustomized` check `getUserDefinedFormat()`/`getCSSFormat()`; `toValue(Color)` = `String.format("0x%06x", …)`. Exact idiom to copy. |
| `BorderColors` ctor | `BorderColors.java:45-51` | `public BorderColors(Color tcolor, Color bcolor, Color lcolor, Color rcolor)`; public fields `topColor/bottomColor/leftColor/rightColor`. |
| `VSAssembly` info accessor | `VSAssembly.java:83` | `VSAssemblyInfo getVSAssemblyInfo();` — used in the export loop. |

---

## Decisions

- **R1 — Two axis wraps only; RectCoord/AxisSpec left alone.** Only the two composition-layer manual spec builds (`RadarGraphGenerator:201`, `GraphGenerator:2345`) are wrapped (Review Findings).
- **R2 — New `VSObjectChromeDefaults` class.** Each modern surface has its own `*Defaults` class; the object frame and page are distinct surfaces. It shares the `viewsheet.modernObjectChrome` gate so object chrome (title bar + frame + page) toggles together.
- **R3 — Border: not-customized AND value-equality guard.** The frame is modernized only when neither USER nor CSS tier set it **and** the DEFAULT-tier border is still exactly the legacy `#DADADA` seed on all four sides — so an assembly whose default border comes from a CSS `TableStyle` (`VSAssemblyInfo.java:1181-1183`) is left untouched.
- **R4 — Border: central live seam + export clone loop.** Wrapping the base `VSObjectModel.createFormatModel` modernizes every object's frame with one clone-returning call; export applies the in-place variant per assembly on the cloned viewsheet. Broader than "chart/table," but the guard makes it defaults-only and safe, and one-system consistency is the point.
- **R5 — Page: warm the page, keep white cards.** `#F8F7F4` page (`--surface-canvas`) with unchanged `#FFFFFF` cards (`--surface-default`). Applied where the legacy gray/white *fallback* is chosen (live: the `else` branch in `CoreLifecycleService`; export: an in-place DEFAULT-tier substitution on the cloned sheet composite so PDF/PNG/SVG all read it). Defaults-only: a user/`format.css` sheet background makes the composite bg non-empty and wins. The chart's explicit `#ffffff` card fill is intentionally **not** touched.
- **R6 — No new property, no swatch invention.** Border rides `viewsheet.modernObjectChrome`; `#D9D5CC` and `#F8F7F4` are already the `--border-default` and `--surface-canvas` swatches. Docs only annotate that they now govern the object frame and page.

---

## Task 1: Wrap the radar label-axis line color through the resolver

**Files:**
- Modify: `core/src/main/java/inetsoft/report/composition/graph/RadarGraphGenerator.java:201` (+ one import)

**Interfaces:**
- Consumes: `VSChartChromeDefaults.resolveAxisLineColor(Color)` (shipped, `VSChartChromeDefaults.java:77`).
- Produces: the radar outer label axis line renders `#E8E5DE` under the gate, unchanged otherwise.

- [ ] **Step 1: Add the import**

At the top of `RadarGraphGenerator.java`, add near the other `inetsoft.uql` imports:

```java
import inetsoft.uql.viewsheet.internal.VSChartChromeDefaults;
```

- [ ] **Step 2: Wrap the call site**

At `RadarGraphGenerator.java:201`, change:

```java
      spec.setLineColor(xdesc.getLineColor());
```

to:

```java
      spec.setLineColor(VSChartChromeDefaults.resolveAxisLineColor(xdesc.getLineColor()));
```

- [ ] **Step 3: Compile**

Run: `./mvnw -q -pl core test-compile`
Expected: `core` compiles clean.

- [ ] **Step 4: Commit**

```bash
git add core/src/main/java/inetsoft/report/composition/graph/RadarGraphGenerator.java
git commit -m "Phase 9C item 5: apply modern axis-line color to the radar label axis"
```

> Render-path wiring, verified in Task 7. The resolver is already unit-tested in `CSSChartStylesModernChromeTest`; per the B3 precedent, the one-line wiring is not separately unit-tested (building a `RadarGraphGenerator` requires a full chart info + dataset the codebase does not harness).

---

## Task 2: Wrap the funnel y-axis line color through the resolver

**Files:**
- Modify: `core/src/main/java/inetsoft/report/composition/graph/GraphGenerator.java:2345`

**Interfaces:**
- Consumes: `VSChartChromeDefaults.resolveAxisLineColor(Color)` (shipped). Already imported in this file (used at `:2579`) — no import edit.
- Produces: the funnel y-axis line resolves to `#E8E5DE` under the gate at setup time.

- [ ] **Step 1: Wrap the call site**

At `GraphGenerator.java:2345`, change:

```java
                  yscale.getAxisSpec().setLineColor(xdesc.getLineColor());
```

to:

```java
                  yscale.getAxisSpec().setLineColor(
                     VSChartChromeDefaults.resolveAxisLineColor(xdesc.getLineColor()));
```

- [ ] **Step 2: Compile**

Run: `./mvnw -q -pl core test-compile`
Expected: `core` compiles clean.

- [ ] **Step 3: Commit**

```bash
git add core/src/main/java/inetsoft/report/composition/graph/GraphGenerator.java
git commit -m "Phase 9C item 5: apply modern axis-line color to the funnel y-axis"
```

---

## Task 3: Add `VSObjectChromeDefaults` (frame border + warm page) resolver + unit tests

**Files:**
- Create: `core/src/main/java/inetsoft/uql/viewsheet/internal/VSObjectChromeDefaults.java`
- Create: `core/src/test/java/inetsoft/uql/viewsheet/internal/VSObjectChromeDefaultsTest.java`

**Interfaces:**
- Consumes: `VSAssemblyInfo.DEFAULT_BORDER_COLOR` (`0xDADADA`); `VSCompositeFormat`/`VSFormat`/`BorderColors`; `VSDensityDefaults.isModern()`; `SreeEnv`.
- Produces (used by Tasks 4–6):
  - `public static boolean isModern()`
  - `public static java.awt.Color objectBorderColor()` — `#D9D5CC`
  - `public static java.awt.Color pageBackground()` — `#F8F7F4`
  - `public static String pageBackgroundCss()` — `"#f8f7f4"` (for the DOM `viewsheetBackground`)
  - `public static VSCompositeFormat applyModernDefaults(VSCompositeFormat objFmt)` — clone with the DEFAULT-tier frame border modernized, or the original unchanged. Never mutates the input.
  - `public static void applyModernObjectBorderInPlace(VSCompositeFormat objFmt)` — mutate the DEFAULT-tier frame border in place (export copy only).
  - `public static void applyModernPageBackgroundInPlace(VSCompositeFormat sheetFmt)` — mutate the DEFAULT-tier background of the sheet's OBJECT composite in place (export copy only).

- [ ] **Step 1: Write the failing test**

```java
package inetsoft.uql.viewsheet.internal;

import inetsoft.sree.SreeEnv;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.viewsheet.BorderColors;
import inetsoft.uql.viewsheet.VSCompositeFormat;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.Color;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class VSObjectChromeDefaultsTest {
   private VSCompositeFormat legacyBorderFormat() {
      VSCompositeFormat fmt = new VSCompositeFormat();
      fmt.getDefaultFormat().setBorderColorsValue(
         new BorderColors(VSAssemblyInfo.DEFAULT_BORDER_COLOR, VSAssemblyInfo.DEFAULT_BORDER_COLOR,
                          VSAssemblyInfo.DEFAULT_BORDER_COLOR, VSAssemblyInfo.DEFAULT_BORDER_COLOR));
      return fmt;
   }

   private int rgb(Color c) {
      return c.getRGB() & 0xFFFFFF;
   }

   private void withGate(String value, Runnable body) {
      String saved = SreeEnv.getProperty("viewsheet.modernVisualization");

      try {
         SreeEnv.setProperty("viewsheet.modernVisualization", value);
         body.run();
      }
      finally {
         SreeEnv.setProperty("viewsheet.modernVisualization", saved);
      }
   }

   @Test
   void colorValues() {
      assertEquals(0xD9D5CC, rgb(VSObjectChromeDefaults.objectBorderColor()));
      assertEquals(0xF8F7F4, rgb(VSObjectChromeDefaults.pageBackground()));
      assertEquals("#f8f7f4", VSObjectChromeDefaults.pageBackgroundCss());
   }

   @Test
   void gateOnModernizesLegacyDefaultBorder() {
      withGate("true", () -> {
         VSCompositeFormat fmt = legacyBorderFormat();
         VSCompositeFormat result = VSObjectChromeDefaults.applyModernDefaults(fmt);

         assertNotSame(fmt, result, "must return a clone, never the input");
         assertEquals(0xD9D5CC, rgb(result.getDefaultFormat().getBorderColors().topColor),
                      "legacy #DADADA default frame is modernized to #D9D5CC");
         assertEquals(VSAssemblyInfo.DEFAULT_BORDER_COLOR,
                      fmt.getDefaultFormat().getBorderColors().topColor,
                      "the source format is never mutated");
      });
   }

   @Test
   void gateOffLeavesLegacyBorder() {
      withGate("false", () -> {
         VSCompositeFormat fmt = legacyBorderFormat();
         assertSame(fmt, VSObjectChromeDefaults.applyModernDefaults(fmt),
                    "gate off returns the input unchanged");
      });
   }

   @Test
   void userBorderIsPreserved() {
      withGate("true", () -> {
         VSCompositeFormat fmt = legacyBorderFormat();
         fmt.getUserDefinedFormat().setBorderColorsValue(
            new BorderColors(Color.RED, Color.RED, Color.RED, Color.RED));
         assertSame(fmt, VSObjectChromeDefaults.applyModernDefaults(fmt),
                    "a user-set (USER tier) border is not modernized");
      });
   }

   @Test
   void nonLegacyDefaultBorderIsPreserved() {
      withGate("true", () -> {
         VSCompositeFormat fmt = new VSCompositeFormat();
         fmt.getDefaultFormat().setBorderColorsValue(
            new BorderColors(Color.BLUE, Color.BLUE, Color.BLUE, Color.BLUE));
         assertSame(fmt, VSObjectChromeDefaults.applyModernDefaults(fmt),
                    "only the legacy #DADADA default is modernized; other defaults are left alone");
      });
   }

   @Test
   void pageBackgroundInPlaceUnderGate() {
      withGate("true", () -> {
         VSCompositeFormat sheet = new VSCompositeFormat();  // no background set (default page)
         VSObjectChromeDefaults.applyModernPageBackgroundInPlace(sheet);
         assertEquals(0xF8F7F4, rgb(sheet.getDefaultFormat().getBackground()),
                      "the unset page background is warmed to #F8F7F4 under the gate");
      });
   }

   @Test
   void pageBackgroundGateOffIsNoOp() {
      withGate("false", () -> {
         VSCompositeFormat sheet = new VSCompositeFormat();
         VSObjectChromeDefaults.applyModernPageBackgroundInPlace(sheet);
         assertNull(sheet.getDefaultFormat().getBackground(),
                    "gate off leaves the page background unset (legacy fallback still applies)");
      });
   }

   @Test
   void userPageBackgroundIsPreserved() {
      withGate("true", () -> {
         VSCompositeFormat sheet = new VSCompositeFormat();
         sheet.getUserDefinedFormat().setBackgroundValue("0x112233");
         VSObjectChromeDefaults.applyModernPageBackgroundInPlace(sheet);
         assertEquals(0x112233, rgb(sheet.getBackground()),
                      "a user-set sheet background wins over the modern page default");
      });
   }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw -q -pl core test -Dtest=VSObjectChromeDefaultsTest -DfailIfNoTests=false`
Expected: FAIL — `VSObjectChromeDefaults` does not exist (compilation error).

- [ ] **Step 3: Create the class**

`core/src/main/java/inetsoft/uql/viewsheet/internal/VSObjectChromeDefaults.java`:

```java
/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package inetsoft.uql.viewsheet.internal;

import inetsoft.sree.SreeEnv;
import inetsoft.uql.viewsheet.BorderColors;
import inetsoft.uql.viewsheet.VSCompositeFormat;
import inetsoft.uql.viewsheet.VSFormat;

import java.awt.Color;

/**
 * Supplies the modern object-frame border and warm page background for viewsheet assemblies, gated
 * by the org modern-visualization setting. Both are server-rendered / server-fed and re-drawn in
 * export, so they are server-owned.
 *
 * Frame border: the object's outer frame is seeded on the DEFAULT tier with the legacy
 * VSAssemblyInfo DEFAULT_BORDER_COLOR (#DADADA); under the gate this substitutes the shared modern
 * structural neutral (#D9D5CC = VSTitleChromeDefaults border = VSOutputChromeDefaults border) on the
 * DEFAULT tier at read time, only when neither the user (USER) nor a format.css class (CSS) has set
 * the border and only when the default is still the legacy seed (so a CSS TableStyle default border
 * is left alone). applyModernDefaults returns a clone; applyModernObjectBorderInPlace mutates
 * directly, for the export copy.
 *
 * Page background: the viewsheet's own OBJECT-path composite is the single source of truth for the
 * page fill (viewer + export). Its legacy value is a fallback (gray in the viewer, white in export)
 * that only fires when the sheet background is unset; under the gate the warm --surface-canvas
 * (#F8F7F4) is used instead, keeping white (#FFFFFF) object cards. Defaults-only: a user/format.css
 * sheet background still wins.
 *
 * Shares the viewsheet.modernObjectChrome gate with VSTitleChromeDefaults so the object title bar,
 * frame, and page modernize together. Mirrors VSTitleChromeDefaults' gate.
 */
public final class VSObjectChromeDefaults {
   private VSObjectChromeDefaults() {
   }

   /**
    * Whether modern object chrome is active: the modern-visualization gate plus its chrome toggle,
    * which defaults on when modern is enabled.
    */
   public static boolean isModern() {
      return VSDensityDefaults.isModern() &&
         !"false".equals(SreeEnv.getProperty("viewsheet.modernObjectChrome", false, true));
   }

   /** Object-frame border — the shared structural neutral (matches the title rule and value border). */
   public static Color objectBorderColor() {
      return OBJECT_BORDER;
   }

   /** Modern viewsheet page/canvas background — warm off-white, one step below the white card surface. */
   public static Color pageBackground() {
      return PAGE_BG;
   }

   /** The modern page background as a CSS color string for the viewer/composer DOM. */
   public static String pageBackgroundCss() {
      return String.format("#%06x", PAGE_BG.getRGB() & 0xFFFFFF);
   }

   /**
    * Return an object format with the modern frame border on the DEFAULT tier of a clone, or the
    * original unchanged (gate off, customized, or a non-legacy default). Never mutates the input.
    */
   public static VSCompositeFormat applyModernDefaults(VSCompositeFormat objFmt) {
      if(!isModern() || objFmt == null || !isLegacyDefaultBorder(objFmt)) {
         return objFmt;
      }

      VSCompositeFormat clone = objFmt.clone();
      applyBorderTo(clone.getDefaultFormat());
      return clone;
   }

   /**
    * In-place frame-border variant for the export copy (the viewsheet is cloned before export).
    * No-op when the gate is off, the border is user / format.css customized, or the default is not
    * the legacy seed; never touches a persisted format.
    */
   public static void applyModernObjectBorderInPlace(VSCompositeFormat objFmt) {
      if(!isModern() || objFmt == null || !isLegacyDefaultBorder(objFmt)) {
         return;
      }

      applyBorderTo(objFmt.getDefaultFormat());
   }

   /**
    * In-place page-background variant for the export copy: set the sheet OBJECT composite's
    * DEFAULT-tier background to the warm page neutral so every export page-fill (PDF/PNG/SVG) reads
    * it. No-op when the gate is off or a user / format.css sheet background is set.
    */
   public static void applyModernPageBackgroundInPlace(VSCompositeFormat sheetFmt) {
      if(!isModern() || sheetFmt == null || isBackgroundCustomized(sheetFmt)) {
         return;
      }

      sheetFmt.getDefaultFormat().setBackgroundValue(toValue(PAGE_BG));
   }

   private static void applyBorderTo(VSFormat def) {
      def.setBorderColorsValue(
         new BorderColors(OBJECT_BORDER, OBJECT_BORDER, OBJECT_BORDER, OBJECT_BORDER));
   }

   // Modernize only a bare legacy default frame: no user/CSS override, and the DEFAULT-tier border
   // is still exactly the #DADADA seed on all four sides.
   private static boolean isLegacyDefaultBorder(VSCompositeFormat f) {
      if(f.getUserDefinedFormat().isBorderColorsValueDefined() ||
         f.getCSSFormat().isBorderColorsValueDefined())
      {
         return false;
      }

      BorderColors def = f.getDefaultFormat().getBorderColors();
      return def != null &&
         VSAssemblyInfo.DEFAULT_BORDER_COLOR.equals(def.topColor) &&
         VSAssemblyInfo.DEFAULT_BORDER_COLOR.equals(def.bottomColor) &&
         VSAssemblyInfo.DEFAULT_BORDER_COLOR.equals(def.leftColor) &&
         VSAssemblyInfo.DEFAULT_BORDER_COLOR.equals(def.rightColor);
   }

   private static boolean isBackgroundCustomized(VSCompositeFormat f) {
      return f.getUserDefinedFormat().isBackgroundValueDefined() ||
         f.getCSSFormat().isBackgroundValueDefined();
   }

   private static String toValue(Color c) {
      return String.format("0x%06x", c.getRGB() & 0xFFFFFF);
   }

   // modern warm-neutral object frame + page; light mode only, dark deferred. Frame equals
   // VSTitleChromeDefaults' title rule / VSOutputChromeDefaults' value border / --border-default;
   // page equals --surface-canvas. Cards stay #FFFFFF (--surface-default).
   private static final Color OBJECT_BORDER = new Color(0xD9D5CC);
   private static final Color PAGE_BG = new Color(0xF8F7F4);
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./mvnw -q -pl core test -Dtest=VSObjectChromeDefaultsTest -DfailIfNoTests=false`
Expected: PASS (9 tests).

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/inetsoft/uql/viewsheet/internal/VSObjectChromeDefaults.java \
        core/src/test/java/inetsoft/uql/viewsheet/internal/VSObjectChromeDefaultsTest.java
git commit -m "Phase 9C item 5: add VSObjectChromeDefaults (frame border + warm page) resolver"
```

---

## Task 4: Wire the frame-border resolver into the central live-model seam

**Files:**
- Modify: `core/src/main/java/inetsoft/web/viewsheet/model/VSObjectModel.java:242-246` (+ one import)

**Interfaces:**
- Consumes: `VSObjectChromeDefaults.applyModernDefaults(VSCompositeFormat)` (Task 3).
- Produces: every object's live `objectFormat` model carries the `#D9D5CC` frame under the gate; `VSTextModel` is unaffected (it overrides `createFormatModel`).

- [ ] **Step 1: Add the import**

At the top of `VSObjectModel.java`, add near the other `inetsoft.uql.viewsheet.internal` imports:

```java
import inetsoft.uql.viewsheet.internal.VSObjectChromeDefaults;
```

- [ ] **Step 2: Wrap the base factory**

At `VSObjectModel.java:242-246`, change:

```java
   protected VSFormatModel createFormatModel(VSCompositeFormat compositeFormat,
                                             VSAssemblyInfo assemblyInfo)
   {
      return new VSFormatModel(compositeFormat, assemblyInfo);
   }
```

to:

```java
   protected VSFormatModel createFormatModel(VSCompositeFormat compositeFormat,
                                             VSAssemblyInfo assemblyInfo)
   {
      return new VSFormatModel(
         VSObjectChromeDefaults.applyModernDefaults(compositeFormat), assemblyInfo);
   }
```

- [ ] **Step 3: Compile and run the chrome suites**

Run: `./mvnw -q -pl core test-compile && ./mvnw -q -pl core test -Dtest="VSObjectChromeDefaultsTest,CSSChartStylesModernChromeTest,VSTitleChromeDefaultsTest,VSOutputChromeDefaultsTest" -DfailIfNoTests=false`
Expected: `core` compiles clean; all suites PASS (no regression in the sibling chrome tests).

- [ ] **Step 4: Commit**

```bash
git add core/src/main/java/inetsoft/web/viewsheet/model/VSObjectModel.java
git commit -m "Phase 9C item 5: modernize the object-frame border in the live model seam"
```

---

## Task 5: Wire the warm page background into the live info seam

**Files:**
- Modify: `core/src/main/java/inetsoft/web/viewsheet/service/CoreLifecycleService.java:263-272` (+ one import)

**Interfaces:**
- Consumes: `VSObjectChromeDefaults.isModern()`, `VSObjectChromeDefaults.pageBackgroundCss()` (Task 3).
- Produces: the DOM viewer/composer `viewsheetBackground` is `#f8f7f4` under the gate when the sheet has no user/`format.css` background, unchanged otherwise.

- [ ] **Step 1: Add the import**

At the top of `CoreLifecycleService.java`, add near the other `inetsoft.uql.viewsheet.internal` imports:

```java
import inetsoft.uql.viewsheet.internal.VSObjectChromeDefaults;
```

- [ ] **Step 2: Add the modern branch**

At `CoreLifecycleService.java:267-272`, change:

```java
         if(!color.isEmpty()) {
            infoMap.put("viewsheetBackground", color);
         }
         else {
            infoMap.put("viewsheetBackground", Tool.getVSCSSBgColorHexString());
         }
```

to:

```java
         if(!color.isEmpty()) {
            infoMap.put("viewsheetBackground", color);
         }
         else if(VSObjectChromeDefaults.isModern()) {
            // modern warm page (--surface-canvas) in place of the legacy gray fallback; a
            // user/format.css sheet background makes `color` non-empty and wins above
            infoMap.put("viewsheetBackground", VSObjectChromeDefaults.pageBackgroundCss());
         }
         else {
            infoMap.put("viewsheetBackground", Tool.getVSCSSBgColorHexString());
         }
```

- [ ] **Step 3: Compile**

Run: `./mvnw -q -pl core test-compile`
Expected: `core` compiles clean.

- [ ] **Step 4: Commit**

```bash
git add core/src/main/java/inetsoft/web/viewsheet/service/CoreLifecycleService.java
git commit -m "Phase 9C item 5: warm the viewsheet page background in the live viewer"
```

> The `viewsheetBackground` value feeds both the viewer (`viewer-app.component.html:386`) and the composer canvas (`viewsheet-pane.component.html:22`), so both warm together. This is a server-fed inline style — no frontend edit. Verified in Task 7.

---

## Task 6: Wire the frame border + warm page into the export path

**Files:**
- Modify: `core/src/main/java/inetsoft/report/io/viewsheet/AbstractVSExporter.java` (add a guarded block after `:1140`; + imports if needed)

**Interfaces:**
- Consumes: `VSObjectChromeDefaults.applyModernObjectBorderInPlace(VSCompositeFormat)` and `applyModernPageBackgroundInPlace(VSCompositeFormat)` (Task 3); `VSAssembly.getVSAssemblyInfo().getFormat()`; `VSAssemblyInfo.OBJECTPATH`.
- Produces: every export format draws the `#D9D5CC` frame and fills the `#F8F7F4` page under the gate (PDF/PNG/SVG all read `format.getBackground()`), matching the live viewer.

- [ ] **Step 1: Confirm imports**

`AbstractVSExporter` already imports `inetsoft.uql.viewsheet.Viewsheet`, `inetsoft.uql.asset.Assembly`, `inetsoft.uql.viewsheet.VSAssembly`, `inetsoft.uql.viewsheet.internal.VSAssemblyInfo`, and `inetsoft.uql.viewsheet.VSCompositeFormat` (all used throughout, incl. the shipped `VSTitleChromeDefaults` title-path work). Add only if Step 3 reports it missing:

```java
import inetsoft.uql.viewsheet.internal.VSObjectChromeDefaults;
```

- [ ] **Step 2: Add the guarded in-place substitutions**

At `AbstractVSExporter.java`, immediately after line `:1140` (`this.viewsheet = viewsheet;`) and before the `LayoutInfo` block, insert:

```java
         // modernize the object-frame border and warm the page background on the export copy (only
         // when the vs was cloned, so a shared/live vs is never mutated). The page fill and every
         // per-type border draw read the OBJECT composite's getBackground()/getBorderColors(), so
         // one DEFAULT-tier substitution per format covers PDF/PNG/SVG at once
         if(cloneVs) {
            VSObjectChromeDefaults.applyModernPageBackgroundInPlace(
               viewsheet.getFormatInfo().getFormat(VSAssemblyInfo.OBJECTPATH));

            for(Assembly exportAssembly : viewsheet.getAssemblies()) {
               if(exportAssembly instanceof VSAssembly) {
                  VSObjectChromeDefaults.applyModernObjectBorderInPlace(
                     ((VSAssembly) exportAssembly).getVSAssemblyInfo().getFormat());
               }
            }
         }
```

- [ ] **Step 3: Compile**

Run: `./mvnw -q -pl core test-compile`
Expected: `core` compiles clean (add the `VSObjectChromeDefaults` import from Step 1 if reported missing, then re-run).

- [ ] **Step 4: Commit**

```bash
git add core/src/main/java/inetsoft/report/io/viewsheet/AbstractVSExporter.java
git commit -m "Phase 9C item 5: modernize the object-frame border and warm page on export"
```

> Guarded by `cloneVs` so it only mutates the freshly-cloned export viewsheet — the "export clones upstream" assumption `VSTitleChromeDefaults.applyModernDefaultsInPlace` documents. `prepareSheet` (which reads the page bg at `PDFVSExporter:1054` / `SVGCoordinateHelper:120`) runs later at `:1279`, so the substitution is seen. `viewsheet.getAssemblies()` returns top-level assemblies; embedded-viewsheet children are a documented boundary (Task 7, Step 5).

---

## Task 7: Validation — gate-off parity, gate-on axis + border + page, live/export match, boundaries

**Files:** none (verification task). Requires a built/running server (`docker compose up` from `docker/target/docker-test`), an org, and a viewsheet with: a **radar chart**, a **funnel chart**, a **table**, a **crosstab**, and a **chart** — all using default (un-customized) axis-line, border, and sheet-background formats. Toggle the gate via EM → Look and Feel (or `SreeEnv` `viewsheet.modernVisualization` / `viewsheet.modernChartChrome` / `viewsheet.modernObjectChrome`).

- [ ] **Step 1: Gate-off byte-identical**

With `viewsheet.modernVisualization=false`, open each object and export (PDF/PNG/SVG). Confirm axis lines render legacy `#EEEEEE`, object frames render legacy `#DADADA`, the viewer page is the legacy gray and the export page is white (unchanged legacy mismatch), and saved sheet XML is unchanged (diff the asset). Expected: identical to pre-plan.

- [ ] **Step 2: Gate-on axis-line coverage**

Enable the gate. Reopen the **radar** chart: the outer **label axis** line renders `#E8E5DE`, matching its value axes (Task 1). Reopen the **funnel** chart: the y-axis line is `#E8E5DE` (Task 2). Confirm live and exported (PDF/PNG/SVG) match.

- [ ] **Step 3: Gate-on object frame + warm page, live + export**

With the gate on, confirm: the outer frame of the **table**, **crosstab**, and **chart** renders `#D9D5CC` (Task 4/6); the viewer page/canvas is warm `#F8F7F4` (Task 5) with **white cards** (chart body stays `#ffffff`); and the exported page (PDF/PNG/SVG) is also `#F8F7F4` with the `#D9D5CC` frame (Task 6). Confirm live == export for the page and frame (the pre-existing gray-viewer/white-export mismatch is now unified). Confirm the frame matches the title bar's bottom rule (`VSTitleChromeDefaults`), and the table body (transparent) reads the warm page through it.

- [ ] **Step 4: Defaults-only proof**

- Set an explicit object border via the format dialog on the table → the user color wins under the gate; clearing it returns to `#D9D5CC`.
- Set an explicit viewsheet background (sheet Format → background) → the user color wins over `#F8F7F4` in viewer and export; clearing it returns to the modern warm page.

- [ ] **Step 5: Boundary checks**

- A table assigned a **CSS `TableStyle`** whose default border is not `#DADADA`: the frame is left at the style's color (value-equality guard, R3).
- **Other object types** (text/output, gauge, selection list, image): frame consistent — text/output already `#D9D5CC` via `VSOutputChromeDefaults`; the rest now match via the central seam. No regression.
- A viewsheet whose theme sets a **`format.css` `Viewsheet` background** on the composite (USER/CSS tier): confirm it still wins over `#F8F7F4` (defaults-only). If a theme sets the page via the CSS `Viewsheet` *fallback* only, confirm the modern `#F8F7F4` now takes precedence under the gate (intended — that fallback is the legacy default being modernized).
- **Embedded-viewsheet** child object frames: note whether they modernize (top-level `getAssemblies()` may not reach them) — record as a known boundary.
- `git diff --stat` confirms core Java only (no CSS/frontend, no new `SreeEnv` property).

- [ ] **Step 6: Scheduled-export org-scope check**

Trigger a scheduled export under the gate; confirm `VSObjectChromeDefaults.isModern()` and `VSChartChromeDefaults.isModern()` resolve their org-scoped `SreeEnv` reads on the scheduler thread (as the sibling defaults already do).

---

## Task 8: Update the swatches + Phase 9C / roadmap status notes

**Files:**
- Modify: `docs/superpowers/specs/lookfeel/visualization-palette-swatches.html` (annotate `--border-default` and `--surface-canvas`)
- Modify: `docs/superpowers/specs/lookfeel/visualization-phase9c-deferred-consolidation-implementation-plan.md` (item 5 status)
- Modify: `docs/superpowers/specs/lookfeel/visualization-implementation-roadmap.md` (Phase 9C item 5 line `:1010-1011`)

- [ ] **Step 1: Annotate the swatches**

Annotate `--border-default: #D9D5CC` to note it now also governs the modern **object frame** (`VSObjectChromeDefaults`; legacy `#DADADA`). Annotate `--surface-canvas: #F8F7F4` to note it now governs the modern **viewsheet page** (`VSObjectChromeDefaults`; keeps `--surface-default #FFFFFF` cards; unifies the legacy gray-viewer/white-export fallback).

- [ ] **Step 2: Mark item 5 status in the Phase 9C plan and roadmap**

- In `visualization-phase9c-deferred-consolidation-implementation-plan.md` scheduled item 5, add: "Status (implemented 2026-07-24): axis-line polish shipped as Phase 6 B3; this pass closed the radar label-axis and funnel y-axis coverage gaps, modernized the object-frame border `#DADADA`→`#D9D5CC`, and warmed the viewsheet page to `#F8F7F4` (white cards kept) via `VSObjectChromeDefaults` — central live seam `VSObjectModel.createFormatModel`, live page in `CoreLifecycleService`, export in-place on the cloned viewsheet — gated by `viewsheet.modernObjectChrome`, defaults-only, export-consistent (unifying the prior gray-viewer/white-export page mismatch)."
- In `visualization-implementation-roadmap.md` (Phase 9C item 5, `:1010-1011`), append the implemented-status note mirroring the item-2/item-3 precedent.

- [ ] **Step 3: Commit**

```bash
git add docs/superpowers/specs/lookfeel/visualization-palette-swatches.html \
        docs/superpowers/specs/lookfeel/visualization-phase9c-deferred-consolidation-implementation-plan.md \
        docs/superpowers/specs/lookfeel/visualization-implementation-roadmap.md
git commit -m "Phase 9C item 5: record axis coverage + object frame + warm page shipped"
```

---

## Validation summary

1. **Build/compile:** `./mvnw -q -pl core test-compile` clean; `git diff --name-only` shows only core Java (`RadarGraphGenerator`, `GraphGenerator`, `VSObjectChromeDefaults`, `VSObjectModel`, `CoreLifecycleService`, `AbstractVSExporter`), one new test, and docs — no CSS/frontend, no new property.
2. **Unit:** `VSObjectChromeDefaultsTest` green (colors, gate-on/off frame border, user/CSS/non-legacy preserved, page-bg in-place, page-bg gate-off no-op, user page-bg preserved); sibling chrome suites still green.
3. **Gate-off byte-identical:** every object live + PDF/PNG/SVG unchanged; viewer gray / export white page unchanged; no saved XML change (Task 7.1).
4. **Gate-on axis coverage:** radar label axis + funnel y-axis `#E8E5DE`, live == export (Task 7.2).
5. **Gate-on frame + page:** frames `#D9D5CC`; page `#F8F7F4` with white cards; live == export (page mismatch unified) (Task 7.3).
6. **Defaults-only:** a user border and a user sheet background each win over the modern default (Task 7.4).
7. **Boundaries:** CSS-`TableStyle` border untouched; other object types consistent; user/`format.css` sheet bg wins; embedded-vs boundary recorded (Task 7.5).
8. **Org scope:** scheduled export resolves both gates per-org (Task 7.6).

---

## Branching (per CLAUDE.md)

Community-only core Java (two axis one-liners, the new `VSObjectChromeDefaults` + test, the `VSObjectModel` seam, the `CoreLifecycleService` branch, the `AbstractVSExporter` block, docs), continuing the visualization work on `viz-updates` alongside Phases 5–9. Confirm whether item 5 stays on `viz-updates` or a child `feature-{issue}`. Nothing on `main` or a `v1.0.x`/`v1.1.x` release branch; nothing pushed/PR'd without explicit approval. An enterprise submodule-pointer bump only at PR time.

---

## Related

- [visualization-phase9c-deferred-consolidation-implementation-plan.md](../specs/lookfeel/visualization-phase9c-deferred-consolidation-implementation-plan.md) — scheduled item 5 (this plan implements + broadens it)
- [visualization-phase6-implementation-plan.md](../specs/lookfeel/visualization-phase6-implementation-plan.md) — Part B3 (the shipped axis-line resolver this plan extends)
- [2026-07-23-viz-phase9c-item3-group-subtotal-emphasis.md](2026-07-23-viz-phase9c-item3-group-subtotal-emphasis.md) — sibling Phase 9C item; the gated-resolver + manual-render-validation structure this plan mirrors
- [visualization-palette-swatches.html](../specs/lookfeel/visualization-palette-swatches.html) — `--border-default #D9D5CC`, `--surface-canvas #F8F7F4`, `--surface-default #FFFFFF`, `--viz-gridline #E8E5DE`
