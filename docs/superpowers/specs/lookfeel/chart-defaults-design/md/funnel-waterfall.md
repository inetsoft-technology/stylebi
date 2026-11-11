<!-- Markdown version of funnel-waterfall/funnel-waterfall.html. The drawn "proposed look" cards are in the HTML page. -->

# Funnel, waterfall, pareto and marimekko

Family page. It lists only what differs from the [common rules](common.md) (C1 to C24). Other family pages: [bar and interval](bar.md), [line and area](line.md), [point and contour](point.md), [pie and donut](pie.md), [radar](radar.md), [hierarchy](hierarchy.md), [relation](relation.md), [Gantt](gantt.md), [geo](geo.md) and [financial](financial.md). Facts marked *observed* come from live charts on a server dashboard with modern chrome, every chart style and format at its default, and made-up data. The translucent card is not in the build yet. All captures are from the composer design view. Preview of the saved dashboard was compared on 2026-11-04 and the portal viewer was checked the same day. Both match all of them. Facts marked *traced* come from reading the code. All decisions on this page were confirmed on 2026-11-04.

## Status

| Type | Status | What exists |
|---|---|---|
| Funnel | Written up | Four charts: 5 and 8 stages, colour by stage, and a small card. |
| Waterfall | Written up | Three charts: 8 and 20 items, and a small card. |
| Pareto | Written up | Four charts: 8 and 20 defects, one dominant defect, and a small card. |
| Marimekko | Written up | Five charts: 4 by 4 and 8 by 6, each with and without colour, and a small card. |
| Geo | Written up | On its own page: [geo](geo.md). |

## Decisions

- **Follows the common rules.** No title line (C2), plot padding by density (C19), grid and axis lines (C3, C4), the label rule (C5), the legend rules (C7 to C10, and C24 for legend order), colour (C11 for the funnel and pareto, C12 for the waterfall), data labels (C14), the bar width floor and cap (C17), the zero line when values cross zero (C18), label colour (C21) and bar corners (C22). Axis titles follow C6: off for the funnel and the waterfall, kept for the pareto and the marimekko, as C6 already says.
- **Funnel stages show their values.** Shipped has no value axis and no labels, only a "Sum(Users)" title under the plot, so the size of a stage can be judged only by eye. The decision is for a value on every stage tall enough for it (at least about 18px), in white or near-black by the fill (C21), and no vertical grid lines behind the funnel (C3: none where the fill is the data). This turns on data labels for the funnel by default, a small exception to C14, in the same way as the treemap family. New behaviour.
- **Funnel colours follow the funnel order.** With a colour field, shipped gives colours in alphabetical order and lists the legend in reverse alphabetical order, so purple lands on the top stage and the legend does not match the picture. Give the first stage palette colour 1 and so on down the funnel, and list the legend in the same order. With no colour field the funnel takes palette colour 1 (C11). New behaviour.
- **Waterfall bars use direction colours and keep the data order.** Shipped draws every bar in the same blue and the Total in purple. C12 already says the waterfall uses the direction colours: teal for an increase and amber for a decrease, and the Total bar in the neutral token. Shipped sorts the items alphabetically, which breaks the running total, so a waterfall keeps the order of its data unless the author sorts it. Bars follow the bar page: a width of about 50% of the category slot, never under 3px and never over 32px. A waterfall bar floats between two values, so both ends are free and both round, with a radius of about 0.08 of the bar thickness and the 2px floor of C22, as the interval does on the bar page. A bar is at least about 2px tall. Grid lines are horizontal hairlines on the value axis only (C3). New behaviour. Confirmed on 2026-11-04.
- **Waterfall bars show their change.** Shipped draws no values, so the size of each step has to be read off the axis, and teal and amber have almost the same luminance, so colour alone is a weak cue for increase and decrease. A value on each bar at least about 18px tall, in white or near-black by the fill (C21), is the second cue the common page relies on. A bar too short for its label shows none, and its value is read from the axis. This turns on data labels for the waterfall by default, an exception to C14 like the funnel's. New behaviour. Confirmed on 2026-11-04.
- **Waterfall value axis ticks include zero.** Shipped ticks at 300, 100, -100, -300, which skips zero. Use round steps that include zero, and draw the zero line (C18).
- **Pareto draws the cumulative line in palette colour 2.** Shipped draws bars and line in one blue. The bars take palette colour 1 and the line palette colour 2 (C11), 1.5px, with the 0 to 100% axis on the right in the same colour as the line. Grid lines are horizontal hairlines for the left axis only (C3). Axis titles stay (C6). The bars grow from the baseline, so they follow the bar page: the free end rounds with a radius of about 0.08 of the bar width and the base stays square.
- **Marimekko colours its segments by the Break By field.** Shipped draws every segment in one blue, separated by 1px white lines, so the break-by dimension cannot be read. When a Break By field is bound and no colour field is set, colour by it, in the palette order, with a titled legend. Column totals above the columns stay, and so do the axis titles (C6). Region names follow C5 (thin, then truncate). Segments under about 2px are kept as a line at least 1px. New behaviour.
- **An axis title that does not fit is hidden, not cut.** At 300×180 the pareto and waterfall Y titles were cut to "Sum(C..." and "Sum(A...". The rule, on C6 of the common page: a title longer than the axis it labels is hidden. For the waterfall C6 turns titles off, so it applies to the pareto and the marimekko. Confirmed on 2026-11-04.
- **Mark floors and caps (C17).** Pareto and waterfall bars are 50% of their slot between 3px and 32px, as for bars. A waterfall bar is also at least about 2px tall. These are set per family, as C17 says.

## Proposed look

Drawn to the decisions at the default card size, with invented values. Funnel with values, waterfall in direction colours with values on the bars tall enough for one, pareto with a second-colour line, and a coloured marimekko with a legend.

### Light

*The drawn cards that show the proposed look are in the HTML page.*

### Dark

## Shipped today against the decisions

| Setting | Shipped today | Proposed |
|---|---|---|
| Funnel fill, no colour field | The legacy steel blue. *Observed.* | Palette colour 1 (C11). |
| Funnel values | None on the stages, no value axis. *Observed.* | A value on each stage of at least about 18px. |
| Funnel legend order, colour by stage | Reverse alphabetical, with colours given in alphabetical order. *Observed.* | Funnel order, top stage first. |
| Funnel grid and axis titles | Vertical grid lines, a rotated "Stage" title and "Sum(Users)". *Observed.* | No grid, no titles. |
| Waterfall bar colours | All one blue, Total purple. *Observed.* | Teal up, amber down, Total in the neutral token (C12). |
| Waterfall values | None. *Observed.* | A value on each bar of at least about 18px (C14, C21). |
| Waterfall order | Alphabetical. *Observed.* | The order of the data. |
| Waterfall value ticks | 300, 100, -100, -300. *Observed.* | Round steps that include zero. |
| Bar corners (funnel excluded) | About half the bar width on a wide bar, a pill. *Observed.* | About 0.08 with a 2px floor (C22). Pareto: free end only. Waterfall: both ends. |
| Waterfall and pareto bar width | No limits: 20 waterfall bars are 6px and 8 are about 22px. *Observed.* | 50% of the slot, between 3px and 32px (as bars). |
| Pareto line colour | The same blue family as the bars. *Observed.* | Palette colour 2. |
| Marimekko segments | One blue, 1px white separators. *Observed.* | Colour by the Break By field, with a legend. |
| Axis labels | Rotated 90° on waterfall and pareto, cut on marimekko. *Observed.* | C5. |
| Title line | A line under the card title. *Observed.* | Removed (C2). |

## Variants

Captured from live charts on a server dashboard, with every chart style and format at its default. The data is made up: a 5 and an 8 stage conversion funnel, a profit and loss waterfall of 8 and 20 items with increases and decreases, 8 and 20 defect types with a long tail, and sales by region and product for 4 by 4 and 8 by 6. Only the bound fields and the amount of data differ between variants.

| Live capture | Variant | What it shows | What it means for the defaults |
|---|---|---|---|
| ![Funnel, 5 stages (canonical)](../funnel-waterfall/img/funnel_5_stages.png) | Funnel, 5 stages (canonical) | A single legacy steel blue, thin white lines between the stages, and a funnel that runs to the left and right edges of the plot. Stage names sit on the left. There is a rotated "Stage" title on the left and "Sum(Users)" under the plot, and vertical grid lines behind the funnel. No values show on the stages and there is no value axis, so the size of a stage can only be judged by eye. | C11 colour, C6 axis titles off, C3 no grid where the fill is the data, values on the stages, C19 padding. |
| ![Funnel, 8 stages](../funnel-waterfall/img/funnel_8_stages.png) | Funnel, 8 stages | The same. Eight stage names fit. The narrow tail at the bottom is about 20px wide, so the last two stages are hard to tell apart. | Same. Values matter more here. |
| ![Funnel, colour by stage](../funnel-waterfall/img/funnel_colour_by_stage.png) | Funnel, colour by stage | Each stage takes a palette colour, with a titled Stage legend on the right. Purple is on the top stage. The legend lists Visit, Trial, Signup, Renewal, Purchase, which is the reverse of the alphabet and not the funnel order (Visit, Signup, Trial, Purchase, Renewal). Colours were handed out in alphabetical order: Purchase blue, Renewal orange, Signup navy, Trial teal, Visit purple. | Legend and colours in funnel order. |
| ![Funnel, small card (300×180)](../funnel-waterfall/img/funnel_small_card_300x180.png) | Funnel, small card (300×180) | At the real 300×180 size it fits: five stage names, the funnel about 200px wide and a plot about 100px tall. The funnel runs to the right edge of the plot with no padding, and the Sum(Users) title still shows. | C19 padding, C6 titles off. |
| ![Waterfall, 8 items (canonical)](../funnel-waterfall/img/waterfall_8_items.png) | Waterfall, 8 items (canonical) | Bars in the legacy blue and one purple Total bar. Up and down changes are the same colour. The items are sorted alphabetically (Cost of goods, Gross profit adj, Marketing, and so on) and not in the order of the data, so the bars do not build on each other the way a waterfall should. The Revenue bar is the tallest and its ends are rounded like a pill. The value axis steps are 300, 100, -100, -300, which skip zero. Labels are rotated 90°. Titles "Sum(Amount)" and "Item" show. | C12 direction colours, data order, C17/C18 corners and zero line, C5 labels, C6 titles off. |
| ![Waterfall, 20 items](../funnel-waterfall/img/waterfall_20_items.png) | Waterfall, 20 items | All 20 labels show rotated 90° and take about 30% of the card height. Small changes are 1 to 2px tall bars. The Total bar is purple and clipped at the right edge. | C5 label rule, a minimum bar height (C17), C19 padding. |
| ![Waterfall, small card (300×180)](../funnel-waterfall/img/waterfall_small_card_300x180.png) | Waterfall, small card (300×180) | At the real 300×180 size the plot is only about 70px tall and the labels take about 45% of the card. The Y title is cut to "Sum(A...", the labels are cut ("Cost of go..", "Gross profi..", "Other inco..") and the value axis shows 300, 100, -300 with -100 dropped. | C5 label rule, C6 titles off, which also removes the cut title. |
| ![Pareto, 8 defects (canonical)](../funnel-waterfall/img/pareto_8_defects.png) | Pareto, 8 defects (canonical) | Blue bars with a thin blue cumulative line on a 0 to 100% axis on the right. The line and bars are the same colour family, so they read as one thing. Both axes have titles ("Sum(Count)" on the left and "Defect" under the plot) and labels are rotated 90°. The line starts above the first bar and ends at the top right. | C11 second colour for the line, C5 label rule. |
| ![Pareto, 20 defects](../funnel-waterfall/img/pareto_20_defects.png) | Pareto, 20 defects | Bars are about 4px wide and most of them under about 10px tall, because the first few defects dominate. The cumulative line carries the picture. Labels are rotated 90° and take about 25% of the card. | C17 bar floor, C5 label rule. |
| ![Pareto, one dominant defect](../funnel-waterfall/img/pareto_one_dominant_defect.png) | Pareto, one dominant defect | The first defect is 58% of the total (520 of 900). The line starts at the top of the first bar, at 58% on the right axis, and rises from there, so it reads correctly. The first bar is five times the next, so the others are short. | No change. |
| ![Pareto, small card (300×180)](../funnel-waterfall/img/pareto_small_card_300x180.png) | Pareto, small card (300×180) | At the real 300×180 size the plot is about 60px tall and the labels take about 40% of the card. The Y title is cut to "Sum(C...", "Misalignm.." is cut, the left axis shows 200 and 0, and the right axis shows 80%, 40% and 0%. | C5 label rule. An axis title that does not fit is hidden, not cut (open item). |
| ![Marimekko, 4 regions by 4 products](../funnel-waterfall/img/marimekko_4x4.png) | Marimekko, 4 regions by 4 products | All segments are the same legacy blue. They are separated by 1px white lines only, and without a legend nothing says which segment is which product. Total sales show above each column (411, 452, 456, 422) without a title. Axis titles "Sum(Sales)" and "Region" show. | Colour the segments by the Break By field. C11. |
| ![Marimekko, 4 by 4, colour by product](../funnel-waterfall/img/marimekko_4x4_colour_by_product.png) | Marimekko, 4 by 4, colour by product | With the product bound to colour, each segment takes a palette colour and a titled Product legend shows on the right. It reads well. The 100% axis and the column totals stay. | Make this the default when a Break By field is bound. |
| ![Marimekko, 8 regions by 6 products](../funnel-waterfall/img/marimekko_8x6.png) | Marimekko, 8 regions by 6 products | Narrow columns and thin segments. Region names are cut ("Coa..", "Isl..") and some segments are 1 to 2px, so they show only as a line. | C5 label rule, a minimum segment height. |
| ![Marimekko, 8 by 6, colour by product](../funnel-waterfall/img/marimekko_8x6_colour_by_product.png) | Marimekko, 8 by 6, colour by product | Readable with colour. Region names are still cut, and the legend takes about 25% of the width. | C5 label rule. |
| ![Marimekko, small card (300×180)](../funnel-waterfall/img/marimekko_small_card_300x180.png) | Marimekko, small card (300×180) | At the real 300×180 size it fits with 4 regions, in a single blue. The plot is about 60px tall, the left axis shows 80%, 40% and 0% and the segments are thin. | As the 4 by 4. |

## Authoring notes

Not defaults. How the charts were bound, in case a reader wants to reproduce them.

- **Funnel:** the stage goes on the Y shelf and the measure on the X shelf. **Waterfall and pareto:** the category on X and the measure on Y, with the chart style chosen from the style picker. **Marimekko:** the category on X, the measure on Y, and the second dimension on the third shelf, "Break By". It is not on the colour slot, which is why the segments have no colour until a field is dropped there.
- The drop rules for the shelves are in `vs-chart-editor.service.ts` (`isFieldAcceptable`): for the marimekko, X and the group take dimensions and Y takes a measure. *Traced.*

## Build items

| # | Item | Cost | Where | Notes |
|---|---|---|---|---|
| 1 | Palette colour 1 for the funnel and pareto bars, colour 2 for the pareto line | New behaviour | The colour frames in `GraphGenerator`. Not traced. | Part of C11. Old saved charts keep their blue. |
| 2 | Values on funnel stages and waterfall bars, no grid lines behind the funnel | New behaviour | `GraphGenerator` creates the funnel as an `IntervalElement` with a stacked group and centred label placement. Traced. | The label placement is already centred, so the item is mostly the default for showing values. The waterfall gets the same default. |
| 3 | Funnel colours and legend in funnel order | Fix | The funnel colour frame and legend comparator. Not traced. | Observed: alphabetical colours, reverse alphabetical legend. |
| 4 | Direction colours on the waterfall, Total in the neutral token | New behaviour | The waterfall colour frame. Not traced. | Part of C12. |
| 5 | Keep the data order on a waterfall | New behaviour | The default sort of the waterfall category. Not traced. | Only when the author has not sorted. |
| 6 | Round value axis ticks that include zero on a waterfall | Fix | The waterfall value scale. Not traced. |  |
| 7 | Colour the marimekko by its Break By field | New behaviour | The marimekko colour binding. Not traced. | Only when no colour field is set. |
| 8 | Bar width, corners and minimum on waterfall and pareto bars | Seed only | The bar corner seed in `ChartVSAssemblyInfo` and the bar width rule from the bar page. | Same as the bar decisions: width 50% of the slot between 3px and 32px, radius about 0.08, free end only on pareto and both ends on the waterfall, and a 2px minimum height on the waterfall. |
| 9 | Tick marks off on the pareto second axis | New behaviour | Common build item 12. | Copy the tick visibility from the axis descriptor to the second axis, as the common item says. |
| 10 | Common items | Common | See the common page | C2, C3, C5, C6, C7 to C10, C18, C19, C21 and C24 apply without extra work here. |

## Open items

- The pareto with a dominant first defect was checked and reads correctly, and Preview and the portal viewer matched the design view on the first set of captures.
- The four small-card captures were redone on 2026-11-04 at a true 300×180 (they were first captured at the full size by mistake). They have not been compared with Preview or the portal viewer.

## Known limits

- A funnel with 8 stages has a tail about 20px wide.
- A marimekko with many small values has segments of 1 to 2px.
