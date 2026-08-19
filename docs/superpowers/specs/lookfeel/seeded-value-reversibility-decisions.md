# Seeded Value Reversibility — Decisions

**Date:** 2026-08-12 (v2 — supersedes the 2026-08-12 v1 decision set in this file); addendum 2026-08-13;
**decision 13 added 2026-08-19, overruling decisions 6 and 7**
**Verified against:** community `viz-updates` @ `881a9b049` for v2; the addendum against `c8011beca`;
decision 13 against `8ef511e45`, which carries P1–P4 of the forward half
**Concerns:** the seed mark (`Visualization Widget Spec.dc.html` §03, sibling-owned, not built) and
`chart-card-design3/Seeded value reversibility - ticket.md`
**Status:** thirteen decisions taken, **two of them (6 and 7) overruled on 2026-08-19 by decision 13**;
one item open. Overruled decisions are retained in place, with the reason they were taken and the reason
they fell, so the history reads without `git log`. **Read both amendment sections below before planning the
mark** — `userTitleHeight` has shipped since v2 and changes how decisions 4 and 11 should be read, and
decision 13 replaces the org-wide revert sweep with a per-dashboard action

## Why this file exists, and what changed in v2

~~The gate writes values into saved dashboards at creation. Turning the gate off has to un-write them.~~
**The first half of that premise is still true and the second is not, as of 2026-08-19.** The gate writes
values at creation; turning it off un-writes nothing, and a person pressing Revert on one dashboard is what
un-writes them. Decision 13. The file's original framing is left standing because every decision from 1 to
12 was taken inside it, and reading them against a premise they did not have would be misleading.

v1 of this file analysed that against the widget spec's tri-state seed mark and left three questions to
the sibling project. v2 records a **product decision set taken here** that answers all three and replaces
most of §03's mechanism, while keeping its central idea — a provenance mark on the assembly.

The three substantive departures from §03:

| | §03 as specified | Decided here |
|---|---|---|
| Reversal shape | recompute the DEFAULT tier onto a **runtime clone**; saved content never written | **persisted wholesale revert**; the saved asset is rewritten |
| Forward direction | `mark ≠ gate` re-seeds an assembly forward automatically on load | **no automatic forward sweep**; modernization is opt-in via a button |
| Mark states | tri-state: `gate-on` / `gate-off` / absent | **unmarked / modern-light / modern-dark**; `gate-off` is not stored |

**Amended 2026-08-19.** The first row's *shape* still holds — reversal is persisted, not a clone — but its
*trigger* does not: decision 13 overruled the org-wide sweep, so the saved asset is rewritten by a person
pressing Revert on one dashboard, never by an EM checkbox. A fourth departure from §03 joins the three
above: **the gate never reverts anything**, in either direction.

Everything below cites a file and line. Where this contradicts the ticket or §03, the code was read.

---

## What changed since this was written — the revert sweep is overruled

**Added 2026-08-19, after P1-P4 of the forward half shipped.** Decisions 6 and 7 specified that unchecking
the modern gate sweeps the org: every marked assembly rewritten to legacy defaults, persisted,
asynchronously, behind a restore point, with scheduled tasks and open composer sessions blocked for the
duration. **That is overruled.** Turning the gate off now changes nothing that already exists; a
per-dashboard **Revert** action, the exact mirror of Modernize, is the only way back. Decision 13 records
the new rule in full, including what it costs. Decisions 6 and 7 stay below because the analysis inside
them — the tier-model consequences, the rejected read-time clone route, the reason a value check cannot be
re-pointed at the new radius — is still load-bearing and is cited elsewhere in this tree.

**What this changes at a glance:**

| | Decisions 6 + 7 | Decision 13 |
|---|---|---|
| Trigger | an EM checkbox, org-wide | a person, one dashboard at a time |
| Reach | every marked assembly in the org, or every org | the assemblies of the sheet in front of you |
| Tiers rewritten | DEFAULT **and** USER | DEFAULT only, through the hook creation uses |
| Undo | a restore point plus a documented procedure | one composer undo step |
| Blocks | scheduler tasks, open composer sessions | nothing |
| Gate-off rendering | legacy, once the sweep completes | unchanged — the mark still decides |

**What it costs, stated once here and again in decision 13: there is no longer an org-wide off switch with
teeth.** A customer with three hundred modernized dashboards who wants out visits three hundred dashboards.

---

## What changed since this was written — read before planning the mark

**Added 2026-08-13.** This file predates the `userTitleHeight` work and does not mention it anywhere. Four
things have happened that change how decision 4 and decision 11 should be read. Nothing below invalidates a
decision; it narrows what remains to build.

**1. One of "the density heights" now exists, and it needed a second flag the mark does not replace.**
Decision 4 keys "the density heights" off the mark. The first of those heights — the title lane row, item
L' in [chart-card-roadmap.md](./chart-card-roadmap.md) — turned out to need *two* conditions, not one:

| Question | Answered by | Why the other cannot answer it |
|---|---|---|
| Is this assembly modern? | the mark | a flag on one property says nothing about the assembly |
| Did an author choose this height? | `userTitleHeight` | the mark records *when* an assembly was made, not which of its properties a person later set |

`userTitleHeight` shipped in `07c91926e` and `307a6ee09` — a persisted boolean on `TitleInfo`, derived on
parse for older files against each type's own default, and stamped at the fourteen sites where a height is
chosen. [Strip and lane decisions](./chart-card-anchored-strip-lane-decisions.md) decision 8 records the
consequence: **the row is scheduled after the mark**, because on the flag alone it would resize every
dashboard ever saved, and shipping it inert beside the mark defers rather than parallelises its verification.

Expect the same shape for the other §04 heights. Row height and selection cell height already carry
`userDataRowHeight`, `userHeaderRowHeight` and `userCellHeight` from 13.3 and earlier, for the same reason.
The mark does not subsume any of them.

**None of these belongs in decision 12's deletion sweep**, and the resemblance is close enough to be worth
saying out loud. The four mechanisms decision 12 deletes are *gate*-provenance — they record that the gate
seeded a value, so the value can be un-seeded, and the mark replaces all four. `userTitleHeight` and its
siblings are *author*-provenance: they record that a person set a value, which the mark never knows and
never will. They survive the mark and are still needed after it.

**2. Decision 4's per-assembly CSS scope has a second consumer waiting.** Strip and lane decision 5 puts a
"follow the default density" checkbox in the Size and Position pane, covering title height and table row
height. It needs to know an assembly's effective height, which is density-derived and therefore
mark-conditioned — so the per-assembly scope decision 4 already requires is a prerequisite for that control
too, not only for the CSS. Worth knowing while sizing decision 4, which this file already calls the largest
single piece of work in the set.

**3. `TitleInfo.equals()` is about to change, and decision 11's enumeration point should know.** Strip and
lane decision 7 adds `userTitleHeight` to `TitleInfo.equals()`, because every assembly info's
`copyViewInfo` transfers the whole `TitleInfo` only when it compares unequal — so a change touching only a
provenance flag is otherwise dropped when the property dialog applies its edited clone. Any per-assembly
provenance the mark introduces has the same exposure through the same eight `copyViewInfo` guards. The audit
is done and recorded there: `TitleInfo.equals()` has exactly eight consumers repo-wide, all of them that
guard.

**4. The pre-mark cohort now has a second member, and the write-off still holds.** "Accepted costs" below
writes off assemblies created with the gate on before the mark exists. Assemblies created since
`307a6ee09` may also carry `userTitleHeight` without a mark. Same disposition, same reasoning — `viz-updates`
has never shipped, so every member is a disposable dev or test dashboard — and the same expiry: it holds
**only while the branch is unreleased**.

---

## The model in one page

1. **Legacy content is never touched.** An assembly with no mark keeps its appearance under every gate
   setting, forever, until someone opts it in.
2. **The mark is per-assembly**, written when the gate is on at creation, and records the gate *tuple*:
   `modern-light` or `modern-dark`. Absent means legacy/unclaimed.
3. **New assemblies inherit the host viewsheet's mark.** Copied, pasted and embedded assemblies keep
   **their own** mark. Mixed dashboards are a supported outcome.
4. **Everything modern follows the mark**, not the gate — seeded values, read-time resolvers and CSS
   alike. This reverses the current behaviour, in which enabling the gate modernizes legacy content.
5. ~~**Disabling the modern gate reverts marked assemblies wholesale**, in a persisted org-wide sweep, and
   clears their marks. Nothing else ever reverts anything.~~ **Overruled 2026-08-19 by decision 13.**
   Disabling the gate reverts nothing. The gate is a creation-time switch: it decides what new content is
   stamped with, and whether Modernize is offered. Nothing automatic ever rewrites a saved asset.
6. **Modernize** stamps unmarked assemblies and applies modern defaults wholesale; **Revert** clears marks
   and applies legacy defaults. Both are manual, per-dashboard, composer-only and undoable, and both run
   through the same hook, so neither can drift from the other.
7. **Bookmarks are never rewritten.** Stale format state is resolved against the assembly's current mark
   at restore time.

---

## The mechanism inventory

**The ticket counts three. There are four in code**, plus three seeded values with no mechanism at all.

| # | Mechanism | Shape | Protects | Where |
|---|---|---|---|---|
| 1 | Value check | compares the value to the seed constant | card corner radius | `VSObjectChromeDefaults.java:79-81`, seed `:129` |
| 2 | Stored flag `modernCornerSeed` | per-value provenance boolean | bar corner radius | `PlotDescriptor.java:1315`, field `:2005` |
| 3 | Stored flag `modernSmoothSeed` | per-value provenance boolean | smooth lines | `PlotDescriptor.java:631-633`, field `:1996` |
| 4 | The seed mark | per-assembly | everything | widget spec §03 — **not built** |
| — | *(none)* | — | **object border colour** | seeded `VSAssemblyInfo.java:1162-1163` → `:1180`, `:1193` |
| — | *(none)* | — | **card background** | seeded `ChartVSAssemblyInfo.java:89`, `TableDataVSAssemblyInfo.java:1568` |
| — | *(none)* | — | **page background** | seeded `ViewsheetVSAssemblyInfo.java:238` |

**Mechanism 3 is not in the ticket's inventory.** It is structurally identical to mechanism 2 and was added
separately. The comment at `PlotDescriptor.java:1993-1995` — "Independent of `modernCornerSeed` by design —
the two mark disjoint chart families and must not un-gate each other" — is the codebase noticing it holds
two copies of one idea and documenting the separation rather than removing it. That is how a fourth arrives.

**How four happened.** Each was the cheapest correct answer at the point it was added. The card radius lives
in a `VSFormat` tier, a generic property bag with nowhere to hang a provenance flag, so the only available
test was the value itself. The bar radius and smooth lines live on `PlotDescriptor`, where a boolean field
costs one line. Nobody was careless; nobody stood back.

All four are retired by decision 11.

## The tier model, because several decisions turn on it

`VSCompositeFormat` holds **three separate `VSFormat` objects** — `userfmt`, `cssfmt`, `deffmt` — and
resolves USER → CSS → DEFAULT (`getRoundCorner`, `:311-316`). Each tier carries its own
`isXxxValueDefined()` predicates (`VSFormat.java:896-1061`), and `applyDarkForeground` already uses them
(`VSObjectChromeDefaults.java:104-106`).

**Consequence, and it corrects a premise the design was nearly built on:** there is no value-sniffing
needed to tell a user value from a default. They live in different objects. A user preference that happens
to equal a modern default is still unambiguously a user preference.

Value-sniffing is needed for one thing only — telling a **gate-seeded** value from a **`format.css`
TableStyle** value *inside the DEFAULT tier* (`VSAssemblyInfo.java:1182-1188`). That is all
`resolveSeededCorner` was ever for, and it disappears once the whole tier is recomputed and the TableStyle
lookup re-run.

---

## Decision 1 — the mark is per-assembly, and records the gate tuple

**Decided.** A nullable field on `VSAssemblyInfo`, written through the existing
`writeAttributes`/`parseAttributes`, plus `clone`/`copyViewInfo` handling. Three states:

| State | Meaning |
|---|---|
| absent | legacy / unclaimed — never touched by any automatic behaviour |
| `modern-light` | created with modern on, dark off |
| `modern-dark` | created with modern on, dark on |

**Per-assembly, not per-dashboard.** Forced by decision 3: a legacy dashboard must be able to hold a
pasted modern chart, and a light dashboard must be able to hold a pasted dark one. "This dashboard is
modern" is derived — every assembly in it is marked.

**Rejected — §03's tri-state with a stored `gate-off`.** §03 needs a distinct `gate-off` state because it
re-seeds forward automatically when `mark ≠ gate`; the third state exists to stop that sweep hitting all
pre-existing content. With forward sweeping removed (decision 5), an assembly created while the gate was
off is simply unmarked, and the state collapses. The residual ambiguity — unmarked meaning both *predates
the feature* and *created gate-off* — has no consequence, because neither is ever touched.

**Rejected — a single boolean.** The dark bit is load-bearing. Decision 4 keys the dark read-time
resolvers off the mark, and decision 3 requires a dark-marked assembly pasted into a light dashboard to
stay internally consistent. A boolean cannot express that.

## Decision 2 — legacy content is never touched

**Decided.** An unmarked assembly renders exactly as it does today, under every gate setting. No sweep, no
read-time substitution, no CSS scope. The only thing that changes it is a person pressing Modernize.

**Rejected — treating unmarked as gate-off and letting the gate sweep it forward.** Factually accurate —
everything in the field was created with the gate off — and it is the failure §03 itself names: with the
gate on, `mark ≠ gate` holds for every dashboard ever authored, all become eligible for forward re-seeding,
and every one gains a frame and a rounded radius on next open. Nobody opted in, and geometry changes are
not forgivable the way a colour is.

## Decision 3 — new assemblies inherit the host; copies keep their own mark

**Decided.**

| Action | Mark applied |
|---|---|
| New assembly created on a viewsheet | the **host viewsheet's** mark (which, for a new viewsheet, is the gate) |
| Copy / paste of an existing assembly | the **source asset's** mark, preserved |
| Type conversion of an existing assembly | the **converted object's own** mark, preserved |
| Embedded viewsheet | the embedded asset's own marks, preserved |
| Save As / duplicate of a whole viewsheet | marks preserved |

**Type conversion was added 2026-08-14**, after implementation found the table silent on it. Converting a
range slider to a selection list, or a table to a freehand table, is closer to "bring this thing as it is"
than to "make me one like this dashboard": the object already existed and already had provenance. Taking the
host's mark instead would silently legacy-ify a modern assembly that happened to be sitting in a legacy
dashboard.

**Why the asymmetry is right.** Creating means "make me one like this dashboard"; pasting means "bring this
thing as it is." Modern-ness is more than chrome — `ChartVSAssemblyInfo:94-100` seeds
`barCornerRadius(0.3)` and `smoothLines(true)`, and the palette, gridlines and label colours shift with it
— so a pasted chart forced onto the host's legacy mark would change its geometry and colours, not just its
frame.

**Mixed dashboards are supported, deliberately.** A 24px-row modern table with a 6px frame beside a 20px
square legacy one is an accepted outcome, not a defect to be fixed later.

**Consequences carried:**

- **Modernize on a mixed dashboard** appears when the dashboard holds *any* unmarked assembly and stamps
  only the unmarked ones, leaving pasted modern assemblies alone.
- **Revert on a mixed dashboard** works by construction — it touches marked assemblies, so a pasted
  modern chart inside a legacy dashboard reverts and its host was never modern. **Worth a test case.**
  (Read "the sweep" here as "Revert" since decision 13; the construction argument is unchanged, and the
  case is now reachable by pressing a button rather than only by flipping an org setting, which makes the
  test cheaper to write.)
- **The page background is viewsheet-level** (`ViewsheetVSAssemblyInfo.java:238`), so a mixed dashboard has
  one page colour, taken from the host. Accepted.

## Decision 4 — everything modern follows the mark, including read-time and CSS

**Decided.** The read-time resolvers, the `.viz-modern` / `viz-dark` CSS scope and the density heights all
key off the assembly's mark rather than the gate.

**This reverses shipped `viz-updates` behaviour.** Today the resolvers apply to every dashboard when the
gate is on, so a legacy dashboard in a gate-on product shows modern tooltips, modern selection and modern
chart chrome on legacy square-cornered cards — §03 calls this "modern overlays on a legacy card." Under
decision 2 that would become the permanent steady state for every existing customer dashboard, which is not
acceptable. Both halves move together or the mismatch simply inverts.

**Cost, stated plainly.** This is the largest single piece of work in the set. It touches all five
read-time resolvers, the CSS scope emission, the density heights and the export painters, and **the mark
must reach the browser** — `viz-dark` is currently one class on `body`
(`viewer-app.component.ts:2798`) and becomes a per-assembly scope. §04 already requires per-assembly
density scope, so it is one change rather than two.

## Decision 5 — no automatic forward sweep; Modernize is the only way in

**Decided.** Enabling the gate modernizes nothing that already exists. `Modernize` is a per-dashboard
action, offered only while the gate is on, requiring write permission on the asset, applying modern
defaults **wholesale** to unmarked assemblies and stamping them.

**One composer undo step.** Made possible by decision 10 — because bookmarks are never rewritten, Modernize
is an ordinary sheet-level `@Undoable` operation with nothing outside the sheet to roll back.

**No bulk path, deliberately.** Modernizing changes chart geometry and row heights, so a dashboard will
usually need manual resizing afterwards. A bulk action would produce hundreds of dashboards needing hand
repair. Revisit later if customers ask.

~~**Accepted asymmetry.** Modernize is undoable and not bulk; revert is bulk and not undoable. This is
recorded as accepted, not overlooked.~~ **No longer accepted, because it no longer exists.** Decision 13
makes revert per-dashboard and undoable too. The asymmetry was always the uncomfortable part of this
decision, and it is worth naming why: the argument made here against a bulk *forward* path — that
modernizing changes geometry, so a bulk action produces hundreds of dashboards needing hand repair —
applies unchanged to a bulk *backward* path, which decisions 6 and 7 nonetheless specified. Decision 13 is
in part this decision's own reasoning, applied in the direction it had not been.

## Decision 6 — disabling the gate reverts marked assemblies wholesale, persisted

> **OVERRULED 2026-08-19 by decision 13.** Nothing in this decision was built. Disabling the gate now
> reverts nothing; a per-dashboard Revert action replaces it. Retained in full because three of its
> arguments are still current and are cited elsewhere: the persisted-versus-clone reasoning (which decision
> 13 keeps — Revert is persisted), the rejection of read-time reversal on coverage grounds, and the reason
> the card radius value check is deleted rather than re-pointed at 6px. **What fell with it:** the org-wide
> reach, the USER-tier clearing, the restore point, and the premise that an EM checkbox should rewrite
> saved assets at all.

**Decided.** Unchecking modern visualization sweeps every marked assembly in scope, rewriting the saved
asset to legacy defaults and clearing the mark.

**Scope of "wholesale":** every property **modernizing changes**, across both the DEFAULT tier and the USER
tier. Properties modernizing does not touch — cell-level highlight colours, conditional formats, column
widths, hyperlinks — are out of scope and are not modified. If a property is later added to what
modernizing does, revert must be extended symmetrically. Decision 11's single enumeration point is what
makes that automatic.

**User-tier values are cleared, and that is a product decision, not a technical limit.** Per the tier
model above, resetting the DEFAULT tier alone would leave user values untouched with no ambiguity. They are
cleared anyway, because a value a designer chose to suit the modern look may not work against legacy
chrome.

**Persisted, not read-time.** §03 reverses onto a runtime clone so saved content is never written. Rejected
here: it makes the Format dialog stop describing what is on screen, which is the exact property §02 cites
to justify seeding at creation in the first place. A persisted revert keeps the dialog honest.

**Rejected — read-time reversal (§03's clone route).** Beyond the format-editor divergence, coverage
becomes manual. The existing radius reversal is *getter-level* (`VSCompositeFormat.java:311-316, 322-326`),
so every caller gets it for free; the clone-shaped resolvers are applied by hand and have exactly two call
sites today (`VSSliderModel.java:45`, `VSSlider.java:67`). Any consumer that misses the call renders stale.

**Rejected — re-pointing the value check at the new radius.** The card radius moves 12px → 6px (chart card
spec §01). 12 is safe as a sentinel precisely because it is off every scale and nothing legitimately
produces it; 6 is `--inet-radius-xl` (`_variables.scss:523`) and a plausible `format.css` TableStyle value,
so the false-positive rate rises. The check is deleted, not aimed elsewhere.

**Not recoverable.** Full USER-tier clearing plus a cleared mark plus no bulk Modernize means one EM
checkbox is unrecoverable across an org. **The sweep must write its own restore point before touching
anything**, with a documented restore procedure — the admin flipping the switch is usually not the person
whose work is being replaced.

## Decision 7 — sweep mechanics

> **OVERRULED 2026-08-19 by decision 13**, in its entirety and with nothing salvaged — there is no sweep
> for these mechanics to govern. Retained as the record of what an org-wide sweep would have had to carry:
> asynchronous execution with progress reporting and a completion notification, site-admin authority,
> org-versus-global scope semantics, scheduled-task cancellation and blocking with a manual resume,
> composer-session blocking with a force override, resumability and idempotency across a partial run, and
> an opt-in flag for scripted deployment. **That list is the size of the thing decision 13 removes**, and
> it is why the release gate shrank rather than merely moved.

**Decided.**

| Aspect | Decision |
|---|---|
| Execution | asynchronous, with progress reporting and a completion notification |
| Authority | site admin only |
| Scope | a global-scope flip reverts **all orgs**; `SreeEnv` is org-scoped, so an org-scope flip reverts that org |
| Scheduled tasks | cancelled at sweep start; tasks are **blocked from starting** for the duration |
| Resuming tasks | the admin resumes after the sweep; automatic resume is acceptable if it can be made reliable |
| Open composer sessions | **block the flip**, listing who is editing; the admin may **force** |
| Mid-run scheduled tasks | likewise block the flip until finished, with the same force override |
| Scripted deployment | never converts imported assets by default; opt-in flag only |

**Rejected — "Save As under a unique name" for editors with unsaved work.** It creates an orphan asset that
is modern in a product that just became legacy, carrying a mark the sweep has already passed; it also needs
create permission the editor may not hold. Blocking with a force override is simpler and states the
situation honestly.

**Carried as a known issue:** a forced flip, or a sweep that fails partway, leaves a **half-and-half
estate** — some assemblies reverted, some not, distinguishable only by the mark. The sweep must therefore
be **resumable and idempotent**, and re-running it must be safe.

**Consequence of blocking task starts:** a scheduled report whose window falls inside the sweep does not
fire. Whether it fires late or is skipped is Quartz misfire policy and is **open** (see below).

## Decision 8 — imports carry marks; conversion is opt-in

**Decided.** The mark travels in the asset XML, so a deployed asset arrives with its marks intact. A
modern-marked asset imported into a gate-off org **stays modern** and renders modern there until someone
flips the gate, at which point the sweep catches it. Interactive import may offer conversion; scripted
deployment (shell DSL, deploy API) never converts without an explicit flag.

**Corrected 2026-08-19 by decision 13.** The clause "until someone flips the gate, at which point the sweep
catches it" no longer holds — there is no sweep. An imported modern-marked asset stays modern
indefinitely, in a gate-off org and a gate-on one alike, until someone opens it in the composer and presses
Revert. The rest of the decision is unaffected: the mark travels in the asset XML, and scripted deployment
still converts nothing without an explicit flag.

## Decision 9 — the dark axis: disabling dark mode changes nothing that exists

**Decided.** Only disabling the **modern** gate ever reverts values. Unchecking dark mode affects newly
created assemblies only. A `modern-dark` assembly stays dark — including one pasted into a `modern-light`
dashboard.

**Amended 2026-08-19 by decision 13, and the amendment is a simplification.** The first sentence is now
"**no** gate setting ever reverts values." This decision used to be the exception — one axis of the gate
that changed nothing existing, beside another that swept the org — and being the exception is what made it
need its own justification. Both axes now behave identically: the gate tuple is read once, at creation, and
stamped into the mark. Everything below still stands; it is simply no longer a special case.

**This only works because of decision 4, and without it the specified behaviour renders broken.** Three
values are persisted at creation (`objectBorderColor`, `cardBackgroundCss`, `pageBackgroundCss`).
Everything else dark is read-time and, today, follows the live flag:

| Consumer | Controls |
|---|---|
| `VSChartChromeDefaults.java:53-108` | gridlines, axis labels, chart titles, legend background |
| `VSChartPaletteDefaults.java:64` | the entire chart colour palette |
| `VSOutputChromeDefaults.java:63-89` | slider/gauge track, handle, ticks, value text |
| `VSCalendarChromeDefaults.java:46` | calendar chrome |
| `VSObjectChromeDefaults.java:88-113` | light text foreground |
| `viewer-app.component.ts:2798` | `body.classList.toggle("viz-dark", modern && darkMode)` |

Under the live flag, unchecking dark leaves the card at `#252428`, the page at `#1C1B1F` and the border at
`#49454F` while labels, gridlines, palette and text foreground all snap back to light — **black text and
light chart chrome on a dark card**. Keying the dark resolvers off the mark instead makes decision 9
coherent and closes v1's axis-blindness defect, which had a reproducible failure against today's code.

**UI consequence:** the EM dark-mode setting affects only newly created dashboards. Label it accordingly, or
it reads as a global appearance switch that does nothing.

## Decision 10 — bookmarks are resolved on restore, never rewritten

**The problem.** Bookmark state carries formats, and user bookmarks are written with `runtime = true`
(`RuntimeViewsheet.java:1140`):

| Assembly | What the blob carries | Where |
|---|---|---|
| `TableVSAssembly` | the entire `FormatInfo`, all tiers | write `:157-163`, restore `:186-194` → `setFormatInfo` |
| `ChartVSAssembly` | `<state_descriptor>` — the whole `ChartDescriptor`, incl. `barCornerRadius`, `smoothLines`, both seed booleans, `roundCorners` — and `<state_format>`, the chart's `VSCompositeFormat` | `:474-490` |

Crosstab and calc table carry no formats. So restoring a stale bookmark silently un-reverts a reverted
table or chart, and un-modernizes a modernized one.

**Decided: resolve on restore, running the full revert/modernize operation against the restored object.**
Nothing is written to any bookmark, at any point.

**What makes it work:** the mark is **not in the bookmark**. `AbstractVSAssembly.writeState` (`:625-630`)
emits only the class, the name and `writeStateContent`; the mark lives in `writeAttributes`, which is asset
XML. So restore replaces the `FormatInfo`/descriptor while the assembly's current, correct mark stays in
place, and the same shared code path from decision 11 recomputes against it.

Two call sites: `TableVSAssembly.parseStateContent` and `ChartVSAssembly.parseStateContent`. For charts the
descriptor is a second path — `barCornerRadius` and `smoothLines` resolve from the mark on restore, which
finishes retiring `modernCornerSeed` and `modernSmoothSeed` in bookmarks as well as in assets.

**USER-tier entries are NOT cleared on restore.** After a revert clears the mark, "unmarked" cannot
distinguish *never modern* from *reverted*, and clearing would strip a designer's deliberately-set value
from a dashboard that was never modern. Accepted consequence: a pre-revert bookmark can carry back one of
the designer's own modern-era user values. That is not a new class of wrongness — a stale bookmark
restoring stale user values is what a bookmark is — and the mark still guarantees the chrome, which is the
part that clashes.

**Rejected — rewriting bookmarks at operation time.** It writes other users' private data (a permission
question with no clean answer), needs a snapshot and an extension to sheet-scoped composer undo to satisfy
decision 5, needs `bookmarkmap` invalidation across the cluster (`AbstractAssetEngine.java:4306`), and —
decisively — **cannot cover bookmarks that arrive afterwards**. `DeployManagerService.java:1078-1094`
imports bookmarks from deployment JARs, so a JAR carrying pre-modernize bookmarks imported into a
modernized environment is stale again, permanently, with no operation left to fix it. Resolve-on-restore
covers that by construction.

Enumeration infrastructure exists either way and is worth knowing about:
`AbstractAssetEngine.getBookmarkUsers` (`:4235`) and `clearVSBookmark` (`:4500-4501`) already perform the
"for every user's bookmark of this asset" loop, and `VSBookmark.getIncompatibilities` (`:303+`) is
precedent for parsing into blobs.

**Accepted cost:** bookmark blobs on disk stay stale forever, so anyone inspecting one sees values that
differ from what the restored dashboard shows. These are opaque internal state; acceptable.

## Decision 11 — one enumeration point: a virtual method delegating to a static helper

**The requirement.** Creation, Modernize, revert and bookmark restore must all agree on which properties
are in scope and what their values are. If any of them knows the list independently, it drifts — and the
drift has already happened once: the object border colour has been seeded at `VSAssemblyInfo.java:1162`
and reversed nowhere since before any reverser existed.

**Decided: `setDefaultFormat` keeps installing the composite at creation; the default-tier computation
moves to a `protected` virtual method that all four paths call. That method delegates its constants and
shared computation to a static helper.**

**Why virtual and not a static helper alone.** Seeding is already polymorphic:
`ChartVSAssemblyInfo:86-100`, `TableDataVSAssemblyInfo:1568` and `ViewsheetVSAssemblyInfo:238` each
override `setDefaultFormat`, call `super`, then add their own type-specific seeded values. A static helper
cannot be extended by a subclass, so it would need either an `instanceof` switch inside it — a second copy
of the type knowledge `isCornerSeedTarget()` (`VSAssemblyInfo.java:1232-1238`) already holds — or a
convention that each subclass calls the helper and then adds its own values, which is precisely the drift
being prevented. With a virtual method, all four paths dispatch on the same object and pick up each type's
contribution for free.

**Why the static helper is still there.** One home for the constants and the shared computation;
polymorphism only where per-type contribution is genuinely needed.

**Contract the virtual method must honour: it mutates the format it is given and never installs a new
composite.** Installing a fresh `VSCompositeFormat` is what makes "just call `setDefaultFormat` again"
unusable — `:1207` → `setFormat(fmt)` → `fmtInfo.setFormat(OBJECTPATH, fmt)` (`:289-291`) leaves the new
composite's USER tier empty, which is harmless at creation and destroys an existing dashboard's formatting
on recompute.

**Carried:** the extracted method keeps the `format.css` TableStyle branch (`:1182-1188`), so revert and
Modernize both pick up the customer's current table style rather than dropping it. Note its real scope —
tables only, and only when `border` is true. The property list is not simply "what is in format.css."

**Pre-existing oddity, deliberately not changed.** The title border colour is set at `:1180`, *before* the
stylesheet override at `:1182-1188`, so a table's stylesheet colour reaches the object border but never the
title border. This predates all of this work. A refactor is exactly when someone will notice and "fix" it,
which would change rendering. Preserve the ordering unless someone confirms it is a bug.

**Rejected — a second routine that rewrites only the gate-derived properties.** Safe today, drifts
tomorrow. Someone adding a seeded default edits `setDefaultFormat`, because that is where they are working,
and never touches the reverser — different file, not referenced, nothing fails.

## Decision 12 — revert calls the legacy creation path; the four old mechanisms are deleted

**Decided.** Revert produces exactly what a legacy-created assembly produces, by calling the same code
path, rather than answering "what is the legacy value" property by property.

**Why it matters.** `null` and `0` are not interchangeable here: `setRoundCornerValue(0)` writes an explicit
zero and makes `isRoundCornerValueDefined()` true, while leaving it unset means undefined, and the two
resolve differently through the tiers. Routing revert through the creation path means the question never
has to be answered per property.

**All four existing mechanisms are deleted**, once the mark has landed and been verified:

- `VSObjectChromeDefaults.resolveSeededCorner()` (`:79-81`) and the tab carve-out in
  `VSCompositeFormat.resolveDefaultTierCorner()` (`:334-337`). The exemption exists only because
  `FormatInfo.copyDefaultFormat` can launder a user radius onto a tab's default tier where it may equal the
  seed — no value test, no exception.
- `PlotDescriptor.modernCornerSeed` (`:1315`, `:2005`) and `modernSmoothSeed` (`:631-633`, `:1996`).

**Worth preserving from mechanisms 2 and 3:** both self-clear on user write (`setSmoothLines` `:645-649`,
`setBarCornerRadius` `:1334`) under the comment "an explicit write makes the value user-authored, so it
stops tracking the gate." The mark needs no equivalent — it is not consulted while the gate and mark agree,
and decision 6 clears user values deliberately — but the reasoning is recorded here rather than lost with
the code.

**Ordering note.** Deleting the value check also removes the only path by which `getRoundCorner()` reaches
`SreeEnv` — via `resolveSeededCorner` → `isModern()` — taking two org-scoped property reads out of a format
getter that runs in loops.

**Sequencing.** Nothing here may be deleted before the mark has landed and been verified; doing so leaves a
window with no reversibility at all.

**Sequencing amended 2026-08-19 by decision 13, and two of the four deletions are no longer optional.** The
trigger moves: these were to be deleted "once the sweep lands," and there is no sweep, so they are deleted
when the Revert phase lands. More than that, `PlotDescriptor.modernCornerSeed` and `modernSmoothSeed`
**must** go in that same phase rather than after it. Both getters test the org gate directly
(`isSmoothLines()` `:635`, `getBarCornerRadius()` `:1319`), and after P4 they are the only reads that still
do — so the moment the gate stops meaning "render legacy," a marked chart in a gate-off org loses its bar
radius and smooth lines while everything else on it stays modern. Deleting them requires the matching `else`
branch in `ChartVSAssemblyInfo.seedChromeDefaults` (`:106-112`), which is forward-only today precisely
because those two booleans did the reversing. `resolveSeededCorner` and its tab carve-out keep the original
sequencing: they may go once Revert exists, because Revert is the reversal path their deletion needs.

The ordering note below gains a second beneficiary: deleting the two `PlotDescriptor` booleans takes two more
org-scoped `SreeEnv` reads out of getters that run inside chart render loops.

---

## Decision 13 — revert is opt-in and per-dashboard: the mirror of Modernize

**Decided 2026-08-19. Overrules decisions 6 and 7**, which stay above with the reasoning that produced them.

**The rule.** The gate is a creation-time switch and nothing more: it decides what a newly created assembly
is stamped with, and whether Modernize is offered. Turning it off changes nothing that already exists — a
marked assembly keeps its mark and goes on rendering modern in a gate-off product. **Revert** is a
per-dashboard composer action, the exact mirror of Modernize: it clears the mark on every marked assembly of
the sheet's own content and re-seeds each one through the same hook creation uses, with an unmarked context.

| | Modernize | Revert |
|---|---|---|
| Trigger | a person, one dashboard | a person, one dashboard |
| Acts on | this sheet's **unmarked** assemblies | this sheet's **marked** assemblies |
| Offered when | gate on, sheet has unmarked content | sheet has marked content, gate state irrelevant |
| Authority | write permission on the asset | write permission on the asset |
| Reversal | one composer undo step | one composer undo step |
| Tiers written | DEFAULT only, via `seedChromeDefaults` | DEFAULT only, via `seedChromeDefaults` |
| Bulk path | none, deliberately (decision 5) | none, deliberately — same argument |

**Revert is offered regardless of the gate**, and that is a feature rather than an oversight: an author who
does not want modern chrome on one dashboard in a modern org can say so. It is the same freedom decision 3
already grants in the other direction when it supports mixed dashboards.

**Both directions run through decision 11's enumeration point, and that is the whole guarantee.**
`VSAssemblyInfo.seedChromeDefaults(VizContext)` already writes the legacy value on the false branch of every
ternary it holds — `DEFAULT_BORDER_COLOR` and radius `0` at `VSAssemblyInfo.java:1253-1258`, the legacy card
background in `ChartVSAssemblyInfo`'s override. So decision 12's "revert calls the legacy creation path"
needs no reverser written at all: Revert is `seedChromeDefaults(VizContext.of(info))` on an assembly whose
mark has just been cleared, which is the identical call creation makes. A property added to what modernizing
does is reverted by the same edit to the same method, or it is not added at all.

**Revert writes the DEFAULT tier only — a deliberate departure from decision 6's "wholesale."** Decision 6
also cleared USER-tier values, reasoning that a value a designer chose to suit modern chrome may not work
against legacy. That justification belonged to an org-wide sweep the designer did not ask for. Under an
action a person presses on their own dashboard, the symmetric rule is both safer and the one that cannot
drift: **Revert undoes exactly what Modernize does, because they are the same method.** Modernize has never
touched the USER tier, so neither does Revert. A designer who tuned colours for modern chrome keeps them
after reverting, and can change them.

### Why this is the better rule

Four arguments, every one of them from a decision already in this file.

1. **It is the rule this file already chose, applied in the direction it had not been.** Decision 2 says
   unmarked content is never touched by any automatic behaviour, forever. Decision 5 rejects an automatic
   forward sweep and makes Modernize the only way in. Decision 6 was the single remaining place where
   anything automatic rewrote a saved asset, and it was the odd one out. One rule now covers all three: the
   gate governs creation, the mark governs rendering, and two symmetric per-dashboard actions move an asset
   between them.
2. **Decision 6 named its own worst property and could not fix it** — "the admin flipping the switch is
   usually not the person whose work is being replaced," which is exactly why it needed an automatic restore
   point and a documented restore procedure. A per-dashboard action puts the decision behind write
   permission on that asset, in front of the person whose work it is, and turns the restore point into an
   ordinary undo step.
3. **Decision 5's own argument against bulk was never applied to the reverse direction.** It rejects a bulk
   forward path because modernizing changes chart geometry and row heights, so a bulk action "would produce
   hundreds of dashboards needing hand repair." Reverting changes the same geometry back, so a bulk backward
   path produces the same repair bill — and decisions 6 and 7 specified it as the automatic consequence of a
   checkbox, with nobody asking.
4. **It makes the two axes of the same gate behave identically.** Decision 9 already says unchecking dark
   mode changes nothing that exists. It needed its own justification only because the modern axis behaved
   differently. Now the gate tuple is read once, at creation, and stamped into the mark; neither axis ever
   reaches back.

Two consequences beyond the argument, both worth having: the release gate shrinks from an async sweep engine
with a restore point, scheduler blocking and composer-session blocking to one composer action of roughly P3's
weight; and two of this file's three open items — Quartz misfire policy and guaranteed scheduler resume —
stop existing, because both exist only to serve a sweep that blocks the scheduler.

### The drawback, stated plainly

**There is no longer an org-wide off switch with teeth.** A customer who adopts modern across three hundred
dashboards and then reverses course visits three hundred dashboards. Nothing in this decision softens that,
and it should not be presented as though something does.

Two things reduce the sting without bringing the sweep back. A **scripted bulk revert** — shell DSL or the
deploy API, over an explicit list of assets — needs none of the sweep's machinery, because it is a
deliberate operation somebody schedules rather than a side effect of a setting: no composer-session
blocking, no scheduler blocking, no restore point beyond the customer's own backups. And decision 5's
disposition applies here too: **revisit if customers ask.** Neither is committed work.

What is genuinely given up is the ability to undo an adoption decision in one click. That is the trade:
the sweep's whole apparatus, and its unrecoverable org-wide blast radius, against a rollback that does not
scale.

**A second, smaller cost: the EM property now says something it does not do.** "Modern Visualization: off"
while modern dashboards go on rendering modern reads as a bug to anyone who does not know the model. The
property wants a rewritten description — it governs newly created content and whether Modernize is offered —
and possibly a rename. Not decided here; flagged for whoever writes the phase.

### What it forces in code, none of it deferrable

Three items, all found by reading `viz-updates` at `8ef511e45`, and all belonging to the Revert phase itself
rather than to a later one.

- **The `gate &&` term is deleted.** `VizContext.of(VizMark)` computes `modern = VSDensityDefaults.isModern()
  && mark != null` (`VizContext.java:66`). The forward-half design calls that term the whole of the interim
  reversibility story and expects the sweep to delete it; the Revert phase deletes it instead. One line.
- **`PlotDescriptor.modernCornerSeed` and `modernSmoothSeed` are deleted with it**, not after it. See the
  amended sequencing note in decision 12: after P4 their two getters are the only reads still testing the org
  gate, so leaving them in place while the gate stops meaning "render legacy" strands marked charts with
  square bars and unsmoothed lines under otherwise modern chrome.
- **`ChartVSAssemblyInfo.seedChromeDefaults` gains its `else` branch.** It is forward-only today —
  `if(ctx.modern)` sets `barCornerRadius` and `smoothLines` with nothing on the false side (`:106-112`) —
  precisely because the two booleans above did the reversing. Once they are gone, the hook has to write both
  legacy values itself, which is also what makes Revert complete for charts.

Net effect on the codebase is subtractive: two persisted booleans, two XML attributes, one predicate term and
two org-scoped `SreeEnv` reads out of getters that run inside chart render loops.

### Known issue the phase has to settle

**Reverting while the gate is on immediately re-arms Modernize.** `modernizable` is recomputed on every
refresh (`CoreLifecycleService.java:314`) as `isModern() && hasUnmarked(vs)`, so a just-reverted sheet
qualifies the instant the refresh lands and the bar returns, offering to modernize what was reverted a second
ago. The bar is dismissable per composer session, so the practical cost is one dismissal — but the phase
should decide whether a sheet reverted in this session suppresses the offer for the rest of it.

### Rejected alternatives

**Rejected — keeping the `gate &&` term permanently as a read-time kill switch**, with per-dashboard Revert
for the persisted half. It buys a partial org-wide off switch, and partial is the whole problem. The
forward-half design's §2 already records that the term does not reach the three persisted colour seeds —
`objectBorderColor`, `cardBackgroundCss` and `pageBackgroundCss` are stored values read straight off the
DEFAULT tier with no predicate in front of them. Flipping the gate off would therefore give legacy read-time
chrome over modern border, card and page colours; where decisions 6 and 7 made that an interim state the
sweep cleaned up, this would make it the permanent steady state for anyone who flipped off. The term would
also survive forever in every predicate that resolves a context. A kill switch that leaves the colours wrong
is worse than no kill switch, because it looks like a defect rather than a choice.

**Rejected — an EM bulk revert with the sweep's mechanics behind a button instead of a checkbox.** That is
decisions 6 and 7 with a different trigger: the same restore point, the same composer-session and scheduler
blocking, the same half-and-half estate after a partial run. If bulk is ever wanted, the scripted shape above
is the cheap one.

**Rejected — reverting on load when the gate is off.** It is the automatic sweep again, spread over time and
made harder to reason about: content changes under a user who only opened a dashboard, the write happens
outside any undo scope, and a read-only viewer session has no business rewriting a saved asset.

---

## Accepted costs and known issues

**The pre-mark cohort is written off.** Assemblies created with the gate on before the mark exists carry
seeded modern values with no mark. Under decision 2 they are never touched, so they keep persisted modern
colours and radius while decision 4 gives them legacy read-time chrome — a permanent half-and-half state.
`viz-updates` has never shipped, so every member is a disposable dev or test dashboard.

**Rejected — retro-marking on load.** Identifying "carries seeded values" requires the value check being
deleted, and it would misfire on exactly the case that check gets wrong: content whose stylesheet
legitimately set the same value.

**Rejected — a one-off migration task.** Correct in principle, and it would run over content nobody needs.

**The constraint this creates:** the write-off holds **only while the branch is unreleased**. After release
the cohort is customer data, indistinguishable and unfixable. If the mark has not landed by the time
release is planned, revisit rather than inherit.

~~**Half-and-half estates during a flip.** A forced flip past the composer-session block, or a sweep that
fails partway, leaves some assemblies reverted and some not. The sweep must be resumable and idempotent.~~
**Gone with decision 13** — there is no flip to be caught halfway through. Mixed estates remain possible and
remain *supported*: decision 3 has always allowed a dashboard to hold marked and unmarked assemblies side by
side, and per-dashboard Revert produces the same shape one dashboard at a time. The difference is that a
mixed estate is now something someone chose rather than the residue of a failed sweep.

~~**Revert is not recoverable.** See decision 6. The automatic restore point is a requirement, not a
nicety.~~ **Gone with decision 13.** Revert is one composer undo step, on one dashboard, taken by someone
with write permission on it. The automatic restore point and its documented restore procedure are no longer
needed because the blast radius is one sheet and the person holding it can undo.

~~**A blocked scheduled window does not fire.** See decision 7 and the open item below.~~ **Gone with
decision 13** — nothing blocks the scheduler any more.

**New with decision 13: there is no org-wide off switch with teeth.** Backing out of modern across an estate
is a per-dashboard job. Fully stated in decision 13's "The drawback"; repeated here because this is the
section someone reads when they want the honest list.

**New with decision 13: the EM property's label outlives its meaning.** "Modern Visualization" off no longer
makes anything look legacy; it stops new content being modern and hides Modernize. The description needs
rewriting or the property renaming.

---

## Still open

1. ~~**Quartz misfire policy for tasks blocked during a sweep.**~~ **Closed 2026-08-19, by obsolescence.**
   Decision 13 blocks no scheduled tasks, so no window is ever missed and there is no policy to pick.
2. ~~**Guaranteed scheduler resume.**~~ **Closed 2026-08-19, by obsolescence.** Nothing stops the scheduler,
   so nothing has to be guaranteed to restart it.
3. **The card radius 12 → 6px.** Decided in chart card spec §01 and sequenced behind the mark. **Its blocker
   moved on 2026-08-19 and got cheaper:** retiring `resolveSeededCorner` needs *a* reversal path, which used
   to mean the sweep and now means the Revert action — so this unblocks a phase earlier than the roadmap
   recorded. Note that
   half its rationale has moved: the title-lane "lozenge" argument assumed a filled title band, and the
   chart card track has since decided the band draws no fill. The scale argument (12 is off a scale topping
   out at 6) still stands on its own.

---

## For the sibling project

Five things §03 does not currently account for:

1. **There is a fourth mechanism.** `modernSmoothSeed` (`PlotDescriptor.java:631-633, 1996`). The
   consolidation ticket is scoped to three.
2. **The gate seeds four values, not one.** Object border colour, card background and page background are
   persisted at creation alongside the radius, and only the radius is reversible. The ticket exempts the
   colours as "computed live at read time"; they are `set…Value` calls on a default format. Its own
   scenario — "add a second seeded default after the mark ships" — already happened three times before the
   mark was written.
3. **Version-blindness needs no schema version.** A mark records *when* an assembly was made, not which
   defaults existed then, but recomputing under the current rules produces the correct value regardless.
   It would matter only if reversal were subtractive, and it is not.
4. **Axis-blindness was the real defect, and decision 9 answers it.** A chart created under modern + dark
   keeps its dark card background after dark is switched off, because the mark and the modern gate still
   agree and nothing recomputes. Fixed by storing the gate tuple and keying the dark resolvers off it.
5. **Bookmarks carry formats.** `TableVSAssembly` carries the whole `FormatInfo`; `ChartVSAssembly` carries
   the `ChartDescriptor` and a `VSCompositeFormat`. Any reversal scheme that ignores them is silently
   undone the next time a user opens an old bookmark. Decision 10 has the shape that costs least.

And the departures they should weigh, all listed in the table at the top: reversal is persisted rather than
clone-based, there is no automatic forward re-seed, and `gate-off` is not a stored state. **A fourth was
added 2026-08-19 by decision 13, and it is the largest:** the gate reverts nothing in either direction. §03
has the gate driving reversal; here it drives creation only, and both transitions — Modernize and Revert —
are per-dashboard actions a person takes.

---

## Related

- [chart-card-source-doc-corrections.md](./chart-card-source-doc-corrections.md) §3.2 — the audit entry this
  file replaces the analysis for
- [chart-card-open-item-decisions.md](./chart-card-open-item-decisions.md) — the chart card's own decisions
- [chart-card-roadmap.md](./chart-card-roadmap.md) — where the seed mark sits in the dependency picture
- `chart-card-design3/Seeded value reversibility - ticket.md` — the external ticket
- `chart-card-design3/Visualization Widget Spec.dc.html` §03 — the mark's specification
