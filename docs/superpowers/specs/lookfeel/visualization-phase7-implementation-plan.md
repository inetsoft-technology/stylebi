# StyleBI Visualization — Phase 7 (KPI And Embedded Controls) — Implementation Plan

## Scope

This plan executes **Phase 7 (KPI And Embedded Controls)** of
[visualization-implementation-roadmap.md](./visualization-implementation-roadmap.md). Phase 7
standardizes two families of visualization widget that are neither tables (Phase 5) nor charts
(Phase 6):

1. **KPI / summary widgets** — the output assemblies that display a single computed value: **Text**
   (`TextVSAssembly`), **Gauge** (`GaugeVSAssembly`), and the range-output family
   (**Thermometer / Cylinder / SlidingScale**). Roadmap Task 1 asks for a hierarchy across *primary
   value → comparison value → semantic emphasis → sparkline/trend → secondary metadata*.
2. **Embedded controls** — the input assemblies placed inside a dashboard: **ComboBox, RadioButton,
   CheckBox, Slider, Spinner, TextInput**, plus the interactive chrome of the embedded-filter data
   surfaces (**SelectionList / SelectionTree / RangeSlider**). Roadmap Task 2 asks these to follow
   *visualization density*, *shell-derived control language*, explicit *filtered/on* states, and
   *neutral passive chrome*.

Like Phases 3, 5, and 6, Phase 7 crosses the render-location boundary and is therefore delivered
**in parts by render location**, following the same risk-staging: land the low-risk browser-CSS half
first, and scope the export-affecting server half as a gated follow-up sub-plan taken after the CSS
half validates.

### The render-location boundary governs every task (read this first)

The foundational constraint from the Phase 0 audit and
[visualization-design-spec.md](./visualization-design-spec.md) ("Rendering And Theming Architecture")
applies with full force here, and it is the single most important framing for Phase 7: **every KPI
and every embedded control is a viewsheet assembly that extends `AbstractVSAssembly`, so it is
server-rendered and appears in export.** A browser `--inet-viz-*` CSS change cannot drive the
exported pixels. Verified against current code (2026-07-14):

- **Text / output KPI** — the live view renders in the Angular DOM (`vs-text.component`) with inline
  styles read from the server `VSFormat`, but the exported value is drawn **server-side** through
  `AbstractVSExporter.writeText(...)` (`AbstractVSExporter.java:1893-1901`, dispatched at `:1318`)
  from the same `VSFormat`. The text default `VSFormat` (font, foreground, alignment, border) is
  seeded **server-side** in `TextVSAssemblyInfo.setDefaultFormat(boolean)`
  (`TextVSAssemblyInfo.java:73-92`) — bold 11pt, foreground `0x2b2b2b`, `H_LEFT|V_CENTER`, border
  `DEFAULT_BORDER_COLOR = 0xDADADA` (`VSAssemblyInfo.java:1545`). This is the exact analog of
  Phase 6A's `titleFormat` seam and Phase 5's Default-Style seam.
- **Gauge / range-output KPI** — rendered as a **server-produced image** (`<img>` in
  `vs-gauge.component.scss`; `GaugeVSAssemblyInfo` draws through
  `inetsoft.report.gui.viewsheet.gauge.VSGauge`, default face `10120`,
  `GaugeVSAssemblyInfo.java:46`), and exported the same way through `writeGauge(...)`
  (abstract `AbstractVSExporter.java:2139`, dispatched at `:1309`). There is **no CSS emphasis
  surface at all** — a gauge's hierarchy/emphasis lives entirely in its face + descriptor, all
  server-side.
- **Embedded controls** — the live view is Angular DOM (each `vs-*.component.scss`) styled by
  component chrome + inline `VSFormat`, but export is server-rendered through
  `writeComboBox/writeCheckBox/writeRadioButton/writeSlider/writeSpinner/writeTextInput`
  (`AbstractVSExporter.java:1321-1339`; shared `writeInputWithLabel`/`writeInputLabelText` at
  `:3709`/`:3848`). So the **interactive** chrome (a combo dropdown panel, a slider track/handle, a
  hover/on state) is DOM-only and never exported, while the **control's own box** (font, foreground,
  background, border) resolves from the server `VSFormat` and *is* exported.

**Consequence for Phase 7, stated plainly:** KPI hierarchy/emphasis defaults and any control-box
default that must survive export **must be server-side** (a gated `VSFormat`-default resolver, the
Phase 5/6A pattern). Only transient interactive chrome — dropdown panels, slider tracks, hover/on/
filtered overlays — is legitimately browser-CSS-themeable via `--inet-viz-*`.

### Parts

- **Part A — Browser-DOM embedded-control + KPI-surround chrome (CSS). The shippable payload.**
  Repoint the *interactive* chrome of embedded controls to the visualization density + state tokens
  under the gate (`--inet-viz-control-height`, `--inet-viz-hover-bg`, `--inet-viz-selected-bg/-text`,
  `--inet-viz-filtered-bg`), and adopt the filtered/on state on live embedded-filter overlays. Broad,
  gate-off byte-identical, **zero export impact**.
- **Part B — Server-side KPI hierarchy/emphasis + control-box defaults (server bridge).** The hard,
  export-affecting half: a gated `VSOutputChromeDefaults` resolver (mirroring
  `VSTableStructureDefaults` / `VSChartChromeDefaults`) seeding modern KPI text/output `VSFormat`
  defaults at the `setDefaultFormat` seam, plus neutral gauge-face/descriptor defaults. Recommended
  as its own follow-up sub-plan after Part A validates and after the shared modern palette is settled
  (see the dependency section). It is the same mechanism-first staging Phases 5 and 6 used.

Phase 7 does **not**:

- change any chart **mark / series / palette** color, ramp, or threshold — that is **Phase 8**
  (a KPI trend/sparkline is a small chart assembly, so its chrome/color rides Phase 6 chrome +
  Phase 8 color, not this phase — see Deferred item 1)
- implement `warning`/`anomaly` **conditional-formatting color primitives** — server-rendered,
  export-visible → **Phase 8**. (The *existing* KPI highlight mechanism — `OutputVSAssemblyInfo`'s
  `HighlightGroup` → `highlightFg/highlightBg/highlightFont`, `OutputVSAssemblyInfo.java:86-110` — is
  the server-side "semantic emphasis" path Phase 7 documents usage rules for, but Phase 7 adds no new
  color plumbing to it)
- restyle the **viewsheet object title bar** shared by KPI/control assemblies — that is **Phase 6A**
  (`VSTitleChromeDefaults`), still unimplemented; see the dependency section
- adopt viz tokens into EM (`projects/em`) — separate build, admin chrome, never exported (deferred
  since Phase 3)

**Definition of done (Part A):** with the gate **off**, computed styles are byte-identical to today
(the repointed tokens' `:root` defaults equal the shell tokens previously used); no export output
changes anywhere. With the gate **on**, embedded controls pick up the visualization density
(compact control heights, tighter option rows) and the modern state palette (hover/selected/filtered/
on), and no server render path — hence no export — has changed. Part A touches only SCSS in
`community/web`.

## Grounding (verified against current code, 2026-07-14)

### World A — Browser-DOM interactive chrome (Part A targets)

| Surface | file:line | current styling | note |
|---|---|---|---|
| **ComboBox dropdown option row** | `vsobjects/objects/combo-box/vs-combo-box.component.scss` (`.dropdown-item`, `&:hover`, `&.selected`) | padding `8px 12px`; hover bg `--inet-hover-primary-bg-color`; selected bg `--inet-selected-item-bg-color` + text `--inet-selected-item-text-color` + `font-weight:600` | **shell tokens on a data-like pick list** → candidate repoint: hover→`--inet-viz-hover-bg` (exact `:root` match, parity-safe), selected→`--inet-viz-selected-bg`/`-selected-text`, row padding/height under density. Live only; export shows the resolved value, not the panel. |
| **ComboBox dropdown viewport** | same file (`.dropdown-viewport { min-height:350px }`, `.dropdown-item` padding) | fixed geometry | density candidate — dropdown behaves like a "dense scrollable list" (design-spec §Density, exactly the allowed local-override case) |
| **Slider track / handle / ticks** | `vsobjects/objects/slider/vs-slider.component.scss` `:host` | **hardcoded** M3 neutrals: active track `#9e9e9e`, inactive `#e0e0e0`, handle `#9e9e9e`, tick `rgba(0,0,0,.38)` | passive chrome → candidate neutralization to visualization neutrals under the gate; **not parity-safe** (hardcoded, not equal to any viz `:root`), so gate-scoped only or defer (mirrors Phase 5 D3 sort-glyph snag) |
| **RadioButton / CheckBox default border** | `vs-radio-button.component.scss` (`.vs-radio-button__default-border`), `vs-check-box.component.scss` (`.vs-check-box__default-border`) | `1px solid --inet-default-border-color` | passive frame — leave shell-neutral (design-spec "neutral passive chrome"); no state to adopt |
| **Spinner / TextInput box** | `vs-spinner.component.scss`, `vs-text-input.component.scss` | inherits `VSFormat`; `TextInput` `border-width:1px` | control-box is server `VSFormat` (Part B); only control-height density is a Part A candidate |
| **Embedded-filter overlays (SelectionList/Tree, RangeSlider)** | selection density already server-side (`SelectionBaseVSAssemblyInfo.getEffectiveCellHeight`, `userCellHeight`, Phase 3 B2b); range-slider chrome `vs-range-slider.component.scss` | Phase 3 density landed; **no `filtered`/`on` state token consumed yet** | the `filtered`/`on` state (`--inet-viz-filtered-bg`) has **zero consumers** today — this is where Phase 7 adopts it on live embedded-filter overlays (DOM; export is server `VSFormat`) |
| **KPI text surround** | `vsobjects/objects/output/text/vs-text.component.scss` | inline from server `VSFormat`; `.component-input` border `#ccc`; **no viz token** | KPI value is server-owned (Part B); the DOM has no themeable emphasis surface, like the Phase 6 title-bar correction |
| **KPI gauge** | `vsobjects/objects/output/gauge/vs-gauge.component.scss` | `<img>` (server image) | **no CSS surface** — emphasis is server-side face/descriptor only (Part B, and mostly deferred — a face is a themed asset) |

**Current `--inet-viz-*` consumers inside `vsobjects/` (post Phase 6):** exactly **one** —
`vs-table.component.scss:108` (`--inet-viz-hover-bg`, Phase 5 Part B). **No embedded control and no KPI
widget consumes a viz token today.** So Part A is net-new adoption on the embedded-control surfaces.

**Client gate is fully wired** (Phase 2/3): body `.viz-modern` (+ `viz-density-<mode>`) is applied at
the portal, viewer, and composer app roots, each reading the server-forwarded `modernVisualization`
flag. The density + state tokens Part A consumes are defined in `_viz-tokens.scss` (density modes at
`:93-119`, including `--inet-viz-control-height` 24/28/30 per mode; state palette at `:71-82`,
including `--inet-viz-filtered-bg #F6E2C8`). Part A rules scope cleanly under the gate.

### World B — Server-rendered KPI/control defaults (Part B targets)

The output-assembly hierarchy and where each default is seeded:

| KPI/control default | seam (file:line) | current default | export path |
|---|---|---|---|
| **Text value font/fg/align/border** | `TextVSAssemblyInfo.setDefaultFormat(boolean)` (`:73-92`), via `initDefaultFormat()` (`:65`) | bold 11pt, fg `0x2b2b2b`, `H_LEFT\|V_CENTER`, border `0xDADADA` | `writeText` (`AbstractVSExporter.java:1893-1901`) |
| **Semantic emphasis (highlight)** | `OutputVSAssemblyInfo.updateHighlight(...)` (`:86-110`) → `highlightFg`/`highlightBg`/`highlightFont` from the user `HighlightGroup` | user-defined; none by default | baked into the rendered/exported cell |
| **Gauge face + value-fill** | `GaugeVSAssemblyInfo` ctor face `10120` (`:46`), `valueFillColor` (`:92-118`); rendered by `VSGauge` | themed face asset | `writeGauge` (`AbstractVSExporter.java:2139`) — server image |
| **Range-output (thermometer/cylinder/sliding-scale)** | `RangeOutputVSAssemblyInfo` min/max/inc + descriptor | `GDefaults`-derived | same output image path |
| **Input control box (combo/radio/check/spinner/text)** | inherited `VSAssemblyInfo` `VSFormat` default (per-type `getObjCSSType()` CSS tier, e.g. `CSSConstants.GAUGE` at `GaugeVSAssemblyInfo.java:202`) | shell-neutral | `writeInputWithLabel`/per-type writer (`:3709`, `:1321-1339`) |

**Colors resolve through the same three tiers charts and tables use:** per-object **USER** `VSFormat`
→ **CSS tier** (`CSSDictionary` over `format.css`/`defaults.css`, keyed by the assembly
`getObjCSSType()` class) → the hardcoded default seeded in `setDefaultFormat`. As Phase 5/6 already
established, the CSS-dictionary route **cannot carry the `viewsheet.modernVisualization` per-org
SreeEnv gate** (it is file/theme-driven per org), so a modern default must be injected by a **Java
resolver at the `setDefaultFormat` seam**, exactly like `VSTableStructureDefaults` /
`VSChartChromeDefaults`.

### The gated resolver pattern to mirror (Part B mechanism)

| Anchor | Location | Note |
|---|---|---|
| Density resolver | `VSDensityDefaults.isModern()` (`:39-40`) | `SreeEnv.getBooleanProperty("viewsheet.modernVisualization", false, true)` (3rd arg = org-scoped) |
| Table-structure resolver | `VSTableStructureDefaults.isModern()` (`:40-45`) | density gate **AND** `viewsheet.modernTableStructure` ≠ `"false"`; exposes warm-neutral structure palette |
| Chart-chrome resolver | `VSChartChromeDefaults.isModern()` (`:44-49`) | density gate **AND** `viewsheet.modernChartChrome` ≠ `"false"`; `gridlineColor #E8E5DE`, `labelColor #6A685F`, `titleColor #35342F` |
| **No output/input render path reads the gate today** | — | verified: `TextVSAssemblyInfo.setDefaultFormat`, `GaugeVSAssemblyInfo`, and the input infos are entirely un-gated. Part B is the first KPI/control render path to read it. |

## Dependency on the server-side modern-mode selection mechanism (call out and sequence)

Phase 7 Part B **depends on a server-side mechanism whose existence prior phase docs put in doubt**,
so this plan states its status explicitly before scheduling any server work.

**Status — the mechanism EXISTS and is proven on three server render paths (verified 2026-07-14).**
The Phase 2 plan characterized `viewsheet.modernVisualization` as "client-forward-only … NO render
path consumes it server-side." That characterization is **now stale**: downstream phases wired
server-side consumption of the same property, all gating on
`SreeEnv.getBooleanProperty("viewsheet.modernVisualization", false, true)` (org-scoped):

- `VSDensityDefaults.isModern()` (Phase 3) → assembly row/header/cell heights, in model **and** export.
- `VSTableStructureDefaults.isModern()` (Phase 5 C1b) → table gridline/header structure, export-visible.
- `VSChartChromeDefaults.isModern()` (Phase 6 Part B) → chart gridline/legend/axis chrome, export-visible.

So the "server-side bridge and server-side modern-mode selection mechanism" the roadmap's Phase 8
dependency note requires be *agreed before further server color work* is **already built and
validated by three sibling resolvers**. Phase 7 Part B does not invent it; it **mirrors** it with a
fourth resolver (`VSOutputChromeDefaults`). This is a hard prerequisite and it is satisfied.

**Blocking sequencing dependency — Phase 6A is NOT built.** Every KPI and every embedded control is
wrapped in the shared **object title bar**, rendered server-side from `titleFormat` (a `VSFormat`),
which **Phase 6A (`VSTitleChromeDefaults`) was chartered to modernize but has not** — verified: no
`VSTitleChromeDefaults.java` exists anywhere in the repo, and `viewsheet.modernObjectChrome` is
referenced by zero source files. Consequence: if Phase 7 Part B modernizes the KPI *value* body but
the title bar above it stays legacy, a modern KPI will look half-themed. **Part B should not ship its
KPI-body defaults ahead of, or without coordinating the neutral palette with, Phase 6A.** The two are
the same shape (gated server-side `VSFormat`-default resolvers) and should share the modern-mode gate
and the warm-neutral palette. Recommended order: **Part A (this phase, now) → Phase 6A title chrome →
Phase 7 Part B KPI-body defaults**, or Part B and Phase 6A landed together as a coordinated
server-chrome pass. Part A has **no** dependency on either and is shippable immediately.

## Decisions (proposed — resolve with initiative owner before Part B)

### D1 — Phase 7 first pass = Part A (CSS, zero export); Part B is a gated follow-up sub-plan → **RECOMMEND YES**

Exactly Phases 5 (D1) and 6 (D2). Part A (embedded-control density/state repoints) is broad,
gate-off byte-identical, no server change, no export change — shippable now. Part B changes
`VSFormat` defaults, is export-visible, reflows saved KPI widgets on enable, and is coupled to the
unbuilt Phase 6A, so it should be its own sub-plan taken after Part A validates, with
PDF/PNG/SVG/Excel parity checks. Keeps the low-risk 80% shippable immediately.

### D2 — State tokens on data-surface controls only; passive chrome stays shell-neutral → **RECOMMEND YES**

Carries forward Phase 5's D2 reversal. The `filtered`/`on`/`selected`/`hover` **state** palette goes
only on surfaces that behave like analytical data (the combo/dropdown pick list, embedded-filter
overlays). Passive framing (radio/checkbox borders, the input box outline) stays shell-neutral per the
design spec's "neutral passive chrome" rule. Do **not** let the visualization selection teal reach
generic form controls — that repeats the Phase 5 shell/Composer leak.

### D3 — Slider track/handle neutrals: gate-scoped only, or defer → **RECOMMEND: gate-scoped, verify parity**

The slider track/handle/tick colors are **hardcoded** (`#9e9e9e`/`#e0e0e0`/`rgba(0,0,0,.38)`), not
shell tokens, so repointing them unconditionally would change the **gate-off** (all-users) appearance
— the same parity snag Phase 5 D3 hit with the sort glyph. Option (a): a gate-scoped `.viz-modern`
rule that repoints the `--slider-*` custom properties to visualization neutrals only under the gate
(gate-off untouched). Option (b): defer entirely (low value). Recommend (a) if a modern slider neutral
is desired, else defer.

### D4 — Embedded-control density inherits visualization density, not a separate control matrix → **RECOMMEND YES**

Per design-spec §Density, controls should follow the surrounding visualization density and stay
hittable. Part A repoints control heights and dropdown row rhythm to the existing
`--inet-viz-control-height` (24/28/30 per mode) and `--inet-viz-cell-padding-*`, not a new token
group. No new density tokens are invented.

### D5 — KPI compound hierarchy is a *composition* pattern, not a widget feature → **RECOMMEND: document, do not build a new widget**

StyleBI has **no compound "scorecard/KPI card"** assembly (confirmed: no such class among the
`*VSAssemblyInfo` set; a KPI is a Text or Gauge assembly, optionally grouped with a trend chart in a
`GroupContainer`). The roadmap's "primary value / comparison value / sparkline / secondary metadata"
hierarchy is therefore realized by *composing* existing assemblies + the highlight mechanism +
`VSFormat` weight/size defaults — **not** by adding a new multi-slot widget. Part B modernizes the
**primary-value** default emphasis (weight/size/color of the Text/Gauge value); comparison value,
sparkline, and secondary metadata are authored as sibling assemblies and inherit the same modern
neutrals. Building a first-class KPI card is a **product feature**, out of scope for a theming pass
(Deferred item 2).

### D6 — Semantic emphasis rides the existing highlight mechanism; no new color plumbing → **RECOMMEND YES**

KPI "semantic emphasis when meaning justifies it" is already server-side and export-visible via
`OutputVSAssemblyInfo`'s `HighlightGroup` (`:86-110`). Phase 7 documents the *usage rule* (semantic
color only when meaning justifies it; keep comparison/sparkline subordinate) and, if a modern
threshold/anomaly palette is wanted as a highlight **default**, that is Phase 8 conditional-formatting
color — not Phase 7. Phase 7 adds no code to the highlight path.

### D7 — EM Material chrome deferred → **RECOMMEND YES** (unchanged from Phase 3/5/6)

### D8 — Gauge face modernization deferred → **RECOMMEND YES**

A gauge renders from a themed **face asset** (`VSGauge`, face `10120`) plus descriptor. Modernizing
gauge emphasis means authoring/selecting a modern face + neutral value-fill default — an asset-and-
descriptor pass distinct from a `VSFormat` seam. Part B may seed a neutral default value-fill under
the gate, but a full modern gauge face is deferred (Deferred item 6).

## Changes

### Part A — Browser-DOM embedded-control + KPI-surround chrome (CSS) — PROPOSED

All repoints are gate-off inert: each repointed token's `:root` default equals the shell token
previously used, so computed styles are byte-identical with the gate off and shift only under
`.viz-modern`. No `vsFormatModel` / server binding is touched, so **export is unchanged**.

**A1 — ComboBox dropdown pick list (density + state).**
- `.dropdown-item` hover `--inet-hover-primary-bg-color` → `--inet-viz-hover-bg` (exact `:root`
  match, parity-safe).
- `.dropdown-item.selected` bg `--inet-selected-item-bg-color` → `--inet-viz-selected-bg`, text
  `--inet-selected-item-text-color` → `--inet-viz-selected-text` (verify `:root` equality before
  swap, as Phase 5 did for `%selected-item-colors`).
- `.dropdown-item` vertical rhythm → `--inet-viz-cell-padding-y` / row height under density (the
  dropdown is the design-spec's explicit "dense scrollable list" local-override case).

**A2 — Embedded-control height (density).** Repoint the control-box height of combo/spinner/
text-input to `--inet-viz-control-height` under the gate so embedded controls compact with
visualization density (24/28/30). Confirm gate-off equals the current shell control height for
parity.

**A3 — Embedded-filter `filtered`/`on` state (net-new token adoption).** Adopt
`--inet-viz-filtered-bg` (and `--inet-viz-selected-bg` for "on") on the **live** embedded-filter DOM
overlays (selection list/tree item in the filtered/selected state, range-slider active band) — the
first consumers of `--inet-viz-filtered-bg`, which has zero consumers today. Parity: verify the
current filtered/selected color equals the token `:root` default, else make it a gate-scoped
`.viz-modern` rule (Phase 5 D3 method). **The export-visible selected/filtered fill is server
`VSFormat` and is NOT changed here** — this is the live-overlay half only, exactly like Phase 5
Part B.

**A4 — Slider neutrals (D3, optional).** If approved, a gate-scoped `.viz-modern` block repoints the
`--slider-*` custom properties to visualization neutrals; gate-off untouched. Else deferred.

**Left shell-neutral (D2):** radio/checkbox default borders, the text-input/spinner box outline, and
any generic form control — passive chrome per the design spec, no state to adopt.

**Part A completeness note.** Like Phase 5's browser-DOM world, the genuinely adoptable interactive
surfaces here are few (the combo dropdown, the embedded-filter overlays, optionally the slider),
because StyleBI's controls render most of their appearance from the server `VSFormat`, not from
component CSS. That is expected and correct — the bulk of KPI/control theming is inherently Part B
(server-side), and Part A is the thin, safe, live-only slice.

### Part B — Server-side KPI hierarchy/emphasis + control-box defaults (gated resolver; needs owner go-ahead + Phase 6A coordination)

Export-affecting server work. Mirrors `VSTableStructureDefaults` / `VSChartChromeDefaults`.

**Gate (`VSOutputChromeDefaults`, `uql/viewsheet/internal/`).** `isModern()` =
`VSDensityDefaults.isModern()` **AND** `SreeEnv.getProperty("viewsheet.modernKpiChrome", …, true)`
≠ `"false"` (org-scoped, defaults ON when modern is on — the admin escape hatch, same shape as
`viewsheet.modernTableStructure`/`modernChartChrome`). Expose the modern KPI-value defaults as
accessors: `valueForeground()`, `valueFont()` / weight+size, `labelForeground()` (secondary/label
text), and a neutral `borderColor()`.

**B1 — KPI text/output value defaults (the clean first seam).** In
`TextVSAssemblyInfo.setDefaultFormat(boolean)` (and the shared output-format seeding), when
`VSOutputChromeDefaults.isModern()`, seed the modern value emphasis instead of the legacy `0x2b2b2b`
bold-11 default: a stronger primary-value weight/size and the warm-neutral foreground (coordinated
with the Phase 5/6 neutrals — proposed `#35342F` title-strength for the primary value,
`#6A685F` for subordinate/label text), and the neutral border default. **Applied defaults-only**: an
explicit user `VSFormat` still wins (it merges above the default), so gate-off is byte-identical and
saved KPIs with custom formats are untouched. This is the direct analog of Phase 6A's title-format
seam.

**B2 — Range-output / gauge neutral defaults (guard-or-defer).** Seed a neutral default value-fill on
the range-output family under the gate; **defer** the gauge *face* modernization (D8 — an asset pass).

**B3 — Input control-box defaults (optional).** If a modern control-box neutral is wanted in export
(not just live density from Part A), seed it at the input `VSFormat` default seam under the gate,
defaults-only. Low priority — controls are mostly chrome, and their exported box rarely carries
emphasis; likely defer.

**Proposed palette (no design spec yet — add a "KPI / Control Chrome Tokens" group to
`visualization-palette-swatches.html`, coordinated with Phase 5 table-structure and Phase 6
chart-chrome neutrals):**
- primary KPI value foreground → `#35342F` (= shell text-default / Phase 6 title neutral)
- secondary/label/metadata foreground → `#6A685F` (= Phase 5 `headerForeground` / Phase 6 `labelColor`)
- control/KPI border default → `#D9D5CC` (= `headerSeparator`, the shared structural border) or keep
  `0xDADADA` if parity with existing frames is preferred
- semantic emphasis → **defer to Phase 8** (threshold/anomaly highlight defaults)

**Why a Java resolver, not `format.css`** (same correction Phase 5/6 made): `CSSDictionary` has no
`viewsheet.modernVisualization` SreeEnv gate — it is file/theme-driven per org — so it cannot carry
the programmatic per-org gate. The `VSOutputChromeDefaults` resolver injected at the `setDefaultFormat`
seam mirrors the three existing resolvers and rides on top of the CSS dictionary (a customer's
`format.css` KPI class still wins as the CSS tier above the seeded default).

**Risk / validation:** export-visible; changes the default emphasis of saved default-formatted KPIs
when the gate is enabled (intended, org-gated). Must be gated per-org (never the browser
`.viz-modern` class), gate-off byte-identical, and validated with **PDF/PNG/SVG/Excel parity** plus a
customer-`format.css` KPI case and a user-format case (confirm both still win). Coordinate the
neutral palette with Phase 6A so a KPI's title bar and value body modernize together. Highest-risk
item in Phase 7.

## Validation

1. **Build/lint:** `cd web && npm run build` and `npm run lint` clean.
2. **Gate-off inertness (Part A):** every Part A repoint's legacy value equals the token `:root`
   default → computed styles byte-identical; confirm no diff on a combo box (closed + open dropdown),
   a selection list/tree, a range slider, a slider, a spinner, a text input, and a text/gauge KPI.
   **No export changes** (Part A touches no server path).
3. **Gate-on DOM (Part A):** `body.viz-modern` → combo dropdown shows modern hover/selected + compact
   density; embedded-filter overlays show the modern `filtered`/`on` state; controls compact to
   `--inet-viz-control-height`. Passive chrome (radio/checkbox borders, input box) unchanged in both
   modes (D2).
4. **Boundary check:** no viewsheet-assembly `vsFormatModel` / `titleFormat` binding modified —
   grep the diff, confirm zero hits. No `ChartPalette` / series color touched (Phase 8). No
   `HighlightGroup` code touched (D6).
5. **Part B (when taken):** gate-off byte-identical KPI render across live + PDF/PNG/SVG/Excel;
   gate-on shows the modern primary-value emphasis + neutral label/border; a user-set `VSFormat`
   (value color, font, border) and a customer `format.css` KPI class still override the modern
   default in both modes. Confirm `isModern()`'s org-scoped `SreeEnv` read resolves on the
   export/scheduled path as the density/structure defaults already do.
6. **Coordination check (Part B):** modernize a KPI whose title bar is also modern (once Phase 6A
   lands) and confirm title + value read as one system, not half-themed.
7. **Interaction-rule checks (design spec):** KPI comparison/sparkline elements stay subordinate to
   the primary value; controls stay hittable at dense sizes; chart categorical hues do not appear on
   control chrome.

## Deferred / follow-ups (prioritized)

1. **KPI sparkline / trend treatment** — a trend is a small **chart assembly**, so its chrome rides
   **Phase 6** (chart chrome) and its color rides **Phase 8**; there is no dedicated sparkline
   widget to theme in Phase 7. Document the composition pattern; no code.
2. **First-class compound KPI card** — a multi-slot primary/comparison/metadata widget does not exist
   (D5); building one is a product feature, not a theming pass.
3. **Conditional-formatting / threshold-anomaly color primitives** — server-rendered, export-visible
   → **Phase 8** (the KPI highlight mechanism is the delivery surface, but the modern color set is
   Phase 8).
4. **Object title-bar modernization (Phase 6A)** — `VSTitleChromeDefaults`, unbuilt; a hard
   coordination dependency for Part B (KPI/control title bars). Sequence Part B alongside or after it.
5. **Slider neutral repoint (Part A / A4)** — hardcoded track/handle colors, not parity-safe;
   gate-scoped only or defer (D3).
6. **Gauge face modernization** — a themed asset + descriptor pass, distinct from the `VSFormat` seam
   (D8); Part B may seed a neutral value-fill but the modern face is deferred.
7. **Input control-box server defaults (Part B / B3)** — low value; controls rarely carry exported
   box emphasis; likely defer.
8. **Dark-mode KPI/control chrome** — Part B is light-first; dark variants ride the initiative's dark
   pass (as with Phase 5/6).
9. **EM Material chrome** — separate build, admin, never exported (D7).

## Branching (per CLAUDE.md)

Part A is community-only CSS (SCSS in the `community/` submodule); enterprise submodule-pointer bump
only at PR time. Part B adds core Java (the `VSOutputChromeDefaults` resolver + `setDefaultFormat`
seam) — same community branch, and should be coordinated with the Phase 6A server-chrome work.
Continues the visualization-theming work on the initiative branch (`viz-updates` per Phase 6);
confirm whether Phase 7 continues there or a child `feature-{issue}`. Nothing on `main` or a
`v1.0.x`/`v1.1.x` release branch; nothing pushed/PR'd without explicit approval.

## Related

- [visualization-implementation-roadmap.md](./visualization-implementation-roadmap.md) — Phase 7 tasks (KPI hierarchy, embedded controls), Phase 8 dependency note, the modern-mode selection requirement
- [visualization-design-spec.md](./visualization-design-spec.md) — KPI And Summary Widgets, Embedded Filters And Controls, Density (local-override rule), Rendering And Theming Architecture (render-location boundary)
- [visualization-phase0-audit.md](./visualization-phase0-audit.md) — the render-location boundary (Task 4) and server-owned defaults (Task 5) this phase rests on
- [visualization-phase1-contract.md](./visualization-phase1-contract.md) — density + state token `:root` defaults (parity baselines); `--inet-viz-control-height`, `--inet-viz-filtered-bg`
- [visualization-phase3-implementation-plan.md](./visualization-phase3-implementation-plan.md) — the gated `VSDensityDefaults` resolver + selection-list `getEffectiveCellHeight` density (embedded-filter data surfaces)
- [visualization-phase5-implementation-plan.md](./visualization-phase5-implementation-plan.md) — the Parts-by-render-location structure, gated server resolver + `VSFormat`-default seam, state-on-data-surfaces-only reversal (D2), sort-glyph parity snag (D3) this plan mirrors
- [visualization-phase6-implementation-plan.md](./visualization-phase6-implementation-plan.md) — the chart-chrome resolver (`VSChartChromeDefaults`) and the Phase 6A title-chrome deferral this phase depends on
- [visualization-palette-swatches.html](./visualization-palette-swatches.html) — palette source of truth; add a "KPI / Control Chrome Tokens" group for Part B, coordinated with the table-structure and chart-chrome neutrals