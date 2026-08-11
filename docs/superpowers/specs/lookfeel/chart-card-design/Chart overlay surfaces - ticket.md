# Align chart overlay surfaces with the shipped token system

**Type:** cleanup / token binding, plus two decided visual changes · **Branch verified:** `epic-74519` @ `c75c3fabdf64`
**Owner:** the chart. **Blocks:** nothing. **Sibling:** `Shell surfaces - ticket.md` (tooltips, annotations, data tips — shared code, different reviewers).

Surfaces drawn over a chart or inside its plot, in code the chart owns: the **selection highlight**, the
plot-resize sliders, the chart's own overlay literals, and the drill and date-comparison tips.

The tooltip work, the annotation overlay and the data-tip stacking moved to `Shell surfaces - ticket.md`:
they live in `scss/internal/_directives.scss`, `widget/tooltip/` and the annotation component, all of which
are used product-wide. They were found here because a chart was what we were looking at.

**Companion drawings:** `Chart overlay surfaces - decided visuals.html` — §01 selection, §03 the Resize
Plot sliders. Read them before implementing either; a layout described in prose gets built two ways.

---

## Cross-dependency with the shell ticket — one decision, two implementations

The selection vocabulary is one rule: everything that means "selected" uses the shipped focus family, and
chrome selections are stroke-only at 2px while data selections keep the fill. It is implemented in
`ChartTool.drawRegions()` here **and** in the annotation border in the shell ticket's item 11. **Ship them
together.** Either alone leaves the product with two selection idioms, which is what the decision ended.

---

## Summary

**Selection (items 7–9).** When a chart component is selected, the selected regions are filled with a translucent orange and stroked. On a small bar that reads correctly. On a large region — a measure axis band, a legend column, a title — it floods the area and makes the thing it marks harder to read than when unselected.

**Everything else drawn over a chart (items 10–14).** Chart overlay literals including `gray`/`white` resize handles (10); annotation overlays, which add a third way of drawing "selected" (11); the data-tip scrim and, more importantly, a pop/data-tip layer that computes its own z-indexes outside the shell's `$stacking-order` registry — **the highest-value single change in this part** (12); the Resize Plot sliders, the largest item in the ticket, carrying a fourth orange, a removed focus outline and roughly half a file of dead IE11 code (13); and the drill / date-comparison chips (14).

## Mechanism

One function draws every selection: `ChartTool.drawRegions()` in `web/projects/portal/src/app/graph/model/chart-tool.ts`, reached from `ChartObjectAreaBase.drawSelectedRegions()` (`graph/objects/chart-object-area-base.ts`) on selection change, chart-object change, `ngAfterViewInit` and window resize.

Defaults at `chart-tool.ts:762`:

```ts
let fillStyle = "rgba(220, 88, 30, 0.3)"; //#dc581e
let strokeStyle = "#dc581e";
```

They are then overridden from the canvas's own computed style:

```ts
if(canvasCssStyle.color && canvasCssStyle.borderColor &&
   !(bodyCssStyle.color == canvasCssStyle.color &&
     bodyCssStyle.borderColor == canvasCssStyle.borderColor))
{
   fillStyle = `${canvasCssStyle.color}`;
   strokeStyle = `${canvasCssStyle.borderColor}`;
}
```

**So the palette is settable in CSS with no TypeScript change** — `color` on the canvas class becomes the fill, `border-color` becomes the stroke. `lineWidth` is 2.

---

## 7. Component selection and data selection are drawn identically — DECIDED 2026-08-01, build it

Selecting a **bar** means "these data values." A fill is correct: it marks the values, and the regions are small.

Selecting the **Y axis** means "this component, so I can act on it." That is a chrome selection, and filling it floods a band that carries no data. Two different meanings currently share one treatment.

Compounding it: the fill is area-proportional but the signal need is constant. 30% alpha is a gentle tint on a 20px bar and a solid block on a 350px axis band — same code, wildly different visual weight.

**Proposal:**

- **Component selection** (`bottom_x_axis`, `top_x_axis`, `left_y_axis`, `right_y_axis`, `legend_content`, `legend_title`, `x_title`, `x2_title`, `y_title`, `y2_title`) — **stroke only, no fill.** 2px. The outline says "selected" without covering the labels the user is reading.
- **Data selection** (`plot_area` VOs) — **keep the fill.** Marking values is what it is for.

`drawRegions` does not currently know which area it is drawing; the area name is available on the caller (`this._chartObject.areaName` in `drawSelectedRegions`), so this is a parameter, not a refactor.

**Decision: accepted as proposed.** Chrome selections are stroke-only; data selections keep the fill.
The trade accepted with it: chrome selection becomes quieter, and on a busy chart a 2px outline is a
weaker signal than a colour wash. That is the point — the outline does not cover the labels the user
selected the axis in order to read, and it does not scale its weight with the size of the band.
Both parts of the vocabulary decision (this and item 8) were taken together, so implement them together
even though item 8 can technically ship alone.

---

## 8. `#dc581e` is off-palette — and the replacement already ships — DECIDED 2026-08-01

The primary is `#E58A2A`, hover `#C96F12`. `#dc581e` is an orphan orange: close enough to look like a mistake, far enough to be one. §07 of the chart card spec claims every colour resolves to a shipped token; this one does not.

No new token is needed. The shipped focus family is already this colour at this alpha:

```
--inet-focus-ring-color: rgba(229, 138, 42, 0.28);   /* primary at ~30% */
--inet-focus-ring-width: 2px;
--inet-primary-color: #E58A2A;
```

**Change:** fill → `--inet-focus-ring-color`, stroke → `--inet-primary-color`, set as `color` / `border-color` on the canvas class. Semantically right too — a selection *is* a focus state.

**Decision: accepted.** Fill → `--inet-focus-ring-color`, stroke → `--inet-primary-color`, written
once on `.chart-object-canvas` (item 9 confirmed every selection canvas carries it). This is the
product's one selection vocabulary: the same family the shell already uses for focus, everywhere
"selected" is drawn. That reaches the annotation border in item 11 too.

Mechanical and independent of item 7, so it can ship first — but see item 9: the CSS branch it turns on
has never executed in production, so it wants one manual pass rather than being treated as a no-risk
token swap.

---

## 9. Canvas class coverage — VERIFIED, coverage is complete

The CSS override only applies when the canvas's computed `color` **and** `border-color` both differ from `body`. Any area whose canvas lacks the class silently keeps the hardcoded `#dc581e`.

**Verified against `epic-74519`.** Every selection-drawing canvas carries **`chart-object-canvas`**, plus a per-area modifier:

| Area | Element | Classes |
|---|---|---|
| Plot (data selection) | `chart-plot-area.component.html:116` | `chart-object-canvas chart-plot-area__canvas` |
| Reference line | `chart-plot-area.component.html:108` | `chart-object-canvas chart-plot-area__reference-canvas` |
| Axes (all four) | `chart-axis-area.component.html:58` | `chart-object-canvas chart-axis-area__canvas` |
| Legend content / title | `chart-legend-area.component.html:45` | `chart-object-canvas chart-legend-area__canvas` |
| Axis titles (x, x2, y, y2) | `chart-title-area.component.html:44` | `chart-object-canvas chart-title-area__canvas` |

All five come from the one `#objectCanvas` view child on `ChartObjectAreaBase`, which is also the only thing `drawSelectedRegions` draws through — so there is no sixth selectable area with a different canvas.

**No area is missing the class, so item 8 cannot half-apply.** One rule on `.chart-object-canvas` reaches every selectable area at once.

**Second finding: the override path is dead today.** All four per-area SCSS blocks declare only `position: absolute`; nothing anywhere sets `color` or `border-color` on `.chart-object-canvas` or any modifier. The `getComputedStyle` branch therefore never fires, and *every* selection in the product currently falls back to the hardcoded `#dc581e`. That makes item 8 additive rather than a change of behaviour — the first rule written is also the first time the branch is exercised, which is worth one round of manual testing (the branch has never run in production).

**Unrelated loose end:** `.chart-legend__canvas` in `chart-legend-container.component.scss` has no matching element in that component's template. Dead rule; delete with the other cleanups.

The risk this item was raised to catch — item 8 fixing some areas and leaving others on the orphan orange, worse than leaving it alone — does not exist. Item 8 is unblocked.

---

## 10. Other overlay literals in the chart

A sweep of `graph/objects/*.scss` and the canvas draw paths. The recently-touched surfaces are already correct — `.axis__sort-icon--surface`, `.chart-area__empty-surface`, `.chart-area__axis-surface` and `.floating-icon` all bind to `--inet-*`. **Leave those alone; they are the reference for the rest.**

The remainder, worst first:

**Named CSS colours, no tokens at all** — `chart-area.component.scss`:

```scss
.axis-resize-handle { background-color: gray; }
.resize-label      { background: gray; color: white; }
```

Bind to `--inet-border-strong-color` (handle) and `--inet-shell-chrome-dark` / `--inet-light-color` (label), or whatever the resize affordance should read as.

**A third tooltip class — separate rule, same off-scale radius.** `.chart__tooltip` in `chart-area.component.scss`:

```scss
.chart__tooltip { white-space: pre; border-radius: 1px; padding: 12px; }
```

This is **not** another copy of `.widget__default-tooltip` — it shares only the `border-radius: 1px`, and differs on `white-space` (`pre` vs `pre-wrap`) and `padding` (12px vs `--inet-space-2 --inet-space-3`). So:

- **Item 6 stays at two classes** — only `.hidden__annotation-tooltip` duplicates the rule property-for-property.
- **Item 3 gains a call site.** The 1px radius appears in three classes, and the 8px in one, so the radius fix touches four places.
- `padding: 12px` is `--inet-space-5`; bind it.

Worth asking separately why a third tooltip class exists at all, and whether `.chart__tooltip` should just be `.widget__default-tooltip` with a `white-space` override.

**Off-palette greys:**

- `chart-area.component.scss:65` — `.chart__area-resizer`, `rgba(125, 125, 125, 0.8)`
- `chart-plot-area.component.ts:642` — `rgba(120, 120, 120, 0.7)`

Two near-identical greys for adjacent affordances; they should be one token.

**Hardcoded values that happen to be right:**

- `.axis-drill-area__icon-container` — `border-radius: 3px` (= `--inet-radius-md`, but hardcoded), plus `letter-spacing: -2px`
- `.chart-nav-bar` — `border-radius: 5px` on the container and its buttons, off the 2/3/4/6 scale; `left/bottom: 15px`; `padding-top: 6px` (= `--inet-space-3`); `i { font-size: 15px }`

**Greens:** `chart-tool.ts:808` and `chart.service.ts:42` both use `#66DD66` for the reference line; `drawTouch()` (`chart-tool.ts:~1055`) uses `#dc581e` again for the touch crosshair.

### Note

`.chart-nav-bar`'s **geometry and elevation** are a design question, not a binding — it is a second floating hover-revealed control in a card whose toolbar the spec anchors. That part is recorded in the chart card spec §02, not here. Only its off-scale literals belong to this ticket.

---

## 13. The plot-resize sliders

`web/projects/portal/src/app/vsobjects/objects/chart/vs-chart.component.scss`. Shown by the **Resize Plot** action (`model.showPlotResizers`), as `#horizontal-resize-slider` / `#vertical-resize-slider` inside `.chart-plot-resizer-horizontal` / `-vertical`.

This is the largest and least-reviewed overlay in the chart. The range styling is the bulk of the file.

### A fourth orange

```scss
background: #ff8d41;   /* track :hover/:focus, and the thumb, in every engine */
```

The product now has four oranges: `--inet-primary-color` #E58A2A, `--inet-primary-color-dark` #C96F12, the selection `#dc581e` (item 8), and this `#ff8d41`. None of the last two is in the palette. **Bind to `--inet-primary-color`**; the hover state should use `--inet-primary-color-dark`, which is what every other control does.

### Off-palette greys

`border: 1px solid #ddd` on the input, `background: #d3d3d3` on the track and the `-ms-fill-*` pair. Shorthand hex, not on the ramp. `--inet-default-border-color` and `--inet-shell-border-default` cover both.

### Focus bypasses the focus ring

```scss
input[type=range]:focus { outline: none; opacity: 1; }
```

Focus is then signalled by recolouring the track and growing the thumb. The shell ships `--inet-focus-ring` / `--inet-focus-ring-width` / `--inet-focus-ring-color` for exactly this, and killing the outline without an equivalent is an accessibility regression. Also `input[type=range]:-moz-focusring { outline: 1px solid white; }` — a named colour.

### Hover and focus shift layout

The thumb grows 13px → 18px on hover **and** on focus, with a compensating `margin-top` per state. So pointing at the slider changes its geometry, and the thumb's base size differs per engine (13px webkit, 11px moz, 11px ms). Pick one size, express the state in colour, and use `@media(pointer: coarse)` — already present — as the only size variant.

### Dead IE11 code

`::-ms-track`, `::-ms-thumb`, `::-ms-fill-lower`, `::-ms-fill-upper` and four `@media screen and (-ms-high-contrast: ...)` blocks. **If IE11 is not supported, this is roughly half the file.** Deleting it is the single largest simplification available in this ticket.

One of those blocks contains **invalid CSS**:

```scss
padding: 20 0 20 0;   /* no units — silently dropped */
```

Further evidence nobody has read this file in a long time.

### Unscoped element selector

The rules target `input[type=range]` directly, not a class. Angular's emulated encapsulation keeps it inside the component, but **any** range input added to `vs-chart` inherits all of it. Should be a class on the two sliders.

### Fixed 150px width

`width: 150px` with `/*required for proper sizing in FF and Edge*/`. On a narrow chart the slider is wider than the plot it resizes. Relevant to the small-chart thread in the chart card spec.

### The proposed appearance is drawn, not described

See **`Chart overlay surfaces - decided visuals.html`** §03 — current versus proposed over a mock plot,
the four states, the token bindings, and what gets deleted. Summary: drop the 150×22 bordered box entirely (the plot is already the surface), track to
`--inet-default-border-color`, 12px thumb in `--inet-primary-color` with a 2px white ring for separation
from the data behind it, hover darkens the thumb only, focus uses the shipped ring, and the coarse-pointer
case grows the *hit area* to 44px rather than the drawn thumb. Length flexes to `min(150px, 60%)` with an
80px floor. No new tokens. Net smaller than the file it replaces.

### Answered 2026-08-01 — both preconditions resolved

**IE11 is provably unsupported, so the `-ms-` code is dead.** `web/package.json` pins Angular
`^21.2.15`; Angular dropped IE11 in v13. Nothing in this repo can run there. The `::-ms-track`,
`::-ms-thumb`, `::-ms-fill-*` pseudo-elements and the four `-ms-high-contrast` blocks are unreachable —
modern Edge is Chromium and ignores them too. **Delete them; no policy call needed.** That is roughly half
the file, including the block with the invalid unitless `padding: 20 0 20 0`. Do this first: it shrinks
everything that follows.

**The portal has no *shared* range control — but it has four private ones.** Corrected after listing the
tree; an earlier draft of this section said the portal had none, which was misleading.

| Component | SCSS | Built from |
|---|---|---|
| `vsobjects/objects/slider/vs-slider.component` | 14.5KB | divs: `.slider-track`, `.slider-tracked`, `.slider-handle`, `.slider-tick`, `.slider-value` |
| `binding/widget/slider.component` | 10.5KB | divs — **the same class vocabulary**, forked |
| `binding/widget/range-slider.component` | 9.5KB | divs |
| `vsobjects/objects/range-slider/vs-range-slider.component` | 4.5KB | divs |
| `widget/color-picker/color-slider.component` | 1.3KB | divs (hue picker; different problem) |

Roughly **40KB of slider styling across four forks**, two of which (`vs-slider` and `binding/widget/slider`)
share a class vocabulary almost line for line — the viewer copy and the binding-pane copy of one design.
`mat-slider` is used only in `em`; the portal hand-rolls all of these.

**So Material is not the alternative — it was never in play here.** The real choice for the chart's Resize
Plot sliders is: keep the native `input[type=range]` and bind its colours to tokens, or rebuild it in the
`.slider-track` / `.slider-handle` vocabulary the other four share.

**Recommend keeping the native control and retokenizing.** It is the only `input[type=range]` in the
portal, which reads as an inconsistency but is actually the *better* end of it: the native control is
keyboard-operable and screen-reader-legible for free, while the div sliders are not — `vs-slider` carries
`role="slider"` with `tabindex="-1"`, so it cannot be reached by keyboard at all. Rebuilding the chart's
control to match them would trade a working control for a consistent-looking broken one. Fix the one real
regression instead (`:focus { outline: none }` with no replacement, item 13) and bind the colours.

**Out of scope, worth its own ticket:** four forks of one slider design, ~40KB, is a consolidation story
that has nothing to do with the chart card. Someone should own it; not this ticket, and not this spec.

---

## 14. Two more overlay chips

Same file, unrelated to the sliders:

```scss
.drill-tip            { z-index: 5000; opacity: 0.8; color: black;
                        background: rgba(255, 255, 255, 0.7); font-size: 16px; }
.date-comparison-tip  { z-index: 5000; opacity: 0.8; color: black;
                        background: rgba(255, 255, 255, 0.7); font-size: 20px; }
```

Named text colour, a translucent-white plate that is not a token, `z-index: 5000` outside the `$stacking-order` registry (see item 12), and 16/20px type off the scale. Both sit at `top: 3px` in a corner of the plot — a third and fourth floating indicator in a card whose toolbar the spec is anchoring.

Also `.vs-object:hover .move-down { background: white; }` — named colour.

---

## Order of work

Item 9 is done — coverage verified, nothing gated.

**Items 7 and 8 are one decided change** and should land together with the shell ticket's item 11
(the annotation border), since all three implement the single selection vocabulary. Item 8 alone first is
acceptable if it must be split, but do not leave item 11 stranded — see the cross-dependency above. Note
that the CSS branch item 8 turns on has never executed in production, so it wants one manual pass rather
than being treated as a no-risk token swap.

**Items 10, 13 and 14 are independent** and can ship any time. Item 13's two preconditions are both
answered — the IE11 code is provably dead and no shared range control exists — so start with the deletion,
which shrinks everything after it, then apply the drawing in `Chart overlay surfaces - decided visuals.html` §03.

Nothing here blocks the shell ticket, and nothing here blocks the chart card spec.

---

## Verification

- Select a bar, then a measure axis, then a legend entry, then an axis title — confirm the fill/stroke split reads correctly in each.
- Multi-select several bars; confirm data selection is still legible as a group.
- Select on a chart with a dark or saturated plot background — the old 30% orange muddies there.
- Brush and zoom, which redraw selections; and resize the window, which re-triggers `drawSelectedRegions`.
- Touch a chart on a mobile device (`drawTouch`) if the touch crosshair is included.
- Select an annotation on a chart and on a table; confirm the treatment matches item 7 — this one is shared with the shell ticket and is the check that catches the two implementations drifting.
- Invoke **Resize Plot** and exercise both sliders: keyboard focus (the focus ring is the regression risk), hover, coarse pointer, and a chart narrow enough that the 150px slider does not fit.
- Open a drill tip and a date-comparison tip; if the shell ticket's item 12 has landed, check they still stack correctly.
- Check a dark or custom theme if one is available.

---

## Not in scope

The chart card spec. Tooltips are `position: fixed` overlays outside the card's box, and selection is drawn on the region canvases inside the plot — no §04 spacing rule reaches either, and the card ships unchanged regardless.
