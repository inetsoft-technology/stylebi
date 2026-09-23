# Density Padding Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the modern-visualization density control spacing as well as height — a per-tier card inset on charts, the same card inset on tables, and per-tier content padding inside table cells.

**Architecture:** Every value is *seeded* onto the assembly by `seedChromeDefaults(ctx)` rather than resolved at render, so it travels in an exported asset; a density change re-fires the hook through `VizModernizeUtil.reseed`. Table cell padding reuses the pipeline `format.css` table styles already run on (`TableLens.getInsets` → browser cell model and export helpers), with one new fallback: when the CSS filter chain returns nothing, the assembly's own seeded value is used. Vertical cell padding adds to the row height exactly as a CSS padding does, and the stored row-height matrix shrinks to absorb it so rendered heights land back on the numbers that shipped.

**Tech Stack:** Java 21, JUnit 5 + Spring test extension (`@Tag("core")`), Angular 21.2, Vitest via the Angular CLI.

**Spec:** `docs/superpowers/specs/lookfeel/2026-09-23-density-padding-design.md` — committed as `06300c2bc`. Its decisions are numbered D1–D6 and tasks below cite them. Read §3 before Task 1 and §4.3 before Task 5.

Background, only if something below does not make sense: `docs/superpowers/specs/lookfeel/chart-card-roadmap.md` is the track's entry point, and `docs/superpowers/specs/lookfeel/seeded-value-reversibility-decisions.md` is where the seed/Modernize/Revert model was decided.

## Global Constraints

- **Branch:** cut from `origin/epic-74519` @ `e7e83e9c2`. Community-only change — the PR goes in the community repo alone. Nothing in `enterprise/` is touched.
- **Two slices, two pull requests.** Slice A is Tasks 1–8, slice B is Tasks 9–13. Slice B must not be opened before slice A merges: Task 10 rebinds the same three table templates Task 6 touches. Commit per task.
- **Scope is charts plus the three table types only.** `TableVSAssemblyInfo`, `CrosstabVSAssemblyInfo` and `CalcTableVSAssemblyInfo` (all extend `TableDataVSAssemblyInfo`), plus `ChartVSAssemblyInfo`. Selection lists, selection trees, gauges and text are explicitly out — D4 exists to keep the selection family unmoved.
- **The value matrices are fixed by D2 and are not to be re-derived:**

  | | comfortable | compact | dense |
  |---|---|---|---|
  | chart card inset | 16 | 12 | 8 |
  | table card inset | 16 | 12 | 8 |
  | cell padding x | 8 | 6 | 4 |
  | cell padding y | 6 | 4 | 3 |
  | data row height **stored** | 16 | 16 | 14 |
  | header row height **stored** | 18 | 18 | 16 |

- **The D3 invariant: `stored row height + 2 × padding-y` must equal 28/24/20 for data rows and 30/26/22 for header rows.** These are the heights that already shipped. Any commit that breaks the invariant is wrong even if its own tests pass, which is why Task 5 changes the matrix and the additive path together.
- **Legacy (unmarked) values, unchanged:** chart inset `Insets(10,10,10,10)`, table inset `Insets(0,0,0,0)`, cell padding `null`, row heights `AssetUtil.defh`. Every seed writes both branches so Revert restores rather than leaving a stale modern value — the rule in `VSAssemblyInfo.seedChromeDefaults`'s own Javadoc.
- **Horizontal cell padding is never additive.** `getCSSColumnPadding`, `getColumnWidthWithPadding`, `ComposerVSTableService:1051` and `BaseTableCellModel:170` stay CSS-only. D3 gives the reason: four export sites add column padding and `BaseTableService` never does, so an additive horizontal value desyncs live from export.
- **Match the surrounding Java style.** `VSDensityDefaults` uses classic `switch(mode) { case X: ... }` statements over `private static final String` constants, not switch expressions. Follow it.
- **No comments in Angular `.html` template files.** Put the explanation in the `.ts` or `.scss` instead.
- **Commit commands are separate `git add` and `git commit` invocations.** A repo hook rejects `&&`-chained git commands.

**Commands used throughout:**

```bash
# one core test class (from community/)
./mvnw test -pl core -Dtest=VSDensityDefaultsTest

# one core test method
./mvnw test -pl core -Dtest=VSDensityDefaultsTest#chartPaddingMatrix

# one frontend spec (from community/web/)
npx ng test portal --include="**/vs-simple-cell.component.spec.ts"

# one testing-library spec — note the portal:test-tl target; `ng test portal` matches no .tl files
npx ng run portal:test-tl --include="**/table-view-general-pane.component.tl.spec.ts"
```

## Review Focus

Five behaviours the spec implies that no task's happy path would exercise. Two of them have no fixture in this repo and are honestly marked as required manual checks rather than dressed up as tests.

1. **A table whose author typed a row height.** Density substitution is skipped for it, but the padding still adds. `ComposerVSTableService:958/969` subtracts padding on resize and `BaseTableService:492` adds it back, so if those two read different sources the height drifts by `2 × padding-y` on every save-and-reopen. **Automated** — Task 5, Step 9, `authorTypedRowHeightSurvivesASaveAndReopen`, driven off the real `VSTableLens.getRowPadding` so missing either call site fails it.
2. **A table carrying a `format.css` `CSSTableStyle` with its own padding.** Its rendered row heights must be byte-identical to today; a naive `max(css, seeded)` would grow every existing stylesheet customer's rows whenever the seeded value was larger. This is the case `isCSSRowFullyPadded` exists for. **Manual** — Task 5, Step 9b. Needs a loaded `format.css`, which `core/src/test` has no fixture for.
3. **A wrapped-text row.** Multi-line row height comes from `lens.getWrappedHeight`, then padding is added. Padding must be added once for the row, not once per line. **Manual** — Task 5, Step 9b.
4. **A crosstab span cell.** A cell with `rowSpan > 1` takes the sum of the rows it spans; the padding must not be multiplied by the span. **Manual** — Task 5, Step 9b.
5. **Revert on a marked table.** Both new values must go back to legacy — inset to zero, cell padding to null — or a reverted table keeps modern spacing forever. **Automated** — Task 3, `revertClearsASeededCellPadding`, and Task 10, `revertClearsASeededCardInset`.

Items 2–4 are release-gating manual checks, not optional ones. They go in the pull request description with their expected numbers, and item 2 in particular is the regression most likely to reach a customer, because it only shows up on a dashboard that has a table stylesheet.

---

# SLICE A — chart inset per tier, and table cell padding

## Task 1: Padding matrices in VSDensityDefaults

Adds the three new matrices. Purely additive — nothing reads them yet, so this commit changes no behaviour.

**Files:**
- Modify: `core/src/main/java/inetsoft/uql/viewsheet/internal/VSDensityDefaults.java`
- Test: `core/src/test/java/inetsoft/uql/viewsheet/internal/VSDensityDefaultsTest.java`

**Interfaces:**
- Consumes: `VizContext` (`ctx.modern`, `ctx.density`) — exists.
- Produces:
  - `public static Insets chartPadding(VizContext ctx)`
  - `public static Insets tablePadding(VizContext ctx)`
  - `public static Insets cellPadding(VizContext ctx)` — returns `null` when not modern
  - `static Insets chartPaddingForMode(String mode)`
  - `static Insets tablePaddingForMode(String mode)`
  - `static Insets cellPaddingForMode(String mode)`

- [ ] **Step 1: Write the failing tests**

Append to `VSDensityDefaultsTest.java`, before the closing brace:

```java
   @Test
   void chartPaddingMatrix() {
      assertEquals(new Insets(16, 16, 16, 16), VSDensityDefaults.chartPaddingForMode("comfortable"));
      assertEquals(new Insets(12, 12, 12, 12), VSDensityDefaults.chartPaddingForMode("compact"));
      assertEquals(new Insets(8, 8, 8, 8), VSDensityDefaults.chartPaddingForMode("dense"));
   }

   @Test
   void compactChartPaddingHoldsTheShippedFlatInset() {
      // compact is the org default (defaults.properties: viewsheet.density=compact), so holding
      // 12 here is what makes a default-density dashboard reflow nothing
      assertEquals(new Insets(12, 12, 12, 12), VSDensityDefaults.chartPaddingForMode("compact"));
   }

   @Test
   void tablePaddingSharesTheChartMatrix() {
      // one card inset concept, one set of numbers
      assertEquals(VSDensityDefaults.chartPaddingForMode("comfortable"),
                   VSDensityDefaults.tablePaddingForMode("comfortable"));
      assertEquals(VSDensityDefaults.chartPaddingForMode("compact"),
                   VSDensityDefaults.tablePaddingForMode("compact"));
      assertEquals(VSDensityDefaults.chartPaddingForMode("dense"),
                   VSDensityDefaults.tablePaddingForMode("dense"));
   }

   @Test
   void cellPaddingMatrix() {
      assertEquals(new Insets(6, 8, 6, 8), VSDensityDefaults.cellPaddingForMode("comfortable"));
      assertEquals(new Insets(4, 6, 4, 6), VSDensityDefaults.cellPaddingForMode("compact"));
      assertEquals(new Insets(3, 4, 3, 4), VSDensityDefaults.cellPaddingForMode("dense"));
   }

   @Test
   void unrecognizedPaddingModeFallsBackToDense() {
      assertEquals(new Insets(8, 8, 8, 8), VSDensityDefaults.chartPaddingForMode("Comfortable"));
      assertEquals(new Insets(3, 4, 3, 4), VSDensityDefaults.cellPaddingForMode("bogus"));
   }

   @Test
   void paddingAccessorsReturnFreshInstances() {
      // Insets is mutable; a shared constant would let one caller's edit reach every other
      Insets first = VSDensityDefaults.chartPaddingForMode("compact");
      Insets second = VSDensityDefaults.chartPaddingForMode("compact");
      assertNotSame(first, second);

      first.left = 99;
      assertEquals(12, VSDensityDefaults.chartPaddingForMode("compact").left);
   }

   @Test
   void unmarkedContextTakesLegacyPadding() {
      SreeEnv.setProperty("viewsheet.density", "comfortable");
      VizContext legacy = VizContext.of((VizMark) null);

      assertEquals(new Insets(10, 10, 10, 10), VSDensityDefaults.chartPadding(legacy));
      assertEquals(new Insets(0, 0, 0, 0), VSDensityDefaults.tablePadding(legacy));
      assertNull(VSDensityDefaults.cellPadding(legacy));
   }

   @Test
   void markedContextTakesTheDensityPadding() {
      SreeEnv.setProperty("viewsheet.density", "comfortable");
      VizContext modern = VizContext.of(VizMark.MODERN_LIGHT);

      assertEquals(new Insets(16, 16, 16, 16), VSDensityDefaults.chartPadding(modern));
      assertEquals(new Insets(16, 16, 16, 16), VSDensityDefaults.tablePadding(modern));
      assertEquals(new Insets(6, 8, 6, 8), VSDensityDefaults.cellPadding(modern));
   }
```

Add `import java.awt.Insets;` to the test's imports.

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./mvnw test -pl core -Dtest=VSDensityDefaultsTest`
Expected: compilation failure — `cannot find symbol: method chartPaddingForMode(String)`.

- [ ] **Step 3: Implement the matrices**

Add `import java.awt.Insets;` to `VSDensityDefaults.java`, then add these methods after `controlHeightForMode` and before the `private static final String` block:

```java
   /**
    * The chart's card inset for the context's mode, or the legacy inset when not modern. The
    * legacy branch matters as much as the modern one: Revert calls the seed with an unmarked
    * context and needs the legacy value written, not left alone.
    */
   public static Insets chartPadding(VizContext ctx) {
      return ctx.modern ? chartPaddingForMode(ctx.density) : new Insets(10, 10, 10, 10);
   }

   /**
    * A table's card inset for the context's mode. Legacy is zero on all four edges - a table has
    * never drawn a card inset, so that is the value Revert has to restore.
    */
   public static Insets tablePadding(VizContext ctx) {
      return ctx.modern ? tablePaddingForMode(ctx.density) : new Insets(0, 0, 0, 0);
   }

   /**
    * A table cell's content padding, or null when not modern. Null rather than a zero Insets
    * because null is what the cell pipeline already reads as "nothing defined here", falling
    * through to the 1px/2px in vs-table-cell.component.scss and to no inset at all in export.
    */
   public static Insets cellPadding(VizContext ctx) {
      return ctx.modern ? cellPaddingForMode(ctx.density) : null;
   }

   /**
    * Card inset for a density mode, uniform on all four edges. Unrecognized modes fall back to
    * dense. Compact holds the value the flat modern inset shipped at, so the org default mode
    * reflows nothing. A fresh Insets every call: the type is mutable.
    */
   static Insets chartPaddingForMode(String mode) {
      int inset;

      switch(mode) {
      case COMFORTABLE:
         inset = 16;
         break;
      case COMPACT:
         inset = 12;
         break;
      default:
         inset = 8;
      }

      return new Insets(inset, inset, inset, inset);
   }

   /**
    * A table's card inset for a density mode. Deliberately the chart's matrix rather than a
    * second one: a table card and a chart card beside it are the same object with different
    * contents, and two matrices would drift.
    */
   static Insets tablePaddingForMode(String mode) {
      return chartPaddingForMode(mode);
   }

   /**
    * Cell content padding for a density mode. The two axes carry different values because they
    * do different work - vertical sets the scan rhythm, horizontal the column rhythm.
    * Unrecognized modes fall back to dense. A fresh Insets every call.
    */
   static Insets cellPaddingForMode(String mode) {
      switch(mode) {
      case COMFORTABLE:
         return new Insets(6, 8, 6, 8);
      case COMPACT:
         return new Insets(4, 6, 4, 6);
      default:
         return new Insets(3, 4, 3, 4);
      }
   }
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./mvnw test -pl core -Dtest=VSDensityDefaultsTest`
Expected: PASS, including the eight pre-existing tests.

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/inetsoft/uql/viewsheet/internal/VSDensityDefaults.java
git add core/src/test/java/inetsoft/uql/viewsheet/internal/VSDensityDefaultsTest.java
```

```bash
git commit -m "Add the density padding matrices"
```

---

## Task 2: The chart's card inset follows density

Item 1 of the spec, complete in one task. `VSObjectChromeDefaults.modernChartPadding()` takes a context and delegates to Task 1's matrix.

**Files:**
- Modify: `core/src/main/java/inetsoft/uql/viewsheet/internal/VSObjectChromeDefaults.java`
- Modify: `core/src/main/java/inetsoft/uql/viewsheet/internal/ChartVSAssemblyInfo.java:122` and `resetCardInset`
- Test: `core/src/test/java/inetsoft/uql/viewsheet/internal/ChartCardInsetDensityTest.java` (create)

**Interfaces:**
- Consumes: `VSDensityDefaults.chartPadding(VizContext)` from Task 1.
- Produces: `VSObjectChromeDefaults.modernChartPadding(VizContext ctx)` — replaces the no-arg form. `legacyChartPadding()` is unchanged and still exists.

- [ ] **Step 1: Write the failing test**

Create `core/src/test/java/inetsoft/uql/viewsheet/internal/ChartCardInsetDensityTest.java`:

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
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.Insets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The chart's card inset is seeded at creation and re-seeded on a density change, so it must
 * follow the density tier rather than the flat 12px it shipped at. An author-set inset
 * (userPadding) is never substituted, and an unmarked chart keeps the legacy 10px.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class ChartCardInsetDensityTest {
   @AfterEach
   void reset() {
      SreeEnv.setProperty("viewsheet.density", null);
   }

   @Test
   void comfortableSeedsTheWidestInset() {
      SreeEnv.setProperty("viewsheet.density", "comfortable");
      ChartVSAssemblyInfo info = new ChartVSAssemblyInfo();

      info.seedChromeDefaults(VizContext.of(VizMark.MODERN_LIGHT));

      assertEquals(new Insets(16, 16, 16, 16), info.getPadding());
   }

   @Test
   void compactSeedsTheShippedInset() {
      SreeEnv.setProperty("viewsheet.density", "compact");
      ChartVSAssemblyInfo info = new ChartVSAssemblyInfo();

      info.seedChromeDefaults(VizContext.of(VizMark.MODERN_LIGHT));

      assertEquals(new Insets(12, 12, 12, 12), info.getPadding());
   }

   @Test
   void denseSeedsTheTightestInset() {
      SreeEnv.setProperty("viewsheet.density", "dense");
      ChartVSAssemblyInfo info = new ChartVSAssemblyInfo();

      info.seedChromeDefaults(VizContext.of(VizMark.MODERN_LIGHT));

      assertEquals(new Insets(8, 8, 8, 8), info.getPadding());
   }

   @Test
   void unmarkedChartKeepsTheLegacyInset() {
      SreeEnv.setProperty("viewsheet.density", "comfortable");
      ChartVSAssemblyInfo info = new ChartVSAssemblyInfo();

      info.seedChromeDefaults(VizContext.of((VizMark) null));

      assertEquals(new Insets(10, 10, 10, 10), info.getPadding());
   }

   @Test
   void authorSetInsetIsNeverSubstituted() {
      SreeEnv.setProperty("viewsheet.density", "comfortable");
      ChartVSAssemblyInfo info = new ChartVSAssemblyInfo();
      info.setUserPadding(true);
      info.setPadding(new Insets(4, 4, 4, 4));

      info.seedChromeDefaults(VizContext.of(VizMark.MODERN_LIGHT));

      assertEquals(new Insets(4, 4, 4, 4), info.getPadding());
   }

   @Test
   void resetCardInsetFollowsTheCurrentDensity() {
      // the padding pane's follow-the-default checkbox calls this and nothing else
      SreeEnv.setProperty("viewsheet.density", "dense");
      ChartVSAssemblyInfo info = new ChartVSAssemblyInfo();
      info.setPadding(new Insets(4, 4, 4, 4));

      info.resetCardInset(VizContext.of(VizMark.MODERN_LIGHT));

      assertEquals(new Insets(8, 8, 8, 8), info.getPadding());
   }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./mvnw test -pl core -Dtest=ChartCardInsetDensityTest`
Expected: FAIL — `comfortableSeedsTheWidestInset` and `denseSeedsTheTightestInset` get `Insets(12,12,12,12)`, because the inset is still flat.

- [ ] **Step 3: Give modernChartPadding a context**

In `VSObjectChromeDefaults.java`, replace the `modernChartPadding()` method and delete the now-unused `MODERN_CARD_INSET` constant:

```java
   /**
    * The modern card inset, seeded at creation and re-seeded on a density change. Delegates to
    * the density matrix rather than holding a constant: the inset is a density-derived size, and
    * VSDensityDefaults is where those live. A fresh object every call, as the type is mutable.
    */
   public static Insets modernChartPadding(VizContext ctx) {
      return VSDensityDefaults.chartPaddingForMode(ctx.density);
   }
```

Delete this line from the constants block:

```java
   // modern card inset, px; = --inet-space-5. One value governs all four edges: the title lane, the
   // axis title and the legend column add no edge padding of their own.
   private static final int MODERN_CARD_INSET = 12;
```

- [ ] **Step 4: Pass the context at both call sites**

In `ChartVSAssemblyInfo.java`, in `seedChromeDefaults`, change:

```java
      if(!isUserPadding() && !isCssPaddingDefined()) {
         setPadding(ctx.modern ? VSObjectChromeDefaults.modernChartPadding()
                       : VSObjectChromeDefaults.legacyChartPadding());
      }
```

to:

```java
      if(!isUserPadding() && !isCssPaddingDefined()) {
         setPadding(ctx.modern ? VSObjectChromeDefaults.modernChartPadding(ctx)
                       : VSObjectChromeDefaults.legacyChartPadding());
      }
```

And in `resetCardInset`, change the final statement the same way:

```java
      setPadding(ctx.modern ? VSObjectChromeDefaults.modernChartPadding(ctx)
                    : VSObjectChromeDefaults.legacyChartPadding());
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `./mvnw test -pl core -Dtest=ChartCardInsetDensityTest`
Expected: PASS, all six.

- [ ] **Step 6: Check nothing else called the no-arg form**

Run: `grep -rn "modernChartPadding()" --include=*.java core/src`
Expected: no output. If there are hits, give each one the `VizContext` already in scope.

- [ ] **Step 7: Run the neighbouring suites**

Run: `./mvnw test -pl core -Dtest=VSDensityDefaultsTest,ChromeSeedClassificationTest,VizContextTest`
Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add core/src/main/java/inetsoft/uql/viewsheet/internal/VSObjectChromeDefaults.java
git add core/src/main/java/inetsoft/uql/viewsheet/internal/ChartVSAssemblyInfo.java
git add core/src/test/java/inetsoft/uql/viewsheet/internal/ChartCardInsetDensityTest.java
```

```bash
git commit -m "Make the chart's card inset follow the density tier"
```

---

## Task 3: Cell padding storage on TableDataVSAssemblyInfo

Adds the field, its serialization and its seed. Nothing reads it yet, so rendering is unchanged — Task 5 wires it up. Owns Review Focus item 5.

**Files:**
- Modify: `core/src/main/java/inetsoft/uql/viewsheet/internal/TableDataVSAssemblyInfo.java`
- Test: `core/src/test/java/inetsoft/uql/viewsheet/internal/TableCellPaddingSeedTest.java` (create)

**Interfaces:**
- Consumes: `VSDensityDefaults.cellPadding(VizContext)` from Task 1.
- Produces, on `TableDataVSAssemblyInfo`:
  - `public Insets getCellPadding()` — the resolved value across the tiers, or `null`
  - `public void setCellPadding(Insets cellPadding, CompositeValue.Type type)`
  - `public boolean isUserCellPadding()` — delegates to `hasUserValue()`
  - `public void resetUserCellPadding()` — delegates to `resetUserValue()`

- [ ] **Step 1: Write the failing test**

Create `core/src/test/java/inetsoft/uql/viewsheet/internal/TableCellPaddingSeedTest.java`:

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
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.CompositeValue;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.Insets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A marked table seeds a density-derived cell padding; an unmarked one has none, which is what
 * Revert has to restore. Authorship rides on CompositeValue's USER tier rather than a boolean,
 * so an author value survives a reseed and clearing it hands the value back to the density.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class TableCellPaddingSeedTest {
   @AfterEach
   void reset() {
      SreeEnv.setProperty("viewsheet.density", null);
   }

   @Test
   void markedTableSeedsTheDensityCellPadding() {
      SreeEnv.setProperty("viewsheet.density", "comfortable");
      TableVSAssemblyInfo info = new TableVSAssemblyInfo();

      info.seedChromeDefaults(VizContext.of(VizMark.MODERN_LIGHT));

      assertEquals(new Insets(6, 8, 6, 8), info.getCellPadding());
   }

   @Test
   void eachTierSeedsItsOwnValue() {
      SreeEnv.setProperty("viewsheet.density", "compact");
      TableVSAssemblyInfo compact = new TableVSAssemblyInfo();
      compact.seedChromeDefaults(VizContext.of(VizMark.MODERN_LIGHT));
      assertEquals(new Insets(4, 6, 4, 6), compact.getCellPadding());

      SreeEnv.setProperty("viewsheet.density", "dense");
      TableVSAssemblyInfo dense = new TableVSAssemblyInfo();
      dense.seedChromeDefaults(VizContext.of(VizMark.MODERN_LIGHT));
      assertEquals(new Insets(3, 4, 3, 4), dense.getCellPadding());
   }

   @Test
   void crosstabAndCalcTableSeedTheSameValue() {
      SreeEnv.setProperty("viewsheet.density", "compact");
      CrosstabVSAssemblyInfo crosstab = new CrosstabVSAssemblyInfo();
      CalcTableVSAssemblyInfo calc = new CalcTableVSAssemblyInfo();

      crosstab.seedChromeDefaults(VizContext.of(VizMark.MODERN_LIGHT));
      calc.seedChromeDefaults(VizContext.of(VizMark.MODERN_LIGHT));

      assertEquals(new Insets(4, 6, 4, 6), crosstab.getCellPadding());
      assertEquals(new Insets(4, 6, 4, 6), calc.getCellPadding());
   }

   @Test
   void unmarkedTableHasNoCellPadding() {
      SreeEnv.setProperty("viewsheet.density", "comfortable");
      TableVSAssemblyInfo info = new TableVSAssemblyInfo();

      info.seedChromeDefaults(VizContext.of((VizMark) null));

      assertNull(info.getCellPadding());
   }

   @Test
   void revertClearsASeededCellPadding() {
      // Review Focus 5: Revert calls the seed with an unmarked context and needs the legacy
      // absence written, not left alone - otherwise a reverted table keeps modern spacing
      SreeEnv.setProperty("viewsheet.density", "comfortable");
      TableVSAssemblyInfo info = new TableVSAssemblyInfo();
      info.seedChromeDefaults(VizContext.of(VizMark.MODERN_LIGHT));
      assertNotNull(info.getCellPadding(), "seeded before revert");

      info.seedChromeDefaults(VizContext.of((VizMark) null));

      assertNull(info.getCellPadding(), "cleared by revert");
   }

   @Test
   void authorValueSurvivesAReseed() {
      SreeEnv.setProperty("viewsheet.density", "comfortable");
      TableVSAssemblyInfo info = new TableVSAssemblyInfo();
      info.setCellPadding(new Insets(1, 2, 1, 2), CompositeValue.Type.USER);

      info.seedChromeDefaults(VizContext.of(VizMark.MODERN_LIGHT));

      assertTrue(info.isUserCellPadding());
      assertEquals(new Insets(1, 2, 1, 2), info.getCellPadding());
   }

   @Test
   void clearingTheAuthorValueHandsItBackToDensity() {
      SreeEnv.setProperty("viewsheet.density", "comfortable");
      TableVSAssemblyInfo info = new TableVSAssemblyInfo();
      info.seedChromeDefaults(VizContext.of(VizMark.MODERN_LIGHT));
      info.setCellPadding(new Insets(1, 2, 1, 2), CompositeValue.Type.USER);

      info.resetUserCellPadding();

      assertFalse(info.isUserCellPadding());
      assertEquals(new Insets(6, 8, 6, 8), info.getCellPadding());
   }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./mvnw test -pl core -Dtest=TableCellPaddingSeedTest`
Expected: compilation failure — `cannot find symbol: method getCellPadding()`.

- [ ] **Step 3: Add the field and its accessors**

In `TableDataVSAssemblyInfo.java`, add the imports it needs (`inetsoft.uql.CompositeValue`, `java.awt.Insets`) if absent, add the field next to the other private fields at the bottom of the class:

```java
   // the cell's content padding. A CompositeValue rather than a plain Insets so authorship needs
   // no companion boolean: the USER tier IS the author's opinion. Deliberately no CSS tier - a
   // table's CSS cell padding arrives through CSSTableStyle, and a second CSS source here would
   // give two mechanisms a claim on one value
   private CompositeValue<Insets> cellPadding = new CompositeValue<>(Insets.class, null);
```

and the accessors, next to the other public accessors:

```java
   /**
    * The cell's content padding, resolved across the tiers, or null when nothing defines one.
    * Null rather than a zero Insets: null is what the cell pipeline reads as "nothing here".
    */
   public Insets getCellPadding() {
      return cellPadding.get();
   }

   /**
    * Set the cell's content padding at one tier. DEFAULT is the density seed, USER the author.
    */
   public void setCellPadding(Insets cellPadding, CompositeValue.Type type) {
      this.cellPadding.setValue(cellPadding, type);
   }

   /**
    * Whether the author set the cell padding. Surfaced in the property dialog as the cell padding
    * pane's follow-the-default checkbox, inverted.
    */
   public boolean isUserCellPadding() {
      return cellPadding.hasUserValue();
   }

   /**
    * Drop the author's cell padding and let the density decide again. What the follow-the-default
    * checkbox calls when it is checked - writing the density value into the USER tier instead
    * would pin the current tier.
    */
   public void resetUserCellPadding() {
      cellPadding.resetUserValue();
   }
```

- [ ] **Step 4: Serialize it**

In `writeAttributes`, after the `userDataRowHeight` line:

```java
      writer.print(" cellPadding=\"" + cellPadding + "\"");
```

In `parseAttributes`, at the end of the method:

```java
      cellPadding.parse(Tool.getAttribute(elem, "cellPadding"));
```

In `copyViewInfo`, inside the `TableDataVSAssemblyInfo` branch alongside the other field copies:

```java
      if(!Tool.equals(cellPadding, info.cellPadding)) {
         cellPadding = (CompositeValue<Insets>) info.cellPadding.clone();
         result = true;
      }
```

If `copyViewInfo`'s parameter is not already narrowed to `TableDataVSAssemblyInfo`, add the copy inside the existing `if(info instanceof TableDataVSAssemblyInfo)` block, binding a local of that type first — follow whatever shape the neighbouring copies in that method already use.

- [ ] **Step 5: Seed it**

In `seedChromeDefaults`, after the title-lane block and before the closing brace:

```java
      // the cell's content padding. Seeded rather than resolved at render so it travels in an
      // exported asset. Both branches write: Revert calls this with an unmarked context and
      // needs the legacy absence restored, not the modern value left in place
      if(!isUserCellPadding()) {
         setCellPadding(VSDensityDefaults.cellPadding(ctx), CompositeValue.Type.DEFAULT);
      }
```

- [ ] **Step 6: Run the test to verify it passes**

Run: `./mvnw test -pl core -Dtest=TableCellPaddingSeedTest`
Expected: PASS, all seven.

- [ ] **Step 7: Commit**

```bash
git add core/src/main/java/inetsoft/uql/viewsheet/internal/TableDataVSAssemblyInfo.java
git add core/src/test/java/inetsoft/uql/viewsheet/internal/TableCellPaddingSeedTest.java
```

```bash
git commit -m "Store and seed a table's cell padding"
```

---

## Task 4: Cell padding round-trips through XML

A stored value that does not survive save-and-reopen is worse than no value, and `CompositeValue.parse` has a `readAsDefault` argument whose behaviour decides whether a seeded value comes back as DEFAULT or USER. Pin it before anything reads the field.

**Files:**
- Test: `core/src/test/java/inetsoft/uql/viewsheet/internal/TableCellPaddingSeedTest.java` (modify)
- Modify, only if the test fails: `core/src/main/java/inetsoft/uql/viewsheet/internal/TableDataVSAssemblyInfo.java`

**Interfaces:**
- Consumes: `getCellPadding`, `setCellPadding`, `isUserCellPadding` from Task 3.
- Produces: nothing new.

- [ ] **Step 1: Write the failing test**

Append to `TableCellPaddingSeedTest.java`:

```java
   @Test
   void seededCellPaddingRoundTripsAsADefault() throws Exception {
      SreeEnv.setProperty("viewsheet.density", "comfortable");
      TableVSAssemblyInfo written = new TableVSAssemblyInfo();
      written.seedChromeDefaults(VizContext.of(VizMark.MODERN_LIGHT));

      TableVSAssemblyInfo read = reparse(written);

      assertEquals(new Insets(6, 8, 6, 8), read.getCellPadding());
      assertFalse(read.isUserCellPadding(), "a seeded value must not come back as the author's");
   }

   @Test
   void authorCellPaddingRoundTripsAsTheAuthors() throws Exception {
      SreeEnv.setProperty("viewsheet.density", "comfortable");
      TableVSAssemblyInfo written = new TableVSAssemblyInfo();
      written.seedChromeDefaults(VizContext.of(VizMark.MODERN_LIGHT));
      written.setCellPadding(new Insets(1, 2, 1, 2), CompositeValue.Type.USER);

      TableVSAssemblyInfo read = reparse(written);

      assertEquals(new Insets(1, 2, 1, 2), read.getCellPadding());
      assertTrue(read.isUserCellPadding(), "the author's value must survive as the author's");
   }

   @Test
   void anAssetSavedBeforeTheFieldExistedParsesWithNoCellPadding() throws Exception {
      // the attribute is simply absent; that must read as "nothing defined", not as a crash
      TableVSAssemblyInfo read = new TableVSAssemblyInfo();
      Element elem = parseElement("<assembly class=\"TableVSAssemblyInfo\"/>");
      read.parseAttributes(elem);

      assertNull(read.getCellPadding());
      assertFalse(read.isUserCellPadding());
   }

   private TableVSAssemblyInfo reparse(TableVSAssemblyInfo written) throws Exception {
      StringWriter buffer = new StringWriter();
      PrintWriter writer = new PrintWriter(buffer);
      writer.print("<assembly");
      written.writeAttributes(writer);
      writer.print("/>");
      writer.flush();

      TableVSAssemblyInfo read = new TableVSAssemblyInfo();
      read.parseAttributes(parseElement(buffer.toString()));
      return read;
   }

   private Element parseElement(String xml) throws Exception {
      return DocumentBuilderFactory.newInstance().newDocumentBuilder()
         .parse(new InputSource(new StringReader(xml))).getDocumentElement();
   }
```

Add these imports to the test:

```java
import org.w3c.dom.Element;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
```

`writeAttributes` and `parseAttributes` are `protected`, and the test is in the same package, so no visibility change is needed.

- [ ] **Step 2: Run the tests**

Run: `./mvnw test -pl core -Dtest=TableCellPaddingSeedTest`
Expected: either PASS (the round-trip already works — go to Step 4) or FAIL on `seededCellPaddingRoundTripsAsADefault` with `isUserCellPadding()` true.

- [ ] **Step 3: If it failed, read the value back as a default**

`CompositeValue.parse(String)` delegates to `parse(str, false)`, which restores into the USER tier. Change the `parseAttributes` line added in Task 3 to:

```java
      // readAsDefault: a value written from the DEFAULT tier must come back to it, or every
      // reopened table would look author-set and stop following its density
      cellPadding.parse(Tool.getAttribute(elem, "cellPadding"), true);
```

Then re-read `CompositeValue.parse(String, boolean)` and confirm it preserves a genuine USER tier across the round trip. If the serialized form does not distinguish the tiers at all, mirror whatever `SelectionBaseVSAssemblyInfo` does for its own `cellPadding` — it has the same problem and shipped a solution — and adjust these tests to match the shape that class proves.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./mvnw test -pl core -Dtest=TableCellPaddingSeedTest`
Expected: PASS, all ten.

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/inetsoft/uql/viewsheet/internal/TableDataVSAssemblyInfo.java
git add core/src/test/java/inetsoft/uql/viewsheet/internal/TableCellPaddingSeedTest.java
```

```bash
git commit -m "Round-trip a table's cell padding through XML at the right tier"
```

---

## Task 5: Resolve cell padding, and rebalance the row matrix

The load-bearing task. Wires the seeded padding into the lens, makes vertical padding additive, and shrinks the stored row matrix so rendered heights are unchanged — **all in one commit**, because any ordering of those three leaves an intermediate state with wrong row heights. Owns Review Focus items 1–4.

Read spec §4.3 before starting.

**Files:**
- Modify: `core/src/main/java/inetsoft/report/composition/VSTableLens.java`
- Modify: `core/src/main/java/inetsoft/uql/viewsheet/internal/VSDensityDefaults.java`
- Modify: `core/src/main/java/inetsoft/web/viewsheet/model/table/BaseTableCellModel.java:136/140`, `:169`, `:315/319`
- Modify: `core/src/main/java/inetsoft/web/viewsheet/controller/table/BaseTableService.java:492/498`
- Modify: `core/src/main/java/inetsoft/report/io/viewsheet/VSTableHelper.java:172`, `:209`
- Modify: `core/src/main/java/inetsoft/report/io/viewsheet/VSCrosstabHelper.java:270`
- Modify: `core/src/main/java/inetsoft/report/io/viewsheet/html/HTMLTableHelper.java:288`
- Modify: `core/src/main/java/inetsoft/web/composer/vs/objects/controller/ComposerVSTableService.java:958/969`
- Test: `core/src/test/java/inetsoft/uql/viewsheet/internal/VSDensityDefaultsTest.java` (modify)
- Test: `core/src/test/java/inetsoft/uql/viewsheet/internal/DensityRowHeightInvariantTest.java` (create)

**Interfaces:**
- Consumes: `TableDataVSAssemblyInfo.getCellPadding()` from Task 3; `VSDensityDefaults.cellPaddingForMode` from Task 1.
- Produces:
  - `VSTableLens.getCellInsets(int r, int c, TableDataVSAssemblyInfo info)` → `Insets` or `null`
  - `VSTableLens.getRowPadding(int row, TableDataVSAssemblyInfo info)` → `int`
  - `VSDensityDefaults.selectionCellHeightForMode(String mode)` → `int`
  - `rowHeightForMode` now returns 16/16/14 and `headerRowHeightForMode` 18/18/16

- [ ] **Step 1: Write the invariant test**

Create `core/src/test/java/inetsoft/uql/viewsheet/internal/DensityRowHeightInvariantTest.java`:

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

import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.asset.internal.AssetUtil;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.Insets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The stored row-height matrix absorbs the cell padding so that what a reader sees is unchanged
 * from what shipped: 28/24/20 for data rows and 30/26/22 for headers. The stored numbers are an
 * implementation detail of that sum and are not meaningful on their own - this test is the
 * contract, and VSDensityDefaultsTest's matrix assertions are its arithmetic.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class DensityRowHeightInvariantTest {
   @Test
   void renderedDataRowHeightsAreUnchanged() {
      assertRenderedDataRow("comfortable", 28);
      assertRenderedDataRow("compact", 24);
      assertRenderedDataRow("dense", 20);
   }

   @Test
   void renderedHeaderRowHeightsAreUnchanged() {
      assertRenderedHeaderRow("comfortable", 30);
      assertRenderedHeaderRow("compact", 26);
      assertRenderedHeaderRow("dense", 22);
   }

   @Test
   void denseRenderedRowStillEqualsTheLegacyDefault() {
      // the anchoring promise: at dense, a marked table's row is the height it always was
      assertRenderedDataRow("dense", AssetUtil.defh);
   }

   @Test
   void selectionCellHeightIsNotDraggedDownByTheRebalance() {
      // the selection family is out of scope and has no additive padding path, so its cell
      // height keeps the pre-rebalance matrix rather than following rowHeightForMode
      assertEquals(28, VSDensityDefaults.selectionCellHeightForMode("comfortable"));
      assertEquals(24, VSDensityDefaults.selectionCellHeightForMode("compact"));
      assertEquals(20, VSDensityDefaults.selectionCellHeightForMode("dense"));
   }

   @Test
   void selectionCellHeightNoLongerTracksTheTableRow() {
      // if these ever converge again, someone has re-merged the two matrices D4 split
      assertNotEquals(VSDensityDefaults.rowHeightForMode("comfortable"),
                      VSDensityDefaults.selectionCellHeightForMode("comfortable"));
   }

   private void assertRenderedDataRow(String mode, int expected) {
      Insets pad = VSDensityDefaults.cellPaddingForMode(mode);
      assertEquals(expected, VSDensityDefaults.rowHeightForMode(mode) + pad.top + pad.bottom,
                   mode + " data row");
   }

   private void assertRenderedHeaderRow(String mode, int expected) {
      Insets pad = VSDensityDefaults.cellPaddingForMode(mode);
      assertEquals(expected, VSDensityDefaults.headerRowHeightForMode(mode) + pad.top + pad.bottom,
                   mode + " header row");
   }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./mvnw test -pl core -Dtest=DensityRowHeightInvariantTest`
Expected: FAIL — `comfortable data row ==> expected: <28> but was: <40>`, because the matrix has not been rebalanced yet, plus a compilation error on `selectionCellHeightForMode`.

- [ ] **Step 3: Rebalance the matrix and split the selection height**

In `VSDensityDefaults.java`, replace `rowHeightForMode` and `headerRowHeightForMode`, and change `cellHeight` to use a new matrix:

```java
   /**
    * Default selection-list cell height. Selection cells are a data surface, but not a table one:
    * they have no additive cell padding, so they keep the matrix table rows used before the
    * padding was introduced rather than following rowHeightForMode down.
    */
   public static int cellHeight(VizContext ctx) {
      return ctx.modern ? selectionCellHeightForMode(ctx.density) : AssetUtil.defh;
   }

   /**
    * STORED data-row height for a density mode - not the height a reader sees. The cell padding
    * is added on top (VSTableLens.getRowPadding), so stored + 2 * padding-y is what renders, and
    * that sum is the contract: 28 / 24 / 20. Unrecognized modes fall back to dense.
    */
   static int rowHeightForMode(String mode) {
      switch(mode) {
      case COMFORTABLE:
         return 16;
      case COMPACT:
         return 16;
      default:
         return 14;
      }
   }

   /**
    * STORED header-row height for a density mode, on the same terms as rowHeightForMode: the
    * rendered sum is 30 / 26 / 22.
    */
   static int headerRowHeightForMode(String mode) {
      switch(mode) {
      case COMFORTABLE:
         return 18;
      case COMPACT:
         return 18;
      default:
         return 16;
      }
   }

   /**
    * Selection-list cell height for a density mode. Deliberately a separate matrix from
    * rowHeightForMode, which it used to share: the two were only ever equal because no padding
    * sat between a table's stored row height and its rendered one. Do not re-merge them.
    */
   static int selectionCellHeightForMode(String mode) {
      switch(mode) {
      case COMFORTABLE:
         return 28;
      case COMPACT:
         return 24;
      default:
         return 20;
      }
   }
```

Comfortable and compact returning the same stored value is correct, not a typo — the design spec keeps font size at 13px for both tiers, so they differ in whitespace, not text box.

- [ ] **Step 4: Update the old matrix assertions**

In `VSDensityDefaultsTest.java`, replace the three tests that assert the old numbers:

```java
   @Test
   void storedDataRowHeightMatrix() {
      // STORED, not rendered - DensityRowHeightInvariantTest owns the rendered contract
      assertEquals(16, VSDensityDefaults.rowHeightForMode("comfortable"));
      assertEquals(16, VSDensityDefaults.rowHeightForMode("compact"));
      assertEquals(14, VSDensityDefaults.rowHeightForMode("dense"));
   }

   @Test
   void storedHeaderRowHeightMatrix() {
      assertEquals(18, VSDensityDefaults.headerRowHeightForMode("comfortable"));
      assertEquals(18, VSDensityDefaults.headerRowHeightForMode("compact"));
      assertEquals(16, VSDensityDefaults.headerRowHeightForMode("dense"));
   }

   @Test
   void unrecognizedModeFallsBackToDense() {
      // values are case-sensitive lowercase; anything else falls back to dense
      assertEquals(14, VSDensityDefaults.rowHeightForMode("Comfortable"));
      assertEquals(16, VSDensityDefaults.headerRowHeightForMode("bogus"));
   }
```

Delete `denseModeMatchesLegacyDataRowHeight`, `compactMode` and `comfortableMode` — the promise they were making is now `DensityRowHeightInvariantTest.denseRenderedRowStillEqualsTheLegacyDefault`, which states it in rendered terms where it is actually true.

- [ ] **Step 5: Run both suites**

Run: `./mvnw test -pl core -Dtest=DensityRowHeightInvariantTest,VSDensityDefaultsTest`
Expected: PASS.

- [ ] **Step 6: Add the two resolvers to VSTableLens**

In `VSTableLens.java`, add next to `getCSSRowPadding`. Keep `getCSSRowPadding` exactly as it is — it stays the CSS-only half, and its cache stays valid:

```java
   /**
    * The cell's effective content inset: the stylesheet's when a CSSTableStyle defines one,
    * otherwise the assembly's own seeded or author value. One rule, and the only source the
    * browser cell model and the three pixel exporters read - so live and export cannot disagree.
    */
   public Insets getCellInsets(int r, int c, TableDataVSAssemblyInfo info) {
      Insets css = getInsets(r, c);

      if(css != null) {
         return css;
      }

      return info == null ? null : info.getCellPadding();
   }

   /**
    * How much taller a row is for its padding: the max effective inset height across the row,
    * which is the same rule getCellInsets applies per cell.
    *
    * Not max(cssRowPadding, seeded). Where a stylesheet covers every column nothing falls back,
    * so the row grows by the CSS amount alone - a blunt max would instead grow an existing
    * format.css customer's rows whenever the seeded value happened to be the larger one.
    */
   public int getRowPadding(int row, TableDataVSAssemblyInfo info) {
      int css = getCSSRowPadding(row);

      if(isCSSRowFullyPadded(row)) {
         return css;
      }

      Insets cell = info == null ? null : info.getCellPadding();
      return cell == null ? css : Math.max(css, cell.top + cell.bottom);
   }

   /**
    * Whether a CSSTableStyle defines an inset for every column of this row, so that no cell in it
    * falls back to the assembly's own padding. Mirrors getCSSRowPadding's traversal and caching.
    */
   private boolean isCSSRowFullyPadded(int row) {
      if(row > getHeaderRowCount()) {
         row = getHeaderRowCount();
      }

      CSSTableStyle cssTableStyle = (CSSTableStyle) Util.getNestedTable(this, CSSTableStyle.class);

      if(cssTableStyle == null) {
         return false;
      }

      int baseRow = TableTool.getBaseRowIndex(this, cssTableStyle, row);

      if(baseRow < 0) {
         return false;
      }

      Boolean cached = fullRowPadding.get(row);

      if(cached != null) {
         return cached;
      }

      boolean full = cssTableStyle.getColCount() > 0;

      for(int c = 0; full && c < cssTableStyle.getColCount(); c++) {
         full = cssTableStyle.getInsets(baseRow, c) != null;
      }

      fullRowPadding.put(row, full);
      return full;
   }
```

Declare the cache beside the existing `maxRowPadding` field, using whatever map type that one uses:

```java
   private final Map<Integer, Boolean> fullRowPadding = new HashMap<>();
```

- [ ] **Step 7: Make the row-height path additive from the new source**

Five call sites move from `getCSSRowPadding(row)` to `getRowPadding(row, info)`. Each already has the `TableDataVSAssemblyInfo` in scope; if a method does not, thread it from its caller rather than reaching for a field.

- `VSTableLens.getRowHeightWithPadding(double height, int row)` — add a `TableDataVSAssemblyInfo info` parameter and return `height + getRowPadding(row, info)`. Update its callers in `AbstractVSExporter:2586/2591/2610`, `ExcelVSUtil:364`, `HTMLCrosstabHelper:122/133`, `HTMLTableHelper:117/123`, `VSTableDataHelper:177/615/620/955`; each already holds the info.
- `BaseTableService:492` — `dataRowHeight += lens.getRowPadding(lens.getHeaderRowCount(), tinfo);`
- `BaseTableService:498` — `headerRowHeights[i] += lens.getRowPadding(i, tinfo);`
- `VSTableHelper:209` — `displayRowHeight += lens.getRowPadding(lens.getHeaderRowCount(), info);`
- `ComposerVSTableService:958` — `height = Math.max(0, height - lens.getRowPadding(i + event.row(), info));`
- `ComposerVSTableService:969` — `height = Math.max(0, height - lens.getRowPadding(lens.getHeaderRowCount(), info));`
- `BaseTableCellModel:169` — `int rowPadding = lens.getRowPadding(row, (TableDataVSAssemblyInfo) assemblyInfo);`, guarded by an `instanceof` check since `assemblyInfo` is the wider type.

Leave `getCSSColumnPadding`, `getColumnWidthWithPadding`, `ComposerVSTableService:1051` and `BaseTableCellModel:170` exactly as they are.

- [ ] **Step 8: Make the five content-inset sites read the effective value**

- `BaseTableCellModel:136` and `:140` — replace both `lens.getInsets(row, col)` with `lens.getCellInsets(row, col, tinfo)`, where `tinfo` is `assemblyInfo` narrowed with an `instanceof` check (pass `null` when it is not a table info, which preserves today's behaviour for every other caller).
- `BaseTableCellModel:315` and `:319` — same change in the second overload.
- `VSTableHelper:172` — `Insets padding = lens.getCellInsets(irow, icol, info);`
- `VSCrosstabHelper:270` — `Insets padding = lens.getCellInsets(irow, icol, info);`
- `HTMLTableHelper:288` — `table.append(vHelper.getPaddingString(lens.getCellInsets(r, c, info)));`

- [ ] **Step 9: Write the resolver tests against a real lens**

Create `core/src/test/java/inetsoft/report/composition/TableCellPaddingResolutionTest.java`. Build the lens the way `VSTableLensTest:40` does — `new VSTableLens(XTableUtil.getDefaultTableLens())` — which puts a `DefaultTableLens` in the chain with no `CSSTableStyle`, so `getInsets` returns null and the fallback is what is under test.

```java
package inetsoft.report.composition;

import inetsoft.sree.SreeEnv;
import inetsoft.test.*;
import inetsoft.uql.viewsheet.internal.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.Insets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The lens resolves a cell's content inset and a row's growth from one rule. With no
 * CSSTableStyle in the chain - the common case, and the one this feature creates - both come
 * from the assembly's own seeded padding.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class TableCellPaddingResolutionTest {
   @AfterEach
   void reset() {
      SreeEnv.setProperty("viewsheet.density", null);
   }

   @Test
   void cellInsetsFallBackToTheSeededPadding() {
      VSTableLens lens = new VSTableLens(XTableUtil.getDefaultTableLens());

      assertNull(lens.getInsets(0, 0), "no CSSTableStyle, so nothing from the chain");
      assertEquals(new Insets(4, 6, 4, 6), lens.getCellInsets(0, 0, markedTable("compact")));
   }

   @Test
   void cellInsetsAreNullForAnUnmarkedTable() {
      VSTableLens lens = new VSTableLens(XTableUtil.getDefaultTableLens());

      assertNull(lens.getCellInsets(0, 0, unmarkedTable()));
   }

   @Test
   void cellInsetsSurviveANullInfo() {
      // the browser cell model calls this for non-table assemblies too
      VSTableLens lens = new VSTableLens(XTableUtil.getDefaultTableLens());

      assertNull(lens.getCellInsets(0, 0, null));
   }

   @Test
   void rowGrowthIsTheSeededVerticalPadding() {
      VSTableLens lens = new VSTableLens(XTableUtil.getDefaultTableLens());

      assertEquals(0, lens.getCSSRowPadding(0), "no stylesheet contributes anything");
      assertEquals(8, lens.getRowPadding(0, markedTable("compact")), "4 top + 4 bottom");
      assertEquals(12, lens.getRowPadding(0, markedTable("comfortable")), "6 top + 6 bottom");
      assertEquals(6, lens.getRowPadding(0, markedTable("dense")), "3 top + 3 bottom");
   }

   @Test
   void rowGrowthIsZeroForAnUnmarkedTable() {
      VSTableLens lens = new VSTableLens(XTableUtil.getDefaultTableLens());

      assertEquals(0, lens.getRowPadding(0, unmarkedTable()));
   }

   @Test
   void authorTypedRowHeightSurvivesASaveAndReopen() {
      // Review Focus 1: the composer stores a resize as a CONTENT height by subtracting the
      // padding (ComposerVSTableService:958/969) and the render adds it back
      // (BaseTableService:492). Both must read the SAME source, or the height drifts by
      // 2 * padding-y on every round trip. Driving both sides off the real resolver is what
      // makes this test fail if one of the two call sites is missed.
      VSTableLens lens = new VSTableLens(XTableUtil.getDefaultTableLens());
      TableVSAssemblyInfo info = markedTable("compact");
      int typed = 40;

      int stored = Math.max(0, typed - lens.getRowPadding(lens.getHeaderRowCount(), info));
      int rendered = stored + lens.getRowPadding(lens.getHeaderRowCount(), info);

      assertEquals(typed, rendered);
      assertEquals(32, stored, "stored as a content height, not the typed one");
   }

   private TableVSAssemblyInfo markedTable(String density) {
      SreeEnv.setProperty("viewsheet.density", density);
      TableVSAssemblyInfo info = new TableVSAssemblyInfo();
      info.seedChromeDefaults(VizContext.of(VizMark.MODERN_LIGHT));
      return info;
   }

   private TableVSAssemblyInfo unmarkedTable() {
      TableVSAssemblyInfo info = new TableVSAssemblyInfo();
      info.seedChromeDefaults(VizContext.of((VizMark) null));
      return info;
   }
}
```

`seedChromeDefaults` is `protected` and this test is in a different package, so either call it through a small package-private helper in `inetsoft.uql.viewsheet.internal` or set the padding directly with `setCellPadding(VSDensityDefaults.cellPaddingForMode(density), CompositeValue.Type.DEFAULT)`. Prefer the direct set — the seed itself is already covered by `TableCellPaddingSeedTest`, and this test is about the lens.

- [ ] **Step 9b: Record the three cases a unit test cannot reach**

Review Focus 2, 3 and 4 need a loaded `format.css`, a wrapped-text column and a span cell respectively — none of which has a fixture in `core/src/test`, and building one is a larger job than this feature. They go in the pull request description as required manual checks, each with its expected number:

- **Review Focus 2 — a `format.css` table style.** Load a `format.css` defining a `TableStyle` padding of 2px on all edges, at comfortable density where the seeded value (6px vertical) is larger. Every row must render at its pre-feature height — the stylesheet's 4px of growth, not the seeded 12px. This is the case `isCSSRowFullyPadded` exists for; if it regresses, every existing stylesheet customer's tables get taller.
- **Review Focus 3 — wrapped rows.** A table with wrapping enabled on a long text column, at comfortable. Each wrapped row grows by 12px total, not 12px per line. `VSTableDataHelper:177` calls `getWrappedHeight` first and adds the padding once; confirm in a PDF export and on screen.
- **Review Focus 4 — crosstab span cells.** A crosstab with a 3-row span cell at comfortable. The span's height is the three rendered row heights summed, so 36px of padding across the three, not 36px added on top of an already-padded sum. Confirm on screen and in a PDF export.

- [ ] **Step 10: Run the full core suite**

Run: `./mvnw test -pl core`
Expected: BUILD SUCCESS. This task changes a method signature used in ten places, so a green single-class run proves nothing here.

Confirm the new resolver tests ran: `./mvnw test -pl core -Dtest=TableCellPaddingResolutionTest`
Expected: PASS, all six.

- [ ] **Step 11: Commit**

```bash
git add core/src/main/java/inetsoft/uql/viewsheet/internal/VSDensityDefaults.java
git add core/src/main/java/inetsoft/report/composition/VSTableLens.java
git add core/src/main/java/inetsoft/web/viewsheet/model/table/BaseTableCellModel.java
git add core/src/main/java/inetsoft/web/viewsheet/controller/table/BaseTableService.java
git add core/src/main/java/inetsoft/web/composer/vs/objects/controller/ComposerVSTableService.java
git add core/src/main/java/inetsoft/report/io/viewsheet/VSTableHelper.java
git add core/src/main/java/inetsoft/report/io/viewsheet/VSCrosstabHelper.java
git add core/src/main/java/inetsoft/report/io/viewsheet/VSTableDataHelper.java
git add core/src/main/java/inetsoft/report/io/viewsheet/html/HTMLTableHelper.java
git add core/src/main/java/inetsoft/report/io/viewsheet/html/HTMLCrosstabHelper.java
git add core/src/main/java/inetsoft/report/io/viewsheet/AbstractVSExporter.java
git add core/src/main/java/inetsoft/report/io/viewsheet/excel/ExcelVSUtil.java
git add core/src/test/java/inetsoft/uql/viewsheet/internal/DensityRowHeightInvariantTest.java
git add core/src/test/java/inetsoft/uql/viewsheet/internal/VSDensityDefaultsTest.java
git add core/src/test/java/inetsoft/report/composition/TableCellPaddingResolutionTest.java
```

```bash
git commit -m "Resolve a table's cell padding, and rebalance the row matrix to absorb it"
```

---

## Task 6: vs-simple-cell honours the cell padding

Crosstab and calc table render span and placeholder cells through `vs-simple-cell`, which has no padding binding at all. Without this, those cells' text sits 2px from the edge while every `vs-table-cell` beside them sits at the density inset.

**Files:**
- Modify: `web/projects/portal/src/app/vsobjects/objects/table/vs-simple-cell.component.html`
- Modify: `web/projects/portal/src/app/vsobjects/objects/table/vs-simple-cell.component.scss:24`
- Test: `web/projects/portal/src/app/vsobjects/objects/table/vs-simple-cell.component.tl.spec.ts` (create)

**Interfaces:**
- Consumes: `cell.vsFormatModel.padding` — populated by `BaseTableCellModel` as of Task 5.
- Produces: nothing other tasks depend on.

- [ ] **Step 1: Write the failing test**

This asserts on rendered DOM, so it is a `*.tl.spec.ts` using `render()` from `@testing-library/angular`, per `CLAUDE.md`'s rule. Two files to read first, both siblings:

- `vs-crosstab.component.display.tl.spec.ts` — for the `render()` call and the component-input shape.
- `vs-table-cell.component.tl.spec.ts:53-100` — for `makeCell(overrides)`, a plain object factory that returns a fully populated `BaseTableCellModel`. Copy that factory rather than re-deriving the model's twenty required fields.

Create `vs-simple-cell.component.tl.spec.ts`:

```typescript
import { render, screen } from "@testing-library/angular";
import { VSSimpleCell } from "./vs-simple-cell.component";
import { BaseTableCellModel } from "../../model/base-table-cell-model";

// copied from vs-table-cell.component.tl.spec.ts:53 - keep the two in step
function makeCell(overrides: Partial<BaseTableCellModel> = {}): BaseTableCellModel {
   // ... paste the sibling's factory body here verbatim ...
   return { cellLabel: "Region", ...overrides } as BaseTableCellModel;
}

async function renderCell(padding: object | null) {
   const cell = makeCell({ vsFormatModel: { padding } as any });
   await render(VSSimpleCell, { componentInputs: { cell, width: 100, height: 20 } });
   return document.querySelector(".simple-cell-container") as HTMLElement;
}

describe("VSSimpleCell padding", () => {
   it("should apply the cell padding from the format model to each edge", async () => {
      const container = await renderCell({ top: 4, left: 6, bottom: 4, right: 6 });

      expect(container.style.paddingTop).toBe("4px");
      expect(container.style.paddingLeft).toBe("6px");
      expect(container.style.paddingBottom).toBe("4px");
      expect(container.style.paddingRight).toBe("6px");
   });

   it("should leave the stylesheet default in place when no padding is defined", async () => {
      const container = await renderCell(null);

      expect(container.style.paddingTop).toBe("");
      expect(container.style.paddingLeft).toBe("");
   });

   it("should still render the label", async () => {
      await renderCell({ top: 4, left: 6, bottom: 4, right: 6 });

      expect(screen.getByText("Region")).toBeInTheDocument();
   });
});
```

Check `vs-simple-cell.component.ts` for the exported class name and its `@Input()` names before writing `componentInputs` — the selector is the attribute form `vs-simple-cell`, and the class name may differ from the guess above.

- [ ] **Step 2: Run it to verify it fails**

Run from `community/web`: `npx ng run portal:test-tl --include="**/vs-simple-cell.component.tl.spec.ts"`
Expected: FAIL — `expected '' to be '4px'`.

Note the `portal:test-tl` target. `ng test portal --include="**/*.tl.spec.ts"` matches nothing and exits 0, which reads as a pass.

- [ ] **Step 3: Bind the four edges**

Replace the body of `vs-simple-cell.component.html` (keeping the licence header):

```html
@if (cell.cellLabel != '' && width > 0) {
  <div class="simple-cell-container"
    [style.width.px]="width - hBorderWidth"
    [style.height.px]="height - vBorderWidth"
    [style.padding-top.px]="cell.vsFormatModel?.padding?.top"
    [style.padding-left.px]="cell.vsFormatModel?.padding?.left"
    [style.padding-bottom.px]="cell.vsFormatModel?.padding?.bottom"
    [style.padding-right.px]="cell.vsFormatModel?.padding?.right"
    [class.cell-padding-defined]="!!cell.vsFormatModel?.padding">
    {{cell.cellLabel}}
  </div>
}
```

In `vs-simple-cell.component.scss`, add the clip rule next to the existing `padding: 0 2px 0 2px;`, matching `vs-table-cell.component.scss:60`:

```scss
  // a defined padding is an inline style that wins over the 2px above; clip to the content box so
  // text cannot bleed back into the inset
  &.cell-padding-defined {
    overflow: clip;
    overflow-clip-margin: content-box;
  }
```

- [ ] **Step 4: Run it to verify it passes**

Run: `npx ng run portal:test-tl --include="**/vs-simple-cell.component.tl.spec.ts"`
Expected: PASS, all three.

- [ ] **Step 5: Commit**

```bash
git add web/projects/portal/src/app/vsobjects/objects/table/vs-simple-cell.component.html
git add web/projects/portal/src/app/vsobjects/objects/table/vs-simple-cell.component.scss
git add web/projects/portal/src/app/vsobjects/objects/table/vs-simple-cell.component.tl.spec.ts
```

```bash
git commit -m "Apply the cell padding to a crosstab's simple cells"
```

---

## Task 7: Print layout gets the vertical cell padding

The sixth render surface, and the one that reaches none of Task 5's call sites: `VsToReportConverter` builds a `TableElementDef` whose padding comes from the report's global value. Left alone, a marked table prints with density row heights and no cell padding, so its rows are too short for their own text.

**Files:**
- Modify: `core/src/main/java/inetsoft/uql/viewsheet/internal/VsToReportConverter.java:1100` (`addTable`)
- Test: manual, per Step 4 — this path has no unit-test fixture in `core/src/test`

**Interfaces:**
- Consumes: `TableDataVSAssemblyInfo.getCellPadding()` from Task 3; `TableElementDef.setPadding(Insets)` (`:504`), which already exists.
- Produces: nothing other tasks depend on.

- [ ] **Step 1: Read the two methods you are about to change**

Read `VsToReportConverter.addTable` (`:1100-1130`) and `TableElementDef:81`, `:504`, and `:2038-2040`. The last one is why this step is vertical-only: it adds `getPadding().left + getPadding().right` to its computed column widths, so a horizontal value here would widen print-layout columns while the browser's stayed put — the same asymmetry D3 forces on the browser side, with its own evidence.

- [ ] **Step 2: Set the vertical padding**

In `addTable`, after `TableElementDef tableelem = new TableElementDef(report, lens);` and before `tableelem.setVSTableLens(lens);`:

```java
      // the cell padding the browser and the pixel exporters get from VSTableLens.getCellInsets.
      // This path reaches none of those sites - TableElementDef seeds itself from the REPORT's
      // global padding - so a marked table would print with density row heights and no padding
      // at all, leaving its rows too short for their own text.
      //
      // Vertical edges only. TableElementDef adds left+right to its computed column widths, so a
      // horizontal value would widen printed columns while the browser's stayed put
      Insets cellPadding = ((TableDataVSAssemblyInfo) assembly.getVSAssemblyInfo()).getCellPadding();

      if(cellPadding != null) {
         tableelem.setPadding(new Insets(cellPadding.top, 0, cellPadding.bottom, 0));
      }
```

Add `import java.awt.Insets;` if absent.

- [ ] **Step 3: Confirm the row-height path agrees**

Read `VsToReportConverter.calculateRowHeights` (`:1193`) and `computePrintLayoutTableHeight` (`:1035`). Both must produce rows that include the padding Step 2 just installed — `applyShrunkBottomTabsShift`'s comment (`:968-972`) records that this renderer does *not* add what `lens.getRowHeightWithPadding` adds, so check which of the two forms each one uses. If either computes an unpadded height, move it to `lens.getRowHeightWithPadding(h, r, info)` from Task 5.

- [ ] **Step 4: Verify by hand**

Build and run per `CLAUDE.md`, then:
1. Create a dashboard with a table, set the org density to comfortable in the EM.
2. Modernize the dashboard.
3. Open Print Layout, add the table, export to PDF.
4. Confirm: rows are tall enough that no descender is clipped, and column widths are the same as before this change (compare against a PDF exported from a build without Task 7).

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/inetsoft/uql/viewsheet/internal/VsToReportConverter.java
```

```bash
git commit -m "Give a printed table its cell padding"
```

---

## Task 8: Cell Padding in the three table property dialogs

All three table dialogs share `table-view-general-pane`, so the pane goes in once and three services wire it.

**Files:**
- Modify: `web/projects/portal/src/app/vsobjects/dialog/padding-pane.component.html:22`
- Modify: `web/projects/portal/src/app/vsobjects/dialog/padding-pane.component.ts`
- Modify: `web/projects/portal/src/app/vsobjects/dialog/table-view-general-pane.component.html`
- Modify: `web/projects/portal/src/app/vsobjects/dialog/table-view-general-pane.component.ts`
- Modify: `web/projects/portal/src/app/vsobjects/model/table-view-general-pane-model.ts`
- Modify: `core/src/main/java/inetsoft/web/composer/model/vs/TableViewGeneralPaneModel.java`
- Modify: `core/src/main/java/inetsoft/web/composer/vs/dialog/TableViewPropertyDialogService.java`
- Modify: `core/src/main/java/inetsoft/web/composer/vs/dialog/CrosstabPropertyDialogService.java`
- Modify: `core/src/main/java/inetsoft/web/composer/vs/dialog/CalcTablePropertyDialogService.java`
- Test: `web/projects/portal/src/app/vsobjects/dialog/padding-pane.component.spec.ts` (create)

**Interfaces:**
- Consumes: `isUserCellPadding()`, `setCellPadding(Insets, Type)`, `resetUserCellPadding()` from Task 3; `PaddingPaneModel` (exists, with `top`/`left`/`bottom`/`right`/`followsDefault`).
- Produces: `TableViewGeneralPaneModel.getCellPaddingPaneModel()` / `setCellPaddingPaneModel(PaddingPaneModel)`; `PaddingPane` gains `@Input() label: string`.

- [ ] **Step 1: Write the failing test for the label input**

Create `padding-pane.component.spec.ts`:

```typescript
import { ComponentFixture, TestBed } from "@angular/core/testing";
import { UntypedFormGroup } from "@angular/forms";
import { PaddingPane } from "./padding-pane.component";

function createFixture(label?: string): ComponentFixture<PaddingPane> {
   TestBed.configureTestingModule({ imports: [PaddingPane] });
   const fixture = TestBed.createComponent(PaddingPane);
   fixture.componentInstance.model = { top: 0, left: 0, bottom: 0, right: 0 };
   fixture.componentInstance.form = new UntypedFormGroup({});

   if(label !== undefined) {
      fixture.componentInstance.label = label;
   }

   fixture.detectChanges();
   return fixture;
}

function legendText(fixture: ComponentFixture<PaddingPane>): string {
   return fixture.nativeElement.querySelector("legend").textContent.trim();
}

describe("PaddingPane label", () => {
   it("should render the default legend when no label is supplied", () => {
      // the localization macro is not expanded in a unit test, so the raw token is what renders
      expect(legendText(createFixture())).toBe("_#(js:Padding)");
   });

   it("should render the supplied label", () => {
      expect(legendText(createFixture("Cell Padding"))).toBe("Cell Padding");
   });
});
```

`PaddingPane` is `standalone`, so it goes in `imports` rather than `declarations`. If the default legend renders as the localized string rather than the raw token in this harness, assert on whatever `ngOnInit` leaves in place — the point of the first test is that the default is unchanged from what the chart, gauge and text panes already show, so read the value once and pin it.

- [ ] **Step 2: Run it to verify it fails**

Run: `npx ng test portal --include="**/padding-pane.component.spec.ts"`
Expected: FAIL — `label` is not a property of `PaddingPane`.

- [ ] **Step 3: Add the label input**

In `padding-pane.component.ts`, beside the existing `@Input()`s:

```typescript
   @Input() label: string = "_#(js:Padding)";
```

In `padding-pane.component.html`, change the legend:

```html
      <legend>{{label}}</legend>
```

- [ ] **Step 4: Run it to verify it passes**

Run: `npx ng test portal --include="**/padding-pane.component.spec.ts"`
Expected: PASS, both.

Then run the panes that already use the component, to prove the default is really the old string:

Run: `npx ng run portal:test-tl --include="**/chart-property-dialog.component.tl.spec.ts"`
Expected: PASS.

- [ ] **Step 5: Add the model field**

In `TableViewGeneralPaneModel.java`, beside `getSizePositionPaneModel`:

```java
   public PaddingPaneModel getCellPaddingPaneModel() {
      if(cellPaddingPaneModel == null) {
         cellPaddingPaneModel = new PaddingPaneModel();
      }

      return cellPaddingPaneModel;
   }

   public void setCellPaddingPaneModel(PaddingPaneModel cellPaddingPaneModel) {
      this.cellPaddingPaneModel = cellPaddingPaneModel;
   }
```

and the field:

```java
   private PaddingPaneModel cellPaddingPaneModel;
```

In `table-view-general-pane-model.ts`, add `cellPaddingPaneModel: PaddingPaneModel;` and its import.

- [ ] **Step 6: Render the pane**

In `table-view-general-pane.component.html`, after the `<table-style-pane>` line and before `<size-position-pane>`:

```html
<padding-pane [model]="model.cellPaddingPaneModel" label="_#(Cell Padding)"></padding-pane>
```

In `table-view-general-pane.component.ts`, add `PaddingPane` to the component's `imports` array and its `import` statement.

- [ ] **Step 7: Wire load and apply in all three services**

In each of `TableViewPropertyDialogService`, `CrosstabPropertyDialogService` and `CalcTablePropertyDialogService`, in the method that populates the model, beside the other `tableViewGeneralPaneModel.getX()` calls:

```java
      PaddingPaneModel cellPaddingPaneModel = tableViewGeneralPaneModel.getCellPaddingPaneModel();
      Insets cellPadding = tableAssemblyInfo.getCellPadding();
      cellPaddingPaneModel.setTop(cellPadding == null ? 0 : cellPadding.top);
      cellPaddingPaneModel.setLeft(cellPadding == null ? 0 : cellPadding.left);
      cellPaddingPaneModel.setBottom(cellPadding == null ? 0 : cellPadding.bottom);
      cellPaddingPaneModel.setRight(cellPadding == null ? 0 : cellPadding.right);
      // null hides the checkbox: an unmarked table has no default to follow, and the pane then
      // behaves exactly as it did before the checkbox existed
      cellPaddingPaneModel.setFollowsDefault(
         tableAssemblyInfo.getVizMark() == null ? null : !tableAssemblyInfo.isUserCellPadding());
```

and in the method that applies it:

```java
      PaddingPaneModel cellPaddingPaneModel = tableViewGeneralPaneModel.getCellPaddingPaneModel();
      Insets editedCellPadding = new Insets(
         cellPaddingPaneModel.getTop(), cellPaddingPaneModel.getLeft(),
         cellPaddingPaneModel.getBottom(), cellPaddingPaneModel.getRight());
      Boolean cellPaddingFollowsDefault = cellPaddingPaneModel.getFollowsDefault();

      if(cellPaddingFollowsDefault == null) {
         // no checkbox was shown, so this table is not marked; store only a real edit
         if(!editedCellPadding.equals(tableAssemblyInfo.getCellPadding())) {
            tableAssemblyInfo.setCellPadding(editedCellPadding, CompositeValue.Type.USER);
         }
      }
      else if(cellPaddingFollowsDefault) {
         // clear the opinion and let the density decide, the same shape Revert uses. Writing the
         // current density value into the USER tier here would pin this tier
         tableAssemblyInfo.resetUserCellPadding();
      }
      else {
         tableAssemblyInfo.setCellPadding(editedCellPadding, CompositeValue.Type.USER);
      }
```

The local holding the info is named differently in each service — read the surrounding lines and match it. Add the `java.awt.Insets` and `inetsoft.uql.CompositeValue` imports.

- [ ] **Step 8: Write the dialog test**

Create `web/projects/portal/src/app/vsobjects/dialog/table-view-general-pane.component.tl.spec.ts`. Read `composer/dialog/vs/viewsheet-options-pane.component.tl.spec.ts` first — it is the closest sibling and shows the `render()` call, the `componentInputs` shape and the `UntypedFormGroup` this pane needs.

Define the two model factories at the top of the file rather than importing them; they differ in exactly one field:

```typescript
function paneModel(followsDefault: boolean | null) {
   return {
      generalPropPaneModel: { enabled: "True", basicGeneralPaneModel: {} },
      titlePropPaneModel: { visible: true, title: "Table1" },
      tableStylePaneModel: {},
      sizePositionPaneModel: { top: 0, left: 0, width: 400, height: 300 },
      paddingPaneModel: { top: 12, left: 12, bottom: 12, right: 12, followsDefault },
      cellPaddingPaneModel: { top: 4, left: 6, bottom: 4, right: 6, followsDefault },
      showMaxRows: false,
      showSubmitOnChange: false
   } as any;
}

// a marked table follows the default, so followsDefault is a real boolean
const markedTableModel = () => paneModel(true);
// an unmarked one has no default to follow, so the server sends null and the checkbox hides
const unmarkedTableModel = () => paneModel(null);

async function renderPane(model: any) {
   await render(TableViewGeneralPane, {
      componentInputs: { model, form: new UntypedFormGroup({}) }
   });
}
```

Then assert:

```typescript
describe("TableViewGeneralPane cell padding", () => {
   it("should show both padding groups", async () => {
      await renderPane(markedTableModel());

      expect(screen.getByText("Padding")).toBeInTheDocument();
      expect(screen.getByText("Cell Padding")).toBeInTheDocument();
   });

   it("should hide the follow-default checkbox on an unmarked table", async () => {
      await renderPane(unmarkedTableModel());   // cellPaddingPaneModel.followsDefault === null

      expect(screen.queryByLabelText(/follow.*default/i)).toBeNull();
   });

   it("should disable the four steppers while following the default", async () => {
      await renderPane(markedTableModel());     // followsDefault === true

      expect(screen.getByLabelText("Top")).toBeDisabled();
   });
});
```

- [ ] **Step 9: Run the tests**

Run: `npx ng run portal:test-tl --include="**/table-view-general-pane.component.tl.spec.ts"`
Expected: PASS.

Run: `./mvnw test -pl core`
Expected: BUILD SUCCESS.

- [ ] **Step 10: Commit**

```bash
git add web/projects/portal/src/app/vsobjects/dialog/padding-pane.component.html
git add web/projects/portal/src/app/vsobjects/dialog/padding-pane.component.ts
git add web/projects/portal/src/app/vsobjects/dialog/padding-pane.component.spec.ts
git add web/projects/portal/src/app/vsobjects/dialog/table-view-general-pane.component.html
git add web/projects/portal/src/app/vsobjects/dialog/table-view-general-pane.component.ts
git add web/projects/portal/src/app/vsobjects/dialog/table-view-general-pane.component.tl.spec.ts
git add web/projects/portal/src/app/vsobjects/model/table-view-general-pane-model.ts
git add core/src/main/java/inetsoft/web/composer/model/vs/TableViewGeneralPaneModel.java
git add core/src/main/java/inetsoft/web/composer/vs/dialog/TableViewPropertyDialogService.java
git add core/src/main/java/inetsoft/web/composer/vs/dialog/CrosstabPropertyDialogService.java
git add core/src/main/java/inetsoft/web/composer/vs/dialog/CalcTablePropertyDialogService.java
```

```bash
git commit -m "Let an author set a table's cell padding"
```

**Slice A is complete. Open the pull request before starting Task 9.**

---

# SLICE B — the table card inset

## Task 9: Hoist userPadding to VSAssemblyInfo

`padding` lives on `VSAssemblyInfo` but the flag that guards it lives on `ChartVSAssemblyInfo`. Tables need the flag, so it moves down to sit beside the field.

**Files:**
- Modify: `core/src/main/java/inetsoft/uql/viewsheet/internal/VSAssemblyInfo.java`
- Modify: `core/src/main/java/inetsoft/uql/viewsheet/internal/ChartVSAssemblyInfo.java` — remove `:1547`, `:1585-1586`, `:1992-1993`, `:2957`, `:2964`, `:3231`, and `resetCardInset`
- Test: `core/src/test/java/inetsoft/uql/viewsheet/internal/UserPaddingHoistTest.java` (create)

**Interfaces:**
- Consumes: `VSDensityDefaults.chartPadding` / `tablePadding` from Task 1.
- Produces, on `VSAssemblyInfo`: `isUserPadding()`, `setUserPadding(boolean)`, `resetPadding(VizContext ctx)`. `ChartVSAssemblyInfo.resetCardInset` is gone; its callers move to `resetPadding`.

- [ ] **Step 1: Write the failing test**

Create `UserPaddingHoistTest.java`, with the same class annotations as `ChartCardInsetDensityTest`:

```java
   @Test
   void aTableCarriesTheFlag() {
      TableVSAssemblyInfo info = new TableVSAssemblyInfo();
      assertFalse(info.isUserPadding());

      info.setUserPadding(true);

      assertTrue(info.isUserPadding());
   }

   @Test
   void theFlagRoundTripsOnATable() throws Exception {
      TableVSAssemblyInfo written = new TableVSAssemblyInfo();
      written.setUserPadding(true);

      assertTrue(reparseTable(written).isUserPadding());
   }

   @Test
   void aChartSavedBeforeTheHoistStillParses() throws Exception {
      // the attribute name is unchanged, so a pre-hoist asset must read exactly as it used to
      ChartVSAssemblyInfo read = new ChartVSAssemblyInfo();
      read.parseAttributes(parseElement(
         "<assembly class=\"ChartVSAssemblyInfo\" userPadding=\"true\"/>"));

      assertTrue(read.isUserPadding());
   }

   @Test
   void theAttributeIsWrittenExactlyOnce() throws Exception {
      // the hoist moves the write to the base class; leaving the subclass copy in place would
      // emit it twice and the second would win on parse
      ChartVSAssemblyInfo info = new ChartVSAssemblyInfo();
      info.setUserPadding(true);

      String xml = writeAttributes(info);
      int first = xml.indexOf("userPadding=");

      assertTrue(first >= 0, "attribute written");
      assertEquals(-1, xml.indexOf("userPadding=", first + 1), "written only once");
   }

   @Test
   void copyInfoCarriesTheFlag() {
      // the dialog's clone-and-merge runs through copyInfo; dropping the flag there would make
      // every OK on a chart or table property dialog forget that the author set the inset
      ChartVSAssemblyInfo from = new ChartVSAssemblyInfo();
      from.setUserPadding(true);
      ChartVSAssemblyInfo to = new ChartVSAssemblyInfo();

      assertTrue(to.copyInfo(from), "copyInfo reports a change");
      assertTrue(to.isUserPadding());
   }

   @Test
   void resetPaddingFollowsDensityForATable() {
      SreeEnv.setProperty("viewsheet.density", "comfortable");
      TableVSAssemblyInfo info = new TableVSAssemblyInfo();
      info.setPadding(new Insets(3, 3, 3, 3));

      info.resetPadding(VizContext.of(VizMark.MODERN_LIGHT));

      assertEquals(new Insets(16, 16, 16, 16), info.getPadding());
   }

   @Test
   void resetPaddingRestoresTheLegacyInsetPerType() {
      TableVSAssemblyInfo table = new TableVSAssemblyInfo();
      ChartVSAssemblyInfo chart = new ChartVSAssemblyInfo();

      table.resetPadding(VizContext.of((VizMark) null));
      chart.resetPadding(VizContext.of((VizMark) null));

      assertEquals(new Insets(0, 0, 0, 0), table.getPadding(), "a table has never had an inset");
      assertEquals(new Insets(10, 10, 10, 10), chart.getPadding(), "the chart's creation default");
   }
```

Reuse the `reparse`/`parseElement`/`writeAttributes` helpers from Task 4, generalised over `VSAssemblyInfo`.

- [ ] **Step 2: Run it to verify it fails**

Run: `./mvnw test -pl core -Dtest=UserPaddingHoistTest`
Expected: compilation failure — `TableVSAssemblyInfo` has no `isUserPadding()`.

- [ ] **Step 3: Move the flag down**

Delete from `ChartVSAssemblyInfo.java`: the `userPadding` field (`:3231`), `isUserPadding`/`setUserPadding` (`:2957-2966`), the `writeAttributes` line (`:1547`), the `parseAttributes` pair (`:1585-1586`) *with its comment*, and the `copyInfo` block (`:1992-1995`).

Add to `VSAssemblyInfo.java`, beside the `padding` field:

```java
   // whether the author set the padding. Distinguishes a deliberate inset from the creation
   // default, so the seed substitutes for the latter only. Lives here rather than on the chart,
   // which owned it first, because the field it guards has always lived here
   private boolean userPadding = false;
```

and the accessors beside `getPadding`/`setPadding`:

```java
   /**
    * Whether the author set the padding. Surfaced in the property dialog as the padding pane's
    * follow-the-default checkbox, inverted.
    */
   public boolean isUserPadding() {
      return userPadding;
   }

   /**
    * Set whether the padding was set by the author.
    */
   public void setUserPadding(boolean userPadding) {
      this.userPadding = userPadding;
   }
```

In `VSAssemblyInfo.writeAttributes`, after the `padding` block:

```java
      writer.print(" userPadding=\"" + isUserPadding() + "\"");
```

In `VSAssemblyInfo.parseAttributes`, after the `paddingTop` block:

```java
      // absent in files saved before the flag existed; a missing flag means no opinion, and the
      // seed's comparison against the creation default decides
      setUserPadding("true".equalsIgnoreCase(Tool.getAttribute(elem, "userPadding")));
```

In `VSAssemblyInfo.copyInfo`, beside the existing `padding` copy:

```java
      if(userPadding != info.userPadding) {
         userPadding = info.userPadding;
         result = true;
      }
```

- [ ] **Step 4: Move resetCardInset down as resetPadding**

Delete `ChartVSAssemblyInfo.resetCardInset` and `isCssPaddingDefined`. Add to `VSAssemblyInfo.java`:

```java
   /**
    * Whether a format.css class set this assembly's padding. setCSSDefaults writes it before the
    * seed runs and there is no tier to record it in, so the dictionary is asked directly.
    */
   protected boolean isCssPaddingDefined() {
      VSCompositeFormat objFormat = getFormat();

      if(objFormat == null) {
         return false;
      }

      return CSSDictionary.getDictionary()
         .isPaddingDefined(objFormat.getCSSFormat().getCSSParam());
   }

   /**
    * The card inset this type takes when nobody has an opinion. Overridden by the types that have
    * one; every other assembly keeps whatever it was constructed with.
    */
   protected Insets defaultPadding(VizContext ctx) {
      return getPadding();
   }

   /**
    * Reset the card inset to whatever "follow the default" means right now. The padding pane's
    * checkbox needs only this write - the full seedChromeDefaults hook also re-runs the card
    * background, the title lane and the colour palette, none of which that checkbox asked for.
    *
    * A format.css class still wins: it already installed its own padding through setCSSDefaults,
    * and this re-reads it live rather than trusting whatever the field currently holds, which is
    * what keeps the checkbox sane after an author had overridden that CSS padding and is now
    * asking to give it back.
    */
   public void resetPadding(VizContext ctx) {
      if(isCssPaddingDefined()) {
         setPadding(CSSDictionary.getDictionary()
                       .getPadding(getFormat().getCSSFormat().getCSSParam()));
         return;
      }

      setPadding(defaultPadding(ctx));
   }
```

Override `defaultPadding` in `ChartVSAssemblyInfo`:

```java
   @Override
   protected Insets defaultPadding(VizContext ctx) {
      return VSDensityDefaults.chartPadding(ctx);
   }
```

and in `TableDataVSAssemblyInfo`:

```java
   @Override
   protected Insets defaultPadding(VizContext ctx) {
      return VSDensityDefaults.tablePadding(ctx);
   }
```

Simplify the chart's seed branch to use the shared helper:

```java
      if(!isUserPadding() && !isCssPaddingDefined()) {
         setPadding(VSDensityDefaults.chartPadding(ctx));
      }
```

- [ ] **Step 5: Repoint resetCardInset's callers**

Run: `grep -rn "resetCardInset" --include=*.java core/src`
Change each hit to `resetPadding`. `ChartPropertyDialogService:414` is the one that matters.

- [ ] **Step 6: Run the tests**

Run: `./mvnw test -pl core -Dtest=UserPaddingHoistTest,ChartCardInsetDensityTest`
Expected: PASS.

Run: `./mvnw test -pl core`
Expected: BUILD SUCCESS.

- [ ] **Step 7: Commit**

```bash
git add core/src/main/java/inetsoft/uql/viewsheet/internal/VSAssemblyInfo.java
git add core/src/main/java/inetsoft/uql/viewsheet/internal/ChartVSAssemblyInfo.java
git add core/src/main/java/inetsoft/uql/viewsheet/internal/TableDataVSAssemblyInfo.java
git add core/src/main/java/inetsoft/web/composer/vs/dialog/ChartPropertyDialogService.java
git add core/src/test/java/inetsoft/uql/viewsheet/internal/UserPaddingHoistTest.java
```

```bash
git commit -m "Move userPadding and the inset reset beside the field they guard"
```

---

## Task 10: Seed the table's card inset and send it to the browser

**Files:**
- Modify: `core/src/main/java/inetsoft/uql/viewsheet/internal/TableDataVSAssemblyInfo.java` — `seedChromeDefaults`
- Modify: `core/src/main/java/inetsoft/web/viewsheet/model/table/BaseTableModel.java`
- Modify: `web/projects/portal/src/app/vsobjects/model/table/base-table-model.ts`
- Test: `core/src/test/java/inetsoft/uql/viewsheet/internal/TableCardInsetSeedTest.java` (create)

**Interfaces:**
- Consumes: `isUserPadding()` from Task 9; `VSDensityDefaults.tablePadding` from Task 1.
- Produces: `BaseTableModel.padding` (an `Insets`, serialized to the browser as `{top,left,bottom,right}`), read by Task 11.

- [ ] **Step 1: Write the failing test**

Create `TableCardInsetSeedTest.java`, same annotations as `TableCellPaddingSeedTest`:

```java
   @Test
   void markedTableSeedsTheDensityCardInset() {
      SreeEnv.setProperty("viewsheet.density", "comfortable");
      TableVSAssemblyInfo info = new TableVSAssemblyInfo();

      info.seedChromeDefaults(VizContext.of(VizMark.MODERN_LIGHT));

      assertEquals(new Insets(16, 16, 16, 16), info.getPadding());
   }

   @Test
   void eachTierSeedsItsOwnInset() {
      SreeEnv.setProperty("viewsheet.density", "compact");
      TableVSAssemblyInfo compact = new TableVSAssemblyInfo();
      compact.seedChromeDefaults(VizContext.of(VizMark.MODERN_LIGHT));
      assertEquals(new Insets(12, 12, 12, 12), compact.getPadding());

      SreeEnv.setProperty("viewsheet.density", "dense");
      TableVSAssemblyInfo dense = new TableVSAssemblyInfo();
      dense.seedChromeDefaults(VizContext.of(VizMark.MODERN_LIGHT));
      assertEquals(new Insets(8, 8, 8, 8), dense.getPadding());
   }

   @Test
   void unmarkedTableHasNoCardInset() {
      SreeEnv.setProperty("viewsheet.density", "comfortable");
      TableVSAssemblyInfo info = new TableVSAssemblyInfo();

      info.seedChromeDefaults(VizContext.of((VizMark) null));

      assertEquals(new Insets(0, 0, 0, 0), info.getPadding());
   }

   @Test
   void revertClearsASeededCardInset() {
      SreeEnv.setProperty("viewsheet.density", "comfortable");
      TableVSAssemblyInfo info = new TableVSAssemblyInfo();
      info.seedChromeDefaults(VizContext.of(VizMark.MODERN_LIGHT));

      info.seedChromeDefaults(VizContext.of((VizMark) null));

      assertEquals(new Insets(0, 0, 0, 0), info.getPadding());
   }

   @Test
   void authorSetInsetIsNeverSubstituted() {
      SreeEnv.setProperty("viewsheet.density", "comfortable");
      TableVSAssemblyInfo info = new TableVSAssemblyInfo();
      info.setUserPadding(true);
      info.setPadding(new Insets(2, 2, 2, 2));

      info.seedChromeDefaults(VizContext.of(VizMark.MODERN_LIGHT));

      assertEquals(new Insets(2, 2, 2, 2), info.getPadding());
   }

   @Test
   void crosstabAndCalcTableSeedTheSameInset() {
      SreeEnv.setProperty("viewsheet.density", "compact");
      CrosstabVSAssemblyInfo crosstab = new CrosstabVSAssemblyInfo();
      CalcTableVSAssemblyInfo calc = new CalcTableVSAssemblyInfo();

      crosstab.seedChromeDefaults(VizContext.of(VizMark.MODERN_LIGHT));
      calc.seedChromeDefaults(VizContext.of(VizMark.MODERN_LIGHT));

      assertEquals(new Insets(12, 12, 12, 12), crosstab.getPadding());
      assertEquals(new Insets(12, 12, 12, 12), calc.getPadding());
   }
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./mvnw test -pl core -Dtest=TableCardInsetSeedTest`
Expected: FAIL — `expected: <java.awt.Insets[top=16,...]> but was: <java.awt.Insets[top=0,...]>`.

- [ ] **Step 3: Seed it**

In `TableDataVSAssemblyInfo.seedChromeDefaults`, beside the cell-padding branch from Task 3:

```java
      // the card inset. Seeded rather than resolved at read time so it travels in an exported
      // asset. Both branches write: a table has never had an inset, and that zero is what Revert
      // has to restore
      if(!isUserPadding() && !isCssPaddingDefined()) {
         setPadding(VSDensityDefaults.tablePadding(ctx));
      }
```

- [ ] **Step 4: Run it to verify it passes**

Run: `./mvnw test -pl core -Dtest=TableCardInsetSeedTest`
Expected: PASS, all six.

- [ ] **Step 5: Send it to the browser**

In `BaseTableModel.java`, add a `padding` property of the same shape `VSFormatModel` uses for its own `Insets` (read `VSFormatModel:258-262` and copy it), populated in whichever builder or constructor already reads the assembly info. In `base-table-model.ts` add:

```typescript
   padding?: { top: number, left: number, bottom: number, right: number };
```

- [ ] **Step 6: Verify the model reaches the browser**

Build and run per `CLAUDE.md`. Open a modernized dashboard with a table, and in the browser devtools confirm the table's model carries a non-zero `padding`. Nothing renders differently yet — Task 11 does that.

- [ ] **Step 7: Commit**

```bash
git add core/src/main/java/inetsoft/uql/viewsheet/internal/TableDataVSAssemblyInfo.java
git add core/src/main/java/inetsoft/web/viewsheet/model/table/BaseTableModel.java
git add web/projects/portal/src/app/vsobjects/model/table/base-table-model.ts
git add core/src/test/java/inetsoft/uql/viewsheet/internal/TableCardInsetSeedTest.java
```

```bash
git commit -m "Seed a table's card inset and send it to the browser"
```

---

## Task 11: Split the browser's card and content geometry

**Files:**
- Create: `web/projects/portal/src/app/vsobjects/objects/table/table-content-rect.ts`
- Modify: `web/projects/portal/src/app/vsobjects/objects/table/base-table.ts:331`, `:1641`, `:1678`, `:1692`
- Modify: `web/projects/portal/src/app/vsobjects/objects/table/vs-table.component.html`
- Modify: `web/projects/portal/src/app/vsobjects/objects/table/vs-crosstab.component.html`
- Modify: `web/projects/portal/src/app/vsobjects/objects/table/vs-calctable.component.html`
- Test: `web/projects/portal/src/app/vsobjects/objects/table/table-content-rect.spec.ts` (create)

**Interfaces:**
- Consumes: `model.padding` from Task 10.
- Produces:
  - `contentRect(card: CardRect, padding: TablePadding | null): CardRect` — exported from `table-content-rect.ts`
  - on `BaseTable`: `getCardWidth()`, `getCardHeight()`, `getContentLeft()`, `getContentTop()`. `getObjectWidth()`/`getObjectHeight()` keep their names and now return the content rect — every existing caller wants the content rect, which is why the new names go on the card.

- [ ] **Step 1: Write the failing test**

The arithmetic goes in a pure module with its own spec rather than on `BaseTable`. `BaseTable` is `abstract class BaseTable<T extends BaseTableModel> extends AbstractVSObject<T>` with a seven-argument constructor (`:282`) and an abstract `updateTableHeight()` (`:380`), so a spec that subclasses it is a brittle fixture that breaks on every unrelated constructor change. `strip-glyph-tone.ts` / `.spec.ts` under `objects/mini-toolbar/` is this track's own precedent for the split.

Create `table-content-rect.spec.ts`:

```typescript
import { contentRect } from "./table-content-rect";

describe("contentRect", () => {
   const card = { width: 400, height: 300 };
   const inset = { top: 12, left: 12, bottom: 12, right: 12 };

   it("should inset the card by the padding on all four edges", () => {
      expect(contentRect(card, inset)).toEqual({ width: 376, height: 276 });
   });

   it("should take each edge independently", () => {
      expect(contentRect(card, { top: 1, left: 2, bottom: 4, right: 8 }))
         .toEqual({ width: 390, height: 295 });
   });

   it("should return the card unchanged when there is no padding", () => {
      expect(contentRect(card, null)).toEqual({ width: 400, height: 300 });
   });

   it("should return the card unchanged for a zero padding", () => {
      expect(contentRect(card, { top: 0, left: 0, bottom: 0, right: 0 }))
         .toEqual({ width: 400, height: 300 });
   });

   it("should clamp at zero rather than going negative on a tiny assembly", () => {
      expect(contentRect({ width: 10, height: 10 }, inset)).toEqual({ width: 0, height: 0 });
   });

   it("should not mutate its arguments", () => {
      contentRect(card, inset);

      expect(card).toEqual({ width: 400, height: 300 });
      expect(inset).toEqual({ top: 12, left: 12, bottom: 12, right: 12 });
   });
});
```

- [ ] **Step 2: Run it to verify it fails**

Run: `npx ng test portal --include="**/table-content-rect.spec.ts"`
Expected: FAIL — `Failed to resolve import "./table-content-rect"`.

- [ ] **Step 3: Write the pure module**

Create `table-content-rect.ts` (with the standard AGPL header every file in this tree carries — copy it from `strip-glyph-tone.ts`):

```typescript
export interface CardRect {
   width: number;
   height: number;
}

export interface TablePadding {
   top: number;
   left: number;
   bottom: number;
   right: number;
}

/**
 * The rect a table's grid draws into: its card minus the card inset. The card rect itself stays
 * with the border, the background and the round-corner clip, which draw at the assembly edge —
 * the same split the chart has had since its own card inset shipped.
 *
 * Clamped at zero: an assembly can be dragged smaller than its own inset, and a negative width
 * reaches the DOM as an invalid style that silently drops the binding.
 */
export function contentRect(card: CardRect, padding: TablePadding | null): CardRect {
   if(!padding) {
      return { width: card.width, height: card.height };
   }

   return {
      width: Math.max(0, card.width - padding.left - padding.right),
      height: Math.max(0, card.height - padding.top - padding.bottom)
   };
}
```

- [ ] **Step 3b: Run it to verify it passes**

Run: `npx ng test portal --include="**/table-content-rect.spec.ts"`
Expected: PASS, all six.

- [ ] **Step 3c: Split the accessors on BaseTable**

In `base-table.ts`, rename the existing bodies of `getObjectWidth()` (`:1678`) and `getObjectHeight()` (`:1641`) to `getCardWidth()` and `getCardHeight()` **unchanged** — including their `model.shrink` and `maxMode` special cases, which describe the card. Then add, importing `contentRect` and `TablePadding`:

```typescript
   /**
    * The card's inset, or zero when this assembly carries none. Read per call rather than cached:
    * a density change rewrites it and the model is replaced wholesale.
    */
   private getPadding(): TablePadding {
      return this.model.padding || { top: 0, left: 0, bottom: 0, right: 0 };
   }

   public getContentLeft(): number {
      return this.getPadding().left;
   }

   public getContentTop(): number {
      return this.getPadding().top;
   }

   public getObjectWidth(): number {
      return contentRect({ width: this.getCardWidth(), height: this.getCardHeight() },
                         this.getPadding()).width;
   }

   public getObjectHeight(): number {
      return contentRect({ width: this.getCardWidth(), height: this.getCardHeight() },
                         this.getPadding()).height;
   }
```

In `updateTableHeight()` — abstract at `:380`, implemented in each of the three components — subtract nothing extra: it already derives from `model.objectFormat.height`, so change it to start from `this.getObjectHeight()` instead.

`vs-table.component.ts:487` overrides `getObjectWidth()` for the `scrollWrapper` case. Rename that override to `getCardWidth()` so the scroll-wrapper behaviour stays on the card, and let the base class's inset apply on top.

- [ ] **Step 4: Confirm the accessors compile and the existing table specs still pass**

Run: `npx ng test portal --include="**/vs-table.component*.spec.ts"`
Expected: PASS. An unmarked table has no `model.padding`, so `contentRect` returns the card unchanged and every existing assertion holds.

- [ ] **Step 5: Rebind the three templates**

In each of `vs-table.component.html`, `vs-crosstab.component.html` and `vs-calctable.component.html`:

- `.table-container` keeps `[style.width.px]` and `[style.height.px]` bound to the **card** rect — change the `@let objectWidth = getObjectWidth();` line at the top to `@let cardWidth = getCardWidth();` and add `@let contentWidth = getObjectWidth();`, same for height.
- `.border-div` binds `[style.width.px]="cardWidth"` and its height to the card.
- `.z-index-wrapper` keeps the background and `border-radius` at the card rect.
- Everything inside — the title lane, the header table, the body table, the scroll wrappers and the drop lines — binds to `contentWidth`/`contentHeight`, and the outermost of those gets `[style.margin-left.px]="getContentLeft()"` and `[style.margin-top.px]="getContentTop()"`.

Work one template at a time and check each in the browser before moving to the next.

- [ ] **Step 6: Verify the regression surface by hand**

Build and run. On a modernized dashboard at comfortable density, check each of these for a table, a crosstab and a calc table:

1. Normal render — grid inset 16px on all four edges, border and background at the assembly edge.
2. Max mode — inset preserved, no clipping.
3. Shrink-to-fit (`model.shrink`) — the card shrinks to the content plus the inset, not to the content alone.
4. Wrapped headers — header lane still aligns with the body columns.
5. Horizontal and vertical scroll — scrollbars sit inside the inset.
6. Round corners — the background clips to the radius and the grid does not overlap it.
7. Binding pane — drop lines land on the inset grid, not the card edge.
8. An unmarked table — pixel-identical to before this task.

- [ ] **Step 7: Run the table suites**

Run: `npx ng test portal --include="**/vs-table.component*.spec.ts"`
Run: `npx ng test portal --include="**/vs-crosstab.component*.spec.ts"`
Run: `npx ng test portal --include="**/vs-calctable*.spec.ts"`
Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add web/projects/portal/src/app/vsobjects/objects/table/table-content-rect.ts
git add web/projects/portal/src/app/vsobjects/objects/table/table-content-rect.spec.ts
git add web/projects/portal/src/app/vsobjects/objects/table/base-table.ts
git add web/projects/portal/src/app/vsobjects/objects/table/vs-table.component.html
git add web/projects/portal/src/app/vsobjects/objects/table/vs-table.component.ts
git add web/projects/portal/src/app/vsobjects/objects/table/vs-crosstab.component.html
git add web/projects/portal/src/app/vsobjects/objects/table/vs-calctable.component.html
```

```bash
git commit -m "Inset a table's grid from its card edge in the browser"
```

---

## Task 12: Inset the exported table

**Files:**
- Modify: `core/src/main/java/inetsoft/report/io/viewsheet/VSTableDataHelper.java` — `getObjectPixelBounds` (`:157`), `getTableRectangle`
- Modify: `core/src/main/java/inetsoft/report/io/viewsheet/VSTableHelper.java` — `calculateColumnsPosition`
- Modify: `core/src/main/java/inetsoft/report/io/viewsheet/VSCrosstabHelper.java` — `calculateColumnsPosition`
- Modify: `core/src/main/java/inetsoft/report/io/viewsheet/html/HTMLTableHelper.java`, `HTMLCrosstabHelper.java`
- Modify: `core/src/main/java/inetsoft/uql/viewsheet/internal/VsToReportConverter.java` — `addTable`

**Interfaces:**
- Consumes: `TableDataVSAssemblyInfo.getPadding()` from Task 10.
- Produces: `VSTableDataHelper.contentBounds(TableDataVSAssemblyInfo info, Rectangle2D cardBounds)` → `Rectangle2D`.

- [ ] **Step 1: Read the shape to copy**

Read `VsToReportConverter:1472-1516`. That is the chart doing exactly this job: it takes the assembly bounds, adds `padding.left`/`padding.top` to the origin and subtracts both edges from the size, while the object format's border and background stay at the full bounds. The table's version is the same arithmetic in four more places.

- [ ] **Step 2: Add the shared helper**

In `VSTableDataHelper.java`:

```java
   /**
    * The rect the grid draws into: the card minus its inset. The card rect itself is what
    * drawObjectFormat keeps using, so border, background and round corner stay at the assembly
    * edge while the title lane, header, body and scrollbars move inside - the same split the
    * chart has had since its card inset shipped (VsToReportConverter:1493-1496).
    */
   protected Rectangle2D contentBounds(TableDataVSAssemblyInfo info, Rectangle2D cardBounds) {
      Insets padding = info.getPadding();

      if(padding == null) {
         return cardBounds;
      }

      double scale = getPixelToPointRatio();

      return new Rectangle2D.Double(
         cardBounds.getX() + padding.left * scale,
         cardBounds.getY() + padding.top * scale,
         Math.max(0, cardBounds.getWidth() - (padding.left + padding.right) * scale),
         Math.max(0, cardBounds.getHeight() - (padding.top + padding.bottom) * scale));
   }
```

Confirm `getPixelToPointRatio()` is the right scale for the unit each caller works in — `getObjectPixelBounds` already multiplies a pixel height by it at `:187`. If a caller is already in points, pass `1`.

- [ ] **Step 3: Apply it in the shared base**

In `getObjectPixelBounds`, wrap the returned bounds: the value handed back to the grid becomes `contentBounds(info, bounds)`, and any use of the same rect for the object format stays on `bounds`. Read the whole method before editing — it has four early returns for `isMatchLayout`, `isShrink` and the two truncation cases, and each needs the same treatment.

In `getTableRectangle`, reduce the available width and height by the inset before the row and column counts are derived from them, so a table truncated to fit is truncated against the content rect.

- [ ] **Step 4: Apply it in the two column-position calculators**

In `VSTableHelper.calculateColumnsPosition` and `VSCrosstabHelper.calculateColumnsPosition`, start the first column at the content rect's `x` rather than the card's, and distribute against the content width.

- [ ] **Step 5: Apply it in the HTML helpers**

`HTMLTableHelper` and `HTMLCrosstabHelper` do not extend `VSTableDataHelper`, so they need their own call. Find where each computes the table's origin and total size and inset both there.

- [ ] **Step 6: Apply it in print layout**

In `VsToReportConverter.addTable`, inset the bounds it passes for the table element, leaving the object format's own bounds at the card. This is the same method Task 7 touched.

- [ ] **Step 7: Verify by hand**

Build and run. For a table, a crosstab and a calc table on a modernized dashboard at comfortable density, export to PDF, PNG, HTML and Print Layout, and confirm for each:

1. The grid is inset 16px from the card border on all four edges.
2. The border and background still draw at the card edge.
3. A table beside a chart has the same visible inset as the chart.
4. An unmarked table is byte-identical to an export from before slice B.

- [ ] **Step 8: Run the core suite**

Run: `./mvnw test -pl core`
Expected: BUILD SUCCESS.

- [ ] **Step 9: Commit**

```bash
git add core/src/main/java/inetsoft/report/io/viewsheet/VSTableDataHelper.java
git add core/src/main/java/inetsoft/report/io/viewsheet/VSTableHelper.java
git add core/src/main/java/inetsoft/report/io/viewsheet/VSCrosstabHelper.java
git add core/src/main/java/inetsoft/report/io/viewsheet/html/HTMLTableHelper.java
git add core/src/main/java/inetsoft/report/io/viewsheet/html/HTMLCrosstabHelper.java
git add core/src/main/java/inetsoft/uql/viewsheet/internal/VsToReportConverter.java
```

```bash
git commit -m "Inset an exported table's grid from its card edge"
```

---

## Task 13: Padding in the three table property dialogs

The second pane, alongside Task 8's Cell Padding.

**Files:**
- Modify: `web/projects/portal/src/app/vsobjects/dialog/table-view-general-pane.component.html`
- Modify: `web/projects/portal/src/app/vsobjects/model/table-view-general-pane-model.ts`
- Modify: `core/src/main/java/inetsoft/web/composer/model/vs/TableViewGeneralPaneModel.java`
- Modify: `core/src/main/java/inetsoft/web/composer/vs/dialog/TableViewPropertyDialogService.java`
- Modify: `core/src/main/java/inetsoft/web/composer/vs/dialog/CrosstabPropertyDialogService.java`
- Modify: `core/src/main/java/inetsoft/web/composer/vs/dialog/CalcTablePropertyDialogService.java`
- Test: `web/projects/portal/src/app/vsobjects/dialog/table-view-general-pane.component.tl.spec.ts` (modify)

**Interfaces:**
- Consumes: `isUserPadding()`, `setUserPadding(boolean)`, `resetPadding(VizContext)` from Task 9.
- Produces: `TableViewGeneralPaneModel.getPaddingPaneModel()` / `setPaddingPaneModel(PaddingPaneModel)`.

- [ ] **Step 1: Write the failing test**

Append to `table-view-general-pane.component.tl.spec.ts`:

```typescript
   it("should set the card inset through the Padding group", async () => {
      const model = markedTableModel();
      await renderPane(model);

      await userEvent.click(screen.getByLabelText(/follow.*default/i, { selector: "#paddingFollowsDefault" }));
      await userEvent.clear(screen.getAllByLabelText("Top")[0]);
      await userEvent.type(screen.getAllByLabelText("Top")[0], "20");

      expect(model.paddingPaneModel.top).toBe(20);
      expect(model.paddingPaneModel.followsDefault).toBe(false);
   });
```

- [ ] **Step 2: Run it to verify it fails**

Run: `npx ng run portal:test-tl --include="**/table-view-general-pane.component.tl.spec.ts"`
Expected: FAIL — only one Padding group renders.

- [ ] **Step 3: Add the model field**

In `TableViewGeneralPaneModel.java`:

```java
   public PaddingPaneModel getPaddingPaneModel() {
      if(paddingPaneModel == null) {
         paddingPaneModel = new PaddingPaneModel();
      }

      return paddingPaneModel;
   }

   public void setPaddingPaneModel(PaddingPaneModel paddingPaneModel) {
      this.paddingPaneModel = paddingPaneModel;
   }
```

and the field `private PaddingPaneModel paddingPaneModel;`. In `table-view-general-pane-model.ts`, add `paddingPaneModel: PaddingPaneModel;`.

- [ ] **Step 4: Render the pane**

In `table-view-general-pane.component.html`, immediately **above** the Cell Padding line added in Task 8:

```html
<padding-pane [model]="model.paddingPaneModel"></padding-pane>
```

No `label` — it takes the default, which is `_#(js:Padding)`.

- [ ] **Step 5: Wire load and apply in all three services**

Load, beside Task 8's cell-padding block:

```java
      PaddingPaneModel paddingPaneModel = tableViewGeneralPaneModel.getPaddingPaneModel();
      Insets padding = tableAssemblyInfo.getPadding();
      paddingPaneModel.setTop(padding.top);
      paddingPaneModel.setLeft(padding.left);
      paddingPaneModel.setBottom(padding.bottom);
      paddingPaneModel.setRight(padding.right);
      paddingPaneModel.setFollowsDefault(
         tableAssemblyInfo.getVizMark() == null ? null : !tableAssemblyInfo.isUserPadding());
```

Apply:

```java
      PaddingPaneModel paddingPaneModel = tableViewGeneralPaneModel.getPaddingPaneModel();
      Insets editedPadding = new Insets(
         paddingPaneModel.getTop(), paddingPaneModel.getLeft(),
         paddingPaneModel.getBottom(), paddingPaneModel.getRight());
      Boolean paddingFollowsDefault = paddingPaneModel.getFollowsDefault();

      if(paddingFollowsDefault == null) {
         // no checkbox was shown, so this table is not marked and the inset is not being
         // resolved; store only a real edit
         if(!editedPadding.equals(tableAssemblyInfo.getPadding())) {
            tableAssemblyInfo.setUserPadding(true);
            tableAssemblyInfo.setPadding(editedPadding);
         }
      }
      else if(paddingFollowsDefault) {
         // clear the opinion and let the default decide, the same shape Revert uses. Storing the
         // legacy inset here would pin zero now that the value is seeded rather than resolved
         tableAssemblyInfo.setUserPadding(false);
         tableAssemblyInfo.resetPadding(VizContext.of(tableAssemblyInfo));
      }
      else {
         tableAssemblyInfo.setUserPadding(true);
         tableAssemblyInfo.setPadding(editedPadding);
      }
```

- [ ] **Step 6: Run the tests**

Run: `npx ng run portal:test-tl --include="**/table-view-general-pane.component.tl.spec.ts"`
Expected: PASS.

Run: `./mvnw test -pl core`
Expected: BUILD SUCCESS.

- [ ] **Step 7: Verify by hand**

Build and run. On a modernized dashboard:
1. Open a table's properties — both Padding and Cell Padding show, both following the default, both with disabled steppers.
2. Uncheck Padding's checkbox, set 24 all round, OK — the grid insets 24px.
3. Reopen, re-check the checkbox, OK — the inset goes back to the density value.
4. Change the dashboard's density — the inset follows, and the author's cell padding, if one was set, does not.

- [ ] **Step 8: Commit**

```bash
git add web/projects/portal/src/app/vsobjects/dialog/table-view-general-pane.component.html
git add web/projects/portal/src/app/vsobjects/dialog/table-view-general-pane.component.tl.spec.ts
git add web/projects/portal/src/app/vsobjects/model/table-view-general-pane-model.ts
git add core/src/main/java/inetsoft/web/composer/model/vs/TableViewGeneralPaneModel.java
git add core/src/main/java/inetsoft/web/composer/vs/dialog/TableViewPropertyDialogService.java
git add core/src/main/java/inetsoft/web/composer/vs/dialog/CrosstabPropertyDialogService.java
git add core/src/main/java/inetsoft/web/composer/vs/dialog/CalcTablePropertyDialogService.java
```

```bash
git commit -m "Let an author set a table's card inset"
```

---

## Before each pull request

Run the whole suite, not the classes you touched:

```bash
./mvnw test -pl core
```

```bash
cd web
npm run test:portal 2>&1 | sed 's/\x1b\[[0-9;]*m//g' | grep -E "Test Files|^ *Tests +[0-9]"
```

That run prints **two** summary blocks — portal's then em's. Read both; piping it through `tail` shows em's numbers and makes portal look like it collected a third of its files.

Then the cross-module build, from the enterprise root:

```bash
./mvnw install -DskipTests -Pcommunity,enterprise
```
