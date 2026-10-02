# Selection family padding — before/after export checks

These are the end-to-end proof for `feature-selection-padding`. No automated test renders an export,
so the legacy guarantee (design D6: existing content never changes) rests entirely on MC-1 and MC-6
below. The whole-branch review made merge conditional on nothing, but found two defects that only a
rendered comparison would have caught, so treat MC-2 and MC-4 as the ones most likely to find
something.

**Branch:** `feature-selection-padding`, 23 commits on `epic-74519` @ `250acdb39` (head `862d3d662`). Not pushed.
**Baselines:** `community/.superpowers/baselines/selection-padding/`. It holds:
- `before/` (MC-0), and the determinism control `before-control/`;
- `sel-fixture.zip`;
- `export_all.py`, which exports every file in the set;
- `compare.py`, which compares two folders by the MC-1 rules;
- a `README.txt` with the capture details.

**The fixture is one machine's local state.** `community/.superpowers/` is excluded from git
(`.git/modules/community/info/exclude`), so nothing built there is reviewable or pinned to a commit.
It must not be cited as a baseline the way a committed test can be: the numbers go in the pull
request description, the files stay local.

## What changed, and what must not

| | Before | After |
|---|---|---|
| Marked list/tree — card inset | none | **16 / 12 / 8**, drawn on all four edges |
| Marked list/tree — rows | flush to the border | **shifted and shrunk** by the inset |
| Marked list/tree — cell padding | none | **6·8 / 4·6 / 3·4** |
| Marked list/tree — *new* default size | 100 × 120 | **132×202 / 124×170 / 116×136** |
| Marked container — collapsed row height | 18 browser, `defh` export | **30 / 26 / 20**, both surfaces |
| Marked list/tree — Expand export | all rows, no inset | **all rows**, box grown by the inset |
| **Unmarked anything** | — | **byte-identical** |
| **Container card inset** | none | **still none** — scoped out |
| **Container default size** | 300 × 240 | **300 × 240**, unchanged |
| **Excel and CSV** | no inset | **still no inset** |

## Reference values

| | comfortable | compact | dense |
|---|---|---|---|
| card inset, every edge | **16px** | **12px** | **8px** |
| the same in PowerPoint (points, × 0.75) | 12pt | 9pt | 6pt |
| title lane | 30px | 26px | 20px |
| list/tree cell height | 28px | 24px | 20px |
| cell padding (top·left) | 6·8 | 4·6 | 3·4 |
| marked container collapsed row | 30px | 26px | 20px |

PNG is 1px per viewsheet pixel; PDF is 1pt per viewsheet pixel.

Modernize, and a density change, resize a list or tree whose stored size is one the seed could have
written: 100 × 120, or a tier default. It becomes the tier default (design D4, D5). Any other size is
the author's and is kept. Opening re-seeds as well (D6 was reversed on 2026-10-02), so a marked
100 × 120 list or tree is shown at the tier default from its first open.

## The fixture

Four roles plus a legacy control, per design §8.1. Deliberately lighter than the table fixture's
nine: the geometry lives in two shared helper bases rather than a grid, and the cell padding is
non-additive, so most of what the table captures existed to police arithmetic this work does not do.

| Role | As built | Covers |
|---|---|---|
| `SelList` | `SelectionList1`: Category, 100 × 120. Its 7 values overflow the box, so it is also the **tall** list MC-4 needs | the inset, the shift, cell padding, the five-row size rule, Expand |
| `SelTree` | `SelectionTree1`: Cal QTR › Cal Month, 100 × 120, with the script `this.expandAll=true;` | node indent against the horizontal inset, at both levels |
| `SelContainer` | `CurrentSelection1`: 300 × 240, holding three lists (`SelectionList2`, `3`, `4`) | the scope-out: container unchanged, children inset normally |
| `SelNoTitle` | `SelectionList5`: Category, title hidden, 100 × 120 | lane 0, inset still drawn |

`SelectionTree2`, a tree with its title hidden, is an extra. No check depends on it.

**One copy per tier, plus the control.** The layout is built four times, on the Examples/Order
Details worksheet.
- `SEL Modern Comfortable`, `SEL Modern Compact` and `SEL Modern Dense` are marked, and each pins
  its own density. One export pass covers all three tiers, and MC-7 needs no density edits.
- `SEL Legacy` (Save As, then the dashboard Revert) is unmarked. It is the control for MC-1.

**The product rules out two things, so the fixture does not have them:**
- **A tree inside a container.** No composer path puts one there. All three drop handlers offer
  "into container" only for a list, a range slider or another container, and a multi-column drop
  builds its tree outside the container. MC-6 covers list children only.
- **A container child with its title hidden.** The title pane drops its Visible checkbox inside a
  container, so `SelNoTitle` has to stand alone.

**A tree is collapsed at Match unless `expandAll` is set.** Export reads `expandAll` at Match and
`expandSelections` at Expand. Nodes opened by clicking are runtime-only and never saved, and no
dialog sets `expandAll`. Hence the script on `SelectionTree1`: without it, HTML has no second level
for MC-3.

## Formats

| Format | `format=` | Why |
|---|---|---|
| HTML | 5 | the measurable one — `html_measure.js` gives per-element rects. **Match only** (see below) |
| PDF | 2 | a second rendering pipeline; it caught what HTML could not in slices C1 and C2 |
| Excel | 0 | **only** to prove the opt-out |

The codes are `FileFormatInfo.EXPORT_TYPE_*`. 1 is PowerPoint and 3 is Snapshot.

**HTML has no Expand.** `HTMLVSExporter.isMatchLayout()` always returns true, so an HTML export
requested with Expand is byte-identical to its Match export. The `html-expand` files are kept so the
file set stays regular, but only PDF and Excel tell the two Data Size settings apart.

PPT and SVG/PNG are skipped: both extend `VSSelectionListHelper`, the same base PDF extends, so PDF
exercises their geometry. **If the inset ever moves out of that base into the leaves, this reasoning
expires and they come back.**

**Both Data Size settings.** Design §8.1 specified Match Layout only, on the reasoning that a
selection has a fixed row set so Expand adds little. That is now wrong: the branch fixes an
Expand-specific defect (MC-4), so Expand must be captured. §8.1 is superseded on this point.

**Expand means two settings, not one.** In the export dialog it is **Expand Components** with
**Expand Selection List/Tree** ticked. `AbstractVSExporter` adds lists, trees and containers to the
expandable set only when `isExpandSelections()` is true. So `match=false` on its own expands tables
and charts, and leaves every selection at its Match size.

## Not a failure

- **A list the author sized keeps its size.** A re-seed resizes only a 100 × 120 or tier-default
  box (D5). Any other size stays, so it can show fewer rows at a taller tier. That is correct.
- **A selection marked before the branch changes on its first open.** It takes the inset and cell
  padding, and a 100 × 120 box grows to the tier default, with no author action. This was accepted
  on 2026-10-02 (design D6 reversed): the feature is not yet live.
- **The container has no inset.** It was scoped out. Its card, its size and its children's placement
  are all unchanged; only its *collapsed row height* follows density, and only when marked.
- **An unmarked container's collapsed rows differ by 2px between browser and export** — 18 against
  `AssetUtil.defh`. That mismatch predates this branch and is knowingly preserved; see spec §11.
- **Excel shows no inset at all.** That is the `insetsTableCard()` gate working.
- **The vertical scroll track sits `inset.right` clear of the body's right edge.** Known, cosmetic,
  recorded as a follow-up by the whole-branch review.

---

## MC-0: capture the before baseline

Do this **first**, from `250acdb39` code — before building the branch. Without it there is nothing
to diff and MC-1 cannot run. The 2026-10-02 run stayed on the branch, and restored its 49 `core/`
and `web/` files to `250acdb39` in the working tree only.

1. Build and start the server from `250acdb39`.
2. In EM confirm `viewsheet.modernVisualization` = `true`. EM density does not matter, since every
   fixture copy pins its own.
3. Build the fixture (above), then export it into `before/` with `python export_all.py before`. The
   script makes these calls:

```
match:   GET /export/viewsheet/global/<name>?format=<N>&match=true
expand:  GET /export/viewsheet/global/<name>?format=<N>&match=false&expandSelections=true
```

   It writes 24 files: `{legacy,modern-comfortable,modern-compact,modern-dense}-{html,pdf,xlsx}-{match,expand}`.

4. Export the same 24 again into `before-control/`. `python compare.py before before-control` must
   report every file the same. That proves each format is deterministic before MC-1 relies on it.
5. Export the fixture asset with its worksheet (`sel-fixture.zip`), so the after run uses the same
   assemblies.

**Pass:** 24 files present, and the control is identical. **Record the exact commit the server was
built from**, and confirm it by `javap` on a loaded class rather than by file timestamp — a stale
`target/` is the most common way these runs go wrong.

---

## MC-1: what must not change (the legacy guarantee)

Build and start the server from `feature-selection-padding`, then re-import the fixture.

**No explicit re-seed is needed.** The fixture's marked copies store padding 0,0,0,0, because they
were marked before the branch existed. But every open re-seeds them in memory (defect A, accepted;
design D6 reversed). An export therefore draws the tier's inset and cell padding, and resizes each
100 × 120 list and tree to the tier default. The container and any author-sized list keep their
sizes.

**Leave the legacy copy alone.** MC-1 tests existing content exactly as it was stored.

Then export the same files, with the same names, with `python export_all.py after`.

Compare the **legacy** pairs with `python compare.py before after legacy-`:

| Files | Compared by |
|---|---|
| `legacy-html-{match,expand}` | byte for byte, after normalising the random generated class names (`c` followed by 6 or more digits) and sorting the CSS rules |
| `legacy-pdf-{match,expand}` | page size, every word's position, every vector drawing |
| `legacy-xlsx-{match,expand}` | every zip entry except `docProps/`, which carries timestamps |

**Pass:** every legacy file is identical to its before copy.

**If a legacy file differs,** rule out non-determinism first: re-export that one file from the same
build into a scratch folder and compare the two *after* copies with each other. If they differ from
each other the format is not deterministic — fall back to comparing by eye. If they match each other
but not the before copy, **the branch changed legacy output**, which is a real failure: keep the
files and report the differing detail.

**Fail:** a legacy file differs and the control shows the export is deterministic.

---

## MC-2: the inset is an inset, not just a shrink

This is the check that matters most. The whole-branch review found the browser *subtracting* the
inset without *moving* the body, so rows stayed flush to the top-left border with dead space at the
bottom-right. Export and print were correct; only live view was wrong. A shrink-without-shift passes
any test that measures the body's size and fails only a test that measures its position.

Open `after/modern-comfortable-html-match.html` beside the before copy, and measure with devtools or
`html_measure.js`:

1. **`SelList`'s first row starts 16px from the card's left border and 16px below the title lane.**
   Not 0. This is the whole check.
2. The **right** edge of the rows sits 16px inside the card's right border.
3. The **last** row's bottom sits 16px above the card's bottom border.
4. The card itself — border, background, rounded corner — is still at the assembly edge, unmoved.
5. Cell text sits 6px from the top of its row and 8px from the left of its cell.

Then the same four distances in `after/modern-comfortable-pdf-match.pdf`. **Live and export must
agree.**

**Pass:** all four distances are 16 in both files, and they match each other.
**Fail:** any distance is 0 (the shift is missing), or 32 (it is applied twice), or HTML and PDF
disagree.

---

## MC-3: `SelTree` — node indent against the horizontal inset

A tree's labels are already indented per level. The inset is added outside that, so the two compose
rather than fight.

Measure the same label in `SelTree` in both the modern and the **legacy control** export:

**Pass:** the marked label sits at **legacy offset + 16px** from the card's left edge, at every
level, in both HTML and PDF.
**Fail:** delta of 0 (the inset is missing for trees) or 32 (applied twice).

Using a delta rather than an absolute means this check needs no indent constant and does not go
stale if the indent changes.

---

## MC-4: Expand mode — every row survives the inset

The dropped row existed only partway through the branch:
1. `b5bb0326b` made the export inset shrink the content box. The expanded box was still sized to the
   rows alone, so the last row's midpoint fell past the shrunk content bottom and the row was
   dropped. Exactly one row was lost at every tier.
2. `c90b6b5d1` fixed it by adding the inset to the expanded height
   (`AbstractVSExporter.getSelectionHeight`).

Both commits are on the branch. The pre-branch baseline draws no inset, so its Expand export already
shows every row. The check is that the inset costs no row, not that a row comes back.

Use `SelList` (`SelectionList1`, 7 values), the tall list, in `SEL Modern Comfortable`.

1. Count its rows in `before/modern-comfortable-pdf-expand.pdf`. The baseline shows 7, at every tier.
2. Count them in `after/modern-comfortable-pdf-expand.pdf`.
3. Count the values the list holds in the live viewer.

**Pass:** all three counts are equal.
**Fail:** the after count is short of the before count. The expanded box does not carry the inset.

Expand's contract is that every row is visible, so a short count here is a real defect, not a
cosmetic one.

---

## MC-5: Excel and CSV take no inset

`ExcelSelectionListHelper` bypasses the shared base, but `ExcelSelectionTreeHelper` **inherits
through it** — so Excel's tree would pick the inset up by inheritance if the `insetsTableCard()`
gate were not doing its job. Only a unit test over a mocked exporter covers this today, which is why
a real workbook is worth diffing.

**The re-seed on open rules out a whole-workbook diff.**
- Opening grows every 100 × 120 list and tree to the tier default: `SelList`, `SelTree`,
  `SelNoTitle` and `SelectionTree2`.
- Excel derives each assembly's rows and columns from its pixel box, so those cells move for that
  reason alone.
- A copy set back to 100 × 120 does not help: the next open resizes it again.

So the check is structural. An inset would show as blank spacer rows or columns between an
assembly's title cell and its first value. Compare where each assembly's title and first values
land, before and after.

**Pass:** in `modern-{comfortable,compact,dense}-xlsx-{match,expand}`, every title and first value
sits in the same cell as before, for the list **and the tree**. Wider columns and more visible rows
are the resize, not an inset.
**Fail:** a first value moved down or right of its title, away from where the before copy had it.

---

## MC-6: the container is unchanged

The container was scoped out of the inset after the whole-branch review. This proves the removal is
complete rather than merely intended.

On `SelContainer` in the `modern-comfortable` export, before and after:

1. **The container's card has no inset** — its first collapsed row starts at the card edge, exactly
   as before.
2. **Its default size is 300 × 240**, unchanged. It did not grow by 32.
3. **Its children inset normally** — a list inside the container shows the same 16px gap a
   standalone list does. Being inside a container is no longer a special case.
4. **Its collapsed row height is 30** at comfortable, where it was 18 in the browser and 20 in
   export. This is the one container thing that *does* change, and only when marked.
5. In the **legacy** copy, every one of the above is untouched: rows at 18 in the browser and
   `defh` in export, mismatched exactly as before.

**Pass:** 1, 2 and 5 match the before copy; 3 shows the same inset as a standalone list (16 at
comfortable), where before showed none; 4 reads 30.
**Fail:** the container grew, gained an inset, or its legacy rows moved.

---

## MC-7: compact and dense

Repeat MC-2 and MC-6 on the `modern-compact` and `modern-dense` exports, which the same pass already
produced. Each copy pins its own tier. Do not change the EM density: it does not reach them, and no
per-dashboard edit is needed beyond MC-1's re-seed.

**Pass:**

| | comfortable | compact | dense |
|---|---|---|---|
| inset, all four distances | 16 | 12 | 8 |
| cell text top · left | 6 · 8 | 4 · 6 | 3 · 4 |
| marked container collapsed row | 30 | 26 | 20 |

**Fail:** any tier reads another tier's number, or the legacy control moves at any tier.

---

## Results

Fill in as the checks are run. **Leave a cell blank rather than guessing** — an invented number here
poisons every future comparison against this baseline.

| Check | comfortable | compact | dense | Notes |
|---|---|---|---|---|
| MC-0 before baseline captured | pass | pass | pass | 2026-10-02 12:20; 24 files (legacy + one copy per tier); 250acdb39 by javap; fonts present; repeat export identical; see baselines README |
| MC-1 legacy unchanged | pass | n/a | n/a | Pre-fix (12:49, `after-unseeded/`) and post-fix (14:53, `after/`): all 6 legacy files identical to before (HTML byte for byte; PDF and xlsx normalised). Legacy PNG and PPTX are unchanged by the fix |
| MC-2 inset is an inset | pass | pass | pass | Post-fix, automated. All four roles (list, tree, no-title list, no-title tree) shift by exactly inset + cell padding right and inset down, in PDF (+23.8/16, +17.8/12, +11.9/8) and HTML (+24/16, +18/12, +12/8). PNG: lists +16/12/8 against pre-fix, trees unchanged. PPTX after E: every role 12 / 9 / 6pt inside its card (±0.5, from whole-point rounding). Pre-fix, lists failed (defects B, C). Live-view part not yet run (manual) |
| MC-3 tree indent delta | pass | n/a | n/a | PDF +23.8 and HTML +24 at both levels = inset 16 + cell padding 8 (PDF's leading 1px is scaled by 100/132). The +16 criterion above omits the cell padding |
| MC-4 expand row count | pass | n/a | n/a | PDF: Category values 14/14 and tree nodes 56/56, before = after at every tier, now with the list inset drawn too. Live count not yet taken (manual) |
| MC-5 Excel opt-out | pass | pass | pass | Match: every title and first-value cell unchanged. The post-fix workbooks are identical to the pre-fix ones, so the fixes did not touch Excel. Expand: the lower assemblies sit a few rows higher, from the resize on open (defect A), not from an inset |
| MC-6 container unchanged | pass | pass | pass | Points 1, 2, 5: the container card is drawn identically. Point 3: children shift by inset + cell padding. After F, a marked list child keeps the viewer's 150px at match layout (comfortable 98→248, 3 rows; compact 90→240, 4; dense 78→228, 6), and the rows after it follow, in PDF, PNG and PPTX. HTML already matched. Point 4 is not exercised: `CurrentSelection1` has `showCurrent=false` |
| MC-7 tiers | n/a | pass | pass | Same results as comfortable at 12 / 8 |

**Defects found by the after run** (server built from `862d3d662`). B, C and D were fixed on 2026-10-02 and verified by the 14:53 run; E and F were fixed and verified by the 15:18 run (`after2/`). All fixes are uncommitted.
- **A. Every open re-seeds marked selections, contrary to D6.**
  - `RuntimeViewsheet.<init>` → `gotoDefaultBookmark` (`:490`) → `getBookmark(INITIAL_STATE)` →
    `Viewsheet.parseState` → `AbstractVSAssembly.parseState` → `reseedAfterRestore` →
    `seedChromeDefaults`. Captured with jdb during a REST export.
  - The seed now writes size, cell padding and inset, so a dashboard marked before the branch grows
    100 × 120 → 132 × 202 and takes cell padding on open, with no author action.
  - This also makes MC-1's explicit re-seed redundant for rendering. It was skipped.
  - **Accepted 2026-10-02, not fixed.** The feature is not yet live, so the content marked before
    it is a small window. Design D6 is reversed and §9 records the cost.
- **B. No export inset for selection lists. Fixed.** `insetRowBounds` returns early on `getExporter() == null`.
  - The list helpers are built without an exporter: `PDFVSExporter:508`, `SVGVSExporter:228` and
    `HTMLVSExporter:528`.
  - `PPTSelectionListHelper` keeps the exporter in its own private field (`:220`).
  - Trees call `setExporter` and inset correctly in PDF.
- **C. HTML export applies no card inset, for lists or trees. Fixed.** The cell padding is applied
  (+8 / +6 / +4). The HTML helpers write their own markup and never reach `insetRowBounds`. The
  design's §7 assumed the shared base would cover them.
- **D. Viewer: the selection kebab sits `padding.top` too low and `padding.left` too far in. Fixed.**
  - `VSObjectContainer.getLaneInset` (`vs-object-container.component.ts:531-533`) now returns the
    selection's padding.
  - A selection's title lane stays flush with the card edge, unlike a chart's or a table's.
- **E. PowerPoint applies the selection inset in points, unscaled. Fixed and verified (15:18 run).**
  `insetRowBounds` now scales the inset by `cHelper.getScale()`. That is 1 in every other format, so they don't change.
  - `CoordinateHelper.getBounds` returns bounds already scaled by `getScale()`, which is 0.75 in
    PowerPoint.
  - `insetRowBounds` then adds the pixel inset to those point bounds, so a 16px inset lands as
    16pt (21px). Measured: rows 16 / 12 / 8 pt inside the card where 12 / 9 / 6 is right.
  - Trees had this before the fix; B extends it to lists. PDF, SVG/PNG and HTML have a scale of 1
    and are unaffected.
- **F. A short container child loses its rows. Fixed for marked children and verified (15:18 run).**
  - At match layout, a marked list child is now clipped at the container's bottom, measured from where
    the container draws it (`CoordinateHelper.getContainerChildTop`). That is where the viewer stacks
    it, so its height matches the viewer's.
  - An unmarked child keeps the old clip, from its stored offset.
  - The stored offsets are an old stacking that assumed a 20px title and 150px for every child.
    Before the fix, the comfortable Customer child was clipped to 278 - 208 = 70.
  - The export gives a container's list child a 40px body, where the live viewer gives it more.
  - At comfortable, 16 + 16 of inset leaves 8px, so no 28px row fits and the child exports empty.
- **G. Out-selection rows overlapped a marked container's children in export. Fixed and verified (15:53 run).**
  - With Show Current Selections on, export draws one collapsed row per outside selection. Since
    `09ca2de2b`, PDF, PowerPoint and SVG draw those rows at the density height (30 / 26 / 20).
  - But the children were stacked as if the rows were 20 (`CoordinateHelper.getContainerChildTop`),
    so they overlapped the rows by 10 / 6 / 0 px per row.
  - HTML drew the rows at 20 and placed its children at 20 as well
    (`HTMLCoordinateHelper.writeSelections`, `adjustChildAssemblyPosition`).
  - All three now use `getOutSelectionRowHeight(defh)`, which is `defh` for an unmarked container.
  - MC-6 point 4 covers this, and the fixture does not exercise it: `CurrentSelection1` has
    Show Current Selections off.
  - Verified on `SEL OutRows Comfortable` (a copy with Show Current Selections on, so four outside
    selections give four out rows).
    - Pre-fix PDF: the rows sat at 68-188 and the first child at 148-178, overlapping them by 40.
    - Post-fix: the first child sits at 188-218 in PDF, HTML, PNG and PPTX (141pt). HTML's rows
      are 30 tall, where they were 20.
- **H. A marked list child can be left out of a match-layout export. Fixed, not yet re-exported.**
  - `AbstractVSExporter.needExport` exports a container's list child only when its stored offset
    lies inside the container (`0 < y - container y < height`).
  - Turning Show Current Selections on re-runs the container's stored layout with 20px rows, which
    pushes Customer's stored offset past 240, so PDF, PNG and PPTX skip it.
  - The container draws it at 218, the viewer shows it, and HTML (which resets the offsets to the
    drawn positions) exports it.
  - It is the same stale-offset cause as F.
  - `needExport` now measures a marked list child from the same drawn top as the clip
    (`getContainerChildTop`). A marked child is left out only when the container draws it past its
    bottom.
  - An unmarked child is still judged by its stored offset.

**Run environment to record with the results:** the commit each server was built from (confirmed by
`javap`, not timestamp), whether `config/fonts/` existed at JVM start, and the density each fixture
viewsheet pins. The slice-C runs lost a day to a font environment that differed between the before
and after captures, which made every text position differ for a reason unrelated to the change.
