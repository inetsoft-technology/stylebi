# Chart Card — Roadmap

**Date:** 2026-08-13 (third revision — the density gating and the userTitleHeight flag both committed)
**Verified against:** community `viz-updates` @ `8357e05d8`, which is `HEAD`. The code baseline is
`307a6ee09`; `55c3bad1a` shipped the density gating and `07c91926e`/`307a6ee09` the userTitleHeight flag.
Every code claim below was re-checked against it.
**Covers:** the chart card track — the anchored toolbar rollout, the shell and chart surfaces found through
it, and the decisions that gate what remains

**Before syncing the external design set, read
[chart-card-source-doc-corrections.md](./chart-card-source-doc-corrections.md).** It records where that set
has been wrong about this code, and corrections applied to the set itself have a lifetime of exactly one
sync — the 2026-08-12 regeneration overwrote four of them. This pointer lives here, outside
`chart-card-design3/`, because a pointer placed inside the set is deleted with the set.

**Verify before trusting.** This branch moves daily. Every claim below cites a commit or a file so it can be
checked rather than believed. If a claim and the branch disagree, the branch is right.

**Hashes go stale when the branch is rewritten, and it has been.** The rebase that produced `2310cea37`
gave every commit in the Done table a new hash; the ones this file cited until 2026-08-13 still resolved
with `git show`, because the old objects were still in the repo, while being unreachable from `HEAD` —
they would fail in a fresh clone and after a `gc`. The table below carries the current hashes. When
checking any hash in this tree, use `git merge-base --is-ancestor <hash> HEAD` rather than `git show`;
`git show` succeeding proves nothing. Other files under `docs/superpowers/` still carry the pre-rebase
hashes.

## How to read this

The durable content is the **dependency picture** and the **seed-mark cluster** — those describe structure
and change only when something lands. The perishable content is what is done, so that section is a table of
item → commit rather than a checklist: `git show` answers it better than a checkbox does.

**"What to pick up next" is the most perishable thing here.** It is a ranking derived from the picture at
one moment, carrying the date and commit it was derived at. When it and the picture disagree, re-derive it
rather than repairing it — the whole of its content is one reading of the two paragraphs above it.

---

## The dependency picture

```
  L. Strip density gating ──→ F. Rollout slices 4–5
     SHIPPED 55c3bad1a          NO LONGER BLOCKED · container, calendar
     the interim · L'' replaces it

  N. userTitleHeight flag ──┐
     SHIPPED 07c91926e      │
     307a6ee09              ├──→ L'. Title lane height row ──→ L''. Geometric suppression
                            │    DESIGN ANSWERED, NOT BUILT      DECIDED 2026-08-13
  M. Seed mark (below) ─────┘    20/26/30 · titleHeight() exists  MUST NOT PRECEDE L'
                                 but is uncalled                  replaces L's density test
                                 decisions 5-8 · SCHEDULED AFTER M

     N answers "did the author choose this height" · M answers "is this assembly modern"
     The row needs both. On the flag alone it resizes every dashboard ever saved.

  M. Seed mark — widget spec §03 ──┬──→ L' above, and §04's other density heights
     DECIDED IN FULL, NOT BUILT    │
     seeded-value decisions 1–12   ├──→ Card radius 12→6, retire resolveSeededCorner()
     ALSO THE RELEASE GATE         │
                                   ├──→ §07 derived selection, retire the teal family
                                   │         │
                                   │         └──→ Range slider — painter half
                                   │
                                   └──→ Outlined text conversion (also behind G)

  G. Chart type scale ──→ H. Outlined text conversion
     needs one measurement

  Ungated: dark (four DOM surfaces) · chart interior dark palette · affordance sweep ·
           selection list interior · Resize Plot sliders · nav bar · data-tip registry
           remainder · chart colour literals · drill and DC tips · zoom naming ·
           dead menu icons · title band
```

**The one hard sequencing rule in this picture: L'' must not ship before L'.** Geometric suppression
compares the assembly's real lane against a 26px threshold (24px strip plus 1px of clearance above and
below). Until the lane row lands, every assembly still carrying `AssetUtil.defh` has a 20px lane at
*every* density, so the threshold fails everywhere and the strip disappears from the whole anchored set —
not just dense. L as written is density-keyed and does not have this property, which is why it ships as
the interim and is replaced rather than amended. See
[chart-card-anchored-strip-lane-decisions.md](./chart-card-anchored-strip-lane-decisions.md) decision 3.

Four corrections this picture carries against the external set, all recorded in
[the corrections doc](./chart-card-source-doc-corrections.md):

- **M does not gate F.** The toolbar rollout writes no persisted state — every file in all three shipped
  slices is under `web/projects/portal/src` — so there is no reversibility record for it to miss. M does not
  block F, but it does change it: the forward-half design's §3 has the mark turning F's gate test into a mark
  test. Both v3
  documents that draw this reverted to a pre-correction body, and the handoff went further: it now orders
  `M precedes F (and H)` and says the calendar and container slices "should wait for the mark." They should
  not, for the same reason the three shipped slices correctly did not. See §5.1 and §5.4.
- **L vanished from the external dependency picture.** v3's handoff diagram has no node for the title lane
  or the density gating, though widget spec §08 step 3 still claims to unblock the rollout. The one item
  that does precede slices 4–5 was dropped in the sync that reinstated one that does not.
- **M *does* gate the height row.** The widget spec's §05 says heights move only for marked gate-on
  assemblies, and `titleHeight()` has no per-assembly condition because there is no field to condition on.
  The 2026-08-11 edition of this roadmap said the row "needs no server change"; that was wrong twice over.
  See §3.3.
- **The 32px chrome floor does not reach dense.** It is a card-height rule, not a lane rule, so the v3
  reversal in §05 does not follow from it. See §1.1. This was the live decision until 2026-08-12; the
  outcome was adopted and the mechanism written explicitly rather than inherited, and it shipped in
  `55c3bad1a`.

---

## What to pick up next

**Ranked 2026-08-13, re-derived against `8357e05d8` after N shipped.** This is a reading of the picture
above, not a new decision — it goes stale as things land, and the picture is what to re-derive it from.
Effort is relative to this track, not absolute.

| # | Item | Impact | Effort | Unblocks | Risk |
|---|---|---|---|---|---|
| 1 | **M — the seed mark** | **highest** — the release gate, and now L''s blocker too | **XL** | six items | reverses shipped `viz-updates` behaviour |
| 2 | **L' — the title lane height row** | high, visible | **M–L** | L'' | scheduled after M (decision 8); export-affecting; carries UI work |
| 3 | **Rollout slices 4–5** | closes the rollout | **S–M** | nothing | modifies shipped slice-3 behaviour |
| 4 | **Dark — four browser surfaces** | fixes a visible defect | **S** — plan written | nothing | reworked when M lands, see below |
| 5 | The ungated cheap items | low each, additive | **S** each | nothing | none |

**M moved to the top in this revision.** It was third when L' looked startable on its own. It is not: the
row needs the mark to avoid resizing legacy content, so the mark now gates six things rather than five,
and every one of the track's remaining large items sits behind it.

**L' is the highest-value item, and its four design questions are now answered — but it is not startable
on its own.** `VSDensityDefaults.titleHeight()` resolves defh/26/30 and is still uncalled, and
`userTitleHeight` now tells an author's height from a default one. That is one half of what the row needs.

**The other half is the seed mark, and it is the correction this ranking previously got wrong.** An
earlier revision called the flag a cheaper alternative to the mark. They answer different questions:
the flag says *did an author choose this height*, so the row does not overwrite a deliberate choice; the
mark says *is this assembly modern*, so the row does not reach dashboards nobody opted in.
[Seeded-value decisions](./seeded-value-reversibility-decisions.md) decision 4 keys the density heights
off the mark, and decision 2 protects unmarked content from every automatic behaviour. **Shipping the row
on the flag alone would resize fifteen years of saved dashboards on next open.**

**Decided 2026-08-13: L' waits for M** ([strip and lane decisions](./chart-card-anchored-strip-lane-decisions.md)
decision 8). Shipping it dormant looks like free parallelism and is not — nothing would be marked, so the
manual export pass could not run, the checkbox would ship doing nothing observable, and the only question
a dormant build answers early is the cheapest part of the work. The cost is accepted: the title lane keeps
its legacy 20px everywhere until the mark lands, the anchored strip's density approximation
(`55c3bad1a`) stays in place longer, and L'' stays behind L'.

### The four L' design questions — answered 2026-08-13

All four are recorded in
[chart-card-anchored-strip-lane-decisions.md](./chart-card-anchored-strip-lane-decisions.md) decisions 5,
6 and 7. Summarised here because this is where an implementer will look first.

**1. The calendar is included** (decision 6). Its 36px default is legacy, not structural: the painter
already floors the lane at the font height (`VSCalendar.java:562`) and the navigation band is a separate
header, so a 20px lane cannot clip it. Existing calendars keep 36 regardless, because the mark protects
unmarked content — only newly created ones take the density sizing. One naming consequence: with two
defaults in play, `getDefaultTitleHeight()` wants renaming to say it means the *legacy* one, or someone
will later "correct" the 36 and break the derive for every calendar ever saved.

**2. The affordance ships with the row** (decision 5). L' is therefore a resolver change **plus** a
dialog-model change, a template change and eleven call-site rewrites — budget it as M–L, not M.

**3. The flag joins `TitleInfo.equals()`** (decision 7). The audit that makes this safe is done:
`equals()` has exactly eight consumers repo-wide, all of them the `copyViewInfo` guard, and nothing
outside `uql/viewsheet/internal/` compares a `TitleInfo` at all. Adding one line fixes the cause;
copying explicitly in eight places would patch the symptoms and leave `equals()` lying.

**4. The control is a checkbox — "follow the default density" — and it covers table row height as well as
title height** (decision 5). Row height already carries `userDataRowHeight` and selection cell height
carries `userCellHeight`; all three infer authorship the same way and inherit the same defects. The
checkbox also gives every type the opt-back-in that today exists only through the table reset-layout
action. One sub-question stays open: what an assembly already carrying the flag shows the first time the
control appears.

Three smaller ones can be settled while writing rather than before: whether 1px of clearance is enough at
compact, the 44px touch target against a 26px lane, and `--inet-viz-chrome-row-height` sitting at 22px
while the dense lane row is 20px (`_viz-tokens.scss:112`, no consumers yet). All three are in the strip and
lane decisions' "Still open".

**And budget the export pass.** Title height is a persisted value the Java painters read, so the row moves
PDF, PNG, Excel and scheduled output and shifts everything below the title. This is not a CSS change.

**M is the largest impact and the wrong thing to start cold.** It gates five items and the release, and
[the decisions](./seeded-value-reversibility-decisions.md) note the pre-mark cohort write-off holds *only
while the branch is unreleased*. But decision 4 alone is called the largest single piece of work in that
set — all five read-time resolvers, the export painters, and `viz-dark` moving from one body class
(`viewer-app.component.ts:2798`) to a per-assembly scope — and decisions 6 and 7 add a resumable org-wide
async sweep with a restore point, composer-session blocking and scheduler interaction. It wants its own
plan and its own branch. **If a release date is set, M's schedule is the thing to work backwards from**,
because after release the cohort is customer data and the write-off is no longer available.

**The interaction to know before taking #5.** The dark browser-surfaces plan declares its four tokens in
the existing `.viz-dark` block at `_viz-tokens.scss:143-159`. Seeded-value decision 4 turns `viz-dark`
from a body class into a per-assembly scope. The rework is mechanical rather than structural, but if M is
imminent the dark work is cheaper after it than before it.

**Two that look ready and are not.** The unfilled title band is M-independent and decided, but it breaks
the title-bar/table-header equality the sibling project endorses — its blocker is sign-off, not code, so
raise it now and it clears by the time it is wanted. And the range slider's browser half must not ship
without its painter half ([decisions](./chart-card-open-item-decisions.md) §3), which sits behind M.

---

## The density gating — shipped in `55c3bad1a`

Twelve files, two under `core/` and ten under `web/projects/portal/src`. This section described them as
in flight until 2026-08-13; they are committed, and the two things that outlive the commit are the
decision it records and the interim it leaves behind.

| Piece | Where it landed |
|---|---|
| Anchored strip gated to compact-and-above | `GuiTool.vizDensityMode()` + `isVizDensityAtLeastCompact()`; one shared `isAnchoredResident()` in `mini-toolbar.service.ts` consumed by both `AbstractVSActions.resident` and `VSObjectContainer.isKebabResident` |
| Dense draws no chrome at all | `isAnchoredChromeSuppressed()`, consumed by `showingActions` beside the existing 32px floor test |
| `height: 24px` → `--inet-control-height-sm` | Value-identical; `_variables.scss:475` |
| `VSDensityDefaults.titleHeight()` at defh/26/30 | `:94`, and deliberately **uncalled** — `grep` finds no caller but its own test |
| Wiring that height into five assemblies | **Not in the commit.** The first attempt was abandoned; the work itself is superseded by N → L', not dropped |

**What shipped is the interim, not the rule.** The commit message is explicit: "the lane test is
approximated by density for now." Density selects the lane's default height, which puts dense alone below
the bound; the direct comparison is L'' and still waits on L'. Do not read `55c3bad1a` as having
implemented [strip and lane decisions](./chart-card-anchored-strip-lane-decisions.md) decision 3.

**The decision the v3 sync reopened is settled: v3 is accepted** —
[decisions](./chart-card-open-item-decisions.md) §4. Dense no longer falls back to the legacy hover overlay;
it draws nothing. The rule is written explicitly rather than inherited from the 32px floor, because that
floor measures the card and not the lane (§1.1).

**One consequence left open for the sibling project.** "Right-click only" has no meaning on touch, so under
the gate at dense an anchored assembly on a tablet has no route to its actions. It matches how the shipped
floor branch already behaves below 32px, and one predicate changes it if that is not intended.

Tests at commit: 255 action specs, 83 unit, 60 TL — all green.

---

## The long pole: the seed mark

**The forward half is now designed, 2026-08-14 — see
[2026-08-14-seed-mark-forward-half-design.md](../2026-08-14-seed-mark-forward-half-design.md).** It covers the
mark, the re-keying of every read path onto it, the per-assembly browser scope and the Modernize action, in six
phases. It leaves the revert sweep, the bookmark path, the deletion of the four old mechanisms and the card
radius out of scope — so it unblocks the six items behind M but does **not** clear the release gate. It also
pulls Modernize forward out of the reverse half, because without it no existing dashboard has any route to
modern and the flip is untestable.

**Superseded by a product decision set, 2026-08-12. Read
[seeded-value-reversibility-decisions.md](./seeded-value-reversibility-decisions.md) before implementing
anything in this section or in widget spec §03.** That file departs from §03 on three points: reversal is a
**persisted wholesale revert** rather than a recompute onto a clone, there is **no automatic forward
re-seed** (modernization is opt-in via a button), and `gate-off` is **not a stored state** — the mark is
`unmarked` / `modern-light` / `modern-dark`. The analysis below is retained because its code citations hold
and because it is the record of how the questions were framed; where it and the decisions file disagree, the
decisions file is current.

A tri-state mark on `VSAssemblyInfo` recording the gate state in force when `setDefaultFormat` ran —
`gate-on`, `gate-off`, `before gate` — so that persisted format seeding becomes reversible.
`VSObjectChromeDefaults` is the one resolver of six that writes at creation rather than at read, so without
the mark a gate-off product cannot un-write the frame colour and card background it seeded while the gate
was on.

**Status: specified to implementation depth, not built.** No such field exists in `VSAssemblyInfo`; the only
shipped seeding state is the corner-specific `PlotDescriptor.modernCornerSeed` plus `isCornerSeedTarget()`
(which is private on `VSAssemblyInfo.java:1232`, not on the chrome resolver, whatever the v3 shell ticket
says). Widget spec §03 remains build-ready and is **byte-identical between v2 and v3**.

**Raised ahead of implementation: the mark is version-blind.** v3's
`Seeded value reversibility - ticket.md` raises it and defers the answer to §03, which did not change.
`resolveSeededCorner` asks *is this value the one I seed?*; the mark asks *was this assembly created under
the gate?* Those coincide only while the set of seeded defaults never changes.

**Milder than the ticket implies, and the fix it proposes may be unnecessary.** §03 line 215 makes reversal
recomputational — "recompute the DEFAULT tier on a clone… instead of stripping values or rewriting saved
content" — so nothing is subtracted and a stale mark produces the right answer anyway. The residual is the
opposite failure: while mark and gate agree no recompute runs, so an assembly created before a default
existed never picks it up, and two same-type assemblies render differently by age. Worth fixing; not the
data-loss the ticket is arguing against. Versioning a mark is a migration you cannot undo cheaply.

**Settle axis-blindness first — it fails today, on two axes.** The gate is three properties
(`viewsheet.modernVisualization` `VSDensityDefaults:40`, `viewsheet.modernObjectChrome`
`VSObjectChromeDefaults:44`, `viewsheet.darkMode` `VSDensityDefaults:48`) and the mark records one.

- **Dark.** All three persisted colour seeds branch on `isDark()` (`:49`, `:54`, `:64`). Create with modern
  and dark on, turn dark off: mark and master gate still agree, nothing recomputes, the dark card
  background stays in a light dashboard forever.
- **Chrome sub-gate.** The seeds guard on the *composed* `VSObjectChromeDefaults.isModern()`; the mark
  records the *master* gate — and §04 line 381 defends that choice explicitly. Turn
  `modernObjectChrome` off with master on and the same stranding occurs. §04's rationale is what makes the
  mark insufficient for §03's own chrome path.

Neither needs a future default or a schema change — both are reachable now by toggling one EM property
twice. The answer determines the mark's field format, the one thing that is expensive to change after the
field exists. **§03 as written is insufficient for three of the four values the gate persists.** Five
options with costs are in §3.2; the choice belongs to the sibling project.

**Both counts in the ticket are wrong, and they are what the decision needs.** It exempts the seeded colours
as "computed live at read time." They are not — `objectBorderColor()` (`VSAssemblyInfo.java:1163→1180,1193`),
`cardBackgroundCss()` (`ChartVSAssemblyInfo.java:89`, `TableDataVSAssemblyInfo.java:1568`) and
`pageBackgroundCss()` (`ViewsheetVSAssemblyInfo.java:238`) are all `set…Value` calls on a default format at
creation. **The gate seeds four values into the persisted DEFAULT tier and exactly one is reversible.** And
it counts three reversal mechanisms where there are four: `modernSmoothSeed` (`PlotDescriptor:632`, field
`:1996`) is a second stored flag of the same shape as `modernCornerSeed`, persisted at `:1531`/`:1718` and
unmentioned. A question about which defaults existed when cannot be answered from a set of one. See §3.2.

**And a hard sequencing rule the ticket understates.** `resolveSeededCorner` does not consult the mark, so
it reverses the radius on unmarked assemblies; the mark cannot, because unmarked means *do not sweep* (§03
line 222). Retiring the check therefore makes every assembly created with the gate on **before** the mark
lands permanently modern. Not a release risk — the seeding has never shipped, `VSObjectChromeDefaults.java`
is absent from `main`, `v1.1.x` and `v1.0.x` — but it means: **do not retire `resolveSeededCorner` until
that population is stamped or discarded.** Discarding is cheap on this branch, which argues for doing it
before the mark rather than after.

**It gates five things and the release.** The four branches above plus the range slider's painter half at
one remove. The spec argues the mark can sequence behind work with users waiting, but is decisive that it
must land **before release**, because the cohort needing migration is empty today and stays empty only until
the branch ships.

**Open questions — all four now answered; kept as the record of what was asked.**

1. ~~**Version-blindness.**~~ Answered: it needs no schema version. A mark records *when* an assembly was
   made, not which defaults existed then, but recomputing under the current rules produces the correct
   value regardless — reversal is not subtractive. Decisions file, "For the sibling project" §3.
2. ~~Is the mark being built, and when?~~ It is a release condition, and the decision set specifies its
   shape: per-assembly, storing the gate tuple. Decisions 1 and 9.
3. ~~Does this track build it?~~ Yes — it is `core/` Java plus the per-assembly CSS scope, in code both
   tracks already touch. Decision 4 is the largest piece and reverses shipped `viz-updates` behaviour.
4. ~~**Does the Modernize bar ship with it?**~~ Yes, and its behaviour is decided rather than inherited from
   §03: manual, per-dashboard, gate-on only, write permission required, one composer undo step, **no bulk
   path**. Decision 5. §03's "applies live with no confirmation dialog" does not survive — decision 6's
   revert is destructive and needs a confirmation and an automatic restore point.

**Two things §03 does not account for, found while deciding.** Bookmarks carry formats —
`TableVSAssembly` the whole `FormatInfo` (`:157-163`), `ChartVSAssembly` the `ChartDescriptor` and a
`VSCompositeFormat` (`:474-490`) — so any reversal that ignores them is undone the next time a user opens an
old bookmark (decision 10). And the dark axis has to be keyed off the mark too, or unchecking dark mode
leaves persisted dark card backgrounds under read-time light chart chrome (decision 9).

---

## Was blocked on a design decision — now answered

**The title lane height row — answered 2026-08-13.** See
[chart-card-anchored-strip-lane-decisions.md](./chart-card-anchored-strip-lane-decisions.md): the widget
spec's values are taken, the strip stays 24px and contained, suppression becomes geometric, and a hidden
title suppresses the strip. The paragraph below records why the first attempt was abandoned; the 13.3
`userDataRowHeight` pattern is the mechanism it was missing. That mechanism now exists — `userTitleHeight`
shipped in `07c91926e`/`307a6ee09` — so the row is no longer gated on the seed mark or on anything else.
What it still needs is the four decisions listed under "Decide before writing the L' plan" above.

Specified at 20/26/30 in widget spec §04 and §08 step 3. Attempted and
abandoned. All five `getTitleHeight()` overrides are the identical line `return titleInfo.getTitleHeight();`
— there is no default branch to redirect — and shared `TitleInfo` seeds its dValue to `AssetUtil.defh` in
both constructors (`TitleInfo.java:53,65`), so there is no unset state to distinguish an author's 20 from
the default. `TitleInfo` is also shared with CheckBox, RadioButton and TimeSlider, which §05 excludes. And
every saved assembly already carries an explicit height (`:260`, `:271`), so even given an unset state the
row would reach newly created assemblies only.

Two shapes, neither free: give `TitleInfo` an unset state plus an overload the five included types call, or
value-sniff `AssetUtil.defh` the way `resolveSeededCorner` sniffs the card radius — cheaper, and the pattern
the set is trying to delete. Specify it before scheduling it.

---

## Ready now

Nothing below is blocked.

| Item | Source | Note |
|---|---|---|
| **Dark — four browser-DOM surfaces** | [Decisions](./chart-card-open-item-decisions.md) §2, plan at `plans/2026-08-11-chart-card-dark-browser-surfaces.md` | CSS only. The v3 dark ticket's dependency claims apply to the server half, which is different work — see §4.2 |
| **Chart interior dark palette** | `Chart card dark values - ticket.md` item 3 | New, unowned, and the most valuable thing in that ticket. `GDefaults` has no dark branch; nothing to reconcile against, so it is design work |
| Affordance sweep | Widget spec §08 step 1 | Live-view only, no export risk |
| Selection list interior | Widget spec §08 step 2 | The one widget the initiative has not touched |
| Resize Plot sliders | Handoff item 13 | `vs-chart.component.scss` untouched since `e8df3491b` (2024-07-15). Delete the 15 `-ms-` lines first — the file halves before anything is retokenized |
| Nav bar | Handoff §02 | Reposition to lower right inset from the plot area, plus `z-index: 9999` and the off-scale `border-radius: 5px` (`chart-nav-bar.component.scss:20,26,32`) |
| Data-tip registry remainder | Handoff 1c | Two of three assignment paths still assign directly; documented in-code at `_directives.scss:20-29` as a known partial state. v3 re-escalated this to "highest-value single change in the whole set" on a misread constant — 9996 is the scrim, not the tip. See §5.6; it is a maintainability item |
| Chart colour literals | Handoff item 10 | 11 occurrences of `#ff8d41`. Declare the gridline CSS token here |
| Chart type scale | Handoff 4a | Gated only by measuring whether 9pt renders as 9px — one build. Its chrome tier is already fully shipped |
| Drill and DC tips · zoom naming · dead menu icons | Handoff step 5 | The "~50 dead icons" count has never been verified |

## Unblocked by `55c3bad1a`

This section read "after the density gating commits" until 2026-08-13. It has.

**Rollout slices 4 and 5** — the container and the calendar. `ANCHORED_ASSEMBLY_TYPES`
(`mini-toolbar.service.ts:41-53`, re-checked at `1c0ace705`) carries six types; the container is
deliberately excluded as its own slice, and the calendar is expected to take the table treatment
unmodified.

Read `github.md`'s new in-project decision to scope the resting kebab by pointer capability first — it
modifies already-shipped slice-3 behaviour.

**Not this — L'' waits for L'.** The geometric suppression that replaces the density test is decided but
must not land until the lane row does, or the strip vanishes from every assembly carrying the default
title height. See the sequencing rule under the dependency picture. When it does land it also retires
`rightEdgeReserve()` and `SORT_CONTROL_RESERVE` (`vs-object-container.component.ts:588-603`), because a
hidden title suppresses the strip rather than overlaying it — a reversal of the slice-2 and slice-3
title-hidden decisions, costed in
[the strip and lane decisions](./chart-card-anchored-strip-lane-decisions.md) decision 4.

## Decided, unscheduled

**The title band becomes unfilled** — [decisions](./chart-card-open-item-decisions.md) §1. No dependency on
the seed mark. Server-rendered and therefore export-affecting, so budget the manual pass, and show it to the
sibling project first: it breaks the title-bar/table-header equality their §05 endorses.

**The card radius drops 12px → 6px** — `Chart Card Spec v3.dc.html` §01. Sequenced behind the mark, because
retiring `resolveSeededCorner` without it leaves no reversal path. Confirm the seeded 12px cohort is empty
before either constant moves — `resolveSeededCorner` keys on exact equality, so already-seeded assets stop
being stripped the moment the constant changes.

---

## Done

Hashes re-resolved 2026-08-13 after the rebase; each is an ancestor of `HEAD`. The pre-rebase hash is
kept alongside because the rest of this tree still cites it.

| Item | Commit | Cited before the rebase |
|---|---|---|
| Phase 9B dark mode — every server-rendered surface, chart included | `8f138595a` | `3e7e52626` |
| Inline-SVG chart rendering coupled to the modern gate | `6e803a702` | `aed8e6b22` |
| Data-mark-anchored tooltip tail | `c4cfa342b` | `7e4a7c809` |
| Shell tooltip retokenize + CARD ramp + data-tip layer declaration | `8b6b006ae` | `43a934add` |
| Selection vocabulary — chart-owned surfaces and the annotation border | `d627ec403` | `052fe61f1` |
| Menu-action reachability, the §06 ladder, and rollout slice 1 — chart | `6da9c024f` | `67c486d67` |
| Right-click reaches max mode on tables | `0db62036f` | `b1eb8df8e` |
| Rollout slice 2 — table, crosstab, calc table | `931bdb02b` | `a4cd1e362` |
| Max-mode mini-toolbar positioning fix | `f3a299cd0` | `1091bd178` |
| Rollout slice 3 — selection list and tree, kebab-only at any width | `5ed02f6ed` | `a038a30b5` |
| Strip suppression where the lane cannot hold it — L, density-approximated | `55c3bad1a` | postdates the rebase |
| `userTitleHeight` — N, the flag and its per-type default | `07c91926e` | postdates the rebase |
| `userTitleHeight` — N, the thirteen stamps, the propagate and the reset un-stamp | `307a6ee09` | postdates the rebase |

The v3 design set and the seeded-value decisions were committed in `2310cea37`, the strip and lane
decisions in `1c0ace705`, the `userTitleHeight` plan and strip-and-lane decision 5 in `8357e05d8`, and the
three earlier docs commits — `2b08a0492`, `a479ba921`, `ae511b8b7` — carry the design sets, the open-item
decisions and this roadmap.

## Still undecided

- **The four L' decisions** — which types the row covers and what the calendar's 36px default does,
  whether the use-the-default affordance ships with the row, how the flag reaches the live assembly, and
  the affordance's shape. Listed in full under "Decide before writing the L' plan" above. These are the
  live ones: nothing else blocks L'.
- ~~**Dense: hover overlay, or no chrome at all?**~~ Answered 2026-08-12 and now shipped in `55c3bad1a`:
  no chrome at all. [Decisions](./chart-card-open-item-decisions.md) §4. What remains open from it is
  dense-plus-touch, below.
- **Whether dense-plus-touch is meant to have no affordance**, and now title-hidden-plus-touch with it —
  [strip and lane decisions](./chart-card-anchored-strip-lane-decisions.md) decision 4 widens the same
  question to a larger population.
- **How the seed mark handles defaults added after it ships.** See the seed-mark section.
- ~~**How the title height row reaches assemblies.**~~ Answered 2026-08-13 and the mechanism now shipped:
  the widget spec's 20/26/30, applied in the per-type read path the way `rowHeight()` is, conditioned on
  `userTitleHeight` (`07c91926e`/`307a6ee09`).
  [Strip and lane decisions](./chart-card-anchored-strip-lane-decisions.md) decisions 1 and 3. What is left
  open there is the clearance value at compact and the 44px touch target against a 26px lane.
- **Does the nav bar render for maps only, or any zoomable chart?**
- **Which render path gauges and thermometers take in the live viewer** — the widget spec flags this itself
  as unverified.
- **Whether the sibling project accepts the unfilled title band**, given they endorsed the fill for
  cross-widget consistency.
- **Whether the teal selection family has an owner.** It is unchanged in `_viz-tokens.scss:51-53`, and v3
  deleted the paragraphs that tracked it without resolving it.

---

## Related documents

- [chart-card-source-doc-corrections.md](./chart-card-source-doc-corrections.md) — the running audit of the
  external set against the branch. **Read it before trusting `chart-card-design3/`, and before requesting
  the next sync**
- [chart-card-open-item-decisions.md](./chart-card-open-item-decisions.md) — the title band, dark scope and
  range slider decisions, with the consequences each triggers
- [chart-card-anchored-strip-lane-decisions.md](./chart-card-anchored-strip-lane-decisions.md) — which v3
  document governs the title lane, the strip's size and containment, and what suppresses it. **Overrides
  `Chart Card Spec v3.dc.html` §04's lane model and §03's title-hidden overlay**
- [2026-08-14-seed-mark-forward-half-design.md](../2026-08-14-seed-mark-forward-half-design.md) — **how M is
  built**: the mark's field and stamp sites, `VizContext` and the ~90 re-keyed call sites, the per-assembly
  browser scope, Modernize and its composer bar, in six phases. Authority on mechanism; the decisions file
  stays the authority on behaviour
- [seeded-value-reversibility-decisions.md](./seeded-value-reversibility-decisions.md) — the seed mark,
  the four seeded values and the mechanism inventory. **Supersedes the roadmap's seed-mark analysis
  below**, which still lists version-blindness as the open question
- [chart-card-slice1-design.md](./chart-card-slice1-design.md) ·
  [chart-card-slice2-tables-design.md](./chart-card-slice2-tables-design.md) ·
  [chart-card-slice3-selection-design.md](./chart-card-slice3-selection-design.md) — how each shipped slice
  works
- `plans/2026-08-11-chart-card-strip-density-gating.md` · `plans/2026-08-11-chart-card-dark-browser-surfaces.md`
- `chart-card-design3/` — the external source set. Regenerated wholesale on each sync, so nothing authored
  there survives. `chart-card-design2/` and `chart-card-design/` are kept as history
- [visualization-implementation-roadmap.md](./visualization-implementation-roadmap.md) — the wider
  initiative, decomposed by phase rather than by slice
