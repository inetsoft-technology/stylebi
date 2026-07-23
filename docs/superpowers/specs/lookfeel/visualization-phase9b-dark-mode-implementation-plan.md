# StyleBI Visualization — Phase 9B (Dark Mode) — Implementation Plan

> Status: **NOT STARTED — planning only.** This document schedules the dark-mode work that every
> prior server/CSS phase deferred to "the initiative's dark pass." No code has been written.

## Scope

Phase 9B delivers the single, cross-cutting dark-mode pass that Phases 5, 6, 6A, 7, 8, and 9 each
deferred with the identical note ("dark variants ride the initiative's dark pass"). It is carved out
as its own phase — rather than folded into the Phase 9C consolidation — because dark mode is *one
coherent concern* spanning table structure, chart chrome, object title chrome, KPI/output chrome,
the categorical palette, and the DOM token layer. The design values already exist; the missing work
is the **selection mechanism** and the **wiring**, and both are shared across all six surfaces.

Per the roadmap, this phase is inserted before Phase 10 (validation stays terminal), mirroring the
6A / 9A lettered-insert precedent.

## The design values already exist

Dark values were recorded in the swatches as each light-first phase shipped. Phase 9B does not invent
color — it wires what is already specified:

| Surface | Dark tokens (in `visualization-palette-swatches.html`) |
|---|---|
| Table structure | `--viz-gridline-dark #3A383D`, `--viz-header-bg-dark #2D2B30`, `--viz-header-text-dark #CAC4D0` |
| Chart / object labels | `--dark-chart-label #E6E0E9`, `--dark-chart-sub #CAC4D0` |
| Surfaces | `--dark-surface-canvas #1C1B1F`, `--dark-surface-default #252428`, `--dark-surface-subtle #2D2B30` |
| Borders | `--dark-border-default #49454F` |
| Categorical palette | `--series-dark-1..8` (plus `--vivid-series-dark-*` / `--family-series-dark-*` variants) |

Phase 5 also recorded the table-structure dark trio `#3A383D` / `#2D2B30` / `#CAC4D0` inline in its
C1 deferral note; those are the same values as the swatch tokens above.

## Rendering boundary (unchanged — dark mode must respect it)

Dark mode obeys the same System-A / System-B split as every phase (see the Rendering And Theming
Architecture section of [visualization-design-spec.md](visualization-design-spec.md)):

- **System A — browser DOM (`--inet-viz-*`, `.viz-modern`):** state colors, DOM-table gridline/header,
  tooltip/surround chrome, embedded-control overlays. Dark-themeable by adding `-dark` token values
  under a browser dark scope. Not part of export.
- **System B — server resolvers (org-scoped gate, `VS*Defaults`):** table structure, chart chrome,
  title chrome, KPI/output chrome, categorical palette. These are baked into the chart image / cell
  render and are **shared with export**.

### THE blocking decision: do exports go dark? (Task 0)

The four server resolvers (`VSTableStructureDefaults`, `VSChartChromeDefaults`, `VSTitleChromeDefaults`
/ `VSOutputChromeDefaults`, `VSChartPaletteDefaults`) are built on one guarantee: **live view and
every export format resolve the same `VSFormat`/`Color` default, so they are pixel-identical.** That
guarantee is exactly what makes dark mode non-trivial for System-B surfaces:

- If dark mode must also darken **exports** (PDF/PNG/SVG/Excel/PPT/HTML), the existing resolver
  pattern extends cleanly — add dark variants selected by a dark flag, and live+export stay in sync.
- If dark mode is **live-only** (the common product expectation — printed/exported output stays
  light on white paper), then the export-consistent resolver is the **wrong mechanism** for the
  System-B surfaces, because it cannot make live diverge from export by construction. A live-only
  dark treatment for server-rendered surfaces would need a *separate live-only override seam*, not
  the shared default resolver.

This is the first thing Phase 9B must resolve with the owner. It determines whether Tasks 2–4 extend
the existing resolvers or introduce a live-only path. Recommended default: **dark mode is live-only;
exports remain light** (matches print expectations and avoids a second export baseline to validate),
which means System-B dark is delivered as a live-only render override, and System-A dark is the
straightforward token pass.

## Tasks

- [ ] **Task 0 — Selection mechanism + export scope (decision, blocks 2–4).**
  Decide (a) how dark mode is *selected* — browser `prefers-color-scheme`, an explicit theme/body
  dark class, or the org/theme dark flag the shell dark work uses — and (b) whether exports darken.
  Ground the selector against however the shell initiative selects dark (reuse it; do not invent a
  second dark switch). Record the decision here before touching code.

- [ ] **Task 1 — System-A DOM dark tokens.**
  Add `-dark` values for the adopted `--inet-viz-*` state tokens (hover, selection bg/text/border,
  sorted) and the DOM-table structure tokens in `_viz-tokens.scss`, consumed under the chosen dark
  scope. Follow the Phase 9 two-tier `--inet-viz-*-modern` seam so customer overrides still reach the
  gated scope. Gate-off and light-mode byte-identical.

- [ ] **Task 2 — Table structure dark (System B).**
  Wire `#3A383D` / `#2D2B30` / `#CAC4D0` (gridline / header-bg / header-text) through
  `VSTableStructureDefaults` per the Task 0 decision (resolver dark variant if exports darken; live-only
  override otherwise). Defaults-only; user formats still win.

- [ ] **Task 3 — Chart chrome + title chrome + KPI/output chrome dark (System B).**
  Wire the dark label/surface/border values (`--dark-chart-label`, `--dark-surface-*`,
  `--dark-border-default`) through `VSChartChromeDefaults`, `VSTitleChromeDefaults`, and
  `VSOutputChromeDefaults`. These three share a palette and a gate, so they move together.

- [ ] **Task 4 — Categorical palette dark (System B).**
  Give `VSChartPaletteDefaults` a dark series head (`--series-dark-1..8`) selected by the dark flag,
  mirroring its existing light modern head. Coordinate the tail with the legacy `COLOR_PALETTE`.

- [ ] **Task 5 — Validation.**
  Fold a dark-mode pass into the Phase 10 screen-by-screen audit: legacy (gate-off) unchanged, light
  modern unchanged, dark modern correct on tables, charts, title bars, KPI/output, and the palette.

## Deferred within 9B

- **Dark exports** — if Task 0 resolves to live-only, exporting in dark is a separate, larger pass
  (a second export baseline to validate) and is explicitly out of 9B scope.
- **Dark sequential/diverging ramps** — depends on ramps landing at all (Phase 9C item 2). If ramps
  ship, add their dark variants alongside; if ramps stay deferred, no dark ramp work exists yet.
- **`--vivid-series-dark-*` / `--family-series-dark-*`** — alternate palette families; ship the
  default `--series-dark-*` first, alternates only if the light alternates are also exposed.

## Related

- [visualization-implementation-roadmap.md](visualization-implementation-roadmap.md) — Phase 9B section
- [visualization-phase9c-deferred-consolidation-implementation-plan.md](visualization-phase9c-deferred-consolidation-implementation-plan.md)
- [visualization-phase5-implementation-plan.md](visualization-phase5-implementation-plan.md) (table dark)
- [visualization-phase8-implementation-plan.md](visualization-phase8-implementation-plan.md) (palette dark)
- [visualization-palette-swatches.html](visualization-palette-swatches.html) (dark token source of truth)
