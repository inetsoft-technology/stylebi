# WBS-046 (Redmine #76627) Fix — auto-alias unaliased aggregate outputs

Status: **PARTIAL FIX**, honestly disclosed below. The alias-tracking gap is fixed and verified.
The deeper "GROUP BY silently collapses to a grand constant" mechanism the live 2026-09-12 finding
reported could NOT be reproduced or fixed in this pass — see "What remains unresolved" below.

## What I changed

`core/src/main/java/inetsoft/web/wiz/worksheet/WorksheetMutationSupport.java`,
`applyAggregateInfo`'s "First aggregate on this column" branch (~line 865): previously, when the
caller's `AggregateSpec` carried no explicit `alias`, nothing happened — `colRef.setAlias(...)` was
never called and nothing was added to `appliedAliases`/`AGGREGATE_OUTPUT_ALIASES`. Now, when the
column also had **no alias already** (i.e. it wasn't separately renamed via `rename_column`), an
alias is auto-generated (the same `"_1"`/`"_2"` suffix-uniqueness convention the existing
secondary-aggregate path already uses) and tracked exactly like an explicit alias would be.

This means the already-fixed `clearAggregateAliases` mechanism (commits `184341cb0`, `036945949`)
now always has something to clear on a subsequent `set_group_aggregate` call, so a caller who reads
the updated model between calls (the intended workflow) and references the aggregate by its
now-displayed output name on a second, un-mirrored call gets the same fail-loud `PairingException`
the explicitly-aliased case already produces — instead of silently resolving back to the raw column.

The auto-alias is **skipped** when the column already carries an alias (e.g. from `rename_column`),
so a deliberate rename is never overwritten — this was verified against the existing
`renameAliasSurvivesReAggregation` and related tests (see below), which all still pass unchanged.

## Reproduction — before/after

### 1. Structural test (alias-chaining): fixed, verified red → green

Added `WorksheetEditServiceMutatorsTest#reAggregatingSameTableWithAutoAliasedOutputNowFailsLoudOnChaining`:

- First call: `set_group_aggregate("T", groups(cust, store), [Sum(amount)])` — **no alias**.
- **Before fix**: `columnByAttribute(t, "amount").getAlias()` was `null` (the output kept
  displaying as the raw name `"amount"`).
- **After fix**: `columnByAttribute(t, "amount").getAlias()` is `"amount_1"` (auto-generated).
- Second call (no mirror): `set_group_aggregate("T", groups(store), [AVG("amount_1") as
  avg_of_sums])`, chaining on the auto-generated alias.
  - **Before fix**: this exact scenario wasn't reachable (there was no `"amount_1"` alias to chain
    on — the bug's silent-resolution behavior instead required reusing the literal raw name
    `"amount"`, which the pre-existing `reAggregatingSameTableClearsStalePriorAlias` test's
    "sanctioned reuse" third call already covers as legitimate).
  - **After fix**: throws `PairingException` containing `"amount_1"` — confirmed by this test.
- A third call reusing the raw `"amount"` name directly still succeeds (the column is not left
  unreachable).

Ran: `./mvnw -o -pl core -am test -Dtest=WorksheetEditServiceMutatorsTest` (with `--git-dir`/
`--work-tree` bound to this worktree) → **247/247 passing**, including the new test and all
existing tests (`reAggregatingSameTableClearsStalePriorAlias`,
`renameAliasSurvivesReAggregation`, `sameColumnAggregatesKeepDistinctAliases`,
`setGroupAggregateCrosstabTogglesAggregateInfo`, etc.) unchanged.

### 2. End-to-end test (real query engine): passes, but does NOT reproduce the live symptom

Following the sibling refuter's technique, I built `WorksheetMutationSupportUnaliasedReaggregateEndToEndTest`
(a new file, not an addition to `WorksheetEditServiceMutatorsTest` — see "Why a separate file"
below), wiring a REAL (non-mocked) `AssetQuerySandbox` against an `EmbeddedTableAssembly` carrying
real in-memory rows (columns `A`, `B` dimensions, `C` numeric; 8 rows, 2 per `(A,B)` combo so `SUM`
differs from any single raw value):

- First call: `groups(A, B)`, `Sum(C)` — **no alias**. Query result column literally named `"C"`
  pre-fix, `"C_1"` post-fix (confirms the fix's naming change is real and observable).
- Second call (no mirror): `groups(A)`, `NthLargest(C, n=2)`, referencing the **raw** name `"C"`
  directly — not the new alias — matching a caller who never re-read the model between calls (the
  literal WBS-046 repro shape, and the shape that actually triggered the live finding).

**Result, both BEFORE and AFTER this fix**: `{X=20.0, Y=5.0}` — correctly differentiated per group
(`X`'s raw `C` values are `{10,5,30,20}`, 2nd-largest = 20; `Y`'s are `{100,1,5,2}`, 2nd-largest =
5). This is the mathematically correct "fresh single-stage aggregate recomputed over raw rows,
grouped by the new grouping" outcome — **not** the live-reported "every group collapses to 30.0
(the grand, fully-ungrouped 2nd-largest)" symptom.

This is expected once traced through: the auto-alias fix only changes behavior for a caller that
references the ALIAS. A caller reusing the raw name (as this repro does, matching the literal
live finding) hits `clearAggregateAliases` clearing the auto-alias first, then resolves `"C"` via
the same "sanctioned legitimate reuse" path `reAggregatingSameTableClearsStalePriorAlias`'s third
call already exercises — unaffected by this fix, before or after.

Ran: `./mvnw -o -pl core -am test -Dtest=WorksheetMutationSupportUnaliasedReaggregateEndToEndTest`
→ **1/1 passing** (and was already passing before this fix was applied — I ran it against unfixed
code first, per the task's own "reproduce before fixing" instruction, and it passed then too).

## What remains unresolved

The end-to-end test's clean pass (both pre- and post-fix) is **evidence, not proof**, that the live
2026-09-12 "collapses to grand constant" finding is a **separate, still-open defect**, not the same
mechanism this fix addresses:

- An `EmbeddedTableAssembly`'s aggregate is always evaluated in Java post-process (a
  `SummaryFilter`-style path) — it is never pushed into a SQL `GROUP BY`.
- The live finding was against a JDBC-backed StyleBI datasource (`Examples/Orders` /
  `ORDER_DETAILS`), where `PreAssetQuery.mergeGroupBy()` (`core/src/main/java/inetsoft/report/composition/execution/PreAssetQuery.java:591`)
  merges the grouping directly into the generated SQL.
- My end-to-end test cannot exercise that SQL-merge code path at all, since it never runs against a
  real/JDBC-backed table.

I could not build a live JDBC-backed reproduction in this role: no browser/MCP access was available
(same environment gap the refuter hit), and constructing a full JDBC-integration test (a real or
in-memory database, a registered `XDataSource`, a `PhysicalBoundTableAssembly` wired through
`AssetQuery`'s SQL-merge path) is a substantially larger undertaking than this task's scope — no
existing test in this codebase does this for `AssetQuery`/`PreAssetQuery` (checked: the only
`jdbc:h2:mem:` usage in `core/src/test` is for a `DataSourceRegistry` CRUD test, not a query-engine
test).

**Recommendation for a follow-up fixer**: reproduce with a live `mcp__composer-chat__*` session (or
a JDBC-integration test harness, if one gets built) against a real datasource, using the exact
`Examples/Orders` shape the original 2026-09-12 finding used, and trace
`PreAssetQuery.mergeGroupBy()`/`AssetQuery`'s SQL generation for a table whose `ColumnSelection`
holds a `ColumnRef` that is simultaneously a `GroupRef`'s target in the OLD (just-cleared)
`AggregateInfo` and a fresh `AggregateRef`'s target in the NEW one, per the refuter's own
unresolved hypothesis in `02-refute.md`.

## Why a separate test file

`WorksheetMutationSupportUnaliasedReaggregateEndToEndTest` is a new file rather than an addition to
`WorksheetEditServiceMutatorsTest.java` (where the task asked for it) because real query execution
needs Spring beans (e.g. `AssetDataCache`) that `WorksheetEditServiceMutatorsTest`'s shared
`@WizAgentTestSupport` context (`BaseTestConfiguration` + `SwapperTestConfiguration`) doesn't
register — confirmed by a `NoSuchBeanDefinitionException` when I first tried adding it there.
Widening that shared context to `IntegrationTestConfiguration` (what
`ChartGroupAggregatePoisonEndToEndTest` uses) would apply to all ~90 other tests in that file, and
I judged that too broad a blast radius to introduce for one test given this task's minimal-fix
scope. The **structural** regression test (`reAggregatingSameTableWithAutoAliasedOutputNowFailsLoudOnChaining`,
which only needs `AggregateInfo`/`PairingException` checks, no real query) IS in
`WorksheetEditServiceMutatorsTest.java`, next to its sibling `reAggregatingSameTableClearsStalePriorAlias`,
per the task's instruction.

## PR

https://github.com/inetsoft-technology/stylebi/pull/5217 (branch `fix/76627-wbs046-regroup-alias-fix`
against `stylebi-wiz-main-rebase`)
