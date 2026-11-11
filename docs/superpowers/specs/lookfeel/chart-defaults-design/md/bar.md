<!-- Markdown version of bar/bar.html. The drawn "proposed look" cards are in the HTML page. -->

# Bar and interval charts

Family page. It lists only what differs from the [common rules](common.md) (C1 to C24). The financial family is on [its own page](financial.md). Facts marked *observed* come from live charts on a server dashboard, with every chart style and format at its default and made-up sales data. Facts marked *traced* come from reading the code. Nothing here has been checked on a customer dashboard.

## Status

| Type | Status | What exists |
|---|---|---|
| Bar, vertical and horizontal | Written up | Canonical plus 19 variants: categories, series, label length, edge values, stacked and clustered, card size. |
| Interval | Written up | One chart: Product on X, Low on Y and Range on Size. The bar runs from Low to Low plus Range. |

## Decisions

All confirmed on 2026-11-03.

- **Follows C19, the density padding on every side.** Applies to bar, horizontal bar and interval. The measured top gap below the title line on the canonical capture is 5 to 7px, about half the density padding at compact. Confirmed on 2026-11-03.
- **Follows the common rules.** No line under the title (C2), plot padding by density (C19, in the first bullet), grid and axis lines (C3, C4), axis labels (C5, with the horizontal rule), axis titles off (C6), single colour from the modern palette slot 1 (C11), legend rules (C7 to C10, and C24 for stack order), the bar width floor and cap (C17), the zero line (C18) and bar corners (C22). On bar these were all observed as shipped differently: the single colour is the legacy blue, both axis titles are on, and the title line shows.
- **Bar width is a proportion of the category slot.** About 50% of the slot, never narrower than 3px and never wider than 32px. Shipped bars are 45% to 52% of the slot with no limit: 3 regions give 56px blocks. The 32px cap is a little wider than box plot's 28px, because a bar is not a box with whiskers.
- **Stacked is the default when a Color dimension is bound.** This is shipped behaviour and stays. Clustered is an author choice through the Stack box.
- **Horizontal bar lists the first category at the top.** Shipped draws the first category at the bottom, the reverse of the vertical chart and of reading order. Confirmed on 2026-11-04. The build still needs a check of how the sort option interacts.
- **Horizontal labels get a width cap.** The category band is at most 30% of the card width, and names truncate with an ellipsis at about 20 characters. The vertical rule is 12 characters (C5), because rotated text costs height.
- **A zero line when values are negative.** Drawn in the axis line colour (C4), so bars hang from a visible baseline. Shipped draws it like any gridline.
- **Corners round at the free end only, with a small radius.** The shipped modern seed is a radius of 0.3 of the bar width, so a 32px bar gets about a 10px curve, and the base stays square. That looks half-finished, most of all on a horizontal bar. The radius becomes about 0.08 of the bar thickness, with a floor of 2px and a cap of a quarter of the thickness (C22). A typical 21px bar gets 2px and the widest 32px bar about 2.6px. Without the floor a typical bar would get under 2px and look square. The floor was added on 2026-11-04. The base stays square against the axis, and only the outermost segment of a stack rounds, as now. An author's own radius still wins. Confirmed on 2026-11-03.
- **Interval follows bar.** Its bars use the same width rule and the same 0.08 corner radius, with both ends rounded because both are free. The Size field takes a measure and sets the length of the bar. When Size is bound, the engine forces every bar to the top of the size range, so interval bars come out fatter than bars (build item 6). The unit and the meaning go in the card title, such as "Price range by product, $", since the Y axis title would name only the start measure (C6 turns it off). Confirmed on 2026-11-03.
- **The legend lists series in stack order, top segment first.** Shipped lists Brand H first although it stacks last, so the legend and the bar run in opposite directions. Applies to stacked bar, and to a horizontal stack the same way. Confirmed on 2026-11-03.
- **No change for skew and zeros.** One dominant bar and zero categories read fine. A log scale and top N are not defaulted.
- **Clustered with many series is not fixed by a default.** Eight clustered series give 2px bars. Stacking avoids it, and it is the shipped default.

## Proposed look

Drawn to the decisions at the default card size, with invented values. Titles off, no title line, horizontal grid only, no value axis line, one palette colour, a top legend for the stacked chart, and rotated at most 45° labels where they do not fit.

### Light

*The drawn cards that show the proposed look are in the HTML page.*

### Dark

## Shipped today against the decisions

| Setting | Shipped today | Decided |
|---|---|---|
| Colour, one series | The legacy blue, with rounded bar tops. *Observed.* | Modern palette slot 1 (C11). |
| Colour, several series | Modern categorical palette, stacked. *Observed.* | Unchanged. |
| Title line | A horizontal line under the card title. *Observed.* | Removed (C2). |
| Axis titles | Both on, for example "Sum(Sales)" and "Product". *Observed.* | Both off (C6). |
| Category labels, vertical | Rotated 90° even for 8 short names, cut at about 15 characters. *Observed.* | Thin, then rotate to at most 45°, truncate at about 12 (C5). |
| Category labels, horizontal | Full names, no cap. *Observed.* | Band at most 30% of the width, ellipsis at about 20. |
| Bar width | 45% to 52% of the slot, no limit. *Observed.* | 50% of the slot between 3px and 32px. |
| Stacking | Stacked when a Color dimension is bound. *Observed.* | Unchanged. |
| Horizontal order | First category at the bottom. *Observed.* | First category at the top. |
| Zero line | Not distinct from other gridlines. *Observed.* | Axis line colour when values are negative. |
| Legend | Right, for every series count. *Observed.* | Top for 2 to 5 entries, right above 5 (C7). |
| Bar corners | Radius 0.3 of the bar width, free end only: about 10px on a 32px bar. *Traced and observed.* | Radius about 0.08 with a 2px floor, free end only (C22). |
| Value labels | Abbreviated with K, no unit. *Observed.* | Unchanged. |

## Variants

Captured from live charts on a server dashboard, with every chart style and format at its default. The data is made up: 1,600 sales rows with 8 products, 3 regions, 30 stores, 8 warehouses with long names, 2 channels, 5 segments and two brand lists of 8 and 12. Only the bound fields and the card size differ between variants.

| Live capture | Variant | What it shows | What it means for the defaults |
|---|---|---|---|
| ![Bar 8 products (canonical)](../bar/img/bar.png) | 8 products (canonical) | One legacy-blue bar per product with rounded tops. Both axis titles are on, there is a line under the card title, and the category labels are rotated 90°. The plot is about 55% of the card height. Value labels are abbreviated (20K). | C2 title line, C5 label rule, C6 axis titles, C11 colour. |
| ![Bar 3 regions](../bar/img/bar_few_3_.png) | 3 regions | Three blocks about 56px wide, roughly 45% of each slot. Labels sit horizontal and the chart looks heavy. | Bar width cap. |
| ![Bar 30 stores](../bar/img/bar_many_30_.png) | 30 stores | Every one of the 30 labels is drawn, rotated 90°, and the bars are about 6px wide. Labels take about a quarter of the card height. | C5: thin the labels first. |
| ![Bar 28-character warehouse names](../bar/img/bar_long_labels.png) | 28-character warehouse names | The labels are cut to about 15 characters with two dots, rotated 90°, and take about 40% of the card height. | C5: truncate at about 12 characters and cap the label band. |
| ![Bar 2 series on Color (stacked by default)](../bar/img/bar_2_series.png) | 2 series on Color (stacked by default) | A colour dimension stacks the series. Legend on the right. | Stacking stays the default. C7 moves the legend to the top. |
| ![Bar 5 series](../bar/img/bar_5_series.png) | 5 series | Five stacked segments with a five-entry legend on the right, about a quarter of the width. | C7: top legend for 2 to 5 entries. |
| ![Bar 8 series](../bar/img/bar_8_series.png) | 8 series | Eight segments on each bar. The legend lists the series in reverse stack order, with eight entries on the right. | C7: right legend above 5 entries. |
| ![Bar 12 series](../bar/img/bar_12_series.png) | 12 series | Twelve segments, and the legend wraps into two columns and takes about 40% of the card width. | C9 caps the legend at 30%, so it must truncate or paginate. |
| ![Bar Negative values](../bar/img/bar_negative_values.png) | Negative values | Bars hang below zero and the category labels stay at the bottom of the plot. The zero line looks the same as the other gridlines. | A zero line in the axis line colour when values are negative. |
| ![Bar One dominant bar](../bar/img/bar_one_dominant.png) | One dominant bar | The tallest bar is about 20 times the others, which shrink to a few pixels. Axis and labels are fine. | No change. A log scale is not a default. |
| ![Bar Zero categories](../bar/img/bar_zeros.png) | Zero categories | Five of eight categories sum to zero. They show no mark and keep their labels. | No change. |
| ![Bar 2 series, clustered](../bar/img/bar_clustered_2_.png) | 2 series, clustered | Side by side bars about 9px wide. Reads well. Needs the Stack box unchecked. | Clustered stays an author choice. |
| ![Bar 8 series, clustered](../bar/img/bar_clustered_8_.png) | 8 series, clustered | Eight bars of 2px per category. Unreadable. | No default change. Stacking avoids it. |
| ![Bar Horizontal, 8 products](../bar/img/bar_horizontal.png) | Horizontal, 8 products | Labels sit horizontal at the left, right-aligned, and the value axis is at the bottom. The first category, Apparel, is at the bottom and Toys is at the top, the reverse of the vertical chart. | Proposed: first category at the top. Same rules for titles and grid. |
| ![Bar Horizontal, 30 stores](../bar/img/bar_horizontal_30_.png) | Horizontal, 30 stores | The 30 bars are about 3px thick and the labels overlap. The axis already drops every second label. | C5 thinning, and a minimum slot for a label. |
| ![Bar Horizontal, 28-character names](../bar/img/bar_horizontal_long_labels.png) | Horizontal, 28-character names | The full names are drawn and take about 35% of the card width. The bars are squeezed into the rest. | Label band at most 30% of the width, truncated with an ellipsis. |
| ![Bar Horizontal, 2 series](../bar/img/bar_horizontal_2_series.png) | Horizontal, 2 series | Stacked, with the legend on the right. | Same as vertical. |
| ![Bar Horizontal, 8 series, clustered](../bar/img/bar_horizontal_clustered.png) | Horizontal, 8 series, clustered | Eight thin bars per category. Unreadable. | No default change. |
| ![Bar Interval: Low on Y, Range on Size](../bar/img/interval.png) | Interval: Low on Y, Range on Size | Floating bars from Low to Low plus Range. The bars are about 30px thick, roughly 75% to 80% of the slot, against about 21px (50%) for a bar chart on the same 8 categories. Both ends are free, so all four corners are rounded with the 0.3 radius and the bars look like pills. The Y axis title says only "Sum(Low)", which names the start and not the range. No legend. | The 0.08 radius with the 2px floor from the bar corner decision (C22) leaves them with a small 2px corner. C6 turns the axis titles off, and the card title says "Low to high". |
| ![Bar Small card, 300×180](../bar/img/bar_small_card_300x180_.png) | Small card, 300×180 | The plot is about 40px tall. Rotated labels, the axis title and the title line take the rest. | C5 label band and C6 titles off give the plot about 65%. |
| ![Bar Large card, 800×480](../bar/img/bar_large_card_800x480_.png) | Large card, 800×480 | Labels are horizontal and the eight bars are about 46px wide. | Bar width cap. |

## Build items

| # | Item | Cost | Where | Notes |
|---|---|---|---|---|
| 1 | Bar width as a proportion with a floor and a cap | New behaviour | The width ratio of the bar element in the graph package. Not traced. | Also covers box plot, with its own numbers. Check how the ratio interacts with clustered groups. |
| 2 | Horizontal category order, first at the top | New behaviour | The coordinate and scale setup for a rotated chart. Not traced. | Check the sort option and any saved chart that relies on the current order. |
| 3 | Horizontal label band and ellipsis | New behaviour | `DefaultAxis` label layout | Shares the label rule in C5, with a width cap in place of a height cap. |
| 4 | Zero line for negative values | New behaviour | The plot coordinate baseline. Not traced. | Uses the axis line colour from C4. |
| 5 | Bar corner radius 0.08 (confirmed) | Seed only | `ChartVSAssemblyInfo` where the modern seed writes the bar corner radius (0.3 today) | One number. An author radius is kept through `userBarCornerRadius`. The 2px floor is capped at a quarter of the thickness, so the 3px thin bar keeps a corner under 1px. |
| 5b | 2px floor and quarter-thickness cap on the bar corner | Small change | `BarVO.buildRoundedBarShape`, the one place that builds a rounded bar for bar, horizontal bar, interval, pareto, waterfall and Gantt. Traced. | Today `arc = min(fraction × short side, short side / 2)`. Change to `min(max(fraction × short side, min(2px, short side / 4)), short side / 2)` when the fraction is above 0. An author fraction that gives a larger corner still wins, and a zero fraction still turns rounding off. The 2px is in screen pixels, so scale it with the screen transform. Stack rounding and the open-end path call the same function. Funnel and 3D bars are skipped before it. The box plot painter needs its own change (item 2b on the financial page). |
| 6 | Legend in stack order for stacked bars | Small change | The legend entry order for a stacked series. Not traced. | Check that clustered bars keep their natural order, and that a stacked area follows the same rule. |
| 7 | Interval bar thickness follows the bar width rule when Size is bound | Small change | `GraphGenerator.fixSizeFrame`, which sets the smallest size to the largest minus 1 for an interval with a measure on Size, and the linear size frame default largest of 30 | Traced, not run. The forced largest size gives interval bars about 30px thickness and overrides the width rule above. Cap the largest at the same limit, or apply the width rule after this step. The Size measure must still set the bar length. |
| 8 | Common items | Common | See the common page | C2, C5, C6, C7, C9 and C11 apply to bar without extra work here. |

## Open items

- None.

## Known limits

Settled: no default change.

- With 30 or more categories a standard card cannot be read. There is no top N or scroll default, and the author filters or enlarges the card.
- Value labels show "20K" with no unit. The card title carries the unit (C5).
