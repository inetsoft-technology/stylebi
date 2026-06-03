# Changelog

All notable changes to StyleBI are documented here, organized by category.

## [1.1.0] - 2026-06-02

### Charts
- Added rounded corner style option for bar charts, including bars produced by date-comparison calculations; option to round bottom corners only is available separately
- Added Smooth Lines option for Line, Area, and Circular Network chart types
- Added Labels on Opposite Side option; axis labels can be moved to the opposite edge of the chart area
- Added SVG as a supported shape type in Add Shape
- Added Legend Symbol Size property to control the size of legend marker icons
- Added "On click only" tooltip mode for chart (`ChartTipOnClick`) and table (`TableTipOnClick`) components
- Added `axis.labelOnSecondaryAxis` script property for placing axis labels on the secondary axis
- Removed 3D Bar and 3D Pie chart types; existing 3D charts automatically display in their 2D equivalents
- Fixed axis misalignment when toggling Labels on Opposite Side on Pareto and other charts
- Fixed axis titles unintentionally following labels to the opposite side
- Fixed rounded corner rendering on stacked bar segments near stack edges
- Fixed rounded bar corners rendering as ellipses in PDF export
- Fixed selection overlay mismatch on stacked bars with rounded corners
- Fixed bar rounding user interface option shown for date-comparison bars on non-bar chart types
- Fixed donut chart format not applying in the dashboard wizard
- Fixed interval chart not rendering when a percent-of-total calculated column is used
- Fixed date-comparison change-from-previous-year incorrectly dropping the quarter constraint
- Fixed date-comparison chart secondary Y-axis not rendering when all change-column values are null
- Fixed date-comparison chart previous-year lookup finding the wrong quarter
- Fixed chart value-of-previous failing at period boundaries
- Fixed chart snapshot shifted and invisible during refresh
- Fixed legend property changes incorrectly affecting date-comparison legends
- Fixed scalar legend labels being clipped and selection border cut off
- Fixed chart title hyperlink lost when exporting to PDF
- Fixed hyperlinks on chart title and empty plot area not rendering in PDF exports
- Fixed brushed donut chart brush overlay arcs incorrect in multi-style charts
- Fixed chart type detection incorrectly hiding plot options for date-comparison and multi-style charts
- Fixed "Binding measures on both X and Y" error for charts with discrete measures
- Fixed incomplete chart image returned when concurrent requests arrive before initialization completes
- Fixed interval chart bars empty on previous-year trend charts
- Fixed date-comparison chart DataGroup appearing on the top X-axis with custom date periods

### Dashboard & Composer
- Added Bottom Tabs layout option for tab containers
- Added Round Bottom Corners Only option for tab containers
- Added quick-switch overlay button on Selection List and Selection Tree for toggling single/multi-selection mode
- Added Hide INFO Notifications option to Dashboard Options
- Added search box to the Create/Open Dashboard dialog in the portal
- Added search box to filter components (Selection List etc.)
- Added Compatible Tables property for filter components
- Added query execution time display in the Composer query and data preview panes
- Renamed "Previous"/"Next" to "Undo"/"Redo" in Dashboard Toolbar Options in Enterprise Manager
- Fixed dropdown components overlapping the bottom tab strip in tab containers
- Fixed embedded dashboard sized at (100, 20) when added to a bottom-tabs container
- Fixed bottom-tabs child positioning in the Composer print layout
- Fixed tab corner and border rendering for bottom tabs in PDF export
- Fixed bottom-tabs positioning for calendar and selection components
- Fixed non-dropdown selection component positioning in bottom-tabs containers
- Fixed tab bar position shifting when an input label changes in a bottom-tabs container
- Fixed bottom tabs positioning in device layouts with scale-to-screen
- Fixed dropdown calendar/selection overlapping the bottom-tabs strip
- Fixed input components with top/bottom labels overlapping in bottom-tabs containers
- Fixed bottom tab component positioning in print layout PDF export
- Fixed drill tabs cleared when returning from the binding editor
- Fixed sibling tabs and server-side sessions not preserved when returning from the binding editor
- Fixed NullPointerException when loading an imported dashboard file with old object column data
- Fixed wizard temporary assemblies being added to dashboard undo checkpoints
- Fixed variable assembly not renamed when the variable name changes; dependent references also updated
- Fixed repository tree crash on session expiry
- Fixed portal appearing disabled after presentation settings reset-to-default

### AI Assistant
- Added AI Assistant panel to portal dashboard viewer, Composer binding pane, and portal wizard pane
- Added collapse/expand control for the AI Assistant panel
- Added branding/theming configuration: custom title, vendor name, logo URL, and portal color scheme integration
- Added support for server storage paths in AI Assistant logo URL
- AI Assistant context type now updates when switching sheet focus in the Composer
- Fixed AI Assistant icon unresponsive in portal wizard pane
- Fixed AI Assistant icon unresponsive in portal binding pane
- Fixed AI Assistant icon appearing smaller in Composer toolbar than in portal navbar
- Fixed duplicate AI Assistant panels when opening the binding pane in Composer
- Fixed panel unexpectedly expanding when navigating to the binding editor
- Fixed AI Assistant panel lagging behind the mouse during resize
- Fixed AI Assistant font styling in Enterprise Manager

### Scheduling
- Added Copy action for scheduled tasks
- Added Schedule Move Task type
- Fixed NullPointerException when importing a schedule task with a null completion condition task name
- Fixed navigation loop and distribution permission handling in schedule tasks
- Enforced schedule option permissions server-side when saving schedule tasks
- Hidden Schedule Tasks node in Enterprise Manager content tree for users without scheduler permission

### Data & Queries
- Added Format Date Values Sent to Query setting for radio button, check box, and combo box components
- Added Switch To Manual Input option for Group Data By Dimension in the worksheet
- Set default JDBC fetch size to 10,000 rows per batch to prevent full-table loads into memory
- Fixed column format metadata not preserved when re-importing dashboard snapshot files
- Fixed form table import throwing a 500 error when the backup sheet is absent or the table name contains underscores
- Fixed expression references in formula columns not scanned for asset dependency tracking
- Fixed NullPointerException when opening a logical model after importing a data source to a folder
- Fixed NullPointerException when renaming a data source twice with a worksheet open in the Composer
- Fixed change-from-previous returning empty results for string dimensions with a sorted dataset filter
- Fixed query execution time displaying as 0ms when the query completes very quickly

### Drill-Down and Hyperlinks
- Added additional hyperlink type options to Drill-Down Into Data configuration
- Added ability to send a parameter value to a form component via a drill-down action
- Fixed "Send Dashboard Parameters" not working when clicking on an empty plot area or chart title

### Security & Administration
- Added Copy and Paste for permissions in Set Repository Permissions and Set Security Actions
- Added Copy and Paste for identities in Create Group, Create Role, and Create User
- Added AI Assistant permission to Set Security Actions
- Fixed Materialize permission check to require WRITE instead of ADMIN
- Fixed Enterprise Manager repository permission check for user-scoped assets
- Fixed Materialized View analysis permission check for private dashboards and worksheets
- Fixed NullPointerException when uninstalling multiple plugins when one fails
- Fixed cloned organization theme deleted when parent organization theme is deleted
- Fixed organization settings blocked when multi-tenancy is disabled
- Fixed non-site-admins unable to manage themes when multi-tenancy is disabled
- Fixed theme files not properly copied on organization clone
- Fixed login page shown in portal when security is not enabled after server restart
- Fixed duplicate dashboards displayed in Enterprise Manager Monitoring in a cluster environment

### Scripting
- Added 16 new script properties for axis and data label positioning
- Added `axis.labelOnSecondaryAxis` script property
- Added `script.cursor.top` server property
- Added `em.home.link` server property
- Fixed slider `labelVisible` script property incorrectly targeting tick labels instead of the component label

### Input Components & Forms
- Fixed input label overlap and clipping in PDF and SVG exports
- Fixed bottom label not displaying in the print layout design pane
- Fixed text input bottom border not visible when component height is small
- Fixed combo box selection resetting to original value on first change after a binding pane commit
- Fixed combo box top label not shown in PDF and print layout
- Fixed slider left/right label not centered vertically in PDF export
- Fixed slider left label overlapping the widget in Excel export
- Fixed slider background rounded corner not applied in the portal viewer
- Fixed input component labels handled correctly in Excel and PowerPoint export
