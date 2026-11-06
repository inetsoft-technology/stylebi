<!-- Markdown version of line/line.html. The drawn "proposed look" cards are in the HTML page. -->

# Line and area charts

Family page. It lists only what differs from the [common rules](common.md) (C1 to C24). Other family pages: [bar and interval](bar.md) and [financial](financial.md). Facts marked *observed* come from live charts on a server dashboard, with every chart style and format at its default and made-up sales data. Facts marked *traced* come from reading the code. Nothing here has been checked on a customer dashboard. All decisions on this page were confirmed on 2026-11-03.

## Status

| Type | Status | What exists |
|---|---|---|
| Line, step line, jump line | Written up | Canonical plus 13 line variants, and one each of step and jump line. |
| Area, step area | Written up | One each, and two multi-series variants. The stacked versions are not built as separate charts. |

## Decisions

- **Follows C19, the density padding on every side.** Applies to line, step and jump line, area and step area. The measured top gap below the title line on the canonical capture is 5px, about half the density padding at compact. Confirmed on 2026-11-03.
- **Follows the common rules.** No title line (C2), plot padding by density (C19, in the first bullet), grid and axis lines (C3, C4), axis labels (C5), axis titles off (C6), single colour from the modern palette slot 1 and further measures in palette order (C11), legend rules (C7 to C10) and the zero line (C18). Observed shipped differently on line: the single colour is the legacy blue, two measures use a dark blue and a grey-blue from the legacy palette, both axis titles are on, and the title line shows.
- **Straight segments by default.** Shipped lines and areas are smooth curves. With four quarterly points the curve draws a hump that is not in the data and invents values between points. Straight segments show only what was measured. Smooth stays available as an author choice. Confirmed on 2026-11-03.
- **A raw date on the X shelf groups by month.** It groups by year today, so two years of daily data become two points. A fixed month default has no data lookup and suits the common case of a year or two of daily rows. For very short or very long spans the author picks the level, as now. A span-based rule (day, week, month, quarter or year from the data range) is a possible later step if month proves wrong often. Confirmed on 2026-11-03.
- **Area with a Color dimension stacks by default.** Shipped overlaps the areas with transparency, drawn back to front, so five series hide each other. Bar already stacks on a Color dimension, so stacking is consistent. The author can switch it off. Confirmed on 2026-11-03.
- **Gaps stay bridged by a dashed grey segment.** It is already a clear convention, so there is no option to turn it off. Shipped, kept. Confirmed on 2026-11-03.
- **Labels follow C5.** Thinning already works for 105 weeks and 731 days. It does not work for 105 long labels, which are all drawn rotated and merge into a black band. The band cap and truncation apply there.
- **A zero line when values are negative,** in the axis line colour. Same as bar. Confirmed on 2026-11-03.
- **Line value axis does not start at zero, area does.** Shipped, kept. The area fill is a magnitude, so a zero baseline is right.
- **No default change for many series.** Five lines tangle and twelve are unreadable. That is for the author to fix with filtering, top N or small multiples.

## Proposed look

Drawn to the decisions at the default card size, with invented values. Titles off, no title line, horizontal grid only, no value axis line, straight segments, one palette colour, and a top legend for the multi-series charts.

### Light

*The drawn cards that show the proposed look are in the HTML page.*

### Dark

## Shipped today against the decisions

| Setting | Shipped today | Proposed |
|---|---|---|
| Colour, one series | The legacy blue. *Observed.* | Modern palette slot 1 (C11). |
| Colour, two measures | Dark blue and grey-blue from the legacy palette. *Observed.* | Modern palette order (C11). |
| Line shape | Smooth curve. *Observed.* | Straight segments (confirmed). |
| Title line and axis titles | Line under the title, both axis titles on. *Observed.* | Both removed (C2, C6). |
| Period labels | Rotated 90°, thinned for long series, not thinned for long labels. *Observed.* | C5. |
| Date on the X shelf | Grouped by year. *Observed.* | Grouped by month (confirmed). |
| Area with a Color dimension | Overlaid, with transparency. *Observed.* | Stacked (confirmed). |
| Gaps | A dashed grey bridge. *Observed.* | Unchanged. |
| Value axis | Line: not zero. Area: zero. *Observed.* | Unchanged. |
| Zero line | Same as other gridlines. *Observed.* | Axis line colour when values are negative (confirmed). |
| Legend | Right. *Observed.* | Top for 2 to 5 entries (C7). |
| Month names as text | Sorted alphabetically. *Observed.* | Open item. |

## Variants

Captured from live charts on a server dashboard, with every chart style and format at its default. The data is made up: two years of daily sales by 3 regions, 2,193 rows, with text period columns of 4, 24, 105 and 731 values, a raw date, 5 products and 12 brands. Only the bound fields and the card size differ between variants. The spike variant was dropped: a single large day vanished into the monthly sum.

| Live capture | Variant | What it shows | What it means for the defaults |
|---|---|---|---|
| ![Bar 24 periods (canonical)](../line/img/line.png) | 24 periods (canonical) | One smooth blue curve with the legacy colour. Both axis titles are on and the title line shows. The 24 labels are rotated 90° and none are dropped. The value axis starts at 8K, not zero. | C2, C5, C6, C11. Straight segments. |
| ![Bar 4 quarters](../line/img/line_few_4_.png) | 4 quarters | The smooth curve rounds a hump between four points that are really a rise and a fall, and invents values between quarters. | Straight segments by default. |
| ![Bar 105 weeks](../line/img/line_many_105_.png) | 105 weeks | Labels are thinned to about every third and rotated 90°. The last week has one day, so the line drops at the end. That is the data, not the chart. | Thinning already works here. |
| ![Bar 731 days](../line/img/line_very_many_731_.png) | 731 days | The line is a dense wiggle and the labels are thinned to every 22nd. It reads as a trend. | No change. |
| ![Bar A raw date on the X shelf](../line/img/line_date_axis.png) | A raw date on the X shelf | The date groups by year, so a two-year daily series becomes two points and one straight line. | Month by default, not year. |
| ![Bar Month names as text](../line/img/line_month_names.png) | Month names as text | The months sort alphabetically: Apr, Aug, Dec, Feb. The curve implies a sequence that is not there. | Open item. No default change proposed. |
| ![Bar 105 long labels](../line/img/line_long_labels_105_.png) | 105 long labels | All 105 labels are drawn, rotated 90°, and merge into a black band of about 40% of the card height. They are not thinned. | C5: thin first, cap the band, truncate. |
| ![Bar 3 series on Color](../line/img/line_3_series.png) | 3 series on Color | Three smooth lines in the modern palette with a legend on the right. | C7 moves the legend to the top. |
| ![Bar 5 series](../line/img/line_5_series.png) | 5 series | Five lines cross each other and read as a tangle. The legend is on the right. | No default change. C7: top legend. |
| ![Bar 12 series](../line/img/line_12_series.png) | 12 series | Twelve lines are unreadable. The legend wraps into two columns and takes about a quarter of the width. | No default change. C9 cap holds. |
| ![Bar 2 measures on the Y shelf](../line/img/line_2_measures.png) | 2 measures on the Y shelf | Both lines share one axis titled "Sum(Orders) / Sum(Sales)", in a dark blue and a grey-blue from the legacy palette, with a "Measures" legend. | C11: further measures follow the modern palette. |
| ![Bar Negative values](../line/img/line_negative_values.png) | Negative values | The curve crosses zero four times. The zero line looks the same as the other gridlines. | A zero line in the axis line colour, as for bar. |
| ![Bar Three missing periods](../line/img/line_gap.png) | Three missing periods | The line is bridged across the gap by a dashed grey segment. | Keep. It is already a clear convention. |
| ![Bar Step line](../line/img/step_line.png) | Step line | Horizontal runs with vertical risers. Same chrome as the line. | Same rules. |
| ![Bar Jump line](../line/img/jump_line.png) | Jump line | Separate horizontal dashes with no risers. | Same rules. |
| ![Bar Area](../line/img/area.png) | Area | A smooth fill at about 70% opacity, starting at zero where the line chart does not. | C11 slot 1. |
| ![Bar Step area](../line/img/step_area.png) | Step area | A stepped fill. | Same rules. |
| ![Bar Area with 3 series](../line/img/area_3_series.png) | Area with 3 series | The areas overlap, drawn back to front with transparency. They are not stacked, unlike bar. | Stacked by default, like bar. |
| ![Bar Area with 5 series](../line/img/area_5_series.png) | Area with 5 series | Five overlapping areas hide each other and read as a blob. | Stacked by default fixes most of it. |
| ![Bar Small card, 300×180](../line/img/line_small_card_300x180_.png) | Small card, 300×180 | The plot is about 40px tall and only three value labels fit. The rotated period labels take a third of the height. | C5 label band and C6 titles off. |
| ![Bar Large card, 800×480](../line/img/line_large_card_800x480_.png) | Large card, 800×480 | Reads well. Labels thin to every second period. | No change. |

## Build items

| # | Item | Cost | Where | Notes |
|---|---|---|---|---|
| 1 | Straight segments as the default (confirmed) | Seed only | The line element smoothing property. Not traced. | Check whether smoothing is a tiered value or a plain one, and whether old saved charts must keep their curve. |
| 2 | Date grouping defaults to month (confirmed) | Small change | The date level chosen when a date is dropped on a shelf. Not traced. | A fixed default, so no data lookup. Applies to every chart with a date on a shelf, so check bar and area. An author level wins. Where the year default comes from is not traced. |
| 3 | Stacked area with a Color dimension (confirmed) | Seed only | The area chart style setup. Not traced. | Same Stack box as bar. The choice of the author wins. |
| 4 | Zero line for negative values (confirmed) | New behaviour | The plot baseline | Shared with bar. |
| 5 | Common items | Common | See the common page | C2, C5, C6, C7, C9 and C11 apply to line and area without extra work here. |

## Open items

- None.

## Known limits

Settled: no default change.

- Month names stored as text sort alphabetically. A natural month and weekday order is a data-model question, not a chart default.
- Stacked line has no meaning on its own, so the stacked variants of the line types were not built.
