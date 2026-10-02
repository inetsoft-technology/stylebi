# Selection family padding — before/after export checks

These are the end-to-end proof for `feature-selection-padding`. No automated test renders an export,
so the legacy guarantee (design D6: existing content never changes) rests entirely on MC-1 and MC-6
below. The whole-branch review made merge conditional on nothing, but found two defects that only a
rendered comparison would have caught, so treat MC-2 and MC-4 as the ones most likely to find
something.

**Branch:** `feature-selection-padding`, 22 commits on `epic-74519` @ `250acdb39`. Not pushed.
**Baselines:** `community/.superpowers/baselines/selection-padding/` — create it; nothing exists yet.

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
| Marked list/tree — Expand export | last row dropped | **all rows present** |
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

An existing assembly keeps its stored size — only a newly created one takes the density default. So
a fixture built before Modernize and then Modernized keeps 100 × 120 and shows **fewer rows**, which
is correct and is not what the size rule addresses.

## The fixture

Four roles plus a legacy control, per design §8.1. Deliberately lighter than the table fixture's
nine: the geometry lives in two shared helper bases rather than a grid, and the cell padding is
non-additive, so most of what the table captures existed to police arithmetic this work does not do.

| Assembly | Role | Covers |
|---|---|---|
| `SelList` | plain selection list | the inset, the shift, cell padding, the five-row size rule |
| `SelTree` | selection tree | node indent against the horizontal inset |
| `SelContainer` | container holding a list and a tree | the scope-out: container unchanged, children inset normally |
| `SelNoTitle` | list with its title hidden | lane 0, inset still drawn |

Build each twice: one **modernized** viewsheet (`SEL Modern`) and one **unmarked** copy
(`SEL Legacy`, via Save As then the dashboard Revert). The legacy copy is the control for MC-1.

Add one **tall** list — more rows than its box can show — to `SEL Modern`. MC-4 needs it.

## Formats

| Format | `format=` | Why |
|---|---|---|
| HTML | 3 | the measurable one — `html_measure.js` gives per-element rects |
| PDF | 2 | a second rendering pipeline; it caught what HTML could not in slices C1 and C2 |
| Excel | 1 | **only** to prove the opt-out |

PPT and SVG/PNG are skipped: both extend `VSSelectionListHelper`, the same base PDF extends, so PDF
exercises their geometry. **If the inset ever moves out of that base into the leaves, this reasoning
expires and they come back.**

**Both Data Size settings.** Design §8.1 specified Match Layout only, on the reasoning that a
selection has a fixed row set so Expand adds little. That is now wrong: the branch fixes an
Expand-specific defect (MC-4), so Expand must be captured. §8.1 is superseded on this point.

## Not a failure

- **An existing assembly keeps its old size.** Only new ones take the density default. A Modernized
  100 × 120 list showing three rows instead of five is correct.
- **The container has no inset.** It was scoped out. Its card, its size and its children's placement
  are all unchanged; only its *collapsed row height* follows density, and only when marked.
- **An unmarked container's collapsed rows differ by 2px between browser and export** — 18 against
  `AssetUtil.defh`. That mismatch predates this branch and is knowingly preserved; see spec §11.
- **Excel shows no inset at all.** That is the `insetsTableCard()` gate working.
- **The vertical scroll track sits `inset.right` clear of the body's right edge.** Known, cosmetic,
  recorded as a follow-up by the whole-branch review.

---

## MC-0: capture the before baseline

Do this **first**, from `epic-74519` @ `250acdb39` — before building the branch. Without it there is
nothing to diff and MC-1 cannot run.

1. Build and start the server from `epic-74519` @ `250acdb39`.
2. In EM confirm `viewsheet.modernVisualization` = `true` and `viewsheet.density` = `comfortable`.
3. Build the fixture (above) and export it into
   `community/.superpowers/baselines/selection-padding/before/`:

```
GET /export/viewsheet/global/<name>?format=<N>&match=<true|false>
```

12 files: `{modern,legacy}-{html,pdf,xlsx}-{match,expand}`.

4. Export the fixture asset itself (`sel-fixture.zip`) so the after run uses the same assemblies.

**Pass:** 12 files present. **Record the exact commit the server was built from**, and confirm it by
`javap` on a loaded class rather than by file timestamp — a stale `target/` is the most common way
these runs go wrong.

---

## MC-1: what must not change (the legacy guarantee)

Build and start the server from `feature-selection-padding`. Re-import the fixture. Export the same
12 files, with the same names, into `after/`.

Compare the **legacy** pairs:

| Files | Compared by |
|---|---|
| `legacy-html-{match,expand}` | byte for byte, after normalising the random generated class names (`/c[0-9]{9,}/`) and sorting the CSS rules |
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

Open `after/modern-html-match.html` beside the before copy, and measure with devtools or
`html_measure.js`:

1. **`SelList`'s first row starts 16px from the card's left border and 16px below the title lane.**
   Not 0. This is the whole check.
2. The **right** edge of the rows sits 16px inside the card's right border.
3. The **last** row's bottom sits 16px above the card's bottom border.
4. The card itself — border, background, rounded corner — is still at the assembly edge, unmoved.
5. Cell text sits 6px from the top of its row and 8px from the left of its cell.

Then the same four distances in `after/modern-pdf-match.pdf`. **Live and export must agree.**

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

## MC-4: Expand mode — the dropped row

The branch fixes a defect where the expanded box was sized without the inset, so the last row's
midpoint fell past the shrunk content bottom and the row was dropped. Exactly one row was lost at
every tier.

Use the **tall** list added to `SEL Modern`.

1. Count its rows in `before/modern-pdf-expand.pdf`.
2. Count them in `after/modern-pdf-expand.pdf`.
3. Count them in the live viewer.

**Pass:** the after count equals the live count, and is **one more** than the before count.
**Fail:** after equals before (the fix did not take), or after is still short of live (the expanded
box is still undersized).

Expand's contract is that every row is visible, so a short count here is a real defect, not a
cosmetic one.

---

## MC-5: Excel and CSV take no inset

`ExcelSelectionListHelper` bypasses the shared base, but `ExcelSelectionTreeHelper` **inherits
through it** — so Excel's tree would pick the inset up by inheritance if the `insetsTableCard()`
gate were not doing its job. Only a unit test over a mocked exporter covers this today, which is why
a real workbook is worth diffing.

**Pass:** `modern-xlsx-match` and `modern-xlsx-expand` are identical before and after, every zip
entry except `docProps/`. Both the list **and the tree**.
**Fail:** any cell geometry moved.

---

## MC-6: the container is unchanged

The container was scoped out of the inset after the whole-branch review. This proves the removal is
complete rather than merely intended.

On `SelContainer` in the modern export, before and after:

1. **The container's card has no inset** — its first collapsed row starts at the card edge, exactly
   as before.
2. **Its default size is 300 × 240**, unchanged. It did not grow by 32.
3. **Its children inset normally** — a list inside the container shows the same 16px gap a
   standalone list does. Being inside a container is no longer a special case.
4. **Its collapsed row height is 30** at comfortable, where it was 18 in the browser and 20 in
   export. This is the one container thing that *does* change, and only when marked.
5. In the **legacy** copy, every one of the above is untouched: rows at 18 in the browser and
   `defh` in export, mismatched exactly as before.

**Pass:** 1, 2, 3 and 5 match the before copy; 4 reads 30.
**Fail:** the container grew, gained an inset, or its legacy rows moved.

---

## MC-7: compact and dense

Repeat MC-2 and MC-6 at the other two tiers. Set the tier through the **per-dashboard** density
override, not the EM setting — the fixture pins its own density, and an EM change will not reach it.

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
| MC-0 before baseline captured | | | | |
| MC-1 legacy unchanged | | n/a | n/a | |
| MC-2 inset is an inset | | | | |
| MC-3 tree indent delta | | n/a | n/a | |
| MC-4 expand row count | | n/a | n/a | |
| MC-5 Excel opt-out | | | | |
| MC-6 container unchanged | | | | |
| MC-7 tiers | n/a | | | |

**Run environment to record with the results:** the commit each server was built from (confirmed by
`javap`, not timestamp), whether `config/fonts/` existed at JVM start, and the density each fixture
viewsheet pins. The slice-C runs lost a day to a font environment that differed between the before
and after captures, which made every text position differ for a reason unrelated to the change.
