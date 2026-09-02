# Chart Interior Dark Chrome — Design

**Date:** 2026-09-02
**Verified against:** community `viz-updates` @ `32f291300`, which is `HEAD`. Every file and line
citation below was read at that commit.
**Roadmap entry:** "Chart interior dark palette", ranked #2 in
[chart-card-roadmap.md](./chart-card-roadmap.md)'s 2026-09-02 re-derivation, and listed in
`chart-card-design3/Chart card dark values - ticket.md` as item 3 — "the one piece of this ticket that's
a real decision rather than a verification."
**Precedent:** [2026-09-01-seeded-chrome-migration-group1-design.md](./2026-09-01-seeded-chrome-migration-group1-design.md)
and [-group2-design.md](./2026-09-01-seeded-chrome-migration-group2-design.md). Group 1's "What the
implementation found" is required reading: its central lesson — enumerate every format path the type
*renders*, not every path the resolver was called from — is what §3 below is about.
**Decides:** which chart-interior values take a dark treatment, that they are **seeded at creation
rather than resolved at render**, why the target surface is excluded entirely, and what the seed makes
deletable.

---

## Scope

**In: six values, all on `PlotDescriptor`, all seeded.**

| # | Value | Field | Today |
|---|---|---|---|
| 1 | Data-label ink | `PlotDescriptor.fmt` DEFAULT tier | `#4b4b4b`, written unconditionally |
| 2 | Plot gridline, x | `xGridColor` | `#eeeeee` |
| 3 | Plot gridline, y | `yGridColor` | `#eeeeee` |
| 4 | Facet lines | `facetColor` | `#eeeeee` |
| 5 | Scatter-matrix diagonal | `diagonalColor` | `#eeeeee` |
| 6 | Quadrant lines | `quadrantColor` | `#eeeeee` |

**Out: the whole target surface, line and label together, for two different reasons.** Recorded as one
unit rather than half-shipped:

- **`GraphTarget.lineColor` (`:783`) is persisted but UNTIERED** — a plain `Color`, not a
  `CompositeValue`, initialised to `GDefaults.DEFAULT_TARGET_LINE_COLOR` (`#afafad`). A seeded write is
  indistinguishable from an author's, so **Revert could not reverse it.** This is the fourth instance of
  the class [the geometry decisions](./chart-card-geometry-decisions.md) §4.3 already names — `padding`,
  `LegendsDescriptor.gap`, `AxisDescriptor.fixedWidth`/`fixedHeight`. The precedent for doing it anyway
  exists (`isUserBarCornerRadius()` is a provenance flag standing in for a missing tier,
  `ChartVSAssemblyInfo:141-145`), so this is deferrable rather than impossible; it just needs a fifth
  flag and its own argument.
- **The target LABEL needs a `VizContext` at target creation, and there are five creation sites.**
  `GraphTarget`'s constructor calls the no-arg `initDefaultFormat()` (`:66`), i.e. `LEGACY`; the
  ctx-taking overload is only reached from the render path (§3). Threading creation means
  `ChartPropertyService:616` (the target dialog), `ChartProcessor:494` and `:533` (the **public script
  API**, `addTargetLine` / `addTargetBand` — see `shell-dsl.md` on script-facing signatures),
  `SyncChartHandler:150` (wizard clone) and `GraphTarget:96` (`instantiateFromXML`). Seeding only the
  targets that exist when the hook runs is **worse than not seeding**: add a target to a modern-dark
  chart afterwards and its label stays legacy permanently, because nothing re-seeds it.

**Out: everything built per-render.** `LineForm:738`, `ParaboxLineGeometry:74`, `IntervalElement:873`
(the interval bridge) and `GeoShapeFrame:223`/`:255`. These are constructed per render inside
`inetsoft.graph`, which holds **zero `VizContext` references** by deliberate boundary (verified: the
package has none). There is no persisted value to seed, so they are skipped rather than resolved —
resolving them would be the read-time mechanism this design exists to avoid.

**Out: the continuous ramps.** `BluesColorFrame` has no dark handling and is the real default for a
measure on Colour (per phase 9C item 2's withdrawal). A sequential ramp cannot be dark-adapted by
substitution: it has to keep its perceptual ordering against a dark ground, and its deep-blue end
currently collapses into `#252428`. That is new design affecting **data encoding** rather than chrome,
and it needs a value sign-off none of the six below do.

---

## 1. What the roadmap said, and what is true

The roadmap has carried this item as "the last visible hole in a dark mode that is otherwise complete…
`GDefaults` has no dark branch to reconcile against, so this is design work" and costed it **M-L, design
first**. **Both halves of that are wrong, and the item is smaller and more concrete than it looked.**

`GDefaults` having no dark branch is true — the package contains no occurrence of "dark", "modern" or
`VizContext` — and **irrelevant**, because two resolvers intercept before its constants matter:

| Surface | Dark value | Where | Cohesive with |
|---|---|---|---|
| gridlines, axis lines, legend borders | `#3A383D` | `VSChartChromeDefaults:148` | the table gridline, per its own comment |
| axis + legend labels | `#CAC4D0` | `:149` | `VSTitleChromeDefaults.TITLE_FG_DARK` |
| axis + legend titles | `#E6E0E9` | `:150` | `VSObjectChromeDefaults`' dark text |
| legend panel background | `#252428` | `:152` | `CARD_BG_DARK` |
| categorical series palette | `DARK_HEAD` / "Modern Dark" | `VSChartPaletteDefaults:51-59` | its own dark ramp |

So **the dark interior palette exists and is already derived from the dark chrome set.** This design
invents no colour. What it fixes is six values that were *missed* by that pass, and it reuses the two
constants above verbatim.

### 1.1 One of the six is a default-on defect, not a polish item

`yGridStyle` defaults to `StyleConstants.THIN_LINE` (`PlotDescriptor:1904-1905`, against
`xGridStyle`'s `NONE` at `:1902-1903`) — **horizontal
gridlines are drawn out of the box.** Their colour comes from `getYGridColor()`, a
`CompositeValue<Color>` constructed with `ChartLineColor.getPlotLineColor(GDefaults.DEFAULT_GRIDLINE_COLOR,
"y")` (`:1900-1901`), which is a `format.css` lookup falling back to `#eeeeee` (`ChartLineColor:56-58`,
`GDefaults:137`).

The render path takes that value **raw**: `GraphGenerator:2738` and `:2745` for x/y,
`:4005`, `:4092` and `:4201` for the facet, `:5904-5905` and `:5935-5936` for diagonal and quadrant.
**There is no render-path caller of `resolveGridlineColor` at all** — grepped across the whole of
`core/src/main`; the only callers are `ChartLinePaneModel` and two dialog models.

So on a modern-**dark** chart with default settings, horizontal gridlines draw near-white `#eeeeee` on a
`#252428` card.

### 1.2 And the composer already disagrees with the canvas

`ChartLinePaneModel` resolves these same values through `VSChartChromeDefaults.resolveGridlineColor(…,
ctx)` at **ten** sites (`:70`, `:151`, `:157`, `:166`, `:172`, `:185`, `:228`, `:233`, `:242`, `:247`).
So the Chart Line pane has been *displaying* `#E8E5DE` / `#3A383D` while the canvas drew `#eeeeee`.

**This is the `FormatPainterService` lesson inverted.** Group 2's design records that case: the pane
showed the stored near-black while the canvas, viewer and export all drew light text. Here the pane shows
modern and the canvas shows legacy. **It is live in light mode too** — `#eeeeee` against `#E8E5DE` differs by
(6, 9, 16) per channel — a barely perceptible warm shift on a near-white hairline, which is exactly why it
shipped unnoticed. Dark mode does not
introduce the inconsistency; it makes a pre-existing one violently visible.

Diagonal and quadrant are the counter-case worth stating: **neither** the render path nor the dialog
resolves them, so they are internally consistent today and merely have no modern or dark treatment. They
are in scope because they are the same field type on the same descriptor taking the same value — not
because anything is broken.

### 1.3 The tier already exists, for the third time on this branch

All five line colours are `CompositeValue<Color>` (`PlotDescriptor:1898-1915`), and the data-label ink
lives in a `CompositeTextFormat` with three tiers — CSS (`:215`), DEFAULT (`:229`) and user-defined
(`:243`). **There is no scale or mechanism to introduce**, which is the third time the roadmap's
"check for an existing tier before costing anything from that set" has held: it held for §04's gaps, for
the title lane, and it holds here.

Two consequences follow directly and are not re-derived below:

- **No customization guards.** A USER or CSS value outranks DEFAULT by construction, so nothing tests
  whether an author has set a gridline colour.
- **No provenance flags.** Unlike `barCornerRadius` and `smoothLines`, which needed
  `isUserBarCornerRadius()` / `isUserSmoothLines()` because the fields are untiered, all six values here
  have a real tier to write into. This is the reason the target line is out and these six are in.

---

## 2. The seed, and where it goes

**Seam: `ChartVSAssemblyInfo.seedChromeDefaults(VizContext ctx)` (`:105`).** It already resolves a
`PlotDescriptor` handle at `:138` for `barCornerRadius` and `smoothLines`, so all six values seed from a
handle that already exists, in the hook every route already calls — creation, `VizModernizeUtil.modernize`,
`VizModernizeUtil.revert`, and `AbstractVSAssembly.parseState`'s re-seed on bookmark restore.

**Both branches always write.** `revert` clears the mark and re-runs this same hook, so a value written on
the modern branch and not on the legacy one is stranded. The legacy branch writes the values the fields
hold today, making a gate-off creation value-identical rather than merely untouched — the pattern
`:151-163` already uses and comments.

### 2.1 The value mapping

| Value | Modern light | Modern dark | Legacy (both) | Source |
|---|---|---|---|---|
| Data-label ink | `#35342F` | `#E6E0E9` | `#4b4b4b` | `VSChartChromeDefaults.titleColor(ctx)` / `GDefaults.DEFAULT_TEXT_COLOR` |
| x + y gridline, facet, diagonal, quadrant | `#E8E5DE` | `#3A383D` | `#eeeeee` | `VSChartChromeDefaults.gridlineColor(ctx)` / the `ChartLineColor` lookup each field is constructed with |

**Two principles behind the first row, recorded so they are not re-litigated.**

*Where light mode uses one value, dark uses one value.* Data labels take the **title** tier, not the label
tier, because `#4b4b4b` sits nearer `TITLE #35342F` than `LABEL #6A685F` — per-channel deltas
(22, 23, 28) against (31, 29, 20), so nearer on two channels of three and by sum — and because data
labels are primary content rather than chrome — they are the
numbers, not the scaffolding. The existing dark pairing `#E6E0E9` on `#252428` is the one already used for
axis titles, so this introduces no new contrast relationship.

*The legacy branch restores the field's constructed value, not a constant.* For the five line colours that
means re-running the same `ChartLineColor.getPlotLineColor(...)` lookup the constructor uses, **not**
writing `#eeeeee` literally — otherwise a customer whose `format.css` sets `ChartPlotLine` would have
their value replaced by a Revert. This mirrors group 2's finding that a legacy branch has to reproduce a
gate-off creation exactly, and it is the one place in this design where the legacy branch is not a
constant.

---

## 3. The clobber — `initDefaultFormat` runs at every render

**This is the load-bearing mechanical detail, and it is why seeding alone is not enough.**

`PlotDescriptor.initDefaultFormat(VizContext ctx)` (`:71-76`) sets
`fmt.getDefaultFormat().setColor(GDefaults.DEFAULT_TEXT_COLOR)` **unconditionally**, then branches on
`ctx` for the font only. `VGraphPair:1097` calls it on **every render**. So a seeded data-label ink is
overwritten on the first repaint unless that write goes.

**The unconditional colour write is removed.** It is safe because `TextSpec.getColor()` returns
`GDefaults.DEFAULT_TEXT_COLOR` when the colour is null (`TextSpec:56`), so the report path and any
`PlotDescriptor` that never passes through the seed still resolve the same ink they do now. **That
fallback must be verified at each render path rather than assumed** — group 1's Critical came from
exactly this class of assumption, where a resolver's call sites and a type's rendered format paths were
not the same set.

**`copyDefaultFormat` does not clobber it.** `VGraphPair:1410-1415` overwrites the DEFAULT tier's colour
only when the object format's CSS or user tier defines a foreground, which is an author's object-level
choice correctly winning over a seeded default. No change needed there.

The five line colours have no equivalent clobber: nothing re-initialises them per render, which is why
they are a pure seed and the data-label ink is a seed plus a deletion.

---

## 4. A deliberate divergence, recorded because it will look like an inconsistency

After this change, the chart interior contains **two mechanisms side by side**, and a reader will see them
two lines apart in `VGraphPair`:

- The six values here are **seeded at creation**.
- The axis and legend label/title colours are **resolved at render**, via `initDefaultFormat(ctx)` plus
  the per-column loop at `VGraphPair:1359-1381`, whose own comment says so: *"initDefaultFormat writes the
  context's own value on both branches, so a cleared mark restores the legacy label color here on the
  next render."*

**`VSChartChromeDefaults` was never part of the read-time migration.** The roadmap's migration node lists
five resolvers — `VSTitleChromeDefaults`, `VSCalendarChromeDefaults`,
`VSObjectChromeDefaults.applyDarkForeground`, `VSOutputChromeDefaults` and
`VSObjectChromeDefaults.chartPadding` — and the chart chrome resolvers are not among them. So the chart
interior is now **the only chrome area still computing values at render**, and it carries the portability
consequence groups 1 and 2 existed to remove: export a dashboard, import it into a build without this
work, and the axis labels fall back to legacy while the gridlines seeded here render modern.

**Not fixed here, and it is the natural successor to this piece.** Migrating the chart chrome resolvers is
group 3 of the same migration, not a detail of this design: it touches every `initDefaultFormat(ctx)` call
in `VGraphPair` (`:1027` through `:1390`, spanning the legends, the four aesthetic descriptors, the four
axis descriptors, the per-column label loop at `:1359-1381` and the per-ref calls at `:1387`/`:1390`) plus
`RadarGraphGenerator`. It is recorded so the
divergence is a known state rather than a discovered one.

---

## 5. What this deletes

Once the six values are seeded, `ChartLinePaneModel`'s **ten** `resolveGridlineColor(…, ctx)` calls become
redundant: the dialog and the render read the same stored value, so the WYSIWYG break in §1.2 closes **by
construction** rather than by keeping two mechanisms in agreement forever. They are deleted rather than
left as a harmless double-resolution — a resolver that agrees with the stored value today is a resolver
that silently disagrees the first time either side changes.

`resolveGridlineColor` itself **stays**: `AxisPropertyDialogModel` and `LegendFormatDialogModel` reach
`resolveAxisLineColor` and `resolveLegendBorderColor`, which share its constants, and the axis-line render
path at `GraphGenerator:2366`/`:2601` and `RadarGraphGenerator:204` still calls the axis variant.

---

## 6. Light mode changes too, and that is intended

Seeding is not a dark-only change. Modern **light** gridlines move `#eeeeee` → `#E8E5DE`, and modern light
data labels `#4b4b4b` → `#35342F`. Approved deliberately rather than accepted as a side effect, for two
reasons: the Chart Line pane has been claiming `#E8E5DE` for these values all along, so the canvas is the
half that is wrong; and the Revert contract requires both branches write regardless, so a dark-only seed
would mean the modern branch writing the legacy value in light mode — encoding the inconsistency instead of
closing it.

Unmarked charts are unaffected in both modes. That is the assertion the tests below pin hardest.

---

## 7. Testing

- **Per-value seed tests**, on the `SeedChromeDefaultsTest` pattern: each of the six across
  `MODERN_LIGHT`, `MODERN_DARK` and unmarked, asserting the unmarked case is **byte-identical** to
  pre-change.
- **Modernize → Revert round trip** proving the legacy branch restores the constructed value, including
  the `format.css` case from §2.1 — a `ChartPlotLine` CSS rule must survive a Revert rather than being
  replaced by `#eeeeee`.
- **A USER-tier guard**: an author-set gridline colour survives both Modernize and Revert, since a USER
  value outranks DEFAULT.
- **The clobber regression**: render twice and assert the data-label ink is stable across repaints. Without
  this, §3's deletion silently regresses the moment anything re-adds an unconditional write.
- **Bookmark restore**, since `parseState` re-seeds: a bookmark taken before a Revert must not reinstate
  modern gridlines on an unmarked chart.
- **Export parity** across PDF, PNG, SVG and Excel — all six are server-painted. Group 2's Excel doctrine
  applies: `paintsPageBackground()` is false for Excel, whose unfilled white cells would take dark ink
  invisibly, so the dark branch must not reach the Excel exporters for the data-label ink. The five line
  colours are chart-internal and unaffected by that distinction.
- **`CalendarPropertyDialogServiceTest`'s lesson**: any new test class needs `@Tag("core")` or surefire's
  hardcoded group filter means it never runs.

---

## 8. Order

W1 — the five line colours. Independent of everything, pure seed, no deletion.
W2 — the data-label ink, with §3's removal of the unconditional write. Must be one commit: seeding without
the removal is inert, and the removal without the seed loses the ink.
W3 — delete `ChartLinePaneModel`'s ten resolver calls. Follows W1, since it is only safe once the stored
value is the seeded one.

W1 and W2 are independent of each other; W3 needs W1.

---

## What this closes, and what it does not

**Closes:** a default-on dark legibility defect (near-white gridlines on a dark card, `yGridStyle`
defaulting to `THIN_LINE`); a composer/canvas WYSIWYG disagreement live in **both** modes; and the
roadmap's "chart interior dark palette" item, which turns out to need no new colour and no design pass.

**Leaves open**, each recorded above with its reason rather than forgotten:

- **The target surface**, line and label — untiered field, and five creation sites including the public
  script API (Scope).
- **Everything built per-render** — form lines, the parabox line, the interval bridge, map shapes. No
  persisted value, and `inetsoft.graph` holds no `VizContext` (Scope).
- **The continuous ramps** — real design, affects data encoding, needs a sign-off (Scope).
- **The chart chrome resolvers' own read-time migration** — group 3, and now the only chrome area left
  computing at render (§4).
