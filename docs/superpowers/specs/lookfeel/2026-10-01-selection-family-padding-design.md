# Selection family — card inset, cell padding, and a density-aware default size

**Date:** 2026-10-01
**Types in scope:** selection list, selection tree.
**Scoped out 2026-10-02:** the selection container. It was in scope when this spec was written and
is no longer; the decision and its consequences are recorded at the points they touch, and §11
collects them.
**Not in scope:** slider and range slider. `SliderVSAssemblyInfo` sits in `isCornerSeedTarget()`
alongside the selection family, but its own javadoc assigns it to form-input modernization, which
is a separate project.

## 1. Why

A selection list's rows run flush to its border while the table beside it has breathing room. That
is the inconsistency §1 of the density design says this work exists to remove, and the selection
family is the largest remaining group inside `isCornerSeedTarget()` that has not taken the card
inset.

A second problem is folded in because it cannot be separated from the first. Density already cost
a default-size selection list two of its rows and nothing was resized to compensate:

| | title | cell | rows visible at the 100×120 default |
|---|---|---|---|
| legacy | 20 | 20 | 5 |
| modern, today | 30 | 28 | **3** |

Adding a 16px inset without addressing that takes it to 2. So the inset cannot ship without the
size rule.

## 2. What already exists

### 2.1 Already built, and reusable

- **Cell padding, end to end.** `SelectionBaseVSAssemblyInfo` carries
  `CompositeValue<Insets> cellPadding` (`:1068`) with getter (`:157`), setter (`:161`), XML write
  (`:570`), parse (`:604`) and `copyInfo` clone (`:703`). It reaches the browser through
  `SelectionListModel:77` into `VSFormatModel.padding`, is bound at
  `selection-list-cell.component.html:54-55` (top/bottom) and `:90-91` (left/right), and is read by
  `HTMLSelectionListHelper:181`, `HTMLSelectionTreeHelper:143`, `PDFSelectionListHelper:219`,
  `SVGSelectionListHelper:202` and `VSSelectionTreeHelper:233`.
  **It is populated only from `format.css`** (`:1029-1030`, `CompositeValue.Type.CSS`). There is no
  density tier and no author path.
- **`userPadding` and `resetPadding(VizContext)`** were hoisted to `VSAssemblyInfo` in slice B
  (`:1412`, `:1456`), so the selection family inherits both.
- **`PaddingPaneModel`** is a reusable four-edge model with a `followsDefault` flag; tables carry
  two instances (`TableViewPropertyDialogService:121-136`).
- **`SizePositionPaneModel.cellHeightFollowsDensity`** (`:85-89`, `:134`) — added by L′, and the
  precedent for this spec's checkbox semantics.

### 2.2 Not built

- **The card inset.** No `defaultPadding(VizContext)` override, no field on `VSSelectionBaseModel`
  (its `TOP_PADDING`/`LEFT_PADDING` at `:263-264` are unrelated max-mode constants), and no
  selection export helper reads `info.getPadding()`.
- **The cell padding's author predicate.** `isUserCellPadding()` exists only on
  `TableDataVSAssemblyInfo` (`:940`, `return cellPadding.hasUserValue()`). The selection base has
  the `CompositeValue` but not the predicate, so it needs adding alongside the reset method the
  follow-the-default checkbox calls.

### 2.3 Three corrections to the 2026-09-29 review's Part 2

Recorded because each one made this work look larger or riskier than it is.

- **`bypassesBaseChrome()` does not classify anything as "not a card."** It has one caller
  (`VSAssemblyInfo:1271`), and its own javadoc states the invariant it protects: "does this type
  write, after super, any value the hook also writes?" Membership is by implementation route — seven
  types override `setDefaultFormat` without super, Calendar never calls it, Tab calls super then
  overwrites. The form-input types on that list **do** get modern card chrome; they seed it
  themselves (`CheckBoxVSAssemblyInfo:433`) under a separate tracked project. The review's "the
  codebase has already classified every form and input component as not-a-card" does not follow.
- **Cell padding is not without a server source.** See 2.1.
- **The hardcoded `padding: 2px` at `selection-list-cell.component.scss:149` and
  `vs-selection.component.scss:197` is not the cell's padding.** Both sit on a gray badge
  (`background: gray; color: white; min-width: 35px`) — the measure/count pill.

The review also named "cell height has no additive path" as a blocker. That is true of
`getEffectiveCellHeight()`, which returns the rendered tier directly, but it does not block
anything: see D3.

## 3. Decisions

### D1 — Shared matrices, no new numbers

Card inset takes **16/12/8**, the matrix chart and table already use
(`VSDensityDefaults.chartPaddingForMode`). Cell padding takes **6·8 / 4·6 / 3·4**
(`cellPaddingForMode`). The density design's own D2 — one card inset concept, one set of numbers —
is preserved. (That document's D2 and this one's are unrelated; cross-references to it are named as
"the density design's" throughout.) `SelectionBaseVSAssemblyInfo` becomes the third implementor of
`defaultPadding(VizContext)`, after `ChartVSAssemblyInfo` and `TableDataVSAssemblyInfo`.

### D2 — The default size follows density, and preserves five rows at every tier

```
height = inset.top + titleHeight + 5 × cellHeight + inset.bottom
width  = inset.left + 100 + inset.right
```

| | comfortable | compact | dense | legacy |
|---|---|---|---|---|
| height | 202 | 170 | 136 | 120 |
| width | 132 | 124 | 116 | 100 |
| rows visible | 5 | 5 | 5 | 5 |

The unifying rule across both axes: **the box is the density content plus the inset.** Content
follows density where density has an opinion (title lane, cell height) and stays legacy where it
does not (the 100px content width).

Five rows rather than four because five restores legacy parity, and because a tier-varying row count
is worse than the problem being fixed — growing by the inset alone yields 3/3/5 across the tiers,
since dense's 20px cell equals the legacy row.

~~The selection container keeps its own `3 × defw × 12 × defh` basis grown by its inset.~~
**Withdrawn 2026-10-02.** The container takes no inset, so growing its box by one is incoherent —
that pairing was itself a defect found and fixed mid-implementation. The container keeps its
pre-existing `3 × defw × 12 × defh` default, unchanged by this work. See §11.

### D3 — Cell padding is non-additive, by construction

The selection cell is fixed-height (`selection-list-cell.component.html:20`,
`[style.height]="height"`) and the padded `.label-container` is a `table-cell` inside a
`height: 100%` container (`selection-list-cell.component.scss:23-32`). So a cell padding insets the
text **within** the cell rather than growing it.

This is the whole reason the table's hardest machinery is not needed here: no stored-versus-rendered
split, no shrink-and-add-back, no `getRowPadding` floor. The text room works out to the table's own
numbers:

| tier | cell | padding y | text room |
|---|---|---|---|
| comfortable | 28 | 12 | 16 |
| compact | 24 | 8 | 16 |
| dense | 20 | 6 | 14 |

### D4 — Both values seed in `seedChromeDefaults`, including the size

`setDefaultFormat` ends by calling `seedChromeDefaults` (`SelectionBaseVSAssemblyInfo:930`), and
`initDefaultFormat()` is invoked by the creating service after construction, so the mark is already
set. The same hook is re-run by `VizModernizeUtil.seedAll:115-125`. One hook therefore covers both
creation and Modernize, and no new constructor hook is required.

**Modernize resizes too.** A Modernized list matches a freshly created one. This is consistent with
L′, which already moves a Modernized list's title lane from 20 to 30 — Modernize has never left
selection geometry alone, and that is how the 5-to-3 loss arrived. The alternative, insetting
without resizing, would make Modernize produce a 2-row list: worse than today and worse than
creating one fresh.

### D5 — The size rule rewrites only sizes the seed itself produced

Mirroring `CheckBoxVSAssemblyInfo:479-489`, the rule fires only when the current size is one it
could have written:

```
100×120 (legacy) · 132×202 (comfortable) · 124×170 (compact) · 116×136 (dense)
```

Anything else is an author size and is left alone. An author-set cell height takes the assembly out
of the set naturally, since the size will not match. Revert restores 100×120, mirroring
`CheckBoxVSAssemblyInfo:495-503`. The rule re-fires on a density change, or a seeded size is
stranded at the old tier.

**An author padding takes the assembly out of the set, and the size is deliberately not
re-derived.** A comfortable list whose author zeroes the inset keeps its 202px and gains room for a
sixth row. The size rule exists to make the default sensible, not to track every later edit.

**Amended 2026-10-06.** Recognizing a size by its value alone cannot tell an author who chose
exactly 100×120 (or a tier size) from the rule, and since the rule re-runs on every open, that author
loses the size every time. [The container density design](./2026-10-05-selection-container-density-design.md)
D7 adds a `userSize` flag, set by the author's resize paths, which this rule now also requires to be
clear, and a Follow default density checkbox for size in Size & Position.

### D6 — Existing assets are not migrated

~~Nothing rewrites a stored size, inset or cell padding on load. The parse funnel uses the no-arg
constructor and never reaches `setVizMark`, which is the guarantee the mark mechanism already
relies on. An existing dashboard changes only when its author Modernizes it (D4).~~

**Reversed 2026-10-02.** The parse funnel is as described, but every open then restores the
`INITIAL_STATE` bookmark:

```
RuntimeViewsheet.gotoDefaultBookmark → Viewsheet.parseState → AbstractVSAssembly.parseState
   → VizModernizeUtil.reseedAfterRestore → seedChromeDefaults
```

That last hook is the one that now writes the cell padding, the card inset and the size. So a
selection marked before this work takes all three, in memory, the first time it is opened, and
keeps them if the dashboard is then saved. The export checks found it (their defect A).

**Accepted, not fixed.** The feature is not yet live, so the content marked before it is a small
window, and splitting the restore path from the creation hook costs more than it saves. An
existing *unmarked* dashboard is still untouched, and MC-1 proves that. See §9.

## 4. Seeding and resolution

Both values seed in `SelectionBaseVSAssemblyInfo.seedChromeDefaults`, under the same guard tables
use, so a `format.css` padding on the assembly class still wins wholesale:

```
if(!isUserPadding() && !isCssPaddingDefined())  → setPadding(VSDensityDefaults.tablePadding(ctx))
if(!isUserCellPadding())                        → setCellPadding(cellPadding(ctx), Type.DEFAULT)
```

`isUserPadding()` is inherited from `VSAssemblyInfo`. `isUserCellPadding()` is not inherited — it
is added here, mirroring `TableDataVSAssemblyInfo:940` (`return cellPadding.hasUserValue()`), as
§2.2 notes.

**Precedence is total, and that is what keeps this clear of the table's CSS defect.**
`CompositeValue.get()` is `userDefined ? userValue : (cssDefined ? cssValue : defaultValue)` — USER
beats CSS beats DEFAULT, wholesale, exactly one wins. The table bug fixed in the 2026-10-01
`getRowPadding` correction arose because a table's CSS padding arrives by a different route —
`CSSTableStyle` in the lens chain, per cell, where partial coverage is possible — against a stored
row height that had already given up its seed. Neither condition exists here: the padding is
all-or-nothing, and D3 means no stored height is shrunk, so nothing is owed back.

## 5. Browser geometry

`VSSelectionBaseModel` gains the inset, so all three types inherit it.

**Prerequisite.** This is the fifth assembly to carry a card inset, which is the trigger named in
the 2026-09-29 review's finding 3. The model-shape convergence lands first, as its own commit:
`VSChartModel`, `VSGaugeModel` and `VSTextModel` move from four flat ints to the object shape the
table already ships, `VSObjectContainer.getLaneInset` collapses to `object.padding ?? ZERO`, and
selections then declare the same field rather than a sixth variant. Note that
`web/tsconfig.json` has `strictTemplates: false`, so the eleven template bindings in
`vs-chart.component.html`, `vs-gauge.component.html` and `vs-text.component.html` are **not**
compiler-checked — they need a manual pass.

**Two choke points carry the whole geometry.** There is no column grid, which is why this is a
fraction of the table work:

- `vs-selection.component.ts:getBodyHeight()` (`:946-957`) subtracts `inset.top + inset.bottom` on
  the normal branch, alongside the existing title and search offsets.
- `getBodyWidth()` (`:959`) subtracts `inset.left + inset.right`.

The card — border, background, round corner — stays at the assembly edge, and the inset is drawn
inside it: the same split the chart and table card use. ~~The title lane sits inside the inset,
consistent with chart and table, which is what D2's arithmetic assumes.~~

**Corrected 2026-10-02.** The title lane stays flush with the card edge, at full width. Only the
body is inset, on all three surfaces.
- **Why:** a selection is too narrow for an inset lane. At 132px wide, 32px of side inset crowds a
  long title.
- **D2 is unaffected.** It sums inset, lane and rows, so it holds wherever the lane sits.
- **One thing does differ from chart and table:** the kebab is placed against the lane, so
  `VSObjectContainer.getLaneInset` returns zero for a list or a tree.

**Three branches take their own answer:**

1. ~~**`inContainer`** — a list inside a selection container must not double-inset.~~
   **Reversed 2026-10-02.** This rested on "the container insets its children", which the scope-out
   falsified. A contained list now insets itself exactly as a standalone one does, on all three
   surfaces, and the `inContainer` exemption was removed rather than mirrored into export. One rule,
   no special case. See §11.
2. **`dropdown && !maxMode`** — this branch computes `cellHeight * listHeight` and never reads
   `objectFormat.height`, so the inset is not implicitly present. **The dropdown panel does not take
   the inset.** It is transient chrome rather than the card, and insetting it costs rows where rows
   are scarcest.
3. **`maxMode`** — the card inset applies on top of the existing `TOP_PADDING`/`LEFT_PADDING`
   max-mode offsets, not instead of them.

**Cell padding needs no browser geometry at all.** The bindings already consume
`cellFormat.padding` and the cell is fixed-height (D3), so seeding the DEFAULT tier is the entire
browser change for that half.

## 6. Dialogs

Two `PaddingPaneModel` instances — card inset and cell padding — added to the three selection
property dialogs, with read and write in `SelectionListPropertyDialogService`,
`SelectionTreePropertyDialogService` and `SelectionContainerPropertyDialogService`.

**Placement follows tables.** Both panes go on `SelectionGeneralPaneModel`, shared by the list and
tree dialogs, exactly as tables use `TableViewGeneralPaneModel`.

**Corrected 2026-10-02.** This section also required a pane on
`SelectionContainerGeneralPaneModel`. The container has no inset, so it has no padding control:
leaving one would let an author store a value that only print honoured. See §11.

**Corrected 2026-10-01, before planning.** This section first said the panes would go on
`SizePositionPaneModel` instead, to sit beside `cellHeight` and its `cellHeightFollowsDensity`
checkbox, and accepted a divergence from tables as the cost. That was wrong on its premise:
`SizePositionPaneModel` is shared by **21** general pane models — every assembly type — so padding
controls placed there would appear on calendars, images, shapes and tabs. `cellHeightFollowsDensity`
lives there only because the pane renders it conditionally. The per-type pane is both the correct
home and the one that matches tables, so the divergence this section argued for does not arise.

**Checkbox semantics follow `cellHeightFollowsDensity` exactly:** a missing flag means no opinion.
That is what makes stale clients and unmarked content behave, and it is the rule L′ established when
it deleted the old value-comparison inference.

## 7. Export and print layout

The helper hierarchy collapses the work into two shared bases, rather than one edit per format
helper:

```
VSSelectionListHelper (extends ExporterHelper)
  ├── PDF / PPT / HTML / SVG selection-list helpers
  └── VSSelectionTreeHelper
        └── PDF / PPT / HTML / SVG / Excel selection-tree helpers

VSCurrentSelectionHelper (separate abstract base) → PDF / SVG / PPT / Excel

ExcelSelectionListHelper (extends ExporterHelper directly, bypassing the base)
```

Geometry goes into `VSSelectionListHelper` and `VSCurrentSelectionHelper`. Print layout is
`VsToReportConverter:682-703`, which converts selections into `TextBoxElement`.

**The opt-out is required, not optional.** Note the asymmetry: `ExcelSelectionListHelper` bypasses
the shared base, but `ExcelSelectionTreeHelper` inherits through it, so Excel's tree would pick up
the inset by inheritance unless something gates it. Reuse the exporter-level predicate
`AbstractVSExporter.insetsTableCard()` (`:1934`), which `ExcelVSExporter:55` and `CSVVSExporter:64`
already override to false; both are cell-grid formats where a pixel inset is meaningless. Its name
is now narrower than what it gates, but renaming touches shipped table code and is not worth it
during a release gate.

## 8. Verification

**Automated.**

- Seeding at all three tiers, for inset and cell padding, on all three types; Revert clearing both.
- The size rule rewriting only the four recognized sizes; an author size left alone; an author cell
  height taking the assembly out of the set; Revert restoring 100×120; the rule re-firing on a
  density change rather than stranding a seeded size at the old tier.
- `getBodyHeight()` and `getBodyWidth()` subtracting the inset **and offsetting the body by it** —
  a shrink without a shift leaves the rows flush to the border, which is the defect §1 names;
  the dropdown branch unchanged.
- Dialog read and write round trip, including `followsDefault` semantics and a missing flag.
- Precedence: USER beats CSS beats DEFAULT, resolving wholesale.

**Manual.**

- A `format.css` declaring a selection padding, confirming wholesale resolution on screen and in
  export.
- The export surfaces, against the fixture in §8.1.

### 8.1 The fixture — deliberately lighter than the table's

There is no selection equivalent of `dpx-fixture.zip`, so one is built. **It is scoped smaller than
the table fixture on purpose**, because this design's risk surface is smaller: the geometry lives in
two shared helper bases rather than a grid, and D3 removes the row-height arithmetic that most of
the table captures existed to police.

**Four roles, against the table fixture's nine:**

| Role | Covers |
|---|---|
| `SelList` | the plain case, and the five-row size rule |
| `SelTree` | node indent against the horizontal inset |
| `SelContainer` | a container holding a list and a tree — **now checks the container is _unchanged_** while its children inset normally (§11) |
| `SelNoTitle` | hidden title, lane 0, with the inset still drawn |

Plus one unmarked viewsheet holding the same four as the legacy control.

**Three formats, against the table's five:**

| Format | Why |
|---|---|
| HTML | the measurable one — `html_measure.js` gives per-element rects, so row heights and insets fall out without a ruler |
| PDF | a second rendering pipeline, which is what caught what HTML could not in C1 and C2 |
| Excel | **only to prove the opt-out** — §7's `insetsTableCard()` gate means Excel must show *no* inset, and `ExcelSelectionTreeHelper` inheriting through the shared base is the specific risk |

PPT and SVG/PNG are skipped deliberately: both extend `VSSelectionListHelper`, the same base PDF
extends, so PDF exercises the shared geometry they would. If the inset ever moves out of that base
into the leaves, this reasoning expires and they come back.

**Match Layout only.** The table fixture captured Match × Expand because an expanded table grows
unboundedly; a selection has a fixed row set, so Expand adds little here.

**Tooling is reused, not rebuilt.** `sree.py` (REST client, density flip, export pull),
`html_measure.js` (the measurement instrument), `vsclient.js` / `flows.js` (STOMP composer driver
for Save As, Modernize/Revert and per-dashboard density) and `inset_audit.py` all exist in
`.superpowers/baselines/density-padding-export/` and were assessed as reusable when that folder was
written.

**The fixture is local state, not a shared reference.** `community/.superpowers/` is excluded from
git (`.git/modules/community/info/exclude`), so nothing built there is reviewable or pinned to a
commit. It must not be cited as a baseline in a pull request the way a committed test can be — the
numbers it produces go in the PR description, the files stay on one machine.

## 9. Risks and accepted costs

- **Modernize reflows a dashboard.** D4 resizes on Modernize, so an author who Modernizes an
  existing dashboard sees selection assemblies change size. Accepted: it is an explicit author
  action, Modernize already moves selection geometry via L′, and the alternative produces a 2-row
  list.
- **A comfortable selection list is 202px tall by default, against 120 today.** Accepted as the
  cost of five-row parity; D2 records the rejected alternatives.
- **A selection marked before this work reflows on its first open.** D6 is reversed. A marked
  100 × 120 list or tree opens at the tier default (132 × 202 at comfortable) with the inset and
  cell padding, with no author action, and can overlap its neighbours. Accepted 2026-10-02: the
  feature is not yet live.
- ~~**The dialog placement differs from tables.**~~ Withdrawn 2026-10-01: the placement that would
  have diverged rested on a false premise about `SizePositionPaneModel`'s reach. See §6.
- **The model-shape convergence is a prerequisite with eleven unchecked template bindings.** Its
  risk is real but bounded and loud at runtime — a missing binding renders padding 0, visibly.

## 10. Open items

None. The one item this spec carried — whether to build a selection export fixture or accept a
narrower visual check — was **decided on 2026-10-01: build one, scoped lighter than the table's.**
The scope is §8.1, and the reasoning for each thing it leaves out is recorded there rather than
here, so a later reader finds it beside the checks it governs.


## 11. The container scope-out — decided 2026-10-02

The selection container was in scope when this spec was written and is not in the shipped work. The
decision was the user's, taken after the whole-branch review established the state below.

**What was found.** Export honoured the container's seeded inset — `VSCurrentSelectionHelper` placed
its rows through `getContentBounds` — while nothing in the browser read it: no container component
touches `model.padding`, and container children are positioned by the viewsheet layout engine. So a
modernized container rendered a box 32px larger with its rows flush as before, and exported the same
box with its rows indented 16px. That is the live/export mismatch §1 says this work exists to remove,
which made shipping it the one combination the design ruled out.

**What was removed.** The container's `defaultPadding` override and padding seed; its size rule;
`VSDensityDefaults.containerSize` / `isSeededContainerSize` / `containerSizeForMode`;
`VSCurrentSelectionHelper`'s `getContentBounds` usage; its `PaddingPaneModel`, dialog read/write
blocks and `<padding-pane>`; and the `applyCardInset` wrapper in `addCurrentSelection`. The container
is byte-identical to pre-branch on seed, model, browser, export, print and dialog, and that is
pinned by tests rather than left true by omission.

**The size rule went with the inset.** Keeping it would have re-created the exact defect the
implementation had already found and fixed once: a box grown by an inset that does not exist.

**A contained child now insets itself.** The `inContainer` exemption existed because the container
was supposed to inset its children. With that false, the consistent rule is that a selection list
looks the same in or out of a container, so the exemption was removed rather than mirrored into
export. A contained child insets on browser, export and print alike.

**The out-selection row height, decided 2026-10-02.** A collapsed child is shown as a one-line
summary row, and that row's height used to come from two unrelated places: the browser's model held
a constant 18 that nothing could set, and export drew `AssetUtil.defh` (20). A modern container's
rows now take the title-lane matrix, 30 / 26 / 20 by tier, from one method,
`CurrentSelectionVSAssemblyInfo.getOutSelectionRowHeight`, which both surfaces call. The title
matrix rather than the cell matrix, because a collapsed row is a child's title lane in summary form
and collapsing a filter must not change how tall its header is; the container's own lane and an
expanded child's title are already density-aware, and the collapsed row was the only part of that
stack still frozen at legacy. The method resolves through `VSDensityDefaults.titleHeight(ctx)`, so no
new matrix or literal was introduced.

**An unmarked container is unchanged on both surfaces.** The method takes the caller's legacy height
as a parameter: the browser passes 18 and export passes `AssetUtil.defh`. Marked containers converge
on the tier value; unmarked ones keep their own numbers, so D6 holds with no exception. The 2px
browser-versus-export disagreement on legacy rows is knowingly left in place. It predates this
branch, and unifying it would move every existing container, which is the one thing D6 rules out.
Whether the container's default size should grow to keep its row count at the larger tiers remains
a separate slice. **Taken up 2026-10-05** in
[the container density design](./2026-10-05-selection-container-density-design.md): twelve lanes at
the tier's lane height, 300×360 / 312 / 240.
