# Chart Companion Colours Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add the companion colour — a soft partner for each categorical palette slot, hue held constant — and use it as the target band's fill.

**Architecture:** A pure sRGB↔OKLab utility supports a derivation rule on `CategoricalColorFrame`; `VSChartPaletteDefaults` layers an authored CSS override on top of that rule; `GraphGenerator` builds a band frame from the target measure's series colour and sets it on the `TargetForm` at render, gated by a new `bandFillUserSet` flag so an author's own band colour always wins.

**Tech Stack:** Java 21, Maven (`./mvnw`), JUnit 5, `inetsoft.graph` rendering engine, CSS-backed palette registry (`defaults.css` + `ColorPalettes`).

**Spec:** [`docs/superpowers/specs/lookfeel/2026-09-14-chart-companion-colors-design.md`](../specs/lookfeel/2026-09-14-chart-companion-colors-design.md)

## Global Constraints

- **Branch:** `feature-chart-companion-colors`, based on `epic-74519` at `0761ce5596`. Community submodule only — this ships as a community PR.
- **All CSS palette indexes are 1-based.** `ColorPalettes.loadPalettes()` skips any index below 1. The source handoff is 0-based; every hex must be shifted by one.
- **Hue is never modified by the derivation rule.** Any gamut resolution reduces chroma holding L and H. Never clamp per-channel — it shifts hue.
- **Anchor threshold is mode-specific:** `L < 0.40` in light, `L < 0.50` in dark.
- **Nothing in this slice adds a selectable palette.** `Modern-soft` and `Modern Dark-soft` must never appear in the picker.
- **`getPalette(name)` resolution stays unfiltered** — filtering resolution breaks rendering.
- Existing dashboards must remain pixel-identical: a `GraphTarget` parsed from XML with no `bandFillUserSet` attribute is treated as **author-set**.
- Verify cross-module builds with `clean`; an incremental `install` has reported SUCCESS over a real break on this branch before.
- Java files carry the existing AGPL header — copy it from a sibling file when creating a new one.
- **Every new test class MUST carry `@Tag("core")`** (`import org.junit.jupiter.api.Tag;`). `core/pom.xml:996` hardcodes Surefire `<groups>core</groups>`, so an untagged JUnit 5 test is never selected: it reports `Tests run: 0` and `BUILD SUCCESS`, which looks identical to passing. 625 of 716 test classes in the module carry it, including every existing file this plan appends to. A `-Dgroups=` flag cannot override the hardcoded value. Found during Task 1; applies to the new test classes in Tasks 2, 6 and 7.

---

### Task 1: The OKLab utility

**Files:**
- Create: `core/src/main/java/inetsoft/graph/internal/OKLab.java`
- Test: `core/src/test/java/inetsoft/graph/internal/OKLabTest.java`

**Interfaces:**
- Consumes: nothing.
- Produces: `OKLab.fromColor(Color) -> double[]{L,a,b}`, `OKLab.toColor(double l, double a, double b) -> Color`, `OKLab.toLCH(double[] lab) -> double[]{L,C,H}`, `OKLab.fromLCH(double l, double c, double h) -> double[]{L,a,b}`, `OKLab.toColorInGamut(double l, double c, double hDegrees) -> Color`. `H` is in **degrees**. `toColorInGamut` is the only entry point callers should use for a computed colour.

- [ ] **Step 1: Write the failing test**

Create `core/src/test/java/inetsoft/graph/internal/OKLabTest.java`:

```java
package inetsoft.graph.internal;

import org.junit.jupiter.api.Test;

import java.awt.Color;

import static org.junit.jupiter.api.Assertions.*;

class OKLabTest {
   @Test
   void whiteAndBlackHitKnownEndpoints() {
      assertEquals(1.0, OKLab.fromColor(Color.WHITE)[0], 0.001);
      assertEquals(0.0, OKLab.fromColor(Color.BLACK)[0], 0.001);
   }

   @Test
   void knownBasesMeasureTheirPublishedLightness() {
      // the eight Modern bases, used by the derivation rule in CategoricalColorFrame
      assertEquals(0.650, OKLab.toLCH(OKLab.fromColor(new Color(0x0490FF)))[0], 0.002);
      assertEquals(0.269, OKLab.toLCH(OKLab.fromColor(new Color(0x241C4F)))[0], 0.002);
      assertEquals(0.813, OKLab.toLCH(OKLab.fromColor(new Color(0xFFB020)))[0], 0.002);
      // the dark anchor sits ABOVE 0.40, which is why the dark threshold is 0.50
      assertEquals(0.420, OKLab.toLCH(OKLab.fromColor(new Color(0x49447D)))[0], 0.002);
   }

   @Test
   void roundTripsEverySrgbValueOnTheGreyAxisAndASample() {
      for(int i = 0; i <= 255; i++) {
         Color c = new Color(i, i, i);
         double[] lab = OKLab.fromColor(c);
         assertEquals(c, OKLab.toColor(lab[0], lab[1], lab[2]), "grey " + i);
      }

      int[] samples = { 0x0490FF, 0xFF5A35, 0x241C4F, 0x03D9B3,
                        0x9A2DDC, 0xFFB020, 0xE5197E, 0x8ED604 };

      for(int rgb : samples) {
         Color c = new Color(rgb);
         double[] lab = OKLab.fromColor(c);
         assertEquals(c, OKLab.toColor(lab[0], lab[1], lab[2]), Integer.toHexString(rgb));
      }
   }

   @Test
   void lchRoundTripsThroughLab() {
      Color c = new Color(0x9A2DDC);
      double[] lch = OKLab.toLCH(OKLab.fromColor(c));
      double[] lab = OKLab.fromLCH(lch[0], lch[1], lch[2]);
      assertEquals(c, OKLab.toColor(lab[0], lab[1], lab[2]));
   }

   @Test
   void inGamutRequestPassesThroughUnchanged() {
      // Azure's light companion is comfortably inside sRGB. Derive L/C/H from the base rather
      // than hardcoding - toLCH normalises hue to [0,360), so a hand-copied atan2 value is wrong.
      double[] lch = OKLab.toLCH(OKLab.fromColor(new Color(0x0490FF)));
      assertEquals(251.913, lch[2], 0.01, "hue is normalised to [0,360)");
      assertEquals(new Color(0x97BEEB),
                   OKLab.toColorInGamut(lch[0] + 0.14, lch[1] * 0.40, lch[2]));
   }

   @Test
   void outOfGamutRequestReducesChromaAndHoldsHue() {
      // Amber's light companion target is outside sRGB at this lightness
      double[] lch = OKLab.toLCH(OKLab.fromColor(new Color(0xFFB020)));
      Color mapped = OKLab.toColorInGamut(lch[0] + 0.14, lch[1] * 0.40, lch[2]);
      double[] out = OKLab.toLCH(OKLab.fromColor(mapped));

      // hue held within rounding, chroma reduced, lightness held
      assertEquals(lch[2], out[2], 1.5, "hue must not shift");
      assertTrue(out[1] < lch[1] * 0.40, "chroma must be reduced");
      assertEquals(lch[0] + 0.14, out[0], 0.01, "lightness held");
   }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run from `community/`:
```
./mvnw test -pl core -Dtest=OKLabTest
```
Expected: FAIL — compilation error, `OKLab` does not exist.

- [ ] **Step 3: Write the implementation**

Create `core/src/main/java/inetsoft/graph/internal/OKLab.java`. Copy the AGPL header from `core/src/main/java/inetsoft/graph/internal/HueImageFilter.java`, then:

```java
package inetsoft.graph.internal;

import java.awt.Color;

/**
 * sRGB to OKLab and OKLCH conversion. OKLab is perceptually uniform, so an equal step in L is an
 * equal step in apparent lightness regardless of hue - which neither sRGB channel scaling
 * (ColorFrame.process) nor HSB brightness can provide.
 */
public final class OKLab {
   private OKLab() {
   }

   public static double[] fromColor(Color c) {
      double r = toLinear(c.getRed());
      double g = toLinear(c.getGreen());
      double b = toLinear(c.getBlue());

      double l = 0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b;
      double m = 0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b;
      double s = 0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b;

      double l2 = Math.cbrt(l);
      double m2 = Math.cbrt(m);
      double s2 = Math.cbrt(s);

      return new double[] {
         0.2104542553 * l2 + 0.7936177850 * m2 - 0.0040720468 * s2,
         1.9779984951 * l2 - 2.4285922050 * m2 + 0.4505937099 * s2,
         0.0259040371 * l2 + 0.7827717662 * m2 - 0.8086757660 * s2
      };
   }

   public static Color toColor(double l, double a, double b) {
      double[] rgb = toLinearRGB(l, a, b);
      return new Color(fromLinear(rgb[0]), fromLinear(rgb[1]), fromLinear(rgb[2]));
   }

   public static double[] toLCH(double[] lab) {
      double h = Math.toDegrees(Math.atan2(lab[2], lab[1]));
      return new double[] { lab[0], Math.hypot(lab[1], lab[2]), h < 0 ? h + 360 : h };
   }

   public static double[] fromLCH(double l, double c, double h) {
      double rad = Math.toRadians(h);
      return new double[] { l, c * Math.cos(rad), c * Math.sin(rad) };
   }

   /**
    * The colour at this lightness, chroma and hue, brought into sRGB by reducing chroma only.
    * Clamping channels instead would shift the hue, which the companion rule forbids.
    */
   public static Color toColorInGamut(double l, double c, double h) {
      if(inGamut(l, c, h)) {
         return toColor(fromLCH(l, c, h)[0], fromLCH(l, c, h)[1], fromLCH(l, c, h)[2]);
      }

      double lo = 0;
      double hi = c;

      for(int i = 0; i < 60; i++) {
         double mid = (lo + hi) / 2;

         if(inGamut(l, mid, h)) {
            lo = mid;
         }
         else {
            hi = mid;
         }
      }

      double[] lab = fromLCH(l, lo, h);
      return toColor(lab[0], lab[1], lab[2]);
   }

   private static boolean inGamut(double l, double c, double h) {
      double[] lab = fromLCH(l, c, h);
      double[] rgb = toLinearRGB(lab[0], lab[1], lab[2]);

      for(double v : rgb) {
         if(v < -1e-6 || v > 1 + 1e-6) {
            return false;
         }
      }

      return true;
   }

   private static double[] toLinearRGB(double l, double a, double b) {
      double l2 = l + 0.3963377774 * a + 0.2158037573 * b;
      double m2 = l - 0.1055613458 * a - 0.0638541728 * b;
      double s2 = l - 0.0894841775 * a - 1.2914855480 * b;

      double l3 = l2 * l2 * l2;
      double m3 = m2 * m2 * m2;
      double s3 = s2 * s2 * s2;

      return new double[] {
         4.0767416621 * l3 - 3.3077115913 * m3 + 0.2309699292 * s3,
         -1.2684380046 * l3 + 2.6097574011 * m3 - 0.3413193965 * s3,
         -0.0041960863 * l3 - 0.7034186147 * m3 + 1.7076147010 * s3
      };
   }

   private static double toLinear(int v) {
      double c = v / 255.0;
      return c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
   }

   private static int fromLinear(double c) {
      double v = c <= 0.0031308 ? 12.92 * c : 1.055 * Math.pow(c, 1 / 2.4) - 0.055;
      return Math.max(0, Math.min(255, (int) Math.round(v * 255)));
   }
}
```

- [ ] **Step 4: Run test to verify it passes**

```
./mvnw test -pl core -Dtest=OKLabTest
```
Expected: PASS, 6 tests.

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/inetsoft/graph/internal/OKLab.java core/src/test/java/inetsoft/graph/internal/OKLabTest.java
```
```bash
git commit -m "Add an sRGB/OKLab conversion utility"
```

---

### Task 2: The companion derivation rule

**Files:**
- Modify: `core/src/main/java/inetsoft/graph/aesthetic/CategoricalColorFrame.java` (add a method near `getDefaultColor(int)` at `:484`)
- Test: `core/src/test/java/inetsoft/graph/aesthetic/CategoricalColorFrameCompanionTest.java`

**Interfaces:**
- Consumes: `OKLab.fromColor`, `OKLab.toLCH`, `OKLab.toColorInGamut` from Task 1.
- Produces: `CategoricalColorFrame.getCompanionColor(int index, boolean dark) -> Color`. Returns `null` when the index has no base colour. This is the **mechanism**; Task 5 layers the authored override on top.

- [ ] **Step 1: Write the failing test**

Create `core/src/test/java/inetsoft/graph/aesthetic/CategoricalColorFrameCompanionTest.java`:

```java
package inetsoft.graph.aesthetic;

import inetsoft.graph.internal.OKLab;
import org.junit.jupiter.api.Test;

import java.awt.Color;

import static org.junit.jupiter.api.Assertions.*;

class CategoricalColorFrameCompanionTest {
   @Test
   void lightNonAnchorLiftsLightnessAndCutsChroma() {
      CategoricalColorFrame frame = frameWith(new Color(0x0490FF));
      assertEquals(new Color(0x97BEEB), frame.getCompanionColor(0, false));
   }

   @Test
   void lightAnchorIsSentToTheCanvasEnd() {
      // Ink measures L 0.269, below the 0.40 light threshold
      CategoricalColorFrame frame = frameWith(new Color(0x241C4F));
      assertEquals(new Color(0xCCCCE9), frame.getCompanionColor(0, false));
   }

   @Test
   void darkNonAnchorDeepensAndHoldsChroma() {
      CategoricalColorFrame frame = frameWith(new Color(0x4FA5FF));
      assertEquals(new Color(0x00569C), frame.getCompanionColor(0, true));
   }

   @Test
   void darkAnchorLiftsBecauseItCannotDeepen() {
      // the dark anchor measures L 0.420 - ABOVE the light threshold of 0.40, which is why
      // the dark threshold is 0.50. A flat 0.40 would yield #0E0033 here.
      CategoricalColorFrame frame = frameWith(new Color(0x49447D));
      assertEquals(new Color(0x8988AB), frame.getCompanionColor(0, true));
   }

   @Test
   void hueIsNeverModified() {
      int[] bases = { 0x0490FF, 0xFF5A35, 0x241C4F, 0x03D9B3,
                      0x9A2DDC, 0xFFB020, 0xE5197E, 0x8ED604 };

      for(int rgb : bases) {
         CategoricalColorFrame frame = frameWith(new Color(rgb));
         double baseHue = OKLab.toLCH(OKLab.fromColor(new Color(rgb)))[2];

         for(boolean dark : new boolean[] { false, true }) {
            Color companion = frame.getCompanionColor(0, dark);
            double hue = OKLab.toLCH(OKLab.fromColor(companion))[2];
            assertEquals(baseHue, hue, 2.0,
                         Integer.toHexString(rgb) + (dark ? " dark" : " light"));
         }
      }
   }

   @Test
   void anAbsentBaseHasNoCompanion() {
      CategoricalColorFrame frame = new CategoricalColorFrame();
      assertNull(frame.getCompanionColor(-1, false));
      assertNull(frame.getCompanionColor(9999, false));
   }

   private CategoricalColorFrame frameWith(Color base) {
      CategoricalColorFrame frame = new CategoricalColorFrame();
      frame.setDefaultColor(0, base);
      return frame;
   }
}
```

- [ ] **Step 2: Run test to verify it fails**

```
./mvnw test -pl core -Dtest=CategoricalColorFrameCompanionTest
```
Expected: FAIL — `getCompanionColor` is undefined.

- [ ] **Step 3: Write the implementation**

In `CategoricalColorFrame.java`, add the import `inetsoft.graph.internal.OKLab` and insert after `getDefaultColor(int index)` (`:484-486`):

```java
   /**
    * The soft companion of the base at this index: the same hue, receding toward the surface.
    * Light lifts toward the canvas; dark deepens instead, and holds chroma because low chroma and
    * low lightness disappear together on a dark surface. The darkest member of a set (the anchor)
    * is the exception in both modes - it has nowhere to deepen to, so it is sent to a fixed
    * lightness. Returns null when the index carries no base colour.
    */
   public Color getCompanionColor(int index, boolean dark) {
      if(index < 0 || defaultColors == null || index >= defaultColors.size()) {
         return null;
      }

      Color base = defaultColors.get(index);

      if(base == null) {
         return null;
      }

      double[] lch = OKLab.toLCH(OKLab.fromColor(base));
      double l = lch[0];
      double c = lch[1];
      double h = lch[2];
      // the anchor sits at a different lightness in each mode, so the threshold does too
      boolean anchor = dark ? l < DARK_ANCHOR_MAX_L : l < LIGHT_ANCHOR_MAX_L;

      if(dark) {
         return anchor ? OKLab.toColorInGamut(0.64, c * 0.55, h)
            : OKLab.toColorInGamut(l - 0.26, c * 1.03, h);
      }

      return anchor ? OKLab.toColorInGamut(0.855, c * 0.45, h)
         : OKLab.toColorInGamut(l + 0.14, c * 0.40, h);
   }
```

Add beside the other constants at the bottom of the class:

```java
   private static final double LIGHT_ANCHOR_MAX_L = 0.40;
   private static final double DARK_ANCHOR_MAX_L = 0.50;
```

- [ ] **Step 4: Run test to verify it passes**

```
./mvnw test -pl core -Dtest=CategoricalColorFrameCompanionTest
```
Expected: PASS, 6 tests.

- [ ] **Step 5: Run the existing frame tests for regressions**

```
./mvnw test -pl core -Dtest='CategoricalColor*'
```
Expected: PASS — the new method is additive, nothing existing should move.

- [ ] **Step 6: Commit**

```bash
git add core/src/main/java/inetsoft/graph/aesthetic/CategoricalColorFrame.java core/src/test/java/inetsoft/graph/aesthetic/CategoricalColorFrameCompanionTest.java
```
```bash
git commit -m "Derive a palette slot's soft companion in OKLab"
```

---

### Task 3: The authored companion palettes

**Files:**
- Modify: `core/src/main/resources/inetsoft/util/css/defaults.css` (append after the `Modern Dark` block)
- Test: `core/src/test/java/inetsoft/uql/viewsheet/graph/aesthetic/ColorPalettesModernTest.java`

**Interfaces:**
- Consumes: nothing at compile time. The hexes are the derivation rule's own output — see Task 2.
- Produces: two new `ChartPalette` names, `Modern-soft` and `Modern Dark-soft`, 8 slots each, 1-based.

- [ ] **Step 1: Write the failing test**

Append to `ColorPalettesModernTest.java`:

```java
   @Test
   void companionPalettesAreDeclaredWithEightColors() {
      for(String name : new String[] { "Modern-soft", "Modern Dark-soft" }) {
         CategoricalColorFrame frame = ColorPalettes.getPalette(name);
         assertNotNull(frame, name + " must be declared in defaults.css");
         assertEquals(8, frame.getColorCount(), name + " slot count");

         for(int i = 0; i < 8; i++) {
            assertNotNull(frame.getDefaultColor(i), name + " index " + (i + 1));
         }
      }
   }

   @Test
   void companionHeadsMatchTheSpec() {
      CategoricalColorFrame light = ColorPalettes.getPalette("Modern-soft");
      assertEquals(new Color(0x97BEEB), light.getDefaultColor(0));
      assertEquals(new Color(0xD8F7BA), light.getDefaultColor(7));

      CategoricalColorFrame dark = ColorPalettes.getPalette("Modern Dark-soft");
      assertEquals(new Color(0x00569C), dark.getDefaultColor(0));
      assertEquals(new Color(0x5F9100), dark.getDefaultColor(7));
   }
```

- [ ] **Step 2: Run test to verify it fails**

```
./mvnw test -pl core -Dtest=ColorPalettesModernTest
```
Expected: FAIL — `ColorPalettes.getPalette("Modern-soft")` returns null.

- [ ] **Step 3: Add the palette declarations**

In `defaults.css`, after the last `ChartPalette[name='Modern Dark'][index='40']` rule, append. Match the existing formatting exactly — quoted attribute values, lowercase hex, one `color:` per rule:

```css
/* Companions of the Modern bases. Not user-selectable: reached only through
   ColorPalettes.getCompanionPalette() and filtered out of getPaletteNames(). */
ChartPalette[name='Modern-soft'][index='1'] {
   color: #97beeb;
}
ChartPalette[name='Modern-soft'][index='2'] {
   color: #f6b2a2;
}
ChartPalette[name='Modern-soft'][index='3'] {
   color: #cccce9;
}
ChartPalette[name='Modern-soft'][index='4'] {
   color: #bff6e5;
}
ChartPalette[name='Modern-soft'][index='5'] {
   color: #ad8bcb;
}
ChartPalette[name='Modern-soft'][index='6'] {
   color: #ffe7c7;
}
ChartPalette[name='Modern-soft'][index='7'] {
   color: #de93ab;
}
ChartPalette[name='Modern-soft'][index='8'] {
   color: #d8f7ba;
}
ChartPalette[name='Modern Dark-soft'][index='1'] {
   color: #00569c;
}
ChartPalette[name='Modern Dark-soft'][index='2'] {
   color: #a62e11;
}
ChartPalette[name='Modern Dark-soft'][index='3'] {
   color: #8988ab;
}
ChartPalette[name='Modern Dark-soft'][index='4'] {
   color: #009378;
}
ChartPalette[name='Modern Dark-soft'][index='5'] {
   color: #550080;
}
ChartPalette[name='Modern Dark-soft'][index='6'] {
   color: #ab792a;
}
ChartPalette[name='Modern Dark-soft'][index='7'] {
   color: #870047;
}
ChartPalette[name='Modern Dark-soft'][index='8'] {
   color: #5f9100;
}
```

- [ ] **Step 4: Run test to verify it passes**

```
./mvnw test -pl core -Dtest=ColorPalettesModernTest
```
Expected: PASS — including the pre-existing `defaultPaletteIsUnchanged` and `modernDeclaresFortyNonNullColors` drift guards.

- [ ] **Step 5: Commit**

```bash
git add core/src/main/resources/inetsoft/util/css/defaults.css core/src/test/java/inetsoft/uql/viewsheet/graph/aesthetic/ColorPalettesModernTest.java
```
```bash
git commit -m "Declare the Modern and Modern Dark companion palettes"
```

---

### Task 4: Keep companions out of the picker

**Files:**
- Modify: `core/src/main/java/inetsoft/uql/viewsheet/graph/aesthetic/ColorPalettes.java` (`getPaletteNames()` at `:44-49` and `:54-59`)
- Test: `core/src/test/java/inetsoft/uql/viewsheet/graph/aesthetic/ColorPalettesModernTest.java`

**Interfaces:**
- Consumes: the palettes declared in Task 3.
- Produces: `ColorPalettes.getCompanionPalette(String baseName) -> CategoricalColorFrame` (null when undeclared), and `ColorPalettes.COMPANION_SUFFIX` (`"-soft"`). `getPaletteNames()` no longer returns any name ending in the suffix.

- [ ] **Step 1: Write the failing test**

Append to `ColorPalettesModernTest.java`:

```java
   @Test
   void companionNamesAreNotOfferedInThePicker() {
      assertFalse(ColorPalettes.getPaletteNames().contains("Modern-soft"));
      assertFalse(ColorPalettes.getPaletteNames().contains("Modern Dark-soft"));
      // the sets a picker must still offer
      assertTrue(ColorPalettes.getPaletteNames().contains("Modern"));
      assertTrue(ColorPalettes.getPaletteNames().contains("Modern Dark"));
      assertTrue(ColorPalettes.getPaletteNames().contains("Default"));
   }

   @Test
   void companionsAreStillResolvableByName() {
      // filtering the picker list must never filter resolution
      assertNotNull(ColorPalettes.getPalette("Modern-soft"));
      assertNotNull(ColorPalettes.getCompanionPalette("Modern"));
      assertNotNull(ColorPalettes.getCompanionPalette("Modern Dark"));
      assertNull(ColorPalettes.getCompanionPalette("Default"));
   }
```

- [ ] **Step 2: Run test to verify it fails**

```
./mvnw test -pl core -Dtest=ColorPalettesModernTest
```
Expected: FAIL — `getCompanionPalette` undefined, and `getPaletteNames()` still contains the companion names.

- [ ] **Step 3: Write the implementation**

In `ColorPalettes.java`, replace the body of both `getPaletteNames()` overloads so the keySet is filtered, and add the accessor. The existing `:44-49` becomes:

```java
   public static Collection<String> getPaletteNames() {
      synchronized(ColorPalettes.class) {
         singleton.loadPalettes();
         return selectable(singleton.paletteMap
            .get(OrganizationManager.getInstance().getCurrentOrgID()).keySet());
      }
   }
```

and `:54-59`:

```java
   public static Collection<String> getPaletteNames(String cssLocation) {
      synchronized(ColorPalettes.class) {
         singleton.loadPalettes(cssLocation, true);
         return selectable(singleton.paletteMap
            .get(OrganizationManager.getInstance().getCurrentOrgID()).keySet());
      }
   }
```

Add beside them:

```java
   /**
    * The companion palette of a named set, or null when none is declared. Companions are reached
    * only through here - they are filtered out of getPaletteNames() so they cannot be picked as
    * a palette in their own right.
    */
   public static CategoricalColorFrame getCompanionPalette(String name) {
      return name == null || name.endsWith(COMPANION_SUFFIX)
         ? null : getPalette(name + COMPANION_SUFFIX);
   }

   // names carrying the reserved suffix are companions, not selectable palettes. Enforced here
   // rather than at each caller so the reservation lives in one place.
   private static Collection<String> selectable(Collection<String> names) {
      return names.stream()
         .filter(name -> !name.endsWith(COMPANION_SUFFIX))
         .collect(Collectors.toCollection(LinkedHashSet::new));
   }

   public static final String COMPANION_SUFFIX = "-soft";
```

Add the imports `java.util.LinkedHashSet` and `java.util.stream.Collectors`. `LinkedHashSet` preserves CSS declaration order, which the picker depends on — `Default` must stay first.

- [ ] **Step 4: Run test to verify it passes**

```
./mvnw test -pl core -Dtest=ColorPalettesModernTest
```
Expected: PASS.

- [ ] **Step 5: Run the picker-facing tests**

```
./mvnw test -pl core -Dtest='ChartColorPaletteControllerTest,VSChartPaletteCssOverrideTest'
```
Expected: PASS. `ChartColorPaletteControllerTest` exercises the endpoint that consumes `getPaletteNames()`.

- [ ] **Step 6: Commit**

```bash
git add core/src/main/java/inetsoft/uql/viewsheet/graph/aesthetic/ColorPalettes.java core/src/test/java/inetsoft/uql/viewsheet/graph/aesthetic/ColorPalettesModernTest.java
```
```bash
git commit -m "Reserve the companion suffix and keep companions out of the picker"
```

---

### Task 5: Authored-over-derived resolution

**Files:**
- Modify: `core/src/main/java/inetsoft/uql/viewsheet/internal/VSChartPaletteDefaults.java` (add beside `hiddenPaletteNames` at `:98`)
- Test: `core/src/test/java/inetsoft/uql/viewsheet/internal/VSChartPaletteDefaultsTest.java`

**Interfaces:**
- Consumes: `CategoricalColorFrame.getCompanionColor(int, boolean)` (Task 2), `ColorPalettes.getCompanionPalette(String)` (Task 4).
- Produces: `VSChartPaletteDefaults.companionColor(CategoricalColorFrame base, String paletteName, int index, boolean dark) -> Color`. `paletteName` may be null (an unnamed frame — user colours, a deployment frame), in which case resolution is derivation-only.

- [ ] **Step 1: Write the failing test**

Append to `VSChartPaletteDefaultsTest.java`:

```java
   @Test
   void authoredCompanionWinsOverDerivation() {
      CategoricalColorFrame modern = ColorPalettes.getPalette("Modern");
      // slot 6 is the one authored value the rule does not reproduce - a deliberate hand-tune,
      // so it proves the authored layer is consulted first
      assertEquals(new Color(0xFFE7C7),
                   VSChartPaletteDefaults.companionColor(modern, "Modern", 5, false));
   }

   @Test
   void anUnnamedFrameStillGetsDerivedCompanions() {
      CategoricalColorFrame frame = new CategoricalColorFrame();
      frame.setDefaultColor(0, new Color(0x0490FF));
      assertEquals(new Color(0x97BEEB),
                   VSChartPaletteDefaults.companionColor(frame, null, 0, false));
   }

   @Test
   void anIndexBeyondTheAuthoredPaletteFallsThroughToDerivation() {
      CategoricalColorFrame modern = ColorPalettes.getPalette("Modern");
      // Modern-soft declares 8; Modern declares 40, so slot 9 must derive
      assertNotNull(VSChartPaletteDefaults.companionColor(modern, "Modern", 8, false));
   }

   @Test
   void authoredCompanionsAgreeWithTheRuleExceptTheHandTunedSlot() {
      // the drift guard: authored hexes ARE the rule's output, so the two cannot silently
      // diverge. Light slot 6 (Amber) is exempt - its rule target is outside sRGB and the
      // designer lowered lightness as well as chroma. An exemption list of one is the point.
      assertCompanionsAgree("Modern", false, 5);
      assertCompanionsAgree("Modern Dark", true, -1);
   }

   private void assertCompanionsAgree(String name, boolean dark, int exemptIndex) {
      CategoricalColorFrame base = ColorPalettes.getPalette(name);
      CategoricalColorFrame authored = ColorPalettes.getCompanionPalette(name);

      for(int i = 0; i < authored.getColorCount(); i++) {
         if(i == exemptIndex) {
            continue;
         }

         assertEquals(base.getCompanionColor(i, dark), authored.getDefaultColor(i),
                      name + " index " + (i + 1) + " must equal the rule's output");
      }
   }
```

- [ ] **Step 2: Run test to verify it fails**

```
./mvnw test -pl core -Dtest=VSChartPaletteDefaultsTest
```
Expected: FAIL — `companionColor` undefined.

- [ ] **Step 3: Write the implementation**

In `VSChartPaletteDefaults.java`, add after `hiddenPaletteNames`:

```java
   /**
    * The companion of a slot: the authored palette when one declares this index, otherwise the
    * rule applied to the base. A null or absent authored entry falls through to derivation - the
    * loader sizes a palette to its highest declared index, so a partial override leaves holes
    * that must not read as absent. paletteName may be null for a frame with no registered name.
    */
   public static Color companionColor(CategoricalColorFrame base, String paletteName, int index,
                                      boolean dark)
   {
      if(base == null || index < 0) {
         return null;
      }

      if(paletteName != null) {
         CategoricalColorFrame authored = ColorPalettes.getCompanionPalette(paletteName);

         if(authored != null && index < authored.getColorCount()) {
            Color color = authored.getDefaultColor(index);

            if(color != null) {
               return color;
            }
         }
      }

      return base.getCompanionColor(index, dark);
   }
```

- [ ] **Step 4: Run test to verify it passes**

```
./mvnw test -pl core -Dtest=VSChartPaletteDefaultsTest
```
Expected: PASS. If `authoredCompanionsAgreeWithTheRuleExceptTheHandTunedSlot` fails at any index other than light 5, either the CSS in Task 3 or the rule in Task 2 is wrong — do not widen the exemption to make it green.

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/inetsoft/uql/viewsheet/internal/VSChartPaletteDefaults.java core/src/test/java/inetsoft/uql/viewsheet/internal/VSChartPaletteDefaultsTest.java
```
```bash
git commit -m "Resolve a companion from the authored palette before the rule"
```

---

### Task 6: Record whether the author set the band fill

**Files:**
- Modify: `core/src/main/java/inetsoft/report/composition/graph/GraphTarget.java` (field block at `:780-794`, `writeXML`/`parseXML` around `:438` and `:541`, `clone` at `:96`)
- Modify: `core/src/main/java/inetsoft/web/viewsheet/service/ChartPropertyService.java` (`:805`, `:818`)
- Test: `core/src/test/java/inetsoft/report/composition/graph/GraphTargetBandFillTest.java`

**Interfaces:**
- Consumes: nothing from earlier tasks.
- Produces: `GraphTarget.isBandFillUserSet() -> boolean` and `GraphTarget.setBandFillUserSet(boolean)`. A newly constructed target is `false`; a target parsed from XML with no such attribute is `true`.

- [ ] **Step 1: Write the failing test**

Create `core/src/test/java/inetsoft/report/composition/graph/GraphTargetBandFillTest.java`:

```java
package inetsoft.report.composition.graph;

import inetsoft.util.Tool;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;

import java.io.*;

import static org.junit.jupiter.api.Assertions.*;

class GraphTargetBandFillTest {
   @Test
   void aNewTargetHasNotBeenAuthored() {
      assertFalse(new GraphTarget().isBandFillUserSet());
   }

   @Test
   void theFlagSurvivesARoundTrip() throws Exception {
      GraphTarget target = new GraphTarget();
      target.setBandFillUserSet(true);
      assertTrue(reparse(target).isBandFillUserSet());

      GraphTarget untouched = new GraphTarget();
      assertFalse(reparse(untouched).isBandFillUserSet());
   }

   @Test
   void aLegacyTargetWithNoAttributeCountsAsAuthored() throws Exception {
      // existing dashboards must stay pixel-identical: with no attribute we cannot tell an
      // author's choice from the built-in greens, so we assume the author's.
      // the root element is <graphTarget> - see GraphTarget.writeXML:373
      String xml = "<graphTarget lineStyle=\"0\" scope=\"subchart\" alphaValue=\"100\">" +
         "<strategy className=\"inetsoft.graph.guide.form.DynamicLineStrategy\"></strategy>" +
         "</graphTarget>";
      GraphTarget target = new GraphTarget();
      target.parseXML(parse(xml));
      assertTrue(target.isBandFillUserSet());
   }

   @Test
   void cloneCarriesTheFlag() {
      GraphTarget target = new GraphTarget();
      target.setBandFillUserSet(true);
      assertTrue(((GraphTarget) target.clone()).isBandFillUserSet());
   }

   private GraphTarget reparse(GraphTarget target) throws Exception {
      StringWriter buf = new StringWriter();
      PrintWriter writer = new PrintWriter(buf);
      target.writeXML(writer);
      writer.flush();

      GraphTarget parsed = new GraphTarget();
      parsed.parseXML(parse(buf.toString()));
      return parsed;
   }

   private org.w3c.dom.Element parse(String xml) throws Exception {
      Document doc = Tool.parseXML(new ByteArrayInputStream(xml.getBytes("UTF-8")));
      return doc.getDocumentElement();
   }
}
```

- [ ] **Step 2: Run test to verify it fails**

```
./mvnw test -pl core -Dtest=GraphTargetBandFillTest
```
Expected: FAIL — `isBandFillUserSet` undefined.

- [ ] **Step 3: Add the flag to GraphTarget**

Add the field beside `bandFill` (`:794`):

```java
   private boolean bandFillUserSet = false;
```

Add accessors beside `getBandFill()` (`:285`):

```java
   /**
    * Whether the band fill colours were set by an author rather than left at the built-in
    * default. The render path only supplies a companion fill when they were not.
    */
   public boolean isBandFillUserSet() {
      return bandFillUserSet;
   }

   public void setBandFillUserSet(boolean bandFillUserSet) {
      this.bandFillUserSet = bandFillUserSet;
   }
```

This is an **attribute**, not a child element, so it goes in `writeAttributes` (`:384`) and `parseAttributes` (`:467`) — not beside `bandFill.writeXML` in `writeContents`. Add to the end of `writeAttributes`:

```java
      writer.print(" bandFillUserSet=\"" + bandFillUserSet + "\"");
```

Add to `parseAttributes`, following the `Tool.getAttribute` idiom already used there at `:470`:

```java
      // absent means a target saved before companions existed: treat it as authored so an
      // existing dashboard keeps the colours it already renders
      String userSet = Tool.getAttribute(elem, "bandFillUserSet");
      bandFillUserSet = userSet == null || "true".equals(userSet);
```

In `clone()` (`:96`), after the existing field copies:

```java
      newTarget.bandFillUserSet = bandFillUserSet;
```

- [ ] **Step 4: Run test to verify it passes**

```
./mvnw test -pl core -Dtest=GraphTargetBandFillTest
```
Expected: PASS, 4 tests. If `theFlagSurvivesARoundTrip` fails on the `false` case, the attribute is being written but parsed with the legacy default — confirm the attribute is actually present in the written XML before changing the parse default.

- [ ] **Step 5: Set the flag where the author picks a colour**

In `ChartPropertyService.java`, at the band colour write (`:805`):

```java
         target.getBandFill().setUserColor(0, bandColor);
         target.setBandFillUserSet(true);
```

and at the frame update (`:818`):

```java
      updateCategoricalColor(targetInfo.getBandFill(), target.getBandFill());
      target.setBandFillUserSet(true);
```

- [ ] **Step 6: Run the chart property tests**

```
./mvnw test -pl core -Dtest='ChartProperty*'
```
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add core/src/main/java/inetsoft/report/composition/graph/GraphTarget.java core/src/main/java/inetsoft/web/viewsheet/service/ChartPropertyService.java core/src/test/java/inetsoft/report/composition/graph/GraphTargetBandFillTest.java
```
```bash
git commit -m "Record whether a target's band fill was set by an author"
```

---

### Task 7: Draw the band in its measure's companion

**The band count is not known where the target is wired.** `bandBoundaries` is computed inside
`TargetForm.createForms` from `strategy.calculateBoundaries(data)` (`TargetForm.java:78`) — at
render, after `addTarget` has returned. So the stepped frame must be built inside `TargetForm`,
which is why `addTarget` passes a single base colour rather than a finished frame. Reading a count
off `gtf.getBandColorFrame()` instead would yield 40, the `CategoricalColorFrame` constructor's
default palette length, and every band would come out the same colour.

Band arithmetic, from the loop at `TargetForm.java:176-195`: bands are created at `i = 1 …
length-1` and coloured with index `i - 1`, so the count is `bandBoundaries.length - 1`.

**Files:**
- Modify: `core/src/main/java/inetsoft/graph/aesthetic/CategoricalColorFrame.java` (static factory beside `getCompanionColor` from Task 2)
- Modify: `core/src/main/java/inetsoft/graph/guide/form/TargetForm.java` (fields beside `bandColors` at `:710`, band loop at `:183`)
- Modify: `core/src/main/java/inetsoft/report/composition/graph/GraphGenerator.java` (`addTarget` at `:5729`, wiring after `:5757`)
- Test: `core/src/test/java/inetsoft/graph/aesthetic/CompanionBandFrameTest.java`

**Interfaces:**
- Consumes: `CategoricalColorFrame.getCompanionColor` (Task 2), `GraphTarget.isBandFillUserSet` (Task 6), `OKLab` (Task 1).
- Produces: `CategoricalColorFrame.companionBands(Color base, Color companion, int bands) -> CategoricalColorFrame` (null when either colour is null), `TargetForm.setCompanionBase(Color)`, `TargetForm.setCompanionFill(Color)`. The factory lives on `CategoricalColorFrame` so `inetsoft.graph.guide.form` can reach it without depending on `inetsoft.report.composition.graph`. It does **pure stepping** — the caller resolves the companion through `VSChartPaletteDefaults.companionColor` (Task 5) so an authored override wins over the rule.

- [ ] **Step 1: Write the failing test**

Create `core/src/test/java/inetsoft/graph/aesthetic/CompanionBandFrameTest.java`. This tests the band-frame construction in isolation — the surrounding generator needs a full runtime viewsheet, which belongs in the manual pass, not a unit test:

```java
package inetsoft.graph.aesthetic;

import inetsoft.graph.internal.OKLab;
import org.junit.jupiter.api.Test;

import java.awt.Color;

import static org.junit.jupiter.api.Assertions.*;

class CompanionBandFrameTest {
   @Test
   void aSingleBandIsExactlyTheResolvedCompanion() {
      CategoricalColorFrame frame = CategoricalColorFrame.companionBands(
         new Color(0x0490FF), new Color(0x97BEEB), 1);
      assertEquals(new Color(0x97BEEB), frame.getDefaultColor(0));
   }

   @Test
   void anAuthoredCompanionIsUsedVerbatimRatherThanRederived() {
      // light slot 6 (Amber) is the authored hand-tune the rule does not reproduce: passing it
      // in must yield it back, which is what proves the override reaches the band
      CategoricalColorFrame frame = CategoricalColorFrame.companionBands(
         new Color(0xFFB020), new Color(0xFFE7C7), 1);
      assertEquals(new Color(0xFFE7C7), frame.getDefaultColor(0));
   }

   @Test
   void multipleBandsWalkLightnessTowardTheBaseAtOneHue() {
      Color base = new Color(0x0490FF);
      CategoricalColorFrame frame =
         CategoricalColorFrame.companionBands(base, new Color(0x97BEEB), 3);

      double baseHue = OKLab.toLCH(OKLab.fromColor(base))[2];
      double baseL = OKLab.toLCH(OKLab.fromColor(base))[0];
      double previousL = Double.MAX_VALUE;

      for(int i = 0; i < 3; i++) {
         double[] lch = OKLab.toLCH(OKLab.fromColor(frame.getDefaultColor(i)));
         assertEquals(baseHue, lch[2], 2.0, "band " + i + " hue");
         assertTrue(lch[0] < previousL, "band " + i + " must be darker than the last");
         assertTrue(lch[0] > baseL, "band " + i + " must not reach the base");
         previousL = lch[0];
      }
   }

   @Test
   void theFirstBandIsTheCompanionRegardlessOfCount() {
      for(int n : new int[] { 1, 2, 5 }) {
         CategoricalColorFrame frame = CategoricalColorFrame.companionBands(
            new Color(0x0490FF), new Color(0x97BEEB), n);
         assertEquals(new Color(0x97BEEB), frame.getDefaultColor(0), "count " + n);
      }
   }

   @Test
   void darkBandsDeepenFromTheirOwnCompanion() {
      CategoricalColorFrame frame = CategoricalColorFrame.companionBands(
         new Color(0x4FA5FF), new Color(0x00569C), 1);
      assertEquals(new Color(0x00569C), frame.getDefaultColor(0));
   }
}
```

- [ ] **Step 2: Run test to verify it fails**

```
./mvnw test -pl core -Dtest=CompanionBandFrameTest
```
Expected: FAIL — `CategoricalColorFrame.companionBands` undefined.

- [ ] **Step 3: Add the band-frame builder**

In `CategoricalColorFrame.java`, add as a static factory beside `getCompanionColor` from Task 2:

```java
   /**
    * A band fill frame stepping from a resolved companion toward its base. Band 0 is the companion
    * itself; further bands walk lightness toward the base without reaching it, so depth reads as
    * depth and no band collides with the series colour. The companion is resolved by the caller
    * (VSChartPaletteDefaults.companionColor) so an authored override wins over the rule.
    */
   public static CategoricalColorFrame companionBands(Color base, Color companion, int bands) {
      if(base == null || companion == null) {
         return null;
      }

      double[] companionLCH = OKLab.toLCH(OKLab.fromColor(companion));
      double baseL = OKLab.toLCH(OKLab.fromColor(base))[0];
      double span = companionLCH[0] - baseL;
      int count = Math.max(1, bands);
      CategoricalColorFrame frame = new CategoricalColorFrame();

      for(int i = 0; i < count; i++) {
         double l = companionLCH[0] - i * span / count;
         frame.setDefaultColor(i, OKLab.toColorInGamut(l, companionLCH[1], companionLCH[2]));
      }

      return frame;
   }
```

- [ ] **Step 4: Run test to verify it passes**

```
./mvnw test -pl core -Dtest=CompanionBandFrameTest
```
Expected: PASS, 4 tests.

- [ ] **Step 5: Resolve the measure's series colour**

Add as a private method beside `getColorFrame` in `GraphGenerator.java`:

```java
   /**
    * The colour a target's measure renders in, or null when it does not resolve to one - colour
    * bound to a dimension means the bars are many colours and one target spans all of them, so
    * there is no single measure colour to companion. Null is what keeps the classic band fill.
    */
   private Color targetSeriesColor(GraphTarget target) {
      String field = target.getField();

      if(field == null) {
         return null;
      }

      // a chart coloured by measure carries one frame per measure
      VisualFrame measureFrame = cvisitor.getMeasureFrame(field);

      if(measureFrame instanceof StaticColorFrame) {
         return ((StaticColorFrame) measureFrame).getColor();
      }

      AestheticRef colorField = info.getColorField();
      boolean unbound = colorField == null || colorField.getRTDataRef() == null;

      if(!unbound && !colorField.isMeasure()) {
         return null;
      }

      ColorFrame color = (ColorFrame) cvisitor.getFrame();

      if(color == null) {
         color = info.getColorFrame();
      }

      return color instanceof CategoricalColorFrame
         ? ((CategoricalColorFrame) color).getDefaultColor(0) : null;
   }
```

- [ ] **Step 6: Let TargetForm build the stepped frame**

In `TargetForm.java`, add beside `bandColors` (`:710`):

```java
   private Color companionBase = null;
   private Color companionFill = null;
```

and accessors beside `setBandColorFrame` (`:521`):

```java
   /**
    * The series colour a companion band fill is derived from. When set, it replaces the band
    * colour frame at render - the band count is only known here, after the strategy has
    * calculated its boundaries.
    */
   public void setCompanionBase(Color companionBase) {
      this.companionBase = companionBase;
   }

   /**
    * The resolved companion of the base - authored where one is declared, derived otherwise. The
    * caller resolves it so the authored override is honoured; this class only steps it per band.
    */
   public void setCompanionFill(Color companionFill) {
      this.companionFill = companionFill;
   }
```

In `generateSubForms`, immediately before the band loop at `:176`:

```java
      // bands run from i = 1 to length - 1, so the count is one less than the boundary count
      CategoricalColorFrame bandFrame = companionBase == null || companionFill == null ? null
         : CategoricalColorFrame.companionBands(
            companionBase, companionFill, Math.max(1, bandBoundaries.length - 1));
```

and change the band colour lookup at `:183` from:

```java
            Color bandColor = bandColors.getColor(i - 1);
```

to:

```java
            Color bandColor = bandFrame != null ? bandFrame.getDefaultColor(i - 1)
               : bandColors.getColor(i - 1);
```

- [ ] **Step 7: Wire it into addTarget**

In `GraphGenerator.addTarget`, immediately after `target.initializeForm(gtf, data);` (`:5757`):

```java
      if(vizContext.modern && !target.isBandFillUserSet()) {
         Color seriesColor = targetSeriesColor(target);

         if(seriesColor != null) {
            gtf.setCompanionBase(seriesColor);
            gtf.setCompanionFill(resolveCompanion(seriesColor));
         }
      }
```

and add the resolver beside `targetSeriesColor`:

```java
   /**
    * The companion of a series colour, resolved through the authored palette where the colour is
    * a slot of the chart's active palette and by rule otherwise. Going through
    * VSChartPaletteDefaults is what lets an authored Modern-soft entry win over the rule.
    */
   private Color resolveCompanion(Color seriesColor) {
      CategoricalColorFrame active = new CategoricalColorFrame();
      active.setDefaultColors(VSChartPaletteDefaults.activePalette(vizContext));
      String paletteName = vizContext.dark ? "Modern Dark" : "Modern";

      for(int i = 0; i < active.getColorCount(); i++) {
         if(seriesColor.equals(active.getDefaultColor(i))) {
            return VSChartPaletteDefaults.companionColor(active, paletteName, i, vizContext.dark);
         }
      }

      // not a palette slot (a user-pinned or static colour): derive, with no authored entry to
      // consult. Passing a null name is the documented derivation-only path.
      CategoricalColorFrame holder = new CategoricalColorFrame();
      holder.setDefaultColor(0, seriesColor);
      return VSChartPaletteDefaults.companionColor(holder, null, 0, vizContext.dark);
   }
```

Only the `TargetForm` is touched — never `target.getBandFill()` — so nothing persisted is written and the stored frame's colour tiers stay irrelevant.

**Why the resolver exists.** Without it the band would call the derivation rule directly and bypass the authored `Modern-soft` palette entirely, leaving Tasks 3-5 with no consumer and contradicting the spec's decision 1. The difference is visible on light slot 6 (Amber), where the authored `#FFE7C7` is a hand-tune the rule cannot reproduce.

- [ ] **Step 8: Run the generator and form tests**

```
./mvnw test -pl core -Dtest='*GraphGenerator*,*Target*,*Form*'
```
Expected: PASS, no new failures against the branch baseline.

- [ ] **Step 9: Commit**

```bash
git add core/src/main/java/inetsoft/graph/aesthetic/CategoricalColorFrame.java core/src/main/java/inetsoft/graph/guide/form/TargetForm.java core/src/main/java/inetsoft/report/composition/graph/GraphGenerator.java core/src/test/java/inetsoft/graph/aesthetic/CompanionBandFrameTest.java
```
```bash
git commit -m "Draw a target band in its measure's companion colour"
```

---

### Task 8: Full verification

**Files:** none modified — this task only runs gates.

- [ ] **Step 1: Run the full core suite**

```
./mvnw test -pl core
```
Expected: green against the branch baseline. Record the pass count; compare with a baseline run on `epic-74519` if anything looks off.

- [ ] **Step 2: Clean cross-module build**

```
./mvnw clean install -DskipTests -Pcommunity,enterprise
```
Expected: BUILD SUCCESS. **Use `clean`** — an incremental `install` has reported SUCCESS over a real cross-module break on this branch before. If enterprise fails on a pre-existing `License.Builder.formLicensed` skew unrelated to this work, substitute a community-only `./mvnw clean install -DskipTests` and record the substitution.

- [ ] **Step 3: Frontend regression check**

Nothing in this plan touches a component, but the picker's option list is server-fed:

```
cd web && npx ng test portal --include='**/palette-dialog.spec.ts'
```
Expected: PASS. Confirm neither `Modern-soft` nor `Modern Dark-soft` appears in `paletteSelectOptions`. Do **not** run the full TL suite.

- [ ] **Step 4: Manual browser pass**

With `viewsheet.modernVisualization` on. The checks that prove *new* behaviour are the first three:

1. **Single-band, no colour binding** — a bar chart on one measure with a target line and band. The band draws pale azure `#97BEEB`, not green. This is the case the design's drawing pins.
2. **Dimension-bound colour** — the same chart with colour bound to a dimension. The band stays the classic pale green. This is decision 2 and the most likely thing to get wrong.
3. **Multi-band** — a standard-deviation target with three factors. Three distinguishable bands at one hue, walking darker toward the base.
4. **Author's choice wins** — set a band colour by hand in the target dialog, save, reload. The chosen colour survives and no companion replaces it.
5. **Existing dashboard unchanged** — open a dashboard with a target band saved before this branch. Pixel-identical to before.
6. **Dark** — repeat 1 and 3 on a `MODERN_DARK` chart. Companions deepen rather than lift, except the anchor.
7. **Classic chart** — an unmarked dashboard keeps green bands throughout.
8. **Picker** — the palette dialog offers Default, Soft, Modern, Modern Dark and Contrast. Neither `-soft` name appears anywhere.

- [ ] **Step 5: Export parity**

Export one modern chart with a target band to PDF, PNG and Excel. Band colours match the viewer.

- [ ] **Step 6: Update the design doc status**

Change the design doc's `**Status:**` line from `approved, not implemented` to `implemented`, and add a short "What the implementation found" section if any premise in it turned out false — the branch's convention is to correct a design in place rather than leave a wrong claim standing.

```bash
git add docs/superpowers/specs/lookfeel/2026-09-14-chart-companion-colors-design.md
```
```bash
git commit -m "Mark the companion colours design implemented"
```

---

## Notes for the executor

**Three corrections this plan already carries**, found by computing the rule against the authored hexes before any code was written. Do not "fix" them back:

1. **The anchor threshold is mode-specific** (0.40 light, 0.50 dark). The source handoff says a flat 0.40; dark Ink measures L 0.420, so a flat threshold yields `#0E0033` against an authored `#8988AB`.
2. **Gamut resolution reduces chroma, never clamps channels.** With chroma reduction the dark companions reproduce the authored hexes 8/8; with channel clamping, 2/8.
3. **Light slot 6 (Amber) is a hand-tune the rule cannot reproduce** and is exempted in exactly one test, by index, with its reason stated. If a second slot ever needs exempting, something has drifted — investigate rather than widen the list.

**The one thing the design deliberately left open** was the accessor for a measure's series colour; Task 7 Step 5 pins it to `cvisitor.getMeasureFrame(field)` with `info.getColorField()` as the dimension-bound guard. If that turns out not to cover a chart type, the correct response is to return `null` — which keeps the classic greens — never to fall back to a guessed colour.
