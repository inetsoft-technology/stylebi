# Chart Card Dark Mode — Browser Surfaces Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give the four chart-card surfaces that live in the browser DOM a dark treatment, so a dark dashboard stops rendering a light toolbar strip and light tooltips over server-darkened chart chrome.

**Architecture:** Phase 9B already darkened every server-rendered visualization surface, including the chart's gridlines, labels, legend and title band. It deliberately left the browser DOM alone except for three named surfaces. This plan follows those three precedents exactly: per-surface rules under `.viz-dark`, no shell neutral redefined. Each surface is one task, independently reviewable and independently shippable.

**Tech Stack:** SCSS, Angular 21 component styles, Vitest (`*.spec.ts`).

## Global Constraints

- Every rule sits under `.viz-dark` or `:host-context(.viz-dark)`. Light-modern and gate-off output must be byte-identical.
- **Do not redefine a shell neutral.** `--inet-text-color`, `--inet-dialog-bg-color`, `--inet-card-bg-color`, `--inet-shell-surface-default` and `--inet-default-border-color` keep their single definitions. Redefining one reaches the entire product, not the chart card.
- Never scope defensively to the chart. `_directives.scss` is product-wide; the tooltip change lands there once.
- Deletions before bindings, inside every task.
- No comments in Angular HTML template files.
- Do not reference ticket numbers, PR numbers, or design-doc filenames in source comments.

## Source of truth

`docs/superpowers/specs/lookfeel/chart-card-open-item-decisions.md` §2, which records the decision and
the four surfaces. Its scoping came from `chart-card-source-doc-corrections.md` §4.1.

## The three precedents to copy

Phase 9B (`3e7e52626`) added exactly three browser-DOM dark rules outside `_viz-tokens.scss`. Read all
three before starting — they are the house style for this work:

```bash
sed -n '1265,1275p' web/projects/portal/src/scss/_themeable.scss
sed -n '34,40p'     web/projects/portal/src/app/vsobjects/objects/output/image/vs-image.component.scss
sed -n '78,95p'     web/projects/portal/src/app/vsobjects/objects/slider/vs-slider.component.scss
```

`vs-slider.component.scss:80` also records the specificity rule this work depends on: the body carries
`viz-modern` and `viz-dark` together, and a single-class `.viz-dark` selector wins on source order when
it follows the `.viz-modern` blocks. Put dark rules after their light-modern counterparts, in the same
file, not in a separate one.

## Palette

Use the neutrals Phase 9B already shipped server-side, so the DOM agrees with the painted output rather
than inventing a second dark palette:

| Role | Value | Where 9B uses it |
|---|---|---|
| Card / strip surface | `#252428` | `VSChartChromeDefaults.LEGEND_BG_DARK` |
| Body text | `#CAC4D0` | `VSChartChromeDefaults.LABEL_DARK`, `VSTitleChromeDefaults.TITLE_FG_DARK` |
| Emphasis text | `#E6E0E9` | `VSChartChromeDefaults.TITLE_DARK` |
| Hairline / border | `#3A383D` | `VSChartChromeDefaults.GRIDLINE_DARK` |

Declare these once, as four `--inet-viz-*` tokens in the existing `.viz-dark` block, rather than
repeating the hexes across four files. That block is at `scss/_viz-tokens.scss:143-159`.

## File structure

| File | Responsibility | Task |
|---|---|---|
| `web/projects/portal/src/scss/_viz-tokens.scss` | Declares the four dark surface tokens in the existing `.viz-dark` block | 1 |
| `web/projects/portal/src/app/vsobjects/objects/mini-toolbar/mini-toolbar.component.scss` | The anchored strip's surface | 1 |
| `web/projects/portal/src/scss/internal/_directives.scss` | All tooltip surfaces, product-wide | 2 |
| `web/projects/portal/src/scss/_themeable.scss` | The chart selection fill and stroke | 3 |
| `web/projects/portal/src/app/graph/objects/chart-nav-bar.component.scss` | The nav bar | 4 |
| `web/projects/portal/src/app/vsobjects/objects/annotation/vs-annotation.component.scss` | The annotation selection border | 4 |

`vs-chart.component.scss` (the Resize Plot sliders) is deliberately **not** in this plan — see the
closing section.

---

### Task 1: The dark tokens and the anchored strip

**Files:**
- Modify: `web/projects/portal/src/scss/_viz-tokens.scss:143-159`
- Modify: `web/projects/portal/src/app/vsobjects/objects/mini-toolbar/mini-toolbar.component.scss:29-42`
- Test: `web/projects/portal/src/app/vsobjects/objects/mini-toolbar/mini-toolbar.component.spec.ts`

**Interfaces:**
- Produces: four custom properties, defined only inside `.viz-dark` — `--inet-viz-surface-dark: #252428`, `--inet-viz-text-dark: #CAC4D0`, `--inet-viz-text-emphasis-dark: #E6E0E9`, `--inet-viz-hairline-dark: #3A383D`. Tasks 2 through 4 consume these by name.

**Background the implementer needs.** The strip's surface is
`background-color: var(--inet-shell-surface-default)` at `mini-toolbar.component.scss:31`, inside a rule
that also sets `width: fit-content !important`. That token has one definition
(`_variables.scss:372`, pointing at `--inet-shell-surface-default`) and no dark variant, which is why the
strip stays light. Override the property on the strip under `.viz-dark`; do not give the token a dark value.

- [ ] **Step 1: Declare the four tokens**

At the end of the existing `.viz-dark` block in `_viz-tokens.scss` (after `--inet-viz-anomaly-bg`, currently line 158):

```scss
  // Chart-card DOM surfaces. Values match the neutrals the server-side resolvers already use for
  // dark, so a painted chart and the chrome around it agree rather than carrying two dark palettes.
  --inet-viz-surface-dark: #252428;
  --inet-viz-text-dark: #CAC4D0;
  --inet-viz-text-emphasis-dark: #E6E0E9;
  --inet-viz-hairline-dark: #3A383D;
```

- [ ] **Step 2: Write the failing test for the strip**

Add to `mini-toolbar.component.spec.ts`:

```typescript
describe("MiniToolbar dark surface", () => {
   afterEach(() => {
      document.body.classList.remove("viz-modern", "viz-dark");
   });

   it("declares a dark surface rule for the strip", () => {
      const styles = Array.from(document.styleSheets)
         .flatMap((sheet) => {
            try {
               return Array.from(sheet.cssRules).map((rule) => rule.cssText);
            }
            catch(e) {
               return [];
            }
         })
         .join("\n");
      expect(styles).toContain("--inet-viz-surface-dark");
   });
});
```

If the component's styles are not reachable from `document.styleSheets` in this runner, drop this test
and rely on Step 6's visual check instead — do not weaken the CSS to make a test pass. Record in the
commit message that the surface is covered visually rather than by unit test.

- [ ] **Step 3: Run it**

Run from `web/`:
```bash
npx ng test portal --include='**/mini-toolbar.component.spec.ts' --watch=false
```
Expected: FAIL — the token is not referenced by the component yet.

- [ ] **Step 4: Give the strip its dark surface**

In `mini-toolbar.component.scss`, after the existing `.viz-modern` strip rules (which end at line 92):

```scss
:host-context(.viz-dark) .mini-toolbar .mini-toolbar-container {
  background-color: var(--inet-viz-surface-dark);
  border-color: var(--inet-viz-hairline-dark);

  button {
    color: var(--inet-viz-text-dark);
  }
}
```

The `& + .mini-toolbar-button-group { border-left: ... }` separator at line 76-78 binds
`--inet-default-border-color`, which stays light. Add it to the block above:

```scss
:host-context(.viz-dark) .mini-toolbar-button-group + .mini-toolbar-button-group {
  border-left-color: var(--inet-viz-hairline-dark);
}
```

- [ ] **Step 5: Run the suite**

Run from `web/`:
```bash
npx ng test portal --include='**/mini-toolbar.component*.spec.ts' --watch=false
```
Expected: PASS.

- [ ] **Step 6: Confirm it visually**

Build and start the server, then in the EM Look and Feel page turn on Modern Visualization and check
Dark Mode. Open a dashboard with a chart at compact density:

```bash
./mvnw clean install -DskipTests && cd docker/target/docker-test && docker compose up -d
```

Check: the strip reads as a dark pill on the dark card, its kebab glyph is legible, and the separator
between button groups is visible but not bright. Then uncheck Dark Mode and confirm the strip is exactly
as it was before this change.

- [ ] **Step 7: Commit**

```bash
git add web/projects/portal/src/scss/_viz-tokens.scss \
        web/projects/portal/src/app/vsobjects/objects/mini-toolbar/mini-toolbar.component.scss \
        web/projects/portal/src/app/vsobjects/objects/mini-toolbar/mini-toolbar.component.spec.ts
git commit -m "feat(vsobjects): darken the anchored mini-toolbar strip

Declares four dark surface tokens matching the neutrals the server-side
resolvers already use, and binds the strip's surface, hairline and glyph
colour to them. The shell neutrals keep their single definitions.

Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>"
```

---

### Task 2: The tooltip surfaces

**Files:**
- Modify: `web/projects/portal/src/scss/internal/_directives.scss:215-229` and the `.widget__card-tooltip` rules that follow it

**Interfaces:**
- Consumes: the four tokens from Task 1.
- Produces: nothing new.

**Background the implementer needs.** `@mixin plain-tooltip-surface` (`:215`) is shared by
`.widget__default-tooltip` and `.hidden__annotation-tooltip`, and it binds `--inet-text-color`,
`--inet-dialog-bg-color`, `--inet-default-border-color` and `--inet-shadow-low`. None of those flip under
`.viz-dark`. `wTooltip` defaults `tooltipCSS` to `widget__default-tooltip`, so this rule reaches **12
consumers** — table, crosstab, calc table, chart, chart plot/axis/legend, selection list, range slider,
gauge, image, text, annotation and hidden annotation. `.widget__card-tooltip` is applied in exactly one
place, `chart-area.component.ts:369`, and has a `--tailed` variant at `:287` that zeroes background,
border and shadow because the SVG tail draws the outline instead.

**Test one instance per consumer, not one per chart type.** Five chart types tell you nothing new; a
tooltip on a table and one on a selection list do.

- [ ] **Step 1: Read the current rule set before editing**

Run from `web/projects/portal/src`:
```bash
sed -n '210,245p;280,320p' scss/internal/_directives.scss
```

Confirm the mixin still holds eleven shared properties and that `max-width`/`max-height` are declared on
`.widget__default-tooltip` only. If that has changed, stop and re-derive — the delta is deliberate and a
previous audit exists specifically because it is easy to flatten by accident.

- [ ] **Step 2: Add the dark rules**

After the last light-mode tooltip rule in the file, add:

```scss
.viz-dark {
  .widget__default-tooltip,
  .hidden__annotation-tooltip,
  .widget__card-tooltip {
    color: var(--inet-viz-text-dark);
    background-color: var(--inet-viz-surface-dark);
    border-color: var(--inet-viz-hairline-dark);
  }

  // The CARD ramp is three roles at 16/13/11. Its title role takes the emphasis neutral so the
  // hierarchy survives dark; body and caption stay on the body neutral set above.
  .widget__card-tooltip .tt-tier-1 {
    color: var(--inet-viz-text-emphasis-dark);
  }

  // The tailed card tooltip draws its own outline in SVG, so it must keep the flattened background,
  // border and shadow it has in light mode — re-asserted here because the rule above would restore them.
  .widget__card-tooltip--tailed {
    background-color: transparent;
    border-color: transparent;
    box-shadow: none;
  }
}
```

The `--tailed` re-assertion is not optional. Without it the dark background rule wins on source order
and puts a filled box behind the SVG tail.

- [ ] **Step 3: Check the SVG tail's own fill**

The tail is drawn in `widget/tooltip/tooltip.component.scss` and `tooltip.component.html`. Run:

```bash
grep -n "fill\|stroke\|--inet-" app/widget/tooltip/tooltip.component.scss
```

If the tail path's fill is a shell token or a literal, it needs the same dark treatment as the tooltip
body or the tail will be light against a dark bubble. Add it to the `.viz-dark` block in
`tooltip.component.scss`, bound to `--inet-viz-surface-dark`, with its border on
`--inet-viz-hairline-dark`.

- [ ] **Step 4: Confirm it visually across three consumers**

With Modern Visualization and Dark Mode on, hover in turn over: a chart data point (the CARD ramp, with
the tail), a table cell (the default tooltip), and a selection list item. All three must read dark. Then
open a hidden annotation and confirm it also reads dark and has **not** acquired a `40vw` width cap.

- [ ] **Step 5: Confirm light mode is untouched**

Uncheck Dark Mode. All four tooltips must be pixel-identical to before this change. Compare against a
screenshot taken before Step 2.

- [ ] **Step 6: Commit**

```bash
git add web/projects/portal/src/scss/internal/_directives.scss \
        web/projects/portal/src/app/widget/tooltip/tooltip.component.scss
git commit -m "feat(shell): darken the tooltip surfaces under the dark gate

Covers the default, hidden-annotation and card tooltips, and re-asserts the
tailed variant's flattened background so the SVG tail keeps drawing the
outline. Reaches all twelve wTooltip consumers, as the class always has.

Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>"
```

---

### Task 3: The chart selection fill

**Files:**
- Modify: `web/projects/portal/src/scss/_themeable.scss:1408-1413`
- Test: `web/projects/portal/src/app/graph/model/chart-tool-selection.spec.ts`

**Interfaces:**
- Consumes: nothing from earlier tasks. This surface keeps the primary accent rather than taking a neutral.
- Produces: nothing new.

**Background the implementer needs.** `ChartTool.drawRegions()` reads `color` as its fill and
`border-color` as its stroke from the computed style of `.chart-object-canvas`. The modern rule at
`_themeable.scss:1410` binds them to `--inet-focus-ring-color` and `--inet-primary-color`.
`--inet-focus-ring-color` is `rgba(229, 138, 42, 0.28)` — the primary at 28%.

**This is the one surface that does not simply take a dark neutral.** Primary is a brand accent and stays
primary in dark. The problem is the alpha: 28% was calibrated as a wash over a *light* plot. Over
`#252428` the same wash is much weaker.

- [ ] **Step 1: Measure before changing anything**

With Modern Visualization and Dark Mode on, select a bar, an axis label and the legend on a chart.
Screenshot each. Judge whether the 28% fill reads at all against the dark plot. **If it reads correctly,
stop here: skip the rest of this task entirely, change nothing, and record in
`chart-card-roadmap.md` that the selection fill needed no dark treatment.** Do not change a value that
is already correct — the fill is a brand accent, not a neutral, and the only open question is its alpha.

- [ ] **Step 2: Write the failing test**

Only if Step 1 showed the fill is too weak. Add to `chart-tool-selection.spec.ts`:

```typescript
describe("selection fill under dark", () => {
   afterEach(() => {
      document.body.classList.remove("viz-modern", "viz-dark");
   });

   it("uses a stronger fill alpha under dark than under light", () => {
      document.body.classList.add("viz-modern");
      const light = getComputedStyle(document.body).getPropertyValue("--inet-viz-selection-fill");
      document.body.classList.add("viz-dark");
      const dark = getComputedStyle(document.body).getPropertyValue("--inet-viz-selection-fill");
      expect(dark).not.toBe(light);
   });
});
```

- [ ] **Step 3: Run it**

Run from `web/`:
```bash
npx ng test portal --include='**/chart-tool-selection.spec.ts' --watch=false
```
Expected: FAIL — both reads return the same empty string, since the property does not exist.

- [ ] **Step 4: Add the dark alpha**

In `_viz-tokens.scss`, add `--inet-viz-selection-fill: var(--inet-focus-ring-color);` to the
`.viz-modern` block and `--inet-viz-selection-fill: rgba(229, 138, 42, 0.42);` to the `.viz-dark` block.
Then in `_themeable.scss`, after the existing `.viz-modern .chart-object-canvas` rule:

```scss
.viz-dark .chart-object-canvas {
  color: var(--inet-viz-selection-fill);
}
```

Leave `border-color` alone — the stroke is the full-strength primary and reads correctly against dark.
Use the alpha Step 1 showed to be right, not 0.42 if the measurement disagreed; 0.42 is the starting
point, not the answer.

- [ ] **Step 5: Run the suite and confirm the fill visually**

Run from `web/`:
```bash
npx ng test portal --include='**/chart-tool-selection.spec.ts' --watch=false
```
Expected: PASS. Then re-take the three screenshots from Step 1 and confirm the selection reads clearly
without flooding the mark.

- [ ] **Step 6: Commit**

```bash
git add web/projects/portal/src/scss/_viz-tokens.scss \
        web/projects/portal/src/scss/_themeable.scss \
        web/projects/portal/src/app/graph/model/chart-tool-selection.spec.ts
git commit -m "feat(chart): strengthen the selection fill alpha under dark

The fill stays the primary accent; only its alpha changes. 28% was
calibrated as a wash over a light plot and reads as nearly nothing over the
dark one. The stroke is unchanged.

Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>"
```

---

### Task 4: The nav bar and the annotation selection border

**Files:**
- Modify: `web/projects/portal/src/app/graph/objects/chart-nav-bar.component.scss:18-35`
- Modify: `web/projects/portal/src/app/vsobjects/objects/annotation/vs-annotation.component.scss:98-105`

**Interfaces:**
- Consumes: the four tokens from Task 1.
- Produces: nothing new.

**Background the implementer needs.** The nav bar is the zoom/pan control that floats over a chart. It
carries two known defects this task should fix while it is open, both recorded as outstanding: an
off-scale `border-radius: 5px` at `:26` and `:32`, and `z-index: 9999` at `:20`. The annotation
component already has a `:host-context(.viz-modern)` block at `:98` that sets the selection border to
`2px solid var(--inet-primary-color)`; that border is correct in dark and needs no change. What needs
checking is the annotation *body* text and background, which the same commit bound to
`--inet-text-color`.

- [ ] **Step 1: Fix the two nav bar defects first**

In `chart-nav-bar.component.scss`, replace both `border-radius: 5px` with `var(--inet-radius-sm)`, and
replace `z-index: 9999` with a registry-declared layer. Check what the shell registry offers:

```bash
grep -n "stacking-order" -A 8 web/projects/portal/src/scss/internal/_directives.scss | head -20
```

If no existing layer fits a chart-local floating control, leave the `z-index` alone and note it — adding
a registry entry for one control is a larger change than this task, and a wrong layer is worse than a
high number. Deletions and corrections before bindings.

- [ ] **Step 2: Give the nav bar its dark surface**

At the end of `chart-nav-bar.component.scss`:

```scss
:host-context(.viz-dark) {
  .chart-nav-bar {
    background-color: var(--inet-viz-surface-dark);
    border-color: var(--inet-viz-hairline-dark);
    color: var(--inet-viz-text-dark);
  }
}
```

Use the actual class names from the file's existing rules rather than `.chart-nav-bar` if they differ —
read the top of the file first.

- [ ] **Step 3: Check the annotation body against dark**

Run from `web/projects/portal/src`:
```bash
sed -n '90,115p' app/vsobjects/objects/annotation/vs-annotation.component.scss
```

The `.viz-modern` block binds annotation body text to `--inet-text-color`, which does not flip. Add to
the same file:

```scss
:host-context(.viz-dark) .vs-annotation__content {
  color: var(--inet-viz-text-dark);
  background-color: var(--inet-viz-surface-dark);
}
```

Use the real class name for the annotation body from the file. Leave
`.vs-annotation__rectangle--selected` alone: its `2px solid var(--inet-primary-color)` is the shared
selection vocabulary and reads correctly on dark.

- [ ] **Step 4: Confirm all three visually**

With Modern Visualization and Dark Mode on: open a map or zoomable chart and confirm the nav bar reads
dark with a legible glyph; add an annotation to a chart and to a table and confirm both read dark with
the same primary selection border; confirm the nav bar's corners now match the shipped radius scale.

- [ ] **Step 5: Confirm light mode is untouched**

Uncheck Dark Mode. The nav bar and annotations must be identical to before, apart from the intended
`border-radius` correction.

- [ ] **Step 6: Commit**

```bash
git add web/projects/portal/src/app/graph/objects/chart-nav-bar.component.scss \
        web/projects/portal/src/app/vsobjects/objects/annotation/vs-annotation.component.scss
git commit -m "feat(chart): darken the nav bar and annotation body

Also brings the nav bar's off-scale 5px corner onto the shipped radius
scale. The annotation's selection border is unchanged: the primary stroke is
the shared selection vocabulary and reads correctly on dark.

Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>"
```

---

## What this plan does not do

- **The Resize Plot sliders are excluded.** `vs-chart.component.scss` is untouched since 2024, holds
  eleven `#ff8d41` literals and fifteen dead IE11 lines, and has its own outstanding ticket that deletes
  roughly half the file before retokenizing anything. Adding a dark rule on top of that would be
  retokenizing before deleting, which the standing rule forbids. Do that ticket, then give it dark.
- **No shell neutral gains a dark value.** If a surface here needs one, that is a shell decision with
  product-wide reach, and it should be raised rather than made inside a chart-card change.
- **No export or server work.** Every surface here is live-view only. There is no parity pass to budget.
