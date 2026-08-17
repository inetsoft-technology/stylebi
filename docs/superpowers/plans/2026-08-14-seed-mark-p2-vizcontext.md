# Seed Mark P2 — `VizContext` Threaded, Sub-Gates Deleted — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace every direct `SreeEnv` gate read inside the `VS*Defaults` family with an explicit `VizContext` parameter, and delete the four sub-gate properties, without changing what any dashboard renders.

**Architecture:** A small immutable `VizContext` (`modern`, `dark`, `density`) is built by factories that read `SreeEnv` in exactly one place. Every `VS*Defaults` value method takes it as its first parameter, and every call site passes `VizContext.ofGate()` — which reads precisely what the statics read today, so the phase is behaviour-neutral by construction. The six per-class `isModern()` predicates, which differ only in which sub-gate property they read, are deleted along with those properties; call sites read `ctx.modern` instead.

**Tech Stack:** Java 21, JUnit 5 (`@Tag("core")`, Spring `@ContextConfiguration` + `@SreeHome`), Maven via `./mvnw`.

**Spec:** `docs/superpowers/specs/2026-08-14-seed-mark-forward-half-design.md` — read §2 in full before starting, especially "The four sub-gate properties — deleted in P2" and "The surface, sized for slicing".

## Global Constraints

- **P2 changes no rendering. None.** Every call site passes `ofGate()`, and `ofGate()` reads the same properties the statics read today. If any test that passed before this phase fails on behaviour rather than on a signature, stop and report — that is a defect, not an expected consequence.
- **Nothing reads the mark in P2 either.** `VizContext.of(VSAssemblyInfo)` and `of(VizMark)` are *written* in Task 1 and *called by nothing* until P4. Do not wire them into a call site. After this phase, `grep -rn "VizContext.of(" --include=*.java core/src/main` must return no hits outside `VizContext` itself and its test.
- **`VSObjectChromeDefaults.resolveSeededCorner(int)` is NOT widened.** It is called from `VSCompositeFormat.resolveDefaultTierCorner`, a method on a *format* with no route to an assembly or a context. The design is explicit that `VSCompositeFormat` needs no changes in this scope. Leave `resolveSeededCorner` reading the gate directly, and leave its `isModern()` call working — see Task 3 for how.
- **`VSChartInteractionDefaults.isInlineSvg()` and `graph.svg.inline` are out of scope.** That is an override, not a sub-gate: an explicit value wins in either direction and it can enable inline SVG without the master gate. Do not touch it.
- **`VSDensityDefaults.isModern()` and `isDark()` survive.** They are the master-gate readers that `VizContext.ofGate()` and `VizMark.fromGate()` call. Only the *six other* classes' `isModern()` methods are deleted.
- **Pure helpers keep no context:** `VSDensityDefaults.normalizeMode(String)`, `rowHeightForMode(String)`, `headerRowHeightForMode(String)`, `titleHeightForMode(String)`, `VSChartPaletteDefaults.spliceLegacy`, `fromFrame`, `clearMemo`. They read no properties.
- Every new `.java` file starts with the AGPL header copied verbatim from a sibling in the same directory, year `2026`.
- Every new test class carries `@Tag("core")`. `core`'s surefire uses `<groups>core</groups>`, so an untagged class silently does not run.
- Comment style: short clauses, not full sentences. No ticket, PR, design-document or task-number references in source comments.
- **Baseline:** community `viz-updates` @ `e670744c1`. Line numbers below were read at that commit; **locate every edit by symbol name and re-read before changing it.** Citations in this feature have been stale in every task so far.
- **The enterprise repo calls none of these classes** — verified. The blast radius is `community/core` only.

---

## Two corrections to the design, found while planning

**1 · The `initDefaultFormat(boolean vs)` surface is nine types, not five.** §2 says "the five chart descriptors". The overload actually exists on `AxisDescriptor`, `ChartRefImpl`, `LegendDescriptor`, `LegendsDescriptor`, `TitleDescriptor`, `PlotDescriptor`, `VSChartAggregateRef`, `VSChartDimensionRef` and `GraphTarget`. Task 7 covers all nine. Note that `TimeSliderVSAssemblyInfo.initDefaultFormat(boolean border)` is an unrelated method — the parameter is `border`, not `vs`. Do not touch it.

**2 · Call-site counts, audited at `e670744c1`.** Use these to budget, not the design's table:

| Class | Sites | Files |
|---|---|---|
| `VSChartChromeDefaults` | 38 | 12 |
| `VSTitleChromeDefaults` | 19 | 14 |
| `VSTableStructureDefaults` | 14 | 1 (`DataVSAQuery`) |
| `VSObjectChromeDefaults` | 14 | 9 |
| `VSDensityDefaults` | 14 | 6 |
| `VSOutputChromeDefaults` | 7 | 2 |
| `VSChartPaletteDefaults` | 4 | 3 |
| `VSCalendarChromeDefaults` | 4 | 2 |
| `VSChartInteractionDefaults` | 1 | 1 — out of scope |

---

## File Structure

**Created:**

| File | Responsibility |
|---|---|
| `core/src/main/java/inetsoft/uql/viewsheet/internal/VizContext.java` | The immutable context and its four factories. The **only** place in the family that reads `SreeEnv` for the gate. |
| `core/src/test/java/inetsoft/uql/viewsheet/internal/VizContextTest.java` | The factories' contract, including that `ofGate()` matches today's predicates exactly. |

**Modified** — the nine `VS*Defaults` classes (eight widened, one untouched), plus the call sites listed per task.

---

### Task 1: `VizContext` and its factories

**Files:**
- Create: `core/src/main/java/inetsoft/uql/viewsheet/internal/VizContext.java`
- Test: `core/src/test/java/inetsoft/uql/viewsheet/internal/VizContextTest.java`

**Interfaces:**
- Consumes: `VSDensityDefaults.isModern()`, `isDark()`, `mode()` (all exist); `VizMark` (exists, from P1).
- Produces: `VizContext` with public final fields `modern` (boolean), `dark` (boolean), `density` (String); statics `ofGate()`, `of(VSAssemblyInfo)`, `of(VizMark)`; constant `LEGACY`. Every later task consumes these.

- [ ] **Step 1: Write the failing test**

Create `core/src/test/java/inetsoft/uql/viewsheet/internal/VizContextTest.java`, AGPL header copied verbatim from `VSDensityDefaultsTest.java` in the same directory:

```java
package inetsoft.uql.viewsheet.internal;

import inetsoft.sree.SreeEnv;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class VizContextTest {
   @AfterEach
   void reset() {
      SreeEnv.setProperty("viewsheet.modernVisualization", null);
      SreeEnv.setProperty("viewsheet.darkMode", null);
      SreeEnv.setProperty("viewsheet.density", null);
   }

   @Test
   void legacyIsOffOnEveryAxis() {
      assertFalse(VizContext.LEGACY.modern);
      assertFalse(VizContext.LEGACY.dark);
      assertEquals("dense", VizContext.LEGACY.density);
   }

   @Test
   void ofGateMatchesTheMasterGate() {
      SreeEnv.setProperty("viewsheet.modernVisualization", "false");
      assertFalse(VizContext.ofGate().modern);

      SreeEnv.setProperty("viewsheet.modernVisualization", "true");
      assertTrue(VizContext.ofGate().modern);
   }

   @Test
   void ofGateAgreesWithTheStaticsItReplaces() {
      // the whole safety argument of this phase: ofGate() must equal today's predicates
      for(String modern : new String[]{ "true", "false" }) {
         for(String dark : new String[]{ "true", "false" }) {
            SreeEnv.setProperty("viewsheet.modernVisualization", modern);
            SreeEnv.setProperty("viewsheet.darkMode", dark);
            VizContext ctx = VizContext.ofGate();
            assertEquals(VSDensityDefaults.isModern(), ctx.modern,
                         "modern must match VSDensityDefaults.isModern()");
            assertEquals(VSDensityDefaults.isDark(), ctx.dark,
                         "dark must match VSDensityDefaults.isDark()");
            assertEquals(VSDensityDefaults.mode(), ctx.density,
                         "density must match VSDensityDefaults.mode()");
         }
      }
   }

   @Test
   void darkRequiresModern() {
      SreeEnv.setProperty("viewsheet.modernVisualization", "false");
      SreeEnv.setProperty("viewsheet.darkMode", "true");
      assertFalse(VizContext.ofGate().dark, "dark is a modifier of modern, never standalone");
   }

   @Test
   void densityFallsBackToDense() {
      SreeEnv.setProperty("viewsheet.modernVisualization", "true");
      assertEquals("dense", VizContext.ofGate().density);

      SreeEnv.setProperty("viewsheet.density", "comfortable");
      assertEquals("comfortable", VizContext.ofGate().density);
   }

   @Test
   void ofAMarkTakesTheMarkNotTheGate() {
      // the gate is closed; a marked assembly still reads modern through of(VizMark)
      SreeEnv.setProperty("viewsheet.modernVisualization", "false");
      assertTrue(VizContext.of(VizMark.MODERN_LIGHT).modern);
      assertFalse(VizContext.of(VizMark.MODERN_LIGHT).dark);
      assertTrue(VizContext.of(VizMark.MODERN_DARK).dark);
   }

   @Test
   void ofAnAbsentMarkIsLegacy() {
      SreeEnv.setProperty("viewsheet.modernVisualization", "true");
      assertFalse(VizContext.of((VizMark) null).modern, "unmarked is never modern");
   }

   @Test
   void ofAnAssemblyReadsItsMark() {
      SreeEnv.setProperty("viewsheet.modernVisualization", "true");
      TextVSAssemblyInfo unmarked = new TextVSAssemblyInfo();
      assertFalse(VizContext.of(unmarked).modern);

      TextVSAssemblyInfo marked = new TextVSAssemblyInfo();
      marked.setVizMark(VizMark.MODERN_DARK);
      assertTrue(VizContext.of(marked).modern);
      assertTrue(VizContext.of(marked).dark);
   }

   @Test
   void ofANullAssemblyIsLegacy() {
      SreeEnv.setProperty("viewsheet.modernVisualization", "true");
      assertFalse(VizContext.of((VSAssemblyInfo) null).modern);
   }

   @Test
   void densityAlwaysComesFromTheOrgNotTheMark() {
      // density is a live preference; the mark decides only whether an assembly honours it
      SreeEnv.setProperty("viewsheet.modernVisualization", "true");
      SreeEnv.setProperty("viewsheet.density", "compact");
      assertEquals("compact", VizContext.of(VizMark.MODERN_LIGHT).density);
      assertEquals("compact", VizContext.of((VizMark) null).density);
   }
}
```

- [ ] **Step 2: Run the test to verify it fails**

From `E:/StyleBI/stylebi-enterprise/community`:
```bash
./mvnw test -pl core -Dtest=VizContextTest
```
Expected: compilation failure — `cannot find symbol: class VizContext`.

- [ ] **Step 3: Write the implementation**

Create `core/src/main/java/inetsoft/uql/viewsheet/internal/VizContext.java`, AGPL header copied verbatim from `VizMark.java` in the same directory:

```java
package inetsoft.uql.viewsheet.internal;

/**
 * The resolved modern-visualization state a value resolver should answer against. Immutable, and
 * built in one of four ways so that the SreeEnv reads live here rather than in every resolver.
 *
 * During the forward half every caller passes ofGate(), which reads exactly what the resolvers used
 * to read themselves - so threading it changes nothing. of(VSAssemblyInfo) and of(VizMark) exist for
 * the phase that makes reads follow the assembly's mark; nothing calls them yet.
 */
public final class VizContext {
   /** Legacy on every axis. For report charts, which have no viewsheet and no mark. */
   public static final VizContext LEGACY = new VizContext(false, false, DENSE);

   private VizContext(boolean modern, boolean dark, String density) {
      this.modern = modern;
      this.dark = dark;
      this.density = density;
   }

   /**
    * The org gate as it stands. Equivalent to the predicates each resolver used to evaluate itself.
    */
   public static VizContext ofGate() {
      boolean modern = VSDensityDefaults.isModern();
      return new VizContext(modern, VSDensityDefaults.isDark(), VSDensityDefaults.mode());
   }

   /**
    * The context an assembly's own provenance implies. Null, or an unmarked assembly, reads legacy.
    */
   public static VizContext of(VSAssemblyInfo info) {
      return of(info == null ? null : info.getVizMark());
    }

   /**
    * The context a mark implies. Density still comes from the org: the mark decides whether an
    * assembly honours density, not which density is in force.
    */
   public static VizContext of(VizMark mark) {
      if(mark == null) {
         return new VizContext(false, false, VSDensityDefaults.mode());
      }

      return new VizContext(true, mark == VizMark.MODERN_DARK, VSDensityDefaults.mode());
   }

   /** Whether modern chrome applies. */
   public final boolean modern;
   /** Whether the dark palette applies. Never true without modern. */
   public final boolean dark;
   /** The active density mode: dense, compact or comfortable. Meaningful only when modern. */
   public final String density;

   private static final String DENSE = "dense";
}
```

Fix the stray indentation on `of(VSAssemblyInfo)`'s closing brace when you paste — it should align with the method's `public`.

- [ ] **Step 4: Run the test to verify it passes**

```bash
./mvnw test -pl core -Dtest=VizContextTest
```
Expected: PASS, 10 tests.

- [ ] **Step 5: Verify the mark-reading factories are unused**

```bash
grep -rn "VizContext.of(" --include=*.java core/src/main
```
Expected: hits only inside `VizContext.java` itself (the `of(VSAssemblyInfo)` → `of(VizMark)` delegation). Nothing else may call them until P4.

- [ ] **Step 6: Snapshot the diff**

Do not commit — `git add`/`commit`/`push` are denied by project policy and the human partner commits manually. Report the files you changed and stop.

---

### Task 2: `VSDensityDefaults` takes the context

**Files:**
- Modify: `core/src/main/java/inetsoft/uql/viewsheet/internal/VSDensityDefaults.java`
- Modify (call sites, 14 across 6 files): `report/composition/VSTableLens.java`, `uql/viewsheet/graph/AbstractChartInfo.java`, `uql/viewsheet/internal/SelectionBaseVSAssemblyInfo.java`, `web/admin/presentation/LookAndFeelService.java`, `web/viewsheet/controller/table/BaseTableService.java`
- Test: `core/src/test/java/inetsoft/uql/viewsheet/internal/VSDensityDefaultsTest.java`

**Interfaces:**
- Consumes: `VizContext.ofGate()`, `VizContext.LEGACY` (Task 1).
- Produces: `rowHeight(VizContext)`, `headerRowHeight(VizContext)`, `cellHeight(VizContext)`, `titleHeight(VizContext)`. `isModern()`, `isDark()`, `mode()`, `normalizeMode(String)` and the three `*ForMode(String)` helpers keep their current signatures.

**Why this class first:** every other `VS*Defaults` delegates to its `isModern()`/`isDark()`, and `VizContext.ofGate()` is built from it. Widening it first means later tasks read `ctx.dark` instead of `VSDensityDefaults.isDark()` with no interim inconsistency.

- [ ] **Step 1: Update the existing test to the new signatures**

In `VSDensityDefaultsTest.java`, the four height accessors now need a context. Replace each `VSDensityDefaults.rowHeight()`-style call with the context form, and add one test pinning that a legacy context yields legacy heights:

```java
   @Test
   void aLegacyContextYieldsLegacyHeights() {
      assertEquals(AssetUtil.defh, VSDensityDefaults.rowHeight(VizContext.LEGACY));
      assertEquals(AssetUtil.defh, VSDensityDefaults.headerRowHeight(VizContext.LEGACY));
      assertEquals(AssetUtil.defh, VSDensityDefaults.cellHeight(VizContext.LEGACY));
      assertEquals(AssetUtil.defh, VSDensityDefaults.titleHeight(VizContext.LEGACY));
   }

   @Test
   void aModernContextYieldsItsDensityHeights() {
      VizContext ctx = VizContext.of(VizMark.MODERN_LIGHT);
      SreeEnv.setProperty("viewsheet.density", "comfortable");
      ctx = VizContext.of(VizMark.MODERN_LIGHT);
      assertEquals(28, VSDensityDefaults.rowHeight(ctx));
      assertEquals(30, VSDensityDefaults.headerRowHeight(ctx));
      assertEquals(30, VSDensityDefaults.titleHeight(ctx));
   }
```

Leave the existing `*ForMode(String)` tests unchanged — those helpers keep their signatures.

- [ ] **Step 2: Run to verify it fails**

```bash
./mvnw test -pl core -Dtest=VSDensityDefaultsTest
```
Expected: compilation failure — `rowHeight(VizContext)` not found.

- [ ] **Step 3: Widen the four height accessors**

In `VSDensityDefaults.java`, change the four accessors to take a context and read it rather than `SreeEnv`. Each currently reads `isModern() ? xForMode(mode()) : AssetUtil.defh`; it becomes:

```java
   /**
    * Default data-row height for the context's mode, or the legacy default when it is not modern.
    */
   public static int rowHeight(VizContext ctx) {
      return ctx.modern ? rowHeightForMode(ctx.density) : AssetUtil.defh;
   }

   /**
    * Default header-row height for the context's mode, or the legacy default when not modern.
    */
   public static int headerRowHeight(VizContext ctx) {
      return ctx.modern ? headerRowHeightForMode(ctx.density) : AssetUtil.defh;
   }

   /**
    * Default selection-list cell height. Selection cells are a data surface, so they share the
    * table row-height matrix.
    */
   public static int cellHeight(VizContext ctx) {
      return ctx.modern ? rowHeightForMode(ctx.density) : AssetUtil.defh;
   }

   /**
    * The title lane's height. Compact and comfortable borrow the header row's steps so the lane can
    * hold the 24px anchored strip with clearance; dense stays at defh, which is the one tier that
    * must equal legacy and the one where the strip does not anchor at all.
    */
   public static int titleHeight(VizContext ctx) {
      return ctx.modern ? titleHeightForMode(ctx.density) : AssetUtil.defh;
   }
```

Leave `isModern()`, `isDark()`, `mode()`, `normalizeMode(String)` and the three `*ForMode(String)` helpers exactly as they are.

- [ ] **Step 4: Update all 14 call sites to pass `ofGate()`**

Locate each by symbol, not line number. In every case the change is the same shape — add `VizContext.ofGate()` as the argument, and where the site guards on `VSDensityDefaults.isModern()` immediately before, hoist one context and use it for both:

- `report/composition/VSTableLens.java` — in the method that sets row sizes, `VSDensityDefaults.isModern()` guards three `rowHeight()`/`headerRowHeight()` calls. Hoist `VizContext ctx = VizContext.ofGate();` above the guard, change the guard to `ctx.modern`, and pass `ctx` to each accessor.
- `web/viewsheet/controller/table/BaseTableService.java` — same shape; hoist one `ctx` and use it for the guard and both accessors.
- `uql/viewsheet/internal/SelectionBaseVSAssemblyInfo.java` — `cellHeight()` under an `isModern()` guard; hoist one `ctx`.
- `uql/viewsheet/graph/AbstractChartInfo.java` — `VSDensityDefaults.isModern()` in the tooltip-style resolution. This one has no height call; leave it reading `VSDensityDefaults.isModern()` directly for now — it is a master-gate read, not a sub-gate, and Task 10 confirms whether it should become a context. **Add no context here.**
- `web/admin/presentation/LookAndFeelService.java` — calls `normalizeMode(String)` only, which is unchanged. **No edit.**
- `uql/viewsheet/internal/VizMark.java` — calls `isModern()`/`isDark()`, both unchanged. **No edit.**

- [ ] **Step 5: Run the tests**

```bash
./mvnw test -pl core -Dtest='VizContextTest,VSDensityDefaultsTest,VizMarkTest,VSAssemblyInfoVizMarkTest,ViewsheetVizMarkTest,AbstractVSAssemblyVizMarkTest'
```
Expected: all pass. The P1 seed-mark tests must be untouched by this change.

- [ ] **Step 6: Compile the module**

```bash
./mvnw clean install -pl core -DskipTests -o
```
Expected: BUILD SUCCESS. A missed call site shows up here as a compile error, which is the point of widening rather than overloading.

- [ ] **Step 7: Report and stop** — no commit.

---

### Task 3: `VSObjectChromeDefaults` — and the one method that must not be widened

**Files:**
- Modify: `core/src/main/java/inetsoft/uql/viewsheet/internal/VSObjectChromeDefaults.java`
- Modify (14 sites across 9 files): `report/gui/viewsheet/VSSlider.java`, `uql/viewsheet/graph/PlotDescriptor.java`, `uql/viewsheet/internal/ChartVSAssemblyInfo.java`, `uql/viewsheet/internal/TableDataVSAssemblyInfo.java`, `uql/viewsheet/internal/ViewsheetVSAssemblyInfo.java`, `uql/viewsheet/internal/VSAssemblyInfo.java`, `web/binding/controller/ChangeChartTypeService.java`, `web/viewsheet/model/VSSliderModel.java`
- **Not modified:** `uql/viewsheet/VSCompositeFormat.java`
- Test: `core/src/test/java/inetsoft/uql/viewsheet/internal/VSObjectChromeDefaultsTest.java`

**Interfaces:**
- Consumes: `VizContext` (Task 1).
- Produces: `objectBorderColor(VizContext)`, `pageBackgroundCss(VizContext)`, `cardBackgroundCss(VizContext)`, `textForegroundCss(VizContext)`, `applyDarkForeground(VSCompositeFormat, VizContext)`. `cardCornerRadius()` stays context-free (a constant). `resolveSeededCorner(int)` stays exactly as it is. `isModern()` is **deleted**.

**The exception, and read it before editing.** `resolveSeededCorner(int)` is called from `VSCompositeFormat.resolveDefaultTierCorner`, which is a method on a format with no route to an assembly or a context. It calls `isModern()` internally. Since `isModern()` is being deleted from this class, `resolveSeededCorner` must inline the master-gate read instead:

```java
   public static int resolveSeededCorner(int radius) {
      // reads the gate directly: the only caller is a VSFormat getter with no context to hand
      return radius == CARD_CORNER_RADIUS && !VSDensityDefaults.isModern() ? 0 : radius;
   }
```

That is a deliberate, commented exception — the interim radius reversal, which the revert sweep deletes later. Do not "fix" it by widening the signature; `VSCompositeFormat` cannot supply an argument.

- [ ] **Step 1: Update the existing test**

`VSObjectChromeDefaultsTest.java` currently exercises `isModern()` and the colour getters. Rewrite it so:
- the `isModern()` tests become `VizContext` assertions where they were really testing the master gate, and are **deleted** where they were testing the sub-gate (see Task 10's list — one of them is in `VSOutputChromeDefaultsTest`, not this file);
- each colour getter is called with a context;
- `resolveSeededCorner` keeps its existing tests **unchanged** — its behaviour and signature do not move.

Add one test pinning the exception:

```java
   @Test
   void resolveSeededCornerStillReadsTheGateDirectly() {
      SreeEnv.setProperty("viewsheet.modernVisualization", "true");
      assertEquals(12, VSObjectChromeDefaults.resolveSeededCorner(12), "gate on keeps the seed");

      SreeEnv.setProperty("viewsheet.modernVisualization", "false");
      assertEquals(0, VSObjectChromeDefaults.resolveSeededCorner(12), "gate off strips the seed");
      assertEquals(6, VSObjectChromeDefaults.resolveSeededCorner(6), "a non-seed radius survives");
   }
```

- [ ] **Step 2: Run to verify it fails**

```bash
./mvnw test -pl core -Dtest=VSObjectChromeDefaultsTest
```
Expected: compilation failure on the widened getters.

- [ ] **Step 3: Widen the getters, delete `isModern()`, inline the gate read in `resolveSeededCorner`**

Each colour getter currently reads `VSDensityDefaults.isDark()`; it now reads `ctx.dark`:

```java
   /** Object-frame border default - the shared structural neutral, dark in dark mode. */
   public static Color objectBorderColor(VizContext ctx) {
      return ctx.dark ? OBJECT_BORDER_DARK : OBJECT_BORDER;
   }

   /** Viewsheet page/canvas background default, as a CSS hex string, dark in dark mode. */
   public static String pageBackgroundCss(VizContext ctx) {
      Color bg = ctx.dark ? PAGE_BG_DARK : PAGE_BG;
      return String.format("#%06x", bg.getRGB() & 0xFFFFFF);
   }

   /**
    * Object-card background default as a CSS hex string. White in legacy and light modern; a lifted
    * dark surface in dark mode so light chart chrome stays legible on the card.
    */
   public static String cardBackgroundCss(VizContext ctx) {
      Color bg = ctx.dark ? CARD_BG_DARK : CARD_BG;
      return String.format("#%06x", bg.getRGB() & 0xFFFFFF);
   }

   /**
    * Dark-mode light text color as a CSS hex string, or null when not dark. For object text whose
    * default is a fixed black and would be dark-on-dark otherwise.
    */
   public static String textForegroundCss(VizContext ctx) {
      return ctx.dark ? String.format("#%06x", TEXT_FG_DARK.getRGB() & 0xFFFFFF) : null;
   }

   /**
    * Return the given object format with a light text foreground substituted on the DEFAULT tier of
    * a clone in dark mode, or the original unchanged. Never mutates the source.
    */
   public static VSCompositeFormat applyDarkForeground(VSCompositeFormat fmt, VizContext ctx) {
      if(!ctx.dark || fmt == null) {
         return fmt;
      }

      if(fmt.getUserDefinedFormat().isForegroundValueDefined() ||
         fmt.getCSSFormat().isForegroundValueDefined())
      {
         return fmt;
      }

      VSCompositeFormat clone = fmt.clone();
      clone.getDefaultFormat().setForegroundValue(
         String.format("0x%06x", TEXT_FG_DARK.getRGB() & 0xFFFFFF));
      return clone;
   }
```

Delete `isModern()` entirely. Apply the `resolveSeededCorner` change quoted above. Leave `cardCornerRadius()` alone.

- [ ] **Step 4: Update the 14 call sites**

Every site currently reads `VSObjectChromeDefaults.isModern() ? VSObjectChromeDefaults.someValue() : legacy`. Hoist one context per method and use it for both halves. The two seeding sites need care because they are the P1 stamp's consumers:

- `uql/viewsheet/internal/VSAssemblyInfo.java`, in `setDefaultFormat(boolean, boolean, boolean)` — the border colour and the card radius. Hoist `VizContext ctx = VizContext.ofGate();` at the top of the method; the two ternaries become `ctx.modern ? VSObjectChromeDefaults.objectBorderColor(ctx) : DEFAULT_BORDER_COLOR` and `ctx.modern && isCornerSeedTarget() ? VSObjectChromeDefaults.cardCornerRadius() : 0`. **`ofGate()`, not `of(this)`** — reads follow the mark in P4, not here.
- `uql/viewsheet/internal/ChartVSAssemblyInfo.java` — `cardBackgroundCss()` is called unconditionally, and `isModern()` guards the `barCornerRadius`/`smoothLines` block. Hoist one `ctx`; pass it to `cardBackgroundCss(ctx)` and change the guard to `ctx.modern`.
- `uql/viewsheet/internal/TableDataVSAssemblyInfo.java` — `cardBackgroundCss()` unconditional; pass `VizContext.ofGate()`.
- `uql/viewsheet/internal/ViewsheetVSAssemblyInfo.java` — `isModern() ? pageBackgroundCss() : "#f5f5f5"`; hoist one `ctx`.
- `uql/viewsheet/graph/PlotDescriptor.java` — `isModern()` in the two seed-boolean getters (`getBarCornerRadius`, `getSmoothLines`). These have no context to hand and are the interim reversal mechanisms the revert sweep deletes. **Change them to `VSDensityDefaults.isModern()` with a short comment**, the same exception as `resolveSeededCorner`.
- `report/gui/viewsheet/VSSlider.java`, `web/viewsheet/model/VSSliderModel.java` — `applyDarkForeground(fmt)` / `textForegroundCss()`; pass `VizContext.ofGate()`.
- `web/binding/controller/ChangeChartTypeService.java` — `isModern()`; replace with `VizContext.ofGate().modern`.

- [ ] **Step 5: Run the tests**

```bash
./mvnw test -pl core -Dtest='VSObjectChromeDefaultsTest,VizContextTest,VSDensityDefaultsTest'
```
Expected: pass.

- [ ] **Step 6: Compile** — `./mvnw clean install -pl core -DskipTests -o`. Expected BUILD SUCCESS.

- [ ] **Step 7: Confirm `VSCompositeFormat` is untouched**

```bash
git diff --stat -- core/src/main/java/inetsoft/uql/viewsheet/VSCompositeFormat.java
```
Expected: empty. If it is not, the `resolveSeededCorner` exception was not honoured.

- [ ] **Step 8: Report and stop** — no commit.

---

### Task 4: `VSTitleChromeDefaults`

**Files:**
- Modify: `core/src/main/java/inetsoft/uql/viewsheet/internal/VSTitleChromeDefaults.java`
- Modify (19 sites across 14 files): `report/io/viewsheet/AbstractVSExporter.java`, `ExportUtil.java`, `VSSelectionListHelper.java`, `VSSelectionTreeHelper.java`, `VSTableDataHelper.java`, `uql/viewsheet/internal/VsToReportConverter.java`, `web/composer/vs/controller/FormatPainterService.java`, `web/viewsheet/model/calendar/VSCalendarModel.java`, `web/viewsheet/model/chart/VSChartModel.java`, `web/viewsheet/model/table/BaseTableModel.java`, `web/viewsheet/model/VSCheckBoxModel.java`, `VSCompositeModel.java`, `VSRadioButtonModel.java`, `VSRangeSliderModel.java`
- Test: `core/src/test/java/inetsoft/uql/viewsheet/internal/VSTitleChromeDefaultsTest.java` (if present; create the assertions inline in an existing test class if not)

**Interfaces:**
- Consumes: `VizContext` (Task 1).
- Produces: `titleBackground(VizContext)`, `titleForeground(VizContext)`, `titleBorderColor(VizContext)`, `applyModernDefaults(VSCompositeFormat, VizContext)`, `applyModernDefaultsInPlace(VSCompositeFormat, VizContext)`. `isModern()` is **deleted**.

- [ ] **Step 1: Update the test to the new signatures**

Change each getter call to pass a context, and replace any `isModern()` assertion with the context equivalent. Add:

```java
   @Test
   void aLegacyContextLeavesATitleFormatAlone() {
      VSCompositeFormat fmt = new VSCompositeFormat();
      assertSame(fmt, VSTitleChromeDefaults.applyModernDefaults(fmt, VizContext.LEGACY),
                 "a legacy context must return the original, not a clone");
   }
```

- [ ] **Step 2: Run to verify it fails** — `./mvnw test -pl core -Dtest=VSTitleChromeDefaultsTest`. Expected: compilation failure.

- [ ] **Step 3: Widen the class**

The three colour getters read `ctx.dark` instead of `VSDensityDefaults.isDark()`. The two `apply*` methods take the context as their second parameter and replace their internal `isModern()` guard with `ctx.modern`:

```java
   public static Color titleBackground(VizContext ctx) {
      return ctx.dark ? TITLE_BG_DARK : TITLE_BG;
   }

   public static Color titleForeground(VizContext ctx) {
      return ctx.dark ? TITLE_FG_DARK : TITLE_FG;
   }

   public static Color titleBorderColor(VizContext ctx) {
      return ctx.dark ? TITLE_BORDER_DARK : TITLE_BORDER;
   }
```

For `applyModernDefaults(VSCompositeFormat titleFmt, VizContext ctx)` and `applyModernDefaultsInPlace(VSCompositeFormat titleFmt, VizContext ctx)`, replace the leading `if(!isModern() …)` guard with `if(!ctx.modern …)` and pass `ctx` to the colour getters inside. Delete `isModern()`.

- [ ] **Step 4: Update the 19 call sites** — each passes `VizContext.ofGate()` as the new trailing argument. Where a site guards on `VSTitleChromeDefaults.isModern()` before calling, hoist one context and use `ctx.modern` for the guard.

- [ ] **Step 5: Run** — `./mvnw test -pl core -Dtest='VSTitleChromeDefaultsTest,VizContextTest'`. Expected: pass.

- [ ] **Step 6: Compile** — `./mvnw clean install -pl core -DskipTests -o`. Expected BUILD SUCCESS.

- [ ] **Step 7: Report and stop** — no commit.

---

### Task 5: `VSOutputChromeDefaults` and `VSCalendarChromeDefaults`

**Files:**
- Modify: `core/src/main/java/inetsoft/uql/viewsheet/internal/VSOutputChromeDefaults.java`, `VSCalendarChromeDefaults.java`
- Modify (11 sites): `report/gui/viewsheet/VSSlider.java`, `report/io/viewsheet/AbstractVSExporter.java`, `web/composer/vs/controller/FormatPainterService.java`, `web/viewsheet/model/VSTextModel.java`, `report/gui/viewsheet/VSCalendar.java`, `web/viewsheet/model/calendar/VSCalendarModel.java`
- Test: `core/src/test/java/inetsoft/uql/viewsheet/internal/VSOutputChromeDefaultsTest.java`

**Interfaces:**
- Consumes: `VizContext` (Task 1).
- Produces: `sliderInactiveTrack(VizContext)`, `sliderActiveTrack(VizContext)`, `sliderHandle(VizContext)`, `sliderTick(VizContext)`, `valueForeground(VizContext)`, `valueBorderColor(VizContext)`, `applyModernDefaults(VSCompositeFormat, VizContext)`, `applyModernDefaultsInPlace(VSCompositeFormat, VizContext)` on `VSOutputChromeDefaults`, whose `isModern()` is **deleted**; and `VSCalendarChromeDefaults.applyModernDefaults(VSCompositeFormat, VizContext)`.

**Grouped because they are small and share callers** — `VSCalendar` and `VSCalendarModel` touch both families.

**Note on `VSCalendarChromeDefaults`:** it has **no** `isModern()` and guards on `VSDensityDefaults.isDark()` alone. Its widened guard is therefore `if(!ctx.dark || fmt == null)`, not `ctx.modern`. There is no sub-gate to delete here.

- [ ] **Step 1: Update the tests.** Pass a context to every getter. **Delete** `VSOutputChromeDefaultsTest`'s escape-hatch test — the one at roughly `:85-86` asserting `"modernObjectChrome=false opts out"` — and remove the `SreeEnv.setProperty("viewsheet.modernObjectChrome", null)` reset line at roughly `:50`. That test *is* the sub-gate; it goes with it.

- [ ] **Step 2: Run to verify it fails** — `./mvnw test -pl core -Dtest=VSOutputChromeDefaultsTest`. Expected: compilation failure.

- [ ] **Step 3: Widen both classes.** Every `VSOutputChromeDefaults` colour getter reads `ctx.dark` instead of `VSDensityDefaults.isDark()`; the two `apply*` methods take `ctx` and use `ctx.modern` for their guard; `isModern()` is deleted. `VSCalendarChromeDefaults.applyModernDefaults` takes `ctx` and guards on `!ctx.dark`.

- [ ] **Step 4: Update the 11 call sites** — each passes `VizContext.ofGate()`.

- [ ] **Step 5: Run** — `./mvnw test -pl core -Dtest='VSOutputChromeDefaultsTest,VizContextTest'`. Expected: pass, with one fewer test than before (the deleted escape hatch).

- [ ] **Step 6: Compile** — `./mvnw clean install -pl core -DskipTests -o`. Expected BUILD SUCCESS.

- [ ] **Step 7: Report and stop** — no commit.

---

### Task 6: `VSTableStructureDefaults`

**Files:**
- Modify: `core/src/main/java/inetsoft/uql/viewsheet/internal/VSTableStructureDefaults.java`
- Modify: `core/src/main/java/inetsoft/report/composition/execution/DataVSAQuery.java` — **all 14 sites are in this one file**
- Test: create `core/src/test/java/inetsoft/uql/viewsheet/internal/VSTableStructureDefaultsTest.java`

**Interfaces:**
- Consumes: `VizContext` (Task 1).
- Produces: `gridlineColor(VizContext)`, `headerBackground(VizContext)`, `headerForeground(VizContext)`, `headerSeparator(VizContext)`, `totalBackground(VizContext)`, `subtotalBackground(VizContext)`, `bandForeground(VizContext)`, `bodyForeground(VizContext)`, `bodyBackground(VizContext)`, `zebraBackground(VizContext)`. `isModern()` is **deleted**.

**This class has no test today** — it was the one sub-gate with zero coverage. Create one, because the widening is otherwise unverified.

- [ ] **Step 1: Write the failing test**

Create `VSTableStructureDefaultsTest.java`, AGPL header from `VSDensityDefaultsTest.java`, `@Tag("core")`, the same Spring annotations. Cover the light/dark split on three representative getters and the fact that a legacy context is never consulted for colour (the caller guards on `ctx.modern`):

```java
   @Test
   void lightAndDarkDifferOnTheHeaderAndBody() {
      VizContext light = VizContext.of(VizMark.MODERN_LIGHT);
      VizContext dark = VizContext.of(VizMark.MODERN_DARK);

      assertNotEquals(VSTableStructureDefaults.headerBackground(light),
                      VSTableStructureDefaults.headerBackground(dark));
      assertNotEquals(VSTableStructureDefaults.bodyBackground(light),
                      VSTableStructureDefaults.bodyBackground(dark));
      assertNotEquals(VSTableStructureDefaults.gridlineColor(light),
                      VSTableStructureDefaults.gridlineColor(dark));
   }

   @Test
   void everyGetterAnswersForALightContextWithoutReadingTheGate() {
      SreeEnv.setProperty("viewsheet.modernVisualization", "false");
      VizContext light = VizContext.of(VizMark.MODERN_LIGHT);
      assertNotNull(VSTableStructureDefaults.headerForeground(light));
      assertNotNull(VSTableStructureDefaults.headerSeparator(light));
      assertNotNull(VSTableStructureDefaults.totalBackground(light));
      assertNotNull(VSTableStructureDefaults.subtotalBackground(light));
      assertNotNull(VSTableStructureDefaults.bandForeground(light));
      assertNotNull(VSTableStructureDefaults.bodyForeground(light));
      assertNotNull(VSTableStructureDefaults.zebraBackground(light));
   }
```

- [ ] **Step 2: Run to verify it fails** — `./mvnw test -pl core -Dtest=VSTableStructureDefaultsTest`. Expected: compilation failure.

- [ ] **Step 3: Widen the ten getters.** Each reads `ctx.dark` instead of `VSDensityDefaults.isDark()`. Delete `isModern()`.

- [ ] **Step 4: Update `DataVSAQuery`.** One `VSTableStructureDefaults.isModern()` guard covers the block containing the other 13 calls. Hoist `VizContext ctx = VizContext.ofGate();` above the guard, change the guard to `ctx.modern`, and pass `ctx` to each getter.

- [ ] **Step 5: Run** — `./mvnw test -pl core -Dtest='VSTableStructureDefaultsTest,VizContextTest'`. Expected: pass.

- [ ] **Step 6: Compile** — `./mvnw clean install -pl core -DskipTests -o`. Expected BUILD SUCCESS.

- [ ] **Step 7: Report and stop** — no commit.

---

### Task 7: `initDefaultFormat(boolean vs)` becomes `(VizContext)` — nine types

**Files:**
- Modify: `uql/viewsheet/graph/AxisDescriptor.java`, `ChartRefImpl.java`, `LegendDescriptor.java`, `LegendsDescriptor.java`, `TitleDescriptor.java`, `PlotDescriptor.java`, `VSChartAggregateRef.java`, `VSChartDimensionRef.java`, `report/composition/graph/GraphTarget.java`
- Modify (callers): `report/composition/graph/VGraphPair.java` (28 calls), `web/composer/vs/objects/controller/ComposerVSSelectionListService.java` (1)

**Interfaces:**
- Consumes: `VizContext.LEGACY` (Task 1), and `VSChartChromeDefaults`'s getters as they stand — Task 8 widens those. **Order matters: do this task before Task 8**, so the descriptors have a context to pass on when Task 8 widens the getters they call.
- Produces: `initDefaultFormat(VizContext ctx)` on all nine types; the no-arg `initDefaultFormat()` delegates to `initDefaultFormat(VizContext.LEGACY)`.

**Why this is a signature change and nothing more.** The `boolean vs` parameter already means "this is a viewsheet chart", which is exactly the distinction a context carries. `false` becomes `VizContext.LEGACY`; `true` becomes the caller's context. No gate read moves in this task — the bodies still call `VSChartChromeDefaults.isModern()` until Task 8.

- [ ] **Step 1: Change the nine declarations**

For each of the nine types, the pattern is identical. `AxisDescriptor` as the worked example:

```java
   public void initDefaultFormat() {
      initDefaultFormat(VizContext.LEGACY);
   }

   public void initDefaultFormat(VizContext ctx) {
      TextFormat deffmt = fmt.getDefaultFormat();
      deffmt.setColor(ctx.modern ? VSChartChromeDefaults.labelColor() : GDefaults.DEFAULT_TEXT_COLOR);
      // ... rest unchanged
   }
```

Note the body's `vs && VSChartChromeDefaults.isModern()` collapses to `ctx.modern` — the two terms it ANDed are exactly "is a viewsheet chart" and "is the gate on", which `ctx.modern` now carries together. `VizContext.LEGACY.modern` is `false`, so the report path is unchanged.

Add `import inetsoft.uql.viewsheet.internal.VizContext;` to each file outside `inetsoft.uql.viewsheet.internal`.

- [ ] **Step 2: Update the 28 `VGraphPair` calls and the one in `ComposerVSSelectionListService`**

In `VGraphPair`, every `xDesc.initDefaultFormat(true)` becomes `xDesc.initDefaultFormat(ctx)`. Hoist **one** `VizContext ctx = VizContext.ofGate();` in each method that contains such calls — do not build a context per call. `ComposerVSSelectionListService` passes `VizContext.ofGate()` likewise.

Also update `VGraphPair`'s two private helpers `initDefaultFormat(ChartRef)` and `initDefaultFormat(CompositeTextFormat)` to take and forward the context rather than reading the gate themselves.

- [ ] **Step 3: Compile** — `./mvnw clean install -pl core -DskipTests -o`. Expected BUILD SUCCESS. Any missed caller is a compile error.

- [ ] **Step 4: Run the chart tests**

```bash
./mvnw test -pl core -Dtest='ChartInfoTooltipPersistenceTest,PlotDescriptorXmlTest,PlotDescriptorTextLayoutTest,TextLayoutItemTest,VGraphPairModernPaletteTest'
```
Expected: pass. These are the existing tests over the descriptors and the graph pair.

- [ ] **Step 5: Report and stop** — no commit.

---

### Task 8: `VSChartChromeDefaults` — the largest surface

**Files:**
- Modify: `core/src/main/java/inetsoft/uql/viewsheet/internal/VSChartChromeDefaults.java`
- Modify (38 sites across 12 files): `report/composition/graph/GraphGenerator.java`, `RadarGraphGenerator.java`, `VGraphPair.java`, `uql/viewsheet/graph/AxisDescriptor.java`, `ChartRefImpl.java`, `CSSChartStyles.java`, `LegendDescriptor.java`, `LegendsDescriptor.java`, `TitleDescriptor.java`, `web/composer/model/vs/ChartLinePaneModel.java`, `web/graph/model/dialog/AxisPropertyDialogModel.java`, `web/graph/model/dialog/LegendFormatDialogModel.java`
- Test: `core/src/test/java/inetsoft/uql/viewsheet/internal/VSChartChromeDefaultsTest.java`

**Interfaces:**
- Consumes: `VizContext` (Task 1); the nine `initDefaultFormat(VizContext)` signatures (Task 7).
- Produces: `gridlineColor(VizContext)`, `legendBorderColor(VizContext)`, `labelColor(VizContext)`, `titleColor(VizContext)`, `legendBackground(VizContext)`, `resolveAxisLineColor(Color, VizContext)`, `resolveGridlineColor(Color, VizContext)`, `resolveLegendBorderColor(Color, VizContext)`. `isModern()` is **deleted**.

- [ ] **Step 1: Update the test.** Pass a context to every getter and to the three `resolve*` methods. Remove the `SreeEnv.setProperty("viewsheet.modernChartChrome", null)` reset line at roughly `:27` — its subject is gone. If the class has an `isModern()` test, convert it to a `VizContext` assertion or delete it if it was testing the sub-gate.

- [ ] **Step 2: Run to verify it fails** — `./mvnw test -pl core -Dtest=VSChartChromeDefaultsTest`. Expected: compilation failure.

- [ ] **Step 3: Widen the class.** The five colour getters read `ctx.dark`. The three `resolve*` methods take the context as a trailing parameter and replace their internal `isModern()` guard with `ctx.modern`. Delete `isModern()`.

- [ ] **Step 4: Update the 38 call sites.** Three shapes, and the third needs a judgement:

- **The five descriptors** (`AxisDescriptor`, `ChartRefImpl`, `LegendDescriptor`, `LegendsDescriptor`, `TitleDescriptor`) — already have `ctx` in scope from Task 7; pass it to the getters.
- **`CSSChartStyles.apply`, `GraphGenerator`, `RadarGraphGenerator`, `VGraphPair`** — hoist one `VizContext.ofGate()` per method and pass it down.
- **The three dialog models** (`ChartLinePaneModel`, `AxisPropertyDialogModel`, `LegendFormatDialogModel`) — these call the `resolve*` family and **may not have an assembly in hand**. For P2 they pass `VizContext.ofGate()`, which is behaviour-identical. **Record in your report whether each one could reach an assembly if it had to**, because P4 needs that answer and the design flags it as an open plumbing question.

- [ ] **Step 5: Run** — `./mvnw test -pl core -Dtest='VSChartChromeDefaultsTest,VizContextTest,ChartPlotOptionsPaneModelTest'`. Expected: pass.

- [ ] **Step 6: Compile** — `./mvnw clean install -pl core -DskipTests -o`. Expected BUILD SUCCESS.

- [ ] **Step 7: Report and stop** — no commit. Include the dialog-model reachability finding.

---

### Task 9: `VSChartPaletteDefaults`

**Files:**
- Modify: `core/src/main/java/inetsoft/uql/viewsheet/internal/VSChartPaletteDefaults.java`
- Modify (4 sites): `report/composition/graph/VGraphPair.java`, `report/css/CSSProcessor.java`, `report/internal/graph/ChangeChartProcessor.java`, `web/portal/controller/ChartColorPaletteController.java`
- Test: `core/src/test/java/inetsoft/uql/viewsheet/internal/VSChartPaletteDefaultsTest.java`, `core/src/test/java/inetsoft/report/composition/graph/VGraphPairModernPaletteTest.java`

**Interfaces:**
- Consumes: `VizContext` (Task 1).
- Produces: `activePalette(VizContext)`, `pickerPalette(VizContext)`, `applyModernPalette(CategoricalColorFrame, VizContext)`. `modernPalette()` and `darkPalette()` stay context-free (they name a fixed palette each). `isModern()` is **deleted**. `spliceLegacy`, `fromFrame` and `clearMemo` are unchanged internal helpers.

**`CSSProcessor.applyCSS` is the one call site with no route to an assembly** — the design's open item 1. For P2 it passes `VizContext.ofGate()`, which is behaviour-identical. **Record in your report whether its callers could supply a context**, because P4 needs that answer.

- [ ] **Step 1: Update the tests.** Pass a context to `activePalette`, `pickerPalette` and `applyModernPalette`. **Delete** `VSChartPaletteDefaultsTest.subGateFalseOptsOutEvenWhenBaseGateOn` at roughly `:68-72` — that test *is* the sub-gate. Remove the `SreeEnv.setProperty("viewsheet.modernChartPalette", null)` reset lines at roughly `VSChartPaletteDefaultsTest:31` and `VGraphPairModernPaletteTest:47`.

- [ ] **Step 2: Run to verify it fails** — `./mvnw test -pl core -Dtest='VSChartPaletteDefaultsTest,VGraphPairModernPaletteTest'`. Expected: compilation failure.

- [ ] **Step 3: Widen the class.** `activePalette(ctx)` returns `ctx.dark ? darkPalette() : modernPalette()`; `pickerPalette(ctx)` and `applyModernPalette(frame, ctx)` replace their `isModern()` guard with `ctx.modern`. Delete `isModern()`.

- [ ] **Step 4: Update the four call sites** — each passes `VizContext.ofGate()`.

- [ ] **Step 5: Run** — `./mvnw test -pl core -Dtest='VSChartPaletteDefaultsTest,VGraphPairModernPaletteTest,VizContextTest'`. Expected: pass, with one fewer test than before.

- [ ] **Step 6: Compile** — `./mvnw clean install -pl core -DskipTests -o`. Expected BUILD SUCCESS.

- [ ] **Step 7: Report and stop** — no commit. Include the `CSSProcessor` reachability finding.

---

### Task 10: The sweep — prove the phase did what it claims

**Files:** none expected. This task verifies; if it finds a gap, fix it and say so.

- [ ] **Step 1: No sub-gate property is read anywhere**

```bash
grep -rn "modernObjectChrome\|modernChartChrome\|modernChartPalette\|modernTableStructure" --include=*.java core/src
```
Expected: **zero hits**, in `main` and `test` alike. Any hit is either a missed deletion or a test that should have gone with it.

- [ ] **Step 2: The six per-class `isModern()` methods are gone**

```bash
grep -rn "public static boolean isModern" --include=*.java core/src/main
```
Expected: exactly **one** hit — `VSDensityDefaults.isModern()`, which survives as the master-gate reader.

- [ ] **Step 3: No resolver reads `SreeEnv` for the gate any more**

```bash
grep -rn "modernVisualization\|viewsheet.darkMode\|viewsheet.density" --include=*.java core/src/main
```
Expected hits only in: `VSDensityDefaults` (the three readers), `LookAndFeelService` (writes them), `PortalController` and `CoreLifecycleService` (send them to the browser). **No `VS*ChromeDefaults`, no `VSTableStructureDefaults`, no `VSChartPaletteDefaults`.**

- [ ] **Step 4: The mark is still unread**

```bash
grep -rn "getVizMark\|VizContext.of(" --include=*.java core/src/main
```
Expected: the P1 sites (the accessor, the constructor stamp, `Viewsheet.getWarningTextAssembly`, the two type-conversion writes) plus `VizContext`'s own `of(VSAssemblyInfo)` → `of(VizMark)` delegation. **Nothing else may call `VizContext.of(...)` until P4.**

- [ ] **Step 5: Full `core` test suite**

```bash
./mvnw test -pl core
```
Expected: pass, or exactly the same pre-existing failures the branch had before this phase. Establish that baseline first by checking out `e670744c1` in a scratch clone if you are unsure — do **not** assume a failure is pre-existing.

- [ ] **Step 6: The behaviour-neutrality claim, checked by hand**

With `viewsheet.modernVisualization` **on** in EM, open a dashboard created under the gate and confirm it renders exactly as it did before this phase: modern card chrome, modern chart gridlines and labels, modern table header, modern slider track. Then turn the gate **off** and confirm it reverts as it did before. Export one dashboard to PDF and one to Excel under each setting. Any visible difference is a defect in this phase, not an expected consequence — P2 changes no rendering.

- [ ] **Step 7: Report** — the four grep outputs, the suite result, and the manual check. No commit; the human partner commits manually.

---

## Done when

- `VizContext` exists with ten passing tests, and `ofGate()` is proven equal to the predicates it replaces across all four gate/dark combinations.
- All eight in-scope `VS*Defaults` classes take a context; `VSChartInteractionDefaults` is untouched.
- The four sub-gate properties are read nowhere in `core/src`, `main` or `test`.
- Exactly one `public static boolean isModern()` remains, in `VSDensityDefaults`.
- `VSCompositeFormat.java` is unmodified, and `resolveSeededCorner`/`PlotDescriptor`'s seed getters read the gate directly with a comment saying why.
- `VizContext.of(...)` is called by nothing outside `VizContext` itself.
- The full `core` suite is no worse than its pre-phase baseline, and the manual check shows no rendering change.

**Not in P2, and do not let it creep in:** any read of the mark, the `applyModernDefaults(fmt, ctx)` enumeration point, Modernize, the `VSObjectModel` field, any CSS, and the deletion of `resolveSeededCorner` or the `PlotDescriptor` seed booleans. Those are P3, P4 and P5.
