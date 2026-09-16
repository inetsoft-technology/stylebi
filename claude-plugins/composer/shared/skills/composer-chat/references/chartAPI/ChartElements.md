# Chart Elements (StyleBI Chart Script Reference)

Source: https://www.inetsoft.com/docs/stylebi/InetSoftUserDocumentation/1.0.0/chartAPI/ChartElements.html

> The following sections discuss the various data-representation elements that can be added to charts.

Object hierarchy: `GraphElement` is the base class. `AreaElement`, `IntervalElement`, `LineElement`,
`PointElement`, `SchemaElement` all extend it directly, except `AreaElement`, which extends
`LineElement` (an area chart is a filled line). Every element is attached to the chart via
`EGraph.addElement(elem)`. To modify an element on a chart already built in the Chart Editor, get a
handle to it first: `var elem = graph.getElement(0);`.

---

## GraphElement (base class)

The GraphElement object contains the visual elements that represent data. For example,
`PointElement` is a `GraphElement` that represents data tuples as points.

### GraphElement.addDim(field)
Add a dimension to a GraphElement object. A dimension is plotted on the X-axis, or on the outer
coordinates of nested coordinates.
- **Parameter** `field`: String containing name of dimension.
```js
dataset = [["City" , "State", "Quantity"],["NJ","Edison",2500],
["NJ","Piscataway",3000], ["NY","NY City",5000],["NY","Yonkers",450]];
graph = new EGraph();
var elem = new IntervalElement("City","Quantity");
elem.addDim("State");
graph.addElement(elem);
```

### GraphElement.addVar(field)
Add a variable or measure to a GraphElement object. A variable is plotted on the Y-axis.
- **Parameter** `field`: String containing name of variable.
```js
dataset = [["State", "Quantity", "Total"],["NY",550,2500],["NJ",370,3000]];
graph = new EGraph();
var elem = new LineElement("State","Quantity");
elem.addVar("Total");
graph.addElement(elem);
```

### GraphElement.getColorFrame() / getShapeFrame() / getSizeFrame() / getTextFrame() / getTextureFrame()
Retrieve the element's `ColorFrame` / `ShapeFrame` / `SizeFrame` / `TextFrame` / `TextureFrame`.
Setters: `setColorFrame(frame)` / `setShapeFrame(frame)` / `setSizeFrame(frame)` /
`setTextFrame(frame)` / `setTextureFrame(frame)`.
- **Returns**: the corresponding frame object.
```js
var elem = graph.getElement(0);
var frame = elem.getColorFrame(); // or getShapeFrame/getSizeFrame/getTextFrame/getTextureFrame
frame.setField("Customer:Company");
```

### GraphElement.getTextSpec(field)
Retrieves the `TextSpec` for a field representing text attributes such as color, font, format, etc.
Setter: `setTextSpec(spec)`.
- **Parameter** `field`: name of a data field (String), including aggregation operator.
- **Returns**: a `TextSpec` object.
```js
var elem = graph.getElement(0);
var spec = elem.getTextSpec("Sum(Product:Total)");
spec.setColor(java.awt.Color(0xff0000));
```

### GraphElement.setAutoTextColor(Boolean)
On a Bar Chart, automatically adjusts the text color of in-bar labels to enhance readability. No
getter.
- **Parameter** `Boolean`: `true` auto-adjust, `false` do not (default).
```js
elem.setColorFrame(new HeatColorFrame());
var tframe = new DefaultTextFrame('Quantity');
elem.setTextFrame(tframe);
elem.setAutoTextColor(true);
```

### GraphElement.setBorderColor(value)
Specifies the element border color. Getter: `getBorderColor()`.
- **Parameter** `value`: a `java.awt.Color` object.
```js
var line = new StaticLineFrame(10);
var elem = new IntervalElement("State", "Quantity");
elem.setLineFrame(line);
elem.setBorderColor(java.awt.Color(0x000000));
```

### GraphElement.setCollisionModifier(value)
Specifies how collisions (elements occupying the same location) should be handled. Getter:
`getCollisionModifier()`.
- **Parameter** `value`: one of `GraphElement.MOVE_NONE` (no stack/center), `MOVE_CENTER` (center,
  no stack), `MOVE_DODGE` (offset horizontal), `MOVE_STACK` (offset vertical / stack), `MOVE_JITTER`
  (random offset — points), `DODGE_SYMMETRIC` (offset horizontal, centered),
  `STACK_SYMMETRIC` (offset vertical, centered).
```js
elem.setStackGroup(true);
elem.setCollisionModifier(GraphElement.STACK_SYMMETRIC);
// or, for a scatter chart:
elem.setCollisionModifier(GraphElement.MOVE_JITTER);
```

### GraphElement.setColorFrame(frame)
Aesthetic color treatment for the chart elements — color-code by value, or a static color scheme.
Getter: `getColorFrame()`.
- **Parameter** `frame`: a `ColorFrame` object.
```js
var frame = new HeatColorFrame();
var elem = new IntervalElement("State", "Quantity");
frame.setField("Quantity");
elem.setColorFrame(frame);
```

### GraphElement.setHint(type,value)
Add a single effect to a GraphElement object. Getter: `getHint(type)`. See also `setHints(array)`
below for setting multiple effects at once.
- **Parameter** `type`: `GraphElement.HINT_EXPLODED` (element separation/explosion),
  `GraphElement.HINT_SHINE` (three-dimensional shading), `GraphElement.HINT_ALPHA`
  (transparency).
- **Parameter** `value`: `'true'`/`'false'` for `HINT_EXPLODED`/`HINT_SHINE`; a float in `[0,1]` for
  `HINT_ALPHA`.
```js
var elem = new IntervalElement("State","Quantity");
elem.setHint(GraphElement.HINT_SHINE,'true');
```

### GraphElement.setHints(array)
Add multiple effects to a GraphElement object at once. Getter: `getHints(type)`.
- **Parameter** `array`: a map of the form `{type1: value1, type2: value2, ...}` using the same
  `exploded`/`shine`/`alpha` keys and values as `setHint`.
```js
elem.setHints({shine:"true", alpha:"0.5"});
```

### GraphElement.setInPlot(Boolean)
Specifies whether the chart should be resized so graph elements remain fully visible in the chart
area. No getter. (See also `AxisSpec.setInPlot` for labels and `GraphForm.setInPlot` for manually
drawn forms.)
- **Parameter** `Boolean`: `true` resize chart (do not crop), `false` do not resize (crop elements).
```js
var scale = new LinearScale("Quantity");
scale.setMax(100);
graph.setScale("Quantity",scale);
elem.setInPlot(false); // crop elements beyond the fixed max
```

### GraphElement.setLabelPlacement(value)
Specifies the location of element labels. Getter: `getLabelPlacement()`.
- **Parameter** `value`: `Chart.CENTER`, `Chart.BOTTOM`, `Chart.TOP`, `Chart.RIGHT`, `Chart.LEFT`.
```js
var frame = new DefaultTextFrame();
frame.setField("Quantity");
elem.setTextFrame(frame);
elem.setLabelPlacement(Chart.BOTTOM);
```

### GraphElement.setLineFrame(frame)
Aesthetic line style of graphical elements — line-code by value, or a static line style. Getter:
`getLineFrame()`.
- **Parameter** `frame`: a `LineFrame` object.
```js
var frame = new StaticLineFrame();
frame.setLine(GLine.LARGE_DASH);
elem.setLineFrame(frame);
```

### GraphElement.setShapeFrame(frame)
Aesthetic shape treatment — shape-code by value, or a static shape. Getter: `getShapeFrame()`.
- **Parameter** `frame`: a `ShapeFrame` object.
```js
var frame = new StarShapeFrame();
frame.setFields(["m1", "m2", "m3"]);
elem.setShapeFrame(frame);
```

### GraphElement.setSizeFrame(frame)
Size of graphical elements — size-code by value, or a static size. Getter: `getSizeFrame()`.
- **Parameter** `frame`: a `SizeFrame` object.
```js
var frame = new LinearSizeFrame();
frame.setField("width");
frame.setSmallest(10);
frame.setLargest(50);
frame.setMax(100);
elem.setSizeFrame(frame);
```

### GraphElement.setTextFrame(frame)
Data values displayed on the chart element as text, plus the value→display-text mapping. Getter:
`getTextFrame()`.
- **Parameter** `frame`: a `TextFrame` object.
```js
var frame = new DefaultTextFrame();
frame.setField("Quantity");
elem.setTextFrame(frame);
```

### GraphElement.setTextSpec(spec)
Text attributes (color, font, format, etc.) for element labels. Getter:
`getTextSpec(field)`.
- **Parameter** `spec`: a `TextSpec` object.
```js
var spec = new TextSpec();
spec.setColor(java.awt.Color(0xff0000));
elem.setTextFrame(frame);
elem.setTextSpec(spec);
```

### GraphElement.setTextureFrame(frame)
Aesthetic texture of graphical elements — texture-code by value, or a static texture. Getter:
`getTextureFrame()`.
- **Parameter** `frame`: a `TextureFrame` object.
```js
var frame = new StaticTextureFrame();
frame.setTexture(GTexture.PATTERN_18);
elem.setTextureFrame(frame);
```

---

## AreaElement (extends LineElement)

Visual elements for an area chart.
```js
var elem = new AreaElement("State", "Quantity");
```
Pass field names to the constructor, or set them later with the inherited `addDim(field)`/
`addVar(field)`. Adds no methods of its own beyond what `LineElement`/`GraphElement` provide.

---

## IntervalElement (extends GraphElement)

Visual elements for bar charts and range visualization.
```js
var elem = new IntervalElement("State", "Quantity");
```

### IntervalElement.addInterval(lower,upper)
Adds a "floating" interval element (bar) whose lower and upper bounds come from two fields, instead
of from zero.
- **Parameters** `lower`, `upper`: field names (String) for the lower/upper bound.
```js
dataset = [["Student","Bottom Score","Top Score"],["Joe",70,80],
["Eric",50,90],["Jane",90,100], ["Sue",40,45]];
graph = new EGraph();
var elem = new IntervalElement();
elem.addDim("Student");
elem.addInterval("Bottom Score","Top Score");
graph.addElement(elem);
```

### IntervalElement.setStackGroup(Boolean)
Whether each element group (bar series) stacks independently, or all form one stack order. No
getter.
- **Parameter** `Boolean`: `true` independent stack per group, `false` single stack for all groups.
```js
elem.setStackGroup(true);
elem.setCollisionModifier(GraphElement.STACK_SYMMETRIC);
// To adjust the Y-axis to include all elements:
graph.getCoordinate().getYScale().setScaleRange(new StackRange());
```

### IntervalElement.setStackNegative(Boolean)
Whether positive and negative values stack independently on opposite sides of the axis, or
cumulatively (arithmetically). No getter.
- **Parameter** `Boolean`: `true` stack pos/neg independently, `false` stack arithmetically.
```js
elem.setStackGroup(true);
elem.setStackNegative(false);
elem.setCollisionModifier(GraphElement.STACK_SYMMETRIC);
```

---

## LineElement (extends GraphElement)

Visual elements for a line chart.
```js
var elem = new LineElement("State", "Quantity");
```

### LineElement.setClosed(Boolean)
Whether the line should automatically close (connect its endpoints). No getter.
- **Parameter** `Boolean`: `true` close the figure, `false` do not.

### LineElement.setEndArrow(Boolean) / LineElement.setStartArrow(Boolean)
Whether an arrow is drawn at the line's end (last point) / start (first point). No getter.
- **Parameter** `Boolean`: `true` draw arrow, `false` do not.
```js
elem.setStartArrow(true);
elem.setEndArrow(true);
```

### LineElement.setStackGroup(Boolean)
Whether each subgroup gets its own independent line, or a single line is used for all. No getter.
- **Parameter** `Boolean`: `true` independent line per group, `false` single line for all (default).
```js
elem.setColorFrame(new CategoricalColorFrame("Product"));
elem.setStackGroup(true);
```

### LineElement.setStackNegative(Boolean)
Same semantics as `IntervalElement.setStackNegative(Boolean)` — independent vs. arithmetic
stacking of positive/negative values. No getter.

### LineElement.setIgnoreNull(Boolean)
Whether a null value is skipped (drawing a continuous line) or honored (breaking the line at that
point). No getter.
- **Parameter** `Boolean`: `true` ignore nulls (continuous line), `false` honor nulls (break line).
```js
dataset = [["State","Quantity"],["Sun",100],["Mon",300],["Tue",null],
["Wed",400],["Thur",600],["Fri",550],["Sat",200]];
elem = new LineElement("State", "Quantity");
elem.setIgnoreNull(true); // continuous line across the null
```

---

## PointElement (extends GraphElement)

Visual elements for a point (scatter) chart.
```js
var elem = new PointElement("State", "Quantity");
```
Pass field names to the constructor, or set them later with the inherited `addDim(field)`/
`addVar(field)`. Adds no methods of its own.

---

## SchemaElement (extends GraphElement)

User-defined visual elements (box-and-whisker, candlestick, stock charts) added to the graph.
```js
var elem = new SchemaElement("State", "Quantity");
```

### SchemaElement.addSchema(col)
Specifies the columns bound to the `SchemaPainter`'s data slots (e.g. Hi/Close/Lo for a stock
chart).
- **Parameter** `col`: list of column names for schema binding.

### SchemaElement.setPainter(painter)
Specifies the `SchemaPainter` used to draw the element. Getter: `getPainter()`.
- **Parameter** `painter`: `BoxPainter` (box-and-whiskers), `CandlePainter` (candlestick),
  `StockPainter` (stock chart).
```js
dataset = [["State", "Hi", "Lo", "Open", "Close"],["NJ", 200, 100, 120, 150],
["NY", 300, 100, 200, 120]];
graph = new EGraph();
var elem = new SchemaElement();
elem.addDim("State");
elem.addSchema("Hi", "Close", "Lo");
elem.setPainter(new StockPainter());
graph.addElement(elem);
```

---

## General notes

- All chart scripting runs as **Add Component Script** on the Chart component itself, with access
  to Chart data and Chart API methods; deselect "Enable Ad Hoc Editing" in Chart Properties for
  scripted charts.
- To modify an element on a chart already built in the Chart Editor rather than creating one from
  scratch, get a handle first — `var elem = graph.getElement(index);` — then call the same setters.
- The five aesthetic setters (`setColorFrame`/`setShapeFrame`/`setSizeFrame`/`setTextFrame`/
  `setTextureFrame`, all on `GraphElement`) follow one pattern: create the matching `*Frame` object
  (see `ChartAesthetics.md`), configure it, then assign it. Static vs. data-driven behavior lives on
  the frame subclass chosen (e.g. `StaticColorFrame` vs. `CategoricalColorFrame`), not on the
  element.
