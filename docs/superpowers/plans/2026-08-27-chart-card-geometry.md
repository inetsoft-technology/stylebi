# Chart Card Geometry (§04) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give a marked chart §04's card geometry — a 12px card inset and the 4 / 8 / 16 interior gap scale — without moving a single unmarked chart, and with every value an author can still take back.

**Architecture:** Four gap values, resolved at read time against the assembly's seed mark rather than seeded into storage. The card inset resolves behind `ChartVSAssemblyInfo.getPadding()`, so all 17 existing read sites follow untouched; the three graph gaps resolve at the descriptor → spec boundary in `GraphGenerator`, which is the one layer holding both the gaps and a `VizContext`. Nothing new is persisted except one authorship flag, so Revert needs no code and bookmarks are unaffected.

**Tech Stack:** Java 21, JUnit 5 (`@Tag("core")`, Spring `@ContextConfiguration`), Angular 21 + TypeScript, Vitest.

**Spec:** `docs/superpowers/specs/lookfeel/chart-card-geometry-decisions.md` — decisions 1–5. Read it before Task 1; it carries the audit that produced every number below and the three §04 corrections the plan depends on.

## Global Constraints

- **Nothing may change an unmarked chart.** Every resolver returns its stored input unchanged when `info.getVizMark() == null`. This is the seed mark's whole purpose; a diff that reflows legacy content is rejected regardless of test results.
- **Never seed these values into storage.** Do not add any of them to `seedChromeDefaults`. Resolution is read-time; the stored value stays legacy so Revert restores by clearing the mark alone. `VizModernizeUtil:95-97` states the rule.
- **`VizContext` must not reach `inetsoft.graph`.** Zero files in that package reference it today and none may after this plan. The engine stays a consumer of already-resolved values.
- **Author values win.** A value the author set is returned unchanged even on a marked chart. Authorship is recorded by an explicit flag, never inferred by comparing against a constant — that is `resolveSeededCorner`, deleted in P6.
- **Legacy constant values, for the comparison guards:** card inset `new Insets(10, 10, 10, 10)`; axis-title gap `0`; axis-label gap `0` (which `DefaultAxis.getLabelGap()` renders as 2); legend gap `0`.
- **§04 target values:** card inset `12`; axis title → labels `4`; axis labels → plot `8`; plot → legend `16` **total**, of which `VGraph.GAP` contributes a fixed 2, so the resolved descriptor value is **14**. Task 4 carries the comment that records this.
- **Java style:** 3-space indent, `if(` with no space, brace on the same line, javadoc on every public method. Match the surrounding file.
- **Test tag:** every new Java test class carries `@Tag("core")` and the four-annotation Spring preamble copied from `VSDensityDefaultsTest`.

---

## File Structure

**Created**

| File | Responsibility |
|---|---|
| `core/src/test/java/inetsoft/uql/viewsheet/internal/ChartPaddingResolverTest.java` | the card-inset resolver's four states |
| `core/src/test/java/inetsoft/web/composer/vs/dialog/PaddingFollowDefaultTest.java` | the padding pane's read/apply round trip |
| `core/src/test/java/inetsoft/uql/viewsheet/internal/ChartGapResolverTest.java` | the three graph-gap resolvers, including inheritance and max mode |

**Modified**

| File | Change |
|---|---|
| `core/src/main/java/inetsoft/uql/viewsheet/internal/VSObjectChromeDefaults.java` | add `chartPadding(info, stored)` + `MODERN_CARD_INSET` / `LEGACY_CHART_PADDING` |
| `core/src/main/java/inetsoft/uql/viewsheet/internal/ChartVSAssemblyInfo.java` | `getPadding()` override; `isUserPadding()` / `setUserPadding()`; write + parse the flag |
| `core/src/main/java/inetsoft/uql/viewsheet/internal/VSChartChromeDefaults.java` | add `resolveAxisTitleGap`, `resolveAxisLabelGap`, `resolveLegendGap` |
| `core/src/main/java/inetsoft/report/composition/graph/GraphGenerator.java` | route `:2591`, `:6183` and `:1696`/`:1775`/`:1810` through the resolvers |
| `core/src/main/java/inetsoft/web/composer/model/vs/PaddingPaneModel.java` | add `Boolean followsDefault` |
| `core/src/main/java/inetsoft/web/composer/vs/dialog/ChartPropertyDialogService.java` | read and apply the padding flag |
| `web/projects/portal/src/app/vsobjects/model/padding-pane-model.ts` | add `followsDefault?: boolean` |
| `web/projects/portal/src/app/vsobjects/dialog/padding-pane.component.ts` | `showFollowDefault`, `followDefaultChanged`, control enabling |
| `web/projects/portal/src/app/vsobjects/dialog/padding-pane.component.html` | the checkbox |
| `core/src/main/java/inetsoft/web/graph/model/dialog/LegendFormatGeneralPaneModel.java` | add `int gap` + `Boolean gapFollowsDefault` |
| `core/src/main/java/inetsoft/web/graph/model/dialog/LegendFormatDialogModel.java` | read and apply the legend gap, guarded against writing back a resolved value |
| `web/projects/portal/src/app/graph/dialog/legend-format-general-pane.component.ts` + `.html` | the gap stepper and its checkbox |

**Deliberately not modified:** `VGraph.java` (its `GAP = 2` stays a constant — Task 4's comment explains why), `AxisDescriptor`/`TitleDescriptor`/`LegendsDescriptor` (their `labelGap`/`gap` are already `CompositeValue`s and need no change), `seedChromeDefaults` (see Global Constraints), any exporter or painter (they read through `getPadding()` and follow for free).

---

### Task 1: The `userPadding` flag and the card-inset resolver

**Files:**
- Modify: `core/src/main/java/inetsoft/uql/viewsheet/internal/ChartVSAssemblyInfo.java` — `isUserPadding()`/`setUserPadding()`, the field, `writeAttributes`, `parseAttributes`
- Modify: `core/src/main/java/inetsoft/uql/viewsheet/internal/VSObjectChromeDefaults.java`
- Test: `core/src/test/java/inetsoft/uql/viewsheet/internal/ChartPaddingResolverTest.java`

**Interfaces:**
- Consumes: `VizContext.of(VSAssemblyInfo)`, `VSAssemblyInfo.getVizMark()`, `Tool.getAttribute`.
- Produces: `ChartVSAssemblyInfo.isUserPadding()` / `setUserPadding(boolean)`, and
  `public static Insets VSObjectChromeDefaults.chartPadding(ChartVSAssemblyInfo info, Insets stored)`. Tasks 2 and 3 call both. The resolver never returns null when `stored` is non-null, and never returns the same mutable `Insets` the caller passed when it substitutes.

The flag ships with the resolver rather than after it: the resolver reads `isUserPadding()`, so it does not compile without it, and a reviewer could not accept one without the other.

This resolver is deliberately shaped like `VSDensityDefaults.titleHeight(T info, int stored)` (`:119-126`): the stored value arrives as a parameter so a composer dialog can pass its design-time value and still see the substitution, and the three cheap tests run before a `VizContext` is built.

- [ ] **Step 1: Write the failing test**

Create `core/src/test/java/inetsoft/uql/viewsheet/internal/ChartPaddingResolverTest.java`:

```java
/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.Insets;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class ChartPaddingResolverTest {
   @Test
   void unmarkedKeepsTheStoredInset() {
      ChartVSAssemblyInfo info = new ChartVSAssemblyInfo();
      Insets stored = new Insets(10, 10, 10, 10);

      assertEquals(stored, VSObjectChromeDefaults.chartPadding(info, stored),
                   "an unmarked chart must not move");
   }

   @Test
   void markedAtTheLegacyDefaultTakesTheCardInset() {
      ChartVSAssemblyInfo info = new ChartVSAssemblyInfo();
      info.setVizMark(VizMark.MODERN_LIGHT);

      assertEquals(new Insets(12, 12, 12, 12),
                   VSObjectChromeDefaults.chartPadding(info, new Insets(10, 10, 10, 10)));
   }

   @Test
   void markedWithAnAuthorInsetKeepsIt() {
      ChartVSAssemblyInfo info = new ChartVSAssemblyInfo();
      info.setVizMark(VizMark.MODERN_LIGHT);
      info.setUserPadding(true);

      Insets stored = new Insets(4, 4, 4, 4);
      assertEquals(stored, VSObjectChromeDefaults.chartPadding(info, stored),
                   "the author's value wins on a marked chart");
   }

   @Test
   void markedWithAStoredInsetOffTheLegacyDefaultKeepsIt() {
      // the belt behind the flag: content saved before the flag existed carries no opinion, so a
      // stored value that is not the legacy default is treated as deliberate
      ChartVSAssemblyInfo info = new ChartVSAssemblyInfo();
      info.setVizMark(VizMark.MODERN_LIGHT);

      Insets stored = new Insets(3, 3, 3, 3);
      assertEquals(stored, VSObjectChromeDefaults.chartPadding(info, stored));
   }

   @Test
   void aSubstitutionDoesNotAliasTheStoredInsets() {
      // Insets is mutable and the stored one belongs to the assembly; a caller must never be handed
      // an object that writes back into storage
      ChartVSAssemblyInfo info = new ChartVSAssemblyInfo();
      info.setVizMark(VizMark.MODERN_LIGHT);
      Insets stored = new Insets(10, 10, 10, 10);

      Insets resolved = VSObjectChromeDefaults.chartPadding(info, stored);
      resolved.left = 99;

      assertEquals(10, stored.left, "the stored insets were mutated through the resolved copy");
   }

   @Test
   void nullStoredIsReturnedUnchanged() {
      ChartVSAssemblyInfo info = new ChartVSAssemblyInfo();
      info.setVizMark(VizMark.MODERN_LIGHT);

      assertNull(VSObjectChromeDefaults.chartPadding(info, null));
   }
}
```

Add one more test, for the flag's own persistence:

```java
   @Test
   void theFlagSurvivesARoundTrip() throws Exception {
      ChartVSAssemblyInfo info = new ChartVSAssemblyInfo();
      info.initDefaultFormat();
      info.setUserPadding(true);

      java.io.StringWriter buf = new java.io.StringWriter();
      java.io.PrintWriter writer = new java.io.PrintWriter(buf);
      info.writeXML(writer);
      writer.flush();

      ChartVSAssemblyInfo restored = new ChartVSAssemblyInfo();
      restored.parseXML(Tool.parseXML(new java.io.StringReader(buf.toString()))
                           .getDocumentElement());

      assertTrue(restored.isUserPadding());
   }

   @Test
   void anAbsentFlagAttributeMeansNoOpinion() {
      // content saved before the flag existed: parse must not invent an author
      ChartVSAssemblyInfo info = new ChartVSAssemblyInfo();

      assertFalse(info.isUserPadding());
   }
```

Add `import inetsoft.util.Tool;` to the test's imports.

- [ ] **Step 2: Run the test to verify it fails**

Run: `.\mvnw.cmd test -pl core -Dtest=ChartPaddingResolverTest -DfailIfNoTests=false`
Expected: compile failure — neither `chartPadding` nor `isUserPadding`/`setUserPadding` exists.

- [ ] **Step 3: Add the flag**

In `ChartVSAssemblyInfo.java`, next to the existing `isUserTitleHeight()` delegation (`:2744-2747`):

```java
   /**
    * Whether the author set the chart's padding. Distinguishes a deliberate inset from the creation
    * default, so the card-inset resolver can substitute for the latter only. Surfaced in the
    * property dialog as the padding pane's follow-the-default checkbox.
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

and with the class's other fields:

```java
   private boolean userPadding = false;
```

In `writeAttributes(PrintWriter writer)`, alongside the existing chart attributes:

```java
      writer.print(" userPadding=\"" + isUserPadding() + "\"");
```

In `parseAttributes(Element elem)`:

```java
      // absent in files saved before the flag existed; a missing flag means no opinion, and the
      // resolver's comparison against the creation default decides
      String userPaddingProp = Tool.getAttribute(elem, "userPadding");
      setUserPadding("true".equalsIgnoreCase(userPaddingProp));
```

- [ ] **Step 4: Add the resolver**

In `VSObjectChromeDefaults.java`, after `cardCornerRadius()` (`:58-60`):

```java
   /**
    * The card inset for one chart: §04's 12px when the assembly is marked, its author has not set a
    * padding, and the stored padding is still the creation default; otherwise the stored padding
    * unchanged. The stored value is a parameter so a composer dialog can pass its design-time value
    * and still get the substitution.
    *
    * Read-time only. Nothing seeds this, so clearing the mark restores the legacy inset with no
    * reverser and no migration, and the value stays out of the bookmark (it lives in
    * writeAttributes, which writeState does not reach).
    *
    * The two cheap tests run before the context is built - VizContext reads the density property,
    * and an unmarked or author-set assembly must not pay for that.
    */
   public static Insets chartPadding(ChartVSAssemblyInfo info, Insets stored) {
      if(info == null || info.getVizMark() == null || info.isUserPadding() ||
         !LEGACY_CHART_PADDING.equals(stored))
      {
         return stored;
      }

      // a fresh object every call: Insets is mutable and the stored one belongs to the assembly
      return new Insets(MODERN_CARD_INSET, MODERN_CARD_INSET, MODERN_CARD_INSET,
                        MODERN_CARD_INSET);
   }
```

and with the other private constants at the foot of the class:

```java
   // the chart's creation-default padding (ChartVSAssemblyInfo.setDefaultFormat), which is what the
   // card inset resolver treats as "no opinion"
   private static final Insets LEGACY_CHART_PADDING = new Insets(10, 10, 10, 10);
   // modern card inset, px; = --inet-space-5. One value governs all four edges: the title lane, the
   // axis title and the legend column add no edge padding of their own.
   private static final int MODERN_CARD_INSET = 12;
```

Add `import java.awt.Insets;` if it is not already present.

- [ ] **Step 5: Run the test to verify it passes**

Run: `.\mvnw.cmd test -pl core -Dtest=ChartPaddingResolverTest -DfailIfNoTests=false`
Expected: PASS, eight tests. Nothing reads the resolver yet — Task 2 wires it in — so no other suite can be affected by this task.

- [ ] **Step 6: Commit**

```bash
git add core/src/main/java/inetsoft/uql/viewsheet/internal/VSObjectChromeDefaults.java core/src/main/java/inetsoft/uql/viewsheet/internal/ChartVSAssemblyInfo.java core/src/test/java/inetsoft/uql/viewsheet/internal/ChartPaddingResolverTest.java
git commit -m "feat(viewsheet): resolve a marked chart's card inset from its mark"
```

---

### Task 2: The `getPadding()` override — 17 read sites in one line

**Files:**
- Modify: `core/src/main/java/inetsoft/uql/viewsheet/internal/ChartVSAssemblyInfo.java` — the `getPadding()` override, near the other overrides
- Test: `core/src/test/java/inetsoft/uql/viewsheet/internal/ChartPaddingResolverTest.java` (extend)

**Interfaces:**
- Consumes: `VSObjectChromeDefaults.chartPadding(ChartVSAssemblyInfo, Insets)` and `isUserPadding()` from Task 1; `super.getPadding()`, the stored-field accessor at `VSAssemblyInfo:1371`.
- Produces: an overridden `ChartVSAssemblyInfo.getPadding()` returning the resolved inset. Task 3 relies on it. `getPaddingValue()` is deliberately **not** added: `VSAssemblyInfo`'s own `writeAttributes`/`parseAttributes`/`clone`/`copyViewInfo` all read the private field directly, so persistence already bypasses the override and needs no separate accessor.

This is the task that can break things, and the only one whose blast radius is the whole product: one override changes what 17 call sites see. Step 5's suite run is its real gate, not Step 4's.

Why the override alone covers 17 read sites: `VGraphPair:282-285` and `:2946`, six sites in `AbstractVSExporter`, two in `HTMLVSExporter`, `PDFVSExporter:239`, `SVGVSExporter:145-146`, `AnnotationVSUtil:1187`, two in `VsToReportConverter`, and `VSChartModel:75-78` all call `getPadding()` on a receiver that is a `ChartVSAssemblyInfo` at runtime. Virtual dispatch does the rest. Gauges call the same getter but are `GaugeVSAssemblyInfo`, so they are untouched — that is intended, not an omission.

- [ ] **Step 1: Write the failing tests**

Append to `ChartPaddingResolverTest`:

```java
   @Test
   void theGetterResolvesForAMarkedChart() {
      ChartVSAssemblyInfo info = new ChartVSAssemblyInfo();
      info.initDefaultFormat();
      info.setVizMark(VizMark.MODERN_LIGHT);

      assertEquals(new Insets(12, 12, 12, 12), info.getPadding(),
                   "every read site follows through the getter");
   }

   @Test
   void theGetterIsLegacyForAnUnmarkedChart() {
      ChartVSAssemblyInfo info = new ChartVSAssemblyInfo();
      info.initDefaultFormat();

      assertEquals(new Insets(10, 10, 10, 10), info.getPadding());
   }

   @Test
   void persistenceCarriesTheStoredInsetAndNotTheResolvedOne() throws Exception {
      ChartVSAssemblyInfo info = new ChartVSAssemblyInfo();
      info.initDefaultFormat();
      info.setVizMark(VizMark.MODERN_LIGHT);

      ChartVSAssemblyInfo restored = roundTrip(info);

      assertEquals(new Insets(12, 12, 12, 12), restored.getPadding(),
                   "the restored chart still resolves");
      assertFalse(restored.isUserPadding(),
                  "a resolved value must never be written back as the author's");
   }

   @Test
   void anAuthorInsetSurvivesARoundTripAndIsNotResolvedAway() throws Exception {
      ChartVSAssemblyInfo info = new ChartVSAssemblyInfo();
      info.initDefaultFormat();
      info.setVizMark(VizMark.MODERN_LIGHT);
      info.setUserPadding(true);
      info.setPadding(new Insets(4, 4, 4, 4));

      ChartVSAssemblyInfo restored = roundTrip(info);

      assertTrue(restored.isUserPadding());
      assertEquals(new Insets(4, 4, 4, 4), restored.getPadding(),
                   "the getter must not substitute over an author's inset");
   }

   @Test
   void contentSavedBeforeTheFlagExistedCarriesNoOpinion() throws Exception {
      // the attribute is absent, so the comparison guard decides: a legacy-default inset resolves,
      // anything else is left alone
      ChartVSAssemblyInfo info = new ChartVSAssemblyInfo();
      info.initDefaultFormat();
      info.setVizMark(VizMark.MODERN_LIGHT);

      String xml = write(info).replaceAll(" userPadding=\"[a-z]*\"", "");
      ChartVSAssemblyInfo restored = new ChartVSAssemblyInfo();
      restored.parseXML(Tool.parseXML(new java.io.StringReader(xml)).getDocumentElement());

      assertFalse(restored.isUserPadding());
      assertEquals(new Insets(12, 12, 12, 12), restored.getPadding());
   }

   private static String write(ChartVSAssemblyInfo info) {
      java.io.StringWriter buf = new java.io.StringWriter();
      java.io.PrintWriter writer = new java.io.PrintWriter(buf);
      info.writeXML(writer);
      writer.flush();
      return buf.toString();
   }

   private static ChartVSAssemblyInfo roundTrip(ChartVSAssemblyInfo info) throws Exception {
      ChartVSAssemblyInfo restored = new ChartVSAssemblyInfo();
      restored.parseXML(Tool.parseXML(new java.io.StringReader(write(info))).getDocumentElement());
      restored.setVizMark(info.getVizMark());
      return restored;
   }
```

Add `import inetsoft.util.Tool;` to the test's imports.

- [ ] **Step 2: Run the tests to verify they fail**

Run: `.\mvnw.cmd test -pl core -Dtest=ChartPaddingResolverTest -DfailIfNoTests=false`
Expected: FAIL. `theGetterResolvesForAMarkedChart` reports 10 where 12 is expected, and `contentSavedBeforeTheFlagExistedCarriesNoOpinion` likewise — the getter is still `VSAssemblyInfo`'s. Task 1's tests stay green.

- [ ] **Step 3: Add the override**

In `ChartVSAssemblyInfo.java`, beside the `isUserPadding()` accessors Task 1 added:

```java
   /**
    * The card inset, resolved against the assembly's mark. Overridden rather than resolved at each
    * read site so the exporters, the report converter, the annotation placement and the browser
    * model all follow untouched. Persistence is unaffected: VSAssemblyInfo's writeAttributes,
    * parseAttributes, clone and copyViewInfo all use the field, not this getter.
    */
   @Override
   public Insets getPadding() {
      return VSObjectChromeDefaults.chartPadding(this, super.getPadding());
   }
```

Add `import java.awt.Insets;` if the file does not already have it.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `.\mvnw.cmd test -pl core -Dtest=ChartPaddingResolverTest -DfailIfNoTests=false`
Expected: PASS, all eleven tests.

- [ ] **Step 5: Run the neighbouring suites that read padding**

Run: `.\mvnw.cmd test -pl core -Dtest='SeedChromeDefaults*,VizContext*,VizModernizeUtil*,VSObjectChromeDefaults*,VSDensityDefaults*' -DfailIfNoTests=false`
Expected: PASS. Any failure here means the override reached a path that expected the stored value — investigate before continuing, do not adjust the test.

- [ ] **Step 6: Commit**

```bash
git add core/src/main/java/inetsoft/uql/viewsheet/internal/ChartVSAssemblyInfo.java core/src/test/java/inetsoft/uql/viewsheet/internal/ChartPaddingResolverTest.java
git commit -m "feat(viewsheet): read a chart's card inset through the mark-gated resolver"
```

---

### Task 3: The padding pane's follow-the-default checkbox

**Files:**
- Modify: `core/src/main/java/inetsoft/web/composer/model/vs/PaddingPaneModel.java`
- Modify: `core/src/main/java/inetsoft/web/composer/vs/dialog/ChartPropertyDialogService.java:147-150` (read) and `:390-393` (apply)
- Modify: `web/projects/portal/src/app/vsobjects/model/padding-pane-model.ts`
- Modify: `web/projects/portal/src/app/vsobjects/dialog/padding-pane.component.ts`
- Modify: `web/projects/portal/src/app/vsobjects/dialog/padding-pane.component.html`
- Test: `core/src/test/java/inetsoft/web/composer/vs/dialog/PaddingFollowDefaultTest.java`

**Interfaces:**
- Consumes: `ChartVSAssemblyInfo.isUserPadding()`/`setUserPadding(boolean)` and the resolving `getPadding()` from Task 2.
- Produces: `PaddingPaneModel.getFollowsDefault()`/`setFollowsDefault(Boolean)` (Java) and `PaddingPaneModel.followsDefault?: boolean` (TS). Nothing later depends on these.

The three-branch apply below is copied from the title-height row at `ChartPropertyDialogService:415-430` — same tri-state contract: `null` means the checkbox was not shown (unmarked chart), so fall back to comparing what came back against what is stored.

**The defect this task exists to prevent:** `:390-393` today writes `setPadding(...)` unconditionally on every Apply, whatever tab the author touched. With Task 2 in place the pane is shown the *resolved* 12, so an unchanged Apply would store 12 and stamp it as the author's — silently converting a resolved value into a stored one. The `followsDefault == null` branch is what stops that.

- [ ] **Step 1: Write the failing test**

Create `core/src/test/java/inetsoft/web/composer/vs/dialog/PaddingFollowDefaultTest.java` with the same licence header and Spring preamble as `TitleHeightFollowDensityTest`:

```java
package inetsoft.web.composer.vs.dialog;

import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.web.composer.model.vs.PaddingPaneModel;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.Insets;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class PaddingFollowDefaultTest {
   @Test
   void readSendsTheResolvedInsetAndTheFlag() {
      ChartVSAssemblyInfo info = new ChartVSAssemblyInfo();
      info.initDefaultFormat();
      info.setVizMark(VizMark.MODERN_LIGHT);

      PaddingPaneModel model = read(info);

      assertEquals(12, model.getTop(), "the dialog shows the resolved card inset");
      assertTrue(model.getFollowsDefault());
   }

   @Test
   void readSendsNoFlagForAnUnmarkedChart() {
      ChartVSAssemblyInfo info = new ChartVSAssemblyInfo();
      info.initDefaultFormat();

      PaddingPaneModel model = read(info);

      assertEquals(10, model.getTop());
      assertNull(model.getFollowsDefault(), "no checkbox on legacy content");
   }

   @Test
   void anUnchangedApplyDoesNotStampTheResolvedValue() {
      // the defect this guards: the pane was shown 12, the author changed nothing, and Apply must
      // not turn that 12 into a stored author value
      ChartVSAssemblyInfo info = new ChartVSAssemblyInfo();
      info.initDefaultFormat();
      info.setVizMark(VizMark.MODERN_LIGHT);

      apply(read(info), info);

      assertFalse(info.isUserPadding());
      assertEquals(new Insets(12, 12, 12, 12), info.getPadding(),
                   "still resolving, not pinned");
   }

   @Test
   void clearingTheCheckboxPinsWhatThePaneShows() {
      ChartVSAssemblyInfo info = new ChartVSAssemblyInfo();
      info.initDefaultFormat();
      info.setVizMark(VizMark.MODERN_LIGHT);

      PaddingPaneModel model = read(info);
      model.setFollowsDefault(false);
      model.setTop(4);
      model.setLeft(4);
      model.setBottom(4);
      model.setRight(4);
      apply(model, info);

      assertTrue(info.isUserPadding());
      assertEquals(new Insets(4, 4, 4, 4), info.getPadding());
   }

   @Test
   void tickingTheCheckboxRestoresTheLegacyStoredInsetAndResolvesAgain() {
      ChartVSAssemblyInfo info = new ChartVSAssemblyInfo();
      info.initDefaultFormat();
      info.setVizMark(VizMark.MODERN_LIGHT);
      info.setUserPadding(true);
      info.setPadding(new Insets(4, 4, 4, 4));

      PaddingPaneModel model = read(info);
      model.setFollowsDefault(true);
      apply(model, info);

      assertFalse(info.isUserPadding(), "the flag is cleared");
      assertEquals(new Insets(12, 12, 12, 12), info.getPadding(),
                   "the pane now resolves the card inset");
   }

   @Test
   void anUnmarkedChartStillAcceptsAnEditedInset() {
      ChartVSAssemblyInfo info = new ChartVSAssemblyInfo();
      info.initDefaultFormat();

      PaddingPaneModel model = read(info);
      model.setTop(6);
      model.setLeft(6);
      model.setBottom(6);
      model.setRight(6);
      apply(model, info);

      assertEquals(new Insets(6, 6, 6, 6), info.getPadding());
   }

   /** Mirrors ChartPropertyDialogService's read of the padding pane. */
   private static PaddingPaneModel read(ChartVSAssemblyInfo info) {
      PaddingPaneModel model = new PaddingPaneModel();
      Insets padding = info.getPadding();
      model.setTop(padding.top);
      model.setLeft(padding.left);
      model.setBottom(padding.bottom);
      model.setRight(padding.right);
      model.setFollowsDefault(info.getVizMark() == null ? null : !info.isUserPadding());
      return model;
   }

   /** Mirrors ChartPropertyDialogService's apply of the padding pane. */
   private static void apply(PaddingPaneModel model, ChartVSAssemblyInfo info) {
      Insets edited = new Insets(model.getTop(), model.getLeft(), model.getBottom(),
                                 model.getRight());
      Boolean followsDefault = model.getFollowsDefault();

      if(followsDefault == null) {
         if(!edited.equals(info.getPadding())) {
            info.setUserPadding(true);
            info.setPadding(edited);
         }
      }
      else if(followsDefault) {
         info.setUserPadding(false);
         info.setPadding(new Insets(10, 10, 10, 10));
      }
      else {
         info.setUserPadding(true);
         info.setPadding(edited);
      }
   }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `.\mvnw.cmd test -pl core -Dtest=PaddingFollowDefaultTest -DfailIfNoTests=false`
Expected: compile failure on `getFollowsDefault` / `setFollowsDefault`.

- [ ] **Step 3: Add the model field**

In `PaddingPaneModel.java`, before the private fields:

```java
   /**
    * Whether the padding follows the modern card inset. Null on an unmarked assembly, where there is
    * no default to follow and the checkbox is not shown.
    */
   public Boolean getFollowsDefault() {
      return followsDefault;
   }

   public void setFollowsDefault(Boolean followsDefault) {
      this.followsDefault = followsDefault;
   }
```

and with the fields:

```java
   private Boolean followsDefault;
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `.\mvnw.cmd test -pl core -Dtest=PaddingFollowDefaultTest -DfailIfNoTests=false`
Expected: PASS, six tests. The test carries its own copies of the read and apply logic, so it passes before the service is wired; Step 5 makes the service match it.

- [ ] **Step 5: Wire the service to match**

In `ChartPropertyDialogService.java`, replace `:147-150`:

```java
      Insets chartPadding = chartAssemblyInfo.getPadding();
      paddingPaneModel.setTop(chartPadding.top);
      paddingPaneModel.setLeft(chartPadding.left);
      paddingPaneModel.setBottom(chartPadding.bottom);
      paddingPaneModel.setRight(chartPadding.right);
      paddingPaneModel.setFollowsDefault(
         chartAssemblyInfo.getVizMark() == null ? null : !chartAssemblyInfo.isUserPadding());
```

and replace `:390-393`:

```java
      Insets editedPadding = new Insets(paddingPaneModel.getTop(), paddingPaneModel.getLeft(),
                                        paddingPaneModel.getBottom(), paddingPaneModel.getRight());
      Boolean paddingFollowsDefault = paddingPaneModel.getFollowsDefault();

      if(paddingFollowsDefault == null) {
         // no checkbox was shown, so an unchanged pane must not be written back: the getter is
         // resolving, and storing what it returned would pin it as the author's value
         if(!editedPadding.equals(assemblyInfo.getPadding())) {
            assemblyInfo.setUserPadding(true);
            assemblyInfo.setPadding(editedPadding);
         }
      }
      else if(paddingFollowsDefault) {
         assemblyInfo.setUserPadding(false);
         assemblyInfo.setPadding(new Insets(10, 10, 10, 10));
      }
      else {
         assemblyInfo.setUserPadding(true);
         assemblyInfo.setPadding(editedPadding);
      }
```

- [ ] **Step 6: Add the frontend field and checkbox**

In `padding-pane-model.ts`:

```typescript
export interface PaddingPaneModel {
   top: number;
   left: number;
   bottom: number;
   right: number;
   followsDefault?: boolean;
}
```

In `padding-pane.component.ts`, add the field and handler, and set the flag in `ngOnInit` before `initForm()`:

```typescript
   showFollowDefault: boolean;

   followDefaultChanged(follows: boolean): void {
      this.model.followsDefault = follows;
      this.setEnabled(follows);
   }

   private setEnabled(follows: boolean): void {
      for(const name of ["top", "left", "bottom", "right"]) {
         const control = this.form.controls[name];

         if(control) {
            follows ? control.disable() : control.enable();
         }
      }
   }
```

In `ngOnInit`, after `initForm()`:

```typescript
      this.showFollowDefault = this.model.followsDefault != null;
      this.setEnabled(this.model.followsDefault === true);
```

In `padding-pane.component.html`, immediately before the closing `</fieldset>`:

```html
@if (showFollowDefault) {
  <div class="row shell-form-row--field">
    <div class="col">
      <div class="form-check">
        <input type="checkbox" class="form-check-input" id="paddingFollowsDefault"
               [ngModel]="model.followsDefault"
               (ngModelChange)="followDefaultChanged($event)"
               [ngModelOptions]="{standalone: true}">
        <label class="form-check-label" for="paddingFollowsDefault">
          _#(composer.vs.followDefaultDensity)
        </label>
      </div>
    </div>
  </div>
}
```

`composer.vs.followDefaultDensity` already exists — it is the string the title-height and cell-height checkboxes use (`size-position-pane.component.html:84,106`). Do not add a new key.

- [ ] **Step 7: Verify the frontend builds and the portal suite is green**

Run, from `web/`: `npm run lint` then `npm run test:portal`
Expected: lint clean; portal suite green at its current count. There is no existing `padding-pane` spec, so no test is added here — the checkbox's contract is covered server-side by Step 1, and adding a first-ever spec for this component is out of scope for this plan.

- [ ] **Step 8: Commit**

```bash
git add core/src/main/java/inetsoft/web/composer/model/vs/PaddingPaneModel.java core/src/main/java/inetsoft/web/composer/vs/dialog/ChartPropertyDialogService.java core/src/test/java/inetsoft/web/composer/vs/dialog/PaddingFollowDefaultTest.java web/projects/portal/src/app/vsobjects/model/padding-pane-model.ts web/projects/portal/src/app/vsobjects/dialog/padding-pane.component.ts web/projects/portal/src/app/vsobjects/dialog/padding-pane.component.html
git commit -m "feat(composer): let a chart's padding follow the card inset, or leave it"
```

---

### Task 4: The three graph-gap resolvers

**Files:**
- Modify: `core/src/main/java/inetsoft/uql/viewsheet/internal/VSChartChromeDefaults.java`
- Test: `core/src/test/java/inetsoft/uql/viewsheet/internal/ChartGapResolverTest.java`

**Interfaces:**
- Consumes: `VizContext.modern`.
- Produces, all on `VSChartChromeDefaults`:
  - `public static int resolveAxisTitleGap(int current, boolean abuttingLabelsDrawn, VizContext ctx)`
  - `public static int resolveAxisLabelGap(int current, VizContext ctx)`
  - `public static int resolveLegendGap(int current, VizContext ctx)`

  Task 5 calls all three. Each takes the descriptor's current value and returns it unchanged in a legacy context or when it is not the legacy default.

`resolveAxisTitleGap` is where decision 5 lives. `abuttingLabelsDrawn` is a parameter rather than something the resolver works out, because the answer is mode-dependent — `GraphGenerator:2588` computes it as `!maxMode && axisD.isLabelVisible() || maxMode && axisD.isMaxModeLabelVisible()`, and only the caller knows `maxMode`.

- [ ] **Step 1: Write the failing test**

Create `core/src/test/java/inetsoft/uql/viewsheet/internal/ChartGapResolverTest.java` with the licence header and the Spring preamble from `VSDensityDefaultsTest`:

```java
package inetsoft.uql.viewsheet.internal;

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
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class ChartGapResolverTest {
   private static final VizContext MODERN = VizContext.of(VizMark.MODERN_LIGHT);
   private static final VizContext LEGACY = VizContext.LEGACY;

   @Test
   void labelGapTakesTheSpecValueWhenModernAndUnset() {
      assertEquals(8, VSChartChromeDefaults.resolveAxisLabelGap(0, MODERN));
   }

   @Test
   void labelGapIsUnchangedWhenLegacy() {
      assertEquals(0, VSChartChromeDefaults.resolveAxisLabelGap(0, LEGACY));
   }

   @Test
   void labelGapKeepsAnAuthorValue() {
      assertEquals(3, VSChartChromeDefaults.resolveAxisLabelGap(3, MODERN));
   }

   @Test
   void legendGapTakesTheTunableShareOfTheSpecValue() {
      // 16px total minus VGraph.GAP's fixed 2
      assertEquals(14, VSChartChromeDefaults.resolveLegendGap(0, MODERN));
   }

   @Test
   void legendGapIsUnchangedWhenLegacy() {
      assertEquals(0, VSChartChromeDefaults.resolveLegendGap(0, LEGACY));
   }

   @Test
   void legendGapKeepsAnAuthorValue() {
      assertEquals(20, VSChartChromeDefaults.resolveLegendGap(20, MODERN));
   }

   @Test
   void titleGapTakesFourWhenItsLabelsAreDrawn() {
      assertEquals(4, VSChartChromeDefaults.resolveAxisTitleGap(0, true, MODERN));
   }

   @Test
   void titleGapInheritsTheLabelGapWhenItsLabelsAreHidden() {
      // decision 5: the innermost visible band takes the plot-adjacent gap
      assertEquals(8, VSChartChromeDefaults.resolveAxisTitleGap(0, false, MODERN));
   }

   @Test
   void titleGapDoesNotInheritWhenLegacy() {
      // a legacy chart's title must not move when its labels are hidden
      assertEquals(0, VSChartChromeDefaults.resolveAxisTitleGap(0, false, LEGACY));
      assertEquals(0, VSChartChromeDefaults.resolveAxisTitleGap(0, true, LEGACY));
   }

   @Test
   void titleGapKeepsAnAuthorValueInBothStates() {
      assertEquals(5, VSChartChromeDefaults.resolveAxisTitleGap(5, true, MODERN));
      assertEquals(5, VSChartChromeDefaults.resolveAxisTitleGap(5, false, MODERN),
                   "inheritance must not override a value the author chose");
   }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `.\mvnw.cmd test -pl core -Dtest=ChartGapResolverTest -DfailIfNoTests=false`
Expected: compile failure — the three resolvers do not exist.

- [ ] **Step 3: Add the three resolvers**

In `VSChartChromeDefaults.java`, after `resolveLegendBorderColor` (`:94-97`):

```java
   /**
    * Resolve the gap between an axis's labels and the plot: the spec value when the context is
    * modern and the descriptor still carries no opinion, otherwise unchanged. Zero is the
    * descriptor's unset marker - DefaultAxis.getLabelGap renders it as 2 - so it doubles as the
    * legacy-default comparison.
    */
   public static int resolveAxisLabelGap(int current, VizContext ctx) {
      return ctx.modern && current == 0 ? AXIS_LABEL_GAP : current;
   }

   /**
    * Resolve the gap between an axis title and what it abuts: the plot-adjacent gap when its own
    * labels are not drawn, its own gap when they are. This is the card's "hidden means zero" rule -
    * each gap belongs to the element on its inner side, so a hidden label band hands its gap to
    * whatever now abuts the plot, and no band degrades to an empty stub.
    *
    * Whether the labels are drawn is a parameter rather than a lookup: the answer is mode-dependent
    * (GraphGenerator reads isMaxModeLabelVisible in max mode) and only the caller knows the mode.
    *
    * Gated on the context as well as on the value: unmarked, a title gap of 0 against a rendered
    * label gap of 2 would move a legacy chart's axis title by 2px whenever its labels are hidden.
    */
   public static int resolveAxisTitleGap(int current, boolean abuttingLabelsDrawn, VizContext ctx) {
      if(!ctx.modern || current != 0) {
         return current;
      }

      return abuttingLabelsDrawn ? AXIS_TITLE_GAP : AXIS_LABEL_GAP;
   }

   /**
    * Resolve the gap between the legend column and the plot. The spec asks for 16px between them and
    * VGraph adds a fixed 2px of its own between the legend area and the content, so the descriptor
    * carries the remaining 14 - the two compose to the specified total. Do not "correct" this to 16
    * without also removing VGraph's constant.
    */
   public static int resolveLegendGap(int current, VizContext ctx) {
      return ctx.modern && current == 0 ? LEGEND_GAP : current;
   }
```

and with the private constants:

```java
   // modern interior gap scale, px. 4 = --inet-space-2, 8 = --inet-space-4, and the legend's 14 plus
   // VGraph's fixed 2 makes the 16 of --inet-space-6.
   private static final int AXIS_TITLE_GAP = 4;
   private static final int AXIS_LABEL_GAP = 8;
   private static final int LEGEND_GAP = 14;
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `.\mvnw.cmd test -pl core -Dtest=ChartGapResolverTest -DfailIfNoTests=false`
Expected: PASS, ten tests.

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/inetsoft/uql/viewsheet/internal/VSChartChromeDefaults.java core/src/test/java/inetsoft/uql/viewsheet/internal/ChartGapResolverTest.java
git commit -m "feat(chart): resolve the card's interior gap scale from the mark"
```

---

### Task 5: Route `GraphGenerator` through the gap resolvers

**Files:**
- Modify: `core/src/main/java/inetsoft/report/composition/graph/GraphGenerator.java` — `:2591`, `:6183`, `:1696`, `:1775`, `:1810`, and `getTitleSpec` at `:6017`
- Test: `core/src/test/java/inetsoft/uql/viewsheet/internal/ChartGapResolverTest.java` (already covers the resolvers; this task's gate is the full suite plus a render check)

**Interfaces:**
- Consumes: the three resolvers from Task 4; `GraphGenerator`'s `vizContext` field (`:7489`), `info` (the chart info), and the `type` argument of `getTitleSpec`.
- Produces: nothing new. This is the wiring task.

`getTitleSpec(TitleDescriptor titleDesc, String type)` gains a third parameter rather than looking the axis up itself, because its four call sites at `:921-924` already know which axis each title belongs to and the caller there has the coord in hand. Widening the signature keeps the axis lookup at one site instead of four.

- [ ] **Step 1: Route the axis label gap**

At `GraphGenerator:2591`, replace:

```java
      axis.setLabelGap(axisD.getLabelGap());
```

with:

```java
      axis.setLabelGap(VSChartChromeDefaults.resolveAxisLabelGap(axisD.getLabelGap(), ctx));
```

`ctx` is already in scope at this site — `:2572` assigns it and `:2584` uses it.

- [ ] **Step 2: Route the legend gap**

At `GraphGenerator:1696`, `:1775` and `:1810`, replace each:

```java
         legend.setGap(legends.getGap());
```

with:

```java
         legend.setGap(VSChartChromeDefaults.resolveLegendGap(legends.getGap(), vizContext));
```

Use the `vizContext` field directly; these three sites have no local `ctx`.

- [ ] **Step 3: Route the axis title gap, and widen `getTitleSpec`**

Change the signature at `:6017`:

```java
   private TitleSpec getTitleSpec(TitleDescriptor titleDesc, String type,
                                  boolean abuttingLabelsDrawn)
```

Replace `:6183`:

```java
      tSpec.setLabelGap(VSChartChromeDefaults.resolveAxisTitleGap(
         titleDesc.getLabelGap(), abuttingLabelsDrawn, vizContext));
```

At the four call sites, `:921-924`:

```java
         graph.setXTitleSpec(getTitleSpec(xtitle, "x", axisLabelsDrawn("x")));
         graph.setX2TitleSpec(getTitleSpec(x2title, "x2", axisLabelsDrawn("x2")));
         graph.setYTitleSpec(getTitleSpec(ytitle, "y", axisLabelsDrawn("y")));
         graph.setY2TitleSpec(getTitleSpec(y2title, "y2", axisLabelsDrawn("y2")));
```

and add the helper next to `getTitleSpec`:

```java
   /**
    * Whether the axis a title sits beside actually draws its labels. Mirrors the expression
    * setupAxisSpec uses for axis.setLabelVisible, max-mode variant included: a title gap resolved
    * off the plain isLabelVisible getter would be wrong in max mode.
    *
    * Reads the chart-level descriptor rather than a per-field one. getAxisDescriptor0() already
    * prefers the runtime descriptor on a VSChartInfo, which is the same value the resize and CSS
    * paths act on. On a facet chart an outer band can carry its own visibility; the title abuts the
    * outermost band, and resolving that per-band is out of scope here - see the plan's Out of scope.
    */
   private boolean axisLabelsDrawn(String type) {
      AxisDescriptor axisD = "y2".equals(type) || "x2".equals(type)
         ? info.getAxisDescriptor2() : getAxisDescriptor0();

      if(axisD == null) {
         return false;
      }

      return !maxMode && axisD.isLabelVisible() || maxMode && axisD.isMaxModeLabelVisible();
   }
```

`getAxisDescriptor0()` is the existing no-argument accessor at `:561-569`; `info.getAxisDescriptor2()` is declared on `ChartInfo:193`. `maxMode` is a field, assigned at `:366`. Nothing new is needed beyond this method.

- [ ] **Step 4: Run the resolver and chart suites**

Run: `.\mvnw.cmd test -pl core -Dtest='ChartGapResolver*,VSChartChromeDefaults*,GraphGenerator*,VGraph*' -DfailIfNoTests=false`
Expected: PASS.

- [ ] **Step 5: Run the whole core suite**

Run: `.\mvnw.cmd test -pl core`
Expected: PASS at the branch's current baseline (5123 tests at the last recorded run). Compare against the baseline rather than against zero failures — record the count in the commit body if it differs.

- [ ] **Step 6: Commit**

```bash
git add core/src/main/java/inetsoft/report/composition/graph/GraphGenerator.java
git commit -m "feat(chart): apply the card's gap scale where the specs are built"
```

---

### Task 6: The legend gap option

**Files:**
- Modify: `core/src/main/java/inetsoft/web/graph/model/dialog/LegendFormatGeneralPaneModel.java`
- Modify: `core/src/main/java/inetsoft/web/graph/model/dialog/LegendFormatDialogModel.java:54` area (read) and `:135` area (write)
- Modify: `web/projects/portal/src/app/graph/dialog/legend-format-general-pane.component.ts` and its `.html`
- Test: `core/src/test/java/inetsoft/uql/viewsheet/internal/ChartGapResolverTest.java` (extend)

**Interfaces:**
- Consumes: `VSChartChromeDefaults.resolveLegendGap(int, VizContext)` from Task 4.
- Produces: `LegendFormatGeneralPaneModel.getGap()`/`setGap(int)` and `getGapFollowsDefault()`/`setGapFollowsDefault(Boolean)`. Nothing later depends on them.

This is decision 3's second half: the 16px default is Task 4, and this makes it an option the author can change. The legend format dialog is the right home and needs no new modelling — `LegendFormatDialogModel`'s constructor already receives the chart-wide `LegendsDescriptor` alongside the per-legend `LegendDescriptor`, and already carries a `VizContext` (`:40-41`). `legendsDesc.getBorder()`, `isRoundCorners()` and `setLayout(...)` are existing chart-wide values edited from this same pane.

**Copy the fill-colour pattern exactly.** `:65-69` reads the border colour *through* `resolveLegendBorderColor(..., ctx)`, and `:129-133` writes it back only `if(!Tool.equals(color, resolveLegendBorderColor(legendsDesc.getBorderColor(), ctx)))`. That comparison against the resolver is what stops a resolved value being stored as the author's — the same defect Task 3 guards for padding.

- [ ] **Step 1: Write the failing test**

Append to `ChartGapResolverTest`:

```java
   @Test
   void anUnchangedLegendGapIsNotWrittenBack() {
      // mirrors the dialog's write guard: the pane was shown the resolved 14, the author changed
      // nothing, and the descriptor must keep its 0 so the value goes on resolving
      LegendsDescriptor legends = new LegendsDescriptor();

      int shown = VSChartChromeDefaults.resolveLegendGap(legends.getGap(), MODERN);
      applyLegendGap(legends, shown, MODERN);

      assertEquals(0, legends.getGap(), "the descriptor still carries no opinion");
      assertEquals(14, VSChartChromeDefaults.resolveLegendGap(legends.getGap(), MODERN));
   }

   @Test
   void anEditedLegendGapIsStored() {
      LegendsDescriptor legends = new LegendsDescriptor();

      applyLegendGap(legends, 24, MODERN);

      assertEquals(24, legends.getGap());
      assertEquals(24, VSChartChromeDefaults.resolveLegendGap(legends.getGap(), MODERN),
                   "an author value is returned unchanged");
   }

   @Test
   void tickingFollowDefaultClearsTheStoredLegendGap() {
      LegendsDescriptor legends = new LegendsDescriptor();
      legends.setGap(24);

      legends.setGap(0);

      assertEquals(14, VSChartChromeDefaults.resolveLegendGap(legends.getGap(), MODERN),
                   "clearing to the unset marker resolves again");
   }

   /** Mirrors LegendFormatDialogModel's write guard for the gap. */
   private static void applyLegendGap(LegendsDescriptor legends, int edited, VizContext ctx) {
      if(edited != VSChartChromeDefaults.resolveLegendGap(legends.getGap(), ctx)) {
         legends.setGap(edited);
      }
   }
```

Add `import inetsoft.uql.viewsheet.graph.LegendsDescriptor;` to the test's imports.

- [ ] **Step 2: Run the test and read what it tells you**

Run: `.\mvnw.cmd test -pl core -Dtest=ChartGapResolverTest -DfailIfNoTests=false`
Expected: **PASS.** Unlike the other tasks' tests this one is not red first, and that is deliberate: it pins the write-guard contract by carrying its own copy of the guard, exactly as `PaddingFollowDefaultTest` does. Its job is to fail later if Task 4's `LEGEND_GAP` moves or the guard is written differently in the dialog. A failure here means `LEGEND_GAP` is not 14 — fix Task 4, not this test.

- [ ] **Step 3: Add the model fields**

In `LegendFormatGeneralPaneModel.java`, alongside the `symbolSize` accessors:

```java
   /** The gap between the legend column and the plot, in pixels. */
   public int getGap() {
      return gap;
   }

   public void setGap(int gap) {
      this.gap = gap;
   }

   /**
    * Whether the gap follows the modern card default. Null on an unmarked chart, where there is no
    * default to follow and the checkbox is not shown.
    */
   public Boolean getGapFollowsDefault() {
      return gapFollowsDefault;
   }

   public void setGapFollowsDefault(Boolean gapFollowsDefault) {
      this.gapFollowsDefault = gapFollowsDefault;
   }
```

and with the fields:

```java
   private int gap;
   private Boolean gapFollowsDefault;
```

- [ ] **Step 4: Wire the read and the write**

In `LegendFormatDialogModel.java`, after `generalPaneModel.setSymbolSize(...)` (`:54`):

```java
      generalPaneModel.setGap(VSChartChromeDefaults.resolveLegendGap(legendsDesc.getGap(), ctx));
      generalPaneModel.setGapFollowsDefault(ctx.modern ? legendsDesc.getGap() == 0 : null);
```

and after `legendDesc.setSymbolSize(...)` (`:135`):

```java
      Boolean gapFollowsDefault = generalPaneModel.getGapFollowsDefault();

      if(gapFollowsDefault == null) {
         // no checkbox was shown, so this chart is not modern and the gap is not being resolved;
         // store only a real edit, exactly as this pane behaved before the option existed
         if(generalPaneModel.getGap() !=
            VSChartChromeDefaults.resolveLegendGap(legendsDesc.getGap(), ctx))
         {
            legendsDesc.setGap(generalPaneModel.getGap());
         }
      }
      else if(gapFollowsDefault) {
         // 0 is the descriptor's unset marker, which is what the resolver substitutes for
         legendsDesc.setGap(0);
      }
      else {
         legendsDesc.setGap(generalPaneModel.getGap());
      }
```

- [ ] **Step 5: Add the frontend field and controls**

In `legend-format-general-pane.component.ts`'s model interface (or the shared `LegendFormatGeneralPaneModel` TS interface it imports), add:

```typescript
   gap: number;
   gapFollowsDefault?: boolean;
```

In the component class:

```typescript
   showGapFollowDefault: boolean;

   gapFollowDefaultChanged(follows: boolean): void {
      this.model.gapFollowsDefault = follows;
   }
```

and set `this.showGapFollowDefault = this.model.gapFollowsDefault != null;` in `ngOnInit`.

In the pane's `.html`, beside the existing symbol-size stepper:

```html
<div class="form-row-float-label row">
  <div class="col">
    <div class="d-flex flex-column">
      <number-stepper [(ngModel)]="model.gap" [min]="0"
                      [disabled]="model.gapFollowsDefault === true"
                      [ngModelOptions]="{standalone: true}">
      </number-stepper>
      <label>_#(Legend Gap)</label>
    </div>
  </div>
</div>
@if (showGapFollowDefault) {
  <div class="form-check">
    <input type="checkbox" class="form-check-input" id="legendGapFollowsDefault"
           [ngModel]="model.gapFollowsDefault"
           (ngModelChange)="gapFollowDefaultChanged($event)"
           [ngModelOptions]="{standalone: true}">
    <label class="form-check-label" for="legendGapFollowsDefault">
      _#(composer.vs.followDefaultDensity)
    </label>
  </div>
}
```

`_#(Legend Gap)` is a new i18n key. Add it to the same bundle that holds the pane's other labels, matching the surrounding entries' casing. `composer.vs.followDefaultDensity` already exists — reuse it.

- [ ] **Step 6: Run the gates**

Run: `.\mvnw.cmd test -pl core -Dtest='ChartGapResolver*,LegendFormat*,ChartRegionHandler*' -DfailIfNoTests=false`
Then, from `web/`: `npm run lint` and `npm run test:portal`
Expected: all green.

- [ ] **Step 7: Commit**

```bash
git add core/src/main/java/inetsoft/web/graph/model/dialog/LegendFormatGeneralPaneModel.java core/src/main/java/inetsoft/web/graph/model/dialog/LegendFormatDialogModel.java core/src/test/java/inetsoft/uql/viewsheet/internal/ChartGapResolverTest.java web/projects/portal/src/app/graph/dialog/legend-format-general-pane.component.ts web/projects/portal/src/app/graph/dialog/legend-format-general-pane.component.html
git commit -m "feat(chart): let an author set the legend gap, or follow the card default"
```

---

### Task 7: Cross-module build, and the render pass

**Files:** none modified. This task is the gate.

**Interfaces:**
- Consumes: Tasks 1–5.
- Produces: a signed-off change set.

Every value in this plan is server-rendered, so the automated gates prove the code and the tests agree with each other, not that a screen agrees with either. The precedent is explicit: a 77-module incremental `install` reported SUCCESS on a cross-module signature break that only `clean` exposed. `getTitleSpec` gains a parameter in Task 5, so the `clean` build is not optional.

- [ ] **Step 1: Clean cross-module build**

Run: `.\mvnw.cmd clean install -DskipTests "-Pcommunity,enterprise"`
Expected: BUILD SUCCESS across all modules. A signature change to a private method should not reach other modules, but this is the step that proves it.

- [ ] **Step 2: Frontend gates**

Run, from `web/`: `npm run lint` and `npm run test:portal` and `npm run test:em`
Expected: all green.

- [ ] **Step 3: The render checks**

Start the server and work through this table. Every row is a state the resolvers distinguish; a row that cannot be produced is a finding, not a skip.

| # | Setup | Expect |
|---|---|---|
| R1 | A newly created chart in a gate-on org | 12px from the card border to the title and to the axis content, on all four edges. The title and the y-axis title start on the same vertical |
| R2 | An existing **unmarked** chart | Pixel-identical to before this change: 10px inset, 2px label gap, legend where it was. This is the row the whole plan is built around |
| R3 | R1's chart, padding pane | Shows 12, with the checkbox ticked |
| R4 | Untick the checkbox, set 4, Apply, reopen | Shows 4, checkbox clear. The card tightens |
| R5 | Re-tick the checkbox, Apply, reopen | Shows 12, checkbox ticked. The stored value went back to 10 and the resolver substituted |
| R6 | R1's chart, open properties, change nothing but the title text, Apply | Padding still resolves — the pane's 12 was **not** stamped as the author's. Re-open and confirm the checkbox is still ticked |
| R7 | R1's chart with a legend | 16px between the plot and the legend column, and the legend column's own left edge on the 12px card inset |
| R8 | R1's chart, hide the y-axis **labels**, keep the y-axis **title** | The title sits **8px** from the plot, not 4px — decision 5. Compare against R9 |
| R9 | The same chart with labels shown | Title 4px from the labels, labels 8px from the plot |
| R10 | R2's chart, hide its y-axis labels | The title keeps its own gap. A legacy chart must not inherit |
| R11 | R1's chart in **max mode**, labels hidden by the max-mode setting | The title still takes 8px. If it takes 4, `axisLabelsDrawn` is reading the wrong getter |
| R12 | R1's chart, then **Revert** on the dashboard | Every value returns to legacy — 10px inset, 2px label gap, legend as R2. No code was written for this; if it fails, something got seeded |
| R13 | R4's chart (author padding 4), then **Revert** | Padding 4 survives. Revert clears the mark, and an author's value was never the mark's to take |
| R14 | Export R1's chart to PDF, PNG and Excel | The exported geometry matches the viewer's. All four values are read by the export painters through the same getters |
| R15 | R1's chart with an axis band **dragged** to a fixed size | Renders without an empty stub between the labels and the axis title. §04 has no rule for this state (decision 4) — record what it does, do not fix it here |
| R16 | R1's chart, open the legend format dialog | Shows the resolved gap with the checkbox ticked and the stepper disabled |
| R17 | Untick it, set 24, Apply | The legend moves further from the plot. Re-open: 24, checkbox clear |
| R18 | Re-tick it, Apply, reopen | Back to the resolved value, checkbox ticked. The descriptor went back to 0 |
| R19 | R2's **unmarked** chart, legend format dialog | No checkbox, and the gap reads as it did before this change |

- [ ] **Step 4: Record the outcome in the decisions doc**

Add a dated line to `chart-card-geometry-decisions.md` stating which rows passed, and file anything R15 turns up as a note under decision 4 rather than as a defect against this change set.

- [ ] **Step 5: Commit the record**

```bash
git add community/docs/superpowers/specs/lookfeel/chart-card-geometry-decisions.md
git commit -m "docs(chart-card): record the card geometry render pass"
```

---

## Out of scope, and why

- **§04-b, the legend panel's border and fill.** A separate change with its own re-measurement; §04 estimates it returns 8–10px of horizontal room, which partly offsets Task 4's legend gap. Sequence it after this plan so the two measurements do not confound each other.
- **`--inet-chart-line-height` at 1.2.** §04's one type value. Chart text is server-painted, so it is a font-metrics change with no CSS half — its own task.
- **The axis drag's missing tier (decision 4).** `AxisDescriptor.fixedWidth`/`fixedHeight` are untiered and drag-only. Recorded, not scheduled; R15 exists so its behaviour is known rather than assumed.
- **Zeroing the graph's own outer margin.** Void: confirmed at render that no such margin exists, and §04's source edit named the card inset itself. See decision 1's §1.1.1.
- **Bookmarks.** Padding lives in `writeAttributes`, which `AbstractVSAssembly.writeState` does not reach, so none of these values enters a bookmark. Decision 10 is unaffected by this plan.
