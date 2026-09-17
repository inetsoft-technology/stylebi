# Engine changes

Ordered by dependency, not by value. Phase 0 unblocks everything else and is
worth shipping alone.

---

## 0 · Foundation — `getCompanionColor(index)`

**File** `core/src/main/java/inetsoft/graph/aesthetic/CategoricalColorFrame.java`

One accessor that returns a slot's soft companion:

1. If an authored companion palette exists for this palette name, use it.
2. Otherwise derive from the base (rule below).

Needs an sRGB↔OKLab utility (~80 lines, no dependency). **The overflow
generator in §3 needs the same utility** — neither feature justifies it alone,
together they do.

### Derivation rule

Hue is never touched, so a companion always reads as the same series.

| | Non-anchor slots | Anchor (L < 0.40) |
|---|---|---|
| Light | L + 0.14, C × 0.40 | L → 0.855, C × 0.45 |
| Dark | L − 0.26, C × 1.03 | L → 0.64, C × 0.55 |

Two things that look like mistakes and are not:

- **Dark holds chroma rather than cutting it.** On a dark surface, low chroma
  *and* low lightness disappear together.
- **The anchor lifts in dark mode.** It is already the darkest member and has
  nowhere to deepen to. A uniform offset here produces a second dark colour,
  which inverts the emphasis of every chart built on slot 3.

---

## 1 · Target band and confidence interval — wiring only

**Files** `TargetForm`, `ConfidenceIntervalStrategy`

`TargetForm.setBandColorFrame()` already accepts a `CategoricalColorFrame`
and is currently handed an empty one, falling back to `alpha` softening.
Pass the companion frame and drop the alpha. `ConfidenceIntervalStrategy` is a
target strategy, so it comes along at no extra cost.

Do not change the boundary lines: `GDefaults.DEFAULT_TARGET_LINE_COLOR`
`#AFAFAD` is chrome, not data. Z-order is already correct —
`TARGET_FILL_Z_INDEX 10` (under the marks), `VO_Z_INDEX 60`,
`TARGET_LINE_Z_INDEX 120` (over them).

---

## 2 · Brushing — signature change

**File** `core/src/main/java/inetsoft/uql/viewsheet/graph/aesthetic/BrushingColor.java`

`getDimColor()` and `getHighlightColor()` are static and take no arguments.
That is *why* they can only return one global grey and one global red. They
need the series index and its frame, and their call sites must resolve per
mark rather than once per chart.

Not a re-architecture — it is already a colour lookup, just a context-free one.

---

## 3 · Palette overflow — replace the wrap

**File** `CategoricalColorFrame.getColor(int, int, boolean, boolean)`

```java
colors.get(index % colors.size())   // two categories share a colour
```

Survivable at 40 slots, not at 8. Replace with a bounds check falling through
to a cached extension that **bisects the widest remaining hue gap** at the
ring's mean lightness and chroma.

For the shipping bases the first three generated slots are:

| Slot | Colour | Bisects |
|---|---|---|
| 9 | `#009FB6` | Teal → Azure, 78° |
| 10 | `#9E9000` | Amber → Acid, 55° |
| 11 | `#D253C0` | Violet → Magenta, 51° |

Three constraints that are easy to miss:

- **Must be a pure function of index and pool.** Frames are cloned and
  `shareColors` syncs palettes across a dashboard; any accumulated state and
  the same category drifts colour between two charts on one screen.
- **The threshold is not always 8.** `updateUnusedColors()` removes pinned
  colours from the pool, so generation can begin at category 6 if three are
  pinned. Any author-facing warning must read the live pool size.
- **`updateCSSColors()` loops to `getColorCount()`**, so generated slots are
  not CSS-themeable unless that bound changes. Decide deliberately.

Generation guarantees the chart is never *wrong*. It cannot make a nine-hue
chart readable — pair it with a Composer nudge toward top-*n*-plus-Other.

---

## 4 · Ramps — no gating needed

Measure→colour binding goes through `LinearColorFrame.getColor(double ratio)`,
an entirely separate path from `CategoricalColorFrame` with its own list.
Ramps cannot leak into the categorical picker.

The real decision is which frame:

| | Mechanism | Consequence |
|---|---|---|
| `GradientColorFrame` | RGB lerp between two colours | Endpoints >90° apart cross grey at the midpoint |
| `RGBCubeColorFrame` | Path through control points | Same muddiness; `getMinRatio 0.2`/`getMaxRatio 0.8` clip unusable ends |
| `AbstractSplineColorFrame` | **Authored hex stops, spline-interpolated** | The ramp goes where a designer put it |

Amber, Teal and Variance are specified as 7 stops and drop straight into the
third. **Keep the two-endpoint generator** — it covers brand matching and
one-offs that no fixed ramp can — but interpolate it in OKLCH along the short
hue arc, and give it an optional third input for a midpoint. Measured on
blue→orange: RGB midpoint `#8A646F` at chroma 0.05, OKLCH `#B850B1` at 0.18.

---

## 5 · Area fill — new visual property

**File** `core/src/main/java/inetsoft/graph/element/AreaElement.java`

`AreaElement` has no fill colour at all. It sets `HINT_ALPHA 0.8` in an
instance initialiser and paints the fill as the *line's* colour at 80% opacity.
Giving the fill its own colour is a new property on the element and on
`AreaVO` — not a lookup swap.

Buys three chart types, not one: `LineElement` has no fill of its own, so a
filled line and a radar are both `AreaElement` (radar is the zero-measure case,
`isRadar()` returns `getVarCount() == 0`).

`LineElement.fillLineAlpha` (0.3, the wash over null-gap segments) is the same
opacity-standing-in-for-colour pattern if you want it in scope.

---

## 6 · Multi-level charts — new function, upstream work

**Files** `TreemapVisualModel`, `TreemapGeometry`, sunburst/icicle elements

`getShade(index, depth, levels, sibling, siblings)` on
`CategoricalColorFrame`, sharing §0's OKLab utility. **Not the same function
as `getCompanionColor`**: a companion is a tuned *pair* whose partner sits at
a deliberate distance; this is a *sequence* fitting n levels into finite
lightness. At two levels they give different answers.

Three signals: hue = which branch (inherited, the only one consuming a palette
slot) · lightness band = how deep · position within band = which sibling.

Hierarchy charts **re-anchor the bases** into L 0.40–0.58, keeping relative
chroma and an L spread of 0.18. Without this, ladder room varies 14.7× across
branches (Ink 0.59, Amber 0.04) and depth would mean something different
depending on which slot a branch drew.

Holds to 3 coloured levels for trees up to 4 children, 2 for wider. Past that,
or below ~8px, fall to alternating neutrals.

**Scope note:** treemap and circle packing are *containment* types —
`TreemapVO.paint()` fills only leaves — so depth there is nested border weight,
not hue. They are out of scope. Sunburst and icicle are not containment types
and every node is filled.

The larger cost is upstream: `TreemapGeometry` carries `getLevel()` and
`getChildRows()`, but nothing passes depth, breadth or sibling index into
colour resolution, so `TreemapVisualModel.getColor` falls through to the
ordinary categorical lookup for every node.
