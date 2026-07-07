# StyleBI Shell Implementation Roadmap

## Purpose

This document translates the shell design specs into an implementation sequence for the current shared UI codebase, with `portal` as the first execution target.

It is intended to answer:

- what should be changed first
- which files are the primary targets
- which changes are token work versus adoption work
- which changes should be exposed to customer theming
- what should be deferred until later phases

This roadmap is `portal`-first, not `portal`-only. `em` alignment should be handled in a later dedicated alignment phase.

In practice, successful shell implementation also requires screen-by-screen review after shared token and selector work lands. Shared adoption catches most modernization work, but legacy Bootstrap-era styling leakage and one-off local overrides still need targeted cleanup when they surface in real screens.

## Composer Boundary

This roadmap covers:

- shared shell token work
- shared shell adoption, starting in `portal`
- shared Composer chrome that should consume shell foundations first

This roadmap does not define Composer-specific authoring-state adoption.

For Composer-specific state implementation such as context, selected, dimmed, and worksheet-specific authoring extensions, use [composer-implementation-roadmap.md](E:\home\dev\github\lookfeel\composer-implementation-roadmap.md).

## Related Specs

- [theme-strategy-overview.md](E:\home\dev\github\lookfeel\theme-strategy-overview.md)
- [shell-design-spec.md](E:\home\dev\github\lookfeel\shell-design-spec.md)
- [shell-palette-spec.md](E:\home\dev\github\lookfeel\shell-palette-spec.md)
- [composer-palette-spec.md](E:\home\dev\github\lookfeel\composer-palette-spec.md)
- [composer-implementation-roadmap.md](E:\home\dev\github\lookfeel\composer-implementation-roadmap.md)

## Delivery Model

Use three change types throughout this roadmap:

- `token`
  - define or redefine shared runtime `--inet-*` variables
- `adoption`
  - update shared selectors so they consume the tokens consistently
- `exposure`
  - add new tokens to the theme editor model when customers should be able to override them

## Value Source Rule

Unless otherwise noted:

- color values in this roadmap come from [shell-palette-spec.md](E:\home\dev\github\lookfeel\shell-palette-spec.md)
- typography, spacing, radius, sizing, elevation, and interaction values come from [shell-design-spec.md](E:\home\dev\github\lookfeel\shell-design-spec.md)

Inline `Default / alias` values in this roadmap are meant to make implementation self-sufficient. When a row needs special clarification, the source should be called out explicitly in that row or subsection.

## Themeability Mechanism

StyleBI's Themes UI in EM admin (`Settings > Presentation > Themes`) lets customers upload custom themes. Each theme carries two CSS blobs:

- `portalCss`
- `emCss`

These are injected at runtime and can override any existing `--inet-*` CSS custom property without code changes.

### What customers can change today

Any value already backed by a runtime `--inet-*` variable, including:

- colors
- fonts
- backgrounds
- hover states
- panel colors
- button variants

### What customers cannot change today without code

Values that are still:

- hardcoded in component SCSS
- compiled from SCSS variables at build time
- expressed only through non-themeable utility classes

Common examples include older off-white/gray utility classes in [_themeable.scss](E:\home\dev\github\stylebi-visual_BI_tool\stylebi\web\projects\portal\src\scss\_themeable.scss:1).

### Themeability rule

If a shell modernization change is meant to be customer-themeable, do not stop at SCSS variable changes alone.

Use this sequence:

1. define or alias a runtime `--inet-*` token in [_variables.scss](E:\home\dev\github\stylebi-visual_BI_tool\stylebi\web\projects\portal\src\scss\_variables.scss:1)
2. adopt that token in [_themeable.scss](E:\home\dev\github\stylebi-visual_BI_tool\stylebi\web\projects\portal\src\scss\_themeable.scss:1), [_bootstrap-override.scss](E:\home\dev\github\stylebi-visual_BI_tool\stylebi\web\projects\portal\src\scss\_bootstrap-override.scss:1), or shared component selectors
3. expose that token in the theme editor model when customer control is required

### Theme editor model note

The list of variables surfaced in the theme editor is driven by `ThemeCssVariableModel[]`. When a new shell token is intended to be customer-themeable, it must also be added there.

## Primary Code Targets

| File / area | Main responsibility |
|---|---|
| [web/projects/portal/src/scss/_variables.scss](E:\home\dev\github\stylebi-visual_BI_tool\stylebi\web\projects\portal\src\scss\_variables.scss:1) | define and alias runtime shell tokens |
| [web/projects/portal/src/scss/_bootstrap-override.scss](E:\home\dev\github\stylebi-visual_BI_tool\stylebi\web\projects\portal\src\scss\_bootstrap-override.scss:1) | restyle Bootstrap-shaped controls and shared component chrome |
| [web/projects/portal/src/scss/_themeable.scss](E:\home\dev\github\stylebi-visual_BI_tool\stylebi\web\projects\portal\src\scss\_themeable.scss:1) | adopt shell tokens in shared utilities, panel/list states, toolbars, and shell-adjacent selectors |
| [web/projects/portal/src/global.scss](E:\home\dev\github\stylebi-visual_BI_tool\stylebi\web\projects\portal\src\global.scss:1) | shell entry point only; avoid putting detailed rules here if lower layers fit |
| [web/projects/portal/src/app/widget/standard-dialog/standard-dialog.component.html](E:\home\dev\github\stylebi-visual_BI_tool\stylebi\web\projects\portal\src\app\widget\standard-dialog\standard-dialog.component.html:1) | shared dialog structure reference |
| [web/projects/portal/src/app/widget/modal-header/modal-header.component.html](E:\home\dev\github\stylebi-visual_BI_tool\stylebi\web\projects\portal\src\app\widget\modal-header\modal-header.component.html:1) | shared dialog title/header structure reference |
| theme editor model in EM | expose new themeable `--inet-*` variables when needed |

## Widget Implementation Map

This section is the practical per-widget guide for shell implementation work, starting in `portal`.

It is reference material for implementation planning, not a separate pre-phase of work.

Each widget block combines:

- where the shared code already lives
- a short implementation summary
- a pointer to detailed appendix deltas when available

Implementation should not stop at shared selector updates alone. After each major shared change lands, run a screen-by-screen review of the most affected surfaces to catch legacy Bootstrap treatments, one-off component overrides, and selectors that bypass the shared shell token layer.

### Buttons

Shared code:

- token definitions already live in [_variables.scss](E:\home\dev\github\stylebi-visual_BI_tool\stylebi\web\projects\portal\src\scss\_variables.scss:1)
- shared button selectors already live in [_bootstrap-override.scss](E:\home\dev\github\stylebi-visual_BI_tool\stylebi\web\projects\portal\src\scss\_bootstrap-override.scss:1)
- primary, secondary, and default button variants are all already wired through `.btn.btn-primary`, `.btn.btn-secondary`, and `.btn.btn-default`

Implementation summary:

- remap primary, secondary, and default button tokens in `_variables.scss`, then finish shared button sizing, radius, padding, and typography in [_bootstrap-override.scss](E:\home\dev\github\stylebi-visual_BI_tool\stylebi\web\projects\portal\src\scss\_bootstrap-override.scss:1)

Detailed implementation deltas:

- for implementation-ready button token and selector changes, use `Appendix A: Buttons`

### Inputs And Selects

Shared code:

- existing token hooks already live in [_variables.scss](E:\home\dev\github\stylebi-visual_BI_tool\stylebi\web\projects\portal\src\scss\_variables.scss:1)
- existing shared selectors already live in [_bootstrap-override.scss](E:\home\dev\github\stylebi-visual_BI_tool\stylebi\web\projects\portal\src\scss\_bootstrap-override.scss:1)
- the main shared selectors are `.form-control`, `.form-floating`, and `.input-group`

Implementation summary:

- finalize shared input, sizing, radius, and focus tokens in `_variables.scss`, then adopt them through `.form-control`, `.form-floating`, and `.input-group` in [_bootstrap-override.scss](E:\home\dev\github\stylebi-visual_BI_tool\stylebi\web\projects\portal\src\scss\_bootstrap-override.scss:1) rather than per-component overrides

Detailed implementation deltas:

- for implementation-ready input token and selector changes, use `Appendix B: Inputs And Selects`

### Dialogs

Shared code:

- shared structure is centralized in [standard-dialog.component.html](E:\home\dev\github\stylebi-visual_BI_tool\stylebi\web\projects\portal\src\app\widget\standard-dialog\standard-dialog.component.html:1) and [modal-header.component.html](E:\home\dev\github\stylebi-visual_BI_tool\stylebi\web\projects\portal\src\app\widget\modal-header\modal-header.component.html:1)
- shared modal presentation should be normalized in [_bootstrap-override.scss](E:\home\dev\github\stylebi-visual_BI_tool\stylebi\web\projects\portal\src\scss\_bootstrap-override.scss:1)

Implementation summary:

- normalize dialog surface, radius, elevation, title treatment, and spacing through shared modal selectors in [_bootstrap-override.scss](E:\home\dev\github\stylebi-visual_BI_tool\stylebi\web\projects\portal\src\scss\_bootstrap-override.scss:1), then validate against the shared dialog structure components before touching one-off dialogs

Detailed implementation deltas:

- for implementation-ready dialog token and selector changes, use `Appendix C: Dialogs`

### Tabs And Navigation

Shared code:

- existing token hooks already live in [_variables.scss](E:\home\dev\github\stylebi-visual_BI_tool\stylebi\web\projects\portal\src\scss\_variables.scss:1)
- shared adoption already lives across [_themeable.scss](E:\home\dev\github\stylebi-visual_BI_tool\stylebi\web\projects\portal\src\scss\_themeable.scss:1) and [_bootstrap-override.scss](E:\home\dev\github\stylebi-visual_BI_tool\stylebi\web\projects\portal\src\scss\_bootstrap-override.scss:1)
- the main shared selector areas are Bootstrap nav/tab selectors plus shared nav/tab helpers

Implementation summary:

- keep the nav/tab token defaults in `_variables.scss`, then finish neutral hover, sizing, and typography adoption in the shared nav/tab selectors across [_bootstrap-override.scss](E:\home\dev\github\stylebi-visual_BI_tool\stylebi\web\projects\portal\src\scss\_bootstrap-override.scss:1) and [_themeable.scss](E:\home\dev\github\stylebi-visual_BI_tool\stylebi\web\projects\portal\src\scss\_themeable.scss:1)

Detailed implementation deltas:

- for implementation-ready navigation token and selector changes, use `Appendix D: Tabs And Navigation`

### Panel Headers And Toolbars

Shared code:

- existing surface tokens already live in [_variables.scss](E:\home\dev\github\stylebi-visual_BI_tool\stylebi\web\projects\portal\src\scss\_variables.scss:1)
- existing shell-adjacent selectors already live in [_themeable.scss](E:\home\dev\github\stylebi-visual_BI_tool\stylebi\web\projects\portal\src\scss\_themeable.scss:1)
- current high-value shared areas include viewer, repository, and Composer toolbar sections, side panels and slide-outs, and header-like classes such as `.vs-header`

Implementation summary:

- use `_variables.scss` only for any missing aliases, then do the real normalization in shared toolbar, side-panel, and header-like selectors inside [_themeable.scss](E:\home\dev\github\stylebi-visual_BI_tool\stylebi\web\projects\portal\src\scss\_themeable.scss:1)

Detailed implementation deltas:

- for implementation-ready toolbar and panel normalization, use `Appendix E: Panel Headers And Toolbars`

### Shell Tables And Lists

Shared code:

- existing token hooks already live in [_variables.scss](E:\home\dev\github\stylebi-visual_BI_tool\stylebi\web\projects\portal\src\scss\_variables.scss:1)
- existing shell table selectors already live in [_bootstrap-override.scss](E:\home\dev\github\stylebi-visual_BI_tool\stylebi\web\projects\portal\src\scss\_bootstrap-override.scss:1)
- the main selector targets are `.table-bordered` and `.table-sm`

Implementation summary:

- keep the implementation inside the shared shell table selectors in [_bootstrap-override.scss](E:\home\dev\github\stylebi-visual_BI_tool\stylebi\web\projects\portal\src\scss\_bootstrap-override.scss:1), tune shell row/header/hover behavior there, and do not extend those changes into visualization-owned dense grids

Detailed implementation deltas:

- for implementation-ready shell table and list changes, use `Appendix F: Shell Tables And Lists`

## Phase 0: Guardrails And Baseline Audit

### Goal

Confirm that the widget map and first-pass shell scope are stable enough to execute, and avoid broad, unbounded styling churn.

### Tasks

- Freeze the first implementation slice to shared shell adoption in `portal`, with broader shell rollout following later phases.
- Treat `em` as out of scope for implementation, except where theme-editor exposure is required.
- Confirm the first-pass shell scope is stable:
  - button remapping
  - input sizing/focus
  - dialog spacing/title treatment
  - nav/tab neutralization
- Confirm the `Widget Implementation Map` is settled enough for this first pass and that no additional shell changes are expected to join it before implementation starts.
- Identify any active user-owned SCSS changes in the same files before editing.
- Plan a screen-by-screen audit pass after each major shared phase to catch Bootstrap leakage, hardcoded local overrides, and shared surfaces that are not yet consuming the intended shell selectors.

### Output

- approved first implementation slice
- known file ownership and risk areas

## Phase 1: Foundation Token Layer

### Goal

Create the runtime token layer required for shell modernization before changing shared selectors.

### Primary file

- [web/projects/portal/src/scss/_variables.scss](E:\home\dev\github\stylebi-visual_BI_tool\stylebi\web\projects\portal\src\scss\_variables.scss:1)

### Tasks

#### 0. Existing tokens to reuse first

Before adding new shell tokens, reuse and clarify the existing foundation tokens that already anchor shell typography and color.

| Token | Purpose | Before | After | Notes |
|---|---|---|---|---|
| `--inet-font-family` | default shell font family | `Roboto, -apple-system, system-ui, BlinkMacSystemFont, "Segoe UI", "Helvetica Neue", Arial, sans-serif` | `Roboto, -apple-system, system-ui, BlinkMacSystemFont, "Segoe UI", "Helvetica Neue", Arial, sans-serif` | no change unless typography spec changes |
| `--inet-font-size-base` | default shell body text size | `13px` | `13px` | no change unless typography spec changes |
| `--inet-text-color` | default shell text color | `#212529` | `#35342F` | update value only |
| `--inet-default-border-color` | default shell border color | `#E0E0E0` | `#D9D5CC` | update value only |
| `--inet-input-border-color` | default input border color | `#E0E0E0` | `var(--inet-default-border-color)` | align input border to shell default border unless real divergence appears |
| `--inet-toolbar-bg-color` | shared toolbar surface | `#FCFCFC` | `#F1EFEA` | update value only |
| `--inet-nav-tabs-bg-color` | shared tab rail surface | `#F0F0F0` | `#F1EFEA` | update value only |
| `--inet-nav-tabs-selected-border-color` | active tab emphasis | `var(--inet-primary-color)` | `var(--inet-primary-color)` | no change; selected emphasis remains primary-led |

Use new tokens only where existing ones are missing, semantically overloaded, or too legacy to represent the new shell roles cleanly.

#### 1. Neutral control token family

Add a neutral control family so shell controls no longer depend on `--inet-secondary-color`.

Define in [_variables.scss](E:\home\dev\github\stylebi-visual_BI_tool\stylebi\web\projects\portal\src\scss\_variables.scss:1):

| Token | Action | Before | After | Notes |
|---|---|---|---|---|
| `--inet-ui-neutral-color` | add | missing | `var(--inet-text-muted-color)` | neutral text/icon color for secondary and default shell controls |
| `--inet-ui-neutral-border-color` | add | missing | `var(--inet-default-border-color)` | neutral border for outline controls |
| `--inet-ui-neutral-hover-bg-color` | add | missing | `#ECE9E2` | neutral hover fill for passive shell controls |
| `--inet-ui-neutral-hover-color` | add | missing | `var(--inet-text-color)` | stronger text/icon color on hover |
| `--inet-shell-selected-bg-color` | add | missing | `#E7E2D8` | neutral selected fill for shell lists and light tables |

#### 2. Shell text aliases

Add text hierarchy aliases that are currently implicit or scattered.

Define in [_variables.scss](E:\home\dev\github\stylebi-visual_BI_tool\stylebi\web\projects\portal\src\scss\_variables.scss:1):

| Token | Action | Before | After | Notes |
|---|---|---|---|---|
| `--inet-text-strong-color` | add | missing | `#1F1F1B` | stronger shell heading and emphasis text |
| `--inet-text-muted-color` | add | missing | `#6A685F` | secondary shell text and neutral control text |
| `--inet-text-subtle-color` | add | missing | `#99958C` | low-priority shell meta text and placeholders |

Optional follow-up additions if needed during adoption:

| Token | Action | Before | After | Notes |
|---|---|---|---|---|
| `--inet-meta-font-size` | add | missing | `11px` | shared helper/meta text size |
| `--inet-badge-font-size` | add | missing | `10px` | shared badge/chip text size |

#### 3. Sizing tokens

Add shared shell sizing tokens.

Define in [_variables.scss](E:\home\dev\github\stylebi-visual_BI_tool\stylebi\web\projects\portal\src\scss\_variables.scss:1):

| Token | Action | Before | After | Notes |
|---|---|---|---|---|
| `--inet-control-height-sm` | add | missing | `24px` | compact shell controls |
| `--inet-control-height-md` | add | missing | `30px` | default shell controls |
| `--inet-control-height-lg` | add | missing | `36px` | larger emphasis controls |
| `--inet-row-height-md` | add | missing | `34px` | shell rows and light lists |
| `--inet-tab-height` | add | missing | `32px` | shell tabs |
| `--inet-pill-height` | add | missing | `26px` | segmented controls |

#### 4. Spacing tokens

Add a small shared spacing ladder.

Define in [_variables.scss](E:\home\dev\github\stylebi-visual_BI_tool\stylebi\web\projects\portal\src\scss\_variables.scss:1):

| Token | Action | Before | After | Notes |
|---|---|---|---|---|
| `--inet-space-1` | add | missing | `2px` | fine spacing |
| `--inet-space-2` | add | missing | `4px` | dense spacing |
| `--inet-space-3` | add | missing | `6px` | compact internal spacing |
| `--inet-space-4` | add | missing | `8px` | default small spacing |
| `--inet-space-5` | add | missing | `12px` | control/container spacing |
| `--inet-space-6` | add | missing | `16px` | section spacing |
| `--inet-space-8` | add | missing | `24px` | panel/dialog spacing |

#### 5. Radius tokens

Define in [_variables.scss](E:\home\dev\github\stylebi-visual_BI_tool\stylebi\web\projects\portal\src\scss\_variables.scss:1):

| Token | Action | Before | After | Notes |
|---|---|---|---|---|
| `--inet-radius-sm` | add | missing | `2px` | tight details |
| `--inet-radius-md` | add | missing | `3px` | default shell control radius |
| `--inet-radius-lg` | add | missing | `4px` | cards and grouped controls |
| `--inet-radius-xl` | add | missing | `6px` | dialogs and overlays |
| `--inet-radius-pill` | add | missing | `999px` | pill shapes |

#### 6. Elevation and focus tokens

Define in [_variables.scss](E:\home\dev\github\stylebi-visual_BI_tool\stylebi\web\projects\portal\src\scss\_variables.scss:1):

| Token | Action | Before | After | Notes |
|---|---|---|---|---|
| `--inet-shadow-low` | add | missing | `0 1px 2px rgba(0,0,0,.06), 0 1px 3px rgba(0,0,0,.04)` | low elevation for cards and light surfaces |
| `--inet-shadow-medium` | add | missing | `0 2px 4px rgba(0,0,0,.08), 0 1px 6px rgba(0,0,0,.05)` | medium elevation for menus and dropdowns |
| `--inet-shadow-overlay` | add | missing | `0 8px 16px rgba(0,0,0,.12), 0 4px 14px rgba(0,0,0,.08)` | overlay elevation for dialogs and modals |
| `--inet-focus-ring-width` | add | missing | `2px` | standard shell focus ring width |
| `--inet-focus-ring-color` | add | missing | `rgba(229, 138, 42, 0.28)` | standard shell focus ring color |

### Rules

- Prefer additive aliases first.
- Keep old token names working where possible.
- Do not break customer themes unnecessarily by deleting or radically repurposing old variables in one pass.

### Output

- expanded shell token surface in `_variables.scss`

## Phase 2: Single-Accent Control Remapping

### Goal

Deliver the highest-visibility shell modernization by removing teal from routine control chrome.

### Primary files

- [web/projects/portal/src/scss/_variables.scss](E:\home\dev\github\stylebi-visual_BI_tool\stylebi\web\projects\portal\src\scss\_variables.scss:1)
- [web/projects/portal/src/scss/_bootstrap-override.scss](E:\home\dev\github\stylebi-visual_BI_tool\stylebi\web\projects\portal\src\scss\_bootstrap-override.scss:1)

### Tasks

#### 1. Remap secondary button tokens

Update in [_variables.scss](E:\home\dev\github\stylebi-visual_BI_tool\stylebi\web\projects\portal\src\scss\_variables.scss:1):

| Token | Action | Before | After | Notes |
|---|---|---|---|---|
| `--inet-button-secondary-bg-color` | update | `var(--inet-secondary-color)` | `transparent` | outlined neutral control |
| `--inet-button-secondary-text-color` | update | `white` | `var(--inet-ui-neutral-color)` | neutral text/icon |
| `--inet-button-secondary-border-color` | update | `var(--inet-secondary-color)` | `var(--inet-ui-neutral-border-color)` | neutral outline |
| `--inet-button-secondary-hover-bg-color` | update | `var(--inet-secondary-color-dark)` | `var(--inet-ui-neutral-hover-bg-color)` | subtle hover fill |
| `--inet-button-secondary-hover-text-color` | update | `white` | `var(--inet-ui-neutral-hover-color)` | stronger hover text |
| `--inet-button-secondary-hover-border-color` | update | `var(--inet-secondary-color-dark)` | `var(--inet-ui-neutral-border-color)` | stable neutral border |

#### 2. Remap default button tokens

Update in [_variables.scss](E:\home\dev\github\stylebi-visual_BI_tool\stylebi\web\projects\portal\src\scss\_variables.scss:1):

| Token | Action | Before | After | Notes |
|---|---|---|---|---|
| `--inet-button-default-bg-color` | keep | `transparent` | `transparent` | ghost-like control |
| `--inet-button-default-text-color` | update | `var(--inet-secondary-color)` | `var(--inet-ui-neutral-color)` | neutral text/icon |
| `--inet-button-default-border-color` | update | `var(--inet-secondary-color)` | `transparent` | no persistent outline by default |
| `--inet-button-default-hover-bg-color` | update | `var(--inet-secondary-color)` | `var(--inet-ui-neutral-hover-bg-color)` | subtle hover fill |
| `--inet-button-default-hover-text-color` | update | `var(--inet-secondary-color-light)` | `var(--inet-ui-neutral-hover-color)` | stronger hover text |
| `--inet-button-default-hover-border-color` | update | `var(--inet-secondary-color)` | `transparent` | remain ghost-like on hover unless selector work suggests otherwise |

#### 3. Preserve primary and semantic buttons

Keep:

- `primary` as the one routine accent
- `danger` as semantic/destructive
- `success`, `warning`, `info` for semantic states only

Practical defaults to preserve:

| Token family | Before | After | Notes |
|---|---|---|---|
| `--inet-button-primary-*` | existing primary token family | no change | keep primary as the one routine accent |
| `--inet-danger-*` and `.btn-danger` styling | existing semantic danger family | no change | keep semantic danger behavior intact |

#### 4. Validate existing selectors

Selectors already wired for this work:

- `.btn.btn-primary`
- `.btn.btn-secondary`
- `.btn.btn-default`

The goal is to let existing selectors inherit new visual behavior through token changes first.

### Output

- shell buttons read as filled / outline / ghost rather than orange / teal / teal-outline

## Phase 3: Input And Focus Modernization

### Goal

Standardize shell form controls around the new shell sizing and focus model.

### Primary files

- [web/projects/portal/src/scss/_variables.scss](E:\home\dev\github\stylebi-visual_BI_tool\stylebi\web\projects\portal\src\scss\_variables.scss:1)
- [web/projects/portal/src/scss/_bootstrap-override.scss](E:\home\dev\github\stylebi-visual_BI_tool\stylebi\web\projects\portal\src\scss\_bootstrap-override.scss:1)

### Tasks

#### 1. Form control value adoption

Adopt through shared form selectors in [_bootstrap-override.scss](E:\home\dev\github\stylebi-visual_BI_tool\stylebi\web\projects\portal\src\scss\_bootstrap-override.scss:1):

| Selector / property | Action | Before | After | Notes |
|---|---|---|---|---|
| `.form-floating .form-control:not(textarea)` `height` | keep | `calc(1.5em + .75rem + 2px)` | `calc(1.5em + .75rem + 2px)` | do not change — see float-label note below |
| shared input selectors `padding-inline` | update | inherited Bootstrap/default | `var(--inet-space-4)` | use `8px` only as a temporary fallback if spacing tokens are not yet available |
| shared input selectors `border-radius` | update | inherited Bootstrap/default | `var(--inet-radius-md)` | align form controls to shell radius |
| shared input selectors `border-color` | update | `var(--inet-input-border-color)` | `var(--inet-default-border-color)` | align shared input borders to the default shell border |
| shared input selectors `:focus box-shadow` | update | `0 0 0 1px var(--inet-primary-color)` | `0 0 0 var(--inet-focus-ring-width) var(--inet-focus-ring-color)` | standardize shell focus ring |
| shared input selectors `:focus border-color` | update | `var(--inet-primary-color-light)` | `var(--inet-primary-color)` | clearer shell focus emphasis |

#### 2. Keep float-label behavior stable

Do not replace the `calc(1.5em + .75rem + 2px)` height on `.form-floating .form-control:not(textarea)` with a fixed token value.

The calc is deliberate geometry: `1.5em` (one line of text at the element font size) + `.75rem` (top padding + bottom padding, both set to `$form-floating-padding-y: .375rem`) + `2px` (borders). It keeps the float-label input height proportional to the font size and internally consistent with the SCSS label-positioning variables (`$form-floating-padding-y`, `$form-floating-label-y`) that are baked in at compile time.

At StyleBI's 13px base font the calc already resolves to approximately `31–34px`, which is close enough to the 30px shell baseline for visual consistency. The `min-height: var(--inet-control-height-md)` applied to the base `.form-control` selector provides the 30px floor for non-float-label inputs without touching float-label geometry.

- do not widen scope into per-component form rewrites in this phase

### Key selectors

- `.form-floating`
- `.form-control`
- `.input-group`

### Output

- consistent `30px` shell control baseline
- clearer primary-focus behavior

### Validation checks

The float-label height is intentionally left at its calc value, so label-positioning regressions are not a concern here. The main change to verify is the new focus ring and border-radius on all shared inputs. Open a dialog or form-heavy screen and check these two float-label components for any unexpected positioning shift from the `border-radius` or `padding-inline` additions:

| File | What to check |
|---|---|
| [app/portal/data/data-datasource-browser/datasources-database/driver-wizard/driver-wizard.component.scss](E:\home\dev\github\stylebi-visual_BI_tool\stylebi\web\projects\portal\src\app\portal\data\data-datasource-browser\datasources-database\driver-wizard\driver-wizard.component.scss) | `span.helper-text` uses `bottom: -1.5em` — verify helper text still clears the field border after radius change |
| [app/vsobjects/dialog/graph/chart-line-pane.component.scss](E:\home\dev\github\stylebi-visual_BI_tool\stylebi\web\projects\portal\src\app\vsobjects\dialog\graph\chart-line-pane.component.scss) | custom invalid feedback uses `top: 0em` — verify feedback text is not clipped after padding-inline change |

## Phase 4: Dialog And Modal Standardization

### Goal

Use the shared dialog structure to modernize a large amount of shell UI quickly.

### Primary files

- [web/projects/portal/src/scss/_bootstrap-override.scss](E:\home\dev\github\stylebi-visual_BI_tool\stylebi\web\projects\portal\src\scss\_bootstrap-override.scss:1)
- shared dialog structure references:
  - [standard-dialog.component.html](E:\home\dev\github\stylebi-visual_BI_tool\stylebi\web\projects\portal\src\app\widget\standard-dialog\standard-dialog.component.html:1)
  - [modal-header.component.html](E:\home\dev\github\stylebi-visual_BI_tool\stylebi\web\projects\portal\src\app\widget\modal-header\modal-header.component.html:1)

### Tasks

Apply through shared modal selectors in [_bootstrap-override.scss](E:\home\dev\github\stylebi-visual_BI_tool\stylebi\web\projects\portal\src\scss\_bootstrap-override.scss:1):

| Selector / property | Action | Before | After | Notes |
|---|---|---|---|---|
| `.modal-content` `border-radius` | update | inherited Bootstrap/default | `var(--inet-radius-xl)` | adopt shell dialog radius |
| `.modal-content` `box-shadow` | update | inherited Bootstrap/default | `var(--inet-shadow-overlay)` | adopt shell overlay elevation |
| `.modal-header` `padding` | update | inherited Bootstrap/default | `14px 16px 12px` | shell dialog header rhythm |
| `.modal-body` `padding` | update | inherited Bootstrap/default | `14px 16px` | shell dialog body rhythm |
| `.modal-footer` `padding` | update | inherited Bootstrap/default | `10px 16px` | shell dialog footer rhythm |
| shared dialog title typography | update | inherited/current | `14px / 600` | validate against `standard-dialog` and `modal-header` structure |

Behavior guidance:

- keep close and utility actions visually quiet
- keep shared dialog structure stable
- prioritize shared modal selectors before per-dialog component work

### Output

- shared dialogs feel lighter, cleaner, and more consistent without per-dialog rewrites

## Phase 5: Tabs, Navigation, And Toolbar Neutralization

### Goal

Make shell navigation read as part of the frame rather than as colorful pills or button rows.

### Primary files

- [web/projects/portal/src/scss/_variables.scss](E:\home\dev\github\stylebi-visual_BI_tool\stylebi\web\projects\portal\src\scss\_variables.scss:1)
- [web/projects/portal/src/scss/_bootstrap-override.scss](E:\home\dev\github\stylebi-visual_BI_tool\stylebi\web\projects\portal\src\scss\_bootstrap-override.scss:1)
- [web/projects/portal/src/scss/_themeable.scss](E:\home\dev\github\stylebi-visual_BI_tool\stylebi\web\projects\portal\src\scss\_themeable.scss:1)

### Tasks

#### 1. Known deltas

| Selector / property | Action | Before | After | Notes |
|---|---|---|---|---|
| `--inet-nav-tabs-bg-color` | update | `#F0F0F0` | `#F1EFEA` | align tab rails to the shell subtle surface |
| `--inet-toolbar-bg-color` | update | `#FCFCFC` | `#F1EFEA` | align shared toolbars to the shell subtle surface |
| `.nav-tabs` `border-bottom` | keep/update | `1px solid var(--inet-default-border-color)` | `1px solid var(--inet-default-border-color)` | keep structure; border tone changes through shell border retuning |
| `.nav-tabs` `background-color` | add | missing | `var(--inet-nav-tabs-bg-color)` | make the tab rail explicitly consume the shell tab surface token |
| `.nav-link` text color variables | update | `--bs-nav-link-color: var(--inet-link-color)` and `--bs-nav-link-hover-color: var(--inet-link-hover-color)` | `--bs-nav-link-color: var(--inet-nav-tabs-text-color)` and `--bs-nav-link-hover-color: var(--inet-text-color)` | align tabs with shell nav text instead of generic link styling |
| `.nav-tabs .nav-link:hover` `background-color` | add | missing | `var(--inet-ui-neutral-hover-bg-color)` | keep passive navigation quiet and neutral |
| `.nav-tabs .nav-link` sizing and typography | add/update | inherited/current mixed sizing | `min-height: var(--inet-tab-height); font-size: var(--inet-font-size-base); font-weight: 500; line-height: 1` | align Bootstrap tabs with shell control sizing |
| `.nav-tabs .nav-item.show .nav-link, .nav-tabs .nav-link.active` `background-color` | update | `var(--inet-dialog-bg-color)` | `var(--inet-main-panel-bg-color)` | use the shared shell content surface for active tabs instead of dialog-specific fill |
| `.nav-tabs .nav-item.show .nav-link, .nav-tabs .nav-link.active` `color` | update | `var(--inet-selected-item-text-color)` | `var(--inet-text-color)` | keep active tabs in shell text language rather than selected-item semantics |
| nested `.nav-link` inside `.portal-sub-navbar, .composer-bottom-tabs, .schedule-navbar, .vpm-navbar, .viewer-bottom-tabs` | keep | color-only override | `color: var(--inet-nav-tabs-text-color)` | keep nested `_themeable.scss` nav-link rules color-only; do sizing through `.nav-tabs .nav-link` in `_bootstrap-override.scss` |
| `.portal-sub-navbar, .composer-bottom-tabs, .schedule-navbar, .vpm-navbar, .viewer-bottom-tabs` `background-color` | keep/update | `var(--inet-nav-tabs-bg-color)` with old token value | `var(--inet-nav-tabs-bg-color)` | shared sub-navbar surfaces should inherit the retuned tab rail token |
| same shared sub-navbar group `.nav-link` `color` | keep/update | `var(--inet-nav-tabs-text-color)` | `var(--inet-nav-tabs-text-color)` | keep existing hook; tone changes through shell text retuning |
| `.composer-bottom-tabs` `min-height` | update | `33px` | `var(--inet-tab-height)` | this selector already owns geometry and should adopt shell tab height directly |
| `.portal-sub-navbar, .schedule-navbar, .vpm-navbar, .viewer-bottom-tabs` sizing | keep | inherited/current | no change | inherit token changes only unless a later selector survey finds direct geometry rules |
| `.nav-item.bb-highlight-primary` and `.nav-item.bt-highlight-primary` | keep/update | `var(--inet-nav-tabs-selected-border-color)` | `var(--inet-nav-tabs-selected-border-color)` | emphasis helpers should inherit token changes only, not shell sizing |
| `.portal-navbar .nav-item.bb-highlight-primary` | keep | `var(--inet-navbar-selected-border-color)` | `var(--inet-navbar-selected-border-color)` | top-navbar selected emphasis already uses the intended primary-led model |
| `.composer-body .search-box` `background-color` | keep/update | `var(--inet-repository-tree-toolbar-bg-color)` with old surface value | `var(--inet-repository-tree-toolbar-bg-color)` | shared repository/search chrome should follow shell framing |
| `.asset-pane-gutter:not(.worksheet-in-view) .gutter-horizontal::before` `background-color` | keep/update | `var(--inet-repository-tree-toolbar-bg-color)` with old surface value | `var(--inet-repository-tree-toolbar-bg-color)` | non-worksheet repository gutter chrome should follow shell framing |
| `.slide-out-content-container, .slide-out-toggle-container` border/surface framing | update | utility-driven gray border framing | `border-color: var(--inet-default-border-color); background-color: var(--inet-toolbar-bg-color)` | shared side-panel and slide-out chrome should follow shell |
| `.viewer-toolbar` `background-color` | keep/update | `var(--inet-vs-toolbar-bg-color)` resolving to `#FCFCFC` | `var(--inet-vs-toolbar-bg-color)` | keep selector token-driven; retune the toolbar surface token |
| `.repository-tree-toolbar` `background-color` | keep/update | `var(--inet-repository-tree-toolbar-bg-color)` resolving to `#FCFCFC` | `var(--inet-repository-tree-toolbar-bg-color)` | keep selector token-driven; retune the repository toolbar surface token |
| `.tab-body .data-pane .btn-toolbar` and shared toolbar group `background-color` | keep/update | `var(--inet-toolbar-bg-color)` resolving to `#FCFCFC` | `var(--inet-toolbar-bg-color)` | keep selectors token-driven; retune the shared toolbar surface token |

##### Out of scope for initial Phase 5

| Selector area | Why it stays out |
|---|---|
| `.inetsoft-logo` | branding, not nav/tab shell behavior |
| `.composer-btn-toolbar` background/text hover system | top Composer navbar/home affordance, not neutral shell tab framing |
| `.composer-btn-toolbar .composer-icon` and related home/breadcrumb icon rules | branded or Composer-home affordances rather than shared shell framing |
| `.composer-breadcrumb-1`, `.editor-breadcrumb-1`, `.assets-icon`, and `.viewsheet-icon` | Composer home and navigation-identity affordances rather than neutral shell framing |
| `.portal-navbar .home-icon` and `.home-nav-item` | top-level branded/home affordance styling |
| `.portal-navbar .portal-nav-item:hover` and `.composer-btn-toolbar .composer-btn:hover` | explicit top-navbar hover affordances already exist and should stay separate from neutral tab/sub-navbar tuning in initial Phase 5 |
| worksheet-specific selectors or worksheet-only view conditions | authoring or worksheet state belongs to Composer/worksheet layers, not shell framing |
| `.composer-body.viewsheet-in-view .gutter-horizontal::after` and `.composer-body.library-in-view .gutter-horizontal::after` | adjacent layout surface treatment, not core nav/tab selector work |

### Key token hooks

- `--inet-nav-tabs-text-color`
- `--inet-nav-tabs-bg-color`
- `--inet-nav-tabs-selected-border-color`
- `--inet-toolbar-bg-color`
- portal/composer navbar token families

### Output

- calmer shell framing
- stronger distinction between shell chrome and Composer authoring states

### Validation checks

Phase 5 adds `min-height` and `background-color` to shared nav/tab selectors. Three component files have explicit geometry or token references that need visual confirmation after this phase lands:

| File | What to check |
|---|---|
| [app/composer/gui/composer-main.component.scss](E:\home\dev\github\stylebi-visual_BI_tool\stylebi\web\projects\portal\src\app\composer\gui\composer-main.component.scss) | `.tabs-bottom.nav-tabs { height: 33px }` — explicit height coexists with the new `min-height`; verify Composer bottom tabs render at the correct height and are not taller than expected |
| [app/composer/gui/tab-selector/tab-selector-shared.scss](E:\home\dev\github\stylebi-visual_BI_tool\stylebi\web\projects\portal\src\app\composer\gui\tab-selector\tab-selector-shared.scss) | nav-link anchors have padding zeroed out — verify these tabs still have visible hit area after `min-height` is added via the shared selector |
| [app/viewer/viewer-view/page-tab.component.scss](E:\home\dev\github\stylebi-visual_BI_tool\stylebi\web\projects\portal\src\app\viewer\viewer-view\page-tab.component.scss) | `.tab-scroller-button` consumes `var(--inet-nav-tabs-bg-color)` directly — token retune flows here automatically; verify the scroll button background matches the retuned tab rail color |

## Phase 6: Panel Headers, Shared Surfaces, And Utility Adoption

### Goal

Apply shell typography, spacing, and surface rules to the shared shell selectors that currently rely on older assumptions or hardcoded SCSS values.

### Primary file

- [web/projects/portal/src/scss/_themeable.scss](E:\home\dev\github\stylebi-visual_BI_tool\stylebi\web\projects\portal\src\scss\_themeable.scss:1)

### Tasks

#### 1. Known deltas

| Selector / property | Action | Before | After | Notes |
|---|---|---|---|---|
| `.viewer-toolbar` `background-color` | update | `var(--inet-vs-toolbar-bg-color)` with old toolbar token value | `var(--inet-vs-toolbar-bg-color)` | most of the visual change should come from token retuning |
| `.viewer-toolbar-btn` `background-color` | keep/update | `var(--inet-vs-toolbar-bg-color)` with old toolbar token value | `var(--inet-vs-toolbar-bg-color)` | keep shared viewer toolbar controls aligned with the parent toolbar surface |
| `.repository-tree-toolbar` `background-color` | update | `var(--inet-repository-tree-toolbar-bg-color)` with old toolbar token value | `var(--inet-repository-tree-toolbar-bg-color)` | keep shared toolbar behavior aligned |
| `.data-model-header-pane` `background-color` | keep/update | `var(--inet-toolbar-bg-color)` with old toolbar token value | `var(--inet-toolbar-bg-color)` | shared header-like pane should follow the shell toolbar surface |
| `.ws-composite-table-breadcrumb-container` `background-color` | keep/update | `var(--inet-toolbar-bg-color)` with old toolbar token value | `var(--inet-toolbar-bg-color)` | shared breadcrumb/tool surface should follow shell chrome |
| `.vs-header-cell-button-sort` `background-color` | keep/update | `var(--inet-toolbar-bg-color)` with old toolbar token value | `var(--inet-toolbar-bg-color)` | small shared header-adjacent control should follow shell chrome |
| `.browser-sort-toolbar, .browser-sort-toolbar-btn` `background-color` | keep/update | `var(--inet-toolbar-bg-color)` with old toolbar token value | `var(--inet-toolbar-bg-color)` | shared sort-toolbar surface and controls should follow shell chrome |
| `.wizard-toolbar, .wizard-toolbar-wrapper, .wizard-toolbar-btn` `background-color` | keep/update | `var(--inet-toolbar-bg-color)` with old toolbar token value | `var(--inet-toolbar-bg-color)` | shell-adjacent workflow chrome should follow the shared toolbar surface |
| `.mini-toolbar-button-group .btn` `background-color` | keep/update | `var(--inet-toolbar-bg-color)` with old toolbar token value | `var(--inet-toolbar-bg-color)` | small shared toolbar buttons should follow shell chrome |
| shared toolbar-like selectors in `_themeable.scss` | update | mixed toolbar backgrounds and spacing | `background-color: var(--inet-toolbar-bg-color); padding: var(--inet-space-4) var(--inet-space-5)` | normalize through shared selectors rather than feature-specific overrides; apply padding only where the selectors already own padding |
| side-panel surface tokens | update | mixed off-white / white fallbacks | `--inet-portal-side-panel-bg-color: #F1EFEA` and `--inet-composer-side-panel-bg-color: #F1EFEA` | align side surfaces with shell framing |
| shared shell header/title selectors | update | mixed inherited/current title values | `font-size: 14px; font-weight: 600` | use one shared shell title treatment for truly shared panel/header titles |
| `.composer-bottom-tabs` utility-driven border inheritance | update | `@extend .bt-gray` | `border-top: 1px solid var(--inet-default-border-color)` | replace utility-driven shell border inheritance with explicit token-backed border styling |
| `.left .slide-out-content-container, .left .slide-out-toggle-container` | update | `@extend .br-gray` | `border-right: 1px solid var(--inet-default-border-color)` | replace utility-driven shell border inheritance with explicit token-backed border styling |
| `.right .slide-out-content-container, .right .slide-out-toggle-container` | update | `@extend .bl-gray` | `border-left: 1px solid var(--inet-default-border-color)` | replace utility-driven shell border inheritance with explicit token-backed border styling |
| `.composer-body .gutter` | update | `@extend .bg-white-inet` | `background-color: var(--inet-portal-main-panel-bg-color)` | replace utility-driven background inheritance with explicit shell surface styling if the selector remains in shared shell scope |

#### 2. Remaining selector survey notes

- review gray/offwhite background utility classes, hover helpers, and hardcoded border utilities before normalizing them

##### Out of scope for initial Phase 6

| Selector area | Why it stays out |
|---|---|
| `.modal-title` | already handled through shared dialog tokens and selectors, not a Phase 6 target |
| `.ws-graph-thumbnail__title`, `.schema-table-title`, `.physical-graph-table-title` | worksheet/schema/graph title systems belong to Composer/worksheet layers |
| `.concat-table-title`, `.ws-concat-table-title-bg`, and `.ws-header-*` family | worksheet-specific state and selection styling belongs to Composer/worksheet layers |
| `.title-bar-logo` | branded title/header element, not shared shell chrome |
| `.preview-header-cell-button` | preview-specific control, not yet confirmed as shared shell chrome |
| `&.toolbar-icon--activated` | stateful icon treatment rather than header/container normalization |
| `.popover-header` and `.new-query-popover .popover-header` | component-specific popover header styling, not shared shell header chrome |

### Output

- more of the shell becomes runtime-themeable
- less design drift between shared surfaces

## Phase 7: Shell Tables And Lists

### Goal

Improve shell tables and lists without touching dense analytical grids owned by the visualization layer.

### Primary files

- [web/projects/portal/src/scss/_variables.scss](E:\home\dev\github\stylebi-visual_BI_tool\stylebi\web\projects\portal\src\scss\_variables.scss:1)
- [web/projects/portal/src/scss/_bootstrap-override.scss](E:\home\dev\github\stylebi-visual_BI_tool\stylebi\web\projects\portal\src\scss\_bootstrap-override.scss:1)

### Tasks

#### 1. Known deltas

| Selector / property | Action | Before | After | Notes |
|---|---|---|---|---|
| `--inet-table-border-color` | update | `var(--inet-default-border-color)` with old border value | `var(--inet-default-border-color)` | most border tone change should come through token retuning |
| `.table-sm>:not(caption)>*>*` `padding` | update | `0.3rem` | `0.375rem` | keeps shell tables visually lighter while landing rows near `32-34px` |
| shell table hover background | add/update | inherited/current hover behavior | `var(--inet-ui-neutral-hover-bg-color)` | use neutral hover treatment |
| `.table-bordered` `border` | keep/update | `$table-border-width solid var(--inet-table-border-color)` | `$table-border-width solid var(--inet-table-border-color)` | keep shared selector; border tone changes through token retuning |
| `.table-bordered th, .table-bordered td` `border` | keep/update | `$table-border-width solid var(--inet-table-border-color)` | `$table-border-width solid var(--inet-table-border-color)` | preserve shared shell table border coverage |
| `.table-hover tbody tr:hover` `background-color` | update | `var(--inet-hover-primary-bg-color)` | `var(--inet-ui-neutral-hover-bg-color)` | use neutral shell hover instead of primary-tinted hover |
| `.list-group-item:hover:not(.not-selectable)` `background-color` | update | `var(--inet-hover-primary-bg-color)` | `var(--inet-ui-neutral-hover-bg-color)` | shared shell lists should use neutral hover treatment |
| `.list-group-item:hover:not(.not-selectable)` `color` | update | `var(--inet-hover-text-color)` | `var(--inet-text-color)` | shared shell lists should use standard shell text on hover |
| `--inet-table-heading-text-color` | update | `#495057` | `var(--inet-text-muted-color)` | shell table headers should be clear but quieter than analytical grids |
| `--inet-shell-selected-bg-color` | add | missing | `#E7E2D8` | use a shell-specific selected surface instead of worksheet or visualization selection styling |
| `.list-group-item.active` `background-color` | update | `var(--inet-selected-item-bg-color)` | `var(--inet-shell-selected-bg-color)` | shared shell list active state should use a dedicated shell selected surface |
| `.list-group-item.active` `border-color` | update | `var(--inet-selected-item-bg-color)` | `var(--inet-default-border-color)` | keep active shell list items within shell border language |
| `.list-group-item.active` `color` | update | `var(--inet-selected-item-text-color)` | `var(--inet-text-color)` | keep active shell list items in shell text language |

#### 2. Remaining selector survey notes

- identify any additional shared shell table and list selectors beyond `.table-bordered`, `.table-sm`, `.table-hover`, and `.list-group-item` before broadening Phase 7 edits
- avoid importing visualization density or chart-adjacent table styling into shell-owned selectors

##### Out of scope for initial Phase 7

| Selector area | Why it stays out |
|---|---|
| `.table-data__row`, `.table-data__row:hover`, `.table-data__row.highlighted` | worksheet/details-table row state belongs to Composer/worksheet layers |
| `.ws-header-*`, `.hover-table-header-resizer`, and `.table-endpoint-*` families | worksheet/schema state systems are not shared shell table chrome |
| dense BI result tables and visualization-owned widget tables | analytical surfaces are explicitly outside shell table scope |

### Important boundary

This phase is for shell tables and list chrome only.

Do not use it to restyle:

- dense BI result tables
- visualization-owned widget tables
- chart-adjacent data surfaces

### Output

- shell lists and light tables look cleaner without interfering with visualization work

## Phase 8: Theme Editor Exposure

### Goal

Ensure new shell tokens can be overridden through StyleBI's built-in theme system when intended.

### Tasks

- review all newly added shell tokens
- decide which should be customer-themeable immediately
- add them to the `ThemeCssVariableModel[]` source used by the theme editor
- verify `portalCss` overrides affect the new runtime tokens as expected

### New shell tokens to review for exposure

- `--inet-ui-neutral-color`
- `--inet-ui-neutral-border-color`
- `--inet-ui-neutral-hover-bg-color`
- `--inet-ui-neutral-hover-color`
- `--inet-shell-selected-bg-color`
- `--inet-text-strong-color`
- `--inet-text-muted-color`
- `--inet-text-subtle-color`
- `--inet-meta-font-size`
- `--inet-badge-font-size`
- `--inet-control-height-sm`
- `--inet-control-height-md`
- `--inet-control-height-lg`
- `--inet-row-height-md`
- `--inet-tab-height`
- `--inet-pill-height`
- `--inet-space-1`
- `--inet-space-2`
- `--inet-space-3`
- `--inet-space-4`
- `--inet-space-5`
- `--inet-space-6`
- `--inet-space-8`
- `--inet-radius-sm`
- `--inet-radius-md`
- `--inet-radius-lg`
- `--inet-radius-xl`
- `--inet-radius-pill`
- `--inet-shadow-low`
- `--inet-shadow-medium`
- `--inet-shadow-overlay`
- `--inet-focus-ring-width`
- `--inet-focus-ring-color`
- `--inet-input-focus-bg-color`

### Retuned existing tokens to verify for exposure behavior

- `--inet-text-color`
- `--inet-default-border-color`
- `--inet-input-border-color`
- `--inet-input-bg-color`
- `--inet-input-text-color`
- `--inet-input-disabled-bg-color`
- `--inet-dialog-bg-color`
- `--inet-dialog-title-font-size`
- `--inet-toolbar-bg-color`
- `--inet-vs-toolbar-bg-color`
- `--inet-repository-tree-toolbar-bg-color`
- `--inet-nav-tabs-text-color`
- `--inet-nav-tabs-bg-color`
- `--inet-nav-tabs-selected-border-color`
- `--inet-table-text-color`
- `--inet-table-border-color`
- `--inet-table-heading-text-color`
- `--inet-portal-side-panel-bg-color`
- `--inet-composer-side-panel-bg-color`
- `--inet-button-secondary-*`
- `--inet-button-default-*`

### Output

- shell modernization remains compatible with runtime theming

## Phase 9: Validation

### Functional checks

- primary/secondary/default button behavior
- focus and keyboard visibility
- dialog spacing and modal layout
- tabs/nav active states
- shared toolbar and side-panel consistency
- shell table row/header behavior

### Themeability checks

- default theme still works
- customer theme overrides still apply cleanly
- old token names still behave reasonably if existing themes reference them

### Visual checks

- shell feels quieter and more neutral
- orange is the only routine accent
- Composer authoring states and visualization surfaces still remain visually distinct

### Screen-By-Screen Audit

After the major shared phases land, conduct a screen-by-screen review of representative `portal` surfaces.

The goal is to catch:

- legacy Bootstrap visual treatments that still leak through
- one-off component SCSS that overrides the shared shell layer
- local geometry or spacing assumptions that regress after shared token updates
- surfaces that should have inherited the shell selectors but are still bypassing them

Treat findings from this audit as expected implementation follow-up, not as evidence that the shared-token strategy was wrong.

## Deferred Work

These should not block the first shell implementation phase:

- broad `em` restyling
- Composer-specific authoring-state adoption beyond shared Composer chrome
- visualization density and widget implementation
- exhaustive panel/header normalization for feature-specific components
- removal of all SCSS-only utilities in one pass

## Recommended First Sprint

If implementation begins now, the best first sprint is:

1. Phase 1 foundation tokens
2. Phase 2 button remapping
3. Phase 3 input/focus modernization
4. Phase 4 dialog standardization

That gives a visible shell improvement quickly while keeping scope controlled.

## Appendix: Detailed Implementation Deltas

Use this appendix when implementation needs explicit before/after changes rather than high-level mapping guidance.

### Appendix A: Buttons

#### `_variables.scss` token deltas

| Token | Action | Before | After | Notes |
|---|---|---|---|---|
| `--inet-button-primary-bg-color` | keep/update | `var(--inet-primary-color)` | `var(--inet-primary-color)` | keep hook; value changes indirectly when `--inet-primary-color` is retuned to the shell primary |
| `--inet-button-primary-text-color` | keep | `white` | `white` | no shell change needed |
| `--inet-button-primary-border-color` | keep/update | `var(--inet-primary-color)` | `var(--inet-primary-color)` | keep hook; follows retuned primary |
| `--inet-button-primary-hover-bg-color` | keep/update | `var(--inet-primary-color-dark)` | `var(--inet-primary-color-dark)` | keep hook; follows retuned primary hover |
| `--inet-button-primary-hover-text-color` | keep | `white` | `white` | no shell change needed |
| `--inet-button-primary-hover-border-color` | keep/update | `var(--inet-primary-color-dark)` | `var(--inet-primary-color-dark)` | keep hook; follows retuned primary hover |
| `--inet-button-secondary-bg-color` | update | `var(--inet-secondary-color)` | `transparent` | remove routine teal fill |
| `--inet-button-secondary-text-color` | update | `white` | `var(--inet-ui-neutral-color)` | neutral outline text/icon color |
| `--inet-button-secondary-border-color` | update | `var(--inet-secondary-color)` | `var(--inet-ui-neutral-border-color)` | neutral outline border |
| `--inet-button-secondary-hover-bg-color` | update | `var(--inet-secondary-color-dark)` | `var(--inet-ui-neutral-hover-bg-color)` | neutral hover fill |
| `--inet-button-secondary-hover-text-color` | update | `white` | `var(--inet-ui-neutral-hover-color)` | stronger neutral hover text |
| `--inet-button-secondary-hover-border-color` | update | `var(--inet-secondary-color-dark)` | `var(--inet-ui-neutral-border-color)` | keep border neutral on hover |
| `--inet-button-default-bg-color` | keep | `transparent` | `transparent` | remains ghost-like |
| `--inet-button-default-text-color` | update | `var(--inet-secondary-color)` | `var(--inet-ui-neutral-color)` | remove routine teal text |
| `--inet-button-default-border-color` | update | `var(--inet-secondary-color)` | `transparent` | default shell ghost button should not keep a persistent teal outline |
| `--inet-button-default-hover-bg-color` | update | `var(--inet-secondary-color)` | `var(--inet-ui-neutral-hover-bg-color)` | neutral hover fill |
| `--inet-button-default-hover-text-color` | update | `var(--inet-secondary-color-light)` | `var(--inet-ui-neutral-hover-color)` | stronger neutral hover text |
| `--inet-button-default-hover-border-color` | update | `var(--inet-secondary-color)` | `transparent` | keep ghost behavior on hover |
| `--inet-ui-neutral-color` | add | missing | `var(--inet-text-muted-color)` | resolves to shell neutral control text |
| `--inet-ui-neutral-border-color` | add | missing | `var(--inet-default-border-color)` | resolves to shell neutral border |
| `--inet-ui-neutral-hover-bg-color` | add | missing | `#ECE9E2` | shell neutral hover fill |
| `--inet-ui-neutral-hover-color` | add | missing | `var(--inet-text-color)` | stronger neutral hover text/icon color |
| `--inet-control-height-md` | add | missing | `30px` | shared default shell control height |
| `--inet-radius-md` | add | missing | `3px` | shared default shell control radius |

#### `_bootstrap-override.scss` selector/property deltas

| Selector | Property | Action | Before | After | Notes |
|---|---|---|---|---|---|
| shared `.btn.btn-primary, .btn.btn-secondary, .btn.btn-default` base rule | `display` | add | missing | `inline-flex` | align button content consistently |
| shared `.btn.btn-primary, .btn.btn-secondary, .btn.btn-default` base rule | `align-items` | add | missing | `center` | vertical centering |
| shared `.btn.btn-primary, .btn.btn-secondary, .btn.btn-default` base rule | `justify-content` | add | missing | `center` | horizontal centering |
| shared `.btn.btn-primary, .btn.btn-secondary, .btn.btn-default` base rule | `min-height` | add | missing | `var(--inet-control-height-md)` | shell control height adoption |
| shared `.btn.btn-primary, .btn.btn-secondary, .btn.btn-default` base rule | `padding` | add/update | inherited Bootstrap padding | `0 var(--inet-space-5)` | shell horizontal padding; use explicit fallback value if spacing token is not yet added |
| shared `.btn.btn-primary, .btn.btn-secondary, .btn.btn-default` base rule | `border-radius` | update | inherited Bootstrap/default | `var(--inet-radius-md)` | shell control radius adoption |
| shared `.btn.btn-primary, .btn.btn-secondary, .btn.btn-default` base rule | `font-size` | add/update | inherited | `var(--inet-font-size-base)` | align button text with shell control type |
| shared `.btn.btn-primary, .btn.btn-secondary, .btn.btn-default` base rule | `font-weight` | add/update | inherited | `500` | use shell control emphasis consistently |
| shared `.btn.btn-primary, .btn.btn-secondary, .btn.btn-default` base rule | `line-height` | add/update | inherited | `1` | keeps button height visually crisp |
| `.btn.btn-primary` | color/background/border bindings | keep | already token-driven | no change | variant should continue consuming primary token family |
| `.btn.btn-secondary` | color/background/border bindings | keep/update | already token-driven | no change | main change happens through remapped tokens |
| `.btn.btn-default` | color/background/border bindings | keep/update | already token-driven | no change | main change happens through remapped tokens |
| `.show > .btn-default.dropdown-toggle` | color/background/border bindings | update | follows old teal hover behavior | `background-color: var(--inet-button-default-hover-bg-color); color: var(--inet-button-default-hover-text-color); border-color: var(--inet-button-default-hover-border-color)` | keep dropdown toggle aligned with default button hover state |

### Appendix B: Inputs And Selects

#### `_variables.scss` token deltas

| Token | Action | Before | After | Notes |
|---|---|---|---|---|
| `--inet-input-bg-color` | update | `transparent` | `#FFFFFF` | match shell `surface-default` for inputs |
| `--inet-input-text-color` | update | `#495057` | `var(--inet-text-color)` | align input text with shell default text |
| `--inet-input-border-color` | update | `#E0E0E0` via `$inet-gray-border` | `var(--inet-default-border-color)` | keep hook; align to shell border |
| `--inet-input-focus-text-color` | keep/update | `var(--inet-input-text-color)` | `var(--inet-input-text-color)` | keep hook; follows retuned input text |
| `--inet-input-focus-bg-color` | add | missing | `var(--inet-input-bg-color)` | explicit focus background hook |
| `--inet-input-disabled-bg-color` | update | `#E0E0E0` via `$inet-gray1` | `#F1EFEA` | use subtle neutral disabled fill |
| `--inet-input-disabled-opacity` | keep/update | `0.5` | `0.5` | keep unless disabled contrast needs tuning after implementation |
| `--inet-control-height-md` | add | missing | `30px` | shared default shell control height |
| `--inet-radius-md` | add | missing | `3px` | shared default shell control radius |
| `--inet-focus-ring-width` | add | missing | `2px` | shared shell focus ring width |
| `--inet-focus-ring-color` | add | missing | `rgba(229, 138, 42, 0.28)` | shared shell focus ring color |

#### `_bootstrap-override.scss` selector/property deltas

| Selector | Property | Action | Before | After | Notes |
|---|---|---|---|---|---|
| `.form-floating .form-control:not(textarea)` | `height` | keep | `calc(1.5em + .75rem + 2px)` | `calc(1.5em + .75rem + 2px)` | do not change - calc is locked to `$form-floating-padding-y` and `$form-floating-label-y` SCSS variables; resolves to ~31-34px at StyleBI's 13px font which is close enough to the 30px shell baseline |
| `.form-control, .form-control[readonly], .form-control.input-group-btn-addon, .input-group > .input-group-btn-addon` | `border-color` | keep/update | `var(--inet-input-border-color)` | `var(--inet-input-border-color)` | keep hook; value changes through token retuning |
| same shared input selector | `background-color` | keep/update | `var(--inet-input-bg-color)` | `var(--inet-input-bg-color)` | keep hook; value changes through token retuning |
| same shared input selector | `color` | keep/update | `var(--inet-input-text-color)` | `var(--inet-input-text-color)` | keep hook; value changes through token retuning |
| same shared input selector | `min-height` | add | missing | `var(--inet-control-height-md)` | adopt shell control height across shared inputs |
| same shared input selector | `border-radius` | add/update | inherited Bootstrap/default | `var(--inet-radius-md)` | adopt shell control radius |
| same shared input selector | `padding-inline` | add/update | inherited Bootstrap/default | `var(--inet-space-4)` | use explicit fallback if spacing token is not yet added |
| same shared input selector `:focus` | `border-color` | update | `var(--inet-primary-color-light)` | `var(--inet-primary-color)` | use clearer shell focus border |
| same shared input selector `:focus` | `box-shadow` | update | `0 0 0 1px var(--inet-primary-color)` | `0 0 0 var(--inet-focus-ring-width) var(--inet-focus-ring-color)` | standardize focus ring treatment |
| `.input-group select.form-control:not([size]):not([multiple])` | `height` | update | `unset` | `var(--inet-control-height-md)` | keep selects aligned with shared input height |
| `.input-group-text` | `background-color` | update | `var(--inet-input-disabled-bg-color)` | `var(--inet-toolbar-bg-color)` | make add-ons read as subtle shell surfaces rather than disabled fields |

### Appendix C: Dialogs

#### `_variables.scss` token deltas

| Token | Action | Before | After | Notes |
|---|---|---|---|---|
| `--inet-dialog-bg-color` | update | `var(--inet-main-panel-bg-color)` | `#FFFFFF` | align dialogs to shell `surface-default` |
| `--inet-dialog-title-text-transform` | keep | `none` | `none` | shell dialog titles should not become uppercase |
| `--inet-dialog-title-font-size` | update | `1.3125rem` | `14px` | align shared dialog title size with shell guidance |
| `--inet-radius-xl` | add | missing | `6px` | shared dialog and overlay radius |
| `--inet-shadow-overlay` | add | missing | `0 8px 16px rgba(0,0,0,.12), 0 4px 14px rgba(0,0,0,.08)` | shared overlay elevation |

#### `_bootstrap-override.scss` selector/property deltas

| Selector | Property | Action | Before | After | Notes |
|---|---|---|---|---|---|
| `.modal-content` | `border-radius` | add/update | inherited Bootstrap/default | `var(--inet-radius-xl)` | adopt shell dialog radius |
| `.modal-content` | `box-shadow` | add/update | inherited Bootstrap/default | `var(--inet-shadow-overlay)` | adopt shell overlay elevation |
| `.modal-header, .modal-footer, .modal-body, .modal-content` | `background-color` | keep/update | `var(--inet-dialog-bg-color)` | `var(--inet-dialog-bg-color)` | keep hook; value changes through token retuning |
| same shared modal selector | `border-color` | keep/update | `var(--inet-default-border-color)` | `var(--inet-default-border-color)` | keep hook; aligns automatically with shell border retune |
| `.modal-header` | `padding` | add/update | inherited Bootstrap/default | `14px 16px 12px` | shell dialog header rhythm |
| `.modal-body` | `padding` | add/update | inherited Bootstrap/default | `14px 16px` | shell dialog body rhythm |
| `.modal-footer` | `padding` | add/update | inherited Bootstrap/default | `10px 16px` | shell dialog footer rhythm |
| dialog title element in shared dialog markup | `font-size` | update | inherited/current title sizing | `var(--inet-dialog-title-font-size)` | validate against shared dialog components |
| dialog title element in shared dialog markup | `font-weight` | add/update | inherited/current title weight | `600` | match shell panel/dialog title emphasis |
| `.modal-header .btn-close` | spacing/tone | update | current compact offsets and standard icon treatment | `margin: 0; color: var(--inet-text-muted-color); opacity: 1` | preserve structure while matching shell dialog tone |

### Appendix D: Tabs And Navigation

#### `_variables.scss` token deltas

| Token | Action | Before | After | Notes |
|---|---|---|---|---|
| `--inet-nav-tabs-text-color` | keep/update | `var(--inet-text-color)` | `var(--inet-text-color)` | keep hook; follows shell text retune |
| `--inet-nav-tabs-bg-color` | update | `#F0F0F0` via `$inet-offwhite4` | `#F1EFEA` | align tab rail with shell `surface-subtle` |
| `--inet-nav-tabs-selected-border-color` | keep/update | `var(--inet-primary-color)` | `var(--inet-primary-color)` | keep primary-emphasis rule |
| `--inet-tab-height` | add | missing | `32px` | shared shell tab height |
| `--inet-ui-neutral-hover-bg-color` | add/keep | missing in current file | `#ECE9E2` | neutral hover fill for passive nav items |

#### `_bootstrap-override.scss` and `_themeable.scss` selector/property deltas

| Selector | Property | Action | Before | After | Notes |
|---|---|---|---|---|---|
| `.nav-tabs` | `border-bottom` | keep/update | `1px solid var(--inet-default-border-color)` | `1px solid var(--inet-default-border-color)` | keep hook; border color follows shell retune |
| `.nav-tabs .nav-item.show .nav-link, .nav-tabs .nav-link.active` | `background-color` | update | `var(--inet-dialog-bg-color)` | `var(--inet-main-panel-bg-color)` | use the shared shell content surface for active tabs |
| same active tab selector | `color` | update | `var(--inet-selected-item-text-color)` | `var(--inet-text-color)` | keep active emphasis in border/text rather than selected-state palette |
| `.nav-tabs .nav-link:hover` | `background-color` | add | missing | `var(--inet-ui-neutral-hover-bg-color)` | passive hover should use neutral fill |
| `.nav-tabs .nav-link` | `min-height` | add | missing | `var(--inet-tab-height)` | adopt shared shell tab height |
| `.nav-tabs .nav-link` | `font-size` | add/update | inherited | `var(--inet-font-size-base)` | align tab text with shell control sizing |
| `.portal-sub-navbar, .composer-bottom-tabs, .schedule-navbar, .vpm-navbar, .viewer-bottom-tabs` in [_themeable.scss](E:\home\dev\github\stylebi-visual_BI_tool\stylebi\web\projects\portal\src\scss\_themeable.scss:1271) | `background-color` | keep/update | `var(--inet-nav-tabs-bg-color)` | `var(--inet-nav-tabs-bg-color)` | keep hook; tab-rail tone changes through token retuning |
| same shared nav surfaces | active highlight border | keep/update | `var(--inet-nav-tabs-selected-border-color)` | `var(--inet-nav-tabs-selected-border-color)` | keep hook; selected state remains primary-led |

### Appendix E: Panel Headers And Toolbars

#### `_variables.scss` token deltas

| Token | Action | Before | After | Notes |
|---|---|---|---|---|
| `--inet-toolbar-bg-color` | update | `#FCFCFC` via `$inet-offwhite1` | `#F1EFEA` | align shared toolbars to shell `surface-subtle` |
| `--inet-vs-toolbar-bg-color` | keep/update | `var(--inet-toolbar-bg-color)` | `var(--inet-toolbar-bg-color)` | keep hook; follows toolbar retune |
| `--inet-repository-tree-toolbar-bg-color` | keep/update | `var(--inet-toolbar-bg-color)` | `var(--inet-toolbar-bg-color)` | keep hook; follows toolbar retune |
| `--inet-portal-side-panel-bg-color` | update | fallback `#F9F9F9` via `$inet-offwhite2` | `#F1EFEA` | align side surfaces to shell subtle surface |
| `--inet-composer-side-panel-bg-color` | update | fallback `white` | `#F1EFEA` | shared Composer chrome should match shell subtle surface |

#### `_themeable.scss` selector/property deltas

| Selector | Property | Action | Before | After | Notes |
|---|---|---|---|---|---|
| `.viewer-toolbar` | `background-color` | keep/update | `var(--inet-vs-toolbar-bg-color)` | `var(--inet-vs-toolbar-bg-color)` | keep hook; token retune does most of the work |
| `.repository-tree-toolbar` | `background-color` | keep/update | `var(--inet-repository-tree-toolbar-bg-color)` | `var(--inet-repository-tree-toolbar-bg-color)` | keep hook; token retune does most of the work |
| `.data-model-toolbar, .data-model-header-pane, .physical-graph-toolbar, .explorer-toolbar, .logical-hierarchy-edit-toolbar, .ws-table-bar, .ws-details-table-button, .ws-composite-table-breadcrumb-container, .slide-out-toggle-container, .slide-out-wrapper .v-resizer, .wizard-aggregate-toolbar` | `background-color` | keep/update | `var(--inet-toolbar-bg-color)` | `var(--inet-toolbar-bg-color)` | normalize through the shared toolbar token rather than per-feature colors |
| `.composer-bottom-tabs` | `min-height` | update | `33px` | `var(--inet-tab-height)` | align bottom tabs with shared shell tab height |
| `.slide-out-content-container, .slide-out-toggle-container` | border treatment | update | utility-class extension based | `border-right: 1px solid var(--inet-default-border-color)` on left-aligned slide-outs; `border-left: 1px solid var(--inet-default-border-color)` on right-aligned slide-outs | move toward token-backed borders rather than utility inheritance where practical |
| header-like shared selectors such as `.vs-header` | typography and spacing | update | mixed inherited/current values | `font-size: 14px; font-weight: 600; padding: var(--inet-space-4) var(--inet-space-5)` | survey exact selectors before implementation, but keep changes in shared selectors only and only where those selectors own padding |

### Appendix F: Shell Tables And Lists

#### `_variables.scss` token deltas

| Token | Action | Before | After | Notes |
|---|---|---|---|---|
| `--inet-table-text-color` | keep/update | `var(--inet-text-color)` | `var(--inet-text-color)` | keep hook; follows shell text retune |
| `--inet-table-border-color` | keep/update | `var(--inet-default-border-color)` | `var(--inet-default-border-color)` | keep hook; follows shell border retune |
| `--inet-table-heading-text-color` | update | `#495057` | `var(--inet-text-muted-color)` | use muted shell text for table headers |
| `--inet-row-height-md` | add | missing | `34px` | shared shell row height |
| `--inet-ui-neutral-hover-bg-color` | add/keep | missing in current file | `#ECE9E2` | neutral hover fill for shell rows/lists |
| `--inet-shell-selected-bg-color` | add | missing | `#E7E2D8` | neutral selected fill for shell lists and light tables |

#### `_bootstrap-override.scss` selector/property deltas

| Selector | Property | Action | Before | After | Notes |
|---|---|---|---|---|---|
| `.table-bordered` | `border` | keep/update | `$table-border-width solid var(--inet-table-border-color)` | `$table-border-width solid var(--inet-table-border-color)` | border tone changes through token retuning |
| `.table-bordered th, .table-bordered td` | `border` | keep/update | `$table-border-width solid var(--inet-table-border-color)` | `$table-border-width solid var(--inet-table-border-color)` | preserve shared selector coverage |
| `.table-sm>:not(caption)>*>*` | `padding` | update | `0.3rem` | `0.375rem` | tune padding so rows land near `32-34px` |
| shared shell table header selector | `font-size` | add/update | inherited/current | `var(--inet-font-size-base)` | align with shell typography guidance |
| shared shell table header selector | `font-weight` | add/update | inherited/current | `600` | make shell headers clear without becoming BI-grid headers |
| shared shell row hover selector | `background-color` | add/update | inherited/current Bootstrap hover behavior | `var(--inet-ui-neutral-hover-bg-color)` | use neutral shell hover, not visualization selection color |
| shared shell row selected selector | `background-color` | add/update | current selected-item behavior where applicable | `var(--inet-shell-selected-bg-color)` | do not borrow visualization or worksheet state styling |

## Appendix G: UI Impact Analysis

This appendix maps the roadmap back to the current `stylebi` portal codebase so implementation can distinguish broad shared changes from narrower surface tuning.

### Highest-risk shared changes

| Area | Why risk is high | Primary implementation files | Major UI areas affected |
|---|---|---|---|
| inputs and selects | shared `form-control` styling is used extremely broadly across dialogs, property panes, and editors | [_bootstrap-override.scss](E:\home\dev\github\stylebi-visual_BI_tool\stylebi\web\projects\portal\src\scss\_bootstrap-override.scss:456), [_variables.scss](E:\home\dev\github\stylebi-visual_BI_tool\stylebi\web\projects\portal\src\scss\_variables.scss:239) | Composer property dialogs, binding editors, wizard forms, settings panes |
| default and secondary buttons | shared variant selectors are centralized, but the app has a very large installed base of `btn-default` usage | [_bootstrap-override.scss](E:\home\dev\github\stylebi-visual_BI_tool\stylebi\web\projects\portal\src\scss\_bootstrap-override.scss:601), [_variables.scss](E:\home\dev\github\stylebi-visual_BI_tool\stylebi\web\projects\portal\src\scss\_variables.scss:265) | dialog footers, inline editor actions, chart and table binding tools, list actions |
| dialogs | shared dialog chrome is reused across a large number of Composer and portal modal flows | [_bootstrap-override.scss](E:\home\dev\github\stylebi-visual_BI_tool\stylebi\web\projects\portal\src\scss\_bootstrap-override.scss:774), [modal-header.component.html](E:\home\dev\github\stylebi-visual_BI_tool\stylebi\web\projects\portal\src\app\widget\modal-header\modal-header.component.html:18), [standard-dialog.component.html](E:\home\dev\github\stylebi-visual_BI_tool\stylebi\web\projects\portal\src\app\widget\standard-dialog\standard-dialog.component.html:17) | VS property dialogs, table/chart binding dialogs, named group dialogs, configuration popups |
| tabs and navigation | shared tab selectors affect both dialog tabs and persistent shell/composer tab rails | [_bootstrap-override.scss](E:\home\dev\github\stylebi-visual_BI_tool\stylebi\web\projects\portal\src\scss\_bootstrap-override.scss:774), [_themeable.scss](E:\home\dev\github\stylebi-visual_BI_tool\stylebi\web\projects\portal\src\scss\_themeable.scss:1271) | dialog tab strips, Composer bottom tabs, viewer bottom tabs, schedule and VPM sub-nav |
| toolbars and side panels | shared toolbar background tokens fan out through many shell-adjacent selectors in `_themeable.scss` | [_themeable.scss](E:\home\dev\github\stylebi-visual_BI_tool\stylebi\web\projects\portal\src\scss\_themeable.scss:1333), [_variables.scss](E:\home\dev\github\stylebi-visual_BI_tool\stylebi\web\projects\portal\src\scss\_variables.scss:197) | viewer toolbar, repository tree, search chrome, data-model header panes, wizard toolbars, slide-outs |

### Impact by phase

| Phase | Primary files | UI impact surface | Risk level | What to watch |
|---|---|---|---|---|
| Phase 1 foundation tokens | [_variables.scss](E:\home\dev\github\stylebi-visual_BI_tool\stylebi\web\projects\portal\src\scss\_variables.scss:142) | broad shell text, borders, toolbar surfaces, tab rails | high visual spread, lower implementation complexity | contrast drift, hierarchy changes, unintended shell-wide color shifts |
| Phase 2 button remap | [_bootstrap-override.scss](E:\home\dev\github\stylebi-visual_BI_tool\stylebi\web\projects\portal\src\scss\_bootstrap-override.scss:601), [_variables.scss](E:\home\dev\github\stylebi-visual_BI_tool\stylebi\web\projects\portal\src\scss\_variables.scss:265) | buttons throughout dialogs and editors | high | neutral secondary/default buttons becoming too low-contrast or too visually similar |
| Phase 3 inputs and focus | [_bootstrap-override.scss](E:\home\dev\github\stylebi-visual_BI_tool\stylebi\web\projects\portal\src\scss\_bootstrap-override.scss:456), [_variables.scss](E:\home\dev\github\stylebi-visual_BI_tool\stylebi\web\projects\portal\src\scss\_variables.scss:239) | nearly every form-heavy surface | highest | height regressions, clipped text, floating-label alignment, focus visibility |
| Phase 4 dialogs | [_bootstrap-override.scss](E:\home\dev\github\stylebi-visual_BI_tool\stylebi\web\projects\portal\src\scss\_bootstrap-override.scss:774), [modal-header.component.html](E:\home\dev\github\stylebi-visual_BI_tool\stylebi\web\projects\portal\src\app\widget\modal-header\modal-header.component.html:18) | shared modal flows across Composer and portal | high | title wrapping, footer density, close-button alignment, header/body rhythm |
| Phase 5 tabs and navigation | [_bootstrap-override.scss](E:\home\dev\github\stylebi-visual_BI_tool\stylebi\web\projects\portal\src\scss\_bootstrap-override.scss:774), [_themeable.scss](E:\home\dev\github\stylebi-visual_BI_tool\stylebi\web\projects\portal\src\scss\_themeable.scss:1271) | dialog tabs, Composer bottom tabs, shared sub-nav | medium-high | active-state clarity, passive hover tone, shell tabs accidentally picking up Composer-state semantics |
| Phase 6 toolbars and panels | [_themeable.scss](E:\home\dev\github\stylebi-visual_BI_tool\stylebi\web\projects\portal\src\scss\_themeable.scss:1333), [_themeable.scss](E:\home\dev\github\stylebi-visual_BI_tool\stylebi\web\projects\portal\src\scss\_themeable.scss:1373) | viewer, repository, data-model, wizard, slide-out chrome | medium-high | shell surfaces becoming too flat, toolbar groupings losing hierarchy, side-panel framing becoming too weak |
| Phase 7 shell tables and lists | [_bootstrap-override.scss](E:\home\dev\github\stylebi-visual_BI_tool\stylebi\web\projects\portal\src\scss\_bootstrap-override.scss:583) | shell lists and light tables | medium | selected and hover states becoming too subtle or colliding with visualization-owned table semantics |

### Concrete high-use UI touchpoints

| Touchpoint | Evidence in codebase | Why it matters |
|---|---|---|
| shared dialog header | [modal-header.component.html](E:\home\dev\github\stylebi-visual_BI_tool\stylebi\web\projects\portal\src\app\widget\modal-header\modal-header.component.html:18) is used across about 151 dialog templates | shell dialog changes will be seen almost immediately across Composer and portal |
| default action buttons | `btn-default` appears broadly in dialog and editor templates | the neutralization of default actions will change the feel of most modal and inline action rows |
| form controls | `form-control` is pervasive in binding and VS property panes | input sizing and focus changes will have the broadest practical regression surface |
| dialog tabs | many VS property dialogs use `.nav-tabs`, for example [tab-property-dialog.component.html](E:\home\dev\github\stylebi-visual_BI_tool\stylebi\web\projects\portal\src\app\composer\dialog\vs\tab-property-dialog.component.html:23) and [text-property-dialog.component.html](E:\home\dev\github\stylebi-visual_BI_tool\stylebi\web\projects\portal\src\app\composer\dialog\vs\text-property-dialog.component.html:22) | tab-state tuning must work for repeated dialog patterns, not just shell sub-nav |
| Composer bottom tabs | [data-editor-tab-pane.component.html](E:\home\dev\github\stylebi-visual_BI_tool\stylebi\web\projects\portal\src\app\binding\widget\binding-tree\data-editor-tab-pane.component.html:20) uses `.composer-bottom-tabs` plus the highlight helpers targeted in Phase 5 | bottom-tab sizing and active-border changes will be highly visible in authoring workflows |
| toolbar family | [_themeable.scss](E:\home\dev\github\stylebi-visual_BI_tool\stylebi\web\projects\portal\src\scss\_themeable.scss:1373) groups data-model, explorer, worksheet details, slide-outs, and wizard aggregate toolbar surfaces together | one token or selector change can alter multiple authoring surfaces at once |

### Recommended regression sweep

After implementation, visually verify at minimum:

- one property dialog with tabs, such as a VS property dialog
- one wizard flow using shared toolbar chrome
- one repository/search screen
- one binding editor pane with dense form controls
- one Composer bottom-tab surface
- one shell list or light table using shared `list-group-item` or Bootstrap table selectors
- any screens where legacy Bootstrap component styling is known to have lingered historically
