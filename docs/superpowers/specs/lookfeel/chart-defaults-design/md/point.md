<!-- Markdown version of point/point.html. The drawn "proposed look" cards are in the HTML page. -->

# Point and contour charts

Family page. It lists only what differs from the [common rules](common.md) (C1 to C24). Other family pages: [bar and interval](bar.md), [line and area](line.md) and [financial](financial.md). Facts marked *observed* come from live charts on a server dashboard, with every chart style and format at its default and made-up data. Facts marked *traced* come from reading the code. Nothing here has been checked on a customer dashboard. All decisions on this page were confirmed on 2026-11-03.

## Status

| Type | Status | What exists |
|---|---|---|
| Point | Written up | Canonical plus 17 variants: point counts, groups, size, shape, colour by measure, data shapes and card size. |
| Scatter contour | Written up | Three charts: 500 points, 3 clusters and 2,000 points. |
| Contour map | Written up | One chart on made-up store data with latitude and longitude, in six metro clusters. |

## Decisions

- **Follows C19, the density padding on every side.** Applies to point and scatter contour. The contour map has its own margin (about 14px at the top) and is exempt. The measured top gap below the title line on the canonical capture is 5 to 6px, about half the density padding at compact. Confirmed on 2026-11-03.
- **Follows the common rules.** No title line (C2), plot padding by density (C19, in the first bullet), axis lines on both continuous axes (C4), axis labels (C5), axis titles on (C6 keeps them for point and scatter contour), legend rules (C7 to C10), the bubble cap (C17), the zero line (C18), size legend symbols (C20) and colour ramps (C23). Observed shipped differently: the single colour is the legacy grey-blue, and there are no vertical grid lines on the continuous X axis (C3, decision 3 below).
- **Vertical grid lines on the continuous X axis (C3).** Point and scatter contour have two continuous axes, so both get grid lines. Shipped draws horizontal lines only. Confirmed on 2026-11-03.
- **Axis titles name the field, not the aggregate.** A point chart shows "Sum(Price)" because the field is aggregated, but with one row per point the sum is the value, and the scatter contour already shows "Price". The title becomes the field name. Confirmed on 2026-11-03.
- **One colour from the modern palette.** Points with no colour field take slot 1 (C11), keeping their transparency. Confirmed on 2026-11-03.
- **Shape encodings use filled markers.** With a shape field the points are pale outlines, circle, triangle and square, that are hard to see. Filled markers in the series colour read better and match the plain dot. Confirmed on 2026-11-03.
- **A measure on Color that crosses zero uses the diverging ramp.** Shipped uses the teal ramp from light to dark, labelled -0.6 to 0.6, so zero has no meaning. The diverging (Variance) ramp is centred on zero. A measure that stays on one side keeps the teal ramp. New behaviour. Confirmed on 2026-11-03.
- **The largest bubble is about 20px.** The size frame defaults to a largest size of 30, traced, and observed bubbles reach about 28px on a 400px card. They overlap and hide each other. Capping at 20 keeps them apart. Confirmed on 2026-11-03.
- **Scatter contour uses the teal ramp.** It draws blue today, which is the classic ramp and not the modern default. Confirmed on 2026-11-03.
- **The contour map fits its extent to the data.** Shipped shows the whole world, so a country's worth of points collapses into a blob a few pixels wide. The map zooms to the bounding box of the points, with a small margin. An author's own extent wins, so the rule must leave an explicit extent alone and an author who wants the world view can still get it (a build note). New behaviour. Confirmed on 2026-11-03.
- **A vertical zero line when both axes cross zero.** The horizontal zero line is the axis line today, and there is no vertical one, so the quadrants are half marked. Draw the vertical zero line in the same axis line colour, with C18. Confirmed on 2026-11-03.
- **Size legend symbols use a chrome neutral.** With a colour field bound next to the size field, the size legend symbols draw in the hard-coded grey-teal `#759595` (traced and observed on the size and colour variant). They change to the modern label grey with the grid colour as the border. This is common rule C20. Confirmed on 2026-11-04.
- **No change for point counts, outliers, clusters and tight correlation.** The transparency handles overlap well up to 2,000 points. The author zooms or filters for outliers.

## Proposed look

Drawn to the decisions at the default card size, with invented values. Titles name the fields, vertical and horizontal grid, one palette colour, a top legend for groups, a capped bubble size, and a teal contour.

### Light

*The drawn cards that show the proposed look are in the HTML page.*

### Dark

## Shipped today against the decisions

| Setting | Shipped today | Proposed |
|---|---|---|
| Colour, one series | Legacy grey-blue at about 60% opacity. *Observed.* | Modern palette slot 1 (C11). |
| Grid | Horizontal only. *Observed.* | Horizontal and vertical (C3). |
| Axis titles | Both on, with the aggregate: "Sum(Price)". Contour shows the field name. *Observed.* | Field name only. |
| Shape field | Hollow pale outlines. *Observed.* | Filled markers. |
| Colour by a measure | Teal ramp, light to dark. *Observed.* | Diverging ramp if the measure crosses zero. |
| Size legend symbols | Grey-teal `#759595` when a colour field is bound. *Observed and traced.* | Chrome neutral (C20). |
| Largest bubble | Up to about 28px. *Observed.* | About 20px. |
| Scatter contour colour | Blue ramp. *Observed.* | Teal ramp. |
| Contour map extent | The whole world. *Observed.* | Fit to the data. |
| Legend | Right. *Observed.* | Top for 2 to 5 entries (C7). Size and Color legends stack on the right. |
| Title line | A line under the card title on point. None on scatter contour. *Observed.* | Removed (C2). |

## Variants

Captured from live charts on a server dashboard, with every chart style and format at its default. The data is made up: items with a price, units sold, revenue and margin, in three segments, with sets of 10, 60, 500 and 2,000 items. Other columns hold three clusters, a tight correlation, extreme outliers and values around zero. Only the bound fields and the card size differ between variants.

| Live capture | Variant | What it shows | What it means for the defaults |
|---|---|---|---|
| ![Point 60 points (canonical)](../point/img/point.png) | 60 points (canonical) | Small semi-transparent grey-blue dots in the legacy colour. Horizontal grid only, both axis titles on ("Sum(Price)", "Sum(Units)"), and the line under the card title. | C2, C3 vertical grid, C11, axis title text. |
| ![Point 10 points](../point/img/point_few_10_.png) | 10 points | About 5px dots, isolated and easy to read but sparse and small. | No change proposed. |
| ![Point 500 points](../point/img/point_many_500_.png) | 500 points | The dots overlap into a band and the transparency shows density. | No change. |
| ![Point 2,000 points](../point/img/point_very_many_2000_.png) | 2,000 points | A dense band, still readable because of the transparency. Outliers stay visible. | No change. |
| ![Point 3 groups on Color](../point/img/point_3_groups.png) | 3 groups on Color | Modern palette dots with a legend on the right. The groups overlap heavily in this data. | C7: top legend for 2 to 5 entries. |
| ![Point 5 groups](../point/img/point_5_groups.png) | 5 groups | Five colours on the same band, legend on the right. | C7: top legend. |
| ![Point 8 groups](../point/img/point_8_groups.png) | 8 groups | Eight colours are hard to tell apart on small dots. The legend lists all eight on the right. | C7: right legend above 5. |
| ![Point A measure on Color](../point/img/point_color_by_measure.png) | A measure on Color | A teal ramp from light to dark with a gradient legend, labelled -0.6 to 0.6. The measure runs both below and above zero, but the ramp is one-directional. | A diverging ramp for a measure that crosses zero. |
| ![Point A measure on Size](../point/img/point_size_by_measure.png) | A measure on Size | Bubbles up to about 28px with a symbol legend (2K, 1.5K, 1K, 500, 0). The biggest overlap and hide dots. | Cap the largest bubble. |
| ![Point Size and Color together](../point/img/point_size_and_color.png) | Size and Color together | Two legends stack on the right, a size legend above a colour legend. The big bubbles are opaque and hide each other. | Same bubble cap. C9 applies to the stack. |
| ![Point Shape by group](../point/img/point_shape_by_group.png) | Shape by group | Hollow circle, triangle and square outlines in a pale blue. They are thin and faint, and the legend is hollow too. | Filled markers. |
| ![Point Three clusters on Color](../point/img/point_clusters.png) | Three clusters on Color | Three clearly separated clusters in palette colours, with a legend. The dense cluster merges into a solid blob. | No change. |
| ![Point A tight correlation](../point/img/point_tight_correlation.png) | A tight correlation | A clean diagonal line of dots. | No change. |
| ![Point Outliers](../point/img/point_outliers.png) | Outliers | Two extreme points stretch both axes to about 250 and 100. The main cluster is squeezed into a corner. | No change. The author zooms or filters. |
| ![Point Negative values on both axes](../point/img/point_negative_values.png) | Negative values on both axes | The cloud straddles zero on both axes. The horizontal axis line is drawn at zero and the labels stay at the bottom. There is no vertical zero line. | C18, and a vertical zero line. |
| ![Point A category on X](../point/img/point_category_on_x.png) | A category on X | A strip of dots for each category on the horizontal axis. Labels are rotated 90°. | C5 label rule. |
| ![Point Scatter contour, 500 points](../point/img/scatter_contour.png) | Scatter contour, 500 points | Blue density contours that fade to transparent, with no points drawn, no legend and no title line. The axis titles read "Price" and "Units", without the aggregate wrapper. The ramp is blue, not the modern teal. | Teal ramp. |
| ![Point Scatter contour, 3 clusters](../point/img/scatter_contour_clusters.png) | Scatter contour, 3 clusters | Three separate density blobs. Reads well. | Teal ramp. |
| ![Point Scatter contour, 2,000 points](../point/img/scatter_contour_many_2000_.png) | Scatter contour, 2,000 points | The same shape with smoother edges. | No change. |
| ![Point Contour map, 280 stores in 6 metro areas](../point/img/contour_map.png) | Contour map, 280 stores in 6 metro areas | The map shows the whole world in pale grey with country borders, and the density is one small blue blob over the middle of the United States, with a faint tail to the west. The map is not zoomed to the data, so the six clusters merge. Latitude and Longitude are recognised as geographic measures by name. The density is blue and there is no legend. The line under the card title shows. | Fit the map to the data. Teal ramp, as for scatter contour. |
| ![Point Small card, 300×180](../point/img/point_small_card_300x180_.png) | Small card, 300×180 | The plot is about 60% of the card height, with sparse labels. It reads fine. | C6 axis titles stay on for point, so nothing to change. |
| ![Point Large card, 800×480](../point/img/point_large_card_800x480_.png) | Large card, 800×480 | Reads well with more labels. | No change. |

## Build items

| # | Item | Cost | Where | Notes |
|---|---|---|---|---|
| 1 | Vertical grid for continuous X axes (C3) | Seed only | `PlotDescriptor.xGridStyle` | Shared with the common build list, item 2. Check that it follows type changes. |
| 2 | Axis title without the aggregate | Small change | The title text built from the bound field. Not traced. | Contour already does it. Check what it does for a non-aggregated point and a real aggregate. |
| 3 | Filled markers with a shape field | Small change | The shape frame setup in `GraphGenerator`. Not traced. | Today the shape frame draws outlines. Check legacy charts. |
| 4 | Diverging ramp for a measure that crosses zero | New behaviour | The colour frame for a measure on Color. Not traced. | Needs the data range. Teal stays when the range does not cross zero. |
| 5 | Largest bubble about 20px | Seed only | The linear size frame, where the largest defaults to 30. Traced. | Write the largest for a measure on Size. An author value must win. |
| 6 | Teal ramp for scatter contour | Seed only | The contour colour frame setup. Not traced. | Same ramp as the maps. |
| 7 | Contour map fits its extent to the data | New behaviour | The map extent setup for the geographic chart. Not traced. | Needs the bounding box of the points, with a margin. An author extent must win. |
| 8 | Vertical zero line when X crosses zero | New behaviour | The plot baseline, with C18. Not traced. | Same colour as the horizontal one. Only when the X range includes zero. |
| 9 | Size legend symbols in a chrome neutral (C20) | Seed only | `LegendItem.SYMBOL_COLOR`. Not traced further. | Traced: the symbol falls back to a constant when the element has no colour. |
| 10 | Common items | Common | See the common page | C2, C6, C7, C9 and C11 apply to point without extra work here. |

## Open items

- None. The explicit-extent question is a build note on the contour map decision. The Break By shelf is under Known limits.

## Known limits

Settled: no default change.

- Eight groups of dots are hard to tell apart. The same limit applies to line and bar.
- The contour map's Break By shelf: with a Store dimension on it the picture did not change, and I did not find out what that shelf contributes to the density. This is not a default question.
- With very few points (10) the dots are small. A data-dependent dot size is new behaviour with little payoff, so there is no change.
