# Object Hierarchy (StyleBI Chart Script Reference)

Source: https://www.inetsoft.com/docs/stylebi/InetSoftUserDocumentation/1.0.0/chartAPI/ObjectHierarchy.html

> The figure below shows the object structure of the Chart API. Among the significant objects,
> GraphElement contains the elements that graphically represent data (lines, bars, etc.).
> VisualFrame contains information about mapping data dimensions to physical properties (size,
> color, etc.), and Scale contains the scaling information for such mappings. GraphForm contains
> information for manually-drawn chart objects.

This page is a single diagram with no methods of its own — each of the four significant object
families it names has its own dedicated reference file:

| Object family | What it's for | Reference file |
|---|---|---|
| `GraphElement` | The elements that graphically represent data (bars, lines, points, areas, etc.) | `references/chartAPI/ChartElements.md` |
| `VisualFrame` | Maps data dimensions to physical properties — color, shape, size, line, texture, text | `references/chartAPI/ChartAesthetics.md` |
| `Scale` | Scaling information for those mappings — linear, log, categorical, time, etc., plus coordinate systems | `references/chartAPI/ChartCoordinates.md` |
| `GraphForm` | Manually-drawn chart objects — arbitrary text, shapes, and lines | `references/chartAPI/ChartAnnotation.md` |

Other chart-level building blocks (`EGraph`, `dataset`, `AxisSpec`, `TextSpec`, etc.) live in
`references/chartAPI/BasicChartProperties.md`.
