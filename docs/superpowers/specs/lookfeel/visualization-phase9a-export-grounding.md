# Phase 9A — Task 0/1 Export Grounding

Confirms that the four Phase 9A affordance icons (sort, expand/collapse, drill,
more/overflow) are rendered exclusively as browser DOM (Angular template `<i>`
elements) and are not drawn as glyphs into viewsheet export output. This
determines that all four are governed by the browser `.viz-modern` CSS gate
(Track A) rather than requiring a server-side exporter change.

Exporter code searched: `core/src/main/java/inetsoft/report/io/viewsheet/**`
(covers `AbstractVSExporter`, the `html/`, `excel/`, `pdf/`, `powerpoint/`, and
`snapshot/` exporters — the full server-side rendering path that produces
exported files).

## Sort

- **Rendered as browser DOM**: `<i sortButton class="sort-button
  vs-header-cell-button-sort ...">` in
  `web/projects/portal/src/app/vsobjects/objects/table/vs-table.component.html:191-196`
  and four equivalent instances in
  `web/projects/portal/src/app/vsobjects/objects/table/vs-crosstab.component.html:187-189,284-286,392-394,492-494`.
  These are interactive header buttons wired to click handlers, not bound to
  any server-rendered cell content.
- **Exporter finding**: the only exporter hits for sort-related terms are
  `SnapshotVSExporter.java:615-616`, which read/copy `SortInfo` (the sort
  *state/ordering* used to determine row/column order in the exported data),
  not a glyph. No exporter file references `sort-ascending-icon`,
  `sort-descending-icon`, `sort-value-*-icon`, `sort-icon`, or any codepoint
  from the sort glyph family (`\ebf0`-`\ebf4`). No exporter draws a sort arrow
  into the output image/HTML/PDF/Excel.
- **Conclusion**: in scope for the `.viz-modern` browser gate (Track A).

## Expand/collapse

- **Rendered as browser DOM**: the crosstab expand/collapse toggle is the
  `<span #drillIcon class="table-cell__drill-icon ...">` element in
  `web/projects/portal/src/app/vsobjects/objects/table/vs-table-cell.component.html:153-159`,
  swapping `plus-box-outline-icon` / `minus-box-outline-icon` via `ngClass`
  based on `cell.drillOp`. The selection-tree folder toggle is
  `getTreeIconClass()` in
  `web/projects/portal/src/app/vsobjects/objects/selection/selection-list-cell.component.ts:462-469`,
  which likewise returns `minus-box-outline-icon` / `plus-box-outline-icon`
  strings consumed by the template. Both are client-side click affordances.
- **Exporter finding**: exporter hits for "expand"/"collapse" are all in
  `AbstractVSExporter.java` (`expandable`, `expandAll`, `expandChart`,
  `expandTable`, `expandSelectionAssembly`, `expandCurrentSelection`,
  `isExpandSelections`) — these describe server-side *layout expansion*
  (computing how many rows/cells to lay out so all data fits on the exported
  page), not drawing a plus/minus toggle glyph. No exporter file references
  `plus-box-outline-icon`, `minus-box-outline-icon`, or `table-cell__drill-icon`.
- **Conclusion**: in scope for the `.viz-modern` browser gate (Track A).

## Drill

- **Rendered as browser DOM**: three families, all Angular template
  elements/action definitions:
  - Crosstab/chart drill context-menu actions —
    `icon: () => "drill-down-filter-icon"` /
    `"drill-up-filter-icon"` in
    `web/projects/portal/src/app/vsobjects/action/crosstab-actions.ts:282,290`
    and `web/projects/portal/src/app/vsobjects/action/chart-actions.ts:380,388`.
  - Chart axis inline drill toggle — `<i class="plus-box-outline-icon
    floating-icon">` / `<i class="minus-box-outline-icon floating-icon">` in
    `web/projects/portal/src/app/graph/objects/chart-axis-area.component.html:97-126`.
  - Chart nav-bar zoom — `shape-plus-icon` / `shape-minus-icon` in
    `web/projects/portal/src/app/graph/objects/chart-nav-bar.component.html:23,26`.
  - Also the table/crosstab header drill tooltip trigger — `<i
    [wTooltip]="model.drillTip" class="drill-filter-icon drill-tip">` in
    `web/projects/portal/src/app/vsobjects/objects/chart/vs-chart.component.html:41`
    and `vs-crosstab.component.html:44`.
- **Exporter finding**: exporter hits for "drill" are `ExportUtil.java:834-844`
  (`XDrillInfo`, `DrillPath`, `DrillSubQuery`) — these carry drill-path
  *metadata* used when the export needs to resolve a drill's underlying query
  (e.g. embedding drill-down data), and `WorksheetAsset2.java:81`
  (`getAutoDrillDependency`, an asset-dependency tracker unrelated to
  rendering). No exporter file references `drill-down-filter-icon`,
  `drill-up-filter-icon`, `plus-box-outline-icon`/`minus-box-outline-icon` (in
  the drill context), or `shape-plus-icon`/`shape-minus-icon`. No exporter
  draws a drill glyph into the output.
- **Conclusion**: in scope for the `.viz-modern` browser gate (Track A).

## More/overflow

- **Rendered as browser DOM**: the viz mini-toolbar kebab —
  `icon: () => "menu-vertical-icon"` in
  `web/projects/portal/src/app/vsobjects/action/abstract-vs-actions.ts:325`,
  consumed by `<i [class]="icon + ' icon-size-small'">` in
  `web/projects/portal/src/app/vsobjects/objects/mini-toolbar/mini-toolbar.component.html:44`.
  This is the assembly-hover mini-toolbar, a viewer/composer-only interaction
  affordance that never appears in the underlying assembly's canvas bounds.
- **Exporter finding**: no exporter file references `menu-vertical-icon`,
  `menu-horizontal-icon`, or any mini-toolbar/kebab class. The only
  "overflow" hits in the exporter package (`HTMLCoordinateHelper.java`,
  `HTMLCrosstabHelper.java`, `HTMLSelectionListHelper.java`,
  `HTMLSelectionTreeHelper.java`, `HTMLTableHelper.java`,
  `HTMLVSExporter.java`) are plain CSS `overflow:hidden` / `overflow:auto`
  used for text/content clipping inside exported HTML cells — unrelated to a
  more/overflow menu glyph.
- **Conclusion**: in scope for the `.viz-modern` browser gate (Track A).

## Summary

All four affordances (sort, expand/collapse, drill, more/overflow) are
rendered purely as browser DOM (Angular template `<i>`/`<span>` elements
driven by click handlers and action definitions) and were not found rendering
a glyph anywhere in the server-side viewsheet exporter
(`core/src/main/java/inetsoft/report/io/viewsheet/`). The exporter only
touches same-named concepts as *data/state* (sort order, drill-path metadata,
layout expansion, CSS overflow clipping) — never as a drawn icon. No
affordance needs to be pulled out of Track A; none require a server-side
resolver pass.
