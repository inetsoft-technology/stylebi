---
description: Annotate a table or datasource — generate AI descriptions, dimension/measure flags, and relationships. Prompts for keep-strategy, sampling, and verification. Usage - /stylebi-viz-chat:annotate [table-or-datasource-name]
---

The user wants to annotate a datasource or table. Run the **data-annotation** skill's workflow (prepare → inputs → annotate → save → verify), but first resolve the target and gather options via a structured prompt. Every annotation lands in AWAITING_APPROVAL — you never make anything live; a human approves in the portal.

## 1. Resolve the target

- If `$ARGUMENTS` is non-empty, treat it as a table name or datasource name:
  - Call `list_datasources`. If `$ARGUMENTS` matches a datasource → scope is that whole datasource.
  - Otherwise call `list_annotation_targets` for the likely datasource and match `$ARGUMENTS` to a table → scope is that one table.
  - If it matches nothing or is ambiguous, list the options and ask which they meant.
- If `$ARGUMENTS` is empty, call `list_datasources` and ask which datasource (and optionally which single table).

Carry each table's `catalog`, `schema`, and `assetData` from `list_annotation_targets` through to every tool call.

## 2. Prompt for options — REQUIRED, use the AskUserQuestion tool

Before annotating, call the **AskUserQuestion** structured-question tool (do NOT just ask in prose) with these questions. Use the **bold** option as the default and let the user accept all at once:

1. **Keep-strategy** — how to treat existing annotations:
   - **Re-annotate pending** (default) — annotate every not-yet-approved column (regenerate `AWAITING_APPROVAL`, fill `NONE`); leave `APPROVED` columns untouched. NOTE: to re-annotate columns that are already APPROVED, the table must first be reopened. When the user's chosen scope includes APPROVED tables, list those tables, get explicit confirmation, then call `reopen_annotation` for each (flips APPROVED → AWAITING_APPROVAL, keeps content, removes it from the live index) BEFORE annotating. Never reopen an approved table without confirming — it undoes a human approval.
   - **Only new** — skip any table that already has annotations; annotate only tables whose status is `NONE`/absent (determine via `get_annotation_status`).
2. **Sample live data** — `prepare_table` before annotating:
   - **Sample + compute stats** (default) — run `prepare_table` so the annotation and verifier have real evidence (uniqueness, ranges, null ratios). Recommended.
   - Skip sampling — annotate from schema + reference docs only; set `confidence` lower.
3. **Verify after saving** — run the review pass:
   - **Verify each table** (default) — after saving, run `verify_annotation` + the `annotation-reviewer` subagent; report verdicts (and, if the user wants, auto-correct rejects with a 2-attempt cap).
   - Skip verification — just save (AWAITING_APPROVAL); the human reviews entirely in the portal.

If the user already stated a preference in their message ("re-do olist with fresh samples, no verify"), honor it and only ask about the unspecified options. If a datasource resolves to many tables, confirm the count ("annotate all N tables?") — via the same AskUserQuestion call or a brief confirm.

## 3. Run the annotation (per in-scope table)

Follow the **data-annotation** skill's per-table workflow, applying the chosen options:
0. If the table is currently APPROVED and the user confirmed re-annotation of approved tables, call `reopen_annotation` { database, table, catalog, schema } first so its columns return under `toAnnotate`.
1. If **Only new**, drop already-annotated tables from the loop first (`get_annotation_status`).
2. If **Sample + compute stats**, call `prepare_table` for the table.
3. `get_annotation_inputs` → reason (see `skills/annotation/instructions/annotation-guidance.md`) → produce the annotation object for the `toAnnotate` columns (never `preserved`).
4. `save_table_annotation` (a 422 means your field names matched no columns — re-check against the inputs and use exact names). An `ANNOTATION_INTEGRITY` error means the guard caught a real defect in what you just generated (e.g. a leaked internal `unresolved:` marker, or an `isDimension`/prose contradiction) — the error names the offending field and the exact problem. Rewrite that field's `ai_context`/`isDimension`/`dimensionPrevalence` to resolve it (never resubmit the same text unchanged) and retry `save_table_annotation`, **2-attempt cap** — mirroring `/review`'s auto-correct pattern. If it still fails after the 2nd attempt, stop, leave the table's prior content in place, and report the field and error to the user instead of retrying further.
5. If **Verify each table**, run `verify_annotation` + dispatch the `annotation-reviewer` subagent; on a `reject` verdict, optionally `reject_annotation` + re-annotate (2-attempt cap), else leave AWAITING_APPROVAL and record the concerns. After the verdict is final, call `record_review` with `{ database, table, catalog, schema, disposition, concerns }` (including `low-touch` tables with empty concerns) so the portal can show the disposition.

After all tables, if scope is a whole datasource, infer and `save_relationships` (skill Step 7).

## 4. Report

Call `get_annotation_status` and summarize: how many tables/columns are now AWAITING_APPROVAL, broken down (if verified) into clean / escalated / corrected / stuck. Remind the user nothing is live until they approve it in the portal.
