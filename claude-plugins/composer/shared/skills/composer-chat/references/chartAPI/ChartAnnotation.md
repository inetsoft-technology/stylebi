# Chart Annotation (StyleBI Chart Script Reference)

Source: https://www.inetsoft.com/docs/stylebi/InetSoftUserDocumentation/1.0.0/chartAPI/ChartAnnotation.html

> This following pages describe functions for adding arbitrary text, shapes, and lines to a Chart.

Object hierarchy: `GraphForm` is the base class. `DefaultForm`, `LabelForm`, `LineForm`, `RectForm`,
`ShapeForm` all extend it. `TagForm` extends `LabelForm`. All forms are attached to a chart via
`EGraph.addForm(form)`.

---

## GraphForm (base class)

The GraphForm object contains information for form (i.e., shape) elements manually drawn on the chart.

### GraphForm.setColor(value)
Specifies the line and fill color of the GraphForm. Getter: `getColor()`.
- **Parameter** `value`: a `java.awt.Color` object.
```js
var form = new LineForm();
form.addPoint(new java.awt.Point(0,0));
form.addPoint(new java.awt.Point(100,100));
form.addPoint(new java.awt.Point(200,100));
form.setColor(java.awt.Color(0xff0000));
graph.addForm(form);
```

### GraphForm.setFill(Boolean)
Specifies whether the form should be filled or unfilled. No getter.
- **Parameter** `Boolean`: `true` fill the shape, `false` do not fill.
```js
var form = new LineForm();
form.addPoint(new java.awt.Point(0,0));
form.addPoint(new java.awt.Point(100,100));
form.addPoint(new java.awt.Point(200,100));
form.setFill(true);
graph.addForm(form);
```

### GraphForm.setInPlot(Boolean)
For forms using relative positioning (values or tuples), specifies whether the chart should be
resized so the form remains fully visible in the chart area. No getter.
- **Parameter** `Boolean`: `true` resize chart, `false` do not resize (crop forms).
```js
var form = new LineForm();
form.addValues(['NJ',0]);
form.addValues(['NJ',300]);
form.addValues(['NY',400]);
form.setInPlot(true);
form.setFill(true);
graph.addForm(form);
```

### GraphForm.setLine(value)
Specifies the line style used to draw the form. Getter: `getLine()`.
- **Parameter** `value`: a `Chart` line-style constant (int) — `Chart.THIN_LINE`,
  `Chart.DOT_LINE`, `Chart.DASH_LINE`, `Chart.MEDIUM_DASH`, `Chart.LARGE_DASH`. **Not** a `GLine`
  constant/instance — despite `GLine`'s own reference page listing `GraphForm.setLine(value)` as a
  consumer (see `references/chartAPI/UtilityObjects.md`), live-testing this against StyleBI throws
  `TypeError: ... Cannot convert 'inetsoft.graph.aesthetic.GLine@...' to Java type 'int'` when a
  `GLine` constant is passed. `GLine` constants/instances are for `StaticLineFrame.setLine()`
  (a chart's aesthetic line frame) instead — a different `setLine` on a different class.
```js
var form = new LineForm();
form.addPoint(new java.awt.Point(0,0));
form.addPoint(new java.awt.Point(100,100));
form.addPoint(new java.awt.Point(200,100));
form.setLine(Chart.DASH_LINE);
graph.addForm(form);
```

### GraphForm.setLineEquation(line)
Specifies the model to fit to the data. No getter.
- **Parameter** `line`: a model to fit — `ExponentialLineEquation`, `LogarithmicLineEquation`,
  `PolynomialLineEquation`, `PowerLineEquation`.
```js
var form = new LineForm();
var equation = new LogarithmicLineEquation();
form.setLineEquation(equation);
form.setColor(java.awt.Color(0xff0000));
graph.addForm(form);
```

### GraphForm.setMeasure(col)
Specifies the measure for which the form should be displayed. If the chart contains a measure of
this name, the form is displayed; otherwise it is not. Useful for `FacetCoord` charts, to show a
form only for the chart representing a particular measure. Getter: `getMeasure()`.
- **Parameter** `col`: column name (String).
```js
var form = new LabelForm();
form.setValues(['NJ',200]);
form.setLabel("NJ Sales");
form.setAlignmentX(Chart.CENTER_ALIGNMENT);
form.setMeasure("Sales");
graph.addForm(form);
```

### GraphForm.setXOffset(value)
Offset in pixels to shift the form horizontally. Positive = right, negative = left.
Getter: `getXOffset()`.
- **Parameter** `value`: integer (pixels).

### GraphForm.setYOffset(value)
Offset in pixels to shift the form vertically. Positive = up, negative = down.
Getter: `getYOffset()`.
- **Parameter** `value`: integer (pixels).
```js
var form = new LabelForm();
form.setLabel("label1");
form.setValues(['NY', 100]);
form.setXOffset(-50);
form.setYOffset(100);
graph.addForm(form);
```

### GraphForm.setZIndex(value)
Layering order for GraphForm objects — a form with a larger zIndex overlays one with a smaller
zIndex. To bring a manually-drawn shape in front of other chart elements, use a large zIndex.
Getter: `getZIndex()`.
- **Parameter** `value`: positive integer.

Default Z-Index of chart objects (larger = on top):

| Object                        | Default Z-Index |
|--------------------------------|-----------------|
| Coordinate border               | 20 |
| Grid line                       | 30 |
| Axis                            | 40 |
| Axis border                     | 50 |
| Visual object                   | 60 |
| Grid line on top of Object       | 70 |
| Form object                     | 80 |
| Facet gridline                  | 90 |
| Text                            | 100 |

```js
var form1 = new LineForm();
var form2 = new LineForm();
form1.addPoint(new java.awt.Point(0,0));
form1.addPoint(new java.awt.Point(100,100));
form1.addPoint(new java.awt.Point(200,100));
form1.setColor(java.awt.Color(0xff0000));
form1.setFill(true);
form1.setZIndex(300);
form2.addPoint(new java.awt.Point(100,0));
form2.addPoint(new java.awt.Point(150,150));
form2.addPoint(new java.awt.Point(200,100));
form2.setColor(java.awt.Color(0xffff00));
form2.setFill(true);
form2.setZIndex(200);
graph.addForm(form1);
graph.addForm(form2);
```

---

## DefaultForm

Creates a plain `GraphForm` object directly (rather than via `LabelForm`/`LineForm`/`RectForm`/
`ShapeForm`).
```js
var form = new DefaultForm();
form.setShape(new java.awt.geom.Rectangle2D.Double(100,100,200,200));
form.setFill(true);
graph.addForm(form);
```

---

## LabelForm (extends GraphForm)

Contains information for labels manually drawn on the chart.
```js
var form = new LabelForm();
```
Border color/style come from the inherited `GraphForm.setColor(value)` / `GraphForm.setLine(value)`.
See `DefaultTextFrame` to automatically use data values as element labels.

### LabelForm.setAlignmentX(value)
Horizontal alignment of the label relative to its X location (also applied to the label text).
Getter: `getAlignmentX()`.
- **Parameter** `value`: `Chart.LEFT_ALIGNMENT` | `Chart.CENTER_ALIGNMENT` | `Chart.RIGHT_ALIGNMENT`.

### LabelForm.setAlignmentY(value)
Vertical alignment of the label relative to its Y location. Getter: `getAlignmentY()`.
- **Parameter** `value`: `Chart.TOP_ALIGNMENT` | `Chart.MIDDLE_ALIGNMENT` | `Chart.BOTTOM_ALIGNMENT`.

### LabelForm.setCollisionModifier(value)
How collisions (labels occupying the same location) should be handled. Getter:
`getCollisionModifier()`.
- **Parameter** `value`: `VLabel.MOVE_NONE` (no adjustment) | `VLabel.MOVE_FREE` (move any
  direction) | `VLabel.MOVE_RIGHT` | `VLabel.MOVE_UP`.

### LabelForm.setInsets(value)
Padding in pixels surrounding the label text. Getter: `getInsets()`. Argument order:
`[top, left, bottom, right]`.
- **Parameter** `value`: a `java.awt.Insets` object.
```js
var form = new LabelForm();
form.setLabel("label1");
form.setValues(['NY', 100]);
var spec = new TextSpec();
spec.setBackground(java.awt.Color(0x00ff00));
form.setTextSpec(spec);
form.setInsets(new java.awt.Insets(0,15,0,15));
graph.addForm(form);
```

### LabelForm.setLabel(value)
Text of the label (use `\n` to insert a newline). Getter: `getLabel()`. See `DefaultTextFrame` to
automatically use data values as element labels.
- **Parameter** `value`: String.

### LabelForm.setPoint(value)
Pixel location (integer values) or proportional location (fractional values) for the label.
Positive values = distance from left/bottom; negative = distance from right/top. Getter:
`getPoint()`.
- **Parameter** `value`: a subclass of `java.awt.geom.Point2D` — `java.awt.Point` for pixels,
  `java.awt.geom.Point2D.Double` for proportion.
```js
var form1 = new LabelForm();
var form2 = new LabelForm();
form1.setLabel("label1");
form2.setLabel("label2");
form1.setPoint(new java.awt.Point(50, 100));           // pixels
form2.setPoint(new java.awt.geom.Point2D.Double(.5,.7)); // proportion
graph.addForm(form1);
graph.addForm(form2);
```

### LabelForm.setTextSpec(spec)
Label text attributes: color, font, format, etc. Getter: `getTextSpec()`.
- **Parameter** `spec`: a `TextSpec` object.
```js
var form = new LabelForm();
form.setLabel("label1");
form.setValues(['NY', 100]);
var spec = new TextSpec();
spec.setColor(new java.awt.Color(0xff0000));
form.setTextSpec(spec);
graph.addForm(form);
```

### LabelForm.setTuple(value)
Point in **logical space** for the label text, relative to the prevailing axis scaling. Getter:
`getTuple()`.
- **Parameter** `value`: an `[X, Y]` pair.
```js
var form = new LabelForm();
form.setLabel("label1");
form.setTuple([0, 100]);
graph.addForm(form);
```

### LabelForm.setValues(value)
Location of the label using coordinate values (numeric or categorical), relative to the prevailing
axis scaling. For a categorical X-axis, the X value must be a categorical value (e.g. `'NJ'`).
Getter: `getValues()`.
- **Parameter** `value`: an `[X, Y]` pair.
```js
var form = new LabelForm();
form.setLabel("label1");
form.setValues(['NY', 100]);
graph.addForm(form);
```

---

## LineForm (extends GraphForm)

Contains information for lines manually drawn on the chart.
```js
var line = new LineForm();
```

### LineForm.addPoint(value)
Pixel location (integer) or proportional location (fractional) of a point on the line. Positive =
distance from left/bottom; negative = distance from right/top.
- **Parameter** `value`: a subclass of `java.awt.geom.Point2D`.
```js
var form1 = new LineForm();
var form2 = new LineForm();
form1.addPoint(new java.awt.Point(100, 0));
form1.addPoint(new java.awt.Point(100, 200));
form1.addPoint(new java.awt.Point(200,100));
form1.setColor(java.awt.Color(0xff0000));
form2.addPoint(new java.awt.geom.Point2D.Double(.5,0));
form2.addPoint(new java.awt.geom.Point2D.Double(.5,.7));
form2.addPoint(new java.awt.geom.Point2D.Double(.7,.5));
form2.setColor(java.awt.Color(0xff00ff));
graph.addForm(form1);
graph.addForm(form2);
```

### LineForm.addTuple(value)
A point defining the line in **logical space**, relative to the prevailing axis scaling.
- **Parameter** `value`: an `[X, Y]` pair.
```js
var form = new LineForm();
form.addTuple([.5, 0]);
form.addTuple([.5, 200]);
form.addTuple([1,100]);
form.setColor(java.awt.Color(0xff0000));
graph.addForm(form);
```

### LineForm.addValues(value)
A point defining the line, relative to axis scaling prior to transformation. For a categorical
X-axis, use a categorical value (e.g. `'NJ'`).
- **Parameter** `value`: an `[X, Y]` pair.
```js
var form = new LineForm();
form.addValues(['NJ', 0]);
form.addValues(['NJ', 200]);
form.addValues(['NY', 100]);
form.setColor(java.awt.Color(0xff0000));
graph.addForm(form);
```

### LineForm.setEndArrow(Boolean)
Whether to draw an arrow at the end of the line (the last point specified). Getter:
`isEndArrow()`.
- **Parameter** `Boolean`: `true` draw arrow, `false` no arrow.

### LineForm.setStartArrow(Boolean)
Whether to draw an arrow at the start of the line (the first point specified). Getter:
`isStartArrow()`.
- **Parameter** `Boolean`: `true` draw arrow, `false` no arrow.
```js
var form = new LineForm();
form.addPoint(new java.awt.Point(0,0));
form.addPoint(new java.awt.Point(100,100));
form.setStartArrow(true);
form.setEndArrow(true);
form.setColor(java.awt.Color(0xff0000));
graph.addForm(form);
```

---

## RectForm (extends GraphForm)

Contains information for rectangles manually drawn on the chart.
```js
var rect = new RectForm();
```

### RectForm.setTopLeftPoint(value) / RectForm.setBottomRightPoint(value)
Pixel (integer) or proportional (fractional) location of the top-left / bottom-right corner.
Positive = distance from left/bottom; negative = distance from right/top. Getters:
`getTopLeftPoint()` / `getBottomRightPoint()`.
- **Parameter** `value`: a subclass of `java.awt.geom.Point2D`.
```js
var rect1 = new RectForm();
var rect2 = new RectForm();
rect1.setTopLeftPoint(new java.awt.Point(100, 100));
rect1.setBottomRightPoint(new java.awt.Point(150, 50));
rect1.setColor(java.awt.Color(0xff0000));
rect2.setTopLeftPoint(new java.awt.geom.Point2D.Double(.5,.8));
rect2.setBottomRightPoint(new java.awt.geom.Point2D.Double(.8,.5));
rect2.setColor(java.awt.Color(0xff00ff));
graph.addForm(rect1);
graph.addForm(rect2);
```

### RectForm.setTopLeftTuple(value) / RectForm.setBottomRightTuple(value)
Point in **logical space** for the top-left / bottom-right corner, relative to axis scaling.
Getters: `getTopLeftTuple()` / `getBottomRightTuple()`.
- **Parameter** `value`: an `[X, Y]` pair.
```js
var rect = new RectForm();
rect.setTopLeftTuple([1,200]);
rect.setBottomRightTuple([2,50]);
rect.setColor(java.awt.Color(0xff0000));
graph.addForm(rect);
```

### RectForm.setTopLeftValues(value) / RectForm.setBottomRightValues(value)
Point for the top-left / bottom-right corner, relative to axis scaling prior to transformation.
For a categorical X-axis, use a categorical value. Getters: `getTopLeftValues()` /
`getBottomRightValues()`.
- **Parameter** `value`: an `[X, Y]` pair.
```js
var rect = new RectForm();
rect.setTopLeftValues(['NJ',200]);
rect.setBottomRightValues(['NY',50]);
rect.setColor(java.awt.Color(0xff0000));
graph.addForm(rect);
```

> Note: once a chart already exists in the Composer, you do not need to add a Chart element again
> in script — just create/configure the form and call `graph.addForm(form)`.

---

## ShapeForm (extends GraphForm)

Contains information for shapes manually drawn on the chart.

### ShapeForm.setAlignmentX(value) / ShapeForm.setAlignmentY(value)
Horizontal / vertical alignment of the shape relative to its X/Y position. Getters:
`getAlignmentX()` / `getAlignmentY()`.
- **Parameter** `value`: `Chart.LEFT_ALIGNMENT`/`Chart.CENTER_ALIGNMENT`/`Chart.RIGHT_ALIGNMENT`
  (X) or `Chart.TOP_ALIGNMENT`/`Chart.MIDDLE_ALIGNMENT`/`Chart.BOTTOM_ALIGNMENT` (Y).

### ShapeForm.setPoint(value)
Pixel or proportional location where the shape is placed. Getter: `getPoint()`.
- **Parameter** `value`: a subclass of `java.awt.geom.Point2D`.
```js
var shape1 = new ShapeForm();
var shape2 = new ShapeForm();
shape1.setPoint(new java.awt.Point(150, 100));
shape1.setShape(GShape.FILLED_TRIANGLE);
shape1.setColor(java.awt.Color(0xff0000));
shape1.setSize(new java.awt.Dimension(10,10));
shape2.setPoint(new java.awt.geom.Point2D.Double(.5,.5));
shape2.setShape(GShape.FILLED_TRIANGLE);
shape2.setColor(java.awt.Color(0xff0000));
shape2.setSize(new java.awt.Dimension(20,20));
graph.addForm(shape1);
graph.addForm(shape2);
```

### ShapeForm.setRotation(value)
Shape rotation in degrees. Getter: `getRotation()`.
- **Parameter** `value`: Number.

### ShapeForm.setShape(shape)
Type of shape. Getter: `getShape()`.
- **Parameter** `shape`: a `GShape` constant, or a `GShape.ImageShape` for a custom image.
```js
// Built-in shape
var form = new ShapeForm();
form.setValues(['NJ',100]);
form.setShape(GShape.FILLED_TRIANGLE);
form.setColor(java.awt.Color(0xff0000));
form.setSize(new java.awt.Dimension(50,50));
graph.addForm(form);

// Image shape
var logo = getImage("https://www.inetsoft.com/images/home/logo.gif");
var shape = new GShape.ImageShape();
shape.setImage(logo);
var form2 = new ShapeForm();
form2.setValues(['NJ',100]);
form2.setShape(shape);
form2.setSize(new java.awt.Dimension(150,40));
graph.addForm(form2);
```

### ShapeForm.setSize(value)
Size of the shape in pixels. Getter: `getSize()`.
- **Parameter** `value`: a `java.awt.Dimension` object.

### ShapeForm.setTuple(value)
Location of the shape in **logical space**, relative to axis scaling. Getter: `getTuple()`.
- **Parameter** `value`: an `[X, Y]` pair.

### ShapeForm.addValues(value)  *(a.k.a. setValues)*
Location of the shape, relative to axis scaling prior to transformation. For a categorical X-axis,
use a categorical value. Getter: `getValues()`.
- **Parameter** `value`: an `[X, Y]` pair.

---

## TagForm (extends LabelForm)

A tag is a label associated with a particular object, automatically positioned to avoid other
objects. Adds no new methods of its own — uses the inherited `LabelForm.setLabel(value)` (text) and
`LabelForm.setValues(value)` (position).
```js
var tag = new TagForm();
```
```js
dataset = [["State","Quantity"],["NJ",200],["NY",300],["NY",305]];
graph = new EGraph();
var elem = new PointElement("State", "Quantity");
var form1 = new TagForm();
var form2 = new TagForm();
form1.setLabel("label1");
form1.setValues(['NY', 300]);
form2.setLabel("label2");
form2.setValues(['NY', 305]);
graph.addForm(form1);
graph.addForm(form2);
graph.addElement(elem);
```

---

## General notes

- All annotation script code runs as **Add Component Script** on the Chart component itself
  (has access to Chart data + Chart API). Ad Hoc Editing should be disabled in Chart Properties for
  scripted charts.
- `graph.addForm(form)` is the one call that actually attaches any of these form objects to the
  chart (`EGraph.addForm(form)`).
- Positioning methods come in three flavors across the form types, all meaning different coordinate
  systems:
  - `*Point(value)` — pixel (`java.awt.Point`) or proportional (`java.awt.geom.Point2D.Double`,
    0–1) screen-space placement.
  - `*Tuple(value)` — an `[X,Y]` pair in **logical/axis space** (post current axis scaling).
  - `*Values(value)` — an `[X,Y]` pair matched against the **data values** (categorical dimensions
    use the literal category string, e.g. `'NJ'`).
