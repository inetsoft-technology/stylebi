# StyleBI v2.1 Release Notes

## Upgrade Notes

The following changes may affect existing installations or user workflows when upgrading to v2.1. These notes cover upgrading from StyleBI v2.0 to v2.1.

**"Not Equal To" Conditions No Longer Return Null Values:**
A "not equal to" condition that StyleBI applies itself no longer returns the rows where the column value is null. This matches the same condition on a database column and the other negated conditions (not one of, not like, not starting with, not containing). StyleBI applies the condition itself for embedded tables, tabular and REST data sources, script expression columns, and post-aggregate (group) and crosstab conditions. Rows with an empty string value are still returned. Dashboards and Data Worksheets that use such a condition may show fewer rows and different totals after upgrading. To keep the rows with a null value, add an "or [column] is null" condition. Highlight conditions, chart color conditions, and conditions sent to the database are not changed.
