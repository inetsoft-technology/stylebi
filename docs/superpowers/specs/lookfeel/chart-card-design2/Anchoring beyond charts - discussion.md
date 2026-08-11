# Anchoring the toolbar beyond charts

**Status:** discussion document — **slices 1–2 have shipped** (chart, table family); the rest is still
undecided · **Branch verified:** `viz-updates` · **Depends on:** the chart card spec §02, which has shipped

**What changed since the last handoff.** `mini-toolbar.service.ts` now carries `ANCHORED_ASSEMBLY_TYPES` /
`isAnchoredAssemblyType()`, explicitly marked TEMPORARY/rollout-boundary in its own comment, naming this
document: `vschart` (slice 1) plus `vstable`, `vscrosstab`, `vscalctable` (slice 2 — "the table family...
all three inherit the chart's treatment unchanged"). `AbstractVSActions.resident`,
`VSObjectContainer.isToolbarAnchored`, the height ladder, the title-hidden overlay + sort-control reserve
(Case 1, below), and the resident kebab are all implemented and gated on `GuiTool.isVizModern()`. So the
pilot shipped, table/crosstab/calc-table followed with no new decisions, and Cases 2–4 below (selection
family, container nesting, range slider) are still the open rollout work — the code points at this
document by name as where those decisions belong. **Corrected — the precondition is not the seed mark.**
An earlier draft of this note put the sibling project's tri-state seed mark ahead of any further slice.
`Visualization Widget Spec.dc.html` §08, which owns that mark, does not: step 4 makes it a **release**
condition ("the branch does not release without it") and never names this rollout, while **step 3 — the
title lane row and the strip's density gating — is what it says "unblocks the chart card's
eight-assembly rollout."** The mark protects *persisted* format seeding through `setDefaultFormat`; this
rollout writes nothing persisted, being entirely under `web/projects/portal/src`, so there is no
reversibility record to miss. Read step 3 before the next slice; the mark is the sibling project's to
land before release.

The chart card spec anchors the mini-toolbar into the chart's title lane. Every other assembly with a toolbar still floats one. This records what generalizing that costs, and the four cases that need answers. **It is deliberately not a specification** for the remaining cases — the chart and table family shipped as pilots; selection, calendar and container should be written up properly before they ship.

## The scope is eight types, not "everything"

`MiniToolbarService.hasMiniToolbar()` enumerates exactly:

`VSCalcTable`, `VSCalendar`, `VSChart`, `VSCrosstab`, `VSSelectionList`, `VSSelectionTree`, `VSTable`, `VSSelectionContainer`, and `VSRangeSlider` **only when** `adhocFilter` is true.

**Small input widgets are not in scope and never were.** Spinner, slider, checkbox, radio button, combo box, submit, text input, image, line, oval, rectangle, gauge and text all have `*-actions.ts` files but never receive a mini-toolbar. They are reached by right-click, before and after. The "how do we anchor a toolbar on a spinner" problem does not exist.

## The enabling fact: seven of the eight already have a title lane

`titleVisible` is declared on:

| Model | Serves |
| --- | --- |
| `base-table-model.ts` | table, crosstab, calc table |
| `vs-calendar-model.ts` | calendar |
| `vs-chart-model.ts` | chart |
| `vs-composite-model.ts` | selection list, selection tree |
| `vs-compound-model.ts` | compound assemblies |

Plus `vs-selection-container-model.ts` carries its own `title`.

So anchoring needs **no new structure anywhere**. Every one of these has the lane the chart spec anchors into, with the same `titleVisible` flag and therefore the same degradation path. This is a much stronger position than treating the chart as a special case.

## Case 1 — Title-hidden assemblies — SETTLED, and shipped for chart + table family

**Decision (chart spec §03): a title-hidden assembly reserves nothing.** The strip overlays the content's top-right corner on its own small surface. This holds for all eight types. **Shipped for chart, table, crosstab, calc table** as `VSObjectContainer.rightEdgeReserve` — a 22px reserve keyed on `titleVisible === false`, covering the table family's column-header sort control specifically.

The rejected alternative was a condensed lane of roughly 28–32px. Applied to all eight, **every title-hidden table and selection list in every existing dashboard would gain ~30px of chrome and lose that much content.** Dashboards are pixel-positioned; that is a visible break across a customer's entire portfolio, not a cosmetic shift — and it would arrive on upgrade with no author action.

Overlaying still delivers everything anchoring is actually for:

- it moves with the assembly
- it stays inside the assembly's border
- it is no longer clamped against viewport bounds by `getToolbarLeft()` / `getToolbarWidth()`
- it cannot cover a neighbouring assembly

…at **zero layout cost**. A lane is reserved only when a title is already visible, where it is free.

The cost is that the strip overlaps content in the title-hidden case. For a table that means covering the first row's right edge; for a selection list, the first item's right edge. Both are recoverable, and the strip sits on `--inet-card-bg-color` at 94% with `--inet-shadow-low` so it reads as a layer rather than a hole.

One qualifier for the rollout: the chart spec also makes **the kebab persistent at rest** (§02), so in the title-hidden case a single dim glyph sits over the content corner permanently, not only on hover. Now live on tables — worth a look at real density; the corner is the first row's right edge.

## Case 2 — The cap of three does not generalize — SETTLED, not yet built

**Decision: the cap of three does not apply to the selection family at all — they show the kebab only.**
**Not yet shipped** — this is still the next slice, not built.

A selection list used as a filter column is often ~150px wide. Three 30px actions plus a kebab is 120px,
leaving 30px for the title.

**So three is a ceiling, not a target.** The rule generalizes as:

```ts
Math.min(3, allowedActionsNum())
```

`AbstractVSActions.allowedActionsNum()` already computes the width-derived number (`objectFormat.width / ~40px`), and `showingActions` / `getMoreActions()` already split on it with a `menu-vertical-icon` kebab from `createMoreAction()`. **The machinery exists**; the chart spec's "cap at three" is a ceiling layered on top of it. It is already live on chart and the table family.

### The lane height does not transfer; the rule does

**Do not copy the chart's 30px lane.** That height is derived from the chart's comfortable target plus lane
padding. A selection list's header is a different animal — a persistent column header in a denser rhythm,
closer to a table header row than a chart title. Forcing 30px inflates chrome in a component often 180–250px
tall, where the header is a much larger proportion of the whole. If it reads as a table column, its header
should read as a table header; a lane borrowed from charts would make it read as a card title, which is the
wrong mental model for something users scan as a list.

What transfers is the rule: **the strip sits in the header lane the component already has, right-aligned, at
the density that lane already uses** — the 24px `--inet-control-height-sm` step, not 30px. Same pattern, same
tokens, same reading; different absolute size because the surrounding rhythm differs.

### The selection family shows the kebab only — no action icons, at any width

**Decision: no width floor, no three-action strip. Selection list, selection tree and selection container
show a single kebab in the header, always.** Still not shipped.

A floor was the wrong shape for this family. Authors size each selection by its data, so there is no
typical rail width to tune a threshold against — any number would be arbitrary, and because widths are
fixed rather than resized, each list would sit permanently on one side of it. The rule would decide
product-wide behaviour on an estimate nobody could validate.

Kebab-only removes the question. It is width-independent, so it needs no measurement and no threshold:

- **These components are narrow by nature.** A selection list is one data column. Three 24px icons plus a
  kebab crowds a header that is often ~150px wide, and crowding is the failure mode users notice.
- **The header is already occupied.** Sort and search live there. Adding three more glyphs makes five or six
  controls competing with the label — the label loses, and the label is what identifies the filter.
- **Nothing is lost.** Every action stays one click away in the menu, which is where a viewer already
  right-clicks. The three-action strip buys speed on a chart people study; a filter column is something
  people set and leave.

So the family follows the chart's *principle* — the kebab is the last thing to go — by starting where the
chart ends up. Below ~56px of width even the kebab plus a truncated label stops making sense; drop chrome
entirely there, as in the chart's bottom row.

## Case 3 — Selection container nesting — not yet built

A selection container has a title *and* holds children with their own titles and their own action sets. Anchoring naively yields two strips in one visual stack.

**The current code already has an answer:** `isMiniToolbarVisible()` returns false when `vsObject.containerType === "VSSelectionContainer"`, and `createToolbarActions()` skips the hide-toolbar action for children of one. The anchored design must **inherit that rule deliberately** rather than rediscover it — the container owns the toolbar, children do not get one.

Also excluded today and worth preserving: `(vsObject as VSSelectionBaseModel).dropdown` and `(vsObject as VSCalendarModel).dropdownCalendar`. Dropdown-variant selections and calendars have no visible chrome to anchor into.

## Case 4 — The adhoc range slider gets no chrome — SETTLED, permanent exclusion

**Decision: the range slider shows nothing — no strip, no kebab. Right-click is its only route.**
`mini-toolbar.service.ts`'s own comment confirms this is permanent, not pending: "VSRangeSlider is
excluded permanently, not pending — it declares no titleVisible, so it has no lane to anchor into."

`VSRangeSliderModel` declares `title`, `titleFormat` and **`titleRatio`** — but **no `titleVisible`**.

The exception holds, but it is `titleVisible` that makes it, not `titleRatio`. `titleRatio` is *not*
unique to the range slider — `VSSelectionBaseModel` (selection list and tree) and
`VSSelectionContainerModel` both declare it too. The difference is inheritance: those two extend
`VSCompositeModel`, so they carry `title`/`titleFormat`/`titleVisible` *and* a ratio split.
`VSRangeSliderModel` extends `VSObjectModel` directly and redeclares `title`/`titleFormat` on its own,
picking up no `titleVisible` — it is the only one of the eight with a title that cannot be hidden.

**Follow-on for the rollout, not for this case.** A ratio-split title is therefore a *family* of three, and
for two of them it coexists with a hideable title: a selection list's title lane is only `titleRatio` of the
assembly's width, with the rest of that row occupied. Anchoring a three-action strip plus kebab into a
fraction of a ~150px lane is the tightest geometry in the rollout — tighter than anything shipped so far
exercises. Case 2 already caps those at `Math.min(3, allowedActionsNum())`; this says the cap will usually
bind, and the fit test must measure the *title's* share, not the assembly's width. **There is also no
shared alignment-anchor model for this measurement yet** — see the sibling audit's finding in
`Open items - handoff.md`, "Two decisions this ticket can't make alone."

Not the bottom rung of the ladder; outside the pattern entirely. Three reasons:

- **There is no lane to anchor into.** Alone among the eight it declares no `titleVisible`, so chrome here
  would be invented rather than relocated.
- **Its toolbar is essentially one action.** A kebab would be a menu wrapping a single item.
- **The component is shorter than the control.** At roughly 40px tall, a 24px kebab is over half its height —
  it reads as a control sitting on a control, not as part of it.

It is also a transient filter rather than a component with chrome, which is the deeper reason: users adjust it
and move on. If that one action must be reachable without right-click, it belongs in the enclosing container's
header, not on the slider.

## What changes visually, per family

Derived from the four cases above plus the chart spec's drawn treatments — an inventory, not a specification for the unshipped families. Nothing here needs new structure; every type listed already has the lane.

**The whole visual story is two sizes:** **30px where the lane is a card title** (chart, table family, calendar) and **24px `--inet-control-height-sm` where it is a header** (selection family, and every title-hidden overlay regardless of type). Right-alignment, the kebab-last rule, the resting kebab and the token set are identical throughout; only the absolute step changes, because the surrounding rhythm does.

**Superseded — read `Visualization Widget Spec.dc.html` §09 and §08 step 3 instead.** The two-size framing above misreads which box the 30px belongs to. §09 settles it as three shipped steps used deliberately: **30px `--inet-control-height-md` is the title lane**, **24px `-sm` is the strip sitting in it**, and 44px is the touch floor. The strip is 24px everywhere, which is also what the branch ships — `GuiTool.MINI_TOOLBAR_HEIGHT_MODERN = 24`, pinned as a hardcoded `height: 24px` at `mini-toolbar.component.scss:82`.

§08 step 3 adds the part this document has no answer for, and it is the real precondition for the remaining rollout: the title lane takes its own row in the density matrix at **20 / 26 / 30**, and **the anchored strip gates to compact-and-above — under dense, today's default, it falls back to the existing hover-revealed overlay** because 20px cannot hold it. That is retroactive: slices 1–3 anchor unconditionally on `GuiTool.isVizModern()` with no density condition (`vs-object-container.component.ts:472-484`), so the gating has to reach them too or the product carries two anchoring rules. The `height: 24px` literal becomes `--inet-control-height-sm` in the same change.

### Tables, crosstabs, calc tables — shipped

Inherit the chart spec's drawing nearly whole: strip right-aligned in the title lane, 30px buttons, `Math.min(3, allowedActionsNum())` plus kebab, kebab drawn at rest. Two divergences, both live:

- **A column header row sits immediately below the title**, not a plot. Shipped as the 22px sort-control reserve in the title-hidden case (`VSObjectContainer.rightEdgeReserve`).
- **Title-hidden overlays the first data row's right edge** at the 24px compact step. Live now — worth a density check on a real dense table.

### Selection list, tree, container — not yet shipped

The largest visual departure. Kebab only, at 24px, always — no three-action strip at any width (Case 2). The container owns it; children inside one get none, inheriting the existing `isMiniToolbarVisible()` rule rather than rediscovering it (Case 3). Below ~56px of width, no chrome at all. Dropdown variants unchanged — nothing to anchor into. ~~Blocked on the seed mark before it ships.~~ **Not blocked on the mark** — see the corrected note at the top of this document. The gating precondition is `Visualization Widget Spec.dc.html` §08 step 3.

### Calendar — not yet shipped

Takes the table treatment unmodified: it has `titleVisible` and no header-row complication. `dropdownCalendar` excluded, as today.

### Adhoc range slider — permanently excluded

No visual change of any kind, ever (Case 4).

### The open one

The selection family's lane is `titleRatio` of a ~150px assembly, so the fit test must measure the
**title's share**, not the assembly width — the tightest geometry in the rollout, and the one thing here
the shipped slices haven't validated. No shared model exists yet for making that measurement; see the
cross-reference above.

## Suggested sequencing

1. ~~Ship the chart card as the pilot.~~ **Done.**
2. ~~Settle Case 1 during the pilot.~~ **Done** — overlay, reserve nothing. See chart spec §03.
3. ~~Ship the table family as the next slice.~~ **Done** — table, crosstab, calc table.
4. ~~Confirm the seed mark has landed before the next slice.~~ **Wrong precondition** — see the corrected note at the top. Instead: **land `Visualization Widget Spec.dc.html` §08 step 3** — the title lane row at 20/26/30 and the strip's compact-and-above gating, applied to the slices already shipped as well as the ones to come.
5. **Then write the remaining rollout properly**, per assembly family: calendar and the container. The selection family shipped in `a038a30b5` (kebab-only at any width), so the entries below describing it as pending are now descriptions of shipped code.
6. **The adhoc range slider is excluded** — no chrome at all (Case 4), permanently.

## Open questions this document does not answer

- Does the persistent kebab hold up on a dense table? Now testable directly — table family has shipped.
- Do tables' title bars behave like the chart's? Now testable directly.
- ~~Does `titleRatio` appear anywhere else?~~ **Answered:** yes — selection list, tree and container all have it. Case 4 is still an exception, but on `titleVisible` rather than `titleRatio`; the ratio split is a family of three. See Case 4.
- Does max-mode change any of this? The chart spec handles max-mode for charts only.
- Does the table's title lane need a bottom inset the chart's does not, and how much? Now testable directly.
