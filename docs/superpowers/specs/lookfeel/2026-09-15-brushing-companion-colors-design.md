# Brushing companion colours — design

**Date:** 2026-09-15
**Status:** implemented, manual verification pending (§6)
**Branch:** `feature-chart-brushing-companion`, stacked on `feature-chart-companion-colors`
(PR #5275, open against `epic-74519`)
**Source:** `design_handoff_chart_palettes/ENGINE.md` §2, and the start-here handoff
`chart-brushing-companion-handoff.md` (2026-09-15, measured at `1a4117250`).

## What this is

The third slice of the chart palette redesign, after
[the re-tune](./2026-09-11-chart-palette-retune-design.md) and
[companion colours](./2026-09-14-chart-companion-colors-design.md). It spends the companion
mechanism on its second and third consumers.

Brushing today collapses every series to two global constants. Everything unselected goes one flat
grey, the selection goes one flat red, whatever the chart's palette. The moment you brush, you stop
being able to tell the series apart — which is the one thing brushing exists to help you do.

This slice makes the unselected marks fade to **each series' own companion**, and the selected marks
render **each series' own base colour**. Series identity survives brushing in both states, and red
leaves the marked-chart brushing path entirely.

## Corrections to the source

Recorded first, because two of them change the shape of the work.

**1. There is a fifth capture site and a third trap, in a different Maven module.** The handoff maps
four captures and two identity-comparison traps, all in `core`. The grep it instructs the reader to
run before starting finds another:

```
utils/inetsoft-xml-formats/.../excel/chart/ExcelChartHelper.java:3103
   private Color brushHLColor = BrushingColor.getHighlightColor();

utils/inetsoft-xml-formats/.../excel/chart/ExcelChartHelper.java:694
   if(sinfo.getDefaultDataPointInfo() == null && (!hasBrush ||
      hasBrush && !brushHLColor.equals(dinfo.getFillColor())))
```

Same pattern — "is this the highlighted mark?" asked by comparing against the global highlight — in
the Excel export path. The exported workbook reads VOs from the rendered `VGraph`, so it sees
whatever the canvas rendered and the guard misfires the moment colours vary. The companion design's
§8 entry, which says "~20 live sites across `GraphGenerator`, `MapGenerator`, `VGraphPair`,
`BrushingComparator`", should read five files, not four.

Every other line number in the handoff verified exactly at `1a4117250`.

**2. ENGINE §2 names the wrong key.** It says the two methods "need the series index and its frame".
The index is not available where it would be needed — `StaticColorFrame` is per-element, not per-row
— and a measure-coloured chart resolving through `LinearColorFrame` has no palette index at all.
The key is the **colour**. `ChartPropertyService.resolveCompanion` (`:407-421`) already works this
way: it walks the active palette for a slot whose colour matches, resolves the authored companion by
name and index when it finds one, and otherwise derives from the colour itself. Keying on colour
recovers the index when there is one and degrades gracefully when there is not.

**3. The brushing source path already resolves per row.** The handoff says "at none of them is the
colour read per mark". True of the colour, but not of the frame. `applyBrushing(ColorFrame, DataSet)`
builds an `HLColorFrame` whose `getColor(data, col, row)` calls `getHighlight(data, row)` and answers
the highlight colour or `def`. The per-row machinery exists; it answers with two constants. Only the
brushing *target* path (`adata != null`) is genuinely flat, and there the flatness is correct — each
of its two layers is wholly selected or wholly unselected.

**4. The handoff's commit ordering overstates the isolation from #5275.** It claims commits 1..n
touch nothing from that PR and only the final commit is exposed. Under the mechanism below, the
lifted resolver and the decorator frame both consume #5275's API. Only the trap conversion is
genuinely independent. The revised ordering is at the end of this document.

## Decisions

The four open questions the handoff left, with their answers and the reasoning.

| Question | Decision | Why |
|---|---|---|
| Seeded, or resolved at render? | **Resolved at render.** | The §1 rework's rule is that a value the user can see and edit in a dialog must be stored, while chrome may be resolved. Brushing colours appear in no dialog, are not persisted per series, and are author-reachable only as a CSS theme override. They are chrome by that exact test, so the §1 precedent argues *for* render-time resolution here rather than against it. |
| What does highlight become? | **The series' own base colour.** Red leaves the marked path. | The companion pair is a tuned perceptual distance between a base and its partner. Using base against companion is using the pair for the purpose it was tuned for, and it keeps series identity in *both* brush states rather than only the unselected one. A flat red against per-series companions would be N companions plus one unrelated hue, and `#FF5A35` (Modern slot 2) already sits near it. |
| Legacy and gate-off charts | **Unchanged, via `VizContext`.** | The track's standing rule: a chart follows its own `VizMark`, never the org gate. An unmarked chart keeps flat grey and flat red. |
| CSS override precedence | **Either property declared, and the whole chart falls back to today's flat behaviour.** | Under the decision above the two colours are a pair; honouring one and not the other yields combinations nobody designed. `checkPresent` is a clean signal because neither `.brush-dim-color` nor `.brush-highlight-color` is declared in any shipped CSS — it is false out of the box and true only when a customer wrote the rule. A themed deployment is therefore pixel-unchanged by this slice. |

One decision the handoff did not anticipate:

| Question | Decision | Why |
|---|---|---|
| How wide is v1? | **Cartesian marks only.** Maps keep flat grey and red. | `MapGenerator` composes four frames per layer and applies `getDarkColor()` to the brush colour for points over polygons — a transform with no per-series meaning yet, and a sub-design of its own. Deferring maps also defers trap 1 *correctly*: `VGraphPair:825`'s colour comparison is only wrong once map colours vary, so leaving maps flat leaves that comparison valid. |

## 1. The mechanism — a companion decorator frame

One new frame in `inetsoft.report.composition.graph`:

```java
CompanionBrushColorFrame extends ColorFrame
   HLColorFrame predicate   // nullable; null means the layer is uniform
   boolean      selected    // read only when predicate == null
   ColorFrame   base        // the chart's real colour frame
   boolean      dark        // from VizContext
```

```
isSelected(data, row):
   predicate != null ? predicate.getHighlight(data, row) != null
                     : selected

getColor(data, col, row):
   Color c = base.getColor(data, col, row)
   if c == null                -> null          (fall through)
   if isSelected(data, row)    -> c             (selected: base colour)
   otherwise                   -> companionOf(c)
```

`isSelected` is public and is the frame's second job: it is the explicit marker that replaces the
identity-by-colour comparisons in §4. A uniform layer answers from `selected` rather than from a
per-row predicate, which is what lets a *target* chart — two flat layers, no `HLColorFrame`
anywhere — still say which of its marks are brushed.

`companionOf` is the lifted `resolveCompanion`: walk the active palette for a slot whose colour
equals `c`, take `VSChartPaletteDefaults.companionColor(active, name, i, dark)` when found — where
`name` is `"Modern Dark"` under dark and `"Modern"` otherwise, so an authored `-soft` entry wins —
and otherwise wrap `c` in a one-slot `CategoricalColorFrame` and derive by rule.

Three properties fall out of keying on colour rather than index, and they are the reason for this
shape:

- **Colour-unbound charts work.** A chart with no colour binding resolves through a single colour;
  the one-slot fallback derives its companion. It dims to a soft version of itself.
- **Measure-coloured charts work.** A gradient-coloured chart has no palette index, but
  `getCompanionColor` is a pure OKLab colour-to-colour function, so its answer is still defined.
- **Nothing needs threading.** No call site gains an index parameter. `BrushingColor`'s two static
  methods keep their signatures and keep serving the legacy and CSS-override paths.

## 2. Where it attaches

Two paths, and they take the decorator differently.

**Brushing source** — `adata == null && isBrushingSource()`. One dataset, one layer, the
`HLColorFrame` deciding per row. `applyBrushing(ColorFrame color, DataSet data)` composes
`[hframe, color]` today, with `hframe` answering red or grey and never falling through. It becomes
`[CompanionBrushColorFrame(hframe, color, dark), color]` — selected rows answer `color`'s own
colour, unselected rows answer its companion. The trailing `color` frame is retained so a null from
the decorator still falls through, matching `CompositeColorFrame.getColor`'s first-non-null
contract.

**Brushing target** — `adata != null`. Two layers, each wholly one state, which is why flat frames
were right here:

| Layer | Today | Becomes |
|---|---|---|
| `acolor`, all data | `StaticColorFrame(brushDimColor)` | `CompanionBrushColorFrame(predicate=null, selected=false, palette, dark)` |
| brushed data | `StaticColorFrame(brushHLColor)` via `createBrushingTargetColorFrame(false, base)` | `CompanionBrushColorFrame(predicate=null, selected=true, palette, dark)` |

For colour alone the brushed layer would need no frame — "selected renders its base colour" means
"do not intervene", and a bare palette would render identically. It is wrapped anyway, as a
pass-through, because the wrapper is what carries `isSelected`. A target chart has no
`HLColorFrame`, so without it the Excel export's guard (§4, trap 3) would have nothing to ask and
would silently treat every mark as unbrushed.

`createBrushingTargetColorFrame(boolean isAll, ColorFrame base)` keeps its bug-53441 branch: when
the brushed data is empty and all data is showing, it returns the `selected=false` frame rather than
the `selected=true` one. The base is a parameter rather than an internal lookup: the lookup the
method used to do was not lifecycle-stable and could hand back a frame that had already been
wrapped, so each caller passes the frame it means.

**Brush source summary** (`GraphGenerator:4452`) sets `StaticColorFrame(brushDimColor)` on summary
elements. It wraps the summary's own frame (`cvisitor.getSummaryFrame(...)`) in the decorator when
one exists, and keeps flat grey when it does not.

## 3. The gate and the CSS fallback

One predicate, evaluated once per generator, deciding whether any of the above applies:

```
companionBrushing = vizContext.modern
                    && !CSSDictionary.getDictionary().checkPresent(".brush-dim-color")
                    && !CSSDictionary.getDictionary().checkPresent(".brush-highlight-color")
```

`vizContext` is already in scope: `GraphGenerator` holds `protected final VizContext vizContext`
(`:7571`), assigned from `VizContext.of(chart)` (`:223`) or `VizContext.LEGACY` (`:439`). Reading
the mark rather than the org gate is what keeps a marked chart in a gate-off org consistent, per the
factory's own contract.

When the predicate is false, every site above keeps its present code path exactly. Legacy charts,
report charts, and CSS-themed deployments are untouched.

The two `checkPresent` reads should be taken once and held, not re-read per site — `CSSDictionary`
memoises the selector lookup, but the pair is a single decision and wants a single evaluation.

## 4. The three traps — one predicate, no new flags

All three ask "is this visual a highlighted one?" by comparing its colour against the global
highlight. Under this design the highlight *is* the palette colour, so all three stop matching —
silently, with no exception.

The handoff proposes an explicit marker on the VO or geometry. That is not needed: the repo already
contains the idiom, at `GraphGenerator:1630`'s contour `PointSelector`:

```java
((CompositeColorFrame) cframe).getFrames(HLColorFrame.class).anyMatch(frame ->
   ((HLColorFrame) frame).getHighlight(data, tidx) != null)
```

`HLColorFrame.getHighlight(data, row)` is a public per-row predicate, and an `ElementGeometry`
carries everything needed to reach it — its element's colour frame, its visual model's dataset, and
its tuple index. One helper, `BrushedMarks.isBrushed(ElementGeometry)`, wraps both sources of the
answer: a `CompanionBrushColorFrame`'s `isSelected` where one is present, and a bare `HLColorFrame`
otherwise, which is the legacy and flat-fallback case.

| Trap | Site | Which source answers | v1 |
|---|---|---|---|
| 2 | `BrushingComparator:57,60` | `HLColorFrame` — the comparator is installed only on the source path, so one is always present | **converted, independently of the rest** |
| 3 | `ExcelChartHelper:694` | either, or neither — the export sees source *and* target charts, and a legacy target chart carries no frame to ask at all | **converted once the marker frame exists, with the colour comparison kept as the fallback** |
| 1 | `VGraphPair:825` | n/a | **left alone**, map colours do not vary in v1 |

This settles the handoff's "`BrushingComparator` given context, or demonstrably not needing it": it
needs no `VizContext` and no colour. It needs the brush predicate, which it can already reach — and
because `voComparator` is assigned only in the brushing-source branch, its answer never depends on
the marker frame. That is what keeps its conversion shippable on its own.

Trap 3's guard picks a default `DataPointInfo` for Excel to use on columns with no data, deliberately
avoiding a highlighted point. Converted to the predicate it keeps that meaning under any palette.
It cannot land before the marker frame, because on a modern target chart there is no `HLColorFrame`
to ask. A *legacy* target chart carries neither frame, so the guard tests `BrushedMarks.hasMarker`
first and falls back to the flat colour comparison when there is nothing to ask. That is why
`ExcelChartHelper` **keeps** its `brushHLColor` field: brushing colours are uniform on the legacy
path, so the comparison is still correct there and the field is the fallback's only input.

## 5. What this does not change

- **Maps.** `MapGenerator` is untouched. Deferred with a trigger below. One map-visible behaviour
  does change, and it is a latent fix rather than damage: `BrushingComparator` now asks the brush
  predicate, so on a map whose point layer sits over polygons it genuinely reorders brushed points
  to the front. The colour comparison it replaces never matched there, because the map path darkens
  the highlight colour before the comparison sees it.
- **The density contour.** `GraphGenerator:1630` sets `StaticColorFrame(brushHLColor)` on a
  `DensityForm`. `DensityFormVO` resolves it with `colors.getColor(i + 1)` — the value-keyed
  overload, on contour band index, not on a data row. A per-row decorator has nothing to attach to,
  so the contour keeps the flat highlight. Deferred with a trigger. Its *colour* is what is
  unchanged: the form's `PointSelector` did change, to `BrushedMarks.isBrushed`, because the
  colour-frame stream it walked finds nothing once the highlight frame is nested inside the
  companion, and without the change it selected no points at all.
- **`BrushingColor`.** Both static methods keep their signatures. They still serve legacy charts and
  the CSS-override fallback, so ENGINE's "signature change" framing does not apply.
- **Legends and tooltips.** Both read frames rather than VOs, which is why the decorator sits in the
  frame and not in a post-layout VO pass — a VO pass would make the legend disagree with the canvas,
  the same failure class as the §1 rework.

## 6. Testing

Matching the bar #5188 and #5275 were held to. The list below is what the design asked for; the
paragraph after it records what was actually built, which is not all of it.

**Unit, `core`:**

- `CompanionBrushColorFrame` — selected row answers the base colour; unselected answers the
  companion; null base falls through; null predicate dims the whole layer.
- Colour-unbound and measure-coloured charts resolve a companion through the one-slot fallback.
- The gate: an unmarked chart and a `VizContext.LEGACY` chart produce the present flat frames.
- The CSS fallback: `.brush-dim-color` alone, `.brush-highlight-color` alone, and both, each produce
  the present flat frames on a marked chart.
- Dark mode resolves against `Modern Dark-soft`, including the anchor's lift at slot 3
  (`#49447D` to `#8988AB`).
- The extracted brush predicate — selected and unselected rows, a frame with no `HLColorFrame`
  in its composite, and a non-composite frame.
- `BrushingComparator` sorts brushed marks to the front when the two marks carry palette colours
  rather than red, which is the case that fails today.

**Drift guard**, in the style of #5275's: assert the companion a brushed mark resolves equals the
authored `-soft` entry for that slot, so the derivation and the authored palette cannot diverge
silently.

**What was built.** Three classes: `CompanionBrushColorFrameTest` (17), `BrushedMarksTest` (11) and
`VSChartPaletteDefaultsCompanionOfTest` (5), plus the drift guard. Two items on the list above were
not: a unit test for the gate, and one for the CSS fallback. Both are decided by a boolean field on
the generator, and reaching it from a unit test means standing up a `GraphGenerator` — a full chart
info, descriptor and dataset. Their coverage is the manual pass below, which exercises the paths the
gate actually protects. A cheaper route would be to make the predicate a static package-private
function of `(VizContext, CSSDictionary)` and test it directly.

**Suites:** scoped palette/companion/brushing sweep, then a full `core` run before the PR — the full
run is what caught a regression in #5188 that eight green scoped runs missed. Community
`./mvnw clean install -DskipTests` across all 46 modules, which is also what compiles the
`inetsoft-xml-formats` change. The pre-existing `License.Builder.formLicensed` skew fails a
`-Pcommunity,enterprise` build and is unrelated; both #5188 and #5275 hit it.

`npm run test:portal` resolves to `em` and does not run the portal project. Nothing here touches the
frontend, so no Angular suite is expected to move.

**Manual, light and dark:**

- A bar and a line chart with several series, brushed — series distinguishable throughout.
- A colour-unbound chart, brushed — dims to a soft version of itself.
- A map, brushed — still flat grey and red. Its point layer over polygons now genuinely sorts
  brushed points to the front, where the old colour comparison never matched; confirm that reads as
  a fix rather than a surprise. Polygon z-order (trap 1) is unchanged.
- A tree or network chart with a node colour binding, brushed — source nodes take the companion of
  the target-bound colour, where the legacy path gave them a flat dim. This is the intended
  behaviour and the one place a relation chart's brushing visibly differs.
- A brushed line or point chart on a brushing **target** dashboard — marks keep the darkening that
  makes them stand out; they should not read lighter than the same chart unbrushed.
- A multi-aesthetic brushing target — the dimmed all-data layer companions from the per-measure
  colours, not from the chart-level frame. Be specific about this one: **Multiple Styles on, two
  measures each with its own colour binding, on a bar chart.** Multi-series by dimension does not
  exercise it at all — `isMultiAesthetic()` is `isMultiStyles()`, so a chart-level colour binding
  never reaches the path. And the chart type matters: line, point, radar, candle, stock and boxplot
  deep-clone the composite per element at `GraphGenerator:4675`, which would hide a fault that a bar
  chart exposes. Pass is each measure's dimmed bars receding to a companion of *its own* colour;
  failure is both measures sharing one, whichever was processed last.
- A brushed chart on an unmarked dashboard — flat grey and red.
- A deployment with `.brush-dim-color` set — pixel-identical to before.
- An Excel export of a brushed chart — default series colour on empty columns still taken from an
  unselected point.

## 7. Deferred, with triggers

| Item | Trigger |
|---|---|
| **Maps** | `MapGenerator:203-211,243-249` composes `GeoColorFrame` layers per polygon/point and applies `getDarkColor()` to the brush colour for points over polygons. Per-series brushing needs that transform to become per-mark, which is an unanswered sub-design. Trigger: cartesian companion brushing ships and maps read as inconsistent beside it. **Taking this up requires converting trap 1 (`VGraphPair:825`) in the same change** — it is correct only while map colours are uniform. |
| **Density contour** | `DensityFormVO:106` resolves by contour band index through `getColor(Object)`, so the per-row decorator does not apply. A marked contour chart keeps a red contour over base-coloured marks. Trigger: a contour chart is brushed on a modern dashboard and the mismatch is reported. The likely answer is seeding the form's `StaticColorFrame` from the chart's colour where the chart is single-coloured. |
| **ENGINE §3 palette overflow** | Manual verification found the trigger this slice was waiting for: a chart past slot 8 draws from the legacy tail, whose lightest entries companion to white. A light-end case in the derivation rule keeps every mark visible, but the tail still does not belong beside the head. Measurements and the constraints §3 must respect are in [the §3 trigger record](./engine-3-palette-overflow-trigger.md). |
| **Connector lines on a relation chart** | Brushing the topmost node also brushes the lines between it and its children, in legacy as well. Edges resolve through the element's own frame rather than the node frame, and this slice did not touch them. Observed, not changed. Trigger: a decision that edge brushing should follow the node it connects. |
| **`BrushingColor` retirement** | Once maps and the contour are converted, the two static methods serve only the legacy and CSS paths. Trigger: both deferred items land. |

## 8. Files touched

| File | Change |
|---|---|
| `graph/BrushedMarks.java` | new — `isBrushed(ElementGeometry)`, the extracted predicate |
| `graph/CompanionBrushColorFrame.java` | new — the decorator and the marker |
| `graph/GraphGenerator.java` | `applyBrushing` source and target paths, summary element, the gate predicate |
| `graph/BrushingComparator.java` | colour comparison to brush predicate |
| `excel/chart/ExcelChartHelper.java` | colour comparison to brush predicate, guarded by `hasMarker`; keeps its `brushHLColor` field as the legacy fallback |
| `internal/VSChartPaletteDefaults.java` | `resolveCompanion` lifted here as the shared resolver |
| `service/ChartPropertyService.java` | becomes a caller of the lifted resolver |
| `2026-09-14-chart-companion-colors-design.md` | §8: three consumers, not one; five files, not four |

## 9. Commit ordering

The split that de-risks the stack, corrected for what actually touches #5275:

| # | Commit | Exposed to #5275 |
|---|---|---|
| 1 | `BrushedMarks.isBrushed`; convert trap 2. No colour changes. | no — rebases cleanly |
| 2 | Lift `resolveCompanion` to `VSChartPaletteDefaults`; `ChartPropertyService` becomes a caller. Drift guard. | yes |
| 3 | `CompanionBrushColorFrame` with unit tests, unwired. | yes |
| 4 | Wire into `applyBrushing` behind the gate and the CSS fallback; convert trap 3; correct the companion design's §8. | yes |

Trap 3 sits in commit 4 rather than commit 1 for the reason in §4: on a brushing *target* chart its
answer comes from the marker frame, which does not exist until commit 3.

Commit 1 is worth landing first on its own merits: it removes a latent identity-by-colour comparison
whether or not the rest ships, and it is the only commit that rebases cleanly off #5275.

Commit 2 is what discharges the companion design's recorded one-consumer risk. That design cites
`applyDarkForeground` — a mechanism that shipped wired to a single surface, sat unused, and
resurfaced later as a defect — and notes companions have the same shape. After this slice the
mechanism has three consumers with their call sites enumerated.

Open the PR against `feature-chart-companion-colors` as a draft so GitHub shows this diff rather
than #5275's ~2,800 lines; it auto-retargets to `epic-74519` when #5275 merges. After that merge:

```bash
git fetch origin
git rebase --onto origin/epic-74519 origin/feature-chart-companion-colors \
  feature-chart-brushing-companion
```
