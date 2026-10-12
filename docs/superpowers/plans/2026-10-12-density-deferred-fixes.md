# Density Deferred Fixes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix four confirmed density defects: a marked selection list's stored row count, an author cell padding's row drift across tiers, a marked container's hidden title in export, and the strip and focus outline on a shrunk marked table in bottom tabs.

**Architecture:** Each fix routes its rule through one method so every caller agrees: `SelectionBaseVSAssemblyInfo.getFittingListHeight` (rows from a box), `VSDensityDefaults.excludedCellPaddingHeight` (the padding a stored row leaves out), `CurrentSelectionVSAssemblyInfo.getTitleLaneHeight` (the lane a container's children sit under), and a client-only `BaseTableModel.cardTopShift` published by the table and read by `vs-object-container`. Every change is gated on the assembly's own mark; unmarked behaviour is byte-identical.

**Tech Stack:** Java 21, JUnit 5, Mockito, Spring test context (`@SreeHome`), Maven (`./mvnw`, module `core`); Angular 21 with Vitest for two specs.

**Spec:** `docs/superpowers/specs/lookfeel/2026-10-12-density-deferred-fixes-design.md`

## Global Constraints

- **Branch and worktree:** `bug-density-deferred-fixes`, in the worktree at `C:/Users/Franky/AppData/Local/Temp/claude/E--StyleBI-stylebi-enterprise/7f50f5d9-1f05-4321-930d-4acc337010a1/scratchpad/density-fixes` (call it `$WT`), cut from #6390's head `c66a5aca51`. Never work in the shared checkout `E:/StyleBI/stylebi-enterprise/community`; other sessions use it.
- **Git:** run each git command as its own call with `git -C "$WT" ...`. Never chain git commands with `&&`; a hook blocks it. Run `git -C "$WT" branch --show-current` before every commit and expect `bug-density-deferred-fixes`. Never push; the user pushes.
- **Commit messages:** plain prose subject, no blockquotes, ending with the line `Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>`.
- **Numbers (comfortable / compact / dense):** title lane 30 / 26 / 20; selection cell 28 / 24 / 20; selection card inset 16 / 12 / 8 per edge; seeded list box 132×202 / 124×170 / 116×136, which holds 5 rows; legacy list box 100×120, title 20, rows 20. Table vertical cell padding 12 / 8 / 6 (comfortable 6/8/6/8, compact 4/6/4/6, dense 3/4/3/4); data rows 28 / 24 / 20, header rows 30 / 26 / 22. Container out-selection rows 30 / 26 / 20.
- **Gates:** server code keys on the assembly's own mark (`getVizMark() != null`, or `VizContext.of(info).modern`); browser code on `model.vizModern`. Never the org gate.
- **Comments:** a short clause each. Never mention the spec, this plan, decisions (D1…), PR numbers or tickets in source code. No comments in `.html` templates.
- **New files** start with the AGPL header used by `TableViewPropertyDialogServiceTest.java` (`Copyright (C) 2026  InetSoft Technology`).
- **Unchanged:** the composer canvas's container drawing, Excel and print-layout container output, every unmarked assembly, the legacy floating strip and legacy focus outline, `TableDataVSAssemblyInfo`'s `!isUserCellPadding()` seed guard.

## Review Focus

1. **A marked dropdown stretched by Arrange → Resize Max Height** keeps the author's Height rows. Pinned in Task 2 (`aStretchedMarkedDropdownKeepsTheAuthorsRows`).
2. **An untouched list switched to Dropdown with Height 10 in one OK**, while Size & Position's density box arrives ticked, keeps 10. Pinned in Task 2 (`aListMadeADropdownKeepsItsHeightWhileTheSizeBoxIsTicked`).
3. **A table with an author padding under a stylesheet padding on every column**, viewed at another tier, stays on the tier rather than below it. Pinned in Task 3 (`anAuthorPaddingUnderFullCoverageFloorsAtTheTierInForce`).
4. **A hidden-title marked container whose first child is a range slider** still exports that slider under match layout (gap 0). Pinned in Task 5 (`aHiddenTitleMarkedContainersFirstSliderIsExportedAtItsTop`).
5. **A shrunk table whose rows grow to fill its box** puts its strip back on `objectFormat.top`. Pinned in Task 7 (`publishes zero once its rows fill the card`).

## How to run tests

Java, from `$WT`. `-q` hides the summary on success; read `core/target/surefire-reports/*.txt` for counts:

```bash
cd "$WT"
./mvnw -q test -pl core -Dtest=ClassName -Dsurefire.failIfNoSpecifiedTests=false
```

Several classes: `"-Dtest=A,B,C"`. Do not add `install`; the shared local repository must not receive this branch's jars.

Frontend: Vitest cannot run from a worktree whose `node_modules` is a junction. Task 7 runs `npm ci` in `$WT/web` once, then:

```bash
cd "$WT/web"
npx ng test portal --include="**/vs-object-container.component.spec.ts"
```

---

### Task 1: A list's rows are what its box holds

Spec D1, §3. The helper and the seed; no caller outside the info class changes yet.

**Files:**
- Modify: `core/src/main/java/inetsoft/uql/viewsheet/internal/SelectionBaseVSAssemblyInfo.java` (after `getContainedListHeight()` ~line 163; the size rule in `seedChromeDefaults` ~lines 1008-1013)
- Test: `core/src/test/java/inetsoft/uql/viewsheet/internal/ContainedListHeightTest.java`
- Test: `core/src/test/java/inetsoft/uql/viewsheet/internal/SelectionDensitySizeTest.java`

**Interfaces:**
- Produces: `public int getFittingListHeight(int height)` and `public void fitListHeightToBox()` on `SelectionBaseVSAssemblyInfo` (Task 2 calls both).

- [ ] **Step 1: Write the failing tests**

Append to `ContainedListHeightTest` (its existing helper is `private SelectionListVSAssemblyInfo list(String density, VizMark mark)`, which sets `listHeight` 6):

```java
   // the rows a contained height holds are the rows it was built from
   @Test
   void theFittingRowsInvertTheContainedHeight() {
      for(String density : new String[] { "comfortable", "compact", "dense" }) {
         SelectionListVSAssemblyInfo info = list(density, VizMark.MODERN_LIGHT);
         assertEquals(6, info.getFittingListHeight(info.getContainedListHeight()), density);
      }

      assertEquals(6, list("comfortable", null).getFittingListHeight(140));
   }

   @Test
   void aMarkedListFitsWholeRowsAndAtLeastOne() {
      SelectionListVSAssemblyInfo info = list("comfortable", VizMark.MODERN_LIGHT);

      assertEquals(6, info.getFittingListHeight(250), "188 / 28 drops the partial row");
      assertEquals(1, info.getFittingListHeight(40), "below the lane and inset still keeps one");
   }

   // what the composer has always stored for an unmarked list: no floor
   @Test
   void anUnmarkedListFitsDefhRowsExactly() {
      assertEquals(0, list("comfortable", null).getFittingListHeight(35));
   }
```

Append to `SelectionDensitySizeTest`. Add the imports `org.junit.jupiter.params.ParameterizedTest` and `org.junit.jupiter.params.provider.CsvSource` (the class already imports `inetsoft.sree.SreeEnv`, `java.awt.Dimension` and the JUnit api wildcard):

```java
   // the tier box holds five rows; storing six made a Dropdown to List rebuild 230 / 194 / 156
   @ParameterizedTest
   @CsvSource({ "comfortable, 202", "compact, 170", "dense, 136" })
   void aFreshMarkedListStoresTheRowsItsBoxHolds(String density, int height) {
      SelectionListVSAssemblyInfo info = created(density, VizMark.MODERN_LIGHT);

      assertEquals(5, info.getListHeight());
      assertEquals(height, info.getContainedListHeight(), "the rows rebuild the box they came from");
   }

   @Test
   void aFreshMarkedTreeStoresTheRowsItsBoxHolds() {
      SreeEnv.setProperty("viewsheet.density", "comfortable");
      SelectionTreeVSAssemblyInfo info = new SelectionTreeVSAssemblyInfo();
      info.setVizMark(VizMark.MODERN_LIGHT);
      info.initDefaultFormat();

      assertEquals(5, info.getListHeight());
   }

   @Test
   void aFreshUnmarkedListKeepsSixRows() {
      SelectionListVSAssemblyInfo info = created("comfortable", null);

      assertEquals(new Dimension(100, 120), info.getPixelSize());
      assertEquals(6, info.getListHeight(), "legacy creation is unchanged");
   }

   @Test
   void aDensityChangeCarriesTheRowsWithTheBox() {
      SelectionListVSAssemblyInfo info = created("comfortable", VizMark.MODERN_LIGHT);
      info.setListHeight(6);

      SreeEnv.setProperty("viewsheet.density", "dense");
      info.seedChromeDefaults(VizContext.of(info));

      assertEquals(new Dimension(116, 136), info.getPixelSize());
      assertEquals(5, info.getListHeight());
   }

   // a density change moves pixels, not rows: an author's box keeps its size and its row count
   @Test
   void aDensityChangeLeavesAnAuthorSizedListsRows() {
      SelectionListVSAssemblyInfo info = created("comfortable", VizMark.MODERN_LIGHT);
      info.setPixelSize(new Dimension(132, 230));
      info.setUserSize(true);
      info.setListHeight(6);

      SreeEnv.setProperty("viewsheet.density", "dense");
      info.seedChromeDefaults(VizContext.of(info));

      assertEquals(new Dimension(132, 230), info.getPixelSize());
      assertEquals(6, info.getListHeight());
   }

   // a dropdown's rows are the author's, even when its box still carries a seeded size
   @Test
   void theSeedLeavesADropdownsRowsAlone() {
      SreeEnv.setProperty("viewsheet.density", "comfortable");
      SelectionListVSAssemblyInfo info = new SelectionListVSAssemblyInfo();
      info.setVizMark(VizMark.MODERN_LIGHT);
      info.setShowTypeValue(SelectionVSAssemblyInfo.DROPDOWN_SHOW_TYPE);
      info.setListHeight(10);

      info.seedChromeDefaults(VizContext.of(info));

      assertEquals(10, info.getListHeight());
   }

   @Test
   void modernizeFitsALegacyListsRowsToItsTierBox() {
      SelectionListVSAssemblyInfo info = created("comfortable", null);

      info.setVizMark(VizMark.MODERN_LIGHT);
      info.seedChromeDefaults(VizContext.ofTransition(null, VizMark.MODERN_LIGHT));

      assertEquals(new Dimension(132, 202), info.getPixelSize());
      assertEquals(5, info.getListHeight());
   }

   // the legacy 100x120 holds five 20px rows under its 20px lane, so Revert stores five
   @Test
   void revertFitsTheRowsToTheLegacyBox() {
      SelectionListVSAssemblyInfo info = created("comfortable", VizMark.MODERN_LIGHT);
      info.setListHeight(6);

      info.setVizMark(null);
      info.seedChromeDefaults(VizContext.ofTransition(null, null));

      assertEquals(new Dimension(100, 120), info.getPixelSize());
      assertEquals(5, info.getListHeight());
      assertEquals(120, info.getContainedListHeight());
   }

   @Test
   void openingAnUnmarkedListLeavesItsRowsAlone() {
      SelectionListVSAssemblyInfo info = created("comfortable", null);

      VizModernizeUtil.reseedAfterRestore(info);

      assertEquals(6, info.getListHeight());
   }

   // an author cell height takes its rows from the same box: (202 - 30 - 32) / 40
   @Test
   void aSeededBoxWithAnAuthorCellHeightStoresTheRowsThatFit() {
      SreeEnv.setProperty("viewsheet.density", "comfortable");
      SelectionListVSAssemblyInfo info = new SelectionListVSAssemblyInfo();
      info.setVizMark(VizMark.MODERN_LIGHT);
      info.setCellHeight(40);
      info.setUserCellHeight(true);

      info.seedChromeDefaults(VizContext.of(info));

      assertEquals(3, info.getListHeight());
   }

   private SelectionListVSAssemblyInfo created(String density, VizMark mark) {
      SreeEnv.setProperty("viewsheet.density", density);
      SelectionListVSAssemblyInfo info = new SelectionListVSAssemblyInfo();
      info.setVizMark(mark);
      info.initDefaultFormat();
      return info;
   }
```

- [ ] **Step 2: Run them and watch them fail**

Run: `./mvnw -q test -pl core "-Dtest=ContainedListHeightTest,SelectionDensitySizeTest" -Dsurefire.failIfNoSpecifiedTests=false`
Expected: compilation fails on `getFittingListHeight`. (After Step 3's helper alone, the seed tests fail with 6 where 5 is expected.)

- [ ] **Step 3: Add the helper and the applier**

In `SelectionBaseVSAssemblyInfo`, after `getContainedListHeight()`:

```java
   /**
    * The rows a box of this height holds, the inverse of getContainedListHeight. Marked, whole
    * rows at its own density inside its card inset and never fewer than one; unmarked keeps the
    * legacy defh rows exactly.
    */
   public int getFittingListHeight(int height) {
      if(getVizMark() == null) {
         return (height - getTitleHeight()) / AssetUtil.defh;
      }

      Insets inset = getPadding();
      int body = height - getTitleHeight() - (inset == null ? 0 : inset.top + inset.bottom);
      return Math.max(1, body / Math.max(1, getEffectiveCellHeight()));
   }

   /**
    * Store the rows this list's box holds, so a switch back from a dropdown or a container expand
    * rebuilds the same box. A dropdown's box is its title lane and its rows are the author's.
    */
   public void fitListHeightToBox() {
      Dimension size = getPixelSize();

      if(size != null && getShowTypeValue() != DROPDOWN_SHOW_TYPE) {
         setListHeight(getFittingListHeight(size.height));
      }
   }
```

- [ ] **Step 4: Write the rows with the seeded box**

In `seedChromeDefaults`, replace:

```java
      // An author's size carries userSize and this rule leaves it alone
      if((ctx.modern || ctx.transition) && followsDensitySize()) {
         setPixelSize(VSDensityDefaults.selectionSize(ctx));
      }
```

with:

```java
      // An author's size carries userSize and this rule leaves it alone. The rows go with the box,
      // since a dropdown and a container expand rebuild the box from them
      if((ctx.modern || ctx.transition) && followsDensitySize()) {
         setPixelSize(VSDensityDefaults.selectionSize(ctx));
         fitListHeightToBox();
      }
```

- [ ] **Step 5: Run the tests and the neighbours**

Run: `./mvnw -q test -pl core "-Dtest=ContainedListHeightTest,SelectionDensitySizeTest,GroupingServiceTest,VSSelectionContainerServiceTest,AbstractLayoutTest,SelectionPaddingDialogTest,SelectionTreeShowTypeSizeTest" -Dsurefire.failIfNoSpecifiedTests=false`
Expected: all pass. The neighbours set `listHeight` explicitly after creation, so the new seed does not reach them; if one fails, read it before touching it.

- [ ] **Step 6: Commit**

```bash
git -C "$WT" branch --show-current
git -C "$WT" add core/src/main/java/inetsoft/uql/viewsheet/internal/SelectionBaseVSAssemblyInfo.java core/src/test/java/inetsoft/uql/viewsheet/internal/ContainedListHeightTest.java core/src/test/java/inetsoft/uql/viewsheet/internal/SelectionDensitySizeTest.java
git -C "$WT" commit -m "Store the rows a seeded selection box holds with the box" -m "Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

### Task 2: A resize and the size tick write the rows the box holds

Spec D1, §3.

**Files:**
- Modify: `core/src/main/java/inetsoft/web/composer/vs/objects/controller/ComposerObjectService.java:191-200`
- Modify: `core/src/main/java/inetsoft/web/composer/vs/dialog/SelectionListPropertyDialogService.java` (before `setAssemblyInfoTables(...)` ~line 383)
- Modify: `core/src/main/java/inetsoft/web/composer/vs/dialog/SelectionTreePropertyDialogService.java` (before `setAssemblyInfoTables(...)` ~line 424)
- Test: `core/src/test/java/inetsoft/web/composer/vs/objects/controller/ComposerObjectServiceTest.java`
- Test: `core/src/test/java/inetsoft/web/composer/vs/dialog/SelectionPaddingDialogTest.java`
- Test: `core/src/test/java/inetsoft/web/composer/vs/dialog/SelectionTreeShowTypeSizeTest.java`

**Interfaces:**
- Consumes: `getFittingListHeight(int)`, `fitListHeightToBox()` from Task 1.

- [ ] **Step 1: Write the failing composer tests**

In `ComposerObjectServiceTest`, add imports `inetsoft.uql.viewsheet.internal.SelectionListVSAssemblyInfo`, `inetsoft.uql.viewsheet.internal.SelectionVSAssemblyInfo`, `inetsoft.uql.viewsheet.internal.VizMark`, `inetsoft.web.composer.vs.objects.event.ResizeVSObjectTitleEvent`, `org.junit.jupiter.params.ParameterizedTest`, `org.junit.jupiter.params.provider.CsvSource`. Insert the tests immediately above the existing `private void resize(Viewsheet vs, String name, int width, int height)` helper:

```java
   // a 30/26/20 lane over 28/24/20 rows inside 16/12/8 of inset on each edge: six whole rows
   @ParameterizedTest
   @CsvSource({ "comfortable, 230", "compact, 194", "dense, 160" })
   void aDraggedMarkedListStoresTheRowsItsBoxHolds(String density, int height) throws Exception {
      Viewsheet vs = tierViewsheet(density);
      SelectionListVSAssembly list = markedList(vs);

      resize(vs, "SelectionList1", list.getPixelSize().width, height);

      assertEquals(6, list.getSelectionListInfo().getListHeight());
   }

   @Test
   void aDraggedMarkedListRebuildsTheBoxItWasDraggedTo() throws Exception {
      Viewsheet vs = tierViewsheet("comfortable");
      SelectionListVSAssembly list = markedList(vs);

      resize(vs, "SelectionList1", 132, 230);

      assertEquals(230, list.getSelectionListInfo().getContainedListHeight(),
                   "what a container expand and a switch back from Dropdown rebuild");
   }

   @Test
   void aMarkedListDraggedBelowOneRowKeepsOne() throws Exception {
      Viewsheet vs = tierViewsheet("comfortable");
      SelectionListVSAssembly list = markedList(vs);

      resize(vs, "SelectionList1", 132, 70);

      assertEquals(1, list.getSelectionListInfo().getListHeight(), "70 - 30 - 32 leaves 8px");
   }

   // Align and Distribute resend the height unchanged, and a width drag changes no row
   @Test
   void aMarkedListWhoseHeightIsUnchangedKeepsItsRows() throws Exception {
      Viewsheet vs = tierViewsheet("comfortable");
      SelectionListVSAssembly list = markedList(vs);
      list.getSelectionListInfo().setListHeight(9);

      resize(vs, "SelectionList1", 300, 202);

      assertEquals(9, list.getSelectionListInfo().getListHeight());
   }

   // a dropdown's box is its title lane and its rows are the author's Height; Arrange can still
   // stretch the box
   @Test
   void aStretchedMarkedDropdownKeepsTheAuthorsRows() throws Exception {
      Viewsheet vs = tierViewsheet("comfortable");
      SelectionListVSAssembly list = markedList(vs);
      SelectionListVSAssemblyInfo info = list.getSelectionListInfo();
      info.setShowTypeValue(SelectionVSAssemblyInfo.DROPDOWN_SHOW_TYPE);
      info.setListHeight(10);
      info.setPixelSize(new Dimension(132, 30));

      resize(vs, "SelectionList1", 132, 400);

      assertEquals(10, info.getListHeight());
   }

   // the lane grows by 10 and the box with it: 212 - 40 - 32 still holds five 28px rows
   @Test
   void aTitleLaneDragKeepsAMarkedListsRows() throws Exception {
      Viewsheet vs = tierViewsheet("comfortable");
      SelectionListVSAssembly list = markedList(vs);
      when(engine.getViewsheet(any(), any())).thenReturn(rvs);
      when(rvs.getViewsheet()).thenReturn(vs);
      ResizeVSObjectTitleEvent event = new ResizeVSObjectTitleEvent();
      event.setName("SelectionList1");
      event.setxOffset(0);
      event.setyOffset(0);
      event.setWidth(132);
      event.setHeight(212);
      event.setTitleHeight(40);

      service.resizeObjectTitle(runtimeViewsheetRef.getRuntimeId(), event, principal, dispatcher,
                                "/test");

      assertEquals(5, list.getSelectionListInfo().getListHeight());
   }

   // the list's own mark decides, not the dashboard's density: (230 - 20) / 20, written even for
   // an unchanged size, exactly as before
   @Test
   void anUnmarkedListKeepsTheLegacyRowCount() throws Exception {
      Viewsheet vs = tierViewsheet("comfortable");
      SelectionListVSAssembly list = unmarkedList(vs);
      list.getVSAssemblyInfo().setPixelSize(new Dimension(100, 230));

      resize(vs, "SelectionList1", 100, 230);

      assertEquals(10, list.getSelectionListInfo().getListHeight());
   }

   @Test
   void anUnmarkedListNoTallerThanItsTitleIsNotWritten() throws Exception {
      Viewsheet vs = tierViewsheet("comfortable");
      SelectionListVSAssembly list = unmarkedList(vs);

      resize(vs, "SelectionList1", 100, 20);

      assertEquals(6, list.getSelectionListInfo().getListHeight());
   }

   private static Viewsheet tierViewsheet(String density) {
      Viewsheet vs = new Viewsheet();
      vs.getVSAssemblyInfo().setName("vs1");
      vs.getViewsheetInfo().setVizDensity(density);
      return vs;
   }

   // the composer's creation order: the mark, then the default format, which runs the seed
   private static SelectionListVSAssembly markedList(Viewsheet vs) {
      SelectionListVSAssembly list = new SelectionListVSAssembly(vs, "SelectionList1");
      list.getSelectionListInfo().setVizMark(VizMark.MODERN_LIGHT);
      list.getSelectionListInfo().initDefaultFormat();
      list.getVSAssemblyInfo().setPixelOffset(new Point(0, 0));
      vs.addAssembly(list);
      return list;
   }

   // pinned, because construction takes the host's mark
   private static SelectionListVSAssembly unmarkedList(Viewsheet vs) {
      SelectionListVSAssembly list = new SelectionListVSAssembly(vs, "SelectionList1");
      list.getSelectionListInfo().setVizMark(null);
      list.getSelectionListInfo().initDefaultFormat();
      list.getVSAssemblyInfo().setPixelOffset(new Point(0, 0));
      vs.addAssembly(list);
      return list;
   }
```

- [ ] **Step 2: Write the failing dialog tests**

Append to `SelectionPaddingDialogTest` (helpers `seededList(String)`, `load(info)`, `write(info, model)` exist):

```java
   // dragged to 230 (six rows), then Follow the default density ticked: the box goes back to 202
   // and the rows go with it, or a Dropdown to List rebuilds 230
   @Test
   void followingTheDensitySizeFitsTheRowsToTheBox() throws Exception {
      SelectionListVSAssemblyInfo info = seededList("comfortable");
      info.setPixelSize(new Dimension(132, 230));
      info.setUserSize(true);
      info.setListHeight(6);

      SelectionListPropertyDialogModel model = load(info);
      model.getSelectionGeneralPaneModel().getSizePositionPaneModel().setSizeFollowsDensity(true);
      SelectionListVSAssemblyInfo saved = write(info, model);

      assertEquals(new Dimension(132, 202), saved.getPixelSize());
      assertEquals(5, saved.getListHeight());
   }

   // an untouched list arrives with the size box ticked; made a dropdown with a Height of 10 in
   // the same visit, the Height is the author's
   @Test
   void aListMadeADropdownKeepsItsHeightWhileTheSizeBoxIsTicked() throws Exception {
      SelectionListVSAssemblyInfo info = seededList("comfortable");

      SelectionListPropertyDialogModel model = load(info);
      SelectionGeneralPaneModel pane = model.getSelectionGeneralPaneModel();
      assertEquals(Boolean.TRUE, pane.getSizePositionPaneModel().getSizeFollowsDensity());
      pane.setShowType(SelectionVSAssemblyInfo.DROPDOWN_SHOW_TYPE);
      pane.setListHeight(10);
      SelectionListVSAssemblyInfo saved = write(info, model);

      assertEquals(10, saved.getListHeight());
   }
```

Append to `SelectionTreeShowTypeSizeTest` (helper `wire(info)` exists; the class resets `viewsheet.density` after each test):

```java
   // dragged to 342 (ten rows), then Follow the default density ticked: the box goes back to 202
   // and the rows go with it
   @Test
   void followingTheDensitySizeFitsATreesRowsToTheBox() throws Exception {
      SreeEnv.setProperty("viewsheet.density", "comfortable");
      SelectionTreeVSAssemblyInfo info = new SelectionTreeVSAssemblyInfo();
      info.setVizMark(VizMark.MODERN_LIGHT);
      VizModernizeUtil.reseedAfterRestore(info);
      info.setPixelSize(new Dimension(132, 342));
      info.setUserSize(true);
      info.setListHeight(10);
      wire(info);

      SelectionTreePropertyDialogModel model =
         service.getSelectionTreePropertyModel("Viewsheet1", "SelectionTree1", null);
      model.getSelectionGeneralPaneModel().getSizePositionPaneModel().setSizeFollowsDensity(true);
      model.getSelectionGeneralPaneModel().getGeneralPropPaneModel().getBasicGeneralPaneModel()
         .setName("SelectionTree1");
      service.setSelectionTreePropertyModel("Viewsheet1", "SelectionTree1", model, "", null,
                                            commandDispatcher);
      ArgumentCaptor<SelectionTreeVSAssemblyInfo> captor =
         ArgumentCaptor.forClass(SelectionTreeVSAssemblyInfo.class);
      verify(vsObjectPropertyService).editObjectProperty(
         any(RuntimeViewsheet.class), captor.capture(), any(), any(), any(),
         nullable(Principal.class), any(CommandDispatcher.class));
      SelectionTreeVSAssemblyInfo saved = captor.getValue();

      assertEquals(new Dimension(132, 202), saved.getPixelSize());
      assertEquals(5, saved.getListHeight());
   }
```

- [ ] **Step 3: Run them and watch them fail**

Run: `./mvnw -q test -pl core "-Dtest=ComposerObjectServiceTest,SelectionPaddingDialogTest,SelectionTreeShowTypeSizeTest" -Dsurefire.failIfNoSpecifiedTests=false`
Expected failures: tier drags 10 / 8 / 7 (want 6); rebuild 342 (want 230); below one row 2 (want 1); unchanged height 8 (want 9); stretched dropdown 18 (want 10); title-lane drag 8 (want 5); the list tick 6 (want 5); the tree tick 10 (want 5). The two unmarked tests and the dropdown-in-one-OK test pass.

- [ ] **Step 4: Refit the rows on a resize**

In `ComposerObjectService.resizeObject`, replace:

```java
            if(event.getHeight() > selectionListInfo.getTitleHeight()) {
               selectionListInfo.setListHeight((event.getHeight() -
                  selectionListInfo.getTitleHeight()) / defh);
            }
```

with:

```java
            if(selectionListInfo.getVizMark() != null) {
               // only a new height changes the rows; Align and a width drag resend it unchanged
               if(size.height != originalSize.height) {
                  selectionListInfo.fitListHeightToBox();
               }
            }
            else if(event.getHeight() > selectionListInfo.getTitleHeight()) {
               selectionListInfo.setListHeight((event.getHeight() -
                  selectionListInfo.getTitleHeight()) / defh);
            }
```

`size` and `originalSize` are already in scope, and `info.setPixelSize(size)` runs before this block. Keep the unmarked lines as they are. `resizeObjectTitle` reaches this method, so it needs no edit.

- [ ] **Step 5: Fit the rows when the size tick puts the box back**

In `SelectionListPropertyDialogService`, immediately before `setAssemblyInfoTables(selectionListAssemblyInfo, selectionListPaneModel, principal);` (it must run after the dialog's own `setListHeight` calls and `setShowTypeValue`):

```java
      // a box put back on its density size holds that size's rows; last, after the title, cell
      // height, inset and show type above
      if(Boolean.TRUE.equals(sizePositionPaneModel.getSizeFollowsDensity())) {
         selectionListAssemblyInfo.fitListHeightToBox();
      }
```

In `SelectionTreePropertyDialogService`, immediately before `setAssemblyInfoTables(streeInfo, selectionTreePaneModel, principal);`:

```java
      // a box put back on its density size holds that size's rows; last, after the title, cell
      // height, inset and show type above
      if(Boolean.TRUE.equals(sizePositionPaneModel.getSizeFollowsDensity())) {
         streeInfo.fitListHeightToBox();
      }
```

- [ ] **Step 6: Run the tests**

Run: `./mvnw -q test -pl core "-Dtest=ComposerObjectServiceTest,SelectionPaddingDialogTest,SelectionTreeShowTypeSizeTest,SelectionDensitySizeTest,ContainedListHeightTest" -Dsurefire.failIfNoSpecifiedTests=false`
Expected: all pass.

- [ ] **Step 7: Commit**

```bash
git -C "$WT" branch --show-current
git -C "$WT" add core/src/main/java/inetsoft/web/composer/vs/objects/controller/ComposerObjectService.java core/src/main/java/inetsoft/web/composer/vs/dialog/SelectionListPropertyDialogService.java core/src/main/java/inetsoft/web/composer/vs/dialog/SelectionTreePropertyDialogService.java core/src/test/java/inetsoft/web/composer/vs/objects/controller/ComposerObjectServiceTest.java core/src/test/java/inetsoft/web/composer/vs/dialog/SelectionPaddingDialogTest.java core/src/test/java/inetsoft/web/composer/vs/dialog/SelectionTreeShowTypeSizeTest.java
git -C "$WT" commit -m "Store a marked list's rows at its own density when its box is resized" -m "Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

### Task 3: A row under an author cell padding sits on the tier in force

Spec D2, §4. One rule for the stored side and the render floor.

**Files:**
- Modify: `core/src/main/java/inetsoft/uql/viewsheet/internal/VSDensityDefaults.java:120-139` (and the class javadoc ~lines 34-35, imports)
- Modify: `core/src/main/java/inetsoft/report/composition/VSTableLens.java:2135-2197` (`getRowPadding`, delete `getSeededInsetHeight`)
- Test: `core/src/test/java/inetsoft/uql/viewsheet/internal/DensityRowHeightInvariantTest.java`
- Test: `core/src/test/java/inetsoft/report/composition/TableCellPaddingCssCoverageTest.java`

**Interfaces:**
- Produces: `public static int excludedCellPaddingHeight(VizContext ctx, TableDataVSAssemblyInfo info)` and `public static int excludedCellPaddingHeight(TableDataVSAssemblyInfo info)` on `VSDensityDefaults`.

- [ ] **Step 1: Write the failing invariant tests**

In `DensityRowHeightInvariantTest`: add `import static org.junit.jupiter.api.Assertions.assertNull;`; change `renderedDataRow`'s first parameter from `TableVSAssemblyInfo` to `TableDataVSAssemblyInfo`; add one sentence to the class javadoc: "An author padding adds to this tier's content box, not to a box sized for the tier that seeded it." Then add:

```java
   @Test
   void anAuthorPaddingRendersTheTierInForceWhateverTierSeededIt() {
      // no reseed between seed and render: the org-density path. 10/6/10/6 adds 20 to each tier's
      // content box
      assertAuthorRendered("compact", "comfortable", 36, 38);
      assertAuthorRendered("comfortable", "dense", 34, 36);
      assertAuthorRendered("dense", "compact", 36, 38);
   }

   @Test
   void aSmallAuthorPaddingIsNotClippedByAShrinkingTier() {
      TableVSAssemblyInfo info = seededAt("comfortable");
      info.setCellPadding(new Insets(1, 1, 1, 1), CompositeValue.Type.USER);

      assertEquals(16, renderedDataRow(info, "dense"));
   }

   @Test
   void anAuthorPaddingSurvivesADashboardReseedOnTheTier() {
      // a dashboard density change re-runs the seed, which leaves the DEFAULT tier alone under an
      // author value - the row must not depend on it
      TableVSAssemblyInfo info = seededAt("compact");
      info.setCellPadding(new Insets(10, 6, 10, 6), CompositeValue.Type.USER);
      info.seedChromeDefaults(contextAt("comfortable"));

      assertEquals(new Insets(4, 6, 4, 6), info.getDefaultCellPadding(), "precondition: DEFAULT kept");
      assertEquals(36, renderedDataRow(info, "comfortable"));
      assertEquals(38, renderedHeaderRow(info, "comfortable"));
   }

   @Test
   void anAuthorPaddingSetBeforeModernizeRendersTheTier() {
      // set while unmarked, so Modernize's seed skips it and no DEFAULT tier is ever written
      TableVSAssemblyInfo info = new TableVSAssemblyInfo();
      info.seedChromeDefaults(VizContext.of((VizMark) null));
      info.setCellPadding(new Insets(4, 6, 4, 6), CompositeValue.Type.USER);
      info.seedChromeDefaults(contextAt("compact"));

      assertNull(info.getDefaultCellPadding(), "precondition: no DEFAULT tier was written");
      assertEquals(24, renderedDataRow(info, "compact"));
      assertEquals(26, renderedHeaderRow(info, "compact"));
      assertEquals(22, renderedDataRow(info, "dense"));
      assertEquals(24, renderedDataRow(info, "comfortable"));
   }

   @Test
   void anAuthorPaddingEqualToTheTierRendersTheTier() {
      TableVSAssemblyInfo info = seededAt("dense");
      info.setCellPadding(new Insets(6, 8, 6, 8), CompositeValue.Type.USER);

      assertEquals(28, renderedDataRow(info, "comfortable"));
      assertEquals(30, renderedHeaderRow(info, "comfortable"));
   }

   @Test
   void onlyTheVerticalSidesOfAnAuthorPaddingReachTheRow() {
      // 10 on top alone: comfortable's content box 16 plus 10
      TableVSAssemblyInfo info = seededAt("compact");
      info.setCellPadding(new Insets(10, 0, 0, 0), CompositeValue.Type.USER);

      assertEquals(26, renderedDataRow(info, "comfortable"));
   }

   @Test
   void crosstabAndCalcTableTakeTheSameRule() {
      TableDataVSAssemblyInfo[] infos = { new CrosstabVSAssemblyInfo(), new CalcTableVSAssemblyInfo() };

      for(TableDataVSAssemblyInfo info : infos) {
         String label = info.getClass().getSimpleName();
         info.seedChromeDefaults(contextAt("compact"));
         info.setCellPadding(new Insets(10, 6, 10, 6), CompositeValue.Type.USER);

         assertEquals(36, renderedDataRow(info, "comfortable"), label + " data row");
         assertEquals(38, renderedHeaderRow(info, "comfortable"), label + " header row");
      }
   }

   @Test
   void aTableTheDensityOwnsStillLeavesOutItsSeed() {
      // with no author value the cell draws the DEFAULT tier, so that is what the stored height
      // leaves out, whatever tier is in force
      TableVSAssemblyInfo info = seededAt("compact");
      VizContext ctx = contextAt("comfortable");

      assertEquals(8, VSDensityDefaults.excludedCellPaddingHeight(ctx, info));
      assertEquals(20, VSDensityDefaults.rowHeight(ctx, info));
      assertEquals(28, renderedDataRow(info, "comfortable"));
   }

   @Test
   void anUnmarkedContextLeavesNothingOut() {
      TableVSAssemblyInfo info = seededAt("compact");
      info.setCellPadding(new Insets(10, 6, 10, 6), CompositeValue.Type.USER);

      assertEquals(0, VSDensityDefaults.excludedCellPaddingHeight(VizContext.of((VizMark) null), info));
   }

   private void assertAuthorRendered(String seedMode, String renderMode, int data, int header) {
      TableVSAssemblyInfo info = seededAt(seedMode);
      info.setCellPadding(new Insets(10, 6, 10, 6), CompositeValue.Type.USER);
      String label = "author padding, " + seedMode + " seeded, " + renderMode + " rendered";

      assertEquals(data, renderedDataRow(info, renderMode), label + " data row");
      assertEquals(header, renderedHeaderRow(info, renderMode), label + " header row");
   }

   private int renderedHeaderRow(TableDataVSAssemblyInfo info, String renderMode) {
      VizContext ctx = contextAt(renderMode);
      VSTableLens lens = new VSTableLens(XTableUtil.getDefaultTableLens());
      return VSDensityDefaults.headerRowHeight(ctx, info) + lens.getRowPadding(0, info);
   }
```

- [ ] **Step 2: Run them and watch them fail**

Run: `./mvnw -q test -pl core -Dtest=DensityRowHeightInvariantTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: compilation fails on `excludedCellPaddingHeight`. Comment out the last two tests temporarily if you want to see the first seven fail at runtime: before the fix they give 40/42, 28/30, 38/40; 10; 40/42; 28/30/26/32; 34/36; 30.

- [ ] **Step 3: One rule in `VSDensityDefaults`**

Add `import java.util.function.Supplier;` after `import java.awt.Insets;`. In the class javadoc, replace "a table stores them less its seeded cell padding, which is added back at render (see rowHeight(VizContext, TableDataVSAssemblyInfo))" with "a table stores them less the cell padding render adds back (see excludedCellPaddingHeight)". Replace `rowHeight(VizContext, TableDataVSAssemblyInfo)`, `headerRowHeight(VizContext, TableDataVSAssemblyInfo)` and `defaultCellPaddingHeight` with:

```java
   /**
    * The data-row height a table stores: the rendered height less the cell padding render adds
    * back (see excludedCellPaddingHeight), so the sum lands on this tier.
    */
   public static int rowHeight(VizContext ctx, TableDataVSAssemblyInfo info) {
      return rowHeight(ctx) - excludedCellPaddingHeight(ctx, info);
   }

   /**
    * The header-row height a table stores for the context's mode, on the same terms as
    * rowHeight(VizContext, TableDataVSAssemblyInfo).
    */
   public static int headerRowHeight(VizContext ctx, TableDataVSAssemblyInfo info) {
      return headerRowHeight(ctx) - excludedCellPaddingHeight(ctx, info);
   }

   /**
    * The vertical cell padding a table's stored row height leaves out, for render to add back:
    * the DEFAULT tier while the density owns the padding, since that is what the cell draws;
    * under an author padding the DEFAULT tier stops following the density, so the tier in force.
    * Zero when not modern.
    */
   public static int excludedCellPaddingHeight(VizContext ctx, TableDataVSAssemblyInfo info) {
      return ctx.modern ? excludedHeight(info, () -> ctx) : 0;
   }

   /**
    * The same, for a table lens, which holds no context and asks once per cell. The mark stands in
    * for ctx.modern: Revert leaves a DEFAULT tier under an author padding that a reverted row never
    * took out. The context is built only for an author padding.
    */
   public static int excludedCellPaddingHeight(TableDataVSAssemblyInfo info) {
      return info != null && info.getVizMark() != null ?
         excludedHeight(info, () -> VizContext.of(info)) : 0;
   }

   private static int excludedHeight(TableDataVSAssemblyInfo info, Supplier<VizContext> ctx) {
      if(info == null) {
         return 0;
      }

      Insets padding = info.isUserCellPadding() ?
         cellPadding(ctx.get()) : info.getDefaultCellPadding();
      return padding == null ? 0 : padding.top + padding.bottom;
   }
```

- [ ] **Step 4: Run the invariant tests**

Run: `./mvnw -q test -pl core -Dtest=DensityRowHeightInvariantTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: all pass.

- [ ] **Step 5: Write the floor tests**

Append to `TableCellPaddingCssCoverageTest` (helpers `markedTable(String)`, `context(String)`, `lens(...)`, `bodyPadded(int)`, `headerPadded(int)` exist):

```java
   @Test
   void anAuthorPaddingUnderFullCoverageFloorsAtTheTierInForce() {
      // the stylesheet displaces the author value on every column, so the row is the tier's. The
      // floor gives back what the stored height left out, which under an author value is the tier
      // in force
      TableVSAssemblyInfo info = markedTable("compact");
      info.setCellPadding(new Insets(10, 6, 10, 6), CompositeValue.Type.USER);
      VizContext ctx = context("comfortable");

      assertEquals(28, VSDensityDefaults.rowHeight(ctx, info) + lens(bodyPadded(1)).getRowPadding(1, info),
                   "data row");
      assertEquals(30,
                   VSDensityDefaults.headerRowHeight(ctx, info) + lens(headerPadded(1)).getRowPadding(0, info),
                   "header row");
   }

   @Test
   void aLargerCssPaddingOverAnAuthorPaddingGrowsFromTheTierInForce() {
      // 28 - 12 + 20
      TableVSAssemblyInfo info = markedTable("compact");
      info.setCellPadding(new Insets(10, 6, 10, 6), CompositeValue.Type.USER);
      VizContext ctx = context("comfortable");

      assertEquals(36, VSDensityDefaults.rowHeight(ctx, info) + lens(bodyPadded(10)).getRowPadding(1, info));
   }

   @Test
   void aDraggedRowRoundTripsUnderAnAuthorPadding() {
      // the floor reads the tier for an author padding; store and render must still agree
      VSTableLens lens = lens(bodyPadded(1));
      TableVSAssemblyInfo info = markedTable("comfortable");
      info.setCellPadding(new Insets(10, 6, 10, 6), CompositeValue.Type.USER);
      int row = lens.getHeaderRowCount();

      int stored = Math.max(0, 40 - lens.getRowPadding(row, info));
      info.setDataRowHeight(stored);
      info.setUserDataRowHeight(true);

      assertEquals(40, stored + lens.getRowPadding(row, info));
   }
```

- [ ] **Step 6: Run them and watch the first fail**

Run: `./mvnw -q test -pl core -Dtest=TableCellPaddingCssCoverageTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: `anAuthorPaddingUnderFullCoverageFloorsAtTheTierInForce` fails at 24 / 26 (the stored side moved, the floor did not). The other two pass.

- [ ] **Step 7: The floor uses the same rule**

In `VSTableLens.getRowPadding`, replace `return Math.max(inset, getSeededInsetHeight(info));` with `return Math.max(inset, VSDensityDefaults.excludedCellPaddingHeight(info));` and delete the private method `getSeededInsetHeight` (its only caller). In the comment above that line, change "the seed is lost only where" to "what the stored height left out is lost only where" and "an author padding below the seed" to "an author padding below it". Replace the method's javadoc with:

```java
   /**
    * How much taller a row is for its padding: the inset the row actually draws, floored by the
    * padding the row's stored height left out.
    *
    * The floor is what keeps a row on its density tier. VSDensityDefaults.rowHeight stores the
    * tier less that padding on the promise that render puts it back, so a row growing by the
    * stylesheet's smaller amount alone would land below the tier, and below what the same table
    * rendered before it was marked.
    *
    * The floor is what was left out and not the resolved padding: the seed, or the tier in force
    * once an author padding exists. An author padding above it is additive and arrives through
    * the inset instead; an unmarked table left nothing out, so nothing is owed back.
    *
    * It is skipped entirely where a stylesheet sets the row height, since that replaces the
    * density height outright rather than shrinking it - adding the floor there would grow a row
    * neither the stylesheet nor the tier asked for.
    *
    * Which inset the text sits at is a separate question, and getCellInsets answers it.
    */
```

- [ ] **Step 8: Run the padding and row-height suite**

Run: `./mvnw -q test -pl core "-Dtest=DensityRowHeightInvariantTest,TableCellPaddingCssCoverageTest,TableCellPaddingSeedTest,VSDensityDefaultsTest,TableExport*Test,TableCardInset*Test,PrintLayoutRowSplitTest,PrintTableRowSplitTest" -Dsurefire.failIfNoSpecifiedTests=false`
Expected: all pass.

- [ ] **Step 9: Commit**

```bash
git -C "$WT" branch --show-current
git -C "$WT" add core/src/main/java/inetsoft/uql/viewsheet/internal/VSDensityDefaults.java core/src/main/java/inetsoft/report/composition/VSTableLens.java core/src/test/java/inetsoft/uql/viewsheet/internal/DensityRowHeightInvariantTest.java core/src/test/java/inetsoft/report/composition/TableCellPaddingCssCoverageTest.java
git -C "$WT" commit -m "Keep a table row on its tier under an author cell padding" -m "Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

### Task 4: Following the density again re-seeds the cell padding

Spec D3, §4.

**Files:**
- Modify: `core/src/main/java/inetsoft/uql/viewsheet/internal/TableDataVSAssemblyInfo.java` (`getDefaultCellPadding` javadoc ~922-927; after `resetUserCellPadding()` ~944-951)
- Modify: `core/src/main/java/inetsoft/web/composer/vs/dialog/TableViewPropertyDialogService.java:288-292`
- Modify: `core/src/main/java/inetsoft/web/composer/vs/dialog/CrosstabPropertyDialogService.java:380-384`
- Modify: `core/src/main/java/inetsoft/web/composer/vs/dialog/CalcTablePropertyDialogService.java:460-464`
- Test: `core/src/test/java/inetsoft/uql/viewsheet/internal/TableCellPaddingSeedTest.java`
- Test: `core/src/test/java/inetsoft/web/composer/vs/dialog/TableViewPropertyDialogServiceTest.java`
- Create: `core/src/test/java/inetsoft/web/composer/vs/dialog/CalcTablePropertyDialogServiceTest.java`

**Interfaces:**
- Produces: `public void resetCellPadding(VizContext ctx)` on `TableDataVSAssemblyInfo`.

- [ ] **Step 1: Write the failing info tests**

Append to `TableCellPaddingSeedTest` (it resets `viewsheet.density` after each test; add `assertNull` and `assertFalse` to its static imports if missing):

```java
   @Test
   void followingTheDensityAgainReseedsFromTheTierInForce() {
      SreeEnv.setProperty("viewsheet.density", "compact");
      TableVSAssemblyInfo info = new TableVSAssemblyInfo();
      info.seedChromeDefaults(VizContext.of(VizMark.MODERN_LIGHT));
      info.setCellPadding(new Insets(10, 6, 10, 6), CompositeValue.Type.USER);
      SreeEnv.setProperty("viewsheet.density", "comfortable");
      info.seedChromeDefaults(VizContext.of(VizMark.MODERN_LIGHT));
      assertEquals(new Insets(4, 6, 4, 6), info.getDefaultCellPadding(),
                   "precondition: the reseed skipped the author value");

      info.resetCellPadding(VizContext.of(VizMark.MODERN_LIGHT));

      assertFalse(info.isUserCellPadding());
      assertEquals(new Insets(6, 8, 6, 8), info.getCellPadding());
   }

   @Test
   void followingTheDensityAfterModernizeSeedsTheMissingDefault() {
      // an author value set while unmarked keeps Modernize from writing any DEFAULT tier, so a
      // bare clear would leave the cells with no inset at all
      SreeEnv.setProperty("viewsheet.density", "compact");
      TableVSAssemblyInfo info = new TableVSAssemblyInfo();
      info.setCellPadding(new Insets(4, 6, 4, 6), CompositeValue.Type.USER);
      info.seedChromeDefaults(VizContext.of(VizMark.MODERN_LIGHT));
      assertNull(info.getDefaultCellPadding(), "precondition");

      info.resetCellPadding(VizContext.of(VizMark.MODERN_LIGHT));

      assertEquals(new Insets(4, 6, 4, 6), info.getCellPadding());
   }

   @Test
   void crosstabAndCalcTableReseedOnFollowToo() {
      SreeEnv.setProperty("viewsheet.density", "dense");
      TableDataVSAssemblyInfo[] infos = { new CrosstabVSAssemblyInfo(), new CalcTableVSAssemblyInfo() };

      for(TableDataVSAssemblyInfo info : infos) {
         info.setCellPadding(new Insets(6, 8, 6, 8), CompositeValue.Type.DEFAULT);
         info.setCellPadding(new Insets(9, 9, 9, 9), CompositeValue.Type.USER);

         info.resetCellPadding(VizContext.of(VizMark.MODERN_LIGHT));

         assertEquals(new Insets(3, 4, 3, 4), info.getCellPadding(), info.getClass().getSimpleName());
      }
   }
```

- [ ] **Step 2: Write the failing dialog tests**

In `TableViewPropertyDialogServiceTest`, add imports `inetsoft.sree.SreeEnv`, `inetsoft.uql.viewsheet.internal.VizMark`, `org.junit.jupiter.api.AfterEach`, and the method:

```java
   @AfterEach
   public void reset() {
      SreeEnv.setProperty("viewsheet.density", null);
   }
```

Then add:

```java
   // the re-tick: a marked table whose seed stood still under an author padding
   @Test
   public void followingTheDensityAgainReseedsTheCurrentTier() throws Exception {
      SreeEnv.setProperty("viewsheet.density", "comfortable");
      tableVSAssemblyInfoSpy.setVizMark(VizMark.MODERN_LIGHT);
      tableVSAssemblyInfoSpy.setCellPadding(new Insets(4, 6, 4, 6), CompositeValue.Type.DEFAULT);
      tableVSAssemblyInfoSpy.setCellPadding(new Insets(10, 6, 10, 6), CompositeValue.Type.USER);

      TableViewPropertyDialogModel model = modelWithCellPadding(10, 6, 10, 6, true);
      service.setTablePropertyModel("Viewsheet1", "Table1", model, "", null, commandDispatcher);

      TableVSAssemblyInfo applied = captureAppliedInfo();
      assertFalse(applied.isUserCellPadding());
      assertEquals(new Insets(6, 8, 6, 8), applied.getCellPadding());
   }

   // OK on a table already following re-seeds it too, as the card inset does
   @Test
   public void followingTableIsReseededOnOk() throws Exception {
      SreeEnv.setProperty("viewsheet.density", "dense");
      tableVSAssemblyInfoSpy.setVizMark(VizMark.MODERN_LIGHT);
      tableVSAssemblyInfoSpy.setCellPadding(new Insets(6, 8, 6, 8), CompositeValue.Type.DEFAULT);

      TableViewPropertyDialogModel model = modelWithCellPadding(6, 8, 6, 8, true);
      service.setTablePropertyModel("Viewsheet1", "Table1", model, "", null, commandDispatcher);

      assertEquals(new Insets(3, 4, 3, 4), captureAppliedInfo().getCellPadding());
   }
```

Create `CalcTablePropertyDialogServiceTest.java` (AGPL header first, as in `TableViewPropertyDialogServiceTest.java`):

```java
package inetsoft.web.composer.vs.dialog;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.sree.SreeEnv;
import inetsoft.test.*;
import inetsoft.uql.CompositeValue;
import inetsoft.uql.viewsheet.CalcTableVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.CalcTableVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.VSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.VizMark;
import inetsoft.web.composer.model.vs.CalcTablePropertyDialogModel;
import inetsoft.web.composer.model.vs.PaddingPaneModel;
import inetsoft.web.composer.model.vs.VSAssemblyScriptPaneModel;
import inetsoft.web.composer.vs.objects.controller.VSObjectPropertyService;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import inetsoft.web.viewsheet.service.VSDialogService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.Insets;
import java.security.Principal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Following the default density again on a freehand table's cell padding re-seeds the tier in
 * force, as the table and crosstab dialogs do.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome()
@ExtendWith({MockitoExtension.class})
@Tag("core")
public class CalcTablePropertyDialogServiceTest {
   @BeforeEach
   public void setup() throws Exception {
      service = new CalcTablePropertyDialogService(vsObjectPropertyService, dialogService, engine);
      when(engine.getViewsheet(anyString(), nullable(Principal.class))).thenReturn(rvs);
      when(rvs.getViewsheet()).thenReturn(viewsheet);
      when(viewsheet.getAssembly(anyString())).thenReturn(calcAssembly);
      when(calcAssembly.getVSAssemblyInfo()).thenReturn(calcInfo);
   }

   @AfterEach
   public void reset() {
      SreeEnv.setProperty("viewsheet.density", null);
   }

   @Test
   public void followingTheDensityAgainReseedsTheCurrentTier() throws Exception {
      SreeEnv.setProperty("viewsheet.density", "comfortable");
      calcInfo.setVizMark(VizMark.MODERN_LIGHT);
      calcInfo.setCellPadding(new Insets(4, 6, 4, 6), CompositeValue.Type.DEFAULT);
      calcInfo.setCellPadding(new Insets(10, 6, 10, 6), CompositeValue.Type.USER);

      CalcTablePropertyDialogModel model = new CalcTablePropertyDialogModel();
      // the calc model carries no script pane by default and the set path reads it
      model.setVsAssemblyScriptPaneModel(
         VSAssemblyScriptPaneModel.builder().expression("").scriptEnabled(false).build());
      PaddingPaneModel pane = model.getTableViewGeneralPaneModel().getCellPaddingPaneModel();
      pane.setTop(10);
      pane.setLeft(6);
      pane.setBottom(10);
      pane.setRight(6);
      pane.setFollowsDefault(true);

      service.setCalcTablePropertyModel("Viewsheet1", "Calc1", model, "", null, commandDispatcher);

      ArgumentCaptor<VSAssemblyInfo> captor = ArgumentCaptor.forClass(VSAssemblyInfo.class);
      verify(vsObjectPropertyService).editObjectProperty(
         any(RuntimeViewsheet.class), captor.capture(), eq("Calc1"), any(), any(),
         nullable(Principal.class), any(CommandDispatcher.class));
      CalcTableVSAssemblyInfo applied = (CalcTableVSAssemblyInfo) captor.getValue();

      assertFalse(applied.isUserCellPadding());
      assertEquals(new Insets(6, 8, 6, 8), applied.getCellPadding());
   }

   private CalcTablePropertyDialogService service;
   private final CalcTableVSAssemblyInfo calcInfo = new CalcTableVSAssemblyInfo();
   @Mock VSObjectPropertyService vsObjectPropertyService;
   @Mock VSDialogService dialogService;
   @Mock ViewsheetService engine;
   @Mock RuntimeViewsheet rvs;
   @Mock Viewsheet viewsheet;
   @Mock CalcTableVSAssembly calcAssembly;
   @Mock CommandDispatcher commandDispatcher;
}
```

If the calc set path dereferences another sub-model that is null by default (a tip, advanced or hierarchy pane), set that sub-model on the test's `model` the way the script pane is set, and say which in the commit message. Do not mock the service's internals.

- [ ] **Step 3: Run them and watch them fail**

Run: `./mvnw -q test -pl core "-Dtest=TableCellPaddingSeedTest,TableViewPropertyDialogServiceTest,CalcTablePropertyDialogServiceTest" -Dsurefire.failIfNoSpecifiedTests=false`
Expected: compilation fails on `resetCellPadding`; once it exists but before the dialogs call it, the dialog tests fail with the old seed (4/6/4/6, 6/8/6/8).

- [ ] **Step 4: Add `resetCellPadding`**

In `TableDataVSAssemblyInfo`, replace `getDefaultCellPadding`'s javadoc with:

```java
   /**
    * The density seed alone, ignoring any author value. What the stored row height leaves out
    * while no author value exists (see VSDensityDefaults.excludedCellPaddingHeight).
    */
```

Replace `resetUserCellPadding`'s javadoc with:

```java
   /**
    * Drop the author's cell padding, leaving the density seed as it stands. The unmarked pane's
    * clear, where there is no tier to re-seed from.
    */
```

and add after it:

```java
   /**
    * Drop the author's cell padding and re-seed the density's from the tier in force. The seed
    * stands still while an author value holds, so clearing that alone would hand the cells a tier
    * the dashboard has left. Writing the density value into the USER tier would pin this tier.
    */
   public void resetCellPadding(VizContext ctx) {
      cellPadding.resetUserValue();
      setCellPadding(VSDensityDefaults.cellPadding(ctx), CompositeValue.Type.DEFAULT);
   }
```

- [ ] **Step 5: Call it from the three dialogs**

In each follow branch, replace the `resetUserCellPadding()` call and its comment. `TableViewPropertyDialogService` (variable `tableAssemblyInfo`):

```java
      else if(cellPaddingFollowsDefault) {
         // clear the opinion and re-seed from the tier in force: the seed stood still while the
         // author value held. Writing the density value into the USER tier would pin this tier
         tableAssemblyInfo.resetCellPadding(VizContext.of(tableAssemblyInfo));
      }
```

`CrosstabPropertyDialogService` uses `assemblyInfo`, `CalcTablePropertyDialogService` uses `calcTableAssemblyInfo`; the code is otherwise identical. Leave the unmarked zero branch's `resetUserCellPadding()` (~lines 282 / 374 / 454) as it is.

- [ ] **Step 6: Run the tests**

Run: `./mvnw -q test -pl core "-Dtest=TableCellPaddingSeedTest,TableViewPropertyDialogServiceTest,CalcTablePropertyDialogServiceTest,DensityRowHeightInvariantTest,TableCellPaddingCssCoverageTest" -Dsurefire.failIfNoSpecifiedTests=false`
Expected: all pass.

- [ ] **Step 7: Commit**

```bash
git -C "$WT" branch --show-current
git -C "$WT" add core/src/main/java/inetsoft/uql/viewsheet/internal/TableDataVSAssemblyInfo.java core/src/main/java/inetsoft/web/composer/vs/dialog/TableViewPropertyDialogService.java core/src/main/java/inetsoft/web/composer/vs/dialog/CrosstabPropertyDialogService.java core/src/main/java/inetsoft/web/composer/vs/dialog/CalcTablePropertyDialogService.java core/src/test/java/inetsoft/uql/viewsheet/internal/TableCellPaddingSeedTest.java core/src/test/java/inetsoft/web/composer/vs/dialog/TableViewPropertyDialogServiceTest.java core/src/test/java/inetsoft/web/composer/vs/dialog/CalcTablePropertyDialogServiceTest.java
git -C "$WT" commit -m "Re-seed a table's cell padding when it follows the density again" -m "Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

### Task 5: A marked container's hidden title gives up its lane — child stack, HTML lists, inclusion

Spec D4, §5. These three sites land together: `getChildTop` alone moves HTML lists up twice and drops a first child from match-layout export.

**Files:**
- Modify: `core/src/main/java/inetsoft/uql/viewsheet/internal/CurrentSelectionVSAssemblyInfo.java` (after `getOutSelectionRowHeight`, ~line 284)
- Modify: `core/src/main/java/inetsoft/uql/viewsheet/CurrentSelectionVSAssembly.java:179-187`
- Modify: `core/src/main/java/inetsoft/report/io/viewsheet/html/HTMLSelectionListHelper.java:83-87`
- Modify: `core/src/main/java/inetsoft/report/io/viewsheet/AbstractVSExporter.java:2693-2698`
- Test: `core/src/test/java/inetsoft/report/io/viewsheet/ContainerChildTopTest.java`
- Test: `core/src/test/java/inetsoft/report/io/viewsheet/SelectionContainerChildExportTest.java`
- Create: `core/src/test/java/inetsoft/report/io/viewsheet/html/HTMLHiddenContainerTitleTest.java`

**Interfaces:**
- Produces: `public boolean isTitleLaneDropped()` and `public int getTitleLaneHeight()` on `CurrentSelectionVSAssemblyInfo` (Task 6 uses both).

- [ ] **Step 1: Write the failing stack tests**

In `ContainerChildTopTest`, add imports `org.junit.jupiter.params.ParameterizedTest` and `org.junit.jupiter.params.provider.CsvSource`. Replace the fixture's first lines so it takes a density and a title flag; everything from `int lane = ...` down stays:

```java
   private static CurrentSelectionVSAssembly container(VizMark mark) {
      return container(mark, "comfortable", true);
   }

   // every mark is pinned, because construction takes the org gate's
   private static CurrentSelectionVSAssembly container(VizMark mark, String density,
                                                       boolean titleVisible)
   {
      Viewsheet vs = new Viewsheet();
      vs.getViewsheetInfo().setVizDensity(density);
      CurrentSelectionVSAssembly container = new CurrentSelectionVSAssembly(vs, "CurrentSelection1");
      vs.addAssembly(container);
      container.getVSAssemblyInfo().setVizMark(mark);
      container.getVSAssemblyInfo().setPixelOffset(new Point(418, 38));
      container.getVSAssemblyInfo().setPixelSize(new Dimension(300, 600));
      info(container).setTitleVisibleValue(titleVisible);
      int lane = mark != null ? 30 : 20;
      // ... the rest of the existing fixture, unchanged ...
   }

   private static CurrentSelectionVSAssemblyInfo info(CurrentSelectionVSAssembly container) {
      return (CurrentSelectionVSAssemblyInfo) container.getVSAssemblyInfo();
   }
```

Add the tests:

```java
   // two 30px rows from the container top, then the dropdown, the slider (40 + 30) and the list
   @Test
   void aMarkedContainerWithAHiddenTitleDrawsItsChildrenFromItsTop() {
      CurrentSelectionVSAssembly container = container(VizMark.MODERN_LIGHT, "comfortable", false);
      assertArrayEquals(new float[] { 98, 128, 198, 428 }, exportTops(container));
   }

   @Test
   void aMarkedContainerWithAHiddenTitleStoresTheStackItDraws() {
      CurrentSelectionVSAssembly container = container(VizMark.MODERN_LIGHT, "comfortable", false);
      container.layout();
      assertArrayEquals(new int[] { 98, 128, 198, 428 }, storedTops(container));
   }

   @Test
   void anUnmarkedContainerWithAHiddenTitleKeepsTheLaneAsBefore() {
      CurrentSelectionVSAssembly container = container(null, "comfortable", false);
      assertArrayEquals(new float[] { 98, 118, 178, 318 }, exportTops(container));
      container.layout();
      assertArrayEquals(new int[] { 98, 118, 158, 298 }, storedTops(container));
   }

   // the first child sits under the two out rows, and under the title lane only while it shows
   @ParameterizedTest
   @CsvSource({ "comfortable, 128, 98", "compact, 116, 90", "dense, 98, 78" })
   void aHiddenTitleGivesUpItsLaneAtEveryTier(String density, float visible, float hidden) {
      assertEquals(visible, exportTops(container(VizMark.MODERN_LIGHT, density, true))[0]);
      assertEquals(hidden, exportTops(container(VizMark.MODERN_LIGHT, density, false))[0]);
   }

   @ParameterizedTest
   @CsvSource({ "comfortable", "compact", "dense" })
   void anUnmarkedContainerKeepsAHiddenTitlesLaneAtEveryTier(String density) {
      assertEquals(98f, exportTops(container(null, density, false))[0]);
   }

   @Test
   void onlyAMarkedContainersHiddenTitleDropsTheLane() {
      assertTrue(info(container(VizMark.MODERN_LIGHT, "comfortable", false)).isTitleLaneDropped());
      assertEquals(0, info(container(VizMark.MODERN_LIGHT, "comfortable", false)).getTitleLaneHeight());
      assertEquals(30, info(container(VizMark.MODERN_LIGHT, "comfortable", true)).getTitleLaneHeight());
      assertFalse(info(container(null, "comfortable", false)).isTitleLaneDropped());
      assertEquals(20, info(container(null, "comfortable", false)).getTitleLaneHeight());
      assertEquals(20, info(container(null, "comfortable", true)).getTitleLaneHeight());
   }
```

- [ ] **Step 2: Write the failing clip and inclusion tests**

In `SelectionContainerChildExportTest`, add one sentence to the class javadoc: "With its title hidden, a marked container draws from its top, so its first child sits at gap 0 and its bottom child keeps the lane's room." Add (its helper `child(Viewsheet, String, Point, int)` returns a `SelectionListVSAssembly`):

```java
   // a marked comfortable container at (418, 38), 300 x 210, title hidden: Customer (180) above
   // Region (30) at 38 and 218, as the viewer draws them; under the 30px lane Region would sit
   // at 248, past the bottom
   @Test
   void aHiddenTitleMarkedContainersFirstListIsExportedAtItsTop() {
      CurrentSelectionVSAssembly container = lists(VizMark.MODERN_LIGHT, false);
      assertTrue(matchLayout(container).needExport(childOf(container, "Customer")));
   }

   @Test
   void aHiddenTitleMarkedContainersBottomListIsExportedWhole() {
      CurrentSelectionVSAssembly container = lists(VizMark.MODERN_LIGHT, false);
      AbstractVSExporter exporter = matchLayout(container);
      VSAssembly region = childOf(container, "Region");

      assertTrue(exporter.needExport(region));
      exporter.prepareAssembly(region);
      assertEquals(30, region.getPixelSize().height);
   }

   @Test
   void aVisibleTitleMarkedContainersBottomListIsLeftOutAsBefore() {
      CurrentSelectionVSAssembly container = lists(VizMark.MODERN_LIGHT, true);
      assertFalse(matchLayout(container).needExport(childOf(container, "Region")));
   }

   // unmarked, the stored stack keeps the 20px lane: Region at 238, cut to the 10 left
   @Test
   void aHiddenTitleUnmarkedContainersBottomListIsClippedAsBefore() {
      CurrentSelectionVSAssembly container = lists(null, false);
      AbstractVSExporter exporter = matchLayout(container);
      VSAssembly region = childOf(container, "Region");

      assertTrue(exporter.needExport(region));
      exporter.prepareAssembly(region);
      assertEquals(10, region.getPixelSize().height);
   }

   // a slider is measured from its stored offset, which layout() writes as drawn: under Customer
   // (160) it sits at 198 and keeps its 40; under the lane it would sit at 228, cut to 20
   @Test
   void aHiddenTitleMarkedContainersSliderIsClippedFromWhereItIsDrawn() {
      CurrentSelectionVSAssembly container = sliderStack(VizMark.MODERN_LIGHT, false, false);
      VSAssembly slider = childOf(container, "Slider");
      matchLayout(container).prepareAssembly(slider);
      assertEquals(40, slider.getPixelSize().height);
   }

   @Test
   void aVisibleTitleMarkedContainersSliderIsClippedAsBefore() {
      CurrentSelectionVSAssembly container = sliderStack(VizMark.MODERN_LIGHT, true, false);
      VSAssembly slider = childOf(container, "Slider");
      matchLayout(container).prepareAssembly(slider);
      assertEquals(20, slider.getPixelSize().height);
   }

   @Test
   void aHiddenTitleMarkedContainersFirstSliderIsExportedAtItsTop() {
      CurrentSelectionVSAssembly container = sliderStack(VizMark.MODERN_LIGHT, false, true);
      assertTrue(matchLayout(container).needExport(childOf(container, "Slider")));
   }

   private static CurrentSelectionVSAssembly lists(VizMark mark, boolean titleVisible) {
      CurrentSelectionVSAssembly container = container210(mark, titleVisible);
      Viewsheet vs = container.getViewsheet();
      child(vs, "Customer", new Point(418, 0), 180).getVSAssemblyInfo().setVizMark(mark);
      child(vs, "Region", new Point(418, 0), 30).getVSAssemblyInfo().setVizMark(mark);
      container.setAssemblies(new String[] { "Customer", "Region" });
      container.layout();
      return container;
   }

   private static CurrentSelectionVSAssembly sliderStack(VizMark mark, boolean titleVisible,
                                                         boolean sliderFirst)
   {
      CurrentSelectionVSAssembly container = container210(mark, titleVisible);
      Viewsheet vs = container.getViewsheet();
      child(vs, "Customer", new Point(418, 0), 160).getVSAssemblyInfo().setVizMark(mark);
      TimeSliderVSAssembly slider = new TimeSliderVSAssembly(vs, "Slider");
      vs.addAssembly(slider);
      TimeSliderVSAssemblyInfo info = (TimeSliderVSAssemblyInfo) slider.getVSAssemblyInfo();
      info.setVizMark(mark);
      info.setPixelOffset(new Point(418, 0));
      info.setPixelSize(new Dimension(300, 40));
      container.setAssemblies(sliderFirst ? new String[] { "Slider", "Customer" } :
                                 new String[] { "Customer", "Slider" });
      container.layout();
      return container;
   }

   private static CurrentSelectionVSAssembly container210(VizMark mark, boolean titleVisible) {
      Viewsheet vs = new Viewsheet();
      vs.getViewsheetInfo().setVizDensity("comfortable");
      CurrentSelectionVSAssembly container = new CurrentSelectionVSAssembly(vs, "CurrentSelection1");
      vs.addAssembly(container);
      CurrentSelectionVSAssemblyInfo info = (CurrentSelectionVSAssemblyInfo) container.getVSAssemblyInfo();
      info.setPixelOffset(new Point(418, 38));
      info.setPixelSize(new Dimension(300, 210));
      info.setTitleHeightValue(20);
      info.setTitleVisibleValue(titleVisible);
      info.setVizMark(mark);
      container.setShowCurrentSelectionValue(false);
      return container;
   }

   private static AbstractVSExporter matchLayout(CurrentSelectionVSAssembly container) {
      AbstractVSExporter exporter = new HTMLVSExporter(new ByteArrayOutputStream());
      exporter.viewsheet = container.getViewsheet();
      exporter.setMatchLayout(true);
      return exporter;
   }

   private static VSAssembly childOf(CurrentSelectionVSAssembly container, String name) {
      return (VSAssembly) container.getViewsheet().getAssembly(name);
   }
```

- [ ] **Step 3: Write the failing HTML tests**

Create `core/src/test/java/inetsoft/report/io/viewsheet/html/HTMLHiddenContainerTitleTest.java` (AGPL header first):

```java
package inetsoft.report.io.viewsheet.html;

import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.test.SwapperTestConfiguration;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.*;
import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * HTML draws a selection container's children where the viewer does. With its title hidden, a
 * marked container stacks them from its top: a list is not moved up a second time, the bottom
 * child keeps its height, and a range slider moves with the lists. An unmarked container keeps
 * the lane its export has always left.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class HTMLHiddenContainerTitleTest {
   // the viewer draws Category 38-68, Customer 68-218 and Product 218-248 in the 210px container
   @Test
   void aMarkedContainersListIsWrittenWhereTheViewerDrawsIt() {
      assertArrayEquals(new double[] { 68, 150 }, box(list(VizMark.MODERN_LIGHT, false, "Customer")));
   }

   @Test
   void aMarkedContainersBottomListKeepsItsHeight() {
      assertArrayEquals(new double[] { 218, 30 }, box(list(VizMark.MODERN_LIGHT, false, "Product")));
   }

   // clipped under the 20px lane, then moved up by it, as before
   @Test
   void anUnmarkedContainersListsAreMovedUpAsBefore() {
      assertArrayEquals(new double[] { 58, 150 }, box(list(null, false, "Customer")));
      assertArrayEquals(new double[] { 208, 20 }, box(list(null, false, "Product")));
   }

   @Test
   void aVisibleTitleKeepsTheListsUnderItAsBefore() {
      assertArrayEquals(new double[] { 98, 150 }, box(list(VizMark.MODERN_LIGHT, true, "Customer")));
   }

   // the viewer draws Category 58-88 and the collapsed slider 88-118
   @Test
   void aMarkedContainersSliderMovesUpWithTheLists() {
      assertArrayEquals(new double[] { 88, 30 }, box(slider(VizMark.MODERN_LIGHT, false)));
   }

   @Test
   void anUnmarkedContainersSliderStaysWhereItWas() {
      assertArrayEquals(new double[] { 98, 20 }, box(slider(null, false)));
   }

   @Test
   void aVisibleTitleKeepsTheSliderUnderItAsBefore() {
      assertArrayEquals(new double[] { 118, 30 }, box(slider(VizMark.MODERN_LIGHT, true)));
   }

   private static String list(VizMark mark, boolean titleVisible, String name) {
      CurrentSelectionVSAssembly container = container(mark, titleVisible, 38, 210);
      Viewsheet vs = container.getViewsheet();
      member(vs, "Category", mark, SelectionVSAssemblyInfo.DROPDOWN_SHOW_TYPE, 150);
      member(vs, "Customer", mark, SelectionVSAssemblyInfo.LIST_SHOW_TYPE, 150);
      member(vs, "Product", mark, SelectionVSAssemblyInfo.DROPDOWN_SHOW_TYPE, 150);
      container.setAssemblies(new String[] { "Category", "Customer", "Product" });
      SelectionListVSAssembly list = (SelectionListVSAssembly) vs.getAssembly(name);
      HTMLSelectionListHelper helper = new HTMLSelectionListHelper(coordinates(vs), vs, list);
      helper.setExporter(new HTMLVSExporter(new ByteArrayOutputStream()));
      StringWriter out = new StringWriter();
      PrintWriter writer = new PrintWriter(out);
      helper.write(writer, list);
      writer.flush();
      return out.toString();
   }

   private static String slider(VizMark mark, boolean titleVisible) {
      CurrentSelectionVSAssembly container = container(mark, titleVisible, 58, 240);
      Viewsheet vs = container.getViewsheet();
      member(vs, "Category", mark, SelectionVSAssemblyInfo.DROPDOWN_SHOW_TYPE, 150);
      TimeSliderVSAssembly slider = new TimeSliderVSAssembly(vs, "Slider");
      vs.addAssembly(slider);
      TimeSliderVSAssemblyInfo info = (TimeSliderVSAssemblyInfo) slider.getVSAssemblyInfo();
      info.setVizMark(mark);
      info.setHidden(true);
      info.setTitleHeightValue(20);
      info.setPixelOffset(new Point(418, 0));
      info.setPixelSize(new Dimension(300, 30));
      container.setAssemblies(new String[] { "Category", "Slider" });
      StringWriter out = new StringWriter();
      coordinates(vs).writeTimeSlider(out, slider, null, 0);
      return out.toString();
   }

   private static CurrentSelectionVSAssembly container(VizMark mark, boolean titleVisible, int top,
                                                       int height)
   {
      Viewsheet vs = new Viewsheet();
      vs.getViewsheetInfo().setVizDensity("comfortable");
      CurrentSelectionVSAssembly container = new CurrentSelectionVSAssembly(vs, "CurrentSelection1");
      vs.addAssembly(container);
      CurrentSelectionVSAssemblyInfo info = (CurrentSelectionVSAssemblyInfo) container.getVSAssemblyInfo();
      info.setPixelOffset(new Point(418, top));
      info.setPixelSize(new Dimension(300, height));
      info.setTitleHeightValue(20);
      info.setTitleVisibleValue(titleVisible);
      info.setVizMark(mark);
      container.setShowCurrentSelectionValue(false);
      return container;
   }

   private static void member(Viewsheet vs, String name, VizMark mark, int showType, int height) {
      SelectionListVSAssembly list = new SelectionListVSAssembly(vs, name);
      vs.addAssembly(list);
      SelectionListVSAssemblyInfo info = list.getSelectionListInfo();
      info.setVizMark(mark);
      info.setShowTypeValue(showType);
      info.setTitleHeightValue(20);
      info.setPixelOffset(new Point(418, 0));
      info.setPixelSize(new Dimension(300, height));
   }

   // the top and height of the first absolute box written
   private static double[] box(String html) {
      Matcher m = Pattern.compile("position:absolute;left:[0-9.]+px;top:([0-9.]+)px;" +
                                  "width:[0-9.]+px;height:([0-9.]+)px;").matcher(html);
      assertTrue(m.find(), html);
      return new double[] { Double.parseDouble(m.group(1)), Double.parseDouble(m.group(2)) };
   }

   private static HTMLCoordinateHelper coordinates(Viewsheet vs) {
      HTMLCoordinateHelper helper = new HTMLCoordinateHelper();
      helper.setViewsheet(vs);
      return helper;
   }
}
```

If the slider cases misbehave because `writeTimeSlider` needs more than the fixture gives it, assert on `coordinates(vs).getBounds(slider, CoordinateHelper.ALL)` instead; that is the box `writeTimeSlider` writes. Keep the expected numbers.

- [ ] **Step 4: Run them and watch them fail**

Run: `./mvnw -q test -pl core "-Dtest=ContainerChildTopTest,SelectionContainerChildExportTest,HTMLHiddenContainerTitleTest" -Dsurefire.failIfNoSpecifiedTests=false`
Expected: compilation fails on `isTitleLaneDropped`. With the two info methods added and nothing else, the marked hidden-title stack tests fail (128/158/228/458 where 98/128/198/428 is wanted), `aHiddenTitleMarkedContainersBottomListIsExportedWhole` fails (Region dropped), `aHiddenTitleMarkedContainersSliderIsClippedFromWhereItIsDrawn` fails (20), the HTML bottom list is `{218, 0}` and the marked slider `{118, 30}`. The first-child inclusion tests and the HTML Customer test pass at this point; they go red if `getChildTop` changes without Steps 7 and 8.

- [ ] **Step 5: The rule**

In `CurrentSelectionVSAssemblyInfo`, after `getOutSelectionRowHeight`:

```java
   /**
    * Check if the title gives up its lane. A hidden title does on a marked container, which the
    * viewer draws from its top; an unmarked container keeps the lane its exports always left.
    */
   public boolean isTitleLaneDropped() {
      return !isTitleVisible() && getVizMark() != null;
   }

   /**
    * Get the height of the lane above the out-selection rows and the children.
    */
   public int getTitleLaneHeight() {
      return isTitleLaneDropped() ? 0 : getTitleHeight();
   }
```

- [ ] **Step 6: The child stack**

In `CurrentSelectionVSAssembly.getChildTop`, change `float titleH = cinfo.getTitleHeight();` to `float titleH = cinfo.getTitleLaneHeight();`, and in its javadoc change "under its title and any collapsed out-selection rows" to "under its title lane and any collapsed out-selection rows". `layout()` needs no edit.

- [ ] **Step 7: HTML takes back only the lane it has**

In `HTMLSelectionListHelper`, replace:

```java
         if(!cinfo.isTitleVisible()) {
            int containerTitleH = cinfo.getTitleHeight();
```

with:

```java
         // getBounds stacks under the container's lane, which HTML does not draw for a hidden
         // title; a container that drops the lane has none to take back
         if(!cinfo.isTitleVisible()) {
            int containerTitleH = cinfo.getTitleLaneHeight();
```

- [ ] **Step 8: A first child at the container's top is inside it**

In `AbstractVSExporter.needExport`, replace `return gap > 0 && gap < csAssembly.getPixelSize().height;` with:

```java
               // with the title lane dropped, the first child sits at the container's top
               boolean inside = gap > 0 || gap == 0 &&
                  ((CurrentSelectionVSAssemblyInfo) csAssembly.getVSAssemblyInfo()).isTitleLaneDropped();
               return inside && gap < csAssembly.getPixelSize().height;
```

- [ ] **Step 9: Run the tests and the container suite**

Run: `./mvnw -q test -pl core "-Dtest=ContainerChildTopTest,SelectionContainerChildExportTest,HTMLHiddenContainerTitleTest,SelectionContainerOutRowsExportTest,OutSelectionRowHeightTest,VSSelectionContainerServiceTest,GroupingServiceTest,AbstractLayoutTest" -Dsurefire.failIfNoSpecifiedTests=false`
Expected: all pass.

- [ ] **Step 10: Commit**

```bash
git -C "$WT" branch --show-current
git -C "$WT" add core/src/main/java/inetsoft/uql/viewsheet/internal/CurrentSelectionVSAssemblyInfo.java core/src/main/java/inetsoft/uql/viewsheet/CurrentSelectionVSAssembly.java core/src/main/java/inetsoft/report/io/viewsheet/html/HTMLSelectionListHelper.java core/src/main/java/inetsoft/report/io/viewsheet/AbstractVSExporter.java core/src/test/java/inetsoft/report/io/viewsheet/ContainerChildTopTest.java core/src/test/java/inetsoft/report/io/viewsheet/SelectionContainerChildExportTest.java core/src/test/java/inetsoft/report/io/viewsheet/html/HTMLHiddenContainerTitleTest.java
git -C "$WT" commit -m "Stack a marked container's children from its top when its title is hidden" -m "Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

### Task 6: The hidden title's frame and out-selection rows

Spec D4, §5. PDF, PNG and PowerPoint share `VSCurrentSelectionHelper`; Excel overrides both methods and is unaffected.

**Files:**
- Modify: `core/src/main/java/inetsoft/report/io/viewsheet/VSCurrentSelectionHelper.java:66` and `:125`
- Test: `core/src/test/java/inetsoft/report/io/viewsheet/SelectionContainerOutRowsExportTest.java`

**Interfaces:**
- Consumes: `isTitleLaneDropped()`, `getTitleLaneHeight()` from Task 5.

- [ ] **Step 1: Write the failing tests**

In `SelectionContainerOutRowsExportTest`, add the import `java.awt.geom.Rectangle2D` and `static org.junit.jupiter.api.Assertions.*` in place of the single `assertEquals` import if needed. Change the fixture to `container(VizMark mark, boolean showCurrent, boolean titleVisible)`, keep a two-argument `container(VizMark mark, boolean showCurrent)` that delegates with `true`, and in the three-argument form add `((CurrentSelectionVSAssemblyInfo) container.getVSAssemblyInfo()).setTitleVisibleValue(titleVisible);` right after `setVizMark(mark)`. Then add:

```java
   @Test
   void aMarkedContainerWithAHiddenTitleFramesTheWholeBox() {
      assertEquals(new Rectangle2D.Double(418, 38, 300, 240), drawn(VizMark.MODERN_LIGHT, false).frame);
   }

   @Test
   void anUnmarkedContainerWithAHiddenTitleFramesBelowTheLaneAsBefore() {
      assertEquals(new Rectangle2D.Double(418, 58, 300, 220), drawn(null, false).frame);
   }

   @Test
   void aVisibleTitleFramesTheWholeBoxAsBefore() {
      assertEquals(new Rectangle2D.Double(418, 38, 300, 240), drawn(VizMark.MODERN_LIGHT, true).frame);
   }

   @Test
   void aMarkedContainerWithAHiddenTitleDrawsItsOutRowsFromItsTop() {
      assertEquals(List.of(38.0, 68.0), drawn(VizMark.MODERN_LIGHT, false).rows);
   }

   @Test
   void aMarkedContainerWithAVisibleTitleDrawsItsOutRowsUnderTheTitleAsBefore() {
      assertEquals(List.of(68.0, 98.0), drawn(VizMark.MODERN_LIGHT, true).rows);
   }

   @Test
   void anUnmarkedContainerWithAHiddenTitleDrawsItsOutRowsUnderTheLaneAsBefore() {
      assertEquals(List.of(58.0, 78.0), drawn(null, false).rows);
   }

   @Test
   void aMarkedContainerWithAHiddenTitleStacksItsChildUnderTheRowsFromItsTop() {
      CurrentSelectionVSAssembly container = container(VizMark.MODERN_LIGHT, true, false);
      VSAssembly child = (VSAssembly) container.getViewsheet().getAssembly("SelectionList2");
      assertEquals(98f, CoordinateHelper.getContainerChildTop(container, child, 38, null));
   }

   private static Probe drawn(VizMark mark, boolean titleVisible) {
      CurrentSelectionVSAssembly container = container(mark, true, titleVisible);
      Probe probe = new Probe(container.getViewsheet());
      probe.write(container);
      return probe;
   }

   // the PDF, PNG and PowerPoint helpers share this drawing; the probe records it
   private static final class Probe extends VSCurrentSelectionHelper {
      Probe(Viewsheet vs) {
         cHelper = new CoordinateHelper();
         cHelper.setViewsheet(vs);
      }

      @Override
      protected void writeTitle(CurrentSelectionVSAssemblyInfo info) {
      }

      @Override
      protected void drawTextBox(String value, Rectangle2D bounds, VSCompositeFormat format,
                                 Insets padding)
      {
         frame = bounds;
      }

      @Override
      protected void writeOutTitle(String title, String value, Rectangle2D bounds,
                                   VSCompositeFormat format, double titleRatio, Insets padding)
      {
         rows.add(bounds.getY());
      }

      private Rectangle2D frame;
      private final List<Double> rows = new ArrayList<>();
   }
```

- [ ] **Step 2: Run them and watch them fail**

Run: `./mvnw -q test -pl core -Dtest=SelectionContainerOutRowsExportTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: the marked hidden-title frame is `(418, 68, 300, 210)` and its rows `[68.0, 98.0]`; the others pass.

- [ ] **Step 3: Frame and rows follow the lane**

In `VSCurrentSelectionHelper.writeObjectBackground`, replace `if(!info.isTitleVisible()) {` with:

```java
      // a hidden title leaves its lane empty above the frame, unless the container drops the lane
      if(!info.isTitleVisible() && !info.isTitleLaneDropped()) {
```

In `writeSelections`, replace `startPos = new Point(startPos.x, startPos.y + info.getTitleHeight());` with `startPos = new Point(startPos.x, startPos.y + info.getTitleLaneHeight());`.

- [ ] **Step 4: Run the container export suite**

Run: `./mvnw -q test -pl core "-Dtest=SelectionContainerOutRowsExportTest,ContainerChildTopTest,SelectionContainerChildExportTest,HTMLHiddenContainerTitleTest,OutSelectionRowHeightTest" -Dsurefire.failIfNoSpecifiedTests=false`
Expected: all pass.

- [ ] **Step 5: Commit**

```bash
git -C "$WT" branch --show-current
git -C "$WT" add core/src/main/java/inetsoft/report/io/viewsheet/VSCurrentSelectionHelper.java core/src/test/java/inetsoft/report/io/viewsheet/SelectionContainerOutRowsExportTest.java
git -C "$WT" commit -m "Frame a marked container's whole box in export when its title is hidden" -m "Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

### Task 7: A marked table's strip and outline follow its shifted card

Spec D5, §6. Browser only.

**Files:**
- Modify: `web/projects/portal/src/app/vsobjects/model/base-table-model.ts:80`
- Modify: `web/projects/portal/src/app/vsobjects/objects/table/base-table.ts:272` and `:1759-1781`
- Modify: `web/projects/portal/src/app/vsobjects/objects/vs-object-container.component.ts` (after `getLaneInset` ~:544; `getToolbarTop` :546-565; `getVsObjectPosition` table branch :818-823; after `getActualWidth` ~:995-998)
- Modify: `web/projects/portal/src/app/vsobjects/objects/vs-object-container.component.html:38` and `:389`
- Create: `web/projects/portal/src/app/vsobjects/objects/table/base-table.spec.ts`
- Test: `web/projects/portal/src/app/vsobjects/objects/vs-object-container.component.spec.ts`

**Interfaces:**
- Produces: `BaseTableModel.cardTopShift?: number`; `VSObjectContainer.getVsObjectHeight(vsObject: VSObjectModel): number`.

- [ ] **Step 1: Install the worktree's frontend dependencies**

```bash
cd "$WT/web"
npm ci
```

Expected: completes without errors (several minutes).

- [ ] **Step 2: Add the model field**

In `base-table-model.ts`, after `objectHeight?: number;`:

```ts
   cardTopShift?: number; // client-only: how far the viewer draws the card below objectFormat.top
```

- [ ] **Step 3: Write the failing table spec**

Create `table/base-table.spec.ts` (AGPL header first, as in `vs-object-container.component.spec.ts`):

```ts
import { ViewsheetInfo } from "../../data/viewsheet-info";
import { createCalcTableComponent } from "./vs-calctable.component.test-helpers";
import { createCrosstabComponent } from "./vs-crosstab.component.test-helpers";
import { createTableComponent } from "./vs-table.component.test-helpers";

const tabs = (bottomTabs = true) =>
   new ViewsheetInfo([{ absoluteName: "tab1", bottomTabs }] as any, null, false, "vs1");

// lets the deferred model writes land
const flush = () => Promise.resolve();

const inBottomTab: any = { shrink: true, scrollHeight: 54, containerType: "VSTab", container: "tab1" };

afterEach(() => vi.restoreAllMocks());

// A shrunk table in a bottom-tabs tab draws its card below objectFormat.top; it publishes that
// shift so the container can place the strip and the focus outline on the card.
describe("BaseTable card top shift", () => {
   it("publishes a shrunk table's bottom-tabs shift after the pass, not during it", async () => {
      // 300x200 at top 50, 20px title and header: tableHeight 160, card 200 - 160 + 54 = 94
      const { comp } = createTableComponent({ model: inBottomTab });
      comp.vsInfo = tabs();

      expect(comp.getObjectTop()).toBe(50 + 106);
      expect(comp.model.cardTopShift).toBeUndefined();

      await flush();

      expect(comp.model.cardTopShift).toBe(106);
   });

   it("marks the view for check when it publishes", async () => {
      const { comp, changeDetectorRef } = createTableComponent({ model: inBottomTab });
      comp.vsInfo = tabs();
      vi.spyOn(comp as any, "getCardHeight").mockReturnValue(120);
      changeDetectorRef.markForCheck.mockClear();

      comp.getObjectTop();
      await flush();

      expect(comp.model.cardTopShift).toBe(80);
      expect(changeDetectorRef.markForCheck).toHaveBeenCalled();
   });

   it("publishes nothing for a table in a top-tabs tab", async () => {
      const { comp, changeDetectorRef } = createTableComponent({ model: inBottomTab });
      comp.vsInfo = tabs(false);
      vi.spyOn(comp as any, "getCardHeight").mockReturnValue(120);
      changeDetectorRef.markForCheck.mockClear();

      expect(comp.getObjectTop()).toBe(50);
      await flush();

      expect(comp.model.cardTopShift).toBeUndefined();
      expect(changeDetectorRef.markForCheck).not.toHaveBeenCalled();
   });

   it("publishes nothing outside the viewer", async () => {
      const { comp } = createTableComponent({
         contextProvider: {
            viewer: false, preview: false, binding: false, composer: true,
            vsWizard: false, vsWizardPreview: false, embedAssembly: false,
         },
         model: inBottomTab,
      });
      comp.vsInfo = tabs();

      expect(comp.getObjectTop()).toBe(50);
      await flush();

      expect(comp.model.cardTopShift).toBeUndefined();
   });

   it("publishes zero once the parent switches to top tabs", async () => {
      const { comp } = createTableComponent({ model: inBottomTab });
      const info = tabs();
      comp.vsInfo = info;
      vi.spyOn(comp as any, "getCardHeight").mockReturnValue(120);

      comp.getObjectTop();
      await flush();
      expect(comp.model.cardTopShift).toBe(80);

      (info.vsObjects[0] as any).bottomTabs = false;
      comp.getObjectTop();
      await flush();

      expect(comp.model.cardTopShift).toBe(0);
   });

   it("publishes zero once its rows fill the card", async () => {
      const { comp } = createTableComponent({ model: inBottomTab });
      comp.vsInfo = tabs();
      const cardHeight = vi.spyOn(comp as any, "getCardHeight").mockReturnValue(120);

      comp.getObjectTop();
      await flush();
      expect(comp.model.cardTopShift).toBe(80);

      cardHeight.mockReturnValue(comp.model.objectFormat.height);
      expect(comp.getObjectTop()).toBe(50);
      await flush();

      expect(comp.model.cardTopShift).toBe(0);
   });

   it("publishes zero in max mode", async () => {
      const { comp } = createTableComponent({ model: inBottomTab });
      comp.vsInfo = tabs();
      vi.spyOn(comp as any, "getCardHeight").mockReturnValue(120);

      comp.getObjectTop();
      await flush();
      comp.model.maxMode = true;
      comp.getObjectTop();
      await flush();

      expect(comp.model.cardTopShift).toBe(0);
   });

   it("publishes the latest shift when several passes run before the write lands", async () => {
      const { comp } = createTableComponent({ model: inBottomTab });
      comp.vsInfo = tabs();
      const cardHeight = vi.spyOn(comp as any, "getCardHeight").mockReturnValue(120);

      comp.getObjectTop();
      cardHeight.mockReturnValue(150);
      comp.getObjectTop();
      await flush();

      expect(comp.model.cardTopShift).toBe(50);
   });

   it("does not write after the table is destroyed", async () => {
      const { comp } = createTableComponent({ model: inBottomTab });
      comp.vsInfo = tabs();
      vi.spyOn(comp as any, "getCardHeight").mockReturnValue(120);

      comp.getObjectTop();
      (comp as any).destroyed = true;
      await flush();

      expect(comp.model.cardTopShift).toBeUndefined();
   });

   it("publishes a crosstab's shift with its hidden title taken out of the card", async () => {
      // tableHeight 200 - 20 header - 20 title = 160; the base card 94 is what objectHeight carries,
      // the crosstab draws 94 - 20 = 74 with its title hidden
      const { comp } = createCrosstabComponent({ model: { ...inBottomTab, titleVisible: false } });
      comp.vsInfo = tabs();

      expect(comp.getObjectTop()).toBe(50 + 126);
      await flush();

      expect(comp.model.cardTopShift).toBe(126);
   });

   it("publishes a freehand table's shift", async () => {
      // 300x180 at top 20, 24px title, 20px header: tableHeight 136, card 180 - 136 + 54 = 98
      const { comp } = createCalcTableComponent({ model: inBottomTab });
      comp.vsInfo = tabs();

      expect(comp.getObjectTop()).toBe(20 + 82);
      await flush();

      expect(comp.model.cardTopShift).toBe(82);
   });
});
```

The geometry comments come from the three test helpers' default models; if a helper's defaults differ, recompute the shift from them (`objectFormat.height − getCardHeight()`) and keep the assertion that the published shift equals `getObjectTop() − objectFormat.top`.

- [ ] **Step 4: Run it and watch it fail**

Run: `cd "$WT/web"` then `npx ng test portal --include="**/table/base-table.spec.ts"`
Expected: the publish tests fail (`cardTopShift` stays undefined); "top tabs", "outside the viewer" and "destroyed" pass.

- [ ] **Step 5: The table publishes its shift**

In `base-table.ts`, after `private pendingHeightUpdate = false;`:

```ts
   private pendingTopShift: number = null;
```

Replace `getObjectTop()` with:

```ts
   /**
    * Calculates the object top with the shrink to fit option in mind, shifting a shrunk table
    * down so its bottom stays flush with a bottom-tabs tab strip.
    */
   public getObjectTop(): number {
      const shift = this.getCardTopShift();
      this.publishCardTopShift(shift);
      return this.model.objectFormat.top + shift;
   }

   private getCardTopShift(): number {
      if(!this.viewer || !this.model.shrink || this.model.maxMode) {
         return 0;
      }

      if(this.model.containerType === "VSTab" && this.vsInfo) {
         const parent = this.vsInfo.vsObjects
            .find(o => o.absoluteName === this.model.container) as VSTabModel;

         if(parent && parent.bottomTabs) {
            // a card-edge measurement: the assembly's own bottom is what stays flush with the strip
            return this.model.objectFormat.height - this.getCardHeight();
         }
      }

      return 0;
   }

   /**
    * Hands the shift to vs-object-container for the strip and focus outline; deferred as in
    * getCardHeight(), and the latest value wins.
    */
   private publishCardTopShift(shift: number): void {
      if(this.pendingTopShift == null && (this.model.cardTopShift ?? 0) === shift) {
         return;
      }

      const scheduled = this.pendingTopShift != null;
      this.pendingTopShift = shift;

      if(scheduled) {
         return;
      }

      Promise.resolve().then(() => {
         const latest = this.pendingTopShift;
         this.pendingTopShift = null;

         if(!this.destroyed && (this.model.cardTopShift ?? 0) !== latest) {
            this.model.cardTopShift = latest;
            this.changeDetectorRef.markForCheck();
         }
      });
   }
```

- [ ] **Step 6: Run the table spec and the table display TL spec**

Run: `npx ng test portal --include="**/table/base-table.spec.ts"`
Expected: all pass.
Run: `npx ng run portal:test-tl --include="**/vs-table.component.display.tl.spec.ts"`
Expected: all pass (its `getObjectTop` groups now also schedule a harmless write).

- [ ] **Step 7: Write the failing container spec**

In `vs-object-container.component.spec.ts`, change the helpers import to:

```ts
import { makeComponent, makeDataTipService, makeObjectFormat, makeVSObject, makeVsInfo }
   from "./vs-object-container.component.test-helpers";
```

Append:

```ts
// A shrunk table in a bottom-tabs tab publishes how far its card draws below objectFormat.top;
// a marked table's strip and focus outline follow the card, an unmarked table's stay put.
describe("VSObjectContainer chrome on a shifted table card", () => {
   const scrollless = { scrollHeight: 600, clientHeight: 600 } as any;

   const shiftedTable = (overrides: any = {}): any => Object.assign(makeVSObject({
      absoluteName: "Table1",
      objectType: "VSTable",
      objectFormat: makeObjectFormat({ top: 40, left: 250, width: 400, height: 300 }),
      container: "Tab1",
      containerType: "VSTab",
   }), {
      vizModern: true,
      shrink: true,
      titleVisible: true,
      titleFormat: { height: 30 },
      padding: { top: 12, left: 10, bottom: 12, right: 6 },
      objectHeight: 180,
      cardTopShift: 120,
   }, overrides);

   const viewerComponent = (obj: any) => {
      const { comp } = makeComponent({
         context: { viewer: true, preview: false, binding: false } as any,
         vsObjectActions: [{ showingActions: [], toolbarActions: [] } as any],
      });
      comp.containerRef = scrollless;
      comp.vsInfo = makeVsInfo([obj]);
      return comp;
   };

   it("anchors a marked table's strip in the title lane of the shifted card", () => {
      const obj = shiftedTable();
      const comp = viewerComponent(obj);

      expect(comp.isToolbarAnchored(obj)).toBe(true);
      // card top, then the lane's inset top and the centring (30 - 24) / 2
      expect(comp.getToolbarTop(obj, 0)).toBe(40 + 120 + 12 + 3);
   });

   it("keeps the strip on objectFormat.top when nothing or zero is published", () => {
      for(const cardTopShift of [undefined, 0]) {
         const obj = shiftedTable({ cardTopShift });
         const comp = viewerComponent(obj);

         expect(comp.getToolbarTop(obj, 0)).toBe(40 + 12 + 3);
         expect(comp.getVsObjectPosition(obj, 0, true)).toBe(40);
      }
   });

   it("leaves the strip's horizontal geometry alone", () => {
      const obj = shiftedTable({ realWidth: 320 });
      const comp = viewerComponent(obj);

      expect(comp.getToolbarLeft(obj, 0)).toBe(250 + 10);
      expect(comp.getAnchoredToolbarWidth(obj)).toBe(320 - 10 - 6);
   });

   it("puts a marked table's focus outline on the shifted card", () => {
      const obj = shiftedTable();
      const comp = viewerComponent(obj);

      expect(comp.getVsObjectPosition(obj, 0, true)).toBe(40 + 120);
      expect(comp.getVsObjectPosition(obj, 0, false)).toBe(250);
      expect(comp.getVsObjectHeight(obj)).toBe(300 - 120);
   });

   it("ends the outline on the card bottom for a crosstab with a hidden title", () => {
      // objectHeight is published before the crosstab takes its hidden title off the card
      const obj = shiftedTable({
         absoluteName: "Crosstab1", objectType: "VSCrosstab", titleVisible: false,
         objectHeight: 194, cardTopShift: 126,
      });
      const comp = viewerComponent(obj);

      expect(comp.getVsObjectPosition(obj, 0, true)).toBe(40 + 126);
      expect(comp.getVsObjectPosition(obj, 0, true) + comp.getVsObjectHeight(obj)).toBe(40 + 300);
   });

   it("still moves the outline at dense, where the lane holds no strip", () => {
      const obj = shiftedTable({ titleFormat: { height: 20 } });
      const comp = viewerComponent(obj);

      expect(comp.isChromeSuppressed(obj)).toBe(true);
      expect(comp.getVsObjectPosition(obj, 0, true)).toBe(40 + 120);
   });

   it("leaves an unmarked table's floating strip and outline where they were", () => {
      const obj = shiftedTable({ vizModern: false, padding: undefined, cardTopShift: 200 });
      const comp = viewerComponent(obj);

      expect(comp.isToolbarAnchored(obj)).toBe(false);
      expect(comp.getToolbarTop(obj, 0)).toBe(40);
      expect(comp.getVsObjectPosition(obj, 0, true)).toBe(40);
      expect(comp.getVsObjectHeight(obj)).toBe(180);
   });

   it("ignores a stale shift in max mode", () => {
      const obj = shiftedTable({ maxMode: true });
      const comp = viewerComponent(obj);

      expect(comp.getToolbarTop(obj, 0)).toBe(40 + 12 + 3);
      expect(comp.getVsObjectPosition(obj, 0, true)).toBeNull();
   });

   it("keeps the outline height rule for assemblies without a shift", () => {
      const comp = viewerComponent(shiftedTable());
      const list = makeVSObject({ objectType: "VSSelectionList", ...({ objectHeight: 30 } as any) });
      const chart = makeVSObject({ objectType: "VSChart" });

      expect(comp.getVsObjectHeight(list)).toBe(30);
      expect(comp.getVsObjectHeight(chart)).toBe(100);
      expect(comp.getVsObjectHeight(shiftedTable({ cardTopShift: undefined }))).toBe(180);
   });
});
```

- [ ] **Step 8: Run it and watch it fail**

Run: `npx ng test portal --include="**/vs-object-container.component.spec.ts"`
Expected: compilation fails on `getVsObjectHeight`.

- [ ] **Step 9: The container reads the shift for marked tables**

In `vs-object-container.component.ts`, after `getLaneInset` (`BaseTableModel` is already imported):

```ts
   /**
    * How far a marked table's card draws below objectFormat.top, as the table published it; zero
    * in max mode, where the card fills the view.
    */
   private static getCardTopShift(object: VSObjectModel): number {
      return !object.vizModern || (<any> object).maxMode ? 0 :
         (<BaseTableModel> object).cardTopShift || 0;
   }
```

In `getToolbarTop`, change the anchored branch's return to:

```ts
         // a shrunk table in bottom tabs draws its card below objectFormat.top
         return object.objectFormat.top + VSObjectContainer.getCardTopShift(object) +
            VSObjectContainer.getLaneInset(object).top + centring;
```

Leave the floating branch (`let top = object.objectFormat.top;` onwards) as it is.

Replace the table branch of `getVsObjectPosition`:

```ts
      if(obj.objectType === "VSCalcTable" || obj.objectType === "VSTable" ||
         obj.objectType === "VSCrosstab")
      {
         if(!(this.viewer || this.embeddedVS) || (<any> obj).maxMode) {
            return null;
         }

         return top ? obj?.objectFormat?.top + VSObjectContainer.getCardTopShift(obj)
            : obj?.objectFormat?.left;
      }
```

After `getActualWidth`:

```ts
   /**
    * The focus outline's height; a shifted card keeps its bottom on the design bottom.
    */
   getVsObjectHeight(vsObject: VSObjectModel): number {
      const shift = VSObjectContainer.getCardTopShift(vsObject);

      return shift > 0 ? vsObject.objectFormat.height - shift
         : (<any> vsObject).objectHeight || vsObject.objectFormat.height;
   }
```

In `vs-object-container.component.html`, replace both `[style.height.px]="vsObject.objectHeight || vsObject.objectFormat.height"` (lines 38 and 389) with:

```html
        [style.height.px]="getVsObjectHeight(vsObject)"
```

Keep each line's existing indentation.

- [ ] **Step 10: Run the specs**

Run: `npx ng test portal --include="**/vs-object-container.component.spec.ts"`
Run: `npx ng test portal --include="**/table/base-table.spec.ts"`
Run: `npx ng test portal --include="**/mini-toolbar*.spec.ts"`
Expected: all pass.

- [ ] **Step 11: Commit**

```bash
git -C "$WT" branch --show-current
git -C "$WT" add web/projects/portal/src/app/vsobjects/model/base-table-model.ts web/projects/portal/src/app/vsobjects/objects/table/base-table.ts web/projects/portal/src/app/vsobjects/objects/table/base-table.spec.ts web/projects/portal/src/app/vsobjects/objects/vs-object-container.component.ts web/projects/portal/src/app/vsobjects/objects/vs-object-container.component.html web/projects/portal/src/app/vsobjects/objects/vs-object-container.component.spec.ts
git -C "$WT" commit -m "Place a marked table's strip and focus outline on its shifted card" -m "Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

`package-lock.json` must not change from `npm ci`; if `git -C "$WT" status` shows it modified, restore it with `git -C "$WT" checkout -- web/package-lock.json`.

---

### Task 8: Whole-branch verification, doc amendments and the manual checks

Spec §7, §9.

**Files:**
- Modify: `docs/superpowers/specs/lookfeel/2026-10-05-selection-container-density-design.md` (§10's hidden-title and composer-resize bullets)
- Modify: `docs/superpowers/specs/lookfeel/2026-09-23-density-padding-design.md` (§4.3's floor bullet, D5's follow sentence)

- [ ] **Step 1: Amend the earlier specs, dated in place**

In the container spec §10, after the bullet on a hidden container title and after the bullet on the composer's resize deriving list height from `defh`, add a line each: "Fixed 2026-10-12 for marked containers / lists: see `2026-10-12-density-deferred-fixes-design.md`." In the density padding spec, after §4.3's floor bullet add: "Amended 2026-10-12: the floor is what `rowHeight` leaves out — the DEFAULT tier, or the tier in force under an author padding (`VSDensityDefaults.excludedCellPaddingHeight`)." After D5's sentence on the follow checkbox add: "Amended 2026-10-12: checking it also re-seeds the DEFAULT tier (`resetCellPadding`)."

- [ ] **Step 2: Full core suite**

Run: `./mvnw -q test -pl core -Dsurefire.failIfNoSpecifiedTests=false`
Expected: all pass, except tests known to fail on Windows or flake (`RelFaultInjectionTest`, `SetTableLensMergedTableFailureTest`); re-run a flaky one on its own before calling it unrelated. Record the count from `core/target/surefire-reports`.

- [ ] **Step 3: Portal project**

Run: `cd "$WT/web"` then `npx ng test portal 2>&1 | sed 's/\x1b\[[0-9;]*m//g' | grep -E "Test Files|^ *Tests +[0-9]"`
Expected: all pass.

- [ ] **Step 4: Commit the docs**

```bash
git -C "$WT" branch --show-current
git -C "$WT" add docs/superpowers/specs/lookfeel/2026-10-05-selection-container-density-design.md docs/superpowers/specs/lookfeel/2026-09-23-density-padding-design.md
git -C "$WT" commit -m "Record the density follow-up fixes in the earlier specs" -m "Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

- [ ] **Step 5: Before captures (the user's server, before any rebuild)**

On the running server (stack-tip build, without this branch), export `DFX3 SEL Legacy Hidden Title`, `DFX3 SEL Hidden Title 210` and `DFX3 CON Hidden Title` as PDF, PNG, PowerPoint (Data Size: Match Layout) and HTML into `$WT/.superpowers/baselines/density-deferred-fixes/before/`. The item 3 investigation's exports in the session scratchpad's `item3/` folder are the same captures if they are still there.

- [ ] **Step 6: Build a server with this branch (the user)**

To keep #6798's open manual checks valid on the same server, build from a throwaway integration branch rather than from `bug-density-deferred-fixes` alone: in a separate worktree, `git switch -c tmp-density-fixes-on-stack origin/feature-form-control-density`, then `git cherry-pick` this branch's commits after `c66a5aca51`. Building from `bug-density-deferred-fixes` directly also works but drops #6484, #6561 and #6798 from the server. Never push the integration branch.

- [ ] **Step 7: Manual checks**

Walk `density-deferred-fixes-repro.md` (enterprise root) item by item at comfortable, then the compact and dense notes:
- Item 1: the Height field, popup, Dropdown→List and container expand show the expected values; a fresh list's Height reads 5; Align leaves the Height alone; `DFX1 ListHeight Legacy` behaves as recorded.
- Item 2: steps 4–10 show the expected values, including the 16px dense row and the legacy-variant 24/26.
- Item 3: the PDF/PNG/PPT/HTML exports of `DFX3 SEL Hidden Title 210` match the viewer with Product present; `DFX3 CON Hidden Title`'s HTML slider sits with the lists; the legacy fixture's after export is identical to Step 5's before capture.
- Item 4: the pill sits in the title lane on `DFX4 Shrink Table Bottom Tabs` (and Compact); the Tab-key outline sits on the card; `DFX4 Shrink Table Bottom Tabs Legacy` is unchanged.

Record the results in the checks section of the PR draft.
