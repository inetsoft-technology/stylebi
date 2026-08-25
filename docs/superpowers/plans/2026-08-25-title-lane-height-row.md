# Title Lane Height Row (L′) — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make a marked assembly's title lane take the density row (20 / 26 / 30) when its author has not set a height, and give the title-height and cell-height steppers a "follow the default density" checkbox so an author can pin the displayed value and opt back in.

**Architecture:** The density substitution goes in each included assembly info's own `getTitleHeight()` override, so all 111 read sites — including the 46 exporter sites — become correct without being touched, and the three excluded types are excluded by simply not being changed. `TitleInfo` is not modified, so XML serialization and `equals()` keep seeing the raw stored height and the asset round-trips unchanged. The dialogs send the effective height and a nullable "follows density" boolean, replacing the value-comparison that currently infers authorship.

**Tech Stack:** Java 21 (core), JUnit 5 + Spring `SpringExtension`, Angular 21 / TypeScript 5.9, Vitest.

**Spec:** `docs/superpowers/specs/2026-08-25-title-lane-height-row-design.md` — read it before starting. Its §2 records three corrections to the decision documents; §3's "Rejected alternatives" explains why the `getEffectiveCellHeight()` pattern is *not* used here.

## Global Constraints

- **Branch:** `viz-updates`.
- **One commit per task.** Task 8 is verification only and commits nothing but documentation.
- **Java conventions:** 3-space indent, `if(cond)` with no space after `if`, brace on the same line, comments kept to a short clause rather than full sentences.
- **No design-doc, decision-record, ticket or plan-phase references in source comments.** State rules directly. Documentation files are exempt.
- **No comments in Angular HTML template (`.html`) files.**
- **Never run the full TL suite** — it exceeds the foreground window and orphans multi-GB workers. Scope any `*.tl.spec.ts` run with `--include`, naming the single spec file. This plan needs no TL tests.
- `./mvnw` is slow (10–40 min). Allow up to 600000 ms per invocation. Use `-Dtest=<class>` while iterating; the full `-pl core` suite runs once, in Task 8.
- **Do not modify `TitleInfo.getTitleHeight()`, `TitleInfo.writeAttributes` or `TitleInfo.parseAttributes`.** The stored height must round-trip unchanged. Task 4 modifies `TitleInfo.equals()` and one javadoc block only.
- **Do not modify `RangeSliderPropertyDialogService`, `CheckBoxVSAssemblyInfo`, `RadioButtonVSAssemblyInfo` or `TimeSliderVSAssemblyInfo`.** These three types are excluded from the row and their exclusion is the absence of a change.
- **Do not touch `userDataRowHeight` or `userHeaderRowHeight`.** Spec §2.1: they are captured by a drag gesture, not inference, and need no affordance.
- Density values are 20 / 26 / 30 for dense / compact / comfortable, where 20 is `AssetUtil.defh`. They already exist in `VSDensityDefaults.titleHeightForMode` (`:139-148`); do not redefine them.

---

## File Structure

| File | Responsibility | Task |
|---|---|---|
| `core/src/main/java/inetsoft/uql/viewsheet/internal/TitledVSAssemblyInfo.java:107` | rename the legacy-default accessor | 1 |
| `core/src/main/java/inetsoft/uql/viewsheet/internal/CalendarVSAssemblyInfo.java:94-96` | its 36px override, renamed | 1 |
| 8 `*VSAssemblyInfo.java` `parseXML` call sites | pass the renamed accessor | 1 |
| `core/src/test/java/inetsoft/uql/viewsheet/internal/TitleInfoTest.java:193,195` | renamed assertions | 1 |
| `core/src/main/java/inetsoft/uql/viewsheet/internal/VSDensityDefaults.java` | the new per-assembly resolver overload | 2 |
| `core/src/test/java/inetsoft/uql/viewsheet/internal/VSDensityDefaultsTest.java` | the resolver's guard matrix | 2 |
| `ChartVSAssemblyInfo`, `TableDataVSAssemblyInfo`, `SelectionBaseVSAssemblyInfo`, `CurrentSelectionVSAssemblyInfo`, `CalendarVSAssemblyInfo` | five `getTitleHeight()` delegations | 3 |
| `core/src/test/java/inetsoft/uql/viewsheet/internal/TitleLaneHeightRowTest.java` | new — the included/excluded regression fence | 3 |
| `core/src/main/java/inetsoft/uql/viewsheet/internal/TitleInfo.java` | `equals()` gains the flag; one javadoc replaced | 4 |
| `core/src/main/java/inetsoft/web/composer/model/vs/SizePositionPaneModel.java` | two nullable Booleans | 5 |
| `web/projects/portal/src/app/vsobjects/model/size-position-pane-model.ts` | the TS mirror | 5 |
| `web/projects/portal/src/app/vsobjects/dialog/size-position-pane.component.ts` | checkbox visibility and form-control enablement | 5 |
| `web/projects/portal/src/app/vsobjects/dialog/size-position-pane.component.html` | the two checkboxes | 5 |
| `core/src/main/resources/inetsoft/util/srinter.properties` | the label string | 5 |
| `web/projects/portal/src/app/vsobjects/dialog/size-position-pane.spec.ts` | pane behaviour | 5 |
| 8 `*PropertyDialogService.java` | title-height read and apply | 6 |
| `SelectionListPropertyDialogService`, `SelectionTreePropertyDialogService` | cell-height read and apply | 7 |

---

## Task 1: Rename `getDefaultTitleHeight()` to `getLegacyTitleHeight()`

Pure rename, no behaviour change. It goes first because Task 2's resolver calls the renamed method, and because with two defaults about to be in play the old name invites a later reader to "correct" the calendar's 36 to match the density row — which would silently re-derive every calendar ever saved.

**Files:**
- Modify: `core/src/main/java/inetsoft/uql/viewsheet/internal/TitledVSAssemblyInfo.java:105-109`
- Modify: `core/src/main/java/inetsoft/uql/viewsheet/internal/CalendarVSAssemblyInfo.java:93-96` and `:933`
- Modify: `ChartVSAssemblyInfo.java:1558`, `CheckBoxVSAssemblyInfo.java:349`, `CurrentSelectionVSAssemblyInfo.java:450`, `RadioButtonVSAssemblyInfo.java:326`, `SelectionBaseVSAssemblyInfo.java:652`, `TableDataVSAssemblyInfo.java:1142`, `TimeSliderVSAssemblyInfo.java:643`
- Test: `core/src/test/java/inetsoft/uql/viewsheet/internal/TitleInfoTest.java:193,195`

**Interfaces:**
- Consumes: nothing.
- Produces: `TitledVSAssemblyInfo.getLegacyTitleHeight()` returning `int` — the assembly type's pre-density default title height. `AssetUtil.defh` (20) for every type except `CalendarVSAssemblyInfo`, which returns 36. Task 2 calls this.

- [ ] **Step 1: Confirm the full call-site list before editing**

Run:
```bash
cd community && grep -rn "getDefaultTitleHeight" --include=*.java core/src enterprise ../enterprise 2>/dev/null | grep -v '\.superpowers'
```

Expected: 12 lines — the interface default, the calendar override, 8 `parseXML` call sites, and 2 assertions in `TitleInfoTest`. If the count differs, reconcile before continuing; a missed site is a compile error, not a silent bug, so this step is a cross-check rather than a safety net.

- [ ] **Step 2: Rename the interface default**

In `TitledVSAssemblyInfo.java`, replace:

```java
   /**
    * Get the default title height for this assembly type.
    */
   default int getDefaultTitleHeight() {
      return AssetUtil.defh;
   }
```

with:

```java
   /**
    * The assembly type's title height before any density row applies. AssetUtil.defh for every
    * type except the calendar, whose lane has always been taller.
    */
   default int getLegacyTitleHeight() {
      return AssetUtil.defh;
   }
```

- [ ] **Step 3: Rename the calendar override**

In `CalendarVSAssemblyInfo.java`, replace:

```java
   @Override
   public int getDefaultTitleHeight() {
      return 36;
   }
```

with:

```java
   @Override
   public int getLegacyTitleHeight() {
      return 36;
   }
```

- [ ] **Step 4: Rename the nine remaining call sites**

Run:
```bash
cd community && grep -rl "getDefaultTitleHeight" --include=*.java core/src | xargs sed -i 's/getDefaultTitleHeight/getLegacyTitleHeight/g'
```

Then re-run the Step 1 grep. Expected: zero hits for `getDefaultTitleHeight`, 12 for `getLegacyTitleHeight`.

- [ ] **Step 5: Compile and run the affected test**

Run:
```bash
cd community && ./mvnw test -pl core -Dtest='TitleInfoTest' -DfailIfNoSpecifiedTests=false
```

Expected: PASS. `TitleInfoTest:193,195` assert 36 for the calendar and `AssetUtil.defh` for the chart; the rename does not change either value.

- [ ] **Step 6: Commit**

```bash
cd community && git add core/src/main/java/inetsoft/uql/viewsheet/internal core/src/test/java/inetsoft/uql/viewsheet/internal/TitleInfoTest.java
git commit -m "refactor(viewsheet): name the pre-density title height for what it is"
```

---

## Task 2: The resolver

Adds `VSDensityDefaults.titleHeight(info, stored)`. Nothing calls it yet, so this task changes no behaviour — Task 3 wires it.

**Files:**
- Modify: `core/src/main/java/inetsoft/uql/viewsheet/internal/VSDensityDefaults.java` — add after the existing `titleHeight(VizContext)` at `:104-106`
- Test: `core/src/test/java/inetsoft/uql/viewsheet/internal/VSDensityDefaultsTest.java`

**Interfaces:**
- Consumes: `TitledVSAssemblyInfo.getLegacyTitleHeight()` from Task 1.
- Produces: `public static <T extends VSAssemblyInfo & TitledVSAssemblyInfo> int VSDensityDefaults.titleHeight(T info, int stored)`. Returns the density-row height when `info` is marked, `info.isUserTitleHeight()` is false, and `stored == info.getLegacyTitleHeight()`; otherwise returns `stored` unchanged. Tasks 3, 6 and 7 all call this.

**Why `stored` is a parameter** rather than read inside: it lets a composer dialog pass its *design-time* value (`getTitleHeightValue()`) and still get the substitution, while render paths pass the runtime value. Task 6 depends on this.

**Why the guard's cheap tests run first:** `VizContext.of(info)` calls `VSDensityDefaults.mode()`, which is a `SreeEnv.getProperty` read. This method lands on 111 call sites, some in render loops, so an unmarked or author-set assembly must not pay for a property lookup. Only a marked, unflagged, at-default assembly builds a context.

- [ ] **Step 1: Write the failing tests**

Add to `VSDensityDefaultsTest.java`. The existing `@AfterEach reset()` already nulls `viewsheet.density`, so no new teardown is needed. Note none of these set `viewsheet.modernVisualization` — `VizContext.of(info)` takes `modern` from the mark, not the gate.

```java
   @Test
   void titleHeightFollowsDensityForAMarkedDefaultAssembly() {
      SreeEnv.setProperty("viewsheet.density", "compact");
      ChartVSAssemblyInfo info = new ChartVSAssemblyInfo();
      info.setVizMark(VizMark.MODERN_LIGHT);
      assertEquals(26, VSDensityDefaults.titleHeight(info, AssetUtil.defh));
   }

   @Test
   void titleHeightResolvesEachDensityTier() {
      ChartVSAssemblyInfo info = new ChartVSAssemblyInfo();
      info.setVizMark(VizMark.MODERN_LIGHT);

      SreeEnv.setProperty("viewsheet.density", "dense");
      assertEquals(AssetUtil.defh, VSDensityDefaults.titleHeight(info, AssetUtil.defh), "dense");
      SreeEnv.setProperty("viewsheet.density", "compact");
      assertEquals(26, VSDensityDefaults.titleHeight(info, AssetUtil.defh), "compact");
      SreeEnv.setProperty("viewsheet.density", "comfortable");
      assertEquals(30, VSDensityDefaults.titleHeight(info, AssetUtil.defh), "comfortable");
   }

   @Test
   void titleHeightKeepsStoredWhenUnmarked() {
      SreeEnv.setProperty("viewsheet.density", "comfortable");
      ChartVSAssemblyInfo info = new ChartVSAssemblyInfo();
      assertEquals(AssetUtil.defh, VSDensityDefaults.titleHeight(info, AssetUtil.defh));
   }

   @Test
   void titleHeightKeepsStoredWhenTheAuthorSetIt() {
      SreeEnv.setProperty("viewsheet.density", "comfortable");
      ChartVSAssemblyInfo info = new ChartVSAssemblyInfo();
      info.setVizMark(VizMark.MODERN_LIGHT);
      info.setUserTitleHeight(true);
      assertEquals(AssetUtil.defh, VSDensityDefaults.titleHeight(info, AssetUtil.defh));
   }

   @Test
   void titleHeightKeepsStoredWhenNotAtTheLegacyDefault() {
      SreeEnv.setProperty("viewsheet.density", "comfortable");
      ChartVSAssemblyInfo info = new ChartVSAssemblyInfo();
      info.setVizMark(VizMark.MODERN_LIGHT);
      assertEquals(25, VSDensityDefaults.titleHeight(info, 25));
   }

   @Test
   void titleHeightAdmitsTheCalendarAtItsOwnLegacyDefault() {
      SreeEnv.setProperty("viewsheet.density", "compact");
      CalendarVSAssemblyInfo info = new CalendarVSAssemblyInfo();
      info.setVizMark(VizMark.MODERN_LIGHT);
      assertEquals(36, info.getLegacyTitleHeight(), "the calendar's legacy lane");
      assertEquals(26, VSDensityDefaults.titleHeight(info, 36));
   }

   @Test
   void titleHeightLeavesAnUnmarkedCalendarAlone() {
      SreeEnv.setProperty("viewsheet.density", "compact");
      CalendarVSAssemblyInfo info = new CalendarVSAssemblyInfo();
      assertEquals(36, VSDensityDefaults.titleHeight(info, 36));
   }

   @Test
   void titleHeightShrinksAMarkedCalendarAtDense() {
      // the one place dense stops equalling legacy: the calendar's legacy lane was never defh
      SreeEnv.setProperty("viewsheet.density", "dense");
      CalendarVSAssemblyInfo info = new CalendarVSAssemblyInfo();
      info.setVizMark(VizMark.MODERN_LIGHT);
      assertEquals(AssetUtil.defh, VSDensityDefaults.titleHeight(info, 36));
   }
```

- [ ] **Step 2: Run the tests to verify they fail**

Run:
```bash
cd community && ./mvnw test -pl core -Dtest='VSDensityDefaultsTest' -DfailIfNoSpecifiedTests=false
```

Expected: compilation failure — no method `titleHeight(ChartVSAssemblyInfo, int)` on `VSDensityDefaults`.

- [ ] **Step 3: Implement the resolver**

In `VSDensityDefaults.java`, add immediately after the existing `titleHeight(VizContext ctx)` method:

```java
   /**
    * Title-lane height for one assembly: the density row when the assembly is marked, its author
    * has not set a height, and the stored height is still the type's pre-density default;
    * otherwise the stored height unchanged. The stored height is a parameter so a composer dialog
    * can pass its design-time value and still get the substitution.
    *
    * The three cheap tests run before the context is built - VizContext reads the density
    * property, and an unmarked or author-set assembly must not pay for that.
    */
   public static <T extends VSAssemblyInfo & TitledVSAssemblyInfo> int titleHeight(T info, int stored) {
      if(info.getVizMark() == null || info.isUserTitleHeight() ||
         stored != info.getLegacyTitleHeight())
      {
         return stored;
      }

      return titleHeight(VizContext.of(info));
   }
```

No new imports: `VSAssemblyInfo`, `TitledVSAssemblyInfo` and `VizContext` are all in `inetsoft.uql.viewsheet.internal`.

- [ ] **Step 4: Run the tests to verify they pass**

Run:
```bash
cd community && ./mvnw test -pl core -Dtest='VSDensityDefaultsTest' -DfailIfNoSpecifiedTests=false
```

Expected: PASS, including the pre-existing `titleHeight(VizContext)` tests at `:116-156`, which are untouched.

- [ ] **Step 5: Commit**

```bash
cd community && git add core/src/main/java/inetsoft/uql/viewsheet/internal/VSDensityDefaults.java core/src/test/java/inetsoft/uql/viewsheet/internal/VSDensityDefaultsTest.java
git commit -m "feat(viewsheet): resolve a title lane height from the assembly's own mark"
```

---

## Task 3: Wire the five included types

This is where behaviour changes. All 111 read sites pick up the substitution, including the 46 exporter sites.

**Files:**
- Modify: `ChartVSAssemblyInfo.java:2716-2718`, `TableDataVSAssemblyInfo.java:241-243`, `SelectionBaseVSAssemblyInfo.java:300-302`, `CurrentSelectionVSAssemblyInfo.java:205-207`, `CalendarVSAssemblyInfo.java:634-636`
- Test: `core/src/test/java/inetsoft/uql/viewsheet/internal/TitleLaneHeightRowTest.java` (new)

**Interfaces:**
- Consumes: `VSDensityDefaults.titleHeight(T info, int stored)` from Task 2.
- Produces: `getTitleHeight()` on the five included infos now returns the effective height. Tasks 6 and 7 rely on this being the *runtime* effective value while `getTitleHeightValue()` stays the raw stored one.

- [ ] **Step 1: Write the failing regression fence**

Create `core/src/test/java/inetsoft/uql/viewsheet/internal/TitleLaneHeightRowTest.java`:

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
class TitleLaneHeightRowTest {
   @AfterEach
   void reset() {
      SreeEnv.setProperty("viewsheet.density", null);
   }

   private <T extends VSAssemblyInfo & TitledVSAssemblyInfo> T marked(T info) {
      info.setVizMark(VizMark.MODERN_LIGHT);
      return info;
   }

   @Test
   void includedTypesTakeTheDensityRow() {
      SreeEnv.setProperty("viewsheet.density", "compact");
      assertEquals(26, marked(new ChartVSAssemblyInfo()).getTitleHeight(), "chart");
      assertEquals(26, marked(new TableVSAssemblyInfo()).getTitleHeight(), "table");
      assertEquals(26, marked(new CrosstabVSAssemblyInfo()).getTitleHeight(), "crosstab");
      assertEquals(26, marked(new CalcTableVSAssemblyInfo()).getTitleHeight(), "calc table");
      assertEquals(26, marked(new EmbeddedTableVSAssemblyInfo()).getTitleHeight(), "embedded table");
      assertEquals(26, marked(new SelectionListVSAssemblyInfo()).getTitleHeight(), "selection list");
      assertEquals(26, marked(new SelectionTreeVSAssemblyInfo()).getTitleHeight(), "selection tree");
      assertEquals(26, marked(new CurrentSelectionVSAssemblyInfo()).getTitleHeight(), "selection container");
      assertEquals(26, marked(new CalendarVSAssemblyInfo()).getTitleHeight(), "calendar");
   }

   @Test
   void excludedTypesNeverTakeTheDensityRow() {
      SreeEnv.setProperty("viewsheet.density", "comfortable");
      assertEquals(AssetUtil.defh, marked(new CheckBoxVSAssemblyInfo()).getTitleHeight(), "check box");
      assertEquals(AssetUtil.defh, marked(new RadioButtonVSAssemblyInfo()).getTitleHeight(), "radio button");
      assertEquals(AssetUtil.defh, marked(new TimeSliderVSAssemblyInfo()).getTitleHeight(), "range slider");
   }

   @Test
   void unmarkedTypesAreUntouchedAtEveryDensity() {
      for(String mode : new String[]{ "dense", "compact", "comfortable" }) {
         SreeEnv.setProperty("viewsheet.density", mode);
         assertEquals(AssetUtil.defh, new ChartVSAssemblyInfo().getTitleHeight(), mode + " chart");
         assertEquals(AssetUtil.defh, new TableVSAssemblyInfo().getTitleHeight(), mode + " table");
         assertEquals(36, new CalendarVSAssemblyInfo().getTitleHeight(), mode + " calendar");
      }
   }

   @Test
   void anAuthorHeightSurvivesOnAMarkedAssembly() {
      SreeEnv.setProperty("viewsheet.density", "comfortable");
      ChartVSAssemblyInfo info = marked(new ChartVSAssemblyInfo());
      info.setTitleHeightValue(25);
      info.setUserTitleHeight(true);
      assertEquals(25, info.getTitleHeight());
   }

   @Test
   void theStoredHeightIsUnchangedWhileTheRowResolvesIt() {
      SreeEnv.setProperty("viewsheet.density", "comfortable");
      ChartVSAssemblyInfo info = marked(new ChartVSAssemblyInfo());
      assertEquals(30, info.getTitleHeight(), "the lane resolves live");
      assertEquals(AssetUtil.defh, info.getTitleHeightValue(), "the stored height does not move");
   }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run:
```bash
cd community && ./mvnw test -pl core -Dtest='TitleLaneHeightRowTest' -DfailIfNoSpecifiedTests=false
```

Expected: `includedTypesTakeTheDensityRow` FAILS with 20 where 26 is expected, for every one of the nine. `excludedTypesNeverTakeTheDensityRow` and `unmarkedTypesAreUntouchedAtEveryDensity` PASS already — they assert today's behaviour, which must not change.

- [ ] **Step 3: Wire the five delegations**

Each of the five is the same edit. In `ChartVSAssemblyInfo.java:2716`, replace:

```java
   public int getTitleHeight() {
      return titleInfo.getTitleHeight();
   }
```

with:

```java
   public int getTitleHeight() {
      return VSDensityDefaults.titleHeight(this, titleInfo.getTitleHeight());
   }
```

Apply the identical change at `TableDataVSAssemblyInfo.java:241`, `SelectionBaseVSAssemblyInfo.java:300`, `CurrentSelectionVSAssemblyInfo.java:205` and `CalendarVSAssemblyInfo.java:634`.

**Do not** change `CheckBoxVSAssemblyInfo.java:134`, `RadioButtonVSAssemblyInfo.java:156` or `TimeSliderVSAssemblyInfo.java:428`.

- [ ] **Step 4: Run the tests to verify they pass**

Run:
```bash
cd community && ./mvnw test -pl core -Dtest='TitleLaneHeightRowTest' -DfailIfNoSpecifiedTests=false
```

Expected: PASS, all five tests.

- [ ] **Step 5: Check the neighbouring suites for fallout**

Run:
```bash
cd community && ./mvnw test -pl core -Dtest='TitleInfoTest,TabVSAssemblyInfoTest,VSDensityDefaultsTest,VSObjectChromeDefaultsTest' -DfailIfNoSpecifiedTests=false
```

Expected: PASS. None of these sets a mark on a titled assembly, so the resolver returns `stored` throughout. If `TabVSAssemblyInfoTest` fails, it means something in the tab-bounds path is marking an assembly — read `TabVSAssemblyInfo:573,578`, which derive tab bounds from a calendar's or selection's title height, before changing anything.

- [ ] **Step 6: Commit**

```bash
cd community && git add core/src/main/java/inetsoft/uql/viewsheet/internal core/src/test/java/inetsoft/uql/viewsheet/internal/TitleLaneHeightRowTest.java
git commit -m "feat(viewsheet): let a marked assembly's title lane follow the org density"
```

---

## Task 4: `userTitleHeight` joins `TitleInfo.equals()`

Now mandatory rather than tidy: Task 5's checkbox lets an author change the flag *without changing the number*, and each info's `copyViewInfo` transfers the whole `TitleInfo` only when it compares unequal — so the change would be silently dropped on apply.

**Files:**
- Modify: `core/src/main/java/inetsoft/uql/viewsheet/internal/TitleInfo.java:412-425` (`equals`) and `:179-220` (one javadoc block)
- Test: `core/src/test/java/inetsoft/uql/viewsheet/internal/TitleInfoTest.java`

**Interfaces:**
- Consumes: nothing from earlier tasks.
- Produces: `TitleInfo.equals()` returns false for two otherwise-identical `TitleInfo`s whose `userTitleHeight` differs. Task 5's checkbox depends on this.

**Cell height needs no equivalent change.** `userCellHeight` lives on `SelectionBaseVSAssemblyInfo`, not on `TitleInfo`, and its `copyViewInfo` already compares it explicitly at `:799-801`. Do not add it to `equals()`.

- [ ] **Step 1: Write the failing test**

Add to `TitleInfoTest.java`. `copyViewInfo` is `protected` on most infos and `public` on `TableDataVSAssemblyInfo`; this test is in the same package, so both are reachable.

```java
   @Test
   void titleHeightFlagMakesTwoTitleInfosUnequal() {
      TitleInfo a = new TitleInfo();
      TitleInfo b = new TitleInfo();
      assertEquals(a, b, "identical to start with");

      b.setUserTitleHeight(true);
      assertNotEquals(a, b, "provenance is part of identity");
   }

   @Test
   void titleHeightFlagPropagatesThroughCopyViewInfo() {
      ChartVSAssemblyInfo target = new ChartVSAssemblyInfo();
      ChartVSAssemblyInfo source = new ChartVSAssemblyInfo();
      source.setUserTitleHeight(true);

      assertFalse(target.isUserTitleHeight(), "clean before the copy");
      target.copyViewInfo(source, false);
      assertTrue(target.isUserTitleHeight(), "the flag alone must transfer");
   }
```

- [ ] **Step 2: Run the tests to verify they fail**

Run:
```bash
cd community && ./mvnw test -pl core -Dtest='TitleInfoTest' -DfailIfNoSpecifiedTests=false
```

Expected: both new tests FAIL. `equals()` currently ignores the flag, so the two infos compare equal and `copyViewInfo`'s guard never fires.

- [ ] **Step 3: Add the flag to `equals()`**

In `TitleInfo.java`, in the `equals` return expression at `:418-424`, add one term. Replace:

```java
      return Tool.equals(title, info.title) &&
         Tool.equals(getTitle(null, null, null), info.getTitle(null, null, null)) &&
         Tool.equals(titleVisible, info.titleVisible) &&
         isTitleVisible() == info.isTitleVisible() &&
         Tool.equals(titleHeight, info.titleHeight) &&
         Tool.equals(getTitleHeight(), info.getTitleHeight()) &&
         Tool.equals(padding, info.padding);
```

with:

```java
      return Tool.equals(title, info.title) &&
         Tool.equals(getTitle(null, null, null), info.getTitle(null, null, null)) &&
         Tool.equals(titleVisible, info.titleVisible) &&
         isTitleVisible() == info.isTitleVisible() &&
         Tool.equals(titleHeight, info.titleHeight) &&
         Tool.equals(getTitleHeight(), info.getTitleHeight()) &&
         userTitleHeight == info.userTitleHeight &&
         Tool.equals(padding, info.padding);
```

- [ ] **Step 4: Replace the obsolete javadoc**

The 40-line javadoc on `isUserTitleHeight()` documents the inference guard, why the displayed value cannot be pinned, why the flag must join `equals()`, and why the flag is one-way for five of eight types. Tasks 4 to 7 resolve all four. Replace the whole block from `/**` above `public boolean isUserTitleHeight()` down to its closing `*/` with:

```java
   /**
    * Whether the author set the title height rather than leaving it to follow the default
    * density. Read from the property dialog's follow-the-default-density control, not inferred.
    * Part of equals(), so a change to it alone still transfers through copyViewInfo.
    */
```

- [ ] **Step 5: Run the tests to verify they pass**

Run:
```bash
cd community && ./mvnw test -pl core -Dtest='TitleInfoTest,TitleLaneHeightRowTest' -DfailIfNoSpecifiedTests=false
```

Expected: PASS.

- [ ] **Step 6: Check every `equals()` consumer still behaves**

`equals()` has exactly eight consumers, all `copyViewInfo` guards. Seven call `Tool.equals(titleInfo, x.titleInfo)`; the eighth, `SelectionBaseVSAssemblyInfo:819`, calls `titleInfo.equals(...)` directly — a grep for the first form finds seven and looks like a contradiction. Confirm the set is unchanged:

```bash
cd community && grep -rn "Tool.equals(titleInfo\|titleInfo.equals(" --include=*.java core/src/main | grep -v '\.superpowers'
```

Expected: 8 guards across `CalendarVSAssemblyInfo`, `ChartVSAssemblyInfo`, `CheckBoxVSAssemblyInfo`, `CurrentSelectionVSAssemblyInfo`, `RadioButtonVSAssemblyInfo`, `SelectionBaseVSAssemblyInfo`, `TableDataVSAssemblyInfo`, `TimeSliderVSAssemblyInfo`, plus one javadoc mention in `TitleInfo.java`. Nothing outside `uql/viewsheet/internal` compares a `TitleInfo`.

- [ ] **Step 7: Commit**

```bash
cd community && git add core/src/main/java/inetsoft/uql/viewsheet/internal/TitleInfo.java core/src/test/java/inetsoft/uql/viewsheet/internal/TitleInfoTest.java
git commit -m "fix(viewsheet): let a title height's provenance travel with its TitleInfo"
```

---

## Task 5: The checkbox — model, template, component

**Files:**
- Modify: `core/src/main/java/inetsoft/web/composer/model/vs/SizePositionPaneModel.java`
- Modify: `web/projects/portal/src/app/vsobjects/model/size-position-pane-model.ts`
- Modify: `web/projects/portal/src/app/vsobjects/dialog/size-position-pane.component.ts`
- Modify: `web/projects/portal/src/app/vsobjects/dialog/size-position-pane.component.html:69-88`
- Modify: `core/src/main/resources/inetsoft/util/srinter.properties`
- Test: `web/projects/portal/src/app/vsobjects/dialog/size-position-pane.spec.ts`

**Interfaces:**
- Consumes: nothing from earlier tasks.
- Produces: `SizePositionPaneModel.getTitleHeightFollowsDensity()` / `setTitleHeightFollowsDensity(Boolean)` and `getCellHeightFollowsDensity()` / `setCellHeightFollowsDensity(Boolean)`, both `Boolean` (nullable). **Null means this assembly does not follow the density row, so no checkbox is rendered — a per-assembly condition, not a per-type one.** Two populations send null: an excluded type (the range slider), and *any unmarked assembly of any included type*, since the resolver returns `stored` untouched when `getVizMark() == null`. An earlier draft of this line said "type", and that error shipped a regression: it sent `true` for every classic assembly, which rendered the checkbox checked and disabled the Title Height stepper on every existing dashboard. Tasks 6 and 7 populate them. The TS model mirrors them as `titleHeightFollowsDensity?: boolean` and `cellHeightFollowsDensity?: boolean`.

**Do not make the height fields nullable.** `size-position-pane.component.ts:111-112` derives control visibility from truthiness (`showTitleHeight = !!this.model.titleHeight`), so a null height would make the whole control vanish.

- [ ] **Step 1: Add the Java model fields**

In `SizePositionPaneModel.java`, add after `setCellHeight` (`:72-74`):

```java
   public Boolean getTitleHeightFollowsDensity() {
      return titleHeightFollowsDensity;
   }

   public void setTitleHeightFollowsDensity(Boolean titleHeightFollowsDensity) {
      this.titleHeightFollowsDensity = titleHeightFollowsDensity;
   }

   public Boolean getCellHeightFollowsDensity() {
      return cellHeightFollowsDensity;
   }

   public void setCellHeightFollowsDensity(Boolean cellHeightFollowsDensity) {
      this.cellHeightFollowsDensity = cellHeightFollowsDensity;
   }
```

and add to the field list after `private int cellHeight;` (`:115`):

```java
   // null: the assembly type does not follow the density row, so no control is offered
   private Boolean titleHeightFollowsDensity;
   private Boolean cellHeightFollowsDensity;
```

- [ ] **Step 2: Add the TS model fields**

In `size-position-pane-model.ts`, replace the interface body's height lines so it reads:

```typescript
export interface SizePositionPaneModel {
   top: number;
   left: number;
   width: number;
   height: number;
   container: boolean;
   locked: boolean;
   titleHeight: number;
   cellHeight: number;
   titleHeightFollowsDensity?: boolean;
   cellHeightFollowsDensity?: boolean;
   scaleVertical?: boolean;
}
```

- [ ] **Step 3: Add the label string**

In `core/src/main/resources/inetsoft/util/srinter.properties`, add in alphabetical position among the `composer.vs.*` keys:

```properties
composer.vs.followDefaultDensity=Follow the default density
```

- [ ] **Step 4: Write the failing pane tests**

Add to `size-position-pane.spec.ts`. Mirror the file's existing `TestBed` setup and its `By` import.

```typescript
   it("should not render a follow-density checkbox when the model does not offer one", () => {
      fixture.componentInstance.model = createModel({titleHeight: 20});
      fixture.detectChanges();
      expect(fixture.debugElement.query(By.css("#titleHeightFollowsDensity"))).toBeNull();
   });

   it("should render the checkbox and disable the stepper while following", () => {
      fixture.componentInstance.model = createModel({titleHeight: 26, titleHeightFollowsDensity: true});
      fixture.detectChanges();
      expect(fixture.debugElement.query(By.css("#titleHeightFollowsDensity"))).not.toBeNull();
      expect(fixture.componentInstance.form.controls["titleHeight"].disabled).toBeTruthy();
   });

   it("should keep the displayed height when the checkbox is cleared", () => {
      fixture.componentInstance.model = createModel({titleHeight: 26, titleHeightFollowsDensity: true});
      fixture.detectChanges();

      fixture.componentInstance.titleHeightFollowChanged(false);
      fixture.detectChanges();

      expect(fixture.componentInstance.model.titleHeightFollowsDensity).toBeFalsy();
      expect(fixture.componentInstance.model.titleHeight).toBe(26);
      expect(fixture.componentInstance.form.controls["titleHeight"].disabled).toBeFalsy();
   });
```

Add a `createModel` helper to the spec if one is not already present, building on whatever object literal the existing tests use:

```typescript
   function createModel(overrides: Partial<SizePositionPaneModel>): SizePositionPaneModel {
      return Object.assign({
         top: 0, left: 0, width: 100, height: 100,
         container: false, locked: false, titleHeight: 0, cellHeight: 0
      }, overrides) as SizePositionPaneModel;
   }
```

- [ ] **Step 5: Run the tests to verify they fail**

Run:
```bash
cd community/web && npx ng test portal --include='**/size-position-pane.spec.ts'
```

Expected: FAIL — `titleHeightFollowChanged` is not a function, and no `#titleHeightFollowsDensity` element exists.

- [ ] **Step 6: Implement the component logic**

In `size-position-pane.component.ts`, add two visibility fields beside the existing ones at `:38-39`:

```typescript
   showTitleHeightFollow: boolean;
   showCellHeightFollow: boolean;
```

Set them in `ngOnInit` (`:110-114`), which becomes:

```typescript
   ngOnInit(): void {
      this.showCellHeight = !!this.model.cellHeight;
      this.showTitleHeight = !!this.model.titleHeight;
      this.showTitleHeightFollow = this.model.titleHeightFollowsDensity != null;
      this.showCellHeightFollow = this.model.cellHeightFollowsDensity != null;
      this.initForm();
   }
```

In `initForm`, the two height controls start disabled while following. Replace the `showTitleHeight` block (`:87-94`) and the `showCellHeight` block (`:96-102`) with:

```typescript
      if(this.showTitleHeight) {
         this.form.addControl("titleHeight", new UntypedFormControl(
            {value: this.model.titleHeight,
             disabled: !this.titleHeightEnable || this.model.titleHeightFollowsDensity === true}, [
            Validators.required,
            FormValidators.isInteger(),
            FormValidators.positiveNonZeroIntegerInRange
         ]));
      }

      if(this.showCellHeight) {
         this.form.addControl("cellHeight", new UntypedFormControl(
            {value: this.model.cellHeight,
             disabled: this.model.cellHeightFollowsDensity === true}, [
            Validators.required,
            FormValidators.isInteger(),
            FormValidators.positiveNonZeroIntegerInRange
         ]));
      }
```

Add the two change handlers after `initForm`:

```typescript
   titleHeightFollowChanged(follows: boolean): void {
      this.model.titleHeightFollowsDensity = follows;
      this.setEnabled("titleHeight", !follows && this.titleHeightEnable);
   }

   cellHeightFollowChanged(follows: boolean): void {
      this.model.cellHeightFollowsDensity = follows;
      this.setEnabled("cellHeight", !follows);
   }

   private setEnabled(name: string, enabled: boolean): void {
      const control = this.form.controls[name];

      if(!control) {
         return;
      }

      if(enabled) {
         control.enable();
      }
      else {
         control.disable();
      }
   }
```

The height value is deliberately not reset on either transition: the stepper already displays the effective height, so unticking pins exactly what the author was looking at.

- [ ] **Step 7: Implement the template**

In `size-position-pane.component.html`, replace the two blocks at `:69-89` with:

```html
        @if (showTitleHeight) {
          <div class="col">
            <div class="d-flex flex-column">
              <label class="form-label fw-bold mt-2">_#(Title Height)</label>
              <number-stepper formControlName="titleHeight"
                              (valueChange)="model.titleHeight = $event"
                              [invalid]="!!form.controls['titleHeight'].errors">
              </number-stepper>
              @if (showTitleHeightFollow) {
                <div class="form-check mt-1">
                  <input type="checkbox" class="form-check-input" id="titleHeightFollowsDensity"
                         [ngModel]="model.titleHeightFollowsDensity"
                         (ngModelChange)="titleHeightFollowChanged($event)"
                         [ngModelOptions]="{standalone: true}">
                  <label class="form-check-label" for="titleHeightFollowsDensity">
                    _#(composer.vs.followDefaultDensity)
                  </label>
                </div>
              }
            </div>
          </div>
        }
        @if (showCellHeight) {
          <div class="col">
            <div class="d-flex flex-column">
              <label class="form-label fw-bold mt-2">_#(Cell Height)</label>
              <number-stepper formControlName="cellHeight"
                              (valueChange)="model.cellHeight = $event"
                              [invalid]="!!form.controls['cellHeight'].errors">
              </number-stepper>
              @if (showCellHeightFollow) {
                <div class="form-check mt-1">
                  <input type="checkbox" class="form-check-input" id="cellHeightFollowsDensity"
                         [ngModel]="model.cellHeightFollowsDensity"
                         (ngModelChange)="cellHeightFollowChanged($event)"
                         [ngModelOptions]="{standalone: true}">
                  <label class="form-check-label" for="cellHeightFollowsDensity">
                    _#(composer.vs.followDefaultDensity)
                  </label>
                </div>
              }
            </div>
          </div>
        }
```

Read `:80-88` first and preserve the existing cell-height stepper's exact attribute set if it differs from what is written above.

- [ ] **Step 8: Run the tests to verify they pass**

Run:
```bash
cd community/web && npx ng test portal --include='**/size-position-pane.spec.ts'
```

Expected: PASS, including the file's pre-existing tests.

- [ ] **Step 9: Commit**

```bash
cd community && git add core/src/main/java/inetsoft/web/composer/model/vs/SizePositionPaneModel.java core/src/main/resources/inetsoft/util/srinter.properties web/projects/portal/src/app/vsobjects/model/size-position-pane-model.ts web/projects/portal/src/app/vsobjects/dialog/size-position-pane.component.ts web/projects/portal/src/app/vsobjects/dialog/size-position-pane.component.html web/projects/portal/src/app/vsobjects/dialog/size-position-pane.spec.ts
git commit -m "feat(vsobjects): offer a follow-the-default-density control beside the height steppers"
```

---

## Task 6: Title height in the eight participating services

**Files:**
- Modify: `core/src/main/java/inetsoft/web/composer/vs/dialog/ChartPropertyDialogService.java:198` and `:412-415`
- Modify: `TableViewPropertyDialogService.java:115` and `:207-210`
- Modify: `CrosstabPropertyDialogService.java:124` and `:298-301`
- Modify: `CalcTablePropertyDialogService.java:140` and `:375-380`
- Modify: `SelectionContainerPropertyDialogService.java:88` and `:149-152`
- Modify: `CalendarPropertyDialogService.java:102` and `:236-239`
- Modify: `SelectionListPropertyDialogService.java:117` and `:217-220`
- Modify: `SelectionTreePropertyDialogService.java:118` and `:243-246`
- Test: `core/src/test/java/inetsoft/web/composer/vs/dialog/TitleHeightFollowDensityTest.java` (new)

**Interfaces:**
- Consumes: `VSDensityDefaults.titleHeight(T info, int stored)` (Task 2); `SizePositionPaneModel.get/setTitleHeightFollowsDensity` (Task 5).
- Produces: nothing later tasks depend on.

**`RangeSliderPropertyDialogService` is not touched.** Its read at `:120` and its apply at `:260-264` stay exactly as they are.

- [ ] **Step 1: Write the failing test**

Create `core/src/test/java/inetsoft/web/composer/vs/dialog/TitleHeightFollowDensityTest.java`. This tests the read and apply *rules* against a model and an info directly, rather than standing up eight services, because the eight edits are identical and the rule is what can regress.

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
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.web.composer.model.vs.SizePositionPaneModel;
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
class TitleHeightFollowDensityTest {
   @AfterEach
   void reset() {
      SreeEnv.setProperty("viewsheet.density", null);
   }

   @Test
   void readSendsTheEffectiveHeightAndTheFollowFlag() {
      SreeEnv.setProperty("viewsheet.density", "compact");
      ChartVSAssemblyInfo info = new ChartVSAssemblyInfo();
      info.setVizMark(VizMark.MODERN_LIGHT);

      SizePositionPaneModel model = new SizePositionPaneModel();
      model.setTitleHeight(VSDensityDefaults.titleHeight(info, info.getTitleHeightValue()));
      model.setTitleHeightFollowsDensity(!info.isUserTitleHeight());

      assertEquals(26, model.getTitleHeight(), "the dialog shows the derived lane");
      assertTrue(model.getTitleHeightFollowsDensity());
   }

   @Test
   void readSendsTheStoredHeightWhenTheAuthorSetIt() {
      SreeEnv.setProperty("viewsheet.density", "compact");
      ChartVSAssemblyInfo info = new ChartVSAssemblyInfo();
      info.setVizMark(VizMark.MODERN_LIGHT);
      info.setTitleHeightValue(25);
      info.setUserTitleHeight(true);

      SizePositionPaneModel model = new SizePositionPaneModel();
      model.setTitleHeight(VSDensityDefaults.titleHeight(info, info.getTitleHeightValue()));
      model.setTitleHeightFollowsDensity(!info.isUserTitleHeight());

      assertEquals(25, model.getTitleHeight());
      assertFalse(model.getTitleHeightFollowsDensity());
   }

   @Test
   void applyFollowingWritesNothingAndClearsTheFlag() {
      SreeEnv.setProperty("viewsheet.density", "compact");
      ChartVSAssemblyInfo info = new ChartVSAssemblyInfo();
      info.setVizMark(VizMark.MODERN_LIGHT);
      info.setUserTitleHeight(true);
      info.setTitleHeightValue(25);

      SizePositionPaneModel model = new SizePositionPaneModel();
      model.setTitleHeightFollowsDensity(true);
      model.setTitleHeight(26);

      applyTitleHeight(model, info);

      assertFalse(info.isUserTitleHeight(), "the flag is cleared");
      assertEquals(25, info.getTitleHeightValue(), "no height is written");
      assertEquals(26, info.getTitleHeight(), "the row now resolves the lane");
   }

   @Test
   void applyNotFollowingPinsTheSubmittedHeight() {
      SreeEnv.setProperty("viewsheet.density", "compact");
      ChartVSAssemblyInfo info = new ChartVSAssemblyInfo();
      info.setVizMark(VizMark.MODERN_LIGHT);

      SizePositionPaneModel model = new SizePositionPaneModel();
      model.setTitleHeightFollowsDensity(false);
      model.setTitleHeight(26);

      applyTitleHeight(model, info);

      assertTrue(info.isUserTitleHeight(), "the flag is set");
      assertEquals(26, info.getTitleHeightValue(), "the displayed value is pinned");
      assertEquals(26, info.getTitleHeight(), "and stays 26 at any density");

      SreeEnv.setProperty("viewsheet.density", "comfortable");
      assertEquals(26, info.getTitleHeight(), "pinned against a density change");
   }

   @Test
   void applyFollowingOnAnUnmarkedAssemblyLeavesTheLegacyLane() {
      SreeEnv.setProperty("viewsheet.density", "comfortable");
      ChartVSAssemblyInfo info = new ChartVSAssemblyInfo();

      SizePositionPaneModel model = new SizePositionPaneModel();
      model.setTitleHeightFollowsDensity(true);
      model.setTitleHeight(AssetUtil.defh);

      applyTitleHeight(model, info);

      assertEquals(AssetUtil.defh, info.getTitleHeight(), "unmarked content never moves");
   }

   // the rule the eight services share, in one place
   private void applyTitleHeight(SizePositionPaneModel model, ChartVSAssemblyInfo info) {
      boolean follows = model.getTitleHeightFollowsDensity();
      info.setUserTitleHeight(!follows);

      if(!follows) {
         info.setTitleHeightValue(model.getTitleHeight());
      }
   }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run:
```bash
cd community && ./mvnw test -pl core -Dtest='TitleHeightFollowDensityTest' -DfailIfNoSpecifiedTests=false
```

Expected: compilation failure — `setTitleHeightFollowsDensity` does not exist if Task 5 was skipped. If Task 5 is done, expect PASS, because the test's private helper already encodes the rule. **That is intentional**: this test pins the contract the eight services must implement, and Step 3 makes the services match it. Treat a passing run here as the contract being agreed, not the services being done — Step 4 is what verifies them.

- [ ] **Step 3: Rewrite the read and apply in all eight services**

For each service, the read line becomes a two-line pair. Using `ChartPropertyDialogService:198` as the worked example, replace:

```java
      sizePositionPaneModel.setTitleHeight(chartAssemblyInfo.getTitleHeightValue());
```

with:

```java
      sizePositionPaneModel.setTitleHeight(
         VSDensityDefaults.titleHeight(chartAssemblyInfo, chartAssemblyInfo.getTitleHeightValue()));
      sizePositionPaneModel.setTitleHeightFollowsDensity(!chartAssemblyInfo.isUserTitleHeight());
```

and replace the apply guard at `:412-415`:

```java
         if(sizePositionPaneModel.getTitleHeight() != assemblyInfo.getTitleHeightValue()) {
            assemblyInfo.setUserTitleHeight(true);
            assemblyInfo.setTitleHeightValue(sizePositionPaneModel.getTitleHeight());
         }
```

with:

**Corrected 2026-08-25 after implementation and the final review — the snippet below is what shipped, and it is not what this plan originally specified.** Two defects were found: clearing the flag without resetting the stored height left a pinned assembly unable to rejoin the row (§3's guard also requires `stored == getLegacyTitleHeight()`), and treating a null flag as not-following stamped provenance on every unmarked apply. Null now means no opinion and keeps the pre-existing comparison guard. See the design doc's §4.1 and §4.3.

```java
         Boolean followsDensity = sizePositionPaneModel.getTitleHeightFollowsDensity();

         if(followsDensity == null) {
            if(sizePositionPaneModel.getTitleHeight() != assemblyInfo.getTitleHeightValue()) {
               assemblyInfo.setUserTitleHeight(true);
               assemblyInfo.setTitleHeightValue(sizePositionPaneModel.getTitleHeight());
            }
         }
         else if(followsDensity) {
            assemblyInfo.setUserTitleHeight(false);
            assemblyInfo.setTitleHeightValue(assemblyInfo.getLegacyTitleHeight());
         }
         else {
            assemblyInfo.setUserTitleHeight(true);
            assemblyInfo.setTitleHeightValue(sizePositionPaneModel.getTitleHeight());
         }
```

Apply the same pair of edits, adapting only the local variable names, at:

| Service | Read | Apply |
|---|---|---|
| `TableViewPropertyDialogService` | `:115` (`tableAssemblyInfo`) | `:207-210` |
| `CrosstabPropertyDialogService` | `:124` (`crosstabAssemblyInfo`) | `:298-301` (`assemblyInfo`) |
| `CalcTablePropertyDialogService` | `:140` (`calcTableAssemblyInfo`) | `:375-380` |
| `SelectionContainerPropertyDialogService` | `:88` (`selectionContainerAssemblyInfo`) | `:149-152` |
| `CalendarPropertyDialogService` | `:102` (`calendarAssemblyInfo`) | `:236-239` (`info`) |
| `SelectionListPropertyDialogService` | `:117` (`selectionListAssemblyInfo`) | `:217-220` |
| `SelectionTreePropertyDialogService` | `:118` (`selectionTreeAssemblyInfo`) | `:243-246` (`streeInfo`) |

Two extra notes:

- `CalcTablePropertyDialogService:375-376` carries a two-line comment above its guard pointing at `TitleInfo.isUserTitleHeight()`. **Delete the comment with the guard** — it describes an inference that no longer exists.
- Add `import inetsoft.uql.viewsheet.internal.VSDensityDefaults;` to any service that does not already import it. Check each with `grep -n "VSDensityDefaults" <file>` before adding.

- [ ] **Step 4: Verify every service was converted and none was missed**

Run:
```bash
cd community && grep -rn "setUserTitleHeight(true)" --include=*.java core/src/main/java/inetsoft/web/composer/vs/dialog
```

Expected: exactly one hit, in `RangeSliderPropertyDialogService.java`. Any other hit is a service that still infers.

Run:
```bash
cd community && grep -rn "setTitleHeightFollowsDensity" --include=*.java core/src/main/java/inetsoft/web/composer/vs/dialog | wc -l
```

Expected: 8.

- [ ] **Step 5: Run the tests**

Run:
```bash
cd community && ./mvnw test -pl core -Dtest='TitleHeightFollowDensityTest,CalendarPropertyDialogServiceTest,SelectionListPropertyDialogServiceTest,SelectionTreePropertyDialogServiceTest' -DfailIfNoSpecifiedTests=false
```

Expected: PASS. The three existing service tests do not set a mark, so their assemblies read legacy and their assertions hold. If one fails on a min-size assertion, read `SelectionListPropertyDialogService:241-246` and `SelectionTreePropertyDialogService:270-276` — both compute a minimum height from `getTitleHeight()`, which is now the effective value. That change is intended; update the assertion to match the effective lane rather than reverting the service.

- [ ] **Step 6: Commit**

```bash
cd community && git add core/src/main/java/inetsoft/web/composer/vs/dialog core/src/test/java/inetsoft/web/composer/vs/dialog/TitleHeightFollowDensityTest.java
git commit -m "feat(composer): read the title height affordance instead of guessing from the value"
```

---

## Task 7: Cell height in the two selection services

Same rule, different flag. Cell height's density row already ships, so this closes a live defect rather than enabling a new one.

**Files:**
- Modify: `core/src/main/java/inetsoft/web/composer/vs/dialog/SelectionListPropertyDialogService.java:120-121` and `:224-227`
- Modify: `core/src/main/java/inetsoft/web/composer/vs/dialog/SelectionTreePropertyDialogService.java:121-122` and `:250-253`
- Test: `core/src/test/java/inetsoft/web/composer/vs/dialog/CellHeightFollowDensityTest.java` (new)

**Interfaces:**
- Consumes: `SizePositionPaneModel.get/setCellHeightFollowsDensity` (Task 5); `SelectionBaseVSAssemblyInfo.getEffectiveCellHeight()` and `isUserCellHeight()`, both already present (`:138`, `:145`).
- Produces: nothing later tasks depend on.

- [ ] **Step 1: Write the failing test**

Create `core/src/test/java/inetsoft/web/composer/vs/dialog/CellHeightFollowDensityTest.java` with the same header, annotations and imports as `TitleHeightFollowDensityTest` from Task 6, then:

```java
class CellHeightFollowDensityTest {
   @AfterEach
   void reset() {
      SreeEnv.setProperty("viewsheet.density", null);
   }

   @Test
   void readSendsTheEffectiveCellHeightAndTheFollowFlag() {
      SreeEnv.setProperty("viewsheet.density", "compact");
      SelectionListVSAssemblyInfo info = new SelectionListVSAssemblyInfo();
      info.setVizMark(VizMark.MODERN_LIGHT);

      SizePositionPaneModel model = new SizePositionPaneModel();
      int cellHeight = info.getEffectiveCellHeight();
      model.setCellHeight(cellHeight <= 0 ? AssetUtil.defh : cellHeight);
      model.setCellHeightFollowsDensity(!info.isUserCellHeight());

      assertEquals(24, model.getCellHeight(), "the compact cell row");
      assertTrue(model.getCellHeightFollowsDensity());
   }

   @Test
   void applyFollowingWritesNothingAndClearsTheFlag() {
      SreeEnv.setProperty("viewsheet.density", "compact");
      SelectionListVSAssemblyInfo info = new SelectionListVSAssemblyInfo();
      info.setVizMark(VizMark.MODERN_LIGHT);
      info.setUserCellHeight(true);
      info.setCellHeight(18);

      SizePositionPaneModel model = new SizePositionPaneModel();
      model.setCellHeightFollowsDensity(true);
      model.setCellHeight(24);

      applyCellHeight(model, info);

      assertFalse(info.isUserCellHeight(), "the flag is cleared");
      assertEquals(24, info.getEffectiveCellHeight(), "the row resolves the cell");
   }

   @Test
   void applyNotFollowingPinsTheSubmittedCellHeight() {
      SreeEnv.setProperty("viewsheet.density", "compact");
      SelectionListVSAssemblyInfo info = new SelectionListVSAssemblyInfo();
      info.setVizMark(VizMark.MODERN_LIGHT);

      SizePositionPaneModel model = new SizePositionPaneModel();
      model.setCellHeightFollowsDensity(false);
      model.setCellHeight(24);

      applyCellHeight(model, info);

      assertTrue(info.isUserCellHeight(), "the flag is set");
      assertEquals(24, info.getEffectiveCellHeight(), "pinned at the value shown");

      SreeEnv.setProperty("viewsheet.density", "comfortable");
      assertEquals(24, info.getEffectiveCellHeight(), "pinned against a density change");
   }

   private void applyCellHeight(SizePositionPaneModel model, SelectionListVSAssemblyInfo info) {
      boolean follows = model.getCellHeightFollowsDensity();
      info.setUserCellHeight(!follows);

      if(!follows) {
         info.setCellHeight(model.getCellHeight());
      }
   }
}
```

If `assertEquals(24, ...)` fails on the compact cell row, read `VSDensityDefaults.rowHeightForMode` (`:111-120`) and use the value it actually returns for `"compact"` — the cell row borrows the data-row steps, and the plan must match the code rather than the reverse.

- [ ] **Step 2: Run the test to verify it fails**

Run:
```bash
cd community && ./mvnw test -pl core -Dtest='CellHeightFollowDensityTest' -DfailIfNoSpecifiedTests=false
```

Expected: compilation failure if Task 5 is not done; otherwise PASS on the encoded contract, as in Task 6 Step 2. Step 4 verifies the services.

- [ ] **Step 3: Rewrite the read and apply in both services**

In `SelectionListPropertyDialogService.java`, replace `:120-121`:

```java
      int cellHeight = selectionListAssemblyInfo.getEffectiveCellHeight();
      sizePositionPaneModel.setCellHeight(cellHeight <= 0 ? AssetUtil.defh : cellHeight);
```

with:

```java
      int cellHeight = selectionListAssemblyInfo.getEffectiveCellHeight();
      sizePositionPaneModel.setCellHeight(cellHeight <= 0 ? AssetUtil.defh : cellHeight);
      sizePositionPaneModel.setCellHeightFollowsDensity(
         !selectionListAssemblyInfo.isUserCellHeight());
```

and replace the apply guard at `:224-227`:

```java
      if(sizePositionPaneModel.getCellHeight() != selectionListAssemblyInfo.getEffectiveCellHeight()) {
         selectionListAssemblyInfo.setUserCellHeight(true);
         selectionListAssemblyInfo.setCellHeight(sizePositionPaneModel.getCellHeight());
      }
```

with:

**Corrected 2026-08-25 after implementation and the final review — the snippet below is what shipped, and it is not what this plan originally specified.** Two defects were found: clearing the flag without resetting the stored height left a pinned assembly unable to rejoin the row (§3's guard also requires `stored == getLegacyTitleHeight()`), and treating a null flag as not-following stamped provenance on every unmarked apply. Null now means no opinion and keeps the pre-existing comparison guard. See the design doc's §4.1 and §4.3.

```java
      Boolean cellFollowsDensity = sizePositionPaneModel.getCellHeightFollowsDensity();

      if(cellFollowsDensity == null) {
         if(sizePositionPaneModel.getCellHeight() != selectionListAssemblyInfo.getEffectiveCellHeight()) {
            selectionListAssemblyInfo.setUserCellHeight(true);
            selectionListAssemblyInfo.setCellHeight(sizePositionPaneModel.getCellHeight());
         }
      }
      else if(cellFollowsDensity) {
         selectionListAssemblyInfo.setUserCellHeight(false);
         selectionListAssemblyInfo.setCellHeight(AssetUtil.defh);
      }
      else {
         selectionListAssemblyInfo.setUserCellHeight(true);
         selectionListAssemblyInfo.setCellHeight(sizePositionPaneModel.getCellHeight());
      }
```

Apply the identical pair in `SelectionTreePropertyDialogService.java` at `:121-122` and `:250-253`, using its local names (`selectionTreeAssemblyInfo` for the read, `streeInfo` for the apply).

- [ ] **Step 4: Verify both services were converted**

Run:
```bash
cd community && grep -rn "setUserCellHeight(true)" --include=*.java core/src/main/java/inetsoft/web/composer/vs/dialog
```

Expected: zero hits.

Run:
```bash
cd community && grep -rn "setCellHeightFollowsDensity" --include=*.java core/src/main/java/inetsoft/web/composer/vs/dialog | wc -l
```

Expected: 2.

- [ ] **Step 5: Run the tests**

Run:
```bash
cd community && ./mvnw test -pl core -Dtest='CellHeightFollowDensityTest,SelectionListPropertyDialogServiceTest,SelectionTreePropertyDialogServiceTest' -DfailIfNoSpecifiedTests=false
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
cd community && git add core/src/main/java/inetsoft/web/composer/vs/dialog core/src/test/java/inetsoft/web/composer/vs/dialog/CellHeightFollowDensityTest.java
git commit -m "feat(composer): read the cell height affordance instead of guessing from the value"
```

---

## Task 8: Verification pass

No code. This task is the phase's evidence, and most of it cannot come from a test run.

**Files:** `docs/superpowers/specs/lookfeel/chart-card-roadmap.md`

- [ ] **Step 1: Full automated gates**

Run each and record the numbers:

```bash
cd community && ./mvnw test -pl core
cd community/web && npx ng test portal
cd community/web && npx ng test em
cd /e/StyleBI/stylebi-enterprise && ./mvnw.cmd install -DskipTests "-Pcommunity,enterprise"
```

Expected: `core` green at or above 5076 tests plus this plan's additions; `portal` and `em` green; the cross-module build clean. **Do not** run the TL suites unfiltered.

- [ ] **Step 2: Confirm the pre-mark cohort before believing any manual result**

Every check below is meaningless against an asset created on `viz-updates` with the gate on before the mark existed — it carries seeded modern values and no mark. Either confirm those dev dashboards are gone, or create the dashboards for Step 3 fresh in this session. Note which you did.

- [ ] **Step 3: The manual checks, in a built server with a browser**

Reference values: lane heights **20 / 26 / 30** for dense / compact / comfortable. Calendar legacy lane **36**.

1. **The row.** Each of the eight included assemblies — chart, table, crosstab, calc table, embedded table, selection list, selection tree, selection container — plus a calendar, marked, at all three densities. Expect 20 / 26 / 30. Unmarked equivalents unchanged, and an unmarked calendar still 36.
2. **The exclusions.** Check box, radio button, range slider: title lane unchanged at every density, marked or not. **Any movement here means the per-info substitution has leaked** and the approach is broken, not merely miswired.
3. **The marked calendar at dense** is 20, not 36, and its title text is not clipped. This is the one place dense stops equalling legacy, and it is deliberate.
4. **The checkbox.** Ticked, the stepper shows the derived value and is disabled. Untick: the number does not move, the stepper enables. Apply, save, close, reopen — the value is pinned and the box is unticked.
5. **The opt-back-in.** On an assembly carrying a hand-set height, tick the box. Apply, save, reopen. Expect it to follow density again. This path exists nowhere today outside the table reset-layout action.
6. **Cell height, same two checks** on a selection list and a selection tree.
7. **Export agreement.** PDF, PNG, Excel and HTML at each density, for a marked and an unmarked dashboard. Content *below* the title must shift consistently — not just the lane.
8. **The two interactions no decision document names.** An annotated chart: annotations stay anchored to the plot as the lane changes (`AnnotationVSUtil:1194` positions them off the title height). A tab holding a calendar and a selection list: the tab sizes correctly (`TabVSAssemblyInfo:573,578` derive tab bounds from a child's title height).
9. **Scheduled export.** Run a scheduled task against a marked dashboard. Different thread, different entry point.
10. **Revert.** Revert a modernized dashboard. Every lane returns to its legacy height — 20 for the seven, 36 for the calendar. This is the reversibility property, and it works only because `TitleInfo` was left raw.

- [ ] **Step 4: Record the results in the roadmap**

Add L′ to the roadmap's Done table with its commit hashes, and re-derive "What to pick up next" from the dependency picture rather than editing it in place, per that file's own instruction. L′ landing unblocks **L″, geometric suppression** — which must not have shipped alongside it. Note also that L′ makes the interim density-keyed suppression accurate rather than approximate: the lane really is 26 at compact now.

- [ ] **Step 5: Commit the documentation update**

```bash
cd community && git add docs/superpowers/specs/lookfeel/chart-card-roadmap.md
git commit -m "docs(chart-card): record the title lane row shipped and re-derive what comes next"
```

---

## Self-Review

**Spec coverage.** §1's two halves → Tasks 2–3 (resolver) and 5–7 (affordance). §2.1's narrowing → Task 5's two independent fields and the Global Constraint forbidding the row-height flags. §2.2's guard → Task 2 Step 3's `getLegacyTitleHeight()` comparison, its calendar tests, and Task 1's rename. §2.2's accepted dense consequence → Task 2's `titleHeightShrinksAMarkedCalendarAtDense` and Task 8 Step 3 item 3. §2.3's 111 sites → Task 3, which touches five delegations and no read sites. §3's resolver → Task 2. §3's "why `TitleInfo` is not touched" → the Global Constraint plus Task 3's `theStoredHeightIsUnchangedWhileTheRowResolvesIt`. §4.1 model → Task 5 Steps 1–2, including the non-nullable-height warning. §4.2 read → Tasks 6 and 7 Step 3. §4.3 apply and guard deletion → Tasks 6 and 7 Step 3, verified by their Step 4 greps. §4.4 untick-pins → Task 5 Step 6's deliberate non-reset and Task 6's `applyNotFollowingPinsTheSubmittedHeight`. §4.5 template → Task 5 Step 7. §4.6 services → Tasks 6 and 7. §5.1 equals → Task 4. §5.2 javadoc → Task 4 Step 4. §6.1's two-line blast radius → Task 1 Step 5. §6.2's test table → Tasks 2, 3, 4, 5, 6, 7. §6.3's manual pass → Task 8 Step 3. §7's out-of-scope → Global Constraints and Task 8 Step 4's L″ note.

**Spec §8's two open questions are both closed by this plan.** The `showTitleHeight` enumeration is resolved: the eight participating services are named in Task 6's file list with exact line numbers, and `RangeSliderPropertyDialogService` is the one exclusion. The `VizContext.of` cost is resolved by Task 2 Step 3's guard ordering — the three cheap tests run before any context is built, so only a marked, unflagged, at-default assembly reads the density property. The spec's own code sketch did not have this ordering; the plan's version supersedes it.

**Placeholder scan.** No TBD, TODO, "similar to Task N", or "add appropriate handling". Every code step carries the actual code. Two steps deliberately instruct the implementer to read the code and match it rather than trusting the plan — Task 5 Step 7 (preserve the cell-height stepper's real attribute set) and Task 7 Step 1 (use the compact cell-row value the code returns). Both are cross-checks against a specific line, not open questions.

**Type consistency.** `getLegacyTitleHeight()` is introduced in Task 1 and consumed in Task 2 Step 3 and Task 2's calendar test under that exact name. `VSDensityDefaults.titleHeight(T info, int stored)` is defined in Task 2 and called in Task 3 Step 3, Task 6 Step 3 and Task 6's test under that signature. `titleHeightFollowsDensity` / `cellHeightFollowsDensity` are defined in Task 5 Steps 1–2 as `Boolean` (Java) and `boolean` optional (TS), and used in Tasks 5–7 under those names; the accessors are `getTitleHeightFollowsDensity` / `setTitleHeightFollowsDensity` throughout. `titleHeightFollowChanged` / `cellHeightFollowChanged` / `setEnabled` are defined in Task 5 Step 6 and referenced in Task 5 Steps 4 and 7 under those names. `showTitleHeightFollow` / `showCellHeightFollow` are defined in Task 5 Step 6 and used in Step 7's template. `getEffectiveCellHeight()` and `isUserCellHeight()` are pre-existing and cited with their current lines.

**One ordering constraint that matters.** Task 4 must land before Task 5 ships to a user, because the checkbox can change the flag without changing the number and `copyViewInfo` would drop it. The plan's order satisfies this. Tasks 6 and 7 both depend on Task 5's model fields and will not compile without them.
