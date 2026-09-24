# Density padding — chart inset per tier, table card inset, table cell padding

**Date:** 2026-09-23
**Verified against:** community `epic-74519` @ `e7e83e9c2`. Every code claim below was checked
against that commit's files.
**Covers:** completing the density feature's spacing half — making the chart's card inset vary by
density tier, giving table/crosstab/calc-table assemblies a card inset of their own, and giving
their cells density-derived content padding.

## 1. Why

Density today controls heights only: `VSDensityDefaults` carries row, header, title and control
height matrices, and every one of them is a *height*. The card inset — the one spacing value the
track shipped — is a flat 12px on all four edges regardless of tier
(`VSObjectChromeDefaults.modernChartPadding()`, `MODERN_CARD_INSET = 12`), and it exists on charts
only. A table card sits flush against its border next to a chart card with 12px of breathing room,
and a dense table's rows are shorter than a comfortable table's while the text inside them sits at
exactly the same 1px offset from the cell edge.

So "density" currently means "rows get shorter." This spec makes it mean "the whole card tightens."

Three items, all scoped to charts and the three table types. Selection lists, selection trees and
every other assembly are deliberately out of scope.

1. Chart card inset varies by density tier.
2. Table, crosstab and calc table get a card inset, at parity with the chart's.
3. Table, crosstab and calc table cells get density-derived content padding.

## 2. What already exists

Four facts, each of which removes work this spec would otherwise have had to invent. All four were
verified in code rather than assumed.

**Storage for the card inset already exists on every assembly.** `VSAssemblyInfo.padding`
(`:1784`) is an `Insets`, serialized at `:869` and read back at `:914`. Charts, gauges and text
read it; tables ignore it. Item 2 needs no new field.

**Table cell padding already has a browser and export pipeline, on four of its six surfaces.**
`TableLens.getInsets(r, c)` reaches `BaseTableCellModel:136-140` and `:315-319`, which write it to
`vsFormatModel.padding`; `vs-table-cell.component.html:30-33` binds all four edges. The same value
reaches export through `VSTableHelper:172` and `VSCrosstabHelper:270` into
`writeTableCell(..., Insets padding)`, where `ExportUtil:211-215` insets the cell bounds, and
through `HTMLTableHelper:288`. Today it returns `null` unless a `format.css` `CSSTableStyle`
defines one, so cells fall back to the hardcoded `padding: 1px 2px` in
`vs-table-cell.component.scss:58`. Item 3 mostly gives that `null` a source rather than building a
pipeline — but two surfaces are genuinely missing and are called out where they land: `vs-simple-cell`
never binds padding at all (§6.1), and print layout never reaches any of these sites (§7.1).

**Table row height is resolved at read time, not seeded.** `VSTableLens:1783` and
`BaseTableService:466/1168` call `VSDensityDefaults.rowHeight(ctx)` live, guarded by
`!isUserDataRowHeight() && dataRowHeight == AssetUtil.defh`. Rebalancing that matrix therefore
needs **no migration**: nothing is stored to rewrite, and an already-marked table picks the new
value up on its next render.

**Author-set row heights are already stored as content heights.**
`ComposerVSTableService:958/969/1051` subtracts the padding on resize before calling
`setDataRowHeight`/`setHeaderRowHeight`, and sets `setUserDataRowHeight(true)`. So an additive
padding model is already round-trip exact for author edits, provided every `getCSSRowPadding` call
site moves to the combined accessor in the same change.

## 3. Decisions

### D1 — The table card inset is at full chart parity

Everything inside the assembly border is inset: the title lane, the header rows, the body and the
scrollbars. The border, the background fill and the round-corner clip stay at the assembly edge.

This is what the chart already does — `VGraphPair:2966` translates the graph by
`padding.left, padding.top + titleHeight` while the object format draws at the assembly bounds, and
[the geometry decisions](./chart-card-geometry-decisions.md) §1.1 records that `padding` *is* the
card inset with the title lane inside it.

Rejected: insetting the grid but leaving the title lane spanning the card. It would have avoided
re-touching the lane geometry that L′ and L″ settled, at the cost of a table card whose top edge
reads differently from every chart card beside it — which is the inconsistency this work exists to
remove.

### D2 — The value matrices

| | comfortable | compact | dense |
|---|---|---|---|
| chart card inset | 16 | 12 | 8 |
| table card inset | 16 | 12 | 8 |
| cell padding x | 8 | 6 | 4 |
| cell padding y | 6 | 4 | 3 |

Legacy (unmarked) values are unchanged: chart `Insets(10,10,10,10)`, table `Insets(0,0,0,0)`,
cell `null`.

The cell padding row is not new — it is the matrix
[the design spec](./visualization-design-spec.md) settled at `:584-585` and which only ever reached
CSS tokens for non-assembly DOM surfaces (`_viz-tokens.scss`). This implements the server half it
always described.

The compact column holds today's shipped chart inset of 12. That matters because
`defaults.properties:229` sets `viewsheet.density=compact`, so the org default tier reflows
nothing — the same anchoring principle `VSDensityDefaults`' class comment records for
`dense = AssetUtil.defh` on the height matrices.

Charts and tables share the inset matrix. One "card inset" concept, one set of numbers.

### D3 — Cell padding y is additive; cell padding x sits inside the column

Vertical padding adds to the row height, exactly as a `format.css` `CSSTableStyle` padding does
today (`VSTableLens:2153`, `BaseTableService:492/498`). The stored row-height matrix shrinks to
absorb it, so rendered heights land back on the numbers that shipped:

| | comfortable | compact | dense |
|---|---|---|---|
| data row **stored** | 16 | 16 | 14 |
| + padding y × 2 | 12 | 8 | 6 |
| = **rendered** | **28** | **24** | **20** |
| header row **stored** | 18 | 18 | 16 |
| + padding y × 2 | 12 | 8 | 6 |
| = **rendered** | **30** | **26** | **22** |

Comfortable and compact both store 16 for data rows. That is correct rather than a collision: the
design spec keeps font size at 13px for both tiers, so those two tiers differ in whitespace, not in
text box. Dense yields a 14px content box under a 12px font, which is tight; it was raised during
design and accepted.

Horizontal padding is **not** additive, and this is forced rather than preferred.
`getColumnWidthWithPadding` is called from `AbstractVSExporter:2728/2883`, `ExcelVSUtil:174/316`,
`HTMLTableHelper:134` and `HTMLCrosstabHelper:143` — but `BaseTableService` adds padding to row
heights only and never to column widths. An additive horizontal padding would therefore widen every
*exported* column while the browser's stayed put, producing a live/export mismatch on every marked
table. Vertical is safe precisely because both halves already add it.

Consequence: a column's content box narrows by `2 × padding-x` at each tier. Column widths are
author- and content-driven rather than density-derived, so nothing in the ladder depends on them.

### D4 — `cellHeight` splits off its own matrix

`VSDensityDefaults.cellHeight(ctx)` (`:118`) currently shares `rowHeightForMode` (`:179`). Its only
consumer is `SelectionBaseVSAssemblyInfo.getEffectiveCellHeight()` (`:140`), which serves selection
lists and trees — out of scope here, and with no additive padding path of their own. If it kept
following the shrunk matrix, selection cells would drop to 14–16px with nothing added back.

So `cellHeight` gains `selectionCellHeightForMode`, holding today's 28/24/20, while
`rowHeightForMode` and `headerRowHeightForMode` take the shrunk values. The duplication is
deliberate and carries a comment saying why: the two matrices were only ever equal because no
padding existed between them.

### D5 — Authorship uses the mechanisms already in place; no new flags

| Value | Field | Authorship | Legacy |
|---|---|---|---|
| chart card inset | `VSAssemblyInfo.padding` (`:1784`) | `userPadding`, hoisted | `10,10,10,10` |
| table card inset | same field | same hoisted flag | `0,0,0,0` |
| table cell padding | new `CompositeValue<Insets> cellPadding` on `TableDataVSAssemblyInfo` | `hasUserValue()` / `resetUserValue()` | `null` |

`userPadding` currently lives on `ChartVSAssemblyInfo` only — field `:3231`, accessors `:2957/:2964`,
`writeAttributes` `:1547`, `parseAttributes` `:1585-1586`, `copyInfo` merge `:1992-1993`. It is
hoisted to `VSAssemblyInfo`, beside the field it guards. The attribute name stays `userPadding`, so
existing assets parse unchanged, and the write moves to the base class so it is emitted exactly
once. `ChartVSAssemblyInfo.resetCardInset` hoists with it as `VSAssemblyInfo.resetPadding(ctx)`:
its `CSSDictionary.isPaddingDefined` check reads the object format's CSS param, which is generic.

`cellPadding` copies the shape proven on `SelectionBaseVSAssemblyInfo` — `CompositeValue<Insets>`
field (`:1068`), `cellPadding=` attribute (`:570`), parse (`:604`), `clone()` in `copyInfo` (`:703`).

It takes **USER and DEFAULT tiers only — no CSS tier**. A table's CSS cell padding already arrives
through a different mechanism (`CSSTableStyle`, the table *style*, not the assembly's CSS class),
and installing a second CSS source would give two mechanisms a claim on the same value.
`CompositeValue`'s `USER > CSS > DEFAULT` precedence (`CompositeValue:40-42`) supplies the
follow-the-default behaviour with no boolean: the checkbox is `!hasUserValue()`, and checking it
calls `resetUserValue()`.

### D6 — Two slices, one spec

Slice A is items 1 and 3; slice B is item 2. Slice B carries essentially all of the regression risk
(new geometry across five render surfaces) and roughly four to five times slice A's cost, and
nothing in B is depended on by A. They are phased so B never blocks A, and so A proves the
seed and authorship plumbing that B rides on.

## 4. Seeding and resolution

### 4.1 Matrices

`VSDensityDefaults` gains, beside the existing height matrices:

```java
static Insets chartPaddingForMode(String mode)          // 16 / 12 / 8 uniform
static Insets tablePaddingForMode(String mode)          // 16 / 12 / 8 uniform
static Insets cellPaddingForMode(String mode)           // y 6/4/3, x 8/6/4
static int    selectionCellHeightForMode(String mode)   // 28 / 24 / 20 (D4)
```

plus `chartPadding(VizContext)`, `tablePadding(VizContext)` and `cellPadding(VizContext)`, each
returning the D2 legacy value when `!ctx.modern`. `rowHeightForMode` and `headerRowHeightForMode`
take the D3 stored values; `cellHeight(ctx)` switches to `selectionCellHeightForMode`.

Every `Insets` accessor returns a fresh object. `Insets` is mutable and
`VSObjectChromeDefaults.modernChartPadding()` already documents the rule.

`VSObjectChromeDefaults.modernChartPadding()` gains a `VizContext` and delegates;
`legacyChartPadding()` stays a constant. The split follows how those two classes already divide —
chrome colours in `VSObjectChromeDefaults`, density-derived sizes in `VSDensityDefaults`.

### 4.2 Seeding

In `seedChromeDefaults(ctx)`, both branches writing so Revert restores rather than leaving a stale
modern value — the rule `VSAssemblyInfo:1260`'s comment states and `TableDataVSAssemblyInfo:1587`
already follows for the card background and title lane.

- `ChartVSAssemblyInfo:122` — the existing padding branch starts passing `ctx`. Nothing else moves.
- `TableDataVSAssemblyInfo.seedChromeDefaults` — two new branches:
  - `if(!isUserPadding())` write `ctx.modern ? tablePadding(ctx) : new Insets(0,0,0,0)`
  - `if(!cellPadding.hasUserValue())` write `ctx.modern ? cellPadding(ctx) : null` at `Type.DEFAULT`

A density change already re-fires this: `ViewsheetPropertyDialogService:345` calls
`VizModernizeUtil.reseed(viewsheet)` whenever `vizDensityChanged`, and `reseed` collects every
assembly (`VizModernizeUtil:106-108`). No new propagation is needed.

### 4.3 Cell padding resolution

One source, two seams, so they cannot disagree:

```
effective(lens, info, r, c) = lens.getInsets(r, c) != null
                                 ? lens.getInsets(r, c)     // format.css wins, behaviour unchanged
                                 : info.getCellPadding()    // seeded or author value
```

**Content inset** — where the text sits inside the cell. Five call sites, each of which already
holds both the lens and the info: `BaseTableCellModel:136/140` and `:315/319`, `VSTableHelper:172`,
`VSCrosstabHelper:270`, `HTMLTableHelper:288`.

**Row growth** — how much taller the row is, vertical only, and derived from the same rule rather
than restated. For each column take the *effective* inset above, then take the max across the row:

```
rowGrowth(r) = max over c of ( effective(lens, info, r, c).top + .bottom )
```

`getCSSRowPadding` (`:2053`) already loops the columns taking a max; the change is that its per-cell
read falls back to `info.getCellPadding()` instead of contributing nothing.

Deriving it this way rather than as `max(cssRowPadding, seededY × 2)` matters in the two mixed
cases. Where a `CSSTableStyle` covers every cell, nothing falls back and the row grows by the CSS
amount alone — a blunt `max` would instead grow existing `format.css` customers' rows whenever the
seeded value happened to be larger. Where a style covers only some cells, the uncovered ones
contribute the seeded inset and the row is tall enough for both.

Sites: `VSTableLens.getRowHeightWithPadding` (`:2148`), `BaseTableService:492/498`,
`VSTableHelper:209`, `ComposerVSTableService:958/969`, `BaseTableCellModel:169`.

**Column growth** — unchanged, CSS-only, per D3. `getCSSColumnPadding`,
`getColumnWidthWithPadding` (`:2159`), `ComposerVSTableService:1051` and `BaseTableCellModel:170`
are untouched.

The new accessors take a `TableDataVSAssemblyInfo` parameter, matching the convention `VSTableLens`
already uses for `getCSSHeaderRowHeight(info)` (`:2017`) and `getCSSDataRowHeight(info)` (`:2032`).

Rejected: a `cellPadding` field on `VSTableLens` with a `getInsets` override. Tidier at the call
sites, but the lens is cached in `ViewsheetSandbox`'s `dmap` under `DataMap.VSTABLE`
(`ViewsheetSandbox:5226/5237`), so a stored padding would go stale the moment an author edited it
and would need a cache invalidation nothing else in this feature requires.

Critically, this does **not** disturb the density-height resolution that runs immediately before
the padding addition at `BaseTableService:466` then `:492`: the density height is resolved from the
stored matrix, then the padding is added. The two compose to the D3 rendered heights.

## 5. Dialogs

All three table property dialogs share one component. `table-view-property-dialog.component.html:28`,
`crosstab-property-dialog.component.html:28` and `calc-table-property-dialog.component.html:28` all
render `<table-view-general-pane [model]="model.tableViewGeneralPaneModel">`. Both padding groups go
in once.

Two `<padding-pane>` instances on `table-view-general-pane`. The component keeps all of its
behaviour — it already carries the `followsDefault` checkbox and disables the four steppers while
following (`padding-pane.component.ts:68-83`) — and gains exactly one thing: an optional `label`
input, because its legend is currently the hardcoded `_#(Padding)`
(`padding-pane.component.html:22`). The input defaults to that string, so the chart, gauge and text
panes that already use the component are unaffected; the second table instance passes
`_#(Cell Padding)`.

`TableViewGeneralPaneModel` gains `paddingPaneModel` and `cellPaddingPaneModel`.

Load and apply wiring goes into `TableViewPropertyDialogService`, `CrosstabPropertyDialogService`
and `CalcTablePropertyDialogService`, copying `ChartPropertyDialogService:147-152` (load) and
`:396-419` (apply) verbatim:

- `followsDefault` is `null` when `getVizMark() == null`. The checkbox is then hidden and the pane
  behaves exactly as it did before the checkbox existed — a real edit is stored, nothing else.
- Otherwise it is `!isUserPadding()` / `!cellPadding.hasUserValue()`.
- Checking it calls `resetPadding(ctx)` / `cellPadding.resetUserValue()` and clears the flag, the
  same shape Revert uses. Storing the legacy value there would pin it.

`Set Cell Size` (`TableCellResizeDialogComponent`, reached from `base-table.ts:2212`) is untouched.
It is per-selected-cell and drives column- and row-resize events; padding is uniform per assembly.

## 6. Browser rendering

### 6.1 Cell padding (slice A)

`vs-table-cell.component.html:30-33` already binds all four edges, and the row height it sits in
already includes the vertical padding, so a table's own cells need no template change.

One gap: **`vs-simple-cell` has no padding binding.** Its template
(`vs-simple-cell.component.html:19-24`) renders a bare `simple-cell-container` and its SCSS
hardcodes `padding: 0 2px 0 2px` (`:24`). Crosstab and calc table use it for span and placeholder
cells — five usages, four in `vs-crosstab.component.html` and one in `vs-calctable.component.html`.
Without the same four bindings those cells' text misaligns against the `vs-table-cell` neighbours
they sit beside.

`BaseTableCellModel`'s format-model cache (`:133-140`) computes by `VSFormat` alone and then builds
a fresh model when the padding differs, without re-caching it — and `setPadding` mutates a shared
instance. A uniform per-assembly padding makes every cell agree, so this is safe as written. It is
noted rather than fixed: narrowing it is not this feature's scope.

### 6.2 Card inset (slice B)

`base-table.ts` splits its geometry in two.

| Reads the **card** rect (assembly bounds) | Reads the **content** rect (card − padding) |
|---|---|
| `border-div`, `z-index-wrapper` background, round-corner clip | title lane, header rows, body, scrollbars, drop lines |

- New `getCardWidth()` / `getCardHeight()` return what `getObjectWidth()` (`:1678`) and
  `getObjectHeight()` (`:1641`) return today.
- `getObjectWidth()` / `getObjectHeight()` subtract the inset.
- New `getContentLeft()` / `getContentTop()` offset the grid.
- `updateTableHeight()` subtracts it too. It is abstract on `base-table.ts:380` and implemented
  per component, so all three implementations change (e.g. `vs-table.component.ts:331`).
- `BaseTableModel` gains `padding`, set server-side from `info.getPadding()`.
- `vs-table.component.html`, `vs-crosstab.component.html` and `vs-calctable.component.html` rebind
  `border-div` and `z-index-wrapper` to the card rect and everything else to the content rect.

The L″ anchored-strip predicate is unaffected: it measures the title lane's *height*, which the
inset does not change.

Regression surface to exercise: max mode (`model.maxMode` / `maxSize`), shrink-to-fit
(`model.shrink`, which already special-cases `getObjectWidth`/`getObjectHeight`), wrapped headers
(`model.wrapped`), `scrollWrapper`, round-corner clipping, and the binding pane's drop lines.

## 7. Export

### 7.1 Cell padding

PDF, SVG/PNG and HTML need no new work beyond the five-site helper in §4.3. `ExportUtil:211-215`
already insets the cell bounds from the `Insets` it is handed, and all three route through it.

**Print layout is a sixth surface and does need work.** It reaches none of those five sites:
`VsToReportConverter.addTable` (`:1100`) builds a `TableElementDef(report, lens)` (`:1115`), and
`TableElementDef:81` seeds it from `report.padding` — the report's global cell padding, not the
assembly's. Left alone, a marked table would render with density row heights and no cell padding in
print layout, and the rows would be too short for their own text.

The fix is one call in `addTable`: `tableelem.setPadding(...)` (`TableElementDef:504`) with the
effective cell padding, **vertical edges only** — `new Insets(padY, 0, padY, 0)`. Horizontal is
excluded for the same reason as D3 and with the same evidence on this side of the codebase:
`TableElementDef:2038-2040` adds `getPadding().left + getPadding().right` to its computed column
widths, so a horizontal value here would widen print-layout columns while the browser's stayed put.

Accepted cost: cell text in print layout takes the report's default horizontal inset rather than the
density one, so a printed table's text sits a pixel or two differently from the same table on
screen. Named here rather than discovered later.

Excel is out of scope by nature: it writes real spreadsheet cells, where padding has no counterpart,
and there is no `ExcelVSExporter` implementation in this repository. CSV is text.

### 7.2 Card inset

One shared `contentBounds(info)` = assembly bounds − `info.getPadding()`, applied wherever the table
*region* origin and size are computed, with `drawObjectFormat` deliberately left on the full bounds
so border and background stay at the card edge. `VsToReportConverter:1493-1496` already does exactly
this for the chart and is the shape to copy.

Sites:

| Surface | Methods |
|---|---|
| PDF and SVG/PNG (shared base) | `VSTableDataHelper.getObjectPixelBounds` (`:157`), `getTableRectangle` |
| PDF and SVG/PNG | `VSTableHelper.calculateColumnsPosition`, `VSCrosstabHelper.calculateColumnsPosition` |
| HTML | `HTMLTableDataHelper` hierarchy (`HTMLTableHelper`, `HTMLCrosstabHelper`) — a separate hierarchy from `VSTableDataHelper` |
| Print layout | `VsToReportConverter` |

`VsToReportConverter` is a fifth path and easy to miss: `applyShrunkBottomTabsShift`'s comment
(`:968-972`) records that the print-layout cell renderer does not add the padding
`lens.getRowHeightWithPadding` adds, so it needs the inset applied explicitly and must not
double-count the cell padding.

## 8. Verification

**`core` JUnit**, beside `VSDensityDefaultsTest`, `ChromeSeedClassificationTest` and
`ControlHeightFollowDensityTest`:

- the three new matrices at all three tiers plus an unrecognised mode
- `rendered == stored + 2 × paddingY` for data and header rows at each tier — the D3 invariant
- `VSDensityDefaultsTest`'s four existing `rowHeightForMode`/`headerRowHeightForMode` assertions
  (`:63-84`) change to the stored values; the rendered values move into the new invariant test
- `cellHeight` still returns 28/24/20 after the D4 split, and `getEffectiveCellHeight` is unmoved
- seed and Revert round-trip for the chart inset, table inset and cell padding
- `userPadding` surviving the hoist: XML write, parse of a pre-hoist asset, and `copyInfo`
- css-wins: a `CSSTableStyle` padding still beats a seeded one, and still adds to the row
- row growth under mixed coverage: full CSS coverage grows the row by the CSS amount alone even
  when the seeded value is larger; partial coverage grows it enough for the seeded cells too

**Frontend**: `*.spec.ts` for the `base-table` geometry split and the `vs-simple-cell` bindings; one
`*.tl.spec.ts` for the two dialog panes. Scope every TL run with `--include` against the
`portal:test-tl` target; never run the full TL suite.

**Manual**: a marked table at each tier against a marked chart beside it, in the viewer, the
composer, PDF, PNG and HTML export, and print layout — confirming the card insets agree and the
rendered row heights are unchanged from what shipped. Print layout gets its own check per §7.1:
rows tall enough for their text, and column widths unchanged from before the feature.

## 9. Risks and accepted costs

1. **A 14px content box at dense under a 12px font.** Raised twice during design and accepted. If
   descenders clip in practice, the lever is the cell padding y row, not the row-height matrix.
2. **Comfortable and compact store the same data-row height (16).** Correct under D3, but it means
   a stored height no longer identifies a tier. Anything that infers density from a stored row
   height would be wrong — nothing does today.
3. **Column content boxes narrow by `2 × padding-x`.** The price of D3's live/export parity. Most
   visible on narrow columns, where text already ellipsizes.
4. **The `userPadding` hoist touches a shipped serialization path.** Mitigated by keeping the
   attribute name and by an explicit parse test against a pre-hoist asset.
5. **Slice B's five render surfaces.** The reason it is a separate slice.
