# Density follow-ups — list rows, author cell padding, hidden container titles, shifted table chrome

**Date:** 2026-10-12
**Verified against:** community `bug-density-deferred-fixes`, cut from `feature-selection-container-density`
@ `c66a5aca51` (#6390). Every code claim below was checked against that commit's files.
**Base:** #6390, because items 1 and 3 change code it introduced. Items 2 and 4 touch no file #6484, #6561 or
#6798 changes except `VSDensityDefaults` and `ComposerObjectService`, in hunks clear of theirs.

## 1. Why

Four defects in density behaviour that has already merged or is in review were deferred by earlier PRs. Each was
reproduced on a live server and measured before this design was written; the evidence, fixtures and click-level
steps are in the enterprise root's `density-deferred-fixes-repro.md`.

1. A composer resize of a marked selection list stores its row count in legacy 20px rows. A 230px drag at
   comfortable stores 10 rows where 6 fit, so Dropdown→List rebuilds a 342px box, the dropdown opens 280px tall and
   a container expand grows to 342. A fresh marked list already disagrees with its own box: it stores 6 rows while
   its seeded box holds 5. *(Amended 2026-10-13: the 6 against 5 is legacy parity, not a defect — see D1.)*
2. A marked table with an author cell padding drifts its rows 2–6px after a density change, and 6–12px when the
   padding was set before Modernize. A 1px padding moved from comfortable to dense draws a 10px row that clips its
   text.
3. A marked selection container with its title hidden exports its children one title lane lower than the viewer
   draws them, frames an empty band where the title would be, and drops or zero-heights the bottom child when the
   box is tight.
4. In the viewer, the anchored strip and the focus outline of a shrunk table in a bottom-tabs tab float above the
   card, by as much as 152px.

## 2. Decisions

### D1 — A list's rows are what its box holds, at its own density

A marked list's `listHeight` is the number of whole rows its box holds under its title lane and inside its card
inset, at its effective cell height, never fewer than one. It is written by a composer resize that changes the
height~~, by the density size seed (so a fresh, Modernized, re-seeded or Reverted box stores the rows it holds), and
by ticking "Follow the default density" in the list and tree dialogs~~ *(seed and tick withdrawn 2026-10-13; see
the amendment below)*. It is never written while the design show
type is Dropdown, because a dropdown's box is its title lane and its rows are the author's Height. Align,
Distribute and a width-only drag resend an unchanged height and leave the rows alone. An author-sized box keeps its
rows through a density change: rows stay the same at every tier, and the popup and Dropdown→List rebuild them at
the new density.

Unmarked lists keep the legacy arithmetic exactly, including rewriting the rows on Align. ~~Revert re-boxes a list
to the legacy 100×120 and stores the 5 rows that box holds, so a reverted list round-trips to its own box; a list
that was never marked still stores 6.~~ *(Withdrawn 2026-10-13: Revert re-boxes a list to 100×120 and leaves its
rows alone.)*

**Amended 2026-10-13.** The density seed's and the tick's row writes are withdrawn; the composer resize refit stays.
A list's `listHeight` is the dropdown's own row count — the dialog's Height, editable only in Dropdown mode, default
6, as legacy's dropdown shows 6 while its 100×120 box shows 5. The density size seed and "Follow the default
density" never write it: a fresh marked list keeps 6, and Modernize, a density change, `reseedAfterRestore` and
Revert leave it alone. The rule for a marked list or tree (`getVizMark() != null`) wherever its List box is rebuilt —
Dropdown→List in the list and tree property dialogs, a selection container's expand, a move into a container, a
device layout:

- **The author never sized the box** (`!isUserSize()`): the List box is the density box for its tier,
  `VSDensityDefaults.selectionSize(VizContext.of(info)).height` (202 / 170 / 136), whatever its rows, cell height or
  title visibility. A mode round trip cannot resize it.
- **The author sized the box** (`isUserSize()`): it is rebuilt from rows, `title + listHeight × effective cell +
  inset`; the composer resize refit keeps those rows what the box holds.

The rule keys on `isUserSize()`, not `followsDensitySize()`, because a dropdown's box is its title lane and never a
recognised tier size. Unmarked lists and trees are unchanged from `c66a5aca51`.

### D2 — A row under an author cell padding sits on the tier in force

The stored row height leaves out "the padding render adds back". While the density owns the padding that is the
DEFAULT tier, as today — it is what the cell draws, so a seed written under another tier still renders this one.
Once an author padding exists the DEFAULT tier stops following the density, so the row leaves out the current
tier's padding instead: the row is this tier's content box with the author's padding on top. One rule,
`VSDensityDefaults.excludedCellPaddingHeight`, serves both the stored side and `VSTableLens.getRowPadding`'s floor,
so they cannot split. Tables without an author padding, and unmarked tables, are unchanged.

The DEFAULT tier stays frozen under an author padding (the seed's `!isUserCellPadding()` guard is kept): nothing
reads it there any more, and the org density path re-seeds nothing, which is why the fix belongs in the row math.

### D3 — Following the density again re-seeds the cell padding

Ticking "Follow the default density" on Cell Padding clears the author value and re-seeds the DEFAULT tier from the
tier in force (`TableDataVSAssemblyInfo.resetCellPadding(VizContext)`), in the table, crosstab and freehand dialogs.
It runs on every OK of a following table, the same as the card inset's `resetPadding`; for a table whose seed went
stale through an org change it moves the text inset to the current tier, and the row stays on the tier.

### D4 — A marked container's hidden title takes no lane in export

For a marked container (`getVizMark() != null`) with its title hidden, every export draws what the viewer draws:
children and collapsed out-selection rows start at the container top, the frame spans the whole box, and the stored
child stack (`layout()`) drops the lane too. One rule on `CurrentSelectionVSAssemblyInfo` —
`isTitleLaneDropped()` and `getTitleLaneHeight()` — feeds `getChildTop`, the PDF/PNG/PPT frame and rows, the HTML
list shift and the match-layout inclusion check, which must accept a first child at gap 0. Unmarked containers and
visible titles are unchanged in every format. The composer canvas is not changed; it still draws the lane, so for
these containers the saved child y values no longer match the composer's drawing, which nothing reads.

### D5 — Marked tables' chrome follows the shifted card

A shrunk table in a bottom-tabs tab publishes how far the viewer draws its card below `objectFormat.top`
(`cardTopShift`, client-only, deferred like `objectHeight`), and `vs-object-container` places the anchored strip
and the focus outline (keyboard focus and the mobile selection border) from it. Only marked tables
(`model.vizModern`) read it; the legacy floating strip and legacy outline keep today's offset. Table, crosstab and
freehand table share the path.

## 3. Item 1 — list rows

- `SelectionBaseVSAssemblyInfo.getFittingListHeight(int height)`, the inverse of `getContainedListHeight()`:
  marked `max(1, (H − title − inset.top − inset.bottom) / effectiveCell)`, unmarked `(H − title) / defh` exactly.
  `fitListHeightToBox()` applies it to the current box unless the show type is Dropdown.
- The size rule in `seedChromeDefaults` calls `fitListHeightToBox()` right after it sets the tier box, which covers
  creation, Modernize, Revert, a dashboard density change and `reseedAfterRestore` with no new seed points. Trees
  inherit it.
- `ComposerObjectService.resizeObject`: a marked list refits only when the height changed; an unmarked list keeps
  `(H − title) / defh` under its existing `H > title` guard. The single drag, Arrange (multiresize) and
  `resizeObjectTitle` all reach this one place.
- `SelectionListPropertyDialogService` and `SelectionTreePropertyDialogService`: when Size & Position's "Follow the
  default density" is ticked, fit the rows last, after the dialog's own `setListHeight(pane value)`.

Whole rows mean some heights do not round-trip exactly: 160 at dense stores 6 rows and rebuilds 156.

**Amended 2026-10-13.** The second and fourth bullets are withdrawn: `seedChromeDefaults` no longer calls
`fitListHeightToBox()` (the size seed itself stays), and the dialogs no longer fit the rows when the size box is
ticked. `getFittingListHeight` and `fitListHeightToBox` remain for the resize, which is unchanged. D1's rule lives in
one method, `SelectionBaseVSAssemblyInfo.getContainedListHeight()`: unmarked, `listHeight × defh + title` as before;
marked and `!isUserSize()`, the tier box; marked and `isUserSize()`, `title + getListBodyHeight()`. Every rebuild
calls it — the list dialog's and the tree dialog's marked Dropdown→List (the tree keeps its `max` with the current
box), `VSSelectionContainerService.applySelection` (the expand and its overflow estimate), `GroupingService` (a move
into a container) and `AbstractLayout.applySelection` (device layouts) — so the dialogs and the container cannot
disagree. The dialogs' unmarked branches are unchanged.

## 4. Item 2 — author cell padding

- `VSDensityDefaults.excludedCellPaddingHeight(VizContext, TableDataVSAssemblyInfo)` (stored side, guarded by
  `ctx.modern`) and `excludedCellPaddingHeight(TableDataVSAssemblyInfo)` (the lens, guarded by the mark, building a
  context only for an author padding). Both return the current tier's vertical padding under an author padding and
  the DEFAULT tier's otherwise. `rowHeight(ctx, info)` and `headerRowHeight(ctx, info)` subtract it; every stored
  row height (viewer, binding pane, the lens rows every exporter reads, `VSTableHelper`) goes through those two.
- `VSTableLens.getRowPadding` floors at the same value; `getSeededInsetHeight` is deleted. Without moving the floor,
  a table with an author padding under full stylesheet coverage would land below its tier.
- `TableDataVSAssemblyInfo.resetCellPadding(VizContext)` (D3), called from the follow branch of
  `TableViewPropertyDialogService`, `CrosstabPropertyDialogService` and `CalcTablePropertyDialogService` with
  `VizContext.of(info)`. `resetUserCellPadding()` stays for the unmarked clear.

## 5. Item 3 — hidden container title

| Site | Change |
|---|---|
| `CurrentSelectionVSAssemblyInfo` | add `isTitleLaneDropped()` = `!isTitleVisible() && getVizMark() != null`, `getTitleLaneHeight()` |
| `CurrentSelectionVSAssembly.getChildTop` | `titleH = getTitleLaneHeight()`; `layout()` follows |
| `VSCurrentSelectionHelper.writeObjectBackground` | skip the one-lane-down frame when the lane is dropped |
| `VSCurrentSelectionHelper.writeSelections` | out rows start at `top + getTitleLaneHeight()` |
| `HTMLSelectionListHelper` | move a list back up by `getTitleLaneHeight()`, not the title height |
| `AbstractVSExporter.needExport` | accept `gap == 0` when the lane is dropped |

`getChildTop`, the HTML shift and `needExport` must land together: `getChildTop` alone moves HTML lists up twice
and drops a first child from match-layout export. Excel and print layout already drop a hidden container title for
every container and need nothing; HTML range sliders follow through `getBounds`.

## 6. Item 4 — shifted table chrome

- `BaseTableModel.cardTopShift?: number`, client-only.
- `BaseTable.getObjectTop()` computes the shift once (`getCardTopShift()`, the existing rule) and publishes it in a
  microtask, last value wins, 0 when it goes away, nothing when it never existed; the crosstab's hidden-title
  `getCardHeight` override flows into it.
- `VSObjectContainer.getCardTopShift(object)`: 0 unless `object.vizModern` and not max mode. `getToolbarTop`'s
  anchored branch, `getVsObjectPosition`'s table branch and a new `getVsObjectHeight` (the outline's height, so a
  shifted card's outline ends on its bottom) read it. The floating branch is unchanged.

## 7. Verification

### 7.1 Automated

| Item | Tests |
|---|---|
| 1 | `ContainedListHeightTest`, `SelectionDensitySizeTest`, `ComposerObjectServiceTest`, `SelectionPaddingDialogTest`, `SelectionTreeShowTypeSizeTest`; from 2026-10-13 also `GroupingServiceTest`, `VSSelectionContainerServiceTest`, `AbstractLayoutTest` |
| 2 | `DensityRowHeightInvariantTest`, `TableCellPaddingCssCoverageTest`, `TableCellPaddingSeedTest`, `TableViewPropertyDialogServiceTest`, new `CalcTablePropertyDialogServiceTest` |
| 3 | `ContainerChildTopTest`, `SelectionContainerChildExportTest`, `SelectionContainerOutRowsExportTest`, new `HTMLHiddenContainerTitleTest` |
| 4 | new `table/base-table.spec.ts`, `vs-object-container.component.spec.ts` |

Each item pins its legacy invariance with at least one unmarked test.

### 7.2 Manual

The repro checklist's steps flip to their expected values on a server built with this branch, at comfortable,
compact and dense, using the `DFX1`–`DFX4` fixtures. The unmarked fixtures (`DFX1 ListHeight Legacy`,
`DFX3 SEL Legacy Hidden Title`, `DFX4 Shrink Table Bottom Tabs Legacy`) behave exactly as recorded in the checklist.

## 8. Risks and accepted costs

1. ~~Content saved before this branch keeps its stored 6 rows in the composer until a height drag, the tick, a
   density change, Modernize or Revert; a composer open does not seed.~~ *(Replaced 2026-10-13: only the dropdown's
   Height and a height drag write the rows now, so saved content needs no seed.)* A Height typed in Dropdown mode on a list the
   author never sized does not size its List box — the List box is the density box until the author sizes it.
2. A typed Height in Size & Position does not refit the rows (unchanged).
3. A marked container with a hidden title saves child y values the composer canvas does not draw.
4. Under full stylesheet coverage with an author padding and an author row height, a tier change now moves the row
   by the tier padding difference, as author-sized rows without a padding already do.
5. `getObjectTop` gains a deferred side effect, like `getCardHeight` already has.

## 9. Out of scope

- A range slider hiding its own title in a container (no UI, script or conversion path reaches it) and the
  contained slider's double-counted track (viewer and export agree) — dropped by the user.
- The stylesheet-only variant of item 2 (full coverage, no author padding, org density change): it needs a re-seed
  on org density change, a different mechanism.
- A reverted table cleared to 0/0/0/0 keeps a stale DEFAULT tier; selection list and tree dialogs have the same
  re-tick gap for their cell padding (inset only).
- The legacy floating strip and legacy outline on a shrunk bottom-tabs table; the outline's width on a width-shrunk
  card.
- `SelectionVSAScriptable.getFixSize`, `ComposerAdhocFilterService` and `ComposerRangeSliderService` read or write
  rows in 20px units on paths this design does not reach.
- GroupingService storing the expanded height on a collapsed contained child.
