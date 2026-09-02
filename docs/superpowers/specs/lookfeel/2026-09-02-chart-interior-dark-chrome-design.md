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

**Correction, found only by the final whole-branch review, not by this analysis: the headline is wrong for
three of the six values.** `CSSChartStyles.apply` (`CSSChartStyles.java:145-156`) writes the modern
gridline colour into the **CSS tier** of `xGridColor`, `yGridColor` and `facetGridColor` whenever
`ctx.modern`, and `CompositeValue.get()` resolves USER > CSS > DEFAULT (`CompositeValue.java:41`), so that
CSS write already outranked the raw legacy DEFAULT this section describes, before any of this design's
work. So x/y/facet gridlines on a modern-dark chart were **already** `#3A383D`, not `#eeeeee` — the
default-on defect this section opens with was already fixed for those three. `diagonalColor` and
`quadrantColor` genuinely had no such mechanism — they are written to the CSS tier only from an explicit
`format.css` `line_diagonal_color` / `line_quadrant_color` rule (`CSSChartStyles.java:225-229`), not
unconditionally — so the defect this section describes was real for those two only.

**Why the original analysis missed it, worth recording on its own:** it grepped for
`resolveGridlineColor` callers, found none on the render path, and concluded the render therefore drew the
raw legacy value. It never asked whether a *different* mechanism substituted the modern colour before the
render read it. One did. Absence of the specific mechanism being searched for is not absence of every
mechanism — the same class of miss group 1's "What the implementation found" already named for a different
type, and it recurred here despite that precedent being cited at the top of this document.

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

**Correction, same source as §1.1: the WYSIWYG break above is not reproducible for x/y/facet.** Both the
Chart Line pane (via `resolveGridlineColor`) and the canvas (via `CSSChartStyles`'s CSS-tier write) resolved
through the same modern colour and agreed — there was no disagreement to see for those three. The ten
resolver calls this section counts were real, but on any already-rendered modern chart they were **no-ops**:
`resolveGridlineColor` substitutes only when its argument equals the legacy default, which a CSS-tier value
never does. Deleting them (§5, Task 3) was harmless and is still correct — the pane and the canvas now read
the same stored value on purpose instead of by two mechanisms coincidentally agreeing — but it closed a
break that, for x/y/facet, was not actually open. Diagonal and quadrant remain the true WYSIWYG fix: neither
had a substituting mechanism before this work, so those two are where §1.2's story is accurate as written.

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

**Correction, found by the final whole-branch review: for three of the six values there is a third
mechanism, and it predates this design.** `CSSChartStyles.apply` (`:145-156`) writes the modern gridline
colour into the **CSS tier** of `xGridColor`, `yGridColor` and `facetGridColor` whenever `ctx.modern`, on
every render, after `resetCompositeValues(CSS)` clears the prior CSS tier. This is neither of the two
mechanisms above — it is not the seed (it writes CSS, not DEFAULT) and it is not `initDefaultFormat`'s
per-render resolution (it runs in `CSSChartStyles`, keyed off the CSS dictionary path, not the label/title
lane). Because CSS outranks DEFAULT (`CompositeValue.get()`, USER > CSS > DEFAULT), this render-time write
is what actually governs x/y/facet gridline colour, seed or no seed, and it is the reason those three
values needed no seed to already render modern. See §5 for why this write must not be deleted as an
apparent duplicate of the seed.

**`VSChartChromeDefaults` was never part of the read-time migration.** The roadmap's migration node lists
five resolvers — `VSTitleChromeDefaults`, `VSCalendarChromeDefaults`,
`VSObjectChromeDefaults.applyDarkForeground`, `VSOutputChromeDefaults` and
`VSObjectChromeDefaults.chartPadding` — and the chart chrome resolvers are not among them. So the chart
interior is now **the only chrome area still computing values at render** for the label and legend
colours this design leaves alone, and it carries the portability consequence groups 1 and 2 existed to
remove for those: export a dashboard, import it into a build without this work, and the axis labels fall
back to legacy.

**Correction, found only once Task 4 tried to test it, not predicted here: the five line colours this
design seeds do NOT carry that same portability property.** The sentence this replaces originally said
the gridlines seeded here would render modern on that same import. They do not, and not only into an
older build — a seeded gridline colour does not survive any save/reload round trip of the asset, in any
build. `CompositeValue.toString()` (`CompositeValue.java:165-179`) appends the DEFAULT tier to the
serialised attribute only when `saveDefault` is true; all five of `PlotDescriptor`'s line-colour fields —
`xGridColor`, `yGridColor`, `diagonalColor`, `quadrantColor`, `facetColor` — are constructed with the
two-argument constructor (`PlotDescriptor.java:1902-1919`), so `saveDefault` stays at its field default of
`false` (`CompositeValue.java:261`) for every one of them. A seeded gridline colour therefore serialises
to an empty attribute and comes back as the field initializer's own legacy value, not the seeded one —
pinned by `SeedChromeDefaultsTest.theSeededLineColoursDoNotTravelInTheAssetYet`, which is written to fail
the moment anyone flips `saveDefault` to true without updating this paragraph. The data-label ink is not
affected: `CompositeTextFormat.writeContents` (`:296-301`) writes `deffmt` inside a `<defaultFormat>`
element unconditionally, with no `saveDefault` gate, which `theSeededDataLabelInkTravelsInTheAsset` pins
the other way.

So of this design's three justifications for seeding over read-time resolution (§1.2's WYSIWYG fix, §2's
Revert correctness, and asset portability), two hold as written and one does not for five of the six
seeded values: the WYSIWYG fix holds regardless of what reaches the asset, because it is about the
composer pane and the canvas reading one in-memory stored value, and Task 3 delivered it; Revert
correctness holds regardless too, because both branches of `seedChromeDefaults` always write, so the mark
still decides on Modernize and Revert within a live session; but "a read-time value is not in the asset"
is true only for the data-label ink. For the five line colours, the value is seeded rather than
read-time, but the seed itself does not yet reach the asset either — a difference of mechanism from the
resolver problem this design set out to close, arriving at the same practical gap for cross-build export.

**Accepted as a limitation, not fixed here.** The fix would be `saveDefault=true` on the five fields, which
appears exactly once in the whole codebase today (`StaticSizeFrame.size`) and is a deliberate exception
rather than a pattern to follow casually: it changes the serialised form of every asset carrying these
fields, marked or unmarked, and `CompositeValue.parse`'s `readAsDefault` branch (`:131`, guarding Bug
#55730) exists precisely because migrating an existing field's write semantics this way has bitten this
class before. That is its own piece of work with its own cohort argument, not a tail-end fix to a plan
whose binding requirements — the dark-mode legibility defect (§1.1) and the composer/canvas WYSIWYG break
(§1.2) — are both delivered and untouched by this gap. No section of this design required a gridline
colour to survive an export into an older build; portability was this design's rationale for seeding the
line colours, not a requirement it was asked to meet.

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
`resolveGridlineColor` itself now has **zero production callers** of its own — the ten deleted calls were
the only ones — and is kept solely for symmetry with those live siblings, not because anything still
invokes it.

**LOAD-BEARING, DO NOT DELETE: `CSSChartStyles.apply`'s CSS-tier write of `xGridColor`/`yGridColor`/
`facetGridColor` (`CSSChartStyles.java:145-156`, see §4) is not part of what this section deletes, and must
never be treated as a redundant second mechanism the way the ten resolver calls above were.** The reasoning
above — "delete a mechanism once the values agree by construction" — applies to the *dialog's* resolver
calls, which read a value that is now correct without them. It does **not** apply to `CSSChartStyles`,
because that write is not redundant with the seed: the seed lands in the DEFAULT tier, and
`CompositeValue.toString()` only serialises the DEFAULT tier when `saveDefault` is true (`:165-179`), which
none of these five fields set (§4's correction). A seeded gridline colour does not survive a save/reload
round trip. `CSSChartStyles.apply` is what re-establishes the modern colour on every render regardless, and
nothing re-seeds on a plain asset open — `VSEventUtil:2101` calls `initDefaultFormat()`/`seedChromeDefaults`
only at **creation**; only Modernize, Revert and `AbstractVSAssembly.parseState`'s bookmark-restore re-run
the hook. Delete the `CSSChartStyles` write and every reopened modern chart goes back to drawing `#eeeeee`
gridlines on a dark card — the exact defect this design set out to close, reintroduced as a regression by
a reader who correctly avoids keeping *harmless* duplicate mechanisms but doesn't realise this one is not
one of them.

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

**Corrected by the final whole-branch review — see §1.1, §1.2 and §4/§5:** the line below originally read
"a default-on dark legibility defect (near-white gridlines on a dark card...)" as this task's first
closure. That overstated it. `CSSChartStyles.apply`'s CSS-tier write already made x/y/facet gridlines
render modern before this task touched anything, so those three were not a defect this work fixed. What
this work actually closes durably is the **data-label ink**: `#4b4b4b` on `#252428` was a genuine
dark-on-dark defect, the seed for it is real, and — unlike the line colours — it survives a save/reload
round trip because `CompositeTextFormat` writes its DEFAULT tier unconditionally. Of the five line-colour
seeds, three (`xGridColor`, `yGridColor`, `facetColor`) were already modern at render via the CSS tier and
remain so after reload for that same reason, independent of this task's seed; the other two
(`diagonalColor`, `quadrantColor`) are genuinely newly seeded — a real, previously-unaddressed gap this
task closes — but that seed does not survive an asset reload (§4), so the closure is durable only within a
live session (Modernize/Revert), not across export or a plain reopen.

**Closes:** the data-label ink's dark-on-dark legibility defect, durably; the diagonal/quadrant lines'
previously-nonexistent modern treatment, within a live session; a composer/canvas WYSIWYG disagreement for
diagonal and quadrant specifically (not x/y/facet, which already agreed — §1.2); and the roadmap's "chart
interior dark palette" item, which turns out to need no new colour and no design pass.

**Leaves open**, each recorded above with its reason rather than forgotten:

- **The target surface**, line and label — untiered field, and five creation sites including the public
  script API (Scope).
- **Everything built per-render** — form lines, the parabox line, the interval bridge, map shapes. No
  persisted value, and `inetsoft.graph` holds no `VizContext` (Scope).
- **The continuous ramps** — real design, affects data encoding, needs a sign-off (Scope).
- **The chart chrome resolvers' own read-time migration** — group 3, and now the only chrome area left
  computing at render (§4).

---

## What the implementation found, and what it left open

Recorded 2026-09-02, after the branch shipped four commits — `a784857de4` (the five line colours),
`a4a679a3d5` (the data-label ink plus §3's removal of the unconditional write), `4c5ccc2b3e` (the ten
`ChartLinePaneModel` resolver calls deleted) and `9985676905` (the round-trip and change-detection tests
that found the finding below). Targeted suites green throughout; the full `core` suite ended at 5525
tests, 0 failures, 0 errors, 72 pre-existing skips. These are the things execution found that this design
did not predict, kept here so the next person in this area does not rediscover them.

**A second, later correction — from the final whole-branch review, not from execution — lives in §1.1,
§1.2, §4 and §5, and in "What this closes" above: three of the five line colours were already rendering
modern before this task, via a `CSSChartStyles` CSS-tier write this design's own analysis missed. Read the
corrections there rather than here. In one line: the render-path grep that found no `resolveGridlineColor`
caller was true but incomplete — it did not rule out a different mechanism substituting the modern colour,
and one existed.**

**The portability claim was wrong for the five line colours, and is corrected in §4 above rather than
here** — read it there; it is the load-bearing finding of this whole task and is not repeated in full a
second time. In one line: `CompositeValue.toString()` only serialises the DEFAULT tier when `saveDefault`
is true, none of the five line-colour fields set it, so a seeded gridline colour does not survive a
save/reload round trip. The data-label ink is unaffected, because `CompositeTextFormat` writes its
DEFAULT tier unconditionally. Two of the design's three justifications for seeding survive intact — the
composer/canvas WYSIWYG fix (§1.2, Task 3) and Revert correctness (§2, both branches always write) — and
the third, asset portability, is corrected rather than silently believed. Ruled an accepted limitation,
not a defect to fix inside this task: `saveDefault=true` is a separate migration with its own cohort
argument, the same kind of thing this branch has repeatedly and correctly split out rather than done
blind at the tail of an unrelated plan.

**The four questions this design flagged as unverified assumptions, answered:**

1. **Did removing `initDefaultFormat`'s colour write break any render path?** No, and the "no" needed one
   more grep than the diff showed. Task 2's review could not confirm from the diff alone that nothing
   reads the plot's text-format colour outside `TextSpec`; a direct grep found
   `SeparateGraphGenerator:529-530`, which reads `plotdesc.getTextFormat().getColor()` — i.e.
   `CompositeTextFormat.getColor()`, not `TextSpec` — behind a null guard and pushes it onto the label's
   own `TextSpec`. Traced through: `CompositeTextFormat.getColor()` resolves USER > CSS > DEFAULT, so it
   sees the seeded DEFAULT tier and the guard still fires, now with modern ink; on an unseeded chart the
   guard skips and `TextSpec.getColor()` (`:56`) falls back to `GDefaults.DEFAULT_TEXT_COLOR` — the exact
   colour the push would otherwise have supplied. No path loses ink, and both mechanisms agree on the same
   fallback. `TitleScriptable:125` reads a TITLE format via script, not the plot label, and is unrelated.
   The full core suite (5518 = 5514 + 4 new, then 5520, 5525 across the later tasks) never showed a
   report-path or exporter regression, which is the same claim verified empirically.
2. **Did Task 3's change-detection guard keep working, or does an unchanged Apply now write a USER tier?**
   Verified by automation, not by a human. The GREEN probe (`ChartLinePaneModelTest`) genuinely
   discriminates — the seed writes DEFAULT and never USER, so a masking USER tier would fail it — and the
   scoped re-review independently confirmed both write-side guards kept their `Tool.equals` comparison
   with only the `VizContext` wrapper dropped, no guard logic deleted. **Manual check 5 — an actual
   Apply-with-no-change in a running composer — has since run and passed (2026-09-10)**, so the guard is
   confirmed by a human as well as by the probe.
3. **Were the seven raw render sites the complete set, or did the manual pass find a gridline the design
   did not enumerate?** **Yes, as far as the matrix reaches.** The manual pass ran on 2026-09-10 and
   surfaced no eighth gridline across the nine checks — which covered bar, faceted, light, dark, composer,
   Modernize, Revert and export. That is strong evidence rather than proof: a chart type outside those nine
   could still carry a plot line this design did not enumerate.
4. **Was the light-mode shift actually imperceptible at (6, 9, 16) per channel, or is it visible enough for
   release notes?** **No issue was raised.** Manual check 3 — a light modern chart, gridlines warm and
   nothing else moved — passed on 2026-09-10. Note what that does and does not establish: nobody reported
   the shift as visible, which is the practical answer for release notes, but no one was asked to judge
   perceptibility side by side against the old value either. Treat it as "not noticed", not as "measured
   imperceptible".

**Two minors deferred rather than fixed, both flagged for final review rather than dropped:**

- The comment added to `PlotDescriptor.initDefaultFormat` runs four sentences against this project's
  standing preference for concise inline comments. It stayed because it explains a cross-file invariant
  (why the colour write had to go, and what covers its absence) and trimming it risked losing that, but it
  is a real style hit rather than reviewer taste — a one-clause version plus a file/line pointer would
  satisfy both.
- `ChartLinePaneModelTest`'s read-path test asserts only the Y gridline. Since Task 1 seeds X and Y with
  the same value, that one test cannot by itself catch an X/Y swap on the *read* path — the write-side
  pairing was verified separately by reading diff context, not by this test. Left as a question for final
  review rather than added speculatively: is an explicit X-side read assertion worth the second test.

### Outstanding

**The nine-check manual browser matrix RAN AND PASSED on 2026-09-10.** It needed a running server and a
human at a browser, which the implementing session could not do; it was run afterwards by the branch
owner. This work is therefore "shipped and seen" in this branch's own sense, not merely shipped, and
questions 2, 3 and 4 above are answered accordingly.

**One thing the matrix could not restore, and it is worth stating so the pass is not over-read.** By the
time it ran, §1.1's correction had already established that x/y/facet gridlines were **already** modern
before this work, via `CSSChartStyles`' CSS-tier write. So manual check 1 — "a dark chart draws a dark
hairline, not near-white" — passing does not demonstrate that this change fixed anything for those three:
it would have passed before the change too. The checks that exercise genuinely new behaviour are 2 (data
labels light on the dark card) and 8 (that ink surviving to PDF, PNG and Excel), plus 5 for the guard.

**The cross-module `-Pcommunity,enterprise` build could not run**, for a reason unrelated to this work.
Enterprise `main`'s `LicenseParser` calls `License.Builder.formLicensed(boolean)`, a method that exists on
the community commit enterprise `main` expects (`dbe6d07143`) but on no branch `viz-updates` descends
from — confirmed absent from `License.java` on `origin/main`, `origin/epic-74519` and `HEAD`. None of this
task's four commits touches licensing; the skew is pre-existing branch drift, not a regression here. A
community-only `clean install -DskipTests` was substituted and passed. This task's own signature changes
— `ChartLinePaneModel`'s constructor and `updateChartLinePaneModel` — were confirmed to have zero callers
outside `community/core`, so the specific cross-module risk the fuller gate exists to catch is absent
here; a rebase of `viz-updates` onto a base carrying the newer `License`, or an enterprise checkout
matching `epic-74519`, is needed before the fuller gate can run at all, independent of this task.
