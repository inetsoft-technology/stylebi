# Open questions for engineering

The design pages say what the defaults should look like and why. This file collects what the design could not settle without knowledge of the code. Use it as the checklist for the first pass of a spec. The build items come straight from the build tables on each page.

Labels used below:

- **not traced**: the design names a behaviour but nobody found where in the code it is set.
- **check needed**: the design names a risk to check before changing the code, for example an interaction with another feature or with saved charts.

## Blocked items on the pages

### financial

- Blocked on the product: can the product set the value range per panel? A Shared Range checkbox exists on value axes and is on by default. On a panelled chart the numbered value axis cannot be selected, so Axis Properties cannot be reached. Parked for now.
- Blocked on the build: the contrast figures use an estimated card colour. The measured real-card figures were 3.4:1 to 3.6:1 in light and 4.2:1 to 4.4:1 in dark, which differ from the estimates. The translucent card is not in the build yet, so the live captures on this page show the current white card with a thin border, on dashboards that are marked modern. They cannot supply the translucent card colour. Sample the card once the translucent card lands.

## Unknowns found while investigating

- **Contour map, Break By shelf** (point page): with a Store dimension on the Break By shelf the density picture did not change. It is not known what that shelf contributes. Not a default question.
- **Gantt milestone legend** (Gantt page): the legend is titled "Measures" with Start and Milestone and leaves out End. Where the title and entries come from is not traced.
- **Where each size and thickness rule lives**: bar width, the Gantt row share, funnel stage height and the waterfall minimum height are described as design rules. Several are marked "not traced" in the list below.
- **Per-panel value range** (financial page): a Shared Range checkbox exists, but the numbered value axis of a panelled chart cannot be selected, so Axis Properties cannot be reached. Parked until the product allows it.

## Build items that still need code investigation


### common

- **0. No line under the chart title (C2)** (Seed only; check needed). Where: VSAssemblyInfo.seedChromeDefaults (the title format block), VSAssemblyInfo.setDefaultFormat, vs-title.component.html (reads titleFormat.border.bottom) Notes: Trace first: find where the default title format switches the bottom border on. Today the seed only sets the border colour. Must leave the check box and radio button inset header alone, and must keep an author's title border.
- **4. Legend border none, legend fill none** (Seed only, plus client check; check needed). Where: LegendsDescriptor (border and the default format), LegendDescriptor.initDefaultFormat, VSChartChromeDefaults, CSSChartStyles, chart-legend-container.component.ts Notes: Check that the client legend wrapper draws nothing when the model has no border or fill. Keep both for an in-place legend. Revert must restore the thin line and white at alpha 50.

### bar

- **1. Bar width as a proportion with a floor and a cap** (New behaviour; not traced, check needed). Where: The width ratio of the bar element in the graph package. Not traced. Notes: Also covers box plot, with its own numbers. Check how the ratio interacts with clustered groups.
- **2. Horizontal category order, first at the top** (New behaviour; not traced, check needed). Where: The coordinate and scale setup for a rotated chart. Not traced. Notes: Check the sort option and any saved chart that relies on the current order.
- **4. Zero line for negative values** (New behaviour; not traced). Where: The plot coordinate baseline. Not traced. Notes: Uses the axis line colour from C4.
- **6. Legend in stack order for stacked bars** (Small change; not traced, check needed). Where: The legend entry order for a stacked series. Not traced. Notes: Check that clustered bars keep their natural order, and that a stacked area follows the same rule.

### line

- **1. Straight segments as the default (confirmed)** (Seed only; not traced, check needed). Where: The line element smoothing property. Not traced. Notes: Check whether smoothing is a tiered value or a plain one, and whether old saved charts must keep their curve.
- **2. Date grouping defaults to month (confirmed)** (Small change; not traced). Where: The date level chosen when a date is dropped on a shelf. Not traced. Notes: A fixed default, so no data lookup. Applies to every chart with a date on a shelf, so check bar and area. An author level wins. Where the year default comes from is not traced.
- **3. Stacked area with a Color dimension (confirmed)** (Seed only; not traced). Where: The area chart style setup. Not traced. Notes: Same Stack box as bar. The choice of the author wins.

### point

- **1. Vertical grid for continuous X axes (C3)** (Seed only; check needed). Where: PlotDescriptor.xGridStyle Notes: Shared with the common build list, item 2. Check that it follows type changes.
- **2. Axis title without the aggregate** (Small change; not traced, check needed). Where: The title text built from the bound field. Not traced. Notes: Contour already does it. Check what it does for a non-aggregated point and a real aggregate.
- **3. Filled markers with a shape field** (Small change; not traced, check needed). Where: The shape frame setup in GraphGenerator. Not traced. Notes: Today the shape frame draws outlines. Check legacy charts.
- **4. Diverging ramp for a measure that crosses zero** (New behaviour; not traced). Where: The colour frame for a measure on Color. Not traced. Notes: Needs the data range. Teal stays when the range does not cross zero.
- **6. Teal ramp for scatter contour** (Seed only; not traced). Where: The contour colour frame setup. Not traced. Notes: Same ramp as the maps.
- **7. Contour map fits its extent to the data** (New behaviour; not traced). Where: The map extent setup for the geographic chart. Not traced. Notes: Needs the bounding box of the points, with a margin. An author extent must win.
- **8. Vertical zero line when X crosses zero** (New behaviour; not traced). Where: The plot baseline, with C18. Not traced. Notes: Same colour as the horizontal one. Only when the X range includes zero.
- **9. Size legend symbols in a chrome neutral (C20)** (Seed only; not traced). Where: LegendItem.SYMBOL_COLOR. Not traced further. Notes: Traced: the symbol falls back to a constant when the element has no colour.

### pie

- **2. Sort slices from largest to smallest, start at 12 o'clock** (New behaviour; not traced, check needed). Where: The pie coordinate and the data order for pie and donut. Not traced. Notes: An author sort must win. Check the start angle and direction in the polar coordinate.
- **3. Pie and donut size: density padding (C19), at most 80% of plot height** (New behaviour; not traced, check needed). Where: The pie and donut coordinate sizing in the polar layout. Not traced. Notes: Uses the chart padding seed from C15. An author size must win. Check the donut hole stays at about 45% of the new diameter.

### radar

- **2. Spoke labels: field name, truncated** (Small change; check needed). Where: RadarGraphGenerator.fixParallelCoord, AbstractParallelCoord.createAxis Notes: Shares the label text rule with the other axes. Check an author alias still shows.
- **3. Scale labels on one spoke only** (New behaviour; not traced). Where: AbstractParallelCoord.createAxis. Not traced further. Notes: Each spoke has its own scale, so the label would need to name the scale for spokes that differ.
- **5. Series line width 1.8px, filled radar at 30% opacity** (Seed only; not traced, check needed). Where: The line size frame and the fill alpha for the radar element. Not traced. Notes: Check how the fill alpha is stored.
- **6. Radar padding of at least the density padding (C19)** (New behaviour; not traced, check needed). Where: The radar layout in RadarGraphGenerator and the parallel coordinate size. Not traced. Notes: Uses the chart padding seed from C15. Check that long labels still fit.

### hierarchy

- **1. Colour by the top level with no colour field** (New behaviour; not traced). Where: The colour setup for the treemap family in GraphGenerator and the chart type change in ChangeChartTypeProcessor. Not traced. Notes: The wizard already puts a dimension on colour for treemap. Old saved charts keep their blue. An author clearing it must stick.
- **2. Hide the size legend for the treemap family** (Seed only; not traced, check needed). Where: The size legend visibility in GraphGenerator legend setup. Not traced. Notes: Check that sunburst, circle packing and icicle share the setting.
- **3. Lighter step of the group colour per level, sunburst and icicle** (New behaviour; not traced, check needed). Where: The colour frame for the sunburst and icicle in GraphGenerator, and the companion colour rule in VSChartPaletteDefaults. Not traced further. Notes: Reuse the companion step, capped at three levels. An author colour per level must win. Check label contrast on the lightest step.

### relation

- **1. Open the tree at its root and scale it to the card width** (New behaviour; not traced). Where: The tree coordinate and its layout size in GraphGenerator. Not traced. Notes: Traced to RelationElement COMPACT_TREE with scalePositions false. The initial scroll offset is also a client matter. Scale to the card width with a minimum box size, then scroll.
- **2. Nodes in palette colour 1, edges in a chrome neutral** (New behaviour; not traced). Where: The relation colour frames in GraphGenerator. Not traced. Notes: Part of C11. Old saved charts keep their blue.
- **3. Cap the edge thickness at about 6px** (Seed only; not traced). Where: The edge size frame for a relation chart. Not traced. Notes: The linear size frame defaults to a largest size of 30, traced.
- **5b. Tree box tint with outline and a corner of about 0.08** (New behaviour, plus a seed; not traced, check needed). Where: RelationVO node paint and resolveNodeShape, and the node corner radius seed. The corner is arc = radius × short side × 2 with radius at most 0.5. Not traced further. Notes: Check that the tree takes the same seeded radius as bars. A fill alpha and a stroke on the node need a new setting.

### gantt

- **1. Bars in palette colour 1 with a corner of about 0.08** (Seed only; not traced). Where: The Gantt element colour frame and the bar corner seed. Not traced. Notes: Part of C11 and the bar corner decision. Old saved charts keep their blue.
- **3. Grid lines at labelled ticks only** (Seed only; not traced). Where: The Gantt coordinate grid settings. Not traced. Notes: 
- **3b. Bar height 50% of the row, between 3px and 32px** (Seed only; not traced). Where: The Gantt bar size in GanttGraphGenerator or the size frame default. Not traced. Notes: The same rule as bars on the bar page.
- **4. Minimum bar height and thinned task labels** (New behaviour; not traced). Where: The Gantt bar size and the category axis label thinning. Not traced. Notes: 
- **6. Remove the Task axis title** (Seed only; not traced). Where: The Gantt axis title default. Not traced. Notes: Same rule as the other families.

### funnel-waterfall

- **1. Palette colour 1 for the funnel and pareto bars, colour 2 for the pareto line** (New behaviour; not traced). Where: The colour frames in GraphGenerator. Not traced. Notes: Part of C11. Old saved charts keep their blue.
- **3. Funnel colours and legend in funnel order** (Fix; not traced). Where: The funnel colour frame and legend comparator. Not traced. Notes: Observed: alphabetical colours, reverse alphabetical legend.
- **4. Direction colours on the waterfall, Total in the neutral token** (New behaviour; not traced). Where: The waterfall colour frame. Not traced. Notes: Part of C12.
- **5. Keep the data order on a waterfall** (New behaviour; not traced). Where: The default sort of the waterfall category. Not traced. Notes: Only when the author has not sorted.
- **6. Round value axis ticks that include zero on a waterfall** (Fix; not traced). Where: The waterfall value scale. Not traced. Notes: 
- **7. Colour the marimekko by its Break By field** (New behaviour; not traced). Where: The marimekko colour binding. Not traced. Notes: Only when no colour field is set.

### geo

- **1. Highlighted regions in palette colour 1 at about 30%, unmatched in the grid colour** (New behaviour; not traced). Where: The map element fill in GraphGenerator. Not traced. Notes: Part of C11. Old saved charts keep the pale orange.
- **2. Start the colour ramp at about 25% of its range** (Seed only; not traced). Where: The map colour frame default. Not traced. Notes: 
- **3. No border and no fill on the gradient legend** (Seed only; check needed). Where: Common legend rule C8. Notes: Check that the gradient legend follows the same setting as the item legend.
- **4. Cap the point size on a map** (Seed only; not traced). Where: The size frame default for a map point element. Not traced. Notes: Same as the point chart.


Counts: 45 build items flagged across 11 pages.
