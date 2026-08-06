# Chart Card — Slice 1 Design (Shell · Selection · Toolbar)

**Date:** 2026-08-05
**Source docs:** `chart-card-design/` (enterprise repo root) — `README.md`, `Open items - handoff.md`,
`Chart Card Spec.html`, `Chart overlay surfaces - ticket.md`, `Shell surfaces - ticket.md`,
`Chart overlay surfaces - decided visuals.html`, `Anchoring beyond charts - discussion.md`
**Source docs verified against:** `c75c3fabdf64` (community, ancestor of current HEAD)
**This design verified against:** community `viz-updates` @ `aed8e6b22` (40 commits after the spec commit)

This design covers **slice 1** of the chart card work: the first three phases of
`Open items - handoff.md`. It deliberately excludes the card geometry the main spec is named for —
see [Scope](#1-scope) for why and what that defers.

---

## 0. Why this design exists rather than just following the handoff

The source docs are a complete, decision-closed specification. They do not need re-designing. What
they need is reconciling with 40 commits of work that landed after they were written, and three
questions they do not answer.

**The docs are stale on the server side.** The spec commit predates the entire `*ChromeDefaults`
family: `VSChartChromeDefaults`, `VSDensityDefaults`, `VSTitleChromeDefaults`, `VSObjectChromeDefaults`,
`VSOutputChromeDefaults`, `VSTableStructureDefaults`, `VSCalendarChromeDefaults`,
`VSChartPaletteDefaults`, `VSChartInteractionDefaults` — the whole modern-visualization server gate.
Section §08's "GDefaults greys — Java constants" work is largely already done:
`VSChartChromeDefaults.GRIDLINE = 0xE8E5DE` is exactly the gridline grey §07 lists as "to add", and
label `0x6A685F` / title `0x35342F` already match the spec's colour table, with dark-mode variants.

**A gate now exists that the docs never mention.** `.viz-modern` on the body plus
`VSDensityDefaults.isModern()` (`SreeEnv.getBooleanProperty("viewsheet.modernVisualization")`,
org-scoped), with a `viz-dark` modifier. Every visual change in these docs has to answer "gated or
unconditional?" and the docs specify everything unconditionally.

**Four of the docs' code claims are wrong and five are stale.** Each is recorded, with how to confirm it
and what it changed, in
[chart-card-source-doc-corrections.md](./chart-card-source-doc-corrections.md) — read that alongside the
source docs, because they cite line numbers confidently enough that nobody re-checks them. The two
places where shipped code embodies the *opposite* design decision are in
[§7 Conflicts](#7-conflicts-recorded-not-resolved-here) below.

---

## 1. Scope

### In scope — three PRs, in this order

| PR | Handoff step | Content |
|---|---|---|
| 1 | §1 (1a, 1b, 1c) | Shell surfaces — tooltip dedupe, token bindings, CARD type ramp, data-tip stacking |
| 2 | §2 | Selection vocabulary — `drawRegions` chrome/data split + annotation border, one changeset |
| 3 | §3 (3a, 3b) + spec §02 | Toolbar chain — menu gap, anchoring, resting kebab, height ladder |

The order is forced by the handoff's own rule 3, "shell before chart": reversed, the chart ships a
local value the shared change then has to unpick.

### Out of scope — recorded, not planned here

- **§4** — chart type scale (`Chart type sizes - ticket.md`) and the outlined-text conversion
  (`Outlined chart text - ticket.md`). Ordered pair, independent of everything in slice 1.
- **§5 independents** — Resize Plot sliders, chart colour literals, drill/date-comparison tips, the
  nav bar, zoom naming (`Zoom naming - ticket.md`), dead menu icons (`Dead menu icons - ticket.md`).
- **All card geometry** — spec §01 anatomy, §04 spacing chain, §05 legend. §05 in particular is
  server-rendered Java work, not CSS (see [§7.4](#74-05-legend-is-server-side)).
- **The eight-assembly rollout** — spec §02's sixth edit reaches all eight types, but the rollout
  itself (`Anchoring beyond charts - discussion.md`) stays a separate plan.

### Why slice 1 stops where it does

The card geometry is blocked on two unresolved conflicts with shipped code ([§7.1](#71-title-band-fill),
[§7.2](#72-strip-height)) and on scoping the server-side legend work. Slice 1 contains everything that
is both decided *and* unblocked.

---

## 2. Cross-cutting mechanics

### 2.1 The gate line

The docs call items 1–6 "mechanical", which describes the *edit* (a one-line value swap), not the
*result*. Most of them move pixels. The line for this slice was drawn by inspecting each change:

**Ungated — three items, and only three.** These are the changes that emit identical pixels:

| Change | Why it is invisible |
|---|---|
| Item 6 — tooltip class dedupe | `.hidden__annotation-tooltip` and `.widget__default-tooltip` are identical property-for-property today; the mixin emits the same CSS |
| Item 12 — `popBackground: "white"` → `--inet-dialog-bg-color`, `"rgba(255,255,255,0)"` → `transparent` | `--inet-dialog-bg-color` resolves through `--inet-shell-surface-default` to `#FFFFFF`. Same pixel, now themeable |
| Item 12 — data tips join `$stacking-order` | Changes stacking only where it is currently wrong. Nothing moves when the bug is not firing |

**Gated behind `.viz-modern` — everything else.** Including the changes the docs call mechanical:

| Change | Before → after | Visibility |
|---|---|---|
| Item 3 — radius | `1px` → `2px` (three classes); `8px` → `6px` (card) | small |
| Item 5 — font size | `12px` → `13px` (two classes) | small |
| Item 1 — elevation | `--inet-shadow-low` (`0 1px 2px`) → `--inet-shadow-overlay` (`0 8px 16px, 0 4px 14px`) | plain — the ticket calls this "the one item a designer notices immediately" |
| Item 12 — scrim | `rgba(0,0,0,0.2)` → `rgba(0,0,0,0.3)` | plain |
| Item 8 — selection colour | `#dc581e` → `#E58A2A` family | plain |
| Item 4 — CARD ramp | six type sizes → three roles | plain |
| Items 7 / 11 — selection vocabulary | chrome loses its fill; annotation border dotted-grey → solid 2px primary | plain |
| Spec §02 | anchored strip, resident kebab | plain |

Consequence: gate-off orgs see no appearance change from slice 1, and still get the data-tip
stacking fix — which the handoff calls the highest-value single change in the whole set.

**One judgment call flagged rather than buried:** PR 3's menu-gap fix (3a) is ungated. It adds two
menu entries and removes nothing, so it changes reachability rather than appearance. If the gate line
is read strictly it belongs behind the gate; the argument for ungating it is that right-click cannot
reach max-mode today and that is a standing bug independent of this spec.

### 2.2 Gate readers, both already shipped

- **CSS** — `.viz-modern` on `<body>`, toggled at runtime per org
  (`viewer-app.component.ts:2790`, `portal/app.component.ts`, `composer/app.component.ts`). Component
  styles reach it with `:host-context(.viz-modern)`; the precedent is
  `mini-toolbar.component.scss:81`.
- **TypeScript** — `GuiTool.isVizModern()` (`gui-tool.ts:65`), which reads the body class live.
- **Dark** — `viz-dark` is a modifier *of* modern. Slice 1 adds no dark-specific values, but any
  gated colour must be checked in dark mode because the gate is shared.

### 2.3 The pilot condition

Anchoring, the action cap and the Hide-MiniToolbar move are keyed on **`VSChart` and the gate
together**.

`topY` lives in `mini-toolbar.component.ts`, shared by all eight assembly types
(`MiniToolbarService.hasMiniToolbar()`: `VSCalcTable`, `VSCalendar`, `VSChart`, `VSCrosstab`,
`VSSelectionList`, `VSSelectionTree`, `VSTable`, `VSSelectionContainer`, plus `VSRangeSlider` when
`adhocFilter`). Changing the math unconditionally anchors every type at once, which the docs
explicitly reject: the other seven need "a reviewed strip rather than an incidental one."

The type condition is **explicitly temporary** and is deleted during the rollout. It is documented as
such at the call site. This is a knowing exception to the handoff's rule 1 ("never scope defensively
to the chart"), and the distinction is that rule 1 governs *shared CSS surfaces* — where a `.vs-chart`
prefix doubles the surface permanently — whereas this is a pilot boundary on shared *behaviour* with a
named removal condition.

The gate does most of the containment work independently: the base-class Hide-MiniToolbar edit only
reaches the other seven types for modern orgs, which shrinks the reviewed-strip problem rather than
deferring it.

### 2.4 Token additions

Only what slice 1 actually consumes:

- `--inet-font-size-lg: 16px` — PR 1, required by the CARD ramp's value role.
- `--inet-control-height-touch: 44px` — PR 3, required by the ladder's touch row. Named rather than
  reusing `--inet-control-height-lg` (36px) so it reads as a hit-target floor and is never quietly
  reduced to fit a layout.

Deferred to the card plan: `--inet-subtle-border-color` (the gridline grey — already present Java-side,
see [§7.3](#73-gridline-grey-exists-in-java-only)) and `--inet-icon-size-sm` / `-md`.

Declarations are additive and ungated — a `:root` declaration nothing reads changes nothing.

---

## 3. PR 1 — Shell surfaces

**Ticket:** `Shell surfaces - ticket.md` items 6, 1, 3, 5, 4, 12. **Handoff:** §1 (1a, 1b, 1c).
**Reviewer:** shell owner, not the chart reviewer.

Order is forced twice over: item 6 before 1/3/5, or each binding is applied twice and a future fix
lands on only one copy; and the handoff's rule 4, deletions before bindings.

### 3.1 Files

- `web/projects/portal/src/scss/internal/_directives.scss` — `.widget__default-tooltip`,
  `.widget__card-tooltip`, `.hidden__annotation-tooltip`, `$stacking-order` (line 20)
- `web/projects/portal/src/scss/_variables.scss` — `:root`, for `--inet-font-size-lg`
- `web/projects/portal/src/app/vsobjects/objects/data-tip/date-tip-helper.ts` — `POP_DIM_COLOR`,
  `POP_UP_BACKGROUND_ZINDEX`
- `web/projects/portal/src/app/vsobjects/objects/data-tip/vs-pop-component.directive.ts` —
  `popBackground` (line 42), the z-index offsets (lines 314–317), the `rgba(255,255,255,0)` literal
- `web/projects/portal/src/app/graph/objects/chart-area.component.scss` — `.chart__tooltip`, for the
  fourth radius call site

### 3.2 Changes

**Ungated.**

1. **Item 6 — deduplicate.** Extract the shared surface into a mixin (or `@extend`) and have both
   `.widget__default-tooltip` and `.hidden__annotation-tooltip` consume it. The two rules are
   currently identical on colour, background, border, `font-size: 12px`, `border-radius: 1px`,
   `box-shadow`, `padding`, `margin: 3px`, `white-space` and `word-wrap`.
   **Note:** `.chart__tooltip` is *not* a third copy — it shares only the `1px` radius and differs on
   `white-space` (`pre` vs `pre-wrap`) and `padding` (`12px` vs `--inet-space-2 --inet-space-3`). The
   mixin stays at two consumers.
2. **Item 12 — named-colour defaults.** `popBackground: string = "white"` →
   `var(--inet-dialog-bg-color)`; `"rgba(255,255,255,0)"` → `transparent`.
3. **Item 12 — register the data tips in `$stacking-order`.** The registry is two entries today
   (`.fixed-dropdown` 999900, `w-tooltip` 999901) while the data tips sit at
   `POP_UP_BACKGROUND_ZINDEX = 9996` — two orders of magnitude below, so any dropdown or tooltip
   renders over a data tip. The `+99998` / `+99999` offsets in `vs-pop-component.directive.ts` are the
   workaround. Join the registry and delete both the constant and the offsets.
   **Do not confuse the two `$stacking-order` maps** — the one at `_directives.scss:365` is worksheet
   graph thumbnails and is unrelated.

**Gated behind `.viz-modern`.**

4. **Item 1 — elevation.** `--inet-shadow-low` → `--inet-shadow-overlay` on all three classes. A
   tooltip is `position: fixed` above all content; carrying the card's own shadow reads as co-planar
   with the thing it hovers over.
5. **Item 3 — radii.** `1px` → `--inet-radius-sm` (2px) in `.widget__default-tooltip`,
   `.hidden__annotation-tooltip` and `.chart__tooltip`; `8px` → `--inet-radius-xl` (6px) in
   `.widget__card-tooltip`. Four call sites.
6. **Item 5 — default font size.** `12px` → `--inet-font-size-base` (13px), two classes.
7. **Item 4 — rebuild the CARD ramp.** Three roles, no fourth size:

   | Role | Size | Weight | Colour |
   |---|---|---|---|
   | Value | 16px `--inet-font-size-lg` | 600 | `--inet-text-color` |
   | Label | 13px `--inet-font-size-base` | 400 | `--inet-text-muted-color` |
   | Caption | 11px `--inet-feedback-font-size` | 400 | `--inet-text-subtle-color` |

   Delete `.tt-tier-1.tt-subtitle`, `.tt-tier-2.tt-subtitle` and the specificity-ordering comment — a
   subtitle becomes the caption role, distinguished by colour. `.tt-stack-total` drops from 20px to
   16px and earns its emphasis from `border-top: 1px solid var(--inet-default-border-color)` with
   `padding-top: var(--inet-space-3)`. Spacing collapses to `--inet-space-4` between sections and
   `--inet-space-1` within, replacing 10 / 6 / 4 / 2 / 1px and most of the override rules. The tier
   opacities (`0.9` / `0.7` / `0.65`) go — this subsumes item 2, which must not be done separately.

   Drawn at real sizes in `Chart overlay surfaces - decided visuals.html` §02. **Read it before
   writing the CSS.**
8. **Item 12 — scrim.** `POP_DIM_COLOR = "rgba(0, 0, 0, 0.2)"` → `--inet-overlay-scrim-bg-color`
   (`rgba(0, 0, 0, 0.3)`). A TS literal, so gated via `GuiTool.isVizModern()` rather than in CSS.

### 3.3 Hazards specific to PR 1

- **The z-index registration is the only ungated change with regression risk.** It reaches every
  drill tip, date-comparison tip and data tip. Verify against dropdowns and tooltips specifically —
  those are the two entries the old magic numbers were tuned to clear.
- **`.widget__card-tooltip--tailed` postdates the docs.** Added by the tooltip-tail work
  (`_directives.scss:287`), it zeroes `background-color`, `border-color` and `box-shadow` because the
  SVG chrome draws the outline instead. The ramp rebuild must not reintroduce a border or shadow that
  defeats it.
- **Consumer reach.** `tooltip.directive.ts:43` declares `@Input() tooltipCSS = "widget__default-tooltip"`,
  so items 1, 3 and 5 reach **12 components** — table, crosstab, calc table, chart, chart
  plot/axis/legend, selection list, range slider, gauge, image, text, annotation, hidden annotation.
  Item 4 reaches **one**: `widget__card-tooltip` is applied only at `chart-area.component.ts:369`,
  gated on `model.tooltipStyle === "CARD"`.

### 3.4 Optional, same commit if cheap

Cursor clearance is two unnamed literals serving one purpose — `margin: 3px` on the tooltip classes
and `offsetTop = 15` / `offsetLeft = 15` in `tooltip.directive.ts`. Not required by this slice.

---

## 4. PR 2 — Selection vocabulary

**Tickets:** `Chart overlay surfaces - ticket.md` items 7, 8 **and** `Shell surfaces - ticket.md`
item 11. **Handoff:** §2.

**One decision, two implementations, one changeset.** Either half alone leaves the product with two
selection idioms, which is the thing the decision was taken to end. All gated.

### 4.1 Mechanism

`ChartTool.drawRegions()` (`web/projects/portal/src/app/graph/model/chart-tool.ts:778`) draws every
selection, reached from `ChartObjectAreaBase.drawSelectedRegions()`
(`graph/objects/chart-object-area-base.ts`) on selection change, chart-object change,
`ngAfterViewInit` and window resize. Defaults:

```ts
let fillStyle = "rgba(220, 88, 30, 0.3)"; //#dc581e
let strokeStyle = "#dc581e";
```

They are overridden from the canvas's own computed style when `color` **and** `borderColor` both
differ from `body`'s. So the palette is settable in CSS with no TypeScript colour change.

### 4.2 Changes

1. **Item 8 — bind the palette.** A rule on `.chart-object-canvas` **already exists** at
   `scss/_themeable.scss:1401`, setting `color: rgba(220, 88, 30, 0.3)` and `border-color: #dc581e` —
   the same values as the TypeScript defaults. Add a gated override under `.viz-modern`:
   `color: var(--inet-focus-ring-color)` (fill), `border-color: var(--inet-primary-color)` (stroke).
   Coverage is verified complete — every selection-drawing canvas carries `chart-object-canvas`
   (plot, reference line, all four axes, legend content/title, all four axis titles), all from the one
   `#objectCanvas` view child on `ChartObjectAreaBase`, so item 8 cannot half-apply.
2. **Item 7 — chrome stops flooding.** `drawRegions()` gains a chrome/data parameter;
   `drawSelectedRegions()` passes `this._chartObject.areaName`, which it already has. The ten chrome
   areas — `bottom_x_axis`, `top_x_axis`, `left_y_axis`, `right_y_axis`, `legend_content`,
   `legend_title`, `x_title`, `x2_title`, `y_title`, `y2_title` — become **stroke only at 2px, no
   fill**. `plot_area` keeps its fill. This is a parameter, not a refactor.
3. **Touch crosshair.** `chart-tool.ts:1071` `drawTouch()` uses `#dc581e` again — bind to the primary.
4. **Item 11 — annotation border.**
   `web/projects/portal/src/app/vsobjects/objects/annotation/vs-annotation.component.scss`:
   `.vs-annotation__rectangle--selected { border: 2px dotted darkgray; }` → solid 2px
   `var(--inet-primary-color)`, matching every other chrome selection. Also
   `.vs-annotation__rectangle-content { color: black; }` → `var(--inet-text-color)`, so annotation
   body text can follow a theme. `vs-annotation` is mounted by 11 hosts — chart, gauge, image, text,
   line, rectangle, oval, table, crosstab, calc table and `vs-object-container` itself.
5. **Ungated deletions.** `.annotition-button { border: 2px outset #cccccc; }` — misspelled class,
   Web-1.0 bevel, off-palette; confirm nothing matches the selector, then delete. And
   `.chart-legend__canvas` in `chart-legend-container.component.scss`, which has no matching element
   in that component's template.

Drawn in `Chart overlay surfaces - decided visuals.html` §01. **Read it before implementing.**

### 4.3 Hazards specific to PR 2

- **Ticket item 9 is wrong about the branch being dead.** It concludes that "nothing anywhere sets
  `color` or `border-color` on `.chart-object-canvas`", having checked only the four per-area component
  stylesheets. `scss/_themeable.scss:1401` sets both, to the same values as the TypeScript defaults —
  which is why the claim survived: the branch firing and the branch not firing render identically
  today. So item 8 is a value change in an existing themeable rule, not a first execution of dead code.
  It follows that the "mandatory manual pass because the path has never run" framing does not apply,
  though the manual pass is still wanted for the item 7 fill/stroke split.
- **Verify at runtime which path is live** before relying on CSS alone. The override requires both
  `color` and `borderColor` to differ from `body`'s computed values. If it turns out the branch does
  *not* fire, the TypeScript defaults at `chart-tool.ts:778-779` must be gated too, or the CSS change
  will have no effect.
- **The drift check.** Select an annotation on a **chart** and on a **table**; they must match. This is
  the single verification that catches the two implementations diverging.

---

## 5. PR 3 — Toolbar chain

**Spec:** §02, §03, §06. **Handoff:** §3 (3a, 3b). Chart-only pilot, gated except 3a.

### 5.1 Files

- `web/projects/portal/src/app/vsobjects/action/chart-actions.ts` — toolbar array, menu actions
- `web/projects/portal/src/app/vsobjects/action/chart-actions.spec.ts` — asserts label strings
- `web/projects/portal/src/app/vsobjects/action/abstract-vs-actions.ts` — `createToolbarActions`
  (the `groups.splice(0, 0, …)` at line 277), `allowedActionsNum()` (line 124), `showingActions`
  (line 133), `getMoreActions()` (line 162), `createMoreAction()`
- `web/projects/portal/src/app/vsobjects/objects/mini-toolbar/mini-toolbar.component.{html,ts,scss}` —
  the `@if (!mobileDevice)` guard, `topY` (line 259)
- `web/projects/portal/src/app/vsobjects/objects/vs-object-container.component.ts` —
  `getToolbarTop()` (line 464), `getToolbarLeft()` (line 479)
- `web/projects/portal/src/app/vsobjects/objects/mini-toolbar/mini-toolbar.service.ts` — viewport
  clamping, `getActionsWidth`
- `web/projects/portal/src/scss/_variables.scss` — `--inet-control-height-touch`

### 5.2 3a — the menu gap (ungated)

`chart show-data`, `chart open-max-mode` and `chart close-max-mode` appear only in
`createToolbarActions`, so right-click cannot reach any of them. Add them to `createMenuActions` with
their existing visibility predicates.

Two notes from the ticket: the max-mode pair is **one state-dependent entry, not two rows**; and menu
actions render label-only (`actions-contextmenu.component.html` renders `action.label()` and an
optional child arrow, nothing else), so **do not carry the toolbar glyph across**.

Not a hard precondition for §02 — the concatenation means a suppressed toolbar's actions overflow to
both entry points anyway — but that reachability is a side effect of overflow arithmetic rather than a
guarantee, and it breaks the moment anything stops building the toolbar array for a suppressed
assembly.

### 5.3 §02 — the six source edits (gated, chart-only)

1. **Reorder the toolbar array.** Today's order is drill-down, drill-up, brush, clear-brush, zoom,
   clear-zoom, exclude-data, then show-data eighth. With nothing selected the strip reads correctly;
   click a data point and Drill Down Filter, Brush and Zoom seize positions 1–3 and push the
   chart-level actions out, so icons reshuffle under the pointer on every selection. Emit the stable
   actions first (show-data, the max-mode pair, Properties), contextual ones after. One array literal
   in `createToolbarActions`, **no predicate touched** — source order is arbitrary today, nothing
   reads position.
2. **Add a Properties toolbar entry** bound to `setting-icon`, reusing the menu entry's handler and
   **all four conjuncts verbatim**: `isActionVisibleInViewer("Properties") && !annotationsSelected &&
   !isPopComponent() && !mobileDevice`. Do not paraphrase — `!annotationsSelected` is what keeps
   Properties out of the strip while an annotation is selected. Verified additive: the toolbar has
   `chart edit`, not Properties.
3. **Delete two toolbar entries.** The kebab shows overflowed toolbar actions followed by the menu
   groups, so the two arrays are concatenated — and `chart clear-brush` / `chart clear-zoom` are the
   same operations as the menu's Clear Brushing / View All Data. Blind concatenation would list each
   twice under two names on any brushed or zoomed chart. Remove the toolbar pair; overflow becomes a
   genuine blind append.
4. **Make the kebab resident.** The machinery already exists — `createMoreAction()` produces a
   `menu-vertical-icon` action and `showingActions` appends it, but **only when width forces
   overflow**. The change is to render it always for gated charts, and to mount it **outside** the
   `@if (!mobileDevice)` block in `mini-toolbar.component.html`. Inside it, touch devices keep exactly
   what they have now, which is no visible route to these actions at all.
5. **Draw it at rest.** Kebab at `opacity: 0.55` at rest; the three action buttons keep their
   hover/focus reveal. **Opacity only, never `display`** — and the lane reserves the full strip width
   in both states, so nothing reflows when the actions appear.
6. **Move Hide MiniToolbar out of slot 0.** `AbstractVSActions.createToolbarActions` does
   `groups.splice(0, 0, …)` to prepend the `close-icon` dismissal ahead of every assembly action, so
   under a cap of three it consumes a slot before `show-data` is reached. It moves to the menu, in the
   **shared base class** — the bug is in shared code, and an override would leave a divergence that
   becomes permanent. Gated, so the other seven types only change for modern orgs.

**Suppression is unchanged and outranks all of this.** Per-action visibility via
`isActionVisible(name)` against the server-supplied `model.actionNames` deny-list still empties the
strip; the cap of three applies to whatever survives those predicates, never to the declared set. And
the resting kebab must honour a live `Hide MiniToolbar` dismissal while it lasts — dismissing hides the
whole strip including the kebab, with right-click as the route back. The dismissal is transient
(`MiniToolbarService` holds one `hideMiniToolbarAssembly` string, cleared by `handleMouseEnter`), so it
is a get-out-of-my-way for the current hover, not a stored preference.

**Right-click gains one rule:** overflowed toolbar actions are prepended for right-click too, not only
for the kebab. Otherwise a plot selection could leave Brush, Zoom and Show Details reachable from the
kebab but from nowhere in the right-click menu.

### 5.4 Anchoring — reposition in place

The strip is mounted as a **sibling** of the assembly in `vs-object-container.component.html:340`
(plus five other mount sites: `editable-object-container`, `embed-chart`, `wizard-preview-container`,
`vs-wizard-object`, `vs-object-view`), absolutely positioned. `topY` returns
`this.top - this.miniToolbarHeight - adj` when there is room above, and `MiniToolbarService` clamps
`left` against viewport bounds.

**The change:** for gated charts, `topY` becomes `top + model.paddingTop` instead of
`top - height - adj`, `left` right-aligns to the assembly's right edge less `model.paddingRight`, and
the viewport clamping is dropped on that path — an anchored strip cannot leave the assembly, so there
is nothing to clamp against.

The inset comes from the assembly's own `paddingTop` / `paddingRight`, which is what `vs-title` already
positions against (`vs-chart.component.html`: `[style.top.px]="model.paddingTop"`,
`[style.left.px]="model.paddingLeft"`). **Slice 1 does not introduce the card's 12px inset** — that is
spec §04's shared-inset rule and belongs to the card-geometry plan. Using the existing padding keeps the
strip aligned with the title that already sits in the lane, whatever that padding currently is.

This is what spec §03 actually asks for: *"the title box is always the full lane width and the strip is
`position:absolute` at the lane's right inset — for every alignment, not only centred… Anchoring means
the strip leaves the card's outside; it does not mean it becomes a flow sibling of the title."* It also
fixes all six mount sites at once.

**Accepted limitation:** the strip cannot make the lane grow, so spec §04's "lane height = greater of
its type box and the toolbar strip" does not come for free. That rule belongs to the card geometry
work, which is out of slice 1. The strip overlays the existing lane; where no title is visible it
overlays the plot's top-right corner on its own surface, per §03 — which reserves nothing and therefore
reflows no existing dashboard.

**Keep the shipped 24px** (`GuiTool.MINI_TOOLBAR_HEIGHT_MODERN`, coupled to the pinned container height
in `mini-toolbar.component.scss:81` — change both together).

### 5.5 3b — the height ladder, simplified

`allowedActionsNum()` is `floor(objectFormat.width / actionWidth)` where `actionWidth` derives from the
root font size (18px glyph + 4px icon padding + 1rem button padding + 2px border ≈ 40px at a 16px
root). **Height is never consulted**, so a 400×24 chart passes the width test and still cannot host a
strip.

Two rules: width caps the action count at `Math.min(3, allowedActionsNum())` — a ceiling on machinery
that already exists — and height decides the row. **In both directions the kebab is the last thing to
go.**

Given the shipped 24px modern strip, §06's four rows collapse to three and the 30px comfortable row is
dropped as superseded:

| Card height | Strip |
|---|---|
| ≥ 56px | up to 3 actions + kebab, 24px targets |
| 32 – 56px | kebab only, 24px |
| < 32px | no chrome; right-click is the only route |
| touch, any height | kebab only at 44px; renders at ≥ 52px (44 + 4 + 4) |

This also makes the in-lane and overlaid ladders **identical**, which §06 already specifies for the
overlaid case ("at any height above 56px the overlaid strip is three 24px actions plus the kebab").
One ladder, both placements.

**Where the numbers come from.** Only 32px is derived: a 24px control needs 4px of clearance above and
below. 56px (32 + 24) is judgement, and the handoff names it "the likeliest number in this spec to be
wrong" because a 56px card is common in a KPI row. **Validate against real dashboards before
implementing**, and if it is wrong, move the threshold rather than the control size.

**Never satisfy a height band by shrinking a touch target below 44px** — drop the control instead.

**Glyph sizing is unchanged.** The buttons keep their existing `icon-size-small` class
(`mini-toolbar.component.html`). Slice 1 introduces no icon-size token, which is why
`--inet-icon-size-sm` / `-md` stay deferred ([§2.4](#24-token-additions)).

### 5.6 Hazards specific to PR 3

- `chart-actions.spec.ts` asserts toolbar and menu label strings directly. The reorder, the two
  deletions and the Properties addition all break it; update in the same commit.
- The `@if (!mobileDevice)` guard stays. Touch gets the kebab and nothing else — a complete route
  (the kebab opens the full list) rather than a reduced one.
- The cap must apply to *visible* actions, evaluated as today, so suppression composes with it and
  needs no special case.

---

## 6. Verification

### 6.1 Automated

- Update `chart-actions.spec.ts` for the reorder, deletions and Properties entry.
- Extend `mini-toolbar.component.spec.ts` and `mini-toolbar.component.tl.spec.ts` for the resident
  kebab, the anchored `topY`, and the ladder rows.
- New unit coverage: cap arithmetic (`Math.min(3, allowedActionsNum())`), the height ladder's
  boundaries at 32 / 56 / 52px, and `drawRegions`' chrome-vs-data classification across all eleven
  area names.
- Frontend suites: `npm run test:portal`, `npm run test:em`, and the `:tl` variants.

### 6.2 Manual — one instance per consumer, not one per chart type

Five chart types tell you nothing new; a tooltip on a table and an annotation on a selection list do.

| Check | Catches |
|---|---|
| Tooltip on a **table** and a **selection list** | PR 1's reach beyond charts — the whole point of the shell/chart split |
| A drill tip and a date-comparison tip, against an open dropdown | PR 1's z-index registration |
| A CARD-style chart data-point tooltip, including the tailed variant | PR 1 item 4 vs `--tailed` |
| Select a bar, then a measure axis, then a legend entry, then an axis title | PR 2's fill/stroke split |
| Multi-select several bars | data selection still legible as a group |
| Select on a chart with a dark or saturated plot background | the old 30% orange muddied there |
| Brush, zoom, and resize the window | all three re-trigger `drawSelectedRegions` |
| Select an annotation on a **chart** and on a **table** | the two selection implementations drifting |
| Touch a chart (`drawTouch`) | the touch crosshair |
| A chart at ≥96px, ~70px, ~40px and ~24px | the ladder rows |
| Keyboard-only: Tab to the kebab, open it | the entry point that does not exist today |
| Dark mode (`viz-dark`) | every gated colour |
| **Gate off** (`viewsheet.modernVisualization = false`) | that only the three ungated items changed |

The gate-off pass is the one that protects existing customers, and it applies to all three PRs.

---

## 7. Conflicts recorded, not resolved here

None blocks slice 1. All four block the card-geometry plan and are recorded so they are not
rediscovered.

### 7.1 Title band fill

Spec §01 says the title lane is *"Unfilled — a hairline rule only. **Never a filled band**"*, and §07's
colour table lists "Title band fill `#EEEDE8` → *— removed —* → dropped".

Shipped code does the opposite, deliberately: `VSTitleChromeDefaults.TITLE_BG = 0xF1EFEA` under the
modern gate, documented as *"equal to the table header background so chrome reads as one system"*, with
`TITLE_FG = 0x6A685F` and `TITLE_BORDER = 0xD9D5CC`.

Two considered, opposite decisions from two design tracks. Needs a single owner's call before the card
geometry work. Note the interaction with slice 1: under the gate, the anchored strip sits **on** that
filled band.

### 7.2 Strip height

Spec §02 and §06 specify 30px comfortable in the lane. `GuiTool.MINI_TOOLBAR_HEIGHT_MODERN = 24`
already ships, coupled to `:host-context(.viz-modern)` in `mini-toolbar.component.scss`. Slice 1 keeps
24px and drops the comfortable row ([§5.5](#55-3b--the-height-ladder-simplified)); if 30px is
reinstated later, the ladder regains a row and both the constant and the SCSS must move together.

### 7.3 Gridline grey exists in Java only

Spec §08 asks for `--inet-subtle-border-color: #E8E5DE`. Java already has it —
`VSChartChromeDefaults.GRIDLINE = new Color(0xE8E5DE)`, with `resolveGridlineColor()` and a
`GRIDLINE_DARK` variant. The CSS token is genuinely still absent, but nothing in slice 1 needs it; add
it when a browser surface consumes it, not speculatively.

### 7.4 §05 legend is server-side

The spec presents "panel to rule-plus-list" as a design change without saying where the code lives.
The legend is server-rendered in-graph chrome: its border and background come from
`LegendsDescriptor` / `LegendDescriptor`, seeded through `VSChartChromeDefaults.legendBackground()` and
`legendBorderColor()`, applied in `CSSChartStyles.java:114`. So §05 is `inetsoft.graph` Java work
affecting export as well as display — a materially different risk profile from the CSS the spec's
framing implies. The same applies to §04's interior spacing chain and §06's ladder steps 1–5 (tick
density, axis titles, legend reflow).

---

## 8. Assumptions to correct

- **Branches and tracking.** No Redmine issue numbers appear in the source docs; the only issue
  reference is epic 74519. Assumed: `feature-74519-shell`, `feature-74519-selection`,
  `feature-74519-toolbar`, with every commit and PR citing the source docs' own item numbers
  (shell items 1/3/4/5/6/12, chart items 7/8, handoff steps 1a–3b).
- **All three PRs are community-only.** Every file in slice 1 is under `community/`, so no enterprise
  PR is needed — but the submodule pointer must be committed and pushed before any enterprise change
  references it.
