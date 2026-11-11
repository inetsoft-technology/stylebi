# Reproduction kit

Everything needed to rebuild the live captures on the family pages, so the design can be compared with a real build later. It is optional. The design pages stand on their own.

## What is here

- `csv/`: the made-up data behind every capture. Each file becomes one table in a worksheet.
- `charts.md` and `charts.json`: every chart that was built, with its chart style and the fields on each shelf and slot.
- `scripts/`: the browser automation used to build the dashboards and take the captures. It drives the composer in a Chrome started with remote debugging. It is helper code and not production quality. Read it as a worked example of the steps.

## Worksheets and dashboards

| Family | Worksheet | Tables (CSV files) | Dashboard |
|---|---|---|---|
| Financial | ACME Prices | candle_*.csv, box_*.csv | Chart Defaults - Financial Charts |
| Bar and interval | Bar Data | bar_data.csv, bar_neg.csv, bar_range.csv | Chart Defaults - Bar Charts |
| Line and area | Line Data | line_data.csv | Chart Defaults - Line Charts |
| Point and contour | Point Data | pt_*.csv, geo_sales.csv | Chart Defaults - Point Charts |
| Pie and donut | Pie Data | pie_data.csv | Chart Defaults - Pie Charts |
| Radar | Radar Data | radar_data.csv | Chart Defaults - Radar Charts |
| Hierarchy | Hierarchy Data | hier_data.csv | Chart Defaults - Hierarchy Charts |
| Relation | Relation Data | rel_*.csv | Chart Defaults - Relation Charts |
| Gantt | Gantt Data | gantt_*.csv | Chart Defaults - Gantt Charts |
| Funnel, waterfall, pareto, marimekko | Funnel Data | funnel_*.csv, waterfall_*.csv, pareto_*.csv, mekko_*.csv | Chart Defaults - Funnel and Waterfall Charts |
| Geo | Geo Data | geo_states.csv, geo_states_few.csv, geo_countries.csv, geo_sales.csv | Chart Defaults - Geo Charts |

## Setup

1. A StyleBI build with the visualization changes, running on `http://localhost:8080`, with the org's modern visualization turned on (Dashboard Options shows Modern Visualization). The captures use the current white card with a thin border, because the translucent card was not in the build yet.
2. Chrome started with `--remote-debugging-port=9222`, and signed in to the composer. The scripts attach to that browser. They do not log in.
3. `npm install` in `scripts/` (puppeteer-core), and Node 18 or later.

## Steps

1. In the composer, create a Data Worksheet, upload each CSV as a table with Add, Upload File, and save it with the name above.
2. Create a Dashboard on that worksheet with the dashboard name above, and make sure it is marked modern.
3. Build each chart in the Full Editor from `charts.md`: choose the chart style, drop the fields, finish, then rename the chart title. `scripts/build_f1.js` is the generic builder. `build_geo_base.js` adds the Set Geographic step and `build_g3.js` adds the Convert To Measure step.
4. For the small and large card captures, resize the chart with the top-left handle (`rs_small.js`, `rs_large.js`), to exactly 300x180 and 800x480.
5. Save the dashboard, then capture each chart with `cap_title.js "<chart title>" <file.png>`. Check Preview and the portal viewer separately.

## Things that cost time

- **Gantt:** Start, End and Milestone take date measures only. In the chart editor tree, right-click each date field and choose Convert To Measure, then drop it. The task field goes on the Y shelf. On X, the chart is drawn wrong.
- **Map:** a State or Country column must be marked geographic first (right-click, Set Geographic, accept the defaults), in each chart, before it can be dropped on the Geographic shelf.
- **Marimekko:** the second dimension goes on the third shelf, Break By. Put it on Color too, or every segment is one colour.
- **Radar with 12 spokes:** the 12th measure lands beyond a 2400px window. Use a wider window while dropping the fields (`setViewport` 3600 wide), then return to 2400. Do not use the wide window for the 6-spoke charts.
- **Session timeout:** a "Session Timeout" dialog appears after a few minutes of inactivity and dims the page. Click Stay Logged In before capturing, or the captures come out dimmed. `cap_title.js` does this.
- **Headless Chrome can stall** after long runs (Network.enable timeouts). Close the tab, open a new one at the composer, and reopen the dashboard with `reopen3.js`, answering No to the autosave prompt (`g_no.js`).
- **Save the dashboard** (`savedash.js`) after every build step. Charts built but not saved are lost on reload. This happened once with the hierarchy variants.
