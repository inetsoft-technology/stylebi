# StyleBI Visualization Implementation Roadmap

## Purpose

This document translates the visualization design strategy into an implementation sequence for StyleBI visualization surfaces.

It is intended to answer:

- what visualization should inherit from shell first
- which token groups visualization should own
- how compatibility gating should work for existing customers
- which changes are foundation work versus adoption work versus analytical specialization
- which surfaces should be standardized first
- what should be deferred until the visualization foundation is stable

This roadmap assumes shell and Composer token work are already underway or complete. It should not be used to replace shell or Composer roadmaps.

Use [visualization-design-spec.md](E:\home\dev\github\lookfeel\specs\visualization-design-spec.md) for visualization layer behavior, surface rules, state vocabulary, and compatibility intent.

## Shell And Composer Boundary

This roadmap covers:

- visualization token work layered on top of shell foundations
- compatibility gating for legacy versus modern visualization
- visualization density and widget-state rollout
- table/grid, chart chrome, KPI, and embedded-control standardization
- analytical color system definition

This roadmap does not redefine:

- shell neutral surfaces, typography, spacing, radius, and focus
- Composer authoring-state language
- generic shell dialogs, tabs, forms, and navigation
- every chart-type-specific visual rule in the first pass

For shell-first work such as buttons, inputs, dialogs, nav, tabs, toolbars, and shell tables, use [shell-implementation-roadmap.md](E:\home\dev\github\lookfeel\specs\shell-implementation-roadmap.md).

## Related Specs

- [theme-strategy-overview.md](E:\home\dev\github\lookfeel\specs\theme-strategy-overview.md)
- [shell-design-spec.md](E:\home\dev\github\lookfeel\specs\shell-design-spec.md)
- [shell-implementation-roadmap.md](E:\home\dev\github\lookfeel\specs\shell-implementation-roadmap.md)
- [palette-coordination-recommendations.md](E:\home\dev\github\lookfeel\specs\palette-coordination-recommendations.md)
- [visualization-design-spec.md](E:\home\dev\github\lookfeel\specs\visualization-design-spec.md)

## Delivery Model

Use four change types throughout this roadmap:

- `foundation`
  - define inherited versus visualization-owned runtime tokens and contracts
- `gating`
  - preserve legacy behavior while introducing explicit modern visualization opt-in
- `adoption`
  - update visualization selectors and components so they consume shared visualization tokens consistently
- `specialization`
  - add denser, more analytical behavior only where the surface requires it

## Value Source Rule

Unless otherwise noted:

- inherited shell values come from [shell-design-spec.md](E:\home\dev\github\lookfeel\specs\shell-design-spec.md) and [shell-implementation-roadmap.md](E:\home\dev\github\lookfeel\specs\shell-implementation-roadmap.md)
- visualization behavior, state usage, and density guidance come from [visualization-design-spec.md](E:\home\dev\github\lookfeel\specs\visualization-design-spec.md)
- cross-layer color rules come from [palette-coordination-recommendations.md](E:\home\dev\github\lookfeel\specs\palette-coordination-recommendations.md)

Inline `Default / alias` values in this roadmap are meant to make implementation self-sufficient. When a row needs special clarification, the source should be called out explicitly in that row or subsection.

## Themeability And Compatibility Mechanism

Visualization should remain runtime-themeable while preserving a safe compatibility path.

That means:

- new visualization tokens should be backed by runtime `--inet-*` variables
- legacy visualization should remain the default behavior
- modern visualization should activate only through an explicit gate
- customer themes should be able to defer modern visualization adoption without blocking shell or Composer progress
- default visualization density should be configurable through an EM property setting rather than requiring a new EM UI surface

### Themeability rule

If a visualization modernization change is meant to remain runtime-themeable, do not stop at one-off selector rewrites.

Use this sequence:

1. define or alias the runtime token
2. preserve legacy mapping by default
3. activate modern mapping only inside the visualization gate
4. adopt the token in shared visualization selectors or widget surfaces
5. expose the token in theme editor models where customer override is required

Density mode should follow the same runtime model, but its default selection should come from an EM property setting rather than a dedicated first-pass UI control.

### Compatibility gate rule

The first implementation pass should use a small number of clear switches:

- a theme-level visualization mode such as `legacy` versus `modern`
- a root class or attribute such as `.viz-modern` or `[data-viz-theme="v2"]`
- optional compatibility alias tokens that let existing widget hooks stay stable while modern values are introduced

Do not begin by overwriting legacy selector meaning globally.

## Primary Implementation Areas

This roadmap is implementation-oriented rather than codebase-exhaustive because visualization surfaces may span multiple widget systems.

The primary work areas are:

| Area | Main responsibility |
|---|---|
| token contract | inherited shell foundations versus visualization-owned tokens |
| compatibility gate | explicit legacy versus modern visualization activation |
| density layer | compact row heights, spacing, and control sizes for visualization surfaces |
| widget-state layer | hover, selected, active, contextual, inline-edit, sorted, filtered, pinned, warning, anomaly, dimmed |
| table/grid standardization | shell-cohesive but BI-dense table behavior |
| chart chrome standardization | headers, legends, axes, gridlines, tooltips, inline widget controls |
| KPI and summary widgets | analytical hierarchy without heavy shell framing |
| analytical color systems | categorical palettes, sequential ramps, diverging ramps, conditional formatting |

## Visualization Surface Implementation Map

Use [visualization-design-spec.md](E:\home\dev\github\lookfeel\specs\visualization-design-spec.md) for the surface model and state-distribution rules.

This roadmap keeps only the implementation-facing mapping:

- compatibility gating should land before broad selector reinterpretation
- density and widget-state rollout should precede chart-palette specialization
- table/grid standardization is the highest-leverage shared visualization adoption surface
- chart chrome should inherit shell neutrals first, then adopt visualization density and state where needed
- KPI and embedded controls should reuse shared visualization token families rather than invent parallel systems

Like the shell and Composer implementation work, visualization work should include screen-by-screen review after major shared phases land. Shared token and selector adoption will not catch every legacy table treatment, chart-chrome override, or one-off widget behavior on its own.

## Token Ownership Model

Visualization should follow this token ownership model:

- inherited from shell:
  - font family
  - strong/default/muted text roles
  - neutral surfaces
  - default borders
  - radius
  - focus ring
  - primary action color
- visualization-owned:
  - density tokens
  - widget-state tokens
  - chart palettes
  - analytical ramps
  - conditional formatting tokens
- shared but overridable within visualization:
  - row height
  - widget toolbar density
  - compact control size inside widgets
  - widget chrome spacing

## Phase 0: Guardrails And Baseline Audit

### Goal

Keep visualization work analytically focused without backsliding into shell-style over-framing or uncontrolled color usage.

### Tasks

- confirm which surfaces are visualization-owned versus shell-owned
- treat compatibility gating as the first architectural requirement rather than a late migration concern
- identify any legacy visualization selectors or token hooks that existing customers may already rely on
- avoid using chart palette colors for routine widget chrome
- avoid treating shell table selectors as a substitute for dense visualization tables
- plan a screen-by-screen audit pass after density/state adoption and again after chart chrome standardization

### Phase 0 Outcome

#### Boundary decisions

- Shell remains the owner of:
  - shared typography
  - strong/default/muted text roles
  - neutral surfaces
  - default borders
  - radius
  - focus treatment
  - generic application controls, dialogs, tabs, forms, navigation, and shell tables
- Composer remains the owner of:
  - authoring-state meaning
  - worksheet and workflow editing cues
  - contextual/selected/dimmed authoring emphasis inside editing surfaces
- Visualization remains the owner of:
  - BI density
  - widget chrome inside data surfaces
  - table/grid analytical behavior
  - chart palettes and analytical ramps
  - visualization-specific widget states
  - conditional-formatting meaning

#### Guardrail decisions

- Compatibility gating is a first-pass architectural requirement, not deferred cleanup.
- Legacy visualization should remain the default runtime path until a modern visualization mode is explicitly enabled.
- New visualization work should prefer additive `--inet-viz-*` tokens and gated reinterpretation over destructive rename or global selector repointing.
- Chart categorical and ramp colors should be reserved for data meaning and should not become routine widget-chrome colors.
- Dense analytical tables should not be treated as a simple restyling of shell list or shell-table behavior.
- Shared visualization rollout should be validated screen by screen after major adoption phases, because token work alone will not catch every one-off legacy override.

#### Compatibility and rollout risks

- Existing customers may already depend on legacy visualization selectors or implicit widget hooks, so broad selector reinterpretation without a gate is high risk.
- Shell modernization and visualization modernization should stay decoupled enough that customers can adopt shell and Composer progress without being forced onto modern visualization at the same time.
- Visualization surfaces likely span multiple widget systems, so uneven adoption is a realistic risk unless table/grid and chart chrome are standardized through shared token families first.
- Local widget overrides are likely to be a larger migration source than shared shell inheritance, especially in dense table behavior, embedded controls, and chart-adjacent chrome.

#### Phase sequencing implications

- Phase 1 should document the shell-to-visualization token contract explicitly rather than inferring it from shell usage.
- Phase 2 should introduce a small, explicit gate such as a theme mode plus scoped root class or attribute before any broad selector reinterpretation.
- Phases 3 and 4 should establish density and widget-state vocabulary before chart-palette specialization.
- Phase 5 table/grid standardization is the highest-leverage first adoption surface because it carries density, state, scanability, and compatibility concerns at once.
- Chart chrome, KPI hierarchy, and analytical color systems should build on the shared density/state foundation rather than begin as isolated redesign tracks.

## Phase 1: Contract Definition And Initial Scaffolding

### Goal

Define inherited versus visualization-owned token groups explicitly.

### Tasks

#### 1. Inherited shell contract

Document the shell values visualization consumes directly:

- font family
- text hierarchy
- neutral surfaces
- default borders
- radius
- focus ring
- primary action color

#### 2. Visualization-owned groups

Define the initial visualization token groups for:

- density
- widget state
- chart color
- analytical thresholds
- conditional formatting

#### 3. Naming rule

Use explicit `--inet-viz-*` naming for new visualization-owned tokens so ownership is visible and does not blur back into shell or Composer.

### Output

- stable token contract between shell and visualization

### Implementation note

- This phase is primarily a contract-definition phase rather than a broad adoption phase.
- It may include initial token scaffolding where that helps lock the contract into code, but it should not yet require large selector rewrites or widget-by-widget rollout.
- Broad runtime behavior change should begin in Phase 2 with compatibility gating, then expand in later adoption phases.

## Phase 2: Compatibility Gate

### Goal

Preserve current customer behavior while introducing a modern visualization opt-in path.

### Tasks

#### 1. Default legacy behavior

- keep legacy visualization as the default runtime behavior
- keep existing widget hooks resolving to legacy behavior unless the modern gate is enabled

#### 2. Gate activation model

Introduce a small, explicit activation mechanism such as:

- theme mode: `legacy` or `modern`
- root class: `.viz-modern`
- root attribute: `[data-viz-theme="v2"]`

#### 3. Dual mapping rule

During migration:

- legacy selectors should continue to map to legacy values by default
- modern visualization should reinterpret those hooks only inside the gated scope
- new `--inet-viz-*` tokens should be introduced alongside old hooks rather than by destructive rename

### Output

- safe compatibility gate for gradual customer adoption

## Phase 3: Density Foundation

### Goal

Establish BI-appropriate density as a first-class visualization behavior.

### Rendering boundary: server-rendered data tables vs browser DOM

Density obeys the same render-location split as color (see the Rendering And Theming Architecture
section of [visualization-design-spec.md](./visualization-design-spec.md)). This governs how the
tasks below are implemented.

The reliable test (see the design spec's Rendering And Theming Architecture section): **is it a
viewsheet assembly?** Anything extending `AbstractVSAssembly` is server-owned.

- **Viewsheet data-surface assemblies** — table assemblies (VSTable/VSCrosstab/VSCalcTable) **and
  selection lists / trees**: row/cell height, font size, and padding are **server-side and
  user-settable** (`TableDataVSAssemblyInfo` row heights, `SelectionBaseVSAssemblyInfo` cell height,
  `VSFormat` fonts, `AssetUtil.defh` / `Util.DEFAULT_FONT` defaults), carried to the client in the
  assembly model and rendered from the same values on export. CSS density tokens do not drive these.
- **Browser DOM tables/lists** (non-assemblies: worksheet detail, EM admin, dialog lists, portal
  lists): CSS-density-themeable through `--inet-viz-*`; not part of viewsheet export.
  (Note: "EM admin" = the Enterprise Manager admin UI's Angular Material tables — admin chrome, not
  BI output, and in a separate build that does not import the viz tokens; a *secondary* density
  candidate, not a primary visualization surface. See the design spec's Browser-DOM density caveats.)

Rule: `--inet-viz-*` density tokens own browser DOM (non-assembly) data surfaces only. Modern
density for viewsheet assemblies must be driven server-side (see Configuration source below), or
live view and export will diverge.

### Tasks

#### 1. Density tokens

Define:

- row height
- widget chrome row height
- cell padding x
- cell padding y
- compact widget toolbar height
- compact control height inside widgets

Also define how `comfortable`, `compact`, and `dense` map onto those tokens at runtime.

The density property should resolve to an explicit per-mode value matrix rather than to a single loose baseline.

#### 2. Density rules

- density should come from spacing, alignment, and interaction design rather than just shrinking text
- dense visualization controls must remain legible and hittable
- dense widget chrome should remain separate from ordinary shell control sizing
- most density change should come from row height and padding before type-size reduction

#### 3. Configuration source

- store the default visualization density in an EM property setting
- do not require a new EM UI surface in the first implementation pass
- apply the configured density mode through **two** paths, by render location:
  - **browser DOM surfaces:** map the property value onto the `--inet-viz-*` density token set at
    runtime
  - **viewsheet data tables:** resolve the server-side density *defaults* (`AssetUtil.defh` row
    height, `Util.DEFAULT_FONT`, padding) from the same property so the live table model **and**
    every export format render the configured density
- apply modern density to **defaults only** — preserve explicit user-set row heights and fonts
- gate the modern default server-side (per-org/theme), not via the browser `.viz-modern` class,
  because changing a default reflows saved viewsheets that rely on it

Implementation is grounded: the server-side default is the hardcoded constant `AssetUtil.defh = 20`
(no property exists yet), resolved at two shared choke points — `BaseTableService` (live model) and
`VsToReportConverter.calculateRowHeights` (export) — with `userDataRowHeight`/`userHeaderRowHeight`
flags marking user-set values. See the *Verified implementation anchors* table in
[visualization-design-spec.md](./visualization-design-spec.md) for file:line references and the
step-by-step plan in [visualization-phase3-implementation-plan.md](./visualization-phase3-implementation-plan.md).

Resolved (2026-07-07): a **single unified density matrix** drives both halves, recalibrated so
`dense = 20px = today's default` (comfortable 28 / compact 24 / dense 20). The default mode is
**`dense`** (zero reflow on enable), selected **org-wide** via a new `viewsheet.density` property;
per-component user-set row heights still take precedence. Server-side font-size density is deferred.

#### 4. Local override boundary

Allow local density override only for scan-heavy analytical list or table surfaces such as:

- tables and grids *(viewsheet assemblies: server-side; DOM tables: CSS)*
- crosstab and pivot-style views *(viewsheet assemblies: server-side)*
- selection lists / trees *(viewsheet assemblies: server-side — override applies to the server default)*
- long filter member lists *(DOM: CSS)*
- multi-select pick lists *(DOM: CSS)*
- long dropdown result panels that behave like dense lists *(DOM: CSS)*

Do not plan local density override in the first pass for:

- charts
- KPI widgets
- short ordinary dropdowns
- standard shell form controls
- legends, tooltips, or other small chrome fragments

### Output

- reusable visualization density baseline distinct from shell sizing

## Phase 4: Widget-State Foundation

### Goal

Define and standardize the shared visualization-state model across data surfaces.

### Tasks

Define tokens and usage rules for:

- hover
- selected
- active
- contextual
- inline-edit
- sorted
- filtered
- pinned
- warning
- anomaly
- dimmed

### Output

- consistent visualization state vocabulary across widgets

### Validation checks

- hover remains lighter than selected
- active is distinguishable from long-lived selection
- filtered and sorted states are visible without creating header noise
- warning and anomaly remain meaning-bearing rather than decorative

### Status (implemented 2026-07-08)

Phase 4 is complete as a **vocabulary-only** phase — see
[visualization-phase4-implementation-plan.md](./visualization-phase4-implementation-plan.md). Modern
values were assigned to the 13 `--inet-viz-*` state tokens under the gate, the render-location routing
for all 11 states was recorded, and the contract was proved on one DOM surface (worksheet-detail table
row hover). **No server render path was changed**, so export-visible state was defined but not yet
rendered from these tokens. Forward impact on later phases:

- **Phase 5** owns the server-side bridge for export-visible **table/selection state** — notably the
  selection-list `selected` fill (server `VSFormat`), plus broad DOM adoption of hover/selected/active/
  sorted/filtered/pinned across tables and lists.
- **Phase 8** owns the `warning`/`anomaly` **conditional-formatting** bridge into the server-side
  render path (`VSFormat` / `format.css`), consistent with its chart-color server bridge.

## Phase 5: Table And Grid Standardization

### Goal

Standardize the core table/grid behavior used across visualization surfaces.

### Rendering boundary: two table worlds

Table work spans two rendering models that must not be conflated (see Phase 3 and the design spec's
Rendering And Theming Architecture section):

- **Viewsheet data-surface assemblies** (VSTable/VSCrosstab/VSCalcTable, and selection lists/trees —
  anything extending `AbstractVSAssembly`): structure and density (row/cell height, font, padding,
  borders) come from the server-side assembly/format model and are shared with export. Standardize
  these through the server-side defaults and `VSFormat`, not CSS. State cues that are DOM overlays
  (hover highlight, selection outline) may still be browser-rendered, but anything that must appear
  in export is server-side.
- **Browser DOM tables/lists** (non-assemblies: worksheet detail, EM admin, dialog lists, portal
  lists): fully CSS-themeable through `--inet-viz-*` density and state tokens; not exported.

Apply the visual-structure and state tasks below to each world through its correct system.

### Tasks

#### 1. Visual structure

- define subtle gridline behavior
- define header hierarchy
- define scan-friendly row rhythm
- define tabular numeral usage and numeric alignment rules

#### 2. State adoption

Apply shared visualization state families to:

- row hover
- row selection
- active cell
- grouped or contextual headers
- sorted headers
- filtered headers or chips
- pinned/frozen dividers
- inline-edit states

#### 3. Boundary rule

Dense visualization tables should not reuse shell list or shell-table behavior as their final design system.

### Output

- a consistent visualization table/grid state model

## Phase 6: Chart Chrome Standardization

### Goal

Standardize the non-mark portions of charts so they feel cohesive with shell without borrowing shell visual density.

### Rendering boundary: in-graph chrome vs surrounding DOM chrome

The canonical statement of this constraint is the Rendering And Theming Architecture section of
[visualization-design-spec.md](./visualization-design-spec.md); the summary below is the
implementation-facing view.

Chart chrome is not one layer. It splits by *where it is rendered*, and only one half is
browser-CSS-themeable.

- **In-graph chrome** — axis lines, tick labels, gridlines, legend swatches/border/background,
  axis/legend titles, plot labels. These are painted **server-side** by the graph engine (colors
  from descriptor color pickers → server-side CSS dictionary classes such as `ChartAxisLine`,
  `ChartPlotLine`, `ChartLegend` in `format.css` → `GDefaults`) and baked into the chart image/SVG,
  including inline SVG (fill/stroke are set as element attributes server-side). Browser
  `--inet-viz-*` tokens **do not** color these, and the live view and every export format share
  this one server-side render path.
- **Surrounding DOM chrome** — the viewsheet object title bar, chart toolbar/menu, and the
  interactive hover tooltip. These are Angular-rendered DOM, are browser-CSS-themeable, and are
  **not part of the exported chart image**.

Export-consistency rule: any color/appearance change that must appear in PDF/PNG/SVG/Excel/PPT/HTML
export must be driven server-side. Changing a browser CSS token cannot make the export match, and
for in-graph chrome cannot even change the live chart.

### Tasks

#### 1. Chart chrome surfaces

Standardize, respecting the rendering boundary above:

- **server-side (descriptors + `format.css` chart classes + `GDefaults`):** axes, axis titles/labels,
  gridlines, legends (swatch/border/background/title), plot labels
- **browser CSS (`--inet-viz-*`):** object header/title bar, inline widget controls, tooltip
  container styling (interactive only; not exported)

#### 2. Interaction rule

- chart chrome should remain visually lighter than data marks
- embedded chart controls should use compact shell-derived control language
- chart palettes should not bleed into surrounding chrome

### Output

- chart containers and chrome that frame data without competing with it
- explicit routing of each chrome surface to its correct theming system so live and export stay in sync

## Phase 6A: Assembly Title-Bar / Object-Chrome Defaults (server-side)

Inserted as a distinct pass (numbered `6A` to avoid renumbering Phases 7–10, which the phase plans and
design spec cross-reference). Surfaced during Phase 6 scoping — see
[visualization-phase6-implementation-plan.md](./visualization-phase6-implementation-plan.md) D3.

### Goal

Modernize the **viewsheet object title bar** (and any equivalent server-rendered object chrome) as a
gated server-side default, product-wide across every assembly type.

### Why it is its own pass, not Phase 6 or Phase 8

- It is **not** browser-DOM chart surround. Phase 6 grounding verified the title bar is rendered from
  `titleFormat` (a server `VSFormat`) with inline styles and **no CSS token hook**
  (`vs-title.component.html`), so it **appears in viewsheet export** — a browser `--inet-viz-*` change
  cannot drive it. It is server-owned like a table cell.
- It is **not** analytical color, so it does not belong in Phase 8. The title bar is **neutral
  chrome**, and it spans **all** assembly types (table, chart, gauge, text, image, selection, …), not
  charts alone. Folding it into Phase 8 would mix a product-wide chrome-default concern into a
  data-color phase.
- It is shaped exactly like **Phase 5's `VSTableStructureDefaults`**: a gated server-side
  `VSFormat`-default resolver, defaults-only, export-consistent, per-org gated.

### Tasks

- add a `VSTitleChromeDefaults` resolver mirroring `VSDensityDefaults` / `VSTableStructureDefaults`:
  gate on `viewsheet.modernVisualization` plus a secondary escape-hatch property (e.g.
  `viewsheet.modernObjectChrome`), org-scoped, returning modern title background/foreground/border/
  height **defaults**
- apply it **defaults-only** at the server-side `titleFormat`/`VSFormat` default seam so user-set
  title formats still win, and so the live model and every export format resolve the same values
- keep gate-off byte-identical; because it changes a default that reflows saved sheets, gate it
  per-org/theme (never the browser `.viz-modern` class), the same rule as Phase 3 density and Phase 5
  table structure
- define the modern title-chrome palette in the swatches (warm-neutral, quiet — "widget chrome stays
  quieter than the data"), coordinated with the Phase 5 table-structure and Phase 6 chart-chrome
  neutrals

### Dependencies / sequencing

Independent of Phase 6 Part A (CSS) and Phase 8 (data color). Naturally sequenced alongside or after
the Phase 6 Part B server bridge, since both are gated server-side `VSFormat`/descriptor default
resolvers and should share the modern-mode selection mechanism and neutral palette.

### Output

- a modern, gated, export-consistent object title-bar default applied product-wide, with user title
  formats preserved

## Phase 7: KPI And Embedded Controls

### Goal

Standardize KPI widgets and embedded widget controls around analytical hierarchy and compact interaction.

### Tasks

#### 1. KPI hierarchy

Define behavior for:

- primary value
- comparison value
- semantic emphasis
- sparkline or trend treatment
- secondary metadata

#### 2. Embedded controls

Define embedded filters and controls using:

- visualization density
- shell-derived control language
- explicit filtered/on states
- neutral passive chrome

### Output

- KPI and embedded-control patterns that feel analytical rather than shell-card-driven

## Phase 8: Analytical Color Systems

### Goal

Define the visualization-owned color systems that should not be borrowed from shell.

### Architectural split: server-rendered graph vs browser DOM

Analytical color spans two independent theming systems that share no tokens. The split is **not**
"marks versus chrome" — it is **where the pixels are rendered**. Almost all chart color, including
in-graph chrome, is server-rendered. Phase 8 must treat the two systems separately or it will only
half-work, and will break export consistency.

- **Server-rendered graph (marks and in-graph chrome).** Bars/lines/points **and** axis lines, tick
  labels, gridlines, legend swatches/border/background, axis and legend titles, and plot labels are
  all colored **server-side by the graph engine** (`inetsoft.graph.aesthetic.*`) and baked into the
  chart image/SVG — including inline SVG, where fill/stroke are set as element attributes
  server-side. Color for these resolves through: per-object descriptor color pickers
  (`AxisDescriptor.lineColor`, `PlotDescriptor.x/yGridColor`, `LegendsDescriptor.borderColor`,
  series `CategoricalColorFrame.setUserColor`) → the server-side CSS dictionary
  (`inetsoft.util.css.CSSDictionary`, parsing `format.css` / `defaults.css` via `PortalThemesManager`
  and `SreeEnv("css.location")`, keyed by `CSSConstants` classes such as `ChartAxisLine`,
  `ChartPlotLine`, `ChartLegend`, `ChartPalette`) → hardcoded `GDefaults` /
  `CategoricalColorFrame.COLOR_PALETTE`.
- **Browser DOM (surrounding chrome only).** Only the viewsheet object title bar, chart
  toolbar/menu, and the interactive hover tooltip are Angular-rendered DOM and themeable through
  browser `--inet-viz-*` CSS. These are **not part of the exported chart image**.

Consequences:

- Browser `--inet-viz-*` tokens **cannot** color marks, gridlines, axis lines, or legend
  border/background. They apply only to surrounding DOM chrome.
- The Phase 2 gate (`.viz-modern` / `[data-viz-theme="v2"]`) is browser-side and **cannot switch any
  server-rendered graph color** by itself.
- **Export consistency:** the live view and every export format (PDF/PNG/SVG/Excel/PPT/HTML) share
  the one server-side render path, so any change that must show up in export **must** be made
  server-side. A browser CSS change cannot make the export match and, for in-graph color, cannot
  even change the live chart.

### Tasks

#### 1. Server-rendered graph color (descriptors + `format.css` + `GDefaults`)

- treat the modern categorical palettes, sequential/diverging ramps, threshold/anomaly primitives,
  **and** in-graph chrome colors (axis lines, gridlines, legend border/background) as the single
  source of truth, then **bridge** that definition into the server-side sources — the `CSSConstants`
  chart classes in `format.css`, the relevant descriptor defaults, and where needed
  `CategoricalColorFrame` — rather than duplicating any of it as browser tokens
- reconcile the existing server-side sources — hardcoded `GDefaults` / `COLOR_PALETTE`, the
  `format.css` CSS dictionary, and per-org theme CSS — so one modern definition drives all of them
- do not route any exported color through browser CSS

#### 2. Browser DOM chrome color (browser tokens)

- define `--inet-viz-*` tokens only for surrounding DOM chrome (object header, inline widget
  controls, interactive tooltip container) — none of which appears in export
- keep these separate from routine widget chrome

#### 3. Conditional formatting

- define conditional formatting tokens and usage rules, explicitly tagging each surface as
  server-rendered (engine/`format.css`-themeable, appears in export) or browser DOM
  (CSS-themeable, not in export)

### Modern-mode selection for server-rendered color

Because the browser gate cannot reach any server-rendered graph color, Phase 8 must define how
legacy versus modern graph color is selected server-side — coordinated with the same EM property
mechanism Phase 3 uses for default density, or a per-org/theme property, rather than relying on the
browser-side visualization gate.

### Output

- explicit visualization-owned color systems for data meaning, correctly split by render location:
  server-rendered graph color (marks + in-graph chrome, appears in export) versus browser DOM chrome
- one modern graph-color definition bridged to the server-side sources so live view and all export
  formats stay in sync

### Dependency note

This phase depends on the render-location boundary recorded in the Phase 0 audit
([visualization-phase0-audit.md](./visualization-phase0-audit.md), Task 4) and the Phase 6 rendering
boundary. Do not begin graph-color work until the server-side bridge and the server-side
modern-mode selection mechanism are agreed.

## Phase 9: Themeability And Exposure Review

### Goal

Confirm the visualization token layer remains runtime-themeable and backward-compatible enough for existing themes, and expose the right tokens for customer override.

### Tasks

- verify all new visualization tokens are runtime `--inet-*` variables
- verify legacy visualization continues to behave correctly when the modern gate is off
- verify modern visualization activates only inside the gated scope
- add any customer-themeable visualization tokens to theme editor models where needed
- verify exposed token naming is clear enough for customer theming

### Output

- verified runtime-themeable visualization token surface with compatibility path

## Phase 10: Validation

### Functional checks

- dense tables remain readable and usable
- widget states are distinguishable without becoming noisy
- chart chrome remains subordinate to data
- embedded controls remain compact and understandable
- legacy visualization remains stable when the modern gate is off

### Visual checks

- visualization feels denser and more analytical than shell
- shell and visualization feel coordinated rather than disconnected
- chart colors are used for data meaning, not routine chrome
- KPI hierarchy reads through typography and value structure rather than heavy framing

### Coordination checks

- shell neutrals still frame visualization cleanly
- primary accent is used intentionally, not as the default first chart color
- semantic families remain aligned across shell and visualization without becoming identical in intensity
- Composer authoring-state colors do not leak into routine visualization states

### Screen-By-Screen Audit

After major visualization phases land, conduct a screen-by-screen review of representative surfaces.

The goal is to catch:

- legacy table or grid treatments still bypassing the shared visualization token layer
- chart chrome that still behaves like shell card framing rather than analytical framing
- embedded controls that are too shell-like or too visually noisy at BI density
- color leakage where chart palettes are being used for routine chrome
- unexpected differences between legacy and modern visualization modes

Treat this audit as part of the implementation method, not as optional polish.

## Deferred Work

These should not block the first visualization implementation phase:

- exhaustive chart-type-specific polish
- advanced conditional-formatting systems beyond initial primitives
- optional density modes beyond the primary BI baseline
- deeper visualization-authoring coordination inside Composer-specific editing UIs
- exhaustive cleanup of every legacy visualization naming hook in one pass
- **compact chart mini-toolbar under modern density** — attempted in Phase 6 Part A and deferred: the
  mini-toolbar is absolutely positioned with JS-computed geometry (vertical offset from the fixed
  constant `GuiTool.MINI_TOOLBAR_HEIGHT`, horizontal size/alignment from
  `miniToolbarService.getActionsWidth()`), so a CSS-only resize desyncs its positioning (overlap or
  gap). Requires a coordinated TS+CSS change that makes those geometry values density-mode-aware — not
  a CSS-only tweak. See [visualization-phase6-implementation-plan.md](./visualization-phase6-implementation-plan.md)
  A1 / Deferred 8.

## Recommended First Sprint

If visualization implementation begins now, the best first sprint is:

1. Phase 1 foundation contract
2. Phase 2 compatibility gate
3. Phase 3 density foundation
4. Phase 4 widget-state foundation
5. Phase 5 table and grid standardization

That establishes the highest-leverage visualization baseline before deeper chart and KPI work.
