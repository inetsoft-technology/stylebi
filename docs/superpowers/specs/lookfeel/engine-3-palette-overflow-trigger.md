# ENGINE §3 — palette overflow: the trigger has fired

**Date:** 2026-09-16
**Status:** recorded, not scheduled
**Raised by:** manual verification of
[brushing companion colours](./2026-09-15-brushing-companion-colors-design.md)

ENGINE §3 was deferred with the note that it was "gated on the slot-9 seam mattering". This file
records the first evidence that it does, so the next person picking §3 up has the measurements
rather than the argument.

## What happened

A tree chart with eleven categories was brushed on a modern-marked dashboard. Several nodes
rendered with **no fill at all** — not dimmed, not soft, invisible against the canvas.

The chart's palette was the modern head spliced with the legacy tail: `spliceLegacy()` keeps
`MODERN_HEAD` in slots 0-7 and takes slots 8 onward from `CategoricalColorFrame.COLOR_PALETTE`.
The nodes that vanished were the ones drawing from the tail.

## Why the tail breaks the companion rule

The companion rule recedes a light-mode colour by lifting lightness `+0.14` and cutting chroma to
`0.40`. `OKLab.toColorInGamut` brings the result back into sRGB **by reducing chroma only** — it
never clamps lightness, because clamping channels would shift the hue and the rule forbids that.

So once `L + 0.14` passes 1.0, no chroma is in gamut and the companion comes back as white.

Measured on the tail colours a chart actually reaches:

| slot colour | OKLab L | L + 0.14 | companion before the fix |
|---|---|---|---|
| `#dadfe1` | 0.900 | 1.040 | `#ffffff` |
| `#c5eff7` | 0.925 | 1.065 | `#ffffff` |
| `#b9dbf4` | 0.875 | 1.015 | `#ffffff` |
| `#fde3a7` | 0.923 | 1.063 | `#ffffff` |
| `#ade095` | 0.853 | 0.993 | `#f9fff6` |
| `#cccccc` | 0.845 | 0.985 | `#fafafa` |
| `#cccc66` | 0.824 | 0.964 | `#f5f7d0` |
| `#518db9` | 0.621 | 0.761 | `#9eb5c7` — fine |

The head does not have this problem. Its lightest member, Amber `#FFB020`, measures L 0.813 and
lifts to 0.953 — just inside. Every other head colour sits well below. **The head was designed as a
set; the tail is a 2010-era list that predates the rule entirely.**

## What was done instead

A light-end case was added to the rule, mirroring the anchor case that already existed for the
darkest member: when the lifted target would exceed L 0.96, the companion deepens and desaturates
(`L → 0.78`, `C × 0.30`) rather than lifting. This is not a new invention — the drift guard's
exemption comment records that the designer had already hand-tuned Amber's authored entry the same
way, "lowered lightness as well as chroma", for the one head colour that approaches the ceiling.

That keeps every mark visible and holds hue. It does not make the tail belong.

## Why §3 is still the real answer

The light-end case is a floor, not a fix. It guarantees a visible companion; it cannot make the
tail look like it was designed with the head:

- **The tail's lightness range is wrong for the set.** Head colours sit between L 0.27 and 0.81.
  Tail entries reach L 0.925. A chart using both renders some series at full weight and others as
  pastels, before any brushing is involved.
- **Above the ceiling the companion deepens while below it the companion lifts.** Two series in the
  same chart therefore recede in opposite directions. Defensible per mark, incoherent as a set.
- **The seam is visible unbrushed too.** `#8ed604` (head slot 8, acid) sits next to `#9368be` (tail,
  violet) with no relationship between them.
- **`getColor(index % size)` still wraps past 40**, so two categories share a colour outright. The
  light-end case does nothing for that.

§3's generator bisects the widest remaining hue gap at the ring's mean lightness and chroma. Every
generated slot therefore lands in the same band as the head, and the companion rule holds for all of
them by construction rather than by exception.

## Constraints §3 must still respect

Carried forward from ENGINE §3, unchanged and still true:

- **A pure function of index and pool.** Frames are cloned and `shareColors` syncs palettes across a
  dashboard; any accumulated state and the same category drifts colour between two charts on one
  screen.
- **The threshold is not always 8.** `updateUnusedColors()` removes pinned colours from the pool, so
  generation can begin at category 6 if three are pinned. An author-facing warning must read the
  live pool size.
- **`updateCSSColors()` loops to `getColorCount()`**, so generated slots are not CSS-themeable
  unless that bound changes. The companion design's §3 note records that declaring `[index=9]` makes
  the slot real, so generated colours are themeable by declaration and no bound needs changing —
  decide deliberately.

One new constraint from this slice:

- **Generated slots need companions too.** Anything §3 generates is immediately a brushing
  consumer. If the generator holds its output inside the head's lightness band, the existing rule
  covers it and the light-end case never fires for a generated slot — which is the outcome to aim
  for, and a reasonable assertion for the generator's tests.

## Where the evidence lives

- The light-end case and its tests: `CategoricalColorFrame.getCompanionColor`, `LIGHT_MAX_L`, and
  `CategoricalColorFrameCompanionTest`.
- The drift guard and its single exemption:
  `VSChartPaletteDefaultsTest.authoredCompanionsAgreeWithTheRuleExceptTheHandTunedSlot`.
- The palettes: `VSChartPaletteDefaults.MODERN_HEAD` / `DARK_HEAD`, `spliceLegacy`, and
  `CategoricalColorFrame.COLOR_PALETTE`.
