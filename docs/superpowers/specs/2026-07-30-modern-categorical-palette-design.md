# Modern Light/Dark Categorical Chart Palette — Design

Date: 2026-07-30
Status: approved, ready for implementation planning

## Goal

Declare the modern categorical chart colors as first-class named palettes — `Modern` and
`Modern Dark` — so that:

1. The **Select Palette** dialog lists them and pre-selects whichever one the chart is actually
   rendering (today it falls through to `Default` and displays legacy swatches while the chart
   renders modern colors).
2. The **swatch grid** shown when picking an individual chart series color offers the modern colors
   instead of a hardcoded legacy copy.

Both surfaces resolve from a single source of truth, so the picker can never disagree with the
renderer.

## Current state

### format.css → ChartPalette

`community/core/src/main/resources/inetsoft/util/css/defaults.css` contains nothing but
`ChartPalette` rules — 544 lines, 11 palettes, in declaration order: `Default` (40 colors), `Soft`
(8), `Pastel` (16), `Red`/`Green`/`Blue`/`Orange`/`Gray` (4 each), `Heat 8`, `Heat 16`, `Heat 24`.
Each entry is a two-attribute selector:

```css
ChartPalette[name='Default'][index='1'] {
   color: #518db9;
}
```

`CSSDictionary.getDictionary()` (`CSSDictionary.java:115`) resolves to `("portal", "format.css",
isReport=false)` for viewsheets. `init()` (`:394-486`) parses classpath `defaults.css` first, then
merges the dataspace `portal/format.css` rules on top, then per-org theme CSS from
`PortalThemesManager.getCssEntries()`. Later rules win, so a customer or theme `format.css` can
override any index of an existing palette or declare an entirely new palette name. Dictionaries are
cached per `(cssDir, cssFile, isReport, orgID)` with a 10-second `lastModified` recheck.

`ColorPalettes.loadPalettes()` (`ColorPalettes.java:88`) scrapes the merged dictionary generically —
no palette name is hardcoded anywhere:

1. `getCSSAttributeValues("ChartPalette", null, "name")` → every palette name, declaration order
   (`OrderedMap`).
2. Per name, `getCSSAttributeValues("ChartPalette", {name}, "index")` → the max index sizes a
   `Color[]`.
3. Per index, `cssDictionary.getForeground(CSSParameter("ChartPalette", {name, index}))`.
4. `new CategoricalColorFrame()` + `setDefaultColors(colors)`, keyed by name; cached per org,
   invalidated when the CSS `lastModified` changes.

Adding a palette is therefore a pure CSS edit.

### The palette selector

`GET /api/composer/chart/colorpalettes` (`VSChartBindingController.java:234-253`) walks
`ColorPalettes.getPaletteNames()` into `CategoricalColorModel[]` with `name` set.
`CategoricalColorPane.ngOnInit()` fetches it once and passes it to `PaletteDialog` as
`colorPalettes`, with `currPalette = frameModel`
(`categorical-color-pane.component.ts:91-134`). The same list also feeds
`widget/target/graph-palette-dialog` and `b-categorical-color-pane`.

`PaletteDialog.getPaletteIndex()` (`palette-dialog.component.ts:73`) chooses the pre-selected entry
by comparing `currPalette.colors` to each palette's colors forward, then reversed (which is what sets
`_reversed`). **When nothing matches it returns `0`** — `Default`. `saveChanges()` overlays the
chosen colors onto the head of `currPalette.colors`, landing in the USER tier.

### Render-time color precedence

`CategoricalColorFrame.getColor()` (`:304-322`): `userColors[i]` → `cssColors[i]` (only when
`parentParams != null`) → `defaultColors[i % n]`.

`cssColors` is populated by `updateCSSColors()` (`:498`) from `ChartPalette[index=N]` rules **with no
`name` attribute**, combined with the chart's parent CSS params. The `parentParams != null` guard is
deliberate: the palettes served to the dialog have no parent, so they are not themselves re-styled.

`VSChartPaletteDefaults` holds an 8-color modern head plus an 8-color dark head, each spliced onto
`CategoricalColorFrame.COLOR_PALETTE[8..39]` so high-cardinality charts keep 40 distinct colors and
do not wrap early. It is applied to `defaultColors` at three render/format-time sites —
`CSSProcessor.java:473`, `VGraphPair.java:1299`, `ChangeChartProcessor.java:1893` — gated by
`VSDensityDefaults.isModern()` plus `viewsheet.modernChartPalette`, with the dark variant selected by
`VSDensityDefaults.isDark()` (`viewsheet.darkMode`). It is never serialized and never registered as a
named palette.

Verified: `CategoricalColorFrame.COLOR_PALETTE` (`:50-64`) is byte-identical to the `defaults.css`
`ChartPalette[name='Default']` indices 1–40.

### The disconnected fourth system

`widget/color-picker/default-palette.ts` — `DefaultPalette.chart` is a hardcoded 5×8 TypeScript copy
of the legacy `Default` 40 colors, and it is the default `[palette]` for `color-picker`,
`cp-color-pane`, `color-editor`, and `color-dropdown`. It never consults the server, `format.css`, or
the modern gate. `combined-color-pane.component.ts:41` even uses `DefaultPalette.chart.flat()` for
the per-series **reset**, so reset re-seeds legacy colors.

Consequence: a customer's `format.css` override reaches the renderer and the palette dialog but has
never reached any color dropdown.

## Decisions

| # | Decision | Rationale |
|---|---|---|
| 1 | Both surfaces in scope: palette dialog **and** swatch grid | They are independent systems; fixing the dialog alone leaves every color dropdown on legacy colors |
| 2 | Two named palettes, both always listed | Declared as CSS rules like every other palette; the dialog's existing color-equality match pre-selects the right one with zero new selection logic |
| 3 | Swatch grid is server-driven from the same CSS | One source of truth; also closes the long-standing gap where `format.css` never reached the pickers |
| 4 | CSS authoritative, Java arrays as fallback | Avoids a second copy of the hexes drifting; makes the modern default retheme-able by a customer `format.css`, consistent with every other palette |
| 5 | Both palettes declare all 40 indices | A short `defaultColors` causes series-color wrap-around, caps `updateCSSColors()`, and caps the composer's editable slots (see below) |
| 6 | Indices 9–40 stay the legacy tail | Ships exactly what renders today; a redesigned tail was explicitly deferred |
| 7 | Server resolves the active grid | Keeps the gate decision in one place; avoids portal viewer widgets calling `/api/composer/**` |
| 8 | Existing gate unchanged | `viewsheet.modernChartPalette` + `viewsheet.darkMode`, org-scoped, as today |

### Why a short palette is lossy

If `defaultColors` were only 8 entries:

- `CategoricalColorFrame.java:317` does `defaultColors.get(index % colors.size())` — series 9 wraps to
  color 1, so series 1 and 9 become visually indistinguishable in plot and legend.
- `getColorCount()` returns `defaultColors.size()` (`:434`) and `updateCSSColors()` loops
  `i < getColorCount()` (`:508`), so a customer's `ChartPalette[index='12']` rule would silently stop
  being read — CSS-tier overrides capped at 8.
- `CategoricalColorModel` sizes `colors`/`cssColors`/`defaultColors` from `getColorCount()`
  (`:37-45`), so the composer's categorical color pane would expose only 8 editable series slots.

An 8-swatch *grid* is harmless (rows are iterated dynamically); an 8-long *palette* is not. The two
are independent choices.

## Architecture

`defaults.css` becomes the single source of truth. Two palettes are declared there, each with all 40
indices, placed immediately after the `Default` block — near the top of the dropdown for
discoverability, while leaving `Default` at index 0 so `getPaletteIndex()`'s no-match fallback is
unchanged for existing installs.

| Palette | Indices 1–8 | Indices 9–40 |
|---|---|---|
| `Modern` | `00D4E8 00B87A F59E0B F43F5E 8B5CF6 3B82F6 0D9488 64748B` | `Default` 9–40, verbatim |
| `Modern Dark` | `22D3EE 10B981 FBB724 FB6181 A78BFA 60A5FA 2DD4BF 94A3B8` | `Default` 9–40, verbatim |

The legacy tail, indices 9–40 in order: `#9368be #be90d4 #95a5a6 #dadfe1 #19b5fe #c5eff7 #869530
#c8d96f #a88637 #d2b267 #019875 #68c3a3 #99CCFF #999933 #CC9933 #006666 #993300 #666666 #663366
#CCCCCC #669999 #CCCC66 #CC6600 #9999FF #0066CC #FFCC00 #009999 #99CC33 #FF9900 #66CCCC #339966
#CCCC33`.

Three consumers derive from those rules:

**1. Render path (existing sites, now CSS-fed).** `VSChartPaletteDefaults.modernPalette()` and
`darkPalette()` resolve the named palettes from `ColorPalettes` instead of returning hardcoded
arrays. `MODERN_HEAD`/`DARK_HEAD` survive purely as fallback. The three call sites are untouched.
Because `COLOR_PALETTE` and the CSS `Default` block are byte-identical, this is a no-op on rendered
output while the fallback holds.

**2. Palette dialog (no code change).** `ColorPalettes` scrapes names generically, so the endpoint
picks the two up for free, and `getPaletteIndex()`'s color-equality match pre-selects whichever one
the chart renders. The three heads are mutually distinct, so a full-40 match is unambiguous even
though all three palettes share indices 9–40.

**3. Swatch grid (new).** A small endpoint returns the already-resolved active 40 colors — gate off →
`Default`, gate on + dark → `Modern Dark`, gate on → `Modern` — by calling the same
`VSChartPaletteDefaults` resolver the renderer uses. A root-provided Angular service fetches it once
and exposes a synchronous snapshot; the chart-series call sites read that instead of
`DefaultPalette.chart`, which remains the pre-resolution fallback.

The gate decision lives in exactly one place (`VSChartPaletteDefaults`) and the colors in exactly one
place (`defaults.css`).

### Falls out for free: no accidental pinning

`ColorFrameModelFactory.updateVisualFrameWrapper0` only calls `setUserColor` when a color *differs*
from the current one (`:98-100`). So clicking OK on `Modern` while a chart already renders modern
defaults writes no USER-tier colors, and the chart keeps following the dark-mode gate. Picking
`Modern Dark` while in light mode does pin it, which is the correct reading of an explicit choice.
No code change; covered by test.

## Components

### Backend — 3 files

**`community/core/src/main/resources/inetsoft/util/css/defaults.css`** — 80 new rules appended after
the `Default` block. Data only.

**`community/core/src/main/java/inetsoft/uql/viewsheet/internal/VSChartPaletteDefaults.java`**

- `modernPalette()` → `resolve("Modern", MODERN_HEAD)`; `darkPalette()` → `resolve("Modern Dark",
  DARK_HEAD)`
- new private `resolve(name, head)`: pull `ColorPalettes.getPalette(name)`, copy out
  `getColorCount()` colors via `getDefaultColor(i)`. Falls back to `spliceLegacy(head)` when the
  frame is null, shorter than `COLOR_PALETTE`, or contains a null hole.
- `spliceLegacy(head)` is today's `modernPalette()` body, unchanged
- new `activePalette()` returns dark-or-light per `VSDensityDefaults.isDark()`, so
  `applyModernPalette()` and the new endpoint share one decision point
- memoizes the resolved `Color[]` keyed on org ID +
  `CSSDictionary.getOrgScopedCSSLastModified(CSSDictionary.getDictionary())` — the same viewsheet
  dictionary and staleness signal `ColorPalettes.loadPalettes()` itself uses (`ColorPalettes.java:95,99`)
- `applyModernPalette()` signature and its three call sites unchanged

**New `ChartColorPaletteController`** (`inetsoft/web/portal/controller/`) — `GET
/api/portal/chart-color-palette` returning `String[]` of 40 hex values. Gate on →
`VSChartPaletteDefaults.activePalette()`; gate off → CSS `Default`, falling back to `COLOR_PALETTE`.
Serialized with `Tool.toString(Color)`, the helper `CategoricalColorModel:43` already uses, so the
hex format matches what the dialog round-trips. Deliberately not under `/api/composer/**` because
portal viewer widgets consume it.

### Frontend — 1 new file, 3 call sites

**New `widget/color-picker/chart-palette.service.ts`**, `providedIn: "root"`: fetches once,
`shareReplay(1)`, exposes a synchronous `chartPalette` snapshot seeded with `DefaultPalette.chart`, an
observable for reactive consumers, and `flatColors()` for the reset path. `ColorPalette` is a strict
5×8 tuple type (`color-classes.ts:22-28`), so the 40→grid conversion needs an explicit build plus a
length guard — a malformed response keeps the fallback rather than producing a ragged grid.

| Change | File | Note |
|---|---|---|
| Palette from service | `binding/widget/color-field-pane.component.ts:41` | only two consumers, both chart-series; promote to `@Input()` for explicitness |
| `reset()` → `flatColors()` | `binding/editor/chart/aesthetic/combined-color-pane.component.ts:41` | stops reset re-seeding legacy colors |
| Pass `[palette]` | `binding/editor/chart/color-mapping-dialog.component.html:58` | per-dimension-value series colors |

`DefaultPalette.chart` is kept — it remains the permanent grid for non-series pickers, not merely a
fallback.

**Bootstrap warm-up.** A root service constructs lazily, so if the first injection were the picker
opening, that first open would show the fallback. The portal, composer, and viewer app components all
run at bootstrap and already fetch mode state; injecting the service there warms it before any picker
can open.

### Scope boundary: no shared defaults change

Changing `color-editor.palette`, `cp-color-pane.palette`, `color-picker.palette`, or
`color-dropdown.getPalette()` would leak modern series colors into non-series surfaces. Audited:

Format-dialog pickers — **unaffected**:

| Surface | Guard | Grid |
|---|---|---|
| `vs-formats-pane:47` Foreground | `[chart]="false"` | `DefaultPalette.palette` |
| `vs-formats-pane:66` Background | `[chart]="false"` + `isBg` + `transEnabled` | `bgWithTransparent` |
| `vs-formats-pane:87` Value Fill | no `[chart]`, but `transEnabled=true` | `fgWithTransparent` |
| `table-style-format-pane` ×6 | `[chart]="false"` | `palette` / `bgWithTransparent` |

Value Fill is safe only because `getPalette()` tests `!isBg && transEnabled` *before* the chart
branch (`color-dropdown.component.ts:47-56`), making the chart branch unreachable there. That is
branch ordering, not intent, so it is pinned by a test.

`format/objects/binding-border-pane.component.html:72` — the Format pane's border color — passes no
`[chart]` and `[transEnabled]="false"`, so it lands in `!isBg && !transEnabled` with `chart`
defaulting to `true` (`color-dropdown.component.ts:36`) and resolves `DefaultPalette.chart`. Changing
the chart branch would have changed it. It is left alone.

`color-dropdown` passes `[palette]="getPalette()"` down explicitly
(`color-dropdown.component.html:19`), so via-dropdown usage is insulated. But `color-editor` used
**directly** falls back to its own default, which is `DefaultPalette.chart`. There are 20 such direct
usages that pass no `[palette]`; one is `color-mapping-dialog` (in scope) and one is
`b-categorical-color-pane` (excluded below), leaving **18 non-series surfaces** that would have been
changed by editing the default:

| Surface | Usages |
|---|---|
| `vsobjects/dialog/graph/chart-line-pane` — grid/trend/diagonal/facet lines | 6 |
| `vsobjects/dialog/annotation/annotation-format-dialog` — box border, fill, line | 3 |
| `widget/highlight/highlight-pane` — foreground, background | 2 |
| `widget/presenter/presenter-property-dialog` | 1 |
| `composer/dialog/vs/line-prop-pane` — shape line | 1 |
| `graph/dialog/axis-line-pane` | 1 |
| `graph/dialog/legend-format-general-pane` — legend border | 1 |
| `widget/target/band-panel`, `line-panel`, `stat-panel` | 3 |

All left alone. Note `fill-prop-pane` and `range-pane` are *not* in this set — they already pass an
explicit `[palette]`, as do `stat-panel`'s Above/Below fill pickers.

**`b-categorical-color-pane` is explicitly excluded.** It has exactly one usage
(`stat-panel.component.html:83`), inside a `<legend>_#(Fill)</legend>` fieldset, bound to
`model.bandFill`, between "Above" and "Below" fill pickers that deliberately use `fillPalette =
DefaultPalette.bgWithTransparent`. Reached via Chart Properties → Advanced → Target Lines →
Add/Edit Target → Statistics → Fill Band. The backend maps it onto `GraphTarget.bandFill` →
`TargetForm.setBandColorFrame`, documented as "the color frame for multiple bands generated by a
statistic". Same `CategoricalColorModel` type and same palette catalog, but these are statistical
band fills, not series colors.

## Error handling and edge cases

**Fallback chain.** `resolve(name, head)` falls back to `spliceLegacy(head)` — today's exact
behavior — when the frame is null, its count is under 40, or any index is null. The null case is
real: `ColorPalettes` sizes `Color[max]` from the highest declared index (`:127-132`), so a
`format.css` declaring only indices 1–8 and 40 yields a 40-length array with 31 null holes. A null
reaching `Graphics.setColor` would NPE mid-render, so the guard is load-bearing.

**Never alias the cached frame.** `ColorPalettes.getPalette()` returns a shared, cached
`CategoricalColorFrame` per org, and `applyModernPalette()` calls `frame.setDefaultColors(...)` on the
chart's frame. Colors must be copied out by index via `getDefaultColor(i)` into a fresh array;
handing the cached frame's internal list to a chart frame would let one chart's mutation contaminate
every other chart in the org.

**New lock on the render path — mitigated.** `ColorPalettes.getPalette()` is
`synchronized(ColorPalettes.class)` and `applyModernPalette()` runs per chart render, potentially on
parallel threads; today it touches no shared monitor. The memo keyed on org ID +
`getOrgScopedCSSLastModified()` means the synchronized block is entered only when the CSS actually
changes. The existing 10-second throttle means a stale palette can persist up to 10s after an edit,
matching how every other CSS-driven value already behaves.

**Org isolation.** `ColorPalettes` caches per `OrganizationManager.getCurrentOrgID()`, so the memo key
must include org ID or one tenant's `format.css` override would leak into another's charts.

**Customer overrides keep working.** Un-named `ChartPalette[index='N']` rules still apply at the CSS
tier via `updateCSSColors()` and still win over the modern defaults. A
`ChartPalette[name='Modern'][index='3']` override now flows into render, dialog, and swatch grid from
one edit — the intended upside of decision 4.

**Client fallbacks.** HTTP failure → `catchError` keeps `DefaultPalette.chart`; the picker never sees
an error. Response length ≠ 40 → keep the fallback, since a ragged grid would break the template's
row iteration.

**Accepted limitation.** Toggling dark mode in EM while a composer session is open leaves the picker
grid stale until reload, so it briefly disagrees with the re-rendered chart. This matches existing
behavior — `body.viz-dark` is also only set at bootstrap — so a reload is already required for dark
mode to take full effect.

## Testing

### Java

Extend `VSChartPaletteDefaultsTest` (already covers gate-off no-op, dark selection, warmed/fresh
frames):

- CSS-resolved path returns the declared colors: indices 1–8 equal `MODERN_HEAD`, 9–40 equal
  `COLOR_PALETTE[8..39]` — simultaneously the no-output-change proof
- `darkPalette()` resolves `Modern Dark`
- Three fallback triggers independently: name absent, count < 40, null hole at index 12
- Returned array is a copy: mutate it, re-resolve, assert the second call is unaffected
- Memoization: unchanged CSS timestamp resolves identically; a bumped timestamp re-resolves

**Drift guard:** assert CSS `Modern` indices 9–40 equal `CategoricalColorFrame.COLOR_PALETTE[8..39]`.
Without it, editing the tail in `defaults.css` would silently make the CSS path and the
`spliceLegacy` fallback disagree — a bug visible only when the fallback triggers.

New `ColorPalettes` test: both names appear in `getPaletteNames()` with 40 non-null colors each, and
`Default` is unchanged. New endpoint test: three gate states return Default / Modern / Modern Dark,
with `#rrggbb` formatting matching `Tool.toString`.

`VGraphPairModernPaletteTest` must pass **unmodified** — the render-level no-change proof.

### Frontend

`chart-palette.service.spec.ts` using `HttpTestingController`: success → 5×8 grid, HTTP error →
`DefaultPalette.chart`, wrong length → fallback, one fetch shared across injections. A plain
`.spec.ts` rather than `.tl.spec.ts`, because `HttpTestingController` already asserts the exact URL
and flushes error responses and there is no DOM to assert on.

`palette-dialog` spec: modern colors pre-select `Modern` with `_reversed` false; dark colors select
`Modern Dark`; legacy colors still select `Default`; an unmatched custom palette still falls back to
index 0.

Format-surface regression tests:

- `vs-formats-pane` — foreground → `palette`, background → `bgWithTransparent`, value fill →
  `fgWithTransparent`
- `binding-border-pane` — border dropdown still resolves `DefaultPalette.chart`
- `color-dropdown` truth table over `isBg × transEnabled × chart`, locking the branch order

Plus `combined-color-pane.reset()` uses service colors, and an extension to the existing
`categorical-color-pane.interaction.tl.spec.ts` for the series grid.

### Manual verification

`mvnw test -pl core`, `npm run test:portal`, then run the app:

- gate off → everything legacy
- gate on light → chart modern, dialog pre-selects `Modern`, grid row 1 is the modern 8
- gate on dark → pre-selects `Modern Dark`
- a >8-series chart shows 40 distinct colors, no wrap
- export to PDF/PNG/Excel matches live view
- visual pass over the Format dialog's foreground, background, value fill, and border pickers
  confirming they are unchanged
- OK on `Modern` while gate-on light writes no USER colors (chart still follows the dark toggle); OK
  on `Modern Dark` in light mode pins it

## Delivery

Every file in this design lives under `community/`, so this is a **community-repo PR only** — no
enterprise PR and no submodule pointer bump.

## Explicitly out of scope

- Redesigning indices 9–40 (the legacy tail is retained deliberately; decision 6)
- EM's own color pickers
- The non-chart `DefaultPalette.palette` / `fgWithTransparent` / `bgWith*` grids
- Gradient and HSL color frames
- `b-categorical-color-pane` statistical band fills
- Any change to the modern/dark gate properties themselves
- `@SwitchOrg`/`@OrganizationID` on `ChartColorPaletteController`: the endpoint resolves the
  caller's current org from thread context, like every other unswitched portal endpoint; the
  Angular service fetches once at bootstrap with no org parameter to switch on, so adding those
  annotations here would be misleading rather than protective
