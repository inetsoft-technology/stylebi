# StyleBI Visualization — Phase 9C (Deferred Consolidation & Polish) — Implementation Plan

> Status: **NOT STARTED — planning only.** This document collects the deferred items scattered across
> Phases 2–9A (excluding dark mode, which is [Phase 9B](visualization-phase9b-dark-mode-implementation-plan.md))
> into one impact-ranked backlog. No code has been written.

## Scope

Every prior phase recorded its own "Deferred / follow-ups" tail with a grounded reason. Phase 9C
consolidates those tails into a single sequence so the residue is addressed deliberately rather than
lost. Items are ranked by **impact × reach × readiness**. Dark mode — deferred by six phases — is
pulled out into Phase 9B; everything else lives here.

Inserted before Phase 10 (validation), mirroring the 6A / 9A precedent.

## How to read the ranking

- **Scheduled tier** — worth doing, ordered by impact. Each item carries its origin phase, why it
  matters, the grounded seam or known-good path already discovered, and its blocking decision (if any).
- **Boundary / won't-schedule tier** — deferred items that are *intentionally not scheduled* because
  they are out of scope for a theming pass, a separate build, an asset/product feature, or open-ended.
  Kept in this doc so they are not silently dropped; each says why.

Render-location rules and the gate mechanism are unchanged from the roadmap — System A = browser DOM
`--inet-viz-*` / `.viz-modern`; System B = org-scoped server resolver, export-visible.

## Scheduled tier (impact order)

### 1. Chart mini-toolbar compaction under modern density  *(from Phase 6 A1 / roadmap Deferred Work)*

- **Impact:** highest — the chart mini-toolbar appears on *every* chart, and modern density currently
  compacts everything around it while the toolbar stays legacy-sized, so charts read as half-themed.
- **Grounded blocker:** the toolbar is absolutely positioned with **JS-computed geometry** — vertical
  offset from the fixed constant `GuiTool.MINI_TOOLBAR_HEIGHT = 28` (`topY = top − 28 − adj`), and
  width/alignment from `miniToolbarService.getActionsWidth()`. A CSS-only resize desyncs both (vertical
  overlap/gap, horizontal misalignment). This is exactly why two Phase 6 Part A CSS attempts were
  reverted.
- **Known path:** a *coordinated* change — make the JS geometry values density-mode-aware (per density
  mode) **and** apply matching CSS. Not CSS-only.
- **Effort:** medium (TS + CSS, but no server/export impact — the toolbar is browser DOM).

### 2. Sequential / diverging color ramps  *(from Phase 8)*

- **Impact:** high — analytical color parity. Phase 8 shipped the modern *categorical* palette, but
  gradient/heatmap/diverging charts still render the legacy ramps, so a modern dashboard mixes modern
  discrete series with legacy continuous color.
- **Grounded blocker:** `GradientColorFrame` reads only `ChartPalette` CSS index 1/2, not
  `defaultColors`; the ColorBrewer / Heat / Bipolar ramps are **hardcoded with no gate hook**; and
  **no modern ramp design values exist yet** (a Phase 8 guard test pins gradient as unchanged).
- **Known path:** (a) design the modern ramp values first (prerequisite — coordinate with the
  palette swatches), then (b) add a gate hook to the ramp sources mirroring `VSChartPaletteDefaults`,
  defaults-only, server-side (export-visible).
- **Effort:** high (needs design input + new server gate seams on multiple ramp classes).

### 3. Group-subtotal emphasis in crosstab/calc tables  *(from Phase 5 item 3b)*

- **Impact:** medium-high — crosstab scanability. Grand-total emphasis shipped; interior group
  subtotals still render as plain body cells, so multi-level crosstabs lose their total hierarchy.
- **Grounded blocker:** subtotals are *body position*, not reachable by the positional style band that
  carried grand-total emphasis. A prior lens-field attempt (`VSTableLens.getFormat` + a `DataVSAQuery`
  transient flag) was **backed out** — the flag is on a different lens instance at render, always null.
- **Known-good path (documented in Phase 5):** a data-borne group-total `XTableStyle.Specification`
  (`ROW_GROUP_TOTAL` / `COL_GROUP_TOTAL`, `crosstab.isTotalCell`) added to the cloned style and
  **prepended ahead of the shipped zebra spec** (since `findSpec` returns the first match,
  `XTableStyle:586`), looping levels 0–9 for both axes. Token `--viz-subtotal-bg #EEEAE1` already
  recorded in the swatches.
- **Effort:** medium; export-visible, needs a validation cycle.

### 4. Conditional-formatting server bridge + highlight authoring presets  *(from Phase 4 → 8)*

- **Impact:** medium-high — semantic emphasis (warning/anomaly) is high analytical value, and it is
  the one Phase-4 state vocabulary pair with no server render path yet.
- **Grounded caveat:** there is **no server default to modernize** — `Highlight` fg/bg default to
  `null` (100% user-authored), and the `--inet-viz-warning-bg` / `-anomaly-bg` DOM tokens already
  shipped in Phase 4. So this is not a "bridge a default" task; it is really **highlight authoring
  presets** — offering modern warning/anomaly colors as *presets* in the highlight authoring UI so
  users reach them easily, plus optionally seeding a modern default palette for the presets.
- **Known path:** add modern semantic presets to the highlight authoring surface; keep them
  user-applied (so export follows naturally via the existing `HighlightGroup` path). Confirm ownership
  vs. Phase 8 before shipping semantic color.
- **Effort:** medium (authoring UI + preset palette; no forced-default risk).

### 5. Axis-line color polish  *(from Phase 6 B3 / Phase 7)*

- **Impact:** low, but a **ready, low-effort quick win** — a subtle hairline refinement
  (`#EEEEEE` → `#E8E5DE`) that aligns axis lines with the modern neutral used everywhere else.
- **Grounded, scoped:** implemented as a render-output substitution at `setupAxisSpec` (no descriptor
  mutation, no clone), value-equality vs `GDefaults.DEFAULT_LINE_COLOR` behind the gate; independent
  of the Phase 6 B1/B2 seams. The plot border is moot (its default is never drawn). Server-side,
  export-visible.
- **Effort:** low. Owner decides implement-vs-leave; recommended to fold in as a cheap consistency win.

### 6. Completeness items (grouped — each has a known blocker)

- **Selection-list selected-highlight in export** *(Phase 5 item 7)* — selected fill is client-CSS
  only today; export-visible selection needs a new `SelectionListVSAQuery` server path. Medium effort,
  completeness value.
- **Tabular numerals** *(Phase 5 item 8)* — no server `font-variant-numeric` support; needs a
  font-feature capability and touches measuring/wrapping/export (font risk). Own spike before scheduling.
- **Embedded-control filtered state** *(Phase 7 A3)* — `--inet-viz-filtered-bg` still has zero
  consumers; the selection item state is inline `cellFormat.background` from server `VSFormat` and the
  range-slider band is a PNG. No CSS overlay layer exists to attach the token to without the server
  seam or a new double-rendering overlay. Blocked until a live-overlay layer exists.

### 7. Icon consolidation tail  *(from Phase 9A)*

- **More / overflow alignment** — kebab (viz) vs meatball (shell) orientation. Deferred pending
  *grounded selectors* (confirmed viable: `.mini-menu-trigger.menu-horizontal-icon`,
  `.mobile-toolbar .menu-horizontal-icon`) **plus mobile-UX sign-off**. Lowest icon priority.
- **Sort-caret glyph unification** — switching sort carets from Unicode `⌃⌄` to ineticons chevrons was
  pushed back: icon-font chevrons are em-centered and would overlap when stacked as a pair, whereas the
  Unicode arrowheads stack tightly in the 18px box. Sort needs a *stacked pair*; expand needs a single
  chevron. Recommend keep Unicode for sort unless a better stacked glyph is found.
- **Context-menu drill/sort icons** — pushed back: these live among dozens of standard ineticons menu
  glyphs; modernizing only these would make them *inconsistent* with sibling menu items. Recommend
  leave unless the whole menu-glyph set is modernized together.

## Boundary / won't-schedule tier (kept visible, not planned)

These are deferred items that are intentionally **not** scheduled. Recorded so they are not silently
dropped.

- **EM Enterprise-Manager Material tables** *(Phases 3/5/6/7)* — admin chrome, a *separate Angular
  build* that does not import the portal viz tokens; never exported. Cross-project token plumbing, not
  a core visualization surface. Only touch if EM adopts the token layer wholesale.
- **WS-detail virtual-scroll row-height JS constants** *(Phase 3/5)* — worksheet-detail row height is
  JS virtual-scroll math, not CSS; needs a TS→token refactor, not a swap. Tech-debt, not a theming gap.
- **First-class KPI card** *(Phase 7)* — building a real KPI/scorecard card is a **product feature**,
  out of scope for a theming pass (there is no KPI assembly; a KPI is an authoring composition).
- **Gauge face modernization** *(Phase 7 D8)* — a gauge face is a themed *asset*, not a color constant;
  a full modern face is an asset pass.
- **Range slider (`VSTimeSlider`) modernization** *(Phase 7)* — its track/handle are *theme images*
  on both live and export (only the tick is a color constant), so there is no live/export mismatch to
  fix; modernization is an asset pass, deferred like the gauge face.
- **Native `<select>` option styling** *(Phase 7 A1)* — the common (≤500-option) combo path is a
  native `<select>` whose option hover/selected highlight is browser/OS-owned and not CSS-themeable.
  Making all combos adopt modern state would require always using the custom dropdown — an
  interaction/accessibility change (keyboard nav, ARIA, dynamic height), out of scope for a theming
  gate. Treat as a native-control boundary, analogous to the server-render boundary.
- **Exhaustive legacy-hook cleanup / exhaustive chart-type-specific polish** *(roadmap Deferred Work)*
  — open-ended; explicitly declared not to block the initiative. Address opportunistically, not as a
  scheduled item.
- **Target / threshold line color** *(Phase 8)* — `GraphTarget.lineColor 0xafafad`, low-value; no
  modern design value. Fold into ramp work (item 2) only if a modern threshold color is designed.

## Sequencing recommendation

Within the scheduled tier, do the quick win (item 5, axis line) opportunistically alongside whichever
server pass is open. Items 1 (mini-toolbar) and 3 (subtotal) are the highest-value self-contained
pieces and can proceed independently. Item 2 (ramps) is gated on a design-values prerequisite, so
start that design conversation early. Items 4, 6, 7 follow.

## Related

- [visualization-implementation-roadmap.md](visualization-implementation-roadmap.md) — Phase 9C section
- [visualization-phase9b-dark-mode-implementation-plan.md](visualization-phase9b-dark-mode-implementation-plan.md)
- Origin phases: [5](visualization-phase5-implementation-plan.md),
  [6](visualization-phase6-implementation-plan.md),
  [7](visualization-phase7-implementation-plan.md),
  [8](visualization-phase8-implementation-plan.md),
  [9A](visualization-phase9a-icon-consolidation-design.md)
