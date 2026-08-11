# Shell surfaces found through the chart

**Type:** cleanup / token binding, plus one decided visual change · **Branch verified:** `epic-74519` @ `c75c3fabdf64`
**Owner:** whoever owns the shell — **not** the chart reviewer. **Blocks:** nothing. **Sibling:** `Chart overlay surfaces - ticket.md` (the chart-owned half).

Every item here lives in shared code and is used beyond charts. They were found while auditing a chart
card, which is an accident of how the work started — `scss/internal/_directives.scss` and
`widget/tooltip/` are product-wide, and the annotation overlay renders on tables and selection lists too.
Split out of the chart ticket so that a chart reviewer is not asked to approve product-wide changes.

**Companion drawings:** `Chart overlay surfaces - decided visuals.html` §02 renders the tooltip ramp at
real sizes; §01 covers the annotation selection border, whose treatment is set by the chart ticket's
selection decision — see the cross-dependency below.

## How these land without breaking their other consumers

The chart was the **lens** these problems were found through, not their **scope**. The project already
settled the principle, for Hide MiniToolbar: the edit lands in the shared base class, not a chart-only
override, because the bug is in shared code and an override leaves a divergence that becomes permanent.
Four rules follow from that.

1. **Inventory the consumers before changing anything.** ✅ **Done — see the next section.** Run it again
   if this ticket sits for a while; it is the basis of the test matrix.
2. **Never scope defensively to the chart.** No `.vs-chart .widget__card-tooltip`. It doubles the surface
   and the divergence is never cleaned up.
3. **Test one instance per consumer, not one per chart type.** Five chart types tell you nothing new; a
   tooltip on a table and an annotation on a selection list do.
4. **Shell first, chart second.** Land the shared change and let the chart inherit it. Reversed, the chart
   ships a local value that the shared change then has to unpick.

**One risk to carry into implementation:** the card-tooltip ramp was judged on one consumer's content — a
chart data point of two to four lines. If another consumer uses CARD style with a different content shape,
three roles may be right here and wrong there. Not a reason to reopen the decision; a reason rule 1 is a
precondition.

---

## Consumer inventory

Read from a local checkout of `epic-74519` @ `c75c3fabdf64` — a full grep, not a bounded search.

### `wTooltip` — 12 components, and the default class reaches all of them

`tooltip.directive.ts:43` declares `@Input() tooltipCSS = "widget__default-tooltip"`, so **every consumer
that does not override it gets the default class.** Items 1, 3 and 5 therefore reach all of the following:

| Consumer | Where |
|---|---|
| Table | `vs-table.component.html` (2 call sites) |
| Crosstab | `vs-crosstab.component.html` (4 cell sites + drill tip + date-comparison tip) |
| Calc table | `vs-calctable.component.html` (4 call sites) |
| Chart | `vs-chart.component.html` (drill tip, date-comparison tip, title link) |
| Chart plot / axis / legend | `chart-area.component.html` (3 sites — **the only `tooltipCSS` override**) |
| Selection list | `vs-selection.component.html` |
| Range slider | `vs-range-slider.component.html` (both handles) |
| Gauge | `vs-gauge.component.html` |
| Image | `vs-image.component.html` |
| Text | `vs-text.component.html` |
| Annotation | `vs-annotation.component.html` (2 sites) |
| Hidden annotation | `vs-hidden-annotation.component.html` (sets its own `tooltipClass`) |

### The CARD ramp is chart-only — the ramp risk is closed

`widget__card-tooltip` is applied in **exactly one place**: `chart-area.component.ts:369`, gated on
`model.tooltipStyle === "CARD"`. Nothing else in the portal ever sets it.

**So the item 4 risk is resolved.** The three-role ramp was judged on chart data-point content, and chart
data points are its only consumer. It can be implemented as decided, with no survey of other content
shapes. The `.widget__card-tooltip` class name suggests a general-purpose surface; it is not one.

### Annotation overlay — 11 mount sites

`vs-annotation` is mounted by chart, gauge, image, text, line, rectangle, oval, table, crosstab, calc
table, and `vs-object-container` itself. The selection-border change in item 11 reaches every one; the
tables mount it repeatedly, once per cell region.

### The stacking registry is smaller than "registry" suggests — and the bug is worse than recorded

`_directives.scss:20` — the whole shell registry is **two entries**:

```scss
$stacking-order: (".fixed-dropdown", w-tooltip);
@include set-z-index-selector($stacking-order, 999900);
```

So `.fixed-dropdown` is 999900 and `w-tooltip` 999901. (A second `$stacking-order` at line 365 is worksheet
graph thumbnails — unrelated, and the name reuse is itself confusing.)

**This changes item 12's characterisation.** The data tips are not merely "outside the registry" at
`POP_UP_BACKGROUND_ZINDEX = 9996` — they are **two orders of magnitude below it**, so any dropdown or
tooltip renders over a data tip. The `+99998`/`+99999` offsets in `vs-pop-component.directive.ts` are the
workaround for that, which is why they exist at all. Joining the registry is not tidying; it replaces a
pair of magic numbers that only accidentally clear the two things that matter.

---

## Cross-dependency with the chart ticket — one decision, two implementations

The selection vocabulary was decided as a single rule: everything that means "selected" uses the shipped
focus family, and chrome selections are stroke-only at 2px while data selections keep the fill. Its
implementation lands in two places — `ChartTool.drawRegions()` in the chart ticket, and the annotation
border in item 11 below. **They must not drift.** If one ships without the other the product still has two
selection idioms, which is the thing the decision was taken to end.

---

# Part A — tooltip surfaces

## Summary

`scss/internal/_directives.scss` defines three tooltip surfaces. They are already mostly bound to `--inet-*` tokens, but a number of values sit off the scale or express intent in ways the token system now covers.

**Items 1, 2, 3, 5 and 6 are mechanical** — small, independently verifiable, no meaningful layout change. **Item 4 is a design proposal** that changes how the card tooltip looks and needs sign-off; it does not block the others. Read item 4 before treating this ticket as safe cleanup.

**Scope note:** these are shell-wide classes. Changes affect every tooltip in the portal — chart data points, worksheet, binding editor, annotations. That is why this is not part of the chart card spec.

## Files

- `web/projects/portal/src/scss/internal/_directives.scss` — `.widget__default-tooltip`, `.widget__card-tooltip`, `.hidden__annotation-tooltip`
- `web/projects/portal/src/app/widget/tooltip/tooltip.directive.ts` — `offsetTop` / `offsetLeft`
- `web/projects/portal/src/app/widget/tooltip/tooltip.component.scss` — `:host` positioning (no change expected)

Token definitions: `web/projects/portal/src/scss/_variables.scss` `:root`.

## 1. Elevation does not express the layer

Both tooltips use `box-shadow: var(--inet-shadow-low)` — the same elevation the chart card and other in-flow surfaces use. But `w-tooltip` is `position: fixed` at z-index 999900, floating above all content. Dropdowns and modals correctly use `--inet-shadow-overlay` (`_bootstrap-override.scss:557`).

**Change:** `--inet-shadow-low` → `--inet-shadow-overlay` on all three classes.

**Why it matters:** a tooltip carrying the card's own shadow reads as co-planar with the thing it is hovering over, which is exactly wrong for a transient overlay. This is the one item a designer notices immediately.

## 2. Opacity used where the text ramp exists

`.widget__card-tooltip` de-emphasizes its tiers with `opacity: 0.9` (tier 2), `0.7` (tier 3), `0.65` (subtitles).

The system ships a text ramp for this: `--inet-text-color` (#35342F), `--inet-text-muted-color` (#6A685F), `--inet-text-subtle-color` (#99958C).

**Change:** replace the tier opacities with `color: var(--inet-text-muted-color)` / `var(--inet-text-subtle-color)`. If item 4 is accepted, this falls out of it — the roles carry color directly.

**Why it matters:** opacity fades the entire subtree, not just text, so any future border or icon inside a tier fades with it. It also breaks under theming — muted text in a dark theme needs to get *lighter*, not more transparent, and an opacity value cannot express that.

## 3. Two radii are off the scale

The radius scale is `--inet-radius-sm: 2px`, `-md: 3px`, `-lg: 4px`, `-xl: 6px`, `-pill: 999px`.

- `.widget__default-tooltip` and `.hidden__annotation-tooltip`: `border-radius: 1px` — not a scale value.
- `.widget__card-tooltip`: `border-radius: 8px` — not a scale value.

**Change:** `1px` → `var(--inet-radius-sm)` (2px); `8px` → `var(--inet-radius-xl)` (6px). Four call sites in total — the 1px appears in `.widget__default-tooltip`, `.hidden__annotation-tooltip` and `.chart__tooltip` (see item 10), the 8px in `.widget__card-tooltip`.

**Bonus:** 6px is the chart card's own radius, so the card tooltip becomes visibly the same family of surface as the card it belongs to.

## 4. Rebuild the CARD type ramp — DECIDED 2026-08-01, three roles

Unlike items 1–3 and 5–6, this is not a mechanical binding — it changes how the card tooltip looks, so it was held for a design decision. **That decision is made: three roles, no fourth size.** Build as proposed below.

### Current

`.widget__card-tooltip` defines six sizes: `.tt-tier-1` 16px, `.tt-tier-1.tt-subtitle` 14px, `.tt-tier-2` 13px, `.tt-tier-2.tt-subtitle` 12px, `.tt-tier-3` 11px, `.tt-stack-total` 20px. Hierarchy is additionally carried by `opacity: 0.9 / 0.7 / 0.65` and by per-tier margins of 10 / 6 / 4 / 2 / 1px.

### Why it should change

- **Six sizes for at most three levels of meaning**, in a surface holding two to four lines. More type sizes than the chart card spec uses for a whole dashboard component.
- **The steps are too small to read as hierarchy.** 16 → 14 → 13 → 12 → 11 is 2, 1, 1, 1. Below ~14px a 1px difference is not a signal; adjacent 13px and 12px rows read as inconsistency rather than structure.
- **20px is an inversion.** The largest type in the shell is `--inet-dialog-title-font-size` at 14px. A transient hover overlay should not contain the biggest text in the product.
- **Hierarchy is encoded three times** — size, opacity and margin all mark the same tiers. That redundancy is why the rule set needs eight margin overrides and a comment explaining which rule wins on equal specificity. It is fragile by construction.

### Proposed — three roles, three sizes

| Role | Size | Weight | Color |
| --- | --- | --- | --- |
| Value | 16px (`--inet-font-size-lg`, new) | 600 | `--inet-text-color` |
| Label | 13px `--inet-font-size-base` | 400 | `--inet-text-muted-color` |
| Caption | 11px `--inet-feedback-font-size` | 400 | `--inet-text-subtle-color` |

16 / 13 / 11 gives ~1.23 and ~1.18 steps — perceptible without shouting. Drop 14, 12 and 20.

**Subtitles stop being a size.** `.tt-tier-1.tt-subtitle` and `.tt-tier-2.tt-subtitle` are deleted; a subtitle is the caption role, distinguished by the subtle text color. The specificity-ordering comment goes with them.

**The stack total earns emphasis structurally.** Same 16px as any value, separated by `border-top: 1px solid var(--inet-default-border-color)` with `padding-top: var(--inet-space-3)`. Position and rule say "summary"; size does not have to.

**Spacing collapses to two values** — `--inet-space-4` (8px) between sections, `--inet-space-1` (2px) within — replacing 10 / 6 / 4 / 2 / 1px and most of the override rules.

Note this proposal subsumes item 2: the tier opacities disappear because the text ramp carries de-emphasis.

### The one real objection — heard and accepted

**The stack total gets quieter.** 20px punches; a separator plus weight does not, quite. The alternative was a fourth named step for the total.

**Decision: three roles, no fourth size.** The total is 16px like any value, earning its emphasis from the hairline rule and its position rather than its size. Accepted knowingly: a hover panel is not where the product should hold its largest text, and a summary line that has to be bigger than everything else to read as a summary is a layout problem rather than a type problem. If testing on real dashboards shows the total genuinely disappears, the fix is a named step — never an orphan literal.

### Token to add

There is no 16px name. `--inet-font-size-lg: 16px` in `:root` — generally useful, not tooltip-specific. Now a committed addition, not a conditional one.

### Cross-ticket dependency

`Outlined chart text - ticket.md` requires naming a chart type scale before its conversion. **Name one ramp for both** rather than minting two; whoever reaches it first defines it. The 11px caption step is the likely shared floor with chart tick labels. With this item decided, the tooltip side of that ramp is fixed at 16 / 13 / 11 — the outlined-text conversion should adopt those names rather than propose its own.

## 5. Default tooltip font size

`.widget__default-tooltip` and `.hidden__annotation-tooltip` use `font-size: 12px` — no such token. It is body copy on a surface.

**Change:** `12px` → `var(--inet-font-size-base)` (13px). A 1px increase, visually indistinguishable, and it makes the tooltip follow theme changes.

## 6. `.hidden__annotation-tooltip` duplicates `.widget__default-tooltip`

The two rules are identical property-for-property: same color, background, border, `font-size: 12px`, `border-radius: 1px`, `box-shadow`, `padding`, `margin: 3px`, `white-space`, `word-wrap`.

**Change:** extract a shared mixin (or `@extend`) so the two cannot drift.

**Do this first** — otherwise items 1, 3 and 5 each have to be applied twice, and a future fix will inevitably land on only one.

## Smaller notes (optional, same commit)

- **Cursor clearance is two unnamed literals serving one purpose:** `margin: 3px` on the tooltip classes and `offsetTop = 15` / `offsetLeft = 15` in `tooltip.directive.ts`. One token would cover both. `--inet-space-2` (4px) is the nearest scale step for the margin.
- **`letter-spacing: 0.02em`** on the CARD subtitles is a literal; there is no letter-spacing scale, so this may be fine as-is. Moot if item 4 lands — it deletes both subtitle classes; applies only if the proposal is rejected.
- **`max-width: 40vw; max-height: 40vh`** is viewport-relative, so tooltip size is unrelated to the chart it belongs to. Defensible — on a very small chart the tooltip is the only readable output — but worth a deliberate decision rather than an inherited one.

---

## 11. Annotation overlays

`web/projects/portal/src/app/vsobjects/objects/annotation/vs-annotation.component.scss`.

**A third selection idiom.**

```scss
.vs-annotation__rectangle--selected { border: 2px dotted darkgray; }
```

Counting this, the product now marks "selected" three different ways: a canvas fill + stroke in `#dc581e` (item 8), the shipped focus ring, and a dotted `darkgray` border here. **Settled 2026-08-01: one vocabulary, the shipped focus family.** The dotted `darkgray` goes; a selected annotation is stroked in `--inet-primary-color` at 2px like every other chrome selection, solid rather than dotted. Original note: **item 7 should decide one selection vocabulary and this should join it** — a dotted browser-default border is not a deliberate third option.

**Hardcoded text colour.** `.vs-annotation__rectangle-content { color: black; }` — a named colour, so annotation body text cannot follow a theme. Should be `--inet-text-color`.

**A dead-looking rule with a typo.**

```scss
.annotition-button { border: 2px outset #cccccc; }
```

The class name is misspelled ("annotition"), `outset` is a Web-1.0 bevel border style used nowhere else, and `#cccccc` is off-palette. **Check whether anything matches this selector** — the misspelling suggests not, in which case delete it. Same failure mode as the dead menu icons.

**Intentional, leave alone.** `.vs-annotation__line-endpoint { background: transparent; // left as not themeable }` — the comment says so explicitly.

**The annotation Format dialog is clean — checked, nothing to do.** `annotation-format-dialog.component.html` has no stylesheet at all and is composed entirely from shared vocabulary: `<modal-header>`, Bootstrap `.modal-body`/`.modal-footer`/`.btn-primary`, the shell form classes (`shell-form-row--field`, `shell-alert--danger`), `form-floating`, and the shared `style-dropdown` / `color-editor` / `radius-dropdown` / `alpha-dropdown` controls. No hardcoded colour, radius or size. It is already governed by `_bootstrap-override.scss` and `_themeable.scss`.

**So do not confuse the two.** The annotation *dialog* is fine; the annotation *rendering* above is not. The general rule this suggests: a chart surface that goes through the shell's modal chrome is probably aligned, and one painted ad-hoc by chart code probably is not.

Two notes on the dialog, neither worth doing: `data-dismiss="modal"` on Cancel is Bootstrap 4 syntax (BS5 wants `data-bs-dismiss`) so it is inert — `(click)="cancel()"` does the work. And the dialog lets an author set a per-annotation **Round Corner** radius and **Fill Color**: those are author data, not tokens, so they must not be bound to `--inet-radius-*`.

**Structural oddity.** `vs-hidden-annotation.component.scss` contains only the licence header — no rules. The style for that component (`.hidden__annotation-tooltip`) lives in the global `_directives.scss` instead. Item 6 moves that rule into a shared mixin; consider whether the component's own file is where it belongs.

---

## 12. Data tips and pop components

`web/projects/portal/src/app/vsobjects/objects/data-tip/`.

**A second scrim colour.** `date-tip-helper.ts:19`:

```ts
const POP_DIM_COLOR: string = "rgba(0, 0, 0, 0.2)";
```

The system ships `--inet-overlay-scrim-bg-color: rgba(0, 0, 0, 0.3)`. Two scrims for the same job, 0.1 apart — nobody chose that difference. **Bind to the token.**

**Magic z-index arithmetic that bypasses the app's own registry.** `date-tip-helper.ts` declares `POP_UP_BACKGROUND_ZINDEX = 9996` and `getPopUpZIndex()` returns `+1`; `vs-pop-component.directive.ts:314–317` adds `99998` / `99999` to a natural z-index.

Meanwhile `scss/internal/_directives.scss` defines a stacking registry:

```scss
$stacking-order: (".fixed-dropdown", w-tooltip);
@include set-z-index-selector($stacking-order, 999900);
```

So the app **has** a declared layer order, and the pop/data-tip layer sits outside it with hand-computed numbers. This is the highest-value item in Part B: it is why layering bugs in this area are hard to reason about. **Register these layers in `$stacking-order`** rather than adding offsets.

**Named-colour defaults.** `vs-pop-component.directive.ts:42` — `@Input() popBackground: string = "white"`; line 325 uses `"rgba(255,255,255,0)"` where `transparent` is meant. Bind the first to `--inet-dialog-bg-color`, simplify the second.

**Filename typo, cosmetic:** `date-tip-helper.ts` should be `data-tip-helper.ts`. Rename only if it is cheap; not worth churn on its own.

---

## Order of work

Item 6 first (deduplication), then 1, 3, 5 together — all mechanical. Item 2 is subsumed by item 4 and
should not be done separately. **Item 4 is decided but visual** — read `Chart overlay surfaces - decided
visuals.html` §02 before implementing, and do the consumer inventory (rule 1) first. Item 12's z-index
registration is the highest-value single change here: it is a live stacking bug, not a cleanup. Item 11's
selection border must land with the chart ticket's items 7-8, not before or after.

## Verification

Beyond the usual: open a tooltip on a **table** and on a **selection list**, not only on a chart — that is
the whole point of this split. Select an annotation on a chart and on a table and confirm the treatment
matches. Open a drill tip and a date-comparison tip and check they still stack correctly after item 12.
