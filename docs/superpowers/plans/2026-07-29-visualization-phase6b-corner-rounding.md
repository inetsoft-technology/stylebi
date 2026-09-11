# Visualization Phase 6B — Corner Rounding Defaults Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make corner rounding a default of modern-visualization mode — newly created charts get rounded bars (`0.3` of bar width) and newly created data/selection assemblies get a 12 px card radius — without altering any saved viewsheet, and reversibly via the existing org gate.

**Architecture:** Both surfaces use one mechanism: a **design-time seed** written at assembly creation (`VSAssemblyInfo.setDefaultFormat` / `ChartVSAssemblyInfo.setDefaultFormat`) when `VSObjectChromeDefaults.isModern()` is true, plus a **gate-aware read** so turning the gate off reverts objects created while it was on. The card radius rides the existing USER/CSS/DEFAULT tier system in `VSCompositeFormat` and needs no new state; `PlotDescriptor` has no tiers so it takes one new `modernCornerSeed` boolean. Rounding itself is already fully implemented on both surfaces — this phase changes defaults only and builds no renderer.

**Tech Stack:** Java 21, Maven (`./mvnw`), JUnit 5 + Spring Test (`@SreeHome`, `@Tag("core")`), all changes in the `community/` submodule `core` module.

## Global Constraints

- **DO NOT COMMIT. DO NOT CREATE BRANCHES.** The repository owner commits this work manually once it is
  complete. Every step in this plan titled "Step N: Commit" is **overridden**: instead of running
  `git commit`, stage the listed files with `git add` and stop there. Never run `git commit`,
  `git checkout -b`, `git switch -c`, `git stash`, `git reset`, or `git clean`. Leave all work in the
  working tree.
- Work happens on the current branch, `viz-updates`, which carries the whole visualization initiative
  including the Phase 6A `VSObjectChromeDefaults` this phase extends. Do not switch branches.
- All changes land in `community/` only — this is a **community-only** change. No enterprise-side change.
- Card corner radius value: **`12`** (px, int). Bar corner radius value: **`0.3`** (double, fraction of bar width, valid range `[0, 0.5]`).
- Single gate for both surfaces: `VSObjectChromeDefaults.isModern()` = `VSDensityDefaults.isModern()` (org-scoped `viewsheet.modernVisualization`) AND not-explicitly-`"false"` `viewsheet.modernObjectChrome`. Do **not** introduce a new property.
- `DEFAULT_BAR_CORNER_RADIUS` stays `0`. The `0.3` value arrives only via the gated seed.
- `barRoundAllCorners` default stays `false`. Do not change the per-chart-type `setRoundAllCorners` calls in `GraphGenerator` — waterfall, gantt and interval already force `true` correctly.
- `nodeCornerRadius` is **out of scope** — it already defaults to `0.3` for new charts, ungated. Do not touch it.
- Never modify `VSFormat`'s binary `writeData()` / `readData()` (`VSFormat.java:1145`, `:1202`) — that is a cluster/hash wire format.
- Line numbers in this plan were verified 2026-07-29. Re-confirm each before editing; the file may have shifted.
- Run `./mvnw test -pl core -Dtest=<TestClass>` from `community/` to run a single test class.
- Follow CLAUDE.md comment style: short clauses, no full-sentence prose, no ticket/PR references in source comments.

## File Structure

| File | Responsibility | Task |
|---|---|---|
| `core/.../uql/viewsheet/internal/VSObjectChromeDefaults.java` | Owns the `12` constant and the gate-strip resolver. Single source of truth for the card radius. | 1 |
| `core/src/test/.../uql/viewsheet/internal/VSObjectChromeDefaultsTest.java` | Resolver + seed tests (extends existing file). | 1, 3 |
| `core/.../uql/viewsheet/VSCompositeFormat.java` | Applies the gate-strip on the DEFAULT tier only, inside the one tier resolver every consumer uses. | 2 |
| `core/src/test/.../uql/viewsheet/VSCompositeFormatRoundCornerGateTest.java` | **New.** Tier-precedence and gate-strip behavior. | 2 |
| `core/.../uql/viewsheet/internal/VSAssemblyInfo.java` | Seeds the card radius at creation; owns the assembly allowlist predicate. | 3 |
| `core/.../uql/viewsheet/graph/PlotDescriptor.java` | Holds `modernCornerSeed` + the gate-aware `getBarCornerRadius()`; XML round-trip. | 4 |
| `core/src/test/.../uql/viewsheet/graph/PlotDescriptorXmlTest.java` | All bar-radius tests. Existing file, but **repaired**: it was untagged (never ran) and lacked the Spring harness `new PlotDescriptor()` requires. | 4 |
| `core/.../uql/viewsheet/internal/ChartVSAssemblyInfo.java` | Seeds the bar radius at chart creation. | 5 |
| `core/src/test/.../uql/viewsheet/internal/ChartVSAssemblyInfoBarRoundingTest.java` | **New.** Chart-creation seed under both gate states. | 5 |
| `core/.../web/graph/model/dialog/ChartPlotOptionsPaneModel.java` | Preserves the seed on a no-op dialog OK; clears it on a real edit. | 6 |
| `core/src/test/.../web/graph/model/dialog/ChartPlotOptionsPaneModelTest.java` | Seed-preservation tests (extends existing file). | 6 |
| `core/.../report/io/viewsheet/AbstractVSExporter.java` | Carries the resolved card radius onto the synthetic export rectangle. | 7 |
| `docs/superpowers/specs/lookfeel/visualization-palette-swatches.html`, `visualization-design-spec.md` | Record `12 px` / `0.3` as named design values. | 8 |

**Dependency order:** 1 → 2 → 3 (card radius complete), 4 → 5 → 6 (bar radius complete), then 7, then 8. Tasks 2 and 4 are independent of each other and can be done in either order after Task 1.

---

### Task 1: Card radius constant and gate-strip resolver

Adds the `12` constant and the resolver that strips it when the gate is off. Pure function, no callers yet — Task 2 wires it in.

**Files:**
- Modify: `core/src/main/java/inetsoft/uql/viewsheet/internal/VSObjectChromeDefaults.java`
- Test: `core/src/test/java/inetsoft/uql/viewsheet/internal/VSObjectChromeDefaultsTest.java`

**Interfaces:**
- Consumes: existing `VSObjectChromeDefaults.isModern()`.
- Produces: `public static int cardCornerRadius()` returning `12`; `public static int resolveSeededCorner(int radius)` returning `0` when `radius == cardCornerRadius() && !isModern()`, else `radius` unchanged.

- [ ] **Step 1: Write the failing tests**

Append these methods inside the existing `VSObjectChromeDefaultsTest` class (it already has the `withGate(String, Runnable)` helper — reuse it, do not redefine it):

```java
   @Test
   void cardCornerRadiusConstant() {
      assertEquals(12, VSObjectChromeDefaults.cardCornerRadius());
   }

   @Test
   void resolveSeededCornerKeepsSeedUnderGate() {
      withGate("true", () -> assertEquals(12, VSObjectChromeDefaults.resolveSeededCorner(12)));
   }

   @Test
   void resolveSeededCornerStripsSeedGateOff() {
      withGate("false", () -> assertEquals(0, VSObjectChromeDefaults.resolveSeededCorner(12)));
   }

   @Test
   void resolveSeededCornerPreservesNonSeedValues() {
      // only the exact seed is gate-owned; any other value is a customer/legacy radius
      withGate("false", () -> {
         assertEquals(8, VSObjectChromeDefaults.resolveSeededCorner(8));
         assertEquals(16, VSObjectChromeDefaults.resolveSeededCorner(16));
         assertEquals(0, VSObjectChromeDefaults.resolveSeededCorner(0));
      });
      withGate("true", () -> assertEquals(8, VSObjectChromeDefaults.resolveSeededCorner(8)));
   }
```

- [ ] **Step 2: Run tests to verify they fail**

Run from `community/`:
```bash
./mvnw test -pl core -Dtest=VSObjectChromeDefaultsTest
```
Expected: FAIL to compile — `cannot find symbol: method cardCornerRadius()` and `resolveSeededCorner(int)`.

- [ ] **Step 3: Write the implementation**

In `VSObjectChromeDefaults.java`, add these methods after the existing `cardBackgroundCss()` method:

```java
   /** Object-card corner radius default, in pixels. Matches the annotation-rectangle radius. */
   public static int cardCornerRadius() {
      return CARD_CORNER_RADIUS;
   }

   /**
    * Gate-strip a DEFAULT-tier corner radius: when the value is our seed and the gate is off, the
    * object reverts to square. Keyed on exact equality with the seed so a format.css TableStyle radius
    * written to the same tier survives. USER and CSS tier values never reach this method.
    */
   public static int resolveSeededCorner(int radius) {
      return radius == CARD_CORNER_RADIUS && !isModern() ? 0 : radius;
   }
```

Add the constant beside the existing color constants at the bottom of the class:

```java
   // modern object-card corner radius, px; = the annotation-rectangle radius
   private static final int CARD_CORNER_RADIUS = 12;
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
./mvnw test -pl core -Dtest=VSObjectChromeDefaultsTest
```
Expected: PASS, all tests in the class including the pre-existing ones.

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/inetsoft/uql/viewsheet/internal/VSObjectChromeDefaults.java \
        core/src/test/java/inetsoft/uql/viewsheet/internal/VSObjectChromeDefaultsTest.java
git commit -m "feat(viz): add modern card corner radius default and gate resolver"
```

---

### Task 2: Gate-aware read on the DEFAULT tier

Wires the Task 1 resolver into `VSCompositeFormat` — the single USER → CSS → DEFAULT resolver that the live model, all export painters, the gauge, tab, HTML and SVG writers already funnel through. Two one-line edits.

**Files:**
- Modify: `core/src/main/java/inetsoft/uql/viewsheet/VSCompositeFormat.java:310-314` and `:321-324`
- Test: `core/src/test/java/inetsoft/uql/viewsheet/VSCompositeFormatRoundCornerGateTest.java` (create)

**Interfaces:**
- Consumes: `VSObjectChromeDefaults.resolveSeededCorner(int)` from Task 1.
- Produces: `VSCompositeFormat.getRoundCorner()` and `getRoundCornerValue()` now gate-strip a DEFAULT-tier value of `12`. USER and CSS tier values pass through untouched. No signature change — every existing caller inherits the behavior.

- [ ] **Step 1: Write the failing test**

Create `core/src/test/java/inetsoft/uql/viewsheet/VSCompositeFormatRoundCornerGateTest.java`:

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
package inetsoft.uql.viewsheet;

import inetsoft.sree.SreeEnv;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The DEFAULT tier of roundCorner is gate-owned: a seeded 12 reverts to square when the modern gate is
 * off. USER and CSS tier values are never gate-stripped.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class VSCompositeFormatRoundCornerGateTest {
   private void withGate(String value, Runnable body) {
      String saved = SreeEnv.getProperty("viewsheet.modernVisualization");

      try {
         SreeEnv.setProperty("viewsheet.modernVisualization", value);
         body.run();
      }
      finally {
         SreeEnv.setProperty("viewsheet.modernVisualization", saved);
      }
   }

   private VSCompositeFormat withDefaultTierRadius(int radius) {
      VSCompositeFormat fmt = new VSCompositeFormat();
      fmt.getDefaultFormat().setRoundCornerValue(radius);
      return fmt;
   }

   @Test
   void defaultTierSeedHonoredUnderGate() {
      withGate("true", () -> {
         VSCompositeFormat fmt = withDefaultTierRadius(12);
         assertEquals(12, fmt.getRoundCorner());
         assertEquals(12, fmt.getRoundCornerValue());
      });
   }

   @Test
   void defaultTierSeedStrippedGateOff() {
      withGate("false", () -> {
         VSCompositeFormat fmt = withDefaultTierRadius(12);
         assertEquals(0, fmt.getRoundCorner(), "seeded card reverts to square when the gate is off");
         assertEquals(0, fmt.getRoundCornerValue(), "format editor shows what is rendered");
      });
   }

   @Test
   void defaultTierNonSeedValuePreservedGateOff() {
      // a format.css TableStyle radius lands on the DEFAULT tier too; only the exact seed is gate-owned
      withGate("false", () -> assertEquals(8, withDefaultTierRadius(8).getRoundCorner()));
   }

   @Test
   void userTierRadiusSurvivesGateOff() {
      withGate("false", () -> {
         VSCompositeFormat fmt = withDefaultTierRadius(12);
         fmt.getUserDefinedFormat().setRoundCornerValue(12);
         assertEquals(12, fmt.getRoundCorner(), "a user-set radius is never gate-stripped");
         assertEquals(12, fmt.getRoundCornerValue());
      });
   }

   @Test
   void userTierRadiusWinsUnderGate() {
      withGate("true", () -> {
         VSCompositeFormat fmt = withDefaultTierRadius(12);
         fmt.getUserDefinedFormat().setRoundCornerValue(4);
         assertEquals(4, fmt.getRoundCorner(), "USER tier beats the DEFAULT-tier seed");
      });
   }

   @Test
   void bareFormatIsSquareInBothGateStates() {
      withGate("true", () -> assertEquals(0, new VSCompositeFormat().getRoundCorner()));
      withGate("false", () -> assertEquals(0, new VSCompositeFormat().getRoundCorner()));
   }
}
```

- [ ] **Step 2: Run the test to verify it fails**

```bash
./mvnw test -pl core -Dtest=VSCompositeFormatRoundCornerGateTest
```
Expected: FAIL on `defaultTierSeedStrippedGateOff` — asserts `0` but gets `12`, because the resolver is not wired in yet. The other tests should already pass.

- [ ] **Step 3: Write the implementation**

In `VSCompositeFormat.java`, `getRoundCorner()` at `:310-314` — wrap **only** the `deffmt` branch:

```java
   public int getRoundCorner() {
      return (userfmt.isRoundCornerDefined() || userfmt.isRoundCornerValueDefined())
         ? userfmt.getRoundCorner()
         : cssfmt.isRoundCornerValueDefined() ? cssfmt.getRoundCornerValue()
         : VSObjectChromeDefaults.resolveSeededCorner(deffmt.getRoundCorner());
   }
```

And `getRoundCornerValue()` at `:321-324`, same substitution on its `deffmt` branch:

```java
   public int getRoundCornerValue() {
      return userfmt.isRoundCornerValueDefined() ? userfmt.getRoundCornerValue() :
         cssfmt.isRoundCornerValueDefined() ? cssfmt.getRoundCornerValue() :
         VSObjectChromeDefaults.resolveSeededCorner(deffmt.getRoundCornerValue());
   }
```

Add the import if not already present:
```java
import inetsoft.uql.viewsheet.internal.VSObjectChromeDefaults;
```

Do **not** touch `isRoundCornerDefined()` (`:415`) or `isRoundCornerValueDefined()` (`:481`) — the seed is a value default, not a definedness assertion, and changing those alters format-painter and copy-format behavior.

- [ ] **Step 4: Run the test to verify it passes**

```bash
./mvnw test -pl core -Dtest=VSCompositeFormatRoundCornerGateTest
```
Expected: PASS, all 6 tests.

- [ ] **Step 5: Run the format regression suites**

```bash
./mvnw test -pl core -Dtest='VSFormat*Test,VSCompositeFormat*Test,FormatPainter*Test,VSObjectModelTest'
```
Expected: PASS. If a test fails asserting a nonzero DEFAULT-tier radius under gate-off, read it carefully — that is the D6 edge case and the test may legitimately need its fixture moved to the USER tier.

- [ ] **Step 6: Commit**

```bash
git add core/src/main/java/inetsoft/uql/viewsheet/VSCompositeFormat.java \
        core/src/test/java/inetsoft/uql/viewsheet/VSCompositeFormatRoundCornerGateTest.java
git commit -m "feat(viz): gate-strip the seeded card corner radius on the DEFAULT tier"
```

---

### Task 3: Seed the card radius at assembly creation

Seeds `12` onto the DEFAULT tier for the allowlisted assembly types when the gate is on. With Task 2 this completes the card-radius half.

**Files:**
- Modify: `core/src/main/java/inetsoft/uql/viewsheet/internal/VSAssemblyInfo.java:1166` and add a predicate method
- Test: `core/src/test/java/inetsoft/uql/viewsheet/internal/VSObjectChromeDefaultsTest.java`

**Interfaces:**
- Consumes: `VSObjectChromeDefaults.cardCornerRadius()` (Task 1); the gate-aware read (Task 2).
- Produces: `private boolean isCornerSeedTarget()` on `VSAssemblyInfo`. After this task, `info.initDefaultFormat()` on an allowlisted type under the gate yields `info.getFormat().getRoundCorner() == 12`.

**Allowlist (seeded):** `TableDataVSAssemblyInfo` (covers Table, Crosstab via `CrossBaseVSAssemblyInfo`, CalcTable, EmbeddedTable), `ChartVSAssemblyInfo`, `SelectionListVSAssemblyInfo`, `SelectionTreeVSAssemblyInfo`, `CurrentSelectionVSAssemblyInfo`.

**Calendar is deliberately NOT in the allowlist.** `CalendarVSAssemblyInfo.initDefaultFormat()` (`:88-92`) overrides the base class and never calls `setDefaultFormat()` — it clones a `static FormatInfo normalDefault` whose object format already hardcodes `setRoundCornerValue(10)` (`:1420`). The seed is therefore unreachable for Calendar. It already renders as a rounded card at 10 px in every org, and Task 2's exact-equality strip leaves that `10` untouched in both gate states, so nothing regresses. Routing Calendar through the seed was rejected: mutating the cloned `FormatInfo` would break the `getFormatInfo().equals(normalDefault)` check at `:1047` that drives an existing reset-to-default path. Accepted outcome: 12 px cards alongside one 10 px calendar.

Everything else is excluded by construction because the predicate is a **positive** list — Gauge, Text, Image, all inputs, RangeSlider (`TimeSliderVSAssemblyInfo`), Tab, GroupContainer, shapes, annotations, PageBreak and embedded Viewsheet need no explicit exclusion clause.

- [ ] **Step 1: Add the required test Spring configuration**

`TableDataVSAssemblyInfo.setDefaultFormat()` calls `LibManagerProvider.getInstance().getManager().getTableStyle(...)` at `:1571`, so instantiating any of the four table types needs a `LibManagerProvider` bean. `VSObjectChromeDefaultsTest`'s context does not register one, and without it the four table assertions below fail with `NoSuchBeanDefinitionException`.

Add `LibManagerTestConfiguration.class` to the class's `@ContextConfiguration`, following the precedent at `VSTableModelTest:50`:

```java
@ContextConfiguration(classes = { BaseTestConfiguration.class, LibManagerTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
```

and add the import:

```java
import inetsoft.test.LibManagerTestConfiguration;
```

This is the **only** permitted change to the class declaration — leave `@ExtendWith`, `@DirtiesContext`, `@SreeHome` and `@Tag` alone.

- [ ] **Step 2: Write the failing tests**

Append to `VSObjectChromeDefaultsTest` (reusing its existing `withGate` helper). Note `GroupContainerVSAssemblyInfo` has **no** no-arg constructor, so it is not instantiated here — its exclusion follows from the positive allowlist:

```java
   private int seededRadius(VSAssemblyInfo info) {
      info.initDefaultFormat();
      return info.getFormat().getRoundCorner();
   }

   @Test
   void cardCornerSeededForDataAndSelectionTypesUnderGate() {
      withGate("true", () -> {
         assertEquals(12, seededRadius(new ChartVSAssemblyInfo()), "chart");
         assertEquals(12, seededRadius(new TableVSAssemblyInfo()), "table");
         assertEquals(12, seededRadius(new CrosstabVSAssemblyInfo()), "crosstab");
         assertEquals(12, seededRadius(new CalcTableVSAssemblyInfo()), "calc table");
         assertEquals(12, seededRadius(new EmbeddedTableVSAssemblyInfo()), "embedded table");
         assertEquals(12, seededRadius(new SelectionListVSAssemblyInfo()), "selection list");
         assertEquals(12, seededRadius(new SelectionTreeVSAssemblyInfo()), "selection tree");
         assertEquals(12, seededRadius(new CurrentSelectionVSAssemblyInfo()), "current selection");
      });
   }

   @Test
   void calendarKeepsItsOwnRadiusInBothGateStates() {
      // CalendarVSAssemblyInfo:88 overrides initDefaultFormat and clones a static template whose
      // object format hardcodes roundCorner=10 (:1420), so the seed never reaches it. 10 survives the
      // gate strip because that keys on exact equality with 12.
      withGate("true", () -> assertEquals(10, seededRadius(new CalendarVSAssemblyInfo())));
      withGate("false", () -> assertEquals(10, seededRadius(new CalendarVSAssemblyInfo())));
   }

   @Test
   void cardCornerNotSeededForExcludedTypesUnderGate() {
      withGate("true", () -> {
         assertEquals(0, seededRadius(new GaugeVSAssemblyInfo()), "gauge stays square");
         assertEquals(0, seededRadius(new TextVSAssemblyInfo()), "text stays square");
         assertEquals(0, seededRadius(new ComboBoxVSAssemblyInfo()), "inputs stay square");
         assertEquals(0, seededRadius(new TimeSliderVSAssemblyInfo()), "range slider stays square");
         assertEquals(0, seededRadius(new RectangleVSAssemblyInfo()), "shapes own their radius");
         // TabVSAssemblyInfo:65 unconditionally sets its own roundCorner of 4; the point is that it is
         // not overwritten by the 12px seed. 4 survives because the strip keys on exact equality with 12.
         assertEquals(4, seededRadius(new TabVSAssemblyInfo()), "tab keeps its own radius, not the seed");
      });
   }

   @Test
   void cardCornerNotSeededGateOff() {
      withGate("false", () -> {
         assertEquals(0, seededRadius(new ChartVSAssemblyInfo()), "chart");
         assertEquals(0, seededRadius(new TableVSAssemblyInfo()), "table");
         assertEquals(0, seededRadius(new SelectionListVSAssemblyInfo()), "selection list");
         assertEquals(0, seededRadius(new CurrentSelectionVSAssemblyInfo()), "current selection");
      });
   }

   @Test
   void cardCornerSeedRevertsWhenGateTurnedOff() {
      TableVSAssemblyInfo info = new TableVSAssemblyInfo();
      withGate("true", () -> info.initDefaultFormat());
      withGate("true", () -> assertEquals(12, info.getFormat().getRoundCorner(), "rounded while on"));
      withGate("false", () -> assertEquals(0, info.getFormat().getRoundCorner(), "square once off"));
   }

   @Test
   void cardCornerUserRadiusSurvivesGateOff() {
      TableVSAssemblyInfo info = new TableVSAssemblyInfo();
      withGate("true", () -> info.initDefaultFormat());
      info.getFormat().getUserDefinedFormat().setRoundCornerValue(6);
      withGate("false", () -> assertEquals(6, info.getFormat().getRoundCorner(),
                                           "a user radius is not gate-stripped"));
   }
```

- [ ] **Step 3: Run tests to verify they fail**

```bash
./mvnw test -pl core -Dtest=VSObjectChromeDefaultsTest
```
Expected: `cardCornerSeededForDataAndSelectionTypesUnderGate`, `cardCornerSeedRevertsWhenGateTurnedOff` and `cardCornerUserRadiusSurvivesGateOff` FAIL (expected `12`, got `0`) — no seed exists yet. The two "not seeded" tests pass already.

- [ ] **Step 4: Write the implementation**

In `VSAssemblyInfo.java`, replace `int borderRadius = 0;` at `:1166` with the gated seed. It **must** stay above the `if(border)` block so the table CSS branch at `:1185-1187` can still overwrite it:

```java
      int borderRadius = VSObjectChromeDefaults.isModern() && isCornerSeedTarget()
         ? VSObjectChromeDefaults.cardCornerRadius() : 0;
```

Add the predicate near the other type checks in the same class:

```java
   /**
    * Whether this assembly type takes the modern card-corner seed. Data and selection surfaces read as
    * cards; outputs, inputs, tabs, containers, shapes and annotations do not. An explicit positive list,
    * not a base-class check — TimeSliderVSAssemblyInfo extends SelectionVSAssemblyInfo and must stay out.
    * Calendar is absent by design: it overrides initDefaultFormat and carries its own radius.
    */
   private boolean isCornerSeedTarget() {
      return this instanceof TableDataVSAssemblyInfo    // table, crosstab, calc table, embedded table
         || this instanceof ChartVSAssemblyInfo
         || this instanceof SelectionListVSAssemblyInfo
         || this instanceof SelectionTreeVSAssemblyInfo
         || this instanceof CurrentSelectionVSAssemblyInfo;
   }
```

All five referenced classes live in the same `inetsoft.uql.viewsheet.internal` package — no imports needed. The base-class-references-subclass shape matches the existing precedent at `:1167` (`this instanceof TableDataVSAssemblyInfo`).

- [ ] **Step 5: Run tests to verify they pass**

```bash
./mvnw test -pl core -Dtest=VSObjectChromeDefaultsTest
```
Expected: PASS, all tests including the pre-existing border/background seed tests.

- [ ] **Step 6: Run the assembly-info and model regression suites**

```bash
./mvnw test -pl core -Dtest='VS*AssemblyInfo*Test,VSObjectModelTest,VSTitleChromeDefaultsTest,VSTableStructureDefaultsTest'
```
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add core/src/main/java/inetsoft/uql/viewsheet/internal/VSAssemblyInfo.java \
        core/src/test/java/inetsoft/uql/viewsheet/internal/VSObjectChromeDefaultsTest.java
git commit -m "feat(viz): seed a 12px card corner radius on new data and selection assemblies"
```

---

### Task 4: Bar radius gate marker and gate-aware getter

Adds `modernCornerSeed` to `PlotDescriptor` with XML round-trip, plus the gate-aware `getBarCornerRadius()` that every existing reader inherits unedited. No seed is written yet — Task 5 does that.

**Files:**
- Modify: `core/src/main/java/inetsoft/uql/viewsheet/graph/PlotDescriptor.java` — field near `:1955-1957`, accessors near `:1295-1307`, `parseAttributes` `:1540-1542`, `writeAttributes` `:1700`, `equalsContent` `:1858`
- Test: `core/src/test/java/inetsoft/uql/viewsheet/graph/PlotDescriptorXmlTest.java` (append gate-independent tests only; do not add annotations)
- Test: (no second test class — the originally planned `PlotDescriptorBarRoundingGateTest` is NOT created; see below)

**Interfaces:**
- Consumes: `VSObjectChromeDefaults.isModern()`.
- Produces on `PlotDescriptor`:
  - `public double getBarCornerRadius()` — effective value; returns `0` when `modernCornerSeed && !VSObjectChromeDefaults.isModern()`.
  - `public double getBarCornerRadiusValue()` — raw stored value, ignores the gate.
  - `public boolean isModernCornerSeed()` / `public void setModernCornerSeed(boolean)`.
  - XML attribute `modernCornerSeed`; absent ⇒ `false`.

**`PlotDescriptorXmlTest` is currently a dead, malformed test class — repair it, then put all new tests in it.**

Two defects compound there, both pre-existing:

1. It carries no `@Tag`. `core/pom.xml:996` configures surefire with `<groups>core</groups>`, so an untagged class is silently excluded from every `mvn test` run and from CI. Its 8 existing tests have never executed. (This is repo-wide: 99 of 592 core test classes are untagged.)
2. It has no Spring harness, but `PlotDescriptor.java:1950` initializes a field with
   `"true".equals(SreeEnv.getProperty("webmap.default"))` — so **`new PlotDescriptor()` cannot be constructed without a Spring-backed `SreeEnv`**. Had the class ever run, all of its tests would have errored. Confirmed: after tagging, 12/12 error.

So a context-free `PlotDescriptor` test is impossible, and the originally planned two-file split has no purpose. Repair the class with the same harness its sibling `PlotDescriptorTextLayoutTest:21-26` already uses, and put **all** new tests — gate-independent and gate-dependent — in that one file. Do not create a second test class.

- [ ] **Step 1a: Repair the test class harness**

Add these imports:

```java
import inetsoft.sree.SreeEnv;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
```

and change the class declaration to match `PlotDescriptorTextLayoutTest:21-26` exactly:

```java
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class PlotDescriptorXmlTest {
```

This makes the class's 8 pre-existing tests execute for the first time. They are expected to pass once `SreeEnv` resolves; if any fails, that is a separate latent defect — report it, do not fix it.

Add the gate helper alongside the existing `roundTrip` helper:

```java
   private void withGate(String value, Runnable body) {
      String saved = SreeEnv.getProperty("viewsheet.modernVisualization");

      try {
         SreeEnv.setProperty("viewsheet.modernVisualization", value);
         body.run();
      }
      finally {
         SreeEnv.setProperty("viewsheet.modernVisualization", saved);
      }
   }
```

- [ ] **Step 1b: Write the failing tests**

Append these methods, reusing the class's existing `roundTrip` helper:

```java
   @Test
   void barCornerRadius_newInstanceStillDefaultsToZero() {
      // the 0.3 value arrives only via the gated seed, never from the field initializer
      assertEquals(0.0, new PlotDescriptor().getBarCornerRadiusValue(), 1e-9);
      assertFalse(new PlotDescriptor().isModernCornerSeed());
   }

   @Test
   void modernCornerSeed_roundTripsTrue() throws Exception {
      PlotDescriptor written = new PlotDescriptor();
      written.setBarCornerRadius(0.3);
      written.setModernCornerSeed(true);

      PlotDescriptor parsed = roundTrip(written);

      assertTrue(parsed.isModernCornerSeed());
      assertEquals(0.3, parsed.getBarCornerRadiusValue(), 1e-9);
   }

   @Test
   void modernCornerSeed_legacyXmlWithoutAttributeIsFalse() throws Exception {
      Document doc = Tool.parseXML(new StringReader("<plotDescriptor/>"));
      PlotDescriptor parsed = new PlotDescriptor();
      parsed.parseXML(doc.getDocumentElement());

      assertFalse(parsed.isModernCornerSeed(),
                  "charts saved before this phase must not look gate-seeded");
      assertEquals(0.0, parsed.getBarCornerRadiusValue(), 1e-9);
   }

   @Test
   void modernCornerSeed_participatesInEqualsContent() {
      PlotDescriptor a = new PlotDescriptor();
      a.setBarCornerRadius(0.3);
      a.setModernCornerSeed(true);

      PlotDescriptor b = new PlotDescriptor();
      b.setBarCornerRadius(0.3);
      b.setModernCornerSeed(false);

      assertFalse(a.equalsContent(b), "a seeded descriptor differs from a user-authored one");
   }
```

`assertFalse` / `assertTrue` come from the existing `import static org.junit.jupiter.api.Assertions.*;` already present in the file.

Then append the two gate-dependent tests to the same class:

```java
   @Test
   void barCornerRadius_seededValueStrippedGateOff() {
      PlotDescriptor pd = new PlotDescriptor();
      pd.setBarCornerRadius(0.3);
      pd.setModernCornerSeed(true);

      withGate("true", () -> assertEquals(0.3, pd.getBarCornerRadius(), 1e-9));
      withGate("false", () -> assertEquals(0.0, pd.getBarCornerRadius(), 1e-9,
                                          "a gate-seeded radius reverts to square"));
      // the raw value is never gate-dependent
      withGate("false", () -> assertEquals(0.3, pd.getBarCornerRadiusValue(), 1e-9));
   }

   @Test
   void barCornerRadius_userValueSurvivesGateOff() {
      PlotDescriptor pd = new PlotDescriptor();
      pd.setBarCornerRadius(0.25);
      pd.setModernCornerSeed(false);

      withGate("false", () -> assertEquals(0.25, pd.getBarCornerRadius(), 1e-9,
                                          "a user-set radius is not gate-stripped"));
   }
```

- [ ] **Step 2: Run the test class to verify it fails**

```bash
./mvnw test -pl core -Dtest=PlotDescriptorXmlTest
```
Expected: FAIL to compile — `cannot find symbol: method isModernCornerSeed()`, `setModernCornerSeed(boolean)`, `getBarCornerRadiusValue()`.

- [ ] **Step 3: Write the implementation**

**Field** — add beside `barCornerRadius` at `:1955-1957`. The comment is load-bearing: it stops a future reader from "simplifying" this to match the ungated siblings two lines below:

```java
   // Gate marker for barCornerRadius: true means modern mode seeded the radius rather than a user
   // setting it, so the gate may take it away again. Deliberately unlike the ungated nodeCornerRadius
   // and smoothLines defaults below — do not collapse this to a plain field default.
   private boolean modernCornerSeed = false;
```

**Accessors** — replace the existing `getBarCornerRadius()` at `:1294-1296` and add the rest. This mirrors the house `getRoundCorner()` vs `getRoundCornerValue()` split:

```java
   /** Effective bar corner radius; a gate-seeded value collapses to 0 when the gate is off. */
   public double getBarCornerRadius() {
      return modernCornerSeed && !VSObjectChromeDefaults.isModern() ? 0 : barCornerRadius;
   }

   /** Raw stored radius, for serialization and content comparison. */
   public double getBarCornerRadiusValue() {
      return barCornerRadius;
   }

   public boolean isModernCornerSeed() {
      return modernCornerSeed;
   }

   public void setModernCornerSeed(boolean modernCornerSeed) {
      this.modernCornerSeed = modernCornerSeed;
   }
```

Add the import:
```java
import inetsoft.uql.viewsheet.internal.VSObjectChromeDefaults;
```

**`parseAttributes()`** — add after the existing `barRoundAllCorners` line at `:1542`. Leave the existing `setBarCornerRadius(...)` `0.0` fallback exactly as it is:

```java
      modernCornerSeed = "true".equals(Tool.getAttribute(node, "modernCornerSeed"));
```

**`writeAttributes()`** — the existing line at `:1700` already writes the raw field, so leave it and add:

```java
      writer.print(" modernCornerSeed=\"" + modernCornerSeed + "\" ");
```

**`equalsContent()`** — add beside the existing `barCornerRadius == desc.barCornerRadius &&` at `:1858`:

```java
         modernCornerSeed == desc.modernCornerSeed &&
```

`clone()` needs no change — both are primitives copied by `super.clone()`.

- [ ] **Step 4: Run tests to verify they pass**

```bash
./mvnw test -pl core -Dtest=PlotDescriptorXmlTest
```
Expected: PASS, including the pre-existing `nodeCornerRadius` and `treeLayout` tests.

- [ ] **Step 5: Run the descriptor and script regression suites**

```bash
./mvnw test -pl core -Dtest='PlotDescriptor*Test,ChartVSAScriptableTest,CSSChartStylesModernChromeTest,ChangeChartTypeService*Test'
```
Expected: PASS. `ChartVSAScriptableTest` matters because `ChartProcessor:248-249` exposes `barCornerRadius` to scripts through the now-gate-aware getter.

- [ ] **Step 6: Commit**

```bash
git add core/src/main/java/inetsoft/uql/viewsheet/graph/PlotDescriptor.java \
        core/src/test/java/inetsoft/uql/viewsheet/graph/PlotDescriptorXmlTest.java \

git commit -m "feat(viz): add gate marker and gate-aware read for bar corner radius"
```

---

### Task 5: Seed the bar radius at chart creation

Writes `0.3` + the marker when a chart is created under the gate. With Task 4 this completes the bar-radius half. `GraphGenerator` needs no edit — its five read sites already call `getBarCornerRadius()`.

**Files:**
- Modify: `core/src/main/java/inetsoft/uql/viewsheet/internal/ChartVSAssemblyInfo.java:86-99` (`setDefaultFormat`)
- Test: `core/src/test/java/inetsoft/uql/viewsheet/internal/ChartVSAssemblyInfoBarRoundingTest.java` (create)

**Interfaces:**
- Consumes: `PlotDescriptor.setBarCornerRadius(double)`, `setModernCornerSeed(boolean)`, `getBarCornerRadius()` (Task 4); `VSObjectChromeDefaults.isModern()`.
- Produces: after `new ChartVSAssemblyInfo().initDefaultFormat()` under the gate, `info.getChartDescriptor().getPlotDescriptor().getBarCornerRadius() == 0.3` and `isModernCornerSeed() == true`.

- [ ] **Step 1: Write the failing test**

Create `core/src/test/java/inetsoft/uql/viewsheet/internal/ChartVSAssemblyInfoBarRoundingTest.java`:

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
import inetsoft.uql.viewsheet.graph.PlotDescriptor;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * New charts created under the modern gate carry a seeded bar corner radius; charts created with the
 * gate off, and charts loaded from saved XML, stay square.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class ChartVSAssemblyInfoBarRoundingTest {
   private void withGate(String value, Runnable body) {
      String saved = SreeEnv.getProperty("viewsheet.modernVisualization");

      try {
         SreeEnv.setProperty("viewsheet.modernVisualization", value);
         body.run();
      }
      finally {
         SreeEnv.setProperty("viewsheet.modernVisualization", saved);
      }
   }

   private PlotDescriptor newChartPlot() {
      ChartVSAssemblyInfo info = new ChartVSAssemblyInfo();
      info.initDefaultFormat();
      return info.getChartDescriptor().getPlotDescriptor();
   }

   @Test
   void barRadiusSeededUnderGate() {
      withGate("true", () -> {
         PlotDescriptor plot = newChartPlot();
         assertEquals(0.3, plot.getBarCornerRadius(), 1e-9);
         assertTrue(plot.isModernCornerSeed(), "seeded value is marked as gate-owned");
      });
   }

   @Test
   void barRadiusNotSeededGateOff() {
      withGate("false", () -> {
         PlotDescriptor plot = newChartPlot();
         assertEquals(0.0, plot.getBarCornerRadius(), 1e-9);
         assertFalse(plot.isModernCornerSeed());
      });
   }

   @Test
   void seededBarRadiusRevertsWhenGateTurnedOff() {
      PlotDescriptor[] holder = new PlotDescriptor[1];
      withGate("true", () -> holder[0] = newChartPlot());

      withGate("false", () -> assertEquals(0.0, holder[0].getBarCornerRadius(), 1e-9,
                                           "a chart made under the gate goes square once it is off"));
      withGate("true", () -> assertEquals(0.3, holder[0].getBarCornerRadius(), 1e-9,
                                          "and rounds again when the gate returns"));
   }

   @Test
   void roundAllCornersStaysOffByDefault() {
      withGate("true", () -> assertFalse(newChartPlot().isBarRoundAllCorners(),
                                         "standard bars round the value end only"));
   }

   @Test
   void legendRoundCornersStillSeeded() {
      // guards the pre-existing new-chart default sitting on the line above the new seed
      withGate("true", () -> {
         ChartVSAssemblyInfo info = new ChartVSAssemblyInfo();
         info.initDefaultFormat();
         assertTrue(info.getChartDescriptor().getLegendsDescriptor().isRoundCorners());
      });
   }
}
```

- [ ] **Step 2: Run the test to verify it fails**

```bash
./mvnw test -pl core -Dtest=ChartVSAssemblyInfoBarRoundingTest
```
Expected: FAIL on `barRadiusSeededUnderGate` (expected `0.3`, got `0.0`) and `seededBarRadiusRevertsWhenGateTurnedOff`. The gate-off and `roundAllCorners` tests pass already.

- [ ] **Step 3: Write the implementation**

In `ChartVSAssemblyInfo.setDefaultFormat()`, add directly after the existing legend round-corners line at `:92`:

```java
      if(VSObjectChromeDefaults.isModern()) {
         PlotDescriptor plotDesc = getChartDescriptor().getPlotDescriptor();
         plotDesc.setBarCornerRadius(0.3);
         plotDesc.setModernCornerSeed(true);
      }
```

Add the import if not already present:
```java
import inetsoft.uql.viewsheet.graph.PlotDescriptor;
```

`VSObjectChromeDefaults` is in the same package — no import needed.

- [ ] **Step 4: Run the test to verify it passes**

```bash
./mvnw test -pl core -Dtest=ChartVSAssemblyInfoBarRoundingTest
```
Expected: PASS, all 5 tests.

- [ ] **Step 5: Enable the geometry coverage this task activates**

This task makes new charts carry a nonzero bar radius for the first time, so the stacked-bar rounding geometry finally matters. Two existing test classes cover exactly that — and neither runs, because they lack `@Tag("core")` and `core/pom.xml:996` filters on `<groups>core</groups>`:

- `core/src/test/java/inetsoft/graph/element/IntervalElementStackOutermostTest.java` — covers `isStackOutermost()`, which drives `BarVO.applyStackRounding()`. Already has `@SreeHome`; add `@Tag("core")` and its import.
- `core/src/test/java/inetsoft/report/composition/graph/GraphTypeUtilCheckTypeTest.java` — covers `GraphTypeUtil.checkType`, which drives `barCornerRadiusVisible`. Has no annotations; add `@Tag("core")` and its import.

Add only the tag to each. If a class then errors for want of a Spring context, give it the same harness `PlotDescriptorTextLayoutTest:21-26` uses, and report that you did. If a test fails on an assertion, that is a latent defect this enabling exposed — report it, do not fix or adjust it.

- [ ] **Step 6: Run the chart-generation regression suite**

```bash
./mvnw test -pl core -Dtest='IntervalElement*Test,GraphRenderTest,VGraphPairModernPaletteTest,GraphTypeUtilCheckTypeTest'
```
Expected: PASS. Confirm from the output how many tests actually executed — `GraphRenderTest` carries a pre-existing `@Disabled`, so one skip there is expected. A `BUILD SUCCESS` with a near-zero executed count means the classes were filtered out, not that they passed.

- [ ] **Step 7: Commit**

```bash
git add core/src/main/java/inetsoft/uql/viewsheet/internal/ChartVSAssemblyInfo.java \
        core/src/test/java/inetsoft/uql/viewsheet/internal/ChartVSAssemblyInfoBarRoundingTest.java
git commit -m "feat(viz): seed rounded bar corners on charts created under the modern gate"
```

---

### Task 6: Preserve the seed on a no-op Plot Options save

`ChartPlotOptionsPaneModel` sends the resolved `0.3` to the client, so a user who opens Plot Options and presses OK without editing would write `0.3` straight back and silently clear the marker — permanently un-gating that chart. Guard on actual change.

**Files:**
- Modify: `core/src/main/java/inetsoft/web/graph/model/dialog/ChartPlotOptionsPaneModel.java:220`
- Test: `core/src/test/java/inetsoft/web/graph/model/dialog/ChartPlotOptionsPaneModelTest.java`

**Interfaces:**
- Consumes: `PlotDescriptor.getBarCornerRadius()`, `setBarCornerRadius(double)`, `setModernCornerSeed(boolean)`, `isModernCornerSeed()` (Task 4).
- Produces: no new API. `updateChartPlotOptionsPaneModel(ChartInfo, PlotDescriptor)` now leaves `modernCornerSeed` intact when the submitted radius equals the currently effective one.

- [ ] **Step 1: Write the failing tests**

**Repair the test class first.** `ChartPlotOptionsPaneModelTest` carries `@SreeHome()` only — no `@Tag("core")` and no Spring context — so, like `PlotDescriptorXmlTest`, it has never executed (44 pre-existing tests). Give it the full harness matching `PlotDescriptorTextLayoutTest:21-26`, since `new PlotDescriptor()` reaches `SreeEnv` at `PlotDescriptor.java:1950`:

```java
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class ChartPlotOptionsPaneModelTest {
```

**These tests DO need the gate toggled.** `PlotDescriptor.getBarCornerRadius()` is gate-aware and `viewsheet.modernVisualization` defaults to `false` under test, so a descriptor with `modernCornerSeed = true` reports a radius of `0` unless the gate is on. Any assertion about a seeded chart's effective radius must therefore run inside `withGate("true", ...)`. Add the same `withGate` helper the other test classes use.

Append to that class:

```java
   private PlotDescriptor seededPlot() {
      PlotDescriptor plotDesc = new PlotDescriptor();
      plotDesc.setBarCornerRadius(0.3);
      plotDesc.setModernCornerSeed(true);
      return plotDesc;
   }

   private ChartPlotOptionsPaneModel roundTripDialog(VSChartInfo info, PlotDescriptor plotDesc) {
      ChartPlotOptionsPaneModel model = new ChartPlotOptionsPaneModel(info, plotDesc);
      model.updateChartPlotOptionsPaneModel(info, plotDesc);
      return model;
   }

   @Test
   void noOpSavePreservesModernCornerSeed() {
      VSChartInfo info = new VSChartInfo();
      info.setChartType(GraphTypes.CHART_BAR);
      PlotDescriptor plotDesc = seededPlot();

      roundTripDialog(info, plotDesc);

      assertTrue(plotDesc.isModernCornerSeed(),
                 "opening the dialog and pressing OK must not un-gate the chart");
      assertEquals(0.3, plotDesc.getBarCornerRadiusValue(), 1e-9);
   }

   @Test
   void editedRadiusClearsModernCornerSeed() {
      VSChartInfo info = new VSChartInfo();
      info.setChartType(GraphTypes.CHART_BAR);
      PlotDescriptor plotDesc = seededPlot();

      ChartPlotOptionsPaneModel model = new ChartPlotOptionsPaneModel(info, plotDesc);
      model.setBarCornerRadius(0.15);
      model.updateChartPlotOptionsPaneModel(info, plotDesc);

      assertFalse(plotDesc.isModernCornerSeed(), "a real edit makes the value user-authored");
      assertEquals(0.15, plotDesc.getBarCornerRadiusValue(), 1e-9);
   }

   @Test
   void clearingRadiusToZeroClearsModernCornerSeed() {
      VSChartInfo info = new VSChartInfo();
      info.setChartType(GraphTypes.CHART_BAR);
      PlotDescriptor plotDesc = seededPlot();

      ChartPlotOptionsPaneModel model = new ChartPlotOptionsPaneModel(info, plotDesc);
      model.setBarCornerRadius(null);
      model.updateChartPlotOptionsPaneModel(info, plotDesc);

      assertFalse(plotDesc.isModernCornerSeed(), "explicitly switching rounding off is a user choice");
      assertEquals(0.0, plotDesc.getBarCornerRadiusValue(), 1e-9);
   }

   @Test
   void seededRadiusIsSentToTheClient() {
      VSChartInfo info = new VSChartInfo();
      info.setChartType(GraphTypes.CHART_BAR);

      ChartPlotOptionsPaneModel model = new ChartPlotOptionsPaneModel(info, seededPlot());

      assertNotNull(model.getBarCornerRadius());
      // .doubleValue() avoids an ambiguous overload between assertEquals(double,double,double)
      // and assertEquals(Object,Object) — the getter returns a boxed Double
      assertEquals(0.3, model.getBarCornerRadius().doubleValue(), 1e-9);
   }
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
./mvnw test -pl core -Dtest=ChartPlotOptionsPaneModelTest
```
Expected: `noOpSavePreservesModernCornerSeed` FAILS — the marker is cleared because the current code writes unconditionally. `editedRadiusClearsModernCornerSeed` and `clearingRadiusToZeroClearsModernCornerSeed` may fail to compile until Task 4 is in place; they pass once it is.

- [ ] **Step 3: Write the implementation**

In `updateChartPlotOptionsPaneModel()`, replace the unconditional write at `:220`:

```java
      plotDesc.setBarCornerRadius(barCornerRadius != null ? barCornerRadius : 0);
```

with a change-guarded write:

```java
      double incomingRadius = barCornerRadius != null ? barCornerRadius : 0;

      // a no-op OK must not turn a gate-seeded radius into a user-authored one
      if(incomingRadius != plotDesc.getBarCornerRadius()) {
         plotDesc.setBarCornerRadius(incomingRadius);
         plotDesc.setModernCornerSeed(false);
      }
```

Leave the `nodeCornerRadius` write at `:239` exactly as it is — that field is ungated and out of scope.

- [ ] **Step 4: Run tests to verify they pass**

```bash
./mvnw test -pl core -Dtest=ChartPlotOptionsPaneModelTest
```
Expected: PASS, all tests including the pre-existing visibility tests.

- [ ] **Step 5: Verify the date-comparison interaction**

`ChartAdvancedPaneModel:161-177` re-applies `barRoundAllCorners` after calling `updateChartPlotOptionsPaneModel`, because design-time `checkType` cannot see DC's runtime bar conversion. Confirm the new guard does not interfere.

**There is no test coverage for this** — neither `ChartAdvancedPaneModel*Test` nor `DateComparison*Test` exists anywhere in the repo, so verify by code trace instead. Read `ChartAdvancedPaneModel:161-177` and confirm that every `plotDesc` mutation in that block targets `barRoundAllCorners` only, never `barCornerRadius` or `modernCornerSeed`. If that holds, the guard cannot interfere, because the two paths touch disjoint fields. Record the conclusion and the lines you read in the report.

- [ ] **Step 6: Commit**

```bash
git add core/src/main/java/inetsoft/web/graph/model/dialog/ChartPlotOptionsPaneModel.java \
        core/src/test/java/inetsoft/web/graph/model/dialog/ChartPlotOptionsPaneModelTest.java
git commit -m "fix(viz): keep the bar-rounding gate marker on a no-op plot options save"
```

---

### Task 7: Carry the chart card radius into export — WITHDRAWN

**This task shipped nothing. Do not implement it.** It was executed, then backed out during the final
whole-branch review; `AbstractVSExporter.java` now diffs to zero against its baseline.

Two reasons it was withdrawn:

1. **The change was dead code.** `writeChartBackgroundShape()` is overridden with an empty body in every
   concrete exporter — `PDFVSExporter:1181`, `SVGVSExporter:761` (inherited by `PNGVSExporter`),
   `HTMLVSExporter:810`, `PoiExcelVSExporter:1860`, `PPTVSExporter:1246` — and the only non-overriding
   subclass, `CSVVSExporter`, emits no visuals at all. The base implementation never executes.
2. **The premise was wrong.** The chart card already rounds in export without any exporter change.
   `VSChart` extends `VSFloatable`, whose `paint()` calls `drawBackground` →
   `ExportUtil.drawBackground(..., format.getRoundCorner())` (`VSFloatable.java:194`) and `drawBorders` →
   `ExportUtil.drawBorders(..., format.getRoundCorner())` (`VSObject.java:329`). Both read the gate-aware
   resolver, so the card follows the gate in every rasterizing exporter.

What survives from this task: the guard test `resolvedRadiusCopiedToUserTierIsNotDoubleStripped` in
`VSCompositeFormatRoundCornerGateTest`, which pins a genuine `VSCompositeFormat` tier-precedence
invariant and is worth keeping on its own merits.

Method error worth recording: the enumeration that "confirmed" this task's premise listed `writeShape`
implementations. The relevant question was whether `writeChartBackgroundShape` itself is overridden. It
is, everywhere. Checking the adjacent method produced a confident and wrong conclusion.

The original task text follows, retained only as a record of what was attempted.

`writeChartBackgroundShape()` builds a synthetic `RectangleVSAssembly` for the chart card and copies background, borders and borderColors — but not the radius. So a rounded chart card renders square in every export format. `VSRectangle:113` already honors `roundCorner`, so one line closes it.

The radius must go on the **USER tier**: the value arrives already gate-resolved from `VSCompositeFormat.getRoundCorner()`, so writing it to the DEFAULT tier would let Task 2's strip run a second time and zero it out.

**Files:**
- Modify: `core/src/main/java/inetsoft/report/io/viewsheet/AbstractVSExporter.java:3574-3590` (`writeChartBackgroundShape`)
- Test: `core/src/test/java/inetsoft/uql/viewsheet/VSCompositeFormatRoundCornerGateTest.java`

**Interfaces:**
- Consumes: `VSCompositeFormat.getRoundCorner()` (gate-resolved, Task 2).
- Produces: no new API. The synthetic export rectangle carries the chart's resolved radius.

`AbstractVSExporter` has 29 abstract methods, so a test double is not worth building. The genuinely subtle part of this change — that a USER-tier radius survives the gate-off strip, which is why the USER tier is the correct target — is unit-tested below. End-to-end rounding is verified manually in Step 5.

- [ ] **Step 1: Write the failing test**

Append to `VSCompositeFormatRoundCornerGateTest` (created in Task 2). This encodes the invariant the export change depends on:

```java
   @Test
   void resolvedRadiusCopiedToUserTierIsNotDoubleStripped() {
      // the export path resolves a radius, then re-applies it to a synthetic rectangle's USER tier;
      // that copy must survive gate-off, otherwise the strip would run twice and zero it
      withGate("false", () -> {
         VSCompositeFormat source = withDefaultTierRadius(12);
         int resolved = source.getRoundCorner();
         assertEquals(0, resolved, "gate off: the chart card itself resolves to square");

         VSCompositeFormat synthetic = new VSCompositeFormat();
         synthetic.getUserDefinedFormat().setRoundCornerValue(resolved);
         assertEquals(0, synthetic.getRoundCorner());
      });

      withGate("true", () -> {
         VSCompositeFormat source = withDefaultTierRadius(12);
         int resolved = source.getRoundCorner();
         assertEquals(12, resolved, "gate on: the chart card resolves to 12");

         VSCompositeFormat synthetic = new VSCompositeFormat();
         synthetic.getUserDefinedFormat().setRoundCornerValue(resolved);
         assertEquals(12, synthetic.getRoundCorner(),
                      "the USER-tier copy is never gate-stripped a second time");
      });
   }
```

- [ ] **Step 2: Run the test to verify it passes for the right reason**

```bash
./mvnw test -pl core -Dtest=VSCompositeFormatRoundCornerGateTest#resolvedRadiusCopiedToUserTierIsNotDoubleStripped
```
Expected: PASS. This one is a guard rather than a red-then-green test — it documents why Step 3 targets the USER tier. If it FAILS, Task 2 was implemented wrongly (the strip is hitting the USER tier) and must be fixed before continuing.

- [ ] **Step 3: Write the implementation**

In `writeChartBackgroundShape()`, add after the existing `setBorderColors(...)` call and before `outerInfo.setPixelOffset(pos)`:

```java
      outerInfo.getFormat().getUserDefinedFormat()
         .setRoundCornerValue(info.getFormat().getRoundCorner());
```

- [ ] **Step 4: Run the export regression suite**

```bash
./mvnw test -pl core -Dtest='VSExportServiceTest,ExportAssetControllerTest'
```
Expected: PASS.

- [ ] **Step 5: Manual export verification**

The one-line change has no unit-test harness, so verify it by hand:

1. Build and start the server per CLAUDE.md (`./mvnw clean install -DskipTests`, then `docker compose up -d` from `docker/target/docker-test`).
2. In Enterprise Manager, set `viewsheet.modernVisualization` to `true` (EM → Settings → Properties).
3. Create a new viewsheet with a bar chart. Confirm in the viewer: rounded bar tops and a 12 px rounded card.
4. Export the viewsheet to **PDF**, **PNG** and **SVG**. Confirm the chart card corners are rounded in each, matching the viewer.
5. Export to **Excel**. Confirm the chart card is rounded but the table/crosstab cards are square — this is the documented, accepted limitation, not a regression.
6. Turn the gate off, reload, and confirm both the viewer and a fresh PDF export show square bars and square cards.

Record the outcome of each step in the commit message or the PR description.

- [ ] **Step 6: Commit**

```bash
git add core/src/main/java/inetsoft/report/io/viewsheet/AbstractVSExporter.java \
        core/src/test/java/inetsoft/uql/viewsheet/VSCompositeFormatRoundCornerGateTest.java
git commit -m "fix(viz): carry the chart card corner radius into viewsheet export"
```

---

### Task 8: Record the values as named design values

`12 px` and `0.3` are currently magic numbers in the code. Record them beside the Phase 6A chrome neutrals so future phases reference a named value rather than re-deriving one.

**Files:**
- Modify: `docs/superpowers/specs/lookfeel/visualization-design-spec.md`
- Modify: `docs/superpowers/specs/lookfeel/visualization-palette-swatches.html`

**Interfaces:**
- Consumes: the constants shipped in Tasks 1 and 5.
- Produces: documentation only. No code depends on this task.

- [ ] **Step 1: Confirm the insertion point**

```bash
grep -n "^## Density System\|^## Surface Guidance By Area" docs/superpowers/specs/lookfeel/visualization-design-spec.md
```
Expected: `## Density System` around line 522 and `## Surface Guidance By Area` around line 608.

Insert the new section **immediately before `## Surface Guidance By Area`** — i.e. at the end of the Density System block. Density is the other server-side geometry default, so the two belong together.

Do **not** put it under `## Token Groups To Define`: that section enumerates browser CSS variable names, and corner radius is a server-side `VSFormat` / descriptor value that never resolves as an `--inet-viz-*` token.

- [ ] **Step 2: Add the corner radius section**

Insert this immediately before the `## Surface Guidance By Area` heading:

```markdown
## Corner Radius Defaults

Modern-mode rounding defaults, seeded at object creation and reverted when the gate is off. Server-side
values, not browser tokens — they resolve identically in the live model and in export. See
[visualization-phase6b-implementation-plan.md](visualization-phase6b-implementation-plan.md).

| Value | Constant | Unit | Applies to |
|---|---|---|---|
| `12` | `VSObjectChromeDefaults.cardCornerRadius()` | px | object-card corners: chart, table, crosstab, calc table, embedded table, selection list/tree/container |
| `0.3` | seeded onto `PlotDescriptor.barCornerRadius` | fraction of bar width (`[0, 0.5]`) | bar marks: bar, pareto, waterfall, gantt, interval |

Outputs, inputs, range slider, tab, group container, shapes and annotations stay square. Tree-chart node
rounding (`PlotDescriptor.nodeCornerRadius`, also `0.3`) predates this phase and is ungated.

Bars round the end that is **not** anchored to a fixed reference: standard bar and pareto keep square
base corners because they sit on the zero baseline, while waterfall, gantt and interval round both ends
because they have no zero anchor. This is the test for any future bar-like chart type.
```

- [ ] **Step 3: Add the radius swatches to the palette page**

Open `visualization-palette-swatches.html`, find the last closing `</section>`, and insert this block before it. Match the surrounding file's existing indentation and class naming — inspect a neighbouring section first and mirror it rather than assuming the markup below is stylistically consistent with the file:

```html
<section>
  <h2>Corner Radius (Phase 6B)</h2>
  <p>
    Modern-mode rounding defaults. Card radius is absolute pixels; bar radius is a fraction of bar
    width, so it scales with the mark.
  </p>
  <div style="display:flex; gap:24px; flex-wrap:wrap; align-items:flex-end;">
    <figure style="margin:0;">
      <div style="width:160px; height:96px; background:#fff; border:1px solid #D9D5CC; border-radius:12px;"></div>
      <figcaption><code>12px</code> — object card</figcaption>
    </figure>
    <figure style="margin:0;">
      <div style="display:flex; gap:8px; align-items:flex-end; height:96px;">
        <div style="width:28px; height:40px; background:#6A685F; border-radius:8px 8px 0 0;"></div>
        <div style="width:28px; height:72px; background:#6A685F; border-radius:8px 8px 0 0;"></div>
        <div style="width:28px; height:56px; background:#6A685F; border-radius:8px 8px 0 0;"></div>
      </div>
      <figcaption><code>0.3</code> of bar width — value end only</figcaption>
    </figure>
  </div>
</section>
```

- [ ] **Step 4: Verify the swatch page still renders**

Open `docs/superpowers/specs/lookfeel/visualization-palette-swatches.html` in a browser. Confirm the new section appears, the existing sections are unchanged, and the page does not scroll horizontally.

- [ ] **Step 5: Commit**

```bash
git add docs/superpowers/specs/lookfeel/visualization-design-spec.md \
        docs/superpowers/specs/lookfeel/visualization-palette-swatches.html
git commit -m "docs(viz): record phase 6B corner radius values as named design values"
```

---

## Final Verification

- [ ] **Full core test suite**

```bash
./mvnw test -pl core
```
Expected: PASS. Trunk-based development means some `main` tests may already be unstable — compare against a pre-change baseline run before attributing a failure to this work.

- [ ] **Full build**

```bash
./mvnw clean install -DskipTests "-Pcommunity,enterprise"
```
Expected: SUCCESS.

- [ ] **Gate-off byte parity check**

With `viewsheet.modernVisualization` unset or `false`, open a viewsheet saved before these changes and confirm it renders identically to a pre-change build — no rounded bars, no rounded cards, in both viewer and PDF export. This is the phase's central guarantee.

- [ ] **Hand off (do not commit or open a PR)**

Leave all changes staged in the working tree on `viz-updates` and report completion. The repository owner
commits and opens any PR. Summarize for them: the files touched, the test commands run and their results,
the Task 7 Step 5 manual export results, and the documented Excel/PDF native-table limitation.

## Deferred (do not implement here)

- Rounding for outputs and inputs (gauge, text, image, combo box, checkbox, radio button, slider, button) — belongs with Phase 7.
- Rounding for range slider, tab, group container — each needs its own geometry reasoning.
- Native **table/crosstab** card rounding in Excel/PDF — not expressible in those formats. Chart cards
  already follow the gate in every rasterizing format via `VSFloatable`, with no exporter change.
- Bringing `nodeCornerRadius`, `smoothLines` or `LegendsDescriptor.roundCorners` under the gate.
- Changing any `setRoundAllCorners` call in `GraphGenerator`.
