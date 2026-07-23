# Modern Default Sequential Gradient Ramp — Phase 9C Item 2 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Under the org-scoped modern visualization gate, replace the legacy default 2-color chart
gradient (`#FF99CC` → `#008000`, a magenta→green rainbow) with a modern **single-hue, light→dark blue**
sequential ramp on the server render path, so continuous/gradient-colored charts match the modern
categorical palette shipped in Phase 8 — defaults-only, export-consistent, and byte-identical when the
gate is off.

**Architecture:** This is the sequential-ramp sibling of Phase 8's categorical work. It adds a fifth
gated resolver in `uql/viewsheet/internal/` — **`VSChartRampDefaults`** — mirroring
`VSChartPaletteDefaults` exactly: it rides `VSDensityDefaults.isModern()` plus a per-family sub-gate
(`viewsheet.modernChartRamp`) and, when on, swaps a render-time `GradientColorFrame`'s **default** from/to
colors. It is injected at the two pure-render seams where a `GradientColorFrame` is already wired
(`VGraphPair` live, `CSSProcessor` export) — the branches and the adjacent `applyModernPalette(...)` calls
already exist, so each seam gains one line. The persisted-design seam (`ChangeChartProcessor`) is
**deliberately excluded** to guarantee no modern color is ever baked into saved asset XML.

**Tech Stack:** Java 21 (core graph engine + viewsheet render seams), JUnit 5 (`@Tag("core")`,
`@SreeHome`). No Angular/CSS/frontend change — chart marks are 100% server-rendered (System B).

## Global Constraints

Copied verbatim from the Phase 8 plan and the roadmap; every task's requirements implicitly include
this section.

- **Render-location rule.** Chart marks are server-rendered and appear in export; browser `--inet-viz-*`
  CSS cannot color them. The modern gradient **must** be driven server-side. The reserved
  `--inet-viz-ramp-sequential-*` / `--inet-viz-ramp-diverging-*` token names stay **conceptual and
  un-wired** in the browser (Phase 1 contract §2c) — this task bridges the *sequential default* subset
  into System B only.
- **Gate is server-side and org-scoped.** Modern gradient is selected by
  `SreeEnv.getBooleanProperty("viewsheet.modernVisualization", false, true)` (3rd arg = org-scoped) read
  through `VSDensityDefaults.isModern()`, **plus** a per-family sub-gate `viewsheet.modernChartRamp` that
  defaults ON when the base gate is on and opts out only on the literal string `"false"`.
- **Defaults-only.** Modern gradient applies only when neither the USER tier (`userFrom`/`userTo` — a
  user-picked gradient endpoint) nor a customer `format.css` `ChartPalette[index=1/2]` rule
  (`cssFrom`/`cssTo`) has set the value. `GradientColorFrame.getFromColor()` resolves
  user → css → default (`GradientColorFrame.java:100`, `:108`), so a user color and a customer CSS rule
  always win after the swap.
- **Gate-off byte-identical.** With the gate off, every render path — live, PDF, PNG, SVG, Excel, PPT,
  HTML — must be pixel-identical to today. No saved XML may change.
- **No destructive rename.** Introduce the modern values additively; never repoint the legacy
  `GradientColorFrame` field defaults (`0xFF99CC` / `0x008000`) or the shipped `defaults.css` rules.
- **Scope: default gradient only.** `GradientColorFrame` (the default continuous ramp) is the *only*
  ramp source bridged. `HSLColorFrame`, the 27 named ColorBrewer schemes (`BluesColorFrame`,
  `RdBuColorFrame`, `SpectralColorFrame`, …), `HeatColorFrame`, and `BipolarColorFrame` are **left on
  legacy** — ColorBrewer schemes are perceptually-optimized standards the user picks *by name*, and no
  modern design values exist for them. A guard test (Task 4) pins this boundary.
- **Naming.** New server resolver: `VSChartRampDefaults` in
  `core/src/main/java/inetsoft/uql/viewsheet/internal/`, mirroring `VSChartPaletteDefaults`. New
  sub-gate property: `viewsheet.modernChartRamp`.

### Modern ramp values → **NEEDS OWNER SIGN-OFF (design values)**

No modern ramp values exist anywhere in the specs (the design spec lists only conceptual token *names*).
Per the Phase 8 D1 precedent, this plan proposes concrete values for owner sign-off, grounded in the
shipped modern categorical primary blue `#3B82F6` (the Phase 8 D1 series-6 hex, Tailwind blue-500) and
the dataviz non-negotiable **"Sequential = one hue, light→dark."**

| Endpoint | Hex | Role |
|---|---|---|
| `from` (ratio 0, low values) | `#DBEAFE` | light blue tint (Tailwind blue-100) |
| `to`   (ratio 1, high values) | `#1E3A8A` | deep blue (Tailwind blue-900) |

Both endpoints share hue ≈ 217°; linear interpolation stays inside the blue family and passes near the
categorical primary `#3B82F6` at mid-ramp, tying the ramp to the shipped categorical head. Orientation
matches the legacy frame (`from` = low/light, `to` = high/dark). Owner confirms the two hexes.
Dark-mode variants ride the initiative's dark pass (Phase 9B / D7), unchanged.

---

## Grounding (verified against current code, branch `viz-updates`, 2026-07-23)

### The resolver to mirror

`VSChartPaletteDefaults.java` (Phase 8, `uql/viewsheet/internal/`): `final` class, private ctor,
static-only. `isModern()` = `VSDensityDefaults.isModern() && !"false".equals(SreeEnv.getProperty(
"viewsheet.modernChartPalette", false, true))`. `applyModernPalette(CategoricalColorFrame)` no-ops when
off. `VSChartRampDefaults` is the fifth sibling (after Chrome/Title/Output/Palette) and copies this shape.

### `GradientColorFrame` — the default continuous ramp

| Anchor | Location | Note |
|---|---|---|
| Color-tier resolution | `GradientColorFrame.java:99-110` | `getToColor()` = `userTo ?? cssTo ?? defaultTo`; `getFromColor()` = `userFrom ?? cssFrom ?? defaultFrom`. Defaults-only precedence is built in — user & css win before default. |
| Legacy field defaults | `GradientColorFrame.java:258-259` | `defaultFrom = new Color(0xFF99CC)`, `defaultTo = new Color(0x008000)`. **Never edited** by this task. |
| **Default-tier setters (already exist)** | `GradientColorFrame.java:132-144` | `setDefaultFromColor(Color)` / `setDefaultToColor(Color)` — both call `init()`, which recomputes the interpolation basis (`a1/r1/g1/b1`, `disA/R/G/B`) from `getFromColor()/getToColor()` (`:55-70`). So the swap takes effect immediately; **no Phase-8-style stale-cache bug**. |
| CSS read | `GradientColorFrame.updateCSSColors():220-238` | reads `ChartPalette[index=1]`→`cssFrom`, `[index=2]`→`cssTo`; triggered by `setParentParams`. Customer CSS wins over the modern default. |
| **Serialization (the key difference from Phase 8)** | `GradientColorFrameWrapper.java:111-112` (write), `:145-150` (parse) | `writeContents` writes `getDefaultFromColor()/getDefaultToColor()` as `fromColor`/`toColor` attrs; `parseContents` routes them **back to `setDefaultFromColor/To`** — i.e. **default→default**, NOT default→user. Unlike Phase 8 categorical (default→user promotion), a swapped modern default stays a *default* and is still overridable by user/css on reload. The wrapper's `setDefaultFromColor` (`:57-60`) sets the `changed` flag, but the **render seams call the frame method directly** (`GradientColorFrame.setDefaultFromColor`, `:132`), bypassing the wrapper → the `changed` flag is not set by the swap (same as Phase 8). |

### The render seams (gradient branch already present)

| Seam | Location | Path | Include? |
|---|---|---|---|
| Live viewsheet render | `VGraphPair.java:1301-1303` | `else if(instanceof GradientColorFrame) { …setParentParams(parentParams); }` inside the `for(runtime)` / `getAestheticRefs` loop; `applyModernPalette(ccf)` already sits in the sibling categorical branch (`:1298`). Mutates a **runtime** frame (transient). | **YES** |
| Export / standalone-report render | `CSSProcessor.java:475-480` | gradient branch calls `setCSSDictionary` + `setParentParams`; sibling categorical branch already calls `applyModernPalette` (`:472`). Mutates a **runtime** frame (transient). | **YES** |
| Composer change/preview | `ChangeChartProcessor.java:1895-1897` | gradient branch on the persisted **design** assembly (`VSChartDataHandler.changeChartData` → this method; the `assembly` ∈ `rvs.getViewsheet()`). Swapping here + a save could rewrite the stored `fromColor/toColor` default. | **NO — excluded (Decision R2)** |

Imports: `VGraphPair.java:50` and `ChangeChartProcessor.java:35` already `import inetsoft.uql.viewsheet.internal.*;`
(covers the new class). `CSSProcessor.java:29` uses a **specific** import (`…internal.VSChartPaletteDefaults;`)
so it needs one added import line for `VSChartRampDefaults`.

### Scope-boundary sources (left legacy — Task 4 guard)

| Source | Location | Why left legacy |
|---|---|---|
| `HSLColorFrame` | `graph/aesthetic/HSLColorFrame.java` | separate continuous (hue-cycle) frame; owner scoped this task to the *gradient* default only. |
| ColorBrewer ramps (27) | `graph/aesthetic/{Blues,RdBu,Spectral,…}ColorFrame.java` extends `AbstractSplineColorFrame` | hardcoded per-subclass ramps, user-selected **by name**; already perceptually-optimized standards. No modern values; re-coloring a named standard is worse than ColorBrewer. |
| `HeatColorFrame` / `BipolarColorFrame` | `graph/aesthetic/*.java` | hardcoded `double[][]` RGB-cube paths; no hook; out of the "default gradient only" scope. |

---

## Decisions

- **R1 — Bridge only `GradientColorFrame` (default gradient).** Owner-selected scope. Highest-impact,
  cleanest bridge; leaves named ColorBrewer schemes and Heat/Bipolar on their standards.
- **R2 — Inject at the two pure-render seams only (`VGraphPair` + `CSSProcessor`); exclude
  `ChangeChartProcessor`.** VGraphPair and CSSProcessor mutate transient runtime frames that are never
  serialized → zero XML risk, and live == export (both included). `ChangeChartProcessor` mutates the
  composer's **persisted design assembly**; because gradient's `setDefaultFromColor` *always* overwrites
  the stored default (even on a user-customized chart) and `writeContents` always serializes
  `getDefaultFromColor()`, swapping there could rewrite the saved `fromColor/toColor` default under the
  gate. Excluding it guarantees the "gate-off byte-identical / no saved XML change" constraint literally.
  Cost: during a composer binding-change *preview*, the gradient may momentarily render legacy until the
  full `VGraphPair` render runs — acceptable (preview-only, not export). Verified in Task 5.
- **R3 — Modern values proposed for sign-off (see Global Constraints table).** `#DBEAFE` → `#1E3A8A`,
  single-hue light→dark blue.

---

## Task 1: `VSChartRampDefaults` resolver

**Files:**
- Create: `core/src/main/java/inetsoft/uql/viewsheet/internal/VSChartRampDefaults.java`
- Test: `core/src/test/java/inetsoft/uql/viewsheet/internal/VSChartRampDefaultsTest.java`

**Interfaces:**
- Consumes: `VSDensityDefaults.isModern()`; `inetsoft.sree.SreeEnv.getProperty(String,boolean,boolean)`;
  `inetsoft.graph.aesthetic.GradientColorFrame` (`setDefaultFromColor(Color)`, `setDefaultToColor(Color)`,
  `getFromColor()`, `getToColor()`).
- Produces (used by Tasks 2–3):
  - `public static boolean isModern()`
  - `public static java.awt.Color modernFromColor()` — `#DBEAFE`
  - `public static java.awt.Color modernToColor()` — `#1E3A8A`
  - `public static void applyModernGradient(GradientColorFrame frame)` — if `isModern()`, set the frame's
    default from/to to the modern values; else no-op.

- [ ] **Step 1: Write the failing test**

```java
package inetsoft.uql.viewsheet.internal;

import inetsoft.graph.aesthetic.GradientColorFrame;
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
class VSChartRampDefaultsTest {
   @AfterEach
   void reset() {
      SreeEnv.setProperty("viewsheet.modernVisualization", null);
      SreeEnv.setProperty("viewsheet.modernChartRamp", null);
   }

   @Test
   void gateOffReturnsFalseAndLeavesGradientUntouched() {
      SreeEnv.setProperty("viewsheet.modernVisualization", "false");
      assertFalse(VSChartRampDefaults.isModern());

      GradientColorFrame frame = new GradientColorFrame();
      VSChartRampDefaults.applyModernGradient(frame);
      // gate off => still the legacy endpoints
      assertEquals(new Color(0xFF99CC), frame.getFromColor());
      assertEquals(new Color(0x008000), frame.getToColor());
   }

   @Test
   void gateOnSwapsGradientToModernBlue() {
      SreeEnv.setProperty("viewsheet.modernVisualization", "true");
      assertTrue(VSChartRampDefaults.isModern());
      assertEquals(new Color(0xDBEAFE), VSChartRampDefaults.modernFromColor());
      assertEquals(new Color(0x1E3A8A), VSChartRampDefaults.modernToColor());

      GradientColorFrame frame = new GradientColorFrame();
      VSChartRampDefaults.applyModernGradient(frame);
      assertEquals(new Color(0xDBEAFE), frame.getFromColor());
      assertEquals(new Color(0x1E3A8A), frame.getToColor());
   }

   @Test
   void userAndCssEndpointsStillWinAfterSwap() {
      SreeEnv.setProperty("viewsheet.modernVisualization", "true");
      GradientColorFrame frame = new GradientColorFrame();
      frame.setUserFromColor(new Color(0x123456)); // user tier
      VSChartRampDefaults.applyModernGradient(frame);
      // defaults-only: user endpoint wins over the swapped default
      assertEquals(new Color(0x123456), frame.getFromColor());
      // the unset endpoint takes the modern default
      assertEquals(new Color(0x1E3A8A), frame.getToColor());
   }

   @Test
   void subGateFalseOptsOutEvenWhenBaseGateOn() {
      SreeEnv.setProperty("viewsheet.modernVisualization", "true");
      SreeEnv.setProperty("viewsheet.modernChartRamp", "false");
      assertFalse(VSChartRampDefaults.isModern());
   }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw -q -pl core test -Dtest=VSChartRampDefaultsTest -DfailIfNoTests=false`
Expected: FAIL — `VSChartRampDefaults` does not exist (compilation error).

- [ ] **Step 3: Write minimal implementation**

Model the file on `VSChartPaletteDefaults.java` verbatim (license header, `final` class, private ctor,
static-only). Body:

```java
package inetsoft.uql.viewsheet.internal;

import inetsoft.graph.aesthetic.GradientColorFrame;
import inetsoft.sree.SreeEnv;

import java.awt.Color;

/**
 * Gated modern default sequential gradient ramp (visualization Phase 9C item 2). Rides the org-scoped
 * modern gate; applied to a render-time gradient color frame only. User endpoints and a customer
 * format.css ChartPalette[index=1/2] rule still win (checked before defaults in
 * GradientColorFrame.getFromColor/getToColor). Single-hue light->dark blue, grounded in the Phase 8
 * modern categorical primary (#3B82F6).
 */
public final class VSChartRampDefaults {
   private VSChartRampDefaults() {
   }

   public static boolean isModern() {
      return VSDensityDefaults.isModern() &&
         !"false".equals(SreeEnv.getProperty("viewsheet.modernChartRamp", false, true));
   }

   public static Color modernFromColor() {
      return MODERN_FROM;
   }

   public static Color modernToColor() {
      return MODERN_TO;
   }

   public static void applyModernGradient(GradientColorFrame frame) {
      if(frame != null && isModern()) {
         frame.setDefaultFromColor(MODERN_FROM);
         frame.setDefaultToColor(MODERN_TO);
      }
   }

   // R3 (NEEDS OWNER SIGN-OFF): single-hue light->dark blue, hue ~217 deg, passing near the
   // shipped modern categorical primary #3B82F6 at mid-ramp.
   private static final Color MODERN_FROM = new Color(0xDBEAFE);
   private static final Color MODERN_TO = new Color(0x1E3A8A);
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./mvnw -q -pl core test -Dtest=VSChartRampDefaultsTest -DfailIfNoTests=false`
Expected: PASS (4 tests).

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/inetsoft/uql/viewsheet/internal/VSChartRampDefaults.java \
        core/src/test/java/inetsoft/uql/viewsheet/internal/VSChartRampDefaultsTest.java
git commit -m "Phase 9C item 2: gated modern default sequential gradient resolver"
```

---

## Task 2: Inject the modern gradient at the live render seam (`VGraphPair`)

**Files:**
- Modify: `core/src/main/java/inetsoft/report/composition/graph/VGraphPair.java:1301-1303`
- Test: `core/src/test/java/inetsoft/report/composition/graph/VGraphPairModernGradientTest.java`

**Interfaces:**
- Consumes: `VSChartRampDefaults.applyModernGradient(GradientColorFrame)` (Task 1); the existing seam at
  `VGraphPair.java:1301-1303`.
- Produces: modern gradient on the live viewsheet chart render path when the gate is on.

- [ ] **Step 1: Write the failing test**

Mirror Phase 8's `VGraphPairModernPaletteTest`: pin the resolver contract at the seam's granularity (a
full graph render needs a live `ViewsheetSandbox` and is the eye-test in Task 5, not a unit test).

```java
package inetsoft.report.composition.graph;

import inetsoft.graph.aesthetic.GradientColorFrame;
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
class VGraphPairModernGradientTest {
   @AfterEach
   void reset() {
      SreeEnv.setProperty("viewsheet.modernVisualization", null);
      SreeEnv.setProperty("viewsheet.modernChartRamp", null);
   }

   @Test
   void gateOnSwapsGradientFrameToModernBlue() {
      SreeEnv.setProperty("viewsheet.modernVisualization", "true");
      GradientColorFrame frame = new GradientColorFrame();
      // simulate the seam call added to VGraphPair.fixChartFormat
      inetsoft.uql.viewsheet.internal.VSChartRampDefaults.applyModernGradient(frame);
      assertEquals(new Color(0xDBEAFE), frame.getFromColor());
      assertEquals(new Color(0x1E3A8A), frame.getToColor());
   }

   @Test
   void gateOffLeavesLegacyGradient() {
      SreeEnv.setProperty("viewsheet.modernVisualization", "false");
      GradientColorFrame frame = new GradientColorFrame();
      inetsoft.uql.viewsheet.internal.VSChartRampDefaults.applyModernGradient(frame);
      assertEquals(new Color(0xFF99CC), frame.getFromColor());
      assertEquals(new Color(0x008000), frame.getToColor());
   }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw -q -pl core test -Dtest=VGraphPairModernGradientTest -DfailIfNoTests=false`
Expected: PASS against the resolver if Task 1 is present (this test pins the resolver contract). Proceed
regardless — the production edit below is what makes the seam apply it on the live render.

- [ ] **Step 3: Add the gated swap at the seam**

In `VGraphPair.java`, extend the existing `GradientColorFrame` branch (`:1301-1303`) to apply the modern
gradient **after** `setParentParams` (so `updateCSSColors` runs first and customer `format.css` still
wins). Replace:

```java
               else if(ref.getVisualFrame() instanceof GradientColorFrame) {
                  ((GradientColorFrame) ref.getVisualFrame()).setParentParams(parentParams);
               }
```

with:

```java
               else if(ref.getVisualFrame() instanceof GradientColorFrame) {
                  GradientColorFrame gcf = (GradientColorFrame) ref.getVisualFrame();
                  gcf.setParentParams(parentParams);
                  VSChartRampDefaults.applyModernGradient(gcf);
               }
```

No import edit needed — `VGraphPair.java:50` already has `import inetsoft.uql.viewsheet.internal.*;`.

- [ ] **Step 4: Run test + compile to verify**

Run: `./mvnw -q -pl core test-compile && ./mvnw -q -pl core test -Dtest=VGraphPairModernGradientTest -DfailIfNoTests=false`
Expected: `core` compiles clean; test PASS.

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/inetsoft/report/composition/graph/VGraphPair.java \
        core/src/test/java/inetsoft/report/composition/graph/VGraphPairModernGradientTest.java
git commit -m "Phase 9C item 2: apply modern gradient at the live chart render seam (VGraphPair)"
```

---

## Task 3: Inject the modern gradient at the export render seam (`CSSProcessor`)

**Files:**
- Modify: `core/src/main/java/inetsoft/report/css/CSSProcessor.java` (add import at `:29`; extend the
  gradient branch at `:475-480`)
- Test: none new — parity with `VGraphPair` is the same resolver; export parity is verified in Task 5.

**Interfaces:**
- Consumes: `VSChartRampDefaults.applyModernGradient(...)`.
- Produces: gate-on modern gradient on the export/standalone-report render path, so live and export match.

- [ ] **Step 1: Grounding sub-step — confirm the gradient seam list**

Run: `rg -n "instanceof GradientColorFrame" core/src/main/java`
Expected exactly three sites: `VGraphPair.java:1301` (Task 2, included), `CSSProcessor.java:475` (this
task, included), `ChangeChartProcessor.java:1895` (**excluded** per Decision R2). If a fourth site is
found on a render path, record it and add the swap there too (unless it is a persisted-design path, in
which case treat it like `ChangeChartProcessor` and exclude with a note).

- [ ] **Step 2: Add the import**

`CSSProcessor.java:29` currently reads `import inetsoft.uql.viewsheet.internal.VSChartPaletteDefaults;`.
Add directly beneath it:

```java
import inetsoft.uql.viewsheet.internal.VSChartRampDefaults;
```

- [ ] **Step 3: Add the gated swap at the gradient branch**

In `CSSProcessor.java`, extend the existing gradient branch (`:475-480`). Replace:

```java
            else if(ref.getVisualFrame() instanceof GradientColorFrame) {
               GradientColorFrame gcf = (GradientColorFrame) ref.
                  getVisualFrame();
               gcf.setCSSDictionary(cssDict);
               gcf.setParentParams(parentParams);
            }
```

with:

```java
            else if(ref.getVisualFrame() instanceof GradientColorFrame) {
               GradientColorFrame gcf = (GradientColorFrame) ref.
                  getVisualFrame();
               gcf.setCSSDictionary(cssDict);
               gcf.setParentParams(parentParams);
               VSChartRampDefaults.applyModernGradient(gcf);
            }
```

- [ ] **Step 4: Confirm `ChangeChartProcessor` is left untouched (Decision R2)**

Run: `rg -n "VSChartRampDefaults" core/src/main/java/inetsoft/report/internal/graph/ChangeChartProcessor.java`
Expected: **no matches**. The persisted-design seam must not carry the swap. (This is the guarantee behind
"no saved XML change", proven end-to-end in Task 5 Step 4.)

- [ ] **Step 5: Compile + run the ramp suite**

Run: `./mvnw -q -pl core test-compile && ./mvnw -q -pl core test -Dtest="VSChartRampDefaultsTest,VGraphPairModernGradientTest" -DfailIfNoTests=false`
Expected: compiles clean; all tests PASS.

- [ ] **Step 6: Commit**

```bash
git add core/src/main/java/inetsoft/report/css/CSSProcessor.java
git commit -m "Phase 9C item 2: apply modern gradient on the export chart render seam (CSSProcessor)"
```

---

## Task 4: Guard test — non-gradient ramps stay legacy under the gate

**Files:**
- Test: `core/src/test/java/inetsoft/graph/aesthetic/RampScopeGuardTest.java`

**Interfaces:**
- Consumes: `HSLColorFrame`, `BluesColorFrame`, `HeatColorFrame`, `BipolarColorFrame` (default constructors).
- Produces: a tested statement that Phase 9C item 2 bridges **only** `GradientColorFrame`, so a future
  accidental bridge of a named/heat/bipolar/HSL ramp is caught.

- [ ] **Step 1: Write the guard test**

`HSLColorFrame` defaults to a fixed start hue; `BluesColorFrame` (a ColorBrewer spline) exposes its ramp
via `getColor(double)`; `HeatColorFrame`/`BipolarColorFrame` are RGB-cube frames. We pin the low-end
endpoint of each under the gate and assert it is unchanged — the swap must not reach them.

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
class RampScopeGuardTest {
   @AfterEach
   void reset() {
      SreeEnv.setProperty("viewsheet.modernVisualization", null);
      SreeEnv.setProperty("viewsheet.modernChartRamp", null);
   }

   @Test
   void nonGradientRampsUnchangedUnderModernGate() {
      // Phase 9C item 2 (R1): scope is the DEFAULT GRADIENT ONLY. Capture legacy endpoints with the
      // gate off, then assert they are identical with the gate on.
      SreeEnv.setProperty("viewsheet.modernVisualization", "false");
      Color hslOff = new HSLColorFrame().getColor(0.0);
      Color bluesOff = new BluesColorFrame().getColor(0.0);
      Color heatOff = new HeatColorFrame().getColor(0.0);
      Color bipolarOff = new BipolarColorFrame().getColor(0.0);

      SreeEnv.setProperty("viewsheet.modernVisualization", "true");
      assertEquals(hslOff, new HSLColorFrame().getColor(0.0), "HSL ramp must stay legacy");
      assertEquals(bluesOff, new BluesColorFrame().getColor(0.0), "ColorBrewer Blues must stay legacy");
      assertEquals(heatOff, new HeatColorFrame().getColor(0.0), "Heat ramp must stay legacy");
      assertEquals(bipolarOff, new BipolarColorFrame().getColor(0.0), "Bipolar ramp must stay legacy");
   }
}
```

- [ ] **Step 2: Run test to verify it passes as-is**

Run: `./mvnw -q -pl core test -Dtest=RampScopeGuardTest -DfailIfNoTests=false`
Expected: PASS. If it FAILS, a change leaked outside the gradient scope — investigate before proceeding.
(These frames have no `applyModern…` call site, so the pass is by construction; the test locks the
boundary in.)

- [ ] **Step 3: Commit**

```bash
git add core/src/test/java/inetsoft/graph/aesthetic/RampScopeGuardTest.java
git commit -m "Phase 9C item 2: guard test pinning default-gradient-only scope (R1)"
```

---

## Task 5: Validation — gate-off parity + gate-on live/export + persistence round-trip

**Files:** none (verification task). Requires a built/running server (`docker compose up` from
`docker/target/docker-test`), an org with a chart binding a **numeric measure to the color aesthetic
using the default gradient** (no user-picked endpoints, no customer `format.css ChartPalette`), and the
gate toggled via EM → Look and Feel (or `SreeEnv` `viewsheet.modernVisualization`).

- [ ] **Step 1: Gate-off byte-identical**

With `viewsheet.modernVisualization=false`, render the gradient-colored chart and export it
(PDF/PNG/SVG/Excel). Confirm the ramp is the legacy magenta→green (`#FF99CC`→`#008000`) and that the
saved chart XML is unchanged (diff the asset). Expected: identical to pre-item-2.

- [ ] **Step 2: Gate-on modern gradient, live + export**

Enable the gate for the org. Render the same chart. Expected: live **and** every export format show the
modern light→dark blue ramp (`#DBEAFE`→`#1E3A8A`); low values light, high values dark. Confirm live and
export match (both included seams share the resolver).

- [ ] **Step 3: Defaults-only proof**

(a) A chart with a **user-picked** gradient endpoint (set via the color dialog): the picked endpoint
still wins under the gate; only the unset endpoint takes the modern default.
(b) An org with a customer `format.css` `ChartPalette[name='Default'][index='1']` (and `[index='2']`)
rule: the customer colors still win under the gate (`cssFrom`/`cssTo` precede `defaultFrom`/`defaultTo`).
Both must hold in live and export.

- [ ] **Step 4: Persistence round-trip — prove no bake-in (Decision R2 guarantee)**

With the gate **ON**, change the gradient-colored chart's binding in the composer (drives
`VSChartDataHandler.changeChartData` → `ChangeChartProcessor.updateColorFrameCSSParentParams`), save,
then turn the gate **OFF**, reload / open as a different org, and **diff the saved asset's gradient XML**
(`fromColor`/`toColor` attributes in the `GradientColorFrame` wrapper block — serialized as
`getRGB()` signed ints, i.e. the ARGB form of the legacy `0xFF99CC` / `0x008000` with the `0xFF` alpha
byte). Expected: the attributes still decode to the legacy `0xFF99CC` / `0x008000` — modern hexes must
**NOT** be persisted, and the chart reverts to legacy with the gate off. If modern hexes appear,
`ChangeChartProcessor` is carrying the swap (it must not — re-check Task 3 Step 4).

- [ ] **Step 5: Boundary checks**

Run: `git diff --stat` and confirm: no `GradientColorFrame` field-default edit (`0xFF99CC`/`0x008000`
intact); no `defaults.css` change; no `HSLColorFrame`/ColorBrewer/`HeatColorFrame`/`BipolarColorFrame`
edit; no `ChangeChartProcessor` change; no `--inet-viz-ramp-*` wired to a mark
(`rg -n "inet-viz-ramp" web/projects` returns only commented/reserved names).

- [ ] **Step 6: Scheduled-export org-scope check**

Trigger a scheduled export of the gradient chart under the gate; confirm `isModern()`'s org-scoped
`SreeEnv` read resolves on the scheduler thread (as the density/chrome/palette resolvers already do).

---

## Task 6: Update the palette swatches + roadmap / phase-9c status note

**Files:**
- Modify: `docs/superpowers/specs/lookfeel/visualization-palette-swatches.html`
- Modify: `docs/superpowers/specs/lookfeel/visualization-phase9c-deferred-consolidation-implementation-plan.md`
  (mark scheduled item 2 status)
- Modify: `docs/superpowers/specs/lookfeel/visualization-implementation-roadmap.md` (Phase 9C / Phase 8
  ramp-deferral status line)

- [ ] **Step 1: Add a modern sequential ramp swatch**

In the "Chart Palette Comparison" section of the swatches, add a card for the **Modern Default Sequential
Ramp**: a light→dark blue bar from `#DBEAFE` to `#1E3A8A`, noting it is the Phase 9C item 2 gated modern
default for `GradientColorFrame`, bridged server-side via `VSChartRampDefaults` (defaults-only; user
endpoints and customer `format.css ChartPalette[index=1/2]` still win). Note it is single-hue light→dark
(dataviz sequential rule) and passes near the categorical primary `#3B82F6` at mid-ramp. Do **not**
present the named ColorBrewer schemes or Heat/Bipolar as changed — they remain legacy.

- [ ] **Step 2: Mark item 2 status in the Phase 9C plan and the roadmap**

In `visualization-phase9c-deferred-consolidation-implementation-plan.md`, annotate scheduled item 2 with
a "Status (implemented …): default gradient bridged (gated, export-consistent); named ColorBrewer /
Heat / Bipolar / HSL deferred with grounded reasons." Add the mirroring note under the roadmap's Phase 8
ramp-deferral line (item 2 is the follow-through on Phase 8 D5).

- [ ] **Step 3: Commit**

```bash
git add docs/superpowers/specs/lookfeel/visualization-palette-swatches.html \
        docs/superpowers/specs/lookfeel/visualization-phase9c-deferred-consolidation-implementation-plan.md \
        docs/superpowers/specs/lookfeel/visualization-implementation-roadmap.md
git commit -m "Phase 9C item 2: record modern default gradient shipped; note remaining ramp deferrals"
```

---

## Validation summary

1. **Build/compile:** `./mvnw -q -pl core test-compile` clean; no web change expected (grep-verify
   `rg -n "inet-viz-ramp" web/projects` returns only reserved names).
2. **Unit:** `VSChartRampDefaultsTest`, `VGraphPairModernGradientTest`, `RampScopeGuardTest` green.
3. **Gate-off byte-identical:** live + PDF/PNG/SVG/Excel unchanged; no saved XML change (Task 5.1).
4. **Gate-on:** modern light→dark blue ramp, live == export (Task 5.2).
5. **Defaults-only:** user endpoint and customer `format.css ChartPalette` still win (Task 5.3).
6. **No bake-in:** gate-on composer edit + save + gate-off reload → legacy XML preserved (Task 5.4);
   `ChangeChartProcessor` untouched (Task 3.4).
7. **Scope boundary:** only `GradientColorFrame` bridged; HSL/ColorBrewer/Heat/Bipolar legacy (Task 4,
   Task 5.5).
8. **Org scope:** scheduled export resolves the gate per-org (Task 5.6).

---

## Branching (per CLAUDE.md)

Community-only core Java (the `VSChartRampDefaults` resolver + two render-seam one-liners + tests +
docs), continuing the visualization work on `viz-updates` alongside Phases 3–9. Confirm whether item 2
stays on `viz-updates` or a child `feature-{issue}`. Nothing on `main` or a `v1.0.x`/`v1.1.x` release
branch; nothing pushed/PR'd without explicit approval. An enterprise submodule-pointer bump only at PR
time.

---

## Related

- [visualization-phase9c-deferred-consolidation-implementation-plan.md](../specs/lookfeel/visualization-phase9c-deferred-consolidation-implementation-plan.md) — scheduled item 2 (this plan implements it)
- [visualization-phase8-implementation-plan.md](../specs/lookfeel/visualization-phase8-implementation-plan.md) — the categorical resolver + render seams this plan mirrors; D5 (ramp deferral) is the origin
- [visualization-design-spec.md](../specs/lookfeel/visualization-design-spec.md) — Analytical Color Rules; `--inet-viz-ramp-sequential-*` conceptual token names
- [visualization-palette-swatches.html](../specs/lookfeel/visualization-palette-swatches.html) — modern categorical head (`#3B82F6` primary) the ramp is grounded in
