# The dark active-border value — design

**Date:** 2026-09-11
**Status:** approved, not implemented
**Branch:** `feature-dark-active-border`, cut from `epic-74519`
**Resolves:** the assembly-selection-border ticket recorded on `feature-chart-palette-retune`
(`333a377118`). That file is not on this branch and arrives with the palette re-tune; this document
restates its mechanism in full so it stands alone.

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

Holding the brand hue at 61°, the curve cannot do much better: `#C56C00` at L 0.62 buys ΔE 0.141 for
4.05:1, `#BF6600` at L 0.60 buys 0.156 for 3.76:1. Leaving the hue toward ~82° buys more
(`#C38E01`: 0.138 at 5.28:1) but the accent stops reading as the brand orange, which is most of what
decision 1 was protecting. Two costs are accepted rather than hidden: legibility drops from 5.88:1
to 4.23:1 at 1px dotted, still above the 3:1 floor; and a light `Modern` palette selected on a dark
chart sits at ΔE 0.108, near the line — improved from today's 0.106, not solved.

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

## Blast radius

**Changes.** Every assembly type's focus outline in the viewer, dark only. Plus
`.vs-combo-box-trigger:focus` (`_bootstrap-override.scss:1318`), the token's other consumer — it
changes in dark too, and should: same affordance, same collision.

**Does not change.** Light mode in every form. Every gate-off assembly, which carries neither class
and still resolves `:root`'s orange. `.bd-selected-cell` and its `@extend` site at
`_themeable.scss:467`, which are the selected family, untouched. The composer's handle outline.

## Verification

`styles.scss` compiles; the portal suite stays green.

Then the manual dark matrix the ticket asks for: chart, table / crosstab / calc table, selection list
and tree, range slider, calendar, and a text or gauge output — focused in a dark org **and** as a
`MODERN_DARK` assembly inside a **light** org, which is the case the body-class route gets wrong and
the per-assembly binding exists to fix.

**The template binding ships unguarded, and that is a deliberate trade.** All four
`vs-object-container` spec files instantiate the component directly through `makeComponent()`; none
renders the template, so asserting a class binding would mean standing up a render harness for a
one-attribute change. Recorded rather than hidden.

## What this leaves open

- **The composer's `#123C44` handle outline on a dark card.** Same family of problem, different
  mechanism, not measured here.
- **Palette × mode combinations generally.** A customer may select any palette, including custom
  ones, on a dark chart. This design measures the two `Modern Dark` sets and the light `Modern` set;
  it does not attempt a guarantee across the legacy 40 or authored palettes. A state colour cannot
  be proof against an arbitrary data palette by hue alone — the durable answer is a two-tone or
  haloed outline, which is a visual change to a shipped affordance and was not taken here.
- **The teal selection family's owner** is unaffected. This design deliberately does **not** point
  the focus outline at it, so the retirement question in roadmap item 3 stays exactly as open as it
  was, with one fewer consumer to migrate.
