# Chart Script Tutorial

Source: https://www.inetsoft.com/docs/stylebi/InetSoftUserDocumentation/1.0.0/chartAPI/ChartScriptTutorial.html

Task-oriented, worked examples for the Chart Script API. Use this when the ask is "how do I
accomplish X" rather than "what does method Y take" (for the latter, see the other files under
`references/chartAPI/`).

## Why Use Chart Script?

In most cases, create Charts using the Chart Editor (see Create a Chart) — easy to use, rich
feature set. Use Chart Script when the Chart Editor cannot produce the desired chart: typically
special-purpose or industry-specific charts with non-standard axis layouts or unusual mixtures of
text and graphics. The object model is illustrated in `ObjectHierarchy.md`. Two ways to use it:

- **Existing Chart**: get a handle to objects on a Chart already built with the Chart Editor, and
  modify those objects via script. Easier, recommended, needs only basic JavaScript.
- **New Chart**: create a Chart from scratch using Chart API commands. More code, more
  flexibility, recommended only for users with programming skills.

Chart API script that operates on the Chart's `EGraph` property (the `graph` variable) belongs in
**element-level (component) script**, not dashboard-level script. Scripted charts are not good
candidates for user modification — deselect "Enable Ad Hoc Editing" in Chart Properties (Advanced
tab) for script-based charts.

> The official tutorial index also lists "Represent Multiple Measure" and "Add Chart Decoration" as
> topics, but neither has its own page in this doc version's left nav (only the 12 pages below do)
> — likely folded into other pages or removed. `Access Chart Data`'s labeling example below is the
> closest match to "Add Chart Decoration" content that does exist.

---

## Modify Chart Properties

For basic Chart element properties, set the desired value in the `onInit` handler, `onRefresh`
handler, or component script. Use auto-completion to find legitimate property names/values.

> **Anomaly**: the doc site's `ModifyChartDataBinding.html` page currently serves the same content
> as `ModifyChartProperties.html` (title tag included) — likely a stale/duplicate page on
> InetSoft's site, not a plugin issue. The distinct "Modify Chart Data Binding" content that
> actually exists lives under `Bind Data to Chart in Script` below.

### Modify Chart Style

Use the `chartStyle` attribute. Multi-style chart: qualify with the dataset name, e.g.
`Chart1.chartStyle['Sum(Total)']`. Single-style chart: `Chart1.chartStyle` with no modifier.
Switch between multi-style and single-style in the "Select Chart Style" panel.

```js
// Change style based on a parameter
if (parameter['Chart Style'] == 'bar') {
  Chart1.chartStyle = Chart.CHART_BAR;
}
else if (parameter['Chart Style'] == 'line') {
  Chart1.chartStyle = Chart.CHART_LINE;
}
```

### Modify Axis Title Text

Use `xTitle.text` (`x2Title.text`) and `yTitle.text` (`y2Title.text`).

```js
Chart1.xTitle.text = 'Text to go below bottom X-axis';
Chart1.x2Title.text = 'Text to go above top X-axis';
```

### Modify Axis Properties

Use axis properties: `axis.font`, `axis.minimum`, `axis.labelColor`, `axis.format`, etc. Type `.`
after `axis` in the editor for the property prompt.

```js
Chart1.axis.Employee.font = 'Comic Sans MS-BOLD-12';
Chart1.axis.Employee.ticksVisible = false;
Chart1.axis.Employee.labelColor = [0,0,255];
Chart1.axis['Sum(Total)'].logarithmic = true;
Chart1.axis['Sum(Total)'].minimum = 10000;
Chart1.axis['Sum(Total)'].format = [Chart.DECIMAL_FORMAT, "#,###.00"];
```

---

## Modify Chart Element

Direct access to charting-engine commands via Java object syntax, to modify the graphical elements
a Chart displays. To build a Chart entirely via script instead of modifying an existing one, see
**Create a Chart with API**; to change what data is displayed, see **Bind Data to Chart in
Script**.

**Walkthrough** (existing chart built via the Chart Editor, binding `Employee` to X and `Total`
three times to Y — once as Max, once as Min, once as Average on the Shape region of a Point-style
chart): once the min/max/average points render, use script to give the "Min" markers a solid red
arrow shape and enlarge the "Max" markers so the fill is more visible.

1. Create the visual frames:
   ```js
   var shpframe = new StaticShapeFrame(GShape.ARROWBAR);       // arrow-shaped markers
   var colframe = new StaticColorFrame(java.awt.Color(0xFF0000)); // static red
   var sizframe = new StaticSizeFrame(10);                      // static size, 10px
   ```
2. Get a handle to each dataset (element) via `EGraph.getElement(index)`:
   ```js
   var elem0 = graph.getElement(0); // Max point element
   var elem1 = graph.getElement(1); // Min point element
   ```
3. Assign the frames via `GraphElement.setShapeFrame(frame)` / `setColorFrame(frame)` /
   `setSizeFrame(frame)`:
   ```js
   elem1.shapeFrame = shpframe; // Min point element
   elem1.colorFrame = colframe; // Min point element
   elem0.sizeFrame = sizframe;  // Max point element
   ```

Complete script:
```js
var shpframe = new StaticShapeFrame(GShape.ARROWBAR);
var colframe = new StaticColorFrame(java.awt.Color(0xFF0000));
var sizframe = new StaticSizeFrame(10);
var elem0 = graph.getElement(0); // Max point element
var elem1 = graph.getElement(1); // Min point element
elem1.shapeFrame = shpframe;
elem1.colorFrame = colframe;
elem0.sizeFrame = sizframe;
```

---

## Create a Chart with API

Builds a new Chart entirely from script rather than modifying one from the Chart Editor. Since
script-based charts have no end-user interactivity, deselect "Enable Ad Hoc Editing" (Advanced tab)
for them.

**Walkthrough**:
1. Define the data (generally via `runQuery(name [,parameters])` against a Data Worksheet, or a
   literal JS array assigned to `data`/`dataset`):
   ```js
   data = runQuery("ws:global:Examples/AllSales");
   ```
2. Create the Chart object — `EGraph` is the global chart object (axes, legends, elements, etc.):
   ```js
   graph = new EGraph();
   ```
3. Create a data element — pass field (column) names to a `GraphElement` constructor:
   ```js
   var elem = new IntervalElement("State", "Sales"); // "bars"/"intervals"
   ```
   Other `GraphElement`s (`PointElement`, `LineElement`, …) generate other chart types (scatter,
   line, …).
4. Attach it: `graph.addElement(elem);`

Complete script:
```js
data = runQuery("ws:global:Examples/AllSales");
graph = new EGraph();
var elem = new IntervalElement("State", "Sales");
graph.addElement(elem);
```

---

## Bind Data to Chart in Script

Binding via the Chart Editor is easiest and lets you still layer script modifications on top (see
Modify Chart Element) — script binding is for when you need it dynamic/data-driven.

### Bind data in component-level script

Use `runQuery(name [,parameters])`, assigned to the Chart's `data` or `dataset` property
(`runQuery()` is only valid in component-level script):
```js
Chart1.data = runQuery("ws:global:Examples/AllSales");
graph = new EGraph();
var elem = new IntervalElement("State", "Sales");
graph.addElement(elem);
```
To use a data block already in the dashboard's own Data Worksheet, the simpler `data` syntax
works instead:
```js
Chart1.data = Sales; // "Sales" is the data block name
graph = new EGraph();
var elem = new IntervalElement("State", "Sales");
graph.addElement(elem);
```
A data-block name containing spaces needs `viewsheet['data block name']` syntax (see Access
Datasource Data).

### Bind data from an array

Set a literal JS array as the `data`/`dataset` property directly in component-level script:
```js
Chart1.data = [["State","Quantity"],["NJ",200],["NY",300]];
graph = new EGraph();
var elem = new IntervalElement("State", "Quantity");
graph.addElement(elem);
```

### Bind data in dashboard-level script

Bind to one of the dashboard's data blocks by name via the Chart's `query` property (e.g. in
`onRefresh`):
```js
Chart1.query = "Orders And Returns";
Chart1.bindingInfo.xFields = [["Company",Chart.STRING]];
Chart1.bindingInfo.yFields = [["Total",Chart.NUMBER]];
```

---

## Access Chart Data

The Chart's `data` property exposes the same aggregate values as the "Show Summary Data" button —
a 2D array where each column is a distinct dataset/measure. `data[i][0]` (first column) holds the
X-axis labels; `data[0][i]` (first row) holds the dataset/measure titles. Index by column number
(`table[1][2]`) or by dataset name (`table[1]['Sum(Measure2)']`); complex formula-table syntax is
also supported (see `data` in the API reference).

Example shape for a chart with two measures:
```
data[0][0] = 'DayOfWeek(Day)'
data[0][1] = 'Sum(Measure1)'
data[0][2] = 'Sum(Measure2)'
data[1][0] = 'Sun'
data[1][1] = 1
data[1][2] = 4
```

**Walkthrough — label only points below a threshold** (chart with `Category` on X, `Total` and
`Quantity Purchased` on Y, faceted into two axis sets):
```js
var threshold = 5000;

// Step through the rows of chart data with index i
for (var i = 1; i < table.length; i++) {
  // Obtain the ith value of 'Category' and 'Quantity'
  var Xvalue = data[i][0];
  var Yvalue = data[i]['Sum(Quantity Purchased)'];

  // Test the value of Quantity against the threshold
  if (Yvalue < threshold) {
    // Create the label object
    var form = new LabelForm();
    // Set the label to appear only on Quantity axes
    form.setMeasure('Sum(Quantity Purchased)');
    // Set the label text
    form.setLabel(Yvalue);
    // Set the label position and alignment
    form.setValues([Xvalue,Yvalue]);
    form.setAlignmentX(Chart.CENTER_ALIGNMENT);
    // Add the label to the graph
    graph.addForm(form);
  }
}
```

---

## Change Chart Coordinates

Several `Coordinate` objects each produce a different kind of chart (see also
`references/chartAPI/ChartCoordinates.md` for the full class reference).

### Rectangular coordinates (default)

`RectCoord` is created automatically for a new Chart — you only need to touch it explicitly when
defining a different coordinate system, or to tune the auto-created one.

Automatic (assigning scales directly to the graph, no explicit `RectCoord`):
```js
dataset = [["Direction", "Score"],[(Math.PI/2),20],[(Math.PI/4),30],[(Math.PI),35]];
graph = new EGraph();
var elem = new PointElement("Direction", "Score");
var xscale = new LinearScale("Direction");
var yscale = new LinearScale("Score");
yscale.setMin(0);
yscale.setMax(40);
var yaxis = new AxisSpec();
yaxis.setGridStyle(Chart.DOT_LINE);
yscale.setAxisSpec(yaxis);
xscale.setMin(0);
xscale.setMax(1.95*Math.PI);
xscale.setIncrement(Math.PI/8);
var xaxis = new AxisSpec();
var tspec = new TextSpec();
tspec.setFormat(new java.text.DecimalFormat("0.0"));
xaxis.setTextSpec(tspec);
xaxis.setGridStyle(Chart.DOT_LINE);
xscale.setAxisSpec(xaxis);
graph.setScale("Direction",xscale);
graph.setScale("Score",yscale);
graph.addElement(elem);
```
Get a handle to the auto-created coordinate object with `EGraph.getCoordinate()`.

Explicit (same result, but constructs `RectCoord` and assigns it via `EGraph.setCoordinate(coord)`):
```js
var rect = new RectCoord(xscale,yscale);
graph.setCoordinate(rect);
```

### Polar coordinates

`PolarCoord` wraps a `RectCoord`. Steps:
1. Build/obtain a `RectCoord` (`rect`, as above).
2. `var polar = new PolarCoord(rect);`
3. `graph.setCoordinate(polar);`

By default the rect X-axis maps to angle and the Y-axis to magnitude (radius); reverse a mapping
with `Coordinate.transpose()`.

**Map only one dimension** (useful for pie charts) with `PolarCoord.setType(value)`:
```js
var polar = new PolarCoord(rect);
polar.setType(PolarCoord.THETA); // map only the angle dimension
graph.setCoordinate(polar);
```

**Pie chart walkthrough** — a pie chart is a one-bar stacked bar chart in polar coordinates:
1. Distinguish categories by a `ColorFrame` instead of X position (pass `null` for the unused
   X-dimension in both the element and `RectCoord`):
   ```js
   var elem = new IntervalElement(null,"Revenue");
   var rect = new RectCoord(null, yscale);
   var cframe = new CategoricalColorFrame("State");
   elem.setColorFrame(cframe);
   ```
2. Stack into a single bar via `GraphElement.setCollisionModifier(value)`, and give it a
   `StackRange` so there's room:
   ```js
   elem.setCollisionModifier(GraphElement.STACK_SYMMETRIC);
   yscale.setScaleRange(new StackRange());
   ```
3. Convert to polar, mapped to the angle dimension:
   ```js
   var polar = new PolarCoord(rect);
   polar.setType(PolarCoord.THETA);
   graph.setCoordinate(polar);
   ```
4. Hide the (now meaningless) axis lines/labels via `AxisSpec`:
   ```js
   var yspec = new AxisSpec();
   yspec.setLabelVisible(false);
   yspec.setLineVisible(false);
   yspec.setTickVisible(false);
   yscale.setAxisSpec(yspec);
   ```
5. Label slices with a `TextFrame` and hide the (redundant) legend:
   ```js
   var tframe = new DefaultTextFrame("State");
   elem.setTextFrame(tframe);
   var legend = new LegendSpec();
   legend.setVisible(false);
   cframe.setLegendSpec(legend);
   ```
6. Explode the slices: `elem.setHint(GraphElement.HINT_EXPLODED,'true');`

Complete script:
```js
dataset = [["State", "Revenue"], ["CA", 200],["NY",300],["PA",150]];
graph = new EGraph();
var elem = new IntervalElement(null,"Revenue");
var xscale = new CategoricalScale("State");
var yscale = new LinearScale("Revenue");
var rect = new RectCoord(null, yscale);
var cframe = new CategoricalColorFrame("State");
elem.setColorFrame(cframe);
elem.setCollisionModifier(GraphElement.STACK_SYMMETRIC);
yscale.setScaleRange(new StackRange());
var polar = new PolarCoord(rect);
polar.setType(PolarCoord.THETA);
var yspec = new AxisSpec();
yspec.setLabelVisible(false);
yspec.setLineVisible(false);
yspec.setTickVisible(false);
yscale.setAxisSpec(yspec);
var tframe = new DefaultTextFrame("State");
elem.setTextFrame(tframe);
var legend = new LegendSpec();
legend.setVisible(false);
cframe.setLegendSpec(legend);
elem.setHint(GraphElement.HINT_EXPLODED,'true');
graph.setCoordinate(polar);
graph.addElement(elem);
```

### Parallel coordinates

`ParallelCoord` displays multiple dimensions as parallel (not orthogonal) axes — takes a set of
`Scale` objects.

```js
dataset = [["Test1","Test2","Test3","Name"],[100,80,20,'Joe'],[75,50,40,'Jane'],[50,30,80,'Fred']];
graph = new EGraph();
var elem = new LineElement();
elem.addDim("Test1");
elem.addDim("Test2");
elem.addDim("Test3");
var scale1 = new LinearScale("Test1");
var scale2 = new LinearScale("Test2");
var scale3 = new LinearScale("Test3");
scale1.setMax(100); scale2.setMax(100); scale3.setMax(100);
scale1.setMin(0); scale2.setMin(0); scale3.setMin(0);
var coord = new ParallelCoord(scale1,scale2,scale3);
var frame = new CategoricalColorFrame("Name");
elem.setColorFrame(frame);
graph.addElement(elem);
graph.setCoordinate(coord);
```

### Facet coordinates

`FacetCoord` nests an outer and inner `RectCoord` pair for multidimensional data as nested charts:
`var rect = new FacetCoord(outerCoord, innerCoord);`

```js
dataset = [["State", "Product", "Name", "Priority"],["NJ", "P1", "Joe", 2],["NJ", "P2", "Sam", 3],
  ["NY", "P1", "Jane", 4],["NJ", "P1", "Sam", 1],["NJ", "P2", "Joe", 10],["NY", "P1", "Sam", 10]];
graph = new EGraph();
var elem = new IntervalElement("Name", "Priority");
var state = new CategoricalScale("State");
var name = new CategoricalScale("Name");
var product = new CategoricalScale("Product");
var priority = new LinearScale("Priority");
var inner = new RectCoord(name, priority);   // "Priority" vs "Name"
var outer = new RectCoord(state, product);   // "Product" vs "State"
var coord = new FacetCoord(outer,inner);
graph.setCoordinate(coord);
graph.addElement(elem);
```
Result: an outer grid from the outer coordinates, with the inner coordinates rendered inside each
outer grid cell.

### Set coordinate background

A `PlotSpec` object sets a background color or image for the coordinate/plot area.

**Background color:**
```js
dataset = [["State", "Quantity"], ["NJ", 200], ["NY", 300]];
graph = new EGraph();
var elem = new IntervalElement("State", "Quantity");
var sscale = new CategoricalScale("State");
var qscale = new LinearScale("Quantity");
var coord = new RectCoord(sscale,qscale);
var spec = new PlotSpec();
spec.setBackground(java.awt.Color(0xEEEEFF));
coord.setPlotSpec(spec);
graph.setCoordinate(coord);
graph.addElement(elem);
```

**Background image** (`PlotSpec.setBackgroundImage(value)`) — example aligning a static Google
Maps image with chart coordinates (for an actual map background without scripting, prefer Web Map;
in script, prefer `setupGoogleMapsPlot(graph, urlPrefix, data, latitudeColumnName,
longitudeColumnName, maxWidthPx, maxHeightPx, widthPx, heightPx)` over doing it manually — this is
shown to illustrate the mechanics):
```js
dataset = [["Latitude","Longitude","PlaceName"],
  [40.8516051126306,-73.95223617553711,' GW Bridge '],
  [40.76292614285948,-74.00982856750488,' Lincoln Tunnel '],
  [40.72755146730012,-74.02107238769531,' Holland Tunnel ']];
graph = new EGraph();
var elem = new PointElement("Longitude","Latitude");
var tframe = new DefaultTextFrame("PlaceName");
var sframe = new StaticShapeFrame();
var cframe = new StaticColorFrame();
cframe.setColor(java.awt.Color(0x0000000));
sframe.setShape(GShape.FILLED_CIRCLE);
var tspec = new TextSpec();
tspec.setBackground(java.awt.Color(0x0000000));
tspec.setFont(java.awt.Font('Trebuchet',java.awt.Font.BOLD, 11));
tspec.setColor(java.awt.Color(0xffff00));
var pspec = new PlotSpec();
pspec.setLockAspect(true);
var logo = getImage("https://maps.google.com/maps/api/staticmap?center=40.7857,-73.9819&zoom=11&size=400x400&sensor=false");
pspec.setBackgroundImage(logo);
pspec.setYMax(40.8902); // high latitude
pspec.setYMin(40.6822); // low latitude
pspec.setXMax(-73.84529); // high longitude
pspec.setXMin(-74.1206);  // low longitude
var latscale = new LinearScale("Latitude");
var lonscale = new LinearScale("Longitude");
var aspec = new AxisSpec();
aspec.setLabelVisible(false);
latscale.setAxisSpec(aspec);
lonscale.setAxisSpec(aspec);
latscale.setScaleOption(0);
lonscale.setScaleOption(0);
latscale.setMax(pspec.getYMax()); latscale.setMin(pspec.getYMin());
lonscale.setMax(pspec.getXMax()); lonscale.setMin(pspec.getXMin());
var coord = new RectCoord(lonscale,latscale);
coord.setPlotSpec(pspec);
elem.setTextFrame(tframe);
elem.setTextSpec(tspec);
elem.setShapeFrame(sframe);
elem.setColorFrame(cframe);
elem.setHint(GraphElement.HINT_ALPHA,1);
graph.setCoordinate(coord);
graph.addElement(elem);
```
The scale limits must match the image's real geographic bounds for the image to align with the
chart axes.

---

## Change Chart Labels

Assign a `TextFrame` to a Chart element to represent data textually, then edit the labels via
`TextFrame.setText(value,text)`.

**Walkthrough — label bars with "Company: Percent-of-total"** (Chart with `Company` on X (ranked
Top 5 of `Sum(Total)`), `Total` on Y):
```js
// Create new TextFrame based on 'Company' field:
var tframe = new DefaultTextFrame('Company');
// Get a handle to the graph element (bars):
var elem = graph.getElement(0);
// Assign the TextFrame to the element (adds category names above the bars):
elem.setTextFrame(tframe);
// Compute the total amount of all companies:
var sumTotal = sum(data['Sum(Total)']);
// Loop through companies on chart:
for (i=1; i<data.length; i++) {
  var oldLabel = data[i][0];
  var barFraction = data[i][1]/sumTotal;
  var barPercent = formatNumber(barFraction,'##.00%');
  var newLabel = oldLabel + ':\n' + barPercent;
  tframe.setText(oldLabel,newLabel);
}
```
To instead **modify existing X-axis labels** rather than creating a new TextFrame, get a handle to
the axis's own TextFrame first:
```js
var tframe = graph.getCoordinate().getXScale().getAxisSpec().getTextFrame();
var elem = graph.getElement(0);
var sumTotal = sum(data['Sum(Total)']);
for (i=1; i<data.length; i++) {
  var oldLabel = data[i][0];
  var barFraction = data[i][1]/sumTotal;
  var barPercent = formatNumber(barFraction,'##.00%');
  var newLabel = oldLabel + ':\n' + barPercent;
  tframe.setText(oldLabel,newLabel);
}
```
(No need to re-assign this TextFrame — it's already wired to the axis; you're just swapping the
labels it holds.)

---

## Change Chart Scaling

A `Scale` maps abstract data values to physical representations (position, color, shape, …) — both
`EGraph` and `VisualFrame` need one. Some objects (e.g. `IntervalElement`) create an implicit
scale automatically.

### Change chart axis scaling

Assign a new `Scale` via `EGraph.setScale(field, scale)`. E.g. switching a widely-varying Y-axis
from implicit linear to log:
```js
dataset = [["State","Quantity"], ["CA",200], ["NY",3000]];
graph = new EGraph();
var elem = new IntervalElement("State", "Quantity");
var scale = new LogScale("Quantity");
graph.addElement(elem);
graph.setScale('Quantity',scale);
```

**Example — dynamically pin the Y-axis minimum to 75% of the smallest visible value** (chart with
`State` (ranked Top 5 of `Sum(Total)`) on X, `Total` on Y):
```js
var dMin = 10000000; // Default minimum
// Get a handle to the chart's Y-axis Scale:
var yScale = graph.getCoordinate().getYScale();
// Find the minimum Y-value in the chart data:
for (var i=0; i < dataset.getRowCount(); i++) {
  yVal = dataset.getData('Sum(Total)',i);
  if (yVal < dMin) { dMin = yVal; }
}
// Set Y-axis lower limit to .75 of minimum value:
yScale.setMin(.75*dMin);
```

### Change VisualFrame scaling

A `VisualFrame` (e.g. `BrightnessColorFrame`) also needs a `Scale` for its data→physical mapping;
assign a new one the same way. Example — force a brightness legend from the default (data-derived)
range to an explicit 500–3000:
```js
dataset = [["State","Quantity","Total"], ["NJ",200,2500],["NY",300,1500]];
graph = new EGraph();
var elem = new IntervalElement("State", "Quantity");
var frame = new BrightnessColorFrame();
frame.setField("Total");
frame.setColor(java.awt.Color(0xff0000));
var scale = new LinearScale("Total");
scale.setFields("Total");
scale.setMax(3000);
scale.setMin(500);
frame.setScale(scale);
elem.setColorFrame(frame);
graph.addElement(elem);
```
On a chart already built with the Chart Editor, get the element via `EGraph.getElement(index)`
first: `var elem = graph.getElement(0);`.

---

## Change Chart Element Appearance

Use a **static** `VisualFrame` for fixed (not data-driven) styling — color, size, texture. For
data-driven styling instead, see **Represent Data with Shape, Color, Size**.

```js
dataset = [["State","Quantity","Total"],["NJ",200,2500],["NY",300,1500]];
graph = new EGraph();
var elem = new PointElement("State", "Quantity");
var cframe = new StaticColorFrame();
cframe.setColor(java.awt.Color(0xff0000)); // red
var sframe = new StaticSizeFrame();
sframe.setSize(10);
elem.setColorFrame(cframe);
elem.setSizeFrame(sframe);
graph.addElement(elem);
```
On a chart already built with the Chart Editor: `var elem = graph.getElement(0);` first.

---

## Change Axis Properties

Use `EGraph.setScale(field, scale)` to assign a new `Scale` — this is how you swap linear↔log,
toggle tick marks, relocate labels, restyle fonts/colors, etc.

**Walkthrough** (bar chart, `State` on X, `Quantity` on Y):
```js
dataset = [["State","Quantity"], ["NJ",200], ["NY",3000]];
graph = new EGraph();
var elem = new IntervalElement("State", "Quantity");
graph.addElement(elem);

// 1. Logarithmic Y scale:
var logscale = new LogScale('Quantity');

// 2. Blue, dotted Y gridlines/axis line via AxisSpec:
var yspec = new AxisSpec();
yspec.setLineColor(java.awt.Color(0x0000ff));
yspec.setGridColor(java.awt.Color(0x0000ff));
yspec.setGridStyle(Chart.DOT_LINE);
logscale.setAxisSpec(yspec);

// 3. Explicit categorical X scale:
var cscale = new CategoricalScale('State');

// 4. Remove X axis line/ticks:
var xspec = new AxisSpec();
xspec.setLineVisible(false);
xspec.setTickVisible(false);
cscale.setAxisSpec(xspec);

// 5. Move X labels above the chart, bigger font:
var tspec = new TextSpec();
tspec.setFont(java.awt.Font('Dialog', java.awt.Font.BOLD, 14));
xspec.setTextSpec(tspec);
xspec.setAxisStyle(AxisSpec.AXIS_SINGLE2);

// 6. Replace X labels (state codes -> full names) via TextFrame:
var tframe = new DefaultTextFrame();
tframe.setText('NJ','New Jersey');
tframe.setText('NY','New York');
xspec.setTextFrame(tframe);

// 7. Assign the scales:
graph.setScale('Quantity',logscale);
graph.setScale('State',cscale);
```
On a chart already built with the Chart Editor, use getters to reach the existing objects instead
of constructing new scales: `graph.getCoordinate()` → `RectCoord`, then `.getYScale()` /
`.getXScale()` on it (`RectCoord.getYScale()`/`getXScale()`), then edit the `AxisSpec` in place.

---

## Change Legend Properties

Assigning a `VisualFrame` to a Chart element auto-creates a legend; edit it via the
`VisualFrame`'s `LegendSpec`. See also **Represent Multiple Measure** for legends spanning several
elements (page not present in this doc version — see the note at the top of this file).

**Walkthrough** (bar chart, `State`/`Quantity`, colored by `State` via `CategoricalColorFrame`):
```js
dataset = [["State", "Quantity"], ["NJ",200], ["NY",300]];
graph = new EGraph();
var elem = new IntervalElement("State", "Quantity");
var frame = new CategoricalColorFrame("State");
elem.setColorFrame(frame);
graph.addElement(elem);

// Blue dotted legend border:
var legend = new LegendSpec();
legend.setBorder(Chart.DOT_LINE);
legend.setBorderColor(java.awt.Color(0x0000ff));
frame.setLegendSpec(legend);

// Bold legend title:
var tspec = new TextSpec();
tspec.setFont(java.awt.Font('Dialog',java.awt.Font.BOLD, 14));
legend.setTitleTextSpec(tspec);
legend.setTitle('State');

// Full state names inside the legend, via TextFrame:
var tframe = new DefaultTextFrame();
tframe.setText('NJ','New Jersey');
tframe.setText('NY','New York');
legend.setTextFrame(tframe);

// Move the legend above the chart:
graph.setLegendLayout(Chart.TOP);
```
On a Chart-Editor-built chart, reach the existing objects via getters: `EGraph.getElement(index)` →
`GraphElement.getColorFrame()` → `VisualFrame.getLegendSpec()` — e.g.
`graph.getElement(0).getColorFrame().getLegendSpec()`.

---

## Represent Data with Shape, Color, Size

A bare `GraphElement` gives a basic 2D representation. Represent an **additional** dimension by
adding a `VisualFrame` (color/shape/size/texture/text) mapped to a field.

**Walkthrough — map `Total` to point size via `LinearSizeFrame`** (Point chart, `State`/`Quantity`):
```js
dataset = [["State","Quantity","Total"],["NJ",200,2500],["NY",300,1500]];
graph = new EGraph();
var elem = new PointElement("State", "Quantity");

// 1. Frame with the field to size by:
var frame = new LinearSizeFrame();
frame.setField("Total");

// 2. A VisualFrame needs a Scale for its mapping (see Change Chart Scaling):
var scale = new LinearScale();
scale.setFields("Total");
scale.setMax(3000);
scale.setMin(1000);
frame.setScale(scale);

// 3. Attach the frame to the element:
elem.setSizeFrame(frame);
graph.addElement(elem);
```
For a fixed (non-data-driven) size/color/etc. instead, see **Change Chart Element Appearance**.
