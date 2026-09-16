# StyleBI Worksheet Expression — JS Function Reference

This file documents the JavaScript functions available in worksheet **expression columns** (`sql: false`).
Column expressions run per-row; they reference the current row's columns via `field['<name>']` and must
return a scalar value. They can **also reference other rows by offset** (see "Row references" below) — that
gives you LAG/LEAD/rolling windows without a self-join or SQL.

## Row references (previous/next rows → LAG / LEAD / rolling)

A `sql: false` expression can read **other rows** using a row-offset index on `field`:

```js
field[-1]['revenue']   // previous row's revenue  (LAG 1)
field[-2]['revenue']   // two rows back           (LAG 2)
field[1]['revenue']    // next row's revenue      (LEAD 1)
field['revenue']       // current row (same as field[0]['revenue'])
```

This makes ordered-window calculations declarative — no self-join, no `sql query table`:

```js
// Month-over-month % change (previous row = previous month, once the table is sorted by month):
!(field[-1]['monthly_rev'] > 0) ? 0
  : (field['monthly_rev'] - field[-1]['monthly_rev']) / field[-1]['monthly_rev'] * 100

// Rolling 3-month average — GUARD the edge rows (see rule 2): at rows 0 and 1 the offset
// reads return the string "rev", and string + number = NaN. Fall back to the current value:
(typeof field[-1]['rev'] === 'number' && typeof field[-2]['rev'] === 'number')
  ? (field['rev'] + field[-1]['rev'] + field[-2]['rev']) / 3
  : field['rev']
```

**Two rules that matter:**
1. **Order is row order.** `field[-1]` is the *previous row as the table is currently ordered*, not "the row whose
   key is one less." Make sure the base table is sorted the way you want. A `GROUP BY` on the ordering key usually
   returns rows in key order in practice, but SQL does **not** guarantee ordering without an explicit sort — add an
   `orderBy` on the ordering key (in the aggregating table, or a mirror over it) to be safe across databases.
   Because it's row-based, it naturally skips gaps (a missing month
   just means the previous *existing* row) — i.e. true LAG-over-ordered-rows semantics.
2. **Edges return the column-name string, not null.** At the first row `field[-1]['x']` (and at the last row
   `field[1]['x']`) evaluates to the literal string `"x"`, not `null`/`undefined`. **Guard it** — e.g.
   `!(field[-1]['x'] > 0) ? 0 : …` (a string fails the numeric comparison, so the guard fires cleanly), or test
   `typeof field[-1]['x'] === 'number'`. Both idioms are equivalent; use the `typeof` form when the value can
   legitimately be ≤ 0 (so the `> 0` shortcut would wrongly reject valid negatives/zeros).

## Runtime Environment

StyleBI uses **Mozilla Rhino 1.7.14** as its JS engine. The language level is **ES5** (no explicit version override is set in the codebase, so `Context.VERSION_DEFAULT` applies — equivalent to ES5 compatibility mode).

**What this means in practice:**

```js
// ✅ ES5 — works
var x = field['PRICE'] * 1.1;
function label(v) { return v > 100 ? 'High' : 'Low'; }

// ❌ ES6+ — does NOT work in expression columns
const x = ...          // no let/const
(v) => v * 2           // no arrow functions
`Hello ${name}`        // no template literals
const {a, b} = obj     // no destructuring
```

**Available functions** fall into two categories:
1. **Standard ES5 built-ins** — `Math`, `Date`, `String`, `Array`, `RegExp`, `JSON` objects and their methods work as normal.
2. **StyleBI custom functions** — InetSoft-specific globals (`dateDiff`, `formatDate`, `isNull`, etc.) and Excel-style `CALC.*` functions documented below.

This file only documents the **StyleBI custom functions** (category 2). Standard ES5 syntax and built-ins are assumed knowledge.

**How to call CALC functions:** use the bare function name (no `CALC.` prefix).
`CALC.round(...)` and `round(...)` are identical — the prefix is optional.

---

## 1. InetSoft Date Arithmetic

These three functions share the same `interval` parameter. **Only the exact short strings below are valid.**
Any other string (e.g. `'day'`, `'month'`, `'year'`) silently returns `0` — there is no error.

### interval values

| interval | Meaning | Notes |
|----------|---------|-------|
| `'yyyy'` | Year | |
| `'q'`    | Quarter | `dateAdd`: adds 3 months per unit |
| `'m'`    | Month | |
| `'ws'`   | Week (ISO) | `dateDiff`/`datePart` only; same as `'ww'` |
| `'ww'`   | Week of year | `dateDiff`/`dateAdd` |
| `'wm'`   | Week of month | `dateAdd`/`datePart` only |
| `'d'`    | Day | **recommended for day arithmetic** |
| `'y'`    | Day of year | Same result as `'d'` in all three functions |
| `'w'`    | Weekday | `dateDiff`→ returns day count (same as `'d'`); `dateAdd` → adds days |
| `'h'`    | Hour | |
| `'n'`    | Minute | |
| `'s'`    | Second | |

`datePart` additionally supports:

| interval | Meaning |
|----------|---------|
| `'mq'`   | Month within current quarter (1–3) |
| `'wq'`   | Week within current quarter |
| `'wy'`   | Week of year (encoded as `month*10 + weekOfMonth`) |
| `'dq'`   | Day within current quarter |

---

### `dateDiff(interval, date1, date2)` → `number`

Returns the integer count of complete `interval` units from `date1` to `date2`.
Positive when `date2 > date1`.

```js
dateDiff('d',    field['ORDER_DATE'], field['SHIP_DATE'])  // days between
dateDiff('m',    field['START'],      field['END'])        // months between
dateDiff('yyyy', field['BIRTH'],      field['NOW'])        // years between (calendar year delta)
dateDiff('h',    field['IN'],         field['OUT'])        // hours between
```

---

### `dateAdd(interval, amount, date)` → `Date`

Returns a new `Date` offset from `date` by `amount` units of `interval`.
`amount` may be negative.

```js
dateAdd('d',    30, field['ORDER_DATE'])   // 30 days later
dateAdd('m',    -3, field['DATE'])         // 3 months earlier
dateAdd('yyyy',  1, field['DATE'])         // 1 year later
dateAdd('h',     8, field['START_TIME'])   // 8 hours later
```

---

### `datePart(interval, date)` → `number`

Extracts one component from `date` as an integer.

```js
datePart('yyyy', field['ORDER_DATE'])  // 4-digit year
datePart('m',    field['ORDER_DATE'])  // month 1–12
datePart('d',    field['ORDER_DATE'])  // day of month 1–31
datePart('q',    field['ORDER_DATE'])  // quarter 1–4
datePart('ww',   field['ORDER_DATE'])  // week of year
datePart('h',    field['TIMESTAMP'])   // hour 0–23
datePart('n',    field['TIMESTAMP'])   // minute 0–59
datePart('s',    field['TIMESTAMP'])   // second 0–59
```

---

## 2. Type Checks

| Function | Signature | Returns |
|----------|-----------|---------|
| `isNull(value)` | `(value: any) → bool` | `true` if value is `null` or `undefined` |
| `isDate(value)` | `(value: any) → bool` | `true` if value is a `Date` |
| `isNumber(value)` | `(value: any) → bool` | `true` if value is a number |

```js
isNull(field['OPTIONAL_COL']) ? 0 : field['OPTIONAL_COL']
isDate(field['COL']) ? datePart('yyyy', field['COL']) : null
```

---

## 3. Date Parsing

| Function | Signature | Returns |
|----------|-----------|---------|
| `parseDate(string)` | `(string: string) → Date` | Parses date string with auto-detected format; returns `null` on failure |
| `parseDate(string, format)` | `(string: string, format: string) → Date` | Parses with explicit Java `SimpleDateFormat` pattern |

```js
parseDate(field['DATE_STR'])                       // auto-detect format
parseDate(field['DATE_STR'], 'yyyy-MM-dd')         // explicit format
```

---

## 4. Formatting

### `formatDate(date, format_spec)` → `string`

Uses Java `SimpleDateFormat` pattern.

| Pattern token | Meaning |
|---------------|---------|
| `yyyy` | 4-digit year |
| `MM` | Month (01–12) |
| `dd` | Day of month (01–31) |
| `HH` | Hour 24h (00–23) |
| `mm` | Minute (00–59) |
| `ss` | Second (00–59) |
| `EEE` | Abbreviated weekday name |

```js
formatDate(field['ORDER_DATE'], 'yyyy-MM-dd')         // "2024-03-15"
formatDate(field['ORDER_DATE'], 'MM/dd/yyyy')         // "03/15/2024"
formatDate(field['TIMESTAMP'],  'yyyy-MM-dd HH:mm')   // "2024-03-15 09:30"
```

Returns `"NaD"` if the value is not a date.

---

### `formatNumber(n, format_spec, round_option?)` → `string`

Uses Java `DecimalFormat` pattern.

| Pattern token | Meaning |
|---------------|---------|
| `0` | Digit, always shown |
| `#` | Digit, hidden when zero |
| `.` | Decimal separator |
| `,` | Grouping separator |
| `%` | Multiplies by 100 and appends `%` |

`round_option` (optional): `'ROUND_UP'`, `'ROUND_DOWN'`, `'ROUND_CEILING'`, `'ROUND_FLOOR'`,
`'ROUND_HALF_UP'` *(default)*, `'ROUND_HALF_DOWN'`, `'ROUND_HALF_EVEN'`, `'ROUND_UNNECESSARY'`.

```js
formatNumber(field['AMOUNT'],  '#,##0.00')              // "1,234.56"
formatNumber(field['RATE'],    '0.0%')                  // "85.3%"
formatNumber(field['PRICE'],   '#,##0.00', 'ROUND_UP')  // with explicit rounding
```

---

## 5. String Utilities (InetSoft native)

| Function | Signature | Returns |
|----------|-----------|---------|
| `trim(string)` | `(string: string) → string` | Removes leading and trailing whitespace |
| `ltrim(string)` | `(string: string) → string` | Removes leading whitespace |
| `rtrim(string)` | `(string: string) → string` | Removes trailing whitespace |

---

## 6. CALC — Date Extraction

All return `number` unless noted. Available without `CALC.` prefix.

| Function | Signature | Returns |
|----------|-----------|---------|
| `year(date)` | `(date: Date) → number` | 4-digit year |
| `month(date)` | `(date: Date) → number` | Month 1–12 |
| `day(date)` | `(date: Date) → number` | Day of month 1–31 |
| `hour(date)` | `(date: Date) → number` | Hour 0–23 |
| `minute(date)` | `(date: Date) → number` | Minute 0–59 |
| `second(date)` | `(date: Date) → number` | Second 0–59 |
| `quarter(date)` | `(date: Date) → number` | Quarter 1–4 |
| `weekday(date, return_type)` | `(date: Date, return_type: number) → number` | Day of week; `return_type`: 1=Sun–Sat(1–7), 2=Mon–Sun(1–7), 3=Mon–Sun(0–6) |
| `weeknum(date, return_type)` | `(date: Date, return_type: number) → number` | Week of year; `return_type`: 1=week starts Sun, 2=week starts Mon |
| `weekdayname(date)` | `(date: Date) → string` | Full weekday name, e.g. `"Monday"` |
| `monthname(date)` | `(date: Date) → string` | Full month name, e.g. `"March"` |
| `now()` | `() → Date` | Current date+time |
| `today()` | `() → Date` | Current date (time = 00:00:00) |
| `time(hour, minute, second)` | `(hour: number, minute: number, second: number) → number` | Constructs a time-of-day fraction (0–1) from components, compatible with `timevalue` |
| `timevalue(date)` | `(date: Date) → number` | Extracts the time-of-day portion of a Date as a fraction of 24 hours (0–1) |

### Date offset / boundary functions

| Function | Signature | Returns |
|----------|-----------|---------|
| `edate(date, months)` | `(date: Date, months: number) → Date` | Same day `months` months later (negative = earlier) |
| `eomonth(date, months)` | `(date: Date, months: number) → Date` | Last day of month `months` months from `date` |
| `workday(date, days, holidays?)` | `(date: Date, days: number, holidays: Date[]) → Date` | Date `days` working days from `date` (skips weekends + holidays) |
| `datevalue(date)` | `(date: Date) → number` | Integer days since 1899-12-31 (Excel epoch) |
| `days360(start, end, method?)` | `(start: Date, end: Date, method: bool) → number` | Days between dates on 360-day calendar; `method=false`: US, `true`: European |
| `networkdays(start, end, holidays?)` | `(start: Date, end: Date, holidays: Date[]) → number` | Working days between two dates (inclusive, excludes weekends + holidays) |
| `yearfrac(start_date, end_date, basis)` | `(start: Date, end: Date, basis: number) → number` | Fraction of year between two dates; `basis`: 0=US 30/360, 1=actual/actual, 2=actual/360, 3=actual/365, 4=European 30/360 |

---

## 7. CALC — Math

Available without `CALC.` prefix.

| Function | Signature | Returns |
|----------|-----------|---------|
| `abs(number)` | `(number: number) → number` | Absolute value |
| `round(number, num_digits)` | `(number: number, num_digits: number) → number` | Rounds to `num_digits` decimal places |
| `roundup(number, num_digits)` | `(number: number, num_digits: number) → number` | Always rounds away from zero |
| `rounddown(number, num_digits)` | `(number: number, num_digits: number) → number` | Always rounds toward zero |
| `ceiling(number, significance)` | `(number: number, significance: number) → number` | Rounds up to nearest multiple of `significance` |
| `floor(number, significance)` | `(number: number, significance: number) → number` | Rounds down to nearest multiple of `significance` |
| `int(number)` | `(number: number) → number` | Truncates to integer (floor toward −∞); alias `integer(number)` is identical |
| `trunc(number, num_digits)` | `(number: number, num_digits: number) → number` | Truncates to `num_digits` decimal places (toward zero) |
| `mod(number, divisor)` | `(number: number, divisor: number) → number` | Remainder after division |
| `quotient(numerator, denominator)` | `(numerator: number, denominator: number) → number` | Integer quotient (truncates toward zero) |
| `mround(number, multiple)` | `(number: number, multiple: number) → number` | Rounds to nearest multiple of `multiple` |
| `even(number)` | `(number: number) → number` | Rounds up to nearest even integer |
| `odd(number)` | `(number: number) → number` | Rounds up to nearest odd integer |
| `sign(number)` | `(number: number) → number` | Returns 1, 0, or −1 |
| `degrees(number)` | `(number: number) → number` | Converts radians to degrees |
| `radians(number)` | `(number: number) → number` | Converts degrees to radians |
| `power(number, power)` | `(number: number, power: number) → number` | `number ^ power` |
| `sqrt(number)` | `(number: number) → number` | Square root |
| `exp(number)` | `(number: number) → number` | `e ^ number` |
| `ln(number)` | `(number: number) → number` | Natural log |
| `log(number, base)` | `(number: number, base: number) → number` | Log base `base` |
| `log10(number)` | `(number: number) → number` | Log base 10 |
| `pi()` | `() → number` | π |
| `rand()` | `() → number` | Random number [0, 1) |
| `randbetween(bottom, top)` | `(bottom: number, top: number) → number` | Random integer in [bottom, top] |

---

## 8. CALC — String

Available without `CALC.` prefix.

| Function | Signature | Returns |
|----------|-----------|---------|
| `len(string)` | `(string: string) → number` | Character count |
| `left(string, num_chars)` | `(string: string, num_chars: number) → string` | First `num_chars` characters |
| `right(string, num_chars)` | `(string: string, num_chars: number) → string` | Last `num_chars` characters |
| `mid(string, start_num, num_chars)` | `(string: string, start_num: number, num_chars: number) → string` | Substring; `start_num` is 1-based |
| `lower(string)` | `(string: string) → string` | Lowercase |
| `upper(string)` | `(string: string) → string` | Uppercase |
| `proper(string)` | `(string: string) → string` | Title case |
| `trim(string)` | `(string: string) → string` | Removes all extra spaces (collapses internal runs too) |
| `find(find_string, string, start_pos?)` | `(find_string: string, string: string, start_pos: number) → number` | 1-based position; case-sensitive; returns 0 if not found |
| `search(find_string, string, start_pos?)` | `(find_string: string, string: string, start_pos: number) → number` | Same as `find` but case-insensitive |
| `replace(string, start_pos, num_chars, new_string)` | `(string: string, start_pos: number, num_chars: number, new_string: string) → string` | Replaces `num_chars` chars at `start_pos` (1-based) |
| `substitute(string, old, new, instance_num?)` | `(string: string, old: string, new: string, instance_num: number) → string` | Replaces all (or Nth) occurrences of `old` with `new` |
| `concatenate(string[])` | `(string: string[]) → string` | Joins an array of strings |
| `rept(string, times)` | `(string: string, times: number) → string` | Repeats `string` `times` times |
| `exact(string1, string2)` | `(string1: string, string2: string) → bool` | Case-sensitive equality check |
| `text(double, format)` | `(double: number, format: string) → string` | Formats number as string using `DecimalFormat` pattern |
| `value(string)` | `(string: string) → number` | Parses string to number |
| `character(number)` | `(number: number) → string` | Character from ASCII/Unicode code point; also callable as `char(number)` |
| `code(string)` | `(string: string) → number` | ASCII/Unicode code of first character |
| `t(string)` | `(string: string) → string` | Returns `string` if it is text, empty string otherwise (useful for type-safe string coercion) |
| `dollar(number, decimals)` | `(number: number, decimals: number) → string` | Formats as `$1,234.56` |
| `fixed(number, decimals, no_comma?)` | `(number: number, decimals: number, no_comma: bool) → string` | Formats number with fixed decimals; `no_comma=true` omits thousands separator |

---

## 9. CALC — Logical

Available without `CALC.` prefix. Prefer these over JS `&&`/`||` in expression columns.

| Function | Signature | Returns |
|----------|-----------|---------|
| `iif(condition, value_if_true, value_if_false)` | `(condition: bool, a: any, b: any) → any` | Ternary — equivalent to `condition ? a : b` |
| `and(conditions)` | `(conditions: any) → bool` | Logical AND of all arguments |
| `or(conditions)` | `(conditions: any) → bool` | Logical OR of all arguments |
| `not(condition)` | `(condition: bool) → bool` | Logical NOT |

```js
iif(isNull(field['DISCOUNT']), field['PRICE'], field['PRICE'] * (1 - field['DISCOUNT']))
iif(field['QTY'] > 0, field['REVENUE'] / field['QTY'], 0)   // divide-by-zero guard
```

---

## 10. Common Patterns

```js
// Null-safe division
field['QTY'] > 0 ? field['REVENUE'] / field['QTY'] : 0

// Days between two date columns
dateDiff('d', field['ORDER_DATE'], field['SHIP_DATE'])

// Age in years
dateDiff('yyyy', field['BIRTH_DATE'], today())

// Month + year label
left(monthname(field['DATE']), 3) + '-' + year(field['DATE'])

// Classify a numeric column
iif(field['SCORE'] >= 90, 'A', iif(field['SCORE'] >= 80, 'B', iif(field['SCORE'] >= 70, 'C', 'F')))

// Extract year-month as integer (YYYYMM)
year(field['DATE']) * 100 + month(field['DATE'])

// Truncate a string and add ellipsis
len(field['NAME']) > 20 ? left(field['NAME'], 17) + '...' : field['NAME']
```

---

## What NOT to use in expression columns

| Function / Category | Reason |
|---------------------|--------|
| CALC statistical (`stdev`, `correl`, `average`, etc.) | Require an array argument — not per-row |
| CALC financial (`pv`, `fv`, `npv`, etc.) | Require rate/period scalar inputs and are primarily useful for financial data rows (loan records etc.); available but rarely needed in general analytics |
| CALC fiscal (`fiscalmonth`, `fiscalyear`, etc.) | Available but require org-specific fiscal calendar configuration (`startMonth`, `startDay`, `yearsWith53Weeks`); omit unless you know the org's fiscal setup |
| `runQuery`, `saveWorksheet`, `appendRow`, `setCellValue` | Worksheet-script context, not expression |
| `alert`, `confirm`, `log` | UI / debug only |
| `importPackage`, `importClass`, `newInstance` | Java interop — expression context does not support |
| `setupGoogleMapsPlot`, `createBulletGraph` | Chart/viewsheet script context only |
| `inGroups`, `intersect`, `union` | Aggregation context, not per-row expressions |
