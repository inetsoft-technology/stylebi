# Chart Card Source Docs — Corrections and Their Effect on the Plan

**Date:** 2026-08-11
**Applies to:** the `chart-card-design2/` document set (`docs/superpowers/specs/lookfeel/`) — thirteen files, including
the new `Visualization Widget Spec.dc.html`
**Source docs were synced against:** community `viz-updates`, sync stamped 2026-08-11T01:23Z
**This audit was verified against:** community `viz-updates` @ `a038a30b5`

**This supersedes the 2026-08-05 edition**, which audited the earlier `chart-card-design/` set against
`aed8e6b22`. Every finding in that edition is accounted for in
[the disposition table](#disposition-of-the-2026-08-05-findings) below — kept, closed, or superseded — so
nobody has to diff two files to learn what happened to one.

**What the new set is.** `chart-card-design2/` is the same twelve-file set re-synced against a new branch,
plus one new document. The two HTML mockup files and three of the markdown tickets are byte-identical to
the previous set; six markdown files changed; nothing was removed and no decision was reversed. The
changes are a status overlay — what has since shipped — plus a small number of new items. The thirteenth
file, `Visualization Widget Spec.dc.html`, is not a status overlay: it is a second decision-making
document covering the eight non-chart assemblies, and several of its decisions land on chart-card work
that has already shipped.

**Why this file exists separately.** The source docs are precise and confident, and they cite line
numbers. That makes them easy to trust and hard to correct in passing. Each entry below therefore states
what the doc says, what the code says, and how to confirm it.

**Reading order:** [§1](#1-factual-errors) is where a doc is wrong about the code.
[§2](#2-staleness) is where the code moved after the docs were written. [§3](#3-design-conflicts) is where
two deliberate decisions disagree. [§4](#4-gaps) is where a doc leaves a decision unmade that its
implementer needs. [§5](#5-regressions-from-the-last-audit) is new, and is the most important section for
process: findings this file already made in August that the re-sync re-broke.

**Scoring.** Of the claims checked this pass: three factual errors, three points of staleness, four design
conflicts, one gap, and three regressions. The set remains substantially accurate — the same verdict as
August — but the failure mode has changed. In August the errors came from reading a large codebase
quickly. This pass, the largest single category is claims that were already corrected here and came back.

---

## Scope of this audit

**Audited in full.** The six changed markdown files: `README.md`, `Open items - handoff.md`,
`Anchoring beyond charts - discussion.md`, `Chart overlay surfaces - ticket.md`,
`Shell surfaces - ticket.md`, `Outlined chart text - ticket.md`.

**Audited where it lands on shipped chart-card work.** `Visualization Widget Spec.dc.html` — six
decisions: the title lane's height row, gating the anchored strip on density, the hardcoded strip height,
derived selection, the seed mark's dependency shape, and the card radius.

**Not audited, and deliberately so.** The widget spec's §04 density matrix, its §05 per-widget literal
inventories, and the range-slider bitmap work — none of it blocks the chart-card rollout, and auditing it
would triple this file for no decision anyone is waiting on. Also not re-audited: `Chart type sizes -
ticket.md`, `Dead menu icons - ticket.md` and `Zoom naming - ticket.md`, which are byte-identical to the
previous set and whose August findings carry over unchanged.

---

## Disposition of the 2026-08-05 findings

| Old § | Finding | Fate |
|---|---|---|
| 1.1 | The selection CSS override path is not dead | **Still true** — and contradicted again by the new set. See §5.1 |
| 1.2 | The data-tip stacking item is not a live bug | **Still true** — and contradicted again by the new set. See §5.2 |
| 1.3 | The two tooltip classes are not identical | **Closed** by `43a934add`. The mixin at `_directives.scss:215` holds the eleven shared properties and carries a comment stating why `max-width`/`max-height` are excluded; `.widget__default-tooltip` re-declares them at `:236-237`. The hidden-annotation tooltip did not acquire a viewport cap |
| 1.4 | The mini-toolbar already renders a kebab | **Closed** by `67c486d67`, which made the existing overflow kebab resident rather than adding a second one |
| 2.1 | The server-side chrome layer landed after the docs | **Still true**, and now extended — the widget spec decides to change one of those shipped constants. See §3.3 |
| 2.2 | The modern gate is never mentioned | **Still true of the chart-card set.** The widget spec closes it from the other side: its §02 documents all six resolvers and the master property. The chart-card documents still specify unconditionally |
| 2.3 | The strip is already 24px under the gate | **Still true** — and contradicted again by the new set. See §5.3, and §3.1 for how the widget spec resolves it |
| 2.4 | The CARD tooltip grew a structure the docs do not know about | **Closed** by `43a934add` |
| 2.5 | `chart-actions.spec.ts` asserts by index, not only by label | **Closed** by `67c486d67`, which appended the new menu group last |
| 3.1 | Title band fill: spec says never filled, `VSTitleChromeDefaults` ships a fill | **Decided 2026-08-11 — unfilled, hairline rule only.** See [chart-card-open-item-decisions.md](./chart-card-open-item-decisions.md) §1, which also records the two consequences that reach past the chart card: the title bar stops matching the table header band, and the widget spec's 6px radius argument loses one of its two supports |
| 3.2 | The legend is server-side, and the spec does not say so | **Still true.** Nothing in the sync diff touches it |
| 4.1 | The anchoring mechanism is unspecified | **Closed.** Resolved as reposition-in-place and shipped across three slices |

---

## 1. Factual errors

### 1.1 Step 0's four `:root` tokens are one token, not four

**Where:** `Open items - handoff.md` step 0, which calls the four additions "a precondition, not a
cleanup" and states that two later steps cannot start until they exist.

**The claim.** Four `:root` additions no other step owns: a gridline grey, a 44px touch control height,
and two icon sizes — "the toolbar glyph and the kebab, currently literals at every call site."

**What the code says.** Three of the four are already resolved.

- **Touch control height — already shipped and already consumed.** `--inet-control-height-touch: 44px` is
  declared at `_variables.scss:480` and read by the mini-toolbar's own touch row at
  `mini-toolbar.component.scss:202,207-208`. The step describes adding a token that the code it points at
  is already using.
- **The two icon sizes — not literals, and not two.** Both call sites in
  `mini-toolbar.component.html` use the same shared class: the action glyph at `:47`
  (`icon + ' icon-size-small'`) and the kebab at `:71` (`kebabAction.icon() + ' icon-size-small'`). There
  is one size, already abstracted into the shipped icon system (`_icon-alias.scss` extends
  `.icon-size-small` throughout). There is nothing to hoist.
- **Gridline grey — genuinely absent from CSS, and narrower than described.** No
  `--inet-subtle-border-color` or equivalent exists in `_variables.scss`. But the value is not missing
  from the product: `VSChartChromeDefaults.GRIDLINE = 0xE8E5DE` shipped before the previous audit, with
  `resolveGridlineColor()` implementing the gridline/axis-line split. What is missing is a CSS token for
  a value the server already resolves.

**Confirm it:**
```bash
cd community/web/projects/portal/src && \
  grep -n "control-height-touch\|subtle-border" scss/_variables.scss && \
  grep -n "icon-size-small" app/vsobjects/objects/mini-toolbar/mini-toolbar.component.html
```

**Effect on the plan.** Step 0 is not a precondition and should not be sequenced as one. It is one token,
and it is only needed by whichever of item 10 (chart colour literals) or the §08 `GDefaults` mapping goes
first — either can declare it. Fold it into that step. The dependency arrow `0 → J` in the handoff's
diagram should be deleted, along with the two arrows it feeds.

---

### 1.2 Every citation to a companion drawing points at a filename that does not exist — resolved

**Where:** nineteen citations across four files — `Chart overlay surfaces - ticket.md` (3),
`Shell surfaces - ticket.md` (2, one of them wrapped across a line break), `github.md` (13), and one
stale `Chart Card Spec v1.dc.html`.

**The claim.** The tickets cite `Chart overlay surfaces - decided visuals.dc.html`; `github.md`'s
changelog and screen map cite `Chart Card Spec v3.dc.html`.

**What the folder contains.** `Chart overlay surfaces - decided visuals.html` and `Chart Card Spec.html`
— the same two files as the previous set, byte-identical to it. The `.dc.html` suffix and the `v3` were
added to the *references* during this sync without the files being renamed. The previous set's references
were correct.

**Why it matters more than a typo.** Both tickets instruct the implementer to read the drawing before
writing any CSS — "a layout described in prose gets built two ways" — and the shell ticket makes it a
precondition of item 4. An implementer who follows the citation finds nothing and either proceeds without
the drawing or stops.

**Decided, 2026-08-11: there are two canonical visual sources, and these are their names.**

| Cite this | Not this | Covers |
|---|---|---|
| `Chart overlay surfaces - decided visuals.html` | `…decided visuals.dc.html` | §01 selection, §02 the card tooltip ramp, §03 the Resize Plot sliders |
| `Visualization Widget Spec.dc.html` | — | The eight non-chart assemblies: title lane geometry, density, derived selection, the range slider redraw |

`Chart Card Spec.html` is the specification, not a drawing; the ten `Chart Card Spec v3.dc.html`
references in `github.md` resolve to it. The `.dc.html` suffix is correct on the widget spec, which is the
filename on disk, and wrong everywhere else.

**The orphan.** No markdown file in the set cites `Visualization Widget Spec.dc.html` at all — not the
README, not the handoff, not `github.md`'s screen map, which has no rows for it. It is the newest and most
decision-dense document in the folder and nothing points at it. An implementer working from the handoff
would never learn that the title lane, the strip's density gating and the card radius were decided
elsewhere, which is the mechanism behind §3.1, §3.3 and §3.4.

**Confirm it:**
```bash
cd community/docs/superpowers/specs/lookfeel/chart-card-design2 && ls *.html && grep -c "dc\.html" *.md
```

**Effect on the plan.** No plan change. **Applied to the source set on 2026-08-11**: the citations were
rewritten per the table above (the two remaining `.dc.html` strings in `github.md` are records of files
that were genuinely deleted — `Chart Card Spec v1.dc.html` and `Resize Plot slider - proposal.dc.html` —
and are correct as history); the widget spec was added to `README.md`'s changelog and "Also here", to
`github.md`'s changelog, and to the screen map as eight new rows; and the four items it overrides now
carry cross-references — the strip's sizing and the seed-mark precondition in `Anchoring beyond charts -
discussion.md`, the dependency picture, step 3c and the alignment-anchor gap in `Open items - handoff.md`,
and the surviving second selection idiom in `Chart overlay surfaces - ticket.md`.

---

### 1.3 The `#ff8d41` count in the Resize Plot slider ticket is 11, not 7

**Where:** `Chart overlay surfaces - ticket.md` item 13, and the previous edition of this file, whose
appendix recorded "7 occurrences. Confirmed."

**What the code says.** Eleven, all `background:` declarations, at
`vs-chart.component.scss:72,76,85,119,123,131,171,207,212,217,222`. The file has not been modified since
`e8df3491b` (2024-07-15), so the count was eleven when the ticket was written and eleven when this file
first claimed to have confirmed seven.

**Confirm it:**
```bash
cd community/web/projects/portal/src && \
  grep -c "ff8d41" app/vsobjects/objects/chart/vs-chart.component.scss
```

**Effect on the plan.** Trivial for the work — item 13 replaces all of them either way. Recorded because
it is a mis-verification in *this file*, and the appendix's value depends on its entries being
trustworthy. It also suggests the previous appendix's counts were sampled rather than run; treat the
uncounted claims in it as unverified rather than verified.

---

## 2. Staleness

Not errors. The docs were accurate at the 01:23Z sync. The code moved.

### 2.1 Slice 3 shipped nine hours after the sync

**What the docs say.** The new set is unambiguous and repeats it in four places: the selection family is
"not yet shipped" and "still undecided," the rollout's "remaining, still undecided" work is "the selection
family (list, tree, container), calendar," and the anchoring document's sequencing puts "confirm the seed
mark has landed" before "write the remaining rollout properly, per assembly family: the selection family…"

**What the code says.** `a038a30b5`, "feat(vsobjects): anchor the selection family, kebab-only", committed
2026-08-11 10:28:13 −0400 — after the 01:23Z sync. `ANCHORED_ASSEMBLY_TYPES` in
`mini-toolbar.service.ts:41-53` now reads `vschart`, `vstable`, `vscrosstab`, `vscalctable`,
`vsselectionlist`, `vsselectiontree`, with the slice-3 entries commented "anchored but not capped:
`AbstractVSActions.kebabOnly` makes the kebab the whole strip at any width. The container is deliberately
absent; it is its own slice."

It shipped without the seed mark, and correctly so — see §3.2.

**Confirm it:**
```bash
cd community && sed -n '41,53p' web/projects/portal/src/app/vsobjects/objects/mini-toolbar/mini-toolbar.service.ts
```

**Effect on the plan.** The remaining rollout is **the container and the calendar**, not four families.
The anchoring document's per-family inventory for "selection list, tree, container — not yet shipped" is
now half a description of shipped code; its predictions are testable rather than pending. One prediction
in it is already contradicted: it says the container "owns it; children inside one get none," while the
shipped slice excludes the container entirely as its own slice.

Also note what slice 3 decided that the documents do not record: selection list and tree are **exempt**
from the 22px sort-control reserve. `VSObjectContainer.rightEdgeReserve` (`:585-600`) returns 0 for both,
because a selection's right-edge occupant is the pending-Apply icon, handled by `.pending-alert` in
`vs-selection.component.scss` instead.

---

### 2.2 `--inet-font-size-lg` already shipped

**Where:** `Open items - handoff.md` step 1b, "Adds `--inet-font-size-lg: 16px`", and
`Chart type sizes - ticket.md`, which lists it as "to add".

**What the code says.** `_variables.scss:239` declares `--inet-font-size-lg: 16px`. The CARD ramp that
consumes it shipped in `43a934add`.

**Effect on the plan.** Remove it from step 1b's deliverables. The type-scale ticket's chrome tier
(11/13/16) now has all three values shipped, which shrinks that ticket to the interior tier alone.

---

### 2.3 The shell now owns a token layer adjacent to the type-scale proposal

**What the docs say.** `github.md` flags `scss/_viz-tokens.scss` as "new, unticketed" and says the overlap
with `Chart type sizes - ticket.md`'s `--inet-font-size-chart-*` proposal is "not yet reconciled."

**What the code says.** The flag is correct and the overlap is smaller than it reads. `_viz-tokens.scss`
declares one font-size token, `--inet-viz-font-size`, resolved by density mode — a row-text size for
browser-DOM data surfaces. The type-scale ticket proposes `--inet-font-size-chart-sm/-base/-title` for
the chart's *interior*, which is server-rendered into SVG and which the viz layer explicitly disclaims
("chart-color tokens reserved as server-rendered/conceptual-only"). They do not collide.

**Effect on the plan.** The reconciliation pass the sync asks for is a ten-minute read, not a
negotiation. The real overlap is elsewhere and is settled by the widget spec's §09 rather than by this
ticket — see §3.4.

---

## 3. Design conflicts

Four. In each case neither side is an error; two deliberate decisions disagree, and someone has to choose.
Three of the four are new, and all three come from the widget spec landing beside a set that had already
shipped code.

### 3.1 The anchored strip's height, and whether it anchors at all under dense

| Source | Decision |
|---|---|
| `Anchoring beyond charts - discussion.md`, new "what changes visually, per family" section | "The whole visual story is two sizes: **30px where the lane is a card title** (chart, table family, calendar) and **24px `--inet-control-height-sm` where it is a header** (selection family, and every title-hidden overlay)" |
| Shipped code | `GuiTool.MINI_TOOLBAR_HEIGHT_MODERN = 24` for every anchored type, pinned at `mini-toolbar.component.scss:82` as a hardcoded `height: 24px` |
| `Visualization Widget Spec.dc.html` §09 | "Three shipped steps used deliberately: 30px `--inet-control-height-md` in the title lane, 24px `-sm` for the overlaid strip, 44px touch floor" |

**The reading.** The chart-card document and the widget spec agree on the *ladder* and disagree with the
code on which rung the anchored strip takes. The widget spec's §08 step 3 states the intended end state
directly: add a title-height row to the density matrix at **20 / 26 / 30**, gate the anchored strip to
**compact and above** with **dense falling back to the existing hover-revealed overlay**, and replace the
hardcoded `height: 24px` with `--inet-control-height-sm`.

**This reaches code that has already shipped.** Slices 1–3 anchor unconditionally whenever
`GuiTool.isVizModern()` is true, with no density condition anywhere in
`VSObjectContainer.isToolbarAnchored` / `isKebabResident` (`:472-484`). Under dense — which is today's
default density — the shipped behaviour is the one the widget spec decides against.

**That is not a complication.** `viz-updates` is an integration branch that has never shipped to
customers and is not merged into `epic-74519` until the initiative is complete, so revising an earlier
slice is ordinary work on this branch. No compatibility shim, and no reason to scope the density
condition to new slices only — apply it across all six anchored types at once, or the product carries two
anchoring rules for as long as the split lasts.

**Effect on the plan.** This, not the seed mark, is what stands between the current state and the
container/calendar slice; the widget spec says so in the same breath ("unblocks the chart card's
eight-assembly rollout"). Schedule it before slice 4, covering the shipped slices in the same change. The
`height: 24px` → `--inet-control-height-sm` swap is mechanical and rides along.

**Confirm it:**
```bash
cd community/web/projects/portal/src && \
  sed -n '80,84p' app/vsobjects/objects/mini-toolbar/mini-toolbar.component.scss && \
  sed -n '472,484p' app/vsobjects/objects/vs-object-container.component.ts
```

---

### 3.2 The seed mark blocks release, not the rollout — and the two new documents disagree about which

| Source | Decision |
|---|---|
| `Open items - handoff.md`, dependency diagram and step 3c | `M. Seed mark ──→ F. Eight-assembly rollout`, "must land first." "Skip the mark and those assemblies ship with no reversibility record — the exact partial-reversal state the mark exists to prevent, and unrecoverable after release. Check the mark's status with that project before starting F" |
| `Visualization Widget Spec.dc.html` §08 | Step 3 is the title lane, and it is what "unblocks the chart card's eight-assembly rollout." Step 4 is the seed mark: "decided, unblocked, and a ship condition — the branch does not release without it." The rollout is not mentioned in step 4 |

**What the mark is.** A tri-state field in `VSAssemblyInfo`'s existing `writeAttributes`/`parseAttributes`
recording the gate value in force when `setDefaultFormat` ran — `gate-on`, `gate-off`, or `before gate`.
It exists to make **persisted format seeding** reversible: `VSObjectChromeDefaults` is the one resolver of
six that writes at creation rather than at read, so a gate-off product cannot currently un-write the frame
colour and card background it seeded while the gate was on. It is decided, and it is not built — there is
no such field in `VSAssemblyInfo`, and the only shipped seeding state is the corner-specific
`PlotDescriptor.modernCornerSeed` plus `isCornerSeedTarget()`.

**Why the handoff's version is wrong.** The anchoring rollout writes no persisted state. Every file in
all three shipped slices — `67c486d67`, `a4cd1e362`, `a038a30b5` — is under `web/projects/portal/src`;
there is no Java, no `VSAssemblyInfo`, no `setDefaultFormat` path. The rollout is render-time gating on
`.viz-modern`, and turning the gate off restores the floating strip immediately with nothing left behind.
There is no reversibility record to miss because nothing is recorded. Slice 3 shipping without the mark
was not an oversight.

**Confirm it:**
```bash
cd community && git show --stat 67c486d67 a4cd1e362 a038a30b5 | grep -v "^ web/projects/portal/src" | grep "|"
```
(expects no output — every changed file is under the portal source tree)

**Effect on the plan.** Delete the `M → F` arrow. Keep M, correctly placed: it gates release, and it gates
the two workstreams that do write the DEFAULT tier — the widget spec's §04 density heights and §07 derived
selection fill. Anyone starting the container or calendar slice should be reading §3.1, not chasing the
mark's status.

---

### 3.3 The card radius the branch seeds is decided to change, and the sentinel it relies on is decided to go

**Where:** `Visualization Widget Spec.dc.html` §03, against shipped Java.

**What the code says.** `VSObjectChromeDefaults.java:129` sets `CARD_CORNER_RADIUS = 12`, and
`resolveSeededCorner(int radius)` at `:79-80` reads `radius == CARD_CORNER_RADIUS && !isModern() ? 0 :
radius` — a value test that strips the seeded radius when the gate is off.

**What the widget spec decides.** The radius becomes **6px** (`--inet-radius-xl`), and
`resolveSeededCorner()` is **retired rather than re-pointed**. Its reasoning is worth preserving because
it is not obvious: the value test works today only because 12 has no legitimate collision, and the same
off-the-scale property that makes 12 wrong as a card radius is what makes it safe as a sentinel. 6px has
the opposite character — `setDefaultFormat`'s own `format.css` TableStyle lookup can write it into the
DEFAULT tier, so a 6px test would strip a customer's stylesheet radius. The mark replaces the test.

**Why it lands here.** The chart card spec fixes 6px for the same card, the shell ticket pulls the card
tooltip onto it, and the anchored toolbar pill already uses it. The chart-card set does not record that
the shipped seed disagrees with all three.

**Effect on the plan.** Sequenced with the seed mark (§3.2), not before it — retiring the sentinel
without the mark leaves the gate with no reversal path at all. Note the coupling for whoever implements
it: `getRoundCorner()` currently reaches `SreeEnv` only by routing through `resolveSeededCorner()` to
`isModern()`, so retiring the test also removes two org-scoped property reads from a format getter that
runs in loops.

---

### 3.4 Two selection idioms, in two token layers, on one gate

**Where:** `Visualization Widget Spec.dc.html` §09, against `_viz-tokens.scss` and the chart-card
selection decision that shipped in `052fe61f1`.

**What the code says.** Both are live simultaneously. The chart-card vocabulary: fill
`--inet-focus-ring-color` (`_variables.scss:539`, `rgba(229, 138, 42, 0.28)` — primary at 28%), stroke
`--inet-primary-color`. The viz layer: `--inet-viz-selected-bg-modern: #DDF1F5`,
`--inet-viz-selected-text-modern: #123C44`, `--inet-viz-selected-border-modern: #BFDDE5`
(`_viz-tokens.scss:51-53`), described in that file as "kept distinct from shell/Composer selection."

A chart and a table side by side render selection in orange and teal respectively — reinstating exactly
the multiple-idiom problem the chart-card decision was taken to end.

**Narrower than it first reads.** `_viz-tokens.scss:37-38` and `:93` already define
`--inet-viz-selected-border` and `--inet-viz-active-border` as `var(--inet-primary-color)`, so the borders
match the chart's stroke today. Only the fills diverge.

**What the widget spec decides.** §07 retires the teal family and derives selection from
`--inet-primary-color` at 28% flattened against the assembly background, carrying the two values in the
§03 re-seed rather than adding a sixth resolver — and ships it as one changeset with the chart's selection
vocabulary, "or the product carries two selection idioms in one dashboard."

**Effect on the plan.** The chart-card set records the selection decision as shipped and closed. It is
shipped for chart-owned surfaces and open for table, selection and calendar surfaces, which are
server-rendered. Add it to the plan as an open item under the widget spec's ownership, and note that it is
export-affecting — it needs the manual pass, which is the same pass §3.3 and the calendar branch need.

---

## 4. Gaps

### 4.1 The dark gap is real, but it is browser-DOM only — the chart's server-rendered half already has dark values

**Where:** `Open items - handoff.md`, "Two decisions this ticket can't make alone," which asks for a
decision on whether dark is in scope for the chart pilot.

**What the code says.** Dark is not a future state to opt into, and the chart is not outside it.
`3e7e52626` (2026-07-28, "Visualization Phase 9B: org-scoped dark mode") landed a full dark treatment
across every **server-rendered** visualization surface, gated on
`VSDensityDefaults.isDark()` — `isModern()` and the new org-scoped `viewsheet.darkMode` property. For the
chart card specifically it covers:

| Chart card section | Dark values shipped by 9B |
|---|---|
| §08 GDefaults greys | `VSChartChromeDefaults` — `GRIDLINE_DARK 0x3A383D`, `LABEL_DARK 0xCAC4D0`, `TITLE_DARK 0xE6E0E9`, applied through `resolveAxisLineColor()` / `resolveGridlineColor()` |
| §05 legend | `legendBackground()` → `LEGEND_BG_DARK 0x252428`, seeded via `LegendDescriptor` / `LegendsDescriptor` |
| §01 title band | `VSTitleChromeDefaults` — `TITLE_BG_DARK`, `TITLE_FG_DARK`, `TITLE_BORDER_DARK` |
| The card itself | `VSObjectChromeDefaults` — object border and page background; series colour via `VSChartPaletteDefaults` |

**The gap is what 9B deliberately scoped out.** Its browser-DOM half is narrow and says so: a `.viz-dark`
block in `_viz-tokens.scss` that redefines only `--inet-viz-*` tokens — the data-grid state overlays —
plus three named exceptions outside it (`vs-slider`, the empty-image placeholder, the table header sort
button in `_themeable.scss:1269`). **No shell neutral is redefined anywhere under `.viz-dark`.**

And the chart card spec's own contribution is almost entirely browser-DOM. What that leaves unconverted:

- **The anchored strip** — `background-color: var(--inet-shell-surface-default)`
  (`mini-toolbar.component.scss:31`), which has one definition and no dark variant, so a light pill sits
  on a server-darkened card. §02 and §03 both.
- **Every tooltip, including the CARD ramp** — `plain-tooltip-surface` binds `--inet-text-color`,
  `--inet-dialog-bg-color`, `--inet-default-border-color` and `--inet-shadow-low`
  (`_directives.scss:215-229`); none of them flip. §07.
- **Chart selection** — `.viz-modern .chart-object-canvas` binds `--inet-focus-ring-color` and
  `--inet-primary-color` (`_themeable.scss:1410-1413`). Brand accents, so not broken, but the 28% fill was
  calibrated against a light plot.
- **Nav bar, Resize Plot sliders, annotation selection border** — no `.viz-dark` rule in any of them.

**Confirm it:**
```bash
cd community/web/projects/portal/src && \
  sed -n '140,159p' scss/_viz-tokens.scss && \
  grep -rn "viz-dark" scss/ app/ --include=*.scss | grep -v _viz-tokens.scss
```

**So the handoff's framing is too broad.** "This chart card spec has no dark values at all… a dashboard
in dark mode would ship modern widgets next to an unconverted chart" was already false when it was
written — the chart's chrome, legend, title band and palette had been dark-aware for three weeks. The
real decision is smaller and more answerable: **do the chart card's browser-DOM surfaces take dark, or is
dark explicitly deferred for them?** Four surfaces, one gate, no server or export involvement.

**Decided 2026-08-11 — in scope, implement it.** See
[chart-card-open-item-decisions.md](./chart-card-open-item-decisions.md) §2 for the four surfaces, the
choice between per-surface `.viz-dark` rules and dark-aware shell neutrals, and the note that the 28%
selection fill was calibrated over a light plot.

**The handoff's other stated gap is smaller than it says.** "No alignment-anchor model" — the widget spec
§09 supplies the missing rule: three shipped control-height steps used deliberately (30 `-md` in a title
lane, 24 `-sm` overlaid, 44 touch floor), plus the title-lane height row at 20/26/30 in §08 step 3. That
is the model the handoff says does not exist. What remains genuinely underived is the selection family's
lane, which is `titleRatio` of the assembly rather than a fixed step — and slice 3 shipped without
resolving it, by making the kebab the whole strip at any width.

**Effect on the plan.** Dark for the chart card stays an open decision and should be recorded as one —
deferring is fine, leaving it unstated is not, and the handoff is right about that. The anchor-model gap
can be closed by citing §09 rather than by new work.

---

## 5. Regressions from the last audit

Three claims corrected in the 2026-08-05 edition of this file returned in the 2026-08-11 sync. In each
case the sync re-derived the claim from the branch and reached the reading this file had already ruled
out. None of the three is a new error; all three are the same error a second time.

### 5.1 "That CSS branch had never executed in production"

**Where it returned:** `Open items - handoff.md` §2, in the bullet making the manual pass mandatory:
"that CSS branch had never executed in production before this shipped, so this is the first time it has."

**Why it is wrong** — unchanged from §1.1 of the previous edition. `_themeable.scss:1401` sets both
`color` and `border-color` on `.chart-object-canvas`, globally, on exactly the class the
`getComputedStyle` branch reads. The branch has always executed. The reading survives because the values
in that rule are identical to the TypeScript defaults at `chart-tool.ts:778-779`, so no observation
distinguishes the two.

**Confirm it:**
```bash
cd community/web/projects/portal/src && grep -n "chart-object-canvas" scss/_themeable.scss
```

**Effect on the plan.** The manual pass is still worth running, but for item 7's fill/stroke split, not
because a dead branch is executing for the first time. The risk framing should not be used to schedule it.

---

### 5.2 "Item 12 is still open — the data tips sit two orders of magnitude below the dropdowns"

**Where it returned:** `Open items - handoff.md` step 1c and `Shell surfaces - ticket.md`'s sync note,
both stating that a "ceiling, not a registration" landed and "the underlying bug… is still open."

**What is right about it.** The observation is accurate: two of the three assignment paths still bypass
the registry. `vs-pop-component.directive.ts:314,317` still sets `this.popZIndex + 99998` / `+ 99999`
inline, and the scrim and source are still assigned at runtime in `date-tip-helper.ts`.

**What is wrong about it.** The premise — that sitting below the dropdown layer is a bug — was corrected
in §1.2 of the previous edition and remains corrected. A dropdown opened inside a data tip must render
above it; the relative order was already right, and the defect was that it was accidental rather than
declared. The clamp the sync reads as an incomplete fix is the deliberate one: `POP_UP_CONTENT_MAX_ZINDEX
= 999899` exists so a large natural z-index cannot carry pop content over `.fixed-dropdown`, with a unit
test asserting it across naturals from 0 to 100000.

**The shipped code already says all of this.** `_directives.scss:20-29` documents the registry's scope in
seven lines, ending "The pop directive's and data-tip's own z-index offsets still assign directly and are
not governed by this registry. Change both together." `date-tip-helper.ts:20-27` mirrors it, including the
effective order. The remaining work is recorded in the code as a known partial state, not hidden.

**Confirm it:**
```bash
cd community/web/projects/portal/src && \
  sed -n '20,36p' scss/internal/_directives.scss && \
  sed -n '18,40p' app/vsobjects/objects/data-tip/date-tip-helper.ts
```

**Effect on the plan.** Item 12 keeps its downgraded value claim. It is a maintainability item — bring the
two remaining paths under the registry — and it is not a reason to sequence it ahead of the colour work.
The instruction "do not let this wait behind the colour work" should be dropped.

---

### 5.3 The 30px strip

**Where it returned:** the anchoring document's new per-family section, "30px where the lane is a card
title."

**Why it is wrong** — unchanged from §2.3 of the previous edition, which recorded that the strip is
already 24px under the gate and that the plan deliberately dropped §06's comfortable row, collapsing a
four-row ladder to three. The August audit's reasoning still holds: §06 already specifies that collapse
for the title-hidden case, so one ladder serves both placements.

**Confirm it:**
```bash
cd community/web/projects/portal/src && sed -n '58,75p' app/common/util/gui-tool.ts
```

**Effect on the plan.** See §3.1 — the widget spec supplies the resolution, and it is not "30px for card
titles." The strip is `-sm` at 24px; 30px `-md` is the *lane*, not the strip in it.

---

### Why this section exists

The three regressions share one cause. The sync was performed by reading the branch, which is the right
primary source and was the right instruction. It was not performed against this file, so every conclusion
this file had already overturned was available to be re-derived from the same evidence that produced it
the first time — and in all three cases the evidence is genuinely ambiguous without the correction:
identical colour values, a correct-but-undeclared stacking order, and a ladder whose top rung was dropped
by decision rather than by omission.

The fix is cheap and belongs in the source set rather than here: `README.md`'s "changed since the last
handoff" section should name this file as required reading for the next sync, and the next sync should
diff its conclusions against it before publishing. A document set that is re-synced on a fast-moving
branch will keep regenerating these unless something points at the corrections.

---

## Appendix — what was checked and held

Verified this pass against `a038a30b5`. Counts were run, not sampled.

- `ANCHORED_ASSEMBLY_TYPES` contents and the slice-3 comment — `mini-toolbar.service.ts:41-53`. Confirmed.
- `stableFirst` gated toolbar ordering in all four action files — `chart-actions.ts:568,580`,
  `table-actions.ts:322,326`, `crosstab-actions.ts:382,392`, `calc-table-actions.ts:366,370`. Confirmed.
- `VSObjectContainer.rightEdgeReserve` and `SORT_CONTROL_RESERVE = 22`, with the selection-family
  exemption — `:585-600`. Confirmed.
- `isToolbarAnchored` / `isKebabResident` read `GuiTool.isVizModern()` and the type set, with no density
  condition — `:472-484`. Confirmed.
- `GuiTool.MINI_TOOLBAR_HEIGHT = 28`, `MINI_TOOLBAR_HEIGHT_MODERN = 24`, `getMiniToolbarHeight()` —
  `gui-tool.ts:58-75`. Confirmed.
- The tooltip mixin's excluded `max-width`/`max-height`, and their re-declaration on
  `.widget__default-tooltip` only — `_directives.scss:215,236-237`. Confirmed; this closes old §1.3.
- `--inet-font-size-lg: 16px`, `--inet-control-height-sm/-md/-lg` at 24/30/36, `--inet-control-height-touch:
  44px`, `--inet-focus-ring-color: rgba(229, 138, 42, 0.28)` — `_variables.scss:239,475-477,480,539`.
  Confirmed.
- The annotation selection border under `.viz-modern`, with the comment naming the retired third idiom —
  `vs-annotation.component.scss:98-104`. Confirmed, matching the decision exactly.
- `VSTitleChromeDefaults` `TITLE_BG/FG/BORDER` at `0xF1EFEA / 0x6A685F / 0xD9D5CC` plus dark variants —
  `:143-149`. Confirmed; old §3.1 is still open.
- `VSObjectChromeDefaults.CARD_CORNER_RADIUS = 12` and `resolveSeededCorner()` — `:79-80,129`. Confirmed.
- `VSAssemblyInfo.isCornerSeedTarget()`'s explicit positive list, and the comments explaining the range
  slider's exclusion and the calendar's absence — `:1227-1239`. Confirmed, and it matches the widget
  spec's eight-type population exactly.
- No tri-state provenance field anywhere in `VSAssemblyInfo`, and no `modernSeeded`/`legacySeeded`
  identifier in any `.java` or `.ts` file. Confirmed absent.
- `vs-chart.component.scss` untouched since `e8df3491b` (2024-07-15); 15 lines matching `-ms-`. Item 13 is
  exactly as described, and the sync's "not in the diff — still open" is right.
- `chart-nav-bar.component.scss` still carries `z-index: 9999` (`:20`) and the off-scale `border-radius:
  5px` (`:26,32`). Both cleanups still outstanding, as the sync says.
- `SVGUtil.getSVGDocument()` returns `new SVGGraphics2D(ctx, true)` — `SVGUtil.java:55-63`. The outlined
  text ticket's "a single global boolean at one choke point" is accurate; the boolean is the positional
  second argument, which is why it does not grep as `textAsShapes`.
- `_viz-tokens.scss` declares one font-size token, `--inet-viz-font-size`, and no chart interior sizes.
  The overlap flagged with `Chart type sizes - ticket.md` does not exist as described.

### Not checked this pass

- The widget spec's §04 density matrix values and §05 per-widget literal inventories.
- The range slider's three PNGs and the `granite/timeslider/` painter set — but the divergence question
  the widget spec §08 left open by name is now answered: the painter is redrawn with the CSS control. See
  [chart-card-open-item-decisions.md](./chart-card-open-item-decisions.md) §3, which carries the scope on
  both sides and the sequencing note that its range band waits on §07, which waits on the seed mark.
- `Dead menu icons - ticket.md`'s "~50 unreachable `icon()` declarations" — unchanged since the previous
  set and not re-counted. Given §1.3, treat the number as unverified.
- Whether the live viewer renders gauges and thermometers through the painter or the DOM — the widget
  spec flags this as unverified and it remains so.
