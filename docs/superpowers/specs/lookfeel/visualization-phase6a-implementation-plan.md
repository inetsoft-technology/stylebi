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

### Where the render-time CSS tier is applied (the injection point)

`VSAssemblyInfo` already resolves title CSS values onto the non-serialized CSS tier at render:

| Anchor | Location | Note |
|---|---|---|
| Title CSS visibility/height from dictionary | `VSAssemblyInfo.java:1413-1432` (`if(this instanceof TitledVSAssemblyInfo)` block inside the setCSS pass) | reads `getFormat(TITLEPATH)`, applies `setTitleVisibleValue`/`setTitleHeightValue` from `CSSDictionary` — the render-time seam |
| Title CSS padding | `VSAssemblyInfo.updateCSSValues()` `:1440-1454` | writes padding via `CompositeValue.Type.CSS` — **the exact non-serialized tier pattern to mirror** |
| `TITLEPATH` constant | `VSAssemblyInfo.java:1538` | `new TableDataPath(-1, TableDataPath.TITLE)` |

### The gated resolver pattern to mirror

| Resolver | File | Gate |
|---|---|---|
| Density | `VSDensityDefaults.isModern()` (`:39-41`) | `getBooleanProperty("viewsheet.modernVisualization", false, true)` (org-scoped) |
| Table structure | `VSTableStructureDefaults.isModern()` (`:40-45`) | density gate **AND** `viewsheet.modernTableStructure` ≠ `"false"`; overlays a per-assembly Default-Style clone in `DataVSAQuery` (defaults-only, not serialized) |
| Chart chrome | `VSChartChromeDefaults.isModern()` (`:44-49`) | density gate **AND** `viewsheet.modernChartChrome` ≠ `"false"`; **writes to the CSS tier of descriptor CompositeValues in `CSSChartStyles.apply`** (not serialized; format.css and user picker still win) |

Phase 6A is the CSS-tier variant: it mirrors `VSChartChromeDefaults` most closely, because the title
format already has a live CSS tier (`updateCSSValues`, `CompositeValue.Type.CSS`) to write into.

## Decisions (proposed — resolve with initiative owner)

### D1 — Inject at the render-time CSS tier, NOT by mutating `setDefaultFormat` → **RECOMMEND YES**

`setDefaultFormat` runs at assembly **creation** and its `fmtInfo` is **serialized** with the sheet, so
seeding modern values there would (a) only affect newly created assemblies, not existing saved
viewsheets, and (b) dirty saved sheets. Instead, write the modern title bg/fg/border to the **CSS
tier** (`CompositeValue.Type.CSS`) of the `TITLEPATH` `VSCompositeFormat` on the render path (the
`:1413-1432` / `updateCSSValues` seam), defaults-only. This is exactly how `VSChartChromeDefaults`
avoids serialization: the CSS tier is not saved, is beaten by the USER tier (an explicit user title
format) and by a customer `format.css` `*TITLE` class, and is read by both the live model and export.

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

## Changes — PROPOSED

### 1. New resolver `VSTitleChromeDefaults` (`core/.../uql/viewsheet/internal/`)

Mirror `VSChartChromeDefaults` exactly:

```java
public static boolean isModern() {
   return VSDensityDefaults.isModern() &&
      !"false".equals(SreeEnv.getProperty("viewsheet.modernObjectChrome", false, true));
}
public static Color titleBackground() { return TITLE_BG; }      // 0xF1EFEA
public static Color titleForeground() { return TITLE_FG; }      // 0x6A685F
public static Color titleBorderColor() { return TITLE_BORDER; } // 0xD9D5CC
```

### 2. Apply defaults-only at the title CSS-tier seam (`VSAssemblyInfo`)

In the `TitledVSAssemblyInfo` render-time CSS block (`:1413-1432` / alongside `updateCSSValues`), when
`VSTitleChromeDefaults.isModern()`, write the modern title background/foreground/bottom-border to the
CSS tier of the `TITLEPATH` `VSCompositeFormat` via `CompositeValue.Type.CSS` — **only where the value
is still the legacy default** (background still `DEFAULT_TITLE_BG`, no USER-tier override). This keeps
the USER tier and any customer `format.css` `*TITLE` class winning, and is not serialized.

### 3. Palette swatches

Add the "Object / Title Chrome Tokens" group (D3) coordinated with the table-structure and
chart-chrome neutrals.

**Gate off:** byte-identical (resolver short-circuits on `isModern()==false`; no CSS-tier write).
**Gate on:** titled assemblies render the warm-neutral title bar in the live model and every export
format; user-set title formats and customer `format.css` TITLE classes still win.

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
