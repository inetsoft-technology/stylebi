# Selection family — title aligns with the rows

**Date:** 2026-10-22
**Types in scope:** selection list, selection tree.
**Not in scope:** the selection container (it takes no card inset, see the 2026-10-01 spec §11),
legacy (unmarked) assemblies, and the dropdown panel.
**Amends:** [2026-10-01-selection-family-padding-design.md](2026-10-01-selection-family-padding-design.md)
§5, the "Corrected 2026-10-02" ruling that the title lane stays flush with the card edge.

## 1. Why

Since #6157, a modern selection list insets its body by the card inset but leaves its title flush.
The title text and the first glyph of each row no longer share a left edge:

| | title text starts at | checkbox starts at | gap |
|---|---|---|---|
| dense (inset 8) | 2 | 14 | 12 |
| compact (inset 12) | 2 | 18 | 16 |
| comfortable (inset 16) | 2 | 22 | 20 |

Measured from the card's inner edge. The row offset is the body inset (`getContentLeft()`), plus
`.selection-list-cell-container`'s 2px margin, plus `LIST_INDENT` (4) applied as the icon's
`padding-left`. The title offset is `title-cell`'s 2px `padding-left`.

Reviewers read the gap as a bug. It is also inconsistent with the chart and the table, whose
titles sit inside the card inset (`VSObjectContainer.getLaneInset`), so a selection list beside a
table has its title further left than the table's.

## 2. The 2026-10-02 ruling, and why it does not hold for the left edge

The ruling gave one reason: "a selection is too narrow for an inset lane. At 132px wide, 32px of
side inset crowds a long title." That counts both sides. Alignment needs only the left. The right
edge of the lane already carries the kebab and the dropdown toggle, so a long title is truncated
against them whatever happens on the left. The extra cost is the left inset plus the list indent:
12–20px, not 32.

The ruling's other consequence stays as it is. The kebab is anchored on the right, and
`getLaneInset` keeps returning zero for a list or a tree.

## 3. Decisions

### D1 — The lane stays flush; only the title text moves

The lane's border, background and round corner stay at the card edge, at full width, as now. Only
the title text's left padding changes. This keeps the header band reading as one piece of chrome,
leaves the kebab and `getLaneInset` alone, and keeps D2 of the 2026-10-01 spec (the size rule)
unaffected, because the lane's height does not change.

### D2 — One inset drives both the title and the body

No new title-padding control. The title follows the existing card inset ("Padding" on the
Selection General pane), so changing that value moves the title and the rows together and they
stay aligned by construction. Three independent knobs would turn alignment into something every
author has to keep right by hand.

`titleFormat.padding`, which today only `format.css` can set, keeps its meaning: an author or CSS
value replaces the computed default, as it does on every other titled assembly.

### D3 — The target is the first row's leading glyph

The title text's left edge is placed where the first row's leading glyph starts:

- **List:** `inset.left + 2 + LIST_INDENT`, the checkbox or radio.
- **Tree:** `inset.left + 2`, the root expand toggle. A root node's toggle has
  `padding-left = nodeIndent - indent = 0`.

The value is computed client-side from the model's `padding` and the controller's indent. It is
not seeded, because it is derived from values that are already persisted.

### D4 — Modern only

The offset applies only when the model carries a card inset. Legacy assemblies (inset absent or 0)
keep today's 2px title and render pixel-identical. Without this gate a legacy list's title would
move 4px to meet its checkbox, and the decision not to migrate existing assets (D6 of the
2026-10-01 spec) would be broken by a side door.

### D5 — The inset stays author-editable, including 0

The card inset is not made a mandatory minimum. Flush lists have legitimate uses: tight filter
rails, children of a selection container, and styles where cell borders act as the card edge. An
author who sets the inset to 0 gets a flush body and, by D2 and D3, a title at `2 + LIST_INDENT`,
still aligned.

## 4. Where it lands

- **Browser.** `vs-selection.component` passes the computed left padding to `title-cell` (or
  binds it on the title's wrapper) when the model is marked and `titleFormat.padding` is unset. The
  `listSelectedString` second title (`.selection-title-cell`) does not move. It is a value
  readout on the right, not a label for the rows.
- **Export.** The title is drawn in the same helpers that took the body inset in #6157:
  `VSSelectionListHelper`, `VSSelectionTreeHelper` and their PDF/SVG/PPT/HTML subclasses. Apply
  the same offset there, gated on `AbstractVSExporter.insetsTableCard()` exactly like the body
  inset, so Excel and CSV are unchanged.
- **Print layout.** `VsToReportConverter` converts selections into `TextBoxElement`. Check
  whether its title text box takes the inset. If it does not, apply the same offset.

## 5. Verification

- List and tree at all three densities: the title text's left edge equals the leading glyph's left
  edge, measured in the browser and in PDF/SVG export.
- Author inset 0: still aligned.
- `format.css` title padding set: the CSS value wins.
- Legacy list and tree: pixel-identical to before.
- A long title at the 132px minimum width: it truncates at the kebab, and nothing overlaps.
- Kebab and dropdown toggle positions: unchanged.

## 6. Open items

- **Selection container children.** The container takes no inset, but its children do (the
  2026-10-01 spec §11). Confirm that a child's title lining up with its own rows still looks right
  under the container's own title, which stays flush.
- **Current-selection readout.** `VSCurrentSelectionHelper` titles are out of scope unless the
  container work reopens.
