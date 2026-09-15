# The dark active-border value — design

**Date:** 2026-09-11
**Status:** implemented and verified; the manual dark matrix passed on every row — see *Verification*
for the one caveat, that it ran before the rebase onto the re-tuned palette
**Branch:** `feature-dark-active-border`, cut from `epic-74519`
**Resolves:** [the assembly-selection-border ticket](./2026-09-11-dark-assembly-selection-border-ticket.md).
It was authored on `feature-chart-palette-retune` and reached `epic-74519` with the palette re-tune
(#5188); this branch was rebased onto that, so the two now sit side by side. This document restates
its mechanism in full and stands alone regardless.

## What this is

`--inet-viz-active-border` is the one token whose dark-block entry re-points at the **light**
primary accent instead of taking a dark value. This authors that value, points the assembly focus
outline at the token that already describes it, and makes the outline's DOM position able to
resolve a dark token at all — which it currently cannot.

Three files, four lines. The reason it is a design document and not a follow-up commit is that the
value is a cross-mode vocabulary decision for a product-wide affordance, and two of the ticket's
three framing questions turn out to be answerable by measurement rather than by taste.

## The defect

`_themeable.scss:112` and `:116`, four lines apart:

```scss
.bd-selected-cell     { border: 2px solid  var(--inet-viz-selected-border) !important; } // dark-aware
.bd-selected-assembly { border: 1px dotted var(--inet-primary-color)       !important; } // not
```

`--inet-primary-color` (`_variables.scss:280` → `$inet-orange` → `$shell-primary`) is `#E58A2A` in
both modes; it is never redefined for dark. The dark block at `_viz-tokens.scss:167-184` redefines
the selected family and then, at `:176`, re-points `--inet-viz-active-border` at that same light
orange. No dark value exists to pick up.

Selecting any assembly in dark mode therefore draws a 1px dotted `#E58A2A` ring, while the selected
*cell* four lines away draws teal. One affordance took the dark-mode pass and its neighbour did not.

`.bd-selected-assembly` predates all of this: **Initial commit `0d2cdd433a`, 2024-07-12.**

## Three corrections to the ticket

**1. The fix it implies cannot reach the element.** `.bd-selected-assembly` is applied at
`vs-object-container.component.html:31` to the `.focus-assembly` overlay div opened at `:23` — a
**preceding sibling** of the `.vs-object-parent-container` at `:39` that carries `viz-modern` /
`viz-dark` at `:46`. The overlay sits outside every per-assembly dark scope, so a `.viz-dark`-scoped
token can never resolve there. The only ancestor that would is body-level `.viz-shell-dark`
(`viewer-app.component.ts:2830`), which follows the org `darkMode` flag rather than the assembly's
mark — the mismatch the tooltip work already corrected once, observed then as a light tooltip on a
`MODERN_DARK` chart in a light org. Reachability is half the fix and the ticket does not mention it.

**2. "Product-wide across composer and viewer" is wrong.** `.bd-selected-assembly` occurs **once**
in the repository. The composer does not use it: its selected object reads through resize handles
coloured by `--inet-composer-selected-outline` (`#123C44`, `_variables.scss:91`, emitted at `:358`),
and `editable-object-container.component.scss:42-44` already carries the reasoning this ticket
re-derives — *"the object's own fill is user-designed and must not be overwritten, so selection
reads via a dedicated outline color on the handles rather than the shared shell-primary accent."*
The scope is viewer-side (viewer, embedded VS, composer preview), every assembly type.

**3. The class name misdescribes the state, and the token set already has the right name.**
`.bd-selected-assembly` binds to `isFocused()` (`vs-object-container.component.ts:446`), not to
selection. `_viz-tokens.scss:99-101` defines the distinction deliberately: *"The selection family is
a visualization-owned teal, kept distinct from shell/Composer selection; **active stays on the
primary accent (outline, no fill) so it reads apart from selection.**"* This is an *active* outline
by the token file's own definition, so it belongs on `--inet-viz-active-border`. That also disposes
of the ticket's option 1 — matching the cell's teal would collapse a distinction built on purpose,
and would add a consumer to a family the roadmap ranks for **retirement** (item 3, blocked on an
owner; *"Whether the teal selection family has an owner"* under Still undecided).

## Decision 1 — the accent rule survives

The affordance stays on the brand accent, outline and no fill, in both modes. The rule at
`_viz-tokens.scss:100` is kept rather than given a dark-only exception.

The alternative was to move chrome off the accent entirely, which
[palette-coordination-recommendations.md](./palette-coordination-recommendations.md)'s decision test
argues for in the abstract (*"If it only expresses interface hierarchy, it should usually be
neutral"*). **Measurement declines it.** Against this branch's `Modern Dark`, whose slot 8 is the
slate `#94A3B8`, a neutral outline drawn from the existing dark text ramp measures:

| neutral candidate | ΔE to nearest `Modern Dark` member |
|---|---|
| `--inet-viz-text-subtle-dark` `#938F99` | **0.060** (slate) |
| `--inet-viz-text-dark` `#CAC4D0` | 0.121 (slate) |

`#938F99` is *worse* than the orange's worst case. Going neutral would have traded one collision for
a larger one, because this palette already contains a neutral. Recorded so it is not re-proposed.

## Decision 2 — the value is `var(--inet-primary-color-dark)`

```scss
--inet-viz-active-border-dark: var(--inet-primary-color-dark);   // #C96F12
```

`$shell-primary-hover` → `$inet-orange-dark` → `$primary-dark`, emitted as
`--inet-primary-color-dark` at `_variables.scss:495`. It is the brand accent's existing deep step;
nothing new is invented.

**The dark accent has to go deeper, not lighter.** Every `Modern Dark` member is high-lightness by
design, so lightness is the axis that separates, and the `#252428` card surface is dark enough that
a deeper orange still clears WCAG 1.4.11's 3:1 non-text floor. Measured in OKLab, on the same scale
the external palette set uses, where **ΔE 0.106** is its stated "close enough to need the
white-hairline treatment" line:

| | contrast on `#252428` | ΔE, `Modern Dark` on this branch | ΔE, re-tuned `Modern Dark` |
|---|---|---|---|
| `#E58A2A` (today) | 5.88:1 | 0.119 (amber `#FBB724`) | **0.079** (coral `#FF8367`) |
| **`#C96F12`** | **4.23:1** | **0.155** (rose `#FB6181`) | **0.130** (coral `#FF8367`) |

It improves both palettes, so **this fix does not depend on the palette re-tune merging.** The
re-tune is what makes it urgent — it pushes the collision from 0.119, above the line, to 0.079,
below it — but the defect and the improvement both exist on `epic-74519` as it stands.

Staying in the brand-orange family, the curve cannot do much better. Measured against the re-tuned
palette, where `#C96F12` scores 0.130: `#C56C00` buys ΔE 0.141 at 4.05:1, `#BF6600` buys 0.156 at
3.76:1. Leaving the family toward ~82° buys more (`#C38E01`: 0.138 at 5.28:1) but the accent stops
reading as the brand orange, which is most of what decision 1 was protecting. The hue is not held
exactly — `#E58A2A` is 61.4° and `#C96F12` is 57.9°, with the alternatives at 57.5-59.0° — but all
stay in the orange family against 81.9° for the yellow candidate.

Three costs are accepted rather than hidden. **Legibility** drops from 5.88:1 to 4.23:1 at 1px
dotted, still above the 3:1 floor. **A light palette on a dark chart** moves the wrong way against
this branch's light `Modern` head, from ΔE 0.060 to 0.143 — that is an improvement in separation,
but the earlier draft of this document quoted 0.106 → 0.108 here, which are the figures for the
**re-tuned** light head and belong to a palette that is not on this branch. **The 40-colour tail**
is the real one: `VSChartPaletteDefaults.spliceLegacy` pads both `Modern` and `Modern Dark` to 40
from the legacy palette, so any chart with more than eight series draws slots 9-40 from it, and slot
31 is `#CC6600` — ΔE **0.019** from `#C96F12`, against 0.052 from `#E58A2A` today. At nine or more
series the ring and a mark are effectively the same colour. See *What this leaves open*.

## The second defect — chart mark selection is canvas-painted and never saw the dark pass

Found while verifying the above, in a different mechanism, and fixed in the same branch because it
is the same symptom: the light brand orange drawn on a dark card by a selection affordance. It is
not reachable from the token work — no amount of repointing `--inet-viz-active-border` touches it.

`.viz-modern .chart-object-canvas` (`_themeable.scss:1444`) names `--inet-primary-color` directly
and has no dark counterpart. A chart's selected marks are not DOM: `ChartTool.drawRegions`
(`chart-tool.ts:781`) reads the **canvas element's computed style** — `color` becomes `fillStyle`,
`borderColor` becomes `strokeStyle` — and paints them. So the affordance resolves through exactly
one rule, and that rule named a value with no dark form. Every custom property the dark scope
redefines was reaching the canvas correctly and being ignored, because nothing read one.

Dark now takes the **selected** family rather than the accent, so a selected mark and a selected
title agree. Within that family it takes the *ink* rather than the *border*, which is the opposite
of what naming would suggest and is forced by where the stroke lands — on top of the marks:

Measured against the re-tuned `Modern Dark` head-8 this branch now sits on, not the pre-rebase one:

| dark stroke candidate | contrast on `#252428` | ΔE to nearest `Modern Dark` |
|---|---|---|
| `#E58A2A` today | 5.88:1 | 0.079 — slot 2 `#FF8367` |
| `--inet-viz-selected-border` `#2DD4BF` | 8.28:1 | 0.072 — slot 4 `#2DEEC6` |
| `--inet-viz-selected-text` `#A8EEF5` | 11.91:1 | 0.114 — slot 4 `#2DEEC6` |

The family's border is the palette's own teal to within ΔE 0.072, under the design set's 0.106
separation line, so a selection stroked with it would not read apart from the data underneath it.
The ink is the only candidate that clears the line, and it is the highest contrast of the three.

Light is untouched and stays on the accent deliberately: the selected family's *light* border is
1.43:1 on a white plot, which is not a usable outline.

**Why the fill token carries pre-multiplied alpha.** The fill is `--inet-viz-selected-fill-dark`,
the same ink at the 28% `--inet-focus-ring-color` uses in light. It is authored as a literal
`rgba()` rather than mixed from `--inet-viz-selected-text-dark` because `ctx.fillStyle` rejects the
`oklab()` / `color(srgb …)` form `getComputedStyle` returns for a `color-mix()`, and drops it
without raising — the selection would simply keep the previous fill. A plain `var()` is fine and is
what the stroke uses; it is the *mixing* that canvas cannot take. `--inet-focus-ring-color`
(`_variables.scss:605`) is authored the same way for the same reason.

**The rule's position is load-bearing.** `.viz-dark .chart-object-canvas` ties
`.viz-modern .chart-object-canvas` on specificity (0-2-0) and a dark assembly carries both classes,
so only source order decides. It must stay after the light rule; a reorder reverts dark charts to
the light accent silently.

## The change

**1. `_viz-tokens.scss`, the customer-overridable dark block (`:58-63`)** — add

```scss
--inet-viz-active-border-dark: var(--inet-primary-color-dark);
```

Its neighbours in that block are literal hexes (`--inet-viz-selected-border-dark: #2DD4BF`). This
one is an alias, deliberately: a hex would silently stop being the brand colour after a primary
rebrand. A customer overriding `--inet-viz-active-border-dark` still wins; the alias supplies only
the default. `--inet-composer-primary-hover` (`_variables.scss:365`) is already authored this way.

**2. `_viz-tokens.scss:176`**, inside `.viz-dark, .viz-shell-dark` —

```scss
--inet-viz-active-border: var(--inet-viz-active-border-dark);   // was var(--inet-primary-color)
```

Both blocks sit at `:root` (`_variables.scss:248-614`), so `--inet-primary-color-dark` resolves on
`html` regardless of source order between the two partials.

**3. `_themeable.scss:117`** — `.bd-selected-assembly` re-points from `--inet-primary-color` to
`--inet-viz-active-border`. Border weight and style are unchanged: 1px dotted.

**4. `vs-object-container.component.html:23`**, the `.focus-assembly` div gains the two bindings its
sibling at `:46` already carries —

```html
[class.viz-modern]="vsObject.vizModern"
[class.viz-dark]="vsObject.vizDark"
```

Both, not only `viz-dark`: the dark block's own comment states the invariant — *"Assembly wrappers
carry viz-modern + viz-dark together."* Adding `viz-modern` changes nothing visually, because the
light `.viz-modern` block resolves `--inet-viz-active-border` to `var(--inet-primary-color)`, the
same value `:root` gives; and the div is empty, so neither class can reach a subtree.

The div is empty but not inert: it hosts `VSDataTip` and `VSPopComponent`, and both directives call
`GuiTool.isVizModernElement(this.elementRef.nativeElement)`, which is a `closest(".viz-modern")`.
Adding the class flips that call's answer from false to true for a marked assembly. It is still
safe, but by a second reason rather than the emptiness one: both call sites
(`vs-data-tip.directive.ts:248`, `vs-pop-component.directive.ts:298`) sit inside `if(this.miniToolbar)`
and neither host on this div sets `miniToolbar`, so the changed value is never read. Found by the
PR review, not by this design.

**5. `_viz-tokens.scss`, the same dark block** — add `--inet-viz-selected-fill-dark`, the selected
ink at 28%, for the canvas fill. Pre-multiplied rather than mixed; see the second defect above.

**6. `_themeable.scss`, after `.viz-modern .chart-object-canvas`** — a `.viz-dark` counterpart that
fills `var(--inet-viz-selected-fill-dark)` and strokes `var(--inet-viz-selected-text)`. Must stay
after the light rule.

## Blast radius

**Changes.** Every assembly type's focus outline in the viewer, dark only. Plus
`.vs-combo-box-trigger:focus` (`_bootstrap-override.scss:1318`), the token's other consumer — it
changes in dark too, and should: same affordance, same collision. Separately, every canvas-painted
chart selection on a marked-dark assembly: plot marks, and the axis, legend and title chrome, since
all five canvases carry `.chart-object-canvas`.

**Does not change.** Light mode in every form. `.bd-selected-cell` and its `@extend` site at
`_themeable.scss:467`, which are the selected family, untouched. The composer's handle outline.

**Changes more widely than first stated, and for the better.** A gate-off assembly carries neither
viz class, so it has no declaration of its own and inherits the body-level one — and `viz-shell-dark`
sits on `document.body` (`viewer-app.component.ts:2830`), inside the same block this change repoints.
So a gate-off assembly in a **dark org** does change. Since the seed mark made the org gate
creation-time only, that is the normal state of every pre-existing dashboard, not an edge case. The
effect is an improvement: a gate-off assembly renders light (`vs-object-model.ts:68` — `vizDark` is
never true unless `vizModern` is), and on its white card the ring goes from **2.62:1** to **3.64:1**,
crossing WCAG 1.4.11's 3:1 floor it previously failed. Found by the final review, not by the design.

## Verification

`global.scss` compiles and the compiled output asserts the token chain. `styles.scss`, which an
earlier draft of this section named, imports neither `_viz-tokens.scss` nor `_themeable.scss` and
would have compiled green whatever this change did. The suite stays green: `npm run test:portal`
runs **two** projects and prints a summary block for each — portal at 225 files / 1454 tests, then
em at 106 / 376. Reading only the trailing block is how an earlier draft of this section came to
claim a collection gap, reporting em's 106 as portal's and setting it against the 225 spec files
the portal target scopes. There is no gap: portal collects all 225.

**The manual dark matrix has been run, and passed on every row.** It is the release gate, and
nothing automated substitutes for it: no check in this work looks at a rendered pixel. Every row the
ticket asks for was checked — chart, table / crosstab / calc table, selection list and tree, range
slider, calendar, and a text or gauge output — focused in a dark org **and** as a `MODERN_DARK`
assembly inside a **light** org, which is the case the body-class route gets wrong and the
per-assembly binding exists to fix. Plus the two rows the final review added: a **gate-off** assembly
in a **dark** org, which inherits the body-level token and therefore changes too, and
`.vs-combo-box-trigger:focus`, the token's other consumer. Nothing looked wrong.

**One caveat on what that pass covers.** The matrix ran before this branch was rebased onto the
palette re-tune (#5188), which replaced all eight `Modern Dark` head colours. So the separation it
confirmed by eye was against a palette the branch no longer carries. The re-measured figures are in
the tables above and the conclusions hold — `#C96F12` at ΔE 0.130 and `#A8EEF5` at 0.114 both clear
the 0.106 line against the re-tuned head-8 — but that is arithmetic, not a second look at a rendered
pixel. A re-check of the chart rows against the re-tuned palette is cheap and has not been done.

**The template binding ships unguarded, and that is a deliberate trade.** All four
`vs-object-container` spec files instantiate the component directly through `makeComponent()`; none
renders the template, so asserting a class binding would mean standing up a render harness for a
one-attribute change. Recorded rather than hidden.

## What this leaves open

- **The composer's `#123C44` handle outline on a dark card.** Same family of problem, different
  mechanism, not measured here.
- **The 40-colour tail, and why no hue escapes it.** `VSChartPaletteDefaults.spliceLegacy` pads both
  `Modern` and `Modern Dark` to 40 slots from `CategoricalColorFrame.COLOR_PALETTE`, so a chart with
  nine or more series reaches the legacy tail. `#C96F12` sits ΔE **0.019** from its slot 31,
  `#CC6600` — worse than the 0.052 `#E58A2A` manages today, and the one axis on which this change is
  a regression. **No available value fixes it.** That tail carries `#CC6600`, `#CC9933`, `#993300`,
  `#FF9900`, `#a88637` and `#d2b267` for any orange, and `#666666`, `#CCCCCC`, `#95a5a6` and
  `#dadfe1` for the neutral this design already declined on other grounds. A state colour cannot be
  made safe against a 40-colour data palette by choosing a better hue; the durable answer is the
  two-tone or haloed outline named below. Accepted here because the collision needs nine series to
  reach, and recorded so it is not rediscovered as a bug report.
- **A two-tone or haloed outline** is what actually solves the class of problem, at the cost of a
  visual change to a shipped affordance in both modes. Not taken here.
- **Custom and authored palettes.** A customer may select any palette on a dark chart. This design
  measures the two `Modern Dark` sets, both light `Modern` heads and the spliced 40; it attempts no
  guarantee beyond those.
- **The teal selection family's owner** is still unowned, and this branch leaves the retirement
  question **more** expensive rather than less. The focus outline deliberately does not point at the
  family — it is an *active* affordance, and the accent split is on purpose. But the second defect
  above puts canvas-painted chart selection onto `--inet-viz-selected-text` and adds
  `--inet-viz-selected-fill-dark` beside it, so the family gains two dark consumers here. An earlier
  draft of this line claimed the opposite, counting only the outline; corrected after the PR review.
