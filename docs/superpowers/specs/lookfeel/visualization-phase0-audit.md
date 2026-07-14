# StyleBI Visualization — Phase 0 Guardrails & Baseline Audit

## Purpose

This document executes **Phase 0** of
[visualization-implementation-roadmap.md](./visualization-implementation-roadmap.md).

Phase 0 is not adoption work. It produces the decisions and the baseline audit that later
phases depend on:

- confirm which surfaces are visualization-owned versus shell-owned
- establish compatibility gating as a first-pass architectural requirement
- catalogue the legacy selectors and token hooks existing customers may already rely on
- set the guardrails that keep chart color and shell chrome from bleeding into each other
- record the codebase reality so Phase 1 (contract) and Phase 2 (gate) start from fact, not assumption

Each roadmap task below is paired with a **task outcome** grounded in the current StyleBI code
(`community/web/projects/…`, `community/core/…`).

## Audit Scope And Confidence

- **Reviewed:** the shell token layer (`portal/src/scss/_variables.scss`, `_themeable.scss`,
  `_bootstrap-override.scss`), chart/table component SCSS inventories, the graph aesthetic
  engine (`core/.../graph/aesthetic`), and the theme persistence model
  (`core/.../sree/portal/CustomTheme*.java`).
- **Confidence:** high on the presence/absence of the token system and the gate; medium on the
  exhaustiveness of legacy-hook enumeration. A full selector-by-selector sweep is deferred to the
  screen-by-screen audit passes the roadmap schedules after Phases 3–5 and Phase 6.
- **Method:** targeted search, not exhaustive file-by-file review. Counts are indicative.

## Task-By-Task Outcomes

### Task 1 — Confirm which surfaces are visualization-owned versus shell-owned

**Outcome: boundary is confirmable, and the token system to express it already exists.**

There is a mature runtime token layer built on CSS custom properties (`--inet-*`), used in 1200+
places across 40+ SCSS files. It is defined centrally in:

- `portal/src/scss/_variables.scss` — token definitions
- `portal/src/scss/_themeable.scss` — themeable utility/selector layer
- `portal/src/scss/_bootstrap-override.scss` — Bootstrap-shaped shell chrome

This is the shell foundation the visualization layer should inherit from. The boundary decisions
below are therefore **implementable as-is** — they do not require new infrastructure, only new
namespacing.

| Surface | Owner | Evidence / note |
|---|---|---|
| typography, text roles, neutral surfaces, borders, radius, focus ring, primary action | **Shell** | already tokenized (`--inet-text-color`, `--inet-default-border-color`, `--inet-radius-*`, `--inet-focus-ring`, `--inet-primary-color`) |
| generic dialogs, tabs, forms, nav, toolbars | **Shell** | `_bootstrap-override.scss` + shell chrome section of `_themeable.scss` |
| worksheet/schema authoring cues, selected/adjacent editing state | **Composer** | `.ws-assembly-*`, `.schema-*`, `--inet-graph-assembly-*` in `_themeable.scss` |
| dense tables/grids, crosstab, selection lists | **Visualization** | today served by shell tokens — see Task 3 |
| chart marks (series color, ramps) | **Visualization**, **server-rendered** | `inetsoft.graph.aesthetic.CategoricalColorFrame` etc. — not browser CSS. See Task 4 |
| in-graph chrome (axis lines, gridlines, legend swatch/border/bg, in-plot labels) | **Visualization**, **server-rendered** | descriptors + `format.css` (`CSSConstants` chart classes) + `GDefaults`. Appears in export. See Task 4 |
| surrounding DOM chrome (object title bar, chart toolbar, interactive tooltip) | **Visualization**, browser-CSS | Angular DOM; **not** in export. See Task 4 |

**Decision:** ownership model from the roadmap Phase 0 Outcome is adopted unchanged. The only
adjustment is the split within "charts" (Task 4).

### Task 2 — Treat compatibility gating as the first architectural requirement

**Outcome: greenfield. No gate exists yet, so there is nothing to unwind — but also no safety net today.** *(Superseded by Phases 1–3: the `--inet-viz-*` namespace, the `.viz-modern` gate, and server-side `VSDensityDefaults.isModern()` now exist. The baseline reasoning stands; the tree state does not.)*

Searches for `inet-viz`, `viz-modern`, `data-viz-theme`, and `viz-legacy` return **no matches**.
There is:

- no `--inet-viz-*` token namespace
- no theme-mode switch (`legacy` vs `modern`)
- no root class/attribute gate

**Implication:** Phase 2 can introduce the gate cleanly (theme mode + `.viz-modern` /
`[data-viz-theme="v2"]`) without colliding with an existing mechanism. Because there is no gate
today, any premature restyle of a shared token would immediately hit every customer — so the gate
must land **before** any selector reinterpretation, exactly as the roadmap sequences it.

**Decision:** compatibility gate is confirmed as the first *code-behavior* change (Phase 2),
preceded only by the Phase 1 contract. No destructive rename of existing `--inet-*` tokens.

### Task 3 — Identify legacy visualization selectors / token hooks customers may rely on

**Outcome: confirmed present. Dense data surfaces already borrow shell tokens — the core migration risk.**

Concrete instances found in `_themeable.scss`:

- **Worksheet detail table rows** reuse shell/worksheet tokens directly:
  - `.table-data__row` → `--inet-ws-table-odd-row-bg-color` / `--inet-ws-table-even-row-bg-color`
  - `.table-data__row:hover` → `--inet-hover-primary-bg-color`
  - `.table-data__row.highlighted` → `--inet-primary-color`
- **Shell-scoped table tokens** exist and are the current basis for table styling:
  `--inet-table-heading-bg-color`, `--inet-table-hover-bg-color`, `--inet-table-selected-bg-color`,
  `--inet-table-text-color`, `--inet-table-border-color`.
- **Global selection semantics** leak everywhere via `%selected-item-colors` / `.selected`
  (`--inet-selected-item-bg-color`, `--inet-selected-item-text-color`), and shell selection
  (`--inet-shell-selected-bg-color`) is applied through `.bg-selected`, `.bg-node-selected`, etc.

These are precisely the hooks the roadmap warns customer themes may depend on. They must be
**preserved as legacy defaults** and only reinterpreted inside the modern gate.

**Decision:** treat the above as the initial legacy-hook register. Expand it during the Phase 5
table/grid work and the post-phase screen-by-screen audits. Do not repoint any of these tokens
globally.

### Task 4 — Avoid using chart palette colors for routine widget chrome

**Outcome: low risk of CSS-side leakage today, because almost all chart color — marks *and*
in-graph chrome — is server-rendered, not browser CSS. The split is by render location, not by
"marks vs chrome."**

The initial audit framed this as "marks server-side, chrome browser-side." Deeper review shows that
was wrong: **in-graph chrome is server-rendered too.** The graph engine paints marks *and* axis
lines, tick labels, gridlines, legend swatches/border/background, and titles into the chart
image/SVG — including inline SVG, where fill/stroke are set as element attributes server-side
(`mxSvgCanvas.setAttribute("stroke", …)`). A search for chart-series CSS tokens (`--inet-*series*`,
`chart-series`, `categorical-color`) in the portal SCSS returns nothing.

Every server-rendered chart color resolves through this chain:

1. per-object descriptor color pickers — `AxisDescriptor.lineColor`, `PlotDescriptor.x/yGridColor`,
   `LegendsDescriptor.borderColor`, series `CategoricalColorFrame.setUserColor` (serialized to XML)
2. a **server-side CSS dictionary** — `CSSDictionary` (`format.css` / `defaults.css` via
   `PortalThemesManager` and `SreeEnv("css.location")`), keyed by `CSSConstants` chart classes such
   as `ChartAxisLine`, `ChartPlotLine`, `ChartLegend`, `ChartPalette`
   (e.g. `ChartLineColor.getAxisLineColor()` reads `CSSConstants.CHART_AXIS_LINE`)
3. hardcoded fallbacks — `GDefaults` / `CategoricalColorFrame.COLOR_PALETTE` (~40 colors)

So the two parallel, non-shared color systems are split by **where they render**:

- **System A** — browser `--inet-*` CSS custom properties: themes shell chrome and the
  **surrounding DOM chrome** only (viewsheet object title bar, chart toolbar/menu, interactive hover
  tooltip). None of this appears in export.
- **System B** — the graph engine + descriptors + server-side CSS dictionary: themes the entire
  chart image (marks **and** in-graph chrome). This is what the live view **and every export
  format** render.

**Export-consistency finding (the key point):** the live browser chart and all exports
(PDF/PNG/SVG/Excel/PPT/HTML) share the one server-side render path (`AssemblyImageService`,
`VSChartAreasService` for live; `PDFVSExporter`, `SVGVSExporter`, `HTMLVSExporter`, Excel/PPT via
`AbstractVSExporter`). Therefore:

1. Adding/changing a **browser CSS token will not drive the export look at all**, and for in-graph
   color it will not even change the live chart.
2. Any color that must appear in export **must** be driven server-side (descriptors and/or the
   `format.css` `CSSConstants` classes, with `GDefaults` fallback).
3. Routing in-graph chrome through browser tokens would create a **live/export mismatch** — the
   exact failure this task guards against.

**Decision:** Phase 8 (and Phase 6) are re-scoped by render location, not by marks-vs-chrome:
(a) server-rendered graph color — marks **and** in-graph chrome — owned by the graph engine and
bridged to the `format.css` / descriptor sources, with modern-vs-legacy selected by a server-side
(EM/org/theme) property; (b) browser `--inet-viz-*` tokens limited to surrounding DOM chrome that
never appears in export. This corrects the earlier chrome assumption and is now reflected in
Phases 6 and 8 of the roadmap.

### Task 5 — Avoid treating shell table selectors as a substitute for dense visualization tables

**Outcome: this substitution is the current state, not a hypothetical.** 

As shown in Task 3, dense data tables are styled today through shell/worksheet table tokens and
global selection helpers. There is no visualization-owned density model:

- density-adjacent tokens exist but are **shell-generic and single-valued**:
  `--inet-row-height`, `--inet-control-height-sm|md|lg`, `--inet-space-1…8`.
- there is **no** `comfortable` / `compact` / `dense` mode matrix, and no `--inet-viz-row-height`
  or per-mode padding tokens.

**Render-location caveat (verified after initial audit).** "Dense tables" are two different worlds,
and the dividing test is **"is it a viewsheet assembly?"** (anything extending `AbstractVSAssembly`
is server-owned — see the design spec's Rendering And Theming Architecture section and its Evidence
table). The CSS/token story above applies only to **browser DOM tables/lists that are not
assemblies** (worksheet detail, EM admin, dialog lists, portal lists, generic `selectable-list`
widgets).

**Viewsheet data-surface assemblies** — table assemblies (VSTable/VSCrosstab/VSCalcTable) **and
selection lists / trees** — get their density from the **server-side** model, not CSS: table row
height is a user-settable property (`TableDataVSAssemblyInfo.getRowHeight/setRowHeight`, default
`AssetUtil.defh`); selection-list cell height likewise (`SelectionBaseVSAssemblyInfo.getCellHeight/
setCellHeight`, serialized `cellHeight=`, default `AssetUtil.defh`); cell font/size comes from
`VSFormat`/`VSFormatModel` (default `Util.DEFAULT_FONT`). These are carried to the client in the
assembly model (`BaseTableModel.dataRowHeight`/`headerRowHeights`; note `rowHeights` lives on `SimpleTableModel`) **and** used by the exporter
(`VsToReportConverter.calculateRowHeights`). So `--inet-viz-*` density tokens cannot drive
viewsheet-assembly density and cannot affect export. This is the density analog of the chart
render-location finding in Task 4. (Correction: an earlier draft grouped selection lists under
browser DOM — they are viewsheet assemblies and are server-owned.)

**Decision:** density and table/grid work split by render location.
- **Browser DOM tables:** introduce visualization-owned `--inet-viz-*` density/state tokens rather
  than extend shell table tokens; shell tokens stay as legacy default, modern resolves inside the
  browser gate.
- **Viewsheet data tables:** modern density must be applied **server-side** by resolving the density
  defaults (`AssetUtil.defh`, `Util.DEFAULT_FONT`, padding) from a backend property, changing
  defaults only (user-set values preserved) and gated per-org/theme — not via the browser gate.

Now reflected in Phase 3, Phase 5 (roadmap), the design spec's Rendering And Theming Architecture
section, and the Phase 1 contract (§2a).

### Task 6 — Plan the screen-by-screen audit passes

**Outcome: two audit checkpoints scheduled; owners of the largest migration surface identified.**

- **Audit pass A** — after Phase 3 (density) + Phase 4 (widget-state) adoption.
- **Audit pass B** — after Phase 6 (chart chrome) standardization.

Highest-risk surfaces to target first (from this audit): worksheet detail tables, viewsheet
tables/crosstab, selection lists, and any chart chrome that currently reads as shell card framing.
Local widget overrides — not shared shell inheritance — are expected to be the larger migration
source, consistent with the token reuse found in Task 3.

**Decision:** the screen-by-screen audit is part of the implementation method, not optional polish.

## Consolidated Phase 0 Outcome

### Boundary decisions (confirmed against code)

- **Shell owns** typography, text roles, neutral surfaces, borders, radius, focus, primary action,
  and generic app chrome — all already tokenized under `--inet-*`.
- **Composer owns** authoring/editing state (`--inet-graph-assembly-*`, `.ws-*`, `.schema-*`).
- **Visualization owns** BI density, dense table/grid behavior, widget state, chart chrome, and
  analytical color meaning — none of which have a dedicated namespace today.

### Guardrail decisions

- New visualization work is **additive** under `--inet-viz-*`; **no destructive rename** of the
  existing `--inet-*` shell tokens.
- Legacy visualization (shell table tokens, global `.selected`, `.table-data__row`) remains the
  **default runtime path** until the modern gate is enabled.
- All server-rendered chart color — marks **and** in-graph chrome (axis lines, gridlines, legend
  border/background) — stays owned by the graph engine, descriptors, and the server-side CSS
  dictionary (`format.css`); browser `--inet-viz-*` tokens are limited to surrounding DOM chrome
  that never appears in export.
- Dense analytical tables are **not** a restyle of shell list/table behavior.
- Shared rollout is validated screen-by-screen after Phases 3–5 and Phase 6.

### Compatibility and rollout risks (evidence-based)

- Customer themes likely override shell table/selection tokens (`--inet-table-*`,
  `--inet-selected-item-*`, `--inet-shell-selected-bg-color`); global reinterpretation without a
  gate is high risk.
- All in-graph color (marks and chrome) lives in a separate server-side system (graph engine +
  descriptors + `format.css` CSS dictionary) and is shared by the live view and every export format.
  Browser CSS cannot drive the export look, and the browser-side gate cannot switch any
  server-rendered graph color — a coordination gap Phases 6 and 8 must close explicitly, or
  live/export will diverge.
- Uneven adoption across widget systems is likely because dense tables currently depend on local
  and shell-shared tokens rather than a single viz layer.

### Phase sequencing implications (unchanged from roadmap, now confirmed feasible)

- Phase 1 documents the shell→viz token contract explicitly (the `--inet-*` surface exists to
  document against).
- Phase 2 introduces the gate first (greenfield, low collision risk).
- Phases 3–4 establish density + state vocabulary before any palette work.
- Phase 5 table/grid is the highest-leverage first adoption surface.
- Phases 6 and 8 must bridge to the server-side graph engine, descriptors, and `format.css` for all
  in-graph color (marks and chrome), so live view and every export format stay in sync.

## Exit Checklist — Ready To Start Phase 1?

| Prerequisite | Status | Source |
|---|---|---|
| Ownership boundaries agreed (viz / shell / Composer) | Done — see Task 1 | this doc |
| Shell→viz token contract documentable against real tokens | Ready — `--inet-*` layer exists | `_variables.scss`, `_themeable.scss` |
| `--inet-viz-*` naming convention reserved | Ready — namespace is free | no matches in code |
| Legacy selectors/hooks catalogued | Initial register done — see Task 3 | `_themeable.scss` |
| Gate mechanism chosen (theme mode + root class/attr) | Decision pending Phase 2, no blocker | greenfield |
| Default density via EM property (not new UI) | Deferred to Phase 3 design | `CustomThemesManager` for persistence |
| Render-location split acknowledged (server graph vs browser DOM) + export consistency | New constraint recorded — see Task 4 | `graph/aesthetic/*`, `report/io/viewsheet/*` |
| Screen-by-screen audit passes scheduled | Done — passes A and B — see Task 6 | this doc |

**Verdict:** Phase 0 is complete enough to begin Phase 1. The render-location constraint has been
folded into the roadmap: Phases 6 and 8 now split chart color by *where it renders* — server-rendered
graph color (marks **and** in-graph chrome: axis lines, gridlines, legend border/background) driven
by descriptors + `format.css` + `GDefaults` and shared by live view and all exports, versus browser
`--inet-viz-*` tokens limited to surrounding DOM chrome that never appears in export. Modern-vs-legacy
graph color is selected by a server-side property, not the browser gate, so live and export stay in
sync.

## Related Specs

- [visualization-implementation-roadmap.md](./visualization-implementation-roadmap.md)
- [visualization-design-spec.md](./visualization-design-spec.md)
- [theme-strategy-overview.md](./theme-strategy-overview.md)
- [palette-coordination-recommendations.md](./palette-coordination-recommendations.md)
