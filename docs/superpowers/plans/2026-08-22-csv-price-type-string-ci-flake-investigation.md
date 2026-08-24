# CSV `price` column type-detection CI failure -- round-3 investigation

**Test:** `WorksheetAgentControllerTest#importCsvDetectTypeFalseMakesEveryColumnString`

**Symptom:** on GitHub Actions Linux CI, deterministically every run, `detectType=true` on
`price\n299.99\n$499.99` ends with the column typed `STRING` (assertion
`assertNotEquals(XSchema.STRING, ...)` fails). Passes reliably on every local Windows repro.
`stylebi` PR #4734 / commit `385c02789` (pinning `AssetUtil`'s NFMT/PFMT/CFMT/C2FMT to
`Locale.US`) is real and correct but does not fix this test.

## What this round confirmed (new information, not just re-checking prior rounds)

Re-traced the entire pipeline end to end, not just `CSVLoader.parseData()` /
`AssetUtil.getType()`'s exception paths (already ruled out in rounds 1-2), and reproduced the
actual algorithm standalone against JDK 21 to remove any doubt about what the scan phase
really computes:

1. `CSVLoader.readCSV()` scan loop, `core/src/main/java/inetsoft/uql/util/filereader/CSVLoader.java:90-198`.
2. `AssetUtil.getType()`, `core/src/main/java/inetsoft/uql/asset/internal/AssetUtil.java:2880-2973`.
3. The Bug #16268 row-to-row type-mismatch merge, `CSVLoader.java:167-178`.
4. `CSVLoader.parseRow()`, `CSVLoader.java:500-642` (the second, full-data pass -- not examined
   in rounds 1-2).
5. `WorksheetAgentController.importCsvBytes()`, `core/src/main/java/inetsoft/web/wiz/worksheet/WorksheetAgentController.java:819-887`.
6. `XEmbeddedTable(String[] types, XTable table)` constructor and `getDataType()`/`getColType()`,
   `core/src/main/java/inetsoft/uql/util/XEmbeddedTable.java:157-188,638-640,773-775`.
7. `EmbeddedQuery.getDefaultColumnSelection0()`, `core/src/main/java/inetsoft/report/composition/execution/EmbeddedQuery.java:135-159`
   -- the actual `ColumnRef`/`ColumnSelection` builder for a plain (non-snapshot)
   `EmbeddedTableAssembly` -- not examined in rounds 1-2.
8. `AssetEventUtil.initColumnSelection()`, `core/src/main/java/inetsoft/report/composition/event/AssetEventUtil.java:1594-1659`
   -- the name-based column-ref reuse logic -- checked for cross-table leakage between the
   "Typed"/"AsText" assemblies in the same test; ruled out (each assembly starts with an empty
   `ColumnSelection`, so there is nothing to reuse across the two imports).

Verified with a standalone JDK 21 program (`SimLoader.java`, reproduces steps 1-4's exact
per-row logic with the real, Locale.US-pinned `NFMT`/`CFMT` instances) that with default type
`INTEGER` (StyleBI's default for a fresh column, `AssetUtil.initDefaultTypes`,
`AssetUtil.java:2856-2858`):

```
row='299.99'   currentType=INTEGER -> newType=DOUBLE   fmtMapNow=NFMT(plain)
row='$499.99'  currentType=DOUBLE  -> newType=DOUBLE   fmtMapNow=CFMT(currency)
FINAL column type = DOUBLE
```

The Bug #16268 merge block (`CSVLoader.java:170`) never fires here: on row 1
`hasRecognizedTypes.get(i)` is still `false` (only set `true` after the merge check, at
`CSVLoader.java:192-193`), so the `INTEGER != DOUBLE` mismatch on row 1 is not treated as a
mismatch. On row 2, `newType` is already `DOUBLE` again (matches `currentType`), so no merge
either. This computation is fully deterministic and does not depend on locale, thread
scheduling, or class-load order -- `NFMT`/`CFMT` are pinned to `Locale.US`
(`AssetUtil.java:3502-3508`, from PR #4734/`385c02789`), and no other locale-sensitive branch
(`Tool.isDate`, `Tool.isTime`, `isDmyOrder`) is reachable for either of these two strings.

`XEmbeddedTable.getDataType(col)` (`XEmbeddedTable.java:773-775`) returns `types[col]` verbatim
-- it does not re-derive type from the actual cell values. `EmbeddedQuery.getDefaultColumnSelection0()`
(`EmbeddedQuery.java:144-156`) reads `data.getDataType(i)` directly and only falls back to
`Tool.getDataType(data.getColType(i))` when that is `null` -- it is `"DOUBLE"`, not `null`, here,
so that fallback path is never taken either. So the declared column type is `DOUBLE`, verbatim,
from `CSVLoader`'s scan phase all the way to the `ColumnRef` the test asserts on, on any
platform, given identical JVM locale/timezone (which CI's surefire `argLine` and a local
`mvn test` both force explicitly: `core/pom.xml:998`).

Conclusion: no environment-dependent branch was found anywhere in this pipeline. Given two
rounds of live-CI instrumentation already ruled out every throw-a-catchable-exception path, and
this round's static+simulated trace rules out every value-based branch as well, the CI-vs-local
divergence is very unlikely to live in `CSVLoader`/`AssetUtil`/`XEmbeddedTable`/`EmbeddedQuery`
at all.

## A separate, real, confirmed bug found along the way (independent of the CI flake)

`CSVLoader`'s per-column `fmtMap` (`CSVLoader.java:69`) keeps exactly one `Format` per header,
and `AssetUtil.getType()` overwrites that entry every time a row's value takes a different
numeric sub-format (plain vs `$`-prefixed vs `%`-suffixed) -- `AssetUtil.java:2965`. Since the
scan visits `"299.99"` (goes to `NFMT`) then `"$499.99"` (goes to `CFMT`, overwriting `NFMT`),
`fmtMap["price"]` ends the scan holding `CFMT`. `CSVLoader.parseRow()` (`CSVLoader.java:522-527`)
then applies that single stored format to every row in the second, full-data pass:

```java
fmt = fmtMap.get(header[c]);
if(fmt != null) {
   val = rdata[c] = fmt.parseObject((String) val, new ParsePosition(0));
}
```

Confirmed live (JDK 21, `DecimalFormat("$#,##0.##;-$#,##0.##", Locale.US)`):
`CFMT.parseObject("299.99", pos)` returns `null` with `pos.getIndex()==0` -- no exception, it
just silently fails (`ParsePosition` API doesn't throw). So the row-1 cell for `"299.99"`
becomes `null` in the final `XSwappableTable`/`XEmbeddedTable`, even though the column is
(correctly) typed `DOUBLE`. This is a real, silent, plausible-but-wrong data-loss bug: every row
that introduced a numeric sub-format different from whichever row happens to scan last loses its
value. It does not explain the STRING-vs-DOUBLE CI divergence (the declared type stays `DOUBLE`
regardless, per the trace above), but it is a genuine defect worth its own fix and test,
independent of this investigation.

Proposed fix (separate PR, not blocking on this investigation): `CSVLoader.parseRow()` should
not assume a single format works for the whole column -- either (a) `AssetUtil.getType()` should
pick/keep the "widest" applicable format per column (don't overwrite a previously successful
entry with a narrower one, or store a small ordered list of formats to try), or (b) `parseRow()`
should fall back to trying `NFMT`/`CFMT`/`PFMT`/`C2FMT` in turn (or `NumberParserWrapper.getDouble()`)
when the column's cached format fails to consume the whole string, instead of accepting a silent
`null`. Add a regression test with mixed-format numeric rows (`"299.99"` then `"$499.99"`)
asserting both rows keep their numeric value, not just that the column type is non-`STRING`.

Other call sites affected by a `parseRow`/`fmtMap` fix: `CSVLoader.readCSV()` is the loader "the
Composer's own import dialog uses" (per `WorksheetAgentController.java:807`), so any fix here
also affects the Composer's manual CSV import path, not just the agent's `import-csv` endpoint.

## Precise next diagnostic (single line, one location)

Given the type-computation pipeline looks fully deterministic, the fastest way to find the real
divergence is to stop guessing inside `CSVLoader`/`AssetUtil` and log the boundary value right
where `WorksheetAgentController.importCsvBytes()` gets control back from the loader -- this
tells us in one CI round whether the problem is before or after this point:

```java
// core/src/main/java/inetsoft/web/wiz/worksheet/WorksheetAgentController.java, right after line 846
System.out.println("[DIAG] types=" + types + " row0col0=" + loaded.getObject(1, 0) +
   " row1col0=" + loaded.getObject(2, 0));
```

(`loaded` row 0 is the header row that `CSVLoader` writes via `dataTable.addRow(header)` at
`CSVLoader.java:254`, so data rows are indices 1 and 2.)

- If CI shows `types=[double]` (matching local / this trace) -- the bug is downstream, in
  `AssetEventUtil.initColumnSelection` / `EmbeddedQuery.getDefaultColumnSelection0` /
  `AssetQuery.createAssetQuery`, or in how the mocked `AssetQuerySandbox`/`RuntimeWorksheet` in
  this unit test behave under CI's Mockito/bytecode setup -- worth diffing Mockito/byte-buddy
  versions or JDK dynamic-agent warnings between CI and local next.
- If CI shows `types=[string]` (contradicting this trace) -- something in `CSVLoader`/`AssetUtil`
  genuinely does differ on CI in a way neither this round's static+simulated trace nor rounds
  1-2's live instrumentation caught, and the next step is to add the same per-row diagnostic used
  in this investigation's `SimLoader.java` harness directly into `CSVLoader.java:196` (log
  `currentType`, `newType`, and `fmtMap.get(header[i])` after every row) so the real CI values can
  be compared line-for-line against the simulation's expected output above.

Either branch resolves this in exactly one more CI round.

## Confidence

High confidence that `CSVLoader`'s scan phase and the `ColumnRef`-building path are
locale/environment-invariant for this input (verified by direct code trace and a standalone
JDK 21 reproduction of the real algorithm, not just static reading). Medium-high confidence that
the `fmtMap` overwrite bug in `parseRow()` is real and independent of the CI flake (verified live
in JDK 21). Not confirmed: the actual CI-vs-local divergence itself -- it was not possible to
reproduce or observe CI's actual runtime values directly in this round; the diagnostic above is
the fastest remaining way to pin it down.
