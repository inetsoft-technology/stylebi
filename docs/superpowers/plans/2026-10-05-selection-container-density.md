# Selection Container Density Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A list inside a modern selection container shows all its rows at every density tier, a contained range slider's title lane matches its siblings', a modern container stores the child positions it draws, and a new container's default size keeps twelve lanes.

**Architecture:** The height formulas live on the assembly infos — `SelectionBaseVSAssemblyInfo.getContainedListHeight()` and `TimeSliderVSAssemblyInfo.isCollapsedInContainer(int)` — so the services that write a child's height (`VSSelectionContainerService`, `GroupingService`, `AbstractLayout`) become one-line callers and the logic is unit-testable without Spring services. The container's drawn-stack rule moves, unchanged, from `CoordinateHelper` onto `CurrentSelectionVSAssembly`, where a marked container's `layout()` uses it. The default size follows the existing `selectionSize` / `isSeededSelectionSize` pattern in `VSDensityDefaults`.

**Tech Stack:** Java 21, JUnit 5, Mockito, Maven. No frontend change: the range slider dialog already renders `<size-position-pane>`, which shows the follow-the-density checkbox whenever the flag is non-null.

**Spec:** `docs/superpowers/specs/lookfeel/2026-10-05-selection-container-density-design.md`

## Global Constraints

- **Title lane: 30 / 26 / 20** (comfortable / compact / dense). **Selection cell height: 28 / 24 / 20.** **Card inset (list and tree only): 16 / 12 / 8.** No new numbers.
- **Unmarked is byte-identical at every site.** Each site keeps its unmarked branch word for word.
- **Whose mark decides:** a list's own mark for its height (D2); a slider's own mark for its lane and collapse test (D4); the **container's** mark for `layout()` (D5) and the default size (D6).
- **The container takes no card inset** (D1). Its property dialog is not touched.
- **Container default size: 300 × 12·lane** — 300×360 / 300×312 / 300×240; legacy 300×240.
- Comments are *why*, not *what*, kept to a short clause; no comments in `.html`; no ticket, PR or design-doc references in source comments.
- **Work only in the worktree** `C:/Users/Franky/AppData/Local/Temp/claude/E--StyleBI-stylebi-enterprise/20be51c2-05de-41cd-883b-988de3648ca4/scratchpad/selection-container-density` (branch `feature-selection-container-density`). The shared checkout `E:/StyleBI/stylebi-enterprise/community` belongs to another session.
- **Commits:** run `git branch --show-current` first; run `git add` and `git commit` as separate commands, never chained. In this environment a hook requires the user's approval for each.

## How to run tests

```bash
# from the worktree root
./mvnw test -pl core -Dtest=ContainedListHeightTest
./mvnw test -pl core -Dtest=ContainedListHeightTest#aMarkedListInAContainerFitsItsRows
```

core's surefire runs only `@Tag("core")` tests; every new test class carries it. The first build in a fresh worktree compiles all of core and takes several minutes.

## Review Focus

Five inputs the spec implies that no happy path exercises, most likely first. Each has its test in the task that owns the code.

1. **A device layout gives the list a layout size.** `VSObjectService.getSize` returns the layout size when one is set, so expand must write the layout size and leave the pixel size alone. **Task 2**, `expandingInADeviceLayoutWritesTheLayoutSize`.
2. **Mixed marks.** A marked list in an unmarked container still fits its rows, and an unmarked list in a marked container keeps the legacy height — the list's own mark decides. **Task 2**, `aMarkedListInAnUnmarkedContainerFitsItsRows` and `anUnmarkedListInAMarkedContainerKeepsTheLegacyHeight`.
3. **A collapsed marked slider stored at the old 20px lane is not mistaken for an expanded one** by the overflow estimate, which would collapse it a second time and rewrite its stored height. **Task 3**, `aCollapsedMarkedSliderIsNotCollapsedAgainForRoom`.
4. **An author-pinned slider lane** (marked, `userTitleHeight`) keeps its height and still expands and collapses by its hidden flag. **Task 3**, `anAuthorLaneStillExpands`.
5. **A container whose child list names an assembly no longer in the viewsheet.** `layout()` and `getChildTop` must skip it, as the legacy loop does. **Task 5**, `aMissingChildIsSkipped`.

---

### Task 0: Before-exports and the checks doc

No production code may land before this task is done: the before-exports are the only proof that legacy output did not move.

**Files:**
- Create (untracked, do not commit unless the user asks): `docs/superpowers/plans/2026-10-05-selection-container-density-checks.md`
- Local state (git-excluded), in the **shared checkout** `E:/StyleBI/stylebi-enterprise/community`:
  - `.superpowers/baselines/selection-padding/` — `sel-fixture.zip`, `export_all.py` (HTML, PDF and XLSX for the four `SEL` viewsheets; usage `python export_all.py <outdir> [name-filter]`), `compare.py` (usage `python compare.py <dir-a> <dir-b> [name-filter]`), `measure_outrows.py`, `measure_png_pptx.py`
  - `.superpowers/baselines/density-padding-export/` — `sree.py` (REST client; its export endpoint is `/export/viewsheet/global/<name>?format=<n>&match=true`, format codes PPTX 1, PNG 4, CSV 6), `html_measure.js`, and the STOMP composer driver `vsclient.js` / `flows.js`

- [ ] **Step 1: Confirm the running server is on the base commit**

The local server must be running code from `origin/epic-74519` @ `d082410751` or earlier on the same line — not from this branch. Ask the user which build is running; record its commit in the checks doc.

- [ ] **Step 2: Extend the fixture**

In each of `SEL Modern Comfortable`, `SEL Modern Compact`, `SEL Modern Dense` and `SEL Legacy`, add, in the composer:

| Role | How |
|---|---|
| `ConMix` | a new selection container; drop two selection lists and one range slider into it; Show Current Selections on, with two selection lists left outside the container so it shows two collapsed rows |
| `ConNew` | a new selection container, left at its default size |
| `ConOld` | a new selection container, saved at 300×240 — its purpose is to be a marked container from **before** the fix |

Save each viewsheet. Then expand `ConMix`'s first list by clicking its title in the composer, which sends `/events/selectionContainer/update/<list name>` with `{"hide": false}`, and save again so the expanded height is stored. The STOMP driver (`vsclient.js`) can send the same event.

Record in the checks doc: each container's stored size and each child's stored offset and size, read from the asset XML.

- [ ] **Step 3: Capture the before-exports**

```bash
cd E:/StyleBI/stylebi-enterprise/community/.superpowers/baselines/selection-padding
python export_all.py before-container
```

Expected: HTML, PDF and XLSX for each of the four viewsheets in `before-container/`.

Then capture PNG, PPTX and CSV the way #6157's `prefix-png-pptx/` was captured: with `sree.py` against `/export/viewsheet/global/<name>?format=<n>&match=true` for formats 4, 1 and 6, into `before-container-png-pptx/`. Check every file is non-empty and the PDF page counts match the viewsheets.

- [ ] **Step 4: Write the checks doc**

```markdown
# Selection container density — export checks

**Base server commit:** <from Step 1>
**Fixture:** SEL Modern Comfortable / Compact / Dense and SEL Legacy, each with ConMix, ConNew, ConOld.
The baselines folder is git-excluded local state; numbers go in the PR description.

| Check | What | Where |
|---|---|---|
| MC-1 | SEL Legacy identical before and after — HTML byte for byte, PNG pixel for pixel, PDF geometry, PPTX and XLSX zip entries, CSV text | all formats |
| MC-2 | an expanded contained list shows all listHeight rows at every tier | viewer and composer; PDF, HTML, PNG at match layout |
| MC-3 | the range slider's lane equals its siblings'; a marked slider collapsed at 20px still expands and collapses | live, then export |
| MC-4 | for a modern container, saved child positions (asset XML) equal drawn positions (html_measure.js, viewer DOM) | after a fresh open, or a change that lays the viewsheet out |
| MC-5 | a range slider at the container's bottom edge is clipped and included in PDF and PNG as the viewer shows it | export |
| MC-6 | ConNew is 300×360 / 312 / 240; ConOld grows on open; Revert restores 300×240; dense unchanged | live, asset XML |
| MC-7 | expanding a list in a nearly full modern container collapses a sibling | live |

## Before (Task 0)

<stored sizes and offsets from Step 2>

## Results (Task 7)
```

No commit for this task.

---

### Task 1: The contained-list height, on the info

**Files:**
- Modify: `core/src/main/java/inetsoft/uql/viewsheet/internal/SelectionBaseVSAssemblyInfo.java` (after `getEffectiveCellHeight()`, `:140-144`)
- Modify: `core/src/main/java/inetsoft/web/composer/vs/dialog/SelectionListPropertyDialogService.java:296-303`
- Modify: `core/src/main/java/inetsoft/web/composer/vs/dialog/SelectionTreePropertyDialogService.java:328-333`
- Test: `core/src/test/java/inetsoft/uql/viewsheet/internal/ContainedListHeightTest.java` (create)

**Interfaces:**
- Produces: `public int SelectionBaseVSAssemblyInfo.getListBodyHeight()` — `listHeight × getEffectiveCellHeight() + inset.top + inset.bottom`.
- Produces: `public int SelectionBaseVSAssemblyInfo.getContainedListHeight()` — marked: `getTitleHeight() + getListBodyHeight()`; unmarked: `getListHeight() × AssetUtil.defh + getTitleHeight()`.

- [ ] **Step 1: Write the failing test**

Create `core/src/test/java/inetsoft/uql/viewsheet/internal/ContainedListHeightTest.java`:

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
class ContainedListHeightTest {
   @AfterEach
   void reset() {
      SreeEnv.setProperty("viewsheet.density", null);
   }

   // seeded through the public creation path, so a marked list carries its tier inset
   private SelectionListVSAssemblyInfo list(String density, VizMark mark) {
      SreeEnv.setProperty("viewsheet.density", density);
      SelectionListVSAssemblyInfo info = new SelectionListVSAssemblyInfo();
      info.setVizMark(mark);
      info.initDefaultFormat();
      info.setListHeight(6);
      return info;
   }

   @Test
   void aMarkedListsBodyIsItsRowsInsideItsInset() {
      assertEquals(6 * 28 + 32, list("comfortable", VizMark.MODERN_LIGHT).getListBodyHeight());
      assertEquals(6 * 24 + 24, list("compact", VizMark.MODERN_LIGHT).getListBodyHeight());
      assertEquals(6 * 20 + 16, list("dense", VizMark.MODERN_LIGHT).getListBodyHeight());
   }

   @Test
   void aMarkedListInAContainerFitsItsRows() {
      assertEquals(230, list("comfortable", VizMark.MODERN_LIGHT).getContainedListHeight());
      assertEquals(194, list("compact", VizMark.MODERN_LIGHT).getContainedListHeight());
      assertEquals(156, list("dense", VizMark.MODERN_LIGHT).getContainedListHeight());
   }

   @Test
   void anUnmarkedListKeepsTheLegacyContainedHeight() {
      assertEquals(6 * AssetUtil.defh + AssetUtil.defh,
                   list("comfortable", null).getContainedListHeight());
   }

   // the legacy formula reads defh, not the stored cell height
   @Test
   void anUnmarkedListWithAnAuthorCellHeightStillUsesDefhRows() {
      SelectionListVSAssemblyInfo info = list("comfortable", null);
      info.setCellHeight(25);
      info.setUserCellHeight(true);

      assertEquals(6 * AssetUtil.defh + AssetUtil.defh, info.getContainedListHeight());
   }

   @Test
   void aMarkedListHonoursAnAuthorCellHeight() {
      SelectionListVSAssemblyInfo info = list("comfortable", VizMark.MODERN_LIGHT);
      info.setCellHeight(25);
      info.setUserCellHeight(true);

      assertEquals(30 + 6 * 25 + 32, info.getContainedListHeight());
   }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw test -pl core -Dtest=ContainedListHeightTest`
Expected: compilation error, `cannot find symbol: method getListBodyHeight()`.

- [ ] **Step 3: Add the two methods**

In `SelectionBaseVSAssemblyInfo.java`, immediately after `getEffectiveCellHeight()`:

```java
   /**
    * The height of this list's rows inside its card inset. The caller adds the title, which a
    * dropdown or a hidden title changes.
    */
   public int getListBodyHeight() {
      Insets inset = getPadding();
      return getListHeight() * getEffectiveCellHeight() +
         (inset == null ? 0 : inset.top + inset.bottom);
   }

   /**
    * The height a selection container gives this list when it is open. Marked, it fits its rows
    * at its own density; unmarked keeps the legacy defh rows exactly.
    */
   public int getContainedListHeight() {
      return getVizMark() != null ? getTitleHeight() + getListBodyHeight() :
         getListHeight() * AssetUtil.defh + getTitleHeight();
   }
```

`Insets` and `AssetUtil` are already imported (`java.awt.*`, `inetsoft.uql.asset.internal.AssetUtil`).

- [ ] **Step 4: Run test to verify it passes**

Run: `./mvnw test -pl core -Dtest=ContainedListHeightTest`
Expected: 5 tests, PASS.

- [ ] **Step 5: Move the two dialogs onto the helper**

In `SelectionListPropertyDialogService.java`, replace:

```java
         // the rows a marked list draws follow the density and sit inside its card inset; both
         // reduce to the stored cell height and zero for an unmarked list
         Insets inset = selectionListAssemblyInfo.getPadding();
         int insetY = inset == null ? 0 : inset.top + inset.bottom;
         size.height = selectionListAssemblyInfo.getTitleHeight() +
            selectionListAssemblyInfo.getListHeight() *
               selectionListAssemblyInfo.getEffectiveCellHeight() + insetY;
```

with:

```java
         // the rows a marked list draws follow the density and sit inside its card inset; both
         // reduce to the stored cell height and zero for an unmarked list
         size.height = selectionListAssemblyInfo.getTitleHeight() +
            selectionListAssemblyInfo.getListBodyHeight();
```

In `SelectionTreePropertyDialogService.java`, replace:

```java
            // a marked tree's rows and title lane follow the density, and its rows sit inside the
            // card inset
            Insets inset = streeInfo.getPadding();
            minListHeight = streeInfo.getListHeight() * streeInfo.getEffectiveCellHeight() +
               (streeInfo.isTitleVisible() ? streeInfo.getTitleHeight() : 0) +
               (inset == null ? 0 : inset.top + inset.bottom);
```

with:

```java
            // a marked tree's rows and title lane follow the density, and its rows sit inside the
            // card inset
            minListHeight = streeInfo.getListBodyHeight() +
               (streeInfo.isTitleVisible() ? streeInfo.getTitleHeight() : 0);
```

Both are pure refactors: the arithmetic is identical, including the null-inset case.

- [ ] **Step 6: Prove the refactor changed nothing**

Run: `./mvnw test -pl core -Dtest='SelectionTreeShowTypeSizeTest,SelectionPaddingDialogTest,ContainedListHeightTest'`
Expected: PASS, with no edits to the two existing test classes.

- [ ] **Step 7: Commit**

```bash
git branch --show-current
git add core/src/main/java/inetsoft/uql/viewsheet/internal/SelectionBaseVSAssemblyInfo.java core/src/main/java/inetsoft/web/composer/vs/dialog/SelectionListPropertyDialogService.java core/src/main/java/inetsoft/web/composer/vs/dialog/SelectionTreePropertyDialogService.java core/src/test/java/inetsoft/uql/viewsheet/internal/ContainedListHeightTest.java
git commit -m "Give a selection list the height a container opens it to

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

(`git branch --show-current` must print `feature-selection-container-density`; run the three commands separately.)

---

### Task 2: Size a contained list for its rows where its height is written

**Files:**
- Modify: `core/src/main/java/inetsoft/web/viewsheet/service/VSSelectionContainerService.java:152-154, 182-183, 214-215`
- Modify: `core/src/main/java/inetsoft/web/composer/vs/objects/controller/GroupingService.java:144`
- Modify: `core/src/main/java/inetsoft/uql/viewsheet/vslayout/AbstractLayout.java:476-478`
- Test: `core/src/test/java/inetsoft/web/viewsheet/service/VSSelectionContainerServiceTest.java` (create)
- Test: `core/src/test/java/inetsoft/web/composer/vs/objects/controller/GroupingServiceTest.java` (extend)
- Test: `core/src/test/java/inetsoft/uql/viewsheet/vslayout/AbstractLayoutTest.java` (extend)

**Interfaces:**
- Consumes: `SelectionBaseVSAssemblyInfo.getContainedListHeight()` (Task 1); `CurrentSelectionVSAssemblyInfo.getOutSelectionRowHeight(int legacy)` (existing, `:262`).
- Produces: `VSSelectionContainerServiceTest` with helpers `CurrentSelectionVSAssembly container(VizMark mark, int height, String... children)` and `SelectionListVSAssembly list(String name, VizMark mark, boolean expanded, int height)`, and `AbstractLayoutTest` helpers `containerLayout()` and `layoutHeight(Viewsheet, String)`, all extended by Task 3.

- [ ] **Step 1: Write the failing service test**

Create `core/src/test/java/inetsoft/web/viewsheet/service/VSSelectionContainerServiceTest.java`:

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
package inetsoft.web.viewsheet.service;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.web.binding.handler.VSAssemblyInfoHandler;
import inetsoft.web.viewsheet.model.VSObjectModelFactoryService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@Tag("core")
class VSSelectionContainerServiceTest {
   @Mock VSObjectService objectService;
   @Mock CoreLifecycleService coreLifecycleService;
   @Mock VSObjectModelFactoryService objectModelService;
   @Mock ViewsheetService viewsheetService;
   @Mock VSOutputService vsOutputService;
   @Mock VSAssemblyInfoHandler assemblyInfoHandler;
   @Mock RuntimeViewsheet rvs;
   @Mock CommandDispatcher dispatcher;

   private VSSelectionContainerService service;
   private Viewsheet vs;

   @BeforeEach
   void setUp() {
      service = new VSSelectionContainerService(objectService, coreLifecycleService,
                                                objectModelService, viewsheetService,
                                                vsOutputService, assemblyInfoHandler);
      vs = new Viewsheet();
      vs.getViewsheetInfo().setVizDensity("comfortable");
      when(rvs.getViewsheet()).thenReturn(vs);

      // VSObjectService.getSize: the layout size when a device layout set one, else the live
      // pixel size, which the service then writes in place
      when(objectService.getSize(any())).thenAnswer(inv -> {
         VSAssemblyInfo info = inv.getArgument(0);
         Dimension layout = info.getLayoutSize(false);
         return layout != null ? layout : vs.getPixelSize(info);
      });
   }

   @Test
   void expandingAMarkedListFitsItsRowsAtTheTier() throws Exception {
      container(VizMark.MODERN_LIGHT, 800, "SelectionList1");
      SelectionListVSAssembly list = list("SelectionList1", VizMark.MODERN_LIGHT, false, 30);

      service.applySelection(rvs, "SelectionList1", false, dispatcher, "");

      assertEquals(230, list.getPixelSize().height, "lane 30 + 6 rows of 28 + inset 16 + 16");
      assertEquals(SelectionVSAssemblyInfo.LIST_SHOW_TYPE,
                   list.getSelectionListInfo().getShowTypeValue());
   }

   @Test
   void expandingAnUnmarkedListKeepsTheLegacyHeight() throws Exception {
      container(null, 800, "SelectionList1");
      SelectionListVSAssembly list = list("SelectionList1", null, false, 20);

      service.applySelection(rvs, "SelectionList1", false, dispatcher, "");

      assertEquals(140, list.getPixelSize().height, "6 rows of 20 + lane 20, as before");
   }

   // the list's own mark decides, not the container's
   @Test
   void aMarkedListInAnUnmarkedContainerFitsItsRows() throws Exception {
      container(null, 800, "SelectionList1");
      SelectionListVSAssembly list = list("SelectionList1", VizMark.MODERN_LIGHT, false, 30);

      service.applySelection(rvs, "SelectionList1", false, dispatcher, "");

      assertEquals(230, list.getPixelSize().height);
   }

   @Test
   void anUnmarkedListInAMarkedContainerKeepsTheLegacyHeight() throws Exception {
      container(VizMark.MODERN_LIGHT, 800, "SelectionList1");
      SelectionListVSAssembly list = list("SelectionList1", null, false, 20);

      service.applySelection(rvs, "SelectionList1", false, dispatcher, "");

      assertEquals(140, list.getPixelSize().height);
   }

   @Test
   void expandingInADeviceLayoutWritesTheLayoutSize() throws Exception {
      container(VizMark.MODERN_LIGHT, 800, "SelectionList1");
      SelectionListVSAssembly list = list("SelectionList1", VizMark.MODERN_LIGHT, false, 30);
      list.getSelectionListInfo().setLayoutSize(new Dimension(300, 30));

      service.applySelection(rvs, "SelectionList1", false, dispatcher, "");

      assertEquals(230, list.getSelectionListInfo().getLayoutSize().height);
      assertEquals(30, list.getPixelSize().height, "the pixel size is left to the design layout");
   }

   // title 30 + two open lists of 230 is 490, past the 470 box. The old estimate counted the
   // opening list's body as 6 rows of 20, so it collapsed nothing and the container overflowed
   @Test
   void expandingCollapsesASiblingTheTierRowsWouldOverflow() throws Exception {
      container(VizMark.MODERN_LIGHT, 470, "SelectionList1", "SelectionList2");
      SelectionListVSAssembly open = list("SelectionList1", VizMark.MODERN_LIGHT, true, 230);
      SelectionListVSAssembly closed = list("SelectionList2", VizMark.MODERN_LIGHT, false, 30);

      service.applySelection(rvs, "SelectionList2", false, dispatcher, "");

      assertEquals(230, closed.getPixelSize().height);
      assertEquals(30, open.getPixelSize().height, "the open sibling collapses to its lane");
      assertEquals(SelectionVSAssemblyInfo.DROPDOWN_SHOW_TYPE,
                   open.getSelectionListInfo().getShowTypeValue());
   }

   // two collapsed rows are 60 at comfortable; the old estimate counted them as 40
   @Test
   void theOverflowEstimateCountsCollapsedRowsAtTheTierHeight() throws Exception {
      CurrentSelectionVSAssembly container =
         container(VizMark.MODERN_LIGHT, 580, "SelectionList1", "SelectionList2");
      list("Outside1", null, false, 20);
      list("Outside2", null, false, 20);
      SelectionListVSAssembly open = list("SelectionList1", VizMark.MODERN_LIGHT, true, 230);
      list("SelectionList2", VizMark.MODERN_LIGHT, false, 30);
      container.setShowCurrentSelectionValue(true);
      container.updateOutSelection();
      assertEquals(2, container.getOutSelectionTitles().length, "the two lists outside");

      service.applySelection(rvs, "SelectionList2", false, dispatcher, "");

      assertEquals(30, open.getPixelSize().height, "the open sibling collapses to make room");
   }

   CurrentSelectionVSAssembly container(VizMark mark, int height, String... children) {
      CurrentSelectionVSAssembly container = new CurrentSelectionVSAssembly(vs, "CurrentSelection1");
      vs.addAssembly(container);
      container.getVSAssemblyInfo().setVizMark(mark);
      container.getVSAssemblyInfo().setPixelOffset(new Point(0, 0));
      container.getVSAssemblyInfo().setPixelSize(new Dimension(300, height));
      container.setAssemblies(children);
      return container;
   }

   // the mark is pinned, because construction takes the org gate's
   SelectionListVSAssembly list(String name, VizMark mark, boolean expanded, int height) {
      SelectionListVSAssembly list = new SelectionListVSAssembly(vs, name);
      vs.addAssembly(list);
      SelectionListVSAssemblyInfo info = list.getSelectionListInfo();
      info.setVizMark(mark);
      info.initDefaultFormat();
      info.setListHeight(6);
      info.setShowTypeValue(expanded ? SelectionVSAssemblyInfo.LIST_SHOW_TYPE :
                               SelectionVSAssemblyInfo.DROPDOWN_SHOW_TYPE);
      info.setPixelSize(new Dimension(300, height));
      return list;
   }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./mvnw test -pl core -Dtest=VSSelectionContainerServiceTest`
Expected: FAIL — `expandingAMarkedListFitsItsRowsAtTheTier`, `aMarkedListInAnUnmarkedContainerFitsItsRows` and `expandingInADeviceLayoutWritesTheLayoutSize` get 150 (6 × 20 + 30); `expandingCollapsesASiblingTheTierRowsWouldOverflow` and `theOverflowEstimateCountsCollapsedRowsAtTheTierHeight` leave `open` at 230. The two unmarked tests pass.

The overflow numbers, for a reader checking them: content = height − 40 − the container's lane (30; its title is visible by default, `CurrentSelectionVSAssemblyInfo:805`) − collapsed rows; the estimate sums the children's stored heights plus the opening list's body. At 470 the old estimate is 230 + 30 + 120 = 380 against 400, so nothing collapses, and the fixed one is 230 + 30 + 200 = 460. At 580 with two collapsed rows, content is 450 at 30px rows but 470 at 20px, against 460.

- [ ] **Step 3: Fix the expand path and the overflow estimate**

In `VSSelectionContainerService.java`, replace:

```java
         if(containerInfo.isShowCurrentSelection()) {
            contentHeight -= AssetUtil.defh * containerInfo.getOutSelectionTitles().length;
         }
```

with:

```java
         // the collapsed rows' drawn height, which follows the density in a marked container
         if(containerInfo.isShowCurrentSelection()) {
            contentHeight -= containerInfo.getOutSelectionRowHeight(AssetUtil.defh) *
               containerInfo.getOutSelectionTitles().length;
         }
```

Replace:

```java
            totalHeight += ((SelectionListVSAssemblyInfo) targetAssemblyInfo)
               .getListHeight() * AssetUtil.defh;
```

with:

```java
            // the body it opens to; a marked list's rows follow its density
            SelectionListVSAssemblyInfo targetListInfo =
               (SelectionListVSAssemblyInfo) targetAssemblyInfo;
            totalHeight += targetListInfo.getContainedListHeight() - targetListInfo.getTitleHeight();
```

Replace:

```java
               size.height = selectionListInfo.getListHeight() * AssetUtil.defh +
                  selectionListInfo.getTitleHeight();
```

with:

```java
               size.height = selectionListInfo.getContainedListHeight();
```

`AssetUtil` stays imported: the slider branches still use it.

- [ ] **Step 4: Run it to verify it passes**

Run: `./mvnw test -pl core -Dtest=VSSelectionContainerServiceTest`
Expected: 7 tests, PASS.

- [ ] **Step 5: Write the failing drop and device-layout tests**

Add to `GroupingServiceTest.java` (add `import inetsoft.uql.viewsheet.internal.VizMark;`):

```java
   @Test
   void aMarkedListDroppedIntoAContainerFitsItsRows() throws Exception {
      assertEquals(230, droppedHeight(VizMark.MODERN_LIGHT));
   }

   @Test
   void anUnmarkedListDroppedIntoAContainerKeepsTheLegacyHeight() throws Exception {
      assertEquals(140, droppedHeight(null));
   }

   private int droppedHeight(VizMark mark) throws Exception {
      when(rvs.getViewsheet()).thenReturn(parentVS);
      parentVS.getViewsheetInfo().setVizDensity("comfortable");

      CurrentSelectionVSAssembly container = new CurrentSelectionVSAssembly(parentVS, "CurrentSelection1");
      container.getVSAssemblyInfo().setVizMark(mark);
      container.getVSAssemblyInfo().setPixelOffset(new Point(0, 0));
      container.getVSAssemblyInfo().setPixelSize(new Dimension(300, 360));
      container.setAssemblies(new String[0]);
      parentVS.addAssembly(container);

      SelectionListVSAssembly list = new SelectionListVSAssembly(parentVS, "SelectionList1");
      parentVS.addAssembly(list);
      list.getSelectionListInfo().setVizMark(mark);
      list.getSelectionListInfo().initDefaultFormat();
      list.getSelectionListInfo().setListHeight(6);

      service.groupComponents(rvs, container, list, true, "", dispatcher);

      return list.getPixelSize().height;
   }
```

Add to `AbstractLayoutTest.java` (add `import inetsoft.uql.viewsheet.internal.*;` and `import inetsoft.uql.asset.internal.AssetUtil;`):

```java
   @Test
   void aMarkedContainedListTakesItsTierHeightInADeviceLayout() {
      Viewsheet applied = containerLayout().apply(containerViewsheet(VizMark.MODERN_LIGHT));
      assertEquals(230, layoutHeight(applied, "SelectionList1"));
   }

   // 6 rows of defh with no title, as before
   @Test
   void anUnmarkedContainedListKeepsTheLegacyDeviceLayoutHeight() {
      Viewsheet applied = containerLayout().apply(containerViewsheet(null));
      assertEquals(6 * AssetUtil.defh, layoutHeight(applied, "SelectionList1"));
   }

   private ViewsheetLayout containerLayout() {
      ViewsheetLayout layout = new ViewsheetLayout();
      layout.setVSAssemblyLayouts(List.of(
         new VSAssemblyLayout("CurrentSelection1", new Point(LAYOUT_X, LAYOUT_Y),
                              new Dimension(300, 360))));
      return layout;
   }

   private Viewsheet containerViewsheet(VizMark mark) {
      Viewsheet vs = new Viewsheet();
      vs.getViewsheetInfo().setVizDensity("comfortable");

      SelectionListVSAssembly list = new SelectionListVSAssembly(vs, "SelectionList1");
      list.getSelectionListInfo().setVizMark(mark);
      list.getSelectionListInfo().initDefaultFormat();
      list.getSelectionListInfo().setListHeight(6);
      list.setPixelOffset(new Point(160, 230));
      list.setPixelSize(new Dimension(300, 150));

      CurrentSelectionVSAssembly container = new CurrentSelectionVSAssembly(vs, "CurrentSelection1");
      container.getVSAssemblyInfo().setVizMark(mark);
      container.setPixelOffset(new Point(160, 200));
      container.setPixelSize(new Dimension(300, 360));

      vs.addAssembly(list);
      vs.addAssembly(container);
      container.setAssemblies(new String[]{ "SelectionList1" });
      return vs;
   }

   private int layoutHeight(Viewsheet vs, String name) {
      return ((VSAssembly) vs.getAssembly(name)).getVSAssemblyInfo().getLayoutSize().height;
   }
```

- [ ] **Step 6: Run them to verify they fail**

Run: `./mvnw test -pl core -Dtest='GroupingServiceTest,AbstractLayoutTest'`
Expected: FAIL — `aMarkedListDroppedIntoAContainerFitsItsRows` gets 150; `aMarkedContainedListTakesItsTierHeightInADeviceLayout` gets 120. The two unmarked tests and the existing tests pass.

- [ ] **Step 7: Fix the drop and the device layout**

In `GroupingService.java`, replace:

```java
               objSize.height = sinfo.getListHeight() * defh + sinfo.getTitleHeight();
```

with:

```java
               objSize.height = sinfo.getContainedListHeight();
```

(`defh` stays statically imported: `:374` still uses it.)

In `AbstractLayout.java`, replace:

```java
            if(aInfo instanceof SelectionBaseVSAssemblyInfo) {
               height = ((SelectionBaseVSAssemblyInfo) aInfo).getListHeight();
            }
```

with:

```java
            if(aInfo instanceof SelectionBaseVSAssemblyInfo sinfo && sinfo.getVizMark() != null) {
               // a marked list fits its rows at its own density, as it does when opened
               childSize.height = sinfo.getContainedListHeight();
            }
            else if(aInfo instanceof SelectionBaseVSAssemblyInfo) {
               height = ((SelectionBaseVSAssemblyInfo) aInfo).getListHeight();
            }
```

The `else if(aInfo instanceof TimeSliderVSAssemblyInfo …)` branch that follows is unchanged in this task.

- [ ] **Step 8: Run all three to verify they pass**

Run: `./mvnw test -pl core -Dtest='GroupingServiceTest,AbstractLayoutTest,VSSelectionContainerServiceTest'`
Expected: PASS.

- [ ] **Step 9: Commit**

```bash
git branch --show-current
git add core/src/main/java/inetsoft/web/viewsheet/service/VSSelectionContainerService.java core/src/main/java/inetsoft/web/composer/vs/objects/controller/GroupingService.java core/src/main/java/inetsoft/uql/viewsheet/vslayout/AbstractLayout.java core/src/test/java/inetsoft/web/viewsheet/service/VSSelectionContainerServiceTest.java core/src/test/java/inetsoft/web/composer/vs/objects/controller/GroupingServiceTest.java core/src/test/java/inetsoft/uql/viewsheet/vslayout/AbstractLayoutTest.java
git commit -m "Open a list in a modern container to all its rows

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

### Task 3: The range slider's lane follows density; collapse reads its hidden flag

**Files:**
- Modify: `core/src/main/java/inetsoft/uql/viewsheet/internal/TimeSliderVSAssemblyInfo.java:428-429` and after `setHidden` (`:518-520`)
- Modify: `core/src/main/java/inetsoft/web/viewsheet/service/VSSelectionContainerService.java:164-166, 189, 229`
- Modify: `core/src/main/java/inetsoft/uql/viewsheet/vslayout/AbstractLayout.java` (the `TimeSliderVSAssemblyInfo` branch after Task 2's edit)
- Modify: `core/src/test/java/inetsoft/uql/viewsheet/internal/TitleLaneHeightRowTest.java:62, 75`
- Modify: `docs/superpowers/specs/lookfeel/chart-card-anchored-strip-lane-decisions.md` (decision 6)
- Test: `core/src/test/java/inetsoft/uql/viewsheet/internal/RangeSliderTitleLaneTest.java` (create)
- Test: `VSSelectionContainerServiceTest.java`, `AbstractLayoutTest.java` (extend)

**Interfaces:**
- Consumes: `VSDensityDefaults.titleHeight(T info, int stored)` (existing, `:250`); the Task 2 test helpers.
- Produces: `public boolean TimeSliderVSAssemblyInfo.isCollapsedInContainer(int storedHeight)` — `isHidden()` whenever it is set; unmarked falls back to `storedHeight == getTitleHeight()`, because a slider collapsed at a tier lane and then Reverted would otherwise be stuck. Task 5's fixture depends on the marked lane being 30 at comfortable.

- [ ] **Step 1: Write the failing lane test**

Create `core/src/test/java/inetsoft/uql/viewsheet/internal/RangeSliderTitleLaneTest.java` (license header as in Task 1):

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
class RangeSliderTitleLaneTest {
   @AfterEach
   void reset() {
      SreeEnv.setProperty("viewsheet.density", null);
   }

   private TimeSliderVSAssemblyInfo slider(String density, VizMark mark) {
      SreeEnv.setProperty("viewsheet.density", density);
      TimeSliderVSAssemblyInfo info = new TimeSliderVSAssemblyInfo();
      info.setVizMark(mark);
      return info;
   }

   @Test
   void aMarkedSliderTakesTheLaneAtEachTier() {
      assertEquals(30, slider("comfortable", VizMark.MODERN_LIGHT).getTitleHeight());
      assertEquals(26, slider("compact", VizMark.MODERN_LIGHT).getTitleHeight());
      assertEquals(20, slider("dense", VizMark.MODERN_LIGHT).getTitleHeight());
   }

   @Test
   void anAuthorLaneIsKept() {
      TimeSliderVSAssemblyInfo info = slider("comfortable", VizMark.MODERN_LIGHT);
      info.setTitleHeightValue(25);
      info.setUserTitleHeight(true);
      assertEquals(25, info.getTitleHeight());
   }

   @Test
   void anUnmarkedSliderKeepsItsStoredLane() {
      assertEquals(AssetUtil.defh, slider("comfortable", null).getTitleHeight());
   }

   // the lane is 30 now, but a slider collapsed before that was stored at 20
   @Test
   void aMarkedSliderIsCollapsedByItsHiddenFlag() {
      TimeSliderVSAssemblyInfo info = slider("comfortable", VizMark.MODERN_LIGHT);
      info.setHidden(true);
      assertTrue(info.isCollapsedInContainer(20));

      info.setHidden(false);
      assertFalse(info.isCollapsedInContainer(30));
   }

   @Test
   void anUnmarkedSliderIsCollapsedByItsStoredHeight() {
      TimeSliderVSAssemblyInfo info = slider("comfortable", null);
      info.setHidden(false);
      assertTrue(info.isCollapsedInContainer(AssetUtil.defh), "the old test, unchanged");
      assertFalse(info.isCollapsedInContainer(60));
   }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./mvnw test -pl core -Dtest=RangeSliderTitleLaneTest`
Expected: compilation error, `cannot find symbol: method isCollapsedInContainer(int)`.

- [ ] **Step 3: Resolve the lane and add the collapse test**

In `TimeSliderVSAssemblyInfo.java`, replace the body of `getTitleHeight()`:

```java
   @Override
   public int getTitleHeight() {
      return VSDensityDefaults.titleHeight(this, titleInfo.getTitleHeight());
   }
```

Immediately after `setHidden(boolean)`, add:

```java
   /**
    * Whether this slider is collapsed to its title lane in a selection container. The hidden flag
    * answers whenever it is set, since a collapsed height stored at another tier's lane (a
    * density change, or Revert) no longer equals the lane; unmarked falls back to the height test.
    */
   public boolean isCollapsedInContainer(int storedHeight) {
      return isHidden() || getVizMark() == null && storedHeight == getTitleHeight();
   }
```

- [ ] **Step 4: Run it to verify it passes**

Run: `./mvnw test -pl core -Dtest=RangeSliderTitleLaneTest`
Expected: 5 tests, PASS.

- [ ] **Step 5: Move the title-lane row test**

In `TitleLaneHeightRowTest.java`, delete:

```java
      assertEquals(AssetUtil.defh, marked(new TimeSliderVSAssemblyInfo()).getTitleHeight(), "range slider");
```

and add, as the last line of `includedTypesTakeTheDensityRow()`:

```java
      assertEquals(26, marked(new TimeSliderVSAssemblyInfo()).getTitleHeight(), "range slider");
```

Run: `./mvnw test -pl core -Dtest=TitleLaneHeightRowTest`
Expected: PASS.

- [ ] **Step 6: Write the failing expand and device-layout tests**

Add to `VSSelectionContainerServiceTest.java` (add `import inetsoft.uql.asset.internal.AssetUtil;`):

```java
   @Test
   void aMarkedSliderCollapsedAtTheOldLaneStillExpands() throws Exception {
      container(VizMark.MODERN_LIGHT, 800, "RangeSlider1");
      TimeSliderVSAssemblyInfo info = slider("RangeSlider1", VizMark.MODERN_LIGHT, true, 20);

      service.applySelection(rvs, "RangeSlider1", false, dispatcher, "");

      assertFalse(info.isHidden(), "the click opens it");
      assertEquals(2 * AssetUtil.defh + 30, info.getPixelSize().height);
   }

   @Test
   void aMarkedSliderCollapsesToItsLane() throws Exception {
      container(VizMark.MODERN_LIGHT, 800, "RangeSlider1");
      TimeSliderVSAssemblyInfo info = slider("RangeSlider1", VizMark.MODERN_LIGHT, false, 70);

      service.applySelection(rvs, "RangeSlider1", true, dispatcher, "");

      assertTrue(info.isHidden());
      assertEquals(30, info.getPixelSize().height);
   }

   @Test
   void anAuthorLaneStillExpands() throws Exception {
      container(VizMark.MODERN_LIGHT, 800, "RangeSlider1");
      TimeSliderVSAssemblyInfo info = slider("RangeSlider1", VizMark.MODERN_LIGHT, true, 25);
      info.setTitleHeightValue(25);
      info.setUserTitleHeight(true);

      service.applySelection(rvs, "RangeSlider1", false, dispatcher, "");

      assertFalse(info.isHidden());
      assertEquals(2 * AssetUtil.defh + 25, info.getPixelSize().height, "the pinned lane is kept");
   }

   // read as open, the overflow estimate would collapse it again and store it at 30
   @Test
   void aCollapsedMarkedSliderIsNotCollapsedAgainForRoom() throws Exception {
      container(VizMark.MODERN_LIGHT, 500, "RangeSlider1", "SelectionList1", "SelectionList2");
      TimeSliderVSAssemblyInfo slider = slider("RangeSlider1", VizMark.MODERN_LIGHT, true, 20);
      SelectionListVSAssembly open = list("SelectionList1", VizMark.MODERN_LIGHT, true, 230);
      list("SelectionList2", VizMark.MODERN_LIGHT, false, 30);

      service.applySelection(rvs, "SelectionList2", false, dispatcher, "");

      assertEquals(30, open.getPixelSize().height, "the open list makes the room");
      assertEquals(20, slider.getPixelSize().height, "the collapsed slider is left alone");
      assertTrue(slider.isHidden());
   }

   TimeSliderVSAssemblyInfo slider(String name, VizMark mark, boolean hidden, int height) {
      TimeSliderVSAssembly slider = new TimeSliderVSAssembly(vs, name);
      vs.addAssembly(slider);
      TimeSliderVSAssemblyInfo info = (TimeSliderVSAssemblyInfo) slider.getVSAssemblyInfo();
      info.setVizMark(mark);
      info.setListHeight(2);
      info.setHidden(hidden);
      info.setPixelSize(new Dimension(300, height));
      return info;
   }
```

Add to `AbstractLayoutTest.java`:

```java
   // stored at the old 20px lane; read as open it would take listHeight x defh
   @Test
   void aMarkedCollapsedSliderKeepsItsHeightInADeviceLayout() {
      Viewsheet vs = new Viewsheet();
      vs.getViewsheetInfo().setVizDensity("comfortable");

      TimeSliderVSAssembly slider = new TimeSliderVSAssembly(vs, "RangeSlider1");
      TimeSliderVSAssemblyInfo info = (TimeSliderVSAssemblyInfo) slider.getVSAssemblyInfo();
      info.setVizMark(VizMark.MODERN_LIGHT);
      info.setListHeight(2);
      info.setHidden(true);
      slider.setPixelOffset(new Point(160, 230));
      slider.setPixelSize(new Dimension(300, 20));

      CurrentSelectionVSAssembly container = new CurrentSelectionVSAssembly(vs, "CurrentSelection1");
      container.getVSAssemblyInfo().setVizMark(VizMark.MODERN_LIGHT);
      container.setPixelOffset(new Point(160, 200));
      container.setPixelSize(new Dimension(300, 360));

      vs.addAssembly(slider);
      vs.addAssembly(container);
      container.setAssemblies(new String[]{ "RangeSlider1" });

      Viewsheet applied = containerLayout().apply(vs);
      assertEquals(20, layoutHeight(applied, "RangeSlider1"));
   }
```

- [ ] **Step 7: Run them to verify they fail**

Run: `./mvnw test -pl core -Dtest='VSSelectionContainerServiceTest,AbstractLayoutTest'`
Expected: FAIL — `aMarkedSliderCollapsedAtTheOldLaneStillExpands` stays hidden (20 ≠ 30 reads as open, and the open branch does nothing for the target); `aCollapsedMarkedSliderIsNotCollapsedAgainForRoom` stores the slider at 30; `aMarkedCollapsedSliderKeepsItsHeightInADeviceLayout` gets 40. The other new tests pass.

- [ ] **Step 8: Use the collapse test at the four sites**

In `VSSelectionContainerService.java`, replace:

```java
            final boolean sliderExpanded = childAssemblyInfo instanceof TimeSliderVSAssemblyInfo &&
               (childSize.height !=
                  ((TimeSliderVSAssemblyInfo) childAssemblyInfo).getTitleHeight());
```

with:

```java
            final boolean sliderExpanded = childAssemblyInfo instanceof TimeSliderVSAssemblyInfo &&
               !((TimeSliderVSAssemblyInfo) childAssemblyInfo).isCollapsedInContainer(childSize.height);
```

Replace:

```java
            if(sliderSize.height == ((TimeSliderVSAssemblyInfo) targetAssemblyInfo).getTitleHeight()) {
```

with:

```java
            if(((TimeSliderVSAssemblyInfo) targetAssemblyInfo).isCollapsedInContainer(sliderSize.height)) {
```

Replace:

```java
            if(size.height == timeSliderInfo.getTitleHeight()) {
```

with:

```java
            if(timeSliderInfo.isCollapsedInContainer(size.height)) {
```

In `AbstractLayout.java`, replace:

```java
            else if(aInfo instanceof TimeSliderVSAssemblyInfo &&
               ((TimeSliderVSAssemblyInfo) aInfo).getTitleHeight() != childSize.getHeight())
            {
```

with:

```java
            else if(aInfo instanceof TimeSliderVSAssemblyInfo &&
               !((TimeSliderVSAssemblyInfo) aInfo).isCollapsedInContainer(childSize.height))
            {
```

- [ ] **Step 9: Run to verify they pass**

Run: `./mvnw test -pl core -Dtest='VSSelectionContainerServiceTest,AbstractLayoutTest,RangeSliderTitleLaneTest,TitleLaneHeightRowTest'`
Expected: PASS.

- [ ] **Step 10: Amend the lane decision record**

In `docs/superpowers/specs/lookfeel/chart-card-anchored-strip-lane-decisions.md`, decision 6, insert after the paragraph that ends `36 is a legacy default, not a layout requirement.`:

```markdown

**Amended 2026-10-05: the range slider joins the row too.** §05's exclusion of `TimeSlider` had no
mechanical reason behind it, unlike check box and radio button, and a range slider shows its title only
inside a selection container — where every sibling and every collapsed row already takes the lane. It
resolves through `VSDensityDefaults.titleHeight(this, stored)` like the other types. Because a
collapsed slider's stored height can no longer be compared with its lane, a marked slider is judged
collapsed by its hidden flag (`TimeSliderVSAssemblyInfo.isCollapsedInContainer`). See
[the container density design](./2026-10-05-selection-container-density-design.md) D4.
```

- [ ] **Step 11: Commit**

```bash
git branch --show-current
git add core/src/main/java/inetsoft/uql/viewsheet/internal/TimeSliderVSAssemblyInfo.java core/src/main/java/inetsoft/web/viewsheet/service/VSSelectionContainerService.java core/src/main/java/inetsoft/uql/viewsheet/vslayout/AbstractLayout.java core/src/test/java/inetsoft/uql/viewsheet/internal/RangeSliderTitleLaneTest.java core/src/test/java/inetsoft/uql/viewsheet/internal/TitleLaneHeightRowTest.java core/src/test/java/inetsoft/web/viewsheet/service/VSSelectionContainerServiceTest.java core/src/test/java/inetsoft/uql/viewsheet/vslayout/AbstractLayoutTest.java docs/superpowers/specs/lookfeel/chart-card-anchored-strip-lane-decisions.md
git commit -m "Give a contained range slider the density title lane

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

### Task 4: The range slider dialog's follow-the-density checkbox

**Files:**
- Modify: `core/src/main/java/inetsoft/web/composer/vs/dialog/RangeSliderPropertyDialogService.java:122-125, 263-268`
- Test: `core/src/test/java/inetsoft/web/composer/vs/dialog/RangeSliderTitleHeightDialogTest.java` (create)

**Interfaces:**
- Consumes: `TimeSliderVSAssemblyInfo.getTitleHeight()` resolving the lane (Task 3).
- Produces: package-private `static void RangeSliderPropertyDialogService.readTitleHeight(TimeSliderVSAssemblyInfo, SizePositionPaneModel)` and `static void applyTitleHeight(TimeSliderVSAssemblyInfo, SizePositionPaneModel)`.

- [ ] **Step 1: Write the failing test**

Create `core/src/test/java/inetsoft/web/composer/vs/dialog/RangeSliderTitleHeightDialogTest.java` (license header as in Task 1):

```java
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
class RangeSliderTitleHeightDialogTest {
   @AfterEach
   void reset() {
      SreeEnv.setProperty("viewsheet.density", null);
   }

   private TimeSliderVSAssemblyInfo slider(VizMark mark) {
      SreeEnv.setProperty("viewsheet.density", "compact");
      TimeSliderVSAssemblyInfo info = new TimeSliderVSAssemblyInfo();
      info.setVizMark(mark);
      return info;
   }

   @Test
   void readShowsTheTierLaneAndOffersTheCheckbox() {
      SizePositionPaneModel model = new SizePositionPaneModel();
      RangeSliderPropertyDialogService.readTitleHeight(slider(VizMark.MODERN_LIGHT), model);

      assertEquals(26, model.getTitleHeight());
      assertTrue(model.getTitleHeightFollowsDensity());
   }

   @Test
   void readOffersNoCheckboxOnAnUnmarkedSlider() {
      SizePositionPaneModel model = new SizePositionPaneModel();
      RangeSliderPropertyDialogService.readTitleHeight(slider(null), model);

      assertEquals(AssetUtil.defh, model.getTitleHeight());
      assertNull(model.getTitleHeightFollowsDensity());
   }

   @Test
   void applyFollowingReturnsAPinnedLaneToTheTier() {
      TimeSliderVSAssemblyInfo info = slider(VizMark.MODERN_LIGHT);
      info.setTitleHeightValue(25);
      info.setUserTitleHeight(true);
      SizePositionPaneModel model = new SizePositionPaneModel();
      model.setTitleHeight(25);
      model.setTitleHeightFollowsDensity(true);

      RangeSliderPropertyDialogService.applyTitleHeight(info, model);

      assertFalse(info.isUserTitleHeight());
      assertEquals(AssetUtil.defh, info.getTitleHeightValue());
      assertEquals(26, info.getTitleHeight());
   }

   @Test
   void applyNotFollowingPinsTheSubmittedLane() {
      TimeSliderVSAssemblyInfo info = slider(VizMark.MODERN_LIGHT);
      SizePositionPaneModel model = new SizePositionPaneModel();
      model.setTitleHeight(26);
      model.setTitleHeightFollowsDensity(false);

      RangeSliderPropertyDialogService.applyTitleHeight(info, model);

      assertTrue(info.isUserTitleHeight());
      SreeEnv.setProperty("viewsheet.density", "comfortable");
      assertEquals(26, info.getTitleHeight(), "pinned against a density change");
   }

   @Test
   void applyWithoutAFlagPinsOnlyAnEditedLane() {
      TimeSliderVSAssemblyInfo info = slider(null);
      SizePositionPaneModel model = new SizePositionPaneModel();
      model.setTitleHeight(AssetUtil.defh);

      RangeSliderPropertyDialogService.applyTitleHeight(info, model);
      assertFalse(info.isUserTitleHeight(), "an untouched lane stays unpinned");

      model.setTitleHeight(26);
      RangeSliderPropertyDialogService.applyTitleHeight(info, model);
      assertTrue(info.isUserTitleHeight());
      assertEquals(26, info.getTitleHeightValue());
   }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./mvnw test -pl core -Dtest=RangeSliderTitleHeightDialogTest`
Expected: compilation error, `cannot find symbol: method readTitleHeight`.

- [ ] **Step 3: Add the two helpers and call them**

In `RangeSliderPropertyDialogService.java`, add as the last two methods of the class, before the fields:

```java
   /**
    * Fill a contained slider's title height. Marked, it shows the lane it resolves and offers the
    * follow-the-density checkbox; unmarked, its stored height and no checkbox.
    */
   static void readTitleHeight(TimeSliderVSAssemblyInfo info, SizePositionPaneModel model) {
      model.setTitleHeight(VSDensityDefaults.titleHeight(info, info.getTitleHeightValue()));
      model.setTitleHeightFollowsDensity(
         info.getVizMark() == null ? null : !info.isUserTitleHeight());
   }

   /**
    * Store a contained slider's submitted title height. A missing flag keeps the old rule, which
    * pins only an edited height.
    */
   static void applyTitleHeight(TimeSliderVSAssemblyInfo info, SizePositionPaneModel model) {
      Boolean followsDensity = model.getTitleHeightFollowsDensity();

      if(followsDensity == null) {
         if(model.getTitleHeight() != info.getTitleHeightValue()) {
            info.setUserTitleHeight(true);
            info.setTitleHeightValue(model.getTitleHeight());
         }
      }
      else if(followsDensity) {
         info.setUserTitleHeight(false);
         info.setTitleHeightValue(info.getLegacyTitleHeight());
      }
      else {
         info.setUserTitleHeight(true);
         info.setTitleHeightValue(model.getTitleHeight());
      }
   }
```

Replace the read site:

```java
      if(timeSliderAssembly.getContainer() != null) {
         sizePositionPaneModel.setTitleHeight(timeSliderAssemblyInfo.getTitleHeightValue());
         sizePositionPaneModel.setContainer(true);
      }
```

with:

```java
      if(timeSliderAssembly.getContainer() != null) {
         readTitleHeight(timeSliderAssemblyInfo, sizePositionPaneModel);
         sizePositionPaneModel.setContainer(true);
      }
```

Replace the write site:

```java
      if(sizePositionPaneModel.getTitleHeight() > 0) {
         if(sizePositionPaneModel.getTitleHeight() != info.getTitleHeightValue()) {
            info.setUserTitleHeight(true);
            info.setTitleHeightValue(sizePositionPaneModel.getTitleHeight());
         }
      }
```

with:

```java
      // only a contained slider's dialog carries a title height
      if(sizePositionPaneModel.getTitleHeight() > 0) {
         applyTitleHeight(info, sizePositionPaneModel);
      }
```

`VSDensityDefaults` and `SizePositionPaneModel` resolve through the file's existing wildcard imports.

- [ ] **Step 4: Run tests to verify they pass**

Run: `./mvnw test -pl core -Dtest='RangeSliderTitleHeightDialogTest,RangeSliderPropertyDialogServiceTest'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git branch --show-current
git add core/src/main/java/inetsoft/web/composer/vs/dialog/RangeSliderPropertyDialogService.java core/src/test/java/inetsoft/web/composer/vs/dialog/RangeSliderTitleHeightDialogTest.java
git commit -m "Offer a contained range slider's title the follow-the-density checkbox

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

### Task 5: A marked container stores the stack it draws

**Files:**
- Modify: `core/src/main/java/inetsoft/uql/viewsheet/CurrentSelectionVSAssembly.java:158-195` (add two methods; change `layout()`)
- Modify: `core/src/main/java/inetsoft/report/io/viewsheet/CoordinateHelper.java:511-589`
- Test: `core/src/test/java/inetsoft/report/io/viewsheet/ContainerChildTopTest.java` (create)

**Interfaces:**
- Consumes: the marked slider lane of 30 at comfortable (Task 3).
- Produces: `public static int CurrentSelectionVSAssembly.getDrawnHeight(VSAssembly child)` and `public float CurrentSelectionVSAssembly.getChildTop(VSAssembly child, double containerTop)`.

The fixture used throughout: a container at (418, 38), Show Current Selections on with two lists outside it (two collapsed rows), holding L1 (dropdown list), S1 (open slider, stored 40), L2 (open list), S2 (hidden slider).

| | marked (comfortable) | unmarked |
|---|---|---|
| first child's top | 38 + 30 + 2 × 30 = 128 | 38 + 20 + 2 × 20 = 98 |
| drawn heights L1 / S1 / L2 / S2 | 30 / 70 / 230 / 30 | 20 / 60 / 140 / 20 |
| drawn tops L1 / S1 / L2 / S2 | 128 / 158 / 228 / 458 | 98 / 118 / 178 / 318 |
| legacy `layout()` tops | — | 98 / 118 / 158 / 298 (stored heights, no slider lane) |

- [ ] **Step 1: Pin today's numbers (characterization)**

Create `core/src/test/java/inetsoft/report/io/viewsheet/ContainerChildTopTest.java` (license header as in Task 1):

```java
package inetsoft.report.io.viewsheet;

import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.*;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class ContainerChildTopTest {
   private static final String[] CHILDREN = { "L1", "S1", "L2", "S2" };

   @Test
   void exportDrawsAMarkedContainersChildrenWhereTheBrowserStacksThem() {
      CurrentSelectionVSAssembly container = container(VizMark.MODERN_LIGHT);
      assertArrayEquals(new float[] { 128, 158, 228, 458 }, exportTops(container));
   }

   @Test
   void exportDrawsAnUnmarkedContainersChildrenAsBefore() {
      CurrentSelectionVSAssembly container = container(null);
      assertArrayEquals(new float[] { 98, 118, 178, 318 }, exportTops(container));
   }

   @Test
   void drawnHeightsAreUnchanged() {
      CurrentSelectionVSAssembly container = container(VizMark.MODERN_LIGHT);
      int[] heights = new int[CHILDREN.length];

      for(int i = 0; i < CHILDREN.length; i++) {
         heights[i] = CoordinateHelper.getAssemblySize(child(container, CHILDREN[i]), null).height;
      }

      assertArrayEquals(new int[] { 30, 70, 230, 30 }, heights);
   }

   // the legacy stacking ignores a slider's lane; out of scope, and its exports still read it
   @Test
   void anUnmarkedContainerKeepsItsLegacyStacking() {
      CurrentSelectionVSAssembly container = container(null);
      container.layout();
      assertArrayEquals(new int[] { 98, 118, 158, 298 }, storedTops(container));
   }

   private static float[] exportTops(CurrentSelectionVSAssembly container) {
      float[] tops = new float[CHILDREN.length];

      for(int i = 0; i < CHILDREN.length; i++) {
         tops[i] = CoordinateHelper.getContainerChildTop(
            container, child(container, CHILDREN[i]), 38, null);
      }

      return tops;
   }

   private static int[] storedTops(CurrentSelectionVSAssembly container) {
      int[] tops = new int[CHILDREN.length];

      for(int i = 0; i < CHILDREN.length; i++) {
         tops[i] = child(container, CHILDREN[i]).getPixelOffset().y;
      }

      return tops;
   }

   private static VSAssembly child(CurrentSelectionVSAssembly container, String name) {
      return (VSAssembly) container.getViewsheet().getAssembly(name);
   }

   // every mark is pinned, because construction takes the org gate's
   private static CurrentSelectionVSAssembly container(VizMark mark) {
      Viewsheet vs = new Viewsheet();
      vs.getViewsheetInfo().setVizDensity("comfortable");
      CurrentSelectionVSAssembly container = new CurrentSelectionVSAssembly(vs, "CurrentSelection1");
      vs.addAssembly(container);
      container.getVSAssemblyInfo().setVizMark(mark);
      container.getVSAssemblyInfo().setPixelOffset(new Point(418, 38));
      container.getVSAssemblyInfo().setPixelSize(new Dimension(300, 600));
      int lane = mark != null ? 30 : 20;

      list(vs, "L1", mark, SelectionVSAssemblyInfo.DROPDOWN_SHOW_TYPE, lane);
      slider(vs, "S1", mark, false, 40);
      list(vs, "L2", mark, SelectionVSAssemblyInfo.LIST_SHOW_TYPE, mark != null ? 230 : 140);
      slider(vs, "S2", mark, true, lane);
      list(vs, "Outside1", null, SelectionVSAssemblyInfo.LIST_SHOW_TYPE, 120);
      list(vs, "Outside2", null, SelectionVSAssemblyInfo.LIST_SHOW_TYPE, 120);

      container.setAssemblies(CHILDREN);
      container.setShowCurrentSelectionValue(true);
      container.updateOutSelection();
      assertEquals(2, container.getOutSelectionTitles().length, "the two lists outside");
      return container;
   }

   private static void list(Viewsheet vs, String name, VizMark mark, int showType, int height) {
      SelectionListVSAssembly list = new SelectionListVSAssembly(vs, name);
      vs.addAssembly(list);
      list.getSelectionListInfo().setVizMark(mark);
      list.getSelectionListInfo().setShowTypeValue(showType);
      list.getSelectionListInfo().setPixelOffset(new Point(0, 0));
      list.getSelectionListInfo().setPixelSize(new Dimension(300, height));
   }

   private static void slider(Viewsheet vs, String name, VizMark mark, boolean hidden, int height) {
      TimeSliderVSAssembly slider = new TimeSliderVSAssembly(vs, name);
      vs.addAssembly(slider);
      TimeSliderVSAssemblyInfo info = (TimeSliderVSAssemblyInfo) slider.getVSAssemblyInfo();
      info.setVizMark(mark);
      info.setHidden(hidden);
      info.setPixelOffset(new Point(0, 0));
      info.setPixelSize(new Dimension(300, height));
   }
}
```

Run: `./mvnw test -pl core -Dtest=ContainerChildTopTest`
Expected: 4 tests, PASS — these pin today's behaviour before anything moves.

- [ ] **Step 2: Write the failing tests for the move and the marked layout**

Add to `ContainerChildTopTest.java`:

```java
   @Test
   void theContainerOwnsTheRule() {
      CurrentSelectionVSAssembly container = container(VizMark.MODERN_LIGHT);
      float[] tops = new float[CHILDREN.length];

      for(int i = 0; i < CHILDREN.length; i++) {
         tops[i] = container.getChildTop(child(container, CHILDREN[i]), 38);
      }

      assertArrayEquals(new float[] { 128, 158, 228, 458 }, tops);
   }

   @Test
   void aMarkedContainerStoresTheStackItDraws() {
      CurrentSelectionVSAssembly container = container(VizMark.MODERN_LIGHT);
      container.layout();
      assertArrayEquals(new int[] { 128, 158, 228, 458 }, storedTops(container));
   }

   @Test
   void aMissingChildIsSkipped() {
      CurrentSelectionVSAssembly container = container(VizMark.MODERN_LIGHT);
      container.setAssemblies(new String[] { "L1", "Gone", "S1", "L2", "S2" });

      container.layout();

      assertArrayEquals(new int[] { 128, 158, 228, 458 }, storedTops(container));
   }
```

Run: `./mvnw test -pl core -Dtest=ContainerChildTopTest`
Expected: compilation error, `cannot find symbol: method getChildTop`.

- [ ] **Step 3: Move the rule onto the container**

In `CurrentSelectionVSAssembly.java`, add before `layout()`:

```java
   /**
    * The height this container draws a child at: a dropdown list is its title lane, a range slider
    * whose title shows is its lane when hidden and its stored height plus its lane otherwise, and
    * anything else is its stored height.
    */
   public static int getDrawnHeight(VSAssembly child) {
      Dimension psize = child.getPixelSize();

      if(child instanceof TimeSliderVSAssembly) {
         TimeSliderVSAssemblyInfo info = (TimeSliderVSAssemblyInfo) child.getVSAssemblyInfo();

         if(info.isTitleVisible()) {
            return info.isHidden() ? info.getTitleHeight() : psize.height + info.getTitleHeight();
         }
      }

      if(child instanceof SelectionListVSAssembly &&
         ((SelectionListVSAssembly) child).getShowType() ==
            SelectionListVSAssemblyInfo.DROPDOWN_SHOW_TYPE)
      {
         return ((SelectionListVSAssemblyInfo) child.getInfo()).getTitleHeight();
      }

      return psize.height;
   }

   /**
    * The y this container draws a child at: under its title and any collapsed out-selection rows,
    * after the children above it.
    * @param containerTop the container's top, in the caller's pixel space.
    */
   public float getChildTop(VSAssembly child, double containerTop) {
      Viewsheet vs = child.getViewsheet();
      CurrentSelectionVSAssemblyInfo cinfo = (CurrentSelectionVSAssemblyInfo) getVSAssemblyInfo();
      float titleH = cinfo.getTitleHeight();
      int outN = !isShowCurrentSelection() ? 0 : getOutSelectionTitles().length;
      float currentY = (float) (containerTop + titleH +
         outN * cinfo.getOutSelectionRowHeight(AssetUtil.defh));

      for(String name : getAssemblies()) {
         if(child.getName().equals(name)) {
            break;
         }

         VSAssembly ass = (VSAssembly) vs.getAssembly(name);

         if(ass == null) {
            continue;
         }

         currentY += getDrawnHeight(ass);
      }

      return currentY;
   }
```

In `layout()`, replace:

```java
      ArrayList arr = new ArrayList();
      int rowsHeight = AssetUtil.defh;// title covered
```

with:

```java
      ArrayList arr = new ArrayList();
      // marked, store the stack as drawn; unmarked keeps the legacy stacking its exports read
      boolean drawn = getVSAssemblyInfo().getVizMark() != null;
      int rowsHeight = AssetUtil.defh;// title covered
```

and replace:

```java
         Point childPos = assembly.getPixelOffset();
         Dimension asize = assembly.getPixelSize();

         if(childPos.y != pos.y + rowsHeight || childPos.x != pos.x ||
            asize.width != size.width)
         {
            childPos.y = pos.y + rowsHeight;
```

with:

```java
         Point childPos = assembly.getPixelOffset();
         Dimension asize = assembly.getPixelSize();
         int childY = drawn ? (int) getChildTop((VSAssembly) assembly, pos.y) :
            pos.y + rowsHeight;

         if(childPos.y != childY || childPos.x != pos.x ||
            asize.width != size.width)
         {
            childPos.y = childY;
```

In `CoordinateHelper.java`, replace the whole body of `getContainerChildTop` with a delegation, and its javadoc with:

```java
   /**
    * The y a selection container draws a child at. The container owns the rule, which its own
    * layout also stores. {@code dim} is unused: a container's children are never tables.
    * @param containerTop the container's top, in the caller's pixel space.
    */
   public static float getContainerChildTop(CurrentSelectionVSAssembly cassembly,
                                            VSAssembly child, double containerTop,
                                            Dimension dim)
   {
      return cassembly.getChildTop(child, containerTop);
   }
```

In `getAssemblySize(VSAssembly, Dimension)`, replace the three branches before `if(!(assembly instanceof TableDataVSAssembly))` — the `TimeSliderVSAssembly` block, the dropdown `SelectionListVSAssembly` block and the plain `SelectionListVSAssembly` block — with:

```java
      // a selection container child's drawn height, which the container owns
      if(assembly instanceof TimeSliderVSAssembly || assembly instanceof SelectionListVSAssembly) {
         return new Dimension(assembly.getPixelSize().width,
                              CurrentSelectionVSAssembly.getDrawnHeight(assembly));
      }
```

Every caller of this overload (`CoordinateHelper:128, 188, 543`, `HTMLCoordinateHelper:1079`, `PDFVSExporter:737`, `SVGVSExporter:418`) only reads `.width` or `.height`, so returning a new `Dimension` rather than the live pixel size is safe.

- [ ] **Step 4: Run to verify everything passes, including the #6157 export tests**

Run: `./mvnw test -pl core -Dtest='ContainerChildTopTest,SelectionContainerOutRowsExportTest,SelectionContainerChildExportTest'`
Expected: PASS — 7 in `ContainerChildTopTest`, and the two existing classes unchanged.

- [ ] **Step 5: Commit**

```bash
git branch --show-current
git add core/src/main/java/inetsoft/uql/viewsheet/CurrentSelectionVSAssembly.java core/src/main/java/inetsoft/report/io/viewsheet/CoordinateHelper.java core/src/test/java/inetsoft/report/io/viewsheet/ContainerChildTopTest.java
git commit -m "Store a modern container's children where it draws them

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

### Task 6: The container's default size keeps twelve lanes

**Files:**
- Modify: `core/src/main/java/inetsoft/uql/viewsheet/internal/VSDensityDefaults.java` (after `selectionSizeForMode`, `:228-233`; constant beside `SELECTION_ROWS`, `:239`)
- Modify: `core/src/main/java/inetsoft/uql/viewsheet/internal/CurrentSelectionVSAssemblyInfo.java:110-112`
- Modify: `core/src/test/java/inetsoft/uql/viewsheet/internal/SelectionDensitySizeTest.java:118-131`
- Modify: `docs/superpowers/specs/lookfeel/2026-10-01-selection-family-padding-design.md` (§11, last paragraph)
- Test: `core/src/test/java/inetsoft/uql/viewsheet/internal/ContainerDensitySizeTest.java` (create)

**Interfaces:**
- Produces: `public static Dimension VSDensityDefaults.containerSize(VizContext ctx)` and `public static boolean VSDensityDefaults.isSeededContainerSize(Dimension size)`.

- [ ] **Step 1: Write the failing test**

Create `core/src/test/java/inetsoft/uql/viewsheet/internal/ContainerDensitySizeTest.java` (license header as in Task 1):

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

import java.awt.Dimension;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class ContainerDensitySizeTest {
   @AfterEach
   void reset() {
      SreeEnv.setProperty("viewsheet.density", null);
   }

   private Dimension seeded(String density, VizMark mark, Dimension start) {
      SreeEnv.setProperty("viewsheet.density", density);
      CurrentSelectionVSAssemblyInfo info = new CurrentSelectionVSAssemblyInfo();
      info.setVizMark(mark);
      info.setPixelSize(start);
      info.seedChromeDefaults(VizContext.of(info));
      return info.getPixelSize();
   }

   @Test
   void aFreshContainerTakesTwelveLanesAtEachTier() {
      Dimension legacy = new Dimension(300, 240);
      assertEquals(new Dimension(300, 360), seeded("comfortable", VizMark.MODERN_LIGHT, legacy));
      assertEquals(new Dimension(300, 312), seeded("compact", VizMark.MODERN_LIGHT, legacy));
      assertEquals(new Dimension(300, 240), seeded("dense", VizMark.MODERN_LIGHT, legacy));
   }

   @Test
   void anAuthorSizeIsLeftAlone() {
      assertEquals(new Dimension(300, 500),
                   seeded("comfortable", VizMark.MODERN_LIGHT, new Dimension(300, 500)));
   }

   @Test
   void aDensityChangeMovesASeededSizeToTheNewTier() {
      assertEquals(new Dimension(300, 312),
                   seeded("compact", VizMark.MODERN_LIGHT, new Dimension(300, 360)));
   }

   @Test
   void revertRestoresTheLegacySize() {
      SreeEnv.setProperty("viewsheet.density", "comfortable");
      CurrentSelectionVSAssemblyInfo info = new CurrentSelectionVSAssemblyInfo();
      info.setPixelSize(new Dimension(300, 360));
      info.setVizMark(null);

      info.seedChromeDefaults(VizContext.ofTransition(null, null));

      assertEquals(new Dimension(300, 240), info.getPixelSize());
   }

   // every open re-runs the seed; an unmarked container that happens to be tier-sized keeps its box
   @Test
   void openingAnUnmarkedContainerLeavesATierSizedBoxAlone() {
      SreeEnv.setProperty("viewsheet.density", "comfortable");
      CurrentSelectionVSAssemblyInfo info = new CurrentSelectionVSAssemblyInfo();
      info.setPixelSize(new Dimension(300, 360));
      info.setVizMark(null);

      VizModernizeUtil.reseedAfterRestore(info);

      assertEquals(new Dimension(300, 360), info.getPixelSize());
   }

   @Test
   void theRecognizerAcceptsOnlySeededSizes() {
      assertTrue(VSDensityDefaults.isSeededContainerSize(new Dimension(300, 240)));
      assertTrue(VSDensityDefaults.isSeededContainerSize(new Dimension(300, 312)));
      assertTrue(VSDensityDefaults.isSeededContainerSize(new Dimension(300, 360)));
      assertFalse(VSDensityDefaults.isSeededContainerSize(new Dimension(300, 361)));
      assertFalse(VSDensityDefaults.isSeededContainerSize(new Dimension(320, 360)));
      assertFalse(VSDensityDefaults.isSeededContainerSize(null));
   }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./mvnw test -pl core -Dtest=ContainerDensitySizeTest`
Expected: compilation error, `cannot find symbol: method isSeededContainerSize`.

- [ ] **Step 3: Add the size pair and the seed rule**

In `VSDensityDefaults.java`, after `selectionSizeForMode`:

```java
   /**
    * The default size of a selection container: twelve title lanes, what the legacy 300x240 held
    * at 20px. Its children collapse to one lane each, so capacity counts lanes, not rows. The
    * width has no density opinion and the container no inset.
    */
   public static Dimension containerSize(VizContext ctx) {
      if(!ctx.modern) {
         return legacyContainerSize();
      }

      return containerSizeForMode(ctx.density);
   }

   /**
    * Whether a size is one containerSize() could have written. Anything else is an author size.
    */
   public static boolean isSeededContainerSize(Dimension size) {
      if(size == null) {
         return false;
      }

      return size.equals(legacyContainerSize())
         || size.equals(containerSizeForMode(COMFORTABLE))
         || size.equals(containerSizeForMode(COMPACT))
         || size.equals(containerSizeForMode(DENSE));
   }

   private static Dimension legacyContainerSize() {
      return new Dimension(3 * AssetUtil.defw, CONTAINER_LANES * AssetUtil.defh);
   }

   private static Dimension containerSizeForMode(String mode) {
      return new Dimension(3 * AssetUtil.defw, CONTAINER_LANES * titleHeightForMode(mode));
   }
```

and beside `SELECTION_ROWS`:

```java
   private static final int CONTAINER_LANES = 12;
```

In `CurrentSelectionVSAssemblyInfo.java`, replace:

```java
      // no card inset and no size rule: the container is a frame around child assemblies that
      // inset themselves, so a second inset here would only indent them twice. Its box therefore
      // keeps the legacy basis at every tier
```

with:

```java
      // no card inset: its children inset themselves, so one here would indent them twice

      // twelve lanes at the tier; only a size the rule wrote moves, and unmarked only under Revert
      if((ctx.modern || ctx.transition) &&
         VSDensityDefaults.isSeededContainerSize(getPixelSize()))
      {
         setPixelSize(VSDensityDefaults.containerSize(ctx));
      }
```

- [ ] **Step 4: Run it to verify it passes**

Run: `./mvnw test -pl core -Dtest=ContainerDensitySizeTest`
Expected: 6 tests, PASS.

- [ ] **Step 5: Split the test that pinned the old size**

Run: `./mvnw test -pl core -Dtest=SelectionDensitySizeTest`
Expected: FAIL in `aModernizedContainerIsUnchanged` — expected `300x240`, was `300x360`.

In `SelectionDensitySizeTest.java`, replace the whole `aModernizedContainerIsUnchanged` method with:

```java
   @Test
   void aModernizedContainerTakesNoInset() {
      SreeEnv.setProperty("viewsheet.density", "comfortable");
      CurrentSelectionVSAssemblyInfo unmarked = new CurrentSelectionVSAssemblyInfo();
      CurrentSelectionVSAssemblyInfo marked = new CurrentSelectionVSAssemblyInfo();
      marked.setVizMark(VizMark.MODERN_LIGHT);

      marked.seedChromeDefaults(VizContext.of(marked));

      // a frame around children that inset themselves; its size follows the density instead
      assertEquals(unmarked.getPadding(), marked.getPadding());
   }
```

Run: `./mvnw test -pl core -Dtest='SelectionDensitySizeTest,ContainerDensitySizeTest'`
Expected: PASS.

- [ ] **Step 6: Point the selection family design at this slice**

In `docs/superpowers/specs/lookfeel/2026-10-01-selection-family-padding-design.md`, replace:

```markdown
Whether the container's default size should grow to keep its row count at the larger tiers remains
a separate slice.
```

with:

```markdown
Whether the container's default size should grow to keep its row count at the larger tiers remains
a separate slice. **Taken up 2026-10-05** in
[the container density design](./2026-10-05-selection-container-density-design.md): twelve lanes at
the tier's lane height, 300×360 / 312 / 240.
```

- [ ] **Step 7: Commit**

```bash
git branch --show-current
git add core/src/main/java/inetsoft/uql/viewsheet/internal/VSDensityDefaults.java core/src/main/java/inetsoft/uql/viewsheet/internal/CurrentSelectionVSAssemblyInfo.java core/src/test/java/inetsoft/uql/viewsheet/internal/ContainerDensitySizeTest.java core/src/test/java/inetsoft/uql/viewsheet/internal/SelectionDensitySizeTest.java docs/superpowers/specs/lookfeel/2026-10-01-selection-family-padding-design.md
git commit -m "Size a new selection container to twelve density lanes

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

### Task 7: Verification

**Files:**
- Modify (untracked): `docs/superpowers/plans/2026-10-05-selection-container-density-checks.md` — the Results section
- Create (enterprise root, untracked): `E:/StyleBI/stylebi-enterprise/selection-container-density-pr-draft.md`

- [ ] **Step 1: Full core suite**

Run: `./mvnw test -pl core`
Expected: BUILD SUCCESS. Record the test count in the checks doc. If a failure names a class with no source on this branch, it is a stale `target/test-classes` artifact from another branch: run `./mvnw clean test -pl core`.

- [ ] **Step 2: The CSV check is withdrawn**

The check is withdrawn: `CSVUtil.needExport`'s callers pass only table-data assemblies, so container children never reach it.

- [ ] **Step 3: The user builds and restarts the server from this branch**

The server builds from the shared checkout, which is on another session's branch. Ask the user to build and restart from `feature-selection-container-density` (core and web) when that is free, and to tell you where the core jar the running server loaded is. Prove the build with `javap` against that jar:

```bash
javap -cp "$CORE_JAR" inetsoft.uql.viewsheet.CurrentSelectionVSAssembly | grep getChildTop
```

Expected: `public float getChildTop(inetsoft.uql.viewsheet.VSAssembly, double);`. If it is missing, the server is running an old build — stop.

- [ ] **Step 4: After-exports and MC-1**

Open each fixture viewsheet once so the restore path re-seeds it, re-send the `ConMix` expand event, then:

```bash
cd E:/StyleBI/stylebi-enterprise/community/.superpowers/baselines/selection-padding
python export_all.py after-container
python compare.py before-container after-container legacy
```

Capture PNG, PPTX and CSV into `after-container-png-pptx/` exactly as Task 0 Step 3 did. Expected: the `legacy` exports identical in every format (MC-1) — `compare.py` for HTML, PDF and XLSX; PNG pixel for pixel, PPTX zip entries except `docProps/`, CSV text for the rest.

- [ ] **Step 5: MC-2 to MC-7**

Run each check in the checks doc's table and record the measured values in its Results section:

- MC-2: count rows in an expanded `ConMix` list — 6 of 6 at every tier — live and in PDF / HTML / PNG.
- MC-3: the range slider's lane height equals its sibling lists' (30 / 26 / 20); open `ConOld`'s slider, collapsed before the fix, and expand and collapse it.
- MC-4: after a fresh open (or a change that lays the viewsheet out, not right after an expand), each `ConMix` child's saved `pixelOffset` in the asset XML equals its drawn top from `html_measure.js`.
- MC-5: the slider at `ConMix`'s bottom edge — clipped and included in PDF and PNG as the viewer shows it.
- MC-6: `ConNew` 300×360 / 312 / 240; `ConOld` grown on open; Revert gives 300×240; dense unchanged.
- MC-7: in a modern container sized so two open lists overflow, expanding the second collapses the first.

- [ ] **Step 6: Draft the PR description**

Write `E:/StyleBI/stylebi-enterprise/selection-container-density-pr-draft.md`: what changed by decision (D1-D6), the accepted costs from the spec's §9, the MC results, and the test count. No blockquotes. End with:

```
🤖 Generated with [Claude Code](https://claude.com/claude-code)
```

Ask the user before pushing or opening the PR.

---

# Part 2 — D7: an author's size (added 2026-10-06)

**Goal:** an author's deliberate size on a selection list, tree or container survives every open, density change, Modernize and Revert, and a Follow default density checkbox for size gives the box back to the density.

**Architecture:** a `userSize` flag on `VSAssemblyInfo`, beside `userPadding`, which both size rules require to be clear. `takesDensitySize()` and `resetSize(VizContext)` mirror `defaultPadding` / `resetPadding`. Two author paths set the flag: the composer's drag-resize, and Size & Position in the three property dialogs. Static helpers on `VSDialogService` keep the three dialogs from repeating the logic. The browser gets one checkbox in the existing `size-position-pane`.

**Spec:** `docs/superpowers/specs/lookfeel/2026-10-05-selection-container-density-design.md` D7 and §6 (amended at `ad67c7f13f`).

## Global Constraints (Part 2)

- **Work in the shared checkout** `E:/StyleBI/stylebi-enterprise/community`, which is on `feature-selection-container-density`. The scratchpad worktree is detached and is not used. Stage only the files your task lists: the checkout carries unrelated local edits (`web/angular.json`, `web/package-lock.json`) and untracked docs that must never be committed.
- The flag is named `userSize`. Its XML attribute `userSize="true"` is written **only when true**. A missing attribute parses as false.
- `takesDensitySize()` is true only on `SelectionBaseVSAssemblyInfo` (list and tree) and `CurrentSelectionVSAssemblyInfo`.
- Only the author's resize paths set the flag: composer drag-resize, and Size & Position in the list, tree and container dialogs. Derived writes never do: expand and collapse, drop, the show-type switch, convert-to-range-slider, the size rules.
- The size checkbox (`sizeFollowsDensity`) is offered for a **marked** container, or a **marked** list or tree in **LIST** show type that is **not** a selection container's child. Everywhere else it is null.
- Comments are why, not what, as a short clause. No comments in `.html`. No ticket, PR or design-doc references in source.
- Commits: `git -C E:/StyleBI/stylebi-enterprise/community branch --show-current` first, then `git -C … add <files>` and `git -C … commit …` as separate commands, never chained with `&&`. End the message with a `Co-Authored-By:` trailer naming your own model.

## How to run tests (Part 2)

```bash
# from E:/StyleBI/stylebi-enterprise/community
./mvnw test -pl core -Dtest=AuthorSizeFlagTest
# frontend, from E:/StyleBI/stylebi-enterprise/community/web
npx ng test portal --include="**/size-position-pane.spec.ts"
```

## Review Focus (Part 2)

1. **Following density on a container re-widens its children.** The container dialog's `setContainerSize` writes the model's width into every child, so the tier size has to be in the model before that call. **Task 10**, `followingWritesTheTierSizeIntoTheBoxAndTheModel`.
2. **Dragging a container must not flag its children**, whose widths the same resize changes. **Task 9**, `aDraggedContainerIsFlaggedButNotItsChildren`.
3. **A client that sends no follow flag must not flag an unchanged box.** Applying the dialog only to change a title must leave the box following density. **Task 10**, `noAnswerSetsTheFlagOnlyOnAChange`.
4. **A flagged box survives Revert and Modernize,** not only an ordinary open. **Task 8**, `anAuthorSizeSurvivesEveryRerun`.
5. **A type without a density size is untouched** by `resetSize`, the dialog helpers and the composer resize. **Task 8** `resetSizeDoesNothingWithoutADensitySize`, **Task 9** `aDraggedTextIsNotFlagged`, **Task 10** `theHelpersLeaveATypeWithoutADensitySizeAlone`.

---

### Task 8: The author size flag, and the rules honour it

**Files:**
- Modify: `core/src/main/java/inetsoft/uql/viewsheet/internal/VSAssemblyInfo.java`: `copyViewInfo` (after the `userPadding` block, `:701-704`), `writeContents` (after `:882`), `parseContents` (after `:931`), accessors (after `setUserPadding`, `:1421-1423`), field (after `:1876`)
- Modify: `core/src/main/java/inetsoft/uql/viewsheet/internal/SelectionBaseVSAssemblyInfo.java:1010-1011`, plus two overrides beside `defaultPadding`
- Modify: `core/src/main/java/inetsoft/uql/viewsheet/internal/CurrentSelectionVSAssemblyInfo.java:113-114`, plus two overrides
- Test: `core/src/test/java/inetsoft/uql/viewsheet/internal/AuthorSizeFlagTest.java` (create)

**Interfaces:**
- Produces: `public boolean VSAssemblyInfo.isUserSize()`, `public void setUserSize(boolean)`, `public boolean takesDensitySize()`, `protected Dimension defaultSize(VizContext)`, `public void resetSize(VizContext)`. Tasks 9 and 10 use the public ones.

- [ ] **Step 1: Write the failing test**

Create `AuthorSizeFlagTest.java` with the GNU AGPL header from `ContainedListHeightTest.java` (2026):

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
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilderFactory;
import java.awt.Dimension;
import java.io.*;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class AuthorSizeFlagTest {
   @AfterEach
   void reset() {
      SreeEnv.setProperty("viewsheet.density", null);
   }

   @Test
   void anUnsetFlagWritesNoAttributeAndParsesAsFalse() throws Exception {
      CurrentSelectionVSAssemblyInfo saved = new CurrentSelectionVSAssemblyInfo();

      assertFalse(xml(saved).contains("userSize"), "a box nobody resized keeps its saved form");

      CurrentSelectionVSAssemblyInfo loaded = new CurrentSelectionVSAssemblyInfo();
      loaded.parseXML(element(saved));
      assertFalse(loaded.isUserSize());
   }

   @Test
   void aSetFlagRoundTrips() throws Exception {
      SelectionListVSAssemblyInfo saved = new SelectionListVSAssemblyInfo();
      saved.setUserSize(true);

      assertTrue(xml(saved).contains("userSize=\"true\""));

      SelectionListVSAssemblyInfo loaded = new SelectionListVSAssemblyInfo();
      loaded.parseXML(element(saved));
      assertTrue(loaded.isUserSize());
   }

   @Test
   void copyCarriesTheFlag() {
      CurrentSelectionVSAssemblyInfo from = new CurrentSelectionVSAssemblyInfo();
      from.setUserSize(true);
      CurrentSelectionVSAssemblyInfo to = new CurrentSelectionVSAssemblyInfo();

      assertTrue(to.copyViewInfo(from, false), "a changed flag reports a change");
      assertTrue(to.isUserSize());
   }

   // open, density change, Modernize and Revert all run this hook
   @Test
   void anAuthorSizeSurvivesEveryRerun() {
      SreeEnv.setProperty("viewsheet.density", "comfortable");
      CurrentSelectionVSAssemblyInfo container = authored(new CurrentSelectionVSAssemblyInfo(),
                                                          new Dimension(300, 240));
      SelectionListVSAssemblyInfo list = authored(new SelectionListVSAssemblyInfo(),
                                                  new Dimension(100, 120));
      SelectionTreeVSAssemblyInfo tree = authored(new SelectionTreeVSAssemblyInfo(),
                                                  new Dimension(100, 120));

      for(VSAssemblyInfo info : new VSAssemblyInfo[] { container, list, tree }) {
         VizModernizeUtil.reseedAfterRestore(info);
         info.seedChromeDefaults(VizContext.of(info));
         SreeEnv.setProperty("viewsheet.density", "compact");
         info.seedChromeDefaults(VizContext.of(info));
         info.setVizMark(null);
         info.seedChromeDefaults(VizContext.ofTransition(null, null));
         SreeEnv.setProperty("viewsheet.density", "comfortable");
      }

      assertEquals(new Dimension(300, 240), container.getPixelSize());
      assertEquals(new Dimension(100, 120), list.getPixelSize());
      assertEquals(new Dimension(100, 120), tree.getPixelSize());
   }

   @Test
   void anUnflaggedSeededSizeStillFollowsTheRule() {
      SreeEnv.setProperty("viewsheet.density", "comfortable");
      CurrentSelectionVSAssemblyInfo container = new CurrentSelectionVSAssemblyInfo();
      container.setVizMark(VizMark.MODERN_LIGHT);
      container.setPixelSize(new Dimension(300, 240));

      container.seedChromeDefaults(VizContext.of(container));

      assertEquals(new Dimension(300, 360), container.getPixelSize());
   }

   @Test
   void resetSizeClearsTheFlagAndWritesTheTierSize() {
      SreeEnv.setProperty("viewsheet.density", "comfortable");
      CurrentSelectionVSAssemblyInfo container = authored(new CurrentSelectionVSAssemblyInfo(),
                                                          new Dimension(300, 500));
      SelectionListVSAssemblyInfo list = authored(new SelectionListVSAssemblyInfo(),
                                                  new Dimension(300, 400));
      SelectionTreeVSAssemblyInfo tree = authored(new SelectionTreeVSAssemblyInfo(),
                                                  new Dimension(300, 400));

      container.resetSize(VizContext.of(container));
      list.resetSize(VizContext.of(list));
      tree.resetSize(VizContext.of(tree));

      assertEquals(new Dimension(300, 360), container.getPixelSize());
      assertEquals(new Dimension(132, 202), list.getPixelSize());
      assertEquals(new Dimension(132, 202), tree.getPixelSize());
      assertFalse(container.isUserSize() || list.isUserSize() || tree.isUserSize());
   }

   @Test
   void resetSizeDoesNothingWithoutADensitySize() {
      SreeEnv.setProperty("viewsheet.density", "comfortable");
      ChartVSAssemblyInfo chart = new ChartVSAssemblyInfo();
      chart.setVizMark(VizMark.MODERN_LIGHT);
      chart.setPixelSize(new Dimension(400, 300));
      chart.setUserSize(true);

      chart.resetSize(VizContext.of(chart));

      assertFalse(chart.takesDensitySize());
      assertEquals(new Dimension(400, 300), chart.getPixelSize());
      assertTrue(chart.isUserSize(), "a type without a density size keeps its flag untouched");
   }

   private static <T extends VSAssemblyInfo> T authored(T info, Dimension size) {
      info.setVizMark(VizMark.MODERN_LIGHT);
      info.setPixelSize(size);
      info.setUserSize(true);
      return info;
   }

   private static String xml(VSAssemblyInfo info) {
      StringWriter sw = new StringWriter();
      PrintWriter writer = new PrintWriter(sw);
      info.writeXML(writer);
      writer.flush();
      return sw.toString();
   }

   private static Element element(VSAssemblyInfo info) throws Exception {
      DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
      dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
      Document doc = dbf.newDocumentBuilder()
         .parse(new ByteArrayInputStream(xml(info).getBytes(StandardCharsets.UTF_8)));
      return doc.getDocumentElement();
   }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./mvnw test -pl core -Dtest=AuthorSizeFlagTest`
Expected: compilation error, `cannot find symbol: method setUserSize(boolean)`.

- [ ] **Step 3: Add the flag to `VSAssemblyInfo`**

In `copyViewInfo`, right after the `userPadding` block:

```java
      if(userSize != info.userSize) {
         userSize = info.userSize;
         result = true;
      }
```

In `writeContents`, right after `writer.print(" userPadding=\"" + isUserPadding() + "\"");`:

```java
      // only when set, so a box nobody resized keeps its saved form
      if(userSize) {
         writer.print(" userSize=\"true\"");
      }
```

In `parseContents`, right after the `setUserPadding(...)` line:

```java
      setUserSize("true".equalsIgnoreCase(Tool.getAttribute(elem, "userSize")));
```

After `setUserPadding(boolean)`:

```java
   /**
    * Whether the author set the size. Surfaced in Size & Position as the follow-the-default-density
    * checkbox for size, inverted; the density size rules leave a box with it set alone.
    */
   public boolean isUserSize() {
      return userSize;
   }

   /**
    * Set whether the size was set by the author.
    */
   public void setUserSize(boolean userSize) {
      this.userSize = userSize;
   }

   /**
    * Whether this type takes a density default size from the seed. Overridden by the types that
    * have one.
    */
   public boolean takesDensitySize() {
      return false;
   }

   /**
    * The size this type takes when nobody has an opinion, or null for a type without one.
    */
   protected Dimension defaultSize(VizContext ctx) {
      return null;
   }

   /**
    * Return the box to its density default, which is what Size & Position's follow-the-default
    * checkbox asks for. Clears the author flag, so the seed manages the size again.
    */
   public void resetSize(VizContext ctx) {
      Dimension size = defaultSize(ctx);

      if(size == null) {
         return;
      }

      setUserSize(false);
      setPixelSize(size);
   }
```

After the `userPadding` field:

```java
   // whether the author set the size; the density size rules leave such a box alone
   private boolean userSize = false;
```

- [ ] **Step 4: Make both rules honour it, and give both types their size**

In `SelectionBaseVSAssemblyInfo.java`, replace:

```java
      if((ctx.modern || ctx.transition) &&
         VSDensityDefaults.isSeededSelectionSize(getPixelSize()))
```

with:

```java
      if(!isUserSize() && (ctx.modern || ctx.transition) &&
         VSDensityDefaults.isSeededSelectionSize(getPixelSize()))
```

and add, beside `defaultPadding(VizContext)`:

```java
   @Override
   public boolean takesDensitySize() {
      return true;
   }

   @Override
   protected Dimension defaultSize(VizContext ctx) {
      return VSDensityDefaults.selectionSize(ctx);
   }
```

In `CurrentSelectionVSAssemblyInfo.java`, replace:

```java
      if((ctx.modern || ctx.transition) &&
         VSDensityDefaults.isSeededContainerSize(getPixelSize()))
```

with:

```java
      if(!isUserSize() && (ctx.modern || ctx.transition) &&
         VSDensityDefaults.isSeededContainerSize(getPixelSize()))
```

and add, after `seedChromeDefaults`:

```java
   @Override
   public boolean takesDensitySize() {
      return true;
   }

   @Override
   protected Dimension defaultSize(VizContext ctx) {
      return VSDensityDefaults.containerSize(ctx);
   }
```

Extend the comment above each rule by one short clause: an author's size carries `userSize` and is never moved.

- [ ] **Step 5: Run it and the size suites**

Run: `./mvnw test -pl core -Dtest='AuthorSizeFlagTest,ContainerDensitySizeTest,SelectionDensitySizeTest,ContainedListHeightTest'`
Expected: PASS, with no change to the existing three classes.

- [ ] **Step 6: Commit**

```bash
git -C E:/StyleBI/stylebi-enterprise/community branch --show-current
git -C E:/StyleBI/stylebi-enterprise/community add core/src/main/java/inetsoft/uql/viewsheet/internal/VSAssemblyInfo.java core/src/main/java/inetsoft/uql/viewsheet/internal/SelectionBaseVSAssemblyInfo.java core/src/main/java/inetsoft/uql/viewsheet/internal/CurrentSelectionVSAssemblyInfo.java core/src/test/java/inetsoft/uql/viewsheet/internal/AuthorSizeFlagTest.java
git -C E:/StyleBI/stylebi-enterprise/community commit -m "Record an author's selection size so the density size rules leave it" -m "Co-Authored-By: <your model> <noreply@anthropic.com>"
```

---

### Task 9: A composer drag-resize records the author's size

**Files:**
- Modify: `core/src/main/java/inetsoft/web/composer/vs/objects/controller/ComposerObjectService.java`, `resizeObject` (`:126`), right after `info.setPixelSize(size);`
- Test: `core/src/test/java/inetsoft/web/composer/vs/objects/controller/ComposerObjectServiceTest.java` (extend)

**Interfaces:**
- Consumes: `VSAssemblyInfo.takesDensitySize()`, `setUserSize(boolean)`, `isUserSize()` (Task 8).

- [ ] **Step 1: Write the failing tests**

Add to `ComposerObjectServiceTest.java` (change the assertion import to `import static org.junit.jupiter.api.Assertions.*;`):

```java
   @Test
   void aDraggedContainerIsFlaggedButNotItsChildren() throws Exception {
      Viewsheet vs = new Viewsheet();
      vs.getVSAssemblyInfo().setName("vs1");

      SelectionListVSAssembly list = new SelectionListVSAssembly(vs, "SelectionList1");
      list.getVSAssemblyInfo().setPixelSize(new Dimension(300, 30));
      vs.addAssembly(list);

      CurrentSelectionVSAssembly container = new CurrentSelectionVSAssembly(vs, "CurrentSelection1");
      container.getVSAssemblyInfo().setPixelOffset(new Point(0, 0));
      container.getVSAssemblyInfo().setPixelSize(new Dimension(300, 360));
      vs.addAssembly(container);
      container.setAssemblies(new String[] { "SelectionList1" });

      resize(vs, "CurrentSelection1", 300, 240);

      assertTrue(container.getVSAssemblyInfo().isUserSize());
      assertFalse(list.getVSAssemblyInfo().isUserSize(), "its re-widened children are not the author's");
   }

   @Test
   void aDraggedListIsFlagged() throws Exception {
      Viewsheet vs = new Viewsheet();
      vs.getVSAssemblyInfo().setName("vs1");
      SelectionListVSAssembly list = new SelectionListVSAssembly(vs, "SelectionList1");
      list.getVSAssemblyInfo().setPixelOffset(new Point(0, 0));
      list.getVSAssemblyInfo().setPixelSize(new Dimension(132, 202));
      vs.addAssembly(list);

      resize(vs, "SelectionList1", 100, 120);

      assertTrue(list.getVSAssemblyInfo().isUserSize());
   }

   @Test
   void aDraggedTextIsNotFlagged() throws Exception {
      Viewsheet vs = new Viewsheet();
      vs.getVSAssemblyInfo().setName("vs1");
      TextVSAssembly text = new TextVSAssembly();
      text.getVSAssemblyInfo().setName("Text1");
      text.getVSAssemblyInfo().setPixelOffset(new Point(0, 0));
      text.getVSAssemblyInfo().setPixelSize(new Dimension(100, 20));
      vs.addAssembly(text);

      resize(vs, "Text1", 200, 40);

      assertFalse(text.getVSAssemblyInfo().isUserSize(), "a type without a density size stays clean");
   }

   private void resize(Viewsheet vs, String name, int width, int height) throws Exception {
      when(engine.getViewsheet(any(), any())).thenReturn(rvs);
      when(rvs.getViewsheet()).thenReturn(vs);
      ResizeVSObjectEvent event = new ResizeVSObjectEvent();
      event.setName(name);
      event.setxOffset(0);
      event.setyOffset(0);
      event.setWidth(width);
      event.setHeight(height);
      service.resizeObject(runtimeViewsheetRef.getRuntimeId(), event, principal, dispatcher, "/test");
   }
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./mvnw test -pl core -Dtest=ComposerObjectServiceTest`
Expected: FAIL in `aDraggedContainerIsFlaggedButNotItsChildren` and `aDraggedListIsFlagged` (the flag stays false). `aDraggedTextIsNotFlagged` and the existing tests pass. If a test fails with an exception, or a strict-stubbing error, fix the fixture, not the production code.

- [ ] **Step 3: Set the flag on an author's resize**

In `ComposerObjectService.resizeObject`, right after `info.setPixelSize(size);`:

```java
         // an author's drag owns the size; the density size rules leave it alone from here on
         if(info.takesDensitySize()) {
            info.setUserSize(true);
         }
```

Do not touch the container-children loop below it.

- [ ] **Step 4: Run them to verify they pass**

Run: `./mvnw test -pl core -Dtest=ComposerObjectServiceTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git -C E:/StyleBI/stylebi-enterprise/community branch --show-current
git -C E:/StyleBI/stylebi-enterprise/community add core/src/main/java/inetsoft/web/composer/vs/objects/controller/ComposerObjectService.java core/src/test/java/inetsoft/web/composer/vs/objects/controller/ComposerObjectServiceTest.java
git -C E:/StyleBI/stylebi-enterprise/community commit -m "Keep a dragged selection size as the author's" -m "Co-Authored-By: <your model> <noreply@anthropic.com>"
```

---

### Task 10: Size & Position reads and writes the size checkbox

**Files:**
- Modify: `core/src/main/java/inetsoft/web/composer/model/vs/SizePositionPaneModel.java` (accessors beside `getCellHeightFollowsDensity`, field beside `cellHeightFollowsDensity`)
- Modify: `core/src/main/java/inetsoft/web/viewsheet/service/VSDialogService.java` (three static helpers after `setContainerSize`)
- Modify: `core/src/main/java/inetsoft/web/composer/vs/dialog/SelectionListPropertyDialogService.java` (read `:118-133`, write `:245`)
- Modify: `core/src/main/java/inetsoft/web/composer/vs/dialog/SelectionTreePropertyDialogService.java` (read `:118-133`, write `:272`)
- Modify: `core/src/main/java/inetsoft/web/composer/vs/dialog/SelectionContainerPropertyDialogService.java` (read `:86-93`, write `:171`)
- Test: `core/src/test/java/inetsoft/web/viewsheet/service/SizeFollowsDensityDialogTest.java` (create)

**Interfaces:**
- Consumes: Task 8's `isUserSize`, `setUserSize`, `takesDensitySize`, `resetSize`.
- Produces: `Boolean SizePositionPaneModel.getSizeFollowsDensity()` / `setSizeFollowsDensity(Boolean)`. Its JSON property `sizeFollowsDensity` is what Task 11's TypeScript model reads.
- Produces: `public static void VSDialogService.readSizeFollowsDensity(VSAssemblyInfo info, SizePositionPaneModel model, boolean governed)`, `public static void followDensitySize(VSAssemblyInfo info, SizePositionPaneModel model)`, `public static void recordAuthorSize(VSAssemblyInfo info, SizePositionPaneModel model, Dimension shown)`.

- [ ] **Step 1: Write the failing test**

Create `SizeFollowsDensityDialogTest.java` (license header as in Task 8):

```java
package inetsoft.web.viewsheet.service;

import inetsoft.sree.SreeEnv;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.web.composer.model.vs.SizePositionPaneModel;
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
class SizeFollowsDensityDialogTest {
   @BeforeEach
   void density() {
      SreeEnv.setProperty("viewsheet.density", "comfortable");
   }

   @AfterEach
   void reset() {
      SreeEnv.setProperty("viewsheet.density", null);
   }

   private static CurrentSelectionVSAssemblyInfo container(VizMark mark, Dimension size,
                                                           boolean userSize)
   {
      CurrentSelectionVSAssemblyInfo info = new CurrentSelectionVSAssemblyInfo();
      info.setVizMark(mark);
      info.setPixelSize(size);
      info.setUserSize(userSize);
      return info;
   }

   private static SizePositionPaneModel model(Boolean follows, int width, int height) {
      SizePositionPaneModel model = new SizePositionPaneModel();
      model.setSizeFollowsDensity(follows);
      model.setWidth(width);
      model.setHeight(height);
      return model;
   }

   @Test
   void readOffersTheCheckboxForAGovernedMarkedBox() {
      SizePositionPaneModel following = new SizePositionPaneModel();
      VSDialogService.readSizeFollowsDensity(
         container(VizMark.MODERN_LIGHT, new Dimension(300, 360), false), following, true);
      assertEquals(Boolean.TRUE, following.getSizeFollowsDensity());

      SizePositionPaneModel authored = new SizePositionPaneModel();
      VSDialogService.readSizeFollowsDensity(
         container(VizMark.MODERN_LIGHT, new Dimension(300, 240), true), authored, true);
      assertEquals(Boolean.FALSE, authored.getSizeFollowsDensity());
   }

   @Test
   void readOffersNoCheckboxWhenNotGovernedUnmarkedOrWithoutADensitySize() {
      SizePositionPaneModel notGoverned = new SizePositionPaneModel();
      VSDialogService.readSizeFollowsDensity(
         container(VizMark.MODERN_LIGHT, new Dimension(300, 360), false), notGoverned, false);
      assertNull(notGoverned.getSizeFollowsDensity());

      SizePositionPaneModel unmarked = new SizePositionPaneModel();
      VSDialogService.readSizeFollowsDensity(
         container(null, new Dimension(300, 240), false), unmarked, true);
      assertNull(unmarked.getSizeFollowsDensity());

      ChartVSAssemblyInfo chart = new ChartVSAssemblyInfo();
      chart.setVizMark(VizMark.MODERN_LIGHT);
      SizePositionPaneModel noDensitySize = new SizePositionPaneModel();
      VSDialogService.readSizeFollowsDensity(chart, noDensitySize, true);
      assertNull(noDensitySize.getSizeFollowsDensity());
   }

   // the container dialog then hands this model to setContainerSize, which re-widens its children
   @Test
   void followingWritesTheTierSizeIntoTheBoxAndTheModel() {
      CurrentSelectionVSAssemblyInfo info = container(VizMark.MODERN_LIGHT, new Dimension(300, 500), true);
      SizePositionPaneModel model = model(true, 300, 500);

      VSDialogService.followDensitySize(info, model);

      assertEquals(new Dimension(300, 360), info.getPixelSize());
      assertFalse(info.isUserSize());
      assertEquals(300, model.getWidth());
      assertEquals(360, model.getHeight());
   }

   @Test
   void notFollowingSetsTheFlagEvenForAnUnchangedSize() {
      CurrentSelectionVSAssemblyInfo info = container(VizMark.MODERN_LIGHT, new Dimension(300, 360), false);

      VSDialogService.recordAuthorSize(info, model(false, 300, 360), new Dimension(300, 360));

      assertTrue(info.isUserSize());
   }

   @Test
   void noAnswerSetsTheFlagOnlyOnAChange() {
      CurrentSelectionVSAssemblyInfo unchanged = container(VizMark.MODERN_LIGHT, new Dimension(300, 360), false);
      VSDialogService.recordAuthorSize(unchanged, model(null, 300, 360), new Dimension(300, 360));
      assertFalse(unchanged.isUserSize(), "applying the dialog for a title change leaves the box following");

      CurrentSelectionVSAssemblyInfo changed = container(VizMark.MODERN_LIGHT, new Dimension(300, 360), false);
      VSDialogService.recordAuthorSize(changed, model(null, 300, 240), new Dimension(300, 360));
      assertTrue(changed.isUserSize());
   }

   @Test
   void theHelpersLeaveATypeWithoutADensitySizeAlone() {
      ChartVSAssemblyInfo chart = new ChartVSAssemblyInfo();
      chart.setVizMark(VizMark.MODERN_LIGHT);
      chart.setPixelSize(new Dimension(400, 300));

      VSDialogService.followDensitySize(chart, model(true, 400, 300));
      VSDialogService.recordAuthorSize(chart, model(false, 500, 300), new Dimension(400, 300));

      assertEquals(new Dimension(400, 300), chart.getPixelSize());
      assertFalse(chart.isUserSize());
   }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./mvnw test -pl core -Dtest=SizeFollowsDensityDialogTest`
Expected: compilation error, `cannot find symbol: method setSizeFollowsDensity(…)`.

- [ ] **Step 3: Add the model field**

In `SizePositionPaneModel.java`, beside the cell-height accessors:

```java
   public Boolean getSizeFollowsDensity() {
      return sizeFollowsDensity;
   }

   public void setSizeFollowsDensity(Boolean sizeFollowsDensity) {
      this.sizeFollowsDensity = sizeFollowsDensity;
   }
```

and beside `cellHeightFollowsDensity`:

```java
   private Boolean sizeFollowsDensity;
```

- [ ] **Step 4: Add the helpers**

In `VSDialogService.java`, after `setContainerSize(...)`:

```java
   /**
    * Offer Size & Position's follow-the-default-density checkbox for size where a density size rule
    * governs the box; null, which hides it, everywhere else.
    * @param governed whether this box is one the size rule writes: not a selection container's child,
    *                 and for a list or tree, shown as a list.
    */
   public static void readSizeFollowsDensity(VSAssemblyInfo info, SizePositionPaneModel model,
                                             boolean governed)
   {
      model.setSizeFollowsDensity(
         governed && info.takesDensitySize() && info.getVizMark() != null ?
            !info.isUserSize() : null);
   }

   /**
    * When the author ticked follow-the-default-density, return the box to its density size and put
    * that size in the model, so the caller's size write - which re-widens a container's children -
    * applies it too.
    */
   public static void followDensitySize(VSAssemblyInfo info, SizePositionPaneModel model) {
      if(!Boolean.TRUE.equals(model.getSizeFollowsDensity()) || !info.takesDensitySize()) {
         return;
      }

      info.resetSize(VizContext.of(info));
      Dimension size = info.getPixelSize();
      model.setWidth(size.width);
      model.setHeight(size.height);
   }

   /**
    * Record an author's size: always when they unticked the checkbox, and, from a client that sent
    * no answer, only when the submitted size differs from what the dialog showed.
    * @param shown the size the dialog opened with.
    */
   public static void recordAuthorSize(VSAssemblyInfo info, SizePositionPaneModel model,
                                       Dimension shown)
   {
      Boolean follows = model.getSizeFollowsDensity();

      if(!info.takesDensitySize() || Boolean.TRUE.equals(follows)) {
         return;
      }

      if(Boolean.FALSE.equals(follows) ||
         model.getWidth() != shown.width || model.getHeight() != shown.height)
      {
         info.setUserSize(true);
      }
   }
```

Add imports only if the file's existing wildcards don't already cover `inetsoft.uql.viewsheet.internal.VizContext` and `java.awt.Dimension`.

- [ ] **Step 5: Run it to verify it passes**

Run: `./mvnw test -pl core -Dtest=SizeFollowsDensityDialogTest`
Expected: 6 tests, PASS.

- [ ] **Step 6: Wire the three dialogs**

`SelectionListPropertyDialogService.java`, read: right after the `sizePositionPaneModel.setCellHeightFollowsDensity(...)` statement:

```java
      VSDialogService.readSizeFollowsDensity(selectionListAssemblyInfo, sizePositionPaneModel,
         !inSelectionContainer &&
            selectionListAssemblyInfo.getShowTypeValue() == SelectionVSAssemblyInfo.LIST_SHOW_TYPE);
```

Write: replace `dialogService.setAssemblySize(selectionListAssemblyInfo, sizePositionPaneModel);` with:

```java
      Dimension shownSize =
         new Dimension(dialogService.getAssemblySize(selectionListAssemblyInfo, rvs.getViewsheet()));
      VSDialogService.followDensitySize(selectionListAssemblyInfo, sizePositionPaneModel);
      dialogService.setAssemblySize(selectionListAssemblyInfo, sizePositionPaneModel);
      VSDialogService.recordAuthorSize(selectionListAssemblyInfo, sizePositionPaneModel, shownSize);
```

`SelectionTreePropertyDialogService.java`, read: right after the `sizePositionPaneModel.setCellHeightFollowsDensity(...)` statement:

```java
      VSDialogService.readSizeFollowsDensity(selectionTreeAssemblyInfo, sizePositionPaneModel,
         !(selectionTreeAssembly.getContainer() instanceof CurrentSelectionVSAssembly) &&
            selectionTreeAssemblyInfo.getShowTypeValue() == SelectionVSAssemblyInfo.LIST_SHOW_TYPE);
```

Write: replace `dialogService.setAssemblySize(streeInfo, sizePositionPaneModel);` with:

```java
      Dimension shownSize =
         new Dimension(dialogService.getAssemblySize(streeInfo, viewsheet.getViewsheet()));
      VSDialogService.followDensitySize(streeInfo, sizePositionPaneModel);
      dialogService.setAssemblySize(streeInfo, sizePositionPaneModel);
      VSDialogService.recordAuthorSize(streeInfo, sizePositionPaneModel, shownSize);
```

(In that method the `RuntimeViewsheet` variable is named `viewsheet`. Check that, and use the method's real name if it differs.)

`SelectionContainerPropertyDialogService.java`, read: right after `sizePositionPaneModel.setContainer(...)`:

```java
      VSDialogService.readSizeFollowsDensity(selectionContainerAssemblyInfo, sizePositionPaneModel, true);
```

Write: replace the `dialogService.setContainerSize(...)` call and its comment line with:

```java
      Dimension shownSize = new Dimension(dialogService.getAssemblySize(selectionContainerAssemblyInfo, vs));
      VSDialogService.followDensitySize(selectionContainerAssemblyInfo, sizePositionPaneModel);
      //When resizing selection container, also resize selection container children
      dialogService.setContainerSize(selectionContainerAssemblyInfo, sizePositionPaneModel,
                                     selectionContainerAssembly.getAssemblies(), vs);
      VSDialogService.recordAuthorSize(selectionContainerAssemblyInfo, sizePositionPaneModel, shownSize);
```

`setContainerPosition` runs earlier in that method and stays where it is. `shownSize` must be captured before `followDensitySize` in all three.

- [ ] **Step 7: Run the dialog suites**

Run: `./mvnw test -pl core -Dtest='SizeFollowsDensityDialogTest,TitleHeightFollowDensityTest,SelectionPaddingDialogTest,SelectionTreeShowTypeSizeTest,RangeSliderTitleHeightDialogTest'`
Expected: PASS, with no change to the existing classes.

- [ ] **Step 8: Commit**

```bash
git -C E:/StyleBI/stylebi-enterprise/community branch --show-current
git -C E:/StyleBI/stylebi-enterprise/community add core/src/main/java/inetsoft/web/composer/model/vs/SizePositionPaneModel.java core/src/main/java/inetsoft/web/viewsheet/service/VSDialogService.java core/src/main/java/inetsoft/web/composer/vs/dialog/SelectionListPropertyDialogService.java core/src/main/java/inetsoft/web/composer/vs/dialog/SelectionTreePropertyDialogService.java core/src/main/java/inetsoft/web/composer/vs/dialog/SelectionContainerPropertyDialogService.java core/src/test/java/inetsoft/web/viewsheet/service/SizeFollowsDensityDialogTest.java
git -C E:/StyleBI/stylebi-enterprise/community commit -m "Offer follow-the-default-density for a selection's size" -m "Co-Authored-By: <your model> <noreply@anthropic.com>"
```

---

### Task 11: The size checkbox in the browser

**Files:**
- Modify: `web/projects/portal/src/app/vsobjects/model/size-position-pane-model.ts`
- Modify: `web/projects/portal/src/app/vsobjects/dialog/size-position-pane.component.ts`
- Modify: `web/projects/portal/src/app/vsobjects/dialog/size-position-pane.component.html`
- Test: `web/projects/portal/src/app/vsobjects/dialog/size-position-pane.spec.ts` (extend)

**Interfaces:**
- Consumes: the JSON property `sizeFollowsDensity` (Task 10): null or absent means no checkbox, true means following, false means the author's size.

- [ ] **Step 1: Write the failing tests**

Add inside the existing `describe` of `size-position-pane.spec.ts`:

```ts
   it("should not render the size checkbox when the model does not offer one", () => {
      fixture.componentInstance.model = createModel();
      fixture.detectChanges();
      expect(fixture.debugElement.query(By.css("#sizeFollowsDensity"))).toBeNull();
   });

   it("should disable width and height while the size follows the density", () => {
      fixture.componentInstance.model = createModel({sizeFollowsDensity: true});
      fixture.detectChanges();
      expect(fixture.debugElement.query(By.css("#sizeFollowsDensity"))).not.toBeNull();
      expect(fixture.componentInstance.form.controls["width"].disabled).toBeTruthy();
      expect(fixture.componentInstance.form.controls["height"].disabled).toBeTruthy();
   });

   it("should re-enable width and height when the size checkbox is cleared", () => {
      fixture.componentInstance.model = createModel({sizeFollowsDensity: true});
      fixture.detectChanges();

      fixture.componentInstance.sizeFollowChanged(false);
      fixture.detectChanges();

      expect(fixture.componentInstance.model.sizeFollowsDensity).toBe(false);
      expect(fixture.componentInstance.form.controls["width"].disabled).toBeFalsy();
      expect(fixture.componentInstance.form.controls["height"].disabled).toBeFalsy();
   });

   it("should keep width and height disabled in a container when the size checkbox is cleared", () => {
      fixture.componentInstance.model = createModel({container: true, sizeFollowsDensity: true});
      fixture.detectChanges();

      fixture.componentInstance.sizeFollowChanged(false);

      expect(fixture.componentInstance.form.controls["width"].disabled).toBeTruthy();
      expect(fixture.componentInstance.form.controls["height"].disabled).toBeTruthy();
   });
```

- [ ] **Step 2: Run them to verify they fail**

Run (from `web/`): `npx ng test portal --include="**/size-position-pane.spec.ts"`
Expected: FAIL. The model has no `sizeFollowsDensity` (a TypeScript error) and the component has no `sizeFollowChanged`.

- [ ] **Step 3: Implement**

`size-position-pane-model.ts`, after `cellHeightFollowsDensity?: boolean;`:

```ts
   sizeFollowsDensity?: boolean;
```

`size-position-pane.component.ts`:
- add the field `showSizeFollow: boolean;` beside `showCellHeightFollow`;
- in `ngOnInit`, beside the other two: `this.showSizeFollow = this.model.sizeFollowsDensity != null;`
- in `initForm`, the `width` and `height` controls' `disabled` becomes
  `(!this.layoutEnabled || this.model.locked || this.model.sizeFollowsDensity === true)`;
- add, beside `cellHeightFollowChanged`:

```ts
   sizeFollowChanged(follows: boolean): void {
      this.model.sizeFollowsDensity = follows;
      const enabled = !follows && this.layoutEnabled && !this.model.locked;
      this.setEnabled("width", enabled);
      this.setEnabled("height", enabled);
   }
```

`size-position-pane.component.html`:
- on the Width and Height `number-stepper`s, `[disabled]` becomes
  `"!layoutEnabled || model.locked || model.sizeFollowsDensity === true"`;
- right after the `</div>` that closes the Top/Left/Width/Height row, before `@if (showTitleHeight || showCellHeight) {`:

```html
    @if (showSizeFollow) {
      <div class="form-check mt-1">
        <input type="checkbox" class="form-check-input" id="sizeFollowsDensity"
               [ngModel]="model.sizeFollowsDensity"
               (ngModelChange)="sizeFollowChanged($event)"
               [ngModelOptions]="{standalone: true}">
        <label class="form-check-label" for="sizeFollowsDensity">
          _#(composer.vs.followDefaultDensity)
        </label>
      </div>
    }
```

No comments in the `.html`.

- [ ] **Step 4: Run them to verify they pass**

Run (from `web/`): `npx ng test portal --include="**/size-position-pane.spec.ts"`
Expected: PASS, including the file's existing tests.

- [ ] **Step 5: Commit**

```bash
git -C E:/StyleBI/stylebi-enterprise/community branch --show-current
git -C E:/StyleBI/stylebi-enterprise/community add web/projects/portal/src/app/vsobjects/model/size-position-pane-model.ts web/projects/portal/src/app/vsobjects/dialog/size-position-pane.component.ts web/projects/portal/src/app/vsobjects/dialog/size-position-pane.component.html web/projects/portal/src/app/vsobjects/dialog/size-position-pane.spec.ts
git -C E:/StyleBI/stylebi-enterprise/community commit -m "Show a follow-the-default-density checkbox for a selection's size" -m "Co-Authored-By: <your model> <noreply@anthropic.com>"
```

Do not stage `web/angular.json` or `web/package-lock.json`.
