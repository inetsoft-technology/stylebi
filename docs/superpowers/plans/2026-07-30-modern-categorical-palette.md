# Modern Light/Dark Categorical Chart Palette Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Declare the modern categorical chart colors as named `ChartPalette` rules in `defaults.css` so the Select Palette dialog pre-selects whichever palette a chart actually renders, and the chart-series swatch grid is served from that same CSS instead of a hardcoded TypeScript copy.

**Architecture:** `defaults.css` becomes the single source of truth for two new palettes, `Modern` and `Modern Dark`, each declaring all 40 indices. `VSChartPaletteDefaults` stops returning hardcoded arrays and resolves those palettes through `ColorPalettes`, keeping its existing arrays as a fallback. A new portal endpoint serves the already-gate-resolved 40 colors, and a root-provided Angular service feeds them to the three chart-series color-picker call sites. The palette dialog needs no code change — its existing color-equality match picks up the new palettes for free.

**Tech Stack:** Java 21, Spring Boot 3.5.8, JUnit 5 (`@Tag("core")`), Angular 21.2, TypeScript 5.9, Vitest 4.1.7.

**Design spec:** `community/docs/superpowers/specs/2026-07-30-modern-categorical-palette-design.md`

## Global Constraints

- **Stage, do not commit.** Every task's final step says "Commit". For this execution run that is overridden: run the `git add` for the listed files, then **stop**. Do not run `git commit`. The listed commit message is still useful — put it in your report so it can be used later. Staging is required, not optional: the review package is built from `git diff HEAD`, which only sees new files once they are staged.
- Work happens on the `community` submodule's existing `viz-updates` branch. Do not create or switch branches.
- **Frontend single-spec command.** Run one Angular spec with `npx ng test portal --include="**/<file>.spec.ts"` from `community/web`. The `npx vitest run <path>` form documented in `community/CLAUDE.md` does **not** work in this repo (it fails on the JIT/DI setup), and `--project portal` does not work either. Testing-library specs use the separate target: `npx ng test portal:test-tl --include="**/<file>.tl.spec.ts"`.
- All work is inside the `community/` submodule. This is a **community-repo PR only** — no enterprise PR, no submodule pointer bump.
- Rendered output must not change while the fallback holds. `VGraphPairModernPaletteTest` must pass **unmodified** at every commit.
- The two new palettes declare **all 40 indices**. A shorter `defaultColors` causes series-color wrap-around at index 9, caps `updateCSSColors()`, and caps the composer's editable slots.
- Indices 9–40 of both new palettes are the legacy `Default` tail **verbatim**. Do not redesign the tail.
- Modern head, indices 1–8: `#00d4e8 #00b87a #f59e0b #f43f5e #8b5cf6 #3b82f6 #0d9488 #64748b`
- Dark head, indices 1–8: `#22d3ee #10b981 #fbb724 #fb6181 #a78bfa #60a5fa #2dd4bf #94a3b8`
- Exact palette names, including the space and capitalization: `Modern` and `Modern Dark`.
- Gate properties are unchanged: `viewsheet.modernVisualization`, `viewsheet.modernChartPalette`, `viewsheet.darkMode`, all org-scoped.
- **Do not change** `color-editor.palette`, `cp-color-pane.palette`, `color-picker.palette`, or `color-dropdown.getPalette()`. Changing any of those leaks modern series colors into 18 non-series surfaces including Format-dialog pickers.
- Never hand the frame returned by `ColorPalettes.getPalette()` to a chart — it is a shared per-org cached instance. Always copy colors out by index.
- Java comments: short clauses, no ticket/PR/spec references in source comments. No comments in Angular `.html` templates.

---

## File Structure

**Backend**

| File | Responsibility |
|---|---|
| `core/src/main/resources/inetsoft/util/css/defaults.css` | Modify — declares the two new palettes. Data only. |
| `core/src/main/java/inetsoft/uql/viewsheet/internal/VSChartPaletteDefaults.java` | Modify — owns all palette resolution: pure frame→`Color[]` conversion, the legacy splice fallback, CSS lookup with memoization, and the gate decision. |
| `core/src/main/java/inetsoft/web/portal/controller/ChartColorPaletteController.java` | Create — thin REST wrapper, no logic beyond hex serialization. |

**Backend tests**

| File | Responsibility |
|---|---|
| `core/src/test/java/inetsoft/uql/viewsheet/internal/VSChartPaletteDefaultsTest.java` | Modify — extend with resolver, fallback, memo, and copy-safety cases. |
| `core/src/test/java/inetsoft/uql/viewsheet/graph/aesthetic/ColorPalettesModernTest.java` | Create — palette catalog contents plus the tail drift guard. |
| `core/src/test/java/inetsoft/web/portal/controller/ChartColorPaletteControllerTest.java` | Create — the three gate states. |

**Frontend**

| File | Responsibility |
|---|---|
| `web/projects/portal/src/app/widget/color-picker/chart-palette.service.ts` | Create — fetches once, exposes a synchronous grid snapshot plus a flat list, falls back to `DefaultPalette.chart`. |
| `web/projects/portal/src/app/binding/widget/color-field-pane.component.ts` | Modify — series swatch grid from the service. |
| `web/projects/portal/src/app/binding/editor/chart/aesthetic/combined-color-pane.component.ts` | Modify — `reset()` reseeds from the service. |
| `web/projects/portal/src/app/binding/editor/chart/color-mapping-dialog.component.{ts,html}` | Modify — pass an explicit `[palette]`. |
| `web/projects/portal/src/app/portal/app.component.ts`, `composer/app.component.ts`, `vsobjects/viewer-app.component.ts` | Modify — inject the service at bootstrap to warm it. |

**Frontend tests**

| File | Responsibility |
|---|---|
| `web/projects/portal/src/app/widget/color-picker/palette-test-fixtures.ts` | Create — the one place the 40-color arrays are written. All three specs below import from it. |
| `web/projects/portal/src/app/widget/color-picker/chart-palette.service.spec.ts` | Create — fetch, grid shape, both fallbacks. |
| `web/projects/portal/src/app/widget/color-picker/color-dropdown.spec.ts` | Create — pins the `isBg × transEnabled × chart` truth table. |
| `web/projects/portal/src/app/binding/editor/chart/palette-dialog.spec.ts` | Create — pre-selection by color equality. |

---

### Task 1: Declare `Modern` and `Modern Dark` in defaults.css

Palette catalog only. No renderer or resolver change, so rendered output is untouched and this task is independently verifiable.

**Files:**
- Modify: `community/core/src/main/resources/inetsoft/util/css/defaults.css` (append after the `Default` block, which ends at line 176)
- Test: `community/core/src/test/java/inetsoft/uql/viewsheet/graph/aesthetic/ColorPalettesModernTest.java`

**Interfaces:**
- Consumes: nothing.
- Produces: two palette names resolvable via `ColorPalettes.getPalette("Modern")` and `ColorPalettes.getPalette("Modern Dark")`, each a `CategoricalColorFrame` with `getColorCount() == 40` and no null entries.

- [ ] **Step 1: Write the failing test**

Create `community/core/src/test/java/inetsoft/uql/viewsheet/graph/aesthetic/ColorPalettesModernTest.java`:

```java
package inetsoft.uql.viewsheet.graph.aesthetic;

import inetsoft.graph.aesthetic.CategoricalColorFrame;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.util.css.CSSDictionary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.Color;
import java.util.Collection;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class ColorPalettesModernTest {
   @BeforeEach
   void setup() {
      CSSDictionary.resetDictionaryCache();
   }

   @Test
   void modernPalettesAreRegistered() {
      Collection<String> names = ColorPalettes.getPaletteNames();
      assertTrue(names.contains("Modern"), "Modern palette must be declared in defaults.css");
      assertTrue(names.contains("Modern Dark"), "Modern Dark palette must be declared in defaults.css");
   }

   @Test
   void modernDeclaresFortyNonNullColors() {
      assertFullPalette(ColorPalettes.getPalette("Modern"));
      assertFullPalette(ColorPalettes.getPalette("Modern Dark"));
   }

   @Test
   void modernHeadMatchesSpec() {
      CategoricalColorFrame modern = ColorPalettes.getPalette("Modern");
      assertEquals(new Color(0x00D4E8), modern.getDefaultColor(0));
      assertEquals(new Color(0x64748B), modern.getDefaultColor(7));

      CategoricalColorFrame dark = ColorPalettes.getPalette("Modern Dark");
      assertEquals(new Color(0x22D3EE), dark.getDefaultColor(0));
      assertEquals(new Color(0x94A3B8), dark.getDefaultColor(7));
   }

   // Drift guard: the CSS tail and the Java spliceLegacy fallback must agree, or swapping between
   // them (which happens whenever the fallback triggers) would silently change rendered colors.
   @Test
   void tailMatchesLegacyPalette() {
      CategoricalColorFrame modern = ColorPalettes.getPalette("Modern");
      CategoricalColorFrame dark = ColorPalettes.getPalette("Modern Dark");

      for(int i = 8; i < CategoricalColorFrame.COLOR_PALETTE.length; i++) {
         assertEquals(CategoricalColorFrame.COLOR_PALETTE[i], modern.getDefaultColor(i),
                      "Modern index " + (i + 1) + " must match the legacy tail");
         assertEquals(CategoricalColorFrame.COLOR_PALETTE[i], dark.getDefaultColor(i),
                      "Modern Dark index " + (i + 1) + " must match the legacy tail");
      }
   }

   @Test
   void defaultPaletteIsUnchanged() {
      CategoricalColorFrame def = ColorPalettes.getPalette("Default");
      assertEquals(40, def.getColorCount());

      for(int i = 0; i < CategoricalColorFrame.COLOR_PALETTE.length; i++) {
         assertEquals(CategoricalColorFrame.COLOR_PALETTE[i], def.getDefaultColor(i));
      }
   }

   private void assertFullPalette(CategoricalColorFrame frame) {
      assertNotNull(frame);
      assertEquals(40, frame.getColorCount());

      for(int i = 0; i < 40; i++) {
         assertNotNull(frame.getDefaultColor(i), "index " + (i + 1) + " must not be null");
      }
   }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
cd community && ./mvnw test -pl core -Dtest=ColorPalettesModernTest
```

Expected: FAIL. `modernPalettesAreRegistered` fails because the names are absent; the others NPE on a null frame.

- [ ] **Step 3: Append the head rules to defaults.css**

Append the following after the `Default` block (immediately before `ChartPalette[name='Soft'][index='1']` at line 178). Note `defaults.css` uses 3-space indentation in the `Default` block:

```css
ChartPalette[name='Modern'][index='1'] {
   color: #00d4e8;
}

ChartPalette[name='Modern'][index='2'] {
   color: #00b87a;
}

ChartPalette[name='Modern'][index='3'] {
   color: #f59e0b;
}

ChartPalette[name='Modern'][index='4'] {
   color: #f43f5e;
}

ChartPalette[name='Modern'][index='5'] {
   color: #8b5cf6;
}

ChartPalette[name='Modern'][index='6'] {
   color: #3b82f6;
}

ChartPalette[name='Modern'][index='7'] {
   color: #0d9488;
}

ChartPalette[name='Modern'][index='8'] {
   color: #64748b;
}

ChartPalette[name='Modern Dark'][index='1'] {
   color: #22d3ee;
}

ChartPalette[name='Modern Dark'][index='2'] {
   color: #10b981;
}

ChartPalette[name='Modern Dark'][index='3'] {
   color: #fbb724;
}

ChartPalette[name='Modern Dark'][index='4'] {
   color: #fb6181;
}

ChartPalette[name='Modern Dark'][index='5'] {
   color: #a78bfa;
}

ChartPalette[name='Modern Dark'][index='6'] {
   color: #60a5fa;
}

ChartPalette[name='Modern Dark'][index='7'] {
   color: #2dd4bf;
}

ChartPalette[name='Modern Dark'][index='8'] {
   color: #94a3b8;
}
```

- [ ] **Step 4: Generate the 32 tail rules for each palette**

Transcribing 64 hex values by hand twice is error-prone. Generate them from the existing `Default` block instead, then let the drift-guard test verify the result.

Run this once per palette name, and paste the output directly after that palette's index-8 rule so each palette's 40 rules stay contiguous:

```bash
cd community/core/src/main/resources/inetsoft/util/css

gen_tail() {
  for i in $(seq 9 40); do
    COLOR=$(awk -v want="ChartPalette[name='Default'][index='$i'] {" '
      index($0, want) == 1 { found = 1; next }
      found && /color:/ { sub(/^[ \t]*color:[ \t]*/, ""); sub(/;.*$/, ""); print; exit }
    ' defaults.css)
    [ -z "$COLOR" ] && { echo "FAILED to read Default index $i" >&2; return 1; }
    printf "ChartPalette[name='%s'][index='%s'] {\n   color: %s;\n}\n\n" "$1" "$i" "$COLOR"
  done
}

gen_tail "Modern"        # paste after the Modern index-8 rule
gen_tail "Modern Dark"   # paste after the Modern Dark index-8 rule
```

The `index($0, want) == 1` match is a literal prefix comparison, which avoids having to escape the `[` and `'` characters in a regex.

Verify the counts before moving on. Note the first pattern ends with `']` so it does not also match `Modern Dark`:

```bash
grep -c "name='Modern'\]" defaults.css        # expect 40
grep -c "name='Modern Dark'\]" defaults.css   # expect 40
grep -c "name='Default'\]" defaults.css       # expect 40, unchanged
```

- [ ] **Step 5: Run the test to verify it passes**

```bash
cd community && ./mvnw test -pl core -Dtest=ColorPalettesModernTest
```

Expected: PASS, all 5 tests.

- [ ] **Step 6: Verify no rendering change**

```bash
cd community && ./mvnw test -pl core -Dtest=VGraphPairModernPaletteTest,VSChartPaletteDefaultsTest
```

Expected: PASS, unmodified. Nothing consumes the new palettes yet.

- [ ] **Step 7: Commit**

```bash
cd community
git add core/src/main/resources/inetsoft/util/css/defaults.css \
        core/src/test/java/inetsoft/uql/viewsheet/graph/aesthetic/ColorPalettesModernTest.java
git commit -m "Declare Modern and Modern Dark chart palettes in defaults.css"
```

---

### Task 2: Extract the pure palette resolver

Pure refactor plus a new tested helper. `modernPalette()` / `darkPalette()` still return exactly what they return today, so this task changes no behavior. The helper exists because the fallback branches cannot be reached through the real CSS pipeline once Task 1 declares complete palettes — `format.css` merges *on top* of `defaults.css` and cannot remove rules, so a missing or short palette is only reachable by calling the helper directly.

**Files:**
- Modify: `community/core/src/main/java/inetsoft/uql/viewsheet/internal/VSChartPaletteDefaults.java`
- Test: `community/core/src/test/java/inetsoft/uql/viewsheet/internal/VSChartPaletteDefaultsTest.java`

**Interfaces:**
- Consumes: nothing from Task 1 at runtime.
- Produces:
  - `static Color[] spliceLegacy(Color[] head)` — package-private. Returns `head` followed by `CategoricalColorFrame.COLOR_PALETTE[head.length..39]`.
  - `static Color[] fromFrame(CategoricalColorFrame frame, Color[] head)` — package-private. Returns the frame's default colors copied by index, or `spliceLegacy(head)` when `frame` is null, `frame.getColorCount() < 40`, or any index is null.

- [ ] **Step 1: Write the failing tests**

Append to `VSChartPaletteDefaultsTest`:

```java
   @Test
   void spliceLegacyKeepsHeadAndTail() {
      Color[] head = { new Color(0x010203), new Color(0x040506) };
      Color[] result = VSChartPaletteDefaults.spliceLegacy(head);

      assertEquals(40, result.length);
      assertEquals(new Color(0x010203), result[0]);
      assertEquals(new Color(0x040506), result[1]);
      assertEquals(CategoricalColorFrame.COLOR_PALETTE[2], result[2]);
      assertEquals(CategoricalColorFrame.COLOR_PALETTE[39], result[39]);
   }

   @Test
   void fromFrameCopiesCompletePalette() {
      CategoricalColorFrame frame = new CategoricalColorFrame();
      Color[] declared = new Color[40];
      Arrays.fill(declared, new Color(0x123456));
      frame.setDefaultColors(declared);

      Color[] result = VSChartPaletteDefaults.fromFrame(frame, MODERN_HEAD_FIXTURE);

      assertEquals(40, result.length);
      assertEquals(new Color(0x123456), result[0]);
      assertEquals(new Color(0x123456), result[39]);
   }

   @Test
   void fromFrameFallsBackWhenFrameIsNull() {
      Color[] result = VSChartPaletteDefaults.fromFrame(null, MODERN_HEAD_FIXTURE);
      assertArrayEquals(VSChartPaletteDefaults.spliceLegacy(MODERN_HEAD_FIXTURE), result);
   }

   @Test
   void fromFrameFallsBackWhenPaletteIsShort() {
      CategoricalColorFrame frame = new CategoricalColorFrame();
      frame.setDefaultColors(new Color[] { new Color(0x111111), new Color(0x222222) });

      Color[] result = VSChartPaletteDefaults.fromFrame(frame, MODERN_HEAD_FIXTURE);

      assertArrayEquals(VSChartPaletteDefaults.spliceLegacy(MODERN_HEAD_FIXTURE), result);
   }

   // A format.css declaring only indices 1-8 and 40 yields a 40-length array with null holes.
   // A null reaching Graphics.setColor would NPE mid-render, so this must fall back wholesale.
   @Test
   void fromFrameFallsBackWhenPaletteHasNullHole() {
      CategoricalColorFrame frame = new CategoricalColorFrame();
      Color[] declared = new Color[40];
      Arrays.fill(declared, new Color(0x123456));
      declared[11] = null;
      frame.setDefaultColors(declared);

      Color[] result = VSChartPaletteDefaults.fromFrame(frame, MODERN_HEAD_FIXTURE);

      assertArrayEquals(VSChartPaletteDefaults.spliceLegacy(MODERN_HEAD_FIXTURE), result);
   }

   private static final Color[] MODERN_HEAD_FIXTURE = {
      new Color(0x00D4E8), new Color(0x00B87A), new Color(0xF59E0B), new Color(0xF43F5E),
      new Color(0x8B5CF6), new Color(0x3B82F6), new Color(0x0D9488), new Color(0x64748B)
   };
```

Add `import java.util.Arrays;` to the test's imports.

- [ ] **Step 2: Run tests to verify they fail**

```bash
cd community && ./mvnw test -pl core -Dtest=VSChartPaletteDefaultsTest
```

Expected: compilation failure — `spliceLegacy` and `fromFrame` do not exist.

- [ ] **Step 3: Add the helpers and refactor**

In `VSChartPaletteDefaults.java`, replace the bodies of `modernPalette()` and `darkPalette()` and add the two helpers:

```java
   public static Color[] modernPalette() {
      return spliceLegacy(MODERN_HEAD);
   }

   public static Color[] darkPalette() {
      return spliceLegacy(DARK_HEAD);
   }

   /**
    * Head colors followed by the legacy tail, so high-cardinality charts keep 40 distinct
    * colors and do not wrap early.
    */
   static Color[] spliceLegacy(Color[] head) {
      List<Color> palette = new ArrayList<>(Arrays.asList(head));
      Color[] legacy = CategoricalColorFrame.COLOR_PALETTE;
      palette.addAll(Arrays.asList(legacy).subList(head.length, legacy.length));
      return palette.toArray(new Color[0]);
   }

   /**
    * Colors copied out of a palette frame by index, or the legacy splice when the frame is
    * absent, short, or has undeclared holes. Copies rather than aliases — the frame is a shared
    * per-org cached instance.
    */
   static Color[] fromFrame(CategoricalColorFrame frame, Color[] head) {
      if(frame == null) {
         return spliceLegacy(head);
      }

      int count = frame.getColorCount();

      if(count < CategoricalColorFrame.COLOR_PALETTE.length) {
         return spliceLegacy(head);
      }

      Color[] colors = new Color[count];

      for(int i = 0; i < count; i++) {
         colors[i] = frame.getDefaultColor(i);

         if(colors[i] == null) {
            return spliceLegacy(head);
         }
      }

      return colors;
   }
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
cd community && ./mvnw test -pl core -Dtest=VSChartPaletteDefaultsTest,VGraphPairModernPaletteTest
```

Expected: PASS. The pre-existing tests still pass because `modernPalette()` and `darkPalette()` return identical arrays.

- [ ] **Step 5: Commit**

```bash
cd community
git add core/src/main/java/inetsoft/uql/viewsheet/internal/VSChartPaletteDefaults.java \
        core/src/test/java/inetsoft/uql/viewsheet/internal/VSChartPaletteDefaultsTest.java
git commit -m "Extract pure palette resolver helpers in VSChartPaletteDefaults"
```

---

### Task 3: Resolve the palettes from CSS with memoization

Flips the source of truth. After this task, editing `defaults.css` (or a customer `format.css`) changes rendered chart colors.

**Files:**
- Modify: `community/core/src/main/java/inetsoft/uql/viewsheet/internal/VSChartPaletteDefaults.java`
- Test: `community/core/src/test/java/inetsoft/uql/viewsheet/internal/VSChartPaletteDefaultsTest.java`

**Interfaces:**
- Consumes: `ColorPalettes.getPalette("Modern")` / `("Modern Dark")` / `("Default")` from Task 1; `fromFrame` / `spliceLegacy` from Task 2.
- Produces:
  - `public static Color[] activePalette()` — dark palette when `VSDensityDefaults.isDark()`, else modern. Does **not** check the modern gate; callers do.
  - `public static Color[] pickerPalette()` — the 40 colors a color picker should offer: `activePalette()` when `isModern()`, else the CSS `Default` palette with `COLOR_PALETTE` as fallback.
  - `static void clearMemo()` — package-private, for tests.

- [ ] **Step 1: Write the failing tests**

Append to `VSChartPaletteDefaultsTest`. Also add `CSSDictionary.resetDictionaryCache()` and `VSChartPaletteDefaults.clearMemo()` to the existing `reset()` method so cached palettes never leak between tests:

```java
   @AfterEach
   void reset() {
      SreeEnv.setProperty("viewsheet.modernVisualization", null);
      SreeEnv.setProperty("viewsheet.modernChartPalette", null);
      SreeEnv.setProperty("viewsheet.darkMode", null);
      VSChartPaletteDefaults.clearMemo();
      CSSDictionary.resetDictionaryCache();
   }

   @Test
   void modernPaletteResolvesFromCss() {
      Color[] modern = VSChartPaletteDefaults.modernPalette();

      assertEquals(40, modern.length);
      assertEquals(new Color(0x00D4E8), modern[0]);
      assertEquals(new Color(0x64748B), modern[7]);
      assertEquals(CategoricalColorFrame.COLOR_PALETTE[8], modern[8]);
      assertEquals(CategoricalColorFrame.COLOR_PALETTE[39], modern[39]);
   }

   @Test
   void darkPaletteResolvesFromCss() {
      Color[] dark = VSChartPaletteDefaults.darkPalette();

      assertEquals(40, dark.length);
      assertEquals(new Color(0x22D3EE), dark[0]);
      assertEquals(new Color(0x94A3B8), dark[7]);
      assertEquals(CategoricalColorFrame.COLOR_PALETTE[8], dark[8]);
   }

   // The memo must not hand out a shared array, or one caller mutating it would corrupt every
   // subsequent chart in the org.
   @Test
   void resolvedPaletteIsACopy() {
      Color[] first = VSChartPaletteDefaults.modernPalette();
      first[0] = Color.MAGENTA;

      Color[] second = VSChartPaletteDefaults.modernPalette();

      assertEquals(new Color(0x00D4E8), second[0]);
   }

   @Test
   void activePaletteFollowsDarkMode() {
      SreeEnv.setProperty("viewsheet.modernVisualization", "true");
      assertEquals(new Color(0x00D4E8), VSChartPaletteDefaults.activePalette()[0]);

      SreeEnv.setProperty("viewsheet.darkMode", "true");
      assertEquals(new Color(0x22D3EE), VSChartPaletteDefaults.activePalette()[0]);
   }

   @Test
   void pickerPaletteIsLegacyWhenGateOff() {
      SreeEnv.setProperty("viewsheet.modernVisualization", "false");

      Color[] picker = VSChartPaletteDefaults.pickerPalette();

      assertEquals(40, picker.length);
      assertEquals(CategoricalColorFrame.COLOR_PALETTE[0], picker[0]);
      assertEquals(CategoricalColorFrame.COLOR_PALETTE[39], picker[39]);
   }

   @Test
   void pickerPaletteFollowsGateAndDarkMode() {
      SreeEnv.setProperty("viewsheet.modernVisualization", "true");
      assertEquals(new Color(0x00D4E8), VSChartPaletteDefaults.pickerPalette()[0]);

      SreeEnv.setProperty("viewsheet.darkMode", "true");
      assertEquals(new Color(0x22D3EE), VSChartPaletteDefaults.pickerPalette()[0]);
   }
```

Add `import inetsoft.util.css.CSSDictionary;` to the test imports.

- [ ] **Step 2: Run tests to verify they fail**

```bash
cd community && ./mvnw test -pl core -Dtest=VSChartPaletteDefaultsTest
```

Expected: compilation failure — `activePalette`, `pickerPalette`, and `clearMemo` do not exist.

- [ ] **Step 3: Wire the resolver to CSS**

In `VSChartPaletteDefaults.java`, add the imports:

```java
import inetsoft.sree.security.OrganizationManager;
import inetsoft.uql.viewsheet.graph.aesthetic.ColorPalettes;
import inetsoft.util.css.CSSDictionary;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
```

Replace `modernPalette()` and `darkPalette()` again, and add the new members:

```java
   public static Color[] modernPalette() {
      return resolve(MODERN_NAME, MODERN_HEAD);
   }

   public static Color[] darkPalette() {
      return resolve(DARK_NAME, DARK_HEAD);
   }

   /**
    * The palette a modern chart renders. Does not check the modern gate; callers do.
    */
   public static Color[] activePalette() {
      return VSDensityDefaults.isDark() ? darkPalette() : modernPalette();
   }

   /**
    * The 40 colors a chart color picker should offer for the current gate state.
    */
   public static Color[] pickerPalette() {
      return isModern()
         ? activePalette()
         : fromFrame(ColorPalettes.getPalette(DEFAULT_NAME), CategoricalColorFrame.COLOR_PALETTE);
   }

   public static void applyModernPalette(CategoricalColorFrame frame) {
      if(frame != null && isModern()) {
         frame.setDefaultColors(activePalette());
      }
   }

   /**
    * Named palette colors, memoized per org until the CSS changes. ColorPalettes.getPalette
    * locks on its own class, so memoizing keeps concurrent chart renders off that monitor.
    */
   private static Color[] resolve(String name, Color[] head) {
      String orgID = OrganizationManager.getInstance().getCurrentOrgID();
      long ts = CSSDictionary.getOrgScopedCSSLastModified(CSSDictionary.getDictionary());
      String memoKey = orgID + "|" + name;
      String stamp = orgID + "|" + name + "|" + ts;
      Memo memo = MEMO.get(memoKey);

      if(memo != null && memo.stamp().equals(stamp)) {
         return memo.colors().clone();
      }

      Color[] resolved = fromFrame(ColorPalettes.getPalette(name), head);
      MEMO.put(memoKey, new Memo(stamp, resolved));
      return resolved.clone();
   }

   static void clearMemo() {
      MEMO.clear();
   }

   private record Memo(String stamp, Color[] colors) {
   }

   private static final String MODERN_NAME = "Modern";
   private static final String DARK_NAME = "Modern Dark";
   private static final String DEFAULT_NAME = "Default";
   private static final Map<String, Memo> MEMO = new ConcurrentHashMap<>();
```

Keep `MODERN_HEAD` and `DARK_HEAD` — they are now the fallback only. Update the class javadoc to note that the palettes resolve from `defaults.css` with the constants as fallback.

- [ ] **Step 4: Run tests to verify they pass**

```bash
cd community && ./mvnw test -pl core -Dtest=VSChartPaletteDefaultsTest,ColorPalettesModernTest,VGraphPairModernPaletteTest
```

Expected: PASS. `VGraphPairModernPaletteTest` passing unmodified is the proof that swapping the source of truth changed no rendered color.

- [ ] **Step 5: Run the full core chart suite for regressions**

```bash
cd community && ./mvnw test -pl core -Dtest='*Chart*,*Palette*,*CSS*'
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
cd community
git add core/src/main/java/inetsoft/uql/viewsheet/internal/VSChartPaletteDefaults.java \
        core/src/test/java/inetsoft/uql/viewsheet/internal/VSChartPaletteDefaultsTest.java
git commit -m "Resolve modern chart palettes from defaults.css with per-org memoization"
```

---

### Task 4: Serve the active picker palette over REST

**Files:**
- Create: `community/core/src/main/java/inetsoft/web/portal/controller/ChartColorPaletteController.java`
- Test: `community/core/src/test/java/inetsoft/web/portal/controller/ChartColorPaletteControllerTest.java`

**Interfaces:**
- Consumes: `VSChartPaletteDefaults.pickerPalette()` from Task 3.
- Produces: `GET /api/portal/chart-color-palette` → `String[]` of 40 lowercase `#rrggbb` values. Consumed by Task 6.

- [ ] **Step 1: Write the failing test**

Create `community/core/src/test/java/inetsoft/web/portal/controller/ChartColorPaletteControllerTest.java`:

```java
package inetsoft.web.portal.controller;

import inetsoft.sree.SreeEnv;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.viewsheet.internal.VSChartPaletteDefaults;
import inetsoft.util.css.CSSDictionary;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class ChartColorPaletteControllerTest {
   @AfterEach
   void reset() {
      SreeEnv.setProperty("viewsheet.modernVisualization", null);
      SreeEnv.setProperty("viewsheet.darkMode", null);
      CSSDictionary.resetDictionaryCache();
   }

   @Test
   void gateOffServesLegacyPalette() {
      SreeEnv.setProperty("viewsheet.modernVisualization", "false");

      String[] colors = new ChartColorPaletteController().getChartColorPalette();

      assertEquals(40, colors.length);
      assertEquals("#518db9", colors[0]);
      assertEquals("#cccc33", colors[39]);
   }

   @Test
   void gateOnServesModernPalette() {
      SreeEnv.setProperty("viewsheet.modernVisualization", "true");

      String[] colors = new ChartColorPaletteController().getChartColorPalette();

      assertEquals(40, colors.length);
      assertEquals("#00d4e8", colors[0]);
      assertEquals("#64748b", colors[7]);
   }

   @Test
   void darkModeServesDarkPalette() {
      SreeEnv.setProperty("viewsheet.modernVisualization", "true");
      SreeEnv.setProperty("viewsheet.darkMode", "true");

      String[] colors = new ChartColorPaletteController().getChartColorPalette();

      assertEquals("#22d3ee", colors[0]);
      assertEquals("#94a3b8", colors[7]);
   }

   @Test
   void everyEntryIsALowercaseHexTriplet() {
      SreeEnv.setProperty("viewsheet.modernVisualization", "true");

      for(String color : new ChartColorPaletteController().getChartColorPalette()) {
         assertTrue(color.matches("#[0-9a-f]{6}"), "not a hex triplet: " + color);
      }
   }
}
```

`Tool.toString(Color)` formats with `String.format("#%06x", ...)`, so output is always lowercase regardless of the case used in the CSS.

- [ ] **Step 2: Run test to verify it fails**

```bash
cd community && ./mvnw test -pl core -Dtest=ChartColorPaletteControllerTest
```

Expected: compilation failure — `ChartColorPaletteController` does not exist.

- [ ] **Step 3: Write the controller**

Create `community/core/src/main/java/inetsoft/web/portal/controller/ChartColorPaletteController.java` with the standard AGPL header used by every file in this tree, then:

```java
package inetsoft.web.portal.controller;

import inetsoft.uql.viewsheet.internal.VSChartPaletteDefaults;
import inetsoft.util.Tool;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.awt.Color;
import java.util.Arrays;

/**
 * Serves the categorical chart palette a color picker should offer, already resolved for the
 * org's modern and dark gates. Not under /api/composer so portal viewer widgets can use it.
 */
@RestController
public class ChartColorPaletteController {
   @GetMapping("/api/portal/chart-color-palette")
   public String[] getChartColorPalette() {
      Color[] colors = VSChartPaletteDefaults.pickerPalette();
      return Arrays.stream(colors).map(Tool::toString).toArray(String[]::new);
   }
}
```

- [ ] **Step 4: Run test to verify it passes**

```bash
cd community && ./mvnw test -pl core -Dtest=ChartColorPaletteControllerTest
```

Expected: PASS, all 4 tests.

- [ ] **Step 5: Commit**

```bash
cd community
git add core/src/main/java/inetsoft/web/portal/controller/ChartColorPaletteController.java \
        core/src/test/java/inetsoft/web/portal/controller/ChartColorPaletteControllerTest.java
git commit -m "Add chart color palette endpoint for color pickers"
```

---

### Task 5: Pin existing color-picker palette resolution

Pure test task, no production change. Written **before** the frontend changes so it functions as a real guard: if a later task widens the blast radius into Format-dialog surfaces, these tests fail.

**Files:**
- Test: `community/web/projects/portal/src/app/widget/color-picker/color-dropdown.spec.ts`

**Interfaces:**
- Consumes: nothing.
- Produces: nothing. Guard only.

- [ ] **Step 1: Write the tests**

Create `color-dropdown.spec.ts` with the AGPL header used by neighbouring spec files, then:

```typescript
import { ColorDropdown } from "./color-dropdown.component";
import { DefaultPalette } from "./default-palette";

// The chart branch of getPalette() is only reachable when !isBg && !transEnabled. Several
// callers rely on that ordering rather than passing [chart] explicitly - notably the Format
// pane's Value Fill picker (transEnabled) and its border picker (chart default true). Pin the
// whole truth table so the branch order cannot be changed silently.
describe("ColorDropdown palette resolution", () => {
   function dropdown(isBg: boolean, transEnabled: boolean, chart: boolean): ColorDropdown {
      const cd = new ColorDropdown();
      cd.isBg = isBg;
      cd.transEnabled = transEnabled;
      cd.chart = chart;
      return cd;
   }

   it("defaults chart to true", () => {
      expect(new ColorDropdown().chart).toBe(true);
   });

   it("returns fgWithTransparent for foreground with transparency, ignoring chart", () => {
      expect(dropdown(false, true, true).getPalette()).toBe(DefaultPalette.fgWithTransparent);
      expect(dropdown(false, true, false).getPalette()).toBe(DefaultPalette.fgWithTransparent);
   });

   it("returns the chart grid for opaque foreground with chart true", () => {
      expect(dropdown(false, false, true).getPalette()).toBe(DefaultPalette.chart);
   });

   it("returns the generic grid for opaque foreground with chart false", () => {
      expect(dropdown(false, false, false).getPalette()).toBe(DefaultPalette.palette);
   });

   it("returns background grids regardless of chart", () => {
      expect(dropdown(true, true, true).getPalette()).toBe(DefaultPalette.bgWithTransparent);
      expect(dropdown(true, true, false).getPalette()).toBe(DefaultPalette.bgWithTransparent);
      expect(dropdown(true, false, true).getPalette()).toBe(DefaultPalette.bgWithNoTransparent);
      expect(dropdown(true, false, false).getPalette()).toBe(DefaultPalette.bgWithNoTransparent);
   });
});
```

- [ ] **Step 2: Run the tests to verify they pass**

```bash
cd community/web && npx ng test portal --include="**/color-dropdown.spec.ts"
```

Expected: PASS. These describe behavior that already exists.

- [ ] **Step 3: Commit**

```bash
cd community
git add web/projects/portal/src/app/widget/color-picker/color-dropdown.spec.ts
git commit -m "Pin color-dropdown palette resolution truth table"
```

---

### Task 6: Add the ChartPaletteService

**Files:**
- Create: `community/web/projects/portal/src/app/widget/color-picker/chart-palette.service.ts`
- Create: `community/web/projects/portal/src/app/widget/color-picker/palette-test-fixtures.ts`
- Test: `community/web/projects/portal/src/app/widget/color-picker/chart-palette.service.spec.ts`

**Interfaces:**
- Consumes: `GET /api/portal/chart-color-palette` from Task 4.
- Produces, used by Task 7:
  - `CHART_COLOR_PALETTE_URI: string` — `"../api/portal/chart-color-palette"`
  - `ChartPaletteService.chartPalette: ColorPalette` — synchronous getter, 5×8, never null
  - `ChartPaletteService.chartPalette$: Observable<ColorPalette>`
  - `ChartPaletteService.flatColors(): string[]` — 40 entries
- Produces, used by Tasks 7 and 8 — `palette-test-fixtures.ts` exports:
  - `LEGACY_HEAD`, `MODERN_HEAD`, `DARK_HEAD`: `string[]` of 8
  - `LEGACY_TAIL`: `string[]` of 32
  - `palette40(head: string[]): string[]` — head concatenated with `LEGACY_TAIL`
  - `grid40(colors: string[]): ColorPalette` — chunks 40 colors into 5×8

- [ ] **Step 1: Write the shared test fixtures**

The same 40 colors are needed by three specs in different shapes. Write them once. Create `palette-test-fixtures.ts` with the AGPL header, then:

```typescript
import { ColorPalette } from "./color-classes";

export const LEGACY_HEAD: string[] = [
   "#518db9", "#b9dbf4", "#62a640", "#ade095", "#fc8f2a", "#fde3a7", "#d64541", "#fda7a5"
];

export const MODERN_HEAD: string[] = [
   "#00d4e8", "#00b87a", "#f59e0b", "#f43f5e", "#8b5cf6", "#3b82f6", "#0d9488", "#64748b"
];

export const DARK_HEAD: string[] = [
   "#22d3ee", "#10b981", "#fbb724", "#fb6181", "#a78bfa", "#60a5fa", "#2dd4bf", "#94a3b8"
];

export const LEGACY_TAIL: string[] = [
   "#9368be", "#be90d4", "#95a5a6", "#dadfe1", "#19b5fe", "#c5eff7", "#869530", "#c8d96f",
   "#a88637", "#d2b267", "#019875", "#68c3a3", "#99ccff", "#999933", "#cc9933", "#006666",
   "#993300", "#666666", "#663366", "#cccccc", "#669999", "#cccc66", "#cc6600", "#9999ff",
   "#0066cc", "#ffcc00", "#009999", "#99cc33", "#ff9900", "#66cccc", "#339966", "#cccc33"
];

export function palette40(head: string[]): string[] {
   return head.concat(LEGACY_TAIL);
}

export function grid40(colors: string[]): ColorPalette {
   const rows: string[][] = [];

   for(let i = 0; i < colors.length; i += 8) {
      rows.push(colors.slice(i, i + 8));
   }

   return rows as ColorPalette;
}
```

- [ ] **Step 2: Write the failing test**

Create `chart-palette.service.spec.ts` with the AGPL header, then:

```typescript
import { HttpClientTestingModule, HttpTestingController } from "@angular/common/http/testing";
import { TestBed } from "@angular/core/testing";
import { CHART_COLOR_PALETTE_URI, ChartPaletteService } from "./chart-palette.service";
import { DefaultPalette } from "./default-palette";
import { MODERN_HEAD, palette40 } from "./palette-test-fixtures";

const MODERN_40: string[] = palette40(MODERN_HEAD);

describe("ChartPaletteService", () => {
   let service: ChartPaletteService;
   let httpMock: HttpTestingController;

   beforeEach(() => {
      TestBed.configureTestingModule({
         imports: [HttpClientTestingModule],
         providers: [ChartPaletteService]
      });

      // constructing the service fires the fetch
      service = TestBed.inject(ChartPaletteService);
      httpMock = TestBed.inject(HttpTestingController);
   });

   afterEach(() => httpMock.verify());

   it("starts on the legacy fallback before the fetch resolves", () => {
      expect(service.chartPalette).toBe(DefaultPalette.chart);
      httpMock.expectOne(CHART_COLOR_PALETTE_URI).flush(MODERN_40);
   });

   it("chunks 40 colors into a 5x8 grid", () => {
      httpMock.expectOne(CHART_COLOR_PALETTE_URI).flush(MODERN_40);

      const grid = service.chartPalette;
      expect(grid.length).toBe(5);
      expect(grid[0].length).toBe(8);
      expect(grid[0][0]).toBe("#00d4e8");
      expect(grid[0][7]).toBe("#64748b");
      expect(grid[4][7]).toBe("#cccc33");
   });

   it("exposes a flat 40-entry list", () => {
      httpMock.expectOne(CHART_COLOR_PALETTE_URI).flush(MODERN_40);

      expect(service.flatColors().length).toBe(40);
      expect(service.flatColors()[0]).toBe("#00d4e8");
   });

   it("falls back to the legacy grid on HTTP error", () => {
      httpMock.expectOne(CHART_COLOR_PALETTE_URI)
         .flush("boom", { status: 500, statusText: "Server Error" });

      expect(service.chartPalette).toBe(DefaultPalette.chart);
   });

   it("falls back when the response is not 40 colors", () => {
      httpMock.expectOne(CHART_COLOR_PALETTE_URI).flush(["#00d4e8", "#00b87a"]);

      expect(service.chartPalette).toBe(DefaultPalette.chart);
   });

   it("fetches only once across multiple subscribers", () => {
      httpMock.expectOne(CHART_COLOR_PALETTE_URI).flush(MODERN_40);

      service.chartPalette$.subscribe();
      service.chartPalette$.subscribe();

      httpMock.verify();
   });
});
```

- [ ] **Step 3: Run test to verify it fails**

```bash
cd community/web && npx ng test portal --include="**/chart-palette.service.spec.ts"
```

Expected: FAIL — cannot resolve `./chart-palette.service`.

- [ ] **Step 4: Write the service**

Create `chart-palette.service.ts` with the AGPL header, then:

```typescript
import { HttpClient } from "@angular/common/http";
import { Injectable } from "@angular/core";
import { Observable, of } from "rxjs";
import { catchError, map, shareReplay, tap } from "rxjs/operators";
import { ColorPalette } from "./color-classes";
import { DefaultPalette } from "./default-palette";

export const CHART_COLOR_PALETTE_URI: string = "../api/portal/chart-color-palette";

const ROWS: number = 5;
const COLUMNS: number = 8;

/**
 * Serves the categorical chart swatch grid, resolved server-side for the org's modern and dark
 * gates. Falls back to the legacy grid until the fetch resolves, and permanently if it fails.
 */
@Injectable({
   providedIn: "root"
})
export class ChartPaletteService {
   private grid: ColorPalette = DefaultPalette.chart;
   private readonly palette$: Observable<ColorPalette>;

   constructor(private http: HttpClient) {
      this.palette$ = this.http.get<string[]>(CHART_COLOR_PALETTE_URI).pipe(
         map((colors) => ChartPaletteService.toGrid(colors)),
         catchError(() => of(DefaultPalette.chart)),
         tap((grid) => this.grid = grid),
         shareReplay(1)
      );
      this.palette$.subscribe();
   }

   get chartPalette(): ColorPalette {
      return this.grid;
   }

   get chartPalette$(): Observable<ColorPalette> {
      return this.palette$;
   }

   flatColors(): string[] {
      return this.grid.flat();
   }

   // ColorPalette is a strict 5x8 tuple, and the template iterates rows, so a ragged grid
   // would break rendering. Anything but exactly 40 colors keeps the fallback.
   private static toGrid(colors: string[]): ColorPalette {
      if(!colors || colors.length !== ROWS * COLUMNS) {
         return DefaultPalette.chart;
      }

      const rows: string[][] = [];

      for(let i = 0; i < colors.length; i += COLUMNS) {
         rows.push(colors.slice(i, i + COLUMNS));
      }

      return rows as ColorPalette;
   }
}
```

- [ ] **Step 5: Run test to verify it passes**

```bash
cd community/web && npx ng test portal --include="**/chart-palette.service.spec.ts"
```

Expected: PASS, all 6 tests.

- [ ] **Step 6: Stage (do not commit)**

```bash
cd community
git add web/projects/portal/src/app/widget/color-picker/chart-palette.service.ts \
        web/projects/portal/src/app/widget/color-picker/palette-test-fixtures.ts \
        web/projects/portal/src/app/widget/color-picker/chart-palette.service.spec.ts
```

Message for later use: `Add ChartPaletteService serving the gate-resolved chart swatch grid`

---

### Task 7: Wire the three chart-series call sites

The only task that changes user-visible picker behavior. Touch **only** these three call sites plus the bootstrap warm-up.

**Files:**
- Modify: `community/web/projects/portal/src/app/binding/widget/color-field-pane.component.ts`
- Modify: `community/web/projects/portal/src/app/binding/editor/chart/aesthetic/combined-color-pane.component.ts`
- Modify: `community/web/projects/portal/src/app/binding/editor/chart/color-mapping-dialog.component.ts` and `.html:58`
- Modify: `community/web/projects/portal/src/app/portal/app.component.ts`, `composer/app.component.ts`, `vsobjects/viewer-app.component.ts`
- Test: `community/web/projects/portal/src/app/binding/widget/color-field-pane.spec.ts`

**Interfaces:**
- Consumes: `ChartPaletteService.chartPalette` and `flatColors()` from Task 6.
- Produces: `ColorFieldPane.palette` becomes an `@Input()` of type `ColorPalette`.

- [ ] **Step 1: Write the failing test**

Create `community/web/projects/portal/src/app/binding/widget/color-field-pane.spec.ts` with the AGPL header, then:

```typescript
import { ColorPalette } from "../../widget/color-picker/color-classes";
import {
   DARK_HEAD,
   grid40,
   MODERN_HEAD,
   palette40
} from "../../widget/color-picker/palette-test-fixtures";
import { ColorFieldPane } from "./color-field-pane.component";

const GRID: ColorPalette = grid40(palette40(MODERN_HEAD));
const OTHER: ColorPalette = grid40(palette40(DARK_HEAD));

describe("ColorFieldPane", () => {
   it("seeds its palette from the chart palette service", () => {
      const pane = new ColorFieldPane({ chartPalette: GRID } as any);
      expect(pane.palette).toBe(GRID);
   });

   it("allows an explicit palette to override the service default", () => {
      const pane = new ColorFieldPane({ chartPalette: GRID } as any);
      pane.palette = OTHER;
      expect(pane.palette).toBe(OTHER);
      expect(pane.palette).not.toBe(GRID);
   });
});
```

- [ ] **Step 2: Run test to verify it fails**

```bash
cd community/web && npx ng test portal --include="**/color-field-pane.spec.ts"
```

Expected: FAIL — `ColorFieldPane` takes no constructor argument.

- [ ] **Step 3: Wire ColorFieldPane**

In `color-field-pane.component.ts`, replace the `DefaultPalette` import with the service and set the palette in the constructor body. A field initializer cannot reference a constructor parameter property, so the assignment must be in the body:

```typescript
import { ChartPaletteService } from "../../widget/color-picker/chart-palette.service";
```

```typescript
export class ColorFieldPane {
   @Input() selectedColor: string = "#518db9";
   @Input() clearEnabled: boolean = false;
   @Input() palette: ColorPalette;
   @Output() colorChanged: EventEmitter<string> = new EventEmitter<string>();
   @Output() colorCleared: EventEmitter<string> = new EventEmitter<string>();

   constructor(chartPaletteService: ChartPaletteService) {
      this.palette = chartPaletteService.chartPalette;
   }
```

Leave the rest of the class and the template unchanged — `color-field-pane.component.html:18` already binds `[palette]="palette"`.

- [ ] **Step 4: Run test to verify it passes**

```bash
cd community/web && npx ng test portal --include="**/color-field-pane.spec.ts"
```

Expected: PASS.

- [ ] **Step 5: Wire the combined-color-pane reset**

In `combined-color-pane.component.ts`, drop the `DefaultPalette` import, add the service, and add a constructor. `AbstractCombinedPane` has no explicit constructor, so a bare `super()` is correct:

```typescript
import { ChartPaletteService } from "../../../../widget/color-picker/chart-palette.service";
```

```typescript
export class CombinedColorPane extends AbstractCombinedPane {
   @Output() colorChanged: EventEmitter<string> = new EventEmitter<string>();

   constructor(private chartPaletteService: ChartPaletteService) {
      super();
   }

   changeColor(ncolor: string, idx: number) {
      if(this.frameInfos) {
         this.frameInfos[idx].frame.color = ncolor;
         this.colorChanged.emit(ncolor);
      }
   }

   reset() {
      const allColors: string[] = this.chartPaletteService.flatColors();

      for(var i = 0; i < this.frameInfos.length; i++) {
         this.frameInfos[i].frame.changed = false;

         if(this.frameInfos[i].summary && this.frameInfos[i].frame.defaultColor) {
            this.frameInfos[i].frame.color = this.frameInfos[i].frame.defaultColor;
         }
         else {
            this.frameInfos[i].frame.color = allColors[i % allColors.length];
         }
      }
   }
}
```

- [ ] **Step 6: Wire the color-mapping dialog**

In `color-mapping-dialog.component.ts`, add the service to the existing constructor and expose the grid:

```typescript
import { ChartPaletteService } from "../../../widget/color-picker/chart-palette.service";
import { ColorPalette } from "../../../widget/color-picker/color-classes";
```

```typescript
   palette: ColorPalette;

   constructor(private modalService: NgbModal, chartPaletteService: ChartPaletteService) {
      this.palette = chartPaletteService.chartPalette;
   }
```

Then in `color-mapping-dialog.component.html`, change line 58 to pass it:

```html
                <color-editor [(color)]="colorMap.color" [palette]="palette"></color-editor>
```

- [ ] **Step 7: Warm the service at bootstrap**

The service fetches in its constructor, so the first injection triggers it. Inject it in each app component so it is warm before any picker can open. No constructor body change is needed — constructing the service starts the fetch.

All three files sit one directory below `app/`, so the same relative path is correct in each: `portal/app.component.ts`, `composer/app.component.ts`, and `vsobjects/viewer-app.component.ts` all add

```typescript
import { ChartPaletteService } from "../widget/color-picker/chart-palette.service";
```

Then add a parameter to each existing constructor, matching that file's existing indentation and trailing-comma style:

```typescript
               chartPaletteService: ChartPaletteService,
```

Declare it as a plain parameter, not `private` — nothing reads it, and an unused private field will trip lint.

- [ ] **Step 8: Run the affected suites**

```bash
cd community/web
npx ng test portal --include="**/color-field-pane.spec.ts"
npx ng test portal --include="**/chart-palette.service.spec.ts"
npx ng test portal --include="**/color-dropdown.spec.ts"
npx ng test portal:test-tl --include="**/categorical-color-pane.interaction.tl.spec.ts"
```

Expected: PASS. Any component spec that constructs `ColorFieldPane`, `CombinedColorPane`, or `ColorMappingDialog` through `TestBed` now needs `HttpClientTestingModule` (or a `ChartPaletteService` stub) in its providers — add it where a spec fails on a missing `HttpClient` provider.

- [ ] **Step 9: Run the full portal suite**

```bash
cd community/web && npm run test:portal
```

Expected: PASS. Task 5's `color-dropdown.spec.ts` passing here is the proof that no Format-dialog surface changed.

- [ ] **Step 10: Lint**

```bash
cd community/web && npm run lint
```

Expected: clean.

- [ ] **Step 11: Commit**

```bash
cd community
git add web/projects/portal/src/app/binding/widget/color-field-pane.component.ts \
        web/projects/portal/src/app/binding/widget/color-field-pane.spec.ts \
        web/projects/portal/src/app/binding/editor/chart/aesthetic/combined-color-pane.component.ts \
        web/projects/portal/src/app/binding/editor/chart/color-mapping-dialog.component.ts \
        web/projects/portal/src/app/binding/editor/chart/color-mapping-dialog.component.html \
        web/projects/portal/src/app/portal/app.component.ts \
        web/projects/portal/src/app/composer/app.component.ts \
        web/projects/portal/src/app/vsobjects/viewer-app.component.ts
git commit -m "Serve chart series swatch grid from the palette service"
```

---

### Task 8: Verify palette dialog pre-selection

The dialog needs no production change — its color-equality match picks up the new palettes automatically. This task proves it, and documents the no-accidental-pinning behavior.

**Files:**
- Test: `community/web/projects/portal/src/app/binding/editor/chart/palette-dialog.spec.ts`

**Interfaces:**
- Consumes: nothing at runtime; fixtures mirror the palettes declared in Task 1.
- Produces: nothing.

- [ ] **Step 1: Write the tests**

Create `palette-dialog.spec.ts` with the AGPL header, then:

```typescript
import { CategoricalColorModel } from "../../../common/data/visual-frame-model";
import {
   DARK_HEAD,
   LEGACY_HEAD,
   LEGACY_TAIL,
   MODERN_HEAD,
   palette40
} from "../../../widget/color-picker/palette-test-fixtures";
import { PaletteDialog } from "./palette-dialog.component";

function palette(name: string, head: string[]): CategoricalColorModel {
   const model = new CategoricalColorModel();
   model.name = name;
   model.colors = palette40(head);
   return model;
}

function dialogWith(currentColors: string[]): PaletteDialog {
   const dialog = new PaletteDialog();
   dialog.colorPalettes = [
      palette("Default", LEGACY_HEAD),
      palette("Modern", MODERN_HEAD),
      palette("Modern Dark", DARK_HEAD)
   ];
   const curr = new CategoricalColorModel();
   curr.colors = currentColors;
   dialog.currPalette = curr;
   return dialog;
}

describe("PaletteDialog pre-selection", () => {
   it("pre-selects Modern for a chart rendering modern defaults", () => {
      const dialog = dialogWith(palette40(MODERN_HEAD));
      expect(dialog.displayPalette.name).toBe("Modern");
      expect(dialog._reversed).toBe(false);
   });

   it("pre-selects Modern Dark for a chart rendering dark defaults", () => {
      const dialog = dialogWith(palette40(DARK_HEAD));
      expect(dialog.displayPalette.name).toBe("Modern Dark");
   });

   it("still pre-selects Default for a legacy chart", () => {
      const dialog = dialogWith(palette40(LEGACY_HEAD));
      expect(dialog.displayPalette.name).toBe("Default");
   });

   // Nothing matches => index 0. Default is declared first in defaults.css specifically so
   // this fallback is unchanged for existing installs.
   it("falls back to the first palette when nothing matches", () => {
      const custom = MODERN_HEAD.slice();
      custom[3] = "#123456";
      const dialog = dialogWith(custom.concat(LEGACY_TAIL));
      expect(dialog.displayPalette.name).toBe("Default");
   });
});
```

- [ ] **Step 2: Run the tests**

```bash
cd community/web && npx ng test portal --include="**/palette-dialog.spec.ts"
```

Expected: PASS. If `displayPalette.name` is undefined for the reversed path, note that `displayPalette` builds a fresh `CategoricalColorModel` without a name when `_reversed` is true — the assertions above all exercise forward matches, so this does not apply.

- [ ] **Step 3: Commit**

```bash
cd community
git add web/projects/portal/src/app/binding/editor/chart/palette-dialog.spec.ts
git commit -m "Verify palette dialog pre-selects the rendered modern palette"
```

---

### Task 9: Full verification

- [ ] **Step 1: Run the backend suite**

```bash
cd community && ./mvnw test -pl core
```

Expected: PASS, or only pre-existing failures unrelated to palettes. `main` can be unstable — confirm any failure also reproduces on a clean checkout before treating it as caused by this work.

- [ ] **Step 2: Run the frontend suites**

```bash
cd community/web && npm run verify
```

Expected: lint clean, all three suites pass.

- [ ] **Step 3: Build and start the app**

```bash
cd community && ./mvnw clean install -DskipTests && cd docker/target/docker-test && docker compose up -d
```

- [ ] **Step 4: Manual verification matrix**

Set the properties in EM under Settings → Presentation → Look and Feel, or via `SreeEnv`. Reload the browser after each change — the `viz-modern` / `viz-dark` body classes and the palette service are both bootstrap-time.

| State | Expected |
|---|---|
| Gate off | Charts legacy. Palette dialog lists `Modern` / `Modern Dark` but pre-selects `Default`. All swatch grids legacy. |
| Gate on, light | Charts modern. Palette dialog pre-selects `Modern`. Series swatch grid row 1 is the 8 modern colors. |
| Gate on, dark | Palette dialog pre-selects `Modern Dark`. Grid row 1 is the 8 dark colors. |
| Chart with >8 series, gate on | 40 distinct colors, no repeat at series 9. |

- [ ] **Step 5: Verify no accidental pinning**

With the gate on and light mode, open a chart's color binding, open the Palette dialog, select `Modern`, click OK. Then enable dark mode and reload. The chart must follow to dark colors — `updateVisualFrameWrapper0` writes no USER color when the chosen color equals the current one. Repeat selecting `Modern Dark` while in light mode: that one **should** pin and must not follow the gate.

- [ ] **Step 6: Verify the Format dialog is untouched**

Open Format on any object and confirm these four grids are visually identical to a pre-change build: Foreground, Background, Value Fill (on an object where it appears), and the border color picker in the binding border pane. Also spot-check Highlight foreground/background and an annotation format dialog.

- [ ] **Step 7: Verify export**

Export a gate-on viewsheet with a categorical chart to PDF, PNG, and Excel. Colors must match live view, since export and live view share the resolver.

- [ ] **Step 8: Final commit if anything was fixed**

```bash
cd community && git status
```

---

## Notes for the implementer

**Why the fallback branches are not reachable via CSS.** `format.css` merges *on top* of `defaults.css`; it cannot delete rules. Once Task 1 declares complete `Modern` and `Modern Dark` palettes, no customer configuration can produce a missing or short palette. That is exactly why Task 2 makes `fromFrame` a package-private pure function — it is the only way to test the guards. Do not try to write a CSS fixture that removes rules.

**Why the memo exists.** `ColorPalettes.getPalette()` is `synchronized(ColorPalettes.class)`. `applyModernPalette()` runs per chart render on potentially parallel threads, and today touches no shared monitor. Without the memo, every concurrent chart render would serialize on that global lock.

**The 10-second staleness window.** `CSSDictionary` throttles its `lastModified` check to 10 seconds, so a `format.css` palette edit can take up to 10 seconds to appear. This is pre-existing behavior shared by every CSS-driven value — do not add a shorter poll.

**Do not widen the frontend scope.** If a spec fails because some component now needs `HttpClientTestingModule`, add the test provider. Do not "fix" it by reverting to `DefaultPalette.chart` in a shared default, and do not add the service to `color-editor`, `cp-color-pane`, `color-picker`, or `color-dropdown`.

## Deliberate deviations from the design spec

Two items in the spec's test list were consolidated rather than implemented literally. Both are
recorded here so the choice is reviewable.

**1. Per-template Format-pane tests → the truth table plus manual check.** The spec called for
component tests asserting `vs-formats-pane`'s three pickers and `binding-border-pane`'s border picker
each resolve their expected grid. Those components pull in large dependency graphs, and this work
does not touch their templates. What can actually regress is the *branch order* inside
`getPalette()`, which Task 5 pins directly, plus removal of a `[chart]="false"` flag, which Task 9
Step 6 checks visually. If you would rather have the template-level tests, add them to Task 5 — the
tradeoff is TestBed setup cost against catching an edit nobody in this plan makes.

**3. The spec's memoization test was dropped, then partially restored.** The spec's Testing section
required "Memoization: unchanged CSS timestamp resolves identically; a bumped timestamp re-resolves."
Task 3's test block omitted it, and this section originally failed to record that — the omission was
caught by the final whole-branch review, not by the plan. Partially remedied afterwards: tests now
cover that repeated resolves return equal-but-distinct arrays and that `clearMemo()` discards the
entry. The stamp-bump half remains untested and is parked: `CSSDictionary.resetDictionaryCache()`
never clears `ORG_LAST_CHECK` / `ORG_LAST_MODIFIED`, and `getOrgScopedCSSLastModified` throttles on a
10-second wall-clock window with no reset hook, so exercising it needs either a multi-second sleep or
reflection into another class's private statics. Revisit if `CSSDictionary` gains a test seam.

**2. `categorical-color-pane.interaction.tl.spec.ts` is run, not extended.** The spec called for
extending it to cover the series grid. Task 7 Step 8 runs it as a regression check instead, because
the grid contract is already covered end to end by `chart-palette.service.spec.ts` (fetch, shape,
both fallbacks) and `color-field-pane.spec.ts` (the component reads the service). Extending the TL
spec would re-assert the same contract through a heavier harness. Add it if you want DOM-level proof
that the rendered swatches change.
