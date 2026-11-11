# Chart plan entries

Each entry is one chart that was placed on a family dashboard, taken from the PLAN arrays of the build scripts in `scripts/`. `table` is the position of the table in the worksheet tree (0 is the first table, tables are listed alphabetically). `shelves` lists the field and the vertical drop position: 78 is the X shelf, 121 the Y shelf, 166 the third shelf (Break By or Geographic). `color`, `size`, `shape` and `geo` name fields dropped on those slots. Some entries are experiments that were rebuilt later, so the family page is the authority on what was kept.

## Area

| Chart title | Table | Style | Shelves | Color | Size | Geographic |
|---|---|---|---|---|---|---|
| Area |  | Area |  |  |  |  |
| Area - 3 series |  | Area |  | Region |  |  |
| Area - 5 series |  | Area |  | Product |  |  |

## Bar

| Chart title | Table | Style | Shelves | Color | Size | Geographic |
|---|---|---|---|---|---|---|
| Bar |  |  |  |  |  |  |
| Bar - few (3) |  |  |  |  |  |  |
| Bar - many (30) |  |  |  |  |  |  |
| Bar - long labels |  |  |  |  |  |  |
| Bar - 2 series |  |  |  | Channel |  |  |
| Bar - 5 series |  |  |  | Segment |  |  |
| Bar - 8 series |  |  |  | Brand8 |  |  |
| Bar - 12 series |  |  |  | Brand12 |  |  |
| Bar - negative values | 1 |  |  |  |  |  |
| Bar - one dominant |  |  |  |  |  |  |
| Bar - zeros |  |  |  |  |  |  |
| Bar - stacked (2) |  | Bar |  | Channel |  |  |
| Bar - stacked (8) |  | Bar |  | Brand8 |  |  |
| Bar - horizontal |  |  | Sales, Product |  |  |  |
| Bar - horizontal (30) |  |  | Sales, Store |  |  |  |
| Bar - horizontal long labels |  |  | Sales, Warehouse |  |  |  |
| Bar - horizontal 2 series |  |  | Sales, Product | Channel |  |  |
| Bar - horizontal stacked |  | Bar | Sales, Product | Brand8 |  |  |
| Bar - small card (300x180) |  |  |  |  |  |  |
| Bar - large card (800x480) |  |  |  |  |  |  |

## Box plot

| Chart title | Table | Style | Shelves | Color | Size | Geographic |
|---|---|---|---|---|---|---|
| Box plot | 1 |  |  |  |  |  |
| Box plot - few (3) | 3 |  |  |  |  |  |
| Box plot - many (20) | 6 |  |  |  |  |  |
| Box plot - by year | 4 |  |  | Year |  |  |
| Box plot - long labels | 5 |  |  |  |  |  |
| Box plot - edge cases | 2 |  |  |  |  |  |
| Box plot - 2 series | 7 |  |  | Year |  |  |
| Box plot - small card (300x180) | 1 |  |  |  |  |  |

## Candle

| Chart title | Table | Style | Shelves | Color | Size | Geographic |
|---|---|---|---|---|---|---|
| Candle - few (8) | 4 |  |  |  |  |  |
| Candle - many (120) | 7 |  |  |  |  |  |
| Candle - 2 tickers | 1 |  |  | Ticker |  |  |
| Candle - 6 tickers | 2 |  |  | Ticker |  |  |
| Candle - long labels | 5 |  |  |  |  |  |
| Candle - edge cases | 3 |  |  |  |  |  |
| Candle - small card (300x180) | 0 |  |  |  | [300, 180] |  |
| Candle - large card (800x480) | 0 |  |  |  | [800, 480] |  |
| Candle - 2 tickers (panels) | 1 |  |  |  |  |  |

## Circle packing

| Chart title | Table | Style | Shelves | Color | Size | Geographic |
|---|---|---|---|---|---|---|
| Circle packing | 0 | Circle Packing | Division, Department |  | Sales |  |
| Circle packing - 3 levels | 0 | Circle Packing | Division, Department, Team |  | Sales |  |
| Circle packing - colour by dimension | 0 | Circle Packing | Division, Department | Division | Sales |  |

## Circular network

| Chart title | Table | Style | Shelves | Color | Size | Geographic |
|---|---|---|---|---|---|---|
| Circular network - 8 nodes | 4 | Circular Network |  |  |  |  |
| Circular network - 30 nodes | 3 | Circular Network |  |  |  |  |
| Circular network - 120 nodes | 2 | Circular Network |  |  |  |  |
| Circular network - colour by cluster | 3 | Circular Network |  |  |  |  |

## Contour map

| Chart title | Table | Style | Shelves | Color | Size | Geographic |
|---|---|---|---|---|---|---|
| Contour map | 0 | Contour Map |  |  |  |  |

## Donut

| Chart title | Table | Style | Shelves | Color | Size | Geographic |
|---|---|---|---|---|---|---|
| Donut | 0 | Donut | Sales | Segment |  |  |
| Donut - 3 slices | 0 | Donut | Sales | Tier |  |  |
| Donut - 8 slices | 0 | Donut | Sales | Group |  |  |
| Donut - 12 slices | 0 | Donut | Sales | Region |  |  |
| Donut - one dominant | 0 | Donut | Skew | Group |  |  |

## Filled radar

| Chart title | Table | Style | Shelves | Color | Size | Geographic |
|---|---|---|---|---|---|---|
| Filled radar - 3 series | 0 | Filled Radar | Speed, Quality, Cost, Safety, Range, Comfort | Entity3 |  |  |
| Filled radar - 5 series | 0 | Filled Radar | Speed, Quality, Cost, Safety, Range, Comfort | Entity5 |  |  |

## Funnel

| Chart title | Table | Style | Shelves | Color | Size | Geographic |
|---|---|---|---|---|---|---|
| Funnel - 5 stages | 0 | Funnel | Users, Stage |  |  |  |
| Funnel - 8 stages | 1 | Funnel | Users, Stage |  |  |  |
| Funnel - colour by stage | 0 | Funnel | Users, Stage | Stage |  |  |
| Funnel - small card (300x180) | 0 | Funnel | Users, Stage |  |  |  |

## Gantt

| Chart title | Table | Style | Shelves | Color | Size | Geographic |
|---|---|---|---|---|---|---|
| Gantt - 8 tasks | 0 | Gantt |  |  |  |  |
| Gantt - 25 tasks | 1 | Gantt |  |  |  |  |
| Gantt - 60 tasks | 2 | Gantt |  |  |  |  |
| Gantt - long task names | 3 | Gantt |  |  |  |  |
| Gantt - colour by project | 1 | Gantt |  | Project |  |  |
| Gantt - with milestones | 1 | Gantt |  |  |  |  |
| Gantt - small card (300x180) | 0 | Gantt |  |  |  |  |
| Gantt - drop test 25 | 1 | Gantt |  |  |  |  |

## Icicle

| Chart title | Table | Style | Shelves | Color | Size | Geographic |
|---|---|---|---|---|---|---|
| Icicle | 0 | Icicle | Division, Department |  | Sales |  |
| Icicle - 3 levels | 0 | Icicle | Division, Department, Team |  | Sales |  |
| Icicle - colour by dimension | 0 | Icicle | Division, Department | Division | Sales |  |
| Icicle - 3 levels, colour by dimension | 0 | Icicle | Division, Department, Team | Division | Sales |  |

## Interval

| Chart title | Table | Style | Shelves | Color | Size | Geographic |
|---|---|---|---|---|---|---|
| Interval | 2 | Interval | Product, Low |  | Range |  |

## Jump line

| Chart title | Table | Style | Shelves | Color | Size | Geographic |
|---|---|---|---|---|---|---|
| Jump line |  | Jump Line |  |  |  |  |

## Line

| Chart title | Table | Style | Shelves | Color | Size | Geographic |
|---|---|---|---|---|---|---|
| Line |  | Line |  |  |  |  |
| Line - few (4) |  | Line |  |  |  |  |
| Line - many (105) |  | Line |  |  |  |  |
| Line - very many (731) |  | Line |  |  |  |  |
| Line - date axis |  | Line |  |  |  |  |
| Line - month names |  | Line |  |  |  |  |
| Line - long labels (105) |  | Line |  |  |  |  |
| Line - 3 series |  | Line |  | Region |  |  |
| Line - 5 series |  | Line |  | Product |  |  |
| Line - 12 series |  | Line |  | Brand12 |  |  |
| Line - 2 measures |  | Line | Period, Sales, Orders |  |  |  |
| Line - negative values |  | Line |  |  |  |  |
| Line - gap |  | Line |  |  |  |  |
| Line - spike |  | Line |  |  |  |  |
| Line - small card (300x180) |  | Line |  |  |  |  |
| Line - large card (800x480) |  | Line |  |  |  |  |

## Map

| Chart title | Table | Style | Shelves | Color | Size | Geographic |
|---|---|---|---|---|---|---|
| Map - states | 3 | Map |  |  |  |  |
| Map - 5 states | 3 | Map | State |  |  | State |
| Map - 20 states | 0 | Map | State |  |  | State |
| Map - 20 states, colour by sales | 0 | Map | State | Sales |  | State |
| Map - 5 states, colour by sales | 3 | Map | State | Sales |  | State |
| Map - small card (300x180) | 3 | Map | State | Sales |  | State |
| Map - world | 1 | Map |  |  |  |  |
| Map - world countries | 1 | Map | Country |  |  | Country |
| Map - world, colour by sales | 1 | Map | Country | Sales |  | Country |
| Map - stores | 2 | Map |  |  |  |  |
| Map - stores as points | 2 | Map | Longitude, Latitude, State |  | Sales | State |
| Map - stores by state | 2 | Map | State |  |  | State |

## Marimekko

| Chart title | Table | Style | Shelves | Color | Size | Geographic |
|---|---|---|---|---|---|---|
| Marimekko - 4x4 | 2 | Marimekko | Region, Sales, Product |  |  |  |
| Marimekko - 8x6 | 3 | Marimekko | Region, Sales, Product |  |  |  |
| Marimekko - small card (300x180) | 2 | Marimekko | Region, Sales, Product |  |  |  |
| Marimekko - 4x4, colour by product | 2 | Marimekko | Region, Sales, Product | Product |  |  |
| Marimekko - 8x6, colour by product | 3 | Marimekko | Region, Sales, Product | Product |  |  |

## Network

| Chart title | Table | Style | Shelves | Color | Size | Geographic |
|---|---|---|---|---|---|---|
| Network | 4 | Network |  |  |  |  |
| Network - 30 nodes | 3 | Network |  |  |  |  |
| Network - 120 nodes | 2 | Network |  |  |  |  |
| Network - colour by cluster | 3 | Network |  |  |  |  |
| Network - edge size by weight | 3 | Network |  |  |  |  |
| Network - edge colour by cluster | 3 | Network |  |  |  |  |
| Network - text labels | 4 | Network |  |  |  |  |
| Network - small card (300x180) | 3 | Network |  |  |  |  |

## Pareto

| Chart title | Table | Style | Shelves | Color | Size | Geographic |
|---|---|---|---|---|---|---|
| Pareto - 8 defects | 5 | Pareto | Defect, Count |  |  |  |
| Pareto - 20 defects | 4 | Pareto | Defect, Count |  |  |  |
| Pareto - small card (300x180) | 5 | Pareto | Defect, Count |  |  |  |
| Pareto - one dominant defect | 6 | Pareto | Defect, Count |  |  |  |

## Pie

| Chart title | Table | Style | Shelves | Color | Size | Geographic |
|---|---|---|---|---|---|---|
| Pie | 0 | Pie | Sales | Segment |  |  |
| Pie - 2 slices | 0 | Pie | Sales | Channel |  |  |
| Pie - 3 slices | 0 | Pie | Sales | Tier |  |  |
| Pie - 8 slices | 0 | Pie | Sales | Group |  |  |
| Pie - 12 slices | 0 | Pie | Sales | Region |  |  |
| Pie - 20 slices | 0 | Pie | Sales | Store |  |  |
| Pie - long labels | 0 | Pie | Sales | Warehouse |  |  |
| Pie - one dominant | 0 | Pie | Skew | Group |  |  |
| Pie - many tiny | 0 | Pie | Tiny | Store |  |  |
| Pie - negative values | 0 | Pie | Delta | Group |  |  |
| Pie - zeros | 0 | Pie | Sparse | Group |  |  |
| Pie - text labels | 0 | Pie | Sales | Segment |  |  |
| Pie - small card (300x180) | 0 | Pie | Sales | Segment |  |  |
| Pie - large card (800x480) | 0 | Pie | Sales | Segment |  |  |

## Point

| Chart title | Table | Style | Shelves | Color | Size | Geographic |
|---|---|---|---|---|---|---|
| Point | 3 | Point | Price, Units, Item |  |  |  |
| Point - few (10) | 0 | Point | Price, Units, Item |  |  |  |
| Point - many (500) | 2 | Point | Price, Units, Item |  |  |  |
| Point - very many (2000) | 1 | Point | Price, Units, Item |  |  |  |
| Point - 3 groups | 3 | Point | Price, Units, Item | Seg3 |  |  |
| Point - 5 groups | 3 | Point | Price, Units, Item | Seg5 |  |  |
| Point - 8 groups | 3 | Point | Price, Units, Item | Seg8 |  |  |
| Point - color by measure | 3 | Point | Price, Units, Item | Margin |  |  |
| Point - size by measure | 3 | Point | Price, Units, Item |  | Revenue |  |
| Point - size and color | 3 | Point | Price, Units, Item | Seg3 | Revenue |  |
| Point - shape by group | 3 | Point | Price, Units, Item |  |  |  |
| Point - clusters | 2 | Point | X2, Y2, Item | Seg3 |  |  |
| Point - tight correlation | 3 | Point | TightX, TightY, Item |  |  |  |
| Point - outliers | 3 | Point | OutX, OutY, Item |  |  |  |
| Point - negative values | 3 | Point | Delta1, Delta2, Item |  |  |  |
| Point - category on X | 3 | Point | Seg8, Price, Item |  |  |  |
| Point - small card (300x180) | 3 | Point | Price, Units, Item |  |  |  |
| Point - large card (800x480) | 3 | Point | Price, Units, Item |  |  |  |

## Radar

| Chart title | Table | Style | Shelves | Color | Size | Geographic |
|---|---|---|---|---|---|---|
| Radar | 0 | Radar | Speed, Quality, Cost, Safety, Range, Comfort |  |  |  |
| Radar - 3 spokes | 0 | Radar | Speed, Quality, Cost |  |  |  |
| Radar - 5 spokes | 0 | Radar | Speed, Quality, Cost, Safety, Range |  |  |  |
| Radar - 8 spokes | 0 | Radar | Speed, Quality, Cost, Safety, Range, Comfort, Design, Reliability |  |  |  |
| Radar - 12 spokes | 0 | Radar | Speed, Quality, Cost, Safety, Range, Comfort, Design, Reliability, Support, Warranty, Efficiency, Brand |  |  |  |
| Radar - 2 series | 0 | Radar | Speed, Quality, Cost, Safety, Range, Comfort | Entity2 |  |  |
| Radar - 3 series | 0 | Radar | Speed, Quality, Cost, Safety, Range, Comfort | Entity3 |  |  |
| Radar - 5 series | 0 | Radar | Speed, Quality, Cost, Safety, Range, Comfort | Entity5 |  |  |
| Radar - 8 series | 0 | Radar | Speed, Quality, Cost, Safety, Range, Comfort | Entity8 |  |  |
| Radar - scale mismatch | 0 | Radar | Speed, Quality, Revenue, Cost, Safety, Range |  |  |  |
| Radar - negative values | 0 | Radar | Delta Q1, Delta Q2, Delta Q3 |  |  |  |
| Radar - long names | 0 | Radar | Customer satisfaction score, Operational efficiency rating, Employee engagement level, Supply chain reliability index |  |  |  |
| Radar - small card (300x180) | 0 | Radar | Speed, Quality, Cost, Safety, Range, Comfort |  |  |  |
| Radar - large card (800x480) | 0 | Radar | Speed, Quality, Cost, Safety, Range, Comfort |  |  |  |

## Scatter contour

| Chart title | Table | Style | Shelves | Color | Size | Geographic |
|---|---|---|---|---|---|---|
| Scatter contour | 2 | Scatter Contour | Price, Units, Item |  |  |  |
| Scatter contour - clusters | 2 | Scatter Contour | X2, Y2, Item |  |  |  |
| Scatter contour - many (2000) | 1 | Scatter Contour | Price, Units, Item |  |  |  |

## Step area

| Chart title | Table | Style | Shelves | Color | Size | Geographic |
|---|---|---|---|---|---|---|
| Step area |  | Step Area |  |  |  |  |

## Step line

| Chart title | Table | Style | Shelves | Color | Size | Geographic |
|---|---|---|---|---|---|---|
| Step line |  | Step Line |  |  |  |  |

## Stock

| Chart title | Table | Style | Shelves | Color | Size | Geographic |
|---|---|---|---|---|---|---|
| Stock | 0 |  |  |  |  |  |
| Stock - few (8) | 4 |  |  |  |  |  |
| Stock - many (120) | 7 |  |  |  |  |  |
| Stock - 2 tickers | 1 |  |  | Ticker |  |  |
| Stock - long labels | 5 |  |  |  |  |  |
| Stock - small card (300x180) | 0 |  |  |  |  |  |

## Sunburst

| Chart title | Table | Style | Shelves | Color | Size | Geographic |
|---|---|---|---|---|---|---|
| Sunburst | 0 | Sunburst | Division, Department |  | Sales |  |
| Sunburst - 3 levels | 0 | Sunburst | Division, Department, Team |  | Sales |  |
| Sunburst - color by dimension | 0 | Sunburst | Division, Department | Division | Sales |  |

## Tree

| Chart title | Table | Style | Shelves | Color | Size | Geographic |
|---|---|---|---|---|---|---|
| Tree | 0 | Tree |  |  |  |  |
| Tree - 60 nodes | 6 | Tree |  |  |  |  |
| Tree - 200 nodes | 5 | Tree |  |  |  |  |
| Tree - long names | 1 | Tree |  |  |  |  |
| Tree - colour by branch | 6 | Tree |  |  |  |  |
| Tree - node size by salary | 6 | Tree |  |  |  |  |

## Treemap

| Chart title | Table | Style | Shelves | Color | Size | Geographic |
|---|---|---|---|---|---|---|
| Treemap | 0 | Treemap | Division, Department |  | Sales |  |
| Treemap - 1 level | 0 | Treemap | Department |  | Sales |  |
| Treemap - 3 levels | 0 | Treemap | Division, Department, Team |  | Sales |  |
| Treemap - 4 levels | 0 | Treemap | Division, Department, Team, Product |  | Sales |  |
| Treemap - color by dimension | 0 | Treemap | Division, Department | Division | Sales |  |
| Treemap - color by measure | 0 | Treemap | Division, Department | Margin | Sales |  |
| Treemap - long labels | 0 | Treemap | Division, LongDepartment |  | Sales |  |
| Treemap - one dominant | 0 | Treemap | Division, Team |  | Skew |  |
| Treemap - many tiny | 0 | Treemap | Team |  | Tiny |  |
| Treemap - small card (300x180) | 0 | Treemap | Division, Department |  | Sales |  |
| Treemap - large card (800x480) | 0 | Treemap | Division, Department |  | Sales |  |

## Waterfall

| Chart title | Table | Style | Shelves | Color | Size | Geographic |
|---|---|---|---|---|---|---|
| Waterfall - 8 items | 7 | Waterfall | Item, Amount |  |  |  |
| Waterfall - 20 items | 6 | Waterfall | Item, Amount |  |  |  |
| Waterfall - small card (300x180) | 7 | Waterfall | Item, Amount |  |  |  |

