<!-- Markdown version of gantt/gantt.html. The drawn "proposed look" cards are in the HTML page. -->

# Gantt

Family page. It lists only what differs from the [common rules](common.md) (C1 to C24). Other family pages: [bar and interval](bar.md), [line and area](line.md), [point and contour](point.md), [pie and donut](pie.md), [radar](radar.md), [hierarchy](hierarchy.md), [relation](relation.md), [funnel, waterfall, pareto and marimekko](funnel-waterfall.md), [geo](geo.md) and [financial](financial.md). Facts marked *observed* come from live charts on a server dashboard with modern chrome, every chart style and format at its default, and made-up data. The translucent card is not in the build yet. All captures are from the composer design view. The portal viewer was checked on 2026-11-04 and matches them. The first milestone capture had no bars because I had not bound Start and End on that chart. It was rebuilt with all three fields and re-captured, and the result is as shown. Preview of the saved dashboard was compared again after the rebuild and matches all seven captures. Facts marked *traced* come from reading the code. All decisions on this page were confirmed on 2026-11-04.

## Status

| Type | Status | What exists |
|---|---|---|
| Gantt | Written up | Six charts: 8, 25 and 60 tasks, long task names, colour by project, and with milestones. |
| Small card | Captured | One chart at 300×180. |

## Decisions

- **Follows the common rules.** No title line (C2), plot padding by density (C19), grid and axis lines (C3, C4), the label rule (C5, with the horizontal rule for task names), axis titles off (C6), the legend at the top for 2 to 5 entries (C7 to C10, with no title), palette colour 1 for bars (C11), bar thickness (C17) and bar corners on both ends (C22). The rotated "Task" title on the left is redundant next to the task names.
- **Bars take palette colour 1 and have small corners.** Shipped draws bars in a legacy steel blue with ends rounded to about half the bar height, so a short bar looks like a pill. The new bar uses the palette colour 1 (C11) and a corner of about 0.08 of the bar height on both ends, with the 2px floor of C22 (a 10px bar gets 2px; below 8px the corner is a quarter of the thickness), as decided for bars. With a colour field the bars take the palette as shipped.
- **Bar height follows the bar page: about 50% of the row, at least 3px, at most 32px.** Shipped fills about 60% of the row, so at 8 tasks the bars are about 12px tall and look chunky with their rounded ends. The new bar is 50% of the row pitch, which is about 10px at 8 tasks. It is never under 3px and never over 32px, the same rule as bar, horizontal bar, interval, pareto and waterfall, so the spec has one thickness rule. With 3 tasks the rows are far apart and the bars are up to 32px thick, as 3 bars are on the bar page. Confirmed on 2026-11-04. This replaces an earlier cap of 10px, which was confirmed first and then changed the same day to match the bar page.
- **The time axis never cuts its labels and the plot keeps padding at the right.** At 25 tasks and more, the time labels are cut to "2..", "0..", although the card has room. Show a month name and the year once ("Jan", "Feb", "2026") and drop labels that would not fit, as the line chart does for dates. Keep the density padding (C19) beyond the last date so that the last bar does not touch the edge.
- **Grid lines are fewer and lighter.** Shipped draws a faint line at every tick, which at 25 tasks is a comb of dozens. Draw them at the labelled ticks only, in the grid colour (C4).
- **Task labels are thinned and never cut mid-name where there is room.** At 60 tasks about half the names show, which is correct, and bars are 1 to 2px tall. Keep a minimum bar height of about 3px and thin the labels. With long names, the label column is capped at about 30% of the card width and the names are cut with an ellipsis (C5).
- **Milestones are small diamonds with a named legend.** Shipped draws diamonds of about 20px, larger than the bars. Cap the diamond at about 10px, in palette colour 2 or the ink colour, and name the legend after the milestone field, not "Measures".

## Authoring notes

Not defaults. Two things that came up while making the captures and that a new user is likely to hit. Noted on 2026-11-04.

- **Start, End and Milestone take date measures only.** A date column from a worksheet is listed under Dimensions in the chart editor, and the slots reject it without a message. To use it: right-click the date field in the tree of that chart, choose Convert To Measure, then drop it on the slot. This has to be done in each chart. *Observed.* The rule is in the drop check, which accepts a field only if it is not a dimension and its type is a date (`vs-chart-editor.service.ts`, `isFieldAcceptable` and `isDateMeasure`), and again on the server (`ChartDndHandler.dropToGantt`, which requires a measure of a date type and sets its formula to none). *Traced.* A group-and-aggregate step in the worksheet does not help: a date aggregated with Max is still listed as a dimension. *Observed.*
- **The task field goes on the Y shelf.** Dropped on X, the chart draws the task names as column headers over a single row of bars, and the time axis is repeated under each task. Dropped on Y it draws the usual Gantt, with names on the left and time along the bottom. *Observed.* The generator reads the last Y dimension as the task axis and transposes the chart (`GanttGraphGenerator.createEGraph0`). *Traced.*

## Proposed look

Drawn to the decisions at the default card size, with invented values. Bars in palette colour 1 with small corners, no title line, month labels, and a titled legend at the top when coloured.

### Light

*The drawn cards that show the proposed look are in the HTML page.*

### Dark

## Shipped today against the decisions

| Setting | Shipped today | Proposed |
|---|---|---|
| Bar colour, no colour field | A legacy steel blue. *Observed.* | Palette colour 1 (C11). |
| Bar height, few tasks | About 60% of the row, about 12px at 8 tasks. *Observed.* | 50% of the row, between 3px and 32px (as bars). |
| Bar corners | About half the bar height, pill ends. *Observed.* | About 0.08 of the bar height, at least 2px (C22). |
| Time axis labels | Cut to "2..", "0.." from about 25 tasks. *Observed.* | Month names with the year once, labels dropped when they do not fit. |
| Time axis end | The last bar touches the right edge. *Observed.* | Density padding past the last date (C19). |
| Grid lines | A faint line at every tick, dozens at 25 tasks. *Observed.* | At the labelled ticks only, in the grid colour. |
| Bar height at 60 tasks | 1 to 2px. *Observed.* | At least about 3px, with thinned labels. |
| Milestone diamond | About 20px, a slate blue, legend titled "Measures". *Observed.* | About 10px, named after the field. |
| Task axis title | A rotated "Task" title on the left. *Observed.* | Removed. The names say it. |
| Title line | A line under the card title. *Observed.* | Removed (C2). |

## Variants

Captured from live charts on a server dashboard, with every chart style and format at its default. The data is made up: 8, 25 and 60 tasks across four projects and five owners, between January and June 2026, with durations of 1 to 45 days and one milestone per task. Only the bound fields and the number of tasks differ between variants.

| Live capture | Variant | What it shows | What it means for the defaults |
|---|---|---|---|
| ![Gantt, 8 tasks (canonical)](../gantt/img/gantt_8_tasks.png) | Gantt, 8 tasks (canonical) | Task names read in full on the left and the time axis reads "2026-01-01", "02-01". Bars are a legacy steel blue with heavy rounded ends, about 14px tall. A bar one day long is a 3px sliver, and the last bar touches the right edge of the plot. There is a rotated "Task" axis title on the left. | One palette colour, small bar corners, padding on the time axis, axis title rule. |
| ![Gantt, 25 tasks](../gantt/img/gantt_25_tasks.png) | Gantt, 25 tasks | Rows are about 8px apart, so the bars are thin. The time axis labels are cut to "2..", "0..", "0.." although the card has room, and there are dozens of faint vertical grid lines, one per tick. | Time axis labels must not be cut. Fewer, lighter grid lines. |
| ![Gantt, 60 tasks](../gantt/img/gantt_60_tasks.png) | Gantt, 60 tasks | Only about half of the task names show. The bars are 1 to 2px hairlines and the plot is a dense field. The time labels are cut as at 25 tasks. | Thin the task labels instead of cutting them. Keep a minimum bar height. |
| ![Gantt, long task names](../gantt/img/gantt_long_task_names.png) | Gantt, long task names | Names are cut at about 40 characters with "..", and the label column takes about a third of the card width. | C5 label cutting, and a cap on the label column. |
| ![Gantt, colour by project](../gantt/img/gantt_colour_by_project.png) | Gantt, colour by project | Bars take the modern palette (blue, orange, navy, teal) with a titled Project legend on the right. The legend takes about 20% of the width, so the time axis is shorter than without it. | No change to the colours. The legend moves to the top, with no title and aligned to the plot's left edge (C7), so the time axis keeps its full width. |
| ![Gantt, with milestones](../gantt/img/gantt_with_milestones.png) | Gantt, with milestones | Bars and diamonds in the same blue family. The diamonds are about 20px, wider than the rows are tall, and they hide the bars: at 25 tasks a bar is a thin line behind a diamond. The legend is titled "Measures" with the entries Start and Milestone, a generic title that leaves out End and does not name the milestone field. | A smaller diamond, a distinct colour from the bars, and a named legend. |
| ![Gantt, small card (300×180)](../gantt/img/gantt_small_card_300x180.png) | Gantt, small card (300×180) | At the real 300×180 size all eight task names still show and the bars are about 8px tall. The time axis loses labels: the first is cut to "2026-.." and the 04-01 label is dropped. The last bar touches the right edge. | Time labels that are not cut (C5), and padding on the time axis (C19). |

## Build items

| # | Item | Cost | Where | Notes |
|---|---|---|---|---|
| 1 | Bars in palette colour 1 with a corner of about 0.08 | Seed only | The Gantt element colour frame and the bar corner seed. Not traced. | Part of C11 and the bar corner decision. Old saved charts keep their blue. |
| 2 | Month labels and padding on the time axis | New behaviour | `GanttGraphGenerator`: the time scale is a `TimeScale` with abbreviated labels and `setLabelBetween(true)`. Traced. | The abbreviation is what cuts the labels. Use the line chart date label rule. |
| 3 | Grid lines at labelled ticks only | Seed only | The Gantt coordinate grid settings. Not traced. |  |
| 3b | Bar height 50% of the row, between 3px and 32px | Seed only | The Gantt bar size in `GanttGraphGenerator` or the size frame default. Not traced. | The same rule as bars on the bar page. |
| 4 | Minimum bar height and thinned task labels | New behaviour | The Gantt bar size and the category axis label thinning. Not traced. |  |
| 5 | Smaller milestone diamond and a named legend | Seed plus a fix | `GraphGenerator` adds a second element, a point element with a diamond shape and `HINT_GANTT_MILESTONE`, on top of the bar element. Traced. The legend title comes from the measure group. | Checked on 2026-11-04: with Start, End and Milestone bound, the bars do draw. They were missing before only because the chart was built without Start and End. Not a bug. |
| 6 | Remove the Task axis title | Seed only | The Gantt axis title default. Not traced. | Same rule as the other families. |
| 7 | Common items | Common | See the common page | C2, C5, C7, C9 and C19 apply without extra work here. |

## Open items

- None.

## Known limits

- With 60 tasks or more, rows are only a few pixels tall and not every name can show.
- Tasks with a one-day length are a few pixels wide on a six-month axis.
