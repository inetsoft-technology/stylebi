# User Functions (StyleBI Common Script Reference)

Source: https://www.inetsoft.com/docs/stylebi/InetSoftUserDocumentation/1.0.0/commonscript/UserFunctions.html

Common scripting constructs available across Data Worksheet expressions, Dashboard/Viewsheet
scripts, and Chart scripts. This file covers the **StyleBI-specific** parts in full (constants and
globals that don't exist in plain JavaScript). The generic JavaScript/Java-interop parts are
summarized at the bottom — those are standard language behavior a model already knows, not
StyleBI-specific knowledge worth vendoring page-by-page.

For the Excel-formula-style function library (`CALC.*`), see
`../commonscript/CalcObjectFunctions.md`.

---

## Style Constant

The `StyleConstant` object is a static object holding constants used throughout the StyleBI host
environment — accessed without instantiation:
```js
line = StyleConstant.DOUBLE_LINE;
```

### Date Grouping Level
Used for Chart and Crosstab binding. One of the (integer) constants:

`YEAR_DATE_GROUP`, `QUARTER_DATE_GROUP`, `MONTH_DATE_GROUP`, `WEEK_DATE_GROUP`, `DAY_DATE_GROUP`,
`QUARTER_OF_YEAR_DATE_GROUP`, `MONTH_OF_YEAR_DATE_GROUP`, `WEEK_OF_YEAR_DATE_GROUP`,
`DAY_OF_MONTH_DATE_GROUP`, `DAY_OF_WEEK_DATE_GROUP`, `HOUR_OF_DAY_DATE_GROUP`,
`MINUTE_OF_HOUR_DATE_GROUP`, `SECOND_OF_MINUTE_DATE_GROUP`, `HOUR_DATE_GROUP`,
`MINUTE_DATE_GROUP`, `SECOND_DATE_GROUP`, `NONE_DATE_GROUP` (no grouping).

### Alignment
Horizontal or vertical direction constants; combine with bitwise OR:
```js
alignment = StyleConstant.H_RIGHT | StyleConstant.V_CENTER;
```
- Horizontal: `H_LEFT`, `H_CENTER`, `H_RIGHT` (synonyms: `LEFT`, `CENTER`, `RIGHT`)
- Vertical: `V_TOP`, `V_CENTER`, `V_BOTTOM`, `V_BASELINE` (character baseline)
- `FILL` — fill the whole space

### Data Type
`BOOLEAN`, `BYTE`, `CHAR`, `DATE` (no time component), `DOUBLE`, `ENUM` (user-defined string
constants), `FLOAT`, `INTEGER`, `LONG`, `SHORT`, `STRING`, `TIME`, `TIME_INSTANT` (date + time).
```js
RangeSlider1.rangeType = StyleConstant.DATE;
```

### Chart Style
How a Chart presents its data (see Chart Types for descriptions):

`CHART_3D_BAR`, `CHART_3D_BAR_STACK`, `CHART_3D_PIE`, `CHART_AREA`, `CHART_AREA_STACK`,
`CHART_AUTO`, `CHART_BAR`, `CHART_BAR_STACK`, `CHART_BOXPLOT`, `CHART_CANDLE`, `CHART_CIRCULAR`,
`CHART_CIRCLE_PACKING`, `CHART_FILL_RADAR`, `CHART_FUNNEL`, `CHART_GANTT`, `CHART_ICICLE`,
`CHART_JUMP`, `CHART_LINE`, `CHART_LINE_STACK`, `CHART_MAP`, `CHART_MEKKO`, `CHART_NETWORK`,
`CHART_PARETO`, `CHART_PIE`, `CHART_POINT`, `CHART_POINT_STACK`, `CHART_RADAR`, `CHART_STEP`,
`CHART_STEP_STACK`, `CHART_STEP_AREA`, `CHART_STEP_AREA_STACK`, `CHART_STOCK` (high-low-closing),
`CHART_SUNBURST`, `CHART_TREE`, `CHART_TREEMAP`, `CHART_WATERFALL`, `CHART_SCATTER_CONTOUR`,
`CHART_MAP_CONTOUR`, `CHART_INTERVAL`, `CHART_DONUT`.

### Line Style
Used anywhere a line style is needed (separators, tab fill, table borders):

`StyleConstant.NO_BORDER`, `ULTRA_THIN_LINE` (¼pt), `THIN_THIN_LINE` (½pt), `THIN_LINE` (1pt),
`MEDIUM_LINE`, `THICK_LINE`, `DOUBLE_LINE`, `RAISED_3D`, `LOWERED_3D`, `DOUBLE_3D_RAISED`,
`DOUBLE_3D_LOWERED`, `DOT_LINE`, `DASH_LINE`, `MEDIUM_DASH`, `LARGE_DASH`, `BREAK_BORDER` (signals
a page break in a table row — table border only).
```js
style = StyleConstant.DOT_LINE;
```

### Summarization Formula
For Crosstab and Chart scripting:

`NONE_FORMULA`, `AVERAGE_FORMULA`, `CONCAT_FORMULA`, `CORRELATION_FORMULA`, `COUNT_FORMULA`,
`COVARIANCE_FORMULA`, `DISTINCTCOUNT_FORMULA`, `MAX_FORMULA`, `MEDIAN_FORMULA_FORMULA`,
`MIN_FORMULA`, `MODE_FORMULA`, `NTHLARGEST_FORMULA`, `NTHMOSTFREQUENT_FORMULA`,
`NTHSMALLEST_FORMULA`, `POPULATIONSTANDARDDEVIATION_FORMULA`, `POPULATIONVARIANCE_FORMULA`,
`PRODUCT_FORMULA`, `PTHPERCENTILE_FORMULA`, `STANDARDDEVIATION_FORMULA`, `SUMSQ_FORMULA`,
`SUMWT_FORMULA`, `SUM_FORMULA`, `VARIANCE_FORMULA`, `WEIGHTEDAVERAGE_FORMULA`.
```js
formula = StyleConstant.AVERAGE_FORMULA;
```

### Point Shape
Shape used in a Point Chart — **prefer `GShape` instead** (see
`../chartAPI/UtilityObjects.md`), this is the older constant set:

`CIRCLE`, `TRIANGLE`, `SQUARE`, `CROSS`, `STAR`, `DIAMOND`, `X`, `FILLED_CIRCLE`,
`FILLED_TRIANGLE`, `FILLED_SQUARE`, `FILLED_DIAMOND`.

---

## Global Object Functions

Properties and functions available everywhere in the scripting environment, with no qualifier
needed — accessible as top-level functions.

### decodeURI(uri)
Produces a new URI with escape sequences/UTF-8 encoding replaced by the characters they represent.
```js
decodeURI("https://www.inetsoft.com/My%20Folder")
// returns: https://www.inetsoft.com/My Folder
```

### encodeURI(uri)
Produces a new URI with special characters replaced by an appropriate encoding.
```js
encodeURI("https://www.inetsoft.com/My Folder")
// returns: https://www.inetsoft.com/My%20Folder
```

### eval(string)
Evaluates a string of JavaScript code. Generally avoid — scripts relying on `eval()` are hard to
debug.

### formatNumber(number, format, round)
Formats a number per a format pattern string.
- `number` — the number to format.
- `format` — a number format pattern.
- `round` — rounding option, e.g. `'ROUND_HALF_UP'`.
```js
formatNumber(3, '#,###.00', 'ROUND_HALF_UP');
```

### getImage(string)
Loads an image from a Java resource path/URL, or a database BLOB (as ASCII Hex or ASCII85 encoded
gif/jpeg). If the argument isn't a valid resource path, it's treated as encoded image data.
```js
Image1.image = getImage('/com/mypackage/icon.gif');
Image1.image = getImage('https://visualizefree.com/images/inetsoft.png');
// 'picture' column in data block 'Query1' contains BLOBs:
Image1.image = getImage(Query1[1]['picture']);
```

### importClass(class)
Enables use of the unqualified class name.
```js
importClass(java.awt.Font);
font = new Font("Verdana", Font.ITALIC, 24); // no java.awt qualifier needed
foreground = Color.red;
```

### importPackage(package)
Enables unqualified names for every class in the given package (java, inetsoft, or any package
starting with `com` or `org`).
```js
importPackage(java.awt);
font = new Font("Verdana", Font.ITALIC, 24);
```

### indexOf(array, value)
Returns the 0-based index of `value` in `array`, or `-1` if not found.
```js
colorArray = ['gold','silver','blue','red','green'];
indexOf(colorArray, 'red');    // 3
indexOf(colorArray, 'purple'); // -1
```

### inGroups(list [,others])
In a Freehand Table expression, returns `true` when the row's values match the group cells named
in `list` (an array of `[groupName, column]` pairs). `others` is the label of the "Others" group.
```js
sum(data['Total?inGroups(["AMG Logistics", Company, "Annie", Salesperson])']);
```

### isNull(object)
Tests for a null value.
```js
if (isNull(Text1.text)) { /* ... */ }
```

### log(string)
Prints a message to the server log (visible in the Visual Composer Console / server log files).
```js
log('onLoad execution completed...');
```

### newInstance(name)
Creates a new Java object by class name (String). Prefer the `new` keyword generally — this exists
for cases where `new` doesn't handle Java packages correctly.
```js
var presenter = newInstance('inetsoft.report.painter.IconCounterPresenter');
```

### parseFloat(string)
Parses a string into a decimal number.
```js
var num = parseFloat("3231.24");
```

### parseInt(string [,radix])
Parses a string into an integer. If `radix` is omitted, base 10 is assumed unless the string starts
with `0x`/`0X` (then 16). `radix` out of `[2,36]` returns `null`.
```js
parseInt("0x1445");    // 5189
parseInt("1445", 16);  // 5189
```

### registerPackage(string)
Registers a package so all its classes can be used unqualified. Packages starting with `java`,
`inetsoft`, `com`, or `org` are registered automatically; others can be registered here, or via the
`javascript.java.packages` server property.
```js
registerPackage('inetsoft.report');
```

### runQuery(name [,parameters])
Runs a Data Worksheet query and returns the result as a 2D array.
- `name` — the data block name string.
- `parameters` — optional 2D array of `[name, value]` pairs matching query parameters.
```js
var rs = runQuery('ws:global:Examples/AllSales', [['category','Business'], ['price',100]]);
```

### toList(list [,options])
Removes duplicates from an array, with optional sort/group/limit, via a comma-separated
`"key=value"` options string:
- `sort` — `asc`/`desc`/`false`.
- `sorton` / `sorton2` — sort measure applied before/after `maxrows` filtering.
- `remainder` — label for an "Others" group in Top/Bottom-N filtering (omit to suppress it).
- `maxrows` — row limit.
- `distinct` — `true`/`false` (default `true`).
- `date` — group returned values by date part: `year`/`quarter`/`month`/`week`/`day`/`hour`/
  `minute`/`second`/`weekday`/`monthname`/`weekdayname`.
- `rounddate` — same as `date` but returns actual date values.
- `timeseries` — `true` retains gaps in date data (e.g. keeps a month with no rows); default
  `false`.
```js
toList([2,3,1,2,3,3,2,2,1,0], 'sort=desc'); // [3,2,1,0]
```

---

## Date Global Functions

Helper date functions, complementing the standard `Date` object methods (see summary below). All
take an `interval` string from this shared table:

| Interval | Meaning |
|---|---|
| `yyyy` | Year |
| `q` | Quarter |
| `m` | Month |
| `y` | Day of the year |
| `d` | Day of the month |
| `w` | Day of the week |
| `ww` | Week of the year |
| `h` | Hour |
| `n` | Minute |
| `s` | Second |

### dateAdd(interval, amount, date)
Returns a new date with `amount` of `interval` added.
```js
var newDate = dateAdd('m', 2, new Date()); // two months from now
```

### dateDiff(interval, date1, date2)
Returns the difference between `date1` and `date2`, measured in `interval` units.
```js
dateDiff('m', new Date(), newDate); // e.g. 2
```

### datePart(interval, date)
Extracts the given interval from a date.
```js
datePart('m', new Date()); // current month
```

### formatDate(date, string)
Formats a date to a string. `string` is `'LONG'`/`'FULL'`/`'MEDIUM'`/`'SHORT'` (locale-adapting —
e.g. `FULL` renders "Wednesday, May 22, 2025" in English, "2025年5月22日" in Chinese), or a pattern
using symbols: `G` era, `y`/`yyyy` year, `Q`/`QQQ` quarter, `M`/`MM`/`MMM`/`MMMM` month, `w` week of
year, `W` week of month, `D` day of year, `d`/`dd` day of month, `u`/`uu` day of week (Monday=1),
`F` day-of-week-in-month, `E`/`EEEE` day of week, `a` AM/PM, `H`/`k`/`h`/`K` hour variants (24h
midnight-0, 24h midnight-24, 12h midnight-12, 12h midnight-0), `m`/`mm` minute, `s`/`ss` second,
`S`/`SSS` millisecond, `z`/`zzzz` timezone name, `Z` RFC 822 timezone, `X`/`XX`/`XXX` ISO 8601
timezone.
```js
formatDate(new Date(), "hh 'o''clock' a, zzzz"); // "12 o'clock PM, Pacific Daylight Time"
```

### isDate(value)
Returns `true` if `value` is a Date object.
```js
isDate(CALC.today()); // true
```

### parseDate(string, format)
Parses a string into a date. `format` is `true` to parse a `timeInstant`, or a pattern string using
the same symbol table as `formatDate`.
```js
parseDate('2006-08-07', 'yyyy-MM-dd');
```

---

## Generic JavaScript / Java interop (summarized, not vendored)

These sections of the doc site describe standard JavaScript/Java behavior — a model already knows
this from training. Only the StyleBI-specific deviation noted under Date Object Functions is worth
remembering; otherwise, treat these as ordinary ECMAScript/Java semantics and don't expect
StyleBI-specific surprises.

- **Java Objects (LiveConnect)** — StyleBI's script engine runs inside a JVM and lets JavaScript
  reach Java objects directly: `new java.awt.Color(0xFF0000)`. A Java getter/setter pair
  (`getFoo()`/`setFoo(x)`) is exposed as a JS property `foo`; a getter with no setter is read-only
  (assignment is silently ignored). Other public Java methods are callable as ordinary JS methods.
- **String Object Functions** — standard JS `String` methods: `charAt`, `charCodeAt`, `concat`,
  `indexOf`, `lastIndexOf`, `length`, `localeCompare`, `ltrim`, `match`, `replace`, `rtrim`,
  `search`, `slice`, `split`, `subString`, `toLocaleUpperCase`, `toLowerCase`, `toUpperCase`,
  `trim`.
- **Number Object Functions** — standard, for converting a number to a string: `isNumber`,
  `numberToString`, `toExponential`, `toFixed`, `toLocaleString`, `toPrecision`, `toString([radix])`.
- **Date Object Functions** — the full standard JS `Date` prototype (`getDate`/`getDay`/
  `getFullYear`/`getHours`/`getTime`/`getTimezoneOffset`/`getMilliseconds`/`getMinutes`/
  `getMonth`/`getSeconds` + `getUTC*` variants, `Date.parse`/`Date.UTC`, `toDateString`/
  `toLocaleString`/`toString`/`toTimeString`/`toLocaleDateString`/`toLocaleTimeString`/
  `toUTCString`, `setDate`/`setFullYear`/`setHours`/`setTime`/`setMilliseconds`/`setMinutes`/
  `setMonth`/`setSeconds` + `setUTC*` variants, `valueOf`). **StyleBI-specific quirk worth
  remembering**: these functions operate only on objects that are genuine JavaScript `Date`
  instances (created via `new Date(...)`) — if the object isn't a JS Date, the corresponding Java
  method runs instead of the JavaScript one, which can silently change behavior when a "date" value
  actually arrived as a `java.util.Date` from the data layer.
- **Array Object Functions** — standard JS `Array` methods: `concat`, `join`, `pop`, `push`,
  `reverse`, `shift`, `slice`, `sort`, `splice`, `unshift`.
- **Math Object Functions** — standard `Math` constants (`E`, `LN10`, `LN2`, `LOG10E`, `LOG2E`,
  `PI`, `SQRT1_2`, `SQRT2`) and methods (`abs`, `acos`, `asin`, `atan`, `ceil`, `cos`, `exp`,
  `floor`, `log`, `max`, `min`, `pow`, `random`, `round`, `sin`, `sqrt`, `tan`). The doc's own
  cross-reference: `CALC` (see `../commonscript/CalcObjectFunctions.md`) is the more complete math
  library, with Excel-equivalent functions.
- **Regex Functions** — standard JS `RegExp` methods: `exec(str)`, `test(str)`, `toString()`, on an
  object created via a `/pattern/flags` literal or `new RegExp(pattern, flags)`.
