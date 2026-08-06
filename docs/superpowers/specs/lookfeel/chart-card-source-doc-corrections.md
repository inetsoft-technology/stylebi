# Chart Card Source Docs — Corrections and Their Effect on the Plan

**Date:** 2026-08-05
**Applies to:** the `chart-card-design/` document set (enterprise repo root)
**Source docs were verified against:** community `c75c3fabdf64` (2026-07-29, "Merge branch 'main' into epic-74519")
**This audit was verified against:** community `viz-updates` @ `aed8e6b22` — **40 commits later**

The `chart-card-design/` set is a decision-closed specification and most of it holds. This file records
every place where a claim in it does not match the code, plus the places where the code has since moved
under it, and what each one changed in
[chart-card-slice1-design.md](./chart-card-slice1-design.md) and the three PR plans.

**Why this file exists separately.** The source docs are precise and confident, and they cite line
numbers. That makes them easy to trust and hard to correct in passing — an implementer who reads
"verified against `epic-74519`" will not re-check. Each entry below therefore states what the doc says,
what the code says, and how to confirm it, so nobody has to rediscover any of this.

**Reading order:** [§1](#1-factual-errors) is where a doc is wrong about the code as it stood.
[§2](#2-staleness) is where the code moved after the docs were written. [§3](#3-design-conflicts) is
where two deliberate decisions disagree. [§4](#4-gaps) is where a doc leaves a decision unmade that its
implementer needs.

**Scoring.** Of roughly 40 code claims across the set, four are wrong, five are stale, two conflict with
shipped design decisions, and one decision is missing. Everything else checked out — including every
line reference I sampled.

---

## 1. Factual errors

### 1.1 The selection CSS override path is not dead

**Where:** `Chart overlay surfaces - ticket.md` item 9, and the summary in `Open items - handoff.md` §2.

**The claim.** *"All four per-area SCSS blocks declare only `position: absolute`; nothing anywhere sets
`color` or `border-color` on `.chart-object-canvas` or any modifier. The `getComputedStyle` branch
therefore never fires, and every selection in the product currently falls back to the hardcoded
`#dc581e`."* The handoff repeats it as *"That CSS branch has never executed in production… This is the
first time the code path runs"* and makes a manual pass mandatory on that basis.

**What the code says.** `web/projects/portal/src/scss/_themeable.scss:1401`:

```scss
.chart-object-canvas {
  color: rgba(220, 88, 30, 0.3);
  border-color: #dc581e;
}
```

Both properties are set, globally, on exactly the class the branch reads.

**Why the error survived.** The values in that rule are *identical* to the TypeScript defaults at
`chart-tool.ts:778-779`. Whether the branch fires or not, the rendered selection is the same colour, so
no observation could distinguish the two readings. The ticket surveyed the four per-area component
stylesheets — where it correctly found only `position: absolute` — and did not search globally.

**Confirm it:**
```bash
cd community/web/projects/portal/src && grep -rn "chart-object-canvas" --include=*.scss .
```

**Effect on the plan.**
- PR 2 gains **Task 1**, a measurement task that runs before any code change: temporarily set the rule to
  an unmistakable green, build, select a bar, and observe. Green means the CSS branch is live; orange
  means the TypeScript defaults win.
- PR 2 Task 3 gains a **conditional step** that only executes if Task 1 shows orange — in that case the
  `chart-tool.ts` defaults must be gated too, or a CSS-only change has no effect whatsoever.
- Item 8 is reframed from "first execution of a dead branch" to "a value change in an existing themeable
  rule," which is a materially lower risk. The manual pass survives, but for **item 7's fill/stroke
  split**, not for item 8.
- Design doc §4.3 was rewritten to say all of this.

---

### 1.2 The data-tip stacking item is not a live bug

**Where:** `Open items - handoff.md` §1c and `Shell surfaces - ticket.md` item 12.

**The claim.** The handoff calls it the *"Highest-value single change in the whole set — a live bug, not a
cleanup"* and *"any dropdown or tooltip renders over a data tip."* The ticket says the workaround offsets
*"only accidentally clear the two things that matter."*

**What the code says.** The effective order today, top to bottom:

| Layer | Value | Source |
|---|---|---|
| `w-tooltip` | 999901 | `$stacking-order`, `_directives.scss:20` |
| `.fixed-dropdown` | 999900 | same |
| pop **content** | `natural + 99999`, so ≲ 101000 | `vs-object-container.component.ts:585` |
| pop **source** | 9997 | `DateTipHelper.getPopUpSourceZIndex()` |
| dim **scrim** | 9996 | `POP_UP_BACKGROUND_ZINDEX` |

So a dropdown does render above a data tip — but that is **correct**: a dropdown opened *inside* a data
tip must be above it. The relative order is already the right one. What is wrong is that it is
accidental rather than declared, which is a maintainability defect, not a rendering defect.

**Confirm it:**
```bash
cd community/web/projects/portal/src && sed -n '18,23p' app/vsobjects/objects/data-tip/date-tip-helper.ts && sed -n '20,26p' scss/internal/_directives.scss
```

**Effect on the plan.**
- PR 1 Task 3's goal changed from "fix the stacking" to **"preserve today's relative order while making
  it declared."** Its first step records the current order explicitly so the implementer can prove it was
  preserved.
- The registry base is `999897`, chosen so `.fixed-dropdown` and `w-tooltip` keep exactly their current
  999900 / 999901 rather than being pushed up.
- A **clamp was added that the source docs never asked for.** Joining the registry naively — content
  = `natural + boost` with the boost raised to the registry neighbourhood — would put content *above*
  `.fixed-dropdown` for any natural z-index ≥ 2, silently inverting the one relationship that was
  already correct. `getPopUpContentZIndex(natural)` clamps it, and a unit test asserts content stays
  below the dropdown layer for naturals from 0 to 100000.
- The item keeps its place early in PR 1, but its **value claim is downgraded** in the design doc: it is
  still worth doing, and it is no longer the reason not to let the colour work go first.

---

### 1.3 The two tooltip classes are not identical

**Where:** `Shell surfaces - ticket.md` item 6.

**The claim.** *"The two rules are identical property-for-property: same color, background, border,
`font-size: 12px`, `border-radius: 1px`, `box-shadow`, `padding`, `margin: 3px`, `white-space`,
`word-wrap`."*

**What the code says.** Those eleven properties do match. But `.widget__default-tooltip`
(`_directives.scss:202`) also carries:

```scss
  max-width: 40vw;
  max-height: 40vh;
```

`.hidden__annotation-tooltip` (`_directives.scss:293`) does not. The ticket's enumerated list is
accurate; the word "identical" is not.

**Why it matters.** The obvious implementation — lift the whole `.widget__default-tooltip` body into a
mixin and `@include` it in both — silently gives every hidden-annotation tooltip a viewport size cap it
has never had. That is a behaviour change hiding inside a refactor billed as byte-identical, and it would
be ungated, since item 6 is one of the three unconditional changes.

**Confirm it:**
```bash
cd community/web/projects/portal/src && sed -n '202,222p;293,312p' scss/internal/_directives.scss
```

**Effect on the plan.**
- PR 1 Task 1 Step 1 is a **verification step before any edit**, checking the delta still holds and
  instructing the implementer to stop and re-derive if it does not.
- The mixin holds the eleven shared properties only, with a comment stating why `max-width`/`max-height`
  are excluded. `.widget__default-tooltip` re-declares them after the `@include`.
- Task 1 Step 4 compiles the file and requires the two selectors to emit the same declarations they did
  before — the check that catches exactly this mistake.
- PR 1's verification matrix includes "hidden annotation tooltip — **no** 40vw cap introduced."

---

### 1.4 The mini-toolbar already renders a kebab

**Where:** `Chart Card Spec.html` §02, fourth source edit.

**The claim.** *"`mini-menu` appears only in `vs-selection` and `current-selection`; `mini-toolbar`'s
template has no kebab. **It is a new control here, not a relabel.**"*

**What the code says.** Literally true that the template has no dedicated kebab element — and misleading,
because a kebab does render today through the generic action loop:

- `AbstractVSActions.createMoreAction()` (`abstract-vs-actions.ts:321`) returns an action with
  `icon: () => "menu-vertical-icon"` and `label: () => "_#(js:More)..."`.
- `showingActions` (`:133-157`) pushes it into the last action group **when
  `objectFormat.width < getActionsWidth(toolbarActions)`** — i.e. on width overflow.
- `MiniToolbarComponent.getActions()` (`mini-toolbar.component.ts:116-118`) returns
  `this.actions.showingActions`, which becomes `displayActions`, which the template's `@for` renders as
  an ordinary button.

So the kebab exists, is wired, and appears on any assembly too narrow for its actions.

**Confirm it:** narrow a chart in the composer until its actions overflow; a `⋮` appears.

**Effect on the plan.**
- PR 3 Task 3 is scoped as **"make the existing overflow kebab resident"**, not "add a new control." The
  change is one condition — `const needsKebab = modern || <the existing width test>` — plus lifting the
  element outside the `@if (!mobileDevice)` guard and giving it a resting opacity.
- This is one of the few corrections that makes the work **smaller** than the doc implies. The spec's own
  framing ("it is a new control here") would have led to a second, parallel kebab implementation
  alongside the one already in `showingActions` — two kebabs on a narrow gated chart.
- PR 3's plan states the machinery-already-exists point in its architecture summary so the implementer
  reads it before Task 3.

---

## 2. Staleness

Not errors — the docs were accurate at `c75c3fabdf64`. The code moved.

### 2.1 The entire server-side chrome layer landed after the docs

**What the docs assume.** §08 treats the `GDefaults.java` greys as raw, unmediated constants: *"These are
the one part of the palette no CSS token reaches"*, with two tasks — separate the gridline from the axis
line, and change every server-rendered value in Java alongside its CSS token.

**What exists now.** Nine classes that did not exist at the spec commit:

```
VSChartChromeDefaults      VSDensityDefaults        VSTitleChromeDefaults
VSObjectChromeDefaults     VSOutputChromeDefaults   VSTableStructureDefaults
VSCalendarChromeDefaults   VSChartPaletteDefaults   VSChartInteractionDefaults
```

`VSChartChromeDefaults` already holds `GRIDLINE = 0xE8E5DE` — *exactly* the gridline grey §07 lists as
"to add" — plus `LABEL = 0x6A685F` and `TITLE = 0x35342F` matching the spec's colour table to the hex,
`resolveAxisLineColor()` / `resolveGridlineColor()` implementing §08's gridline/axis-line split, and
dark-mode variants for all of them.

**Confirm it:**
```bash
cd community && git cat-file -e c75c3fabdf64:core/src/main/java/inetsoft/uql/viewsheet/internal/VSChartChromeDefaults.java 2>/dev/null && echo EXISTED || echo "did not exist at the spec commit"
```

**Effect on the plan.** §08's Java half is mostly already done, so it is **out of slice 1** rather than
in it. The one genuinely outstanding piece — the CSS token `--inet-subtle-border-color` — is deferred to
the card plan rather than added speculatively, since nothing in slice 1 consumes it (design §2.4,
§7.3).

### 2.2 The modern gate is never mentioned

The docs specify every change unconditionally. `VSDensityDefaults.isModern()`
(`SreeEnv.getBooleanProperty("viewsheet.modernVisualization")`, org-scoped) and the `.viz-modern` body
class — toggled at runtime in `viewer-app.component.ts:2790`, `portal/app.component.ts`,
`composer/app.component.ts` — plus a `viz-dark` modifier, all postdate them.

**Effect on the plan.** This is the single largest structural difference between the docs and the
delivered design. Design §2.1 draws a gate line by inspecting each change for whether it moves a pixel,
finds **only three** that do not, and gates everything else. The docs' framing of items 1/3/5/6 as
"mechanical" describes the edit, not the result: item 1 alone takes every tooltip in the portal from
`0 1px 2px` to `0 8px 16px, 0 4px 14px`.

### 2.3 The strip is already 24px under the gate

`GuiTool.MINI_TOOLBAR_HEIGHT_MODERN = 24` and `getMiniToolbarHeight()` ship
(`gui-tool.ts:58-75`), pinned to `:host-context(.viz-modern)` at `mini-toolbar.component.scss:81`,
delivered by `plans/2026-07-23-viz-phase9c-item1-mini-toolbar-compaction.md`. The spec's §02/§06 ask for
30px "comfortable".

**Effect on the plan.** PR 3 keeps 24px and **drops §06's comfortable row**, collapsing the four-row
ladder to three. This is not merely a concession: §06 already specifies exactly that collapse for the
title-hidden case (*"the top two rows collapse into one"*), so one ladder now serves both placements.
PR 3 also requires reading `getMiniToolbarHeight()` rather than any literal, and flags that it partly
supersedes the earlier plan's premise — that plan's goal was for the strip to sit *above* the object.

### 2.4 The CARD tooltip has grown a structure the docs do not know about

Item 4 says to delete `.tt-tier-1.tt-subtitle` and `.tt-tier-2.tt-subtitle`. Since the docs were written,
the combined-card and tooltip-tail work added `.tt-section`, `.tt-section.tt-section-first`, and a
`.widget__card-tooltip--tailed` variant (`_directives.scss:287`) that zeroes background, border and
shadow because the SVG tail draws the outline instead. `.tt-tier-2.tt-subtitle` is now documented in
the code as the *"Shared X-dim header for combined card."*

**Effect on the plan.** PR 1 Task 6 Step 1 inventories what the rule set actually contains before
editing, and Step 2 maps each existing class onto a role in a table. The ramp reassigns the **type** those
classes carry; it must not remove their **grouping** — `.tt-section` margins survive. Step 4 adds a gated
re-assertion of the `--tailed` flattening, because Task 5's gated `box-shadow` on
`.widget__card-tooltip` would otherwise win on specificity and put a shadow behind the tail.

Pleasingly, the reassignment is what the existing comment already claims: a subtitle "reads as a caption"
becomes literally the caption role.

### 2.5 `chart-actions.spec.ts` asserts by index, not only by label

`Zoom naming - ticket.md` warns that the spec file *"asserts these label strings directly."* True, and
incomplete: it also asserts **positionally** — `menuActions[1].actions[0].id()`,
`menuActions[3].actions[0].id()` (lines 221, 225, 251).

**Effect on the plan.** PR 3 Task 1 appends its new menu group **last**, specifically so those indices do
not shift, and Step 5 instructs that any assertion which does shift be converted to a lookup by `id()`
— *"do not change `menuActions[1]` to `menuActions[2]`… re-indexing preserves the fragility that caused
the breakage."*

---

## 3. Design conflicts

Neither is an error. Two deliberate decisions from two design tracks disagree, and someone has to choose.

### 3.1 Title band fill

| Source | Decision |
|---|---|
| `Chart Card Spec.html` §01 | *"Unfilled — a hairline rule only. **Never a filled band.**"* §07 lists "Title band fill `#EEEDE8` → *— removed —* → dropped" |
| `VSTitleChromeDefaults` (shipped) | `TITLE_BG = 0xF1EFEA`, documented as *"equal to the table header background so chrome reads as one system"*, with `TITLE_FG = 0x6A685F`, `TITLE_BORDER = 0xD9D5CC` |

**Effect on the plan.** Recorded as design §7.1 and listed as a blocker for the card-geometry plan. It
does **not** block slice 1, because the title band is §01/§04 work — but note the interaction: under the
gate, PR 3's anchored strip sits *on* that filled band. Whoever resolves this should know the chart card
spec's proposal reaches beyond charts, since the shipped value was chosen for cross-widget consistency
with table headers.

### 3.2 The legend is server-side, and the spec does not say so

§05 presents "panel to rule-plus-list" as a design change with no statement of where the code lives. It
is server-rendered in-graph chrome: `LegendsDescriptor.java:102` and `LegendDescriptor.java:76` seed
background from `VSChartChromeDefaults.legendBackground()`, and `CSSChartStyles.java:114` applies
`legendBorderColor()`.

So §05 is `inetsoft.graph` Java work that **affects export as well as display** — a materially different
risk profile from the CSS its framing implies. The same is true of §04's interior spacing chain (the
gaps between axis title, labels, plot and legend) and §06's ladder steps 1–5 (tick density, axis titles,
legend reflow).

**Effect on the plan.** This is a substantial part of why slice 1 stops where it does. Design §1 puts all
card geometry out of scope and §7.4 records the render-location finding, so the card plan is scoped as
Java-plus-export from the start rather than discovering it mid-implementation. Only §02/§03, the card
inset and the title lane are browser DOM.

---

## 4. Gaps

### 4.1 The anchoring mechanism is unspecified

§02 says only *"The strip already computes its own `topY` and `left` — anchoring deletes that math rather
than adding any."* That is true and insufficient: it does not say **where the strip is mounted**, and the
answer decides the size of the change.

**What the code says.** `<mini-toolbar>` is a **sibling of the assembly**, not a child, mounted at
`vs-object-container.component.html:340` and at five further sites —
`editable-object-container.component.html:374`, `embed-chart.component.html:50`,
`wizard-preview-container.component.html:106`, `vs-wizard-object.component.html:130`,
`vs-object-view.component.html:62`. It is absolutely positioned from `getToolbarTop()` /
`getToolbarLeft()`, and `topY` (`mini-toolbar.component.ts:259-268`) subtracts its own height.

"Anchoring in the title lane" therefore admits at least three readings — reposition in place, move the
mount into `vs-chart.component.html`, or reposition plus pass lane geometry — differing by roughly an
order of magnitude in blast radius.

**Effect on the plan.** Resolved as **reposition in place** (design §5.4), which is also what §03's own
rule requires: *"the title box is always the full lane width and the strip is `position:absolute` at the
lane's right inset… Anchoring means the strip leaves the card's outside; it does not mean it becomes a
flow sibling of the title."* The container resolves the geometry because it is the component that knows
the assembly's type and format; the strip receives one boolean, `anchorInTitleLane`, telling it to stop
subtracting its own height.

Two consequences the plan states explicitly:

- **§04's lane-height rule does not come for free.** "Lane height = greater of its type box and the
  toolbar strip" needs the lane to size to the strip, which a positioned sibling cannot cause. That rule
  moves to the card-geometry plan.
- **`topY` is shared by all eight assembly types**, so the pilot needs a type condition — marked
  temporary at each site, deleted during the rollout. The five authoring mount sites keep floating by
  default because they never pass the input.

---

## Appendix — what was checked and held

So the corrections above are read as targeted, not as a verdict on the document set:

- Every `*ChromeDefaults` line reference, token name and token value in §07/§08 that I sampled resolved
  correctly against `_variables.scss`.
- `AbstractVSActions.createToolbarActions`'s `groups.splice(0, 0, …)` at line 277 — exactly as described,
  including the `othersGroups` capture before the splice.
- `chart-actions.ts`'s toolbar order — drill-down, drill-up, brush, clear-brush, zoom, clear-zoom,
  exclude-data, show-data eighth. Exactly as described.
- `show-data`, `open-max-mode` and `close-max-mode` absent from `createMenuActions`. Confirmed; right-click
  genuinely cannot reach max-mode.
- Properties is menu-only; the toolbar has `chart edit`, not Properties. Confirmed, so §02's second edit
  is genuinely additive.
- `allowedActionsNum()` never consults height. Confirmed at `abstract-vs-actions.ts:124-131`.
- `mini-toolbar.component.html` wraps the whole button container in `@if (!mobileDevice)`. Confirmed —
  there is currently no visible route to these actions on touch.
- Item 9's canvas-class **coverage** claim — every selection-drawing canvas carries
  `chart-object-canvas`. Confirmed across all five templates. (Its *deadness* conclusion is §1.1; the
  coverage half is right.)
- `#dc581e` at `chart-tool.ts:778-779` and again at `:1071` in `drawTouch`. Confirmed.
- `#ff8d41` in `vs-chart.component.scss` — 7 occurrences. Confirmed.
- 15 lines matching `-ms-` in `vs-chart.component.scss` (`grep -c`, so pseudo-elements plus the
  `-ms-high-contrast` media blocks), and Angular pinned well past IE11 support. Confirmed dead. I did
  not verify the ticket's "roughly half the file" estimate.
- The off-scale `1px` and `8px` tooltip radii, across four call sites including `.chart__tooltip`.
  Confirmed.
- `.annotition-button` misspelled and matching nothing; `.chart-legend__canvas` with no element in its
  template. Both confirmed dead.
- `tooltip.directive.ts:43`'s `tooltipCSS = "widget__default-tooltip"` default, and
  `widget__card-tooltip` applied in exactly one place (`chart-area.component.ts:369`). Both confirmed —
  which is what closes the item 4 ramp risk.
