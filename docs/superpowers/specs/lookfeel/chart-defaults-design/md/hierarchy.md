<!-- Markdown version of hierarchy/hierarchy.html. The drawn "proposed look" cards are in the HTML page. -->

# Treemap, sunburst, circle packing and icicle

Family page. It lists only what differs from the [common rules](common.md) (C1 to C24). Other family pages: [bar and interval](bar.md), [line and area](line.md), [point and contour](point.md), [pie and donut](pie.md), [radar](radar.md) and [financial](financial.md). Facts marked *observed* come from live charts on a server dashboard with modern chrome, every chart style and format at its default, and made-up data. The translucent card is not in the build yet. All captures are from the composer design view. Facts marked *traced* come from reading the code. All decisions on this page were confirmed on 2026-11-03.

## Status

| Type | Status | What exists |
|---|---|---|
| Treemap | Written up | Canonical plus 9 variants: 1, 3 and 4 levels, colour by dimension and by measure, long names, one dominant, many tiny, and both card sizes. |
| Sunburst | Written up | Three charts: 2 levels, 3 levels and colour by dimension. |
| Circle packing | Written up | Three charts: 2 and 3 levels, and colour by the top level. |
| Icicle | Written up | Three charts: 2 and 3 levels, and colour by the top level. A three-level coloured icicle was not built: the editor would not open for it. |

## Decisions

- **Follows the common rules.** No title line (C2), plot padding by density (C19), the legend on the right (C7 to C10), the label rule (C5), data labels automatic for the treemap family (C14), label colour (C21) and no size legend (C20). These charts already have no axes, no axis titles and no gridlines, so C3, C4 and C6 do not apply.
- **Colour by the top level when no colour field is bound.** Shipped draws every cell in the legacy blue, so a three-level treemap shows no grouping at all. Colouring by the first dimension uses the modern palette, makes each group visible, and matches how the wizard already puts a dimension on colour for pie, donut and treemap. The author can clear it. This is the largest change on this page. New behaviour. Confirmed on 2026-11-03.
- **Each deeper level is a lighter step of its group's colour.** In the sunburst and the icicle, shipped draws every level of a group in the same colour (observed on the coloured sunburst and the coloured icicle), so the rings and bars of one division merge into one mass. A lighter step per level keeps the group's hue and separates the levels. It uses the companion colour rule that the palette already has for brushing and target bands (a lighter or darker step of a series colour, same hue). Cap it at three levels and keep the labels at 4.5:1 on the lightest step. Circle packing already nests with outlines, and the treemap keeps one fill per group, with thin separators between cells. New behaviour. Confirmed on 2026-11-03.
- **No size legend.** Size is the area of a cell, so the "Sum(Sales)" legend of rounded symbols from 0 to 40K tells the reader nothing and adds a second legend box beside a colour legend. When a colour field is bound its symbols also draw in a hard-coded grey-teal, `#759595`, that belongs to neither the palette nor the cells (C20). Hide the legend. Seed only. Confirmed on 2026-11-03.
- **Labels pick white or near-black per fill.** The captures show grey text on blue, navy and teal cells, and some labels are unreadable. The baseline says this contrast rule exists for the treemap family, but it did not apply here. This is the common build item 6 and is likely the cause: the code compares the label colour to exactly #4B4B4B, which a modern chart no longer has. Fix: keep the rule under the modern chrome. Confirmed on 2026-11-03.
- **Labels truncate with an ellipsis (C5).** About 12 characters, and a cell too small for a label shows none. Already how small cells behave.
- **No default change for deep levels or many items.** Three or more levels, 48 or more cells, and many tiny items cannot be labelled, and the author uses fewer levels, Top N or a different chart.

## Proposed look

Drawn to the decisions at the default card size, with invented values. Coloured by the top level in the modern palette, a lighter step of the group colour for each deeper level in the sunburst, labels readable on every fill, one colour legend on the right, and no size legend.

### Light

*The drawn cards that show the proposed look are in the HTML page.*

### Dark

## Shipped today against the decisions

| Setting | Shipped today | Proposed |
|---|---|---|
| Colour, no colour field | The legacy blue for every cell. *Observed.* | The top level on the modern palette. |
| Colour by a dimension | Modern palette, one colour per group. *Observed.* | Unchanged. |
| Colour by a measure | Teal ramp with a gradient legend. *Observed.* | Unchanged. |
| Sunburst and icicle levels | Every level of a group in the same colour. *Observed.* | A lighter step of the group colour per level. |
| Size legend | Always shown, with rounded symbols. With a colour field the symbols are grey-teal `#759595`. *Observed and traced.* | Hidden. |
| Cell labels | Grey text on the fill, hard to read on blue, navy and teal. *Observed.* | White or near-black per fill. |
| Title line | A line under the card title. *Observed.* | Removed (C2). |
| Padding | About 5px under the title line. *Observed.* | The density padding (C19). |
| Axis titles and grid | None. *Observed.* | Unchanged. |

## Variants

Captured from live charts on a server dashboard, with every chart style and format at its default. The data is made up: 3,200 rows over four divisions, twelve departments, forty-eight teams and ninety-six products, with long department names, a signed margin, one dominant team and many tiny teams. Only the bound fields and the card size differ between variants.

| Live capture | Variant | What it shows | What it means for the defaults |
|---|---|---|---|
| ![Treemap, 2 levels (canonical)](../hierarchy/img/treemap.png) | Treemap, 2 levels (canonical) | Department rectangles in one legacy blue with thin white lines between them. Names are grey text on the blue, hard to read. The Division level is not shown as a group. A "Sum(Sales)" size legend with rounded symbols from 0 to 40K sits on the right, and there is a line under the card title. | C2, one colour from the palette, no size legend, label contrast. |
| ![Treemap, 1 level](../hierarchy/img/treemap_1_level.png) | Treemap, 1 level | Twelve department rectangles, all blue. | Same. |
| ![Treemap, 3 levels](../hierarchy/img/treemap_3_levels.png) | Treemap, 3 levels | Forty-eight team rectangles. The two upper levels are not visible, so the hierarchy is lost. | Colour by the top level. |
| ![Treemap, 4 levels](../hierarchy/img/treemap_4_levels.png) | Treemap, 4 levels | Ninety-six leaf cells. Labels read "Produ.." or are dropped. No grouping is visible. | Colour by the top level. C5 label rule. |
| ![Treemap, colour by the top level](../hierarchy/img/treemap_color_by_dimension.png) | Treemap, colour by the top level | Four groups in the modern palette. The grouping is clear and the layout reads well. Two legends stack on the right, the size legend and the Division legend. The labels on the dark navy cells are hard to read. | This is the look to default to. Hide the size legend. Fix label contrast. |
| ![Treemap, colour by a measure](../hierarchy/img/treemap_color_by_measure.png) | Treemap, colour by a measure | A teal ramp from light to dark with a gradient legend, and the size legend above it. The grey labels fail on the mid and dark teal cells. | Label contrast, no size legend. |
| ![Treemap, long names](../hierarchy/img/treemap_long_labels.png) | Treemap, long names | Names are cut at the cell edge ("Regional Distribution Pa..", "Insurance Underwrit..") and small cells show none. | C5: truncate at about 12 characters with an ellipsis. |
| ![Treemap, one dominant item](../hierarchy/img/treemap_one_dominant.png) | Treemap, one dominant item | One cell takes about a third of the area. The rest pack into the corner. | No change. |
| ![Treemap, many tiny items](../hierarchy/img/treemap_many_tiny.png) | Treemap, many tiny items | Two big cells and a pixel grid of tiny cells in one corner, with no labels. | Known limit. |
| ![Sunburst, 2 levels](../hierarchy/img/sunburst.png) | Sunburst, 2 levels | Two rings in one blue with white dividers. Labels follow the arcs. The hole is about 30% of the diameter and there is no centre label. | Colour by the top level, no size legend. |
| ![Sunburst, 3 levels](../hierarchy/img/sunburst_3_levels.png) | Sunburst, 3 levels | The outer ring has 48 thin segments. The labels overlap and cannot be read. | Known limit. |
| ![Sunburst, colour by the top level](../hierarchy/img/sunburst_color_by_dimension.png) | Sunburst, colour by the top level | Each division has its own colour across its rings. Reads well. Two legends again. | Hide the size legend. |
| ![Circle packing, 2 levels](../hierarchy/img/circle_packing.png) | Circle packing, 2 levels | Circles nested in a thin outline circle, all blue. Labels are clipped inside small circles. | Colour by the top level. |
| ![Circle packing, 3 levels](../hierarchy/img/circle_packing_3_levels.png) | Circle packing, 3 levels | Many small circles with no readable labels. | Known limit. |
| ![Circle packing, colour by the top level](../hierarchy/img/circle_packing_color_by_dimension.png) | Circle packing, colour by the top level | Each division has its own colour on its leaf circles. The parent circles are outlines with no fill, in a neutral grey line. Two legends stack on the right, the size legend with grey-teal symbols and the Division legend. Labels are grey, and unreadable on the navy circles. | Hide the size legend. Label contrast. |
| ![Icicle, 2 levels](../hierarchy/img/icicle.png) | Icicle, 2 levels | Stacked horizontal bars, all blue, with the division on the left and the departments on the right. Labels are grey on blue. | Colour by the top level. Label contrast. |
| ![Icicle, colour by the top level](../hierarchy/img/icicle_color_by_dimension.png) | Icicle, colour by the top level | Both levels of a division use the same solid colour, so the division bar and its department bars merge into one block, and the division is told apart from its parts only by position. Two legends stack on the right. The grey labels fail on navy and teal. | A lighter step of the group colour per level. Hide the size legend. Label contrast. |
| ![Icicle, 3 levels](../hierarchy/img/icicle_3_levels.png) | Icicle, 3 levels | The team level is a stack of thin lines that cannot be labelled. | Known limit. |
| ![Treemap, small card (300×180)](../hierarchy/img/treemap_small_card_300x180_.png) | Treemap, small card (300×180) | The legend takes about 30% of the width. Labels are cut off. | No size legend frees the width. |
| ![Treemap, large card (800×480)](../hierarchy/img/treemap_large_card_800x480_.png) | Treemap, large card (800×480) | The same layout, scaled. Labels fit better. | No change. |

## Build items

| # | Item | Cost | Where | Notes |
|---|---|---|---|---|
| 1 | Colour by the top level with no colour field | New behaviour | The colour setup for the treemap family in `GraphGenerator` and the chart type change in `ChangeChartTypeProcessor`. Not traced. | The wizard already puts a dimension on colour for treemap. Old saved charts keep their blue. An author clearing it must stick. |
| 2 | Hide the size legend for the treemap family | Seed only | The size legend visibility in `GraphGenerator` legend setup. Not traced. | Check that sunburst, circle packing and icicle share the setting. |
| 3 | Lighter step of the group colour per level, sunburst and icicle | New behaviour | The colour frame for the sunburst and icicle in `GraphGenerator`, and the companion colour rule in `VSChartPaletteDefaults`. Not traced further. | Reuse the companion step, capped at three levels. An author colour per level must win. Check label contrast on the lightest step. |
| 4 | Keep white or near-black label text under modern | Fix | Common build item 6: `GraphGenerator` label colour tests near lines 1160, 1235 and 1546, `ChartVSAssemblyInfo.seedChromeDefaults`. | The tests compare the label colour to exactly `#4B4B4B`. This page is the live evidence that the rule is lost under modern. |
| 5 | Common items | Common | See the common page | C2, C5, C7, C9 and C19 apply without extra work here. C20 does not apply, since the size legend is hidden. |

## Open items

- None.

## Known limits

Settled: no default change.

- Three or more levels and 48 or more cells cannot be labelled.
- Many tiny items give a pixel grid in a corner.
- The sunburst has no centre label, and its outer ring at three levels overlaps.
