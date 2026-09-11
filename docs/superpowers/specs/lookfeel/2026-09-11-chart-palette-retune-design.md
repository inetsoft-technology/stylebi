# Chart palette re-tune — design

**Date:** 2026-09-11
**Status:** approved, not implemented
**Branch:** `epic-74519` (base)
**Source:** the external design set `SBI Color and Type Pairings.dc.html` and its
`design_handoff_chart_palettes/` folder (README, CSS.md, ENGINE.md, github.md), exported
2026-09-11. The handoff's last repo sync was 2026-08-09 against `epic-74519`.

## What this is

The first slice of the chart palette redesign. It re-tunes the eight head colours of `Modern`
and `Modern Dark`, adds `Contrast` as a new categorical palette, and gates the palette picker on
the chart's own modern mark. It is palette data, one endpoint change, and a dropdown filter. Nothing in
`inetsoft.graph` changes, no new colour-frame classes are added, and no persisted contract moves.

## Why the handoff could not be applied as written

The handoff proposes 11 `ChartPalette` names become 6, with `Default` narrowing to 8 slots. Four
findings, each verified against the code now on `epic-74519`, made that inapplicable as a first step.

**`Modern` already occupies the slot the design asks for.** `VSChartPaletteDefaults` ships
`MODERN_HEAD` — eight curated bases — with a `Modern Dark` light/dark pair, resolved by name from
`defaults.css`, gated on the modern mark, and padded to 40 via `spliceLegacy()`. The design's
`Default · 8` is the same structure with different hexes. Shipping it under its own name would
leave two competing modern defaults.

**There are 13 palettes on this branch, not 11.** `Modern` and `Modern Dark` landed after the
handoff's last sync and appear nowhere in its migration map.

**The CSS block is 0-based; the loader is 1-based.** `ColorPalettes.loadPalettes()` skips any
index below 1, and shipped rules run `index='1'` upward. Applied verbatim, every palette would
lose its first colour.

**An 8-slot `Default` would be silently ignored.** `VSChartPaletteDefaults.fromFrame()` falls back
to `spliceLegacy()` whenever the frame declares fewer colours than
`CategoricalColorFrame.COLOR_PALETTE`, which is 40. Non-modern charts would keep rendering the
classic 40 regardless of the CSS.

## Decisions

1. **Re-tune `Modern` in place.** The design's eight bases replace `MODERN_HEAD`; its dark eight
   replace `DARK_HEAD`. No new palette names for the default set. The gate, Revert, the seed mark
   and picker pre-selection are all untouched.
2. **Gate retirements on the mark; ship additions to everyone.** A modern-marked chart sees the
   reduced list. A classic chart keeps all of today's names. `Contrast` is offered to both, because
   an accessibility palette is not a look-and-feel choice.
3. **Two gate authorities, deliberately.** The binding picker reads the chart's own `VizMark`; the
   target-band colour pane reads the org gate, because it has no assembly to read.
4. **Ramps are out of scope.** `Variance`, `Amber` and `Teal` are `LinearColorFrame` work, not CSS
   (see Corrections below).

## 1. Palette data

`core/src/main/resources/inetsoft/util/css/defaults.css`, 1-based indexes.

`Modern` indexes 1-8 take the design's light bases:

| 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 |
|---|---|---|---|---|---|---|---|
| `#0490FF` | `#FF5A35` | `#241C4F` | `#03D9B3` | `#9A2DDC` | `#FFB020` | `#E5197E` | `#8ED604` |
| Azure | Coral | Ink | Teal | Violet | Amber | Magenta | Acid |

`Modern Dark` indexes 1-8 take the design's dark bases. This is not the light set lifted — slot 3
inverts, because Ink is already the darkest member and has nowhere to deepen to:

| 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 |
|---|---|---|---|---|---|---|---|
| `#4FA5FF` | `#FF8367` | `#49447D` | `#2DEEC6` | `#AE41F5` | `#FFCB82` | `#FE3290` | `#9FEB28` |

`Contrast`, new, indexes 1-8 only:

| 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 |
|---|---|---|---|---|---|---|---|
| `#0B3D91` | `#F5943F` | `#B04AB8` | `#F7D22E` | `#A8330A` | `#7CC4EE` | `#00947F` | `#5FA83C` |
| Navy | Orange | Orchid | Yellow | Brick | Sky | Teal | Green |

Every member sits on its own rung of a luminance ladder, no two closer than 0.05 relative
luminance across a 0.05-0.66 span, and the slot order interleaves the ladder so a two- or
three-series chart draws from opposite ends. `Contrast` gets **no dark variant**: its contract is
paper and projection.

Eight slots is a supported shape. `Soft` already ships at 8, and `PaletteDialog.saveChanges()`
splices a short palette over the first *n* colours while preserving the chart's existing tail.

Indexes 9-40 of `Modern` and `Modern Dark` are **untouched**.

`MODERN_HEAD` and `DARK_HEAD` in `VSChartPaletteDefaults` take the same eight hexes each. **Both
sides must move together** — the Java constants are the fallback when a CSS rule is missing or
malformed, so stale constants would make a broken `format.css` render the old palette.

## 2. Picker gating

`VSChartBindingController.getColorPalettes` gains optional `vsId` and `assemblyName` request
params and moves its body into `VSChartBindingService` as a
`@ClusterProxyMethod(WorksheetEngine.CACHE_NAME)` with `@ClusterProxyKey String vsId`, matching
`getChartBinding`. It resolves the runtime viewsheet, reads the assembly's `VSAssemblyInfo`, and
derives `VizContext.of(info)`.

| Caller | Context | Why |
|---|---|---|
| `categorical-color-pane` (binding picker) | `VizContext.of(info)` | already holds `vsId` and `assemblyName`; survives an org gate switched off after modernization |
| `b-categorical-color-pane` (target band fill) | `VizContext.ofGate()` | holds only `assetId`; same reasoning as `ChartColorPaletteController` |

The target-band pane sends no new params, so it lands on the absent-assembly branch naturally. Its
component needs no change.

Reading the chart's mark rather than the org property matters because the org gate is creation-time
only: a dashboard already using the modern look keeps it when an admin switches the gate off. A
picker reading the property would strand those charts with a list that does not match what they
render.

**The policy lives in `VSChartPaletteDefaults` as `hiddenPaletteNames(VizContext)`**, returning the
set of names to mark hidden for that context — empty under a classic mark. It does not remove
anything from the list; section 3 explains why. That class already owns modern-palette policy,
already imports `ColorPalettes`, and sits beside `VizContext` in `uql.viewsheet.internal`.
`ColorPalettes.getPaletteNames()` stays unfiltered.

**`getPalette(name)` resolution stays unfiltered unconditionally.** `legacyPalette()` resolves
`"Default"` and `modernPalette()`/`darkPalette()` resolve `"Modern"`/`"Modern Dark"` by name.
Filtering resolution would break rendering.

Hidden under a modern mark, nine names: `Pastel`, `Heat 8`, `Heat 16`, `Heat 24`, `Blue`, `Green`,
`Red`, `Orange`, `Gray`. Visible under either mark: `Default`, `Soft`, `Modern`, `Modern Dark`,
`Contrast`.

**`Default` stays visible to modern charts.** It is declared first in `defaults.css` precisely so
the index-0 fallback is stable, and filtering preserves CSS order — keeping it leaves that
invariant pointing where it points today, in both lists. It also leaves a per-chart route back to
the classic 40 that does not require reverting the whole dashboard.

**`Modern` and `Modern Dark` stay visible to classic charts**, as today. They are neither an
addition nor a retirement, and hiding them would be a new restriction outside this scope.

## 3. Pre-selection

`getPaletteIndex()` returns 0 when nothing matches, and `saveChanges()` splices whatever the dialog
is displaying over the chart's colours. So a chart whose palette is not in the list shows "Default"
selected and repaints on a no-op OK. That path exists today. Omitting hidden palettes from the
response would widen it to every modern-marked chart sitting on one of the nine.

**The response carries every palette with a `hidden` flag** — set from `hiddenPaletteNames(ctx)` —
rather than omitting entries.

- `getPaletteIndex()` continues to match against every palette, so a modern chart on `Pastel` still
  pre-selects `Pastel` and nothing repaints.
- `paletteSelectOptions` drops hidden entries from the dropdown **except** the currently selected
  one, keeping the original array position as the option `value`. It already emits
  `{value: index, label: name}`, so this is a filter over the existing shape.

The resulting semantics: a retired palette is not offered for new choices and is never taken away
from a chart already using it.

## 4. Testing

**Java — `ColorPalettesModernTest`**

- `modernHeadMatchesSpec` updates to the new slot-1 and slot-8 hexes for both palettes.
- `tailMatchesLegacyPalette`, `defaultPaletteIsUnchanged` and `modernDeclaresFortyNonNullColors`
  stay green untouched. They are the drift guards proving only the head moved.
- New: assert `MODERN_HEAD` and `DARK_HEAD` equal `defaults.css` indexes 1-8. Nothing today catches
  the Java constants and the CSS drifting apart, and section 1 depends on them moving together.
- New: `Contrast` is registered and declares 8 non-null colours.
- New: `hiddenPaletteNames(VizContext)` returns exactly the nine names under a modern mark and is empty
  under a classic one.

**Frontend**

- Update `MODERN_HEAD` and `DARK_HEAD` in `palette-test-fixtures.ts`. The four spec files that
  consume them assert fixture-relative and follow automatically.
- New `palette-dialog.spec.ts` cases: a hidden palette stays pre-selectable when it is the chart's
  current one, and is absent from the dropdown when it is not.

## 5. Deferred, with triggers

| Item | Trigger |
|---|---|
| ENGINE §3 palette overflow generator | Any change narrowing a palette below 40 slots, or a decision to replace the legacy tail. Keeping `Modern` at 40 removes the urgency, not the need — the slot-9 seam between the new heads and the 2010-era tail is real and unaddressed. |
| ENGINE §0/§1/§2/§5 companions | Independent. The largest behavioural win left. Needs the sRGB-OKLab utility plus `Modern-soft` and `Modern Dark-soft` CSS. |
| `Variance`, `Amber`, `Teal` ramps | Each needs an `AbstractSplineColorFrame` subclass, a `LinearColorFrameWrapper`, and a `VisualFrameWrapper` switch case. |
| ENGINE §6 multi-level shading | Blocked upstream: nothing passes depth, breadth or sibling index into colour resolution. |
| `Soft` re-authoring, `Pastel` folded into companions | Both fold into the companion work. |

## Corrections to the source handoff

Recorded so a later slice does not re-derive them.

**The CSS block is 0-based and must be applied 1-based.** `CSS.md` hedges and then writes
`index=0..7`.

**`Amber`, `Teal` and `Variance` are not CSS, and the handoff contradicts itself.** `CSS.md`
declares them as `ChartPalette` rules, but `ColorPalettes.loadPalettes()` builds a
`CategoricalColorFrame` for every `ChartPalette` name — so declaring them that way puts them in the
categorical picker, which ENGINE §4 explicitly forbids. A real ramp is an
`AbstractSplineColorFrame` subclass with a `LinearColorFrameWrapper` and a case in
`VisualFrameWrapper`'s class-name switch, which is also where ramps, unlike palettes, are
persisted by name.

**ENGINE §3's premise does not hold under decision 1.** It argues the overflow wrap is "survivable
at 40 slots, not at 8". Keeping `Modern` at 40 means the wrap engages only past 40, exactly as
today. Deferred, not declined.

**The README's open question is answered: no.** Palette names are not stored in saved viewsheet
XML. `PaletteDialog.saveChanges()` emits a `CategoricalColorModel` carrying only colours, and
`VisualFrameModel.name` is transport-only. So no alias table is needed — retiring a name repaints
nothing. The consequence is instead the pre-selection problem in section 3, and the fact that
existing dashboards never pick up a re-tuned palette, because they hold resolved hexes.

**A "hard-fail with a migration warning" for `Gray` has nowhere to live.** There is no name lookup
at load time to fail in.

## Files touched

| File | Change |
|---|---|
| `core/src/main/resources/inetsoft/util/css/defaults.css` | `Modern` 1-8, `Modern Dark` 1-8, new `Contrast` 1-8 |
| `core/src/main/java/inetsoft/uql/viewsheet/internal/VSChartPaletteDefaults.java` | `MODERN_HEAD`, `DARK_HEAD`, new `hiddenPaletteNames(VizContext)` |
| `core/src/main/java/inetsoft/web/binding/VSChartBindingController.java` | `getColorPalettes` gains `vsId`, `assemblyName`; body delegates |
| `core/src/main/java/inetsoft/web/binding/VSChartBindingService.java` | new `@ClusterProxyMethod` resolving the assembly and filtering |
| `core/src/main/java/inetsoft/web/binding/model/graph/aesthetic/CategoricalColorModel.java` | `hidden` transport flag |
| `web/projects/portal/src/app/binding/editor/chart/aesthetic/categorical-color-pane.component.ts` | send `vsId` and `assemblyName` |
| `web/projects/portal/src/app/binding/editor/chart/palette-dialog.component.ts` | filter hidden entries from `paletteSelectOptions` |
| `web/projects/portal/src/app/widget/color-picker/palette-test-fixtures.ts` | new head hexes |
| `core/src/test/java/inetsoft/uql/viewsheet/graph/aesthetic/ColorPalettesModernTest.java` | updated and new assertions |
| `web/projects/portal/src/app/binding/editor/chart/palette-dialog.spec.ts` | new hidden-palette cases |

Every path is inside the `community` submodule, so this ships as a community PR.
