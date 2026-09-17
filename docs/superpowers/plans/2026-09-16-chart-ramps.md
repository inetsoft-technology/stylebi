# Chart Ramps (ENGINE §4) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Author three house colour ramps for the measure→colour path (Amber, Teal, Variance), hide the legacy ramp families they succeed from a modern-marked chart, and re-point the seeded default so a modern chart is born on Teal rather than on `BluesColorFrame`.

**Architecture:** Three `AbstractSplineColorFrame` subclasses holding authored seven-stop hex tables, derived once by a rule that lives in test code and is pinned by a drift guard. Picker policy lives in `VSChartPaletteDefaults` beside the re-tune's `hiddenPaletteNames`; the Angular pane stays a filter over its existing arrays. The seeded default becomes a resolver, `defaultLinearFrame(VizContext)`, and the eight hard-coded `new BluesColorFrame()` sites become calls to it with a `VizContext` threaded from the assembly.

**Tech Stack:** Java 21, JUnit 5 (`@Tag("core")`, `@SreeHome`, `SpringExtension`), Angular 21 standalone components, Vitest + `@testing-library/angular` + MSW.

**Spec:** `docs/superpowers/specs/lookfeel/2026-09-16-chart-ramps-design.md`

**Branch:** `feature-chart-ramps`, based on `feature-chart-companion-colors` (PR #5275) because `OKLab` is not yet on `epic-74519`. Retarget the PR to `epic-74519` once #5275 merges.

## Global Constraints

Copied verbatim from the spec. Every task's requirements implicitly include this section.

- **Surfaces.** Light canvas `#F8F7F4`. Dark surface `#252428` (`VSChartChromeDefaults.LEGEND_BG_DARK`, `--dark-surface-default`). Never hard-code the OKLab lightness of either — measure both with `OKLab.fromColor` so the constants follow the surfaces if a surface moves.
- **C1 Legibility.** Every stop clears both surfaces by **ΔL ≥ 0.12** in OKLab.
- **C2 Ordering.** L strictly monotonic across Amber and Teal, spanning **at least 0.30** end to end. For Variance, chroma strictly monotonic outward from the centre on each wing, with L inside a **0.10** band across the whole ramp.
- **C3 Gamut.** Every derived stop survives `OKLab.toColorInGamut` without chroma collapse.
- **C4 Hue fidelity.** Derived hue within **5°** of the handoff's in OKLCH. Variance's midpoint is exempt — achromatic by construction.
- **Nothing is removed from resolution.** Hiding is a display concern only. A chart already on any retired ramp must keep rendering it and keep showing it selected.
- **The assembly's `VizMark` is the authority.** `VizContext.ofGate()` is the absent-assembly default, never the first choice. A mark that cannot be resolved falls back rather than failing the request.
- **Frontend test runs are scoped.** Never run the full TL suite. Use the `portal:test-tl` target with an explicit `--include` path.
- **Commit trailer.** Every commit ends with:
  `Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>`

---

### Task 1: Derivation harness and the three ramps' stops

The rule lives in test sources, not production. Nothing needs runtime derivation — the three ramps are fixed — so production ships literal hexes and the test re-derives them to catch drift. This task produces the hexes every later task depends on.

**Files:**
- Create: `core/src/test/java/inetsoft/graph/aesthetic/ChartRampDerivation.java`
- Create: `core/src/test/java/inetsoft/graph/aesthetic/ChartRampDerivationTest.java`

**Interfaces:**
- Consumes: `inetsoft.graph.internal.OKLab` — `double[] fromColor(Color)`, `double[] toLCH(double[] lab)`, `Color toColorInGamut(double l, double c, double h)`. Hue is degrees in `[0, 360)`.
- Produces: `ChartRampDerivation.derive(String[] sourceHex, Kind kind)` returning `String[]` of seven lowercase six-digit hex strings **without** a leading `#`, in the concatenated form `AbstractSplineColorFrame.getColorRamps()` expects. `ChartRampDerivation.AMBER_SOURCE`, `TEAL_SOURCE`, `VARIANCE_SOURCE` are the handoff's stops. `ChartRampDerivation.Kind` is `SEQUENTIAL` or `DIVERGING`.

- [ ] **Step 1: Write the derivation helper**

Create `core/src/test/java/inetsoft/graph/aesthetic/ChartRampDerivation.java`:

```java
package inetsoft.graph.aesthetic;

import inetsoft.graph.internal.OKLab;

import java.awt.Color;

/**
 * The ENGINE §4 derivation rule. Lives in test sources because nothing derives a ramp at runtime -
 * the three house ramps are fixed, authored literally, and this class exists to prove the authored
 * hexes are still what the rule produces.
 */
public final class ChartRampDerivation {
   public enum Kind { SEQUENTIAL, DIVERGING }

   public static final Color LIGHT_SURFACE = new Color(0xF8F7F4);
   public static final Color DARK_SURFACE = new Color(0x252428);

   /** Every stop clears both surfaces by at least this much OKLab lightness. */
   public static final double DELTA_L_MIN = 0.12;
   /** Chroma probe: large enough that toColorInGamut always bisects down to the ceiling. */
   public static final double CHROMA_PROBE = 0.40;
   /** How much of the available chroma ceiling a fully-saturated stop takes. */
   public static final double CHROMA_FILL = 0.90;

   public static final String[] AMBER_SOURCE = {
      "FCEFE0", "F7DDBB", "EFBE87", "DE9A4A", "C1731A", "96520B", "5E3204"
   };
   public static final String[] TEAL_SOURCE = {
      "E2F4F2", "BCE6E1", "7FCEC7", "35AFA6", "1D8A86", "12665F", "0A3F3B"
   };
   public static final String[] VARIANCE_SOURCE = {
      "12665F", "35AFA6", "A8D8D3", "EFEDE7", "F2C89A", "DE9A4A", "96520B"
   };

   private ChartRampDerivation() {
   }

   /** The low end of the usable lightness band: dark surface plus the clearance. */
   public static double bandLow() {
      return lightnessOf(DARK_SURFACE) + DELTA_L_MIN;
   }

   /** The high end of the usable lightness band: light surface minus the clearance. */
   public static double bandHigh() {
      return lightnessOf(LIGHT_SURFACE) - DELTA_L_MIN;
   }

   public static double lightnessOf(Color c) {
      return OKLab.fromColor(c)[0];
   }

   /**
    * The rule.
    *
    * Sequential: keep each source stop's hue, remap its lightness linearly from the source range
    * onto the usable band, and give it the same relative share of the chroma available at its new
    * lightness. The near-white low end therefore becomes a light but chromatic colour instead of a
    * tint of the canvas.
    *
    * Diverging: hold lightness flat at the band's midpoint so lightness carries no signal, keep the
    * wings' hues, and run chroma from zero at the centre to the ceiling at each end. "At target"
    * becomes an achromatic mark rather than an absent one.
    */
   public static String[] derive(String[] sourceHex, Kind kind) {
      double[][] lch = new double[sourceHex.length][];

      for(int i = 0; i < sourceHex.length; i++) {
         lch[i] = OKLab.toLCH(OKLab.fromColor(Color.decode("#" + sourceHex[i])));
      }

      return kind == Kind.SEQUENTIAL ? deriveSequential(lch) : deriveDiverging(lch);
   }

   private static String[] deriveSequential(double[][] lch) {
      double srcLo = Double.MAX_VALUE;
      double srcHi = -Double.MAX_VALUE;
      double srcCMax = 0;

      for(double[] stop : lch) {
         srcLo = Math.min(srcLo, stop[0]);
         srcHi = Math.max(srcHi, stop[0]);
         srcCMax = Math.max(srcCMax, stop[1]);
      }

      String[] out = new String[lch.length];

      for(int i = 0; i < lch.length; i++) {
         double t = (lch[i][0] - srcLo) / (srcHi - srcLo);
         double l = bandLow() + t * (bandHigh() - bandLow());
         double hue = lch[i][2];
         double share = srcCMax == 0 ? 0 : lch[i][1] / srcCMax;

         out[i] = hex(OKLab.toColorInGamut(l, share * chromaCeiling(l, hue) * CHROMA_FILL, hue));
      }

      return out;
   }

   private static String[] deriveDiverging(double[][] lch) {
      int mid = lch.length / 2;
      double l = (bandLow() + bandHigh()) / 2;
      String[] out = new String[lch.length];

      for(int i = 0; i < lch.length; i++) {
         if(i == mid) {
            out[i] = hex(OKLab.toColorInGamut(l, 0, 0));
            continue;
         }

         double hue = lch[i][2];
         double share = Math.abs(i - mid) / (double) mid;

         out[i] = hex(OKLab.toColorInGamut(l, share * chromaCeiling(l, hue) * CHROMA_FILL, hue));
      }

      return out;
   }

   /** The most chroma sRGB can hold at this lightness and hue. */
   public static double chromaCeiling(double l, double hue) {
      Color capped = OKLab.toColorInGamut(l, CHROMA_PROBE, hue);
      return OKLab.toLCH(OKLab.fromColor(capped))[1];
   }

   public static String hex(Color c) {
      return String.format("%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue());
   }

   /** The seven stops joined as AbstractSplineColorFrame.getColorRamps() expects them. */
   public static String joined(String[] stops) {
      return String.join("", stops);
   }
}
```

- [ ] **Step 2: Write the failing constraint test**

Create `core/src/test/java/inetsoft/graph/aesthetic/ChartRampDerivationTest.java`. These tests encode C1–C4 — they are the requirement, not a description of the output:

```java
package inetsoft.graph.aesthetic;

import inetsoft.graph.internal.OKLab;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.awt.Color;

import static inetsoft.graph.aesthetic.ChartRampDerivation.*;
import static org.junit.jupiter.api.Assertions.*;

@Tag("core")
class ChartRampDerivationTest {
   @Test
   void everyStopClearsBothSurfaces() {
      double light = lightnessOf(LIGHT_SURFACE);
      double dark = lightnessOf(DARK_SURFACE);

      for(String[] ramp : allRamps()) {
         for(String stop : ramp) {
            double l = lightnessOf(Color.decode("#" + stop));
            assertTrue(Math.abs(l - light) >= DELTA_L_MIN,
                       stop + " is within " + DELTA_L_MIN + " of the light canvas");
            assertTrue(Math.abs(l - dark) >= DELTA_L_MIN,
                       stop + " is within " + DELTA_L_MIN + " of the dark surface");
         }
      }
   }

   @Test
   void sequentialRampsAreMonotonicAndSpanEnough() {
      for(String[] ramp : new String[][] { amber(), teal() }) {
         double first = lightnessOf(Color.decode("#" + ramp[0]));
         double last = lightnessOf(Color.decode("#" + ramp[ramp.length - 1]));

         for(int i = 1; i < ramp.length; i++) {
            double prev = lightnessOf(Color.decode("#" + ramp[i - 1]));
            double cur = lightnessOf(Color.decode("#" + ramp[i]));
            assertTrue(cur < prev, "lightness must fall monotonically, broke at stop " + i);
         }

         assertTrue(Math.abs(first - last) >= 0.30, "lightness span must be at least 0.30");
      }
   }

   @Test
   void varianceCarriesMagnitudeInChromaNotLightness() {
      String[] ramp = variance();
      double lo = Double.MAX_VALUE;
      double hi = -Double.MAX_VALUE;

      for(String stop : ramp) {
         double l = lightnessOf(Color.decode("#" + stop));
         lo = Math.min(lo, l);
         hi = Math.max(hi, l);
      }

      assertTrue(hi - lo <= 0.10, "Variance lightness must stay inside a 0.10 band, was " + (hi - lo));

      int mid = ramp.length / 2;
      assertEquals(0.0, chromaOf(ramp[mid]), 0.01, "the midpoint must be achromatic");

      for(int i = mid + 1; i < ramp.length; i++) {
         assertTrue(chromaOf(ramp[i]) > chromaOf(ramp[i - 1]), "chroma must rise outward, stop " + i);
      }

      for(int i = mid - 1; i >= 0; i--) {
         assertTrue(chromaOf(ramp[i]) > chromaOf(ramp[i + 1]), "chroma must rise outward, stop " + i);
      }
   }

   @Test
   void hueStaysFaithfulToTheSource() {
      assertHueMatches(AMBER_SOURCE, amber(), -1);
      assertHueMatches(TEAL_SOURCE, teal(), -1);
      assertHueMatches(VARIANCE_SOURCE, variance(), VARIANCE_SOURCE.length / 2);
   }

   @Test
   void everyStopIsInGamutWithLiveChroma() {
      for(String[] ramp : allRamps()) {
         for(String stop : ramp) {
            Color c = Color.decode("#" + stop);
            assertEquals(stop, hex(c), "a derived stop must round-trip through sRGB exactly");
         }
      }
   }

   private void assertHueMatches(String[] source, String[] derived, int exemptIndex) {
      for(int i = 0; i < source.length; i++) {
         if(i == exemptIndex) {
            continue;
         }

         double want = hueOf(source[i]);
         double got = hueOf(derived[i]);
         double delta = Math.abs(((want - got + 540) % 360) - 180);
         assertTrue(delta <= 5.0, "stop " + i + " drifted " + delta + "° from the source hue");
      }
   }

   private static double hueOf(String hex) {
      return OKLab.toLCH(OKLab.fromColor(Color.decode("#" + hex)))[2];
   }

   private static double chromaOf(String hex) {
      return OKLab.toLCH(OKLab.fromColor(Color.decode("#" + hex)))[1];
   }

   private static String[] amber() {
      return derive(AMBER_SOURCE, Kind.SEQUENTIAL);
   }

   private static String[] teal() {
      return derive(TEAL_SOURCE, Kind.SEQUENTIAL);
   }

   private static String[] variance() {
      return derive(VARIANCE_SOURCE, Kind.DIVERGING);
   }

   private static String[][] allRamps() {
      return new String[][] { amber(), teal(), variance() };
   }
}
```

- [ ] **Step 3: Run the test**

Run: `./mvnw test -pl core -Dtest=ChartRampDerivationTest`

Expected: all five pass. The rule is built to satisfy the constraints by construction, so a failure here is a real finding, not a tuning exercise. **If `sequentialRampsAreMonotonicAndSpanEnough` or `varianceCarriesMagnitudeInChromaNotLightness` fails, stop and report it — the spec says a C1-versus-C2 conflict comes back to the design rather than being settled here.**

- [ ] **Step 4: Print the derived stops and record them**

Add a temporary `@Test` that prints `joined(amber())`, `joined(teal())` and `joined(variance())`, run it, copy the three 42-character strings into the task notes, then delete the temporary test. These three strings are the input to Task 2 and must be reported back verbatim when this task completes.

Run: `./mvnw test -pl core -Dtest=ChartRampDerivationTest`

- [ ] **Step 5: Commit**

```bash
git add core/src/test/java/inetsoft/graph/aesthetic/ChartRampDerivation.java core/src/test/java/inetsoft/graph/aesthetic/ChartRampDerivationTest.java
git commit -m "Derive the house ramps against both surfaces

The handoff's stops are tuned to the light canvas alone: both sequential
low ends and Variance's midpoint are tints of it, so each becomes the
brightest thing on screen against the dark surface. The rule remaps
lightness into a band that clears both and gives each stop its source
share of the chroma available there.

Variance inverts. Its midpoint means at target, which the source spells as
dissolving into the canvas; one hex cannot do that for two surfaces. So
lightness goes flat and chroma carries magnitude from an achromatic centre.

C1-C4 are the tests, not a description of the output.

Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>"
```

---

### Task 2: The three frame classes, wrappers and the drift guard

**Files:**
- Create: `core/src/main/java/inetsoft/graph/aesthetic/AmberColorFrame.java`
- Create: `core/src/main/java/inetsoft/graph/aesthetic/TealColorFrame.java`
- Create: `core/src/main/java/inetsoft/graph/aesthetic/VarianceColorFrame.java`
- Create: `core/src/main/java/inetsoft/uql/viewsheet/graph/aesthetic/AmberColorFrameWrapper.java`
- Create: `core/src/main/java/inetsoft/uql/viewsheet/graph/aesthetic/TealColorFrameWrapper.java`
- Create: `core/src/main/java/inetsoft/uql/viewsheet/graph/aesthetic/VarianceColorFrameWrapper.java`
- Modify: `core/src/main/java/inetsoft/uql/viewsheet/graph/aesthetic/VisualFrameWrapper.java` — add three cases to `createWrapper`, in alphabetical position
- Create: `core/src/test/java/inetsoft/graph/aesthetic/HouseRampTest.java`

**Interfaces:**
- Consumes: Task 1's three 42-character stop strings; `ChartRampDerivation.derive` and `joined` for the drift guard.
- Produces: `AmberColorFrame`, `TealColorFrame`, `VarianceColorFrame` in `inetsoft.graph.aesthetic`, each `extends AbstractSplineColorFrame`. Their wrappers in `inetsoft.uql.viewsheet.graph.aesthetic`, each `extends LinearColorFrameWrapper`.

- [ ] **Step 1: Write the failing test**

Create `core/src/test/java/inetsoft/graph/aesthetic/HouseRampTest.java`. The round-trip test is the one that would have caught the live `"BluesColorFrameW"` typo, so it is written first and covers all three:

```java
package inetsoft.graph.aesthetic;

import inetsoft.uql.viewsheet.graph.aesthetic.*;
import inetsoft.util.Tool;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;

import java.io.*;

import static inetsoft.graph.aesthetic.ChartRampDerivation.*;
import static org.junit.jupiter.api.Assertions.*;

@Tag("core")
class HouseRampTest {
   @Test
   void authoredStopsAgreeWithTheDerivationRule() throws Exception {
      assertEquals(joined(derive(AMBER_SOURCE, Kind.SEQUENTIAL)), rampOf(AmberColorFrame.class));
      assertEquals(joined(derive(TEAL_SOURCE, Kind.SEQUENTIAL)), rampOf(TealColorFrame.class));
      assertEquals(joined(derive(VARIANCE_SOURCE, Kind.DIVERGING)), rampOf(VarianceColorFrame.class));
   }

   @Test
   void eachRampDeclaresExactlySevenStops() throws Exception {
      for(String ramp : new String[] { rampOf(AmberColorFrame.class),
                                       rampOf(TealColorFrame.class),
                                       rampOf(VarianceColorFrame.class) })
      {
         assertEquals(42, ramp.length(), "seven six-digit stops");
      }
   }

   @Test
   void eachFrameSurvivesTheWrapperRoundTrip() throws Exception {
      assertRoundTrips(new AmberColorFrame(), AmberColorFrameWrapper.class);
      assertRoundTrips(new TealColorFrame(), TealColorFrameWrapper.class);
      assertRoundTrips(new VarianceColorFrame(), VarianceColorFrameWrapper.class);
   }

   private void assertRoundTrips(LinearColorFrame frame, Class<?> expected) throws Exception {
      VisualFrameWrapper wrapper = VisualFrameWrapper.wrap(frame);
      assertSame(expected, wrapper.getClass(), "wrap() must resolve the declared switch case");

      StringWriter buf = new StringWriter();
      wrapper.writeXML(new PrintWriter(buf));

      Document doc = Tool.parseXML(new StringReader(buf.toString()));
      VisualFrameWrapper parsed = VisualFrameWrapper.createVisualFrame(doc.getDocumentElement());

      assertSame(expected, parsed.getClass(), "parse must resolve the same wrapper");
      assertEquals(frame.getClass(), parsed.getVisualFrame().getClass());
   }

   /**
    * getColorRamps() is protected, and the house frames live in a different package from this test's
    * AbstractSplineColorFrame, so read the table reflectively rather than widening production access
    * for a test.
    */
   private static String rampOf(Class<? extends AbstractSplineColorFrame> cls) throws Exception {
      java.lang.reflect.Method m =
         AbstractSplineColorFrame.class.getDeclaredMethod("getColorRamps");
      m.setAccessible(true);

      String[] ramps = (String[]) m.invoke(cls.getDeclaredConstructor().newInstance());
      assertEquals(1, ramps.length, "a house ramp declares exactly one stop table");

      return ramps[0];
   }
}
```

Add `import inetsoft.graph.rgb.AbstractSplineColorFrame;` — `AbstractSplineColorFrame` is in `inetsoft.graph.rgb`, not in the frames' own package.

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw test -pl core -Dtest=HouseRampTest`
Expected: FAIL — `AmberColorFrame` does not exist, compilation error.

- [ ] **Step 3: Write the three frames**

Create `core/src/main/java/inetsoft/graph/aesthetic/AmberColorFrame.java`, substituting Task 1's Amber string for `<AMBER_STOPS>`. Copy the GNU AGPL header from `BluesColorFrame.java` verbatim into each new file:

```java
package inetsoft.graph.aesthetic;

import inetsoft.graph.rgb.AbstractSplineColorFrame;

/**
 * A sequential house ramp, tuned to clear both the light canvas and the dark surface. Its stops are
 * derived rather than picked; ChartRampDerivation holds the rule and HouseRampTest pins this table
 * to it.
 *
 * @version 15.0
 * @author InetSoft Technology
 */
public class AmberColorFrame extends AbstractSplineColorFrame {
   @Override
   protected String[] getColorRamps() {
      return new String[] { "<AMBER_STOPS>" };
   }

   private static final long serialVersionUID = 1L;
}
```

`TealColorFrame.java` is identical but for the class name and `<TEAL_STOPS>`.

`VarianceColorFrame.java` is identical but for the class name, `<VARIANCE_STOPS>`, and this class comment:

```java
/**
 * A diverging house ramp. Lightness is held flat so it carries no signal: chroma carries magnitude
 * outward from an achromatic centre and hue carries direction. "At target" therefore reads as a grey
 * mark rather than as an absent one, which is what lets one table serve both surfaces.
 *
 * @version 15.0
 * @author InetSoft Technology
 */
```

- [ ] **Step 4: Write the three wrappers**

Create `core/src/main/java/inetsoft/uql/viewsheet/graph/aesthetic/AmberColorFrameWrapper.java`, with the AGPL header copied from `BluesColorFrameWrapper.java`:

```java
package inetsoft.uql.viewsheet.graph.aesthetic;

import inetsoft.graph.aesthetic.AmberColorFrame;
import inetsoft.graph.aesthetic.VisualFrame;

/**
 * This class defines a sequential house color frame for continuous numeric values.
 *
 * @version 15.0
 * @author InetSoft Technology
 */
public class AmberColorFrameWrapper extends LinearColorFrameWrapper {
   /**
    * Create the corresponding frame.
    */
   @Override
   protected VisualFrame createVisualFrame() {
      return new AmberColorFrame();
   }
}
```

`TealColorFrameWrapper` and `VarianceColorFrameWrapper` are identical but for the class and frame names.

- [ ] **Step 5: Add the three switch cases**

In `core/src/main/java/inetsoft/uql/viewsheet/graph/aesthetic/VisualFrameWrapper.java`, inside `createWrapper`, add each case in its alphabetical position among the existing ones. **Spell the case with no suffix** — `stripInnerName` has already removed `"Wrapper"` before the switch sees the name. The neighbouring `case "BluesColorFrameW":` is a live typo that silently falls through to `Class.forName`; do not copy it and do not fix it here.

```java
      case "AmberColorFrame":
         return new AmberColorFrameWrapper();
```

```java
      case "TealColorFrame":
         return new TealColorFrameWrapper();
```

```java
      case "VarianceColorFrame":
         return new VarianceColorFrameWrapper();
```

- [ ] **Step 6: Run test to verify it passes**

Run: `./mvnw test -pl core -Dtest=HouseRampTest`
Expected: PASS, 3 tests.

- [ ] **Step 7: Commit**

```bash
git add core/src/main/java/inetsoft/graph/aesthetic/AmberColorFrame.java core/src/main/java/inetsoft/graph/aesthetic/TealColorFrame.java core/src/main/java/inetsoft/graph/aesthetic/VarianceColorFrame.java core/src/main/java/inetsoft/uql/viewsheet/graph/aesthetic/AmberColorFrameWrapper.java core/src/main/java/inetsoft/uql/viewsheet/graph/aesthetic/TealColorFrameWrapper.java core/src/main/java/inetsoft/uql/viewsheet/graph/aesthetic/VarianceColorFrameWrapper.java core/src/main/java/inetsoft/uql/viewsheet/graph/aesthetic/VisualFrameWrapper.java core/src/test/java/inetsoft/graph/aesthetic/HouseRampTest.java
git commit -m "Add the Amber, Teal and Variance ramps

Three AbstractSplineColorFrame subclasses holding the derived stops, their
wrappers, and the switch cases that let them resolve by class name through
VisualFrameWrapper.

The round-trip test earns its place: the neighbouring Blues case reads
'BluesColorFrameW' and has never matched, because stripInnerName removes
the Wrapper suffix before the switch sees the name. It falls through to
Class.forName and resolves anyway, so nothing ever failed. Left alone;
recorded in the design.

Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>"
```

---

### Task 3: Model factories and the TypeScript models

**Files:**
- Modify: `core/src/main/java/inetsoft/web/binding/service/graph/aesthetic/ColorFrameModelFactory.java`
- Create: three model classes beside the existing linear ones — find `BluesColorModel.java` with `find core/src/main/java -name BluesColorModel.java` and mirror its package and shape
- Modify: `web/projects/portal/src/app/common/data/visual-frame-model.ts`

**Interfaces:**
- Consumes: Task 2's `AmberColorFrameWrapper`, `TealColorFrameWrapper`, `VarianceColorFrameWrapper`.
- Produces: Java `AmberColorModel`, `TealColorModel`, `VarianceColorModel`; TypeScript `AmberColorModel`, `TealColorModel`, `VarianceColorModel` exported from `visual-frame-model.ts`. The TypeScript class names are what Task 5's pane arrays and Task 4's `getSrc` switch key on, as the strings `"AmberColorModel"`, `"TealColorModel"`, `"VarianceColorModel"`.

- [ ] **Step 1: Write the three Java models**

Create `core/src/main/java/inetsoft/web/binding/model/graph/aesthetic/AmberColorModel.java`, with the AGPL header copied from `BluesColorModel.java` in the same directory:

```java
package inetsoft.web.binding.model.graph.aesthetic;

import inetsoft.graph.aesthetic.AmberColorFrame;
import inetsoft.graph.aesthetic.VisualFrame;
import inetsoft.uql.viewsheet.graph.aesthetic.AmberColorFrameWrapper;

public class AmberColorModel extends ColorFrameModel {
   public AmberColorModel() {
   }

   public AmberColorModel(AmberColorFrameWrapper wrapper) {
      super(wrapper);
   }

   @Override
   public VisualFrame createVisualFrame() {
      return new AmberColorFrame();
   }
}
```

`TealColorModel.java` and `VarianceColorModel.java` are identical but for the three substituted type names in each.

- [ ] **Step 2: Add the three factories**

In `ColorFrameModelFactory.java`, add three inner classes in alphabetical position among the existing ones, each annotated `@Component` exactly as its neighbours are:

```java
   @Component
   public static final class AmberColorFactory
      extends ColorFrameModelFactory<AmberColorFrameWrapper, AmberColorModel>
   {
      @Override
      public Class<AmberColorFrameWrapper> getVisualFrameWrapperClass() {
         return AmberColorFrameWrapper.class;
      }

      @Override
      public AmberColorModel createVisualFrameModel(AmberColorFrameWrapper wrapper) {
         return new AmberColorModel(wrapper);
      }

      @Override
      protected VisualFrame getVisualFrame() {
         return new AmberColorFrame();
      }
   }
```

Repeat for `TealColorFactory` and `VarianceColorFactory`, substituting the type names throughout.

- [ ] **Step 3: Add the three TypeScript models**

In `web/projects/portal/src/app/common/data/visual-frame-model.ts`, beside `BluesColorModel` (around line 244) and in alphabetical position among the linear models:

```typescript
export class AmberColorModel extends ColorFrameModel {
   clazz: string = "inetsoft.web.binding.model.graph.aesthetic.AmberColorModel";
}

export class TealColorModel extends ColorFrameModel {
   clazz: string = "inetsoft.web.binding.model.graph.aesthetic.TealColorModel";
}

export class VarianceColorModel extends ColorFrameModel {
   clazz: string = "inetsoft.web.binding.model.graph.aesthetic.VarianceColorModel";
}
```

The `clazz` string must match the Java model's fully-qualified name exactly — it is what `switchColorModel` and the pane's radio `value` bindings compare against.

- [ ] **Step 4: Verify it compiles**

Run: `./mvnw test -pl core -Dtest=HouseRampTest`
Expected: PASS — this only confirms `core` still compiles with the new factories.

Run: `cd web && npx tsc --noEmit -p projects/portal/tsconfig.app.json`
Expected: no errors. If that tsconfig path does not exist, run `ls web/projects/portal/tsconfig*` and use the app config found there.

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/inetsoft/web/binding/ web/projects/portal/src/app/common/data/visual-frame-model.ts
git commit -m "Carry the house ramps through the model layer

Three ColorFrameModelFactory inner classes and their Java and TypeScript
models, so the new frames survive the trip to the binding pane the same way
the twenty-seven ColorBrewer ramps do.

Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>"
```

---

### Task 4: Dropdown swatches

Each ramp's entry in the dropdown is a static 80×19 PNG, not a rendered gradient. A ramp with no asset renders a broken image rather than failing, so this is a deliverable, and generating the swatch from the authored stops also makes it a visual check on Task 1.

**Files:**
- Create: `web/projects/portal/src/assets/Amber.png`
- Create: `web/projects/portal/src/assets/Teal.png`
- Create: `web/projects/portal/src/assets/Variance.png`
- Modify: `web/projects/portal/src/app/binding/editor/chart/aesthetic/linear-color-dropdown.component.ts` — three `getSrc()` cases

**Interfaces:**
- Consumes: Task 1's stop strings; Task 3's TypeScript model names.
- Produces: three assets at the paths above.

- [ ] **Step 1: Confirm the existing swatch geometry**

Run: `cd web/projects/portal/src/assets && python -c "import struct;d=open('Blues.png','rb').read();print(struct.unpack('>II',d[16:24]))"`
Expected: `(80, 19)`. Every new swatch must match.

- [ ] **Step 2: Generate the three swatches**

Write a throwaway script in the scratchpad that renders each ramp as an 80×19 PNG by interpolating its seven stops across the 80-pixel width, using the same B-spline basis the frame uses at render if convenient, or a linear interpolation between stops if not — the swatch is an indication, not a specification. Substitute Task 1's stop strings.

```python
# scratchpad only - do not commit
from PIL import Image

RAMPS = {
    "Amber": "<AMBER_STOPS>",
    "Teal": "<TEAL_STOPS>",
    "Variance": "<VARIANCE_STOPS>",
}

def stops(s):
    return [tuple(int(s[i*6+k*2:i*6+k*2+2], 16) for k in range(3)) for i in range(len(s)//6)]

for name, table in RAMPS.items():
    cols = stops(table)
    img = Image.new("RGB", (80, 19))
    for x in range(80):
        t = x / 79 * (len(cols) - 1)
        i = min(int(t), len(cols) - 2)
        f = t - i
        px = tuple(round(cols[i][c] + (cols[i+1][c] - cols[i][c]) * f) for c in range(3))
        for y in range(19):
            img.putpixel((x, y), px)
    img.save(f"{name}.png")
```

If Pillow is unavailable, run `pip install Pillow` first. Copy the three files to `web/projects/portal/src/assets/`.

- [ ] **Step 3: Add the three getSrc cases**

In `linear-color-dropdown.component.ts`, add to the `getSrc(frame: string)` switch, in alphabetical position among the existing cases:

```typescript
      case "AmberColorModel":
         return "assets/Amber.png";
```

```typescript
      case "TealColorModel":
         return "assets/Teal.png";
```

```typescript
      case "VarianceColorModel":
         return "assets/Variance.png";
```

- [ ] **Step 4: Verify the swatches**

Run: `cd web/projects/portal/src/assets && for f in Amber Teal Variance; do python -c "import struct,sys;d=open(sys.argv[1],'rb').read();print(sys.argv[1], struct.unpack('>II',d[16:24]))" $f.png; done`
Expected: each reports `(80, 19)`.

Open all three and look at them. Amber and Teal must read as smooth sequential ramps with no near-white end; Variance must be grey in the middle and chromatic at both ends. **If Variance's centre does not read as neutral, Task 1's derivation is wrong — stop and report rather than adjusting the image.**

- [ ] **Step 5: Commit**

```bash
git add web/projects/portal/src/assets/Amber.png web/projects/portal/src/assets/Teal.png web/projects/portal/src/assets/Variance.png web/projects/portal/src/app/binding/editor/chart/aesthetic/linear-color-dropdown.component.ts
git commit -m "Add the house ramp swatches

The dropdown renders a static PNG per ramp rather than a gradient, so a new
ramp without one shows a broken image. Generated from the authored stops,
which also makes each swatch a visual check on the derivation.

Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>"
```

---

### Task 5: The `defaultLinearFrame` resolver

**Files:**
- Modify: `core/src/main/java/inetsoft/uql/viewsheet/internal/VSChartPaletteDefaults.java`
- Modify: `core/src/test/java/inetsoft/uql/viewsheet/internal/VSChartPaletteDefaultsTest.java`

**Interfaces:**
- Consumes: Task 2's `TealColorFrame`; existing `VizContext` with public final fields `modern`, `dark`, `density` and factories `LEGACY`, `ofGate()`, `of(VSAssemblyInfo)`, `of(VizMark)`.
- Produces: `public static LinearColorFrame VSChartPaletteDefaults.defaultLinearFrame(VizContext ctx)` — `TealColorFrame` when `ctx.modern`, `BluesColorFrame` otherwise. Task 6 calls this at every seed site.

- [ ] **Step 1: Write the failing test**

Append to `core/src/test/java/inetsoft/uql/viewsheet/internal/VSChartPaletteDefaultsTest.java`:

```java
   @Test
   void modernChartsSeedTealAndClassicChartsSeedBlues() {
      assertInstanceOf(TealColorFrame.class,
                       VSChartPaletteDefaults.defaultLinearFrame(VizContext.of(VizMark.MODERN_LIGHT)));
      assertInstanceOf(TealColorFrame.class,
                       VSChartPaletteDefaults.defaultLinearFrame(VizContext.of(VizMark.MODERN_DARK)));
      assertInstanceOf(BluesColorFrame.class,
                       VSChartPaletteDefaults.defaultLinearFrame(VizContext.LEGACY));
   }

   @Test
   void aNullContextSeedsTheLegacyRamp() {
      assertInstanceOf(BluesColorFrame.class, VSChartPaletteDefaults.defaultLinearFrame(null));
   }
```

Add `import inetsoft.graph.aesthetic.BluesColorFrame;` and `import inetsoft.graph.aesthetic.TealColorFrame;` if absent. Confirm the `VizMark` constant names first with `grep -n "MODERN" core/src/main/java/inetsoft/uql/viewsheet/internal/VizMark.java` and use whatever that file declares.

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw test -pl core -Dtest=VSChartPaletteDefaultsTest`
Expected: FAIL — `defaultLinearFrame` is not defined.

- [ ] **Step 3: Write the resolver**

In `VSChartPaletteDefaults.java`, add beside `activePalette` and `companionOf`:

```java
   /**
    * The linear colour frame a chart's measure-to-colour binding is born on.
    *
    * A modern chart takes the house sequential ramp; everything else keeps the ColorBrewer ramp that
    * has been the default since 12.3. There is no dark branch: one table serves both surfaces, which
    * is why the frame can be persisted by class name at all - see the ramps design, decision 2.
    *
    * A null context seeds legacy rather than throwing. A seed site that cannot name its context is a
    * site that should not silently modernize a chart.
    */
   public static LinearColorFrame defaultLinearFrame(VizContext ctx) {
      return ctx != null && ctx.modern ? new TealColorFrame() : new BluesColorFrame();
   }
```

Add the imports for `LinearColorFrame`, `TealColorFrame` and `BluesColorFrame`.

- [ ] **Step 4: Run test to verify it passes**

Run: `./mvnw test -pl core -Dtest=VSChartPaletteDefaultsTest`
Expected: PASS, including every pre-existing test in the class.

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/inetsoft/uql/viewsheet/internal/VSChartPaletteDefaults.java core/src/test/java/inetsoft/uql/viewsheet/internal/VSChartPaletteDefaultsTest.java
git commit -m "Resolve the seeded linear frame from the chart's context

One resolver beside activePalette and companionOf, so the eight hard-coded
BluesColorFrame sites have something to call. A modern chart takes Teal;
everything else, including a null context, keeps Blues.

Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>"
```

---

### Task 6: Thread the context to the seed sites

The largest task by file count and the one where a missed site fails quietly — seeding Blues on a chart that should have had Teal. The ten construction sites are enumerated so none is rediscovered.

**Files:**
- Modify: `core/src/main/java/inetsoft/report/internal/graph/ChangeChartTypeProcessor.java` — context-taking constructor overloads; seed sites at 166, 863, 1176, 1250, 1344, 1420
- Modify: `core/src/main/java/inetsoft/report/internal/graph/ChangeChartProcessor.java` — `fixColorField`; seed site at 234
- Modify: `core/src/main/java/inetsoft/report/composition/graph/GraphUtil.java` — `fixVisualFrame`; seed site at 951
- Modify: `core/src/main/java/inetsoft/report/internal/graph/ChangeChartDataProcessor.java:60`
- Modify: `core/src/main/java/inetsoft/uql/viewsheet/internal/ChartVSAssemblyInfo.java:2574,2585`
- Modify: `core/src/main/java/inetsoft/web/binding/controller/ChangeChartTypeService.java:152,170`
- Modify: `core/src/main/java/inetsoft/uql/viewsheet/graph/ChartDcProcessor.java:293,850`
- Modify: `core/src/main/java/inetsoft/web/composer/vs/dialog/DateComparisonDialogService.java:197`
- Modify: `core/src/main/java/inetsoft/report/internal/ChartElementDef.java:626,644`
- Create: `core/src/test/java/inetsoft/report/internal/graph/SeededLinearFrameTest.java`

**Interfaces:**
- Consumes: Task 5's `VSChartPaletteDefaults.defaultLinearFrame(VizContext)`.
- Produces: nothing later tasks call. Line numbers are from the branch point — confirm each with `grep -n "new BluesColorFrame()"` before editing, since earlier tasks have not touched these files but the numbers are advisory.

- [ ] **Step 1: Locate every seed site**

Run: `grep -rn "new BluesColorFrame()" core/src/main/java`
Expected: nine hits — six in `ChangeChartTypeProcessor`, one each in `ChangeChartProcessor`, `GraphUtil` and `DensityForm`. Record the real line numbers.

**`DensityForm` is out of scope.** It is a field initialiser on a graph-level form with no reachable assembly, deferred in the design alongside the brushing slice's density contour item. Leave it exactly as it is.

- [ ] **Step 2: Write the failing test**

Create `core/src/test/java/inetsoft/report/internal/graph/SeededLinearFrameTest.java`:

```java
package inetsoft.report.internal.graph;

import inetsoft.graph.aesthetic.*;
import inetsoft.report.composition.graph.GraphUtil;
import inetsoft.uql.asset.AggregateFormula;
import inetsoft.uql.viewsheet.VSAggregateRef;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.graph.internal.GTool;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@Tag("core")
class SeededLinearFrameTest {
   /**
    * Guards the fixture itself. fixVisualFrame only reaches the linear seed when the ref is a
    * measure, so a fixture that drifts categorical would make every other test here pass by
    * never running the branch under test.
    */
   @Test
   void theFixtureReachesTheMeasureBranch() {
      assertFalse(GraphUtil.isCategorical(measureColorRef().getDataRef()),
                  "fixture must read as a measure or the seed branch is never reached");
   }

   @Test
   void graphUtilSeedsTealForAModernChart() {
      assertInstanceOf(TealColorFrame.class, seedThrough(VizContext.of(VizMark.MODERN_LIGHT)));
   }

   @Test
   void graphUtilSeedsTealForAModernDarkChart() {
      assertInstanceOf(TealColorFrame.class, seedThrough(VizContext.of(VizMark.MODERN_DARK)));
   }

   @Test
   void graphUtilSeedsBluesForAClassicChart() {
      assertInstanceOf(BluesColorFrame.class, seedThrough(VizContext.LEGACY));
   }

   private VisualFrame seedThrough(VizContext ctx) {
      AestheticRef ref = measureColorRef();

      GraphUtil.fixVisualFrame(ref, ChartConstants.AESTHETIC_COLOR, GraphTypes.CHART_BAR,
                               new VSChartInfo(), ctx);

      return ref.getVisualFrame();
   }

   /** A colour aesthetic bound to a measure, carrying no frame yet. */
   private AestheticRef measureColorRef() {
      VSAggregateRef aggr = new VSAggregateRef();
      aggr.setColumnValue("Total");
      aggr.setFormula(AggregateFormula.SUM);

      VSAestheticRef ref = new VSAestheticRef();
      ref.setDataRef(aggr);

      return ref;
   }
}
```

Confirm the `VizMark` constant names with `grep -n "MODERN" core/src/main/java/inetsoft/uql/viewsheet/internal/VizMark.java` and use whatever that file declares. `ChartConstants` and `GraphTypes` may need their imports adjusted to the packages the surrounding tests use — resolve them by compiling, not by guessing. Drop the `GTool` import if unused.

- [ ] **Step 3: Add the context parameters**

`ChangeChartTypeProcessor`: add a `private final VizContext vizContext;` field, add a context-taking overload beside each existing constructor, and have the existing overloads delegate with `VizContext.LEGACY`. Replace each of the six `new BluesColorFrame()` with `VSChartPaletteDefaults.defaultLinearFrame(vizContext)`.

`ChangeChartProcessor`: change `public void fixColorField(ChartBindable info, int type)` to `public void fixColorField(ChartBindable info, int type, VizContext ctx)`, keep a two-argument overload delegating with `VizContext.LEGACY`, and replace the seed with `VSChartPaletteDefaults.defaultLinearFrame(ctx)`.

`GraphUtil`: change `public static boolean fixVisualFrame(AestheticRef ref, int type, int chartType, ChartInfo info)` to take a trailing `VizContext ctx`, keep a four-argument overload delegating with `VizContext.LEGACY`, and replace the seed the same way.

Retaining the no-context overloads is deliberate: a missed caller falls back to the pre-slice behaviour rather than to something new. It is also why Step 4 enumerates every caller rather than trusting the compiler.

- [ ] **Step 4: Update the ten callers**

Run: `grep -rn "new ChangeChartTypeProcessor(" core/src/main/java` and confirm ten hits. Then pass a context at each:

| Caller | Pass |
|---|---|
| `ChartVSAssemblyInfo:2574,2585` | `VizContext.of(this)` |
| `ChangeChartTypeService:152,170` | `VizContext.of(<the assembly info already in scope>)` |
| `ChartDcProcessor:293,850` | `VizContext.of(<the chart assembly's info>)` |
| `DateComparisonDialogService:197` | `VizContext.of(assemblyInfo)` — already cast to `ChartVSAssemblyInfo` |
| `ChartElementDef:626,644` | `VizContext.LEGACY` — the report path, truthfully |
| `ChangeChartDataProcessor:60` | the context from its own new constructor parameter |

`ChangeChartDataProcessor` holds only a `ChartInfo`, so give it the same treatment: a `VizContext` field, a context-taking constructor overload, existing overloads delegating with `LEGACY`, and pass the field through at `:60`. Then run `grep -rn "new ChangeChartDataProcessor(" core/src/main/java` and give each of its callers a context by the same rules — an assembly's context where one is in scope, `LEGACY` on the report path.

Also update the callers of `fixColorField` and `fixVisualFrame` that have an assembly in scope: run `grep -rn "fixColorField(\|fixVisualFrame(" core/src/main/java` and pass a real context wherever one is reachable. A caller with no assembly keeps the legacy overload.

- [ ] **Step 5: Run the tests**

Run: `./mvnw test -pl core -Dtest=SeededLinearFrameTest`
Expected: PASS, 2 tests.

Run: `./mvnw test -pl core`
Expected: the full `core` suite green. This task changes signatures used across the graph layer, so the full suite is the check, not a scoped run. Report the exact pass/fail counts.

- [ ] **Step 6: Commit**

```bash
git add core/src/main/java/inetsoft/report/ core/src/main/java/inetsoft/uql/viewsheet/ core/src/main/java/inetsoft/web/ core/src/test/java/inetsoft/report/internal/graph/SeededLinearFrameTest.java
git commit -m "Seed a modern chart's measure binding on Teal

The default measure-to-colour frame was BluesColorFrame, hard-coded at
eight sites, so a modern chart with a re-tuned categorical palette still
took a 2010-era ColorBrewer ramp the moment a measure was bound to colour.
Each site now asks the chart's context.

The categorical side survives a stale org gate because applyModernPalette
re-resolves from live CSS on every render. A ramp is persisted by class
name and gets no second chance, so the context has to be right at seed
time - which is why this threads the assembly's mark rather than reading
the gate.

DensityForm keeps Blues: a field initialiser on a graph-level form with no
assembly to ask. Deferred with the brushing slice's contour item.

Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>"
```

---

### Task 7: Picker policy and its endpoint

**Files:**
- Modify: `core/src/main/java/inetsoft/uql/viewsheet/internal/VSChartPaletteDefaults.java` — add `hiddenLinearFrames`
- Modify: `core/src/main/java/inetsoft/web/binding/VSChartBindingController.java` — add the endpoint beside `getColorPalettes`
- Modify: `core/src/test/java/inetsoft/uql/viewsheet/internal/VSChartPaletteDefaultsTest.java`

**Interfaces:**
- Consumes: the controller's existing private `pickerContext(String vsId, String assemblyName, Principal principal)` helper and `VSChartBindingService.getChartVizMark`.
- Produces: `public static Set<String> VSChartPaletteDefaults.hiddenLinearFrames(VizContext ctx)` returning **TypeScript model names**, not Java class names — the client keys on `"BluesColorModel"`. And `GET /api/composer/chart/hiddenlinearframes` returning `String[]`, which Task 8 consumes.

- [ ] **Step 1: Write the failing test**

Append to `VSChartPaletteDefaultsTest.java`:

```java
   @Test
   void aClassicChartHidesNoRamps() {
      assertTrue(VSChartPaletteDefaults.hiddenLinearFrames(VizContext.LEGACY).isEmpty());
   }

   @Test
   void aModernChartHidesTheSucceededFamiliesOnly() {
      Set<String> hidden = VSChartPaletteDefaults.hiddenLinearFrames(VizContext.of(VizMark.MODERN_LIGHT));

      assertEquals(16, hidden.size(), "six single hue, nine diverging, and Heat");

      // the single-hue family, succeeded by Amber and Teal
      assertTrue(hidden.containsAll(Set.of("BluesColorModel", "GreensColorModel", "GreysColorModel",
                                           "OrangesColorModel", "PurplesColorModel", "RedsColorModel")));
      // the diverging family, succeeded by Variance
      assertTrue(hidden.containsAll(Set.of("BrBGColorModel", "PiYGColorModel", "PRGnColorModel",
                                           "PuOrColorModel", "RdBuColorModel", "RdGyColorModel",
                                           "RdYlGnColorModel", "SpectralColorModel",
                                           "RdYlBuColorModel")));
      // Heat, succeeded by Amber
      assertTrue(hidden.contains("HeatColorModel"));

      // multi-hue has no house successor and survives intact
      assertFalse(hidden.contains("BuGnColorModel"));
      assertFalse(hidden.contains("YlOrRdColorModel"));
      // the house ramps are never hidden from the charts they were built for
      assertFalse(hidden.contains("AmberColorModel"));
      assertFalse(hidden.contains("TealColorModel"));
      assertFalse(hidden.contains("VarianceColorModel"));
      // Custom is retained by ENGINE §4
      assertFalse(hidden.contains("GradientColorModel"));
   }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw test -pl core -Dtest=VSChartPaletteDefaultsTest`
Expected: FAIL — `hiddenLinearFrames` is not defined.

- [ ] **Step 3: Write the policy**

In `VSChartPaletteDefaults.java`, beside `hiddenPaletteNames`:

```java
   /**
    * The linear colour frames a picker hides, by client model name.
    *
    * Hidden at family granularity: a family the house set succeeds goes, a family it does not stays.
    * Amber and Teal succeed the single hues and Heat; Variance succeeds the diverging set. Multi-hue
    * has no house member and survives whole, and so does Custom, which ENGINE §4 keeps for brand
    * matching. Greys and Purples go with their family and have no individual successor - the linear
    * analogue of the categorical Gray, which the handoff also retires with none.
    *
    * Hiding is a display concern. Nothing here is removed from resolution, so a chart already on any
    * of these keeps rendering it and keeps showing it selected.
    */
   public static Set<String> hiddenLinearFrames(VizContext ctx) {
      if(ctx == null || !ctx.modern) {
         return Set.of();
      }

      return HIDDEN_LINEAR_FRAMES;
   }

   private static final Set<String> HIDDEN_LINEAR_FRAMES = Set.of(
      // single hue, succeeded by Amber and Teal
      "BluesColorModel", "GreensColorModel", "GreysColorModel",
      "OrangesColorModel", "PurplesColorModel", "RedsColorModel",
      // diverging, succeeded by Variance
      "BrBGColorModel", "PiYGColorModel", "PRGnColorModel", "PuOrColorModel",
      "RdBuColorModel", "RdGyColorModel", "RdYlBuColorModel", "RdYlGnColorModel",
      "SpectralColorModel",
      // Heat, succeeded by Amber
      "HeatColorModel");
```

- [ ] **Step 4: Add the endpoint**

In `VSChartBindingController.java`, directly after `getColorPalettes`:

```java
   @RequestMapping(value = "/api/composer/chart/hiddenlinearframes", method = RequestMethod.GET)
   @HandleExceptions
   @SwitchOrg
   public String[] getHiddenLinearFrames(
      @OrganizationID String orgId,
      @RequestParam(required = false, value = "vsId") String vsId,
      @RequestParam(required = false, value = "assemblyName") String assemblyName,
      Principal principal)
      throws Exception
   {
      return VSChartPaletteDefaults
         .hiddenLinearFrames(pickerContext(vsId, assemblyName, principal))
         .toArray(new String[0]);
   }
```

`pickerContext` already carries the absent-assembly and unresolvable-mark fallbacks and its comment explains both; do not duplicate that reasoning here.

- [ ] **Step 5: Run test to verify it passes**

Run: `./mvnw test -pl core -Dtest=VSChartPaletteDefaultsTest`
Expected: PASS, all tests in the class.

- [ ] **Step 6: Commit**

```bash
git add core/src/main/java/inetsoft/uql/viewsheet/internal/VSChartPaletteDefaults.java core/src/main/java/inetsoft/web/binding/VSChartBindingController.java core/src/test/java/inetsoft/uql/viewsheet/internal/VSChartPaletteDefaultsTest.java
git commit -m "Decide which ramps a modern chart is offered

Hidden at family granularity: a family the house set succeeds goes, one it
does not stays. Multi-hue keeps all twelve because nothing replaces them,
and Custom stays because ENGINE §4 keeps it.

The policy sits beside hiddenPaletteNames so both pickers answer to one
class, and the endpoint reuses the controller's existing pickerContext,
which already carries the absent-assembly and unresolvable-mark fallbacks.

Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>"
```

---

### Task 8: Filter the pane

**Files:**
- Modify: `web/projects/portal/src/app/binding/editor/chart/aesthetic/linear-color-pane.component.ts`
- Modify: `web/projects/portal/src/app/binding/editor/chart/aesthetic/linear-color-pane.component.html`
- Modify: `web/projects/portal/src/app/binding/editor/chart/aesthetic/color-field-mc.component.html:105`
- Modify: `web/projects/portal/src/app/binding/editor/chart/aesthetic/linear-color-pane.component.tl.spec.ts`

**Interfaces:**
- Consumes: Task 7's `GET /api/composer/chart/hiddenlinearframes`; Task 3's model names.
- Produces: nothing downstream.

- [ ] **Step 1: Read the existing fetch pattern**

Read `categorical-color-pane.component.ts:90-125`. It builds `HttpParams`, sets `vsId` and `assemblyName` only when truthy, and subscribes in `ngOnInit`. Mirror that exactly — including the truthiness guards, which are what let the absent-assembly hosts reach the endpoint without sending empty strings.

- [ ] **Step 2: Write the failing test**

In `linear-color-pane.component.tl.spec.ts`, add MSW handlers for the new endpoint and three tests:

1. With `vsId`/`assemblyName` set and the endpoint returning the sixteen hidden names, the Single Hue dropdown offers exactly `AmberColorModel` and `TealColorModel`, the Diverging dropdown offers exactly `VarianceColorModel`, the Multi-Hue dropdown still offers all twelve, and the Heat row is absent from the DOM.
2. With the endpoint returning `[]`, every row renders and every dropdown holds its original count.
3. With the frame already set to `SpectralColorModel` and the endpoint returning the sixteen, `SpectralColorModel` is still offered in the Diverging dropdown and is still the selected value — the hide-never-remove rule.

Follow the file's existing MSW setup rather than adding a new server; the TL setup file already manages the lifecycle.

- [ ] **Step 3: Run test to verify it fails**

Run: `cd web && npx ng test portal:test-tl --include=projects/portal/src/app/binding/editor/chart/aesthetic/linear-color-pane.component.tl.spec.ts`
Expected: FAIL — the component sends no request and filters nothing.

**Never run the full TL suite.** If this invocation reports zero tests, the target or path is wrong — fix the command, do not fall back to an unscoped run.

- [ ] **Step 4: Implement the filter**

In `linear-color-pane.component.ts`: add `@Input() vsId: string;` and `@Input() assemblyName: string;`, a `hiddenFrames: string[] = []` field, and an `ngOnInit` fetch mirroring the categorical pane. Then filter each group array through a helper that keeps the selected frame even when hidden:

```typescript
   private visible(models: string[]): string[] {
      return models.filter(m => !this.hiddenFrames.includes(m) || this.frame?.clazz?.endsWith("." + m));
   }
```

**Do not call `visible()` from the template** — a method in a binding re-runs on every change-detection pass and allocates three arrays each time. Hold the results in fields and recompute them only when an input to them changes:

```typescript
   visibleSingleHue: string[] = [];
   visibleMultiHue: string[] = [];
   visibleDiverging: string[] = [];

   private recomputeVisible(): void {
      this.visibleSingleHue = this.visible(this.singleHueModels);
      this.visibleMultiHue = this.visible(this.multiHueModels);
      this.visibleDiverging = this.visible(this.divergingModels);
   }
```

Call `recomputeVisible()` in three places: after the `hiddenFrames` fetch resolves, at the end of `ngOnInit`, and at the end of `switchColorModel` — the last because the selected frame is itself an input to the filter, through the hide-never-remove clause. Bind the three `linear-color-dropdown` elements to the three fields.

In `linear-color-pane.component.html`: wrap the Heat radio and its `color-strip` column in `@if (!hiddenFrames.includes("HeatColorModel"))`. If hiding Heat leaves its row half-empty, let the Diverging column widen rather than restructuring the grid — the row is shared.

In `color-field-mc.component.html:105`, add the two attributes to the `<linear-color-pane>` element, exactly as lines 94-96 do for the categorical pane:

```html
              <linear-color-pane [(frame)]="frames[0]"
                [vsId]="vsId"
                [assemblyName]="assemblyName"
```

- [ ] **Step 5: Run test to verify it passes**

Run: `cd web && npx ng test portal:test-tl --include=projects/portal/src/app/binding/editor/chart/aesthetic/linear-color-pane.component.tl.spec.ts`
Expected: PASS, 3 tests plus whatever the file already held.

- [ ] **Step 6: Commit**

```bash
git add web/projects/portal/src/app/binding/editor/chart/aesthetic/
git commit -m "Offer a modern chart only the ramps built for it

The pane keeps one template and one code path: the server says which model
names to hide and the client filters its three existing arrays. A frame the
chart is already on stays offered and stays selected even when hidden, so
nothing repaints on a no-op OK.

color-field-mc already passed vsId and assemblyName to the categorical pane
and passed none to this one, so the gate is two attributes.

Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>"
```

---

### Task 9: Full verification and the spec's manual pass

**Files:** none — this task changes nothing and exists to produce evidence.

- [ ] **Step 1: Run the full Java suite**

Run: `./mvnw test -pl core`
Expected: green. Record the exact test and failure counts; do not paraphrase them.

- [ ] **Step 2: Run the scoped frontend suites**

Run: `cd web && npx ng test portal --include=projects/portal/src/app/binding/editor/chart/aesthetic/**/*.spec.ts`
Run: `cd web && npx ng test portal:test-tl --include=projects/portal/src/app/binding/editor/chart/aesthetic/linear-color-pane.component.tl.spec.ts`

Record both counts. Note that `npm run test:portal` does **not** run the portal project — it resolves to `em` — so never use it as evidence.

- [ ] **Step 3: Build the module**

Run: `./mvnw clean install -pl core -am -DskipTests`
Expected: BUILD SUCCESS.

- [ ] **Step 4: Work the spec's manual list**

From the design's §5, in a running server, in both light and dark:

- A modern chart binding a measure to colour is born on Teal.
- **A chart created through the object wizard**, binding a measure to colour, is also born on Teal — the path Task 10 closed, and the one that was wrong for the whole slice until then.
- The same chart in dark mode reads at both ends — the low stop is not the brightest thing on screen.
- A classic chart is unchanged in both picker and seed.
- A report chart is unchanged.

**The picker's exact expected contents** — this is what "the swatches render" has to mean to be checkable:

| Row | Modern-marked chart | Classic chart |
|---|---|---|
| Custom | visible | visible |
| Single Hue | **2** — Amber, Teal | **8** — Amber, Blues, Greens, Greys, Oranges, Purples, Reds, Teal |
| Multi-Hue | **12**, unchanged | **12**, unchanged |
| Diverging | **1** — Variance | **10** — the nine ColorBrewer diverging ramps, plus Variance |
| Heat | **row absent** | visible |

Amber is first in Single Hue and Teal last; Variance is last in Diverging. Confirm each of the three renders an image rather than a broken-image placeholder — the files are `assets/Amber.png`, `assets/Teal.png`, `assets/Variance.png`.

**Then the hide-never-remove case, which is the behaviour most likely to be wrong.** Open a modern chart already bound to a hidden ramp — `Spectral` is the clearest — and confirm Diverging shows **two** entries, Variance and Spectral, with Spectral selected. Click OK without changing anything and confirm the chart does not repaint. That rule is what keeps existing dashboards from silently re-colouring.

Record what was checked and what was seen. **Anything that does not match goes in the report, not into a fix — the reviewer decides.**

- [ ] **Step 5: Commit any evidence**

If the manual pass produced notes worth keeping, append them to the design's §5 and commit:

```bash
git add docs/superpowers/specs/lookfeel/2026-09-16-chart-ramps-design.md
git commit -m "Record the ramps manual pass

Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>"
```

---

## Notes for the executor

- **Task 1 gates everything.** Tasks 2 and 4 need its three stop strings verbatim. Do not start them until Task 1 reports the hexes.
- **Two stop-and-report conditions**, both in Task 1's and Task 4's steps: a C1/C2 conflict in derivation, and a Variance swatch whose centre does not read neutral. Both mean the design is wrong, and the design says such findings come back rather than being tuned away.
- **Line numbers are advisory.** They were read at the branch point. Confirm each with `grep -n` before editing.
- **Do not fix `case "BluesColorFrameW"`.** It is recorded in the design as deferred, and widening the diff at a review gate is the wrong trade.

---

### Task 10: Thread the context through the wizard recommender

**Runs before Task 9**, so the final verification covers it. Added after Task 6's review disproved the deferral this plan originally carried.

Task 6 threaded a `VizContext` to every seed site the composer reaches. The object wizard's recommender was left on legacy overloads, so a wizard-created chart binding a measure to colour is seeded `BluesColorFrame` — and keeps it, because every threaded seed guards on `!(frame instanceof LinearColorFrame)` and Teal is one. This is a live gap, not a latent one: the wizard's temp chart carries the host viewsheet's mark.

**Files:**
- Modify: `core/src/main/java/inetsoft/web/vswizard/recommender/object/VSChartDefaultRecommendationFactory.java` — `recommend(VSWizardData, Principal)` resolves the context
- Modify: `core/src/main/java/inetsoft/web/vswizard/recommender/chart/ChartCombinationUtil.java` — pass it through
- Modify: `core/src/main/java/inetsoft/web/vswizard/recommender/chart/ChartTypeFilter.java` — three constructors and the field
- Modify: its subclasses under `.../recommender/chart/`
- Modify: the five legacy-overload callers — `ChartTypeFilter:342`, `CirclePackingChartFilter:67,92`, `MekkoChartFilter:63`, `ScatterChartFilter:112`, `WordCloudFilter:78`
- Modify: the two direct seeds — `ContourMapChartFilter:49`, `ContourScatterChartFilter:48`
- Test: `core/src/test/java/inetsoft/web/vswizard/recommender/WizardSeededLinearFrameTest.java`

**Interfaces:**
- Consumes: `VSChartPaletteDefaults.defaultLinearFrame(VizContext)` and `GraphUtil.fixVisualFrames(ChartInfo, VizContext)`, both from Task 6.
- Produces: nothing later tasks call.

- [ ] **Step 1: Confirm the chain, then re-derive the site list**

The mark is reachable in one call. `VSWizardBindingHandler.getTempChart(VSWizardData)` (`:1888-1896`) already holds the marked assembly and returns only its info:

```java
   public VSChartInfo getTempChart(VSWizardData wizardData) {
      ChartVSAssembly chart = null;

      if(wizardData != null && wizardData.getVsTemporaryInfo() != null) {
         chart = wizardData.getVsTemporaryInfo().getTempChart();
      }

      return chart == null ? null : chart.getVSChartInfo();
   }
```

The assembly is stamped at creation: `VSWizardTemporaryInfoService:69` builds it as `new ChartVSAssembly(vs, TEMP_CHART_NAME)` from the real runtime viewsheet, and `AbstractVSAssembly:136` sets `info.setVizMark(hostInfo.getVizMark())`.

Then re-derive rather than trusting the list above:

- `grep -rn "new BluesColorFrame()" core/src/main/java/inetsoft/web/vswizard`
- `grep -rn "fixVisualFrames(\|fixVisualFrame(" core/src/main/java/inetsoft/web/vswizard`
- `grep -rn "new ChartTypeFilter(\|extends ChartTypeFilter" core/src/main/java/inetsoft/web/vswizard`

**Your grep wins over this plan.** Its predecessor was wrong about both counts in Task 6. Report any discrepancy.

- [ ] **Step 2: Write the failing test**

Create `WizardSeededLinearFrameTest`. Mirror the shape of `SeededLinearFrameTest.aModernMarkedAssemblyIsBornOnTeal` — build a `ChartVSAssemblyInfo`, `setVizMark(VizMark.MODERN_LIGHT)`, bind a measure to colour, drive the recommender path, assert `TealColorFrame`; and an unmarked counterpart asserting `BluesColorFrame`.

Drive it at the lowest layer that still crosses the threading you add. If standing up a full `VSWizardData` needs a runtime viewsheet, test `ChartTypeFilter` (or whichever class actually holds the new context field) directly instead — and say in your report which layer you chose and what that leaves uncovered.

- [ ] **Step 3: Run it and watch it fail**

Run: `./mvnw test -pl core -Dtest=WizardSeededLinearFrameTest`
Expected: FAIL, the modern case returning `BluesColorFrame`. **If it passes before you change anything, stop and report** — it means the test does not cross the threading and proves nothing.

- [ ] **Step 4: Thread the context**

Resolve it once at the factory, from the assembly rather than the info:

```java
      ChartVSAssembly tempChart = wizardData == null || wizardData.getVsTemporaryInfo() == null
         ? null : wizardData.getVsTemporaryInfo().getTempChart();
      VizContext ctx = tempChart == null
         ? VizContext.LEGACY : VizContext.of(tempChart.getVSAssemblyInfo());
```

Pass it through `ChartCombinationUtil` and onto `ChartTypeFilter` as a field, keeping a no-context overload on each constructor defaulting to `VizContext.LEGACY`, exactly as Task 6 did. Then:

- the five `GraphUtil.fixVisualFrames(info)` calls become `GraphUtil.fixVisualFrames(info, ctx)`
- the two `info.setColorFrame(new BluesColorFrame())` become `info.setColorFrame(VSChartPaletteDefaults.defaultLinearFrame(ctx))`

- [ ] **Step 5: Run the test, then the full suite**

Run: `./mvnw test -pl core -Dtest=WizardSeededLinearFrameTest` → PASS
Run: `./mvnw test -pl core` → compare against 5795 tests / 0 failures / 0 errors / 69 skipped, and report exact counts.

- [ ] **Step 6: Commit**

```bash
git add core/src/main/java/inetsoft/web/vswizard core/src/test/java/inetsoft/web/vswizard
git commit -m "Seed a wizard-created chart on Teal too

The recommender held the temp chart's mark and threw it away: getTempChart
returned only the info, and every filter called the legacy fixVisualFrames
overload that hard-codes the pre-modern context. A wizard-created modern
chart took Blues and kept it, because the seeds only fire when no linear
frame is present.

Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>"
```
