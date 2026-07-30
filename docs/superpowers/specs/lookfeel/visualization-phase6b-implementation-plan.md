# StyleBI Visualization — Phase 6B (Modern Corner Rounding Defaults) — Implementation Plan

## Scope

Phase 6B makes **corner rounding a default of modern-visualization mode** rather than something every
author must opt into per chart. Two surfaces:

1. **Bar marks** — `PlotDescriptor.barCornerRadius` seeded to `0.3` for newly created charts, so bars
   render with a rounded value end out of the box.
2. **Assembly cards** — `VSFormat.roundCorner` seeded to `12` px on the DEFAULT tier for newly created
   data/selection assemblies, so objects read as rounded cards.

Both surfaces use **one mechanism** — a design-time seed under the modern gate, plus a gate-aware read
so turning the gate off reverts objects created while it was on (D1). Saved sheets are never touched,
and a user-set or `format.css` value always wins.

It is numbered `6B` because it extends `VSObjectChromeDefaults` — the resolver
[Phase 6A](visualization-phase6a-implementation-plan.md) introduced — with a geometry default beside
its existing color defaults, and reuses 6A's `viewsheet.modernObjectChrome` toggle. Numbering it `6B`
avoids renumbering Phases 7–10, which the other phase plans and
[visualization-design-spec.md](visualization-design-spec.md) cross-reference.

Unlike Phases 3/5/7 this phase has **no browser-CSS half**. Both values are server-owned: the bar
radius is baked into the graph by `BarVO` / serialized to the canvas via `ChartRegion`, and the card
radius resolves from a server `VSCompositeFormat` that both the live model and the export painters
read. A `--inet-viz-*` token cannot drive either one and would produce a live/export mismatch.

## Rendering boundary: both values are server-owned and export-visible

**Bar radius.** `GraphGenerator` reads `PlotDescriptor.getBarCornerRadius()` and pushes it onto the
graph element (`:3652`, `:3697`, `:3799`, `:3833`, `:3848`). From there it reaches two independent
renderers: `BarVO.getPath0()` builds the rounded `GeneralPath` for Java2D (export/PDF/PNG), and
`GraphBuilder.createChartRegion()` attaches `cornerRadius` + `barDirection` to `ChartRegion` for the
browser canvas. Both originate from the one descriptor value, so seeding the descriptor covers live
view and export together.

**Card radius.** `VSCompositeFormat.getRoundCorner()` (`:310`) is a single tier resolver
(USER → CSS → DEFAULT) that every consumer funnels through:

| Consumer | Site | Surface |
|---|---|---|
| Live client model | `VSFormatModel:52`, `:171` | all DOM assemblies (`[style.border-radius.px]`) |
| Export borders | `VSObject.drawBorders:331` | all image-rendered assemblies |
| Export backgrounds | `ExportUtil.drawBackground` via `VSCalendar:576`, `VSCompound:433`, `VSFloatable:194`, `VSGroupContainer:66` | calendar, compound, floatable, group container |
| Gauge image | `DefaultVSGauge:92` | gauge |
| Tab painter | `VSTab:96`, `:185`, `:281` | tab |
| HTML export | `HTMLCoordinateHelper:158` | HTML |
| SVG export | `SVGVSExporter:514` | SVG |
| Composer format pane | `FormatPainterService:332` (via `getRoundCornerValue()`) | format editor display |

Because that resolver is the one funnel, the gate-aware read is **two one-line edits** rather than a
dozen call-site changes — the same export-consistency guarantee Phases 5, 6 and 6A rely on.

## Grounding (verified against current code, 2026-07-28; re-confirm line numbers before editing)

### What already exists (this phase builds no rounding — only defaults)

Corner rounding is fully implemented on both surfaces. Nothing in this phase adds a renderer.

| Capability | Where | State |
|---|---|---|
| Bar corner radius, open-end-only + all-corners | `PlotDescriptor.barCornerRadius` / `barRoundAllCorners`, `IntervalElement`, `BarVO`, `ChartRegion.cornerRadius`/`barDirection`, `chart-tool.ts` `drawRoundedBar()` | shipped, default **off** (`DEFAULT_BAR_CORNER_RADIUS = 0`, `:1955`) |
| Bar radius UI | `chart-plot-options-pane.component.html:266-275`, `number-stepper` `min 0 / max 0.5 / step 0.05` | shipped |
| Tree node radius | `PlotDescriptor.nodeCornerRadius`, `GraphGenerator:1338` | shipped, **already defaults to `0.3` for new charts** (`:1959`) |
| Assembly card radius | `VSFormat.roundCornerValue` (a `DynamicValue2`, px), resolved at `VSCompositeFormat:310` | shipped, default **`0`** |
| Card radius DOM rendering | `[style.border-radius.px]` in `vs-chart`, `vs-table`, `vs-crosstab`, `vs-calctable`, `vs-selection`, `vs-selection-container`, `vs-calendar`, `vs-gauge`, `vs-text`, `vs-image`, `vs-combo-box`, `vs-check-box`, `vs-radio-button`, `vs-range-slider`, `vs-group-container`, `vs-input-label-wrapper` | shipped |
| Card radius seeding point | `VSAssemblyInfo.setDefaultFormat():1193` `objfmt.setRoundCornerValue(borderRadius)` | shipped, `borderRadius = 0` except the table CSS branch (`:1185-1187`) |
| 12 px as an established radius | `VSAnnotationService:54` `setRoundCornerValue(12)` | shipped |

### The house pattern for "new objects only"

Three sibling fields already solve the new-vs-saved problem the same way — **field initializer holds
the new-object value; `parseAttributes()` falls back to the legacy value when the XML attribute is
absent** — with no gate and no marker:

- `PlotDescriptor.nodeCornerRadius = 0.3` (`:1959-1960`), parse fallback `0` (`:1544`)
- `PlotDescriptor.smoothLines = false` (`:1951`) — inverse direction, same mechanism
- `ChartVSAssemblyInfo.setDefaultFormat():92` `getChartDescriptor().getLegendsDescriptor().setRoundCorners(true)`,
  commented *"Enable round corners by default for newly created charts. Existing charts loaded from XML
  default to false for backward compatibility."*

`ChartVSAssemblyInfo.setDefaultFormat()` is therefore the established seam for chart-descriptor
new-object defaults, and it already sits beside a gated one — `:89`
`getFormat().getDefaultFormat().setBackgroundValue(VSObjectChromeDefaults.cardBackgroundCss())`.

### The creation seam

`VSEventUtil.createVSAssembly()` calls `assembly.initDefaultFormat()` (`:2095`) immediately before
`vs.addAssembly(...)`.

The guarantee this phase depends on is **not** that `initDefaultFormat()` has a single caller — it has
many, including `ComposerObjectService` (twice), `FormatPainterService`, `GroupingService`,
`VSTableService`, `ComposerAdhocFilterService`, `ComposerRangeSliderService`,
`ComposerVSSelectionListService`, `VSLayoutService` and `LayoutOptionDialogService`. The guarantee is
that **no deserialization path calls it**: `ChartVSAssemblyInfo.parseContents()` and its constructor do
not, and `PlotDescriptor.parseAttributes()` yields `modernCornerSeed = false` for XML lacking the
attribute. So a saved viewsheet can never acquire a seed on load.

Every other caller is a creation or deliberate reset-to-defaults path — e.g.
`ComposerObjectService:766-769` fires when a selection assembly is dragged out of a selection container,
which intentionally resets that assembly. Such a reset re-seeding under the gate is correct behavior,
not leakage.

### The gated resolver pattern to mirror

`VSObjectChromeDefaults` (added in Phase 6A) documents the contract this phase extends:

> Applied as DESIGN-TIME defaults: the gated seed is written to the assembly's default format at
> creation (`VSAssemblyInfo.setDefaultFormat` and `ViewsheetVSAssemblyInfo.setDefaultFormat`), so a new
> object created under the gate carries the modern default (visible in the format editor, effective in
> the viewer and export). A user format or a `format.css` class still overrides it via the normal
> USER > CSS > DEFAULT tier precedence.

Its gate is `VSObjectChromeDefaults.isModern()` = `VSDensityDefaults.isModern()`
(org-scoped `viewsheet.modernVisualization`) **and** not-explicitly-false
`viewsheet.modernObjectChrome`.

## Decisions

### D1 — Design-time seed with a gate-aware read, not a pure display-time resolver → **DECIDED**

Applies to **both surfaces**. Phases 3/5/6 resolve at display time, so flipping the gate off is
byte-identical. This phase instead **seeds at creation** and makes the read gate-aware, which yields:

| | Existing objects | Objects created under the gate |
|---|---|---|
| Gate ON | unchanged (square) | rounded |
| Gate OFF | unchanged (square) | revert to square |
| User set a value | honored | honored, in both gate states |

Rationale: rounding is an authoring default ("new charts should look modern"), not a re-theming of
existing content — reflowing every saved dashboard's silhouette is a larger change than intended. The
gate-aware read is what keeps gate-off honest, so the gate remains a true escape hatch rather than a
one-way door.

**Both surfaces share one gate: `VSObjectChromeDefaults.isModern()`**, the Phase 6A gate. The bar radius
is a mark geometry rather than object chrome, so `VSChartChromeDefaults.isModern()`
(`viewsheet.modernChartChrome`) is the other candidate — but that toggle exists to govern in-graph chrome
*color*, and splitting the two would let an org end up with rounded cards and square bars, or the
reverse, from a single-word config change. Rounding turns on and off as one unit.

Only the *storage* of the seed marker differs between the two surfaces (D2 vs D3), because one layer has
a tier system and the other does not. The observable behavior is identical.

### D2 — Card radius needs no new field; the tier system supplies the gate-aware read → **DECIDED**

Seed onto the DEFAULT tier and gate-strip **only that tier** at `VSCompositeFormat`. USER tier (format
pane) and CSS tier (`format.css`, read live from `CSSDictionary` in `VSCSSFormat:385`) are untouched,
so both survive the gate flipping in either direction. No new serialized field, and crucially no
change to `VSFormat`'s custom binary `writeData()` (`:1145`, `:1202`) — a wire-format change would
affect cluster/hash paths.

Strip on **exact equality with the seed constant** (`12`), not on "any DEFAULT-tier value", so the
legacy table CSS branch at `:1185-1187` keeps working. See D6 for the residual edge case.

### D3 — Bar radius needs one new serialized flag to carry the gate marker → **DECIDED**

`PlotDescriptor` has no tier system, so D2's trick is unavailable, and `setBarCornerRadius()` clamps to
`[0, 0.5]` (`:1299`) — a negative sentinel cannot round-trip, because `parseAttributes():1541` calls that
clamping setter. One boolean, `modernCornerSeed`, therefore carries what the DEFAULT tier carries for the
card radius: *this value came from the gate, not from a user.*

The field default stays `0`, so `DEFAULT_BAR_CORNER_RADIUS` is unchanged and the value arrives only via
the seed in change 5.

One flag rather than two: `barCornerRadiusVisible` and `nodeCornerRadiusVisible` are mutually exclusive
by chart type (`GraphTypes.isBar/isInterval/isPareto/isWaterfall/isGantt` vs
`ctype == GraphTypes.CHART_TREE`, `ChartPlotOptionsPaneModel:121-134`), so one flag cannot cause one
field's edit to un-seed the other in practice. `nodeCornerRadius` is out of scope regardless (D4), so the
flag governs `barCornerRadius` only.

Rejected alternative — the ungated house pattern (`DEFAULT_BAR_CORNER_RADIUS = 0.3`, no flag, matching
`nodeCornerRadius` / `smoothLines` / `LegendsDescriptor.roundCorners`): it would make the whole bar half a
one-line change, but it drops the gate. New charts would round in every org, and an org that opted out of
modern mode could not opt out of rounded bars. Keeping the gate is worth one XML attribute. See D8 for the
local-consistency cost this accepts.

### D4 — `nodeCornerRadius` stays as-is, ungated and out of scope → **DECIDED**

It already defaults to `0.3` for new charts and `0` for saved charts (`:1958-1960`, `:1544`) — exactly
the behavior this phase wants — but **ungated**. Bringing it under the gate would make new tree charts
in a non-modern org render square, a regression of shipped behavior for no benefit. Leave it.

### D5 — Seeded assembly allowlist → **DECIDED**

**Seeded (12 px):** Chart; Table, Crosstab, CalcTable, EmbeddedTable; SelectionList, SelectionTree,
CurrentSelection.

**Calendar was intended but proved unreachable** (found during implementation, 2026-07-29).
`CalendarVSAssemblyInfo.initDefaultFormat()` (`:88-92`) overrides the base class and never calls
`setDefaultFormat()` — it clones a `static FormatInfo normalDefault` whose object format already
hardcodes `setRoundCornerValue(10)` (`:1420`), so the seam this phase seeds at is never executed for
Calendar. It already renders as a rounded card at 10 px in every org, and the D2 strip keys on exact
equality with `12`, so its `10` survives untouched in both gate states — nothing regresses. Routing it
through the seed was rejected: mutating the cloned `FormatInfo` breaks the
`getFormatInfo().equals(normalDefault)` check at `:1047` that drives an existing reset-to-default path.
Accepted outcome: 12 px cards alongside one 10 px calendar.

**Not seeded (stay square):** Gauge, Text, Image and all other outputs; ComboBox, CheckBox,
RadioButton, Slider, Spinner, TextInput, Submit/Button and all other inputs; RangeSlider
(`TimeSliderVSAssemblyInfo`); Tab (`VSTab` has a bespoke corner painter); GroupContainer (a frame
*behind* other objects — rounding it double-rounds); shapes (`ShapeVSAssemblyInfo`: Line, Rectangle,
Oval — radius is a user-facing shape property owned by `RectanglePropertyDialogService:178`);
annotations (`BaseAnnotationVSAssemblyInfo` already sets its own 12 px at `VSAnnotationService:54`);
PageBreak; embedded Viewsheet (`ViewsheetVSAssemblyInfo`).

Implemented as an **explicit positive list**, not `instanceof SelectionVSAssemblyInfo` — the latter
would silently capture `TimeSliderVSAssemblyInfo` (range slider), which descends from
`SelectionVSAssemblyInfo` via `MaxModeSelectionVSAssemblyInfo` and is excluded.

`this instanceof TableDataVSAssemblyInfo` covers all four table types (Crosstab arrives via
`CrossBaseVSAssemblyInfo`). A base-class `instanceof` check on subclasses follows existing precedent
at `VSAssemblyInfo:1167`.

### D6 — Accepted edge case: a `TableStyle` CSS radius of exactly 12 px under gate-off → **ACCEPTED**

The DEFAULT-tier strip in D2 keys on equality with `12`. The table branch at `:1185-1187` writes a
`format.css` `TableStyle[region=Table] { border-radius }` value onto the same DEFAULT tier. A customer
whose stylesheet specifies exactly `12px`, on a table, with the gate off, would see it stripped to `0`.

Narrow and cosmetic. Not worth a marker field or a tier migration. Note it in the phase's release
notes. (The assembly's *own* CSS type is read live through the CSS tier at `VSCSSFormat:385`, which
wins at `VSCompositeFormat:313` before the DEFAULT tier is consulted, so ordinary `format.css`
rounding is unaffected — only this one cross-selector legacy copy is exposed.)

### D7 — `barRoundAllCorners` stays `false`; the per-type overrides are already correct → **DECIDED**

Audit of every chart type that honors `barCornerRadius`, and what each does with all-corners today:

| Chart type | Radius applied at | `roundAllCorners` today | Checkbox shown? | Should default change? |
|---|---|---|---|---|
| Bar (standard: plain, grouped, stacked, horizontal, negative) | `GraphGenerator:3848` | **user setting**, default `false` | yes | **no** — baseline is anchored |
| Pareto (bar half; the line half is unaffected) | `:3652-3653` | **user setting**, default `false` | yes | **no** — baseline is anchored |
| Waterfall (`bar` + `sumBar`) | `:3697-3701` | **forced `true`** | no | already `true` |
| Gantt | `:3799-3801` | **forced `true`** | no | already `true` |
| Interval | `:3833-3835` | **forced `true`** | no | already `true` |
| 3D bar | — | n/a | no | excluded (`Bar3DVO` skipped; `!is3DBar` in visibility) |
| Funnel | — | n/a | no | excluded (`!isFunnel` in visibility) |
| Tree (nodes, not bars) | `:1338` via `nodeCornerRadius` | n/a | n/a | out of scope (D4) |

**The types you asked about are already handled.** Waterfall, gantt and interval each hardcode
`setRoundAllCorners(true)` in `GraphGenerator` with a comment giving the reason, and
`ChartPlotOptionsPaneModel:126-129` correspondingly hides the checkbox for them
(`barRoundAllCornersVisible` is limited to `isBar || isPareto`). So the only types with a user-facing
choice are standard bar and pareto, and those are the two that should stay `false`.

The rule the code already encodes — worth stating explicitly since it is the test for any future type:

> **Round an end iff it is not anchored to a fixed reference.** A standard bar and a pareto bar sit on
> the zero baseline, so their base corners must stay square or the bar visually detaches from the axis.
> Waterfall intermediate bars float on prior totals, gantt bars span a start/end pair, and interval bars
> have two value endpoints — none has a zero anchor, so both ends are open and both round.

Two consequences worth recording:

- **Stacked bars do not need a special case.** `BarVO.applyStackRounding()` (`:1530-1577`) clips each
  segment against the *full-bar* rounded silhouette using `isStackOutermost()` / `isStackInnermost()`,
  so the arc continues across segment boundaries instead of rounding each segment. With
  `roundAllCorners = false` only the outer end of the whole stack rounds — correct. With `true` the
  inner end rounds too (`:1570-1573`), which would lift the stack off the axis — reinforcing `false` as
  the default for stacked bars specifically.
- **Negative bars are already correct** without an all-corners override: the open end is chosen by
  `openDir`, which flips with sign.

For the record, `false` also matches Chart.js `borderSkipped: 'start'`, ApexCharts
`borderRadiusApplication: "end"`, ECharts `borderRadius: [4,4,0,0]` and Highcharts `where: "stack"`.

**No code change from this decision** — it documents that the existing per-type behavior is right and
records the rule for future chart types.

### D8 — Two marker-storage idioms inside `PlotDescriptor`; accepted → **DECIDED**

The two surfaces behave identically (D1) but store the gate marker differently, and `PlotDescriptor` ends
up holding two *styles* of new-chart default:

| | Bar radius (D3) | Card radius (D2) |
|---|---|---|
| Behavior | seed under gate, revert on gate-off | seed under gate, revert on gate-off |
| Gate | `VSObjectChromeDefaults.isModern()` | `VSObjectChromeDefaults.isModern()` |
| Marker storage | new `modernCornerSeed` boolean | the existing DEFAULT tier |
| New serialized state | one XML attribute | none |

The accepted cost is **local inconsistency inside `PlotDescriptor`**: after this phase it holds three
ungated new-chart defaults (`nodeCornerRadius`, `smoothLines`, `LegendsDescriptor.roundCorners` — all
"field initializer + parse fallback") and one gated one with a marker flag. A reader of that class will
see two idioms for what looks like the same job.

Accepted because the alternative is worse in the direction that matters: dropping the gate to match the
siblings would round bars in orgs that explicitly opted out of modern visualization, and there is no way
to gate a plain field initializer without some marker. The divergence is documented at the field itself
(change 4) so the next reader does not "fix" it by deleting the flag.

Should the ungated siblings ever be brought under the gate, they can adopt this same flag rather than
each inventing one — `modernCornerSeed` is named for the concept, not for `barCornerRadius`.

## Changes

### 1. `VSObjectChromeDefaults` — add the card-radius default and its resolver

`core/src/main/java/inetsoft/uql/viewsheet/internal/VSObjectChromeDefaults.java`

```java
/** Object-card corner radius default, in pixels. Matches the annotation-rectangle radius. */
public static int cardCornerRadius() {
   return CARD_CORNER_RADIUS;
}

/**
 * Gate-strip a DEFAULT-tier corner radius: when the value is our seed and the gate is off, the
 * object reverts to square. Keyed on exact equality with the seed so a format.css TableStyle radius
 * written to the same tier is preserved. USER and CSS tier values never reach this method.
 */
public static int resolveSeededCorner(int radius) {
   return radius == CARD_CORNER_RADIUS && !isModern() ? 0 : radius;
}

private static final int CARD_CORNER_RADIUS = 12;
```

### 2. `VSAssemblyInfo` — seed the card radius at creation

`core/src/main/java/inetsoft/uql/viewsheet/internal/VSAssemblyInfo.java`

Replace `int borderRadius = 0;` (`:1166`) with a gated seed. It must stay **above** the `if(border)`
block so the table CSS branch at `:1185-1187` still overwrites it:

```java
int borderRadius = VSObjectChromeDefaults.isModern() && isCornerSeedTarget()
   ? VSObjectChromeDefaults.cardCornerRadius() : 0;
```

Add the allowlist predicate (D5) near `getObjCSSType()`:

```java
/**
 * Whether this assembly type takes the modern card-corner seed. Data and selection surfaces read as
 * cards; outputs, inputs, tabs, containers, shapes and annotations do not. Explicit list rather than
 * a base-class check so TimeSliderVSAssemblyInfo, which extends SelectionVSAssemblyInfo, stays out.
 */
private boolean isCornerSeedTarget() {
   return this instanceof TableDataVSAssemblyInfo    // table, crosstab, calc table, embedded table
      || this instanceof ChartVSAssemblyInfo
      || this instanceof SelectionListVSAssemblyInfo
      || this instanceof SelectionTreeVSAssemblyInfo
      || this instanceof CurrentSelectionVSAssemblyInfo
      || this instanceof CalendarVSAssemblyInfo;
}
```

### 3. `VSCompositeFormat` — gate-aware read on the DEFAULT tier only

`core/src/main/java/inetsoft/uql/viewsheet/VSCompositeFormat.java`

`getRoundCorner()` (`:310-314`) — wrap only the `deffmt` branch:

```java
: VSObjectChromeDefaults.resolveSeededCorner(deffmt.getRoundCorner());
```

`getRoundCornerValue()` (`:321-324`) — same substitution on its `deffmt` branch, so the composer
format pane (`FormatPainterService:332`) shows what is actually rendered.

Leave `isRoundCornerDefined()` / `isRoundCornerValueDefined()` untouched: the seed is a value default,
not a definedness assertion, and changing those would alter format-painter and copy-format behavior.

### 4. `PlotDescriptor` — add the gate marker and the gate-aware getter

`core/src/main/java/inetsoft/uql/viewsheet/graph/PlotDescriptor.java`

`DEFAULT_BAR_CORNER_RADIUS` stays `0` (`:1955`) — the value arrives only via the gated seed in change 5.

**Field** (beside `:1955-1957`). Comment the divergence from the sibling fields two lines below, per D8:
```java
// Gate marker for barCornerRadius: true means the radius was seeded by modern mode rather than set by
// a user, so the gate may take it away again. Deliberately unlike the ungated nodeCornerRadius /
// smoothLines defaults below — do not collapse this to a plain field default (see phase 6B D3/D8).
private boolean modernCornerSeed = false;
```

**Accessors** — mirror the existing runtime-vs-design split (`getRoundCorner()` vs
`getRoundCornerValue()`), so every current reader picks up the gate with no edits:
```java
/** Effective bar corner radius: a gate-seeded value collapses to 0 when the gate is off. */
public double getBarCornerRadius() {
   return modernCornerSeed && !VSObjectChromeDefaults.isModern() ? 0 : barCornerRadius;
}

/** Raw stored radius, for serialization and content comparison. */
public double getBarCornerRadiusValue() {
   return barCornerRadius;
}

public boolean isModernCornerSeed() {
   return modernCornerSeed;
}

public void setModernCornerSeed(boolean modernCornerSeed) {
   this.modernCornerSeed = modernCornerSeed;
}
```

**`writeAttributes()`** (`:1700`) — write the raw value plus the flag:
```java
writer.print(" barCornerRadius=\"" + barCornerRadius + "\" ");
writer.print(" modernCornerSeed=\"" + modernCornerSeed + "\" ");
```

**`parseAttributes()`** (`:1540-1542`) — an absent flag yields `false`, so every chart saved before this
phase keeps its existing behavior. The existing `0.0` fallback for the radius itself is unchanged:
```java
modernCornerSeed = "true".equals(Tool.getAttribute(node, "modernCornerSeed"));
```

**`equalsContent()`** (`:1858`) — compare the raw value plus the flag:
```java
barCornerRadius == desc.barCornerRadius &&
modernCornerSeed == desc.modernCornerSeed &&
```

`clone()` needs no change (primitive, copied by `super.clone()`).

### 5. `ChartVSAssemblyInfo` — seed the bar radius at creation

`core/src/main/java/inetsoft/uql/viewsheet/internal/ChartVSAssemblyInfo.java`

In `setDefaultFormat()`, directly beside the existing legend round-corners line (`:92`):

```java
if(VSObjectChromeDefaults.isModern()) {
   PlotDescriptor plotDesc = getChartDescriptor().getPlotDescriptor();
   plotDesc.setBarCornerRadius(0.3);
   plotDesc.setModernCornerSeed(true);
}
```

`GraphGenerator` needs no edit: `:3652`, `:3697`, `:3799`, `:3833` and `:3848` already call
`getBarCornerRadius()`, which is now gate-aware. The existing zero-out of special-purpose element types
still wins where it applies, and the per-type `roundAllCorners` overrides audited in D7 are unchanged.

### 6. `ChartPlotOptionsPaneModel` — do not silently materialize the seed

`core/src/main/java/inetsoft/web/graph/model/dialog/ChartPlotOptionsPaneModel.java`

The constructor (`:119-120`) sends `null` when the radius is `0`, so a seeded chart sends `0.3` and the
stepper shows it. `updateChartPlotOptionsPaneModel()` (`:220`) would then write `0.3` straight back on OK
and clear the seed even though the user changed nothing. Guard on actual change:

```java
double incoming = barCornerRadius != null ? barCornerRadius : 0;

if(incoming != plotDesc.getBarCornerRadius()) {
   plotDesc.setBarCornerRadius(incoming);
   plotDesc.setModernCornerSeed(false);
}
```

Once the user does edit the value it becomes user-authored and stops tracking the gate — intended, and the
same rule the card radius gets from the USER tier.

Note the pre-existing date-comparison wrinkle at `ChartAdvancedPaneModel:161-177`: design-time `checkType`
does not see DC's runtime bar conversion, so that class re-applies `barRoundAllCorners` afterward. Verify
the new guard interacts correctly with a DC-converted chart.

### 7. `AbstractVSExporter` — withdrawn

An edit to `writeChartBackgroundShape()` carrying the chart card radius onto its synthetic rectangle
was attempted and has been withdrawn: every concrete exporter overrides that method with an empty
body, so the line never ran and was dead code. No exporter change is needed — the chart card already
follows the gate in export because `VSChart` extends `VSFloatable`, whose `paint()` passes
`format.getRoundCorner()` straight to `ExportUtil.drawBackground`/`drawBorders`.

### 8. Swatches / spec documentation

Record `12 px` as the modern card radius and `0.3` as the modern bar radius in
[visualization-palette-swatches.html](visualization-palette-swatches.html) and the geometry section of
[visualization-design-spec.md](visualization-design-spec.md), so they sit alongside the Phase 6A
chrome neutrals as named design values rather than magic numbers.

## Export coverage (corrected again 2026-07-29 after the final review)

Two earlier drafts of this section were wrong. The accurate position:

**The chart card already rounded in export before this phase changed anything.** `VSChart` extends
`VSFloatable`, whose `paint()` calls `drawBackground` → `ExportUtil.drawBackground(..., format.getRoundCorner())`
(`VSFloatable.java:194`) and `drawBorders` → `ExportUtil.drawBorders(..., format.getRoundCorner())`
(`VSObject.java:329`). Both read the gate-aware resolver, so the card follows the gate in every
rasterizing exporter with no exporter change at all.

**No exporter change was needed, and none shipped.** An earlier draft added a line to
`AbstractVSExporter.writeChartBackgroundShape()` to carry the radius onto a synthetic rectangle. That
line was dead code and has been removed: every concrete exporter overrides that method with an empty
body — `PDFVSExporter:1181`, `SVGVSExporter:761` (inherited by `PNGVSExporter`), `HTMLVSExporter:810`,
`PoiExcelVSExporter:1860`, `PPTVSExporter:1246` — and the only non-overriding subclass, `CSVVSExporter`,
emits no visuals at all.

**The real limitation is table and crosstab cards.** They are written as native Excel cells and
cell-by-cell PDF drawing by the per-type table helpers, so a rounded outer frame has nowhere to be
expressed and exports square. Inherent to the output formats, not a defect to fix here.

## Validation

### Saved-sheet parity (both gate states)

- Every existing sheet renders identically to pre-phase in viewer, composer, and all export formats,
  with the gate on **and** off. Bars: `parseAttributes` yields `0`. Cards: DEFAULT-tier radius is `0`.
- Excluded types (Gauge, Text, Image, all inputs, RangeSlider, Tab, GroupContainer, shapes,
  annotations, PageBreak, embedded VS) are unchanged in both gate states.

### Card radius — gate behavior

- Gate on, new table / crosstab / calc table / embedded table / selection list / selection tree /
  current selection / calendar / chart: 12 px card radius.
- Gate off, newly created assemblies: square.
- An assembly created while the gate was **on** renders square once the gate is turned **off**, and
  rounds again when it is turned back on (change 3's DEFAULT-tier strip).

### Bar radius — gate behavior

- Gate on, new chart: bars round at the value end at `0.3`.
- Gate off, new chart: bars square (`modernCornerSeed` never set).
- A chart created while the gate was **on** renders square once the gate is turned **off**, and rounds
  again when it is turned back on (change 4's gate-aware getter).
- A chart **saved before this phase** loads square in both gate states — `parseAttributes` yields
  `modernCornerSeed = false` and a radius of `0`.
- 3D bar and funnel remain square (existing `GraphGenerator` zero-out).
- Waterfall, gantt and interval round all corners; standard bar and pareto round the value end only —
  per-type behavior unchanged from today (D7).
- Stacked bar: the arc spans the whole stack silhouette, not each segment
  (`BarVO.applyStackRounding()`), and the stack base stays square.
- Negative bars round the end away from zero.
- Tree charts still round their nodes, unchanged (D4).

### Override precedence

- A radius typed into the format pane (USER tier) is honored in both gate states.
- A `format.css` radius on the assembly's own CSS type is honored in both gate states.
- A bar radius typed into Plot Options is honored in both gate states, including `0` to switch rounding
  off entirely — editing clears `modernCornerSeed`, so the value stops tracking the gate.
- Opening Plot Options on a seeded chart and pressing OK **without editing** leaves
  `modernCornerSeed = true` (change 6 guard), so the chart still reverts on gate-off.
- The "Round all corners" checkbox still round-trips for standard bar and pareto, and stays hidden for
  waterfall / gantt / interval.

### Round-trip and export

- Save → reload → re-render: no drift in either gate state.
- Chart and calendar agree between viewer and PDF / PNG / SVG / HTML export.
- Table and crosstab export square in Excel and PDF — expected, documented above.
- Date-comparison chart that converts to bars at runtime: seed and guard behave correctly against
  `ChartAdvancedPaneModel:161-177`.

### Regression surface

- `equalsContent()` on `PlotDescriptor` — a seeded new chart must not compare equal to a saved unseeded
  one, and must not spuriously dirty an unchanged sheet.
- Format painter and copy-format across a seeded and an unseeded assembly
  (`FormatPainterService:332`, `:1336`, `FormatInfo:334`).
- `VSFormat.writeData()` (`:1145`) is untouched by design — confirm no binary/hash drift.
- Chart script API (`ChartProcessor:248-255`) reads through the gate-aware getter — confirm
  `chart.barCornerRadius` reports the effective value and remains settable.

## Deferred / follow-ups

- **Outputs and inputs** (Gauge, Text, Image, combo box, checkbox, radio button, slider, button) keep
  square corners. Rounding them belongs with Phase 7's KPI and embedded-control work, where their
  chrome is already being addressed.
- **RangeSlider, Tab, GroupContainer** — each needs its own geometry reasoning before rounding; not a
  seed decision.
- **Native table rounding in Excel/PDF** — out of reach of the export formats.
- **Bringing the ungated sibling defaults under the gate** — `nodeCornerRadius`, `smoothLines` and
  `LegendsDescriptor.roundCorners` remain ungated (D4/D8). If that is ever wanted, they can reuse
  `modernCornerSeed` rather than each inventing a marker.
- **All-corners defaults** — no change (D7). The per-type overrides in `GraphGenerator` are already
  correct; the anchoring rule recorded in D7 is the test for any new bar-like chart type.

## Branching (per CLAUDE.md)

All changes land in `community/`, so this is a **community-only PR**. Branch off `main` in the
submodule; no enterprise-side change is required.

## Related

- [visualization-implementation-roadmap.md](visualization-implementation-roadmap.md) — Phase 6B
- [visualization-phase6a-implementation-plan.md](visualization-phase6a-implementation-plan.md) —
  `VSObjectChromeDefaults` / `VSTitleChromeDefaults`, the resolver this phase extends
- [visualization-phase6-implementation-plan.md](visualization-phase6-implementation-plan.md) —
  `VSChartChromeDefaults`, in-graph chart chrome
- [visualization-phase5-implementation-plan.md](visualization-phase5-implementation-plan.md) —
  `VSTableStructureDefaults`, the gated-resolver precedent
- [visualization-phase7-implementation-plan.md](visualization-phase7-implementation-plan.md) — KPI and
  embedded controls, which inherit the deferred output/input rounding
- [visualization-design-spec.md](visualization-design-spec.md) — rendering and theming architecture
