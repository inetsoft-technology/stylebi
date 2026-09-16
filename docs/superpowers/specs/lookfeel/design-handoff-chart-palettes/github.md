repo: inetsoft-technology/stylebi
branch: epic-74519
path: core/src/main/java/inetsoft

## Last sync
date: 2026-08-09T01:55:40Z

### Updated in this project
- Read `LineElement`: it has no fill of its own, so filled line and radar are both `AreaElement` — the phase-3 fill property covers all three chart types.
- Read `TreemapVO`, `TreemapGeometry` and `TreemapVisualModel`: containment map types fill only leaves, so treemap and circle packing carry depth by nested border weight, not colour.
- Removed the treemap comparison from 3k; the hierarchy shading rule applies to sunburst and icicle only.
- Read `ColorPalettes` and resolved the last open question: `getPalette()` is a plain map lookup that returns null silently, so every retired palette name must be aliased.
- Added 3j — alias table for the nine retired names, all implementable as CSS with no code or migration script.
- Read `AreaElement` and specced the four companion engine changes: one new frame accessor, plus call-site work ranked by real cost.
- Found area fill has no colour of its own (`HINT_ALPHA 0.8` over the line colour), making it the most expensive case rather than the cheapest.
- Read `GDefaults` z-order and redrew the target band faithfully: fill at index 10 (under the marks), boundary lines at 120 (over them), default line colour `#AFAFAD`.
- Verified the four companion use cases against the engine: `TargetForm` bands, `ConfidenceIntervalStrategy`, and `BrushingColor` all exist.
- Corrected two claims — target bands already take a `CategoricalColorFrame`, and brushing already resolves a dim *color* (one global grey), not an opacity pass.
- Read `ColorPalettes.java` and confirmed palette discovery is data-driven from CSS — adding a named palette needs no code change.
- Added 3i: how a deployment authors a palette, the loader's three footguns, and the companion gap.
- Corrected the earlier claim that generated overflow slots are not themeable — declaring `[index=9]` makes the slot real.
- Recomputed the gradient interpolation demo, whose endpoint had been swapped by the palette propagation without its steps being re-derived.

## Screen map
| Screen | Repo files |
| --- | --- |
| SBI Color and Type Pairings.dc.html — 3a–3e (palette set) | core/src/main/resources/inetsoft/util/css/defaults.css, core/src/main/java/inetsoft/uql/viewsheet/graph/aesthetic/ColorPalettes.java, web/projects/portal/src/app/binding/editor/chart/palette-dialog.component.\* |
| SBI Color and Type Pairings.dc.html — 3f (overflow) | core/src/main/java/inetsoft/graph/aesthetic/CategoricalColorFrame.java |
| SBI Color and Type Pairings.dc.html — 3g (ramps) | core/src/main/java/inetsoft/graph/aesthetic/LinearColorFrame.java, GradientColorFrame.java, HeatColorFrame.java, BluesColorFrame.java |
| SBI Color and Type Pairings.dc.html — 3b (companion cases) | core/src/main/java/inetsoft/graph/guide/form/TargetForm.java, core/src/main/java/inetsoft/graph/guide/form/ConfidenceIntervalStrategy.java, core/src/main/java/inetsoft/uql/viewsheet/graph/aesthetic/BrushingColor.java, core/src/main/java/inetsoft/graph/internal/GDefaults.java, core/src/main/java/inetsoft/graph/element/AreaElement.java, core/src/main/java/inetsoft/uql/viewsheet/graph/aesthetic/ColorPalettes.java |
| SBI Color and Type Pairings.dc.html — 3h (authoring) | core/src/main/java/inetsoft/uql/viewsheet/graph/aesthetic/ColorPalettes.java, core/src/main/java/inetsoft/util/css/CSSDictionary.java |

| SBI Color and Type Pairings.dc.html — 3k (multi-level) | core/src/main/java/inetsoft/graph/visual/TreemapVO.java, core/src/main/java/inetsoft/graph/geometry/TreemapGeometry.java, core/src/main/java/inetsoft/graph/internal/TreemapVisualModel.java |

## Sync history
- 2026-08-08T04:18:23Z — read the continuous color-frame family to answer measure-to-color binding.
- 2026-08-08T02:26:18Z — read `CategoricalColorFrame` to scope the palette-overflow change.
- 2026-08-07T19:50:59Z — read the 11 shipped palettes from defaults.css and the Select Palette dialog.
- 2026-08-07T19:35:15Z — tracked branch switched from main to epic-74519.
- 2026-08-07T19:27:11Z — initial read of portal/EM theme variables.
