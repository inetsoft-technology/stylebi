# StyleBI Visualization — Phase 8 (Analytical Color Systems) — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development
> (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use
> checkbox (`- [ ]`) syntax for tracking.

**Goal:** Introduce a gated, export-consistent **modern categorical chart palette** on the
server-side graph render path, and record the render-location-correct ownership of the remaining
analytical color systems (sequential/diverging ramps, threshold/target color, conditional
formatting) — with everything but the categorical palette scoped as documented deferral because the
code has no modern default to bridge for them yet.

**Architecture:** Phase 8 rides the mechanism the initiative already built — an org-scoped
`SreeEnv` modern gate (`VSDensityDefaults.isModern()`) plus a per-family sub-gate, consumed by a
`final` resolver class in `uql/viewsheet/internal/` that supplies modern default `Color`s only when
neither the user nor a customer `format.css` has set the value. Phase 8 adds a fourth-generation
sibling of `VSChartChromeDefaults` / `VSTitleChromeDefaults` / `VSOutputChromeDefaults`:
**`VSChartPaletteDefaults`**, injected at the chart render seams where each `CategoricalColorFrame`
is wired (`setParentParams`). Because the graph engine, the live view, and every export format share
one render path, the single injection point keeps live and export in sync.

**Tech Stack:** Java 21 (core graph engine + viewsheet render seams), JUnit 5 (`@Tag("core")`,
`@SreeHome`). No Angular/CSS change ships in this phase (the browser-DOM half is already covered by
Phases 6/6A and has no new analytical-color surface — see Part A).

## Global Constraints

Copied from the roadmap and design spec; every task's requirements implicitly include this section.

- **Render-location rule.** Chart marks **and** in-graph chrome are server-rendered and appear in
  export; browser `--inet-viz-*` CSS cannot color them. Any color that must appear in export **must**
  be driven server-side. (`visualization-design-spec.md` → Rendering And Theming Architecture.)
- **Gate is server-side and org-scoped.** The browser `.viz-modern` class cannot switch any
  server-rendered graph color. Modern graph color is selected by
  `SreeEnv.getBooleanProperty("viewsheet.modernVisualization", false, true)` (3rd arg = org-scoped),
  read through `VSDensityDefaults.isModern()`, plus a per-family sub-gate property that defaults ON
  when the base gate is on and opts out only on the literal string `"false"`.
- **Defaults-only.** Modern color applies only when neither the USER tier (a user-picked series
  color / `format.css`) nor the customer CSS dictionary has set the value. A user color and a
  customer `format.css ChartPalette` rule always win.
- **Gate-off byte-identical.** With the gate off, every render path — live, PDF, PNG, SVG, Excel,
  PPT, HTML — must be pixel-identical to today. No saved XML may change.
- **No destructive rename.** Introduce modern values additively; never repoint the legacy
  `CategoricalColorFrame.COLOR_PALETTE` or the shipped `defaults.css ChartPalette` rules.
- **Naming.** New server resolver: `VSChartPaletteDefaults` in
  `core/src/main/java/inetsoft/uql/viewsheet/internal/`, mirroring the three sibling resolvers.
  New sub-gate property: `viewsheet.modernChartPalette`.
- **Scope discipline (roadmap "Implementation Note"): do not redesign every chart type.** Phase 8
  ships the categorical palette; ramps, target/threshold color, and conditional-formatting authoring
  presets are documented deferrals with grounded reasons, not silent omissions.

---

## Render-location boundary (read this first)

This is the single most important framing, verified against current code (branch `viz-updates`,
2026-07-17). Almost all chart color is **server-rendered**; the browser cannot drive it.

- **System B — server-rendered graph (the payload).** The graph engine (`inetsoft.graph.*`) paints
  marks *and* in-graph chrome into the chart image/SVG (including inline SVG, where fill/stroke are
  element attributes set server-side). Every series color resolves through
  `CategoricalColorFrame.getColor(...)`: **userColors → cssColors (`format.css ChartPalette[index=N]`,
  only when the frame is styleable) → `defaultColors` (= `COLOR_PALETTE`, 40 colors)**
  (`CategoricalColorFrame.java:304-322`). This path is shared by the live view **and every export
  format**, so Phase 8's categorical work lives here.
- **System A — browser DOM (a small surround).** Only the object title bar (Phase 6A,
  `VSTitleChromeDefaults`), the chart toolbar, and the interactive tooltip (Phase 6 Part A) are
  Angular DOM. None appears in export, and none is an analytical-**data**-color surface — so Phase 8
  has **no new browser-CSS work** (Part A below).

**Consequence:** the `--inet-viz-chart-series-*` / `--inet-viz-ramp-*` / `--inet-viz-threshold-*` /
`--inet-viz-conditional-*` names reserved in `visualization-phase1-contract.md` §2c are a
**conceptual** palette definition. Phase 8 bridges the categorical subset into System B and leaves
the names un-wired in the browser, exactly as §2c requires.

---

## Grounding (verified against current code, branch `viz-updates`, 2026-07-17)

### The modern gate and the resolver pattern to mirror

| Anchor | Location | Note |
|---|---|---|
| Base gate | `VSDensityDefaults.isModern()` (`uql/viewsheet/internal/VSDensityDefaults.java:39-41`) | `SreeEnv.getBooleanProperty("viewsheet.modernVisualization", false, true)` (org-scoped) |
| Sibling resolver (chrome) | `VSChartChromeDefaults.java` (88 lines) | `isModern()` = base gate `&&` `!"false".equals(SreeEnv.getProperty("viewsheet.modernChartChrome", false, true))`; constants `GRIDLINE 0xE8E5DE`, `LABEL 0x6A685F`, `TITLE 0x35342F`; private ctor, all static |
| Sibling resolver (title) | `VSTitleChromeDefaults.java` (Phase 6A, 145 lines) | `viewsheet.modernObjectChrome` sub-gate; `applyModernDefaults`/`…InPlace` DEFAULT-tier pattern |
| Sibling resolver (output) | `VSOutputChromeDefaults.java` (Phase 7 Part B, 172 lines) | shares `viewsheet.modernObjectChrome`; class doc notes a `HighlightGroup` on a higher tier still wins |

The new `VSChartPaletteDefaults` is the fourth sibling and copies this exact shape.

### Categorical series palette (the payload)

| Anchor | Location | Note |
|---|---|---|
| Palette constant | `CategoricalColorFrame.java:50-64` | `public static final Color[] COLOR_PALETTE` — **40** colors; first entry `0x518db9`. Seeded into `defaultColors` in the ctor (`:72`). |
| Resolution / fallback | `CategoricalColorFrame.java:304-322` | `getColor(int index,int oindex,boolean negative,boolean unassigned)`: userColors (`:305`) → cssColors iff `parentParams != null` (`:307-308`) → `defaultColors.get(index % size)` (`:316-317`). |
| **Public palette setter** | `CategoricalColorFrame.java:477-481` | `public void setDefaultColors(Color ...defaultColors)` — the render-time swap seam. **Bugfix 2026-07-17:** it now calls `clearUsedColors()` (mirroring `setDefaultColor`) so the swap invalidates the cached `unusedColors`/`unassignedScale` the value-based render path serves; without it, marks kept the stale legacy palette while the edit-color dropdown showed modern. `defaultColors` field `:645`. |
| CSS palette read | `CategoricalColorFrame.updateCSSColors()` (`:494-522`) | builds `cssColors` from `ChartPalette[index=N]` (1-based, `:506-508`); triggered by `setParentParams` (inherited `ColorFrame.setParentParams` `:137-140`). Customer CSS wins over defaults. |
| Shipped CSS mirror | `core/src/main/resources/inetsoft/util/css/defaults.css:18-176` | `ChartPalette[name='Default'][index='1'..'40']` mirror `COLOR_PALETTE` exactly. **Not touched** by Phase 8 (customer-override baseline). |
| **Primary render seam** | `VGraphPair.fixChartFormat` — `VGraphPair.java:1296-1298` | `if(ref.getVisualFrame() instanceof CategoricalColorFrame) ((CategoricalColorFrame)…).setParentParams(parentParams);` inside the `for(runtime)` / `getAestheticRefs` loop (`:1290-1306`). Phase 6 already gates adjacent code on `VSChartChromeDefaults.isModern()` at `:1335`. |
| Export/report seam | `report/css/CSSProcessor.java:471` | categorical `setParentParams` on the export/standalone-report path. |
| Change/preview seam | `report/internal/graph/ChangeChartProcessor.java:1891` | categorical `setParentParams` on the change-chart/preview path. |
| Composer-DnD seam | `web/binding/dnd/controller/VSChartDndService.java` (near the `CSSChartStyles.apply` at `:224`) | categorical frame wiring on the DnD clone path. |
| Frames and serialization — **CORRECTED (final review 2026-07-17)** | `CategoricalColorFrameWrapper.writeContents:187-232`; `parseContents:294-327`; `CategoricalColorFrame.setColor:383-384` | **The earlier "not serialized" claim was WRONG.** `defaultColors` **is** serialized (`<colors>` block), as are `<cssColors>`/`<userColors>`. On reload, indexed `<colors>` entries route through `setColor(index)` → `setUserColor` → **`userColors`** (wins over `defaultColors`). The **render seams** (`VGraphPair` live, `CSSProcessor` export) mutate a **runtime** frame → transient overlay, safe. But the **`ChangeChartProcessor` seam runs on the composer's persisted design assembly** (`VSChartDataHandler.changeChartData:412-414/452`, `assembly` ∈ `rvs.getViewsheet()`); `setDefaultColors` does not set the `changed` flag, so a **gate-on binding edit + save can bake modern colors into the asset XML** (→ reloads into `userColors`, survives gate-off / cross-org). **OPEN — must be confirmed/closed by the expanded Task 6 round-trip checks before merge.** |

### Sequential / diverging ramps (deferred — grounded)

| Anchor | Location | Note |
|---|---|---|
| Gradient endpoints | `GradientColorFrame.java:98-110`, defaults `:258-259` | `getFromColor/getToColor` resolve user → css → default; `updateCSSColors` (`:220-238`) reads `ChartPalette[index='1'/'2']`. Defaults `0xFF99CC`/`0x008000`. **Reads CSS index 1/2 only — NOT `defaultColors`**, so swapping the categorical palette does not touch it. |
| ColorBrewer ramps | `graph/rgb/AbstractSplineColorFrame.java` + `Blues/Spectral/RdBu/…` | ramps hardcoded as hex strings per subclass; **no CSS, no GDefaults hook, no `parentParams` wiring**. Un-bridgeable without net-new plumbing. |
| Heat / Bipolar | `HeatColorFrame.java:66-70`, `BipolarColorFrame.java:49-53` | hardcoded `double[][]` RGB paths; no hook. |

No modern sequential/diverging ramp values exist in `visualization-palette-swatches.html` (only
categorical series + state tokens). → **Deferred** (Decision D5).

### Threshold / target-line color (optional — grounded)

| Anchor | Location | Note |
|---|---|---|
| Target line default | `GraphTarget.java:781` | `private Color lineColor = GDefaults.DEFAULT_TARGET_LINE_COLOR;` = `0xafafad` (`GDefaults.java:145`). `fillAbove/BelowColor` default `null` (`:782-783`). |

Single hardcoded neutral; low visual weight; usually user-set when meaningful. → **Optional / low
priority** (Decision D6).

### Conditional formatting (no server default to bridge — grounded)

| Anchor | Location | Note |
|---|---|---|
| Highlight color fields | `report/filter/TextHighlight.java:310-312` | `foreground`/`background`/`tFont` all default **`null`** — an unset highlight simply does not override. **There is no server-side default color to modernize.** |
| Table render | `report/internal/table/TableHighlightAttr.java` → `HighlightTableLens` (`:624`, `getForeground` `:659`, `getBackground` `:677`) | resolves user highlights to per-cell fg/bg on the render path; cell wins over row wins over base. |
| Output render | `OutputVSAssemblyInfo.java:86-108` (`updateHighlight`), fields `:891-893` | resolved fg/bg/font cached transient; live via `VSFormatModel.java:127-159`; export via `AbstractVSExporter.java:1912-1924` (highlight on USER tier, applied **after** `VSOutputChromeDefaults.applyModernDefaultsInPlace` at `:1910`, so highlight wins). |
| No semantic CSS classes | `util/css/CSSConstants.java` (read in full) | no warning/anomaly/threshold/error color classes exist; nearest is `CHART_PLOT_ERROR` (error-bar element, not a color). |
| DOM tokens already shipped | `web/projects/portal/src/scss/_viz-tokens.scss` (Phase 4) | `--inet-viz-warning-bg #F8E8CC`, `--inet-viz-anomaly-bg #F7DEDE` exist; zero server consumers (correct — highlights are user-authored). |

Because highlight colors are 100% user-defined with `null` defaults, Phase 8 adds **no** render code
to the highlight path. Its conditional-formatting deliverable is the usage documentation + the
already-shipped tokens (Decision D4).

---

## Decisions (proposed — resolve with initiative owner before Part B)

### D1 — Modern categorical palette values → **NEEDS OWNER SIGN-OFF (design values)**

The modern light palette from `visualization-palette-swatches.html` (series-1..8):

| Idx | Hex | | Idx | Hex |
|---|---|---|---|---|
| 1 | `#00D4E8` | | 5 | `#8B5CF6` |
| 2 | `#00B87A` | | 6 | `#3B82F6` |
| 3 | `#F59E0B` | | 7 | `#0D9488` |
| 4 | `#F43F5E` | | 8 | `#64748B` |

These differ from today's legacy head (`#518db9, #b9dbf4, #62a640, …`). **Recommendation:** define
the modern **first 8** as above and, for indices 9+, **append the legacy `COLOR_PALETTE` tail
(entries 9..40 unchanged)** so high-cardinality charts keep 40 distinct colors and do not wrap early.
The alternative (a full 40-color modern set) requires 32 more design values that do not exist yet.
Owner confirms the 8 hexes and the "8 modern + legacy tail" composition. Dark-mode variants
(`series-dark-*`) ride the initiative's dark pass (D7).

### D2 — Change the default palette at all → **RECOMMEND YES, gated**

Introducing a modern default palette is explicitly what the roadmap Phase 8 Task 1 asks for
("treat the modern categorical palettes … as the single source of truth, then bridge that definition
into the server-side sources"). It is high visual impact (every chart using default colors changes
when an org enables the gate) but it is **org-opt-in** and **gate-off byte-identical**, matching the
risk profile the initiative accepted for Phase 3 density and Phase 5 table structure.

### D3 — Injection seam: swap `defaultColors` at the render seams, gated → **RECOMMEND YES**

Add `VSChartPaletteDefaults.applyModernPalette(CategoricalColorFrame)` and call it at each seam where
a categorical frame is wired (`VGraphPair.java:1297`, `CSSProcessor.java:471`,
`ChangeChartProcessor.java:1891`, `VSChartDndService`), gated on `isModern()`. Rationale:
- Mirrors how `CSSChartStyles.apply(...)` is itself invoked at these same seams (Phase 6) rather
  than pushed into the graph engine.
- Respects layering: the gate/`SreeEnv` read stays in `uql/viewsheet/internal` (viewsheet layer);
  `CategoricalColorFrame` (`graph/aesthetic`, the lowest graph layer) gains **no** viewsheet
  dependency. Do **not** put the gate inside `CategoricalColorFrame.updateCSSColors()` (layering
  inversion).
- Defaults-only for free: `getColor` checks userColors and cssColors **before** `defaultColors`
  (`:305-317`), so a user series color and a customer `format.css ChartPalette` still win after the
  swap. The swap is not serialized (transient frame).

**Tradeoff (accepted):** the swap is replicated at ~4 call sites (less DRY) to keep the layering
clean. **Grounding step required in Task 3** to confirm the exact seam list and the swap ordering
relative to `setParentParams`/`updateCSSColors` (the CSS-index read uses `getColorCount()`).

### D4 — Conditional formatting = tokens + usage docs, no render code → **RECOMMEND YES**

Highlight colors default to `null` (grounded), so there is no export-visible default to bridge. The
`--inet-viz-warning-bg`/`-anomaly-bg` tokens already shipped in Phase 4. Phase 8 documents the usage
rules (server-rendered highlight vs DOM state token; keep meaning-bearing) and adds no code to the
`HighlightGroup` path. A future "modern threshold/anomaly highlight **preset**" in the highlight
authoring dialog is a product feature → deferred (D8).

### D5 — Sequential/diverging ramps deferred → **RECOMMEND YES**

`GradientColorFrame` reads only `ChartPalette` index 1/2 (not `defaultColors`), and the ColorBrewer /
Heat / Bipolar ramps have no CSS/GDefaults hook. No modern ramp values are defined in the swatches.
Bridging ramps would need net-new plumbing **and** new design values, which the roadmap's "do not
redesign every chart type" note argues against for the first pass. Deferred with grounded reasons
(Deferred item 1). *Optional stretch:* if the owner wants gradient (2-color) to follow the modern
palette, bridge `GradientColorFrame` from/to to modern index-1/index-2 under the gate — small, but
out of the recommended first pass.

### D6 — Target/threshold line neutral: optional, low priority → **RECOMMEND DEFER**

`GraphTarget.lineColor` default `0xafafad` is a single subtle neutral, usually overridden when the
target carries meaning. A gated substitution (mirroring `resolveAxisLineColor`) is cheap but
low-value; recommend deferring to keep Phase 8 focused (Deferred item 2). Include only if the owner
wants chrome-neutral unification completed.

### D7 — Dark-mode palette deferred → **RECOMMEND YES** (unchanged from Phases 5/6/7)

Part B is light-first; `series-dark-*` rides the initiative's dark pass.

### D8 — Highlight authoring presets deferred → **RECOMMEND YES**

Offering modern warning/anomaly colors as one-click presets in the highlight dialog is a UI feature,
not a theming default. Deferred.

---

## Parts

- **Part A — Browser-DOM analytical color.** **No shippable code.** Per the render boundary, the
  only DOM chart surfaces (title bar, toolbar, tooltip) are done (Phases 6/6A) and are neutral
  chrome, not data color; the reserved `--inet-viz-chart-*` names stay un-wired (Phase 1 §2c).
  Part A's deliverable is the render-location documentation in this plan + a verification grep
  (Task 6, step "confirm no `--inet-viz-chart-series` is wired to a mark"). Mirrors Phase 6 Part A
  being intentionally thin.
- **Part B — Server-side categorical palette (the payload).** Tasks 1–4.
- **Part C — Conditional-formatting documentation (no code).** Task 5.
- **Validation + swatch note.** Tasks 6–7.

---

## Task 1: `VSChartPaletteDefaults` resolver

**Files:**
- Create: `core/src/main/java/inetsoft/uql/viewsheet/internal/VSChartPaletteDefaults.java`
- Test: `core/src/test/java/inetsoft/uql/viewsheet/internal/VSChartPaletteDefaultsTest.java`

**Interfaces:**
- Consumes: `VSDensityDefaults.isModern()`; `inetsoft.sree.SreeEnv.getProperty(String,boolean,boolean)`;
  `inetsoft.graph.aesthetic.CategoricalColorFrame` (`COLOR_PALETTE`, `setDefaultColors(Color...)`,
  `getColorCount()`, `getColor(int)`).
- Produces (used by Tasks 2–3):
  - `public static boolean isModern()`
  - `public static java.awt.Color[] modernPalette()` — the resolved modern default array (D1: 8
    modern + legacy tail)
  - `public static void applyModernPalette(CategoricalColorFrame frame)` — if `isModern()`, swap the
    frame's default colors to `modernPalette()`; else no-op

- [ ] **Step 1: Write the failing test**

```java
package inetsoft.uql.viewsheet.internal;

import inetsoft.graph.aesthetic.CategoricalColorFrame;
import inetsoft.sree.SreeEnv;
import inetsoft.test.SreeHome;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.Color;

import static org.junit.jupiter.api.Assertions.*;

@SreeHome
@ExtendWith(SpringExtension.class)
@Tag("core")
class VSChartPaletteDefaultsTest {
   @AfterEach
   void reset() {
      SreeEnv.setProperty("viewsheet.modernVisualization", null);
      SreeEnv.setProperty("viewsheet.modernChartPalette", null);
   }

   @Test
   void gateOffReturnsFalseAndLeavesPaletteUntouched() {
      SreeEnv.setProperty("viewsheet.modernVisualization", "false");
      assertFalse(VSChartPaletteDefaults.isModern());

      CategoricalColorFrame frame = new CategoricalColorFrame();
      VSChartPaletteDefaults.applyModernPalette(frame);
      // gate off => still the legacy head color
      assertEquals(CategoricalColorFrame.COLOR_PALETTE[0], frame.getColor(0));
   }

   @Test
   void gateOnSwapsToModernHeadButKeepsLegacyTail() {
      SreeEnv.setProperty("viewsheet.modernVisualization", "true");
      assertTrue(VSChartPaletteDefaults.isModern());

      Color[] modern = VSChartPaletteDefaults.modernPalette();
      assertEquals(40, modern.length, "8 modern + 32 legacy tail = 40");
      assertEquals(new Color(0x00D4E8), modern[0]);
      assertEquals(new Color(0x64748B), modern[7]);
      // index 9+ preserves the legacy tail unchanged
      assertEquals(CategoricalColorFrame.COLOR_PALETTE[8], modern[8]);
      assertEquals(CategoricalColorFrame.COLOR_PALETTE[39], modern[39]);

      CategoricalColorFrame frame = new CategoricalColorFrame();
      VSChartPaletteDefaults.applyModernPalette(frame);
      assertEquals(new Color(0x00D4E8), frame.getColor(0));
      assertEquals(new Color(0x00B87A), frame.getColor(1));
   }

   @Test
   void subGateFalseOptsOutEvenWhenBaseGateOn() {
      SreeEnv.setProperty("viewsheet.modernVisualization", "true");
      SreeEnv.setProperty("viewsheet.modernChartPalette", "false");
      assertFalse(VSChartPaletteDefaults.isModern());
   }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw -q -pl core test -Dtest=VSChartPaletteDefaultsTest -DfailIfNoTests=false`
Expected: FAIL — `VSChartPaletteDefaults` does not exist (compilation error).

- [ ] **Step 3: Write minimal implementation**

Model the file on `VSChartChromeDefaults.java` verbatim (license header, `final` class, private
ctor, static-only). Body:

```java
package inetsoft.uql.viewsheet.internal;

import inetsoft.graph.aesthetic.CategoricalColorFrame;
import inetsoft.sree.SreeEnv;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Gated modern default categorical chart palette (visualization Phase 8). Rides the org-scoped
 * modern gate; applied to a render-time color frame only, never serialized. User series colors and
 * a customer format.css ChartPalette rule still win (checked before defaults in
 * CategoricalColorFrame.getColor).
 */
public final class VSChartPaletteDefaults {
   private VSChartPaletteDefaults() {
   }

   public static boolean isModern() {
      return VSDensityDefaults.isModern() &&
         !"false".equals(SreeEnv.getProperty("viewsheet.modernChartPalette", false, true));
   }

   public static Color[] modernPalette() {
      // D1: 8 modern head + legacy tail (indices 9..40 unchanged) so high-cardinality
      // charts keep 40 distinct colors and do not wrap early.
      List<Color> palette = new ArrayList<>(Arrays.asList(MODERN_HEAD));
      Color[] legacy = CategoricalColorFrame.COLOR_PALETTE;
      palette.addAll(Arrays.asList(legacy).subList(MODERN_HEAD.length, legacy.length));
      return palette.toArray(new Color[0]);
   }

   public static void applyModernPalette(CategoricalColorFrame frame) {
      if(frame != null && isModern()) {
         frame.setDefaultColors(modernPalette());
      }
   }

   private static final Color[] MODERN_HEAD = {
      new Color(0x00D4E8), new Color(0x00B87A), new Color(0xF59E0B), new Color(0xF43F5E),
      new Color(0x8B5CF6), new Color(0x3B82F6), new Color(0x0D9488), new Color(0x64748B)
   };
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./mvnw -q -pl core test -Dtest=VSChartPaletteDefaultsTest -DfailIfNoTests=false`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/inetsoft/uql/viewsheet/internal/VSChartPaletteDefaults.java \
        core/src/test/java/inetsoft/uql/viewsheet/internal/VSChartPaletteDefaultsTest.java
git commit -m "Phase 8 B1: gated modern categorical chart palette resolver"
```

---

## Task 2: Inject the modern palette at the primary render seam (`VGraphPair`)

**Files:**
- Modify: `core/src/main/java/inetsoft/report/composition/graph/VGraphPair.java:1296-1298`
- Test: `core/src/test/java/inetsoft/report/composition/graph/VGraphPairModernPaletteTest.java`

**Interfaces:**
- Consumes: `VSChartPaletteDefaults.applyModernPalette(CategoricalColorFrame)` (Task 1);
  the existing seam `VGraphPair.java:1296-1298`.
- Produces: modern series color on the live viewsheet chart render path when the gate is on.

- [ ] **Step 1: Write the failing test**

The most robust unit-level assertion (no full graph render) exercises the resolver applied to the
frame taken from an aesthetic ref, matching what the seam does. Build a minimal `VSChartInfo` with a
color aesthetic ref bound to a `CategoricalColorFrame`, then assert the seam swapped the palette.

```java
package inetsoft.report.composition.graph;

import inetsoft.graph.aesthetic.CategoricalColorFrame;
import inetsoft.sree.SreeEnv;
import inetsoft.test.SreeHome;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.Color;

import static org.junit.jupiter.api.Assertions.*;

@SreeHome
@ExtendWith(SpringExtension.class)
@Tag("core")
class VGraphPairModernPaletteTest {
   @AfterEach
   void reset() {
      SreeEnv.setProperty("viewsheet.modernVisualization", null);
      SreeEnv.setProperty("viewsheet.modernChartPalette", null);
   }

   @Test
   void gateOnSwapsCategoricalFrameToModernHead() {
      SreeEnv.setProperty("viewsheet.modernVisualization", "true");
      CategoricalColorFrame frame = new CategoricalColorFrame();
      // simulate the seam call added to VGraphPair.fixChartFormat
      inetsoft.uql.viewsheet.internal.VSChartPaletteDefaults.applyModernPalette(frame);
      assertEquals(new Color(0x00D4E8), frame.getColor(0));
   }

   @Test
   void gateOffLeavesLegacyPalette() {
      SreeEnv.setProperty("viewsheet.modernVisualization", "false");
      CategoricalColorFrame frame = new CategoricalColorFrame();
      inetsoft.uql.viewsheet.internal.VSChartPaletteDefaults.applyModernPalette(frame);
      assertEquals(CategoricalColorFrame.COLOR_PALETTE[0], frame.getColor(0));
   }
}
```

> Note for the implementer: this test pins the resolver contract at the seam's granularity. A
> full-render assertion (building a `VGraph` and reading a mark's fill) is the eye-test / manual
> parity check in Task 6 — do not attempt a full graph render in a unit test; it needs a live
> `ViewsheetSandbox`.

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw -q -pl core test -Dtest=VGraphPairModernPaletteTest -DfailIfNoTests=false`
Expected: FAIL only if Task 1 not present; otherwise this test passes against the resolver. If it
passes here, still proceed — the production edit below is what makes the seam actually apply it.

- [ ] **Step 3: Add the gated swap at the seam**

In `VGraphPair.java`, inside the `for(runtime)` / `getAestheticRefs` loop, extend the existing
categorical branch (`:1296-1298`) to apply the modern palette **after** `setParentParams` (so the
CSS-index read in `updateCSSColors` runs first and customer `format.css` still wins):

```java
if(ref.getVisualFrame() instanceof CategoricalColorFrame) {
   CategoricalColorFrame ccf = (CategoricalColorFrame) ref.getVisualFrame();
   ccf.setParentParams(parentParams);
   VSChartPaletteDefaults.applyModernPalette(ccf);
}
```

Add the import `import inetsoft.uql.viewsheet.internal.VSChartPaletteDefaults;` (Phase 6 already
imports `VSChartChromeDefaults` from the same package here, so the import block already references
`uql.viewsheet.internal`).

- [ ] **Step 4: Run test + compile to verify**

Run: `./mvnw -q -pl core test-compile && ./mvnw -q -pl core test -Dtest=VGraphPairModernPaletteTest -DfailIfNoTests=false`
Expected: `core` compiles clean; test PASS.

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/inetsoft/report/composition/graph/VGraphPair.java \
        core/src/test/java/inetsoft/report/composition/graph/VGraphPairModernPaletteTest.java
git commit -m "Phase 8 B1: apply modern palette at the live chart render seam (VGraphPair)"
```

---

## Task 3: Replicate the injection on the export / preview / DnD seams

**Files:**
- Modify: `core/src/main/java/inetsoft/report/css/CSSProcessor.java:471`
- Modify: `core/src/main/java/inetsoft/report/internal/graph/ChangeChartProcessor.java:1891`
- Modify: `core/src/main/java/inetsoft/web/binding/dnd/controller/VSChartDndService.java`
  (the categorical `setParentParams` near `:224`)
- Test: extend `VSChartPaletteDefaultsTest` with a "all seams call the same helper" contract note (no
  new render test — parity is verified in Task 6).

**Interfaces:**
- Consumes: `VSChartPaletteDefaults.applyModernPalette(...)`.
- Produces: gate-on modern palette on **every** render path so live and export stay in sync.

- [ ] **Step 1: Grounding sub-step — confirm the seam list**

Run: `rg -n "instanceof CategoricalColorFrame" core/src/main/java` and
`rg -n "setParentParams" core/src/main/java/inetsoft/report core/src/main/java/inetsoft/web/binding`.
Confirm exactly which sites wire a categorical frame on a render path (expected: the four above).
Record any additional seam found; add the swap there too. This closes Decision D3's grounding
requirement.

- [ ] **Step 2: Add the gated swap at `CSSProcessor.java:471`**

Wherever the categorical branch calls `setParentParams`, follow it with
`VSChartPaletteDefaults.applyModernPalette(ccf);` on the same frame reference, plus the import.
Example shape:

```java
ccf.setParentParams(parentParams);
inetsoft.uql.viewsheet.internal.VSChartPaletteDefaults.applyModernPalette(ccf);
```

- [ ] **Step 3: Add the gated swap at `ChangeChartProcessor.java:1891` and `VSChartDndService`**

Same one-line addition after each categorical `setParentParams`. Use the resolver's static no-op-when-
off contract, so gate-off is byte-identical on every path.

- [ ] **Step 4: Compile + run the core color suite**

Run: `./mvnw -q -pl core test-compile && ./mvnw -q -pl core test -Dtest="VSChartPaletteDefaultsTest,VGraphPairModernPaletteTest" -DfailIfNoTests=false`
Expected: compiles clean; all tests PASS.

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/inetsoft/report/css/CSSProcessor.java \
        core/src/main/java/inetsoft/report/internal/graph/ChangeChartProcessor.java \
        core/src/main/java/inetsoft/web/binding/dnd/controller/VSChartDndService.java
git commit -m "Phase 8 B1: apply modern palette on export/preview/DnD chart render seams"
```

---

## Task 4: Confirm gradient rides / does not ride the palette; record ramp deferral

**Files:**
- Modify (docs only): this plan's "Deferred" section is authoritative; no code change unless the
  owner approves the optional gradient bridge (D5 stretch).
- Test: `core/src/test/java/inetsoft/graph/aesthetic/GradientColorFrameGateTest.java` (a guard test
  that documents current behavior, so a future accidental bridge is caught).

**Interfaces:**
- Consumes: `GradientColorFrame` (`getFromColor/getToColor`, defaults `0xFF99CC`/`0x008000`).
- Produces: an explicit, tested statement that Phase 8 leaves gradient/diverging ramps on legacy.

- [ ] **Step 1: Write the guard test**

```java
package inetsoft.graph.aesthetic;

import inetsoft.sree.SreeEnv;
import inetsoft.test.SreeHome;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.Color;

import static org.junit.jupiter.api.Assertions.*;

@SreeHome
@ExtendWith(SpringExtension.class)
@Tag("core")
class GradientColorFrameGateTest {
   @AfterEach
   void reset() { SreeEnv.setProperty("viewsheet.modernVisualization", null); }

   @Test
   void gradientEndpointsStayLegacyUnderModernGate() {
      // Phase 8 D5: ramps are deferred; the categorical palette swap must NOT touch gradient.
      SreeEnv.setProperty("viewsheet.modernVisualization", "true");
      GradientColorFrame frame = new GradientColorFrame();
      assertEquals(new Color(0xFF99CC), frame.getFromColor());
      assertEquals(new Color(0x008000), frame.getToColor());
   }
}
```

- [ ] **Step 2: Run test to verify it passes as-is**

Run: `./mvnw -q -pl core test -Dtest=GradientColorFrameGateTest -DfailIfNoTests=false`
Expected: PASS (documents that no gradient bridge shipped). If it FAILS, a categorical change leaked
into the gradient frame — investigate before proceeding.

- [ ] **Step 3: Commit**

```bash
git add core/src/test/java/inetsoft/graph/aesthetic/GradientColorFrameGateTest.java
git commit -m "Phase 8: guard test documenting deferred ramp/gradient scope (D5)"
```

---

## Task 5: Conditional-formatting usage documentation (no code)

**Files:**
- Modify: `docs/superpowers/specs/lookfeel/visualization-phase8-implementation-plan.md` (this file —
  the "Conditional formatting" grounding + Decision D4 are the deliverable).

**Interfaces:** none (documentation task).

- [ ] **Step 1: Confirm the tokens exist and have no server consumer**

Run: `rg -n "inet-viz-warning-bg|inet-viz-anomaly-bg" web/projects/portal/src/scss/_viz-tokens.scss`
Expected: both tokens present (Phase 4). Run
`rg -n "inet-viz-warning|inet-viz-anomaly" web/projects/portal/src` and confirm they are used only in
DOM state contexts (no viewsheet-assembly render binding).

- [ ] **Step 2: Confirm highlight defaults are `null` (no server default to modernize)**

Run: `rg -n "foreground = null|background = null" core/src/main/java/inetsoft/report/filter/TextHighlight.java`
Expected: matches at `:311-312`. This is the evidence backing Decision D4 — record the finding as a
line in the plan's changelog note (see Task 7).

- [ ] **Step 3: Commit (docs)**

```bash
git add docs/superpowers/specs/lookfeel/visualization-phase8-implementation-plan.md
git commit -m "Phase 8: document conditional-formatting = tokens + usage rules, no server default (D4)"
```

---

## Task 6: Validation — gate-off parity + gate-on manual export check

**Files:** none (verification task). Requires a built/running server (`docker compose up` from
`docker/target/docker-test`), an org with charts using default series colors, and the modern gate
toggled via EM → Look and Feel (or `SreeEnv` `viewsheet.modernVisualization`).

- [ ] **Step 1: Gate-off byte-identical**

With `viewsheet.modernVisualization=false`, render a multi-series bar chart and export it
(PDF/PNG/SVG/Excel). Confirm the mark colors are the legacy palette head (`#518db9, #b9dbf4, …`) and
that saved chart XML is unchanged (diff the asset). Expected: identical to pre-Phase-8.

- [ ] **Step 2: Gate-on modern palette, live + export**

Enable the gate for the org. Render the same chart. Expected live **and** every export format show
the modern head (`#00D4E8, #00B87A, #F59E0B, …`) for the first 8 series, legacy tail for series 9+.
Confirm live and export match (one render path).

- [ ] **Step 3: Defaults-only proof**

(a) A chart with a **user-picked** series color: the picked color still wins under the gate.
(b) An org with a customer `format.css` `ChartPalette[name='Default'][index='1']` rule: the customer
color still wins under the gate (cssColors precede defaultColors). Both must hold in live and export.

- [ ] **Step 4: Boundary checks**

Run: `git diff --stat` and confirm: no `defaults.css` change; no `COLOR_PALETTE` edit; no
`HighlightGroup`/`TextHighlight` code touched (D4); no `--inet-viz-chart-series-*` wired to a mark
(`rg -n "inet-viz-chart-series|inet-viz-ramp" web/projects` returns only commented/reserved names).

- [ ] **Step 5: Scheduled-export org-scope check**

Trigger a scheduled export of a chart under the gate; confirm `isModern()`'s org-scoped `SreeEnv`
read resolves on the scheduler thread (as the density/chrome resolvers already do).

- [ ] **Step 6: Persistence round-trip (MUST — closes the final-review OPEN risk)**

The grounding above is corrected: `defaultColors` **is** serialized and reloads into `userColors`,
and the `ChangeChartProcessor` seam mutates the composer's persisted design assembly. These checks
disambiguate whether the swap (a) actually takes effect on real (saved) viewsheets and (b) can bake
modern colors permanently.

(a) **Effect on a saved chart:** save a color-bound chart, reload it, enable the gate, render — confirm
the modern head actually appears. If it does **not**, the reload-into-`userColors` path makes the swap
inert for that chart and the feature only reaches never-round-tripped charts.

(b) **Bake-in on composer edit (the data-integrity risk):** with the gate **ON**, change a chart's
binding in the composer (drives `VSChartDataHandler.changeChartData` → `ChangeChartProcessor.updateColorFrameCSSParentParams`),
save, then turn the gate **OFF**, reload / open as a different org, and **diff the saved asset's
`<colors>` XML**. Modern hexes must **NOT** be persisted; the chart must revert to legacy. If they
persist, apply the fix: drop the `ChangeChartProcessor` seam swap (keep `VGraphPair`+`CSSProcessor`),
or guard it against persisted assemblies.

(c) Run Step 3 (defaults-only: user color + `format.css ChartPalette`) on a **saved-and-reloaded**
chart, not only a fresh one.

---

## Task 7: Update the palette swatches + roadmap note

**Files:**
- Modify: `docs/superpowers/specs/lookfeel/visualization-palette-swatches.html`
- Modify: `docs/superpowers/specs/lookfeel/visualization-implementation-roadmap.md` (Phase 8 status
  line)

- [ ] **Step 1: Annotate the swatches**

In the "Chart Palette Comparison" section, add a note on the Default Palette card that series-1..8 is
now the **Phase 8 gated modern categorical default**, bridged server-side via `VSChartPaletteDefaults`
into `CategoricalColorFrame.defaultColors` (defaults-only; customer `format.css ChartPalette` and user
colors still win). Note indices 9+ retain the legacy `COLOR_PALETTE` tail (D1). Do not present the
vivid/hue-family palettes as shipped — they remain exploratory.

- [ ] **Step 2: Mark Phase 8 status in the roadmap**

Add a "Status (implemented …)" note under Phase 8 mirroring the Phase 4/5/6 status notes: categorical
palette shipped (gated, export-consistent); ramps/target-line/conditional-format-presets deferred
with grounded reasons.

- [ ] **Step 3: Commit**

```bash
git add docs/superpowers/specs/lookfeel/visualization-palette-swatches.html \
        docs/superpowers/specs/lookfeel/visualization-implementation-roadmap.md
git commit -m "Phase 8: mark modern categorical palette shipped; record deferrals"
```

---

## Accepted behavior — palette is pinned per chart at save time (Decision A, 2026-07-17)

Verified end-to-end (code + manual test). A color-bound chart **freezes its palette into the saved
asset**: `CategoricalColorFrameWrapper.writeContents` serializes `defaultColors` into `<colors>`, and
on reload `parseContents` routes those indexed entries through `setColor(index)` → `setUserColor` →
`userColors`, which outranks `defaultColors` (`getColor` order: userColors → cssColors →
defaultColors). Consequences, all **accepted as by-design**:

- **New charts** (created/saved with the gate on) freeze the modern palette and stay modern.
- **Existing charts** saved before the gate keep their legacy palette — the modern swap cannot
  override `userColors`. This matches StyleBI's long-standing per-chart color stability (changing the
  default palette has never retroactively recolored saved charts). **This is the chosen rollout
  model (Decision A): modern applies to new charts; existing charts are not recolored.**
- **Deliberate color/palette choices are always preserved.** A user-picked color or a named palette
  (Soft/Pastel/custom) is written to `userColors` (`ColorFrameModelFactory.updateVisualFrameWrapper0:95-101`,
  guarded by `Tool.equals` so an unchanged panel writes nothing), so the swap never clobbers it. A
  customer `format.css ChartPalette` rule lands in `cssColors` and also wins.

Because the swap only reaches charts on the pure default palette, the earlier "bake-in on composer
edit" concern is **subsumed by this model**: editing/saving under the gate freezes whatever palette
is active (modern for a default-palette chart, unchanged for one with `userColors`), which is exactly
the pinning behavior above — not an unintended override.

If the product later wants **existing** charts to adopt modern on gate-enable, that is the separate,
scoped "gate-scoped load-time reconciliation" follow-up (route `<colors>` → `defaultColors` under the
gate so the swap overrides a frozen *default* while `<userColors>` edits stay preserved) — deliberately
**out of scope** here.

## Deferred / follow-ups (prioritized)

1. **Sequential/diverging ramps** (D5) — `GradientColorFrame` (CSS index 1/2 only) + ColorBrewer /
   Heat / Bipolar ramps (hardcoded, no hook). Needs net-new bridging **and** modern ramp design
   values (none exist in the swatches). Optional stretch: bridge gradient from/to to modern index-1/2.
2. **Target/threshold line neutral** (D6) — `GraphTarget.lineColor` `0xafafad`; a gated substitution
   mirroring `resolveAxisLineColor` is cheap but low-value.
3. **Modern highlight presets** (D8) — one-click warning/anomaly colors in the highlight authoring
   dialog; a product feature, not a theming default.
4. **Dark-mode categorical palette** (D7) — `series-dark-*`; rides the initiative's dark pass.
5. **Full 40-color modern set** — if the owner rejects the "8 modern + legacy tail" composition (D1),
   32 additional modern design values are needed.
6. **EM Material chrome** — separate build, admin, never exported (unchanged from Phases 3/5/6/7).

---

## Validation summary

1. **Build/compile:** `./mvnw -q -pl core test-compile` clean; `cd web && npm run build` clean
   (no web change expected — grep-verify).
2. **Unit:** `VSChartPaletteDefaultsTest`, `VGraphPairModernPaletteTest`, `GradientColorFrameGateTest`
   green.
3. **Gate-off byte-identical:** live + PDF/PNG/SVG/Excel unchanged; no saved XML change (Task 6.1).
4. **Gate-on:** modern head on first 8 series, legacy tail on 9+, live == export (Task 6.2).
5. **Defaults-only:** user series color and customer `format.css ChartPalette` still win (Task 6.3).
6. **Boundary:** no `defaults.css`/`COLOR_PALETTE`/`HighlightGroup` change; no chart-series browser
   token wired (Task 6.4).
7. **Org scope:** scheduled export resolves the gate per-org (Task 6.5).

---

## Branching (per CLAUDE.md)

Phase 8 is community-only core Java (the `VSChartPaletteDefaults` resolver + the four render-seam
one-liners + tests), continuing the visualization work on `viz-updates` alongside Phases 3–7. Confirm
whether Phase 8 stays on `viz-updates` or a child `feature-{issue}`. Nothing on `main` or a
`v1.0.x`/`v1.1.x` release branch; nothing pushed/PR'd without explicit approval. An enterprise
submodule-pointer bump only at PR time.

---

## Related

- [visualization-implementation-roadmap.md](./visualization-implementation-roadmap.md) — Phase 8
  tasks, architectural server/DOM split, the "modern-mode selection for server-rendered color"
  requirement (satisfied by the three sibling resolvers; Phase 8 adds the fourth)
- [visualization-design-spec.md](./visualization-design-spec.md) — Analytical Color Rules, Charts
  guidance, Rendering And Theming Architecture (System A/B)
- [visualization-phase1-contract.md](./visualization-phase1-contract.md) — §2c chart-color
  conceptual-source-of-truth constraint (bridge to server, do not wire to browser marks)
- [visualization-phase6-implementation-plan.md](./visualization-phase6-implementation-plan.md) — the
  chart-chrome resolver (`VSChartChromeDefaults`) + render seam this phase mirrors and sits beside
- [visualization-phase7-implementation-plan.md](./visualization-phase7-implementation-plan.md) — the
  `VSOutputChromeDefaults` DEFAULT-tier pattern and the HighlightGroup-wins interplay behind D4
- [visualization-palette-swatches.html](./visualization-palette-swatches.html) — the modern
  categorical palette values (series-1..8) this phase bridges
