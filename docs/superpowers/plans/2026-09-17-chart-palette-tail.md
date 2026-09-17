# Chart Palette Derived Tail Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace slots 9–40 of the `Modern` and `Modern Dark` chart palettes — today the 2010-era
legacy list — with a tail derived from each palette's own head, shipped as literal hexes and held in
place by a drift guard.

**Architecture:** Nothing generates at runtime. A derivation rule lives in **test sources** and is
the single authority for what the 64 hexes should be; the shipped values are literals in two places
(`defaults.css` and `VSChartPaletteDefaults`), and a guard re-derives and compares both. This is the
pattern the ramps slice used. `CategoricalColorFrame` is not touched at all — no bounds check, no
cache, no purity constraint, and `getColor`'s `index % size` wrap stays exactly where it is.

**Tech Stack:** Java 21, JUnit 5 + Spring test context, Maven (`./mvnw`), `inetsoft.graph.internal.OKLab`
(already on `epic-74519`, public static API).

**Spec:** `docs/superpowers/specs/lookfeel/2026-09-17-chart-palette-tail-design.md`

## Global Constraints

- **Branch:** `feature-chart-palette-tail`, already cut from `epic-74519` in the `community`
  submodule. Community-only change; nothing in this plan touches the enterprise repo.
- **`MODERN_HEAD` and `DARK_HEAD` are not modified.** Only slots 9–40 move.
- **`CategoricalColorFrame` is not modified.** If a task seems to need it, stop — that is a scope
  error, and the spec's "Why the source needed correcting" explains why.
- **The `Default` palette and `CategoricalColorFrame.COLOR_PALETTE` are not modified.** A classic
  chart resolves through `legacyPalette()` and must render identically after this change.
- **CSS indices are 1-based.** `defaults.css` slot `index='9'` is Java array index 8.
- **CSS hexes are lowercase**, matching every existing `ChartPalette` rule in the file.
- **Derivation constants, fixed for every task:** 3 lightness rings, ring spread `dL = 0.12`,
  chroma = the head's mean C, lightness centre = the head's mean L, 32 generated slots.
- **Build/test commands** run from the `community` submodule root:
  - Single test class: `./mvnw test -pl core -Dtest=ClassName`
  - Full core suite: `./mvnw test -pl core`

---

## File Structure

| File | Responsibility |
|---|---|
| `core/src/test/java/inetsoft/uql/viewsheet/internal/ChartTailDerivation.java` | **new.** The rule. Test sources only — nothing derives at runtime. One public entry point. |
| `core/src/test/java/inetsoft/uql/viewsheet/internal/ChartTailDerivationTest.java` | **new.** Port validation, determinism, the drift guard against the shipped Java constants, and the §2 acceptance constraints. |
| `core/src/main/java/inetsoft/uql/viewsheet/internal/VSChartPaletteDefaults.java` | `MODERN_TAIL` / `DARK_TAIL` constants; `spliceLegacy` → `splice`; `fromFrame` takes a complete fallback rather than a head. |
| `core/src/main/resources/inetsoft/util/css/defaults.css` | `Modern` and `Modern Dark` indices 9–40. |
| `core/src/test/java/inetsoft/uql/viewsheet/internal/VSChartPaletteDefaultsTest.java` | Six existing tests assert the legacy tail and must be re-pointed; the CSS↔Java guard widens from 8 slots to 40. |
| `core/src/test/java/inetsoft/uql/viewsheet/graph/aesthetic/ColorPalettesModernTest.java` | `tailMatchesLegacyPalette` asserts the old tail and must be re-pointed. |

**Known-breaking tests, enumerated so nobody discovers them one at a time.** Verified by reading at
`3a663abc2`:

| Test | File | Why it breaks |
|---|---|---|
| `gateOnSwapsToModernHeadButKeepsLegacyTail` | `VSChartPaletteDefaultsTest` | asserts `COLOR_PALETTE[8]` and `[39]` are slots 9 and 40 |
| `darkPaletteSwapsToDarkHeadKeepsLegacyTail` | `VSChartPaletteDefaultsTest` | same, dark |
| `spliceLegacyKeepsHeadAndTail` | `VSChartPaletteDefaultsTest` | `spliceLegacy` is removed |
| `fromFrameFallsBackWhenFrameIsNull` | `VSChartPaletteDefaultsTest` | asserts `spliceLegacy(...)` is the fallback |
| `fromFrameFallsBackWhenPaletteIsShort` | `VSChartPaletteDefaultsTest` | same |
| `fromFrameFallsBackWhenPaletteHasNullHole` | `VSChartPaletteDefaultsTest` | same |
| `tailMatchesLegacyPalette` | `ColorPalettesModernTest` | asserts CSS slots 9–40 equal the legacy tail |

**Must keep passing unchanged** — these are the regression anchors proving classic charts are
untouched: `ColorPalettesModernTest.defaultPaletteIsUnchanged` and
`VSChartPaletteDefaultsTest.darkInertWithoutModern`.

---

### Task 1: The derivation rule

Build the rule first, with nothing depending on it yet. Its own test proves it reproduces ENGINE
§3's published values when run in §3's single-ring configuration, which is what validates the OKLab
port before any shipped hex is derived from it.

**Files:**
- Create: `core/src/test/java/inetsoft/uql/viewsheet/internal/ChartTailDerivation.java`
- Test: `core/src/test/java/inetsoft/uql/viewsheet/internal/ChartTailDerivationTest.java`

**Interfaces:**
- Consumes: `inetsoft.graph.internal.OKLab` — `fromColor(Color)`, `toLCH(double[])`,
  `toColorInGamut(double l, double c, double h)`, all `public static`.
- Produces:
  - `static Color[] ChartTailDerivation.derive(Color[] head, int count, int rings, double lSpread)`
  - `static Color[] ChartTailDerivation.derive(Color[] head)` — the shipping configuration,
    `derive(head, 32, 3, 0.12)`
  - `static double ChartTailDerivation.deltaE(Color a, Color b)` — Euclidean distance in OKLab,
    used by Task 4

- [ ] **Step 1: Write the failing test**

Create `core/src/test/java/inetsoft/uql/viewsheet/internal/ChartTailDerivationTest.java`. Copy the
17-line AGPL header from `VSChartPaletteDefaultsTest.java` verbatim as the file's first lines.

```java
package inetsoft.uql.viewsheet.internal;

import org.junit.jupiter.api.Test;

import java.awt.Color;

import static org.junit.jupiter.api.Assertions.*;

class ChartTailDerivationTest {
   // The eight re-tuned head colours, duplicated here on purpose: this test is the authority on
   // what the rule produces, so it must not read the constants the rule is used to check.
   private static final Color[] MODERN_HEAD = {
      new Color(0x0490FF), new Color(0xFF5A35), new Color(0x241C4F), new Color(0x03D9B3),
      new Color(0x9A2DDC), new Color(0xFFB020), new Color(0xE5197E), new Color(0x8ED604)
   };

   // Port validation. ENGINE §3 publishes its first three generated slots, derived with ONE ring.
   // Reproducing them is what proves this OKLab port agrees with the one the handoff was written
   // against, before any shipped hex is derived from it. Slot 11 is excluded deliberately: its
   // published chroma is 0.202 where the head's mean is 0.188, so it did not come from the stated
   // rule. Recorded in the design's decision 3.
   @Test
   void singleRingReproducesTheHandoffsPublishedSlots() {
      Color[] tail = ChartTailDerivation.derive(MODERN_HEAD, 3, 1, 0);

      assertEquals(new Color(0x009FB6), tail[0], "ENGINE §3 slot 9");
      assertEquals(new Color(0x9E9000), tail[1], "ENGINE §3 slot 10");
   }

   @Test
   void theRuleIsAPureFunctionOfTheHead() {
      assertArrayEquals(ChartTailDerivation.derive(MODERN_HEAD),
                        ChartTailDerivation.derive(MODERN_HEAD),
                        "two runs on the same head must agree, or the shipped literals drift");
   }

   @Test
   void theShippingConfigurationProducesThirtyTwoSlots() {
      assertEquals(32, ChartTailDerivation.derive(MODERN_HEAD).length);
   }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw test -pl core -Dtest=ChartTailDerivationTest`
Expected: FAIL — compilation error, `ChartTailDerivation` does not exist.

- [ ] **Step 3: Write minimal implementation**

Create `core/src/test/java/inetsoft/uql/viewsheet/internal/ChartTailDerivation.java`, again with the
AGPL header copied verbatim from a sibling file.

```java
package inetsoft.uql.viewsheet.internal;

import inetsoft.graph.internal.OKLab;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The rule that produces the Modern and Modern Dark palette tails, slots 9-40.
 *
 * This lives in test sources on purpose. Nothing derives a tail at runtime - the palettes ship
 * literal hexes in defaults.css and VSChartPaletteDefaults, and ChartTailDerivationTest re-derives
 * and compares. Keeping the rule out of main means the frame stays free of the purity, caching and
 * CSS-reachability constraints that ENGINE §3's runtime generator would have carried.
 */
final class ChartTailDerivation {
   private ChartTailDerivation() {
   }

   /** The shipping configuration: 32 slots, three rings, 0.12 apart. */
   static Color[] derive(Color[] head) {
      return derive(head, 32, 3, 0.12);
   }

   /**
    * Bisect the widest remaining hue gap, count times, seeded with the head's hues. Each generated
    * hue joins the circle for the next round, so the gaps close evenly.
    *
    * Lightness is the interesting part. ENGINE §3 puts every generated slot at the ring's mean
    * lightness, which collapses at 32 slots - hue alone cannot carry forty categories. So there are
    * three rings, and each slot takes whichever ring separates it furthest from everything already
    * placed, head included. Ties break on the interleave order, which keeps the walk deterministic
    * and therefore keeps the shipped literals reproducible.
    */
   static Color[] derive(Color[] head, int count, int rings, double lSpread) {
      List<Double> hues = new ArrayList<>();
      double sumL = 0;
      double sumC = 0;

      for(Color c : head) {
         double[] lch = OKLab.toLCH(OKLab.fromColor(c));
         hues.add(lch[2]);
         sumL += lch[0];
         sumC += lch[1];
      }

      double meanL = sumL / head.length;
      double meanC = sumC / head.length;
      List<Color> out = new ArrayList<>();
      List<Color> placed = new ArrayList<>(List.of(head));

      for(int i = 0; i < count; i++) {
         double mid = widestGapMidpoint(hues);
         Color best = null;
         double bestSeparation = -1;

         for(int r = 0; r < rings; r++) {
            int ringIndex = rings <= 1 ? 0 : (i + r) % rings;
            double l = meanL + (rings <= 1 ? 0 : (ringIndex - (rings - 1) / 2.0) * lSpread);
            Color candidate = OKLab.toColorInGamut(l, meanC, mid);
            double separation = Double.MAX_VALUE;

            for(Color p : placed) {
               separation = Math.min(separation, deltaE(candidate, p));
            }

            if(separation > bestSeparation) {
               bestSeparation = separation;
               best = candidate;
            }
         }

         out.add(best);
         placed.add(best);
         hues.add(mid);
      }

      return out.toArray(new Color[0]);
   }

   /** Euclidean distance in OKLab. */
   static double deltaE(Color a, Color b) {
      double[] x = OKLab.fromColor(a);
      double[] y = OKLab.fromColor(b);

      return Math.sqrt(Math.pow(x[0] - y[0], 2)
                          + Math.pow(x[1] - y[1], 2)
                          + Math.pow(x[2] - y[2], 2));
   }

   private static double widestGapMidpoint(List<Double> hues) {
      List<Double> sorted = new ArrayList<>(hues);
      Collections.sort(sorted);
      double widest = -1;
      double midpoint = 0;

      for(int i = 0; i < sorted.size(); i++) {
         double lo = sorted.get(i);
         double hi = i + 1 < sorted.size() ? sorted.get(i + 1) : sorted.get(0) + 360;

         if(hi - lo > widest) {
            widest = hi - lo;
            midpoint = (lo + hi) / 2 % 360;
         }
      }

      return midpoint;
   }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./mvnw test -pl core -Dtest=ChartTailDerivationTest`
Expected: PASS, 3 tests.

If `singleRingReproducesTheHandoffsPublishedSlots` fails, **stop and do not adjust the expected
hexes.** Those two values come from the external handoff and are the only independent check that
this port is correct. A mismatch means the rule is wrong, not that the handoff is.

- [ ] **Step 5: Commit**

```bash
git add core/src/test/java/inetsoft/uql/viewsheet/internal/ChartTailDerivation.java core/src/test/java/inetsoft/uql/viewsheet/internal/ChartTailDerivationTest.java
git commit -m "Add the palette tail's derivation rule"
```

---

### Task 2: The tail constants and the fallback re-point

The Java side. After this task the fallback path serves derived colours; CSS still serves legacy, so
the two disagree until Task 3 — which is exactly what the guard added here will say.

**Files:**
- Modify: `core/src/main/java/inetsoft/uql/viewsheet/internal/VSChartPaletteDefaults.java:192-229`
  (`spliceLegacy` and `fromFrame`), `:48-54` (`modernPalette`/`darkPalette`), `:235` (`resolve`)
- Modify: `core/src/test/java/inetsoft/uql/viewsheet/internal/VSChartPaletteDefaultsTest.java`
- Test: `core/src/test/java/inetsoft/uql/viewsheet/internal/ChartTailDerivationTest.java`

**Interfaces:**
- Consumes: `ChartTailDerivation.derive(Color[])` from Task 1.
- Produces:
  - `VSChartPaletteDefaults.MODERN_TAIL`, `DARK_TAIL` — `private static final Color[]`, 32 each,
    reached from tests by reflection the way `MODERN_HEAD` already is
  - `static Color[] VSChartPaletteDefaults.splice(Color[] head, Color[] tail)`
  - `static Color[] VSChartPaletteDefaults.fromFrame(CategoricalColorFrame frame, Color[] fallback)`
    — same arity as today, but the second parameter is now the **complete** 40-colour fallback
    rather than a head to splice onto

- [ ] **Step 1: Write the failing test**

Append to `ChartTailDerivationTest.java`, and add `import java.lang.reflect.Field;` to its imports:

```java
   private static final Color[] DARK_HEAD = {
      new Color(0x4FA5FF), new Color(0xFF8367), new Color(0x49447D), new Color(0x2DEEC6),
      new Color(0xAE41F5), new Color(0xFFCB82), new Color(0xFE3290), new Color(0x9FEB28)
   };

   // The drift guard. If the head is ever re-tuned again, the tail fails loudly here instead of
   // silently belonging to the previous head.
   @Test
   void shippedTailsMatchTheRule() throws Exception {
      assertArrayEquals(ChartTailDerivation.derive(MODERN_HEAD), colorArray("MODERN_TAIL"),
                        "MODERN_TAIL must be what the rule derives from MODERN_HEAD");
      assertArrayEquals(ChartTailDerivation.derive(DARK_HEAD), colorArray("DARK_TAIL"),
                        "DARK_TAIL must be what the rule derives from DARK_HEAD");
   }

   @Test
   void shippedTailsAreThirtyTwoSlotsEach() throws Exception {
      assertEquals(32, colorArray("MODERN_TAIL").length);
      assertEquals(32, colorArray("DARK_TAIL").length);
   }

   private static Color[] colorArray(String fieldName) throws Exception {
      Field field = VSChartPaletteDefaults.class.getDeclaredField(fieldName);
      field.setAccessible(true);
      return (Color[]) field.get(null);
   }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw test -pl core -Dtest=ChartTailDerivationTest`
Expected: FAIL — `NoSuchFieldException: MODERN_TAIL`.

- [ ] **Step 3: Write minimal implementation**

In `VSChartPaletteDefaults.java`, **replace** `spliceLegacy` and `fromFrame` (currently lines
192–229) with:

```java
   /**
    * A palette's head followed by its tail. The tail is derived from the head rather than taken
    * from the legacy list - see the palette tail design. ChartTailDerivation, in test sources, is
    * the authority on the values, and ChartTailDerivationTest re-derives and compares.
    */
   static Color[] splice(Color[] head, Color[] tail) {
      List<Color> palette = new ArrayList<>(Arrays.asList(head));
      palette.addAll(Arrays.asList(tail));
      return palette.toArray(new Color[0]);
   }

   /**
    * Colors copied out of a palette frame by index, or the given fallback when the frame is
    * absent, short, or has undeclared holes. Copies rather than aliases - the frame is a shared
    * per-org cached instance, and the fallback is a shared static constant that legacyPalette()
    * hands straight to a caller without resolve()'s clone.
    */
   static Color[] fromFrame(CategoricalColorFrame frame, Color[] fallback) {
      if(frame == null) {
         return fallback.clone();
      }

      int count = frame.getColorCount();

      if(count < CategoricalColorFrame.COLOR_PALETTE.length) {
         return fallback.clone();
      }

      Color[] colors = new Color[count];

      for(int i = 0; i < count; i++) {
         colors[i] = frame.getDefaultColor(i);

         if(colors[i] == null) {
            return fallback.clone();
         }
      }

      return colors;
   }
```

Change `resolve`'s signature and its `fromFrame` call (currently line 235 and 247) — rename the
parameter only, since it is now a complete fallback:

```java
   private static Color[] resolve(String name, Color[] fallback) {
```

```java
      Color[] resolved = fromFrame(getPaletteSafely(name), fallback);
```

Change the two public entry points (currently lines 48–54):

```java
   public static Color[] modernPalette() {
      return resolve(MODERN_NAME, MODERN_FALLBACK);
   }

   public static Color[] darkPalette() {
      return resolve(DARK_NAME, DARK_FALLBACK);
   }
```

`legacyPalette()` at line 84 **needs no change** — it already passes the complete 40-colour
`CategoricalColorFrame.COLOR_PALETTE`, which is exactly the new contract.

Add to the constants block at the bottom of the class, immediately after `DARK_HEAD`:

```java
   private static final Color[] MODERN_TAIL = {
      new Color(0x00788A), new Color(0x9E9000), new Color(0xF77FE4), new Color(0x00A956),
      new Color(0xA25100), new Color(0x00A2A0), new Color(0x2FC1FF), new Color(0xFF8B93),
      new Color(0x405CD4), new Color(0x836600), new Color(0xAEBF00), new Color(0xBF60D1),
      new Color(0xB02B80), new Color(0xBC9FFF), new Color(0x1C8000), new Color(0x007E57),
      new Color(0xFF915F), new Color(0xC87800), new Color(0x007B70), new Color(0x00CAD7),
      new Color(0x009DC2), new Color(0x0071AB), new Color(0xBF272B), new Color(0xE65078),
      new Color(0x757CFC), new Color(0x86B3FF), new Color(0xE4A700), new Color(0x5D7500),
      new Color(0xD1B000), new Color(0x706F00), new Color(0x913DB3), new Color(0x9F35A1)
   };

   private static final Color[] DARK_TAIL = {
      new Color(0x008FA4), new Color(0x8E8100), new Color(0xFFA7EF), new Color(0x2FC16A),
      new Color(0xC06200), new Color(0x00BBB9), new Color(0x84D5FF), new Color(0xD2485A),
      new Color(0x5575E5), new Color(0x9FAF00), new Color(0xF6C200), new Color(0xD37BE5),
      new Color(0xC44B94), new Color(0x8C63D8), new Color(0x009668), new Color(0x369725),
      new Color(0xFFB596), new Color(0xE78A00), new Color(0x009386), new Color(0x0087CB),
      new Color(0x00E4F2), new Color(0x00B5DF), new Color(0xFD716A), new Color(0xFFB0BE),
      new Color(0xABCBFF), new Color(0x8D98FF), new Color(0xD19800), new Color(0xD2D226),
      new Color(0x6F8C00), new Color(0xC0A200), new Color(0xA459C5), new Color(0xB352B4)
   };

   private static final Color[] MODERN_FALLBACK = splice(MODERN_HEAD, MODERN_TAIL);
   private static final Color[] DARK_FALLBACK = splice(DARK_HEAD, DARK_TAIL);
```

**Java initialisation order matters here.** `MODERN_FALLBACK` calls `splice` and reads
`MODERN_HEAD`, so it must be declared *after* both `MODERN_HEAD` and `DARK_HEAD` in the source. A
static field that reads a later-declared static reads `null` without any warning.

- [ ] **Step 4: Run test to verify it passes**

Run: `./mvnw test -pl core -Dtest=ChartTailDerivationTest`
Expected: PASS, 5 tests.

- [ ] **Step 5: Re-point the six broken tests**

In `VSChartPaletteDefaultsTest.java`:

Rename `gateOnSwapsToModernHeadButKeepsLegacyTail` to `gateOnSwapsToModernHeadAndDerivedTail` and
replace its two tail assertions:

```java
      // index 9+ is now derived from the head, not taken from the legacy list
      assertEquals(new Color(0x00788A), modern[8]);
      assertEquals(new Color(0x9F35A1), modern[39]);
```

Rename `darkPaletteSwapsToDarkHeadKeepsLegacyTail` to `darkPaletteSwapsToDarkHeadAndDerivedTail` and
replace its two:

```java
      assertEquals(new Color(0x008FA4), dark[8]);
      assertEquals(new Color(0xB352B4), dark[39]);
```

Replace `spliceLegacyKeepsHeadAndTail` entirely — `spliceLegacy` no longer exists:

```java
   @Test
   void spliceJoinsHeadAndTail() {
      Color[] head = { new Color(0x010203), new Color(0x040506) };
      Color[] tail = { new Color(0x070809) };
      Color[] result = VSChartPaletteDefaults.splice(head, tail);

      assertEquals(3, result.length);
      assertEquals(new Color(0x010203), result[0]);
      assertEquals(new Color(0x040506), result[1]);
      assertEquals(new Color(0x070809), result[2]);
   }
```

In the three `fromFrameFallsBack*` tests, replace each
`assertArrayEquals(VSChartPaletteDefaults.spliceLegacy(MODERN_HEAD_FIXTURE), result);` with
`assertArrayEquals(MODERN_HEAD_FIXTURE, result);` — `fromFrame` now returns the fallback it was
handed. Then widen `MODERN_HEAD_FIXTURE` (line 177) to a complete 40-colour array so it is a legal
fallback, keeping its existing first two entries and padding the rest:

```java
   private static final Color[] MODERN_HEAD_FIXTURE = fixture();

   private static Color[] fixture() {
      Color[] colors = new Color[40];
      colors[0] = new Color(0x0490FF);
      colors[1] = new Color(0xFF5A35);

      for(int i = 2; i < colors.length; i++) {
         colors[i] = new Color(0x100000 + i);
      }

      return colors;
   }
```

- [ ] **Step 6: Run the palette tests**

Run: `./mvnw test -pl core -Dtest=VSChartPaletteDefaultsTest`
Expected: PASS. `darkInertWithoutModern` must still pass untouched — that is the classic-chart
regression anchor.

`ColorPalettesModernTest.tailMatchesLegacyPalette` is still expected to FAIL at this point, because
CSS has not moved yet. That is Task 3. Do not "fix" it here by weakening it.

- [ ] **Step 7: Commit**

```bash
git add core/src/main/java/inetsoft/uql/viewsheet/internal/VSChartPaletteDefaults.java core/src/test/java/inetsoft/uql/viewsheet/internal/VSChartPaletteDefaultsTest.java core/src/test/java/inetsoft/uql/viewsheet/internal/ChartTailDerivationTest.java
git commit -m "Derive the modern palettes' fallback tail from their heads"
```

---

### Task 3: The CSS declarations

CSS is what a modern chart actually renders from; the Java constants are only the fallback. After
this task the two agree and the change is visible.

**Files:**
- Modify: `core/src/main/resources/inetsoft/util/css/defaults.css` — `Modern` and `Modern Dark`,
  indices 9–40
- Modify: `core/src/test/java/inetsoft/uql/viewsheet/graph/aesthetic/ColorPalettesModernTest.java`
- Modify: `core/src/test/java/inetsoft/uql/viewsheet/internal/VSChartPaletteDefaultsTest.java:294-311`

**Interfaces:**
- Consumes: `MODERN_TAIL` / `DARK_TAIL` from Task 2. The CSS values are the same 64 hexes,
  lowercased.
- Produces: nothing new. This task makes CSS and Java agree.

- [ ] **Step 1: Write the failing test**

In `ColorPalettesModernTest.java`, replace `tailMatchesLegacyPalette` (and its two-line comment
above it) with:

```java
   // The tail is no longer the legacy list. The exhaustive CSS-to-Java comparison lives in
   // VSChartPaletteDefaultsTest.cssMatchesTheJavaFallback, which is in the same package as the
   // constants and already reflects into them; this file keeps literal spot-checks in its own
   // style, and guards the thing that would be easy to half-do - leaving some slots behind.
   @Test
   void tailIsDerivedRatherThanLegacy() {
      CategoricalColorFrame modern = ColorPalettes.getPalette("Modern");
      CategoricalColorFrame dark = ColorPalettes.getPalette("Modern Dark");

      assertEquals(new Color(0x00788a), modern.getDefaultColor(8), "Modern index 9");
      assertEquals(new Color(0x9f35a1), modern.getDefaultColor(39), "Modern index 40");
      assertEquals(new Color(0x008fa4), dark.getDefaultColor(8), "Modern Dark index 9");
      assertEquals(new Color(0xb352b4), dark.getDefaultColor(39), "Modern Dark index 40");

      // no slot may still hold the legacy value it replaced
      for(int i = 8; i < CategoricalColorFrame.COLOR_PALETTE.length; i++) {
         assertNotEquals(CategoricalColorFrame.COLOR_PALETTE[i], modern.getDefaultColor(i),
                         "Modern index " + (i + 1) + " is still the legacy colour");
         assertNotEquals(CategoricalColorFrame.COLOR_PALETTE[i], dark.getDefaultColor(i),
                         "Modern Dark index " + (i + 1) + " is still the legacy colour");
      }
   }
```

No new imports needed, verified at `3a663abc2`: that file already has
`import static org.junit.jupiter.api.Assertions.*;` (line 18), which covers `assertNotEquals`, and
already imports `CategoricalColorFrame` for the test being replaced.

Verified too that **no derived slot coincidentally equals the legacy colour it replaces**, in either
palette — so the loop above cannot fail for a reason other than an unconverted slot.

Then widen the CSS↔Java guard in `VSChartPaletteDefaultsTest.assertHeadMatches` (line 299) so it
covers all forty slots rather than the head alone. Rename it and its caller:

```java
   @Test
   void cssMatchesTheJavaFallback() throws Exception {
      assertPaletteMatches("MODERN_FALLBACK", "Modern");
      assertPaletteMatches("DARK_FALLBACK", "Modern Dark");
   }

   private void assertPaletteMatches(String fieldName, String paletteName) throws Exception {
      Field field = VSChartPaletteDefaults.class.getDeclaredField(fieldName);
      field.setAccessible(true);
      Color[] fallback = (Color[]) field.get(null);
      CategoricalColorFrame css = ColorPalettes.getPalette(paletteName);

      assertEquals(40, fallback.length, fieldName + " must declare all forty slots");

      for(int i = 0; i < fallback.length; i++) {
         assertEquals(fallback[i], css.getDefaultColor(i),
                      paletteName + " index " + (i + 1) + " must match " + fieldName);
      }
   }
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./mvnw test -pl core -Dtest=ColorPalettesModernTest,VSChartPaletteDefaultsTest`
Expected: FAIL — `tailIsDerivedRatherThanLegacy` reports `Modern index 9 expected #00788A but was
#9368BE`, and `cssMatchesTheJavaFallback` reports `Modern index 9 must match MODERN_FALLBACK`. CSS
is still declaring the legacy tail.

- [ ] **Step 3: Write the CSS**

In `defaults.css`, replace the `color:` value of each existing
`ChartPalette[name='Modern'][index='N']` rule for N = 9..40, in order:

```
9  #00788a   10 #9e9000   11 #f77fe4   12 #00a956   13 #a25100   14 #00a2a0
15 #2fc1ff   16 #ff8b93   17 #405cd4   18 #836600   19 #aebf00   20 #bf60d1
21 #b02b80   22 #bc9fff   23 #1c8000   24 #007e57   25 #ff915f   26 #c87800
27 #007b70   28 #00cad7   29 #009dc2   30 #0071ab   31 #bf272b   32 #e65078
33 #757cfc   34 #86b3ff   35 #e4a700   36 #5d7500   37 #d1b000   38 #706f00
39 #913db3   40 #9f35a1
```

And for `ChartPalette[name='Modern Dark'][index='N']`, N = 9..40:

```
9  #008fa4   10 #8e8100   11 #ffa7ef   12 #2fc16a   13 #c06200   14 #00bbb9
15 #84d5ff   16 #d2485a   17 #5575e5   18 #9faf00   19 #f6c200   20 #d37be5
21 #c44b94   22 #8c63d8   23 #009668   24 #369725   25 #ffb596   26 #e78a00
27 #009386   28 #0087cb   29 #00e4f2   30 #00b5df   31 #fd716a   32 #ffb0be
33 #abcbff   34 #8d98ff   35 #d19800   36 #d2d226   37 #6f8c00   38 #c0a200
39 #a459c5   40 #b352b4
```

**Do not touch `ChartPalette[name='Default']`** at any index, and do not touch either palette at
indices 1–8. The rule count must stay at exactly 40 per palette — replace values in place, do not
add or remove rules.

- [ ] **Step 4: Run tests to verify they pass**

Run: `./mvnw test -pl core -Dtest=ColorPalettesModernTest,VSChartPaletteDefaultsTest`
Expected: PASS. `ColorPalettesModernTest.defaultPaletteIsUnchanged` must still pass — it is the
proof that classic charts did not move.

- [ ] **Step 5: Verify the rule count did not change**

Run:
```bash
grep -c "ChartPalette\[name='Modern'\]\[index=" core/src/main/resources/inetsoft/util/css/defaults.css
grep -c "ChartPalette\[name='Modern Dark'\]\[index=" core/src/main/resources/inetsoft/util/css/defaults.css
grep -c "ChartPalette\[name='Default'\]\[index=" core/src/main/resources/inetsoft/util/css/defaults.css
```
Expected: `40`, `40`, `40`.

- [ ] **Step 6: Commit**

```bash
git add core/src/main/resources/inetsoft/util/css/defaults.css core/src/test/java/inetsoft/uql/viewsheet/graph/aesthetic/ColorPalettesModernTest.java core/src/test/java/inetsoft/uql/viewsheet/internal/VSChartPaletteDefaultsTest.java
git commit -m "Declare the derived tail in the modern palettes' CSS"
```

---

### Task 4: The acceptance constraints as assertions

The design's §2 table is the argument for the slice. Turning it into assertions means a later change
to the rule has to restate its cost rather than quietly lose it.

**Files:**
- Modify: `core/src/test/java/inetsoft/uql/viewsheet/internal/ChartTailDerivationTest.java`

**Interfaces:**
- Consumes: `ChartTailDerivation.derive(Color[])` and `ChartTailDerivation.deltaE(Color, Color)`
  from Task 1; `MODERN_FALLBACK` / `DARK_FALLBACK` from Task 2 by reflection.
- Produces: nothing. Terminal verification task.

- [ ] **Step 1: Write the failing test**

Append to `ChartTailDerivationTest.java`. Add `import inetsoft.graph.internal.OKLab;` to its imports.

```java
   // The companion rule's light-mode escape hatch: above this, getCompanionColor deepens instead of
   // lifting, because a lift would land past white. Mirrors CategoricalColorFrame.LIGHT_MAX_L.
   private static final double LIGHT_MAX_L = 0.96;

   // The companion rule's anchor threshold in dark mode, from CategoricalColorFrame.
   private static final double DARK_ANCHOR_MAX_L = 0.50;

   // The whole point of the slice. Holding the tail inside the head's lightness band means the
   // companion rule covers every generated slot by construction, so no slot recedes in the opposite
   // direction from its neighbours. Today's legacy tail trips this three times by slot 16.
   @Test
   void noModernSlotNeedsTheLightEndException() throws Exception {
      for(Color c : colorArray("MODERN_FALLBACK")) {
         double l = OKLab.toLCH(OKLab.fromColor(c))[0];
         assertTrue(l + 0.14 <= LIGHT_MAX_L,
                    "no Modern slot may need the light-end exception, but " + hex(c)
                       + " sits at L " + l);
      }
   }

   // Dark's mirror. Exactly one slot may take the anchor branch - the anchor itself, #49447D, which
   // is the set's darkest member and is meant to. Today's tail adds three more.
   @Test
   void onlyTheAnchorTakesTheDarkAnchorBranch() throws Exception {
      int anchors = 0;

      for(Color c : colorArray("DARK_FALLBACK")) {
         if(OKLab.toLCH(OKLab.fromColor(c))[0] < DARK_ANCHOR_MAX_L) {
            anchors++;
         }
      }

      assertEquals(1, anchors, "only #49447D may take the dark anchor branch");
   }

   @Test
   void everySlotSitsInsideTheHeadsLightnessBand() throws Exception {
      for(Color c : colorArray("MODERN_FALLBACK")) {
         double l = OKLab.toLCH(OKLab.fromColor(c))[0];
         assertTrue(l >= 0.269 - 1e-3 && l <= 0.813 + 1e-3,
                    hex(c) + " at L " + l + " sits outside the head's band 0.269-0.813");
      }
   }

   // The measured figures from the design's §2. Asserted with a tolerance rather than as floors, so
   // a change to the rule has to restate its cost instead of silently coasting under a round number.
   @Test
   void separationMatchesTheMeasuredFigures() throws Exception {
      assertEquals(0.1311, minDeltaE("MODERN_FALLBACK", 12), 5e-4, "Modern at n=12");
      assertEquals(0.1127, minDeltaE("MODERN_FALLBACK", 16), 5e-4, "Modern at n=16");
      assertEquals(0.0319, minDeltaE("MODERN_FALLBACK", 40), 5e-4, "Modern at n=40");
      assertEquals(0.1491, minDeltaE("DARK_FALLBACK", 12), 5e-4, "Modern Dark at n=12");
      assertEquals(0.1079, minDeltaE("DARK_FALLBACK", 16), 5e-4, "Modern Dark at n=16");
      assertEquals(0.0361, minDeltaE("DARK_FALLBACK", 40), 5e-4, "Modern Dark at n=40");
   }

   // A tail colour that reads as a head colour is the failure the aggregate figures hide, because it
   // pairs slot 1 with slot 34. Modern beats the legacy tail's 0.0365 here; dark's 0.0405 is below
   // the legacy tail's 0.0461 and is recorded in the design as the one axis that does not improve.
   @Test
   void worstHeadToTailPairMatchesTheMeasuredFigures() throws Exception {
      assertEquals(0.0521, worstHeadToTail("MODERN_FALLBACK"), 5e-4, "Modern");
      assertEquals(0.0405, worstHeadToTail("DARK_FALLBACK"), 5e-4, "Modern Dark");
   }

   private static double minDeltaE(String fieldName, int n) throws Exception {
      Color[] palette = colorArray(fieldName);
      double worst = Double.MAX_VALUE;

      for(int i = 0; i < n; i++) {
         for(int j = i + 1; j < n; j++) {
            worst = Math.min(worst, ChartTailDerivation.deltaE(palette[i], palette[j]));
         }
      }

      return worst;
   }

   private static double worstHeadToTail(String fieldName) throws Exception {
      Color[] palette = colorArray(fieldName);
      double worst = Double.MAX_VALUE;

      for(int i = 0; i < 8; i++) {
         for(int j = 8; j < palette.length; j++) {
            worst = Math.min(worst, ChartTailDerivation.deltaE(palette[i], palette[j]));
         }
      }

      return worst;
   }

   // colorArray(String) already exists from Task 2 — do not add a second copy.

   private static String hex(Color c) {
      return String.format("#%02X%02X%02X", c.getRed(), c.getGreen(), c.getBlue());
   }
```

- [ ] **Step 2: Run test to verify it passes**

Run: `./mvnw test -pl core -Dtest=ChartTailDerivationTest`
Expected: PASS, 10 tests.

These assert properties of code written in Tasks 2 and 3, so they pass immediately rather than
failing first. That is correct for a characterisation test — its job is to pin a measured property,
not to drive new code. **Prove each one can fail** before committing, per Step 3.

- [ ] **Step 3: Prove the constraints can fail**

A test that cannot fail is not a guard. Temporarily change `MODERN_TAIL`'s first entry in
`VSChartPaletteDefaults.java` from `0x00788A` to `0xFFFFF0` and re-run:

Run: `./mvnw test -pl core -Dtest=ChartTailDerivationTest`
Expected: FAIL in at least `shippedTailsMatchTheRule`, `noModernSlotNeedsTheLightEndException` and
`everySlotSitsInsideTheHeadsLightnessBand`.

Then **revert that edit** and re-run to confirm green again.

- [ ] **Step 4: Commit**

```bash
git add core/src/test/java/inetsoft/uql/viewsheet/internal/ChartTailDerivationTest.java
git commit -m "Pin the derived tail's acceptance constraints"
```

---

### Task 5: Full verification

**Files:** none modified. This task produces evidence, not code.

**Interfaces:**
- Consumes: everything above.
- Produces: the figures a PR description needs.

- [ ] **Step 1: Run the full core suite**

Run: `./mvnw test -pl core`
Expected: 0 failures, 0 errors. Record the exact test count for the PR description.

If anything outside the seven tests enumerated in **File Structure** fails, **stop and report it**
rather than fixing it inline — an unlisted failure means the blast radius is wider than this plan
measured, and the spec's §4 claims about who is affected would need revisiting.

- [ ] **Step 2: Confirm the cross-module build**

Run: `./mvnw clean install -pl core -am -DskipTests`
Expected: BUILD SUCCESS. `clean` matters: the ramps slice found that a 77-module incremental
`install` can report SUCCESS while a signature change leaves another module stale.

- [ ] **Step 3: The manual pass**

Not unit-testable, and the reason the trigger was raised on a real chart rather than in a test. Build
and start the server per `CLAUDE.md`, then on a **modern-marked** dashboard check:

1. A twelve-series bar chart in light mode — does it read as one set, or does the ninth series look
   like it came from a different palette?
2. The same in dark mode.
3. A tree chart with eleven categories, **brushed**. This is the exact case from
   `engine-3-palette-overflow-trigger.md` that rendered nodes with no fill at all. Every node must
   be visible, and each unselected node must recede toward its own series colour.
4. A **classic** (unmarked) chart with twelve series — must be pixel-identical to before. This is
   the regression that matters most, because `legacyPalette()` is a different code path that this
   slice deliberately did not touch.
5. A chart on the `Contrast` palette with nine categories — unchanged, per decision 2. Confirming
   it still shows its old tail is confirming the deferral, not finding a bug.

- [ ] **Step 4: Record the outcome in the design**

Add a "What the implementation found" section to
`docs/superpowers/specs/lookfeel/2026-09-17-chart-palette-tail-design.md` covering anything the
plan got wrong, and the date the manual pass ran. The sibling designs all carry one; a design whose
premises were never checked against the build is worth less than one that was.

- [ ] **Step 5: Commit**

```bash
git add docs/superpowers/specs/lookfeel/2026-09-17-chart-palette-tail-design.md
git commit -m "Record what building the palette tail found"
```

---

## Out of scope, and deliberately so

Taken from the design's §6. If a task seems to need one of these, it is a scope error — stop and
re-read the spec rather than widening the change.

- **`Contrast` and `Soft` tails.** Both are 8 slots and reach a tail only through a deliberate author
  action.
- **`CategoricalColorFrame.getColor`'s `index % size` wrap**, at line 361. Still there, still
  unreachable below 41 categories.
- **The negative-colour wrap** at line 357.
- **Authored companions past slot 8.** `Modern-soft` and `Modern Dark-soft` stay at 8 entries;
  generated slots fall to the derivation rule, which is now exact for them by construction.
- **The Composer top-*n*-plus-Other nudge.** Nothing here makes a twenty-category chart readable.
