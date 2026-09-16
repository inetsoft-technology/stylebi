# Chart Coordinates (StyleBI Chart Script Reference)

Source: https://www.inetsoft.com/docs/stylebi/InetSoftUserDocumentation/1.0.0/chartAPI/ChartCoordinates.html

> The following pages discuss objects related to setting chart coordinates and their scaling
> properties. See Change Chart Scaling for a tutorial introduction.

Object hierarchy: `Coordinate` is the base class for coordinate systems (`RectCoord`, `Rect25Coord`,
`PolarCoord`, `ParallelCoord`, `FacetCoord`, `TriCoord`). `Scale` is the base class for axis scales
(`CategoricalScale`, `LinearScale`, `LogScale`, `PowerScale`, `TimeScale` — `LogScale` and
`PowerScale` extend `LinearScale`). `ScaleRange` is the base class for range-computation strategies
(`LinearRange`, `StackRange`). Assign a coordinate system to a chart with `EGraph.setCoordinate(coord)`;
assign a scale to a field with `EGraph.setScale(field, scale)`.

---

## Coordinate (base class)

The Coordinate object contains the coordinates against which data can be represented.

### Coordinate.getPlotSpec()
Retrieve the PlotSpec of the coordinates. Setter: `setPlotSpec(spec)`.
- **Returns**: a `PlotSpec` object.
```js
var coord = graph.getCoordinate();
var spec = coord.getPlotSpec();
spec.setBackground(java.awt.Color(0xEEEEFF));
```

### Coordinate.reflect(vert)
Reflect the coordinates about the vertical or horizontal axis.
- **Parameter** `vert`: `true` reflect about horizontal axis, `false` reflect about vertical axis.
```js
var coord = new RectCoord(sscale, qscale);
coord.reflect(true);
graph.setCoordinate(coord);
```
To modify an existing chart: `graph.getCoordinate().reflect(true);`

### Coordinate.rotate(value)
Rotate the axes by the specified angle.
- **Parameter** `value`: an angle in degrees.
```js
var polar = new PolarCoord(rect);
polar.rotate(45);
polar.setType(PolarCoord.THETA);
graph.setCoordinate(polar);
```

### Coordinate.setExtent(minX, minY, maxX, maxY)
Sets the extent of a geographical coordinate system (for a map-type chart) to the specified
latitudes and longitudes. X = longitude, Y = latitude.
- **Parameters** `minX`/`minY`/`maxX`/`maxY`: min/max longitude/latitude.
```js
graph.getCoordinate().setExtent(-77.036667, 38.895111, -71.063611, 42.358056);
graph.getElement(0).setInPlot(false); // otherwise the full map still forces display
```

### Coordinate.setFullMap(Boolean)
When `true`, the entire map is displayed within the element's borders. When `false`, only regions
representing data are displayed and others are cropped.
- **Parameter** `Boolean`: `true` fit entire map (default), `false` fit only data regions.
```js
var zoomed = SelectionTree1.selectedObjects.length > 0;
graph.getCoordinate().setFullMap(!zoomed);
graph.getElement(0).setInPlot(!zoomed);
```

### Coordinate.setPlotSpec(spec)
Assign a `PlotSpec` to the coordinates. Getter: `getPlotSpec()`.
- **Parameter** `spec`: a `PlotSpec` object.
```js
var coord = graph.getCoordinate();
var spec = new PlotSpec();
var logo = getImage("https://www.inetsoft.com/images/home/logo.gif");
spec.setBackgroundImage(logo);
spec.setAlpha(.3);
coord.setPlotSpec(spec);
```

### Coordinate.transpose()
Interchanges the axes — e.g. in a rectangular coordinate system, X becomes Y and Y becomes X.
```js
var coord = new RectCoord(sscale, qscale);
coord.transpose();
graph.setCoordinate(coord);
```
To modify an existing chart: `graph.getCoordinate().transpose();`

---

## RectCoord (extends Coordinate)

Rectangular coordinates against which data can be represented.
```js
var rect = new RectCoord(xscale, yscale);
```
You can pass Scale objects to the constructor, or assign them later via `setXScale`/`setYScale`.

### RectCoord.getXScale() / RectCoord.getYScale() / RectCoord.getYScale2()
Retrieve the Scale for the X-axis / Y-axis / secondary (right-side) Y-axis. Setters:
`setXScale(scale)` / `setYScale(scale)` / `setYScale2(scale)`.
- **Returns**: a `Scale` object.
```js
var xspec = graph.getCoordinate().getXScale().getAxisSpec();
xspec.setLineColor(java.awt.Color(0xff0000));
```

### RectCoord.setXScale(scale) / RectCoord.setYScale(scale)
Specifies the Scale for the X-axis / Y-axis. Can also be passed to the constructor.
- **Parameter** `scale`: a `Scale` object.
```js
var coord = new RectCoord();
coord.setXScale(new CategoricalScale("State"));
coord.setYScale(new LinearScale("Quantity"));
graph.setCoordinate(coord);
```

### RectCoord.setYScale2(scale)
Specifies the Scale for the secondary (right-side) Y-axis — used for a dual-axis chart.
- **Parameter** `scale`: a `Scale` object.
```js
var coord = new RectCoord();
coord.setXScale(sscale);
coord.setYScale(qscale);
coord.setYScale2(qscale2);
graph.setCoordinate(coord);
```

---

## Rect25Coord (extends RectCoord)

Identical to `RectCoord`, but renders elements with a 3D effect. Inherits all of RectCoord's
methods (`getXScale`/`getYScale`/`getYScale2`/`setXScale`/`setYScale`/`setYScale2`) and adds none
of its own.
```js
var coord = new Rect25Coord(xscale, yscale);
graph.setCoordinate(coord);
```

---

## PolarCoord (extends Coordinate)

Polar coordinates against which data can be represented.
```js
var polar = new PolarCoord(rect);
```
Pass a `RectCoord` to the constructor, or assign one later via `setCoordinate(coord)`.

### PolarCoord.setCoordinate(coord)
Specifies the `RectCoord` the polar coordinates are based on. Getter: `getCoordinate()`.
- **Parameter** `coord`: a `RectCoord` object.
```js
var polar = new PolarCoord();
polar.setCoordinate(rect);
graph.setCoordinate(polar);
```

### PolarCoord.setHoleRatio(value)
Size of the "hole" when using `PolarCoord.PLUS` for `setType(value)`. Getter: `getHoleRatio()`.
- **Parameter** `value`: number between 0 and 1 — fraction of outer plot radius occupied by the
  center hole. Default `0.5`; `0` = no hole, `1` = hole fills the whole chart.
```js
var polar = new PolarCoord(coord);
polar.setType(PolarCoord.PLUS);
polar.setHoleRatio(.25);
graph.setCoordinate(polar);
```

### PolarCoord.setType(value)
Type of polar transformation. Getter: `getType()`.
- **Parameter** `value`: `PolarCoord.THETA` (angle only) | `PolarCoord.THETA_RHO` (angle and
  radius) | `PolarCoord.RHO` (radius only) | `PolarCoord.PLUS` (angle and radius, with hole).
```js
var rect = new RectCoord(null, yscale);
var polar = new PolarCoord(rect);
polar.setType(PolarCoord.THETA);
graph.setCoordinate(polar);
```

---

## ParallelCoord (extends Coordinate)

Parallel coordinates against which multi-dimension data can be represented.
```js
var coord = new ParallelCoord(scale1, scale2, /* ... */);
```

### ParallelCoord.setScales(scales)
The set of parallel scales to use. Can also be passed to the constructor.
- **Parameter** `scales`: array of `Scale` objects.
```js
var coord = new ParallelCoord();
coord.setScales([qscale, tscale, rscale]);
elem.addDim("Quantity");
elem.addDim("Total");
elem.addDim("Returns");
graph.setCoordinate(coord);
```

---

## FacetCoord (extends Coordinate)

A set of inner and outer coordinates on which multidimensional data can be represented as nested
charts (small multiples).
```js
var rect = new FacetCoord(outerCoord, innerCoord);
```

### FacetCoord.setInnerCoordinates(coord)
The `RectCoord` (or array of them) for the inner coordinates of the facet graph. If an array, each
inner coordinate set is plotted independently. Getter: `getInnerCoordinates()`.
- **Parameter** `coord`: array of `RectCoord` objects.

### FacetCoord.setOuterCoordinate(coord)
The `RectCoord` used for the outer coordinates — generally has categorical scales on both axes.
Getter: `getOuterCoordinate()`.
- **Parameter** `coord`: a `RectCoord` object.
```js
var inner = new RectCoord(city, quantity);
var outer = new RectCoord(state, product);
var coord = new FacetCoord();
coord.setInnerCoordinates([inner]);
coord.setOuterCoordinate(outer);
graph.setCoordinate(coord);
```

### FacetCoord.setVertical(Boolean)
Whether the inner coordinates are stacked vertically or horizontally. No getter.
- **Parameter** `Boolean`: `true` stack vertically (default), `false` stack horizontally.

---

## TriCoord (extends Coordinate)

Triangular coordinates. Although there are three axes, only two independent measures exist because
all three must sum to the scale's maximum — effective for representing proportions of a fixed
total.
```js
var coord = new TriCoord(qscale);
```

### TriCoord.setScale(scale)
The scale to use for all three axes. Can also be passed to the constructor. Getter: `getScale()`.
- **Parameter** `scale`: a `Scale` object.
```js
var qscale = new LinearScale("Quantity");
qscale.setMin(0);
qscale.setMax(200);
var coord = new TriCoord();
coord.setScale(qscale);
elem.addDim("Quantity");   // bottom axis
elem.addDim("Total");      // right axis
elem.addVar("Returns");    // left axis -- addVar, not addDim, for the third measure
graph.setScale("Quantity", qscale);
graph.setScale("Total", qscale);
graph.setScale("Returns", qscale);
graph.setCoordinate(coord);
```

---

## Scale (base class)

The Scale object defines the measurement of a dimension. Subclasses: `LinearScale`, `LogScale`,
`PowerScale`, `TimeScale`, `CategoricalScale`.

### Scale.getAxisSpec()
Retrieve the axis properties as an `AxisSpec` object. Setter: `setAxisSpec(spec)`.
- **Returns**: an `AxisSpec` object.
```js
var spec = graph.getCoordinate().getYScale().getAxisSpec();
spec.setTickVisible(false);
```

### Scale.init(dataset)
Forces immediate computation of the automatic scale attributes. Only needed to access
auto-computed attributes (e.g. `getMax()`) from within a script before the scale is otherwise
initialized.
- **Parameter** `dataset`: the Chart `dataset` attribute.
```js
var qscale = new LinearScale("Quantity");
qscale.init(dataset);
var defaultMax = qscale.getMax();
qscale.setMax(defaultMax + defaultMax / 2);
graph.setScale("Quantity", qscale);
```

### Scale.setAxisSpec(spec)
Specifies the axis properties. Getter: `getAxisSpec()`.
- **Parameter** `spec`: an `AxisSpec` object.

### Scale.setDataFields(arr)
Fields to use for initializing the scale (determining max/min), if different from the fields the
scale is bound to. Getter: `getDataFields()`.
- **Parameter** `arr`: array of Strings.
```js
var qscale = new LogScale();
qscale.setDataFields(["Total"]);
graph.setScale("Quantity", qscale);
```

### Scale.setFields(field)
The field(s) the Scale should be applied to. Getter: `getFields()`.
- **Parameter** `field`: a String column name.
```js
var qscale = new LogScale();
qscale.setFields(["Quantity"]);
```

### Scale.setScaleOption(value)
A scaling option for the default scaling. Combine multiple options with `|` (or). Getter:
`getScaleOption()`.
- **Parameter** `value`: `Scale.RAW` (no modification) | `Scale.NO_NULL` (remove NULL-data gaps) |
  `Scale.TICKS` (use rounded tick values, not the raw max/min data values) | `Scale.ZERO`
  (use zero as the minimum, if positive).
```js
qscale.setScaleOption(Scale.ZERO | Scale.TICKS);
```

### Scale.setSharedRange(Boolean)
For a `FacetCoord`, whether the same scale range is shared across every sub-graph, or only within
the same row (Y-axis) / column (X-axis).
- **Parameter** `Boolean`: `true` share across all sub-graphs, `false` share by row/column.

---

## CategoricalScale (extends Scale)

A nominal scale — maps categorical (non-numeric) values to physical attributes.
```js
var qscale = new CategoricalScale('State');
```
Field names can be passed to the constructor, or set later via the inherited `Scale.setFields(field)`.

### CategoricalScale.setFill(Boolean)
Scale boundaries are set equal to the extreme data values, leaving no gap at the axis edges.
- **Parameter** `Boolean`: `true` fill axis to edges, `false` leave gap at edges (default).

### CategoricalScale.setValues(value)
The categorical values in the scale, and their display order. Getter: `getValues()`.
- **Parameter** `value`: array of Strings.
```js
var sscale = new CategoricalScale("State");
sscale.setValues(["NY", "NJ"]);
```

---

## LinearScale (extends Scale)

A linear scale — linearly maps numerical values to physical attributes.
```js
var qscale = new LinearScale('Last Year', 'This Year');
```

### LinearScale.getScaleRange()
Retrieves the scale range. Setter: `setScaleRange(range)`.
- **Returns**: a `ScaleRange` object.

### LinearScale.setIncrement(value)
Interval between values displayed on the axis. Getter: `getIncrement()`.
- **Parameter** `value`: Number.

### LinearScale.setMax(value) / LinearScale.setMin(value)
Maximum / minimum value of the scale. Getters: `getMax()` / `getMin()`. If the max is set smaller
than the largest data value (to crop data), also call `elem.setInPlot(false)` — otherwise the chart
forces the full data range and leaves the axis partially unlabeled.
- **Parameter** `value`: Number.
```js
qscale.setMin(150);
qscale.setMax(450);
```
To modify an existing chart: `graph.getCoordinate().getYScale().setMin(2000000); ...setMax(15000000);`

### LinearScale.setMinorIncrement(value)
Interval between minor tick marks displayed on the axis. Getter: `getMinorIncrement()`.
- **Parameter** `value`: Number.

### LinearScale.setReversed(Boolean)
Orientation of the scale. No getter. **Call this after `setMin`/`setMax`.**
- **Parameter** `Boolean`: `true` value increases top-to-bottom, `false` bottom-to-top (default).

### LinearScale.setScaleRange(range)
The calculation strategy for finding the scale range. Getter: `getScaleRange()`.
- **Parameter** `range`: a `ScaleRange` object (`LinearRange` or `StackRange`).
```js
qscale.setScaleRange(new StackRange()); // adds 200+300
```

---

## LogScale (extends LinearScale)

A logarithmic scale — logarithmically maps numerical data to physical attributes.
```js
var qscale = new LogScale('Last Year', 'This Year');
```
Inherits everything from `LinearScale`/`Scale`.

### LogScale.setBase(value)
Base of the logarithm. Default `10`. Getter: `getBase()`.
- **Parameter** `value`: Number.

---

## PowerScale (extends LinearScale)

Maps values to physical attributes by raising them to a specified exponent.
```js
var qscale = new PowerScale('Last Year', 'This Year');
```
Inherits everything from `LinearScale`/`Scale`.

### PowerScale.setExponent(value)
The scaling exponent — axis position of data `x` is `x^value`. Getter: `getExponent()`.
- **Parameter** `value`: Number.

---

## TimeScale (extends Scale)

A time scale — linearly maps date/time values to physical attributes.
```js
var qscale = new TimeScale('Date');
```

### TimeScale.setIncrement(value)
Integer increment at which to place axis labels, in terms of the prevailing time unit (e.g. every
`12` weeks). Getter: `getIncrement()`.
- **Parameter** `value`: integer.

### TimeScale.setMax(value) / TimeScale.setMin(value)
Latest / earliest date on the scale. Getters: `getMax()` / `getMin()`. As with `LinearScale`,
cropping the max below the largest data value also needs `elem.setInPlot(false)`.
- **Parameter** `value`: a `Date` object.

### TimeScale.setType(type)
Placement of tick marks on the axis (analogous to `LinearScale`'s increment). Getter: `getType()`.
- **Parameter** `type`: `TimeScale.SECOND` | `MINUTE` | `HOUR` | `DAY` | `WEEK` | `MONTH` |
  `QUARTER` | `YEAR`.

---

## ScaleRange (base class)

The calculation strategy for finding a scale's range. Subclasses: `LinearRange`, `StackRange`.

### ScaleRange.setAbsoluteValue(Boolean)
Whether negative quantities are represented against the positive axis, or the negative axis
(default).
- **Parameter** `Boolean`: `true` show negatives on the positive axis, `false` on the negative axis
  (default).
```js
var range = new LinearRange();
range.setAbsoluteValue(true);
qscale.setScaleRange(range);
```

---

## LinearRange (extends ScaleRange)

Computes the range using the minimum and maximum data values. Adds no methods of its own.
```js
var range = new LinearRange();
qscale.setScaleRange(range);
```

---

## StackRange (extends ScaleRange)

Computes the range by "stacking" the data values.
```js
var range = new StackRange();
qscale.setScaleRange(range); // e.g. adds 200+300
```

### StackRange.setGroupField(value)
Determines the scale range from the stacked values of the *largest single group*, based on the
grouping field, rather than stacking every row together. Getter: `getGroupField()`.
- **Parameter** `value`: a String field name.
```js
var range = new StackRange();
range.setGroupField("State"); // range = max of (200+100), (400+300)
qscale.setScaleRange(range);
```

### StackRange.setStackNegative(Boolean)
Whether the negative scale range is computed by independently stacking the negative values
(default), or without stacking.
- **Parameter** `Boolean`: `true` stack negative values (default), `false` do not.

---

## General notes

- Positioning/scaling always flows: create a `Scale` → assign to a `Coordinate` (`RectCoord`,
  `PolarCoord`, etc. — or `EGraph.setScale(field, scale)` directly) → assign the `Coordinate` to
  the chart with `EGraph.setCoordinate(coord)`.
- To modify a chart already built in the Chart Editor rather than building one from scratch, walk
  down from `graph.getCoordinate()` (e.g. `.getXScale()`/`.getYScale()`) instead of constructing
  new objects — this is the pattern used throughout Chart Script Tutorial's "modify an existing
  chart" examples.
- `Rect25Coord` is a real, distinct class (3D-effect variant), not an alias — the doc site's left
  nav highlight for it visually matches `RectCoord`'s (shared submenu state), but its own page
  content is separate.
