# Density padding, slice C: the table card inset in export (handoff)

**Status:** brainstorming, partway through. This is a context file, not a spec. Decisions taken so
far are in §3. The approach, the design sections, the spec and the plan are all still to do (§10).

**Written:** 2026-09-25, against community `feature-density-padding-card-inset` @ `ee09dd7b6`.
Every line number below is from that commit. Re-check with `grep -n` before relying on one: slice B
was still taking fixes from its manual checks when this was written.

**Re-checked:** 2026-09-25, at `6820f9db2`, the one commit #5618 gained after this was written. It
touches four frontend files only: `base-table.ts`, `mini-toolbar.service.ts` and two TL specs. No
Java line cited here moved. The two `base-table.ts` sites in §9 moved by six lines and are updated.
Its content is in §5.

**Where this file lives:** it began untracked at the enterprise root to keep it off slice B. It
moved here with the slice C branch's first commit. Fold it into the spec when that is written.

---

## 1. Where this sits

- **Parent design:** `community/docs/superpowers/specs/lookfeel/2026-09-23-density-padding-design.md`.
  Its §7.2 is the export section this work replaces. §7.2's site list is wrong (see §4).
- **Slice A** is PR #5617 (`feature-density-padding` → `epic-74519`): the chart inset per tier plus
  table cell padding. **Slice B** is PR #5618 (`feature-density-padding-card-inset`, stacked on A):
  the table card inset in the browser and the dialogs. Both are open.
- Slice B **deliberately leaves export out**. Until slice C lands, a table with a non-zero
  `getPadding()` shows its inset on screen and in the dialog, but not in PDF, PNG, SVG, HTML,
  PowerPoint or print layout. That is the live/export mismatch the parent spec's one-resolver rule
  exists to prevent, which is why slice C should follow closely. `epic-74519` is unreleased, so no
  customer sees the gap.
- **Local-only sources, git-excluded** (`.git/modules/community/info/exclude`), which the ledger
  says to delete once the export PR ships:
  `community/.superpowers/sdd/2026-09-23-density-padding/task-12-report.md` (the first redesign,
  R1–R8) and `progress.md` (about 50 rulings across two sessions). This file carries everything from
  them that slice C needs, so it does not depend on them surviving.

## 2. Branching

- **C1** (PDF, SVG/PNG, PowerPoint, HTML) and **C2** (print layout) go on a **new branch**, not on
  slice B. They are two PRs, C2 after C1.
- Slice C needs slice B's code: `VSAssemblyInfo.userPadding` / `resetPadding`, the table padding
  seed in `TableDataVSAssemblyInfo.seedChromeDefaults`, and `BaseTableModel.padding`.
- **Branched** 2026-09-25 (user ruling: stay stacked): `feature-density-padding-export` was cut from
  `origin/feature-density-padding-card-inset` @ `6820f9db2`. It is stacked on #5618, which is
  stacked on #5617, and it is not cut from `epic-74519`. At the cut it was 29 commits ahead of the
  epic and 91 behind (fork point `e7e83e9c2`, 2026-09-23). It also lacks slice A's `8fd6142d7`,
  which landed on slice A after slice B was cut from it.
- It has **no upstream**. `git switch -c` had set slice B's branch as its upstream, and that was
  removed so that a bare `git push` cannot land on #5618. Push it with
  `git push -u origin feature-density-padding-export`.
- Once #5617 and #5618 merge, rebase it onto `epic-74519`. Until then, rebase it onto slice B
  whenever slice B takes more fixes.
- Naming convention is `feature-{redmine#}` (see `CLAUDE.md`). No Redmine issue exists yet, so the
  branch follows slices A and B (`feature-density-padding`, `feature-density-padding-card-inset`).

## 3. Decisions

| Question | Ruling | Source |
|---|---|---|
| Which formats | PDF, SVG/PNG, PowerPoint and HTML in C1; print layout in C2. **Excel and CSV are out.** | user, 2026-09-25 |
| PowerPoint (the task-12 report's R5) | **In.** It inherits the shared PDF/SVG base, so it gets the inset largely for free. The chart's inset already reaches PPT, because it is baked into the chart image. | user, 2026-09-25 |
| How Excel and CSV stay out | **Proposed, not yet confirmed:** a named exporter capability, following `paintsPageBackground()` (`AbstractVSExporter.java:1834`, overridden to false in `web/viewsheet/service/ExcelVSExporter.java:47`), rather than an Excel type check | assumption stated 2026-09-25, not objected to |
| Print layout | **Faithful**, in its own PR (C2): the report engine's table painter learns an outer inset (§6.4). Not the chart-style fixed card element, and not a permanent gap. | user, 2026-09-25 |
| Which tables | **Every table with a non-zero `getPadding()`**, marked or not. The slice B Padding group is ungated (ruling I2: gauge, text and chart all mount `<padding-pane>` ungated too), so an author can type an inset on an unmarked table. The chart's exporters already honour `getPadding()` for every chart. | slice B ruling, 2026-09-24 |
| A `format.css` padding (the task-12 report's R8) | **Accepted, not gated.** A stylesheet padding wins outright for marked and unmarked assemblies alike, so a table with `padding` on its CSS rule gains an exported inset it never had. The chart has always behaved this way. | user, 2026-09-24 ("Option 1") |
| Shrink-to-fit (the task-12 report's R6) | **The card grows to content plus inset**; it does not shrink the content. This follows from browser parity: `getCardWidth()` = min(Σcolumns + L + R, design width). Treated as settled; confirm when presenting the design. | follows from slice B |
| Legacy output | A table with padding `0,0,0,0` exports **byte-identically** to today. Every new path must be inert at a zero inset. | parent spec, slice B MT-8 |

## 4. Why the original Task 12 must not be reused

Plan Task 12 (`community/docs/superpowers/plans/2026-09-23-density-padding.md:2417`) names four
levers, and every one is wrong. All four were verified in code by two separate sessions:

1. `VSTableDataHelper.getObjectPixelBounds` (`:157`) **has no grid consumer.** All ten callers draw the
   card: the round-corner clip and `drawObjectFormat` in `PDFTableHelper`, `PDFCrosstabHelper`,
   `SVGTableHelper` and `SVGCrosstabHelper`, plus `drawObjectFormat` in `PPTTableHelper` and
   `PPTCrosstabHelper`. Insetting it moves the **border** inward.
2. `getTableRectangle` returns `Rectangle(anchor_x, anchor_y, colCount, rowCount)`
   (`VSTableHelper.java:252-269`). Its width and height are **counts**, and its x/y are **Excel's
   anchor cell**.
3. `calculateColumnsPosition` (`VSTableHelper.java:46-68`, `VSCrosstabHelper.java:48-101`) builds
   spreadsheet **column indices**, not pixel positions.
4. In print layout, the `TableElementDef` **is** the border carrier (`VsToReportConverter.java:1173-1178`),
   so insetting its bounds moves the border too.

## 5. The browser model to match (slice B)

- **Card rect** = the assembly rect. It carries the border, the background fill and the round-corner
  clip. `getCardWidth()`/`getCardHeight()` in `base-table.ts`.
- **Content rect** = the card minus the padding on all four edges. The title lane, header rows, body
  rows and scrollbars sit inside it, in the `.table-content` box. `getObjectWidth()`/`getObjectHeight()`
  now return this rect; the arithmetic is in `table-content-rect.ts`.
- **Shrink-to-fit:** card width = min(Σcolumns + L + R, design width), and card height takes the
  inset too.
- **Last column** stretches to the **content** width (`base-table.ts` `updateDisplayColumnWidth`,
  fixed in `d134281c1` / `3dc998b50`). But see F1 in §9: the server may already be filling it to the
  card width.
- **Last column's right border:** it gives up 1px when there is a right inset (`25997ad2a`). Since
  `6820f9db2` that pixel is display-only. `lastColBorderAllowance` records it, a column drag adds
  it back, and the stored `model.colWidths` never carries it. A hidden, zero-width last column
  keeps its 0. So the widths the server stores, and the export reads through `getColumnWidth2`,
  carry no browser allowance. Export has nothing to mirror or undo here.
- **Crosstab drill and date-comparison tip icons** stay at the card corner, the same as the chart's.
  Whether export draws them at all has not been checked.

## 6. Surface map

Mapped by four read-only agents and spot-checked by hand. Paths are under
`community/core/src/main/java/inetsoft/` unless another root is named.

### 6.1 PDF, SVG/PNG and PowerPoint: one shared base

```
VSTableDataHelper                  report/io/viewsheet/
├── VSTableHelper                  table, embedded table
│   ├── PDFTableHelper, SVGTableHelper          in scope (PNG extends SVGVSExporter)
│   ├── PPTTableHelper       utils/inetsoft-xml-formats   in scope, inherits all geometry
│   ├── ExcelTableHelper     utils                        OUT, must stay unchanged
│   │   └── OfflineExcelTableHelper                       the factory default (PoiOfficeExporterFactory:36)
│   └── AlertTableHelper     sree/schedule/ViewsheetAction:2255   no-op sink, safe either way
└── VSCrosstabHelper               crosstab AND calc table
    ├── PDFCrosstabHelper, SVGCrosstabHelper, PPTCrosstabHelper   in scope
    ├── ExcelCrosstabHelper                                        OUT
    └── AlertCrosstabHelper                                        no-op sink
```

`VSTableDataHelper.write` (`:318`) runs in this order: `BaseTableService.getColWidths` (`:330`),
title width (`:345`), `calculateColumnsPosition` (`:347`), round-corner clip (`:351`), background
(`:354`), title (`:355`), data (`:358`), border (`:369`).

Geometry sites. Every one reads the **card** today:

| Site | Reads | Needs |
|---|---|---|
| `VSTableDataHelper.getPixelBounds` `:513` (cell origin `:517`, bottom clamp `:559-563`) | `getPixelPosition(info.getPixelOffset())`, `getPixelSize().height` | origin + (L, T); clamp − B |
| `VSTableDataHelper.writeTitle` `:393` (origin `:398`, width from `:345-346`) | pixel offset, `CoordinateHelper.getAssemblySize` | origin + (L, T); width − (L + R) |
| `ExcelVSUtil.calculateColumnWidths` `:146` (`totalPixelW` `:151`, last-column fill `:190-219`) | `getPixelSize().width` | content width, **for non-Excel callers only** |
| `BaseTableService.getColWidths` `:1196` (fill `:1216-1222`), called at `VSTableDataHelper:330` | `layoutSize`, else `pixelSize` width | content width. **Also the browser's column-width source.** See F1. |
| `VSTableLens.initTableLensColumnWidths` `:1735-1739` | `getPixelSize().width`; cached once and computed before any helper runs | content width |
| `VSTableHelper.getVisibleRowCount` `:200` (match mode: rows that fit) | `pixelSize.height` − title − header | − (T + B) |
| `VSCrosstabHelper.writeData` height budget (about `:367-373`) and trim (`:193-207`) | `vs.getPixelSize(info)` | − (T + B) |
| `VSTableHelper` horizontal cull (about `:144-161`) | `vs.getPixelSize(info).width` | − (L + R) |
| `PDFTableHelper` page cap `:195-197` | **writes** `info.getPixelSize().height` | card = content + inset |
| `getObjectPixelBounds` `:157-216` (shrink `:193-204`: width = Σcolumns, height = rows) | the card, or columns and rows when shrunk | card = content bounds **grown** by the inset (R6) |
| `VSTableDataHelper.applyTableBorders` `:422`, called at `VSTableHelper:140` and `VSCrosstabHelper:230` | copies the assembly border onto the outer cells | pass `false` for any edge with an inset, or a second frame appears at the content edge |
| Expand mode: `AbstractVSExporter.getExpandTableHeight` `:2542-2582`, applied by `PDFVSExporter.expandTable` `:606-627` (`setPixelSize` + `insertRowCol`), SVG `:329-351`; width at about `:2683-2734`, `:2827-2851` and `:2860-2894` | row and column totals | + inset, or the last rows are clamped off and assemblies below shift too little. The chart precedent adds padding in `expandChart` (`:2044-2049`). |
| Match-mode region lens: `getRegionTableLens` (about `:931-1065`), built before `writeX` | `table.getPixelSize()` | content size |
| Bottom-tabs shift: `AbstractVSExporter:288` → `:312-333` → `VSTableDataHelper.applyShrunkBottomTabsShift` `:868` / `computeShrunkRenderedHeight` `:926` | title + rows | + (T + B), so the **card's** bottom stays flush with the tab strip |

**PowerPoint:**
- Its only geometry override is `getPixelBounds`, which calls `super` and scales by 0.75
  (`PPTTableHelper:161-171`, `PPTCrosstabHelper:158-167`).
- Each cell is its own text box.
- Round corners are drawn as shape geometry on the object box. There is no clip:
  `beginRoundCornerClip` is a no-op outside PDF and SVG.

### 6.2 HTML: a separate hierarchy, and the simplest surface

- `HTMLTableHelper` handles table and embedded table. `HTMLCrosstabHelper` handles crosstab and calc
  table (`HTMLVSExporter:549-573`).
- A single outer `div` carries everything at card level: position and size, background, border
  (`vHelper.getCSSStyles(bounds, fmt, true)`), and `border-radius` + `overflow:hidden` when the corner
  is rounded. It is written at `HTMLTableHelper.write:77-92` and `HTMLCrosstabHelper.write:81-100`.
  The title and scroll wrappers inside it are `width:100%`.
- **Approach:** one inner `position:absolute` wrapper at (L, T) with the content width and height,
  emitted only when the inset is non-zero. The border, background, radius and clip stay on the
  outer div.
- Four explicit edits besides the wrapper:
  1. **Shrink:** `HTMLTableDataHelper.fixShrinkTableBounds` (`:65-76`) must use Σcolumns + L + R
     and rows + title + T + B.
  2. **Data height:** `dataH` = bounds.h − titleH (`HTMLTableHelper:91`, `HTMLCrosstabHelper:100`),
     and `isTableYOverflow`, must use the content height.
  3. **Crosstab last-column fill:** `HTMLCrosstabHelper.initRowColumns` (`:108`, fill `:153`) fills to
     `bounds.getWidth()`, and must fill to the content width. The plain table fills by CSS `width:100%`
     (`HTMLTableHelper:276-285`), so the wrapper handles it.
  4. **Annotations** (`HTMLTableDataHelper:152-167`) are separate elements, so they need + (L, T).
- Do not put `overflow:hidden` on the wrapper: it would clip the 17px scrollbar gutter.

### 6.3 Excel: what it consumes, and so what must read zero

- Excel ignores `getPixelBounds` x/y entirely.
- It **does** consume:
  - `getTableRectangle` x/y, which become grid coordinates
  - `columnPixelW`
  - the `writeTitle` geometry
  - the crosstab's pixel heights (`VSCrosstabHelper:191-207`, reached via `ExcelCrosstabHelper:329`)
- The grid uses fixed 20px rows, so an 8–16px inset cannot be represented.
- Containment options:
  - the task-12 report's `getCardInset(info)` hook on `VSTableDataHelper`, overridden to zero in
    `ExcelTableHelper` and `ExcelCrosstabHelper` (`OfflineExcelTableHelper` inherits it)
  - an exporter capability (§3)
- **Test to write either way:** a modernized info seen through an Excel helper yields a zero inset.
  It is the only mechanical proof that Excel output cannot move.

### 6.4 Print layout (C2)

- All three table types reach `VsToReportConverter.addTable` (`:1104`) through one `switch` case
  (`:605-608` → `:642`). Its bounds come from `layoutPosition`/`layoutSize` (`getPixelBounds` `:959`),
  not from the pixel offset and size.
- **The title** is a separate `TextBoxElementDef` from `createTitle` (`:1372`). It merges the object's
  top, left and right borders into itself.
- **The table element carries the border** (`:1173-1178`). `TablePaintable.paintBorder`
  (`report/internal/TablePaintable.java:1412`) draws the left, right and bottom edges at the element's
  printed area, and the bottom only on the last region. The background is only a per-cell fallback.
- **The table grows:** `TableElementDef` defaults `GROW` to true (`:87-88`), and every row prints.
  That is why the chart's approach fails here: `addChart` (`:1481`) draws a card `TextBoxElementDef`
  fixed at the design rect (`borderTextBox` `:1492`), and a chart never grows.
- **Chosen direction:** an outer inset in the report engine.
  - `TablePaintable` (with `TableElementDef` carrying the value) paints the card background and
    border at the element's printed area, and lays the cells out inside it.
  - The top inset applies to the first page region, the bottom inset to the last.
  - The title lane moves inside the inset.
  - Inert at a zero inset.
  - This is shared report-engine code (classic reports use it too), which is why it is its own PR.
- **Also in C2:**
  - `calculateColumnWidths` (`:1246`) stretches the last column to `layoutSize.width`, and the
    fit-page decision (`:1144-1149`) compares against that same width. Both need the content width.
  - The shrink and bottom-tabs arithmetic (`computePrintLayoutTableHeight` `:1036`, compared at
    `:1004`) needs + (T + B).
  - A title-hidden table still loses the title height from its bounds (`:1439-1446`). This quirk
    predates this work; don't fix it by accident.
- **Round corners:** print layout draws none for tables today.

## 7. Candidate approaches for C1 (not chosen)

Both approaches need the same work outside the helpers:
- the expansion, region-lens and bottom-tabs changes
- `applyTableBorders` edge suppression
- the last-column fill to the content width
- the Excel containment

**A. Hand the grid a content-rect copy of the assembly.**
- Before a padded table's helper runs, derive a copy whose info has pixel offset + (L, T),
  pixel size − (L + R, T + B) and padding 0.
- The grid code (title, cells, column fitting, truncation) runs on the copy. The card chrome (clip,
  `drawObjectFormat`) uses the copy's object bounds grown outward by the inset.
- This mirrors the browser's `.table-content` box.
- *For:* every geometry read downstream is consistent by construction. That removes the class of
  defect that sank Task 12: a site nobody listed.
- *Against:*
  - Writes that land on the copy must be copied back (`PDFTableHelper:197`).
  - The reads that happen before the helper runs (expansion, region lens, lens width cache,
    bottom-tabs shift) still see the original.
  - The seam must exclude Excel, which shares `VSTableDataHelper.write`.

**B. Apply an inset at each geometry site.** This is the task-12 report's R1–R4, extended by the
table in §6.1.
- `getCardInset(info)` on `VSTableDataHelper`, zero for Excel, applied at each site.
- *For:* the arithmetic is explicit and reviewable per site, and Excel is provably + 0.
- *Against:*
  - About 15 sites across five classes, plus a new `ExcelVSUtil.calculateColumnWidths` parameter.
  - A missed site silently keeps card geometry, and this site list has been wrong once already.

## 8. Verification: still open

- No test renders an export end to end. `ExporterDarkOptOutTest` says why: it tests "at the
  substitution rather than through an exporter, which needs a bootstrapped" environment.
- An integration fixture exists that could make a render harness feasible: `TableVSAScriptableTest`
  uses `@SreeHome(importResources = "TableVSAScriptableTest.vso")` with the Spring
  `BaseTestConfiguration`/`IntegrationTestConfiguration`. A harness would export a padded and an
  unpadded table to SVG and HTML and assert positions, which would cover the legacy byte-identity
  guarantee too. Nobody has checked whether it can drive an exporter.
- The next question to put to the user: invest in that harness, or rely on helper-level unit tests
  plus manual checks.
- **Manual checks, whatever else is chosen:** a marked table, a crosstab and a calc table at each
  density, beside a marked chart, in PDF, PNG, SVG, HTML and PPT. Cover both match layout and expand
  mode, shrink-to-fit, bottom tabs and round corners. For each, confirm:
  - the inset matches the chart's
  - the border and background sit at the card edge
  - no row or column is clipped
  - an unmarked table's export is byte-identical to one from before slice C

## 9. Findings outside slice C, for slices A and B

- **F1 (slice B), likely a live defect, seen only in code so far:**
  - The server fills the last column to the **card** width. `BaseTableService.getColWidths`
    (`:1196`, fill `:1216-1222`) stretches it to the `layoutSize`/`pixelSize` width, and
    `LoadTableDataCommand` sends that array as `model.colWidths` (`:441-443`, `base-table.ts:1081`).
  - The browser's stretch (`base-table.ts:750-758`) only ever adds width, so a marked table's columns
    sum to the card while the grid is clipped to the content rect.
  - Re-checked at `6820f9db2`: the server fill is unchanged. #5618's round-2 review established that
    the horizontal scroll extent is `sum(model.colWidths)` (`vs-table.component.html:316`), so the
    columns summing to the card is exactly what would produce the scrollbar.
  - **Expected on screen:** a horizontal scrollbar with about 2 × inset of travel (about 32px at
    comfortable) on a marked table whose columns are narrower than the assembly.
  - Asked the user to check this on slice B's MT-1 table; not yet answered.
  - If confirmed, fix it in slice B. The export reads the same method (`VSTableDataHelper:330`), so
    one fix serves both, and slice C should branch after it.
- **F2 (slice A), HTML cell padding top:** `HTMLCoordinateHelper.getPaddingString`
  (`report/io/viewsheet/html/HTMLCoordinateHelper.java:303-304`) writes `padding-top` from
  `padding.right`. The bug predates this work, but slice A's asymmetric cell padding (y 6 / x 8 at
  comfortable) is the first common value to reach it: every HTML-exported cell gets 8px on top
  instead of 6. The fix is one line and belongs with #5617. It also changes text-assembly HTML
  wherever top ≠ right.
- **F3 (slice A), stale comment:** `VSTableDataHelper.java:865-866` says print layout renders
  unpadded row heights; slice A made them padded (the comment at `VsToReportConverter:969-973` is
  already updated).

## 10. Next steps

The remaining brainstorming path (architectural):
1. Settle verification (§8).
2. Choose between approaches A and B (§7), with a recommendation.
3. Present the design in sections for approval: C1 shared base, HTML, Excel containment, C2 report
   engine, testing.
4. Write the spec. It should supersede §7.2 of the parent design, whose site list is wrong (§4).
5. Self-review the spec, get user approval, then run `superpowers:writing-plans`.

Before any of that, get an answer on F1: it decides whether slice C branches after a slice B fix.
