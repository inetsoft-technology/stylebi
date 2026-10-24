# Modern card default — translucent tint, no border

**Date:** 2026-10-23
**Types in scope:** every modern (marked) assembly that draws a card: chart, table, crosstab, calc
table, selection list, selection tree, selection container, range slider, group container,
calendar, gauge, text, image.
**Not in scope:** legacy (unmarked) assemblies, which must render pixel-identical; form inputs
(combo box, spinner, check box, radio button, text input, submit), whose white field fill is part
of the control rather than the card; shapes; the embedded viewsheet's page background.

## 1. Why

Modern cards today are a 1px `#D9D5CC` border (`VSObjectChromeDefaults.OBJECT_BORDER`) with a
6px radius, plus an opaque `#FFFFFF` / `#252428` background on charts and tables. Two problems:

**Faint corners on dark dashboards.** A 1px border curved at a 6px radius is anti-aliased into
half-coverage pixels. On a black dashboard the straight edges are full `rgb(217,213,204)` while
the curve pixels land near `rgb(128,128,128)`, so the corners read as thin or missing. This is
not a rendering bug: it happens on an empty card, at every radius except 0, and only a 2px border
removes it. Measured on *Examples/Census 2* (selection lists Region and State).

**The defaults assume a page colour we don't own.** Authors set the dashboard background. A solid
light border and an opaque white card are right on `#F8F7F4` and wrong on black, navy or an image.

A neutral grey at low alpha takes on the colour of whatever is behind it, so a single value
reads as a card on any background. A filled rounded shape anti-aliases along a solid edge, which
looks clean, so the corner problem disappears with the border.

Validated by hand on Census 2, Region set to `#AAAAAA` at 15% alpha with no border. Over the black
dashboard it composites to about `rgb(26,26,26)`, a quiet card with clean corners.

## 2. Decisions

### D1 — The value

Card background `#AAAAAA`, alpha **15%**. Card border: none (no width seeded).

| Behind the card | Composite | Reads as |
|---|---|---|
| light modern page `#F8F7F4` | ≈ `#ECEBE9` | soft recessed card |
| dark modern page `#1C1B1F` | ≈ `#313034` | lifted card, a step above today's `#252428` |
| black | ≈ `#1A1A1A` | quiet card |
| white | ≈ `#F2F2F2` | soft card |

`#AAAAAA` rather than `#808080`: a neutral tint is always weaker on dark backgrounds (`#808080` at
8% over black is `rgb(10,10,10)`, effectively invisible), so a lighter grey keeps the dark case
visible without making the light case heavy.

### D2 — One value for light and dark

The tint composites against what is behind it, so dark mode needs no separate card colour.
`CARD_BG_DARK` stops being a card default. The dark *page* colour stays.

### D3 — All card types switch together

On a dark dashboard, an opaque `#252428` chart next to a `≈#313034` tinted selection list shows two
card greys side by side. The switch is all modern card types at once, or none.

### D4 — Alpha travels with the default colour only

Format alpha is resolved independently of colour (`VSCompositeFormat.getAlpha()`: user → CSS →
default). If the 15% lands in the default tier on its own, an author who picks a background colour
and leaves transparency alone would silently get their colour at 15%. The seeded alpha must apply
only while the colour also resolves from the default tier. Once the author sets a colour without
setting alpha, it renders opaque, as it does today.

### D5 — One card per visual card

A child inside a container takes no tint of its own. The container is the card. Otherwise the
layers stack: two 15% layers give about 28%, three give about 39%.

### D6 — Modern only, no migration

Seeding stays in `seedChromeDefaults(ctx)` behind `ctx.modern`. Legacy assemblies are untouched.
Modern assets saved before this change are reseeded on restore (`VizModernizeUtil.reseedRestored`)
like any other default change. Author-set values are not touched.

### D7 — The group container has no fill

A group is often only a way to move objects together, not a visual card. It loses its modern
border and seeds no tint. Its children are ordinary cards, so D5 does not apply to them.

### D8 — The calendar joins the modern card

The calendar stops bypassing the modern hook and gets the same tint, no border and 6px radius as
every other card.

### D9 — Overlap is accepted

Where an author stacks objects on purpose, such as text over an image, the tints add up in the
overlap. No special case: the author can set the top object's background to transparent.

### D10 — Translucent borders are out of scope

Border colours are stored and emitted as opaque RGB (`BorderColors.java:81`,
`VSCSSUtil.getBorder`). Supporting alpha on borders is a separate enhancement for later.

## 3. Changes

### 3.1 Server defaults (`core`)

- `VSObjectChromeDefaults`:
  - Add a modern card tint constant (`#AAAAAA`) and its alpha (15).
  - Do **not** change `CARD_BG`. It is documented as white in legacy and is read by the chart
    unconditionally (below).
- `VSAssemblyInfo.seedChromeDefaults` (around lines 1289–1322):
  - The base hook writes `objectBorderColor` to every modern assembly.
  - Stop seeding border widths for cards in modern. The colour can stay, since it is latent without
    a width.
  - Seed the tint plus its alpha per D4.
- Per-type seeds currently installing the 1px frame (`setDefaultFormat(true)` / `THIN_LINE`).
  Modern branch only:
  - `TableVSAssemblyInfo` (≈75)
  - `CalcTableVSAssemblyInfo` (≈66)
  - `CrosstabVSAssemblyInfo` (≈60) / `CrossBaseVSAssemblyInfo` (≈57)
  - `ChartVSAssemblyInfo` (≈88)
  - `CurrentSelectionVSAssemblyInfo` (≈72)
- `ChartVSAssemblyInfo.seedChromeDefaults` (≈115):
  - Writes `cardBackgroundCss(ctx)` **without** a `ctx.modern` gate.
  - Gate it, then switch the modern branch to the tint. Without the gate every legacy chart picks
    up the tint on its next restore.
- `TableDataVSAssemblyInfo.seedChromeDefaults` (≈1683): switch the modern branch from
  `cardBackgroundCss` to the tint.
- `VSTableStructureDefaults`:
  - Dark body `BODY_BG_DARK` `#252428` (line 120) must become `null` (transparent), as light already
    is. Otherwise it paints a darker rectangle inside the card.
  - The dark zebra `ZEBRA_BG_DARK` `#2D2B30` (121) and the light zebra `#F5F5F5` (see
    `DataVSAQuery` ≈266) become a translucent stripe, e.g. `#AAAAAA` at 8%.
  - Header and total bands (`#F1EFEA`, `#E9E4DA`, `#EEEAE1`) stay opaque in light. In dark, check
    they still read on the lifted card.
- `CalendarVSAssemblyInfo` joins the modern card (D8):
  - It bypasses the modern hook today (`VSAssemblyInfo` ≈1371) and keeps a legacy `#DADADA` 1px
    border with radius 10 (≈1617–1629). Remove the bypass for modern. A modern calendar seeds the
    tint, no border and `cardCornerRadius()`. Legacy keeps `#DADADA`, radius 10.
  - Day and month cells use `bodyBackground` (≈185–190): transparent in dark too, via the change
    above.
  - The month/year header keeps its opaque `#F1EFEA` band, like the table header.
  - The dropdown calendar's white `0xffffff` object and title (≈1669, ≈1675) are a popup surface,
    not a card. Leave them.
- Chart legends:
  - White, or `LEGEND_BG_DARK`, at alpha 50 (`LegendsDescriptor` ≈103, `LegendDescriptor` ≈77).
  - Leave as is. It reads as a lighter panel on the card, which is intended.

### 3.2 Nesting (D5)

- **Selection container:**
  - The container background is bound twice: the outer div
    (`vs-selection-container.component.html` 27–33) and the children layer
    (`vs-selection-container-children.component.html` 27–31). Paint it once, on the outer div.
  - Children (selection lists, range sliders whose container is a `VSSelectionContainer`) seed no
    tint.
- **Group container (D7):**
  - Modern currently draws a client-side border, `:host-context(.viz-modern) .vs-group-container
    { border: 1px solid var(--inet-default-border-color) }` (`vs-group-container.component.scss`
    29–31). Remove it.
  - The group itself has no fill and no border.
  - Its children are ordinary cards and keep their own tint. The group adds no layer, so there is
    nothing to stack.
- **Tab:** children sit below the 24px strip, not over it, so there is no stacking. Each child is
  its own card. The tab strip keeps its bottom rule and active indicator (`TabVSAssemblyInfo`
  91–108).
- **Embedded viewsheet:** keeps its opaque `pageBackgroundCss`. Its children carry the tint over
  that page, so there is one layer, which is fine.

The child check needs to happen at seed time and again when an assembly is moved into or out of a
container. Without the second check, a list dragged into a container keeps its tint.

### 3.3 Client (`web/projects/portal`)

#### Backdrops behind overlay controls

Three selection-list elements sit over other content and carry a hard-coded light backdrop so the
control stays readable. They are not decoration: making them transparent would let the content
underneath show through.

| Element | Where | What it covers | Today |
|---|---|---|---|
| Header buttons (mini-menu, collapse toggle) | `vs-selection.component.html` ≈68 | Right end of the title, on hover, for a dropdown or in-container list | `rgba(255,255,255,0.75)` |
| Pending-submit icon | `vs-selection.component.scss` 213–216 (`.pending-alert.left`) | The title, for a dropdown or in-container list | `rgb(245,245,245)` |
| Quick-switch button | `vs-selection.component.scss` 270–285 (`.quick-switch-overlay-btn`) | The rows (floating, with its own border and shadow) | `rgba(255,255,255,0.88)` |

All three are already white patches on a dark dashboard today; the tinted card makes them more
noticeable. Give them the floating mini-toolbar's surface, which already solves this
(`mini-toolbar.component.scss` 25–34, 294–296):

- **Modern:** `--inet-shell-surface-default`, with a `--inet-default-border-color` hairline where
  the element has a border.
- **Dark:** `--inet-viz-surface-dark` and `--inet-viz-hairline-dark`.
- **Legacy:** unchanged.

#### Outline colour

The composer selection outline (`editable-object-container.component.scss` 148–159) and the viewer
focus ring (`_themeable.scss` 131–135) use `--inet-viz-selected-border`, which is `#BFDDE5` in light
modern (`_viz-tokens.scss` 53, 115). Against a tinted card that is about 1.2:1, and without the
neighbouring border the outline nearly disappears.

Do not darken `--inet-viz-selected-border` itself. It also drives cell selection borders, the chart
selection stroke and annotation borders (10 uses), customers override it, and the server-side tab
active indicator shares its value.

Add `--inet-viz-assembly-outline`, used only by those two outlines:

| Mode | Value | Contrast |
|---|---|---|
| Light modern | `#2F7F93` | ≈3.9:1 on the tinted card, ≈4.3:1 on the `#F8F7F4` page (≥3:1, WCAG 1.4.11) |
| Dark | `#2DD4BF` (unchanged) | ≈7:1 on the tinted dark card |

Define it with a customer-overridable `-modern` / `-dark` pair, like its neighbours in
`_viz-tokens.scss`. Legacy keeps its current outline.

#### Toolbar strip icon colour: no change

`strip-glyph-tone.ts` 85–116 composites translucent backgrounds over a fixed `#FFFFFF` / `#252428`.
At 15% the light/dark ink choice is the same whichever base it assumes. That it ignores the real
dashboard background is an existing limitation, unrelated to this change.

#### 1px offsets: one change

| Rule | Purpose | Affected |
|---|---|---|
| `base-table.scss` 221, 229 | IE11 scrollbar-button workaround | No |
| `vs-range-slider.component.scss` 35 | Aligns the title's own top border with the object above | No |
| `vs-selection.component.scss` `.pending-alert.left` / `.top` | Positions the pending-submit icon | No |
| `vs-calendar.component.scss` 167, `.day-table { width: calc(100% - 2px) }` | Room for the calendar's own 1px left and right border | **Yes** (D8) |

Content sizes (`getBodyWidth()`, the table's content box) already subtract the border width the
server sends, so they adapt when it goes to 0.

The calendar's day table should subtract only the calendar's actual left and right border widths,
so it fills the width when the border is 0. That this 2px is for the calendar's border is read
from the CSS, not tested; confirm in a browser before and after.

### 3.4 Exports (`utils/inetsoft-xml-formats`)

PDF, PNG, SVG and HTML already honour background alpha: `VSCompositeFormat.getBackground()`
returns ARGB, and PDF writes an ExtGState `/ca`. No change is needed there. Excel is the blocker:

- **Excel cells:** `PoiExcelVSUtil.setCellBackground` (line 1561) falls back to the object's
  background for every table, crosstab and selection cell without its own fill. Excel ignores
  alpha on cell fills, so every cell exports solid `#AAAAAA`. Blend before writing.
- **Excel text boxes:** `PoiExcelVSExporter` line 1157,
  `tb.setFillColor(bg.getRed(), bg.getGreen(), bg.getBlue())`, drops alpha. Blend, or emit
  `<a:alpha>`, which XSSF shapes support.
- **Blend target:** `PoiExcelVSUtil.getColorByAlpha` (line 1585) blends against a hard-coded
  white.
  - Add an overload that takes the backdrop, and pass the viewsheet background resolved for the
    export.
  - Blending against white on a dark viewsheet would produce a light-grey card.
  - `PPTValueHelper` (≈272, ≈390) has the same white assumption; fix it the same way.
- Scheduled email attachments go through the same exporters and need no separate change.

## 4. Testing

- **Server unit tests:**
  - For each card type, the modern seed writes no border width, the tint, and alpha 15. The legacy
    seed is unchanged.
  - The chart's legacy seed writes no background.
  - D4: an author colour with no author alpha resolves to alpha 100.
  - A child of a selection container seeds no tint. Moving it out of the container gives it the
    tint.
  - A group container seeds no fill and no border. Its children keep their tint.
  - A modern calendar seeds the tint, no border and radius 6. A legacy calendar keeps `#DADADA`,
    radius 10.
- **Client:**
  - `vs-selection` and `vs-selection-container` specs: the container background is bound once, and
    there are no hard-coded white backgrounds in modern.
  - Re-run the strip-glyph-tone spec with a 15% `#AAAAAA` background on both bases.
  - The three overlay backdrops resolve to the toolbar surface tokens in modern and dark, and keep
    their current values in legacy.
  - The composer outline and viewer focus ring use `--inet-viz-assembly-outline` in modern, and
    `--inet-viz-selected-border` usages elsewhere are unchanged.
- **Export:** a golden-file or assertion test for an Excel export of a modern table. The cell fill
  equals the blend over the viewsheet background, never `#AAAAAA`. Run it on light and on a black
  viewsheet. Spot-check PDF and PNG on a black viewsheet.
- **Manual, on Census 2 and one light dashboard:**
  - Light page, dark mode, black background, image background.
  - A selection container with children, a group with children, overlapping objects.
  - The composer selection outline and the viewer focus ring on a tinted card.
  - A calendar before and after: the day table fills the card width with no 2px gap on the right.
  - Hover a dropdown and an in-container selection list on a dark dashboard: the header buttons, the
    pending-submit icon and the quick-switch button show a dark surface, not a white patch.
