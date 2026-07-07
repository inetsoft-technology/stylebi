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

- [theme-strategy-overview.md](E:\home\dev\github\lookfeel\specs\theme-strategy-overview.md)
- [shell-design-spec.md](E:\home\dev\github\lookfeel\specs\shell-design-spec.md)
- [shell-palette-spec.md](E:\home\dev\github\lookfeel\specs\shell-palette-spec.md)
- [palette-coordination-recommendations.md](E:\home\dev\github\lookfeel\specs\palette-coordination-recommendations.md)
- [visualization-implementation-roadmap.md](E:\home\dev\github\lookfeel\specs\visualization-implementation-roadmap.md)

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

Use [visualization-implementation-roadmap.md](E:\home\dev\github\lookfeel\specs\visualization-implementation-roadmap.md) for file targets, token rollout, compatibility gating, validation, and implementation sequencing.

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

It includes:

- widget headers
- legends
- axes and gridline scaffolding
- toolbar strips inside widgets
- filter chips and compact controls attached to widgets
- KPI framing and comparison scaffolding
- tooltip containers and annotation chrome

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
| table row height | `30px` | `28px` | `26px` | primary scan-density control |
| widget chrome row height | `30px` | `28px` | `26px` | headers, filter rows, lightweight widget strips |
| font size | `13px` | `13px` | `12px` | keep type mostly stable across modes |
| cell padding x | `8px` | `6px` | `4px` | horizontal scan rhythm |
| cell padding y | `6px` | `4px` | `2px` | vertical compaction |
| toolbar control height | `30px` | `28px` | `24px` | only for visualization-owned compact chrome |

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

Chart surfaces should distinguish clearly between chart chrome and data marks.

Chart chrome should:

- inherit shell neutrals first
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

- [theme-strategy-overview.md](E:\home\dev\github\lookfeel\specs\theme-strategy-overview.md)
- [shell-design-spec.md](E:\home\dev\github\lookfeel\specs\shell-design-spec.md)
- [shell-palette-spec.md](E:\home\dev\github\lookfeel\specs\shell-palette-spec.md)
- [palette-coordination-recommendations.md](E:\home\dev\github\lookfeel\specs\palette-coordination-recommendations.md)
- [visualization-implementation-roadmap.md](E:\home\dev\github\lookfeel\specs\visualization-implementation-roadmap.md)
