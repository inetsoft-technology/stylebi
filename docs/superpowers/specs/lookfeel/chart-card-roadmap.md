# Chart Card — Roadmap

**Amended 2026-08-28 (not a revision — the seventeenth still stands below).** Three additions, all in "What
to pick up next" and all mirrored in the dependency picture. **Decision 10 / bookmarks is IN FLIGHT in a
parallel session** — check for its branch before starting it. **A new #2: defaulting
`viewsheet.modernVisualization` to true**, which must follow bookmarks, because the gate being off is what
currently confines decision 10's defect to opted-in dashboards. **A new #3: migrating the five read-time
resolvers onto `seedChromeDefaults`** — a value computed only at render is not in the asset, so an export
into an older build renders the card mixed, modern where a value was seeded and classic where it was
substituted. That third one came out of designing the title lane and is the more general form of the
question that design had to answer. The seventeenth revision's note follows.


**Date:** 2026-08-27 (seventeenth revision — **S / §04, the card's sizing and spacing, has SHIPPED, and this file costed it XL when it was not.** `2afb06bc1` carries the 12px card inset and the 4 / 8 / 14(+2) interior gap scale, `2b86a9fa3` two follow-ups. The estimate was wrong for a reason that generalises to every remaining item taken from the external set: it assumed binding §04's gaps meant introducing a spacing scale into a subsystem CSS cannot reach, and three of the four gaps turned out to be **tiered `CompositeValue`s that `GraphGenerator` already threaded, in a class that already held a `VizContext`**. The work was seeding DEFAULT tiers on values that existed. **Check for an existing tier before costing anything else from that set.** **FOUR of §04's own numbers are disproved in code** and each is recorded with its evidence in the new [chart-card-geometry-decisions.md](./chart-card-geometry-decisions.md): `padding` already IS the card inset with the title lane inside it; there is no graph outer margin to zero, confirmed at render, because the band §04 measured was that same padding seen from inside an exported SVG; the legend panel's border and fill return ~2px, not 8–10px; and the gaps needed no new scale. **§05's legend is CLOSED BY DECISION rather than deferred** — every mechanical and palette item was already in, and what the drawing still asks for is declined or moot, reasoning in the dependency picture. **The one thing nobody has answered: the plot lost ~28px of width and ~14px of height, where §2.1 predicted ~19px**, because the difference was §04-b's legend return and that return does not exist. **What is left of §04 is one item, `--inet-chart-line-height` at 1.2.** Bookmarks is still first and still the only thing between this branch and a release. The sixteenth revision's note follows — **L″, the geometric suppression, has shipped, and the anchored strip is no longer gated on density anywhere.** The predicate now measures the assembly's own lane: `lane >= 24` draws the strip, below it draws no chrome at all, and a hidden title yields lane 0 so one rule covers both populations. **The threshold shipped at 26 and was corrected to 24 the same day** — 26 bought 1px of clearance whose only stated purpose was keeping the pill's border off the lane edge, §10.2 deletes that border, and lanes of 24 and 25 were losing their toolbar while the strip physically fitted. `GuiTool.isVizDensityAtLeastCompact`, `rightEdgeReserve` and `SORT_CONTROL_RESERVE` are all deleted, so title-hidden tables get 22px of plot back. **One design premise in L″'s own spec was false and is corrected there rather than quietly dropped:** it claimed touch loses its action route when a lane suppresses, but mobile never used this mechanism — `mini-toolbar.component.html:35` hides the buttons on mobile and `viewer-app.component.html:304` renders a page-level `viewer-mobile-toolbar` fed the selected assembly's actions. The fifteenth revision's note follows: **L′, the title lane height row, has shipped, and with it the last of the six items the seed mark existed to free.** The mark has now paid for all six. Nine included assembly types take the 20/26/30 lane at their org's density, the three excluded types are confirmed unmoved, and title and selection cell height each gained a follow-the-default-density checkbox that replaces the value comparison the dialogs used to infer authorship from. It is off the ranking, in the Done table, and it makes L″ startable for the first time — under decision 3's one-directional rule, which is the thing not to get wrong next. The fourteenth revision's note follows: **§04, the card's sizing and spacing model, is now tracked
here.** It is the largest body of design in the external set with no roadmap entry, no plan and no audit,
and it is a server-side geometry change rather than a token sweep — see "The next long pole" below.
Nothing else in the ranking moved. The thirteenth revision's note follows: **the seed mark is finished. All six phases are shipped,
committed and seen in a browser; P6's ten manual checks and the P0 pre-mark-cohort confirmation have run and
passed, confirmed by a human partner on 2026-08-25.** Two things landed after the twelfth revision was
written and neither was in any plan: `1b8eb3cea`, five bug-fix rounds because Revert did not carry a chart's
colours, and the card radius constant, 12 → 6. **The release gate now has exactly one item left, and it is
unbuilt: bookmarks, decision 10.** It is confirmed unbuilt against the code, and its surface is wider than
decision 10 costed — `<state_info>` carries the whole `VSChartInfo` and therefore the colour frames, so a
stale bookmark un-reverts the palette as well as the chrome. **Also: the branch has been rewritten a THIRD
time and every hash the twelfth revision cited is unreachable, including the ones it had itself re-resolved
after the second rebase.** The Done table has been re-derived by matching commit subjects and its
dead-hash column is deleted rather than updated; see that section. "What to pick up next" was re-derived from
the dependency picture, not repaired. The twelfth revision's note follows: **P6 has been built, and its four automated gates are clean; the
whole phase is uncommitted, and none of its ten manual checks nor the P0 pre-mark-cohort confirmation has
run.** Task 8 ran `core` (4904 tests, 0 failures, 0 errors, 67 skipped), `portal` (1329/1329), `em`
(356/356) and the cross-module `-Pcommunity,enterprise` build (BUILD SUCCESS, 3:55 min) — all four clean.
The commit-approval gate refused all eight of P6's task commits, the same as it refused P3's and P4's before
a human partner committed those two outside the building session — so unlike them there is no commit hash
here yet, only a working tree. Read the Done table, "What to pick up next" and "P6 built 2026-08-21" in the
seed-mark section before treating any of this as verified in a running application: the automated gates
prove the code and the tests agree with each other, not that a screen agrees with either. The eleventh
revision's note follows: **P6 has been reviewed against the code, ahead of planning it.**
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
**Verified against:** community `viz-updates` @ `1b8eb3cea`, which is `HEAD`, plus this commit's own working
tree for the card radius. Every hash in this file was re-checked at this revision with
`git merge-base --is-ancestor <hash> HEAD`; the ones that failed are named in the Done section. The twelfth
revision's "verified against `8ef511e45`" is one of the failures — that hash is no longer reachable. The seventh
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

**Hashes go stale when the branch is rewritten, and it has now happened three times.** The 2026-08-14 rebase moved
all 62 commits onto `e7ef501fb` and rewrote every hash this file cited at the third revision — checked,
and **not one of `8357e05d8`, `952614aa7`, `1c0ace705`, `55c3bad1a`, `07c91926e` or `307a6ee09` is an
ancestor of `HEAD` any more.** They still resolve with `git show`, because the old objects linger in the
repo while being unreachable, so they would fail in a fresh clone and after a `gc`. The Done table below
carries the post-rebase hashes. When checking any hash in this tree, use
`git merge-base --is-ancestor <hash> HEAD` rather than `git show`; `git show` succeeding proves nothing.
Every other file under `docs/superpowers/` still carries pre-rebase hashes and should be read with that in
mind — the design docs' *file and line* citations were re-derived and are current, but their commit hashes
were not.

**The third rebase, and how to read the dead hashes still in this file's prose.** A third rewrite happened
before 2026-08-25 and killed every hash the twelfth revision carried, including the nine it had itself
re-resolved after the second rebase. The dependency picture, the Done table and "What to pick up next" carry
current hashes. **The narrative sections below still cite the dead ones ~38 times, deliberately** — they are
historical prose, and rewriting a hash inside a sentence about what a phase found is how prose gets broken.
Resolve them here instead:

| Cited in prose below | Current | What it is |
|---|---|---|
| `8f75872a6` | `da34d78a7` | M-P1 — the mark's field, persistence, creation stamps |
| `119bfdaac` | `37667c1bc` | M-P2 — `VizContext` threaded, four sub-gates deleted |
| `be0e3c664` | `6db87680c` | M-P1's null-guard |
| `cd06da9b1` | `f07ea96d2` | M-P3 — enumeration point and Modernize |
| `8ef511e45` | `b2e2d56dc` | M-P4 — server reads follow the mark |
| `4c237a7dd` | `6d8d5da04` | M-P5 — browser reads follow the mark |
| `380705bc1` | `f5f568f12` | L — strip density gating (also in two section headings) |
| `1d26dbefb` | `df3044734` | N — `userTitleHeight`, the flag |
| `d4d0d5d48` | `5d0c7782f` | N — `userTitleHeight`, the stamps |

Hashes from earlier revisions — `8357e05d8`, `952614aa7`, `1c0ace705`, `55c3bad1a`, `07c91926e`,
`307a6ee09`, `a38cb6957`, `e7ef501fb`, `881a9b049`, `ef42a6c65`, `35ca4fce0`, `27ea5fdd5` — are two or three
rewrites dead and are not worth resolving; read them as "some earlier commit". **When this branch is rebased
again, update this one table rather than the prose.**

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
     SHIPPED f5f568f12          NO LONGER BLOCKED · container, calendar
     the interim · L'' HAS NOW REPLACED IT (the density test is deleted)

  N. userTitleHeight flag ──┐
     SHIPPED df3044734      │
     5d0c7782f              ├──→ L'. Title lane height row ──→ L''. Geometric suppression
                            │    SHIPPED 2eb67ba07                SHIPPED · lane >= 24, not density
  M-P4. Read paths ─────────┘    20/26/30 · titleHeight() exists  MUST NOT PRECEDE L'
        SHIPPED b2e2d56dc        resolver reads the mark now      replaces L's density test

     N answers "did the author choose this height" · the MARK answers "is this assembly modern"
     The row needs both. On the flag alone it resizes every dashboard ever saved.
     P1 and P2 landing did NOT free it: the row must READ the mark, and P4 is what makes that read exist.
     L' has been unblocked since P4 — its read path is a server read, P4's subject, not P5's.

  M. Seed mark — SIX phases, ALL SHIPPED, ALL SEEN IN A BROWSER as of 2026-08-25.
     design: ../2026-08-14-seed-mark-forward-half-design.md · decisions 1–13
     ALSO THE RELEASE GATE — and after P6 the gate has exactly ONE item left: BOOKMARKS.
     Decision 13 overruled the sweep (decisions 6+7) on 2026-08-19, so release needs
     P6 + bookmarks rather than an async org-wide sweep with a restore point, scheduler
     blocking and composer-session blocking. P6 is done. Bookmarks is not.

     P1  the field, persisted, stamped at creation      SHIPPED da34d78a7 — nothing reads it
      │
     P2  VizContext threaded, 71 files, ofGate()        SHIPPED 37667c1bc
      │  + all four sub-gate properties deleted         verified behaviour-neutral, suite green
     P3  decision 11's enumeration point + Modernize    SHIPPED f07ea96d2
      │  the only route in for old content              its eleven checks ran inside P5's pass
     P4  server reads follow the mark                   SHIPPED b2e2d56dc · THE BEHAVIOUR REVERSAL
      │  43 read sites + the creation site              unblocked L' · nine manual checks PASSED
     P5  browser reads follow the mark                  SHIPPED 6d8d5da04 · 61 files
      │  resolved modern/dark on the model · 15         4905 core / 1316+356 portal / full
      │  bindings across 7 templates · body class       cross-module build · ALL manual
      │  renamed viz-shell · isVizModern() deleted      checks passed, incl. P3's eleven
      │  ✗ getTooltipStyle NOT done — accepted, R20     P4's interim state is closed
      ├──┬──→ §07 derived selection, retire the teal family
      │  │         └──→ Range slider — painter half
      │  └──→ Outlined text conversion (also behind G)
      │
     P6  Revert — the per-dashboard mirror of         SHIPPED 47521e72c · 4 automated gates
      │  Modernize; deletes the gate && term,         green · TEN MANUAL CHECKS + THE P0
      │  both PlotDescriptor seed booleans AND        COHORT CONFIRMATION ALL PASSED
      │  resolveSeededCorner, all in one commit       2026-08-25
      │
      ├──→ Card radius 12→6 (the constant only)      SHIPPED · no migration and no cohort
      │                                              check needed: branch unreleased and
      │                                              unmerged, so every marked asset is a
      │                                              test asset
      │
      └──→ Palette carried by Revert                 SHIPPED 1b8eb3cea · NOT a planned phase.
                                                     Revert did not carry a chart's colours;
                                                     five defects, each visible only once the
                                                     one before it was fixed. 6 new test
                                                     classes. Four narrower colour defects
                                                     recorded and deliberately unfixed

  BOOKMARKS. Decision 10 — resolve chrome on restore, never rewrite    UNBUILT · THE RELEASE GATE
     Confirmed unbuilt 2026-08-25: TableVSAssembly.parseStateContent (:173-194) parses
     state_tableformat into a fresh FormatInfo and calls setFormatInfo, with no re-resolution
     against the mark; neither TableVSAssembly nor ChartVSAssembly names seedChromeDefaults or
     VizModernizeUtil at all. So a bookmark taken before a Revert silently un-reverts.
     WIDER THAN DECISION 10 COSTED: ChartVSAssembly.writeStateContent also emits <state_info>,
     the whole VSChartInfo (:457-470), which carries the aesthetic refs and therefore the COLOUR
     FRAMES — so a stale bookmark un-reverts the palette too, the same class of defect 1b8eb3cea
     spent five rounds closing everywhere else. The card radius rides in the same
     VSCompositeFormat. Re-derive the property list from the code, not from decision 10's table.

  GATE DEFAULT. viewsheet.modernVisualization ships true            NEW 2026-08-28 · FOLLOWS BOOKMARKS
     The property is unset today, so every read resolves false and modern is opt-in per org. Shipping
     it true is what turns the whole initiative on by default. Four reads resolve it —
     `VSDensityDefaults.isModern` (`:42`), `LookAndFeelService` (`:59`), `PortalController` (`:115`)
     and `CoreLifecycleService` (`:305`) — plus the EM checkbox's own unset state.
     It is a CREATION-TIME switch (VizContext.of(VizMark)'s contract), so flipping it does not
     re-render one existing dashboard: it decides what a NEW assembly is stamped with, and it
     re-opens the composer's Modernize offer everywhere.
     WHY IT FOLLOWS BOOKMARKS: while the gate is off, decision 10's bookmark defect reaches only
     dashboards an author opted in. Default-true makes every newly created dashboard marked, so the
     defect becomes the common path rather than the opt-in one. Do not flip this ahead of it.
     Also re-reads the srinter tooltip at `srinter.properties:4508`, which is written for an
     opt-in switch.

  READ-TIME MIGRATION. Move the substituting resolvers onto seedChromeDefaults   NEW 2026-08-28
     Five resolvers substitute a modern value at render instead of writing it at creation. A read-time
     value is NOT IN THE ASSET, so an export/import into a build older than this work renders the card
     MIXED — modern frame, radius, background, bars and palette (all seeded, all stored), classic title
     lane and legacy 10px inset (both substituted, both absent). That is the defect this item closes.

       VSTitleChromeDefaults.applyModernDefaults / InPlace     18 sites · 9 converted by the title-lane
                                                               spec, the other 9 are this item
       VSCalendarChromeDefaults.applyModernDefaults             4 sites (VSCalendar, VSCalendarModel)
       VSObjectChromeDefaults.applyDarkForeground / InPlace     6 sites (slider, selection list/tree,
                                                               HTML helpers, format painter)
       VSOutputChromeDefaults.applyModernDefaults / InPlace     3 sites (exporter, painter, VSTextModel)
       VSObjectChromeDefaults.chartPadding                      1 site — ChartVSAssemblyInfo.getPadding
                                                               (:2855), the 12px card inset

     NOT IN SCOPE, and the distinction is the whole safety property: VSDensityDefaults.rowHeight /
     titleHeight / mode() must STAY read-time. mode() reads the live org property viewsheet.density
     (:66-69) and the dependency picture is explicit that the mark decides whether an assembly honours
     density, not which density is in force. Seeding a density-derived value freezes it at creation.
     Dark is the opposite and IS seedable: VizContext.of(VizMark) derives it from mark == MODERN_DARK,
     so it is already per-assembly and already stamped at creation.

     The three arguments that produced the read-time family in the first place are all disproved, and
     the disproof is recorded in 2026-08-28-title-lane-unfilled-design.md section 1 rather than here
     because whoever picks this up will reach for them again: a seeded value needs no Revert reverser
     (revert clears the mark and re-runs the same hook); the chart's title format is not in its
     bookmark at all (state_format is the OBJECT format); and the bookmark collision is obsolete since
     the bookmark work made parseState call reseedAfterRestore, which resolves any seeded value on restore BY
     CONSTRUCTION. What each conversion does owe is a LEGACY BRANCH THAT EXACTLY REPRODUCES A GATE-OFF
     CREATION — per type, since the legacy title chrome differs by type.

     ORDERING TRAP, paid once per type: VSAssemblyInfo.setDefaultFormat calls seedChromeDefaults last
     (:1235), but ChartVSAssemblyInfo (:98), SelectionBaseVSAssemblyInfo and TimeSliderVSAssemblyInfo
     each install or overwrite their TITLEPATH composite AFTER super, so the hook seeds a composite
     they then discard. Each needs the hook re-invoked after its install. This is what
     installsOwnTitleFormat() (:1340) exists to work around today.


     ONE SHIPPED ENTRY STATES THE SUPERSEDED RATIONALE. S / §04's row in the Done table reads "Nothing is
     seeded, so clearing the mark reverts by construction — no reverser, no migration, nothing added to
     the bookmark path." That was an accurate description of a real trade at the time and it is why the
     card inset is on the list above. It is not an endorsement to copy: the same three arguments are
     disproved, and what the inset bought with them was a value that does not travel in the asset.

     END STATE: VSTitleChromeDefaults.applyModernDefaults is deleted and all 18 of its call sites drop
     it. Same for the other four. The classes survive as palette suppliers, which is what
     VSObjectChromeDefaults already is.

     P4 unblocked the first of the six — L' — directly, ahead of P5. P5 gated four of the other five. P1 and
     P2 unblocked none of the six, by design; P3 unblocked P4's testability rather than any of the six; P4
     was the first phase whose landing moved one.

     The card radius history, kept because it explains why the item took three revisions to become one line:
     it moved off P5 onto P6 on 2026-08-19 — it never needed the sweep specifically, only A reversal path for
     resolveSeededCorner's retirement, and Revert is one. The 2026-08-20 P6 review then sharpened that:
     retiring resolveSeededCorner was REQUIRED BY P6 rather than unblocked by it, because its gate read
     stranded a marked assembly's card radius the moment the gate && term went. That put the retirement inside
     P6's same-commit set and left only the 12→6 CONSTANT as a follow-on. Landed 2026-08-25 with the
     annotation-rectangle question answered (unrelated, USER tier, left alone) and §01's cohort instruction
     found void rather than satisfied — resolveSeededCorner, the equality test the instruction protects
     against, no longer exists.

  G. Chart type scale ──→ H. Outlined text conversion
     needs one measurement

  S. Card geometry — §04 sizing and spacing            SHIPPED 2afb06bc1 + 2b86a9fa3
     12px card inset · interior gaps 4 / 8 / 14(+2) · hidden-means-zero inheritance
     Audited first, and the audit moved the numbers rather than confirming them: THREE of §04's
     claims are disproved in code, recorded with evidence in
     ./chart-card-geometry-decisions.md. It was costed XL on the premise that the graph engine
     had no spacing scale; three of the four gaps turned out to be tiered CompositeValues that
     GraphGenerator already threaded, so the work was seeding DEFAULT tiers on values that
     existed. Read that doc before costing anything else from §04's numbers.
     LEFT UNDONE, and it is the whole remainder of §04: --inet-chart-line-height at 1.2,
     §04's one type value. Chart text is server-painted, so it is a Java font-metrics change
     with no CSS half whatever the token-shaped name suggests.
     THE COST NOBODY HAS ANSWERED: the plot lost ~28px of width and ~14px of height, where
     §2.1 predicted ~19px. The difference is §04-b's legend return, which does not exist
     (below). Nothing offsets it.

  §05 legend — CLOSED BY DECISION 2026-08-27, not deferred
     Every mechanical, layout-affecting and palette item is already in: label colour
     (LegendDescriptor:73), title colour (LegendsDescriptor:99), dark-aware background
     (:78 / :104), border colour on the CSS tier (CSSChartStyles:113-114), panel round corners
     set at creation (ChartVSAssemblyInfo:92), the categorical palette, the plot-to-legend gap
     with its author option, and rounded swatches with a per-aesthetic-type dialog toggle.
     What §05 still draws is declined or moot: dropping the panel border returns 2px, NOT the
     8-10px §05 claims (THIN_LINE is 1px and getContentBounds subtracts lw*2; padding defaults
     to null, so there is nothing to reclaim) — declined as not worth the change. Dropping the
     fill is invisible either way, LEGEND_BG_DARK equals CARD_BG_DARK and the light fill equals
     CARD_BG, and removing it forfeits 9B's legibility guarantee for a legend over a non-card
     surface. The 1px rule dies with the border: a rule inside a bordered panel is more chrome,
     not less. Square swatches would REVERSE a shipped default. Do not re-open this from §05's
     drawing — the drawing is persuasive and the reasoning is here.

  Ungated: chart interior dark palette · affordance sweep · selection list interior ·
           Resize Plot sliders · nav bar · data-tip registry remainder · chart colour
           literals · drill and DC tips · zoom naming · dead menu icons · title band

  Ungated since P5 shipped: dark (four DOM surfaces). Decision 4 turned viz-dark from a
           body class into a per-assembly scope, and P5 (6d8d5da04) did that, so the
           do-it-twice cost this line used to warn about is paid. CSS only.
```

**The one hard sequencing rule in this picture was L'' must not ship before L'. It was honoured: L' shipped in `2eb67ba07`, L'' after it, and never together.** The reasoning is kept because it is why the order mattered, and because the same trap recurs for anything else that measures a lane. Geometric suppression
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

**Re-derived a second time on 2026-08-25, to place §04 — the card's sizing and spacing model — which had
never been on this list at all.** It enters at #3: it does not gate the release and it is not in flight, so
it sits behind bookmarks and L', but it is much larger than either and it is what most of the external
set's mockups are actually drawing. Nothing else moved, and the reasoning below still holds. The first
re-derivation's note follows.

**Re-derived 2026-08-25, from the dependency picture above rather than by editing the 2026-08-21 ranking in
place — this file's own instructions say to re-derive rather than repair.** Every prior revision's ranking is
gone rather than amended; what follows is one reading of the picture at this commit. Four things changed
since the last one, and the fourth is the reason the table below has a new first row:

- **P6 committed** as `47521e72c`, and **its ten manual checks plus the P0 cohort confirmation have run and
  passed**, confirmed by a human partner on 2026-08-25. M-P6 is therefore *shipped and seen*, not merely
  built — the distinction the 2026-08-21 revision was careful to draw, now closed in the good direction.
- **The palette work committed** as `1b8eb3cea` — five bug-fix rounds after Revert turned out not to carry a
  chart's colours. Not a phase anyone planned; it is what P6's first browser pass found.
- **The card radius shipped** (this commit), which empties the "Decided, unscheduled" section's only
  mark-dependent entry. It needed no migration and no cohort check: the branch is unreleased and unmerged, so
  every marked asset in existence is a test asset, confirmed by a human partner on 2026-08-25.
- **Decision 10 — bookmarks — is the last release-gate item, and it is unbuilt.** The dependency picture has
  said "release needs P6 + bookmarks" since 2026-08-19 and P6 has absorbed all the attention since. It is
  first now by elimination as much as by weight: nothing else is between this branch and a release.

**Amended 2026-08-28, not re-derived — two facts, neither of which changes the ranking's reasoning.**
The file's instruction is to re-derive rather than repair, and this is deliberately not that: nothing in
the picture above moved, so the ranking below still reads correctly and only gains an entry and a status.

- **#1 is IN FLIGHT.** A parallel session is implementing decision 10's bookmark handling — the restore-side
  resolution for Revert and Modernize. Do not start it a second time; check for its branch before picking
  anything off this table. Everything the three notes below say about its surface still stands and is what
  that session is working against.
- **A new item enters at #2: default `viewsheet.modernVisualization` to true.** It is the switch that turns
  the initiative on for everyone rather than per opted-in org, and it is the first item since P6 that changes
  what an untouched customer sees. It sits behind bookmarks for the reason in the dependency picture: the
  gate being off is what currently keeps decision 10's defect to opted-in dashboards, and default-true makes
  every newly created dashboard marked. §04's last item and the dark palette each move down one.
- **And a third enters at #3, added the same day: migrate the read-time resolvers onto
  `seedChromeDefaults`.** It came out of designing the title lane, where the mechanism question turned out
  to decide something larger than the title lane: five resolvers substitute their modern value at render
  rather than writing it at creation, and a value that is only ever computed at render **is not in the
  asset**. Export such a dashboard and import it into a build older than this work and the card arrives
  mixed — everything seeded renders, everything substituted falls back to legacy. §04's last item, the
  dark palette and the cheap items each move down one again.

| # | Item | Impact | Effort | Unblocks | Risk |
|---|---|---|---|---|---|
| 1 | **Decision 10 — resolve chrome on bookmark restore** | the last release-gate item, and a correctness defect rather than a polish one: a bookmark taken before a Revert silently un-reverts the assembly | **M** — four restore points across three classes, not the two this row used to claim: `ChartVSAssembly` writes `state_info` (`:470`), `state_descriptor` (`:479`) and `state_format` (`:488`), `TableVSAssembly` writes `state_tableformat` (`:161`) and `CrosstabVSAssembly` writes `state_crosstabformat` (`:362`). `CalcTableVSAssembly` writes only `state_calctable` (`:503`) and carries no format, so it is out of scope | the release | see the three notes below; the surface is wider than decision 10 was written against |
| 2 | **Default `viewsheet.modernVisualization` to true** | the switch that makes the whole initiative the product's look rather than an opt-in; first item since P6 that changes what an untouched customer sees | **S** in code — four gate reads (`VSDensityDefaults:42`, `LookAndFeelService:59`, `PortalController:115`, `CoreLifecycleService:305`) plus the EM checkbox's unset state and the `srinter.properties:4508` tooltip, which is worded for an opt-in switch. The cost is the decision and the sign-off, not the diff | the rollout as a default | **the highest-risk item on this table, and the risk is not in the code.** Creation-time only, so no existing dashboard re-renders — but every new one is stamped, which promotes decision 10's bookmark defect from an opt-in path to the common one. Must follow #1 |
| 3 | ~~**Migrate the read-time resolvers onto `seedChromeDefaults`**~~ **— the title resolver is DONE for 7 of its 10 titled types; the other four resolvers are untouched** | closes a portability defect, not a polish one: a read-time value is not in the asset, so an export into an older build renders the card mixed — modern frame, radius, background, bars and palette, classic title lane and legacy inset | **was M / 32 call sites across five resolvers.** The title resolver is now converted for the chart and three table types (`f499c0ffa`) and the selection list, tree, container and range slider (this section's note below). **What is left: dark-foreground 6, calendar 4, output 3, chart inset 1, plus the title resolver's last three types — checkbox, radio button, calendar** | deleting `applyModernDefaults` outright | med, and the remaining title types are the hard ones — see below |
| 4 | **§04's last item — `--inet-chart-line-height` at 1.2** | closes §04 completely; the rest of it shipped in `2afb06bc1` + `2b86a9fa3` | **M** — server-painted text, so Java font metrics, no CSS half despite the token-shaped name | closing §04 | low-med. Touches every chart text run, so budget the export pass |
| 5 | **Chart interior dark palette** | the last visible hole in a dark mode that is otherwise complete | **M-L**, design first — `GDefaults` has no dark branch to reconcile against | nothing | med |
| 6 | The ungated cheap items | low each, additive | **S** each | nothing | none |

**AMENDED 2026-08-31 — this ranking is stale in three rows and needs re-deriving, not repairing. Three things shipped after it was written:**

- **#1, decision 10 / bookmarks, is DONE** — `f47c59304`, "resolve an assembly's chrome when its state is restored". The re-seed goes at `AbstractVSAssembly.parseState`, the chokepoint every restore passes through. It went wider than decision 10 costed: `dimensionColors` preservation, provenance flags so an author's bar-corner-radius and smooth-lines survive the seed, and capturing the live mark across the parse so a calc table's serialized info cannot reinstall a stale one. **The release gate this file has cited since 2026-08-19 is therefore empty.**
- **#2, `viewsheet.modernVisualization` default-true, is DONE** — `c7790bbf0`, two lines in `defaults.properties`. **Density shipped at `compact`, not dense**, because dense's 20px lane cannot hold the 24px anchored strip and L″ draws no chrome at all below that threshold — so dense would have shipped the modern look with no toolbar and no kebab on charts, tables, crosstabs, calc tables and both selection types. **Consequence for everything below: "unset" no longer means legacy.** A test or a gate-off check must now set the property to `"false"` explicitly.
- **#3's title resolver is 7 of 10 types done.** `f499c0ffa` + `bee8d4169` converted the chart and the three table types to an unfilled lane with a `#D9D5CC` bottom rule; the selection list, tree, container and range slider followed. **The three that remain are the hard ones and need their own design**: checkbox, radio button and calendar are all on `bypassesBaseChrome()`, where the hook returns before writing anything, so converting them means deciding whether that predicate splits into a bypassable object-chrome half and an always-run title half — a change to the hook's contract rather than an application of it. The calendar additionally has never run the hook at all (`initDefaultFormat` never calls `setDefaultFormat`) and `AbstractVSExporter:2716-2721` would box its bottom-only rule back to four sides. Full record, including a bookmark-restore defect for selection-container children that the work found and fixed, and a recommended hoist, in [the selection family design](./2026-08-31-selection-family-title-lane-design.md) §9.

**Two things that shipped with the selection family and are worth carrying forward, because both cost a review round to find.** `FormatInfo.getFormat(TITLEPATH, false)` is **not a read** — it mutates the stored DEFAULT tier via `copyDefaultFormat`, and a seeded value survives only because its setter sets the matching `*ValDefined` flag. And a signature change reached `utils/inetsoft-xml-formats` for the **second** time on this branch (`ExportUtil.getBackGroundColor` → `PPTVSExporter`, after `getValueFormat` did the same); widen rather than overload so a missed caller is a compile error.

**Re-derived a third time on 2026-08-25, after L′ shipped in this commit.** L′ comes off the table because it is built, reviewed and seen in a browser — nine included assembly types checked at three densities, the three excluded types confirmed unmoved, export agreement across PDF, PNG and Excel, and the composer round-trip for the new checkbox. It is the last of the six items the seed mark existed to free, so **the mark has now paid for all six**. §04 and the cheap items each move up one; nothing else changed, and bookmarks stays first because it is still the only thing between this branch and a release.

**What L′ unblocked, and what became of it.** L″ has since shipped: the density-keyed strip test is deleted and the predicate compares a real lane against **24px** (26 at first, corrected the same day — see the header). The reasoning for the order is kept because it explains the trap. Until L′ landed every assembly carried a 20px lane at every density, so the test would have failed everywhere and stripped the toolbar from the whole anchored set. **The sequencing rule in [the strip and lane decisions](./chart-card-anchored-strip-lane-decisions.md) decision 3 is one-directional: L″ must follow L′, never accompany it.** It did not, and it must not be folded into a follow-up that also touches the lane. L′ also retires the interim's approximation rather than the interim itself: `f5f568f12` keyed suppression off density as a stand-in for a 26px lane, and at compact the lane now actually is 26.

**Decision 10 is confirmed unbuilt, not merely unrecorded.** `TableVSAssembly.parseStateContent` (`:173-194`)
parses `state_tableformat` into a fresh `FormatInfo` and calls `setFormatInfo` — no re-resolution against the
mark anywhere in the method, and neither `TableVSAssembly` nor `ChartVSAssembly` mentions
`seedChromeDefaults` or `VizModernizeUtil` at all. So the defect decision 10 describes is live: restore a
pre-Revert bookmark and the table's chrome comes back modern on an unmarked assembly.

**And its surface is wider than decision 10 costed, because of what shipped after it was written.** Decision
10 enumerates `barCornerRadius`, `smoothLines`, the two seed booleans and `roundCorners` from
`<state_descriptor>`, plus the chart's `VSCompositeFormat` from `<state_format>`. Two additions:
`ChartVSAssembly.writeStateContent` also emits **`<state_info>`**, the whole `VSChartInfo`
(`ChartVSAssembly.java:457-470`), which carries the aesthetic refs and therefore **the colour frames** — so a
stale bookmark un-reverts the *palette* as well, the exact class of defect `1b8eb3cea` spent five rounds
closing on every other path. And the card radius now sits in the same `VSCompositeFormat` the bookmark
restores. Whoever takes this should re-derive the property list from the code rather than from decision 10's
table.

**Two things decision 10 already settled that are worth not re-litigating.** The mark is *not* in the
bookmark — `AbstractVSAssembly.writeState` emits only class, name and `writeStateContent`, while the mark
lives in `writeAttributes`, which is asset XML — so restore can replace the formats while the assembly's
correct mark stays put, and the decision-11 path recomputes against it. And USER-tier entries are *not*
cleared on restore, because after a Revert clears the mark, "unmarked" cannot tell *never modern* from
*reverted*, and clearing would strip a designer's deliberate value from a dashboard that was never modern.

**M-P6's manual checks are off this table because they have run and passed** — including the four that no
prior pass had exercised: a marked assembly in a gate-off org keeping card radius and bar corners, density
applying there, the EM's unhidden density control, and the confirmation that legacy tooltip chrome and the
absent inline-SVG animation are the *only* two surfaces staying legacy. That last one closes the two accepted
costs negatively, as P6's plan intended. **P3's eleven were covered by P5's pass**, per the dependency
picture; the prose further down this file that still says they are outstanding predates that and is wrong.

**The card radius is off this table because it shipped.** What it needed was not the cohort check the v3
spec's section 01 asks for — that instruction died with `resolveSeededCorner` in P6 — but a sign-off on the
value, which it has. See [the corrections doc](./chart-card-source-doc-corrections.md) section 3.4, closed in
the same commit.

**L-prime has not moved for any reason of its own.** `VSDensityDefaults.titleHeight()` still resolves
defh/26/30 and is still uncalled by the resolver itself; `userTitleHeight` still tells an author's height from
a default one; P4 still supplies the read path. It is second rather than first only because #1 stands between
this branch and a release and L-prime does not. If the release is not imminent, swap them — the argument for
L-prime first is that it is the item the whole mark investment exists to free, and it is a fair argument.

**The seed mark is complete.** All six phases are shipped, committed and — as of 2026-08-25 — seen in a
browser. What used to be "M, XL, six items" is closed, and the five items it gated are ordinary work with no
phase in front of them. The one thing it left behind is the bookmark path, which was never one of the six.

**Two gate reads survive by decision, not by omission**, and they are confirmed to be the only two:
`AbstractChartInfo.getTooltipStyle` and `VSChartInteractionDefaults.isInlineSvg()`. Each makes a marked chart
in a gate-off org modern everywhere except one surface — legacy tooltip chrome, and no inline-SVG animation or
hover dimming. Threading the tooltip means widening five `ChartArea` constructor overloads reached by the
report painter, the exporter, annotations and the scheduler, which is larger than the whole of P6;
`isInlineSvg()` is interaction rather than chrome and carries a `graph.svg.inline` override, which is the
workaround for release notes.

**Four colour defects are recorded and deliberately unfixed**, all found while closing `1b8eb3cea` and all
narrower than what it fixed: the target-line band fill pins all three colour tiers on every apply (separate
code path, pre-existing); toggling multi-styles off and back on around a Revert can strand modern colours on
the per-measure frames; `applyGlobalColors` writes a derived colour back without its provenance mark; and the
value axis's runtime clone (`VGraphPair:1338-1341`) is intentionally still gated. They live in
[the forward-half design](../2026-08-14-seed-mark-forward-half-design.md)'s dated 2026-08-24 block. Do not
report them as new.

---

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
the bound; the direct comparison is L'', which has since shipped. Do not read `380705bc1` as having
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

**P6 built 2026-08-21 — all four deletions above landed together, plus Revert, plus `resolveSeededCorner`'s
retirement.** Eight tasks, and every one of them is **uncommitted**: the same commit-approval gate that
refused P3's and P4's task commits (before a human partner committed those two outside the building session)
refused all eight of P6's, including the documentation commit this entry is part of. **Task 8 ran the four
automated gates and all four are clean:** `core` — 4904 tests, 0 failures, 0 errors, 67 skipped (down from
P5's 4905 by exactly the test surface Task 6 deleted along with the mechanisms it covered — a lower total is
the expected shape, not a sign of anything skipped); `portal` — 217 files, 1329/1329 passing (up from P5's
1316, the new unit tests this phase added); `em` — 101 files, 356/356 passing (unchanged from P5); the
cross-module `./mvnw.cmd install -DskipTests "-Pcommunity,enterprise"` build — BUILD SUCCESS, 3:55 min, no
reactor failures. **None of that is the same claim as "verified."** P6's ten manual checks — a marked
dashboard in a gate-off org rendering fully modern including card radius and bar corners; Modernize-then-
Revert byte-comparable with never-modernized; Undo after Revert; a mixed dashboard reverting only its marked
assemblies; a host revert not touching an embedded sheet's own assemblies; Revert offered with the gate off
and refused without write permission; export agreement across PDF/PNG/Excel for both a reverted and a
gate-off-marked dashboard; density behaviour in a gate-off org including the anchored mini-toolbar and a
legacy-dashboard control; org-level surfaces taking the org density; and confirming the two accepted costs
(legacy tooltip chrome, no inline-SVG animation) are the *only* two surfaces that stay legacy — **have not
run.** Nor has the P0 prerequisite: confirming no pre-mark-cohort dashboard survives on this branch, or
building fresh ones for the checks to run against. Both need a built server, a browser and exported files,
none of which a shell can produce, and neither was attempted here. Treat P6 as code-reviewed and
automated-test-clean, not as seen working, until they run — see "Carried into P6" below for the full list
and its own P0 note.

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

## §04, the card's sizing and spacing — SHIPPED, and it was not XL

**Shipped 2026-08-27 in `2afb06bc1` (the card inset and the gap scale) and `2b86a9fa3` (a custom axis
title's gap, and an author's zero legend gap). The decisions, the audit and the evidence are in
[chart-card-geometry-decisions.md](./chart-card-geometry-decisions.md) — read that, not the section
below, before touching anything §04 describes.** What remains of §04 is one item:
`--inet-chart-line-height` at 1.2.

**The estimate was wrong by roughly an order of magnitude, and the reason generalises.** This section
costed §04 as XL on the premise that binding its gaps meant "introducing a spacing scale where none
exists, in the one subsystem CSS cannot reach," citing eight private gap constants in the graph engine.
Three of the four gaps turned out to be **tiered `CompositeValue`s that `GraphGenerator` already
threaded, in a class that already held a `VizContext`** — `AxisDescriptor.labelGap`,
`TitleDescriptor.labelGap`, `LegendsDescriptor.gap`. The work was seeding DEFAULT tiers on values that
existed. The eight constants were mostly a red herring: only `VGraph.GAP` mattered, and it was left
alone. **Before costing anything else from this external set, check whether the value already has a
tier — the set describes its own drawings, not this codebase.**

**Four of §04's claims are disproved in code.** Each is recorded with its evidence in the decisions doc:

| §04 says | The code says |
|---|---|
| the card inset is new geometry, and the graph's own outer margin is the assembly's `padding` | `padding` **already is** the card inset, with the title lane inside it — so this half was a constant change, 10 → 12, not a restructure |
| zero the graph's own outer margin | **there is no such margin.** Confirmed at render: the band §04 measured was that same `padding`, seen from inside an exported SVG, which `SVGVSExporter:145-149` sizes as `vgraph.getBounds()` **plus** the padding |
| dropping the legend panel border and fill returns 8–10px of horizontal room | **~2px.** `THIN_LINE` is 1px and `getContentBounds` subtracts `lw * 2`; the fill is invisible (`LEGEND_BG_DARK` = `CARD_BG_DARK`); `padding` defaults to null |
| the interior gaps need a new spacing scale in the engine | three of four were already tiered; see above |

**The cost nobody has answered.** §2.1 of the decisions doc predicted ~19px of plot width, netting out
§04-b's legend return. That return does not exist, so what shipped is **left +12, right +16 — about
+28px of width and +14px of height off the plot.** Nothing offsets it. `Legend.BAND_GAP = 10` looks like
a candidate at 20px total and is not one: `bandW = w - BAND_GAP * 2` insets the band *within* the
legend's own width, so shrinking it widens the band rather than returning anything. If plot area
matters, the only real lever is re-examining whether 4 / 8 / 16 are right for this codebase — they come
from the same document, measured against the same mockups, as the four claims above.

**Everything below this line predates the audit and is kept only as the record of what was believed
before it. It is wrong in the four ways tabled above. Do not act on it.**

**What §04 specifies.** Five nested boxes and only one of them elastic: axis label bands, axis title bands
and the legend column size to their own content; the title lane takes the greater of its type box and the
toolbar strip; the plot is whatever is left. One 12px card inset governs all four edges, and no nested region
adds edge padding of its own inside it. Interior gaps are 4px (axis title → labels), 8px (labels → plot) and
16px (plot → legend), and a hidden element contributes no height, no width and no gap — the chain closes up
rather than leaving an empty stub.

**The one source edit it names, and where it lands.** §04 asks that the graph's own outer margin go to zero so
the card's 12px is the only edge inset, on the argument that the two stack today and the axis sides therefore
read looser than the title side, which sits inside the lane and escapes the doubling. In code that margin is
the chart assembly's padding: `ChartVSAssemblyInfo.java:88` seeds `new Insets(10, 10, 10, 10)` inside
`setDefaultFormat`, **unconditionally** — it is one of the creation defaults P3 deliberately left outside
`seedChromeDefaults`, so it is not gate-dependent and neither Modernize nor Revert touches it. It persists as
four XML attributes on `VSAssemblyInfo` (`:867-871` write, `:913-917` parse) and is consumed by `VGraphPair`
at `:282-285` and `:2946`. And there is no card inset on the server side at all today. So this is not a
retokenize: it is new geometry, it is persisted, and making it modern-only means moving it into
`seedChromeDefaults`, which adds a fifth seeded value for Revert to carry and puts it under
[seeded-value reversibility](./seeded-value-reversibility-decisions.md).

**Why the interior gaps are the expensive part.** §04's instruction is "bind to the shipped tokens, don't
create new ones", and on the browser side that is already true — `--inet-space-2/4/5/6` resolve to
4 / 8 / 12 / 16 today (`_variables.scss:559-563`). The graph engine has no such scale. It has at least eight
independent private gap constants with no shared vocabulary and no gate awareness: `Legend.GAP = 4` and
`Legend.BAND_GAP = 10` (`:1213-1214`), `LegendItem.GAP = 5` (`:309`), `LegendGroup.GAP = 2` (`:422`),
`VGraph.GAP = 2` (`:1916`), `VisualObject.TEXT_GAP = 2` (`:36`), `RelationCoord.GAP = 4` (`:285`) and
`GDefaults.TICK_MIN_GAP = 4` (`:109`). Binding §04 on the server is therefore not a sweep over existing
tokens; it is introducing a spacing scale where none exists, in the one subsystem CSS cannot reach. Same
shape as the slider whose appearance is defined twice.

**The legend change carries its own warning, in the spec's own voice.** Dropping the legend panel's border
and fill is "the one change with layout consequences" — it returns roughly 8–10px of horizontal room, so the
plot's right edge and the legend column have to be re-measured at real render sizes rather than at the
mockups' 1100×620. Legend padding is a separate path again: `LegendsDescriptor.getPadding()`, applied at
`GraphGenerator.java:1697`, `:1776` and `:1811`.

**One slice of §04 is already in flight.** Its title-lane-height rule — the lane being the greater of the
type box and the strip — is the thing §04 itself flags as *not what shipped*, and it is what L' is
reconciling. Do not build it twice.

**Two things must come first.**

1. **An audit**, on the corrections doc's own pattern. Precedent says it will move the numbers rather than
   confirm them: the L' design found three corrections in the documents it implements, and a read surface of
   111 sites where the source had costed eleven.
2. **A measurement pass at render, not in a mock.** §04 says this itself, and the reason is that every mock
   in the document draws the intended result, so none of them can show the defect. The tell is a plot with
   too much air on the axis sides and comparatively little under the title.

Then three or four sequenced commits: the card inset with the zeroed outer margin; the legend panel with its
re-measurement; the interior gap scale; and an export parity pass on P6's scale, because every value here is
server-rendered and shows up in PDF, PNG and Excel.

**The risk, stated plainly: this reflows existing charts.** It must be mark-conditioned or it changes every
saved dashboard on next open, which is the exact thing the seed mark was built to prevent. It is buildable
now only because P4 supplied the read path — the same reason L' became startable.

---

## Ready now

Nothing below is blocked, with one exception kept in place rather than moved: **§10.1 is blocked on a
decision**, recorded in its own row. It stays here because it was never blocked on sequencing and its
note is where the reasoning lives; do not read the heading as clearing it.

| Item | Source | Note |
|---|---|---|
| **§10.1 — resting visibility by pointer capability** (touch draws the kebab at rest; a pointer device leaves the lane empty until hover or focus, then fades the kebab in by opacity while the action groups leave layout) | `Chart Card Spec v3.dc.html` §10.1 and §02 | **S, browser-only, added 2026-08-25 while scoping L″.** Today `isAnchoredResident` decides resting from **density** (`mini-toolbar.service.ts:74`), which conflates two orthogonal questions: *does the lane hold the strip* (geometry — L″ answers this) and *is it drawn at rest* (pointer capability — this item). L″ deliberately leaves the resting semantics alone so the two do not tangle, which means after L″ the predicate still derives resting from geometry rather than from the pointer. The kebab must hide by `opacity`, never `display: none` or `visibility: hidden`, so a transparent button keeps its tab order and accessible name — that is what preserves the keyboard route. The three action groups use `display` instead, deliberately: the pill is `width: fit-content` right-aligned by an auto margin, so transparent-but-in-layout buttons would leave a wide empty pill at rest. Modifies already-shipped slice-3 behaviour; see `github.md`'s in-project decision. **BLOCKED ON A DECISION, not on sequencing.** `github.md`'s 2026-08-12 entry: the pointer query would leave every kebab-only family with no chrome at all at rest on a desktop, which is not what `kebabOnly` was approved for. Three of the eight anchored types are kebab-only now that the container has joined them, so the question is larger than when it was written. Needs its own design |
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

**Rollout slices 4 and 5 have SHIPPED** — the container and the calendar. `ANCHORED_ASSEMBLY_TYPES`
is no longer a rollout boundary; it is the permanent anchored set, and it differs from
`hasMiniToolbar()` by exactly one entry, the adhoc range slider.

**Its promised deletion is unsafe and must not be carried out.** The comment used to say the last
slice would delete the predicate, leaving the `.viz-modern` gate as the only condition. Do that and
`isAnchoredChromeSuppressed` becomes true for every laneless assembly under the gate — they all
resolve to a zero lane — and the composer's mobile toolbar, which renders `showingActions` for
whatever assembly is focused, goes blank for text, gauge, image and spinner. The reasoning is now in
the code as well as here.

**Not this — L'' waits for L'.** The geometric suppression that replaces the density test is decided but
must not land until the lane row does, or the strip vanishes from every assembly carrying the default
title height. See the sequencing rule under the dependency picture. When it does land it also retires
`rightEdgeReserve()` and `SORT_CONTROL_RESERVE` (`vs-object-container.component.ts:588-603`), because a
hidden title suppresses the strip rather than overlaying it — a reversal of the slice-2 and slice-3
title-hidden decisions, costed in
[the strip and lane decisions](./chart-card-anchored-strip-lane-decisions.md) decision 4.

## Decided, unscheduled

**The chart selection fill needed no dark treatment — closed by measurement 2026-08-27, not deferred.**
[Decision 2](./chart-card-open-item-decisions.md) §2 listed it as one of the four dark surfaces and flagged
its alpha as "one nuance, not a blocker": `--inet-focus-ring-color` is the primary at 28%, and the decision
reasoned that 28% "was calibrated as a wash over a *light* plot", so over `#252428` "the same wash is much
weaker." That is the opposite of what it measures. Composited over the two card backgrounds the plot
actually sits on and compared with the unwashed plot by WCAG relative luminance: over `CARD_BG #FFFFFF` the
wash lands at `rgb(248,222,195)`, **1.29:1**, with the stroke at **2.62:1**; over `CARD_BG_DARK #252428` it
lands at `rgb(91,65,41)`, **1.63:1**, with the stroke at **5.88:1**. The wash reads *more* strongly against
the dark plot and the stroke more than doubles, because the primary is a mid-tone — over white it can only
darken slightly, over near-black it lightens a long way. So `.chart-object-canvas` keeps its single
`.viz-modern` rule (`_themeable.scss:1442-1445`), the fill stays the brand accent it is meant to be in both
modes, and no dark alpha, token or rule was added. Do not reopen this as an unexamined item.

**The title band becomes unfilled — DESIGNED 2026-08-28, and the design reversed its own mechanism.**
The scope, the values and the call-site split are in
[2026-08-28-title-lane-unfilled-design.md](./2026-08-28-title-lane-unfilled-design.md); read it before
costing this from §1's text. Three things §1 did not have. **The rule does not exist yet** —
`titleBorderColor()` has no production caller and a modern chart's title format carries no borders value,
so this introduces the hairline rather than swapping the fill for it; a chart's `#F1EFEA` comes entirely
from `applyModernDefaults`, so its fill needs no clearing at all once it stops calling it. **The treatment
is scoped to the chart and the three table types**, with the selection and input family following, and for
tables only the border WIDTHS and the fill move — the colour is already `#D9D5CC` via
`seedChromeDefaults`. **And the value is SEEDED AT CREATION, not substituted at read time** — the first
revision chose read-time and was wrong on all three of its reasons; the disproof is in that file's §1 and
is what produced the read-time migration item above. Still no dependency on the seed mark, still
export-affecting, and it still breaks the title-bar/table-header equality the sibling project's §05
endorses — show it to them before it merges.

**The card radius dropped 12px to 6px — SHIPPED 2026-08-25, so this entry is closed.**
`CARD_CORNER_RADIUS` is now 6 (`VSObjectChromeDefaults.java:107`), matching `--inet-radius-xl`
(`_variables.scss:584`), the top of the DOM radius scale. Two notes for the record, because this entry
accreted three revisions of caveats and only one of them turned out to be real. The **cohort caveat was
void, not satisfied**: it protected against `resolveSeededCorner`, whose exact-equality test would have
stopped stripping already-seeded assets the moment the constant moved, and P6 deleted that method. And
the **annotation rectangle was the one travelling item that needed an answer** — the spec asked whether
12px was shared and never read the value. It is not shared structurally: `VSAnnotationService:54` writes
`setRoundCornerValue(12)` into an annotation rectangle's USER tier at creation, a different assembly
type, not gate-dependent, untouched by Modernize or Revert. The annotation keeps 12px and the constant
comment that asserted the equality now cites the DOM token. Full disposition in [the corrections
doc](./chart-card-source-doc-corrections.md) section 3.4.

---

## Done

**Hashes re-resolved 2026-08-25, after a THIRD rebase — and this is the important part: every hash the
twelfth revision carried was stale, including the ones it had itself re-resolved on 2026-08-14 after the
second rebase.** Checked with `git merge-base --is-ancestor <hash> HEAD`: `8f75872a6`, `119bfdaac`,
`be0e3c664`, `cd06da9b1`, `8ef511e45`, `4c237a7dd`, `380705bc1`, `1d26dbefb` and `d4d0d5d48` are **none of
them** reachable from `HEAD` any more. They still resolve under `git show`, which is exactly why that command
must not be used to check them. The table below was re-derived by matching commit *subjects*, not by trusting
any prior column — subjects survive a rebase and hashes do not. **Do not add a "cited at revision N" column
again**: three rebases in, a history of dead hashes is a history of things a reader may not use, and it grew
this file for no benefit. Cite by subject and re-resolve.

| Item | Commit |
|---|---|
| Phase 9B dark mode — every server-rendered surface, chart included | `38c36aba1` |
| Inline-SVG chart rendering coupled to the modern gate | `e39516e40` |
| Data-mark-anchored tooltip tail | `4077f9099` |
| Shell tooltip retokenize + CARD ramp + data-tip layer declaration | `cc2948231` |
| Selection vocabulary — chart-owned surfaces and the annotation border | `9dbc9857e` |
| Menu-action reachability, the §06 ladder, and rollout slice 1 — chart | `5c7e106f2` |
| Right-click reaches max mode on tables | `2736113df` |
| Rollout slice 2 — table, crosstab, calc table | `39239784b` |
| Max-mode mini-toolbar positioning fix | `9881350c1` |
| Rollout slice 3 — selection list and tree, kebab-only at any width | `65e249e91` |
| Strip suppression where the lane cannot hold it — L, density-approximated | `f5f568f12` |
| `userTitleHeight` — N, the flag and its per-type default | `df3044734` |
| `userTitleHeight` — N, the thirteen stamps, the propagate and the reset un-stamp | `5d0c7782f` |
| **M-P1 — the seed mark's field, persistence and creation stamps** | `da34d78a7` |
| **M-P2 — `VizContext` threaded, the six `isModern()` predicates and all four sub-gates deleted** | `37667c1bc` |
| M-P1's null-guard — a mocked host viewsheet with no assembly info | `6db87680c` |
| **M-P3 — the enumeration point, `seedChromeDefaults`'s bypass predicates, `VizModernizeUtil`, the endpoint and the composer bar** | `f07ea96d2` |
| **M-P4 — the 43 read-path sites and the creation site flipped from `ofGate()` to the mark, one documented survivor left** | `b2e2d56dc` |
| **M-P5 — browser reads follow the mark; 61 files, the body class renamed `viz-shell`, `isVizModern()` deleted** | `6d8d5da04` |
| The modern strip no longer draws controls that do nothing | `f4e4c4498` |
| **M-P6 — Revert (the per-dashboard mirror of Modernize), plus the `gate &&` term in `VizContext.of(VizMark)`, both `PlotDescriptor` seed booleans and `VSObjectChromeDefaults.resolveSeededCorner` all deleted in one commit** | `47521e72c` |
| **Revert carries a chart's colours with the assembly** — the palette seeded in `seedChromeDefaults`, derived per-value colours marked and dropped, the two sheet-level colour caches cleared, the binding pane's Apply comparing against the reported tier state, and the value axis's per-column label gate removed. Five bug-fix rounds, six new test classes | `1b8eb3cea` |
| **Card radius 12 → 6** — `CARD_CORNER_RADIUS` onto `--inet-radius-xl`, the top of the DOM radius scale | `f69751842` |
| **L′ — the title lane height row**, plus the follow-the-default-density affordance for title and selection cell height. Five per-type `getTitleHeight()` delegations consult a mark-gated resolver, so all 111 read sites follow untouched, 46 of them painters; `TitleInfo` keeps its own getter, serialization and equality, which is what makes the row reversible. `getDefaultTitleHeight` renamed `getLegacyTitleHeight` so the calendar’s 36 is not later “corrected” into the row. Eight dialog services read and write the checkbox and their inference guards are deleted; a missing flag means no opinion, so unmarked content and stale clients keep the old comparison behaviour | *this commit* |
| **L″ — geometric suppression of the anchored strip.** The two predicates stop reading the org density and take the assembly's own lane: `lane >= 24` draws the strip, below it draws no chrome, and a hidden title yields lane 0 so one rule covers both. `anchoredLaneHeight` rounds, because the composer's drag path feeds `getBoundingClientRect()` heights in. Deletes `GuiTool.isVizDensityAtLeastCompact`, `rightEdgeReserve` and `SORT_CONTROL_RESERVE` — title-hidden tables gain 22px of plot. Threshold shipped at 26, corrected to 24 the same day. Browser-only: no persisted state, no export pass | *this commit* |
| **§10.2 — the seamless in-lane strip and its derived glyph tone.** The anchored strip's surface is gone: `.mini-toolbar--anchored .mini-toolbar-container` overrides the unconditional fill, border and radius from `mini-toolbar.component.scss:25-33` to transparent/none/0, so the white bordered box §10.2 named no longer draws on the anchored path. Each glyph and its button take one of two inks — black or white — chosen by measured contrast against the resolved background (the title lane, falling back through the card, the optional viewsheet canvas, then `vizDark`), computed once per background change in the new pure `resolveStripGlyphTone` (`strip-glyph-tone.ts`) and delivered from `vs-object-container` as `data-tone` plus `--viz-strip-glyph-a`. Hover and the focus ring derive from that alpha in CSS; the resting kebab's `opacity: 0.55` dimmer is gone, replaced by the tone's own resting-to-hover step. **Diverges from the source spec on the tone rule.** §10.2's `L > 0.45` luminance threshold is not what shipped — measured, it selects the *lower*-contrast ink across a wide band of author colours, including 1.86:1 on the swatch the spec itself flags as the one to check first. The resolver instead carries no threshold constant: it measures both candidate inks at their base alphas, takes whichever reads higher, and lifts only that ink's alpha until it clears WCAG 1.4.11's 3:1 floor. The scrim §10.2 prescribes for a glyph overlaying the plot also does not ship — measured, it reduces contrast at every alpha, and post-L″ its title-hidden case does not occur on the anchored path. Full record of this and two smaller corrections (the `<img>` precondition, the hover-background framing) in [the corrections doc](./chart-card-source-doc-corrections.md) §1.5–§1.8, and the shipped design in [chart-card-seamless-strip-design.md](./chart-card-seamless-strip-design.md) | `ffae8a1dd` — landed as one commit rather than the five intended subjects |
| **Dark — the browser-DOM surfaces**, re-scoped against `ffae8a1dd` before building. Five `--inet-viz-*-dark` tokens in the existing dark block carry the neutrals the server-side resolvers already use, so a painted chart and the chrome around it hold one dark palette; a fifth, caption-tier value (`#938F99`, 4.87:1 on the surface) is new, because the painted chrome needs only two text tiers and the CARD tooltip binds three. Three surfaces take them: the **floating** strip (`mini-toolbar.component.scss`), every **tooltip** plus its SVG tail (`_directives.scss`, `tooltip.component.scss`), and the **nav bar** (`chart-nav-bar.component.scss`, which also brings its off-scale 5px corner onto `--inet-radius-xl` under the modern gate). **Four of the plan's own premises were false and are corrected rather than carried:** its Task 1 targeted the *anchored* strip, which `ffae8a1dd` had already left surfaceless — the light pill that survives is the floating one, on the types in `hasMiniToolbar()` but not `ANCHORED_ASSEMBLY_TYPES` (as written, calendar, selection container and adhoc-filter range slider; since slices 4 and 5 landed, the adhoc-filter range slider alone); its tooltip scope class was `.viz-dark`, the per-assembly wrapper, which can never match a body-level overlay — the gate there is `.viz-shell`, so the dark scope is `.viz-shell-dark`; its CARD-ramp override was one foreground, now three; and its Task 3 is closed as a **no-op by measurement** (see below). The nav-bar glyphs needed the ink set on the `<i>`, not the button: they extend `.ineticons`, which declares its own `color`, and the icon token's `#6A685F` measures **2.76:1** on the dark bar and **2.07:1** pressed — both under WCAG 1.4.11's 3:1 floor. The same correction was applied to the floating strip. The pan toggle's pressed fill is a neutral raise, not the modern selection teal: light reads that state as a 1.12 step off its own surface, and a toggle being on is a pressed state rather than a selection**The browser pass then found two defects and both are fixed here.** (1) Tooltips were scoped to `.viz-shell-dark` and had to be re-scoped per assembly: a tooltip is reparented to the body, so it has no assembly ancestor, and the body class follows the live org `viewsheet.darkMode` while an assembly follows its mark — observed drawing a light tooltip on a `MODERN_DARK` chart in a light org, and a dark tooltip on a `MODERN_LIGHT` chart in a dark org. `TooltipDirective.resolveDark()` now reads `closest(".viz-modern, .viz-dark")` from the hovered element and stamps `widget__tooltip--dark` / `tooltip-chrome--dark`, falling back to the shell only where there is no wrapper. This cashes in the R17/R21 deferral on its own stated trigger — "revisit only if a mixed dashboard makes it visible" — for the palette only; the scrim and the data-tip offsets stay org-scoped, because a 4px offset is the inventing-precision case and a palette is not. The five `--inet-viz-*-dark` tokens also moved to bare `:root`, since a body-level tooltip sits outside every dark scope and could not resolve them. (2) Selection list and tree **cell text** rendered near-black on the dark surface — `SelectionBaseVSAssemblyInfo.setDefaultFormat` hardcodes `0x2b2b2b` on the DEFAULT tier, an unconditional creation default that `seedChromeDefaults` is documented never to touch, delivered to the browser as an inline style so no stylesheet could reach it. Fixed in `SelectionListModel.getFormats()` via `applyDarkForeground`, the 9B mechanism that existed for exactly this and was wired to only one surface, the slider; one site covers list and tree both. **Not a regression from this work** — a pre-existing 9B gap. **Export parity was then corrected too, and the title is the precedent for why one read point is not enough.** What makes `VSTitleChromeDefaults` right is not the clone-at-read-time shape but that it runs at *every* read point, `FormatPainterService` included — miss that and the composer's format pane shows the stored near-black while the canvas, the viewer and the export all draw light text, a design-time WYSIWYG break. The substitution now runs at five: the browser model; `VSSelectionListHelper.getValueFormat` (given a `VizContext`), the chokepoint PDF, SVG/PNG and the tree all route through; both HTML helpers, which bypass it and emit CSS from `svalue.getFormat()`; `VsToReportConverter` on the DETAIL path; and the composer picker via a new `applyDarkForegroundInPlace`. Two load-bearing details: the substitution runs *before* `getValueFormat`'s dimming, which writes the USER tier and must keep winning so an excluded value stays grey; and the tree helper passes `sv.getFormat()` uncloned, so the clone-returning variant is required there and the in-place one must never touch a format reached from a `FormatInfo`. A code review after the manual passes then corrected four things, three of them defects it would have shipped: the `getValueFormat` signature change left three callers in `utils/inetsoft-xml-formats` (Excel list, Excel tree, PPT) stale — a build break that a 77-module incremental `install` reported as SUCCESS and only `clean` exposed, so verify a cross-module signature change with `clean`; the print-layout substitution is **reverted**, being the one renderer that does not paint the dark page (`setUserBackground` fires only when the background differs from the DEFAULT tier, which is where the dark page colour is written, so the report stays white and light ink is *less* readable); the composer branch also caught Measure Text / Measure Bar / Measure Bar(-), which share the DETAIL type and whose foreground *is* the bar colour, now excluded by testing for an empty path array rather than the incomplete `isMeasureTextBar`; and the tooltip's `viz-shell-dark` fallback is gone, since that class paints no surface and the shell's own surfaces stay light. Excel is deliberately left on the legacy ink: unfilled white cells, no page to paint, so the light neutral would be invisible. Clean 77-module build, core 5123 and portal 1375 green | **No commit yet.** Complete in the working tree, gates green: portal 1369/1369, the mini-toolbar TL suite 24/24 (two new, and the exclusion assertion was confirmed to fail when the `:not(.mini-toolbar--anchored)` guard is removed), and `styles.scss` compiles. **Not yet seen in a browser** — the visual pass across the three surfaces is the outstanding gate. Three intended subjects: `feat(vsobjects): darken the floating mini-toolbar strip`; `feat(shell): darken the tooltip surfaces under the dark gate`; `feat(chart): darken the chart nav bar and put its corner on the radius scale` |
| **Dark — the browser-DOM surfaces** — the row above committed as `46e8f6b5a`, and its browser pass ran (it found the two defects the row records, both fixed before the commit). Its "No commit yet / not yet seen in a browser" text predates that by minutes and is stale | `46e8f6b5a` |
| **S / §04 — the card's inset and its interior gap scale.** A marked chart resolves a 12px card inset and 4 / 8 / 14(+2) interior gaps at read time, and an unmarked chart is bit-for-bit unchanged in the viewer, the composer and every export. Nothing is seeded, so clearing the mark reverts by construction — no reverser, no migration, nothing added to the bookmark path. The inset resolves behind `ChartVSAssemblyInfo.getPadding()`, so all 17 read sites follow untouched (four exporters, the report converter, annotation placement, the browser model); the three gaps resolve at the descriptor-to-spec boundary in `GraphGenerator`, keeping `inetsoft.graph` a consumer of resolved values with zero `VizContext` references. `userPadding` records authorship and is carried through the dialog's clone-and-merge; the padding pane and the legend format dialog each gain a follow-the-default checkbox. Hidden-means-zero inheritance: an axis title whose labels are hidden takes the plot-adjacent gap, resolved against the same per-ref descriptor the axis spec uses and mirroring its mode-dependent visibility test, so it holds on a separated chart and in max mode. **Costed XL by this file and it was not** — see the §04 section. **Four of §04's numbers are disproved in code**, recorded in [the geometry decisions](./chart-card-geometry-decisions.md) | `2afb06bc1` |
| **§04 follow-ups** — a custom axis title now resolves the card gap (`getTitleSpec` returned early before the gap was set, so a typed title kept 0 while a derived one took the card's); legacy is fed 0 explicitly there so a CSS `label_gap` cannot start moving unmarked charts. An author can now set the legend gap to 0: the descriptor reports whether it holds an opinion and the resolver asks that instead of comparing against zero. The opinion test is `hasUserValue() || getGap() != 0` — the flag alone is a **regression**, because `CompositeValue` records `cssDefined` privately with no accessor, so a CSS gap would be overridden. Confirmed empirically: the flag alone failed a pre-existing test | `2b86a9fa3` |
| The geometry decisions and the plan that built them | `f4a7993f9` |
| **Decision 10 — chrome resolves on bookmark restore.** The re-seed goes at `AbstractVSAssembly.parseState`, the chokepoint every restore passes, and runs after the content is parsed because a table's parse replaces the whole format object the seed writes into. Wider than decision 10 costed: `dimensionColors` is left alone (persisted author content, not a cache), author-set bar corner radius and smooth lines survive via two provenance flags, and the live mark is captured across the parse so a calc table's serialized info cannot reinstall a stale one. **This emptied the release gate** | `f47c59304` |
| **`viewsheet.modernVisualization` defaults to true**, with `viewsheet.density=compact`. Two lines in `defaults.properties`; no read site changed. Compact rather than dense because dense's 20px lane cannot hold the 24px anchored strip and L″ draws no chrome below that — dense would have shipped the modern look with no toolbar on charts, tables, crosstabs, calc tables and both selection types. **Unset no longer means legacy** | `c7790bbf0` |
| **The title lane becomes unfilled — chart and the three table types.** A `#D9D5CC` bottom rule replaces the `#F1EFEA` filled band, seeded at creation so it travels in an exported asset and resolves on bookmark restore. Both branches write, because the legacy one is the Revert contract. The chart needed the hook re-invoked after it installs its own title composite. Design: [2026-08-28-title-lane-unfilled-design.md](./2026-08-28-title-lane-unfilled-design.md) | `f499c0ffa`, `bee8d4169` |
| **The title lane becomes unfilled — the selection family**: selection list, tree, container and range slider. For the first three this is only a colour change; their lane was already unfilled with a bottom-only rule, and they looked filled solely because the read-time substitution repainted them. The container is structurally the table's case. `isSeededTitle` widens to seven types; the browser and all four export call sites convert, one of them (`PPTVSExporter`) in another Maven module. Full `core` suite 5222/0. **Also fixes a pre-existing bookmark-restore defect**: a container's children are re-created from the state blob and never pass through `parseState`, so they kept the bookmark's mark and came back modern over a reverted container. Their marks are now captured before removal and handed back with a re-seed, mirroring the annotation branch in the same method. **Leaves a recommended hoist** — see [the design](./2026-08-31-selection-family-title-lane-design.md) §9 | *"draw the selection family's title lane unfilled"* |
| **Rollout slice 4 — the selection container**, kebab-only like the rest of its family | `f36469d2f` |
| Slice 4 follow-up — corrected what the max-mode test claims to pin | `8b92878fd` |
| A preparatory test refactor — moved the suite's "outside the anchored set" control from the calendar to the adhoc range slider | `4434877ca` |
| **Rollout slice 5 — the calendar**, the table treatment unmodified; the last slice | `09b29620e` |
| **The anchored set made permanent** — the four TEMPORARY markers rewritten, and the promised deletion of the type test recorded as unsafe: it would empty every laneless assembly's toolbar under the gate | `aab93d919` |
| **A suppressed assembly's actions stay reachable, and the kebab flattens.** Found by the manual browser pass, not by any test: a title-hidden container or calendar right-clicked to a menu holding only Hide MiniToolbar, because the lane-0 rung draws neither strip nor kebab and right-click opens `menuActions`, a different list from `toolbarActions`. **Anchoring a type has always cost a menu-reachability duplication** — the chart pays it for show-data and the max-mode pair, the table family for show-details and export — and slices 4 and 5 did not pay it. Both types now carry their toolbar actions in the menu, under their toolbar twins' ids. **The kebab also flattens where the menu wrapper is the only overflow**, which is every anchored type's normal shape at three or fewer visible actions, so this changes chart, the four table types and both selection types too — taken deliberately, not as a side effect. Flattening then exposed the menu's copies beside the buttons they duplicate, so the merge now also deduplicates against what the strip is drawing | *"keep a suppressed assembly's actions reachable, and flatten its kebab"* |

**Why P2 landed as two commits.** `6db87680c` is a two-line guard in `AbstractVSAssembly`'s stamp plus its
regression test: a mocked `Viewsheet` returns null from `getVSAssemblyInfo()`, which threw and was the sole
cause of ten suite errors at P2's baseline. It belongs to P1's subject, not P2's, so it was split out rather
than folded in. `37667c1bc` carries the phase itself — 71 files under `core/src` — plus its plan, the design
doc's sub-gate decision and this roadmap.

**Why the palette work is one commit and not six.** P6 shipped Revert, and Revert immediately failed on
colour: the modern palette was copied into several stores on the way to the screen and each held a copy that
outranked the re-seeded one. The seed, the per-value provenance mark, the two sheet-level caches, the binding
pane's Apply and the axis-label gate are five distinct defects, but each was only observable once the one
before it was fixed, so they were verified together and committed together rather than shipping four states
in which Revert visibly did not work.

**The docs-only commits are no longer listed here.** All nine hashes this paragraph used to carry —
`d4ef55100`, `ef42a6c65`, `e47bd207a`, `b7bf79157`, `74c8638a6`, `e670744c1`, `91a7babce`, `d1042fd2f`,
`e7ca3c69b` — went stale in the third rebase along with every other hash in this section. A documentation
commit is also the one thing a hash is least needed for:
`git log --oneline -- docs/superpowers/specs/lookfeel/` answers it exactly and cannot go stale. Use that.

**Why §10.2 cites five subjects and not five rows.** Unlike M-P1–M-P6, which each earned its own row because
each was a separately reviewable phase with its own hash, the five tasks behind §10.2 compose one design
item — the tone resolver, the background chain, the binding, the CSS seam and the cleanup of the rules it
replaced — and none of the five has a commit yet. Five rows would each read "no commit yet" for no benefit;
one row carries all five subjects, in the order they are meant to land. Re-derive this row into per-commit
hashes once they exist, the same way the M-phases were, rather than leaving the subjects behind as prose.

**Why the calendar's test-control migration and slice 4's follow-up each have their own row.** `4434877ca`
ran ahead of `09b29620e` specifically so the suite stayed green while the calendar still played its old role —
folding it into the slice would have hidden a self-contained, independently-verifiable change inside a bigger
one. `8b92878fd` is the same kind of correction at a smaller scale: it fixes what a slice 4 test claims to
pin rather than adding behaviour. Full account of the migration in
[the design doc](./2026-09-01-anchored-rollout-slices-4-5-design.md#what-the-implementation-found)'s closing
section. The design and plan documents themselves are not listed here, for the reason the docs-only-commits
note above already gives.

## Still undecided

- **Whether `viewsheet.modernVisualization` ships true, and on which release.** Raised 2026-08-28. The
  mechanism is settled and small — the property is simply unset today — so what is open is the product
  call, not the code. Three things the decision needs an answer on: whether default-true waits for
  decision 10 to land (the dependency picture says it must, because the gate is what currently confines
  that defect to opted-in dashboards); whether an upgrading customer who has never seen the EM checkbox
  should find their *next* new dashboard modern with no action taken; and whether the two surfaces that
  still follow the gate rather than the mark — `AbstractChartInfo.getTooltipStyle` and
  `VSChartInteractionDefaults.isInlineSvg` — become the default everywhere the moment it flips, which
  turns two accepted costs into two shipped behaviours.
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

## Follow-up annotations

**2026-08-21 — stale phase reference in `VSObjectModel.java:604-606`.** The comment on the `vizModern`
field reads: "The assembly's resolved modern-visualization state, not its mark. Resolving on the server
keeps the gate term (VizContext.of(VizMark), deleted in P6) in one place and one language; shipping the
raw mark would need the client to re-evaluate it and drift the moment that term goes." The parenthetical
now describes this change set: the gate term inside `VizContext.of(VizMark)` is what P6 deleted, and the
code now reads `boolean modern = mark != null` (`VizContext.java:66`). The comment's statement remains
true and is not edited here (the file is outside this phase's change set; widening the diff for a
comment at a merge gate is the wrong trade). When `VSObjectModel.java` is next edited for unrelated
reasons, the parenthetical can be replaced with a one-line mention of what was deleted — e.g.
"(VizContext.of(VizMark), which was deleted in P6 and replaced with `mark != null`)" — if brevity is
needed, or simply removed if the parenthetical has lost its explanatory value by then.

---

## Related documents

- [chart-card-source-doc-corrections.md](./chart-card-source-doc-corrections.md) — the running audit of the
  external set against the branch. **Read it before trusting `chart-card-design3/`, and before requesting
  the next sync**
- [chart-card-open-item-decisions.md](./chart-card-open-item-decisions.md) — the title band, dark scope and
  range slider decisions, with the consequences each triggers
- [2026-08-28-title-lane-unfilled-design.md](./2026-08-28-title-lane-unfilled-design.md) — **how the
  unfilled title lane is built**: creation-time seeding through `seedChromeDefaults`, the per-type legacy
  branches Revert depends on, the chart's ordering trap, where the border reaches on each of the five
  render paths and the one place it does not. Its **§1 is the general argument for seeding over read-time
  substitution** and is the reasoning behind the read-time migration item — read it before converting any
  other resolver. Supersedes [chart-card-open-item-decisions.md](./chart-card-open-item-decisions.md) §1
  on mechanism and scope; §1 remains the authority on *why* the band is unfilled
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
