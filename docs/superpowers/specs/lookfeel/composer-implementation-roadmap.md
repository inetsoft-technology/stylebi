# StyleBI Composer Implementation Roadmap

## Purpose

This document translates the Composer palette and layering strategy into an implementation sequence for the current `portal` codebase, with shared Composer authoring surfaces as the execution target.

It is intended to answer:

- what Composer should inherit from the shell first
- which authoring states should be promoted into shared Composer tokens
- which worksheet-specific states should remain narrower extensions
- which files are the primary targets
- which changes are token work versus adoption work versus containment work
- what should be deferred until shell work is stable

This roadmap assumes the shell token and shared-shell adoption work are already underway or complete. It should not be used to replace the shell roadmap.

Use [composer-design-spec.md](E:\home\dev\github\lookfeel\specs\composer-design-spec.md) for Composer layer behavior, surface rules, and state-usage boundaries.

## Shell Prerequisite

This roadmap covers:

- shared Composer token work layered on top of shell foundations
- shared Composer chrome adoption where authoring surfaces still rely on shell structure first
- shared authoring-state adoption across worksheet, viewsheet, wizard, and related editing surfaces
- worksheet-specific containment work where Composer still needs narrower graph, schema, or detail-state language

This roadmap does not redefine:

- shell neutral surfaces, spacing, radius, focus, and routine control hierarchy
- portal-only shell chrome
- visualization widget internals
- broad EM alignment

For shell-first implementation work such as buttons, inputs, dialogs, nav, tabs, toolbars, and table/list chrome, use [shell-implementation-roadmap.md](E:\home\dev\github\lookfeel\shell-implementation-roadmap.md).

## Related Specs

- [theme-strategy-overview.md](E:\home\dev\github\lookfeel\theme-strategy-overview.md)
- [shell-design-spec.md](E:\home\dev\github\lookfeel\shell-design-spec.md)
- [composer-design-spec.md](E:\home\dev\github\lookfeel\specs\composer-design-spec.md)
- [shell-implementation-roadmap.md](E:\home\dev\github\lookfeel\shell-implementation-roadmap.md)
- [composer-palette-spec.md](E:\home\dev\github\lookfeel\composer-palette-spec.md)

## Delivery Model

Use three change types throughout this roadmap:

- `token`
  - define or refine runtime `--inet-composer-*` variables and narrower retained worksheet aliases
- `adoption`
  - update selectors so Composer surfaces consume shell and Composer tokens consistently
- `exposure`
  - add new Composer-facing runtime tokens to the theme editor model when customer override is required
- `containment`
  - keep worksheet-only states narrow so they do not leak back into shared Composer chrome or shell chrome

## Value Source Rule

Unless otherwise noted:

- shared shell values come from [shell-design-spec.md](E:\home\dev\github\lookfeel\shell-design-spec.md) and [shell-implementation-roadmap.md](E:\home\dev\github\lookfeel\shell-implementation-roadmap.md)
- Composer behavior and state-usage rules come from [composer-design-spec.md](E:\home\dev\github\lookfeel\specs\composer-design-spec.md)
- Composer state values come from [composer-palette-spec.md](E:\home\dev\github\lookfeel\composer-palette-spec.md)

Inline `Default / alias` values in this roadmap are meant to make implementation self-sufficient. When a row needs special clarification, the source should be called out explicitly in that row or subsection.

## Themeability Mechanism

Composer runs inside the same runtime-themeable `portal` CSS system as the shell.

That means:

- Composer tokens should be backed by runtime `--inet-*` variables
- customer themes should continue to override those variables without code changes
- implementation should prefer aliasing and retuning existing hooks before inventing many new names

### Themeability rule

If a Composer change is meant to remain runtime-themeable, do not stop at a one-off selector rewrite.

Use this sequence:

1. define or alias the runtime token in [_variables.scss](E:\home\dev\github\stylebi-visual_BI_tool\stylebi\web\projects\portal\src\scss\_variables.scss:1)
2. adopt that token in [_themeable.scss](E:\home\dev\github\stylebi-visual_BI_tool\stylebi\web\projects\portal\src\scss\_themeable.scss:1) or, where needed, [_bootstrap-override.scss](E:\home\dev\github\stylebi-visual_BI_tool\stylebi\web\projects\portal\src\scss\_bootstrap-override.scss:1)
3. keep old token names working where customer themes may already reference them

### Theme editor model note

The list of variables surfaced in the theme editor is driven by `ThemeCssVariableModel[]`.

When a new Composer token is intended to be customer-themeable, it must also be added there.

### Themeability boundary

Composer work should avoid redefining shell foundations when a shell token already expresses the correct meaning.

Do not add Composer-specific replacements for:

- shell surface tokens used by shared dialogs and forms
- shell neutral control tokens used by standard buttons and inputs
- shell nav and tab structure when the surface is still ordinary chrome
- shell semantic tokens for success, warning, error, and info

## Primary Code Targets

| File / area | Main responsibility |
|---|---|
| [web/projects/portal/src/scss/_variables.scss](E:\home\dev\github\stylebi-visual_BI_tool\stylebi\web\projects\portal\src\scss\_variables.scss:1) | define and alias shared `--inet-composer-*` tokens and any retained `--inet-ws-*`, `--inet-schema-*`, and `--inet-graph-*` extensions |
| [web/projects/portal/src/scss/_themeable.scss](E:\home\dev\github\stylebi-visual_BI_tool\stylebi\web\projects\portal\src\scss\_themeable.scss:1) | adopt shell and Composer tokens in shared Composer chrome, worksheet panes, graph states, viewsheet editing states, and related utilities |
| [web/projects/portal/src/scss/_bootstrap-override.scss](E:\home\dev\github\stylebi-visual_BI_tool\stylebi\web\projects\portal\src\scss\_bootstrap-override.scss:1) | preserve shell-driven Bootstrap-shaped controls used inside Composer dialogs, forms, tabs, and toolbars |
| shared Composer templates and components in `web/projects/portal/src/app` | targeted follow-up only when shared SCSS adoption is not enough |
| theme editor model in EM | expose new themeable `--inet-*` Composer variables when customer control is required |

## Composer Surface Implementation Map

Use [composer-design-spec.md](E:\home\dev\github\lookfeel\specs\composer-design-spec.md) for the surface model and state-distribution rules.

This roadmap keeps only the implementation-facing mapping:

- shared Composer chrome work is primarily in [_variables.scss](E:\home\dev\github\stylebi-visual_BI_tool\stylebi\web\projects\portal\src\scss\_variables.scss:1), [_themeable.scss](E:\home\dev\github\stylebi-visual_BI_tool\stylebi\web\projects\portal\src\scss\_themeable.scss:1), and [_bootstrap-override.scss](E:\home\dev\github\stylebi-visual_BI_tool\stylebi\web\projects\portal\src\scss\_bootstrap-override.scss:1)
- shared authoring-state rollout is primarily in [_variables.scss](E:\home\dev\github\stylebi-visual_BI_tool\stylebi\web\projects\portal\src\scss\_variables.scss:1) and [_themeable.scss](E:\home\dev\github\stylebi-visual_BI_tool\stylebi\web\projects\portal\src\scss\_themeable.scss:1)
- worksheet extension containment is primarily in [_variables.scss](E:\home\dev\github\stylebi-visual_BI_tool\stylebi\web\projects\portal\src\scss\_variables.scss:1) and [_themeable.scss](E:\home\dev\github\stylebi-visual_BI_tool\stylebi\web\projects\portal\src\scss\_themeable.scss:1)
- viewsheet and wizard adoption primarily reuses the shared authoring-state rollout rather than introducing separate token systems

Like the shell implementation, Composer work should include screen-by-screen review after major shared phases land. Shared token and selector adoption will not catch every legacy Bootstrap-era treatment, worksheet-local override, or authoring-specific leakage point on its own.

## Current Code Touchpoints

The current shell code already exposes some Composer-specific and worksheet-specific hooks:

- Composer surface tokens in [_variables.scss](E:\home\dev\github\stylebi-visual_BI_tool\stylebi\web\projects\portal\src\scss\_variables.scss:1)
  - `--inet-composer-main-panel-bg-color`
  - `--inet-composer-side-panel-bg-color`
  - `--inet-composer-navbar-text-color`
  - `--inet-composer-navbar-bg-color`
  - `--inet-composer-navbar-hover-text-color`
  - `--inet-composer-navbar-hover-bg-color`
- worksheet and graph-state tokens in [_variables.scss](E:\home\dev\github\stylebi-visual_BI_tool\stylebi\web\projects\portal\src\scss\_variables.scss:1)
  - `--inet-graph-assembly-*`
  - `--inet-graph-connection-*`
  - `--inet-schema-*`
  - `--inet-ws-*`
- shared selected and background helpers in [_themeable.scss](E:\home\dev\github\stylebi-visual_BI_tool\stylebi\web\projects\portal\src\scss\_themeable.scss:1)
  - `.bg-selected`
  - `.bg-bold-selected`
  - `.bd-selected-*`
  - `.bt-selected`, `.bb-selected`, `.bl-selected`, `.br-selected`
- shared Composer chrome adoption in [_themeable.scss](E:\home\dev\github\stylebi-visual_BI_tool\stylebi\web\projects\portal\src\scss\_themeable.scss:1)
  - Composer toolbars
  - Composer navbars
  - worksheet graph elements
  - side panes and split-pane gutters

Practical rule:

- reuse and reinterpret existing hooks first
- add new `--inet-composer-*` aliases only where the current names are too worksheet-specific or too legacy to express the broader Composer state model clearly

## Phase 0: Guardrails And Baseline Audit

### Goal

Keep Composer work layered on top of shell work instead of reopening shell decisions, and prevent worksheet-specific styling from spreading into shared Composer chrome.

### Tasks

- treat shell token and shared shell adoption work as the prerequisite
- confirm the first-pass Composer scope is stable:
  - Composer token alignment
  - shared Composer chrome adoption
  - shared authoring-state adoption
  - worksheet-only containment
- identify which selectors are truly shared authoring chrome versus worksheet-only
- identify any active user-owned SCSS changes in the same files before editing
- freeze the first implementation slice to shared Composer adoption in `portal`
- plan a screen-by-screen audit pass after shared chrome adoption and again after shared authoring-state adoption

### Output

- clear boundary between shell adoption and Composer adoption
- known file ownership and risk areas

## Phase 1: Composer Token Alignment

### Goal

Define the shared Composer authoring-state token layer without duplicating shell foundations.

### Primary file

- [web/projects/portal/src/scss/_variables.scss](E:\home\dev\github\stylebi-visual_BI_tool\stylebi\web\projects\portal\src\scss\_variables.scss:1)

### Tasks

#### 0. Existing tokens to reuse first

Before adding many new Composer tokens, reuse and clarify the existing hooks that already anchor Composer chrome and worksheet state.

| Token | Purpose | Before | After | Notes |
|---|---|---|---|---|
| `--inet-composer-main-panel-bg-color` | shared Composer canvas surface | `var(--inet-main-panel-bg-color)` | `var(--inet-main-panel-bg-color)` | keep shell inheritance; no Composer-specific override needed |
| `--inet-composer-side-panel-bg-color` | shared Composer side surface | `var(--inet-side-panel-bg-color, #F8F7F4)` | `var(--inet-toolbar-bg-color)` | align shared Composer chrome to shell subtle surface |
| `--inet-composer-navbar-text-color` | Composer nav text | `var(--inet-navbar-text-color)` | `var(--inet-nav-tabs-text-color)` | align to shell nav text behavior |
| `--inet-composer-navbar-bg-color` | Composer nav rail surface | `var(--inet-navbar-bg-color)` | `var(--inet-nav-tabs-bg-color)` | align to shell tab/nav rail surface |
| `--inet-composer-navbar-hover-text-color` | Composer nav hover text | `var(--inet-navbar-hover-text-color, var(--inet-composer-navbar-text-color))` | `var(--inet-text-color)` | neutral hover emphasis rather than distinct Composer hue |
| `--inet-composer-navbar-hover-bg-color` | Composer nav hover surface | `var(--inet-navbar-hover-bg-color, var(--inet-secondary-color))` | `var(--inet-ui-neutral-hover-bg-color)` | remove accidental teal hover behavior from shared Composer chrome |

Use new Composer tokens only where existing shell or legacy Composer hooks are missing, semantically overloaded, or too worksheet-specific to represent the shared authoring state model clearly.

#### 1. Shared Composer context family

Add shared Composer context tokens for related or in-focus authoring state.

Define in [_variables.scss](E:\home\dev\github\stylebi-visual_BI_tool\stylebi\web\projects\portal\src\scss\_variables.scss:1):

| Token | Action | Before | After | Notes |
|---|---|---|---|---|
| `--inet-composer-context-bg` | add | missing | `#FEF8EE` | related or in-focus authoring surface |
| `--inet-composer-context-border` | add | missing | `#F3DFC2` | contextual border |
| `--inet-composer-context-text` | add | missing | `#8C5510` | text on contextual surfaces |

#### 2. Shared Composer selected family

Add shared Composer selected tokens for explicit authoring selection.

Define in [_variables.scss](E:\home\dev\github\stylebi-visual_BI_tool\stylebi\web\projects\portal\src\scss\_variables.scss:1):

| Token | Action | Before | After | Notes |
|---|---|---|---|---|
| `--inet-composer-selected-bg` | add | missing | `#DDF1F5` | selected authoring surface |
| `--inet-composer-selected-border` | add | missing | `#BFDDE5` | selected border |
| `--inet-composer-selected-text` | add | missing | `#123C44` | text on selected surfaces |
| `--inet-composer-selected-ring` | add | missing | `rgba(167, 215, 227, 0.35)` | selected halo or emphasis ring |

#### 3. Shared Composer primary authoring family

Add Composer aliases for authoring emphasis where shell primary alone is too generic.

Define in [_variables.scss](E:\home\dev\github\stylebi-visual_BI_tool\stylebi\web\projects\portal\src\scss\_variables.scss:1):

| Token | Action | Before | After | Notes |
|---|---|---|---|---|
| `--inet-composer-primary-accent` | add | missing | `#E58A2A` | key authoring affordance; alias shell primary |
| `--inet-composer-primary-hover` | add | missing | `#C96F12` | authoring hover state; alias shell primary-hover |
| `--inet-composer-primary-text` | add | missing | `#4A2500` | text on Composer primary surfaces; alias shell primary-text |
| `--inet-composer-primary-soft` | add | missing | `#F6E2C8` | soft primary authoring surface; alias shell primary-soft |

#### 4. Shared Composer dimmed family

Add Composer dimmed tokens for inactive or unavailable authoring state.

Define in [_variables.scss](E:\home\dev\github\stylebi-visual_BI_tool\stylebi\web\projects\portal\src\scss\_variables.scss:1):

| Token | Action | Before | After | Notes |
|---|---|---|---|---|
| `--inet-composer-dimmed-bg` | add | missing | `#FBFAF7` | dimmed surface |
| `--inet-composer-dimmed-border` | add | missing | `#E6E2D9` | dimmed border |
| `--inet-composer-dimmed-text` | add | missing | `#B8B3AA` | dimmed text |

#### 5. Worksheet extension retuning

Retain worksheet-specific tokens only where the meaning is narrower than shared Composer authoring state.

Define or refine in [_variables.scss](E:\home\dev\github\stylebi-visual_BI_tool\stylebi\web\projects\portal\src\scss\_variables.scss:1):

| Token | Action | Before | After | Notes |
|---|---|---|---|---|
| `--inet-graph-assembly-bg-color` | update | legacy off-white fallback | `var(--inet-shell-surface-default, var(--inet-main-panel-bg-color))` | graph nodes should start from shell surface, not legacy tint |
| `--inet-graph-assembly-header-selected-bg-color` | update | `var(--inet-selected-item-bg-color)` | `var(--inet-composer-selected-bg)` | explicit authoring selection should use Composer selected family |
| `--inet-graph-assembly-header-selected-text-color` | update | `var(--inet-selected-item-text-color)` | `var(--inet-composer-selected-text)` | keep selected text aligned with Composer selected family |
| `--inet-graph-connection-color` | update | legacy gray | `#5E5D57` | default connection line |
| `--inet-graph-connection-warning-color` | update | legacy primary-light mapping | `#EEA555` | warning connection should align with Composer primary/warning-adjacent emphasis |
| `--inet-schema-connection-color` | update | legacy gray | `#BDBDBD` | schema editor connection line |
| `--inet-schema-column-connected-bg-color` | update | `var(--inet-fourth-color)` | `#E2F3E8` | connected schema state |
| `--inet-schema-column-compatible-bg-color` | update | `var(--inet-secondary-color-light)` | `#DDF1F5` | compatible schema state aligns with Composer selected family tone |
| `--inet-schema-column-incompatible-bg-color` | update | legacy primary-light mapping | `#F6E2C8` | incompatible schema state uses warm authoring emphasis rather than shell warning |
| `--inet-ws-table-odd-row-bg-color` | update | `#fff` | `#FFFFFF` | normalize explicit row token |
| `--inet-ws-table-even-row-bg-color` | update | `#f2f2f2` | `#F7F6F2` | softer worksheet row alternation |

### Rules

- prefer additive aliases first
- keep shell surfaces, text, focus, radius, and spacing inherited rather than redefined
- keep old token names working where possible
- do not promote worksheet-only states into shared Composer tokens unless the meaning is truly shared across worksheet, viewsheet, and wizard

### Output

- stable shared Composer token surface layered on top of shell

## Phase 2: Shared Composer Chrome Adoption

### Goal

Make shared Composer chrome consume shell rules consistently before adding state styling.

### Primary files

- [web/projects/portal/src/scss/_themeable.scss](E:\home\dev\github\stylebi-visual_BI_tool\stylebi\web\projects\portal\src\scss\_themeable.scss:1)
- [web/projects/portal/src/scss/_bootstrap-override.scss](E:\home\dev\github\stylebi-visual_BI_tool\stylebi\web\projects\portal\src\scss\_bootstrap-override.scss:1)

### Tasks

#### 1. Known deltas

Use shell tokens for shared chrome and remove accidental worksheet or secondary-accent behavior.

| Selector / area | Property | Action | Before | After | Notes |
|---|---|---|---|---|---|
| shared Composer navbar selectors in `_themeable.scss` | `background-color` | keep/update | `var(--inet-composer-navbar-bg-color)` | `var(--inet-composer-navbar-bg-color)` | keep hook; value changes through token retuning |
| same shared Composer navbar selectors | `color` | keep/update | `var(--inet-composer-navbar-text-color)` | `var(--inet-composer-navbar-text-color)` | keep hook; aligns with shell nav text |
| same shared Composer navbar selectors `:hover` | `background-color` | keep/update | `var(--inet-composer-navbar-hover-bg-color)` | `var(--inet-composer-navbar-hover-bg-color)` | keep hook; hover becomes neutral rather than teal |
| same shared Composer navbar selectors `:hover` | `color` | keep/update | `var(--inet-composer-navbar-hover-text-color)` | `var(--inet-composer-navbar-hover-text-color)` | stronger neutral hover text |
| `.composer-bottom-tabs` and related tab rails | `background-color` | keep/update | shell-adjacent tab rail treatment | `var(--inet-nav-tabs-bg-color)` | bottom tabs remain shell-driven chrome |
| same bottom-tab selectors | active border / highlight | keep/update | legacy selected helpers or current tab accent | `var(--inet-nav-tabs-selected-border-color)` | active shell tab emphasis remains primary-led, not Composer-state-led |
| shared toolbars, side panes, and slide-out chrome | `background-color` | keep/update | mixed Composer/worksheet/shell values | `var(--inet-toolbar-bg-color)` or `var(--inet-composer-side-panel-bg-color)` | align shared authoring chrome to shell subtle surface model |
| shared dialogs and form controls used in Composer | shell visual properties | preserve | shell-driven selectors in `_bootstrap-override.scss` | shell-driven selectors in `_bootstrap-override.scss` | do not fork dialog/input styling into a Composer mini-theme |

#### 2. Important boundary

The following should remain shell-driven unless the surface is explicitly carrying authoring meaning:

- dialogs and modal footers
- standard form controls and input groups
- generic buttons and icon buttons
- generic tabs and nav strips
- passive side-panel chrome

Composer tokens should not be used just because a surface appears inside Composer.

### Output

- shared Composer chrome reads as shell-aligned authoring UI rather than as a separate mini-theme

### Validation checks

- Composer bottom tabs still read as shell tabs, not selected-object states
- shared side panes and slide-outs feel structurally related to shell toolbars
- dialogs, forms, and button rows in Composer still match shell component standards

## Phase 3: Shared Authoring-State Adoption

### Goal

Apply the shared Composer state grammar across authoring surfaces.

### Primary file

- [web/projects/portal/src/scss/_themeable.scss](E:\home\dev\github\stylebi-visual_BI_tool\stylebi\web\projects\portal\src\scss\_themeable.scss:1)

### Tasks

#### 1. Selected-state adoption

Use the Composer selected family for explicit authoring selection rather than shell list selection.

| Selector / area | Property | Action | Before | After | Notes |
|---|---|---|---|---|---|
| `.bg-selected` where used for authoring targets | `background-color` | update | `var(--inet-shell-selected-bg-color)` | `var(--inet-composer-selected-bg)` | only where selector usage is true authoring selection |
| `.bd-selected`, `.bd-selected-*`, `.bt-selected`, `.bb-selected`, `.bl-selected`, `.br-selected` where used for authoring targets | border color | update | `var(--inet-selected-item-bg-color)` | `var(--inet-composer-selected-border)` | preserve shell selection for shell lists; reinterpret only authoring usage |
| selected graph/node header selectors | `background-color` | keep/update | current selected-item mapping | `var(--inet-composer-selected-bg)` | selected node/header should use shared Composer selection |
| selected graph/node header selectors | `color` | keep/update | current selected-item mapping | `var(--inet-composer-selected-text)` | selected text should stay readable on cool selected fill |
| selected authoring surfaces needing stronger emphasis | `box-shadow` / ring | add | missing or mixed | `0 0 0 2px var(--inet-composer-selected-ring)` | use ring only where fill/border is not enough |

#### 2. Context-state adoption

Use Composer context for related or in-focus state rather than generic highlight.

| Selector / area | Property | Action | Before | After | Notes |
|---|---|---|---|---|---|
| related worksheet or authoring-surface helpers | `background-color` | add/update | mixed hover or legacy warm fills | `var(--inet-composer-context-bg)` | context is not the same as explicit selection |
| same context selectors | `border-color` | add/update | mixed or missing | `var(--inet-composer-context-border)` | contextual containment |
| same context selectors | `color` | add/update | inherited or mixed | `var(--inet-composer-context-text)` | contextual text emphasis |

#### 3. Primary authoring emphasis adoption

Use Composer primary sparingly for key authoring emphasis without replacing shell primary globally.

| Selector / area | Property | Action | Before | After | Notes |
|---|---|---|---|---|---|
| key authoring affordances inside editor surfaces | `background-color` | add/update | mixed shell primary or worksheet-specific warm fills | `var(--inet-composer-primary-soft)` or `var(--inet-composer-primary-accent)` | choose soft or stronger treatment based on density |
| same key authoring affordances | `color` | add/update | mixed | `var(--inet-composer-primary-text)` | text on soft primary surfaces |
| same key authoring affordances `:hover` | hover tone | add/update | mixed | `var(--inet-composer-primary-hover)` where needed | do not rewrite standard shell primary buttons |

#### 4. Dimmed-state adoption

Use Composer dimmed states for inactive or unavailable authoring surfaces.

| Selector / area | Property | Action | Before | After | Notes |
|---|---|---|---|---|---|
| inactive graph nodes, unavailable authoring targets, or muted editing regions | `background-color` | add/update | mixed pale neutrals | `var(--inet-composer-dimmed-bg)` | dimmed surfaces should remain readable |
| same dimmed selectors | `border-color` | add/update | mixed | `var(--inet-composer-dimmed-border)` | keep borders softer than active state |
| same dimmed selectors | `color` | add/update | mixed | `var(--inet-composer-dimmed-text)` | mute text without losing legibility |

### Output

- a shared authoring-state model across worksheet, viewsheet, and wizard surfaces

### Validation checks

- explicit selection reads consistently across worksheet, viewsheet, and wizard
- context state remains visually distinct from selection
- dimmed state lowers emphasis without looking disabled unless the state is truly unavailable
- Composer primary emphasis does not start competing with routine shell primary actions

## Phase 4: Worksheet Extension Containment

### Goal

Keep worksheet-specific states precise and narrow.

### Primary file

- [web/projects/portal/src/scss/_themeable.scss](E:\home\dev\github\stylebi-visual_BI_tool\stylebi\web\projects\portal\src\scss\_themeable.scss:1)

### Tasks

#### 1. Known deltas

Map schema compatibility, graph connection, and detail-row states to retained worksheet token families only where the meaning is truly worksheet-specific.

| Selector / area | Property | Action | Before | After | Notes |
|---|---|---|---|---|---|
| schema connected-column selectors | `background-color` | keep/update | legacy schema token mapping | `var(--inet-schema-column-connected-bg-color)` | keep worksheet-specific meaning |
| schema compatible-column selectors | `background-color` | keep/update | legacy schema token mapping | `var(--inet-schema-column-compatible-bg-color)` | compatible is still worksheet-specific even though it visually aligns with Composer selected family |
| schema incompatible-column selectors | `background-color` | keep/update | legacy schema token mapping | `var(--inet-schema-column-incompatible-bg-color)` | keep warm incompatibility surface narrow to worksheet |
| graph connection selectors | `stroke` / line color | keep/update | current graph connection hooks | `var(--inet-graph-connection-color)` | worksheet-only graph meaning |
| graph warning connection selectors | `stroke` / line color | keep/update | current warning connection hooks | `var(--inet-graph-connection-warning-color)` | worksheet-only warning connection meaning |
| schema connection selectors | `stroke` / line color | keep/update | current schema connection hook | `var(--inet-schema-connection-color)` | separate from general selected or context borders |
| worksheet detail-row odd/even selectors | `background-color` | keep/update | current row tokens | `var(--inet-ws-table-odd-row-bg-color)` and `var(--inet-ws-table-even-row-bg-color)` | keep worksheet row rhythm narrow to detail tables |
| worksheet detail-row selected selectors | `background-color` | add/update | mixed selected-item or table-selected behavior | `var(--inet-ws-row-selected-bg)` | use the dedicated worksheet selected-row token from the Composer palette spec; do not reuse shell table selected state |

#### 2. Important boundary

The following should not use worksheet-only tokens:

- shared Composer side panes
- pane tabs and nav
- generic dialogs
- shared forms and controls
- viewsheet selection states
- wizard recommendations or step emphasis

Worksheet-specific states should stay inside:

- schema compatibility visuals
- graph connections and assemblies
- worksheet detail rows and table-specific authoring states

### Output

- worksheet remains expressive without fragmenting the broader Composer palette

## Phase 5: Themeability And Exposure Review

### Goal

Confirm the new Composer token layer remains runtime-themeable and backward-compatible enough for existing themes, and expose the right Composer tokens for customer override.

### Tasks

- verify all new shared Composer tokens are defined as runtime `--inet-*` variables
- verify legacy Composer and worksheet hooks still resolve to reasonable values after retuning
- avoid deleting old token names in the same pass
- add any customer-themeable Composer tokens to `ThemeCssVariableModel[]`
- verify exposed Composer tokens are named and grouped clearly enough for customer theming

### New Composer tokens to review for exposure

- `--inet-composer-context-bg`
- `--inet-composer-context-border`
- `--inet-composer-context-text`
- `--inet-composer-selected-bg`
- `--inet-composer-selected-border`
- `--inet-composer-selected-text`
- `--inet-composer-selected-ring`
- `--inet-composer-primary-accent`
- `--inet-composer-primary-hover`
- `--inet-composer-primary-text`
- `--inet-composer-primary-soft`
- `--inet-composer-dimmed-bg`
- `--inet-composer-dimmed-border`
- `--inet-composer-dimmed-text`

### Retuned existing tokens to verify for exposure behavior

- `--inet-composer-side-panel-bg-color`
- `--inet-composer-navbar-text-color`
- `--inet-composer-navbar-bg-color`
- `--inet-composer-navbar-hover-text-color`
- `--inet-composer-navbar-hover-bg-color`
- `--inet-graph-assembly-header-selected-bg-color`
- `--inet-graph-assembly-header-selected-text-color`
- `--inet-schema-column-connected-bg-color`
- `--inet-schema-column-compatible-bg-color`
- `--inet-schema-column-incompatible-bg-color`
- `--inet-graph-connection-color`
- `--inet-graph-connection-warning-color`
- `--inet-schema-connection-color`

### Output

- verified Composer runtime token surface with backward-compatible migration path

## Phase 6: Validation

### Functional checks

- shared Composer dialogs, forms, tabs, and toolbars still align with shell
- selected, context, dimmed, and Composer-primary states are consistent across authoring surfaces
- worksheet graph, schema, and detail states still communicate clearly

### Themeability checks

- new Composer tokens remain runtime-themeable where intended
- old token names still behave reasonably if customer themes reference them
- shell token overrides still flow through shared Composer chrome correctly

### Visual checks

- shared Composer chrome feels shell-aligned rather than like a separate theme
- authoring-state styling appears only where meaning requires it
- worksheet-specific states remain narrower than the overall Composer palette
- viewsheet and wizard use fewer Composer states than worksheet rather than inventing parallel color systems

### Screen-By-Screen Audit

After major shared Composer phases land, conduct a screen-by-screen review of representative authoring surfaces.

The goal is to catch:

- legacy Bootstrap visual treatments still leaking into Composer screens
- one-off worksheet, viewsheet, or wizard overrides that bypass the shared Composer and shell token layers
- shell chrome inside Composer that still carries old accent behavior or older Bootstrap defaults
- authoring-state drift where selected, context, primary, or dimmed usage is inconsistent in real screens

Treat this audit as part of the implementation method, not as optional polish.

## Deferred Work

These should not block the first Composer implementation phase:

- broad component-by-component rewrites where shared SCSS adoption is enough
- viewsheet- or wizard-specific micro-polish beyond shared state adoption
- visualization widget internals
- exhaustive cleanup of all legacy worksheet naming in one pass
- broad EM alignment
- exhaustive screen-by-screen cleanup before shared token and selector phases land

## Recommended First Sprint

If Composer implementation begins after shell stabilization, the best first sprint is:

1. Phase 1 Composer token alignment
2. Phase 2 shared Composer chrome adoption
3. Phase 3 shared authoring-state adoption

That gives a clear layered model before doing narrower worksheet follow-up.

Immediately after that sprint, run a screen-by-screen audit across worksheet, viewsheet, wizard, and shared Composer chrome to identify lingering Bootstrap leakage and local overrides that need targeted cleanup.

## Appendix: Detailed Implementation Deltas

This appendix makes the Composer roadmap implementation-ready by turning design direction into explicit token and selector deltas.

### Appendix A: Shared Composer Chrome

#### `_variables.scss` token deltas

| Token | Action | Before | After | Notes |
|---|---|---|---|---|
| `--inet-composer-side-panel-bg-color` | update | `var(--inet-side-panel-bg-color, #F8F7F4)` | `var(--inet-toolbar-bg-color)` | shared Composer side chrome should use shell subtle surface |
| `--inet-composer-navbar-text-color` | update | `var(--inet-navbar-text-color)` | `var(--inet-nav-tabs-text-color)` | align with shell nav text |
| `--inet-composer-navbar-bg-color` | update | `var(--inet-navbar-bg-color)` | `var(--inet-nav-tabs-bg-color)` | align with shell tab/nav rail |
| `--inet-composer-navbar-hover-text-color` | update | `var(--inet-navbar-hover-text-color, var(--inet-composer-navbar-text-color))` | `var(--inet-text-color)` | neutral hover emphasis |
| `--inet-composer-navbar-hover-bg-color` | update | `var(--inet-navbar-hover-bg-color, var(--inet-secondary-color))` | `var(--inet-ui-neutral-hover-bg-color)` | remove teal hover in shared chrome |

#### `_themeable.scss` and `_bootstrap-override.scss` selector/property deltas

| Selector | Property | Action | Before | After | Notes |
|---|---|---|---|---|---|
| shared Composer navbar selectors | `background-color` | keep/update | `var(--inet-composer-navbar-bg-color)` | `var(--inet-composer-navbar-bg-color)` | hook stays; token meaning changes |
| shared Composer navbar selectors | `color` | keep/update | `var(--inet-composer-navbar-text-color)` | `var(--inet-composer-navbar-text-color)` | hook stays; token meaning changes |
| shared Composer navbar selectors `:hover` | `background-color` | keep/update | `var(--inet-composer-navbar-hover-bg-color)` | `var(--inet-composer-navbar-hover-bg-color)` | neutral shell hover |
| `.composer-bottom-tabs` and related shared tab rails | `background-color` | keep/update | mixed current values | `var(--inet-nav-tabs-bg-color)` | bottom tabs stay shell-driven |
| shared side panes and slide-out chrome | `background-color` | keep/update | mixed current values | `var(--inet-composer-side-panel-bg-color)` | shell subtle surface model |
| shared dialogs/forms/buttons inside Composer | shell visual properties | preserve | current shell selectors | current shell selectors | no Composer-specific fork |

### Appendix B: Shared Authoring States

#### `_variables.scss` token deltas

| Token | Action | Before | After | Notes |
|---|---|---|---|---|
| `--inet-composer-context-bg` | add | missing | `#FEF8EE` | related or in-focus authoring state |
| `--inet-composer-context-border` | add | missing | `#F3DFC2` | contextual border |
| `--inet-composer-context-text` | add | missing | `#8C5510` | contextual text |
| `--inet-composer-selected-bg` | add | missing | `#DDF1F5` | explicit authoring selection |
| `--inet-composer-selected-border` | add | missing | `#BFDDE5` | selected border |
| `--inet-composer-selected-text` | add | missing | `#123C44` | selected text |
| `--inet-composer-selected-ring` | add | missing | `rgba(167, 215, 227, 0.35)` | selected halo |
| `--inet-composer-primary-accent` | add | missing | `#E58A2A` | key authoring emphasis; alias shell primary |
| `--inet-composer-primary-hover` | add | missing | `#C96F12` | key authoring hover; alias shell primary-hover |
| `--inet-composer-primary-text` | add | missing | `#4A2500` | text on soft primary; alias shell primary-text |
| `--inet-composer-primary-soft` | add | missing | `#F6E2C8` | soft primary authoring fill; alias shell primary-soft |
| `--inet-composer-dimmed-bg` | add | missing | `#FBFAF7` | dimmed surface |
| `--inet-composer-dimmed-border` | add | missing | `#E6E2D9` | dimmed border |
| `--inet-composer-dimmed-text` | add | missing | `#B8B3AA` | dimmed text |

#### `_themeable.scss` selector/property deltas

| Selector | Property | Action | Before | After | Notes |
|---|---|---|---|---|---|
| `.bg-selected` in authoring-state usage | `background-color` | update | `var(--inet-shell-selected-bg-color)` | `var(--inet-composer-selected-bg)` | reinterpret only where usage is true authoring selection |
| `.bd-selected`, `.bd-selected-*`, `.bt-selected`, `.bb-selected`, `.bl-selected`, `.br-selected` in authoring-state usage | border color | update | `var(--inet-selected-item-bg-color)` | `var(--inet-composer-selected-border)` | preserve shell selection elsewhere |
| selected authoring targets | `color` | add/update | inherited or mixed | `var(--inet-composer-selected-text)` | explicit selected text |
| selected authoring targets needing stronger emphasis | `box-shadow` | add | missing or mixed | `0 0 0 2px var(--inet-composer-selected-ring)` | use only where fill and border are not enough |
| contextual authoring surfaces | `background-color` | add/update | mixed hover or legacy warm fills | `var(--inet-composer-context-bg)` | context is not selection |
| contextual authoring surfaces | `border-color` | add/update | mixed or missing | `var(--inet-composer-context-border)` | contextual containment |
| contextual authoring surfaces | `color` | add/update | inherited or mixed | `var(--inet-composer-context-text)` | contextual text emphasis |
| key authoring affordances inside editor surfaces | `background-color` | add/update | mixed shell primary or worksheet-specific warm fills | `var(--inet-composer-primary-soft)` or `var(--inet-composer-primary-accent)` | do not replace standard shell primary buttons |
| key authoring affordances inside editor surfaces | `color` | add/update | mixed | `var(--inet-composer-primary-text)` | text on soft primary surfaces |
| key authoring affordances inside editor surfaces `:hover` | hover tone | add/update | mixed | `var(--inet-composer-primary-hover)` where needed | keep hover aligned with Composer primary aliasing |
| dimmed authoring surfaces | `background-color` | add/update | mixed pale neutrals | `var(--inet-composer-dimmed-bg)` | inactive state |
| dimmed authoring surfaces | `border-color` | add/update | mixed | `var(--inet-composer-dimmed-border)` | inactive border |
| dimmed authoring surfaces | `color` | add/update | mixed | `var(--inet-composer-dimmed-text)` | inactive text |

### Appendix C: Worksheet Extensions

#### `_variables.scss` token deltas

| Token | Action | Before | After | Notes |
|---|---|---|---|---|
| `--inet-graph-assembly-bg-color` | update | legacy off-white fallback | `var(--inet-shell-surface-default, var(--inet-main-panel-bg-color))` | graph nodes start from shell surface |
| `--inet-graph-assembly-header-selected-bg-color` | update | `var(--inet-selected-item-bg-color)` | `var(--inet-composer-selected-bg)` | worksheet selection aligns with shared Composer selected family |
| `--inet-graph-assembly-header-selected-text-color` | update | `var(--inet-selected-item-text-color)` | `var(--inet-composer-selected-text)` | worksheet selected text aligns with shared Composer selected family |
| `--inet-graph-connection-color` | update | legacy gray | `#5E5D57` | default graph connection |
| `--inet-graph-connection-warning-color` | update | legacy primary-light mapping | `#EEA555` | warning connection |
| `--inet-schema-connection-color` | update | legacy gray | `#BDBDBD` | schema connection |
| `--inet-schema-column-connected-bg-color` | update | `var(--inet-fourth-color)` | `#E2F3E8` | connected schema state |
| `--inet-schema-column-compatible-bg-color` | update | `var(--inet-secondary-color-light)` | `#DDF1F5` | compatible schema state |
| `--inet-schema-column-incompatible-bg-color` | update | legacy primary-light mapping | `#F6E2C8` | incompatible schema state |
| `--inet-ws-table-odd-row-bg-color` | update | `#fff` | `#FFFFFF` | normalize explicit odd row token |
| `--inet-ws-table-even-row-bg-color` | update | `#f2f2f2` | `#F7F6F2` | soften even row token |

#### `_themeable.scss` selector/property deltas

| Selector | Property | Action | Before | After | Notes |
|---|---|---|---|---|---|
| graph assembly selectors | `background-color` | keep/update | `var(--inet-graph-assembly-bg-color)` | `var(--inet-graph-assembly-bg-color)` | hook stays; value retuned |
| graph assembly selected header selectors | `background-color` | keep/update | `var(--inet-graph-assembly-header-selected-bg-color)` | `var(--inet-graph-assembly-header-selected-bg-color)` | hook stays; now maps to Composer selected family |
| same selected header selectors | `color` | keep/update | `var(--inet-graph-assembly-header-selected-text-color)` | `var(--inet-graph-assembly-header-selected-text-color)` | hook stays; now maps to Composer selected text |
| graph connection selectors | `stroke` / line color | keep/update | `var(--inet-graph-connection-color)` | `var(--inet-graph-connection-color)` | worksheet-only meaning retained |
| graph warning connection selectors | `stroke` / line color | keep/update | `var(--inet-graph-connection-warning-color)` | `var(--inet-graph-connection-warning-color)` | worksheet-only warning connection retained |
| schema connection selectors | `stroke` / line color | keep/update | `var(--inet-schema-connection-color)` | `var(--inet-schema-connection-color)` | worksheet-only schema line retained |
| schema compatibility selectors | `background-color` | keep/update | current schema background hooks | current schema background hooks | values retuned; usage remains worksheet-only |
| worksheet detail row selectors | `background-color` | keep/update | current row hooks | current row hooks | keep row state local to worksheet tables |

## Appendix D: Surface Guidance Reference

Use [composer-design-spec.md](E:\home\dev\github\lookfeel\specs\composer-design-spec.md) for:

- viewsheet and wizard state distribution guidance
- shared chrome boundaries
- worksheet extension boundaries
- cross-surface state-usage rules
