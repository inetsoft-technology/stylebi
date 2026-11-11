<!-- Markdown version of common/common.html. The drawn "proposed look" cards are in the HTML page. -->

# Chart defaults: common rules

The rules every chart type shares, on the translucent no-border card (`#AAAAAA` at 15%, PR #7319) in the modern palette. Each rule has an ID, so a family page can say "follows C5" or "overrides C6". What differs for a chart type lives on its family page, with drawn cards and live captures. Facts marked *observed* come from live charts on a server. Facts marked *traced* come from reading the code on `epic-74519`. Anything not marked is a decision or a proposal, and none of the proposals is built yet.

## Family pages

| Family | Chart types | Page |
|---|---|---|
| Financial | Candle, stock, box plot | [Written up, with variants](financial.md) |
| Bar and interval | Bar, horizontal bar, interval | [Written up, with variants](bar.md) |
| Line and area | Line, step line, jump line, area, step area | [Written up, with variants](line.md) |
| Point and contour | Point, scatter contour, contour map | [Written up, with variants](point.md) |
| Pie and donut | Pie, donut | [Written up, with variants](pie.md) |
| Radar | Radar, filled radar | [Written up, with variants](radar.md) |
| Hierarchy | Treemap, sunburst, circle packing, icicle | [Written up, with variants](hierarchy.md) |
| Relation | Tree, network, circular network | [Written up, with variants](relation.md) |
| Gantt | Gantt | [Written up, with variants](gantt.md) |
| Geo | Map | [Written up, with variants](geo.md) |
| Funnel and waterfall | Funnel, waterfall, pareto, marimekko | [Written up, with variants](funnel-waterfall.md) |

## Rulebook

Status is Decided or Proposed. Cost is the build class: **Seed only** is a default-tier write under the modern mark, **Needs provenance** is a plain field that cannot tell a seeded value from the author's, and **New behaviour** is a new property or layout rule. A family page can override a rule and must say so.

| ID | Rule | Status | Cost | Notes |
|---|---|---|---|---|
| C1 | Card | Decided | Shipped | Translucent grey, no border (PR #7319). The translucent card is not in the build yet, so the live captures show the current white card with a thin border. The card tint stays at or below about 30%. See C16. |
| C2 | No line under the chart title | Decided | Seed only | Every live capture shows a horizontal line under the card title. It is the title's bottom border: the web client draws it from the title format's border value, and the base chrome seed only recolours that border to the modern object-border colour. The modern mark would write no bottom border on the default tier of the chart's title format, so an author's border still wins and Revert restores it. The card is borderless and the title is plain text, so the line adds a frame the card does not otherwise have. Check: confirm the default format is where the line is turned on, and that no other assembly type is affected. The inset header on check box and radio button titles is a separate rule and stays. Confirmed on 2026-11-03 on the bar page, and used on every later family page. |
| C3 | Grid lines | Decided | Seed only | Horizontal hairlines on the value axis only, `#D6D2C8` light and `#4A484F` dark, 1px. Vertical lines only for a continuous or time x axis. None for charts where the fill is the data. Shipped grid is about 1.06:1 on the card, nearly invisible. Confirmed on 2026-11-03 on the bar page, and used on the pareto, waterfall and funnel pages. |
| C4 | Axis line | Decided | Needs provenance | On for the category or time baseline only. The value axis line is off. Charts with two continuous axes keep both. Colour `#85827A` light, `#7E7B86` dark, which clears 3:1. Confirmed on 2026-11-03 on the bar page. |
| C5 | Axis labels | Decided | New behaviour for the rule below | On for every axis that carries values or categories. Unit goes in the card title. **Label rule:** thin first, rotate only as a last resort and never past 45°, label band at most about 20% of the plot height, truncate beyond about 12 characters, compact dates. Tested on candle, stock and box plot captures, where 90° labels took up to 45% of a card. Horizontal category axes (horizontal bar, Gantt) use a label band of at most 30% of the card width and truncate beyond about 20 characters, as the bar page decided. |
| C6 | Axis titles | Decided | Seed only | Off. The card title names the measure. On only where two axes carry different measures and nothing else names them: point, marimekko, pareto and scatter contour. Overridden off for candle and stock (four measures describe one price). Box plot keeps the Y title and drops the X title, because the category labels name the dimension. An axis title that is longer than the axis it labels (the plot height for a Y title, the plot width for an X title) is hidden, never cut with an ellipsis. Confirmed on 2026-11-04. Off by default confirmed on 2026-11-03 on the bar page, and the pareto and marimekko exceptions on 2026-11-04 on the funnel and waterfall page. |
| C7 | Legend placement | Decided | Needs provenance, type-aware | Top for cartesian charts with 2 to 5 entries, aligned to the plot's left edge. Right for a colour ramp, part-to-whole charts (pie, donut, the treemap family, funnel, marimekko), network groups and more than 5 entries. A single series has none. |
| C8 | Legend border and fill | Decided | Seed only | None for modern charts. An in-place legend over the plot keeps a fill and border. An author's setting wins. This reverses the PR #7319 spec, which keeps the fill. |
| C9 | Legend size | Decided | New behaviour | Capped at 30% of the width for a side legend and 25% of the height for a top or bottom legend. No plot floor. |
| C10 | Legend gap and alignment | Decided | Seed only, and new behaviour | Gap one step larger for top and bottom: 12, 16, 20 on screen. A new alignment property, start at the plot edge by default for top and bottom legends. |
| C11 | Colour with no colour field | Decided | New behaviour | The first measure takes modern slot 1 and further measures follow the modern palette order, in place of the legacy blue and legacy palette. Never touches a colour the author set. Revert restores the legacy colours. This is the common rule for the single legacy blue. Box plot follows it. Candle, stock and waterfall override it with C12. |
| C12 | Direction colours | Decided | New behaviour | Dedicated up, down and neutral tokens, not palette slots. Teal and amber confirmed. Candle and waterfall use them, and stock when no colour dimension is bound. A family page says where. |
| C13 | Text | Decided | Shipped | 10px labels and legend entries, 11px titles, all plain weight. Modern chrome colours. |
| C14 | Data labels | Decided | Shipped | Off unless Show Values is on or a text field is bound. Automatic for the treemap family and the tree. Also automatic for the funnel and the waterfall: a value on each stage or bar at least about 18px tall. A bar too short for its label shows none. Confirmed on 2026-11-04 on the funnel and waterfall page. |
| C15 | Density | Decided | Shipped | Dense, compact (default) and comfortable drive padding, label gap, title gap and legend gap. See Legend spacing. |
| C16 | Direction colours follow the background | Decided | New behaviour | Pick the light or dark token set by the lightness of the effective background, not only the theme. Light tokens fall to about 2:1 on a dark page. A mid-tone page cannot pass with any fixed pair, so the author's colour is the way out. Keep the card tint at or below about 30%, where the light tokens still reach 3:1 on a light grey page. Contrast figures are on the Financial page. |
| C17 | Mark size floors and caps | Decided | Small change | Marks that carry data keep a minimum so they stay visible: candle body about 3px with a 1px wick, stock tick about 3px, box plot box height about 2px. Box plot box width is 30% of its slot between 6px and 28px. Bar width is 50% of its slot between 3px and 32px. Set per family. A bubble on a point chart or a map is at most about 20px. Pareto and waterfall bars follow the bar width rule, and a waterfall bar is also at least about 2px tall. Gantt bars follow the bar rule, measured on the row (50% of the row, 3px to 32px). A marimekko segment under about 2px stays as a line of at least 1px. |
| C18 | Zero line | Decided | New behaviour | When the value axis includes both positive and negative values, draw the zero line in the axis line colour (C4), an exception to the value axis line being off. Applies to bar, line, point and waterfall. On a scatter that crosses zero on both axes the vertical zero line is drawn too. Confirmed on 2026-11-03. |
| C19 | Plot padding follows density, on all four sides | Decided | New behaviour | The plot, with its labels, keeps the density chart padding (8 dense, 12 compact, 16 comfortable) from the card edges and from the title lane. Measured on 15 canonical captures at compact, the top gap below the title line is about 5px on every chart type, while the bottom is 13 to 16 and the left 17 (these include axis text, so the real padding is about 12). In an experiment on the radar at the three densities, the bottom and left gaps rose by 4px per step (bottom 9, 13, 17 and left 13, 17, 21) but the top stayed at 4 to 5px. So the top padding is the one that is missing. The rule lifts it to the density padding. The title line itself is removed by C2, so the measure is from the title text. Contour map has its own margin and is exempt. Pie and radar follow it, and pie adds an 80% size cap. Confirmed on 2026-11-03. The circular network follows the pie, with the same 80% cap. |
| C20 | Size legend symbols use a chrome neutral | Decided | Seed only | When a size legend has no element colour of its own, its symbols draw in the label grey of the modern chrome (`#6A685F` light, `#CAC4D0` dark) with the grid colour as the border. Shipped falls back to the hard-coded `LegendItem.SYMBOL_COLOR`, a grey-teal `#759595` with a `#A3A3A3` border, whenever a colour field is bound next to the size field. That colour belongs to neither the palette nor the chrome. A size legend means "how big", not "which category", so a neutral is right. Hierarchy charts hide the size legend altogether (their size is the cell area). Where the chrome colour reaches `LegendItem` is not traced, because the graph package has no modern-mark context. Confirmed on 2026-11-04 on the relation page and the geo page. |
| C21 | Label colour on a mark | Decided | Fix | Text drawn on a filled mark is white or near-black, chosen per fill so that it reaches 4.5:1. Under the modern chrome the engine compares the label colour with exactly #4B4B4B, so its automatic contrast rule no longer runs. Applies to the treemap family, the funnel and the waterfall. Tree boxes use a dark ink on a light tint instead. Confirmed on 2026-11-03 on the hierarchy page and on 2026-11-04 on the relation and the funnel and waterfall pages. Confirmed as a common rule on 2026-11-04. |
| C22 | Bar corners | Decided | Seed only | A radius of about 0.08 of the bar thickness, with a floor of 2px and a cap of a quarter of the thickness, so a bar under 8px thick gets less than 2px (2px from 8px up to about 25px thick, 2.6px on the widest 32px bar), in place of the modern seed of 0.3. A bar that grows from a baseline rounds its free end only and keeps its base square. A bar that floats between two values rounds both ends: interval, Gantt and waterfall. The box plot box follows the same radius on all four corners. An author's radius wins. Confirmed on 2026-11-03 on the bar page and on 2026-11-04 on the Gantt and the funnel and waterfall pages. Confirmed as a common rule on 2026-11-04. |
| C23 | Colour ramp for a measure | Decided | New behaviour | A measure on colour that stays on one side of zero uses the shipped teal ramp, light to dark. A measure that crosses zero uses the diverging ramp, centred on zero. On a map the light end keeps a floor at about 25% of the range, so the lowest data value stays distinct from the fill for no data. Confirmed on 2026-11-03 on the point page and on 2026-11-04 on the geo page. Confirmed as a common rule on 2026-11-04. |
| C24 | Legend order | Decided | Fix | Entries are listed in the order of the marks: stack order with the top segment first for a stacked bar, and funnel order with the top stage first for a funnel. Shipped lists the bar legend in the reverse order and the funnel legend in reverse alphabetical order. Confirmed on 2026-11-03 on the bar page and on 2026-11-04 on the funnel and waterfall page. Confirmed as a common rule on 2026-11-04. |

## Bar corner check

C22 tested at the bar thicknesses seen on the family pages. The radius is a fraction of the bar thickness, so a thin bar gets a tiny corner. The code builds every bar of these charts (bar, horizontal bar, interval, pareto, waterfall and Gantt) in one place, `BarVO.buildRoundedBarShape`, as `min(fraction × short side, short side / 2)`, so one change reaches all of them. Funnel stages and 3D bars are skipped there, and the box plot is drawn by its own painter. *Traced.*

| Where | Thickness | Charts | Shipped seed, 0.3 | 0.08 alone | 0.08 with the floor |
|---|---|---|---|---|---|
| Widest bar (the 32px cap) | 32px | bar, interval | 9.6px | 2.6px | 2.6px |
| Bar, 5 categories; waterfall, 8 items | 21px | bar, horizontal bar, interval, waterfall | 6.3px | 1.7px | 2.0px |
| Pareto, 8 defects | 17px | pareto | 5.1px | 1.4px | 2.0px |
| Gantt, 8 tasks | 10px | Gantt | 3.0px | 0.8px | 2.0px |
| Minimum for a 2px floor to fit (8px) | 8px | any | 2.4px | 0.6px | 2.0px |
| Waterfall, 20 items; bar, 30 categories | 6px | waterfall, bar | 1.8px | 0.5px | 1.5px |
| Gantt, 25 tasks | 5px | Gantt | 1.5px | 0.4px | 1.2px |
| Pareto, 20 defects | 4px | pareto | 1.2px | 0.3px | 1.0px |
| Smallest bar (the 3px floor) | 3px | bar, Gantt at 60 tasks | 0.9px | 0.2px | 0.8px |
| Waterfall bar of minimum height | 2px | waterfall | 0.6px | 0.2px | 0.5px |

With 0.08 alone, every bar up to about 25px thick has a corner under 2px, which reads as square. That covers the typical bar (21px), the Gantt (10px) and everything thinner. With the floor, bars from 8px to about 25px get 2px, and the widest bar gets 2.6px. Under 8px the corner is a quarter of the thickness, so a 3px bar keeps a corner under 1px and never turns into a pill. Edge cases from the code: a bar shorter than it is thick (a short Gantt task, a small waterfall step) uses its short side, so its corner is smaller still. A stacked bar rounds only the outer end of the whole stack, using the full bar width. An author's own radius still wins, because the floor only applies when it is larger than the fraction. Drawn below at 3 times the real size, with the shipped seed, 0.08 alone and 0.08 with the floor.

## How the family pages were built

Each chart type was built on a server dashboard with every chart style and format left at its default, on made-up data. Only the data and the card size change between variants, so the capture shows the default and not an author's formatting. Variants are chosen because they change the look: how many categories, how many series, label length, card size, and edge shapes in the data. Candle was the pilot.

## Baseline: what is already set

Read from `VSChartChromeDefaults`, `VSDensityDefaults`, `VSChartPaletteDefaults`, the legend, axis, plot and title descriptors, and the chart-card design docs under `docs/superpowers/specs/lookfeel/`. "Modern" means a chart whose dashboard is marked modern. Compact is the default density. These values apply to a chart with no author setting, and an author's value always wins. Per chart type, I traced the axis, coordinate, title, legend and data-label code in `GraphGenerator`, the generators and the coordinate classes. It was a static read, not a run. Items I could not settle are listed under the table.

| Area | Setting | Value shipped | Driven by |
|---|---|---|---|
| Spacing | Chart padding, card edge to chart | 8 dense, 12 compact, 16 comfortable. 10 when not modern. | Density. The geometry decisions doc calls this the card inset. |
| Axis label gap, labels to plot | 6 / 8 / 12. Engine default 2 when not modern. | Density |  |
| Axis title gap | 2 / 4 / 6 | Density. A hidden label band hands its gap to the title, so no empty stub. |  |
| Legend gap, plus 2px added by the graph layout | 10 / 14 / 18, so 12 / 16 / 20 on screen. 0 not modern. | Density. Compact matches the 16px in the chart-card spacing model. |  |
| Legend padding (top, left, bottom, right) | 0, 2, 0, 2 dense. Engine insets at compact. 1, 6, 2, 6 comfortable. | Density |  |
| Chrome colour | Gridline, light and dark | `#E8E5DE`, `#3A383D` | Modern chrome. Legacy `#EEEEEE`. |
| Axis line | Borrows the gridline neutral | `resolveAxisLineColor` |  |
| Axis and legend labels | `#6A685F` light, `#CAC4D0` dark | Modern chrome. Legacy `#4B4B4B`. |  |
| Axis and legend titles | `#35342F` light, `#E6E0E9` dark | Modern chrome. Legacy `#2B2B2B`. |  |
| Legend border | 1px thin line, grid neutral, rounded corners on new charts | Border is thin by default. Corners are set rounded at creation in `ChartVSAssemblyInfo`. |  |
| Legend fill | White, or `#252428` in dark, at alpha 50 | `LegendsDescriptor`, and the PR #7319 spec keeps it. |  |
| Structure | Axis line, tick marks, labels | Line on, ticks off, labels on | `AxisDescriptor` defaults, the same for every axis |
| Grid lines | Horizontal on, vertical off | `PlotDescriptor` y grid style thin, x grid style none |  |
| Axis titles | Visible, with the bound field names as text. Not tied to an axis existing: pie, donut, radar and funnel still get a y title (the measure name). The treemap family, tree and networks get none with the default binding, and maps get none unless X and Y fields are bound. | `TitleDescriptor` default visible. `VGraph` draws any title that has text, whatever the coordinate. |  |
| Legend placement | Right, for every type and every creation path | `LegendsDescriptor` default. No code path sets top, bottom or left on creation, including the wizard. A top legend comes from a user drag to an edge, the Format Legend dialog, the `legendPosition` script property, the wizard copying the legend from an existing chart, or a saved file with a stored layout. So the top legends in the two screenshots were set by a user or came in from another chart. |  |
| Legend size | Content-sized, capped at 50% of the chart on that axis | `VGraph` layout |  |
| When a legend appears | Only when a colour, shape or size field is bound, or there are two or more measures (a "Measures" colour legend). One measure and no colour field shows none. Hidden for a sparkline, for the duplicate colour and size legends on contour charts, for a donut with a total, and when the layout is set to none. What a new chart binds: changing a chart to pie or donut moves its last dimension onto colour, so a legend appears. The wizard puts a dimension on colour for pie, donut and treemap. Changing to a treemap with one measure sets size only, so no legend. Funnel and waterfall get no colour, size or shape. Dropping a field on the plot area fills the first empty aesthetic slot, colour first. | `VisualFrame.isVisible`, `GraphGenerator` legend setup |  |
| Legend title | 10px, plain, the modern title colour (`#35342F` light, `#E6E0E9` dark). Not bold. The text is the legend's field name, or "Measures". | `LegendsDescriptor.initDefaultFormat`. The bold 11px fallback in `Legend.createTitle` applies only when no title format exists. |  |
| Data labels on shapes | Off by default (Show Values is off). Automatic for the treemap family and the tree only. Network and circular network need Show Values or a text field. | `PlotDescriptor.valuesVisible` false. Treemap text frame and tree `forceText` in `GraphGenerator`. |  |
| Colour | Categorical palette | Modern 8-slot head and tail up to 40. Modern Dark has its own set. | `VSChartPaletteDefaults`, with `defaults.css` overrides |
| Measure to colour ramp | Teal modern, Blues not modern. Amber and Variance also exist. | Ramps from PR #5345, on `epic-74519` and not yet on `main` |  |
| Brushing and target bands | A companion colour of the series colour | PRs #5311 and #5275 |  |
| Chart with no colour field (one or more measures) | The legacy blue `#518DB9` for the first measure, and legacy palette colours for further measures. Not the modern palette, and not modern-gated. | `StaticColorFrame.DEFAULT_COLOR`. The modern palette and ramp seeding reaches only categorical colour on a dimension and the linear ramp. This is a static read, not confirmed at run time. |  |

### Cost classes

Read from the field declarations in the descriptors, not from a prototype. "Seed only" means the default tier of a tiered value can be written under the modern mark, an author's value wins, and Revert restores it, with no new property. "Needs provenance" means the field is a plain value with no tiers, so a seeded value cannot be told from the author's and Revert cannot undo it, the same class the geometry decisions doc already names. "New behaviour" means a new property or layout rule.

### Build list

Common items, cheapest first, with the files each would touch. Line numbers drift, so search by name. Every item needs a Modernize and Revert round trip: `VizModernizeUtil` re-runs the seed on every mark change, and a legacy write must happen only on a transition, not on a plain restore. Tests belong beside `ChromeSeedClassificationTest` and `ModernVisualizationDefaultTest`. Items that belong to one family, such as direction colours for candle, waterfall and stock, are on that family's page.

| # | Item | Cost | Where | Decision or risk |
|---|---|---|---|---|
| 0 | No line under the chart title (C2) | Seed only | `VSAssemblyInfo.seedChromeDefaults` (the title format block), `VSAssemblyInfo.setDefaultFormat`, `vs-title.component.html` (reads `titleFormat.border.bottom`) | Trace first: find where the default title format switches the bottom border on. Today the seed only sets the border colour. Must leave the check box and radio button inset header alone, and must keep an author's title border. |
| 1 | Axis titles off (most types) | Seed only | `TitleDescriptor`, `ChartVSAssemblyInfo.seedChromeDefaults`, `ChangeChartTypeProcessor`, `VizModernizeUtil` | Write the default tier per chart type, and reseed on a type change. A user toggle through `VSChartTitlesVisibilityService` must still win. The per-type on and off list is in C6 and on the family pages. |
| 2 | Vertical grid for continuous or time x axes | Seed only | `PlotDescriptor.xGridStyle`, `ChartVSAssemblyInfo`, `ChangeChartTypeProcessor` | Seed by chart type. Marimekko draws no grid lines whatever the style says, and radar ignores the plot grid styles, so neither is affected here. |
| 3 | New grid, axis line and legend border colours | Seed only | `VSChartChromeDefaults` (grid, axis line and legend border constants), `CSSChartStyles.apply` | The axis line currently borrows the grid colour in `resolveAxisLineColor`, so it needs its own constant. The grid constant is documented as equal to the table gridline, so changing it separates the two. |
| 4 | Legend border none, legend fill none | Seed only, plus client check | `LegendsDescriptor` (border and the default format), `LegendDescriptor.initDefaultFormat`, `VSChartChromeDefaults`, `CSSChartStyles`, `chart-legend-container.component.ts` | Check that the client legend wrapper draws nothing when the model has no border or fill. Keep both for an in-place legend. Revert must restore the thin line and white at alpha 50. |
| 5 | Legend gap one step larger for top and bottom | Seed only | `VSChartChromeDefaults.resolveLegendGap` and `legendGapForMode`, and its callers: `GraphGenerator` (three sites), `LegendFormatDialogModel` (two), `VSChartResizeLegendService` | Add the layout as a parameter and update every caller. The layout is not tiered, so a user drag changes the seeded gap. |
| 6 | Keep auto white or black label text under modern (C21) | Fix | `GraphGenerator` (the label colour tests near lines 1160, 1235 and 1546), `ChartVSAssemblyInfo.seedChromeDefaults`, `ChartRefImpl` | The tests compare the label colour to exactly `#4B4B4B`, so modern charts probably lose the rule. The plot-level seed uses the title colour while per-field labels use the label colour. Pick one. |
| 7 | Value axis line off | Needs provenance | `AxisDescriptor.lineVisible` and its XML read and write, `GraphGenerator` (`setupAxisSpec` and the funnel and shared sites), `AxisPropertyDialogModel`, `VSChartAxesVisibilityService` | Make the field tiered, or add a user-set flag following the `isUserBarCornerRadius` pattern. Old saved charts must keep their line. |
| 8 | Legend top by default for cartesian charts with 2 to 5 entries | Needs provenance, type-aware | `LegendsDescriptor.option`, `ChartVSAssemblyInfo.seedChromeDefaults`, `ChangeChartTypeProcessor`, and the user paths: `VSChartMoveLegendService`, `LegendFormatDialogModel`, `VSChartLegendsVisibilityService`, `ChartProcessor` (`legendPosition`), `SyncChartHandler` | Each user path must mark the layout as set by the user. Nothing sets a layout at creation today. |
| 9 | Legend alignment property | New behaviour | `LegendSpec`, `LegendsDescriptor` (field, XML attributes), `LegendGroup.layoutTB`, the legend setup in `GraphGenerator`, `LegendFormatDialogModel`, `legend-format-dialog.component.ts`, `property-type.properties`, `ChartProcessor` script | Risk: the legend row starts at the chart edge and is centred, and the plot edge is only known after the axes are laid out. Start at the plot edge needs that inset passed into the legend layout. |
| 10 | Legend size caps (30% side, 25% top and bottom) | New behaviour | `VGraph.layout0` (the 0.5 caps), `EGraph` (new property), `GraphGenerator`, `VSChartChromeDefaults` | The graph package has no modern-mark context, so the caps must come in as an `EGraph` property that `GraphGenerator` sets. The plot floor is dropped, since the 30% cap already leaves the plot about 70% of the width. |
| 11 | Radar rings | New behaviour | `RadarGraphGenerator.fixParallelCoord`, `AbstractParallelCoord.createAxis`, `PolarCoord` | Spoke specs are built bare, so the plot grid styles are never read. Wire them in, and add rings or a web. |
| 12 | Tick marks off on the pareto second axis | New behaviour | `GraphDefault` (second axis spec), `GraphGenerator` | Copy tick visibility from the axis descriptor, and do the same for its label and line visibility if wanted. |
| 13 | Modern palette for charts with no colour field | New behaviour | `StaticColorFrame` (default colour), `VSChartAggregateRef`, `ChartAggregateInfoFactory`, `VSColorFrameStrategy`, `VSFrameVisitor`, `GraphUtil.fixDuplicateColor`, `VSChartPaletteDefaults` | Static frames are saved with the chart and live in the graph package, which has no modern-mark context. Existing charts must keep their blue, so this needs a seed with provenance, not a changed constant. |

Suggested order: items 0 to 6 first, since they are seeds in one or two files and change the look most. Then 7 and 8, which need the provenance pattern once and reuse it. Then 9 to 13, each as its own change. Items 8 and 9 should land together, since the top legend is the case where alignment matters.

## Colour palette specs

The modern palette applies only to a chart whose dashboard is marked modern, and only as a seeded default. A classic (unmarked) chart keeps the classic 40-colour palette and the Blues ramp. Precedence, highest first: a colour the author picked, a customer `format.css` `ChartPalette` rule, then the seeded default. The modern values below are the fallbacks in `VSChartPaletteDefaults`, which the `Modern` and `Modern Dark` rules in `defaults.css` override.

### Where each colour comes from

| Role | Palette | Used for |
|---|---|---|
| Categorical | Modern 8-slot head, then a tail up to 40 colours. Modern Dark has its own set. Slots are taken in order. | Anything coloured by a dimension or by series: bar, line, area and point families, pie, donut, treemap, sunburst, circle packing, icicle, radar, marimekko, tree and network groups, gantt status, pareto. |
| Single series | Ships as legacy blue `#518DB9`, not the modern palette. Decided (C11): modern slot 1. | Charts with one series and no colour field. |
| Sequential | Teal, the modern default measure-to-colour ramp (`defaultLinearFrame`). Classic charts get Blues. Amber is the other house sequential ramp. | Maps, contour map, scatter contour, and any chart with a measure on colour. |
| Direction | Dedicated tokens, not palette slots. Light: up `#1C857B`, down `#AD6119`, neutral `#767676`. Dark: up `#3FBFB2`, down `#E8954A`, neutral `#A8A5AD`. Contrast on the card, computed in code against the composited card: light 3.43, 3.57 and 3.48; dark 4.42, 4.19 and 4.11. This replaces the legacy purple waterfall total. | Candle up and down, waterfall increase, decrease and total. Avoids red and green. Up and down have almost the same luminance, so a second cue is needed: waterfall bars are labelled with their change, and candle keeps hollow for up and filled for down. Teal and amber confirmed on 2026-11-03: they avoid the red and green clash between markets and stay distinct under all three colour-blindness types. Contrast on the translucent card, by tint and background, is on the Financial page. |
| Diverging | Variance, a 7-stop ramp. In a modern picker it replaces the nine ColorBrewer diverging ramps. | Above and below a target, when a measure is on colour. |
| Companion | A lighter or darker step of a series colour, same hue, derived by rule, with authored overrides. | Brushed-out marks, target bands. |

### Categorical: first 8 slots

| Slot | Modern (light) | Modern Dark | Classic |
|---|---|---|---|

Contrast is against the card as composited over the modern page (`#ECEBE9` light, `#313034` dark), computed in the page. Slots under 3:1 are fine as large fills with labels but weak as thin lines and points, so line and point charts should not lean on them.

### Ramps

| Ramp | Stops, low to high | Role | Notes |
|---|---|---|---|

## Line specs

Colours are opaque RGB, pre-blended for the card, because borders and chart lines are stored as opaque RGB today (D10 in the spec). Contrast is against the card as composited over the modern page (`#ECEBE9` light, `#313034` dark), computed by hand and approximate. The `.5px` hairline would anti-alias to a blur at 1x, so the width is a full 1px.

| Line | Shipped today | Proposed light | Proposed dark | Width | Style | Alpha | Contrast on card | Notes |
|---|---|---|---|---|---|---|---|---|
| Grid line (plot) | `#E8E5DE` light, `#3A383D` dark. Thin line, horizontal only. | **#D6D2C8 | **#4A484F | 1px | Solid | 100% | 1.3:1 light, 1.45:1 dark | Shipped modern grid is `#E8E5DE` / `#3A383D`, about 1.06:1 and 1.13:1 on the new tint: nearly invisible. This is the main change. |
| Axis line | Same neutral as the grid, line on for every axis. | **#85827A | **#7E7B86 | 1px | Solid | 100% | about 3.2:1 light, 3.1:1 dark | Reaches 3:1 so the baseline reads as an edge. Today the modern axis line borrows the grid colour (`resolveAxisLineColor`), so it needs its own value. |
| Tick marks | Off | Off (no change) | - | - | - | - | Matches what ships: tick marks are off by default in `AxisDescriptor`. |  |
| Radar spokes and rings | Not traced | Same as grid line | 1px | Solid | 100% | as grid | Spokes act as the axes, but rings are grid. |  |
| Legend border | 1px thin line, grid neutral, rounded corners on new charts | None (C8) | - | - | - | - | Decided (C8), not shipped. Today it is a 1px line, coloured with the modern grid neutral (`#E8E5DE` light, `#3A383D` dark), which is almost invisible on the tint. The card is borderless and the legend fill already reads as a panel, so an outline would add a second frame. Keep a border for an in-place legend over the plot, and when the author sets one. The legend fill proposal is in the Legend spacing section, and it differs from the PR #7319 spec. |  |

## Text specs

Sizes, and the label and title colours, match what ships (10px labels, 11px titles, the modern chrome colours). Titles stay plain, which is what ships. Sizes are in px at 1x. Colours for labels and titles are the shipped modern values, which already pass 4.5:1 on the tinted card (about 4.7:1 light, 7.6:1 dark).

| Text | Shipped today | Font | Size | Weight | Light | Dark | Other |
|---|---|---|---|---|---|---|---|
| Axis labels | 10px, plain, modern chrome colour. Modern and legacy both start from the engine text font. | Roboto, no change | 10px | 400 | **#6A685F | **#CAC4D0 | Tabular numerals on value axes. Y labels right-aligned to the axis. Rotate or thin out only when they would overlap. |
| Axis titles | 11px, plain, modern chrome colour. | Roboto, no change | 11px | 400, no change | **#35342F | **#E6E0E9 | Y title rotated 90 degrees, no wrapping. Only drawn where C6 turns titles on. |
| Legend entries | 10px, plain, modern chrome colour. | Roboto, no change | 10px | 400 | **#6A685F | **#CAC4D0 | Spacing is in the Legend spacing table below. |
| Legend title | 10px, plain, the modern title colour. The legend title is not bold. | Roboto, no change | 10px, no change | 400, no change | **#35342F | **#E6E0E9 | Drawn on a side legend (right or left), headed by the field name or Measures, as the family pages show. A top or bottom legend has no title. |
| Labels on shapes | Labels are off unless Show Values is on or a text field is bound, except the treemap family and the tree. Size 10px, plain. Colour is the per-field label ink, `#6A685F` light and `#CAC4D0` dark in modern (legacy `#4B4B4B`), not the plot-level seed. White or near-black per fill exists only for the treemap family, tree and network (black when luminance is above 0.25, so it prefers white). The code compares the label colour to exactly `#4B4B4B` before keeping that rule, so I infer it switches off in modern charts. That is unconfirmed at run time. | Roboto, no change | 10px | 500 | Chosen per fill for 4.5:1 (white or near-black) | Treemap, icicle, circle packing, funnel, tree and network nodes, waterfall values. |  |

Shipped engine defaults for comparison (`GDefaults`): text 10 and title 11 with colours `#4B4B4B` and `#2B2B2B`, small 9, grid and axis line both `#EEEEEE`. The modern chrome swaps in `#6A685F`, `#35342F`, and the grid neutral `#E8E5DE`. Font family: the logical family "Default" resolves to Roboto through the `default.font.family` property. Roboto is not bundled as a font file. The Docker image installs it from the OS package, and I did not trace what happens on a server without it. In a viewsheet, the sizes also move with the global `viewsheet.font.size` property, absolute or plus/minus, so the 10px and 11px above are the starting sizes.

## Legend spacing

These are the values the code seeds today for a modern chart that has no author setting (`VSChartChromeDefaults`). An author's value, including 0, always wins. A chart not marked modern keeps the engine values. I have not changed any of them for the translucent card.

| Setting | Dense | Compact (default) | Comfortable | Not modern | Notes |
|---|---|---|---|---|---|
| Legend gap, legend to plot or axis | 10 | 14 | 18 | 0 | Descriptor value, in px. The distance on screen is 2px more, because the graph layout adds a fixed 2px between the legend and the plot. Applies on the side facing the plot: height for a top or bottom legend, width for left or right. Script property `legendGap`. |
| Legend padding (top, left, bottom, right) | 0, 2, 0, 2 | Engine insets | 1, 6, 2, 6 | Engine insets | Item and title insets in px. Engine insets are 4px left and right on items and the title, none top or bottom. A set padding replaces all four sides. Top and bottom apply per entry. |
| Symbol size | 12px, limited to 6 to 50 | 12px | Engine default. Not seeded by density. The cards above draw 8px, so they understate legend height slightly. |  |  |
| Axis label gap (for comparison) | 6 | 8 | 12 | engine | Axis labels to the plot. Script property `labelGap`. |
| Chart padding, card edge to chart (all four sides) | 8 | 12 | 16 | 10 | Seeded for modern charts by `VSDensityDefaults.chartPaddingForMode`, the same value on every side. The legend has no outer margin of its own, so this is the legend's distance from the card edge, and also the axis title's and the plot's. Read from the code, not checked on a rendered chart. Setting it per side, or giving the legend its own margin, would be new. |
| Axis title gap (for comparison) | 2 | 4 | 6 | engine | Axis title to its labels, or to the plot when the labels are hidden. |

Fit rule: when a fixed or squeezed axis cannot hold the label gap plus the labels, the gap shrinks, down to 2px, so labels stay visible. The legend group adds its gap to its minimum size.

### Legend and plot size: proposed defaults

Today the legend is sized to fit its entries, unless the author sets a proportion or a pixel size. It is capped at 50% of the chart on that axis, floored at its minimum, and the plot gets the rest. These are my proposals, not shipped behaviour, and none of them has been rendered or tested.

| Setting | Today | Proposed | Why |
|---|---|---|---|
| Placement | Right for every type | Top for cartesian charts with 2 to 5 entries, aligned to the plot edge. Right for a colour ramp, part-to-whole charts (pie, donut, the treemap family, funnel, marimekko), network groups and more than 5 entries. | Decided. A top legend costs height, which cards usually have more of. The width-based rule is dropped, because the cap below already protects narrow cards. |
| Cap, left or right legend | 50% of width | 30% of width | Keeps the plot readable on narrow cards. When entries do not fit, truncate with an ellipsis or paginate. |
| Cap, top or bottom legend | 50% of height | 25% of height | Same reason, on the vertical axis. |
| Plot floor with a side legend | None | Dropped | Decided. The 30% cap already leaves the plot about 70% of the width before the gap. |
| Legend size field | Unset, content-sized | Unset, content-sized | Unchanged. Only an explicit author value overrides it. |
| Crowded top legend: 1. fill | Filled band across the full width | No fill (see Legend fill below) | From a screenshot of a scatter chart with two top legends: the filled band reads as a toolbar stacked on the chart and is the heaviest object in it. That chart looks like the legacy defaults, so it may not be modern. |
| Crowded top legend: 2. gap to the plot | Descriptor 10 / 14 / 18 (dense, compact, comfortable), 0 not modern. 2px more on screen. | One step larger for a top or bottom legend: 12 / 16 / 20 | The numbers are my proposal and untried. In the screenshot the band ends about 25px above the first Y label and the plot tops sit right under it. Applies to top and bottom only. |
| Crowded top legend: 3. text weight | Modern ships plain 10px. The first screenshot, which looks legacy, shows a bold title. Probably the bold 11px fallback used when no title format is set, not confirmed. | Entries 400, legend title 400 at 10px. No change. | As in Text specs. The legend should not compete with the axis labels. |
| Crowded top legend: 4. alignment (new behaviour) | No setting. A single legend is centred across the full chart width, several fill the width, and the row starts at the chart's left edge, left of the Y labels. | New legend alignment property: start, centre, end. Modern default for top and bottom legends: start, aligned to the plot's left edge. | Needs a new property on the legend, Modernize and Revert handling, and a decision between the plot edge and the chart edge: I propose the plot edge. With several legends, start means the row begins at the plot edge and the legends sit side by side at their content width, not filling the width. Side legends are unaffected. |
| Legend border | 1px line, modern grid neutral | None | See Line specs. It needs "no border" written at the default tier for modern charts, and Revert must restore the thin line. |
| Legend fill | Modern: white (light) or `#252428` (dark), at alpha 50. Not modern: white at alpha 50. | None. An in-place legend over the plot keeps a fill, in the card's own tone at about 70% (untried). | Proposal, and it reverses the PR #7319 spec's "leave as is". By my hand calculation, not rendered, today's fill lifts the legend to about `#F0F0F0` on a card of about `#E2E1E0` in light, but sinks it to about `#343337` on a card of about `#434246` in dark, so the spec's "lighter panel" holds only in light. With no fill and no border the card stays the only surface. Needs "no fill" written at the default tier for modern charts, and Revert must restore white at alpha 50. An author's fill still wins. |
| Legend corners | Rounded for newly created charts, modern or not (set in `ChartVSAssemblyInfo.setDefaultFormat`). Square for charts loaded from older saved files. | Keep rounded, whenever the legend has a border or a fill | Corrected: an earlier version of this row said square. The engine only has rounded or square, so an exact radius matching the card's 6px would be a new setting. |
| Single series | No legend unless a colour field is bound or there are two or more measures | No legend, no change | Already ships. |
