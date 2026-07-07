# StyleBI Visualization — Phase 1 Implementation Plan

## Scope

This plan executes the **code** side of Phase 1 (Contract Definition And Initial Scaffolding). The
contract itself is already written ([visualization-phase1-contract.md](./visualization-phase1-contract.md)).
What remains is the *initial scaffolding* the roadmap and contract §4 call for: lock the
`--inet-viz-*` token contract into code as one additive, inert SCSS file.

Phase 1 is deliberately narrow. This plan does **not**:

- rewrite any selector or adopt any token (Phase 3+)
- introduce the compatibility gate / `.viz-modern` (Phase 2)
- add density modes or state values (Phases 3–4)
- touch any server-side render path (charts/tables density — Phases 3/6/8)
- emit chart-mark colors as browser tokens (forbidden by the render-location boundary)

The definition of done is: the new file compiles, loads after `variables`, declares every
`--inet-viz-*` token at its legacy default, and **produces zero visual change** because nothing
consumes the tokens yet.

## Grounding (verified against current code)

- Import site: `community/web/projects/portal/src/global.scss` line 18 is `@import './scss/variables';`
  followed by roboto/bootstrap/ineticons/`_imports`. New import goes immediately after line 18 so
  viz tokens can reference shell tokens and load before the themeable/bootstrap-override layer.
- All 16 inherited shell tokens the scaffolding references are confirmed present in
  `community/web/projects/portal/src/scss/_variables.scss`:
  `--inet-font-size-base` (13px, L237), `--inet-text-muted-color` (L242), `--inet-primary-color`
  (L252), `--inet-shell-surface-default` (L263), `--inet-shell-surface-subtle` (L264),
  `--inet-selected-item-text-color` (L292), `--inet-border-strong-color` (L343),
  `--inet-danger-color-light` (L450), `--inet-warning-color-light` (L453),
  `--inet-ui-neutral-hover-bg-color` (L462), `--inet-shell-selected-bg-color` (L464),
  `--inet-control-height-sm` (24px, L473), `--inet-control-height-md` (30px, L474),
  `--inet-row-height-md` (34px, L476), `--inet-space-3` (6px, L494), `--inet-space-4` (8px, L495).
- Namespace is free: no existing `--inet-viz-*`, `.viz-modern`, or `[data-viz-theme]` in the repo
  (Phase 0 audit Task 2), so this collides with nothing.

## Changes

### Change 1 — new file `community/web/projects/portal/src/scss/_viz-tokens.scss`

A single `:root { … }` block. Every density/state token is declared as `var(--inet-...)` pointing at
its legacy-default shell token (or the current literal, e.g. `0.5`), so adoption-without-a-gate is
visually inert. `--inet-viz-mode: legacy` reserved for Phase 2. Chart-color group is emitted only as
a commented reserved-names block with the render-location constraint, so nobody wires marks to
browser CSS.

Proposed content (matches contract §2 / §4 exactly):

```scss
/*!
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
 *
 * <full AGPL header, copied verbatim from global.scss lines 1-17>
 */

// Phase 1 visualization token contract (see specs: visualization-phase1-contract.md).
// Legacy-default values only; inert until a selector consumes them in Phase 3+.
// Do NOT wire the chart-color group to marks or in-graph chrome: those are server-rendered
// (graph engine + format.css + GDefaults) and appear in export; browser CSS cannot drive them.
:root {
  // density — browser-DOM data surfaces only (viewsheet-assembly density is server-side; §2a).
  --inet-viz-row-height: var(--inet-row-height-md);
  --inet-viz-chrome-row-height: var(--inet-row-height-md);
  --inet-viz-cell-padding-x: var(--inet-space-4);
  --inet-viz-cell-padding-y: var(--inet-space-3);
  --inet-viz-toolbar-height: var(--inet-control-height-md);
  --inet-viz-control-height: var(--inet-control-height-sm);
  --inet-viz-font-size: var(--inet-font-size-base);

  // state (Phase 4 assigns modern values; live-view DOM overlays only — export-visible state
  // is server-rendered via VSFormat).
  --inet-viz-hover-bg: var(--inet-ui-neutral-hover-bg-color);
  --inet-viz-selected-bg: var(--inet-shell-selected-bg-color);
  --inet-viz-selected-text: var(--inet-selected-item-text-color);
  --inet-viz-selected-border: var(--inet-primary-color);
  --inet-viz-active-border: var(--inet-primary-color);
  --inet-viz-context-bg: var(--inet-shell-surface-subtle);
  --inet-viz-inline-edit-bg: var(--inet-shell-surface-default);
  --inet-viz-filtered-bg: var(--inet-shell-surface-subtle);
  --inet-viz-sorted-color: var(--inet-text-muted-color);
  --inet-viz-pinned-divider: var(--inet-border-strong-color);
  --inet-viz-warning-bg: var(--inet-warning-color-light);
  --inet-viz-anomaly-bg: var(--inet-danger-color-light);
  --inet-viz-dimmed-opacity: 0.5;

  // compatibility (Phase 2 activates the gate). CSS gate switches DOM only; server-rendered
  // surfaces follow the server-side per-org/theme property.
  --inet-viz-mode: legacy;

  // chart color: RESERVED / CONCEPTUAL ONLY — server-rendered, do not wire to marks or in-graph
  // chrome. Bridged to format.css / descriptors in Phase 8. See visualization-design-spec.md.
  // --inet-viz-chart-series-*, --inet-viz-ramp-sequential-*, --inet-viz-ramp-diverging-*,
  // --inet-viz-threshold-*, --inet-viz-conditional-*
}
```

### Change 2 — import in `community/web/projects/portal/src/global.scss`

Add one line immediately after line 18:

```scss
@import './scss/variables';
@import './scss/viz-tokens';        // <-- new
@import 'node_modules/roboto-fontface/css/roboto/sass/roboto-fontface.scss';
```

No other file changes. EM (`projects/em/src/styles.scss`) mirror is explicitly deferred (contract §4;
portal is the primary target).

## Verification

1. **Compile** — frontend build must succeed with the new import:
   `cd community/web && npm run build` (or a scoped Sass compile). A `var()` referencing a missing
   token would not error at compile time, but the token-existence check above already rules that out.
2. **Inertness** — grep confirms no selector references any `--inet-viz-*` name yet, so computed
   styles are unchanged. Optional spot check: the tokens appear on `:root` in devtools but affect
   nothing.
3. **Lint** — `npm run lint` clean (SCSS is import-only; no selector added).

## Branching (per CLAUDE.md)

This modifies the `community/` submodule, so the change needs a branch in the community repo (and a
matching enterprise submodule-pointer bump only when we're ready to PR). No Redmine issue number is
attached to this theming initiative yet — **need a branch name before I create it.** Options:

- a `feature-{issue}` branch if an issue is opened for the visualization theming work, or
- a descriptive `feature-viz-token-contract` working branch if we proceed without an issue.

I will not commit to `main` or any `v1.0.x`/`v1.1.x` release branch. Nothing is pushed or PR'd
without explicit approval.

## Open items carried from the contract (do not block Phase 1)

- **Token home** (contract §5 open decision): keep density/state under `--inet-viz-*` (Option A) vs
  move DOM-table density to shell/Composer (Option B). Scaffolding is unaffected — names are inert —
  so this can be decided before Phase 3/5 consumption, not now.
