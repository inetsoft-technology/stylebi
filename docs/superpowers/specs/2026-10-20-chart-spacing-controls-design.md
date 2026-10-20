# Chart Spacing Controls — Design

**Date:** 2026-10-20
**Covers:** axis label distance, axis title distance, the "Legend Gap" relabel, and density-scaled
defaults for all three
**Status:** Ready for implementation (density values in §5.5 approved 2026-10-20)
**Repo:** community only (no enterprise changes, single community PR)
**Size:** Small–medium, roughly 10 Java files and 6 TS/HTML files
**Follow-up:** legend padding is split into a separate review:
[2026-10-20-legend-padding-review-design.md](./2026-10-20-legend-padding-review-design.md)

## 1. Problem

Charts let designers style many inner parts (axis labels, axis titles, legends) through dialogs and
the Format pane, but the spacing around axis labels and axis titles can only be changed through
`format.css` or script. The engine and the descriptors already support these values; nothing in
the UI exposes them. The legend-to-plot distance is the one spacing value already in the UI, under
the label "Legend Gap".

| Spacing | Stored on | Editable today | This work |
|---|---|---|---|
| Axis labels ↔ axis line | `AxisDescriptor.labelGap` | CSS `label_gap`, script `AxisSpec.setLabelGap` | Add to UI |
| Axis title ↔ axis labels/plot | `TitleDescriptor.labelGap` | CSS `label_gap` | Add to UI |
| Legend ↔ plot | `LegendsDescriptor.gap` | Legend Format dialog ("Legend Gap"), CSS `legend_gap` | Relabel only |
| Legend inner padding | `LegendsDescriptor.padding` | CSS `padding` | Out of scope (follow-up) |

## 2. Goal

1. Expose the axis label and axis title distances in the chart's existing per-element property
   dialogs, following the modern-default behaviour that Legend Gap already has.
2. Relabel "Legend Gap" so that all three distance settings use the same wording, which describes
   what moves.
3. Make the modern defaults for all three distances follow the dashboard's **density** (dense /
   compact / comfortable), the same way the chart card padding already does. A value the user sets
   always overrides the density default. Today these three defaults are fixed numbers, so the
   "Follow the default density" checkbox would follow a value that ignores density.

### Terminology (important for reviewers and UI copy)

A "gap" in the engine is **not** CSS padding. It is a single number applied to **one side only,
the side facing the plot**, which makes it closer to a one-sided margin:

- **Axis label gap**: space perpendicular to the axis only (vertical for X axes, horizontal for
  Y axes). See `DefaultAxis.getLabelGapForSize` (`DefaultAxis.java:2081`).
- **Axis title gap**: one inset on the plot-facing side of the title (top for X, bottom for X2,
  right for Y, left for Y2). See `VGraph.java:335-356`.
- **Legend gap**: one side, depending on legend position. Applied once to the whole legend group.

For this reason the UI must never say "gap". The visible labels are in §4. Internal names (fields,
XML attributes, CSS properties, script API) **do not change**.

## 3. Non-goals

- **Legend padding** (`LegendsDescriptor.padding`). Its top/bottom values space every item row,
  setting it replaces several built-in defaults at once, and it is shared by all legends. It needs
  a design decision first; see the follow-up review linked above.
- Format pane changes. Spacing is a descriptor (layout) property, not a `CompositeTextFormat`
  property. Adding it to the Format pane would also mean defining Format Painter, Reset and
  multi-select behaviour for it. The dialogs avoid all of that.
- Chart **title** padding (`TitleInfo.padding`). That setting is shared by every titled assembly
  type and should be handled as a cross-assembly change.
- Data label offset, plot area margins, facet spacing, space between legends. These need new
  engine properties.
- Format Painter copying spacing (Legend Gap is not copied today either).
- Renaming CSS properties (`label_gap`, `legend_gap`) or script methods.

## 4. UI specification

Every row has a number input plus a "follow default" checkbox. The checkbox is shown **only on
modern charts** (`VizContext.modern`), exactly like the current Legend Gap row. When it is checked,
the input is disabled and shows the resolved default value **for the dashboard's current density**
(§5.5).

| Dialog / tab | Label (new i18n key) | Control | Min | Tooltip |
|---|---|---|---|---|
| Axis Properties → Label | `Label Distance from Axis` | `number-stepper` (new) | **1** | Space between the axis line and its labels. Applies perpendicular to the axis only. |
| Axis Title Format | `Title Distance from Axis` | `number-stepper` (new) | 0 | Space between the axis title and the axis labels (or the plot, when labels are hidden). The default varies with label visibility. |
| Legend Format → General | `Distance from Chart` (replaces `Legend Gap`) | existing `number-stepper`, unchanged | 0 | Space between the legends and the plot area. Applies to all legends. |

- Checkbox label: reuse the existing key `composer.vs.followDefaultDensity` ("Follow the default
  density"). Do not add a new key.
- Why min = 1 for axis labels: `DefaultAxis.getLabelGap()` (`DefaultAxis.java:2111-2115`) renders
  `0` as `2px` for backward compatibility. Allowing 0 in the UI would show a value that the chart
  does not honour. Do not change that engine fallback.

### i18n (`core/src/main/resources/inetsoft/util/srinter.properties`)

Add:

```
Label\ Distance\ from\ Axis=Label Distance from Axis
Title\ Distance\ from\ Axis=Title Distance from Axis
Distance\ from\ Chart=Distance from Chart
```

Plus one key per tooltip (use the `composer.vs.*` dotted style that the follow-default key uses,
for example `composer.vs.chart.labelDistanceTip`).

**Remove** `Legend\ Gap=Legend Gap` (line ~1694). It is referenced only by
`legend-format-general-pane.component.html:78`, has no translations and is not used by e2e tests.

## 5. Backend changes

### 5.1 "Unset" vs explicit zero (must do first)

`AxisDescriptor.labelGap` and `TitleDescriptor.labelGap` are `CompositeValue<Integer>` with default
`0`, and `0` is also the "no opinion" marker. In modern mode the resolvers replace `0` with the card
default, so a user who sets `0` would silently get 8 (or 4).

1. **`AxisDescriptor`** (`uql/viewsheet/graph/AxisDescriptor.java`): add

   ```java
   public boolean hasLabelGapValue() {
      return labelGap.hasUserValue() || getLabelGap() != 0;
   }

   public void resetLabelGap(CompositeValue.Type type) {
      labelGap.resetValue(type);
   }
   ```

   Mirror `LegendsDescriptor.hasGapValue()` / `resetGap()` (`LegendsDescriptor.java:332-349`).
   Do not reuse `resetCompositeValues(USER)`, which also resets unrelated values.
2. **`TitleDescriptor`** (`uql/viewsheet/graph/TitleDescriptor.java`): add the same two methods.
3. **`VSChartChromeDefaults`** (`uql/viewsheet/internal/VSChartChromeDefaults.java:105-127`): add a
   `hasOpinion` parameter, matching `resolveLegendGap`:

   ```java
   public static int resolveAxisLabelGap(int current, boolean hasOpinion, VizContext ctx) {
      return ctx.modern && !hasOpinion ? AXIS_LABEL_GAP : current;
   }

   public static int resolveAxisTitleGap(int current, boolean hasOpinion,
                                         boolean abuttingLabelsDrawn, VizContext ctx)
   {
      if(!ctx.modern || hasOpinion) {
         return current;
      }

      return abuttingLabelsDrawn ? AXIS_TITLE_GAP : AXIS_LABEL_GAP;
   }
   ```

   Update the Javadoc so it no longer says that zero is the unset marker.

### 5.2 GraphGenerator (`report/composition/graph/GraphGenerator.java`)

- **Line 2671:** pass `axisD.hasLabelGapValue()`.
- **Line 6317:** the custom-title branch currently ignores the descriptor's gap on legacy charts.
  Change it so a value the user sets applies on legacy charts too, while legacy charts without one
  render exactly as before:

  ```java
  boolean useGap = vizContext.modern || titleDesc.hasLabelGapValue();
  int customTitleGap = useGap ? titleDesc.getLabelGap() : 0;
  tSpec.setLabelGap(VSChartChromeDefaults.resolveAxisTitleGap(
     customTitleGap, titleDesc.hasLabelGapValue(), abuttingLabelsDrawn, vizContext));
  ```
- **Line 6345:** pass `titleDesc.hasLabelGapValue()`.

These are the only production callers (verified by grep).

### 5.3 Axis Properties dialog

- **`web/graph/model/dialog/AxisLabelPaneModel.java`:** add `int labelGap` and
  `Boolean labelGapFollowsDefault` (null = checkbox hidden), with getters and setters.
- **`AxisPropertyDialogModel.java`:** the constructor (line 41) and
  `updateAxisPropertyDialogModel` (line 235) already receive `VizContext ctx`.
  - Constructor:
    `labelGap = resolveAxisLabelGap(axisDesc.getLabelGap(), axisDesc.hasLabelGapValue(), ctx)`;
    `labelGapFollowsDefault = ctx.modern ? !axisDesc.hasLabelGapValue() : null`.
  - Update: copy the three-way logic from `LegendFormatDialogModel.java:140-160`:
    - `followsDefault == null` (legacy): write only if the value differs from what was shown.
    - `true`: `axisDesc.resetLabelGap(CompositeValue.Type.USER)`.
    - `false`: `axisDesc.setLabelGap(value)`.
- Scope note: the dialog edits whichever `AxisDescriptor` `ChartRegionHandler.getAxisDescriptor`
  resolves for the clicked axis, the same as the other label and line options in this dialog. No
  new resolution logic is needed.

### 5.4 Axis Title Format dialog

- **`TitleFormatPaneModel.java`:** add `int labelGap` and `Boolean labelGapFollowsDefault`.
- **`TitleFormatDialogModel.java`:** add a `VizContext ctx` parameter to the constructor (line 31)
  and to `updateTitleProperties` (line 63). Apply the same read and three-way write logic as §5.3,
  using `resolveAxisTitleGap`. For the displayed default, pass `abuttingLabelsDrawn = true`.
- **Callers:** `composer/vs/dialog/RegionPropertyDialogService.java:266` and `:293`. Pass
  `VizContext.of(chartAssemblyInfo)`, as the axis and legend paths in the same class already do.
  Grep for any other `new TitleFormatDialogModel(` or `updateTitleProperties(` callers (binding
  editor, wizard) and update them too.

### 5.5 Density-scaled defaults

**Today:** `VSChartChromeDefaults` uses three fixed constants for every modern chart
(`VSChartChromeDefaults.java:156-158`): `AXIS_TITLE_GAP = 4`, `AXIS_LABEL_GAP = 8`,
`LEGEND_GAP = 14`. The resolvers already receive `VizContext`, which carries `ctx.density`, but
ignore it. The chart card padding, by contrast, already scales with density
(`VSDensityDefaults.chartPaddingForMode`, `VSDensityDefaults.java:483-498`: 8 / 12 / 16).

**Change:** replace the three constants with per-mode functions in `VSChartChromeDefaults`,
following the shape of `chartPaddingForMode`. Use them from `resolveAxisLabelGap`,
`resolveAxisTitleGap` and `resolveLegendGap` when the chart is modern and the descriptor has no
opinion.

```java
static int axisLabelGapForMode(String mode) { ... }
static int axisTitleGapForMode(String mode) { ... }
static int legendGapForMode(String mode) { ... }
```

Keep them package-private and static, like `chartPaddingForMode`, so they can be tested directly.
Unrecognized modes fall back to the dense value, matching `chartPaddingForMode`.

**Values (approved):**

| Default | Dense | Compact | Comfortable |
|---|---|---|---|
| Axis label distance (`AXIS_LABEL_GAP`) | 6 | **8** | 12 |
| Axis title distance (`AXIS_TITLE_GAP`) | 2 | **4** | 6 |
| Legend distance from chart (`LEGEND_GAP`) | 10 | **14** | 18 |

Rules behind the values:

- **Compact equals the current modern values.** Compact is the org default
  (`VSDensityDefaults.mode()` returns `compact` when `viewsheet.density` is unset), and this is the
  same rule `chartPaddingForMode` follows.
- Dense and comfortable step down and up in proportion to the card padding (8 / 12 / 16).
- The legend values stay 2px below the intended total: `VGraph` adds a fixed 2px between the legend
  area and the content (see the Javadoc on `resolveLegendGap`).
- Dense axis label distance must stay ≥ 1 (the UI minimum in §4).

Update the comment block at `VSChartChromeDefaults.java:154-158` to describe the per-mode scale.

**Why this needs no migration:** these defaults are resolved every time the chart is generated, not
seeded into the descriptor. Changing a dashboard's density therefore updates every chart that
follows the default, and leaves charts with a user-set value alone (the `hasOpinion` change in
§5.1). Legacy charts are unaffected because each resolver returns early when `!ctx.modern`.

**The dialogs show the same values.** The dialog models build `VizContext.of(chartAssemblyInfo)`,
which resolves the dashboard's own density (falling back to the org's). So the value shown next to
a checked "follow default" box matches what the chart renders.

### 5.6 Legend Format dialog

No backend change. The relabel is frontend and i18n only (§4, §6). The default shown in the legend
distance field now varies with density through `resolveLegendGap` (§5.5), with no dialog code
change.

## 6. Frontend changes (`web/projects/portal/src/app/graph/`)

| File | Change |
|---|---|
| `model/dialog/axis-label-panel-model.ts` | add `labelGap: number; labelGapFollowsDefault?: boolean;` |
| `dialog/axis-label-pane.component.{ts,html}` | new row: `number-stepper [min]="1"` plus follow-default checkbox, structured exactly like `legend-format-general-pane.component.html:72-93` |
| `model/dialog/title-format-pane-model.ts` | add `labelGap`, `labelGapFollowsDefault?` |
| `dialog/title-format-pane.component.{ts,html}` | new row, same pattern, `[min]="0"` |
| `dialog/legend-format-general-pane.component.html` | line 78: change `_#(Legend Gap)` to `_#(Distance from Chart)` and add its tooltip; nothing else changes |

Follow-default behaviour (matching `legend-format-general-pane.component.ts:155-170`): show the
checkbox when the model value is non-null. While it is checked, disable the input. When the user
unchecks it, keep the displayed resolved value as the starting point.

Add tooltips with the existing field-help pattern (`help-question-mark-icon field-help-icon` with
`ngbTooltip`, as in `vs-formats-pane.component.html:125-128`).

## 7. Tests

### Java (`core/src/test/java/...`)
- **Extend `inetsoft/uql/viewsheet/internal/ChartGapResolverTest.java`** (it already covers both
  resolvers) for the new signature:
  - modern + no opinion + 0 → card default (8 / 4 / 8)
  - modern + **opinion + 0 → 0** (title) — the regression this work fixes
  - legacy + no opinion → unchanged
  - CSS-tier value (non-zero, no USER) → treated as an opinion
  - The existing assertions (8 / 4 / 14 for a modern chart with no opinion) keep passing unchanged,
    because `VizContext.of(VizMark.MODERN_LIGHT)` takes the org density, which is compact by default.
    Make sure the test does not depend on a `viewsheet.density` property left set by another test.
- **Per-mode functions:** test `axisLabelGapForMode`, `axisTitleGapForMode` and `legendGapForMode`
  directly for `dense`, `compact`, `comfortable` and an unrecognized mode (→ dense value).
- **Dialog model round trips** for Axis and Title (new tests next to the models):
  - follow-default `true` clears the USER tier, so a CSS value shows through again after
    re-reading.
  - `false` with an explicit value persists at the USER tier and survives `clone()` and XML
    write/parse.
  - Legacy (`null`): opening and saving with no edit writes nothing (`hasUserValue()` stays false).
- **GraphGenerator legacy custom title:** a legacy chart with a custom title and no user gap
  renders with gap 0 (unchanged); with a user gap of 6, it renders 6.

### Frontend (`*.spec.ts`)
- `axis-label-pane`: stepper disabled while follow-default is checked; checkbox hidden when the
  model value is null; min is 1. (There is already `axis-label-pane.tl.spec.ts`; add plain-spec
  coverage, or extend the TL spec if DOM assertions are easier there.)
- `title-format-pane`: same.
- `legend-format-general-pane.spec.ts`: label reads "Distance from Chart".

## 8. Manual verification

Use one **modern** dashboard and one **legacy** (unmarked) dashboard with a bar chart that has a
color legend and both axis titles shown.

1. Modern, compact density (the default), defaults: open each dialog. Follow-default is checked and the shown values are 8 (axis
   labels), 4 (title), 14 (legend distance). The chart looks unchanged.
2. Modern: uncheck follow-default, set the axis label distance to 1 and the title distance to 0.
   The labels move close to the axis line and the title sits flush against the labels. Re-open the
   dialogs: the values persist and the checkbox is unchecked.
3. Modern: check follow-default again. The chart returns to its defaults.
4. CSS: with `format.css` setting `label_gap: 20` on the axis, follow-default shows 20. Set an
   explicit value: it wins over CSS. Follow-default: CSS applies again.
5. Legacy: the dialogs show no follow-default checkbox. Opening and pressing OK without edits
   changes nothing (compare with an export before the change). Setting values works.
6. Legacy + custom axis title text + title distance 10: the gap applies (it was ignored before).
7. Legend Format dialog: the field reads "Distance from Chart" and behaves exactly as Legend Gap
   did.
8. Undo/redo after each dialog OK; save, reload, and check the values persist; export to PDF/PNG
   and confirm the spacing matches the screen.
9. Max mode: the axis label distance applies there too (the same descriptor is used).
10. Density: on a modern dashboard with defaults, switch the dashboard density between dense,
    compact and comfortable. The axis label, axis title and legend spacing change with the card
    padding. Compact looks identical to before this change. The values shown in the three dialogs
    match the current density.
11. Density + user value: set an explicit axis label distance, then switch density. The explicit
    value stays; only the other two distances change.
12. Org vs dashboard density: with the org set to comfortable and one dashboard overriding it to
    dense, the dashboard's charts use the dense values.

## 9. Risks

- **Legacy charts must render exactly as before.** Two changes reach legacy rendering: the resolver
  `hasOpinion` argument (each resolver still returns early when `!ctx.modern`) and the legacy
  custom-title condition in §5.2 (only changes the result when the user has set a title distance).
  The resolver tests and manual checks 5 and 6 cover this. Legend layout code is not touched.
- **Legacy open-and-save must write nothing.** Opening the Axis Properties or Axis Title dialog on
  a legacy chart and pressing OK without edits must not store a USER-tier gap; otherwise the
  explicit-zero semantics in §5.1 would change that chart's spacing. Covered by the dialog model
  round-trip tests and manual check 5.
- **Density change must regenerate the chart.** This should already happen, because the card
  padding depends on density. Confirm it with manual check 10; if a cached chart image survives a
  density switch, that is an existing bug that this change makes more visible.
- **`TitleFormatDialogModel` signature change:** compile errors will surface every caller; make
  sure none in the binding editor or wizard are missed.

## 10. PR

- Branch: `feature-<redmine#>` in `community/`.
- Community-only PR; no enterprise PR is needed beyond the usual submodule bump.
- The PR description should include the terminology note from §2, before/after screenshots of
  the three dialogs, and a chart at each density showing the new defaults.
