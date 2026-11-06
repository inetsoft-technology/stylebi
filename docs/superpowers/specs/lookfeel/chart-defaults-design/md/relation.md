<!-- Markdown version of relation/relation.html. The drawn "proposed look" cards are in the HTML page. -->

# Tree, network and circular network

Family page. It lists only what differs from the [common rules](common.md) (C1 to C24). Other family pages: [bar and interval](bar.md), [line and area](line.md), [point and contour](point.md), [pie and donut](pie.md), [radar](radar.md), [hierarchy](hierarchy.md), [Gantt](gantt.md), [funnel, waterfall, pareto and marimekko](funnel-waterfall.md), [geo](geo.md) and [financial](financial.md). Facts marked *observed* come from live charts on a server dashboard with modern chrome, every chart style and format at its default, and made-up data. The translucent card is not in the build yet. All captures are from the composer design view. Facts marked *traced* come from reading the code. All decisions on this page were confirmed on 2026-11-04.

## Status

| Type | Status | What exists |
|---|---|---|
| Tree | Written up | Six charts: 15, 60 and 200 nodes, long names, colour by branch and node size by salary. |
| Network | Written up | Seven charts: 8, 30 and 120 nodes, colour by cluster, edge colour, edge size and text labels. |
| Circular network | Written up | Four charts: 8, 30 and 120 nodes, and colour by cluster. |
| Small card | Captured | One network at 300×180. |

## Decisions

- **Follows the common rules.** No title line (C2), plot padding by density (C19), the legend on the right (C7 to C10), the label rule (C5), palette colour 1 for nodes (C11), label colour (C21) and size legend symbols (C20). These charts already have no axes, no axis titles and no gridlines, so C3, C4 and C6 do not apply.
- **The tree opens at its root and fits the card as far as it can.** This is the biggest finding. The tree is drawn at full size in a chart area that scrolls, and a tree with more than about 15 nodes is much larger than the card. The first view does not start at the root. For 60 and 200 nodes it lands on blank space, and the card looks empty until the reader scrolls. At 15 nodes the right branch runs off the edge. The cause, traced: the tree uses a compact tree layout with fixed 40×30 boxes and a 12px gap, and its positions are not scaled to the card (`RelationElement`, COMPACT_TREE, scalePositions false). Two changes. First, the initial scroll position shows the root at the top, centred. Second, the tree scales down to fit the card width until the boxes reach a minimum size, and scrolls only beyond that. Preview shows the same as the design view. New behaviour. Confirmed on 2026-11-04.
- **Tree boxes use a light fill with an outline, and small corners.** Shipped draws each box in a solid blue with grey text, and the corner is about 30% of the short side, about 9px on a 30px box, so the boxes look like pills. The new box is a tint of the palette colour at about 15% with a 1px outline in the full colour, and the text in the dark chrome ink. That lowers the visual weight, gives the label 4.5:1 or better on every fill, and keeps the colour that a colour field gives. The corner follows the bar decision, a radius of about 0.08 of the short side, 2 to 3px. Network and circular nodes are small markers and stay solid. New behaviour. Confirmed on 2026-11-04.
- **The network keeps the density padding around the graph.** At 300×180 the network layout fills the whole plot and its nodes touch the edges, because the organic layout is sized to the layout area with only a small gap of at most 20px or 5%. The graph stays inside the density padding (C19) at every card size. New behaviour. Confirmed on 2026-11-04.
- **Edges are a chrome neutral and nodes take palette colour 1.** Shipped draws nodes and edges in the same legacy blue, so the lines compete with the nodes. Edges in the axis line colour (C4) let the nodes lead. With a colour field, nodes take the palette and edges stay neutral. Edge colour by a field still works as shipped. New behaviour. Confirmed on 2026-11-04.
- **Edge thickness is capped at about 6px.** Shipped scales edges by weight up to about 35px, which buries the nodes. The same rule as the bubble cap on the point chart. Confirmed on 2026-11-04.
- **The circular network stays inside the plot.** At 30 and 120 nodes and with a colour legend, the circle is cut off at the top or the bottom, in Preview too. Traced: its radius is 90% of the smaller layout side minus the node size, so the layout size is larger than the visible plot. Keep the circle within the plot with at least the density padding (C19), and at most 80% of the plot height, as for the pie. New behaviour. Confirmed on 2026-11-04.
- **Node labels follow the common label rule and the hierarchy label fix.** The boxes show "Person.." in grey text on blue. White or near-black per fill (C21), and truncation at about 12 characters with an ellipsis (C5). This is common build item 6. Confirmed on 2026-11-04.
- **Size legend symbols use a chrome neutral (C20).** The edge size and node size legends show grey-teal symbols, observed on this page. Confirmed on 2026-11-04.
- **No default change for density.** A network with 120 nodes and a circular network with 120 nodes are a mass of lines. Text labels overlap on a small graph. These are known limits.

## Proposed look

Drawn to the decisions at the default card size, with invented values. Edges in a neutral, nodes in palette colour 1, a tree that fits the card, a circle inside the plot, and a titled legend on the right.

### Light

*The drawn cards that show the proposed look are in the HTML page.*

### Dark

## Shipped today against the decisions

| Setting | Shipped today | Proposed |
|---|---|---|
| Tree size and first view | Full size in a scrolling area. More than about 15 nodes run outside the card, and the first view of 60 or 200 nodes shows blank space until the reader scrolls. *Observed.* | Opens at the root, scales to the card width down to a minimum box size, then scrolls. |
| Node colour, no colour field | The legacy blue. *Observed.* | Palette colour 1 (C11). |
| Edge colour, no colour field | The same legacy blue as the nodes. *Observed.* | A chrome neutral. |
| Edge thickness by a measure | Up to about 35px. *Observed.* | At most about 6px. |
| Circular network size | The circle is cut off at the top or bottom from 30 nodes. *Observed.* | Inside the plot, at most 80% of its height. |
| Tree node labels | Grey text on blue, cut to "Person..". *Observed.* | Dark ink on a light tint, cut at about 12 characters. |
| Tree box fill and corners | Solid blue, corner about 30% of the short side, about 9px on a 30px box. *Observed and traced.* | A 15% tint with a 1px outline, corner about 0.08 (2 to 3px). |
| Size legend symbols | Grey-teal `#759595`. *Observed and traced.* | Chrome neutral (C20). |
| Title line | A line under the card title. *Observed.* | Removed (C2). |
| Axes, axis titles, grid | None. *Observed.* | Unchanged. |

## Variants

Captured from live charts on a server dashboard, with every chart style and format at its default. The data is made up: reporting trees of 15, 60 and 200 people with salaries and branches, trees with long job titles, and networks of 8, 30 and 120 nodes with weights and clusters. Only the bound fields differ between variants.

| Live capture | Variant | What it shows | What it means for the defaults |
|---|---|---|---|
| ![Tree, 15 nodes (canonical)](../relation/img/tree.png) | Tree, 15 nodes (canonical) | Rounded boxes in the legacy blue with names cut to "Person.." in grey text. Connector lines are thin blue. The tree is wider than the card: the right branch runs off the edge and the root sits low, so only part of it shows without scrolling. There is a line under the card title. | C2, one palette colour, label contrast, open at the root, fit the tree to the card. |
| ![Tree, 60 nodes](../relation/img/tree_60_nodes.png) | Tree, 60 nodes | Almost nothing is visible at first. The tree is drawn at full size in a scrolling area, and the first view lands on blank space with only a corner of a box. The reader has to scroll to find it. | Open at the root. Fit the tree to the card. |
| ![Tree, 200 nodes](../relation/img/tree_200_nodes.png) | Tree, 200 nodes | The card looks empty at first. The tree is far larger than the card and has to be scrolled to. | Open at the root. Fit the tree to the card. |
| ![Tree, long names](../relation/img/tree_long_names.png) | Tree, long names | Three boxes with "Senior.." are visible. The rest of the tree is off the card and has to be scrolled to. | Open at the root, fit, and C5 label truncation. |
| ![Tree, colour by branch](../relation/img/tree_colour_by_branch.png) | Tree, colour by branch | A legend of Person 001 and Person 002 shows, and the tree is not in the first view. It is on the scrolling area, as for the same 60 nodes without a colour field. | Open at the root. Fit the tree to the card. |
| ![Tree, node size by salary](../relation/img/tree_node_size_by_salary.png) | Tree, node size by salary | A size legend with grey-teal symbols shows, and the tree is not in the first view. It is on the scrolling area. | Open at the root. Fit the tree. C20 for the legend symbols. |
| ![Network, 8 nodes (canonical)](../relation/img/network.png) | Network, 8 nodes (canonical) | Eight small square nodes, about 5px, in the legacy blue, joined by thin blue lines. The graph fills about a third of the plot. No labels. | One palette colour, neutral edges. |
| ![Network, 30 nodes](../relation/img/network_30_nodes.png) | Network, 30 nodes | Reads well. The nodes are small and the edges are thin. | Same. |
| ![Network, 120 nodes](../relation/img/network_120_nodes.png) | Network, 120 nodes | A dense mass of lines and squares. Individual connections cannot be followed. | Known limit. |
| ![Network, node colour by cluster](../relation/img/network_colour_by_cluster.png) | Network, node colour by cluster | Nodes in the modern palette with a titled Cluster legend. Clusters are mixed in space, so the layout does not group them. | No change. |
| ![Network, edge colour by cluster](../relation/img/network_edge_colour_by_cluster.png) | Network, edge colour by cluster | Edges in blue, orange and navy. Readable, with a Cluster legend. | No change. |
| ![Network, edge size by weight](../relation/img/network_edge_size_by_weight.png) | Network, edge size by weight | Edges up to about 35px thick pile up into one mass, and the nodes disappear. A "Sum(Weight)" size legend has grey-teal symbols. | Cap the edge thickness. C20. |
| ![Network, text labels](../relation/img/network_text_labels.png) | Network, text labels | Names overlap each other ("Node 001Node 001") because the graph is small. | Known limit. Fit the graph to the plot. |
| ![Network, colour by cluster, large card](../relation/img/network_color_by_cluster_large_card.png) | Network, colour by cluster, large card | In a card of about 910 by 1080 px the 30-node graph fills about 250 by 200 px in the middle. The rest of the plot is empty, so a larger card gains nothing. Reported from the portal view. | Fit the graph to the plot (build item 7). |
| ![Network, small card (300×180)](../relation/img/network_small_card_300x180_.png) | Network, small card (300×180) | The graph is laid out again for the smaller plot and fills almost all of it: nodes sit at the left and bottom edges with no padding, where at 400×240 the same graph took about 40% of the plot. | Keep the density padding (C19) around the graph. |
| ![Circular network, 8 nodes](../relation/img/circular_network_8_nodes.png) | Circular network, 8 nodes | Nodes on a dashed circle with curved edges. The circle fits the card. | Padding. |
| ![Circular network, 30 nodes](../relation/img/circular_network_30_nodes.png) | Circular network, 30 nodes | The circle is cut off at the bottom of the card. | Keep the circle inside the plot. |
| ![Circular network, 120 nodes](../relation/img/circular_network_120_nodes.png) | Circular network, 120 nodes | The circle is wider than the card and is cut off at the top and the bottom. The edges form a solid mass. | Keep the circle inside the plot. Known limit for the density. |
| ![Circular network, colour by cluster](../relation/img/circular_network_colour_by_cluster.png) | Circular network, colour by cluster | Nodes in the palette with a legend. The circle is cut off at the bottom. | Keep the circle inside the plot. |

## Build items

| # | Item | Cost | Where | Notes |
|---|---|---|---|---|
| 1 | Open the tree at its root and scale it to the card width | New behaviour | The tree coordinate and its layout size in `GraphGenerator`. Not traced. | Traced to `RelationElement` COMPACT_TREE with scalePositions false. The initial scroll offset is also a client matter. Scale to the card width with a minimum box size, then scroll. |
| 2 | Nodes in palette colour 1, edges in a chrome neutral | New behaviour | The relation colour frames in `GraphGenerator`. Not traced. | Part of C11. Old saved charts keep their blue. |
| 3 | Cap the edge thickness at about 6px | Seed only | The edge size frame for a relation chart. Not traced. | The linear size frame defaults to a largest size of 30, traced. |
| 4 | Keep the circular network inside the plot | New behaviour | `RelationElement` CIRCLE layout, where the radius is 90% of the smaller layout side. Traced. | Same rule as pie: density padding and at most 80% of the plot height. |
| 5 | White or near-black label text under modern | Fix | Common build item 6: `GraphGenerator` label colour tests. | Shared with hierarchy. For the tree the light tint replaces the need, because the ink is always dark. |
| 5c | Keep the density padding around a network graph | New behaviour | `RelationElement` ORGANIC layout, where the layout rectangle is the layout size minus a gap of at most 20px or 5%. Traced. | Use the density padding as the gap. Applies at every card size. |
| 5b | Tree box tint with outline and a corner of about 0.08 | New behaviour, plus a seed | `RelationVO` node paint and `resolveNodeShape`, and the node corner radius seed. The corner is `arc = radius × short side × 2` with radius at most 0.5. Not traced further. | Check that the tree takes the same seeded radius as bars. A fill alpha and a stroke on the node need a new setting. |
| 6 | Size legend symbols in a chrome neutral (C20) | Seed only | `LegendItem.SYMBOL_COLOR` | Traced. |
| 7 | Spread a network over the whole plot when the card is enlarged | Needs a fix | `RelationElement.mxLayout` ORGANIC branch, which lays out inside layoutSize minus a gap of at most 20px. layoutSize comes from the graph size set in `GraphGenerator`. Partly traced: the layout is bounded by that size, but I did not find why the result stays small. | Observed on a 30-node network in a tall card of about 910 by 1080 px: the graph fills roughly 250 by 200 px in the middle and the rest is empty. Enlarging the card should give the graph more room. Rule: fit the drawing to the plot area, keep its proportions, leave the C19 padding, and never enlarge the nodes themselves. |
| 8 | Common items | Common | See the common page | C2, C5, C7, C9 and C19 apply without extra work here. |

## Open items

- None. The portal viewer was checked on 2026-11-04 and matches these captures, as Preview does.

## Known limits

Settled: no default change.

- Networks and circular networks with about 120 nodes are a mass of lines.
- Node labels overlap on a small graph.
- The network layout does not group clusters together.
