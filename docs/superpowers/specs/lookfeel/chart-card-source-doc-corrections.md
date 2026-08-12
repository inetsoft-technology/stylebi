# Chart Card Source Docs — Corrections and Their Effect on the Plan

**Date:** 2026-08-12
**Applies to:** the `chart-card-design3/` document set (`docs/superpowers/specs/lookfeel/`) — fifteen files,
including two that are new this sync
**Source docs were synced against:** commit `52c127c128c3`, 2026-08-12. The new reversibility ticket labels
this "tree hash, not a commit sha"; it is a commit sha — the full sha of `52c127c12`, whose entire content
is the in-repo decisions document. The tree at that commit also contains the previous edition of this file.
**This audit was verified against:** community `viz-updates` @ `881a9b049`, plus twelve uncommitted files in
the working tree (the density-gating work — see §3.1, which turns on them)

**This supersedes the 2026-08-11 edition**, which audited `chart-card-design2/` against `a038a30b5`. Every
finding in that edition is accounted for in [the disposition table](#disposition-of-the-2026-08-11-findings)
— kept, closed, regressed, or superseded.

**What the new set is.** `chart-card-design3/` is a wholesale regeneration, not a diff. Four ticket
markdowns are byte-identical to v2; the two HTML documents were rebuilt without their embedded font and SVG
payloads, which is why they shrank 60–75% while their prose *grew*; `github.md` gained 13KB of changelog;
and two genuinely new tickets appeared — `Seeded value reversibility - ticket.md` and
`Chart card dark values - ticket.md`. Both new tickets land on questions the previous edition of this file
raised, and both are good. The set's technical quality is up. Its *bookkeeping* quality is down, and in a
specific way: every correction that had been applied to the v2 files in place was lost when the files were
regenerated.

**The one thing to read if you read nothing else.** §1.1. A decision was reversed this sync on the strength
of an argument that does not hold, and the reversal contradicts code already written. Everything else here
is ordinary drift.

**Reading order.** [§1](#1-factual-errors) is where a doc is wrong about the code.
[§2](#2-staleness) is where the code moved after the docs were written. [§3](#3-design-conflicts) is where
two deliberate decisions disagree. [§4](#4-gaps) is where a decision the implementer needs is unmade.
[§5](#5-regressions-from-the-last-audit) is corrections that were applied to v2 and did not survive
regeneration. [§6](#6-why-the-fix-did-not-hold) is the process finding, and it is the one with a cheap fix.

**Scoring.** Four factual errors, three points of staleness, four design conflicts, two gaps, **seven
regressions**. Two prior findings were closed by the set improving.

The regression count is up from three to seven, and their character has split in two. Three are conclusions
re-derived from the branch — the same failure as last edition, where the sync read the code and not this
file, and reached the reading this file had ruled out. Four are worse: corrections that had been written
into the v2 source files were **overwritten** by regeneration, and in two cases the overturned claim came
back *stronger* than before it was corrected. A correction that survives one sync and dies in the next is
not a correction; it is a delay. That needs a different fix from the one prescribed last time — see §6.

---

## Scope of this audit

**Audited in full.** `README.md`, `github.md`, `Open items - handoff.md`,
`Anchoring beyond charts - discussion.md`, `Chart overlay surfaces - ticket.md`,
`Shell surfaces - ticket.md`, and both new tickets.

**Audited where it lands on shipped or in-flight chart-card work.** `Visualization Widget Spec.dc.html`
§03/§04/§05/§08, and `Chart Card Spec v3.dc.html` §01 and §06.

**Confirmed byte-identical to v2 and not re-audited.** `Chart type sizes - ticket.md`,
`Dead menu icons - ticket.md`, `Outlined chart text - ticket.md`, `Zoom naming - ticket.md`. Their August
findings carry over unchanged — including the one in §2.3 below, which is now three editions old.

**Not audited.** The widget spec's §04 density matrix values and §06 per-widget affordance inventories, and
the range-slider bitmap work.

---

## Disposition of the 2026-08-11 findings

| Old § | Finding | Fate |
|---|---|---|
| 1.1 | Step 0's four `:root` tokens are one token | **Regressed by deletion.** Step 0 is simply gone from the handoff with no note. The correct conclusion and the incorrect one leave the same trace — see §5.2 |
| 1.2 | Nineteen citations point at filenames that do not exist | **Closed, and recurred in a new form.** Every `.html`→`.dc.html` repair held. Two *different* phantom targets appeared instead — see §1.3. The orphan half also recurred, on a different document — see §5.7 |
| 1.3 | The `#ff8d41` count is 11, not 7 | **Closed.** v3 makes no count claim; it calls `#ff8d41` one of four oranges, which is accurate |
| 2.1 | Slice 3 shipped nine hours after the sync | **Still true, one sync later.** The widget spec's own read-against date still predates the slice-3 commit — see §2.1 |
| 2.2 | `--inet-font-size-lg` already shipped | **Still true, unfixed.** `Chart type sizes - ticket.md` is byte-identical and still lists it as "to add" — see §2.3 |
| 2.3 | The shell owns a token layer adjacent to the type-scale proposal | **Still true.** Unchanged in v3 |
| 3.1 | The anchored strip's height, and whether it anchors under dense | **Superseded by §3.1 below.** The height half is settled and implemented; the anchoring half was reversed this sync, on a bad argument |
| 3.2 | The seed mark blocks release, not the rollout | **Half kept, half corrected — by me.** Still right that the *toolbar rollout* writes no persisted state. Wrong to have generalised it: the widget spec's §05 said heights move only for marked assemblies, and the height work is therefore mark-coupled. See §3.3 |
| 3.3 | The card radius is decided to change; the sentinel is decided to go | **Closed as a finding, promoted to a ticket.** v3's `Seeded value reversibility - ticket.md` covers it properly and adds a problem this file missed — see §3.2 |
| 3.4 | Two selection idioms, in two token layers | **Still true, and the tracking was deleted.** The teal family is unchanged in code; the ticket paragraph that tracked it was removed — see §5.3 |
| 4.1 | The dark gap is browser-DOM only | **Closed as a finding, promoted to a ticket** — and the ticket disagrees with the in-repo decision about scope and dependencies. See §4.2 |
| 5.1 | "That CSS branch had never executed in production" | **Regressed, third edition running.** Verbatim in the v3 handoff, and now self-refuting — see §5.5 |
| 5.2 | "The data tips sit two orders of magnitude below the dropdowns" | **Regressed and escalated** from "still open" to "highest-value single change in the whole set." See §5.6 |
| 5.3 | The 30px strip | **Closed.** v3's ladder is consistent: 30px is the lane, 24px is the strip |

---

## 1. Factual errors

### 1.1 The 32px chrome floor is a card-height rule. Dense's 20px lane never reaches it

**Where:** `Visualization Widget Spec.dc.html` §05, and again in §08 step 3.

**The claim.** Verbatim:

> A 24px strip cannot fit a 20px lane, so dense drops below chrome entirely — no strip, no kebab,
> right-click only. This is not a density-specific carve-out: it is the same floor the chart card spec's
> own toolbar ladder already enforces on height alone (its §06) — a 24px control needs 4px of clearance
> above and below, so anything under 32px hosts no chrome at all, kebab included. Dense's 20px lane sits
> under that floor, so it inherits the ladder's bottom rung rather than inventing a new one.

**What the code says.** The floor is real and the arithmetic is right, but it measures the **assembly**, not
the lane. `AbstractVSActions.showingActions` at `abstract-vs-actions.ts:217`:

```typescript
if(modern && this.model.objectFormat.height < AbstractVSActions.ACTION_FLOOR) {
```

`objectFormat.height` is the assembly's own height. `ACTION_FLOOR = 32` is declared at `:53` under a comment
that matches the spec's reasoning exactly — "a 24px control needs 4px of clearance above and below, so below
32px no control fits."

**The chart card spec agrees with the code, not with the widget spec.** §06 is unambiguously a card-height
ladder. Its own words: "How the strip thins out and finally disappears **as the card shrinks** is §06's
ladder"; "a 200px title-hidden card is 24px and a 200px card with a title is 30px"; and the table row
"`< 32px` — No chrome. A 24px control with 4px clearance does not fit; right-click is the only route."
§10.1 refers to the same rule as "a **card** too short to host any control at all."

**So the inheritance does not happen.** A 200px-tall chart in dense mode has a 20px title lane and a 200px
card. §06's ladder reads 200px and yields the full strip. The lane's height and the card's height are
different measurements of different things, and only one of them is on the ladder. Dense charts are not
short cards.

**Confirm it:**
```bash
cd community/web/projects/portal/src && \
  sed -n '48,54p;213,220p' app/vsobjects/action/abstract-vs-actions.ts
```

**Effect on the plan.** The reversal's *motive* survives this and is worth keeping: v3 also argues that
falling back to a floating strip is inconsistent with a ladder that "only shrinks the strip, then drops to
kebab-only, then drops to nothing," and that is a fair objection to the v2 decision. What does not survive
is the claim that dense therefore needs no rule of its own. Whichever outcome is chosen, dense requires an
explicit density condition — exactly the "density-specific carve-out" §05 says it is avoiding. The choice
is live, and it is between two written decisions rather than between a decision and an oversight. See §3.1.

---

### 1.2 The README points at the wrong branch, and contradicts its own changelog

**Where:** `README.md`, against `github.md` in the same folder.

**The claim.** The README names `epic-74519` as the branch this work is read against. v2's README had been
corrected to `viz-updates`.

**What the folder says.** `github.md` states `branch: viz-updates` and carries five sync entries dated
against it through 2026-08-12T04:10Z. The two files disagree about the most basic fact in the set.

**How it happened.** v2's handoff opened with a "What changed since the last handoff" block whose first
clause was "branch moved from `epic-74519` to `viz-updates`." v3 deletes that block and replaces it with a
dated sync note that does not restate the move. The one sentence recording the branch change was removed,
and the README reverted to the pre-change value — the same mechanism as §5.1 and §5.4, on a smaller fact.

**Effect on the plan.** No plan change; the work is on `viz-updates` and everything else in the set assumes
it. Recorded because a reader who trusts the README will diff against a branch that does not contain any of
this, find nothing, and reasonably conclude the set is fantasy. It is also the exact class of drift the
deleted pointer existed to catch — see §6.

---

### 1.3 Two citations name documents that have never existed

**Where:** `README.md:57,64` and `github.md:130,134-135` cite **`Corrections since handoff.md`**;
`github.md:127-128` cites **`Chart card handoff - mark impact patch.md`** as living "in the Visualization
Widget Spec project."

**What exists.** Neither file exists anywhere in this repository, and there is no "Visualization Widget Spec
project" folder under `lookfeel/`. `Corrections since handoff.md` is cited four times as a live, current
document — it is described as changed this turn.

Two other `.dc.html` references (`Resize Plot slider - proposal.dc.html`, `Chart Card Spec v1.dc.html`) are
explicitly marked superseded or deleted and are correct as history, as they were last edition.

**Why it recurs.** This is the third consecutive sync in which the set cites documents by names they do not
have. The previous instance was a suffix drift on real files and was repaired; this one invents two
documents outright. The repair held for the files it touched, which is evidence the corrections *work* when
they survive — see §6 for why these did not.

**Confirm it:**
```bash
cd community/docs/superpowers/specs/lookfeel/chart-card-design3 && \
  grep -rn "Corrections since handoff\|mark impact patch" . && \
  find ../../../../.. -name "Corrections since handoff.md"
```

**Effect on the plan.** None on the work. An implementer chasing `Corrections since handoff.md` finds
nothing. If that title describes a document the design project keeps on its own side, it needs to ship with
the set or stop being cited.

---

### 1.4 Two class attributions are wrong

**Where:** `Shell surfaces - ticket.md`'s new calendar-radius paragraph, and `github.md`'s property list.

- `isCornerSeedTarget()` is attributed to `VSObjectChromeDefaults`. It is a **private method on
  `VSAssemblyInfo`**, at `VSAssemblyInfo.java:1232`, whose own comment already documents the calendar's
  exclusion and the range slider's.
- `viewsheet.modernObjectChrome` is listed as a sibling property inside `VSDensityDefaults.java`. It is
  declared in `VSObjectChromeDefaults.java:44`.

Every underlying *value* in both passages checks out — the calendar's 10px seed, `PlotDescriptor
.modernCornerSeed`, the chart's `0.3` bar radius, the 12px/6px conflict, the dark hex values. Only the
homes are wrong.

**Effect on the plan.** Minor, but both send a reader to the wrong file, and `VSAssemblyInfo` versus
`VSObjectChromeDefaults` is precisely the distinction the seed mark's design turns on — the mark lives on
the assembly, the resolvers do not.

---

## 2. Staleness

Not errors. The docs were accurate when written.

### 2.1 The widget spec is still read against a date that predates slice 3

**What the docs say.** §08 frames the "eight-assembly rollout" as blocked future work that step 3 unblocks.

**What the code says.** Six of the eight seeded types are already anchored: `67c486d67` (chart),
`a4cd1e362` (table, crosstab, calc table), `a038a30b5` (selection list, tree). The widget spec's stated
read-against date is 2026-08-10, which predates the 2026-08-11 selection-family commit, so the document is
stale by its own baseline — the same finding as last edition, one sync later.

**Effect on the plan.** The remaining rollout is the container and the calendar. "Unblocks the
eight-assembly rollout" overstates by six.

---

### 2.2 Step 3's token swap is already done

`mini-toolbar.component.scss:82` now reads `height: var(--inet-control-height-sm)` in the working tree —
the exact change §08 step 3 lists as outstanding. `--inet-control-height-sm` is `24px` at
`_variables.scss:475`, so it is value-identical. Committed `HEAD` still carries the literal; only the
working tree has the swap.

**Effect on the plan.** One of step 3's three pieces is finished. See §3.1 for the other two.

---

### 2.3 `--inet-font-size-lg` is still listed as "to add", three editions on

`Chart type sizes - ticket.md` is byte-identical to v2 and still lists `--inet-font-size-lg: 16px` as a
token to add. It shipped in `43a934add` and is live at `_variables.scss:239`.

**Effect on the plan.** Unchanged from last edition: remove it from the deliverable, which shrinks that
ticket's chrome tier to nothing and leaves only the interior tier. Recorded again because a byte-identical
file is not an unchanged fact — the four untouched tickets should be re-read against the branch at some
point rather than assumed stable.

---

## 3. Design conflicts

### 3.1 §05 reverses the dense decision, against code already written to the previous one

| Source | Decision |
|---|---|
| `Visualization Widget Spec.dc.html` §05, **v2** | Dense loses the anchored strip and "falls back to the existing hover-revealed overlay — the legacy `.mini-toolbar:hover` path, already shipping, no new code" |
| `Visualization Widget Spec.dc.html` §05, **v3** | "Dense drops below chrome entirely — no strip, no kebab, right-click only." Floating "does not reappear anywhere in this" |
| Working tree, uncommitted | v2's decision, implemented. `GuiTool.isVizDensityAtLeastCompact()` and the shared `isAnchoredResident()` in `mini-toolbar.service.ts` gate anchoring to compact-and-above; dense returns to the hover path. The source comments quote v2's wording |

**Both readings are defensible and they are not close.** v2 keeps an affordance that exists today. v3
removes it, on the argument that a fallback to floating is inconsistent with a ladder that never floats.
That argument is real. The argument *attached* to it — that dense inherits this automatically from the 32px
floor — is not; see §1.1.

**What is not in dispute.** Anchoring must stop under dense, because a 24px strip does not fit a 20px lane.
Both versions agree, and that half is implemented.

**Resolved 2026-08-12: v3 is accepted.** See
[chart-card-open-item-decisions.md](./chart-card-open-item-decisions.md) §4. The reasoning that carries it
is v3's own — the ladder never falls back to a floating strip anywhere else, so reintroducing one at a
single density would be a second idiom for the same state. The claim in §1.1 is not what carries it, and
the implementation therefore writes the density rule explicitly rather than relying on inheritance:
`isAnchoredChromeSuppressed()` in `mini-toolbar.service.ts`, a sibling of `isAnchoredResident()` rather
than its negation, consumed by `showingActions` beside the existing floor test.

Two consequences recorded with the decision. **Touch has no route under dense** — "right-click only" has no
meaning on a tablet, and this is left flagged for the sibling project rather than decided here. And gating
`resident` had already meant dense assemblies shorter than 32px would **regain** chrome the floor used to
remove; suppressing dense outright closes that hole in the same change rather than leaving it open.

---

### 3.2 The seed mark is version-blind, and the two documents that know it defer to each other

**Where:** `Seeded value reversibility - ticket.md`, against `Visualization Widget Spec.dc.html` §03.

**The problem, in the ticket's words.** `resolveSeededCorner` asks *is this value the one I seed?* The mark
asks *was this assembly created under the gate?* Those coincide only while the set of seeded defaults never
changes. Add a second seeded default after the mark ships, and an assembly marked `gate-on` from before that
default existed is assumed to carry it. Value-sniffing is immune to this by construction.

**Why it is unresolved.** The ticket names two ways out — version the mark, or keep a per-value check for
defaults added after it — and defers the choice to the sibling project's §03. **§03 is byte-identical
between v2 and v3.** It has not been updated to answer the question, and it does not mention
version-blindness at all. The ticket is explicit that this must be decided "before the mark is the only
mechanism, because it is the failure that surfaces later and quietly."

**But it is milder than the ticket implies, because §03's reversal is recomputational.** §03 line 215: "On
load, when the mark and the gate disagree, **recompute the DEFAULT tier on a clone** — the same shape as the
other five read-time resolvers — instead of stripping values or rewriting saved content." Nothing is
subtracted. An assembly marked `gate-on` from before a default existed, opened with the gate off, has its
whole DEFAULT tier recomputed under gate-off rules — which produces the right answer for every value,
including ones the gate never seeded into it. §03 line 180 confirms the recompute reproduces
`setDefaultFormat`'s own `format.css` TableStyle lookup, so a customer stylesheet value survives too.

So version-blindness does not destroy anything. It manifests as **staleness in the other direction**: while
the mark and the gate agree, no recompute runs, so an assembly created before a default existed never picks
it up. Two assemblies of the same type, both marked `gate-on`, both opened under the gate, render
differently depending on when they were made. That is worth fixing and it is not the data-loss failure the
ticket's framing suggests. "Version the mark" is a schema decision being proposed against the severe reading.

### The count the ticket gets wrong, which is what version-blindness actually needs

The ticket exempts the seeded colours: "The seeded colours (`objectBorderColor`, `pageBackgroundCss`,
`cardBackgroundCss`) are computed live at read time rather than written and sniffed, so they need nothing
from this ticket."

**All three are written, not read.** Every one is a `set…Value` call on a default format, at creation:

| Value | Written at | Gate-dependence |
|---|---|---|
| `objectBorderColor()` | `VSAssemblyInfo.java:1163` → `:1180` (title) and `:1193` (object) | Direct ternary on `isModern()` |
| `cardBackgroundCss()` | `ChartVSAssemblyInfo.java:89`, `TableDataVSAssemblyInfo.java:1568` | Via `VSDensityDefaults.isDark()`, which is itself modern-gated |
| `pageBackgroundCss()` | `ViewsheetVSAssemblyInfo.java:238` | Ternary on `isModern()`, `#f5f5f5` otherwise |

The hex string is computed at the moment of the call; the call happens inside `setDefaultFormat` and the
result is persisted. "Computed live at read time" describes the five substituting resolvers (§03 line 92,
"lands on the DEFAULT tier of a clone… nothing is persisted"), not these.

Grepping the whole of `core` for reversal machinery returns exactly one path —
`VSObjectChromeDefaults.resolveSeededCorner`, reached only through
`VSCompositeFormat.resolveDefaultTierCorner` (`:334-337`). **So the gate seeds four values into the
persisted DEFAULT tier and one of them is reversible.**

The ticket's *conclusion* about the colours is still right — under a recomputational mark they need no
separate mechanism, because the recompute rewrites the whole tier. But it is right for the wrong reason, and
the reason is what matters here: version-blindness is a question about *which defaults existed when*, and
answering it requires knowing what the seeded set is. Nobody has counted it. The ticket's own scenario —
"add a second seeded default after the mark ships" — already happened three times before the mark was
written.

### Axis-blindness: the same failure, but it happens today

Version-blindness needs a future default to be added before it bites. There is a sibling problem that does
not wait, and neither document names it.

**The gate is three properties. The mark records one.**

| Property | Read at | Composed into |
|---|---|---|
| `viewsheet.modernVisualization` | `VSDensityDefaults.java:40` | the master gate |
| `viewsheet.modernObjectChrome` | `VSObjectChromeDefaults.java:44` | ANDed into `VSObjectChromeDefaults.isModern()` |
| `viewsheet.darkMode` | `VSDensityDefaults.java:48` | `isDark()` = `isModern() && darkMode` |

All three persisted seeds branch on `isDark()`, not on the master gate: `objectBorderColor()` at `:49`,
`pageBackgroundCss()` at `:54`, `cardBackgroundCss()` at `:64`. The mark, per §03 line 215, records "the
gate value that was in force when `setDefaultFormat` ran."

**So the failure runs like this, with today's code and the mark exactly as specified:**

1. Create an assembly with modern **and** dark on. `#252428` and its two companions are written into the
   persisted DEFAULT tier. Mark = `gate-on`.
2. Turn dark off. Modern is still on, so **the mark and the gate agree** — and §03 recomputes only when they
   disagree.
3. No recompute runs. The dark card background, page background and object border stay in a light-mode
   dashboard, permanently.

That is not a question about *when* the assembly was made. It is a question about *which switch* it was made
under, and a tri-state mark on one axis cannot answer it. Unlike version-blindness it needs no future
default and no schema evolution — it is reachable now, by toggling one EM property twice.

**Confirm it:**
```bash
cd community/core/src/main/java/inetsoft/uql/viewsheet/internal && \
  grep -n "modernVisualization\|modernObjectChrome\|darkMode" VSDensityDefaults.java VSObjectChromeDefaults.java && \
  grep -n "isDark()" VSObjectChromeDefaults.java
```

**The second axis is worse, because the spec chooses it deliberately.** The seeds do not guard on the master
gate. `VSAssemblyInfo:1162` and `:1166` and `ViewsheetVSAssemblyInfo:238` all guard on
`VSObjectChromeDefaults.isModern()`, which ANDs in `viewsheet.modernObjectChrome` at `:44`. The mark does
not. §03 line 215 says it "records whether an assembly was created under the modern gate, not merely which
chrome branch ran," and §04 line 381 defends that as a widening: "It broadens what the mark means — from
'which chrome branch ran' to 'was this assembly created under the modern gate' — which is what the name
should have carried all along."

So: set `viewsheet.modernObjectChrome=false` with the master gate still on. The composed gate is now off and
the seeds should reverse — but the mark records the master gate, which has not moved, so mark and gate agree
and nothing recomputes. **§04's rationale for reading the master gate is exactly what makes the mark
insufficient for §03's own chrome path.** The density path needs the master gate; the chrome path needs the
composed one; one tri-state cannot serve both, and the spec commits to the density path's reading without
noting the cost to the other.

**What each seed actually depends on:**

| Seed | Guarded by | Value branches on |
|---|---|---|
| Object border colour `VSAssemblyInfo:1162`→`:1180`,`:1193` | composed chrome gate | `isDark()` |
| Card corner radius `:1166`→`:1194` | composed chrome gate | — (constant) |
| Page background `ViewsheetVSAssemblyInfo:238` | composed chrome gate | `isDark()` |
| Card background `ChartVSAssemblyInfo:89`, `TableDataVSAssemblyInfo:1568` | **nothing — unguarded** | `isDark()` |

The card background is written on every creation regardless of any gate. In light mode it writes white,
matching the legacy default at `VSAssemblyInfo:1198`, so it diverges only under dark — a seed no gate check
protects, reachable by the dark toggle alone.

**The one resolver that is immune cannot be copied.** `applyDarkForeground` (`:99-114`) reads `isDark()` at
call time, defers to USER and CSS (`:104-108`), clones rather than mutating, and writes the clone's DEFAULT
tier (`:110-112`). Its immunity comes from persisting nothing — the value is re-derived on every read. That
is not a technique the mark can adopt; the mark exists for values that *are* persisted. The pattern is
available to them only by ceasing to persist them.

**Options, with what each costs.** Recorded without a recommendation — this is the sibling project's call.

- **A — master gate only, as §03 specifies.** Already written. Insufficient for three of the four seeds.
- **B — the composed chrome gate.** Fixes the sub-gate stranding, still strands dark, and contradicts §04's
  rationale — the density path is not chrome-gated, so §04 would need its own reading.
- **C — a tuple or bitfield over every axis a persisted seed reads.** Covers both. The schema stops being a
  tri-state, and it reintroduces version-blindness on a new dimension: marks written before an axis is added
  carry no value for it.
- **D — per-value provenance flags**, the `modernCornerSeed`/`modernSmoothSeed` shape. Immune to both
  blindnesses by construction, since the flag records *that* the gate seeded a value without reference to
  which switch, and it self-clears on user write. Costs one field per seeded value. The ticket rejects
  generalising this; note its stated reason — "gives no answer for content created before any of them" — is
  equally true of the mark, since unmarked content reads as `before gate`.
- **E — stop persisting, resolve at read.** Removes the problem class rather than tracking it. Costs a
  resolver per value on both the live model and the export painters, which is the shape the other five
  already have, and it gives up the format-editor visibility the ticket cites as the reason the radius is
  stored. Note the radius is already read-filtered by `resolveSeededCorner`, so its stored value is not
  authoritative today.

E applied to the three colours would leave the mark responsible for one persisted value, the radius — at
which point the choice between A, B, C and D is a much smaller decision.

**Effect on the plan.** This displaces version-blindness as the question to settle first. Version-blindness
is largely answered by §03's recompute rule and by fixing the seeded-set count. Axis-blindness is answered
nowhere, it fails today rather than after a future change, and the answer determines the mark's field
format — which is the one thing that is expensive to change after the field exists. **§03 as written is
option A, and option A is insufficient for three of the four values the gate currently persists.** That is a
finding about the spec, not a preference among the options.

### And the mechanism count is four, not three

The ticket's title is "Consolidate seeded-value reversibility on the seed mark" and it enumerates three
mechanisms. There are four.

| # | Mechanism | Protects | Where |
|---|---|---|---|
| 1 | Value check | card corner radius | `VSObjectChromeDefaults.resolveSeededCorner` `:79` |
| 2 | Stored flag `modernCornerSeed` | bar corner radius | `PlotDescriptor:1315`, field `:2005` |
| 3 | **Stored flag `modernSmoothSeed`** | **smooth lines** | **`PlotDescriptor:632`, field `:1996`** |
| 4 | The mark | everything, in principle | widget spec §03 — not built |

Number 3 is missing from the ticket. It is the same shape as number 2 —
`return modernSmoothSeed && !VSObjectChromeDefaults.isModern() ? false : smoothLines;` — persisted in XML
at `:1531` and `:1718`, and compared in `equals` at `:1873`. The ticket calls `modernCornerSeed` "the
best-designed of the three"; there are two of it.

**Effect on the plan.** Three decisions were taken against this on 2026-08-12 and are recorded, with the
options rejected and why, in
[seeded-value-reversibility-decisions.md](./seeded-value-reversibility-decisions.md): the pre-mark cohort is
written off; the value check and its tab carve-out are deleted once the mark lands; and the DEFAULT-tier
computation is extracted so creation and recompute share one definition of the seeded set. Four items remain
open there, two of them the sibling project's.

The blocker is not version-blindness. It is the **pre-mark cohort** — assemblies created with the gate on
before the mark exists, which parse as unmarked, read as *before gate* (§03 line 216), and are therefore
never recomputed. Writing them off is defensible only while the branch is unreleased.

The codebase already noticed. `PlotDescriptor:1994`: "Independent of `modernCornerSeed` by design — the two
mark disjoint chart families and must not un-gate each other." That is the comment of a codebase observing
it has two copies of one idea and documenting why they stay separate. The pattern self-replicates, which is
the ticket's own argument for consolidating, strengthened by one.

**Worth stealing from the flags before they are deleted.** Both clear themselves on explicit write —
`setSmoothLines` at `:648`, `setBarCornerRadius` at `:1334` — so an author's write ends the gate's claim on
that value. Neither the value check nor the mark has that property. The mark does not need it in the same
way (author edits land in the USER tier, which already wins), but the behaviour is the cleanest expression
of "the gate owns this until someone else claims it" in the codebase, and it should be a deliberate
omission rather than an accidental one.

**Confirm it:**
```bash
cd community/core/src/main/java/inetsoft/uql/viewsheet/graph && \
  grep -n "modernSmoothSeed\|modernCornerSeed" PlotDescriptor.java
```

### The recompute cannot be implemented the obvious way, and neither document says so

§03 line 180 says re-seeding "reproduces the correct value including that lookup" — the `format.css`
TableStyle branch. It does not say how, and the obvious route is a trap.

`setDefaultFormat` writes three kinds of value into one tier:

| Kind | Where |
|---|---|
| Gate-dependent | object border colour `:1162`→`:1193`, corner radius `:1166`→`:1194`, title border colour `:1180` |
| Customer stylesheet, overriding the gate, tables only | `:1182-1188` |
| Fixed constants | border widths, fonts, backgrounds, alignment — `:1179`, `:1192`, `:1195`, `:1198`, `:1211-1213` |

**Calling `setDefaultFormat` again would destroy user formatting.** It does not mutate the existing format —
it builds `format = new VSCompositeFormat()` at `:1156` and installs it wholesale via `setFormat(format)`
at `:1207`, which is `fmtInfo.setFormat(OBJECTPATH, fmt)` at `:290`. The new composite's USER tier is empty.
At creation that is correct, because nothing has been formatted yet. Run it on an assembly a user has
styled and every USER-tier value on the object path is replaced. The tier model that makes DEFAULT-tier
recomputation safe (§03 line 92, "USER and format.css tiers still win") depends on the USER tier surviving,
and this path does not preserve it.

So the recompute must either reimplement the gate-derived subset — a second place computing the same
defaults, which drifts the first time someone adds a seeded default and updates one of them, which is how
three mechanisms happened — or `setDefaultFormat` must be split so the default-tier computation is callable
on its own and both creation and recompute use it. The second forces the seeded set to be written down in
one place, which is what §3.2 needs anyway; it costs a refactor of a method every assembly type inherits.

**Confirm it:**
```bash
cd community/core/src/main/java/inetsoft/uql/viewsheet/internal && \
  sed -n '1156p;1207p' VSAssemblyInfo.java && sed -n '289,291p' VSAssemblyInfo.java
```

**A pre-existing asymmetry a refactor will surface.** The title border colour is written at `:1180`, before
the stylesheet override reassigns `bcolors` at `:1183`. So a table's stylesheet border colour reaches the
object border (`:1193`) but never the title border. That may be deliberate. It is worth knowing before
someone extracts this method and "tidies" the ordering, because doing so changes rendering.

### The pre-mark cohort: not a release risk, but it constrains the delete order

Assemblies created with the gate on **before** the mark ships will parse as unmarked, read as `before gate`,
and never be recomputed — §03 line 216, "mark-keyed application leaves an unmarked assembly untouched," and
line 222, `before gate` means *do not sweep*. So they carry the gate's seeded values with no record that the
gate put them there.

**As a release risk this is already handled.** The gate defaults off (`viewsheet.modernVisualization`, read
with no true-default; §03 line 215 states it), and the seeding has never shipped —
`VSObjectChromeDefaults.java` is absent from `main`, `origin/main`, `v1.1.x` and `v1.0.x`, so no release
contains the code that writes these values. The population is internal test content by construction. §03
line 306 agrees: "no migration at all, since nothing in the field predates it, and no write against customer
assets." The spec's stated requirement — land the mark **before release** — is the mitigation, and both
editions of this file already record it.

**What is not handled is the order of the delete.** `resolveSeededCorner` is a read-time value check and
does not consult the mark, so it reverses the radius on unmarked assemblies today. The mark cannot: unmarked
means *do not sweep*. So retiring the check, which is this ticket's whole purpose, converts the unmarked
gate-on population from *reversible* to *permanently modern* — for the radius as well as the colour it
already cannot reach.

The ticket half-sees this. Its sequencing says "deleting before step 1 leaves a window with no reversibility
at all," which treats the exposure as a window that closes when the mark lands. For unmarked content the
window does not close; the mark ships and steps over it. §03 line 280 supplies the only recovery — "stamp
`gate-off` onto its unmarked assemblies and the existing re-seed runs" — but that is the opt-in migration
path, framed there for genuinely old content, and it is a deliberate action rather than something the mark
does on its own.

**So the rule is sharper than the ticket states.** Do not retire `resolveSeededCorner` until the unmarked
gate-on population has been either stamped or discarded. On this branch discarding is cheap, which is the
argument for doing it before the mark rather than after.

**Confirm it:**
```bash
cd community && for b in origin/main origin/v1.1.x origin/v1.0.x; do \
  git cat-file -e "$b:core/src/main/java/inetsoft/uql/viewsheet/internal/VSObjectChromeDefaults.java" \
    2>/dev/null && echo "$b HAS it" || echo "$b absent"; done
```

**Effect on the plan.** Three corrections to the ticket, none fatal to it. Count the seeded set before
deciding anything about versioning, because the ticket is reasoning from a set of one. Name the recompute's
property set and mechanism. And put the recomputational reading to the sibling project explicitly: if §03
line 215 stands, versioning the mark is a migration bought against a failure that mostly does not occur, and
the residual is staleness rather than loss.

A third option the ticket does not cost: **stop persisting the radius.** It is the only one of the four that
needs machinery, and the ticket says why it is written — "to be visible in the format editor." It is already
filtered at read time by `resolveSeededCorner`, so the stored number is not authoritative anyway. If the
editor can show a computed default as a placeholder, the sentinel, the tab carve-out and the 12→6 collision
all disappear for the one property that has them.

---

### 3.3 The title-height work cannot honour "marked gate-on assemblies only", because the mark does not exist

**Where:** `Visualization Widget Spec.dc.html` §05's "Two consequences" paragraph, which states that heights
move "only for marked gate-on assemblies (§03, §04)".

**What the working tree does.** `VSDensityDefaults.titleHeight()` resolves purely on the org-level
`isModern()` and `mode()`. There is no per-assembly condition, because there is no per-assembly field to
condition on — no tri-state provenance mark exists anywhere in `VSAssemblyInfo`. Wired up as-is, turning the
org gate on would reflow the title lane of **every** assembly, marked or not.

**This corrects a claim in the previous edition of this file.** §3.2 last edition concluded that the mark
"blocks release, not the rollout." That is still right about the *toolbar* rollout, which writes no
persisted state and shipped correctly without the mark. It was wrong to leave the impression that the
density work was likewise independent: §05 had already said the heights are mark-coupled, and this file did
not carry that through to the height row. The in-repo roadmap inherited the error and said the title lane
row "needs no server change"; it has been corrected.

**Effect on the plan.** `titleHeight()` is safe as it stands only because nothing calls it. Its first call
site is the point at which the mark becomes a hard dependency. See §4.1 for the separate reason the call
site cannot be written yet.

---

### 3.4 The card radius is decided, and the code still seeds the old value

**What the docs decide.** `Chart Card Spec v3.dc.html` §01 states the server seed drops to 6px, matching
`--inet-radius-xl`. `Seeded value reversibility - ticket.md` attributes the decision to §01 correctly and
adds the consequence: `resolveSeededCorner` keys on exact equality with the seed constant, so any
already-seeded 12px asset stops being stripped the moment the constant moves. It asks for the seeded cohort
to be confirmed empty before either change lands.

**What the code says.** `VSObjectChromeDefaults.CARD_CORNER_RADIUS` is still `12` (`:129`), and
`resolveSeededCorner` still reads `radius == CARD_CORNER_RADIUS && !isModern() ? 0 : radius` (`:80`).
`--inet-radius-xl: 6px` is at `_variables.scss:523`, as cited.

**Effect on the plan.** Unchanged from last edition and now better documented by the source set than by this
file. Sequenced behind the mark. The cohort check is the new instruction and it is cheap — do it before
either constant moves, not after.

---

## 4. Gaps

### 4.1 The title lane has no unset state to attach a density row to — unaddressed in both versions

**Where:** `Visualization Widget Spec.dc.html` §05 and §08 step 3 specify a title-height row at 20/26/30 for
the eight seeded assembly types. Neither version says how it reaches them.

**What the code says.** It cannot, as specified. All five relevant `getTitleHeight()` overrides —
`ChartVSAssemblyInfo:2636`, `TableDataVSAssemblyInfo:241`, `SelectionBaseVSAssemblyInfo:299`,
`CurrentSelectionVSAssemblyInfo:205`, `CalendarVSAssemblyInfo:629` — are the identical single line
`return titleInfo.getTitleHeight();`. There is no default branch to redirect. The value lives in a shared
`TitleInfo`, whose two constructors seed the dValue to `AssetUtil.defh` (`TitleInfo.java:53,65`), so
`getTitleHeightValue()`'s fallback at `:167` is unreachable and an author who set 20 is indistinguishable
from one who never touched it.

Two further consequences:

- **`TitleInfo` is shared with the assemblies §05 excludes.** `CheckBoxVSAssemblyInfo`,
  `RadioButtonVSAssemblyInfo` and `TimeSliderVSAssemblyInfo` all carry one. An edit there reaches them.
- **Every saved assembly already carries an explicit height.** `writeAttributes` always writes
  `titleHeight` (`:260`) and `parseAttributes` always reads it (`:271`). Even given an unset state, the
  density row would reach newly created assemblies only. Existing dashboards would need the mark and the
  Modernize bar to move — which is §3.3's coupling arriving from the other direction.

**Confirm it:**
```bash
cd community/core/src/main/java/inetsoft/uql/viewsheet/internal && \
  grep -n "getTitleHeight()" ChartVSAssemblyInfo.java TableDataVSAssemblyInfo.java \
    SelectionBaseVSAssemblyInfo.java CurrentSelectionVSAssemblyInfo.java CalendarVSAssemblyInfo.java && \
  sed -n '48,66p;145,170p' TitleInfo.java
```

**Effect on the plan.** Step 3's height row is not implementable as written and was attempted and abandoned
on that basis. It needs a design decision first — give `TitleInfo` an unset state and an overload the five
included types call, or accept value-sniffing on `AssetUtil.defh` the way `resolveSeededCorner` sniffs the
card radius, with the same false-positive cost. The second is cheaper and is the pattern the set is trying
to delete. Either way it is a change to a shared class, and it should be specified before it is scheduled.

---

### 4.2 Dark: two documents, two scopes, and they are not the same work

| Source | Scope | Dependencies claimed |
|---|---|---|
| `chart-card-open-item-decisions.md` §2 (in-repo, 2026-08-11) | The four **browser-DOM** surfaces 9B left light: the anchored strip, the tooltip surfaces, the chart selection fill, the nav bar and annotation body | None. CSS only, no server, no export |
| `Chart card dark values - ticket.md` (new in v3) | The **card's server-side** dark values — binding this spec's light literals to the four shipped `VSObjectChromeDefaults` constants, plus the chart interior via `GDefaults` | The card-radius conflict, and "the seed mark reaching this card the same way it reaches modern" |

**They do not conflict; they are disjoint.** The new ticket is explicit that it is "a reconciliation, not a
blank page," and it is right about the server half — `VSObjectChromeDefaults` has shipped dark values since
`3e7e52626`. It correctly identifies the chart interior (`GDefaults` gridlines and axis lines) as the one
genuinely undecided piece, since no server value exists there to reconcile against.

**But its item 4 asks for something already answered.** It asks to "verify, don't assume, that
selection/annotation/tooltip tokens already resolve under dark," and guesses they "likely already resolve."
They do not. `plain-tooltip-surface` binds `--inet-text-color`, `--inet-dialog-bg-color`,
`--inet-default-border-color` and `--inet-shadow-low` (`_directives.scss:215-229`), none of which flip under
`.viz-dark`; the anchored strip binds `--inet-shell-surface-default` (`mini-toolbar.component.scss:31`),
which has one definition and no dark variant. **No shell neutral is redefined anywhere under `.viz-dark`** —
9B's browser-DOM half deliberately redefines only `--inet-viz-*` tokens. That verification is exactly what
the in-repo decision already performed, and its answer is the four-surface list above.

**Effect on the plan.** Keep both. The in-repo plan
(`docs/superpowers/plans/2026-08-11-chart-card-dark-browser-surfaces.md`) covers the DOM half and is
unblocked — the new ticket's dependency claims apply to the server half it is actually about, not to CSS
rules on shell surfaces. The ticket's item 3, the chart interior palette, is new work nobody owns and is the
most valuable thing in it.

---

## 5. Regressions from the last audit

Seven, in two kinds.

**§5.1 to §5.4 are overwritten edits.** Corrections were applied directly to the `chart-card-design2/`
files on 2026-08-11; the regeneration carried none of them forward. This failure is new this edition and is
the more serious of the two, because the corrected text existed and was discarded.

**§5.5 to §5.7 are re-derivations** — the failure from the last edition, unchanged: the sync read the branch
and not this file, and reached the reading this file had already ruled out. In both of §5.5 and §5.6 the
claim returned *stronger* than the version that was corrected, which is what happens when a conclusion is
re-derived from ambiguous evidence by someone who has not seen it refuted.

### 5.1 The anchoring discussion reverted to its pre-correction body

**Where:** `Anchoring beyond charts - discussion.md`.

v2's copy carried an applied correction — a paragraph headed "Corrected — the precondition is not the seed
mark," the shipped detail for Case 1, and dated sequencing. v3 prepends accurate new sync notes and then
reverts the body beneath them to the stale draft. The seed-mark correction is gone entirely.

The result is a document that contradicts itself two paragraphs apart: the sync notes record slices 1–3 as
shipped, and the body then says the work "Depends on: §02 shipping first" and "1. Ship the chart card as the
pilot. It is nearly ready."

**Effect on the plan.** The precondition correction from §3.2 of the last edition has to be re-applied, or
the next reader re-learns that the rollout is blocked on the mark. It is not.

---

### 5.2 Step 0 vanished rather than being corrected

**Where:** `Open items - handoff.md`.

The last edition found that step 0's "four `:root` tokens, a precondition not a cleanup" was one token, not
four — touch height and both icon sizes already ship. v3 deletes step 0 entirely, with no note.

The outcome is right and the record is not. A deleted step and a corrected step read identically to the next
sync, which is how a corrected claim becomes available for re-derivation. Two of the three tokens are still
described as absent elsewhere in the set.

---

### 5.3 The teal selection finding was deleted, not resolved

**Where:** `Chart overlay surfaces - ticket.md`.

v2's copy carried two paragraphs, added in the last correction pass, flagging that `_viz-tokens.scss` still
defines a second selection idiom — `--inet-viz-selected-bg-modern: #DDF1F5`,
`--inet-viz-selected-text-modern: #123C44`, `--inet-viz-selected-border-modern: #BFDDE5` at `:51-53` —
alongside the shipped orange focus-family vocabulary. v3 removes both paragraphs and, with them, the
ticket's only reference to `Visualization Widget Spec.dc.html`, which is where §07's fix lives.

**The tokens are unchanged in the code.** A chart and a table side by side still render selection in orange
and teal. The problem is exactly as live as it was; only its tracking is gone.

**Effect on the plan.** Unchanged from §3.4 last edition — it is open, it is owned by the widget spec's §07,
and it is export-affecting. It now has no owner in the chart-card set.

---

### 5.4 The seed-mark correction was reverted in the handoff too — and inverted

**Where:** `Open items - handoff.md`, dependency diagram and ordering list.

§5.1 above records the correction being lost from the anchoring discussion. It was applied to the handoff as
well, and the handoff did not merely lose it — it restored the claim the correction overturned and extended
it.

| | Text |
|---|---|
| **v2, corrected** | `M. Seed mark … gates RELEASE and the two workstreams that write persisted format (§04 density heights, §07 derived selection) — **not F**`, under a paragraph headed "Corrected — an earlier draft ordered M before F instead" |
| **v3** | `M. Seed mark (sibling spec, Visualization Widget Spec §03) ─┘ **should land first**`, and "Five things are ordered: … **M precedes F** (and H)" |

v3 also adds a justification: "Calendar and the container — the two slices still open — should wait for the
mark: it records whether an assembly's seeded chrome ran under gate-on or gate-off, and skipping it means
those two slices ship with no reversibility record."

**Why it is wrong, unchanged.** The rollout writes no persisted state. Every file in all three shipped
slices is under `web/projects/portal/src` — no Java, no `VSAssemblyInfo`, no `setDefaultFormat` path. There
is no reversibility record for the calendar and container slices to miss, for the same reason there was none
for the three that shipped. The mark is real, decided and unbuilt; it gates release and the workstreams that
write the DEFAULT tier, and it does not gate this.

**And L disappeared from the diagram entirely.** v2's dependency picture carried
`L. Title lane + strip density gating … must land first` as an explicit node preceding F. v3's diagram has
D→E→F, M, G→H, plus two new nodes K and N for the new tickets — and no L at all. The widget spec's §08 step
3 still exists and still claims to unblock the rollout; the handoff simply stopped drawing it. So the one
item that genuinely does precede slices 4 and 5 was dropped from the picture in the same sync that
reinstated an item that does not.

**Confirm it:**
```bash
cd community && git show --stat 67c486d67 a4cd1e362 a038a30b5 | grep "|" | grep -v "web/projects/portal/src"
```
(expects no output)

---

### 5.5 "That CSS branch has never executed in production" — third edition, now self-refuting

**Where it returned:** `Open items - handoff.md` §2, "Manual pass required."

> That CSS branch has never executed in production: nothing currently sets `color`/`border-color` on those
> canvases, so every selection today is on the hardcoded `#dc581e`. This is the first time the code path
> runs.

**What the code says.** `_themeable.scss:1401-1403`:

```scss
.chart-object-canvas {
  color: rgba(220, 88, 30, 0.3);
  border-color: #dc581e;
}
```

Global, unscoped, on exactly the class the `getComputedStyle` branch reads. The branch has always executed.

**The claim now contradicts itself.** It names `#dc581e` as the hardcoded value the product falls back to
"because nothing sets `border-color`" — and `#dc581e` is the `border-color` in the rule it says does not
exist, at `:1403`. The value could not be reaching the render by the route described.

**Effect on the plan.** Unchanged from the last two editions. Run the manual pass for item 7's fill/stroke
split, not because a dead branch is executing for the first time. The risk framing should not be used to
schedule it.

---

### 5.6 The data-tip z-index claim returned, escalated, and misreads the constant

**Where it returned:** `Open items - handoff.md` step 1c, now headed **"Highest-value single change in the
whole set"** — up from v2's "still open, despite looking touched."

> a live bug, not a cleanup: the registry holds two entries (`.fixed-dropdown` 999900, `w-tooltip` 999901)
> while the data tips sit at 9996, two orders of magnitude below, so any dropdown or tooltip renders over a
> data tip.

**What the code says.** 9996 is not the data tip. It is `POP_UP_BACKGROUND_ZINDEX`
(`date-tip-helper.ts:28`) — the **scrim**, the dim layer painted behind pop content. The content layer is
computed at `:61-62`:

```typescript
return Math.min(POP_UP_CONTENT_MAX_ZINDEX,
                POP_UP_CONTENT_BOOST_ZINDEX + (naturalZIndex || 0));
```

`99999 + natural`, capped at `999899` — one below `.fixed-dropdown` at 999900, deliberately. The file's own
comment at `:26` states the effective order: `scrim 9996 < source 9997 < content (99999 + natural, capped)
< .fixed-dropdown 999900 < w-tooltip 999901`.

**So the premise fails twice.** The number quoted belongs to a different layer, and the relative order it
calls a bug is the decided one: a dropdown opened inside a data tip must render above it. The clamp exists
to guarantee that against a large natural z-index, with a unit test asserting it across naturals from 0 to
100000.

**What is true.** Two of three assignment paths still bypass the registry —
`vs-pop-component.directive.ts:314,317` and the runtime scrim/source assignment. That is a maintainability
item, and `_directives.scss:20-29` documents it in the shipped code as a known partial state.

**Effect on the plan.** The escalation to "highest-value single change in the whole set" is not supported.
Item 12 keeps its downgraded value claim, and "do not let this wait behind the colour work" — which v3
restored verbatim — should be dropped again.

---

### 5.7 The orphan pattern recurred, on the newest document

The last edition found that `Visualization Widget Spec.dc.html` — the most decision-dense file in the set —
was cited by no markdown in its own folder, and added it to the README, the changelog and eight screen-map
rows. Those citations held.

`Chart card dark values - ticket.md` is now in the same position: **nothing in the set references it.**
`Seeded value reversibility - ticket.md`, added in the same sync, is referenced from both `README.md` and
`github.md`. One of the two new documents was indexed and the other was not.

---

## 6. Why the fix did not hold

The last edition diagnosed the regressions correctly and prescribed the wrong remedy. It said:

> The fix is cheap and belongs in the source set rather than here: `README.md`'s "changed since the last
> handoff" section should name this file as required reading for the next sync.

That was done. `chart-card-design2/README.md` gained a "Required reading before the next sync" section
naming this file. **v3's README deletes the section and every reference to it**, and the v3 set as a whole
contains no reference to any in-repo document at all:

```bash
cd community/docs/superpowers/specs/lookfeel && \
  grep -rn "chart-card-source-doc-corrections\|chart-card-open-item-decisions\|chart-card-roadmap" \
    chart-card-design3/          # no output
```

The remedy could not work, for a reason that was visible at the time and that this file had already
written down two paragraphs earlier: the set is **regenerated wholesale on each sync**, so nothing authored
inside it survives. A pointer placed in the regenerated set is deleted by the next regeneration, along with
every other in-place correction — which is precisely what §5.1 through §5.4 record.

**What is now known about the delivery path.** The sync read commit `52c127c128c3`. That commit's tree
contains both `chart-card-source-doc-corrections.md` and `chart-card-open-item-decisions.md`, at the top
level of the same `lookfeel/` directory the design folder sits in. Availability is not the problem.
Discoverability is: nothing the design process reads points at them, and the one thing that did was inside
the file it overwrites.

**So the pointer has to live where regeneration cannot reach.** Three candidates, cheapest first:

1. **The sync request itself.** Whoever asks for the next sync names this file in the ask. Costs nothing,
   survives everything, depends on a person remembering.
2. **`lookfeel/README.md` or the initiative roadmap** — outside the design folder, in the directory the
   design folder sits in, so a sync that reads the tree at all can find it.
3. **A checked-in note the design tooling reads by convention**, if such a convention exists on that side.

Option 2 is the one this repository can implement unilaterally, and
[chart-card-roadmap.md](./chart-card-roadmap.md) now names this file in its first section for that reason.
None of the three is reliable on its own. What is reliable is the observation that **corrections applied to
the design set have a lifetime of one sync**, so anything worth keeping has to be stated here, in the
roadmap, or in the decisions document — never only in the source files.

---

## Appendix — what was checked and held

Verified this pass against `881a9b049` plus the uncommitted working tree. Counts were run, not sampled.

- `ACTION_FLOOR = 32` and its use against `objectFormat.height` — `abstract-vs-actions.ts:53,217`.
  Confirmed; this is §1.1.
- Chart Card Spec §06's ladder is card-height throughout, including the `< 32px` row and §10.1's "a card
  too short to host any control at all." Confirmed.
- `ANCHORED_ASSEMBLY_TYPES` still carries the six types; the container is still deliberately absent —
  `mini-toolbar.service.ts:41-53`. Confirmed.
- `isAnchoredResident()` is the single shared gate, consumed by both `AbstractVSActions.resident` and
  `VSObjectContainer.isKebabResident` — working tree only. Confirmed.
- `VSDensityDefaults.titleHeight()` returns `AssetUtil.defh`/26/30 with no per-assembly condition, and has
  no call site — working tree only. Confirmed; this is §3.3.
- All five `getTitleHeight()` overrides are one-line delegations to `titleInfo`; `TitleInfo` seeds
  `AssetUtil.defh` in both constructors and is shared with CheckBox, RadioButton and TimeSlider —
  `TitleInfo.java:53,65`. Confirmed; this is §4.1.
- `VSObjectChromeDefaults.CARD_CORNER_RADIUS = 12` and `resolveSeededCorner()` unchanged — `:80,129`.
  `--inet-radius-xl: 6px` — `_variables.scss:523`. Confirmed.
- No tri-state provenance field anywhere in `VSAssemblyInfo`. Confirmed absent, third pass running.
- `isCornerSeedTarget()` is private on `VSAssemblyInfo.java:1232`; `viewsheet.modernObjectChrome` is
  declared in `VSObjectChromeDefaults.java:44`. Confirmed; this is §1.4.
- The teal family is unchanged — `_viz-tokens.scss:51-53`. Confirmed; this is §5.3.
- No shell neutral is redefined under `.viz-dark`; `plain-tooltip-surface` binds four unflipped tokens
  (`_directives.scss:215-229`) and the strip binds `--inet-shell-surface-default`
  (`mini-toolbar.component.scss:31`). Confirmed; this is §4.2.
- `.chart-object-canvas` sets both `color` and `border-color` globally at `_themeable.scss:1401-1403`, and
  `#dc581e` is the `border-color` in that rule. Confirmed; this is §5.5.
- `POP_UP_BACKGROUND_ZINDEX = 9996` is the scrim (`date-tip-helper.ts:28`); pop content resolves to
  `min(999899, 99999 + natural)` at `:61-62`, one below `.fixed-dropdown`. Confirmed; this is §5.6.
- The v3 handoff's dependency diagram contains D→E→F, M, G→H, K and N, and no L. Confirmed; this is §5.4.
- `--inet-font-size-lg: 16px` — `_variables.scss:239`. Confirmed shipped; this is §2.3.
- `#ff8d41` appears 11 times in `vs-chart.component.scss`. Confirmed; v3 makes no competing claim.
- `52c127c128c3` resolves to a commit, not a tree. `git cat-file -t` returns `commit`. Confirmed.
- The two HTML documents lost only embedded assets: after tag-stripping, the chart card spec's prose grew
  97,987 → 110,312 bytes and the overlay document's is byte-identical. Confirmed; no content was lost in
  the rebuild.

### Not checked this pass

- The widget spec's §04 density matrix values, and the two rows and three affordances added to §04/§06 this
  sync.
- `Dead menu icons - ticket.md`'s "~50 unreachable `icon()` declarations" — byte-identical since v1 and
  never counted. Still treat as unverified.
- Whether the live viewer renders gauges and thermometers through the painter or the DOM.
- `github.md`'s new in-project decision to scope the resting kebab by pointer capability, which modifies
  already-shipped slice-3 behaviour. Read it before the container slice.
