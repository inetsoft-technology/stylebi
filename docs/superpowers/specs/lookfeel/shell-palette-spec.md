# StyleBI Shell Palette Spec

## Purpose

This document defines the recommended shell palette for StyleBI and maps it to the current token system.

The shell palette should:

- provide product-wide visual structure
- establish one routine accent color
- rely primarily on neutrals for hierarchy
- preserve a full semantic palette without letting it dominate routine chrome
- remain compatible with the existing `--inet-*` token system

This document does not define the Composer authoring palette or the future visualization palette. Those are separate layers.

## Related Specs

- [shell-design-spec.md](E:\home\dev\github\lookfeel\shell-design-spec.md)
- [composer-palette-spec.md](E:\home\dev\github\lookfeel\composer-palette-spec.md)
- [palette-coordination-recommendations.md](E:\home\dev\github\lookfeel\palette-coordination-recommendations.md)

## Shell Palette Principles

- The shell should feel mostly neutral.
- The shell should have one routine accent: primary.
- Secondary and other support hues should still exist, but with reduced use.
- Success, warning, danger, and info should be semantic-only by default.
- Routine controls such as default and secondary buttons should be driven by neutral control tokens, not by the secondary hue.

## Proposed Shell Palette

## Neutral Scale

These colors should do most of the shell work.

| New token | Value | Intended use |
|---|---|---|
| `surface-canvas` | `#F8F7F4` | page/canvas background |
| `surface-default` | `#FFFFFF` | cards, dialogs, raised panels, inputs |
| `surface-subtle` | `#F1EFEA` | tabs, toolbars, side areas, subtle containers |
| `surface-hover` | `#ECE9E2` | hover fills and neutral active surfaces |
| `border-default` | `#D9D5CC` | normal borders and dividers |
| `border-strong` | `#C8C2B7` | stronger separators and emphasis borders |
| `text-strong` | `#1F1F1B` | strong headings and emphasis |
| `text-default` | `#35342F` | primary body and control text |
| `text-muted` | `#6A685F` | secondary labels and subdued text |
| `text-subtle` | `#99958C` | placeholders, low-priority meta text |

## Primary Family

This is the only routine shell accent.

| New token | Value | Intended use |
|---|---|---|
| `primary` | `#E58A2A` | primary actions and key emphasis |
| `primary-hover` | `#C96F12` | hover state for primary actions |
| `primary-active` | `#A95607` | active/pressed state |
| `primary-soft` | `#F6E2C8` | soft primary surface |
| `primary-text` | `#4A2500` | text on soft primary surfaces |

## Support Families

These families exist for limited informational, structural, or categorical meaning in shell-adjacent areas such as Data Model.

They should not behave like additional routine shell accents.

| New token | Value | Intended use |
|---|---|---|
| `secondary` | `#7455A8` | constrained violet structural/categorical emphasis |
| `secondary-hover` | `#624494` | hover state |
| `secondary-active` | `#4E3378` | active state |
| `secondary-soft` | `#EDE8F5` | soft violet support surface |
| `secondary-text` | `#3D2070` | text on secondary-soft surfaces |
| `third` | `#B54B6E` | constrained rose structural/categorical emphasis |
| `third-soft` | `#F5DCEA` | soft rose support surface |
| `third-text` | `#6B1F3A` | text on third-soft surfaces |
| `fourth` | `#1D8A86` | constrained teal structural/categorical emphasis |
| `fourth-soft` | `#D5EDEC` | soft teal support surface |
| `fourth-text` | `#0F4E4C` | text on fourth-soft surfaces |

## Semantic Families

These should be reserved for meaning-bearing states.

| New token | Value | Intended use |
|---|---|---|
| `success` | `#2E8B57` | success states |
| `success-soft` | `#E2F3E8` | soft success surfaces |
| `success-text` | `#1D5B39` | text on success-soft surfaces |
| `warning` | `#B7791F` | warning states |
| `warning-soft` | `#F8E8CC` | soft warning surfaces |
| `warning-text` | `#7A4E10` | text on warning-soft surfaces |
| `danger` | `#C84C4C` | destructive/error states |
| `danger-soft` | `#F7DEDE` | soft danger surfaces |
| `danger-text` | `#7F2E2E` | text on danger-soft surfaces |
| `info` | `#3E7FC4` | informational states |
| `info-soft` | `#E4EFFA` | soft info surfaces |
| `info-text` | `#1F548A` | text on info-soft surfaces |

## Usage Levels

| Family | Usage level | Notes |
|---|---|---|
| Neutrals | Heavy use | Primary source of hierarchy |
| Primary | Moderate, intentional | Only routine shell accent |
| Secondary / Third / Fourth | Limited | Support families for recurring informational or structural meaning only |
| Success / Warning / Danger / Info | Semantic only | No routine chrome usage |

## Support Family Rules

- `secondary`, `third`, and `fourth` are constrained support families, not routine shell accents.
- They should not be used for general chrome, arbitrary emphasis, or primary action styling.
- Prefer `*-soft` plus matching `*-text` tokens over saturated fills in shell surfaces.
- Use them only where the same recurring meaning appears across a product area, such as Data Model categorization.

## Control Strategy

The biggest shell behavior change should be in routine controls.

### Current problem

The current token system ties default and secondary button tokens directly to the secondary color family. This makes the shell feel like it has two competing accents.

### Recommendation

Introduce neutral control tokens and map routine non-primary controls to them.

Use two neutral control behaviors:

- `default` should be a ghost control
  - transparent at rest
  - neutral text/icon color
  - hover fill only
  - no persistent border unless a specific product area needs a stronger local affordance
- `secondary` should be the neutral outline control
  - transparent or quiet neutral fill at rest
  - neutral border
  - neutral hover fill

This preserves a single-accent shell model similar to Figma-style product chrome, where one primary CTA carries routine emphasis and other actions stay structurally quiet.

### Recommended neutral control tokens

| New token | Suggested value | Intended use |
|---|---|---|
| `--inet-ui-neutral-color` | `#6A685F` | neutral control text/icon color |
| `--inet-ui-neutral-border-color` | `#D9D5CC` | neutral control borders |
| `--inet-ui-neutral-hover-bg-color` | `#ECE9E2` | hover background for neutral controls |
| `--inet-ui-neutral-hover-color` | `#1F1F1B` | hover text/icon color |

## Migration Strategy

To preserve compatibility:

1. Keep the existing `--inet-*` tokens.
2. Introduce clearer shell aliases where needed.
3. Re-point old tokens to the new shell intent.
4. Add missing aliases rather than doing a destructive rename.

## Full Migration Table

### Neutral And Surface Tokens

| New shell token | Current token | Status | Recommendation | Notes |
|---|---|---|---|---|
| `surface-canvas` | `--inet-main-panel-bg-color` | redefine | map to `#F8F7F4` | global shell canvas |
| `surface-canvas` | `--inet-portal-main-panel-bg-color` | redefine | map to `surface-canvas` | portal surface |
| `surface-canvas` | `--inet-composer-main-panel-bg-color` | redefine | map to `surface-canvas` | composer surface |
| `surface-default` | `--inet-card-bg-color` | redefine | map to `#FFFFFF` | raised shell surface |
| `surface-default` | `--inet-dialog-bg-color` | redefine | map to `#FFFFFF` | dialog fill |
| `surface-default` | `--inet-dropdown-bg-color` | redefine | map to `#FFFFFF` | dropdown fill |
| `surface-default` | `--inet-input-bg-color` | redefine | map to `#FFFFFF` | form fill |
| `surface-subtle` | `--inet-toolbar-bg-color` | redefine | map to `#F1EFEA` | toolbar/background strip |
| `surface-subtle` | `--inet-nav-tabs-bg-color` | redefine | map to `#F1EFEA` | tabs and segmented areas |
| `surface-subtle` | `--inet-portal-side-panel-bg-color` | redefine | map to `#F1EFEA` | portal side surfaces |
| `surface-subtle` | `--inet-composer-side-panel-bg-color` | redefine | map to `#F1EFEA` | composer side surfaces |
| `surface-hover` | `--inet-hover-secondary-bg-color` | redefine | keep token, change semantics to neutral hover surface | current name is misleading |
| `border-default` | `--inet-default-border-color` | redefine | map to `#D9D5CC` | default border |
| `border-default` | `--inet-input-border-color` | redefine | map to `border-default` | form border |
| `border-default` | `--inet-table-border-color` | redefine | map to `border-default` | table border |
| `border-strong` | none | add | add new alias token | missing today |
| `text-strong` | `--inet-dark-color` | redefine | map to `#1F1F1B` | strong text |
| `text-default` | `--inet-text-color` | redefine | map to `#35342F` | primary shell text |
| `text-muted` | none | add | add new alias token | needed for secondary labels |
| `text-subtle` | none | add | add new alias token | needed for placeholders/meta text |

### Primary Family

| New shell token | Current token | Status | Recommendation | Notes |
|---|---|---|---|---|
| `primary` | `--inet-primary-color` | redefine | map to `#E58A2A` | core accent |
| `primary-hover` | `--inet-primary-color-dark` | redefine | map to `#C96F12` | accent hover |
| `primary-active` | none | add | add new alias token | active/pressed distinction |
| `primary-soft` | `--inet-primary-color-light` | redefine | map to `#F6E2C8` | soft primary surface |
| `primary-text` | none | add | add new alias token | text on soft primary surfaces |
| `primary` | `--inet-navbar-selected-border-color` | redefine | map to `primary` | selected shell nav indicator |
| `primary-soft` | `--inet-navbar-home-bg-color` | redefine | likely map to soft primary or primary depending on desired emphasis | home affordance |
| `primary-hover` | `--inet-link-color` | redefine | optional, or keep separate link tuning | decide if links remain amber family |

### Support Families

| New shell token | Current token | Status | Recommendation | Notes |
|---|---|---|---|---|
| `secondary` | `--inet-secondary-color` | redefine | map to `#7455A8` | constrained violet support family |
| `secondary-hover` | `--inet-secondary-color-dark` | redefine | map to `#624494` | structural hover |
| `secondary-active` | none | add | add new alias token `#4E3378` | structural active state |
| `secondary-soft` | `--inet-secondary-color-light` | redefine | map to `#EDE8F5` | violet soft fill |
| `secondary-text` | none | add | add new alias token `#3D2070` | text on secondary-soft surfaces |
| `third` | `--inet-third-color` | redefine | map to `#B54B6E` | constrained rose support family |
| `third-soft` | `--inet-third-color-light` | redefine | map to `#F5DCEA` | rose soft fill |
| `third-text` | none | add | add new alias token `#6B1F3A` | text on third-soft surfaces |
| `fourth` | `--inet-fourth-color` | redefine | map to `#1D8A86` | constrained teal support family |
| `fourth-soft` | `--inet-fourth-color-light` | redefine | map to `#D5EDEC` | teal soft fill |
| `fourth-text` | none | add | add new alias token `#0F4E4C` | text on fourth-soft surfaces |

### Semantic Families

| New shell token | Current token | Status | Recommendation | Notes |
|---|---|---|---|---|
| `success` | `--inet-success-color` | redefine | map to `#2E8B57` | semantic only |
| `success-soft` | `--inet-success-color-light` | redefine | map to `#E2F3E8` | semantic only |
| `success-text` | `--inet-success-color-dark` | redefine | map to `#1D5B39` | semantic only |
| `warning` | `--inet-warning-color` | redefine | map to `#B7791F` | semantic only |
| `warning-soft` | `--inet-warning-color-light` | redefine | map to `#F8E8CC` | semantic only |
| `warning-text` | `--inet-warning-color-dark` | redefine | map to `#7A4E10` | semantic only |
| `danger` | `--inet-danger-color` | redefine | map to `#C84C4C` | semantic only |
| `danger-soft` | `--inet-danger-color-light` | redefine | map to `#F7DEDE` | semantic only |
| `danger-text` | `--inet-danger-color-dark` | redefine | map to `#7F2E2E` | semantic only |
| `info` | `--inet-info-color` | redefine | map to `#3E7FC4` | semantic only |
| `info-soft` | `--inet-info-color-light` | redefine | map to `#E4EFFA` | semantic only |
| `info-text` | `--inet-info-color-dark` | redefine | map to `#1F548A` | semantic only |

### Neutral Control Migration

These are the most important behavior changes.

| Current token | Current source | Status | New source | Notes |
|---|---|---|---|---|
| `--inet-button-secondary-bg-color` | `--inet-secondary-color` | redefine | `transparent` or neutral soft surface | no solid teal secondary button by default |
| `--inet-button-secondary-text-color` | white | redefine | `--inet-ui-neutral-color` | neutral secondary text |
| `--inet-button-secondary-border-color` | `--inet-secondary-color` | redefine | `--inet-ui-neutral-border-color` | neutral outline border |
| `--inet-button-secondary-hover-bg-color` | `--inet-secondary-color-dark` | redefine | `--inet-ui-neutral-hover-bg-color` | neutral hover background |
| `--inet-button-secondary-hover-text-color` | white | redefine | `--inet-ui-neutral-hover-color` | neutral hover text |
| `--inet-button-secondary-hover-border-color` | `--inet-secondary-color-dark` | redefine | `--inet-ui-neutral-border-color` | neutral hover border |
| `--inet-button-default-bg-color` | transparent | keep/redefine | transparent | remain ghost-like |
| `--inet-button-default-text-color` | `--inet-secondary-color` | redefine | `--inet-ui-neutral-color` | neutral default control |
| `--inet-button-default-border-color` | `--inet-secondary-color` | redefine | `transparent` | default stays ghost at rest |
| `--inet-button-default-hover-bg-color` | `--inet-secondary-color` | redefine | `--inet-ui-neutral-hover-bg-color` | neutral hover background |
| `--inet-button-default-hover-text-color` | `--inet-secondary-color-light` | redefine | `--inet-ui-neutral-hover-color` | neutral hover text |
| `--inet-button-default-hover-border-color` | `--inet-secondary-color` | redefine | `transparent` | hover relies on fill and text, not outline |

### Existing Tokens That Should Mostly Stay Semantic

| Existing token | Recommendation | Notes |
|---|---|---|
| `--inet-secondary-color` | redefine | constrained violet support family (`#7455A8`) |
| `--inet-secondary-color-light` | redefine | violet soft fill (`#EDE8F5`) |
| `--inet-secondary-color-dark` | redefine | violet active/dark (`#4E3378`) |
| `--inet-third-color*` | redefine | constrained rose support family (`#B54B6E`) |
| `--inet-fourth-color*` | redefine | constrained teal support family (`#1D8A86`) |
| `--inet-success-color*` | keep | semantic family |
| `--inet-warning-color*` | keep | semantic family |
| `--inet-danger-color*` | keep | semantic family |
| `--inet-info-color*` | keep | semantic family |

## New Alias Tokens To Add

The following shell aliases do not map cleanly today and should be added.

| New token | Reason |
|---|---|
| `--inet-shell-surface-canvas` | clearer shell role |
| `--inet-shell-surface-default` | clearer shell role |
| `--inet-shell-surface-subtle` | clearer shell role |
| `--inet-shell-surface-hover` | clearer shell role |
| `--inet-shell-border-strong` | missing today |
| `--inet-shell-text-muted` | missing today |
| `--inet-shell-text-subtle` | missing today |
| `--inet-primary-active-color` | missing active value |
| `--inet-primary-text-color` | missing text-on-primary-soft value |
| `--inet-secondary-active-color` | missing active value |
| `--inet-secondary-text-color` | missing text-on-secondary-soft value |
| `--inet-third-soft-color` | clarify soft support surface role |
| `--inet-third-text-color` | text on third-soft surfaces |
| `--inet-fourth-soft-color` | clarify soft support surface role |
| `--inet-fourth-text-color` | text on fourth-soft surfaces |
| `--inet-ui-neutral-color` | needed for neutral control strategy |
| `--inet-ui-neutral-border-color` | needed for neutral control strategy |
| `--inet-ui-neutral-hover-bg-color` | needed for neutral control strategy |
| `--inet-ui-neutral-hover-color` | needed for neutral control strategy |

## Implementation Notes

- Existing `--inet-*` tokens should remain the compatibility surface.
- New shell aliases should clarify intent and make later maintenance easier.
- The first implementation pass should focus on token remapping, not component redesign.
- The highest-impact change is neutralizing default and secondary button/control tokens.

## Design Intent Summary

The shell should look modern by relying on:

- warm, disciplined neutrals
- one primary amber accent
- reduced use of secondary and support hues
- semantic colors only when they convey meaning

This preserves a complete palette while keeping the shell visually calm.
