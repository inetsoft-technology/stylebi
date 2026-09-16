---
name: composer-chat
description: Use when editing a StyleBI sheet that is open in the Composer — worksheet structure, viewsheet layout and formatting, chart and table binding, or the JavaScript attached to a viewsheet. Covers pairing, the four domains, and when to reach for a script instead of a tool.
---

# Editing a live StyleBI sheet

## When to use this

The user has a sheet **open in the StyleBI Composer** and wants it changed.

Not this skill:
- **Building a chart from data** with no sheet open → viz-chat.
- **Server settings, properties, credentials** → admin-chat.

## Pairing: one code per open sheet

`connect_sheet` takes a pairing code from the Composer toolbar. One code covers every domain that
drives that runtime — layout, binding and script all share a viewsheet session. Ask for a second
code only for a *different* sheet, such as the worksheet behind the viewsheet.

Codes are single-use and expire in seconds. Ask for one immediately before using it, one at a time.

`status` reports which sheets are connected. "No connected worksheet" means exactly that — ask the
user to open it and pair it, rather than retrying.

## The four domains

| Domain | What it covers |
|---|---|
| Worksheet | Columns, joins, filters, aggregates, expressions — the data |
| Viewsheet | Layout, properties, formatting, conditions, highlights, hyperlinks |
| Binding | What an assembly shows: chart shelves, aesthetics, chart type, table/crosstab fields, sort, ranking |
| Script | JavaScript attached to a viewsheet or an expression |

## Prefer the tool. Script is the fallback, not the first move.

If a tool performs the change, use it. An equivalent script is harder to review, is invisible to
the Composer's own editors, and re-runs on every refresh.

| If asked to… | Use | Not |
|---|---|---|
| change a chart's type | `set_chart_type` | an `assemblyMain` script |
| bind or re-bind a field | `set_chart_shelf`, `set_table_fields` | a binding script |
| hide an axis, legend or title | `set_chart_element_visibility` | `graph.axis.visible = false` |
| colour, font, alignment, number format | `set_format` | a format script |
| conditional colour by value | `set_highlight` | a script testing the value |
| filter an assembly | `set_condition` | a script filtering the data |
| move, resize, group, align | `edit` | positional script |
| show or hide an assembly | `set_assembly_properties` | `assembly.visible = false` |
| sort or top-N | `set_field_sort`, `set_field_ranking` | a sorting script |

**Script is the right answer** for behaviour no tool exposes: cross-assembly coordination on
`onInit`/`onLoad`, `onClick` interactions, computed values that depend on runtime state, and
anything reached through the graph API with no shelf or property equivalent.

## Choosing a script kind

Once script is the right answer, decide **where** with this table before
calling any script tool -- all of it is fixed by the product, not by what's
open right now:

| Kind | Runs | Use it for |
|---|---|---|
| `viewsheetOnInit` | once, when the sheet opens | one-time setup: seed a variable, set an initial default |
| `viewsheetOnLoad` | on every refresh (open + every later refresh/parameter change) | cross-assembly coordination that must stay current: sync one assembly from another's live state, recompute something on every refresh |
| `assemblyMain` | each time that one assembly renders | behavior scoped to a single assembly's own rendering |
| `assemblyOnClick` | on user click | interactive handlers -- only Text, Image, Submit, and TextInput support this; `list_script_targets` omits it for every other assembly type |
| `calcField` | per row, when the field is evaluated | a value derived from other columns, wanted as a reusable named field bindable/displayable like any other column, scoped to this viewsheet |
| `worksheetExpression` | per row, in the worksheet's own query | the same shape as `calcField`, one layer down -- shared by every viewsheet built on that worksheet |
| `worksheetCondition` | per row, as a filter test | replaces a WHOLE condition (operator + every value) |
| `worksheetConditionValue` | per row, as part of a filter test | narrower -- only the VALUE of an existing single-value condition, never its operator |

One target this table does not cover: a calc (freehand) table's own
cells. A calc table's per-cell formula/script is a 9th place, entirely
outside the 8 kinds above -- it has its own addressing ({assembly, row,
col}, not kind+assembly+name) and its own tools
(set_calc_cell_script/get_calc_cell_script), and list_script_targets
does not enumerate it. Check this before starting the walk below:

- Is the target assembly a calc (freehand) table, and is the request
  scoped to one specific cell's value (e.g. "put a running total in this
  cell", "this cell should show Sum(Sales)")? -> Stop here -- none of the 8
  kinds apply. Use set_calc_cell_script/get_calc_cell_script. See the
  Binding section's calc-table paragraph for the cell's binding vocabulary
  (content/grouping/expand) that the script sits on top of.
- Is the request about the calc table's own overall rendering or a click
  on it, not one cell's value? -> A calc table is an ordinary assembly for
  this purpose -- continue to step 1 below; assemblyMain/assemblyOnClick
  apply to it exactly as to any other assembly.

**The decision, as a walk:**

1. **Is it a value, not an action** -- something you'd want to reference
   elsewhere as a column or field (bind it, display it, sort by it), with no
   side effects? -> `calcField` if it's scoped to this viewsheet,
   `worksheetExpression` if it belongs on the worksheet itself (shared across
   viewsheets).
2. **Is it a filter/inclusion test on rows?** -> `worksheetCondition` to
   replace the whole condition, `worksheetConditionValue` to change only the
   value of an existing single-value condition without touching its
   operator.
3. **Otherwise it's an imperative action.** Ask when it must run:
   exactly once at open -> `viewsheetOnInit`; every refresh, or it
   coordinates more than one assembly -> `viewsheetOnLoad`; scoped to one
   assembly's own rendering -> `assemblyMain`; triggered by a click ->
   `assemblyOnClick` (only on a click-capable assembly).

**When two kinds both still look right, ask -- don't guess.** The classic
case: "recalculate this value when the page loads" is genuinely ambiguous
between `calcField` (an automatically-recomputed column) and
`viewsheetOnLoad` (an imperative recompute-and-assign on every refresh). The
tie always comes down to step 1's question -- is "this value" a named
field the user would reference elsewhere, or a one-off side effect (a
label's text, a visibility flag, a non-field property)? If the request
doesn't say, ask which one is meant rather than picking either -- the same
fail-loud principle `scriptTarget.ts` already applies to an ambiguous legacy
target string: a silently-wrong kind produces a plausible result that only
breaks later, in a way that's harder to trace back to the real cause than a
clarifying question would have been.

**`list_script_targets` already tells you the answer for anything that
already exists.** Every returned target carries a `runsWhen` string
(e.g. "once, at viewsheet initialization", "on every refresh", "each time
the assembly renders", "on user click", "per row, when the field is
evaluated") computed straight from this same table -- read it instead of
re-deriving it when you're choosing among *existing* targets (e.g. deciding
whether to edit `Chart1`'s existing main script or add a new `onClick`).

## Undo steps are shared with the user

Each `edit`, `set_format`, or binding/table write is exactly one undo checkpoint — matching one
Composer action, even when a single call touches many assemblies (`align`, `distribute`, a table
pivot via `move_table_field`). The stack itself is the **same one the user's Composer session is
using**, so `undo`/`redo` step through their edits as readily as yours. Prefer making a correct
edit over undoing a wrong one, and never call `undo` speculatively.

## A clean return is not a correct result

`edit`, `set_format` and the binding tools report that a change was **accepted**, not that it looks
right. Call `get_viewsheet_image` after a visual change, before telling the user it worked.
`run_script_live`'s and `execute_script`'s return values are the same way — they show whether the
script raised an error, not what rendered.

For a table or crosstab, `get_viewsheet_image` falls back to rendering the **whole viewsheet**
instead of just that assembly — a per-assembly cropped export was tried and dropped because the
crop coordinates weren't reliably consistent with the exporter's own canvas geometry. The response
carries a `note` field when this fallback happened; read it before assuming the image shows only
the assembly you asked for. A render can also 503 if a chart's graph hasn't finished computing yet
— retry once after a beat before treating it as a real failure.

## Domain caveats

### Script

- **`execute_script` is not an isolated dry run.** It runs against the same live runtime as
  `run_script_live` — there is no sandboxed clone. Only the 7 named destructive globals
  (`runQuery`, `setCellValue`, `refreshData`, `saveWorksheet`, and the others gated in
  `run_script_live`) are blocked pending confirmation; any other mutating statement, including a
  direct assembly-property write, executes for real during "dry run" too.
- **`bindingInfo.xFields`/`yFields` need tuples, not plain strings.** `[["State", "string"]]` for
  a dimension, `[["Land Area", "number", "sum"]]` for an aggregate. A plain string array
  (`["State"]`) is silently accepted and produces an empty binding with no error — verify the
  chart actually renders data after a script-driven binding change, don't trust a clean
  `execute_script`.
- **A script references an assembly by its own bound name, never a generic type alias.** There is
  no `chart`/`table`/etc. global in a viewsheet's script scope — resolve the real name (e.g.
  `Chart1`) via `read_viewsheet_model` or `get_script_context` first. The examples below use
  `Chart1` as that resolved name.
- **Script can't create a chart's source-table binding from scratch.** It can only modify fields on a
  chart that already has a source. Check `Chart1.query` first — if it's `null`, give the chart a
  source before scripting its binding: `set_chart_shelf` (or `set_chart_source`) does that from the
  binding domain, and no manual drag in the Composer is needed for it any more.
- **Recolouring one category needs a `CategoricalColorFrame` assigned via `colorFrame`, not
  `colors[field] = frame`.** `Chart1.highlighted[name]` is keyed by a pre-existing named highlight
  rule, not by data values — don't use it to recolor a specific bar/point. Instead:
  `Chart1.bindingInfo.setColorField(dim, type)` (adds a color-by-category legend — there is no
  legend-free way to do this via script), then build a `new
  inetsoft.graph.aesthetic.CategoricalColorFrame()`, call `.setColor(value, new
  java.awt.Color(r,g,b))` on it, then assign `Chart1.bindingInfo.colorFrame = frame`. Assigning
  through `colors[measureFieldName] = frame` instead skips a `useGlobal=false` fix-up and the
  auto palette silently overrides the color.
- **`get_function_signature`'s accuracy depends on when its metadata was last regenerated.**
  It reads a generated snapshot (`js-functions.generated.json`) of the scripting API, not the
  live runtime — if that snapshot predates a scripting-engine change, a signature can reflect the
  old API surface. Treat a signature that doesn't match observed behavior as a stale snapshot, not
  a tool bug, and fall back to `execute_script` to confirm the actual call shape.
- For scripting questions, call `search_product_docs` with
  `modules: ["viewsheetscript", "chartAPI", "commonscript"]`; retry unfiltered
  if results are thin. **`dashboardscript` is a real path segment on the public doc site but is not
  a valid `search_product_docs` module** — it was deliberately removed from the tool's known-module
  list (no titles mapping exists for it in the docs-search backend), and passing it gets a 400. Use
  the local `references/dashboardscript/FreehandTable.md` file below instead of trying to search for
  this module.
- **The Chart Script API and common scripting functions have local reference files under
  `references/`, mirroring the official doc site's own module layout** — read the relevant one
  before writing this kind of script; they're local files, cheaper and faster than a
  `search_product_docs` round trip for a fixed, enumerable API surface like these:
  - `references/chartAPI/ObjectHierarchy.md` — how the Chart API's classes relate (`GraphElement`,
    `VisualFrame`, `Scale`, `GraphForm`).
  - `references/chartAPI/BasicChartProperties.md` — `EGraph`, `dataset`/`data`, `AxisSpec`,
    `LegendSpec`, `PlotSpec`, `TextSpec`, `TitleSpec`.
  - `references/chartAPI/ChartElements.md` — the data-representation elements
    (`IntervalElement`, `LineElement`, `PointElement`, `AreaElement`, `SchemaElement`,
    `GraphElement`).
  - `references/chartAPI/ChartCoordinates.md` — coordinate systems and scales (`RectCoord`,
    `PolarCoord`, `FacetCoord`, `LinearScale`, `TimeScale`, etc.).
  - `references/chartAPI/ChartAesthetics.md` — `VisualFrame` subclasses for color/shape/size/
    line/texture/text (`CategoricalColorFrame`, `StaticShapeFrame`, `GradientColorFrame`, etc.).
  - `references/chartAPI/ChartAnnotation.md` — **adding arbitrary text, shapes, or lines onto a
    chart**: `GraphForm` and its subclasses (`LabelForm`, `LineForm`, `RectForm`, `ShapeForm`,
    `TagForm`, `DefaultForm`). Every form is only visible once attached via `graph.addForm(form)`.
  - `references/chartAPI/UtilityObjects.md` — constant holders (`GLine`, `GShape`, `GTexture`,
    line-fit equations) and `Special Chart Functions`.
  - `references/chartAPI/ChartScriptTutorial.md` — worked, task-oriented examples (change
    scaling, rebind data, recolor by value, add decoration) — reach for this when the ask is "how
    do I accomplish X" rather than "what does method Y take."
  - `references/commonscript/UserFunctions.md` — StyleBI-specific scripting globals: `Style
    Constant` enums, `Global Object Functions` (`runQuery`, `formatNumber`, `inGroups`, `toList`,
    etc.), `Date Global Functions` (`dateAdd`, `dateDiff`, `formatDate`, etc.). Plain JS/Java
    built-ins (`String`/`Number`/`Date` instance methods, `Array`, `Math`, `Regex`, LiveConnect
    Java interop) are summarized there in one paragraph rather than vendored — that's standard
    JavaScript/Java behavior a model already knows; only StyleBI-specific deviations are worth a
    dedicated line.
  - `references/commonscript/CalcObjectFunctions.md` — the `CALC` object's Excel-formula-style
    function library (date/time, financial, logical, math, statistical, text), as compact
    signature tables rather than one page per function given its size.
  - `references/dashboardscript/FreehandTable.md` — the calc (freehand) table `layoutInfo` API
    (`setCellBinding`, `setCellName`, `setExpansion`, `setSpan`, row/column grouping and merging) and,
    most importantly, the `$<name>` cross-cell reference syntax and the `data[...]` source-data-query
    syntax used inside a formula cell's script — read this before writing any calc-table cross-cell
    formula (see the Binding section's calc-table paragraph below for the tool-level `name` field this
    syntax depends on).

### Viewsheet

- **Starting a new dashboard from scratch? `edit` with `op: "add"` is how a chart, crosstab,
  table, or freehand table (`calc_table`) gets placed on the canvas** — the same backend call the
  Composer's own drag-and-drop from the Insert toolbox uses, not a separate creation tool. `type`
  takes a friendly string (`chart`, `crosstab`, `table`, `calc_table`, plus ~20 more — gauge,
  selection list, slider, and everything else in the Insert toolbox), case/separator-insensitive,
  with common aliases accepted (`pivot`/`pivot_table` → crosstab, `freehand` → calc_table,
  `dropdown` → combo_box); an unrecognized type is refused client-side with the valid list rather
  than sent to the server. `x`/`y` are required (no auto-placement) and every type has its own
  sane default size. **The new assembly starts unbound** — `read_viewsheet_model` afterward to
  learn the generated name (e.g. `Chart1`, `Table2`), then `set_chart_source`/`set_table_source`
  or a shelf tool's own `table` argument to bind it. Creation and binding are always two calls,
  never one.
- **The viewsheet's own properties are a separate trio**: `list_viewsheet_properties`,
  `get_viewsheet_properties`, `set_viewsheet_properties`. They take **no `assembly`** — the target
  is the sheet, so there is no name to pass. This is where description, display `alias`, `maxRows`,
  `snapGrid`, metadata mode, MV and selection association live. `alias` is writable, exactly as
  `set_worksheet_properties` writes a worksheet's. **The sheet's script is not**: a patch touching
  `vsScriptPane` is refused, because script authoring is its own capability and reaching it through
  a properties patch would route around it — read it here if you want to see it, write it with
  `update_script`. Layouts (`screensPane`) are not exposed either; renaming or rearranging one is
  not a property edit — use the eight layout tools below instead.
- **No property sets the whole dashboard's background** — none of the trio above carries one, and
  `set_format`/`set_assembly_properties` only ever target a real assembly. The Composer's own
  workaround: add a full-canvas `Rectangle`, `set_format` its `backgroundColor`, `set_z_index` it to
  the back, then `set_format` every assembly sitting on top with `backgroundColor: "transparent"` —
  skip that last step and the rectangle stays hidden behind their opaque backgrounds.
- **Layout has its own eight tools**: `list_layouts`, `get_layout`, `set_print_layout`,
  `manage_device_layout`, `edit_layout_objects`, `set_layout_table_options`, `layout_undo`,
  `layout_redo` — print layouts, device layouts, and per-object placement within a layout, always
  distinct from the Master view. `list_layouts` names every layout by type plus the read-only
  device catalogue; `get_layout` reads one layout's objects in **both** coordinate spaces (the
  object's position/size within that layout, and that same assembly's current position/size on the
  Master, read independently) and reports `supportsTableLayout` per object. `set_print_layout`/
  `manage_device_layout` patch layout settings read-merge-write, validated whole before any of it
  is applied; `edit_layout_objects`/`set_layout_table_options` add, remove, move/resize, or
  configure an object within one layout. Two behaviors worth knowing before calling these:
  **`layout_undo`/`layout_redo` cover only edits made since this layout was last opened in this
  session** — switching to a different layout and back resets that history, so an older edit's
  undo lives only under its own layout, not this one. **`manage_device_layout`'s
  `selectedDevices` can only pick from the existing, read-only device catalogue** (`list_layouts`'s
  `devices`) — it cannot create or register a new device size; asking it to is refused, naming the
  org-admin device settings as the right place instead.
- **`list_assembly_properties` before `set_assembly_properties`** — the vocabulary differs by
  assembly type and a guessed name costs a round trip to find out. Short names come from the
  listing; a dotted key is a raw path into the underlying dialog model (`get_assembly_properties`
  with `raw: true` shows it). The patch is validated whole before any of it is applied, so a typo
  in one key does not leave the others written, and an unknown key is refused with the nearest
  match rather than dropped. Values are forgiving where intent is clear (`"true"`, `"100"`, an
  enum token in any case) and refused where it is not (`"yes"` fails rather than being guessed at).
  Covered types (24): gauge, text, image, chart, table, crosstab, calc table, selection list,
  selection tree, selection container, group container, check box, combo box, radio button, slider,
  spinner, text input, range slider, calendar, tab, line, oval, rectangle, submit. Image was
  genuinely uncovered until `PropertyPath` learned to read a bare Immutables accessor; it is
  covered now, so do not refuse an image-property request.
- **A hyperlink hangs off a region, not the assembly as a whole** — a cell, an axis, a title, or
  the empty plot area. Omit the region for the assembly's own link; pass `row`/`col` (plus
  `colName` for a table) for a cell; pass `titleLink: true` for the title. Clearing is its own
  type (`{linkType: "none"}`) rather than an empty value, and a link whose type has no matching
  destination is refused rather than stored inert. Highlights are a separate mechanism with their
  own tools (below) — they are not reachable through `set_hyperlink` or `set_assembly_properties`.
  Use `list_hyperlink_targets` before guessing a `"viewsheet"`-type link's `assetLinkPath` — it
  enumerates the same global/personal scopes `set_hyperlink` itself checks, and returns
  `assetLinkId` for when a name exists in both.
- **Chart axis/legend/title vocabulary is not `set_chart_region_properties`'s vocabulary.** In
  `set_chart_element_visibility`, an axis target is a **column name** and a legend target is an
  **aesthetic field name** (`list_chart_elements` on the chart, or `get_chart_aesthetics`, for
  those — a channel name works too when the chart has only one legend on it); title targets are
  `x`, `x2`, `y`, `y2`, `chart`. In `set_chart_region_properties`, an axis is addressed by its
  **type** — `y`, `y2`, `x`, `x2` — and the column goes in `field`. Passing a column name as
  `target` there used to read back a plausible property list for an axis that doesn't exist and
  fail with a null-pointer 500 on write; it is now refused by name. Showing one of something is
  not possible — the Composer's show operation restores all of that element at once, so
  `visible: true` with a `target` is refused.
- **Pass `assembly` to `list_chart_elements`, and don't assume `y2` exists.** Without an assembly
  it returns the flat vocabulary — what the tool can address at all, the same for every chart.
  Named a chart, it reports that chart's real `axes` and filters the title targets to them.
  This matters because a `y2` the chart does not have was not inert: `ChartArea` builds all four
  axis areas unconditionally, and the axis object at the right of an ordinary chart is a real one
  carrying the grid lines — not an empty placeholder — so the region read described the phantom
  with the full y1 property list, the write stored a value against it, and the read-back confirmed
  the write: yes at every step for an axis that isn't there.
  `list_chart_region_properties` and `set_chart_region_properties` now **refuse** an axis, or an
  axis title, the chart doesn't have, naming the ones it does. A `y2`/`x2` exists only when a
  measure is set to use the secondary axis, or the engine made one (date comparison, percent
  scale) — and on an inverted graph it lands on `x2`, not `y2`. Read `axesMeasured`: `true` means
  the laid-out graph was consulted, `false` means the answer came from the binding alone because
  the chart has no graph yet (unbound, empty, still computing).
- **Naming a legend was the one wrong target that was never a no-op, so read `legends` before
  hiding one.** `list_chart_elements` on a chart reports the legends it actually **renders**, each
  with its `channel`, its `field` and the `index` the region tools address — a different question
  from `get_chart_aesthetics`, which reports what is *bound*, and the two do not always agree on
  the channel: a line chart renders its **shape** aesthetic as a `line` legend. (`shape`, `line`
  and `texture` are one target for hiding, so any of the three resolves it; `color` and `size` are
  their own.) Hiding by a name the chart had no legend for did not do nothing: the underlying
  event reads a hide with no channel as "hide them all", so it hid **every** legend while the
  summary reported hiding the one that was named, and a single-legend chart made that
  indistinguishable from a no-op. Both the field and an unambiguous channel now resolve, and
  anything else is refused with the real legends named. A chart with no laid-out graph is refused
  too rather than falling back to hide-all — hide every legend by omitting `target`, deliberately.
- **Plot sizing is its own thing, and its own verification.** `resize_chart_plot`'s `ratio` scales
  the plot's **minimum** size, not a share of the assembly: above 1 enlarges it and makes the
  assembly scroll, 1 is the default, and a value below the plot's own baseline usually changes
  nothing because it lowers a minimum the plot already exceeds. **Verify with
  `get_chart_plot_size`, not with a render** — `get_viewsheet_image` fits the graph to the
  assembly box, so a plot enlarged past it renders identically to a default one, which is what
  made this tool unverifiable before that read existed. There, `resized` and `percent` are the
  fields that mean something: the layout only re-applies a resize while `percent` is >= 1, so a
  ratio under the baseline reads back accepted and inert. `ratio` is not a round-trip of what you
  wrote (it is recomputed from the last box laid out), and the geometry lags a write —
  `expandedPlot` larger than `plot` proves an enlargement is live, but equal sizes prove only that
  this graph has not been rebuilt with it. Hiding an axis, by contrast, *does* change the plot's
  proportions visibly — check `get_viewsheet_image` for that one.
- **Each condition carries the junction to the NEXT one; the last must not have one.** Every
  `field` must appear in `get_condition`'s `fields` list. Operators accept aliases (`=`, `in`,
  `startsWith`, `!=`); `between` needs exactly two values, `null` takes none. `date_in` needs a
  name from `list_condition_date_ranges` — don't invent one. **Browse before you filter** —
  `browse_condition_values` shows what a column actually holds; a value that doesn't occur
  produces an empty but structurally valid assembly and a clean return, which looks like a working
  chart that happens to have no data.
- **Highlights use exactly the condition vocabulary above** — same junction rule, same operators,
  same field validation. `name` is required and addresses the highlight for update/delete; a name
  already in use is refused (pass `replace: true` to update deliberately). At least one of
  `foreground`/`background` is required — a highlight with neither is stored and renders nothing,
  which looks exactly like a condition that never matched.
- **Date comparison must say where the range ends** — `endDate` or `endToday: true`, never both,
  never neither. StyleBI stores both an end date and an anchor-on-today flag, and when the flag is
  set **the end date is silently discarded** — a comparison on a due date or any forward-looking
  field then ends today instead of where you asked, with nothing reporting it. Naming one or the
  other makes the choice explicit instead of guessed.
- **Do not use viz-chat's `apply_filter`/`apply_highlight` on a Composer-connected sheet.** Both
  are copy-then-apply — they duplicate the target, so a chat session accumulates parallel
  versions. Here that silently produces a second assembly instead of changing the one the user is
  looking at. Use `set_condition`/`set_highlight`, which edit in place.

### Binding

- **Every field reference is `{column, type, aggregate?, dateLevel?, namedGroup?}`.** `type` is
  always `dimension` or `measure` and is never inferred — a field without it is refused by name
  rather than coerced onto the wrong shelf, where it would render plausibly wrong. `aggregate`
  applies to measures; `dateLevel`/`namedGroup` apply to dimensions.
- **Shelves differ by object type**: chart is `x`/`y`/`group` via `set_chart_shelf`; crosstab is
  `rows`/`cols`/`aggregates`; table has only `details` — StyleBI's plain Table has no grouping or
  aggregation of its own, that is Crosstab's job; a calc table has none — its binding lives
  in its cells, and `get_binding` on one refuses and points at `get_calc_layout` rather than
  returning an empty result. Putting a measure on a dimension shelf (or vice versa) is refused
  rather than coerced — a measure dropped into `rows` would become a grouping column and render a
  real-looking table of the wrong shape. `details` holds ungrouped raw columns and never carries an
  `aggregate`. **To pivot, use `move_table_field`, not remove-then-add** — as one call it is one
  undo checkpoint and the user never sees the half-pivoted state.
- **`x`/`y`/`group` are not the whole chart vocabulary.** Ten more shelves hold exactly one field
  each and are written with `set_chart_single_shelf`: `open`/`high`/`low`/`close` (candlestick and
  OHLC), `start`/`end`/`milestone` (Gantt), `source`/`target` (network and flow), and `path` (path
  ordering). They are a separate tool because they hold one field rather than a list — passing a
  list would bind the first and drop the rest. **Set the chart type first**: which family a chart
  reads depends on its type, and binding the wrong one renders an empty chart with no error. If a
  request looks impossible with `x`/`y`/`group` — a Gantt, a candlestick, a flow diagram — it
  probably is not; check here before saying so.
- **Reading a chart or table: `read_viewsheet_model` for assembly names, `list_bindable_fields` for
  what can go on a shelf, `get_binding` for what currently is.** Assembly names are how every tool
  addresses a target and cannot be guessed, so the read comes first — the same rule the worksheet
  section states, and it applies just as much here.
- **`get_binding` reports a chart's single-field shelves only where something is bound.** x, y and
  group are always listed even when empty; open/high/low/close/path/source/target/start/end/
  milestone appear only when they hold a field, so a missing key means nothing is bound there and
  not that the shelf does not exist.
- **`get_chart_type` reads the chart-level type; `get_binding` reads the per-measure ones.** Which
  of the two is authoritative is decided by `multiStyles`, which `get_chart_type` reports alongside
  `separated` and `stackMeasures`: with it on, each measure on x and y carries its own type and the
  chart-level value is only a default. `get_binding` names those types and leaves the key out
  entirely where a field cannot have one, and adds a `runtimeChartType` on a measure whose render
  resolved to something other than what was set — which is the only way to learn what a measure left
  at `auto` actually drew. Whichever level owns the type is the level whose runtime
  type StyleBI keeps: with multi-style **on** the per-measure ones are maintained and
  `get_chart_type` omits its assembly-level value, and with it **off** the reverse. The flag is
  `multiStyles` — not the chart's `separated` setting, which is unrelated despite naming the
  parameter this depends on. `set_chart_type` writes either level — pass `field` for
  one measure, omit it for the whole chart. **Read the state back rather than assuming the write did
  one thing** — retyping redistributes bound fields, and it rewrites all three flags whether or not
  you passed them.
- **Every field on one assembly comes from ONE source table.** `list_bindable_fields` groups columns
  by table, and scoped to an assembly it marks that assembly's table `current: true`. That grouping
  is a constraint, not presentation: the Composer enforces it by *deleting* every bound field the
  newly chosen table does not have, so a cross-table binding is a state the product will not hold.
  A column from any other table is refused rather than bound into nothing.
- **When the user names a column that is not in the assembly's source, say so — don't resolve it
  yourself.** Both repairs are destructive or wrong, and which one they want is not inferable:
  switching the assembly's table discards every field already bound to it, and there is no
  same-named column in the current source to substitute. Tell them which table the assembly uses,
  offer the columns it does have, and offer the repoint *with* what it would cost. Two mistakes to
  avoid specifically: silently taking the same-named column from another table (they mostly all have
  `ORDER_ID`, so this "works" and renders something plausible), and silently repointing.
  A column that exists in **no** table is a different problem — it has to be created in the
  worksheet first, which is worksheet-domain work, not a binding fix.
- **An assembly added in the Composer starts with no data source**, and without one its shelves can
  be filled in and it still renders completely empty, reporting success at every step. Charts and
  tables get one differently:
  - **Chart** — `set_chart_shelf` / `set_chart_single_shelf` / `set_aesthetic_field` establish it
    themselves, from their optional `table` argument or inferred when the columns belong to exactly
    one table. That mirrors the Composer, where dropping a column sets the source as a side effect
    of the drag. `set_chart_source` is for the two cases inference cannot cover: a column name
    several tables share (it refuses rather than guess — passing `table` to the write does the same
    job in one call), and moving an already-bound chart, which needs `force: true` because it
    discards the fields.
  - **Table / crosstab / calc table** — `set_table_source` attaches one, and nothing infers it.
- **Aesthetic tools**: `get_chart_aesthetics` reads, `set_aesthetic_field` binds a field to a
  channel, `set_visual_frame` writes a frame, `clear_aesthetic_field` removes one.
- **Aesthetic field channels and frame channels are not the same set.** Field channels: `color`,
  `shape`, `size`, `text`. Frame channels: `color`, `shape`, `size`, `line`, `texture` — `line` and
  `texture` have frames but no field binding, and `text` is the other way round. Frame shapes:
  `{type:"static", color}`, `{type:"categorical", colors:[...]}`,
  `{type:"gradient", from, to}`, `{type:"palette", palette}`. Palette names come only from
  `list_aesthetic_options` (27 of them) and are not derivable from the colors they contain — don't
  guess one. An unknown frame type fails rather than silently falling back to a default.
- **Calc (freehand) table cells are addressed by `{row, col}`, 0-based — not A1 notation.** A cell
  binding uses `content` (text/column/formula), `grouping` (group/detail/summary), and `expand`
  (none/vertical/horizontal) — deliberately not `type`/`btype`/`role`, which fail and name the key
  meant instead, because `type` already means several unrelated things across this tool surface.
  **`expand` is the hard part** — a group cell with `expand: "vertical"` is what makes a calc table
  generate one row per value; get it wrong and the table renders with a plausible but wrong row
  count, and nothing in the return value says so — check the row count in `get_viewsheet_image`.
  **Any layout change invalidates coordinates** — inserting a row moves everything below it down
  by one, so `modify_calc_layout`/`copy_calc_cells` return the updated layout; use that, not
  coordinates read before the call. Merging a single cell or splitting an unmerged one are refused
  (both are no-ops in the Composer that would otherwise report success).
- A cell's optional script sits on top of this binding, and is a separate
  tool pair. get_calc_cell_script/set_calc_cell_script read/write the
  script a content: "formula" cell evaluates -- see "Choosing a script
  kind" for how this relates to the other 8 script locations. Prefer
  set_calc_cell_script over set_cell_binding whenever only a cell's
  script text is changing and the cell is already content: "formula" --
  it preserves the cell's grouping/expand/mergeCells/rowGroup/
  colGroup automatically. set_cell_binding replaces the cell's ENTIRE
  binding on every call and does not merge with what's already there --
  omitting grouping/expand on an existing cell does not leave them
  alone, it resets them (grouping to an invalid unrecognized value,
  expand to "none", silently disabling that cell's row/column
  generation).
- **A formula cell references another cell's computed value with `$<name>`, never `field[...]` and
  never a bareword.** `field[...]` binds to the calc table's *source-table* columns only (it resolves
  to `null` for anything else, which is why guessing this shape for a cross-cell reference silently
  renders blank instead of erroring); a bareword identifier throws `ReferenceError`. Name the source
  cell first via `set_cell_binding`'s `name` field (preserved across later `set_cell_binding`/
  `set_calc_cell_script` calls on that cell when omitted, so a later edit doesn't silently un-name
  it), then reference it from another cell's script as `$<name>`:
  `set_calc_cell_script(assembly, row, col, script: "$OrderTotal - $ReturnTotal")`. Full API —
  `setExpansion`/`setSpan`/`setRowGroup`/`setColGroup`/`setMergeCells`/`setMergeRowGroup`/
  `setMergeColGroup`, and the `data[...]` source-data-query syntax used inside a formula script — is
  in `references/dashboardscript/FreehandTable.md`.
- **Columns are named in sort/rank/options calls, never indexed** — an index is only stable until
  the shelf is reordered, after which it silently sorts or ranks the wrong column. `value_asc`/
  `value_desc` need `sortByField` or the sort silently falls back to the label. Ranking needs both
  `n` and `measure` — ranking nests **within** the outer dimension when two dimensions are on
  rows (a top-5 on the inner one gives 5 per outer value; that is native behaviour, not a choice
  this tool makes). That `n`+`measure` pairing is `set_field_ranking`'s vocabulary, on the binding
  side — the worksheet's `set_ranking` is a different shape; see the Worksheet section. When the
  same column is bound twice, `set_field_ranking` needs `index` to say which one, and refuses
  rather than picking. **`percentageBy` sets the direction only** — `"col"` or `"row"` — it does
  not turn the values into percentages; that is the aggregate's own percentage formula. On its own
  it renders the same raw numbers and reports success, and it defaults to `col`, so reading it
  back as `col` does not mean anyone chose it. It also needs columns bound, or the crosstab
  renders zeros.
  Booleans are real booleans — `"yes"` is refused, because anything but `"true"` reads as
  **false**, which would silently turn the setting off.

### Worksheet

The worksheet is the data layer feeding a viewsheet's tables and charts — columns, joins, filters,
aggregates, expressions. 73 tools across the categories below; `read_worksheet_model` is the read
tool for all of it.

- **`open_base_worksheet` follows a connected viewsheet down to its data.** It opens that
  viewsheet's own base worksheet in the user's Composer, pairs it, and returns the session — so a
  request like "the chart is wrong, fix the join behind it" needs no second pairing code. Both
  sheets are then connected at once, and every worksheet tool below works on the new session.
  **It takes no asset id, deliberately**: the agent can reach exactly one worksheet, the base of a
  viewsheet a human already paired. That is the whole consent story, so do not look for a
  parameter to name a different sheet — there isn't one, and that is the design.
  Three refusals worth recognising, each naming what to do: a viewsheet whose base is a **logical
  model or data source** has no worksheet to open (a legitimate configuration, not an error); a
  worksheet session **already connected** is refused rather than replaced, naming its runtime —
  `detach_sheet` it first if you want the base instead; and the agent's own **worksheet
  permission** is checked, so pairing a viewsheet does not grant worksheet rights.

- **`create_worksheet` mints a brand-new, blank worksheet from nothing** — no asset, no table,
  reusing an already-connected session's browser socket the same way `create_viewsheet` does in
  the reverse direction. This is the tool for "this viewsheet has no source and I need to build
  one from a join/expression, not attach something that already exists" — `attach_base_worksheet`
  only ever attaches an existing, already-saved asset; it cannot fill this gap on its own.
  **Creating a worksheet does not connect it to anything.** The full chain for giving a connected,
  sourceless viewsheet (`list_bindable_fields` returns empty) a data source is: `create_worksheet`
  → design it with `add_table`/`add_join`/`add_calc_field`/etc. → `save_worksheet(name: ...)` →
  `attach_base_worksheet(path: <the saved path>)` → `save_viewsheet`. Stopping after
  `save_worksheet` leaves the viewsheet exactly as sourceless as before — when a viewsheet
  session is also held, `create_worksheet`'s own returned summary repeats this reminder.

- **Read first, and after every structural change.** Call `read_worksheet_model` before proposing
  or applying any edit. New columns from `add_join`, `add_table`, `add_concatenation`, `add_mirror`,
  `add_rotate`, or `add_unpivot` don't appear until the model is read again — never reference a
  table or column that isn't in the most recent read result.
- **`sources` tells you what an assembly is built from, in order.** Every composite table reports
  it: a concatenation's subtables, a join's two sides (including cross and merge joins, which carry
  no predicates and so leave `joins` empty), and a mirror's single source. **Order is meaningful** —
  a concatenation takes its entire column list from `sources[0]`, and for a MINUS the order decides
  which table is subtracted from which. A concatenation also reports `concatType`, a mirror reports
  `autoUpdate`, and every column reports `visible`.
- **Still not reported**: any assembly's position, and table-level properties (alias, description,
  maxRows, distinct, mode). Those are writable but not readable, so **do not plan on reading one
  back to confirm it** — `set_table_properties`' `maxRows` can only be confirmed by its effect on
  `preview_worksheet_data`, not by reading it.
- **Schema & data discovery**: `list_datasources`, `list_logical_models` (entities plus the
  physical join path for a datasource — check its `joins` array before calling `add_join` between
  two entities from the same model, since logical-model entities don't expose FK columns directly),
  `get_table_details` (column metadata for a physical table — note its own `catalog`/`schema`
  fields come back null, so it cannot supply what `add_table` needs), `search_schema`,
  `preview_worksheet_data` (sample rows, default 50/max 200 — verify results before saving).
- **`search_schema` has two modes and they do not compose.** `query` alone matches **table names
  only** — searching a column name returns an empty result even when that column exists, which
  reads exactly like the data source not having the field. Pass `fields` to match column names
  instead; but `fields` **replaces** `query` rather than narrowing it, so
  `search_schema(query: "order", fields: ["DISCOUNT"])` returns every table with a DISCOUNT column
  and ignores "order" entirely. This is also the only place to get the `schema`/`catalog` values
  `add_table` requires.
- **Tables**: `add_table` — physical table needs `datasource`+`schema` (+`catalog`); logical model
  entity needs `datasource`+`logicalModel`; omitting `datasource` creates an empty embedded table.
  For a METADATA, FILE, Rest, or Rest.XML datasource use `add_table`'s `queryParams` form instead
  — see `references/tabularDatasources.md` for the decision order: self-discover via
  `list_tabular_targets`/`get_tabular_query_contract` before ever asking the user.
  `delete_table`, `rename_table`, `duplicate_assembly`, `set_primary_assembly`,
  `convert_to_embedded` (one-way — cannot convert back). `set_table_properties`/
  `set_worksheet_properties` cover display name/description, and `get_worksheet_properties` reads a
  worksheet's back (name, alias, description, data-source flag — `read_worksheet_model` carries
  none of them); `set_table_properties` also
  carries `maxRows` (0 or -1 = unlimited) and `distinct`; `set_table_mode` switches
  `live`/`default`/`full`/`detail`/`edit` view; `refresh_data` clears the query cache for one table
  or all.
- **Columns**: `add_column`, `remove_column` (fails if the column feeds a join or aggregate — check
  `read_worksheet_model` first), `rename_column` (does not cascade into filters/aggregates/sorts
  that reference the old name — update those separately), `set_column_visibility`,
  `change_column_type`, `set_column_description`, `reorder_columns`. A hidden join-key column on
  the left side of a chained join breaks the *next* join with a "cross join" error — restore
  visibility before adding a 3rd+ table.
- **`add_column` means two different things.** On an embedded table it creates a new blank column;
  on a query-bound table it only puts back a column the underlying query already produces, and
  cannot introduce a new one. Asking a bound table for a name its query doesn't offer used to
  return success and do nothing at all — the call is now verified and fails instead, naming
  `add_expression_column` as what to use for a computed column. `get_table_details` lists what the
  source actually offers. Two distinct failures, so read which one you got: *"applied nothing"*
  means no column exists and the request was wrong; *"gained X rather than the requested Y"* means
  StyleBI de-duplicated the name (as `add_table` does) and **the column does exist** under that
  other name — use it, or `rename_column` it, but do not call `add_column` again.
- **`remove_column` is also refused when an expression column still reads the target** via
  `field['name']`. That removal never failed and never blanked the dependent column — it changed
  what the column *computed*: an arithmetic formula started returning null and a bucketing
  `CASE` fell through to its `ELSE`, so every row reported a real-looking category that was no
  longer true, with the expression text and the model both still reading as healthy. The check
  covers the same table only, and only the `field[...]` accessor — a native-SQL expression naming
  a column directly is not seen, so verify with `preview_worksheet_data` after removing a column a
  `sql: true` expression might use.
- **Filters & conditions**: **setting one on an embedded table is refused** — `EMBEDDED` and
  `EMBEDDED_SNAPSHOT` alike, and the server's message names only the snapshot ("Filtering is not
  supported for snapshot"), so don't read it as a misclassified table; mirror the table and put
  the condition on the mirror. It covers `add_filter`, `edit_condition`, `set_conditions` and
  `set_post_conditions`; **`remove_filter` is not guarded** and does clear a condition an embedded
  table already carries (a `convert_to_embedded` of a filtered bound table leaves one).
  `add_filter`/`remove_filter`/`edit_condition` target one column;
  `set_conditions` replaces the full pre-aggregate (WHERE) tree — alternating `condition`/
  `junction` nodes with a `level` (0 = root); `set_post_conditions` is the same shape but
  post-aggregate (HAVING). Operations: `=`, `!=`, `<`, `>`, `<=`, `>=`, `BETWEEN`, `ONE_OF`,
  `STARTING_WITH`, `CONTAINS`, `LIKE`, `NULL`. Pass `[]` to clear.
- **Aggregate & ranking**: `set_group_aggregate` replaces the group-by + aggregate spec wholesale —
  `groups: [{field, dateLevel?}]`, `aggregates: [{field, formula, alias?}]` (`Sum`, `Count`,
  `Average`, `Max`, `Min`, `Count Distinct`, `Count All`, `First`, `Last`, `Variance`,
  `Standard Deviation`, `Median`, `Mode`, `Product`, `Concat`). **Worksheets DO support crosstab
  mode natively** — this is the Group and Aggregate dialog's own "Switch to Crosstab" toggle, not
  something exclusive to viewsheets/charts. Pass `crosstab: true` to display the result with
  row/column headers instead of a flat grouped table (default `false`); `read_worksheet_model`
  reports the current state back as `aggregates.crosstab`. It requires at least 2 `groups` and at
  least 1 `aggregates` entry — fewer than that is refused with an error naming the actual counts,
  rather than silently applying a non-crosstab result. This is unrelated to `add_rotate`, which
  transposes a plain table's rows/columns and has nothing to do with grouping. Use
  `dateLevel` here for "group by
  month/quarter/year" — it buckets the existing column in place with no extra column; reach for
  `add_date_range_column`/`add_numeric_range_column` only when the bucketed value needs to exist as
  its own separate, independently sortable column. To change an existing range column's grouping
  option or bucket boundaries, use `edit_date_range_column`/`edit_numeric_range_column` — do not
  `remove_column` and re-add, which orphans anything already bound to the old column.
  **`add_numeric_range_column`/`edit_numeric_range_column` support custom bucket labels via
  `labels`** (e.g. "Below standard"/"Meets standard"/"Good" instead of the default
  "<10000"/"10000-50000"/">50000" text) — this is NOT a reason to reach for an expression column;
  the engine has always supported it, the same as the Composer's own Range Column dialog.
  `labels` must have exactly `boundaries.length + 1` entries, ascending: below the first
  boundary, one between each adjacent pair, above the last. A wrong count is refused with the
  expected/actual counts named, not silently truncated or padded.
  `set_ranking` limits to top/bottom N after
  aggregation: `{field, n, operation}` required, plus optional `groupOthers` and **`of`**. **`of`
  is what makes a "top N by <measure>" correct** when `field` is a group column — `field: "CITY",
  of: "CUSTOMER_COUNT"` answers "top 3 cities by customer count". Omit it there and the ranking is
  by the dimension itself: plausible, wrong, and returned as success. Omit `of` only when `field`
  is already the aggregate to rank by. With an outer group column too, ranking nests within it —
  `field: "CITY"` under a STATE grouping gives top 3 cities *per state*. Note this is not
  `set_field_ranking`'s `{n, measure}` shape from the binding side; `set_ranking` forwards its
  `ranking` object without validating unknown keys, so a stray `measure` here is silently ignored.
- **An expression column added to a grouped table never reaches the output.** This is plain GROUP
  BY semantics: an aggregated table only outputs group keys and aggregate-wrapped columns, and a
  fresh expression column is neither. StyleBI accepts the call, the column appears in
  `read_worksheet_model`, and it is simply absent from every result row — no error from the plugin
  or the server. **Add it to a mirror of the grouped table instead**, which carries no such
  restriction. The plugin now refuses this outright, but only for genuinely *new* columns:
  `edit_expression` on a column that already existed before the table was grouped is unguarded and
  can still vanish the same way.
- **Expressions & SQL**: `add_expression_column`/`edit_expression` are JavaScript by default
  (`field['name']`, `?:` conditionals; date subtraction between two date columns auto-rewrites to a
  millisecond difference — divide by 86400000 for days), or native SQL with `sql: true`.
  `add_sql_query`/`edit_sql_query` run freeform SQL against a JDBC datasource — **always alias
  every projected column explicitly**, even unambiguous ones: an unaliased *qualified* column
  inside a derived-table subquery wrapped by an outer query (the standard per-group-ranking
  pattern, e.g. a `ROW_NUMBER() OVER (...)` subquery) hits a confirmed StyleBI engine bug that
  silently drops the column from every result row, with no error — this also affects the
  Composer's own built-in SQL editor, not just this tool. `get_query_plan` shows the SQL that
  would actually run — **for a SQL-bound table only**, so it is not a general "what will this
  table query" tool.
- **Sort**: `set_sort` — one slot per table; a new call replaces the previous, and there's no value
  that clears it. `direction` isn't schema-constrained but `"ASC"`/`"DESC"` (uppercase) are
  confirmed working.
- **Joins**: `add_join` (single-key `leftKey`/`rightKey`, or multi-key `leftKeys`/`rightKeys`
  arrays; `joinType` `INNER`/`LEFT`/`RIGHT`/`FULL`/`CROSS`/`MERGE`), `edit_join`, `remove_join` (by
  the join assembly's own name, not the table pair — removes access to the right side's columns),
  `add_cross_join` (cartesian, no keys), `add_merge_join` (positional row matching, no keys).
  `edit_join` is the more dangerous of the pair — it changes an assembly a viewsheet may already be
  bound to — and discloses on the same terms as `add_join`, including its own text for `CROSS`
  (row count becomes a product) and `MERGE` (matched by position, truncated to the shorter side).
  Passing neither `joinType` nor keys changes nothing and discloses nothing. **Only INNER/LEFT/
  RIGHT/FULL are verified on an edit** — `CROSS` and `MERGE` are disclosed correctly if the server
  applies them, but they have only been confirmed via `add_join`; build those with `add_cross_join`
  / `add_merge_join` rather than editing an existing join into one.
- **A concatenation is shaped entirely by its first source, and pairs columns by POSITION, not by
  name.** `sources[0]` supplies the whole column list; the other sources contribute only their
  values and a numeric-type merge, matched index by index. Two consequences worth holding onto:
  a second source's differing column *name* simply disappears (its values arrive under the first
  source's name — auditing that the columns line up semantically is the caller's job, since the
  server only checks the count), and **editing the first source reshapes the concatenation** while
  editing any other source does not.
- **Editing the columns of a table that feeds a concatenation is refused.** `add_column`,
  `remove_column`, `set_column_visibility`, `add_expression_column`, `add_date_range_column`,
  `add_numeric_range_column`, `reorder_columns`, `insert_column`, `change_column_type` and
  `add_rotate` (only when `headerColumn` is given — it is implemented as a reorder of the *source*)
  all check first and refuse, naming the concatenations involved. (`edit_numeric_range_column` is
  NOT in this list — it only changes bucket boundaries, never the column's name or position, so it
  has no concat-shift hazard and runs unguarded even on a concat-fed table.)
  **Every source is protected, not just the first** — sources pair up by position, so a change
  anywhere shifts later columns onto different source columns and values start arriving under a
  name that no longer describes them. **And it is not only direct sources**: a join, mirror, rotate
  or unpivot re-derives its columns from what it is built on, so a table two hops below a
  concatenation is refused too, with the chain named in the error. **When the column is wanted for
  some other purpose, `add_mirror(source: "<the table>")` and put it on the mirror**: a mirror's own
  column changes do not propagate back to its source, so the concatenation is untouched. When the
  concatenation itself should change, use `add_concat_subtable` / `remove_concat_subtable` rather
  than editing a source in place.
  - Two carve-outs. `rename_column` and `edit_date_range_column` (changing a date range column's
    grouping option renames it — its name encodes the option) are refused **only where they reach
    the first source**, since a non-first source contributes no column names — editing on any other
    source is unaffected. And `remove_column` **is allowed on the first source of a concatenation
    already reported as `columnsDiverge`**, because that is the repair that field asks for.
- **`columnsDiverge` is a backstop, and silence from it proves nothing.** The equal-column rule is
  enforced when a concatenation is built and never re-checked, so `read_worksheet_model` compares
  the sources' column counts and attaches `columnsDiverge` when the assembly advertises more
  columns than its shortest source can fill. **If that field is present, do not bind or reference
  the affected columns.** To repair it, `remove_column` the surplus column from the *first* source
  — that call is deliberately permitted here — or delete and rebuild the concatenation. Adding the
  column to the other sources does not help; the first source shapes the output. It counts columns
  rather than matching them, so **a source whose column count is unchanged but whose column order
  shifted still lines up numerically while feeding the wrong values**, and nothing reports that —
  which is why `reorder_columns` is refused up front rather than caught here.
- **`columnsUnverified` and `warnings` mean "could not check", which is not the same as "sound".**
  A concatenation the check could not evaluate — a source missing from the model, or a StyleBI
  build that does not report `sources` — carries `columnsUnverified` instead of `columnsDiverge`. A
  top-level `warnings` array appears when an assembly reports no `type`, naming which ones: such an
  assembly cannot be recognized as a concatenation, so **every refusal listed above is inactive for
  it** and a corrupting column edit will be accepted and reported as success. The check is per
  assembly rather than per build on purpose — a model that types its ordinary tables and omits it on
  the concatenation is the case that matters, and an all-or-nothing test would miss exactly that. In
  either case verify with `preview_worksheet_data` before `save_worksheet` instead of trusting the
  model.
- **Concatenation, mirror, rotate/unpivot**: `add_concatenation` (UNION/INTERSECT/MINUS — same
  column count and compatible types across sources) plus `add_concat_subtable`/
  `remove_concat_subtable`/`reorder_concat_subtables`. `add_mirror` keeps a synced reference copy
  for applying different filters/aggregates to the same underlying data — and its own column edits
  do **not** propagate back to the source, which is what makes it the safe place to add a column the
  source cannot afford. `set_mirror_auto_update`'s `visible` parameter is the enable flag despite
  its name, not a display toggle; `update_mirror` refreshes manually when auto-update is off.
  **Auto-update can only be turned off on a mirror of an asset in a *different* worksheet** — a
  mirror of a table in this same worksheet always tracks its source, and StyleBI drops the setting
  silently, so the tool reads the flag back and fails rather than reporting a change that did not
  happen. For a copy that will not follow its source, use `convert_to_embedded`.
  **`remove_concat_subtable` deletes the whole
  concatenation** if fewer than 2 tables would remain — it is not only a subtable removal.
  **`reorder_concat_subtables` is not cosmetic either**: the result takes its entire column list
  from whichever source is first, so moving a different table into that position renames every
  column of the output and re-pairs the sources against a different set of names, while the table
  looks exactly as it did before. Read the current order from `sources` and change it only when
  that is the intent.
  `add_rotate` transposes rows and columns, and **whichever column is first in the source's current
  order becomes the new header row** — not necessarily the one you want, since a mirror/group/
  expression chain easily leaves a different column first. Pass `headerColumn` to choose it; that
  reorders the source first, so you do not have to get `reorder_columns` right yourself. Without
  it you get an arbitrary header and a clean return. `add_unpivot`/`edit_unpivot` melt wide data to
  long format via `headerColumns` (how many leading columns stay as row identifiers).
- **Named groups & variables**: `add_named_group`/`edit_named_group` bucket raw values
  (`groupMappings: [{name, values, operation?}]` — `operation` is any condition operator, e.g.
  `STARTING_WITH`, defaulting to equality; `groupOthers` for an "Others" bucket) — either scoped to
  a `datasource`+`sourceTable`+`attribute` path (+ optional `logicalModel`, or `schema`/`catalog`
  for a physical table), matching what a human creates via the Composer's own "Add Grouping"
  dialog ("Only For" + "Attribute"), or standalone by `type` for use in `set_group_aggregate`.
  **There is no mode that attaches to a worksheet table's own column** — the worksheet UI has no
  such option either, so this tool doesn't expose one. `add_variable`/`edit_variable`/
  `rename_variable`/`delete_variable` manage `$(name)`-style parameters referenced in
  conditions/expressions; `set_variable_values` sets runtime values and dependent tables refresh
  automatically.
- **Embedded table data**: `import_csv_table`/`import_excel_table` create a real, editable embedded
  table — not the same thing as a **Snapshot Embedded Table**, which is what the Composer's own
  file-import wizard produces (and what caching a large query result produces); no tool here creates
  one. `read_worksheet_model` reports the two apart: `EMBEDDED` versus `EMBEDDED_SNAPSHOT`.
  **A snapshot's data is read-only** — `edit_cell`/`insert_row`/`delete_row` are refused on it, and
  so is restructuring its data columns (`add_column`/`insert_column`, and `remove_column` on a data
  column). The Composer offers no cell editing on one either, so there is no UI workaround; to make
  the data editable, re-import the source file with `import_csv_table`/`import_excel_table`.
  **Expression columns are the one thing that does work**: they live only in the column selection,
  never in the data, so `add_expression_column` succeeds on a snapshot and `remove_column` will
  drop one. So check `type` before offering to edit data a user imported themselves, and if they
  say "snapshot table" ambiguously, ask.
  `edit_cell`/`insert_row`/`delete_row`/`insert_column` are 0-based and only operate on embedded
  (not query-bound) tables — row 0 is the first *data* row, not the header. `insert_column`'s
  `insert` flag chooses the side: `true` (the default) inserts before `index`, `false` appends
  after it.
- **`import_csv_table` exposes the same settings as the Composer's Import Data File dialog**, and
  the server parses with the same loader the dialog uses: `encoding`, `delimiter` (or
  `delimiterTab`), `detectType`, `firstRowAsHeader`, `removeQuotes`, and `unpivot` with
  `headerCols`. Defaults are a comma-separated file with a header row and types detected, so a
  plain call needs none of them. Reach for them when a file misbehaves rather than concluding the
  import is broken:
  - **a numeric-looking column importing as `null`** → `detectType: false` keeps the whole column
    as text, so `$499.99` survives instead of failing coercion;
  - **mojibake** → `encoding`, and note it only applies to `filePath` (whose bytes are uploaded).
    Inline `csv` was already decoded before it reached the tool, so no encoding can rescue it —
    pass the path, not the text, when the charset is in question;
  - **quotes appearing in values** → `removeQuotes: true` treats surrounding quotes as escaping;
    left false, values keep them, which is the default and matches the dialog;
  - **`col0`, `col1`, … as names** → the file's first line was data, so pass
    `firstRowAsHeader: false` deliberately (or fix the file), and expect generated names;
  - **a crosstab-shaped file** → `unpivot: true` with `headerCols` set to the number of leading
    identifier columns turns it into a tabular table.
- **CSV headers must pass StyleBI's column-name rule**, and `import_csv_table` refuses a file that
  breaks it rather than importing it anyway: letters (a–z, A–Z, CJK and fullwidth — *not* accented
  Latin or kana), digits, space, and `#  %  _  &  -  !  .`. A slash, parentheses or a colon are
  refused, exactly as the Composer's own import dialog refuses them. **Rename the offending column
  rather than substituting a character** — swapping `/` for `-` is how a real import turned
  `YY/MM/DD` and `YY-MM-DD` into one column, and the collision is silent. If the user hits this,
  the fix is to decide what the column should be called, not to find a character that slips past.
- **Layout**: `auto_layout` arranges every assembly automatically — call it once after building a
  worksheet up from an empty or near-empty one (multiple `add_table`/`add_join` calls), right when
  the edits for the current request are done, whether or not the user asked to save. New assemblies
  otherwise land wherever the server drops them, leaving the canvas cluttered for whoever opens it
  next. Skip it for a small, single-table tweak to an already-organized worksheet.
  `set_assembly_position` is for a single targeted nudge instead of a full re-arrange.
- **`save_worksheet` takes a `scope`**: `"global"` (the default) saves to the shared repository
  everyone can see; `"user"` saves to the current user's private folder. Passing neither publishes
  to the shared repository, so say which one you used when reporting a save.
- **Renaming the worksheet asset itself is not supported.** `rename_table`/`rename_column`/
  `rename_variable` rename those objects in place, but there is no equivalent for the worksheet
  asset. Do not call `save_worksheet` with a new `name` to fulfill a "rename the worksheet" request
  — that performs a Save As, creating a brand-new copy in the repository and leaving the original
  untouched. The Composer UI has no rename action for a checked-out worksheet either; explain this
  and offer Save As as a distinct alternative, only doing it if the user explicitly confirms.
- For worksheet-specific questions, call `search_product_docs` with `modules: ["dataworksheet"]`;
  retry unfiltered if results are thin.

### Complex worksheet edits — use the worksheet-editor subagent

For requests involving multiple interdependent **worksheet** edits (e.g. "add a join, group by the
new column, and filter for Q4"), delegate to the `worksheet-editor` agent. It reads first, proposes
an ordered mutation plan, self-reviews for dependency issues, then applies step by step. It only
covers the worksheet domain — reach for the viewsheet/binding/script tools directly for anything
outside that.

Skip the subagent for single-tool requests — just call the tool directly.

## Highlights need a data cell

On a crosstab or table, a highlight attaches to a **data** cell. Cell (0,0) is a header and exposes
no highlightable fields.

**Always pass `row`/`col` pointing at a data cell.** Omitting them does *not* fall forward to the
first data cell, whatever it may look like from the service code: a fall-forward exists but is
gated on a null region, and the controller always constructs one from its nullable request
parameters, normalizing a missing `row`/`col` to `0` — the header. So omitting them lands on
exactly the cell that has no highlightable fields and is then refused. Pick a real data cell from
`get_viewsheet_image` or the model and name it.

## Errors worth recognizing

- **"No connected … sheet"** — nothing is paired for that runtime. Ask for a code; do not retry.
- **Session expired** — the pairing lapsed or the sheet was closed. Ask for a fresh code.
- **403 "Identity mismatch" on `connect_sheet`** — reads like a permissions problem; it isn't. It
  means the agent is logged in as a different user than the one who has the sheet open in the
  browser. The fix is logging in as that same user, not asking for another code or adjusting
  permissions.
- **A pairing code rejected twice in a row** — the obvious read is "code expired," but a repeated
  failure on an ostensibly-fresh code is usually a stale or wrong deployment URL instead (common
  after a rebuild/restart changes the exposed port) — `status`/`login_start` will proceed against
  whatever URL they're given without validating it. See `connect.md`'s error handling for the full
  remediation; don't just ask for a third code.
- **Two sheets connected, `undo` refused** — pass `sheet: "viewsheet"` or `sheet: "worksheet"`.
  It is not guessed because an undo on the wrong sheet is silent and destructive.
- **"Sheet agent pairing is disabled"** — the deployment has agent pairing turned off. This is an
  administrator setting; the user, not this session, has to enable it.
- **"Viewsheet is unsaved" on `save_viewsheet`** — the viewsheet has no name yet. There is no
  save-as here; ask the user to save it once in the Composer, then retry.
- **Unknown op, or a missing field** — the error names the op and the field it needs. Read it and
  correct the call rather than guessing a different op.
