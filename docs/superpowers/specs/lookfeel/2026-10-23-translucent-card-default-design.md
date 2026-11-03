# Modern card default — translucent tint, no border

**Date:** 2026-10-23. Revised 2026-11-02 (Bug #79276): decisions D11–D20, D4's mechanism, and
corrections to §3.
**Types in scope:** every modern (marked) assembly that takes the card corner, plus the calendar
(D8, D11):
- chart
- table, crosstab, calc table and embedded table
- selection list, selection tree and selection container
- slider and range slider
- calendar

The group container is in scope only to lose its border (D7).

**Not in scope:**
- legacy (unmarked) assemblies, which must render pixel-identical
- form inputs (combo box, spinner, check box, radio button, text input, submit), whose white field
  fill is part of the control rather than the card
- text, image and gauge, which stay transparent (D11)
- shapes
- the embedded viewsheet's page background

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

**Where: in resolution.** `VSCompositeFormat.getAlpha()` (≈292) returns the default-tier alpha only
while the background also resolves from the default tier. Otherwise it returns the user or CSS alpha
if one is set, and 100 if not.
- One place covers the format pane, `format.css` colours, scripts and the format painter.
- The format pane shows 15 for an untouched modern card, and 100 once the author picks a colour.
- Legacy is unaffected: its default alpha is 100.

Not taken: writing a user alpha of 100 whenever the author sets a colour. That misses `format.css`
colours and every writer other than the format pane.

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

### D11 — The tint goes where the card corner goes

The tint is seeded for exactly the `isCornerSeedTarget()` types (`VSAssemblyInfo` ≈1337): the table
family, chart, selection list, selection tree, selection container, slider and range slider. The
calendar joins them (D8) and seeds its own radius. Every tinted card is rounded, and every rounded
card is tinted.

- **Text, image and gauge stay transparent.** Text carries headings, labels and KPI numbers, and a
  grey box behind each would change how every modern dashboard reads. A transparent PNG logo would
  show a grey rectangle. An author who wants a card behind one of these sets a background.
- **The slider is in.** It already carries the card corner. Form inputs are excluded because of
  their white field fill, and the slider has none.

Not taken:
- Adding gauge. It would also need the radius, or it would be the only square tinted card.
- This spec's original scope, which included text, image and gauge.
- Every type the base hook reaches. Shapes and other types would be tinted. The type checks in
  `VSAssemblyInfo` are explicit lists for the same reason.

### D12 — The mark must match the page

Dark mode comes from the dashboard's mark, which is set in Viewsheet Options. It doesn't come from
the page colour, and the ink seeds follow the mark. The opaque card used to shield the ink from the
page. With a 15% tint the page shows through:
- light-mark ink on a black page drops to 1.23–3.11:1
- dark-mark ink on a white page drops to 1.16–1.52:1

The ink colours are only defaults. An author who sets a page colour that contradicts the mark is
already changing colours, and will likely want to set the ink too or switch the mark. So the
composer offers the switch once, at the moment it becomes relevant (§3.3):
- When a change to the dashboard background crosses the threshold against the current mark, the
  composer offers to switch the mark. That is a light mark with the page going from light to dark, or
  a dark mark with the page going from dark to light.
- The offer appears once, on that change. Once it is dismissed or accepted, it stays hidden until a
  later change crosses the threshold again.
- Image backgrounds are the author's call.

Not taken:
- A warning that stays up while the page and the mark disagree. It would nag an author who keeps the
  mismatch on purpose.
- An opaque card when the page contradicts the mark. The seed would depend on the page colour, and
  the tint would disappear on exactly the pages this spec is for.
- Ink chosen from the page colour. "Dark" would mean two things, and it undoes the per-assembly mark.

### D13 — The neutrals inside the card are retuned as one set

The lines and bands inside a card were tuned against an opaque card. On the tint, most of them nearly
disappear: gridlines go to 1.06 / 1.13:1 and totals to 1.01–1.06:1. The header band also inverts in
both modes. Each is retuned to keep its shipped contrast against the card composite (`#ECEBE9` light,
`#313034` dark), on the same side of the card as shipped.

| Neutral | Light: shipped → new | Dark: shipped → new | On the card (light / dark) |
|---|---|---|---|
| Gridline (table and chart) | `#E8E5DE` → `#D6D2C8` | `#3A383D` → `#4A484F` | 1.27 / 1.45 |
| Axis line (new constant) | grid colour → `#85827A` | grid colour → `#7E7B86` | 3.22 / 3.16 |
| Header separator and title rule | `#D9D5CC` → `#C7C4BB` | `#49454F` → `#544F5A` | 1.46 / 1.65 |
| Header band | `#F1EFEA` → `#DEDCD7` | `#2D2B30` → `#38363C` | 1.15 / 1.10 |
| Total band | `#E9E4DA` → `#D6D2C9` | `#35333A` → `#403E46` | 1.27 / 1.24 |
| Subtotal band | `#EEEAE1` → `#DBD7CF` | `#302E34` → `#3B3940` | 1.20 / 1.15 |
| Slider inactive track | `#E8E5DE` → gridline | `#3A383D` → gridline | as gridline |

- **How the values were derived.** Each shipped colour's luminance was scaled until it has its old
  ratio against the card. The gridline and axis values are the ones proposed for Feature #80141: in
  light they are within rounding of the derived value, and in dark they are slightly stronger. Design
  may adjust any value, as long as it keeps at least the contrast in the table.
- **Why one set.** It keeps the shipped order of emphasis (band, then rule, then gridline) in both
  modes. `VSOutputChromeDefaults` notes that these neutrals are coordinated so they read as one system.
- **Why opaque.**
  - Chart colours lose their alpha on save: `CompositeValue` ≈145 parses with `new Color(int)`.
  - Table gridlines are borders, which D10 keeps opaque.
  - One alpha can't match both modes (D14).
- **Why in this change.** D3 switches charts at the same time. Without this, charts would ship with
  gridlines at 1.06 / 1.13.

Not taken:
- Charts only. Tables would ship with an inverted header band and totals at 1.01–1.06:1.
- Translucent lines and bands.
- Keeping the shipped values.

### D14 — The zebra stripe is translucent, per mode

`#AAAAAA` at **15%** in light and **5%** in dark. The shipped stripe is 1.09:1 in light and 1.10:1 in
dark. Matching those needs 15% and 5% respectively, so one alpha can't match both. The earlier
"e.g. 8%" gave 1.05 / 1.16.

Not taken:
- One alpha for both modes. At 10–12%, light is weaker than shipped and dark is stronger.
- Opaque values per mode, which are tuned to the modern page only.

### D15 — Radar spokes keep the grid colour

Radar spokes take the axis-line colour today: `RadarGraphGenerator` ≈204 calls
`resolveAxisLineColor`. With D13's axis constant they would turn `#85827A` / `#7E7B86`. A radar has
one spoke per measure, so 5–10 spokes at 3.2:1 form a web that competes with the data polygons. A
spoke also isn't the baseline the data stands on, which is why the axis line gets 3:1. So spokes
resolve to the grid colour instead. Author-set line colours are kept, as now.

Not taken: spokes take the axis colour. That is one rule with no special case, but it produces the
web.

### D16 — Excel keeps today's opaque card

Excel paints no page: `ExcelVSExporter.paintsPageBackground()` is false. For a dark viewsheet, the
exporter's dark-mode opt-out (`AbstractVSExporter`, `applyDarkOptOutInPlace`) sends titles, sliders
and list-input items back to the legacy near-black ink, because Excel's cells are white. That rules
out both blends:
- Blending the tint against the viewsheet background would put that ink on `#313034` cells, at about
  1.1:1.
- Blending against white would put a dark table's light ink on near-white cells.

So wherever the background resolves to the default-tier tint, the Excel exporter uses the shipped
opaque card colour instead: `#FFFFFF` in light, `#252428` in dark. That covers cell fills, text boxes
and the chart image's backing. Excel output stays as it is today. PDF, PNG, SVG and PowerPoint paint
the page and keep the tint (§3.4).

Not taken:
- Per-surface blend targets keyed to the list of surfaces that go back to near-black. That is two
  lists to keep in sync, and light dashboards would export grey blocks on white.
- Blending against the viewsheet background, or against white, everywhere.

### D17 — A restore keeps the live border widths

Table and chart bookmarks carry formats in all tiers, but not the mark (seeded-value-reversibility
Decision 10). A bookmark saved while modern stores a border width of none. Restored after a Revert,
R3's non-transition pass would write nothing, and the table or chart would lose its legacy frame.
That is the un-revert Decision 10 rules out. R4 (§3.5) keeps the live assembly's default-tier border
widths across a state restore.

Not taken:
- A stored flag. It is new state, and Decision 10 retired the earlier seed flags.
- Writing the legacy width on every legacy restore. That drops R3 and breaks D6.
- Accepting the loss.

### D18 — Data tips and pop components get an opaque surface

`VSDataTipService` writes the tip alpha (default 100) as a user alpha on every format of the tip view
(≈237). Its light fallback (≈164) applies only to a null background. With the tint, a tip view would
render on solid `#AAAAAA`, where dark ink is 1.8:1. A floating surface sits over chart marks, so it
needs an opaque background anyway. When the view's background resolves to the default-tier tint, the
service uses the shipped opaque card colour (`#FFFFFF` / `#252428`), which is what tips get today, and
then applies the tip alpha.

Not taken:
- Treating the tint as no background, so the fallback applies. The fallback is light, so dark tips
  would get light ink on a light surface.
- Keeping the tint.

### D19 — One outline colour for every selection

The 2026-10-21 selection outline decisions make inner selections use the outer outline's exact style
and colour (Decision 1, #7190). Inner selections also sit on the tinted card, where `#BFDDE5` is
1.20:1. So `--inet-viz-assembly-outline` (§3.3) is used by every selection outline:
- the composer outline and layout mode
- the viewer focus ring
- title and cell selections
- chart axis, legend and title selections
- annotations

`--inet-viz-selected-border` keeps its value.

Not taken:
- Only the composer outline and the focus ring. One object would show two selection colours, and
  inner selections would stay at 1.20:1.
- Darkening `--inet-viz-selected-border-modern` itself. Customers override it, and the server-side tab
  active indicator shares its value.

### D20 — The dark legend reads lighter than the card

Legends keep a fill at alpha 50 so they stay readable over a surface that isn't the card
(`chart-card-roadmap.md` ≈387).
- In light, white at 50% over the card (`#F6F5F4` on `#ECEBE9`) reads as a lighter panel, a step of
  1.09.
- In dark, `LEGEND_BG_DARK` `#252428` at 50% over `#313034` gives `#2B2A2E`, which is darker than the
  card.

`LEGEND_BG_DARK` becomes `#3E3C43`, and the alpha stays 50. Over the card it gives `#38363C`, a step
of 1.10, and the legend text stays at 7.0:1 or better. Light is unchanged.

Not taken:
- Leaving it. The legend would then get opposite treatments in the two modes.
- No legend fill in modern. A legend over the plot would lose its backing.

## 3. Changes

### 3.1 Server defaults (`core`)

- `VSObjectChromeDefaults`:
  - Add a modern card tint constant (`#AAAAAA`) and its alpha (15).
  - Do **not** change `CARD_BG`. It is documented as white in legacy and is read by the chart
    unconditionally (below). `CARD_BG_DARK` stays too: Excel (D16) and data tips (D18) substitute
    these opaque values for the tint.
- `VSAssemblyInfo.seedChromeDefaults` (around lines 1284–1324):
  - Today the base hook writes `objectBorderColor` to the object and title borders of every modern
    assembly that doesn't bypass it (`bypassesBaseChrome()`, ≈1363). It writes the corner radius only
    to `isCornerSeedTarget()` types (≈1293, ≈1337). It writes no border widths; those are set once at
    creation (see 3.5).
  - Add the border width and the tint (with its alpha, per D4) to this hook, following the rules in
    3.5. The tint goes to `isCornerSeedTarget()` types only (D11). The object border colour can stay,
    since it is latent without a width.
  - Title rule (D13): in modern, the title border colour becomes the header separator value, so the
    title rule and the table header rule stay one colour. `objectBorderColor` keeps `#D9D5CC` /
    `#49454F` for the latent object border and for the tab strip's rule (`TabVSAssemblyInfo` ≈92),
    which sits on the page, not on a card.
- Types whose creation installs the 1px frame (`setDefaultFormat(true)` / `THIN_LINE`). Their
  legacy width becomes the legacy value 3.5 writes back:
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
- `VSCompositeFormat.getAlpha()` (≈292): the default-tier alpha applies only while the background
  resolves from the default tier (D4).
- `VSTableStructureDefaults`:
  - Dark body `BODY_BG_DARK` `#252428` (line 120) must become `null` (transparent), as light already
    is. Otherwise it paints a darker rectangle inside the card.
  - The zebra becomes a translucent `#AAAAAA` stripe: 15% in light, 5% in dark (D14).
    - Today `zebraBackground` (≈99) returns `ZEBRA_BG_DARK` `#2D2B30` (121) in dark. In light it
      returns null, which keeps the shipped Default Style's `#F5F5F5` stripe.
    - It now returns the stripe in light modern too, and `DataVSAQuery.applyDarkZebra` (≈288), which
      recolours the style's alternating-row spec, applies in both modes.
    - A table-style colour's alpha reaches the cell through `VSFormatTableLens` (≈768) when the cell
      has no alpha of its own. Confirm the stripe in a browser.
  - `GRIDLINE`, `HEADER_SEPARATOR`, `HEADER_BG`, `TOTAL_BG` and `SUBTOTAL_BG`, light and dark
    (104–117), take the D13 values. The bands stay opaque.
- `VSChartChromeDefaults`:
  - `GRIDLINE` / `GRIDLINE_DARK` take the D13 values. The legend border (`legendBorderColor` ≈45,
    seeded at `CSSChartStyles` ≈114) and the facet grid (`CSSChartStyles` ≈152–156) follow
    `GRIDLINE`, with no change of their own.
  - Add the axis-line constants from D13. The modern `resolveAxisLineColor` (≈75) returns them
    instead of the grid colour. Its callers are `GraphGenerator` ≈2432 and ≈2667, and
    `AxisPropertyDialogModel` ≈93 and ≈278.
  - `RadarGraphGenerator` ≈204 resolves spokes to the grid colour instead (D15).
  - `LEGEND_BG_DARK` becomes `#3E3C43` (D20). It is private and is read only through
    `legendBackground` (≈64), which `LegendsDescriptor` ≈104 and `LegendDescriptor` ≈78 seed. Light
    stays white.
- `VSOutputChromeDefaults`: `SLIDER_INACTIVE_MODERN` and `SLIDER_INACTIVE_DARK` (≈103, ≈117) follow
  the gridline values (D13).
- `CalendarVSAssemblyInfo` joins the modern card (D8):
  - It bypasses the modern hook today (`VSAssemblyInfo` ≈1371) and keeps a legacy `#DADADA` 1px
    border with radius 10 (≈1617–1629). Remove the bypass for modern. A modern calendar seeds the
    tint, no border and `cardCornerRadius()`. Legacy keeps `#DADADA`, radius 10.
  - Day and month cells use `bodyBackground` (≈185–190): transparent in dark too, via the change
    above.
  - The month/year header keeps an opaque band at the table header value (D13).
  - The dropdown calendar's white `0xffffff` object and title (≈1669, ≈1675) are a popup surface,
    not a card. Leave them.
- `VSDataTipService` (≈155–165): when a tip or pop view's background resolves to the default-tier
  tint, set the opaque card colour by the mark before applying the tip alpha (D18).

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

#### Outline colour (D19)

The selection outlines use `--inet-viz-selected-border`, which is `#BFDDE5` in light modern
(`_viz-tokens.scss` 53, 115). Against a tinted card that is about 1.2:1, and without the neighbouring
border the outline nearly disappears.

Do not darken `--inet-viz-selected-border` itself. Customers override it, and the server-side tab
active indicator (`VSObjectChromeDefaults.ACTIVE_INDICATOR`) shares its value. It keeps its value.

Add `--inet-viz-assembly-outline` and use it for every selection outline:

| Outline | Where |
|---|---|
| Composer selected assembly | `editable-object-container.component.scss` ≈149 |
| Layout mode | `layout-object.component.scss` ≈53 |
| Viewer focus ring | `_themeable.scss` ≈133 |
| Title and cell selections (`.bd-selected-cell`) | `_themeable.scss` ≈113 |
| Chart axis, legend and title selections | `chart-tool.ts` ≈944, which reads the token from the canvas style |
| Annotations | `vs-annotation.component.scss` ≈101 |

Chart data selections keep their own stroke (the `.chart-object-canvas` rules in `_themeable.scss`).
They are not outlines.

| Mode | Value | Contrast |
|---|---|---|
| Light modern | `#2F7F93` | ≈3.9:1 on the tinted card, ≈4.3:1 on the `#F8F7F4` page (≥3:1, WCAG 1.4.11) |
| Dark | `#2DD4BF` (unchanged) | ≈7:1 on the tinted dark card |

Define the token like its neighbours in `_viz-tokens.scss`:
- a customer-overridable `-modern` / `-dark` pair
- at the unmarked level, the token defaults to `var(--inet-viz-selected-border)`, so a legacy
  consumer of the new token renders exactly as before
- list `--inet-viz-assembly-outline-modern` in the EM theme CSS editor next to
  `--inet-viz-selected-border-modern` (`theme-css-view.component.ts` ≈329)

#### Mark switch offer (D12)

This applies in the composer only.
- **Threshold.** A relative luminance of 0.179. That is the point where black and white text have
  equal contrast, so above it dark ink reads better and below it light ink does.
- **Crossing.** A change to the dashboard background crosses when the old and new colours fall on
  opposite sides of the threshold, and the new side contradicts the mark: a light mark with a page
  below the threshold, or a dark mark with a page above it.
- **The offer.** On a crossing, show a dismissible offer to switch the mark. Show it once, on that
  change.
  - No later change on the same side shows it again.
  - The next crossing does show it again.
- **Nothing is stored.** The check compares the old and new background of the change itself.
- **Accepting** switches the mark through `VizModernizeUtil.applyMark`, which is the path Viewsheet
  Options uses (`ViewsheetPropertyDialogService` ≈354).
- **Image backgrounds** get no offer.
- **Every composer path** that changes the dashboard background runs the check. The plan lists those
  paths.

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
returns ARGB, and PDF writes an ExtGState `/ca`. One HTML caveat: some presenters are drawn into
`TYPE_INT_RGB` buffers (`HTMLCoordinateHelper` ≈64, ≈1256), where a translucent fill lands on black.
Check it in the export test.

**Excel (D16).** Wherever the background resolves to the default-tier tint, use the shipped opaque
card colour for the mark (`CARD_BG` / `CARD_BG_DARK`):
- **Cells:** `PoiExcelVSUtil.setCellBackground` (line 1561) falls back to the object's background for
  every table, crosstab and selection cell without its own fill. Substitute there.
- **Text boxes:** `PoiExcelVSExporter` line 1157, `tb.setFillColor(bg.getRed(), bg.getGreen(),
  bg.getBlue())`, drops alpha. Substitute there.
- **Chart images:** `AbstractVSExporter.getImageWithPadding` (≈1851) fills the image with
  `fmt.getBackground()`, alpha included. When `!paintsPageBackground()`, fill it with the opaque card
  colour instead.
- `PoiExcelVSUtil.getColorByAlpha` (line 1585) keeps blending author-set translucent colours against
  white, as today.

**PowerPoint** paints the viewsheet background on the slide, so it keeps the tint:
- `PPTValueHelper` (≈272, ≈390) blends against a hard-coded white.
- Add an overload that takes the backdrop, and pass the viewsheet background resolved for the export.
  Blending against white on a dark viewsheet would produce a light-grey card.

Scheduled email attachments go through the same exporters and need no separate change.

### 3.5 Modernize and Revert

Modernize, Revert and the per-dashboard mark switch (`VizModernizeUtil.applyMark`) all set or clear
the mark and then rerun `seedChromeDefaults(ctx)`. Revert has no undo logic of its own: it clears
the mark and relies on the legacy branch of each ternary in the hook to write the old values back
(`VizModernizeUtil` 85–92). Every default this spec changes must therefore be written in
`seedChromeDefaults` with both a modern and a legacy value. Four rules follow.

**R1 — Border widths move into the hook.** Today the 1px width is written only at creation
(`setDefaultFormat(border = true)`, `VSAssemblyInfo` 1217–1227), and the hook writes colour and
radius only. If the spec only stopped setting the width at creation:

- Modernize would leave an existing legacy table's 1px frame in place while a new modern table has
  none, so a modernized dashboard would not match a freshly built one.
- Revert of a table created in modern would have no width to restore, so it would come back
  without the legacy frame.

The hook writes the object border width: none in modern, and in legacy the width the type's
creation installs (`THIN_LINE` on four sides for table, calc table, crosstab, chart and selection
container; none for selection list, tree, slider, gauge, image, text and the others that call
`setDefaultFormat(false)`). Title border widths are unchanged.

**R2 — Background and alpha are written together.** The legacy branch writes the legacy background
*and* resets the default-tier alpha to opaque. If it restored white but kept the 15%, a reverted
chart or table would be white at 15%, which is effectively transparent. Same for the dark branch
of `applyMark`: light to dark and back must leave the tint and alpha exactly as seeded.

**R3 — The legacy width is written only on a transition.** `reseedAfterRestore` reruns the hook on
every state and bookmark restore, legacy assemblies included (`VizModernizeUtil` 237–240). Writing a
legacy width there would overwrite whatever an old asset's stored default holds and break D6's
"legacy renders pixel-identical". So:

| Context | Border width written |
|---|---|
| `ctx.modern` | none |
| legacy, `ctx.transition` (Revert, `applyMark` to no mark) | the type's legacy width |
| legacy, not a transition (restore, density reseed) | nothing; the stored value stays (a state restore puts back the live value, R4) |

`VizContext.transition` is already set by `VizContext.ofTransition`, which `seedAll` uses only
when a mark moves. A new legacy assembly keeps getting its width from `setDefaultFormat` at
creation, as now.

The legacy background and alpha (R2) do not need this guard. The hook already writes the legacy
background on every pass for table and chart, so restoring the opaque alpha there is the same
value an old asset already holds.

**R4 — A state restore keeps the live border widths (D17).** `TableVSAssembly.parseStateContent` and
`ChartVSAssembly.parseStateContent` replace the format from the bookmark. Before they do, save the
live assembly's default-tier border widths, and put them back afterwards.
- The live assembly already holds the right width for its mark: Revert wrote it, creation wrote it,
  or it is an old asset's own value.
- So a bookmark saved under modern and opened after a Revert keeps the legacy frame, and a legacy
  asset's non-standard width still survives (R3).
- Crosstab and calc table carry no formats in state (Decision 10), so they need nothing.

**Calendar (D8).** It skips the base hook today (`bypassesBaseChrome()`, `VSAssemblyInfo` ≈1371). Once
it joins, its legacy branch writes back the `#DADADA` 1px border and radius 10 (R1 and R3 apply).

**Unaffected:**

- Author-set (user-tier) borders and backgrounds: the hook writes the DEFAULT tier only.
- Client-side parts (group container border, overlay backdrops, `--inet-viz-assembly-outline`):
  they key off `.viz-modern` / `.viz-dark`, which come from the same mark, so they switch with it.
- Chart chrome written when the graph is built: the gridline and legend border (`CSSChartStyles`,
  CSS tier, not saved) and the legend fill (`VGraphPair` ≈1081, `initDefaultFormat(ctx)`). These are
  resolved from the mark each time, so they switch with it.

**Layout.** Dropping the 1px border gives the content 2px more width and height, so content sizes
change on Modernize and back on Revert. Sliders in a selection container and children of a
bottom-tabs strip are already re-laid out after a mark change (`restoreContainedSliders`,
`keepOnBottomTabs`). Covered by the round-trip test in §4.

## 4. Testing

- **Server unit tests:**
  - For each D11 type, the modern seed writes no border width, the tint, and alpha 15. The legacy
    seed is unchanged.
  - Text, image and gauge seed no tint in modern.
  - The chart's legacy seed writes no background.
  - D4:
    - an author colour with no author alpha resolves to alpha 100
    - so does a `format.css` colour
    - an untouched modern card resolves to 15
  - D13 and D14: the modern seeds write the new neutrals and the zebra alphas, light and dark. The
    legacy values are unchanged.
  - D15: a modern radar's spokes resolve to the grid colour, and its other axis lines to the axis
    colour. An author-set line colour is kept.
  - D18: a tip view over a tinted modern chart gets the opaque card colour, then the tip alpha, in
    light and dark.
  - D20: the dark legend's default fill is `#3E3C43` at alpha 50. Light is unchanged.
  - A child of a selection container seeds no tint. Moving it out of the container gives it the
    tint.
  - A group container seeds no fill and no border. Its children keep their tint.
  - A modern calendar seeds the tint, no border and radius 6. A legacy calendar keeps `#DADADA`,
    radius 10.
- **Modernize and Revert (3.5):**
  - Round trip: for each card type, a legacy assembly taken through Modernize then Revert ends with
    the same DEFAULT-tier borders, border colours, radius, background and alpha it started with,
    and the same pixel size and body size.
  - An assembly created in modern, then reverted, matches a freshly created legacy assembly of the
    same type, including its border width (R1) and an opaque alpha (R2).
  - Light to dark to light via `applyMark` leaves tint and alpha as seeded.
  - Restoring a legacy assembly whose stored default has a non-standard border width leaves that
    width untouched (R3).
  - A table bookmark and a chart bookmark saved under modern, restored after Revert, keep the legacy
    frame (R4).
  - A user-set border and background survive Modernize and Revert unchanged.
  - A selection container with an expanded slider, and a bottom-tabs strip with children, keep
    their layout across the round trip.
- **Client:**
  - `vs-selection` and `vs-selection-container` specs: the container background is bound once, and
    there are no hard-coded white backgrounds in modern.
  - Re-run the strip-glyph-tone spec with a 15% `#AAAAAA` background on both bases.
  - The three overlay backdrops resolve to the toolbar surface tokens in modern and dark, and keep
    their current values in legacy.
  - Every outline in the §3.3 table uses `--inet-viz-assembly-outline` in modern and dark. Legacy
    renders unchanged.
  - Mark switch offer:
    - it is shown on a crossing
    - it is not shown again for a second change on the same side
    - it is shown again on the next crossing
    - it is never shown for an image background
- **Export:**
  - Excel: a golden-file or assertion test of a modern table.
    - The cell fill is the shipped opaque card colour (`#FFFFFF` light, `#252428` dark), never
      `#AAAAAA` and never a blend.
    - Run it on a light and on a dark viewsheet.
    - A dark chart's image has an opaque `#252428` backing.
  - PowerPoint: a tinted cell on a black viewsheet blends against the viewsheet background.
  - Spot-check PDF, PNG and HTML on a black viewsheet.
- **Manual, on Census 2 and one light dashboard:**
  - Light page, dark mode, black background, image background.
  - A selection container with children, a group with children, overlapping objects.
  - The composer selection outline, the viewer focus ring, and inner selections (a cell, a chart
    axis, an annotation) on a tinted card.
  - A table's header, totals and gridlines, and a chart's gridlines and axis line, light and dark.
  - A radar chart's spokes.
  - A data tip on a modern chart, light and dark.
  - Set a light-mark dashboard's background to black: the offer appears once. Change it to another
    dark colour: no offer. Set it back to light: no offer, since that change doesn't contradict the
    mark.
  - A calendar before and after: the day table fills the card width with no 2px gap on the right.
  - Hover a dropdown and an in-container selection list on a dark dashboard: the header buttons, the
    pending-submit icon and the quick-switch button show a dark surface, not a white patch.
