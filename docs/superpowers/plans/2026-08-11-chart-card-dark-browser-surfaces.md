# Chart Card Dark Mode — Browser Surfaces Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Re-scoped 2026-08-27, against `ffae8a1dd`.** The plan was written on 2026-08-11 and four of its
assumptions have changed under it, one of them by measurement rather than by a later commit. Nothing was
repaired in place; the tasks below are re-derived, and what changed is listed here so an implementer can
tell a correction from a rewrite:

- **Task 1's subject is now the *floating* strip, not the anchored one.** §10.2 (`ffae8a1dd`) deleted the
  anchored strip's fill, border and radius outright and derives its glyph ink from the resolved
  background — folding in the title lane, the card, the canvas and `vizDark` — so there is no anchored
  surface left to darken. What still draws a light pill under dark is the floating strip, on the three
  types that get a strip but are not anchored.
- **Task 2's scope class was wrong.** Tooltips are global overlays outside every assembly wrapper, and
  their light-modern gate is `.viz-shell` (`_directives.scss:316`), not `.viz-modern`. The dark
  counterpart is therefore `.viz-shell-dark`, the body class, not `.viz-dark`, the wrapper class. A
  `.viz-dark` rule would never match a tooltip.
- **Task 2's card ramp grew from one foreground to three.** The `.viz-shell` block now binds the CARD
  ramp's three roles to `--inet-text-color`, `--inet-text-muted-color` and `--inet-text-subtle-color`
  (`:349-390`). None of the three flips under dark, so dark needs three values, not the single
  `.tt-tier-1` override the plan carried. That adds a fifth token.
- **Task 3 is closed as a no-op, by measurement.** Its premise — that the selection wash was calibrated
  over a light plot and reads weaker over a dark one — is false. Measured, the wash reads *stronger*
  against the dark card than the light one (1.63:1 vs 1.29:1) and its stroke more than doubles (5.88:1 vs
  2.62:1). Task 3's own Step 1 says to stop and change nothing in that case. See the task.
- **The annotation is out of scope, with a reason.** Its rectangle background is a server-painted image
  seeded to `0xfef7e0` (`VSAnnotationService:55`), a pale cream that does not follow the dark flag, so
  its dark body text is correct on it and a CSS background would fight the painted fill. Making the
  annotation dark is server work, not a browser surface.

**Corrected 2026-08-27 after the browser pass, and this is the important correction in this file.**
Task 2 originally scoped the tooltips to `.viz-shell-dark`, the body class, and reported that as a fix
to the plan's own `.viz-dark` mistake. Both were wrong, for the same underlying reason. A tooltip is
reparented to `document.body`, so it has no assembly ancestor at all — but the body class follows the
live org `viewsheet.darkMode` property while an assembly's palette follows its **seed mark**, and those
two disagree in both directions. Observed on a real dashboard:

| org `darkMode` | assembly mark | tooltip was | should be |
|---|---|---|---|
| off | `MODERN_DARK` | light | dark |
| on | `MODERN_DARK` | dark | dark |
| on | `MODERN_LIGHT` | **dark** | light |

So the shell can never answer this question. `TooltipDirective` now resolves it per tooltip from the
hovered element's own assembly (`resolveDark()`, reading `closest(".viz-modern, .viz-dark")`) and stamps
`widget__tooltip--dark` on the box plus `tooltip-chrome--dark` on the SVG chrome; the CSS keys off those
instead of a shell class. Falling back to the shell when there is no wrapper keeps org-level surfaces
(worksheet detail pane, combo-box list) behaving as before.

This is the [decisions](./chart-card-open-item-decisions.md) R17/R21 deferral being cashed in: that entry
says page-level overlays follow the org and to "revisit only if a mixed dashboard makes it visible." It
became visible in both directions, so it was revisited. **The scrim and the data-tip offsets are NOT
included** — they remain org-scoped, because a 4px offset genuinely is the "inventing precision" case
that entry describes, while a palette is not.

One consequence worth knowing: the five `--inet-viz-*-dark` tokens moved from the `.viz-dark` /
`.viz-shell-dark` block up to bare `:root`. A body-level tooltip sits outside every dark scope, so it
could not resolve them where they were. That matches the house pattern already used for the `-dark` state
colours a few lines above them.

A second defect found in the same pass, fixed and tracked here because it surfaced with this work but is
**not** a regression from it: selection list and tree **cell text** rendered near-black on the dark
surface. `SelectionBaseVSAssemblyInfo.setDefaultFormat` hardcodes `setForegroundValue("0x2b2b2b")` on the
DEFAULT tier — an unconditional creation default, the category `seedChromeDefaults` is documented never to
touch — and the browser binds it as an inline style, so no stylesheet could reach it. Fixed server-side in
`applyDarkForeground`, the mechanism 9B built for exactly this and had wired to only one surface, the slider.

**Corrected again the same day: one read point was not enough, and the title is the precedent for why.**
The first attempt substituted only at the live-model boundary (`SelectionListModel.getFormats()`), and export
parity was written up as a deliberate exclusion. That was wrong on both counts. `VSTitleChromeDefaults` is the
model to copy, and what makes it correct is not the clone-at-read-time shape — which the first attempt already
had — but that it is applied at **every** read point: the live model, the export painters, the print-layout
converter, and `FormatPainterService`, the composer's format picker. Miss that last one and the format pane
shows the stored near-black while the canvas, the viewer and the export all draw light text, which is a
design-time WYSIWYG break rather than a cosmetic gap. The substitution now runs at all five:

| Read point | Where |
|---|---|
| Browser model | `SelectionListModel.getFormats()` — covers list and tree, since `CompositeSelectionValueModel` builds a nested `SelectionListModel` per level |
| PDF, SVG/PNG, and the tree in every format | `VSSelectionListHelper.getValueFormat()`, which gained a `VizContext` parameter — the shared chokepoint all three callers route through |
| HTML | `HTMLSelectionListHelper` / `HTMLSelectionTreeHelper`, which bypass `getValueFormat` and emit CSS from `svalue.getFormat()` directly |
| PPT | `PPTSelectionListHelper`, which passes the assembly context: the slide takes the viewsheet background, and that *is* seeded dark |
| Excel | **deliberately legacy** — `ExcelSelectionListHelper` / `ExcelSelectionTreeHelper` pass a null-mark context. A spreadsheet has no page to paint and a selection list never seeds a dark card background the way a chart or table does, so its cells are unfilled white and the light neutral would be invisible on them |
| Composer format picker | `FormatPainterService`, via the new `applyDarkForegroundInPlace` — the format there is already a private clone |

Two ordering details that are load-bearing. The substitution runs **before** `getValueFormat`'s existing
dimming, not after: dimming writes the USER tier and the substitution yields to it, so an excluded or
unselected value keeps its grey — substituting afterwards would repaint a dimmed value as ordinary text and
lose the "not in this selection" signal. And the tree helper passes `sv.getFormat()` **uncloned**, so the
clone-returning `applyDarkForeground` is required there and the in-place variant must never be used on a
format reached from an assembly's `FormatInfo`.

**A code review after the manual passes then corrected four things in this fix; three were defects it would
have shipped.**

1. **A build break.** `getValueFormat` gained its `VizContext` parameter with no overload left behind, and
   three callers in `utils/inetsoft-xml-formats` — the Excel list, Excel tree and PPT helpers — still passed
   three arguments. They were missed because the search for callers covered `core/` only. **Maven hid it:**
   the incremental compiler skips sources whose own timestamps are unchanged, so a 77-module
   `install -DskipTests` reported BUILD SUCCESS while a clean compile of that module failed. Verify a
   cross-module signature change with `clean`, never an incremental build.
2. **The print-layout substitution was wrong and is reverted.** It is the one renderer that does *not* paint
   the dark viewsheet background: `setUserBackground` is called only when the resolved background differs
   from the DEFAULT tier, and the dark page colour is written *on* that tier, so the report page stays white.
   Light ink there is less readable than the near-black it replaced, not more.
3. **The composer branch caught the measure sub-paths.** Measure Text, Measure Bar and Measure Bar(-) all
   carry `TableDataPath.DETAIL` as their type, distinguished only by one path element — and for the two bars
   the DEFAULT-tier foreground *is* the bar colour. A type-only test showed the picker a light grey where the
   canvas draws the categorical palette or the negative-bar red: the same mismatch, one control over. The
   predicate now tests for an empty path array rather than naming the three, because the existing
   `isMeasureTextBar` already misses Measure Bar(-).
4. **The tooltip shell fallback is gone.** `resolveDark()` fell back to the body `viz-shell-dark` when the
   hovered element sat in no assembly. But that class redefines only the `--inet-viz-*` state tokens and
   paints no surface, so the shell's own surfaces — repository and asset trees, query panes, the worksheet
   detail pane, combo-box lists — stay light in a dark org, and a dark tooltip over them was the mismatch
   moved somewhere else. No wrapper now means light, which also settles the unmarked-assembly case.

One finding was examined and left alone: the `contentIsTemplate()` branch never receives the dark class.
True, but no caller in the codebase passes a `TemplateRef` to `wTooltip`, and giving that branch a
`widget__default-tooltip` surface would change its light rendering on a guess about a consumer that does not
exist.

**Goal:** Give the chart-card surfaces that live in the browser DOM a dark treatment, so a dark dashboard
stops rendering light tooltips and a light floating strip over server-darkened chart chrome.

**Architecture:** Phase 9B already darkened every server-rendered visualization surface, the chart's
gridlines, labels, legend and title band included. It deliberately left the browser DOM alone except for
three named surfaces. This plan follows those three precedents exactly: per-surface rules under the dark
scope class, no shell neutral redefined. Each surface is one task, independently reviewable and
independently shippable.

**Tech Stack:** SCSS, Angular 21 component styles, Vitest (`*.spec.ts`, `*.tl.spec.ts`).

## Global Constraints

- Every rule sits under `.viz-dark` / `.viz-shell-dark` (or `:host-context()` of one). Light-modern and
  gate-off output must be byte-identical — the diff is the proof, and it is checkable by eye.
- **Nothing may put a fill, border or radius back on the anchored strip.** `ffae8a1dd` removed all three
  so an author-set card colour shows through, and every rule it added requires both `[data-tone]` and
  `.mini-toolbar--anchored`. A dark rule on `.mini-toolbar-container` must exclude the anchored path
  explicitly with `:not(.mini-toolbar--anchored)` rather than rely on specificity or source order.
- **Do not redefine a shell neutral.** `--inet-text-color`, `--inet-text-muted-color`,
  `--inet-text-subtle-color`, `--inet-dialog-bg-color`, `--inet-shell-surface-default` and
  `--inet-default-border-color` keep their single definitions. Redefining one reaches the entire product,
  not the chart card.
- Never scope defensively to the chart. `_directives.scss` is product-wide; the tooltip change lands
  there once.
- Deletions before bindings, inside every task.
- No comments in Angular HTML template files.
- Do not reference ticket numbers, PR numbers, or design-doc filenames in source comments.

## What jsdom can and cannot verify here

Every change in this plan is a CSS declaration, and `cssstyle` leaves `var()` unexpanded, so
`getComputedStyle` cannot read any of these values back — already documented in
`mini-toolbar.component.tl.spec.ts:175-183` and `:340-350`. The house pattern for a CSS-only rule is
therefore: assert the declaration survives into the compiled stylesheet, assert the *scoping* that makes
it correct, and argue the rendered result by CSS reasoning in the report rather than dressing it as a
passing behavioural test. Global stylesheets (`_viz-tokens.scss`, `_directives.scss`) are not loaded into
TestBed at all, so for those the diff plus the browser pass is the whole verification. Do not weaken a
rule to make a test readable.

## Source of truth

`docs/superpowers/specs/lookfeel/chart-card-open-item-decisions.md` §2, which records the decision and
the four surfaces it named. Its scoping came from `chart-card-source-doc-corrections.md` §4.1. Two of
those four have since been answered elsewhere — the strip by `ffae8a1dd`, the selection fill by the
measurement in Task 3.

## The two dark scope classes, and which surface takes which

| Class | Where it is set | Surfaces |
|---|---|---|
| `.viz-dark` | the assembly wrapper and the `<mini-toolbar>` host (`vs-object-container.component.html:46,348,400`) | anything inside one assembly: the strip, the nav bar |
| `.viz-shell-dark` | `<body>`, by the portal, composer and viewer app components | anything in a body-level overlay: every tooltip |

`_viz-tokens.scss:155-156` already declares its dark block against both selectors, so a token declared
there is available to either scope.

## Palette

Use the neutrals Phase 9B already shipped server-side, so the DOM agrees with the painted output rather
than carrying a second dark palette:

| Role | Value | Where it comes from | On `#252428` |
|---|---|---|---|
| Card / strip / tooltip surface | `#252428` | `VSChartChromeDefaults.LEGEND_BG_DARK`, `VSObjectChromeDefaults.CARD_BG_DARK` | — |
| Emphasis text | `#E6E0E9` | `VSChartChromeDefaults.TITLE_DARK` | 11.90:1 |
| Body text | `#CAC4D0` | `VSChartChromeDefaults.LABEL_DARK` | 9.05:1 |
| Caption text | `#938F99` | **new** — see below | 4.87:1 |
| Hairline / border | `#3A383D` | `VSChartChromeDefaults.GRIDLINE_DARK` | 1.33:1, correct for a hairline |

**Why a fifth value, and why this one.** The light text ramp is three steps —
`$shell-text-default #35342F` (12.47:1 on white), `$shell-text-muted #6A685F` (5.59:1),
`$shell-text-subtle #99958C` (2.99:1) — and the CARD tooltip binds all three. 9B named only two dark
neutrals, because the server-rendered chrome it darkened uses only two. `#938F99` is the same
Material dark family every other value here comes from (`#1C1B1F`, `#252428`, `#49454F`, `#CAC4D0`,
`#E6E0E9` are all from it) at the step below body text. At 4.87:1 it is *more* legible than the light
subtle it mirrors, which is the right direction to err for an 11px caption.

Declare all five as `--inet-viz-*-dark` custom properties in the existing dark block rather than
repeating hexes across four files.

---

### Task 1: The dark tokens and the floating strip

**Files:**
- Modify: `web/projects/portal/src/scss/_viz-tokens.scss` (the `.viz-dark, .viz-shell-dark` block, `:155-172`)
- Modify: `web/projects/portal/src/app/vsobjects/objects/mini-toolbar/mini-toolbar.component.scss`
- Test: `web/projects/portal/src/app/vsobjects/objects/mini-toolbar/mini-toolbar.component.tl.spec.ts`

**Interfaces:**
- Produces: five custom properties, defined only inside the dark block — `--inet-viz-surface-dark: #252428`, `--inet-viz-text-emphasis-dark: #E6E0E9`, `--inet-viz-text-dark: #CAC4D0`, `--inet-viz-text-subtle-dark: #938F99`, `--inet-viz-hairline-dark: #3A383D`. Tasks 2 and 4 consume these by name.

**Background the implementer needs.** `.mini-toolbar-container` takes
`background-color: var(--inet-shell-surface-default)`, `border: 1px solid var(--inet-default-border-color)`
and `border-radius: var(--inet-radius-xl)` unconditionally at `mini-toolbar.component.scss:30-32`, and
the group separator takes `border-left: 1px solid var(--inet-default-border-color)` at `:76-78`. None of
those tokens has a dark variant, which is why the strip stays light.

`ffae8a1dd` overrides all three to transparent/none/0 for the anchored path only — every one of its rules
requires `[data-tone]` on the host **and** `.mini-toolbar--anchored` on the inner div. So the base rules
at `:30-32` still draw for the floating path, and that is the surface this task darkens.

**Which types actually reach the floating path.** `hasMiniToolbar()`
(`mini-toolbar.service.ts:324-335`) admits nine types; `ANCHORED_ASSEMBLY_TYPES` (`:41-53`) claims six.
The difference is the floating population: **VSCalendar, VSSelectionContainer, and VSRangeSlider when it
is an adhoc filter.** An anchored type whose lane is under 24px is *not* in it — post-L″ it draws no
chrome at all rather than falling back to floating.

- [ ] **Step 1: Declare the five tokens**

At the end of the existing dark block in `_viz-tokens.scss`, after `--inet-viz-anomaly-bg`:

```scss
  // Chart-card DOM surfaces. Values match the neutrals the server-side resolvers already use for
  // dark, so a painted chart and the chrome around it agree rather than carrying two dark palettes.
  // The caption step has no server counterpart: the painted chrome needs only two text tiers.
  --inet-viz-surface-dark: #252428;
  --inet-viz-text-emphasis-dark: #E6E0E9;
  --inet-viz-text-dark: #CAC4D0;
  --inet-viz-text-subtle-dark: #938F99;
  --inet-viz-hairline-dark: #3A383D;
```

- [ ] **Step 2: Give the floating strip its dark surface**

At the end of `mini-toolbar.component.scss`, after the `[data-tone]` rules so the anchored overrides are
never the later declaration:

```scss
:host-context(.viz-dark) .mini-toolbar-container:not(.mini-toolbar--anchored) { ... }
```

The real selector depends on which element carries `.mini-toolbar--anchored` — it is the inner
`.mini-toolbar` div, not the container — so the exclusion has to be written against that ancestor:

```scss
// The floating strip keeps a surface of its own: it overlays the assembly rather than sitting in an
// author-coloured lane, so it has no background to borrow. The anchored path is excluded explicitly
// -- it deliberately draws no fill, border or radius at all, and a colour reaching it would put the
// pill back over a card the author owns.
:host-context(.viz-dark) .mini-toolbar:not(.mini-toolbar--anchored) .mini-toolbar-container {
  background-color: var(--inet-viz-surface-dark);
  border-color: var(--inet-viz-hairline-dark);

  button {
    color: var(--inet-viz-text-dark);
  }
}

:host-context(.viz-dark) .mini-toolbar:not(.mini-toolbar--anchored)
  .mini-toolbar-button-group + .mini-toolbar-button-group {
  border-left-color: var(--inet-viz-hairline-dark);
}
```

- [ ] **Step 3: Pin the scoping in the TL suite**

The values are unreadable in jsdom; the scoping is not. Add one test to
`mini-toolbar.component.tl.spec.ts` asserting the compiled stylesheet carries the dark rule **and** that
its selector excludes the anchored path — that exclusion is the load-bearing half, and dropping it
silently re-fills the anchored strip and undoes `ffae8a1dd`.

Run from `web/`:
```bash
npx ng test portal:test-tl --include='**/mini-toolbar.component.tl.spec.ts' --watch=false
```

- [ ] **Step 4: Run the component's unit suite**

```bash
npx ng test portal --include='**/mini-toolbar.component.spec.ts' --watch=false
```
Expected: PASS, unchanged — no TS was touched.

- [ ] **Step 5: Confirm it visually**

With Modern Visualization and Dark Mode on, hover a **calendar** and a **selection container** (the
floating population), and confirm the pill reads as a raised dark surface against the dark page with a
legible glyph and a visible-but-quiet separator. Then hover a **chart** and confirm the anchored strip is
still surfaceless — if a pill has come back, Step 2's `:not()` is wrong.

- [ ] **Step 6: Commit**

```
feat(vsobjects): darken the floating mini-toolbar strip
```

---

### Task 2: The tooltip surfaces

**Files:**
- Modify: `web/projects/portal/src/scss/internal/_directives.scss`
- Modify: `web/projects/portal/src/app/widget/tooltip/tooltip.component.scss`

**Interfaces:**
- Consumes: the five tokens from Task 1.
- Produces: nothing new.

**Background the implementer needs.** `@mixin plain-tooltip-surface` (`:215-228`) binds
`--inet-text-color`, `--inet-dialog-bg-color`, `--inet-default-border-color` and `--inet-shadow-low`, and
is included by `.widget__default-tooltip` (`:234`) and `.hidden__annotation-tooltip` (`:312`).
`.widget__card-tooltip` (`:240`) does not use the mixin and declares its own three. None of those tokens
flips under dark. `wTooltip` defaults `tooltipCSS` to `widget__default-tooltip`, so the default rule
reaches twelve consumers — table, crosstab, calc table, chart, chart plot/axis/legend, selection list,
range slider, gauge, image, text, annotation and hidden annotation.

Two things that will break the change if missed:

1. **The gate is `.viz-shell`, so the dark scope is `.viz-shell-dark`.** These are global classes on a
   body-level overlay, not inside an assembly wrapper, which is why the light-modern rules at `:316` use
   a descendant selector on the shell class rather than `:host-context`. `.viz-dark` cannot match here.
2. **The CARD ramp is three roles, each on its own shell neutral** (`:349-390`): value on
   `--inet-text-color`, label on `--inet-text-muted-color`, caption — tier 3 and every subtitle — on
   `--inet-text-subtle-color`. All three need a dark counterpart or the ramp collapses to one tone.

And one that will produce a visible defect: `.widget__card-tooltip--tailed` (`:305`, re-asserted under
the gate at `:401`) zeroes background, border and shadow because the SVG chrome draws the outline
instead. A dark `background-color` on `.widget__card-tooltip` wins over it on source order and puts a
filled box behind the tail.

- [ ] **Step 1: Read the current rule set before editing**

```bash
sed -n '215,260p;300,320p;340,406p' web/projects/portal/src/scss/internal/_directives.scss
```

Confirm the mixin still holds eleven properties, that `max-width`/`max-height` are declared on
`.widget__default-tooltip` only, and that the `.viz-shell` CARD block still binds exactly three
foregrounds. If any of that has changed, stop and re-derive — the deltas are deliberate and a previous
audit exists specifically because they are easy to flatten by accident.

- [ ] **Step 2: Add the dark rules**

After the `.viz-shell` block closes (`:406`):

```scss
// Dark scope for the same three surfaces the block above gates. These are body-level overlays, so the
// class is the shell's, not the per-assembly one, and the values are the neutrals the server-side
// resolvers use for dark so a tooltip agrees with the chart it describes.
.viz-shell-dark {
  .widget__default-tooltip,
  .hidden__annotation-tooltip,
  .widget__card-tooltip {
    color: var(--inet-viz-text-dark);
    background-color: var(--inet-viz-surface-dark);
    border-color: var(--inet-viz-hairline-dark);
  }

  // The CARD ramp's three roles, in the same order as the light block: value, label, caption. Dark
  // needs all three -- the shell text neutrals they bind do not flip.
  .widget__card-tooltip {
    .tt-tier-1,
    .tt-stack-total {
      color: var(--inet-viz-text-emphasis-dark);
    }

    .tt-tier-2 {
      color: var(--inet-viz-text-dark);
    }

    .tt-tier-3,
    .tt-subtitle,
    .tt-tier-1.tt-subtitle,
    .tt-tier-2.tt-subtitle {
      color: var(--inet-viz-text-subtle-dark);
    }

    .tt-stack-total {
      border-top-color: var(--inet-viz-hairline-dark);
    }
  }

  // The tailed card's SVG chrome draws the outline, so it must stay flat -- the fill above would
  // otherwise put a box behind the tail.
  .widget__card-tooltip--tailed {
    background-color: transparent;
    border-color: transparent;
    box-shadow: none;
  }
}
```

The `--tailed` re-assertion is not optional.

- [ ] **Step 3: Darken the SVG tail**

`tooltip.component.scss:36-45` fills `.tooltip-chrome__bg` and `.tooltip-chrome__tail` with
`--inet-dialog-bg-color` and strokes `.tooltip-chrome__border`/`__tail` with
`--inet-default-border-color`. Both stay light, so a dark bubble would keep a light tail. Add at the end
of that file:

```scss
:host-context(.viz-shell-dark) {
  .tooltip-chrome__bg,
  .tooltip-chrome__tail {
    fill: var(--inet-viz-surface-dark);
  }

  .tooltip-chrome__border,
  .tooltip-chrome__tail {
    stroke: var(--inet-viz-hairline-dark);
  }
}
```

Leave the `drop-shadow` filter alone: it is two low-alpha blacks, which read as depth on a dark surface
as well as a light one, and replacing it is a shadow-token decision with product-wide reach.

- [ ] **Step 4: Confirm it visually across three consumers**

With Modern Visualization and Dark Mode on, hover in turn: a chart data point (the CARD ramp, with the
tail — check the tail is dark and that no filled box appears behind it), a table cell (the default
tooltip), and a selection list item. Then open a hidden annotation and confirm it reads dark and has
**not** acquired a `40vw` width cap. Confirm the CARD ramp still reads as three tiers, not one.

- [ ] **Step 5: Confirm light mode is untouched**

Uncheck Dark Mode. All four tooltips must be identical to before. Every rule added in this task is inside
`.viz-shell-dark` or `:host-context(.viz-shell-dark)`, so the diff is the argument; the screenshot is the
check.

- [ ] **Step 6: Commit**

```
feat(shell): darken the tooltip surfaces under the dark gate
```

---

### Task 3: The chart selection fill — CLOSED, no change needed

**Measured 2026-08-27. This task ships nothing.** Its own Step 1 said: *"If it reads correctly, stop
here: skip the rest of this task entirely, change nothing."* It reads correctly.

`ChartTool.drawRegions()` takes its fill from `color` and its stroke from `border-color` on
`.chart-object-canvas`; the modern rule (`_themeable.scss:1442-1445`) binds them to
`--inet-focus-ring-color` (`rgba(229, 138, 42, 0.28)`, the primary at 28%) and `--inet-primary-color`.
The plan assumed 28% was calibrated as a wash over a light plot and would disappear over a dark one.
Composited against the two card backgrounds the plot actually sits on — `CARD_BG #FFFFFF` and
`CARD_BG_DARK #252428` — and compared with the unwashed plot by WCAG relative luminance:

| | washed plot | contrast vs unwashed | stroke vs plot |
|---|---|---|---|
| light `#FFFFFF` | `rgb(248,222,195)` | **1.29:1** | 2.62:1 |
| dark `#252428` | `rgb(91,65,41)` | **1.63:1** | 5.88:1 |

The wash is *stronger* against the dark plot, and the stroke more than doubles. The reason is that the
primary is a mid-tone: over white it can only darken slightly, over near-black it lightens a long way. So
the surface needs no dark alpha, no new token, and no rule — and the fill stays the brand accent it is
meant to be in both modes.

- [ ] **Step 1: Record the closure**

Note in `chart-card-roadmap.md` that the selection fill needed no dark treatment, with the two ratios, so
this is not re-opened as an unexamined item. Nothing else in this task runs.

---

### Task 4: The nav bar

**Files:**
- Modify: `web/projects/portal/src/app/graph/objects/chart-nav-bar.component.scss`

**Interfaces:**
- Consumes: the five tokens from Task 1.
- Produces: nothing new.

**Background the implementer needs.** The nav bar is the zoom/pan control that floats over a chart
(`.chart-nav-bar`, `:18-28`): `background-color: var(--inet-toolbar-bg-color)` — which resolves to
`--inet-shell-surface-subtle`, no dark variant — plus a `--inet-default-border-color` hairline. It has no
`.viz-modern` or `.viz-dark` rule of any kind, so it is light in dark mode.

It also carries two known defects, and only one of them belongs in this task:

- **`border-radius: 5px` at `:26` and `:32` is off the radius scale** (2/3/4/6/pill). Correcting it
  unconditionally would change gate-off output, which this plan's own constraint forbids, so the
  correction goes under `:host-context(.viz-modern)` — where it is a modern-gate change that happens to
  land in a dark commit, not a dark change. Use `--inet-radius-xl` (6px): the floating mini-toolbar pill,
  the surface this control is a sibling of, already uses it, and 6px is the card's own radius.
- **`z-index: 9999` at `:20` stays.** It sits above the data-tip scrim at 9996 and the mini-toolbar's
  documented 9920, so it is plausibly wrong — but the shell has no registry layer for a chart-local
  floating control, and a wrong layer is worse than a high number. Leave it and note it; adding a
  registry entry for one control is a larger change than this task.

- [ ] **Step 1: Correct the radius under the modern gate**

Add a `:host-context(.viz-modern)` block putting `.chart-nav-bar` and its buttons on
`var(--inet-radius-xl)`. Do not touch the unconditional `5px` — gate-off keeps it.

- [ ] **Step 2: Give the nav bar its dark surface**

```scss
:host-context(.viz-dark) .chart-nav-bar {
  background-color: var(--inet-viz-surface-dark);
  border-color: var(--inet-viz-hairline-dark);
  color: var(--inet-viz-text-dark);
}
```

Check the button glyphs actually inherit `color` — the buttons declare `background: none` when unselected
but no colour of their own, so they inherit. If a selected/hover state binds a light token, give it the
same treatment.

- [ ] **Step 3: Confirm it visually**

With Modern Visualization and Dark Mode on, open a zoomable chart, confirm the nav bar reads dark with
legible glyphs and 6px corners. Uncheck Dark Mode: the nav bar must be light again and identical apart
from the radius. Turn the modern gate off: `5px` must be back.

- [ ] **Step 4: Commit**

```
feat(chart): darken the chart nav bar and put its corner on the radius scale
```

---

## What this plan does not do

- **The Resize Plot sliders are excluded.** `vs-chart.component.scss` is untouched since 2024, holds
  eleven `#ff8d41` literals and fifteen dead `-ms-` lines, and has its own outstanding ticket that
  deletes roughly half the file before retokenizing anything. Adding a dark rule on top of that would be
  retokenizing before deleting, which the standing rule forbids. Do that ticket, then give it dark.
- **The annotation is excluded, and not because it was forgotten.** Its rectangle is a server-painted
  image (`vs-annotation.component.html:65`, `getSrc()`) over a default background seeded to `0xfef7e0`
  (`VSAnnotationService:55`) that does not follow the dark flag. Dark body text on a pale cream fill is
  correct; a CSS background under a painted light image is not. Its `.viz-modern` selection border
  (`2px solid var(--inet-primary-color)`) reads correctly on dark and is shared selection vocabulary.
  Making annotations dark is server work.
- **The chart interior is excluded.** `GDefaults` has no dark branch and the graph engine has no dark
  awareness; that is design work with no browser half, tracked separately in the roadmap's Ready-now
  table.
- **No shell neutral gains a dark value.** If a surface here needs one, that is a shell decision with
  product-wide reach, and it should be raised rather than made inside a chart-card change.
- **No export or server work.** Every surface here is live-view only. There is no parity pass to budget.

---

## Manual test matrix

Derived 2026-08-27 against the working tree, with every precondition and trigger condition checked in
code rather than assumed. **Read section P first.** Four of the five preconditions are ones that silently
make rows inert rather than fail them — a tester who misses P3 or P5 will see nothing at all and conclude
the work does not function.

Expected values, for a devtools spot-check: surface `#252428`, hairline `#3A383D`, body ink `#CAC4D0`,
emphasis ink `#E6E0E9`, caption ink `#938F99`.

### P. Preconditions

| # | Precondition | Where | Why a miss is silent |
|---|---|---|---|
| P1 | Modern Visualization **ON** | EM → Settings → Presentation → Look and Feel | Dark is a modifier, not a peer: `VSDensityDefaults.isDark()` is `modern && darkMode`. With modern off, dark is off whatever the checkbox says |
| P2 | Dark Mode **ON** | same page | Sets `viz-shell-dark` on `<body>`; see P5 for why this alone does not darken assemblies |
| P3 | Density = **compact** or **comfortable** | same page | The lane ladder is 20/26/30 (`VSDensityDefaults.titleHeightForMode`) and `ANCHORED_LANE_MIN` is 24. **Dense is the default and yields a 20px lane**, so at dense a marked chart or table draws *no chrome at all* — correct behaviour, but it means there is no anchored strip to look at, and rows E1 and E2 are testing different things |
| P4 | `graph.svg.inline` = **true** | EM → Settings → Properties (SreeEnv). **Not** yaml `additionalProperties` | `tooltipTail = tooltipStyle === "CARD" && chartConfigService.inlineSvg` (`chart-area.component.ts:392`). Without it no tooltip ever grows a tail, and **C4 — the highest-risk row in this matrix — cannot be exercised at all** |
| P5 | Test assemblies carry the **MODERN_DARK** mark | create a new dashboard *while P2 is on*, **or** run Revert then Modernize on an existing one | **This is the one that wastes an afternoon.** `VizContext.of(VizMark)` resolves dark as `mark == VizMark.MODERN_DARK`, and the mark is stamped once at creation from `VizMark.fromGate()`. Turning Dark Mode on in EM therefore does **not** darken existing marked assemblies, and Modernize will not fix it either — it only stamps *unmarked* assemblies (`VizModernizeUtil.modernize` iterates `unmarked(vs)`), so on an already-MODERN_LIGHT dashboard it is a no-op returning 0. Revert clears the mark, and the Modernize after it re-stamps from the live gate |

**The expected confusing state, if P5 is missed:** tooltips go dark immediately, because they follow the
live org property via the body class, while every assembly stays light. That is not a bug — see F1.

### B. The floating strip — Task 1

The floating population is not "anything with a strip"; it is the four cases below. Everything in
`ANCHORED_ASSEMBLY_TYPES` (chart, table, crosstab, calc table, selection list, selection tree) is
anchored and must stay **surfaceless** — that is row E1, not a B row.

For each: hover the assembly to reveal the strip.

| # | Assembly | Expect |
|---|---|---|
| B1 | **Calendar** | Pill fills `#252428` with a `#3A383D` hairline; glyphs legible — they must *not* be the dim `#6A685F` icon default; group divider visible but quieter than the glyphs |
| B2 | **Selection container** | As B1 |
| B3 | **Range slider as an adhoc filter** | As B1. Permanently floating — it declares no `titleVisible`, so it has no lane to anchor into |
| B3b | **Selection list and tree item text**, any show type | Item labels legible on the dark surface — the light text neutral, not the near-black `0x2b2b2b` default. Server-side: the colour arrives as an inline style, so a devtools CSS override will not reproduce or disprove it |
| B3c | **Export and composer parity for B3b.** Export the same dashboard to **PDF** and **PNG**, and open the **Format** pane on a selection cell in the composer | All four agree with the screen: viewer, PDF, PNG, and the format pane's foreground swatch. The picker is the one that used to disagree even after the browser was fixed, and it is the reason the substitution runs at five read points rather than one |
| B4 | **Selection list or tree in max mode** | As B1. Counter-intuitive but correct: `isToolbarAnchored` is `isKebabResident && !isMaxModeSelection`, so max mode drops these two out of the anchored path and onto the floating one |

### C. Tooltips — Task 2

One instance per *consumer class*, not per chart type: five chart types tell you nothing new, a table and
a selection list do.

| # | Trigger | Expect |
|---|---|---|
| C1 | Hover a **table** cell (default tooltip) | Dark surface, dark hairline, body ink text |
| C1b | With the org `viewsheet.darkMode` **off**, hover a data point on a **`MODERN_DARK`-marked** chart | Tooltip still dark — it follows the mark, not the org. This is the first defect reported from the browser pass. Its *typography* stays legacy in this state, because `.viz-shell` is a genuine org-level class and is absent; only the palette follows the assembly |
| C1c | With the org dark **on**, hover a data point on a **`MODERN_LIGHT`-marked** chart | Tooltip **light**. The second reported defect: it used to go dark because the body class said so |
| C2 | Hover a **selection list** item | As C1. A second consumer of the same class, which is the point |
| C3 | Hover a **chart** data point (CARD tooltip) | Dark surface, and the ramp still reads as **three** distinct tiers — emphasis value, body label, caption. If all three look alike, the three-role override did not land and the ramp has collapsed to one tone |
| C4 | Hover a chart data point **with P4 satisfied**, so the tooltip has a tail | **The highest-risk row.** Whenever a tail is present the bubble goes transparent and the tail's SVG chrome draws the fill and outline instead. Expect: tail dark, outline dark, and **no filled box behind or around the tail**. A visible rectangle means the dark fill beat the `--tailed` flattening on source order |
| C5 | Open a **hidden annotation**'s tooltip | Dark, and it must **not** have acquired a `40vw` width cap — that belongs to `.widget__default-tooltip` alone |
| C6 | Hover a **stacked** chart's mark, so the stack total renders | Total in the emphasis ink above a quiet `#3A383D` hairline rule, not the bright light border |

### D. The nav bar — Task 4

| # | Trigger | Expect |
|---|---|---|
| D1 | Open a **geographic (map)** chart, nav enabled, **not faceted** | Bar dark with legible glyphs. The condition is `isNavMap()` = `GraphTypes.isGeo(chartType) && navEnabled && no facets`, so an ordinary zoomable chart will **not** show this control — a tester given "a zoomable chart" will fail to reproduce it |
| D2 | Click **Pan** on that bar so the toggle is on | Pressed fill is a quiet **neutral** raise off the bar, not a teal selection fill, and the glyph stays legible on it |
| D3 | Same bar, modern gate on | Corners are 6px, not the old off-scale 5px |

### E. Regression — the rows that catch a real defect

| # | Check | Expect |
|---|---|---|
| E1 | A marked **chart** at compact or comfortable density (P3) | The anchored strip is **still surfaceless** — no fill, no border, no radius, bare glyphs toned to the card. **If a pill has come back, the `:not(.mini-toolbar--anchored)` exclusion is broken and this change has undone `ffae8a1dd`.** The TL suite asserts the selector; only this row proves the render |
| E2 | The same chart at **dense** density | No chrome at all — no strip, no kebab, right-click only. Not a light pill, and not a dark one |
| E3 | Dark Mode **off**, modern on | Every surface in B, C and D identical to before this change. Every rule added sits inside a dark scope, so the diff is the argument and this row is the check |
| E4 | Modern **off**, on an **unmarked** assembly | Fully unchanged, including the nav bar's original 5px corner |
| E5 | A **marked** assembly in a **gate-off** org | Keeps its modern chrome, including the 6px nav bar corner, and its dark palette if marked MODERN_DARK. This is P6's designed behaviour, not a leak — the mark decides rendering, not the gate |
| E6 | Repeat B1, C3 and D1 in the **composer** | Same results. `.viz-dark` is bound at three more render sites (`editable-object-container`, `layout-object`, the wizard preview), and the composer sets its own `viz-shell-dark` body class |

### F. Known non-defects — do not file these

| # | What you will see | Why it is correct |
|---|---|---|
| F1 | On a **mixed** dashboard, a dark tooltip over a light **unmarked** assembly | Narrowed 2026-08-27. A tooltip now follows the assembly it describes, so a *marked* light assembly correctly gets a light tooltip and a marked dark one a dark tooltip, whatever the org says. An **unmarked** assembly carries neither wrapper class, so it still falls through to the shell — the one case a per-assembly answer cannot reach, and unchanged from before. The pop-dim scrim and the data-tip offsets remain org-scoped by decision (R17/R21): a 4px offset is the "inventing precision" case, a palette was not |
| F2 | A marked chart in a **gate-off** org showing legacy tooltip chrome | The second accepted cost: `AbstractChartInfo.getTooltipStyle` resolves AUTO off `VSDensityDefaults.isModern()` directly, because no assembly or context reaches that getter |
| F3 | The same chart losing inline-SVG animation and hover dimming | The third accepted cost: `VSChartInteractionDefaults.isInlineSvg()`. `graph.svg.inline` is the documented override |
| F4 | Chart gridlines, labels, legend and title band already dark | Phase 9B darkened every server-rendered surface. Not this work — and the neutrals used here were chosen to match it, which is why they agree |
| F5 | An **annotation** rectangle still pale cream | Its fill is a server-painted image seeded to `0xfef7e0` and does not follow the dark flag, so its dark body text is correct on it. Explicitly out of scope; making annotations dark is server work |
| F6 | **Resize Plot** sliders still light | Excluded by the plan: that file holds eleven colour literals and fifteen dead `-ms-` lines and has its own ticket that deletes half of it first. Retokenizing before deleting is the wrong order |
| F7 | The **chart interior / plot** palette not darkened | `GDefaults` has no dark branch at all. Design work with no browser half, tracked separately in the roadmap |

### Not in scope for this pass

Export parity **is** part of this pass for the selection-cell fix, and B3c is the row that checks it —
that fix is server-side and reaches PDF, PNG, HTML and the print layout. The three *browser* surfaces
(strip, tooltips, nav bar) remain live-view only: no persisted state, no server resolver, no painter,
so there is nothing of theirs to verify in an export.
resolver, no painter — so there is nothing to verify in PDF, PNG or Excel. That is the whole reason this
work was picked as the cheap visible one.
