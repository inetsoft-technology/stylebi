# StyleBI Shell Design Spec

## Purpose

This document defines the broader shell design system for StyleBI

It covers:

- foundational design tokens
- sizing and spacing guidance
- component shape and spacing
- focus and interaction treatment
- widget application rules

This spec is meant to work alongside:

- [shell-palette-spec.md](E:\home\dev\github\lookfeel\shell-palette-spec.md)
- [composer-palette-spec.md](E:\home\dev\github\lookfeel\composer-palette-spec.md)
- [shell-implementation-roadmap.md](E:\home\dev\github\lookfeel\shell-implementation-roadmap.md)
- [composer-implementation-roadmap.md](E:\home\dev\github\lookfeel\composer-implementation-roadmap.md)

Two visual example HTML files show the intended theming outcomes and should be used as visual references alongside this spec.

- [shell-widget-examples-appendix.html](E:\home\dev\github\lookfeel\shell-widget-examples-appendix.html)
- [composer-widget-examples-appendix.html](E:\home\dev\github\lookfeel\composer-widget-examples-appendix.html)

When visualizatin palette is updated, check against this coordination guide to ensure consistency between all palette

- [palette-coordination-recommendations.md](E:\home\dev\github\lookfeel\palette-coordination-recommendations.md)

## Scope and Architecture

This spec covers the **application shell only**. The visualization layer (`vsobjects` canvas) is custom Angular with no Bootstrap dependency and is addressed under a separate strategy.

StyleBI has two shell applications with different underlying frameworks:

- **portal & composer** - Bootstrap 5 + ng-bootstrap (`modals`, `tabs`); primary implementation target for this spec
- **em** (Enterprise Manager) - Angular Material; deferred to Phase 2 once portal work is stable

## Composer Handoff

Composer should consume this shell spec in layers rather than replace it.

Inside Composer:

- shared chrome should follow shell rules
- shared dialogs, tabs, panels, forms, toolbars, and navigation should consume shell tokens first
- authoring-specific state meaning should not be defined in this shell spec

Use [composer-palette-spec.md](E:\home\dev\github\lookfeel\composer-palette-spec.md) for Composer-specific state families such as:

- context
- selected
- dimmed
- primary authoring emphasis

Worksheet and schema-editing behavior are narrower extensions on top of Composer rather than part of the shell baseline.

Use [composer-palette-spec.md](E:\home\dev\github\lookfeel\composer-palette-spec.md) when a surface is communicating worksheet-specific graph, schema, connection, or detail-row state rather than shared shell structure.

For execution details about how Composer adopts shell foundations and then layers authoring-state behavior on top, use [composer-implementation-roadmap.md](E:\home\dev\github\lookfeel\composer-implementation-roadmap.md).

## Shell Design Principles

- The shell should feel calm, modern, and efficient.
- Structure should come more from spacing, borders, and typography than from color variation.
- The shell should have one routine accent color.
- Most controls should use neutral treatments.
- Worksheet and visualization surfaces can add specialized state systems on top of the shell.

## Figma-Inspired Shell Language

The shell should explicitly follow a Figma-inspired product language rather than only a generic modernized Bootstrap look.

### Core characteristics

- neutral-first visual hierarchy
- minimal persistent chrome
- restrained use of fills and borders
- compact controls with precise spacing
- low, crisp elevation
- strong separation between shell structure and content surfaces
- emphasis through state, typography, and composition rather than extra decoration

### Surface model

The shell should rely on a small set of surface roles:

- `canvas`
  - the application background
  - quiet and low-contrast
- `raised`
  - dialogs, menus, cards, focused panels, overlays
- `subtle`
  - toolbars, tab rails, side panels, grouped shell controls
- `interactive-hover`
  - temporary hover or active chrome surfaces

The shell should not create many different tinted panel backgrounds unless they carry meaning.

### Control model

Controls should follow a clear hierarchy:

- filled
  - reserved for primary actions
- outline
  - reserved for secondary actions that still need visible affordance
- ghost
  - reserved for passive actions, toolbar actions, and low-priority commands

Secondary and default controls should feel neutral, not like colored brand buttons.

### Composition model

The shell should prefer:

- separators over boxed framing
- canvas layouts over nested card stacks
- grouped toolbars over many standalone controls
- quiet container headers over decorative panel chrome

The shell should avoid:

- unnecessary cards
- heavy framed panels around every region
- permanent button rows when hover or menus would suffice
- thick visual chrome that competes with BI content

## How To Use This Spec

This document is the design-facing shell reference.

Use it to understand:

- the intended shell language
- the role of shell foundations such as typography, spacing, radius, and focus
- the intended behavior and visual properties of shell widgets

For implementation phases, file targets, token rollout, themeability steps, and validation, use [shell-implementation-roadmap.md](E:\home\dev\github\lookfeel\shell-implementation-roadmap.md).

## Foundations

The foundation layer should focus on values that are shared enough to justify tokenization. It should not try to encode every possible component detail up front.

## Typography

### Recommended font model

- Primary UI font: `Inter` if available, otherwise current system fallback stack
- Fallback-safe product stack: `Roboto, -apple-system, system-ui, BlinkMacSystemFont, "Segoe UI", "Helvetica Neue", Arial, sans-serif`

### Type scale

| Role | Value | Notes |
|---|---|---|
| Display / page title | `28-36px / 600-700` | use sparingly |
| Section title | `18px / 600` | larger shell sections and page sections |
| Panel title | `14px / 600` | dialogs, cards, side panels, shell headers |
| Control label | `12px / 500-600` | buttons, tabs, shell table headers, form labels |
| Body text | `12-13px / 400` | default shell text |
| Meta / helper text | `11px / 400-500` | secondary information |
| Badge text | `10px / 600` | compact semantic labels |

### Typography guidance

- Keep shell UI mostly in the `12-13px` range.
- Use uppercase sparingly and mostly for compact labels or badges.
- Prefer medium weight over bold for control emphasis.
- Use tabular numerals in tables and numeric controls.

## Spacing

The shell should use a compact but consistent spacing scale.

### Recommended spacing scale

| Token role | Value | Notes |
|---|---|---|
| `space-1` | `2px` | fine adjustments |
| `space-2` | `4px` | shared dense control spacing |
| `space-3` | `6px` | compact internal spacing |
| `space-4` | `8px` | default small spacing |
| `space-5` | `12px` | control/container spacing |
| `space-6` | `16px` | section spacing |
| `space-7` | `20px` | use sparingly for larger grouping |
| `space-8` | `24px` | panel/dialog spacing |

### Spacing guidance

- Dense controls should rely heavily on `4px`, `6px`, and `8px`.
- Panels and dialogs should mostly use `12px` to `16px`.
- Avoid oversized whitespace in high-density shell areas.
- Use separators and layout rhythm instead of large padding when possible.

## Radius

The shell should use a restrained radius system.

### Recommended radius scale

| Token role | Value | Notes |
|---|---|---|
| `radius-sm` | `2px` | tight shell details |
| `radius-md` | `3px` | default controls |
| `radius-lg` | `4px` | cards and grouped controls |
| `radius-xl` | `6px` | dialogs and overlays |
| `radius-pill` | `999px` | pills and segmented controls |

### Radius guidance

- Default shell radius should be `3px`.
- Use larger radii only for overlays or deliberately softer surfaces.
- Avoid mixing many radius values within the same surface.

## Borders

### Recommended border widths

| Token role | Value | Notes |
|---|---|---|
| `border-thin` | `1px` | default border |
| `border-strong` | `1.5px` or `2px` | selected or focused shell states only |
| `border-table` | `0.5px` or `1px` | shell lists and light tables only |

### Border guidance

- Use border color and contrast before increasing border thickness.
- Most shell components should use `1px` borders.
- Selection or focus can use a stronger visual ring instead of thick persistent borders.

## Heights And Sizing

### Recommended component heights

| Component size | Value | Notes |
|---|---|---|
| `height-sm` | `24px` | compact shell controls |
| `height-md` | `30px` | default buttons/inputs/selects |
| `height-lg` | `36px` | larger emphasis controls only |
| `row-height-md` | `34px` | shell table/list chrome |
| `tab-height` | `32px` | nav/tab height |
| `pill-height` | `26px` | segmented controls where needed |

### Sizing guidance

- Default shell controls should target `30px`.
- Table/list rows should target `32-34px` in shell areas.
- Shell sizing should remain stable and predictable.

## Shadows And Elevation

The shell should use light elevation.

### Recommended elevation scale

| Role | Suggested shadow |
|---|---|
| low | `0 1px 2px rgba(0,0,0,.06), 0 1px 3px rgba(0,0,0,.04)` |
| medium | `0 2px 4px rgba(0,0,0,.08), 0 1px 6px rgba(0,0,0,.05)` |
| overlay | `0 8px 16px rgba(0,0,0,.12), 0 4px 14px rgba(0,0,0,.08)` |

### Elevation guidance

- Default cards should use low elevation.
- Dropdowns, popovers, and menus should use medium elevation.
- Modals should use overlay elevation.
- Do not stack many shadows in dense shell regions.

## Focus And Interaction

### Focus

- Use a `2px` focus ring.
- Use translucent primary color for the ring.
- Keep focus visible but not loud.

### Interaction guidance

- Use hover fills instead of strong hover color changes.
- Prefer subtle transitions around `80-120ms`.
- Use color mainly for action, selection, or semantics.
- Passive controls should rely on neutrals.

## Component Rules

## Buttons

### Button roles

- Primary: filled accent button
- Secondary: neutral outlined button
- Default: neutral ghost or light outline button
- Danger: semantic destructive button
- Ghost/icon: minimal chrome, hover fill only

Use emphasis intentionally:

- exactly one primary action per visible scope
- if two actions feel equally important, neither should be primary
- secondary actions should be used sparingly so they do not compete with the primary action
- most shell actions should usually be ghost or ghost/icon controls
- when a screen has no secondary action, review non-primary actions within each meaningful cluster or subsection
- prerequisite or unlock actions can outrank subsection-local create actions when choosing a secondary
- if one action in a cluster clearly stands above its neighboring ghost actions, it may be promoted to secondary
- do not force a single secondary across unrelated subsections of the same screen

### Button specs

| Token area | Guidance |
|---|---|
| Height | `30px` default |
| Padding | `0 12px` default, `0 8px` for compact |
| Radius | `3px` |
| Font | `12px`, `500` |
| Border | `1px` |

Role guidance:

- `Primary` (`solid accent`)
  - reserve for the single most important action a user can take on that surface right now
  - common examples: save, create, confirm, finish
- `Secondary` (`outlined`)
  - use for actions that still need visible emphasis, but are not the headline action
  - common examples: add, connect, refresh, move, clear, chooser/open-browser actions
- `Default` / `Ghost`
  - use for local controls inside subsections, toolbars, list/table controls, inline field actions, and secondary navigation
  - multiple ghost actions on a surface are normal
- `Danger`
  - reserve for destructive actions that need explicit semantic emphasis
  - use the semantic danger family rather than neutral shell treatments
- `Ghost/icon`
  - use for row actions, toolbar icon buttons, and minimal chrome actions where hover state provides the affordance

### Button behavior

- Primary buttons use shell primary.
- Secondary and default buttons should use neutral control tokens.
- Ghost and ghost/icon buttons should be the dominant button style by count on most screens.
- Danger buttons should use the semantic danger family for text, border, and surface treatment rather than neutral shell tokens.
- Icon buttons should usually be borderless until hover or focus.

## Inputs And Selects

### Input specs

| Token area | Guidance |
|---|---|
| Height | `30px` |
| Padding | `0 8px` |
| Radius | `3px` |
| Font | `12px` |
| Border | `1px` neutral |
| Focus | primary border + 2px ring |

### Input guidance

- Use white or raised surface fill by default.
- Hover should slightly strengthen border contrast.
- Placeholder text should use subtle text color.

## Dropdowns And Menus

### Dropdown specs

| Token area | Guidance |
|---|---|
| Surface | white / raised |
| Border | neutral |
| Radius | `3px` to `4px` |
| Elevation | medium |
| Item height | `34px` |
| Item padding | `0 12px` |

### Dropdown guidance

- Items should use subtle hover fill, not saturated fill.
- Active items can use a soft primary or contextual background with stronger text.
- Headers inside dropdowns should use subtle text and uppercase sparingly.

## Tabs And Navigation

### Tabs

- Height around `32px`
- Use neutral backgrounds and border structure
- Active tab should use primary as a focused indicator, not a large filled accent block

### Pills / segmented controls

- Use subtle neutral container fill
- Use white or raised active pill
- Keep pill states neutral unless they represent true selection emphasis

### Sidebar/navigation links

- Default links should be neutral
- Hover should use subtle neutral surface fill
- Active state can use soft primary background, primary border, or stronger text

### Figma-style navigation guidance

- tabs should read as part of the shell frame, not as colorful pills by default
- segmented controls should use subtle container fill with a raised active item
- sidebar items should rely on text weight, subtle fill, and a thin active indicator rather than saturated backgrounds
- navigation should feel quiet enough that BI content remains visually dominant

## Cards And Panels

### Card specs

| Token area | Guidance |
|---|---|
| Surface | white / raised |
| Border | neutral |
| Radius | `4px` |
| Shadow | low |
| Header padding | `8px 14px` |
| Body padding | `14px` |

### Card guidance

- Avoid heavy card styling in dense flows.
- Use cards where containment matters, not as default layout for everything.

### Figma-style containment guidance

- prefer open layouts with separators for primary work areas
- use cards for discrete modules, summaries, or overlays
- avoid nesting cards inside cards in shell-heavy screens
- large work surfaces should feel like canvas plus rails, not dashboard tiles by default

## Dialogs And Modals

### Dialog specs

| Token area | Guidance |
|---|---|
| Radius | `6px` |
| Border | neutral or slightly accent-aware if desired |
| Shadow | overlay |
| Header padding | `14px 16px 12px` |
| Body padding | `14px 16px` |
| Footer padding | `10px 16px` |

### Dialog guidance

- Dialogs should feel raised and crisp, not oversized.
- Footer action areas can use subtle shell surface contrast.
- Titles should be concise and visually calm.
- Chrome inside dialogs should stay lighter than the content itself.

## Tables And Lists

### Shell table specs

| Token area | Guidance |
|---|---|
| Header height | `32px` |
| Row height | `34px` |
| Header text | `11px`, `600`, optional uppercase |
| Cell text | `12px` |
| Border | subtle neutral |
| Hover | subtle warm or neutral fill |
| Selected row | soft contextual or selected fill depending on use case |

### Table guidance

- Numeric columns should use tabular numerals.
- Use subtle row backgrounds and borders.
- Do not rely on thick borders for structure.
- Use soft fill plus a leading accent marker if stronger emphasis is needed.

### Shell table guidance

Shell tables and lists should look like product chrome, not full analytical grids.

- headers should be quiet and compact
- row hover should be subtle
- selected states should use soft fills, not loud saturation
- embedded list/table controls should still follow shell control hierarchy

## Badges And Alerts

### Badges

- `10px`, `600`
- compact padding
- prefer tinted semantic fills instead of heavy solid fills

### Alerts

- use semantic color families only
- keep surfaces tinted and readable
- use stronger left border or border color for emphasis

## Widget Mapping

The shell should apply tokens to widgets using a role-based approach.

| Widget | Main token families |
|---|---|
| Primary button | primary + text-on-primary |
| Secondary button | neutral control tokens |
| Default button | neutral control tokens |
| Danger button | danger family |
| Input/select | surface-default + border-default + text-default + primary focus |
| Dropdown/menu | surface-default + border-default + surface-hover |
| Top nav | surface-subtle + text-muted + primary active indicator |
| Tabs | surface-subtle + text-muted + primary active indicator |
| Card | surface-default + border-default + low elevation |
| Dialog | surface-default + border-default + overlay elevation |
| Toolbar | surface-subtle + border-default |
| Shell table | surface-default + surface-subtle + border-default + contextual/selected row states |
| Badge | semantic soft families |
| Alert | semantic families |

## Implementation Note

This spec defines shell intent and target behavior. For execution details such as implementation phases, codebase mapping, themeability rules, current file touchpoints, and rollout order, use [shell-implementation-roadmap.md](E:\home\dev\github\lookfeel\shell-implementation-roadmap.md).

## Design Intent Summary

The shell should modernize StyleBI through:

- stronger neutral discipline
- one routine accent color
- consistent sizing
- restrained radius and elevation
- neutral control treatments
- clearer token-to-widget mapping
- explicit Figma-inspired surface and control hierarchy
- less boxed chrome and more canvas-plus-separator composition

This creates a shell that is modern and efficient without fighting worksheet or future visualization layers.
