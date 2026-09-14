# WBS-042 / Redmine #76627 — Implementation

Full design/rationale: `bug-wbs042/DESIGN.md`.

## Summary

Gives `field['ColumnName']` a genuine per-row binding inside a worksheet CONDITION's
JAVASCRIPT-typed value, by (1) forcing such a condition out of SQL merge and into StyleBI's
in-memory row evaluation, and (2) re-executing the script against a real row (via the same
`TableRow`/`TableRowScope` classes a worksheet expression column already uses) on every row
instead of resolving it once before any row is known.

## Files changed

- `core/src/main/java/inetsoft/uql/asset/ExpressionValue.java` — new `referencesField()` detector:
  `true` iff the value is JAVASCRIPT-typed and its expression text matches `\bfield\s*[\[.]`.
- `core/src/main/java/inetsoft/report/composition/execution/PreAssetQuery.java` —
  `isConditionItemMergeable`/`isConditionMergeable` now refuse to SQL-merge a condition whose value
  references field via `referencesField()`, forcing it into the in-memory path. Covers both
  preConditions (`mergeWhere`) and postConditions (`mergeHaving`), which share these two methods.
- `core/src/main/java/inetsoft/report/filter/ConditionGroup.java` — new `protected
  evalFieldExpression(ExpressionValue, AssetQuerySandbox, String, TableLens, int)` (pure addition,
  inert unless called); `getExpressionVal` widened from `private` to `protected` so a subclass can
  reuse the unchanged one-time-resolution path for the common, non-field-referencing case.
- `core/src/main/java/inetsoft/report/composition/execution/AssetConditionGroup.java` — the actual
  opt-in policy (deliberately confined here, not the shared `ConditionGroup` base class — see
  DESIGN.md's "Scope decision"): overrides `execExpressionValues` to defer a field-referencing value
  into a new `fieldExprBindings` list instead of resolving it once, and `evaluate(TableLens, int)`
  to resolve each deferred binding against the real current row (via `evalFieldExpression`) before
  delegating to `super.evaluate`.
- `core/src/main/java/inetsoft/report/script/TableRowScope.java` — **unplanned fix discovered
  during implementation**: `hasMember` didn't special-case the `basename` identifier (`"field"`)
  the way `getMember` already does, so GraalJS's `ScopeProxy` (which consults `hasMember` first to
  decide whether an unqualified identifier resolves in a scope) reported `field` as absent and threw
  `ReferenceError: field is not defined` before `getMember` was ever reached. Fixed by adding the
  same `basename` check to `hasMember`. This is shared code (also backs
  `FormulaTableLens`/`CalcTableLens`'s worksheet-expression-column and calc-table-cell `field['Col']`
  binding) — see DESIGN.md for why this was verified to be a pure correctness fix, not a
  WBS-042-only patch.
- Tests (all new):
  - `core/src/test/java/inetsoft/uql/asset/ExpressionValueReferencesFieldTest.java` — 8 unit tests
    for the detector (JS+field bracket/dot/embedded → true; JS without field, JS with an unrelated
    "myfield" substring, SQL+field, null expression, null type → false).
  - `core/src/test/java/inetsoft/report/composition/execution/ConditionFieldReferencingJavascriptValueTest.java`
    — 3 end-to-end tests against a real `Worksheet`/`EmbeddedTableAssembly`/`AssetQuerySandbox`/
    `AssetQuery` pipeline (no mocks): the falsifiable WBS-042 claim (`CUSTOMER_ID =
    field['REGION_ID']` discriminates per row, matching only the 2 of 3 rows where it's true), a
    plain JS condition without `field[...]` still resolves exactly as before (no regression on the
    dominant case), and the SQL-typed sibling is provably unaffected by the new detector.
  - `core/src/test/java/inetsoft/report/script/TableRowScopeTest.java` — 3 unit tests for the
    `hasMember`/`basename` fix (basename now reported present; a real column name still resolves;
    an unrelated identifier stays absent — no false positive introduced).

## Scope

- **Included:** preConditions and postConditions on a worksheet table assembly — both route through
  the identical `AssetConditionGroup`/`ConditionFilter`/`PostProcessor.filter` mechanism once
  non-mergeable (`AssetQuery.getConditionTableLens` is called for both), so implementing one
  necessarily implements the other; there was no smaller, correctly-scoped subset available.
- **Deliberately not touched:** `CalcConditionGroup`/`CrosstabConditionGroup`/
  `FreehandConditionGroup`/`GraphConditionGroup` (report/calc-table/graph condition evaluators) —
  putting the new logic in the shared `ConditionGroup` base class instead of `AssetConditionGroup`
  specifically would have risked turning those unrelated callers' "resolves to a wrong scalar" into
  "permanently unresolved `ExpressionValue` object," since most of them override `evaluate()` with
  different signatures that never reach the 3-arg `evaluate(TableLens,int,int)` our per-row
  resolution hooks into. Confining the change to `AssetConditionGroup` makes this a genuine no-op
  for those classes (verified no test file references any of them, so nothing to regress).
- **Not touched at all, per the task's explicit instruction:**
  `web/projects/portal/src/app/composer/dialog/ws/assembly-condition-item-pane-provider.ts` (the
  Formula Editor's Fields tree for a JS-typed worksheet condition value) — confirmed zero diff
  (`git diff --stat` against this path is empty).

## Test results

All commands run from the worktree with explicit `--git-dir`/`--work-tree` for git, and
`./mvnw -o -pl core test -Dtest=<Class> -Dsurefire.failIfNoSpecifiedTests=false` for Maven (offline,
scoped to the `core` module, no `-am` needed once `build-tools/antlr2-maven-plugin` was installed
once at the start of this session).

- **New tests, all green:**
  - `ExpressionValueReferencesFieldTest` — 8/8 passed.
  - `ConditionFieldReferencingJavascriptValueTest` — 3/3 passed.
  - `TableRowScopeTest` — 3/3 passed.
- **Sibling regression check, all green:** `AssetQueryCacheNormalizerTest`, `AssetQueryTest`,
  `TableRowTest`, `AssetQueryScopeTest`, `XConditionGroupTest`, `XTableRowTest`,
  `AssetConditionTest` — clean (exit 0).
- **Broader Condition-domain regression check** (26 test classes matching `*Condition*Test`/
  `*Condition*EndToEnd*` across the repo, 551 tests total): 550/551 passed. The one failure,
  `ConditionTest$RegressionTests.toSqlConditionSilentlyDefaultsOnAnUnmatchedName`, compares two
  `java.sql.Date` values each freshly constructed from `System.currentTimeMillis()` inside the test
  method (`Condition.toNullSqlCondition`/`toSqlCondition`, code this change never touches) — a
  pre-existing, time-boundary-sensitive flake, not a regression: re-running `ConditionTest` alone
  (same code, no changes) passed clean on the very next invocation.
- **Not run:** the full 555-file core suite (out of scope/time budget for this session; the targeted
  runs above cover every file this change touches plus its immediate siblings).

## What is unconfirmed / lower-confidence (carried over from DESIGN.md's risk list)

- **Joins/mirrors:** not independently verified with a live join in this pass — the structural
  argument (same `TableLens`/row mechanism a worksheet expression column already uses on a joined
  table) was not backed by a dedicated join-specific test.
- **Post-aggregate (postConditions/HAVING) semantics:** covered by the SAME code path as
  preConditions (both route through `getConditionTableLens`/`AssetConditionGroup`), but **not**
  independently exercised by a dedicated end-to-end test in this pass — building a realistic
  group+aggregate+postCondition fixture proved materially more complex than the preCondition case
  within this session's time budget, and per the task's own explicit permission to scope down
  rather than force something unverified, this was deliberately left untested rather than landing a
  fragile or unconfirmed assertion. This is the single largest gap in this pass's verification.
- **Crosstab/rotated tables:** unchanged, pre-existing special-case behavior in `mergeHaving`
  (`PreAssetQuery.java:743-747`) is untouched and not independently re-verified here.
- **Performance:** re-execution is O(rows) instead of O(1) for a field-referencing condition — an
  intentional, necessary cost of correctness, not benchmarked in this pass.

## PR

See the PR description for the summary and link back to this file / DESIGN.md. This does **not**
retroactively change plugin-side PR stylebi-wiz#2482 (the client-side fail-loud guard that currently
rejects `field[...]` in a JS-typed `set_conditions` value) — that plugin PR needs a follow-up once
this StyleBI-side capability is confirmed working end-to-end (e.g., via the actual Composer/plugin
UI, not just this Java-level test suite) to switch from rejecting `field[...]` to accepting/
forwarding it. Not attempted here — out of scope per the task brief (Java-repo-only).
