# Chart Card Open Items — Decisions

**Date:** 2026-08-11
**Verified against:** community `viz-updates` @ `a038a30b5`
**Decides:** the title band fill, dark mode for the chart card's browser-DOM surfaces, and the range
slider's render divergence
**Raised by:** [chart-card-source-doc-corrections.md](./chart-card-source-doc-corrections.md) §3.1, §4.1,
and `chart-card-design2/Visualization Widget Spec.dc.html` §08

## Why this file exists

Three questions had no answer anywhere: one open since 2026-08-05, one mis-scoped, one that the sibling
project explicitly declined to settle and handed to whoever opens its ticket. This records the answers.

It is deliberately not one of the places they were raised. `chart-card-design2/` is regenerated wholesale
by an external sync — that is how v2 replaced v1 — so anything written there survives at most one pass.
`chart-card-source-doc-corrections.md` is an audit of that folder against the code, rewritten each pass
for the same reason. Both are derived artifacts. A decision needs somewhere that is not.

Each decision below states the question, the answer, what it costs, and what it obliges someone else to
know. Implementation plans are written separately, one per decision, under `docs/superpowers/plans/`.

---

## 1. The title band is unfilled — a hairline rule only

**The question.** `Chart Card Spec.html` §01: *"Unfilled — a hairline rule only. **Never a filled
band.**"* §07 lists the title band fill `#EEEDE8` as dropped. `VSTitleChromeDefaults` ships
`TITLE_BG = 0xF1EFEA` (`:143`), documented as *"equal to the table header background so chrome reads as
one system."* Two deliberate decisions, in direct contradiction, unresolved since the 2026-08-05 audit
first recorded it.

**The decision. Unfilled, per the chart card spec.** The rendered appearance in
`chart-card-design2/Chart Card Spec.html` is authoritative for the exact treatment — that file is a
bundled page whose prose cannot be extracted reliably, so read the rendering rather than re-deriving the
rule from text. `TITLE_BG` stops being applied. `TITLE_BORDER` (`0xD9D5CC`, the rule) and `TITLE_FG`
(`0x6A685F`) stay: the hairline *is* the treatment, so the border is the part that carries it.

**Where it lands.** `core/src/main/java/inetsoft/uql/viewsheet/internal/VSTitleChromeDefaults.java` —
the background resolver at `:58`, its application at `:116`, and the constants at `:143`/`:148`. The dark
variant `TITLE_BG_DARK = 0x2D2B30` goes the same way; dark is a modifier, not a separate decision.

**Server-rendered, so export-affecting.** The title bar is a format default, read by both the browser and
the Java painters, so PDF, PNG, Excel and scheduled reports all change with it. This is not a CSS edit.

### Three consequences that reach past the chart card

**The title bar stops matching the table header band.** `VSTitleChromeDefaults` chose `0xF1EFEA`
deliberately for that equality, and `Visualization Widget Spec.dc.html` §05 endorses it on exactly that
basis — *"deliberately equal to the table header so chrome reads as one system."* This decision breaks
that equality across all eight titled assembly types, and it is being taken by the chart card track. The
sibling project should see it and object if they want to; it is stated plainly here for that purpose.

**One of the two supports for the 6px card radius disappears.** The widget spec §03 argues the seeded
radius down from 12px to 6px partly from the fill: *"a 12px radius consumes 12 of the lane's 26px at each
end, so the band's fill pinches away from the corners and reads as a lozenge rather than the top of a
card."* With no fill there is no pinch and no lozenge. The 6px conclusion may still hold on the scale
argument alone — 12px is off a scale that tops out at 6 — but whoever implements §03 should know it now
rests on one leg rather than two.

**The anchored strip becomes the only fill in the lane.** The strip carries its own pill background
(`--inet-shell-surface-default`, `mini-toolbar.component.scss:31`). Against a filled band it read as a
raised control; against an unfilled one it is the sole filled object in an otherwise open lane. Worth
looking at on a real dashboard before the change is considered done, not because the decision is in doubt
but because the strip's treatment may want revisiting once its background changes.

**Closes** the corrections doc's disposition entry for old §3.1.

---

## 2. Dark mode covers the chart card's four browser-DOM surfaces

**The question.** `Open items - handoff.md` asked whether dark is in scope for the chart pilot or
explicitly deferred, on the premise that *"this chart card spec has no dark values at all."* That premise
was already false: `3e7e52626` (2026-07-28, Phase 9B) gave every **server-rendered** visualization
surface a dark treatment, the chart included — `VSChartChromeDefaults` carries `GRIDLINE_DARK 0x3A383D`,
`LABEL_DARK 0xCAC4D0`, `TITLE_DARK 0xE6E0E9`; `legendBackground()` resolves `LEGEND_BG_DARK 0x252428`;
`VSTitleChromeDefaults` carries the dark title band; `VSChartPaletteDefaults` covers series colour.

What 9B scoped out is the browser DOM, and that is where the chart card's own contribution lives.

**The decision. In scope — implement it.** Four surfaces:

| Surface | Why it stays light today |
|---|---|
| The anchored strip (§02, §03) | `background-color: var(--inet-shell-surface-default)`, `mini-toolbar.component.scss:31` — one definition, no dark variant |
| All tooltips, including the CARD ramp (§07) | `plain-tooltip-surface` binds `--inet-text-color`, `--inet-dialog-bg-color`, `--inet-default-border-color`, `--inet-shadow-low` (`_directives.scss:215-229`) |
| The chart selection fill | `.viz-modern .chart-object-canvas` binds `--inet-focus-ring-color` and `--inet-primary-color` (`_themeable.scss:1410-1413`) |
| Nav bar, Resize Plot sliders, annotation selection border | No `.viz-dark` rule in any of them |

**Why this is small.** Live view only. No server resolver, no export path, no parity pass — the entire
change is CSS behind a body class that already exists and is already toggled in the portal, composer and
standalone viewer.

**The one real choice for the implementer.** `.viz-dark` today redefines only `--inet-viz-*` tokens
(`_viz-tokens.scss:143-159`), plus three named exceptions outside that block: `vs-slider`, the
empty-image placeholder, and the table header sort button (`_themeable.scss:1269`). **No shell neutral
flips.** So these four surfaces can either take their own `.viz-dark` rules, following those three
precedents, or the shell neutrals they bind can be made dark-aware. The first is narrower and matches
what 9B did; the second is a larger change with reach far beyond the chart. Take the first unless
something forces otherwise.

**One nuance, not a blocker.** The selection fill is `--inet-focus-ring-color`, primary at 28%. Primary
is a brand accent, so it is not *wrong* in dark — but 28% was calibrated as a wash over a light plot.
Check it over a dark plot; a dark-specific alpha may be needed even though the hue does not change.

**Closes** the corrections doc's §4.1.

---

## 3. The range slider's export painter is redrawn with the CSS control

**The question**, posed by `Visualization Widget Spec.dc.html` §08 and left open by name: its §05 redraws
the browser control in CSS while the painter keeps its bitmaps, so *"either the painter is redrawn with
it, or the two renderers deliberately differ and the spec says so. That belongs in the §05 ticket before
it opens, not in a parity pass afterwards."*

**The decision. Redraw the painter with it.** The §05 ticket carries both renderers and the slider's
share of the export parity pass.

### Why

**The selection band is the entire justification for the work.** §05 is explicit that the reason to do
this rather than defer it is not that the control is untokenized — it is that `.thumb-middle` *is* the
band showing the selected interval, so the slider renders selection in grey while tables and the calendar
render theirs in primary. Redrawing only the browser half means the selected interval reads primary on
screen and grey in the PDF. That does not fix the two-idiom problem §07 exists to end; it relocates it
into export.

**The slider genuinely exports.** `AbstractVSExporter.java:715` constructs `VSTimeSlider`. The divergence
would land in customer PDFs and PNGs, not in theory.

**The two renderers agree today only by asset duplication.** The browser copies three of the painter's
eight PNGs into `web/projects/portal/src/assets/default/timeslider/`. Same artwork, two locations.
Redraw one and they part — there is no shared source keeping them honest.

**The pattern already exists in this initiative.** `VSCalendar.java` applies
`VSCalendarChromeDefaults.applyModernDefaults` at `:733` and `:955`, and 9B deliberately did live model
and export painter together for both the calendar and `VSSlider`. Doing the slider the same way is
consistency, not new machinery.

**The precedent §08 cites does not cover the question.** It points at the chart's Resize Plot slider
decision as *"the precedent to copy rather than re-litigate."* That control is Angular-only with no
painter counterpart, so it is a precedent for redrawing in CSS and silent on export parity. Copy it for
the drawing; it cannot answer this.

### Scope

**Browser** — `vs-range-slider.component.scss`, 229 lines, zero `--inet-` tokens, zero `.viz-modern`,
unchanged since `e8df3491b` (2024-07-15). Three `background-image` references at `:83`, `:101`, `:118`.

**Painter** — `VSTimeSlider.java`, 225 lines. `paintComponent` at `:71`; three
`getTheme().getImage("widget|SliderBase", …)` + `drawImage` pairs at `:111-112`, `:134-136` and
`:139-144`; hardcoded `new Color(0x888888)` at `:119` and `Color.BLACK` at `:158`. The eight-state PNG
set is at `core/src/main/resources/inetsoft/report/gui/images/binding/granite/timeslider/`.

**Treatment**, unchanged from §05: track takes a flat token fill with its border from
`--inet-default-border-color` in place of literal `#bababa`; the range band takes §07's derived fill from
`--inet-primary-color`; the handle becomes a bordered circle on the control tokens and gains hover and
press, which it has never had because only the `_up` variants are referenced; `.range-slider-tip` loses
its "left as not themeable" exemption. Geometry is unchanged and outside density — 9px track, 16px
handle, the existing 30×36 `mobile-padding` for touch.

### What the decision adds to the ticket

**Colour resolves through a defaults class, not twice.** Both renderers read one source, the way the
calendar does. Reproducing the values independently in SCSS and in Java rebuilds the duplication this
decision exists to remove, in a form that is harder to see than two copies of a PNG.

**The slider's row in the export parity pass belongs to this ticket.** PDF and PNG, compact and dense —
per §08's scoping, which already lists the range slider as one of its three genuine risk points.

**Both asset sets can retire** once nothing references them — three PNGs in the browser tree, eight in
`core/`. Deletions before bindings, per the standing rule.

### Sequencing — this is not the next thing to start

The range band takes §07's derived selection fill. §07 carries that value in §03's re-seed rather than
adding a sixth resolver, and §03 is the tri-state seed mark, which is decided but **not built** — there
is no such field in `VSAssemblyInfo` and no plan in this repo that produces one. So the painter half of
this ticket sits behind two pieces of the sibling project's work that nobody has started.

The browser half could use `--inet-focus-ring-color` directly and proceed sooner. Splitting it that way
is exactly the divergence this decision was taken to prevent, so it should not be split — but the
dependency is real and someone should confirm the seed mark's status before scheduling any of it.

---

## Still open after this

- **The seed mark's status.** A release condition for `viz-updates` per the widget spec §03, decided
  there, absent from code, and now gating decision 3 as well as §04's density heights. Nothing in this
  repo tracks it. This is a question for the sibling project, not work.
- **The strip's density gating** (corrections doc §3.1) — decided by the widget spec §08 step 3, not yet
  accepted or scheduled here, and it is what actually gates the container and calendar slices.
- **The card radius 12→6 and retiring `resolveSeededCorner()`** (corrections doc §3.3) — decided,
  sequenced behind the seed mark, and see decision 1 above for what just happened to half its rationale.
- **Four cheap verifications** carried over: the 9pt→9px measurement gating the type scale, the nav bar's
  reach (maps only or any zoomable chart), the unverified dead-icon count, and which render path gauges
  and thermometers take in the live viewer.
