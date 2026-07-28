# Visualization Phase 9B — Dark Mode Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add an org-scoped "Dark Mode" option under "Modern Visualization" that swaps the modern-light visualization palette for the already-specified dark palette across server-rendered surfaces (tables, chart chrome, title/KPI chrome, categorical palette) and the browser-DOM surround — live view and every export format alike.

**Architecture:** Dark mode is a *modifier of modern mode*, not an independent mode. A new org-scoped boolean property `viewsheet.darkMode` gates a new `VSDensityDefaults.isDark()` (true only when the master modern gate is also on). The five existing System-B resolvers (`VSTableStructureDefaults`, `VSChartChromeDefaults`, `VSTitleChromeDefaults`, `VSOutputChromeDefaults`, `VSChartPaletteDefaults`) each grow a parallel dark color set and branch their output on `isDark()`. Because those resolvers are shared by the live model build **and** the export choke points by construction, exports darken automatically with no separate seam. A new `.viz-dark` scope in `_viz-tokens.scss`, toggled by a `viz-dark` body class forwarded from the server, darkens the browser-DOM surround (System A).

**Tech Stack:** Java 21 (JUnit 5 + Spring test harness, `@SreeHome`), Angular 21 / TypeScript (EM + portal), SCSS custom-property tokens.

## Global Constraints

Every task's requirements implicitly include this section. Copied verbatim from the visualization design spec and the Phase 9B planning note.

- **Gate-off byte-identical.** When `viewsheet.modernVisualization` is off, no code path changes: legacy output is unchanged.
- **Light-modern unchanged.** When modern is on but dark is off, every existing light-modern value is unchanged. Dark adds a branch; it never edits a light-modern constant.
- **Dark requires modern.** `isDark()` is `isModern() && viewsheet.darkMode`. Turning on dark with modern off does nothing. Dark only recolors surfaces that are *already* modern (a surface whose per-surface modern sub-toggle is off stays legacy, not dark).
- **Defaults-only.** All resolver color substitution behaves as a DEFAULT: a user picker (USER tier), a `format.css` class (CSS tier), and (for palettes) a user series color still win. Dark changes only the default a bare value falls back to.
- **Org-scoped.** `viewsheet.darkMode` is read and written org-scoped, exactly like `viewsheet.modernVisualization` (trailing `true` on the `VSDensityDefaults` read; `!globalProperty` / `!globalSettings` on the `LookAndFeelService` read/write).
- **Exports darken.** Dark is delivered *through* the shared resolvers, so live view and PDF/PNG/SVG/Excel/PPT/HTML all render dark together. This is the chosen behavior — do not add a live-only export seam.
- **Comment style.** Keep inline comments to a short clause. No ticket/PR numbers, doc names, or "mockup" references in source comments. No comments in Angular `.html` templates.
- **Shell boundary.** The portal page/panel background and global chrome are owned by the *shell* dark initiative, not this phase. Phase 9B darkens the *visualization* surfaces (server-rendered marks/chrome/tables + the viz-owned DOM state overlays). Where a viz surface sits on the shell canvas, it assumes the shell supplies a dark canvas; do not restyle shell chrome here.

## Color source of truth

Canonical dark values are recorded in `community/docs/superpowers/specs/lookfeel/visualization-palette-swatches.html`. Values marked **(derived)** below are now recorded there too (as `--viz-*-dark` / `--inet-viz-*-dark` tokens with swatch cards), grounded in the canonical dark surface set (`--dark-surface-canvas #1C1B1F`, `--dark-surface-default #252428`, `--dark-surface-subtle #2D2B30`, `--dark-border-default #49454F`). Task 9 still cross-checks them against the reference page `https://swaker854.github.io/lookfeel/flat/portal-dark.html`.

| Role | Light-modern (unchanged) | Dark |
|---|---|---|
| Table/chart gridline | `#E8E5DE` | `#3A383D` (swatch `--viz-gridline-dark`) |
| Table header bg / title bg | `#F1EFEA` | `#2D2B30` (swatch `--viz-header-bg-dark`) |
| Table header fg / chrome label | `#6A685F` | `#CAC4D0` (swatch `--viz-header-text-dark` / `--dark-chart-sub`) |
| Chrome title / KPI value fg | `#35342F` | `#E6E0E9` (swatch `--dark-chart-label`) |
| Structural border / separator | `#D9D5CC` | `#49454F` (swatch `--dark-border-default`) |
| Grand-total band bg | `#E9E4DA` | `#35333A` **(derived)** |
| Subtotal band bg | `#EEEAE1` | `#302E34` **(derived)** |
| Slider inactive track | `#E8E5DE` | `#3A383D` **(derived, = gridline dark)** |
| Slider active track | `#C8C2B7` | `#49454F` **(derived, = border dark)** |
| Slider handle / tick | `#6A685F` | `#CAC4D0` **(derived, = label dark)** |
| Categorical head 1..8 | `#00D4E8 #00B87A #F59E0B #F43F5E #8B5CF6 #3B82F6 #0D9488 #64748B` | `#22D3EE #10B981 #FBB724 #FB6181 #A78BFA #60A5FA #2DD4BF #94A3B8` (swatch `--series-dark-1..8`) |

---

## Task 1: Server dark gate — `VSDensityDefaults.isDark()`

Introduce the single canonical dark gate that every resolver branches on. This is the keystone; all System-B tasks depend on it.

**Files:**
- Modify: `community/core/src/main/java/inetsoft/uql/viewsheet/internal/VSDensityDefaults.java`
- Test: `community/core/src/test/java/inetsoft/uql/viewsheet/internal/VSDensityDefaultsTest.java`

**Interfaces:**
- Consumes: `SreeEnv.getBooleanProperty(String, boolean, boolean)` (existing three-arg overload), `VSDensityDefaults.isModern()` (existing).
- Produces: `public static boolean VSDensityDefaults.isDark()` — true iff the master modern gate is on **and** `viewsheet.darkMode` is true (org-scoped). Every System-B resolver in Tasks 2–5 calls this.

- [ ] **Step 1: Write the failing test**

Add the Spring/`@SreeHome` harness to the existing plain test class so it can flip `SreeEnv`, then add the dark-gate cases. Change the class header and add the imports/annotations to match `VSChartPaletteDefaultsTest`:

```java
package inetsoft.uql.viewsheet.internal;

import inetsoft.sree.SreeEnv;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.asset.internal.AssetUtil;
import org.junit.jupiter.api.*;
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
class VSDensityDefaultsTest {
   @AfterEach
   void reset() {
      SreeEnv.setProperty("viewsheet.modernVisualization", null);
      SreeEnv.setProperty("viewsheet.darkMode", null);
   }
```

Then append these test methods (keep all existing `rowHeightForMode`/`normalizeMode` tests exactly as-is):

```java
   @Test
   void isDarkFalseByDefault() {
      assertFalse(VSDensityDefaults.isDark());
   }

   @Test
   void isDarkRequiresModern() {
      // dark alone, without modern, is inert
      SreeEnv.setProperty("viewsheet.darkMode", "true");
      assertFalse(VSDensityDefaults.isDark());
   }

   @Test
   void isDarkOnWhenModernAndDarkBothOn() {
      SreeEnv.setProperty("viewsheet.modernVisualization", "true");
      SreeEnv.setProperty("viewsheet.darkMode", "true");
      assertTrue(VSDensityDefaults.isDark());
   }

   @Test
   void isDarkOffWhenModernOnButDarkOff() {
      SreeEnv.setProperty("viewsheet.modernVisualization", "true");
      assertFalse(VSDensityDefaults.isDark());
   }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd community && ./mvnw -q -pl core test -Dtest=VSDensityDefaultsTest`
Expected: FAIL — `isDark()` does not exist (compile error), or the four new assertions fail.

- [ ] **Step 3: Write the minimal implementation**

In `VSDensityDefaults.java`, add the method directly after `isModern()` (after line 41):

```java
   /**
    * Whether dark mode is on for the current org. Dark is a modifier of modern: it requires the
    * master modern gate and recolors only surfaces that are already modern.
    */
   public static boolean isDark() {
      return isModern() && SreeEnv.getBooleanProperty("viewsheet.darkMode", false, true);
   }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd community && ./mvnw -q -pl core test -Dtest=VSDensityDefaultsTest`
Expected: PASS (all existing + four new).

- [ ] **Step 5: Commit**

```bash
git add community/core/src/main/java/inetsoft/uql/viewsheet/internal/VSDensityDefaults.java \
        community/core/src/test/java/inetsoft/uql/viewsheet/internal/VSDensityDefaultsTest.java
git commit -m "Viz phase 9B: add VSDensityDefaults.isDark() dark gate"
```

---

## Task 2: Table structure dark (`VSTableStructureDefaults`)

Give the six table-structure accessors a dark branch. They are caller-gated by `VSTableStructureDefaults.isModern()`, so branching on `VSDensityDefaults.isDark()` is correct: dark only applies when the table is already modern.

**Files:**
- Modify: `community/core/src/main/java/inetsoft/uql/viewsheet/internal/VSTableStructureDefaults.java`
- Test: `community/core/src/test/java/inetsoft/uql/viewsheet/internal/VSTableStructureDefaultsTest.java`

**Interfaces:**
- Consumes: `VSDensityDefaults.isDark()` (Task 1).
- Produces: unchanged method signatures (`gridlineColor()`, `headerBackground()`, `headerForeground()`, `headerSeparator()`, `totalBackground()`, `subtotalBackground()`) that now return dark values when `isDark()`.

- [ ] **Step 1: Write the failing test**

Add the Spring/`@SreeHome` harness (mirroring Task 1) to `VSTableStructureDefaultsTest`, an `@AfterEach reset()` clearing `viewsheet.modernVisualization` and `viewsheet.darkMode`, and these dark cases (keep the six existing light-value tests unchanged — they pass because dark defaults off):

```java
   @Test
   void gridlineColorDark() {
      SreeEnv.setProperty("viewsheet.modernVisualization", "true");
      SreeEnv.setProperty("viewsheet.darkMode", "true");
      assertEquals(0x3A383D, rgb(VSTableStructureDefaults.gridlineColor()));
   }

   @Test
   void headerBackgroundDark() {
      SreeEnv.setProperty("viewsheet.modernVisualization", "true");
      SreeEnv.setProperty("viewsheet.darkMode", "true");
      assertEquals(0x2D2B30, rgb(VSTableStructureDefaults.headerBackground()));
   }

   @Test
   void headerForegroundDark() {
      SreeEnv.setProperty("viewsheet.modernVisualization", "true");
      SreeEnv.setProperty("viewsheet.darkMode", "true");
      assertEquals(0xCAC4D0, rgb(VSTableStructureDefaults.headerForeground()));
   }

   @Test
   void structuralBandsDark() {
      SreeEnv.setProperty("viewsheet.modernVisualization", "true");
      SreeEnv.setProperty("viewsheet.darkMode", "true");
      assertEquals(0x49454F, rgb(VSTableStructureDefaults.headerSeparator()));
      assertEquals(0x35333A, rgb(VSTableStructureDefaults.totalBackground()));
      assertEquals(0x302E34, rgb(VSTableStructureDefaults.subtotalBackground()));
   }

   @Test
   void darkInertWithoutModern() {
      // dark set but modern off => still light-modern constants are irrelevant; isDark() is false
      SreeEnv.setProperty("viewsheet.darkMode", "true");
      assertEquals(0xE8E5DE, rgb(VSTableStructureDefaults.gridlineColor()));
   }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd community && ./mvnw -q -pl core test -Dtest=VSTableStructureDefaultsTest`
Expected: FAIL — dark assertions get the light constants (e.g. `0xE8E5DE` instead of `0x3A383D`).

- [ ] **Step 3: Write the minimal implementation**

In `VSTableStructureDefaults.java`, change each accessor to branch, and add the dark constant block. Replace the six accessor bodies:

```java
   public static Color gridlineColor() {
      return VSDensityDefaults.isDark() ? GRIDLINE_DARK : GRIDLINE;
   }

   public static Color headerBackground() {
      return VSDensityDefaults.isDark() ? HEADER_BG_DARK : HEADER_BG;
   }

   public static Color headerForeground() {
      return VSDensityDefaults.isDark() ? HEADER_FG_DARK : HEADER_FG;
   }

   public static Color headerSeparator() {
      return VSDensityDefaults.isDark() ? HEADER_SEPARATOR_DARK : HEADER_SEPARATOR;
   }

   public static Color totalBackground() {
      return VSDensityDefaults.isDark() ? TOTAL_BG_DARK : TOTAL_BG;
   }

   public static Color subtotalBackground() {
      return VSDensityDefaults.isDark() ? SUBTOTAL_BG_DARK : SUBTOTAL_BG;
   }
```

Update the light-palette comment (drop "dark deferred") and add the dark block after the existing constants:

```java
   // modern warm-neutral structure palette (light mode)
   private static final Color GRIDLINE = new Color(0xE8E5DE);
   private static final Color HEADER_SEPARATOR = new Color(0xD9D5CC);
   private static final Color HEADER_BG = new Color(0xF1EFEA);
   private static final Color HEADER_FG = new Color(0x6A685F);
   private static final Color TOTAL_BG = new Color(0xE9E4DA);
   private static final Color SUBTOTAL_BG = new Color(0xEEEAE1);

   // dark structure palette; total/subtotal bands lifted above the header for total hierarchy
   private static final Color GRIDLINE_DARK = new Color(0x3A383D);
   private static final Color HEADER_SEPARATOR_DARK = new Color(0x49454F);
   private static final Color HEADER_BG_DARK = new Color(0x2D2B30);
   private static final Color HEADER_FG_DARK = new Color(0xCAC4D0);
   private static final Color TOTAL_BG_DARK = new Color(0x35333A);
   private static final Color SUBTOTAL_BG_DARK = new Color(0x302E34);
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd community && ./mvnw -q -pl core test -Dtest=VSTableStructureDefaultsTest`
Expected: PASS (six light + five dark).

- [ ] **Step 5: Commit**

```bash
git add community/core/src/main/java/inetsoft/uql/viewsheet/internal/VSTableStructureDefaults.java \
        community/core/src/test/java/inetsoft/uql/viewsheet/internal/VSTableStructureDefaultsTest.java
git commit -m "Viz phase 9B: dark table-structure palette"
```

---

## Task 3: Chart chrome dark (`VSChartChromeDefaults`)

Branch the four plain chrome accessors and the three `resolve*(current)` methods. The `resolve*` methods self-gate on `VSChartChromeDefaults.isModern()`, so the dark sub-selection nests inside that check.

**Files:**
- Modify: `community/core/src/main/java/inetsoft/uql/viewsheet/internal/VSChartChromeDefaults.java`
- Test: `community/core/src/test/java/inetsoft/uql/viewsheet/internal/VSChartChromeDefaultsTest.java` (new)

**Interfaces:**
- Consumes: `VSDensityDefaults.isDark()` (Task 1), `GDefaults.DEFAULT_LINE_COLOR`, `GDefaults.DEFAULT_GRIDLINE_COLOR` (existing).
- Produces: unchanged signatures (`gridlineColor()`, `legendBorderColor()`, `labelColor()`, `titleColor()`, `resolveAxisLineColor(Color)`, `resolveGridlineColor(Color)`, `resolveLegendBorderColor(Color)`) returning dark values when `isDark()`.

- [ ] **Step 1: Write the failing test**

Create `VSChartChromeDefaultsTest.java` with the `@SreeHome` harness:

```java
package inetsoft.uql.viewsheet.internal;

import inetsoft.graph.internal.GDefaults;
import inetsoft.sree.SreeEnv;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import org.junit.jupiter.api.*;
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
class VSChartChromeDefaultsTest {
   @AfterEach
   void reset() {
      SreeEnv.setProperty("viewsheet.modernVisualization", null);
      SreeEnv.setProperty("viewsheet.modernChartChrome", null);
      SreeEnv.setProperty("viewsheet.darkMode", null);
   }

   private void darkOn() {
      SreeEnv.setProperty("viewsheet.modernVisualization", "true");
      SreeEnv.setProperty("viewsheet.darkMode", "true");
   }

   @Test
   void lightModernValuesUnchanged() {
      SreeEnv.setProperty("viewsheet.modernVisualization", "true");
      assertEquals(0xE8E5DE, rgb(VSChartChromeDefaults.gridlineColor()));
      assertEquals(0x6A685F, rgb(VSChartChromeDefaults.labelColor()));
      assertEquals(0x35342F, rgb(VSChartChromeDefaults.titleColor()));
   }

   @Test
   void plainAccessorsDark() {
      darkOn();
      assertEquals(0x3A383D, rgb(VSChartChromeDefaults.gridlineColor()));
      assertEquals(0x3A383D, rgb(VSChartChromeDefaults.legendBorderColor()));
      assertEquals(0xCAC4D0, rgb(VSChartChromeDefaults.labelColor()));
      assertEquals(0xE6E0E9, rgb(VSChartChromeDefaults.titleColor()));
   }

   @Test
   void resolveAxisLineDarkOnlyWhenStillLegacyDefault() {
      darkOn();
      // a bare legacy default becomes the dark gridline
      assertEquals(0x3A383D, rgb(VSChartChromeDefaults.resolveAxisLineColor(GDefaults.DEFAULT_LINE_COLOR)));
      // a user/format.css color is preserved
      Color custom = new Color(0x123456);
      assertEquals(0x123456, rgb(VSChartChromeDefaults.resolveAxisLineColor(custom)));
   }

   @Test
   void resolveGridlineDark() {
      darkOn();
      assertEquals(0x3A383D, rgb(VSChartChromeDefaults.resolveGridlineColor(GDefaults.DEFAULT_GRIDLINE_COLOR)));
   }

   private static int rgb(Color c) {
      return c.getRGB() & 0xFFFFFF;
   }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd community && ./mvnw -q -pl core test -Dtest=VSChartChromeDefaultsTest`
Expected: FAIL — dark assertions return light constants.

- [ ] **Step 3: Write the minimal implementation**

In `VSChartChromeDefaults.java`, branch the four plain accessors:

```java
   public static Color gridlineColor() {
      return VSDensityDefaults.isDark() ? GRIDLINE_DARK : GRIDLINE;
   }

   public static Color legendBorderColor() {
      return VSDensityDefaults.isDark() ? GRIDLINE_DARK : GRIDLINE;
   }

   public static Color labelColor() {
      return VSDensityDefaults.isDark() ? LABEL_DARK : LABEL;
   }

   public static Color titleColor() {
      return VSDensityDefaults.isDark() ? TITLE_DARK : TITLE;
   }
```

Branch the three `resolve*` methods so the modern substitution picks the dark neutral when dark is on:

```java
   public static Color resolveAxisLineColor(Color current) {
      return isModern() && GDefaults.DEFAULT_LINE_COLOR.equals(current)
         ? (VSDensityDefaults.isDark() ? GRIDLINE_DARK : GRIDLINE) : current;
   }

   public static Color resolveGridlineColor(Color current) {
      return isModern() && GDefaults.DEFAULT_GRIDLINE_COLOR.equals(current)
         ? (VSDensityDefaults.isDark() ? GRIDLINE_DARK : GRIDLINE) : current;
   }

   public static Color resolveLegendBorderColor(Color current) {
      return isModern() && GDefaults.DEFAULT_LINE_COLOR.equals(current)
         ? (VSDensityDefaults.isDark() ? GRIDLINE_DARK : GRIDLINE) : current;
   }
```

Update the light comment (drop "dark deferred") and add dark constants after the existing three:

```java
   // dark chrome; gridline matches the table gridline, label/title lift for on-dark legibility
   private static final Color GRIDLINE_DARK = new Color(0x3A383D);
   private static final Color LABEL_DARK = new Color(0xCAC4D0);
   private static final Color TITLE_DARK = new Color(0xE6E0E9);
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd community && ./mvnw -q -pl core test -Dtest=VSChartChromeDefaultsTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add community/core/src/main/java/inetsoft/uql/viewsheet/internal/VSChartChromeDefaults.java \
        community/core/src/test/java/inetsoft/uql/viewsheet/internal/VSChartChromeDefaultsTest.java
git commit -m "Viz phase 9B: dark chart-chrome palette"
```

---

## Task 4: Title + KPI/output chrome dark (`VSTitleChromeDefaults`, `VSOutputChromeDefaults`)

These two share the `viewsheet.modernObjectChrome` sub-toggle and move together. Both write modern neutrals to the DEFAULT tier of a format clone via `applyTo(...)`; the dark branch changes the values written there. `VSOutputChromeDefaults` also has the slider ternaries.

**Files:**
- Modify: `community/core/src/main/java/inetsoft/uql/viewsheet/internal/VSTitleChromeDefaults.java`
- Modify: `community/core/src/main/java/inetsoft/uql/viewsheet/internal/VSOutputChromeDefaults.java`
- Test: `community/core/src/test/java/inetsoft/uql/viewsheet/internal/VSOutputChromeDefaultsTest.java` (modify — slider accessors are pure and easy to pin)
- Test: `community/core/src/test/java/inetsoft/uql/viewsheet/internal/VSTitleChromeDefaultsTest.java` (modify)

**Interfaces:**
- Consumes: `VSDensityDefaults.isDark()` (Task 1).
- Produces:
  - `VSOutputChromeDefaults.sliderInactiveTrack()` / `sliderActiveTrack()` / `sliderHandle()` / `sliderTick()` return dark values when `isDark()`; `valueForeground()` / `valueBorderColor()` return dark values when `isDark()`.
  - `VSTitleChromeDefaults.titleBackground()` / `titleForeground()` / `titleBorderColor()` return dark values when `isDark()`; `applyModernDefaults` / `applyModernDefaultsInPlace` write dark values when `isDark()`.

- [ ] **Step 1: Write the failing test (slider — the simplest pure surface first)**

In `VSOutputChromeDefaultsTest.java` add the `@SreeHome` harness if not already present (mirror Task 1's imports/annotations and an `@AfterEach` clearing `viewsheet.modernVisualization`, `viewsheet.modernObjectChrome`, `viewsheet.darkMode`), then add:

```java
   @Test
   void sliderChromeDark() {
      SreeEnv.setProperty("viewsheet.modernVisualization", "true");
      SreeEnv.setProperty("viewsheet.darkMode", "true");
      assertEquals(0x3A383D, rgb(VSOutputChromeDefaults.sliderInactiveTrack()));
      assertEquals(0x49454F, rgb(VSOutputChromeDefaults.sliderActiveTrack()));
      assertEquals(0xCAC4D0, rgb(VSOutputChromeDefaults.sliderHandle()));
      assertEquals(0xCAC4D0, rgb(VSOutputChromeDefaults.sliderTick()));
   }

   @Test
   void valueChromeDark() {
      SreeEnv.setProperty("viewsheet.modernVisualization", "true");
      SreeEnv.setProperty("viewsheet.darkMode", "true");
      assertEquals(0xE6E0E9, rgb(VSOutputChromeDefaults.valueForeground()));
      assertEquals(0x49454F, rgb(VSOutputChromeDefaults.valueBorderColor()));
   }
```

Add a matching `rgb` helper if the class does not already have one:

```java
   private static int rgb(java.awt.Color c) {
      return c.getRGB() & 0xFFFFFF;
   }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd community && ./mvnw -q -pl core test -Dtest=VSOutputChromeDefaultsTest`
Expected: FAIL — dark assertions get modern-light constants.

- [ ] **Step 3: Implement `VSOutputChromeDefaults` dark**

Branch the four slider accessors from two-way to three-way:

```java
   public static Color sliderInactiveTrack() {
      return VSDensityDefaults.isDark() ? SLIDER_INACTIVE_DARK
         : isModern() ? SLIDER_INACTIVE_MODERN : SLIDER_INACTIVE_LEGACY;
   }

   public static Color sliderActiveTrack() {
      return VSDensityDefaults.isDark() ? SLIDER_ACTIVE_DARK
         : isModern() ? SLIDER_ACTIVE_MODERN : SLIDER_ACTIVE_LEGACY;
   }

   public static Color sliderHandle() {
      return VSDensityDefaults.isDark() ? SLIDER_HANDLE_DARK
         : isModern() ? SLIDER_HANDLE_MODERN : SLIDER_HANDLE_LEGACY;
   }

   public static Color sliderTick() {
      return VSDensityDefaults.isDark() ? SLIDER_TICK_DARK
         : isModern() ? SLIDER_TICK_MODERN : SLIDER_TICK_LEGACY;
   }
```

Branch the two value accessors:

```java
   public static Color valueForeground() {
      return VSDensityDefaults.isDark() ? VALUE_FG_DARK : VALUE_FG;
   }

   public static Color valueBorderColor() {
      return VSDensityDefaults.isDark() ? VALUE_BORDER_DARK : VALUE_BORDER;
   }
```

Branch the `applyTo` writer so the DEFAULT-tier substitution uses dark values:

```java
   private static void applyTo(VSFormat def, boolean fg, boolean border) {
      boolean dark = VSDensityDefaults.isDark();
      Color fgColor = dark ? VALUE_FG_DARK : VALUE_FG;
      Color borderColor = dark ? VALUE_BORDER_DARK : VALUE_BORDER;

      if(fg) {
         def.setForegroundValue(toValue(fgColor));
      }

      if(border) {
         def.setBorderColorsValue(new BorderColors(borderColor, borderColor, borderColor, borderColor));
      }
   }
```

Update the modern comment (drop "dark deferred") and add dark constants after the existing modern block:

```java
   // dark KPI/slider chrome; neutrals track the shared dark structure palette
   private static final Color SLIDER_INACTIVE_DARK = new Color(0x3A383D);
   private static final Color SLIDER_ACTIVE_DARK = new Color(0x49454F);
   private static final Color SLIDER_HANDLE_DARK = new Color(0xCAC4D0);
   private static final Color SLIDER_TICK_DARK = new Color(0xCAC4D0);
   private static final Color VALUE_FG_DARK = new Color(0xE6E0E9);
   private static final Color VALUE_BORDER_DARK = new Color(0x49454F);
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd community && ./mvnw -q -pl core test -Dtest=VSOutputChromeDefaultsTest`
Expected: PASS.

- [ ] **Step 5: Write the failing test for `VSTitleChromeDefaults`**

In `VSTitleChromeDefaultsTest.java`, add the `@SreeHome` harness (clearing `viewsheet.modernVisualization`, `viewsheet.modernObjectChrome`, `viewsheet.darkMode`) and:

```java
   @Test
   void titleAccessorsDark() {
      SreeEnv.setProperty("viewsheet.modernVisualization", "true");
      SreeEnv.setProperty("viewsheet.darkMode", "true");
      assertEquals(0x2D2B30, rgb(VSTitleChromeDefaults.titleBackground()));
      assertEquals(0xCAC4D0, rgb(VSTitleChromeDefaults.titleForeground()));
      assertEquals(0x49454F, rgb(VSTitleChromeDefaults.titleBorderColor()));
   }
```

Add the `rgb` helper if absent (as in Step 1).

- [ ] **Step 6: Run test to verify it fails**

Run: `cd community && ./mvnw -q -pl core test -Dtest=VSTitleChromeDefaultsTest`
Expected: FAIL.

- [ ] **Step 7: Implement `VSTitleChromeDefaults` dark**

Branch the three accessors:

```java
   public static Color titleBackground() {
      return VSDensityDefaults.isDark() ? TITLE_BG_DARK : TITLE_BG;
   }

   public static Color titleForeground() {
      return VSDensityDefaults.isDark() ? TITLE_FG_DARK : TITLE_FG;
   }

   public static Color titleBorderColor() {
      return VSDensityDefaults.isDark() ? TITLE_BORDER_DARK : TITLE_BORDER;
   }
```

Branch the `applyTo` writer:

```java
   private static void applyTo(VSFormat def, boolean bg, boolean fg) {
      boolean dark = VSDensityDefaults.isDark();

      if(bg) {
         def.setBackgroundValue(toValue(dark ? TITLE_BG_DARK : TITLE_BG));
      }

      if(fg) {
         def.setForegroundValue(toValue(dark ? TITLE_FG_DARK : TITLE_FG));
      }
   }
```

Update the light comment (drop "dark deferred") and add dark constants:

```java
   // dark title chrome; coordinated with the table header and chart chrome dark palette
   private static final Color TITLE_BG_DARK = new Color(0x2D2B30);
   private static final Color TITLE_FG_DARK = new Color(0xCAC4D0);
   private static final Color TITLE_BORDER_DARK = new Color(0x49454F);
```

Note: `titleBorderColor()` is currently unused by `applyTo` (border is not written there); keep the accessor consistent with the dark palette regardless, since callers read it directly.

- [ ] **Step 8: Run both tests to verify they pass**

Run: `cd community && ./mvnw -q -pl core test -Dtest=VSTitleChromeDefaultsTest,VSOutputChromeDefaultsTest`
Expected: PASS.

- [ ] **Step 9: Commit**

```bash
git add community/core/src/main/java/inetsoft/uql/viewsheet/internal/VSTitleChromeDefaults.java \
        community/core/src/main/java/inetsoft/uql/viewsheet/internal/VSOutputChromeDefaults.java \
        community/core/src/test/java/inetsoft/uql/viewsheet/internal/VSTitleChromeDefaultsTest.java \
        community/core/src/test/java/inetsoft/uql/viewsheet/internal/VSOutputChromeDefaultsTest.java
git commit -m "Viz phase 9B: dark title + KPI/output chrome"
```

---

## Task 5: Categorical palette dark (`VSChartPaletteDefaults`)

Add a dark head (`--series-dark-1..8`) and select it in `applyModernPalette` when `isDark()`. The tail reuses the legacy `COLOR_PALETTE` indices 8..39, matching the light-modern palette's tail strategy.

**Files:**
- Modify: `community/core/src/main/java/inetsoft/uql/viewsheet/internal/VSChartPaletteDefaults.java`
- Test: `community/core/src/test/java/inetsoft/uql/viewsheet/internal/VSChartPaletteDefaultsTest.java`

**Interfaces:**
- Consumes: `VSDensityDefaults.isDark()` (Task 1), `CategoricalColorFrame.COLOR_PALETTE`, `CategoricalColorFrame.setDefaultColors(Color[])` (existing).
- Produces: `public static Color[] VSChartPaletteDefaults.darkPalette()` (8 dark head + legacy tail); `applyModernPalette(frame)` selects dark vs light head by `isDark()`.

- [ ] **Step 1: Write the failing test**

In `VSChartPaletteDefaultsTest.java`, extend `@AfterEach reset()` to also clear `viewsheet.darkMode`, then add:

```java
   @Test
   void darkPaletteSwapsToDarkHeadKeepsLegacyTail() {
      SreeEnv.setProperty("viewsheet.modernVisualization", "true");
      SreeEnv.setProperty("viewsheet.darkMode", "true");

      Color[] dark = VSChartPaletteDefaults.darkPalette();
      assertEquals(40, dark.length, "8 dark + 32 legacy tail = 40");
      assertEquals(new Color(0x22D3EE), dark[0]);
      assertEquals(new Color(0x94A3B8), dark[7]);
      assertEquals(CategoricalColorFrame.COLOR_PALETTE[8], dark[8]);
      assertEquals(CategoricalColorFrame.COLOR_PALETTE[39], dark[39]);

      CategoricalColorFrame frame = new CategoricalColorFrame();
      VSChartPaletteDefaults.applyModernPalette(frame);
      assertEquals(new Color(0x22D3EE), frame.getColor(0));
      assertEquals(new Color(0x10B981), frame.getColor(1));
   }

   @Test
   void darkInertWithoutModern() {
      // dark set but modern off => palette untouched (legacy head)
      SreeEnv.setProperty("viewsheet.darkMode", "true");
      CategoricalColorFrame frame = new CategoricalColorFrame();
      VSChartPaletteDefaults.applyModernPalette(frame);
      assertEquals(CategoricalColorFrame.COLOR_PALETTE[0], frame.getColor(0));
   }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd community && ./mvnw -q -pl core test -Dtest=VSChartPaletteDefaultsTest`
Expected: FAIL — `darkPalette()` does not exist (compile error).

- [ ] **Step 3: Write the minimal implementation**

In `VSChartPaletteDefaults.java`, add `darkPalette()` next to `modernPalette()`:

```java
   public static Color[] darkPalette() {
      // 8 dark head + legacy tail (indices 8..39 unchanged), mirroring modernPalette()
      List<Color> palette = new ArrayList<>(Arrays.asList(DARK_HEAD));
      Color[] legacy = CategoricalColorFrame.COLOR_PALETTE;
      palette.addAll(Arrays.asList(legacy).subList(DARK_HEAD.length, legacy.length));
      return palette.toArray(new Color[0]);
   }
```

Branch `applyModernPalette`:

```java
   public static void applyModernPalette(CategoricalColorFrame frame) {
      if(frame != null && isModern()) {
         frame.setDefaultColors(VSDensityDefaults.isDark() ? darkPalette() : modernPalette());
      }
   }
```

Add the dark head after `MODERN_HEAD`:

```java
   private static final Color[] DARK_HEAD = {
      new Color(0x22D3EE), new Color(0x10B981), new Color(0xFBB724), new Color(0xFB6181),
      new Color(0xA78BFA), new Color(0x60A5FA), new Color(0x2DD4BF), new Color(0x94A3B8)
   };
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd community && ./mvnw -q -pl core test -Dtest=VSChartPaletteDefaultsTest`
Expected: PASS (existing + two new).

- [ ] **Step 5: Commit**

```bash
git add community/core/src/main/java/inetsoft/uql/viewsheet/internal/VSChartPaletteDefaults.java \
        community/core/src/test/java/inetsoft/uql/viewsheet/internal/VSChartPaletteDefaultsTest.java
git commit -m "Viz phase 9B: dark categorical palette"
```

---

## Task 6: EM persistence + Dark Mode checkbox

Persist `viewsheet.darkMode` through the Look & Feel settings, and surface a "Dark Mode" checkbox that appears only when "Modern Visualization" is on (dependent control, exactly like the density dropdown).

**Files:**
- Modify: `community/core/src/main/java/inetsoft/web/admin/presentation/model/LookAndFeelSettingsModel.java`
- Modify: `community/core/src/main/java/inetsoft/web/admin/presentation/LookAndFeelService.java`
- Modify: `community/web/projects/em/src/app/settings/presentation/look-and-feel-settings-view/look-and-feel-settings-model.ts`
- Modify: `community/web/projects/em/src/app/settings/presentation/look-and-feel-settings-view/look-and-feel-settings-view.component.ts`
- Modify: `community/web/projects/em/src/app/settings/presentation/look-and-feel-settings-view/look-and-feel-settings-view.component.html`

**Interfaces:**
- Consumes: existing `SreeEnv` get/set org-scoped pattern.
- Produces: `LookAndFeelSettingsModel.darkMode()` (Java, `@Value.Default default false`) and `LookAndFeelSettingsModel.darkMode: boolean` (TS); the property `viewsheet.darkMode` is read/written in `LookAndFeelService`.

- [ ] **Step 1: Add the Java model field**

In `LookAndFeelSettingsModel.java`, after line 54 (`boolean modernVisualization();`) add:

```java
   @Value.Default default boolean darkMode() { return false; }
```

- [ ] **Step 2: Wire read + write in the service**

In `LookAndFeelService.java` `getModel(...)`, after line 61 (the `visualizationDensity` read) add:

```java
      boolean darkMode = SreeEnv.getBooleanProperty("viewsheet.darkMode", false, !globalProperty);
```

and in the builder chain, after `.modernVisualization(modernVisualization)` (line 144) add:

```java
         .darkMode(darkMode)
```

In `setModel(...)`, after the `viewsheet.density` write (lines 173-174) add:

```java
      SreeEnv.setProperty("viewsheet.darkMode",
                          Boolean.toString(model.darkMode()), !globalSettings);
```

- [ ] **Step 3: Add the TS model field**

In `look-and-feel-settings-model.ts`, after line 43 (`modernVisualization: boolean;`) add:

```typescript
   darkMode: boolean;
```

- [ ] **Step 4: Wire the form control**

In `look-and-feel-settings-view.component.ts`:

In the `fb.group({...})` declaration (after line 136 `modernVisualization: [false],`) add:

```typescript
            darkMode: [false],
```

In the `set model(...)` populate branch, after line 85 add:

```typescript
         this.form.get("darkMode").setValue(!!model.darkMode, {emitEvent: false});
```

In the `else` (no-model) branch, after line 108 add:

```typescript
         this.form.get("darkMode").setValue(false, {emitEvent: false});
```

In `emitModel()`, after line 224 add:

```typescript
      this.model.darkMode = this.form.get("darkMode").value;
```

- [ ] **Step 5: Add the dependent checkbox to the template**

In `look-and-feel-settings-view.component.html`, inside the `@if (form.controls.modernVisualization?.value) { ... }` block (currently the density `mat-form-field`, lines 39-48), add the checkbox after the density field, still inside the `@if`:

```html
          <mat-checkbox formControlName="darkMode">_#(Dark Mode)</mat-checkbox>
```

The result is that "Dark Mode" renders only when "Modern Visualization" is checked, alongside the density selector.

- [ ] **Step 6: Build the frontend + backend to verify compilation**

Run: `cd community && ./mvnw -q -pl core install -DskipTests` and `cd community/web && npx tsc -p projects/em/tsconfig.app.json --noEmit`
Expected: both succeed (the Immutables `ImmutableLookAndFeelSettingsModel` regenerates with `darkMode`; TS compiles).

- [ ] **Step 7: Commit**

```bash
git add community/core/src/main/java/inetsoft/web/admin/presentation/model/LookAndFeelSettingsModel.java \
        community/core/src/main/java/inetsoft/web/admin/presentation/LookAndFeelService.java \
        community/web/projects/em/src/app/settings/presentation/look-and-feel-settings-view/look-and-feel-settings-model.ts \
        community/web/projects/em/src/app/settings/presentation/look-and-feel-settings-view/look-and-feel-settings-view.component.ts \
        community/web/projects/em/src/app/settings/presentation/look-and-feel-settings-view/look-and-feel-settings-view.component.html
git commit -m "Viz phase 9B: EM Dark Mode setting persistence + checkbox"
```

---

## Task 7: Forward `darkMode` to the client + toggle the `viz-dark` body class

The System-B resolvers already read the org property directly, so exports and server renders are covered by Tasks 1–5. This task covers the System-A DOM: forward the flag to the browser and toggle a `viz-dark` body class in portal, composer, and the standalone viewer.

**Files:**
- Modify: `community/core/src/main/java/inetsoft/web/portal/model/PortalModel.java`
- Modify: `community/core/src/main/java/inetsoft/web/portal/controller/PortalController.java`
- Modify: `community/core/src/main/java/inetsoft/web/viewsheet/service/CoreLifecycleService.java`
- Modify: `community/web/projects/portal/src/app/portal/portal-model.ts`
- Modify: `community/web/projects/portal/src/app/portal/app.component.ts`
- Modify: `community/web/projects/portal/src/app/composer/app.component.ts`
- Modify: `community/web/projects/portal/src/app/vsobjects/viewer-app.component.ts`

**Interfaces:**
- Consumes: existing `PortalModel` builder, `command.info` map, the `updateVisualizationMode` methods.
- Produces: `PortalModel.darkMode()` (Java default false), `PortalModel.darkMode: boolean` (TS), `command.info["darkMode"]`; the `viz-dark` body class is present iff `modern && darkMode`.

- [ ] **Step 1: Add the Java `PortalModel` field**

In `PortalModel.java`, after the `modernVisualization()` default (lines 55-58) add:

```java
   @Value.Default
   public boolean darkMode() {
      return false;
   }
```

- [ ] **Step 2: Populate it in `PortalController`**

In `PortalController.java`, after line 117 (`String vizDensity = ...`) add:

```java
      boolean darkMode = SreeEnv.getBooleanProperty("viewsheet.darkMode", false, true);
```

and in the builder chain, after `.modernVisualization(modernVisualization)` (line 151) add:

```java
         .darkMode(darkMode)
```

- [ ] **Step 3: Populate `command.info` in `CoreLifecycleService`**

In `CoreLifecycleService.java`, after the `vizDensity` put (lines 307-309) add:

```java
         infoMap.put("darkMode",
                     SreeEnv.getBooleanProperty("viewsheet.darkMode", false, true));
```

- [ ] **Step 4: Add the TS `PortalModel` field**

In `portal-model.ts`, after line 38 (`modernVisualization: boolean;`) add:

```typescript
   darkMode: boolean;
```

- [ ] **Step 5: Toggle `viz-dark` in the portal shell**

In `portal/app.component.ts`, after the `VIZ_DENSITY_CLASSES` field (line 90) add a class constant:

```typescript
   private readonly VIZ_DARK_CLASS: string = "viz-dark";
```

In `updateVisualizationMode()` (lines 260-273), after the density block and before the closing brace add:

```typescript
      body.classList.toggle(this.VIZ_DARK_CLASS, modern && !!this.model.darkMode);
```

- [ ] **Step 6: Toggle `viz-dark` in the composer**

In `composer/app.component.ts`, after `VIZ_DENSITY_CLASSES` (line 45) add:

```typescript
   private readonly VIZ_DARK_CLASS: string = "viz-dark";
```

In `updateVisualizationMode(model)` (lines 133-146), after the density block and before the closing brace add:

```typescript
      body.classList.toggle(this.VIZ_DARK_CLASS, modern && !!model.darkMode);
```

- [ ] **Step 7: Toggle `viz-dark` in the standalone viewer**

In `viewer-app.component.ts`, add a `darkMode` field next to `vizDensity` (after line 430):

```typescript
   darkMode: boolean = false;
```

Read it from `command.info` next to `vizDensity` (after line 2751):

```typescript
      this.darkMode = !!command.info["darkMode"];
```

In the `if(!this.inPortal) { ... }` body-class block (lines 2782-2792), after the density block and before the closing brace add:

```typescript
         body.classList.toggle("viz-dark", modern && this.darkMode);
```

- [ ] **Step 8: Build to verify compilation**

Run: `cd community && ./mvnw -q -pl core install -DskipTests` and `cd community/web && npx tsc -p projects/portal/tsconfig.app.json --noEmit`
Expected: both succeed (`ImmutablePortalModel` regenerates with `darkMode`).

- [ ] **Step 9: Commit**

```bash
git add community/core/src/main/java/inetsoft/web/portal/model/PortalModel.java \
        community/core/src/main/java/inetsoft/web/portal/controller/PortalController.java \
        community/core/src/main/java/inetsoft/web/viewsheet/service/CoreLifecycleService.java \
        community/web/projects/portal/src/app/portal/portal-model.ts \
        community/web/projects/portal/src/app/portal/app.component.ts \
        community/web/projects/portal/src/app/composer/app.component.ts \
        community/web/projects/portal/src/app/vsobjects/viewer-app.component.ts
git commit -m "Viz phase 9B: forward darkMode + toggle viz-dark body class"
```

---

## Task 8: System-A DOM dark tokens (`.viz-dark` scope)

Add a `.viz-dark` scope to `_viz-tokens.scss` that overrides the browser-DOM state tokens with dark values. The body carries `viz-modern viz-density-<mode> viz-dark` together; `.viz-dark` must appear *after* the `.viz-modern` blocks so its single-class selector wins on source order. Follow the two-tier `-modern` seam: customer-overridable colors live on `:root` as `-dark` tokens and are consumed inside `.viz-dark`.

State dark values below are **(derived)** — grounded in the dark surface set and the viz-owned teal selection family — and are recorded in the swatches HTML as `--inet-viz-*-dark` tokens; Task 9 cross-checks them against the reference page. `--inet-viz-dimmed-opacity` keeps its `:root` value.

**Files:**
- Modify: `community/web/projects/portal/src/scss/_viz-tokens.scss`

**Interfaces:**
- Consumes: existing `--inet-viz-*` token names, the `.viz-modern` scope precedent.
- Produces: a `.viz-dark` scope and `--inet-viz-*-dark` customer-overridable `:root` tokens.

- [ ] **Step 1: Add the customer-overridable `-dark` tokens on `:root`**

In `_viz-tokens.scss`, inside the `:root { ... }` block, after the `-modern` customer-overridable group (lines 49-53) add:

```scss
  // customer-overridable dark state colors, mirroring the -modern seam; consumed only inside
  // .viz-dark below, so gate-off / light-modern are inert.
  --inet-viz-hover-bg-dark: #26313A;
  --inet-viz-selected-bg-dark: #0E3B44;
  --inet-viz-selected-text-dark: #A8EEF5;
  --inet-viz-selected-border-dark: #2DD4BF;
  --inet-viz-sorted-color-dark: #E0A458;
```

- [ ] **Step 2: Add the `.viz-dark` scope at the end of the file**

Append after the density matrices (after line 128):

```scss
// Dark scope (DOM surfaces only). The body carries viz-modern + viz-density-<mode> + viz-dark
// together; this block follows the .viz-modern blocks so its single-class selector wins on source
// order. Density stays driven by the .viz-modern.viz-density-* matrices above; only color changes here.
.viz-dark {
  --inet-viz-hover-bg: var(--inet-viz-hover-bg-dark);
  --inet-viz-selected-bg: var(--inet-viz-selected-bg-dark);
  --inet-viz-selected-text: var(--inet-viz-selected-text-dark);
  --inet-viz-selected-border: var(--inet-viz-selected-border-dark);
  --inet-viz-active-border: var(--inet-primary-color);
  --inet-viz-context-bg: #2E2A22;
  --inet-viz-inline-edit-bg: #252428;
  --inet-viz-filtered-bg: #3A3020;
  --inet-viz-sorted-color: var(--inet-viz-sorted-color-dark);
  --inet-viz-pinned-divider: #49454F;
  --inet-viz-warning-bg: #3A2E1A;
  --inet-viz-anomaly-bg: #3A1F1F;
}
```

- [ ] **Step 3: Build the portal styles to verify the SCSS compiles**

Run: `cd community/web && npm run build 2>&1 | tail -20`
Expected: build completes with no SCSS error referencing `_viz-tokens.scss`. (If a full `npm run build` is too slow for iteration, a targeted `npx sass projects/portal/src/scss/_viz-tokens.scss` smoke-check is acceptable to confirm the file parses.)

- [ ] **Step 4: Commit**

```bash
git add community/web/projects/portal/src/scss/_viz-tokens.scss
git commit -m "Viz phase 9B: .viz-dark DOM state tokens"
```

---

## Task 9: Validation pass (fold into Phase 10 audit)

Dark mode has no separate acceptance suite; it folds a dark column into the Phase 10 screen-by-screen audit. This task is a manual verification checklist, not a code change — but it gates the phase as "done."

**Files:**
- None (verification). Optionally record findings in `community/docs/superpowers/specs/lookfeel/visualization-phase9b-dark-mode-implementation-plan.md` under a new "9B validation" section.

- [ ] **Step 1: Build and run locally with a dark org**

Run: `cd community && ./mvnw -q clean install -DskipTests -PdockerImage` then start `docker/target/docker-test` (`docker compose up -d`). In EM → Presentation → Look & Feel, enable **Modern Visualization**, pick a density, enable **Dark Mode**, save.

- [ ] **Step 2: Verify the three gate states on every surface**

For each surface — data tables, crosstabs, chart marks, chart axis/gridline/legend chrome, object title bars, KPI/output text, sliders, the categorical palette, and the DOM state overlays (row hover, selection, sorted/filtered indicators) — confirm:
  - **Gate off** (modern off): byte-identical to legacy.
  - **Light modern** (modern on, dark off): the existing light-modern look, unchanged.
  - **Dark modern** (modern on, dark on): the dark palette from the source-of-truth table.

- [ ] **Step 3: Verify exports darken and match live**

Export a dark viewsheet to PDF, PNG, and Excel. Confirm the exported tables/charts/title bars carry the same dark palette as live view (the shared-resolver guarantee). Confirm a user-picked color and a `format.css` class still win in both live and export (defaults-only rule).

- [ ] **Step 4: Cross-check derived values against the reference**

Compare the **(derived)** values (total/subtotal bands, slider tracks, and the `.viz-dark` state colors) against `https://swaker854.github.io/lookfeel/flat/portal-dark.html`. Adjust any that read poorly and re-run the affected resolver test with the new pinned value.

- [ ] **Step 5: Record results and close the phase**

Note pass/fail per surface in the phase 9B planning doc and mark Phase 9B complete.

---

## Self-Review

**Spec coverage** (against the phase 9B planning doc's Tasks 0–5):
- Task 0 (selection mechanism + export scope) → resolved in Global Constraints (org-scoped `viewsheet.darkMode`, dark-requires-modern, exports darken via shared resolvers) and implemented across Tasks 1, 6, 7.
- Task 1 (System-A DOM dark tokens) → Task 8.
- Task 2 (table structure dark) → Task 2.
- Task 3 (chart + title + KPI/output chrome dark) → Tasks 3 and 4.
- Task 4 (categorical palette dark) → Task 5.
- Task 5 (validation) → Task 9.

**Placeholder scan:** every code step carries the exact edit; derived color values are concrete hex, flagged as derived and gated on a Task 9 cross-check — no "TBD"/"handle edge cases".

**Type/name consistency:** the gate `VSDensityDefaults.isDark()` is defined in Task 1 and consumed by the same name in Tasks 2–5; `viewsheet.darkMode` is the single property key across Tasks 1, 6, 7; `darkMode` is the field name in both Java models (`LookAndFeelSettingsModel`, `PortalModel`) and both TS models; `viz-dark` is the single body-class string across Tasks 7 and 8; the dark constant naming (`*_DARK`) is uniform across the five resolvers.

## Related

- `community/docs/superpowers/specs/lookfeel/visualization-phase9b-dark-mode-implementation-plan.md` — the phase 9B planning note this plan operationalizes
- `community/docs/superpowers/specs/lookfeel/visualization-palette-swatches.html` — dark token source of truth
- `community/docs/superpowers/specs/lookfeel/visualization-design-spec.md` — Rendering And Theming Architecture (System A / System B split)
- `community/docs/superpowers/specs/lookfeel/visualization-implementation-roadmap.md` — Phase 9B section
