# CALC Object Functions (StyleBI Common Script Reference)

Source: https://www.inetsoft.com/docs/stylebi/InetSoftUserDocumentation/1.0.0/commonscript/CalcObjectFunctions.html
(+ its 6 category pages: `CalcDateTimeFunctions`, `CalcFinancialFunctions`, `CalcLogicalFunctions`,
`CalcMathFunctions`, `CalcStatisticalFunctions`, `CalcTextFunctions`)

The `CALC` object is a static library of Excel-formula-style functions, usable without
instantiation: `CALC.average([1,2,3])`. **Unless noted otherwise below, each function returns the
same result as the Excel function of the similar name** — that promise is stated on every category
page, so for anything that maps cleanly to a well-known Excel function, standard Excel semantics
apply. A handful of functions have no Excel equivalent at all (StyleBI's own additions) — those are
called out explicitly in their own row/note below, since "same as Excel" does not apply to them.

This file is a compressed **signature + one-line description** table per category, not one page
per function — each of the ~225 individual function pages on the doc site follows an identical
boilerplate template (description, parameters restating the signature, one example, then four
identical paragraphs about how to reference data from a Worksheet Expression Column / Calculated
Field / Property Expression / Dashboard Script). That shared boilerplate is captured once in
**How to pass data to a CALC function**, below, instead of per function.

## How to pass data to a CALC function

- **Data Worksheet Expression Column or Dashboard Calculated Field**: use `field['Column Name']`,
  e.g. `CALC.abs(field['Cost'])`.
- **Dashboard global script, component script, or property script**: use the scope's own data
  keywords — `value`, `selectedObjects`, `data`, `table`, `parameter` — e.g.
  `CALC.abs(Slider1.selectedObject)`, `CALC.abs(parameter.Cost)`.
- **Array-taking functions** (`average`, `sum`, `stdev`, etc.) also accept a Data Worksheet data
  block's column directly by name: `CALC.average(Sales['Total'])`.

## Day-count `basis` parameter (Date/Time and Financial functions)

Several Date/Time and Financial functions take an optional `basis` parameter for the day-count
convention:

| Code | Day Count Convention |
|---|---|
| 0 | US 30/360 (default) |
| 1 | Actual/Actual |
| 2 | Actual/360 |
| 3 | Actual/365 |
| 4 | European 30/360 |

---

## Date and Time Functions

| Function | Description |
|---|---|
| `CALC.datevalue(date)` | Converts a date-formatted string to a date serial value. |
| `CALC.day(date)` | Day-of-month component of a date. |
| `CALC.days360(start_date, end_date[, method])` | Number of days between two dates using a 360-day year (12 x 30-day months). |
| `CALC.edate(date, months)` | The date that is `months` months before or after `date`. |
| `CALC.eomonth(date, months)` | The last day of the month that is `months` months before or after `date`. |
| `CALC.fiscalyear(date, startMonth [,startDay,timeZone])` | **Not an Excel function.** For a fiscal calendar with an arbitrary start date, returns the fiscal year (e.g. `2014`) containing `date`. `startMonth`/`startDay` set the calendar's start; `startDay` defaults to 1. |
| `CALC.fiscalyear445(date, startYear, startMonth, startDay, leapYears[,timeZone])` | **Not an Excel function.** Fiscal year under a **4-4-5 calendar** (each quarter = 4 weeks + 4 weeks + 5 weeks, 52 weeks/364 days in a normal year). `leapYears` is an explicit array of years that get a 53rd week (added to the last month of Q4). |
| `CALC.fiscalyear454(date, startYear, startMonth, startDay, leapYears[,timeZone])` | Same as `fiscalyear445` but for a **4-5-4** week pattern per quarter. |
| `CALC.fiscalyear544(date, startYear, startMonth, startDay, leapYears[,timeZone])` | Same as `fiscalyear445` but for a **5-4-4** week pattern per quarter. |
| `CALC.fiscalquarter(date, startMonth [,startDay,timeZone])` | Fiscal quarter (1-4) for an arbitrary-start fiscal calendar. Same parameters as `fiscalyear`. |
| `CALC.fiscalquarter445(date, startYear, startMonth, startDay, leapYears[,timeZone])` | Fiscal quarter under a 4-4-5 calendar. |
| `CALC.fiscalquarter454(date, startYear, startMonth, startDay, leapYears[,timeZone])` | Fiscal quarter under a 4-5-4 calendar. |
| `CALC.fiscalquarter544(date, startYear, startMonth, startDay, leapYears[,timeZone])` | Fiscal quarter under a 5-4-4 calendar. |
| `CALC.fiscalmonth(date, startMonth [,startDay,timeZone])` | Fiscal month number for an arbitrary-start fiscal calendar. |
| `CALC.fiscalmonth445(date, startYear, startMonth, startDay, leapYears[,timeZone])` | Fiscal month under a 4-4-5 calendar. |
| `CALC.fiscalmonth454(date, startYear, startMonth, startDay, leapYears[,timeZone])` | Fiscal month under a 4-5-4 calendar. |
| `CALC.fiscalmonth544(date, startYear, startMonth, startDay, leapYears[,timeZone])` | Fiscal month under a 5-4-4 calendar. |
| `CALC.fiscalweek(date, startMonth [,startDay,timeZone])` | Fiscal week number for an arbitrary-start fiscal calendar. |
| `CALC.fiscalweek445(date, startYear, startMonth, startDay, leapYears[,timeZone])` | Fiscal week under a 4-4-5 calendar. |
| `CALC.fiscalweek454(date, startYear, startMonth, startDay, leapYears[,timeZone])` | Fiscal week under a 4-5-4 calendar. |
| `CALC.fiscalweek544(date, startYear, startMonth, startDay, leapYears[,timeZone])` | Fiscal week under a 5-4-4 calendar. |
| `CALC.hour(date)` | Hour component (0-23). |
| `CALC.minute(date)` | Minute component (0-59). |
| `CALC.month(date)` | Month component (1-12). |
| `CALC.monthname(date)` | Month name as text. |
| `CALC.networkdays(start_date, end_date[, holiday_array])` | Number of whole working days (excludes weekends and an optional holiday list) between two dates. |
| `CALC.now()` | Current date and time. |
| `CALC.quarter(date)` | Quarter of the year (1-4). |
| `CALC.second(date)` | Second component (0-59). |
| `CALC.time(hour, minute, second)` | Constructs a time value from hour/minute/second. |
| `CALC.timeValue(date)` | The time-of-day portion of a date, as a fraction of a 24-hour day. |
| `CALC.today()` | Current date (no time component). |
| `CALC.weekday(date [,number])` | Day of the week as a number; `number` selects which numbering scheme (1=Sunday..7, etc., matching Excel's `return_type` codes). |
| `CALC.weekdayname(date)` | Day of the week as text. |
| `CALC.weeknum(date)` | Week number within the year. |
| `CALC.workday(date, days, [holidayArray])` | The date that is `days` working days before/after `date`, skipping weekends and an optional holiday list. |
| `CALC.year(date)` | Year component. |
| `CALC.yearfrac(start_date, end_date [,basis])` | Fraction of a year between two dates, using the day-count `basis` above. |

## Financial Functions

| Function | Description |
|---|---|
| `CALC.accrint(issue, interest, settlement, rate, par, freq [, basis])` | Accrued interest for a security that pays periodic interest. |
| `CALC.accrintm(issue, maturity, rate, par, [basis])` | Accrued interest for a security that pays interest at maturity. |
| `CALC.amordegrc(cost, date_purchased, first_period, salvage, period, rate, [basis])` | Depreciation for each accounting period (French accounting system, degressive method). |
| `CALC.amorlinc(cost, date_purchased, first_period, salvage, period, rate, [basis])` | Depreciation for each accounting period (French accounting system, linear method). |
| `CALC.coupdaybs(settlement, maturity, frequency [,basis])` | Days from the start of the coupon period to the settlement date. |
| `CALC.coupdays(settlement, maturity, frequency, [basis])` | Days in the coupon period containing the settlement date. |
| `CALC.coupdaysnc(settlement, maturity, frequency, [basis])` | Days from settlement to the next coupon date. |
| `CALC.coupncd(settlement, maturity, frequency, [basis])` | Next coupon date after settlement. |
| `CALC.coupnum(settlement, maturity, frequency, [basis])` | Number of coupons payable between settlement and maturity. |
| `CALC.couppcd(settlement, maturity, frequency, [basis])` | Previous coupon date before settlement. |
| `CALC.cumipmt(rate, nper, pv, start_period, end_period, type)` | Cumulative interest paid between two periods of a loan. |
| `CALC.cumprinc(rate, nper, pv, start_period, end_period, type)` | Cumulative principal paid between two periods of a loan. |
| `CALC.db(cost, salvage, life, period, [month])` | Depreciation using the fixed-declining-balance method. |
| `CALC.ddb(cost, salvage, life, period, [factor])` | Depreciation using the double-declining-balance method. |
| `CALC.disc(settlement, maturity, pr, redemption, [basis])` | Discount rate for a security. |
| `CALC.duration(settlement, maturity, rate, yld, frequency, [basis])` | Annual (Macaulay) duration — weighted average time to receive cash flows. |
| `CALC.effect(nominal_rate, npery)` | Effective annual interest rate from a nominal rate and compounding periods. |
| `CALC.fv(rate, nper, pmt, [pv], [type])` | Future value of an investment/loan. |
| `CALC.fvschedule(principal, schedule)` | Future value of a principal after applying a series of (possibly varying) interest rates. |
| `CALC.intrate(settlement, maturity, investment, redemption, [basis])` | Interest rate for a fully invested security. |
| `CALC.ipmt(rate, per, nper, pv, fv, type)` | Interest payment for a given period of an investment. |
| `CALC.ispmt(rate, per, nper, pv)` | Interest paid for a specific period, for a loan with level principal payments. |
| `CALC.mduration(settlement, maturity, rate, yld, frequency, [basis])` | Modified Macaulay duration. |
| `CALC.mirr(values, finance_rate, reinvest_rate)` | Modified internal rate of return, given a financing rate and a reinvestment rate. |
| `CALC.nominal(effect_rate, nper)` | Nominal annual interest rate from an effective rate. |
| `CALC.nper(rate, pmt, pv, fv, type)` | Number of periods for an investment/loan. |
| `CALC.npv(rate, values)` | Net present value of a series of cash flows at a fixed discount rate. |
| `CALC.pmt(rate, nper, pv, fv, type)` | Periodic payment for a loan/annuity. |
| `CALC.ppmt(rate, per, nper, pv, fv, type)` | Payment on the principal for a given period. |
| `CALC.price(settlement, maturity, rate, yld, redemption, frequency, [basis])` | Price per $100 face value of a security paying periodic interest. |
| `CALC.pricedisc(settlement, maturity, discount, redemption, [basis])` | Price per $100 face value of a discounted security. |
| `CALC.pricemat(settlement, maturity, issue, rate, yld, [basis])` | Price per $100 face value of a security paying interest at maturity. |
| `CALC.pv(rate, nper, pmt, fv, type)` | Present value of an investment/loan. |
| `CALC.received(settlement, maturity, investment, discount, [basis])` | Amount received at maturity for a fully invested security. |
| `CALC.sln(cost, salvage, life)` | Straight-line depreciation for one period. |
| `CALC.syd(cost, salvage, life, per)` | Sum-of-years'-digits depreciation for a given period. |
| `CALC.tbilleq(settlement, maturity, discount)` | Bond-equivalent yield for a Treasury bill. |
| `CALC.tbillprice(settlement, maturity, discount)` | Price per $100 face value for a Treasury bill. |
| `CALC.tbillyield(settlement, maturity, pr)` | Yield for a Treasury bill. |
| `CALC.vdb(cost, salvage, life, start_period, end_period, [factor, no_switch])` | Depreciation using declining-balance, with `no_switch` controlling switch to straight-line. |
| `CALC.xirr(values, dates, [guess])` | Internal rate of return for a schedule of cash flows on irregular dates. |
| `CALC.xnpv(rate, values, dates)` | Net present value for a schedule of cash flows on irregular dates. |
| `CALC.yielddisc(settlement, maturity, pr, redemption, [basis])` | Annual yield for a discounted security. |
| `CALC.yieldmat(settlement, maturity, issue, rate, pr, [basis])` | Annual yield for a security paying interest at maturity. |

## Logical Functions

| Function | Description |
|---|---|
| `CALC.and(Boolean_1, Boolean_2, …, Boolean_n)` | `true` if every argument is `true`. |
| `CALC.iif(condition, value_if_true, value_if_false)` | Returns `value_if_true`/`value_if_false` depending on `condition`. Equivalent to the JS ternary operator (`condition ? a : b`), which can be used instead. |
| `CALC.not(Boolean)` | Inverts a boolean. |
| `CALC.or(Boolean_1, Boolean_2, …, Boolean_n)` | `true` if any argument is `true`. |

## Math Functions

| Function | Description |
|---|---|
| `CALC.abs(number)` | Absolute value. |
| `CALC.acos(number)` | Arccosine, in radians. |
| `CALC.acosh(number)` | Inverse hyperbolic cosine. |
| `CALC.asin(number)` | Arcsine, in radians. |
| `CALC.asinh(number)` | Inverse hyperbolic sine. |
| `CALC.atan(number)` | Arctangent, in radians. |
| `CALC.atan2(numberX, numberY)` | Arctangent of the given x,y coordinates, in radians. |
| `CALC.atanh(number)` | Inverse hyperbolic tangent. |
| `CALC.ceiling(number, significance)` | Rounds `number` away from zero to the nearest multiple of `significance` (`significance` must share `number`'s sign). |
| `CALC.combin(totalNumber, groupNumber)` | Number of ways to choose `groupNumber` items from `totalNumber`, unordered. |
| `CALC.cos(number)` | Cosine. |
| `CALC.cosh(number)` | Hyperbolic cosine. |
| `CALC.degrees(number)` | Converts radians to degrees. |
| `CALC.even(number)` | Rounds up (away from zero) to the nearest even integer. |
| `CALC.exp(number)` | e raised to `number`. |
| `CALC.fact(number)` | Factorial. |
| `CALC.factdouble(number)` | Double factorial (product of every other integer down to 1 or 2). |
| `CALC.floor(number, significance)` | Rounds `number` toward zero to the nearest multiple of `significance`. |
| `CALC.gcd(array)` | Greatest common divisor. |
| `CALC.int(number)` | Rounds down to the nearest integer. |
| `CALC.lcm(array)` | Least common multiple. |
| `CALC.ln(number)` | Natural logarithm. |
| `CALC.log(number, base)` | Logarithm to the given base. |
| `CALC.log10(number)` | Base-10 logarithm. |
| `CALC.mdeterm(array)` | Determinant of a square matrix. |
| `CALC.minverse(array)` | Inverse of a square matrix. |
| `CALC.mmult(array1, array2)` | Matrix product of two arrays. |
| `CALC.mod(number, divisor)` | Remainder after division. |
| `CALC.mround(number, factor)` | Rounds to the nearest multiple of `factor`. |
| `CALC.multinomial(array)` | Ratio of the factorial of the values' sum to the product of their factorials. |
| `CALC.odd(number)` | Rounds up (away from zero) to the nearest odd integer. |
| `CALC.pi()` | π. |
| `CALC.power(number, power)` | `number` raised to `power`. |
| `CALC.product(array)` | Product of all values. |
| `CALC.quotient(numerator, denominator)` | Integer portion of a division. |
| `CALC.radians(number)` | Converts degrees to radians. |
| `CALC.roman(number)` | Converts an integer to a Roman-numeral string. |
| `CALC.round(number, num_digits)` | Rounds to `num_digits` decimal places. |
| `CALC.rounddown(number, num_digits)` | Rounds toward zero to `num_digits` decimal places. |
| `CALC.roundup(number, num_digits)` | Rounds away from zero to `num_digits` decimal places. |
| `CALC.seriessum(x, n, m, coefficients)` | Sum of a power series at `x`. |
| `CALC.sign(number)` | Sign of a number: `-1`, `0`, or `1`. |
| `CALC.sin(number)` | Sine. |
| `CALC.sinh(number)` | Hyperbolic sine. |
| `CALC.sqrt(number)` | Square root. |
| `CALC.sqrtpi(number)` | Square root of `number × π`. |
| `CALC.subtotal(functionNumber, array)` | Aggregates `array` using one of Excel's `SUBTOTAL` function-number codes (1=AVERAGE, 9=SUM, etc.). |
| `CALC.sum(array)` | Sum of all values. |
| `CALC.sumif(array1, conditionString, array2)` | Sums the values in `array2` where the corresponding `array1` entry matches `conditionString`. |
| `CALC.sumproduct(2Darray)` | Sum of the products of corresponding array components. |
| `CALC.sumsq(array)` | Sum of squares. |
| `CALC.sumx2my2(array1, array2)` | Sum of `(x² − y²)` over corresponding pairs. |
| `CALC.sumx2py2(array1, array2)` | Sum of `(x² + y²)` over corresponding pairs. |
| `CALC.sumxmy2(array1, array2)` | Sum of `(x − y)²` over corresponding pairs. |
| `CALC.tan(number)` | Tangent. |
| `CALC.tanh(number)` | Hyperbolic tangent. |
| `CALC.trunc(number, num_digits)` | Truncates (does not round) to `num_digits` decimal places. |

## Statistical Functions

| Function | Description |
|---|---|
| `CALC.avedev(array)` | Average of the absolute deviations from the mean. |
| `CALC.average(array)` | Arithmetic mean. |
| `CALC.averagea(array)` | Arithmetic mean, counting text as 0 and `TRUE`/`FALSE` as 1/0. |
| `CALC.binomdist(number_s, trials, probability_s, [cumulative])` | Binomial distribution probability. |
| `CALC.correl(array1, array2)` | Pearson correlation coefficient between two arrays. |
| `CALC.count(array)` | Count of numeric values. |
| `CALC.counta(array)` | Count of non-blank values (any type). |
| `CALC.countblank(array)` | Count of blank/empty values. |
| `CALC.countDistinct(array)` | **Not an Excel function.** Count of distinct (unique) values. |
| `CALC.countn(array)` | **Not an Excel function.** Count of numeric elements in a mixed-type array. |
| `CALC.countif(array, condition)` | Count of values matching `condition`. |
| `CALC.covar(array1, array2)` | Covariance of two arrays. |
| `CALC.devsq(array)` | Sum of squared deviations from the mean. |
| `CALC.expondist(x, lambda, cumulative)` | Exponential distribution. |
| `CALC.fisher(x)` | Fisher transformation. |
| `CALC.fisherinv(z)` | Inverse Fisher transformation. |
| `CALC.forecast(x, known_y_values, known_x_values)` | Predicted `y` value at `x` via linear regression. |
| `CALC.frequency(data_array, bins_array)` | Frequency distribution of `data_array` across the `bins_array` buckets. |
| `CALC.geomean(array)` | Geometric mean. |
| `CALC.harmean(array)` | Harmonic mean. |
| `CALC.hypgeomdist(sample_s, number_sample, population_s, number_population)` | Hypergeometric distribution probability. |
| `CALC.intercept(array_Y, array_X)` | Y-intercept of the linear regression line through the given points. |
| `CALC.kurt(array)` | Kurtosis of a distribution. |
| `CALC.large(array, k)` | k-th largest value. |
| `CALC.max(array)` | Maximum value. |
| `CALC.maxa(array)` | Maximum, including text/logical values (text/`FALSE`=0, `TRUE`=1). |
| `CALC.median(array)` | Median. |
| `CALC.min(array)` | Minimum value. |
| `CALC.mina(array)` | Minimum, including text/logical values. |
| `CALC.mode(array)` | Most frequently occurring value. |
| `CALC.negbinomdist(number_f, number_s, probability_s)` | Negative binomial distribution probability. |
| `CALC.nthlargest(array,n)` | **Not an Excel function** (name looks like `LARGE` but isn't the same signature order). n-th largest value; `n=1` is equivalent to `CALC.max(array)`. |
| `CALC.nthmostfrequent(array,n)` | **Not an Excel function.** n-th most frequently occurring value; `n=1` is equivalent to `CALC.mode(array)`. |
| `CALC.nthsmallest(array,n)` | **Not an Excel function.** n-th smallest value, mirroring `nthlargest`. |
| `CALC.pearson(array1, array2)` | Pearson correlation coefficient (same value as `correl`). |
| `CALC.percentile(array, k)` | Value at the k-th percentile. |
| `CALC.percentrank(array, x, [significance])` | Percentage rank of `x` within `array`. |
| `CALC.permut(number, number_chosen)` | Number of ordered permutations. |
| `CALC.poisson(x, lambda, cumulative)` | Poisson distribution probability. |
| `CALC.prob(array_x, arrayProb, lower_limit, [upper_limit])` | Probability that values in `array_x` fall within a range, given their probabilities `arrayProb`. |
| `CALC.pthpercentile(array,p)` | **Maps to Excel's `PERCENTILE.EXC`, not plain `PERCENTILE`.** Value below which `p` percent of values fall (`p` in `[0,100]`); `p=50` is equivalent to `CALC.median(array)`. |
| `CALC.quartile(array, quart)` | Quartile of a dataset (`quart` 0-4). |
| `CALC.rand()` | Random number in `[0,1)`. |
| `CALC.randbetween(bottom,top)` | Random integer in `[bottom,top]`. |
| `CALC.rank(number, array, order)` | Rank of `number` within `array`. |
| `CALC.rsq(array1, array2)` | Square of the Pearson correlation coefficient. |
| `CALC.skew(array)` | Skewness of a distribution. |
| `CALC.slope(array_y, array_x)` | Slope of the linear regression line through the given points. |
| `CALC.small(array, k)` | k-th smallest value. |
| `CALC.standardize(x, mean, standard_dev)` | Normalized value (z-score) of `x`. |
| `CALC.stdev(array)` | Sample standard deviation. |
| `CALC.stdeva(array)` | Sample standard deviation, including text/logical values. |
| `CALC.stdevp(array)` | Population standard deviation. |
| `CALC.stdevpa(array)` | Population standard deviation, including text/logical values. |
| `CALC.steyx(array_y, array_x)` | Standard error of the predicted y for each x in a regression. |
| `CALC.trimmean(array, percent)` | Mean of the interior of a dataset after trimming `percent` of outliers from each end. |
| `CALC.varn(array)` | Sample variance. (Named `varn`, not Excel's `VAR`, to avoid colliding with the JS reserved word `var`.) |
| `CALC.vara(array)` | Sample variance, including text/logical values. |
| `CALC.varp(array)` | Population variance. |
| `CALC.varpa(array)` | Population variance, including text/logical values. |
| `CALC.weibull(x, alpha, beta, cumulative)` | Weibull distribution. |
| `CALC.weightedavg(array1, array2)` | **Not an Excel function.** Average of `array1` weighted by the corresponding entries in `array2` (text evaluates to 0, `true`/`false` to 1/0). |

## Text Functions

| Function | Description |
|---|---|
| `CALC.char(number)` | Character for a given character code. |
| `CALC.code(string)` | Numeric code of a string's first character. |
| `CALC.concatenate(array)` | Joins an array of strings into one string. |
| `CALC.dollar(number, decimals)` | Formats a number as currency text. |
| `CALC.exact(string1, string2)` | `true` if two strings are identical (case-sensitive). |
| `CALC.find(string1, String2, search_index)` | Position of `string1` within `String2`, starting at `search_index` (case-sensitive). |
| `CALC.fixed(number, decimals, no_commas)` | Formats a number as text with a fixed number of decimals. |
| `CALC.left(string, num_chars)` | Leftmost `num_chars` characters. |
| `CALC.len(string)` | String length. |
| `CALC.lower(string)` | Converts to lowercase. |
| `CALC.mid(String, start_num, num_chars)` | Substring starting at `start_num`, `num_chars` long. |
| `CALC.proper(string)` | Capitalizes the first letter of each word. |
| `CALC.replace(old_string, start_num, num_chars, new_string)` | Replaces `num_chars` characters of `old_string`, starting at `start_num`, with `new_string`. |
| `CALC.rept(string, number_times)` | Repeats a string. |
| `CALC.right(string, num_chars)` | Rightmost `num_chars` characters. |
| `CALC.search(find_string, search_string, start_num)` | Position of `find_string` within `search_string`, starting at `start_num` (case-insensitive; wildcards allowed). |
| `CALC.substitute(old_string, rep_string , new_string, instance_num)` | Replaces occurrences of `rep_string` in `old_string` with `new_string` (all, or just `instance_num` if given). |
| `CALC.t(value)` | Returns `value` if it's text, otherwise an empty string. |
| `CALC.text(value, format)` | Formats `value` using an Excel-style format string. |
| `CALC.trim(string)` | Removes leading/trailing spaces and collapses internal runs of spaces to one. |
| `CALC.upper(string)` | Converts to uppercase. |
| `CALC.value(string)` | Converts a numeric-looking text string to a number. |

## General notes

- Functions whose row above is marked **"Not an Excel function"** are StyleBI's own additions —
  don't assume Excel-identical edge-case behavior for those (empty array, ties, etc.); if precise
  edge-case behavior matters, verify against the live server rather than assuming Excel parity.
- Several statistics functions come in `X`/`Xa` pairs (`stdev`/`stdeva`, `var*`/`vara`, `max`/`maxa`,
  `min`/`mina`, `average`/`averagea`) — the `a` variant is the one that also counts text and
  boolean values (as 0/0/1 respectively) instead of ignoring them.
- `CALC.varn` (not `CALC.var`) is deliberately renamed from Excel's `VAR` because `var` is a
  reserved word in JavaScript.
