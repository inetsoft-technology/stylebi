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
  export-affecting half: a `VSChartChromeDefaults` resolver + neutral defaults for axis/gridline/
  legend/title/label. Scoped here but **recommended as its own follow-up sub-plan** after Part A
  validates (mirrors Phase 5's D1). This is the mechanism Phase 8 depends on.

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

**The two exceptions (plain `Color`, serialized — value-equality unavoidable):**
- **`AxisDescriptor.lineColor`** (`:893`, plain `Color`, no user-set flag; UI writes it directly via
  `AxisPropertyDialogModel:257`) — **and, correcting an earlier assumption, `PlotDescriptor.borderColor`
  is ALSO a plain `Color`** (`:1943`, not a `CompositeValue`), serialized (`:1650`), and it feeds the
  auto-created ("fake") axis line color (`GraphGenerator.java:526`).
- These have no tier to hide a default in, so a defaults-only override must (a) value-equality-test
  against the shipped default `ChartLineColor.getAxisLineColor(GDefaults.DEFAULT_LINE_COLOR)` (= the
  fragile pattern Phase 5 C1 abandoned) and (b) apply the modern value to a per-render **clone** /
  `RTAxisDescriptor` since the field is serialized.

**Recommendation for the Part B sub-plan:** do the four clean element groups via the CSS/default-format
tiers at the `fixChartFormat`/`CSSChartStyles.apply` seam (covers gridlines, legend border, and all
chrome text). For axis line + plot border, either (i) accept the isolated value-equality guard on those
two fields, or (ii) **defer them** — a modern gridline+legend+text pass is already most of the visual
win, and the axis/plot border lines currently share the same `#EEEEEE` as gridlines, so recoloring
gridlines alone still reads coherent. Decide (i) vs (ii) when the sub-plan is taken.

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

### Part B — Server-side in-graph chrome neutrals + gated resolver (scoped sub-plan; needs owner go-ahead)

**Mechanism:** add `VSChartChromeDefaults` (stateless, `uql/viewsheet/internal/`) mirroring
`VSTableStructureDefaults`:
- `isModern()` = `VSDensityDefaults.isModern()` **AND** `viewsheet.modernChartChrome` not `"false"`
  (org-scoped, defaults ON when modern is on) — an admin escape hatch, same shape as
  `viewsheet.modernTableStructure`.
- color accessors returning warm-neutral chrome (light-mode first; dark deferred): `axisLineColor()`,
  `gridlineColor()`, `legendBorderColor()`, `axisLabelColor()` / `axisTitleColor()`,
  `legendTextColor()`, `plotLabelColor()`.

**Proposed neutral values** (align with the Phase 5 table-structure palette and shell; **proposed — no
design spec yet**, add a "Chart Chrome Tokens" group to `visualization-palette-swatches.html` as Phase 5
did for table structure):
- axis line + gridline + legend border → **`#E8E5DE`** (= table `gridlineColor`, unifies all hairline
  chrome; warmer + subtler than today's `#EEEEEE`)
- axis tick/label + legend content text → **`#6A685F`** (= table `headerForeground` / shell text-muted;
  quiet chrome, verify legibility on white plot)
- axis/legend title text → **`#35342F`** (= shell text-default; slightly stronger than labels, warm,
  quieter than today's near-black `#2B2B2B`)
- legend background → leave `Color.WHITE` α50 (transparent-ish); no change first pass

**Injection (grounded — D4):** write the modern chrome to the **CSS tier** of the `CompositeValue`
chrome and the **default-format** tier of the chrome text formats, inside
`VGraphPair.fixChartFormat` / `CSSChartStyles.apply`, gated by `VSChartChromeDefaults.isModern()`.
These tiers are reset-and-reapplied every render and never serialized, so it is defaults-only (USER
wins) and cannot dirty saved state — no clone needed. Covers gridlines, legend border, and all chrome
text cleanly. `AxisDescriptor.lineColor` and `PlotDescriptor.borderColor` are plain serialized `Color`
fields (no tier) — either guarded by value-equality on a clone, or deferred (D4 (i)/(ii)).

**Why not the `defaults.css`/`format.css` CSS route** (mirrors Phase 5's route correction): the CSS
dictionary has **no `viewsheet.modernVisualization` SreeEnv gate** — it is file/theme-driven per-org
(`PortalThemesManager`), so it cannot carry the required programmatic per-org gate. The Java resolver
at the descriptor-construction seam is the route that mirrors `VSDensityDefaults`/`VSTableStructureDefaults`.

**Risk:** export-visible; reflows saved charts that rely on default chrome color. **Must** be gated
per-org (never the browser `.viz-modern` class), gate-off byte-identical, its own validation pass with
PDF/PNG/SVG/Excel parity checks. Highest-risk item in Phase 6.

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
7. **Axis line + plot border color (Part B tail)** — the two plain-`Color`, serialized fields
   (`AxisDescriptor.lineColor`, `PlotDescriptor.borderColor`) have no tier to hold a default, so they
   need value-equality-vs-`GDefaults` on a cloned/RT descriptor (the fragile Phase 5 C1 pattern) —
   decided (i) guard vs (ii) defer when the Part B sub-plan is taken (D4). The clean Part B elements
   (gridlines, facet, legend border, all chrome text) do **not** depend on this.
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
