---
description: Review saved annotations for a table or datasource — verify + adversarial reviewer, then report or auto-correct. Usage - /stylebi-viz-chat:review [table-or-datasource-name]
---

The user wants to review already-saved annotations (verify them and get a verdict), optionally auto-correcting bad ones. Use the **data-annotation** skill's verify/review machinery (`verify_annotation` + the `annotation-reviewer` subagent). You can REJECT but never APPROVE — approval stays a human action in the portal.

## 1. Resolve the target

- If `$ARGUMENTS` is non-empty, treat it as a table name or a datasource name. Match it:
  - Call `list_datasources`. If `$ARGUMENTS` matches a datasource, the scope is that whole datasource.
  - Otherwise call `list_annotation_targets` for the likely datasource(s) and match `$ARGUMENTS` to a table name. If it matches one table, scope is that table.
  - If it matches nothing or is ambiguous, list the options and ask which they meant.
- If `$ARGUMENTS` is empty, call `list_datasources` and ask the user which datasource (and, if they want, which single table) to review.

Always carry each table's `catalog`, `schema`, and `assetData` from `list_annotation_targets` through to the tools.

## 2. Prompt for options (one concise multiple-choice round)

Ask the user these four options before running (use sensible defaults shown in **bold**; let them accept all defaults at once):

1. **Action mode** — what to do when a table fails review:
   - **Report only** — verify + report verdicts and concerns; change nothing.
   - Auto-correct — reject the bad ones and re-annotate them (max 2 attempts each), then report.
2. **Keep-strategy** (only matters if auto-correcting) — which existing annotations to preserve:
   - **Keep approved only** — re-annotate non-approved columns; leave APPROVED ones untouched. (This is the natural default: `get_annotation_inputs` already returns approved columns under `preserved`.)
   - Keep all — skip any table/column that already has an annotation (only touch `NONE`-state columns).
   - Re-annotate all (non-approved) — redo every non-approved column. NOTE: the plugin cannot re-do **already-APPROVED** columns — un-approving is a portal-only action. To truly redo an approved column, tell the user to un-approve it in the portal first.
3. **Re-sample data** — refresh the statistics the verifier checks against:
   - **Re-sample** — run `prepare_table` first (fresh live sample + recomputed stats). Recommended; gives the verifier real evidence.
   - Reuse — skip `prepare_table`; use whatever samples/stats already exist (verifier will warn and fall back to integrity checks if none).
4. **Confidence threshold** — which annotations to act on:
   - **All** — review every table in scope.
   - Low-confidence only — only flag / auto-correct annotations the reviewer rates `low` (or `low`+`medium`) confidence; leave high-confidence ones as-is.

## 3. Run the review (per in-scope table)

For each table in scope:
1. If **Re-sample** chosen, call `prepare_table` with `{ database, table, catalog, schema }` (non-destructive — only adds samples/stats).
2. Call `verify_annotation` with `{ database, table, catalog, schema }` → `{ ok, defects, warnings }`.
3. Dispatch the **`annotation-reviewer`** subagent for the table → `{ verdict, concerns, confidence }`.
4. Apply the **Confidence threshold**: if "low-confidence only" and the reviewer's `confidence` is above the threshold, treat as accepted (skip correction) regardless of verdict.
5. Act per **Action mode**:
   - **Report only** → record the verdict + concerns; change nothing.
   - **Auto-correct** → for a `reject` verdict (within the confidence filter): call `reject_annotation`, then re-run `get_annotation_inputs` → reason (honoring the **Keep-strategy** and the reviewer's `concerns`) → `save_table_annotation` → re-`verify_annotation`. **Cap: 2 attempts.** If it still fails after the 2nd, stop and leave it AWAITING_APPROVAL with the concerns recorded.
   - `escalate` and `low-touch` verdicts are never auto-rejected — leave them AWAITING_APPROVAL.
6. **Persist the verdict:** call `record_review` with `{ database, table, catalog, schema, disposition, concerns }` for every reviewed table (including `low-touch` with empty concerns) so the portal can show the disposition and offer "Approve all low-touch".

## 4. Report

Call `get_annotation_status` and give the user a triaged summary:
- **Clean / low-touch** — passed review; safe to bulk-approve in the portal.
- **Escalated** — passed deterministic checks but need a human judgment call; include the concerns.
- **Corrected** — were rejected and successfully re-annotated (now AWAITING_APPROVAL).
- **Stuck** — failed review twice during auto-correct; need human attention; include the concerns.

Remind the user that nothing is live until they approve it in the portal — `/review` can flag, reject, and re-annotate, but only a human approves.
