# Hierarchy detection — identifying drill-down levels within a table

Read this when building a table's annotation for `save_table_annotation`. It explains how to detect **drill-down hierarchies** among a single table's columns and the exact shape to put on the annotation's optional `hierarchies` field.

## What a hierarchy is

A hierarchy is an ordered group of columns forming a **coarse-to-fine roll-up / drill-down path** *within one table*, where each level fully contains the next (a one-to-many containment relationship). Order the levels **coarsest first**.

Why it matters: once stored, StyleBI can use the hierarchy to let a user **drill up/down directly on a chart or crosstab** — the interaction is native and does not go through the model. So detecting it at annotation time is what makes one-click drill possible later.

## When to detect

During the per-table pass (annotation Step 4), after you have the table's `schema` from `get_annotation_inputs`. Hierarchies are **single-table** — do not span tables (cross-table paths are foreign-key relationships, handled by `save_relationships`, not here).

## How to detect

Decide from the **meaning** of the columns, using your domain knowledge of how such attributes nest:

- **Work out what each column really is from its name and data type, using your domain knowledge — not the annotation alone.** Some annotations are generic, code-filled placeholders that misdescribe a column (e.g. a column literally named `state` annotated "Row state; enum" is really a geographic state). Judge by the column name and type, not a vague annotation.
- Geographic, temporal, organizational, and product-classification columns are the common hierarchy members. `country`/`state`/`city`, `year`/`quarter`/`month`, `division`/`department`/`team` nest because of what they *mean*, and you already know how they roll up.
- Ask: does each level **fully contain** the next (a one-to-many relationship)? One country contains many states; one state belongs to exactly one country. If there is no such containment, it is not a hierarchy.
- **Include every intermediate level — do not skip.** If the table has region, state, city, zip, and address, the hierarchy is `region → state → city → zip → address`, not `region → address`. Don't stop at the coarsest and finest columns and drop the middle ones.
- **Containment is a semantic judgement — don't infer it from statistics.** Cardinality (e.g. `distinct_count`) does not establish a hierarchy: two unrelated columns can have increasing distinct counts without any containment. Decide from what the columns *mean*.
- **Only annotatable columns can be levels.** A level must be a column in `toAnnotate`/`schema`. A column that appears in the data but is NOT in `toAnnotate` cannot be a level — the backend would filter it out. When a needed *intermediate* level is missing from the column set, include the levels you do have and note the gap in the hierarchy `description`; do not silently pretend the chain is complete.

## Self-check before you save — do this every time

Detection is easy to under-do when you are annotating many columns at once. Before finalizing the annotation, run this explicit self-check so a valid level is never dropped by oversight:

1. **Enumerate the candidates.** List every column in `toAnnotate` that is geographic (`country`/`region`/`state`/`city`/`zip`/`address`), temporal (`year`/`quarter`/`month`/`day`/`date`), organizational (`division`/`department`/`team`), or product-classification (`category`/`subcategory`/`product`).
2. **Group them into containment chains** and order each chain coarsest → finest.
3. **Include EVERY available level in the chain — do not stop early.** In particular, do NOT drop the finest level just because it is high-cardinality or unique. A unique leaf like `address` is still a valid drill target and MUST be included (e.g. `region → state → address`, not `region → state`).
4. **Justify every omission.** For each candidate column you left OUT of all hierarchies, state the reason to yourself. If the only reason is "it's unique", "high cardinality", or "rarely drilled" — that is NOT a valid reason; put it back. The only valid reasons to omit a candidate are: it is not in `toAnnotate` (not annotatable), or it genuinely has no containment relationship with the others.

### Positive examples
- Geography: `country → state → city → address`
- Time: `year → quarter → month → day`
- Organization: `division → department → team`
- Product: `category → subcategory → product`

### Negative examples (do NOT emit these)
- Unrelated columns that merely co-occur (`price`, `color`, `weight`) — no containment.
- Two columns with no roll-up relationship.
- A single column — a hierarchy needs at least two levels.

### Anti-pattern — dropping a valid level (the common mistake)
Table has `region_id`, `state`, `address` in `toAnnotate`. Emitting `["region_id","state"]` and dropping `address` because it is unique / "rarely drilled" is WRONG — the correct hierarchy is `["region_id","state","address"]`. Include the unique finest level. (If `city` existed in the data but not in `toAnnotate`, it is correctly excluded — note that gap in the `description` — but `address`, being annotatable, must be kept.)

## The shape

Put a `hierarchies` array on the annotation object you pass to `save_table_annotation` (alongside `name`, `ai_context`, `fields`). Each entry:

```typescript
interface OsiHierarchy {
  name: string;          // human-readable, e.g. "Geography" or "Time"
  levels: string[];      // >= 2 real column names, COARSEST first, e.g. ["country","state","city"]
  description?: string;  // optional
}
```

Constraints (the backend also enforces these — a violated group is dropped, not an error):
- **At least 2 levels.** Fewer is discarded.
- **Real column names only.** Every level must be an exact column name from `get_annotation_inputs`; hallucinated names are filtered out (and if that leaves fewer than 2, the whole group is dropped).
- **No duplicate column within one group.**
- A table may have **zero, one, or several** hierarchies. If none apply, omit `hierarchies` entirely — do not send an empty array or invent one.

## Worked example

Table `locations` with columns:

```
country  — varchar
state    — varchar
city     — varchar
address  — varchar
```

Geographic columns that you know nest as country ⊃ state ⊃ city ⊃ address → one clear hierarchy. Add to the annotation object:

```json
{
  "name": "locations",
  "ai_context": { "instructions": "..." },
  "fields": [ ... ],
  "hierarchies": [
    { "name": "Geography", "levels": ["country", "state", "city", "address"] }
  ]
}
```
