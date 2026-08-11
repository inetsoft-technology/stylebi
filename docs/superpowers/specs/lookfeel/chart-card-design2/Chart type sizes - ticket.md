# Chart type scale — name it before anything converts

**Status:** ready to apply. **Blocks:** `Outlined chart text - ticket.md` (hard prerequisite);
`Chart overlay surfaces - ticket.md` item 4 (already assumes the overlay half).
**Branch read:** `inetsoft-technology/stylebi` @ `epic-74519`.

This is a **freeze, not an improvement.** Every size below is the size charts render at today. Nothing
moves. Improving chart type is a separate change with visible consequences and should be argued on its
own — see "Deferred, deliberately" at the end.

---

## Why this exists

There are two type systems inside one chart card, and they have never had to agree.

| | Source | Sizes |
|---|---|---|
| Chart interior — tick labels, data labels, axis and legend titles | `GDefaults.java`, rendered by Java2D | **9 / 10 / 11pt** (small / text / title) |
| Card chrome and overlays — card title, tooltips | CSS, `_variables.scss` | **13px** `--inet-font-size-base` |

They cannot collide today because chart labels are **path outlines** — the SVG contains zero `<text>`
elements, so no stylesheet reaches them. The chart interior is effectively an image carrying its own
private scale.

**Two pending projects end that.** The outlined-text conversion turns those paths into real `<text>`,
which then inherits CSS: without a named scale, chart text silently picks up the 13px base, and labels
that fit stop fitting — tick spacing, wrapping and axis extents all shift, on every existing chart, on
upgrade, with no author action. Separately, the card-tooltip ramp needs a named 16 / 13 / 11. If the two
proceed independently they mint two scales and the divergence becomes permanent.

So: one ramp, named once, at today's rendered values.

---

## The scale

### Chart interior

| Token | Value | Role | From |
|---|---|---|---|
| `--inet-font-size-chart-sm` | 9px | Tick labels, data labels | `DEFAULT_SMALL_FONT` |
| `--inet-font-size-chart-base` | 10px | Chart body text, legend entries | `DEFAULT_TEXT_FONT` |
| `--inet-font-size-chart-title` | 11px | Axis titles, legend titles | `DEFAULT_TITLE_FONT` |

### Card chrome and overlays — already decided

| Token | Value | Role |
|---|---|---|
| `--inet-feedback-font-size` | 11px | Caption / subtitle (ships) |
| `--inet-font-size-base` | 13px | Label, card title (ships) |
| `--inet-font-size-lg` | 16px | Tooltip value (**to add**) |

**Combined: 9 · 10 · 11 · 13 · 16.** Two tiers of one ramp, meeting at 11.

### Why 11 gets two names at one value

`--inet-font-size-chart-title` and `--inet-feedback-font-size` are both 11px, and it is tempting to alias
one to the other. Don't. They meet by coincidence of current values, not by shared meaning, and the
deferred chart-ramp revision below must be able to move chart type without dragging shell captions with
it. Two names, one value, documented as such.

---

## Pin the pixel values before applying — one open step

The table above reads `GDefaults`' point sizes as pixels. That is almost certainly right and should still
be measured once.

`new StyleFont(family, Font.PLAIN, 9)` is a `java.awt.Font`, whose size is in **points**. Batik's
`SVGGraphics2D` user space is 1 unit = 1pt at 72dpi, so a 9pt label emits 9 user units, which is 9 CSS px
when the SVG is placed 1:1. Hence 9 / 10 / 11pt → 9 / 10 / 11px.

**Verify:** render a chart, measure the height of a tick label and the axis title against a known
reference. Two things that would change the answer — a non-72dpi transform anywhere in the export path,
and `viewsheetScale`, which multiplies the whole SVG (a scaled chart's labels are not 9px on screen, but
they are still 9px *in the chart's own coordinate space*, which is what the token names).

If the measurement disagrees, keep the roles and the structure and substitute the measured values. The
point of this document is that the ramp is named and shared, not that it is 9/10/11.

---

## Deferred, deliberately

**The chart tier has 1px steps — the same flaw the tooltip ramp was just rejected for.** 9 → 10 → 11 is
not perceptible as hierarchy; a tick label and an axis title differing by 2px read as an inconsistency
rather than a structure. The tooltip ramp was rebuilt for exactly this reason.

We are freezing it anyway. Changing chart type sizes re-lays out every chart in the product — that is a
change with real visual consequences, it needs its own testing on real dashboards, and bundling it into a
conversion whose entire selling point is "nothing moves" would make both impossible to review.

Recorded here so whoever picks it up does so knowingly, not by discovering it again. If it is taken up:
the interior tier wants roughly 10 / 12 / 14, the same ~1.2 steps the overlay tier uses, and it should be
argued alongside `TICK_MIN_GAP` since tighter type changes what "too close" means.

---

## Still to answer: does export match the browser?

Unchanged from the original ticket, and independent of the scale above.

**Do the browser chart and its PDF/image export use the same type sizes?** Open one chart in the browser,
export the same chart, compare a tick label and the chart title.

- **Same** → the client renderer wins in normal use, the `GDefaults` constants only surface where nobody
  sees them, and this closes with a note.
- **Different** → an **export-fidelity bug**, not a design item. Reassign to whoever owns export.

Check a freshly created chart as well as one deserialised from a saved viewsheet: a saved chart carries
its own font descriptors and never falls through to the defaults, so only new charts show the raw
`GDefaults` values.

---

## Also true, no action

The font **family** already agrees. `StyleFont.DEFAULT_FONT_FAMILY = "Default"` is a sentinel resolved by
`getDefaultFontFamily()` → `SreeEnv.getProperty("default.font.family", "Roboto")`, and Roboto is in the
shell stack. Only the sizes diverged.

`GDefaults.TICK_MIN_GAP = 4` equals the shipped `--inet-space-2`. Renderer and spec already agree.

---

## Files

- `core/src/main/java/inetsoft/graph/internal/GDefaults.java` — `DEFAULT_SMALL_FONT` 9, `DEFAULT_TEXT_FONT` 10, `DEFAULT_TITLE_FONT` 11, `TICK_MIN_GAP` 4
- `core/src/main/java/inetsoft/report/StyleFont.java` — family resolution
- `utils/inetsoft-xml-formats/src/main/java/inetsoft/util/graphics/SVGUtil.java` — `getSVGDocument()`, the `textAsShapes` flag the conversion flips
- `web/projects/portal/src/scss/_variables.scss` — `--inet-font-size-base`, `--inet-feedback-font-size`; add `--inet-font-size-lg` and the three chart tokens here
