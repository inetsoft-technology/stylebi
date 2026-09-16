# Freehand Table (StyleBI Dashboard Script Reference)

Sources:
- https://www.inetsoft.com/docs/stylebi/InetSoftUserDocumentation/1.0.0/dashboardscript/FreehandTable.html
  (`layoutInfo` API index and per-method pages)
- https://www.inetsoft.com/docs/stylebi/InetSoftUserDocumentation/1.0.0/dashboard/AddFreehandTable.html
  ("Add Formula Cells" / "Dynamic Formulas" section — the cross-cell reference syntax)

> A Freehand (calc) table is a grid of individually-bound cells (`{row, col}` addressing), unlike a
> Crosstab's uniform aggregation model. Each cell is independently one of: static text, a bound field,
> or a formula script. This is the mechanism to reach for whenever a business requirement mixes
> straight aggregation with cross-row/cross-cell arithmetic (e.g. "order total", "return total", and
> "net sales = order total − return total" in one table) — a Crosstab has no vocabulary for the third
> kind of cell.

---

## The `$<name>` cross-cell reference — the answer to "how does one cell's formula reference another cell's value"

**This is the one thing that is easy to get wrong by guessing, and StyleBI's own official docs confirm
it explicitly**: a formula cell refers to another cell's value with `$cell_name`, where `cell_name` is
whatever name was assigned to that cell (via `layoutInfo.setCellName` in script, or the "Cell Name"
field in the Composer's cell-properties dialog — the composer-chat MCP tool surface exposes this as
`set_cell_binding`'s `name` field).

```js
// Cell (0,1) was named "OrderTotal", cell (1,1) was named "ReturnTotal".
// A formula cell can now reference both by name:
FreehandTable1.layoutInfo.setCellBinding(2, 1, 3, "$OrderTotal - $ReturnTotal");
```

**What does *not* work, despite looking plausible**: spreadsheet-style `B1-B2` addressing, a bareword
column/cell identifier with no `$` prefix, and `field["ColumnName"]` (that syntax is for binding a
cell to a column of the table's *source data*, not to another calc-table cell's computed value — see
below). All three either throw a `ReferenceError`/`SyntaxError` or silently evaluate to nothing.

A cell's own name can also be inserted into the script editor directly by picking it from the editor's
"Cell" folder, rather than typing `$name` by hand — useful context if a human built the sheet through
the Composer UI and a script inherited from them uses this syntax already.

### Referencing the underlying source data

To access the table's bound *source* data (as opposed to another calc-table cell), use the `data`
keyword — this is the freehand-table equivalent of what `field[...]` does when a cell is directly
bound to a column:

```js
// Unique, sorted list of values from the source data's 'Company' field:
toList(data['Company'], 'sort=desc')

// Query-style filtering: value of 'Product' where 'Category' matches the value
// currently held by the cell named 'CategoryCell':
toList(data['Product@Category:$CategoryCell'])
```

Remember to set the cell's expansion (`layoutInfo.setExpansion`, below) whenever a formula returns an
array of values — a formula that returns an array but isn't marked to expand renders only the first
value.

See also (for more advanced source-data referencing, not calc-table-specific): "Reference Query Data",
"Reference Datasource Data", and "Run a Query from Script" in StyleBI's dashboard-script docs.

---

## `layoutInfo` — the per-cell configuration API

`FreehandTable1.layoutInfo` is the entry point for every per-cell property below. Every method takes
`row`/`col` (0-indexed cell coordinates) as its first two arguments. Either the unqualified
(`layoutInfo.setX(...)`) or qualified (`FreehandTable1.layoutInfo.setX(...)`) form works inside that
table's own component script; the qualified form is required from `onInit`/`onRefresh` or another
component's script.

### `layoutInfo.setCellBinding(row, col, type, value)`

Sets what a cell holds. `type` is the discriminator:
- `1` — plain static text. `value` is the literal text.
- `2` — a bound field name. `value` is the column name from the table's source data (equivalent to
  `field["ColumnName"]` binding).
- `3` — a formula script. `value` is the script text — this is where `$<name>` cross-cell references
  and `data[...]` source-data references (above) are used.

```js
FreehandTable1.layoutInfo.setCellBinding(1, 0, 1, 'label text');           // static text
FreehandTable1.layoutInfo.setCellBinding(1, 0, 2, 'State');                // bound field
FreehandTable1.layoutInfo.setCellBinding(1, 0, 3,
  "toList(q['Date'], 'sort=asc, rounddate=year')");                        // formula
```

### `layoutInfo.setCellName(row, col, name)`

Names a cell so other cells can reference its computed value as `$name` (above). This is the
prerequisite step for any cross-cell formula — a cell has no name by default.

```js
FreehandTable1.layoutInfo.setCellName(1, 0, 'state');
```

### `layoutInfo.setExpansion(row, col, type)`

Marks a cell to expand into multiple rows/columns when its formula returns an array (typically via
`toList(...)`). `type`: `1` = horizontal expansion, `2` = vertical expansion. This is what makes a
"group" cell actually generate one row/column per distinct value — a group cell with no expansion
renders plausibly with the wrong row/column count and nothing in a tool's return value says so; always
render-check (`get_viewsheet_image`) after setting this.

```js
var q = runQuery('ws:global:Examples/AllSales');
FreehandTable1.layoutInfo.setCellBinding(1, 0, 3, "toList(q['State'], 'sort=asc')");
FreehandTable1.layoutInfo.setExpansion(1, 0, 1); // horizontal expansion
```

### `layoutInfo.setRowGroup(row, col, name)` / `setColGroup(row, col, name)`

Sets which named cell (or `'(default)'`) an aggregate cell's row/column grouping follows — the
freehand-table equivalent of a Crosstab's row/column dimension grouping, needed when an aggregate
cell's automatic grouping doesn't line up with the intended dimension cell.

```js
FreehandTable1.layoutInfo.setRowGroup(1, 0, '(default)');
FreehandTable1.layoutInfo.setColGroup(1, 0, 'state');
```

### `layoutInfo.setMergeCells(row, col, Boolean)`

Enables/disables merging for an *expanded* cell — useful when expansion produces duplicate/adjacent
entries that should visually collapse into one merged cell. Not the same as merging two originally
distinct static cells (that's a Composer UI action — "Merge Cells" — with no direct script
equivalent documented here).

```js
FreehandTable1.layoutInfo.setMergeCells(1, 0, true);
```

### `layoutInfo.setMergeRowGroup(row, col, name)` / `setMergeColGroup(row, col, name)`

Sets which named cell a *merged* cell's row/column grouping follows (the merged-cell counterpart to
`setRowGroup`/`setColGroup` above).

```js
FreehandTable1.layoutInfo.setMergeRowGroup(1, 0, 'state');
FreehandTable1.layoutInfo.setMergeColGroup(1, 0, 'state');
```

### `layoutInfo.setSpan(row, col, width, height)`

Sets how many cells (horizontally/vertically) a single cell should visually span, starting from
`(row, col)`.

```js
FreehandTable1.layoutInfo.setSpan(0, 0, 2, 1); // span two columns
FreehandTable1.layoutInfo.setSpan(0, 0, 1, 2); // span two rows
```

---

## Table-level properties (not per-cell)

### `fillBlankWithZero` (Boolean)
Populates empty result cells with `0` instead of leaving them blank (empty cells occur when no data
matches a given row/column heading combination).
```js
fillBlankWithZero = true;
```

### `keepRowHeightOnPrint` (Boolean)
Preserves on-screen row heights when exported via a print layout, instead of StyleBI's default
auto-adjustment for print.
```js
FreehandTable1.keepRowHeightOnPrint = true;
```

### `sortOthersLast` (Boolean, default `true`)
When Top/Bottom ranking's "Group all others together" is enabled, controls whether the "Others" group
is forced after every ranked group (`true`, ignoring the specified dimension sort) or placed according
to that sort (`false`).
```js
sortOthersLast = false;
```

---

## Row/column insertion — a known open question, not answered by the official docs

StyleBI's UI supports inserting/appending/deleting rows and columns via right-click context menu
(`Insert Row`, `Append Row`, `Insert Column`, `Append Column`, `Delete Row`, `Delete Column`) and
merging multiple originally-distinct cells (`Ctrl`-click select, then "Merge Cells" — for combining
static cells, not the same as `setMergeCells`'s expanded-cell merging above). The official
documentation does not state whether an existing `$<name>` cross-cell reference automatically survives
a row/column insertion that shifts the referenced cell's position, or whether references are resolved
by name (survives insertion) vs. position (would need rewriting). **This is worth confirming
empirically** rather than assuming either way — the naming mechanism above strongly suggests references
are name-based (looked up by the `$name` string, not by `{row, col}`), which would make them
insertion-safe by construction, but this reference doc does not itself state that as a documented
guarantee.
