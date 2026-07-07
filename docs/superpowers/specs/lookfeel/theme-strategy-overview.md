# StyleBI Theme Strategy Overview

## Purpose

This document captures the high-level design strategy for the StyleBI theme system.

It is intentionally architectural and directional. It does not define detailed widget rules. Those live in the focused shell and visualization design specs.

## Figma-Inspired Direction

In this strategy, Figma-like does not mean copying Figma literally.

It means the shell should emphasize:

- neutral-first hierarchy rather than many competing hues
- minimal persistent chrome around the main work area
- restrained radius and light, crisp elevation
- compact, precise controls with consistent spacing
- emphasis through typography, separators, and interaction state rather than decorative framing
- canvas-plus-rails composition instead of deeply nested card stacks
- one routine accent color rather than multiple competing brand accents

The goal is a shell that feels modern, calm, and highly functional without competing with authoring states or visualization meaning.

## Core Goal

StyleBI should feel:

- modern rather than legacy BI
- quiet and structured in the shell
- analytically powerful in visualization surfaces
- cohesive across shell, worksheet, and future visualization layers

## Core Design Model

The product should be understood as layered visual systems:

1. Shell - foundation and direclty theming portal and composer
2. Composer - authoring layer for shared Composer chrome and specialized editing surfaces
3. Visualization - visualiation components of StyleBI

Each layer should have its own responsibilities while sharing a common token foundation.

## Layer Responsibilities

### Shell

The shell owns:

- product structure
- global navigation
- neutral surfaces
- control hierarchy
- one routine accent color
- shared typography, radius, focus, and baseline spacing

In code terms, the shell should be understood as the shared application UI foundation rather than as a single app area.

It applies most clearly to:

- Portal product chrome
- EM product chrome at the principle level
- shared Composer chrome such as dialogs, tabs, panels, forms, toolbars, and common controls

The shell should be quiet, efficient, and Figma-inspired.

Portal and Composer are the primary implementation targets for the current shell spec. EM should align to the same shell principles through its own theming layer, and that EM-specific alignment should be documented in a future spec.

### Composer

The Composer layer is the product's authoring environment.

It sits between shell and visualization.

It is not the same as the shell and not the same as final data visualization.

It owns:

- shared authoring-state language
- worksheet and workflow editing states
- viewsheet editing states
- wizard and guided-creation states
- contextual, selected, dimmed, and authoring-emphasis states inside editing surfaces

Composer should inherit shell foundations where appropriate while adding a richer authoring-state model on top.

Inside Composer:

- shared chrome such as dialogs, tabs, panels, forms, toolbars, and inspectors should follow shell rules first
- worksheet and workflow surfaces should use narrower Composer extensions where graph, schema, connection, or detail-row meaning is needed
- viewsheet and wizard surfaces should use the shared Composer state model more narrowly where appropriate

### Visualization

Visualization owns:

- BI density
- widget chrome inside data surfaces
- table/grid behavior
- chart palettes
- analytical meaning
- visualization-specific state tokens

It should be purpose-built for BI output, not just a compact app UI.

## Bootstrap Position

Bootstrap should remain infrastructure, not the design system.

Use it for:

- layout
- utilities where useful
- base component plumbing

Do not let it define:

- shell visual language
- control density
- visualization density
- widget-state behavior

## Color Strategy

### Shell

- routine shell chrome should be mostly neutral
- the shell should have one routine accent color
- semantic colors should remain available but used with restraint

### Composer

- Composer should use shell neutrals plus Composer authoring-state families
- warm contextual and cool selected families are appropriate for editing and workflow surfaces
- worksheet-specific extensions should remain narrower than the overall Composer palette

### Visualization

- vivid color belongs primarily to visualization
- analytical colors should communicate data meaning, not decorate shell chrome
- chart palettes, ramps, conditional formatting, and data emphasis should remain visualization-owned
- note: chart marks and in-graph chrome are **server-rendered** and shared by live view and all
  export formats, so their color is not driven by browser CSS tokens — see the Rendering And Theming
  Architecture section of [visualization-design-spec.md](visualization-design-spec.md)

## Density Strategy

Density should be treated as a visualization-specific concern, not a product-wide design theme.

- The shell should simply follow good web application sizing and spacing discipline.
- Composer should follow authoring- and workflow-specific interaction needs.
- Visualization should explicitly own BI density strategy.

## Coordination Principle

The shell and visualization should coordinate, not compete.

- shell owns structure
- visualization owns analytical meaning
- Composer owns authoring state language

Shared foundations should include:

- typography
- text colors
- neutral surfaces
- border colors
- radius
- focus treatment
- primary action accent

Layer-specific tokens should be allowed where needed.

Shared shell controls should also follow a strict emphasis hierarchy:

- one primary action per visible scope
- secondary actions used sparingly
- most local, toolbar, subsection, and helper actions treated as ghost controls

This keeps the shell quiet and legible while still making the main action obvious.

## Shell-To-Visualization Token Contract

Visualization should inherit from shell:

- text colors
- neutral surfaces
- default borders
- radius
- focus ring
- primary action color
- base font family

Visualization should override for:

- density
- widget-specific spacing
- row heights
- compact control sizes inside widgets

Visualization should own:

- chart palettes
- analytical ramps
- conditional formatting
- widget-specific state tokens

## High-Level Design Direction

The target product experience should read as:

- Figma-like shell
- Composer authoring environment with a distinct workflow state model
- Airtable/Tableau/Power BI-inspired visualization surfaces

## Related Specs

- [shell-design-spec.md](E:\home\dev\github\lookfeel\shell-design-spec.md)
- [shell-implementation-roadmap.md](E:\home\dev\github\lookfeel\shell-implementation-roadmap.md)
- [composer-design-spec.md](E:\home\dev\github\lookfeel\specs\composer-design-spec.md)
- [composer-palette-spec.md](E:\home\dev\github\lookfeel\composer-palette-spec.md)
- [composer-implementation-roadmap.md](E:\home\dev\github\lookfeel\composer-implementation-roadmap.md)
- [visualization-design-spec.md](E:\home\dev\github\lookfeel\visualization-design-spec.md)
- [visualization-implementation-roadmap.md](E:\home\dev\github\lookfeel\visualization-implementation-roadmap.md)
