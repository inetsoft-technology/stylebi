# Geometric Strip Suppression (L″) — Design

**Date:** 2026-08-25
**Covers:** replacing the density approximation that decides whether an anchored mini-toolbar is drawn
with a measurement of the assembly's actual title lane
**Verified against:** community `viz-updates` @ `2eb67ba07` (L′, the title lane height row). Every line
cited below was read at that commit.
**Implements:** [the strip and lane decisions](./lookfeel/chart-card-anchored-strip-lane-decisions.md)
decisions 2, 3 and 4.
**Depends on:** L′, shipped. The browser could not measure a lane before it; see §2.

**Scope: geometry only.** Two adjacent questions are deliberately excluded and tracked separately on the
roadmap's "Ready now" table — §10.1 (resting visibility by pointer capability) and §10.2 (the seamless
in-lane strip). §6 says why folding either in would be wrong.

---

## 1. What is being built

One question replaces a guess. Today the code asks *is the org's density at least compact*; it will ask
*does this assembly's lane actually hold the strip*:

```
lane = titleVisible ? titleFormat.height : 0

lane >= 24   →  anchored strip drawn
lane <  24   →  no chrome at all, right-click only
```

**24 = the strip's own height**, so a lane of 24 holds it exactly. **Corrected 2026-08-25, after the
threshold shipped at 26 and was reviewed: 26 was wrong.** Decision 2 chose it to leave 1px of clearance
above and below, and its only stated reason was that at 24 "the pill's border would touch the lane's
edges". §10.2 deletes that border and surface outright — it replaces the white bordered box with two glyph
tones on the card — so the clearance was protecting something already scheduled for removal. Meanwhile the
strip's 18px glyph inside its 24px control already supplies 3px of breathing room, and every assembly with
a 24px or 25px lane was losing its toolbar entirely while the strip physically fitted.

Three consequences, all of them improvements. The density tiers do not move: dense 20 stays suppressed
(decision 4 intact), compact 26 and comfortable 30 stay anchored. The population that gains is exactly the
one the review flagged — author-set lanes of 24 and 25. And **the zero-margin risk in §5.1 dissolves**:
compact's 26 against a 24 threshold carries 2px of margin rather than none, so the rounding hazard is no
longer load-bearing.

Accepted cost, resolved when §10.2 lands: at exactly 24 today the pill's 1px border sits flush against the
lane edge. Cosmetically tight, functional, and strictly better than drawing no toolbar at all.

The strip is 24px at every density — it binds `--inet-control-height-sm` (`_variables.scss:536`), which
`mini-toolbar.component.scss:82` already does, and its glyph is `.icon-size-small`, 18px
(`_icons.scss:75-76`).

The density test was always a stand-in. `mini-toolbar.service.ts:69` says so in as many words: *"fit is
approximated by density for now."* Both predicates carry `TEMPORARY` in their doc comments.

### Why a hidden title needs no special case

Decision 4: *a lane that cannot contain the strip draws no chrome, and a hidden title is one such lane.*
A hidden title yields `lane = 0`, which fails the same comparison. One rule covers both populations, and
the title-hidden behaviour falls out rather than being bolted on.

---

## 2. Why this is possible now and was not before

The predicate needs the assembly's real lane height in the browser. It has one only because L′ shipped.

All three model families set the title format's height from `getTitleHeight()`:

| Model | Line | What it does |
|---|---|---|
| `VSCompositeModel` (selection list, selection tree) | `:43-44` | `new Dimension(width, assemblyInfo.getTitleHeight())` then `titleFormat.setPositions(...)` |
| `VSChartModel` | `:62` | `int titleHeight = info.getTitleHeight();` |
| `BaseTableModel` (table, crosstab, calc table) | `:79` | `int titleHeight = tinfo.getTitleHeight();` |

L′ made `getTitleHeight()` resolve to 20 / 26 / 30 for a marked assembly. **Before L′ it returned 20 for
everything**, so `20 >= 24` would have been false for every assembly at every density and this change would
have stripped the toolbar from the entire anchored set. That is precisely the hazard
[decision 3](./lookfeel/chart-card-anchored-strip-lane-decisions.md) encodes as a one-directional
sequencing rule, and it is why L″ could not ship first.

**Coverage is complete for the anchored set.** `ANCHORED_ASSEMBLY_TYPES`
(`mini-toolbar.service.ts:41-53`) holds six types: `vschart` → `VSChartModel`; `vstable`, `vscrosstab`,
`vscalctable` → `BaseTableModel`; `vsselectionlist`, `vsselectiontree` → `VSSelectionBaseModel extends
VSCompositeModel`. Every one carries `titleFormat`.

**The lane height is not self-zeroing when the title is hidden.** `titleFormat.height` is set from
`getTitleHeight()` regardless of `titleVisible`, which is a separate field. The existing code already
handles this the way this design will: `vs-object-container.component.ts:994` computes
`chart.titleVisible ? chart.titleFormat.height : 0`. This is an existing idiom, not a new one.

---

## 3. Architecture

### 3.1 The predicates take a lane, not a density

```ts
export function isAnchoredResident(objectType: string, vizModern: boolean, laneHeight: number): boolean
export function isAnchoredChromeSuppressed(objectType: string, vizModern: boolean, laneHeight: number): boolean
```

They stay pure functions over primitives — the shape they have today — rather than taking a model. A
single exported helper converts a model to a lane so the `titleVisible ? height : 0` expression exists in
one place:

```ts
export function anchoredLaneHeight(model: VSObjectModel): number
```

**Why a number rather than the model.** Passing the model would couple the service to the model type for
no gain, and would make the predicates untestable without constructing one. A number keeps the boundary
test — the thing most worth covering, per §5 — a one-line call.

### 3.2 The three call sites

| File | Line | Current | Becomes |
|---|---|---|---|
| `abstract-vs-actions.ts` | `:143` | `isAnchoredResident(this.model.objectType, this.model.vizModern)` | adds `anchoredLaneHeight(this.model)` |
| `abstract-vs-actions.ts` | `:222` | `isAnchoredChromeSuppressed(this.model.objectType, this.model.vizModern)` | adds `anchoredLaneHeight(this.model)` |
| `vs-object-container.component.ts` | `:486` | `isAnchoredResident(object.objectType, object.vizModern)` | adds `anchoredLaneHeight(object)` |

`vs-object-container.component.ts:473` (`isAnchoredKebab`) and the template's
`[residentKebab]="isKebabResident(vsObject)"` at `:358` reach the predicate through `:486` and need no
edit of their own.

### 3.3 What retires

- **`GuiTool.isVizDensityAtLeastCompact()`** (`gui-tool.ts:96`). Verified: these two predicates are its
  only consumers repo-wide. It goes with them.
- **`rightEdgeReserve()`** and **`SORT_CONTROL_RESERVE`** (`vs-object-container.component.ts:588-603`).
  `SORT_CONTROL_RESERVE` is 22 — "20px control + its 2px inset" — reserved at the right edge of a
  title-hidden table so the overlaid kebab misses the last column header's sort control. Under this design
  a title-hidden assembly draws no kebab, so there is nothing to miss. Its one consumer is
  `getAnchoredToolbarWidth()` at `:570`, which loses the subtraction. **Title-hidden tables get 22px of
  plot corner back.** Selection lists and trees were already exempt.

### 3.4 What does NOT retire, despite what the comments say

`ANCHORED_ASSEMBLY_TYPES` and the two `TEMPORARY` wrappers — `AbstractVSActions.resident` (`:139-143`) and
`VSObjectContainerComponent.isKebabResident` (`:485-487`) — carry comments saying they are "deleted
together with this predicate." **That refers to the type set, not to this change.** Reading
`ANCHORED_ASSEMBLY_TYPES`' own comment, the set exists because slices 4 and 5 (the container and the
calendar) have not joined the anchored family yet; it retires when they do, which is a separate roadmap
item. An implementer following the comment literally would delete a type gate that six of eight types
still need.

---

## 4. The title-hidden reversal, and what it costs

Decision 4 reverses a shipped decision, knowingly and by name. It cites
`chart-card-slice2-tables-design.md:73` ("Title-hidden assemblies reserve nothing (Case 1). The strip
overlays the content's top-right"), `:93`, `chart-card-slice3-selection-design.md:75`, and
`Chart Card Spec v3.dc.html` §03 — which is titled *"Title hidden — overlay, never reserve"* and specifies
the strip floating over the plot's top-right on its own small surface.

**Decided 2026-08-25: take the reversal.** A title-hidden assembly draws no chrome and is reached by
right-click.

**The cost, stated plainly.** Authors who hide titles — common on charts whose title duplicates a
dashboard heading — lose a hover toolbar they have today. Right-click still reaches every action, and the
kebab menu and the right-click menu have always shown an identical list (§02).

**One inconsistency this exposes rather than creates.** §03 and slice 2 both say a title-hidden assembly
reserves *nothing*, yet the shipped code reserves 22px (`:599`). The design text and the code have
disagreed since slice 2. This change resolves it in the direction both documents wanted for the reserve,
while going further than either on the strip.

**Rejected — keep the overlaid strip on title-hidden assemblies.** It preserves two placements for one
control, which decision 4 exists to end: *"anchoring and floating are two placements of a control that is
not drawn here either way."*

---

## 5. Verification

### 5.1 The risk worth designing a test around

**Superseded 2026-08-25 by the threshold dropping to 24 — compact's 26 now carries 2px of margin, not
zero.** The rounding fix below shipped anyway and is kept: `anchoredLaneHeight` rounds, because the
composer's drag path feeds `getBoundingClientRect()` heights in and a lane dragged to a value can arrive
fractional. It is no longer the difference between a toolbar and no toolbar at compact. The original
reasoning follows, because it is why the boundary is tested at all.

**Compact's lane is 26 and the threshold is 26 — exactly zero margin.** If `titleFormat.height` is ever a
pixel short at compact through rounding, a CSS-defined title height, or a scaled layout, compact loses its
toolbar entirely and silently. This is the one failure mode that would look like the feature simply not
working.

Test the boundary explicitly at 23 / 24 / 25 rather than trusting the arithmetic, and treat compact as a
required manual check rather than an inference from dense and comfortable passing.

### 5.2 Unit tests — `mini-toolbar.service.spec.ts` (exists)

| Case | Expectation |
|---|---|
| lane 23, 24, 25 on an anchored type, marked | suppressed / resident / resident |
| lane 20 (dense) | suppressed |
| lane 30 (comfortable) | resident |
| `titleVisible: false` with a 30px `titleFormat.height` | lane resolves 0, suppressed |
| non-anchored type at lane 30 | neither resident nor suppressed |
| `vizModern: false` at lane 30 | neither resident nor suppressed |

The last two matter: `isAnchoredChromeSuppressed` must stay a *separate* predicate rather than
`!isAnchoredResident`, because negation would be true for every non-anchored type and every gate-off
assembly, stripping toolbars users have today. Its doc comment says so; the tests should hold it.

### 5.3 The snapshot risk

`abstract-vs-actions.ts:222` reaches `ToolbarActionsHandler.copyActions([], this.showing)` — suppression
empties the action list. The `*-actions.spec.ts.snap` snapshots capture action lists, so **a fixture whose
model happens to change suppression state under the new rule will move its snapshot.** Six of those
snapshots already showed spurious churn during L′ and were deliberately excluded from that commit; do not
confuse the two. Any snapshot that moves here needs its fixture read before the update is accepted.

### 5.4 Manual pass

Browser-only. **No export pass, no persisted state, no migration, no reversibility question** — the whole
change lives under `web/projects/portal/src`.

1. A marked chart at dense / compact / comfortable: no strip / strip / strip.
2. The other five anchored types at compact: strip present.
3. Compact specifically, per §5.1.
4. A marked chart with its title hidden: no strip, and right-click reaches the same actions the strip
   offered.
5. A title-hidden **table**: no strip, and the last column header's sort control sits where it should now
   that the 22px reserve is gone.
6. An unmarked assembly at every density: unchanged from today.
7. A non-anchored type (gauge, text): unchanged.

---

## 6. Out of scope, and why each would be wrong to fold in

**§10.1 — resting visibility by pointer capability.** `Chart Card Spec v3.dc.html` §02: *"On touch the
kebab is drawn at rest; on a pointer device the lane is empty until hover or focus."* Today
`isAnchoredResident` decides resting from **density**, conflating two orthogonal questions: *does the lane
hold the strip* (this design) and *is it drawn at rest* (pointer capability). This change answers the first
and leaves the second, so afterwards the predicate still derives resting from geometry rather than the
pointer. Both would edit the same predicate, so shipping them together would make it impossible to tell
which rule caused a behaviour change. Tracked on the roadmap.

**§10.2 — the seamless in-lane strip.** The anchored strip should draw no surface of its own, with two
glyph tones derived from the card background's luminance. Pure appearance; zero overlap with geometry.
Note that §10.2's stated precondition is wrong about this code — see
[the corrections doc](./lookfeel/chart-card-source-doc-corrections.md) §1.5. Tracked on the roadmap.

**§02's six scoped edits** — cap the strip at three actions plus the kebab, reorder `createToolbarActions`
so "first three visible" is stable. Toolbar composition, not placement.

**Touch. Scoping it out is correct, and the reason this section originally gave was false.** The original
text said "the gap is real but pre-existing — the density approximation already produces it at dense". There
is no gap. **Mobile does not use this mechanism at all**, which was established from the running product and
then verified in three places:

- `mini-toolbar.component.html:35` wraps the action buttons in `@if (!mobileDevice)`, so the anchored strip's
  buttons never render on mobile, lane or no lane.
- `viewer-app.component.html:304-306` renders a dedicated `<viewer-mobile-toolbar [actions]="selectedActions">`
  with `(closeMobileToolbar)="clearSelectedAssemblies(null, true)"` — a **page-level** strip fed the selected
  assembly's actions.
- `viewer-app.component.html:61-65` gives `.viewer-toolbar` its own `[class.mobile]` variant, so that toolbar
  is a first-class separate mechanism rather than a fallback.

So on touch the route to an assembly's actions is *select → page-level toolbar*, and it has never depended on
the title lane. Nothing about this change reaches it.

**This matters beyond a corrected sentence.** A whole-branch review built a Critical finding on the original
premise — that a title-hidden marked assembly on touch loses its only route to actions — and recommended
keeping a resident kebab regardless of lane. Acting on that would have added a second, competing action route
to a platform that already has one. The false premise was mine, and it survived three reviews because every
reviewer reasoned from the predicate chain rather than from whether the platform uses the mechanism.

The 44px touch target against a short lane remains unsettled in every document, and remains irrelevant here
for the same reason: no in-lane control is drawn on touch.

---

## 7. Open questions

- **Is 1px of clearance enough at compact?** Listed as open in the strip and lane decisions. It is what a
  26px lane leaves around a 24px strip, and the widget spec chose 26 for exactly that fit. **Partly answered
  2026-08-25 by the threshold dropping to 24:** the question of whether 1px is *enough* no longer gates
  whether a strip is drawn, because 1px of clearance is no longer required for one. What remains is the
  narrower aesthetic question of how a strip looks in a lane that holds it exactly — which §10.2's removal of
  the pill's surface largely dissolves too. Judge it at
  render during §5.4 step 3; the lever is compact's lane row, not the strip.
- **Two clearance values now coexist in `abstract-vs-actions.ts`.** `ACTION_FLOOR` is 32 — a 24px control
  needing 4px above and below against the **card height** — while this design's lane threshold is 24, a
  24px control needing 1px above and below against the **lane height**. They measure different things and
  both are defensible, but the file will carry both numbers. Worth one comment saying which is which.
- **`--inet-viz-chrome-row-height` is 22px at dense** (`_viz-tokens.scss:119`) while the dense lane is 20px.
  Still no consumers, so nothing disagrees yet. If the threshold were ever derived from a token rather than
  a constant, this is the token that would tempt someone, and it is the wrong one.
