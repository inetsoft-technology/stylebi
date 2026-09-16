# Key detection — inferring relationships across tables

Read this before calling `save_relationships`. It explains how to reason about primary and foreign key relationships from the batch of schemas you collected during per-table annotation, and shows the exact shape that `save_relationships` expects.

## When to run

After all per-table annotation passes are complete. You now have the full schema for every table in the datasource. Reason across all of them at once to detect cross-table key relationships.

If the datasource has only one table, skip this step entirely — no cross-table relationships are possible.

## Identifying primary keys

A column is a strong primary-key candidate when all three are true:
1. `is_unique: true` in its statistics
2. `null_ratio` is 0 or near 0
3. The column name or annotation indicates an identifier (ends in `_id`, `_key`, `_no`, `_num`, `_code`; or the annotation calls it a surrogate key / unique identifier)

If a table has no single-column primary key, look for composite keys (two columns together that uniquely identify rows). Only assert a composite key when the data strongly supports it.

If the domain context (`appDomain`) provides domain-specific guidance (e.g. in Healthcare, `patient_id` is clearly a primary key), treat that as confirmation.

## Identifying foreign key relationships

A column is a foreign-key candidate pointing to another table's primary key when:
- Its name contains a suffix like `_id`, `_key`, `_code`, `_no`, `_num`
- The annotation indicates it references another entity (e.g. "references the customer table")
- `is_unique: false` (values repeat — consistent with many rows referencing the same parent row)
- `null_ratio` is low (most rows have a valid reference)
- A likely match exists in another table: same or similar column name in that table, with `is_unique: true`

Name-matching heuristics (in order of confidence):
1. Exact match: `payment.customer_id` → `customer.customer_id` (same name, target is unique)
2. Stem match: `payment.rental_id` → `rental.rental_id` (foreign table name embedded in column name)
3. Semantic match from annotation: a column annotated as "references the inventory item" → `inventory.inventory_id`

Only assert a relationship when the evidence is reasonably strong. Err on the side of fewer high-confidence relationships over many speculative ones.

## The `OsiRelationship` shape

Each relationship object passed to `save_relationships` must match the `OsiRelationship` interface:

```typescript
interface OsiRelationship {
  name: string;         // A human-readable label for the relationship, e.g. "payment_to_rental"
  from: string;         // The child table (the one that holds the foreign key), e.g. "payment"
  to: string;           // The parent table (the one that holds the primary key), e.g. "rental"
  from_columns: string[]; // Column(s) in the child table that are the foreign key, e.g. ["rental_id"]
  to_columns: string[];   // Column(s) in the parent table that are the primary key, e.g. ["rental_id"]
}
```

`name` is required and should be descriptive. A relationship may also optionally carry an `ai_context` description (inherited from the `AIContextAble` interface), but it is not required and the examples below omit it. `from_columns` and `to_columns` are arrays to support composite keys, but single-column keys are the common case.

The `save_relationships` tool takes:
```json
{
  "database": "<datasource path>",
  "relationships": [ <OsiRelationship>, ... ]
}
```

## Worked example

**Tables (abbreviated schemas from the annotation passes):**

```
rental:
  rental_id  — integer, is_unique: true, null_ratio: 0   → PRIMARY KEY
  customer_id — integer, is_unique: false, null_ratio: 0
  inventory_id — integer, is_unique: false, null_ratio: 0
  staff_id — integer, is_unique: false, null_ratio: 0

customer:
  customer_id — integer, is_unique: true, null_ratio: 0  → PRIMARY KEY
  store_id — integer, is_unique: false, null_ratio: 0

payment:
  payment_id  — integer, is_unique: true, null_ratio: 0  → PRIMARY KEY
  customer_id — integer, is_unique: false, null_ratio: 0
  rental_id   — integer, is_unique: false, null_ratio: 0  (0.03 null_ratio — some payments not linked to a rental)
  staff_id    — integer, is_unique: false, null_ratio: 0
```

**Reasoning:**

- `rental.customer_id` → matches `customer.customer_id` (unique in customer). Clear FK.
- `payment.customer_id` → matches `customer.customer_id`. Clear FK.
- `payment.rental_id` → matches `rental.rental_id` (unique in rental). Low null_ratio despite ~3% nulls — still a valid FK (some payments may not tie to a specific rental record).
- `rental.staff_id`, `payment.staff_id` → both reference `staff.staff_id` if a `staff` table is in the datasource. Omit if `staff` was not in the annotation targets list.
- `rental.inventory_id` → references `inventory.inventory_id` if an `inventory` table is present.

**`relationships` array to pass to `save_relationships`:**

```json
[
  {
    "name": "rental_to_customer",
    "from": "rental",
    "to": "customer",
    "from_columns": ["customer_id"],
    "to_columns": ["customer_id"]
  },
  {
    "name": "payment_to_customer",
    "from": "payment",
    "to": "customer",
    "from_columns": ["customer_id"],
    "to_columns": ["customer_id"]
  },
  {
    "name": "payment_to_rental",
    "from": "payment",
    "to": "rental",
    "from_columns": ["rental_id"],
    "to_columns": ["rental_id"]
  }
]
```

## Tool call

```json
{
  "database": "/datasources/sakila",
  "relationships": [
    { "name": "rental_to_customer", "from": "rental", "to": "customer", "from_columns": ["customer_id"], "to_columns": ["customer_id"] },
    { "name": "payment_to_customer", "from": "payment", "to": "customer", "from_columns": ["customer_id"], "to_columns": ["customer_id"] },
    { "name": "payment_to_rental",   "from": "payment", "to": "rental",   "from_columns": ["rental_id"],  "to_columns": ["rental_id"]  }
  ]
}
```

The tool returns `{ count, summary }`. If it errors, check that each object has all five required fields (`name`, `from`, `to`, `from_columns`, `to_columns`) and that the `relationships` array is non-empty.
