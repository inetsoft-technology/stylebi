---
name: viz-chat
description: Use when the user wants to create, modify, filter, or save a StyleBI visualization (chart) from their data via natural language — covers the full login → datasource → schema → worksheet → viewsheet → modify → save → view workflow and all 31 viz-chat MCP tools.
---

# StyleBI viz-chat

Create and modify StyleBI visualizations by chaining the `viz-chat` MCP tools. This skill documents the tool surface and the order to call things in.

## When to use

Use this when the user asks to build, change, filter, or save a chart/visualization against a StyleBI deployment. Not for general questions unrelated to StyleBI charts.

## Prerequisites

1. **Authenticated.** The `viz-chat` tools require a StyleBI login (OAuth) driven through your MCP client. Handle auth barriers yourself — do not stop and punt to the user.
   - **If the `viz-chat` tools are unavailable**, that means the server is NOT authenticated — it does NOT mean the server is down or misconfigured, so do not give up. Run the `/stylebi-viz-chat:login` flow (it handles the client-specific OAuth steps), then resume the tool call / workflow the user asked for.
   - **If a tool call returns an auth error (401 / session expired)**, the token expired: the client may refresh it silently, so retry once first. If it still fails, run the `/stylebi-viz-chat:login` flow, then retry the same tool call and continue. Never ask for a StyleBI URL — the deployment is fixed by the server configuration.
   - Once the tools are available, you can call `whoami` to confirm the logged-in user.
2. **A thread with a datasource.** Each workspace has one active thread. Call `new_thread` to start one (optionally pass a `datasourceId`). Pick a datasource with `list_datasources` → `set_thread_datasource`. `get_thread_state` shows the current thread + datasource.

## Canonical workflow

Build a chart by walking these steps in order (skip steps already satisfied):

1. **Find the data.** `search_schema` with the entities/fields you extracted from the user's request → ranked column matches + candidate tables. Then `get_table_details` on the chosen table to confirm exact column names and types — pass the candidate's `databasePath` as `datasourceId` and its `table` as `tableId`.
2. **Build + validate a worksheet.** Construct the worksheet model from the schema output and call `validate_worksheet`. It returns `{ ok, wsId, errors? }`. You need `wsId` for the next step; if `ok` is false, fix the model from `errors` and re-validate.
   - **Make it dashboard-filterable (do this proactively, not just when asked).** A dashboard can only offer a filter for a column that's actually present on the chart's own final bound table (see **Materializing a dashboard → Additional filters**). An aggregated chart's final table carries *only* its group-by dimension(s) + aggregate measures — so unless you plan ahead, the dashboard has nothing to filter by beyond what the chart already plots (the common cause of a dashboard with too few filters). When the chart is aggregated, **retain the high-signal business dimensions a user would want to slice by** (status, customer, region, segment, product attributes, …) so they survive onto the final table:
     - a dimension **functionally dependent on the group-by key** (one value per group — e.g. `product_category` for a chart grouped by `product_name`) → carry it as a **pass-through** aggregate (`First`/`Last`), *never* by adding it to `groupBy` (that fragments the aggregate and silently changes every measure's value — see **worksheet-construction.md**). This is the safe, lossless case — do it by default for such dimensions.
     - an **orthogonal** dimension (many values per group — e.g. `state` for a chart grouped by `quarter`) → it can't survive onto the *final* table (a pass-through would be lossy, and `groupBy` changes the grain). Instead **select it into the raw source table's column list** (just add it to that physical table's `columns` — do NOT add it to `groupBy` or aggregate it; StyleBI retains a base column that's only used for filtering), then filter by it **pre-aggregation** at board time: `curate_board` `additionalFilters` with `preAggregation: true`. That binds the control to the raw source (a WHERE before the group-by), so the chart's own aggregate re-computes over the filtered rows — the chart looks unchanged but is now sliceable by that dimension. **Only safe for a structurally simple per-group-aggregate chart** — a chart with a global aggregate, a normalization/ratio expression, a window function, or stacked aggregates would have its cross-row math collapsed by a subset WHERE, so leave `preAggregation` off (or omit the filter) for those and flag it.
     - then at board time surface each retained column via `curate_board`'s `additionalFilters`/`additionalPerChartFilters` (step below) — a retained-but-unbound column isn't auto-proposed, since `deriveFilters` only ranks *bound* dimensions.
3. **Create the chart.** `create_viewsheet` with the `wsId`, an `intentCategory`, and `fieldConfigs` (the fields to bind — see `instructions/viewsheet-binding.md`). StyleBI's recommender assigns slots and renders; returns a `runtimeId` + sampled rows, persisted locally as the active chart. The result also includes `candidates` (the feasibility-filtered chart-type menu the recommender produced, highest fit first) — use it to choose or offer chart types. **If the result has a `selectionNote`, read `selectionNoteKind` to find out which of its two OPPOSITE meanings applies — never judge from the prose, which reads like an apology either way:**
   - **`selectionNoteKind: "substitution"`** — your requested `visualizationType` was **not** feasible and a **different** type was rendered. Tell the user what was used and why, and offer a feasible alternative from `candidates` instead of silently presenting the substitute.
   - **`selectionNoteKind: "delivery"`** — the type you asked for **was** rendered; the note only explains that StyleBI's recommender doesn't offer that shape, so the plugin built the binding explicitly (treemap, sunburst, icicle, heatmap, box plot, radar, funnel, pareto, Gantt, scatter, binned histogram, and the pie/donut slice-label rebind all take this path). **This is a success — do not report it as a refusal and do not offer replacements for a chart that is already what was asked for.**

   **Every chart-shaped result includes `binding.slots` — the RESOLVED placement (which slot each field actually landed in) — and `binding.notes` (warnings, e.g. an over-cap color binding you pinned). Before replying, check `binding.slots` against the user's intent: if a field landed in the wrong slot, fix the request (re-pin, roll up a high-cardinality dimension, or change type) instead of presenting the chart. Relay any `binding.notes` to the user.**
4. **Adjust the chart (optional).** Prefer modifying in place over recreating — see "Refine vs recreate" below.
   - Change type: `change_chart_type` with the target type (it reuses the active chart's stored ids and re-renders — see `instructions/viewsheet-binding.md` for valid chart types).
   - Format: `set_chart_format` to set axis titles, y-axis scale (min/max/increment/log), and legend placement in place (only the props you pass change). Per-field labels/number formats are set at create via `fieldConfigs` (title/format).
   - Colors: `set_chart_colors` to set a static color, a named/custom palette, per-category colors, or a named gradient in place. The mode follows the chart's color binding; if the result has a `note`, relay it (it explains recreating with a field on the color aesthetic).
   - Filter: pick real values with `lookup_column_values`, build a `conditionModel`, then `apply_filter` (see `instructions/condition-model.md`). **`apply_filter` REPLACES the whole filter** — to *add/remove* a single condition, first call `get_current_chart_state` to read the existing `conditionModel`, merge your change into it, then `apply_filter` with the merged model.
   - Highlight: to color the marks/labels (chart) or cells/rows (table/crosstab) whose values meet a condition, build a `highlightModel` and call `apply_highlight`. Highlight is styling only — it does NOT drop rows (use `apply_filter` for that). **`apply_highlight` REPLACES the whole highlight**, like `apply_filter`.
5. **Save + show.** `save_viewsheet` (named, optional folder) to persist; keep the returned saved id for step 6. Only then `open_companion_viewer` — it returns a URL; print it verbatim so the user can see the chart in their browser. **Do not call `open_companion_viewer` before the chart has been created and recorded with its `vizContext` (step 6)** — the viewer renders whatever step 6 records, so without it the viewer is empty or shows a stale chart from an earlier turn.
6. **Record the turn (with the chart's `vizContext`) + offer next steps.** Call `record_assistant_message` once per user-visible reply (not after every tool call) so it appears in the user's history — do this at the end of *any* reply that changed something, including filter or chart-type edits that don't reach step 5.

   **When the reply created, changed, or saved a chart you MUST pass `vizContext`, and after a `save_viewsheet` it MUST include the saved id as `savedId` — e.g. `vizContext: { savedId: "<id from save_viewsheet>", visualizationType: "<type>" }`.** The companion viewer does NOT read local state: `get_current_chart` resolves the "current chart" by scanning the thread's history **backwards for the most recent message that has a `visualizationContent`**, preferring its `savedId` (which it reopens into a fresh live runtime). **Omit `vizContext` and the message carries no chart context — the viewer skips it and renders a stale chart from an earlier turn or session** (the failure mode is the viewer showing a completely unrelated old chart). `record_assistant_message` auto-fills `runtimeId`/`assemblyName`/`visualizationType` from local state, but `savedId` must come from your call.

   **Also pass `vizContext.insightsMarkdown`** — a **markdown** writeup of the chart's *analytical* insights, saved with the visualization (persisted by `savedId`, survives reopening) and shown in the viewer's insights side pane.

### Insights = interpretation; the Key-figures block carries the numbers

create_viewsheet / apply_binding / update_binding now return a deterministic `factsPack`
(keyFigures + caveats + provenance) that the viewer renders beneath your prose. Therefore:

- Author `insightsMarkdown` as INTERPRETATION: lead with the so-what / the surprising or inverted read.
- Reference figures by MEANING ("a heavy upper tail", "essentially uncorrelated") — do NOT re-list the
  raw numbers the block already shows.
- Translate each `warn` caveat into a plain-language implication.
- NEVER cite a number that is not in the factsPack. If you state a figure, it must match a keyFigure.
  `factsPack.table` now also carries up to 25×8 captured cells, but that does **not** widen this rule:
  the captured table exists to back `checks` and the viewer's Data tab, and any figure read or derived
  from it (a difference, a ratio, a single cell) belongs in `contrast` or a check's `note` — not
  scattered through `insightsMarkdown`, which stays interpretation against the key figures.
- `record_assistant_message` carries prose + suggestedActions only — never numbers/facts; the backend
  already attached the factsPack to the chart.

   Before writing, actually read the numbers in the result rows, then cover:
   - **Headline = the "so what"**, as the bold first line — the finding, not the chart's subject. (`**Late delivery hurts ratings.**`, not `**Ratings by delivery time.**`)
   - **Shape of the distribution** — concentration vs flat; what share the top holds; mean/median/range where it matters; outliers.
   - **The non-obvious read** — a surprising rank, an inversion from what you'd expect, a break in a trend, a correlation. If the data shape reveals something about the *data itself* (e.g. uniform/random values, dates all on one day, an inverted geography), say so — that is often the most useful insight.
   - **So-what / caveat** — what decision this does or does **not** support.
   - **Better cuts** as 1–2 specific follow-ups (e.g. "weight by revenue not count", "roll up to region"), not generic ("break it down further").

   Do **NOT** put tooling/fix/rendering notes (chart type chosen, a backend bug, "the map rendered") in `insightsMarkdown` — those belong in the chat reply, not the saved insights. Keep the chat `text` short; put the depth here. Example: `vizContext: { savedId, visualizationType, insightsMarkdown: "**Late delivery hurts ratings.**\n\nRatings fall steadily across the delivery-time buckets — on-time leads, late deliveries collapse. Nearly all 1-star reviews are late orders.\n\n**So what:** delivery speed, not product, drives satisfaction here.", suggestedActions: [{id:"by-seller-state",label:"Break down by seller state",prompt:"Re-bind the active chart to show late-delivery rate by seller state, then save and open the viewer."},{id:"filter-late",label:"Filter to late deliveries only",prompt:"Apply a filter to the active chart showing only orders delivered 4+ days late, then save and open the viewer."}] }`.

   **You MUST also pass `vizContext.suggestedActions`** — 1–3 structured next-step buttons persisted with the visualization and shown in the viewer's insights pane. This is required on every chart turn: omit it and the insights pane shows no next-step buttons (the failure mode the product is built to avoid). Each entry is `{ id, label, prompt }`: `id` is a short slug (e.g. `"filter-region"`), `label` is the button caption (≤~40 chars, e.g. `"Break down by region"`), and `prompt` is a complete, self-contained instruction Claude can execute later without any extra context (e.g. `"Filter the active chart to show only North America, then save and open the viewer."`). Always author at least one genuinely useful next step — a different analysis angle, a filter, a drill-down, a comparison. **Because next steps are structured buttons, do NOT append a "Next:" follow-up line to `insightsMarkdown`** — keep `insightsMarkdown` for the analytical writeup only. **Ground these in the data:** the chart-building tool results include `factsPack.candidateActions` — a deterministic list of `{id,label,prompt}` next steps derived by the backend from the chart's own facts/caveats (e.g. a top-N roll-up when a dimension is high-cardinality, a fix for an incomplete date comparison, excluding nulls, drilling into a dominant category). **Prefer these as your starting set** — use them as-is or reword their `label`/`prompt` — and only invent a next step from scratch when they don't cover a genuinely better angle. This keeps the buttons consistent and tied to what the data actually shows.

   In your reply to the user, add 1–2 short **insights** about what the chart shows (e.g. the top value, an outlier, a trend). These are your reasoning, not a tool.

**Also pass `vizContext.contrast`** when the finding is comparative — one or two sentences naming the comparison that carries the finding, rendered as a highlighted callout above the checked figures. This is the ONE insights field where you may state figures derived by arithmetic from the captured table or key figures (a difference, a ratio): e.g. "Packaging carries **5 more products** and **8 more orders** than Maintenance & Repair, and earns **$1.84M less**." Everywhere else the rule stands — never cite a number that is not in the facts pack.

**Pass `vizContext.checks`** (0-4 entries; singular `check` also accepted) to show "what was checked" as scannable cards: `[{ label, claim: { kind: "sum" | "count" | "distinct", column, expected? }, note? }]`. The backend **recomputes** each claim over the rows captured in `factsPack.table` and only then marks it verified, so the tick means the figure was recomputed — not that you asserted it. `column` must name a column of that captured table, and an unknown column or kind **fails loud** rather than being dropped. Add `expected` when you want the backend to confirm a specific figure; a mismatch renders both values. Use `note` for the meaning ("Matches the dataset net-revenue baseline") — that part is your claim, not something the backend can verify. The captured table is capped at 25 rows × 8 columns, and every claim is recomputed over **those rows only** — so on a larger chart a `sum` is a partial total, not a grand total. The resolved check is marked partial and the viewer captions it "over the N captured rows"; reserve `checks` for figures that genuinely fit within the captured table, or pair one with a `note` that names the caveat. `kind` is case- and whitespace-normalized, so "SUM" works. Requires a chart whose facts pack has a table; recording checks without one is an error.

**Session recap (cross-chart).** When a tool result's `factsPack.candidateActions` contains an action with `id: "summarize-session"` (the backend flags this once the thread has several charts), when `save_board`'s result reports no session recap yet (see **Saving a board** below — this applies on every save, not just when asked), or when the user asks for a session summary: call `get_conversation_messages` for the active thread, read each prior chart message's `visualizationContent.insightsMarkdown`, and synthesize a short **"What this session shows"** markdown list of the *distinct* findings — dedupe near-identical ones, keep it to a handful of bullets. Record it via `record_assistant_message` with `vizContext.sessionRecap` set to that markdown. It renders in the viewer's **Session** tab. **Findings only — do NOT assert cross-chart numeric comparisons** (different charts usually have different filters/grains, so "17M here vs 24M there" is misleading); describe what each chart showed, not deltas between them.

### Time comparisons: apply them, don't compute them in prose

When the user asks for a period-over-period comparison — "vs last year", "YTD vs prior", "month over month", year-over-year — call **`apply_date_comparison`** on the active chart with a structured config built from their words (e.g. `{ date_range_type: "standard", date_range_unit: "year", date_range_count: 2, granularity: "month", comparison_metrics: "%change&value" }`, or a `custom` config with explicit `date_ranges`). Do **NOT** hand-roll the period math (Δ, %change) in `insightsMarkdown` — the backend validates the config (and returns a field-named error if it's malformed), applies StyleBI's native comparison, and the period-over-period figures (`Latest …`, `Δ vs …`, `%change`, biggest mover) come back in the facts block. Interpret those figures by meaning as usual; if the block carries a `comparison_incomplete` caveat (a period lacks overlapping points), translate it into a plain-language limitation rather than glossing over it.

### Consuming a pending suggested action

When the user clicks a suggested-action button in the viewer, it queues a pending action on the thread. The click happens **in the browser**, so it leaves no trace in the chat transcript — the queue is invisible to you unless you check it.

**"run queued" is the canonical phrase** the viewer tells the user to say after clicking a suggestion button (`Queued "…" — switch to Claude and say "run queued"`). On "run queued" — or any close variant ("run the queued one", "run the queued action") — call `consume_pending_action` and execute the returned action; do NOT interpret it as anything else (a build, a PR merge, the previous task).

**"save to board" is queued the same way, from the portal's own "Save to board" control** (not a viewer suggestion button) — the portal doesn't save anything itself, it just queues a `{ id: "save-board", label: "Save to board", prompt }` action and tells the user to switch to Claude Code and say "save to board". Treat that phrase exactly like "run queued": call `consume_pending_action` and execute the returned `action.prompt` — it asks you to save the thread as a board, so follow **Saving a board** below (including the session-recap requirement) rather than treating it as a fresh, unscoped request. This is very often called from a **brand-new Claude Code session that has no active thread at all** (the user queued it from the portal, then switched over) — that's fine, don't ask for a thread id first: just call `consume_pending_action` with no arguments. See the `resumedThreadId` handling below.

**Also check the queue before interpreting any OTHER vague "run" request.** When a chart/viewer is active and the user gives a short imperative whose object is unstated — e.g. "run it", "go", "do it", "next", "yes", "that one", "the suggestion" — do **not** assume "it" refers to whatever you were last doing. First call `consume_pending_action`. If it returns an action, that is almost certainly what was meant — execute it. If it returns `null`, fall back to chat context. (A button click is invisible to you otherwise.) Note: a bare "run it" is genuinely ambiguous — prefer the distinct "run queued" for the breadcrumb case; if a recent turn was clearly a build/PR/test action and the queue is empty, "run it" likely means that instead.

To run the pending/suggested action (whether asked explicitly or resolved from a vague request as above):

1. Call `consume_pending_action` (pass `{ threadId }` only if you already know which thread; otherwise call it bare — it fetches **and clears** the queued action atomically, falling back first to this session's active thread, then to whatever thread you most recently queued an action on if the session has none).
2. If it returns `{ action: { id, label, prompt } }` — execute `action.prompt` as a normal chart-build or modification, then `record_assistant_message` with a fresh `vizContext` (including new `suggestedActions` for the next round). If the result also carries `resumedThreadId`, that thread is now the session's active thread (same effect as `resume_thread`) — mention to the user which conversation you resumed before acting, since they may not remember queueing it from that particular thread.
3. If it returns `{ action: null }` — nothing is queued anywhere for you. Tell the user, then call `get_current_chart_state` and offer its `suggestedActions` (if any) as choices.

### Regenerating a board's insights

`consume_pending_action` can also return a **board regeneration** follow-up (queued by
`regenerate_board` / the portal's Regenerate button after refreshing a board's data). Its `prompt`
names the board and lists the `savedId`s that now have fresh data but stale insights. For each one:

1. `reload_saved_visualization(savedId)` to make it the active chart (you don't need to see the old
   version — its data is already current).
2. Write a fresh, substantive `insightsMarkdown` write-up for it, the same way you would for a chart
   you just created.
3. `record_assistant_message` with `vizContext: { savedId, insightsMarkdown }` — this both persists
   the insights and clears the board tile's "insights pending refresh" flag.

Do this for every `savedId` the prompt lists before considering the follow-up complete. There is no
separate "regeneration" tool for this step — it is the exact same insight-authoring process used for
any new chart.

7. **Clean up.** `close_viewsheet` when done with a chart or before starting a fresh one in the same thread.

### Geographic maps

When the user asks for a map or choropleth, follow this sequence after the worksheet + `create_viewsheet` steps:

1. **Build the worksheet.** One row per geographic region. Use `Mode(<category>)` aggregate when the user wants "most-popular `<category>` per region" (e.g. most-rented film category by country). Pass `intentCategory: "geospatial"` and `visualizationType: "map"` to `create_viewsheet`.
   - **`create_viewsheet` will return a `bar`, NOT a map — this is EXPECTED, not a failure.** The recommender only proposes a map once the column is marked *geographic*, which happens in `geo_detect` (step 2). Do **not** stop here or conclude "maps don't work" because the result is a bar and `selectionNote` says "map not feasible" — that just means geo_detect hasn't run yet. Always continue to step 2.
2. **Detect geo coverage.** `geo_detect(column)` — marks the column geographic, auto-detects the map type + layer + feature matching, and converts the chart to a map. Returns `geoType`, `layerName`, `matchedCount`, `unmatched` (values StyleBI could not place), and `candidateFeatures` (the known feature names in that geo layer, closest to each unmatched value). Sub-national layers ship for **US states, Canadian provinces, and Mexican states** (data may be full names or 2-letter codes — both resolve); a country with no shipped sub-national layer (e.g. Brazil) resolves only to the Country layer, so deliver those as a ranked bar instead.
3. **Auto-resolve unmatched values.** For each entry in `unmatched`, pick the best match from `candidateFeatures` — correcting typos, aliases, and territory-name variants. Values that have no plausible match go into `drop`.
4. **Apply.** `geo_apply(column, mappings, drop)` — `mappings` maps each resolvable data value to its correct geo feature name; `drop` lists the genuinely unmappable ones to exclude.
5. **Save + show.** Proceed to `save_viewsheet` → `open_companion_viewer` as normal.
6. **Report to the user.** State how many values were matched automatically, what was mapped (e.g. "Calif. → California"), and what was dropped and why (e.g. "N/A — no geographic meaning").

**Caveat:** a map may begin rendering in StyleBI's native UI before the companion viewer is ready — tell the user this is expected if they see the chart appear in the main app first.

### Refine vs recreate
When the user wants to change an existing chart, pick the cheapest path and tell them what you did:
- **Modify in place** (fast): change chart type → `change_chart_type`; axis titles / y-axis scale / legend placement → `set_chart_format`; colors → `set_chart_colors`; change the filter → `get_current_chart_state` → merge → `apply_filter`.
- **Re-bind in place** (`update_binding`): changing the **aggregate, sort, top/bottom-N ranking, which fields are bound, per-field labels/number-formats, intent, or chart type** — pass only what changes to `update_binding` (patch semantics: omitted props reuse the active chart's current binding). It re-runs the recommender over the **same worksheet** and the **same output viewsheet**, so the saved id is preserved (no new asset). Prefer this over recreating. For a *pure* type switch that must preserve the exact slot placement, `change_chart_type` is still the cheapest. Use `get_current_chart_state` first if you need to see the current binding before patching it.
- **Recreate** (only when the **data layer** changes — new joins, filters baked into the data, or columns the worksheet doesn't expose): rebuild the worksheet (`validate_worksheet` / `create_worksheet_table`) then `create_viewsheet`. A pure binding change does **not** need this — use `update_binding`.
- **Highlight / conditional formatting** (`apply_highlight`): to emphasize the marks/labels (chart) or cells/rows (table/crosstab) whose values meet a condition, without dropping any rows. Build a `highlightModel` (conditions reuse the `apply_filter` condition shape) and call `apply_highlight`; it REPLACES the whole highlight and re-renders in place.
- **Not available**: drill is not supported (no server-side commit). Say so rather than pretending; offer ranking/filtering as an alternative where it fits.

### Filtered worksheet returns no data
When `create_viewsheet` on a filtered table (e.g. a `FILTERED_*` mirror with a SUBQUERY condition) returns `hasData: false` or 0 rows, **do not assume the result is correct without self-checking first**. 0 rows can mean either (a) the filter works correctly and no data meets the condition, or (b) a calculation error (wrong expression, wrong column reference, type mismatch, incorrect join) is silently excluding all rows.

**Self-check before concluding:**
1. Bind the upstream computed table (e.g. `CAT_RATES`) to verify the intermediate values are correct (e.g. check that `return_rate` contains plausible non-zero values per group).
2. Verify the threshold/scalar table (e.g. `THRESHOLD`) produces a sensible value — not NULL, not 0, not ∞.
3. Confirm the filter condition field type matches the subquery value type (e.g. both numeric). A type mismatch (string vs double) can silently exclude all rows.

**If an error is found** — fix the incorrect part (expression, join, condition, or type) within the **same existing worksheet** (pass the existing `worksheetId` to `create_worksheet_table`). Do not create a new worksheet from scratch.

**If no error is found** — the 0-row result is correct: no data meets the condition. Do NOT:
- Create a new worksheet to get non-empty results.
- Regress to an earlier/simpler table in the same worksheet (e.g. raw counts like `total_orders`/`total_returns` when the user asked about a computed metric like `return_rate`).

Instead:
1. **Report directly** — state that no data meets the condition, show the actual values and threshold to explain why.
2. **Supplementary chart (optional)** — if the user still wants a visualization for context, bind to the **upstream computed table** using the **same final metric** the user asked about (e.g. `return_rate`), not a raw intermediate field. This stays within the same existing worksheet; no new worksheet is needed.

### Saving a board

`save_board({ name, threadId? })` saves the active thread and all its recorded charts as a named, reopenable board — distinct from `save_viewsheet` (a single chart). Each call creates a NEW board.

**Every `save_board` call must leave the thread with a session recap.** The board's "Summary of findings" panel is a live read of the thread's `session_recap` — there is no LLM in the backend to synthesize one, so this is on you, every time, not just when the user asks for a session summary. `save_board`'s result tells you whether one is already present; when it isn't, immediately follow the **Session recap (cross-chart)** steps above (`get_conversation_messages` → synthesize the distinct findings → `record_assistant_message` with `vizContext.sessionRecap`) before considering the save done.

### Exporting a board

To export a saved board as a report or slide deck:

1. Call `get_board_export_bundle({ boardId })` (use `list_boards` first if you don't already have the id). It returns `{ name, datasource, charts: [{ savedId, title, chartType, caption, factsPack, image, imageFormat, renderError }], omitted, rendered, degraded }`.
2. Propose keep/drop/reorder to the user — surface `omitted` (charts a prior curation dropped) and any chart with a `renderError` or that came back degraded. When the user confirms a curation, persist it with `curate_board({ boardId, charts: [{ savedId, order, caption?, dropped? }] })` so the next export reuses it instead of asking again.
3. Compose the deliverable as an **Artifact** (HTML report or slide deck). Embed each chart's `image` inline: raw SVG markup when `imageFormat === "svg"`, or an `<img src="data:image/png;base64,…">` when `"png"`. Inline/data-URI only — the Artifact CSP blocks external assets, so nothing may be linked out.
4. Lead each chart with its narrative/caption and the key figures from `factsPack` — the same interpretation-over-restatement rule as `insightsMarkdown` above. A chart with `image: null` is shown facts-only, with a note that the image didn't render (and why, from `renderError`) instead of silently omitting it.

### Materializing a dashboard

To turn a saved board into a live StyleBI dashboard (distinct from the static report export):

- Call `materialize_board_dashboard({ boardId })`. It composes the board's **curated** kept charts (same curation as the export; set it first with `curate_board` if needed) into one StyleBI dashboard and returns `{ dashboardSavedId, skipped, uncontrolledCharts, filtersApplied, filtersSkipped, perChartFiltersApplied, perChartFiltersSkipped, preAggregationFiltersApplied, preAggregationFiltersSkipped, summary }`.
- Re-running updates the same dashboard in place (one dashboard per board). Charts that couldn't be merged (e.g. a deleted saved viz) are listed in `skipped`; surface them to the user.
- The result is a normal saved StyleBI asset — it appears in the Visualizations list and can be opened/embedded/edited in StyleBI. Report/deck export (`get_board_export_bundle`) and dashboard materialize are two independent projections of the same curated board.

**Layout.** Charts pack into a grid via a 2D shelf-skyline algorithm: tiles size by chart type, short tiles can stack vertically beside a tall one, and whichever side of a shared row ends up shorter stretches to match — never a plain single-column stack. This runs at three adaptive tiers (base 2-column, Wide 3-column ≥2020px, Ultrawide 4-column ≥2700px, plus a fixed-tile Mobile stack) baked into the same saved asset; StyleBI's own viewer picks the right tier live from the real screen width when the dashboard is opened — no regeneration needed to adapt to a different screen.

**Shared filter bar and per-chart filters.** A top filter bar is auto-derived from the kept charts' own bound dimensions — shared dimensions (used by ≥2 charts) ranked first, then single-chart ones, capped at 4 (`filtersApplied`/`filtersSkipped`). A chart whose own dimension didn't make the bar gets a dedicated per-chart filter in its own tile instead of going uncontrolled (`perChartFiltersApplied`/`perChartFiltersSkipped`; `uncontrolledCharts` lists any chart with no dimension to promote at all).

**Pre-aggregation filters are now discovered automatically — no manual retention/curation needed for the common case.** On every `materialize_board_dashboard` call, an annotated dimension that never survives to a chart's aggregated final table (e.g. order `state` on a revenue-by-quarter chart) is found on the chart's raw source and added to the shared bar as a `preAggregation: true` filter, provided (a) the annotation is approved with the column marked a dimension, and (b) the chart's own aggregate math is structurally safe to subset (a simple per-group aggregate — see the manual section below for exactly which shapes qualify). A chart whose math forbids a subset WHERE (a global aggregate, a normalization/ratio expression, a window function) is never silently skipped — it's reported in `preAggregationFiltersSkipped` with its actual reason (`global-scalar-join`, `window-function`, `opaque-sql`, `unprovable-condition`, or `no-annotated-candidate` when nothing on its raw source qualifies), so you can tell the user *why* a chart isn't filterable by that column instead of just noticing it isn't. **One structurally-unsafe chart sharing a column name vetoes that field board-wide** (`reason: "unsafe-sharer"`), even for a different, structurally-safe chart that also exposes it: the underlying StyleBI mechanism binds a pre-aggregation filter to *every* root table across the merged dashboard worksheet carrying that column name, not just the chart that offered it, so one unsafe sharer would otherwise silently collapse that chart's own math. The name matching is alias-first (a raw column aliased to `state` is matched as `state`), and it never depends on the sharing column being annotated — the hazard is the name existing on an unsafe chart's raw source, annotated or not. **A chart whose worksheet structure can't be read at all vetoes every discovered field board-wide** (`reason: "unknown-exposure"`), because its exposed raw columns are unknown rather than known-empty, so no field is provably safe to bind. **The shared bar holds at most 4 controls in TOTAL** — auto-derived, curated and discovered filters all spend the same budget, because StyleBI lays the bar out in a single unwrapped 200px-per-control row and anything past the canvas is placed where a user can't reach it. Discovered filters fill only the slots left over, so they never displace a curated one, and a field cut by the budget is reported with `reason: "shared-bar-full"`. Discovery is best-effort and additive only — a lookup/classification failure never blocks dashboard generation, it just reports nothing new. **Two shapes still contribute nothing, by design:** a chart whose raw source is an opaque `sql query table` or carries a window function (the classifier condemns the whole chain, so no column on it is ever offered), and a chart saved before the plugin began recording which worksheet table it bound — if its worksheet holds more than one chart's tables there is no way to tell which pipeline is that chart's, so it is reported `opaque-sql` rather than guessed at. Re-saving such a chart fixes it. Discovered filters never displace an explicitly-curated `additionalFilters`/`additionalPerChartFilters` entry (below) for the same field. Use `additionalFilters` when you want to force a filter for a column that isn't annotated, or that discovery didn't offer for some other reason.

**Additional filters — columns not bound to any chart.** StyleBI can filter on any column present on a chart's own bound worksheet table, not just the columns that chart happens to visualize. If the business question calls for slicing by something no chart currently shows (e.g. filtering a revenue-by-product chart by `product_type`, even though no chart bins by it), set it via `curate_board`'s `additionalFilters` (shared-bar candidates) or `additionalPerChartFilters` (targets one chart by `savedId`, and replaces — not adds to — whatever the automatic per-chart selection picked for that chart, since a tile can only hold one per-chart filter control):
  ```
  curate_board({ boardId, charts: [...], additionalFilters: [{ field, dataType, label, preAggregation? }] })
  ```
  **Filtering an *aggregated* chart by an orthogonal raw column** (e.g. order `state` on a revenue-by-quarter chart) — a column that lives on the chart's raw source but never survives to its final aggregated table — set `preAggregation: true` on the additional filter. It binds the control to the raw source (a WHERE *before* the group-by), so the chart's aggregate re-computes over the filtered rows. Requires the column to be present in the raw source table's column selection (retain it at build time — see **step 2 → Make it dashboard-filterable**), and is **only safe for a structurally simple per-group-aggregate chart**: a chart with a global aggregate, a normalization/ratio expression, a window function, or stacked aggregate mirrors would have its cross-row math collapsed by a subset WHERE — check `get_worksheet_structure` and leave `preAggregation` off for those. Default (omitted/false) = bind post-aggregation to the final table (only reaches columns that survive the aggregation).
  These are always included on top of the auto-derived bar (never competing with its cap-of-4 ranking) and persist across regenerates — set once, `materialize_board_dashboard` keeps applying them. **The column must actually be present on the chart's bound table** — check via `get_worksheet_structure` or `get_table_details` before proposing one. A field that doesn't resolve is silently reported in `filtersSkipped`/`perChartFiltersSkipped`, not an error — so if you add one, verify it lands in `filtersApplied` rather than assuming it worked. If the column you want isn't there because a chart's own aggregation pipeline dropped it, see **worksheet-construction.md**'s note on retaining a column as a pass-through for exactly this case — don't just add it to the worksheet's `groupBy`, which fragments the aggregate and changes the chart's own displayed values. **Best avoided entirely by planning ahead:** when you first build a chart you expect to place on a board, retain its likely filter columns then (canonical workflow **step 2 → Make it dashboard-filterable**) rather than discovering at materialize time that every chart's final table carries only its plotted dimensions.

## Read before you build

- Before constructing a worksheet (the data layer) → call **`get_worksheet_construction_guide`** to get the worksheet-construction knowledge (tables, joins, columns, filters, grouping/aggregation, multi-step `create_worksheet_table`). Call it with **no `topics`** first to get the risk index — a map from a recognizable feature of the data need to the matching topic, flagging the silent-failure traps — then call again with the matching `topics` in one call. It is served live from the backend (single source, always current); do not rely on a bundled copy of this reference.
- Before `create_viewsheet` / `change_chart_type` / `set_chart_format` / `set_chart_colors` (the chart layer) → **`instructions/viewsheet-binding.md`** (chart types, dimension/measure `fieldConfigs`, intent categories, `calculateInfo`, ranking/timeSeries, recommender, `explicitBindings`).
- Before calling `apply_filter` → **`instructions/condition-model.md`** (operations, value types, junctions, nested groups, highlight, worked examples).

## Delegating to subagents

Two bundled subagents handle specialized reasoning in their own context. They are advisory and read-only — they never commit; you act on what they return.

- **`chart-type-selector`** — when the user's intent is open-ended ("show me how sales are doing") or the best chart isn't obvious, hand it the intent and the candidate table/fields (it reasons over `instructions/viewsheet-binding.md`). It returns a `visualizationType` + `fieldConfigs` + `intentCategory` + rationale; you then call `create_viewsheet` with those.
- **`viz-reviewer`** — before committing a non-trivial worksheet, binding, or `conditionModel`, hand it the artifact for a pre-commit check. It returns `{ ok, issues[] }`; fix any issues before you call `create_viewsheet` / `change_chart_type` / `apply_filter`.

Skip them for simple, unambiguous requests — they add a round-trip. Use them when a wrong guess would be costly (a complex binding, an unfamiliar dataset, a multi-condition filter).

## Tool reference (30 tools)

**Auth / session**
- `whoami` — confirm the current login; returns the authenticated user and StyleBI URL. Use to verify the connection before real work.
- `logout` — revoke the current access token; to use `viz-chat` again, re-authenticate by running the `/stylebi-viz-chat:login` flow (see the **Authenticated** prerequisite).

> Login is a browser OAuth flow — start it with `/stylebi-viz-chat:login` (it drives the client-specific OAuth and waits until authenticated). For thread/datasource state use `get_thread_state`.

**Threads / state**
- `new_thread` — start a new thread; optionally pre-select a datasource; persists the threadId locally.
- `get_thread_state` — show thread bookkeeping (threadId, deployment, datasource, current chart runtime). Local read.
- `list_conversations` — list past threads on the deployment (most recent first).
- `get_conversation_messages` — fetch all messages for a thread by id.
- `record_assistant_message` — persist an assistant reply to history (once per user-visible reply, not after every tool call). **When the reply involves a chart, pass `vizContext` (include `savedId` after a `save_viewsheet`, `insightsMarkdown` for the saved-with-the-chart insights pane, and `suggestedActions: [{id,label,prompt}]` — 1–3 next-step buttons persisted with the viz and shown in the viewer; this is REQUIRED on every chart turn) — the companion viewer resolves which chart to show from the latest history message that carries one; omitting it leaves the viewer on a stale chart.**
- `set_thread_datasource` — set the active datasource for the thread (local; backend told on the next chart-creating call).
- `list_datasources` — list datasources for the user (id, name, type, description).

**Schema**
- `search_schema` — natural-language search of the annotated schema; returns ranked field→column matches + candidate tables (each with `databasePath` + `table`).
- `get_table_details` — full schema (columns + annotations) for one table; use to confirm exact names/types. Pass a candidate's `databasePath` as `datasourceId` and its `table` as `tableId`.
- `search_product_docs` — semantic search over the StyleBI product documentation (`corpus: "docs"`, default) or per-property server-property reference (`corpus: "properties"`). Call it before concluding a chart type, binding, or feature is unsupported — e.g. small multiples/trellis is not its own `visualizationType`, it's two dimensions bound to the same x/y axis (see `instructions/viewsheet-binding.md`), and the docs would have surfaced that instead of a wrong "not supported" answer. Needs the deployment's AI assistant server configured (`chat.app.internal.url`/`chat.app.server.url`); returns a 503-derived error otherwise.

**Worksheet / viewsheet**
- `validate_worksheet` — validate a worksheet model; returns `{ ok, wsId, errors? }`. `wsId` feeds `create_viewsheet`.
- `create_viewsheet` — create a chart from `wsId` + `intentCategory` + `fieldConfigs`; StyleBI's recommender binds + renders. Returns `runtimeId` + sampled rows; persists the active chart.
- `apply_binding` — **escape hatch:** render a chart from an EXPLICIT, hand-crafted `config` (`{ data:{source:wsId}, bindingInfo:{ bindingType, …slots } }`), **bypassing the recommender** (no type substitution, slot inference, feasibility/readability gating, or same-field measure dedup). Use only when `create_viewsheet` can't express the binding (e.g. an x/y scatter it renders as color/size, same-field measures, or an "unsatisfiable" pin combo). See `instructions/viewsheet-binding.md` § apply_binding.
- `change_chart_type` — switch the active chart's type and re-render (reuses the active chart's stored ids).
- `set_chart_format` — set axis titles, y-axis scale (min/max/increment/log), and legend placement on the active chart, in place. Only the props you pass change; binding/data untouched.
- `set_chart_colors` — set colors on the active chart in place: static color, named/custom palette, per-category overrides, or named gradient. Mode follows the color binding; unfitting requests come back as a `note`.
- `close_viewsheet` — close a viewsheet by runtimeId (best-effort cleanup); call when done with a chart or before starting a fresh one in the same thread.

**Modify**
- `lookup_column_values` — distinct values of a column on the active chart (≤200, flags truncation). Use before filtering.
- `get_current_chart_state` — local read of the active chart's binding + current filter (runtimeId, visualizationType, intentCategory, fieldConfigs, conditionModel, suggestedActions). Call before `apply_filter` to merge into the existing filter, before recreating to reuse the prior binding, and to retrieve the current chart's `suggestedActions` when nothing is queued.
- `consume_pending_action` — fetch **and clear** a queued action (set when the user clicks a suggested-action button in the viewer, or the portal's "Save to board" control). `threadId` is optional — omit it to use the session's active thread, or (if there is none) whatever thread you most recently queued an action on. Returns `{ action: {id,label,prompt} | null, resumedThreadId? }`. If `action` is non-null, execute `action.prompt`; `resumedThreadId` present means that thread is now attached to the session. If `action` is null, nothing is queued.
- `apply_filter` — commit a `conditionModel` to the active chart; REPLACES any existing filter; returns the refreshed sample.
- `apply_highlight` — commit a `highlightModel` (one or more rules: conditions + foreground/background/font, chart `applyArea`, table `applyRow`) to the active chart; REPLACES any existing highlight; returns the refreshed sample. Styling only — does NOT drop rows.
- `geo_detect` — detect geographic type + match quality for a column on the active map chart; returns `geoType`, `matchedCount`, `unmatched` values, and `candidateFeatures` for resolution. Call after `create_viewsheet` for a map.
- `geo_apply` — apply resolved geographic value mappings (`mappings`: data value → geo feature name) and a `drop` list to the active map chart; returns `status` (complete|partial) and any `stillUnmatched`.

**Save / view**
- `save_viewsheet` — save the active chart as a named visualization (optional displayName, folderPath); returns the saved id.
- `list_saved_visualizations` — list the user's saved visualizations (identifier, name, folder). Use an id with `reload_saved_visualization` or `delete_saved_visualization`.
- `reload_saved_visualization` — re-open a saved visualization by its id into a fresh live runtime and make it the active chart (so you can filter, re-save, or view it without rebuilding). Use the id from `save_viewsheet`.
- `delete_saved_visualization` — delete saved visualization(s) by id. Irreversible.
- `open_companion_viewer` — return the companion-viewer URL for a thread; print it verbatim for the user.

## Error handling

When a tool returns an error, surface the message and any suggested remediation to the user. Do not blind-retry. Triage by kind:
- **Validation / "could not build a chart"** — fix the input from the returned message (correct the worksheet model, fieldConfigs, or conditionModel) and retry once. If it fails the same way, show the error and ask the user rather than looping.
- **Auth (AUTH_REQUIRED / session expired / 401)** — the token expired or is missing: retry once in case the client refreshed it silently; if it still fails, run the `/stylebi-viz-chat:login` flow to re-authenticate (it handles the client-specific OAuth steps), then retry the tool call. Do not ask for a StyleBI URL.
- **Server/timeout (5xx)** — surface it, retry once, then stop and report; don't keep hammering.

Show the user the tool's `summary` + remediation, not the raw JSON payload.