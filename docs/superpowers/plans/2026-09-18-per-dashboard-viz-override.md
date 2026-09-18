# Per-Dashboard Visualization Override Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let an author set modern/legacy, dark and density per dashboard from the Options pane of the viewsheet property dialog, instead of taking the org-wide settings.

**Architecture:** Mode and dark need no new persisted field — the sheet's own `VizMark` already is a per-dashboard mode, stamped from the org gate at creation and inherited by every assembly created afterwards. The dialog reads and writes that mark through a new generalized `VizModernizeUtil.applyMark`. Density needs one new nullable `ViewsheetInfo.vizDensity`, resolved inside `VizContext` (which is density's only reader) and carried to the browser on the assembly wrapper next to the existing `viz-modern` / `viz-dark` classes.

**Tech Stack:** Java 21, Spring Boot 3.5.8, JUnit 5 (`@Tag("core")`), Angular 21.2, TypeScript 5.9, Vitest 4.1.7, SCSS.

**Spec:** `docs/superpowers/specs/lookfeel/2026-09-18-per-dashboard-viz-override-design.md`

## Global Constraints

- **Repo:** all changes are in the `community/` submodule. Branch `feature-per-dashboard-viz-override`, already created off `epic-74519` at `f897b9050`. No enterprise or server change.
- **Git commands must never be chained.** No `&&`, and no `cd <dir> && git …` prefix — both trip the approval gate. Use `git -C E:/StyleBI/stylebi-enterprise/community <subcommand>` as a single command per Bash call.
- **Java tests:** run from `E:/StyleBI/stylebi-enterprise/community` with `./mvnw test -pl core -Dtest=<ClassName>`.
- **Frontend `*.spec.ts`:** run from `E:/StyleBI/stylebi-enterprise/community/web` with `npx ng test portal --include="**/<file>.spec.ts"`.
- **Frontend `*.tl.spec.ts`:** run with `npx ng run portal:test-tl --include='**/<file>.tl.spec.ts'`. **`ng test portal` silently matches nothing for `.tl.spec.ts` files and exits 0** — a scoped run through the wrong target is indistinguishable from a pass. Never run the full TL suite.
- **No comments in Angular HTML template files.**
- **Code comments are short clauses, not full sentences**, and never reference tickets, PRs, design docs or mockups.
- **Density mode values** are exactly `"dense"`, `"compact"`, `"comfortable"`. The org default when `viewsheet.density` is unset is `"compact"` (`VSDensityDefaults.mode()`); `VSDensityDefaults.normalizeMode` clamps an unrecognized value to `"dense"`.
- **`vizDensity` is nullable throughout.** Null means "inherit the org". The XML attribute is written only when non-null so existing files, exports and bookmarks stay byte-identical until an author sets it.

---

### Task 1: `ViewsheetInfo.vizDensity` and the resolution helper

The persisted field and the sheet-then-org-then-default fallback. Nothing reads it yet.

**Files:**
- Modify: `core/src/main/java/inetsoft/uql/viewsheet/ViewsheetInfo.java` (getter/setter near `getSnapGrid` at `:553`, write at `:762`, parse at `:829`, field at `:1183`)
- Modify: `core/src/main/java/inetsoft/uql/viewsheet/internal/VSDensityDefaults.java` (new overload next to `mode()` at `:76`)
- Test: `core/src/test/java/inetsoft/uql/viewsheet/ViewsheetInfoVizDensityTest.java` (create)

**Interfaces:**
- Consumes: nothing.
- Produces:
  - `String ViewsheetInfo.getVizDensity()` — the stored value, null when unset.
  - `void ViewsheetInfo.setVizDensity(String)` — null or empty clears it.
  - `static String VSDensityDefaults.mode(ViewsheetInfo)` — resolved mode, never null, always one of the three valid values.

- [ ] **Step 1: Write the failing test**

Create `core/src/test/java/inetsoft/uql/viewsheet/ViewsheetInfoVizDensityTest.java`:

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
import inetsoft.uql.viewsheet.internal.VSDensityDefaults;
import inetsoft.util.Tool;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.w3c.dom.Element;

import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class ViewsheetInfoVizDensityTest {
   @AfterEach
   void reset() {
      SreeEnv.setProperty("viewsheet.density", null);
   }

   @Test
   void anUnsetSheetWritesNoAttribute() {
      assertFalse(writeXml(new ViewsheetInfo()).contains("vizDensity"),
                  "an existing file must stay byte-identical until an author sets it");
   }

   @Test
   void aSetSheetRoundTrips() throws Exception {
      ViewsheetInfo info = new ViewsheetInfo();
      info.setVizDensity("comfortable");

      assertEquals("comfortable", reparse(info).getVizDensity());
   }

   @Test
   void anAbsentAttributeParsesAsNull() throws Exception {
      assertNull(reparse(new ViewsheetInfo()).getVizDensity());
   }

   @Test
   void anEmptyValueClearsTheField() {
      ViewsheetInfo info = new ViewsheetInfo();
      info.setVizDensity("compact");
      info.setVizDensity("");

      assertNull(info.getVizDensity(), "empty means inherit, same as null");
   }

   @Test
   void theSheetValueOverridesTheOrg() {
      SreeEnv.setProperty("viewsheet.density", "comfortable");
      ViewsheetInfo info = new ViewsheetInfo();
      info.setVizDensity("dense");

      assertEquals("dense", VSDensityDefaults.mode(info));
   }

   @Test
   void anUnsetSheetFallsBackToTheOrg() {
      SreeEnv.setProperty("viewsheet.density", "comfortable");

      assertEquals("comfortable", VSDensityDefaults.mode(new ViewsheetInfo()));
   }

   @Test
   void aNullSheetFallsBackToTheOrg() {
      SreeEnv.setProperty("viewsheet.density", "comfortable");

      assertEquals("comfortable", VSDensityDefaults.mode((ViewsheetInfo) null));
   }

   @Test
   void anUnsetSheetInAnUnsetOrgTakesTheShippedDefault() {
      assertEquals("compact", VSDensityDefaults.mode(new ViewsheetInfo()));
   }

   @Test
   void anUnrecognizedSheetValueClampsToDense() {
      ViewsheetInfo info = new ViewsheetInfo();
      info.setVizDensity("roomy");

      assertEquals("dense", VSDensityDefaults.mode(info));
   }

   private String writeXml(ViewsheetInfo info) {
      StringWriter buffer = new StringWriter();
      PrintWriter writer = new PrintWriter(buffer);
      info.writeXML(writer);
      writer.flush();
      return buffer.toString();
   }

   private ViewsheetInfo reparse(ViewsheetInfo info) throws Exception {
      Element elem = Tool.parseXML(new StringReader(writeXml(info))).getDocumentElement();
      ViewsheetInfo parsed = new ViewsheetInfo();
      parsed.parseXML(elem);
      return parsed;
   }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./mvnw test -pl core -Dtest=ViewsheetInfoVizDensityTest`
Expected: FAIL — compilation error, `getVizDensity()` and `mode(ViewsheetInfo)` do not exist.

- [ ] **Step 3: Add the field, accessors and serialization**

In `ViewsheetInfo.java`, add the field beside `snapGrid` (`:1183`):

```java
   private String vizDensity; // null = inherit the org
```

Add accessors immediately after `setSnapGrid` (`:557-559`):

```java
   /**
    * The dashboard's own density mode, or null to follow the org.
    */
   public String getVizDensity() {
      return vizDensity;
   }

   public void setVizDensity(String vizDensity) {
      this.vizDensity = vizDensity == null || vizDensity.isEmpty() ? null : vizDensity;
   }
```

In `writeAttributes`, after the `snapGrid` line (`:762`):

```java
      if(vizDensity != null) {
         // omitted when unset, so an untouched sheet's file does not change
         writer.print(" vizDensity=\"" + vizDensity + "\"");
      }
```

In `parseAttributes`, after the `snapGrid` block (`:829-831`):

```java
      this.vizDensity = Tool.getAttribute(elem, "vizDensity");
```

- [ ] **Step 4: Add the resolution overload**

In `VSDensityDefaults.java`, directly after the existing `mode()` (`:76-79`):

```java
   /**
    * The density mode for one dashboard: its own value when set, else the org's. Never null, and
    * always one of the three valid modes.
    */
   public static String mode(ViewsheetInfo info) {
      String density = info == null ? null : info.getVizDensity();
      return normalizeMode(density == null || density.isEmpty() ? mode() : density);
   }
```

Add the import `inetsoft.uql.viewsheet.ViewsheetInfo` at the top of the file.

Note `normalizeMode` clamps an unrecognized value to `"dense"` but the bare `mode()` returns `"compact"` when the property is unset, so the unset-org path must not be clamped through a value that never existed — passing `mode()`'s result through `normalizeMode` is safe because all three of its returns are valid modes.

- [ ] **Step 5: Run the test to verify it passes**

Run: `./mvnw test -pl core -Dtest=ViewsheetInfoVizDensityTest`
Expected: PASS, 9 tests.

- [ ] **Step 6: Run the neighbouring suite for regressions**

Run: `./mvnw test -pl core -Dtest=VSDensityDefaultsTest`
Expected: PASS, unchanged.

- [ ] **Step 7: Commit**

```
git -C E:/StyleBI/stylebi-enterprise/community add core/src/main/java/inetsoft/uql/viewsheet/ViewsheetInfo.java core/src/main/java/inetsoft/uql/viewsheet/internal/VSDensityDefaults.java core/src/test/java/inetsoft/uql/viewsheet/ViewsheetInfoVizDensityTest.java
```

```
git -C E:/StyleBI/stylebi-enterprise/community commit -m "Per-dashboard density: the persisted field and its fallback chain

ViewsheetInfo gains a nullable vizDensity, written only when set so an
untouched sheet's file, export and bookmarks stay byte-identical.
VSDensityDefaults.mode(ViewsheetInfo) resolves sheet, then org, then the
shipped default, clamping through the existing normalizeMode.

Nothing reads it yet.

Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>"
```

---

### Task 2: `VizContext` resolves density from the sheet

Density's single reader starts asking the dashboard. Every live density site inherits this with no edit of its own.

**Files:**
- Modify: `core/src/main/java/inetsoft/uql/viewsheet/internal/VizContext.java` (`of(VSAssemblyInfo)` at `:65`, `of(VizMark)` at `:77`, `ofTransition` at `:88`)
- Modify: `core/src/main/java/inetsoft/uql/viewsheet/internal/VizModernizeUtil.java` (the two `ofTransition` call sites at `:61` and `:105`)
- Test: `core/src/test/java/inetsoft/uql/viewsheet/internal/VizContextTest.java` (extend)

**Interfaces:**
- Consumes: `VSDensityDefaults.mode(ViewsheetInfo)` from Task 1.
- Produces:
  - `VizContext.of(VSAssemblyInfo)` — unchanged signature, `density` now sheet-scoped.
  - `static VizContext VizContext.ofTransition(Viewsheet vs, VizMark mark)` — **replaces** the one-arg `ofTransition(VizMark)`. Task 4 calls this.

- [ ] **Step 1: Write the failing test**

Append to `VizContextTest`:

```java
   @Test
   void aSheetsOwnDensityOverridesTheOrg() {
      SreeEnv.setProperty("viewsheet.density", "comfortable");
      Viewsheet vs = new Viewsheet();
      vs.getViewsheetInfo().setVizDensity("dense");
      TextVSAssembly text = new TextVSAssembly(vs, "Text1");
      vs.addAssembly(text);

      assertEquals("dense", VizContext.of(text.getVSAssemblyInfo()).density);
   }

   @Test
   void aSheetWithNoDensityFallsBackToTheOrg() {
      SreeEnv.setProperty("viewsheet.density", "comfortable");
      Viewsheet vs = new Viewsheet();
      TextVSAssembly text = new TextVSAssembly(vs, "Text1");
      vs.addAssembly(text);

      assertEquals("comfortable", VizContext.of(text.getVSAssemblyInfo()).density);
   }

   @Test
   void anAssemblyWithNoSheetFallsBackToTheOrg() {
      SreeEnv.setProperty("viewsheet.density", "comfortable");

      assertEquals("comfortable", VizContext.of(new TextVSAssemblyInfo()).density);
   }

   @Test
   void embeddedContentTakesTheOutermostSheetsDensity() {
      SreeEnv.setProperty("viewsheet.density", "dense");
      Viewsheet outer = new Viewsheet();
      outer.getViewsheetInfo().setVizDensity("comfortable");

      Viewsheet embedded = new Viewsheet();
      embedded.getViewsheetInfo().setVizDensity("compact");
      embedded.getVSAssemblyInfo().setName("EmbeddedVS");
      TextVSAssembly text = new TextVSAssembly(embedded, "InnerText");
      embedded.addAssembly(text);
      outer.addAssembly(embedded);

      assertEquals("comfortable", VizContext.of(text.getVSAssemblyInfo()).density,
                   "the page's density, not the embedded asset's");
   }

   @Test
   void thatSameSheetEditedStandaloneTakesItsOwnDensity() {
      SreeEnv.setProperty("viewsheet.density", "dense");
      Viewsheet vs = new Viewsheet();
      vs.getViewsheetInfo().setVizDensity("compact");
      TextVSAssembly text = new TextVSAssembly(vs, "Text1");
      vs.addAssembly(text);

      assertEquals("compact", VizContext.of(text.getVSAssemblyInfo()).density,
                   "outermost when opened on its own");
   }

   @Test
   void aTransitionCarriesTheSheetsDensity() {
      SreeEnv.setProperty("viewsheet.density", "comfortable");
      Viewsheet vs = new Viewsheet();
      vs.getViewsheetInfo().setVizDensity("dense");

      VizContext ctx = VizContext.ofTransition(vs, VizMark.MODERN_LIGHT);

      assertEquals("dense", ctx.density);
      assertTrue(ctx.transition);
      assertTrue(ctx.modern);
   }
```

Add the imports `inetsoft.uql.viewsheet.TextVSAssembly` and `inetsoft.uql.viewsheet.Viewsheet` to the test file.

- [ ] **Step 2: Run the test to verify it fails**

Run: `./mvnw test -pl core -Dtest=VizContextTest`
Expected: FAIL — `ofTransition(Viewsheet, VizMark)` does not exist; the density assertions return the org value.

- [ ] **Step 3: Resolve density from the sheet**

In `VizContext.java`, replace the body of `of(VSAssemblyInfo)` (`:65-67`) so it no longer delegates to the mark-only factory:

```java
   public static VizContext of(VSAssemblyInfo info) {
      VizMark mark = info == null ? null : info.getVizMark();
      boolean modern = mark != null;
      return new VizContext(modern, modern && mark == VizMark.MODERN_DARK, densityOf(info));
   }
```

Add the resolver as a private static on the same class:

```java
   /**
    * The density in force for an assembly: its dashboard's own value, or the org's. Walks to the
    * outermost sheet, so an embedded asset's content takes the page's density rather than its own -
    * deliberately unlike the mark, which VizModernizeUtil.collect() leaves to the embedded asset.
    */
   private static String densityOf(VSAssemblyInfo info) {
      Viewsheet vs = info == null ? null : info.getViewsheet();

      for(Viewsheet parent; vs != null && (parent = vs.getViewsheet()) != null; vs = parent) {
      }

      return vs == null ? VSDensityDefaults.mode() : VSDensityDefaults.mode(vs.getViewsheetInfo());
   }
```

Add the import `inetsoft.uql.viewsheet.Viewsheet`.

Leave `of(VizMark)` as it is — its remaining callers are the chart palette picker, where density is unused.

- [ ] **Step 4: Give the transition factory the sheet**

Replace `ofTransition(VizMark)` (`:88-91`) with:

```java
   public static VizContext ofTransition(Viewsheet vs, VizMark mark) {
      boolean modern = mark != null;
      String density = vs == null ?
         VSDensityDefaults.mode() : VSDensityDefaults.mode(vs.getViewsheetInfo());
      return new VizContext(modern, modern && mark == VizMark.MODERN_DARK, density, true);
   }
```

Keep the existing Javadoc on it and add one clause: the sheet is a parameter because seeding writes control heights, which are density-derived.

- [ ] **Step 5: Update the two call sites**

In `VizModernizeUtil.java`, `:61`:

```java
      VizContext ctx = VizContext.ofTransition(vs, mark);
```

and `:105`:

```java
      VizContext ctx = VizContext.ofTransition(vs, null);
```

- [ ] **Step 6: Run the test to verify it passes**

Run: `./mvnw test -pl core -Dtest=VizContextTest`
Expected: PASS — the 12 existing cases plus the 6 new ones.

- [ ] **Step 7: Run the architectural guards and the modernize suite**

Run: `./mvnw test -pl core -Dtest=VizContextReadFlipTest`
Expected: PASS, unchanged. `onlyTheDocumentedSitesStillReadTheOrgGate` and `exactlyTwoDocumentedSitesStillReadTheOrgGate` must not need editing — this change adds no org-gate reads, only a density-property read, and density is a separate property. **If either fails, stop: something read the gate that should not have.**

Run: `./mvnw test -pl core -Dtest=VizModernizeUtilTest`
Expected: PASS, all 34 unchanged.

- [ ] **Step 8: Commit**

```
git -C E:/StyleBI/stylebi-enterprise/community add core/src/main/java/inetsoft/uql/viewsheet/internal/VizContext.java core/src/main/java/inetsoft/uql/viewsheet/internal/VizModernizeUtil.java core/src/test/java/inetsoft/uql/viewsheet/internal/VizContextTest.java
```

```
git -C E:/StyleBI/stylebi-enterprise/community commit -m "Per-dashboard density: VizContext resolves it from the sheet

VizContext is density's only reader, so one class covers every live site -
table row and header height, selection cell height, the title lane - and
export agrees for free because it reads the same VSTableLens.

Embedded content takes the outermost sheet's density: density is a live
layout property of the page, unlike the mark, which stays with the embedded
asset. ofTransition takes the sheet because seeding writes control heights.

Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>"
```

---

### Task 3: Control heights follow a density change

Seeding is guarded on the legacy default, so once a control is seeded to 24/28/30 the modern branch can never fire again and a later density change would strand it while tables and titles reflow.

**Files:**
- Modify: `core/src/main/java/inetsoft/uql/viewsheet/internal/ComboBoxVSAssemblyInfo.java:427`
- Modify: `core/src/main/java/inetsoft/uql/viewsheet/internal/SpinnerVSAssemblyInfo.java:76`
- Modify: `core/src/main/java/inetsoft/uql/viewsheet/internal/SubmitVSAssemblyInfo.java:93`
- Modify: `core/src/main/java/inetsoft/uql/viewsheet/internal/TextInputVSAssemblyInfo.java:93`
- Modify: `core/src/main/java/inetsoft/uql/viewsheet/internal/CheckBoxVSAssemblyInfo.java:482`
- Modify: `core/src/main/java/inetsoft/uql/viewsheet/internal/RadioButtonVSAssemblyInfo.java:432`
- Test: `core/src/test/java/inetsoft/uql/viewsheet/internal/ControlHeightFollowDensityTest.java` (extend)

**Interfaces:**
- Consumes: `VizContext.ofTransition(Viewsheet, VizMark)` from Task 2 (used by the test through `VizContext.of(VizMark)`, which still reads org density — the test sets the org property directly).
- Produces: no new API. Behaviour change only.

- [ ] **Step 1: Write the failing test**

Append to `ControlHeightFollowDensityTest`:

```java
   @Test
   void spinnerHeightFollowsALaterDensityChange() {
      SreeEnv.setProperty("viewsheet.density", "comfortable");
      SpinnerVSAssemblyInfo info = new SpinnerVSAssemblyInfo();
      info.seedChromeDefaults(VizContext.of(VizMark.MODERN_LIGHT));
      assertEquals(30, info.getPixelSize().height);

      SreeEnv.setProperty("viewsheet.density", "dense");
      info.seedChromeDefaults(VizContext.of(VizMark.MODERN_LIGHT));

      assertEquals(24, info.getPixelSize().height,
                   "a seeded control must not be stranded at the old tier");
   }

   @Test
   void comboBoxHeightFollowsALaterDensityChange() {
      SreeEnv.setProperty("viewsheet.density", "dense");
      ComboBoxVSAssemblyInfo info = new ComboBoxVSAssemblyInfo();
      info.seedChromeDefaults(VizContext.of(VizMark.MODERN_LIGHT));
      assertEquals(24, info.getPixelSize().height);

      SreeEnv.setProperty("viewsheet.density", "comfortable");
      info.seedChromeDefaults(VizContext.of(VizMark.MODERN_LIGHT));

      assertEquals(30, info.getPixelSize().height);
   }

   @Test
   void textInputHeightFollowsALaterDensityChange() {
      SreeEnv.setProperty("viewsheet.density", "comfortable");
      TextInputVSAssemblyInfo info = new TextInputVSAssemblyInfo();
      info.seedChromeDefaults(VizContext.of(VizMark.MODERN_LIGHT));

      SreeEnv.setProperty("viewsheet.density", "compact");
      info.seedChromeDefaults(VizContext.of(VizMark.MODERN_LIGHT));

      assertEquals(28, info.getPixelSize().height);
   }

   @Test
   void submitHeightFollowsALaterDensityChange() {
      SreeEnv.setProperty("viewsheet.density", "comfortable");
      SubmitVSAssemblyInfo info = new SubmitVSAssemblyInfo();
      info.seedChromeDefaults(VizContext.of(VizMark.MODERN_LIGHT));

      SreeEnv.setProperty("viewsheet.density", "dense");
      info.seedChromeDefaults(VizContext.of(VizMark.MODERN_LIGHT));

      assertEquals(24, info.getPixelSize().height);
   }

   @Test
   void checkBoxHeightFollowsALaterDensityChange() {
      SreeEnv.setProperty("viewsheet.density", "comfortable");
      CheckBoxVSAssemblyInfo info = new CheckBoxVSAssemblyInfo();
      info.seedChromeDefaults(VizContext.of(VizMark.MODERN_LIGHT));
      assertEquals(60, info.getPixelSize().height, "the doubled comfortable tier");

      SreeEnv.setProperty("viewsheet.density", "dense");
      info.seedChromeDefaults(VizContext.of(VizMark.MODERN_LIGHT));

      assertEquals(48, info.getPixelSize().height, "the doubled dense tier");
   }

   @Test
   void radioButtonHeightFollowsALaterDensityChange() {
      SreeEnv.setProperty("viewsheet.density", "comfortable");
      RadioButtonVSAssemblyInfo info = new RadioButtonVSAssemblyInfo();
      info.seedChromeDefaults(VizContext.of(VizMark.MODERN_LIGHT));

      SreeEnv.setProperty("viewsheet.density", "dense");
      info.seedChromeDefaults(VizContext.of(VizMark.MODERN_LIGHT));

      assertEquals(48, info.getPixelSize().height);
   }

   @Test
   void aControlHandSizedToATierValueIsAlsoMoved() {
      SreeEnv.setProperty("viewsheet.density", "comfortable");
      SpinnerVSAssemblyInfo info = new SpinnerVSAssemblyInfo();
      info.setPixelSize(new Dimension(info.getPixelSize().width, 28));

      info.seedChromeDefaults(VizContext.of(VizMark.MODERN_LIGHT));

      assertEquals(30, info.getPixelSize().height,
                   "the accepted false positive: a hand-sized control matching a tier is moved, "
                      + "the same rule isControlHeight already applies on Revert");
   }
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./mvnw test -pl core -Dtest=ControlHeightFollowDensityTest`
Expected: FAIL — the seven new cases; each control keeps its first-seeded height because the modern branch only fires at `AssetUtil.defh`.

- [ ] **Step 3: Widen the modern branch in the four single-height types**

In `ComboBoxVSAssemblyInfo.java:427`, `SpinnerVSAssemblyInfo.java:76`, `SubmitVSAssemblyInfo.java:93` and `TextInputVSAssemblyInfo.java:93`, change the guard from:

```java
      if(ctx.modern && getPixelSize().height == AssetUtil.defh) {
```

to:

```java
      // also re-fires on a density change, or a seeded control is stranded at the old tier
      if(ctx.modern && (getPixelSize().height == AssetUtil.defh ||
         VSDensityDefaults.isControlHeight(getPixelSize().height)))
      {
```

The `else if(!ctx.modern && …)` branch below each is unchanged.

- [ ] **Step 4: Widen the modern branch in the two doubled types**

In `CheckBoxVSAssemblyInfo.java:482` and `RadioButtonVSAssemblyInfo.java:432`, change:

```java
      if(ctx.modern && getPixelSize().height == 2 * AssetUtil.defh) {
```

to:

```java
      // also re-fires on a density change, or a seeded control is stranded at the old tier
      if(ctx.modern && (getPixelSize().height == 2 * AssetUtil.defh ||
         getPixelSize().height % 2 == 0 &&
            VSDensityDefaults.isControlHeight(getPixelSize().height / 2)))
      {
```

The inner `if(getCellHeight() == AssetUtil.defh)` guard stays as it is; on a re-fire the cell height is no longer at the legacy default, so it is recomputed by the surrounding container logic rather than re-seeded here.

- [ ] **Step 5: Re-point two existing cases off a tier value**

`AssetUtil.defh` is 20, so the legacy doubled default is 40 and the three doubled tiers are 48
(dense), 56 (compact) and 60 (comfortable). Two existing cases —
`checkBoxHeightIsLeftAloneWhenAlreadyResized` (`:198`) and `radioButtonHeightIsLeftAloneWhenAlreadyResized`
— set density to compact and then hand-set the height to **60**, which is comfortable's doubled tier.
Under the widened guard that is indistinguishable from a control seeded when the org was comfortable,
so it is re-seeded to 56 and both cases fail.

Their intent — *an author-resized control is never substituted* — is still correct and still worth
covering. 60 is simply a bad value to express it with now. In both cases change the two `60`s to `64`
(64 / 2 = 32, which is not a tier, and 64 is not the legacy 40), leaving the assertion message alone:

```java
      info.setPixelSize(new Dimension(info.getPixelSize().width, 64));

      info.seedChromeDefaults(VizContext.of(VizMark.MODERN_LIGHT));

      assertEquals(64, info.getPixelSize().height, "an author-resized checkbox is never substituted");
```

**This is the only test edit this task may make.** Do not change any other existing case to make the
build pass; if another one fails, report BLOCKED.

- [ ] **Step 6: Add explicit coverage for the doubled-type false positive**

The behaviour that displaced those two values is real and accepted, so it gets its own test rather than
living in the gap where they used to be:

```java
   @Test
   void aDoubledControlHandSizedToATierValueIsAlsoMoved() {
      SreeEnv.setProperty("viewsheet.density", "compact");
      CheckBoxVSAssemblyInfo info = new CheckBoxVSAssemblyInfo();
      info.setPixelSize(new Dimension(info.getPixelSize().width, 60));

      info.seedChromeDefaults(VizContext.of(VizMark.MODERN_LIGHT));

      assertEquals(56, info.getPixelSize().height,
                   "60 is comfortable's doubled tier, so it reads as seeded rather than hand-sized - "
                      + "the same accepted false positive the single-height types take");
   }
```

- [ ] **Step 7: Run the test to verify it passes**

Run: `./mvnw test -pl core -Dtest=ControlHeightFollowDensityTest`
Expected: PASS, all cases. `spinnerHeightIsLeftAloneWhenAlreadyResized` uses height 40, which is not a
single-height tier, so the widening never reached it and it needs no change.

- [ ] **Step 8: Run the seeding suites for regressions**

Run: `./mvnw test -pl core -Dtest=SeedChromeDefaultsTest`
Run: `./mvnw test -pl core -Dtest=VizModernizeUtilTest`
Expected: PASS, both unchanged.

- [ ] **Step 9: Commit**

```
git -C E:/StyleBI/stylebi-enterprise/community add core/src/main/java/inetsoft/uql/viewsheet/internal/ComboBoxVSAssemblyInfo.java core/src/main/java/inetsoft/uql/viewsheet/internal/SpinnerVSAssemblyInfo.java core/src/main/java/inetsoft/uql/viewsheet/internal/SubmitVSAssemblyInfo.java core/src/main/java/inetsoft/uql/viewsheet/internal/TextInputVSAssemblyInfo.java core/src/main/java/inetsoft/uql/viewsheet/internal/CheckBoxVSAssemblyInfo.java core/src/main/java/inetsoft/uql/viewsheet/internal/RadioButtonVSAssemblyInfo.java core/src/test/java/inetsoft/uql/viewsheet/internal/ControlHeightFollowDensityTest.java
```

```
git -C E:/StyleBI/stylebi-enterprise/community commit -m "Control heights follow a later density change

Seeding was guarded on the legacy default, so a control seeded to 24, 28 or
30 could never be re-substituted and a density change would reflow tables
and titles while stranding combo boxes and spinners at the old tier.

Widened to re-fire on any of the three tier heights, reusing the rule
isControlHeight already applies on Revert, including its accepted false
positive: a control hand-sized to exactly a tier value is also moved.

Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>"
```

---

### Task 4: `VizModernizeUtil.applyMark`

The separate Dark switch needs a transition that neither `modernize()` nor `revert()` can express: `MODERN_LIGHT` to `MODERN_DARK`. `modernize()` targets only unmarked content and `revert()` only marked content, so today the flip is a no-op.

**Files:**
- Modify: `core/src/main/java/inetsoft/uql/viewsheet/internal/VizModernizeUtil.java` (`modernize` at `:54`, `revert` at `:99`, add `applyMark`)
- Test: `core/src/test/java/inetsoft/uql/viewsheet/internal/VizModernizeUtilTest.java` (extend)

**Interfaces:**
- Consumes: `VizContext.ofTransition(Viewsheet, VizMark)` from Task 2.
- Produces:
  - `static int VizModernizeUtil.applyMark(Viewsheet vs, VizMark mark)` — stamps and re-seeds every target whose mark **differs** from `mark`, returns how many were touched. Task 6 calls this for a mark change.
  - `static int VizModernizeUtil.reseed(Viewsheet vs)` — re-seeds **every** target under the mark it already holds, stamping nothing, returns how many were seeded. Task 6 calls this for a density-only change.

**Why two methods, not one.** `applyMark` must stay differing-only or it breaks
`modernizeIsIdempotent` and `modernizeLeavesMarkedSiblingsAlone`, which assert on the count of what
actually changed. But a density-only change has no mark difference at all, so a differing-only
traversal collects nothing and the control-height substitution from Task 3 would never fire. `reseed`
is that second traversal. Do not merge them.

- [ ] **Step 1: Write the failing test**

Append to `VizModernizeUtilTest`:

```java
   @Test
   void applyMarkMovesALightSheetToDark() {
      Viewsheet vs = legacySheet();
      gateOn();
      VizModernizeUtil.modernize(vs);

      assertEquals(3, VizModernizeUtil.applyMark(vs, VizMark.MODERN_DARK),
                   "two assemblies plus the sheet itself");

      assertEquals(VizMark.MODERN_DARK, vs.getVSAssemblyInfo().getVizMark());

      for(Assembly assembly : vs.getAssemblies(true)) {
         assertEquals(VizMark.MODERN_DARK,
                      ((VSAssembly) assembly).getVSAssemblyInfo().getVizMark());
      }
   }

   @Test
   void applyMarkIsANoOpWhenNothingDiffers() {
      Viewsheet vs = legacySheet();
      gateOn();
      VizModernizeUtil.modernize(vs);

      assertEquals(0, VizModernizeUtil.applyMark(vs, VizMark.MODERN_LIGHT));
   }

   @Test
   void applyMarkReseedsOnALightToDarkFlip() {
      Viewsheet vs = legacySheet();
      gateOn();
      VizModernizeUtil.modernize(vs);
      TableVSAssembly table = (TableVSAssembly) vs.getAssembly("Table1");
      String light = table.getVSAssemblyInfo().getFormat().getDefaultFormat()
         .getBackgroundValue();

      VizModernizeUtil.applyMark(vs, VizMark.MODERN_DARK);

      assertNotEquals(light, table.getVSAssemblyInfo().getFormat().getDefaultFormat()
         .getBackgroundValue(), "the dark card background replaces the light one");
   }

   @Test
   void applyMarkClearsEveryMarkWhenGivenNull() {
      Viewsheet vs = legacySheet();
      gateOn();
      VizModernizeUtil.modernize(vs);

      assertEquals(3, VizModernizeUtil.applyMark(vs, null));
      assertFalse(VizModernizeUtil.hasMarked(vs));
   }

   @Test
   void applyMarkTouchesOnlyWhatDiffers() {
      Viewsheet vs = legacySheet();
      gateOn();
      TextVSAssembly text = (TextVSAssembly) vs.getAssembly("Text1");
      text.getVSAssemblyInfo().setVizMark(VizMark.MODERN_DARK);

      assertEquals(2, VizModernizeUtil.applyMark(vs, VizMark.MODERN_DARK),
                   "the table and the sheet; the text already matches");
   }

   @Test
   void reseedTouchesEveryTargetWithoutMovingAnyMark() {
      Viewsheet vs = legacySheet();
      gateOn();
      VizModernizeUtil.modernize(vs);

      assertEquals(3, VizModernizeUtil.reseed(vs), "two assemblies plus the sheet itself");
      assertEquals(VizMark.MODERN_LIGHT, vs.getVSAssemblyInfo().getVizMark(), "marks unchanged");
   }

   @Test
   void reseedIsWhatADensityOnlyChangeNeeds() {
      Viewsheet vs = legacySheet();
      gateOn();
      VizModernizeUtil.modernize(vs);

      assertEquals(0, VizModernizeUtil.applyMark(vs, VizMark.MODERN_LIGHT),
                   "applyMark collects nothing when no mark moves, which is why reseed exists");
      assertEquals(3, VizModernizeUtil.reseed(vs));
   }

   @Test
   void reseedLeavesAMixedSheetMixed() {
      Viewsheet vs = legacySheet();
      TextVSAssembly text = (TextVSAssembly) vs.getAssembly("Text1");
      text.getVSAssemblyInfo().setVizMark(VizMark.MODERN_DARK);

      VizModernizeUtil.reseed(vs);

      assertEquals(VizMark.MODERN_DARK, text.getVSAssemblyInfo().getVizMark());
      assertNull(((TableVSAssembly) vs.getAssembly("Table1")).getVSAssemblyInfo().getVizMark());
   }

   @Test
   void applyMarkClearsTheSharedFramesWhenItTouchesAnything() {
      Viewsheet vs = legacySheet();
      gateOn();
      VizModernizeUtil.modernize(vs);
      vs.setSharedFrames(new HashMap<>(Map.of(
         "k", new SharedFrameParameters(new TealColorFrame(), null))));

      VizModernizeUtil.applyMark(vs, VizMark.MODERN_DARK);

      assertTrue(vs.getSharedFrames().isEmpty(),
                 "a render prefers the sheet's shared frame over an assembly's own");
   }
```

Before writing `applyMarkClearsTheSharedFramesWhenItTouchesAnything`, check how the existing `modernize` tests in this file assert on shared frames and mirror that exactly — the file already imports `SharedFrameParameters`, `TealColorFrame` and `Map`, so a matching helper probably exists. If the setter or getter names differ from the above, use the file's own.

- [ ] **Step 2: Run the test to verify it fails**

Run: `./mvnw test -pl core -Dtest=VizModernizeUtilTest`
Expected: FAIL — compilation error, `applyMark` does not exist.

- [ ] **Step 3: Add `applyMark` and make the two operations delegate**

In `VizModernizeUtil.java`, add:

```java
   /**
    * Move every target to one mark: stamp each info whose mark differs, then re-seed it through
    * the hook creation uses. Returns how many were touched. The only route that can express a
    * MODERN_LIGHT to MODERN_DARK flip, which neither of the one-way operations below can reach.
    */
   public static int applyMark(Viewsheet vs, VizMark mark) {
      return seedAll(vs, mark, collect(vs, info -> info.getVizMark() != mark), true);
   }

   /**
    * Re-seed every target under the mark it already holds, stamping nothing. For a density change,
    * which moves no mark and so collects nothing through applyMark, but still has to re-fire the
    * density-derived control-height substitution.
    */
   public static int reseed(Viewsheet vs) {
      return seedAll(vs, null, collect(vs, info -> true), false);
   }

   private static int seedAll(Viewsheet vs, VizMark mark, List<VSAssemblyInfo> targets,
                              boolean stamp)
   {
      for(VSAssemblyInfo info : targets) {
         if(stamp) {
            info.setVizMark(mark);
         }

         info.seedChromeDefaults(VizContext.ofTransition(vs, info.getVizMark()));
      }

      if(!targets.isEmpty()) {
         // seeding rewrote chart colour frames, and a render clones the sheet's shared frame in
         // preference to an assembly's own, so the stale one has to go
         vs.clearSharedFrames();
         vs.clearDimensionColors();
      }

      return targets.size();
   }
```

The context is built per target rather than once, because `reseed` leaves each target on its own mark
and a mixed sheet would otherwise get one sheet-wide context that is wrong for half of it. For
`applyMark` the stamp happens first, so `info.getVizMark()` is already the target mark by the time the
context is built and the result is identical to hoisting it.

Replace `modernize`'s body (keeping its Javadoc and the gate floor). **It keeps its own `unmarked`
collector and shares only the seeding helper — it must not delegate to `applyMark`:**

```java
   public static int modernize(Viewsheet vs) {
      VizMark mark = VizMark.fromGate();
      // unmarked(), not applyMark()'s differing predicate: an already-marked sibling (e.g. dark)
      // must survive a light-gated modernize untouched, which "differs from target mark" would not
      return mark == null ? 0 : seedAll(vs, mark, unmarked(vs), true);
   }
```

`applyMark(vs, MODERN_LIGHT)` would collect an already-`MODERN_DARK` sibling, because its mark differs
from the target, and re-stamp it to light. `modernizeLeavesMarkedSiblingsAlone` pins that as wrong —
`modernize` only ever touches genuinely unmarked content. `revert` has no such problem: "differs from
null" is exactly the same set as "marked", so its delegation is an identity.

Replace `revert`'s body (keeping its Javadoc):

```java
   public static int revert(Viewsheet vs) {
      return applyMark(vs, null);
   }
```

`collect` already takes a predicate, so `unmarked` and `marked` stay for `hasUnmarked` / `hasMarked`. `VizMark` is an enum, so `!=` is identity comparison and correct here.

- [ ] **Step 4: Run the test to verify it passes**

Run: `./mvnw test -pl core -Dtest=VizModernizeUtilTest`
Expected: PASS — **all 34 existing cases unchanged** plus the 6 new ones. If an existing case needs editing, the delegation is wrong: stop and fix the delegation rather than the test.

- [ ] **Step 5: Run the dependent suites**

Run: `./mvnw test -pl core -Dtest=ControlHeightFollowDensityTest`
Run: `./mvnw test -pl core -Dtest=SeedChromeDefaultsTest`
Run: `./mvnw test -pl core -Dtest=ChromeSeedClassificationTest`
Expected: PASS, all unchanged.

- [ ] **Step 6: Commit**

```
git -C E:/StyleBI/stylebi-enterprise/community add core/src/main/java/inetsoft/uql/viewsheet/internal/VizModernizeUtil.java core/src/test/java/inetsoft/uql/viewsheet/internal/VizModernizeUtilTest.java
```

```
git -C E:/StyleBI/stylebi-enterprise/community commit -m "Generalize Modernize and Revert into applyMark

modernize() targeted only unmarked content and revert() only marked, so no
path existed that moves a dashboard from MODERN_LIGHT to MODERN_DARK - the
flip a per-dashboard Dark switch needs.

applyMark stamps and re-seeds every target whose mark differs, and the two
one-way operations become callers of it, so the Modernize bar, the Revert
menu item and the options pane cannot drift apart.

Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>"
```

---

### Task 5: Density reaches the browser on the assembly wrapper

Density takes the shape `viz-modern` and `viz-dark` already have: server-resolved, carried per assembly. This is what makes composer tabs, embedded viewsheets and the Shadow-DOM embed all work without a page-level class.

**Files:**
- Modify: `core/src/main/java/inetsoft/web/viewsheet/model/VSObjectModel.java` (`:103`, getter near `:565`, field near `:607`)
- Modify: `web/projects/portal/src/scss/_viz-tokens.scss:136-169`
- Modify: `web/projects/portal/src/app/vsobjects/objects/vs-object-container.component.html:23-25` and `:47-48`
- Modify: `web/projects/portal/src/app/composer/gui/vs/editor/editable-object-container.component.html:23-24`, `:377-378`, `:406-407`
- Modify: `web/projects/portal/src/app/composer/gui/vs/layouts/layout-object.component.html:74-75`, `:221-222`, `:338-339`, `:350-351`
- Modify: `web/projects/portal/src/app/embed/chart/embed-chart.component.html:23-24`, `:54-55`
- Modify: `web/projects/portal/src/app/vs-wizard/gui/object-wizard/wizard-preview-container.component.html:43-44`, `:110-111`
- Modify: `web/projects/portal/src/app/vs-wizard/gui/objects/vs-wizard-object.component.html:31-32`, `:134-135`
- Modify: `web/projects/portal/src/app/vsview/view/vs-object-view.component.html:25`
- Modify: `web/projects/portal/src/app/vsobjects/model/vs-object-model.ts`
- Modify: `web/projects/portal/src/app/common/util/gui-tool.ts:88-97` (delete `vizDensityMode`)
- Modify: `web/projects/portal/src/app/common/util/gui-tool.spec.ts` (delete its cases)
- Test: `core/src/test/java/inetsoft/web/viewsheet/model/VSObjectModelVizContextTest.java` (extend)

**Test coverage note — read before Step 2.** The server half of this task has a real automated gate:
`VSObjectModelVizContextTest` already exists for exactly this plumbing. The browser half does not, and
this plan does not invent one. No existing spec renders those wrapper templates and asserts on their
DOM classes — `vs-object-container.component.display.tl.spec.ts` tests component methods, and
`gui-tool.spec.ts` / `tooltip.directive.spec.ts` build their DOM by hand. Adding a rendering fixture
for a container that heavy is more test than this binding is worth, so the binding is gated by the
mechanical grep in Step 8 and by the manual checks, and that limit is stated rather than papered over.

**Interfaces:**
- Consumes: `VizContext.of(VSAssemblyInfo).density` from Task 2.
- Produces:
  - `String VSObjectModel.getVizDensity()` — one of the three modes, never null.
  - `vizDensity: string` on the TypeScript `VSObjectModel` interface.

- [ ] **Step 1: Audit every `.viz-modern` binding site before writing code**

Run from `E:/StyleBI/stylebi-enterprise/community`:

```
grep -rn "class.viz-modern" --include=*.html web/projects
```

The file list in **Files** above was taken from this command at `f897b9050`. If it returns a site not listed there, add it — a wrapper that carries `viz-modern` without a density class silently falls back to dense. Record the count in the commit message.

- [ ] **Step 2: Write the failing test**

Append to `VSObjectModelVizContextTest`, matching the file's existing style:

```java
   @Test
   void theModelCarriesTheSheetsResolvedDensity() {
      SreeEnv.setProperty("viewsheet.density", "dense");
      Viewsheet vs = new Viewsheet();
      vs.getViewsheetInfo().setVizDensity("comfortable");
      TextVSAssembly text = new TextVSAssembly(vs, "Text1");
      text.getVSAssemblyInfo().setVizMark(VizMark.MODERN_LIGHT);
      vs.addAssembly(text);

      assertEquals("comfortable", VizContext.of(text.getVSAssemblyInfo()).density,
                   "the model copies this straight onto vizDensity");
   }

   @Test
   void twoSheetsResolveToDifferentDensitiesAtOnce() {
      SreeEnv.setProperty("viewsheet.density", "dense");

      Viewsheet tight = new Viewsheet();
      tight.getViewsheetInfo().setVizDensity("dense");
      TextVSAssembly a = new TextVSAssembly(tight, "Text1");
      a.getVSAssemblyInfo().setVizMark(VizMark.MODERN_LIGHT);
      tight.addAssembly(a);

      Viewsheet roomy = new Viewsheet();
      roomy.getViewsheetInfo().setVizDensity("comfortable");
      TextVSAssembly b = new TextVSAssembly(roomy, "Text1");
      b.getVSAssemblyInfo().setVizMark(VizMark.MODERN_LIGHT);
      roomy.addAssembly(b);

      assertEquals("dense", VizContext.of(a.getVSAssemblyInfo()).density);
      assertEquals("comfortable", VizContext.of(b.getVSAssemblyInfo()).density,
                   "two composer tabs must not share one density");
   }
```

Add `SreeEnv.setProperty("viewsheet.density", null);` to the existing `reset()` method.

- [ ] **Step 3: Run the test to verify it fails**

Run from `E:/StyleBI/stylebi-enterprise/community`:
`./mvnw test -pl core -Dtest=VSObjectModelVizContextTest`

Expected: PASS already for these two, because Task 2 shipped the resolution — they are a regression
gate on the resolution surviving the model change, not a driver for it. **If either fails, Task 2
regressed; stop and fix that before continuing.** The genuinely new code in this task is gated by the
grep in Step 8 and by the manual checks, per the coverage note above.

- [ ] **Step 4: Ship the resolved density on the model**

In `VSObjectModel.java`, at `:103-105`:

```java
      VizContext vizContext = VizContext.of(assemblyInfo);
      vizModern = vizContext.modern;
      vizDark = vizContext.dark;
      vizDensity = vizContext.density;
```

Add the getter beside `isVizDark()` (`:569`):

```java
   public String getVizDensity() {
      return vizDensity;
   }
```

Add the field beside `vizDark` (`:608`):

```java
   private String vizDensity;
```

In `web/projects/portal/src/app/vsobjects/model/vs-object-model.ts`, add to the interface next to `vizDark`:

```ts
   vizDensity: string;
```

- [ ] **Step 5: Bind the class on every wrapper**

At each site listed in **Files**, directly below the existing `[class.viz-dark]` line, add three bindings:

```html
[class.viz-density-dense]="vsObject.vizDensity === 'dense'"
[class.viz-density-compact]="vsObject.vizDensity === 'compact'"
[class.viz-density-comfortable]="vsObject.vizDensity === 'comfortable'"
```

Use whatever expression the neighbouring `[class.viz-modern]` on that line uses — `vsObject`, `model.objectModel`, `childModel`, `vsObject?` — not `vsObject` blindly.

Three separate `[class.x]` bindings rather than one `[class]="'viz-density-' + …"`: the design doc sketched the single-binding form, but these elements carry static `class` attributes and other `[class.x]` bindings, and three boolean bindings avoid any question about how Angular merges a whole-string `[class]` binding with them.

- [ ] **Step 6: Move the density tokens onto the wrapper in SCSS**

In `web/projects/portal/src/scss/_viz-tokens.scss`, the block at `:136` currently reads:

```scss
.viz-modern,
.viz-shell,
.viz-density-dense .viz-modern {
```

Change its selector list to drop the descendant form:

```scss
.viz-modern,
.viz-shell,
.viz-modern.viz-density-dense {
```

At `:148`, change:

```scss
.viz-density-compact.viz-shell,
.viz-density-compact .viz-modern {
```

to:

```scss
.viz-density-compact.viz-shell,
.viz-modern.viz-density-compact {
```

At `:159`, change:

```scss
.viz-density-comfortable.viz-shell,
.viz-density-comfortable .viz-modern {
```

to:

```scss
.viz-density-comfortable.viz-shell,
.viz-modern.viz-density-comfortable {
```

**The descendant forms must be deleted, not kept alongside the compound ones.** `.viz-density-comfortable .viz-modern` and `.viz-modern.viz-density-compact` are both specificity (0,2,0), so keeping both puts the outcome back on source order, where comfortable always wins and per-dashboard density silently does nothing.

Update the block comment at `:127-133` to say the density class is carried per assembly next to `viz-modern`, and that the body class now reaches only `.viz-shell` chrome. Keep it to the same length.

- [ ] **Step 7: Delete the dead body-class reader**

In `gui-tool.ts`, delete `vizDensityMode()` (`:88-97`) and its comment. It reads the body class, which no longer drives assembly density, and it has zero production callers — confirm with:

```
grep -rn "vizDensityMode" --include=*.ts web/projects
```

Expected: only `gui-tool.ts` and `gui-tool.spec.ts`. If anything else appears, stop and re-point it at the element instead of deleting.

Delete the corresponding cases from `gui-tool.spec.ts`.

- [ ] **Step 8: Verify the binding mechanically, then run the tests**

Every wrapper that carries `viz-modern` must now carry the three density bindings. Run from
`E:/StyleBI/stylebi-enterprise/community`:

```
grep -rc "class.viz-modern" --include=*.html web/projects
```

```
grep -rc "class.viz-density-dense" --include=*.html web/projects
```

The two counts must be equal, file by file. This is the gate that stands in for a DOM test — a wrapper
with `viz-modern` and no density class silently falls back to dense, which looks like nothing is wrong.

Run from `E:/StyleBI/stylebi-enterprise/community`:
`./mvnw test -pl core -Dtest=VSObjectModelVizContextTest`
Expected: PASS.

Run from `E:/StyleBI/stylebi-enterprise/community/web`:
`npx ng test portal --include="**/gui-tool.spec.ts"`
Expected: PASS, with the deleted cases gone.

`npx ng run portal:test-tl --include='**/mini-toolbar.component.tl.spec.ts'`
Expected: PASS. This spec asserts on the modern mini-toolbar height and is the nearest thing to a
regression gate on the token change. **Note the target is `portal:test-tl`, not `test portal`** — the
latter matches no `.tl.spec.ts` file and exits 0, which is indistinguishable from a pass.

- [ ] **Step 9: Commit**

```
git -C E:/StyleBI/stylebi-enterprise/community add core/src/main/java/inetsoft/web/viewsheet/model/VSObjectModel.java core/src/test/java/inetsoft/web/viewsheet/model/VSObjectModelVizContextTest.java web/projects/portal/src/scss/_viz-tokens.scss web/projects/portal/src/app/common/util/gui-tool.ts web/projects/portal/src/app/common/util/gui-tool.spec.ts web/projects/portal/src/app/vsobjects/model/vs-object-model.ts web/projects/portal/src/app/vsobjects/objects/vs-object-container.component.html web/projects/portal/src/app/composer/gui/vs/editor/editable-object-container.component.html web/projects/portal/src/app/composer/gui/vs/layouts/layout-object.component.html web/projects/portal/src/app/embed/chart/embed-chart.component.html web/projects/portal/src/app/vs-wizard/gui/object-wizard/wizard-preview-container.component.html web/projects/portal/src/app/vs-wizard/gui/objects/vs-wizard-object.component.html web/projects/portal/src/app/vsview/view/vs-object-view.component.html
```

**Stage explicit paths only — never a directory.** This checkout has pre-existing modified and
untracked files under `web/` (`angular.json`, `vitest-tl.config.ts`, `nul`, several `test-output-*.txt`)
that belong to the user, not to this task. A directory `git add` would sweep them into the commit. If
the Step 1 audit found a wrapper template not in the list above, add that path here too.

```
git -C E:/StyleBI/stylebi-enterprise/community commit -m "Density reaches the browser on the assembly wrapper

Density now has the shape viz-modern and viz-dark already have: resolved on
the server per assembly, carried on the object wrapper. The body class keeps
only its compound .viz-shell form, for org chrome outside any wrapper.

This is what makes the three hard cases work. The composer holds several
dashboards open at once, so a body class cannot express per-dashboard
density. Embedded content was already resolved per assembly, so nothing
nests. And the embed renders into a Shadow DOM with the stylesheet copied
in, which a body class has never matched - it silently took the dense
fallback whatever the org was set to, and now follows its dashboard.

The descendant .viz-density-X .viz-modern rules are deleted rather than kept:
they share specificity (0,2,0) with the compound form, so leaving both would
put the outcome back on source order.

GuiTool.vizDensityMode() goes with them; it read the body class and had no
production callers.

Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>"
```

---

### Task 6: Dialog model and the apply path

Backend plumbing for the three controls. No UI yet.

**Files:**
- Modify: `core/src/main/java/inetsoft/web/composer/model/vs/VSOptionsPaneModel.java` (accessors near `:56`, fields near `:190`)
- Modify: `core/src/main/java/inetsoft/web/composer/vs/dialog/ViewsheetPropertyDialogService.java` (read near `:110`, write near `:290`)
- Test: `core/src/test/java/inetsoft/web/composer/vs/dialog/ViewsheetPropertyVizTest.java` (create)

**Interfaces:**
- Consumes: `VizModernizeUtil.applyMark(Viewsheet, VizMark)` from Task 4; `ViewsheetInfo.getVizDensity()` / `setVizDensity(String)` from Task 1.
- Produces:
  - `boolean VSOptionsPaneModel.isVizModern()` / `setVizModern(boolean)`
  - `boolean VSOptionsPaneModel.isVizDark()` / `setVizDark(boolean)`
  - `String VSOptionsPaneModel.getVizDensity()` / `setVizDensity(String)` — empty string means inherit.

- [ ] **Step 1: Write the failing test**

Create `core/src/test/java/inetsoft/web/composer/vs/dialog/ViewsheetPropertyVizTest.java`. It tests the mark-to-model mapping in isolation rather than the full `@ClusterProxyMethod` service, which needs a runtime viewsheet:

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
package inetsoft.web.composer.vs.dialog;

import inetsoft.sree.SreeEnv;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.viewsheet.TextVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.VizMark;
import inetsoft.uql.viewsheet.internal.VizModernizeUtil;
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
class ViewsheetPropertyVizTest {
   @AfterEach
   void reset() {
      SreeEnv.setProperty("viewsheet.modernVisualization", null);
      SreeEnv.setProperty("viewsheet.darkMode", null);
      SreeEnv.setProperty("viewsheet.density", null);
   }

   @Test
   void aLightSheetReadsAsModernAndNotDark() {
      Viewsheet vs = modernSheet(VizMark.MODERN_LIGHT);

      assertTrue(ViewsheetPropertyDialogService.isVizModern(vs));
      assertFalse(ViewsheetPropertyDialogService.isVizDark(vs));
   }

   @Test
   void aDarkSheetReadsAsBoth() {
      Viewsheet vs = modernSheet(VizMark.MODERN_DARK);

      assertTrue(ViewsheetPropertyDialogService.isVizModern(vs));
      assertTrue(ViewsheetPropertyDialogService.isVizDark(vs));
   }

   @Test
   void aLegacySheetReadsAsNeither() {
      Viewsheet vs = new Viewsheet();
      vs.getVSAssemblyInfo().setVizMark(null);

      assertFalse(ViewsheetPropertyDialogService.isVizModern(vs));
      assertFalse(ViewsheetPropertyDialogService.isVizDark(vs));
   }

   @Test
   void theTargetMarkFollowsTheTwoSwitches() {
      assertNull(ViewsheetPropertyDialogService.targetMark(false, false));
      assertNull(ViewsheetPropertyDialogService.targetMark(false, true),
                 "dark without modern is not a state the mark can hold");
      assertEquals(VizMark.MODERN_LIGHT, ViewsheetPropertyDialogService.targetMark(true, false));
      assertEquals(VizMark.MODERN_DARK, ViewsheetPropertyDialogService.targetMark(true, true));
   }

   @Test
   void applyingTheTargetFlipsAWholeSheet() {
      Viewsheet vs = modernSheet(VizMark.MODERN_LIGHT);

      VizModernizeUtil.applyMark(vs, ViewsheetPropertyDialogService.targetMark(true, true));

      assertEquals(VizMark.MODERN_DARK, vs.getVSAssemblyInfo().getVizMark());

      TextVSAssembly text = (TextVSAssembly) vs.getAssembly("Text1");
      assertEquals(VizMark.MODERN_DARK, text.getVSAssemblyInfo().getVizMark());
   }

   private Viewsheet modernSheet(VizMark mark) {
      SreeEnv.setProperty("viewsheet.modernVisualization", "false");
      Viewsheet vs = new Viewsheet();
      vs.addAssembly(new TextVSAssembly(vs, "Text1"));
      VizModernizeUtil.applyMark(vs, mark);
      return vs;
   }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./mvnw test -pl core -Dtest=ViewsheetPropertyVizTest`
Expected: FAIL — compilation error, the three static helpers do not exist.

- [ ] **Step 3: Add the model fields**

In `VSOptionsPaneModel.java`, add the fields beside `snapGrid` (`:190`):

```java
   private boolean vizModern;
   private boolean vizDark;
   private String vizDensity = "";
```

Add the accessors after `setSnapGrid` (`:60-62`):

```java
   public boolean isVizModern() {
      return vizModern;
   }

   public void setVizModern(boolean vizModern) {
      this.vizModern = vizModern;
   }

   public boolean isVizDark() {
      return vizDark;
   }

   public void setVizDark(boolean vizDark) {
      this.vizDark = vizDark;
   }

   /**
    * The dashboard's own density, or empty to follow the org.
    */
   public String getVizDensity() {
      return vizDensity;
   }

   public void setVizDensity(String vizDensity) {
      this.vizDensity = vizDensity;
   }
```

- [ ] **Step 4: Add the three static helpers to the service**

In `ViewsheetPropertyDialogService.java`, add near the top of the class body:

```java
   /**
    * The sheet's own mark is the dashboard's mode: stamped from the org gate at creation and
    * inherited by every assembly created since.
    */
   static boolean isVizModern(Viewsheet vs) {
      return vs.getVSAssemblyInfo().getVizMark() != null;
   }

   static boolean isVizDark(Viewsheet vs) {
      return vs.getVSAssemblyInfo().getVizMark() == VizMark.MODERN_DARK;
   }

   /** Dark is a modifier of modern, so it cannot be set on its own. */
   static VizMark targetMark(boolean modern, boolean dark) {
      if(!modern) {
         return null;
      }

      return dark ? VizMark.MODERN_DARK : VizMark.MODERN_LIGHT;
   }
```

Add imports for `inetsoft.uql.viewsheet.internal.VizMark` and `inetsoft.uql.viewsheet.internal.VizModernizeUtil`.

- [ ] **Step 5: Run the test to verify it passes**

Run: `./mvnw test -pl core -Dtest=ViewsheetPropertyVizTest`
Expected: PASS, 5 tests.

- [ ] **Step 6: Wire the read side**

In `getViewsheetInfo`, after the `setSnapGrid` line (`:110`):

```java
      vsOptionsPaneModel.setVizModern(isVizModern(viewsheet));
      vsOptionsPaneModel.setVizDark(isVizDark(viewsheet));
      vsOptionsPaneModel.setVizDensity(
         info.getVizDensity() == null ? "" : info.getVizDensity());
```

- [ ] **Step 7: Wire the write side**

In `setViewsheetInfo`, after the `setOnReport` line (`:291`):

```java
      VizMark targetMark =
         targetMark(vsOptionsPaneModel.isVizModern(), vsOptionsPaneModel.isVizDark());
      boolean densityChanged =
         !Tool.equals(info.getVizDensity(), vsOptionsPaneModel.getVizDensity());

      info.setVizDensity(vsOptionsPaneModel.getVizDensity());

      if(targetMark != viewsheet.getVSAssemblyInfo().getVizMark()) {
         VizModernizeUtil.applyMark(viewsheet, targetMark);
      }
      else if(densityChanged) {
         // no mark moved, so applyMark collects nothing; control heights still have to re-fire
         VizModernizeUtil.reseed(viewsheet);
      }
```

**The `else if` is load-bearing.** `applyMark` collects only targets whose mark differs, so a
density-only change collects an empty list and seeds nothing — the control-height substitution from
Task 3 would silently never fire. `reseed` is the traversal that covers that case. Calling `applyMark`
for both is wrong even though it compiles.

**Do not set `reset = true` here.** `reset` drives `rvs.resetRuntime()` at `:473-475`, which is a
*data* reset — it would re-run every query for a change that touches only chrome. The model refresh
this needs is already unconditional: `setViewsheetInfo` calls
`coreLifecycleService.refreshViewsheet(...)` at the end of the method (`:494`) on every save, which
rebuilds `VSObjectModel` for every assembly and is exactly what a mark or density change requires.

- [ ] **Step 8: Run the test and the build**

Run: `./mvnw test -pl core -Dtest=ViewsheetPropertyVizTest`
Expected: PASS.

Run: `./mvnw test -pl core -Dtest=VizModernizeUtilTest`
Expected: PASS, unchanged.

- [ ] **Step 9: Commit**

```
git -C E:/StyleBI/stylebi-enterprise/community add core/src/main/java/inetsoft/web/composer/model/vs/VSOptionsPaneModel.java core/src/main/java/inetsoft/web/composer/vs/dialog/ViewsheetPropertyDialogService.java core/src/test/java/inetsoft/web/composer/vs/dialog/ViewsheetPropertyVizTest.java
```

```
git -C E:/StyleBI/stylebi-enterprise/community commit -m "Options pane model and apply path for the viz override

The two switches read and write the sheet's own VizMark rather than a new
field: it already is a per-dashboard mode, so a second representation would
be one more thing to keep in step. Density is the one genuinely new value.

A density change with no mark change still runs applyMark, because control
heights are density-derived and only the seed re-fires them.

No UI yet.

Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>"
```

---

### Task 7: The Options pane controls

**Files:**
- Modify: `web/projects/portal/src/app/composer/data/vs/viewsheet-options-pane-model.ts`
- Modify: `web/projects/portal/src/app/composer/dialog/vs/viewsheet-options-pane.component.html` (Options fieldset, after the `hideNotifications` switch)
- Modify: `web/projects/portal/src/app/composer/dialog/vs/viewsheet-property-dialog.component.ts` (`saveChanges` at `:107`)
- Test: `web/projects/portal/src/app/composer/dialog/vs/viewsheet-options-pane.component.tl.spec.ts` (extend)
- Test: `web/projects/portal/src/app/composer/dialog/vs/viewsheet-property-dialog.component.tl.spec.ts` (extend)

**Interfaces:**
- Consumes: `VSOptionsPaneModel`'s three new fields from Task 6.
- Produces: no API for later tasks.

- [ ] **Step 1: Confirm the labels — no new properties keys are needed**

`srinter.properties` is **not** modified by this task. Two facts settle it:

- The label form `_#(Some Label)` keys on the English string itself (`Use\ Metadata=Use Metadata` at
  `:3039`), not on a dotted identifier. The dotted form is reserved for longer messages like
  `composer.vs.revert.confirm`.
- An unkeyed label falls back to its own literal. The EM look-and-feel pane already ships
  `_#(Dark Mode)` and `_#(Visualization Density)` with no entry in `srinter.properties`, and they
  render correctly.

So this task reuses the **exact vocabulary the org-level control already uses**, in
`look-and-feel-settings-view.component.html:39-46`, so the per-dashboard control and the org-level one
read the same:

| Label | Keyed? |
|---|---|
| `_#(Modern Visualization)` | yes, `:1880` |
| `_#(Dark Mode)` | no — as EM ships it |
| `_#(Visualization Density)` | no — as EM ships it |
| `_#(Default)` | yes, `:956` |
| `_#(Dense)` / `_#(Compact)` / `_#(Comfortable)` | no — as EM ships them |

Do not invent "Dark" or "Density" as new labels; they would diverge from the org-level pane for no gain.

- [ ] **Step 2: Write the failing test**

This file already has `createModel(overrides)` at `:74`-ish and
`async renderComponent(modelOverrides)` at `:97`, which returns `{ comp, fixture, form, model }`.
First add the three fields to `createModel`'s defaults, beside `worksheet: false`:

```ts
      vizModern: false,
      vizDark: false,
      vizDensity: "",
```

Then append:

```ts
   it("renders the three visualization controls", async () => {
      await renderComponent({ vizModern: true });

      expect(screen.getByLabelText("Modern Visualization")).toBeTruthy();
      expect(screen.getByLabelText("Dark Mode")).toBeTruthy();
      expect(screen.getByLabelText("Visualization Density")).toBeTruthy();
   });

   it("disables Dark while Modern is off", async () => {
      await renderComponent({ vizModern: false });

      expect((screen.getByLabelText("Dark Mode") as HTMLInputElement).disabled).toBe(true);
   });

   it("enables Dark once Modern is on", async () => {
      await renderComponent({ vizModern: true });

      expect((screen.getByLabelText("Dark Mode") as HTMLInputElement).disabled).toBe(false);
   });

   it("clears Dark when Modern is switched off", async () => {
      const { comp, model } = await renderComponent({ vizModern: true, vizDark: true });

      model.vizModern = false;
      comp.vizModernChanged();

      expect(model.vizDark).toBe(false);
   });

   it("offers Default plus the three tiers for density", async () => {
      await renderComponent({ vizModern: true });

      const options = Array.from(
         (screen.getByLabelText("Visualization Density") as HTMLSelectElement).querySelectorAll("option"))
         .map(o => o.textContent.trim());

      expect(options).toEqual(["Default", "Dense", "Compact", "Comfortable"]);
   });
```

Check the file's imports for `screen` from `@testing-library/angular` and add it if absent.

- [ ] **Step 3: Run the test to verify it fails**

Run from `E:/StyleBI/stylebi-enterprise/community/web`:
`npx ng run portal:test-tl --include='**/viewsheet-options-pane.component.tl.spec.ts'`
Expected: FAIL — the controls do not exist.

- [ ] **Step 4: Add the model fields**

In `viewsheet-options-pane-model.ts`, add to the interface:

```ts
   vizModern: boolean;
   vizDark: boolean;
   vizDensity: string;
```

- [ ] **Step 5: Add the controls to the template**

In `viewsheet-options-pane.component.html`, inside the `Options` fieldset, after the `hideNotifications` switch's closing `</div>`:

```html
<div class="col">
  <div class="form-check form-switch">
    <input class="form-check-input" type="checkbox" [(ngModel)]="model.vizModern"
      [ngModelOptions]="{standalone: true}" id="vizModern"
      (ngModelChange)="vizModernChanged()"/>
    <label class="form-check-label" for="vizModern">
      _#(Modern Visualization)
    </label>
  </div>
</div>
<div class="col">
  <div class="form-check form-switch">
    <input class="form-check-input" type="checkbox" [(ngModel)]="model.vizDark"
      [ngModelOptions]="{standalone: true}" id="vizDark" [disabled]="!model.vizModern"/>
    <label class="form-check-label" for="vizDark">
      _#(Dark Mode)
    </label>
  </div>
</div>
```

and, after the multiple-checkboxes row closes, a row for the dropdown matching the pane's other labelled fields:

```html
<div class="row shell-form-row--field">
  <div class="col-4">
    <label class="form-label" for="vizDensity">_#(Visualization Density)</label>
    <select class="form-control" id="vizDensity" [(ngModel)]="model.vizDensity"
      [ngModelOptions]="{standalone: true}">
      <option value="">_#(Default)</option>
      <option value="dense">_#(Dense)</option>
      <option value="compact">_#(Compact)</option>
      <option value="comfortable">_#(Comfortable)</option>
    </select>
  </div>
</div>
```

No comments in the template.

- [ ] **Step 6: Add the switch handler**

In `viewsheet-options-pane.component.ts`, add to the class:

```ts
   vizModernChanged(): void {
      if(!this.model.vizModern) {
         this.model.vizDark = false;
      }
   }
```

- [ ] **Step 7: Run the pane test to verify it passes**

Run: `npx ng run portal:test-tl --include='**/viewsheet-options-pane.component.tl.spec.ts'`
Expected: PASS.

- [ ] **Step 8: Write the failing confirm test**

This file has `createModel(overrides)` at `:74` and `async renderComponent(modelOverrides)` at `:96`,
which returns `{ comp, fixture }`. Extend `createModel`'s `vsOptionsPane` fixture with the three
fields, then append:

```ts
   it("confirms before committing a switch from modern to legacy", async () => {
      const confirm = vi.spyOn(ComponentTool, "showConfirmDialog").mockResolvedValue("yes");
      const { comp } = await renderComponent(
         { vsOptionsPane: { ...createModel().vsOptionsPane, vizModern: true } });

      comp.model.vsOptionsPane.vizModern = false;
      comp.saveChanges();

      expect(confirm).toHaveBeenCalled();
   });

   it("does not confirm when switching from legacy to modern", async () => {
      const confirm = vi.spyOn(ComponentTool, "showConfirmDialog");
      const { comp } = await renderComponent(
         { vsOptionsPane: { ...createModel().vsOptionsPane, vizModern: false } });

      comp.model.vsOptionsPane.vizModern = true;
      comp.saveChanges();

      expect(confirm).not.toHaveBeenCalled();
   });

   it("does not confirm for a density change alone", async () => {
      const confirm = vi.spyOn(ComponentTool, "showConfirmDialog");
      const { comp } = await renderComponent(
         { vsOptionsPane: { ...createModel().vsOptionsPane, vizModern: true } });

      comp.model.vsOptionsPane.vizDensity = "comfortable";
      comp.saveChanges();

      expect(confirm).not.toHaveBeenCalled();
   });
```

`saveChanges()` calls `testScript()`, which posts through `HttpClient`; the file's existing render
helper already provides whatever HTTP double the other cases use, so these three need no new mock
beyond the `ComponentTool` spy. If `renderComponent` does not expose `comp`, read what it does return
and use that — do not add a second helper.

- [ ] **Step 9: Run it to verify it fails**

Run: `npx ng run portal:test-tl --include='**/viewsheet-property-dialog.component.tl.spec.ts'`
Expected: FAIL — no confirm is raised.

- [ ] **Step 10: Snapshot the loaded value and confirm on downgrade**

In `viewsheet-property-dialog.component.ts`, add a field:

```ts
   private loadedVizModern: boolean;
```

Where the model arrives from the server in `ngOnInit`, immediately after it is assigned to `this.model`:

```ts
      this.loadedVizModern = this.model.vsOptionsPane.vizModern;
```

Replace `saveChanges()` (`:107-109`):

```ts
   saveChanges(): void {
      if(this.loadedVizModern && !this.model.vsOptionsPane.vizModern) {
         ComponentTool.showConfirmDialog(this.modalService, "_#(js:Confirm)",
            "_#(js:composer.vs.revert.confirm)", {"yes": "_#(js:Yes)", "no": "_#(js:No)"})
            .then(option => {
               if(option == "yes") {
                  this.testScript(true);
               }
            });

         return;
      }

      this.testScript(true);
   }
```

- [ ] **Step 11: Run both dialog tests to verify they pass**

Run: `npx ng run portal:test-tl --include='**/viewsheet-property-dialog.component.tl.spec.ts'`
Run: `npx ng run portal:test-tl --include='**/viewsheet-options-pane.component.tl.spec.ts'`
Expected: PASS, both.

- [ ] **Step 12: Commit**

```
git -C E:/StyleBI/stylebi-enterprise/community add web/projects/portal/src/app/composer/data/vs/viewsheet-options-pane-model.ts web/projects/portal/src/app/composer/dialog/vs/viewsheet-options-pane.component.html web/projects/portal/src/app/composer/dialog/vs/viewsheet-options-pane.component.ts web/projects/portal/src/app/composer/dialog/vs/viewsheet-options-pane.component.tl.spec.ts web/projects/portal/src/app/composer/dialog/vs/viewsheet-property-dialog.component.ts web/projects/portal/src/app/composer/dialog/vs/viewsheet-property-dialog.component.tl.spec.ts
```

**Stage explicit paths only — never a directory**, for the reason given in Task 5's commit step.

```
git -C E:/StyleBI/stylebi-enterprise/community commit -m "Options pane: modern, dark and density controls

Two switches and a dropdown in the Options fieldset. Dark is disabled while
Modern is off and clears when Modern is switched off, because dark is a
modifier of the mark rather than a state it can hold on its own. Density
offers Default, which leaves the sheet following the org.

Switching Modern off runs a Revert over the dashboard's content, which
discards chrome an author may have been working against, so it takes the
same confirmation the Revert menu item uses. Switching it on, and a density
change on its own, apply silently.

Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>"
```

---

### Task 8: Detached-overlay audit for per-dashboard dark

A dashboard can now be dark in a light org. `viz-shell-dark` on `body` still follows the org gate, and it themes surfaces that sit outside any assembly wrapper. Most of those are portal chrome and should keep following the org — but overlays **owned by a dashboard assembly** and reparented to `body` will disagree with the dashboard they belong to.

This task's deliverable is the audit plus whatever fixes it finds. It may find nothing, in which case the deliverable is the recorded finding.

**Files:**
- Modify: whatever the audit identifies. Likely candidates are the combo-box dropdown list, context menus, and popup/data-tip containers.
- Test: a `*.spec.ts` per surface that needs a fix.

**Interfaces:**
- Consumes: `vizDark` on `VSObjectModel`, already shipped before this plan.
- Produces: nothing for later tasks.

- [ ] **Step 1: Enumerate the reparented surfaces**

Run from `E:/StyleBI/stylebi-enterprise/community`:

```
grep -rn "appendTo\|body.appendChild\|container=\"body\"" --include=*.ts --include=*.html web/projects/portal/src/app/vsobjects web/projects/portal/src/app/widget
```

For each hit, answer two questions and write the answers into the commit message:
1. Is the surface owned by one dashboard assembly, or is it portal chrome?
2. Does it consume any `--inet-viz-*` token that `viz-shell-dark` redefines?

Only a surface that is **both** assembly-owned and token-consuming needs a fix.

- [ ] **Step 2: Read the pattern already used for this**

Read `web/projects/portal/src/app/widget/tooltip/tooltip.directive.ts:205-220` and the `.widget__tooltip--dark` block at `_directives.scss:409-425`. That is the established fix: the directive resolves the assembly's own dark state and puts a class on the reparented element itself, rather than letting it inherit from the shell. Follow it exactly rather than inventing a second mechanism.

- [ ] **Step 3: Fix each surface the audit identified**

For each, write the failing spec first, asserting the reparented element carries the dark class when its owning assembly's `vizDark` is true and the body has no `viz-shell-dark`. Then apply the tooltip pattern. Then run:

`npx ng test portal --include="**/<file>.spec.ts"`

If the audit found nothing, skip to Step 4.

- [ ] **Step 4: Record the result**

If the audit found no surface needing a fix, commit the finding as a comment beside the `viz-shell-dark` toggle in `viewer-app.component.ts:2830`, one clause, naming what was checked.

- [ ] **Step 5: Commit**

```
git -C E:/StyleBI/stylebi-enterprise/community add <the explicit paths the audit changed>
```

**Stage explicit paths only — never a directory**, for the reason given in Task 5's commit step. List
each file the audit actually touched; if it touched none, commit only the one-clause finding comment.

```
git -C E:/StyleBI/stylebi-enterprise/community commit -m "Dark overlays follow their own dashboard

A dashboard can now be dark in a light org, so overlays owned by one of its
assemblies but reparented to body could disagree with the dashboard they
describe. Resolved the same way the tooltip already does it: the owning
assembly's state, on a class the reparented element carries itself, rather
than inheriting from a shell class that follows the org.

Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>"
```

---

## Final verification

- [ ] **Full core suite**

Run from `E:/StyleBI/stylebi-enterprise/community`: `./mvnw test -pl core`
Expected: no new failures against the pre-change baseline. Capture the baseline **before** starting Task 1 — `main` and `epic-74519` are trunk-based and some failures may pre-exist.

- [ ] **Full build with both profiles**

Run from `E:/StyleBI/stylebi-enterprise`: `.\mvnw.cmd install -DskipTests "-Pcommunity,enterprise"`
Expected: BUILD SUCCESS.

- [ ] **Portal unit suite**

Run from `E:/StyleBI/stylebi-enterprise/community/web`:
`npm run test:portal 2>&1 | sed 's/\x1b\[[0-9;]*m//g' | grep -E "Test Files|^ *Tests +[0-9]"`

This prints **two** blocks — portal first, then em. Do not read it with `| tail`; the trailing block is em's and attributing its count to portal looks exactly like a collection gap. Baseline at `f897b9050` is portal 225 files / 1454 tests, em 106 files / 376 tests.

- [ ] **Manual checks, none of which an automated gate covers**

1. Two composer tabs open on dashboards at different densities — each keeps its own row heights.
2. A dashboard set to Legacy in a modern org — adding a new chart to it produces a legacy chart, not a modern one.
3. A dashboard set Modern + Dark in a light org — cards are dark, the portal shell stays light, and a combo-box dropdown inside it agrees with its own assembly.
4. An embedded viewsheet — adopts the host page's density, keeps its own mark.
5. That same embedded asset opened standalone — takes its own density.
6. The embed web component — honours its dashboard's density through the Shadow DOM, which it never did before.
7. A PDF and a PNG export of a dashboard at non-default density — agree with the screen.
8. Switch a dashboard Modern to Legacy from the dialog, confirm, then one undo — the dashboard comes back whole.
9. **The two older routes still work and agree with the dialog.** Open a legacy dashboard in a modern org: the Modernize bar appears, modernizes on click, and the dialog's switch then reads Modern. Use the Revert context-menu item on a modern dashboard: it confirms, reverts, and the dialog's switch then reads Legacy. Task 4 made both delegate to `applyMark`, so this is the check that the delegation behaves as well as compiles.

---

## Notes for the executor

- **`community/CLAUDE.md:66` says to run a single frontend spec with `npx vitest run path/to/spec.ts`. That does not work in this checkout** — it bypasses the builder's setup files and fails with "describe is not defined". Use the `ng test` / `ng run portal:test-tl` invocations given in each task.
- **Task order matters.** Tasks 1 through 4 are a chain: each consumes the previous task's API. Tasks 5, 6 and 7 all depend on 1-4 but only 7 depends on 6. Task 8 is independent of everything and can run any time after Task 7's UI exists to test against.
- **Every identifier in this plan is taken from the codebase at `f897b9050`**, including the frontend test helpers `createModel` and `renderComponent`, which both Task 7 spec files already define. If one has been renamed since, read the file and use its real name rather than adding a second fixture.
- **One gap is deliberate and stated in Task 5:** the per-assembly density class binding has no DOM-level automated gate, because no existing spec renders those wrappers and asserts on their classes. It is covered by a mechanical grep and by manual checks 1, 4, 5 and 6. Do not claim it is unit-tested.
