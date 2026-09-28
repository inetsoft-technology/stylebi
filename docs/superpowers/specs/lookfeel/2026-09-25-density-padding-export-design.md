# Density padding, slice C: the table card inset in export

**Date:** 2026-09-25
**Verified against:** community `feature-density-padding-export` @ `080e1017f`, which sits on slice B's
`39a4336a6`. The line numbers in §5–§7 are from that commit. §8 was re-verified on 2026-09-28 against
`4f742abe8`, the C1 head, which changed none of the print-layout files. Re-check a line with `grep -n`
before relying on it.
**Supersedes:** §7.2 of `2026-09-23-density-padding-design.md`, whose site list is wrong (§4). This
spec also absorbs `2026-09-25-density-padding-export-handoff.md`, which is removed alongside it.
Paths are under `community/core/src/main/java/inetsoft/` unless another root is named.

## 1. Scope

Slice B (#5618) gives a table, crosstab or calc table a card inset in the browser and the dialogs,
and deliberately leaves export out. Until this lands, a table with a non-zero `getPadding()` shows
its inset on screen but not in any exported file. That is the live/export mismatch the parent
spec's one-resolver rule exists to prevent. `epic-74519` is unreleased, so no customer sees it.

This slice closes the gap in two PRs:
- **C1:** PDF, SVG, PNG (which goes through SVG), PowerPoint and HTML, on
  `feature-density-padding-export` (#5808).
- **C2:** print layout, after C1, on `feature-density-padding-print-layout` (§11).

## 2. Decisions

| Question | Ruling | Source |
|---|---|---|
| Which formats | PDF, SVG/PNG, PowerPoint and HTML in C1; print layout in C2. **Excel and CSV are out.** | user, 2026-09-25 |
| PowerPoint | **In.** It inherits the shared PDF/SVG base. The chart's inset already reaches PPT, baked into the chart image. | user, 2026-09-25 |
| Print layout | **Faithful**, in its own PR: the report engine's table painter learns an outer inset (§8). Not a fixed card element over a growing table, and not a permanent gap. | user, 2026-09-25 |
| Which tables | **Every table with a non-zero `getPadding()`**, marked or not. Slice B's Padding group is ungated, so an author can type an inset on an unmarked table, and the chart's exporters honour `getPadding()` for every chart. | slice B ruling, 2026-09-24 |
| A `format.css` padding | **Accepted, not gated.** A stylesheet padding wins for marked and unmarked assemblies alike, so a table with `padding` on its CSS rule gains an exported inset it never had. The chart has always behaved this way. | user, 2026-09-24 |
| Shrink-to-fit | **The card grows to content plus inset**; the content does not shrink. Browser parity: `getCardWidth()` = min(Σcolumns + L + R, design width). | follows from slice B |
| Legacy output | A table with padding `0,0,0,0` exports **byte-identically** to today. Every new path is inert at a zero inset. | parent spec |
| Verification | **Helper-level unit tests plus manual checks** (§9). An end-to-end render harness is reconsidered once C1 runs locally. | user, 2026-09-25 |
| How the inset reaches each site | **Approach B:** an explicit inset at each classified site, not a content-rect copy of the assembly. | user, 2026-09-25 |
| Where the inset is resolved | **On the exporter**, as a capability, so the sites that run before any helper get the same value. This is also how Excel and CSV stay out (§7). | user, 2026-09-25 |
| Print layout: a page break | **A continuation page gets no top inset and no top border**, as today. The inset is (0, L, B, R) on every region. | user, 2026-09-28 |
| Print layout: the last row and B | **The last row moves to the next page with its B band** when it fits without B but not with it. The card is never cut off at the page bottom. | user, 2026-09-28 |
| Print layout: a hidden title | **The card-top box carries the top border**, so a padded table has its full card border either way. An unpadded table keeps today's missing top. | user, 2026-09-28 |
| Print layout: the painter's geometry | **Its box stays the grid, and its public geometry speaks for the card** (§8.6). This was preferred over moving every cell read by L, and over a separate card paintable. | user, 2026-09-28 |
| Print layout: F5 | **Parked.** It sits upstream of every C2 site (§10). | user, 2026-09-28 |

Approach A (hand the grid a content-rect copy) was rejected for two reasons:
- It would have to copy the whole assembly, because `CoordinateHelper.getAssemblySize(assembly, …)`
  reads the assembly.
- It adds hazards no grep can find: lookups by name that return the original, and in-place writes
  such as `PDFTableHelper:197` that would have to be copied back.

Under B every read is listed in §5.3, and a review can check that list against the grep.

## 3. The browser model to match

Slice B's browser model is the reference:
- **Card rect** = the assembly rect. It carries the border, the background and the round-corner
  clip (`getCardWidth()`/`getCardHeight()` in `base-table.ts`).
- **Grid rect** = the card less the padding on all four edges. The title lane, the header and body
  rows and the scrollbars sit inside it (`getObjectWidth()`/`getObjectHeight()`,
  `table-content-rect.ts`).
- **Shrink-to-fit:** card width = min(Σcolumns + L + R, design width); the card height takes T + B
  too.
- **Last column** fills the grid width. Since `39a4336a6` the server fills it there as well
  (`BaseTableService.getColWidths`), so the widths a padded table sends add up to its grid.
- The last column's 1px right-border allowance is display-only and never reaches stored widths
  (`6820f9db2`), so export has nothing to mirror.

Below, L, T, R and B are the inset's four edges, W and H the card's width and height, and the grid is
(x + L, y + T, W − L − R, H − T − B).

## 4. Why the parent plan's Task 12 is not reused

Plan Task 12 (`docs/superpowers/plans/2026-09-23-density-padding.md:2417`) names four levers, and
each is wrong:
1. `VSTableDataHelper.getObjectPixelBounds` (`:157`) has no grid consumer. All ten of its callers
   draw the card: the round-corner clip and `drawObjectFormat` in the PDF and SVG helpers, and
   `drawObjectFormat` in the PPT helpers. Insetting it moves the **border**.
2. `getTableRectangle` returns `Rectangle(anchor_x, anchor_y, colCount, rowCount)`: its width and
   height are **counts**, and its x/y are **Excel's anchor cell**.
3. `calculateColumnsPosition` builds spreadsheet **column indices**, not pixel positions.
4. In print layout the `TableElementDef` **is** the border carrier (`VsToReportConverter:1173-1178`),
   so insetting its bounds moves the border.

## 5. C1: the shared base (PDF, SVG/PNG, PowerPoint)

```
VSTableDataHelper                  report/io/viewsheet/
├── VSTableHelper                  table, embedded table
│   ├── PDFTableHelper, SVGTableHelper          in scope
│   ├── PPTTableHelper       utils/inetsoft-xml-formats   in scope, inherits all geometry
│   ├── ExcelTableHelper     utils                        out; its exporter resolves zero (§7)
│   │   └── OfflineExcelTableHelper
│   └── AlertTableHelper     sree/schedule/ViewsheetAction   draws nothing
└── VSCrosstabHelper               crosstab and calc table
    ├── PDFCrosstabHelper, SVGCrosstabHelper, PPTCrosstabHelper   in scope
    ├── ExcelCrosstabHelper                                        out
    └── AlertCrosstabHelper                                        draws nothing
```

`VSTableDataHelper.write` (`:318`) runs in this order: `getColWidths` (`:331`), the title width
(`:346`), `calculateColumnsPosition` (`:348`), the round-corner clip (`:352`), the background
(`:355`), the title (`:356`), the data (`:359`), and last the border (`:370`).

### 5.1 The resolver

- `VSExporter` declares `Insets getTableCardInset(TableDataVSAssemblyInfo info)` as a default
  returning zero, so an implementer outside `AbstractVSExporter` keeps today's output. The
  helpers hold a `VSExporter` (`ExporterHelper.getExporter()`). Every implementer extends
  `AbstractVSExporter`; `PDFVSExporter`, `PoiExcelVSExporter` and `PPTVSExporter` also name the
  interface directly.
- `AbstractVSExporter` implements it: the info's `getPadding()` when `insetsTableCard()` is true,
  otherwise zero. A null padding resolves to zero.
- `insetsTableCard()` is a protected capability, true by default and false in `ExcelVSExporter` and
  `CSVVSExporter` (§7). It follows `paintsPageBackground()` (`AbstractVSExporter:1834`).
- Every site below, inside or outside the helpers, reads the inset only through this resolver.

### 5.2 Two rectangles

`VSTableDataHelper` gets two accessors built on the resolver: the **card** (the assembly rect, as read
today) and the **grid** (the card inset by the resolver's value). Each classified read goes through
one of them, or takes the inset's edges directly where it works in one axis.

### 5.3 The thirty reads

A grep for `getPixelSize(`, `getPixelOffset(`, `getPixelPosition(`, `getAssemblySize(`,
`getLayoutSize(` and `getLayoutPosition(` across the nine helpers finds 30 lines:
- 11 in `VSTableDataHelper`, 9 in `VSTableHelper`, 9 in `VSCrosstabHelper` and 1 in `PDFTableHelper`;
- none in the SVG and PPT helpers or in `PDFCrosstabHelper`.

Each is one of four kinds:
- **grid**: needs the inset;
- **card**: stays on the card;
- **Excel-only**: grid indices that must not move;
- **edge count**: row/column counts that decide which cells are outer cells.

| Line | What it computes | Kind | Action |
|---|---|---|---|
| `VSTableDataHelper:188-189` | `getObjectPixelBounds` branch test (counts against pixels) | card | unchanged |
| `VSTableDataHelper:346` | title width, `getAssemblySize(assembly, …)` | grid | − (L + R) |
| `VSTableDataHelper:399` | `writeTitle` origin | grid | + (L, T) |
| `VSTableDataHelper:518` | `getPixelBounds` cell origin | grid | + (L, T) |
| `VSTableDataHelper:560` | `getPixelBounds` bottom clamp | grid | clamp at the grid's bottom, card bottom − B |
| `VSTableDataHelper:883` | bottom-tabs design height | card | unchanged |
| `VSTableDataHelper:914`, `:920` | bottom-tabs shift of the pixel offset and layout position | card | unchanged: the whole card moves |
| `VSTableDataHelper:936` | `computeShrunkRenderedHeight` fallback for an unloaded lens | card | unchanged: the shift stays 0 |
| `VSTableDataHelper:959` | `computeShrunkRenderedHeight` cap | card | unchanged cap; the height it caps adds T + B (§5.4) |
| `VSTableHelper:51-52` | `calculateColumnsPosition` | Excel-only | unchanged |
| `VSTableHelper:120`, `:122` | `isBottom` row count for the border copy | edge count | unchanged; the copy drops inset edges instead (§5.4) |
| `VSTableHelper:145` | horizontal cull | grid | width − (L + R) |
| `VSTableHelper:203`, `:207` | `getVisibleRowCount`: rows that fit, in match mode | grid | the row budget − (T + B); it gains an inset parameter, passed by its callers at `VSTableHelper:259` and `VSCrosstabHelper:317` |
| `VSTableHelper:263-264` | `getTableRectangle` anchors | Excel-only | unchanged |
| `VSCrosstabHelper:51`, `:54-55` | `calculateColumnsPosition` | Excel-only | unchanged |
| `VSCrosstabHelper:209`, `:211` | bottom and right edge counts | edge count | unchanged |
| `VSCrosstabHelper:319-320` | `getTableRectangle` anchors | Excel-only | unchanged |
| `VSCrosstabHelper:336` | `getTablePixelHeight` | grid | height − (T + B) |
| `VSCrosstabHelper:368` | `writeData` height budget, which the trim at `:196-201` spends | grid | height − (T + B) |
| `PDFTableHelper:197` | page cap, which **writes** `info.getPixelSize().height` | card | write `height + T + B`, so the grid keeps the rows it was capped to |

`CoordinateHelper.getAssemblySize` reads `getPixelSize()` internally (`:537`, `:579`). It is
reached only through the lines above, so it needs no change of its own.

### 5.4 Sites beyond the thirty

- **The chrome bounds.** `getObjectPixelBounds` (`VSTableDataHelper:157`) first builds a rect from
  `getPixelBounds`, whose origin is now the grid's, and replaces its height with title + rows.
  - That rect moves back by (−L, −T) and grows by (L + R, T + B) before any branch reads it, so it
    describes the card.
  - The branch taken in match mode, or when the test at `:188-189` passes, starts from
    `vHelper.getBounds(info)`, which is already the card. Its non-shrink height compares against
    the grown rect.
  - Its shrink width, Σcolumns from `getShrinkTableWidth`, adds L + R, so a shrunk card is
    Σcolumns + L + R wide and title + rows + T + B tall.
- **The pixel column widths.** `VSTableDataHelper.calculateColumnWidths` (`:488`) passes a fill width
  to `ExcelVSUtil.calculateColumnWidths` (`:146`, shared by every helper despite its name). The
  parameter replaces `info.getPixelSize().width` at `:151`, so the truncation test, the last-column
  fill and the match-layout clip all move to the grid width.
- **The lens's cached widths.** `VSTableLens.initTableLensColumnWidths` (`:1708`) fills its cached
  last column to the card, once, for every exporter. It stays that way, because Excel, CSV, HTML and
  print layout read it too.
  - Each inset path that reads that cached width for the last column first takes back the fill
    beyond the grid. The rule is `last − min(last − base, L + R)`, where `base` is the lens's own
    unfilled width (the set width, or `AssetUtil.defw` when none is set), so `last − base` is the
    fill the lens added and is never negative. The site's own fill then tops the column up to the
    grid width.
  - That is the rule `getColWidths` already applies since `39a4336a6`. One shared helper applies it
    at every inset path that reads the cached width:
    - `getColWidths`;
    - `ExcelVSUtil.calculateColumnWidths` (reads at `:154`);
    - `getExpandWidth` (`AbstractVSExporter:2868`);
    - `HTMLCrosstabHelper.initRowColumns` (`:138`, §6);
    - in C2, `VsToReportConverter.calculateColumnWidths` (`:1260`, §8).

    A zero-width, hidden last column keeps its 0.
- **`getColWidths`.** The call at `VSTableDataHelper:331` passes `true` when the resolver's inset is
  non-zero, and `false` otherwise. Excel therefore stays on the card.
- **The border copy.** `applyTableBorders` (`VSTableDataHelper:423`, called at `VSTableHelper:140` and
  `VSCrosstabHelper:230`) copies the assembly border onto the outer cells. An edge with an inset
  passes `false`, or a second frame appears at the grid edge.
- **Exporter-level sites.** These run in `AbstractVSExporter` before any helper, so they read the
  resolver directly:
  - **Expand height:** `getExpandTableHeight` (`:2542`) returns the rows' height. `expandTable`
    (`:2827`, `PDFVSExporter:606`, `SVGVSExporter:329`) sizes the card as that + T + B. The
    precedent is `expandChart` adding the chart's padding (`:2045-2049`).
  - **Expand width:** `getExpandTableWidth` (`:2683`) adds L + R. `getExpandWidth` (`:2860`) sums
    the lens's cached widths and keeps the card width when the columns fit it (`:2890`). It takes
    back the lens's fill first and compares against the grid width, W − L − R. When it expands, the
    width it returns adds L + R.
  - **Region lens:** `getRegionTableLens` (`:898`, reads at `:938` and `:963`) is sized to the grid.
  - **Bottom tabs:** `:333` passes the inset to `VSTableDataHelper.applyShrunkBottomTabsShift`, whose
    `computeShrunkRenderedHeight` adds T + B. The card's bottom then stays flush with the tab strip.

### 5.5 PowerPoint

- Its only geometry override is `getPixelBounds`, which calls `super` and scales by 0.75
  (`PPTTableHelper:161-171`, `PPTCrosstabHelper:158-167`), so it inherits the grid origin.
- Each cell is its own text box.
- Rounded corners are drawn as shape geometry by `drawObjectFormat` on `getObjectPixelBounds`, so they
  follow the card.
- There is no clip: `beginRoundCornerClip` is a no-op outside PDF and SVG.

### 5.6 Tip icons

The browser keeps the crosstab's drill and date-comparison tips at the card corner. No code in the
export helpers draws them, so export has nothing to place. The manual check confirms it.

## 6. C1: HTML

HTML has its own hierarchy: `HTMLTableHelper` for table and embedded table, `HTMLCrosstabHelper` for
crosstab and calc table. The HTML helpers hold no exporter, so `HTMLVSExporter` hands each one
the resolver's value (`setCardInset`). It keeps the capability's default, true, so that value is
the table's padding.

- **One inner wrapper.** When the inset is non-zero, `HTMLTableHelper.write` (`:70`) and
  `HTMLCrosstabHelper.write` emit a `position:absolute` div at (L, T), sized W − L − R by H − T − B.
  It wraps the title (`vHelper.getTitle`) and the data.
  - The outer div keeps everything at card level: position and size, background, the border from
    `vHelper.getCSSStyles(bounds, fmt, true)`, and `border-radius` with `overflow:hidden` when the
    corner is rounded (`HTMLCoordinateHelper:363-364`).
  - At a zero inset no wrapper is emitted, so the markup is identical to today's.
- **Sizes that read the grid.**
  - The data height becomes H − title − T − B (`HTMLTableHelper:91`, `HTMLCrosstabHelper:100`).
  - `isTableYOverflow` and `isTableXOverflow` (`HTMLTableHelper:173-186`) compare against the grid.
- **Shrink.** `HTMLTableDataHelper.fixShrinkTableBounds` (`:65`) grows the card to Σcolumns + L + R
  and title + rows + T + B.
- **The crosstab's last column.** `HTMLCrosstabHelper.initRowColumns` builds every column from the
  lens's cached widths (`:138`). So it first applies §5.4's take-back to the last column, then fills
  to the grid width instead of `bounds.getWidth()` (`:148-157`).
  - The plain table needs neither step. Its widths come from the info (`HTMLTableHelper:127-136`),
    and its last column is `width:100%` (`:276-278`), which the wrapper limits.
- **Annotations** (`HTMLTableDataHelper:152-167`) are positioned apart from the table, so they add
  (L, T).
- **The scrollbar gutter.** The data's scroll div is `calc(100% + 17px)` wide
  (`HTMLTableHelper:149`). Today that places the vertical-scroll gutter just past the card's right
  edge. Inside the wrapper it lands in the right inset band, 16px wide at comfortable, so it
  overhangs the card by 1px.
  - The wrapper gets no `overflow:hidden`, because that would clip the gutter.
  - The HTML manual check looks at the rendered page. If a scrollbar that is hidden today becomes
    visible in the band, the scroll div widens by R.

## 7. Excel and CSV containment

- `insetsTableCard()` is false in `ExcelVSExporter` (`web/viewsheet/service/ExcelVSExporter.java`).
  `PoiExcelVSExporter` and `OfflineExcelVSExporter` inherit it.
- `CSVVSExporter` extends `AbstractVSExporter` directly and gets its own false.
- For those exporters every site in §5 adds 0:
  - the grid equals the card;
  - `getColWidths` stays on `false`;
  - the column fill width is the card;
  - the exporter-level sites read zero.
- Excel stays out because its grid uses fixed 20px rows, which cannot represent an 8–16px inset. CSV
  has no geometry; its false guarantees nothing shared can move it.
- Untouched either way:
  - the Excel-only reads in §5.3;
  - `PoiExcelVSUtil.calculateColumnWidths` (`PoiExcelVSExporter:388`), Excel's own copy, which does
    not go through `VSTableDataHelper`.
- Every other exporter keeps the default true: PDF, SVG, PNG, PPT, HTML, and the alert exporter in
  `ViewsheetAction`, whose table helpers draw nothing.

## 8. C2: print layout

Paths in this section are under `core/src/main/java/inetsoft/`. A bare line number is in the file
named last. C1 changed none of `VsToReportConverter`, `TableElementDef`, `TablePaintable` or
`PDFVSExporter`, and its manual check held both print PDFs to their baselines.

### 8.1 What print layout draws today

- **Only PDF.** Only `PDFVSExporter` runs `VsToReportConverter` (`report/io/viewsheet/pdf/PDFVSExporter:112`).
  That includes the composer's print-layout preview, which swaps its layout in before the export
  runs (`web/viewsheet/service/VSExportService:342-359`).
- **Entry point.** All three table types reach `addTable` (`uql/viewsheet/internal/VsToReportConverter:1104`).
  Its bounds come from `layoutPosition`/`layoutSize` (`getPixelBounds`, `:959`), not from the pixel
  offset and size.
- **The title** is its own `TextBoxElementDef`, from `createTitle` (`:1372`). Its borders merge the
  TITLE path with the object (`getTitleBorders`, `:2167`):
  - top, left and right take the heavier of the two;
  - the bottom comes from the TITLE path alone.

  The table element starts 1px above the title's bottom (`:1415-1418`).
- **The table** is a `TableElementDef` in a section band.
  - `printFixedContainer` (`report/internal/StyleCore:935`) gives it the element's x and width, and
    lets it grow to the page bottom.
  - The elements below are pushed down by the bottoms of the paintables it adds.
- **Each page region** is a `TablePaintable`.
  - Its cells start at `printBox.x + 1` (`report/internal/TablePaintable:656`).
  - `paintBorder` (`:1412`) draws the left and right borders at the band's print width, down to the
    region's content bottom, and the bottom border on the last region only.
  - So the card's height follows the content, not the design box (baseline `modern-print.pdf`,
    page 3).
  - A continuation page has no card top border.
- **The background** is only a per-cell fallback (`:1020`).
- **Border suppressions.** Three of them hide an outer cell's own border where the table border would
  double it:
  - left, `:747` and `:760`;
  - right, `:799`, when `isContentMatchTableWidth` (`:4507`) holds;
  - bottom, on the last region, `:817`.

### 8.2 The inset reaches the converter

- **No exporter today.** The converter is built without an exporter (`VsToReportConverter:80`).
- **The setter.** It gains a setter for an inset lookup, which resolves zero when none is set.
  `PDFVSExporter:112` passes `this::getTableCardInset`, and PDF's `insetsTableCard()` is true, so the
  lookup returns the table's padding.
- **No scaling.** The inset is not scaled by the print layout's `scalefont`. That matches the chart's
  padding (`VsToReportConverter:1507-1510`) and the cell padding (`:1232-1236`).
- **At zero,** every path in §8.3–§8.6 is skipped, so the output is today's.

### 8.3 The card-top box and the title

W and H are the card's width and height. titleH is the title lane's height, and 0 when the title is
hidden. With a non-zero inset:

- **The card-top box** is a blank `TextBoxElementDef` at (x, y, W, T + titleH). It carries:
  - the borders (top, left, `NO_BORDER`, right), from the OBJECT format, with its border colour;
  - the OBJECT background.

  The borders are set explicitly. Through `addTextBoxElement0`, `applyFormat`'s default-border rule
  (`:2118`) would turn every `NO_BORDER` edge into `THIN_LINE`, bottom included, because
  `TableDataVSAssemblyInfo` is not on its exception list. The chart sets its own borders after the
  same call (`:1495`).
- **The title box** sits at (x + L, y + T, W − L − R, titleH).
  - It is added after the card-top box, so the stable z-index sort (`Arrays.sort`, `:3059`) paints
    it on top.
  - It keeps its TITLE-path format, and only its TITLE-path borders; the object's are not merged in.
- **A hidden title** creates no title box. The card-top box is T tall and still carries the top
  border. An unpadded title-hidden table keeps today's missing top.
- **The table element** starts 1px above the card-top box's bottom, as it starts 1px above the title
  today, and gets the card inset (0, L, B, R). Its height subtracts T and the title height, whether
  or not the title shows. The hidden-title subtraction (`:1439-1447`) predates this work and stays.
- **Rounded corners:** not added. Print layout draws none for tables today.

### 8.4 The converter's arithmetic

- **`calculateColumnWidths`** (`:1246`) reads two card widths: the layout width (`:1258`) and the
  pixel width (`:1278-1279`). Both lose L + R.
  - A column with no width set reads the lens's cached width (`:1260`). For the last column, that
    read goes through §5.4's take-back, `getColumnWidthInGrid`
    (`report/composition/VSTableLens:346`), before the fill.
  - The lens here is the converter's `RegionTableLens`, a `VSTableLens` that copies its base's
    widths (`report/composition/RegionTableLens:30`, `:51-53`).
- **The fit-page decision** (`VsToReportConverter:1144-1149`) compares the column total against
  W − L − R, in both branches.
- **`computePrintLayoutTableHeight`** (`:1036`, compared at `:1004`) adds T + B, so a shrunk table in
  bottom tabs still ends flush with the tab strip.

### 8.5 `TableElementDef`

- **The field.** `cardInset` is a plain field: null by default, beside `borders` and `bcolors`
  (`report/internal/TableElementDef:2674-2675`). Its setter stores a copy.
  - It is not in `TableElementInfo`, so a report's XML never carries it.
  - `clone()` (`:2484`) shares it, which is safe because it is never written in place.
  - It is not named padding: `getPadding()` (`:496`) is the cell padding.
- **The grid area.** `calcRemainingArea` (`:1021`) returns its area at x + L, W − L − R wide. That area
  feeds:
  - `buildColumnWidth` (`:1359`), so `TABLE_FIT_PAGE` scales the columns to the grid;
  - the horizontal cut;
  - `TABLE_FIT_CONTENT_PAGE`'s last-column adjustment.

  The next-page area (`:1565-1571`) is inset the same way.
- **B.** A region that reaches the table's last row counts B.
  - Both row-fit loops (`:1640`, `:1656`) add B before comparing, once they reach the last row. So a
    last row that fits without B, but not with it, moves to the next page with its band.
  - `fitNext` (`:854`) adds B for the last region, the one `TablePaintable` flags `lastregion`.
  - A horizontally split table's earlier segments of its last rows reserve B without drawing it.
    They draw no bottom border today either.

### 8.6 `TablePaintable`

- **Its own field.** The paintable reads the inset from the element in its constructor (`:69`) and
  keeps it in a non-transient field of its own. A swapped-out page comes back with a bare
  `BaseElement` (`:3392`), so no paint-time code reads the inset from the element.
- **The grid box.** `box.x` (`:656`) adds L. All the cell math reads `box` and stays untouched:
  `getCellBounds`, `locate` and the paint loops.
- **The card geometry.** Three accessors report the card: x − L, the width plus L + R, and plus B on
  the last region.
  - `getBounds` (`:1880`) feeds `printFixedContainer`'s push-down.
  - `getBounds2` (`:1902`) feeds `SectionElementDef`'s rewind test (`report/internal/SectionElementDef:1508`).
  - `getHeight` advances `printHead` (`TableElementDef:829`).
- **Location.** `getLocation` (`TablePaintable:1931`) and `setLocation` (`:1913`) work on the card
  origin too, so `setLocation(getLocation())` moves nothing. It matters because paintables are moved
  from their bounds:
  - `StyleCore` moves them with `setLocation(new Point(getBounds().x, …))` (`StyleCore:1434`,
    `:766-818`);
  - `ReportGenerator.adjustLocation` moves them from `getLocation` (`report/internal/ReportGenerator:343`).

  A bounds origin that differs from the location origin would move the table by L on each call.
- **The background.** Before the cells, a non-null element background fills the rect `paintBorder`
  frames for the region:
  - across, from the card's x through the band's print width, capped at the page the way
    `paintBorder` caps it;
  - down, from the region's top to its bottom, B included on the last region.

  The per-cell fallback stays.
- **The border.** `paintBorder` is unchanged. It reads `getBounds`, so the left and right borders stay
  at the card edge, and the last region's bottom border lands below B.
- **The suppressions.** Each of the three in §8.1 skips its edge when that edge has an inset, so the
  outer cell keeps its own border, B, L or R apart from the card's. This is print layout's
  counterpart to C1's `applyTableBorders` fix (§5.4).
- **`isContentMatchTableWidth`** (`TablePaintable:4507`) compares against the band width less L + R.
  `refreshLastCol` (`:4486`) follows it.
- **Page breaks.** A continuation region gets no top inset and no top border. The inset is
  (0, L, B, R) on every region.

A zero or null inset leaves every path as it is. Classic reports share this code but never set the
inset, which is why C2 is its own PR.

## 9. Testing

1. **Baselines first.** Before any slice C code, on this branch as it stands, export one fixture
   viewsheet in PDF, PNG, HTML, PPT, Excel and print layout, the formats the Export dialog offers.
   - The fixture holds a marked table, crosstab and calc table, an unmarked table and a marked chart.
   - These files are the reference for the legacy guarantee, and they cannot be recreated once C1
     code lands.
2. **C1 unit tests.** Each runs at a non-zero inset and at zero.
   - The non-zero case is written first and watched fail.
   - The zero case passes from the start: it guards today's behaviour, with literals derived by
     hand.
   - **Resolver:**
     - PDF, SVG, PPT and HTML return the padding.
     - The POI and offline Excel exporters and the CSV exporter return zero.
     - A null padding returns zero.
   - **Grid reads:**
     - `getPixelBounds` origin and bottom clamp.
     - `writeTitle` position and width, captured through a recording subclass.
     - The `calculateColumnWidths` fill, with the take-back for a width-less last column.
     - The horizontal cull.
     - The crosstab height budget.
     - `getVisibleRowCount`.
   - **Card reads:** `getObjectPixelBounds` in its non-match, match and shrink branches.
   - **Other fixes:**
     - `applyTableBorders` drops the inset edges.
     - The PDF page cap writes back `height + T + B`.
     - Expand-mode height and width, including `getExpandWidth`'s take-back and its compare
       against the grid width.
     - The region-lens size and the bottom-tabs shift.
   - **Excel proof:** a padded table pushed through `ExcelTableHelper` and `ExcelCrosstabHelper`
     gives a grid equal to the card. With the resolver tests, this is the mechanical guarantee that
     Excel output cannot move.
   - **HTML**, asserting on the real markup the helpers write to a `PrintWriter`:
     - the wrapper with (L, T, W − L − R, H − T − B), and its absence at zero;
     - the data height, the shrink bounds, the crosstab last-column fill with its take-back, and the
       annotation offset.
3. **C2 unit tests.** Each runs at a non-zero inset and at zero, under the same rules as C1's: the
   non-zero case is written first and watched fail, and the zero case uses literals derived by hand.
   - **The converter**, calling its private methods by reflection, as `PrintLayoutCellPaddingTest`
     does:
     - the column-width take-back, and the fill to W − L − R;
     - the fit-page decision against W − L − R;
     - `computePrintLayoutTableHeight` adds T + B;
     - `addTable`'s three rects: the card-top box with borders (top, left, 0, right), and the title
       box keeping only its TITLE-path borders;
     - a hidden title gives a card-top box T tall that carries the top border;
     - `PDFVSExporter` hands the converter its resolver.
   - **`TableElementDef`:**
     - the columns are laid out in the grid area;
     - a last row that fits without B, but not with it, moves to the next region;
     - `fitNext` counts B.

     These drive `print()` on a `TabularSheet` with a set print box. The plan's first task confirms
     that works without the full report generator; if it does not, the arithmetic moves into
     testable helpers.
   - **`TablePaintable`:**
     - `getBounds`, `getBounds2` and `getHeight` report the card, with B on the last region only;
     - `setLocation(getLocation())` moves nothing;
     - the cells start at x + L;
     - each edge skips its border suppression only when it has an inset;
     - `isContentMatchTableWidth` compares against the grid;
     - the background covers the inset bands, checked by pixel on a `BufferedImage`;
     - the inset survives serialization;
     - at zero, the region and cell bounds equal today's, which covers classic reports too.
4. **Manual checks**, against the baselines.
   - **What to export:**
     - a marked table, crosstab and calc table at each density, beside a marked chart;
     - PDF, PNG (which covers SVG), HTML and PPT for C1, and print layout for C2;
     - match layout and expand mode, shrink-to-fit, bottom tabs and rounded corners.
   - **What to confirm:**
     - the inset matches the chart's;
     - the border and background sit at the card edge;
     - no row or column is clipped;
     - the unmarked table's HTML diffs empty against the baseline and its PNG compares identical,
       and its PDF and PPT look identical (SVG is not an export format of its own; it is PNG's
       renderer, and PDF and PPT containers carry timestamps);
     - a padded table's Excel output has the same cell layout as its baseline;
     - HTML: where the scrollbar gutter lands (§6);
     - crosstab: no tip icons in any format.
   - **C2's own baselines, before any C2 code.** The baseline print layouts hold TableView1,
     TableView2, TableView4, Crosstab1, TableView7 and Tab1. So:
     - add Chart1 (the reference inset), TableView5 (title hidden) and TableView3 (wide) to both
       print copies. The fixture's layouts use `TABLE_FIT_PAGE` (`tableLayout="1"`), so TableView3's
       columns are scaled into the grid rather than split across pages, and a horizontal split stays
       outside the manual checks;
     - take the conditions off their tables, because a conditioned table prints unreliably (F5,
       §10);
     - recapture the legacy and modern print PDFs at the branch point. C1 left print output
       unchanged, so they still show pre-slice-C output.
   - **C2's checks,** at comfortable, compact and dense:
     - the table's inset matches Chart1's;
     - the border and background sit at the card edge;
     - at a page break, the last row, its B band and the bottom border land on the same page;
     - a continuation page has no top band and no top border;
     - a padded title-hidden table has its top border;
     - no row or column is clipped;
     - the legacy print PDF keeps its page count, every word's position and every vector drawing.
       This is C1's MC-1 method, including its restart once the fonts folder exists.
5. **Harness checkpoint.** Once C1 runs locally, reconsider a render harness. The pieces exist:
   `RuntimeViewsheetExtension` with `@SreeHome(importResources = …)` opens an imported viewsheet into
   a live sandbox, and `AbstractVSExporter.getVSExporter(type, theme, stream)` with
   `export(box, sheet, helper)` drives an exporter. If a time-boxed spike exports SVG from a `.vso`,
   the manual byte-identity diff becomes a test.

## 10. Open items and follow-ups

- **HTML scrollbar gutter** (§6): settled by the rendered manual check.
- **Slice B leftover:** a shrink-to-fit table whose last column was resized in the composer before
  `39a4336a6` has a card-filling width saved. It keeps a scrollbar in the browser until that column
  is dragged again. It is saved author data, so it is not rewritten.
- **For slice A (#5617), found while mapping export:**
  - **F2:** `HTMLCoordinateHelper.getPaddingString` (`report/io/viewsheet/html/HTMLCoordinateHelper.java:303-304`)
    writes `padding-top` from `padding.right`. Slice A's asymmetric cell padding (6 by 8 at
    comfortable) is the first common value to reach it: every HTML-exported cell gets 8px on top
    instead of 6. The fix is one line. It also changes text-assembly HTML wherever top ≠ right.
  - **F3:** `VSTableDataHelper:866-867` says print layout renders unpadded row heights. Slice A made
    them padded; the matching comment at `VsToReportConverter:969-973` is already updated.

- **F4, found in the baselines: the modern Excel export drops tables.** It predates slice C: the
  baselines were exported from code identical to slice B's `39a4336a6`, and the legacy Excel
  exports are complete.
  - **Origin:** not slice A, B or C. The Excel title and expand logic is old code that is also
    on main. The epic's density heights (`1133d86e2`, #5150) trigger it: 28px data rows, a 30px
    header and a 30px title each span two 20px Excel rows, so the epic alone reproduces it.
    Slice A made the expand estimate one row worse, because `94524d20a` missed the two
    expand-height readers that still round the stored height. The fix belongs in a separate PR
    against `epic-74519`.
  - **Match layout.** A shrunk table's title is handed the table's per-row Excel counts
    (`ExcelTableHelper.writeTitleCell`), so it merges the header plus the first data row, four
    rows (`B27:J30`), while the body starts two rows down (`B29:C30`). POI throws
    `IllegalStateException` from `ExcelTableHelper.writeData:277`, and the bad region stays in
    the sheet. Every table written after it then fails with the same message. In the fixture,
    that drops six of nine tables.
  - **Expand.** The space reserved for an expanded table rounds the stored row height, 16px to one
    row, while the writer draws the padded 28px as two. The tables below are pushed down too
    little and overlap (`A78:B79` against `B78:F79`), so the crosstab and the calc table are
    dropped (`ExcelCrosstabHelper.writeData:241`).
  - **For slice C:** Excel is outside it, so after C1 the modern Excel exports must reproduce
    exactly, missing sheets included. Until F4 is fixed, no export can show a padded shrink
    table, crosstab or calc table in Excel.
  - The evidence and the fixture are in `community/.superpowers/baselines/density-padding-export/`
    (`README.txt`, known issue 1).

- **F5, found in the baselines: print layout can drop a conditioned table's rows.** It predates slice C,
  and it is parked; the fix belongs in its own PR.
  - **What it looks like.**
    - Both non-print PDFs show TableView4 ("TableShrink") with its 5 rows.
    - In print layout it shows its header only in `legacy-print.pdf` (page 4), and 3 of its 5 rows
      plus a blank one in `modern-print.pdf` (page 6).
    - TableView9, which also has a condition, prints its header only in legacy print, and all 4 rows
      in modern.
  - **The probe, 2026-09-28**, on a Save As copy of DPX Legacy Print:
    - with TableView4's Shrink to Fit off, it still prints its header only;
    - with its conditions removed, its rows print, while TableView9, which keeps its condition,
      still prints its header only.

    The condition is the trigger, not shrink.
  - **Where it sits.** A header-only table whose bottom border sits right under the header means
    `layout()` found no printable body rows. So the rows are already gone or zero-height in the lens
    or `calculateRowHeights`, which C2 reads but does not change.
  - **Not explained.** Legacy and modern differ (0 of 5 against 3 of 5) on the same data, which a
    plain filter does not account for. A guess, not verified: print conversion reads a conditioned
    table before its rows are complete.
  - **For slice C:** C2's print checks use tables without conditions (§9.4). The probe exports are in
    the baselines folder, under `f5-probe/`.

## 11. Branching and PRs

- `feature-density-padding-export` is stacked on slice B (#5618), which is stacked on slice A (#5617).
  It is not cut from `epic-74519`.
- It was pushed at `4f742abe8` on 2026-09-28 and is #5808, against slice B's branch. Before that it
  had no upstream on purpose, because `git switch -c` had set slice B's branch as its upstream.
- Rebase it onto slice B whenever slice B takes more fixes, and onto `epic-74519` once #5617 and
  #5618 merge.
- C1 and C2 are two PRs, C2 after C1. No Redmine issue exists yet, so the branch names follow slices
  A and B.
- C1 is #5808, from `feature-density-padding-export`. C2 is on `feature-density-padding-print-layout`,
  cut from the C1 head `4f742abe8`, so C2 commits never reach #5808.
  - It has no upstream until it is pushed with
    `git push -u origin feature-density-padding-print-layout`.
  - Its PR targets `feature-density-padding-export` and opens after #5808.
  - Rebase it onto C1 whenever C1 takes review fixes, and onto `epic-74519` once #5617, #5618 and
    #5808 merge.
  - Nothing outside `community/` changes, so there is no enterprise PR.
