# Chart Card — Card Geometry Decisions

**Date:** 2026-08-27
**Verified against:** community `viz-updates` @ `46e8f6b5a`, which is `HEAD`
**Covers:** §04, the card's sizing and spacing — roadmap item **S**. This file opens with decision 1,
the chart assembly's `padding`: what it is, and how a modern value reaches it reversibly.

Every claim below cites a file and line so it can be checked rather than believed. Where a §04 premise
is the spec's claim rather than a verified one, it says so.

---

## Decision 1 — how a modern padding value reaches a chart

### 1.1 Padding vs inset — `padding` already *is* §04's card inset

`java.awt.Insets` is a **value type**: a 4-tuple of top/left/bottom/right, carrying no semantics of its
own. "inset" is not a domain term in this codebase — outside `java.awt.Insets` the word appears only as
local variables and prose (`IntervalElement.bridgeInset` `:888`, stroke-alignment comments in `Legend`
`:933` and `:1023`, `mxSvgCanvas:624`). So there is no difference of type or of mechanism to find.

**And there is no difference of box either: `padding` is the card inset, and the title lane already sits
inside it.** Verified on both render paths:

- **Browser.** `vs-chart.component.html:63-66` offsets `vs-title` by `[style.left.px]="model.paddingLeft"`
  and `[style.top.px]="model.paddingTop"`, and `:73` sets `titleWidth` to
  `objectFormat.width - paddingLeft - paddingRight`. The plot follows below it —
  `chartContainerTop = titleFormat.height + paddingTop` (`chart-area.component.ts:1451`),
  `chartContainerLeft = paddingLeft` (`:1465`).
- **Server.** `g.translate(padding.left, padding.top + titleHeight)` (`VGraphPair:2946`) — the same
  ordering: one inset at the card edge, the title inside it, the graph below the title.

So a default chart already draws its title 10px in from the card's top and left, on the same vertical as
the axis content below it. That is §04's model — "the title lane needs no left or right rule of its own
… it takes the card's 12px inset, so the title starts on the same vertical as the y-axis title below it"
(§04) — **already implemented, at 10px instead of 12px.**

**§04-a is therefore a constant change, not a restructure.** `padding` 10 → 12 is the whole of the card
inset half. Nothing moves relative to anything else; every edge gains 2px together.

**What §04 asks to zero is a different value, and the roadmap mis-identifies it.** §04 scopes it
precisely: "zero the SVG's outer margin so the graph draws to its own bounds … this is the empty band
outside the outermost drawn element, nothing else." That band is inside the rendered graph image — a
graph-engine value — not the assembly's `padding`. The roadmap's §04 entry names
`ChartVSAssemblyInfo.java:88` as "the graph's own outer margin"; that line is the **card inset**, the
value §04 wants kept and set to 12. The two are separate and the confusion inverts the change.

| §04's term | The code | Today | §04 wants |
|---|---|---|---|
| the card's 12px inset | `VSAssemblyInfo.padding` — persisted, **author-editable** | 10px | 12px |
| the graph's own outer margin | **does not exist — it is the same `padding`, seen from inside an exported SVG** | — | — |

### 1.1.1 The graph has no outer margin, and §04's two instructions name one number

Searched for and not found. The chain:

- `VGraph.layout` calls `layout0(x, y, w - 1, h - 1)` (`:591-594`, the −1 being a `drawRect` overdraw
  correction) and content begins at `x, y` with no inset: `contentx = affectx ? x + vlegendsw + GAP : x`
  (`:722-725`).
- `VGraphPair` lays the graph out at `plotAndLayout(data, 0, 0, width2, height2)` (`:617`), where
  `width2 = width - leftPadding - rightPadding` (`:602-603`). No further inset.
- `VLabel.insets` defaults to `new Insets(0, 0, 0, 0)` (`:1813`), and the four axis titles take theirs
  from `TitleSpec.getLabelGap()` (`VGraph:334-356`), which defaults to 0 (`TitleSpec:99`).
- No outer-margin constant exists in the engine. `GDefaults`' only gap is `TICK_MIN_GAP = 4`;
  `VGraph`/`EGraph` declare none. `RectCoord`'s margins are axis-label clearance *inside* the plot —
  `getElementMargin` (`:466-469`) and `getAxisMargin`, whose own javadoc reads "the top margin for axis
  max label" (`:811-813`). §04 explicitly keeps that one: "the plot's top clearance, which is
  typographic".

**What §04 measured, and where the band comes from.** Its evidence is "the sample asset's bands sit inset
before any card padding exists" — a sample SVG. `SVGVSExporter` sizes an exported chart as
`vgraph.getBounds()` **plus** `info.getPadding()` on each axis, plus the title height (`:145-149`). So in
an exported SVG the graph's content genuinely does sit inset from the image bounds — by exactly
`padding`. Measured from inside that file it reads as a reservation the graph made for itself. It is the
card inset.

**Consequence.** §04's "let the card's 12px be the only edge inset" and "zero the graph's own outer
margin" are instructions about the same number, so the second cannot be carried out alongside the first.
The net server-side instruction for §04-a is `padding = 12`, and there is nothing else to zero. This also
removes the only item that was going to give plot area back (§2.1) — under §04 as specified, the plot
only shrinks.

**Confirmed at render 2026-08-27, not merely reasoned from code.** A human partner set a chart's padding
to 0 in the property dialog and compared it against the default side by side: the title's left edge and
the x-axis title's bottom edge both reach the card border, with no residual band. So the code reading is
right — **there is no graph outer margin to zero, and §04's source edit is void.** This is the render
verification §04 asked for, and it returns the opposite of what §04 predicted: the tell §04 named, "a
plot with too much air on the axis sides and comparatively little under the title", is not present,
because one uniform inset governs the title and the axis sides alike (§1.1).

The same comparison is worth keeping for a second reason: at padding 0 the title sits hard against the
card border and reads visibly cramped. `padding` is doing real work as the card inset, which is the
positive case for §04's 12px rather than for zeroing anything.

### 1.2 What is verifiably true today

**Three writers, and the third is the hardest constraint in this decision.**

| Writer | What it does |
|---|---|
| `ChartVSAssemblyInfo:88` | creation default, `setPadding(new Insets(10, 10, 10, 10))`, **unconditional**, inside `setDefaultFormat` |
| `VSAssemblyInfo:1531` | the CSS dictionary, untiered |
| `ChartPropertyDialogService:390-393` | **the author**, on every Apply of the chart property dialog, unconditionally |

The third is in neither §04 nor the roadmap. The chart property dialog **has a padding pane**:
`ChartGeneralPaneModel` carries a `PaddingPaneModel`, the service reads `getPadding()` into it at
`:147-150`, and writes `setPadding(new Insets(...))` back at `:390-393` on every Apply regardless of
which tab the author touched. So (a) an author can and does set chart padding, and (b) **whatever the
getter resolves is round-tripped into storage as an author's value on the next Apply.** That is the same
shape as the binding pane's Apply defect `1b8eb3cea` closed for colours.

**No tier, no flag.** `padding` is a plain `Insets` with a plain setter (`VSAssemblyInfo:1378`) and no
`CompositeValue.Type`. Both other padding values on the same screen *are* tiered:
`TitleInfo.setPadding(padding, type)` (`:198`) and `LegendsDescriptor.setPadding(padding, type)`
(`:346`). So nothing today distinguishes a seeded 10 from an author's 10.

**Persistence is field-direct, and that is load-bearing.** `writeAttributes` (`:867-871`),
`parseAttributes` (`:913-917`), `clone` (`:1144-1145`) and `copyViewInfo` (`:694-695`) all touch the
field, never the getter. A getter override therefore cannot leak a resolved value into storage — the
same property that makes L′ reversible.

**Padding is not in a bookmark. This corrects the roadmap.** It is written in `writeAttributes`
(`:849`) — asset XML, the same place the seed mark lives — and `AbstractVSAssembly.writeState`
(`:629-634`) emits only class, name and `writeStateContent`. So §04-a adds **no** fifth value for
decision 10 to carry and does not widen the bookmark surface. The roadmap's §04 entry says making the
padding modern-only "adds a fifth seeded value for Revert to carry and puts it under seeded-value
reversibility"; the Revert half is true only if the value is seeded rather than resolved, and the
bookmark half is not true at all.

**The read surface is eight times what §04 costed.** The roadmap names two consumers, both in
`VGraphPair`. There are at least **17 read statements across nine files**:

| File | Sites | What breaks if it disagrees |
|---|---|---|
| `VGraphPair:282-285`, `:2946` | 2 | the plot area itself, and the painted image's origin |
| `AbstractVSExporter:1396, 1459, 1637, 1987, 2320, 3883` | 6 | every export format's shared geometry |
| `HTMLVSExporter:311, 486` | 2 | HTML export |
| `PDFVSExporter:239` | 1 | PDF |
| `SVGVSExporter:145-146` | 1 | SVG and PNG |
| `AnnotationVSUtil:1187` | 1 | **annotation position on a chart** |
| `VsToReportConverter:1476, 1588-1589` | 2 | the print layout / report path |
| `ChartPropertyDialogService:147-150` | 1 | what the author is shown |
| **`VSChartModel:75-78`** | 1 | **the live viewer** — four fields on the browser model, consumed by `chart-area.component.ts:1404-1465` |

`VSChartModel` is the one that makes the resolver worth having: it reads `info.getPadding()`, so a getter
override reaches the browser with no client change and no new model field. It is also the site that
proves the value is not server-only, which the roadmap's §04 entry implies.

Not individually confirmed: `AbstractVSExporter`'s receiver is typed generally, so some of its six sites
serve every assembly type rather than charts only. The gauge family shares the same getter
(`BulletGraphGauge:59`, `VSGauge:234`, `:513`) but is a different type.

### 1.3 The four options

**A — tier it.** Make `padding` a `CompositeValue<Insets>`; the seed writes DEFAULT, the author USER, CSS
its own tier; add it to `seedChromeDefaults`. Right by construction and matches the two neighbours. But
it **changes the persisted shape**: a `CompositeValue` embeds its default behind a `^^DEF^^` prefix
(`CompositeValue.java:263`, parsed `:114-122`) where padding is four plain integer attributes today, and
`parseAttributes` is on `VSAssemblyInfo`, so the migration reaches every assembly type rather than
charts only.

**B — a `userPadding` flag.** Follow the three shipped precedents — `userTitleHeight`,
`userDataRowHeight`, `userCellHeight` — a boolean recording that an author set the value, stamped by the
writer and consulted by the reader. Cheapest correct mechanism on this branch. The stamp must compare
against what the pane was given, or the dialog's unconditional Apply (`:390-393`) stamps every chart the
first time anyone opens its properties.

**C — value-sniff the constant.** Compare against `new Insets(10,10,10,10)` and substitute when equal.
This is `resolveSeededCorner`, deleted in P6. **Rejected as the mechanism** — but note L′ kept a
third-belt version of exactly this alongside the flag (`stored != info.getLegacyTitleHeight()`,
`VSDensityDefaults:119-123`) to cover content that predates the flag. Same role here: belt, never
mechanism.

**D — resolve at read time, persist nothing new.** Override `getPadding()` on `ChartVSAssemblyInfo` to
consult a mark-gated resolver, exactly as L′ did for `getTitleHeight()` — `ChartVSAssemblyInfo:2716-2718`
delegating to `VSDensityDefaults.titleHeight(this, stored)` (`:119-126`).

### Decision: D, with B's flag as its second guard

```java
// ChartVSAssemblyInfo — mirrors getTitleHeight() at :2716-2718
@Override
public Insets getPadding() {
   return VSObjectChromeDefaults.chartPadding(this, super.getPadding());
}
```

with the resolver mirroring `VSDensityDefaults.titleHeight(T info, int stored)`: the stored value in,
three cheap tests before a `VizContext` is built, and the stored value back out unless the assembly is
marked, the author has no opinion, and the stored value is still the legacy default.

**Why D.**

1. **All 16 read sites follow untouched.** This is the property that let L′ move 111 read sites, 46 of
   them painters, by editing five delegations. Four exporters, the report converter and annotation
   positioning all get the modern value without being edited and without knowing the mark exists.
2. **Nothing new is persisted.** No XML migration, no bookmark work, and nothing for Revert to carry:
   clearing the mark restores the legacy geometry on the next read, by construction. `VizModernizeUtil`
   states the governing rule at `:95-97` — "Every value Modernize does is therefore reverted by the same
   edit to the same method, or it is not added." A read-time resolver satisfies that rule more cheaply
   than a seed does, because there is no stored value to put back.
3. **Modernize and Revert need no code at all.** They already stamp and clear the mark; the resolver
   reads it.
4. **The author's value stays authoritative and visible** in the dialog, which is what the flag is for.

**Why not A.** The migration is real, reaches every assembly type, and buys nothing D does not. D also
leaves A available later: if the card inset turns out to be a genuinely separate, CSS-authorable value,
that one wants real tiering and can have it then.

**The cost of D, stated plainly.** The stored padding and the rendered padding disagree for a marked
chart, which is a WYSIWYG hazard at exactly one place — the property dialog. L′ paid the identical cost,
and the mitigation is the identical one: the resolver takes the stored value as a parameter so a dialog
can pass its design-time value and still see the substitution (`VSDensityDefaults:109-117` says this in
as many words), and the dialog must never write back a value the author did not supply.

### 1.4 What must be built

1. `isUserPadding()` / `setUserPadding()` on `ChartVSAssemblyInfo`, persisted in `writeAttributes`. A
   missing flag means no opinion, so old content and stale clients keep the comparison guard.
2. Stamp the flag in `ChartPropertyDialogService` on a real change only — not on every Apply.
3. `VSObjectChromeDefaults.chartPadding(info, stored)`: mark-gated, three cheap tests before the
   `VizContext` is built.
4. The `getPadding()` override on `ChartVSAssemblyInfo` **only**. Gauges share the getter and are a
   different type, so they are unaffected — confirm that is a decision and not an omission.
5. The "follow the default" checkbox in the padding pane, if the pane is to match the title-height and
   cell-height panes L′ gave one.
6. An export pass on P6's scale: PDF, PNG/SVG, HTML, Excel and the print layout all read this value.
7. Check `AnnotationVSUtil:1187` first, not last — annotations on a marked chart will move.

### 1.5 What §04 answers, and the one thing it does not know

**§04 was read at this revision.** It answers more than the roadmap's summary of it suggests, and two
questions this file previously listed as open are settled in the document:

| Question | §04's answer |
|---|---|
| One inset or several nested ones? | **One.** "Gaps do not stack. The card's 12px inset is what sits between the card border and the outermost content on each side; the title lane, the y-axis title, the x-axis title and the legend column add no edge padding of their own… There is no exception." Where a nested region does set an inset, "it replaces the card's rather than adding to it" |
| Does the title lane sit inside the inset? | **Yes** — "the title lane needs no left or right rule of its own… it takes the card's 12px inset". Already true in code (§1.1), so this is not a change |
| What binds the value? | `--inet-space-5`, i.e. 12px. "Code should carry the step, never the literal" |

**The one thing §04 does not know is that the value already exists and belongs to the author.** Its
sentence on the graph margin reads "the sample asset's bands sit inset **before any card padding
exists**" — the spec believes there is no card padding today. There is: `padding`, seeded to 10px at
creation (`ChartVSAssemblyInfo:88`), persisted per assembly, and **editable by the author in the chart
property dialog's padding pane** (`ChartPropertyDialogService:147-150` read, `:390-393` write). The word
"padding" appears ten times in the whole spec and never once in this sense. So §04 specifies the card
inset as a design-system constant while the product ships it as a per-chart author setting, and the
document cannot answer what happens when the two disagree. That is decision 1 below.

**Not a decision — a measurement.** The values: 12px was drawn against mockups at 1100×620 and has not
been measured at real render sizes here, and §04's own "graph's own outer margin" is unlocated in code
(§1.1). §04 asks for this pass itself, on the grounds that every mock in the document draws the intended
result and so none of them can show the defect.

### 1.6 The decision — the author wins, but opts in

**Taken 2026-08-27.** When a marked chart's card inset is 12px and the author has typed 4px, **the author
wins, and the padding pane gains a "follow the default" checkbox** so they can hand the value back.

The two rejected answers and why: *the author always wins silently* is cheaper but makes §04's "one
number governs every vertical in the card" advisory, with no way for an author to return to the default
once they have left it; *the design system wins* enforces §04 exactly but deprecates a shipped control
and changes behaviour for anyone already using it.

What decides it is that the product already has this exact affordance in two places — L′ shipped
follow-the-default checkboxes for title height and selection cell height, replacing the value comparison
those dialogs used to infer authorship from. A third one is one idiom, not a second. It also makes the
`userPadding` flag of §1.3 option B load-bearing rather than internal: the checkbox *is* the flag,
surfaced.

So the mechanism is settled end to end: read-time resolver (option D) + `userPadding` (option B) + the
checkbox as its UI. §1.4 is the build list, unchanged, with item 5 now required rather than conditional.

**Not open — precedent settles it.** The mechanism. Read-time resolution plus a user flag is L′'s shipped
shape, it fits all 17 read sites, and the alternatives were weighed in §1.3. Whichever answer the
decision above takes, this is how it is built.

**Still to check, not decide.**

- Gauges share the getter (`BulletGraphGauge:59`, `VSGauge:234`, `:513`) and are a different type —
  currently out of scope by scoping rather than by decision.
- §04-b's legend panel change moves the plot's right edge too; §04 puts the border-and-fill removal at
  8–10px of returned horizontal room, and the roadmap says it needs its own re-measurement.
- §04 flags a spec-vs-code disagreement of its own on the title lane's *height* — whether the strip
  participates in it or overlays it. L′ and L″ have since shipped and changed that lane, so read §04's
  height rule against them rather than against the code it was written for.

---

## Decision 2 — the interior gap scale is four values, not one

**The card inset is one row of §04's spacing table.** Decision 1 settles that row: `padding` 10 → 12,
resolver plus flag plus checkbox. It does **not** deliver §04's spacing model. The section specifies four
gaps, and each is a different value in a different place with a different default and a different
mechanism available:

| §04's gap | Spec | The code | Today | Author-settable | Mechanism |
|---|---|---|---|---|---|
| card border → outermost content | **12** | `VSAssemblyInfo.padding` | **10** | yes — chart property dialog | untiered → decision 1 builds it |
| axis title → its labels | **4** | `TitleSpec.labelGap`, from `titleDesc.getLabelGap()` (`GraphGenerator:6183`), applied as `Insets` at `VGraph:335-356` | **0** (`TitleSpec:99`) | yes | **already a `CompositeValue`** (`TitleDescriptor:161-176`) |
| axis labels → plot | **8** | `AxisSpec.labelGap`, from `axisD.getLabelGap()` (`GraphGenerator:2591`) | **2** (`DefaultAxis:2111-2115`, `gap == 0 ? 2`) | yes | **already a `CompositeValue`** (`AxisDescriptor:439-456`), CSS writer at `CSSChartStyles:348` |
| plot → legend | **16** | `VGraph.GAP` (`:1916`), applied `:722-725` | **2** | no | **none** — see below |

**Two of the four need no new mechanism at all.** `AxisDescriptor.labelGap` and
`TitleDescriptor.labelGap` are already tiered, and `GraphGenerator` already holds a `VizContext`
(`:7489`, set from the chart at `:223` or `LEGACY` at `:439`) with `ctx` in scope at the axis-spec site
(`:2572`, twenty lines above `:2591`). Seed the DEFAULT tier under the mark and an author's USER value
wins for free — no flag, no resolver, no migration. This is the cheapest part of §04 and it was costed
as part of the expensive part.

**One of the four has no route.** `VGraph.GAP` is a private constant in `inetsoft.graph`, and
**`VizContext` does not reach that package — zero files.** It stops at `report/composition/graph`, the
layer that builds a graph from a viewsheet. So making the legend gap mark-aware means either giving
`VGraph` a settable gap that `GraphGenerator` writes, or coupling the engine to the viewsheet model.
The first is right and small; it is still a new property on an engine class rather than a constant edit.

### 2.1 The magnitudes are not cosmetic, and they cost plot area

The deltas are 10→12, 0→4, 2→8 and 2→16. Composed along each edge of a fully-labelled chart:

- **left**: +2 (card) +4 (y-axis title gap) +6 (y label gap) = **+12px off the plot**
- **bottom**: same chain = **+12px**
- **right, with a legend**: +2 (card) +14 (legend gap) −8…10 (§04-b's legend border-and-fill removal,
  §04's own estimate) = **+6…8px**
- **top**: +2, plus whatever the title lane does — L′ owns that row

Call it **~19px of width and ~14px of height**, about 5% of each on the 400×240 default chart size
(`ChartVSAssemblyInfo:74`) and proportionally worse on a small chart in a dense dashboard.

**§04's spacing model spends plot area to buy the card look, and after §1.1.1 nothing gives any back
except §04-b.** Zeroing the graph's own outer margin was the other half of the exchange and it does not
exist. That was not knowable from the document — §04 states the offset as a source edit, not a
hypothesis — so the arithmetic here is the first time the model's net cost has been stated.

**The single most expensive value is plot → legend, at 2 → 16 — over half the horizontal cost.** It was
taken on its own; see decision 3, which also corrects what this section first said about its mechanism.

### 2.2 Three more §04 items that are not gaps

- **Zero the graph's own outer margin.** Still unlocated in code (§1.1). §04 observed it in a sample
  asset. Until it is found, the four gap changes are unopposed and the plot only shrinks.
- **`--inet-chart-line-height` at 1.2** — "the one type value this spec adds", covering title, axis
  titles, tick labels and legend labels. Chart text is server-painted, so this is a Java font-metrics
  change, not a CSS token, whatever the token name suggests.
- **Hidden means zero, with gap inheritance.** §04's rule is subtler than "collapse": with the y labels
  hidden but the axis title kept, the title takes the labels' 8px rather than keeping its own 4px. Check
  whether the engine already does this — the bands size to content, so it may — before costing it.

### 2.3 "Hidden means zero" — the collapse half already holds, the inheritance half does not

Checked against the engine 2026-08-27. §04's rule is two rules, and they land differently.

**Half 1, collapse — a hidden element contributes no size and no gap. Already true, for all three
elements, with no work needed:**

| Element | Where it collapses |
|---|---|
| axis title | `xTitle` is constructed only `if(xTitleSpec != null && xTitleSpec.getLabel() != null)` (`VGraph:332-337`), and its gap is the `VLabel`'s own inset assigned inside that same block, so the gap leaves with the title. Size then reads `xtitleh = xTitle == null ? 0 : …` (`:739`) |
| axis labels | `containsLabel() = isLabelVisible() && vlabels != null && vlabels.length > 0` (`DefaultAxis:991-993`). `getAxisWidth`/`getAxisHeight` add the label size **and** `getLabelGap()` inside `if(containsLabel())` (`:815-818`, `:884-887`), so hiding the labels drops both and the band falls to the axis line alone |
| legend | `vlegends == null \|\| legendLayout == NONE` zeroes the size (`VGraph:639-641`), and `GAP` is added only under `affectx/affecty/affectw/affecth`, each of which requires a real LEFT/RIGHT/TOP/BOTTOM layout (`:713-725`) — so `NONE` takes the gap with it |

Half 1 was also **confirmed at render 2026-08-27**: hiding a chart's y-axis labels moved the plot's left
edge out to meet the retained "Cal Month" axis title, so both the band and its gap were reclaimed. The
screenshots settle half 1 and cannot settle half 2 — see the note at the end of this section.

**Half 2, gap inheritance — hide the inner element and the outer one should take the inner one's gap.
Not implemented, and nothing in the engine could implement it as written.** The two gaps live on
different objects and neither reads the other:

- the title's gap is `TitleSpec.labelGap`, applied as the title's own `VLabel` inset (`VGraph:346-351`)
- the labels' gap is `AxisSpec.labelGap`, applied inside `getAxisWidth`, and it vanishes with the labels

Grepping `isLabelVisible|containsLabel` across `VGraph`, `TitleSpec` and `GraphGenerator` returns one
hit — `GraphGenerator:2588`, which *sets* label visibility — so there is no read anywhere that makes a
title gap depend on whether the labels beside it are drawn. With the labels hidden, the axis title sits
at **its own** gap from the plot, which is the opposite of §04's rule.

**Why this has never been noticed, and why it starts mattering the moment decision 2 lands.** Today
`TitleSpec.labelGap` defaults to 0 (`TitleSpec:99`) and `AxisSpec.labelGap` resolves to 2
(`DefaultAxis:2111-2115`). So title → labels is 0, labels → plot is 2, and with the labels hidden
title → plot is 0. The discrepancy the rule exists to correct is 0 against 2 — invisible. Seed §04's 4
and 8 and it becomes **4 where §04 says 8**: a 4px error in precisely the state the rule was written for,
introduced by the same commit that introduces the values.

**Why a screenshot cannot settle half 2, and what can.** Hiding the labels visibly returns the band —
that is half 1, and it is what a before/after pair shows. Half 2 is not *whether* the space comes back
but *how wide the surviving gap is* between the axis title and the plot's left edge. Today the two
candidate answers are the title's own 0 and the labels' 2, so they differ by 2px; after decision 2 they
are 4 and 8, differing by 4px. Neither difference is legible in a screenshot, which is why this was
settled by reading the assignment site rather than by looking.

An empirical demonstration is possible but needs a script or a CSS rule: neither
`AxisDescriptor.labelGap` nor `TitleDescriptor.labelGap` has any dialog exposure — the only non-script
writer is `CSSChartStyles:348`, for the axis one. Set the axis title gap to ~20 and the label gap to ~40,
then hide the labels: inheritance would leave a 40px gap, its absence leaves 20px.

**Three ways forward, and the third would have been a legitimate answer.**

1. Make the title's inset conditional on the abutting axis's label visibility, inside `VGraph`. A new
   cross-object read in the engine, for a 4px case.
2. Express §04's rule where the gaps are assigned: **the innermost visible band takes the plot-adjacent
   gap, anything outside it takes its own.** This is what §04 means by "the gap belongs to the element on
   the content side of the arrow"; it states the rule once rather than deriving it twice.
3. Decline the rule, let the title keep its own gap, and record the clause as declined.

### Decision 5 — option 2, resolved in `GraphGenerator`

**Taken 2026-08-27.** The rule is implemented, and it belongs at the descriptor → spec boundary in
`GraphGenerator` — the one layer where both gaps, label visibility and the `VizContext` are all in scope.
Only one of the four states needs any code:

| State | Wanted | Today |
|---|---|---|
| title and labels both shown | title 4, labels 8 | correct by construction |
| **labels hidden, title shown** | **title takes 8** | **title takes its own 4** — the only gap |
| title hidden, labels shown | labels 8 | correct — the labels' own gap already is the plot-adjacent one |
| both hidden | plot reaches the card inset | correct — half 1 |

**The two assignment sites.**

- `GraphGenerator:2591`, `axis.setLabelGap(axisD.getLabelGap())` — the plot-adjacent gap. Everything
  needed is already at this site: `:2588` computes label visibility and `ctx` is in scope, already used
  by a modern-chrome resolver three lines up at `:2584`.
- `GraphGenerator:6183`, `tSpec.setLabelGap(titleDesc.getLabelGap())`, inside
  `getTitleSpec(TitleDescriptor titleDesc, String type)` (`:6017`, called at `:921-924`). The `type`
  argument is `"x"`/`"x2"`/`"y"`/`"y2"`, which identifies the abutting axis, so its descriptor is
  reachable without widening the signature.

**Two traps, both of which would ship a defect if missed.**

1. **Label visibility is mode-dependent.** `:2588` reads
   `!maxMode && axisD.isLabelVisible() || maxMode && axisD.isMaxModeLabelVisible()`. The title-gap
   resolution must use that same expression, not the plain `isLabelVisible()` getter, or the gap is wrong
   in max mode — where a chart is most often looked at closely.
2. **The inheritance must itself be mark-gated.** On an unmarked chart the title gap is 0 and the
   plot-adjacent gap resolves to 2, so applying inheritance unconditionally would move a legacy chart's
   axis title by 2px whenever its labels are hidden. That is the reflow the seed mark exists to prevent.
   Resolve inheritance only when the assembly is marked, alongside the values themselves.

Option 1 was rejected for putting a viewsheet-shaped rule inside `inetsoft.graph`, which is also what
makes the legend gap cheap (decision 3) — the engine stays a consumer of resolved values.

### 2.4 What §04 says about tokens does not apply to the card inset

§04 says "code should carry the step, never the literal", and maps the card inset to `--inet-space-5`.
That cannot hold for this value: the inset lives in Java (`padding`), reaches the browser as four numbers
on `VSChartModel` (`:75-78`), and positions the title through `[style.left.px]`/`[style.top.px]` bindings
(`vs-chart.component.html:65-66`) rather than through a stylesheet. A CSS token cannot be its source of
truth. Two of the other three gaps are also Java-side. §04's token instruction is sound for the DOM
chrome it was written for and does not reach the card's geometry.

Also stale: §04 says only the 12px step is "an addition — one .75rem step merged into the map".
`--inet-space-2/4/5/6` already resolve to 4 / 8 / 12 / 16 (`_variables.scss:559-563`), so all four values
have tokens today.

---

## Decision 3 — the legend gap takes §04's 16px, and gains an option

**Taken 2026-08-27.** Plot → legend defaults to §04's 16px on a marked chart, and becomes an
author-settable option rather than a fixed value.

**Its mechanism already exists, and decision 2 first said it did not.** Correcting that: decision 2
looked at `VGraph.GAP` and concluded the gap had no author route and no mark route. `VGraph.GAP` is only
a fixed 2px residue between the legend container and the content (`VGraph:722-725`). The tunable value is
**`LegendsDescriptor.gap`** — a `CompositeValue<Integer>` defaulting to 0 (`:595`), javadoc'd as "the gap
between the legend and the axis/plot" (`:302-324`), already threaded to the legend by `GraphGenerator` at
`:1696`, `:1775` and `:1810` (the same three sites that pass legend padding), and applied by
`LegendGroup.getLegendGap()`, which shrinks the legend group's own bounds (`:125-126`, `:163-167`).

So this is the **same cheap shape as the two axis gaps**: seed the DEFAULT tier under the mark, and an
author's USER value wins for free. No engine change, no new property on `VGraph`, no `VizContext` in
`inetsoft.graph`. **Three of §04's four gaps need no new mechanism; only the card inset does.**

**One interpretation to settle explicitly rather than by accident.** Total separation today is
`VGraph.GAP` 2 + `LegendsDescriptor.gap` 0 = **2px**. §04's "16px to the legend" either means the total,
in which case the DEFAULT tier is **14**, or the tunable part, in which case it is 16 and the total is 18.
Pick one and say which in the commit; a 14 that exists to make a 16 look right is the kind of constant
that gets "corrected" later.

**The option.** `gap` has no dialog exposure today — its only web consumer is
`VSChartResizeLegendService` (`:75`, `:78`), on the legend drag-resize path, where it is added back when
computing the legend's size. So "add option" means surfacing it, and it should carry the same
follow-the-default checkbox decision 1 gives padding: an author who nudges it needs a way back, and a
third idiom is worse than a third instance of one idiom.

---

## Decision 4 — the axis drag, and what it does to §04's premise

**Noted 2026-08-27, from a composer screenshot: the boundary between the plot and an axis band can be
dragged, which moves an axis edge inside the card.** Traced:

| | |
|---|---|
| What it stores | `AxisDescriptor.fixedWidth` / `fixedHeight` — plain `double` fields (`:909-910`), **untiered**, persisted as XML attributes (`:778-779` write, `:591-596` parse), 0 meaning no fixed size |
| Route in | drag only. `vs-chart.service.ts:206-231` → `VSChartAxisResizeEvent` → `/events/vschart/resize-axis` → `VSChartAxisResizeService:61,64` |
| Route out | drag to the extreme, which sends size 0. There is no dialog and no reset control |
| Who reads it | `GraphGenerator:2743` → `spec.setAxisSize(...)` |

### 4.1 It does not touch the outer/inner question

`padding` is the outermost box and a fixed axis band sits inside it, so nothing about decision 1 changes.
The card inset stays one uniform value on all four edges whatever the axis bands do.

### 4.2 But it falsifies §04's structural premise, which nothing else had

§04's model is "five nested boxes and **only one of them is elastic** … axis label bands, axis title
bands and the legend column **size to their own content** … the plot is whatever is left." A dragged axis
is a **fixed** band that does not size to its content, so in that state the plot is not the remainder and
the band is not intrinsic. The screenshot shows the consequence directly: a large empty band between the
x-axis labels and the x-axis title — which is precisely the state §04's rule *"no band ever degrades to
an empty stub"* says cannot exist. **§04 has no state for a fixed band**, and this is not an edge case
reached by an unusual setting; it is reached by a drag handle the composer offers on every chart.

Consequence for decision 2's arithmetic: the ~19px / ~14px figures assume the plot absorbs every gap
increase. Where an author has fixed a band, the increase comes out of that band's interior instead, so
the cost lands differently per chart — and on a band already dragged near its content, adding 4 or 8px
is what produces the stub.

### 4.3 It is the third instance of one defect class

`padding` (decision 1), `LegendsDescriptor.gap` (decision 3) and now `fixedWidth`/`fixedHeight` are all
author-editable geometry with no tier and no opt-back-in short of returning the control to an extreme.
That is the same shape L′ solved for title height, selection cell height and table row height. The axis
sizes are the worst of the three: untiered, and reachable only by drag.

Worth naming precisely because it is inside one file: `AxisDescriptor.labelGap` **is** a `CompositeValue`
(`:439-456`) while `AxisDescriptor.fixedWidth`/`fixedHeight` are plain doubles, so the same class models
one geometry value reversibly and two irreversibly.

**Not scheduled here.** Decisions 1–3 are what §04-a and the gap scale need. This one is recorded so it
is not re-discovered as a defect during the export pass, and because it is the strongest argument yet
that the follow-the-default checkbox should be a pattern rather than three separate controls.

---

## Related documents

- [chart-card-roadmap.md](./chart-card-roadmap.md) — the track's entry point; §04 is item **S** there,
  and "The next long pole" is its scope statement. **That section's bookmark claim is corrected in
  §1.2 above**
- [seeded-value-reversibility-decisions.md](./seeded-value-reversibility-decisions.md) — the seed mark
  and the four seeded values. Decision 1 above deliberately does **not** add a fifth
- [chart-card-anchored-strip-lane-decisions.md](./chart-card-anchored-strip-lane-decisions.md) — L′ and
  L″, whose read-time resolver shape decision 1 copies
- [chart-card-source-doc-corrections.md](./chart-card-source-doc-corrections.md) — the running audit of
  the external set. §1.5 above belongs in it at the next sync
