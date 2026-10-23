# Selection family — title aligns with the rows

**Date:** 2026-10-22 (amended 2026-10-23, before implementation, with the rulings of Bug #79230)
**Types in scope:** selection list, selection tree.
**Not in scope:** the selection container (it takes no card inset, see the 2026-10-01 spec §11),
legacy (unmarked) assemblies, and the dropdown panel.
**Amended 2026-10-23.** Also out of scope: a list or tree inside a selection container. Its title
keeps today's 2px (D7). The dropdown panel stays out, but a dropdown list's title is in, and it
needs no special case (D3).
**Amends:** [2026-10-01-selection-family-padding-design.md](2026-10-01-selection-family-padding-design.md)
§5, the "Corrected 2026-10-02" ruling that the title lane stays flush with the card edge.

Line references in the amended paragraphs are against `epic-74519` at `3f94a2968c`.

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
side inset crowds a long title." That counts both sides. Alignment needs only the left. ~~The right
edge of the lane already carries the kebab and the dropdown toggle, so a long title is truncated
against them whatever happens on the left.~~ The extra cost is the left inset plus the list indent:
12–20px, not 32.

**Amended 2026-10-23.** The kebab and the dropdown toggle sit on top of the title rather than
truncating it. The label is a `display: table` that grows to its text, so a long title runs past the
lane and is clipped at the lane's edge, with no ellipsis (`title-cell.component.scss:27-33`). The
anchored kebab strip and `.selection-list__header-buttons` are drawn over it. The conclusion stands:
moving the text in costs only the left offset, which hides that much more of a long title.

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
- ~~**Tree:** `inset.left + 2`, the root expand toggle. A root node's toggle has
  `padding-left = nodeIndent - indent = 0`.~~

**Amended 2026-10-23.**
- **Tree:** `inset.left + 2` applies only to a tree of more than one level, whose root rows lead
  with the expand toggle (`padding-left = nodeIndent - indent = 0`). The cell indent follows the
  level count, not the object type: `indent = levels > 1 ? TREE_INDENT : LIST_INDENT`
  (`vs-selection.component.ts:409`). A one-level tree has no toggles, and its checkbox sits where a
  list's does, so it takes the list target.
- **Dropdown:** the browser reads the body's own origin, `getContentLeft()`. The open dropdown
  panel keeps the horizontal inset (`getBodyInset`, `vs-selection.component.ts:957-966`), so a
  dropdown title lines up with its panel with no special case.

The value is computed client-side from the model's `padding` and the controller's indent. It is
not seeded, because it is derived from values that are already persisted.

### D4 — Modern only

~~The offset applies only when the model carries a card inset. Legacy assemblies (inset absent or 0)
keep today's 2px title and render pixel-identical.~~ Without this gate a legacy list's title would
move 4px to meet its checkbox, and the decision not to migrate existing assets (D6 of the
2026-10-01 spec) would be broken by a side door.

**Amended 2026-10-23.** The gate is the assembly's mark: `model.vizModern` in the browser and
`getVizMark() != null` (`VizContext.of(info).modern`) in export and print. Whether a padding is
present cannot tell a legacy list apart:

- an unmarked list seeded since #6157 carries `Insets(0,0,0,0)` (`VSDensityDefaults.tablePadding`,
  `:543-545`), so "inset absent" misses it;
- an unmarked list whose `format.css` class sets a padding carries a real inset
  (`VSAssemblyInfo.setCSSDefaults`, `:1728-1729`), installed whether or not the list is marked.

Gating on "absent or 0" would also switch the offset off for D5's modern list at an author inset of
0. Under the mark gate, every legacy list keeps its 2px title whatever padding it carries, and D5
holds.

### D5 — The inset stays author-editable, including 0

The card inset is not made a mandatory minimum. Flush lists have legitimate uses: tight filter
rails, children of a selection container, and styles where cell borders act as the card edge. An
author who sets the inset to 0 gets a flush body and, by D2 and D3, a title at `2 + LIST_INDENT`,
still aligned.

### D6 — Only left-aligned titles move

*Added 2026-10-23.* The offset applies only when the title is left-aligned, which is the default.
Centered and right-aligned titles render as today.

- **Why:** the body inset is the same on both sides, so a centered title is already centered over
  the rows. A right-aligned title ends under the kebab, and its right edge has nothing to align with.
- **Not taken:** applying it to every alignment pushes a centered title off-center by half the
  offset. Padding both sides changes nothing a centered title needs.

### D7 — A selection container's children keep a 2px title

*Added 2026-10-23, closing the first open item of §6.* A list or tree inside a selection container
keeps today's title, in the browser and in export. Print already converts no container child.

- **Why:** inside a container, the container's own title and the rows summarizing selections made
  outside it (`current-selection.component.html:33`) both start at 2px. A collapsed child shows
  only its title, with nothing under it to align with. Keeping child titles at 2px gives the
  container one edge for its labels, and an expanded child's rows read as indented under their
  header.
- **Not taken:** moving child titles too would be one rule with no special case. But child titles
  would then sit 14–22px in from their siblings, the container title and the outside-selection rows.
- **Cost:** an expanded child keeps the gap §1 describes. #6157 removed the container exception for
  the body; this brings one back, for the title only.

### D8 — Export uses one target for lists and trees: `inset.left + 2 + LIST_INDENT`

*Added 2026-10-23.* Every export format that draws the card inset (PDF, SVG/PNG, PowerPoint, HTML)
pads the title by `inset.left + 6`, for lists and trees alike. Excel and CSV are unchanged through
`insetsTableCard()`.

- **Why:** no export format draws the browser's leading glyphs the way the browser does:
  - PDF, SVG and PowerPoint draw rows as text boxes only, with the label at
    `inset.left + cellPadding.left` (`PDFSelectionListHelper.java:217-219`). A tree indents with two
    spaces per level (`VSSelectionTreeHelper.java:177`).
  - HTML draws `<input>` checkboxes but no toggle (`HTMLSelectionListHelper.java:207-218`,
    `HTMLSelectionTreeHelper.java:157`).

  So the browser's tree target, at the toggle, has nothing to align with in export. One constant
  lands close to the first row, and only the inset varies, so D2 holds. Measured on the fixture
  (title / first row, px from the card edge, dense / compact / comfortable):
  - HTML: 14 / 18 / 22 on the list checkbox exactly; a tree's checkbox sits 2px left of it.
  - PDF and PNG: title 14 / 18 / 22 against row text at 12.9 / 18.8 / 24.8.
  - PowerPoint: title 14.7 / 18.7 / 22.7 against row text at 13.3 / 20.0 / 26.7.

  The row side carries offsets of its own: the PDF text box adds about 0.8px, and PowerPoint passes
  the cell padding through unscaled, so 8px lands as 8pt. Those predate this note and are left alone.
- **Not taken:** copying the browser exactly, with trees at `inset.left + 2`, puts a PDF tree's
  title up to 6px left of its row text. Aligning with each format's own rows, at
  `inset + cellPadding.left`, makes the exported title follow the cell padding while the browser's
  does not, which breaks D2.
- **Cost:** a multi-level tree's exported title sits 4px right of the browser's.

### D9 — Print layout pads the title box by `inset.left`

*Added 2026-10-23, answering §4's print item.* A marked list's or tree's printed title text box takes
`inset.left` as its left padding, and its other edges keep the report default.

- **Why:** `VsToReportConverter.createTitle` (`:1446`) insets no titled assembly, not even a chart
  or a table. The selection's printed value box has taken the card inset since #6157
  (`applyCardInset`, `:1890-1896`), so print shows the same gap as the browser. Print has no rows
  or checkboxes, only a text box of selected values that starts at `inset.left`, so that is the
  target.
- **Not taken:** leaving print alone keeps the gap on one more surface. The change stays inside the
  two selection callers; `createTitle`'s other callers are untouched.

## 4. Where it lands

- **Browser.** `vs-selection.component` passes the computed left padding to `title-cell` ~~(or
  binds it on the title's wrapper)~~ when the model is marked and `titleFormat.padding` is unset. The
  `listSelectedString` second title (`.selection-title-cell`) does not move. It is a value
  readout on the right, not a label for the rows.

  **Amended 2026-10-23.** Padding the wrapper is not an option: `title-cell` paints the title
  background on `.selection-list-title` (`title-cell.component.html:20`), so it would pull the
  background in with the text and break D1. The offset is a `title-cell` input, used as the
  label's left padding unless `titleFormat.padding` is set. The composer's inline edit input uses
  it too, so the text does not jump on double-click. A legacy title, with no input value, leaves
  both untouched.
- **Export.** The title is drawn in the same helpers that took the body inset in #6157:
  `VSSelectionListHelper`, `VSSelectionTreeHelper` and their PDF/SVG/PPT/HTML subclasses. Apply
  the same offset there, gated on `AbstractVSExporter.insetsTableCard()` exactly like the body
  inset, so Excel and CSV are unchanged.

  **Amended 2026-10-23.** The exporter answers the indent next to `getSelectionCardInset`, and the
  six title call sites read it:
  - PDF, SVG and PowerPoint `writeTitle` for a list;
  - `VSSelectionTreeHelper.writeTitle` for a PDF, SVG or PowerPoint tree;
  - the HTML list and tree, through `HTMLCoordinateHelper.appendContainerTitle`.

  PowerPoint scales the indent by its points-per-pixel factor, as it scales the body inset. A
  `format.css` title padding is passed through unscaled, as today. A container child's
  `writeTitleInContainer` is untouched (D7).
- **Print layout.** `VsToReportConverter` converts selections into `TextBoxElement`. ~~Check
  whether its title text box takes the inset. If it does not, apply the same offset.~~

  **Amended 2026-10-23.** It does not, for any titled assembly. The selection callers pass the
  inset to `createTitle` (D9).

## 5. Verification

- ~~List and tree at all three densities: the title text's left edge equals the leading glyph's left
  edge, measured in the browser and in PDF/SVG export.~~
- Author inset 0: still aligned.
- `format.css` title padding set: the CSS value wins.
- Legacy list and tree: pixel-identical to before.
- ~~A long title at the 132px minimum width: it truncates at the kebab, and nothing overlaps.~~
- Kebab and dropdown toggle positions: unchanged.

**Amended 2026-10-23.**
- List, one-level tree, multi-level tree and dropdown list at all three densities: in the browser,
  the title text's left edge equals the leading glyph's left edge. In export the title starts at
  `inset.left + 6`, which lands on the HTML list checkbox, and within 3px of the first row's text in
  PDF and PNG, and 4px in PowerPoint (D8 has the measured numbers). In print it equals the value
  text's left edge.
- A long title at the 132px comfortable default: it starts at the offset and is clipped at the
  lane's edge, and the kebab and toggle positions are unchanged. The kebab already sat over the
  title before this change.
- A container child, expanded and collapsed: unchanged.
- A centered or right-aligned title: unchanged.
- The composer's inline title edit: the text starts where the label's did.

## 6. Open items

~~- **Selection container children.** The container takes no inset, but its children do (the
  2026-10-01 spec §11). Confirm that a child's title lining up with its own rows still looks right
  under the container's own title, which stays flush.~~
~~- **Current-selection readout.** `VSCurrentSelectionHelper` titles are out of scope unless the
  container work reopens.~~

**Amended 2026-10-23.** None. Container children keep their title (D7), and the current-selection
readout stays out of scope with the rest of the container.
