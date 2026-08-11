# Anchoring the toolbar beyond charts

**Status:** discussion document, no decisions made · **Branch verified:** `epic-74519` @ `c75c3fabdf64` · **Depends on:** the chart card spec §02 shipping first

The chart card spec anchors the mini-toolbar into the chart's title lane. Every other assembly with a toolbar still floats one. This records what generalizing that costs, and the four cases that need answers. **It is deliberately not a specification** — the chart should ship as the pilot before this is written properly.

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

## Case 1 — Title-hidden assemblies — SETTLED

**Decision (chart spec §03): a title-hidden assembly reserves nothing.** The strip overlays the content's top-right corner on its own small surface. This holds for all eight types.

The rejected alternative was a condensed lane of roughly 28–32px. Applied to all eight, **every title-hidden table and selection list in every existing dashboard would gain ~30px of chrome and lose that much content.** Dashboards are pixel-positioned; that is a visible break across a customer's entire portfolio, not a cosmetic shift — and it would arrive on upgrade with no author action.

Overlaying still delivers everything anchoring is actually for:

- it moves with the assembly
- it stays inside the assembly's border
- it is no longer clamped against viewport bounds by `getToolbarLeft()` / `getToolbarWidth()`
- it cannot cover a neighbouring assembly

…at **zero layout cost**. A lane is reserved only when a title is already visible, where it is free.

The cost is that the strip overlaps content in the title-hidden case. For a table that means covering the first row's right edge; for a selection list, the first item's right edge. Both are recoverable, and the strip sits on `--inet-card-bg-color` at 94% with `--inet-shadow-low` so it reads as a layer rather than a hole.

One qualifier for the rollout: the chart spec also makes **the kebab persistent at rest** (§02), so in the title-hidden case a single dim glyph sits over the content corner permanently, not only on hover. For a table that corner is the first row's right edge — worth a look at real density before the table rollout, though it is one 24px glyph at 55% opacity.

## Case 2 — The cap of three does not generalize — SETTLED

**Decision: the cap of three does not apply to the selection family at all — they show the kebab only.**

A selection list used as a filter column is often ~150px wide. Three 30px actions plus a kebab is 120px,
leaving 30px for the title.

**So three is a ceiling, not a target.** The rule generalizes as:

```ts
Math.min(3, allowedActionsNum())
```

`AbstractVSActions.allowedActionsNum()` already computes the width-derived number (`objectFormat.width / ~40px`), and `showingActions` / `getMoreActions()` already split on it with a `menu-vertical-icon` kebab from `createMoreAction()`. **The machinery exists**; the chart spec's "cap at three" is a ceiling layered on top of it.

Note the existing formula never checks height — see the chart spec's §06 discussion, which applies here too.

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
show a single kebab in the header, always.**

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

## Case 3 — Selection container nesting

A selection container has a title *and* holds children with their own titles and their own action sets. Anchoring naively yields two strips in one visual stack.

**The current code already has an answer:** `isMiniToolbarVisible()` returns false when `vsObject.containerType === "VSSelectionContainer"`, and `createToolbarActions()` skips the hide-toolbar action for children of one. The anchored design must **inherit that rule deliberately** rather than rediscover it — the container owns the toolbar, children do not get one.

Also excluded today and worth preserving: `(vsObject as VSSelectionBaseModel).dropdown` and `(vsObject as VSCalendarModel).dropdownCalendar`. Dropdown-variant selections and calendars have no visible chrome to anchor into.

## Case 4 — The adhoc range slider gets no chrome — SETTLED

**Decision: the range slider shows nothing — no strip, no kebab. Right-click is its only route.**

`VSRangeSliderModel` declares `title`, `titleFormat` and **`titleRatio`** — but **no `titleVisible`**.

**Confirmed 2026-08-01 against `epic-74519`.** The exception holds, but it is `titleVisible` that makes it,
not `titleRatio`. `titleRatio` is *not* unique to the range slider — `VSSelectionBaseModel` (selection list
and tree) and `VSSelectionContainerModel` both declare it too. The difference is inheritance:
those two extend `VSCompositeModel`, so they carry `title`/`titleFormat`/`titleVisible` *and* a ratio split.
`VSRangeSliderModel` extends `VSObjectModel` directly and redeclares `title`/`titleFormat` on its own,
picking up no `titleVisible` — it is the only one of the eight with a title that cannot be hidden.

**Follow-on for the rollout, not for this case.** A ratio-split title is therefore a *family* of three, and
for two of them it coexists with a hideable title: a selection list's title lane is only `titleRatio` of the
assembly's width, with the rest of that row occupied. Anchoring a three-action strip plus kebab into a
fraction of a ~150px lane is the tightest geometry in the rollout — tighter than anything the chart pilot
exercises. Case 2 already caps those at `Math.min(3, allowedActionsNum())`; this says the cap will usually
bind, and the lane width test must measure the *title's* share, not the assembly's width.

Not the bottom rung of the ladder; outside the pattern entirely. Three reasons:

- **There is no lane to anchor into.** Alone among the eight it declares no `titleVisible`, so chrome here
  would be invented rather than relocated.
- **Its toolbar is essentially one action.** A kebab would be a menu wrapping a single item.
- **The component is shorter than the control.** At roughly 40px tall, a 24px kebab is over half its height —
  it reads as a control sitting on a control, not as part of it.

It is also a transient filter rather than a component with chrome, which is the deeper reason: users adjust it
and move on. If that one action must be reachable without right-click, it belongs in the enclosing container's
header, not on the slider.

## Suggested sequencing

1. **Ship the chart card as the pilot.** It is nearly ready and does not depend on any of this.
2. ~~Settle Case 1 during the pilot.~~ **Done** — overlay, reserve nothing. See chart spec §03.
3. **Then write the rollout properly**, per assembly family: tables (which have column headers immediately below the title), the selection family (narrow, with dropdown variants), calendar, and the container.
4. **The adhoc range slider is excluded** — no chrome at all (Case 4).

## Open questions this document does not answer

- Does the persistent kebab hold up on a dense table? One glyph over the first row's right edge is cheap on a chart, less obviously so on a 40-row grid.
- Do tables' title bars behave like the chart's? A table's title sits above its column headers, so an anchored strip has a header row immediately beneath it rather than a plot — the visual crowding is different.
- ~~Does `titleRatio` appear anywhere else?~~ **Answered:** yes — selection list, tree and container all have it. Case 4 is still an exception, but on `titleVisible` rather than `titleRatio`; the ratio split is a family of three. See Case 4.
- Does max-mode change any of this? The chart spec handles max-mode for charts only.
