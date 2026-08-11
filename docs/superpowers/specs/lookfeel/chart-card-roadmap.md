# Chart Card — Roadmap

**Date:** 2026-08-11
**Verified against:** community `viz-updates` @ `a038a30b5`
**Covers:** the chart card track — the anchored toolbar rollout, the shell and chart surfaces found
through it, and the decisions that gate what remains

**Verify before trusting.** This branch moves daily; the selection-family slice shipped nine hours after
the document set that called it unshipped. Every claim below cites a commit or a file so it can be
checked rather than believed. If a claim and the branch disagree, the branch is right.

## How to read this

The durable content is the **dependency picture** and the **seed-mark cluster** — those describe
structure, and change only when something lands. The perishable content is what is done, so that section
is a table of item → commit rather than a checklist: `git show` answers it better than a checkbox does.

---

## The dependency picture

```
  L. Title lane row + strip density gating ──→ F. Rollout slices 4–5
     widget spec §08 step 3                      container, calendar
     READY NOW

  M. Seed mark — widget spec §03 ──┬──→ §04 density heights read through the mark
     NOT BUILT · no owner here     │
     ALSO THE RELEASE GATE         ├──→ Card radius 12→6, retire resolveSeededCorner()
                                   │
                                   ├──→ §07 derived selection, retire the teal family
                                   │         │
                                   │         └──→ Range slider — painter half
                                   │
                                   └──→ Outlined text conversion (also behind G)

  G. Chart type scale ──→ H. Outlined text conversion
     needs one measurement

  Ungated: dark (four surfaces) · affordance sweep · selection list interior · Resize Plot
           sliders · nav bar · data-tip registry remainder · chart colour literals ·
           drill and DC tips · zoom naming · dead menu icons · title band
```

Two corrections this picture carries against `Open items - handoff.md`, both recorded in
[chart-card-source-doc-corrections.md](./chart-card-source-doc-corrections.md) §3.1 and §3.2:

- **M does not gate F.** The rollout writes no persisted state — every file in all three shipped slices is
  under `web/projects/portal/src` — so there is no reversibility record for it to miss. The widget spec
  agrees: its §08 names step 3, not the mark, as what "unblocks the chart card's eight-assembly rollout."
- **Step 0's four `:root` tokens are one token.** Touch height and both icon sizes already ship; only a
  gridline grey is genuinely absent from CSS. It is not a precondition — whichever of the colour literals
  or the §08 `GDefaults` mapping goes first can declare it.

---

## The long pole: the seed mark

A tri-state mark on `VSAssemblyInfo` recording the gate state in force when `setDefaultFormat` ran —
`gate-on`, `gate-off`, `before gate` — so that persisted format seeding becomes reversible.
`VSObjectChromeDefaults` is the one resolver of six that writes at creation rather than at read, so
without the mark a gate-off product cannot un-write the frame colour and card background it seeded while
the gate was on.

**Status: specified to implementation depth, not built.** No such field exists in `VSAssemblyInfo`; the
only shipped seeding state is the corner-specific `PlotDescriptor.modernCornerSeed` plus
`isCornerSeedTarget()`. But `chart-card-design2/Visualization Widget Spec.dc.html` §03 is build-ready —
it gives the mechanism (a nullable field, one line each in `writeAttributes`/`parseAttributes`,
`clone`/`copyViewInfo` handling, two lines in `setDefaultFormat`), the resolution rule (mark ≠ gate →
recompute the DEFAULT tier on a clone, at the runtime model build and the export painters, where the
other resolvers already run), coverage for all six mark-and-gate states, a performance requirement
(resolve the gate once per render, in the same change), the rejected alternative with reasons, and its
own estimate: *"The mark is a day."*

**It gates five things and the release.** The four branches in the picture above, plus the range slider's
painter half at one remove. The spec argues that the mark can legitimately sequence behind work with
users waiting — "design debt paid before it is owed" — but is decisive that it must land **before
release**, because the cohort needing migration is empty today and stays empty only until the branch
ships.

**So what is open is ownership and scope, not design.** Three questions for the project that authored §03:

1. Is the mark being built, and when? Nothing in this repo produces it.
2. If not, does this track build it? It is `core/` Java in code both tracks already touch, and the spec
   is complete enough to implement from.
3. **Does the Modernize bar ship with it?** This one is scope, not status. §03 specifies the bar in the
   same section — count-first, applies live with no confirmation dialog, dismissal sticking per
   dashboard, a permanent menu item as the way back, never acting on open alone, no admin bulk command —
   and that is UI work well past "a day." The outlined-text switch is named there as a third consumer
   whose charts must be counted once it ships.

---

## Ready now

Nothing below is blocked.

| Item | Source | Note |
|---|---|---|
| **Title lane row + strip density gating** | Widget spec §08 step 3 | **The leverage item** — the only pending work that unblocks other work. Needs no server change: `viz-density-<mode>` is already set on `body` by `portal/app.component.ts:271`, `composer/app.component.ts:144` and `viewer-app.component.ts:2795`. Retroactive across the three shipped slices |
| **Dark — four browser-DOM surfaces** | [Decisions](./chart-card-open-item-decisions.md) §2 | CSS only. No server, no export, no parity pass |
| Affordance sweep | Widget spec §08 step 1 | Live-view only, no export risk, no decision needed |
| Selection list interior | Widget spec §08 step 2 | The one widget the initiative has not touched; most visible in a filter-heavy dashboard |
| Resize Plot sliders | Handoff item 13 | `vs-chart.component.scss` untouched since `e8df3491b` (2024-07-15). Delete the 15 `-ms-` lines first — the file halves before anything is retokenized |
| Nav bar | Handoff §02 | Reposition to lower right inset from the plot area, plus two cleanups: `z-index: 9999` and the off-scale `border-radius: 5px` (`chart-nav-bar.component.scss:20,26,32`). One open scope question below |
| Data-tip registry remainder | Handoff 1c | One of three assignment paths is under the registry; `vs-pop-component.directive.ts:314,317` and the data tip still assign directly. Documented in-code at `_directives.scss:20-29` as a known partial state |
| Chart colour literals | Handoff item 10 | Declare the gridline CSS token here |
| Chart type scale | Handoff 4a | Gated only by measuring whether 9pt renders as 9px — one build |
| Drill and DC tips · zoom naming · dead menu icons | Handoff step 5 | The "~50 dead icons" count has never been verified; treat it as an estimate |

## After the leverage item

**Rollout slices 4 and 5** — the container and the calendar. `ANCHORED_ASSEMBLY_TYPES`
(`mini-toolbar.service.ts:41-53`) carries six types; the container is deliberately excluded as its own
slice, and the calendar is expected to take the table treatment unmodified.

## Decided, unscheduled

**The title band becomes unfilled** — [decisions](./chart-card-open-item-decisions.md) §1. No dependency
on the seed mark, so it can go whenever someone picks it up. Server-rendered and therefore
export-affecting, so budget the manual pass, and show it to the sibling project first: it breaks the
title-bar/table-header equality their §05 endorses.

---

## Done

| Item | Commit |
|---|---|
| Phase 9B dark mode — every server-rendered surface, chart included | `3e7e52626` |
| Inline-SVG chart rendering coupled to the modern gate | `aed8e6b22` |
| Data-mark-anchored tooltip tail | `7e4a7c809` |
| Shell tooltip retokenize + CARD ramp + data-tip layer declaration (handoff 1a, 1b, part of 1c) | `43a934add` |
| Selection vocabulary — chart-owned surfaces and the annotation border (handoff step 2) | `052fe61f1` |
| Menu-action reachability, the §06 ladder, and rollout slice 1 — chart (handoff 3a, 3b, 3c) | `67c486d67` |
| Right-click reaches max mode on tables | `b1eb8df8e` |
| Rollout slice 2 — table, crosstab, calc table | `a4cd1e362` |
| Max-mode mini-toolbar positioning fix | `1091bd178` |
| Rollout slice 3 — selection list and tree, kebab-only at any width | `a038a30b5` |

## Still undecided

- **Does the nav bar render for maps only, or any zoomable chart?** Changes the reach of that item, not
  the decision.
- **Which render path gauges and thermometers take in the live viewer** — the widget spec flags this
  itself as unverified.
- **Whether the sibling project accepts the unfilled title band**, given they endorsed the fill for
  cross-widget consistency.

---

## Related documents

- [chart-card-open-item-decisions.md](./chart-card-open-item-decisions.md) — the title band, dark scope,
  and range slider decisions, with the consequences each triggers
- [chart-card-source-doc-corrections.md](./chart-card-source-doc-corrections.md) — the running audit of
  the external document set against the branch. **Read it before trusting `chart-card-design2/`**
- [chart-card-slice1-design.md](./chart-card-slice1-design.md) ·
  [chart-card-slice2-tables-design.md](./chart-card-slice2-tables-design.md) ·
  [chart-card-slice3-selection-design.md](./chart-card-slice3-selection-design.md) — how each shipped
  slice works
- `chart-card-design2/` — the external source set: the chart card spec, the tickets, and the sibling
  project's `Visualization Widget Spec.dc.html`. Regenerated wholesale on each sync, so nothing
  authored there survives
- [visualization-implementation-roadmap.md](./visualization-implementation-roadmap.md) — the wider
  initiative, decomposed by phase rather than by slice
