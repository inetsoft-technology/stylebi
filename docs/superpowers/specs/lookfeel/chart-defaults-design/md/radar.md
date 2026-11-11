<!-- Markdown version of radar/radar.html. The drawn "proposed look" cards are in the HTML page. -->

# Radar and filled radar

Family page. It lists only what differs from the [common rules](common.md) (C1 to C24). Other family pages: [bar and interval](bar.md), [line and area](line.md), [point and contour](point.md), [pie and donut](pie.md) and [financial](financial.md). Facts marked *observed* come from live charts on a server dashboard with modern chrome, every chart style and format at its default, and made-up data. The translucent card is not in the build yet. All captures are from the composer design view. Facts marked *traced* come from reading the code. All decisions on this page were confirmed on 2026-11-03.

## Status

| Type | Status | What exists |
|---|---|---|
| Radar | Written up | Canonical plus 13 variants: 3, 5, 8 and 12 spokes, 2, 3, 5 and 8 series, mismatched scales, negative values, long names, and the small (300×180) and large (800×480) cards. |
| Filled radar | Written up | Two charts: 3 and 5 series. |

## Decisions

- **Follows the common rules.** No title line (C2), axis title off (C6), plot padding by density (C19, see the padding decision below), spoke labels (C5), rings in the grid colour (C3), the legend on the right for a polar chart (C7 to C10), and the modern palette for series (C11).
- **No axis title.** The title is a join of all the measure names, such as "Sum(Comfort) / Sum(Range) / Sum(...", and it is cut off. The spokes already carry their names (C6 off). Confirmed on 2026-11-03.
- **Spoke labels name the field, not the aggregate, and truncate.** "Speed", not "Sum(Speed)", cut at about 12 characters with an ellipsis (C5). With long measure names the labels take most of the card and the radar shrinks to about 60px. The same rule as the point chart's axis titles. Confirmed on 2026-11-03.
- **The scale is labelled once.** Shipped repeats the ring labels (5K, 10K, 15K, 20K) on every spoke, six times for six spokes, which crowds the centre. Show them on the top spoke only. New behaviour. Confirmed on 2026-11-03.
- **Rings are drawn as a web.** Shipped draws only the outer circle and the spokes. Faint polygon rings at the scale values, in the grid colour (C3), let a reader judge a value. This is common build item 11. New behaviour. Confirmed on 2026-11-03.
- **Series lines are 1.8px.** Shipped lines are about 1px and thin against the card. The line chart uses 1.8px. Confirmed on 2026-11-03.
- **Filled radar fills at about 30% opacity with a solid outline.** Shipped fills are nearly opaque, so the front series hides the rest and the scale labels. Confirmed on 2026-11-03.
- **The radar keeps at least the chart padding around it.** Measured on the 400×240 captures, the top spoke label starts about 5px under the title line and the bottom label ends about 14px above the card bottom, so the plot, labels included, fills the height with little room at the top. The radar keeps at least the chart padding of the density (8, 12 or 16px, 12 in compact), which is rule C19, from the title line, the card edges and the legend, and shrinks to fit. The same padding rule as pie and donut, without the 80% cap, since the spoke labels already take space. New behaviour. Confirmed on 2026-11-03.
- **No default change for scale and series limits.** Each spoke is its own scale, a series count above 5 tangles, and negative values give odd shapes. These are known limits.

## Proposed look

Drawn to the decisions at the default card size, with invented values. No title line or axis title, spoke labels with the field name, a faint web, the scale on the top spoke only, 1.8px lines, and a titled legend on the right.

### Light

*The drawn cards that show the proposed look are in the HTML page.*

### Dark

## Shipped today against the decisions

| Setting | Shipped today | Proposed |
|---|---|---|
| Title line | A line under the card title. *Observed.* | Removed (C2). |
| Axis title | All measure names joined, cut off. *Observed.* | Off (C6). |
| Spoke labels | "Sum(Speed)", cut with two dots when long. *Observed.* | Field name, cut at about 12 characters. |
| Scale labels | Repeated on every spoke. *Observed.* | Once, on the top spoke. |
| Rings | Outer circle and spokes only. *Observed.* | Faint polygon web at the scale values. |
| Series line | About 1px, no markers. *Observed.* | 1.8px. |
| Filled radar | Near-opaque fill, the front series hides the rest. *Observed.* | About 30% opacity with a solid outline. |
| Legend | Right, with a title. *Observed.* | Right (C7, C9). |
| Radar size and padding | The plot with its labels fills the height: about 5px under the title line and 14px above the card bottom. *Observed.* | At least the density padding on every side (C19, confirmed). |

## Variants

Captured from live charts on a server dashboard, with every chart style and format at its default. The data is made up: 320 rows of scores for 12 named measures, a revenue measure in the thousands, three signed measures and four long-named measures, across entity lists of 2, 3, 5 and 8. Only the bound fields differ between variants.

| Live capture | Variant | What it shows | What it means for the defaults |
|---|---|---|---|
| ![6 spokes (canonical)](../radar/img/radar.png) | 6 spokes (canonical) | A thin blue polygon on six spokes labelled "Sum(Speed)" and so on, inside a faint outer circle. The scale labels (5K to 20K) are repeated on every spoke. A long rotated axis title, "Sum(Comfort) / Sum(Range) / Sum(...", runs down the left and is cut off. The radar is about 160px across in a 240px card, and there is a line under the card title. | C2, C6, spoke labels, scale labels once. |
| ![3 spokes](../radar/img/radar_3_spokes.png) | 3 spokes | A triangle. The scale labels repeat on all three spokes. | Scale labels once. |
| ![5 spokes](../radar/img/radar_5_spokes.png) | 5 spokes | A pentagon. Reads well. | Scale labels once. |
| ![12 spokes](../radar/img/radar_12_spokes.png) | 12 spokes | A dodecagon about 150px across at the default size. Twelve spoke labels fit around it, but each carries the "Sum(" prefix and the repeated scale labels from every spoke pile up in the centre as an unreadable cluster. The axis title is a join of 12 names, cut to "Sum(Brand) / Sum(Efficiency) / Sum(...". | Scale labels once, field names without the aggregate, and no axis title (all already decided). |
| ![Small card, 300×180](../radar/img/radar_small_card_300x180.png) | Small card, 300×180 | The radar is about 80px across. The six spoke labels fit around it, but the scale labels from all six spokes overlap in the centre ("5K 5K 5K"). The axis title is cut to "Sum(Comfort) / Sum(R...". | Scale labels once, and no axis title (already decided). |
| ![Large card, 800×480](../radar/img/radar_large_card_800x480.png) | Large card, 800×480 | The radar fills the height at about 350px across and leaves wide empty areas at the sides. The scale labels repeat on every spoke. The axis title is a vertical string of all six measure names that takes the full left edge. | Scale labels once and no axis title (already decided). |
| ![8 spokes](../radar/img/radar_8_spokes.png) | 8 spokes | An octagon with eight spoke labels and the scale labels on every spoke, which crowd the centre. | Scale labels once. |
| ![2 series on Color](../radar/img/radar_2_series.png) | 2 series on Color | Two thin polygons, blue and orange, with a titled legend on the right. They overlap closely in this data. | Thicker lines, legend rule. |
| ![3 series](../radar/img/radar_3_series.png) | 3 series | Three polygons in the modern palette. Readable. | Thicker lines. |
| ![5 series](../radar/img/radar_5_series.png) | 5 series | Five polygons nested inside each other. Still readable, with five legend entries. | No change. |
| ![8 series](../radar/img/radar_8_series.png) | 8 series | Eight polygons cross each other and tangle. The legend lists eight. | Known limit. |
| ![One measure far larger than the rest](../radar/img/radar_scale_mismatch.png) | One measure far larger than the rest | Each spoke has its own scale: the revenue spoke reads up to 2000K while the others read up to 20K. The polygon stays a plausible shape because each spoke is scaled on its own. | Known limit. Each spoke is its own scale. |
| ![Negative values](../radar/img/radar_negative_values.png) | Negative values | Three spokes with ranges that cross zero draw a thin triangle. The scale labels of the three spokes read differently, and the centre is not zero. | Known limit. |
| ![Long measure names](../radar/img/radar_long_names.png) | Long measure names | Spoke labels are cut with two dots ("Sum(Supply chain reliability ind.."), and the radar shrinks to about 60px across because the labels take most of the card. | Truncate at about 12 characters and drop the "Sum()" wrapper. |
| ![Filled radar, 3 series](../radar/img/filled_radar_3_series.png) | Filled radar, 3 series | The fills are nearly opaque, so the front polygon hides the others and the scale labels. Only the outer series is fully visible. | Fill at about 30% opacity with a solid outline. |
| ![Filled radar, 5 series](../radar/img/filled_radar_5_series.png) | Filled radar, 5 series | Five filled polygons in one dark mass. The inner series are not visible. | Fill at about 30% opacity. |

## Build items

| # | Item | Cost | Where | Notes |
|---|---|---|---|---|
| 1 | No axis title on radar | Seed only | `TitleDescriptor`, `ChartVSAssemblyInfo.seedChromeDefaults` | Same mechanism as the other types. |
| 2 | Spoke labels: field name, truncated | Small change | `RadarGraphGenerator.fixParallelCoord`, `AbstractParallelCoord.createAxis` | Shares the label text rule with the other axes. Check an author alias still shows. |
| 3 | Scale labels on one spoke only | New behaviour | `AbstractParallelCoord.createAxis`. Not traced further. | Each spoke has its own scale, so the label would need to name the scale for spokes that differ. |
| 4 | Polygon web | New behaviour | `RadarGraphGenerator.fixParallelCoord`, `PolarCoord` | Common build item 11. The plot grid styles are never read for radar today. |
| 5 | Series line width 1.8px, filled radar at 30% opacity | Seed only | The line size frame and the fill alpha for the radar element. Not traced. | Check how the fill alpha is stored. |
| 6 | Radar padding of at least the density padding (C19) | New behaviour | The radar layout in `RadarGraphGenerator` and the parallel coordinate size. Not traced. | Uses the chart padding seed from C15. Check that long labels still fit. |
| 7 | Common items | Common | See the common page | C2, C7, C9 and C11 apply to radar without extra work here. |

## Open items

- None. The 12-spoke radar and the small and large cards were captured on 2026-11-04. They show the same problems the decisions already cover: the repeated scale labels, the "Sum(" prefix on spoke labels and the joined axis title. They have not been compared with Preview or the portal viewer.

## Known limits

Settled: no default change.

- Each spoke has its own scale, so a measure that is far larger than the rest still draws a plausible polygon.
- More than about 5 series tangle.
- Negative values give odd shapes and the centre is not zero.
- At 12 spokes the radar is about 150px across at the default size, so the spoke labels are small and close together. The scale-once rule reduces the centre clutter but not the label density.
