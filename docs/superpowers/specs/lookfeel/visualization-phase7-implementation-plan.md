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

**Sequencing dependency — Phase 6A IS BUILT (resolved, verified 2026-07-16).** An earlier revision of
this plan recorded Phase 6A as unbuilt; that is now stale. `VSTitleChromeDefaults` landed in commit
`16ec7af83` (before Part A `f8f3912b0`) and modernizes the shared **object title bar** — the chrome
wrapping every KPI and control. It is wired across every model-build seam (`web/viewsheet/model/*`)
and every export seam (`report/io/viewsheet/*`). Consequence: the half-themed-KPI risk is gone — the
title bar above a KPI value is already modern, and Part B only needs to modernize the *value body*
below it, coordinating the same gate + palette (which 6A already fixed — see the palette table in
Part B below). Part B is therefore **unblocked**, and 6A is the concrete template it mirrors. The
recommended order collapses to: **Part A (done) → Phase 7 Part B (this section)**.

## Decisions (proposed — resolve with initiative owner before Part B)

### D1 — Phase 7 first pass = Part A (CSS, zero export); Part B is a gated follow-up sub-plan → **RECOMMEND YES**

Exactly Phases 5 (D1) and 6 (D2). Part A (embedded-control density/state repoints) is broad,
gate-off byte-identical, no server change, no export change — shipped. Part B changes `VSFormat`
defaults, is export-visible, and reflows saved default-formatted KPI widgets on enable, so it is a
gated follow-up with PDF/PNG/SVG/Excel parity checks. Its Phase 6A coordination dependency is now
satisfied (6A built), and it is grounded in the Part B section below. Kept the low-risk 80% shippable
first.

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

**Implementation status (as executed 2026-07-16, `viz-updates`, community CSS only).** The
parity-safe slice actually shipped is even thinner than proposed — A1 colors + A4 only:

- **A1 (shipped, colors only).** `.dropdown-item` hover/selected repointed to
  `--inet-viz-hover-bg` / `--inet-viz-selected-bg` / `--inet-viz-selected-text`, all gate-off
  byte-identical, crossing the body-portal via the custom-property cascade (NOT `:host-context` —
  the dropdown is appended to `document.body` by `fixed-dropdown.service`, so a `:host-context`
  ancestor selector cannot reach it). **A1-density deferred** (token `:root` ≠ current padding, and
  CDK `[itemSize]="35"` pins row height).
- **A1 real-world limitation (native `<select>`).** `.dropdown-item` is only rendered when
  `model.labels.length > 500` (`vs-combo-box.component.html:71`); the common `≤ 500` path is a
  native `<select>`/`<option>` (`:60-69`) whose hover/selected highlight is **browser/OS-owned and
  not CSS-themeable** (`accent-color` does not cover `<select>` options; `appearance: base-select`
  is Chromium-only). So A1 is effectively inert for typical combos and reaches only large
  (>500-option) combos. Making all combos adopt the modern state would require always using the
  custom dropdown — an interaction/accessibility change (keyboard nav, ARIA, dynamic height vs the
  fixed `min-height:350px`), out of scope for a theming pass and not appropriate to couple to a
  visual gate. Treat native `<select>` option styling as a native-control boundary, analogous to
  the server-render boundary.
- **A2 deferred (no CSS anchor).** combo/spinner/text-input all render at `height:100%` of their
  server-sized assembly box; there is no CSS control-height to repoint. Embedded-control density is
  inherently server-side (`VSDensityDefaults`).
- **A3 deferred (no live-DOM consumer).** `--inet-viz-filtered-bg` still has zero consumers: the
  selection item state is inline `cellFormat.background` from server `VSFormat`
  (`selection-list-cell.component.html:21`) and the range-slider band is a PNG asset. No CSS overlay
  layer exists to attach the token to without either the Part B server seam or a new
  double-rendering overlay.
- **A4 (shipped, gate-scoped).** `:host-context(.viz-modern)` block in `vs-slider.component.scss`
  repoints `--slider-*` to warm-neutrals (`#E8E5DE` inactive / `#C8C2B7` active / `#6A685F`
  handle+tick+label). Live view only. **Export mismatch is expected** — see B4.

**B4 — Slider server-render counterpart (export parity for A4).** A4 modernized only the live-view
CSS; the exported/server-image slider (`VSSlider.java` hardcoded constants) stays legacy. This is now
a Part B item — see **B4** in the Part B section below for the grounded seams and gating.

### Part B — Server-side KPI/control chrome defaults (gated resolver) — GROUNDED, decisions resolved (2026-07-16)

Export-affecting server work. All seams below are **code-verified on `viz-updates`**. Two corrections
to the earlier draft, now folded in:

- **Phase 6A is built** (`VSTitleChromeDefaults`, commit `16ec7af83`) — Part B is unblocked and mirrors
  it directly (same file shape, gate, and palette).
- **Mechanism is NOT a `setDefaultFormat` change.** Phase 6A established the canonical pattern Part B
  follows: a static `applyModernDefaults(VSCompositeFormat)` that writes the modern neutral to the
  **DEFAULT tier of a *clone* at read time**, applied only when the USER tier
  (`getUserDefinedFormat().isXxxValueDefined()`) and CSS tier (`getCSSFormat().isXxxValueDefined()`)
  have not set the value, invoked at **every model-build seam and every export seam** (same function
  both paths → live and export agree, no persisted-format mutation). Do not touch `setDefaultFormat`.

**Gate (`VSOutputChromeDefaults`, `uql/viewsheet/internal/`, mirrors `VSTitleChromeDefaults`).**
`final` class, private ctor, all static. **DECISION D-B1 (resolved): reuse the existing
`viewsheet.modernObjectChrome` sub-gate** — one admin toggle for all object chrome (title + value +
slider export), no new property:
`isModern()` = `VSDensityDefaults.isModern() && !"false".equals(SreeEnv.getProperty("viewsheet.modernObjectChrome", false, true))`.
Expose `valueForeground()`, `labelForeground()`, `borderColor()` (+ slider chrome accessors for B4),
and an `applyModernDefaults(VSCompositeFormat)` / `…InPlace(…)` pair matching 6A.

**Palette — coordinated, already in code, reuse verbatim** (no new colors, no swatch-file change
needed):

| Role | Hex | Source constant |
|---|---|---|
| Primary value foreground | `#35342F` | `VSChartChromeDefaults.TITLE` |
| Label / secondary foreground | `#6A685F` | `VSChartChromeDefaults.LABEL` / `VSTitleChromeDefaults.TITLE_FG` |
| KPI / control border | `#D9D5CC` | `VSTitleChromeDefaults.TITLE_BORDER` |
| Slider inactive/active/handle | `#E8E5DE` / `#C8C2B7` / `#6A685F` | matches Part A A4 CSS |

**B4 — Slider export parity (DONE 2026-07-16).** Live view was modernized by Part A A4; the
server-painted export was still legacy. Implemented: new `VSOutputChromeDefaults`
(`uql/viewsheet/internal/`, reuses the `viewsheet.modernObjectChrome` gate) exposes
`sliderInactiveTrack/ActiveTrack/Handle/Tick()`, each returning the legacy VSSlider color gate-off and
the warm-neutral (`#E8E5DE`/`#C8C2B7`/`#6A685F`) gate-on. `VSSlider.paintComponent` now reads those
accessors instead of its four hardcoded constants; all exporters draw via `VSSlider`, so one change
covers PDF/SVG/HTML/CSV/Excel. Pure chrome, not user-formattable → gate-off byte-identical. Unit test
`VSOutputChromeDefaultsTest` (3 tests) green. **`VSTimeSlider` (range slider) is out of scope**: its
track/handle are **theme images** (`getTheme().getImage("widget|SliderBase", …)`), not color constants
(only the tick is `0x888888`), and it is asset-themed on both live and export — so there is no
live/export mismatch to fix; range-slider modernization is an asset pass, deferred like the gauge face.
Remaining: manual PDF/PNG/SVG/Excel parity spot-check (gate-off unchanged, gate-on warm-neutral).

**B1 — KPI text/output value defaults (DONE 2026-07-16).** Value format is the object
`VSCompositeFormat` seeded on the DEFAULT tier at `TextVSAssemblyInfo.setDefaultFormat` (fg `0x2b2b2b`,
BOLD 11, border `0xDADADA`). Per **D-B2**, modernized **foreground + border only; weight/size
unchanged**. Implemented: `VSOutputChromeDefaults.applyModernDefaults(VSCompositeFormat)` /
`…InPlace(…)` substitute fg → `#35342F` and border → `#D9D5CC` on the DEFAULT tier of a clone, only when
neither the USER tier nor the CSS tier has set them (so a user format and a `format.css` class still
win). Wired at:
- **Live:** `VSTextModel.createFormatModel` wraps the object format with `applyModernDefaults`.
- **Export:** `AbstractVSExporter.getTextFormat` calls `applyModernDefaultsInPlace(fmt)` on the cloned
  format **before** the highlight override (which writes the `HighlightGroup` emphasis to the USER
  tier), so highlights still win (D6 preserved).

Unit test `VSOutputChromeDefaultsTest` extended (7 tests total, green): palette values, bare-default →
modern fg+border, user fg/border still win, gate-off no-op.

**Design-time WYSIWYG (composer format picker).** The Phase 3 rule requires the composer to match the
viewer. The canvas already does (it renders from the same `VSTextModel` model). The **format panel
picker** is populated separately by `FormatPainterService` from the raw assembly format
(`:204`/`:383`/`:366`) and does not go through the model — so without help it shows the legacy default
while the canvas shows modern. Fixed by applying `VSOutputChromeDefaults.applyModernDefaultsInPlace` to
the cloned display format for `TextVSAssembly` (`FormatPainterService` after the object-format clone).
This mirrors how the table header already shows its modern structure colors in the picker (Phase 5
bakes them into the lens format the picker reads). **Full WYSIWYG:** the same `FormatPainterService`
seam also applies `VSTitleChromeDefaults.applyModernDefaultsInPlace` when the selected `dataPath` is
`TITLE`, so object title-bar pickers show the modern title bg/fg matching the 6A render. **Safe against
persistence:** the apply-back path is change-detection against the echoed `origFormat` (`:899`), and
both current and orig now carry the same modern value, so an unchanged panel never persists it —
gate-off stays byte-identical. (Remaining picker gap: chart *internal* chrome — gridline/axis/legend
colors from Phase 6 — is edited via the chart-specific format dialogs / `ChartDescriptor`, a separate
subsystem from `FormatPainterService`, not covered here.)

Remaining: manual live + PDF/PNG/SVG/Excel parity spot-check on a Text KPI (gate-off unchanged; gate-on
warm value + border in canvas, picker, and export; a user format, `format.css` class, and highlight
each still override).

**B2 — Range-output / gauge (mostly defer).** Gauge is a server image via `VSGauge` (face `10120` = a
themed asset → **defer**, D8). Assess whether the range-output painter carries `VSSlider`-style
hardcoded constants; if so, fold into B4-style gating, else defer. A neutral gauge value-fill default
is optional/low-priority.

**B3 — Input control-box defaults (defer).** Exported control box rarely carries emphasis; no seam
change this pass.

**Why a Java resolver, not `format.css`:** `CSSDictionary` has no `viewsheet.modernVisualization`
SreeEnv gate (it is file/theme-driven per org), so it cannot carry the programmatic per-org gate. The
`VSOutputChromeDefaults` resolver rides on top of the CSS dictionary — a customer's `format.css` output
class still wins as the CSS tier, checked explicitly by `applyModernDefaults`.

**Org gate on the export/scheduled thread — CONFIRMED.** 6A already applies `applyModernDefaults` on
the export/report path (`AbstractVSExporter`, `ExportUtil`, `VsToReportConverter`), proving the
org-scoped `SreeEnv` gate resolves there. Part B inherits this; no thread-context work.

**Sequencing:** **B4 → B1 → (assess B2 / defer B3).** B4 first is self-contained and validates the
server-chrome gate end-to-end on the export path at minimal risk.

**Risk / validation:** export-visible. Gate-off byte-identical across live + PDF/PNG/SVG/Excel for a
Text KPI, a slider, and a gauge; gate-on shows modern value fg/border + slider neutrals in every export
format, with title bar (6A) and value body (B1) reading as one system. Confirm a user `VSFormat`, a
`format.css` output class, and a `HighlightGroup` each still override the modern default. Spot-check a
scheduled export for org-scope resolution. B1 is the highest-risk item; B4 is low-risk.

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
6. **Coordination check (Part B):** modernize a KPI whose title bar is also modern (Phase 6A is
   built) and confirm title + value read as one system, not half-themed.
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
4. **Object title-bar modernization (Phase 6A)** — DONE (`VSTitleChromeDefaults`, commit `16ec7af83`);
   was the coordination dependency for Part B, now satisfied. Part B reuses its gate + palette.
5. **Slider server-render counterpart (B4)** — promoted into Part B (grounded); it is the recommended
   first Part B increment. See Part B / B4.
6. **Gauge face modernization** — a themed asset + descriptor pass, distinct from the `VSFormat` seam
   (D8); Part B may seed a neutral value-fill but the modern face is deferred.
7. **Input control-box server defaults (Part B / B3)** — low value; controls rarely carry exported
   box emphasis; likely defer.
8. **Dark-mode KPI/control chrome** — Part B is light-first; dark variants ride the initiative's dark
   pass (as with Phase 5/6).
9. **EM Material chrome** — separate build, admin, never exported (D7).

## Branching (per CLAUDE.md)

Part A is community-only CSS (SCSS in the `community/` submodule), committed on `viz-updates`
(`f8f3912b0`); enterprise submodule-pointer bump only at PR time. Part B adds core Java (the
`VSOutputChromeDefaults` resolver + `applyModernDefaults` at the Text model/export seams and the
`VSSlider`/`VSTimeSlider` painters) — same `viz-updates` branch, alongside the landed Phase 6A
server-chrome work. Nothing on `main` or a `v1.0.x`/`v1.1.x` release branch; nothing pushed/PR'd
without explicit approval.

## Related

- [visualization-implementation-roadmap.md](./visualization-implementation-roadmap.md) — Phase 7 tasks (KPI hierarchy, embedded controls), Phase 8 dependency note, the modern-mode selection requirement
- [visualization-design-spec.md](./visualization-design-spec.md) — KPI And Summary Widgets, Embedded Filters And Controls, Density (local-override rule), Rendering And Theming Architecture (render-location boundary)
- [visualization-phase0-audit.md](./visualization-phase0-audit.md) — the render-location boundary (Task 4) and server-owned defaults (Task 5) this phase rests on
- [visualization-phase1-contract.md](./visualization-phase1-contract.md) — density + state token `:root` defaults (parity baselines); `--inet-viz-control-height`, `--inet-viz-filtered-bg`
- [visualization-phase3-implementation-plan.md](./visualization-phase3-implementation-plan.md) — the gated `VSDensityDefaults` resolver + selection-list `getEffectiveCellHeight` density (embedded-filter data surfaces)
- [visualization-phase5-implementation-plan.md](./visualization-phase5-implementation-plan.md) — the Parts-by-render-location structure, gated server resolver + `VSFormat`-default seam, state-on-data-surfaces-only reversal (D2), sort-glyph parity snag (D3) this plan mirrors
- [visualization-phase6-implementation-plan.md](./visualization-phase6-implementation-plan.md) — the chart-chrome resolver (`VSChartChromeDefaults`) and the now-built Phase 6A title-chrome (`VSTitleChromeDefaults`) this phase's Part B mirrors
- [visualization-palette-swatches.html](./visualization-palette-swatches.html) — palette source of truth; Part B reuses the existing table-structure/chart-chrome/title-chrome neutrals verbatim (value `#35342F`, label `#6A685F`, border `#D9D5CC`), so no new swatch group is required