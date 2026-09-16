# Basic Chart Properties (StyleBI Chart Script Reference)

Source: https://www.inetsoft.com/docs/stylebi/InetSoftUserDocumentation/1.0.0/chartAPI/BasicChartProperties.html

> The top-level components required to build a Chart: the `dataset`/`data` objects that hold Chart
> data, the `EGraph` object (the global Chart object), and the `AxisSpec`/`LegendSpec`/`PlotSpec`/
> `TextSpec`/`TitleSpec` objects used to format axes, legends, plot area, text, and titles.

---

## data

The Chart `data` object is a two-dimensional array containing the aggregate data displayed on the
Chart. Use standard array notation, `data[i][j]`, to access row `i`/column `j`. `data.length` and
`data.size` give the number of X-axis labels and number of datasets, respectively.

You can also set the Chart's `query` property in `onRefresh` script, or assign the results of
`runQuery` directly to `data`:

```js
data = runQuery("ws:global:Examples/AllSales");
```

`data` also supports formula-table syntax:

```js
// Data in aggregated measure/column 'Sum(Sales)'
data["Sum(Sales)"]

// Data in 'Sum(Sales)' for state of NJ
data["Sum(Sales)@State:NJ"]

// Data in 'Sum(Sales)' where the value exceeds 1000000
data["State?Sum(Sales) > 1000000"]
```

---

## dataset / DataSet

The `dataset` object holds the values displayed on the graph, as a two-dimensional array where each
column is a distinct measure. It can be set three ways: via the Chart Editor's binding, by assigning
a JavaScript array literal to `dataset`, or by assigning a Data Worksheet query result:

```js
dataset = [["State", "Quantity"],["NJ", 200],["NY", 300],["PA", 370],["CT", 75]];
// or
dataset = runQuery("ws:global:Examples/AllSales");
```

`dataset` is also readable — in many cases the `data` property is more convenient for reading, but
`DataSet` exposes these methods directly:

### DataSet.getColCount()
Returns the number of columns in the dataset.
- **Returns**: Integer number of columns.

### DataSet.getData(column, row)
Returns the value at the given column/row indices. **Index order is (column, row)**, not
(row, column). The first column (index 0) holds the X-axis labels.
- **Parameters**: `column` (Integer), `row` (Integer).
```js
// Create a chart with two elements:
dataset = [["State", "Total", "Profit"],["NJ", 200, 25], ["NY", 300, 150]];
graph = new EGraph();
var elem1 = new IntervalElement("State", "Total");
var elem2 = new IntervalElement("State", "Profit");
var frame = new StaticColorFrame(java.awt.Color.red);
elem2.setColorFrame(frame);
graph.addElement(elem1);
graph.addElement(elem2);

// Loop through the rows and columns, and place labels on the bars.
for (var i=0; i<dataset.getRowCount(); i++) {
  for (var j=0; j<dataset.getColCount(); j++) {
    var form = new LabelForm();
    form.setColor(java.awt.Color.black);
    form.setLabel(dataset.getData(j,i));
    form.setValues([dataset.getData(0,i),dataset.getData(j,i)-20]);
    graph.addForm(form)
  }
}
```

### DataSet.setOrder(dim, arr)
Sets a manual label ordering for a given dimension field.
- **Parameters**: `dim` (name of the dimension to sort), `arr` (array of label strings in desired
  order, e.g. `['label1','label2','label3']`).
```js
dataset = [["State", "Quantity"],["NJ", 200],["NY", 300],["PA", 25]];
dataset.setOrder('State',['PA','NY','NJ']);
graph = new EGraph();
var elem = new IntervalElement("State", "Quantity");
graph.addElement(elem);
```

### DataSet.getRowCount()
Returns the number of rows in the DataSet, **including the header row**.
- **Returns**: Integer number of rows.

---

## DefaultDataSet

Creates a dataset from an array (an alternative to assigning an array literal directly to
`dataset`).
```js
var arr = [["State","Quantity"], ["NJ",200], ["NY",300]];
dataset = new DefaultDataSet(arr);
graph = new EGraph();
var elem = new IntervalElement("State", "Quantity");
graph.addElement(elem);
```

---

## EGraph

The `EGraph` object represents the graph definition — the top-level object for building a chart via
script.
```js
graph = new EGraph();
```

### EGraph.addElement(elem)
Adds a `GraphElement` to the chart (see Chart Elements). Inverse operations:
`EGraph.removeElement(idx)`, `EGraph.clearElements()`.
- **Parameter** `elem`: a GraphElement object.

### EGraph.addForm(form)
Adds a `GraphForm` to the chart (see Chart Annotation). Inverse operations:
`EGraph.removeForm(idx)`, `EGraph.clearForms()`.
- **Parameter** `form`: a GraphForm object.
```js
var form = new LineForm();
form.addPoint(new java.awt.Point(0,0));
form.addPoint(new java.awt.Point(100,100));
form.addPoint(new java.awt.Point(200,100));
form.setFill(true);
graph.addForm(form);
```
To add a form to a Chart already built with the Chart Editor, just call `graph.addForm(form)` —
no need to define a new Chart element.

### EGraph.clearElements()
Removes **all** `GraphElement` and `GraphForm` objects from the chart. Inverse:
`EGraph.addElement(elem)`/`EGraph.addForm(form)`.

### EGraph.clearForms()
Removes all `GraphForm` objects from the chart (elements are untouched). Inverse:
`EGraph.addForm(form)`.

### EGraph.getCoordinate()
Returns a handle to the graph's `Coordinate` object — useful when the coordinate wasn't assigned to
a variable at creation time. Setter: `EGraph.setCoordinate(coord)`.
- **Returns**: a Coordinate object.
```js
var coord = graph.getCoordinate();
coord.transpose();
```

### EGraph.getElement(index)
Returns a handle to the `GraphElement` at the given index. To add an element, use
`EGraph.addElement(elem)`.
- **Parameter** `index`: Integer index of the element.
```js
var elem = graph.getElement(0);
elem.setHint(GraphElement.HINT_SHINE,'false');
```

### EGraph.getElementCount()
Returns the number of `GraphElement` objects currently on the chart.
- **Returns**: Integer count.
```js
var elemCount = graph.getElementCount();
for (var i=0; i<elemCount; i++) {
  graph.getElement(i).endArrow = true;
}
```

### EGraph.getForm(index)
Returns a handle to the `GraphForm` at the given index. To add a form, use `EGraph.addForm(form)`.
- **Parameter** `index`: Integer index of the form.
```js
graph.addForm(new LineForm());
var form = graph.getForm(0);
form.addPoint(java.awt.Point(100,100));
form.addPoint(java.awt.Point(200,200));
form.setColor(java.awt.Color(0xff0000));
```

### EGraph.getFormCount()
Returns the number of `GraphForm` objects currently on the chart.
- **Returns**: Integer count.

### EGraph.getLegendLayout()
Returns the chart legend's position. Setter: `EGraph.setLegendLayout(value)`.
- **Returns**: one of `Chart.NONE` (0, no legend), `Chart.TOP` (1, above graph, aligned left),
  `Chart.RIGHT` (2, right of graph, aligned top — default), `Chart.BOTTOM` (3, below X-axis title,
  aligned left), `Chart.LEFT` (4, left of Y-axis title, aligned top), `Chart.IN_PLACE` (5,
  superimposed on graph).

### EGraph.getLegendPreferredSize()
Returns the legend's height/width in pixels, as set by `EGraph.setLegendPreferredSize(value)`.
- **Returns**: Integer (pixels).

### EGraph.getScale(field)
Returns a handle to the `Scale` for the given field — useful when the scale wasn't assigned to a
variable at creation. Setter: `EGraph.setScale(field, scale)`.
- **Parameter** `field`: String field name.
```js
var scale = graph.getScale("Sum(Total)");
scale.setMin(600000);
scale.setMax(1000000);
```

### EGraph.getVisualFrames()[idx]
Returns the `VisualFrame` at the given index (color/shape/size/etc. frame assigned to an element —
see Chart Visuals).
- **Parameter** `idx`: index of the VisualFrame.
```js
var frame = new BrightnessColorFrame();
frame.setField("Quantity");
frame.setColor(java.awt.Color(0xff0000));
elem.setColorFrame(frame);
graph.addElement(elem);
alert(Chart1.graph.getVisualFrames()[0].getColor());
```

### EGraph.getXTitleSpec() / getX2TitleSpec() / getYTitleSpec() / getY2TitleSpec()
Return a `TitleSpec` for the X-axis below the chart / X-axis above the chart / Y-axis to the left /
Y-axis to the right, respectively. Setters: the matching `setXTitleSpec`/`setX2TitleSpec`/
`setYTitleSpec`/`setY2TitleSpec`.
- **Returns**: a TitleSpec object.
```js
var spec = Chart1.graph.getXTitleSpec();
alert(spec.getLabel());
```

### EGraph.removeElement(idx)
Removes the specified `GraphElement`. Elements are indexed in the order added, starting at 0.
- **Parameter** `idx`: a GraphElement object (its index).
```js
graph.addElement(elem);
graph.removeElement(0);
```

### EGraph.removeForm(idx)
Removes the specified `GraphForm`. Forms are indexed in the order added, starting at 0.
- **Parameter** `idx`: a GraphForm object (its index).

### EGraph.setCoordinate(coord)
Sets the `Coordinate` for the Chart (see Chart Coordinates). Getter: `EGraph.getCoordinate()`.
- **Parameter** `coord`: a Coordinate object.
```js
var sscale = new CategoricalScale("State");
var qscale = new LinearScale("Quantity");
var coord = new RectCoord(sscale,qscale);
coord.transpose();
graph.setCoordinate(coord);
```

### EGraph.setLegendLayout(value)
Sets the chart legend's position. Getter: `EGraph.getLegendLayout()`. See also
`setActionVisible(name, Boolean)` to control whether a user may reposition the legend.
- **Parameter** `value`: one of the `Chart.NONE`/`TOP`/`RIGHT`/`BOTTOM`/`LEFT`/`IN_PLACE` constants
  (see `getLegendLayout()` above).

### EGraph.setLegendPreferredSize(value)
Sets the legend's height/width in pixels. Getter: `EGraph.getLegendPreferredSize()`.
- **Parameter** `value`: Integer number of pixels.

### EGraph.setScale(field, scale)
Sets the `Scale` for the given axis field (see Chart Coordinates). Getter:
`EGraph.getScale(field)`.
- **Parameters**: `field` (String, axis/column name), `scale` (a Scale object).
```js
var qscale = new LinearScale("Quantity");
qscale.setMin(100);
qscale.setMax(500);
graph.setScale("Quantity", qscale);
```

### EGraph.setXTitleSpec(spec) / setX2TitleSpec(spec) / setYTitleSpec(spec) / setY2TitleSpec(spec)
Set the `TitleSpec` for the X-axis below / X-axis above / Y-axis left / Y-axis right, respectively.
Getters: the matching `getXTitleSpec`/`getX2TitleSpec`/`getYTitleSpec`/`getY2TitleSpec`.
- **Parameter** `spec`: a TitleSpec object.
```js
var spec = new TitleSpec();
spec.setLabel("X Title");
graph.setXTitleSpec(spec);
```

---

## AxisSpec

Holds axis information for a `Scale` — border/grid/label/tick appearance for one axis. Reached via
`Scale.setAxisSpec(spec)`/`Scale.getAxisSpec()` on the Scale bound to that axis, or (on a
Chart-Editor-built chart) via `graph.getCoordinate().getXScale()/getYScale().getAxisSpec()`.

### AxisSpec.getTextSpec()
Retrieves the axis `TextSpec`. Setter: `AxisSpec.setTextSpec(spec)`.
- **Returns**: TextSpec.

### AxisSpec.setAbbreviate(Boolean)
Omits the common prefix of `TimeScale` labels to conserve space. Only observed when a Date format is
specified. No getter.
- **Parameter** `Boolean`: `true` drop common prefixes, `false` keep them.

### AxisSpec.setAxisStyle(value)
Sets the axis style. Getter: `getAxisStyle()`.
- **Parameter** `value`: one of `AxisSpec.AXIS_SINGLE` (axes on left/bottom), `AXIS_SINGLE2` (axis
  on top/right), `AXIS_DOUBLE` (both axes, left/bottom labels), `AXIS_DOUBLE2` (both axes, top/right
  labels), `AXIS_CROSS` (axis at zero-position), `AXIS_NONE` (axis not drawn).

### AxisSpec.setColorFrame(frame)
Maps axis label values to colors. Getter: `getColorFrame()`.
- **Parameter** `frame`: a ColorFrame object.
```js
var aspec = new AxisSpec();
var frame = new CategoricalColorFrame();
frame.setField("State");
frame.setColor("NJ", java.awt.Color(0xff0000));
frame.setColor("NY", java.awt.Color(0x0000ff));
aspec.setColorFrame(frame);
```

### AxisSpec.setFontFrame(frame)
Maps axis label values to fonts. Getter: `getFontFrame()`.
- **Parameter** `frame`: a CategoricalFontFrame object.

### AxisSpec.setGridAsShape(Boolean)
Whether grid lines are represented as shapes (can curve under a coordinate transform, e.g.
rectangular→polar) or as positions (stay straight; only endpoints transform).
- **Parameter** `Boolean`: `true` (default) as shape, `false` as position.

### AxisSpec.setGridColor(value)
Color of the axis grid lines. Getter: `getGridColor()`.
- **Parameter** `value`: a java.awt.Color object.

### AxisSpec.setGridOnTop(Boolean)
Whether grid lines layer over or under the chart elements.
- **Parameter** `Boolean`: `true` grid over elements, `false` elements over grid.

### AxisSpec.setGridStyle(value)
Style of the axis grid lines. Getter: `getGridStyle()`.
- **Parameter** `value`: a `GLine` constant (`THIN_LINE`/`DOT_LINE`/`DASH_LINE`/`MEDIUM_DASH`/
  `LARGE_DASH`).

### AxisSpec.setInPlot(Boolean)
Whether the scale's specified maximum value is included within the plot region (adds a small buffer
so the max value doesn't sit right at the plot edge). No getter.
- **Parameter** `Boolean`: `true` max value within plot region, `false` max value at plot edge.

### AxisSpec.setLabelGap(value)
Gap in pixels between axis labels and the axis line. Getter: `getLabelGap()`.
- **Parameter** `value`: Integer pixels.

### AxisSpec.setLabelVisible(Boolean)
Whether axis labels are shown. No getter.
- **Parameter** `Boolean`: `true` visible, `false` hidden.

### AxisSpec.setLineColor(value)
Color of the axis line. Getter: `getLineColor()`.
- **Parameter** `value`: a java.awt.Color object.

### AxisSpec.setLineVisible(Boolean)
Whether the axis line is shown. No getter.
- **Parameter** `Boolean`: `true` visible, `false` hidden.

### AxisSpec.setTextFrame(frame)
Maps axis values to replacement display text. Getter: `getTextFrame()`.
- **Parameter** `frame`: a TextFrame object (e.g. `DefaultTextFrame`).
```js
var tframe = new DefaultTextFrame();
tframe.setText('NJ','New Jersey');
tframe.setText('NY','New York');
var aspec = new AxisSpec();
aspec.setTextFrame(tframe);
```

### AxisSpec.setTextSpec(spec)
Controls axis text appearance (color, font, format, etc). Getter: `AxisSpec.getTextSpec()`.
- **Parameter** `spec`: a TextSpec object.

### AxisSpec.setTickVisible(Boolean)
Whether axis tick marks are shown. No getter.
- **Parameter** `Boolean`: `true` visible, `false` hidden.

**Common pattern for modifying an AxisSpec on a chart already built with the Chart Editor** (no
Chart element definition needed) — chain from the coordinate down to the scale's AxisSpec:
```js
var coord = graph.getCoordinate();
var scale = coord.getYScale();      // or getXScale()
var spec = scale.getAxisSpec();
// Compact: var spec = graph.getCoordinate().getYScale().getAxisSpec();
spec.setGridColor(java.awt.Color(0xff0000));
```

---

## LegendSpec (extends VisualFrame)

Contains legend formatting information. Reached via `VisualFrame.setLegendSpec(spec)`/
`VisualFrame.getLegendSpec()` on the ColorFrame/etc. bound to an element, or
`EGraph.setLegendLayout(value)` to place the legend in a predefined location.

### LegendSpec.setBackground(value)
Legend background color. Getter: `getBackground()`.
- **Parameter** `value`: a java.awt.Color object.

### LegendSpec.setBorder(value)
Legend border style. Getter: `getBorder()`.
- **Parameter** `value`: a `GLine` constant.

### LegendSpec.setBorderColor(value)
Legend border color. Getter: `getBorderColor()`.
- **Parameter** `value`: a java.awt.Color object.

### LegendSpec.setPartial(Boolean)
`true`: legend items scroll if they don't fit the allotted area. `false`: force all items to
display without scrolling (may truncate labels).
- **Parameter** `Boolean`.

### LegendSpec.setPosition(value)
Position of the legend's bottom-left corner, for `Chart.IN_PLACE` layout. Positive = distance from
left/bottom; negative = distance from right/top. Getter: `getPosition()`.
- **Parameter** `value`: a subclass of java.awt.geom.Point2D (Point for pixels, Point2D.Double for
  proportion).

### LegendSpec.setPreferredSize(value)
Legend size in pixels, for `Chart.IN_PLACE` layout. Getter: `getPreferredSize()`.
- **Parameter** `value`: a java.awt.Dimension object.

### LegendSpec.setTextFrame(frame)
Maps legend values to replacement display text. Getter: `getTextFrame()`.
- **Parameter** `frame`: a TextFrame object.

### LegendSpec.setTextSpec(spec)
Legend body text attributes (color, font, format). Getter: `getTextSpec(measure)`.
- **Parameter** `spec`: a TextSpec object.

### LegendSpec.setTitle(value)
Legend title text. Getter: `getTitle()`.
- **Parameter** `value`: String.

### LegendSpec.setTitleTextSpec(spec)
Legend title text attributes (color, font, format). Getter: `getTitleTextSpec()`.
- **Parameter** `spec`: a TextSpec object.

### LegendSpec.setTitleVisible(Boolean)
Whether the legend title is shown. Getter: `getTitleVisible()`.
- **Parameter** `Boolean`.

### LegendSpec.setVisible(Boolean)
Whether the legend is shown. Getter: `getVisible()`.
- **Parameter** `Boolean`.

```js
var frame = new CategoricalColorFrame();
frame.setField("State");
var spec = new LegendSpec();
spec.setTitle('Legend1');
spec.setBackground(java.awt.Color(0xff00ff));
frame.setLegendSpec(spec);
elem.setColorFrame(frame);
```

**Common pattern for modifying a LegendSpec on a chart already built with the Chart Editor**:
```js
var elem = graph.getElement(0);
var frame = elem.getColorFrame();
var spec = frame.getLegendSpec();
// Compact: var spec = graph.getElement(0).getColorFrame().getLegendSpec();
spec.setVisible(false);
```

---

## PlotSpec

Contains visual information for a `Coordinate` object — the chart's plot area. Reached via
`Coordinate.setPlotSpec(spec)`/`Coordinate.getPlotSpec()`.

### PlotSpec.setAlpha(value)
Transparency of the plot background color/image. Getter: `getAlpha()`.
- **Parameter** `value`: Number in `[0,1]` — 0 fully transparent, 1 fully opaque.

### PlotSpec.setBackground(value)
Background color of the plot area. Getter: `getBackground()`.
- **Parameter** `value`: a java.awt.Color object.

### PlotSpec.setBackgroundImage(value)
Background image of the plot area. Getter: `getBackgroundImage()`.
- **Parameter** `value`: an Image object (see `getImage(string)`).

### PlotSpec.setLockAspect(Boolean)
`true`: retain the background image's original aspect ratio (coordinate scaling adapts to it).
`false` (default): resize the image to fit the existing coordinate scaling.
- **Parameter** `Boolean`.

### PlotSpec.setXMax(value) / setXMin(value) / setYMax(value) / setYMin(value)
X/Y-axis value at which to place the right/left/top/bottom edge of the background image. Getters:
`getXMax()`/`getXMin()`/`getYMax()`/`getYMin()`.
- **Parameter** `value`: a Number.

```js
var sscale = new CategoricalScale("State");
var qscale = new LinearScale("Quantity");
var coord = new RectCoord(sscale,qscale);
var spec = new PlotSpec();
var logo = getImage("https://www.inetsoft.com/images/home/logo.gif");
spec.setBackgroundImage(logo);
spec.setAlpha(.3);
coord.setPlotSpec(spec);
graph.setCoordinate(coord);
```

**Common pattern for modifying a PlotSpec on a chart already built with the Chart Editor**:
```js
var coord = graph.getCoordinate();
var spec = coord.getPlotSpec();
// Compact: var spec = graph.getCoordinate().getPlotSpec();
spec.setAlpha(0.5);
```

---

## TextSpec

Contains information about the display of text — used by `AxisSpec`, `TitleSpec`, `LegendSpec`,
`LabelForm`, etc.

### TextSpec.setBackground(value)
Text background color. Getter: `getBackground()`.
- **Parameter** `value`: a java.awt.Color object.

### TextSpec.setColor(value)
Text color. Getter: `getColor()`.
- **Parameter** `value`: a java.awt.Color object.

### TextSpec.setFont(value)
Text font. Getter: `getFont()`.
- **Parameter** `value`: a java.awt.Font object, or a string `'FontFamily-FontStyle-FontSize'`
  (FontFamily = a server font or generic family like serif/sans serif; FontStyle =
  `BOLD`/`ITALIC`/`PLAIN`/`BOLD ITALIC`; FontSize = any integer).

### TextSpec.setFormat(format)
How date/numeric data is formatted as a string for display. Getter: `getFormat()`.
- **Parameter** `format`: a `java.text.Format`, or `inetsoft.util.ExtendedDecimalFormat` when the
  number format uses a multiplier suffix (`K`/`M`/`B`, e.g. `"#,##0.0M"` — plain
  `java.text.DecimalFormat` won't scale the values).
  - Date masks: `M`=month, `d`=date, `y`=year, `E`=day of week (e.g. `MMM-dd-yyyy` → "Nov-08-2006").
  - Number masks: `#`=number, `0`=zero-padded number (e.g. `#,###.00` → "124,521.63").
  - Text masks: `{0}` is a placeholder for the string, e.g. `"Salesperson: {0}"` → "Salesperson: Susan".
```js
var tspec = new TextSpec();
tspec.setFormat(java.text.DecimalFormat("##,###.00"));
aspec.setTextSpec(tspec);
```

### TextSpec.setRotation(value)
Text rotation in degrees. Getter: `getRotation()`.
- **Parameter** `value`: Number of degrees.

**Common pattern for modifying a TextSpec on a chart already built with the Chart Editor** — chain
from whichever object owns the TextSpec (a TitleSpec, an AxisSpec, etc.):
```js
var textspec = graph.getXTitleSpec().getTextSpec();
textspec.setColor(java.awt.Color(0xff0000));
```

---

## TitleSpec

Contains title text and formatting information — used for axis titles (`EGraph.setXTitleSpec`
etc.) and legend titles.

### TitleSpec.getTextSpec()
Retrieves the title's `TextSpec`. Setter: `TitleSpec.setTextSpec(spec)`.
- **Returns**: TextSpec.

### TitleSpec.setLabel(value)
Title text. Getter: `getLabel()`.
- **Parameter** `value`: String.

### TitleSpec.setTextSpec(spec)
Title text attributes (color, font, format). Getter: `TitleSpec.getTextSpec()`.
- **Parameter** `spec`: a TextSpec object.

```js
var spec = new TitleSpec();
var textspec = new TextSpec();
textspec.setColor(java.awt.Color(0xff0000));
spec.setLabel("X Title");
spec.setTextSpec(textspec);
graph.setXTitleSpec(spec);
```
