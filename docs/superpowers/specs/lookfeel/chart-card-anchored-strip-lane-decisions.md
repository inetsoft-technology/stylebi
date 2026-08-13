# The Anchored Strip and the Title Lane — Decisions

**Date:** 2026-08-13 (decisions 1–4); decision 5 added later the same day, after `userTitleHeight` was
built and surfaced two problems the lane row inherits
**Verified against:** community `viz-updates` @ `881a9b049` for decisions 1–4, plus the twelve
density-gating files then uncommitted and since shipped as `55c3bad1a`. Decision 5 is verified against the
`userTitleHeight` work in the working tree, which is complete and reviewed but not yet committed
**Decides:** which v3 document governs the title lane, how the anchored strip relates to it, what
suppresses the strip, and how an author tells the lane row to leave a title height alone
**Supersedes:** `chart-card-design3/Chart Card Spec v3.dc.html` §04's lane model, for the anchored strip

## Why this file exists

The two v3 documents specify different title lanes, and the difference is exactly the number the
anchored strip needs. `Visualization Widget Spec.dc.html` §05 gives the lane its own density row and
sizes it to clear a fixed strip; `Chart Card Spec v3.dc.html` §04 derives the lane from its contents and
lets a larger strip drive it. Both are current, both are cited elsewhere as authoritative, and nothing
in the tree said which one an implementer should follow.

It is deliberately not written inside `chart-card-design3/`. That set is regenerated wholesale on each
sync, so a decision recorded there survives at most one pass. See
[chart-card-source-doc-corrections.md](./chart-card-source-doc-corrections.md).

Each decision states the question, the answer, what it costs, and what it obliges someone else to know.
Rejected alternatives are kept so a reviewer arriving cold does not have to reconstruct them.

---

## The model in one line

**Density sets default sizes. The lane's actual height decides whether the strip is drawn.**

Density changes the default title height, the default control sizes and the default row heights. It is
not itself a condition anywhere in the strip's logic — it reaches the strip only through the lane it
produces. Everything below follows from that.

---

## Decision 1 — the Visualization Widget Spec governs the lane

**The question.** Two lane models, both from the 2026-08-12 v3 sync:

| | Widget Spec §04/§05 | Chart Card Spec v3 §03/§04 |
|---|---|---|
| Lane height | its own density row: 20 / 26 / 30 | derived: title line box, or the strip, whichever is greater, plus 12px above and 8px below |
| Strip height | 24px, constant | 30px in the lane, 24px overlaid |
| Which drives which | the lane is sized to clear the strip | the strip drives the lane |
| Density | the whole mechanism | not a factor |

They coincide at comfortable (30px) for opposite reasons and with opposite arithmetic — under the chart
card model a 30px strip yields a roughly 50px lane once its gaps are added, not 30px.

**Decided: the widget spec's values.** The lane is a density row at 20 / 26 / 30, dense pinned to
`AssetUtil.defh` so it stays legacy-exact, and compact's 26px clears a 24px strip by construction —
`VSDensityDefaults.titleHeightForMode()` as written in the uncommitted diff.

**What this overrules.** Chart Card Spec v3 §04's "whichever is greater" rule, its 30px in-lane strip,
and the two-target-sizes statement that pairs 30px in the lane with 24px overlaid. §03's description of
the strip as "right-aligned in the lane, sharing it with the title" is unaffected and is the placement
this decision assumes.

**Rejected — the chart card model.** It makes lane height depend on the strip, and therefore on the
button count, the touch target and the theme's control tokens. §04 already flags where that leads:
raising the touch target from 30px to 44px would make every card's lane taller and every plot shorter.
A persisted per-assembly model value should not move because a CSS token did.

---

## Decision 2 — the strip is 24px at every density, contained and vertically centred

**The question.** The widget spec contradicts itself. §04's density matrix carries toolbar and control
height at 24 / 28 / 30 — it lists "the declared toolbar height 30 → 24" as one of dense's four
divergences — while §05's collision math assumes a fixed 24px strip. At compact those disagree: a 28px
strip does not fit a 26px lane.

**Decided: the anchored strip is exempt from the control-height row and stays 24px at every tier.** It
binds `--inet-control-height-sm` (`_variables.scss:475`), which is what
`mini-toolbar.component.scss:82` already does.

The strip is contained by the lane with clearance, not equal to it, and is vertically centred:

| Mode | Lane | Strip | Clearance |
|---|---|---|---|
| dense | 20 | — | does not fit; see decision 4 |
| compact | 26 | 24 | 1px above and below |
| comfortable | 30 | 24 | 3px above and below |

**Why exempt.** The anchored strip is a control living inside another control's lane, not a control in
flow. Scaling both together is what produces the §04/§05 collision: any strip that grows with density
outgrows a lane that grows at the same rate.

**Rejected — the strip matches the lane exactly.** It would make the strip 20 / 26 / 30, and 26px is not
a shipped control step (`--inet-control-height-sm` is 24px, `-md` is 30px). It also leaves zero
clearance, so the pill's border would touch the lane's edges.

**Rejected — the strip follows §04's control-height row.** 28px in a 26px lane at compact, which is the
collision this decision exists to remove.

---

## Decision 3 — suppression is geometric, not density-keyed

**The question.** The uncommitted `isAnchoredChromeSuppressed()` reads the `viz-density-*` body class
through `GuiTool.vizDensityMode()`. It measures no height at all — neither the lane's nor the card's.

**Decided: compare the assembly's actual lane height against the strip.** The strip is drawn when the
lane can contain it with clearance, and suppressed otherwise. Density is not consulted.

**Why the density predicate is wrong, in both directions.** `VSDensityDefaults` is a read-time resolver
that applies only where the assembly still carries the legacy default — an author-set title height keeps
its own value at every density. So:

- dense plus an author-set 40px title: the strip is suppressed although it fits with room to spare;
- comfortable plus an author-set 16px title: the strip is drawn and overhangs into the content.

Both are reachable today by opening the property dialog's Size and Position pane and typing a number.

**The plumbing already exists.** `titleFormat.height` is on the model for the entire anchored set —
`VSChartModel.java:60-63`, `VSCompositeModel.java:39-41` (selection list, tree and container),
`base-table-model.ts:41`, `vs-range-slider-model.ts:39` — and every one of them sources it from
`info.getTitleHeight()`. The browser already knows each assembly's real lane height; nothing new has to
be sent.

**The threshold is 26px** — the 24px strip plus 1px of clearance above and below. That is compact's lane
row exactly, which is what §04 means by "clears the 24px anchored strip by construction."

**What this removes.** `isAnchoredChromeSuppressed()` stops calling `GuiTool.vizDensityMode()`, and
`GuiTool.isVizDensityAtLeastCompact()` loses what is currently its only consumer. Today's dense behaviour
is unchanged in outcome — dense's 20px lane fails the threshold — but it now falls out of the rule rather
than being a rule of its own.

**Implementation note.** `titleFormat` is declared on the five concrete models, not on `VSObjectModel`,
so `AbstractVSActions` needs an accessor or a cast — the same shape as `getToolbarTop()`'s existing
`<VSChartModel>` cast for `paddingTop` (`vs-object-container.component.ts:507`).

**Hard sequencing rule: this must not ship before the lane row.** Every assembly still carrying
`AssetUtil.defh` has a 20px lane at *every* density until decision 1's row is applied, so a 26px threshold
fails everywhere and the strip disappears from the entire anchored set — not just dense. The uncommitted
density-keyed predicate does not have this failure mode, so it ships as the interim and is replaced by
this rule rather than amended into it. The roadmap carries the same rule under its dependency picture.

**Rejected — keep the density predicate and accept the author-height cases.** It is cheaper by one model
read and wrong on any dashboard whose author has ever set a title height, which after fifteen years of
saved content is not an edge case.

---

## Decision 4 — a lane that cannot contain the strip draws no chrome, and a hidden title is one such lane

**Decided.** The strip is drawn when, and only when, the title is visible and its lane is at least 26px.
Otherwise the assembly draws no chrome at all — no strip, no kebab, right-click only — which is the
existing behaviour below the 32px card floor and the behaviour decision 4 of
[chart-card-open-item-decisions.md](./chart-card-open-item-decisions.md) adopted for dense.

**This answers a question Chart Card Spec v3 §04 left open.** §04 observes that `.mini-toolbar` is
`position: absolute` at a computed top, left and width, so it overlays whatever lane the title produces
rather than growing it, and offers two ways out: implement the "whichever is greater" rule in the lane
math, or restate the strip as an overlay and specify the overhang. This takes a third — the lane never
grows for the strip, and a strip that does not fit is not drawn. No overhang to specify, and no
persisted geometry that moves with a CSS token.

### This reverses a shipped decision, and that is the cost

Title-hidden anchoring is not unspecified. It was decided and shipped:

- `chart-card-slice2-tables-design.md:73` — "Title-hidden assemblies reserve nothing (Case 1). The strip
  overlays the content's top-right";
- `:93` — "The resident kebab stays resident on title-hidden tables, accepting that it overlays the last
  column header's sort control";
- `chart-card-slice3-selection-design.md:75` — the same decision for selection lists and trees;
- Chart Card Spec v3 §03 — "No lane, no rule, no reserved band… The strip floats over the plot's
  top-right on a small surface" — and §04, "when the title is hidden the strip overlays the plot at the
  same 12px from the top and right borders, so one inset governs every edge in every state."

So this decision:

1. **Removes the toolbar from every title-hidden anchored assembly** — chart, table, crosstab, calc
   table, selection list and selection tree, across all three shipped slices. Right-click becomes the
   only route, and on touch there is no route at all.
2. **Retires `rightEdgeReserve()` and `SORT_CONTROL_RESERVE`** (`vs-object-container.component.ts:588-603`).
   Both exist only to keep the kebab off the table's sort control in the title-hidden state; with no
   strip there, they become dead code. Deletions before bindings, per the standing rule.
3. **Overrides Chart Card Spec v3 §03 and §04** on the title-hidden overlay, in addition to decision 1's
   override of §04's lane model.

**Why it is taken anyway.** The alternative is two rules for one state: a strip contained by a lane when
there is a lane, and a strip floating over content when there is not. That is the second idiom the
initiative has been removing everywhere else — the same argument that settled dense in
[open-item-decisions](./chart-card-open-item-decisions.md) §4, where reintroducing a floating strip at
one density was rejected as the only place in the model where a control that stopped fitting reappears
somewhere else. A hidden title is that case exactly.

**Rejected — keep the title-hidden overlay.** It preserves shipped behaviour and the two slice designs,
and it costs the single-idiom property above. It also keeps a live collision: slice 2 accepted a resident
kebab sitting on a sort indicator as a known cost, and that cost only exists because the strip is drawn
where there is no lane to hold it.

**One consequence to confirm.** A title-hidden dashboard at any density now behaves exactly as a dense
one does: no affordance on touch. This is the same open question
[open-item-decisions](./chart-card-open-item-decisions.md) §4 records for dense plus touch, now reaching
a larger population. One predicate changes it if that is not intended.

---

## Decision 5 — the lane row ships with a use-the-default affordance on the title height

**Added 2026-08-13**, after `userTitleHeight` was built. That flag is what makes the row possible, and
building it surfaced two problems the row inherits. Both have the same fix, and it is UI work rather than
a resolver change, so it has to be scheduled with the row rather than after it.

**The question.** The flag records whether an author chose a title height. It is inferred from whether the
property dialog's value changed, because the dialog carries no other signal:
`SizePositionPaneModel.titleHeight` is a primitive `int` and the control is a plain `number-stepper`
(`size-position-pane.component.html:73-76`) — no blank, no auto, no checkbox. Inference is adequate today
and stops being adequate when the row lands.

**Problem 1 — an author cannot pin the height the dialog is showing them.** Today the guard compares the
incoming value against the *stored* height, so changing 25 to 20 is captured even though 20 is also the
default. Once the row makes the displayed height density-derived, the comparison has to move to the
effective height (see "What this costs to build" below and `TitleInfo.isUserTitleHeight()`'s javadoc).
After that move, an author who sees 26 at compact and wants 26 permanently has no way to say so: typing 26
is indistinguishable from accepting 26. The sibling `userCellHeight` accepts exactly this
(`SelectionListPropertyDialogService.java:220-221` — "accepting the org density default leaves the stored
height at its default and the flag clean"), which is right for a flag nobody has asked to pin, and wrong
for a lane whose height authors have been setting by hand for fifteen years.

**Problem 2 — the flag is one-way for five of the eight titled types.** The only path that clears it is
the table reset-layout action (`ComposerVSTableService.java:414`), which lives on
`TableDataVSAssemblyInfo`. A chart, calendar, selection list, selection tree or range slider that acquires
the flag has no route back to tracking the default — the author can opt out of the density row but cannot
opt back in.

**Decided: give the title height an explicit use-the-default control, and read the flag from it.** The
flag stops being inferred from what changed and becomes a direct record of what the author said. Ticking
the control clears the flag and lets the row resolve the height; unticking it and entering a number sets
the flag. That answers both problems at once — pinning the displayed value becomes expressible, and every
type gains the opt-back-in that only tables have.

**What it costs.** `SizePositionPaneModel.titleHeight` becomes nullable or gains a paired boolean; the
dialog template gains the control beside the stepper; the eleven dialog services read the control instead
of comparing values. The comparison-based guards are deleted rather than amended — with a real signal
there is nothing left to infer.

**Rejected — keep inferring, and accept that the displayed value cannot be pinned.** It is free, and it is
what the sibling does. It fails the population this row exists for: authors who have set title heights
deliberately are exactly the ones the row must not disturb, and telling them to set a value one pixel off
and back again to make it stick is not a design.

**Rejected — infer, but treat any dialog OK as intent.** Marks every assembly whose dialog was ever
opened, so the row reaches almost nothing. This was tried during the flag's implementation and reversed.

**Still open within this decision:** whether the control is a checkbox or a blank-means-default field;
whether cell height gets the same treatment for consistency, which would mean revisiting the sibling; and
what happens to assemblies already carrying the flag when the control ships — most plausibly they read
back as unticked, which is accurate.

---

## What this costs to build

**It is export-affecting.** Title height is a persisted model value read by the Java painters as well as
the browser, so changing its default moves PDF, PNG, Excel and scheduled output, and shifts the content
below it. This is not a CSS change and it needs the manual export pass.

**It is mark-gated as specified.** Widget spec §04 is explicit that the title-height row's "gate and mark
conditions are the same as every other height here" — it resolves through the seed mark, which does not
exist in `VSAssemblyInfo` and which nothing in this repo produces. See
[seeded-value-reversibility-decisions.md](./seeded-value-reversibility-decisions.md).

**There is a cheaper unblock, and it is already in the codebase.** The row does not strictly need the
mark; it needs a way to tell an author's 20px from the default 20px. `TableDataVSAssemblyInfo` solved the
identical problem in 13.3 with a persisted `userDataRowHeight` boolean plus an `== AssetUtil.defh` test
at read time (`VSTableLens.java:1780`, `BaseTableService.java:462`), and derives the flag for older files
on parse as `getDataRowHeight() != AssetUtil.defh` (`TableDataVSAssemblyInfo.java:997`). A
`userTitleHeight` flag of the same shape, stamped at the property-dialog `setTitleHeightValue` call
sites, unblocks this without waiting on the mark.

**`TitleInfo` is shared with three excluded types.** CheckBox, RadioButton and TimeSlider use the same
`TitleInfo`, and §05 excludes them. So the density substitution belongs in the per-type read path — the
way `rowHeight()` is applied in `VSTableLens` and `BaseTableService` — not inside
`TitleInfo.getTitleHeight()`, whose eight callers would all inherit it.

**The roadmap's blocker note is out of date.** `chart-card-roadmap.md` records this row as "not buildable
as specified" because `TitleInfo` seeds `AssetUtil.defh` in both constructors (`:53`, `:65`) and always
persists an explicit height (`:260`), so there is no unset state. All of that is true, and the 13.3
pattern shows an unset state was never the requirement.

---

## Still open

- **Is 1px of clearance enough at compact?** It is what a 26px lane leaves around a 24px strip, and the
  widget spec chose 26 for exactly that fit. If it reads tight at render, the lever is compact's lane row
  rather than the strip.
- **The 44px touch target against a 26px lane.** Chart card spec §06 says the touch floor "overrides all
  of them"; read strictly that puts a 44px control in a 26px lane. Unresolved in every document.
- **Does the sibling project accept overriding Chart Card Spec v3 §04?** Decisions 1 and 4 both do, and
  the chart card track is taking them.
- **`--inet-viz-chrome-row-height` is 22px at dense while the lane row is 20px** (`_viz-tokens.scss:112`).
  The token has no consumers today, so nothing disagrees yet; it will the moment something binds it.
- **The shape of decision 5's control**, whether cell height should get the same treatment, and how
  already-marked assemblies read back when it ships. Listed in full at the end of that decision.

---

## Related

- [chart-card-roadmap.md](./chart-card-roadmap.md) — where this sits in the dependency picture; its
  "needs a design decision" entry for the title lane is answered here
- [chart-card-open-item-decisions.md](./chart-card-open-item-decisions.md) — §4 decided dense draws no
  chrome; decision 4 above generalises it from a density rule to a lane rule
- [seeded-value-reversibility-decisions.md](./seeded-value-reversibility-decisions.md) — the mark this
  row is gated on, and what was decided about it here
- [chart-card-source-doc-corrections.md](./chart-card-source-doc-corrections.md) — the running audit of
  the external set against the branch
- [chart-card-slice2-tables-design.md](./chart-card-slice2-tables-design.md) §4 and
  [chart-card-slice3-selection-design.md](./chart-card-slice3-selection-design.md) — the title-hidden
  decisions that decision 4 reverses
- `chart-card-design3/Visualization Widget Spec.dc.html` §04, §05 — the values taken
- `chart-card-design3/Chart Card Spec v3.dc.html` §03, §04 — the lane model overruled
