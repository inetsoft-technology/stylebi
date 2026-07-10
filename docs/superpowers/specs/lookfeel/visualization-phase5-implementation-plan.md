# StyleBI Visualization — Phase 5 (Table And Grid Standardization) — Implementation Plan

## Scope

This plan executes **Phase 5 (Table And Grid Standardization)** of
[visualization-implementation-roadmap.md](./visualization-implementation-roadmap.md). Phase 5 is the
roadmap's "highest-leverage first adoption surface" — it turns the density tokens (Phase 3) and the
state vocabulary (Phase 4) into real table/grid behavior. Like Phase 3 it crosses the render-location
boundary and is delivered through both systems, so it is scoped **in parts by render location**.

Phase 5 delivers the roadmap's Phase 5 tasks:

1. **Visual structure** — subtle gridlines, header hierarchy, scan-friendly row rhythm, numeric
   alignment / tabular numerals.
2. **State adoption** — apply the shared state families (`hover`, `selected`, `active`, `contextual`,
   `sorted`, `filtered`, `pinned`, `inline-edit`) to real table/grid surfaces.
3. **Boundary rule** — dense visualization tables must not reuse shell list/table behavior as their
   final design; consume the `--inet-viz-*` layer, not raw shell tokens.

### The two table worlds (governs every task below)

Verified against current code (two-agent survey, 2026-07-08). Every concern routes to exactly one
world by the "is it a viewsheet assembly?" test:

- **Browser-DOM tables/lists** (worksheet detail, generic lists, portal schedule/repository/preview
  tables, trees, dialog lists) — fully CSS-themeable via `--inet-viz-*`; **never exported**. This is
  where broad state + structure adoption happens safely.
- **Viewsheet table ASSEMBLIES** (VSTable/VSCrosstab/VSCalcTable) — split *within* the component:
  - **DOM overlays** (transient, not exported): row **hover**, cell/range **selection** border,
    **active**-cell border, **sort** glyphs. CSS-themeable.
  - **Server-rendered** via `VSFormat`/`VSTableLens` (baked into the live model **and** export): cell
    **fill**, **gridlines/borders**, **header/data** distinction, **font + numeric alignment**,
    **conditional formatting**. Not CSS-themeable — a browser token change cannot alter export.

### Parts

- **Part A — Browser-DOM table/grid state adoption (CSS).** Broad, gate-off-inert. The primary payload.
- **Part B — Viewsheet-assembly DOM overlays (CSS).** Repoint the assembly's transient hover/selection/
  sort overlays; zero export impact.
- **Part C — Server-side structure + export-visible state (server bridge).** The hard, export-affecting
  half (gridline/header/alignment defaults, selection-list selected-fill bridge). Scoped here but
  **recommended as its own follow-up sub-plan** after A+B validate (see D1).

Phase 5 does **not**:

- change any chart mark or in-graph chrome (Phases 6/8)
- implement `warning`/`anomaly` conditional formatting — that is server-rendered and belongs to
  **Phase 8**; Phase 4 already defined the tokens
- adopt viz tokens into the EM (`projects/em`) Material tables — separate build, admin chrome, not BI
  output (deferred since Phase 3)
- rewrite the worksheet-detail virtual-scroll row-height JS constants (deferred since Phase 3)

**Definition of done (Parts A + B):** with the gate **off**, computed styles are byte-identical to
today (legacy `:root` aliases); no export output changes anywhere. With the gate **on**, the adopted
DOM tables/lists and the viewsheet-assembly overlays render the modern state palette (hover/selected/
active/sorted), and no server render path — hence no export — has changed.

## Grounding (verified against current code, 2026-07-08)

### Current `--inet-viz-*` consumers (post Phase 3 + 4)

Only three files consume viz tokens today: `_themeable.scss:830` (Phase 4 hover proof),
`generic-selectable-list.component.scss:30,33` and `schedule-task-list.component.scss:97,112,194,238`
(Phase 3 density). **No surface yet consumes** `--inet-viz-selected-*`, `--inet-viz-sorted-color`,
`--inet-viz-filtered-bg`, `--inet-viz-dimmed-opacity`, or the `--inet-viz-active-border`. EM has zero.

### World 1 — Browser-DOM surfaces (Part A targets)

| Surface / concern | file:line | current value | note |
|---|---|---|---|
| **Shared** `%bg-hover-primary` | `_themeable.scss:237-239` | `--inet-hover-primary-bg-color` | **exact match** to `--inet-viz-hover-bg` `:root` → parity-safe swap |
| **Shared** `.bg-selected` | `_themeable.scss:233-235` | `--inet-shell-selected-bg-color` | **exact match** to `--inet-viz-selected-bg` `:root` → parity-safe |
| **Shared** `%selected-item-colors` / `.selected` | `_themeable.scss:418-425` | `--inet-selected-item-bg-color`, `--inet-selected-item-text-color` | uses `-item-*` tokens; verify equality to viz `:root` before swap |
| **Shared** `.tree-node-hover` | `_themeable.scss:1219-1232` | `--inet-ui-neutral-hover-bg-color` | `--inet-hover-primary-bg-color` aliases this (`_variables.scss:286`) → parity-safe |
| **Shared** `.bg-node-selected` / `.bg-portal-node-selected` | `_themeable.scss:1234-1244` | `--inet-shell-selected-bg-color` | exact match → parity-safe |
| WS detail row hover | `_themeable.scss:829-831` | `--inet-viz-hover-bg` | **done (Phase 4)** |
| WS detail highlighted row | `_themeable.scss:833-835` | `--inet-primary-color` | candidate (selected-style) |
| WS detail odd/even bg, borders | `_themeable.scss:820-827`; `ws-details-table-data.component.scss:68-69` | `--inet-ws-table-*-row-bg-color`, `--inet-default-border-color` | structure (gridline token gap — see D4) |
| generic-selectable-list hover / selected | `generic-selectable-list.component.scss:46-52` | `--inet-list-hover-bg-color`, `--inet-list-selected-bg-color` | density done; state candidate |
| schedule-task-list hover / selected | `schedule-task-list.component.scss:216-227` | `--inet-table-hover-bg-color`, `--inet-table-selected-bg-color` | candidate |
| schedule-task-list sorted indicator | `schedule-task-list.component.scss:251-288` | `--inet-text-muted-color` + `opacity 1/0.22` | candidate → `--inet-viz-sorted-color` |
| schedule-task-list disabled/dimmed | `schedule-task-list.component.scss:298-300,355-357` | hardcoded `line-through` (no opacity) | candidate → `--inet-viz-dimmed-opacity` |
| repository/data-preview tables | e.g. `query-preview-table.component.scss:30,33,79` | `--inet-table-*` (borders/heading) | structure; lower priority |

### World 2 — Viewsheet table ASSEMBLIES

DOM overlays (Part B — CSS-themeable, not exported):

| Concern | file:line | current value | viz token | parity |
|---|---|---|---|---|
| Row hover | `vs-table.component.scss:106-110` | `--inet-hover-primary-bg-color !important` | `--inet-viz-hover-bg` | **exact** (`:root` matches) ✓ |
| Cell/range selection + active border | `_themeable.scss:458-472`, `.bd-selected-cell:112-114` | `border 2px solid --inet-primary-color !important` | `--inet-viz-selected-border` | **exact** (`:root` = `--inet-primary-color`) ✓ |
| Header sort glyph | `base-table.scss:124` (`td { color:#333 }`, glyph inherits) | **hardcoded `#333`** | `--inet-viz-sorted-color` | **MISMATCH** (`:root` = `--inet-text-muted-color` `#6A685F`) — see D3 |
| Crosstab in-cell sort icon | `vs-table-cell.component.scss:79-88` | **hardcoded** `darkblue`/`white`/`black`, active `#bdbdbd` | `--inet-viz-sorted-color` | mismatch — see D3 |

Server-rendered (NOT CSS-themeable, export-visible — Part C / deferred):

| Concern | evidence | note |
|---|---|---|
| Cell fill (incl. selected cell, conditional highlight) | `vs-table.component.html:271` ← `BaseTableCellModel.java:132,280` ← `VSFormatModel.java:54` | `VSFormat` RGBA baked into model + export |
| Gridlines / borders | `vs-table.component.html:273-276` ← `VSFormat.getBorders` | server border strings |
| Header vs data distinction | `vs-table.component.html:157` (`isHeader`) — header gets own `VSFormat` | server |
| Font + numeric alignment (`hAlign`, tabular numerals) | `vs-table-cell.component.html:24-29`; `VSFormatModel.java:67-87` | server font/align |
| Conditional formatting | applied to lens before `lens.getFormat` (`BaseTableCellModel.java:132`) | → **Phase 8** |
| Filter indicator | none in the assembly table — filtering lives in separate selection/range assemblies | nothing to do here |

## Decisions (resolved 2026-07-08 with initiative owner)

All five confirmed as recommended.

### D1 — Phase 5 first pass = Parts A + B (CSS, zero export impact); defer Part C → **RESOLVED: YES**

A + B are the CSS-themeable DOM/overlay work in both worlds: broad state adoption, gate-off
byte-identical, no server change, no export change. Part C (server-side structure defaults + the
selection-list selected-fill bridge) changes `VSFormat`/export output and reflows saved sheets — it
carries the same risk profile as Phase 3 Part B and is its **own follow-up sub-plan**, to be
created/scoped after A+B land and validate. This keeps the high-leverage, low-risk 80% shippable now.

### D2 — Lead Part A with the shared `_themeable.scss` primitives → **REVERSED (scope leak, 2026-07-08)**

Initially resolved YES for leverage, then **reversed** after review. The shared primitives
(`%bg-hover-primary`, `.bg-selected`, `%selected-item-colors`, `.tree-node-hover`,
`.bg-node-selected`, `.bg-portal-node-selected`) are applied on **shell navigation and Composer
authoring chrome** — `tree-node.component.html` (repository tree, data-source tree, component tree),
asset browsers, datasource/format/VPM panes — **not** visualization BI output. Repointing them made
the *visualization state palette* restyle shell/Composer navigation, which violates the design spec's
layer ownership ("navigation" is shell-owned) **and** its selection-distinctness rule (data selection
"should not be confused with Composer selection meaning"). The leverage was in the wrong direction.

**Corrected scope (owner-confirmed): STATE tokens go on data surfaces only.** Reverted the six shared
primitives, `generic-selectable-list` (generic dialog widget), and the `schedule-task-list` (portal
management table) back to their shell tokens. Kept only genuine visualization surfaces — see the
revised Part A/B below. (Phase 3 **density** tokens on those DOM lists remain: the token-home Option A
already accepted density on Composer/shell DOM surfaces; the reversal is about **state color**, which
the spec requires to stay distinct.)

### D3 — Sort-glyph tokenization has a gate-off parity snag → **RESOLVED: DEFER sort glyph**

The viewsheet header sort glyph is hardcoded `#333` and the crosstab in-cell icon hardcodes
`darkblue`/`white`/`black` — none equal the `--inet-viz-sorted-color` `:root` default
(`--inet-text-muted-color`), so repointing them would change the **gate-off** (default, all users)
appearance. Deferred to the Part C follow-up (option b — deliberate normalization with sign-off — can
ride with the gated server work). The `sorted` state is instead adopted in Part A on
`schedule-task-list` (DOM), whose sort indicator already uses `--inet-text-muted-color` = exact match.

### D4 — DOM gridline/structure is a token gap → **RESOLVED: state-only first pass**

No general gridline/border viz token exists (only `--inet-viz-pinned-divider`). First pass is **state
adoption** (where tokens exist) plus the already-shipped Phase 3 density; DOM gridline/header
restyling is a small follow-up, and `--inet-viz-gridline` / `--inet-viz-header-*` are added to the
contract only if that follow-up needs them (ties to the Phase 4 D3 candidate-token note).

### D5 — EM Material tables deferred → **RESOLVED: YES** (unchanged from Phase 3)

`projects/em` is a separate build, admin chrome, never exported; adopting viz tokens needs
cross-project token plumbing. Out of Phase 5 scope.

### Two parity refinements applied during implementation

- **WS-detail "highlighted" row left raw.** `_themeable.scss:833-835` uses `--inet-primary-color` for
  a *search-match highlight* — a different semantic from row *selection*, and not parity-safe against
  `--inet-viz-selected-bg`. Left unchanged.
- **`dimmed` opacity deferred on `schedule-task-list` disabled rows.** Those rows have no current
  opacity (only `line-through`); adding `opacity: var(--inet-viz-dimmed-opacity)` (root `0.5`) would
  introduce dimming that doesn't exist today, changing gate-off. Deferred — first pass is strictly
  repoints of existing state colors, not new visuals.

## Changes

### Part A — Browser-DOM DATA-table state adoption (CSS) — ✅ IMPLEMENTED, then re-scoped (2026-07-08)

**Scope correction (D2 reversal).** State color goes on **data surfaces only**. The shared-primitive
and management-list adoptions were reverted as a shell/Composer leak. Only browser-DOM surfaces that
display dataset rows keep viz **state** tokens.

**Kept — data-preview DOM table:**
- Worksheet-detail table row hover `_themeable.scss:830` → `--inet-viz-hover-bg` (shipped in Phase 4
  as the state proof; worksheet detail shows query/dataset rows, so it is a data surface). Gate-off
  byte-identical (`--inet-viz-hover-bg` `:root` = `--inet-hover-primary-bg-color`).

**Reverted — shell/Composer navigation (D2 leak):**
- `_themeable.scss` shared primitives `%bg-hover-primary`, `.bg-selected`, `%selected-item-colors`,
  `.tree-node-hover`, `.bg-node-selected`, `.bg-portal-node-selected` → back to shell tokens.
- `generic-selectable-list` hover/selected → back to `--inet-list-*`.
- `schedule-task-list` hover/selected/sorted → back to `--inet-table-*` / `--inet-text-muted-color`.
- (Phase 3 **density** tokens on `generic-selectable-list` and `schedule-task-list` are unchanged —
  density, not state.)

**Left raw regardless:** WS-detail highlighted row (`--inet-primary-color`, a search-match highlight,
different semantic from selection); schedule-task-list disabled dimmed (would add new opacity).

**Part A completeness (first pass).** After the D2 reversal, the browser-DOM world has essentially
**one** BI data surface with adoptable interactive state — the worksheet-detail table hover (shipped
Phase 4). The other DOM data tables are **read-only preview grids** with no row hover/selection state:
`vs-preview-table` (transparent bg only), `preview-table` (`:hover` reveals format/sort icons +
scrollbar, no row state), `query-preview-table` (no state rules). There is therefore nothing further
to adopt in Part A. This is expected: StyleBI's real BI data tables are **viewsheet assemblies**
(server-rendered — Part B for overlays, Part C for the export-visible half), not browser-DOM tables.
**Phase 5 first-pass net-new deliverable = Part B**, with the worksheet-detail hover as the lone DOM
data surface. Part A is complete, not deferred.

### Part B — Viewsheet-assembly DOM overlays (CSS, not exported) — ✅ IMPLEMENTED (2026-07-08)

Parity-safe repoints only (D3 excludes the sort glyph):
- Row hover `vs-table.component.scss:108` → `--inet-viz-hover-bg` (was `--inet-hover-primary-bg-color`;
  kept `!important`)
- Cell/range selection + active border `.bd-selected-cell:113` (extended by `.selected-cell:before`
  and `.selected-table-cell`, the table/selection/title-cell selection effect) → `--inet-viz-selected-border`
  (was `--inet-primary-color`; kept `!important` and `2px solid`)

Gate-off byte-identical (both legacy values equal the viz `:root` defaults). Gate-on: assembly hover
and cell selection pick up the modern teal palette. **No `vsFormatModel` binding is touched**, so
export is unchanged.

### Part C — Server-side structure + export-visible state — SCOPED SUB-PLAN (deferred, needs owner go-ahead)

Part C is the export-affecting half. **C2 (sort-glyph normalization) is implemented and ships with
Parts A/B; C1 (server-side table structure) is scoped and pending** (per D1, after A/B validate —
confirmed correct at runtime, small changeset). Grounding below is verified (2026-07-08) and corrects
two assumptions carried from Phase 4/5.

**Part C decisions (resolved 2026-07-08 with initiative owner):**

- **Property model:** modern table structure is gated by `viewsheet.modernVisualization` and
  **defaults on when modern is enabled**, via a dedicated boolean property (e.g.
  `viewsheet.modernTableStructure`, default `true`, meaningful only when the gate is on) — an admin
  escape hatch, mirroring the Phase 3 property style but simpler (a toggle, not a mode enum).
- **Palette:** table-structure colors are added to `visualization-palette-swatches.html` as a
  **Table Structure Tokens** group (`--viz-gridline`, `--viz-header-bg`, `--viz-header-text`, + `-dark`
  variants). Values map the example mockups'
  ([portal-light3/dark3](https://swaker854.github.io/lookfeel/flat/)) *relationships* — very subtle
  low-contrast gridlines, quiet header — into the warm-neutral visualization system, **not** the
  mockups' purple/blue hexes. See "Table structure palette — open color decisions" below.
- **C1 scope:** first pass = **gridline + header hierarchy** on the base table CSS classes
  (`TABLE`/`TABLE_HEADER`/`TABLE_DETAIL`/`CELL`/`HEADER`/`DETAIL`); crosstab/calc-table-specific
  region classes (`CROSSTAB`/`CALC_TABLE`/`GRAND_TOTAL`/`GROUP_HEADER`/`SUMMARY`) and cell bg/fg are a
  later pass. Numeric alignment already done; tabular numerals out.
- **Route:** ~~the `format.css` CSS-dictionary route~~ **corrected to a gated `VSTableLens` resolver
  after code verification** (2026-07-09) — the CSS route is dead for the default (Default-Style)
  case and cannot carry the SreeEnv gate. See C1 "Route correction".
- **C2:** ships with A/B (done).
- **Selection selected-highlight (C-note):** deferred.

**Corrections from grounding (change the scope):**

- **Selection-list `selected` fill is NOT server export-visible.** Each selection value carries one
  shared cell `VSCompositeFormat` from the `CELLPATH` (`SelectionListVSAQuery.java:349-350` →
  `AbstractSelectionVSAQuery.refreshFormat:402`, `svalue.setFormat(vfmt)`); the model emits only a
  `STATE_SELECTED` bit + `formatIndex` (`SelectionValueModel:42-71`), and the HTML/PDF/SVG helpers
  read that same format and merely toggle the checkbox on `isSelected()` (`HTMLSelectionListHelper.java:180,212`).
  The *selected highlight* is decided **client-side (CSS)**, and export shows a checkbox, not a fill.
  So the "selected-fill server bridge" deferred from Phase 4 D2 is a **DOM concern**, not a server one
  (see C-note below), and does not belong in Part C.
- **Numeric right-alignment is already server-side and unconditional** (`FormatTableLens2.java:143,150`:
  `numeric ? H_RIGHT : …` keyed on `XSchema.isNumericType`), shared by every export path. Roadmap
  Phase 5 Task 1 "right-align numeric columns" is **already satisfied** — nothing to add.

**C1 — Modern table structure defaults via a gated `VSTableLens` resolver (primary server task).**
Route **corrected after code verification (2026-07-09)**: the originally-planned `format.css`
CSS-dictionary route is **not viable** and was replaced with a Java gated resolver that mirrors
`VSDensityDefaults` — see "Route correction" below for why.

- Mechanism (verified): a default table's structure comes from the shipped **"Default Style"
  `XTableStyle`** (a serialized library blob `style/Default Style`, loaded by `LibManager`, applied in
  `DataVSAQuery.getTableLens:120-128`), **not** from the two constants the earlier draft cited. The
  Default Style values are: interior gridlines **#E6E6E6** (body `rcolor`/`ccolor`, `THIN_LINE`),
  header-row background **#FFFFFF** + bold, body foreground **#404040** (header inherits it), zebra
  odd-row **#F5F5F5**, outer border **#CCCCCC**. `VSTableLens.getFormat(r,c,spanRow)`
  (`VSTableLens.java:110-188`) emits these per-cell and is shared by the live model **and** every
  exporter — the correct single injection point.
- Implementation (revised at C1b, 2026-07-10 — see "C1b" below): the initial C1 remapped merged colors
  by value equality in `VSTableLens.getFormat`; that was **superseded** by **style-layer injection** in
  `DataVSAQuery.getViewTableLens`. The Default Style is already cloned per-assembly there
  (`DataVSAQuery.java:125`), so when the gate is on and the assembly uses "Default Style" the modern
  palette is overlaid onto that clone via `XTableStyle.put(...)` (`applyModernTableStructure`). Because
  the user-format lens (`VSFormatTableLens`) wraps **above** the style, user cell/column/row formats
  merge on top and win — the modern colors are true defaults, and the value-equality guesswork (and its
  edge case) is gone. `VSTableStructureDefaults` now exposes `isModern()` + the palette
  (`gridlineColor`/`headerBackground`/`headerForeground`/`totalBackground`); the `getFormat` hook was
  removed.
- Gate + guard: `viewsheet.modernVisualization` **and** `viewsheet.modernTableStructure` (default on when
  modern is on), and only when `"Default Style".equals(tinfo.getTableStyle())` — a table assigned a
  non-default style is never modernized.
- Colors: interior gridline + outer frame + header-row separator → `#E8E5DE` (unifies legacy `#E6E6E6`
  body and `#CCCCCC` frame/header); header-row **and** header-col background → `#F1EFEA`, foreground →
  `#6A685F`; grand-total (trailer row/col) background → `#E9E4DA` (proposed token, adjustable). Body
  foreground stays `#404040`. Header-col + trailer keys affect only crosstabs (plain tables have no
  header/trailer bands).
- Route correction (why not the CSS-dictionary route the draft chose):
  - The `CSSTableStyle`/`format.css` path (`DataVSAQuery.getTableLens:129-150`) fires **only when no
    table style is found** — but the shipped config always has "Default Style", so that path is dead
    for the default case; editing `format.css` would change nothing.
  - The cited constants were mis-mapped: `DEFAULT_BORDER_COLOR = 0xDADADA` (`VSAssemblyInfo.java:1545`)
    is the assembly's **outer** border (`objfmt`) + the unstyled interior fallback, **not** the
    interior gridlines of a Default-Style table; `DEFAULT_TITLE_BG = 0xf5f5f5` is the assembly **title
    bar** (`TITLEPATH`), **not** the column-header row.
  - `CSSDictionary` has **no** `viewsheet.modernVisualization` SreeEnv gate — it is file/theme-driven
    per-org (`PortalThemesManager` CSS entries), with no programmatic per-org style injection. It
    therefore cannot carry the required gate. The route that actually mirrors `VSDensityDefaults` is the
    Java resolver at the shared lens layer, which is what the density work itself uses
    (`VSTableLens.java:1779`).
- Correctness (resolved at C1b): the earlier value-equality edge case (a color a user deliberately set
  to a legacy default would be modernized) is **gone** — style-layer injection makes the modern colors
  defaults that user formats structurally override, so there is no guessing and no dirty-flag needed.
- Risk: export-visible and reflows saved default-styled tables, so it **must** be gated per-org (never
  the browser `.viz-modern` class) and gate-off must be byte-identical (the cloned Default Style is left
  exactly as shipped when the gate is off). Highest-risk item; own validation pass with PDF/PNG/Excel
  parity checks.

**C2 — Sort-glyph normalization (from D3) — CSS, gated, active-sort only — ✅ IMPLEMENTED (ships with A/B).**
The `sorted` color marks the **active** sort state (a meaning-bearing cue), not every sort affordance,
so it applies only to the persistent asc/desc header arrow. One global gated rule:

```scss
.viz-modern .vs-header-cell-button-sort.sort-ascending-icon,
.viz-modern .vs-header-cell-button-sort.sort-descending-icon {
  color: var(--inet-viz-sorted-color) !important;
}
```

Gate-off: unchanged (`#6a685f`). Gate-on: an actively-sorted column's arrow is `--inet-viz-sorted-color`
(`#8C5510`); the passive hover sort button on unsorted columns (`.sort-icon`, "none" state) stays neutral.

Runtime corrections (2026-07-08/09):
- The glyph color is **not** `td { color:#333 }` as first grounded — it comes from the ineticons rule
  `.icon-color-default { color: var(--inet-icon-color) !important }` (`_icons.scss:96`), whose computed
  value (`#6a685f` = `--inet-text-muted-color`) already equals the `--inet-viz-sorted-color` `:root`
  default, so gate-off parity holds. The override therefore had to (a) live in the **global** stylesheet,
  not the component-encapsulated `base-table.scss` (in `vs-table`/`vs-crosstab`/`vs-calctable`
  `styleUrls`), and (b) carry **`!important`** to beat the icon `!important`.
- The crosstab in-cell `.table-cell-sort-icon` override was **reverted**: that glyph is `display:none`
  until `div:hover` — a transient invitation to sort with no persistent active-sort state — so it is
  not colored as sorted state (left as its original hover affordance).

**C-note — selection-list `selected` highlight (reclassified, not Part C).** Because the selected
highlight is client-CSS and not in export, adopting the viz selected palette on selection-list items is
a **DOM adoption** (Part B-style, if a real client selected-highlight selector exists) — but note the
Phase 5 D2 boundary: a selection list *is* a visualization assembly (BI data surface), so unlike the
navigation trees this adoption would be in-scope. Making the selected fill actually **appear in export**
is a separate feature (a new server "selected" format path in `SelectionListVSAQuery` — new plumbing,
not a mirror of the height resolver); low priority, deferred as a feature, not part of this theming pass.

**Out of Part C:**
- **Tabular numerals** — no `font-variant-numeric`/font-feature support exists server-side (grep clean);
  a new capability, and font changes touch measuring/wrapping/export layout (the Phase 3 D3 font-risk).
  Deferred.
- **`warning`/`anomaly` conditional formatting** — server-rendered, export-visible → **Phase 8**.

**Part C gated-defaults contract (mirrors Phase 3 Part B):** a `VSDensityDefaults`-style resolver reads
the gate + a structure property (`SreeEnv orgScope`), returns modern structure colors only when on,
applied **defaults-only** with user-set/explicit-format guards, at the CSS-dictionary emission point so
live model and every export format agree. Gate-off byte-identical; saved user-styled tables unchanged.

**Table structure palette — RESOLVED (2026-07-08).** Values confirmed; in the swatches file.

1. **Gridline contrast — `--viz-gridline #E8E5DE`** (subtle warm hairline, lighter than today's solid
   `#DADADA`).
2. **Header treatment — soft warm fill `--viz-header-bg #F1EFEA`** + a `viz-gridline` bottom rule
   (warmer/subtler than today's `#f5f5f5`).
3. **Header text — `--viz-header-text #6A685F`** (muted, quiet chrome).
4. **Dark mode — deferred from first-pass C1.** The dark variants (`#3A383D` / `#2D2B30` / `#CAC4D0`)
   stay in the swatches as reference for a later dark pass but are not emitted by C1.

C1 is **implemented** — initially (2026-07-09) via a `VSTableLens.getFormat` value-equality resolver,
then **migrated to style-layer injection in `DataVSAQuery`** at C1b (2026-07-10) after grounding (see
C1b below). Dark mode stays deferred; light-mode gridline `#E8E5DE`, header-bg `#F1EFEA`, header-text
`#6A685F`, grand-total `#E9E4DA`.

## Validation

1. **Build/lint:** `cd web && npm run build` and `npm run lint` clean.
2. **Gate-off inertness:** every Part A/B repoint's legacy value equals the token `:root` default →
   computed styles byte-identical; confirm no diff on a worksheet-detail table, a selectable-list, a
   schedule list, a tree, and a viewsheet table/crosstab. **No export changes** (Parts A/B touch no
   server path).
3. **Gate-on DOM (Part A):** `body.viz-modern` → the worksheet-detail data table shows modern row
   hover (`#EEF4F6`). Shell/Composer navigation (trees, browsers, panes, schedule/dialog lists) is
   **unchanged** in both modes — the state palette must not reach it (D2 reversal).
4. **Gate-on assembly overlays (Part B):** a viewsheet table/crosstab in the viewer shows modern row
   hover and modern cell-selection border; **export (PDF/PNG/Excel) is unchanged** (overlays are DOM).
5. **Boundary check:** no viewsheet-assembly `vsFormatModel` binding (cell fill/border/font/align) was
   modified — grep the diff for `vsFormatModel` and confirm zero hits.
6. **Regression:** saved viewsheets and exports byte-identical in all modes for Parts A/B (server
   untouched).

## Deferred / follow-ups (prioritized: correctness → consistency → best practices → least)

C1, C2, and C1b shipped. The backlog below is ordered by impact; items 1–3 are **done** (C1b) and kept
here for traceability.

1. ✅ **Value-equality → dirty-flag hardening** — *correctness — RESOLVED (C1b, D7).* Moot: style-layer
   injection makes modern colors defaults that user formats structurally override — no value equality,
   no dirty flag needed.
2. ✅ **Crosstab header-column treatment** — *consistency — DONE (C1b, D6 full treatment).* `header-col`
   background/foreground set on the modern style; the left dimension column gets tint + muted text and
   zebra is suppressed there.
3. ◑ **Crosstab/calc region emphasis** — *consistency — PARTIAL (C1b, D8).* Grand-total done (trailer
   background `#E9E4DA`, positional). **Group-header / summary (interior subtotals) remain** — they are
   body-position, unreachable by positional style keys, so they need a region-type path
   (`getTableDataPath().getType()` or `ROW_GROUP_TOTAL`/`COL_GROUP_TOTAL` specs). Deferred as item 3b.
3b. **Group-header / summary emphasis** — *consistency.* Region-type application (see above); should
    reuse the total token or a new subtotal token. Next micro-step after C1b validates.
4. **Header-separator strength decision** — *consistency/design.* Header→body rule is currently the same
   subtle `#E8E5DE` as interior; decide if it needs a stronger rule (own token).
5. **Dark-mode table structure** — *consistency.* Dark variants (`#3A383D`/`#2D2B30`/`#CAC4D0`) are in the
   swatches but C1 is light-only; do with the initiative's dark pass.
6. **DOM gridline/header structure tokens** — *best practices/contract.* Add `--inet-viz-gridline` /
   `--inet-viz-header-*` only if a browser-DOM structure follow-up needs them (D4).
7. **Selection-list selected-highlight in export** — *best practices/completeness.* Selected fill is
   client-CSS only; export-visible selection is a new `SelectionListVSAQuery` server path (C-note).
8. **Tabular numerals** — *best practices/enhancement.* No server `font-variant-numeric` support; needs a
   font-feature capability and touches measuring/wrapping/export (font risk). Own spike.
9. **WS-detail virtual-scroll row-height JS constants** — *tech-debt.* TS→token refactor; since Phase 3.
10. **EM Material tables** — *least/scope.* Separate build, admin chrome, never exported; cross-project
    token plumbing (D5).
11. **`warning`/`anomaly` conditional formatting** — *separate phase.* Server-rendered, export-visible →
    Phase 8.

(Numeric right-alignment is already done — `FormatTableLens2`. Filter indicator: none exists in the table
component; filtering lives in separate assemblies — no action.)

### C1b — grounded, decided, and IMPLEMENTED (2026-07-10)

Decisions D6/D7/D8 below were resolved with the owner and implemented; the change **migrated C1 from the
`getFormat` value-equality resolver to style-layer injection** in `DataVSAQuery`. Same org gate;
gate-off byte-identical (the cloned Default Style is left exactly as shipped).

**Grounding (2026-07-10) revised all three items.** Root finding: `XTableStyle` resolves cell
colors/fonts **positionally** — outer border → header row → trailer row → header col → trailer col →
per-cell zebra spec → body (`XTableStyle.java:790-1041`; predicates `TableStyle.java:168-199`) — and
**never by semantic region type**. The shipped Default Style defines specs for only `header-row`
(bg `#FFFFFF`, bold), `body` (fg `#404040`, gridline `#E6E6E6`), the odd-row zebra (`#F5F5F5`), and the
outer border (`#CCCCCC`); every other position falls through to `body`. This invalidates the original
C1b-2/C1b-3 value-equality assumptions and turns each item into an open decision:

- **C1b-2 — Crosstab row-header (left dimension) column — DECISION D6.** These cells are header
  *columns* positionally, but the Default Style has no `header-col` spec, so they render as **body**:
  fg `#404040` (coincidentally the header value), background body/zebra (`#F5F5F5` odd rows), **not**
  the header `#FFFFFF` fill and **not** bold (`XTableStyle` fall-through; `CrossTabFilter.java:558-571`
  bands; `CrosstabVSAQuery` sets no colors). So the earlier "just widen the predicate to
  `c < getHeaderColCount()`" plan only remaps the *text* (`#404040`→`#6A685F`), giving muted text on a
  zebra body — a partial, possibly-odd look. A real header appearance requires *affirmatively* applying
  the header fill where none exists (overriding zebra on those cells) — a new visual, not a
  value-equality modernization. Options: (a) leave as body-region; (b) muted-text-only via predicate
  widen; (c) full header treatment (affirmative tint + muted text, suppresses zebra there).
- **C1b-1 — Dirty-flag hardening (correctness) — DECISION D7.** API reachable (`getFormatInfo()`,
  `VSFormat.isBorderColorsDefined/isBackgroundDefined/isForegroundDefined`), but `FormatInfo.getFormat`
  is **exact-path match** (`FormatInfo.java:234` `fmtmap.get(tpath)`), so a correct "user set this color"
  check must walk the cell→column→row→object chain per cell (more code + per-cell cost). Cleaner
  alternative: inject modern colors at the **Default Style application layer** (`DataVSAQuery`
  per-assembly) so user formats — merged on top — win naturally; inherently correct, no value equality,
  but a different/larger injection point. Options: (a) keep value-equality as-is (accepted limitation);
  (b) path-chain user-defined check; (c) style-layer injection.
- **C1b-3 — Region emphasis (grand-total / group-header / summary) — DECISION D8.** These regions carry
  **no distinct default colors** — all inherit body (`#404040`/`#E6E6E6`; grand totals land in the
  style's trailer positions but the Default Style has no trailer spec → body). So there is nothing to
  remap by equality; emphasis must be applied **affirmatively** by region type
  (`getTableDataPath(r,c).getType()` — `HEADER`/`DETAIL`/`GRAND_TOTAL`(=`TRAILER`, same value
  `0x0400`)/`GROUP_HEADER`/`SUMMARY`; `TableDataPath.java:42-102`, `CrossFilterDataDescriptor.java:193-297`),
  which needs **new modern palette tokens** (total/group-header bg) from the design.

**Decisions resolved (2026-07-10):** D6 = (c) **full header treatment**; D7 = (c) **style-layer
injection**; D8 = **define token now + implement** (grand-total done; group-header/summary deferred as
item 3b since they are body-position and need a region-type path, not the positional style layer).

**Implemented.** `DataVSAQuery.getViewTableLens` overlays the modern palette onto the per-assembly
Default Style clone (`applyModernTableStructure`) when the gate is on and the assembly uses "Default
Style". Keys set: `body.rcolor`/`ccolor` + `header-row.rcolor`/`ccolor` + the four outer
`*-border.color` → gridline `#E8E5DE`; `header-row`/`header-col` background `#F1EFEA`, foreground
`#6A685F`; `trailer-row`/`trailer-col` background `#E9E4DA`. `VSFormatTableLens` wraps above the style,
so user formats win (D7 correctness). Header-col + trailer keys no-op on plain tables (no such bands).
The old `VSTableLens.getFormat` hook + value-equality helpers were removed; `VSTableStructureDefaults`
now exposes `isModern()` + the four palette accessors. `#E9E4DA` is a **proposed** grand-total color
(no design spec yet) — adjustable. Not covered: the `style == null` CSS-fallback branch (rare no-style
tables) and item 3b.

## Branching (per CLAUDE.md)

Parts A + B are community-only CSS (SCSS in the `community/` submodule); enterprise submodule-pointer
bump only at PR time. Part C, when taken, adds core Java (server-side) — same community branch.
Continues the visualization-theming work; confirm the branch (continue `epic-74519` vs a child
`feature-{issue}`). Nothing on `main` or a `v1.0.x`/`v1.1.x` release branch; nothing pushed/PR'd
without approval.

## Related

- [visualization-implementation-roadmap.md](./visualization-implementation-roadmap.md) — Phase 5 tasks, two-table-worlds boundary, sequencing
- [visualization-design-spec.md](./visualization-design-spec.md) — Tables And Grids guidance, state grammar, render-location boundary, density matrix
- [visualization-phase1-contract.md](./visualization-phase1-contract.md) — state + density token `:root` defaults (parity baselines)
- [visualization-phase3-implementation-plan.md](./visualization-phase3-implementation-plan.md) — density adoption + the server-side gated-default mechanism Part C mirrors
- [visualization-phase4-implementation-plan.md](./visualization-phase4-implementation-plan.md) — state vocabulary + routing table + the DOM-overlay-vs-server split this phase adopts
