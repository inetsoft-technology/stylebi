# StyleBI v1.1 Release Notes

*Generated: June 3, 2026*

## Overview

StyleBI v1.1 delivers a new AI Assistant, significant chart enhancements, improved dashboard authoring tools, enhanced security controls, and a versioned documentation system. Below is a summary of the major new capabilities.

---

## Upgrade Notes

The following changes may affect existing installations or user workflows when upgrading to v1.1.

**3D Chart Styles Removed:**
3D Bar and 3D Pie chart styles are no longer supported. Any dashboards using these styles will automatically render using their 2D equivalents. No manual migration is required, but you may wish to review affected dashboards to confirm the updated appearance meets your needs.

**Date-Comparison Calculation Fixes:**
Several corrections were made to date-comparison calculations, including quarter constraint handling in change-from-previous-year, previous-year period lookups, and secondary Y-axis rendering. Existing dashboards using date-comparison charts may display updated results that differ from v1.0.

**JDBC Default Fetch Size Changed:**
The default JDBC fetch size has been changed to 10,000 rows per batch. This prevents entire large tables from being loaded into memory at once and may improve query performance. Installations that previously relied on unbounded fetches should verify query behavior after upgrading.

**Materialized View Permission Level Changed:**
The permission required to materialize assets has been corrected from ADMIN to WRITE. Administrators should review existing permission assignments to ensure access is granted to the intended users and roles.

**Dashboard Toolbar Button Labels Renamed:**
The 'Previous' and 'Next' buttons in 'Dashboard Toolbar Options' have been renamed to 'Undo' and 'Redo'. 

**Slider `labelVisible` Script Property Fix:**
The `labelVisible` script property on slider components previously targeted tick labels instead of the component label. This has been corrected. Existing scripts that used this property to control tick label visibility should be reviewed and updated.

---

## What's New

### AI Assistant

An AI Assistant panel is now available throughout the product. The Assistant can answer natural-language questions about the current environment and help with analysis tasks.

The panel can be collapsed, expanded, and resized by dragging. Organizations can customize it with a custom title, vendor name, and logo, and it automatically picks up the User Portal and Enterprise Manager color scheme. Administrators can control access to the AI Assistant using a dedicated permission in Set Security Actions.

---

### Charts

**Rounded Bar Corners:**
Bar charts now support a rounded-corners style. You can apply rounding to all corners or to the bottom corners only. This option is also available for bars produced by date-comparison calculations.

**Smooth Lines:**
Line, Area, and Circular Network charts now support a 'Smooth Lines' option, which curves the line segments between data points for a more polished appearance.

**Labels on Opposite Side:**
Axis labels can now be placed on the opposite side of the chart from their default position — for example, moving Y-axis labels from left to right.

**SVG Shapes:**
The Add Shape tool in the Chart Editor now supports SVG files, allowing custom vector graphics to be embedded directly in charts.

**Legend Symbol Size:**
A new 'Symbol Size' property lets you control the size of legend marker icons independently of other legend formatting.

**Tooltip on Click:**
A new 'On click only' tooltip option for charts and tables makes tooltips appear only when a user clicks a data point or cell, rather than on hover.

**3D Chart Styles Removed:**
3D bar and 3D pie chart styles have been removed. Existing dashboards that used these styles will automatically display using their 2D equivalents.

---

### Dashboard & Visual Composer

**Bottom Tabs for Tab Containers:**
Tab containers now support a 'Bottom Tabs' layout, moving the tab strip to the bottom of the container. A separate 'Round Bottom Corners Only' option is also available.

**Quick-Switch Selection Mode:**
Selection List and Selection Tree components now show a hover overlay button that lets dashboard users instantly toggle between single-selection and multi-selection mode.

**Hide INFO Notifications in Visual Composer:**
A new 'Dashboard Options' setting suppresses informational toast notifications in the Visual Composer, reducing visual noise during development.

**Dashboard Search in User Portal:**
The Create/Open Dashboard dialog in the User Portal now includes a search box for quickly finding dashboards by name.

**Filter Component Search Box:**
Filter components such as Selection Lists now include an optional search box that lets users filter the displayed values by typing.

**Compatible Tables Property:**
Filter components expose a new 'Compatible Tables' property that controls which data tables in the dashboard the filter applies to.

**Query Execution Time in Preview:**
When previewing query results or table data in the Visual Composer, the elapsed query execution time is now displayed, making it easier to identify slow queries during development.

---

### Scheduling

**Copy Scheduled Tasks:**
Scheduled tasks can now be duplicated with a single action, making it easy to create similar tasks without starting from scratch.

**Schedule Move Task:**
A new Schedule Move Task type is available for programmatically relocating dashboard assets on a schedule.

---

### Data & Queries

**Format Date Values Sent to Query:**
Radio Button, Check Box, and Combo Box components now include a 'Format Date Values Sent to Query' setting. When enabled, date values selected by users are formatted before being passed to the query, improving compatibility with data sources that expect specific date string formats.

**Switch to Manual Input for Group Dimensions:**
When grouping Data Worksheet data by dimension, a new 'Switch To Manual Input' option allows group members to be specified directly rather than detected automatically.

**Improved JDBC Performance:**
The default JDBC fetch size is now 10,000 rows per batch, preventing entire large tables from being loaded into memory during query execution.

---

### Drill-Down and Hyperlinks

Drill-Down Into Data configuration now exposes additional hyperlink types, and drill-down actions can now send a parameter value directly to a form component (such as a Text Input or Combo Box) in the target dashboard — enabling tightly coupled drill-through workflows.

---

### Security & Administration

**Copy and Paste Permissions:**
Repository permissions and security action assignments can now be copied from one asset and pasted to another, reducing repetitive configuration work.

**Copy and Paste Identities:**
When creating or editing groups, roles, and users, identity assignments can be copied and pasted across records.

**Enterprise-Only Connector Labeling:**
The following connectors are now clearly marked as Enterprise Edition only: Facebook Ad Insights, Facebook Page Insights, Google Analytics (GA4), Salesforce, and SAP.

**Refresh Metadata Button:**
Over 60 data source configuration pages now include a 'Refresh Metadata' button, allowing schema and column information to be reloaded without leaving the configuration dialog.

---

### Scripting

Sixteen new script properties are available for controlling axis and data label positioning. Additional new server properties include `axis.labelOnSecondaryAxis`, `script.cursor.top`, and `em.home.link`.

---

### Documentation

StyleBI's online help system now serves version-matched documentation for v1.0 and v1.1. Help links from within the product route to the documentation for the installed version. Additional reference pages have been added for data types, date format patterns, label-positioning script properties, and new UI features introduced in this release.
