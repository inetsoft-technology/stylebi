# Selection outlines — one style for inner and outer selection

**Date:** 2026-10-21
**Status:** decided; implemented on `feature-selection-outline`
**Verified against:** community `epic-74519` @ `1f2a5bc5d8`
**Scope:** browser-only. No server, export or persisted-state change.

This file records the decisions behind the change and the reasoning for each, so a reviewer can
overturn any one of them without re-deriving the rest. Each decision says who took it — the product
owner, or the builder where the owner left the detail open — and what overturning it costs.

## The problem

In the composer, a selected modern object draws its outer selection as a **2px dashed light blue**
outline (`editable-object-container.component.scss:148-160`, `--inet-viz-selected-border`: `#BFDDE5`
light, `#2DD4BF` dark). That style landed in #5246 (PR #5078). The selections *inside* an object never
followed it:

- a selected chart legend item, axis or axis title draws a **2px solid orange** box
- a selected selection-list cell, title or table cell draws a **2px solid blue** box
- the resize handles inside a selected cell or title are **solid orange** squares, while the outer
  handles are hollow

The request: every inner and outer selection on a viewsheet object matches the dashed blue outline, in
the composer and in the viewer. Worksheet tables and other non-viewsheet surfaces are excluded.

## Summary

| # | Decision | Taken by | Overturn cost |
|---|---|---|---|
| 1 | Inner selections use the outer's style: 2px dashed `--inet-viz-selected-border` | owner | the whole change |
| 2 | Only modern/dark (marked) objects change; legacy objects keep today's look | builder | one selector per rule |
| 3 | Viewer keyboard focus and mobile tap outlines change too | owner | one rule (outer), or a class split at 28 bindings (inner) |
| 4 | Selected chart marks (bars, points) do not change | owner | one branch in `drawRegions` |
| 5 | Inner resize handles take the outer's hollow style, ungated like the outer | owner; ungated is builder | one rule |
| 6 | Dark chart chrome takes the border token, not the lighter ink | builder | one CSS value |

---

## Decision 1 — inner selections match the outer outline

**Decision.** Every inner selection on a viewsheet object draws as 2px dashed
`--inet-viz-selected-border`, the outer outline's exact style and colour.

**Why.** It is the request, and an outline that changes style between the object and its parts
suggests two different states when there is one ("this is what you are editing").

**Mechanisms it reaches.** Five separate code paths draw a selection today. All five are needed to get
one look:

| Path | Where | Today (modern) | Covers |
|---|---|---|---|
| `.bd-selected-cell`, and `.selected-cell:before` which `@extend`s it | `_themeable.scss:112`, `:460-474` | 2px solid blue | **Titles:** chart, table, crosstab, freehand table, checkbox, radio button, selection container (`vs-title`); selection list/tree and current-selection rows (`title-cell`); calendar title and month/year header; range slider title. **Cells:** table, crosstab and freehand table cells and headers; selection list/tree label, measure text and measure bar; checkbox and radio items. **Labels:** combo box, slider, spinner, text input |
| Chart canvas, chrome areas (stroke only) | `chart-tool.ts:783` `drawRegions`, colour from `_themeable.scss:1349` | 2px solid orange | the 4 axes, legend content, legend title, the 4 axis titles — composer and viewer |
| Annotation | `vs-annotation.component.scss:102` | 2px solid orange | annotations on every object type that hosts one |
| `.bd-selected-assembly` | `_themeable.scss:116` | 1px dotted orange | see decision 3 |
| Inner resize handles | `bg-primary` in vs-title, title-cell, calendar title, and 4 in `selection-list-cell` | solid orange | see decision 5 |

**Already matching:** the composer outer outline, the layout-mode outer outline
(`layout-object.component.scss:53-65`), and selection container children, which each get their own
composer container and so the outer rule.

**What it supersedes.**

- `chart-card-slice1-design.md` §4 ("selection vocabulary"): every chrome selection drawn solid 2px
  in the primary orange. That decision was taken before the outer outline moved to dashed blue, and the
  two have disagreed since #5246.
- The annotation comment at `vs-annotation.component.scss:98-101` that states it.

**Overturning it.** Revert the change. Nothing persisted depends on it.

## Decision 2 — marked objects only; legacy keeps its look

**Decision.** Every new rule is scoped to the per-object `.viz-modern` / `.viz-dark` wrapper. An
unmarked or reverted object keeps its current selection look: dotted outer, solid inner.

**Why.**

- **It is the outer outline's own scope.** The outer rule only goes dashed under `.viz-modern` /
  `.viz-dark` (`editable-object-container.component.scss:156`). "Match the outer" therefore means
  dashed where the outer is dashed. Applying dashed inner selections to a legacy object would make it
  mismatch in the other direction.
- **It keeps the modernize/revert contract**: unmarked content is never modernized, and Revert puts
  an object back exactly as it was.
- **It keeps out-of-scope surfaces out for free.** The worksheet, the data model join graph, the Show
  Details table and EM all use `bd-selected-cell` / `selected-cell` too, but none sits inside a
  per-object wrapper. Scoping to the wrapper, and **not** to the `.viz-shell` body class, leaves them
  unchanged with no exclusion list to maintain.

**Pre-existing quirk, not changed here.** In an org with modern visualization on, the body carries
`.viz-shell`, which redefines `--inet-viz-selected-border` for the whole page
(`composer/app.component.ts:142`, `_viz-tokens.scss:109-114`). So a legacy object in such an org already
draws its selections in the blue, dotted outer and solid inner. Only in a gate-off org is a legacy
object's selection orange.

**Overturning it.** Add `.viz-shell` (or drop the scope) on each new selector. Expect worksheet and
data-model selections to change with it unless they are excluded explicitly.

## Decision 3 — viewer keyboard focus and mobile tap change too

**Decision.** Both the viewer's outer focus outline and its inner keyboard-focus boxes become 2px
dashed blue on marked objects.

- **Outer:** `.bd-selected-assembly` on `.focus-assembly`, shown for the keyboard-focused object, and
  for the tapped object on mobile.
- **Inner:** `.bd-selected-cell` where it marks a keyboard-focused part.

**Why.**

- **The request covers the viewer.** The viewer has no click-selection outline of its own;
  `.bd-selected-assembly` is the only outer indicator it draws, and on mobile it *is* the selection.
- **The inner focus boxes share one class with click selection.** 28 bindings in 9 templates use
  `bd-selected-cell` for keyboard focus (calendar ×14 across three templates, range slider ×5,
  selection ×4, mini-toolbar ×2, slider, combo box and text input ×1 each). Leaving them solid while
  click selections go dashed means splitting the class at all 28.
- **The inner focus boxes are already blue.** Going dashed changes their line style, not their
  colour or contrast.

**Costs accepted.**

- **Focus contrast drops in light mode.** The outer focus outline moves from orange `#E58A2A` to
  `#BFDDE5`:

  | Colour | On card `#FFFFFF` | On canvas `#F8F7F4` |
  |---|---|---|
  | `#E58A2A` (today) | 2.62:1 | 2.45:1 |
  | `#BFDDE5` (new) | 1.43:1 | 1.33:1 |

  WCAG 1.4.11 asks 3:1 for the visual cue that identifies a component's state. Today's orange already
  misses it; the blue misses it by more. A dashed line is also lighter in visual weight than a solid
  one at the same width. Dark mode is unaffected: `#2DD4BF` is 8.28:1 on the dark card, against
  4.23:1 for today's dark orange `#C96F12`.
- **It reverses a documented split.** `_viz-tokens.scss:107-108` keeps "active" (focus) on the primary
  accent so it reads apart from "selected". After this change, focus and selection look the same.
- **Geometry, builder's call.** `.focus-assembly` uses content-box sizing
  (`vs-object-container.component.scss:38-42`), so a 2px border would grow the box and leave the ring
  2px inside the object on the left and top but 2px outside it on the right and bottom. The modern
  rule therefore drops the border and draws a 2px `outline`, which sits evenly outside the box, the
  same way the composer's outer outline does.

**Overturning it.**

- **Outer only:** drop the `.viz-modern.bd-selected-assembly` rule. The inner focus boxes stay dashed,
  because they share the class.
- **All keyboard focus:** give the 28 focus bindings their own class (same style as today's
  `bd-selected-cell`) and leave it out of the dashed rule.

## Decision 4 — selected chart marks do not change

**Decision.** Selected bars, points and other data marks (the `plot_area` path in `drawRegions`) keep
their fill and their solid outline: orange in light, the `#A8EEF5` ink in dark.

**Why.**

- **It is a different state.** The code already separates the two: chrome selections mean "this
  part, so I can act on it", data selections mean "these values" (`chart-tool.ts:753-760`). The fill is
  what shows which values are selected. An outline alone would stop marking them.
- **Dark mode cannot take the border colour on marks.** `#2DD4BF` sits ΔE 0.072 from the Modern Dark
  palette's own teal series `#2DEEC6`, under the design set's 0.106 line. That is why dark mark
  selection was moved to the lighter ink (`_themeable.scss:1355-1365`). A selection outlined in the
  border colour would not stand apart from the teal series under it.
- **Data selection has its own owner.** Roadmap item #3 (§07 derived selection fill) covers how
  selected values look. Changing marks here would pre-empt it.

**Consequence accepted.** In the composer, selecting a bar to format it still draws an orange fill and
a solid orange outline inside the dashed blue outer outline. This is the one mismatch left on purpose.

**Overturning it.** Apply the chrome branch (dashed, selection colour) to data areas too, and decide
separately what happens to the fill.

## Decision 5 — inner resize handles match the outer handles

**Decision.** The handles inside a selected title or cell take the outer handles' hollow style: a
surface fill with a 1px `--inet-composer-selected-outline` ring. That covers `.title-resize-handle` in
vs-title, title-cell and the calendar title, and the four `.selection-list-cell-handle`s.

**Why.** Without it, orange marks stay inside the blue dashed box. That is what the selection-list
screenshot showed next to the border. The outer handles are already hollow
(`editable-object-container.component.scss:57`), but that rule sits in a component stylesheet, so
Angular's style encapsulation keeps it from reaching handles rendered by child components.

**Builder's call: ungated.** The outer handle rule applies to every object, legacy included, so the
inner handles follow it everywhere rather than under the mark. Decision 2 is "inner matches outer". For
handles, the outer is the same on every object.

**Watch for.** These handles are clipped by the cell they sit in, so they render as part-squares. A
hollow part-square needs a look in the browser.

**Overturning it.** Gate the rule to `.viz-modern` / `.viz-dark` (legacy keeps solid orange inner
handles), or drop it (every object keeps them).

## Decision 6 — implementation details the owner left open

All builder's calls.

- **Dark chart chrome uses the border token, not the ink.** Chrome outlines use the outer outline's
  colour, `#2DD4BF` in dark, rather than the `#A8EEF5` ink that dark data selection uses. Matching the
  outer is the point of the change. The cost is the ΔE 0.072 near-match from decision 4: a legend item
  next to a teal swatch gets a teal dashed box. Chrome outlines surround labels rather than lying on
  marks, so this is milder than it would be for data. **Overturn:** point the chrome colour at
  `--inet-viz-selected-text` in `.viz-dark`.
- **The chart's chrome colour comes from the token itself.** `drawRegions` reads
  `--inet-viz-selected-border` from the canvas's computed style. The canvas sits inside the object
  wrapper, so it inherits the light, dark or theme-overridden value the outer outline uses. This is the
  same kind of read `drawTouch` already makes for `--inet-primary-color`. If the token is empty, the
  canvas keeps the stroke from its CSS rule. An `outline-color` on the canvas rule was considered and
  dropped: it adds a CSS hop for the same value, and jsdom leaves `var()` unresolved in it, so a unit
  test could not see it.
- **The canvas dash is measured, not assumed.** In the reported screenshot, the 2px dashed outer
  outline draws 6px dashes and 4px gaps, with Chromium stretching a few to 7/3 to fit the box. The
  chrome outline uses `setLineDash([6, 4])`. Browsers fit their dashes to each box and the canvas
  does not, so the match is close, not exact.
- **`@extend` carries the cell rule.** The dashed override sits next to `.bd-selected-cell` in
  `_themeable.scss`, so Sass extends it to `.selected-cell:before` and the table cell overlay. This is
  checked in the compiled CSS, not assumed.

## Left out of scope, and why

| Surface | Why it is not a selection outline |
|---|---|
| Selected-value fills: selection list/tree, calendar days, range slider span | Data state, painted by the server through `VSFormat`, and part of exports |
| Built-in focus rings on inputs: spinner, submit button, the chart's plot-resize slider, dialogs | Control focus (`:focus`), drawn by the focus-ring tokens |
| Drag/drop highlights, the composer search-result outline, snap guides, the target-layout frame | Interaction aids, not selection |
| Worksheet, data model join graph, Show Details table, EM | Not viewsheet objects; excluded by decision 2's scope |

## Interaction with roadmap item #3

Roadmap item #3 plans to retire the teal selection family (`_viz-tokens.scss:50-54`, `:58-66`). This
change gives `--inet-viz-selected-border` three more consumers: chart chrome outlines, the annotation
border and the viewer focus outline. They all read the one token, so a retirement that redefines it
moves them all together with the outer outline. That is the consistency this change is for. A
retirement that *replaces* the token has these sites to migrate as well.

## Verification planned

- **Unit:** `chart-tool-selection.spec.ts` covers the path split. A chrome area gets a dash and the
  selection colour; the plot area stays solid with its fill; gate-off stays solid orange with the fill.
  Run with `ng test portal`, not `npm run test:portal`, which resolves to `em`.
- **Compiled CSS:** grep the compiled stylesheet for the extended selectors
  (`.viz-modern .selected-cell:before` and the table cell overlay).
- **Browser:** composer and viewer, light and dark. Check each row of decision 1's table, keyboard Tab
  focus in the viewer, a mobile tap, an annotation on a chart and on a table, a reverted dashboard
  (unchanged) and a worksheet (unchanged).
