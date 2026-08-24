# CSVLoader per-column format cache silently drops earlier rows' numeric values -- fix plan

**Found during:** investigation of the CI-only `WorksheetAgentControllerTest#importCsvDetectTypeFalseMakesEveryColumnString`
flake (see `2026-08-22-csv-price-type-string-ci-flake-investigation.md`). This is an
independent, confirmed defect, not the cause of that flake -- write-up split out per the team
lead's request so a fixer can pick it up separately.

## Root cause

`CSVLoader.readCSV()` keeps exactly one `java.text.Format` per column header in `fmtMap`
(`core/src/main/java/inetsoft/uql/util/filereader/CSVLoader.java:69`,
`Map<String, Format> fmtMap = new Object2ObjectOpenHashMap<>();`).

During the type-detection scan (`CSVLoader.java:90-198`), each row's value is passed to
`AssetUtil.getType()` (`core/src/main/java/inetsoft/uql/asset/internal/AssetUtil.java:2880-2973`).
For a numeric column, `getType()` picks a format based on that row's own shape --
`NFMT` for a plain number, `CFMT` for a dollar-prefixed value, `PFMT` for percent-suffixed,
`C2FMT` for parenthesized negatives (`AssetUtil.java:2940-2970`) -- and unconditionally
overwrites the column's single `fmtMap` entry with whichever format that row needed:

```java
// AssetUtil.java:2965
fmtMap.put((String) header, fmt);
```

So a column that mixes formats row-to-row (e.g. "299.99" then "$499.99") ends the scan with
`fmtMap[header]` holding only the last row's format (CFMT, currency), even though the column
was correctly, consistently typed DOUBLE throughout the scan.

`CSVLoader.parseRow()` (`core/src/main/java/inetsoft/uql/util/filereader/CSVLoader.java:500-642`)
is called once per row in the second, full-data read pass, and applies that single cached format
to every row's raw string, unconditionally:

```java
// CSVLoader.java:522-527
fmt = fmtMap.get(header[c]);

if(fmt != null) {
   val = rdata[c] = fmt.parseObject((String) val, new ParsePosition(0));
}
```

`Format.parseObject(String, ParsePosition)` does not throw on failure -- it returns null and
leaves the ParsePosition at its start index. Confirmed live (JDK 21):

```
CFMT = new DecimalFormat with a dollar-prefixed pattern, pinned to Locale.US
CFMT.parseObject("299.99", new ParsePosition(0))  ->  null   (index stays 0, no exception)
CFMT.parseObject("$499.99", new ParsePosition(0)) ->  499.99 (index advances to 7)
```

So for `price` / `299.99` / `$499.99` with detectType=true: the column is (correctly) typed
DOUBLE, but the row-1 cell silently becomes null in the final XSwappableTable / XEmbeddedTable --
the "299.99" value is lost with no error, warning, or exception anywhere in the call chain.
Execution falls through into parseRow's DOUBLE/DECIMAL branch (`CSVLoader.java:547-554`) with
val == null, which matches neither "val instanceof Number" nor "val != null", so the already-null
rdata[c] is left as-is -- no further fallback is attempted.

This is exactly the silent, plausible-but-wrong-result-on-unexpected-input pattern: an
internally-consistent column (every row parses to a correct numeric value during the scan) ends
up with a hole in its data during the second pass, and nothing signals it happened.

## Proposed fix

File: `core/src/main/java/inetsoft/uql/util/filereader/CSVLoader.java`

In `parseRow()`, when the column's cached `fmt` fails to consume the full string
(`parseObject` returns null, or the returned ParsePosition doesn't reach the string's length),
fall back to the original string instead of accepting the silent null, so the existing
type-specific `NumberParserWrapper` fallback further down the same branch (already present, see
below) gets a chance to parse it:

```java
fmt = fmtMap.get(header[c]);

if(fmt != null) {
   ParsePosition pos = new ParsePosition(0);
   Object parsed = fmt.parseObject((String) val, pos);

   if(parsed != null && pos.getIndex() == ((String) val).length()) {
      val = rdata[c] = parsed;
   }
   // else: leave val/rdata[c] as the original string -- the type-specific branch
   // below (DOUBLE/INTEGER/etc., CSVLoader.java:539-586) already calls
   // NumberParserWrapper on val.toString() whenever val isn't already a Number,
   // which parses "299.99" correctly on its own without needing the cached format.
}
```

This is the minimal change: it makes the cached format an optimization/fast-path rather than the
only path, and relies on the type-specific branches' existing NumberParserWrapper fallback
(already present at e.g. `CSVLoader.java:552`, `:560`, `:568`, `:576`, `:584`) to handle the case
where the cached format doesn't fit. No change is needed in `AssetUtil.getType()` itself -- the
per-row type detection during the scan phase is already correct; only the second pass's reliance
on a single cached format is the bug.

## Regression test

Add a case with mixed numeric sub-formats in one column to wherever `CSVLoader.readCSV` already
has direct tests (check for an existing `CSVLoaderTest`-style file first before adding a new
one), asserting both rows keep their numeric value, not just that the column type is non-STRING:

```java
// price\n299.99\n$499.99, detectType=true
XSwappableTable t = CSVLoader.readCSV(..., detectType=true, ...);
assertEquals(XSchema.DOUBLE, types.get(0));
assertEquals(299.99, (Double) t.getObject(1, 0), 0.001, "plain-format row must not be dropped");
assertEquals(499.99, (Double) t.getObject(2, 0), 0.001, "currency-format row must still parse");
```

Also worth a second case with the order reversed ("$499.99" then "299.99") to confirm the fix
isn't just accidentally correct for one scan order -- with the current bug, reversing the order
flips which row is silently dropped (whichever format is cached last wins), so a
one-directional test could pass by luck depending on which format happens to be last.

## Other call sites affected

`CSVLoader.readCSV()` is shared by both the agent's import-csv/import-csv-file endpoints
(`core/src/main/java/inetsoft/web/wiz/worksheet/WorksheetAgentController.java:807-887`, comment:
"the same loader the Composer's own import dialog uses") and the Composer's manual "Import Data
File" dialog. A fix here benefits both call sites; there is no separate copy of this logic to
update. `CSVLoader.parseData()` (the first-pass, type-detection function) is unaffected by this
fix -- only `parseRow()` (the second, full-data pass) changes.

## Confidence

High. The defect is confirmed live against JDK 21 (Format.parseObject returning null with a
non-advancing ParsePosition, no exception, when the cached currency format is applied to a plain
number string), and the code path (fmtMap single-entry overwrite, parseRow's unconditional apply)
is unambiguous from reading CSVLoader.java/AssetUtil.java directly. Not yet confirmed: the exact
existing-test-file location for the regression test -- check the repo for an existing direct
CSVLoader test class before adding a new one.
