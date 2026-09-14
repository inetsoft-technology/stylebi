# WBS-042 / Redmine #76627 — Design: real per-row `field['Col']` support in a worksheet condition's JS-typed value

Status: IMPLEMENTING — design settled below; code changes follow the same session.

## Goal

Give `field['ColumnName']` genuine per-row semantics inside a worksheet CONDITION's
JAVASCRIPT-typed `ExpressionValue`, modeled on the per-row `field` binding that a worksheet
EXPRESSION COLUMN already has working correctly. Background reading: `bug-wbs042/01-diagnosis.md`
(original diagnosis) and `docs/teams/2026-09-14-bugs-76627-cond-agg-expr/bug-wbs042/09-stylebi-rootcause-debugger.md`
(fuller root-cause, in the stylebi-wiz repo).

## Current (broken) execution model

A worksheet condition's JAVASCRIPT-typed value is resolved exactly ONCE, before row iteration,
via one of two parallel call sites, both ending in the same shape of bug:

- `PreAssetQuery.getExpression()` (`core/src/main/java/inetsoft/report/composition/execution/PreAssetQuery.java:4189-4256`),
  when the condition is merged into SQL: the JAVASCRIPT branch (`:4241`) calls
  `execScriptExpression(exp, cond, vtable, box)` — a single scalar evaluation against
  `box.getScope()` (an `AssetQueryScope` binding only `parameter` and worksheet table names,
  never `field`) — and that scalar becomes a literal bind value used for every row. The SQL
  branch (`:4237`, `parseFieldExpression`) is unaffected — it textually inlines `field['Col']`
  into real SQL text and already works correctly for every row.
- `ConditionGroup.execExpressionValues`/`getExpressionVal` (`core/src/main/java/inetsoft/report/filter/ConditionGroup.java:539-678`),
  used for the in-memory ("post process") evaluation path when a condition doesn't merge into
  SQL for some other reason: also resolves ONCE, against `box.createAssetQueryScope()` (same
  `AssetQueryScope`, no `field`), then bakes the result onto the condition via
  `acond.setDynamicValue(i, val, false)` (`:552`) BEFORE any row is evaluated.

Neither path has a concept of "the current row" at the point the script runs — this is the root
cause `field['ColumnName']` cannot work today.

## The working model being mirrored

A plain worksheet EXPRESSION COLUMN (`ColumnRef` wrapping an `ExpressionRef`, built server-side by
`WorksheetMutationSupport.addExpressionColumn`, `core/src/main/java/inetsoft/web/wiz/worksheet/WorksheetMutationSupport.java:1326-1349`)
is marked `colRef.setSQL(sql)`. When `sql == false` (JAVASCRIPT), `PreAssetQuery.isSQLExpression`/
`column.isSQL()` (`PreAssetQuery.java:3416`) already makes that column non-mergeable, forcing it
through Java-side, row-by-row evaluation via `FormulaTableLens` (`core/src/main/java/inetsoft/report/lens/FormulaTableLens.java`).
`FormulaTableLens.TableRow2` (`:1024-1129`) computes each row's expression cell by executing the
compiled script against a `TableRowScope` (`core/src/main/java/inetsoft/report/script/TableRowScope.java`)
wrapping a `TableRow` (`core/src/main/java/inetsoft/report/script/TableRow.java`) for **that specific
row** of the table (`thisScope = new TableRowScope(this, "field")`, `FormulaTableLens.java:1027`).

- `TableRow(XTable table, int row)` is a lightweight, reusable, row-and-table-scoped scriptable:
  `getArrayElement`/`getMember` resolve a column name (via `getColFromColMap`) against
  `table.getObject(row, col)` for the specific `row` it was constructed with — this is exactly
  `field['ColumnName']`'s existing syntax; no new syntax is introduced.
- `TableRowScope(TableRow base, String basename="field")` is a `DynamicScope` that returns `base`
  itself when the identifier `"field"` is looked up, and falls through to `getParentScope()` for
  anything else not found on `base` (builtins like `Array`/`Math`/`Date` are special-cased to defer
  to the JS engine's own globals). Chaining `fieldScope.setParentScope(outerScope)` where
  `outerScope` resolves `parameter` (an `AssetQueryScope`) reproduces exactly the vocabulary
  `ScriptContextService.CALC_FIELD_VARS = List.of("field", "parameter")` already documents for a
  per-row calc context — confirming `field` + `parameter`, nothing else, is the intended scope
  shape, matching what a worksheet expression column already gets.
- Confirmed iteration model: `FormulaTableLens` iterates real `TableLens` rows in Java
  (`for(int i = nrows + hrows; i <= maxr && table.moreRows(i) ...)`, `:377`) and re-executes the
  compiled script once per row (`TableRow2.exec`, `:1106-1123`), each time against the SAME
  `TableRow`/`TableRowScope` instance but with `thisScope`'s underlying row index advanced
  (`TableRow.setRow`) — i.e., genuinely fresh `field` bindings per row, not a single fixed scalar
  reused across rows.

## Plan

### 1. Mergeability — force field-referencing JS condition values into the in-memory path

Add `ExpressionValue.referencesField()` (`core/src/main/java/inetsoft/uql/asset/ExpressionValue.java`):
`true` iff `type == JAVASCRIPT` and the expression text matches `\bfield\s*[\[.]` (mirrors the
same informal `field[`/`field.` detection the plugin-side guard in PR stylebi-wiz#2482 already
uses, so both sides agree on what counts as "referencing field").

In `PreAssetQuery.java`, add a small static helper `referencesFieldInJavascript(Condition cond)`
that scans `cond.getValues()` for an `ExpressionValue` with `referencesField() == true`, and call
it from both:
- `isConditionItemMergeable(ConditionItem item)` (`:449-523`, used by `mergeWhere` for
  preConditions) — return `false` (non-mergeable) if the condition's value references `field`.
- `isConditionMergeable(ConditionList conds)` (`:525-551`, used both as `mergeWhere`'s
  whole-list gate and by `mergeHaving`/`isPostConditionListMergeable` for postConditions) — same
  check, per condition item in the list.

This is the only place SQL-mergeability is decided for a condition value's content; both
preConditions and postConditions route through one or the other of these two methods, so this one
check covers both without postConditions needing separate handling.

### 2. In-memory evaluation — per-row re-execution with a real `field` binding

Both preConditions and postConditions, once non-mergeable, are filtered via the SAME call path:
`AssetQuery.getConditionTableLens(TableLens base, VariableTable vars, ConditionList conds)`
(`core/src/main/java/inetsoft/report/composition/execution/AssetQuery.java:1837-1848`) builds an
`AssetConditionGroup(base, conds, mode, box, touchtime)` and wraps it in
`PostProcessor.filter(base, cgroup)` → `ConditionFilter`/`ConditionFilter2`
(`core/src/main/java/inetsoft/report/filter/ConditionFilter.java`), whose `checkCondition(int r)`
calls `conditions.evaluate(getTable(), r)` for every row of `base` — this is called once per row
of whatever `TableLens` is being filtered (the raw pre-aggregate table for preConditions, or the
already-summarized table for postConditions/HAVING — "the row" is correctly whatever table this
particular `ConditionGroup` was built against either way, so no separate pre-/post-aggregate logic
is needed).

`ConditionGroup.evaluate(TableLens lens, int row, int col)`
(`core/src/main/java/inetsoft/report/filter/ConditionGroup.java:308-414`) is the real per-row
entry point: it already has `lens`/`row` and already has an established, analogous mechanism for
a DIFFERENT case — a condition value that is a literal `DataRef` (column-to-column comparison) is
re-read from `lens.getObject(row, index)` on every call (`hasField`/`fieldmap`/`colmap`,
`:309-355`) before the row is evaluated. The new mechanism mirrors this same shape for
field-referencing JAVASCRIPT values instead of literal `DataRef` values.

**Scope decision — confine the new logic to `AssetConditionGroup`, not the shared
`ConditionGroup` base class.** `ConditionGroup.execExpressionValues`/`evaluate(TableLens,int,int)`
are shared by several unrelated subclasses (`CalcConditionGroup`, `CrosstabConditionGroup`,
`FreehandConditionGroup`, `GraphConditionGroup`'s inner class) that are report/calc-table/graph
condition evaluators, not worksheet conditions, and most of them override `evaluate()` with
different signatures entirely (no-arg `evaluate()`, `evaluate(DataSet,int)`) that never reach
`ConditionGroup`'s 3-arg `evaluate(TableLens,int,int)`. Putting the new per-row resolution directly
in the shared base class's `execExpressionValues(AssetCondition,...)` would mean those other
subclasses silently start deferring resolution of any field-referencing value they happen to hold,
but then never actually re-resolve it (their own `evaluate()` overrides never call the 3-arg
method) — turning today's "wrong single scalar" into "permanently unresolved `ExpressionValue`
object used as a comparison value," a real regression for callers this task has no business
touching. `AssetConditionGroup` (`core/src/main/java/inetsoft/report/composition/execution/AssetConditionGroup.java`)
is the class actually used by worksheet/asset condition evaluation (`AssetQuery.getConditionTableLens`)
and nothing else, so it is the correct and sufficiently narrow place to opt in:

- `ConditionGroup` gains one new `protected` method, `evalFieldExpression(ExpressionValue eval,
  AssetQuerySandbox box, String type, TableLens lens, int row)` — pure addition, mirrors
  `getExpressionVal`'s script-compile/exec/error-handling shape exactly, but builds
  `new TableRowScope(new TableRow(lens, row), "field")` with parent scope
  `box.createAssetQueryScope()` instead of using `box.getScope()`/`box.createAssetQueryScope()`
  alone. This method is inert until a subclass calls it — zero behavior change for any existing
  caller of `ConditionGroup`.
- `ConditionGroup.getExpressionVal` visibility widens from `private` to `protected` (pure
  visibility change, no behavior change) so `AssetConditionGroup`'s override of
  `execExpressionValues` can reuse it verbatim for the (overwhelmingly common) non-field-referencing
  branch, keeping that branch byte-for-byte identical to today.
- `AssetConditionGroup` overrides `execExpressionValues(AssetCondition acond, AssetQuerySandbox box,
  DataRef attr, String type)`: for each value, if it's a field-referencing `ExpressionValue`, defer
  it into a new `fieldExprBindings` list (condition + value index + eval + type + box) instead of
  resolving it; otherwise, behavior is identical to the base class (calls `getExpressionVal` +
  `setDynamicValue`, unchanged).
- `AssetConditionGroup` overrides `evaluate(TableLens lens, int row)` (already overridden today for
  subquery row-binding, `:148-155`): before delegating to `super.evaluate(lens, row)`, resolve each
  deferred binding via `evalFieldExpression(...)` against the current `lens`/`row`, then
  `binding.cond.clearCache(); binding.cond.setValue(binding.index, val);` — same
  clear-cache-then-set-value shape the existing `hasField` mechanism already uses at
  `ConditionGroup.java:341-351`, so this is a fresh, correct value on every row, exactly mirroring
  a worksheet expression column's per-row re-execution.

### What `field` exposes

Just named-column access matching `field['ColName']`'s existing syntax and existing semantics —
`TableRow`/`TableRowScope` are reused unmodified from the expression-column mechanism, so no new
binding surface is introduced; whatever a worksheet expression column's `field['Col']` already
means (column-name lookup against the current row, including the alias/qualified-name fallbacks
`TableRow.getColFromColMap` already implements) is exactly what a condition's `field['Col']` means
too. `parameter` remains available via the parent-scope chain (`AssetQueryScope`), matching
`ScriptContextService.CALC_FIELD_VARS`.

### Risks / limitations (explicitly flagged, not hidden)

- **Performance.** A field-referencing JS condition value is re-parsed-from-cache (the existing
  `scriptCache` is reused, so no recompilation cost) but re-EXECUTED once per row, instead of once
  per query. This is materially more expensive than SQL pushdown and was already true (implicitly)
  before this change for the "resolve once" case's per-query cost — the difference is O(1) → O(n
  rows). This is an intentional, necessary cost of correctness: there is no way to give `field[...]`
  real per-row meaning without running per row. Not mitigated further in this pass (e.g., no
  short-circuit for rows already excluded by an earlier AND-ed mergeable condition) — a possible
  future optimization, not attempted here.
- **Crosstab / rotated tables.** `mergeHaving` already special-cases crosstab/rotated-table
  postConditions (`PreAssetQuery.java:743-747`, returns `false` early, skipping post-process
  condition application in Java entirely for that case's non-mergeable branch) — this pass does not
  change or interact with that pre-existing special case; a field-referencing JS postCondition on a
  crosstab table inherits whatever that existing special case already does (out of scope here to
  change).
- **Joins/mirrors.** No special handling added or needed: `TableRow`/`TableRowScope` operate on
  whatever `TableLens` is passed to `evaluate(TableLens lens, int row)`, which for a joined/mirrored
  table assembly is already the fully-resolved, joined row — same as how a worksheet expression
  column on a joined table already works today via `FormulaTableLens`. Not independently verified
  with a live join in this pass; flagged as unconfirmed rather than assumed safe by inspection alone
  beyond the structural argument above.
- **Post-aggregate (`postConditions`/HAVING) semantics.** "The row" for a postCondition is a row of
  the post-aggregate/summarized table (whatever `base` is by the time `getConditionTableLens` is
  called for postConditions, i.e. after `getSummaryTableLens`), not the raw source row — so
  `field['Col']` in a postCondition resolves against aggregate/group output columns, not raw
  pre-aggregate columns. This is the architecturally consistent behavior (same as any other
  postCondition column reference) but is flagged explicitly per the task's own callout, and is
  covered by a narrower regression test than the pre-aggregate case (see below) — treat as
  lower-confidence/lightly-verified relative to the pre-aggregate case.
- **Scope: preConditions AND postConditions both included**, not narrowed to preConditions-only —
  because both route through the identical `getConditionTableLens`/`AssetConditionGroup` mechanism
  once non-mergeable, implementing one necessarily implements the other; there was no smaller,
  safe subset to land instead without artificially special-casing postConditions out, which would
  have been more code, not less.
- **Blast radius kept deliberately narrow to `AssetConditionGroup`** (see the scope decision above)
  — `CalcConditionGroup`/`CrosstabConditionGroup`/`FreehandConditionGroup`/`GraphConditionGroup`
  remain completely unmodified and behave exactly as before (their field-referencing JS values, if
  any exist anywhere in the wild, remain exactly as broken/no-op as they are today — not worse, not
  better). Extending real `field[...]` support to those report-side condition contexts is explicitly
  out of scope for this task and not attempted.
- **Clone/thread-safety envelope.** `AssetConditionGroup` does not override `clone()`; a clone shares
  the same `fieldExprBindings` list and the same underlying `AssetCondition` objects as the
  original, so per-row `setValue` calls mutate shared condition state across clones. This is the
  identical envelope the pre-existing `hasField`/`fieldmap` and `sarr` (subquery) mechanisms already
  operate under — not a new risk category introduced by this change.

## What is NOT touched

Per the task's explicit instruction, `community/web/projects/portal/src/app/composer/dialog/ws/assembly-condition-item-pane-provider.ts`
(the Formula Editor's "Fields" tree for a JS-typed worksheet condition value) is left exactly as-is
— it already offers `field[...]` insertion, which becomes correct/intended now that this change
lands, matching its own "fields should be accessible in JS" comment.

## Regression tests planned

New JUnit test(s) exercising:
1. `CUSTOMER_ID = field['REGION_ID']`-shaped preCondition on a table with real per-row data —
   correctly discriminates (matches only the rows where true), not all rows / not zero rows.
2. A plain (no `field[...]`) JS condition value still resolves exactly as before (no regression on
   the dominant, already-working case).
3. The SQL-typed sibling (`ExpressionValue.SQL`, `field['Col']` via `parseFieldExpression`'s textual
   substitution) is unaffected by this change.
4. A postCondition (HAVING) with a field-referencing JS value, flagged as the lighter-verified case
   per the risk note above.
5. `ExpressionValue.referencesField()` unit-level cases (JS + field ref → true; JS without field ref
   → false; SQL + field ref → false, since the SQL branch's own textual mechanism already handles
   it and must not be forced non-mergeable).
