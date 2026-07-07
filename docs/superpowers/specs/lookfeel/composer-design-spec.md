# StyleBI Composer Design Spec

## Purpose

This document defines the broader Composer design layer for StyleBI.

It covers:

- Composer's role between shell and visualization
- the surface model for shared authoring chrome and editing surfaces
- the shared authoring-state grammar
- usage boundaries for worksheet, viewsheet, and wizard surfaces
- behavioral rules for when Composer should inherit shell versus introduce authoring meaning

This spec is meant to work alongside:

- [theme-strategy-overview.md](E:\home\dev\github\lookfeel\theme-strategy-overview.md)
- [shell-design-spec.md](E:\home\dev\github\lookfeel\specs\shell-design-spec.md)
- [composer-palette-spec.md](E:\home\dev\github\lookfeel\specs\composer-palette-spec.md)
- [composer-implementation-roadmap.md](E:\home\dev\github\lookfeel\specs\composer-implementation-roadmap.md)

Visual references:

- [composer-widget-examples-appendix.html](E:\home\dev\github\lookfeel\specs\composer-widget-examples-appendix.html)
- [composer-palette-swatches.html](E:\home\dev\github\lookfeel\specs\composer-palette-swatches.html)

## Scope and Architecture

Composer is the product's authoring environment.

It sits between the shell and final visualization output.

It is not:

- a replacement for the shell
- a generic product-wide theme layer
- the visualization system

Composer should be understood as the authoring-state layer that sits on top of shared shell foundations.

## Relationship To Shell And Visualization

Composer should inherit from shell:

- shared surfaces
- typography
- spacing
- radius
- borders
- focus treatment
- routine control hierarchy
- routine primary action emphasis
- standard semantic success, warning, danger, and info families

Composer should add:

- shared authoring-state language
- editing-surface emphasis
- workflow-specific context and selection meaning
- narrower worksheet-only extensions where shared Composer states are not precise enough

Visualization should continue to own:

- analytical meaning
- chart palettes
- data ramps
- widget-specific internal density and states

## Composer Design Principles

- Composer should feel structurally aligned with the shell.
- Shared chrome should stay calm and mostly neutral.
- Authoring states should appear only where the interface is expressing editing meaning.
- Worksheet should be the richest Composer state user, but still not become a separate theme.
- Viewsheet and wizard should use a narrower subset of Composer states than worksheet.
- Worksheet-specific states should remain local to worksheet-specific meaning.

## How To Use This Spec

Use this document to understand:

- when a surface should stay shell-driven
- when Composer state should appear
- how the Composer state grammar should behave
- how worksheet, viewsheet, and wizard differ in state usage

Use [composer-palette-spec.md](E:\home\dev\github\lookfeel\specs\composer-palette-spec.md) for token values and palette families.

Use [composer-implementation-roadmap.md](E:\home\dev\github\lookfeel\specs\composer-implementation-roadmap.md) for file targets, token rollout, themeability steps, and validation.

## Composer Surface Model

Composer should use two visual layers:

1. Shared Composer chrome
2. Authoring surfaces

### Shared Composer Chrome

Shared Composer chrome is the shell-aligned UI around the editor.

It includes:

- top toolbars
- pane rails
- pane tabs
- inspectors
- dialogs
- forms
- property panes
- slide-out chrome
- repository and library framing

Shared Composer chrome should:

- consume shell tokens first
- remain mostly neutral
- use shell subtle and default surfaces rather than custom tinted backgrounds
- preserve shell button, input, tab, dialog, and toolbar behavior

Shared Composer chrome should not:

- use worksheet-only tokens
- use selected-object styling as generic chrome
- use Composer context as a passive background treatment

### Authoring Surfaces

Authoring surfaces are the editing surfaces where users manipulate structure, layout, or workflow state.

They include:

- worksheet graph and schema editors
- worksheet detail and binding states
- viewsheet editing and object targeting
- wizard selection and recommendation flows
- object and binding editors when they are expressing edit state rather than ordinary form structure

These surfaces may use Composer-specific state tokens where shell alone is not expressive enough.

## Composer State Grammar

Composer should use a small shared state vocabulary:

- `default`
  - neutral authoring surface
- `context`
  - related, adjacent, or in-focus context
- `selected`
  - explicit active selection or chosen target
- `primary`
  - key authoring action or emphasis
- `dimmed`
  - inactive or de-emphasized state

### Default

Default authoring surfaces should still start from shell surfaces and text roles.

Use default when the UI is structural or readable but not communicating special editing meaning.

### Context

Context is for related or in-focus state.

Use it for:

- related nodes
- adjacent workflow state
- helper previews
- in-focus but not actively selected surfaces

Do not use context as a generic hover substitute or as a replacement for selection.

### Selected

Selected is for explicit authoring selection.

Use it for:

- selected nodes
- selected objects
- chosen wizard items
- active edit targets
- handles or surfaces that need clear active targeting

Selected should read more explicit than context and should remain consistent across Composer modes.

### Primary

Primary authoring emphasis should stay aligned with shell primary.

Use it for:

- key authoring affordances
- high-value guided actions
- deliberate workflow emphasis inside editing surfaces

Do not let Composer primary replace normal shell primary buttons or become a second routine accent family.

### Dimmed

Dimmed is for inactive or de-emphasized authoring state.

Use it for:

- unavailable edit targets
- muted graph elements
- inactive side content inside editing flows

Dimmed should lower emphasis without looking broken or unreadable.

## Surface Guidance By Area

### Worksheet

Worksheet uses the broadest part of the Composer state grammar.

It should:

- start from shell surfaces for graph nodes and structural panels
- use `context` for related or connected state
- use `selected` for active nodes, headers, and explicit targets
- use `primary` for key add/edit emphasis
- use `dimmed` for inactive graph elements and de-emphasized editing state

Worksheet may retain narrower local extensions for:

- schema compatibility
- graph connections
- detail-row rhythms and local table states

Those narrower states should remain worksheet-only and should not leak into shared chrome.

### Viewsheet

Viewsheet should use a narrower Composer subset than worksheet.

It should:

- inherit shell surfaces first for side panels and layout chrome
- use `selected` for chosen objects, handles, and active edit targets
- use `primary` for key add/edit affordances
- use `dimmed` for inactive or unavailable objects
- use `context` only when a real related-state meaning exists

Viewsheet should not inherit worksheet graph or schema colors.

### Wizard

Wizard should feel like guided authoring built on Composer foundations.

It should:

- inherit shell structure for framing, forms, dialogs, and ordinary controls
- use `selected` for chosen items and active preview targets
- use `primary` for recommendations or key guided actions
- use `context` sparingly for related helper previews
- use shell semantic colors for standard validation and feedback

Wizard should use fewer Composer states than worksheet and should stay visually controlled.

## Shared Chrome Rules

The following should stay shell-driven unless they are directly carrying authoring meaning:

- dialogs and modal footers
- standard form controls
- generic buttons and icon buttons
- generic tabs and nav strips
- passive side-panel chrome
- standard toolbars and toolbar groups

Composer tokens should not be used just because a surface appears inside Composer.

## Worksheet Extension Boundary

Worksheet-specific states should stay inside:

- schema compatibility visuals
- graph connections and assemblies
- worksheet detail rows
- worksheet-only authoring states with narrower meaning than the shared Composer grammar

The following should not use worksheet-only tokens:

- shared Composer side panes
- pane tabs and nav
- generic dialogs
- shared forms and controls
- viewsheet selection states
- wizard recommendations or step emphasis

## State Distribution Rules

Composer should follow this distribution model:

- shared chrome: shell first
- worksheet: full Composer state grammar plus narrow worksheet extensions
- viewsheet: mostly selected, primary, and dimmed
- wizard: mostly selected and primary, with occasional context

If a Composer surface starts to require many local colors outside this model, treat that as a design smell and re-evaluate whether the meaning belongs to shell, Composer, worksheet-only, or visualization.

## Implementation Note

The first implementation pass should focus on:

- token alignment
- shell-first shared chrome adoption
- shared authoring-state adoption
- worksheet extension containment

It should not start by redesigning every Composer component independently.

## Design Intent Summary

Composer should read as a shell-aligned authoring environment with a distinct workflow-state language.

The experience should feel:

- structurally calm like the shell
- more stateful than the shell where editing meaning requires it
- narrower and more disciplined than a full visualization palette

## Related Specs

- [theme-strategy-overview.md](E:\home\dev\github\lookfeel\specs\theme-strategy-overview.md)
- [shell-design-spec.md](E:\home\dev\github\lookfeel\specs\shell-design-spec.md)
- [composer-palette-spec.md](E:\home\dev\github\lookfeel\specs\composer-palette-spec.md)
- [composer-implementation-roadmap.md](E:\home\dev\github\lookfeel\specs\composer-implementation-roadmap.md)
