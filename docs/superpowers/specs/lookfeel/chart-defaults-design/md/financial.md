<!-- Markdown version of financial/financial.html. The drawn "proposed look" cards are in the HTML page. -->

# Financial charts: candle, stock, box plot

Family page. It lists only what differs from the common chart defaults, which hold the shared spacing, chrome colour, text, palette and legend rules. Candle is the pilot and is written up in full below. Stock and box plot are written up after it. Facts marked *observed* come from live charts on the server. Facts marked *traced* come from reading the code. Nothing here has been checked on a customer dashboard.

## Status

| Type | Status | What exists |
|---|---|---|
| Candle | Written up | Canonical plus 9 variants built on made-up price data, decisions recorded, build items listed. |
| Stock | Written up | Canonical plus 5 variants on the same made-up price data. |
| Box plot | Written up | Canonical plus 8 variants on made-up score data, 5 regions with a real spread. |

## Candle

### Decisions

- **Follows C19, the density padding on every side.** Applies to candle, stock and box plot. The measured top gap below the title line on the canonical capture is 5 to 6px, about half the density padding at compact. Confirmed on 2026-11-03.
- **Follows the common rules.** No title line (C2), plot padding by density (C19, in the first bullet), grid and axis lines (C3, C4), the label rule (C5), axis titles (C6: off for candle and stock, Y title only for the box plot), legend rules for a Color dimension (C7 to C10), the single box plot colour from the modern palette (C11), direction colours (C12, C16), the minimum candle body, stock tick and box size (C17) and the zero line when values cross zero (C18). This page was written before the rules were numbered, and the sections below spell out the same decisions.
- **Colour means direction.** A rising candle uses the up colour and a falling candle the down colour. Defaults are dedicated direction tokens: light `#1C857B` up and `#AD6119` down, dark `#3FBFB2` up and `#E8954A` down. Teal and amber are confirmed: they avoid the red and green clash between markets, and stay distinct under all three colour-blindness types. Contrast against the translucent card is below.
- **The Color field is for the up and down colours.** An author sets the two colours there as they like, and an author's choice always wins over the defaults. The Color field does not accept a dimension on a candle.
- **Hollow for up, filled for down stays.** It is already shipped and is the second cue, so direction survives colour blindness and greyscale.
- **Axis titles are off.** Four measures describe one price, and the title today lists all four with a Sum prefix. The unit goes in the card title, for example "ACME price, $". A title the author sets still shows.
- **Labels follow the common label rule.** Thin first, rotate only as a last resort, never past 45°, a label band of at most about 20% of the plot height, and truncation beyond about 12 characters. This is common rule C5.
- **Body and wick.** A body is at least about 3px wide and the wick is 1px, so 30 candles stay visible.
- **Value axis does not start at zero.** This is shipped behaviour and stays.
- **Several series become panels.** A dimension on the X or Y shelf splits the chart, with the range not shared across panels and a minimum panel height of about 100px. To verify when built, see the open items.

### Proposed look

Drawn to the decisions above at the default card size, with invented prices. No axis titles, direction colours, hollow for up and filled for down, and thinned horizontal labels.

## Light

*The drawn cards that show the proposed look are in the HTML page.*

## Dark

### Shipped today against the decisions

| Setting | Shipped today | Decided |
|---|---|---|
| Colour | One colour, the legacy blue `#518DB9` darkened to about `#3C698A`. Not modern-gated. *Traced and observed.* | Direction tokens, author-editable through the Color field. |
| Up and down cue | Hollow white body for up, filled for down. *Traced and observed.* | Kept, with colour added. |
| Color field | Accepts a dimension and turns it into a series colour. *Observed.* | Sets the up and down colours. Takes no dimension. |
| Axis titles | Both on. Y lists all four measures with a Sum prefix, X repeats the labels. *Observed.* | Both off. |
| X labels | Rotated 90°, thinned when they overlap. *Observed.* | Common label rule. |
| Value axis | Does not start at zero. *Observed.* | Unchanged. |
| Legend | None, unless a Color dimension is bound. *Traced.* | No Up and Down legend. Direction already has two cues. |
| Several series | A Color dimension overlays them on one axis. *Observed.* | Panels from a dimension on X or Y. |
| Data labels | Never drawn for candle. *Traced.* | Unchanged. |

### Variants

Captured from live charts on a server dashboard, with every chart style and format at its default. Only the data and the card size differ. The data is a made-up random walk: 30 trading days from a start of 150, with open, high, low and close. Other variants use the same generator with different row counts, label formats, ticker counts and one injected gap and flat days.

| Live capture | Variant | What it shows | What it means for the defaults |
|---|---|---|---|
| ![30 candles (canonical)](../financial/img/candle_canonical.png) | 30 candles (canonical) | Bodies are 3 to 6px wide, the day labels are rotated 90°, and the plot is about 60% of the card height. | Label rule, minimum body width. |
| ![8 candles](../financial/img/candle_few.png) | 8 candles | The best case: wide bodies, horizontal labels, and hollow versus filled reads clearly. | Nothing to change. Use as the reference look. |
| ![120 candles](../financial/img/candle_many.png) | 120 candles | The candles merge into one band that looks like a line, and the rotated labels overlap. | Above about 60 candles, thin the labels harder. Zoom or aggregation is not defaulted. |
| ![2 tickers on the Color field](../financial/img/candle_two_tickers.png) | 2 tickers on the Color field | Both tickers share one price axis, so each is a row of tiny dots. A legend appears on the right. | The Color field must not take a dimension. |
| ![6 tickers on the Color field](../financial/img/candle_six_tickers.png) | 6 tickers on the Color field | Six rows of dots and a six-entry legend. Unreadable as candles. | Same as above. |
| ![15-character day labels](../financial/img/candle_long_labels.png) | 15-character day labels | The rotated labels take about 45% of the card height and squeeze the plot. | Label band cap and truncation. |
| ![Flat days and a 12% gap](../financial/img/candle_edge.png) | Flat days and a 12% gap | A flat day shows as a small tick, and the gap shows as one long hollow body. Both read correctly. | No change needed. |
| ![Small card, 300×180](../financial/img/candle_small.png) | Small card, 300×180 | Title and labels take about half the height, leaving a plot of roughly 90px. | Titles off, and the label rule with fewer labels. |
| ![Large card, 800×480](../financial/img/candle_large.png) | Large card, 800×480 | Reads as a proper candle chart. The axis title lists all four measures with a Sum prefix. | Axis titles off. |
| ![2 tickers as panels (dimension on Y)](../financial/img/candle_panels.png) | 2 tickers as panels (dimension on Y) | The rows separate, but both panels share one 60 to 180 range, so each is about 55px tall and the candles stay tiny. | Independent range per panel and a minimum panel height. To verify when built. |

Rules of thumb from the variants: about 10 candles or fewer reads well, 10 to 40 is thin but works, and past about 60 the candles merge.

### Build items for candle

| # | Item | Cost | Where | Notes |
|---|---|---|---|---|
| 1 | Direction colours with author override | New behaviour | `CandlePainter`, the candle colour frame setup in `GraphGenerator`, `VSChartPaletteDefaults` (tokens and dark variants), the colour zone in the binding editor | Candle draws one colour today and darkens it. The up and down colours are stored in a form that persists, with an author value stronger than the default. |
| 2 | Color field takes no dimension on candle | New behaviour | The colour zone in the binding editor, `ChangeChartTypeProcessor` when switching to candle | Dropping a dimension is refused or ignored. Switching an existing chart to candle clears a colour dimension. |
| 3 | Axis titles off for candle | Seed only | `TitleDescriptor`, `ChartVSAssemblyInfo.seedChromeDefaults`, `ChangeChartTypeProcessor` | Title visibility is a tiered value, so an author toggle wins and Revert restores it. |
| 4 | Minimum body width and 1px wick | Small change | `CandlePainter` | Body width from the slot width with a floor of about 3px. |
| 5 | Panel range not shared, minimum panel height | Needs provenance, plus layout | `AxisDescriptor` (the shared range is a plain true or false), the facet layout in `VGraph` | Seed the range off when a candle has a panel dimension. Needs a flag for an author setting. |
| 6 | Label rule | Common | `DefaultAxis` label layout | Tracked on the common page. Not candle-specific. |

### Direction colours on a translucent card

The card is the dashboard background with a grey tint over it, so contrast depends on the page behind. Computed, not sampled. Contrast needed for graphics is 3:1.

| Token set | Background | Card tint | Up | Down |
|---|---|---|---|---|
| Light | White or the light page | 15% | 3.8 to 4.2:1 | 3.9 to 4.2:1 |
| Light | White or the light page | 30% | 3.4 to 3.6:1 | 3.5 to 3.7:1 |
| Light | Light grey #E5E5E5 | 30% | 3.0:1 | 3.1:1 |
| Dark | Dark page | 15% | 5.8:1 | 5.5:1 |
| Dark | Dark page | 30% | 4.2:1 | 4.0:1 |
| Dark | Black | 30% | 5.6:1 | 5.3:1 |
| Either set | Mid grey | 15 to 30% | 1.2 to 1.6:1 | 1.3 to 1.6:1 |
| Light on dark page | Dark page | 15 to 30% | 2.1 to 2.9:1 | 2.0 to 2.8:1 |

- **Tint ceiling.** Keep the card tint at or below about 30%. Light tokens reach 3:1 only at that limit on a light grey page.
- **Tokens follow the effective background.** Pick the light or dark set by the lightness of the page behind the card, not only by the chosen theme. A mismatch falls to about 2:1.
- **Mid-tone pages cannot pass.** No fixed pair works there. The author's choice in the Color field is the way out.

### Open items

- Blocked on the product: can the product set the value range per panel? A Shared Range checkbox exists on value axes and is on by default. On a panelled chart the numbered value axis cannot be selected, so Axis Properties cannot be reached. Parked for now.
- Blocked on the build: the contrast figures use an estimated card colour. The measured real-card figures were 3.4:1 to 3.6:1 in light and 4.2:1 to 4.4:1 in dark, which differ from the estimates. The translucent card is not in the build yet, so the live captures on this page show the current white card with a thin border, on dashboards that are marked modern. They cannot supply the translucent card colour. Sample the card once the translucent card lands.

## Stock

### Decisions

- **Direction colour when no Color dimension is bound.** A day that closes at or above its open uses the up colour, and a day that closes lower uses the down colour. They are the same tokens as candle, with the same contrast results above. Stock has no hollow or filled body, so colour is the only direction cue.
- **A Color dimension switches to the series palette.** Unlike candle, stock stays readable with two tickers because there is no body to crowd the neighbour. When a dimension is bound the chart uses the modern series palette, and direction colour is dropped. The open and close ticks still show direction.
- **The author can still set the up and down colours.** The Color field sets them when no dimension is bound, and the author's choice wins over the defaults.
- **Axis titles are off, labels follow the common rule, value axis does not start at zero.** Same as candle.
- **Tick length.** Each open and close tick is at least about 3px, and the high to low line is 1px.
- **Several tickers as panels.** Same as candle, with the same unverified range behaviour.

### Proposed look

Drawn to the decisions at the default card size, with invented prices. The first two cards use direction colour with no Color dimension. The third has a ticker bound and uses the series palette.

## Light

## Dark

### Shipped today against the decisions

| Setting | Shipped today | Decided |
|---|---|---|
| Colour | One colour, the legacy blue. Not modern-gated. *Observed.* | Direction tokens with no dimension. Series palette with a dimension. |
| Color field | Accepts a dimension and colours each series. *Observed.* | Same, and with no dimension it sets the up and down colours. |
| Axis titles | Both on, with a Sum prefix on the measures. *Observed.* | Both off. |
| X labels | Rotated 90°, thinned when they overlap. *Observed.* | Common label rule. |
| Value axis | Does not start at zero. *Observed.* | Unchanged. |
| Data labels | Never drawn for stock. *Traced.* | Unchanged. |

### Variants

Captured from live charts on the same server dashboard and the same made-up data as candle, with every chart style and format at its default.

| Live capture | Variant | What it shows | What it means for the defaults |
|---|---|---|---|
| ![Stock 30 days (canonical)](../financial/img/stock_canonical.png) | 30 days (canonical) | One blue line per day with a tick on each side. Ticks are about 3px, so open and close are hard to read. Rising and falling days look the same. | Direction colour. Axis titles off. Label rule. |
| ![Stock 8 days](../financial/img/stock_few.png) | 8 days | The best case. Open and close ticks read clearly, but nothing shows direction. | Direction colour is the only cue to add. |
| ![Stock 120 days](../financial/img/stock_many.png) | 120 days | The days merge into one band. The rotated labels overlap. | Same thinning rule as candle above about 60 days. |
| ![Stock 2 tickers on the Color field](../financial/img/stock_two_tickers.png) | 2 tickers on the Color field | Each ticker is its own coloured series on a shared axis, with a legend. It stays readable because stock has no body to crowd the neighbour. | A Color dimension is allowed and switches to the series palette. |
| ![Stock 15-character day labels](../financial/img/stock_long_labels.png) | 15-character day labels | The rotated labels take a large share of the card height. | Label band cap and truncation. |
| ![Stock Small card, 300×180](../financial/img/stock_small.png) | Small card, 300×180 | The plot is only about 70px tall. The rotated labels overlap and the axis titles take the rest. | Titles off and the label rule with fewer labels, as for candle. |

### Build items for stock

| # | Item | Cost | Where | Notes |
|---|---|---|---|---|
| 1 | Direction colours when no dimension is bound | New behaviour | The stock painter, the stock colour frame setup in `GraphGenerator`, `VSChartPaletteDefaults` | Shares the tokens and the author override with candle item 1. The up and down test is close against open for each day. |
| 2 | Series palette when a Color dimension is bound | Small change | The stock colour frame setup in `GraphGenerator` | Dropping the dimension returns to direction colours. |
| 3 | Axis titles off for stock | Seed only | `TitleDescriptor`, `ChartVSAssemblyInfo.seedChromeDefaults` | Same as candle item 3. |
| 4 | Minimum tick length | Small change | The stock painter | Floor of about 3px on each side. |
| 5 | Label rule and panel range | Common | See the candle items | Not stock-specific. |

### Open items

- None. Dropping direction colour when a Color dimension is bound was confirmed.

## Box plot

### Decisions

- **One colour moves to the modern palette.** A box plot with no Color field draws the legacy blue. It becomes series colour 1, in line with the common rule for charts with no colour field. The white median stays.
- **A Color dimension stays as it is.** Boxes group side by side in the series palette with a legend. Observed to read well with two series.
- **Value axis is not forced to zero.** It follows the data, as shipped.
- **Axis titles.** The Y title stays, because it names the measure. The X title is off, because the category labels already name the dimension. Confirmed on 2026-11-03.
- **Labels follow the common label rule.** Thin above about 30 boxes, rotate last, truncate at about 12 characters.
- **Box width is a proportion of the category slot.** The box is about 30% of its slot, never narrower than 6px and never wider than 28px. With 3 regions the live chart draws boxes about 50px wide, which look heavy. With the cap they are 28px. With 5 regions they are about 20px, and with 20 regions the 6px floor keeps them visible. The whisker caps are half the box width.
- **Box corners follow the bar corner rule (C22).** Shipped draws each box with a radius hard-coded in the painter at about one sixth of its short side, on all four corners, and it does not read the bar corner setting. A box floats between its quartiles with no baseline, so both ends are free, as for the interval. The radius becomes about 0.08 of the box's short side with the 2px floor of C22 (2px on a typical box, 2.2px on the widest 28px box), on all four corners, and the painter reads the same seeded radius as bars, so an author's own radius wins. The median stays a white pill with rounded ends, and the whiskers and outlier dots are unchanged. Traced in `BoxPainter`, and confirmed on 2026-11-04.
- **A flat region stays visible.** A minimum box height of about 2px, so identical values show a thin box and not a hairline.
- **Outlier dots, median and whiskers are unchanged.**

### Proposed look

Drawn to the decisions at the default card size, with invented quartiles. The first card is one colour from the modern palette. The second has a Color field with two series.

## Light

## Dark

### Shipped today against the decisions

| Setting | Shipped today | Decided |
|---|---|---|
| Colour | One colour, the legacy blue. *Observed.* | Modern palette colour 1. |
| Color field | A text column groups boxes side by side in the series palette, with a legend. *Observed.* | Unchanged. |
| Color field with a number | Gradient legend, both ends labelled 2K. *Observed.* | Defect to log. |
| Axis titles | Both on, naming the measure and the dimension. *Observed.* | Y kept, X off (confirmed). |
| X labels | Rotated 90° when crowded, cut at about 16 characters with an ellipsis. *Observed.* | Common label rule. |
| Box corners | A radius of about one sixth of the short side, hard-coded in the painter, on all four corners. *Traced and observed.* | About 0.08 of the short side with a 2px floor, all four corners, from the shared setting (C22). |
| Box width | About 45% of the category slot with no limit, so 3 regions give boxes about 50px wide. *Observed.* | 30% of the slot, between 6px and 28px. |
| Flat region | Hairline with no box. *Observed.* | Minimum box height of about 2px. |
| Value axis | Follows the data, and starts at zero only when the data is wide. *Observed.* | Unchanged. |
| Legend | None for a single series. Series legend with a dimension. *Observed.* | Unchanged. |

### Variants

Captured from live charts on the same server dashboard, with every chart style and format at its default. The data is made up: five regions with 40 scores each, with different centres, spreads and outliers. Other variants change the region count, label length, series and add edge cases.

| Live capture | Variant | What it shows | What it means for the defaults |
|---|---|---|---|
| ![Box plot 5 regions, 40 values each (canonical)](../financial/img/box_canonical.png) | 5 regions, 40 values each (canonical) | One blue box per region with a white median, whiskers, and dots for outliers. Rounded boxes, no direction or value labels. | Move the single colour to the modern palette. |
| ![Box plot 3 regions](../financial/img/box_few.png) | 3 regions | The best case. Wide boxes and clear outlier dots. | Nothing to change. Use as the reference look. |
| ![Box plot 20 regions](../financial/img/box_many.png) | 20 regions | Boxes are about 8px wide and still readable. The 20 labels rotate 90° and take about a third of the card height. | Label rule. Thinning starts above about 30 boxes. |
| ![Box plot 27-character region names](../financial/img/box_long_labels.png) | 27-character region names | The labels are cut to about 16 characters with an ellipsis and rotated 90°. The plot shrinks to about half the card height. | Label band cap and truncation at about 12 characters. |
| ![Box plot 2 series on the Color field (text column)](../financial/img/box_two_series.png) | 2 series on the Color field (text column) | Boxes sit side by side within each region, in the series palette, with a legend on the right. It reads well. | Allowed as it is. Keep. |
| ![Box plot A number column on the Color field](../financial/img/box_numeric_color.png) | A number column on the Color field | The legend becomes a gradient ramp whose two ends are both labelled 2K, and the boxes shade light to dark. It looks like a defect. | Log as a defect for the legend number format. Not a default question. |
| ![Box plot Small card, 300×180](../financial/img/box_small.png) | Small card, 300×180 | The plot is about 85px tall. Both axis titles and the labels use the rest, and the boxes stay readable. | Same as the rest. Axis titles are the one cost, left to the common rule. |
| ![Box plot Edge cases: 3 values, all equal, extreme outliers, very wide](../financial/img/box_edge.png) | Edge cases: 3 values, all equal, extreme outliers, very wide | A region with identical values shows as a hairline with no box. Three values still draw a box. A wide spread stretches the axis and squeezes the other boxes. | A minimum box height of about 2px so a flat region stays visible. |

### Build items for box plot

| # | Item | Cost | Where | Notes |
|---|---|---|---|---|
| 1 | Single colour from the modern palette | Needs provenance | The box plot colour frame setup in `GraphGenerator`, `VSChartPaletteDefaults` | The colour is static legacy today and not modern-gated, so an author choice needs a way to tell it from the default. |
| 2 | Minimum box height and box width limits | Small change | The box plot painter | Height floor of about 2px. Width is 30% of the slot, clamped to 6px to 28px. |
| 2b | Box corner radius from the shared bar setting | Small change | The box plot painter, `BoxPainter`: the box is a `RoundRectangle2D` with an arc of the short side divided by 3, built in pixels. | Replace the constant with the seeded bar corner radius, about 0.08. Keep the arc in pixels, as the code comment explains, so a tall axis scale does not distort it. |
| 3 | Label rule | Common | `DefaultAxis` label layout | Tracked on the common page. |
| 4 | Gradient legend number format | Defect | The legend ramp label formatter | Both ends read 2K for 2024 and 2025. Separate from the defaults. |

### Open items

- None.
