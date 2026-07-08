# StyleBI Visualization — Phase 1 Token Contract

## Purpose

This document is the deliverable of **Phase 1** of
[visualization-implementation-roadmap.md](./visualization-implementation-roadmap.md): a stable
token contract between the shell and the visualization layer.

It defines:

- the shell tokens visualization inherits directly (the inherited contract)
- the visualization-owned `--inet-viz-*` token groups (density, state, chart color, thresholds,
  conditional formatting)
- the `--inet-viz-*` naming rule
- the initial scaffolding that locks the contract into code

Per the roadmap, Phase 1 is contract definition plus *optional* scaffolding. It does **not** include
selector rewrites, widget-by-widget rollout, or the compatibility gate (that is Phase 2). Modern
values arrive in Phases 3–4 under the gate.

## Grounding

The shell token layer is real and mature: runtime `--inet-*` variables are declared in
`community/web/projects/portal/src/scss/_variables.scss` (the `:root` block), consumed by
`_themeable.scss` and `_bootstrap-override.scss`. Import order in
`community/web/projects/portal/src/global.scss` is: `variables` → bootstrap → `_imports`
(themeable, bootstrap-override). Scaffolding is added directly after `variables`.

## 1. Inherited Shell Contract

Visualization consumes these existing shell tokens directly. They are **not** redefined under
`--inet-viz-*`; visualization references them as-is.

| Concept | Inherited shell token(s) | Current value / source |
|---|---|---|
| font family | `--inet-font-family` | Inter/Roboto system stack |
| base font size | `--inet-font-size-base` | `13px` |
| text — strong | `--inet-text-strong-color` (`--inet-shell-text-strong`) | `#1F1F1B` |
| text — default | `--inet-text-color` (`--inet-shell-text-default`) | `#35342F` |
| text — muted | `--inet-text-muted-color` (`--inet-shell-text-muted`) | `#6A685F` |
| text — subtle | `--inet-text-subtle-color` (`--inet-shell-text-subtle`) | `#99958C` |
| surface — canvas | `--inet-shell-surface-canvas` | `#F8F7F4` |
| surface — default | `--inet-shell-surface-default` | `#FFFFFF` |
| surface — subtle | `--inet-shell-surface-subtle` | `#F1EFEA` |
| surface — hover | `--inet-shell-surface-hover` | `#ECE9E2` |
| border — default | `--inet-default-border-color` | → `#D9D5CC` |
| border — strong | `--inet-border-strong-color` | → `#C8C2B7` |
| radius | `--inet-radius-sm/md/lg/xl/pill` | `2/3/4/6/999px` |
| focus ring | `--inet-focus-ring` (+ `-width`, `-color`) | `0 0 0 2px rgba(229,138,42,.28)` |
| primary action | `--inet-primary-color` (+ `-light/-dark/-soft-color/-text-color`) | `#E58A2A` |

Contract rule: if any of these shell tokens change in a theme, visualization inherits the change
automatically. Visualization must not fork copies of them.

## 2. Visualization-Owned Token Groups

New tokens use the `--inet-viz-*` prefix. In Phase 1 each token is **declared with a legacy-default
value** (the closest existing shell token or current behavior) so that adoption without the gate is
visually inert. Modern per-mode/analytic values are layered under `.viz-modern` in later phases.

### 2a. Density — the browser-DOM half only (Phase 3 fills modern per-mode matrix)

**Density has two implementations; these CSS tokens are the smaller half.** Per the render-location
boundary (design spec Rendering And Theming Architecture, the "is it a viewsheet assembly?" test):

- **Server-side defaults — the PRIMARY path.** The main BI data surfaces — viewsheet table
  assemblies (VSTable/VSCrosstab/VSCalcTable) **and** selection lists / selection trees, i.e.
  anything extending `AbstractVSAssembly` — get row/cell height, font size, and padding from the
  **server-side, user-settable** model (`TableDataVSAssemblyInfo` row heights,
  `SelectionBaseVSAssemblyInfo` cell height, `VSFormat` fonts, `AssetUtil.defh` /
  `Util.DEFAULT_FONT` defaults), shared by the live model and every export format. Modern density
  for these is a **server-side default** (Phase 3, Configuration source) — **not a CSS token**, and
  therefore not defined in this contract.
- **CSS tokens — the DOM half (this table).** The tokens below govern only browser-DOM data
  surfaces that are *not* viewsheet assemblies: worksheet detail tables, EM admin tables, dialog
  lists, portal repository/schedule lists, and generic `selectable-list` dialog widgets.

Consequence: the "Modern target" column below has a **server-side counterpart** (the same per-mode
values applied to `AssetUtil.defh` etc.). The two halves must be kept in agreement, or a dense DOM
table and a dense viewsheet table will not match. See [§5](#5-decisions-to-confirm-before-writing-code)
for the open decision on whether this CSS group belongs under `--inet-viz-*` at all.

| Token (DOM surfaces only) | Legacy default (Phase 1) | Modern target (Phase 3; must match server-side half) |
|---|---|---|
| `--inet-viz-row-height` | `var(--inet-row-height-md)` (34px) | comfortable 30 / compact 28 / dense 26 |
| `--inet-viz-chrome-row-height` | `var(--inet-row-height-md)` | 30 / 28 / 26 |
| `--inet-viz-cell-padding-x` | `var(--inet-space-4)` (8px) | 8 / 6 / 4 |
| `--inet-viz-cell-padding-y` | `var(--inet-space-3)` (6px) | 6 / 4 / 2 |
| `--inet-viz-toolbar-height` | `var(--inet-control-height-md)` (30px) | 30 / 28 / 24 |
| `--inet-viz-control-height` | `var(--inet-control-height-sm)` (24px) | mode-scaled compact control |
| `--inet-viz-font-size` | `var(--inet-font-size-base)` (13px) | 13 / 13 / 12 |

### 2b. State (Phase 4 assigns modern values; usage rules in design spec state grammar)

| Token | Legacy default (Phase 1) |
|---|---|
| `--inet-viz-hover-bg` | `var(--inet-hover-primary-bg-color)` *(Phase 4 realigned from `--inet-ui-neutral-hover-bg-color` to match the data-table row-hover hook)* |
| `--inet-viz-selected-bg` | `var(--inet-shell-selected-bg-color)` |
| `--inet-viz-selected-text` | `var(--inet-selected-item-text-color)` |
| `--inet-viz-selected-border` | `var(--inet-primary-color)` |
| `--inet-viz-active-border` | `var(--inet-primary-color)` |
| `--inet-viz-context-bg` | `var(--inet-shell-surface-subtle)` |
| `--inet-viz-inline-edit-bg` | `var(--inet-shell-surface-default)` |
| `--inet-viz-filtered-bg` | `var(--inet-shell-surface-subtle)` |
| `--inet-viz-sorted-color` | `var(--inet-text-muted-color)` |
| `--inet-viz-pinned-divider` | `var(--inet-border-strong-color)` |
| `--inet-viz-warning-bg` | `var(--inet-warning-color-light)` |
| `--inet-viz-anomaly-bg` | `var(--inet-danger-color-light)` |
| `--inet-viz-dimmed-opacity` | `0.5` |

Validation intent (from design spec): hover lighter than selected; active distinct from long-lived
selection; sorted/filtered visible without header noise; warning/anomaly meaning-bearing.

**Render-location caveat.** These tokens drive **live-view DOM overlays** (hover, active) — which
never export — so they are safe as CSS for transient cues. But state that **appears in export** is
server-rendered and will not read these tokens: a selection-list's selected-item formatting, and
sort/filter indicators on a viewsheet table, come from `VSFormat` / the assembly render path. Phase 4
adoption must route export-visible state through the server-side half, the same way §2a splits
density.

**Phase 4 resolution (2026-07-08).** Phase 4 assigned modern values to these tokens under the gate
(see [visualization-phase4-implementation-plan.md](./visualization-phase4-implementation-plan.md),
Part A) and proved the contract on one DOM surface, but is **vocabulary-only**: it changes **no
server render path**, so export-visible state (`selected` fill, `warning`, `anomaly`) is **defined
here but not yet rendered from these tokens**. Bridging export-visible state into the server-side
half is deferred — table `selected` fill to **Phase 5**, `warning`/`anomaly` conditional formatting
to **Phase 8**. Gate-off remains byte-identical.

### 2c. Chart color — CONCEPTUAL SOURCE OF TRUTH, NOT BROWSER-AUTHORITATIVE

Per the Rendering And Theming Architecture section of
[visualization-design-spec.md](./visualization-design-spec.md), chart **marks and in-graph chrome
are server-rendered** (descriptors + `format.css` `CSSConstants` classes + `GDefaults`) and shared
by the live view and every export format. Browser CSS cannot drive them.

Therefore these names are a **conceptual palette definition** that Phase 8 must bridge into the
server-side sources. They must **not** be wired to chart marks or in-graph chrome in the browser.
Only chart color applied to surrounding DOM chrome (object header, toolbar, interactive tooltip)
may resolve as a real browser token.

- `--inet-viz-chart-series-*` (conceptual; bridge to `ChartPalette` / `CategoricalColorFrame`)
- `--inet-viz-ramp-sequential-*` (conceptual; bridge to server-side ramps)
- `--inet-viz-ramp-diverging-*` (conceptual; bridge to server-side ramps)
- `--inet-viz-threshold-*` (conceptual; server-side for graph, CSS only for DOM/table conditional UI)
- `--inet-viz-conditional-*` (split per surface: table/KPI DOM = CSS; in-graph = server-side)

Phase 1 reserves these names and records the constraint; it does not emit mark colors.

### 2d. Compatibility (Phase 2 activates)

- `--inet-viz-mode` — reserved; resolves to `legacy` by default, `modern` inside the gate
- legacy/modern aliasing is introduced in Phase 2; Phase 1 only reserves the names
- **The gate has two halves.** `--inet-viz-mode` is a CSS token, so it can only switch
  browser-DOM surfaces. Everything server-rendered — chart color/chrome and viewsheet-assembly
  density/state — is gated by the **server-side per-org/theme property**, not by `.viz-modern`. Do
  not assume the browser class flips a viewsheet table or a chart; those follow the server-side gate.

## 3. Naming Rule

- all new visualization-owned tokens use the `--inet-viz-*` prefix
- do not extend shell token names (`--inet-table-*`, `--inet-row-height-*`, etc.) for
  visualization-owned meaning; those remain shell-owned legacy hooks
- density/state/chart/threshold/conditional/compat are the token families; keep the family segment
  in the name (`--inet-viz-<family>-<role>`)

## 4. Initial Scaffolding Plan

Lock the contract into code with one additive file. No selectors change; nothing is adopted yet.

- **New file:** `community/web/projects/portal/src/scss/_viz-tokens.scss`
  - a single `:root { … }` block declaring every `--inet-viz-*` token from Section 2 with its
    legacy default
  - chart-color group emitted as commented reserved names with the server-side constraint note (not
    live values), so nobody wires them to marks
- **Import:** in `community/web/projects/portal/src/global.scss`, add
  `@import './scss/viz-tokens';` immediately after `@import './scss/variables';` (line 18) so viz
  tokens can reference shell tokens and load before `_imports` (themeable/bootstrap-override)
- **EM (optional, later):** if EM data surfaces adopt viz tokens, mirror the import in
  `projects/em/src/styles.scss`; not required for the first pass (portal is the primary target)

### Scaffolding skeleton (illustrative)

```scss
// _viz-tokens.scss — Phase 1 contract scaffolding (legacy defaults; inert until adopted)
:root {
  // density — DOM data surfaces only (viewsheet-assembly density is server-side; see §2a).
  // Phase 3 layers modern per-mode values under .viz-modern, matched to the server-side half.
  --inet-viz-row-height: var(--inet-row-height-md);
  --inet-viz-chrome-row-height: var(--inet-row-height-md);
  --inet-viz-cell-padding-x: var(--inet-space-4);
  --inet-viz-cell-padding-y: var(--inet-space-3);
  --inet-viz-toolbar-height: var(--inet-control-height-md);
  --inet-viz-control-height: var(--inet-control-height-sm);
  --inet-viz-font-size: var(--inet-font-size-base);

  // state (Phase 4 assigns modern values)
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

  // compatibility (Phase 2 activates the gate)
  --inet-viz-mode: legacy;

  // chart color: RESERVED / CONCEPTUAL ONLY — server-rendered, do not wire to marks or in-graph
  // chrome. Bridged to format.css / descriptors in Phase 8. See visualization-design-spec.md.
  // --inet-viz-chart-series-*, --inet-viz-ramp-sequential-*, --inet-viz-ramp-diverging-*,
  // --inet-viz-threshold-*, --inet-viz-conditional-*
}
```

## 5. Decisions To Confirm Before Writing Code

1. **Portal-only first pass?** Recommend yes — add scaffolding to portal `global.scss`; defer EM.
2. **Legacy-default values** in Section 2a/2b — confirm the chosen shell-token aliases are the
   intended baselines.
3. **File name / location** — `scss/_viz-tokens.scss` imported after `variables`. Confirm the
   naming fits the existing `_variables` / `_themeable` convention.
4. **Chart-color group treatment** — confirm reserving as commented conceptual names (not live CSS)
   is acceptable for Phase 1.

### Open decision — home of the CSS density/state tokens

**Status: unresolved.** The CSS density tokens (§2a) — and to a lesser extent the export-visible
CSS state tokens (§2b) — only cover browser-DOM surfaces, which are mostly **Composer/shell**
(worksheet detail grid, EM admin tables, dialog/portal lists), not viewsheet visualization output.
The actual visualization BI tables/selection lists are server-side. That raises a genuine ownership
question:

- **Option A (keep under `--inet-viz-*`):** visualization "owns density" conceptually, implemented
  in two places (server-side defaults for assemblies + these CSS tokens for DOM data surfaces).
  Simple mental model; but a `viz` prefix ends up styling mostly Composer/shell surfaces.
- **Option B (move DOM-table density/state to shell/Composer):** treat DOM-table density as a
  shell/Composer concern and make "visualization density" **purely server-side**. Cleaner ownership;
  but splits the density story across two token namespaces and loses a single "viz density" concept.

Recommendation: lean Option A with explicit scoping (as written in §2a/§2b), but this should be
decided before the tokens are consumed by any selector (Phase 3/5), not now. Phase 1 scaffolding is
unaffected either way — the names are inert until adopted.

## 6. Definition Of Done (Phase 1)

- inherited shell contract documented against real tokens (this doc, Section 1)
- `--inet-viz-*` token groups defined with legacy defaults (Section 2)
- naming rule recorded (Section 3)
- `_viz-tokens.scss` added and imported after `variables`, compiling with no visual change (no
  selector consumes the tokens yet)
- chart-color constraint encoded so marks/in-graph chrome are never wired to browser tokens
- no compatibility gate yet (Phase 2), no density modes yet (Phase 3), no state adoption yet
  (Phase 4)

## Related

- [visualization-implementation-roadmap.md](./visualization-implementation-roadmap.md) — Phase 1 tasks, sequencing
- [visualization-design-spec.md](./visualization-design-spec.md) — token groups, state grammar, rendering architecture
- [visualization-phase0-audit.md](./visualization-phase0-audit.md) — baseline audit and constraints
