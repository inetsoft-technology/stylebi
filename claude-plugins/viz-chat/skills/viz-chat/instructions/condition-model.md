# Condition model (filters) for apply_filter

Read this before calling `apply_filter`. The tool commits a `conditionModel` to the active chart and REPLACES any existing filter — each call sends the whole model (no incremental merge). It returns the refreshed data sample.

## Build-a-filter loop

1. `lookup_column_values(columnName)` — get the real distinct values of the column you want to filter on (≤200, flags truncation). Use these so you filter on values that actually exist.
2. Build a `conditionModel` (below).
3. `apply_filter({ conditionModel })` — commits it to the active chart and returns the refreshed rows.

## conditionModel shape

```ts
{
  baseConditions: ConditionNode[],       // row filters — applied BEFORE aggregation (SQL WHERE)
  aggregateConditions: ConditionNode[]   // filters on aggregated values (SQL HAVING)
}
```

Both arrays are required — set the one you are not using to `[]`. Most filters use `baseConditions`; use `aggregateConditions` only when filtering on an aggregate value (e.g. "regions whose Sum(Sales) > 1000").

## ConditionNode — leaf or group

A node is one of two shapes, discriminated by `type`:

**Leaf** (a single condition):
```ts
{ type: "condition", junction?: "and" | "or", condition: Condition }
```

**Group** (nested conditions, for precedence):
```ts
{ type: "group", junction?: "and" | "or", items: ConditionNode[] }
```

`junction` (lowercase `"and"` / `"or"`) joins a node to its **previous sibling**. The first node in a list omits it.

## Condition

```ts
{
  field: string,                 // column name
  operation: ConditionOperation, // see enum below
  values: ConditionValue[],      // operands
  negated: boolean,              // true = NOT this condition (required)
  aggregateFormula?: string,     // when filtering an aggregate (e.g. "Sum")
  secondaryField?: string,
  nOrP?: number,
  dateGroupLevel?: string,
  equal?: boolean                // inclusive bound for LESS_THAN / GREATER_THAN
}
```

**Operations** (`ConditionOperation`): `EQUAL_TO`, `ONE_OF`, `LESS_THAN`, `GREATER_THAN`, `BETWEEN`, `STARTING_WITH`, `CONTAINS`, `LIKE`, `NULL`, `DATE_IN`.

- `ONE_OF` — `values` holds the allowed set.
- `BETWEEN` — `values` holds two operands (low, high).
- `LESS_THAN` / `GREATER_THAN` — set `equal: true` for `<=` / `>=`.
- `NULL` — no operand; use `negated: true` for "is not null".

## ConditionValue — an operand

```ts
{ type: "VALUE" | "EXPRESSION" | "FIELD" | "SESSION_DATA", value: any }
```
- `VALUE` — a literal (numbers as JSON numbers, not strings).
- `FIELD` — another column's value.
- `EXPRESSION` — a computed expression.
- `SESSION_DATA` — a session variable.

## Worked examples

**Single — Sales > 50:**
```json
{ "baseConditions": [
  { "type": "condition", "condition": {
      "field": "Sales", "operation": "GREATER_THAN", "negated": false,
      "values": [{ "type": "VALUE", "value": 50 }] } }
],
  "aggregateConditions": [] }
```

**AND — Sales > 50 AND Region = "East":**
```json
{ "baseConditions": [
  { "type": "condition", "condition": {
      "field": "Sales", "operation": "GREATER_THAN", "negated": false,
      "values": [{ "type": "VALUE", "value": 50 }] } },
  { "type": "condition", "junction": "and", "condition": {
      "field": "Region", "operation": "EQUAL_TO", "negated": false,
      "values": [{ "type": "VALUE", "value": "East" }] } }
],
  "aggregateConditions": [] }
```

**ONE_OF — Region in {East, West}:**
```json
{ "baseConditions": [
  { "type": "condition", "condition": {
      "field": "Region", "operation": "ONE_OF", "negated": false,
      "values": [{ "type": "VALUE", "value": "East" }, { "type": "VALUE", "value": "West" }] } }
],
  "aggregateConditions": [] }
```

**BETWEEN — Sales between 50 and 100:**
```json
{ "baseConditions": [
  { "type": "condition", "condition": {
      "field": "Sales", "operation": "BETWEEN", "negated": false,
      "values": [{ "type": "VALUE", "value": 50 }, { "type": "VALUE", "value": 100 }] } }
],
  "aggregateConditions": [] }
```

**Nested group — Sales > 50 AND (Region = "East" OR Region = "West"):**
```json
{ "baseConditions": [
  { "type": "condition", "condition": {
      "field": "Sales", "operation": "GREATER_THAN", "negated": false,
      "values": [{ "type": "VALUE", "value": 50 }] } },
  { "type": "group", "junction": "and", "items": [
      { "type": "condition", "condition": {
          "field": "Region", "operation": "EQUAL_TO", "negated": false,
          "values": [{ "type": "VALUE", "value": "East" }] } },
      { "type": "condition", "junction": "or", "condition": {
          "field": "Region", "operation": "EQUAL_TO", "negated": false,
          "values": [{ "type": "VALUE", "value": "West" }] } }
  ] }
],
  "aggregateConditions": [] }
```

## Highlight (mark, don't remove) and pre-aggregated worksheets

- **Highlight vs filter.** A HAVING-style condition that should only *visually mark* matching rows
  **without removing them** is StyleBI's **highlight** — express it as an `aggregateConditions` entry
  here (e.g. mark regions where `Sum(Sales) > 1000`), not as a worksheet `having` (which would *remove*
  rows). A condition that must actually drop rows from the aggregated result stays in the worksheet
  `having` (see `worksheet-construction.md`).
- **Pre-aggregated worksheet columns.** When the worksheet already did the group/aggregate (so the
  chart binds those columns with `aggregateFormula: "none"`), a highlight that was originally a HAVING
  condition now applies to an already-aggregated column with no further aggregation — so it **degrades
  to a plain `baseCondition`**: put it in `baseConditions` **without** an `aggregateFormula`.

If StyleBI rejects a model, it returns an error message — surface it, fix the model, and retry.
