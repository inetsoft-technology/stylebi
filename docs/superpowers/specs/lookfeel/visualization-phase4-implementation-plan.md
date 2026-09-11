# StyleBI Visualization — Phase 4 (Widget-State Foundation) — Implementation Plan

## Scope

This plan executes **Phase 4 (Widget-State Foundation)** of
[visualization-implementation-roadmap.md](./visualization-implementation-roadmap.md). Phase 4 is a
**vocabulary phase**, not a broad-adoption phase — the roadmap sequences it as "establish density and
widget-state vocabulary *before* chart-palette specialization," and puts the actual per-surface state
rollout in **Phase 5 (Table And Grid Standardization)**. It is the state analogue of what Phase 1 was
for the token contract: define the values and the rules, lock a minimal proof into code, and hand the
broad adoption to the next phase.

Phase 4 delivers the roadmap's Phase 4 tasks:

1. **Assign modern values** to the 13 `--inet-viz-*` state tokens (defined legacy-inert in Phase 1;
   see Grounding) so the modern gate carries a coherent, validated state palette.
2. **Usage + render-location rules** for the 11-state grammar (`hover`, `selected`, `active`,
   `contextual`, `inline-edit`, `sorted`, `filtered`, `pinned`, `warning`, `anomaly`, `dimmed`) —
   each state routed to the system that owns it (browser-DOM overlay vs server-rendered/export-visible),
   the same render-location split Phase 3 applied to density.
3. **Minimal proof-of-adoption** on one low-friction DOM surface, mirroring Phase 3 Part A2, so the
   contract is exercised by real selectors rather than only declared.

Phase 4 does **not**:

- adopt state tokens across viewsheet tables / crosstabs / selection lists broadly — that is **Phase 5**
- bridge export-visible state (`selected` fill in a selection list, `sorted`/`filtered` baked into a
  cell, `warning`/`anomaly` conditional formatting, server-side chart brush/dimming) into the
  server-side render path — that is **Phase 5** (table state) and **Phase 8** (conditional-format /
  graph color); Phase 4 only *defines* those tokens and records the routing
- touch any chart mark or in-graph chrome color (Phases 6/8)
- add any EM UI surface (no per-state configuration exists or is planned)

**Definition of done:** with the gate **off** (default), computed styles are byte-identical to today
(the `:root` legacy state aliases are untouched). With the gate **on**, the state tokens resolve to
the modern palette, the one proof-adopted DOM surface visibly picks up the modern hover/selection
treatment, and the documented routing table tags every state as DOM-overlay (Phase 4/5 CSS) or
server-rendered/export-visible (Phase 5/8 server bridge). No export output changes.

## Grounding (verified against current code, `epic-74519`)

Phases 0–3 are implemented. For Phase 4 the relevant facts:

### State-token facts

| Fact | Evidence |
|---|---|
| All 13 state tokens exist with **legacy-default** values, inert | `web/projects/portal/src/scss/_viz-tokens.scss:32-45` (`:root` block) |
| State tokens have **zero modern values and zero consumers** | grep for `inet-viz-hover-bg`/`-selected-bg`/`-sorted-color`/`-filtered-bg` across `web/projects` returns only `_viz-tokens.scss` |
| The gate is already wired (Phase 2) and carries a density body class (Phase 3) | `.viz-modern` / `[data-viz-theme="v2"]` scope in `_viz-tokens.scss:58-61`; `viz-density-<mode>` classes applied at the three portal roots (Phase 3 A4) |
| Modern **state** palette source of truth exists | `docs/superpowers/specs/lookfeel/visualization-palette-swatches.html:20-31` (concrete hexes, below) |

### How each state renders today (render-location anchors — re-verify before editing)

| Surface / state | Where it renders | Export-visible? |
|---|---|---|
| Worksheet-detail DOM table row hover | CSS `.table-data__row:hover` → `--inet-hover-primary-bg-color` (`_themeable.scss:829-831`) | No (DOM) |
| DOM list/tree hover, selected | CSS `%bg-hover-primary` / `.bg-selected` → `--inet-hover-primary-bg-color` / `--inet-shell-selected-bg-color` (`_themeable.scss:233-239`); `%selected-item-colors` (L418-421) | No (DOM) |
| Viewsheet **table** cell fill (incl. selected / conditional) | server `VSFormat` → inline style in cell model; `vs-table-cell.component.scss` is structure + sort/drill glyph chrome only (`:79-87` sort icon, hardcoded `#bdbdbd`) | **Yes** (server) |
| Viewsheet table row **hover** / lasso highlight | DOM overlay in the Angular table component (transient) | No (DOM) |
| Table **sort** indicator | DOM glyph `.table-cell-sort-icon` / `.vs-header-cell-button-sort` (`base-table.scss:110-152`, hardcoded `gray`) | No (glyph is DOM; the header cell itself is server) |
| Selection-list **selected / dimmed** item | server `VSFormat` applied inline from `SelectionValueModel` state bitmask; `selection-list-cell.component.scss` is structure-only (no hover/selected/opacity rule) | **Yes** (server) |
| Chart **dimmed** (brush/de-emphasis) | server graph engine, baked into image/inline-SVG (see `project_stacked_bar_snap_dim` memory) | **Yes** (server) |

Consequence, mirroring Phase 3's density split: **transient interaction cues are browser-DOM and
Phase-4-ready via CSS; anything that appears in export is server-rendered** and Phase 4 only defines
the token — the server bridge is Phase 5 (table) / Phase 8 (conditional-format, chart).

### Modern state palette (source of truth — swatches file)

| Token | Modern value | Notes |
|---|---|---|
| `--inet-viz-hover-bg` | `#EEF4F6` | very light teal tint — deliberately lighter than selected |
| `--inet-viz-selected-bg` | `#DDF1F5` | more saturated teal fill |
| `--inet-viz-selected-border` | `#BFDDE5` | selected outline |
| `--inet-viz-selected-text` | `#123C44` | selected text, high contrast on teal |
| `--inet-viz-active-border` | `#E58A2A` | shell **primary** — active = orange outline, no fill |
| `--inet-viz-context-bg` | `#F7F3EA` | warm neutral, quieter than selected |
| `--inet-viz-inline-edit-bg` | `#FFFFFF` | plain editable surface |
| `--inet-viz-filtered-bg` | `#F6E2C8` | amber chip fill (text `#4A2500`) |
| `--inet-viz-sorted-color` | `#8C5510` | glyph/label color, not a fill |
| `--inet-viz-pinned-divider` | `#C8C2B7` | structural divider, low emphasis |
| `--inet-viz-warning-bg` | `#F8E8CC` | amber (text `#7A4E10`) — server-rendered target |
| `--inet-viz-anomaly-bg` | `#F7DEDE` | red (text `#7F2E2E`) — server-rendered target |
| `--inet-viz-dimmed-opacity` | `0.5` (unchanged) | swatches carry no override; keep the `:root` value |

These modern hexes **differ** from the Phase 1 legacy `:root` aliases (which point at shell tokens),
so — exactly like Phase 3 A2's padding change — enabling the gate visibly shifts DOM state colors,
while **gate-off remains byte-identical** (legacy aliases untouched).

## Decisions (resolved 2026-07-08 with initiative owner)

Unlike Phase 3 (whose D1–D3 were pre-resolved), these were proposed in this plan and confirmed. All
four settled as recommended.

### D1 — Phase 4 = vocabulary + one proof, broad adoption deferred to Phase 5 → **RESOLVED: YES**

The roadmap explicitly separates the state *vocabulary* (Phase 4) from table *state adoption*
(Phase 5, Task 2). Assigning modern values, documenting routing, and proving the contract on a single
DOM surface locks the foundation without pre-empting Phase 5's per-surface work. This matches how
Phase 1 (contract) preceded Phase 3 (density adoption). **No state adoption is performed in Phase 4
beyond the single D4 proof.**

### D2 — Modern values are DOM-only in Phase 4; export-visible state is defined-only → **RESOLVED: YES**

Assign modern CSS values for the states whose primary rendering is a **DOM overlay** (`hover`,
`active`, `contextual`, `inline-edit`, `filtered` chip, `sorted` glyph, `pinned` divider, DOM
`dimmed`). For states that are **server-rendered / export-visible** (`selected` fill in a selection
list, `warning`/`anomaly` conditional formatting, chart `dimmed`/brush), Phase 4 records the token +
routing but does **not** change the server render path — that would alter export output and belongs to
Phase 5/8. This keeps Phase 4 zero-export-impact. **Forward impact:** Phase 5 inherits the server-side
bridge for the selection-list `selected` fill and any export-visible table state; Phase 8 owns the
`warning`/`anomaly` conditional-format bridge (noted in those phases' roadmap sections).

### D3 — Keep the 13-token set as-is → **RESOLVED: YES, candidates noted**

The Phase 1 set covers the 11-state grammar. Not expanded in Phase 4. Candidate additions to
revisit during Phase 5 adoption if a surface needs them: `--inet-viz-hover-text`,
`--inet-viz-active-bg`, `--inet-viz-context-border`. Leaving them out now keeps the contract stable.

### D4 — Proof-of-adoption target → **RESOLVED: worksheet-detail DOM table row hover**

`_themeable.scss:829-831` (`.table-data__row:hover`) is the proof surface — a scan-heavy data-table
list inside the roadmap's local-override boundary, repointed to `--inet-viz-hover-bg` in a single
line. Because that selector's legacy value is `--inet-hover-primary-bg-color` (**not**
`--inet-ui-neutral-hover-bg-color`, the token's original Phase 1 baseline), the token's `:root`
default is **realigned to `var(--inet-hover-primary-bg-color)`** so gate-off stays byte-identical —
also the more correct baseline for a *data-table* row hover than the neutral UI-control hover. The
Phase 1 baseline was a best-guess alias and no selector consumed it, so this realignment is safe. (The
`generic-selectable-list` alternative was not taken.)

## State token modern-value assignment (Part A)

One additive block in `_viz-tokens.scss`, scoped to the existing gate, mirroring the Phase 3 density
matrices. Legacy `:root` values stay untouched (gate-off inertness). Unlike density there are no
per-mode variants — state values are density-independent, so a single `.viz-modern` block suffices.

```scss
// Modern widget-state palette (DOM overlays only; export-visible state is server-rendered — see
// the Phase 4 routing table). Legacy :root defaults above stay unchanged, so gate-off is
// byte-identical. Values are the visualization-owned state palette (teal selection family, kept
// distinct from shell/Composer selection meaning; active stays on the primary accent).
.viz-modern,
[data-viz-theme="v2"] {
  --inet-viz-hover-bg: #EEF4F6;
  --inet-viz-selected-bg: #DDF1F5;
  --inet-viz-selected-border: #BFDDE5;
  --inet-viz-selected-text: #123C44;
  --inet-viz-active-border: var(--inet-primary-color); // #E58A2A — primary, no fill
  --inet-viz-context-bg: #F7F3EA;
  --inet-viz-inline-edit-bg: #FFFFFF;
  --inet-viz-filtered-bg: #F6E2C8;
  --inet-viz-sorted-color: #8C5510;
  --inet-viz-pinned-divider: #C8C2B7;
  --inet-viz-warning-bg: #F8E8CC;
  --inet-viz-anomaly-bg: #F7DEDE;
  // --inet-viz-dimmed-opacity keeps the :root value (0.5); no modern override.
}
```

Note: `--inet-viz-active-border` uses `var(--inet-primary-color)` rather than the literal `#E58A2A`
so it tracks a themed primary; the swatch hex and the current primary coincide.

## Render-location routing (the core Phase 4 deliverable)

Each state is owned by exactly one system, decided by *where it renders* — the same test as density.
"Adoption phase" is where the token is actually wired to a surface (not Phase 4, except the D4 proof).

| State | Primary render | Export-visible | Phase 4 action | Adoption |
|---|---|---|---|---|
| `hover` | DOM overlay (row/item/mark hover) | No | modern value **+ D4 proof** | 5 (tables/lists) |
| `selected` | selection-list & table cell fill = **server `VSFormat`**; table-row/mark highlight = DOM overlay | **Yes** (fill) / No (overlay) | modern value for DOM overlay; **define only** for server fill | 5 (DOM overlay); server fill 5/8 |
| `active` | DOM overlay (active cell border, popover anchor) | No | modern value | 5 |
| `contextual` | DOM overlay (grouped/related highlight) | No | modern value | 5 |
| `inline-edit` | DOM (editable cell / embedded input) | No | modern value | 5 / 7 |
| `sorted` | DOM glyph + color in header | No (glyph) | modern value | 5 |
| `filtered` | DOM chip / on-indicator in widget chrome | No | modern value | 5 / 7 |
| `pinned` | DOM frozen/anchored divider | No (live overlay) | modern value | 5 |
| `warning` | **server `VSFormat`** conditional formatting | **Yes** | **define only** | 8 |
| `anomaly` | **server `VSFormat`** conditional formatting | **Yes** | **define only** | 8 |
| `dimmed` | chart brush/de-emphasis = **server graph engine**; DOM list de-emphasis = CSS opacity | **Yes** (chart) / No (DOM) | modern value (DOM opacity); server dimming already owned by graph | 5 (DOM); graph owns server |

Rule (carried from Phase 3): browser `--inet-viz-*` state tokens own **DOM-overlay** cues only.
Export-visible state must be driven server-side, or the live view and export diverge — so `warning`,
`anomaly`, the selection-list `selected` fill, and chart `dimmed` are **conceptual definitions** in
Phase 4, bridged in Phase 5/8 (the same way Phase 8 bridges chart color into `format.css`/descriptors).

## Changes

### Part A — Modern state values (CSS) — the vocabulary — ✅ IMPLEMENTED (2026-07-08)

`_viz-tokens.scss`:

- Added the `.viz-modern, [data-viz-theme="v2"]` **state block** (the modern-value table above),
  placed after the `--inet-viz-mode` block and before the density matrices (state is
  density-independent, so one block covers all modes). Legacy `:root` values are unchanged, so
  gate-off is byte-identical. `--inet-viz-dimmed-opacity` keeps its `:root` value (`0.5`).
- Realigned the `:root` default of `--inet-viz-hover-bg` from `var(--inet-ui-neutral-hover-bg-color)`
  to `var(--inet-hover-primary-bg-color)` (D4) — the data-table row-hover hook the Part B proof
  surface uses, so its gate-off computed style is unchanged.

### Part B — Proof-of-adoption (one DOM surface) — ✅ IMPLEMENTED (2026-07-08)

Mirrors Phase 3 A2's minimal, gate-off-safe repoint (D4):

- `_themeable.scss:829-831` — `.table-data__row:hover` `background-color`:
  `var(--inet-hover-primary-bg-color)` → `var(--inet-viz-hover-bg)`. With the Part A `:root`
  realignment, gate-off resolves to the identical color; gate-on resolves to the modern hover tint
  (`#EEF4F6`). This exercises the hover token end-to-end on a real scan-heavy data-table surface.

Broad table/list hover, selection, sort, and filter adoption is **Phase 5**, not here.

**A2 note (resolved).** The proof selector's legacy value (`--inet-hover-primary-bg-color`) differed
from the token's original Phase 1 baseline (`--inet-ui-neutral-hover-bg-color`). Resolved per D4 by
realigning the token `:root` default to the data-table hook rather than switching to a different proof
surface — the baseline was a best-guess alias with no consumers, and the data-table hook is the more
correct baseline for a viz row-hover token.

### Part C — none

No server-side change and no EM change in Phase 4 (contrast with Phase 3's Parts B and C). Export
output is unchanged by construction. Server-side state (selection-list selected fill, conditional
`warning`/`anomaly`) is introduced when Phase 5/8 build the server bridge.

## Validation (roadmap Phase 4 checks)

Verify against the assigned palette:

1. **Hover lighter than selected** — `hover #EEF4F6` (near-white teal tint) vs `selected #DDF1F5`
   (saturated) + border + dark text. ✓
2. **Active distinguishable from long-lived selection** — `active` = primary **orange border, no
   fill**; `selected` = **teal fill** + border + text. Different hue family and different treatment
   (outline vs fill). ✓
3. **Filtered and sorted visible without header noise** — `sorted` = glyph/label color `#8C5510`
   (no fill); `filtered` = a chip fill `#F6E2C8` on the control, not a full header repaint. ✓
4. **Warning and anomaly meaning-bearing** — amber `#F8E8CC` / red `#F7DEDE` map to shell semantic
   families, reserved for threshold/exception meaning (server-rendered), not decoration. ✓
5. **Selection distinct from Composer authoring state** — the visualization `selected` teal family is
   deliberately not the shell/Composer selection color, per the design spec. ✓

Build/inertness checks (as Phase 3):

6. `cd web && npm run build` clean; `npm run lint` clean.
7. **Gate-off:** computed styles byte-identical (only `:root` legacy aliases apply); no export change.
8. **Gate-on:** `body.viz-modern` resolves the state tokens to the modern palette; the proof surface's
   hover shows the modern tint. No viewsheet-assembly render or export output changes (Part C is empty).

## Deferred / follow-ups (→ Phase 5 / 8)

- **Broad table/grid state adoption** (row hover, row selection, active cell, sorted/filtered headers,
  pinned dividers, inline-edit) — Phase 5, Task 2, split by render location: DOM overlays via these
  CSS tokens; export-visible cell state via the server `VSFormat` path.
- **Selection-list `selected` fill server bridge** — export-visible; Phase 5 alongside the table
  server-state work.
- **`warning` / `anomaly` conditional formatting** — server-rendered, export-visible; Phase 8
  (conditional formatting), bridged into `VSFormat` / `format.css`, not browser tokens.
- **Chart `dimmed` / brush** — already server-owned by the graph engine (see the stacked-bar snap
  dimming work); no browser token involvement.
- **Candidate token additions** (`--inet-viz-hover-text`, `-active-bg`, `-context-border`) — revisit
  during Phase 5 only if a real surface needs them (D3).
- **Shared body-class helper** — the Phase 3 A4 duplication across the three portal roots is unchanged;
  still a candidate consolidation, unrelated to Phase 4.

## Branching (per CLAUDE.md)

Community-only change (SCSS in the `community/` submodule); an enterprise submodule-pointer bump only
at PR time. Continues the visualization-theming work on `epic-74519`; confirm whether Phase 4 lands on
`epic-74519` or a child `feature-{issue}` branch. Nothing committed to `main` or a `v1.0.x`/`v1.1.x`
release branch; nothing pushed/PR'd without explicit approval.

## Related

- [visualization-implementation-roadmap.md](./visualization-implementation-roadmap.md) — Phase 4 tasks, validation checks, sequencing (Phase 5 = table state adoption)
- [visualization-design-spec.md](./visualization-design-spec.md) — Visualization State Grammar (the 11 states), render-location boundary, Token Groups (state)
- [visualization-phase1-contract.md](./visualization-phase1-contract.md) — §2b state token contract + legacy defaults + render-location caveat
- [visualization-phase3-implementation-plan.md](./visualization-phase3-implementation-plan.md) — the gate + density body-class + A2 proof-adoption pattern this plan mirrors
- [visualization-palette-swatches.html](./visualization-palette-swatches.html) — modern state palette source of truth
