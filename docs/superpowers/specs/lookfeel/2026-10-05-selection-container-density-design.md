# Selection container — density sizing, the range slider lane, and a stored stack that matches the drawing

**Date:** 2026-10-05
**Branch:** community `feature-selection-container-density`, cut from `epic-74519` @ `d082410751`
**Verified against:** `d082410751`. Every file and line citation below was checked against that commit.
**Follows:** [the selection family padding design](./2026-10-01-selection-family-padding-design.md)
§11, which scoped the container out of the card inset and left one item open: "Whether the
container's default size should grow to keep its row count at the larger tiers remains a separate
slice." This is that slice, plus three defects found while scoping it.
**Types in scope:** the selection container (`CurrentSelectionVSAssembly`), the selection lists and
trees inside it, and the range slider (`TimeSliderVSAssembly`) as a contained child.
**Amended 2026-10-06:** D7 records an author's size so that neither size rule rewrites it, and
gives Size & Position a follow-the-default-density checkbox to undo that. It covers standalone
selection lists and trees as well, because the list size rule from the selection family design has
the same gap.

## 1. Why

Four problems, the first two visible in the browser:

1. **A contained list loses rows at the larger tiers.** Expanding a list inside a container sizes it
   `listHeight × 20 + titleHeight`, and the browser's body for it is `stored − title − inset`. At
   comfortable that is 150 − 30 − 32 = 88px, which fits **3 of 6 rows** at 28px. The #6157 export
   checks recorded the same 3 / 4 / 6 rows at comfortable / compact / dense (defect F). It is the
   loss D2 of the selection family design fixed for a standalone list, reappearing inside the
   container.
2. **The range slider's title lane stays at 20px** while the container's own lane, its collapsed
   rows and its list children all take 30 / 26 / 20.
3. **The stored stack does not match the drawing.** `CurrentSelectionVSAssembly.layout()` stores
   child positions as if the container's title and every collapsed row were 20px. The browser never
   reads them, but export does, and #6157 had to work around it three times (defects F, G, H). Two
   readers still use the stale value.
4. **A modern container's default size holds fewer lanes.** The legacy 300×240 holds its title plus
   11 collapsed lanes of 20px; at comfortable, with 30px lanes, it holds 7.

## 2. What exists today

### 2.1 How a container draws its children — the browser computes, it never reads stored positions

- Children are stacked in normal flow (`d-block`) inside a body div
  (`vs-selection-container-children.component.html`), whose top is the container's top plus its title
  lane (`getBodyTop()`, `vs-selection-container-children.component.ts:430-432`).
- A list child's element height is its title lane when it is a dropdown, otherwise its stored height
  (`vs-selection-container-children.component.html:85`). A range slider child's is the height its
  model reports: its lane when hidden, its stored height plus its lane otherwise
  (`VSRangeSliderModel:127`).
- Collapsed out-selection rows take `vsObject.dataRowHeight`, which is
  `CurrentSelectionVSAssemblyInfo.getOutSelectionRowHeight` (`:262`) — 30 / 26 / 20 when modern.
- A contained list's body is `objectFormat.height − title − search − inset`
  (`vs-selection.component.ts` `getBodyHeight()`, `:978`).
- Contained children drop their stored top: `vs-selection.component.ts` `topPosition` returns null for
  `inContainer` outside bottom tabs, and `vs-range-slider.component.html:18-20` sets no top inside a
  `VSSelectionContainer`.

### 2.2 Where a contained child's height is written

| Site | When | Today |
|---|---|---|
| `GroupingService:142-144` | a list is dropped into a container | switched to dropdown; `listHeight × defh + titleHeight` |
| `VSSelectionContainerService:214-216` | **a contained list is expanded** — every click, viewer and composer (`VSSelectionContainerController:64-66` is `@Undoable`; the client sends it from `selection-list-controller.ts:239/245`) | `listHeight × defh + titleHeight`; switched to LIST |
| `VSSelectionContainerService:220-221` | a contained list is collapsed | `titleHeight`; switched to DROPDOWN |
| `VSSelectionContainerService:229-237` | a contained range slider is expanded or collapsed | `listHeight × defh + titleHeight`, or `titleHeight`; `setHidden` |
| `VSSelectionContainerService:145-201` | before an expand: an overflow estimate that collapses expanded siblings | collapsed rows at `defh` (`:153`); the expanding list's body at `listHeight × defh` (`:178-184`) |
| `AbstractLayout:476-486` | a device layout is applied | every contained list `listHeight × defh`, without its title |

These are writes. `VSObjectService.getSize` returns the live `layoutSize` or `pixelSize` object, not a
copy (`Viewsheet.getPixelSize:4640-4644`), so `size.height = …` changes the stored size in place.
Because the expand path recomputes the height from `listHeight` on every click, whatever height was
stored at drop time lasts only until the first expand.

A bookmark captures the result: the container's state writes each child's whole XML
(`CurrentSelectionVSAssembly.writeStateContent:340-354`), size, show type and hidden flag included.

### 2.3 Who reads the stored stack

`layout()` (`CurrentSelectionVSAssembly:158-190`) writes child positions. It runs from
`Viewsheet.layout()` at export start (`AbstractVSExporter:303`) and during the session
(`CoreLifecycleService:815`, `:2957`).

| Reader | Uses |
|---|---|
| Export drawing — `CoordinateHelper.getContainerChildTop:516` | a **computed** top: title, collapsed rows at `getOutSelectionRowHeight`, and each earlier child's drawn height (`getAssemblySize:552-589`). It follows the browser's stack for every child type, except where §10 records a pre-existing difference (a hidden container title, a hidden slider title, legacy collapsed rows at 18 vs 20) |
| HTML — `HTMLCoordinateHelper.adjustChildAssemblyPosition` | rebuilds positions with `getOutSelectionRowHeight` |
| Match-layout clip — `AbstractVSExporter.prepareAssembly:1944` | the **computed** top for a marked selection list only (`:1919-1928`); the **stored** top for anything else |
| Export inclusion — `AbstractVSExporter.needExport:2685` | same helper, same split |
| `CSVUtil.needExport:222` | the **stored** top, but its five callers (`ExportDialogService:95`, `EmailDialogService:99`, `ScheduleDialogService:235`, `ScheduleTaskActionService:346`, `VSExportService:185`) pass only table-data assemblies, so it never sees a container child |
| Browser | none (2.1) |
| Print layout | none — `VsToReportConverter` returns early for a contained list (`:1844`) or tree (`:1891`) |
| Viewsheet bounds and grid | none — the loops at `Viewsheet:1683`, `:2696`, `:2751` skip contained children |

So for a modern container, a range slider child is clipped and included by a stored top that is 10px
too high per lane above it at comfortable (6px at compact, 0 at dense).

### 2.4 The range slider's title lane

- `TimeSliderVSAssemblyInfo.getTitleHeight()` returns the stored value (`:428-429`), and
  `VSRangeSliderModel` sends it (`:110`) and adds it to a contained slider's height (`:127`).
- The title is drawn only inside a selection container (`vs-range-slider.component.html:45`), so a
  standalone slider has no visible lane.
- `TitleLaneHeightRowTest:75` pins the exclusion. It was inherited from the external Widget Spec §05
  (`chart-card-anchored-strip-lane-decisions.md:290`) with no code reason; the other two excluded
  types, check box and radio button, have one (`CheckBoxVSAssemblyInfo:474-480`).
- Four sites decide whether a contained slider is collapsed by comparing its stored height with
  `getTitleHeight()`: `VSSelectionContainerService:164-166`, `:189`, `:229`, and `AbstractLayout:480`.
  These are the sites the change covers; §10 lists other readers left alone.
- The range slider is not in `ANCHORED_ASSEMBLY_TYPES` (`mini-toolbar.service.ts:42-63`), and a
  container's children never draw their own strip.

## 3. Decisions

### D1 — The container takes no card inset (declined 2026-10-05)

Every header-like part of a container already sits flush: its own title, its collapsed rows
(`current-selection.component.scss` carries only a 14px right padding), each child's title lane
(selection family design §5, corrected 2026-10-02) and the range slider's title. The only inset
surface inside a container is a list or tree body, and each child insets its own (§11). A container
inset would have nothing left to inset except the children, a second time — which is why `c90b6b5d16`
removed it. The §11 scope-out therefore stands as a decision, not an open item, and the container's
property dialog keeps no padding pane.

### D2 — A contained list is sized for its rows at its own density

```
height = titleHeight + listHeight × effectiveCellHeight + inset.top + inset.bottom
```

| listHeight 6 | comfortable | compact | dense | legacy |
|---|---|---|---|---|
| height | 30 + 168 + 32 = **230** | 26 + 144 + 24 = **194** | 20 + 120 + 16 = **156** | 20 + 120 = 140 |
| body | 168 | 144 | 120 | 120 |
| rows visible | 6 | 6 | 6 | 6 |

- **The child's own mark decides**, because the lane, the cell height and the inset being summed are
  the child's own. A modern list in a legacy container takes the rule; a legacy list in a modern
  container does not.
- **An unmarked list keeps today's formula word for word** at every site, so a legacy list dropped
  into or expanded in any container is byte-identical.
- **One helper.** The rows-plus-inset part already exists twice, in the list and tree dialogs'
  dropdown-to-list switch (`SelectionListPropertyDialogService:296-303`,
  `SelectionTreePropertyDialogService:332`). It moves to
  `SelectionBaseVSAssemblyInfo.getListBodyHeight()` —
  `listHeight × getEffectiveCellHeight() + inset.top + inset.bottom` — and callers add the title as
  they do now. Each site's unmarked branch stays where it is, because the legacy formulas differ
  (`defh` at one, the stored cell height at another).

### D3 — The fix is in the write paths, so existing contained lists correct themselves at their next expand

The expand path recomputes the height on every click (2.2), so fixing it reaches every contained list
in a modern container the next time it is expanded — new or old. No size-recognition rule is needed.

Fixing only lists dropped after the change was considered and is not implementable without new
persisted state: the expand path cannot tell a new child from an old one, and the drop-time height is
overwritten at the first expand anyway.

**Accepted:** a bookmark taken before the fix restores the old expanded height (2.2) until the list
is next collapsed and expanded; and a density change leaves an already-expanded list at the old tier's
height until its next expand, the same staleness every stored size has.

### D4 — The range slider joins the title-lane row

- `getTitleHeight()` resolves through `VSDensityDefaults.titleHeight(this, stored)` (`:250`), the
  resolver the other nine types use. Resolution is at read time: a marked slider whose author has not
  set a height takes 30 / 26 / 20; an author-set or unmarked slider keeps its stored height. Existing
  marked sliders take the lane on their next render, which is how every other type joined the row.
- **Collapsed-or-expanded uses `isHidden()` whenever it is set** at the four sites in 2.4. Without
  it, a marked slider collapsed at a stored 20px reads as expanded once its lane is 30, and clicking
  it to expand falls through both branches at `VSSelectionContainerService:229-237` and does nothing.
  Unmarked sliders keep the height comparison as a fallback, because a slider collapsed at a tier lane and then Reverted would otherwise be stuck. `GroupingService:150` already uses `isHidden()`.
- **The slider's body is unchanged.** `listHeight × defh` at `:189-191` and `:230` is the track's
  height, not rows, so only the title term moves.
- Check box and radio button stay excluded, for their documented reason.

### D5 — A modern container stores the stack it draws

- Two methods move to `CurrentSelectionVSAssembly`, **moved, not rewritten**:
  - `getDrawnHeight(VSAssembly child)` — a dropdown list counts as its title lane; a range slider whose
    title shows counts as its lane when hidden, its stored height plus its lane otherwise; anything
    else counts as its stored height. This is `CoordinateHelper.getAssemblySize:552-581`'s
    container-child cases.
  - `getChildTop(VSAssembly child, double containerTop)` — the container's top, plus
    `getTitleHeight()`, plus collapsed rows × `getOutSelectionRowHeight(AssetUtil.defh)`, plus the
    drawn heights of the children above it. This is `CoordinateHelper.getContainerChildTop:516-547`.
- `CoordinateHelper.getContainerChildTop` and those cases of `getAssemblySize` delegate to them, so
  export's numbers do not change.
- `layout()` stacks children by `getChildTop` when **the container** is marked. An unmarked container
  keeps the loop at `:158-190` exactly.
- **Readers are not edited.** For a modern container, `prepareAssembly` and `needExport`
  now read stored positions that match the drawing, which fixes the range
  slider's clip and inclusion. The exporter's list-only rule (`:1919-1928`) stays: it
  still covers a modern list inside a legacy container.

### D6 — The default size keeps twelve lanes

```
containerSize(ctx) = 300 × 12 · titleHeight(ctx)      →  300×360 / 300×312 / 300×240
legacy             = 300 × 240                        (CurrentSelectionVSAssemblyInfo:61)
```

- `VSDensityDefaults.containerSize(VizContext)` and `isSeededContainerSize(Dimension)`, recognizing
  300 × {240, 312, 360}. The pair mirrors `selectionSize` / `isSeededSelectionSize` (`:192`, `:208`).
- The rule runs in `CurrentSelectionVSAssemblyInfo.seedChromeDefaults` (`:76`) under
  `(ctx.modern || ctx.transition) && isSeededContainerSize(getPixelSize())` — the guard
  `SelectionBaseVSAssemblyInfo:991-992` uses, so an open never shrinks an unmarked container that
  happens to sit at a tier size.
- **Reach:** creation, Modernize, a density change, and every open of a marked container (the restore
  path re-seeds, per the selection family design's reversed D6). Revert reaches it through the
  transition and restores 300×240. At dense it is a no-op. An author size is left alone — by the
  recognizer when it is off the recognized set, and by D7's flag when it is on it.
- Width stays 300: density has no opinion on the container's width, and it has no inset (D1).
- The comment at `:110-112` ("no card inset and no size rule … keeps the legacy basis at every tier")
  is rewritten to keep its inset half and drop the size half.

### D7 — An author's size is recorded, and "follow the default density" gives it back (added 2026-10-06)

**Why.** D6's rule and the list size rule (selection family design D5) know a seeded size only by
its value, and both re-run on every open. So an author who deliberately sizes a marked container to
300×240 — the old default, and the natural value to pick, since changing only the height keeps the
width at 300 — has it rewritten to the tier size on the next open, and on every open after that.
The same holds for a list sized exactly 100×120, 132×202, 124×170 or 116×136. Every other density
value an author can set is already protected by a flag: table row heights by `userDataRowHeight` and
`userHeaderRowHeight` (read at `VSTableLens:1812-1818`), title height by `userTitleHeight`, cell
height by `userCellHeight`, the card inset by `userPadding`. The size rules had only the value test.

The size stays a seeded write rather than becoming a read-time substitution. Read-time resolution
would not remove the need for a flag — an author's 300×240 would still look exactly like the legacy
default — and every reader of `getPixelSize()` (layout, composer move and resize, overlap, device
layouts, every exporter, the browser model) would have to go through a resolver.

**The flag.** `VSAssemblyInfo` gains `userSize` beside `userPadding` (`:1414-1423`, `:1876`):
accessors, copied in `copyInfo` the way `userPadding` is (`:701-704`), parsed with a missing
attribute meaning false. Unlike `userPadding` it is written only when true, so an assembly that never
gets it — every type without a size rule, and every list or container nobody resized — keeps its
saved XML unchanged.

**Which types.** Mirroring `defaultPadding` / `resetPadding` (`:1444`, `:1458`):
- `public boolean takesDensitySize()` — false on `VSAssemblyInfo`; true on
  `SelectionBaseVSAssemblyInfo` (list and tree) and `CurrentSelectionVSAssemblyInfo`.
- `public void resetSize(VizContext ctx)` — clears `userSize` and writes the rule's size:
  `VSDensityDefaults.selectionSize(ctx)` for a list or tree, `containerSize(ctx)` for a container. It
  does nothing on a type that does not take a density size.
- `public boolean followsDensitySize()` (amended 2026-10-06, after the Task 10 review) — whether the
  size rule owns the box's size: false on `VSAssemblyInfo`; on the two types, the flag is clear **and**
  the size is one the rule recognizes. The rule guards and the dialog read (§6) both use it, so the
  checkbox can never claim a size the rule would not move.

**The rules honour it.** Both guards gain `!isUserSize()`, through `followsDensitySize()`:

```
SelectionBaseVSAssemblyInfo:     (ctx.modern || ctx.transition) && followsDensitySize()   // !isUserSize() && isSeededSelectionSize(getPixelSize())
CurrentSelectionVSAssemblyInfo:  (ctx.modern || ctx.transition) && followsDensitySize()   // !isUserSize() && isSeededContainerSize(getPixelSize())
```

A flagged box is therefore left alone by every open, by a density change, by Modernize and by
Revert. The checkbox below is the only way back to the density size.

**What sets the flag: the author's resize paths, and nothing else.**
- Composer drag-resize, `ComposerObjectService.resizeObject` (`:126`). Multi-select resize routes
  through it too (`ComposerObjectController:88-89`). It sets the flag on the resized assembly when
  `takesDensitySize()` — not on the container's children, whose widths it changes to follow the
  container.
  - *Amended 2026-10-06, after the final review.* The multi-select handler also serves the toolbar's
    Align, Distribute and Same Width/Height, which send every selected box with its current size. So
    the flag is set only when the size **changed**, the same test the dialogs' null branch uses. An
    Align or a resize back to the starting size leaves a following box following.
- Size & Position in the list, tree and container property dialogs (§6).

Derived writes never set it: the expand and collapse path (D2), the drop into a container, the
dialogs' show-type switch, convert-to-range-slider, device and print layout sizes (which live in
`VSAssemblyLayout`, not the pixel size), and the size rules themselves.

**Existing content.** Nothing saved before the flag carries it. An older box at a recognized size
still follows the rule on its first open (§9); any author resize after that records the flag.

**Bookmarks.** A container's bookmark writes each child's whole XML, flag included (2.2). A
standalone list's bookmark state carries no size. Nothing else changes.

## 4. Contained-list sizing in detail (D2, D3)

| Site | Marked child | Unmarked child |
|---|---|---|
| `VSSelectionContainerService:214-215` — expand | `titleHeight + getListBodyHeight()` | unchanged |
| `VSSelectionContainerService:153` — collapsed rows in the overflow estimate | `containerInfo.getOutSelectionRowHeight(AssetUtil.defh)` — already `defh` for an unmarked container, so no branch | — |
| `VSSelectionContainerService:178-184` — the expanding list's body in the estimate | `getListBodyHeight()` | unchanged |
| `GroupingService:144` — drop into a container | `titleHeight + getListBodyHeight()` | unchanged |
| `AbstractLayout:476-486` — device layout | `titleHeight + getListBodyHeight()` | unchanged (already a row short, because it leaves out the title; fixing it would move legacy output) |
| `SelectionListPropertyDialogService:296-303` | refactored onto the helper — no behaviour change, marked or not | |
| `SelectionTreePropertyDialogService:332` (marked branch) | refactored onto the helper — no behaviour change | `:337` unchanged |

The overflow estimate's `size.height − 40` (`:145`) is a pre-existing allowance and is left alone.

## 5. The range slider's lane in detail (D4)

- `TimeSliderVSAssemblyInfo:428-429` returns `VSDensityDefaults.titleHeight(this, titleInfo.getTitleHeight())`.
- `isHidden()` answers whenever it is set, in place of the height comparison, at `VSSelectionContainerService:164-166`,
  `:189`, `:229` and `AbstractLayout:480`.
- A contained slider's drawn height grows by 10 / 6 / 0 (`VSRangeSliderModel:127`). Export's
  `getAssemblySize` already counts the lane, and `layout()` will through D5.
- `chart-card-anchored-strip-lane-decisions.md` decision 6's account of the §05 exclusion is amended:
  the range slider leaves the excluded set, because it has no mechanical reason to be there and its
  siblings in the container all take the lane.

## 6. Dialogs

**Range slider only.** `RangeSliderPropertyDialogService` shows a title height only for a contained
slider (`:122-125`). It gains the follow-the-density checkbox, copied from
`SelectionContainerPropertyDialogService:89-93` (read) and `:154-168` (write):

- **Read:** the title height is `VSDensityDefaults.titleHeight(info, info.getTitleHeightValue())`;
  `titleHeightFollowsDensity` is `!isUserTitleHeight()` for a marked slider in a selection container,
  and null for an unmarked one or one in a Tab or group container, where its title never draws. Null
  hides the checkbox (`size-position-pane.component.ts:139`).
- **Write:** inside the existing `getTitleHeight() > 0` guard (`:263`), which keeps a standalone
  slider's stored lane untouched: null keeps today's logic at `:264-267`; true clears the author
  flag and stores `getLegacyTitleHeight()`; false stores the author's value and sets the flag —
  the three branches of `SelectionContainerPropertyDialogService:154-168`.

The container's title height already follows the density, and it has no padding pane (D1).

**Size follows density (D7, added 2026-10-06).** Size & Position gains a follow-the-default-density
checkbox for size, `SizePositionPaneModel.sizeFollowsDensity` (Java and TypeScript), with the same
null-means-no-opinion semantics as `titleHeightFollowsDensity`. The list, tree and container dialog
services read and write it.

- **Read:** `followsDensitySize()` when the box is one a size rule governs — a marked container, or a
  marked list or tree in LIST show type that is not a selection container's child — otherwise null,
  which hides the checkbox. A contained child's height belongs to the container's expand path (D2), and a
  dropdown's box is its title, so neither is the rule's box. A list in a Tab or group container is
  still the rule's box and gets the checkbox.
  - *Amended 2026-10-06.* The read was `!isUserSize()`. That ticked the box for an unflagged box at a
    size the rule never moves — anything saved before the flag at a non-default size, or a list
    dragged out of a container (which keeps the container's width and is not an author resize). The
    browser sends the ticked value back, so any OK, even for a title edit, reset such a box to the
    tier size. Such a box now reads false: the rule leaves it alone, so the size is the author's in
    effect, and an OK records the flag.
- **Write**, right after the existing `dialogService.setAssemblySize` / `setContainerSize` call and
  before the list and tree dialogs' show-type switch:
  - true → `resetSize(VizContext.of(info))`, ignoring the submitted width and height. The container
    dialog puts the tier size into the pane model before its `setContainerSize` call, so its
    children's widths follow as they do for a typed size.
  - false → set the flag and keep the submitted size. Unticking says "this size is mine" even if the
    numbers did not change.
  - null (a stale client, or a box the checkbox is not offered for) → set the flag only if the
    submitted width or height differs from the stored size.
- **Browser** (`size-position-pane.component.*`): the checkbox sits under Width and Height, shown when
  the flag is non-null. Ticked, it disables the width and height steppers, the way
  `titleHeightFollowsDensity` disables the title stepper. The server writes the tier size on Apply,
  so the steppers may show the old numbers until then. No comment goes in the `.html`.

## 7. Export and print

- **Export follows by construction.** Drawing already uses the computed stack (2.3); D2 changes the
  stored heights it sums, D4 the lane it counts, and D5 moves the computation without changing it.
- **Excel** takes no inset (`insetsTableCard()` is false). A modern contained list's expanded height
  changes, so a modern workbook's cell layout changes where such a list is expanded; legacy workbooks
  are identical.
- **Print layout** converts the container as a whole and skips its children (2.3). Only the
  container's default size (D6) reaches it.

## 8. Verification

### 8.1 Automated

- `getListBodyHeight()` at each tier, modern and legacy.
- The expand path: a modern list expands to `title + getListBodyHeight()`; a legacy list to exactly
  `listHeight × defh + title`; collapse unchanged.
- The overflow estimate: a modern container collapses a sibling when the expanding list would
  overflow, counting collapsed rows at the tier height.
- Drop into a container and device-layout sizing, modern and legacy (the legacy assertions pin the old
  numbers exactly).
- The two dialog tests from `cd3a36e8b` (`SelectionTreeShowTypeSizeTest`, `SelectionPaddingDialogTest`)
  pass unchanged — proof the refactor is a no-op.
- Range slider lane: each tier; author-set; unmarked. `TitleLaneHeightRowTest:75` loses its range
  slider line; check box and radio stay.
- The regression D4 exists for: a marked slider collapsed at a stored 20px expands and collapses.
- Range slider dialog: read and write round trip, including a null flag.
- `getChildTop` equals today's `CoordinateHelper.getContainerChildTop` for every child shape — a
  collapsed and an expanded list, a slider shown and hidden — with collapsed rows on and off.
- An unmarked `layout()` produces exactly the old positions; a marked `layout()` matches `getChildTop`.
- The size rule: each tier; an author size untouched; Revert to 300×240; an unmarked container at
  300×360 not shrunk on open; dense unchanged.
- **One existing test changes:** `SelectionDensitySizeTest.aModernizedContainerIsUnchanged`
  (`:119-131`) asserts a modernized container keeps both its padding and its size. It splits — the
  padding assertion stays (D1), the size assertion becomes D6's.
- **Two existing tests stay as they are, by design:** `SelectionContainerChildExportTest` builds an
  unmarked container around a marked list, the mixed case D5 leaves to the exporter's list-only rule;
  `SelectionContainerOutRowsExportTest` calls `getContainerChildTop`, whose numbers D5 keeps.
- **D7, the author size flag:**
  - persistence: a missing attribute parses as false; true is written and read back; false writes no
    attribute; `copyInfo` carries it;
  - both rules skip a flagged box on an open, a density change, Modernize and Revert, for a list, a
    tree and a container — and still rewrite an unflagged box at a recognized size;
  - `resetSize` clears the flag and writes the tier size (list, tree, container), and does nothing on
    a type without a density size;
  - composer resize flags a list, a tree and a container, but not a chart or table, and not the
    container's children;
  - `followsDensitySize()`: true for an unflagged box at a recognized size; false when flagged, at
    any other size, or on a type without a density size;
  - each dialog: the read offers the checkbox only where §6 says, reading false for an unflagged
    box at an unrecognized size; the write handles true (tier size, flag cleared, a container's
    children re-widthed), false (flag set) and null (flag set only on a changed size);
  - `size-position-pane.component.spec.ts`: the checkbox is hidden when the flag is null and shown
    otherwise; ticking it disables Width and Height, unticking re-enables them, and the model follows.
- Full `core` suite, and the portal spec run for `size-position-pane`.

### 8.2 Fixture and manual checks

The selection fixture from #6157 is extended rather than rebuilt, reusing its tooling (`sree.py`,
`export_all.py`, `compare.py`, `html_measure.js`, the STOMP composer driver) in the git-excluded
`.superpowers/baselines/selection-padding/`. **Before-exports are captured before any code lands.**
The fixture is one machine's local state: its numbers go in the PR description, its files do not.

Each tier copy (`SEL Modern Comfortable / Compact / Dense`) and `SEL Legacy` gains:

| Role | What it holds |
|---|---|
| `ConMix` | a container with two lists and a range slider, Show Current Selections on with two collapsed rows, one list expanded through the real `/selectionContainer/update` event over STOMP |
| `ConNew` | a container created fresh at the default size |
| `ConOld` | a marked container saved at 300×240 before the fix |

| Check | What | Where |
|---|---|---|
| MC-1 | `SEL Legacy` identical before and after — HTML byte for byte, PNG pixel for pixel, PDF geometry, PPTX and XLSX zip entries, CSV text | all formats |
| MC-2 | an expanded contained list shows all `listHeight` rows at every tier | viewer and composer; PDF, HTML, PNG at match layout |
| MC-3 | the range slider's lane equals its siblings'; a marked slider collapsed at 20px still expands and collapses | live, then export |
| MC-4 | for a modern container, saved child positions (asset XML) equal drawn positions (`html_measure.js`, viewer DOM) | after a fresh open, or a change that lays the viewsheet out |
| MC-5 | a range slider at the container's bottom edge is clipped and included in PDF and PNG as the viewer shows it | export |
| MC-6 | `ConNew` is 300×360 / 312 / 240; `ConOld` grows on open; Revert restores 300×240; dense unchanged | live, asset XML |
| MC-7 | expanding a list in a nearly full modern container collapses a sibling | live |
| MC-8 | an author's 300×240 container and 100×120 list survive reopening, a density change, Modernize and Revert; ticking Follow default density returns each to the tier size, and dragging it unticks the box again | composer, live |

Device layout is covered by a unit test only; the fixture has no device layout.

## 9. Risks and accepted costs

- **Existing contained lists change height at their next expand** (D3). In the composer the expand is
  undoable and a Save persists it.
- **A pre-fix bookmark restores the old expanded height** until the next expand (D3).
- **Existing marked range sliders take the lane on their next render** (D4), growing each container
  that holds one by 10 / 6 / 0 px at once. The stored expanded height already includes the lane and `VSRangeSliderModel:127` adds it again (a pre-existing double count, §10), so a slider grows by 20 / 12 / 0 px after its next expand.
- **XLSX shifts a standalone range slider too.** `PoiExcelVSExporter.getAnchorPosition` shifts every TimeSlider's anchor by `getTitleHeight()`, so a modern standalone slider lands 10 / 6 / 0 px lower in XLSX. Limiting the shift to contained sliders would move legacy XLSX output, and Excel quantises to rows.
- **Existing marked containers at exactly 300×240 grow on their first open** (D6) and can overlap what
  sits below them — the same cost the selection family accepted for standalone lists.
- ~~**An author who sized a marked container to exactly 300×312 or 300×360** is treated as seeded and
  follows the tier.~~ **Resolved by D7 (2026-10-06)**, for lists and trees as well. This understated
  the gap: 300×240 was the likeliest author size, and because the rule runs on every open the size was
  lost every time, not once. What remains is content saved **before** the flag existed: an older box
  at exactly a recognized size has no flag, so it follows the rule on its first open. Any author
  resize after that records the flag.
- **Ticking Follow default density moves the box** to the tier size at once, and the box can then
  overlap what sits below it — the same cost as D6's growth on open, but now asked for by the author.

## 10. Out of scope

- **`layout()`'s legacy mismatches.** An unmarked container's stored stack ignores a range slider's
  title and a collapsed dropdown's height. Fixing it would move unmarked exports through
  `prepareAssembly` and `needExport`.
- **A container with a hidden title.** Export always counts the title (`getContainerChildTop`); the
  viewer omits it (`getBodyTop()`). D5 moves the rule without changing that.
- **A contained range slider with a hidden title.** Export counts its lane only when its title is
  visible (`getAssemblySize:557`); the model adds the lane either way (`VSRangeSliderModel:127`). The
  title shows by default (`TimeSliderVSAssemblyInfo:1135`), and D5 moves export's rule unchanged.
- **The legacy collapsed-row disagreement** — 18px in the browser, 20 in export — recorded and kept in
  §11 of the selection family design.
- **The range slider's body**, and the size `ComposerVSSelectionListService.convertToRangeSlider`
  gives a converted slider: a track, not rows.
- **Other height-based slider checks are not switched to the hidden flag.** `PDFVSExporter:726`, `PPTVSExporter:757` and the SVG exporter decide whether to draw the track by comparing height with the lane; a hidden slider stored at a larger lane draws a zero-height image, which is harmless. `ComposerAdhocFilterService:612` (`size.height <= defh`) treats a marked collapsed slider as open-sized, as it already did for marked lists.
- **The container's card inset** — declined, D1.
- **Size changes that are not an author's design-time choice** do not set D7's flag: a script
  setting `size` at runtime, device and print layout sizes (kept in `VSAssemblyLayout`), and the
  derived writes listed under D7.
- **The dashboard wizard's resize** (`WizardVSObjectService.resizeVSObject`) does not set D7's flag
  either. It lays the whole sheet out on a 20px grid, so an author can land a list on exactly 100×120
  or a container on 300×240, and the rule then moves it on the next open — the behaviour before D7,
  on this one path. Added 2026-10-06; the service has no test harness, so the flag there is left to a
  follow-up.
- **The composer's resize of a selection list derives its list height from `defh`**
  (`ComposerObjectService:192-193`, `(height − title) / defh`). It is density-blind but sits outside the
  container slice; D7 only adds the flag beside it.

## 11. Open items

None.
