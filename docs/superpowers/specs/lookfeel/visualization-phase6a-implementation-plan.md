# StyleBI Visualization — Phase 6A (Assembly Title-Bar / Object-Chrome Defaults) — Implementation Plan

## Scope

Phase 6A modernizes the **viewsheet object title bar** as a gated, server-side, export-consistent
default, per [visualization-implementation-roadmap.md](visualization-implementation-roadmap.md)
Phase 6A. It is the object-chrome sibling of the Phase 5 table-structure and Phase 6 chart-chrome
server resolvers, and a hard prerequisite for
[visualization-phase7-implementation-plan.md](visualization-phase7-implementation-plan.md) Part B
(a KPI value body modernized without its title bar reads as half-themed).

Unlike Phases 3/5/7 this phase has **no browser-CSS half**. The title bar is rendered entirely from
the server `titleFormat` (`VSFormat`) with inline style bindings and **no `--inet-viz-*` hook**
(`vs-title.component.html:25-37` binds height/font/color/border/background straight from
`titleFormat`), and it is re-drawn server-side in export. So Phase 6A is a single server-side pass.

### Scoping correction: title bars belong to `TitledVSAssemblyInfo`, not "every assembly type"

The roadmap says object chrome "spans all assembly types (table, chart, gauge, text, image,
selection, …)". That is inaccurate: the title bar is seeded and rendered **only for
`TitledVSAssemblyInfo`** (`VSAssemblyInfo.java:1209` `if(this instanceof TitledVSAssemblyInfo)`).
Verified titled set:

- **Composite assemblies** (`CompositeVSAssemblyInfo extends TitledVSAssemblyInfo`,
  `CompositeVSAssemblyInfo.java:27`): tables / crosstab / calc-table, selection list / tree /
  container, checkbox, radio button
- **Direct implementers:** chart (`ChartVSAssemblyInfo.java:58`), calendar
  (`CalendarVSAssemblyInfo.java:49`), range slider (`TimeSliderVSAssemblyInfo.java:45`)

**Not titled** (no title bar, excluded from this phase): gauge, text, image, output, and the simple
inputs (combo box, slider, spinner, text input). Their value/label chrome is Phase 7, not 6A. The
roadmap's "gauge/text/image" mention is a doc error worth a correction.

## Rendering boundary: the title bar is server-owned and export-visible

The title format is a server `VSCompositeFormat` at `getFormatInfo().getFormat(TITLEPATH)`
(`TITLEPATH = new TableDataPath(-1, TableDataPath.TITLE)`, `VSAssemblyInfo.java:1538`). It reaches
the live client as the `titleFormat` model field (`getTitleFormat()` on `BaseTableModel`,
`VSChartModel`, `VSSelectionListModel`, `VSCheckBoxModel`, `VSRadioButtonModel`, `VSRangeSliderModel`,
`VSCalendarModel`, `VSSelectionContainerModel`, `VSCompositeModel`) and is re-drawn in export from the
**same** format — `ExportUtil.getBackGroundColor(titleFormat, objFormat)` (`ExportUtil.java:108-112`)
and the per-type export helpers (`VSTableHelper`, `VSCrosstabHelper`, `VSSelectionListHelper`,
`VSCurrentSelectionHelper`, and the PDF/SVG/HTML variants). Because live and export share the one
resolved `VSCompositeFormat`, a default written onto that format is picked up by both — the same
export-consistency guarantee Phases 5 and 6 rely on. A browser `--inet-viz-*` change cannot drive it
and would produce a live/export mismatch.

## Grounding (verified against current code, 2026-07-14; re-confirm line numbers before editing)

### The legacy title default (what "still default" means)

| Property | Where seeded | Legacy value |
|---|---|---|
| Title background | `VSAssemblyInfo.setDefaultFormat(...)` `titlefmt.setBackgroundValue(DEFAULT_TITLE_BG)` (`:1210`) | `DEFAULT_TITLE_BG = "0xf5f5f5"` (`:1546`) |
| Title alignment | `titlefmt.setAlignmentValue(H_LEFT \| V_CENTER)` (`:1211`) | left / vertical-center |
| Title font | `titlefmt.setFontValue(getTitleDefaultFont())` (`:1212`, `getTitleDefaultFont` `:1493`) | default face, 11 |
| Title foreground | not set at default | inherited / null (no explicit default) |
| Title border | set only under the `border` branch (`:1178-1179`) as `DEFAULT_BORDER_COLOR` on all edges | `DEFAULT_BORDER_COLOR` (`0xDADADA`) |
| Title CSS type | `cssfmt.setCSSType(getObjCSSType() + CSSConstants.TITLE)` (`:1216`) | per-assembly TITLE CSS class |
| Title height | CSS-driven at `:1427-1430` (`isHeightDefined`→`setTitleHeightValue`), else `TitledVSAssemblyInfo` default | assembly default |

### The injection point (corrected 2026-07-14 after grounding — supersedes the CSS-tier plan)

The first draft proposed writing the modern colors to the title format's **CSS tier**
(`CompositeValue.Type.CSS`), mirroring `VSChartChromeDefaults`. **Grounding proved that is not
viable for a `VSFormat`:** the title CSS tier is **dictionary-backed and read-only from code** —
`VSCSSFormat.getBackgroundValue()`/`isBackgroundValueDefined()` compute live from the CSS dictionary
(`VSCSSFormat.java:265-268`, `:503-504`); the CSS tier *is* `format.css`, with no writable slot. And
the proposed hook, `setCSSDefaults()`, runs only at assembly **creation/binding/wizard**
(`initDefaultFormat` callers), not per-open, so it could not modernize existing saved viewsheets;
`updateCSSValues()` has only embedded-viewsheet callers, also not the general per-open path.

Correct mechanism = **read-time substitution, defaults-only** (the `VSDensityDefaults` two-choke-point
shape, not the chart CSS-tier shape). `VSCompositeFormat.getBackground()` resolves
**user → CSS(dictionary) → default** (`VSCompositeFormat.java:77-92`), so substituting the modern
neutral **only when the resolved color still equals the legacy default** (`DEFAULT_TITLE_BG`)
automatically preserves a user title format and a customer `format.css` TITLE class, and never mutates
or serializes anything. The seams (no single shared chokepoint — title spans several render paths):

| Seam | Location | Note |
|---|---|---|
| Legacy default background | `VSAssemblyInfo.DEFAULT_TITLE_BG = "0xf5f5f5"` (`:1546`), seeded in `setDefaultFormat` (`:1210`) | the "still default" comparison value (`new Color(0xF5F5F5)`) |
| `TITLEPATH` constant | `VSAssemblyInfo.java:1538` | `new TableDataPath(-1, TableDataPath.TITLE)` |
| **Live model — composite assemblies** | `VSCompositeModel` ctor `:36` (`new VSFormatModel(finfo.getFormat(TITLEPATH, false), info)`) | shared by tables/crosstab/calc, selection list/tree/container, calendar, range slider, checkbox, radio |
| **Live model — chart** | `VSChartModel.setTitleFormat` `:370` | chart title |
| **Export** | per-type helpers reading `titleFormat.getBackground()` — `ExportUtil.getBackGroundColor(titleFormat, objFormat)` `:108-112`, `VSTableHelper`/`VSCrosstabHelper`/`VSSelectionListHelper`/`VSCurrentSelectionHelper` (+ PDF/SVG/HTML variants) | **no single shared chokepoint — each helper's title draw is a separate seam** |

`VSFormatModel.background` is a CSS **string** (`VSFormatModel.java:314`, built via `VSCSSUtil`), so the
substitution is done at the `Color` level (comparable to `DEFAULT_TITLE_BG`) before the DTO is built /
at the export color read, via the resolver's `resolve*(Color)` methods.

### The gated resolver pattern to mirror

| Resolver | File | Gate |
|---|---|---|
| Density | `VSDensityDefaults.isModern()` (`:39-41`) | `getBooleanProperty("viewsheet.modernVisualization", false, true)` (org-scoped) |
| Table structure | `VSTableStructureDefaults.isModern()` (`:40-45`) | density gate **AND** `viewsheet.modernTableStructure` ≠ `"false"`; overlays a per-assembly Default-Style clone in `DataVSAQuery` (defaults-only, not serialized) |
| Chart chrome | `VSChartChromeDefaults.isModern()` (`:44-49`) | density gate **AND** `viewsheet.modernChartChrome` ≠ `"false"`; **writes to the CSS tier of descriptor CompositeValues in `CSSChartStyles.apply`** (not serialized; format.css and user picker still win) |

Phase 6A reuses these resolvers' **gate** (`isModern()` shape, org-scoped property + escape hatch) but
**not** their write mechanism: `VSTableStructureDefaults` overlays a Default-Style clone at query time
and `VSChartChromeDefaults` writes descriptor CompositeValue CSS-tier slots — neither maps to a
`VSFormat` title (see the corrected injection point above), so Phase 6A substitutes at read time
instead.

## Decisions (proposed — resolve with initiative owner)

### D1 — Read-time substitution (defaults-only), NOT a CSS-tier write and NOT `setDefaultFormat` → **RECOMMEND YES** (revised)

Neither of the first-draft options works (see the corrected injection point): `setDefaultFormat` is
creation-time + serialized (misses existing sheets, dirties saved sheets), and the title CSS tier is
dictionary-backed with no writable slot. Instead substitute the modern neutral **at read time** — when
the live title `VSFormatModel` is built and when export draws the title — **only when the resolved
color still equals the legacy default**. Because `VSCompositeFormat.getBackground()` resolves
user → CSS(dictionary) → default, this preserves a user title format and a customer `format.css` TITLE
class for free, mutates nothing, and serializes nothing. The trade-off vs the chart case is there is
**no single shared chokepoint**: the live side is a few model builders and the export side is several
per-type helpers.

### D2 — Scope = `TitledVSAssemblyInfo` only; gauge/text/image excluded → **RECOMMEND YES**

Apply only where `this instanceof TitledVSAssemblyInfo`. Gauge/text/image/output and simple inputs
have no title bar; their chrome is Phase 7. Correct the roadmap's "all assembly types" wording.

### D3 — Modern title palette (warm-neutral, quieter than data) → **RECOMMEND, confirm hexes**

Coordinated with Phase 5 table-structure / Phase 6 chart-chrome neutrals so chrome reads as one system:

- background → `#F1EFEA` (= `VSTableStructureDefaults.headerBackground()`) — quiet warm neutral,
  replacing legacy `#f5f5f5`
- foreground → `#6A685F` (= `headerForeground` / chart `labelColor`) — muted, "chrome quieter than data"
- bottom border / separator → `#D9D5CC` (= `headerSeparator`, the shared structural border) so the
  title-to-body rule matches the table header rule
- add a "Object / Title Chrome Tokens" group to
  [visualization-palette-swatches.html](visualization-palette-swatches.html)

### D4 — Leave title height at its default (defaults-only) → **RECOMMEND YES**

Do not change the title height default in the first pass (height density is deferred; changing it
reflows layout). Only background/foreground/border are neutral-repointed.

### D5 — Share the gate and palette with Phase 7 Part B → **RECOMMEND YES**

Both are gated server-side `VSFormat`-default resolvers over overlapping assemblies. Land 6A first (or
together with 7B) so a modern KPI's title bar and value body modernize as one system.

## Changes

### 1. New resolver `VSTitleChromeDefaults` (`core/.../uql/viewsheet/internal/`) — DONE (2026-07-14)

Mirrors `VSChartChromeDefaults`' gate + `resolveAxisLineColor` substitution shape. Pure, no render
dependency:

```java
public static boolean isModern() {
   return VSDensityDefaults.isModern() &&
      !"false".equals(SreeEnv.getProperty("viewsheet.modernObjectChrome", false, true));
}
public static Color titleBackground() { return TITLE_BG; }      // 0xF1EFEA
public static Color titleForeground() { return TITLE_FG; }      // 0x6A685F
public static Color titleBorderColor() { return TITLE_BORDER; } // 0xD9D5CC
// defaults-only substitution: modern iff gate on AND current is still the legacy default
public static Color resolveBackground(Color current) { … LEGACY_TITLE_BG.equals(current) … }
public static Color resolveForeground(Color current) { … current == null … }
```

Unit test `VSTitleChromeDefaultsTest` (Spring/`@SreeHome` harness like `CSSChartStylesModernChromeTest`):
6 tests, all passing — palette pins, gate-off pass-through, gate-on defaults-only substitution, and
the `modernObjectChrome=false` escape hatch.

### 2. Wire the read-time substitution seams — DONE (2026-07-14; `core` compiles clean)

Each title-format fetch is wrapped with `VSTitleChromeDefaults.applyModernDefaults(...)` (gate-off /
non-default returns the original object → byte-identical). Verified: every titled assembly builds its
live title independently (NOT all through `VSCompositeModel`), so each was wired:

- **Live model:** `BaseTableModel` (tables/crosstab/calc), `VSChartModel` (chart), `VSCompositeModel`
  (selection list/tree via `VSSelectionBaseModel`, current-selection container via
  `VSSelectionContainerModel`), `VSCheckBoxModel`, `VSRadioButtonModel`, `VSRangeSliderModel`,
  `VSCalendarModel`.
- **Export:** `VSTableDataHelper` (table/crosstab), `VSSelectionListHelper`, `VSSelectionTreeHelper`,
  `AbstractVSExporter` (chart title→TextVSAssembly, chart title format, calendar title),
  `ExportUtil.getBackGroundColor` (range slider / current selection background).

First pass substitutes **background + foreground** only; the bottom-border neutral (`titleBorderColor`)
is available on the resolver but not yet applied (border affects title layout — deferred to keep the
first pass parity-safe).

#### Eye-test round 1 findings (2026-07-14, PDF export)

- **`isModern()` confirmed true at export** — the modern chart chrome (`#6a685f`/`#35342f`/`#e8e5de`)
  is baked into the PDF, so the org gate resolves on the export thread. Not a gate problem.
- **Background substitution mechanism verified** end-to-end by unit test
  (`applyModernDefaultsResolvesModernBackground`): a title format with the legacy default bg resolves
  `getBackground()` → `#f1efea`.
- **Foreground bug fixed** — an un-customized title foreground resolves to **black**, not `null`, so
  the original `current == null` check never fired. `resolveForeground` now treats null-or-black as
  the default.
- **Chart title needed a special seam** — the chart title is exported as a synthesized
  `TextVSAssembly` drawn via `writeText`, which uses the assembly's **OBJECT** format, not the title
  format. Wrapping the title `getFormat` there was dead code. Fixed by giving the synthesized
  title-text a private (cloned) `FormatInfo` whose OBJECT background/foreground carry the resolved
  modern title colors (chart body format untouched, gate-off byte-identical),
  `AbstractVSExporter` chart-title block.
- Other export helpers (calendar `drawTextBox`, table/selection `writeText(bounds, format, …)`) draw
  from the title format directly, so their `applyModernDefaults` wrap is effective as-is.

#### Eye-test round 2 findings (2026-07-14, PDF export) — unified export seam

Round-1 fixes landed chart + table titles, but calendar / checkbox / selection-list object titles
stayed legacy. Root cause: export draws titles at **~15 per-widget / per-format sites** (base +
`pdf/` + `svg/` + `html/` helpers — e.g. `PDFSelectionListHelper`, `PDFCurrentSelectionHelper`,
`HTMLCoordinateHelper`), and the base-helper wraps only covered a few; several widgets (checkbox,
standalone selection list, calendar object title) draw through paths that were never wrapped.

Fix — **one unified seam instead of 15**: the exporter clones the whole viewsheet
(`AbstractVSExporter` `viewsheet = viewsheet.clone()`) and calls `prepareAssembly(assembly)` per
assembly before drawing. `prepareAssembly` now calls `VSTitleChromeDefaults.applyModernDefaultsInPlace`
on the title format of every `TitledVSAssemblyInfo`, mutating the **cloned** assembly's title-format
default tier — so every downstream title draw (any widget, any format) reads the modern chrome from
the one shared format. Gated + defaults-only; mutates nothing persisted (export copy). The chart title
keeps its dedicated fix (it renders as a synthesized `TextVSAssembly`, not a titled assembly).

The earlier per-site export wraps are now redundant (they see an already-modern format and pass
through) — harmless, flagged for cleanup after the eye-test confirms.

#### Eye-test round 3 findings (2026-07-14) — print-layout is a third pipeline

Non-print PDF export now correct, but **print-layout PDF** kept legacy titles. Cause: print-layout
export uses a **separate converter**, `VsToReportConverter` (not `AbstractVSExporter`), which builds
the title as a report textbox in `createTitle` and fetches the title format at its own seam. Fixed by
wrapping that fetch (`VsToReportConverter.createTitle`, the `getFormat(TITLE)` call) with
`applyModernDefaults` — gated + defaults-only, non-mutating. Chart/table/selection/calendar take the
textbox's `detailfmt` directly so they modernize; checkbox/radio are `CompoundVSAssembly` and null
their title bg by design (unchanged), their title text still modernizes.

So the title chrome now has **three** render entry points, each wired: live model builders,
`AbstractVSExporter.prepareAssembly` (interactive/normal export), and `VsToReportConverter.createTitle`
(print-layout export).

#### Eye-test round 4 findings (2026-07-14) — resolver too narrow + chart print path

Print-layout fixed checkbox but not chart/calendar. Two causes:

1. **Resolver was keyed on `bg == #f5f5f5`**, but legacy title default backgrounds **vary by widget**:
   chart `#ffffff`, calendar `#f5f5f5`, selection-list / checkbox transparent (null). So the substitution
   only fired for calendar-like defaults. **Redesigned:** substitute the modern neutral whenever the
   **user (USER tier) / format.css (CSS tier) has *not* set the value** — regardless of the default
   color — so all widgets get one consistent modern title bar, and only an explicit user/format.css
   color is preserved. (`isBackgroundCustomized`/`isForegroundCustomized`.) The old `resolveBackground`/
   `resolveForeground(Color)` are removed (they couldn't see the tiers).
2. **Print-layout chart title uses `addChart` → `addTextBoxElement0`**, not `createTitle`, so nothing
   reached it. Wired via `applyModernDefaultsInPlace` on the cloned chart's title format
   (`VsToReportConverter.addChart`).

Print-layout title paths now both wired: `VsToReportConverter.createTitle` (table / selection /
checkbox / current-selection) and `VsToReportConverter.addChart` (chart). Checkbox/radio still null
their title bg by design in print layout (their title text modernizes).

Still requires a render/export **parity eye-test** (clean rebuild) before merge.

### 3. Palette swatches — PENDING

Add the "Object / Title Chrome Tokens" group (D3) coordinated with the table-structure and
chart-chrome neutrals.

**Gate off:** byte-identical (`resolve*` short-circuits on `isModern()==false`, returning the input
unchanged — proven by the unit test). **Gate on (after wiring):** titled assemblies render the
warm-neutral title bar in the live model and every export format; user-set title formats and customer
`format.css` TITLE classes still win.

## Validation

1. **Build/lint:** core compiles; `cd web && npm run build` clean (no web change expected — title
   bar consumes the resolved `titleFormat`, so no `vs-title.component` edit).
2. **Gate-off inertness:** a viewsheet with a table, crosstab, chart, selection list/tree/container,
   calendar, range slider, checkbox, radio renders byte-identical to today in live **and** in
   PDF/PNG/SVG/Excel/PPT/HTML export.
3. **Gate-on (per-org `viewsheet.modernVisualization=true`):** the same assemblies show the modern
   warm-neutral title bar in live **and** every export format (this is the core export-parity check —
   the reason 6A is server-side).
4. **Defaults-only precedence:** a user-set title background/foreground/border still wins in both
   modes; a customer `format.css` `*TITLE` class still wins over the modern default.
5. **Escape hatch:** `viewsheet.modernObjectChrome=false` (with modern on) leaves title bars legacy
   while the rest of modern stays active.
6. **Boundary check:** no `vs-title.component` / `titleFormat` client binding changed; grep the diff
   for zero web changes. Non-titled assemblies (gauge/text/image) unchanged (D2).
7. **Coordination:** with Phase 5/6 modern on, the title bar, table header, and chart chrome read as
   one warm-neutral system; with Phase 7 Part B (when landed) a KPI title + value read as one.

## Deferred / follow-ups

1. **Title height density** — leave default; height is a layout-reflow concern (D4).
2. **Dark-mode title chrome** — light-first, matching Phase 5/6 (dark deferred initiative-wide).
3. **Per-assembly-type title differentiation** — one neutral for all titled assemblies in the first
   pass; per-type nuance deferred.
4. **Roadmap wording correction** — "all assembly types (…gauge, text, image…)" → `TitledVSAssemblyInfo`
   set (D2); apply as a spec correction.

## Branching (per CLAUDE.md)

Community-only core Java (`VSTitleChromeDefaults` + the `VSAssemblyInfo` seam) — community repo
commit/PR, enterprise submodule-pointer bump at PR time. Coordinate with the Phase 7 Part B
server-chrome work (shared gate + palette). Nothing on `main` or a `v1.0.x`/`v1.1.x` release branch;
nothing pushed/PR'd without explicit approval.

## Related

- [visualization-implementation-roadmap.md](visualization-implementation-roadmap.md) — Phase 6A charter, Phase 6/8 rendering boundary
- [visualization-design-spec.md](visualization-design-spec.md) — Widget Chrome (title/framing), Rendering And Theming Architecture
- [visualization-phase5-implementation-plan.md](visualization-phase5-implementation-plan.md) — `VSTableStructureDefaults` gated server resolver + defaults-only precedence this phase mirrors
- [visualization-phase6-implementation-plan.md](visualization-phase6-implementation-plan.md) — `VSChartChromeDefaults` CSS-tier write pattern this phase mirrors most closely
- [visualization-phase7-implementation-plan.md](visualization-phase7-implementation-plan.md) — Part B KPI-body defaults that depend on and coordinate with this phase
- [visualization-palette-swatches.html](visualization-palette-swatches.html) — add the "Object / Title Chrome Tokens" group
