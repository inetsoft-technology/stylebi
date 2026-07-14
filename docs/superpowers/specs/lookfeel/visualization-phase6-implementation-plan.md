# StyleBI Visualization — Phase 6 (Chart Chrome Standardization) — Implementation Plan

## Scope

This plan executes **Phase 6 (Chart Chrome Standardization)** of
[visualization-implementation-roadmap.md](./visualization-implementation-roadmap.md). Phase 6
standardizes the **non-mark** portions of charts — axes, gridlines, legends, titles, plot labels, and
the DOM chrome around a chart — so they frame data cohesively without borrowing shell visual density
or bleeding chart palette colors into structure.

Phase 6 delivers the roadmap's Phase 6 tasks:

1. **Chart chrome surfaces**, routed by render location:
   - **server-side** (descriptors + `CSSConstants` chart classes + `GDefaults`): axis lines, axis
     titles/labels, gridlines, legend border/background/title/content, plot labels
   - **browser CSS** (`--inet-viz-*`): the chart toolbar/menu and the interactive tooltip container
     (interactive only; never exported)
2. **Interaction rule**: chrome stays visually lighter than data marks; embedded chart controls use
   compact shell-derived control language; chart palettes do not bleed into surrounding chrome.

Like Phases 3 and 5, Phase 6 crosses the render-location boundary and is delivered **in parts by
render location**, and it follows the same risk-staging: land the low-risk CSS half first, scope the
export-affecting server half as a gated sub-plan taken after the CSS half validates.

### The Phase 6 / Phase 8 boundary (read this first)

Phase 6 and Phase 8 both touch **in-graph chrome color** (axis lines, gridlines, legend
border/background). The roadmap lists these under Phase 6 Task 1 *and* Phase 8 Task 1. This plan draws
the boundary the same way the initiative drew Phase 3↔Phase 5 (mechanism first, broad color later):

- **Phase 6 owns the *chrome* and the *mechanism*.** It builds the gated server-side chart resolver
  (mirroring `VSDensityDefaults` / `VSTableStructureDefaults`) and sets the **neutral chrome
  defaults** — axis/gridline/legend/title/label colors → the warm-neutral visualization system, so
  chrome recedes behind marks. That resolver **is** the "server-side bridge and server-side
  modern-mode selection mechanism" that Phase 8's dependency note requires be *agreed before graph-color
  work begins*. Completing it here unblocks Phase 8.
- **Phase 8 owns the *data* color.** Categorical series palettes, sequential/diverging ramps,
  threshold/anomaly primitives, and conditional formatting ride the mechanism Phase 6 builds. Series
  swatch/mark color (`ChartPalette` / `CategoricalColorFrame`) is **not** touched in Phase 6.

This split is **Decision D1** below and needs owner sign-off, because it decides whether in-graph
chrome color ships in Phase 6 (as neutral defaults) or waits for Phase 8.

### Parts

- **Part A — Browser-DOM chart-surround chrome (client).** Default to the feature-#74894 CARD tooltip
  under the gate (D6, `AUTO` tri-state). Gate-off inert, zero export impact. **The shippable payload.**
  (Toolbar compaction was attempted and **deferred** — the mini-toolbar's geometry is JS-computed, so a
  CSS-only resize desyncs its positioning; see A1 below.)
- **Part B — Server-side in-graph chrome neutrals + the gated resolver (server bridge).** The hard,
  export-affecting half: a `VSChartChromeDefaults` resolver + neutral chrome defaults, grounded into
  **three seams** — **B1** gridline/facet/legend-border color (clean, one method, ready), **B2** chrome
  text colors (second seam, scoped), **B3** axis line/plot border (plain `Color`, guard-or-defer).
  Recommended as its own follow-up sub-plan after Part A validates (mirrors Phase 5's D1); it is the
  mechanism Phase 8 depends on.

Phase 6 does **not**:

- change any chart **mark / series / palette** color, ramp, or threshold — that is **Phase 8**
- implement `warning`/`anomaly` conditional formatting — server-rendered, export-visible → **Phase 8**
- restyle the **viewsheet object title bar** — verified server-rendered (`titleFormat` `VSFormat`,
  appears in export) and shared across all assemblies, **not** a browser-DOM chart surround as the
  roadmap assumed; see the Grounding correction and Deferred item 1
- adopt viz tokens into EM (`projects/em`) — separate build, admin chrome, never exported (deferred
  since Phase 3)

**Definition of done (Part A):** with the gate **off**, behavior is byte-identical to today (`AUTO`
resolves to the legacy `DEFAULT` tooltip); no export output changes anywhere. With the gate **on**,
un-customized (`AUTO`) charts render the CARD tooltip (D6), while an explicit per-chart `DEFAULT`/`CARD`
choice still wins. The only change is the `AUTO`→tooltip-style resolution in
`AbstractChartInfo.getTooltipStyle()` — it feeds a **DOM-only** tooltip class and touches **no
chart-image render path**, so export output is unchanged. (Toolbar compaction A1 was reverted/deferred.)

## Grounding (verified against current code, 2026-07-10, branch `viz-updates`)

Two-agent survey of both render worlds. Two findings **correct the roadmap's assumptions** and are
called out inline.

### World A — Browser-DOM chart surround (Part A targets)

| Surface | file:line | current styling | note |
|---|---|---|---|
| **Chart toolbar / mini-menu** | `web/.../vsobjects/objects/mini-toolbar/mini-toolbar.component.scss:31-33,37,77` | container bg `--inet-shell-surface-default` (31), border `--inet-default-border-color` (32), radius `--inet-radius-xl` (33), button sizing `--inet-control-height-md` (37), divider `--inet-default-border-color` (77); buttons `transparent !important` (40); focus `--inet-primary-color`/`--inet-focus-ring` (44-45) | **shell tokens** → repoint sizing to `--inet-viz-toolbar-height`, surface/border to viz surround tokens. Shared by all assemblies (not chart-only) — acceptable DOM chrome. Not exported. |
| **Chart tooltip (default)** | `web/.../scss/internal/_directives.scss:204-221` (`.widget__default-tooltip`) | text `--inet-text-color` (211), bg `--inet-dialog-bg-color` (213), shadow `--inet-shadow-low` (215), padding `--inet-space-2/-3` (216), border `--inet-default-border-color` (217); **hardcoded** `font-size:12px` (212), `border-radius:1px` (214), `margin:3px` (218) | applied via `chart-area.component.ts:292`. **Chart-specific.** Not exported. |
| **Chart tooltip (card)** | `_directives.scss:223-269` (`.widget__card-tooltip`) | same shell-token set (229/230/231/233/234); **hardcoded** `border-radius:8px` (232), tier font-sizes 11–20px | applied via `chart-area.component.ts:354-355` (`tooltipStyle === "CARD"`). Not exported. **The modern tooltip (feature #74894) — Phase 6 defaults to it under the gate; see D6.** |
| **Object title bar** | `web/.../vsobjects/objects/title/vs-title.component.html:25,28,32-36,37` | **inline from `titleFormat` server model** (`[style.background]`, `[style.color]`, `[style.height.px]`, `[style.border-*]`); SCSS has **no** color tokens | **CORRECTION — not a DOM surround.** `titleFormat` is a server `VSFormat`; the title bar **appears in export** and is shared across all assemblies. It is server-owned like a table cell, not browser-CSS chrome. **Excluded from Part A** (see Deferred 1). |

**Current `--inet-viz-*` consumers (post Phase 5):** `_themeable.scss:113` (`selected-border`), `:830`
(`hover-bg`), `:1269` (`sorted-color`, gated `.viz-modern` at 1267-1268); `generic-selectable-list.
component.scss:30,33`; `schedule-task-list.component.scss:97,112,194,238`; `vs-table.component.scss:108`.
**None of the chart-surround surfaces consume viz tokens today.**

**Client gate is fully wired** (Phase 2/3): body `.viz-modern` (+ `viz-density-<mode>`) applied at
portal `app.component.ts:260-273`, viewer `viewer-app.component.ts:2782-2792`, composer
`app.component.ts:133-146`, each reading the server-forwarded `modernVisualization` flag. Part A rules
scope cleanly under it.

### World B — Server-rendered in-graph chrome (Part B targets)

**Colors resolve at descriptor CONSTRUCTION**, baked into fields — not re-consulted per render. The
chain is: per-object **USER** value → **CSS tier** (`CSSDictionary` over `format.css`/`defaults.css`,
keyed by `CSSConstants` chart class) → **GDefaults** DEFAULT tier.

| Chrome element | Default source (file:line) | Current default | CSS class |
|---|---|---|---|
| Axis line | `AxisDescriptor.java:51` ← `ChartLineColor.getAxisLineColor(GDefaults.DEFAULT_LINE_COLOR)`; field `lineColor` L893, getter L163 | `0xEEEEEE` (`GDefaults.java:141`) | `ChartAxisLine` (`CSSConstants:74`) |
| Gridlines (x/y) | `PlotDescriptor.java:1895-1898` `CompositeValue<Color>` ← `ChartLineColor.getPlotLineColor(GDefaults.DEFAULT_GRIDLINE_COLOR,"x"/"y")`; getters L138/179 | `0xEEEEEE` (`GDefaults.java:137`) | `ChartPlotLine` (`:78`) |
| Facet / plot border | `PlotDescriptor.java:1911-1912,1943` default `GDefaults.DEFAULT_LINE_COLOR`; getters L446/L1009 | `0xEEEEEE` | `ChartPlotLine` |
| Axis tick/label text | `AxisDescriptor.java:62-67` `initDefaultFormat()` → `DEFAULT_TEXT_COLOR` | `0x4B4B4B` (`GDefaults.java:129`) | `ChartAxisLabels` (`:70`) |
| Axis title text | `TitleDescriptor.java:64-70` → `GDefaults.DEFAULT_TITLE_COLOR` | `0x2B2B2B` (`GDefaults.java:133`) | `ChartAxisTitle` (`:66`) |
| Legend border | `LegendsDescriptor.java:579-580` `CompositeValue<Color>` default `GDefaults.DEFAULT_LINE_COLOR`; getter L114 | `0xEEEEEE` | `ChartLegend` (`:90`) |
| Legend title / content text + bg | `LegendsDescriptor.java:94-101`, `LegendDescriptor.java:68-75` → text `DEFAULT_TEXT_COLOR`, bg `Color.WHITE` alpha 50 | text `0x4B4B4B`, bg white α50 | `ChartLegendTitle` (`:98`) / `ChartLegendContent` (`:94`) |
| Plot label text | `PlotDescriptor.java:70-74` → `DEFAULT_TEXT_COLOR` | `0x4B4B4B` | `ChartPlotLabels` (`:106`) |

**Resolution site:** `ChartLineColor.java:38-48` — `dict.checkPresent(cls)` → `dict.getForeground(new
CSSParameter(cls,…))`, else the passed GDefaults constant. `CSSDictionary.getDictionary()`
(`CSSDictionary.java:115`) keys to `portal/format.css`; `init()` (L394) always parses built-in
`inetsoft/util/css/defaults.css` first (L405-406); org-scoped cache (L150).

**CORRECTION — the shipped stylesheet is `defaults.css`, not `format.css`, and it currently defines
ONLY `ChartPalette[...]` (series) rules** — no `ChartAxisLine`/`ChartPlotLine`/`ChartLegend`/title/
label rules. So **all in-graph chrome color falls straight through to the `GDefaults` constants
today**, which are cool grays (`0xEEEEEE` line/grid, `0x4B4B4B` text, `0x2B2B2B` title). This is the
gap Phase 6 Part B closes with warm-neutral modern defaults.

### The gated resolver pattern to mirror (Part B mechanism)

| Anchor | Location | Note |
|---|---|---|
| Density resolver | `VSDensityDefaults.java:39` | `isModern()` = `SreeEnv.getBooleanProperty("viewsheet.modernVisualization", false, true)` (3rd arg = org-scoped) |
| Table-structure resolver | `VSTableStructureDefaults.java:40-45` | `isModern()` = density gate **AND** `viewsheet.modernTableStructure` not `"false"`; exposes `gridlineColor 0xE8E5DE`, `headerSeparator 0xD9D5CC`, `headerBackground 0xF1EFEA`, `headerForeground 0x6A685F`, `totalBackground 0xE9E4DA` |
| Phase 5 injection point | `DataVSAQuery.java:137` (`applyModernTableStructure`, def L238-256) | overlays modern palette onto a **clone** of the shipped Default Style when gated; user formats wrap above and still win |
| Gate forwarding (client only) | `CoreLifecycleService.java:305-306`, `PortalController.java:115-116`, `LookAndFeelService.java:60/171` | forward the flag to the browser / EM config only |
| **No chart render path reads the gate today** | — | verified; the graph chrome is entirely un-gated. Part B is the first chart render path to read it. |

## Decisions (proposed — resolve with initiative owner before Part B)

### D1 — Phase 6/8 boundary: Phase 6 = chrome neutrals + mechanism; Phase 8 = data color → **RECOMMEND YES**

Phase 6 sets neutral in-graph chrome defaults and builds the gated server resolver; Phase 8 owns
series palettes / ramps / thresholds / conditional formatting and reconciles them with the chrome as
one system. Rationale: matches the initiative's mechanism-first method (Phase 3 built the density
resolver, Phase 5 the table-structure resolver), and Phase 8's dependency note explicitly asks that
the "server-side bridge and modern-mode selection mechanism" be agreed **before** graph-color work —
so building it in Phase 6 is the intended sequencing, not scope creep. **If owner prefers**, all
in-graph chrome color can instead defer to Phase 8 and Phase 6 ships Part A only (see D2).

### D2 — Phase 6 first pass = Part A (CSS, zero export); Part B is a gated follow-up sub-plan → **RECOMMEND YES**

Exactly Phase 5's D1. Part A (toolbar + tooltip repoints) is broad, gate-off byte-identical, no server
change, no export change — shippable now. Part B changes descriptor/graph-engine defaults, is
export-visible, and reflows saved charts on enable, so it carries the Phase 5 Part C risk profile and
should be its own sub-plan taken after Part A validates, with PDF/PNG/SVG/Excel parity checks. Keeps
the high-leverage, low-risk 80% shippable immediately.

### D3 — Object title bar is out of Phase 6 scope → **RESOLVED: DEFERRED (own pass, not Phase 8)**

Grounding shows the title bar is server-rendered from `titleFormat` (`VSFormat`), appears in export,
and is shared across **all** assemblies — it is not a browser-DOM chart surround (roadmap assumption
corrected). Modernizing it is a server-default concern like table structure, applies product-wide not
chart-only, and belongs to a dedicated pass (a `VSFormat` title default resolver). Deferred (item 1).

**Does it belong in Phase 8?** No. Phase 8 is *analytical color systems* — categorical/sequential/
diverging palettes, thresholds, and conditional formatting, i.e. **data-meaning** color. The title bar
is neutral **assembly chrome**, not data color, and it spans every assembly type (table, gauge, text,
image, …), not just charts. Folding it into Phase 8 would mix a product-wide chrome-default concern
into a data-color phase. It is closer in shape to **Phase 5's `VSTableStructureDefaults`** (a gated
server-side `VSFormat`-default resolver) than to anything in Phase 8. **Added to the roadmap as its own
pass — Phase 6A (Assembly Title-Bar / Object-Chrome Defaults)** — a `VSTitleChromeDefaults` resolver
applied product-wide, sequenced independently of both Phase 6 and Phase 8.

### D4 — Part B injection seam → **GROUNDED & RESOLVED (2026-07-13)**

Verified in code. The seam and the mechanics are settled; one part carries an unavoidable
value-equality caveat isolated to two fields.

**Seam: `VGraphPair.fixChartFormat(ChartVSAssemblyInfo)` (`VGraphPair.java:1003`, called from `:279`),
alongside its existing `CSSChartStyles.apply(...)` at `:1339`** — the true chart analog of Phase 5's
`DataVSAQuery.getViewTableLens`. It runs **once per render, per assembly**, and already seeds
default/CSS chrome tiers. For export + composer + report parity, the overlay can instead live inside
`CSSChartStyles.apply` (`CSSChartStyles.java:88`), which every render path funnels through
(`VGraphPair:1339`, `CSSProcessor.java:301`, `VSChartDndService.java:224`). Gate with a
`VSChartChromeDefaults.isModern()` mirroring `VSDensityDefaults` (org-scoped `SreeEnv`).

**Tier mechanics (why this is defaults-only and persistence-safe).** `CompositeValue`
(`inetsoft.uql.CompositeValue`) resolves `USER > CSS > DEFAULT` (`get()` L40-42); setting the **CSS
tier** leaves any USER value winning, and the **CSS tier is never serialized** (`toString` L166-179).
`CSSChartStyles.apply` already calls `resetCompositeValues(Type.CSS)` every render (L103/134/262/301/
373) then rewrites the CSS tier — so writing modern chrome to the **CSS tier** matches the existing
reset-and-reapply discipline and cannot dirty saved XML, even though the object reached is the
persisted descriptor.

**Clean elements (write CSS tier / default-format tier — no value-equality):**
- legend border → `LegendsDescriptor.setBorderColor(c, CompositeValue.Type.CSS)` (`:143`)
- plot x/y gridline → `PlotDescriptor.setXGridColor/setYGridColor(c, Type.CSS)` (`:172`/`:213`)
- facet grid → `PlotDescriptor.setFacetGridColor(c, Type.CSS)` (`:480`)
- axis / legend / plot / title **text** colors → `CompositeTextFormat.getDefaultFormat().setColor(c)`
  (`:229`; precedence `getColor()` L121-124 = USER>CSS>DEFAULT) applied right after each
  `initDefaultFormat()` inside `fixChartFormat` (e.g. L1240/1268/1282/1316/1346), re-seeded per render
  so it never leaks to saved state

**Refinement (grounded 2026-07-13) — these are TWO seams, not one.** `CSSChartStyles.apply` sets only
the `CompositeValue` chrome (gridline/legend border) — it does **not** touch text colors. So the
CompositeValue chrome (B1) and the `CompositeTextFormat` text colors (B2) are injected at different
places (`CSSChartStyles.apply` vs the `initDefaultFormat` seeding). B1's exact injection ordering (CSS-tier
baseline *before* the format.css dictionary block, so customer CSS wins) is grounded under **Part B / B1**
below. Treat this D4 list as the element inventory; Part B B1/B2/B3 is the implementation spec.

**The two exceptions (plain `Color`, serialized — value-equality unavoidable):**
- **`AxisDescriptor.lineColor`** (`:893`, plain `Color`, no user-set flag; UI writes it directly via
  `AxisPropertyDialogModel:257`) — **and, correcting an earlier assumption, `PlotDescriptor.borderColor`
  is ALSO a plain `Color`** (`:1943`, not a `CompositeValue`), serialized (`:1650`), and it feeds the
  auto-created ("fake") axis line color (`GraphGenerator.java:526`).
- These have no tier to hide a default in, so a defaults-only override must (a) value-equality-test
  against the shipped default `ChartLineColor.getAxisLineColor(GDefaults.DEFAULT_LINE_COLOR)` (= the
  fragile pattern Phase 5 C1 abandoned) and (b) apply the modern value to a per-render **clone** /
  `RTAxisDescriptor` since the field is serialized.

**Recommendation for the Part B sub-plan:** lead with **B1** (gridline + facet + legend border via the
CSS-tier baseline in `CSSChartStyles.apply`) — one method, export-consistent, gate-off byte-identical,
no clone; follow with **B2** (text colors at the `initDefaultFormat` seam) once grounded; treat **B3**
(axis line + plot border, plain `Color`) as guard-or-defer since it needs value-equality on a clone. See
**Part B** below for the full spec and the exact B1 ordering.

### D5 — EM Material chrome deferred → **RECOMMEND YES** (unchanged from Phase 3/5)

### D6 — Default to the feature-74894 CARD tooltip under modern viz → **GROUNDED & RESOLVED: server-side `AUTO` tri-state (2026-07-13)**

Feature **#74894** (commit `e5a93b3`, "Add card-style chart tooltip") already shipped a modern
card-style chart tooltip. Phase 6 defaults charts to it when modern viz is on, reusing that work.
Grounding corrected the mechanism and settled the approach.

**Verified mechanism (corrections in bold):**
- `ChartInfo.TooltipStyle { DEFAULT, CARD }` enum (`ChartInfo.java:883`).
- `AbstractChartInfo.tooltipStyle` is a **`DynamicValue2`** (not a plain enum), design-value default
  **`"CARD"`** (`:4071`); runtime `getTooltipStyle()` `:3736`, design `getTooltipStyleValue()` `:3746`;
  `writeAttributes` writes the design value **unconditionally** (`:2555`); `parseAttributes` maps an
  absent/unknown attribute to **`DEFAULT`** (`:2836-2837`, helper `:3775`).
- Flows to client via `VSChartModel.tooltipStyle` → `chart-model.ts:68`;
  `chart-area.component.ts:354-355` renders `.widget__card-tooltip` on `"CARD"`. Card SCSS
  `_directives.scss:223-269`.

**Key finding — `DEFAULT` is NOT distinguishable from unset today.** A pre-74894 legacy chart
(absent attr → `DEFAULT`) and a user who explicitly picked "Default" in the Tip Customize dialog (two
radios only, `tip-customize-dialog.component.html:91-105`; write always explicit via
`ChartPropertyDialogService.java:429`) both hold the identical value `DEFAULT` (confirmed by existing
`ChartInfoTooltipPersistenceTest`). New charts are already `CARD`, so only legacy charts need
defaulting — but they can't be told apart from a deliberate `DEFAULT`. **Honoring an explicit DEFAULT
under modern is therefore impossible without a tri-state**, and a client-only fix is ruled out
(`chart-area` has no modern-viz input, and the client payload carries no explicit-vs-unset bit).

**Resolution — add an `AUTO` tri-state, resolved server-side at the model-build seam.** Client
untouched. Touch points (all `core`):
1. `ChartInfo.java:883` — add `AUTO` to the enum.
2. `AbstractChartInfo.java:4071` — design-value default `"CARD"` → `"AUTO"` (new charts carry AUTO).
3. `AbstractChartInfo.java:2837` — **absent** attribute → `AUTO` (legacy charts become AUTO); **unknown**
   value still → `DEFAULT` (forward-compat).
4. **`AbstractChartInfo.getTooltipStyle()` — resolve `AUTO → VSDensityDefaults.isModern() ? CARD :
   DEFAULT`** (implementation correction, see below). The runtime getter — not `GraphBuilder:148` — is
   the correct seam: `PlotArea` (`:873`, `:1432`, `:1620`, `:2216`, …) **also** reads
   `getTooltipStyle()` to build the server-side tooltip *content/structure*, so resolving only at
   `GraphBuilder:148` would leave the card CSS wrapper (client) wrapping default-structured content
   (server). Resolving inside `getTooltipStyle()` makes every runtime consumer agree; `GraphBuilder:148`
   (`cinfo.getTooltipStyle().name()`) then gets the resolved value with no change of its own.
   `getTooltipStyleValue()` (design) stays raw `AUTO` for persistence.
5. `ChartPropertyDialogService.java:221` — map `AUTO`→its resolved value so the two-radio dialog
   preselects correctly (AUTO stays internal; saving the dialog collapses to an explicit choice — a
   deliberate act).

**Result:** new + legacy (AUTO) charts show CARD when modern is on, DEFAULT when off; any explicit
`DEFAULT`/`CARD` a user saved is a concrete enum value AUTO-resolution never touches, so the deliberate
choice is preserved. Render path is **DOM-only, not exported**, so no Part B export risk — rides
**Part A**.

**Pinning tradeoff (dialog-save collapses AUTO → explicit) — intentional.** With only two radios
(Default/Card) the dialog cannot *display* "currently AUTO" distinctly from "explicitly the resolved
value," so opening the Tip Customize dialog and saving pins the chart to the resolved style (step 5).
This is the **only** way, short of a third "Auto" radio, for a user to make a style survive a later
org gate flip — e.g. keep CARD after modern is disabled. The alternative (preserve AUTO unless the
radio changed) would make pinning the *currently-resolved* value impossible (clicking the already-
selected radio is a no-op), which is worse. The cost is a mild incidental freeze: editing an unrelated
tip setting (custom text, snap) and saving also pins the style — acceptable, since the user is editing
that chart's tip config and the visible tooltip does not change at save time. A future, fully-expressive
option is an explicit "Auto (follow theme)" radio, but that exposes AUTO to users (D6 keeps it internal)
and touches the client model — deferred, out of Part A scope.

**Backward-compat (confirmed):** an old server parsing `tooltipStyle="AUTO"` throws in `valueOf` →
caught → `DEFAULT` (legacy look); and the old writer strips the unknown attribute, so re-saving on a
pre-74894 server permanently degrades AUTO→DEFAULT. Acceptable, worth noting.

## Changes

### Part A — Browser-DOM chart-surround chrome — ✅ IMPLEMENTED (2026-07-13)

Gate-off inert: the repointed token's `:root` default equals the shell token previously used, so
computed styles are byte-identical with the gate off and shift only under `.viz-modern`. No
`vsFormatModel`/`titleFormat` binding is touched, so **export is unchanged**. Verified: `core` compiles
clean; `ChartInfoTooltipPersistenceTest` (9 tests) passes.

**A1 — Chart toolbar / mini-menu — ❌ ATTEMPTED, REVERTED, DEFERRED (2026-07-13).** Two CSS attempts
failed because **the mini-toolbar's geometry is JS-computed, not CSS-driven**, so any CSS-only resize
desyncs it from its own positioning math:
- Attempt 1 (`min-width` → `--inet-viz-toolbar-height`): no visible effect — the button's real size is
  `38×26`, driven by `.btn-sm` padding `4px 8px`; `min-width` is only a floor and never binds.
- Attempt 2 (gate-scoped `:host-context` padding): the button height changed, but the toolbar is
  absolutely positioned above the object at `topY = this.top − GuiTool.MINI_TOOLBAR_HEIGHT − adj` with
  the **fixed constant `MINI_TOOLBAR_HEIGHT = 28`**. A taller button (comfortable) pushed the toolbar
  **into** the object title (overlap); a shorter one (dense) left a **gap**. Width is likewise
  JS-assumed via `miniToolbarService.getActionsWidth()` (drives `alignLeft` / container width), so a
  width change would misalign horizontally.
- **Conclusion:** compacting this toolbar safely requires making the JS geometry gate-aware
  (`MINI_TOOLBAR_HEIGHT` + `getActionsWidth`), i.e. coordinated TS **and** CSS — beyond Part A's
  CSS-only DOM-chrome scope. Both CSS attempts fully reverted (`mini-toolbar.component.scss` back to
  upstream). **Deferred** (Deferred 7). Part A's shippable deliverable is **A2 only**.

**A2 — Chart tooltip: default to the CARD style under modern (D6)** — ✅ done. Reuses feature #74894's
card tooltip. `AUTO` added to `ChartInfo.TooltipStyle` (`ChartInfo.java:883`); field default `"CARD"` →
`"AUTO"` (`AbstractChartInfo.java` field); `parseAttributes` maps **absent** attr → `AUTO`, unknown →
`DEFAULT`; **`getTooltipStyle()` resolves `AUTO → isModern() ? CARD : DEFAULT`** (the runtime seam that
covers both `PlotArea` content and `GraphBuilder` model — see D6 #4 correction); `getTooltipStyleValue()`
stays raw `AUTO` for persistence; `ChartPropertyDialogService:221` preselects the resolved value for the
two-radio dialog. Client untouched: un-customized charts render `.widget__card-tooltip`
(`_directives.scss:223-269`, `chart-area.component.ts:354-355`) under the gate; an explicit
`DEFAULT`/`CARD` is preserved. DOM-only, never exported — no export risk.
- The card tooltip's SCSS keeps its shell-neutral tokens per "inherit shell neutrals first"; the modern
  differentiation is the **card layout**, not a recolor. No new `--inet-viz-tooltip-*` token needed.
- **Test infra fix:** `ChartInfoTooltipPersistenceTest` was committed without `@Tag("core")` (so it
  never ran) and without the `@ExtendWith(SpringExtension)`/`@SreeHome` bootstrap `VSChartInfo`
  construction needs. Added both so the suite actually exercises the tooltip contract; updated the
  legacy-XML assertion to the new AUTO semantics and added a new-chart-default test.
- **Post-review follow-ups (all applied):** (1) `PlotArea` memoizes the resolved style once per render
  (`getResolvedTooltipStyle()`) instead of calling `getTooltipStyle()` — which does a `SreeEnv` read on
  the AUTO branch — per chart element; the `PlotArea` is built fresh per render with a `final`
  `chartInfo`, so the memo is safe (the static `cardPutsMeasureFirst(chartInfo, …)` keeps the direct
  call). (2) `chart-model.ts:68` documents that `tooltipStyle` is server-resolved and never `"AUTO"`.
  (3) added `autoResolvesToCardWhenModernGateOn` (gate-on branch, via scoped `SreeEnv.setProperty`).
  Suite is 10/10 green.

**D-A (Part A = card tooltip; no surround recolor):** the design spec wants chart chrome to *inherit
shell neutrals*, so Part A does **not** recolor the tooltip/toolbar surface away from shell. The one
landed, spec-aligned Part A win is **defaulting to the CARD tooltip under modern** (A2/D6 — the modern
look already exists), which is gate-off inert and export-safe. Toolbar compaction was the intended
second win but is **deferred** (A1) — the mini-toolbar's geometry is JS-computed, so it needs a
coordinated TS+CSS change, not a CSS tweak. No surround color tokens are invented; if a later design
calls for a distinct surround surface, `--inet-viz-tooltip-*` is added to the contract then
(Deferred 4).

### Part B — Server-side in-graph chrome neutrals + gated resolver (grounded 2026-07-13; needs owner go-ahead)

Export-affecting server work. Grounding **refined the scope**: what D4 treated as one seam is actually
**three**, because `CSSChartStyles.apply` covers only the `CompositeValue` chrome (gridlines, legend
border) — **not** chrome text *colors* (separate `CompositeTextFormat` seam) and **not** the plain-`Color`
axis line. Split accordingly, lead with the clean single-seam win.

**Gate (`VSChartChromeDefaults`, `uql/viewsheet/internal/`, mirrors `VSTableStructureDefaults`).**
`isModern()` = `VSDensityDefaults.isModern()` **AND** `SreeEnv.getProperty("viewsheet.modernChartChrome", …, true)`
not `"false"` (org-scoped, defaults ON when modern is on — admin escape hatch, same shape as
`viewsheet.modernTableStructure`). Color accessors: `gridlineColor()`, `legendBorderColor()`,
`axisLabelColor()`/`axisTitleColor()`/`legendTextColor()`/`plotLabelColor()` (B2), `axisLineColor()` (B3).

**Proposed neutral values** (**proposed — no design spec yet**; add a "Chart Chrome Tokens" group to
`visualization-palette-swatches.html` as Phase 5 did for table structure):
- gridline + facet + legend border + axis line → **`#E8E5DE`** (= table `gridlineColor`, unifies all
  hairline chrome; warmer/subtler than today's `#EEEEEE`)
- axis tick/label + legend content text → **`#6A685F`** (= table `headerForeground`; verify legibility)
- axis/legend title text → **`#35342F`** (= shell text-default; quieter than today's `#2B2B2B`)
- legend background → leave `Color.WHITE` α50; no change

#### B1 — gridline + facet + legend-border color (the clean first pass) — ✅ IMPLEMENTED (2026-07-13)

Added `VSChartChromeDefaults` (`uql/viewsheet/internal/`, mirrors `VSTableStructureDefaults`:
`isModern()` = density gate AND `viewsheet.modernChartChrome` ≠ `"false"`; `gridlineColor()` /
`legendBorderColor()` = `#E8E5DE`) and the CSS-tier baseline in `CSSChartStyles.apply` — one gate read,
then `setXGridColor`/`setYGridColor`/`setFacetGridColor` and `setBorderColor` on the CSS tier after each
`resetCompositeValues(CSS)` and before the dictionary block. `core` compiles clean;
`CSSChartStylesModernChromeTest` (3 tests) verifies gate-off = legacy `GDefaults`, gate-on = `#E8E5DE`
gridline/facet/legend-border, and a USER-tier grid color still winning. Grounding below is the spec it
was built from.

**Dialog round-trip — grounded SAFE (no leak).** The chart-properties **Line** pane reads the *resolved*
color (`getXGridColor()`/`getFacetGridColor()`/`getBorderColor()` = `USER > CSS > DEFAULT`), so with the
gate on it may *display* the modern `#E8E5DE`. But its OK path calls the `setXxxColor(color, force=false)`
overloads, which **only write the USER tier when the color differs from the current resolved value**
(`PlotDescriptor.setXGridColor/​setFacetGridColor`, `LegendsDescriptor.setBorderColor`). So leaving the
modern default unchanged and clicking OK **does not** persist it as a user value — gate-off still falls to
`GDefaults`. Only an actual color change is persisted (deliberate). This is the same guard that already
prevents a `format.css` chrome value from being frozen on OK, so B1 introduces no new leak, and the
descriptor-instance identity (whether the dialog reads a CSS-applied descriptor) is moot for correctness.


- **Seam:** `CSSChartStyles.apply(ChartDescriptor, ChartInfo, …)`
  (`core/.../uql/viewsheet/graph/CSSChartStyles.java:88`) — the one method that already sets these
  `CompositeValue` chrome colors, called on **both** the live path (`VGraphPair.java:1339`) **and the
  export/report path (`CSSProcessor.java:301`)**, so a single change is export-consistent. (Also the
  composer-DnD clone path `VSChartDndService.java:224`.)
- **Injection = a CSS-tier baseline, written after each `resetCompositeValues(CSS)` and *before* the
  format.css dictionary block.** Precedence is `USER > CSS > DEFAULT` (`CompositeValue.get()`:41). Order:
  1. `resetCompositeValues(Type.CSS)` (already there — L103/134/262/301/373) clears the CSS tier.
  2. **(new)** if `VSChartChromeDefaults.isModern()`, set modern color on the **CSS tier**:
     `plotDesc.setXGridColor/setYGridColor/setFacetGridColor(c, Type.CSS)` (`PlotDescriptor` :172/213/480),
     `legendsDesc.setBorderColor(c, Type.CSS)` (`LegendsDescriptor` :143).
  3. the existing `if(cssStyle != null)` dictionary block runs — a customer's `format.css` chrome value
     **overwrites** the modern baseline (customer wins).
- **Why this ordering is correct on every axis:**
  - *Customer `format.css` chrome* → written after → wins. ✓
  - *User picker* → USER tier → wins over CSS. ✓
  - *Gate off* → step 2 skipped → after reset, `cssDefined=false` → `get()` returns the DEFAULT tier
    (GDefaults `#EEEEEE`) → **byte-identical to today**. ✓
  - *Persistence* → the **CSS tier is never serialized** (`CompositeValue.toString()`:166-179 writes only
    `userValue`, + `defaultValue` iff `saveDefault`; these fields are `saveDefault=false`), so no saved
    XML changes and **no clone is needed** — unlike Phase 5's table style, which had no tier system.
  - *Inverted graphs* → x/y are swapped by the dictionary code, but modern sets both grid colors to the
    same neutral, so inversion is a no-op.
- Covers the most prominent chrome (gridlines + legend border). This is the recommended first server
  sub-pass: one method, export-consistent, gate-off byte-identical, no clone, no value-equality.

#### B2 — chrome text colors — ✅ IMPLEMENTED (2026-07-13)

Axis labels, axis titles, legend title, legend content. Modern text neutrals are seeded in each chrome
descriptor's `initDefaultFormat(vs)`, gated `vs && VSChartChromeDefaults.isModern()`. `VSChartChromeDefaults`
gained `labelColor()` = `#6A685F` and `titleColor()` = `#35342F`. Changed: `AxisDescriptor` +
`ChartRefImpl` (axis labels) → label; `LegendDescriptor` (legend content) → label; `TitleDescriptor`
(axis titles) + `LegendsDescriptor` (legend title) → title. `core` compiles clean;
`ChartChromeTextColorTest` (3 tests) verifies gate-off = legacy `GDefaults`, gate-on = modern label/title,
and the **report path (`vs=false`) stays legacy**. Grounding and decisions below.

**Fix found in eye-test (2026-07-13) — value (Y) axis was still legacy.** The measure/value axis renders
from the **runtime** descriptor `VSChartInfo.getRTAxisDescriptor()` (`GraphGenerator.getAxisDescriptor0`),
which `createRTAxisDescriptor` clones during VS execution **before** `fixChartFormat` seeds the design
descriptor — so `fixChartFormat` seeding only `chartInfo.getAxisDescriptor()` (design) left the rendered
RT value axis on the legacy color (dimension axes were fine — their per-ref RT is lazily cloned from the
already-seeded design). Fix: `VGraphPair.fixChartFormat` now also seeds
`chartInfo.getRTAxisDescriptor()`/`getRTAxisDescriptor2()`. Robust either way — if the RT is null,
`GraphGenerator` falls back to the seeded design; gate-off seeds `GDefaults` on both. (RT descriptors are
transient/not serialized.) Verified visually; unit tests cover the descriptor seeding, not the render
path, so this RT gap is an eye-test finding.

**Scope correction — `PlotDescriptor` (plot value labels) EXCLUDED.** `PlotDescriptor`'s text format
colors the on-mark **data value labels** ("Show Values" text), which are **data annotations, not chrome**
(the spec's chrome/data split puts data color in Phase 8). Recoloring them muted would hurt data
readability and blur the Phase 6/8 boundary, so B2 leaves them at their legacy `GDefaults` color.

**Decisions (resolved 2026-07-13 with owner):**
- **Palette — APPROVED:** labels `#6A685F` (= table `headerForeground`), titles `#35342F` (= shell
  text-default). (Unifies both chart titles at `#35342F`; note legend title moves from its legacy
  `#4B4B4B` to the stronger `#35342F`, and axis titles from `#2B2B2B` slightly lighter — both land on the
  one modern title neutral.)
- **Report scope — RESOLVED, no `vs=false` needed.** `VsToReportConverter` (VS→PDF with print layout)
  builds the chart via `box.getVGraphPair(...)` — the **viewsheet** render path (`VGraphPair` →
  `initDefaultFormat(true)`), then just wraps the pre-built `VGraph` in a report `ChartElementDef`. So a
  print-layout export renders as `vs=true` and **is covered** by the `vs && isModern()` gate. The
  `vs=false` path (`CSSProcessor` + `initDefaultFormat(false)`) is the standalone report-chart path, which
  is dead — correctly left legacy. (Retroactive B1 note: B1's shared `CSSChartStyles.apply` also reaches
  the dead `CSSProcessor` path; harmless.)
- **Dialog round-trip — to verify post-creation (owner).** The chart text-format editor reads `getColor()`
  (resolved, incl. `deffmt`), so with the gate on it may *display* the modern color; confirm its OK path
  only persists a *changed* value (the B1 `force=false`-style guard) so it doesn't freeze the modern
  default into `userfmt`. Tracked as a follow-up verification.

**How chrome text color resolves (verified).** Each chrome text element carries a `CompositeTextFormat`
with three sub-formats — `deffmt`, `cssfmt`, `userfmt`; `getColor()` = `userfmt ▸ cssfmt ▸ deffmt`
(`CompositeTextFormat.java:121-124`). Per render, `VGraphPair.fixChartFormat` does, for **every** text
element (legend title/content, x/x2/y/y2 axis labels, plot labels, targets, and per-ref aggregate/dim
labels — ~15 sites, L1023-1140):
1. `descriptor.initDefaultFormat(true)` → `deffmt.setColor(GDefaults.DEFAULT_TEXT_COLOR)` (labels,
   `#4B4B4B`) or `DEFAULT_TITLE_COLOR` (titles, `#2B2B2B`);
2. `copyDefaultFormat(deffmt, objFmt)` (`VGraphPair`) → overlays the **chart object's foreground onto
   `deffmt` only if the object's CSS or user format defines one** (`isForegroundValueDefined`).

So the effective text default = object foreground (if the chart's own format sets it) else the GDefaults
neutral, then `cssfmt` (`format.css` text color) and `userfmt` (user text format) override via `getColor()`.

**Seam — seed the modern color inside each descriptor's `initDefaultFormat(vs)`** (one line each),
*before* the object-foreground overlay, gated `vs && VSChartChromeDefaults.isModern()`:
- `AxisDescriptor.initDefaultFormat(vs)` and `ChartRefImpl.initDefaultFormat(vs)` (per-ref axis labels)
  → `labelColor()`
- `LegendDescriptor.initDefaultFormat(vs)` (legend content) → `labelColor()`
- `TitleDescriptor.initDefaultFormat(vs)` (axis titles) → `titleColor()`
- `LegendsDescriptor.initDefaultFormat(vs)` (legend title) → `titleColor()`
- `PlotDescriptor.initDefaultFormat(vs)` (plot value labels) → **excluded** (data, not chrome — see above)

**Why this precedence is correct.** `initDefaultFormat` runs *before* `copyDefaultFormat`, so an explicit
chart-object foreground still overrides the modern default (object ▸ modern ▸ GDefaults), and `format.css`
(`cssfmt`) and a user text format (`userfmt`) still win via `getColor()`. **Gate off → `initDefaultFormat`
seeds exactly the GDefaults colors it does today → byte-identical.**

**Serialization is NOT a blocker (verified, but note the asymmetry with B1).** `CompositeTextFormat`
serializes **all three** sub-formats including `deffmt` (`writeContents` L296-311) — there is **no
non-serialized tier** like B1's CompositeValue CSS tier. But `deffmt` is **re-seeded every render** by
`initDefaultFormat`, so: gate-off always seeds GDefaults (identical to today, no dirty), and a gate-on
save persisting a modern `deffmt` is harmless because the next render re-seeds it per the gate. This is
the *same* render-time `deffmt` mutation `copyDefaultFormat` already performs, so it introduces no new
dirty-state behavior (`ChartDescriptor.equalsContent` compares the descriptors, but the render rewrites
`deffmt` every pass regardless of gate).

Remaining follow-up: the **dialog round-trip** verification above (post-creation, owner). Everything else
(palette, report scope, `PlotDescriptor` exclusion) is resolved and built.

#### B3 — axis line color — ✅ IMPLEMENTED (2026-07-14)

Closes the residual mismatch: after B1, gridlines are `#E8E5DE` but the axis line stayed legacy
`#EEEEEE`. B3 unifies the axis line to `#E8E5DE`. Added `VSChartChromeDefaults.resolveAxisLineColor(Color)`
(gate + value-equality vs `GDefaults.DEFAULT_LINE_COLOR` → `gridlineColor()`, else unchanged) and call it
at `GraphGenerator.setupAxisSpec` (`axis.setLineColor(...)`). No descriptor mutation, no clone. `core`
compiles clean; `CSSChartStylesModernChromeTest` gained 2 resolver tests (gate-on default→modern,
custom/null preserved; gate-off unchanged). Grounding (with B2's pipeline knowledge) **shrank the scope
and changed the mechanism** — details below.

**Scope reduced to the axis line only — plot border drops out.** `PlotDescriptor.borderColor` (`:1943`,
plain `Color`, default `GDefaults.DEFAULT_LINE_COLOR` `#EEEEEE`) has only two consumers, neither needing
action: (a) `GraphGenerator:3739` (circle-packing element border) **explicitly skips the default color**
(`if(!GDefaults.DEFAULT_LINE_COLOR.equals(borderColor))`), so the default is never drawn; (b)
`GraphGenerator:526` feeds a fake/auto axis's `lineColor` from the plot border — which then flows through
the axis-line substitution below. There is **no general plot-area border draw** off `borderColor`. So B3
= **axis line only**.

**Mechanism — render-output substitution at the application point, NOT descriptor mutation.** The axis
line color is applied at **`GraphGenerator.setupAxisSpec` → `axis.setLineColor(axisD.getLineColor())`**
(`:2578`). Substitute there:

```java
Color lineColor = axisD.getLineColor();
if(VSChartChromeDefaults.isModern() && Tool.equals(lineColor, GDefaults.DEFAULT_LINE_COLOR)) {
   lineColor = VSChartChromeDefaults.gridlineColor(); // #E8E5DE, unify with gridlines
}
axis.setLineColor(lineColor);
```

Recommend wrapping the test+substitute in a unit-testable resolver on `VSChartChromeDefaults`
(e.g. `resolveAxisLineColor(Color current)` → modern iff gate on and `current` is the legacy default),
mirroring the tooltip `AUTO` resolution so it has coverage without a full render.

**Why this is clean (and better than the plan's earlier clone idea).** B2 showed descriptor mutation
drags in the RT-copy, per-column, and serialization/dirty tangle. Substituting at the single
application point sidesteps all of it: the descriptor is **never mutated**, so gate-off is trivially
byte-identical and there is no persistence/dirty concern. `setupAxisSpec` is called with the resolved
`axisD` (RT or design, whichever renders), so it catches every axis; fake axes inherit via `:526`/`:2660`.

**Value-equality is safe here (unlike Phase 5 C1).** It is a **single** comparison against the hardcoded
fallback `GDefaults.DEFAULT_LINE_COLOR` (`#EEEEEE`):
- unset (no CSS, no user) → `#EEEEEE` → matches → modernized ✓
- customer `format.css ChartAxisLine` → resolves to a non-`#EEEEEE` color → **skipped**, customer wins ✓
- user axis-line picker (`AxisPropertyDialogModel`) → non-`#EEEEEE` → **skipped**, user wins ✓
- only false-positive: a user who explicitly picks `#EEEEEE` — astronomically rare, and substituting the
  modern neutral under the gate is a defensible default. Far less fragile than Phase 5's multi-color
  style equality.

**Gate/color/risk.** Gate `VSChartChromeDefaults.isModern()` (same as B1/B2); color `gridlineColor()`
`#E8E5DE`; gate-off skips the substitution entirely (byte-identical). **Visual impact is subtle**
(`#EEEEEE` → `#E8E5DE`, near-white hairlines) — this is low-priority polish that completes chrome
unification; the owner may reasonably defer it.

**Why not the `format.css` CSS-dictionary route** (mirrors Phase 5's correction): `CSSDictionary` has
**no `viewsheet.modernVisualization` SreeEnv gate** — it's file/theme-driven per-org
(`PortalThemesManager`), so it can't carry the programmatic per-org gate. The `VSChartChromeDefaults`
Java resolver injected at the shared `CSSChartStyles.apply` seam mirrors `VSDensityDefaults`/
`VSTableStructureDefaults` and *rides on top of* the dictionary (customer CSS still wins).

**Risk / validation:** export-visible; changes the default chrome color of saved charts when the gate is
enabled (intended, org-gated). Must be gated per-org (never the browser `.viz-modern` class), gate-off
byte-identical, and validated with **PDF/PNG/SVG/Excel parity** plus a customer-`format.css`-chrome case
(confirm customer values still win) and a user-picker case. Confirm `isModern()`'s org-scoped `SreeEnv`
read resolves correctly on the **export/scheduled** path (`CSSProcessor`), as the density defaults already
do. Highest-risk item in Phase 6.

## Validation

1. **Build/lint:** `cd web && npm run build` and `npm run lint` clean.
2. **Gate-off inertness (Part A):** no CSS repoint remains (A1 reverted); the only change is the
   server `AUTO` resolution, which resolves to `DEFAULT` when the gate is off → gate-off behavior is
   unchanged; no export change (Part A touches no export render path).
3. **Gate-on (Part A / A2):** with modern on → an un-customized (`AUTO`) chart shows the CARD tooltip;
   a chart with an explicit `DEFAULT` still shows the legacy tooltip (explicit choice preserved); a
   chart with explicit `CARD` shows CARD in both modes. Gate off → `AUTO` charts show the legacy
   tooltip. Export (PDF/PNG/Excel) unchanged in every case (tooltip is DOM-only).
4. **Boundary check:** no `vsFormatModel` / `titleFormat` binding modified — grep the diff, confirm
   zero hits. No `ChartPalette` / `CategoricalColorFrame` (series) color touched — that is Phase 8.
5. **Part B (when taken):** gate-off byte-identical chart image across live + PDF/PNG/SVG/Excel; gate-on
   shows warm-neutral axis/gridline/legend/title/label; a user-set descriptor color (axis line, grid
   color, legend border) still overrides the modern default in both modes.
6. **Interaction-rule checks (design spec):** chrome reads lighter than marks; palette colors do not
   appear in tooltip/axis chrome. (Toolbar compaction deferred — A1.)

## Deferred / follow-ups (prioritized)

1. **Object title-bar modernization** — server-rendered (`titleFormat` `VSFormat`), export-visible,
   product-wide (all assemblies). **Now roadmap Phase 6A** (Assembly Title-Bar / Object-Chrome
   Defaults): a `VSTitleChromeDefaults` resolver mirroring `VSTableStructureDefaults`, **not** Phase 8
   — it is neutral chrome, not analytical data color, and spans every assembly type (D3).
2. **Series / palette / ramp / threshold color** — **Phase 8** (rides the Part B resolver mechanism).
3. **`warning` / `anomaly` conditional formatting** — server-rendered, export-visible → **Phase 8**.
4. **`--inet-viz-*` surround tokens** (`--inet-viz-tooltip-bg/-text/-border`, surround surface) — add to
   the contract only if a modern chart surround must visibly differ from shell neutrals (D-A); the
   design spec's "inherit shell neutrals first" argues against inventing them prematurely.
5. **Dark-mode chart chrome** — Part B is light-first; dark variants ride the initiative's dark pass
   (as with Phase 5 table structure).
6. **EM Material chrome** — separate build, admin, never exported (D5).
7. **Axis line color (Part B / B3)** — GROUNDED and scoped (see B3 above): reduced to the axis line
   only (plot border is moot — its default is never drawn and its fake-axis feed flows through the axis
   substitution), implemented as a **render-output substitution at `setupAxisSpec`** (no descriptor
   mutation, no clone), value-equality vs `GDefaults.DEFAULT_LINE_COLOR` behind the gate. Independent of
   B1/B2. Ready to implement; **low-priority polish** (subtle `#EEEEEE`→`#E8E5DE` hairline) — owner
   decides implement vs defer.
8. **Compact chart mini-toolbar (A1)** — the toolbar's geometry is JS-computed: vertical position uses
   the fixed constant `GuiTool.MINI_TOOLBAR_HEIGHT = 28` (`topY = top − 28 − adj`) and width/alignment
   use `miniToolbarService.getActionsWidth()`. A CSS-only resize desyncs both (overlap/gap vertically,
   misalignment horizontally). Compaction needs a coordinated change: make those JS values gate-aware
   (per density mode) **and** apply matching CSS. Own follow-up; not CSS-only, so out of Part A scope.

## Branching (per CLAUDE.md)

Part A is community-only: the D6 tooltip `AUTO` tri-state in `core` Java (resolved in
`AbstractChartInfo.getTooltipStyle()`, DOM-only, no export impact) plus a one-line `chart-model.ts`
comment; the mini-toolbar SCSS was reverted (A1 deferred). Part B adds the export-affecting core Java
(server-side) — all on the same community branch. Continues the visualization-theming work on `viz-updates`;
confirm whether Phase 6 continues there or a child `feature-{issue}`. Nothing on `main` or a
`v1.0.x`/`v1.1.x` release branch; nothing pushed/PR'd without explicit approval. An enterprise
submodule-pointer bump only at PR time.

## Related

- [visualization-implementation-roadmap.md](./visualization-implementation-roadmap.md) — Phase 6 tasks, chart-chrome render boundary, Phase 8 dependency note
- [visualization-design-spec.md](./visualization-design-spec.md) — Charts guidance, Rendering And Theming Architecture (System A/B), widget-chrome rules
- [visualization-phase1-contract.md](./visualization-phase1-contract.md) — §2c chart-color conceptual-source constraint, §2b state tokens
- [visualization-phase4-implementation-plan.md](./visualization-phase4-implementation-plan.md) — render-location routing method
- [visualization-phase5-implementation-plan.md](./visualization-phase5-implementation-plan.md) — the Parts-by-render-location structure, gated server resolver (`VSTableStructureDefaults`) + injection point this plan mirrors
- [visualization-palette-swatches.html](./visualization-palette-swatches.html) — palette source of truth; add a "Chart Chrome Tokens" group for Part B
- Feature #74894 (commit `e5a93b3`, "Add card-style chart tooltip") — the modern card tooltip Phase 6 defaults to under the gate (D6)
