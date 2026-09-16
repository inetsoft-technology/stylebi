# Viewsheet binding — chart types, fields & intent

Read this before calling `create_viewsheet`, `change_chart_type`, `set_chart_format`, or
`set_chart_colors`. This is the **visual layer**: given a `wsId` (built per
`worksheet-construction.md`), you tell `create_viewsheet` *which fields* to bind (`fieldConfigs`) and
*what the user is trying to see* (`intentCategory`); StyleBI's recommender assigns the fields to chart
slots and renders.

You do **not** hand-build a chart binding. Supply `fieldConfigs` + `intentCategory` (+ an optional
`visualizationType` steer) and let the recommender place fields; use `explicitBindings` only to pin a
field the recommender misplaces.

- The **data layer** (tables, joins, columns, filters/WHERE, group/aggregate/HAVING, multi-step
  `create_worksheet_table`) is covered in **`worksheet-construction.md`** — build and validate the
  worksheet first; `create_viewsheet` needs the `wsId` it returns.
- Row-level / highlight **filters** on a rendered chart are covered in **`condition-model.md`**
  (`apply_filter`).

## Chart types

`create_viewsheet` takes an optional `visualizationType` (a steer — omit it to let StyleBI recommend),
and `change_chart_type` takes one. Valid values (StyleBI `ComponentType`):

`bar`, `3d_bar`, `area`, `point`, `step_area`, `interval`, `line`, `step_line`, `jump_line`, `pie`, `3d_pie`, `donut`, `radar`, `filled_radar`, `scatter_contour`, `stock`, `candle`, `boxplot`, `waterfall`, `pareto`, `treemap`, `sunburst`, `circle_packing`, `icicle`, `marimekko`, `gantt`, `funnel`, `tree`, `network`, `circular_network`, `contour_map`, `map`, `table`, `crosstab`, `image`, `gauge`, `text`.

**Cannot be switched via `change_chart_type`** (complex types — create them fresh instead): `stock`, `candle`, `boxplot`, `gantt`, `interval`, `scatter_contour`, `tree`, `network`, `circular_network`, `contour_map`, `map`.

### Point sub-types that have NO `visualizationType` of their own

`wordcloud` and `scattermatrix` are **not** valid `visualizationType`/`change_chart_type` values for
**either tool** — they are not in the `ComponentType` list above, and (unlike `heatmap`) neither
`create_viewsheet` nor `change_chart_type` has a bypass for them, so passing either is rejected
downstream. They are **sub-types of `point`**, produced by using `visualizationType: "point"` and
steering the binding into the shape StyleBI detects. Use `point` + `explicitBindings`:

- **Word cloud** (words sized by a measure): a dimension on the **`text`** aesthetic + a measure on the
  **`size`** aesthetic, and **nothing on `x`/`y`**. Example — products sized by sales:
  `visualizationType: "point"`, `fieldConfigs: [ {fieldType:"dimension", field:"product"},
  {fieldType:"measure", field:"sales", aggregateFormula:"Sum"} ]`,
  `explicitBindings: [ {role:"text", field:"product"}, {role:"size", field:"Sum(sales)"} ]`.
  (Its only highlightable field is that `text` dimension — highlight the words, e.g. `product CONTAINS "a"`,
  not the size measure.)
- **Scatter matrix (SPLOM)**: the **same set of ≥2 measures** on both `x` and `y` (no dimension on either axis).

Omitting `visualizationType` and describing the intent in words can also let the recommender return one of
these as a candidate, but the `point` + `explicitBindings` recipe above is the reliable way to force it.

> **Heat map is different — it *does* have a `visualizationType`.** For `create_viewsheet`, pass
> `visualizationType: "heatmap"` with **exactly two dimensions + one measure** (`planHeatmap` intercepts it
> and bypasses the recommender). See the **"Heatmap (2-categorical matrix)"** entry below for the verified
> recipe — do **not** route a heat map through the `point` + x/y/color recipe above, since the recommender
> never proposes a heatmap from that shape (it returns a crosstab).

## Fields (the `fieldConfigs` entries)

`fieldConfigs` is a list of **field** objects — the fields you want the chart to bind. When
`fieldConfigs` is non-empty, **only the named fields are bound**, and each `field` must exactly
match a visible worksheet column name (alias) — a name that matches nothing fails with a 400
listing the available columns. Include the fields that matter (especially measures needing an
aggregate, or dimensions needing date-grouping or ranking). An empty `fieldConfigs: []` lets
StyleBI infer everything from all visible columns. A field is either a dimension or a measure.

**Dimension** (categorical — the "by what"):
```json
{ "fieldType": "dimension", "field": "Region", "type": "string", "title": "Region",
  "dateGroupLevel": null, "timeSeries": null, "ranking": null }
```
- `dateGroupLevel` (optional) groups dates (e.g. by month/quarter). Valid values are the lowercase
  level names in the **Date Group Levels** table in `worksheet-construction.md` (the same canonical
  vocabulary used by worksheet `groupBy`) — e.g. `"month"`, `"quarter"`, `"month of year"`.
- `timeSeries` (optional, boolean) — **fills in missing time periods** for a date dimension. Set
  `true` to make the chart/crosstab complete gaps in the time axis so periods with no data still show
  (as zero/empty) instead of being skipped. This is the answer to *"show every month, including months
  with zero sales"*: build the plain grouped data in `validate_worksheet` (a single-table/flat
  group-by — no date-spine table to construct), then bind the month dimension with `"timeSeries": true`.
  Date-gap completion is a visualization feature; **do not** build a calendar/date-spine table in the
  worksheet for this.
- `ranking` (optional): `{ "optionValue": number, "rankingN": number, "rankingCol": string }` for top/bottom-N.
  Set it on the **dimension** being limited. `optionValue`: **9 = top-N**, **10 = bottom-N** (omit `ranking` for no limit).
  `rankingN` is the count (e.g. 10).
  `rankingCol` must be the **aggregated measure's ref name** exactly as
  it appears in the binding — i.e. `"<Aggregate>(<field>)"`, e.g. `"Sum(amount)"` (not the bare `"amount"`).
  Example — top 10 cities by total sales: dimension `city` with
  `"ranking": { "optionValue": 9, "rankingN": 10, "rankingCol": "Sum(amount)" }`, measure `amount` with `"aggregateFormula": "Sum"`.
- **Ranking is per-group, scoped by the dimension order (this gives you "top-N per group").** When more
  than one dimension is bound, the ranking on a dimension applies **within each group formed by the
  dimensions above it in the hierarchy**, not globally. So for the hierarchy `State → City`:
  - putting top-3 `ranking` on **`City`** → "the top 3 cities **within each State**" (State groups the
    data, City is ranked inside each group). This is how you express *"top 3 products per region"*: bind
    `region` then `product`, and set the top-N `ranking` on **`product`**.
  - putting top-3 `ranking` on **`State`** → ranks the States themselves (top 3 States by the measure),
    not cities within a state.
  Order the dimensions outer→inner and place the ranking on the **inner** dimension you want limited
  per group. No multi-step worksheet is needed for per-group top-N — it is a binding-layer concern.
  Do **not** reach for a SQL window function (`ROW_NUMBER() OVER (PARTITION BY …)`) for ordinary
  per-group top-N; the binding layer handles it natively.
  - ⚠ **Per-group top-N requires a chart that keeps both dimensions in ONE nested hierarchy — use a
    `crosstab` with both dimensions on `rows` (outer→inner), the ranking on the inner one.** A
    **bar / graph** chart does NOT preserve the nesting (the ranked dimension is reordered to the
    outer axis, or the two dims land on separate x/y axes), so the ranking silently collapses to a
    **global** top-N (the overall top-N, not per-group) with no error. Express per-group top-N like
    this: `visualizationType: "crosstab"`, `fieldConfigs: [ {fieldType:"dimension", field:"<outer>"},
    {fieldType:"dimension", field:"<inner>", ranking:{optionValue:9, rankingN:3, rankingCol:"Sum(<m>)"}},
    {fieldType:"measure", field:"<m>", aggregateFormula:"Sum"} ]`, `explicitBindings: [ {role:"rows",
    field:"<outer>"}, {role:"rows", field:"<inner>"} ]`. The plugin auto-steers to this when you omit
    `visualizationType`/`explicitBindings`, and warns if you force a bar — heed the warning.
- **Sorting (bar order).** A dimension's `order` controls bar/category order: `1`
  ascending / `2` descending **alphabetically**; `17` ascending / `18` descending
  **by aggregate value** (these require `sortByCol`, the aggregated measure's ref name,
  e.g. `"Sum(amount)"`); `8` **manual** — pair with `manualOrder` (see below). A top-N
  `ranking` (optionValue 9) now **auto-sorts** value-descending by `rankingCol`, and a
  bottom-N (10) auto-sorts value-ascending — so "top 25 suppliers by spend" renders
  descending without extra config. Pass an explicit `order` (+ `sortByCol` for
  value-sort) to override; an explicit `order` is never overridden by the ranking
  auto-sort. Value-sort is ignored for `timeSeries` date dimensions (those stay
  time-ordered).
- `manualOrder` (optional): `string[]` — the dimension values in the exact display order
  desired. Set `order: 8` alongside it to activate manual sort. When switching away from
  manual order, clear `manualOrder` and set the desired numeric `order`.

**Measure** (numeric — the "how much"):
```json
{ "fieldType": "measure", "field": "Sales", "type": "double", "title": "Sum of Sales",
  "aggregateFormula": "Sum", "secondaryField": null, "nOrP": null }
```
- `aggregateFormula` — how the measure is aggregated. Valid values:
  `none`, `Average`, `Count`, `DistinctCount`, `Max`, `Min`, `Sum`, `First`, `Last`, `Median`, `Mode`, `Correlation`, `Covariance`, `Variance`, `StandardDeviation`, `PopulationVariance`, `PopulationStandardDeviation`, `WeightedAverage`, `Product`, `Concat`, `NthLargest`, `NthSmallest`, `NthMostFrequent`, `PthPercentile`, `SumSQ`, `SumWT`.
- Some formulas take an operand: `secondaryField` (for `Correlation`, `Covariance`, `WeightedAverage`, `SumWT`) and `nOrP` (the N or P for `NthLargest` / `NthSmallest` / `NthMostFrequent` / `PthPercentile`).
- **Already-aggregated worksheet columns** (the worksheet did the group/aggregate) → bind with
  `aggregateFormula: "none"`; do not re-aggregate in the visualization. **Caveat:** the recommender
  often misclassifies a `none` numeric as a *dimension* — the chart then falls back to `table`/`point`
  with the field on an axis/shape and an empty measure slot. When that happens (or for a **rate /
  percentage** column), bind it with **`Average`** instead: the worksheet has one row per group, so
  `Average` returns the value unchanged *and* registers as a measure. **Never `Sum` a rate/percentage**
  — summing it is meaningless and labels the axis `Sum(...)`, which is wrong even when one-row-per-group
  makes the number coincidentally match.
- `calculateInfo` (optional) — adds a **trend / comparison calculation** to a measure. See the
  **Trend & comparison calculations** section below — this is the right layer for running totals,
  cumulative %, period-over-period (YoY/QoQ) change, moving averages, and percent-of-total. **Do not
  build a multi-step worksheet for these** — they are window-style calculations the visualization
  computes for you over the bound dimension(s).

Base field props (both kinds): `field` (column name), `type` (data type — string/integer/double/date/timeInstant), `title` (display), `format` (optional display pattern).

**`title` and `format` are applied at creation** (set on the bound field — the axis/legend/series label
and its number/date display format):
- `title` overrides the field's label wherever it appears (e.g. the y-axis caption for a measure, the
  x-axis/legend label for a dimension). Use it to rename `Sum(amount)` → `Total Sales`, `city` → `City`.
- `format` is a display pattern applied by the field's data **type**: a **DecimalFormat** pattern for
  numeric measures (e.g. `"#,##0"`, `"$#,##0.00"`, `"0.0%"`) and a **DateFormat** pattern for date
  dimensions (e.g. `"yyyy-MM"`, `"MMM d, yyyy"`). Patterns on string fields are ignored.
- These per-field props are part of the **binding** like aggregate/ranking. To change them on an
  existing chart, use **`update_binding`** (patch the `fieldConfigs`) — it re-binds in place over the
  same worksheet and preserves the saved id. Recreate with `create_viewsheet` only when the data layer
  itself changes. Use `get_current_chart_state` to read the current binding before patching it.
- **Chart-level format is in-place** via `set_chart_format`: axis **titles** (xAxisTitle/yAxisTitle),
  y-axis **scale** (yAxisMin/yAxisMax/yAxisIncrement/yAxisLogarithmic), and **legend** placement
  (none/top/right/bottom/left/in_place).
- **Colors are in-place** via `set_chart_colors`: `staticColor` (one color when there's no color
  dimension), `paletteName` (named palette, or a gradient name when a measure is on color),
  `colorList` (ordered hex list), and `categoryColors` (`{value: "#hex"}` overrides). The mode follows
  the chart's color binding; if a color can't apply to the current binding the result carries a `note`
  telling you to recreate with a field on the color aesthetic.

## Trend & comparison calculations (`calculateInfo` on a measure)

`calculateInfo` is a **window-style calculation computed by the visualization** over the measure's
bound dimension(s) — running total, cumulative %, period-over-period change, moving average, etc.
These are **not** worksheet operations: the worksheet stays a plain grouped/raw table, and you add the
calculation in `fieldConfigs` on the measure. **Never build a multi-step `create_worksheet_table`
chain for any of these** — there is no window-function primitive in the worksheet, and you don't need
one.

It is set on a **measure** field: `"calculateInfo": { "classType": "...", ... }`. Variants:

| `classType` | What it computes | Key fields |
|---|---|---|
| `PERCENT` | Percentage share of a reference total (percent-of-total / cumulative % when combined with `RUNNINGTOTAL`) | `level` (1 = of Grand Total, 2 = of Subtotal); `columnName` (a specific dimension for "percent of `<dim>`", else omit); `byRow`/`byColumn` for crosstab direction |
| `CHANGE` | Absolute or % change from a reference point — **this is the YoY / QoQ / period-over-period tool** | `from` (0=First, 1=Previous, 2=Next, 3=Last, **4=Previous Year**, 5=Previous Quarter, 6=Previous Week, 7=Previous Month, 8=Previous Range); `asPercent` (true = % change); `columnName` (dimension; null = inner dimension) |
| `VALUE` | The raw value at a reference point (e.g. last year's value alongside this year's) | `from` (same codes as CHANGE); `columnName` |
| `MOVING` | Sliding-window aggregate over preceding/following periods | `aggregate` (Average, Sum, …); `previous`, `next` (window size each side); `includeCurrentValue`; `nullIfNoEnoughValue`; `innerDim` |
| `RUNNINGTOTAL` | **Cumulative aggregation** (running total / running average), optionally resetting at a time boundary | `aggregate` (Sum, Average, …); `resetLevel` (-1=None/never reset, 0=Year, 1=Quarter, 2=Month, 3=Week, 4=Day, 5=Hour, 6=Minute); `breakBy` (dimension to reset on) |
| `COMPOUNDGROWTH` | Compound growth rate over a period | `aggregate`; `resetLevel` (same codes); `breakBy` |

**Worked mappings:**
- *"month-over-month sales, show YoY % change"* → measure `sales` with
  `{ "classType": "CHANGE", "from": 4, "asPercent": true }` (4 = Previous Year). The worksheet is just
  raw rows; bind the date dimension grouped by month. **Do not** self-join a date-shifted table in the
  worksheet.
- *"cumulative sales over time"* (running total) → measure `sales` with
  `{ "classType": "RUNNINGTOTAL", "aggregate": "Sum", "resetLevel": -1 }`.
- *"cumulative % of total"* → a running total **and** a percent-of-grand-total reading; bind the
  measure with `RUNNINGTOTAL` (Sum, no reset) and/or `PERCENT` `level: 1`. (For a Pareto chart, prefer
  `visualizationType: "pareto"`, which renders cumulative % directly.)
- *"3-period moving average"* → `{ "classType": "MOVING", "aggregate": "Average", "previous": 2, "next": 0, "includeCurrentValue": true }`.

## Intent category

`create_viewsheet` requires an `intentCategory` — it tells the recommender what the user is trying to
see, which shapes the slot assignment. One of:

`comparison` (compare a measure across categories), `trend` (a measure over time), `distribution`
(spread of values), `proportion` (part-to-whole), `relationship` (two measures correlated), `ranking`
(top/bottom-N), `geospatial` (on a map), `other`.

### Distribution intent: special cases

- **Distribution of a numeric measure split by a category** (e.g. "price distribution by category")
  now renders as a **faceted binned histogram** — one mini-histogram panel per category
  (`x = Range@<measure>` per panel, `y = Count`, panels laid out by the category). Bind it as
  `intentCategory: "distribution"` with exactly one numeric measure + one categorical dimension; the
  bin is auto-intervalled. (Previously this silently produced a `Sum(measure)`-by-category chart.)

## Pivoting (rows → side-by-side columns) is a crosstab binding

When the user wants one dimension's values laid out as separate columns — e.g. *"category sales for
channel A vs channel B, side by side"* — this is a **crosstab binding**, not worksheet pivoting (no
CASE-WHEN / conditional aggregation). Build the plain grouped data in the worksheet (`groupBy` category
+ channel, `Sum(sales)` — a flat `validate_worksheet` query), then pick a **`crosstab`** and bind
`channel` to **`cols`**, `category` to `rows`, `Sum(sales)` to `aggregates`. The crosstab spreads the
channel values across columns for you.

- **A date column grouped at two levels** (e.g. weekday × hour "activity heatmap") is expressible
  directly in `create_viewsheet`: bind the same date field twice in `fieldConfigs` as two dimensions
  with different `dateGroupLevel` (e.g. `"day of week"` and `"hour of day"`) plus a measure. It renders
  as a crosstab matrix. (You may also pre-group in the worksheet with two `groupBy` date parts — both
  work.)

- **Gantt timeline (task schedule):** to render a Gantt (one bar per task from its start to end date), request `visualizationType:"gantt"` with a task/label **dimension** in `fieldConfigs` plus the two date columns, and pin the dates via `explicitBindings` roles: `explicitBindings:[{role:"start",field:"date_start"},{role:"end",field:"date_finish"}]` (optional `{role:"milestone",field:"…"}`; optional `{role:"x",field:"…"}` to name the task label explicitly). This bypasses the recommender (which does not offer Gantt) via explicit binding, and the date fields are bound at raw day-level (no YEAR grouping). Without the `start`/`end` role pins the request falls through to a table.

- **Count of a category by itself:** "count of X by X" — a `Count`/`DistinctCount` measure on the *same* field as the grouping dimension (e.g. `fieldConfigs:[{fieldType:"dimension",field:"contract_type"},{fieldType:"measure",field:"contract_type",aggregateFormula:"Count"}]`) — now renders correctly. Previously the recommender collapsed the dimension and the same-field count (it keys fields by bare name) and dropped the count, leaving a chart with no proportions; it is now bound explicitly (dimension on color for donut/pie, on x otherwise; the count on y). Counting a *different* column (e.g. `Count(id)`) was never affected and still goes through the recommender.

- **Heatmap (2-categorical matrix):** to render a heatmap — a grid of two categorical dimensions colored by a measure (e.g. bug count by `status` × `priority`, or calls by weekday × hour) — request `visualizationType:"heatmap"` with **exactly two dimensions + one measure** in `fieldConfigs`. The first dimension goes on x, the second on y, and the measure on color. This bypasses the recommender (which delivers a crosstab for that shape) via explicit binding — StyleBI renders it as a point chart with the measure on color. With anything other than exactly 2 dimensions + 1 measure, the request falls through to the recommender.

- **Box plot (distribution across a category):** to render a box plot — the spread of a measure (min / lower-quartile / median / upper-quartile / max, plus outliers) shown as a box per category, e.g. deal `amount` by `sales_stage` — request `visualizationType:"boxplot"` with **exactly one dimension + one measure** in `fieldConfigs`. The dimension goes on x, the measure on y; StyleBI computes the box statistics per category. This bypasses the recommender (which does not offer a box plot). With anything other than exactly 1 dimension + 1 measure, the request falls through to the recommender. (The chart carries no deterministic facts block — a distribution has no single-figure total.)

- **Funnel / Pareto:** request `visualizationType:"funnel"` or `"pareto"` with **exactly one dimension + one measure** in `fieldConfigs`. Both put the dimension on x and the measure on y (funnel = stage progression; pareto = ranked bars + cumulative %). Both bypass the recommender (which substitutes a bar for that shape) via explicit binding. With anything other than exactly 1 dimension + 1 measure, the request falls through to the recommender.

- **Hierarchy charts (treemap / sunburst / circle_packing / icicle):** request `visualizationType:"treemap"`, `"sunburst"`, `"circle_packing"`, or `"icicle"` with **one or more dimensions + one measure** in `fieldConfigs`. The dimensions become nested hierarchy levels (rings / nested tiles) in order, sized by the measure — one dimension is a single level, two or more give a true multi-level hierarchy (e.g. `status → contract_type`). This bypasses the recommender via explicit binding. RAW apply_binding note: the hierarchy dimensions go on the **`t`** slot (a `group` array is NOT the hierarchy input for these types — StyleBI's treemap-family binder ignores `group`). With no dimension or not exactly one measure, the request falls through to the recommender.

- **Radar (spider) chart:** request `visualizationType:"radar"` (or `"filled_radar"`) with **three or more measures** plus up to two optional dimensions in `fieldConfigs`. Each measure becomes a spoke/axis; the first dimension becomes a polygon per value (color) and the second goes on shape. This bypasses the recommender via explicit binding (measures on y, nothing on x). With fewer than 3 measures or more than 2 dimensions, the request falls through to the recommender. (The chart carries no deterministic facts block — a radar has no single headline figure; use it to compare multi-measure profiles across categories.)

## Readability & correctness the recommender can't judge (apply these yourself)

StyleBI's recommender picks a *feasible* chart from the field shapes, but it can't see what the data
*means* or, fully, how much of it there is. Three judgments are yours — make them **before**
`create_viewsheet`, and use what it returns to correct course:

1. **High-cardinality dimensions → top-N or roll-up.** A dimension with hundreds of distinct values
   (e.g. 600 cities, every customer) produces an unreadable chart, and the recommender barely penalizes
   it. When a binding dimension is high-cardinality, add a top-N `ranking` on it (see Fields above) or
   roll up to a coarser level — e.g. "top 20 cities by Sum(amount)" instead of all 600.
   - **Signal:** `create_viewsheet` returns `fieldCardinalities` (distinct count per bound non-date
     dimension). Anything in the dozens-plus on an axis usually wants top-N. If you suspect high
     cardinality up front, you can also check with `lookup_column_values`. (Cardinality is only sampled
     for ≤9 fields, so an absent entry isn't a guarantee it's low.)
2. **Match the aggregate to what the measure *means*.** The recommender defaults measures to `Sum`,
   which is wrong for some columns:
   - **Ratios / percentages / rates / averages-of-averages** → don't `Sum`; use `Average`. Summing a
     percentage is meaningless. This holds **even when the worksheet pre-aggregated to one row per group**
     (so `Sum` and `Average` return the same number): `Sum(rate)` still mislabels the axis — bind `Average`.
   - **IDs / codes / years-as-numbers** → these are dimensions, not measures; if used as a measure use
     `Count` / `DistinctCount`, never `Sum`.
   - **Already-aggregated worksheet columns** → bind with `aggregateFormula: "none"`; but if the
     recommender then treats the column as a dimension (chart falls back to `table`/`point`), switch to
     `Average` — one row per group means the value is unchanged and it now types as a measure.
3. **Chart type vs. the measure's value range.** Don't request `pie`, `donut`, or stacked
   (`bar_stack` / `area_stack`) for a measure that can be **negative** (e.g. profit, net change) — part-
   to-whole/stacking is nonsensical with negatives; use a plain `bar` or `line`. Use the column meaning
   from `search_schema`/annotations to decide.

You also know hierarchies the recommender doesn't infer (e.g. `Country → State → City`,
`Year → Quarter → Month`). For part-to-whole *within a hierarchy*, bind the dimensions in order and
steer `treemap` / `sunburst` (intent `proportion`).

## Letting the recommender bind (default)

For almost every chart, supply `fieldConfigs` + `intentCategory` (+ an optional `visualizationType`
steer) and let StyleBI's recommender assign the fields to chart slots. You do not specify axes, color,
size, etc. — the recommender does, and `create_viewsheet` returns the rendered result.

## Pinning a field to a slot (`explicitBindings`, optional)

When the recommender puts a field on the wrong slot, pass `explicitBindings` to pin specific fields:

```json
"explicitBindings": [{ "role": "x", "field": "Region" }, { "role": "y", "field": "Sales" }]
```

Valid `role` values: `x`, `y`, `color`, `shape`, `size`, `text`, `group`, `rows`, `cols`,
`aggregates`, `details`. Pin only the fields you need to place; leave the rest for the recommender.

**Faceting / small multiples.** There is no dedicated facet/trellis/panel `visualizationType` — asking
for one (e.g. `visualizationType:"facet"`) is rejected as infeasible, since it names no real chart
type. To split a **graph** chart (point, bar, line, …) into small multiples by a (low-cardinality)
dimension, keep the graph type and pin that dimension with role **`cols`** (side-by-side panels) or
**`rows`** (stacked panels) — e.g. `[{ "role": "cols", "field": "region" }, { "role": "x", "field": "freight_burden" }, { "role": "y", "field": "avg_review" }]`.
The backend auto-nests the facet dimension onto the matching axis shelf (the form StyleBI's graph
recommender actually accepts), so you do **not** need to hand-nest it (though pinning the same
dimension twice to `x`/`y` yourself works identically — `cols`/`rows` just says what you mean).
Leaving the two dimensions unpinned and letting the recommender infer does **not** reliably produce
this: with no explicit pins it commonly resolves a `color` overlay (a single stacked/grouped panel)
instead of true small multiples — pin `cols`/`rows` explicitly when that's what's wanted. Keep the
facet dimension's cardinality small (roll up first), and avoid combining a facet with *both* `color`
and `size` measures — that is often over-constrained and rejected. (`rows`/`cols` are also
first-class for **crosstab** pivoting, where they are passed through unchanged.)

**Pins are constraints, not hints.** The recommender only selects candidates that honor every pin:

- A pin that defeats a readability guard (e.g. a 100-value dimension pinned to `color`) is
  **honored with a warning** in the result's `binding.notes` — prefer rolling the dimension up
  (e.g. top-8 + "Other" computed column) before pinning it to an aesthetic slot.
- A structurally impossible pin (unknown slot, field not bound, a measure on `shape`) fails with
  a 400 whose body names the pin and the reason — fix the request; do not retry verbatim.
- `visualizationType` stays a *preference*: if the requested type has no pin-satisfying candidate,
  a different type is selected and reported via `selectionNote` with
  `selectionNoteKind: "substitution"`. A `selectionNote` carrying `selectionNoteKind: "delivery"`
  means the opposite — the requested type WAS rendered, just built explicitly because the recommender
  offers no candidate for that shape. Always branch on the kind, never on the note's wording.

After every chart call, verify `binding.slots` (the resolved placement) matches what you pinned.

## apply_binding (recommender bypass)

`explicitBindings` are **constraints the recommender must honor** — it still generates feasible
candidates, filters to those honoring your pins, may **substitute the chart type**, fills the unpinned
slots, applies readability guards, and **dedups measures by field** (two aggregates of the *same*
field collapse to one). When that gating is itself the problem — the recommender returns
**"unsatisfiable"**, renders your scatter as color/size instead of x/y, drops a same-field measure, or
swaps the type — use **`apply_binding`**: it sends a fully-specified binding straight to StyleBI's
`/viewsheet/create`, **bypassing the recommender entirely** (no candidates, no feasibility filtering,
no dedup, type applied verbatim). Prefer `create_viewsheet` for everything else — `apply_binding` has
**no safety net** (a nonsensical binding renders empty/wrong; that's on you).

**Inputs:** `visualizationType` + a full `config`:

```json
{
  "visualizationType": "point",
  "config": {
    "data": { "source": "<wsId from validate_worksheet>" },
    "bindingInfo": {
      "bindingType": "chart",
      "x":     [ { "field": "order_count", "type": "double", "aggregateFormula": "none" } ],
      "y":     [ { "field": "avg_value",   "type": "double", "aggregateFormula": "none" } ],
      "color": { "field": "name", "type": "string" }
    }
  }
}
```

- **`bindingType`** (REQUIRED) selects the binding shape — `"chart"` (graph charts) · `"table"` ·
  `"crosstab"` · `"output"` (gauge/text) · `"image"`. **Omitting it → a 400** (the polymorphic binding
  can't deserialize). This is the most common mistake.
- **`data.source`** = the worksheet's full `wsId` (from `validate_worksheet` / `create_worksheet_table`).
- **chart `bindingInfo` slots:** `x`/`y`/`group` are **arrays**; `color`/`shape`/`size`/`text`/`path`
  are **single** field objects; `high`/`low`/`close` for stock/candle; `t` (array) for crosstab dims.
- **field object** = `{ field, type, title?, format? }` + for **measures** `aggregateFormula`
  (`Sum`/`Average`/`Count`/`none`/… — use `none` when the worksheet is already aggregated),
  `secondaryField?`, `nOrP?`, `calculateInfo?`; for **dimensions** `dateGroupLevel?`, `ranking?`.
- Optional `runtimeId` (re-bind an existing runtime), `viewsheetIdentifier` (preserve a saved id),
  `threadId` (defaults to the active thread so the live viewer reflects it).

Chart-layer analogue of the worksheet `sql query table` escape hatch — reach for it only when the
declarative recommender path genuinely can't express the binding.

## Render = the dry-run

There is no separate binding dry-run: `create_viewsheet` builds and renders in one call, and returns
`hasData` plus the sampled rows so you can see immediately whether the chart came out usefully. (The
worksheet itself is validated separately by `validate_worksheet` — see `worksheet-construction.md`.)

## Worked example — Sum(Sales) by Region

```json
{
  "wsId": "<from validate_worksheet>",
  "intentCategory": "comparison",
  "fieldConfigs": [
    { "fieldType": "dimension", "field": "Region", "type": "string" },
    { "fieldType": "measure", "field": "Sales", "type": "double", "aggregateFormula": "Sum" }
  ]
}
```
