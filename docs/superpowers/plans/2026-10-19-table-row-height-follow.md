# Table Row-Height Follow Density Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a "Follow the default density" checkbox under Cell Height in the composer's Set Cell Size dialog for marked tables, recording row-height authorship explicitly.

**Architecture:** The dialog sends a nullable `followsDensity` with its existing `changeCellSize` event. The server reads it only for marked tables: it clears or sets `userDataRowHeight` / `userHeaderRowHeight` and fixes up the stored heights. A missing value keeps today's behaviour, so the viewer and stale clients are unaffected. The table model says whether each row type is pinned, so the dialog starts in the right state. Row height is already resolved when the table is drawn, so nothing is seeded.

**Tech Stack:** Java 21, Spring, Immutables, JUnit 5 + Mockito (core); Angular 21, reactive forms, Vitest via `ng test` (portal).

**Spec:** `docs/superpowers/specs/lookfeel/2026-10-19-table-row-height-follow-design.md` (D1–D10). Read it before any task.

## Global Constraints

- Composer only. The box shows only when the table component's `viewer` getter is false. That getter is `context.viewer || context.preview` (`abstract-vsobject.component.ts:71-73`). The viewer and preview send exactly today's event, with no `followsDensity`.
- Mark, not gate: on the client, `model.vizModern`; on the server, `info.getVizMark() != null`. An unmarked table ignores `followsDensity` entirely.
- A null `followsDensity` means today's behaviour: write the height and set the flag.
- Label: reuse `_#(composer.vs.followDefaultDensity)` ("Follow the default density"). No new string.
- No comments in `.html` templates. Code comments are a short clause, with no references to specs, tickets or PRs.
- Never call `TableDataVSAssemblyInfo.resetRowHeights()` here: it also resets the title height.
- Java tests that reach Spring beans carry `@ExtendWith(SpringExtension.class)`, `@ContextConfiguration(classes = BaseTestConfiguration.class, initializers = ConfigurationContextInitializer.class)`, `@SreeHome` and `@Tag("core")`. Run every new or changed test class on its own.
- Git: run `git add` and `git commit` as separate commands, never chained with `&&`. End every commit message with `Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>`.
- The worktree is `C:/Users/Franky/AppData/Local/Temp/claude/E--StyleBI-stylebi-enterprise/cfc7bcb9-7d26-4219-93c9-afb8efd3758f/scratchpad/table-row-height-follow`, on branch `feature-table-row-height-follow`. Run every command from there, never from the shared checkout.
- Stored-terms tiers used by the tests: the rendered tier less the seeded cell padding top + bottom (dense 3+3, compact 4+4, comfortable 6+6).

  | tier | data row | header row |
  |---|---|---|
  | dense | 14 | 16 |
  | compact | 16 | 18 |
  | comfortable | 16 | 18 |

## Review Focus

1. **A freehand cell spanning header and data rows** counts as the type of its first row (D3). Covered by the Task 3 test `a span cell starting in the header reads the header pin`.
2. **Composer preview** must hide the box, not just the viewer. Covered by the Task 3 test `hides the box in preview`.
3. **An unticked OK on a header row whose flag is already set** must not rewrite the other header rows. Covered by the Task 1 test `anUntickedOkOnAPinnedHeaderWritesOnlyTheClickedRow`.
4. **A lens with no row heights yet**, when the other header rows are pinned, must fall back to the tier rather than writing −1. Covered by the Task 1 test `anUntickedOkWithNoLensRowsFallsBackToTheTier`.
5. **A ticked OK on a data cell** must leave pinned header rows pinned, and the other way round. Covered by the Task 1 test `aTickedOkOnADataCellLeavesHeaderRowsAlone`.

---

### Task 1: Server — honour `followsDensity` on Set Cell Size

**Files:**
- Modify: `core/src/main/java/inetsoft/web/composer/vs/objects/event/ResizeTableCellEvent.java`
- Modify: `core/src/main/java/inetsoft/uql/viewsheet/internal/TableDataVSAssemblyInfo.java` (beside `isUserDataRowHeight` at :906)
- Modify: `core/src/main/java/inetsoft/web/composer/vs/objects/controller/ComposerVSTableService.java` (`resizeTableCell` at :425-433; new helpers near `changeRowHeight` at :929-980)
- Create: `core/src/test/java/inetsoft/web/composer/vs/objects/controller/ComposerVSTableServiceRowHeightFollowTest.java`

**Interfaces:**
- Produces:
  - `@Nullable Boolean followsDensity()` on `ResizeTableCellEvent` (`javax.annotation.Nullable`, the convention in `web/admin/cache/CacheProperty.java`). The JSON property is `followsDensity`; Task 3 sends it.
  - `public boolean isDataRowHeightPinned()` on `TableDataVSAssemblyInfo`: `userDataRowHeight || dataRowHeight != AssetUtil.defh`, reading the field, not the map-aware `getDataRowHeight()`. This is the negation of the render rule's data condition (spec D4). Task 2 reads it.

- [ ] **Step 1: Write the failing tests**

Model the class on `ComposerVSTableServiceColumnWidthInsetTest`: the same Spring annotations, the same mocked `RuntimeViewsheet` / `ViewsheetSandbox` / `ViewsheetService`, and the same `new ComposerVSTableService(mock(CoreLifecycleService.class), null, null, null, null, viewsheetService, null, null)`. Fixture helpers:
- `marked(info, mode)`:
  - `SreeEnv.setProperty("viewsheet.density", mode)`
  - `info.setVizMark(VizMark.MODERN_LIGHT)`
  - `info.seedChromeDefaults(VizContext.of(info))`
  - clear the property in `@AfterEach`
- **Data-cell tests** use a `TableVSAssembly` over a 2-row `DefaultTableLens`.
- **Header tests** use a `CalcTableVSAssembly(vs, "Calc1")` with `info.setHeaderRowCount(2)` over a 3-row lens. Call `lens.initTableGrid(info)` unless the test says otherwise.
- **Events:** built with `ImmutableResizeTableCellEvent.builder()` and `ImmutableResizeTableRowEvent.builder()`. Set `row`, `rowHeight`, `rowSpan = 1`, `header`, `start = 0`, `rowCount` and `assemblyName`. The column event resizes column 0 to 120.

```java
// compact unless stated; "pinned" = flag set with the given stored heights beforehand
aTickedOkLeavesAFollowingRowAlone        // data, flag clear, TRUE
   -> !isUserDataRowHeight(), getDataRowHeight() == AssetUtil.defh, getColumnWidthValue(0) == 120
aTickedOkHandsAPinnedDataRowBack         // data pinned at 30, TRUE
   -> !isUserDataRowHeight(), getDataRowHeight() == AssetUtil.defh
aTickedOkHandsBackADataHeightStoredWithoutTheFlag // flag clear, setDataRowHeight(30), TRUE
   -> isDataRowHeightPinned() before; after: !isDataRowHeightPinned(), getDataRowHeight() == defh
aTickedOkHandsPinnedHeaderRowsBack       // header pinned {30, 30}, TRUE on row 0
   -> !isUserHeaderRowHeight(), getHeaderRowHeight(0) == defh, getHeaderRowHeight(1) == defh
aTickedOkOnADataCellLeavesHeaderRowsAlone // header pinned {30, 30}, data cell TRUE
   -> isUserHeaderRowHeight(), getHeaderRowHeight(0) == 30, getHeaderRowHeight(1) == 30
anUntickedOkPinsTheShownHeight           // data, flag clear, FALSE, rowHeight 16
   -> isUserDataRowHeight(), getDataRowHeight() == 16
@ParameterizedTest @CsvSource({"dense,16", "compact,18", "comfortable,18"})
anUntickedOkOnOneHeaderRowKeepsTheOthers(mode, tier) // header flag clear, FALSE on row 0, rowHeight 25
   -> isUserHeaderRowHeight(), getHeaderRowHeight(0) == 25, getHeaderRowHeight(1) == tier
anUntickedOkOnAPinnedHeaderWritesOnlyTheClickedRow  // header pinned {30, 40}, FALSE on row 0, 25
   -> getHeaderRowHeight(0) == 25, getHeaderRowHeight(1) == 40
anUntickedOkWithNoLensRowsFallsBackToTheTier         // as above but flag clear, no initTableGrid
   -> getHeaderRowHeight(1) == 18
aMissingValueKeepsTodaysPin              // marked, data, flag clear, followsDensity unset, 16
   -> isUserDataRowHeight(), getDataRowHeight() == 16
anUnmarkedTableIgnoresTheValue           // no mark, data, flag clear, TRUE, 16
   -> isUserDataRowHeight(), getDataRowHeight() == 16
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./mvnw -o -q -pl core test -Dtest=ComposerVSTableServiceRowHeightFollowTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: compile failure, because `followsDensity` is not on the builder. If `2.1.0-SNAPSHOT` upstream modules are missing from `~/.m2`, add `-am`.

- [ ] **Step 3: Add `@Nullable Boolean followsDensity()` to `ResizeTableCellEvent`, and `isDataRowHeightPinned()` to `TableDataVSAssemblyInfo`**

- [ ] **Step 4: Route `resizeTableCell` through `private void changeCellHeight(RuntimeViewsheet rvs, ResizeTableRowEvent event, Boolean followsDensity) throws Exception`**

Replace the call `changeRowHeight(rvs, event.rowEvent(), false)` in `resizeTableCell` with `changeCellHeight(rvs, event.rowEvent(), event.followsDensity())`. Inside `changeCellHeight`, resolve the info and lens the way `changeRowHeight` does, then branch:
- **`followsDensity == null` or `info.getVizMark() == null`:** call `changeRowHeight(rvs, event, false)`, which is today's behaviour.
- **`TRUE`:** call `followDensityRowHeight(info, event.header(), lens)`. Do not call `changeRowHeight`.
- **`FALSE`:**
  - if `event.header() && !info.isUserHeaderRowHeight()`, first call `pinHeaderRows(info, lens, headerRowCount)`, where `headerRowCount` comes from the same calc-table-vs-lens rule `changeRowHeight` uses
  - then call `changeRowHeight(rvs, event, false)`

- [ ] **Step 5: Implement the two helpers in `ComposerVSTableService`**

- `static void followDensityRowHeight(TableDataVSAssemblyInfo info, boolean header, VSTableLens lens)`:
  - **header**, only when `isUserHeaderRowHeight()`: clear the flag and `setHeaderRowHeights(new int[] { AssetUtil.defh })`, as `VSTableLens.java:1797-1801` does
  - **data**, only when `isDataRowHeightPinned()`: clear the flag and `setDataRowHeight(AssetUtil.defh)`
  - if anything changed and `lens != null`, call `lens.setRowHeights(null)`
- `static void pinHeaderRows(TableDataVSAssemblyInfo info, VSTableLens lens, int headerRowCount)`:
  - write a fresh `int[headerRowCount]` with `setHeaderRowHeights`
  - entry `i` is `lens.getRowHeight(i)` when the lens is non-null and that is `> 0`
  - otherwise entry `i` is `VSDensityDefaults.headerRowHeight(VizContext.of(info), info)`
  - `changeRowHeight` then overwrites the clicked rows. Writing the whole array avoids `setHeaderRowHeight`'s index-out-of-bounds when the stored array is still `{ defh }`

- [ ] **Step 6: Run the tests to verify they pass**

Run: `./mvnw -o -q -pl core test -Dtest=ComposerVSTableServiceRowHeightFollowTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: `Tests run: 13, Failures: 0, Errors: 0`. That is 11 methods, one of them parameterized over 3 densities.

- [ ] **Step 7: Run the neighbouring classes**

Run: `./mvnw -o -q -pl core test "-Dtest=ComposerVSTableService*Test,DensityRowHeightInvariantTest" -Dsurefire.failIfNoSpecifiedTests=false`
Expected: 0 failures.

- [ ] **Step 8: Commit**

```bash
git add core/src/main/java/inetsoft/web/composer/vs/objects/event/ResizeTableCellEvent.java core/src/main/java/inetsoft/uql/viewsheet/internal/TableDataVSAssemblyInfo.java core/src/main/java/inetsoft/web/composer/vs/objects/controller/ComposerVSTableService.java core/src/test/java/inetsoft/web/composer/vs/objects/controller/ComposerVSTableServiceRowHeightFollowTest.java
```
```bash
git commit -m "Honour follow-the-default-density on Set Cell Size for marked tables" -m "Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

### Task 2: Table model says whether each row type is pinned

**Files:**
- Modify: `core/src/main/java/inetsoft/web/viewsheet/model/table/BaseTableModel.java` (constructor near :88; getters near :129; fields near :255)
- Test: `core/src/test/java/inetsoft/web/viewsheet/model/VSTableModelTest.java`

**Interfaces:**
- Consumes: `TableDataVSAssemblyInfo.isDataRowHeightPinned()` (Task 1).
- Produces: `boolean isDataRowHeightPinned()` (from `tinfo.isDataRowHeightPinned()`) and `boolean isHeaderRowHeightPinned()` (from `tinfo.isUserHeaderRowHeight()`) on `BaseTableModel`, serialized as `dataRowHeightPinned` and `headerRowHeightPinned`. Task 3 reads them.

- [ ] **Step 1: Write the failing test `modelSaysWhichRowTypesArePinned` in `VSTableModelTest`**

Use the class's existing setup and build each model through `VSTableModelFactory`. Three cases:
- `setUserDataRowHeight(true)`, `setUserHeaderRowHeight(false)`: `isDataRowHeightPinned()` true, `isHeaderRowHeightPinned()` false.
- Both flags false, data height `defh`: both getters false.
- Both flags false, `setDataRowHeight(30)`: `isDataRowHeightPinned()` true. This is the wizard-sync case.

- [ ] **Step 2: Run it to verify it fails**

Run: `./mvnw -o -q -pl core test -Dtest=VSTableModelTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: compile failure, because the getters don't exist.

- [ ] **Step 3: Add the two fields, set them from `tinfo` in the constructor beside `shrink`, and add the getters**

- [ ] **Step 4: Run it to verify it passes**

Run: the Step 2 command. Expected: 0 failures.

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/inetsoft/web/viewsheet/model/table/BaseTableModel.java core/src/test/java/inetsoft/web/viewsheet/model/VSTableModelTest.java
```
```bash
git commit -m "Tell the browser which table row types are pinned" -m "Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

### Task 3: The checkbox in the Resize Cell dialog

**Files:**
- Modify: `web/projects/portal/src/app/vsobjects/objects/table/table-cell-resize-dialog/table-cell-resize-dialog.component.ts` and `.html`
- Create: `web/projects/portal/src/app/vsobjects/objects/table/table-cell-resize-dialog/table-cell-resize-dialog.component.spec.ts`
- Modify: `web/projects/portal/src/app/vsobjects/event/table/resize-table-cell-event.ts`
- Modify: `web/projects/portal/src/app/vsobjects/model/base-table-model.ts` (near `explicitTableWidth?` at :45)
- Modify: `web/projects/portal/src/app/vsobjects/objects/table/base-table.ts` (`openCellSizeDialog` at :2318, `updateCellSize` at :2344)
- Test: `web/projects/portal/src/app/vsobjects/objects/table/base-table.spec.ts`

**Interfaces:**
- Consumes: the `followsDensity` JSON property (Task 1); `dataRowHeightPinned` and `headerRowHeightPinned` on the model (Task 2).
- Produces:
  - `TableCellResizeDialogComponent`: `@Input() followsDensity: boolean | null = null`, where null hides the box
  - `TableCellResizeDialogResult`: gains `followsDensity?: boolean`, left undefined when the box is hidden
  - `ResizeTableCellEvent`: a third constructor parameter `public followsDensity?: boolean`
  - `BaseTableModel` (TS): `dataRowHeightPinned?: boolean`, `headerRowHeightPinned?: boolean`
  - `BaseTable`: `protected cellHeightFollowsDensity(cell: BaseTableCellModel): boolean | null`

- [ ] **Step 0: Install dependencies in the worktree (once)**

Run: `cd web`, then `npm ci`. A junctioned `node_modules` breaks Vitest's path resolution, so a real install is required.

- [ ] **Step 1: Write the failing dialog spec**

```ts
it("hides the box when followsDensity is null")
   // no #cellHeightFollowsDensity; cellHeight enabled; ok() emits followsDensity === undefined
it("starts ticked with the height greyed and reports true")
   // input true, height 16: cellHeight disabled with value 16; ok() emits { width, height: 16, followsDensity: true }
it("unticking makes the shown height editable and reports false")
   // input true, then followsDensity control -> false: cellHeight enabled, value still 16;
   // ok() emits followsDensity: false, height 16
it("starts unticked with the height editable")
   // input false: cellHeight enabled; checkbox unchecked
```

- [ ] **Step 2: Write the failing `base-table.spec.ts` cases** in a new `describe("BaseTable cell height follow")`

The composer context is `{ viewer: false, preview: false, binding: false, composer: true, vsWizard: false, vsWizardPreview: false, embedAssembly: false }`. Models set `vizModern`, `headerRowCount`, `dataRowHeightPinned` and `headerRowHeightPinned`.

```ts
it("a data cell follows while data rows are not pinned")          // -> true; dataRowHeightPinned -> false
it("a header cell reads the header pin")                          // row 0, headerRowCount 1
it("a span cell starting in the header reads the header pin")     // row 0, rowSpan 3, headerRowCount 2
it("hides the box in the viewer")                                // default (viewer) context -> null
it("hides the box in preview")                                   // preview: true -> null
it("hides the box on an unmarked table")                         // vizModern false -> null
it("sends followsDensity with the cell size event")
   // (comp as any).updateCellSize({ width: 96, height: 16, followsDensity: true }, cell)
   // -> viewsheetClient.sendEvent("/events/composer/viewsheet/table/changeCellSize",
   //       objectContaining({ followsDensity: true }))
it("sends no followsDensity when the dialog had no box")
   // result without followsDensity -> the event's followsDensity is undefined
```

- [ ] **Step 3: Run both specs to verify they fail**

Run: `npx ng test portal --include="**/table-cell-resize-dialog.component.spec.ts"`, then `npx ng test portal --include="**/base-table.spec.ts"` (both from `web`).
Expected: FAIL on the missing input, method and fields.

- [ ] **Step 4: Implement the dialog**

- Add a `followsDensity` form control only when the input is non-null.
- While it is true, disable `cellHeight` and keep its value. Enabling the control again restores editing at the same value.
- `ok()` adds `followsDensity` to the result only when the box is shown.
- **Template:** under the Cell Height stepper and its error alert, inside the same column, add `@if (followsDensity != null)` with a `form-check mt-1` block. It holds `<input type="checkbox" class="form-check-input" id="cellHeightFollowsDensity" formControlName="followsDensity">` and its label `_#(composer.vs.followDefaultDensity)`, matching `size-position-pane.component.html:92-101`.

- [ ] **Step 5: Implement the event, the model fields and `BaseTable`**

- `ResizeTableCellEvent` gains the optional third parameter.
- **`cellHeightFollowsDensity(cell)`:**
  - returns `null` when `this.viewer || !this.model.vizModern`
  - otherwise returns `cell.row < this.model.headerRowCount ? !this.model.headerRowHeightPinned : !this.model.dataRowHeightPinned`
- **`openCellSizeDialog`:** sets `dialog.followsDensity = this.cellHeightFollowsDensity(cell)`.
- **`updateCellSize`:** passes `result.followsDensity` as the event's third argument.

- [ ] **Step 6: Run both specs to verify they pass**

Run: the Step 3 commands. Expected: all cases pass; base-table's existing cases still pass.

- [ ] **Step 7: Commit**

```bash
git add web/projects/portal/src/app/vsobjects/objects/table/table-cell-resize-dialog web/projects/portal/src/app/vsobjects/event/table/resize-table-cell-event.ts web/projects/portal/src/app/vsobjects/model/base-table-model.ts web/projects/portal/src/app/vsobjects/objects/table/base-table.ts web/projects/portal/src/app/vsobjects/objects/table/base-table.spec.ts
```
```bash
git commit -m "Add follow-the-default-density to the composer's Set Cell Size dialog" -m "Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

### Task 4: Docs, full suites and manual checks

**Files:**
- Modify: `docs/superpowers/specs/lookfeel/chart-card-anchored-strip-lane-decisions.md`:
  - decision 5's "**Still open:**" line (:281)
  - the "Still open" list's last bullet (:446-447)
- Modify: `docs/superpowers/specs/lookfeel/chart-card-roadmap.md`, adding a Done-table row after the L′ row at :1581
- Create: `docs/superpowers/plans/2026-10-19-table-row-height-follow-manual-checks.md`

- [ ] **Step 1: Amend the decisions and the roadmap**

- **Decision 5's open question:** record it as answered for table row height. The box starts unticked when the flag is set; link the design's D4.
- **Done-table row:** names the step and links the design.
- No other edits.

- [ ] **Step 2: Run the full core suite**

Run: `./mvnw -o -pl core test`
Expected: 0 failures, or only known timing flakes (`XSwapperStaleSwapFileMapTest`, `RelFaultInjectionTest`, `StoredWildcardReparseTest.reparseTimesOut`, `ChartInsetCssOverrideTest`). Each flake must pass when rerun alone with `-Dtest=<Class>`.

- [ ] **Step 3: Run the portal suite**

Run (from `web`): `npm run test:portal 2>&1 | sed 's/\x1b\[[0-9;]*m//g' | grep -E "Test Files|^ *Tests +[0-9]"`
Expected: two summary blocks, portal then em, both with 0 failed.

- [ ] **Step 4: Write the manual-check checklist**

Each check gets an ID, the steps and the expected result. Run them in the composer on a server built from this branch, with org density compact. Use a marked table, a crosstab with 2 column-header rows and a freehand table.

| ID | Check | Expected |
|---|---|---|
| MT-1 | Set Cell Size on a never-resized data cell, change only the width, OK. Then switch the dashboard density to comfortable. | Box ticked, height greyed at 16. Width changes. Rows go to 28 after the switch. |
| MT-2 | Drag a data row border, then reopen Set Cell Size. Tick the box, OK. | Box unticked and editable before; rows back on the tier after. |
| MT-3 | Untick and press OK without typing. Then change the density. | The row doesn't move on OK, and no longer moves on the density change. |
| MT-4 | Crosstab header cell: untick, type 30, OK. | The clicked header row changes; the other header row keeps its exact height. |
| MT-5 | Tick the box on that crosstab's header cell, OK. | Every header row returns to the tier. |
| MT-6 | Freehand table: MT-1 and MT-4. | Same results. |
| MT-7 | An unmarked (legacy) table. | No box; the dialog behaves as before. |
| MT-8 | The viewer, and the composer preview. | No box; the dialog behaves as before. |
| MT-9 | Undo after MT-2's tick. | One undo step restores the pinned height. |
| MT-10 | PDF export after MT-1. | Exported rows match the composer. |
| MT-11 | A wrapped-text table: tick on a pinned data row. | Wrap growth switches to whole-row steps. This is expected, not a regression. |

- [ ] **Step 5: Commit**

```bash
git add docs/superpowers/specs/lookfeel/chart-card-anchored-strip-lane-decisions.md docs/superpowers/specs/lookfeel/chart-card-roadmap.md docs/superpowers/plans/2026-10-19-table-row-height-follow-manual-checks.md
```
```bash
git commit -m "Record the table row-height follow box in the density decisions and roadmap" -m "Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

- [ ] **Step 6: Hand the manual checks to the user**

The user builds the server from this branch and runs MT-1 to MT-11. Results go in the checklist's Results table.
