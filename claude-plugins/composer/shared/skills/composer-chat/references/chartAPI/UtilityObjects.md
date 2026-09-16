# Utility Objects (StyleBI Chart Script Reference)

Source: https://www.inetsoft.com/docs/stylebi/InetSoftUserDocumentation/1.0.0/chartAPI/UtilityObjects.html

> The following sections describe objects which provide useful constants.

---

## GLine

Source: https://www.inetsoft.com/docs/stylebi/InetSoftUserDocumentation/1.0.0/chartAPI/GLine.html

Line style for the built-in styles available as Chart Constants. Used by `StaticLineFrame` (see
`references/chartAPI/ChartAesthetics.md`). **Not** used by `GraphForm.setLine(value)` (see
`references/chartAPI/ChartAnnotation.md`) despite this object's own upstream reference page
listing it as a consumer — live-testing found `GraphForm.setLine()` only accepts a `Chart`
line-style constant (an int), and throws a `TypeError` when given a `GLine` constant/instance.

Predefined constants:
```
GLine.THIN_LINE
GLine.DOT_LINE
GLine.DASH_LINE
GLine.MEDIUM_DASH
GLine.LARGE_DASH
```

You can also construct a `GLine`:
- From a `Chart` constant (see Line Style in `references/commonscript/UserFunctions.md`):
  `var line = new GLine(Chart.DOT_LINE);`
- User-defined, from a dash size and width (both `double`):
  `var line = new GLine(dashsize, width);`

```js
// Example 1 — apply to a LineElement via StaticLineFrame
dataset = [["State", "Quantity"], ["NJ",200], ["NY",300], ["PA",100]];
graph = new EGraph();
var elem = new LineElement("State", "Quantity");
var frame = new StaticLineFrame();
frame.setLine(GLine.DASH_LINE);
// or frame.setLine(new GLine(10,5));
elem.setLineFrame(frame);
graph.addElement(elem);
```
```js
// Example 2 — control an existing chart's LineFrame via bindingInfo, in onRefresh
Chart1.bindingInfo.lineFrame = new StaticLineFrame;
Chart1.bindingInfo.lineFrame.line = GLine(20,10);
```
Dashboard script that modifies `bindingInfo` should generally be placed in the `onRefresh` handler.

---

## GShape

Source: https://www.inetsoft.com/docs/stylebi/InetSoftUserDocumentation/1.0.0/chartAPI/GShape.html

A set of shapes. For element properties requiring a `GShape`, pass a `GShape.ImageShape`
(user-defined image, see below) or one of these constants:
```
GShape.ARROW           GShape.ARROWBAR
GShape.CIRCLE          GShape.CROSS
GShape.DIAMOND         GShape.FILLED_ARROW
GShape.FILLED_ARROWBAR GShape.FILLED_CIRCLE
GShape.FILLED_DIAMOND  GShape.FILLED_SQUARE
GShape.FILLED_TRIANGLE GShape.HYPHEN
GShape.LINE            GShape.LSHAPE
GShape.SQUARE          GShape.STAR
GShape.STICK           GShape.TRIANGLE
GShape.VSHAPE          GShape.XSHAPE
GShape.NIL (no shape)
```
Used by `ShapeForm` (decorative shapes, see `references/chartAPI/ChartAnnotation.md`) and by
`StaticShapeFrame` (see `references/chartAPI/ChartAesthetics.md`).

Methods:
- `GShape.create(outline, fill)`
- `GShape.setFillColor(color)`

### GShape.ImageShape

A user-defined bitmap image used as a shape. Methods:
- `GShape.ImageShape.setAlignment(value)`
- `GShape.ImageShape.setImage(image)`
- `GShape.ImageShape.setTile(Boolean)`

---

## GTexture

Source: https://www.inetsoft.com/docs/stylebi/InetSoftUserDocumentation/1.0.0/chartAPI/GTexture.html

A texture pattern, e.g. `GTexture.PATTERN_5`. The page presents the available patterns as a visual
swatch grid (not enumerable as text) — see `StaticTextureFrame` in
`references/chartAPI/ChartAesthetics.md` for how to apply a static texture, and check the live doc
page for the actual `PATTERN_n` values/swatches when a specific pattern is needed.

---

## SVGShape (a.k.a. GShape.ImageShape on the doc site's nav)

Source: https://www.inetsoft.com/docs/stylebi/InetSoftUserDocumentation/1.0.0/chartAPI/SVGShape.html

A set of built-in SVG shapes, for use with `StaticShapeFrame` and `ShapeForm`. Constants:
```
SVGShape.CHECK        SVGShape.DOWN_ARROW
SVGShape.FACE_BLANK   SVGShape.FACE_HAPPY
SVGShape.FACE_OK      SVGShape.FACE_SAD
SVGShape.FACE_SMILE   SVGShape.FEMALE
SVGShape.LEFT_ARROW   SVGShape.MALE
SVGShape.MINUS        SVGShape.PLUS
SVGShape.RIGHT_ARROW  SVGShape.STAR
SVGShape.SUN          SVGShape.UP_ARROW
SVGShape.WARNING      SVGShape.X
SVGShape.NIL (no shape)
```
You can also create a custom `SVGShape` from an SVG image on the local file system or a server.

```js
// Example 1 — built-in SVG shape constant
dataset = [["State","Quantity"], ["NJ",200], ["NY",300]];
graph = new EGraph();
var elem = new PointElement("State","Quantity");
var shapeFrame = new StaticShapeFrame(SVGShape.FACE_HAPPY);
var sizeFrame = new StaticSizeFrame(10);
elem.setSizeFrame(sizeFrame);
elem.setShapeFrame(shapeFrame);
graph.addElement(elem);
```
```js
// Example 2 — local SVG file
var svg = new SVGShape("file:\\C:/HappyFaceSVG.svg");
var shapeFrame = new StaticShapeFrame(svg);
```
```js
// Example 3 — remote SVG URL
var svg = new SVGShape("https://www.w3.org/Icons/SVG/svg-logo.svg");
var shapeFrame = new StaticShapeFrame(svg);
```
In all three, use `GraphElement.setShapeFrame(frame)` to attach the `ShapeFrame` to the element
(e.g. a `PointElement`).

---

## Line-fit equations

Source (index): https://www.inetsoft.com/docs/stylebi/InetSoftUserDocumentation/1.0.0/chartAPI/ObjectHierarchy.html (linked from `GraphForm.setLineEquation(line)`, see `references/chartAPI/ChartAnnotation.md`)

Each is passed to `GraphForm.setLineEquation(line)` on a `LineForm`, then added via
`EGraph.addForm(form)`:

```js
var form = new LineForm();
var equation = new ExponentialLineEquation(); // or Logarithmic/Power/Polynomial below
form.setLineEquation(equation);
form.setColor(java.awt.Color(0xff0000));
graph.addForm(form);
```

- **ExponentialLineEquation** — exponential data fit. `new ExponentialLineEquation()`.
- **LogarithmicLineEquation** — logarithmic data fit. `new LogarithmicLineEquation()`.
- **PowerLineEquation** — power data fit. `new PowerLineEquation()`.
- **PolynomialLineEquation** — polynomial data fit, one of three degrees:
  - `new PolynomialLineEquation.Linear()`
  - `new PolynomialLineEquation.Quadratic()`
  - `new PolynomialLineEquation.Cubic()`

---

## Special Chart Functions

Source: https://www.inetsoft.com/docs/stylebi/InetSoftUserDocumentation/1.0.0/chartAPI/SpecialChartFunctions.html

> Special-purpose charting functions. Each returns a ready-made `EGraph`-like chart object built
> from data-worksheet column names — assign the result directly to `graph` in the Chart Component
> Script rather than building the chart up with `EGraph`/elements/coordinates by hand.

All parameters name **columns from the bound data worksheet**, not literal values (except
`color`/`opts`).

### createBulletGraph(measure, ranges, target, color, xdims, ydims, opts)
Generates a bullet graph showing a measure alongside a target and multiple value ranges. All
parameters are optional — pass `null` to omit one.
- `measure` — column providing the measure values.
- `ranges` — array of column names providing range values (shaded regions).
- `target` — column providing the target value (vertical bar).
- `color` — a scalar hex color (`0x0000FF`), or an array `[measure, target, range1, range2, range3, …]`.
- `xdims` / `ydims` — arrays of column names used as X-axis / Y-axis dimensions.
- `opts` — options string: `'vertical=false'` (force horizontal bars), `'ylabel=false'`,
  `'xlabel=false'`, `'vlabel=false'` (hide y-dim/x-dim/measure-value labels respectively).
```js
dataset = viewsheet['Query1'];
graph = createBulletGraph('Total', ['range1','range2','range3'], 'Target', 0x0000FF,
  ['Company'], ['Employee'], 'vertical=false');
```

### createTreeMap(colorDim, sizeCol, treeDims, [xdims, ydims])
Generates a Treemap chart.
- `colorDim` — dimension column mapped to color.
- `sizeCol` — measure column determining box size.
- `treeDims` — array of dimension columns forming the treemap hierarchy.
- `xdims` / `ydims` — dimensions (from `colorDim`/`treeDims`) to place on the X/Y axis.
```js
dataset = viewsheet['Query1'];
graph = createTreemap("state", "price", ["state", "city", "product_name"]);
```

### createCirclePackingGraph(colorDim, sizeCol, textCol, treeDims, [xdims, ydims])
Generates a Circle Packing chart. Same as `createTreeMap` plus `textCol` — a dimension column
supplying the group labels.
```js
dataset = viewsheet['Query1'];
graph = createCirclePackingGraph("_", "price", "state", ["state", "city", "product_name"]);
```

### createIcicleGraph(colorDim, sizeCol, treeDims, [xdims, ydims])
Generates an Icicle chart. Same parameter shape as `createTreeMap`.
```js
dataset = viewsheet['Query1'];
graph = createIcicleGraph('state', "price", ["state", "city", "product_name"]);
```

### createMekkoGraph(xdim, innerDim, measure, colorDim, textCols)
Generates a Marimekko chart.
- `xdim` — dimension placed on the X-axis.
- `innerDim` — dimension used to break out each X column.
- `measure` — measure (with aggregation) represented on the chart.
- `colorDim` — dimension column mapped to color.
- `textCols` — array of dimension columns to render as labels.
```js
dataset = viewsheet['Query1'];
graph = createMekkoGraph("state", "city", "price", "state");
```

### createSunburstGraph(colorDim, sizeCol, treeDims, xdims, ydims)
Generates a Sunburst chart. Same parameter shape as `createTreeMap`.
```js
dataset = viewsheet['Query1'];
graph = createSunburstGraph("state", "price", ["state", "city", "product_name"]);
```

All six: script that builds/assigns `graph` this way belongs in the Chart Component Script (has
access to Chart data and Chart API methods); disable "Enable Ad Hoc Editing" in Chart Properties
for scripted charts. The source `Data Worksheet` referenced in the official examples ('All Sales')
ships in the product's sample `Data Worksheet Sample Queries` folder (via `examples.zip` on
GitHub) — not something to assume exists in an arbitrary deployment.
