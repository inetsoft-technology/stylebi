# Text Label Density Height Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A marked Text component takes the form controls' density height (24 / 28 / 30), follows a dashboard density change, and offers Follow default density in Size & Position, with height-only authorship.

**Architecture:** Text joins the author-size API that #6390 added and #6798 extended (`takesDensitySize`, `followsDensitySize`, `defaultSize`, `densitySizeIsHeightOnly`, `seedDensitySize`, `resetSize`), with the calendar's reach. A new text is sized in `initDefaultFormat`, and a following text in `seedDensitySize`, which only `VizModernizeUtil.reseed` (a dashboard density change) calls. The generic composer-resize and dialog helpers then work for text unchanged. Only `TextPropertyDialogService` needs wiring, and layout-only text is put back to the legacy height.

**Tech Stack:** Java 21 (core), JUnit 5 + Spring test context + Mockito. No frontend change.

**Spec:** `docs/superpowers/specs/lookfeel/2026-10-16-text-label-density-design.md`

## Global Constraints

- Marked texts only (`VizContext.modern`, `getVizMark() != null`). Unmarked text behaves exactly as today.
- The height is `VSDensityDefaults.controlHeight(ctx)`: 24 / 28 / 30 at dense / compact / comfortable, 20 unmarked. The "is it following" recognizer is `VSDensityDefaults.isControlHeight(h)` (24/28/30 only). **Never** use `isSeededControlHeight` for text, because it also reads 20 as density-owned.
- Reach: creation (`TextVSAssemblyInfo.initDefaultFormat`) and `VizModernizeUtil.reseed` (via `seedDensitySize`) only. Nothing in `seedChromeDefaults`, restore, Modernize or Revert may change a text's size.
- Auto Size is read with `isAutoSize()`, the runtime value with design fallback, because the warning text sets it at runtime. An Auto Size text never takes, follows or is offered a density size.
- Authorship is height-only (`densitySizeIsHeightOnly()` returns true). The width is always the author's.
- No change to the font, the default width (`AssetUtil.defw`, 100) or any frontend file.
- Source comments are a short clause. No ticket, PR or design-doc references in source comments.
- The branch is based on #6798, rebased on `epic-74519` (2.1.0-SNAPSHOT, not installed locally). Run core tests from the worktree root with `./mvnw -o test -pl core -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=<Classes>`. Never install.
- Git: one git command per Bash call, using `git -C <worktree>`. Never chain with `&&`. `<worktree>` is the root of the `feature-text-label-density` worktree; run every `./mvnw` command from it.

## Review Focus

1. **A legacy text an author sized to exactly 24, 28 or 30, then Modernized and given a density change.** It moves to the tier height: an accepted cost (spec §4), pinned so it is a decision rather than an accident. Task 1, `aLegacyTextAtATierHeightFollowsOnceModernized`.
2. **Auto Size ticked in the same dialog save as a ticked Follow default density.** Auto Size wins and nothing is resized or flagged. Task 3, `autoSizeInTheSameSaveWinsOverATickedFollow`.
3. **A following text inside a bottom-tabs container when the density changes.** It is resized and stays on the strip. Task 1, `aDensityChangeKeepsABottomTabsTextOnTheStrip`.
4. **A dashboard whose own density differs from the org's when a text is dropped.** The dashboard's density decides. Task 1, `aNewTextTakesTheDashboardsDensityOverTheOrgs`.
5. **A text whose Auto Size is set only at runtime (the dashboard warning text).** It is never sized. Task 1, `theWarningTextKeepsTheLegacyHeight`.

---

### Task 1: Text's density size rule

**Files:**
- Modify: `core/src/main/java/inetsoft/uql/viewsheet/internal/TextVSAssemblyInfo.java` (`initDefaultFormat`, about line 63, plus five new overrides after `seedChromeDefaults`)
- Create: `core/src/test/java/inetsoft/uql/viewsheet/internal/TextDensitySizeTest.java`
- Modify: `core/src/test/java/inetsoft/web/composer/vs/objects/controller/ComposerObjectServiceTest.java` (replace `aDraggedTextIsNotFlagged`, about lines 309-322)

**Interfaces:**
- Consumes (existing, on `VSAssemblyInfo`): `takesDensitySize()`, `followsDensitySize()`, `protected Dimension defaultSize(VizContext)`, `densitySizeIsHeightOnly()`, `protected void seedDensitySize(VizContext)`, `isUserSize()`, `resetSize(VizContext)`, `final isAuthorSizeChange(Dimension, Dimension)`. From `VSDensityDefaults`: `static int controlHeight(VizContext)` and `static boolean isControlHeight(int)`. From `VizModernizeUtil`: `static int reseed(Viewsheet)`, `applyMark(Viewsheet, VizMark)`, `revert(Viewsheet)` and `reseedAfterRestore(VSAssemblyInfo)`.
- Produces: `TextVSAssemblyInfo` overrides with these exact signatures: `public boolean takesDensitySize()`, `public boolean followsDensitySize()`, `protected Dimension defaultSize(VizContext ctx)`, `public boolean densitySizeIsHeightOnly()`, `protected void seedDensitySize(VizContext ctx)`. Tasks 2 and 3 rely on `takesDensitySize()` and `followsDensitySize()` behaving as below.

- [ ] **Step 1: Write the failing test**

Create `core/src/test/java/inetsoft/uql/viewsheet/internal/TextDensitySizeTest.java`:

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
import inetsoft.uql.viewsheet.TabVSAssembly;
import inetsoft.uql.viewsheet.TextVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.Dimension;
import java.awt.Point;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class TextDensitySizeTest {
   @AfterEach
   void reset() {
      SreeEnv.setProperty("viewsheet.density", null);
   }

   // created the way the composer creates one: the host's mark is stamped, then initDefaultFormat
   private static TextVSAssembly created(String density, VizMark mark) {
      Viewsheet vs = new Viewsheet();
      vs.getViewsheetInfo().setVizDensity(density);
      vs.getVSAssemblyInfo().setVizMark(mark);
      TextVSAssembly text = new TextVSAssembly(vs, "Text1");
      text.initDefaultFormat();
      vs.addAssembly(text);
      return text;
   }

   private static TextVSAssemblyInfo info(TextVSAssembly text) {
      return (TextVSAssemblyInfo) text.getVSAssemblyInfo();
   }

   // no viewsheet, so the context takes the org's density
   private static TextVSAssemblyInfo marked(String orgDensity, Dimension size) {
      SreeEnv.setProperty("viewsheet.density", orgDensity);
      TextVSAssemblyInfo info = new TextVSAssemblyInfo();
      info.setVizMark(VizMark.MODERN_LIGHT);
      info.setPixelSize(size);
      return info;
   }

   // a bottom-tabs strip sits under its children, so a child the density resizes has to move
   private static TabVSAssembly bottomTabs(Viewsheet vs, String... children) {
      TabVSAssembly tab = new TabVSAssembly(vs, "Tab1");
      TabVSAssemblyInfo tabInfo = (TabVSAssemblyInfo) tab.getVSAssemblyInfo();
      tabInfo.setPixelOffset(new Point(78, 722));
      tabInfo.setPixelSize(new Dimension(400, 24));
      tabInfo.setBottomTabsValue(true);
      vs.addAssembly(tab);
      tab.setAssemblies(children);
      return tab;
   }

   @Test
   void aNewTextTakesItsTiersControlHeight() {
      assertEquals(new Dimension(100, 30),
                   info(created("comfortable", VizMark.MODERN_LIGHT)).getPixelSize());
      assertEquals(new Dimension(100, 28),
                   info(created("compact", VizMark.MODERN_LIGHT)).getPixelSize());
      assertEquals(new Dimension(100, 24),
                   info(created("dense", VizMark.MODERN_LIGHT)).getPixelSize());
   }

   @Test
   void aNewUnmarkedTextKeepsTheLegacyHeight() {
      assertEquals(new Dimension(100, 20), info(created("comfortable", null)).getPixelSize());
   }

   @Test
   void aNewTextTakesTheDashboardsDensityOverTheOrgs() {
      SreeEnv.setProperty("viewsheet.density", "dense");

      assertEquals(new Dimension(100, 30),
                   info(created("comfortable", VizMark.MODERN_LIGHT)).getPixelSize());
   }

   @Test
   void anAutoSizeTextTakesNoDensitySize() {
      TextVSAssemblyInfo info = marked("comfortable", new Dimension(100, 28));
      info.setAutoSizeValue(true);

      assertFalse(info.takesDensitySize());
      assertFalse(info.followsDensitySize());
      info.seedDensitySize(VizContext.of(info));
      assertEquals(new Dimension(100, 28), info.getPixelSize());
   }

   // the warning text turns Auto Size on at runtime before its format is initialized
   @Test
   void theWarningTextKeepsTheLegacyHeight() {
      Viewsheet vs = new Viewsheet();
      vs.getViewsheetInfo().setVizDensity("comfortable");
      vs.getVSAssemblyInfo().setVizMark(VizMark.MODERN_LIGHT);

      assertEquals(20, vs.getWarningTextAssembly(true).getPixelSize().height);
   }

   // 20 is the legacy height, so a text saved before density sizing never reads as following
   @Test
   void onlyATierHeightFollows() {
      assertFalse(marked("compact", new Dimension(100, 20)).followsDensitySize());
      assertTrue(marked("compact", new Dimension(100, 24)).followsDensitySize());
      assertTrue(marked("compact", new Dimension(100, 28)).followsDensitySize());
      assertTrue(marked("compact", new Dimension(100, 30)).followsDensitySize());
      assertFalse(marked("compact", new Dimension(100, 40)).followsDensitySize());
   }

   @Test
   void anAuthorHeightDoesNotFollow() {
      TextVSAssemblyInfo info = marked("comfortable", new Dimension(100, 28));
      info.setUserSize(true);

      assertFalse(info.followsDensitySize());
      info.seedDensitySize(VizContext.of(info));
      assertEquals(new Dimension(100, 28), info.getPixelSize());
   }

   @Test
   void aDashboardDensityChangeMovesAFollowingTextAndKeepsItsWidth() {
      TextVSAssembly text = created("compact", VizMark.MODERN_LIGHT);
      info(text).setPixelSize(new Dimension(240, 28));
      Viewsheet vs = text.getViewsheet();

      vs.getViewsheetInfo().setVizDensity("comfortable");
      VizModernizeUtil.reseed(vs);
      assertEquals(new Dimension(240, 30), info(text).getPixelSize());

      vs.getViewsheetInfo().setVizDensity("dense");
      VizModernizeUtil.reseed(vs);
      assertEquals(new Dimension(240, 24), info(text).getPixelSize());
   }

   @Test
   void aDensityChangeLeavesAnAuthorHeightAlone() {
      TextVSAssembly text = created("compact", VizMark.MODERN_LIGHT);
      info(text).setPixelSize(new Dimension(100, 28));
      info(text).setUserSize(true);
      Viewsheet vs = text.getViewsheet();

      vs.getViewsheetInfo().setVizDensity("comfortable");
      VizModernizeUtil.reseed(vs);

      assertEquals(new Dimension(100, 28), info(text).getPixelSize());
   }

   @Test
   void aDensityChangeLeavesALegacyHeightTextAlone() {
      TextVSAssembly text = created("compact", null);
      Viewsheet vs = text.getViewsheet();
      VizModernizeUtil.applyMark(vs, VizMark.MODERN_LIGHT);

      vs.getViewsheetInfo().setVizDensity("comfortable");
      VizModernizeUtil.reseed(vs);

      assertEquals(new Dimension(100, 20), info(text).getPixelSize());
   }

   // accepted cost: an unflagged legacy text at a tier height reads as following once marked
   @Test
   void aLegacyTextAtATierHeightFollowsOnceModernized() {
      TextVSAssembly text = created("compact", null);
      info(text).setPixelSize(new Dimension(100, 28));
      Viewsheet vs = text.getViewsheet();

      VizModernizeUtil.applyMark(vs, VizMark.MODERN_LIGHT);
      assertEquals(new Dimension(100, 28), info(text).getPixelSize());

      vs.getViewsheetInfo().setVizDensity("comfortable");
      VizModernizeUtil.reseed(vs);
      assertEquals(new Dimension(100, 30), info(text).getPixelSize());
   }

   @Test
   void aDensityChangeKeepsABottomTabsTextOnTheStrip() {
      TextVSAssembly text = created("compact", VizMark.MODERN_LIGHT);
      info(text).setPixelOffset(new Point(78, 722 - 28));
      Viewsheet vs = text.getViewsheet();
      bottomTabs(vs, text.getName());

      vs.getViewsheetInfo().setVizDensity("comfortable");
      VizModernizeUtil.reseed(vs);
      assertEquals(new Dimension(100, 30), info(text).getPixelSize());
      assertEquals(new Point(78, 722 - 30), info(text).getPixelOffset());

      vs.getViewsheetInfo().setVizDensity("dense");
      VizModernizeUtil.reseed(vs);
      assertEquals(new Point(78, 722 - 24), info(text).getPixelOffset());
   }

   @Test
   void aRestoreDoesNotResize() {
      TextVSAssemblyInfo info = marked("comfortable", new Dimension(100, 28));

      VizModernizeUtil.reseedAfterRestore(info);

      assertEquals(new Dimension(100, 28), info.getPixelSize());
   }

   @Test
   void modernizeAndRevertDoNotResize() {
      TextVSAssembly text = created("comfortable", null);
      Viewsheet vs = text.getViewsheet();

      VizModernizeUtil.applyMark(vs, VizMark.MODERN_LIGHT);
      assertEquals(new Dimension(100, 20), info(text).getPixelSize());

      info(text).setPixelSize(new Dimension(100, 30));
      VizModernizeUtil.revert(vs);
      assertEquals(new Dimension(100, 30), info(text).getPixelSize());
   }

   @Test
   void resetSizeReturnsTheTierHeightAndKeepsTheWidth() {
      TextVSAssemblyInfo info = marked("comfortable", new Dimension(240, 44));
      info.setUserSize(true);

      info.resetSize(VizContext.of(info));

      assertEquals(new Dimension(240, 30), info.getPixelSize());
      assertFalse(info.isUserSize());
   }

   @Test
   void onlyAHeightChangeIsTheAuthors() {
      TextVSAssemblyInfo info = marked("compact", new Dimension(100, 28));

      assertTrue(info.densitySizeIsHeightOnly());
      assertFalse(info.isAuthorSizeChange(new Dimension(100, 28), new Dimension(300, 28)));
      assertTrue(info.isAuthorSizeChange(new Dimension(100, 28), new Dimension(100, 40)));
   }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./mvnw -o test -pl core -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=TextDensitySizeTest`

Expected: FAIL. `aNewTextTakesItsTiersControlHeight` fails (expected `100x30`, got `100x20`), `onlyATierHeightFollows` fails (expected true for 24), and so do the density-change, reset, bottom-tabs and height-only tests. The legacy, Auto Size, warning-text, restore and Modernize/Revert guards may already pass; they are controls.

- [ ] **Step 3: Write the implementation**

In `TextVSAssemblyInfo.java`, replace `initDefaultFormat`:

```java
   /**
    * Initialize the default format.
    */
   @Override
   public void initDefaultFormat() {
      setDefaultFormat(true);
      VizContext ctx = VizContext.of(this);

      // a new marked text starts at its tier's control height
      if(ctx.modern && takesDensitySize()) {
         setPixelSize(defaultSize(ctx));
      }
   }
```

Then add these overrides directly after the `seedChromeDefaults` override:

```java
   /**
    * Not reached by a restore, Modernize or Revert, so a text already on a dashboard keeps its
    * height until the dashboard's density changes.
    */
   @Override
   protected void seedDensitySize(VizContext ctx) {
      if(ctx.modern && followsDensitySize()) {
         setPixelSize(defaultSize(ctx));
      }
   }

   // an auto-size text's height comes from its content
   @Override
   public boolean takesDensitySize() {
      return !isAutoSize();
   }

   // only a tier height: 20 is the legacy height, never a seeded one
   @Override
   public boolean followsDensitySize() {
      return takesDensitySize() && !isUserSize() &&
         VSDensityDefaults.isControlHeight(getPixelSize().height);
   }

   @Override
   protected Dimension defaultSize(VizContext ctx) {
      return new Dimension(getPixelSize().width, VSDensityDefaults.controlHeight(ctx));
   }

   @Override
   public boolean densitySizeIsHeightOnly() {
      return true;
   }
```

`Dimension` comes from the existing `import java.awt.*;`. `VizContext` and `VSDensityDefaults` are in the same package.

- [ ] **Step 4: Run the test to verify it passes**

Run: `./mvnw -o test -pl core -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=TextDensitySizeTest`

Expected: PASS, 16 tests.

- [ ] **Step 5: Update the composer drag tests, which pinned text as a type with no density size**

In `ComposerObjectServiceTest.java`, replace the whole `aDraggedTextIsNotFlagged` test with:

```java
   @Test
   void aTallerTextIsFlagged() throws Exception {
      Viewsheet vs = new Viewsheet();
      vs.getVSAssemblyInfo().setName("vs1");
      TextVSAssembly text = new TextVSAssembly(vs, "Text1");
      text.getVSAssemblyInfo().setPixelOffset(new Point(0, 0));
      text.getVSAssemblyInfo().setPixelSize(new Dimension(100, 28));
      vs.addAssembly(text);

      resize(vs, "Text1", 100, 44);

      assertTrue(text.getVSAssemblyInfo().isUserSize());
   }

   @Test
   void aWiderTextIsNotFlagged() throws Exception {
      Viewsheet vs = new Viewsheet();
      vs.getVSAssemblyInfo().setName("vs1");
      TextVSAssembly text = new TextVSAssembly(vs, "Text1");
      text.getVSAssemblyInfo().setPixelOffset(new Point(0, 0));
      text.getVSAssemblyInfo().setPixelSize(new Dimension(100, 28));
      vs.addAssembly(text);

      resize(vs, "Text1", 300, 28);

      assertFalse(text.getVSAssemblyInfo().isUserSize(), "density owns only the height");
   }

   @Test
   void anAutoSizeTextIsNotFlagged() throws Exception {
      Viewsheet vs = new Viewsheet();
      vs.getVSAssemblyInfo().setName("vs1");
      TextVSAssembly text = new TextVSAssembly(vs, "Text1");
      ((TextVSAssemblyInfo) text.getVSAssemblyInfo()).setAutoSizeValue(true);
      text.getVSAssemblyInfo().setPixelOffset(new Point(0, 0));
      text.getVSAssemblyInfo().setPixelSize(new Dimension(100, 20));
      vs.addAssembly(text);

      resize(vs, "Text1", 200, 40);

      assertFalse(text.getVSAssemblyInfo().isUserSize(), "an auto-size text takes no density size");
   }
```

If `TextVSAssemblyInfo` is not already imported in that test, add `import inetsoft.uql.viewsheet.internal.TextVSAssemblyInfo;`.

- [ ] **Step 6: Run the regression set**

Run: `./mvnw -o test -pl core -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest='TextDensitySizeTest,ComposerObjectServiceTest,SeedChromeDefaultsTest,VizModernizeUtilTest,BookmarkChromeResolutionTest,VSObjectChromeDefaultsTest,VSFormatModelTest,AbstractVSAssemblyVizMarkTest,VSObjectModelVizContextTest,GroupingServiceTest,CdataEndAssetRepositoryRoundTripTest,AbstractLayoutTest,ControlDensitySizeTest,ControlHeightFollowDensityTest,BottomTabsReseedTest,CalendarDensitySizeTest,AuthorSizeFlagTest'`

Expected: all pass. If a class outside `TextDensitySizeTest` and `ComposerObjectServiceTest` fails, read the assertion before changing it:
- If it pins **a new marked text's 20px height**, that is the intended change. Update the expectation to the tier height and note it in the commit message.
- Any other failure: stop and report it, without changing production code or the test.

- [ ] **Step 7: Commit**

```bash
git -C <worktree> add core/src/main/java/inetsoft/uql/viewsheet/internal/TextVSAssemblyInfo.java core/src/test/java/inetsoft/uql/viewsheet/internal/TextDensitySizeTest.java core/src/test/java/inetsoft/web/composer/vs/objects/controller/ComposerObjectServiceTest.java
```
```bash
git -C <worktree> commit -m "Give a marked text the form controls' density height, following a dashboard density change"
```

---

### Task 2: Layout-only text keeps the legacy height

**Files:**
- Modify: `core/src/main/java/inetsoft/web/composer/vs/controller/VSLayoutService.java` (`createVSAssembly`, after `assembly.initDefaultFormat();`, about line 140; add an import)
- Test: `core/src/test/java/inetsoft/web/composer/vs/controller/VSLayoutServiceTest.java`

**Interfaces:**
- Consumes: Task 1's creation seed in `TextVSAssemblyInfo.initDefaultFormat()`, and the existing `public VSAssembly createVSAssembly(AddVSLayoutObjectEvent event, AbstractLayout vsLayout, Viewsheet viewsheet, String name)`.
- Produces: no new API.

- [ ] **Step 1: Write the failing test**

Add to `VSLayoutServiceTest.java`:

```java
   // a layout-only text is never reached by a dashboard density change, so it keeps the legacy height
   @Test
   void aLayoutTextKeepsTheLegacyHeightOnAMarkedDashboard() {
      Viewsheet vs = new Viewsheet();
      vs.getViewsheetInfo().setVizDensity("comfortable");
      vs.getVSAssemblyInfo().setVizMark(VizMark.MODERN_LIGHT);
      AddVSLayoutObjectEvent event = new AddVSLayoutObjectEvent();
      event.setType(AbstractSheet.TEXT_ASSET);

      VSAssembly text = service.createVSAssembly(event, null, vs, "Text1");

      assertEquals(20, text.getVSAssemblyInfo().getPixelSize().height);
   }
```

Add these imports if missing: `inetsoft.uql.asset.AbstractSheet`, `inetsoft.uql.viewsheet.internal.VizMark`, and `inetsoft.web.composer.vs.event.AddVSLayoutObjectEvent`.

- [ ] **Step 2: Run the test to verify it fails**

Run: `./mvnw -o test -pl core -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=VSLayoutServiceTest`

Expected: FAIL in `aLayoutTextKeepsTheLegacyHeightOnAMarkedDashboard`, "expected: <20> but was: <30>". The other tests pass.

- [ ] **Step 3: Write the implementation**

In `VSLayoutService.createVSAssembly`, directly after `assembly.initDefaultFormat();`:

```java
      // layout-only text: no dashboard density change reaches it, so it keeps the legacy height
      if(assembly instanceof TextVSAssembly) {
         Dimension size = assembly.getVSAssemblyInfo().getPixelSize();
         assembly.getVSAssemblyInfo().setPixelSize(new Dimension(size.width, AssetUtil.defh));
      }
```

Add `import inetsoft.uql.asset.internal.AssetUtil;`. `TextVSAssembly` and `Dimension` are already covered by `inetsoft.uql.viewsheet.*` and `java.awt.*`.

- [ ] **Step 4: Run the test to verify it passes**

Run: `./mvnw -o test -pl core -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=VSLayoutServiceTest`

Expected: PASS, all tests in the class.

- [ ] **Step 5: Commit**

```bash
git -C <worktree> add core/src/main/java/inetsoft/web/composer/vs/controller/VSLayoutService.java core/src/test/java/inetsoft/web/composer/vs/controller/VSLayoutServiceTest.java
```
```bash
git -C <worktree> commit -m "Keep a print or device layout text at the legacy height"
```

---

### Task 3: The Text dialog offers Follow default density

**Files:**
- Modify: `core/src/main/java/inetsoft/web/composer/vs/dialog/TextPropertyDialogService.java`: the getter after `sizePositionPaneModel.setPositions(pos, size);` (about line 123), and the setter's `dialogService.setAssemblySize(textAssemblyInfo, sizePositionPaneModel);` (about line 259)
- Test: `core/src/test/java/inetsoft/web/composer/vs/dialog/TextPropertyDialogServiceTest.java`
- Test: `core/src/test/java/inetsoft/web/viewsheet/service/SizeFollowsDensityDialogTest.java`

**Interfaces:**
- Consumes: Task 1's `takesDensitySize()`, `followsDensitySize()` and `densitySizeIsHeightOnly()`. Also `VSDialogService.readSizeFollowsDensity(VSAssemblyInfo, SizePositionPaneModel, boolean governed)`, `VSDialogService.applyDensitySize(VSAssemblyInfo, SizePositionPaneModel)`, `VSDialogService.recordAuthorSize(VSAssemblyInfo, SizePositionPaneModel, Dimension shown)` and the instance method `setAssemblySizeFollowingDensity(VSAssemblyInfo, SizePositionPaneModel, Viewsheet)`.
- Produces: no new API.

- [ ] **Step 1: Write the failing service tests**

In `TextPropertyDialogServiceTest.java`, add these tests. Add `@AfterEach` and the other imports listed after the code if missing.

```java
   @AfterEach
   void clearDensity() {
      SreeEnv.setProperty("viewsheet.density", null);
   }

   @Test
   public void the_dialog_offers_follow_default_density_for_a_marked_text() throws Exception {
      SreeEnv.setProperty("viewsheet.density", "compact");
      textVSAssemblyInfoSpy.setVizMark(VizMark.MODERN_LIGHT);
      textVSAssemblyInfoSpy.setPixelSize(new Dimension(100, 28));
      when(dialogService.getAssemblyPosition(any(), any())).thenReturn(new Point(0, 0));
      when(dialogService.getAssemblySize(any(), any())).thenReturn(new Dimension(100, 28));

      TextPropertyDialogModel model = service.getTextPropertyDialogModel("Viewsheet1", "Text1", null);

      SizePositionPaneModel pane = model.getTextGeneralPaneModel().getSizePositionPaneModel();
      assertEquals(Boolean.TRUE, pane.getSizeFollowsDensity());
      assertTrue(pane.isDensityHeightOnly());
   }

   @Test
   public void the_dialog_does_not_offer_it_for_an_auto_size_text() throws Exception {
      textVSAssemblyInfoSpy.setVizMark(VizMark.MODERN_LIGHT);
      textVSAssemblyInfoSpy.setAutoSizeValue(true);
      when(dialogService.getAssemblyPosition(any(), any())).thenReturn(new Point(0, 0));
      when(dialogService.getAssemblySize(any(), any())).thenReturn(new Dimension(100, 28));

      TextPropertyDialogModel model = service.getTextPropertyDialogModel("Viewsheet1", "Text1", null);

      assertNull(model.getTextGeneralPaneModel().getSizePositionPaneModel().getSizeFollowsDensity());
   }

   @Test
   public void saving_writes_the_size_through_the_density_rule() throws Exception {
      BDDMockito.given(textPropertyDialogModel.getTextGeneralPaneModel().getOutputGeneralPaneModel()
                          .getGeneralPropPaneModel().getBasicGeneralPaneModel().getName())
         .willReturn("Text1");

      service.setTextPropertyDialogModel("Viewsheet1", "Text1", textPropertyDialogModel, "", null, commandDispatcher);

      verify(dialogService).setAssemblySizeFollowingDensity(eq(textVSAssemblyInfoSpy), any(), eq(viewsheet));
      verify(dialogService, never()).setAssemblySize(any(VSAssemblyInfo.class), any(SizePositionPaneModel.class));
   }
```

Imports to add if missing:
- `inetsoft.sree.SreeEnv`
- `inetsoft.uql.viewsheet.internal.VizMark`
- `inetsoft.web.composer.model.vs.SizePositionPaneModel`
- `org.junit.jupiter.api.AfterEach`
- `java.awt.Dimension` and `java.awt.Point`
- `static org.junit.jupiter.api.Assertions.*`, replacing the single `assertNotNull` import
- `static org.mockito.ArgumentMatchers.eq`

If the getter throws on an unstubbed mock before reaching the size pane, add a stub for that specific call returning an empty value, such as `when(vsObjectPropertyService.getSupportedPopComponents(any(), any())).thenReturn(new String[0])`. Do not stub `readSizeFollowsDensity`; it is static and has to run for real.

If `getSizePositionPaneModel()` lives on a different getter path in `TextPropertyDialogModel`, follow the path the service itself uses at about line 93, `result.getTextGeneralPaneModel().getSizePositionPaneModel()`.

- [ ] **Step 2: Write the failing helper tests**

In `SizeFollowsDensityDialogTest.java`, add a text factory next to `container(...)`, plus these tests. The class's `@BeforeEach` already sets the org density to comfortable.

```java
   private static TextVSAssemblyInfo text(Dimension size, boolean userSize) {
      TextVSAssemblyInfo info = new TextVSAssemblyInfo();
      info.setVizMark(VizMark.MODERN_LIGHT);
      info.setPixelSize(size);
      info.setUserSize(userSize);
      return info;
   }

   @Test
   void aTickedTextReturnsToTheTierHeightAndKeepsATypedWidth() {
      TextVSAssemblyInfo info = text(new Dimension(240, 44), true);
      SizePositionPaneModel model = model(true, 300, 44);

      VSDialogService.applyDensitySize(info, model);
      VSDialogService.recordAuthorSize(info, model, new Dimension(240, 44));

      assertEquals(300, model.getWidth());
      assertEquals(30, model.getHeight());
      assertFalse(info.isUserSize());
   }

   @Test
   void anUntickedTextClaimsItsHeight() {
      TextVSAssemblyInfo info = text(new Dimension(100, 30), false);

      VSDialogService.recordAuthorSize(info, model(false, 100, 30), new Dimension(100, 30));

      assertTrue(info.isUserSize());
   }

   @Test
   void aTypedWidthAloneLeavesATextFollowing() {
      TextVSAssemblyInfo info = text(new Dimension(100, 30), false);

      VSDialogService.recordAuthorSize(info, model(null, 300, 30), new Dimension(100, 30));

      assertFalse(info.isUserSize());
   }

   @Test
   void aTypedHeightIsATextAuthorsHeight() {
      TextVSAssemblyInfo info = text(new Dimension(100, 30), false);

      VSDialogService.recordAuthorSize(info, model(null, 100, 40), new Dimension(100, 30));

      assertTrue(info.isUserSize());
   }

   @Test
   void autoSizeInTheSameSaveWinsOverATickedFollow() {
      TextVSAssemblyInfo info = text(new Dimension(100, 44), true);
      info.setAutoSizeValue(true);
      SizePositionPaneModel model = model(true, 100, 44);

      VSDialogService.applyDensitySize(info, model);
      VSDialogService.recordAuthorSize(info, model, new Dimension(100, 44));

      assertEquals(new Dimension(100, 44), info.getPixelSize());
      assertEquals(44, model.getHeight());
      assertTrue(info.isUserSize(), "the earlier claim stays, since Auto Size writes nothing");
   }

   @Test
   void readDoesNotOfferTheCheckboxForAnUnmarkedText() {
      TextVSAssemblyInfo info = text(new Dimension(100, 20), false);
      info.setVizMark(null);
      SizePositionPaneModel model = new SizePositionPaneModel();

      VSDialogService.readSizeFollowsDensity(info, model, true);

      assertNull(model.getSizeFollowsDensity());
   }
```

Add imports if missing: `inetsoft.uql.viewsheet.internal.TextVSAssemblyInfo` and `inetsoft.uql.viewsheet.internal.VizMark`.

- [ ] **Step 3: Run the tests to verify they fail**

Run: `./mvnw -o test -pl core -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest='TextPropertyDialogServiceTest,SizeFollowsDensityDialogTest'`

Expected:
- `the_dialog_offers_follow_default_density_for_a_marked_text` fails (expected TRUE, got null: the getter never reads it).
- `saving_writes_the_size_through_the_density_rule` fails (`setAssemblySizeFollowingDensity` was never called).
- `the_dialog_does_not_offer_it_for_an_auto_size_text` already passes; it is a control, and must still pass after Step 4.
- The six `SizeFollowsDensityDialogTest` text cases already pass, because Task 1 made text take a density size. They pin the helper behaviour for text.

- [ ] **Step 4: Write the implementation**

In `TextPropertyDialogService.getTextPropertyDialogModel`, directly after `sizePositionPaneModel.setPositions(pos, size);`:

```java
      VSDialogService.readSizeFollowsDensity(textAssemblyInfo, sizePositionPaneModel, true);
```

In `setTextPropertyDialogModel`, replace:

```java
      dialogService.setAssemblySize(textAssemblyInfo, sizePositionPaneModel);
```

with:

```java
      dialogService.setAssemblySizeFollowingDensity(textAssemblyInfo, sizePositionPaneModel, vs);
```

This must stay after `textAssemblyInfo.setAutoSizeValue(textPaneModel.isAutoSize());`, which it already is, so a newly ticked Auto Size is seen. `vs` is the setter's local `Viewsheet`.

- [ ] **Step 5: Run the tests to verify they pass**

Run: `./mvnw -o test -pl core -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest='TextPropertyDialogServiceTest,SizeFollowsDensityDialogTest,TextDensitySizeTest'`

Expected: PASS for all three classes.

- [ ] **Step 6: Commit**

```bash
git -C <worktree> add core/src/main/java/inetsoft/web/composer/vs/dialog/TextPropertyDialogService.java core/src/test/java/inetsoft/web/composer/vs/dialog/TextPropertyDialogServiceTest.java core/src/test/java/inetsoft/web/viewsheet/service/SizeFollowsDensityDialogTest.java
```
```bash
git -C <worktree> commit -m "Offer Follow default density for a text's height in its dialog"
```

---

### Task 4: Verification and manual checks

**Files:**
- Create: `docs/superpowers/plans/2026-10-16-text-label-density-manual-checks.md`

**Interfaces:**
- Consumes: Tasks 1-3.
- Produces: the manual checklist the user runs on a live server.

- [ ] **Step 1: Run the full core suite**

Run: `./mvnw -o test -pl core -am`

Expected: 0 failures, apart from the known timing test `RelFaultInjectionTest`. If that one fails, rerun it alone with `-Dtest=RelFaultInjectionTest` and record that it passes alone. Report any other failure with its assertion and stop.

- [ ] **Step 2: Write the manual checklist**

Create `docs/superpowers/plans/2026-10-16-text-label-density-manual-checks.md` with these checks. Each one gets a Result column for the user to fill in.

```markdown
# Text label density height — manual checks

Server built from `feature-text-label-density`. One dashboard per tier (dense, compact, comfortable), plus a legacy (unmarked) copy.

| # | Check | Expected |
|---|---|---|
| MT-1 | Drop a Text on each tier's dashboard and on the legacy copy | 100×24 / 100×28 / 100×30; legacy 100×20 |
| MT-2 | Put a Text beside a Text Input at compact | Both 28 tall; the label and the input's text on one line |
| MT-3 | Widen a new text, then change the dashboard density (Properties → density) | The height follows the tier; the width is kept |
| MT-4 | Drag a text taller, then change the density | The dragged height is kept |
| MT-5 | Text properties → Size & Position | Follow default density shown and ticked; Width editable; Height locked |
| MT-6 | Untick it, change the density | The height is kept. Tick it again: back to the tier height, width kept |
| MT-7 | Turn on Auto Size | No Follow checkbox; a density change leaves the text alone |
| MT-8 | Modernize the legacy copy | Texts stay 20; the checkbox is shown unticked |
| MT-9 | Revert a modern dashboard | Texts keep 24 / 28 / 30 |
| MT-10 | Print layout: add a Text | 20 tall |
| MT-11 | A text in a bottom-tabs container, then change the density | Its bottom stays on the tab strip |
| MT-12 | Export PNG, PDF and HTML at each tier | Text boxes at the stored heights; nothing clipped |
```

- [ ] **Step 3: Commit**

```bash
git -C <worktree> add docs/superpowers/plans/2026-10-16-text-label-density-manual-checks.md
```
```bash
git -C <worktree> commit -m "Add the text label density manual checks"
```
