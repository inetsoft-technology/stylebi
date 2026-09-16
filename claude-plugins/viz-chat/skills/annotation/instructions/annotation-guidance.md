# Annotation guidance

Read this before building an annotation object for `save_table_annotation`. It distills the three reasoning stages the backend agent pipeline runs (semantic profiling, business context enrichment, and annotation synthesis) into guidance for how you should reason when annotating a table.

## Evidence hierarchy

Use all bundle fields — do not skip any:

| Bundle field | What to use it for |
|---|---|
| `appDomain` | Primary context. Guides semantic type inference and business vocabulary. If the domain is "E-commerce", prefer "customer lifetime value" framing; if "Healthcare", prefer clinical terminology. |
| `statistics` per column | `is_unique` + low `null_ratio` → likely an identifier / primary key. Repeated categoricals → dimension. `min < 0` → possibly a financial amount (balance, adjustment). Integer column whose name contains age, duration, days, months, tenure → temporal duration, not a bare number. |
| `samples` rows | Cross-check value patterns against the column name. A column named `status` with values `['active','inactive']` is categorical. A column named `amount` with float values is a measure. |
| `referenceDocs` | Glossary and doc fragments retrieved for this table. Use exact business definitions and alternative terms found here as synonyms and examples. Do not invent synonyms not present in these docs or derivable from the schema; if none are available, return an empty `synonyms` array. |
| `schema` `semanticTypeCandidates` | Confirmation signal. Use it alongside statistics and samples, not alone. |

## Writing `instructions` (description)

Write 1–2 sentences. Focus on business meaning, not technical detail. Ask: "What does a business analyst need to know to use this correctly?"

**Table-level instructions:** Describe what the table represents, its granularity (one row = one what?), and its main analytical purpose. Example: "Records one completed rental transaction per row, linking a customer to a specific film copy with rental dates and return status. Used to analyze rental volume, customer behaviour, and inventory utilization."

**Column-level instructions:** Describe what the value means and how analysts use it — not what data type it is.

For **numeric measure columns**, be precise about aggregation semantics:
- If the column is a *per-unit price, rate, or ratio* (a value attached to one row; e.g. a list price): describe it as a per-unit figure. Do not call it "revenue", "total", or "sales". Example: "The rental fee charged per rental transaction. Multiply by the number of rentals to derive total rental revenue."
- If the column is an *actual transacted or collected amount* (the money recorded for that specific transaction): describe it as the transacted amount and say it is the basis for totals. Example: "The actual payment amount received for this transaction. Summing this column yields total revenue collected."

Never attach revenue/totals semantics to a unit-price column.

## Synonyms

Only use alternative names found in `referenceDocs.alias_candidates` or clearly implied by the schema itself (e.g. `cust_id` → `customer id`, `customer identifier`). Do not invent synonyms from general knowledge. If no candidates are available, output `synonyms: []` or omit the field.

## Examples

2–4 short analyst-style questions or query descriptions that this table or column answers. If they require specific values (statuses, categories, region names), use only values from `statistics.top_values` or `samples` — do not invent values. If context is too thin to write non-trivial examples, leave the array empty.

Good column example: `["What is the rental fee for this film?", "Filter rentals where amount is above $4.99"]`
Good table example: `["How many rentals were returned late this month?", "Which customers have rented more than 10 films?"]`

## `isDimension`

Ask: "Would a business analyst use this column to **group or slice** data in a report?"

**`true` (dimension):**
- Date, timestamp, year, month, quarter
- Geographic fields (country, city, region, state)
- Categorical fields (status, category, channel, department, product name, customer name)
- Boolean flags
- Human-readable business codes (country_code, status_code) — even if named with `_code`
- Low-cardinality integer codes that represent categories (rating, priority level)

**`false` (measure / non-grouping):**
- Numeric aggregatable metrics (amount, quantity, price, ratio, score, count)
- Surrogate / auto-increment primary keys (order_id, customer_id) — analysts do not group by these; they join on them
- Free-text fields (notes, comments, description)

`isDimension: false` covers two distinct things: genuine **numeric measures** AND **non-grouping non-measures** (surrogate keys, free-text). Only the first is summable. **Word the description to match what the column actually is:** describe a key as a "key/identifier", free-text as "free-text"/"notes" — do NOT use measure verbs (sum, total, amount, aggregate) for a non-numeric key or text field. The verifier checks the description against the data: a column *described* as a summable measure that is non-numeric draws a **warning** (not a hard defect) for review; a description that mentions "primary key / unique identifier" is treated as a key and exempt from the measure check. So: precise wording keeps a string ID or text column clean even though it is `isDimension: false`.

**Edge case — numeric IDs and codes:** A column named `rental_id` with `is_unique: true` is a surrogate key → `isDimension: false`. A column named `rating` with values `['G','PG','PG-13','R','NC-17']` is categorical → `isDimension: true` even though it might look like a score. A column named `store_id` with 2 distinct values is a low-cardinality categorical grouping field → `isDimension: true`.

## `dimensionPrevalence`

Score from `0.00` to `1.00` (two decimal places) based on how often this dimension appears in typical business analyses:

| Range | Meaning |
|---|---|
| `0.80–1.00` | Almost every analysis uses it; core reports cannot be built without it (e.g. `date`, `category`, `store`, `region` in a retail datasource) |
| `0.50–0.79` | Appears frequently in standard reports; analysts know it well (e.g. `customer_type`, `product_name`) |
| `0.20–0.49` | Used in specific scenarios or ad-hoc analyses, not everyday (e.g. `payment_method`, `return_reason`) |
| `0.01–0.19` | Occasionally used as a filter but rarely for grouping (e.g. `country_code` when most data is one country) |
| `0.00` | Always `0.00` when `isDimension` is `false` |

## `confidence`

- `high`: clearly verifiable from schema, samples, and statistics — e.g. a column named `rental_id` with `is_unique: true`.
- `medium`: reasonably inferred but could benefit from human review — e.g. a column named `amount` in a domain with multiple amount types. Also use `medium` when `samples` and `statistics` are empty (table not yet preprocessed) and the inference is based solely on column names, types, and reference docs.
- `low`: inferred from very limited context; shape is ambiguous or name is cryptic. Use `low` when `samples`, `statistics`, and `referenceDocs` are all empty and the column name alone is the only signal.

**Note:** `get_annotation_inputs` is read-only. When a table has not yet been preprocessed, `samples`, `statistics`, and `appDomain` may all be empty — this is expected. Annotate from column names, data types, and any `referenceDocs` present, and calibrate `confidence` accordingly.

## Primary key

Set `primary_key` (a top-level array on `save_table_annotation`'s `annotation` object, e.g. `primary_key: ["id"]`) on every table, using the same identification reasoning as `key-detection.md`'s "Identifying primary keys" section: `is_unique: true`, `null_ratio` near 0, and a name that reads as an identifier.

This is not cosmetic. `planFkLabelJoin` treats a table's `primary_key` as the ONLY structural proof that a join into it is safe (won't multiply rows) — a table with no `primary_key` set can never be the TARGET of an FK-label join (e.g. resolving another table's `customer_id` to this table's `name` column), and the join is rejected silently: no error surfaces anywhere, the caller just gets a worse fallback control. `refresh_table_metadata` sets this automatically from the live database, but a hand-authored `save_table_annotation` call does not — set it explicitly whenever you are not using `refresh_table_metadata`. To correct just the primary key on an already-annotated table without a full re-annotation, patch it per column via `update_column_annotation`'s `isPrimaryKey` flag instead.

## Drill-down hierarchies

A hierarchy in `save_table_annotation.hierarchies` is a drill path over columns of the **same conceptual dimension**, ordered **coarsest → finest**. Its whole purpose is to let an analyst start at a summary level and drill *all the way down to the most detailed level available*. The guiding rule is therefore **completeness**:

**Include every level of the dimension that exists in the table, down to and including the finest one — even when that finest level is unique or high-cardinality.** A level being `is_unique: true`, high-cardinality, or "rarely used for standalone grouping" is **not** a reason to drop it from a hierarchy. Reaching maximum detail is exactly what the deepest drill step is *for*; the leaf level is *supposed* to be the most granular. Do **not** confuse `dimensionPrevalence` (how often a column is used as a *standalone* grouping field) with hierarchy membership — a column can have a low prevalence (even `0.05`) and still be a legitimate, required hierarchy level.

Only exclude a column from a hierarchy when:
- it does **not** belong to that conceptual dimension, or
- it is **redundant** with an adjacent level — a 1:1 alias (e.g. `state_code` vs `state_name`; keep one), or
- including it would **break strict coarse→fine containment** (each finer value must nest under exactly one coarser value).

This is a **general rule for every dimension type**, not just geography. Common patterns — always extend to the finest level that exists in the table:

| Dimension | Levels, coarse → fine (include the finest present) |
|---|---|
| Geography | region → country → state/province → city → postal/ZIP → **street address** |
| Time | year → quarter → month → day → **date/timestamp** |
| Product | category → subcategory → brand → **product name / SKU** |
| Organization | division → department → team → **employee** |
| Account | account type → account group → **account id/number** |

The street address, the individual timestamp, the SKU, the specific employee — these unique/near-unique leaves are the correct **bottom** of their hierarchies, not columns to omit. When unsure whether a granular column is a real drill target, **prefer including it**: an over-complete drill path is a minor annoyance, but a truncated one silently prevents the user from drilling to the detail they asked for.

## Worked example

**Bundle inputs (abbreviated):**

```
table: "rental"
appDomain: "DVD Rental"
toAnnotate: ["rental_id", "rental_date", "inventory_id", "customer_id", "return_date", "staff_id", "last_update"]
preserved: []

statistics (selected):
  rental_id:    is_unique: true, null_ratio: 0, inferred_type: "integer"
  rental_date:  is_unique: false, null_ratio: 0, inferred_type: "timestamp", sample_values: ["2005-05-24 22:53:30", ...]
  customer_id:  is_unique: false, null_ratio: 0, inferred_type: "integer", distinct_count: 599
  return_date:  is_unique: false, null_ratio: 0.03, inferred_type: "timestamp"
  staff_id:     is_unique: false, null_ratio: 0, inferred_type: "integer", distinct_count: 2
  last_update:  is_unique: false, null_ratio: 0, inferred_type: "timestamp"

samples: [{ rental_id: 1, rental_date: "2005-05-24 22:53:30", inventory_id: 367, customer_id: 130,
            return_date: "2005-05-26 22:04:30", staff_id: 1, last_update: "2006-02-15 21:30:53" }, ...]
referenceDocs: []   // none available; synonyms will be empty
```

**Annotation object to pass to `save_table_annotation`:**

```json
{
  "name": "rental",
  "ai_context": {
    "instructions": "Records one completed or in-progress rental transaction per row, linking a customer to a specific inventory item with rental and return timestamps. Used to analyze rental volume, overdue returns, and customer activity.",
    "synonyms": [],
    "examples": [
      "How many rentals were returned late this month?",
      "Which customers have the most active rentals?",
      "What is the average rental duration by staff member?"
    ],
    "confidence": "high"
  },
  "fields": [
    {
      "name": "rental_id",
      "ai_context": {
        "instructions": "Unique surrogate key identifying each rental transaction. Used for joining to payment and other related tables, not for grouping.",
        "synonyms": [],
        "examples": [],
        "confidence": "high"
      },
      "isDimension": false,
      "dimensionPrevalence": 0.00
    },
    {
      "name": "rental_date",
      "ai_context": {
        "instructions": "Timestamp when the rental transaction began. Used to group and trend rental activity over time (daily, weekly, monthly).",
        "synonyms": [],
        "examples": [
          "How many rentals started in May 2005?",
          "Show rental volume by month"
        ],
        "confidence": "high"
      },
      "isDimension": true,
      "dimensionPrevalence": 0.85
    },
    {
      "name": "inventory_id",
      "ai_context": {
        "instructions": "Foreign key referencing the specific physical copy of a film that was rented. Used to join rental transactions to inventory and film tables.",
        "synonyms": [],
        "examples": [],
        "confidence": "high"
      },
      "isDimension": false,
      "dimensionPrevalence": 0.00
    },
    {
      "name": "customer_id",
      "ai_context": {
        "instructions": "Foreign key identifying the customer who rented the item. Used to join to the customer table and analyze per-customer rental history.",
        "synonyms": [],
        "examples": [
          "Which customer has rented the most films?"
        ],
        "confidence": "high"
      },
      "isDimension": false,
      "dimensionPrevalence": 0.00
    },
    {
      "name": "return_date",
      "ai_context": {
        "instructions": "Timestamp when the rented item was returned. Null when the rental is still active. Used to calculate rental duration and identify overdue returns.",
        "synonyms": [],
        "examples": [
          "How many rentals have not been returned yet?",
          "What is the average number of days between rental and return?"
        ],
        "confidence": "high"
      },
      "isDimension": true,
      "dimensionPrevalence": 0.55
    },
    {
      "name": "staff_id",
      "ai_context": {
        "instructions": "Foreign key identifying the staff member who processed the rental. Used to group transactions by staff and analyse workload distribution.",
        "synonyms": [],
        "examples": [
          "How many rentals did each staff member process?",
          "Filter rentals handled by staff ID 1"
        ],
        "confidence": "high"
      },
      "isDimension": true,
      "dimensionPrevalence": 0.35
    },
    {
      "name": "last_update",
      "ai_context": {
        "instructions": "Timestamp of the last modification to this row. Used for data pipeline and ETL auditing, not typically for business analysis.",
        "synonyms": [],
        "examples": [],
        "confidence": "high"
      },
      "isDimension": true,
      "dimensionPrevalence": 0.05
    }
  ],
  "primary_key": ["rental_id"]
}
```

This object is a valid `save_table_annotation` annotation input: it has `name` matching the table, a table-level `ai_context`, one `fields` entry per `toAnnotate` column each with `isDimension` and `dimensionPrevalence`, and `primary_key` naming `rental_id` — the column identified above as `is_unique: true` with `null_ratio: 0`.
