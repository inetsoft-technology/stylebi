# Calendar Density Default Size Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A new calendar's default box follows density (row heights 38 / 34 / 30), set at creation and on a dashboard density change, with the follow-the-default-density checkbox in its property dialog.

**Architecture:** `VSDensityDefaults` gains a calendar size and a recognizer for it. A new, empty `VSAssemblyInfo.seedDensitySize(VizContext)` hook is overridden only by `CalendarVSAssemblyInfo`. It is called from the calendar's `initDefaultFormat` (creation) and from `VizModernizeUtil.reseed` (dashboard density change), and from nowhere else, so restores, Modernize and Revert never resize a calendar. Author sizes are protected by PR #6390's `userSize` flag, and the calendar dialog gets #6390's three size-checkbox calls.

**Tech Stack:** Java 21, JUnit 5, Mockito, Spring test context (`@SreeHome`), Maven (`./mvnw`, module `core`).

**Spec:** `docs/superpowers/specs/lookfeel/2026-10-06-calendar-density-size-design.md`

## Global Constraints

- **Prerequisite:** PR #6390 merged into `epic-74519`, and this branch rebased onto it (Task 0). Nothing else starts before that.
- **Numbers:** row heights comfortable 38, compact 34, dense 30. Month band 36. Rows 7. Width 300 at every tier. Legacy 300×300.
- **Tier boxes:** comfortable 300×332, compact 300×300, dense 300×266. The recognized set is exactly {300×300, 300×332, 300×266}.
- **Reach:** the size rule runs at creation (`CalendarVSAssemblyInfo.initDefaultFormat`) and in `VizModernizeUtil.reseed` only. Never in `seedChromeDefaults`, `seedAll` or `reseedAfterRestore`.
- **Guard:** `ctx.modern && followsDensitySize()`, where `followsDensitySize()` is `!isUserSize() && VSDensityDefaults.isSeededCalendarSize(getPixelSize())`.
- **Checkbox:** offered only when `getShowTypeValue() == CALENDAR_SHOW_TYPE && getViewModeValue() == SINGLE_CALENDAR_MODE`, plus the helper's own marked/`takesDensitySize` test.
- **Unchanged:** the export painter `VSCalendar`, every calendar template and stylesheet, `fixCalendarSize()`, `fitCalendarHeightToTitle()`, `CALENDAR_BODY_HEIGHT`, the Toggle Double Calendar action, the constructor's `new Dimension(300, 300)`, and the bottom-tabs branch of `CalendarPropertyDialogService`. No frontend change.
- **Shared checkout:** other Claude sessions use this same checkout. Run `git branch --show-current` immediately before every `git add` and `git commit`, and expect `feature-calendar-density-size`. Run each git command as its own call; never chain them with `&&`.
- **Comments:** keep code comments to a short clause. Never mention the spec, this plan, slices or PR numbers in source.
- **Commits:** message body in plain prose, ending with the line `Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>`. Never push; the user pushes.

## Review Focus

1. **Ticking the checkbox on a calendar inside a bottom-tabs container** keeps the calendar's bottom edge on the tab strip, because the dialog recomputes its top from the new height. Pinned in Task 4 (`tickingInBottomTabsKeepsTheBottomOnTheTabStrip`).
2. **A dashboard whose own density differs from the org's:** a density change sizes by the dashboard's value. Pinned in Task 3 (`theDashboardsOwnDensityWinsOverTheOrgs`).
3. **Changing the dashboard density repeatedly** (compact → comfortable → dense → compact) keeps following, including back to 300×300, which is also the legacy size. Pinned in Task 3 (`aDashboardDensityChangeMovesAFollowingCalendar`).
4. **Pressing OK in the dialog for an unrelated change** (a title edit) on a following calendar leaves it following. Pinned in Task 4 (`anUnchangedSizeKeepsFollowing`).
5. **An unrecognized density value** sizes the calendar like dense and throws nothing. Pinned in Task 1 (`anUnrecognizedDensityTakesTheDenseSize`).

## How to run tests

`-q` hides the summary on success, so read the surefire report after each run:

```bash
cd /e/StyleBI/stylebi-enterprise/community
./mvnw -q test -pl core -Dtest=ClassName -Dsurefire.failIfNoSpecifiedTests=false
grep -o 'tests="[0-9]*"\|failures="[0-9]*"\|errors="[0-9]*"\|skipped="[0-9]*"' core/target/surefire-reports/TEST-<fully.qualified.ClassName>.xml
```

A `NoSuchMethodError` from a class you did not touch means a stale `target/` from another branch: add `clean` and rerun.

## File Structure

| File | Responsibility |
|---|---|
| `core/src/main/java/inetsoft/uql/viewsheet/internal/VSDensityDefaults.java` | `calendarSize`, `isSeededCalendarSize`, the calendar row matrix and constants |
| `core/src/main/java/inetsoft/uql/viewsheet/internal/VSAssemblyInfo.java` | the empty `seedDensitySize` hook |
| `core/src/main/java/inetsoft/uql/viewsheet/internal/CalendarVSAssemblyInfo.java` | opts in: `takesDensitySize`, `followsDensitySize`, `defaultSize`, `seedDensitySize`; calls the hook at creation |
| `core/src/main/java/inetsoft/uql/viewsheet/internal/VizModernizeUtil.java` | `reseed` calls the hook on each target |
| `core/src/main/java/inetsoft/web/composer/vs/dialog/CalendarPropertyDialogService.java` | reads and applies the size checkbox |
| `core/src/test/java/inetsoft/uql/viewsheet/internal/CalendarDensitySizeTest.java` | new: sizes, recognizer, seed and reach |
| `core/src/test/java/inetsoft/web/composer/vs/dialog/CalendarSizeFollowsDensityTest.java` | new: the dialog checkbox, read and save |
| `core/src/test/java/inetsoft/web/composer/vs/dialog/CalendarPropertyDialogServiceTest.java` | one stub so its save path survives the new size read |
| `core/src/test/java/inetsoft/web/composer/vs/objects/controller/ComposerObjectServiceTest.java` | two calendar drag-resize cases |

---

### Task 0: Rebase onto `epic-74519` after #6390 merges

**Files:** none changed.

**Interfaces:**
- Consumes: nothing.
- Produces: a branch containing #6390's `VSAssemblyInfo.isUserSize()`, `setUserSize(boolean)`, `takesDensitySize()`, `followsDensitySize()`, `protected Dimension defaultSize(VizContext)`, `resetSize(VizContext)`, and the static `VSDialogService.readSizeFollowsDensity(VSAssemblyInfo, SizePositionPaneModel, boolean)`, `applyDensitySize(VSAssemblyInfo, SizePositionPaneModel)` and `recordAuthorSize(VSAssemblyInfo, SizePositionPaneModel, Dimension)`. All later tasks use these.

- [ ] **Step 1: Confirm #6390 has merged**

Run: `gh pr view 6390 --json state -q .state`
Expected: `MERGED`. If it prints anything else, stop and tell the user. Do not start Task 1.

- [ ] **Step 2: Confirm the branch and a clean tree**

Run: `git branch --show-current`
Expected: `feature-calendar-density-size`

Run: `git status --short --untracked-files=no`
Expected: no output.

- [ ] **Step 3: Fetch**

Run: `git fetch origin`

- [ ] **Step 4: Rebase**

Run: `git rebase origin/epic-74519`
Expected: success. Only the two spec commits replay, so there should be no conflicts.

- [ ] **Step 5: Confirm the #6390 machinery is present**

Run: `git grep -n "public boolean takesDensitySize\|public boolean followsDensitySize\|public void resetSize\|public boolean isUserSize" -- core/src/main/java/inetsoft/uql/viewsheet/internal/VSAssemblyInfo.java`
Expected: 4 lines.

Run: `git grep -n "public static void readSizeFollowsDensity\|public static void applyDensitySize\|public static void recordAuthorSize" -- core/src/main/java/inetsoft/web/viewsheet/service/VSDialogService.java`
Expected: 3 lines.

Run: `git grep -n "CONTAINER_LANES = 12" -- core/src/main/java/inetsoft/uql/viewsheet/internal/VSDensityDefaults.java`
Expected: 1 line.

- [ ] **Step 6: Baseline the calendar tests**

Run: `./mvnw -q test -pl core "-Dtest=CalendarPropertyDialogServiceTest,CalendarVSAssemblyInfoFixSizeTest,ContainerDensitySizeTest" -Dsurefire.failIfNoSpecifiedTests=false`
Expected: every report shows `failures="0"` and `errors="0"`. `CalendarPropertyDialogServiceTest` has one test skipped by `@Disabled`, which is expected. If anything else fails, stop and report it: the failure predates this work.

---

### Task 1: The calendar size and its recognizer

**Files:**
- Modify: `core/src/main/java/inetsoft/uql/viewsheet/internal/VSDensityDefaults.java`
- Create: `core/src/test/java/inetsoft/uql/viewsheet/internal/CalendarDensitySizeTest.java`

**Interfaces:**
- Consumes: `VizContext.of(VizMark)`, `VizContext.modern`, `VizContext.density`, and the existing package-private `VSDensityDefaults.titleHeightForMode(String)`.
- Produces:
  - `public static Dimension VSDensityDefaults.calendarSize(VizContext ctx)`
  - `public static boolean VSDensityDefaults.isSeededCalendarSize(Dimension size)`
  - `static int VSDensityDefaults.calendarRowHeightForMode(String mode)`

- [ ] **Step 1: Write the failing test**

Create `core/src/test/java/inetsoft/uql/viewsheet/internal/CalendarDensitySizeTest.java`:

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

import java.awt.Dimension;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class CalendarDensitySizeTest {
   @AfterEach
   void reset() {
      SreeEnv.setProperty("viewsheet.density", null);
   }

   private static Dimension sizeAt(String density) {
      SreeEnv.setProperty("viewsheet.density", density);
      return VSDensityDefaults.calendarSize(VizContext.of(VizMark.MODERN_LIGHT));
   }

   @Test
   void eachTierStacksItsRowsUnderTheLaneAndBand() {
      assertEquals(new Dimension(300, 332), sizeAt("comfortable"));
      assertEquals(new Dimension(300, 300), sizeAt("compact"));
      assertEquals(new Dimension(300, 266), sizeAt("dense"));
   }

   @Test
   void unmarkedIsTheLegacySize() {
      SreeEnv.setProperty("viewsheet.density", "comfortable");
      assertEquals(new Dimension(300, 300),
                   VSDensityDefaults.calendarSize(VizContext.of((VizMark) null)));
   }

   // every density matrix falls back to dense for a value it does not know
   @Test
   void anUnrecognizedDensityTakesTheDenseSize() {
      assertEquals(new Dimension(300, 266), sizeAt("spacious"));
   }

   @Test
   void theRecognizerAcceptsOnlySeededSizes() {
      assertTrue(VSDensityDefaults.isSeededCalendarSize(new Dimension(300, 300)));
      assertTrue(VSDensityDefaults.isSeededCalendarSize(new Dimension(300, 332)));
      assertTrue(VSDensityDefaults.isSeededCalendarSize(new Dimension(300, 266)));
      assertFalse(VSDensityDefaults.isSeededCalendarSize(new Dimension(300, 162)));
      assertFalse(VSDensityDefaults.isSeededCalendarSize(new Dimension(600, 300)));
      assertFalse(VSDensityDefaults.isSeededCalendarSize(new Dimension(300, 301)));
      assertFalse(VSDensityDefaults.isSeededCalendarSize(null));
   }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./mvnw -q test -pl core -Dtest=CalendarDensitySizeTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: test compilation fails with `cannot find symbol` for `calendarSize` and `isSeededCalendarSize`.

- [ ] **Step 3: Implement**

In `VSDensityDefaults.java`, insert this block immediately after the `CONTAINER_LANES` constant (`private static final int CONTAINER_LANES = 12;` and its javadoc):

```java
   /**
    * The default size of a calendar: its title lane, the month band and seven rows - the weekday
    * header and six weeks. Rows fill the box, so this is the only way density reaches them.
    * Compact equals the legacy 300x300, so the default tier is unchanged. Density has no opinion
    * on the width.
    */
   public static Dimension calendarSize(VizContext ctx) {
      if(!ctx.modern) {
         return legacyCalendarSize();
      }

      return calendarSizeForMode(ctx.density);
   }

   /**
    * Whether a size is one calendarSize() could have written. Anything else is an author size.
    */
   public static boolean isSeededCalendarSize(Dimension size) {
      if(size == null) {
         return false;
      }

      return size.equals(legacyCalendarSize())
         || size.equals(calendarSizeForMode(COMFORTABLE))
         || size.equals(calendarSizeForMode(COMPACT))
         || size.equals(calendarSizeForMode(DENSE));
   }

   /**
    * The pre-density default, read by both the producer and the recognizer so they cannot drift.
    * Must equal the size CalendarVSAssemblyInfo's constructor sets.
    */
   private static Dimension legacyCalendarSize() {
      return new Dimension(CALENDAR_WIDTH, LEGACY_CALENDAR_HEIGHT);
   }

   private static Dimension calendarSizeForMode(String mode) {
      return new Dimension(CALENDAR_WIDTH, titleHeightForMode(mode) + CALENDAR_NAV_BAND
         + CALENDAR_ROWS * calendarRowHeightForMode(mode));
   }

   private static final int CALENDAR_WIDTH = 300;
   private static final int LEGACY_CALENDAR_HEIGHT = 300;

   /**
    * The browser's month band, which holds the 32px navigation buttons at every tier.
    */
   private static final int CALENDAR_NAV_BAND = 36;

   /**
    * The weekday header and six weeks.
    */
   private static final int CALENDAR_ROWS = 7;
```

Then insert this method immediately after `controlHeightForMode(String mode)`:

```java
   /**
    * Calendar row height for a density mode. Unrecognized modes fall back to dense.
    */
   static int calendarRowHeightForMode(String mode) {
      switch(mode) {
      case COMFORTABLE:
         return 38;
      case COMPACT:
         return 34;
      default:
         return 30;
      }
   }
```

- [ ] **Step 4: Run it to verify it passes**

Run: `./mvnw -q test -pl core -Dtest=CalendarDensitySizeTest -Dsurefire.failIfNoSpecifiedTests=false`
Then: `grep -o 'tests="[0-9]*"\|failures="[0-9]*"\|errors="[0-9]*"' core/target/surefire-reports/TEST-inetsoft.uql.viewsheet.internal.CalendarDensitySizeTest.xml`
Expected: `tests="4"`, `failures="0"`, `errors="0"`.

- [ ] **Step 5: Commit**

Run: `git branch --show-current` (expect `feature-calendar-density-size`)

Run: `git add core/src/main/java/inetsoft/uql/viewsheet/internal/VSDensityDefaults.java core/src/test/java/inetsoft/uql/viewsheet/internal/CalendarDensitySizeTest.java`

Run:
```bash
git commit -m "Give the calendar a density default size

Rows fill a calendar's box, so density reaches them only through the box. Row heights 38/34/30 under
the title lane and the 36px month band, 300 wide, with compact equal to the legacy 300x300.

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

### Task 2: The calendar opts in, and is sized at creation

**Files:**
- Modify: `core/src/main/java/inetsoft/uql/viewsheet/internal/VSAssemblyInfo.java`
- Modify: `core/src/main/java/inetsoft/uql/viewsheet/internal/CalendarVSAssemblyInfo.java`
- Test: `core/src/test/java/inetsoft/uql/viewsheet/internal/CalendarDensitySizeTest.java`

**Interfaces:**
- Consumes: `VSDensityDefaults.calendarSize(VizContext)` and `isSeededCalendarSize(Dimension)` from Task 1, and #6390's `isUserSize()` and `resetSize(VizContext)`.
- Produces:
  - `protected void VSAssemblyInfo.seedDensitySize(VizContext ctx)`, empty by default. Task 3 calls it from `VizModernizeUtil`, which is in the same package.
  - `CalendarVSAssemblyInfo.takesDensitySize()` returns true, `followsDensitySize()`, `defaultSize(VizContext)` and `seedDensitySize(VizContext)` overrides. Task 4's dialog code and #6390's resize code read them.

- [ ] **Step 1: Write the failing tests**

In `CalendarDensitySizeTest.java`, add these imports:

```java
import inetsoft.uql.viewsheet.CalendarVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
```

Add these helpers below `sizeAt`:

```java
   // created the way the composer creates one: the host's mark is stamped, then initDefaultFormat
   private static CalendarVSAssembly created(String density, VizMark mark) {
      Viewsheet vs = new Viewsheet();
      vs.getViewsheetInfo().setVizDensity(density);
      vs.getVSAssemblyInfo().setVizMark(mark);
      CalendarVSAssembly calendar = new CalendarVSAssembly(vs, "Calendar1");
      calendar.initDefaultFormat();
      vs.addAssembly(calendar);
      return calendar;
   }

   private static CalendarVSAssemblyInfo info(CalendarVSAssembly calendar) {
      return (CalendarVSAssemblyInfo) calendar.getVSAssemblyInfo();
   }

   // no viewsheet, so the context takes the org's density
   private static CalendarVSAssemblyInfo marked(String orgDensity, Dimension size) {
      SreeEnv.setProperty("viewsheet.density", orgDensity);
      CalendarVSAssemblyInfo info = new CalendarVSAssemblyInfo();
      info.setVizMark(VizMark.MODERN_LIGHT);
      info.setPixelSize(size);
      return info;
   }
```

Add these tests:

```java
   @Test
   void aNewCalendarTakesItsTiersBox() {
      assertEquals(new Dimension(300, 332),
                   info(created("comfortable", VizMark.MODERN_LIGHT)).getPixelSize());
      assertEquals(new Dimension(300, 300),
                   info(created("compact", VizMark.MODERN_LIGHT)).getPixelSize());
      assertEquals(new Dimension(300, 266),
                   info(created("dense", VizMark.MODERN_LIGHT)).getPixelSize());
   }

   @Test
   void aNewUnmarkedCalendarKeepsTheLegacySize() {
      assertEquals(new Dimension(300, 300), info(created("comfortable", null)).getPixelSize());
   }

   @Test
   void theConstructorSizeIsTheLegacySize() {
      assertEquals(VSDensityDefaults.calendarSize(VizContext.of((VizMark) null)),
                   new CalendarVSAssemblyInfo().getPixelSize());
   }

   @Test
   void theSeedLeavesAnAuthorFlaggedBoxAlone() {
      CalendarVSAssemblyInfo info = marked("comfortable", new Dimension(300, 300));
      info.setUserSize(true);

      info.seedDensitySize(VizContext.of(info));

      assertEquals(new Dimension(300, 300), info.getPixelSize());
   }

   @Test
   void theSeedLeavesASizeOffTheRecognizedSetAlone() {
      CalendarVSAssemblyInfo info = marked("comfortable", new Dimension(400, 300));

      info.seedDensitySize(VizContext.of(info));

      assertEquals(new Dimension(400, 300), info.getPixelSize());
   }

   @Test
   void theSeedLeavesAnUnmarkedCalendarAlone() {
      CalendarVSAssemblyInfo info = marked("comfortable", new Dimension(300, 266));
      info.setVizMark(null);

      info.seedDensitySize(VizContext.of(info));

      assertEquals(new Dimension(300, 266), info.getPixelSize());
   }

   // a dropdown draws only its stored width, so its height can follow harmlessly
   @Test
   void aDropdownIsSizedToo() {
      CalendarVSAssemblyInfo info = marked("comfortable", new Dimension(300, 300));
      info.setShowTypeValue(CalendarVSAssemblyInfo.DROPDOWN_SHOW_TYPE);

      info.seedDensitySize(VizContext.of(info));

      assertEquals(new Dimension(300, 332), info.getPixelSize());
   }

   @Test
   void aToggledDoubleWidthLeavesTheRuleAndTogglingBackReturnsIt() {
      CalendarVSAssemblyInfo info = marked("comfortable", new Dimension(600, 332));
      assertFalse(info.followsDensitySize());

      info.setPixelSize(new Dimension(300, 332));
      assertTrue(info.followsDensitySize());
   }

   @Test
   void resetSizeReturnsAnAuthorSizeToTheTier() {
      CalendarVSAssemblyInfo info = marked("comfortable", new Dimension(400, 500));
      info.setUserSize(true);
      assertTrue(info.takesDensitySize());

      info.resetSize(VizContext.of(info));

      assertEquals(new Dimension(300, 332), info.getPixelSize());
      assertFalse(info.isUserSize());
   }
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./mvnw -q test -pl core -Dtest=CalendarDensitySizeTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: test compilation fails with `cannot find symbol` for `seedDensitySize`.

- [ ] **Step 3: Add the hook to `VSAssemblyInfo`**

In `VSAssemblyInfo.java`, insert immediately after the `resetSize(VizContext ctx)` method:

```java
   /**
    * Write this type's density size. Runs at creation and on a dashboard density change only, not
    * on a restore, Modernize or Revert. Empty for a type with no size rule, or whose rule runs in
    * seedChromeDefaults.
    */
   protected void seedDensitySize(VizContext ctx) {
   }
```

- [ ] **Step 4: Opt the calendar in**

In `CalendarVSAssemblyInfo.java`, replace the body of `initDefaultFormat()`:

```java
   @Override
   public void initDefaultFormat() {
      setFormatInfo(normalDefault.clone());
      setCSSDefaults();
      titleInfo.setTitleHeightValue(36);
      // this type never calls setDefaultFormat, so the base's hook call is unreachable
      VizContext ctx = VizContext.of(this);
      seedChromeDefaults(ctx);
      seedDensitySize(ctx);
   }
```

Then insert immediately after the `seedChromeDefaults(VizContext ctx)` override (the method ending in `applyCalendarSeed(getFormatInfo(), ctx, prototype(getShowType()));`):

```java
   /**
    * Not reached by a restore, Modernize or Revert, so a calendar already on a dashboard keeps its
    * box until the dashboard's density changes.
    */
   @Override
   protected void seedDensitySize(VizContext ctx) {
      if(ctx.modern && followsDensitySize()) {
         setPixelSize(VSDensityDefaults.calendarSize(ctx));
      }
   }

   @Override
   public boolean takesDensitySize() {
      return true;
   }

   @Override
   public boolean followsDensitySize() {
      return !isUserSize() && VSDensityDefaults.isSeededCalendarSize(getPixelSize());
   }

   @Override
   protected Dimension defaultSize(VizContext ctx) {
      return VSDensityDefaults.calendarSize(ctx);
   }
```

`Dimension` is already available through the file's `import java.awt.*;`.

- [ ] **Step 5: Run the tests to verify they pass**

Run: `./mvnw -q test -pl core -Dtest=CalendarDensitySizeTest -Dsurefire.failIfNoSpecifiedTests=false`
Then: `grep -o 'tests="[0-9]*"\|failures="[0-9]*"\|errors="[0-9]*"' core/target/surefire-reports/TEST-inetsoft.uql.viewsheet.internal.CalendarDensitySizeTest.xml`
Expected: `tests="13"`, `failures="0"`, `errors="0"`.

- [ ] **Step 6: Check the existing calendar suites are unaffected**

Run: `./mvnw -q test -pl core "-Dtest=CalendarVSAssemblyInfoFixSizeTest,CalendarVSAssemblyInfoModeDisplayValueTest,CalendarPropertyDialogServiceTest" -Dsurefire.failIfNoSpecifiedTests=false`
Expected: every report shows `failures="0"` and `errors="0"`. Those tests build `CalendarVSAssemblyInfo` without a mark, so the new seed never acts in them.

- [ ] **Step 7: Commit**

Run: `git branch --show-current` (expect `feature-calendar-density-size`)

Run: `git add core/src/main/java/inetsoft/uql/viewsheet/internal/VSAssemblyInfo.java core/src/main/java/inetsoft/uql/viewsheet/internal/CalendarVSAssemblyInfo.java core/src/test/java/inetsoft/uql/viewsheet/internal/CalendarDensitySizeTest.java`

Run:
```bash
git commit -m "Size a new calendar for its density tier

A separate seed hook, so the size is written at creation without riding seedChromeDefaults onto
every restore. The calendar takes a density size, follows it while the box is one the rule wrote and
the author has not sized it, and resets to it from the size checkbox.

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

### Task 3: A dashboard density change resizes a following calendar

**Files:**
- Modify: `core/src/main/java/inetsoft/uql/viewsheet/internal/VizModernizeUtil.java` (`reseed`)
- Test: `core/src/test/java/inetsoft/uql/viewsheet/internal/CalendarDensitySizeTest.java`

**Interfaces:**
- Consumes: `VSAssemblyInfo.seedDensitySize(VizContext)` and the calendar's override from Task 2. The `created`, `info` and `marked` helpers in `CalendarDensitySizeTest` from Task 2.
- Produces: `VizModernizeUtil.reseed(Viewsheet)` with the same signature and return value (the number of targets seeded), now also calling `seedDensitySize` on each target.

- [ ] **Step 1: Write the failing tests and the guards**

Add to `CalendarDensitySizeTest.java`:

```java
   @Test
   void aDashboardDensityChangeMovesAFollowingCalendar() {
      CalendarVSAssembly calendar = created("compact", VizMark.MODERN_LIGHT);
      Viewsheet vs = calendar.getViewsheet();

      vs.getViewsheetInfo().setVizDensity("comfortable");
      VizModernizeUtil.reseed(vs);
      assertEquals(new Dimension(300, 332), info(calendar).getPixelSize());

      vs.getViewsheetInfo().setVizDensity("dense");
      VizModernizeUtil.reseed(vs);
      assertEquals(new Dimension(300, 266), info(calendar).getPixelSize());

      vs.getViewsheetInfo().setVizDensity("compact");
      VizModernizeUtil.reseed(vs);
      assertEquals(new Dimension(300, 300), info(calendar).getPixelSize());
   }

   @Test
   void theDashboardsOwnDensityWinsOverTheOrgs() {
      SreeEnv.setProperty("viewsheet.density", "comfortable");
      CalendarVSAssembly calendar = created("compact", VizMark.MODERN_LIGHT);
      Viewsheet vs = calendar.getViewsheet();

      vs.getViewsheetInfo().setVizDensity("dense");
      VizModernizeUtil.reseed(vs);

      assertEquals(new Dimension(300, 266), info(calendar).getPixelSize());
   }

   @Test
   void aDensityChangeLeavesAnAuthorSizeAlone() {
      CalendarVSAssembly calendar = created("compact", VizMark.MODERN_LIGHT);
      info(calendar).setUserSize(true);
      Viewsheet vs = calendar.getViewsheet();

      vs.getViewsheetInfo().setVizDensity("comfortable");
      VizModernizeUtil.reseed(vs);

      assertEquals(new Dimension(300, 300), info(calendar).getPixelSize());
   }

   // guards: these paths must never resize, before or after the reseed change
   @Test
   void aRestoreDoesNotResize() {
      CalendarVSAssemblyInfo info = marked("comfortable", new Dimension(300, 300));

      VizModernizeUtil.reseedAfterRestore(info);

      assertEquals(new Dimension(300, 300), info.getPixelSize());
   }

   @Test
   void modernizeAndRevertDoNotResize() {
      CalendarVSAssembly calendar = created("comfortable", null);
      Viewsheet vs = calendar.getViewsheet();

      VizModernizeUtil.applyMark(vs, VizMark.MODERN_LIGHT);
      assertEquals(new Dimension(300, 300), info(calendar).getPixelSize());

      info(calendar).setPixelSize(new Dimension(300, 332));
      VizModernizeUtil.revert(vs);
      assertEquals(new Dimension(300, 332), info(calendar).getPixelSize());
   }
```

- [ ] **Step 2: Run them to verify the right ones fail**

Run: `./mvnw -q test -pl core -Dtest=CalendarDensitySizeTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: `aDashboardDensityChangeMovesAFollowingCalendar` and `theDashboardsOwnDensityWinsOverTheOrgs` fail (the size stays 300×300). `aDensityChangeLeavesAnAuthorSizeAlone`, `aRestoreDoesNotResize` and `modernizeAndRevertDoNotResize` pass already; they are guards.

- [ ] **Step 3: Implement**

In `VizModernizeUtil.java`, replace `reseed`:

```java
   /**
    * Re-seed every target under the mark it already holds, stamping nothing. For a density change,
    * which moves no mark and so collects nothing through applyMark, but still has to re-fire the
    * density-derived control-height substitution and move a density size.
    */
   public static int reseed(Viewsheet vs) {
      List<VSAssemblyInfo> targets = collect(vs, info -> true);
      // mark is inert here: nothing is stamped, so every target keeps the one it has
      int seeded = seedAll(vs, null, targets, false, false);

      // the one re-seed that moves a box: a restore, Modernize and Revert leave it where it is
      for(VSAssemblyInfo info : targets) {
         info.seedDensitySize(VizContext.of(info));
      }

      return seeded;
   }
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./mvnw -q test -pl core -Dtest=CalendarDensitySizeTest -Dsurefire.failIfNoSpecifiedTests=false`
Then: `grep -o 'tests="[0-9]*"\|failures="[0-9]*"\|errors="[0-9]*"' core/target/surefire-reports/TEST-inetsoft.uql.viewsheet.internal.CalendarDensitySizeTest.xml`
Expected: `tests="18"`, `failures="0"`, `errors="0"`.

- [ ] **Step 5: Check the other re-seed users are unaffected**

Run: `./mvnw -q test -pl core "-Dtest=ContainerDensitySizeTest,SelectionDensitySizeTest,AuthorSizeFlagTest,VSDensityDefaultsTest" -Dsurefire.failIfNoSpecifiedTests=false`
Expected: every report shows `failures="0"` and `errors="0"`.

- [ ] **Step 6: Commit**

Run: `git branch --show-current` (expect `feature-calendar-density-size`)

Run: `git add core/src/main/java/inetsoft/uql/viewsheet/internal/VizModernizeUtil.java core/src/test/java/inetsoft/uql/viewsheet/internal/CalendarDensitySizeTest.java`

Run:
```bash
git commit -m "Move a following calendar on a dashboard density change

The density re-seed is the one re-seed that writes a density size. A restore, Modernize and Revert
still leave the box alone, so an already-modern calendar keeps its size until its dashboard's
density changes.

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

### Task 4: The author's size: the dialog checkbox and drag-resize

**Files:**
- Modify: `core/src/main/java/inetsoft/web/composer/vs/dialog/CalendarPropertyDialogService.java`
- Create: `core/src/test/java/inetsoft/web/composer/vs/dialog/CalendarSizeFollowsDensityTest.java`
- Modify: `core/src/test/java/inetsoft/web/composer/vs/dialog/CalendarPropertyDialogServiceTest.java`
- Modify: `core/src/test/java/inetsoft/web/composer/vs/objects/controller/ComposerObjectServiceTest.java`

**Interfaces:**
- Consumes: the calendar's `takesDensitySize()`, `followsDensitySize()` and `defaultSize(VizContext)` from Task 2; #6390's static `VSDialogService.readSizeFollowsDensity`, `applyDensitySize` and `recordAuthorSize`; `SizePositionPaneModel.setSizeFollowsDensity(Boolean)` and `getSizeFollowsDensity()`.
- Produces: `CalendarPropertyDialogService.getCalendarPropertyModel(...)` fills `SizePositionPaneModel.sizeFollowsDensity`, and `setCalendarPropertyModel(...)` applies it. No signatures change.

- [ ] **Step 1: Keep the existing dialog test's save path working**

The save path will read `dialogService.getAssemblySize(...)` and copy it into a new `Dimension`. In `CalendarPropertyDialogServiceTest` that is a mock returning null, which would throw. Add the stub #6390 added to the selection list's test.

In `CalendarPropertyDialogServiceTest.java`, add the imports:

```java
import inetsoft.uql.viewsheet.internal.VSAssemblyInfo;
```
```java
import static org.mockito.Mockito.lenient;
```

At the end of `setup()`, after the `service = new CalendarPropertyDialogService(...)` statement, add:

```java
      lenient().when(dialogService.getAssemblySize(any(), any()))
         .thenAnswer(inv -> ((VSAssemblyInfo) inv.getArgument(0)).getPixelSize());
```

- [ ] **Step 2: Write the failing dialog tests**

Create `core/src/test/java/inetsoft/web/composer/vs/dialog/CalendarSizeFollowsDensityTest.java`:

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

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.sree.SreeEnv;
import inetsoft.test.*;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.web.binding.handler.VSAssemblyInfoHandler;
import inetsoft.web.composer.model.vs.CalendarPropertyDialogModel;
import inetsoft.web.composer.model.vs.SizePositionPaneModel;
import inetsoft.web.composer.vs.objects.controller.VSObjectPropertyService;
import inetsoft.web.composer.vs.objects.controller.VSTrapService;
import inetsoft.web.portal.controller.database.QueryManagerService;
import inetsoft.web.viewsheet.service.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.*;
import java.security.Principal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

/**
 * The size checkbox is offered for a marked single calendar only, and the dialog's save applies it.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@ExtendWith(MockitoExtension.class)
@Tag("core")
class CalendarSizeFollowsDensityTest {
   @BeforeEach
   void setup() throws Exception {
      SreeEnv.setProperty("viewsheet.density", "comfortable");
      service = new CalendarPropertyDialogService(
         vsObjectPropertyService, vsOutputService, dialogService, engine, trapService,
         assemblyInfoHandler, mock(QueryManagerService.class));
      lenient().when(engine.getViewsheet(anyString(), nullable(Principal.class))).thenReturn(rvs);
      lenient().when(rvs.getViewsheet()).thenReturn(viewsheet);
      lenient().when(viewsheet.getAssembly(anyString())).thenReturn(calendarAssembly);
      lenient().when(dialogService.getAssemblyPosition(any(), any())).thenReturn(new Point(0, 0));
      lenient().when(dialogService.getAssemblySize(any(), any()))
         .thenAnswer(inv -> ((VSAssemblyInfo) inv.getArgument(0)).getPixelSize());
   }

   @AfterEach
   void reset() {
      SreeEnv.setProperty("viewsheet.density", null);
   }

   @Test
   void aMarkedSingleCalendarIsOfferedTicked() throws Exception {
      assertEquals(Boolean.TRUE, read(calendar(VizMark.MODERN_LIGHT, new Dimension(300, 332))));
   }

   @Test
   void aMarkedCalendarAtAnAuthorSizeIsOfferedUnticked() throws Exception {
      assertEquals(Boolean.FALSE, read(calendar(VizMark.MODERN_LIGHT, new Dimension(400, 300))));
   }

   @Test
   void noCheckboxForAnUnmarkedCalendarADropdownOrADoubleCalendar() throws Exception {
      assertNull(read(calendar(null, new Dimension(300, 300))));

      CalendarVSAssemblyInfo dropdown = calendar(VizMark.MODERN_LIGHT, new Dimension(300, 332));
      dropdown.setShowTypeValue(CalendarVSAssemblyInfo.DROPDOWN_SHOW_TYPE);
      assertNull(read(dropdown));

      CalendarVSAssemblyInfo twoMonths = calendar(VizMark.MODERN_LIGHT, new Dimension(600, 332));
      twoMonths.setViewModeValue(CalendarVSAssemblyInfo.DOUBLE_CALENDAR_MODE);
      assertNull(read(twoMonths));
   }

   @Test
   void tickingWritesTheTierSizeAndClearsTheFlag() throws Exception {
      CalendarVSAssemblyInfo info = calendar(VizMark.MODERN_LIGHT, new Dimension(400, 500));
      info.setUserSize(true);

      CalendarVSAssemblyInfo result = save(info, Boolean.TRUE, new Dimension(400, 500));

      assertEquals(new Dimension(300, 332), result.getPixelSize());
      assertFalse(result.isUserSize());
   }

   @Test
   void untickingFlagsTheSize() throws Exception {
      CalendarVSAssemblyInfo result =
         save(calendar(VizMark.MODERN_LIGHT, new Dimension(300, 332)), Boolean.FALSE,
              new Dimension(300, 332));

      assertEquals(new Dimension(300, 332), result.getPixelSize());
      assertTrue(result.isUserSize());
   }

   @Test
   void aTypedSizeFlagsTheSize() throws Exception {
      CalendarVSAssemblyInfo result =
         save(calendar(VizMark.MODERN_LIGHT, new Dimension(300, 332)), null,
              new Dimension(300, 250));

      assertEquals(new Dimension(300, 250), result.getPixelSize());
      assertTrue(result.isUserSize());
   }

   // OK for an unrelated change leaves the box following
   @Test
   void anUnchangedSizeKeepsFollowing() throws Exception {
      CalendarVSAssemblyInfo result =
         save(calendar(VizMark.MODERN_LIGHT, new Dimension(300, 332)), null,
              new Dimension(300, 332));

      assertFalse(result.isUserSize());
      assertTrue(result.followsDensitySize());
   }

   @Test
   void tickingInBottomTabsKeepsTheBottomOnTheTabStrip() throws Exception {
      TabVSAssemblyInfo tabInfo = new TabVSAssemblyInfo();
      tabInfo.setBottomTabsValue(true);
      tabInfo.setPixelOffset(new Point(0, 420));
      TabVSAssembly tab = mock(TabVSAssembly.class);
      when(tab.getVSAssemblyInfo()).thenReturn(tabInfo);
      when(calendarAssembly.getContainer()).thenReturn(tab);

      CalendarVSAssemblyInfo info = calendar(VizMark.MODERN_LIGHT, new Dimension(300, 400));
      info.setPixelOffset(new Point(50, 20));
      info.setUserSize(true);

      CalendarVSAssemblyInfo result = save(info, Boolean.TRUE, new Dimension(300, 400));

      assertEquals(new Dimension(300, 332), result.getPixelSize());
      assertEquals(new Point(50, 420 - 332), result.getPixelOffset());
   }

   private static CalendarVSAssemblyInfo calendar(VizMark mark, Dimension size) {
      CalendarVSAssemblyInfo info = new CalendarVSAssemblyInfo();
      info.setVizMark(mark);
      info.setTitleHeightValue(36);
      info.setPixelOffset(new Point(0, 0));
      info.setPixelSize(size);
      return info;
   }

   private Boolean read(CalendarVSAssemblyInfo info) throws Exception {
      lenient().when(calendarAssembly.getVSAssemblyInfo()).thenReturn(info);
      CalendarPropertyDialogModel result =
         service.getCalendarPropertyModel("Viewsheet1", "Calendar1", null);
      return result.getCalendarGeneralPaneModel().getSizePositionPaneModel()
         .getSizeFollowsDensity();
   }

   /**
    * Save through the dialog. The size pane is a real model: the checkbox writes the tier size into
    * it, and a deep stub would drop that write.
    */
   private CalendarVSAssemblyInfo save(CalendarVSAssemblyInfo info, Boolean follows,
                                       Dimension typed) throws Exception
   {
      when(calendarAssembly.getVSAssemblyInfo()).thenReturn(info);
      doCallRealMethod().when(dialogService)
         .setAssemblySize(any(), any(SizePositionPaneModel.class));
      doCallRealMethod().when(dialogService).setAssemblySize(any(), anyInt(), anyInt());

      SizePositionPaneModel size = new SizePositionPaneModel();
      size.setSizeFollowsDensity(follows);
      size.setWidth(typed.width);
      size.setHeight(typed.height);
      size.setTitleHeight(info.getTitleHeightValue());
      when(model.getCalendarGeneralPaneModel().getSizePositionPaneModel()).thenReturn(size);
      given(model.getCalendarGeneralPaneModel().getGeneralPropPaneModel()
               .getBasicGeneralPaneModel().getName())
         .willReturn("Calendar1");
      given(model.getCalendarAdvancedPaneModel().getShowType())
         .willReturn(info.getShowTypeValue());
      given(model.getCalendarAdvancedPaneModel().getViewMode())
         .willReturn(info.getViewModeValue());

      service.setCalendarPropertyModel("Viewsheet1", "Calendar1", model, "", null,
                                       commandDispatcher);

      ArgumentCaptor<CalendarVSAssemblyInfo> captor =
         ArgumentCaptor.forClass(CalendarVSAssemblyInfo.class);
      verify(vsObjectPropertyService).editObjectProperty(
         any(RuntimeViewsheet.class), captor.capture(), any(String.class), any(String.class),
         any(String.class), nullable(Principal.class), any(CommandDispatcher.class));
      return captor.getValue();
   }

   @Mock VSObjectPropertyService vsObjectPropertyService;
   @Mock VSOutputService vsOutputService;
   @Mock CommandDispatcher commandDispatcher;
   @Mock RuntimeViewsheet rvs;
   @Mock ViewsheetService engine;
   @Mock VSTrapService trapService;
   @Mock VSDialogService dialogService;
   @Mock VSAssemblyInfoHandler assemblyInfoHandler;
   @Mock CalendarVSAssembly calendarAssembly;
   @Mock(answer = Answers.RETURNS_DEEP_STUBS)
   private Viewsheet viewsheet;
   @Mock(answer = Answers.RETURNS_DEEP_STUBS)
   private CalendarPropertyDialogModel model;
   private CalendarPropertyDialogService service;
}
```

- [ ] **Step 3: Add the drag-resize cases**

In `ComposerObjectServiceTest.java`, add after `aDraggedTreeIsFlagged`:

```java
   @Test
   void aDraggedCalendarIsFlagged() throws Exception {
      Viewsheet vs = new Viewsheet();
      vs.getVSAssemblyInfo().setName("vs1");
      CalendarVSAssembly calendar = new CalendarVSAssembly(vs, "Calendar1");
      calendar.getVSAssemblyInfo().setPixelOffset(new Point(0, 0));
      calendar.getVSAssemblyInfo().setPixelSize(new Dimension(300, 332));
      vs.addAssembly(calendar);

      resize(vs, "Calendar1", 300, 400);

      assertTrue(calendar.getVSAssemblyInfo().isUserSize());
   }

   @Test
   void aCalendarResizedToItsOwnSizeIsNotFlagged() throws Exception {
      Viewsheet vs = new Viewsheet();
      vs.getVSAssemblyInfo().setName("vs1");
      CalendarVSAssembly calendar = new CalendarVSAssembly(vs, "Calendar1");
      calendar.getVSAssemblyInfo().setPixelOffset(new Point(0, 0));
      calendar.getVSAssemblyInfo().setPixelSize(new Dimension(300, 332));
      vs.addAssembly(calendar);

      resize(vs, "Calendar1", 300, 332);

      assertFalse(calendar.getVSAssemblyInfo().isUserSize(), "a position-only change is not the author's size");
   }
```

`CalendarVSAssembly` is covered by the file's `import inetsoft.uql.viewsheet.*;`.

- [ ] **Step 4: Run them to verify the right ones fail**

Run: `./mvnw -q test -pl core "-Dtest=CalendarSizeFollowsDensityTest,ComposerObjectServiceTest" -Dsurefire.failIfNoSpecifiedTests=false`
Expected in `CalendarSizeFollowsDensityTest`: `aMarkedSingleCalendarIsOfferedTicked`, `aMarkedCalendarAtAnAuthorSizeIsOfferedUnticked`, `tickingWritesTheTierSizeAndClearsTheFlag`, `untickingFlagsTheSize`, `aTypedSizeFlagsTheSize` and `tickingInBottomTabsKeepsTheBottomOnTheTabStrip` fail. `noCheckboxFor...` and `anUnchangedSizeKeepsFollowing` already pass. Both new `ComposerObjectServiceTest` cases pass already, because the resize path flags any type that `takesDensitySize()` (Task 2).

- [ ] **Step 5: Implement the read**

In `CalendarPropertyDialogService.getCalendarPropertyModel`, insert immediately after `sizePositionPaneModel.setContainer(calendarAssembly.getContainer() != null);`:

```java
      // a dropdown draws no stored height, and the rule's 300 width would squeeze a double calendar
      VSDialogService.readSizeFollowsDensity(calendarAssemblyInfo, sizePositionPaneModel,
         calendarAssemblyInfo.getShowTypeValue() == CalendarVSAssemblyInfo.CALENDAR_SHOW_TYPE &&
            calendarAssemblyInfo.getViewModeValue() == CalendarVSAssemblyInfo.SINGLE_CALENDAR_MODE);
```

- [ ] **Step 6: Implement the save**

In `CalendarPropertyDialogService.setCalendarPropertyModel`, replace the single line

```java
      dialogService.setAssemblySize(info, sizePositionPaneModel);
```

with

```java
      Dimension shownSize =
         new Dimension(dialogService.getAssemblySize(info, viewsheet.getViewsheet()));
      VSDialogService.applyDensitySize(info, sizePositionPaneModel);
      dialogService.setAssemblySize(info, sizePositionPaneModel);
      VSDialogService.recordAuthorSize(info, sizePositionPaneModel, shownSize);
```

In this method `viewsheet` is the `RuntimeViewsheet`. `Dimension` comes from the file's `import java.awt.*;`, and `VSDialogService` from `import inetsoft.web.viewsheet.service.*;`. The bottom-tabs branch further down reads `info.getPixelSize()` after this, so it repositions against the tier size.

- [ ] **Step 7: Run the tests to verify they pass**

Run: `./mvnw -q test -pl core "-Dtest=CalendarSizeFollowsDensityTest,CalendarPropertyDialogServiceTest,ComposerObjectServiceTest" -Dsurefire.failIfNoSpecifiedTests=false`
Then: `grep -o 'tests="[0-9]*"\|failures="[0-9]*"\|errors="[0-9]*"\|skipped="[0-9]*"' core/target/surefire-reports/TEST-inetsoft.web.composer.vs.dialog.CalendarSizeFollowsDensityTest.xml core/target/surefire-reports/TEST-inetsoft.web.composer.vs.dialog.CalendarPropertyDialogServiceTest.xml core/target/surefire-reports/TEST-inetsoft.web.composer.vs.objects.controller.ComposerObjectServiceTest.xml`
Expected: `CalendarSizeFollowsDensityTest` `tests="8"` with 0 failures and 0 errors; the other two with 0 failures and 0 errors, and `CalendarPropertyDialogServiceTest` still `skipped="1"`.

- [ ] **Step 8: Commit**

Run: `git branch --show-current` (expect `feature-calendar-density-size`)

Run: `git add core/src/main/java/inetsoft/web/composer/vs/dialog/CalendarPropertyDialogService.java core/src/test/java/inetsoft/web/composer/vs/dialog/CalendarSizeFollowsDensityTest.java core/src/test/java/inetsoft/web/composer/vs/dialog/CalendarPropertyDialogServiceTest.java core/src/test/java/inetsoft/web/composer/vs/objects/controller/ComposerObjectServiceTest.java`

Run:
```bash
git commit -m "Offer follow-the-default-density for a calendar's size

The calendar dialog reads and applies the size checkbox the selection dialogs use, for a single full
calendar only: a dropdown draws no stored height, and the rule's 300 width would squeeze a double
calendar. A drag-resize already flags the size once the calendar takes a density size.

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

### Task 5: Verification and hand-off

**Files:** none changed.

**Interfaces:**
- Consumes: everything above.
- Produces: a green `core` suite and the spec's manual checks handed to the user.

- [ ] **Step 1: Confirm nothing outside community overrides the new hook**

Run: `grep -rn "seedDensitySize" ../enterprise ../server --include=*.java`
Expected: no output.

- [ ] **Step 2: Run every related class together**

Run: `./mvnw -q test -pl core "-Dtest=CalendarDensitySizeTest,CalendarSizeFollowsDensityTest,CalendarPropertyDialogServiceTest,CalendarVSAssemblyInfoFixSizeTest,CalendarVSAssemblyInfoModeDisplayValueTest,ComposerObjectServiceTest,ContainerDensitySizeTest,SelectionDensitySizeTest,AuthorSizeFlagTest,SizeFollowsDensityDialogTest,SizeFollowsDensityReadTest,VSDensityDefaultsTest" -Dsurefire.failIfNoSpecifiedTests=false`
Then: `grep -l 'failures="[1-9]\|errors="[1-9]' core/target/surefire-reports/TEST-*.xml`
Expected: no output. `target/` keeps reports from earlier runs, so a listed file for a class not in this run's list is stale: check its timestamp and ignore it if it predates this run.

- [ ] **Step 3: Run the full core suite**

Run: `./mvnw -q test -pl core` (long; run it in the background and wait for it to finish)
Then: `grep -l 'failures="[1-9]\|errors="[1-9]' core/target/surefire-reports/TEST-*.xml`
Expected: no output. For any file listed, rerun that class alone. If it still fails, check whether it touches calendars, density, sizes or `VizModernizeUtil`. Report it to the user either way rather than changing code outside this plan.

- [ ] **Step 4: Hand the manual checks to the user**

Tell the user the automated work is done. Give them the seven manual checks in the spec's §6.2, to be run in a browser with the dashboard set to each density in turn. Check 7 (bottom tabs after a density change) is expected to show the misalignment §7 describes; the user decides whether that follow-up is filed. Do not describe the slice as finished until the user reports the checks.
