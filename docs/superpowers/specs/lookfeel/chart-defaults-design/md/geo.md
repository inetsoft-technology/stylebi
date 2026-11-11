<!-- Markdown version of geo/geo.html. The drawn "proposed look" cards are in the HTML page. -->

# Geo: map

Family page. It lists only what differs from the [common rules](common.md) (C1 to C24). Other family pages: [bar and interval](bar.md), [line and area](line.md), [point and contour](point.md), [pie and donut](pie.md), [radar](radar.md), [hierarchy](hierarchy.md), [relation](relation.md), [Gantt](gantt.md), [funnel, waterfall, pareto and marimekko](funnel-waterfall.md) and [financial](financial.md). Facts marked *observed* come from live charts on a server dashboard with modern chrome, every chart style and format at its default, and made-up data. The translucent card is not in the build yet. All captures are from the composer design view and were not yet compared with Preview or the portal viewer. Facts marked *traced* come from reading the code. All decisions on this page were confirmed on 2026-11-04.

## Status

| Type | Status | What exists |
|---|---|---|
| Map | Written up | Eight charts: a U.S. map with 5 and 20 states, each with and without a colour measure, a world map with and without colour, stores as points, and a small card. |

## Decisions

- **Follows the common rules.** No title line (C2), the legend on the right (C7 to C10), palette colour (C11), the label rule (C5), the density padding (C19), the bubble cap (C17), size legend symbols (C20) and colour ramps (C23). A map has no axes and no grid, so C3, C4 and C6 do not apply.
- **A highlighted region with no colour field is a tint of palette colour 1.** Shipped fills highlighted regions in a pale orange that is not in the palette, and with 20 states it merges into one mass. The new fill is palette colour 1 at about 30%, with the region border kept in the card colour. This keeps the points on the points map visible over the states under them. The unmatched regions keep a neutral fill, the grid colour of C3. New behaviour (C11).
- **The colour ramp for a measure is kept, with a floor on the light end.** Shipped draws a muted teal gradient from about #ACCECA (lightest) to about #355D59 (darkest), measured from the live chart. It reads well, but the lowest value is almost the grey of the unmatched regions, so a state with data looks like a state without data. Start the ramp at about 25% of its range instead of 0, so the lightest data colour is always visibly different from the unmatched fill. The gradient legend keeps its title and end values. Seed only, on the ramp's low stop. A measure that crosses zero uses the diverging ramp, as on the point page (C23).
- **The gradient legend has no border and no fill (C8) and uses the legend size rules (C9).** Shipped draws a rounded border and a white fill. At 300×180 the legend takes about 30% of the card width, so the cap already keeps the map the larger part of a small card, and the legend stays on the right for a colour ramp (C7).
- **Points on a map follow the point chart.** Size is capped as on the point chart, so 35px circles do not hide the states, and the fill is palette colour 1 (C11). The size legend symbols use the chrome neutral of C20. Shipped draws the symbols hollow at the small sizes.
- **Maps do not get a different default for small regions.** Small countries and islands are a few pixels at the default size. This is a known limit of a world map in a card.

## Proposed look

Drawn to the decisions at the default card size, with invented values. A highlighted-states map, a coloured map with a legend, and a points map with capped circles. The map shapes are the real state outlines from the chart, and the ramp uses the shipped teal, with the lightest value at 25% of its range.

### Light

*The drawn cards that show the proposed look are in the HTML page.*

### Dark

## Shipped today against the decisions

| Setting | Shipped today | Proposed |
|---|---|---|
| Highlighted region, no colour field | A pale orange that is not in the palette. *Observed.* | Palette colour 1 at about 30% (C11). |
| Colour ramp low end | The lightest data colour is close to the unmatched grey. *Observed.* | Starts at about 25% of the range. |
| Gradient legend box | A border and a white fill. *Observed.* | None (C8). |
| Gradient legend at 300×180 | About 30% of the width. *Observed.* | The map stays the larger part (C9). |
| Point size on a map | About 35px circles that hide the states. *Observed.* | Capped as on the point chart. |
| Size legend symbols | Blue, hollow at the small sizes. *Observed.* | Chrome neutral (C20). |
| Title line | A line under the card title. *Observed.* | Removed (C2). |
| Axes and grid | None. *Observed.* | Unchanged. |

## Variants

Captured from live charts on a server dashboard, with every chart style and format at its default. The data is made up: sales for 5 and 20 U.S. states, sales for 12 countries, and 280 stores in six states with a latitude, a longitude and a sales value. Only the bound fields and the amount of data differ between variants.

| Live capture | Variant | What it shows | What it means for the defaults |
|---|---|---|---|
| ![Map, 5 states, no colour field (canonical)](../geo/img/map_5_states.png) | Map, 5 states, no colour field (canonical) | A U.S. map with the five states in the data (California, Texas, Florida, Illinois, New York) in a pale orange, and the other states in a warm grey with thin white borders. Alaska and Hawaii sit as insets at the bottom left. There is no legend and no axes, and a line under the card title. The pale orange is not a palette colour. | C11 colour, C2 title line. |
| ![Map, 20 states, no colour field](../geo/img/map_20_states.png) | Map, 20 states, no colour field | The same, with 20 states highlighted. The highlighted states merge into regions, and the borders between them nearly vanish in the pale orange. | Same. |
| ![Map, 5 states, colour by sales](../geo/img/map_5_states_colour_by_sales.png) | Map, 5 states, colour by sales | A teal gradient from light to dark, with a gradient legend titled Sum(Sales) with the end values 100 and 700 only. The legend has a border and a fill. The lightest state (Florida) is faint against the grey of the other states. | C8 legend border, a floor on the light end of the ramp. |
| ![Map, 20 states, colour by sales](../geo/img/map_20_states_colour_by_sales.png) | Map, 20 states, colour by sales | Readable. The ramp runs from 0 to 1K. Several mid-value states are close in shade, so exact values need a tooltip. | No change. The same floor on the ramp. |
| ![Map, world, no colour field](../geo/img/map_world_countries.png) | Map, world, no colour field | A world map with 12 countries highlighted in the same pale orange. Small countries and islands are a few pixels, and the legend is absent. | C11 colour. |
| ![Map, world, colour by sales](../geo/img/map_world_colour_by_sales.png) | Map, world, colour by sales | The same teal ramp with a legend from 0 to 1K. Countries without data stay grey. Small countries (such as the ones in Europe) are tiny and hard to see. | No change. |
| ![Map, stores as points, size by sales](../geo/img/map_stores_as_points.png) | Map, stores as points, size by sales | Longitude on X, latitude on Y, the states as the geographic field, and the size by sales. The 280 stores fall in six clusters, so the points are drawn on top of each other as large blue circles about 35px wide that hide the states. The states under them are the pale orange. The Sum(Sales) size legend shows blue circles with a hollow white look at the small sizes. | Cap the point size as on the point chart, C20 legend symbols, C11 colour. |
| ![Map, small card (300×180)](../geo/img/map_small_card_300x180.png) | Map, small card (300×180) | The gradient legend takes about 30% of the card width, so the map shrinks to about 150px wide and the state shapes are small. | C9 legend size. Keep the map the larger part of the card. |

## Authoring notes

Not defaults. How the maps were bound, in case a reader wants to reproduce them.

- **A text column must be marked geographic first.** The geographic shelf (G) does not accept a plain State or Country column. Right-click the column in the chart's field tree, choose Set Geographic, and pick the map (U.S. or World), the layer (State or Country) and the mapping (Built-in). Only then can it be dropped on G. The dialog opens on U.S., State, Built-in for the State column and World Map, Country, Built-in for the Country column. This is done in each chart. *Observed.*
- Dropping a geographic column on G also fills the X and Y shelves with Longitude and Latitude of that column. *Observed.* For points, the store latitude and longitude columns go on X and Y in place of those, with the state on G. *Observed.*
- State names are matched by the full name (New York, not NY). *Observed with the data used here.*

## Build items

| # | Item | Cost | Where | Notes |
|---|---|---|---|---|
| 1 | Highlighted regions in palette colour 1 at about 30%, unmatched in the grid colour | New behaviour | The map element fill in `GraphGenerator`. Not traced. | Part of C11. Old saved charts keep the pale orange. |
| 2 | Start the colour ramp at about 25% of its range | Seed only | The map colour frame default. Not traced. |  |
| 3 | No border and no fill on the gradient legend | Seed only | Common legend rule C8. | Check that the gradient legend follows the same setting as the item legend. |
| 4 | Cap the point size on a map | Seed only | The size frame default for a map point element. Not traced. | Same as the point chart. |
| 5 | Size legend symbols in a chrome neutral (C20) | Seed only | `LegendItem.SYMBOL_COLOR` | Traced. |
| 6 | Common items | Common | See the common page | C2, C7 to C10 and C19 apply without extra work here. |

## Open items

- Preview and the portal viewer have not been compared with these design-view captures.

## Known limits

- Only U.S. states and world countries were tried. Counties and cities were not, and the defaults on this page do not depend on the layer.
- Small countries and islands are a few pixels at the default card size.
- Several mid-value regions in a ramp are close in shade, so exact values need the tooltip.
