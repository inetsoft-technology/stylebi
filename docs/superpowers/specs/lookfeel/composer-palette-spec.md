# StyleBI Composer Palette Spec

## Purpose

This document defines the recommended palette model for Composer in StyleBI.

Composer is an authoring environment, not just shell chrome and not final visualization output. It includes:

- worksheet data manipulation
- viewsheet layout and design
- wizard-guided visualization creation
- shared authoring chrome such as toolbars, panes, tabs, dialogs, forms, and inspectors

The goal is to give Composer a coherent visual grammar while sharing as much as possible with the shell palette.

## Position In The Palette Architecture

StyleBI should use three palette layers:

1. Shell palette
2. Composer palette
3. Visualization palette

The Composer palette should:

- inherit shell surfaces, neutrals, typography, borders, radius, and focus treatment
- inherit shell semantic families for standard success, warning, danger, and info meaning
- reuse shell primary for routine actions and key emphasis
- add only the extra state language needed for authoring
- remain quieter than the visualization palette

## Relationship To Shell

The shell palette is still the shared foundation for product UI.

Inside Composer:

- shared chrome should stay shell-driven
- authoring-specific states should come from the Composer palette

Shell should continue to own:

- global and shared chrome surfaces
- standard buttons, inputs, tabs, dialogs, and toolbar treatments
- neutral structure and routine product hierarchy
- default canvas, surface, border, and text roles where Composer does not need distinct meaning

Composer should add only the states needed for authoring workflows.

## Composer Surface Model

Composer should use two visual layers:

### Shared Composer Chrome

This is shell-aligned UI around the editor.

It includes:

- top toolbars
- left and right panes
- pane tabs
- inspectors and sidebars
- dialogs
- forms
- script and table-style editing chrome

These surfaces should remain mostly neutral and should consume shell tokens first.

### Authoring Surfaces

These are the editing surfaces where users manipulate structure, layout, or workflow state.

They include:

- worksheet graph and schema editors
- viewsheet design canvas states
- wizard selection and preview flows
- binding and object editing states

These surfaces can use Composer-specific state tokens where shell alone is not expressive enough.

## Composer State Grammar

Composer should use a small, shared state vocabulary:

- default = neutral authoring surface
- context = related or in-focus context
- selected = explicit active selection
- primary = key action or key emphasis
- dimmed = inactive or de-emphasized state

This vocabulary should be shared across Composer modes as much as possible.

## Proposed Tokens

Composer should not redefine shell foundations unless the meaning is different.

Where Composer introduces alias tokens for values that already exist in shell, those aliases should resolve to the shell palette values rather than introducing a second near-match.

Use shell tokens first for:

- canvas and background surfaces
- raised and subtle surfaces
- default borders and separators
- default and muted text
- focus behavior
- routine button, tab, dialog, and form styling

Composer-specific tokens should be added only for authoring-state meaning that shell does not already express.

## Shell Alignment Rules

- Shared Composer chrome should consume the shell palette directly for `surface-canvas`, `surface-default`, `surface-subtle`, `surface-hover`, `border-default`, `border-strong`, `text-strong`, `text-default`, `text-muted`, and `text-subtle`.
- Composer routine actions should use the shell primary family as defined in [shell-palette-spec.md](E:\home\dev\github\lookfeel\specs\shell-palette-spec.md).
- Composer should keep using shell semantic tokens for standard success, warning, danger, and info meaning instead of creating parallel Composer semantic families.
- Composer should not introduce additional routine accent families that compete with shell primary inside shared chrome.

### Context Family

This family is for related, adjacent, or in-focus context. It is not the same as direct selection.

| Token | Value | Use |
|---|---|---|
| `--inet-composer-context-bg` | `#FEF8EE` | related surface or in-focus context |
| `--inet-composer-context-border` | `#F3DFC2` | contextual border |
| `--inet-composer-context-text` | `#8C5510` | text on contextual surfaces |

### Selected Family

This family is for explicit selection.

| Token | Value | Use |
|---|---|---|
| `--inet-composer-selected-bg` | `#DDF1F5` | selected surface |
| `--inet-composer-selected-border` | `#BFDDE5` | selected border |
| `--inet-composer-selected-text` | `#123C44` | text on selected surfaces |
| `--inet-composer-selected-ring` | `rgba(167, 215, 227, 0.35)` | selected halo or emphasis ring |

### Primary / Action Family

This family should stay aligned with shell primary and should be introduced only where authoring surfaces need their own aliases.

| Token | Value | Use |
|---|---|---|
| `--inet-composer-primary-accent` | `#E58A2A` | key authoring affordance; alias shell `primary` |
| `--inet-composer-primary-hover` | `#C96F12` | primary hover state; alias shell `primary-hover` |
| `--inet-composer-primary-text` | `#4A2500` | text on primary surfaces; alias shell `primary-text` |
| `--inet-composer-primary-soft` | `#F6E2C8` | soft primary background; alias shell `primary-soft` |

### Dimmed / Inactive Family

| Token | Value | Use |
|---|---|---|
| `--inet-composer-dimmed-bg` | `#FBFAF7` | dimmed surface |
| `--inet-composer-dimmed-border` | `#E6E2D9` | dimmed border |
| `--inet-composer-dimmed-text` | `#B8B3AA` | dimmed text |

## Interaction Mapping

### Shared Composer Chrome

- toolbars, panes, tabs, forms, and dialogs should use shell tokens first
- Composer tokens should appear only when the UI communicates authoring state rather than normal shell structure

### Worksheet

Worksheet uses the broadest part of the Composer state grammar.

- default graph nodes use shell surface and text roles first
- related or connected nodes use the context family
- selected nodes use the selected family
- schema compatibility and workflow-specific cues should use worksheet extensions where shell semantics are not precise enough
- primary actions use the primary family
- dimmed graph elements use the dimmed family

### Viewsheet

Viewsheet should use a narrower subset of Composer states.

- canvas and side editing surfaces should inherit shell surfaces first
- selected objects, handles, and active edit targets use the selected family
- add/edit actions use the primary family
- inactive or unavailable objects use the dimmed family
- use context family only when there is a real related-state meaning, not as a generic highlight

### Wizard

Wizard should feel like guided authoring built on Composer foundations.

- step structure and framing should inherit shell surfaces first
- chosen items and active steps use the selected family
- recommendation or key action emphasis uses the primary family
- helper previews and soft emphasis can use the context family sparingly
- standard validation states should inherit shell semantic tokens

## Worksheet-Only Extensions

Most Composer surfaces should use the shared Composer palette. Worksheet needs a few extra tokens because it has graph- and schema-specific visuals that do not apply broadly elsewhere.

### Compatibility And Workflow Highlight States

Use shell semantic tokens for general warning, error, success, and info states.

Worksheet-specific tokens are only needed where authoring meaning is more specific than shell semantics.

| Token | Value | Use |
|---|---|---|
| `--inet-ws-schema-connected-bg` | `#E2F3E8` | connected schema column |
| `--inet-ws-schema-compatible-bg` | `#DDF1F5` | compatible schema column |
| `--inet-ws-schema-incompatible-bg` | `#F6E2C8` | incompatible schema column |
| `--inet-ws-schema-highlight-bg` | `#F7F3EA` | hover or highlight surface |

### Connections

| Token | Value | Use |
|---|---|---|
| `--inet-ws-connection-default` | `#5E5D57` | default connection line |
| `--inet-ws-connection-muted` | `#B8B3AA` | de-emphasized connection line |
| `--inet-ws-connection-warning` | `#EEA555` | warning connection |
| `--inet-ws-connection-schema` | `#BDBDBD` | schema editor connection |

### Worksheet Table Detail States

| Token | Value | Use |
|---|---|---|
| `--inet-ws-row-odd-bg` | `#FFFFFF` | odd detail row |
| `--inet-ws-row-even-bg` | `#F7F6F2` | even detail row |
| `--inet-ws-row-selected-bg` | `#EAF6F8` | selected detail row |
| `--inet-ws-row-hover-bg` | `#F7F3EA` | detail row hover |

These should be treated as worksheet extensions of the Composer palette, not as a separate full palette system.

## Token Mapping Guidance

The intended direction is:

- shell tokens remain the foundation for shared chrome and default surfaces
- new `--inet-composer-*` tokens express shared authoring states
- shell semantic tokens remain the default source for success, warning, error, and info
- `--inet-ws-*` tokens remain only where worksheet has truly unique needs

Where older worksheet tokens already exist, they can be mapped gradually onto the Composer model rather than replaced in one pass.

## Design Intent Summary

The Composer palette should make authoring feel coherent across worksheet, viewsheet, and wizard without fragmenting into too many independent color systems.

- shell provides structure
- Composer provides authoring state
- visualization provides analytical meaning

The shared Composer palette should do most of the work, with worksheet-specific extensions only where necessary.

## Related Specs

- [theme-strategy-overview.md](E:\home\dev\github\lookfeel\theme-strategy-overview.md)
- [shell-palette-spec.md](E:\home\dev\github\lookfeel\specs\shell-palette-spec.md)
- [shell-design-spec.md](E:\home\dev\github\lookfeel\shell-design-spec.md)
- [composer-implementation-roadmap.md](E:\home\dev\github\lookfeel\composer-implementation-roadmap.md)
