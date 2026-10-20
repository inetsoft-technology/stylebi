# Table row-height follow — manual checks

**Design:** [table row-height follow](../specs/lookfeel/2026-10-19-table-row-height-follow-design.md)
**Plan:** [2026-10-19-table-row-height-follow.md](./2026-10-19-table-row-height-follow.md)

## Setup

- Build and run a server from `feature-table-row-height-follow`. It needs a full build: the change spans core and the portal.
- Set the org density to **compact**.
- In a dashboard created with the modern gate on, so its assemblies are marked, add:
  - a table
  - a crosstab with **2 column-header rows**
  - a freehand table
- Also keep one **unmarked** (legacy) dashboard with a table.
- **Set Cell Size:** right-click a cell, then *Set Cell Size*. The dialog is titled "Resize Cell".
- **Compact values:** a data row draws at 24 and the dialog shows 16. A header row draws at 26 and the dialog shows 18. Comfortable is 28 for data rows and 30 for header rows.

## Checks

| ID | Steps | Expected |
|---|---|---|
| MT-1 | On a table never resized, open Set Cell Size on a data cell. Change only Cell Width, then OK. Then switch the dashboard density to comfortable. | Before OK the box is ticked and Cell Height is greyed at 16. The width changes. After the switch, data rows draw at 28. |
| MT-2 | Drag a data row border on the table, then reopen Set Cell Size on a data cell. Tick the box, then OK. | Before ticking, the box is unticked and Cell Height is editable. After OK the data rows are back on the tier (24 at compact). |
| MT-3 | Open Set Cell Size on a following data row. Untick the box and press OK without typing. Then change the dashboard density. | OK doesn't move the row. The density change no longer moves it either. |
| MT-4 | Crosstab, never resized: open Set Cell Size on a cell in the first column-header row. Untick, type 30, then OK. | The first header row changes. The second header row keeps exactly the height it drew before (26 at compact). |
| MT-5 | Same crosstab: open Set Cell Size on a header cell. Tick the box, then OK. | Every header row returns to the tier. |
| MT-6 | Freehand table: repeat MT-1 and MT-4. | Same results as on the table and the crosstab. |
| MT-7 | Unmarked dashboard: open Set Cell Size on a table cell. | No box. The dialog looks and behaves as before. |
| MT-8 | Open the marked dashboard in the viewer, then in the composer's preview. Use Set Cell Size in each. | No box in either. The dialog looks and behaves as before. |
| MT-9 | After MT-2's tick, press Undo once. | One undo step restores the pinned height. |
| MT-10 | After MT-1, export the dashboard to PDF. | The exported data rows match what the composer draws. |
| MT-11 | A table with wrapped text: pin a data row (as in MT-2), then tick the box and press OK. | Wrapped rows now grow in whole-row steps, as on a never-resized table. This is expected, not a regression. |

## Results

| ID | Result | Notes |
|---|---|---|
| MT-1 | | |
| MT-2 | | |
| MT-3 | | |
| MT-4 | | |
| MT-5 | | |
| MT-6 | | |
| MT-7 | | |
| MT-8 | | |
| MT-9 | | |
| MT-10 | | |
| MT-11 | | |
