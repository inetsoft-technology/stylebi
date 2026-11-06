<!-- Markdown version of pie/pie.html. The drawn "proposed look" cards are in the HTML page. -->

# Pie and donut charts

Family page. It lists only what differs from the [common rules](common.md) (C1 to C24). Other family pages: [bar and interval](bar.md), [line and area](line.md), [point and contour](point.md) and [financial](financial.md). Facts marked *observed* come from live charts on a server dashboard with modern chrome, every chart style and format at its default, and made-up data. The translucent card is not in the build yet, so the captures show the current white card. Facts marked *traced* come from reading the code. All decisions on this page were confirmed on 2026-11-03. All captures are from the composer design view. In Preview the pie renders differently, see the defect below.

## Status

| Type | Status | What exists |
|---|---|---|
| Pie | Written up | Canonical plus 12 variants: slice counts, label length, edge values, text labels and card size. |
| Donut | Written up | Canonical plus 4 variants: 3, 8 and 12 slices and one dominant slice. |

## Decisions

- **Follows the common rules.** No title line (C2), axis title off (C6), plot padding by density (C19, see the padding decision below), and the legend rules: on the right for part-to-whole charts (C7), no border or fill (C8), capped at 30% of the width (C9) and the gap (C10). The pie and donut are already clean: no gridlines, no axes, the modern palette, so C3 to C5 do not apply.
- **No axis title.** Both pie and donut draw a rotated "Sum(Sales)" beside the chart, though there is no axis to name. The card title names the measure (C6 off). This is the one visible clutter item. Confirmed on 2026-11-03.
- **Slices run from largest to smallest, clockwise from 12 o'clock.** Shipped orders slices alphabetically by category, so the biggest slice can land anywhere. Largest first is the standard reading order and puts the small ones together at the end. The author can sort differently. New behaviour. Confirmed on 2026-11-03.
- **The pie keeps breathing room.** The pie keeps at least the chart padding of the density (8, 12 or 16px, 12 in compact) on all four sides, which is rule C19, and its diameter is at most 80% of the plot height. Measured in the design view and the portal, the shipped pie fills the plot: about 7px under the title line and 16px above the card bottom at every card size, which is 76% of the height at 400×240 and 88% at 800×480. With the rule the pie is about 159px at 400×240 and about 340px at 800×480, and the gaps equalise at 12px or more. Applies to the donut too. New behaviour. Confirmed on 2026-11-03.
- **The legend keeps zero-value categories.** Shipped lists all eight groups when only three draw a slice. The legend lists the categories, so no change. Confirmed on 2026-11-03.
- **No default change for many slices or negative values.** At 12 slices the legend wraps to two columns, and at 20 the pie is unreadable. A pie cannot show negatives, and shipped draws their magnitude with no marker. These are known limits that the author handles with a bar chart, Top N or filtering.
- **No default change for the donut hole or for text labels.** The hole is about 45% of the diameter, and labels are off until the author adds Text.

## Proposed look

Drawn to the decisions at the default card size, with invented values. No title line or axis title, slices sorted from largest, a pie that is 80% of the plot height with at least the chart padding around it, and a titled legend on the right.

### Light

*The drawn cards that show the proposed look are in the HTML page.*

### Dark

## Shipped today against the decisions

| Setting | Shipped today | Proposed |
|---|---|---|
| Colour | Modern categorical palette, eight slots then a tail. *Observed.* | Unchanged. |
| Title line | A line under the card title. *Observed.* | Removed (C2). |
| Axis title | A rotated "Sum(Sales)" beside the chart. *Observed.* | Off (C6). |
| Pie size and padding | Fills the plot: about 7px under the title line and 16px above the card bottom, 76% of the height at 400×240 and 88% at 800×480. *Observed in design view and portal.* | At least the density padding (12px compact) on every side (C19), and at most 80% of the plot height. |
| Slice order | Alphabetical by category. *Observed.* | Largest to smallest, clockwise from 12. |
| Legend | Right, with a title, two columns from about 12 entries. *Observed.* | Right, capped at 30% of the width (C7, C9). |
| Zero-value categories | Listed in the legend, no slice. *Observed.* | Unchanged. |
| Negative values | Drawn as magnitude, no marker. *Observed.* | Unchanged. Known limit. |
| Labels | Off. With Text bound, outside names in small grey text. *Observed.* | Unchanged. |
| Donut hole | About 45% of the diameter, no total. *Observed.* | Unchanged. |

## Variants

Captured from live charts on a server dashboard, with every chart style and format at its default. The data is made up: 2,400 sales rows with categories of 2, 3, 5, 8, 12 and 20 members, 28-character warehouse names, and measures with one dominant category, many tiny ones, negative sums and zero sums. Only the bound fields and the card size differ between variants.

| Live capture | Variant | What it shows | What it means for the defaults |
|---|---|---|---|
| ![5 slices (canonical)](../pie/img/pie.png) | 5 slices (canonical) | A pie in the modern palette with a titled legend on the right. A rotated "Sum(Sales)" axis title sits to the left of the pie, and there is a line under the card title. The slices have no borders and no labels. Slices run in alphabetical category order, not by size. | C2, C6 (axis title off), slice order. |
| ![2 slices](../pie/img/pie_2_slices.png) | 2 slices | Two halves, blue and orange, with a two-entry legend. | No change. |
| ![3 slices](../pie/img/pie_3_slices.png) | 3 slices | Reads well. | No change. |
| ![8 slices](../pie/img/pie_8_slices.png) | 8 slices | The eight-slot palette is all used. Neighbouring slices are distinguishable. Legend on the right with eight entries. | No change. |
| ![12 slices](../pie/img/pie_12_slices.png) | 12 slices | Colours from the tail of the palette appear (olive, pink, green). The legend wraps into two columns and takes about 35% of the card width. | C9 caps the legend at 30%, so it truncates or paginates. |
| ![20 slices](../pie/img/pie_20_slices.png) | 20 slices | Thin wedges and 20 colours, many close in hue. The two-column legend takes about 35% of the width. Not readable as a pie. | Known limit. The author uses a bar chart or Top N. |
| ![28-character category names](../pie/img/pie_long_labels.png) | 28-character category names | The legend carries the full names and takes about 40% of the card width, so the pie shrinks. | C9: cap the legend at 30% and truncate with an ellipsis. |
| ![One dominant slice](../pie/img/pie_one_dominant.png) | One dominant slice | One slice takes about 85% and the rest are slivers with no labels. Legend and colours still identify them. | No change. |
| ![20 slices, 3 dominant](../pie/img/pie_many_tiny.png) | 20 slices, 3 dominant | Three large slices and 17 slivers of a pixel or two, in a 20-entry legend. | Known limit. |
| ![Negative values](../pie/img/pie_negative_values.png) | Negative values | Three of eight groups sum below zero, and all eight still draw a slice. The negative ones appear as their magnitude, and nothing marks them as negative. | Known limit. A pie cannot show negatives. |
| ![Zero categories](../pie/img/pie_zeros.png) | Zero categories | Only three slices draw, but the legend still lists all eight groups. | Keep. The legend lists the categories. |
| ![Category names as Text](../pie/img/pie_text_labels.png) | Category names as Text | Names sit outside the slices in small grey text with no leader lines. The pie shrinks to about 45% of the card height. | No change. The author turned labels on. |
| ![Donut, 5 slices](../pie/img/donut.png) | Donut, 5 slices | A ring with the same palette and a hole of about 45% of the diameter. No total in the hole. The rotated axis title is still there. | C6. A total in the hole is open. |
| ![Donut, 3 slices](../pie/img/donut_3_slices.png) | Donut, 3 slices | Reads well. | No change. |
| ![Donut, 8 slices](../pie/img/donut_8_slices.png) | Donut, 8 slices | Reads well, with a legend of eight. | No change. |
| ![Donut, 12 slices](../pie/img/donut_12_slices.png) | Donut, 12 slices | The ring is narrow, so the wedges are thin, and the legend takes two columns. | No change. |
| ![Donut, one dominant slice](../pie/img/donut_one_dominant.png) | Donut, one dominant slice | A big arc and a run of slivers. | No change. |
| ![Small card, 300×180](../pie/img/pie_small_card_300x180_.png) | Small card, 300×180 | The pie is about 85px across and the legend takes about 40% of the width. The title line and axis title still show. | C2 and C6 give the pie more room. |
| ![Large card, 800×480](../pie/img/pie_large_card_800x480_.png) | Large card, 800×480 | The pie scales up and the legend stays small beside it. | No change. |

## Build items

| # | Item | Cost | Where | Notes |
|---|---|---|---|---|
| 1 | No axis title on pie and donut | Seed only | `TitleDescriptor`, `ChartVSAssemblyInfo.seedChromeDefaults` | Same mechanism as the other types. The type list in the common build item 1 needs pie and donut. |
| 2 | Sort slices from largest to smallest, start at 12 o'clock | New behaviour | The pie coordinate and the data order for pie and donut. Not traced. | An author sort must win. Check the start angle and direction in the polar coordinate. |
| 3 | Pie and donut size: density padding (C19), at most 80% of plot height | New behaviour | The pie and donut coordinate sizing in the polar layout. Not traced. | Uses the chart padding seed from C15. An author size must win. Check the donut hole stays at about 45% of the new diameter. |
| 4 | Common items | Common | See the common page | C2, C7 and C9 apply to pie and donut without extra work here. |

## Open items

- None.

## Defect: pies are clipped in Preview

Reproduced on the server on 2026-11-03. The design view draws every pie whole, but Preview clips the pie in a way that shows only part of the wedges, and a two-slice pie shows nothing at all. It happens for a pie at 400×240 and at 300×180. A donut at 400×240 and a pie at 800×480 render correctly in the same Preview. The axis title and the legend are fine. The cause is not traced. This is a product bug and not a default question, so it needs its own issue. Every pie capture on this page comes from the design view and does not show it.

## Known limits

Settled: no default change.

- More than about 12 slices cannot be read, and the legend takes two columns.
- A pie draws negative values as magnitude with no marker.
- Zero-value categories keep their legend entry.
- The donut total is created by a script function, so there is no default for it.
