# WBS-042 / Redmine #76627 — Design: real per-row `field['Col']` support in a worksheet condition's JS-typed value

Status: IN PROGRESS — writing incrementally as investigation and implementation proceed.

## Goal

Give `field['ColumnName']` genuine per-row semantics inside a worksheet CONDITION's
JAVASCRIPT-typed `ExpressionValue`, modeled on the per-row `field` binding that
`add_expression_column` (a worksheet expression column) already has working correctly. Today
(per prior diagnosis, `bug-wbs042/01-diagnosis.md` and
`docs/teams/2026-09-14-bugs-76627-cond-agg-expr/bug-wbs042/09-stylebi-rootcause-debugger.md` in
the stylebi-wiz repo) a JS-typed condition value is resolved exactly ONCE, before row iteration,
via `PreAssetQuery.execScriptExpression` / `ConditionGroup.getExpressionVal`, against an
`AssetQueryScope` that only binds `parameter` and worksheet table names — never `field`. This
document records the investigation and the concrete plan before any code changes land.

(This section will be filled in as investigation proceeds below.)
