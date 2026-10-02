# Selection family padding — manual checks

**Superseded in part, 2026-10-02.** The before/after export comparison now lives in
`2026-10-02-selection-padding-export-checks.md`, which is the document to run. Use this one only for
the on-screen observations it carries that the export checks do not.

**MT-1 is obsolete.** It asks whether the container's card inset reaches the browser. The container
was scoped out of the inset entirely after the whole-branch review, so the question no longer has an
answer to find — the container has no inset on any surface. What replaced it is MC-6 in the new
document, which proves the container is *unchanged* rather than asking where its inset went.

**MT-5 still stands** and is the check most worth a human's time: `ExcelSelectionTreeHelper`
inherits through the shared helper base, so Excel's tree is the one place the opt-out could fail by
inheritance, and only a unit test over a mocked exporter covers it today.

Five checks that no automated test covers. Captured numbers: **none yet**. Every value below is
an expectation derived from the implementation and the tier matrices, not a measurement.

**Branch:** `feature-selection-padding`
**Capture status:** NOT PERFORMED. No server was built or started when this was written, so every
expected value is unverified and every Measured line is blank. Fill them in with the date and
commit when the checks are run.

## The fixture is local state

The tooling lives in `.superpowers/baselines/density-padding-export/` and any fixture built for
these checks goes in `.superpowers/baselines/selection-padding/`. `community/.superpowers/` is
excluded from git (`.git/modules/community/info/exclude`), so nothing there is reviewable or pinned
to a commit. Do not cite it as a baseline in a pull request the way a committed test can be; put
the measured numbers in the PR description and leave the files on one machine.

## Why these are manual

| Check | Why no test |
|---|---|
| MT-1 container | Needs the viewer layout engine and an export of the same dashboard, compared. No unit boundary spans both. |
| MT-2 list | The five-row rule is a size computed at Modernize time; the visible row count needs a rendered card. |
| MT-3 tree | Indent against inset needs real label widths in a rendered node. |
| MT-4 no title | Needs the export pipeline with the title hidden. |
| MT-5 Excel | Needs a real workbook; the gate is tested, the shared-base inheritance is not. |

## Reference values

| | comfortable | compact | dense |
|---|---|---|---|
| card inset, all edges | 16px | 12px | 8px |
| title lane | 30px | 26px | 20px |
| cell height | 28px | 24px | 20px |
| default list size | 132x202 | 124x170 | 116x136 |
| default container size | 332x272 | 324x264 | 316x256 |

## Shared setup

1. Build and run per `CLAUDE.md`, from this branch.
2. EM, Settings, Presentation: set density to **comfortable** (repeat at compact and dense where a
   check says so).
3. Build one viewsheet holding four **modernized** assemblies: `SelList`, `SelTree`,
   `SelContainer` (holding one list and one tree), `SelNoTitle` (a list with its title hidden).
   Build a second viewsheet with the same four **not** modernized, as the legacy control.
4. Match Layout only. Expand adds nothing here, because a selection has a fixed row set.
5. Reusable tooling: `sree.py` (density flip, export pull), `vsclient.js` and `flows.js` (Save As,
   Modernize/Revert), `html_measure.js` (per-element rects), `inset_audit.py` (PDF bands).

Measure with devtools in the viewer, or `html_measure.js` on the HTML export. For PDF, use
`inset_audit.py` or overlay on a pre-change export.

---

## MT-1 — does the container's inset reach the browser? (the critical check)

**Why it matters.** This is open and was not settled by reading. Export honours the container
inset: `VSCurrentSelectionHelper` places its rows through `getContentBounds`, which subtracts it.
The browser may not. It was reported, and has not been verified, that no selection-container
component reads `model.padding` and that container children are positioned by the viewsheet
layout engine; treat that as a hypothesis, which this check exists to test. So a container could render its rows at the
card edge in live view while the export indents them by 16px, which is the live/export mismatch this
design exists to remove.

**Standalone, about five minutes.**

Setup: at comfortable density, a modernized dashboard holding one selection container that
contains one list and one tree. Tooling: devtools in the viewer, and `inset_audit.py` (or any PDF
viewer with a ruler) for the export.

1. At comfortable, open the modernized dashboard in the **viewer**.
2. In devtools, take the container's outer card element and its first out-selection row (the first
   child list or tree). Measure the left offset of that row from the card's left edge. Call it
   **L**.
3. Export the same dashboard to **PDF** at Match Layout. Measure the same left offset of the first
   row from the card's left edge, converted to px at the export's scale. Call it **P**.
4. Compare L and P.

**Expected:** L = P = **16px** (the card inset), with the child's own inset not added on top
(not 32px).

Measured L: ____  Measured P: ____  Date/commit: ____

**Pass:** L = P = 16px.

**Failure signatures.**

- **L = 0 and P = 16 is a DEFECT FOUND, not a check passed.** The browser ignores the container's
  inset while export applies it. Record the exact numbers and flag the mismatch prominently in the
  PR. The fix is in the container's browser layout, which must subtract the inset where children
  are placed.
- L = P = 0: neither side applies the inset; it reaches nothing, so the container was not
  seeded or the helper ignores it.
- L = P = 32: the child is inset by the container and again by its own card (the double-inset risk
  in design section 5, branch 1).
- L = 16 and P = 0: export lost the inset; `VSCurrentSelectionHelper` is not reaching
  `getContentBounds`.

---

## MT-2 — `SelList`: the plain case and the five-row size rule

1. Modernize a fresh default-size selection list at comfortable. Open it in the viewer.
2. Measure the body inset on all four edges, count fully visible rows, and read the stored size.
3. Repeat at compact and dense, and in PDF export.

**Expected, comfortable:** body inset **16px** each edge; **5 rows** visible; default size
**132x202**. Compact: 12px, 124x170. Dense: 8px, 116x136. Row height 28 / 24 / 20.

Measured: ____  Date/commit: ____

**Failure signatures.** 4 rows visible, or a partly cut fifth: the size was not grown to absorb the
inset. Size 100x120 (the legacy default): Modernize did not resize. Do not mistake 100x120 on an unmodernized assembly for a failure; only a modernized one should read 132x202. Inset 16 in export but 0 in
HTML, or the reverse: browser and export subtract differently.

---

## MT-3 — `SelTree`: node indent against the horizontal inset

1. Modernize a selection tree with at least three levels and some long labels. Viewer, then PDF.
2. Measure the body inset and the left edge of a level-1, level-2 and level-3 label.

Also build the same tree unmarked (the legacy control) and measure the same labels there, in the
viewer and in PDF. This check is a delta, so it needs no indent constant.

**Expected, comfortable:** inset **16px** each edge; each label sits at **legacy offset + 16px**
from the card's left edge, at every level, in both HTML and PDF. No label clipped at the right.

Measured: ____  Date/commit: ____

**Failure signatures.** Delta of 0: the inset is missing for trees. Delta of 32: the inset is applied twice. Labels
clipped at the right edge: the available width was not reduced by the inset. Level offsets
different between HTML and PDF: tree geometry is computed in two places.

---

## MT-4 — `SelNoTitle`: hidden title, lane 0, inset still drawn

1. Modernize a selection list and hide its title. Viewer, then PDF.
2. Measure the gap above the first row, and all four insets.

**Expected, comfortable:** title lane **0** (not 30); inset **16px** on every edge including the
top, so the first row starts 16px below the card top.

Measured: ____  Date/commit: ____

**Failure signatures.** First row at 46px (16 + 30): the lane is still reserved. First row at 0:
hiding the title also dropped the inset. A gap in export but not in the viewer, or the reverse.

---

## MT-5 — Excel opt-out: identical before and after, all tiers

**Why it matters.** `insetsTableCard()` gates the card inset and Excel returns false, so a workbook
must not change. The specific risk is `ExcelSelectionTreeHelper` inheriting the inset through the
shared base.

1. Export the unmarked control viewsheet to `.xlsx` at Match Layout. Export the modernized fixture
   likewise.
2. Compare cell positions, row heights and column widths for each selection, at comfortable,
   compact and dense.
3. Compare each against an Excel export of the same dashboard from a build without this branch.

**Expected:** identical in every tier, for list, tree, container and the no-title list. No offset
of 16, 12 or 8 anywhere.

Measured: ____  Date/commit: ____

**Failure signatures.** Any row or column shifted by 16/12/8, or a tree workbook that differs while
the list workbook does not: the tree helper is applying the inset from the shared base instead of
honouring the gate.

---

## What remains for a human

All five checks. Run MT-1 first: it decides whether the design is complete. PPT and SVG/PNG are
skipped on purpose, because both extend `VSSelectionListHelper`, the base PDF extends. If the inset
ever moves from that base into the leaves, that reasoning expires and they come back.
