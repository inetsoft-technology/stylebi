# StyleBI v2.1 Release Notes

## Upgrade Notes

The following changes may affect existing installations or user workflows when upgrading to v2.1. These notes cover upgrading from StyleBI v2.0 to v2.1.

**"Not Equal To" Conditions No Longer Return Null Values:**
A "not equal to" condition that StyleBI applies itself no longer returns the rows where the column value is null. This matches the same condition on a database column and the other negated conditions (not one of, not like, not starting with, not containing). StyleBI applies the condition itself for embedded tables, tabular and REST data sources, script expression columns, and post-aggregate (group) and crosstab conditions. Rows with an empty string value are still returned. Dashboards and Data Worksheets that use such a condition may show fewer rows and different totals after upgrading. To keep the rows with a null value, add an "or [column] is null" condition. Highlight conditions (on Tables, Crosstabs, and Charts) and conditions sent to the database are not changed.

**CALC.edate Keeps the Day of the Month Across Years:**
CALC.edate now returns the same day of the target month, or the last day of that month when the day does not exist, also when the result is in another year. It now gives the same result as dateAdd("m") and Excel EDATE. Previously, a negative month shift into the previous year always returned the last day of the month (2001-01-15 minus 1 month gave 2000-12-31, now 2000-12-15), and a positive shift into the next year could roll over into the following month (2000-12-31 plus 2 months gave 2001-03-03, now 2001-02-28). Calculations that shift a date across a year boundary may show different dates after upgrading.
