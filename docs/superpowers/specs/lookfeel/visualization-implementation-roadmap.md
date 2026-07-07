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
- apply the configured density mode by mapping the property value onto the visualization density token set at runtime

#### 4. Local override boundary

Allow local density override only for scan-heavy analytical list or table surfaces such as:

- tables and grids
- crosstab and pivot-style views
- selection lists
- long filter member lists
- multi-select pick lists
- long dropdown result panels that behave like dense lists

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

## Phase 5: Table And Grid Standardization

### Goal

Standardize the core table/grid behavior used across visualization surfaces.

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

### Tasks

#### 1. Chart chrome surfaces

Standardize:

- headers
- legends
- axes
- gridlines
- tooltip containers
- inline widget controls

#### 2. Interaction rule

- chart chrome should remain visually lighter than data marks
- embedded chart controls should use compact shell-derived control language
- chart palettes should not bleed into surrounding chrome

### Output

- chart containers and chrome that frame data without competing with it

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

### Tasks

- define categorical palettes
- define sequential ramps
- define diverging ramps
- define threshold and anomaly primitives
- define conditional formatting tokens and usage rules
- keep these color systems separate from routine widget chrome

### Output

- explicit visualization-owned color systems for data meaning

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

## Recommended First Sprint

If visualization implementation begins now, the best first sprint is:

1. Phase 1 foundation contract
2. Phase 2 compatibility gate
3. Phase 3 density foundation
4. Phase 4 widget-state foundation
5. Phase 5 table and grid standardization

That establishes the highest-leverage visualization baseline before deeper chart and KPI work.
