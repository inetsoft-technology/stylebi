# Chart card alignment — review brief

## What this is

A specification for the chart card: the title band, legend, border and plot treated as one chart-owned object rather than host chrome. It states sizing as rules — which boxes flex, which size to their content — and then gives the default-density values those rules resolve to.

Ground truth is `inetsoft-technology/stylebi` @ `epic-74519`. Every code claim in the document was read from the branch, not recalled.

**Main document:** `Chart Card Spec v3.dc.html` — ten sections, roughly a 20-minute read. It is self-contained: nothing in it requires reading any other file in this project.

## What it asks for

One visual change and a small number of edits behind it.

The mini-toolbar stops floating above the assembly and anchors in the title lane, capped at three actions — show-data, max-mode, properties — with a kebab holding the rest. **The kebab is drawn at rest on touch and revealed on hover on pointer devices**, and the **toolbar glyph takes one of two tones derived from the author-set card background** rather than sitting in a white bordered box. Both rules are general enough that the toolbar is only their first consumer, so they are specified in §10 with §02 pointing at them. That resting state is the only part of this users will actually notice: it is the first touch and keyboard route to chart actions, and on a phone it gives the card a permanent "there are controls here" signal. Supporting all of it takes six source edits, listed in §02: reorder `createToolbarActions` so stable actions come first; add a Properties toolbar entry; delete two duplicated toolbar entries; mount the kebab on charts, outside the `@if (!mobileDevice)` guard (inside it, touch devices keep the nothing they have today); draw that kebab at rest under the capability query; and move the Hide MiniToolbar dismissal out of slot 0.

**One of the six reaches beyond the chart.** `AbstractVSActions.createToolbarActions` splices the Hide MiniToolbar dismissal in at index 0, ahead of every assembly action, so a cap of three spends a slot on it. It moves to the menu, and the edit lands in the shared base class rather than a chart-only override — the bug is in shared code, and an override would leave a divergence that becomes permanent. That means it changes the strip for all eight assembly types at once, so it must be sequenced with the rollout and not shipped alone: the other seven need their strips reviewed, not changed incidentally.

When the title is hidden nothing is reserved — the strip overlays the plot's top-right corner instead of collapsing to a lane. That choice is what keeps the pattern rollable to the other seven assembly types without reflowing existing dashboards.

Everything else is spacing, alignment and legend treatment: one shared 12px inset governing every vertical in the card, a documented order in which chrome degrades as the card shrinks, and the legend moving from a bordered panel to a heading-plus-rule list in its four docked placements.

## What it does not ask for

`epic-74519` already ships the `--inet-*` token system, and its neutral palette matches this spec's greys to the hex. The document binds to shipped names rather than proposing new ones. Four `:root` declarations remain to be added; they are additive and gate nothing.

## Where to focus review

**§02 — the toolbar change and the resting kebab.** All the behavioural edits in the document. Worth checking that "first three visible actions" stays stable across selection states. The dashboard-density objection to a persistent kebab is answered by §10.1's capability query — pointer devices show nothing at rest — so what remains to check is the hybrid-laptop case, which follows the pointer rule.

**§10 — chrome on a background the author owns.** New, and the section to read if you only read one. Three rules: the capability query governing resting visibility (10.1), the two glyph tones derived from background luminance (10.2), and dark mode, which this spec has no values for (10.3). Two values here are the ones a reviewer will want to argue about: the luminance threshold of 0.45, and the two tone alpha pairs. 10.1 also names the consequence that needs a per-family call — where the kebab is the only chrome, the query leaves the card empty at rest on a desktop. Two questions that were open here are now settled and recorded: `chart-nav-bar` stays floating and moves to the lower right, inset from the plot area; and the Hide MiniToolbar edit lands in the shared base class, which is what ties §02 to the eight-assembly rollout. Nothing in §02 is waiting on a decision.

**§03 — the title-hidden state.** Settled as overlay, not a reserved lane. This is the decision with app-wide reach, so it is worth agreeing with deliberately rather than by default.

**§04 — spacing.** Every gap is anchored to a specific element so none stack. One source edit is called for: zeroing the graph's own outer margin, which otherwise doubles with the card inset.

**§06 — toolbar degradation.** New: the toolbar now has its own height-driven ladder (comfortable ≥96px, compact 56–96px, kebab-only 32–56px, plot-only below), with the kebab always the last thing to go. It carries a hard precondition — `show-data` and max-mode must reach `createMenuActions` first, or suppression makes them unreachable. Thresholds want checking against real dashboards.

**§05 — legend.** Dropping the panel border returns 8–10px of horizontal room, so the plot and legend column need re-measuring at real render sizes. The floating placement is specified as a surface, not a position, because it has no anchor.

## Out of scope

Four projects were separated out rather than absorbed. None gates the card.

- **Series palette** — handed to the chart palette project. Swatch colours in the mocks are illustrative.
- **`Chart overlay surfaces - ticket.md`** and **`Shell surfaces - ticket.md`** — the surfaces that sit on top of a chart without being part of the card, split by who owns the code: chart-owned (selection drawing, resize sliders, chart literals, drill tips) and shared (the tooltip classes in `scss/internal/_directives.scss`, the annotation overlay, data-tip stacking). The split exists so a chart reviewer is not asked to approve product-wide changes; the selection vocabulary is one decision implemented in both, so those pieces ship together. The three decided changes that are visual are drawn in **`Chart overlay surfaces - decided visuals.dc.html`**. Tooltips: five mechanical token bindings — including an off-scale radius repeated across four call sites — plus a proposal to cut the card tooltip's six-size type ramp to three roles. Selection highlight: replace the off-palette `#dc581e` with the shipped focus family, and a proposal to stop filling large chrome regions (axis, legend, title) the way data regions are filled. Plus a sweep of every other chart, annotation and data-tip overlay: four different oranges, three different ways of drawing "selected", two overlay scrims 0.1 alpha apart, a data-tip layer that computes its own z-indexes outside the shell's stacking registry, a focus state that removes the outline without replacing it, and roughly half a file of dead IE11 code. The selection proposals are both decided — one vocabulary on the shipped focus family, stroke-only for chrome selections. The card-tooltip ramp is decided too — three roles (value 16 / label 13 / caption 11), no fourth size for the stack total. Nothing in this ticket is now waiting on a decision.
- **`Dead menu icons - ticket.md`** — ~50 unreachable `icon()` declarations across two dozen action files. Delete, don't populate.
- **`Seeded value reversibility - ticket.md`** — three mechanisms now answer "did the gate put this value here, so may it take it back?": value-sniffing for the card radius, a per-value boolean for the bar radius, and the sibling project's seed mark. Consolidate on the mark, delete the other two. Carries one problem to solve first — the mark records which branch ran, not which defaults existed, so it is version-blind where value-sniffing is not.
- **`Outlined chart text - ticket.md`** — converting chart SVG from path outlines to real `<text>`. One boolean, large consequences; requires naming the chart type scale first.
- **`Anchoring beyond charts - discussion.md`** — what anchoring the toolbar costs for the other seven assembly types that float one. Not a specification; the chart is the pilot. Records why small widgets are not affected, and the one decision that would reflow existing dashboards.

## Open items

`Open items - handoff.md` (the implementation plan) — everything still outstanding, split by whether it needs a decision-maker,
a verification against the code, or just an engineer. Nothing on it now blocks §02 shipping. It records the six decisions already made — overlay for the
title-hidden state, the resting kebab (now scoped by pointer capability — see
`Corrections since handoff.md`), the nav bar staying floating, Hide MiniToolbar moving to the
menu via the base class, one selection vocabulary, and the three-role tooltip ramp — so they are not reopened.

## Also here

`github.md` — sync receipt: repo, branch, commit, and which screens were built from which files.

`Corrections since handoff.md` — a reminder for anyone who read the handoff set *before* these
changes; not required reading for the spec, which stands alone. It carries what moved and what that
breaks: the pointer-capability scoping of the resting kebab modifies shipped `viz-updates` behaviour
and needs a call on the kebab-only families, and the seed mark from the sibling project gates this
project's card chrome too. Values and rules live in §10.
