# Chart Palette Re-tune — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `Modern` and `Modern Dark` render the redesigned eight head colours, `Contrast` joins the palette picker, and the picker's list is scoped by the chart's own provenance mark so a modern chart is no longer offered the nine retired palettes — the eight single-hue ramps plus Pastel.

**Architecture:** Palette data plus one endpoint change plus a dropdown filter. The eight head hexes move in two places that must agree — `defaults.css` and the `MODERN_HEAD`/`DARK_HEAD` fallback constants. A new `hiddenPaletteNames(VizContext)` in `VSChartPaletteDefaults` names what a modern chart should not be offered; the endpoint attaches it to the response as a per-palette `hidden` flag rather than dropping entries, and the dialog filters its dropdown. Nothing in `inetsoft.graph` changes, no colour-frame class is added, and no persisted contract moves.

**Tech Stack:** Java 21, Maven, JUnit 5, Spring. Angular 21 / TypeScript 5.9 / Vitest on the browser side.

**Spec:** [2026-09-11-chart-palette-retune-design.md](../specs/lookfeel/2026-09-11-chart-palette-retune-design.md)

---

## Global Constraints

- **Branch:** `feature-chart-palette-retune`, already created off `epic-74519`. Never commit to `main`, `v1.0.x` or `v1.1.x`.
- **CSS indexes are 1-based.** `ColorPalettes.loadPalettes` skips any index below 1 (`ColorPalettes.java:130-133`). The source handoff's `CSS.md` is written 0-based; applied verbatim every palette loses its first colour. Every rule in this plan is written `index='1'` through `index='8'`.
- **`defaults.css` and the Java constants must move in the same commit.** `MODERN_HEAD`/`DARK_HEAD` are the fallback `fromFrame` uses when a CSS rule is missing or malformed (`VSChartPaletteDefaults.java:108-130`). Stale constants make a broken `format.css` silently render the old palette.
- **Indexes 9-40 of `Modern` and `Modern Dark` are not touched.** `ColorPalettesModernTest.tailMatchesLegacyPalette` and `VSChartPaletteDefaultsTest.gateOnSwapsToModernHeadButKeepsLegacyTail` are the drift guards that prove only the head moved. If either fails, the edit went too far.
- **`Contrast` is appended at the end of `defaults.css`.** `CSSDictionary.getCSSAttributeValues` returns a `LinkedHashSet` (`CSSDictionary.java:949`) and `ColorPalettes` stores into an `OrderedMap`, so CSS declaration order *is* picker order. `Default` must stay the first declaration — the dialog's index-0 fallback depends on it, pinned by `palette-dialog.spec.ts:75-80`.
- **`ColorPalettes.getPaletteNames()` and `getPalette(name)` stay unfiltered.** `legacyPalette()` resolves `"Default"` and `modernPalette()`/`darkPalette()` resolve `"Modern"`/`"Modern Dark"` by name (`VSChartPaletteDefaults.java:47-84`). Filtering either would break rendering, not just the picker.
- **CSS style:** three-space indent and lowercase hex, matching the `Default`/`Modern`/`Modern Dark` blocks. The 92 older `color:` lines use two spaces; do not copy those.
- **Comments:** short clauses. No ticket numbers, PR numbers, or references to this plan, the spec or the roadmap inside source comments.
- **Java test command (from `community/`):** `./mvnw test -pl core -Dtest=<ClassName>` — PowerShell `.\mvnw.cmd test -pl core "-Dtest=<ClassName>"`. Surefire runs `<groups>core</groups>`, so a new test class needs `@Tag("core")`. Multiple classes are **comma** separated.
- **Frontend test command (from `community/web/`):** `npx ng test portal --include='**/<file>.spec.ts'`. Always scope with `--include`; never run the suite unscoped.

### The new hexes, once, for reference

| Slot | 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 |
|---|---|---|---|---|---|---|---|---|
| **Modern** | `0490ff` | `ff5a35` | `241c4f` | `03d9b3` | `9a2ddc` | `ffb020` | `e5197e` | `8ed604` |
| **Modern Dark** | `4fa5ff` | `ff8367` | `49447d` | `2deec6` | `ae41f5` | `ffcb82` | `fe3290` | `9feb28` |
| **Contrast** | `0b3d91` | `f5943f` | `b04ab8` | `f7d22e` | `a8330a` | `7cc4ee` | `00947f` | `5fa83c` |

---

## File Structure

| File | Responsibility | Task |
|---|---|---|
| `core/src/main/resources/inetsoft/util/css/defaults.css` | The palette definitions | 1, 3 |
| `core/src/main/java/inetsoft/uql/viewsheet/internal/VSChartPaletteDefaults.java` | Head constants; the hidden-name policy | 1, 4 |
| `core/src/main/java/inetsoft/web/binding/VSChartBindingService.java` | Resolve a chart's mark on the node that owns the runtime | 5 |
| `core/src/main/java/inetsoft/web/binding/VSChartBindingController.java` | Build the palette list, attach the flag | 5 |
| `core/src/main/java/inetsoft/web/binding/model/graph/aesthetic/CategoricalColorModel.java` | The `hidden` transport flag | 5 |
| `web/projects/portal/src/app/common/data/visual-frame-model.ts` | The browser side of that flag | 5 |
| `web/projects/portal/src/app/binding/editor/chart/aesthetic/categorical-color-pane.component.ts` | Send the assembly context | 6 |
| `web/projects/portal/src/app/binding/editor/chart/palette-dialog.component.ts` | Filter the dropdown | 6 |
| `web/projects/portal/src/app/widget/color-picker/palette-test-fixtures.ts` | Browser mirror of the head hexes | 2 |
| Five Java test classes (see Task 1) | Existing assertions on the old hexes; the new CSS/Java drift guard | 1 |
| `web/projects/portal/src/app/widget/color-picker/chart-palette.service.spec.ts` | Hardcoded hexes, not fixture-relative | 2 |

---

## Task 1: Re-tune Modern and Modern Dark

**Files:**
- Modify: `core/src/test/java/inetsoft/uql/viewsheet/internal/VSChartPaletteDefaultsTest.java` (19 sites)
- Modify: `core/src/test/java/inetsoft/uql/viewsheet/graph/aesthetic/ColorPalettesModernTest.java:48-53`
- Modify: `core/src/test/java/inetsoft/web/portal/controller/ChartColorPaletteControllerTest.java:60-72`
- Modify: `core/src/test/java/inetsoft/uql/viewsheet/internal/VSChartPaletteCssOverrideTest.java:71,106,107`
- Modify: `core/src/test/java/inetsoft/report/composition/graph/VGraphPairModernPaletteTest.java:56`
- Modify: `core/src/main/resources/inetsoft/util/css/defaults.css:178-208` and `:338-368`
- Modify: `core/src/main/java/inetsoft/uql/viewsheet/internal/VSChartPaletteDefaults.java:180-188`

**Interfaces:**
- Consumes: nothing.
- Produces: `VSChartPaletteDefaults.modernPalette()[0..7]` and `darkPalette()[0..7]` returning the new bases. Every later task reads these through existing accessors; no signature changes here.

**Why the test list is longer than the spec's.** The spec's §4 names only `ColorPalettesModernTest`. A sweep for the sixteen old hexes finds **five** classes carrying genuine assertions on them. Three further classes — `CategoricalColorDerivedPersistenceTest` (12 sites), `VizModernizeUtilTest:659`, and a comment in `CategoricalColorFactoryResolvedDefaultGuardTest:75` — use `0x00D4E8` and friends as *arbitrary fixture colours* passed into `setDerivedColor`, never asserting the palette. Those pass either way. **Leave them alone**; changing them is churn that hides the real diff.

- [ ] **Step 1: Update the assertions to the new hexes**

`VSChartPaletteDefaultsTest` — nineteen sites. The light substitutions are `0x00D4E8 → 0x0490FF`, `0x64748B → 0x8ED604`, `0x00B87A → 0xFF5A35`; the dark ones are `0x22D3EE → 0x4FA5FF`, `0x94A3B8 → 0x9FEB28`, `0x10B981 → 0xFF8367`. Applied at `:54`, `:55`, `:62`, `:63`, `:97`, `:98`, `:104`, `:105`, `:185`, `:186`, `:196`, `:197`, `:210`, `:216`, `:219`, `:236`, `:239`, `:287`, plus the fixture at `:175-178`:

```java
   private static final Color[] MODERN_HEAD_FIXTURE = {
      new Color(0x0490FF), new Color(0xFF5A35), new Color(0x241C4F), new Color(0x03D9B3),
      new Color(0x9A2DDC), new Color(0xFFB020), new Color(0xE5197E), new Color(0x8ED604)
   };
```

That fixture is only ever passed as the `head` argument to `fromFrame`, and its four tests assert `spliceLegacy(FIXTURE)` against the result — they are self-consistent and would pass with any values. Update it anyway so a reader is not told two different stories about what the modern head is.

`ColorPalettesModernTest:46-54`:

```java
   @Test
   void modernHeadMatchesSpec() {
      CategoricalColorFrame modern = ColorPalettes.getPalette("Modern");
      assertEquals(new Color(0x0490FF), modern.getDefaultColor(0));
      assertEquals(new Color(0x8ED604), modern.getDefaultColor(7));

      CategoricalColorFrame dark = ColorPalettes.getPalette("Modern Dark");
      assertEquals(new Color(0x4FA5FF), dark.getDefaultColor(0));
      assertEquals(new Color(0x9FEB28), dark.getDefaultColor(7));
   }
```

`ChartColorPaletteControllerTest` asserts lowercase strings, not `Color`:

```java
      assertEquals("#0490ff", colors[0]);    // :60
      assertEquals("#8ed604", colors[7]);    // :61
      assertEquals("#4fa5ff", colors[0]);    // :71
      assertEquals("#9feb28", colors[7]);    // :72
```

`VSChartPaletteCssOverrideTest`: `:71` and `:106` become `new Color(0x0490FF)`, `:107` becomes `new Color(0x8ED604)`.

`VGraphPairModernPaletteTest:56` becomes `assertEquals(new Color(0x0490FF), frame.getColor(0));`.

- [ ] **Step 2: Run the five classes to verify they fail**

```
./mvnw test -pl core -Dtest=VSChartPaletteDefaultsTest,ColorPalettesModernTest,ChartColorPaletteControllerTest,VSChartPaletteCssOverrideTest,VGraphPairModernPaletteTest
```

Expected: failures in all five, every one an `expected: <java.awt.Color[r=4,g=144,b=255]> but was: <java.awt.Color[r=0,g=212,b=232]>` shape. **If any class passes, stop** — it means the assertions were not actually updated, or the class was already resolving from somewhere other than `defaults.css`.

- [ ] **Step 3: Move the CSS head colours**

`defaults.css:178-208`, eight rules, values only — selectors and indentation unchanged:

```css
ChartPalette[name='Modern'][index='1'] {
   color: #0490ff;
}

ChartPalette[name='Modern'][index='2'] {
   color: #ff5a35;
}

ChartPalette[name='Modern'][index='3'] {
   color: #241c4f;
}

ChartPalette[name='Modern'][index='4'] {
   color: #03d9b3;
}

ChartPalette[name='Modern'][index='5'] {
   color: #9a2ddc;
}

ChartPalette[name='Modern'][index='6'] {
   color: #ffb020;
}

ChartPalette[name='Modern'][index='7'] {
   color: #e5197e;
}

ChartPalette[name='Modern'][index='8'] {
   color: #8ed604;
}
```

`defaults.css:338-368`, the same eight indexes under `name='Modern Dark'`, taking `#4fa5ff`, `#ff8367`, `#49447d`, `#2deec6`, `#ae41f5`, `#ffcb82`, `#fe3290`, `#9feb28` in order.

Index 9 of each palette (`:210`, `:370`) is the first legacy-tail entry and must not move.

- [ ] **Step 4: Move the Java fallback constants**

`VSChartPaletteDefaults.java:180-188`:

```java
   private static final Color[] MODERN_HEAD = {
      new Color(0x0490FF), new Color(0xFF5A35), new Color(0x241C4F), new Color(0x03D9B3),
      new Color(0x9A2DDC), new Color(0xFFB020), new Color(0xE5197E), new Color(0x8ED604)
   };

   private static final Color[] DARK_HEAD = {
      new Color(0x4FA5FF), new Color(0xFF8367), new Color(0x49447D), new Color(0x2DEEC6),
      new Color(0xAE41F5), new Color(0xFFCB82), new Color(0xFE3290), new Color(0x9FEB28)
   };
```

- [ ] **Step 5: Run the five classes to verify they pass**

Same command as Step 2. All five green, and specifically `tailMatchesLegacyPalette` and `gateOnSwapsToModernHeadButKeepsLegacyTail` green — those are the proof the tail is intact.

- [ ] **Step 6: Add the drift guard**

Nothing today catches `defaults.css` and the Java fallback constants disagreeing: every existing test asserts one side or the other, never that they match. This whole task depends on them moving together, so pin it.

`MODERN_HEAD` and `DARK_HEAD` are `private static final`. Read them by reflection rather than widening them for a test — `VSChartPaletteDefaultsTest.clearMemoDiscardsTheCachedEntry:274-288` already reaches the private `MEMO` field the same way, so the idiom is established in this exact class. Append there:

```java
   // The CSS rules and the Java fallback constants are two statements of the same eight colors.
   // Every other test asserts one side or the other, so only this one fails when they drift - and
   // a drift is silent in production until a malformed format.css makes the fallback fire.
   @Test
   void cssHeadMatchesTheJavaFallback() throws Exception {
      assertHeadMatches("MODERN_HEAD", "Modern");
      assertHeadMatches("DARK_HEAD", "Modern Dark");
   }

   private void assertHeadMatches(String fieldName, String paletteName) throws Exception {
      Field field = VSChartPaletteDefaults.class.getDeclaredField(fieldName);
      field.setAccessible(true);
      Color[] head = (Color[]) field.get(null);
      CategoricalColorFrame css = ColorPalettes.getPalette(paletteName);

      assertEquals(8, head.length, fieldName + " must declare exactly the eight head colors");

      for(int i = 0; i < head.length; i++) {
         assertEquals(head[i], css.getDefaultColor(i),
                      paletteName + " index " + (i + 1) + " must match " + fieldName);
      }
   }
```

Add `import inetsoft.uql.viewsheet.graph.aesthetic.ColorPalettes;` to the test.

- [ ] **Step 7: Prove the guard actually guards**

A test that passes before and after a change proves nothing on its own. Temporarily change one `MODERN_HEAD` entry to `new Color(0x000000)`, run `./mvnw test -pl core -Dtest=VSChartPaletteDefaultsTest`, and confirm `cssHeadMatchesTheJavaFallback` fails naming index 1. Revert the edit and re-run to green. Do not commit the broken state.

- [ ] **Step 8: Commit**

```bash
git add core/src/main/resources/inetsoft/util/css/defaults.css core/src/main/java/inetsoft/uql/viewsheet/internal/VSChartPaletteDefaults.java core/src/test/java/inetsoft/uql/viewsheet/internal/VSChartPaletteDefaultsTest.java core/src/test/java/inetsoft/uql/viewsheet/graph/aesthetic/ColorPalettesModernTest.java core/src/test/java/inetsoft/web/portal/controller/ChartColorPaletteControllerTest.java core/src/test/java/inetsoft/uql/viewsheet/internal/VSChartPaletteCssOverrideTest.java core/src/test/java/inetsoft/report/composition/graph/VGraphPairModernPaletteTest.java
git commit -m "Re-tune the Modern and Modern Dark head colours"
```

State in the body that the CSS and the Java fallback constants moved together, and that indexes 9-40 are untouched.

---

## Task 2: Re-tune the browser's palette fixtures

**Files:**
- Modify: `web/projects/portal/src/app/widget/color-picker/palette-test-fixtures.ts:24-30`
- Modify: `web/projects/portal/src/app/widget/color-picker/chart-palette.service.spec.ts:53,54,63,74`
- Test: `web/projects/portal/src/app/widget/color-picker/chart-palette.service.spec.ts`

**Interfaces:**
- Consumes: nothing from Task 1 at runtime — these are browser-side fixtures describing what the server now sends.
- Produces: `MODERN_HEAD` and `DARK_HEAD` exports carrying the new hexes, consumed by four spec files.

**Why this is its own task.** The fixtures are self-consistent: `palette40(MODERN_HEAD)` builds the array *and* the assertions read from it, so nothing breaks if they stay stale — they just describe a palette the server no longer serves. The exception is `chart-palette.service.spec.ts`, which **hardcodes the hexes** rather than reading the fixture, so it breaks the moment the fixture moves. The spec's claim that all four consumers "assert fixture-relative and follow automatically" is true of three of them and false of this one.

- [ ] **Step 1: Update the fixtures**

`palette-test-fixtures.ts:24-30`:

```typescript
export const MODERN_HEAD: string[] = [
   "#0490ff", "#ff5a35", "#241c4f", "#03d9b3", "#9a2ddc", "#ffb020", "#e5197e", "#8ed604"
];

export const DARK_HEAD: string[] = [
   "#4fa5ff", "#ff8367", "#49447d", "#2deec6", "#ae41f5", "#ffcb82", "#fe3290", "#9feb28"
];
```

`LEGACY_HEAD` and `LEGACY_TAIL` are unchanged.

- [ ] **Step 2: Run the consumer specs to verify one fails**

```
npx ng test portal --include='**/chart-palette.service.spec.ts' --include='**/combined-color-pane.spec.ts' --include='**/color-field-pane.spec.ts' --include='**/palette-dialog.spec.ts'
```

Expected: `chart-palette.service.spec.ts` fails on "chunks 40 colors into a 5x8 grid" and "exposes a flat 40-entry list". The other three pass — they read the fixture. **If all four pass, the fixture edit did not land.**

- [ ] **Step 3: Make the hardcoded spec read the fixture**

`chart-palette.service.spec.ts:53-54` and `:63` stop restating the hexes. Import `MODERN_HEAD` — the file already imports `palette40` from the same module (`:22`) — and assert through it, so this file can never drift again:

```typescript
      expect(grid[0][0]).toBe(MODERN_HEAD[0]);
      expect(grid[0][7]).toBe(MODERN_HEAD[7]);
```

and:

```typescript
      expect(service.flatColors()[0]).toBe(MODERN_HEAD[0]);
```

`:74` (`flush(["#00d4e8", "#00b87a"])`) proves a two-colour response falls back; the values are arbitrary and never asserted. Change it to `flush(MODERN_HEAD.slice(0, 2))` so no stale hex survives in the file.

- [ ] **Step 4: Run the four specs to verify they pass**

Same command as Step 2. All four green.

- [ ] **Step 5: Commit**

```bash
git add web/projects/portal/src/app/widget/color-picker/palette-test-fixtures.ts web/projects/portal/src/app/widget/color-picker/chart-palette.service.spec.ts
git commit -m "Point the browser palette fixtures at the re-tuned head colours"
```

---

## Task 3: Add the Contrast palette

**Files:**
- Modify: `core/src/test/java/inetsoft/uql/viewsheet/graph/aesthetic/ColorPalettesModernTest.java`
- Modify: `core/src/main/resources/inetsoft/util/css/defaults.css` (append after `:864`)

**Interfaces:**
- Consumes: `ColorPalettes.getPaletteNames()`, `ColorPalettes.getPalette(String)`.
- Produces: a palette named `Contrast` with eight declared colours, discoverable with no Java registration. Task 4 relies on it existing; Task 5 serves it.

**Why eight slots is fine.** `Soft` already ships at 8, and `PaletteDialog.saveChanges()` (`palette-dialog.component.ts:122-127`) splices the chosen palette over the first *n* of the chart's colours and keeps the rest, so a short palette is an established shape. `Contrast` is never resolved by `VSChartPaletteDefaults`, so `fromFrame`'s 40-colour floor (`:115`) never sees it.

- [ ] **Step 1: Write the failing test**

Append to `ColorPalettesModernTest`:

```java
   @Test
   void contrastIsRegisteredWithEightColors() {
      assertTrue(ColorPalettes.getPaletteNames().contains("Contrast"),
                 "Contrast palette must be declared in defaults.css");

      CategoricalColorFrame contrast = ColorPalettes.getPalette("Contrast");
      assertNotNull(contrast);
      assertEquals(8, contrast.getColorCount());

      for(int i = 0; i < 8; i++) {
         assertNotNull(contrast.getDefaultColor(i), "index " + (i + 1) + " must not be null");
      }

      assertEquals(new Color(0x0B3D91), contrast.getDefaultColor(0));
      assertEquals(new Color(0x5FA83C), contrast.getDefaultColor(7));
   }

   // Default must stay the first declaration: the palette dialog falls back to index 0 when a
   // chart's colors match nothing, and that fallback is only correct if index 0 is Default.
   @Test
   void defaultIsStillDeclaredFirst() {
      assertEquals("Default", ColorPalettes.getPaletteNames().iterator().next());
   }
```

- [ ] **Step 2: Run it to verify it fails**

`./mvnw test -pl core -Dtest=ColorPalettesModernTest`

Expected: `contrastIsRegisteredWithEightColors` fails on the `getPaletteNames().contains` assertion. `defaultIsStillDeclaredFirst` should **pass already** — it pins existing behaviour so Step 3 cannot break it.

- [ ] **Step 3: Append the CSS block**

At the end of `defaults.css`, after the `Heat 24` index 24 rule that currently closes the file at `:864`. Appending puts `Contrast` last in the picker and leaves every existing position untouched:

```css

ChartPalette[name='Contrast'][index='1'] {
   color: #0b3d91;
}

ChartPalette[name='Contrast'][index='2'] {
   color: #f5943f;
}

ChartPalette[name='Contrast'][index='3'] {
   color: #b04ab8;
}

ChartPalette[name='Contrast'][index='4'] {
   color: #f7d22e;
}

ChartPalette[name='Contrast'][index='5'] {
   color: #a8330a;
}

ChartPalette[name='Contrast'][index='6'] {
   color: #7cc4ee;
}

ChartPalette[name='Contrast'][index='7'] {
   color: #00947f;
}

ChartPalette[name='Contrast'][index='8'] {
   color: #5fa83c;
}
```

There is deliberately no `Contrast Dark`. The palette's contract is paper and projection.

- [ ] **Step 4: Run it to verify it passes**

`./mvnw test -pl core -Dtest=ColorPalettesModernTest` — all seven tests green.

- [ ] **Step 5: Commit**

```bash
git add core/src/main/resources/inetsoft/util/css/defaults.css core/src/test/java/inetsoft/uql/viewsheet/graph/aesthetic/ColorPalettesModernTest.java
git commit -m "Add the Contrast categorical palette"
```

---

## Task 4: The hidden-name policy

**Files:**
- Modify: `core/src/test/java/inetsoft/uql/viewsheet/internal/VSChartPaletteDefaultsTest.java`
- Modify: `core/src/main/java/inetsoft/uql/viewsheet/internal/VSChartPaletteDefaults.java`

**Interfaces:**
- Consumes: `VizContext.modern`, `VizContext.of(VizMark)`, `VizContext.ofGate()`.
- Produces: `public static Set<String> hiddenPaletteNames(VizContext ctx)` — the names a picker should mark hidden for that context. Empty under a classic mark. Task 5 is its only caller.

**Why here.** This class already owns modern-palette policy, already imports `ColorPalettes`, and sits in the same package as `VizContext`. It returns names to *mark*, never a filtered list — Task 5 explains why nothing is removed.

- [ ] **Step 1: Write the failing tests**

Append to `VSChartPaletteDefaultsTest`:

```java
   @Test
   void hiddenNamesAreEmptyForAClassicChart() {
      assertTrue(VSChartPaletteDefaults.hiddenPaletteNames(VizContext.of((VizMark) null)).isEmpty(),
                 "a classic chart keeps every palette it has today");
   }

   @Test
   void hiddenNamesAreTheNineRampsForAModernChart() {
      Set<String> hidden = VSChartPaletteDefaults.hiddenPaletteNames(VizContext.of(VizMark.MODERN_LIGHT));

      assertEquals(Set.of("Pastel", "Heat 8", "Heat 16", "Heat 24",
                          "Blue", "Green", "Red", "Orange", "Gray"), hidden);
   }

   @Test
   void hiddenNamesAreTheSameUnderADarkMark() {
      assertEquals(VSChartPaletteDefaults.hiddenPaletteNames(VizContext.of(VizMark.MODERN_LIGHT)),
                   VSChartPaletteDefaults.hiddenPaletteNames(VizContext.of(VizMark.MODERN_DARK)),
                   "dark is a modifier of modern, not a different palette set");
   }

   // The five a modern chart keeps. Default stays so the dialog's index-0 fallback is unchanged
   // and a chart has a route back to the classic 40 without reverting the whole dashboard.
   @Test
   void hiddenNamesNeverCoverTheKeptFive() {
      Set<String> hidden = VSChartPaletteDefaults.hiddenPaletteNames(VizContext.of(VizMark.MODERN_LIGHT));

      for(String kept : new String[]{ "Default", "Soft", "Modern", "Modern Dark", "Contrast" }) {
         assertFalse(hidden.contains(kept), kept + " must stay offered to a modern chart");
      }
   }

   @Test
   void hiddenNamesFollowTheGateWhenThereIsNoAssembly() {
      SreeEnv.setProperty("viewsheet.modernVisualization", "false");
      assertTrue(VSChartPaletteDefaults.hiddenPaletteNames(VizContext.ofGate()).isEmpty());

      SreeEnv.setProperty("viewsheet.modernVisualization", "true");
      assertEquals(9, VSChartPaletteDefaults.hiddenPaletteNames(VizContext.ofGate()).size());
   }
```

Add `import java.util.Set;` to the test's imports.

- [ ] **Step 2: Run them to verify they fail**

`./mvnw test -pl core -Dtest=VSChartPaletteDefaultsTest`

Expected: compilation failure — `hiddenPaletteNames` does not exist. That is the correct red state for a new method.

- [ ] **Step 3: Write the implementation**

In `VSChartPaletteDefaults`, beside the other public accessors:

```java
   /**
    * Palette names a picker should mark hidden for the given context. A modern chart is not
    * offered the single-hue ramps; a classic chart keeps everything. Never removes a name from
    * resolution - getPalette must still answer for every one of these.
    */
   public static Set<String> hiddenPaletteNames(VizContext ctx) {
      return ctx.modern ? MODERN_HIDDEN : Set.of();
   }
```

and with the other constants:

```java
   private static final Set<String> MODERN_HIDDEN =
      Set.of("Pastel", "Heat 8", "Heat 16", "Heat 24", "Blue", "Green", "Red", "Orange", "Gray");
```

Add `import java.util.Set;`.

- [ ] **Step 4: Run them to verify they pass**

`./mvnw test -pl core -Dtest=VSChartPaletteDefaultsTest` — green, including every pre-existing test in the class.

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/inetsoft/uql/viewsheet/internal/VSChartPaletteDefaults.java core/src/test/java/inetsoft/uql/viewsheet/internal/VSChartPaletteDefaultsTest.java
git commit -m "Name the palettes a modern chart is not offered"
```

---

## Task 5: Serve the flag with assembly context

**Files:**
- Modify: `core/src/main/java/inetsoft/web/binding/model/graph/aesthetic/CategoricalColorModel.java`
- Modify: `core/src/main/java/inetsoft/web/binding/VSChartBindingService.java`
- Modify: `core/src/main/java/inetsoft/web/binding/VSChartBindingController.java:234-253`
- Modify: `web/projects/portal/src/app/common/data/visual-frame-model.ts:40-51`
- Create: `core/src/test/java/inetsoft/web/binding/VSChartBindingColorPalettesTest.java`

**Interfaces:**
- Consumes: `VSChartPaletteDefaults.hiddenPaletteNames(VizContext)` from Task 4.
- Produces:
  - `CategoricalColorModel.isHidden()` / `setHidden(boolean)`, serialized as `hidden`.
  - `VSChartBindingService.getChartVizMark(@ClusterProxyKey String vsId, String assemblyName, Principal principal)` returning `VizMark` (nullable).
  - `getColorPalettes` accepting optional `vsId` and `assemblyName` query params.

**The shape, and why it is not what the spec drew.** The spec proposed moving the whole `getColorPalettes` body into the service as a `@ClusterProxyMethod`. Two facts make a smaller cut better. First, `@ClusterProxyKey` is the routing key — the generated proxy calls `_wsService.isLocal(vsId)` and then `affinityCall(vsId, ...)` (`VSChartBindingServiceProxy.java:49-60`), so a null `vsId` has no node to route to, and the target-band pane sends none. The controller must branch on that regardless. Second, the controller already holds `visualService`, and keeping the list-building there means one code path for the list and one small branch for the context. So the proxy carries only the mark.

Org context survives the hop either way: `@SwitchOrg` pushes a `SwitchOrgAspectTask` onto `ServiceProxyContext.aspectTasks` (`EventAspect.java:576`), which the generated callable replays via `serviceProxyContext.preprocess()` on the receiving node.

- [ ] **Step 1: Write the failing test**

```java
package inetsoft.web.binding;

import inetsoft.sree.SreeEnv;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.util.css.CSSDictionary;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class VSChartBindingColorPalettesTest {
   @AfterEach
   void reset() {
      SreeEnv.setProperty("viewsheet.modernVisualization", null);
      CSSDictionary.resetDictionaryCache();
   }

   // The response must carry every palette, hidden ones included, or the dialog's color-equality
   // match cannot pre-select a chart that is sitting on a retired palette - and an unmatched
   // dialog repaints the chart with Default on a no-op OK.
   @Test
   void aModernContextHidesNineAndOmitsNothing() {
      Set<String> hidden =
         VSChartPaletteDefaults.hiddenPaletteNames(VizContext.of(VizMark.MODERN_LIGHT));

      assertEquals(9, hidden.size());
      assertTrue(hidden.contains("Heat 8"));
      assertFalse(hidden.contains("Default"));
   }

   @Test
   void hiddenIsOffByDefaultOnTheModel() {
      assertFalse(new inetsoft.web.binding.model.graph.aesthetic.CategoricalColorModel().isHidden(),
                  "a palette is visible unless the server says otherwise");
   }
}
```

- [ ] **Step 2: Run it to verify it fails**

`./mvnw test -pl core -Dtest=VSChartBindingColorPalettesTest`

Expected: compilation failure on `isHidden()`.

- [ ] **Step 3: Add the transport flag**

In `CategoricalColorModel`, beside the other private fields and accessors:

```java
   public boolean isHidden() {
      return hidden;
   }

   public void setHidden(boolean hidden) {
      this.hidden = hidden;
   }
```

and with the fields at `:177-185`:

```java
   private boolean hidden;
```

It goes on `CategoricalColorModel`, not the `VisualFrameModel` base: only palettes are ever hidden, and the base is shared by the shape, size, texture and line frame models. It reaches no persisted state — `createVisualFrame()` (`:165-167`) returns a bare `new CategoricalColorFrame()` and reads no transport field, and `CategoricalColorFrameWrapper.writeContents()/parseContents()` (`:182`, `:267`) never touch it.

The browser mirror, `visual-frame-model.ts:40-51`:

```typescript
export class CategoricalColorModel extends ColorFrameModel {
   clazz: string = "inetsoft.web.binding.model.graph.aesthetic.CategoricalColorModel";
   colors: string[];
   cssColors: string[];
   defaultColors: string[];
   colorMaps: ColorMap[];
   globalColorMaps: ColorMap[];
   useGlobal: boolean;
   shareColors: boolean;
   dateFormat: number;
   colorValueFrame?: boolean;
   hidden?: boolean;
}
```

- [ ] **Step 4: Add the proxy method**

In `VSChartBindingService`, following the `getChartBinding` shape at `:66-73`:

```java
   /**
    * The chart's own provenance mark, read on the node that owns the runtime viewsheet. Null for
    * an unmarked chart, a missing assembly, or an assembly that is not a chart.
    */
   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public VizMark getChartVizMark(@ClusterProxyKey String vsId, String assemblyName,
                                  Principal principal)
      throws Exception
   {
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(Tool.byteDecode(vsId), principal);
      Viewsheet viewsheet = rvs.getViewsheet();
      Assembly assembly = viewsheet == null ? null : viewsheet.getAssembly(assemblyName);

      if(!(assembly instanceof ChartVSAssembly chart)) {
         return null;
      }

      return chart.getVSAssemblyInfo().getVizMark();
   }
```

Add `import inetsoft.uql.viewsheet.internal.VizMark;` — the class already imports `ChartVSAssemblyInfo` from that package but not `VizMark`. `Assembly` arrives with the existing `inetsoft.uql.viewsheet.*` import.

The proxy class is generated by `inetsoft.cluster.apt.ClusterAnnotationProcessor`; do not hand-edit `VSChartBindingServiceProxy`.

- [ ] **Step 5: Widen the endpoint**

`VSChartBindingController:234-253` becomes:

```java
   @RequestMapping(value = "/api/composer/chart/colorpalettes", method = RequestMethod.GET)
   @HandleExceptions
   @SwitchOrg
   public CategoricalColorModel[] getColorPalettes(
      @OrganizationID String orgId,
      @RequestParam(required = false, value = "vsId") String vsId,
      @RequestParam(required = false, value = "assemblyName") String assemblyName,
      Principal principal)
      throws Exception
   {
      VizContext ctx = vsId == null || assemblyName == null
         ? VizContext.ofGate()
         : VizContext.of(chartBindingService.getChartVizMark(vsId, assemblyName, principal));
      Set<String> hidden = VSChartPaletteDefaults.hiddenPaletteNames(ctx);
      String[] names = ColorPalettes.getPaletteNames().toArray(new String[0]);
      CategoricalColorModel[] palettes = new CategoricalColorModel[names.length];

      for(int i = 0; i < names.length; i++) {
         CategoricalColorFrame palette = ColorPalettes.getPalette(names[i]);
         CategoricalColorFrameWrapper wrapper = new CategoricalColorFrameWrapper();
         wrapper.setVisualFrame(palette);
         CategoricalColorModel model = visualService.createVisualFrameModel(wrapper);
         model.setName(names[i]);
         model.setHidden(hidden.contains(names[i]));
         palettes[i] = model;
      }

      return palettes;
   }
```

Add `import inetsoft.uql.viewsheet.internal.VizContext;` and `import inetsoft.uql.viewsheet.internal.VSChartPaletteDefaults;`. `Set` arrives with the existing `java.util.*` import; `ColorPalettes`, `CategoricalColorFrame` and `CategoricalColorFrameWrapper` are already imported via the wildcard imports at `:21` and `:31`.

The absent-assembly branch is what the target-band colour pane lands on: it sends only `assetId` (`b-categorical-color-pane.component.ts:62-66`), so it resolves through `ofGate()` — the same authority `ChartColorPaletteController:45` already uses for a global swatch list with no assembly in scope.

- [ ] **Step 6: Run the test to verify it passes**

`./mvnw test -pl core -Dtest=VSChartBindingColorPalettesTest,VSChartPaletteDefaultsTest`

- [ ] **Step 7: Commit**

```bash
git add core/src/main/java/inetsoft/web/binding/VSChartBindingController.java core/src/main/java/inetsoft/web/binding/VSChartBindingService.java core/src/main/java/inetsoft/web/binding/model/graph/aesthetic/CategoricalColorModel.java core/src/test/java/inetsoft/web/binding/VSChartBindingColorPalettesTest.java web/projects/portal/src/app/common/data/visual-frame-model.ts
git commit -m "Flag the palettes a modern chart is not offered"
```

---

## Task 6: Filter the dropdown

**Files:**
- Modify: `web/projects/portal/src/app/binding/editor/chart/palette-dialog.spec.ts`
- Modify: `web/projects/portal/src/app/binding/editor/chart/palette-dialog.component.ts:42-47`
- Modify: `web/projects/portal/src/app/binding/editor/chart/aesthetic/categorical-color-pane.component.ts:110-115`

**Interfaces:**
- Consumes: `CategoricalColorModel.hidden` from Task 5.
- Produces: nothing downstream. This is the last task.

**The invariant being protected.** `getPaletteIndex()` returns 0 when nothing matches (`palette-dialog.component.ts:73-113`) and `saveChanges()` splices whatever is displayed over the chart's colours (`:122-127`). So an unmatched dialog shows "Default" and repaints the chart on a no-op OK. Matching therefore runs over **every** palette and only the dropdown is filtered — a chart sitting on a retired palette still pre-selects it, and can still choose to leave it.

- [ ] **Step 1: Write the failing tests**

Append to `palette-dialog.spec.ts`. The `palette()` helper at `:28-33` needs an optional flag:

```typescript
function palette(name: string, head: string[], hidden = false): CategoricalColorModel {
   const model = new CategoricalColorModel();
   model.name = name;
   model.colors = palette40(head);
   model.hidden = hidden;
   return model;
}
```

then:

```typescript
describe("PaletteDialog hidden palettes", () => {
   function dialogWithHidden(currentColors: string[]): PaletteDialog {
      const dialog = new PaletteDialog();
      dialog.colorPalettes = [
         palette("Default", LEGACY_HEAD),
         palette("Modern", MODERN_HEAD),
         palette("Heat 8", HEAT_HEAD, true)
      ];
      const curr = new CategoricalColorModel();
      curr.colors = currentColors;
      dialog.currPalette = curr;
      return dialog;
   }

   it("drops a hidden palette from the dropdown", () => {
      const dialog = dialogWithHidden(palette40(MODERN_HEAD));
      expect(dialog.paletteSelectOptions.map((o) => o.label)).toEqual(["Default", "Modern"]);
   });

   it("keeps a hidden palette in the dropdown when the chart is using it", () => {
      const dialog = dialogWithHidden(palette40(HEAT_HEAD));
      expect(dialog.displayPalette.name).toBe("Heat 8");
      expect(dialog.paletteSelectOptions.map((o) => o.label))
         .toEqual(["Default", "Modern", "Heat 8"]);
   });

   // The option value indexes into colorPalettes, not into the filtered list, or selecting an
   // option after a hidden one would apply the wrong palette.
   it("keeps option values aligned with the unfiltered array", () => {
      const dialog = dialogWithHidden(palette40(MODERN_HEAD));
      expect(dialog.paletteSelectOptions.map((o) => o.value)).toEqual([0, 1]);
   });

   it("does not repaint a chart sitting on a hidden palette", () => {
      const dialog = dialogWithHidden(palette40(HEAT_HEAD));
      expect(dialog.displayPalette.colors).toEqual(palette40(HEAT_HEAD));
   });
});
```

Declare the fixture used above near the top of the file, after the imports:

```typescript
const HEAT_HEAD: string[] = [
   "#663300", "#914800", "#bd5e00", "#e97400", "#ff8a15", "#ffa041", "#ffb66d", "#ffcc99"
];
```

- [ ] **Step 2: Run them to verify they fail**

```
npx ng test portal --include='**/palette-dialog.spec.ts'
```

Expected: the four new tests fail — `paletteSelectOptions` currently returns all three labels. The four pre-existing pre-selection tests must still pass.

- [ ] **Step 3: Filter the dropdown**

`palette-dialog.component.ts:42-47`:

```typescript
   get paletteSelectOptions(): CustomSelectOption<number>[] {
      const selected = this.displayPalette == null ? -1 : this._selectedIndex;

      return (this.colorPalettes || [])
         .map((palette, index) => ({ palette, index }))
         .filter(({ palette, index }) => !palette.hidden || index === selected)
         .map(({ palette, index }) => ({
            value: index,
            label: palette.name
         }));
   }
```

Reading `this.displayPalette` first is what resolves `_selectedIndex` from `-1` on the first call — the getter assigns it at `:54-56`. The `index` carried through the filter is the position in the unfiltered `colorPalettes`, which is what `_selectedIndex` and `displayPalette` both index by.

- [ ] **Step 4: Run them to verify they pass**

`npx ng test portal --include='**/palette-dialog.spec.ts'` — all eight green.

- [ ] **Step 5: Send the assembly context**

`categorical-color-pane.component.ts:110-115`. The component already holds both inputs (`:63`, `:65`), and `getColorMappingDialog` two methods above shows the idiom:

```typescript
   private getColorPalettes(): Observable<any> {
      const params = new HttpParams()
         .set("orgId", createAssetEntry(this.assetId).organization)
         .set("vsId", this.vsId)
         .set("assemblyName", this.assemblyName);

      return this.modelService.getModel(COLOR_PALETTES_URI, params);
   }
```

`b-categorical-color-pane.component.ts` is not touched: sending nothing is what puts it on the org-gate branch.

- [ ] **Step 6: Run the binding specs**

```
npx ng test portal --include='**/palette-dialog.spec.ts' --include='**/categorical-color-pane*.spec.ts' --include='**/combined-color-pane.spec.ts'
```

- [ ] **Step 7: Commit**

```bash
git add web/projects/portal/src/app/binding/editor/chart/palette-dialog.component.ts web/projects/portal/src/app/binding/editor/chart/palette-dialog.spec.ts web/projects/portal/src/app/binding/editor/chart/aesthetic/categorical-color-pane.component.ts
git commit -m "Hide retired palettes from a modern chart's picker"
```

---

## Final Verification

- [ ] **Core suite:** `./mvnw test -pl core` — green, no new failures or skips against the branch baseline.
- [ ] **Cross-module build:** `./mvnw clean install -DskipTests -Pcommunity,enterprise` — BUILD SUCCESS. Use `clean`; an incremental `install` has reported SUCCESS over a real break on this branch before.
- [ ] **Frontend:** `npx ng test portal` from `community/web/` — green. Do not run the TL suite; nothing in this plan touches a `*.tl.spec.ts`.
- [ ] **A modern chart in the browser.** With `viewsheet.modernVisualization` on, create a dashboard and a chart. Its marks render the new bases — azure first, coral second, the near-black ink at slot 3. Open the colour binding's palette dialog: the dropdown offers Default, Soft, Modern, Modern Dark and Contrast, and none of Pastel, Heat 8/16/24, Blue, Green, Red, Orange or Gray.
- [ ] **A classic chart in the browser.** On an unmarked dashboard, the same dialog offers all fourteen names — the thirteen that shipped plus Contrast.
- [ ] **The no-op OK.** On a modern chart, set the palette to Heat 8 *before* this change is deployed (or set its colours to Heat 8's by hand), then reopen the dialog. It must pre-select **Heat 8**, not Default, and clicking OK must leave the chart's colours untouched. This is the regression the `hidden` flag exists to prevent; if it shows Default, the response is omitting entries instead of flagging them.
- [ ] **The target-band pane.** Open a chart's target line dialog and the Fill Band colour control. It resolves through the org gate, so with the gate on it shows the modern-scoped list and with the gate off the full one — independent of any chart's mark.
- [ ] **An org gate switched off after modernization.** Turn `viewsheet.modernVisualization` off with a marked dashboard already saved. Its chart's binding picker must still show the reduced list, because it reads the mark and not the property. The target-band pane will show the full list; that divergence is intended and documented in the spec.
- [ ] **A cluster hop.** In a two-node install, open a chart whose runtime viewsheet lives on the other node and confirm the palette list is still correctly scoped — this exercises `getChartVizMark` through `affinityCall` and the `SwitchOrgAspectTask` replay.
- [ ] **Export parity.** One modern chart exported to PDF, PNG and Excel, matching the viewer's colours.
