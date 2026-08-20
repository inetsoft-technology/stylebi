# Chart Card — Roadmap

**Date:** 2026-08-20 (eleventh revision — **P6 has been reviewed against the code, ahead of planning it.**
Six findings, four decided the same day. The one that changes the shape of the phase: `resolveSeededCorner`
is a **fourth** deletion inside P6's same-commit set, because the design's claim that the two
`PlotDescriptor` seed booleans are "the only reads left that still test the org gate" is false — leave the
method in and a marked assembly in a gate-off org loses its card radius while keeping every other modern
value. Two more gate reads survive P6 as documented accepted costs, and three items the spec did not list
are folded in, one of them a P5 density regression on org-level surfaces. Everything is recorded in
"Carried into P6" below, in [the design](../2026-08-14-seed-mark-forward-half-design.md) §5's P6 and in
[decision 13](./seeded-value-reversibility-decisions.md). The tenth revision's note follows: **P5 has
shipped as `4c237a7dd`**, 61 files, with all three automated
gates green and **every manual check passed, including P3's eleven**, which had never been run before. The
section below, "What P5 left behind", carries what it deferred; two of the six items it originally listed
were withdrawn after testing. The eighth revision's notes follow: **P4 committed as `8ef511e45`**, and a product decision taken
that day, **decision 13, overrules the org-wide revert sweep**: disabling the gate now reverts nothing, and a
per-dashboard Revert action mirrors Modernize. That collapses most of the release gate into one phase, P6.
The seventh revision's P4 note, the sixth's P3 note, the fifth's P2 note and the fourth's rebase note are all
retained below)
**Verified against:** community `viz-updates` @ `8ef511e45`, which is `HEAD` and carries P4. The seventh
revision verified against `cd06da9b1` plus P4's then-uncommitted working tree; that has since committed
unchanged. A commit-approval gate denied every one of P3's nine tasks its own commit while P3 was in progress, so
this file's sixth revision verified against `a38cb6957` with P3 only on disk — but `cd06da9b1` (**"feat(viewsheet):
seed modern chrome from one hook, and modernize on request"**) has since landed on top of it, carrying P3 in
full, committed outside the session that built it. P4's own tasks hit the same gate and remain uncommitted at
this revision, so P4's code citations below were checked directly against files on disk rather than against a
commit. `8f75872a6` shipped the seed mark's P1 and `119bfdaac` its P2;
`be0e3c664` carries the P1 null-guard that P2's suite run exposed. `380705bc1` shipped the density gating;
`1d26dbefb`/`d4d0d5d48` the userTitleHeight flag. Every code claim below was re-checked, against `HEAD` where
a claim is about committed code and against the working tree where it is about P3.
**Covers:** the chart card track — the anchored toolbar rollout, the shell and chart surfaces found through
it, and the decisions that gate what remains

**Before syncing the external design set, read
[chart-card-source-doc-corrections.md](./chart-card-source-doc-corrections.md).** It records where that set
has been wrong about this code, and corrections applied to the set itself have a lifetime of exactly one
sync — the 2026-08-12 regeneration overwrote four of them. This pointer lives here, outside
`chart-card-design3/`, because a pointer placed inside the set is deleted with the set.

**Verify before trusting.** This branch moves daily. Every claim below cites a commit or a file so it can be
checked rather than believed. If a claim and the branch disagree, the branch is right.

**Hashes go stale when the branch is rewritten, and it has happened twice.** The 2026-08-14 rebase moved
all 62 commits onto `e7ef501fb` and rewrote every hash this file cited at the third revision — checked,
and **not one of `8357e05d8`, `952614aa7`, `1c0ace705`, `55c3bad1a`, `07c91926e` or `307a6ee09` is an
ancestor of `HEAD` any more.** They still resolve with `git show`, because the old objects linger in the
repo while being unreachable, so they would fail in a fresh clone and after a `gc`. The Done table below
carries the post-rebase hashes. When checking any hash in this tree, use
`git merge-base --is-ancestor <hash> HEAD` rather than `git show`; `git show` succeeding proves nothing.
Every other file under `docs/superpowers/` still carries pre-rebase hashes and should be read with that in
mind — the design docs' *file and line* citations were re-derived and are current, but their commit hashes
were not.

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
     SHIPPED 380705bc1          NO LONGER BLOCKED · container, calendar
     the interim · L'' replaces it

  N. userTitleHeight flag ──┐
     SHIPPED 1d26dbefb      │
     d4d0d5d48              ├──→ L'. Title lane height row ──→ L''. Geometric suppression
                            │    DESIGN ANSWERED, STARTABLE       DECIDED 2026-08-13
  M-P4. Read paths ─────────┘    20/26/30 · titleHeight() exists  MUST NOT PRECEDE L'
        BUILT, uncommitted       resolver reads the mark now      replaces L's density test

     N answers "did the author choose this height" · the MARK answers "is this assembly modern"
     The row needs both. On the flag alone it resizes every dashboard ever saved.
     P1 and P2 landing did NOT free it: the row must READ the mark, and P4 is what makes that read exist.
     L' is unblocked now, not "when P5 lands" — its read path is a server read, P4's subject, not P5's.

  M. Seed mark — SIX phases now. P1–P4 SHIPPED; P5 and P6 remain.
     design: ../2026-08-14-seed-mark-forward-half-design.md · decisions 1–13
     ALSO THE RELEASE GATE — and the gate SHRANK on 2026-08-19: decision 13 overruled the
     sweep (decisions 6+7), so release needs P6 + bookmarks, not an async org-wide sweep
     with a restore point, scheduler blocking and composer-session blocking

     P1  the field, persisted, stamped at creation      SHIPPED 8f75872a6 — nothing reads it
      │
     P2  VizContext threaded, 71 files, ofGate()        SHIPPED 119bfdaac
      │  + all four sub-gate properties deleted         verified behaviour-neutral, suite green
     P3  decision 11's enumeration point + Modernize    SHIPPED cd06da9b1
      │  the only route in for old content              eleven manual checks still outstanding
     P4  server reads follow the mark                   SHIPPED 8ef511e45 · THE BEHAVIOUR REVERSAL
      │  43 read sites + the creation site              unblocks L' now · nine manual checks PASSED
     P5  browser reads follow the mark                  SHIPPED 4c237a7dd · 61 files
      │  resolved modern/dark on the model · 15         4905 core / 1316+356 portal / full
      │  bindings across 7 templates · body class       cross-module build · ALL manual
      │  renamed viz-shell · isVizModern() deleted      checks passed, incl. P3's eleven
      │  ✗ getTooltipStyle NOT done — deferred, R20     P4's interim state is closed
      ├──┬──→ §07 derived selection, retire the teal family
      │  │         └──→ Range slider — painter half
      │  └──→ Outlined text conversion (also behind G)
      │
     P6  Revert — the per-dashboard mirror of         NEW 2026-08-19 · decision 13
         Modernize; deletes the gate && term,         replaces the sweep entirely
         both PlotDescriptor seed booleans AND        ~P3's weight, not the sweep's
         resolveSeededCorner, all in one commit       REVIEWED 2026-08-20 · 5 pieces
      └──→ Card radius 12→6 (the constant only)

     P4 unblocks the first of the six — L' — directly, ahead of P5. P5 gates four of the other five. P1 and
     P2 unblocked none of the six, by design; P3 unblocked P4's testability rather than any of the six; P4
     is the first phase whose landing moves one.

     The card radius moved off P5 and onto P6 on 2026-08-19. It never needed the sweep specifically — it
     needed A reversal path for resolveSeededCorner's retirement to fall back on, and Revert is one.

     Split again on 2026-08-20 by the P6 review, and this is the sharper version: retiring
     resolveSeededCorner is REQUIRED BY P6, not unblocked by it. Its gate read strands a marked
     assembly's card radius the moment the gate && term goes — the same stranding the design already
     calls non-negotiable for the PlotDescriptor booleans, on a more visible property. So it is inside
     P6's same-commit set. Only the 12→6 CONSTANT is a follow-on, and it needs its own sign-off.

  G. Chart type scale ──→ H. Outlined text conversion
     needs one measurement

  Ungated: chart interior dark palette · affordance sweep · selection list interior ·
           Resize Plot sliders · nav bar · data-tip registry remainder · chart colour
           literals · drill and DC tips · zoom naming · dead menu icons · title band

  Cheaper AFTER P5, not ungated: dark (four DOM surfaces) — decision 4 turns viz-dark
           from a body class into a per-assembly scope, so doing it first means doing it twice
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
  assemblies, and `titleHeight()` still has no per-assembly condition. The field to condition on now exists —
  P1 shipped it — but nothing reads it yet, so the row waits on P4 rather than on the field. The 2026-08-11
  edition of this roadmap said the row "needs no server change"; that was wrong twice over. See §3.3.
- **The 32px chrome floor does not reach dense.** It is a card-height rule, not a lane rule, so the v3
  reversal in §05 does not follow from it. See §1.1. This was the live decision until 2026-08-12; the
  outcome was adopted and the mechanism written explicitly rather than inherited, and it shipped in
  `380705bc1`.

---

## What to pick up next

**Re-derived 2026-08-19, from the dependency picture above rather than by editing the 2026-08-18 ranking in
place — this file's own instructions say to re-derive rather than repair. Two things changed: P4 committed,
and decision 13 turned the release gate's largest unbuilt item into a phase small enough to rank.** This is a
reading of the picture, not a new decision; it goes stale as things land. Effort is relative to this track,
not absolute.

**Re-derived 2026-08-20, after P5 shipped and its manual checks passed** — from the picture above rather
than by editing the previous ranking, per this file's own instruction. The previous revision put P5's manual
checks at the top because nothing in the track could be trusted until they ran. They have, and they passed,
so the item is gone rather than demoted.

| # | Item | Impact | Effort | Unblocks | Risk |
|---|---|---|---|---|---|
| 1 | **L′ — the title lane height row** | the highest-value visible item, startable since P4 | **M–L** (decision 2, four L' design questions) | L″ next | none new — it is the item the mark and the flag exist to free |
| 2 | **M-P6 — Revert** | clears most of what is left of the release gate | **M** — P3's wiring mirrored, plus four deletions and three folded-in items; reviewed up from S–M on 2026-08-20 | card radius 12→6 (the constant only) | deletes the `gate &&` term, both `PlotDescriptor` seed booleans **and `resolveSeededCorner`** together; splitting any of them strands charts or card corners. **Also drops the `if(modern)` guard on the density body class**, fixes a P5 density regression on the same selectors, and unhides the EM density control — see "Carried into P6" |
| 3 | The ungated cheap items | low each, additive | **S** each | nothing | none |

**The binding-pane dead buttons are off this table because they are built** — both predicates landed
2026-08-20. **They are built but not yet verified in a running app** — the manual pass on 2026-08-20 ran
against a bundle built before the change (`target/classes` chunk at 11:07, source edited at 14:16) and so
tested pre-fix code. Rebuild the web module before re-running it. See "What P5 left behind" item 1. The
overlap defect recorded beside them was **not** folded in and is not XS; it is recorded there too.

**P6's ordering constraint is satisfied — P5 is built, so P6 is unblocked.** The reasoning is retained
because it explains why the order mattered: P6 makes gate-off mean
"marked content stays modern" on the server, while the browser's `viz-modern` body class is still toggled
from the org gate until P5 lands — so P6-before-P5 would put legacy CSS over modern server chrome for every
gate-off org. Nothing else couples them: P6 is server plus composer wiring and touches none of P5's files.

**M-P3 and M-P4 are off this table because they are built.** P3 shipped in `cd06da9b1` and P4 in
`8ef511e45`, both committed outside the sessions that built them. **P4's nine manual checks all passed on
2026-08-19**, per its commit message: an unmarked dashboard rendering legacy chrome under an open gate, a
Modernized copy rendering modern, export agreeing with view across PDF, PNG and Excel for both, a property
dialog on a legacy chart previewing legacy chrome, and a mixed dashboard rendering each assembly by its own
mark. **P3's eleven have not**, and nothing in P4's pass substitutes for them. They need a built server, a
browser and a legacy dashboard. Running them is a real prerequisite for anyone continuing this track, even
though it is not a ranked item in the sense the table means: there is no design work or code left to write.

**P5 also inherits one server-side reader P4 deferred.** `AbstractChartInfo.getTooltipStyle` resolves AUTO to
CARD from the org gate; threading it changes the `ChartInfo` interface getter and ripples through four
classes, and its most visible consumer ships `tooltipStyle` to a browser that still reads org-gated flags
until P5. Fixing the server half alone would have bought nothing visible. Recorded in P4's commit message and
in the design's P4 section.

**L′ is first because P4 is what was missing, and P4 is now built.** The row needed a read path that
consults the mark at the title-height resolver, and nothing supplied one until this phase — see "the seed
mark" section below for the 43 sites and the creation-site flip. `userTitleHeight` has been sitting shipped
since `1d26dbefb`/`d4d0d5d48` with nothing to pair it with; that pairing exists now. P5, by contrast, still
only unblocks other things rather than shipping one of the six itself.

**The seed mark is no longer one XL item, and four of its five phases are now behind us.** P1 shipped the
field; P2 shipped the carrier; P3 built the only route existing content has to modern chrome; P4 flipped the
reads. The first three deliberately unblocked **nothing** for the six items downstream — P3 unblocked P4's
testability instead — but P4 is different: it is the phase that turns the mark from a fact nothing consults
into one L′ can read, so it unblocks the first of the six directly. What used to be "M, XL, six items" is one
remaining phase, P5, and it frees the other five.

**P3's design was re-checked against the code on 2026-08-18 and carries five corrections, and P3 has since
shipped to exactly that shape.** They are in the forward-half design's §4, and none of them changes the shape:
one virtual method that creation and Modernize share. What they change is that the method takes no
`VSCompositeFormat` (four of the values it must carry live on `PlotDescriptor`, not on any format), that it
carries only the gate-dependent values (the overrides mix those with unconditional defaults, and re-running
those would reset an author's padding, table style and fonts), that its per-path contract has to name
TITLEPATH and DETAIL as well as OBJECTPATH (the base and two subclasses install *fresh* composites at
seventeen other paths, dropping the author's USER tier at each), that one of the three seeding overrides sits
on a different overload, and that `applyModernDefaults` is not available as a name. The two decisions it
raised were settled the same day and are recorded in that document's items 6 and 7: the hook is
`seedChromeDefaults(VizContext)`, and it does write the title border colour at TITLEPATH, by mutating the
composite already installed there.

**Building P3 found three more bypass cases the corrections above had not, all closed before the phase was
called done.** Seven types — `CheckBoxVSAssemblyInfo`, `ComboBoxVSAssemblyInfo`, `RadioButtonVSAssemblyInfo`,
`SpinnerVSAssemblyInfo`, `SubmitVSAssemblyInfo`, `TextInputVSAssemblyInfo` and `TextVSAssemblyInfo` — override
`setDefaultFormat` without calling `super` and hardcode the legacy border colour directly, so the hook never
ran for them at creation and must not run for them under Modernize either; `CalendarVSAssemblyInfo` reaches
the same place by never calling `setDefaultFormat` at all. `ChartVSAssemblyInfo` and
`SelectionBaseVSAssemblyInfo` replace the whole title composite after `super` returns, discarding whatever
border colour the base wrote, so the hook must not write one they will only throw away. Both findings became
private predicates on `VSAssemblyInfo` (`bypassesBaseChrome()`, `installsOwnTitleFormat()`), consulted by the
hook itself for the same reason `isCornerSeedTarget()` already lives there rather than in a caller. Full
citations are in the forward-half design's §4.

**P3 built 2026-08-18, and shipped in `cd06da9b1` — see "the seed mark" section below for the
detail.** `seedChromeDefaults(VizContext)` on `VSAssemblyInfo`, three overrides, `VizModernizeUtil`'s
enumeration and stamp-then-seed loop, the composer endpoint and its `@ClusterProxy` service, the
`modernizable` flag on `SetViewsheetInfoCommand`, and the dismissable bar with its permanent menu entry. The
one thing this phase's own verification claim could not check without a live composer is left explicitly
outstanding: the eleven manual checks the plan calls for, plus a twelfth added while building, covering
what an empty undo checkpoint does to Ctrl+Z. None of them has run.

**P2 shipped: `VizContext` is threaded and all four sub-gates are gone.** Every `VS*Defaults` value method
now takes a context; every call site passes `VizContext.ofGate()`, which reads exactly what the statics read
before, so nothing renders differently. `viewsheet.modernObjectChrome`, `modernChartChrome`,
`modernChartPalette` and `modernTableStructure` are read nowhere in `core/src` — main, test and comments
alike — and exactly one `public static boolean isModern()` survives, `VSDensityDefaults`', as the master-gate
reader. Sizing note for anyone budgeting a comparable sweep: the design guessed ~90 call sites, this file
previously recorded an audited 115, and the landed change is **71 files** (56 production, 15 test) — the
count that matters is files touched per class, not raw call sites, because a single method often holds
several.

**P2 answered both of the design's open plumbing questions, and P4 has since settled both the way it
described as most likely.**

- **The three composer dialog models had no route to an assembly** — `ChartLinePaneModel`,
  `AxisPropertyDialogModel`, `LegendFormatDialogModel` see only `ChartInfo`, `PlotDescriptor`,
  `AxisDescriptor` or `ChartArea`. `ChartPropertyDialogService` and `RegionPropertyDialogService` each held a
  `ChartVSAssemblyInfo` one or two call-hops up without forwarding it. **P4 threaded it down** rather than
  falling back to `ofGate()`: both services now resolve the dialog's own chart by object id and pass its
  context through four widened `ChartRegionHandler` methods, so a property dialog opened on an unmarked
  chart previews legacy chrome and agrees with the canvas behind it.
- **`CSSProcessor.applyCSS` has no route structurally, not merely today.** Every hop back from it stays inside
  the legacy report / `ReportSheet` / `ChartElementDef` model, which never carries a `VSAssembly` — and
  `applyCSS(ReportSheet)` has no callers anywhere in `core/src` or the enterprise modules. **P4 confirmed there
  is no route to thread and passed `VizContext.LEGACY`** at `CSSProcessor.java:474` and at its own `ofGate()`
  site, exactly as this paragraph predicted.
- **The dialog models were not the whole of the threading problem** — added 2026-08-18, closed by P4. Five
  chart-pipeline sites see a `ChartInfo` or a descriptor and never a `VSAssemblyInfo`: `GraphGenerator`,
  `CSSChartStyles.apply` (called from `VGraphPair`, `CSSProcessor` and `VSChartDndService`) and
  `ChangeChartProcessor`. **P4 gave `GraphGenerator` a `VizContext` field set at construction** — `of(chart)`
  on the constructor taking a `ChartVSAssemblyInfo`, `LEGACY` on the one taking a bare `ChartInfo` — which
  also gives the LEGACY-identity axis a real home instead of a convention two tests defend.
  `CSSChartStyles.apply` takes the context as a parameter, `of(info)` from its viewsheet callers and `LEGACY`
  from `CSSProcessor`. One site had no assembly at all — `ChartColorPaletteController.getChartColorPalette()`,
  a bootstrap fetch with no parameters — and **P4 decided it stays org-gated**, with a comment on the one
  surviving `ofGate()` call explaining why: a global swatch list is not per-assembly chrome, and a
  per-assembly endpoint would put a client change and an uncacheable fetch inside a server-only phase.

**One thing P2 introduced that P4 had to carry rather than resolve.** `VizContext` still carries an implicit
fourth axis — *is this a viewsheet chart at all* — encoded as **identity** against `VizContext.LEGACY`. Seven
chart descriptors' font lines read `ctx != VizContext.LEGACY`, which is what the old bare `vs` boolean meant,
and identity is the only faithful encoding: with the gate off, `ofGate()` returns a context *value-equal* to
`LEGACY` that must still count as a viewsheet chart. No factory may return the `LEGACY` instance, and P4's
own `GraphGenerator` field is built on that identity rather than replacing it with an explicit `viewsheet`
field — the convention became a fact rather than being retired. Related: the design says the context is
each method's **first** parameter; landed signatures across P2 and P4 alike take it **trailing**, which is
the better shape and what the plans' own snippets did.

**L' is still the highest-value visible item, and P4 is what frees it.** `VSDensityDefaults.titleHeight()`
resolves defh/26/30 and is still uncalled by the resolver itself, and `userTitleHeight` has told an author's
height from a default one since `1d26dbefb`/`d4d0d5d48`. That is one half of what the row needs. The other
half was *reading* the mark at the title-height resolver — not there yet at P1, not there yet at P3, and
supplied now that P4's 43 sites and its creation-site flip exist. A mark that exists but is never consulted
moves nothing; P4 is what makes it consulted.

**The other half is the seed mark, and it is the correction this ranking previously got wrong.** An
earlier revision called the flag a cheaper alternative to the mark. They answer different questions:
the flag says *did an author choose this height*, so the row does not overwrite a deliberate choice; the
mark says *is this assembly modern*, so the row does not reach dashboards nobody opted in.
[Seeded-value decisions](./seeded-value-reversibility-decisions.md) decision 4 keys the density heights
off the mark, and decision 2 protects unmarked content from every automatic behaviour. **Shipping the row
on the flag alone would resize fifteen years of saved dashboards on next open.**

**Decided 2026-08-13: L' waits for M** ([strip and lane decisions](./chart-card-anchored-strip-lane-decisions.md)
decision 8) **— and the wait is over now that M-P4 is built.** Shipping it dormant would have looked like
free parallelism and would not have been — nothing would have been marked, so the manual export pass could
not have run, the checkbox would have shipped doing nothing observable, and the only question a dormant
build answers early is the cheapest part of the work. That cost no longer needs accepting: the mark exists,
P4 reads it, and Modernize gives existing content a route onto it, so L' can be picked up as a live resolver
change rather than a dormant one. The anchored strip's density approximation (`380705bc1`) still stands in
for L'' until the row itself lands.

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
(`viewer-app.component.ts:2798`) to a per-assembly scope — ~~and decisions 6 and 7 add a resumable org-wide
async sweep with a restore point, composer-session blocking and scheduler interaction.~~ It wants its own
plan and its own branch. **If a release date is set, M's schedule is the thing to work backwards from**,
because after release the cohort is customer data and the write-off is no longer available.

**Amended 2026-08-19, and the second half of that sentence is gone.** Decision 13 overruled decisions 6 and
7: there is no org-wide sweep, so no restore point, no composer-session blocking and no scheduler
interaction. What replaced them is P6, a per-dashboard Revert action of roughly P3's weight. The rest of the
paragraph stands — decision 4 is still the largest single piece of work in the set, and the write-off still
expires at release.

**The interaction to know before taking #5.** The dark browser-surfaces plan declares its four tokens in
the existing `.viz-dark` block at `_viz-tokens.scss:143-159`. Seeded-value decision 4 turns `viz-dark`
from a body class into a per-assembly scope. The rework is mechanical rather than structural, but if M is
imminent the dark work is cheaper after it than before it.

**Two that look ready and are not.** The unfilled title band is M-independent and decided, but it breaks
the title-bar/table-header equality the sibling project endorses — its blocker is sign-off, not code, so
raise it now and it clears by the time it is wanted. And the range slider's browser half must not ship
without its painter half ([decisions](./chart-card-open-item-decisions.md) §3), which sits behind M.

---

## The density gating — shipped in `380705bc1`

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
the bound; the direct comparison is L'' and still waits on L'. Do not read `380705bc1` as having
implemented [strip and lane decisions](./chart-card-anchored-strip-lane-decisions.md) decision 3.

**The decision the v3 sync reopened is settled: v3 is accepted** —
[decisions](./chart-card-open-item-decisions.md) §4. Dense no longer falls back to the legacy hover overlay;
it draws nothing. The rule is written explicitly rather than inherited from the 32px floor, because that
floor measures the card and not the lane (§1.1).

**One consequence left open for the sibling project.** "Right-click only" has no meaning on touch, so under
the gate at dense an anchored assembly on a tablet has no route to its actions. It matches how the shipped
floor branch already behaves below 32px, and one predicate changes it if that is not intended.

Tests at commit: 255 action specs, 83 unit, 60 TL — all green.

**One defect found in it on 2026-08-20 and fixed the same day: dense drew an empty pill above the card.**
Reported from a running viewer during the binding-pane manual pass, with a screenshot — a thin bordered
sliver where dense is supposed to draw nothing at all.

`MiniToolbarComponent.showToolbarContainer` conditioned its emptiness test on being resident:

```ts
if(!this.kebabResident) {
   return !this.mobileDevice;      // renders regardless of content
}
return (this.actionButtonGroups && this.actionButtonGroups.length > 0) || !!this.kebabAction;
```

Dense reaches that first branch. `isAnchoredResident()` is false at dense, so the host passes
`anchorInTitleLane` false and `kebabResident` is false — while `isAnchoredChromeSuppressed()` has already
emptied `showingActions`. Container renders, nothing inside it, and the assembly-hover reveal takes it to
full opacity. The guard was written for the 32px floor, which only ever occurs *while* anchored, so the
dense rung was never in its scope. Its own doc comment recorded the gap as intended behaviour ("the
container renders whenever the device isn't mobile, regardless of content"), and a unit test asserted it —
both have been corrected.

The fix makes residency decide only whether mobile suppresses the container, never whether its content is
checked. **Both rungs that empty the action list now hide the container, which is what "no chrome at all"
was supposed to mean in the first place.**

---

## The long pole: the seed mark

**P1 through P4 have shipped — P3 in `cd06da9b1`, P4 in `8ef511e45` — and the design document
[2026-08-14-seed-mark-forward-half-design.md](../2026-08-14-seed-mark-forward-half-design.md) now covers a
sixth phase.** It covers the mark, the re-keying of every read path onto it, the per-assembly browser scope,
the Modernize action and, since 2026-08-19, Revert. With P4 in it unblocks the first of the six items behind
M (L′). It pulled Modernize forward out of what used to be the reverse half, because without it no existing
dashboard has any route to modern and the flip is untestable — and on 2026-08-19 it absorbed the rest of that
reverse half too, because decision 13 shrank it to one phase.

~~It leaves the revert sweep, the bookmark path, the deletion of the four old mechanisms and the card radius
out of scope, and does **not** clear the release gate.~~ **Amended 2026-08-19.** The sweep is overruled; the
design now carries P6 in its place. What still sits outside it: the bookmark path (decision 10) and two of
decision 12's four mechanisms. The card radius moved inside, behind P6.

**P1 shipped in `8f75872a6`.** `VizMark` (`modern-light`/`modern-dark`, absent meaning unmarked) on
`VSAssemblyInfo`, persisted as one attribute and **cleared unconditionally on parse when absent**; a sheet
stamps itself from the gate in `ViewsheetVSAssemblyInfo`'s constructor; an assembly inherits its host's mark
in `AbstractVSAssembly`'s two-arg constructor; `copyViewInfo` carries it; and a type conversion keeps the
converted object's own mark. 22 tests across six classes. **Nothing reads it** — that is the phase's central
constraint, not an omission, and it is why P1 unblocked none of the six items.

Three things P1 established that change how the rest should be read:

- **Assembly creation has no single funnel.** The design assumed `VSEventUtil.createVSAssembly` was one; it
  is not, and roughly 74 sites construct assemblies directly. That cost an extra task and moved the stamp
  from a service method to a constructor. Any later phase that assumes a chokepoint should check first.
- **`initDefaultFormat()` is not the hook it looks like.** Every one of those sites calls it, but it also
  *resets* an existing assembly's format, so stamping there would destroy a pasted assembly's provenance.
- **The human partner decided type conversion keeps the converted object's own mark**, extending decision 3,
  whose table had been silent on it. Recorded in the decisions file.

**P2 shipped in `119bfdaac`.** `VizContext` — immutable, `modern`/`dark`/`density`, factories `ofGate()`,
`of(VSAssemblyInfo)`, `of(VizMark)` and the `LEGACY` constant — is threaded through all eight in-scope
`VS*Defaults` classes and the nine chart descriptors' `initDefaultFormat`. The six per-class `isModern()`
predicates and all four sub-gate properties are deleted. Every call site passes `ofGate()`, so **nothing
renders differently**; the manual gate-on/gate-off and PDF/Excel export pass confirmed it. 70 files, and the
full `core` suite is green at 4846 tests.

Four things P2 established that the later phases should read before starting:

- **`of(VizMark)` keeps the gate term.** A marked assembly reads modern only while the org gate is also on —
  `modern = VSDensityDefaults.isModern() && mark != null`, with `dark` gated behind `modern` so the
  never-dark-without-modern invariant holds structurally. The P2 plan had dropped that term and a test had
  pinned the omission; the design's §2 "interim term" paragraph is authoritative and was restored. Deleting it
  is still the one-line change ~~the revert sweep~~ **P6** makes — reassigned 2026-08-19 by decision 13,
  which overruled the sweep. Still one line, still the same line.
- **`VizContext.LEGACY` is a sentinel whose identity is load-bearing.** See the note in "What to pick up next"
  — seven descriptor font lines compare against it to mean *is a viewsheet chart*, and no factory may return
  it.
- **Two methods still read the gate directly, on purpose and commented.**
  `VSObjectChromeDefaults.resolveSeededCorner` is called from a `VSCompositeFormat` getter with no route to a
  context, and `PlotDescriptor`'s two seed-boolean getters are the interim reversal mechanisms ~~the sweep~~
  **P6** deletes — and, since decision 13, deletes in the *same commit* as the `gate &&` term, because after
  P4 those two getters are the only reads left that still test the org gate. A third, `AbstractChartInfo`'s tooltip-style resolution, is now commented the same way — so the
  claim "no resolver reads `SreeEnv` for the gate" is true of the eight `VS*Defaults` classes, not of every
  read path.
- **`ofGate()` is called per-row.** `SelectionBaseVSAssemblyInfo.getEffectiveCellHeight()` runs once per
  displayed row from two `getRowHeights` loops and once per selection value. P2 removed the redundant
  double gate read this exposed; anything P4 adds to the factories pays that multiplier.

**P3 built 2026-08-18, and shipped in `cd06da9b1`** — "feat(viewsheet): seed modern chrome from one hook,
and modernize on request." A commit-approval gate denied every one of its nine tasks' own commits while it
was in progress, so the code sat on disk only until a human partner committed it directly, outside the
session that built it. It extracted
`seedChromeDefaults(VizContext)` from `VSAssemblyInfo.setDefaultFormat` and the three subclass overrides that
used to compute the gate-dependent seeds inline, built `VizModernizeUtil` to stamp and re-seed a dashboard's
unmarked content, and wired a composer-only Modernize action behind a dismissable bar and a permanent menu
entry. Every read still calls `ofGate()`; nothing renders differently until Modernize is pressed.

Four things P3 established that P4 and P5 should read before starting:

- **Three more types opt out of the base chrome seeds than the design's corrections had found**, discovered by
  re-reading every override while extracting the hook rather than by trusting the corrections' enumeration.
  Seven types (`CheckBoxVSAssemblyInfo`, `ComboBoxVSAssemblyInfo`, `RadioButtonVSAssemblyInfo`,
  `SpinnerVSAssemblyInfo`, `SubmitVSAssemblyInfo`, `TextInputVSAssemblyInfo`, `TextVSAssemblyInfo`) never call
  `super.setDefaultFormat` at all, and `CalendarVSAssemblyInfo` never calls `setDefaultFormat` at all; a
  Modernize that did not know this would give a modernized checkbox or text assembly a border colour no
  freshly created one of the same type has ever taken. `ChartVSAssemblyInfo` and `SelectionBaseVSAssemblyInfo`
  separately replace the whole title composite after `super` returns, so a title-border write the hook makes
  would be inert at creation but land on a live composite under Modernize. Both are now private predicates on
  `VSAssemblyInfo` the hook consults directly, for the reason `isCornerSeedTarget()` already lives there:
  a same-package caller could not consult a private predicate without exposing it or duplicating the list.
- **`Viewsheet.getAssemblies(true)` never yields embedded-viewsheet containers.** Its recursive collector
  recurses into a `Viewsheet`-typed child and never adds it, so `VizModernizeUtil` enumerates containers with a
  second, non-recursive pass. A container is this sheet's own content and gets stamped like anything else; its
  children belong to the embedded asset and stay excluded.
- **`isEmbedded()` means two different things depending on the info type.** The base means "my containing
  sheet is embedded"; `ViewsheetVSAssemblyInfo`'s override means "I am a container," the opposite question.
  P4 and P5's read paths will consult this method, and neither should meet the asymmetry as a surprise.
- **A new assembly inherits its host's stored mark, absence included — never the gate.** So on a legacy
  dashboard an author's newly added assembly comes out unmarked even with the gate open, and the sheet stays
  wholly legacy until someone presses Modernize. Pre-existing since P1, but P3 is where it first has visible
  consequences worth restating.

**Outstanding: the eleven manual checks the plan calls for, plus a twelfth added while building.** None has
run — they need a built server, a browser and a legacy dashboard. See the design document's P3 section for
the full list, including the added check on what an empty undo checkpoint does to Ctrl+Z.

**P4 built 2026-08-18; committed unchanged as `8ef511e45`, "feat(viewsheet): render from the assembly's
mark, not the org gate" — confirmed an ancestor of `HEAD` on 2026-08-19 with `git merge-base
--is-ancestor`.** `VizContext.ofGate()` → `of(info)` across
the 43 read-path sites the design's "shape P4 takes" table lays out — model layer, export and painter, chart
pipeline, dialog models, services and controllers, query and lens, report-only and the one info-local site —
plus the creation-path flip decided the same day: `VSAssemblyInfo.java:1235` now seeds from `of(this)` rather
than `ofGate()`, so a new assembly's persisted seeds and its mark agree by construction instead of by
coincidence. One call survives on the org gate, `ChartColorPaletteController.java:45`, documented in place
for the reason the design's §4 already gives — a parameterless bootstrap fetch with no assembly to resolve a
mark from. A literal sweep (`grep -rnF "VizContext.ofGate()" --include=*.java core/src/main`) confirms exactly
that one line, and the persisted guard, `VizContextReadFlipTest.exactlyOneDocumentedSiteStillReadsTheOrgGate`,
asserts it tree-wide by filename rather than by package, closing four packages a set of per-package
assertions had left uncovered. The full `core` suite passed at 4899 run, 0 failures, 0 errors, 67 skipped, on
the first run to exercise every one of the 43 sites together.

Two things P4 established that P5 should read before starting:

- **The two open plumbing questions P2 left behind resolved the way P2 guessed they would.** The three
  composer dialog models are threaded rather than left on `ofGate()` — `ChartPropertyDialogService` and
  `RegionPropertyDialogService` each resolve the dialog's own chart by object id and pass its context through
  four widened `ChartRegionHandler` methods — and `CSSProcessor.applyCSS` takes `VizContext.LEGACY`, since the
  report-path model it operates on has no route to an assembly at all. Neither answer required inventing a
  third option.
- **The sweep's own planning had a regex bug worth naming, because it is the kind of mistake a future sweep
  could repeat.** An early `grep -rn "VizContext.ofGate()"` (no `-F`) matched `VizContext.java`'s own
  `public static VizContext ofGate() {` on the strength of an unescaped `.` matching a space, and was read as
  a second surviving call site rather than a definition. A literal match resolves it; the persisted guard
  above is what makes the distinction durable.

**The nine manual checks the plan calls for have all run, and all passed, 2026-08-19** — recorded in
`8ef511e45`'s commit message. They needed a built server, a browser, a dashboard saved before the mark
existed, and exported PDF, PNG and Excel files. Included is the check that could not have passed before this
phase: a legacy dashboard under an open gate rendering legacy chrome. Alongside them, 4899 core tests green,
a clean cross-module build, and exactly one documented gate reader still guarded by name. **P3's eleven
remain outstanding and are not covered by any of these.**

**The reverse half was overruled on 2026-08-19, and this is the largest change to M since it was written.**
Decisions 6 and 7 had unchecking the org gate sweep every marked assembly in the org: persisted, wholesale
across the DEFAULT and USER tiers, asynchronous, behind an automatic restore point, with scheduled tasks
cancelled and blocked and open composer sessions blocking the flip unless the admin forced it, and the whole
thing resumable and idempotent because a forced or failed run leaves a half-and-half estate.
**Decision 13 replaces all of it with a per-dashboard Revert action — the exact mirror of Modernize.**
Disabling the gate now reverts nothing at all: the gate decides what new content is stamped with and whether
Modernize is offered, and the mark decides how an assembly renders.

**Why the decision was taken — four arguments, all from decisions already in the set.** It is the rule
decisions 2 and 5 already chose, applied in the other direction: nothing automatic touches a saved asset, and
decision 6 was the last place anything did. Decision 6 had named its own worst property — "the admin flipping
the switch is usually not the person whose work is being replaced" — which is why it needed a restore point;
a per-dashboard action puts the decision in front of the person who owns the work and makes the restore point
an ordinary undo step. Decision 5's argument against a bulk *forward* path (modernizing changes geometry, so
bulk produces hundreds of dashboards needing hand repair) applies unchanged to a bulk *backward* path, and
had never been applied there. And it makes the gate's two axes consistent: decision 9 already said unchecking
dark mode changes nothing that exists, and needed its own justification only because the modern axis behaved
differently.

**What it buys this roadmap.** The release gate loses the async sweep engine, the restore point and its
documented procedure, scheduler cancellation and blocking, composer-session blocking with a force override,
and resumability across a partial run — replaced by one composer action of roughly P3's weight. Both of the
decisions file's open items, Quartz misfire policy and guaranteed scheduler resume, close by obsolescence.
Two accepted costs — "revert is not recoverable" and "half-and-half estates during a flip" — go with them.
The card radius 12→6 unblocks a phase earlier, because it never needed the sweep specifically, only *a*
reversal path for `resolveSeededCorner`'s retirement.

**What it costs, and it is not softened anywhere: there is no org-wide off switch with teeth.** A customer
who adopts modern across three hundred dashboards and then reverses course visits three hundred dashboards.
A scripted bulk revert (shell DSL or deploy API, over an explicit list) would need none of the sweep's
machinery because it is a deliberate operation rather than the side effect of a checkbox — but it is not
committed work, and decision 5's "revisit if customers ask" is the standing disposition. A second, smaller
cost: the EM property's label outlives its meaning, since "Modern Visualization: off" no longer makes
anything look legacy. It wants a rewritten description or a rename.

**What P6 must not split.** The `gate &&` term in `VizContext.of(VizMark)` (`VizContext.java:66`) and both
`PlotDescriptor` seed booleans (`isSmoothLines()` `:635`, `getBarCornerRadius()` `:1319`) are deleted in the
same commit. After P4 those two getters are the only reads left that still test the org gate, so deleting the
term alone leaves a marked chart in a gate-off org modern everywhere except its bar corners and line
smoothing. Deleting the booleans in turn requires the `else` branch in `ChartVSAssemblyInfo.seedChromeDefaults`
(`:106-112`), which is forward-only today precisely because those booleans did the reversing.

**Superseded by a product decision set, 2026-08-12. Read
[seeded-value-reversibility-decisions.md](./seeded-value-reversibility-decisions.md) before implementing
anything in this section or in widget spec §03.** That file departs from §03 on three points: reversal is a
**persisted wholesale revert** rather than a recompute onto a clone, there is **no automatic forward
re-seed** (modernization is opt-in via a button), and `gate-off` is **not a stored state** — the mark is
`unmarked` / `modern-light` / `modern-dark`. **A fourth was added 2026-08-19 by decision 13, and it is the
largest: the gate reverts nothing in either direction.** Reversal is still persisted — but it is triggered by
a person pressing Revert on one dashboard, never by the gate. The analysis below is retained because its code citations hold
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

**Settle axis-blindness first — it fails today, on two axes.** *(Half of this is now obsolete: P2 deleted the
sub-gates, so only the dark axis survives. The chrome bullet is retained as the record of how the question was
framed, and because §04's rationale is still cited elsewhere.)* The gate was three properties
(`viewsheet.modernVisualization` `VSDensityDefaults:40`, `viewsheet.modernObjectChrome`
`VSObjectChromeDefaults:44`, `viewsheet.darkMode` `VSDensityDefaults:48`) and the mark records one.

- **Dark. Still live.** All three persisted colour seeds branch on `isDark()`. Create with modern
  and dark on, turn dark off: mark and master gate still agree, nothing recomputes, the dark card
  background stays in a light dashboard forever. Decision 9 keys the dark axis off the mark; P4 is where that
  lands.
- ~~**Chrome sub-gate.**~~ **Gone.** The seeds guarded on the *composed* `VSObjectChromeDefaults.isModern()`
  while the mark recorded the *master* gate, and §04 line 381 defended that choice. P2 deleted both the
  property and that `isModern()`, so the composed predicate no longer exists and this axis cannot strand
  anything. The citation `VSObjectChromeDefaults:44` above no longer resolves to a property read.

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
the branch ships. **Still true on 2026-08-19, and the release half got cheaper without getting less
mandatory:** decision 13 shrank what release needs from a sweep to P6, but the pre-mark cohort write-off
still expires the day the branch ships, and P6 is still on the near side of that date.

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
   path**. Decision 5. §03's "applies live with no confirmation dialog" does not survive — ~~decision 6's
   revert is destructive and needs a confirmation and an automatic restore point.~~ **Restated 2026-08-19:**
   Revert still takes a confirmation, because it discards chrome an author may have been working against for
   months; the automatic restore point is gone with decision 6, replaced by the same one composer undo step
   Modernize takes. Modernize itself still needs no confirmation — it adds chrome, and undo is one step away.

**Two things §03 does not account for, found while deciding.** Bookmarks carry formats —
`TableVSAssembly` the whole `FormatInfo` (`:157-163`), `ChartVSAssembly` the `ChartDescriptor` and a
`VSCompositeFormat` (`:474-490`) — so any reversal that ignores them is undone the next time a user opens an
old bookmark (decision 10). And the dark axis has to be keyed off the mark too, or unchecking dark mode
leaves persisted dark card backgrounds under read-time light chart chrome (decision 9).

---

## What P5 left behind

**Added 2026-08-19, after P5 was built.** P5 is "browser reads follow the mark": the server's resolved
answer reaches the client on `VSObjectModel`, fifteen bindings across eight templates carry
`viz-modern`/`viz-dark` per assembly, the body class became `viz-shell`/`viz-shell-dark`, and
`GuiTool.isVizModern()` is gone. **Shipped as `4c237a7dd`**, 61 files. Automated gates: core 4905/0
failures, portal 1316+356 passing, full `-Pcommunity,enterprise` build clean. **Every manual check passed**
— the viewer, composer, layout pane, wizard, binding pane and embed, plus P3's eleven, which had never been
run before this pass.

Six items came out of the phase and **two were withdrawn after testing** — they are kept below with the
reasoning that produced them, because both were code-reading findings that reasoned one step past what they
had verified, and that is worth not repeating. Of the four that stand, two are new findings about
pre-existing behaviour and two are things P5 deliberately did not do.

### 1. Binding-pane chart toolbar shows two dead buttons — pre-existing, XS fix — DONE

**Both predicates landed 2026-08-20, and the section named only half the problem: there are two hosts, not
one.** The fix as prescribed — `&& !this.binding` on `propertiesToolbar.visible` in `chart-actions.ts` and on
the hide-toolbar→kebab move in `abstract-vs-actions.ts` — was verified in a running composer and **the gear
and kebab were still there**, because the screen in question was the **object wizard's preview pane**, not
the classic binding editor.

`object-wizard-pane.component.ts:96` provides `ContextProvider` via `VSWizardPreviewContextProviderFactory`,
which sets `binding` **false** and `vsWizardPreview` true, so a binding test cannot reach it. Both predicates now carry
`&& !this.vsWizardPreview` as well.

**Correction to this entry, made the same day: the mechanism first recorded here was overstated.** It said
the gear fires into an unsubscribed output, because `wizard-preview-container.component.ts:76` declares
`@Output() onAssemblyActionEvent` and nothing under `vs-wizard/` binds to it. That much is true and is not
the whole picture: the preview renders `<vs-chart cChartActionHandler [actions]="actions">`
(`wizard-preview-container.component.html:51`), and **two other subscribers take the same emitter** —
`vs-chart.component.ts:258`, whose switch falls through to `propertiesHandler` for ids it does not name, and
`chart-action-handler.directive.ts:61`, which handles six ids (the four hyperlink variants, highlight and
conditions). So actions in the wizard are not universally unrouted.

**The gear being dead there is a tested observation, not a derived one** — confirmed in a running wizard —
and the precise reason the properties path does not function in that host was never isolated. The
suppression stands on the product argument (the wizard carries its own configuration surfaces, and a dead
button is worse than no button) plus that observation. Anyone reopening this should isolate the mechanism
before quoting one.

**The dismissal case is worse in the wizard than in the binding pane, and this is why the guard belongs on
both.** The "menu actions" wrapper that would surface a relocated dismissal is *itself* hidden by
`!this.vsWizardPreview` (`abstract-vs-actions.ts:465`). So under the mark the wizard moved the dismissal off
the toolbar and into a menu that never renders — not relocated, stranded.

**A third defect surfaced once the gear was gone: the wizard's kebab opened onto nothing.** Verified in a
running wizard, then reproduced exactly in a unit probe — marked chart, compact, 400x200: `resident` true,
`allowedActionsNum()` 3, **two** visible toolbar actions, and `showingActions` carrying `more actions`
anyway while `getMoreActions()` returned empty.

The cause is one line, `abstract-vs-actions.ts`: `const needsKebab = modern || width < actionsWidth`, where
`modern` is `this.resident` — `isAnchoredResident(objectType, model.vizModern)`, which reads **the mark and
the body density class and never the host**. So every marked assembly at compact-or-above got a kebab,
overflow or not. The design knew the kebab could be empty and said so in a comment; what made that
survivable was the trailing `menu actions` wrapper staying on the strip to carry the menu. **In the wizard
that wrapper is hidden (`!vsWizardPreview`) and `vs-wizard/` has no `actionsContextmenuAnchor` anywhere**, so
there was no menu route at all and the control opened an empty dropdown.

Fixed by gating `needsKebab` on `kebabHasContent()`, which asks the very list the kebab opens rather than
re-deriving the overflow arithmetic. Two things that look like details and are not: **emptiness cannot be
read off the array's length**, because `ToolbarActionsHandler.getMoreActions()` pads its result with one
empty group per overflowed slot; and the touch / 32-56px routes are untouched because both are
`allowedActionsNum() === 0`, where `getMoreActions()` returns the flattened whole menu. **This also removes
the same dead click in the viewer and composer where nothing overflows** — accepted deliberately, and one
existing test that asserted the empty kebab was updated, keeping the property it was actually written to
guard (both real actions stay on the strip).

**Flattening the kebab everywhere under the gate was raised on 2026-08-20 and is planned, not done** —
see [the plan](../../plans/2026-08-20-flatten-kebab-under-the-gate.md). The viewer is the case that motivates
it: its kebab's entire content is the `menu actions` wrapper, so every menu item sits three clicks away for
no benefit. The plan is not a one-line change for two reasons, both measured rather than assumed, and both
recorded there.

**Evidence for the open composer-anchoring question in item 2, found on the way.** `AbstractVSActions
.resident` is true in the wizard, the binding pane **and** the composer canvas — none of which pass
`[anchorInTitleLane]` — so all three are also carrying the gate's three-action cap while their strips are
not anchored at all. Making `resident` consult the host would fix that and would likely subsume the empty
kebab as well; it was **not** taken here because it changes composer behaviour inside a question nobody has
decided. Whoever settles item 2 should settle this with it, and should check B3 ("the dismissal is not in
the composer canvas kebab") against the same cause.

**The lesson for anyone extending a context predicate here: enumerate the hosts, do not reason from the one
in the report.** Four `ContextProvider` factories set neither `viewer` nor a plain `composer` — binding
(two flavours), wizard and wizard-preview — and `mini-toolbar` is mounted by
`vsview/view/vs-object-view`, `vs-wizard/gui/object-wizard/wizard-preview-container` and
`vs-wizard/gui/objects/vs-wizard-object`. The last of those passes `[miniToolbarActions]` rather than
`[actions]`, so `AbstractVSActions` predicates do not reach it at all. Five tests in
`abstract-vs-actions.spec.ts` cover them, using the `BindingContextProviderFactory` that already existed
beside the viewer and composer ones. The whole action suite (29 files, 260 tests), the mini-toolbar specs
and the `vsview` TL specs are green.

**A correction was published here on 2026-08-20 and withdrawn the same day; the section as originally
written was right.** The withdrawn claim was that the kebab renders in the binding pane with the mark off as
well as on, so only the gear was the mark's. Its evidence was `TestUtils.createMockVSChartModel`, which
makes six chart menu entries visible (`chart properties`, `chart show-format-pane`, `chart show-title`,
`chart MenuAction HelperText`, `chart show-data`, `chart open-max-mode`) that a real binding-pane chart does
not. **In the running composer an unmarked chart's strip is exactly two buttons — the dismissal and summary
data** — so the kebab is the mark's after all, and "reproduces the legacy two-button set exactly" stands as
the acceptance test.

**The lesson is the one items 5 and 6 below already record, one layer in:** a mock model is not the
application. A visibility predicate read through `TestUtils` answers "what does this fixture make visible",
not "what does a user see". Where a claim is about what renders, the running app is the only evidence that
settles it.

The tests keep the shape they were given, because it survives the correction: they assert the marked strip
and the unmarked strip are **identical** rather than naming two buttons. That property holds under the
fixture and in the app, and does not have to be rewritten when a fixture changes.

**Symptom as originally reported.** Editing a chart in the binding pane, the mini-toolbar shows *summary data*, a **gear
(Properties)** and a **kebab**. The gear and the kebab do nothing. Legacy showed only summary data and
hide-toolbar.

**Root cause.** `vsview/view/vs-object-view.component.html:30` renders `<vs-chart #object>` with **no
action-handler directive**. The binding pane has `bTableActionHandler`, `bCrosstabActionHandler` and
`bCalcTableActionHandler` — `vsview/action/` contains exactly those three files and no chart equivalent. So
chart toolbar actions have never been routed in the binding pane and the buttons have nothing to dispatch
to.

**Not P5's.** At pristine `HEAD` both extras were gated on `GuiTool.isVizModern()` — the *body* class — so
in any modern org they already appeared in the binding pane. P5 moved the gate from org-wide to
per-assembly; it did not put these buttons here.

**Recommended fix — suppress rather than wire.** A dead button is worse than no button, the binding pane
already has its own settings surface, and making Properties work there is a product decision (should the
chart properties dialog open from inside the binding editor?) rather than a bug fix. The mechanism is
already in place and already used by these classes: `AbstractVSActions` has `protected get binding()`
(`:88-90`) over `ContextProvider.binding` (`vsobjects/context-provider.service.ts:114`).

- `vsobjects/action/chart-actions.ts:514` — add `&& !this.binding` to `propertiesToolbar.visible`
- `vsobjects/action/abstract-vs-actions.ts:432` — gate the hide-toolbar→kebab move on `&& !this.binding`,
  which returns hide-toolbar to toolbar index 0 and reproduces the legacy two-button set exactly

**Second, separate defect in the same screenshot — NOT folded in, and it is not XS.** The binding pane's
toolbar *overlaps the chart title lane*. `vs-object-view.component.html:66` passes `[top]="0" [left]="5"`
with `[forceAbove]="true"`, so `topY` floors at 0 and the strip lands on top of the chart. Pre-existing, and
it reproduces with the mark off.

It was left open deliberately on 2026-08-20. `.vs-object-container` is `overflow-x: auto; overflow-y: hidden`
(`vs-object-view.component.scss:22-25`) and the strip is `position: absolute` inside it, so the floor at 0 is
the only thing keeping the strip on screen at all — there is no space above origin to move into. Making room
means either padding the container against its `min-height: objectFormat.height`, or anchoring the strip in
the title lane, which is exactly the undecided product question in item 2 below. Needs a running composer to
measure; do not treat it as a predicate change.

### 2. The composer never anchors the mini-toolbar — open product question, not a defect

Anchoring is decided by the host passing `[anchorInTitleLane]`. The viewer supplies it
(`vs-object-container.component.html:357` ← `isToolbarAnchored(vsObject)`, with `[residentKebab]` at
`:358`). The composer supplies neither, passing only `[top]="vsObject.objectFormat.top"` — and never has:
its block at pristine `HEAD` contains zero occurrences of `anchorInTitleLane`. **The anchored strip is a
viewer-only feature of the toolbar rollout.**

The gap above the card in the composer is also deliberate: `topY` returns `top - miniToolbarHeight - adj`
with `adj = 3`, commented "don't cover resize handle in composer".

**The question for item F:** should the composer anchor too? If yes it needs `isToolbarAnchored` /
`isKebabResident` equivalents on the composer host plus a decision about the resize-handle clearance that
3px exists for. Nobody has decided this; P5 never touched it. A P5 manual check asserted viewer/composer
parity on an inferred premise and was corrected rather than the code.

### 3. Tooltips stay org-scoped — both halves deferred together (R15 + R20)

`TooltipComponent` and `TooltipDirective` take content, CSS classes and tail geometry only; neither has any
route to a source assembly, and the only one available is the `tooltipCSS` input set by every site that
applies `wTooltip`. So `_directives.scss`'s ninety-line tooltip block was re-pointed at `.viz-shell` (one
line) rather than compound-converted, and `tailRadius()` reads `GuiTool.isVizShell()`.

**`AbstractChartInfo.getTooltipStyle` is the server half of the same surface and was deferred with it.**
The plan had assumed `PlotArea` could reach a `ChartVSAssemblyInfo`; it cannot — it holds a `ChartInfo`
(`PlotArea.java:79,88`) and so does `ChartArea` across five constructor overloads whose callers include the
report painter, the exporter, annotations, the scheduler and the format painter, several with no assembly
in existence. Converting only `GraphBuilder` and the dialog service would make the tooltip *content*
builder and the model *shipped to the browser* disagree about the same chart — the exact property
`AbstractChartInfo:3739-3742` says the method exists to guarantee.

**Cost while deferred:** on a mixed dashboard in a modern org, a legacy assembly's tooltip takes modern
tooltip chrome. Status quo, not a regression. Doing it means the constructor-threading job above, unchanged
in size by waiting.

**Updated 2026-08-20: P6 adds a second, mirror-image cost, and the item was decided rather than deferred
again.** Once the `gate &&` term goes, a marked chart in a **gate-off** org renders modern everywhere and
takes a legacy tooltip — the same defect pointing the other way, and new, because until P6 gate-off meant
legacy throughout. Weighed against the constructor-threading job (five `ChartArea` overloads reached by the
report painter, the exporter, annotations and the scheduler, several with no assembly in existence), it is
**accepted and documented rather than fixed in P6**. Recorded as an accepted cost in the design and in
decision 13. It stays R20 for whenever the threading job is worth doing on its own terms.

### 4. Three overlay surfaces follow the org, not the assembly (R17, R21)

- **The pop-dim scrim** behind a data tip fills a canvas over the whole container, so `popDimColor` reads
  `GuiTool.isVizShell()`.
- **Data-tip and pop-component offsets** may sit 4px stale on mixed dashboards:
  `vs-data-tip.directive.ts` and `vs-pop-component.directive.ts` hold only assembly *names*
  (`@Input() dataTipName`, `popContainerName`), no model, so they pass `isVizShell()` to
  `getMiniToolbarHeight()`.

Each is a page-level overlay where a per-assembly answer would be inventing precision the surface does not
have. Revisit only if a mixed dashboard makes it visible.

### 5. ~~Print-layout text and image objects always render legacy~~ — WITHDRAWN, not reproducible

**Reported by the final review as a marking defect (M6/R26); withdrawn 2026-08-19 after the human partner
could not reproduce it.** The mechanism the review described is real, and the conclusion drawn from it was
wrong.

Real part: `VSLayoutService.java:410` does replace the info — `assembly.setVSAssemblyInfo(info)` on a
freshly constructed `TextVSAssembly`/`ImageVSAssembly`, so whatever mark the constructor inherited at
`AbstractVSAssembly:136` is gone.

Why it does not matter: **neither type has any gate-dependent chrome to lose.**
`TextVSAssemblyInfo` is in `bypassesBaseChrome()` (`VSAssemblyInfo.java:1327`), so `seedChromeDefaults`
returns immediately for it — a text object receives no border colour, no radius, nothing, marked or not.
And neither text nor image is in `isCornerSeedTarget()`'s positive list (`:1297-1301` — table family, chart,
selection list, selection tree, current selection), so no card radius either. For an image the base hook
does run and writes a DEFAULT-tier border *colour*, but that is invisible unless a border style is set.

**The lesson, not the defect, is what is worth keeping:** the review reasoned from "the mark is discarded"
to "it renders legacy" without checking whether the type consumes the mark at all. Same
inference-over-verification shape as several P5 findings, one level further out.

### 6. ~~Embedded assemblies always take dense metrics~~ — WITHDRAWN, disproved by test

**Reported by the final review (M7/R27), defended once by the controller, then disproved: an embedded
viewsheet with a crosstab, org density set to compact, rendered compact row heights.** Withdrawn
2026-08-19.

**Two errors produced the false finding, and both were the same mistake.**

1. **A directory-scoped grep stood in for a behavioural question.** The claim "nothing in the embed app sets
   a viz class" came from grepping `web/projects/portal/src/app/embed/` for `classList` and finding nothing.
   But `embed/viewer/embed-viewer.component.html:24` renders `<viewer-app>` (imported at
   `embed-viewer.component.ts:51`), and `viewer-app.component.ts:2792-2800` is one of the three shells that
   toggles `viz-shell` / `viz-shell-dark` / `viz-density-<mode>` on `document.body`. The code that sets the
   class lives outside the directory that was searched. The question was "does this page end up with the
   class on `body`", not "does any file under `embed/` write it".
2. **The shadow boundary was assumed always present.** `ShadowDomService.addShadowRootHost` is guarded by
   `if(element && this.isInShadowDom(element))` (`shadow-dom.service.ts:32`) — it re-homes Angular's
   `<style>` elements when the host page **has already** mounted the element inside a shadow root. It does
   not create one. On an ordinary embed there is no boundary between `body` and the assembly wrapper, so
   `.viz-density-compact .viz-modern` matches normally.

**Untested, and deliberately not claimed as a defect:** the case where a customer mounts `inetsoft-viewer`
or `inetsoft-chart` inside their *own* shadow root. There a density selector genuinely could not match
across the boundary — but nothing here has been observed to misbehave, and the stylesheet situation in that
configuration differs in other ways too. Do not record it as a defect without a reproduction.

**Embed gaining modern chrome at all is still new from P5** — before this phase the wrapper carried no
`viz-modern` and embed rendered fully legacy. That part stands.

### Carried into P6 — do not lose this

**Reviewed 2026-08-20 against `35ca4fce0`, and the list grew.** P6's spec was checked citation by citation
before planning; six findings changed the phase and four were decided the same day. The full account is in
[the design](../2026-08-14-seed-mark-forward-half-design.md) §5's P6, which now carries five pieces rather
than four, and in [the decisions file](./seeded-value-reversibility-decisions.md) decision 13. The headline
is in the dependency picture above: **`resolveSeededCorner` is a fourth deletion inside P6's same-commit
set**, not a follow-on, because the design's claim that the two `PlotDescriptor` booleans are "the only reads
left that still test the org gate" is false. Three items below, two of them new.

**P6 must drop the `if(modern)` guard on the density body class.** All three shells add
`viz-density-<mode>` only when the org gate is on (`portal/app.component.ts:272`,
`composer/app.component.ts:145`, `viewer-app.component.ts:2796` — re-verified 2026-08-20; the `:270` first
recorded here has moved). After P6, gate-off stops meaning legacy — so a marked assembly in a gate-off org
would find no density class on the body and fall back to the bare `.viz-modern` dense defaults. One line
each, and it is required under either of the two density designs that were considered.

**New 2026-08-20 — and it is a P5 regression sitting on the same selectors, so it is folded in rather than
filed.** Pre-P5 the density matrices were **compound** (`.viz-modern.viz-density-compact`), so a `body`
carrying both classes matched and org-level surfaces resolved the org's chosen density. Post-P5 they are
ancestor-descendant and `.viz-shell` appears only in the bare/dense group (`_viz-tokens.scss:115-117`), so a
body carrying `viz-shell` + `viz-density-compact` resolves **dense** — a descendant selector cannot match its
own element. Confirmed against `git show 4c237a7dd^` of the file. The affected consumers are exactly the ones
the design names as the reason `.viz-shell` was added: the combo-box dropdown appended to `document.body`, the
worksheet details pane, the schedule task list. Fix is two added compound selectors,
`.viz-density-compact.viz-shell` and `.viz-density-comfortable.viz-shell`. **The design's §3 correction box
asserts the opposite** — that a `viz-shell` body resolving dense "matches what a body carrying `viz-modern` +
`viz-density-<mode>` resolved before the split." It does not; that claim is now struck through in place.

**New 2026-08-20 — the EM hides Visualization Density behind the gate checkbox.**
`look-and-feel-settings-view.component.html:38-49` shows Density and Dark Mode only while Modern Visualization
is checked. After P6, density is still read live for every marked assembly in a gate-off org
(`VizContext.of(mark)` takes it from `VSDensityDefaults.mode()` regardless) and the body-class fix above makes
it apply in the browser too — so an admin loses sight of a setting that still does something. Density is
unhidden; **Dark Mode stays hidden**, because the dark axis is stamped at creation and a gate-off org creates
no marked content. The gate's description is rewritten in the same pass, which is decision 13's "the EM
property now says something it does not do."

**Two gate reads survive P6 as accepted costs, both decided 2026-08-20 rather than deferred by omission.**
`AbstractChartInfo.getTooltipStyle` (`:3736-3746`) — the R20 half recorded under "What P5 left behind" item
3 — and `VSChartInteractionDefaults.isInlineSvg()` (`:41-49`). Each makes a marked chart in a **gate-off** org
modern everywhere except one surface: legacy tooltip chrome, and no inline-SVG animation or hover dimming.
Both are the gate-off mirror of an inconsistency already accepted in the gate-on direction. Threading the
tooltip means widening five `ChartArea` constructor overloads reached by the report painter, the exporter,
annotations and the scheduler — larger than the whole of P6; `isInlineSvg()` is interaction rather than chrome
and already carries a `graph.svg.inline` override, which is the workaround for release notes.

**Also for P6's reader:** the design document's §3 carries a correction box added after P5 shipped. Three
of its statements were edited *during* P5 on reasoning implementation then disproved, including one that
would send a P6 implementer in the opposite direction on density. Read the box, not the body text — and note
that the box itself has since been corrected on one point, per the second item above.

**And one prerequisite that is not code.** P6's headline verification — a modernized-then-reverted dashboard
byte-comparable with one never modernized — is meaningless if it runs against a pre-mark-cohort asset, which
carries seeded modern values with no mark. **P0's status is unverified**; use a freshly created dashboard, or
confirm the pre-mark dev dashboards are gone, before reading that check as a pass.

### Test-coverage debt this phase created

Recorded because it is invisible from the diff. Rulings R9, R10 and R11 left **Tasks 2–4 with no automated
tests** — the two container `.tl.spec.ts` files construct components directly with mocked services and
render no DOM, so the plan's DOM assertions could not be written. **Four templates have no spec file at
all**: `layout-object.component`, `wizard-preview-container.component`, `embed-chart.component`, and the
layout pane's child-assembly bindings. An Angular binding on a wrongly-scoped variable applies no class and
raises no error, so those four are verified by code reading and a type-check only.

Three pre-existing failures at `HEAD`, not from this work: `chart-plot-area.component.interaction.tl.spec.ts`
"should redraw the current selection when it belongs to the plot area" (**confirmed** pre-existing via
`git stash`), and two scrollbar assertions in `chart-plot-area.component.display.tl.spec.ts` (200 vs 201,
100 vs 101 — not stash-confirmed).

---

## Was blocked on a design decision — now answered

**The title lane height row — answered 2026-08-13.** See
[chart-card-anchored-strip-lane-decisions.md](./chart-card-anchored-strip-lane-decisions.md): the widget
spec's values are taken, the strip stays 24px and contained, suppression becomes geometric, and a hidden
title suppresses the strip. The paragraph below records why the first attempt was abandoned; the 13.3
`userDataRowHeight` pattern is the mechanism it was missing. That mechanism now exists — `userTitleHeight`
shipped in `1d26dbefb`/`d4d0d5d48`.

**The row was gated twice more after that, and both gates are now clear.** An earlier revision said the row
"needs no server change"; the third revision replaced that with "no longer gated on the seed mark or on
anything else." Both were wrong, and for the same reason: the row needs to *read* whether an assembly is
modern. `userTitleHeight` answers a different question — whether an author chose the height — and P1's
mark, though it existed and persisted from that point on, was read by nothing until P4. The row was gated on
**P4**, when the read paths would start consulting the mark; P4 is now built, so that gate is clear too. Its
four design questions are answered and its blocking phase has landed — what is left is scheduling it, not
waiting on it.

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
| **Dark — four browser-DOM surfaces** — *moved out of "ready", see below* | [Decisions](./chart-card-open-item-decisions.md) §2, plan at `plans/2026-08-11-chart-card-dark-browser-surfaces.md` | CSS only, and still correct — but decision 4 turns `viz-dark` from a body class into a per-assembly scope at P5, so doing this before P5 means doing it twice. Cheaper after. The v3 dark ticket's dependency claims apply to the server half, which is different work — see §4.2 |
| **Chart interior dark palette** | `Chart card dark values - ticket.md` item 3 | New, unowned, and the most valuable thing in that ticket. `GDefaults` has no dark branch; nothing to reconcile against, so it is design work |
| Affordance sweep | Widget spec §08 step 1 | Live-view only, no export risk |
| Selection list interior | Widget spec §08 step 2 | The one widget the initiative has not touched |
| Resize Plot sliders | Handoff item 13 | `vs-chart.component.scss` untouched since `e8df3491b` (2024-07-15). Delete the 15 `-ms-` lines first — the file halves before anything is retokenized |
| Nav bar | Handoff §02 | Reposition to lower right inset from the plot area, plus `z-index: 9999` and the off-scale `border-radius: 5px` (`chart-nav-bar.component.scss:20,26,32`) |
| Data-tip registry remainder | Handoff 1c | Two of three assignment paths still assign directly; documented in-code at `_directives.scss:20-29` as a known partial state. v3 re-escalated this to "highest-value single change in the whole set" on a misread constant — 9996 is the scrim, not the tip. See §5.6; it is a maintainability item |
| Chart colour literals | Handoff item 10 | 11 occurrences of `#ff8d41`. Declare the gridline CSS token here |
| Chart type scale | Handoff 4a | Gated only by measuring whether 9pt renders as 9px — one build. Its chrome tier is already fully shipped |
| Drill and DC tips · zoom naming · dead menu icons | Handoff step 5 | The "~50 dead icons" count has never been verified |

## Unblocked by `380705bc1`

This section read "after the density gating commits" until 2026-08-13. It has.

**Rollout slices 4 and 5** — the container and the calendar. `ANCHORED_ASSEMBLY_TYPES`
(`mini-toolbar.service.ts:41-53`, re-checked at `ef42a6c65`) carries six types; the container is
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

**Amended 2026-08-20: this entry is now only about the constant, and its cohort caveat dissolves.** The P6
review moved the *retirement* of `resolveSeededCorner` inside P6, because its gate read strands a marked
assembly's card radius the moment the `gate &&` term goes. So what is left here is `CARD_CORNER_RADIUS`
12 → 6 and the sign-off it needs. The caveat above described a stripping behaviour that no longer exists
after P6, so there is no cohort question to answer — the constant can move on its own schedule.

---

## Done

Hashes re-resolved 2026-08-14 after the second rebase; each was checked with
`git merge-base --is-ancestor <hash> HEAD`. The third-revision column is kept only to help anyone reading an
older copy of this file recognise what moved — **none of those hashes is reachable from `HEAD` any more.**

| Item | Commit | Cited at the third revision |
|---|---|---|
| Phase 9B dark mode — every server-rendered surface, chart included | `fc15df1da` | `8f138595a` |
| Inline-SVG chart rendering coupled to the modern gate | `1124bf6f7` | `6e803a702` |
| Data-mark-anchored tooltip tail | `4104c80a2` | `c4cfa342b` |
| Shell tooltip retokenize + CARD ramp + data-tip layer declaration | `f4ddb2ea2` | `8b6b006ae` |
| Selection vocabulary — chart-owned surfaces and the annotation border | `a7ec8796d` | `d627ec403` |
| Menu-action reachability, the §06 ladder, and rollout slice 1 — chart | `ef690e83e` | `6da9c024f` |
| Right-click reaches max mode on tables | `de765242d` | `0db62036f` |
| Rollout slice 2 — table, crosstab, calc table | `1e74918af` | `931bdb02b` |
| Max-mode mini-toolbar positioning fix | `f6c87eda4` | `f3a299cd0` |
| Rollout slice 3 — selection list and tree, kebab-only at any width | `5a0d3e254` | `5ed02f6ed` |
| Strip suppression where the lane cannot hold it — L, density-approximated | `380705bc1` | `55c3bad1a` |
| `userTitleHeight` — N, the flag and its per-type default | `1d26dbefb` | `07c91926e` |
| `userTitleHeight` — N, the thirteen stamps, the propagate and the reset un-stamp | `d4d0d5d48` | `307a6ee09` |
| **M-P1 — the seed mark's field, persistence and creation stamps** | `8f75872a6` | postdates the third revision |
| **M-P2 — `VizContext` threaded, the six `isModern()` predicates and all four sub-gates deleted** | `119bfdaac` | postdates the third revision |
| M-P1's null-guard — a mocked host viewsheet with no assembly info | `be0e3c664` | postdates the third revision |
| **M-P3 — the enumeration point, `seedChromeDefaults`'s bypass predicates, `VizModernizeUtil`, the endpoint and the composer bar** | *uncommitted — working tree, blocked by a commit-approval gate* | postdates the fifth revision |
| **M-P4 — the 43 read-path sites and the creation site flipped from `ofGate()` to the mark, one documented survivor left** | *uncommitted — working tree, blocked by a commit-approval gate* | postdates the sixth revision |

**Why P2 landed as two commits.** `be0e3c664` is a two-line guard in `AbstractVSAssembly`'s stamp plus its
regression test: a mocked `Viewsheet` returns null from `getVSAssemblyInfo()`, which threw and was the sole
cause of ten suite errors at P2's baseline. It belongs to P1's subject, not P2's, so it was split out rather
than folded in. `119bfdaac` carries the phase itself — 71 files under `core/src` — plus its plan, the design
doc's sub-gate decision and this roadmap.

The docs commits, also re-resolved: the v3 design set and the seeded-value decisions in `d4ef55100`, the
strip and lane decisions in `ef42a6c65`, the `userTitleHeight` plan and strip-and-lane decision 5 in
`e47bd207a`, the title-lane answers in `b7bf79157`, what the flag changed for the mark in `74c8638a6`, and
the seed mark's forward-half design plus its P1 plan in `e670744c1`. `91a7babce`, `d1042fd2f` and `e7ca3c69b`
carry the roadmap, the open-item decisions and the design sets.

## Still undecided

- ~~**The four sub-gates.**~~ **Answered 2026-08-14 and shipped in P2: all four are deleted**, and
  `VizContext` keeps its three fields. They were rollout scaffolding — undocumented, absent from EM,
  referenced nowhere outside the six classes that read them, default-on, and one with no test. Deleting them
  also makes "marked but unseeded" unreachable, which several later-phase claims already assumed. Recorded at
  [the design](../2026-08-14-seed-mark-forward-half-design.md) §2, with the mechanics, the tests that went
  with them, and why `graph.svg.inline` is **not** in scope. No behaviour change: all four defaulted on and
  the branch has never shipped. **One residual worth a two-minute check before release:** if any internal or
  demo instance has one of the four keys set to `"false"` in a `sree.properties`, that instance changes
  appearance on upgrade. The argument that this is impossible rests on `viz-updates` never having shipped,
  which cannot be verified from the repo.
- ~~**`CSSProcessor.applyCSS`'s context.**~~ **Answered in P2, confirmed in P4: no route, structurally.**
  Every hop back from it stays inside the legacy report / `ReportSheet` / `ChartElementDef` model, which
  never carries a `VSAssembly`, and `applyCSS(ReportSheet)` has no callers anywhere in `core/src` or the
  enterprise modules. It is report-path only: P4 passes `VizContext.LEGACY` at `CSSProcessor.java:474`.
- ~~**How the three composer dialog models reach an assembly.**~~ **Answered and shipped in P4: threaded
  down.** `ChartPropertyDialogService` and `RegionPropertyDialogService` each resolve the dialog's own chart
  by object id and forward its context through four widened `ChartRegionHandler` methods, so a property
  dialog opened on an unmarked chart previews legacy chrome rather than org-gate chrome.
- ~~**Whether `VizContext` gains an explicit `viewsheet` field.**~~ **Settled by P4 without adding one.** The
  identity-against-`LEGACY` convention P2 surfaced became a fact rather than a field: `GraphGenerator`'s
  `VizContext` member is set once at construction from whichever of its two constructors ran, so the
  LEGACY-identity axis has a real home without a new field on `VizContext` itself.
- ~~**The four L' decisions**~~ — answered 2026-08-13; see the L' section. Nothing blocks L' now: P4 shipped
  the read path the row needed, so what is left is scheduling the work, not a decision or a phase.
- ~~**Dense: hover overlay, or no chrome at all?**~~ Answered 2026-08-12 and now shipped in `380705bc1`:
  no chrome at all. [Decisions](./chart-card-open-item-decisions.md) §4. What remains open from it is
  dense-plus-touch, below.
- **Whether dense-plus-touch is meant to have no affordance**, and now title-hidden-plus-touch with it —
  [strip and lane decisions](./chart-card-anchored-strip-lane-decisions.md) decision 4 widens the same
  question to a larger population.
- **How the seed mark handles defaults added after it ships.** See the seed-mark section.
- ~~**How the title height row reaches assemblies.**~~ Answered 2026-08-13 and the mechanism now shipped:
  the widget spec's 20/26/30, applied in the per-type read path the way `rowHeight()` is, conditioned on
  `userTitleHeight` (`1d26dbefb`/`d4d0d5d48`).
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
  built**: the mark's field and stamp sites, `VizContext` and the re-keyed call sites, the per-assembly
  browser scope, Modernize and its composer bar, in six phases. Authority on mechanism; the decisions file
  stays the authority on behaviour. **Two of its statements were overtaken by what P2 actually built** — it
  says the context is each method's *first* parameter (landed trailing) and describes the
  `initDefaultFormat(boolean vs)` surface as five types (it is nine). Its §2 "interim term" paragraph, by
  contrast, was authoritative and beat the P2 plan, which had dropped the `gate &&` term
- [seeded-value-reversibility-decisions.md](./seeded-value-reversibility-decisions.md) — the seed mark,
  the four seeded values and the mechanism inventory. **Supersedes the roadmap's seed-mark analysis
  below**, which still lists version-blindness as the open question
- [chart-card-slice1-design.md](./chart-card-slice1-design.md) ·
  [chart-card-slice2-tables-design.md](./chart-card-slice2-tables-design.md) ·
  [chart-card-slice3-selection-design.md](./chart-card-slice3-selection-design.md) — how each shipped slice
  works
- `plans/2026-08-11-chart-card-strip-density-gating.md` · `plans/2026-08-11-chart-card-dark-browser-surfaces.md`
- [plans/2026-08-20-flatten-kebab-under-the-gate.md](../../plans/2026-08-20-flatten-kebab-under-the-gate.md)
  — **proposed, not implemented.** Widens `flattenedMoreActions()` from the kebab-only rungs to every
  host that offers the wrapper, so the kebab stops nesting its contents behind a "More" row. Carries the
  two traps that make it non-trivial: flattening resurrects the wizard's suppressed kebab, and the
  strip/menu Properties pair does not share an id, so dedup misses it
- `chart-card-design3/` — the external source set. Regenerated wholesale on each sync, so nothing authored
  there survives. `chart-card-design2/` and `chart-card-design/` are kept as history
- [visualization-implementation-roadmap.md](./visualization-implementation-roadmap.md) — the wider
  initiative, decomposed by phase rather than by slice
