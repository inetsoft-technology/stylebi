# StyleBI Visualization — Phase 3 (Density Foundation) — Implementation Plan

## Scope

This plan executes **Phase 3 (Density Foundation)** of
[visualization-implementation-roadmap.md](./visualization-implementation-roadmap.md). Phase 3 is the
first phase that changes runtime rendering, and the first that crosses the render-location boundary:
density must be delivered through **two independent systems** — a server-side default for viewsheet
data-surface assemblies (the primary BI surfaces, shared with export) and browser CSS tokens for
non-assembly DOM tables/lists. The two halves are gated by one switch and must stay in agreement.

Phase 3 delivers the roadmap's four Phase 3 tasks:

1. **Density tokens** — a `comfortable`/`compact`/`dense` value matrix for row height, chrome row
   height, cell padding x/y, toolbar/control height, and font size.
2. **Density rules** — density comes from spacing/alignment before type reduction; controls stay
   hittable; dense chrome stays separate from shell control sizing.
3. **Configuration source** — an EM property holds the default density mode; it is applied through
   **both** render paths (browser CSS tokens *and* server-side assembly defaults), gated server-side.
4. **Local override boundary** — CSS override allowed only for scan-heavy DOM list/table surfaces.

Phase 3 does **not**:

- assign widget-*state* values (`hover`/`selected`/etc. — Phase 4)
- touch any chart color or in-graph chrome (Phases 6/8)
- add a new EM density *UI control* beyond the single property (roadmap §3: "do not require a new EM
  UI surface in the first pass")
- rewrite the worksheet-detail virtual-scroll table or EM Material tables (see Deferred — both need
  structural work, not a token swap)

**Definition of done:** with the gate **off** (default), computed styles and server-rendered row
heights are byte-identical to today. With the gate **on** and a density mode selected, (a) the
adopted DOM list/table surfaces reflect the mode's CSS density tokens, and (b) viewsheet table and
selection-list assemblies that use the **default** height render at the mode's server-side height in
the live view **and** in every export format — while any user-set row height/font is preserved.

## Grounding (verified against current code, `epic-74519`)

Phases 0–2 are implemented on `epic-74519`: `_viz-tokens.scss` exists and is imported at
`global.scss:19`; the `viewsheet.modernVisualization` property + `body.viz-modern` gate are wired
(EM Look & Feel → `PortalModel`/`CoreLifecycleService` → body class). `_viz-tokens.scss` has **zero
selector consumers** today (verified), so Phase 3 is the first adoption.

### Server-side density facts

| Fact | Evidence |
|---|---|
| Default row/cell height is a hardcoded constant, **not** a property | `AssetUtil.defh = 20` at `core/.../uql/asset/internal/AssetUtil.java:3492-3493` |
| Table row-height API + user-set flags | `TableDataVSAssemblyInfo` (`core/.../uql/viewsheet/internal/`): `dataRowHeight = AssetUtil.defh` (L1651), `getDataRowHeight(int)` L816, `getHeaderRowHeights(boolean)` L731, `userDataRowHeight`/`userHeaderRowHeight` flags L1653-1654 |
| Selection-list cell height + user path | `SelectionBaseVSAssemblyInfo.getCellHeight/setCellHeight` L121/L129, `cellHeight = AssetUtil.defh` L1004, serialized `cellHeight=` L530 |
| Default cell font (size 10) is a render-time fallback, not stored | `Util.DEFAULT_FONT = new StyleFont(DEFAULT, 0, 10)` `core/.../report/internal/Util.java:68`; substituted where `VSFormat.getFont()` is null (e.g. `HTMLCoordinateHelper.java:1245`) |
| **Single live choke point** (both interactive + populates model) | `BaseTableService.java:449-495` reads `tinfo.getDataRowHeight(...)`/`getHeaderRowHeights(...)`, applies existing CSS overrides (`getCSSHeaderRowHeight`/`getCSSDataRowHeight`/`getCSSRowPadding`, L460-478), feeds `BaseTableModel.dataRowHeight`/`headerRowHeights` |
| **Single export choke point** (reads same values) | `VsToReportConverter.calculateRowHeights(TableDataVSAssemblyInfo, VSTableLens)` L1193; rows at `AssetUtil.defh` are treated as "default" (L1200, L1209-1211) |
| Existing runtime override hook already exists | `VSTableLens.getCSSDataRowHeight/getCSSHeaderRowHeight/getCSSRowPadding` (server-side CSS dictionary), consumed at `BaseTableService.java:460-478` |
| No render path reads the gate today | only 3 reads of `getBooleanProperty("viewsheet.modernVisualization")`: `CoreLifecycleService:306`, `PortalController:116`, `LookAndFeelService:59` — all forward the flag to the client; none touch height/font |
| No existing configurable density/row-height property | searches for `table.row.height`/`viewsheet.*.height`/`density` found no `SreeEnv` key |

### Browser-DOM density facts

| Fact | Evidence |
|---|---|
| `--inet-viz-*` has zero consumers | grep across `web/` returns only the definition file |
| Only one row-height shell token exists | `--inet-row-height-md: 34px` (`_variables.scss:476`); control heights `-sm 24 / -md 30 / -lg 36` (L473-475); spacing `--inet-space-1..8` = 2/4/6/8/12/16/·/24px (L492-498) |
| Shell table tokens are color-only | `_variables.scss:373-378` (`--inet-table-*`); `_themeable.scss:963-971` consumes them; **no** row-height/padding in `_themeable.scss` |
| `.table-data__row` color hook is real | `_themeable.scss:820-835` (`--inet-ws-table-*-row-bg-color`, `--inet-hover-primary-bg-color`, `--inet-primary-color`) |
| Worksheet detail row height is a **JS constant**, not CSS | `ws-details-table-data.component.ts:145-146` `HEADER_CELL_HEIGHT=30`, `TABLE_CELL_HEIGHT=21` (virtual-scroll math) → **not** a token swap |
| `generic-selectable-list` is already token-driven (clean target) | `.../generic-selectable-list.component.scss:30` `min-height: calc(var(--inet-control-height-sm) + var(--inet-space-1))`, `:33` `padding-inline: var(--inet-space-4)` |
| `schedule-task-list` partly token-driven (secondary target) | `.../schedule-task-list.component.scss:42` `min-height: calc(var(--inet-control-height-lg) + var(--inet-space-3))`, plus hardcoded `min-height:30px` at L97/112 |
| EM tables are Angular Material in a **separate build** | `projects/em` uses `mat-mdc-table`; does not import `projects/portal/.../_viz-tokens.scss` → cross-project plumbing, deferred |

## Decisions (resolved)

Resolved with the initiative owner (2026-07-07). These are settled; the sections below implement them.

### D1 — Density matrix calibration → **RESOLVED: recalibrate to a 20px-parity unified matrix**

The design spec's original matrix set **dense = 26px**, but the current viewsheet default is
`AssetUtil.defh = 20px` and the DOM worksheet table is `21px` — so the spec's *dense* was **taller**
than today, and applying it literally would make BI rows *roomier*, contradicting "denser than
shell / dense is the primary BI target." Resolution: **one unified matrix recalibrated to StyleBI's
reality** (below), where **dense = 20px = today's default** (zero reflow on enable), and
compact/comfortable step up for legibility. Both halves (DOM CSS and server-side assemblies) use the
same matrix — no separate server draft. (Supersedes the design spec's original 26–30 values; the
design-spec Target Specs table has been updated to match.)

### D2 — Density mode plumbing → **RESOLVED: org-level `viewsheet.density`, default `dense`**

A second `SreeEnv` property `viewsheet.density` (`comfortable`|`compact`|`dense`), read the same way
as the Phase 2 gate (per-org in enterprise, global in community), meaningful only when the gate is
on. **Default `dense`** — with the D1 parity matrix, enabling modern reflows *nothing* on
default-height viewsheet tables; the visible win is DOM surfaces normalizing to one coherent system.

**Density is org-level, not per-component — because per-component control already exists.** Three
layers, high → low precedence:

1. **Per-component, user-set** row/cell height (exists today: `TableDataVSAssemblyInfo.setRowHeight`/
   `setDataRowHeight` + `userDataRowHeight`; `SelectionBaseVSAssemblyInfo.setCellHeight`) → always wins
2. **Org/theme `viewsheet.density` mode** (new default) → applies only where the user hasn't set a value
3. **Legacy `AssetUtil.defh = 20`** → when the gate is off

No new per-component density-mode UI is added: it would be redundant with the existing (finer-grained)
per-row-height control and is out of first-pass scope.

### D3 — Font-size density → **RESOLVED: defer server-side font-size**

Ship row height + padding only. `Util.DEFAULT_FONT` (size 10) is a null-fallback and cell fonts flow
through `VSFormat`; driving font size from density touches every text-measuring path (wrapping,
autosize, export layout) and is materially higher-risk. The spec itself says "most density change
should come from row height and padding before type-size reduction." Server-side font-size density is
a follow-up. In the DOM half, font drops to 12px only at the `dense` tier (CSS-only, low risk).

### §5 (carried from Phase 1 contract) — Token home → **RESOLVED: lean Option A, revisit at Phase 5**

Keep DOM density/state under `--inet-viz-*` (one "viz density" concept, explicitly scoped) rather
than moving DOM-table density to shell/Composer. Formally revisit before broad Phase 5 selector
adoption; Phase 3 scaffolding is unaffected either way.

## Density token matrix (resolved — D1)

One unified matrix for both halves (browser-DOM CSS tokens **and** server-side viewsheet-assembly
defaults). `dense` equals today's `AssetUtil.defh = 20px`, so enabling modern with the default mode
reflows no default-height viewsheet table.

| Token role | Comfortable | Compact | **Dense (default)** | Notes |
|---|---|---|---|---|
| table row height | 28 | 24 | **20** | primary scan-density control; dense = today's `defh` |
| header row height | 30 | 26 | **22** | headers slightly taller than data rows |
| cell padding x | 8 | 6 | **4** | horizontal rhythm |
| cell padding y | 6 | 4 | **3** | vertical compaction |
| toolbar/control height | 30 | 28 | **24** | DOM chrome only (no server counterpart) |
| font size | 13 | 13 | **12** | DOM half only (D3: server font-size deferred) |

## Changes

### Part A — Browser-DOM half (CSS tokens + adoption) — ✅ IMPLEMENTED

Implemented and verified on `epic-74519` (core `mvnw compile` + `npm run build` both clean). A1–A4
below reflect what actually shipped; deviations from the original draft are called out inline. Parts
B and C are not started.


**A1. `_viz-tokens.scss` — add modern per-mode values under the gate.** Keep `:root` legacy
defaults untouched (inertness preserved). Add mode matrices scoped to the gate, selected by a
density body class carried from the server (A4):

The "toolbar/control height" matrix row drives **both** `--inet-viz-toolbar-height` (chrome/toolbar
buttons) and `--inet-viz-control-height` (list-control rows). The original plan omitted
`--inet-viz-control-height` from the matrices, but A2 consumes it (`generic-selectable-list`), so it
must be set per mode or that surface would be inert. Both are written to the same matrix value.

```scss
// Modern density matrices (DOM surfaces only). Legacy :root defaults are unchanged above.
// Base .viz-modern = the default mode (dense); the density class only needs to override the
// non-default modes, but all three are written explicitly for clarity.
.viz-modern,
.viz-modern.viz-density-dense {
  --inet-viz-row-height: 20px;  --inet-viz-chrome-row-height: 22px;
  --inet-viz-cell-padding-x: var(--inet-space-2); // 4px
  --inet-viz-cell-padding-y: 3px;
  --inet-viz-toolbar-height: 24px;
  --inet-viz-control-height: 24px;
  --inet-viz-font-size: 12px;   // only tier that reduces type
}
.viz-modern.viz-density-compact {
  --inet-viz-row-height: 24px;  --inet-viz-chrome-row-height: 26px;
  --inet-viz-cell-padding-x: var(--inet-space-3); // 6px
  --inet-viz-cell-padding-y: var(--inet-space-2); // 4px
  --inet-viz-toolbar-height: 28px;
  --inet-viz-control-height: 28px;
  --inet-viz-font-size: var(--inet-font-size-base); // 13px
}
.viz-modern.viz-density-comfortable {
  --inet-viz-row-height: 28px;  --inet-viz-chrome-row-height: 30px;
  --inet-viz-cell-padding-x: var(--inet-space-4); // 8px
  --inet-viz-cell-padding-y: var(--inet-space-3); // 6px
  --inet-viz-toolbar-height: 30px;
  --inet-viz-control-height: 30px;
  --inet-viz-font-size: var(--inet-font-size-base); // 13px
}
```

**Note on DOM-surface parity:** the D1 "dense = 20px = zero reflow" parity holds for **server-side
viewsheet assemblies** (`AssetUtil.defh = 20`). It does **not** hold for these browser-DOM surfaces:
their legacy shell values differ from the dense-mode values (e.g. cell padding 8→4, toolbar 30→24),
so enabling modern at the default `dense` mode visibly tightens the adopted DOM lists. Only the
**gate-off** state is byte-identical for DOM surfaces (legacy `:root` aliases).

**A2. Adopt tokens in the two low-friction DOM targets** (roadmap §4 local-override boundary — both
are scan-heavy list surfaces). Each repoint keeps the legacy shell value, so gate-off is
byte-identical:

- `generic-selectable-list.component.scss` — item `min-height`:
  `--inet-control-height-sm`→`--inet-viz-control-height` (24=24); `padding-inline`:
  `--inet-space-4`→`--inet-viz-cell-padding-x` (8=8).
- `schedule-task-list.component.scss` (the **portal** copy under `portal/schedule/`) — `thead th` and
  `tbody td` `padding`: `var(--inet-space-3) var(--inet-space-4)`→
  `var(--inet-viz-cell-padding-y) var(--inet-viz-cell-padding-x)` (6/8 identical), so data-row height
  densifies via padding (the table has no explicit row-height rule); the two hardcoded
  `min-height: 30px` action buttons → `--inet-viz-toolbar-height` (30 identical). The panel-header
  `min-height` (`calc(--inet-control-height-lg + …)`) is **left as-is** — it is chrome, not a data
  row, and has no legacy-parity viz token. (Original plan cited lines 42/97/112 and
  `--inet-viz-row-height`; the file had drifted and that repoint would have broken gate-off inertness,
  so the padding-driven adoption above was used instead.)

**A3. Carry the density mode to the browser.** Mirror the `modernVisualization` wiring:
`PortalController`/`CoreLifecycleService` read `viewsheet.density` and pass it in `PortalModel` /
the viewsheet info map (new field `vizDensity`).

**A4. Apply the density body class.** When modern is on, add `viz-density-<mode>` (whitelisted to the
three valid modes) alongside `viz-modern` on the document body, at every portal-build root:

- `PortalAppComponent.updateVisualizationMode()` (portal shell — reads `this.model`).
- `viewer-app.component.ts` (standalone viewer — reads the viewsheet info map `vizDensity`, guarded by
  `!inPortal` so embedded viewers inherit the portal body).
- `composer/app.component.ts` (composer — has no `PortalModel`, so it fetches
  `../api/portal/get-portal-model` on `ngOnInit` and applies the class). Added because composer
  dialogs (e.g. the hyperlink-parameters `generic-selectable-list`) otherwise render at legacy
  density; the composer shares the `projects/portal` `global.scss`, so the tokens are already present.

The class-apply logic is now duplicated across these three roots (matching the pre-existing
portal/viewer duplication). A shared helper is a candidate consolidation follow-up.

### Part B — Server-side half (viewsheet assemblies — the primary path)

**B1 + B2a (tables) = IMPLEMENTED (2026-07-08); B2b (selection lists) not started.** Core compiles
clean; gate-off is byte-identical (every substitution short-circuits on `isModern()==false`, the
default).

**B1. Density resolver — DONE.** `core/.../uql/viewsheet/internal/VSDensityDefaults.java`:

- `isModern()` reads `viewsheet.modernVisualization` and `mode()` reads `viewsheet.density` via
  `SreeEnv.getBooleanProperty/getProperty(name, false, true)` (`orgScope=true`, same resolution as
  Phase 2 — per-org in enterprise, global in community);
- `rowHeight()` / `headerRowHeight()` return the unified matrix value for the active mode
  (comfortable 28/30, compact 24/26, dense 20/22), or `AssetUtil.defh` when the gate is off. Matches
  the `_viz-tokens.scss` matrix so DOM, live, and export agree. (Dropped the draft's
  `defaultCellHeight()`/`cellPaddingX/Y()` — cell height is a B2b concern and padding is DOM-only.)

**B2. Resolve at the choke points — defaults only.** No change to stored
`dataRowHeight`/`cellHeight` fields or serialization (would rewrite saved sheets). Resolve at
read/model-build time, preserving user-set values.

**B2a — Table assemblies — DONE.** Three client/export reads, each guarded by
`isModern() && !userDataRowHeight/!userHeaderRowHeight && value == AssetUtil.defh` (so user-set,
per-row/date-comparison overrides, and CSS-dictionary heights all still win):

- `BaseTableService.java` `LoadTableDataCommand` path (~L459): substitute `rowHeight()` for
  `dataRowHeight` and fill still-default `headerRowHeights[]` with `headerRowHeight()`, **before** the
  existing CSS-override block so CSS wins.
- `BaseTableService.loadTableModelProperties` (~L1160): same `dataRowHeight` substitution on the
  initial `BaseTableModel`, so the object model and the data-load command agree.
- `VSTableLens.initTableLensRowHeights` (~L1745): the **real shared export choke point** — both the
  live model (`BaseTableService` calls `lens.initTableGrid`) and every export format helper read row
  heights through the lens `rows[]`, and it already honours the user flags + CSS. (Correction to the
  draft: `VsToReportConverter.calculateRowHeights:1193` is **print-layout-only**, so it is not the
  general export path; the lens is. Not edited.)

**B2b — Selection lists — scoped, Option A (real dirty flag). Own commit, isolable from B2a.**

`SelectionBaseVSAssemblyInfo` has no user-set flag (unlike tables' `userDataRowHeight`), and
`getCellHeight()` is read at ~10 sites — render, layout, export, *and* the composer property dialogs.
Resolving inside `getCellHeight()` would leak density into the dialog's cell-height field (shown as
user-set, persisted on save) and into `maxLines`/min-list-height math. So the resolution is
centralized in one new method, and each read site is repointed by role.

*Design — one resolver method.* Add to `SelectionBaseVSAssemblyInfo`:

```java
public int getEffectiveCellHeight() {
   return VSDensityDefaults.isModern() && !userCellHeight && cellHeight == AssetUtil.defh
      ? VSDensityDefaults.cellHeight() : cellHeight;
}
```

`getCellHeight()` stays raw. Render/export/layout sites call `getEffectiveCellHeight()`; design-edit
and serialization sites keep `getCellHeight()`. Add `VSDensityDefaults.cellHeight()` delegating to the
existing data-surface matrix (`rowHeightForMode`: 20/24/28) — selection default is also `defh=20`, so
dense = parity.

*Repoint to `getEffectiveCellHeight()` (render — density shows in viewer, export, and composer
canvas, per the design-time-WYSIWYG rule):* `VSSelectionBaseModel:52` (live + canvas model),
`VSSelectionListModel:150` (min-list-height), `SelectionValueModel:60` (`maxLines`),
`SelectionListVSAssemblyInfo:384` + `SelectionTreeVSAssemblyInfo:896` (internal `getRowHeights()`),
`ExcelSelectionTreeHelper:160,174`, `HTMLSelectionListHelper:150,179`, `HTMLSelectionTreeHelper:142`,
`VSSelectionTreeHelper:98`. The `maxLines`/min-height sites *must* use the same effective height as
the row render or wrap/scroll math desyncs.

*Leave RAW `getCellHeight()`:* `SelectionList/TreePropertyDialogService:120/121` (dialog field) and
`:237/:267` (`minListHeight` validation); **serialization** `SelectionBaseVSAssemblyInfo:530` (write)
/ `:548` (parse) — the one true hazard: a leaked effective height here rewrites saved sheets;
`copyInfo`/clone `:759-760`.

*Out of scope:* `HTMLCoordinateHelper:727/748/760` and `VSCompound:109` read
`ListInputVSAssemblyInfo.getCellHeight()` (checkbox/radio input widgets) — a different assembly
family, unaffected by selection density.

*Dirty flag (Option A).* Add `userCellHeight` field + write/parse serialization + `copyInfo`. Set
`true` in `ComposerVSSelectionListService:103` (drag-resize, mirrors the table resize handler at
`ComposerVSTableService:962/973`). In the two dialog `setModel` paths (`:218`/`:244`) use
**change-detection** — `if(newCellHeight != info.getCellHeight()) setUserCellHeight(true)` — so a
no-op OK doesn't freeze the default at `defh`, but an explicit choice (even 20) sticks. This closes
the "user deliberately picked 20" ambiguity that a bare `cellHeight != defh` heuristic (Option B,
rejected) cannot.

*Verify:* save a default-height selection list under `comfortable`, reopen with the gate off →
`cellHeight` still 20 (serialization untouched); HTML/PDF/Excel export parity with the live view;
composer canvas matches the viewer; a user-set cell height is preserved in every mode; gate-off
byte-identical.

**B3. (Alternative to evaluate, not both) — CSS-dictionary bridge.** The server already honours
`VSTableLens.getCSSDataRowHeight/getCSSHeaderRowHeight/getCSSRowPadding` from the server-side CSS
dictionary (`format.css`). An alternative to B2 is to emit the modern default row height/padding
into the server-side chart/table CSS classes so the *existing* override hook applies it. This reuses
a proven path and would later align with the Phase 6/8 `format.css` bridge — but it is more indirect
and couples density to the CSS dictionary. Recommendation: use **B2** (explicit resolver at the
choke point) for Phase 3; note B3 as the convergence option to revisit in Phase 6/8.

**B4. Preserve user-set values (regression guard — the top layer of D2's precedence).** The
`userDataRowHeight`/`userHeaderRowHeight` flags and a non-`defh` `cellHeight` are the "user set it"
signal. The resolver applies **only** when those indicate the default is still in force — so a
per-component row height (layer 1) always wins over the org density mode (layer 2). This satisfies
roadmap §3 "apply modern density to defaults only" and is why no per-component density-mode UI is
needed (D2).

### Part C — EM property surface — scoped, completes Phase 3

Pure property plumbing that mirrors the Phase 2 `modernVisualization` checkbox end-to-end plus one
`mat-select`. It lets an admin *set* the `viewsheet.density` value that Part A (DOM) and Part B
(assemblies) already *consume*. All in `community/` (core + em). `mat-select` is already used in
`look-and-feel-settings-view.component.html`, so no module-import change is needed.

**C1. Backend (`core`, 3 edits).**

- `LookAndFeelSettingsModel.java` (after `modernVisualization()`, ~L54):
  `@Value.Default default String visualizationDensity() { return "dense"; }` — `@Value.Default` (not
  a mandatory attribute or `@Nullable`) so the Immutables build never fails on a missing value and
  the default is centralized. `Value` is already imported.
- `LookAndFeelService.getModel` (~L59, beside the `modernVisualization` read):
  `String visualizationDensity = SreeEnv.getProperty("viewsheet.density", false, !globalProperty);`,
  default `"dense"` if null/empty; add `.visualizationDensity(visualizationDensity)` to the builder
  (~L138). The `!globalProperty` scope gives density the same per-org (enterprise) / global
  (community) behavior as the gate, for free.
- `LookAndFeelService.setModel` (~L164, beside the `modernVisualization` write):
  `SreeEnv.setProperty("viewsheet.density", model.visualizationDensity(), !globalSettings);`.
  `setModel` is already `@Audited(OBJECT_TYPE_EMPROPERTY)`, so density changes are audited.

**C2. Frontend (`em`, 3 files).**

- `look-and-feel-settings-model.ts` — add `visualizationDensity: string;` after `modernVisualization`.
- `look-and-feel-settings-view.component.ts` — mirror `modernVisualization` in 3 spots: form group
  (`visualizationDensity: ["dense"]`, ~L134); `setModel` populate (~L85, `?? "dense"`) and the
  null-model branch (~L107, `"dense"`); `emitModel` (~L221).
- `look-and-feel-settings-view.component.html` — after the checkbox (L38), a `mat-select` shown only
  when modern is on, mirroring the existing `@if (form.controls.repositoryTree?.value)` pattern for
  "Expand All Nodes":

```html
@if (form.controls.modernVisualization?.value) {
  <mat-form-field appearance="outline" color="accent">
    <mat-label>_#(Visualization Density)</mat-label>
    <mat-select formControlName="visualizationDensity">
      <mat-option value="comfortable">_#(Comfortable)</mat-option>
      <mat-option value="compact">_#(Compact)</mat-option>
      <mat-option value="dense">_#(Dense)</mat-option>
    </mat-select>
  </mat-form-field>
}
```

**Behavior.** Auto-save: `form.valueChanges → emitModel` (200 ms after init) persists on change, like
the checkbox — no save button. Show/hide via `@if` (matches the sibling "Expand All Nodes"); the
select reappears at its retained value when modern is re-checked. The select emits exactly
`comfortable|compact|dense` — the same whitelist as Part A's body class and Part B's `mode()` — which
closes the hand-typed-value ambiguity (once C ships, the property is UI-constrained). Default `dense`
everywhere (model `@Value.Default`, TS form default, service fallback), matching A and B.

**i18n.** The Phase 2 `_#(Modern Visualization)` label has **no** `srinter.properties` key (`_#()`
falls back to the literal), so the four new `_#()` labels need no keys to render in English. Adding
`Visualization Density`/`Comfortable`/`Compact`/`Dense` keys is optional (localization only);
matching the Phase 2 precedent, none are added.

This is an **org-level** default (D2). It does not add per-component density control — components keep
their existing per-row-height override, which wins (B4).

*Verify:* EM → Presentation → Look and Feel → check Modern Visualization → Density select appears
(default Dense) → set Comfortable → auto-saves → reload shows Comfortable, and viewsheet tables +
selection lists (B) and DOM lists (A) render at comfortable; uncheck Modern → select hides.

## Local override boundary (roadmap §4 / design spec)

CSS-token density is applied **only** to scan-heavy DOM list/table surfaces (Part A2 targets, and
future DOM tables). Do **not** apply `--inet-viz-*` density to charts, KPI widgets, short ordinary
dropdowns, standard shell form controls, or small chrome fragments. Viewsheet table/crosstab/
selection-list assemblies get density from Part B (server-side), never from the CSS tokens.

## Verification

1. **Build:** `cd community/web && npm run build` and `./mvnw -q -pl core compile -DskipTests` clean.
2. **Gate-off inertness:** with `viewsheet.modernVisualization=false` (default), computed DOM styles
   are unchanged (legacy `:root` aliases), and `BaseTableService`/`VsToReportConverter` return
   `AssetUtil.defh` exactly as today. Confirm no diff in a rendered table model and a PDF export.
3. **Gate-on DOM:** enable modern + a mode → `body` carries `viz-modern viz-density-<mode>`;
   `generic-selectable-list` / `schedule-task-list` rows reflect the mode's height/padding.
4. **Gate-on server (live + export parity):** a viewsheet table and a selection list at the
   **default** height render at the mode's server height in the browser **and** in PDF/PNG/Excel
   export (the whole point — live and export share the choke points). At the **default `dense`
   mode** this is 20px = today, so no visible change; switch the org to `compact`/`comfortable` and
   both the live model and every export grow to 24/28px. A table with a **user-set** row height is
   unchanged in all modes.
5. **Regression:** saved viewsheets with explicit row heights/fonts are byte-identical in all modes.
   Default-height assemblies are unchanged at `dense` (parity) and reflow only when the org selects a
   roomier mode — never when the gate is off.
6. **Lint:** `npm run lint` clean.

## Branching (per CLAUDE.md)

Changes span the `community/` submodule (SCSS, Angular, core Java) — needs a community branch, and
an enterprise submodule-pointer bump only at PR time. This continues the visualization-theming work
currently on `epic-74519`; confirm whether Phase 3 lands on `epic-74519` or a child
`feature-{issue}` branch. Nothing is committed to `main` or a `v1.0.x`/`v1.1.x` release branch, and
nothing is pushed/PR'd without explicit approval.

## Deferred / follow-ups

- **Worksheet detail table density** — row height is the JS constant `TABLE_CELL_HEIGHT=21`
  (virtual-scroll math), not CSS. Needs a TS→token refactor, not a swap; defer to a focused follow-up.
- **EM ("Enterprise Manager") Material tables** — defer. *What these are:* the admin-UI data
  tables in the `projects/em` Angular app (a **separate build** from the end-user portal), rendered
  with Angular Material (`<mat-table>`), usually via the shared wrapper
  `projects/em/src/app/common/util/table/regular-table/`. Examples to review visually:
  **EM → Server → Schedule → Tasks** (`settings/schedule/schedule-task-list/`), **EM → Monitoring →
  Auditing** (`auditing/audit-table-view/`). *Why deferred, and not core visualization:* an EM table
  is **admin chrome**, not BI output — it is never a viewsheet assembly and never appears in export,
  so it falls outside the visualization layer proper (it is only a *secondary* browser-DOM density
  candidate). Practically, `projects/em` does not import
  `projects/portal/.../_viz-tokens.scss`, so adopting viz tokens there needs cross-project token
  plumbing. Treat as a low-priority DOM-density follow-up, not part of the Phase 3 primary surface.
- **Server-side font-size density (D3)** — deferred behind row-height/padding per the design rule.
- **Composer canvas** — the composer *document body* is now gated (A4: `composer/app.component.ts`),
  so composer **DOM** dialogs/lists (e.g. hyperlink-parameters `generic-selectable-list`) follow the
  active density. The server-rendered **canvas assemblies** themselves are still out of scope (same
  render-location boundary as the viewer — they follow Part B, not the CSS gate).
- **`--inet-viz-font-size` DOM adoption** — kept inert until D3 is taken up.

## Open items carried forward

- **D1/D2/D3 and §5 are resolved** (see Decisions section) — no longer blockers.
- **§5 (token home)** landed on Option A but is a *formal revisit at Phase 5*, not a reopened Phase 3
  item.
- **Branch** for Phase 3 (`epic-74519` vs a child `feature-{issue}`) still to confirm before coding.

## Related

- [visualization-implementation-roadmap.md](./visualization-implementation-roadmap.md) — Phase 3 tasks
- [visualization-design-spec.md](./visualization-design-spec.md) — density system, target specs, render-location boundary
- [visualization-phase0-audit.md](./visualization-phase0-audit.md) — density render-location findings (Task 5)
- [visualization-phase1-contract.md](./visualization-phase1-contract.md) — §2a density token contract
- [visualization-phase2-implementation-plan.md](./visualization-phase2-implementation-plan.md) — the gate + property plumbing Phase 3 extends
