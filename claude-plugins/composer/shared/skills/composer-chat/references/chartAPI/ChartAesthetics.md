# Chart Visuals (StyleBI Chart Script Reference)

Source: https://www.inetsoft.com/docs/stylebi/InetSoftUserDocumentation/1.0.0/chartAPI/ChartAesthetics.html

> This following pages present the VisualFrame objects that can be added to Chart elements to
> introduce visual style. VisualFrame objects allow you to represent additional data dimensions by
> using the physical attributes of Chart elements, or to apply a fixed (static) visual style.

Every visual attribute (color, shape, size, line style, texture, text label) is controlled by a
`VisualFrame` subclass assigned to a `GraphElement` via `setColorFrame`/`setShapeFrame`/
`setSizeFrame`/`setLineFrame`/`setTextureFrame`/`setTextFrame`. Frames come in three flavors per
attribute: **Categorical** (a distinct value per discrete value), **Linear/Gradient/Brightness/
Saturation/Circular/RGBCube/Heat/Rainbow/Bipolar** (a continuous mapping for numeric data), and
**Static** (a fixed value, or explicit per-row values read from a column).

---

## VisualFrame (base class)

Contains common properties for all aesthetic frames — `ColorFrame`, `SizeFrame`, `ShapeFrame`,
`TextFrame`, `LineFrame`, `TextureFrame` all extend it.

### VisualFrame.setField(field)
Specifies the field (column) associated with this VisualFrame. Getter: `getField()`.
- **Parameter** `field`: name of the column (String).
```js
var elem = new IntervalElement("State", "Quantity");
var frame = new BrightnessColorFrame();
frame.setField("Quantity");
frame.setColor(java.awt.Color(0xff0000));
elem.setColorFrame(frame);
graph.addElement(elem);
```

### VisualFrame.setScale(scale) / VisualFrame.getScale()
The `Scale` object associated with this frame (controls how data values map onto the visual
range — see `references/chartAPI/ChartCoordinates.md`). Getter/setter pair.
```js
var frame = new BrightnessColorFrame();
frame.setField("Quantity");
var scale = new LinearScale();
scale.setMax(325);
scale.setMin(175);
frame.setScale(scale);
```

### VisualFrame.setLegendSpec(spec) / VisualFrame.getLegendSpec()
Formatting for the legend generated for this VisualFrame. Getter/setter pair.
- **Parameter** `spec`: a `LegendSpec` object.
```js
var frame = new LinearSizeFrame();
var spec = new LegendSpec();
spec.setBorderColor(java.awt.Color(0xff0000));
frame.setField("Quantity");
frame.setLegendSpec(spec);
```

### VisualFrame.setScaleOption(value)
A scaling option for the default scaling. Getter: `getScaleOption()`. Combine options with `|`
(e.g. `frame.setScaleOption(Scale.ZERO | Scale.TICKS)`).
- **Parameter** `value`: `Scale.TICKS` (use rounded tick min/max instead of actual data min/max) |
  `Scale.ZERO` (use zero as the minimum rather than the minimum data value, if positive).
```js
var frame = new GradientColorFrame();
frame.setField("Quantity");
frame.setScaleOption(Scale.TICKS);
elem.setColorFrame(frame);
```

**Two ways to reach a frame that already exists on a Chart built in the Chart Editor** (this
pattern recurs on nearly every frame class below, so it's stated once here instead of per class):
- **Getter chain**: `var elem = graph.getElement(0); var frame = elem.getColorFrame();` (or
  `getShapeFrame()`/`getSizeFrame()`/`getLineFrame()`/`getTextureFrame()`/`getTextFrame()`) then
  call setters on `frame` directly — placed in **Chart Component Script**.
- **bindingInfo property syntax**: `Chart1.bindingInfo.colorFrame = new BrightnessColorFrame;
  Chart1.bindingInfo.colorFrame.color = 0xFF0000;` — a chart's `bindingInfo.<frame>` properties
  can be read/assigned directly as plain values (hex number, color name string, `[r,g,b]` array,
  `{r,g,b}` JSON, or a `java.awt.Color`/`GLine`/`GShape`/`GTexture` constant) without calling the
  frame's own setter methods — placed in the viewsheet's **onRefresh** handler (see Advanced
  Dashboard Scripting), not the chart component script.

---

## Color

### ColorFrame (base class)
Color treatment for visual chart elements — color-codes a data dimension, or applies a fixed
color. Subclasses: `BrightnessColorFrame`, `SaturationColorFrame`, `BipolarColorFrame`,
`StaticColorFrame`, `CircularColorFrame`, `GradientColorFrame`, `HeatColorFrame`,
`RainbowColorFrame`, `CategoricalColorFrame`.

### BrightnessColorFrame
Continuous color frame returning varying brightnesses of one color.
```js
var frame = new BrightnessColorFrame('Quantity');
```
#### BrightnessColorFrame.setColor(value)
The color whose brightness is varied. No getter shown.
- **Parameter** `value`: a `java.awt.Color` object (property syntax also accepts hex number,
  color-name string, `[r,g,b]`, `{r,g,b}`, or a `java.awt.Color.XXX` constant).
```js
var frame = new BrightnessColorFrame();
frame.setField("Quantity");
frame.setColor(java.awt.Color(0xff0000));
elem.setColorFrame(frame);
```

### SaturationColorFrame
Continuous color frame returning varying saturations of one color.
```js
var frame = new SaturationColorFrame('Quantity');
```
#### SaturationColorFrame.setColor(value)
The color whose saturation is varied. Getter: `getColor()`.
```js
var frame = new SaturationColorFrame();
frame.setField("Quantity");
frame.setColor(java.awt.Color(0xff0000));
elem.setColorFrame(frame);
```

### BipolarColorFrame
Continuous color frame returning gradations between two colors (fixed palette, no extra methods
of its own).
```js
var frame = new BipolarColorFrame('Quantity');
elem.setColorFrame(frame);
```

### StaticColorFrame
A fixed color, or explicit per-row color/number data read from the `setField` column.
```js
var frame = new StaticColorFrame();               // no color yet
var frame = new StaticColorFrame(java.awt.Color(0xFF00FF)); // color via constructor
```
#### StaticColorFrame.setColor(value)
Static color for positive field values (if the `setField` column holds numbers/colors, those win
instead). Getter: `getColor()`.
```js
var frame = new StaticColorFrame();
frame.setColor(java.awt.Color(0x00ff00));
elem.setColorFrame(frame);
```
#### StaticColorFrame.setNegativeColor(value)
Static color for negative field values — when set, `setColor` covers positives and this covers
negatives, and the `setField` column is ignored entirely. Getter: `getNegativeColor()`.
```js
var frame = new StaticColorFrame();
frame.setField("Quantity");
frame.setColor(java.awt.Color(0x00ff00));
frame.setNegativeColor(java.awt.Color(0xff0000));
elem.setColorFrame(frame);
```

### CircularColorFrame
Continuous color frame returning gradations from the full spectrum (no extra methods of its own).
```js
var frame = new CircularColorFrame('Quantity');
elem.setColorFrame(frame);
```

### GradientColorFrame
Continuous color frame returning gradations between two explicit colors.
```js
var frame = new GradientColorFrame();
```
#### GradientColorFrame.setFromColor(value) / GradientColorFrame.setToColor(value)
Starting color (for the lowest value) / ending color (for the highest value) of the gradient.
Getters: `getFromColor()` / `getToColor()`.
```js
var frame = new GradientColorFrame();
frame.setFromColor(java.awt.Color(0x000000));
frame.setToColor(java.awt.Color(0xff0000));
frame.setField("Quantity");
elem.setColorFrame(frame);
```

### HeatColorFrame
Continuous color frame returning varying shades of brown (no extra methods of its own).
```js
var frame = new HeatColorFrame('Quantity');
elem.setColorFrame(frame);
```

### RainbowColorFrame
Continuous color frame returning colors of the rainbow (no extra methods of its own).
```js
var frame = new RainbowColorFrame('Quantity');
elem.setColorFrame(frame);
```

### RGBCubeColorFrame
Continuous color frame drawn from a range of the RGB color cube between two boundary colors
(no extra methods of its own — the range is passed to the constructor).
```js
var frame = new RGBCubeColorFrame([[0,0,0],[.5,1,1]]);
frame.setField("Quantity");
elem.setColorFrame(frame);
```

### CategoricalColorFrame
A distinct color for each unique value in the bound field.
```js
var frame = new CategoricalColorFrame('State');
```
#### CategoricalColorFrame.getColor(val) / CategoricalColorFrame.setColor(val,color)
Get/assign the color for one specific value.
- **Parameters** `val`: a data value. `color`: a `java.awt.Color`.
```js
frame = new CategoricalColorFrame();
frame.setField("State");
frame.setColor('NJ',java.awt.Color(0xff0000));
var NJcolor = frame.getColor('NJ');
frame.setColor('NY',NJcolor);
elem.setColorFrame(frame);
```
#### CategoricalColorFrame.init(val[,color])
Initialize the frame with a set of categorical values and (optionally) their colors in one call.
- **Parameters** `val`: array of categorical values, or a field name. `color`: array of colors
  matching `val`.
```js
var cframe = new CategoricalColorFrame();
cframe.init(["Quantity","Total"],[java.awt.Color(0xff00ff),java.awt.Color(0x00ffff)]);
elem.setColorFrame(cframe);
elem2.setColorFrame(cframe);
```

---

## Shape

### ShapeFrame (base class)
Shape style for visual chart objects — shape-codes a data dimension, or applies a fixed shape.
Subclasses: `OvalShapeFrame`, `FillShapeFrame`, `OrientationShapeFrame`, `PolygonShapeFrame`,
`TriangleShapeFrame`, `CategoricalShapeFrame`, `StaticShapeFrame`, and the `MultiShapeFrame` family
below.

### StaticShapeFrame
A fixed shape, or explicit per-row shape-name data read from the `setField` column.
```js
var frame = new StaticShapeFrame('GShape.CIRCLE');           // GShape/ImageShape constant
var frame = new StaticShapeFrame(SVGShape.FACE_HAPPY);        // an SVGShape constant
```
#### StaticShapeFrame.setShape(shape)
Static shape for graphical elements (a per-row shape-name value in the `setField` column wins
instead, if present). Getter: `getShape()`.
- **Parameter** `shape`: a `GShape`, `GShape.ImageShape`, or `SVGShape` constant.
```js
var frame = new StaticShapeFrame();
frame.setShape(GShape.CROSS);
elem.setShapeFrame(frame);
```

### CategoricalShapeFrame
A distinct shape for each unique value.
```js
var frame = new CategoricalShapeFrame('State');
```
#### CategoricalShapeFrame.setShape(val,shape)
Assign a shape to a specific value. Getter: `getShape(val)`.
- **Parameters** `val`: a data value. `shape`: a `GShape` or `GShape.ImageShape` object.
```js
shapeframe = new CategoricalShapeFrame("State");
shapeframe.setShape('NJ',GShape.FILLED_CIRCLE);
shapeframe.setShape('NY',GShape.FILLED_DIAMOND);
elem.setShapeFrame(shapeframe);
```

### OvalShapeFrame
Shape styles for oval elements of varying aspect ratio.
```js
var frame = new OvalShapeFrame('Total');
```
#### OvalShapeFrame.setFill(Boolean)
Whether the ovals are filled.
```js
var frame = new OvalShapeFrame("Total");
frame.setFill(true);
elem.setShapeFrame(frame);
```

### FillShapeFrame
Shape styles for oval elements with a variable degree of fill (no extra methods of its own).
```js
var shapeframe = new FillShapeFrame("Total");
elem.setShapeFrame(shapeframe);
```

### OrientationShapeFrame
Shape styles for line elements with variable orientation (no extra methods of its own).
```js
var shapeframe = new OrientationShapeFrame("Total");
elem.setShapeFrame(shapeframe);
```

### PolygonShapeFrame
Shape styles for elements with a varying number of sides.
```js
var frame = new PolygonShapeFrame('Total');
```
#### PolygonShapeFrame.setFill(Boolean)
Whether the polygonal elements are filled.
```js
var frame = new PolygonShapeFrame("Total");
frame.setFill(true);
elem.setShapeFrame(frame);
```

### TriangleShapeFrame
Shape styles for isosceles-trapezoid elements with varying width ratios.
```js
var frame = new TriangleShapeFrame('Total');
```
#### TriangleShapeFrame.setFill(Boolean)
Whether the triangular elements are filled.
```js
var frame = new TriangleShapeFrame("Total");
frame.setFill(true);
elem.setShapeFrame(frame);
```

### MultiShapeFrame (base for multi-dimensional shapes)
Shape styles driven by multiple dimension columns at once. Subclasses: `BarShapeFrame`,
`PieShapeFrame`, `ProfileShapeFrame`, `StarShapeFrame`, `SunShapeFrame`, `ThermoShapeFrame`,
`VineShapeFrame`.
```js
var frame = new MultiShapeFrame("m1","m2","m3");
```
#### MultiShapeFrame.setFields(arr)
Columns supplying the shape data. Getter: `getFields()`.
- **Parameter** `arr`: array of column-name Strings.
```js
var frame = new StarShapeFrame();
frame.setFields(["m1", "m2", "m3"]);
elem.setShapeFrame(frame);
```
#### MultiShapeFrame.setScales(arr)
Scale to use for each shape field (in `setFields` order). Getter: `getScales()`.
- **Parameter** `arr`: array of `Scale` objects.
```js
var frame = new StarShapeFrame();
var yscale = new LinearScale("Quantity");
yscale.setMax(500);
frame.setFields(["m1", "m2", "m3"]);
var scale1 = new LinearScale("m1"); scale1.setMax(10);
var scale2 = new LinearScale("m2"); scale2.setMax(10);
var scale3 = new LinearScale("m3"); scale3.setMax(10);
frame.setScales([scale1, scale2, scale3]);
elem.setShapeFrame(frame);
```

#### BarShapeFrame
Multidimensional "mini-bar chart" elements — dimensions map to bar heights left-to-right, in
`setFields` order (no methods of its own beyond the inherited ones above).
```js
var frame = new BarShapeFrame("m1","m2","m3");
frame.setFields(["m1","m2","m3"]);
elem.setShapeFrame(frame);
```

#### PieShapeFrame
Multidimensional "mini-pie" elements for use with `PointElement` — dimension values map
proportionately to pie-slice area.
```js
var frame = new PieShapeFrame("m1","m2","m3");
frame.setFields(["m1","m2","m3"]);
elem.setShapeFrame(frame);
```

#### ProfileShapeFrame
Multidimensional "mini-line chart" elements.
```js
var frame = new ProfileShapeFrame("m1","m2","m3");
frame.setFields(["m1","m2","m3"]);
elem.setShapeFrame(frame);
```

#### StarShapeFrame
Multidimensional "star" (closed line) elements.
```js
var frame = new StarShapeFrame("m1","m2","m3");
frame.setFields(["m1","m2","m3"]);
elem.setShapeFrame(frame);
```

#### SunShapeFrame
Multidimensional "sun" (radial line) elements — dimension values map proportionately to segment
length.
```js
var frame = new SunShapeFrame("m1","m2","m3");
frame.setFields(["m1","m2","m3"]);
elem.setShapeFrame(frame);
```

#### ThermoShapeFrame
Two-dimensional "thermometer" elements with a fill level and box width, in
`[fill level, box width]` order.
```js
var frame = new ThermoShapeFrame("Height", "Weight");
var hscale = new LinearScale(); hscale.setMin(0); hscale.setMax(100);
var wscale = new LinearScale(); wscale.setMin(0); wscale.setMax(5);
frame.setFields(["Height", "Weight"]);
frame.setScales([hscale, wscale]);
elem.setShapeFrame(frame);
```

#### VineShapeFrame
Three-dimensional "vine" elements, dimensions in `[angle, magnitude, radius]` order (angle of the
stem line, length of the stem line, radius of the circle).
```js
var frame = new VineShapeFrame("m1","m2","m3");
```
##### VineShapeFrame.setStartAngle(value) / VineShapeFrame.setEndAngle(value)
Angle (degrees) that the minimum/maximum data value maps to. The relevant scale's `setMin`/`setMax`
also affects the displayed angle. Getters: `getStartAngle()` / `getEndAngle()`.
```js
var frame = new VineShapeFrame();
var mscale = new LinearScale(); mscale.setMin(0); mscale.setMax(5);
var rscale = new LinearScale(); rscale.setMin(0); rscale.setMax(90);
frame.setScales([rscale, mscale, mscale]);
frame.setFields(["m1", "m2", "m3"]);
frame.setStartAngle(0);
frame.setEndAngle(90);
elem.setShapeFrame(frame);
```

---

## Size

### SizeFrame (base class)
Size scale for visual chart objects. Subclasses: `StaticSizeFrame`, `LinearSizeFrame`,
`CategoricalSizeFrame`.

#### SizeFrame.setLargest(value) / SizeFrame.setSmallest(value)
Pixel size (or, for `SchemaElement`/`IntervalElement`, a size **relative to `setMax`**) at which
the largest/smallest bound value is displayed; other values scale per the frame's own mapping
(e.g. linear for `LinearSizeFrame`). `largest` must stay below `max`. Getters: `getLargest()` /
`getSmallest()`. Ignored for `PointElement`/`LineElement` for `setSmallest` framing purposes noted
on `setMax`.
#### SizeFrame.setMax(value)
An arbitrary maximum-allowable-size value; `setLargest`/`setSmallest` on `SchemaElement`/
`IntervalElement` are relative to it (e.g. `largest=50, max=100` displays the largest value at
half the max size). Ignored for `PointElement`/`LineElement`. Getter: `getMax()`.
```js
var frame = new LinearSizeFrame();
frame.setField("width");
frame.setSmallest(10);
frame.setLargest(50);
frame.setMax(100);
elem.setSizeFrame(frame);
```

### StaticSizeFrame
A fixed size, or explicit per-row size data read from the `setField` column.
```js
var frame = new StaticSizeFrame(10);
```
#### StaticSizeFrame.setSize(value)
Static size (in pixels for `PointElement`/`LineElement`; relative to `setMax` — default 30 — for
`SchemaElement`/`IntervalElement`). A per-row positive value in the `setField` column wins instead
of this setting, if present. Getter: `getSize()`.
```js
var frame = new StaticSizeFrame();
frame.setMax(100);
frame.setSize(50);
elem.setSizeFrame(frame);
```

### LinearSizeFrame
Linearly maps numerical data values to sizes (no extra methods of its own beyond the inherited
`SizeFrame` ones).
```js
var frame = new LinearSizeFrame('Quantity');
```

### CategoricalSizeFrame
A distinct size for each unique value.
```js
var frame = new CategoricalSizeFrame('State');
```
#### CategoricalSizeFrame.setSize(val,size)
Assign a size to a specific value. Getter: `getSize(val)`.
```js
var frame = new CategoricalSizeFrame();
frame.setField("State");
frame.setSize('NJ',5);
frame.setSize('NY',10);
elem.setSizeFrame(frame);
```

---

## Line

### LineFrame (base class)
Line design for visual chart objects. Subclasses: `LinearLineFrame`, `CategoricalLineFrame`,
`StaticLineFrame`.
```js
var frame = new StaticLineFrame();
frame.setLine(GLine.DASH_LINE);
elem.setLineFrame(frame);
```

### StaticLineFrame
A fixed line style, or explicit per-row `GLine` data read from the `setField` column.
```js
var frame = new StaticLineFrame(GLine.LARGE_DASH);
```
#### StaticLineFrame.setLine(value)
Static line style (a per-row `GLine` value in the `setField` column wins instead, if present).
Getter: `getLine()`.
- **Parameter** `value`: a `GLine` constant, or `Chart.NONE` for an empty border.
```js
var frame = new StaticLineFrame();
frame.setLine(GLine.DOT_LINE);
elem.setLineFrame(frame);
```

### LinearLineFrame
Continuous line frame returning varying line styles for numeric data (no extra methods of its own).
```js
var lframe = new LinearLineFrame('Quantity');
elem.setLineFrame(lframe);
```

### CategoricalLineFrame
A distinct line style for each unique value.
```js
var frame = new CategoricalLineFrame('Quantity');
```
#### CategoricalLineFrame.setLine(val,line)
Assign a line style to a specific value. Getter: `getLine(val)`.
- **Parameter** `line`: a `GLine` constant.
```js
frame = new CategoricalLineFrame();
frame.setField("State");
frame.setLine('NJ',GLine.THIN_LINE);
frame.setLine('NY',GLine.LARGE_DASH);
elem.setLineFrame(frame);
```

---

## Texture

### TextureFrame (base class)
Texture for visual chart objects. Subclasses: `LeftTiltTextureFrame`, `OrientationTextureFrame`,
`RightTiltTextureFrame`, `GridTextureFrame`, `CategoricalTextureFrame`, `StaticTextureFrame`.

### StaticTextureFrame
A fixed texture, or explicit per-row `GTexture` data read from the `setField` column.
```js
var frame = new StaticTextureFrame();
var frame = new StaticTextureFrame(GTexture.PATTERN_5);
```
#### StaticTextureFrame.setTexture(value)
Static texture (a per-row `GTexture` value in the `setField` column wins instead, if present).
Getter: `getTexture()`.
- **Parameter** `value`: a `GTexture` pattern constant, e.g. `GTexture.PATTERN_5`.
```js
frame = new StaticTextureFrame();
frame.setTexture(GTexture.PATTERN_18);
elem.setTextureFrame(frame);
```

### CategoricalTextureFrame
A distinct texture for each unique value.
```js
var frame = new CategoricalTextureFrame('State');
```
#### CategoricalTextureFrame.setTexture(val,texture)
Assign a texture to a specific value. Getter: `getTexture(val)`.
```js
textureframe = new CategoricalTextureFrame("State");
textureframe.setTexture('NJ',GTexture.PATTERN_18);
textureframe.setTexture('NY',GTexture.PATTERN_14);
elem.setTextureFrame(textureframe);
```

### GridTextureFrame
Texture rendered by variably-spaced orthogonal lines (no extra methods of its own).
```js
var frame = new GridTextureFrame("Total");
elem.setTextureFrame(frame);
```

### LeftTiltTextureFrame
Texture rendered by negatively-sloping lines with variable spacing (no extra methods of its own).
```js
var frame = new LeftTiltTextureFrame("Total");
elem.setTextureFrame(frame);
```

### RightTiltTextureFrame
Texture rendered by positively-sloping lines with variable spacing (no extra methods of its own).
```js
var frame = new RightTiltTextureFrame("Total");
elem.setTextureFrame(frame);
```

### OrientationTextureFrame
Texture rendered by uniformly-spaced lines with variable slope (no extra methods of its own).
```js
var frame = new OrientationTextureFrame("Total");
elem.setTextureFrame(frame);
```

---

## Text

### TextFrame (base class)
A mapping between values and displayed text. Subclasses: `DefaultTextFrame`, `MultiTextFrame`
(and its own subclass `StackTextFrame`).

#### TextFrame.setText(value,text)
Maps one data value to replacement display text. Getter: `getText(value)`.
```js
var tframe = new DefaultTextFrame("State");
tframe.setText('NJ','New Jersey');
tframe.setText('NY','New York');
elem.setTextFrame(tframe);
```

### DefaultTextFrame
Displays data values as text, with an optional value→replacement-text mapping (via the inherited
`setText`). Style/position it via `GraphElement.setTextSpec(spec)` /
`GraphElement.setLabelPlacement(value)`.
```js
var frame = new DefaultTextFrame('Quantity');
```
```js
var spec = new TextSpec();
var frame = new DefaultTextFrame();
spec.setFont(java.awt.Font('Verdana',java.awt.Font.BOLD, 14));
frame.setField("Quantity");
elem.setTextFrame(frame);
elem.setTextSpec(spec);
```

### MultiTextFrame
Maps values to **multiple** articles of displayed text at once (place the extra fields in the
Chart's "Break By" region if they aren't already bound elsewhere).
```js
var frame = new MultiTextFrame('m1', 'm2', 'm3');
```
#### MultiTextFrame.setFields(arr)
Columns supplying the text values. Getter: `getFields()`.
```js
var mtframe = new MultiTextFrame();
mtframe.setFields("Name","Quantity");
elem.setTextFrame(mtframe);
```
#### MultiTextFrame.setDelimiter(str)
Delimiter used to join the field values for display. For finer control, use `setMessageFormat`
instead.
```js
var tframe = new MultiTextFrame();
tframe.setFields("Customer:Region", "Sum(Product:Price)", "Sum(Product:Total)");
tframe.setDelimiter("\n");
graph.getElement(0).setTextFrame(tframe);
```
#### MultiTextFrame.setMessageFormat(str)
Formats the joined fields using `java.text.MessageFormat` syntax — `{0}` is the first field in
`setFields`, `{1}` the second, etc.
```js
var tframe = new MultiTextFrame();
tframe.setFields("Customer:Region", "Sum(Product:Price)", "Sum(Product:Total)");
tframe.setMessageFormat(new java.text.MessageFormat("Region: {0}\nPrice: {1}\nTotal: {2}"));
graph.getElement(0).setTextFrame(tframe);
```

### StackTextFrame (extends MultiTextFrame)
Text data for a Stacked Chart (no extra methods of its own — takes the target `GraphElement` in
its constructor).
```js
var elem = graph.getElement(0);
var frame = new StackTextFrame(elem);
elem.setTextFrame(frame);
```

---

## Font (axis labels)

### CategoricalFontFrame
Assigns a distinct font to each unique axis-label value. Not a `ColorFrame`/`ShapeFrame`/etc.
subtype — it attaches to an axis via `AxisSpec.setFontFrame(frame)`, not to a `GraphElement`.
```js
var frame = new CategoricalFontFrame();
```
#### CategoricalFontFrame.setFont(val,font)
Assign a font to a specific axis-label value. No getter shown.
- **Parameters** `val`: a data value. `font`: a `java.awt.Font` object.
```js
var aspec = new AxisSpec();
var frame = new CategoricalFontFrame();
frame.setFont("NJ", new java.awt.Font("Arial", java.awt.Font.BOLD, 14));
frame.setFont("CA", new java.awt.Font("Arial", java.awt.Font.ITALIC, 14));
aspec.setFontFrame(frame);
xscale.setAxisSpec(aspec);
```
