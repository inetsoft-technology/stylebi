# Chart ramps — design

**Date:** 2026-09-16
**Status:** approved, not implemented
**Branch:** `epic-74519` (base). Depends on `OKLab` from
[#5275](https://github.com/inetsoft-technology/stylebi/pull/5275); if that has not merged when this
starts, base on `feature-chart-companion-colors` as the brushing slice did.
**Source:** ENGINE §4 of the external design set `SBI Color and Type Pairings.dc.html` and its
`design_handoff_chart_palettes/` folder, recovered into
`docs/superpowers/specs/lookfeel/design-handoff-chart-palettes/`.

## What this is

ENGINE §4 — the third slice of the chart palette redesign, after the
[re-tune](./2026-09-11-chart-palette-retune-design.md) and the
[companions](./2026-09-14-chart-companion-colors-design.md). It authors three house ramps for the
measure→colour path — `Amber`, `Teal` and `Variance` — hides the legacy ramps they succeed from a
modern-marked chart, and re-points the seeded default so a modern chart stops being born on a
2010-era ColorBrewer ramp.

It is the categorical re-tune's missing half. The re-tune gated the *categorical* picker on the
assembly's `VizMark` and re-tuned the series colours; a chart that binds a **measure** to colour has
been untouched by any of it.

## Why the source needed correcting

Four findings, each verified against the code now on `epic-74519`.

**The ramp machinery already exists, in full.** ENGINE §4 reads as a build. Twenty-seven ColorBrewer
ramps ship today as `AbstractSplineColorFrame` subclasses — six single-hue, twelve multi-hue, nine
diverging — each with a wrapper, a `ColorFrameModelFactory` inner class, a `VisualFrameWrapper`
switch case, a TypeScript model and an entry in the pane's group array. The slice is authoring three
more, not building the mechanism.

**`Variance` is not the first diverging ramp.** ENGINE §4 calls it "New — nothing today serves above
or below target". `BrBG`, `PiYG`, `PRGn`, `PuOr`, `RdBu`, `RdGy`, `RdYlBu`, `RdYlGn` and `Spectral`
are all diverging and all reachable from the pane's own **Diverging** row. What Variance adds is a
*house* diverging ramp, not the capability.

**The default measure→colour frame is `BluesColorFrame`, not the gradient generator.** It is
hard-coded at eight sites: `ChangeChartTypeProcessor` 166, 863, 1176, 1250, 1344, 1420;
`ChangeChartProcessor:234`; `GraphUtil:951`. A ninth, `DensityForm:127`, is a field initialiser.
`GradientColorFrame` is the *pane's* client-side editor default and `MapGenerator`'s, which is what
the handoff was reading.

**The handoff's stops are derived against `#F8F7F4` only and cannot be used unchanged.** Amber's low
stop `#FCEFE0`, Teal's `#E2F4F2` and Variance's midpoint `#EFEDE7` are all tints of the light canvas.
Each is *meant* to recede into the surface — "least", "least", "at target" — and each becomes the
brightest thing on screen against the dark surface `#252428`
(`VSChartChromeDefaults.LEGEND_BG_DARK`, `--dark-surface-default`).

## Decisions

1. **Author three ramps, gate the legacy ones, and re-seed the modern default.** All three, not a
   subset. Adding without re-seeding leaves a modern chart born on Blues; gating without re-seeding
   takes Blues out of the picker while still starting the chart there.
2. **One set of stops per ramp, tuned to clear both surfaces.** No `Teal` / `Teal Dark` pair and no
   render-time substitution. A `SplineColorFrame` is persisted by class name through
   `VisualFrameWrapper`, so a dark variant either does not follow a theme switch (separate classes)
   or reopens the render-time mechanism #5275 withdrew. Neither is worth a second palette axis.
3. **Derive the stops by rule from the handoff, author the output, guard the drift.** Hue and
   relative chroma come from the source; lightness is remapped to clear both surfaces. The derived
   hexes are authored literally and a test asserts the rule still reproduces them — the idiom
   `VSChartPaletteDefaultsTest` already uses for companions.
4. **Hide a family the house set succeeds; keep a family it does not.** The rule is at family
   granularity, not per ramp. Amber and Teal succeed the single-hue family and Heat; Variance
   succeeds the diverging family. Multi-hue has no house member and survives intact; so does Custom,
   which ENGINE §4 explicitly retains for brand matching and one-offs.

   **`Greys` and `Purples` go with their family and have no individual successor.** That is the
   linear analogue of the categorical `Gray`, which the handoff's own migration table retires with
   "*(none)*", and it is accepted here on the same terms: nothing is removed from resolution, so a
   chart already on either keeps rendering it.
5. **The assembly's mark is the authority; the org gate is the absent-assembly default.** As the
   re-tune decided. It matters more here: the categorical side survives a stale gate because
   `applyModernPalette` re-resolves from live CSS at `VGraphPair:1300` on every render, and decision
   2 denies the linear path that correction. A wrong seed here is permanent.

## 1. Ramp data and the derivation rule

Three classes in `inetsoft.graph.aesthetic`, each returning a single seven-stop entry from
`getColorRamps()`. Every construction site found uses the no-arg constructor, which resolves
`ramps[ramps.length - 1]`, so the multi-stop-count tables the ColorBrewer ramps carry are vestigial
and a one-entry array is correct.

### Sequential — Amber and Teal

Keep each stop's OKLCH hue and its relative chroma profile from the handoff. Remap the authored
lightness span into a band where every stop clears both surfaces. The low end stops being a
near-white tint and becomes a light but chromatic colour; magnitude still reads as lightness over a
shorter span, with chroma carrying more of the difference.

### Diverging — Variance

This one inverts, and it is the only place this design departs from the source rather than re-tuning
it.

Variance's midpoint means *at target*, and the handoff spells that as the canvas neutral `#EFEDE7` —
absence rendered as dissolving into the surface. A single hex can only dissolve into one surface. So
on a dual-surface ramp the midpoint becomes a mid-lightness achromatic grey, and the signal moves
channel: **chroma carries magnitude, hue carries direction.** Chroma runs from near zero at the
centre to its maximum at both wings; lightness stays near-flat across the ramp.

"At target" therefore reads as a grey mark rather than as an absent one. That is a real loss against
the source's intent and is recorded as such — see *Deferred*.

### Acceptance constraints

The rule is stated as constraints rather than as a band, so it is falsifiable. Each is measured
during implementation and pinned by the drift guard.

| | Constraint |
|---|---|
| **C1** | **Legibility.** Every stop clears both `#F8F7F4` and `#252428` by **ΔL ≥ 0.12** in OKLab. The usable band follows from the two surfaces' measured lightness rather than being asserted here; measure them first, and if the band that falls out is too narrow to satisfy C2, that is a C1-versus-C2 conflict and comes back to this document. |
| **C2** | **Ordering.** L strictly monotonic across Amber and Teal, spanning **at least 0.30** end to end so magnitude still reads. For Variance, chroma strictly monotonic outward from the centre on each wing, with L held inside a **0.10** band across the whole ramp so lightness carries no signal. |
| **C3** | **Gamut.** Every derived stop survives `OKLab.toColorInGamut` without chroma collapse — the failure that turned the legacy tail's lightest entries into white companions, measured in [the §3 trigger record](./engine-3-palette-overflow-trigger.md). |
| **C4** | **Hue fidelity.** Derived hue stays within **5°** of the handoff's in OKLCH, so the ramps stay recognisably Amber, Teal and Variance. Variance's midpoint is exempt — it is achromatic by construction and has no meaningful hue. |

If derivation cannot satisfy all four, that is a design-level finding and comes back to this document
rather than being settled in the implementation.

## 2. Component inventory

Per ramp, six artefacts, each copied from an existing ColorBrewer ramp:

| Artefact | Shape |
|---|---|
| `graph/aesthetic/{Amber,Teal,Variance}ColorFrame` | `extends AbstractSplineColorFrame`, one seven-stop entry |
| `…graph/aesthetic/{…}ColorFrameWrapper` | `extends LinearColorFrameWrapper`, one `createVisualFrame()` |
| `ColorFrameModelFactory.{…}ColorFactory` | inner class beside the twenty-seven existing |
| `VisualFrameWrapper.createWrapper` case | spelled exactly — see the trap below |
| `visual-frame-model.ts` model | one class |
| pane group array | `singleHueModels` gains Amber and Teal; `divergingModels` gains Variance |
| `assets/{Amber,Teal,Variance}.png` | **new** — the dropdown swatch, 80×19, plus a `getSrc()` case |

**The dropdown swatch is a static image, not a rendered gradient.**
`linear-color-dropdown.component.ts` maps each model name to a PNG under
`web/projects/portal/src/assets/` through a `getSrc()` switch, and every one of the twenty-seven
existing ramps has a file there at 80×19. A new ramp with no asset renders a broken image rather
than failing, so the swatches are part of the deliverable and are generated from the same authored
stops as the frame — which also makes them a visual check on the derivation.

Plus one resolver, `VSChartPaletteDefaults.defaultLinearFrame(VizContext)`, beside `activePalette`
and `companionOf` in the class that already owns modern-palette policy.

**The trap.** `VisualFrameWrapper:258` reads `case "BluesColorFrameW":`, but `stripInnerName` strips
a trailing `"Wrapper"` and hands the switch `"BluesColorFrame"`. The case never matches and Blues
falls through to the `Class.forName` default — harmless, since the default resolves correctly, and
invisible, since nothing fails. The switch is explicitly an optimization, so a misspelled case only
costs a reflective load and is not a correctness defect; ours are spelled correctly regardless, and
no test can or should detect the difference. Fixing the existing typo is out of scope.

**No name collision.** `defaults.css` declares no `ChartPalette` named Amber, Teal or Variance —
these three names exist only as frame classes, which is what the re-tune's Corrections require.
`SplineColorFrame.equals` compares class identity only, so nothing there needs touching.

## 3. Picker gating

`VSChartPaletteDefaults.hiddenLinearFrames(VizContext)` returns the frame class names to hide — empty
under a classic mark; under a modern mark the six single hues, the nine diverging and Heat.
Multi-hue and Custom are absent by decision 4. The policy stays in Java beside `hiddenPaletteNames`;
the client remains a filter over its existing `singleHueModels` and `divergingModels` arrays, so the
pane keeps one template rather than forking on the mark.

The Heat row is a radio plus a fixed `assets/heat.png` rather than a dropdown entry, so hiding it is
a row-level conditional, not an array filter.

**Wiring.** `color-field-mc.component.html:94-96` already passes `vsId`, `assetId` and `assemblyName`
to `categorical-color-pane` and passes none of them to `linear-color-pane` at `:105`. The inputs
exist on the shared base (`aesthetic-field-mc.ts:41-43`). So the gate is two inputs on the pane and
two attributes in the template.

**Two rules carry over from the re-tune verbatim.**

- **Hide, never omit from resolution.** A modern chart already on `Spectral` keeps rendering
  Spectral, still shows it selected, and does not repaint on a no-op OK. Resolution by class stays
  unfiltered unconditionally.
- **One authority, gate as the absent-assembly default.** `visual-dropdown-pane` supplies no
  assembly and lands on `VizContext.ofGate()`. The component doesn't change; an absent-assembly
  host must still resolve to something sane rather than fail.

## 4. Re-seeding the modern default

Seven literals become `defaultLinearFrame(ctx)` calls, across two signatures that each gain a
`VizContext`:

| Site | Count | Change |
|---|---|---|
| `ChangeChartTypeProcessor` 166, 863, 1176, 1250, 1344, 1420 | 6 | a context-taking overload on each constructor; the existing overloads keep `LEGACY` |
| `GraphUtil.fixVisualFrame(AestheticRef, int, int, ChartInfo)` :951 | 1 | context parameter, public static |

**`ChangeChartProcessor.fixColorField` was going to be the eighth and is not.** An earlier revision
gave it a context overload; that was withdrawn. The method has no caller anywhere in community or
enterprise, so the overload was dead API on dead API — and worse, the base would have taken the
context as a *parameter* where both subclasses hold it as a *field*, so a future caller inside a
subclass would have made the natural two-argument call and seeded the legacy ramp with a correct
context sitting beside it. `ChangeChartProcessor` is untouched by this slice.

The real threading is wider than this table implies. `fixVisualFrame` is reached from
`ChangeChartTypeProcessor.process()` only through `GraphUtil.fixVisualFrames` and
`fixVisualFrames0`, which a grep for `fixVisualFrame(` does not match — so the count of *call sites*
that needed a context is 34, not the seven literals above. The object wizard's recommender adds a
further eight seeds on its own path — recorded in §7, where it is closed rather than deferred.

The ten `ChangeChartTypeProcessor` construction sites each have an answer:

| Caller | Context |
|---|---|
| `ChartVSAssemblyInfo:2574,2585` | `VizContext.of(this)` — it *is* the assembly info |
| `ChangeChartTypeService:152,170` | holds the assembly info |
| `ChartDcProcessor:293,850` | holds the chart assembly |
| `DateComparisonDialogService:197` | holds `assemblyInfo`, already cast to `ChartVSAssemblyInfo` |
| `ChartElementDef:626,644` | the report path — `VizContext.LEGACY`, truthfully |
| `ChangeChartDataProcessor:60` | holds only a `ChartInfo`; takes the same context parameter on its own constructor and passes it through |

`DensityForm:127` is the one seed site with no reachable context — a field initialiser on a
graph-level form. It stays Blues and is deferred, for the same structural reason the brushing slice
deferred the density contour.

**Retaining the no-context overloads means a missed caller fails quietly**, seeding Blues on a chart
that should have had Teal. That is acceptable only because the quiet failure lands on the pre-slice
behaviour rather than on something new, and because the ten construction sites are enumerated above
rather than left to be rediscovered. The alternative — no default, every caller forced to answer —
would break `ChangeChartTypeProcessor`'s existing no-arg constructor at `:46` and widen the diff well
past this slice.

## 5. Testing

**Java.**

- A derivation test asserting C1–C4 against both surface hexes.
- A drift guard pinning the authored stops to the rule's output, mirroring
  `VSChartPaletteDefaultsTest.authoredCompanionsAgreeWithTheRuleExceptTheHandTunedSlot`.
- `defaultLinearFrame` returning Teal under a modern context and Blues otherwise, including the
  dark-modern case, which must still be Teal.
- A `wrap` → XML → `createVisualFrame` round-trip per new frame. This is the test that would have
  caught `"BluesColorFrameW"`, and the reason it is worth writing rather than assuming.
- `hiddenLinearFrames` returning empty under a classic mark and the expected sixteen names under a
  modern one.

**Frontend.** `linear-color-pane.component.tl.spec.ts` already exists. Extend it for modern
filtering, for the Heat row's disappearance, and for selected-but-hidden retention. Scoped runs only
— `ng test portal --include` against the `portal:test-tl` target, never the full TL suite.

**Manual.** A modern chart binding a measure to colour is born on Teal. The same chart in dark mode
reads at both ends. A classic chart is unchanged in picker and in seed. A modern chart saved on
Spectral before this slice still renders and still pre-selects Spectral. A report chart takes
`LEGACY` and is unchanged.

## 6. Failure modes

Nothing is removed from resolution, so a retired or unknown frame class still loads. A mark that
cannot be resolved falls back to `ofGate()` rather than failing the request, as in the re-tune. A
derived stop that violates a constraint fails the drift guard at build time rather than at render.

## 7. Deferred, with triggers

| Item | Trigger |
|---|---|
| **The vswizard recommender's measure→colour seed — closed, not deferred.** | Believing no assembly was reachable from the recommender was wrong, not a scope decision. `VSWizardTemporaryInfoService:69` builds the wizard's temp chart as `new ChartVSAssembly(vs, TEMP_CHART_NAME)` against the real runtime viewsheet, and `AbstractVSAssembly:136` stamps that new assembly with the viewsheet's own `VizMark`. `VSWizardBindingHandler.getTempChart` held that marked assembly all along and returned only its `ChartInfo`, discarding the mark on the way out — so every recommender seed read legacy regardless of the host's mark. `getTempChartContext(VSWizardData)` now reads the mark off the held assembly instead, and the context is threaded through `VSChartDefaultRecommendationFactory.recommend`, `ChartCombinationUtil.createFilters`/`getChartInfos`, and every `ChartTypeFilter` subclass, so a chart created through the object wizard is born on Teal exactly like one built in the composer. No trigger — done. |
| **Designer ratification of the derived stops** | Especially Variance's inversion — chroma carrying magnitude from a grey midpoint is a departure from the source's intent, not a re-tune of it. Trigger: the next sync of the external design set. The constraint and the reasoning must live in a sibling file, never inside `chart-card-design3/`, which is regenerated wholesale. |
| **A house multi-hue ramp** | Multi-hue is the only family left with twelve legacy ramps and no successor, so decision 4 leaves it fully visible to modern charts. Trigger: a decision to author one, at which point the twelve become hideable on the same rule. |
| **`DensityForm:127`** | A contour chart's default stays Blues. Trigger: the density contour colour item the brushing slice deferred is taken up — both are the same structural problem, a graph-level form with no assembly context. |
| **`GradientColorFrame` interpolation** | ENGINE §4 also asks that the two-endpoint generator interpolate in OKLCH along the short hue arc with an optional midpoint, measured at `#8A646F` chroma 0.05 versus `#B850B1` at 0.18. Custom survives this slice untouched. Trigger: a report that a custom two-colour ramp goes muddy through its middle. |
| **Dark-specific ramps** | Closed by decision 2, not open. Trigger: the dual-surface compromise is measured as weak on one surface, which would reopen decision 2 rather than add a variant quietly. |
| **`VisualFrameWrapper:258` `"BluesColorFrameW"`** | Recorded, not fixed. Trigger: any change to that switch for another reason. |
| **`VizContext.LEGACY`'s identity is overloaded with a second meaning.** | Every no-context overload in this slice defaults to the `VizContext.LEGACY` *singleton*, and seven descriptor sites separately compare identity against that exact instance to mean "this is a report chart": `AxisDescriptor:69`, `LegendDescriptor:75`, `LegendsDescriptor:101`, `PlotDescriptor:74`, `TitleDescriptor:71`, `ChartRefImpl:62`, `GraphTarget:83`. Inert today — no context this slice builds ever reaches a descriptor constructor, and `VizContext.of(VSAssemblyInfo)` on an unmarked assembly returns a freshly constructed instance rather than the `LEGACY` sentinel, so a missed seed site cannot masquerade as a report chart through this path. Trigger: a caller ever hands a filter's or seed site's context to a descriptor constructor — an unmarked real viewsheet chart would then satisfy `ctx == VizContext.LEGACY` and be misclassified as a report chart, losing its viewsheet-specific default formatting. |

## Files touched

| File | Change |
|---|---|
| `core/.../graph/aesthetic/AmberColorFrame.java` | **new** |
| `core/.../graph/aesthetic/TealColorFrame.java` | **new** |
| `core/.../graph/aesthetic/VarianceColorFrame.java` | **new** |
| `core/.../uql/viewsheet/graph/aesthetic/{Amber,Teal,Variance}ColorFrameWrapper.java` | **new** ×3 |
| `core/.../uql/viewsheet/graph/aesthetic/VisualFrameWrapper.java` | three switch cases |
| `core/.../web/binding/model/graph/aesthetic/{Amber,Teal,Variance}ColorModel.java` | **new** ×3 — the server-side picker model per ramp |
| `core/.../web/binding/service/graph/aesthetic/ColorFrameModelFactory.java` | three factory inner classes |
| `core/.../web/binding/service/ChartInfoModelBuilder.java` | recognizes the three new frame classes when building a chart's binding model |
| `core/.../web/binding/service/VSChartInfoModelBuilder.java` | same, on the viewsheet-specific builder |
| `core/.../uql/viewsheet/internal/VSChartPaletteDefaults.java` | `defaultLinearFrame(VizContext)`, `hiddenLinearFrames(VizContext)` |
| `core/.../web/binding/VSChartBindingController.java` | **new** `GET /api/composer/chart/hiddenlinearframes` endpoint; shared `pickerContext(vsId, assemblyName, principal)` helper |
| `core/.../report/internal/graph/ChangeChartTypeProcessor.java` | `VizContext` on constructors; six seed sites |
| `core/.../report/internal/graph/ChangeChartDataProcessor.java` | context passed through |
| `core/.../report/composition/graph/GraphUtil.java` | `fixVisualFrame`/`fixVisualFrames` context; one seed site |
| `core/.../uql/viewsheet/internal/ChartVSAssemblyInfo.java` | pass `VizContext.of(this)` ×2 |
| `core/.../uql/viewsheet/ChartVSAssembly.java` | pass `VizContext.of(getVSAssemblyInfo())` into `GraphUtil.fixVisualFrames` |
| `core/.../web/binding/controller/ChangeChartTypeService.java` | pass the assembly's context ×2 |
| `core/.../web/binding/controller/{ChangeChartAestheticService,ChangeChartDataService,ChangeChartRefService,ConvertChartRefService}.java` | thread the assembly's context to the shared seed helper |
| `core/.../web/binding/handler/{ChartDndHandler,VSChartBindingHandler,VSChartDataHandler}.java` | thread the context through the drag/drop and data-change seed paths |
| `core/.../report/script/AbstractChartBindingScriptable.java`, `.../script/viewsheet/VSChartBindingScriptable.java` | thread the context through the scripting API's chart-binding mutators |
| `core/.../uql/viewsheet/graph/ChartDcProcessor.java` | pass the assembly's context ×2 |
| `core/.../web/composer/vs/dialog/DateComparisonDialogService.java` | pass the assembly's context |
| `core/.../web/composer/vs/dialog/ChartPropertyDialogService.java` | pass `VizContext.of(assemblyInfo)` into `GraphUtil.fixVisualFrames` |
| `core/.../report/internal/ChartElementDef.java` | pass `VizContext.LEGACY` ×2 |
| `core/.../web/vswizard/handler/VSWizardBindingHandler.java` | **new** `getTempChartContext(VSWizardData)`, reading the mark off the held temp-chart assembly |
| `core/.../web/vswizard/recommender/object/VSChartDefaultRecommendationFactory.java` | resolves and threads the context into the recommender |
| `core/.../web/vswizard/recommender/chart/ChartCombinationUtil.java` | threads the context through `createFilters`/`getChartInfos` into every `ChartTypeFilter` |
| `core/.../web/vswizard/recommender/chart/ChartTypeFilter.java` | context field and constructors |
| `core/.../web/vswizard/recommender/chart/{CirclePackingChartFilter,ContourMapChartFilter,ContourScatterChartFilter,MekkoChartFilter,ScatterChartFilter,WordCloudFilter}.java` | pass the context through to `super()` — the seven sites that were seeding Blues unconditionally from the wizard |
| `web/.../binding/editor/chart/aesthetic/linear-color-dropdown.component.ts` | three `getSrc()` cases |
| `web/projects/portal/src/assets/{Amber,Teal,Variance}.png` | **new** ×3 — 80×19 swatches |
| `web/.../binding/editor/chart/aesthetic/linear-color-pane.component.ts` | `vsId`/`assemblyName` inputs; `AmberColorModel`/`TealColorModel`/`VarianceColorModel` instance properties; `singleHueModels` gains Amber and Teal, `divergingModels` gains Variance; fetches `hiddenlinearframes` and filters the group arrays, keeping a hidden-but-selected frame visible |
| `web/.../binding/editor/chart/aesthetic/linear-color-pane.component.html` | Heat row conditional |
| `web/.../binding/editor/chart/aesthetic/color-field-mc.component.html` | pass `vsId`/`assemblyName` to the linear pane |
| `web/.../common/data/visual-frame-model.ts` | three models |
| tests | `ChartRampDerivation`/`ChartRampDerivationTest`/`HouseRampTest` (derivation, drift guard, wrapper round-trip), `SeededLinearFrameTest` (`defaultLinearFrame` and composer threading), `VSChartPaletteDefaultsTest` (`hiddenLinearFrames`), `WizardSeededLinearFrameTest` (**new** — the wizard recommender threading), extended `linear-color-pane.component.tl.spec.ts` |

Every path is inside the `community` submodule, so this ships as a community PR.
