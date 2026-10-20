# Tables — follow the default density for row height

**Date:** 2026-10-19
**Branch:** community `feature-table-row-height-follow`, cut from `epic-74519` @ `1f2a5bc5d8`
**Verified against:** `1f2a5bc5d8`. Every line citation below is at that commit.
**Depends on:** nothing unmerged. #6832 is in `epic-74519`. #6798 (form controls) and #6979 (text
labels) touch no table file.
**Follows:** [strip and lane decisions](./chart-card-anchored-strip-lane-decisions.md) decision 5, which
gives table row height the same "follow the default density" checkbox that title height and selection
cell height already have, and [the density padding design](./2026-09-23-density-padding-design.md),
which set the 20/24/28 data-row and 22/26/30 header-row tiers.
**Types in scope:** the three table types that offer **Set Cell Size**: table (`TableVSAssembly`),
crosstab (`CrosstabVSAssembly`) and freehand table (`CalcTableVSAssembly`).

## 1. Why

A marked table's rows follow the dashboard's density until an author sets a row height. After that,
the rows are pinned, and only **Reset Table Layout** puts them back. That action also wipes column
widths, the title height and the table's size. Decision 5 named the two problems this causes, and
both apply to row height:

- **No way back.** An author who pinned a row height cannot hand it back to the density without
  losing the rest of the table's layout.
- **No way to pin what you see.** The flag is inferred from an action, not stated, so the author has
  no direct way to say "keep this height".

Reading the code turned up a third problem that only applies to the composer:

- **A width-only edit pins the height.** **Set Cell Size** always sends both a column resize and a
  row resize. The row half always sets the flag, so an author who opens the dialog to change only the
  width pins that row type at its current density height (§2.2).

The fix is a **Follow the default density** checkbox under Cell Height in the Set Cell Size dialog,
shown in the composer for marked tables only. It is an explicit record of what the author said.

## 2. What exists today

### 2.1 Row height is resolved when the table is drawn

- A table stores one data-row height for all data rows (`TableDataVSAssemblyInfo.dataRowHeight`,
  default `AssetUtil.defh`), one height per header row (`headerRowHeights[]`), and one flag for each
  row type: `userDataRowHeight` and `userHeaderRowHeight` (`TableDataVSAssemblyInfo.java:898-912`).
- A freehand table's drawn rows come from the same fields. `VSTableLens` builds its row heights from
  the assembly info alone (`initTableLensRowHeights`, `VSTableLens.java:1774`) and never reads
  `TableLayout` row heights, which only feed the binding editor's layout grid
  (`VSTableLayoutService.java:364`).
- For older files the flags are derived on parse: data is "user" when the stored height is not
  `defh`, header when any header height is not `defh` (`TableDataVSAssemblyInfo.java:1067-1078`).
- On a marked table, the density tier replaces the stored height only while the flag is clear, and
  for data rows only while the stored height is still `defh`. The same rule is applied in three places:
  - the browser model (`BaseTableService.java:463-478`)
  - the table lens, which every export reads (`VSTableLens.java:1809-1819`)
  - the export table helper (`VSTableHelper.java:219-220`)
- The value used is the tier less the cell padding that is added back when the table is drawn
  (`VSDensityDefaults.rowHeight(VizContext, TableDataVSAssemblyInfo)`, `VSDensityDefaults.java:125-135`).
  The browser model gets the full row height (`BaseTableService.java:490-499` adds the padding back).
- When the header flag is clear, the lens resets `headerRowHeights` to `{ defh }` every time it
  builds (`VSTableLens.java:1797-1801`). The browser model only applies the tier to header entries
  still at `defh` (`BaseTableService.java:469-477`), so this design resets the header heights itself
  rather than relying on that side effect. Data rows likewise need `dataRowHeight` back at `defh`.
- When CSS sets a row height, it wins over both the stored height and the density
  (`VSTableLens.getCSSDataRowHeight`/`getCSSHeaderRowHeight`, which only apply while the flag is
  clear). A script's `rowHeight[row]` writes the per-row `rowHeights` map
  (`TableDataVSAScriptable.java:586`), which wins over `dataRowHeight` (`TableDataVSAssemblyInfo.java:826-835`).

Because this is all resolved when the table is drawn, a dashboard density change, Modernize and Revert
already move the rows whenever the flag is clear. Nothing is seeded.

### 2.2 How an author sets row height

There are two ways, and both are composer canvas actions:

- **Dragging a row border.** `/composer/viewsheet/table/changeRowHeight` →
  `ComposerVSTableService.changeRowHeight(rvs, event, true)`.
- **Set Cell Size.** Right-clicking a cell and choosing *Set Cell Size* opens the Resize Cell dialog
  (`table-cell-resize-dialog.component.*`), which has Cell Width and Cell Height. Its OK
  (`base-table.ts:2344-2375`) always builds both a `ResizeTableColumnEvent` and a
  `ResizeTableRowEvent`, even though the method's own comment says it sends only what changed, and
  sends them together to `/composer/viewsheet/table/changeCellSize`. On the server,
  `resizeTableCell` (`ComposerVSTableService.java:425-433`) always calls
  `changeRowHeight(rvs, event.rowEvent(), false)`.

`changeRowHeight` (`ComposerVSTableService.java:929-980`) writes the height for the row type of the
event's row, and sets the flag unconditionally:
- **Header:** `setHeaderRowHeight(i, h)` for each spanned header row, then `setUserHeaderRowHeight(true)`.
- **Data:** `setDataRowHeight(h)`, then `setUserDataRowHeight(true)`.

The dialog shows the height less the cell's row padding (`base-table.ts:2318-2337`), and
`resizeTableCell` passes `removePadding = false`, so the number shown is the stored height. An OK with
the number untouched writes back the value already stored, so the row does not move. Only the flag
changes.

**Reset Table Layout** (`ComposerVSTableService.resetTableLayout`, `:393-420`) is the only path that clears either
flag.

### 2.3 Set Cell Size also exists in the viewer

The action is offered in the viewer and in preview unless the dashboard's action settings hide it
(`table-actions.ts:87-91`, `crosstab-actions.ts:105-109`, `calc-table-actions.ts:130-134`;
`AbstractVSActions.isActionVisibleInViewer` at `abstract-vs-actions.ts:672-674`). OK sends the same
event there.

### 2.4 The sibling checkboxes

Title height and selection cell height already have this checkbox, in Size & Position
(`size-position-pane.component.html:81-125`). Each:
- sits under its number field
- is labelled `_#(composer.vs.followDefaultDensity)` ("Follow the default density")
- greys out the field while ticked (`size-position-pane.component.ts:114-122`)
- is offered only when the assembly is marked: the dialog service sends `null` for an unmarked
  assembly and the pane hides the box (for example `CrosstabPropertyDialogService.java:126-128`,
  `size-position-pane.component.ts:149-150`)

## 3. Decisions

Rulings marked **(user)** were made by the user on 2026-10-19 and are not to be reopened. Calls marked
**(my call, confirmed)** were mine and the user agreed to them. Calls marked **(my call)** alone were
made after the builder check and have not been confirmed.

**D1 — The box lives only in Set Cell Size, under Cell Height. (user)** Not in the property dialog's
Size & Position pane or anywhere else. It sits under the Cell Height field, as the three sibling boxes
do. Placing it to the right of the field was considered and rejected.

**D2 — Composer only, marked tables only.** Composer only is the user's ruling. Marked only follows
the siblings (§2.4): an unmarked table has no density to follow. "Marked" means the table model's
`vizModern`, never the org gate. The viewer and preview never show the box. A table component's
`viewer` getter is already `context.viewer || context.preview` (`abstract-vsobject.component.ts:71-73`),
so `!viewer` is the composer test. **(my call, confirmed)** The box appears wherever the composer
offers Set Cell Size today, including the binding editor where it is offered.

**D3 — The box covers the row type of the cell's first row. (user)** A data cell means every data row,
and `userDataRowHeight`. A header cell means every header row, and `userHeaderRowHeight`, even though
each header row stores its own height. A freehand cell that spans header and data rows counts as the
type of its first row, as the dialog already decides `headerRow = cell.row < headerRowCount`.

**D4 — The starting state reads the flag. (user)** The box is ticked when that row type's flag is
clear, and unticked when it is set. This answers the question decision 5 left open, "unticked", which
is accurate: the flag means an author set the height.

**D5 — What OK does. (user)**

| Box at OK | Effect on that row type |
|---|---|
| ticked, flag was clear | no row resize. Only the column width changes, and the flag stays clear. |
| ticked, flag was set | flag cleared, and that row type's stored heights reset to `defh`: `dataRowHeight` for a data cell, every `headerRowHeights` entry for a header cell. The rows return to the density tier. Not `resetRowHeights()`, which also resets the title height. |
| unticked | the typed height is written and the flag set, even when the number is unchanged. This is how an author pins the height they see. |

The column half of OK is unchanged in every case.

**An unticked OK on a header cell keeps every other header row where it is. (user)** Only the clicked
cell's header rows take the typed height. When the header flag was clear before OK, every other
header row is given a stored height such that it draws at exactly the height it drew before OK. Under
the density that is the tier less the row's padding (`VSDensityDefaults.headerRowHeight(ctx, info)`).
Where CSS set the header height (D10), work it out from what the row drew and the padding it will
draw with once the flag is set, and prove it with a test. Without this, setting the shared flag would
drop those rows back to `defh` plus padding: 22→26 at dense, 26→28 at compact and 30→32 at
comfortable. When the flag was already set, the other rows already store their own heights and
nothing extra is written. Dragging a header border keeps today's behaviour (D8), which does move the
other rows.

**D6 — The greyed number is what the dialog shows today. (my call, confirmed)** It is the stored-terms
height (§2.2), so a compact data row shows 16 (24 less 4 + 4 padding). Unticking and pressing OK
without typing leaves the row exactly where it is.

That promise covers a single-row cell on an unwrapped table. Two cases already drift on today's
dialog, on every OK, and keep doing so after an unticked OK. **(my call)** Both are out of scope:
fixing them means changing the dialog's shared math, which the viewer also runs (D7). A ticked OK
sends no row resize, so it avoids both.
- **Span cells.** The dialog shows the span's total height less one row's padding
  (`base-table.ts:2322-2327`). `calculateSpanHeight` then divides it by `rowSpan`
  (`base-table.ts:2377-2398`), and every row adds its own padding back. Two compact rows (24 each)
  show 40 and store 20 each, so each renders at 28. Crosstab outer row-header cells take this path.
- **Wrapped tables.** `getRowHeight` returns the clicked row's grown height (`base-table.ts:1984-1992`),
  so OK pins that height to every data row. Setting the flag also switches the wrap-growth rule
  (`VSTableLens.java:1663-1680`).

**D7 — The viewer and preview are left completely alone. (user)** Same dialog, same event, same
server behaviour, including the width-only pin of §1. I had proposed fixing the pin in the viewer too
by only resizing a row whose number changed. The user ruled against it.

**D8 — Everything else is unchanged. (my call, confirmed)**
- Dragging a row border still sets the flag.
- Reset Table Layout still clears both flags.
- A CSS row height still wins.
- A script's `rowHeight[]` still wins.
- OK is still one undo step (`resizeTableCell` is already `@Undoable`).

**D9 — Nothing new to seed. (my call, confirmed)** Row height is resolved when the table is drawn
(§2.1), so a density change, Modernize, Revert, the browser and every export follow the flag already.
There is no seed hook, no restore step and no Modernize or Revert change.

**D10 — CSS and script heights: the box still reads only the flag. (my call)** While the flag is
clear, a CSS row height (`VSTableLens.getCSSDataRowHeight`/`getCSSHeaderRowHeight`) or a script's
`rowHeight[]` entry (`TableDataVSAssemblyInfo.java:826-835`) decides the height instead of the
density. The box still starts ticked there. It records whether an author set a height, not which
default applies, and a ticked OK sends no row resize, so CSS and the script stay in charge (D8).
Hiding or unticking the box there would make a width-only OK pin the row and cut it off from CSS,
which is the bug this design removes. The cost is that the label says "density" when CSS or a script
is what the rows follow. Unticking and typing a height pins it and overrides CSS, as dragging does
today.

## 4. Goals, non-goals, anti-goals

**Goals**
- An author can hand a hand-sized row type back to the density from the dialog, without Reset Table
  Layout.
- An author can pin the height the dialog is showing.
- In the composer, a width-only Set Cell Size on a marked table no longer pins the row height.

**Non-goals**
- Any row-height control in the property dialog.
- Per-header-row follow. One flag covers every header row (D3).
- Changing drag-to-resize, Reset Table Layout, CSS or script row heights.
- Unmarked tables.

**Anti-goals** (failure even if it technically works)
- Any change to what the viewer or preview do, down to the event they send (D7).
- The box appearing on an unmarked table, or keyed off the org gate rather than the mark.
- A row moving when the author did not ask:
  - an untick-and-OK round trip on a single-row cell that drifts (D6 lists the cases that already
    drift today)
  - an unticked OK on one header cell that moves the other header rows
  - a ticked OK that moves a row which was already following
- A ticked box over rows that an author's stored height holds (for example, flag clear but the
  stored data height not `defh`). See §6. CSS and script heights are D10, not this case.

## 5. Constraints

- **The wire change must be backward compatible.** The viewer sends exactly what it sends today (D7),
  so a missing "follows" value must mean today's behaviour on the server: write the height and set
  the flag. A stale composer client gets the same.
- **Mark, not gate.** Show the box from the table model's `vizModern`. The server applies D5 only
  when the assembly is marked, so a client that sends the field for an unmarked table changes
  nothing.
- **Reuse the existing label** `composer.vs.followDefaultDensity`. No new string.
- **No HTML comments** in the dialog template, per the user's standing preference.

## 6. Left to the builder

- **How the dialog learns the flags.** The table model carries `vizModern` but neither row-height
  flag. Adding `userDataRowHeight` and `userHeaderRowHeight` to the table model is the cheap route.
  Fetching them when the dialog opens also works.
- **The event shape.** A nullable `Boolean` on `ResizeTableCellEvent` (or its row event), where null
  means today's behaviour (§5).
- **Crosstab header heights.** `CrossBaseVSAssemblyInfo.getHeaderRowHeights(isWrappedHeader, count)`
  builds the header array differently. Confirm that a cleared header flag puts every crosstab header
  row back on the tier.
- **A stored data height with the flag clear.** If anything can leave `dataRowHeight != defh` while
  `userDataRowHeight` is false, a ticked box would show for rows that do not follow (the anti-goal).
  The parse-time derivation rules this out for saved files. Check the other writers
  (`SyncTableHandler.java:56`, `TableDataVSAssembly.java:349-355`). If one can do it, the box's
  starting state should also require the stored height to be `defh`.
- **The data round trip under CSS.** Setting the data flag switches CSS off and can change the row's
  padding floor (`VSTableLens.getRowPadding`). Check whether an unticked OK without typing moves a
  CSS-sized data row. If it does, today's dialog does the same on every OK. Record it in D6's list;
  don't fix it.
- **Wrapped rows.** `userDataRowHeight` also switches how wrapped text grows a row
  (`VSTableLens.java:1663-1680`), so ticking the box changes wrap growth to the never-touched
  behaviour. That is expected. Cover it in a manual check rather than treating it as a regression.
- **Tests.**
  - A service test for each D5 row, for data and header cells.
  - A service test for an unticked OK on one header cell of a table with two or more header rows,
    at each density: the other header rows keep their drawn heights.
  - A service test that a missing value keeps today's behaviour.
  - A dialog spec for the box's visibility (composer and marked, versus viewer, preview and unmarked)
    and for greying out the field.
  - Manual checks in the composer at each density, and a viewer check that Set Cell Size behaves
    exactly as before. The checks should include a density change after ticking, and a PDF export.
- **Docs.** Once this ships, record decision 5's open question as answered (D4) in the strip and lane
  decisions' "Still open", and add the step to the roadmap's Done table.
