# StyleBI Visualization Design Spec

## Purpose

This document defines the broader visualization design layer for StyleBI.

It covers:

- visualization's role relative to shell and Composer
- the surface model for BI output chrome and data surfaces
- the shared visualization-state grammar
- usage boundaries for tables, charts, KPIs, and embedded widget controls
- behavioral rules for when visualization should inherit shell versus introduce analytical meaning
- adoption and compatibility rules for customers who are not ready to move immediately

This spec is meant to work alongside:

- [theme-strategy-overview.md](theme-strategy-overview.md)
- [shell-design-spec.md](shell-design-spec.md)
- [shell-palette-spec.md](shell-palette-spec.md)
- [palette-coordination-recommendations.md](palette-coordination-recommendations.md)
- [visualization-implementation-roadmap.md](visualization-implementation-roadmap.md)

## Scope and Architecture

Visualization is the product's BI output layer.

It sits on top of shell foundations and alongside Composer, but it is not the same as either layer.

It is not:

- a replacement for the shell
- a generic product-wide theme layer
- the shared authoring-state system

Visualization should be understood as the layer that owns dense analytical output, widget-level behavior, and data meaning.

## Relationship To Shell And Composer

Visualization should inherit from shell:

- base font family
- text hierarchy
- neutral surfaces
- default borders
- radius
- focus treatment
- routine primary action emphasis where a true action exists

Visualization should not inherit automatically from shell:

- shell control density
- shell table/list assumptions
- shell-selected state semantics
- shell hover behavior where BI density requires different behavior

Visualization should remain separate from Composer:

- Composer owns authoring-state meaning
- visualization owns analytical meaning
- Composer selected state should not automatically become selected data state
- worksheet editing cues should not become routine chart or table colors

## Rendering And Theming Architecture

This is a foundational constraint for all visualization theming work. It is documented here so it
does not have to be rediscovered in the codebase each time.

### The one test: is it a viewsheet assembly?

Visualization appearance is governed by **two independent systems, split by where the pixels are
rendered**. The reliable way to tell them apart:

> **If it is a viewsheet assembly — anything extending `AbstractVSAssembly` (chart, table, crosstab,
> calc table, selection list, selection tree, range slider, gauge, text, image, input widgets, …) —
> its appearance is server-owned and appears in export.** Its format and size live in
> `VSAssemblyInfo` (`getFormat()` → `VSCompositeFormat`, `getFormatInfo()`, `getPixelSize()`), are
> user-editable, are sent to the live client in the assembly model, and are re-rendered server-side
> by the exporter. Browser `--inet-viz-*` CSS cannot drive them and cannot change the export.

Only surfaces that are **not** viewsheet assemblies — composer/worksheet chrome, EM admin screens,
dialogs, portal lists, and transient interaction overlays drawn over an assembly (hover, resize
handles, context menus, the interactive tooltip) — are browser-DOM and themeable through CSS. None
of those appear in viewsheet export.

The rest of this section applies that test to the two areas where it matters most: chart color and
table/grid density. A compact evidence table is at the end.

Chart color and appearance follow this split — **not** "marks versus chrome." Almost all chart
color, including in-graph chrome, is rendered server-side.

### System B — server-rendered graph (the majority of chart color)

The graph engine (`inetsoft.graph.*`) paints the chart **server-side** and bakes color into the
chart image/SVG — including inline SVG, where fill/stroke are set as element attributes on the
server. This covers:

- data marks (bars, lines, points) and their series/ramp colors
- **in-graph chrome**: axis lines, tick labels, gridlines, legend swatches/border/background, axis
  and legend titles, and plot labels

Every server-rendered color resolves through this chain:

1. **per-object user settings** — descriptor color pickers (`AxisDescriptor.lineColor`,
   `PlotDescriptor.x/yGridColor`, `LegendsDescriptor.borderColor`, series
   `CategoricalColorFrame.setUserColor`), serialized with the chart
2. **server-side CSS dictionary** — `inetsoft.util.css.CSSDictionary` parsing `format.css` /
   `defaults.css` (managed by `PortalThemesManager` and `SreeEnv("css.location")`), keyed by
   `CSSConstants` chart classes such as `ChartAxisLine`, `ChartPlotLine`, `ChartLegend`,
   `ChartPalette`
3. **hardcoded defaults** — `GDefaults` and `CategoricalColorFrame.COLOR_PALETTE`

### System A — browser DOM (a small surround)

Only chrome rendered as Angular DOM *around* the chart image is themeable through browser
`--inet-*` / `--inet-viz-*` CSS: the viewsheet object title bar, the chart toolbar/menu, and the
interactive hover tooltip. None of this is part of the exported chart.

### Export-consistency rule (why this matters)

The live browser chart and every export format (PDF, PNG, SVG, Excel, PowerPoint, HTML) share the
**one** server-side render path. Therefore:

- A browser CSS/token change **cannot** drive the export look, and for in-graph color cannot even
  change the live chart.
- Any color or appearance that must appear in export **must** be driven server-side — through
  descriptors and/or the `format.css` `CSSConstants` classes, with `GDefaults` as fallback.
- Routing in-graph chrome through browser tokens would produce a **live/export mismatch**.

Practical rule: to decide which system owns a chart color, ask *where it renders*. If it is inside
the chart image (marks or in-graph chrome), it is server-side and must be themed through System B.
If it is DOM around the chart and never exported, it may use browser tokens.

### Density and layout follow the same boundary

The render-location split is not limited to color. Table/grid **density** — row height, font size,
cell padding — obeys the same rule, with its own server-side mechanism (distinct from the graph
engine).

- **Viewsheet data-surface assemblies** — table assemblies (VSTable, VSCrosstab, VSCalcTable) **and
  selection lists / selection trees** — are the primary BI surfaces, and all pass the assembly test
  above, so their density is server-owned. Table row height
  (`TableDataVSAssemblyInfo.getRowHeight/setRowHeight`, default `AssetUtil.defh`), selection-list
  cell height (`SelectionBaseVSAssemblyInfo.getCellHeight/setCellHeight`, serialized `cellHeight=`,
  default `AssetUtil.defh`), cell font (`VSFormat`/`VSFormatModel`, default `Util.DEFAULT_FONT`), and
  padding are **server-side and user-settable**. These reach the live client in the assembly model
  (`BaseTableModel.dataRowHeight` / `headerRowHeights` / `rowHeights`) and the exporter renders from
  the same values (`VsToReportConverter.calculateRowHeights`, `lens.getRowHeight`, `fmt.getFont`).
  Browser CSS density tokens do not drive them and do not affect export.
- **Browser DOM tables/lists** — surfaces that are *not* viewsheet assemblies: worksheet detail
  tables, EM admin tables, dialog lists, portal repository/schedule lists, and generic
  `selectable-list` widgets in dialogs. These are Angular/CSS-rendered, genuinely density-themeable
  through browser `--inet-viz-*` tokens, and not part of viewsheet export. (Note: a viewsheet
  SelectionList assembly is **not** in this group — it is server-owned, above.)

Consequences for density work:

- CSS `--inet-viz-*` density tokens own **browser DOM** data surfaces only.
- Modern density for **viewsheet data tables** must be applied **server-side** — by resolving the
  density *defaults* (`AssetUtil.defh`, default font, padding) from a backend property so both the
  live model and every export format pick it up. The browser gate cannot switch it.
- Modern density changes **defaults only**; explicit user row heights/fonts are preserved. Because
  changing a default reflows saved viewsheets that rely on it, the modern default must be gated
  server-side (per-org/theme), not applied globally.

### Evidence

Code references behind the claims above (paths under `community/`). Verify against current source
before implementation.

| Claim | Code evidence |
|---|---|
| All viewsheet assemblies carry server-side format + size | `core/.../uql/viewsheet/AbstractVSAssembly` → `getVSAssemblyInfo()`/`getFormatInfo()`; `VSAssemblyInfo.getFormat()` (`VSCompositeFormat`), `getPixelSize()` |
| Chart mark colors are server-side | `core/.../graph/aesthetic/CategoricalColorFrame` (`COLOR_PALETTE`, `updateCSSColors`) |
| Chart chrome colors are server-side (user pickers) | `AxisDescriptor.lineColor`, `PlotDescriptor.x/yGridColor`, `LegendsDescriptor.borderColor` (serialized to XML) |
| Chart chrome defaults come from server CSS, not browser | `ChartLineColor.getAxisLineColor()` → `CSSDictionary` / `CSSConstants.CHART_AXIS_LINE` (`format.css`) |
| SVG fills/strokes baked server-side (incl. inline SVG) | `core/.../graph/mxgraph/canvas/mxSvgCanvas` `setAttribute("stroke", …)` |
| Table row height = server property, user-settable | `TableDataVSAssemblyInfo.getRowHeight/setRowHeight`, default `AssetUtil.defh` |
| Table cell font/size = server format | `BaseTableCellModel` / `VSFormatModel` ← `VSFormat.getFont()`, default `Util.DEFAULT_FONT` |
| Selection list cell height = server property, user-settable | `SelectionBaseVSAssemblyInfo.getCellHeight/setCellHeight`, serialized `cellHeight=`, default `AssetUtil.defh` |
| Server heights reach the live client via model | `core/.../web/viewsheet/model/table/BaseTableModel` (`dataRowHeight`/`rowHeights`); `web/.../vsobjects/model/base-table-model.ts` |
| Export renders from the same server model | `core/.../report/io/viewsheet/AbstractVSExporter`; `VsToReportConverter.calculateRowHeights` / `lens.getRowHeight` / `fmt.getFont` |
| No chart-series/density CSS tokens exist in the web layer | no matches for `--inet-*series*` / `--inet-viz-*` in `web/projects/portal/src/scss` |

### Verified implementation anchors (density path, as of `epic-74519`)

Precise file:line anchors for the server-side density mechanism, verified so Phase 3+ need not
re-derive them. Re-confirm against current source before editing — line numbers drift.

| Anchor | Location | Note |
|---|---|---|
| Default row/cell height constant | `core/.../uql/asset/internal/AssetUtil.java:3492-3493` | `public static final int defh = 20;` (also `defw = 100`). Package is `uql/asset/internal`, **not** `util/asset`. The **only** default — there is no configurable density property today. |
| Table row-height API + "user set it" flags | `core/.../uql/viewsheet/internal/TableDataVSAssemblyInfo.java` | `dataRowHeight = AssetUtil.defh` (L1651); `getDataRowHeight(int)` L816; `getHeaderRowHeights(boolean)` L731; `userDataRowHeight`/`userHeaderRowHeight` L1653-1654 — the signal for "default still in force" vs user-set. |
| Selection-list cell height | `core/.../uql/viewsheet/internal/SelectionBaseVSAssemblyInfo.java` | `getCellHeight/setCellHeight` L121/L129; `cellHeight = AssetUtil.defh` L1004; serialized `cellHeight=` L530. |
| Default cell font (fallback only) | `core/.../report/internal/Util.java:68` | `DEFAULT_FONT = new StyleFont(DEFAULT, 0, 10)` (size 10). Not stored on `VSFormat`; substituted at render when `VSFormat.getFont()` is null (e.g. `HTMLCoordinateHelper.java:1245`). Font-size density is higher-risk — touches text measuring/wrapping/export layout. |
| **Live choke point** (builds client model) | `core/.../web/viewsheet/controller/table/BaseTableService.java:449-495` | Reads `getDataRowHeight`/`getHeaderRowHeights`, feeds `BaseTableModel.dataRowHeight`/`headerRowHeights`. |
| **Export choke point** (same values) | `core/.../uql/viewsheet/internal/VsToReportConverter.java:1193` | `calculateRowHeights(...)`; rows at `AssetUtil.defh` treated as "default" (L1200, L1209-1211). Sharing these two choke points is why live and export stay in sync. |
| Existing runtime override hook | `VSTableLens.getCSSDataRowHeight/getCSSHeaderRowHeight/getCSSRowPadding`, consumed at `BaseTableService.java:460-478` | Server-side CSS dictionary (`format.css`) can already override row height/padding — the natural convergence point for the Phase 6/8 `format.css` bridge. |
| Gate is read on server render paths (as of Phase 3/6) | `getBooleanProperty("viewsheet.modernVisualization")` forwarded to the client at `CoreLifecycleService:306`, `PortalController:116`, `LookAndFeelService:59`, **and consumed server-side** by `VSDensityDefaults.isModern()` (org-scoped) | Superseded: `VSDensityDefaults`/`VSTableStructureDefaults`/`VSChartChromeDefaults` (via `VSTableLens`, `SelectionBaseVSAssemblyInfo`, `BaseTableService`, `DataVSAQuery`, `CSSChartStyles`) **do** consume the gate on the render path. "Client-forward only" was true at Phase 2 baseline but stale as of Phase 3 (density) and Phase 6 (chart chrome). |

### Browser-DOM density caveats (verified)

Two surfaces the density render-location model would otherwise be assumed CSS-themeable but are not
a simple token swap:

- **Worksheet detail table row height is a JS constant, not CSS.**
  `web/.../composer/gui/ws/editor/ws-details-table-data.component.ts:145-146`
  (`HEADER_CELL_HEIGHT=30`, `TABLE_CELL_HEIGHT=21`) drives virtual-scroll math via inline styles.
  Adopting `--inet-viz-*` here is a TS→CSS refactor, not a repoint.
- **EM admin tables are Angular Material in a separate build — and are admin chrome, not BI
  output.** "EM" = Enterprise Manager, the admin UI (`projects/em`), a separate Angular app from the
  end-user portal. Its tables use `mat-mdc-table` (shared wrapper
  `projects/em/.../common/util/table/regular-table/`; e.g. the Schedule task list and Auditing
  grid). They are **never viewsheet assemblies and never exported**, so they sit outside the
  visualization layer proper — only a *secondary* browser-DOM density candidate. `projects/em` does
  **not** import `projects/portal/.../_viz-tokens.scss`, so adopting viz tokens there needs
  cross-project token plumbing.
- **Clean first CSS targets** (already partly token-driven): `generic-selectable-list.component.scss`
  (L30/33) and `schedule-task-list.component.scss` (L42/97/112).
- Shell density tokens available: `--inet-row-height-md` (34px) is the **only** row-height token;
  control heights `-sm/-md/-lg` = 24/30/36px; `--inet-space-1..8` = 2/4/6/8/12/16/·/24px
  (`_variables.scss:473-498`). Shell `--inet-table-*` tokens (L373-378) are **color-only** — no
  height/padding.

## Visualization Design Principles

- Visualization should feel analytically dense without becoming visually noisy.
- Widget chrome should stay quieter than the data itself.
- Analytical meaning should come from explicit visualization-owned tokens rather than borrowed shell accents.
- Dense output should be achieved through spacing, alignment, hierarchy, and interaction design rather than only smaller text.
- Tables, charts, KPIs, and embedded controls should read as parts of one visualization system rather than unrelated widget families.
- Visualization modernization should remain opt-in until customers are ready.

## How To Use This Spec

Use this document to understand:

- which surfaces stay shell-driven
- which surfaces are visualization-owned
- how visualization states should behave
- how dense BI output should feel
- how tables, charts, KPIs, and embedded controls should differ from ordinary shell UI
- how the compatibility gate should work during migration

Use [visualization-implementation-roadmap.md](visualization-implementation-roadmap.md) for file targets, token rollout, compatibility gating, validation, and implementation sequencing.

## Adoption And Compatibility Strategy

Existing customers may not be ready to adopt the new visualization system immediately.

The visualization update should therefore be gated as an explicit opt-in capability rather than as an automatic global replacement.

### Default Adoption Rule

- legacy visualization remains the default behavior
- the modern visualization layer should ship as opt-in
- customer adoption timing should be controlled intentionally rather than inferred from shell or Composer changes

### Recommended Gate Model

The preferred gating model is:

1. preserve current visualization behavior as the compatibility baseline
2. introduce a new opt-in visualization mode such as `modern` or `v2`
3. activate new visualization tokens and selectors only when that mode is enabled
4. allow customer themes or runtime configuration to opt into the new mode when ready

### Preferred Implementation Shape

Visualization should be gated through a small number of clear switches rather than many local booleans.

Recommended mechanisms:

- a theme-level visualization mode such as `legacy` versus `modern`
- a root class or attribute such as `.viz-modern` or `[data-viz-theme="v2"]`
- new visualization-owned `--inet-viz-*` tokens that map to modern behavior only inside the gated mode

### Compatibility Rules

- do not globally repoint legacy visualization selectors to new token meanings in the first pass
- do not require existing customers to adopt the new visualization layer just because shell or Composer tokens evolve
- do not fragment the rollout through many per-widget feature flags unless there is no safer alternative
- preserve a coherent legacy path and a coherent modern path rather than mixing the two unpredictably

### Migration Intent

During migration:

- legacy widget hooks should continue to resolve to legacy behavior by default
- modern visualization should be activated through explicit scoping
- new visualization tokens should be introduced alongside legacy hooks rather than by destructive rename
- the product should support side-by-side validation of legacy and modern visualization behavior during rollout

## Visualization Surface Model

Visualization should use two visual layers:

1. Widget chrome
2. Data surfaces

### Widget Chrome

Widget chrome is the non-data structure around BI output.

Note the render-location split from [Rendering And Theming Architecture](#rendering-and-theming-architecture):
some items below are **in-graph chrome** painted server-side (legends, axes, gridlines inside a
chart) and are themed through System B, while others are **surrounding DOM chrome** themable through
browser tokens. Only the DOM items accept `--inet-viz-*` chrome tokens.

It includes:

- widget headers
- legends *(in-graph: server-rendered)*
- axes and gridline scaffolding *(in-graph: server-rendered)*
- toolbar strips inside widgets *(DOM: browser-themeable)*
- filter chips and compact controls attached to widgets *(DOM: browser-themeable)*
- KPI framing and comparison scaffolding
- tooltip containers and annotation chrome *(interactive tooltip is DOM; in-graph annotations are server-rendered)*

Widget chrome should:

- inherit shell neutrals first
- stay compact and quiet
- use visualization state only when the surface is communicating BI-specific state
- avoid borrowing chart categorical colors for routine structure

Widget chrome should not:

- use authoring-state colors from Composer
- use vivid chart hues as passive background treatments
- feel like standard shell card chrome when the surface is primarily analytical

### Data Surfaces

Data surfaces are the analytical surfaces where data is read, compared, filtered, or manipulated.

They include:

- tables and dense grids
- chart marks
- chart plot areas
- KPI values and trends
- embedded widget-level filters and segmented controls
- conditional-formatting surfaces

These surfaces may use visualization-specific density, state, and analytical color tokens where shell alone is not expressive enough.

## Inherited Foundations

Visualization should inherit the following from shell unless there is a strong reason to diverge:

- base font family
- strong/default/muted text hierarchy
- neutral background and border roles
- default radius scale
- focus ring treatment
- primary action color for real widget actions

These inherited foundations keep the product cohesive without forcing BI output to look like ordinary shell chrome.

## Visualization-Owned Foundations

Visualization should define its own:

- density tokens
- widget-state tokens
- chart palettes
- analytical ramps
- conditional formatting colors
- compact widget chrome spacing
- table/grid interaction patterns
- dense widget-level control sizing where needed

## Visualization State Grammar

Visualization should use a small but explicit state vocabulary:

- `default`
  - neutral analytical surface
- `hover`
  - temporary inspection or interaction proximity
- `selected`
  - explicit selected data item, row, mark, or legend item
- `active`
  - current edit focus, popover anchor, active cell, or open state
- `contextual`
  - related, grouped, or scoped analytical context
- `inline-edit`
  - editable cell or embedded edit region
- `sorted`
  - active sort meaning
- `filtered`
  - active filter-on meaning
- `pinned`
  - frozen column or anchored region meaning
- `warning`
  - threshold or caution meaning
- `anomaly`
  - outlier, exception, or error-adjacent data meaning
- `dimmed`
  - de-emphasized but still readable analytical state

### Default

Default visualization surfaces should begin from shell neutrals and visualization density tokens.

Use default when the UI is analytical but not expressing special state.

### Hover

Hover is for temporary inspection and interaction proximity.

Use it for:

- row hover
- mark hover
- header hover
- reveal-on-hover widget actions

Hover should remain lighter and lower-priority than explicit selection.

### Selected

Selected is for explicit data selection.

Use it for:

- selected rows
- selected data points
- selected legend items
- selected widget regions

Selected data state should be visually clearer than hover and should not be confused with Composer selection meaning.

### Active

Active is for the current interaction focus inside a widget.

Use it for:

- active cell
- active filter control
- open popover anchor
- focused editable value

Active should read as immediate interaction focus rather than long-lived selection.

### Contextual

Contextual is for grouped, related, or scoped analytical meaning.

Use it for:

- grouped headers
- related rows
- focus regions
- scoped filter areas

Do not use contextual as a generic replacement for hover or selection.

### Inline-Edit

Inline-edit is for editable surfaces inside dense widgets.

Use it for:

- editable cells
- embedded value editors
- compact in-row inputs

Inline-edit must remain readable and stable at dense BI sizes.

### Sorted And Filtered

Sorted and filtered should communicate active analytical state without becoming noisy.

Use them for:

- sort headers
- sort glyphs
- filter-on chips
- active filter indicators in widget chrome

### Pinned

Pinned is for frozen or anchored analytical regions.

Use it for:

- frozen columns
- pinned headers
- anchored region dividers

Pinned state should support structure and scanability rather than call attention to itself.

### Warning And Anomaly

Warning and anomaly are meaning-bearing analytical states.

Use them for:

- thresholds
- exception states
- outliers
- cautionary data conditions

These states may align with shell semantic hue families, but visualization may require stronger contrast than shell.

### Dimmed

Dimmed is for de-emphasized but still readable analytical content.

Use it for:

- muted series
- de-emphasized rows
- background comparison data
- inactive but visible controls

Dimmed should lower emphasis without making data unreadable.

## Density System

Visualization density should be handled as a first-class concern in this layer.

### Density Modes

- `comfortable`
- `compact`
- `dense`

Dense is the primary BI target for data-heavy views.

### Density Control Model

Density should be user-controllable at the product configuration level, but it should not require a new EM UI surface in the first pass.

Use this model:

- visualization defines the density modes and the tokens behind them
- EM stores the default visualization density as a property setting
- runtime applies the configured density mode through the visualization token layer
- selected visualization surfaces may allow narrower local density overrides where scan-heavy list or table behavior justifies it

Theme work should define how each density mode looks.

The default density choice itself should be treated as a configuration property rather than as a theme-only concern.

### Density Override Boundary

Per-component density override should remain limited.

It makes sense for:

- tables and grids
- crosstab and pivot-style data views
- selection lists
- long filter member lists
- multi-select pick lists
- long dropdown result panels when they behave like dense scrollable lists

It should usually not be added for:

- charts
- KPI widgets
- short ordinary dropdowns
- standard shell form controls
- isolated legends, tooltips, or small control fragments

Practical rule:

- allow local density override where the surface behaves like a scan-heavy analytical list or table
- otherwise inherit the surrounding visualization density

### Target Specs

Density modes should map to explicit values rather than to a single shared target.

| Token role | Comfortable | Compact | Dense | Notes |
|---|---|---|---|---|
| table row height | `28px` | `24px` | `20px` | primary scan-density control; dense = today's `AssetUtil.defh` |
| header row height | `30px` | `26px` | `22px` | headers slightly taller than data rows |
| font size | `13px` | `13px` | `12px` | keep type mostly stable across modes |
| cell padding x | `8px` | `6px` | `4px` | horizontal scan rhythm |
| cell padding y | `6px` | `4px` | `3px` | vertical compaction |
| toolbar control height | `30px` | `28px` | `24px` | only for visualization-owned compact chrome (DOM only) |

> **Calibration (resolved 2026-07-07 — supersedes the earlier 26–30 draft).** An earlier draft of
> this matrix set `dense = 26px`, but the current **viewsheet-assembly** default is
> `AssetUtil.defh = 20px` and the DOM worksheet table is `21px` — both *denser* than 26px — so that
> draft would have made modern rows *taller* than today, contradicting "denser than shell." The
> matrix above is the recalibration: **one unified matrix** used by both halves (browser-DOM CSS
> tokens and server-side viewsheet-assembly defaults), with **`dense` = 20px = today's default**, so
> the default mode reflows nothing when modern is enabled. The default density mode is **`dense`**,
> set org-wide via the `viewsheet.density` property; per-component user-set row heights still win
> over it. See `visualization-phase3-implementation-plan.md` (Decisions D1/D2) for the full rationale
> and precedence model.

### Density Guidance

- density should increase data visibility without making interaction fragile
- dense mode should prioritize scanability, alignment, and structure
- density should come from spacing, alignment, and interaction design rather than just shrinking text
- embedded controls must remain hittable and legible at dense sizes
- the first implementation pass should source the default density from an EM property setting rather than a new EM UI control
- most density change should come from row height and padding rather than aggressive type reduction

## Surface Guidance By Area

### Tables And Grids

Tables and grids are the primary BI surfaces.

They should:

- use subtle gridlines
- prefer alignment and rhythm over heavy borders
- keep rows single-line with ellipsis by default
- right-align numeric columns
- use tabular numerals
- support hover, selected, active, sorted, filtered, pinned, and inline-edit states clearly

They should not:

- inherit shell list styling as a substitute for dense analytical tables
- use heavy shell card framing around every grid
- rely on color alone to communicate sort or filter meaning

### Charts

Chart surfaces should distinguish clearly between chart chrome and data marks — but note that most
chart chrome (axes, gridlines, legends, in-plot labels) is **server-rendered**, not browser CSS.
See [Rendering And Theming Architecture](#rendering-and-theming-architecture). Chrome color must be
driven through System B (descriptors + `format.css` + `GDefaults`) so it stays consistent in export;
only the surrounding DOM (toolbar, interactive tooltip) uses browser tokens.

Chart chrome should:

- inherit shell neutrals first (as server-side defaults, e.g. the `ChartAxisLine` / `ChartLegend`
  `format.css` classes — not browser tokens)
- stay visually lighter than the data
- use neutral legends, axes, gridlines, and label structure
- keep embedded actions compact and quiet

Charts should continue to own:

- categorical palettes
- sequential ramps
- diverging ramps
- highlight treatment
- threshold and analytical emphasis

Chart palettes should not bleed into surrounding widget chrome.

### KPI And Summary Widgets

KPI widgets are visualization outputs, not generic shell cards.

They should:

- emphasize data hierarchy first
- use semantic color only when meaning justifies it
- keep comparison and sparkline elements subordinate to the primary value
- stay visually quieter than dashboards full of data marks

KPI widgets should not become heavily framed shell cards with decorative chrome.

### Embedded Filters And Controls

Controls inside BI widgets should follow visualization density while still inheriting shell control language.

They should:

- stay compact
- use neutral ghost or outline patterns for passive controls
- use primary only for real actions or commits
- use explicit filtered/on states through widget-state tokens

They should not:

- use chart categorical hues for routine control chrome
- copy full shell toolbar density when the widget requires a denser layout

## Shared Chrome Rules

The following should stay shell-driven unless they are directly carrying BI-specific meaning:

- standard buttons inside widgets
- generic menu and popover shells
- routine dialog and form controls outside dense data surfaces
- passive container framing
- default tooltip structure before analytical emphasis is added

Visualization tokens should not be used just because a surface appears near data.

## Analytical Color Rules

### Allowed Shared Use With Shell

- neutrals
- primary for deliberate emphasis
- semantic families when the meaning aligns

### Visualization-Owned Color

- categorical palettes
- sequential ramps
- diverging ramps
- heatmap colors
- conditional formatting colors
- analytical highlight colors

### Prohibitions

- do not use chart categorical hues for routine widget chrome
- do not let vivid chart colors replace shell control states
- do not use multiple accent hues in surrounding widget UI without meaning

## Token Groups To Define

Visualization should eventually define explicit token groups such as:

### Density

- `--inet-viz-row-height`
- `--inet-viz-cell-padding-x`
- `--inet-viz-cell-padding-y`
- `--inet-viz-toolbar-height`
- `--inet-viz-control-height`
- `--inet-viz-chrome-row-height`
- `--inet-viz-font-size`

### State

- `--inet-viz-hover-bg`
- `--inet-viz-selected-bg`
- `--inet-viz-selected-text`
- `--inet-viz-selected-border`
- `--inet-viz-active-border`
- `--inet-viz-context-bg`
- `--inet-viz-inline-edit-bg`
- `--inet-viz-filtered-bg`
- `--inet-viz-sorted-color`
- `--inet-viz-pinned-divider`
- `--inet-viz-warning-bg`
- `--inet-viz-anomaly-bg`
- `--inet-viz-dimmed-opacity`

### Chart

These names define the **conceptual** chart color source of truth. Because chart marks and in-graph
chrome are server-rendered (see [Rendering And Theming Architecture](#rendering-and-theming-architecture)),
they are **not** literal browser CSS variables that color the graph — they must be bridged into
System B (the `format.css` `CSSConstants` classes and descriptor defaults) so live view and export
match. Only chart color applied to surrounding DOM chrome resolves as an actual browser token.

- `--inet-viz-chart-series-*`
- `--inet-viz-ramp-sequential-*`
- `--inet-viz-ramp-diverging-*`
- `--inet-viz-threshold-*`
- `--inet-viz-conditional-*`

### Compatibility

- `--inet-viz-mode`
- `--inet-viz-legacy-*`
- `--inet-viz-modern-*`

## Implementation Note

The first implementation pass should focus on:

- token ownership clarity
- compatibility gating
- density foundations
- shared table/grid state adoption
- chart chrome standardization

It should not start by redesigning every chart type independently.

## Design Intent Summary

Visualization should read as a shell-cohesive but analytically distinct layer.

The experience should feel:

- denser than shell
- quieter in widget chrome than in data marks
- explicit about analytical state
- clearly separate from Composer authoring-state meaning
- safe to adopt gradually through opt-in gating

## Related Specs

- [theme-strategy-overview.md](theme-strategy-overview.md)
- [shell-design-spec.md](shell-design-spec.md)
- [shell-palette-spec.md](shell-palette-spec.md)
- [palette-coordination-recommendations.md](palette-coordination-recommendations.md)
- [visualization-implementation-roadmap.md](visualization-implementation-roadmap.md)
