# Chart card alignment — review brief

## Required reading before the next sync

**`community/docs/superpowers/specs/lookfeel/chart-card-source-doc-corrections.md`** — the running audit
of this document set against the branch. Read it *with* the branch, not after; and diff the next sync's
conclusions against it before publishing.

This is not optional housekeeping. The 2026-08-11 sync was performed by reading `viz-updates` directly —
the right primary source — but not that file, so three findings it had already overturned came back:
that the selection CSS override path had never executed (it is set globally in `_themeable.scss:1401`),
that the data-tip stacking order was a live rendering bug (the order is correct and deliberate; only its
*declaration* was missing), and that the anchored strip is 30px (it is 24px, and the 30px belongs to the
lane, not the strip). All three are re-derivable from the branch alone, because in each case the evidence
is genuinely ambiguous without the correction — identical colour values, a correct-but-undeclared
stacking order, and a ladder whose top rung was dropped by decision rather than omission.

## Changed since the last handoff

- **Branch moved: `epic-74519` → `viz-updates`.** Ground truth below is updated to match.
- **Much of §02 has already shipped as code**, not just decided — anchored toolbar, the §06 height ladder,
  and the menu-actions reachability fix are live for chart + table/crosstab/calc-table. Treat the matching
  steps in `Open items - handoff.md` as verification passes, not build work — but verify before skipping
  them, this branch moves daily.
- **The chart-owned and shell selection changes have also shipped**, matching the decision exactly (`Chart
  overlay surfaces - ticket.md`, items 7/8/9/11).
- **Item 12 (data-tip stacking) is still open despite looking touched** — a partial fix landed that adds a
  ceiling but doesn't retire the offsets it was meant to replace. See `Shell surfaces - ticket.md` item 12.
- **New precondition: a seed mark must land before the eight-assembly rollout (step 3c).** A sibling
  project decided a tri-state provenance mark for newly-created assemblies; without it, the rollout ships
  seeded chrome with no reversibility record. See `Open items - handoff.md`, step 0 and the dependency
  picture.
- **Two decisions this project hasn't made, surfaced by that same sibling audit:** no dark-mode values for
  the chart card, and no shared alignment-anchor model for how a strip anchors to a lane. See `Open items -
  handoff.md`, "Two decisions this ticket can't make alone."
- **New, unticketed work on the branch, not yet reconciled with this project's decisions:** a data-mark-
  anchored tooltip tail (changes tooltip positioning, not just palette) and a shell-owned `_viz-tokens.scss`
  token layer adjacent to `Chart type sizes - ticket.md`'s proposal. Flagged, not yet reviewed.
- **`Visualization Widget Spec.dc.html` joins the set — the sibling project, and the second canonical
  drawing source.** It is not a status overlay. Four of its decisions land on chart-card work: the card
  radius drops from the seeded 12px to 6px and `resolveSeededCorner()` retires (§03); the title lane takes
  20/26/30 by density and **the anchored strip gates to compact-and-above, with dense falling back to the
  hover-revealed overlay** (§05/§08) — retroactive to the three slices already shipped; the
  `_viz-tokens.scss` teal selection family retires in favour of a fill derived from
  `--inet-primary-color` (§07); and §09 supplies the alignment-anchor rule this set records as missing.
  **It also relocates the seed mark**: the mark gates *release* and the two workstreams that write
  persisted format, not the toolbar rollout — see `Open items - handoff.md`, step M.

## What this is

A specification for the chart card: the title band, legend, border and plot treated as one chart-owned object rather than host chrome. It states sizing as rules — which boxes flex, which size to their content — and then gives the default-density values those rules resolve to.

Ground truth is `inetsoft-technology/stylebi` @ `viz-updates`. Every code claim in the document was read from the branch, not recalled; where a claim was verified against the code shipping rather than just being decided, the document says so.

**Main document:** `Chart Card Spec.html` — nine sections, roughly a 20-minute read.

## What it asks for

One visual change and a small number of edits behind it.

The mini-toolbar stops floating above the assembly and anchors in the title lane, capped at three actions — show-data, max-mode, properties — with a kebab holding the rest. **The kebab is drawn at rest**; the three actions fade in on hover or focus. That resting state is the only part of this users will actually notice: it gives the card a permanent "there are controls here" signal, and it is the first keyboard and touch route to chart actions. **This has shipped in code** for the chart, plus table, crosstab and calc table — verify against current `viz-updates` before treating any of the six source edits below as still to do.

**One of the six reaches beyond the chart.** `AbstractVSActions.createToolbarActions` splices the Hide MiniToolbar dismissal in at index 0, ahead of every assembly action, so a cap of three spends a slot on it. It moved to the menu, in the shared base class rather than a chart-only override — the bug was in shared code, and an override would have left a divergence that becomes permanent. That changed the strip for the types in the rollout at once; the remaining assembly families (selection list/tree, calendar, container) are still undecided — see `Anchoring beyond charts - discussion.md`.

When the title is hidden nothing is reserved — the strip overlays the plot's top-right corner instead of collapsing to a lane. That choice is what keeps the pattern rollable to the other assembly types without reflowing existing dashboards.

Everything else is spacing, alignment and legend treatment: one shared 12px inset governing every vertical in the card, a documented order in which chrome degrades as the card shrinks, and the legend moving from a bordered panel to a heading-plus-rule list in its four docked placements.

## What it does not ask for

`viz-updates` ships the `--inet-*` token system, and its neutral palette matches this spec's greys to the hex. The document binds to shipped names rather than proposing new ones. Four `:root` declarations remain to be added; they are additive and gate nothing — see `Open items - handoff.md` step 0.

## Where to focus review

**§02 — the toolbar change and the resting kebab.** Shipped for chart, table, crosstab, calc table. Worth confirming "first three visible actions" stays stable across selection states on the real branch, and that a persistent kebab per assembly reads correctly at dashboard density. `chart-nav-bar` stays floating, moved to the lower right, inset from the plot area — unaffected by the sync, not in the diff.

**§03 — the title-hidden state.** Shipped as overlay, not a reserved lane, with a 22px sort-control reserve for table-family types. This is the decision with app-wide reach, so worth confirming it reads correctly on a real dashboard rather than just in code.

**§04 — spacing.** Every gap is anchored to a specific element so none stack. One source edit is called for: zeroing the graph's own outer margin, which otherwise doubles with the card inset. Not in the sync diff — still open.

**§06 — toolbar degradation.** Shipped: `ACTION_FLOOR=32`, `ACTIONS_MIN=56`, cap at `Math.min(3, allowedActionsNum())`, kebab always last to go. Worth checking the thresholds against real dashboards now that they're live rather than proposed.

**§05 — legend.** Dropping the panel border returns 8–10px of horizontal room, so the plot and legend column need re-measuring at real render sizes. Not in the sync diff — still open. The floating placement is specified as a surface, not a position, because it has no anchor.

## Out of scope

Four projects were separated out rather than absorbed. None gates the card.

- **Series palette** — handed to the chart palette project. Swatch colours in the mocks are illustrative.
- **`Chart overlay surfaces - ticket.md`** and **`Shell surfaces - ticket.md`** — the surfaces that sit on top of a chart without being part of the card, split by who owns the code: chart-owned (selection drawing, resize sliders, chart literals, drill tips) and shared (the tooltip classes in `scss/internal/_directives.scss`, the annotation overlay, data-tip stacking). The selection vocabulary and the CARD tooltip ramp have both shipped, matching the decisions. Item 12 (data-tip stacking) is still genuinely open — see the changelog above. Nothing in either ticket is waiting on a decision.
- **`Dead menu icons - ticket.md`** — ~50 unreachable `icon()` declarations across two dozen action files. Delete, don't populate. Not re-verified this sync.
- **`Outlined chart text - ticket.md`** — converting chart SVG from path outlines to real `<text>`. One boolean, large consequences; requires naming the chart type scale first. Not re-verified this sync; also see the changelog note about a shell `_viz-tokens.scss` layer adjacent to this ticket's proposal.
- **`Anchoring beyond charts - discussion.md`** — what anchoring the toolbar costs for the assembly families that still float one. The chart was the pilot and has shipped, along with the table family; selection list/tree, calendar and the container are still undecided.

## Open items

`Open items - handoff.md` (the implementation plan) — everything still outstanding, now split by what's shipped-and-needs-verifying versus what's genuinely still to build. See its own top note for what changed.

## Also here

`Visualization Widget Spec.dc.html` — the sibling project: everything that is not the chart. Read §03,
§05 and §09 before implementing anything in this set; they override it. Second of the two canonical
drawing sources, the other being `Chart overlay surfaces - decided visuals.html`.

`github.md` — sync receipt: repo, branch, sync date, and which screens were built from which files.
