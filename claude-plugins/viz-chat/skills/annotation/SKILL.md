---
name: data-annotation
description: Use when the user wants to annotate a StyleBI datasource — generate AI descriptions, synonyms, dimension/measure flags, and key relationships for tables and columns. Wraps the deterministic annotation MCP tools; Claude does the reasoning.
---

# StyleBI data annotation

Enrich a StyleBI datasource with AI-generated metadata: business descriptions, synonyms, dimension/measure classification, and foreign-key relationships. You do the reasoning; the MCP tools handle reads and writes. Every annotation lands in **AWAITING_APPROVAL** status and is invisible to end users until approved. You can now approve annotations directly (see §Approving and excluding below) — do so after a review pass to let clean tables go live without a portal trip.

## Prerequisites

Logged in to a StyleBI deployment. If the `viz-chat` tools are unavailable (this means NOT authenticated — not that the server is down) or a call returns an auth error, run the `/stylebi-viz-chat:login` flow (it handles the client-specific OAuth steps), then resume the annotation task. Annotation uses the same credentials.

## Workflow

### Step 1 — Pick targets and keep-strategy

1. Call `list_datasources` to confirm which datasource to annotate. If the user named one, match it; if not, ask.
2. Call `list_annotation_targets` with `{ database }` → it returns `{ tables, summary }`. Each table entry has `name`, `table`, `catalog`, `schema`, `tableType`, and `assetData` (sourced from the live StyleBI schema).
3. If the user named a single table, annotate only that one. If they said "annotate the whole datasource", loop through all tables in the list.
4. **Ask the keep-strategy** before annotating (one quick multiple-choice; default in **bold**) — how to treat existing annotations:
   - **Re-annotate pending (default)** — annotate every not-yet-approved column: regenerate anything still `AWAITING_APPROVAL` and fill in `NONE` columns, while leaving `APPROVED` columns untouched. NOTE: to re-annotate columns that are already APPROVED, the table must first be reopened — call `reopen_annotation` after getting explicit confirmation (flips APPROVED → AWAITING_APPROVAL, keeps content, removes it from the live index). Never reopen an approved table without confirming — it undoes a human approval.
   - **Only new** — skip any table that already has annotations; annotate only tables whose status is `NONE`/absent. (= the portal's "keep all".) Determine this from `get_annotation_status` (Step 8's tool) up front: drop already-annotated tables from the loop.

   If the user already implied a choice ("re-do olist", "just the new tables"), honor it without re-asking.

### Step 2 — Per table: prepare (sample + statistics)

Before fetching inputs, call `prepare_table` with `{ database, table, catalog, schema }` once for the table. This samples the live data and computes per-column statistics so both your annotation and the verifier have real evidence (uniqueness, ranges, null ratios, top values) instead of column names alone. It is **non-destructive** — it does not change any existing annotation, only adds samples/statistics.

If `prepare_table` returns `prepared: false` (raw-data export failed), proceed anyway with schema-only annotation, but set `confidence` lower and expect the verifier to warn that statistics are absent.

### Step 3 — Per table: fetch inputs

For each table you will annotate, call `get_annotation_inputs` with `{ database, table, catalog, schema, assetData }` — pass the `catalog`, `schema`, and `assetData` exactly as returned by `list_annotation_targets` for that table. **`catalog` and `schema` are required for the live per-table fetch to resolve correctly** — omitting `schema` when it is non-empty causes a "SampleDataDocId is required!" error on the backend. The bundle contains:
- `schema` — columns with data types and semantic-type hints
- `samples` — sample rows
- `statistics` — per-column stats (`null_ratio`, `is_unique`, `inferred_type`, `min`, `max`, `top_values`, etc.)
- `referenceDocs` — retrieved glossary/doc fragments
- `appDomain` — the business domain context (may be null)
- `toAnnotate` — columns you must annotate
- `preserved` — already-approved columns; do NOT include them in the annotation you save

### Step 4 — Per table: reason and build the annotation object

Read **`instructions/annotation-guidance.md`** before this step. Produce the full annotation object for this table — covering both the table-level `ai_context` and an entry in `fields` for every column in `toAnnotate`. Do not include `preserved` columns.

If applicable, also detect **drill-down hierarchies** among this table's columns (e.g. `country → state → city`) and add them to the annotation object's optional `hierarchies` field — this is what enables native drill up/down on charts later. Read **`instructions/hierarchy-detection.md`** for how to detect them and the exact shape. If no hierarchy applies, omit the field.

### Step 5 — Per table: save

Call `save_table_annotation` with:
```
{
  database,
  table,
  catalog,     // from list_annotation_targets (pass through exactly as returned)
  schema,      // from list_annotation_targets (pass through exactly as returned; REQUIRED for live fetch)
  assetData,   // from list_annotation_targets
  annotation: { ...your annotation object... }
}
```

The server validates the annotation against its Zod schema. If the call returns a validation error, read the reported field issues, fix them in the annotation object, and retry. A **422** specifically means your field names matched no columns — re-check the names against `get_annotation_inputs` and use them exactly. Do not retry more than once on the same error without diagnosing the cause.

Each call saves exactly one table. Do not batch multiple tables into one call.

### Step 6 — Per table: verify and review

After saving, certify the annotation with an independent pass (do not trust the save's own success signal):

1. Call `verify_annotation` with `{ database, table, catalog, schema }`. It returns `{ ok, defects, warnings }` from deterministic checks (integrity + data-refutation against the prepared statistics).
2. Dispatch the **`annotation-reviewer`** subagent for this table. It runs the verifier and `get_annotation_inputs` adversarially and returns a verdict: `reject`, `escalate`, or `low-touch`. Act on it:
   - **`reject`** → call `reject_annotation` with the reviewer's `concerns` as context, then go back to Step 4 and re-annotate this table addressing the concerns. **Cap: 2 auto-correction attempts.** If it still fails after the 2nd attempt, STOP retrying — leave it AWAITING_APPROVAL, record the concerns, and flag it for human review in your final report.
   - **`escalate`** → leave it AWAITING_APPROVAL; in the final report, tell the user this table needs a human judgment call, with the concerns.
   - **`low-touch`** → leave it AWAITING_APPROVAL; note it as verified/clean for quick approval.
3. **Persist the verdict:** call `record_review` with `{ database, table, catalog, schema, disposition, concerns }` for every reviewed table (including `low-touch` with empty concerns) so the portal can show the disposition and offer "Approve all low-touch".

The reviewer never approves or rejects on its own — you act on its verdict. For `escalate` tables, leave the final approval decision to a human; for `low-touch` tables, you can approve via `approve_low_touch` after recording dispositions.

### Step 7 — Whole datasource: infer relationships

After all per-table passes are done, read **`instructions/key-detection.md`**. Using the schemas you collected across the tables, reason about which columns are primary keys and which are foreign keys pointing to another table's primary key. Build a `relationships` array and call `save_relationships` with `{ database, relationships }`.

If the datasource has only one table, skip this step (no cross-table relationships possible).

### Step 8 — Report status

Call `get_annotation_status` with `{}`. It returns `{ statuses, summary }` — per-table AWAITING_APPROVAL / APPROVED counts.

Tell the user:
- How many tables and columns are awaiting approval, broken down by the reviewer verdict: clean/low-touch (ready to approve with `approve_low_touch`), escalated (needs human judgment, with the concerns), and stuck-after-retries (failed verification twice — needs human attention).
- For low-touch tables: offer to run `approve_low_touch` now if they want to make those annotations live immediately, or they can do it from the portal.
- For escalated and stuck tables: they need a human to review in the portal (or resolve the concern first, then approve individually).
- If any table failed, surface the error.

## Rejecting a wrong annotation

When reviewing a saved annotation that is wrong, call `reject_annotation` with `{ database, table, catalog?, schema? }` (pass `catalog`/`schema` exactly as returned by `list_annotation_targets`). This sets the table back to rejected in the review queue and sends it for re-annotation — it does NOT delete the annotation silently or make anything live. After rejecting, you can re-run `get_annotation_inputs` → reason → `save_table_annotation` to replace it with a corrected annotation (which lands back in AWAITING_APPROVAL for you or a human to approve).

## Approving and excluding

The plugin can approve annotations and exclude tables:

- `approve_low_touch { database }` — approves every AWAITING_APPROVAL table whose recorded review disposition is `low-touch`. Run this only AFTER a review pass (`record_review`) has recorded dispositions for all tables. It makes those annotations live to end users.
- `approve_table_annotation { database, table, catalog?, schema? }` — approves one table individually. Use for an `escalate` table after you (or a human) have resolved the concern.
- `reopen_annotation { database, table, catalog?, schema? }` — un-approve a table (APPROVED → AWAITING_APPROVAL, content kept) so it can be re-annotated; confirm first.
- `exclude_table` / `include_table { database, table, catalog?, schema? }` — mark a table off-limits for AI workflows (annotation re-runs, visualization retrieval, vector store), and reverse that. Use `exclude_table` for framework or plumbing tables that should never surface in queries.

**Recommended flow after annotating and reviewing:** run `approve_low_touch` to bulk-approve the clean tables, then leave any `escalate` tables for human judgment (or approve individually once the concern is resolved). Approval makes annotations live — the operator invoking the tool is the gate.

## Notes

- `get_annotation_inputs` is a **read-only** call (it never writes). Running `prepare_table` first (Step 2) populates the `samples`/`statistics` the bundle returns and the verifier checks against. If you skip prepare or it fails, the bundle may contain only `schema`, `referenceDocs`, and empty `samples`/`statistics`/`appDomain` — annotate from column names, data types, and reference docs, and set `confidence` to `medium`/`low` since statistical evidence is absent.
- Always verify after saving (Step 6). The save tool reports what it wrote, but never trust that as certification — `verify_annotation` + the `annotation-reviewer` subagent re-read the persisted doc independently.
- One `save_table_annotation` call per table — no batching.
- Keep `name` in the annotation object exactly equal to the table name returned by `get_annotation_inputs` (the `dataset.table` field in the bundle).
- When re-annotating after a portal rejection: the reviewer may leave a rejection hint on specific columns. These hints are the highest-priority signal — they override statistical inference. Align the new annotation for those columns with the hint. Hints apply only to the column they name; do not generalize them.
- Annotations start in AWAITING_APPROVAL and are invisible to end users until approved. Use `approve_low_touch` (for bulk clean tables) or `approve_table_annotation` (for individual tables) to make them live. Leave `escalate` tables for human judgment unless the concern has been resolved.
- `list_annotation_targets` returns each table's `catalog`, `schema`, `table`, and `assetData` sourced from the live StyleBI schema. Always pass these through to `get_annotation_inputs` and `save_table_annotation` — omitting them when non-empty will cause backend errors.

## Read before you build

- **`instructions/annotation-guidance.md`** — how to write `instructions`, `synonyms`, `examples`; how to decide `isDimension` and `dimensionPrevalence`; worked example of a complete annotation object.
- **`instructions/key-detection.md`** — how to infer primary and foreign keys from the cross-table schema; the exact `OsiRelationship` shape that `save_relationships` expects; worked example.
- **`instructions/hierarchy-detection.md`** — how to detect single-table drill-down hierarchies (coarse → fine) from cardinality and samples; the `OsiHierarchy` shape for the annotation's `hierarchies` field; worked example.
