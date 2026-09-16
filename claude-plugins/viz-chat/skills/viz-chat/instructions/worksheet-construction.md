# Worksheet, fields & chart intent

Read this before calling `validate_worksheet`, `create_viewsheet`, or `change_chart_type`.

In this plugin you do **not** hand-build a chart binding. You build a worksheet (the data), then tell
`create_viewsheet` *which fields* to bind (`fieldConfigs`) and *what the user is trying to see*
(`intentCategory`); StyleBI's recommender assigns the fields to chart slots and renders. This file
covers the worksheet model, the chart-type vocabulary, the field objects you put in `fieldConfigs`,
and the optional `explicitBindings` escape hatch.

## Worksheet vs viewsheet

- A **worksheet** is the data layer: the tables/joins/columns/filters a chart draws from. Construct a
  worksheet model from `search_schema` + `get_table_details` output and confirm it with
  `validate_worksheet`, which returns a `wsId`.
- A **viewsheet** is the visual layer: the chart built on a worksheet. `create_viewsheet` takes the
  `wsId` plus your `intentCategory` + `fieldConfigs` and returns a `runtimeId` (the live chart).

Always `validate_worksheet` first — `create_viewsheet` needs the `wsId` it returns.

## The worksheet model

The object you pass as `validate_worksheet`'s `worksheet` argument is forwarded to StyleBI and built
into a real worksheet there. It is **not** just a list of columns — it is a full query model. Every
part below is processed. Build only the parts you need; omit the rest (leave the array absent or empty).

```json
{
  "name": "sales_by_city",
  "worksheetId": null,
  "fields":             [ /* QueryField[]        — the columns to select  */ ],
  "joinPaths":          [ /* JoinPath[]          — table-to-table joins   */ ],
  "tableSetOperations": [ /* TableSetOperation[] — union / intersect / except */ ],
  "filters":            [ /* Condition[]         — row filters (WHERE)    */ ],
  "groupBy":            [ /* GroupByField[]      — GROUP BY dimensions    */ ],
  "aggregates":         [ /* AggregateField[]    — aggregate measures     */ ],
  "having":             [ /* HavingCondition[]   — filter on aggregates   */ ],
  "orderBy":            [ /* OrderByInfo[]       — sort                   */ ]
}
```

- `name` — the worksheet's table name. `worksheetId` — set it to an existing wsId to **merge into /
  extend** that worksheet instead of creating a new one (normally `null`).
- If `joinPaths` is empty you get a single-table worksheet (just column selection + filters/etc. on
  `fields[0]`'s table). With joins, the columns come from the joined result.

### Tables & sources (used everywhere a `table` appears)

Every field/join/group references a `table`:

```json
{ "name": "payment", "source": { "type": "DATABASE", "path": "<datasource>", "schema": "public" } }
```

- `source.type`: **`"DATABASE"`** for a physical DB table (the common case — verified), or
  **`"WORKSHEET"`** to draw from an existing saved worksheet (then `path` = that worksheet's path).
- `source.path` is the datasource/database name from `get_table_details`; `schema` (and optional
  `catalog`) qualify the table. Use the exact names returned by `get_table_details` / `search_schema`.

### Columns — `fields: QueryField[]`

```json
{ "fieldName": "amount", "alias": "amount", "table": { /* TableInfo */ },
  "type": "double", "expression": null, "description": null }
```

- `fieldName` + `table` pick a physical column. `alias` renames it in the result (use it to give
  joined/computed columns clean names). `type` is the data type.
  use `fieldConfigs` in `create_viewsheet` to control which columns are bound to the chart.
- **Computed / derived column:** set `expression` to a formula string and give the column a name
  via **`fieldName`** (falls back to `alias`; setting both to the same name is fine). A name is
  required — `validate_worksheet` rejects an expression field with neither. Rules:
  - Reference other worksheet columns as **`field['<column>']`**, and make sure each referenced
    column is selected in `fields` — `field[...]` refs only resolve against selected columns.
  - The expression is **merged verbatim into the generated SQL** (with `field[...]` refs replaced
    by column refs), so it must be valid for the target database. E.g. string concatenation on
    Postgres: `"concat(field['first_name'], ' ', field['last_name'])"` — NOT `+`, which fails with
    `operator does not exist: character varying + unknown`.
  - If the generated SQL fails at render time, `create_viewsheet` fails with
    `"Worksheet query failed — a computed-column expression or filter is invalid for the data
    source."` — fix the expression's SQL dialect and recreate. (On pre-2026-06 StyleBI builds this
    failure was silent instead: rows like `"<column>1"` / `999.99` are fabricated sample data.)

### Joins — `joinPaths: JoinPath[]`

```json
{ "leftTable":  { /* TableInfo for payment  */ }, "leftKey":  "customer_id",
  "rightTable": { /* TableInfo for customer */ }, "rightKey": "customer_id",
  "joinType": "inner", "joinOperator": "=" }
```

- `joinType`: `inner` · `left` · `right` · `full` · `cross`.
- `joinOperator`: `=` · `>` · `<` · `>=` · `<=` · `<>` (almost always `=`).
- For multi-table joins, create a single `RelationalJoinTable` containing all related source tables
  in `baseTables`. Represent the join graph by listing one `JoinPath` per edge (A→B, B→C, C→D).
  Each path names both its tables explicitly, so order in the array is not significant.
- A chain of `JoinPath`s describes relationships within a single `RelationalJoinTable`;
  it does **not** imply creating a separate worksheet join table for each hop.
- Use the smallest number of `RelationalJoinTable`s necessary to produce the required analytical grain.
  If all required fields can be reached through a single join graph, prefer one join table containing all source tables.
- Split joins into multiple worksheet tables only when a single join would introduce incorrect cardinality (such as M:N multiplication),
  incompatible aggregation grains, or other semantic changes that would alter the meaning of the result.
- **Compound join (multi-column key):** to join the same two tables on more than one column (e.g.
  `A.customer_id = B.customer_id AND A.order_date = B.max_date`), list **two separate `JoinPath`
  entries** — one for each column pair. Both conditions are ANDed. This is the correct pattern when
  joining a detail table to an aggregate table where the aggregate key includes multiple columns (e.g.
  joining raw orders to a per-customer max-date aggregate on both customer and date). Using only one
  column in this case produces a cross-product-like inflation (every order row joins to its customer's
  aggregate row regardless of date).
- Join keys need not be listed in `fields` — keys missing from the selection are auto-added
  automatically. Use `fieldConfigs` in `create_viewsheet` to name exactly the fields the chart
  should bind, so join keys never appear as junk measures.

### Filters — `filters: Condition[]` (row-level WHERE)

```json
{ "field": "amount", "operation": "GREATER_THAN", "value": "100",
  "conditionOperator": "AND", "conditionLevel": 0, "negated": false }
```

- `field` is the column (its `alias`/name as it appears in the worksheet).
- `operation`: `EQUAL_TO` · `GREATER_THAN` · `LESS_THAN` · `BETWEEN` · `ONE_OF` · `LIKE` · `STARTING_WITH` · `CONTAINS` · `NULL` · `DATE_IN`.
- `equal` (bool, default false): for `GREATER_THAN` / `LESS_THAN` only — set `true` for inclusive comparisons (≥ / ≤).
- `value` is a string, or a string array for `ONE_OF` / `BETWEEN` (`["2023-01-01","2023-12-31"]`). `NULL` takes no value.
- `conditionOperator` (`AND` / `OR`) joins this condition to the next; `conditionLevel` (int, default 0)
  nests/groups conditions (deeper level = parenthesized subgroup); `negated` inverts the single
  condition. Use worksheet `filters` when a non-highlight HAVING condition forces all conditions into
  the worksheet (see **Where to put conditions** above), or for static data-scoping on a reusable
  worksheet. Otherwise prefer `apply_filter` on the visualization for row-level filters.
- **NOT IN works** via `operation: "ONE_OF"` + `negated: true`. (Historically this hit a StyleBI
  one-shot `/ws/generate` SQL-generation bug that silently returned placeholder data — `"XXXXXXXX"`,
  `999.99` — instead of erroring. Fixed: any single-table worksheet carrying filters/having is now
  routed through the multi-step `/ws/table` builder, which serializes the negation correctly.) The
  filtered column must be listed in `fields` (an unselected filter column fails loud). For complex
  exclusion logic you can also use `create_worksheet_table` with
  `preAggregateCondition: WorksheetConditionNode[]` and `negated: true` — the same `/ws/table` path.

### Grouping & aggregation — `groupBy` + `aggregates` (+ required `having`)

```json
"groupBy":    [ { "fieldName": "city", "table": { /* … */ }, "dateGroupLevel": null } ],
"aggregates": [ { "fieldName": "amount", "table": { /* … */ }, "formula": "Sum",
                  "secondaryField": null, "n": null } ],
"having":     []
```

- `groupBy[].dateGroupLevel` groups a date column by the chosen level (see **Date Group Levels** section below).
- `aggregates[].formula` uses the same vocabulary as `fieldConfigs` (`Sum`, `Average`, `Count`,
  `DistinctCount`, `Max`, `Min`, `Median`, `NthLargest`, `Correlation`, `WeightedAverage`, …). Two-column
  formulas (`Correlation`, `Covariance`, `WeightedAverage`) use `secondaryField`; Nth/percentile
  formulas use `n`. An unknown formula falls back to `Sum`.
- **⚠️ Gotcha:** group-by/aggregates are applied **only if BOTH `aggregates` and `having` are
  non-null.** To aggregate *without* a having-filter, still pass **`"having": []`** (a present, empty
  array). Omitting `having` silently drops the aggregation.

### Date Group Levels

`dateGroupLevel` accepts the following `name` values (case-sensitive):

| Category | Value | Description |
|---|---|---|
| Interval | `"year"` | Group by year (e.g., 2024) |
| Interval | `"quarter"` | Group by quarter (Q1, Q2, …) |
| Interval | `"month"` | Group by month (Jan 2024, …) |
| Interval | `"week"` | Group by week |
| Interval | `"day"` | Group by day |
| Interval | `"hour"` | Group by hour (datetime/time only) |
| Interval | `"minute"` | Group by minute (datetime/time only) |
| Interval | `"second"` | Group by second (datetime/time only) |
| Part | `"quarter of year"` | Quarter number 1–4 (integer result) |
| Part | `"month of year"` | Month number 1–12 (integer result) |
| Part | `"week of year"` | Week of year 1–52 (integer result) |
| Part | `"week of month"` | Week of month 1–5 (integer result) |
| Part | `"day of year"` | Day of year 1–366 (integer result) |
| Part | `"day of month"` | Day of month 1–31 (integer result) |
| Part | `"day of week"` | Day of week Sun–Sat (integer result) |
| Part | `"hour of day"` | Hour 0–23 (integer result) |
| Part | `"minute of hour"` | Minute 0–59 (integer result) |
| Part | `"second of minute"` | Second 0–59 (integer result) |
| Full week | `"year of week"` | Year by full-week calculation |
| Full week | `"quarter of week"` | Quarter by full-week calculation |
| Full week | `"month of week"` | Month by full-week calculation |
| Full week | `"quarter of week part"` | Quarter part by full-week calculation (integer) |
| Full week | `"month of week part"` | Month part by full-week calculation (integer) |
| None | `"none"` | No grouping — raw date values |

**Interval** levels truncate/group the date (good for time-series axes). **Part** levels extract a numeric component (good for cyclic patterns, e.g. same month across different years). **Full week** levels handle fiscal or custom week alignment.

### Filter on aggregates — `having: HavingCondition[]`

**Use `having` only for non-highlight filtering** — conditions that must actually remove rows from the aggregated result (SQL `HAVING` semantics). For HAVING-like conditions that should only *visually mark* matching rows without removing them, use `apply_filter`'s `aggregateConditions` instead (StyleBI's highlight feature, which belongs in the visualization layer). See **When to aggregate in the worksheet** above for the full decision rules.

```json
{ "field": "amount", "aggregateFormula": "Sum", "operation": "GREATER_THAN", "value": "1000",
  "n": null, "secondaryField": null }
```

- `operation`: same vocabulary as `filters` — `EQUAL_TO` · `GREATER_THAN` · `LESS_THAN` · `BETWEEN`. Use `equal: true` for inclusive (≥ / ≤); use `negated: true` for not-equal.
- Equivalent to SQL `HAVING Sum(amount) > 1000`.
- **Multiple conditions**: add a `"junction": "AND"` (or `"OR"`) field to every condition *except the
  first*. Omit `junction` (or leave it null) on the first entry. Example — "count > 5 AND sum > 100":
  ```json
  [
    { "field": "order_id", "aggregateFormula": "Count", "operation": "GREATER_THAN", "value": "5" },
    { "field": "amount",   "aggregateFormula": "Sum",   "operation": "GREATER_THAN", "value": "100", "junction": "AND" }
  ]
  ```

### Sort — `orderBy: OrderByInfo[]`

```json
{ "field": "amount", "direction": "DESC" }
```
`direction`: `ASC` · `DESC`. (For top/bottom-N on a chart, prefer the dimension `ranking` in
`fieldConfigs` below — it limits *and* sorts in the chart binding.)

### Set operations — `tableSetOperations: TableSetOperation[]`

```json
{ "leftTable": { /* … */ }, "rightTable": { /* … */ }, "operation": "UNION" }
```
`operation`: `UNION` · `INTERSECT` · `EXCEPT`. The two tables must be column-compatible.

## Shape data in the worksheet, or bind it in the viewsheet?

Both layers can group/aggregate — **do not do it in both** (double aggregation).

**Default: aggregate in the visualization (`fieldConfigs`).** Build a worksheet of raw joined rows and let the measure's `aggregateFormula` aggregate at bind time. This keeps the chart re-bindable (drill, change measure, re-rank) without rebuilding the worksheet.

### When to aggregate in the worksheet

Aggregate in the worksheet when **any** of the following apply to the final visualization-source table:

1. **Intermediate table** — the table feeds another worksheet table rather than the chart directly. Freely aggregate and filter intermediate tables for data shaping.
2. **Non-highlight HAVING condition** — the query must *remove rows* based on an aggregated value (e.g. show only categories where `Sum(sales) > 1000`). This is distinct from StyleBI's **highlight** feature, which visually marks regions that meet a HAVING-like condition *without filtering rows* — highlight conditions belong in `apply_filter` on the visualization, not in the worksheet.
3. **SubQuery condition with group+aggregate** — the query groups and aggregates rows, and the result needs to be filtered against another table's values. Visualization does not support subQuery conditions, so the aggregation and the subQuery condition must both live in the worksheet.

### Retaining a column for board-level filtering, without fragmenting the aggregate

A dashboard's `additionalFilters`/`additionalPerChartFilters` (see the main `SKILL.md`'s **Materializing a dashboard**) can only target a column that's actually present on the chart's own final bound table. If you anticipate wanting to filter a board by some dimension a chart won't itself visualize (e.g. a bar chart grouped by `product_name` where you also want `product_type` filterable later), and that dimension is **functionally dependent on the existing group-by key** (each `product_name` always has exactly one `product_type` — a real 1:1 relationship, not one-to-many), add it to the aggregate as a **pass-through column**, not a `groupBy` addition: aggregate it with `First`/`Last`/`Max`/`Min` (any of these — the value doesn't vary within the group, so the choice of formula is arbitrary) rather than including it in `groupBy`. Adding it to `groupBy` instead would fragment the aggregate into one row per (group-by key, this column) combination, silently changing every other measure's displayed value — a real correctness bug, not a formatting nitpick. If the dimension is genuinely **not** functionally dependent on the grain (a true one-to-many relationship, e.g. a product with multiple suppliers), there's no free way to retain it without either fragmenting the aggregate or picking a lossy resolution (first/most-common) — that's a real modeling tradeoff to flag to the user, not something to paper over.

### Where to put conditions

| Scenario | Condition placement |
|---|---|
| No HAVING, no subQuery | All conditions → `apply_filter` on the visualization |
| Highlight HAVING only | All conditions → `apply_filter` (`aggregateConditions`) |
| SubQuery row-level filter only (no HAVING) | SubQuery condition → worksheet; all other conditions → `apply_filter` on the visualization. Group+aggregate, if needed, stays in the visualization. |
| Non-highlight HAVING exists | **All** conditions (row-level AND HAVING) → worksheet. The worksheet exposes only group-by + aggregated columns; any row-level column that is not a group-by column is unavailable to the visualization. |

### Binding pre-aggregated data

When aggregation is in the worksheet, bind the already-aggregated columns in `fieldConfigs` with **`aggregateFormula: "none"`** — do not re-aggregate in the visualization.

### Filtering an already-aggregated upstream table

The non-highlight HAVING rule above (group + aggregate + `postAggregateCondition` in ONE table) applies when THIS table is the one doing the aggregation. If the grouped aggregate you need is **already materialized by an upstream table** (e.g. an existing `<measure> by <dimension>` mirror from a prior step or an earlier turn), do **not** rebuild the group/aggregate in a downstream filter table. Re-grouping an already-aggregated column — an aggregate with `formula: "none"` sitting under a `GROUP BY` — produces invalid SQL (the pass-through column is neither grouped nor aggregated). Instead, build the filter table as a plain mirror of that upstream table **with no `aggregateInfo`**, and carry the threshold as a **`preAggregateCondition`** (a plain `WHERE` on the already-computed aggregate column).

Example: an existing `CUSTOMERS_BY_STATE` mirror `(STATE, TOTAL_CUSTOMERS)` to be filtered to `TOTAL_CUSTOMERS > 3` → a mirror over it with **no `aggregateInfo`** and `preAggregateCondition: TOTAL_CUSTOMERS > 3` — **not** a re-group of `STATE` with a `formula: "none"` `TOTAL_CUSTOMERS` plus `postAggregateCondition`.

### Special case — both non-highlight and highlight HAVING

The non-highlight HAVING triggers push-down: all aggregation and all conditions go into the worksheet. In `fieldConfigs`, bind the aggregated measure columns with `aggregateFormula: "none"`. The original highlight HAVING condition, now applied to a pre-aggregated column with no further aggregation in the visualization, **degrades to a regular `baseCondition`** — pass it to `apply_filter` as a `baseCondition` without an `aggregateFormula`.

---

## Worked example — total payments by city (join + filter)

User: *"top cities by total payment amount in 2023."* Raw-rows worksheet (let the chart aggregate &
rank); join `payment → customer → address → city`, filter to 2023:

```json
{
  "name": "payment_by_city",
  "fields": [
    { "fieldName": "amount",       "alias": "amount",       "type": "double", "table": { "name": "payment",  "source": { "type": "DATABASE", "path": "sakila", "schema": "public" } } },
    { "fieldName": "payment_date", "alias": "payment_date", "type": "date", "table": { "name": "payment",  "source": { "type": "DATABASE", "path": "sakila", "schema": "public" } } },
    { "fieldName": "city",         "alias": "city",         "type": "string", "table": { "name": "city",     "source": { "type": "DATABASE", "path": "sakila", "schema": "public" } } }
  ],
  "joinPaths": [
    { "leftTable": { "name": "payment",  "source": { "type": "DATABASE", "path": "sakila", "schema": "public" } }, "leftKey": "customer_id",
      "rightTable": { "name": "customer", "source": { "type": "DATABASE", "path": "sakila", "schema": "public" } }, "rightKey": "customer_id", "joinType": "inner", "joinOperator": "=" },
    { "leftTable": { "name": "customer", "source": { "type": "DATABASE", "path": "sakila", "schema": "public" } }, "leftKey": "address_id",
      "rightTable": { "name": "address",  "source": { "type": "DATABASE", "path": "sakila", "schema": "public" } }, "rightKey": "address_id",  "joinType": "inner", "joinOperator": "=" },
    { "leftTable": { "name": "address",  "source": { "type": "DATABASE", "path": "sakila", "schema": "public" } }, "leftKey": "city_id",
      "rightTable": { "name": "city",     "source": { "type": "DATABASE", "path": "sakila", "schema": "public" } }, "rightKey": "city_id",     "joinType": "inner", "joinOperator": "=" }
  ],
  "filters": [
    { "field": "payment_date", "operation": "BETWEEN", "value": ["2023-01-01", "2023-12-31"], "conditionOperator": "AND", "conditionLevel": 0, "negated": false }
  ]
}
```

The join keys (`customer_id`, `address_id`, `city_id`) are not listed — StyleBI auto-adds them as
hidden columns to satisfy the joins (see the join-key note above).

Then `create_viewsheet` with `intentCategory: "ranking"`, dimension `city`, and measure `amount`
(`aggregateFormula: "Sum"`) plus the top-N `ranking` on `city` (see `fieldConfigs` below) — the chart
does the `Sum` + top-N, so the worksheet stays raw and re-bindable.

If instead you needed the *aggregated table itself* (e.g. to apply `Sum(amount) > 5000`), add to the
same worksheet: `groupBy: [{ "fieldName": "city", … }]`, `aggregates: [{ "fieldName": "amount", "formula": "Sum", … }]`,
`having: [{ "field": "amount", "aggregateFormula": "Sum", "operation": "GREATER_THAN", "value": "5000" }]`.

If `validate_worksheet` returns `ok: false`, correct the model from the returned `errors` (usually a
wrong column/table name or an unbalanced join key) and re-validate until you get a `wsId`.

## Chart types

`create_viewsheet` takes an optional `visualizationType` (a steer — omit it to let StyleBI recommend),
and `change_chart_type` takes one. Valid values (StyleBI `ComponentType`):

`bar`, `3d_bar`, `area`, `point`, `step_area`, `interval`, `line`, `step_line`, `jump_line`, `pie`, `3d_pie`, `donut`, `radar`, `filled_radar`, `scatter_contour`, `stock`, `candle`, `boxplot`, `waterfall`, `pareto`, `treemap`, `sunburst`, `circle_packing`, `icicle`, `marimekko`, `gantt`, `funnel`, `tree`, `network`, `circular_network`, `contour_map`, `map`, `table`, `crosstab`, `image`, `gauge`, `text`.

**Cannot be switched via `change_chart_type`** (complex types — create them fresh instead): `stock`, `candle`, `boxplot`, `gantt`, `interval`, `scatter_contour`, `tree`, `network`, `circular_network`, `contour_map`, `map`.

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
  "dateGroupLevel": null, "ranking": null }
```
- `dateGroupLevel` (optional) groups dates (e.g. by month/quarter).
- `ranking` (optional): `{ "optionValue": number, "rankingN": number, "rankingCol": string }` for top/bottom-N.
  Set it on the **dimension** being limited. `optionValue`: **9 = top-N**, **10 = bottom-N** (0 = none).
  `rankingN` is the count (e.g. 10). `rankingCol` must be the **aggregated measure's ref name** exactly as
  it appears in the binding — i.e. `"<Aggregate>(<field>)"`, e.g. `"Sum(amount)"` (not the bare `"amount"`).
  Example — top 10 cities by total sales: dimension `city` with
  `"ranking": { "optionValue": 9, "rankingN": 10, "rankingCol": "Sum(amount)" }`, measure `amount` with `"aggregateFormula": "Sum"`.

**Measure** (numeric — the "how much"):
```json
{ "fieldType": "measure", "field": "Sales", "type": "double", "title": "Sum of Sales",
  "aggregateFormula": "Sum", "secondaryField": null, "nOrP": null }
```
- `aggregateFormula` — how the measure is aggregated. Valid values:
  `none`, `Average`, `Count`, `DistinctCount`, `Max`, `Min`, `Sum`, `First`, `Last`, `Median`, `Mode`, `Correlation`, `Covariance`, `Variance`, `StandardDeviation`, `PopulationVariance`, `PopulationStandardDeviation`, `WeightedAverage`, `Product`, `Concat`, `NthLargest`, `NthSmallest`, `NthMostFrequent`, `PthPercentile`, `SumSQ`, `SumWT`.
- Some formulas take an operand: `secondaryField` (for `Correlation`, `Covariance`, `WeightedAverage`, `SumWT`) and `nOrP` (the N or P for `NthLargest` / `NthSmallest` / `NthMostFrequent` / `PthPercentile`).
- `calculateInfo` (optional) — adds a trend/comparison calculation to a measure (percent-of-total, change, moving average, running total, etc.).

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

## Intent category

`create_viewsheet` requires an `intentCategory` — it tells the recommender what the user is trying to
see, which shapes the slot assignment. One of:

`comparison` (compare a measure across categories), `trend` (a measure over time), `distribution`
(spread of values), `proportion` (part-to-whole), `relationship` (two measures correlated), `ranking`
(top/bottom-N), `geospatial` (on a map), `other`.

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
   - **Ratios / percentages / rates / averages-of-averages** → don't `Sum`; use `Average` (or `none`
     and aggregate upstream). Summing a percentage column is meaningless.
   - **IDs / codes / years-as-numbers** → these are dimensions, not measures; if used as a measure use
     `Count` / `DistinctCount`, never `Sum`.
   - **Already-aggregated worksheet columns** → bind with `aggregateFormula: "none"`.
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

## Validate before committing

- `validate_worksheet(worksheetModel)` → `{ ok, wsId, errors? }`. If `ok` is false, fix the model
  using `errors` and re-validate; don't call `create_viewsheet` without a `wsId`.
- There is no separate binding dry-run: `create_viewsheet` builds and renders in one call, and returns
  `hasData` plus the sampled rows so you can see immediately whether the chart came out usefully.

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

---

## Complex queries — multi-step worksheet construction

The one-shot `validate_worksheet` model works for simple joins and aggregations. It breaks down when the query requires **chained aggregation** (aggregate, then compute something from the result, then filter on that) or **subquery conditions** that reference another table's values. Use the step-by-step `create_worksheet_table` tool for those cases.

### ⚠️ Before you build: pick the grain, don't over-decompose

> **Join at the coarsest grain that answers the question.** Before reaching for a line-item / detail
> table (`ORDER_DETAILS`) or a dimension table (`PRODUCTS`), ask **which entity already *mediates* the
> relationship** the question is about. A relationship phrased as *"X **handles / processes / is
> responsible for / serves** Y's transaction"* is recorded on the **transaction row itself** — e.g.
> "the salesperson responsible for a customer's goods" is `ORDERS.EMPLOYEE_ID` + `ORDERS.CUSTOMER_ID`:
> one order row already ties salesperson ↔ customer ↔ that order's goods. **Answer it from
> `SALES_EMPLOYEES ⋈ ORDERS ⋈ CUSTOMERS` — do not descend to `ORDER_DETAILS`/`PRODUCTS`.**
>
> Descending to a finer grain **decouples** the entities and silently **changes the question**.
> Matching purchases to a salesperson's sales on `(region, product)` turns *"was the customer served by
> their region's salesperson?"* into *"did the customer ever buy a product their region's salesperson
> **also sells**, in any order, to anyone?"* — a far broader relation that, on dense data, collapses an
> anti-join to **empty (or all rows)**. Descend to line-item grain **only** when the question is
> genuinely per-item (e.g. "quantity of product P per order"). Use the fewest tables that express the
> relationship; each extra grain is a chance to inflate joins and shift semantics.
>
> **An empty result — or one that returns *every* row — is a semantic red flag, not a green light.**
> When an anti-join / subquery filter yields 0 rows or all rows, do **not** conclude "verified correct"
> from matching counts and column names. Re-examine whether the join **grain** and the **correlation**
> actually match the question — a correctly-wired query at the wrong grain verifies clean and is still
> wrong. Sanity-check against a hand expectation ("surely *some* customer wasn't served by their own
> region") before reporting 0 or all.

#### Join-table construction

After identifying the analytical grain, build the smallest number of RelationalJoinTables necessary to produce that grain.
If all required fields can be reached through a single join graph, create ONE RelationalJoinTable containing all source tables and represent the relationships with multiple JoinPaths.
Do not create intermediate RelationalJoinTables solely to follow join dependency order.

Only split into multiple join tables when a single join would:
- introduce M:N multiplication / Cartesian expansion
- combine incompatible aggregation grains
- filter out required rows from an independent fact path

### Simple vs complex — routing decision

| Scenario | Approach |
|---|---|
| Single table, optional filters | `validate_worksheet` (one-shot) |
| Join + aggregate + chart binding, **every field the chart needs already exists as a column** | `validate_worksheet` (one-shot) |
| The measure asked for is **not an existing column** — it must be COMPUTED from one or more columns (sales amount = quantity × price, a late fee = flat_fee + rate × days_overdue, a ratio a/b) | Multi-step: a mirror carrying that `expressionColumn`, which is then your output table |
| Expression on top of an aggregated result | Multi-step |
| Subquery condition (compare to another table's column) | Multi-step |
| "Compare each row to the overall average / threshold" | Multi-step |
| Any chain: aggregate → compute → filter → aggregate again | Multi-step |
| Top/bottom-N groups or rows | WS `rankingCondition` or VS `fieldConfigs` dimension `ranking` |
| Whole-partition window (running total, per-group rank, share-of-group) | Declarative **self-join + aggregate** (see "Window functions declaratively" below) |
| LAG/LEAD, rolling average (ordered-neighbor) — includes "percent change from last/previous month", "month-over-month", "vs. prior period" comparisons | Declarative **`field[-N]` row-offset expression** (no join; see below) |
| ROW_NUMBER/RANK/DENSE_RANK/NTILE, per-group rank, LAG/LEAD, windowed SUM/AVG (pushed down) | **`windowColumns`** — real `OVER(...)` on the DB (see "Window columns (pushed down)" below) |
| CTEs, compound/nested window expressions, window-over-aggregate | **`sql query table`** (see below) |

> **⚠️ The binding layer only AGGREGATES what the worksheet hands it.** It applies an `aggregateFormula` to a
> column that already exists on the bound table, and nothing there can define a new column
> (`create_viewsheet`'s `fieldConfigs` takes no expression). So a measure that must be COMPUTED from other
> columns can only come from the worksheet: build it there as an `expressionColumn`. *"Leave the arithmetic to
> the chart"* is never an option. Deferring it fails **silently** — the chart binds the closest existing column
> instead (e.g. `Sum(QUANTITY)` where the user asked for `Sum(quantity × price)`).

Rule of thumb: if the mental model involves "first compute X, then compute Y from X", it's multi-step.

### Window columns (pushed down) — `windowColumns` (preferred for real window functions)

A worksheet table can declare `windowColumns` that render as real SQL `OVER (...)` clauses executed on the
database. This is the preferred way to get `ROW_NUMBER/RANK/DENSE_RANK/NTILE/LAG/LEAD/PERCENT_RANK/CUME_DIST`
and windowed `SUM/AVG/COUNT/MIN/MAX`: it is **pushed down** (fast), composes with the table's joins/filters,
and — unlike the bare `row` scope variable, which is physical-scan-order only and cannot be ordered — gives a
true `ORDER BY`-controlled rank.

Put `windowColumns` on a **mirror** over the base table (window/expression columns aren't valid on a grouped
table). Each entry:

    { name, fn, column?, n?, partitionBy?: string[], orderBy?: [{ field, direction }], type? }

- `fn`: ROW_NUMBER · RANK · DENSE_RANK · NTILE · LAG · LEAD · FIRST_VALUE · LAST_VALUE · PERCENT_RANK ·
  CUME_DIST · SUM · AVG · COUNT · MIN · MAX
- `column`: the argument column — required for LAG/LEAD/FIRST_VALUE/LAST_VALUE and the windowed aggregates
- `n`: NTILE bucket count, or LAG/LEAD row offset
- `orderBy`: required for the ranking/offset functions (ROW_NUMBER/RANK/DENSE_RANK/NTILE/LAG/LEAD/PERCENT_RANK/
  CUME_DIST); optional for a plain windowed aggregate. `direction` defaults to `DESC`.
- refs (`column`, `partitionBy`, `orderBy.field`) use the mirror's visible column names (e.g. `OPP.amount`) and
  are emitted as `field['…']` so StyleBI applies dialect-correct identifier quoting.

Per-group ROW_NUMBER (verified: restarts at 1 within each `sales_stage`, ordered by amount desc):

    { "tableName": "OPP_W", "tableType": "mirror table", "baseTables": ["OPP"],
      "windowColumns": [{ "name": "rnk", "fn": "ROW_NUMBER",
        "partitionBy": ["OPP.sales_stage"], "orderBy": [{ "field": "OPP.amount", "direction": "DESC" }] }] }

Deal-size quartiles (verified: `NTILE(3)` over 200 rows → 67/67/66, remainder front-loaded — true NTILE):

    { "name": "tile", "fn": "NTILE", "n": 3, "orderBy": [{ "field": "OPP.amount", "direction": "DESC" }] }

**Gotchas:**
- **Frame default on windowed aggregates.** A windowed aggregate (`SUM`/`AVG`/…) *with* `orderBy` gets the ANSI
  default frame (`RANGE … UNBOUNDED PRECEDING TO CURRENT ROW`) — i.e. a **running total**, not a group total.
  For a whole-partition total, set `partitionBy` and **omit `orderBy`**.
- **Window over an aggregate → use two tables (the verified, preferred shape).** A single table may not carry
  BOTH `windowColumns` and `aggregateInfo` (fails loud) — and you don't want it to: a single-table window can't
  reference its own aggregate in the `OVER` (SQL forbids referencing a same-level SELECT alias in `OVER`).
  Instead, **aggregate in table A, then window in a mirror B over A**, where the aggregate is a plain output
  column of A. Verified live (pushes down to one nested `RANK() OVER (ORDER BY …) FROM (… GROUP BY …)`):
  ```
  A = OPP → STG_AGG   (aggregateInfo: group sales_stage, SUM(amount) → stage_total)
  B = mirror(STG_AGG) windowColumns:[{ name:"rnk", fn:"RANK",
                                       orderBy:[{ field:"STG_AGG.stage_total", direction:"DESC" }] }]
  ```
  yields each stage ranked by its total. Reach for `sql query table` only for shapes neither table can express.
- **`LAST_VALUE` is supported** (Phase 3): pass it like any windowed function. The classic ANSI footgun is that
  the default frame (`… UNBOUNDED PRECEDING TO CURRENT ROW`) makes `LAST_VALUE` return the *current* row, not the
  partition's last value. To be explicit and portable, set an explicit whole-partition frame —
  `frame: { startBound: "UNBOUNDED_PRECEDING", endBound: "UNBOUNDED_FOLLOWING" }` — which guarantees the partition's
  actual last value. (Equivalently, `FIRST_VALUE` over the reversed `orderBy` gives the same result and needs no
  frame.) *Verified live (Postgres, image local-1017): StyleBI's pushed-down window builder defaults `LAST_VALUE` to
  a whole-partition frame, so even an un-framed `LAST_VALUE` returned the true partition-last value here — but keep
  setting the explicit frame, since the ANSI default differs by dialect.*
- **Dialect support.** Emitted as standard ANSI window SQL and pushed down. Databases known to lack window
  functions (MS Access, Derby, MongoDB, MySQL < 8.0, MariaDB < 10.2) are now gated **early** at
  `create_worksheet_table` with a clear message (naming the dialect + remedy), instead of failing later with a
  raw DB error. Unknown dialects (or a datasource with no reported type/version) stay permissive and, if truly
  unsupported, surface the database's own error at render (`create_viewsheet`).

### Window functions declaratively (self-join + aggregate) — prefer this over raw SQL where it applies

Many "window function" needs are expressible in the declarative model — no `sql query table` required.
Whole-partition frames (running total, per-group rank, share-of-group) use a **self-join + aggregate**;
ordered-neighbor windows (LAG/LEAD, rolling average) use a **`field[-N]` row-offset expression** with no join
at all. Both are dialect-agnostic and re-bindable, so prefer them. (Requires a StyleBI build with the non-equi-join column-disambiguation fix,
stylebi#4206 / `local-983`+; on older builds a non-equi self-join's duplicate columns are unresolvable and
the aggregate **silently returns 0 rows** — there, fall back to the `sql query table` below. If a declarative
window returns 0 rows unexpectedly, that's the tell.)

The key mechanism: an ordinary equi-join can't express "compare a row to *other* rows", but a **non-equi
join** (`joinOperator` `>=`/`<=`/`>`/`<`) can — join a table to a copy of itself on an inequality, then
aggregate. Give the two sides **distinct column names** (the fix does this automatically by table-qualifying
duplicates, e.g. `T_B_amount`); use those names in the downstream group/aggregate.

**Running total** (`SUM(x) OVER (ORDER BY t)`) — for a plain running total, prefer a **`windowColumns`**
`SUM … OVER (ORDER BY t)` (see "Window columns (pushed down)" above); it's one line and needs no join.
Reach for this triangular self-join only when you need **dialect-agnostic portability** (a plain theta-join
+ GROUP BY runs even where the DB lacks window functions) or a **rank/count value to filter or aggregate on
further**. The self-join usually pushes down to SQL too (same-datasource merge), so it isn't cheaper —
triangular self-join on `t_a >= t_b`, then SUM per `t_a`:
```
BASE  → GRAIN   (agg: group t, SUM(x) → x_t)     # t must be ORDERABLE: an int key (YYYYMM) or a truncated
                                                 #   date — NOT a "Jan 2025" label (that sorts lexically)
GRAIN → GRAIN_B (mirror copy of GRAIN)
JOIN  = GRAIN ⋈ GRAIN_B  ON t >= t   (relational join, joinOperator ">=")
RUNTOT= mirror(JOIN) agg: group t(A), SUM(x_t of B) → running_total    # ends at the grand total
```

**Per-group top-N / rank** (`ROW_NUMBER()/RANK() OVER (PARTITION BY g ORDER BY m DESC)`) — rank = the count
of same-partition rows whose measure is `>=` this row's. Compound self-join (equi on `g` **AND** non-equi on
`m`), then COUNT:
```
BASE  → BASE_B (mirror copy)
JOIN  = BASE ⋈ BASE_B  ON g = g (equi)  AND  m <= m (non-equi → B holds the >= measures)
RANKED= mirror(JOIN) agg: group [g, id, m, …], COUNT(B.id) → rnk       # the max in each g gets rnk 1
TOPN  = mirror(RANKED) preAggregateCondition rnk <= N
```
This yields **RANK** semantics (ties share a rank); with a continuous measure there are no ties, so it equals
`ROW_NUMBER`. (For pure *display* of per-group top-N you can also use the binding-layer crosstab ranking — see
the note below — but the self-join is what gives you an actual rank *value* to filter or compute on.)

**Share-of-group / deviation from group mean** (`x / SUM(x) OVER(g)`, `x - AVG(x) OVER(g)`, `x / AVG(x) OVER(g)`)
— no self-join needed: aggregate per `g`, **equi**-join that back to the detail on `g`, and compute the ratio/
difference in a mirror `expressionColumn`.

**`LAG`/`LEAD` and rolling windows** (`LAG(x) OVER (ORDER BY t)`, moving averages) — **no join at all**: a
`sql: false` expression column can reference other rows by offset, `field[-1]['x']` (previous row = LAG 1),
`field[-2]['x']` (LAG 2), `field[1]['x']` (next row = LEAD 1). Sort the base table by the ordering key first —
a `GROUP BY t` usually returns rows in `t` order but SQL doesn't guarantee it, so add an `orderBy` on the key to
be safe — and guard the edge row: at the first row `field[-1]` returns the literal column-name **string**, not
null. Example MoM %:
`!(field[-1]['rev'] > 0) ? 0 : (field['rev'] - field[-1]['rev']) / field[-1]['rev'] * 100`. See
`ws-expression-functions.md` § "Row references" for the full rules. (It's row-offset, so it's true
LAG-over-ordered-rows — a gap collapses to the previous *existing* row.)

**When to use which:** for `NTILE` and true (tie-broken, ORDER-BY-controlled) `ROW_NUMBER`/`RANK`, prefer
**`windowColumns`** (pushed-down real SQL — see "Window columns (pushed down)" above); the self-join recipes
here remain useful when you need the rank *value* inside a further declarative aggregate/filter, or on a
StyleBI build without the non-equi-join disambiguation fix. **Still genuinely `sql query table`-only:** CTEs,
compound/nested window expressions, and window-over-aggregate (`windowColumns` cannot combine with
`aggregateInfo`).

> **Why `NTILE` can't be faked with rank + a bucket expression** (a tempting dead end): `NTILE(k)` needs
> strict **distinct** ordered positions and front-loads the remainder into the *first* buckets (N rows, k tiles →
> the first `N mod k` buckets get one extra row). The declarative self-join rank is count-of-peers = **RANK**
> (ties share a value), and a `ceil(rank·k/N)` bucket formula both back-loads the remainder into the *last*
> buckets and mis-handles ties. The two only coincide when `N` is divisible by `k` **and** the order key has no
> ties — a degenerate case that looks like it works and isn't general. Use a `windowColumns` `NTILE` (real
> pushed-down SQL — see "Window columns (pushed down)" above), not the self-join.

### `sql query table` — raw SQL escape hatch (NTILE, tie-broken ROW_NUMBER, CTEs, and windows on older builds)

For the cases the declarative patterns above **can't** cover — `NTILE`, strict tie-broken `ROW_NUMBER`, CTEs,
or any window function on a StyleBI build without the disambiguation fix — use a `create_worksheet_table` step
with `tableType: "sql query table"`:

> **Note on per-group top-N ("top 3 categories *per* state"):** you do **not** need a SQL window function
> for this. Ordinary per-group top-N is a **binding-layer** concern — bind both dimensions and set the
> top-N `ranking` on the inner one, rendered as a **crosstab** with both dims on `rows` (see
> `viewsheet-binding.md` § Fields → ranking). Reach for a `sql query table` window function only when the
> per-group ranking must combine with something the binding can't do (e.g. also computing a running total,
> or filtering on the rank value before further aggregation).
set `sqlExpression` to a raw SQL `SELECT` (executed verbatim on the DB) and `physicalSource.datasourcePath`
to the datasource. Use the real physical table/column names. Other tables can mirror/join the result by name,
and you bind its columns in `create_viewsheet` like any other table.

**Always format `sqlExpression` with proper line breaks and indentation.** Use `\n` within the JSON string to separate clauses, with spaces for indentation. Each major SQL clause on its own line; never write it as a single long line.

```json
{ "tableName": "TOP3_PER_STATE", "tableType": "sql query table",
  "physicalSource": { "datasourcePath": "olist", "schema": "public", "tableName": "order_items" },
  "sqlExpression": "SELECT seller_state, category, revenue, rnk\nFROM (\n  SELECT\n    s.seller_state,\n    c.product_category_name_english AS category,\n    SUM(i.price) AS revenue,\n    ROW_NUMBER() OVER (PARTITION BY s.seller_state ORDER BY SUM(i.price) DESC) AS rnk\n  FROM order_items i\n  JOIN products p ON i.product_id=p.product_id\n  JOIN sellers s ON i.seller_id=s.seller_id\n  LEFT JOIN product_category_name_translation c ON p.product_category_name=c.product_category_name\n  GROUP BY s.seller_state, c.product_category_name_english\n) t\nWHERE rnk <= 3" }
```

(`physicalSource.schema`/`tableName` are ignored for this type — only `datasourcePath` is used — but pass a real
table to satisfy the shape.) Prefer the declarative physical/mirror/join model for everything it *can* express;
reach for raw SQL only when it genuinely can't.

**⚠️ Confirm database support before using advanced SQL syntax.** Always call `get_table_details` first to obtain `datasourceType` and `datasourceVersion`. Only use SQL features (window functions, CTEs, specific aggregate functions, type casting) you can confirm are supported by that specific database **and version**. Do **not** infer support from related or similar databases — even when one database's syntax is influenced by another, it only implements a subset of that dialect. If support is uncertain, fall back to the declarative physical/mirror/join model or compute the result in a mirror `expressionColumn` instead. The `sql query table` is validated structurally at creation time but the SQL is only executed at render time (`create_viewsheet`) — unsupported syntax is accepted silently during construction and only fails later, requiring a full worksheet rebuild.

### Two-phase execution: Plan then Execute

**Phase 1 — Plan (no API calls).** Analyze the query, decide all table steps, dependency order, join types, and which steps require mirrors. Write the complete plan before calling any tool. Wrong guesses here cause cycle errors or mismatched column names that are hard to debug later.

**Phase 2 — Execute (one `create_worksheet_table` call per dependent step).**
- Physical tables with no inter-dependencies can be batched in one `WorksheetRequest`.
- Every subsequent step reads the previous step's `columns` response — use those exact names for field references in the next request; do not guess from the request alone.

> **⚠️ `create_viewsheet` binds the LAST-created worksheet table** (StyleBI resolves it as the worksheet's
> primary table). Your **final output table must be the last one you create**. If you append a scratch/probe
> table *after* your output table, `create_viewsheet` will bind that scratch table instead and reject your
> intended fields with an opaque *"Unknown field(s)… Available worksheet columns: […]"* error listing the
> **wrong** table's columns. Fix — **for that scratch-table case only**: append a **passthrough mirror** (a
> `MirrorTable` with no `aggregateInfo` and no `expressionColumns`) over the table you actually want to chart,
> so it becomes the last-created table — then `create_viewsheet`. *"Passthrough"* describes that **remedy**, not
> a shape the final table has to have: a mirror carrying `expressionColumns` (or `aggregateInfo`) is an equally
> valid last-created table. If your output needs a derived column, build it **on** that final mirror — never
> strip it to make the table "passthrough".
> (`create_viewsheet` now appends this rule + remedy to that error automatically.)

### Table types

**PhysicalBoundTable** — selects columns from a single physical database table. All columns in one `PhysicalBoundTable` must come from the same source table.

**RelationalJoinTable** — joins two or more already-created worksheet tables on key columns. Join types: `inner` · `left` · `right` · `full` · `cross`. Use `left` join when one side may have no matching rows (e.g. orders with no returns); an `inner` join silently drops those rows and skews aggregates.

**MirrorTable** — a transparent view over an existing worksheet table. The mirror sees only the base table's output columns, not its internals. Three reasons to create a mirror:

1. **Add an expression after aggregation.** StyleBI prohibits expression columns on a table that has `aggregateInfo`. Create a mirror of the aggregated table, then add the expression to the mirror.
2. **Avoid cycle errors.** A table cannot have a subquery condition that (directly or indirectly) references a table derived from it. Create a fresh mirror of the base table and put the condition there instead.
3. **Re-aggregate aggregated results.** To compute "average of per-category rates", first aggregate to category rates in one mirror, then aggregate again in a second mirror with no `groupBy`.

**SQLQueryTable** — `tableType: "sql query table"`: a table bound to a raw SQL `SELECT` (in `sqlExpression`) run verbatim on the datasource. The escape hatch for window functions / per-group top-N / CTEs that the declarative model can't express (see the dedicated section above). Other tables can mirror/join it by name.

### CreateWorksheetTableRequest — field reference

```
tableName    string          Name for this table; subsequent steps reference it by this name.
tableType    enum            "physical table" | "mirror table" | "relational join table" |
                             "merge join table" | "cross join table" | "sql query table"

── Physical table only ──────────────────────────────────────────────────────────
physicalSource  { datasourcePath, schema, tableName }
columns         PhysicalColumn[]   { name, alias?, type }
                  All columns are visible by default; use fieldConfigs in create_viewsheet
                  to control chart binding. Use alias to disambiguate same-name columns
                  across tables (e.g. "alias": "OD_CUSTOMER_ID").

── Mirror table only ────────────────────────────────────────────────────────────
baseTables      string[]           ARRAY with one element: the table to mirror.
                                   ⚠️ Always an array — "baseTables": ["TableName"]
                                   The singular "baseTable" string is invalid.
expressionColumns  ExpressionColumn[]  { name, expression, type, sql? }
  sql: boolean (default false)
        false (default) — JavaScript expression, runs in StyleBI's JS engine.
        true            — SQL expression, inlined verbatim into the generated query.
                          Must be valid SQL for the datasource's dialect.
  ⚠️ Only valid when the mirror itself has NO aggregateInfo.

#### sql vs JS decision — ONLY read this sub-section if the plan includes `expressionColumns`

**If no table in the plan has `expressionColumns`, skip the rest of this sub-section entirely.**

⚠️ **How you reference a column differs by `sql`, and getting it wrong fails two different ways.**
- `sql: false` → `field['TableName.col']`.
- `sql: true` → the column's **bare SQL alias**, unquoted or double-quoted: `COALESCE(starting, due)`.
  A `field[...]` reference inside a `sql: true` expression is **not** rewritten — StyleBI JS-evaluates
  the whole expression and you get `ReferenceError: COALESCE is not defined`. A table-qualified
  identifier (`"MyMirror"."col"`) fails the other way, with `missing FROM-clause entry for table`,
  because the generated SQL has flattened those tables away. Only the bare alias is in scope.

⚠️ **Alias a `dateGroupLevel` group whenever anything downstream must NAME it.** Unaliased, its output
column is the rendered expression `Month(T__fkjoin.due_date)` — not a SQL alias — so it cannot be
referenced from a `sql: true` expression under any form. Set `alias` on the group (same idea as an
aggregate alias) and it becomes addressable, which is what makes the canonical FULL-join shared axis
expressible in pushed-down SQL:

```
groups: [{ fieldName: "start_date", dateGroupLevel: "month", alias: "start_month" }]
… then downstream: { sql: true, expression: "COALESCE(start_month, due_month)" }
```

A date bucket is expressed with `dateGroupLevel` (see the **Date Group Levels** section — `"month"` is
one of its Interval values), not with a hand-written SQL truncation function. Alias the group as above
and take the resulting column name from the response.

If the response reports `groupAliasesNotApplied` — or an aliased group comes back as a rendered
expression (`Month(T.due_date)`) instead of the alias you asked for, comparing on the **bare** name
after the last dot, since a group column is re-prefixed with its base table — the connected StyleBI
predates group aliases and dropped them. Only then compute the bucket in a `sql: true` expression
column instead. The truncation function you name there must exist in **this** datasource: confirm it
against the `datasourceType` and `datasourceVersion` returned by `get_table_details`, and do not infer
support from a related dialect.
Any JDBC datasource can be connected, so there is no list of portable functions to consult and no
family a function can be assumed safe within — the datasource in front of you is the only authority.
`DATE_TRUNC` is the example that broke: Derby has no such function at all, and among the dialects that
*do* have it the argument order differs (Postgres `DATE_TRUNC('month', ts)` vs BigQuery
`DATE_TRUNC(ts, MONTH)`), so copying a form that works elsewhere is not evidence either. A mirror's
expression columns are resolved at build time, so `create_worksheet_table` returns `success: false`
for that table.

Either way, keep the chain in SQL. A `sql: false` JS expression over the date column also works, but one
`sql: false` expression makes the table not `sqlMergeable`, so the whole pipeline stops being pushed to
the database.

  **During planning, determine each table's `sqlMergeable` in dependency order.**
  A table is `sqlMergeable` only if its direct base is `sqlMergeable` AND it adds none
  of the following. Use `sql: true` for expression columns only if this table is
  `sqlMergeable`.

  | Makes a table NOT `sqlMergeable`         |
  |-----------------------------------------|
  | Physical table: `datasourceType` absent / unknown, or cross-source join |
  | `rankingCondition` |
  | `aggregateInfo` formula: `First`/`Last`/`NthLargest`/`NthSmallest`/`NthMostFrequent`/`PthPercentile`/`Product`/`Concat`/`Mode` |
  | `orderBy` + `First`/`Last` formula |
  | `preAggregateCondition` / `postAggregateCondition` / `orderBy` references a `sql: false` expression column |

  When `sql: true`, use dialect-appropriate SQL. Only use `sql: false` when the operation has no SQL equivalent (see table below).

  `sql: false` only for: zero-guard division (JS ternary is simpler), date arithmetic
  (use `dateDiff`/`dateAdd`/`datePart`), type casting (dialect type names vary), and string
  concatenation (see warning below).
  Everything else (`+`/`-`/`*`/`/`, `CASE WHEN`, `CONCAT`/`||`, `COALESCE`, DB functions): `sql: true`.

  ⚠️ **`sql: false` string concatenation:** use JS `+` between two string-typed values, e.g.
  `field['T.first_name'] + ' ' + field['T.last_name']` — never `||`. In StyleBI's JS engine `||`
  is logical OR / truthy short-circuit, **not** string concatenation: `'Smith' || ', '` returns
  `'Smith'` outright and silently drops everything after it, with no error at any layer, build or
  render. If either operand might be numeric-typed, cast explicitly first (e.g.
  `String(field['id']) + field['suffix']`) — `+` between two numbers adds them instead of
  concatenating, which silently produces the wrong value the same way.

  For all available `sql: false` JS functions, interval codes, and ES5 constraints, read `instructions/ws-expression-functions.md`.

  **`sql` decision examples:**

  | Scenario | sql |
  |---|---|
  | Expression `field['price'] * field['quantity']` on mirror of standard JDBC table | `true` |
  | Expression `dateDiff('d', field['order_date'], field['ship_date'])` | `false` |
  | Same mirror has expr A (`price * 1.1`, would be true) and expr B (`dateDiff(...)`, false) | both `false` — B makes the table not `sqlMergeable`, A must follow |
  | Mirror of aggregation using `Sum(amount)` group by city; expression `field['sum_amount'] * 1.1` | `true` — `Sum` is sqlMergeable |
  | Mirror of aggregation using `First(order_date)`; any downstream expression | `false` — `First` makes base not `sqlMergeable` |
  | Mirror of table with `rankingCondition`; any downstream expression | `false` — ranking makes base not `sqlMergeable` |

── Join table only ──────────────────────────────────────────────────────────────
baseTables      string[]           Names of already-created tables to join
joinPaths       JoinPath[]         { leftTable, leftKey, rightTable, rightKey,
                                    joinType, joinOperator }
                  ⚠️ Join keys always use the column name WITHOUT its table prefix.
                  The part before "." in a response column is the source table name,
                  not part of the column name. If the response column is
                  "ORDERS.O_CUSTOMER_ID", the join key is "O_CUSTOMER_ID".
                  This applies whether the table in baseTables is a physical table,
                  a join table, or a mirror.

── Mirror & Join ────────────────────────────────────────────────────────────────
aggregateInfo   { groups?: GroupByField[], aggregates?: AggregateField[] }
  GroupByField    { fieldName, dateGroupLevel? }
                  dateGroupLevel — see **Date Group Levels** section above for valid values.
  AggregateField  { fieldName, formula, alias?, secondaryField?, n? }
                  formula: Sum | Count | DistinctCount | Average | Max | Min | Median |
                  First | Last | StandardDeviation | Variance | NthLargest | NthSmallest |
                  NthMostFrequent | PthPercentile | Correlation | Covariance |
                  WeightedAverage | Product | Concat | none
                  alias — **Strongly recommended when formula is not "none".** Always set
                  alias explicitly on every aggregate. A fallback is applied when omitted,
                  but auto-generated names may be less readable in subsequent steps.
                  Two acceptable formats:
                  · FORMULA_fieldName (preferred): SUM_amount, AVG_price,
                    COUNT_DISTINCT_user_id — unambiguous for downstream viewsheet binding.
                  · Descriptive semantic name: total_revenue, avg_return_rate — acceptable
                    when it clearly conveys the formula and field.
                  Without alias the backend returns raw prefixed names (e.g.
                  "Query1.amount") that are harder to reference in subsequent steps.

── Output column naming ─────────────────────────────────────────────────────────
Column names in the create_worksheet_table response follow these rules.
The prefix before "." is always a table name — not part of the column name itself.
· Physical table       → no prefix  (e.g. "O_CUSTOMER_ID", "PRODUCT_NAME")
· Relational join table → prefixed with the physical source table name
                          (e.g. "ORDERS.O_CUSTOMER_ID", "PRODUCTS.PRODUCT_NAME")
· Mirror of a join table → re-prefixed with the direct base table name
                          (e.g. mirroring FullJoin whose column was
                          "ORDERS.O_CUSTOMER_ID" → "FullJoin.O_CUSTOMER_ID")
· Mirror aggregateInfo groups → same re-prefix rule — NOT stripped to bare name
                          (e.g. group on "OrdersJoin.O_CUSTOMER_ID" in a mirror
                          of OrdersJoin → still "OrdersJoin.O_CUSTOMER_ID")
· Mirror aggregateInfo aggregates with alias → bare column name only ("MAX_DATE")
· Mirror expressionColumns → bare column name only ("return_rate")

Use the response column names in: aggregateInfo.fieldName, expressionColumn
field[...] references, and condition field values.
Exception — joinPaths keys and fieldConfigs always use the bare column name
(without table prefix); see notes on those sections.
⚠️ "Bare" means without the TABLE PREFIX — not "strip everything before the first
dot". A DERIVED column name is one unsplittable attribute even when it contains
dots: a dateGroupLevel group column comes back as "Quarter(WP__fkjoin.due_date)",
and that whole string is the joinPaths key. Those dots are inside the function
argument, not a table prefix — "due_date" and "Quarter(due_date)" are both rejected
with "join key not found in table". The server resolves the key by ATTRIBUTE NAME,
which for a plain column is the bare name and for a derived one is the whole
rendered expression. Take the name verbatim from the previous step's `columns`
response, and strip a leading "<TableName>." only when the remainder is a plain
column.

── Conditions ───────────────────────────────────────────────────────────────────
preAggregateCondition   WorksheetCondition[]   Applied before GROUP BY (WHERE)
postAggregateCondition  WorksheetCondition[]   Applied after GROUP BY (HAVING)
rankingCondition        WorksheetCondition[]   Top / bottom-N, runs last.
                        operation must be TOP_N or BOTTOM_N (not the standard filter operations).
                        Two modes — (a) group mode (table has aggregateInfo): field = group column,
                        aggregateFormula = the measure formula → selects top N groups by that
                        aggregate. (b) flat mode: field = aggregate column → top N rows globally.
                        (c) per-group mode: add partitionBy:[<dim cols>] on the ranking leaf of a
                        PLAIN physical table → top/bottom N rows WITHIN each partition. The table is
                        auto-rewritten into a generated ROW_NUMBER() OVER (PARTITION BY … ORDER BY
                        field <TOP=DESC/BOTTOM=ASC>) sql query table, materializing a rank column
                        (rankAlias, default "rnk"). field = the raw column to rank by; N from values.
                        SCOPE: physical table only, no joins/aggregation/expressions/other conditions
                        (fails loud otherwise). Needs a DB with window functions (Postgres ✓). For a
                        derived input, or per-group top-N for DISPLAY only, prefer the crosstab
                        binding-layer ranking (see viewsheet-binding.md) — no SQL needed.
```

Per-group top-N example — top 3 opportunities by amount within each sales stage:
```json
{ "tableName": "TOP3_PER_STAGE", "tableType": "physical table",
  "physicalSource": { "datasourcePath": "suitecrm", "schema": "public", "tableName": "opportunities" },
  "columns": [ { "name": "name", "type": "string" }, { "name": "amount", "type": "double" }, { "name": "sales_stage", "type": "string" } ],
  "rankingCondition": [ { "type": "condition", "condition": {
    "field": "amount", "operation": "TOP_N", "values": [ { "type": "VALUE", "value": 3 } ],
    "partitionBy": ["sales_stage"] } } ] }
```

### Condition tree structure

Conditions on a table are a **tree** of nodes, mirroring `ConditionNodeSchema` (conditionSchemas.ts). Each condition array (`preAggregateCondition`, `postAggregateCondition`, `rankingCondition`) holds `WorksheetConditionNode[]`.

A node is either a **leaf** or a **group**:

```
WorksheetConditionLeaf          WorksheetConditionGroup
─────────────────────           ───────────────────────────────────
type: "condition"               type: "group"
junction?: "and" | "or"         junction?: "and" | "or"
condition: WorksheetCondition   items: WorksheetConditionNode[]   ← recursive; min 2
```

`junction` joins this node to its **preceding sibling**; omit it for the first node in a list.

Groups nest arbitrarily — e.g. `(A AND B) OR (C AND D)` is two groups at the top level joined by `"or"`, each containing two leaves.

**WorksheetCondition** (leaf payload) — mirrors `ConditionSchema`:

```
field              string    Column to filter on
operation          enum      EQUAL_TO | ONE_OF | LESS_THAN | GREATER_THAN | BETWEEN |
                             STARTING_WITH | CONTAINS | LIKE | NULL | DATE_IN
negated?           boolean   true = IS NOT
equal?             boolean   LESS_THAN / GREATER_THAN only: true = inclusive (≤ / ≥)
dateGroupLevel?    string    Date grouping before comparison, e.g. "year", "month"
aggregateFormula?  string    For HAVING-style conditions, e.g. "Sum", "Count"
secondaryField?    string    Two-column aggregate formulas (Correlation, WeightedAverage…)
nOrP?              number    N/P for NthLargest / NthSmallest / PthPercentile
values             WorksheetConditionValue[]
  NULL → 0 values · BETWEEN → 2 · ONE_OF → 1..N · all others → 1
```

**WorksheetConditionValue** — each entry in `values`:

| `type` | Use when | `value` | `subQuery` |
|---|---|---|---|
| `VALUE` | Literal constant | the literal (number, string, date string) | — |
| `FIELD` | Another column in the same table | column name | — |
| `EXPRESSION` | JS expression as operand | JS string | — |
| `SESSION_DATA` | Session variable (current user / org) | session key | — |
| `SUBQUERY` | Value from another worksheet table | — | `SubQueryValue` |

**⚠️ `EXPRESSION` column references must use `field['COLUMN']` for physical-table conditions, or `field['TABLE.COLUMN']` for join/mirror-table conditions** — bare identifiers (e.g. `REORDER_LEVEL`) are evaluated as JS variables, fail with `ReferenceError`, and silently degrade to a tautology (`col = col`).

`candidates` (optional, `VALUE` type only): semantic alternatives for fuzzy string matching; first element must equal `value`.

**SubQueryValue:**
```
subQueryName        string    Name of an already-created worksheet table
inSubQueryColumn    string    Column in that table whose value is the operand.
                              Format rules:
                              · If the column has an explicit alias set in aggregateInfo.aggregates
                                → use the alias alone, e.g. "total_cats" (NO table prefix).
                              · If no alias was set → use the source field reference,
                                e.g. "CATEGORIES.CATEGORY_ID".
                              · Never use a table-qualified alias like "AllCatCount.total_cats" —
                                that form is not recognised and the condition is silently ignored.
where?              object    Omit for a global scalar (single-row table).
  subQueryColumn      string  Column in the subquery table to match on
  currentTableColumn  string  Column in the current table to match against
```

**Example — nested condition `(return_rate > threshold) AND (total_ordered > 0 OR category IS NOT NULL)`:**
```json
[
  {
    "type": "condition",
    "condition": {
      "field": "Category_return_rate.return_rate", "operation": "GREATER_THAN",
      "values": [{ "type": "SUBQUERY", "subQuery": { "subQueryName": "Threshold", "inSubQueryColumn": "Threshold.threshold" } }]
    }
  },
  {
    "type": "group", "junction": "and",
    "items": [
      {
        "type": "condition",
        "condition": { "field": "Category_return_rate.total_ordered", "operation": "GREATER_THAN", "values": [{ "type": "VALUE", "value": 0 }] }
      },
      {
        "type": "condition", "junction": "or",
        "condition": { "field": "Category_return_rate.CATEGORY_NAME", "operation": "NULL", "negated": true, "values": [] }
      }
    ]
  }
]
```

### Expression column syntax

Expression columns use JavaScript. Reference other worksheet columns as `field['TableName.col']`. Every referenced column must be selected in the same worksheet table.

```js
// Return rate — guard against divide-by-zero
!field['Category_totals.total_ordered'] ? 0
  : field['Category_totals.total_returned'] / field['Category_totals.total_ordered']

// Double a value
field['Global_avg.global_avg'] * 2

// String concatenation (JS string concat, not SQL +)
field['T.first_name'] + ' ' + field['T.last_name']
```

### Common multi-step patterns

**Pattern 1 — aggregate → expression (always needs a mirror between them)**
```
PhysicalTables
  └─join──► JoinTable
              └─mirror──► AggregatedMirror   (aggregateInfo: groupBy + aggregates)
                            └─mirror──► ExpressionMirror  (expressionColumns)
```

**Pattern 2 — global scalar comparison ("each row vs the overall average")**
```
RawTable
  └─mirror──► PerKeyAggregated     (groupBy key, aggregate measure)
                └─mirror──► PerKeyRate      (expressionColumn: rate = A / B)
                              ├─mirror──► GlobalAvg   (no groupBy, AVG(rate) → 1 row)
                              │             └─mirror──► Threshold  (rate * N expression)
                              └─mirror──► Result       (preAggregateCondition: rate > SUBQUERY(Threshold))
                                                        ← no where on subquery (scalar)
```
Note: Result mirrors `PerKeyRate`, not `GlobalAvg`/`Threshold`, because those derive from `PerKeyRate`. Putting the condition on `PerKeyRate` itself would create a cycle.

**Pattern 3 — cycle avoidance**
If table B is derived from A, you cannot add to A a condition that references B. Solution: create a sibling mirror of A and add the condition there.
```
A ──mirror──► B  (derived — cannot be referenced in A's own condition)
A ──mirror──► A_filtered  ← add the condition here; A_filtered and B are siblings, no cycle
```

**Pattern 4 — anti-join / NOT IN ("rows whose key is NOT in another set")**
For exclusion queries — *"customers who never returned anything"*, *"products never ordered"* — do
**not** use a literal negated `ONE_OF`. Express it as a **SUBQUERY condition with `negated: true`**:
build the table that produces the excluded key set, then filter the base table where its key is `ONE_OF`
that subquery's column, negated.
**⚠️ Known SQL generation bug:** on databases that forbid `SELECT *` in `IN (...)` subqueries,
StyleBI wraps the subquery as `select * from (...)`, which causes a syntax error on those databases.
Fallback: use a **left join + IS NULL mirror** instead —
left-join the base table with the exclusion table on the key, then add a mirror filtering `exclusion_key IS NULL`.
```
Customers ─────────────────────────────────► base
Returns ──mirror──► ReturnedCustomerIds  (DistinctCount / group by customer_id → the set to exclude)
Customers ──mirror──► NeverReturned
    preAggregateCondition: customer_id ONE_OF SUBQUERY(ReturnedCustomerIds.customer_id), negated:true
```
```json
{ "tableName": "NeverReturned", "tableType": "mirror table", "baseTables": ["Customers"],
  "preAggregateCondition": [{
    "type": "condition",
    "condition": {
      "field": "CUSTOMER_ID",
      "operation": "ONE_OF",
      "negated": true,
      "values": [{
        "type": "SUBQUERY",
        "subQuery": { "subQueryName": "ReturnedCustomerIds", "inSubQueryColumn": "CUSTOMER_ID" }
      }]
    }
  }]
}
```
Here the subquery is a **set** (many rows), and the match is membership — so `where` is omitted (the
operand is the whole column), and `negated: true` flips IN to NOT IN.

**Pattern 4b — CORRELATED anti-join ("not in a set that depends on the row")**
When the excluded set is **not global** but depends on an attribute of each base row — *"customers never
served by a salesperson **of their own region**"*, *"products never sold **in their own category's**
top store"* — the subquery must be **correlated**: add a `where` to the SUBQUERY value mapping a column
of the subquery table to the current row's column. The subquery is then filtered **per base row**, and
`ONE_OF … negated:true` becomes a correlated `NOT EXISTS`.

**Do the correlation with the subquery `where` — NOT by promoting it to a join key on a finer-grain
fact table.** Encoding "same region" as a join condition between two detail tables (e.g.
`purchases.region = sales.region AND purchases.product = sales.product`) changes the grain and the
question (see "pick the grain" at the top of this section); it is the classic way to make an anti-join
silently return empty. Keep the base table coarse and put the correlation in `where`.

Worked example — *"Which customers have never purchased orders processed by salespeople in the same region as themselves?"* (customers never served by a salesperson
of their own region). The salesperson who **handles an order** is responsible for its goods, so this is
answerable at the **order grain** — no `ORDER_DETAILS`/`PRODUCTS`:
```
SALES_EMPLOYEES ⋈ ORDERS (on EMPLOYEE_ID) ─► Served   (O_CUST_ID, SE_REGION)   [one row per customer×serving-region]
CUSTOMERS ──mirror──► Result
    preAggregateCondition: CUSTOMER_ID ONE_OF SUBQUERY(Served.O_CUST_ID
        WHERE Served.SE_REGION = CUSTOMERS.REGION_ID), negated:true
```
```json
{ "tableName": "Result", "tableType": "mirror table", "baseTables": ["CUSTOMERS"],
  "preAggregateCondition": [{
    "type": "condition",
    "condition": {
      "field": "CUSTOMER_ID",
      "operation": "ONE_OF",
      "negated": true,
      "values": [{
        "type": "SUBQUERY",
        "subQuery": {
          "subQueryName": "Served",
          "inSubQueryColumn": "ORD.O_CUST_ID",
          "where": { "subQueryColumn": "SE.SE_REGION", "currentTableColumn": "REGION_ID" }
        }
      }]
    }
  }]
}
```
The `where` makes membership **per-row**: for each customer, the subquery is filtered to salespeople in
*that customer's* region, so the condition reads "customer was served by an own-region salesperson",
and `negated:true` keeps those who never were. Contrast Pattern 4, where `where` is omitted and the
excluded set is identical for every row. `subQueryColumn` is the column **inside** the subquery table
to match (use the source-field-reference form per Rule 5 if it has no alias); `currentTableColumn` is
the bare column on the table the condition sits on.

**Left / inner / outer join — which to use.**
- **inner** — keep only rows present on both sides. Default for dimensional lookups where the key is
  guaranteed to exist (orders → products on product_id).
- **left** — keep all rows of the left table, filling unmatched right columns with null. **Use whenever
  the right side may be absent and those rows must survive** — e.g. *"all orders, with returns if any"*,
  *"all customers, with their order totals (including customers who never ordered)"*. An `inner` join
  here silently drops the unmatched rows and skews counts/averages. After a left join, a right-side
  measure aggregates with nulls treated per the formula (e.g. `Sum` ignores null → 0-equivalent), so
  guard rates with the divide-by-zero check shown in the expression-column section.
- **right** / **full** — symmetric / both-sides-kept variants; rare. **cross** — Cartesian product (see
  set/cross-join notes); use only deliberately.
- Anti-membership ("rows with no match on the other side") — prefer the **Pattern 4 subquery condition**
  above; fall back to left-join + IS NULL only when the database rejects `SELECT *` in IN subqueries (see Pattern 4 ⚠️).

### Critical rules for create_worksheet_table

Violating any of these causes silent wrong results or hard-to-diagnose errors.

**Rule 1 — One call, all tables in dependency order.**
ALL tables must be in a SINGLE `createTablesRequest` array. Sequential calls each start a fresh worksheet — tables from a prior call disappear. Dependency order: physical tables → join tables → mirror tables (each after its base).

**Rule 2 — `baseTables` is always an array.**
For join and mirror tables always use `"baseTables": ["TableName"]` (array, even for a single base). The singular `"baseTable"` string is not a valid parameter and fails silently.

**Rule 3 — use alias to disambiguate; join keys use the bare column name (no table prefix).**
Keep all columns visible and use `fieldConfigs` in `create_viewsheet` to control which columns appear in the chart binding. Use `alias` (e.g., `"alias": "O_CUSTOMER_ID"`) to disambiguate same-name columns across physical tables.

Join keys use the column name **without the table prefix**. The prefix before `"."` in a response column is the source table name — not part of the column name. Even when a join or mirror table's response column appears as `"ORDERS.O_CUSTOMER_ID"`, the `joinPaths` key is `"O_CUSTOMER_ID"`. This applies regardless of whether the base table is physical, a join, or a mirror.

**Rule 3b — When joining a detail table to an aggregate-value table, also join on the aggregate's output column.**
This rule applies ONLY to a specific pattern: you have a detail table (e.g. all orders) and a per-group aggregate table whose output value is itself a filter criterion (e.g. MAX(ORDER_DATE) per customer). In this case, joining on the group-by key alone (e.g. customer_id) is insufficient — it pairs each aggregate row with ALL detail rows for that group. You must add a second `JoinPath` on the aggregate's output column (e.g. `ORDER_DATE = MAX_DATE`) so that only the detail rows matching the aggregate value are kept.

This does NOT apply to ordinary dimensional joins (e.g. orders → customers on customer_id, orders → products on product_id) — those have a single, correct join key and need no extra condition.

**Rule 4 — `postAggregateCondition` field = original source column, not alias.**
When a mirror has both `aggregateInfo` and `postAggregateCondition` (HAVING semantics), the condition's `field` must be the source column (e.g., `"Query1.CATEGORY_ID"`), not the computed alias (e.g., `"cust_cat_count"`). Include `aggregateFormula` to name the aggregate function.
```json
✅ { "field": "Query1.CATEGORY_ID", "operation": "EQUAL_TO", "aggregateFormula": "DistinctCount", "values": [...] }
❌ { "field": "cust_cat_count", "operation": "EQUAL_TO", "aggregateFormula": "DistinctCount", "values": [...] }
```

**Rule 5 — `inSubQueryColumn` format: alias only (no table prefix), or source field if no alias.**
- Explicit alias set (e.g. `"alias": "total_cats"`) → use `"total_cats"` — the bare alias, **no table prefix**.
- No alias set → use the source field reference (e.g. `"CATEGORIES.CATEGORY_ID"`).
- ❌ Never use a table-qualified alias like `"AllCatCount.total_cats"` — that form is not recognised and the subquery condition is silently ignored.

**Rule 6 — Combine `aggregateInfo` + `postAggregateCondition` on the same mirror (preferred).**
The cleanest pattern for "HAVING aggregate = subquery scalar": put both `aggregateInfo` AND `postAggregateCondition` on the **same** mirror. This makes it the final table, which is automatically set as the worksheet's primary (binding) table and so is used for visualization directly. Only create an extra Result mirror when cycle avoidance is required (see Pattern 3).

**Rule 7 — Use clean (no-prefix) column names in `fieldConfigs`.**
`create_viewsheet` exposes available worksheet columns with **clean names** (all dot-prefixes stripped), regardless of how they appear in the `create_worksheet_table` response. Always reference fields by their bare column name — e.g. `"field": "COMPANY_NAME"`, not `"field": "CUSTOMERS.COMPANY_NAME"`. If `fieldConfigs` receives a dot-prefixed name, it gets a 400 "Unknown field" error listing the available clean names. Use those listed names directly.

---

### Worked example — categories with return rate > 2× global average

Query: *"哪些商品类别的退货率超过整体平均退货率的两倍？"*

Plan: CATEGORIES ─inner─ PRODUCTS ─inner─ ORDER_DETAILS ─**left**─ RETURNS
→ aggregate to category totals → compute per-category rate → compute global average
→ double it → filter categories exceeding the threshold.

> **All steps below belong in a single `create_worksheet_table` call.** The steps are presented separately to show the dependency chain. Combine all table definitions into one `createTablesRequest` array in this order (physical → join → mirrors) before calling the tool.

---

**Step 1 — physical tables (batched, no dependencies)**

```json
{ "createTablesRequest": [
  { "tableName": "CATEGORIES", "tableType": "physical table",
    "physicalSource": { "datasourcePath": "orders", "schema": "SA", "tableName": "CATEGORIES" },
    "columns": [
      { "name": "CATEGORY_ID",   "type": "integer" },
      { "name": "CATEGORY_NAME", "type": "string"  }
    ]
  },
  { "tableName": "PRODUCTS", "tableType": "physical table",
    "physicalSource": { "datasourcePath": "orders", "schema": "SA", "tableName": "PRODUCTS" },
    "columns": [
      { "name": "PRODUCT_ID",  "type": "integer" },
      { "name": "CATEGORY_ID", "alias": "P_CATEGORY_ID", "type": "integer" }
    ]
  },
  { "tableName": "ORDER_DETAILS", "tableType": "physical table",
    "physicalSource": { "datasourcePath": "orders", "schema": "SA", "tableName": "ORDER_DETAILS" },
    "columns": [
      { "name": "PRODUCT_ID", "alias": "OD_PRODUCT_ID",  "type": "integer" },
      { "name": "ORDER_ID",   "alias": "OD_ORDER_ID",    "type": "integer" },
      { "name": "QUANTITY",   "alias": "order_QUANTITY", "type": "integer"  }
    ]
  },
  { "tableName": "RETURNS", "tableType": "physical table",
    "physicalSource": { "datasourcePath": "orders", "schema": "SA", "tableName": "RETURNS" },
    "columns": [
      { "name": "PRODUCT_ID", "alias": "R_PRODUCT_ID",   "type": "integer" },
      { "name": "ORDER_ID",   "alias": "R_ORDER_ID",     "type": "integer" },
      { "name": "QUANTITY",   "alias": "return_QUANTITY", "type": "integer"  }
    ]
  }
]}
```

---

**Step 2 — join all four (Query1) — left join on RETURNS to keep orders with no returns**

```json
{ "tableName": "Query1", "tableType": "relational join table",
  "baseTables": ["CATEGORIES", "PRODUCTS", "ORDER_DETAILS", "RETURNS"],
  "joinPaths": [
    { "leftTable": "CATEGORIES",    "leftKey": "CATEGORY_ID",   "rightTable": "PRODUCTS",      "rightKey": "P_CATEGORY_ID", "joinType": "inner", "joinOperator": "=" },
    { "leftTable": "PRODUCTS",      "leftKey": "PRODUCT_ID",    "rightTable": "ORDER_DETAILS", "rightKey": "OD_PRODUCT_ID", "joinType": "inner", "joinOperator": "=" },
    { "leftTable": "ORDER_DETAILS", "leftKey": "OD_ORDER_ID",   "rightTable": "RETURNS",       "rightKey": "R_ORDER_ID",    "joinType": "left",  "joinOperator": "=" }
  ]
}
```

---

**Step 3 — mirror Query1, group by category, sum quantities (Category_totals)**

```json
{ "tableName": "Category_totals", "tableType": "mirror table", "baseTables": ["Query1"],
  "aggregateInfo": {
    "groups": [
      { "fieldName": "Query1.CATEGORY_ID"   },
      { "fieldName": "Query1.CATEGORY_NAME" }
    ],
    "aggregates": [
      { "fieldName": "Query1.order_QUANTITY",  "formula": "Sum", "alias": "total_ordered"  },
      { "fieldName": "Query1.return_QUANTITY", "formula": "Sum", "alias": "total_returned" }
    ]
  }
}
```
Response columns: `Query1.CATEGORY_ID`, `Query1.CATEGORY_NAME`, `total_ordered`, `total_returned`.
(Group-by columns keep the dot-prefix from the base join table; aggregate aliases are clean.)

---

**Step 4 — mirror Category_totals, add return_rate expression (Category_return_rate)**

Cannot add expressionColumns to Category_totals (it has aggregateInfo). Mirror it first.

```json
{ "tableName": "Category_return_rate", "tableType": "mirror table", "baseTables": ["Category_totals"],
  "expressionColumns": [{
    "name": "return_rate", "type": "double",
    "expression": "!field['Category_totals.total_ordered'] ? 0 : field['Category_totals.total_returned'] / field['Category_totals.total_ordered']"
  }]
}
```
Response columns: `Category_totals.CATEGORY_ID`, `Category_totals.CATEGORY_NAME`, `Category_totals.total_ordered`, `Category_totals.total_returned`, `return_rate`.
(Mirroring Category_totals re-prefixes its columns with "Category_totals."; expression columns stay clean.)

---

**Step 5 — mirror Category_return_rate, compute global average with no groupBy (Global_avg)**

`groups: []` (empty, not omitted) + `aggregates` → single-row result.

```json
{ "tableName": "Global_avg", "tableType": "mirror table", "baseTables": ["Category_return_rate"],
  "aggregateInfo": {
    "groups": [],
    "aggregates": [
      { "fieldName": "Category_return_rate.return_rate", "formula": "Average", "alias": "global_avg" }
    ]
  }
}
```
Response columns: `global_avg`. One row — the overall average return rate across all categories.

---

**Step 6 — mirror Global_avg, compute 2× threshold (Threshold)**

```json
{ "tableName": "Threshold", "tableType": "mirror table", "baseTables": ["Global_avg"],
  "expressionColumns": [{
    "name": "threshold", "type": "double",
    "expression": "field['Global_avg.global_avg'] * 2"
  }]
}
```
Response columns: `Global_avg.global_avg`, `threshold`. One row.
(Mirroring Global_avg re-prefixes its "global_avg" column; expression column "threshold" stays clean.)

---

**Step 7 — mirror Category_return_rate with subquery condition (Result)**

`Threshold` derives (indirectly) from `Category_return_rate`, so we cannot put this condition on `Category_return_rate` itself — that would be a cycle. Mirror it fresh.

No `where` on the subquery: `Threshold` is a global scalar (one row), so no row-matching is needed.

```json
{ "tableName": "Result", "tableType": "mirror table", "baseTables": ["Category_return_rate"],
  "preAggregateCondition": [{
    "field": "Category_return_rate.return_rate",
    "operation": "GREATER_THAN",
    "negated": false,
    "values": [{
      "type": "SUBQUERY",
      "subQuery": {
        "subQueryName": "Threshold",
        "inSubQueryColumn": "Threshold.threshold"
      }
    }]
  }]
}
```

**Result** contains only the categories whose return rate exceeds twice the global average — ready to bind as a chart or table.
