# Assembly selection border is orange in dark mode — ticket

**Date:** 2026-09-11
**Status:** open, unassigned
**Found:** manual dark-mode pass during the chart palette re-tune
**Not caused by:** [the palette re-tune](./2026-09-11-chart-palette-retune-design.md) — see *Provenance* below

## What happens

Selecting any assembly in dark mode draws a **1px dotted `#E58A2A` orange** border around it.
The selected *cell* border, four lines away in the same stylesheet, is dark-aware and draws teal.
One selection affordance took the dark-mode pass and its neighbour did not.

## The mechanism

`web/projects/portal/src/scss/_themeable.scss:113` and `:117`:

```scss
.bd-selected-cell {
  border: 2px solid var(--inet-viz-selected-border) !important;   // dark-aware
}

.bd-selected-assembly {
  border: 1px dotted var(--inet-primary-color) !important;        // not dark-aware
}
```

The two resolve differently:

| Class | Token chain | Light | Dark |
|---|---|---|---|
| `.bd-selected-cell` | `--inet-viz-selected-border` → `--inet-viz-selected-border-dark` (`_viz-tokens.scss:62`, `:175`) | shell selected-bg | `#2DD4BF` teal |
| `.bd-selected-assembly` | `--inet-primary-color` → `$inet-orange` → `$shell-primary` (`_variables.scss:280`, `:127`, `:51`) | `#E58A2A` | `#E58A2A` — unchanged |

**`--inet-primary-color` is never redefined for dark mode.** The dark block at `_viz-tokens.scss:173-176`
redefines `--inet-viz-selected-bg`, `-text` and `-border`, then re-points `--inet-viz-active-border`
at `--inet-primary-color` — the same light-mode orange. No dark value exists to pick up.

`vs-object-container.component.html:31` applies `.bd-selected-assembly` on focus or mobile
selection, so this covers **every assembly type**, not only charts.

## Provenance

Not introduced by the palette re-tune, and not by the dark-mode work either:

- `.bd-selected-assembly` dates to the **Initial commit, `0d2cdd433a`, 2024-07-12**
- `--inet-viz-active-border` last moved in `1133d86e22`, the chart card track merge
- the palette re-tune branch changed **zero** `.scss` files

## Why it matters more now, with numbers

Against the selection orange `#E58A2A` (OKLCH L 0.715, C 0.151, H 61°), the closest dark series
colour before and after the re-tune:

| Dark series colour | ΔE to `#E58A2A` |
|---|---|
| old amber `#FBB724` | 0.119 |
| **new coral `#FF8367`** | **0.079** |
| new amber `#FFCB82` | 0.166 |

The external palette design set flags **ΔE 0.106** as the point where a series colour is "close
enough to need the white-hairline treatment" against this orange. The re-tuned coral is inside
that. A dotted orange selection border on a chart carrying a coral series can read as data rather
than as state.

**The re-tune improved the other half.** The old dark palette's slot 7 was `#2DD4BF` — an *exact*
match to the cell-selection border. The new nearest is `#2DEEC6`, ΔE 0.072. Cell selection went
from an exact collision to a near one.

## What a fix has to decide

Not a one-line swap, which is why this is a ticket and not a follow-up commit:

1. **What should dark selection be?** Matching `.bd-selected-cell`'s teal makes the two affordances
   consistent, but teal is now ΔE 0.072 from a series colour — trading one collision for another.
   A dark-adjusted orange, or a neutral, are the other options.
2. **Should the selected assembly be a brand-accent affordance at all?**
   [palette-coordination-recommendations.md](./palette-coordination-recommendations.md) says the
   shell gets one routine accent and the visualization layer should not inherit shell accent usage
   by default. Selection chrome sits on the boundary.
3. **The blast radius is product-wide.** `.bd-selected-assembly` styles selection for every
   assembly type in composer and viewer, in both themes. Whatever lands changes how selection looks
   everywhere, so it wants a visual check across assembly types rather than a chart-only one.

## What not to do

- **Do not fix it by changing a palette colour.** The collision is between a series colour and a
  *state* colour; moving the series colour to dodge the state colour inverts the dependency and
  breaks the palette's luminance spacing.
- **Do not scope it to charts.** A chart-only override would leave charts and tables disagreeing
  about what selection looks like.
