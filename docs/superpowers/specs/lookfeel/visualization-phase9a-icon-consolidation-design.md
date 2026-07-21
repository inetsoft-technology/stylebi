# StyleBI Visualization — Phase 9A (Icon Consolidation) — Design Spec

## Scope

Phase 9A consolidates **visualization affordance icons** (sort, expand/collapse, drill, more/overflow)
onto the shell's cleaner, already-shipping icon patterns, so visualization surfaces read as one system
with the shell. It is inserted between Phase 9 (themeability review) and Phase 10 (validation), numbered
`9A` — mirroring the Phase 6A precedent — so Phase 10 is not renumbered.

The intent is **consolidation and reuse, not new artwork**. Where a cleaner target already exists in the
shell it is adopted, whether that target is a different ineticons glyph or a shell **CSS pattern** (the
sort indicator). No new icon-font glyphs are commissioned.

Unlike Phases 3/5/6/6A/7/8 — which are server-side because they touch export — the visualization
affordance icons in scope are **client-side browser DOM** (`<i>` glyph elements and DOM spans), so this is
predominantly a **System A (browser-DOM)** phase that rides the browser `.viz-modern` gate directly. It
does not need a server resolver except for any indicator proven to be export-baked (see Task 0).

### Why icons are a new concern

The visualization roadmap and design spec cover density, widget state, tables, chart chrome, KPI, and
analytical color. **Icons are not addressed anywhere** in
[visualization-design-spec.md](visualization-design-spec.md) or
[visualization-implementation-roadmap.md](visualization-implementation-roadmap.md). Phase 9A is the first
icon-specific pass. It does not amend the state grammar or color systems; it aligns the *glyphs and
indicator patterns* those surfaces draw.

## Rendering boundary: mostly browser DOM, with one thing to verify

Apply the one test from the [Rendering And Theming Architecture](visualization-design-spec.md#rendering-and-theming-architecture)
section: *is it a viewsheet assembly?* The affordance icons in scope are drawn as **DOM elements inside
the assembly's Angular template** (e.g. `<i class="sort-ascending-icon">` in `vs-table.component.html`),
not baked into the server-rendered assembly image. They are therefore browser-CSS-themeable and gate-able
through the browser `.viz-modern` body class — the same mechanism Phase 2 established.

**Task 0 (grounding, must run first): confirm the export path.** The interactive table/crosstab sort
indicator is a DOM overlay in the live view. Before any change, verify whether the **exporter** renders a
sort indicator of its own (server-side) for tables/crosstabs. Two outcomes:

- If export draws no interactive sort glyph (expected — sort is an interaction affordance, not stored
  cell content), then all Phase 9A sort work is browser-DOM and fully covered by `.viz-modern`.
- If any target indicator *is* export-baked, that specific indicator is server-owned and must be bridged
  server-side (like Phases 5/6/8) or explicitly left out of Phase 9A. Flag it; do not route an
  export-visible icon through a browser token.

Expand/collapse, drill, and more/overflow icons are all interaction chrome (context menus, on-canvas
toggles, mini-toolbar), so the same browser-DOM expectation applies; Task 0 spot-checks them too.

## The audit method (why the divergence list is trustworthy this time)

A prior inventory compared surfaces by ineticons **class name** (`*-icon`) and **missed** that the shell's
data-page sort indicator is not a font glyph at all — it is a `.sort-indicator` span drawn with CSS
pseudo-element Unicode arrowheads. A name-based grep is structurally blind to CSS-drawn indicators, inline
SVG, `mat-sort-header`, Font Awesome, and `background-image` icons.

Phase 9A therefore uses a **mechanism-agnostic, state-aware, rendered** audit:

1. **static sweep across all mechanisms** — ineticons glyph classes; CSS pseudo-element `content:`
   codepoints and bespoke indicator classes; inline `<svg>` / `<mat-icon>` / `mat-sort-header`; Font
   Awesome; `background-image` — resolving each to what it actually renders, **per state**.
2. **rendered audit gallery** — an HTML swatch page (styled like
   [visualization-palette-swatches.html](visualization-palette-swatches.html)) that shows each affordance
   × state × surface side-by-side, so mechanism/visual divergence is visible at a glance. Class names
   cannot show that ⌃⌄ ≠ heavy-lines; a rendered gallery can.
3. **running-app spot capture** — screenshots of the marquee surfaces (table/crosstab headers, selection
   lists, chart toolbar) to catch runtime CSS/theme overrides.

The gallery is a reviewable deliverable and the authority that **finalizes the change list** before Track
A edits begin.

## Grounding (verified against current source; re-confirm line numbers before editing)

Icon architecture: a single shared `ineticons` font (~558 glyphs). Codepoints in
`web/projects/portal/src/assets/ineticons/variables.scss` → class map + semantic aliases in
`web/projects/portal/src/scss/_icons.scss:28-52` → `@extend` aliases in
`web/projects/portal/src/scss/_icon-alias.scss`. The alias layer is the swap seam: templates reference a
class, the glyph is chosen centrally. There is **no separate "modern" icon set** in the product; even EM's
`<mat-icon>` renders `fontSet="ineticons"`.

### Sort (marquee divergence — 3 mechanisms, 3 visuals)

| Surface | Mechanism | Renders | States | Anchor |
|---|---|---|---|---|
| Shell data-folder list | **CSS pseudo** | `.sort-indicator` `::before "\2303"` ⌃ / `::after "\2304"` ⌄, reveal-on-hover, inactive arm dimmed to 0.22 | none / asc / desc | `portal/data/data-folder-browser/data-folder-list-view/data-folder-list-view.component.scss:43-92`, `.component.html:32-66` |
| Shell asset-item list | CSS pseudo | identical `.sort-indicator` | none / asc / desc | `portal/data/asset-item-list-view/asset-item-list-view.component.scss:40-93`, `.html:31-36` |
| Shell datasource browser | CSS pseudo | identical `.sort-indicator` (6 columns) | none / asc / desc | `portal/data/data-datasource-browser/data-datasource-browser.component.html:107-159` |
| Viz table header | **font glyph** | `sort-ascending-icon` `\ebf0` / `sort-descending-icon` `\ebf1` / `sort-icon` `\ebf4` (heavy sort-amount lines) | asc / desc / none | `vsobjects/objects/table/vs-table.component.html:191-202` |
| Viz crosstab header | font glyph | above **plus** `sort-value-ascending-icon` `\ebf2` / `sort-value-descending-icon` `\ebf3` | none / asc / desc / **val-asc / val-desc (5-state)** | `vsobjects/objects/table/vs-crosstab.component.html:191-195, 288-292, 396-400, 496-500` |
| Viz selection list/tree ctx-menu | font glyph | `sort-icon` / `sort-ascending-icon` / `sort-descending-icon` | none / asc / desc | `vsobjects/action/selection-list-actions.ts:135,143,151`; `selection-tree-actions.ts:114,122,130` |
| Existing `.viz-modern` sort seam | (recolors font glyph) | recolors active `sort-ascending/descending-icon` to `--inet-viz-sorted-color` | asc / desc | `web/projects/portal/src/scss/_themeable.scss:1267-1270` |
| EM regular-table (**out of scope**) | **mat** | `mat-sort-header` Material triangle | asc / desc / none | `em/.../common/util/table/regular-table/regular-table.component.html:20,41` |

Codepoints: `$sort-ascending-icon:"\ebf0"` (variables.scss:498), `$sort-descending-icon:"\ebf1"` (499),
`$sort-value-ascending-icon:"\ebf2"` (500), `$sort-value-descending-icon:"\ebf3"` (501),
`$sort-icon:"\ebf4"` (502).

### Expand/collapse (visual-metaphor divergence)

| Surface | Renders | Anchor |
|---|---|---|
| Shell widget tree | chevron pair `downward-icon` (expanded) / `forward-icon` (collapsed) | `widget/tree/tree-node.component.html:58-64`, `.ts:367-373 getToggleIcon()` |
| EM flat-tree | same glyphs via `<mat-icon fontSet="ineticons">` | `em/.../common/util/tree/flat-tree-view.component.html:62-65` |
| Viz selection tree folder | **boxed** `minus-box-outline-icon` (open) / `plus-box-outline-icon` (closed) | `vsobjects/objects/selection/selection-list-cell.component.ts:463-469` |
| Viz selection show/hide | `upward-icon` / `downward-icon` (flip-aware) | `vsobjects/objects/selection/collapse-toggle-button.component.html:21-35` |

### Drill (internal viz inconsistency — 3 ± families, no shell counterpart)

| Surface | Renders | Anchor |
|---|---|---|
| Crosstab / chart ctx-menu | `drill-down-filter-icon` / `drill-up-filter-icon` | `vsobjects/action/crosstab-actions.ts:282,290`; `chart-actions.ts:380,388` |
| Chart axis inline | `plus-box-outline-icon` / `minus-box-outline-icon` | `graph/objects/chart-axis-area.component.html:97-126` |
| Chart nav zoom | `shape-plus-icon` `\ebcd` / `shape-minus-icon` | `graph/objects/chart-nav-bar.component.html:23,26` |

### More/overflow (minor — rotated variant, same font)

| Surface | Renders | Anchor |
|---|---|---|
| Viz mini-toolbar | vertical kebab `menu-vertical-icon` `\eb2a` | `vsobjects/action/abstract-vs-actions.ts:325`, `vsobjects/objects/mini-toolbar/mini-toolbar.component.html:44` |
| Shell widget tree | horizontal meatball `menu-horizontal-icon` | `widget/tree/tree-node.component.html:90-93` |

### Track B cleanup targets (bug-class)

- **Font Awesome contamination** — `fa fa-sliders` / `fa fa-format` / `fa fa-calculator` / `fa fa-trash`
  mixed with ineticons in a viz menu: `vsobjects/action/selection-list-actions.ts:48-91`. These sit on
  non-sort menu actions (measure/format options); replace with ineticons equivalents.
- **Dead glyph names (verify in gallery)** — a prior report flagged `sort-asc-icon` / `sort-desc-icon` /
  `sort-val-asc-icon` / `sort-val-desc-icon` near `base-table-actions.ts:93-105` as names absent from the
  font map (would render nothing). The mechanism sweep saw a valid `sort-icon` at `:105`. Task 1's gallery
  resolves this discrepancy definitively; fix any name that renders nothing.

### Matches — leave alone

Search (`search-icon` `\eb7d`), close/clear (`close-icon` `\ea7a` / `close-circle-icon`), and the
drill-embedded filter glyphs use consistent ineticons font glyphs across shell and viz. No SVG / Font
Awesome / Bootstrap glyphicon / `background-image` icon renders any in-scope affordance.

## Tasks and tracks

### Task 0 — export grounding
Confirm no in-scope indicator is server-baked into viewsheet export (see Rendering boundary). Flag any
that is; it leaves the browser-DOM track.

### Task 1 — mechanism-agnostic rendered audit gallery
Produce the static sweep + HTML gallery + spot capture described under *The audit method*. Output finalizes
the change list. Commit the gallery alongside this spec's siblings.

### Track A — gated consolidation (`.viz-modern`; gate-off byte-identical)

Repoint each divergent viz affordance to the shell's cleaner target through **viz-scoped alias classes**,
so the shared font glyph the shell still uses is untouched and legacy mode is unchanged. Build on the
existing `.viz-modern` sort seam in `_themeable.scss:1267-1270` rather than inventing a parallel gate.

1. **Sort** — adopt the shell `.sort-indicator` ⌃/⌄ CSS pattern for label sort (none/asc/desc via
   opacity/dimming) in viz table and crosstab headers under `.viz-modern`. Because crosstab is **5-state**,
   design a modern **value-sort variant** in the same visual language (the double-arrowhead plus a small
   numeric/measure accent) so value-asc/value-desc remain distinct from label-asc/desc. One indicator
   language across table + crosstab. Legacy (gate-off) keeps the `sort-*-icon` font glyphs exactly.
2. **Expand/collapse** — under `.viz-modern`, repoint the selection-tree folder toggle from boxed
   `plus/minus-box-outline-icon` to the shell **chevron pair** (`forward-icon` / `downward-icon`),
   matching widget and EM trees.
3. **Drill** — unify viz's three ± families to one canonical set under `.viz-modern`. Prefer aligning the
   inline chart-axis drill and zoom controls to a single ± glyph and keeping the compound
   `drill-*-filter-icon` only where the filter semantics genuinely differ from plain expand/collapse. The
   gallery informs the final mapping.
4. **More/overflow** — align kebab/meatball orientation across viz (lowest priority; may land last). No
   shell change; this only makes viz internally consistent and consistent with the shell's dominant
   choice.

### Track B — global cleanup (ungated, bug-class)

Ship correctness fixes globally (they improve legacy mode too, and gating a bug fix would leave the bug in
legacy): replace the Font Awesome refs in `selection-list-actions.ts:48-91` with ineticons equivalents;
fix any dead/non-rendering glyph name confirmed by the gallery.

## Mechanism / seam

- **Track A** lives in the SCSS gate scope: extend `_themeable.scss` (the existing `.viz-modern` icon
  seam) and/or add `.viz-modern`-scoped rules in `_icon-alias.scss`. Introduce viz-scoped semantic classes
  where a template must switch between a font glyph (legacy) and a CSS-pattern indicator (modern) — e.g. a
  `.viz-sort-indicator` element the header renders, inert in legacy and styled only under `.viz-modern`.
- Prefer **repointing an alias** over editing a template. Only touch a template where the modern target is
  a CSS pattern that needs different markup than a font `<i>` (the sort indicator is the main such case).
- **Never** edit a shared `$*-icon` codepoint in `variables.scss` to achieve a viz-only change — that
  leaks into the shell and cannot be gated.

## Compatibility and validation

- **Gate-off is byte-identical.** With `.viz-modern` absent, every in-scope surface renders exactly
  today's glyph/pattern. Only Track B (bug fixes) changes legacy output, and only where it renders nothing
  or a foreign font today.
- **Validation** folds an icon pass into the Phase 10 screen-by-screen audit: verify legacy renders
  unchanged; modern renders the consolidated indicators; the crosstab value-sort states remain
  distinguishable; no affordance regresses in hit-area or legibility at dense sizes.

## Out of scope

- EM `mat-sort-header` and EM Material tables (admin chrome, separate `projects/em` build, never exported;
  a secondary surface at most).
- Any indicator proven export-baked in Task 0 (flagged for a separate server-side pass).
- Search / close / clear / filter glyphs (already consistent).
- Type/decorative glyphs (chart-type, data-type, shape/texture/line-style) — not affordance icons.
- New icon-font artwork.

## Output

- A rendered, mechanism-agnostic icon audit gallery for the in-scope affordances.
- Visualization sort, expand/collapse, drill, and more/overflow affordances consolidated onto the shell's
  cleaner patterns under `.viz-modern`, with the crosstab 5-state sort meaning preserved.
- Font Awesome removed from the affected viz menu and any dead glyph names fixed, shipped globally.
- Legacy visualization unchanged when the gate is off.

## Related specs

- [visualization-design-spec.md](visualization-design-spec.md)
- [visualization-implementation-roadmap.md](visualization-implementation-roadmap.md)
- [visualization-phase6a-implementation-plan.md](visualization-phase6a-implementation-plan.md) (letter-insert precedent)
- [visualization-phase9-implementation-plan.md](visualization-phase9-implementation-plan.md)
