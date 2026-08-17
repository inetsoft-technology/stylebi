# Seeded Value Reversibility — Decisions

**Date:** 2026-08-12 (v2 — supersedes the 2026-08-12 v1 decision set in this file); addendum 2026-08-13
**Verified against:** community `viz-updates` @ `881a9b049` for v2; the addendum against `c8011beca`
**Concerns:** the seed mark (`Visualization Widget Spec.dc.html` §03, sibling-owned, not built) and
`chart-card-design3/Seeded value reversibility - ticket.md`
**Status:** twelve decisions taken; three items open, all small. **Read the addendum below before planning
the mark** — `userTitleHeight` has shipped since v2 and changes how decisions 4 and 11 should be read

## Why this file exists, and what changed in v2

The gate writes values into saved dashboards at creation. Turning the gate off has to un-write them.
v1 of this file analysed that against the widget spec's tri-state seed mark and left three questions to
the sibling project. v2 records a **product decision set taken here** that answers all three and replaces
most of §03's mechanism, while keeping its central idea — a provenance mark on the assembly.

The three substantive departures from §03:

| | §03 as specified | Decided here |
|---|---|---|
| Reversal shape | recompute the DEFAULT tier onto a **runtime clone**; saved content never written | **persisted wholesale revert**; the saved asset is rewritten |
| Forward direction | `mark ≠ gate` re-seeds an assembly forward automatically on load | **no automatic forward sweep**; modernization is opt-in via a button |
| Mark states | tri-state: `gate-on` / `gate-off` / absent | **unmarked / modern-light / modern-dark**; `gate-off` is not stored |

Everything below cites a file and line. Where this contradicts the ticket or §03, the code was read.

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
5. **Disabling the modern gate reverts marked assemblies wholesale**, in a persisted org-wide sweep, and
   clears their marks. Nothing else ever reverts anything.
6. **Modernize** is the mirror: manual, per-dashboard, gate-on only, stamps unmarked assemblies and
   applies modern defaults wholesale.
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
- **Revert on a mixed dashboard** works by construction — the sweep touches marked assemblies, so a pasted
  modern chart inside a legacy dashboard reverts and its host was never modern. **Worth a test case.**
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

**Accepted asymmetry.** Modernize is undoable and not bulk; revert is bulk and not undoable. This is
recorded as accepted, not overlooked.

## Decision 6 — disabling the gate reverts marked assemblies wholesale, persisted

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

## Decision 9 — the dark axis: disabling dark mode changes nothing that exists

**Decided.** Only disabling the **modern** gate ever reverts values. Unchecking dark mode affects newly
created assemblies only. A `modern-dark` assembly stays dark — including one pasted into a `modern-light`
dashboard.

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

**Half-and-half estates during a flip.** A forced flip past the composer-session block, or a sweep that
fails partway, leaves some assemblies reverted and some not. The sweep must be resumable and idempotent.

**Revert is not recoverable.** See decision 6. The automatic restore point is a requirement, not a
nicety.

**A blocked scheduled window does not fire.** See decision 7 and the open item below.

---

## Still open

1. **Quartz misfire policy for tasks blocked during a sweep.** A nightly report whose window falls inside
   the sweep either fires late or is skipped. Someone has to pick, and the choice should be stated in the
   admin warning.
2. **Guaranteed scheduler resume.** Manual resume by the admin is the decided default, but a sweep that
   dies mid-run must not leave scheduling stopped silently — at minimum a visible state, ideally a timeout.
3. **The card radius 12 → 6px.** Decided in chart card spec §01 and sequenced behind the mark. Note that
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
clone-based, there is no automatic forward re-seed, and `gate-off` is not a stored state.

---

## Related

- [chart-card-source-doc-corrections.md](./chart-card-source-doc-corrections.md) §3.2 — the audit entry this
  file replaces the analysis for
- [chart-card-open-item-decisions.md](./chart-card-open-item-decisions.md) — the chart card's own decisions
- [chart-card-roadmap.md](./chart-card-roadmap.md) — where the seed mark sits in the dependency picture
- `chart-card-design3/Seeded value reversibility - ticket.md` — the external ticket
- `chart-card-design3/Visualization Widget Spec.dc.html` §03 — the mark's specification
