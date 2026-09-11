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
3. **One authority; the org gate is only the absent-assembly default.** The binding picker resolves
   `VizContext.of(mark)` from the chart's own `VizMark`. A caller with no assembly to resolve falls
   through to `VizContext.ofGate()` as the null-branch default, and a mark that cannot be resolved
   falls back the same way rather than failing the request — the mark decides which names carry the
   flag, never which palettes are returned.

   **Three hosts render `categorical-color-pane`, and only one supplies assembly context.**
   `color-field-mc` (the binding editor) passes `vsId`, `assemblyName` and `assetId`. The other two —
   `visual-dropdown-pane` and the date-comparison pane — pass none of the three, and that is
   pre-existing: `ngOnInit` calls `createAssetEntry(this.assetId).organization`, and
   `createAssetEntry` returns `null` for an unparseable id, so both throw before the request is
   made. Neither can reach the org-gate branch at all until that is fixed, which is a separate
   defect. The target-band pane is a fourth caller, through `b-categorical-color-pane`, and it does
   null-guard that call; it deliberately shows every palette, because it cannot tell a classic chart
   from a modern one and filtering it on the org gate would hide retired palettes from classic
   charts in a modern org.
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
params. `VSChartBindingService` gains a `@ClusterProxyMethod(WorksheetEngine.CACHE_NAME)` with
`@ClusterProxyKey String vsId` that resolves the runtime viewsheet, reads the assembly's
`VSAssemblyInfo`, and **returns only its `VizMark`**. The controller keeps the list-building and
derives `VizContext.of(mark)` from the result.

**The proxy returns the mark rather than the whole list, because a null `vsId` cannot route.** The
generated proxy is `isLocal(vsId)` followed by `affinityCall(vsId, ...)`, so with no key there is no
node to call — and the target-band pane sends no key. The controller therefore has to branch on null
before it can invoke the proxy at all. Moving the list-building across that boundary would mean
duplicating it in the null branch or reaching past the proxy; returning an enum keeps one code path
for the list and one small branch for the context.

`@SwitchOrg` survives the hop: it pushes a `SwitchOrgAspectTask` onto `ServiceProxyContext.aspectTasks`
and the generated callable replays it through `preprocess()` on the receiving node.

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

**Java — five classes assert the old hexes, not one.** A sweep for the sixteen head colours finds
31 real assertion sites:

| Class | Sites |
|---|---|
| `VSChartPaletteDefaultsTest` | 20 |
| `ColorPalettesModernTest` | 4 |
| `VSChartPaletteCssOverrideTest` | 4 |
| `ChartColorPaletteControllerTest` | 4 |
| `VGraphPairModernPaletteTest` | 1 |

`CategoricalColorDerivedPersistenceTest` carries 12 further occurrences and **must be left alone** —
they are arbitrary fixture colours handed to `setDerivedColor` and asserted for round-trip, so they
pass either way. Changing them would be noise.

- `ColorPalettesModernTest.modernHeadMatchesSpec` updates to the new slot-1 and slot-8 hexes.
- `tailMatchesLegacyPalette`, `defaultPaletteIsUnchanged` and `modernDeclaresFortyNonNullColors`
  stay green untouched. They are the drift guards proving only the head moved.
- New, in `VSChartPaletteDefaultsTest`: assert `MODERN_HEAD` and `DARK_HEAD` equal `defaults.css`
  indexes 1-8. Nothing today catches the Java constants and the CSS drifting apart, and section 1
  depends on them moving together. It reads the two `private` constants **by reflection**, which is
  already the idiom in that file — `clearMemoDiscardsTheCachedEntry` reaches `MEMO` the same way — so
  no production visibility widens for a test's benefit.
- New: `Contrast` is registered and declares 8 non-null colours, and `Default` is still declared
  first in `defaults.css`.
- New: `hiddenPaletteNames(VizContext)` returns exactly the nine names under a modern mark and is empty
  under a classic one.

**Frontend**

- Update `MODERN_HEAD` and `DARK_HEAD` in `palette-test-fixtures.ts`. Three of the four consuming
  spec files assert fixture-relative and follow automatically. **`chart-palette.service.spec.ts` does
  not** — it hardcodes `#00d4e8`, `#64748b` and `#00b87a` at `:54,55,63,74`. Convert it onto the
  fixture rather than editing the literals, so it cannot drift again.
- New `palette-dialog.spec.ts` cases: a hidden palette stays pre-selectable when it is the chart's
  current one, and is absent from the dropdown when it is not.

**Observed, not acted on.** `_viz-tokens.scss:62` sets `--inet-viz-selected-border-dark: #2DD4BF`,
exactly the old `DARK_HEAD[6]`. The re-tune removes that value from the dark palette, so the
selected border stops colliding with a series colour. Confirmed incidental; the token is unchanged.

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
nothing. The consequence is instead the pre-selection problem in section 3, and this:
`VGraphPair.java:1300` and `ChangeChartProcessor.java:1893` both call
`VSChartPaletteDefaults.applyModernPalette`, an unconditional `setDefaultColors(activePalette(ctx))`
whenever `ctx.modern` — so a modern-marked chart's default tier *is* re-resolved from live CSS on
every render, and does take the new head.

The opening this leaves: a modern chart whose old head colours were pinned into the **user** tier
(possible for sheets saved before the resolved-default guard landed) cannot be overwritten by
`applyModernPalette` — the user tier wins over defaults unconditionally. That chart's rendered
colours then match no registered palette, the dialog shows `Default` selected, and a no-op OK
repaints it to the classic legacy 40 — the exact hazard section 3 exists to prevent, arriving
through a door section 3 did not consider. It is not something an automated test can cover.

**Checked manually on 2026-09-11 and it does not occur.** A dashboard created under the modern gate
before the re-tune still pre-selects `Modern` in the palette dialog afterwards, so the stored head
is re-resolved as described rather than stranding the chart on an unmatched colour list. Recorded
here so the next re-tune does not have to re-derive it — though the check is worth repeating, since
what makes it safe is the default tier holding those colours, not anything this branch enforces.

**A "hard-fail with a migration warning" for `Gray` has nowhere to live.** There is no name lookup
at load time to fail in.

## Files touched

| File | Change |
|---|---|
| `core/src/main/resources/inetsoft/util/css/defaults.css` | `Modern` 1-8, `Modern Dark` 1-8, new `Contrast` 1-8 |
| `core/src/main/java/inetsoft/uql/viewsheet/internal/VSChartPaletteDefaults.java` | `MODERN_HEAD`, `DARK_HEAD`, new `hiddenPaletteNames(VizContext)` |
| `core/src/main/java/inetsoft/web/binding/VSChartBindingController.java` | `getColorPalettes` gains `vsId`, `assemblyName`; builds the list, branches on a null key |
| `core/src/main/java/inetsoft/web/binding/VSChartBindingService.java` | new `@ClusterProxyMethod` returning the assembly's `VizMark` |
| `core/src/main/java/inetsoft/web/binding/model/graph/aesthetic/CategoricalColorModel.java` | `hidden` transport flag |
| `web/projects/portal/src/app/binding/editor/chart/aesthetic/categorical-color-pane.component.ts` | send `vsId` and `assemblyName` |
| `web/projects/portal/src/app/binding/editor/chart/palette-dialog.component.ts` | filter hidden entries from `paletteSelectOptions` |
| `web/projects/portal/src/app/widget/color-picker/palette-test-fixtures.ts` | new head hexes |
| `core/src/test/java/inetsoft/uql/viewsheet/internal/VSChartPaletteDefaultsTest.java` | 20 hex sites; new CSS↔Java drift guard and `hiddenPaletteNames` cases |
| `core/src/test/java/inetsoft/uql/viewsheet/graph/aesthetic/ColorPalettesModernTest.java` | 4 hex sites; new `Contrast` and declaration-order cases |
| `core/src/test/java/inetsoft/uql/viewsheet/internal/VSChartPaletteCssOverrideTest.java` | 4 hex sites |
| `core/src/test/java/inetsoft/web/portal/controller/ChartColorPaletteControllerTest.java` | 4 hex sites |
| `core/src/test/java/inetsoft/report/composition/graph/VGraphPairModernPaletteTest.java` | 1 hex site |
| `web/projects/portal/src/app/widget/color-picker/chart-palette.service.spec.ts` | converted off hardcoded hexes onto the fixture |
| `web/projects/portal/src/app/binding/editor/chart/palette-dialog.spec.ts` | new hidden-palette cases |

Sixteen files. `CategoricalColorDerivedPersistenceTest` is deliberately **not** in this list despite
carrying 12 occurrences of the old hexes — see section 4.

Every path is inside the `community` submodule, so this ships as a community PR.
