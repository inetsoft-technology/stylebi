# Legend Padding — Design

**Date:** 2026-10-20 (decisions recorded 2026-10-21, grounded and amended 2026-10-22)
**Status:** Decided, ready for implementation
**Ticket:** Bug #79139
**Repo:** community only (single community PR)
**Size:** Small, roughly 5 Java files and 4 TS/HTML files
**Builds on:** [2026-10-20-chart-spacing-controls-design.md](./2026-10-20-chart-spacing-controls-design.md)
and its implementation (Bug #79136, PR #7259), which adds the axis label and axis title distances,
relabels "Legend Gap" to "Distance from Chart" and makes those distances follow density. This design
touches some of the same files (§6.6) and follows the rules that implementation settled (§3, rows 6-8).

## 1. What is being built

Legend padding can only be set through `format.css` today (`padding:` on the legend selector,
`CSSChartStyles.java:131-132`) or script (`LegendSpec.setPadding`, a public `@TernMethod`). This
design exposes it in the Legend Format dialog and gives it a density default, so legends scale
with the dashboard's density like the other chart spacing.

The value is one `Insets` that the engine applies to several parts of the legend in different ways
(§2). Showing it as a plain "Padding: top/left/bottom/right" control would mislead users, so the UI
presents it as two pairs that say what they actually do: **Row Spacing** and **Side Indent**.

## 2. What the engine does with legend padding

### 2.1 One value, three interpretations

| Legend part | Effect of padding | Code |
|---|---|---|
| **Title** | All four sides become insets around the title text. | `Legend.java:596-602` |
| **Items** (symbol + label rows) | Top and bottom are added to **every row's height**. Left and right are applied **once** to the whole item block, as an indent from the border. | `LegendItem.java:59-63`, `LegendItem.java:166-172`, `Legend.java:514-519` |
| **Gradient band** (continuous color) | All four sides become insets around both the min and max labels. | `Legend.java:640-651` |

So top/bottom padding acts as **row spacing**. A legend with 10 items and `top = 5` gets about
50px taller, not 5px.

Inside a row, the symbol and the label are both centred in the content band between the
paddings: `top` px sit above them and `bottom` px below (`LegendItem.java:195-240`). The label
stays centred on its symbol for any top / bottom pair, and the label box never leaves its row.
Before #79398 the symbol was centred on the whole row and the label was offset by `bottom - top`,
so unequal values (e.g. 0 / 10) pushed the label off its symbol and the first label into the title.

### 2.2 Setting it replaces all the built-in defaults at once

When padding is `null`, the engine uses fixed defaults that differ by part:

| Part | Left | Right | Top | Bottom | Source |
|---|---|---|---|---|---|
| Title | 4 | 4 | 0 | 0 | `Legend.java:1215-1216` |
| Items | 4 | 4 | 0 | 1 | `Legend.java:1217-1218`, `LegendItem.java:305-306` |
| Gradient min/max labels | 0 | 0 | 0 | 0 | `Legend.java:648-651` (insets only set when non-null) |

As soon as padding is non-null, every one of these defaults is replaced by the single value. No
single `Insets` value reproduces the built-in title, item and gradient defaults together.

### 2.3 Multiple legends

- Padding lives on `LegendsDescriptor`, so the same value is copied into every legend's
  `LegendSpec` (`GraphGenerator.java:1744`, `:1824`, `:1860`). Each legend applies it on its own.
  The setting therefore applies to **all legends** on the chart, the same as Distance from Chart.
- `LegendGroup` places legends directly against each other (`LegendGroup.java:123-185`). Nothing
  controls the space between them; that stays out of scope (§3).

### 2.4 Stored values

Padding has been a tiered `CompositeValue<Insets>` since the repository's initial commit, and is the
same in 13.7. It is written only when the author tier is set (`CompositeValue.toString`), so unlike
the chart gaps there is no residue from older builds to tell apart: none of the 31 legend
descriptors in the customer sample assets carries a `padding` attribute. No save marker is needed.
A bookmark restore builds a fresh `ChartDescriptor` (`ChartVSAssembly:594-596`), and
`CompositeValue.clone()` copies each tier's value.

## 3. Decisions

| # | Question | Decision |
|---|---|---|
| 1 | Which option? | **Option A**: expose the existing `Insets` with descriptive labels, plus a density default. Options B and C are recorded in §8. |
| 2 | Labels and starting values | **"Row Spacing"** (top / bottom) and **"Side Indent"** (left / right). On compact, the starting values are T0 / L4 / B1 / R4. |
| 3 | Precedence for split properties | Not applicable (Option B only). |
| 4 | Space between legends | **Out of scope** for now. |
| 5 | Density default | **Yes. Compact keeps the engine's built-in defaults** (`null`); dense and comfortable get explicit values (§4). Design accepts that on dense and comfortable the values also set the title's insets and the gradient labels' insets (§2.2). |
| 6 | Checkbox when only format.css sets padding | **Ticked whenever the author tier is empty**, as the Distance from Chart row in the same dialog does (chart spacing D2). The steppers show the format.css value, disabled. The chart draws the same either way: a format.css value is still an opinion to the resolver. |
| 7 | Unticking on compact and pressing OK without edits | **Store the displayed values**, as the Distance from Chart row stores its value. The result is `Insets(0, 4, 1, 4)`, which differs from the built-in look by 1px below the title and, on a gradient legend, by 4px side insets and a 1px bottom inset around the min/max labels. Accepted. |
| 8 | Layout | **One row of four steppers under two captions** (§5). |

## 4. Density defaults

| Density | Resolved value | Row spacing (top / bottom) | Side indent (left / right) |
|---|---|---|---|
| Dense | `Insets(0, 2, 0, 2)` | 0 / 0 | 2 / 2 |
| Compact | `null` (built-in) | shown as 0 / 1 | shown as 4 / 4 |
| Comfortable | `Insets(1, 6, 2, 6)` | 1 / 2 | 6 / 6 |

(`Insets` constructor order is top, left, bottom, right.)

What each density looks like compared with compact:

- **Dense:** each item row is 1px shorter and its label sits centred on the symbol instead of 1px
  off (§2.1); title and items are indented 2px instead of 4px; gradient min/max labels get a 2px
  left/right inset.
- **Compact:** exactly the built-in legend, as today.
- **Comfortable:** each item row is 2px taller (10 items ≈ +20px); title and items are indented
  6px; the title gets 1px above and 2px below; gradient min/max labels get the full inset.

Legacy charts are not affected: the resolver returns the stored value unchanged when
`!ctx.modern`, and legacy charts have no density.

## 5. UI

In **Legend Format → General**, below the existing "Distance from Chart" row:

```
Row Spacing              Side Indent
[ 0 ▲▼ ] [ 1 ▲▼ ]        [ 4 ▲▼ ] [ 4 ▲▼ ]
 Top      Bottom          Left     Right
[x] Follow the default density
```

- Four `number-stepper`s, `[min]="0"`, in one row: two groups, each captioned by a `form-label`
  ("Row Spacing", "Side Indent") with its steppers labelled below as the padding pane does
  ("Top", "Bottom", "Left", "Right").
- One "follow default" checkbox for all four, reusing the key `composer.vs.followDefaultDensity`.
  Shown **only on modern charts**, like the Distance from Chart checkbox. It is ticked whenever the
  author has not set a value (decision 6). While it is ticked, the four steppers are disabled and
  show the value the chart draws: the format.css value when there is one, otherwise the density
  value (§4). On compact with no format.css value, that's the built-in item values 0 / 1 / 4 / 4.
- When the user unticks it, the steppers keep the displayed values as the starting point. Saving
  them unchanged stores them (decision 7).
- Do **not** reuse `<padding-pane>`. Its Top/Left/Bottom/Right framing under a "Padding" legend is
  the misleading framing this design avoids.
- Tooltips: `[title]` on each group, the dialog convention the chart spacing work settled (C4).
  `.field-help-icon` is styled only inside the formats pane.
  - Row Spacing: "Space added above and below each legend item and the legend title. Applies to
    all legends."
  - Side Indent: "Indent of the legend title and items from the legend edge. Applies to all
    legends."

### i18n (`core/src/main/resources/inetsoft/util/srinter.properties`)

```
Row\ Spacing=Row Spacing
Side\ Indent=Side Indent
composer.vs.chart.legendRowSpacingTip=Space added above and below each legend item and the legend title. Applies to all legends.
composer.vs.chart.legendSideIndentTip=Indent of the legend title and items from the legend edge. Applies to all legends.
```

The field labels Top, Bottom, Left and Right already exist (`_#(Top)` etc., used by
`padding-pane.component.html`).

## 6. Implementation

### 6.1 `LegendsDescriptor` (`uql/viewsheet/graph/LegendsDescriptor.java`)

Add, mirroring `resetGap` / `hasGapValue` / `hasGapUserValue`:

```java
public boolean hasPaddingValue() {
   return padding.hasUserValue() || getPadding() != null;
}

public boolean hasPaddingUserValue() {
   return padding.hasUserValue();
}

public void resetPadding(CompositeValue.Type type) {
   padding.resetValue(type);
}
```

`hasPaddingValue()` is true for a USER **or** CSS value, so a stylesheet's `padding:` counts as an
opinion and is never replaced by the density default. This matches how `hasGapValue()` treats a
CSS `legend_gap`. `hasPaddingUserValue()` drives the dialog's checkbox (decision 6).

### 6.2 `VSChartChromeDefaults` (`uql/viewsheet/internal/VSChartChromeDefaults.java`)

```java
/**
 * Resolve the legend padding: the density value when the context is modern and the descriptor
 * has no opinion, otherwise unchanged. Null means the engine's built-in legend insets.
 */
public static Insets resolveLegendPadding(Insets current, boolean hasOpinion, VizContext ctx) {
   return ctx.modern && !hasOpinion ? legendPaddingForMode(ctx.density) : current;
}

/** Legend padding for a density mode; compact is null, the engine's built-in insets. */
static Insets legendPaddingForMode(String mode) {
   switch(mode) {
   case VSDensityDefaults.COMFORTABLE:
      return new Insets(1, 6, 2, 6);
   case VSDensityDefaults.COMPACT:
      return null;
   default:
      return new Insets(0, 2, 0, 2);
   }
}
```

- Return a fresh `Insets` on every call; the type is mutable (same rule as
  `VSDensityDefaults.chartPaddingForMode`).
- Unrecognized modes fall back to dense, matching the other per-mode functions.
- The mode constants are package-visible since the chart spacing work.

### 6.3 `GraphGenerator` (`report/composition/graph/GraphGenerator.java`)

At lines 1744, 1824 and 1860, replace `legend.setPadding(legends.getPadding())` with:

```java
legend.setPadding(VSChartChromeDefaults.resolveLegendPadding(
   legends.getPadding(), legends.hasPaddingValue(), vizContext));
```

These are the only places legend padding reaches the engine. Script runs after generation
(`VGraphPair.Creator.createGraph`), and no script property writes the descriptor's padding, so a script's
`LegendSpec.setPadding` still overrides everything, as today.

### 6.4 Legend Format dialog model

- **`web/graph/model/dialog/LegendFormatGeneralPaneModel.java`:** add `PaddingPaneModel padding`
  (reuse the data class only, not the component). Its existing `followsDefault` field (null =
  checkbox hidden) carries the checkbox state.
- **`LegendFormatDialogModel.java`** (constructor at line 43 and `updateLegendFormatDialogModel` at
  line 120 already receive `VizContext ctx`):
  - **Read:** `Insets shown = resolveLegendPadding(legendsDesc.getPadding(),
    legendsDesc.hasPaddingValue(), ctx)`; if `shown == null`, use the built-in item values
    `Insets(0, 4, 1, 4)`. Fill `padding` from `shown`. Set
    `padding.followsDefault = ctx.modern ? !legendsDesc.hasPaddingUserValue() : null`.
  - **Write**, using the same three-way logic as the gap at `LegendFormatDialogModel.java:144-164`:
    - `followsDefault == null` (legacy): call `setPadding(new Insets(...))` only if the four values
      differ from what was shown. Opening and saving without edits must write nothing.
    - `true`: `legendsDesc.resetPadding(CompositeValue.Type.USER)`.
    - `false`: `legendsDesc.setPadding(new Insets(top, left, bottom, right))`.
    - No `padding` in the request (an older client): write nothing.
  - Keep the built-in `Insets(0, 4, 1, 4)` as a named value next to the dialog model logic, with a
    comment pointing at `Legend` and `LegendItem`.

### 6.5 Frontend (`web/projects/portal/src/app/graph/`)

| File | Change |
|---|---|
| `model/dialog/legend-format-general-pane-model.ts` | add `padding: PaddingPaneModel` (import from `vsobjects/model/padding-pane-model.ts`) |
| `dialog/legend-format-general-pane.component.html` | add the Row Spacing / Side Indent row and the follow-default checkbox row below the Distance from Chart row (§5) |
| `dialog/legend-format-general-pane.component.ts` | `showPaddingFollowDefault = model.padding?.followsDefault != null`; a `paddingFollowDefaultChanged(follows)` handler like `gapFollowDefaultChanged` |

### 6.6 Overlap with the spacing work

The spacing implementation (PR #7259, on the spec PR #7114) also changes
`legend-format-general-pane.component.html`, `VSChartChromeDefaults`, `LegendFormatDialogModel`
and `ChartGapResolverTest`. This work is a branch on top of it (`bug-79139` on `bug-79136`). The two
changes are independent in behaviour.

## 7. Verification

### 7.1 Tests

- **`legendPaddingForMode`:** dense → `Insets(0, 2, 0, 2)`, compact → `null`, comfortable →
  `Insets(1, 6, 2, 6)`, unrecognized → dense value; two calls return different instances.
- **`resolveLegendPadding`:** modern + no opinion → density value; modern + opinion → current
  (including a CSS-tier value); legacy → current, whether or not it is null.
- **Render:** a modern chart's legends receive the dashboard density's padding; a legacy chart's
  receive the stored value.
- **Dialog model round trip** (next to the existing legend dialog tests):
  - Compact + no value: shows 0 / 4 / 1 / 4 with follow-default checked.
  - CSS value only: shows the CSS value with follow-default checked.
  - Follow-default `true` clears the USER tier, so a CSS padding shows through again on re-read.
  - `false` with explicit values persists at the USER tier and survives `clone()` and XML
    write/parse.
  - Legacy (`null`): open and save with no edit writes nothing (`hasPaddingValue()` stays false).
- **Frontend** (`legend-format-general-pane.spec.ts`): the four steppers are disabled while
  follow-default is checked; the checkbox is hidden when `followsDefault` is null; the captions read
  "Row Spacing" and "Side Indent".

### 7.2 Manual checks

Use a modern dashboard and a legacy dashboard, each with a bar chart that has a color legend
(categorical), a size legend, and a second chart with a gradient (continuous color) legend.

1. **Compact, defaults:** legends look exactly as before. The dialog shows 0 / 1 and 4 / 4 with
   follow-default checked.
2. **Dense / comfortable:** switch the dashboard density. Rows tighten or loosen and the indent
   changes as in §4, on all legends of the chart. On dense, each label sits centred on its symbol.
   The dialog's shown values match the density.
3. **Explicit value:** uncheck follow-default, set row spacing 3 / 3; every row of every legend
   grows by 5px (3 + 3 against the built-in 0 + 1). Switch density: the explicit value stays.
4. **Back to default:** check follow-default again; the legend returns to the density default.
5. **CSS:** with `format.css` setting legend `padding: 5px`, the checkbox shows **checked** with 5s,
   disabled. Uncheck and set an explicit value: it wins. Check follow-default: the CSS value applies
   again. OK without edits stores nothing.
6. **Legacy:** no checkbox; opening and saving without edits changes nothing; setting values works.
7. **Unticking on compact without edits:** the title gains 1px below; a gradient legend's min/max
   labels move 4px in from each side (decision 7). Nothing else moves.
8. **Legend positions:** right, bottom and top; two legends stacked; a gradient legend at each
   density.
9. **Undo/redo**, save and reload, and PDF/PNG export match the screen.

## 8. Options considered

- **Option A (chosen):** expose the existing `Insets` as Row Spacing and Side Indent, with a
  density default. No legend layout code changes.
- **Option B: split into separate properties** (title padding, row spacing, item indent, space
  between legends). Each control would do one thing, but it needs new fields on
  `LegendsDescriptor` and `LegendSpec`, new layout logic in `Legend`, `LegendItem` and
  `LegendGroup`, XML/CSS/script support, and a precedence rule with the existing public
  `LegendSpec.setPadding`. Rejected for now as too large for the benefit; revisit if users need
  to space the title separately from the rows.
- **Option C: leave it CSS-only.** Rejected: it leaves the deficiency that started this work.
- **Space between legends:** out of scope (decision 4). It is a `LegendGroup` layout feature that
  doesn't depend on padding and can be designed on its own if needed.
