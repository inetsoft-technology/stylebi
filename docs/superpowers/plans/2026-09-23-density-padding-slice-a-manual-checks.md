# Density padding, slice A — manual checks

Six checks that no automated test covers. Five are release-gating; MT-2 additionally surfaces an
open design question.

**Branch:** `feature-density-padding` @ `c27f5e2d3`
**Automated state:** `core` 5977/0/0 · portal 1497 · em 376 · TL 46 · utils-inclusive build exit 0

## Why these are manual

| Check | Why no test |
|---|---|
| MT-1 | The round trip spans a controller method and a service method with no unit-level fixture. A test that asserted it was deleted for proving only `x − p + p == x`. |
| MT-2 | Needs a loaded `format.css`. |
| MT-3 | Needs a wrapped-text column. |
| MT-4 | Needs a crosstab span cell. |
| MT-5 | `addTable` is private and needs a live `ViewsheetSandbox`. Everything downstream of it is tested. |
| MT-6 | End-to-end dialog behaviour; the pieces are unit-tested, the path is not. |

## Reference values

Rendered heights are unchanged from what shipped. The *stored* numbers changed to absorb the
padding — that is the whole design, and it is what MT-1 and MT-3 are checking.

| | comfortable | compact | dense |
|---|---|---|---|
| data row **rendered** | 28px | 24px | 20px |
| header row **rendered** | 30px | 26px | 22px |
| cell padding (top, left, bottom, right) | 6, 8, 6, 8 | 4, 6, 4, 6 | 3, 4, 3, 4 |
| chart card inset, all edges | 16px | 12px | 8px |
| *(stored data row, for reference)* | *16px* | *16px* | *14px* |

## Shared setup

1. Build and run per `CLAUDE.md`.
2. EM → Settings → Presentation → set visualization density to **comfortable** (or
   `viewsheet.density=comfortable`).
3. Create a dashboard with a table bound to a few string columns. Leave row heights at their
   defaults.
4. **Modernize** the dashboard, so the assembly is marked and seeded.

Measure pixels with browser devtools on the row or cell element. Where a check says "compare to a
pre-change build", export the same dashboard from `epic-74519` and overlay the two PDFs.

---

## MT-1 — an author-typed row height survives a round trip

**Why it matters.** The composer stores a resize as a *content* height by subtracting the padding,
and the render adds it back. If those two read different sources the height drifts silently on
every save.

1. On the marked table, drag a data row to **40px**.
2. Save the dashboard, close it, reopen it.
3. Measure the same row.

**Pass:** still 40px.
**Fail:** 52px or 28px — a drift of exactly 12px (2 × 6px padding-y) per round trip means
`ComposerVSTableService:958/969` and `BaseTableService:492` are reading different sources. Repeat
the save/reopen: a compounding drift confirms it.

---

## MT-2 — a `format.css` table style (highest risk, and one open question)

**Why it matters.** This is the regression most likely to reach a customer, because it only appears
on a dashboard that has a table stylesheet. It is also the case `isCSSRowFullyPadded` exists for.

1. Upload a `format.css` via EM → Settings → Presentation → Look and Feel, defining a `TableStyle`
   rule with **2px padding on all edges**.
2. Build two dashboards with the same table: one **not** modernized, one modernized.
3. Measure a data row in each.

**Part A — the unmarked table must not move.**

| | pre-change | expected now |
|---|---|---|
| unmarked + `format.css` | 20 + 4 = **24px** | **24px** |

**Pass:** 24px. **Fail:** 32px means the seeded 12px is being added on top of the stylesheet's 4px —
every stylesheet customer's tables just got taller.

**Part B — an open question, not a pass/fail.** Measure the *modernized* table with the same
stylesheet:

| | height | why |
|---|---|---|
| marked, no stylesheet | 28px | stored 16 + seeded 12 |
| marked, **with** the 2px stylesheet | **20px** | stored 16 + css 4; the stylesheet wins wholesale |
| same table unmarked | 24px | stored 20 + css 4 |

I traced this in `VSTableLens.getRowPadding` and `BaseTableService:466/492` and it is what the code
does today. It follows the documented "css wins" rule, but it has a consequence nobody decided:
**a stylesheet customer's table gets *shorter* when modernized** — 20px, which is identical to
dense, and 4px shorter than before they modernized it.

Record what you measure. If 20px is wrong, the fix is in `getRowPadding`'s fully-covered branch —
it would need to return `max(css, seeded)` for marked tables while still returning `css` alone for
unmarked ones. That was never specified either way.

---

## MT-3 — wrapped rows grow once, not per line

**Why it matters.** Multi-line row height comes from `getWrappedHeight`, which already sums the
lines; the padding is then added once. Adding it per line would inflate wrapped rows badly.

1. On the marked table, enable wrapping on a column and give it text that wraps to **3 lines**.
2. Measure the wrapped row on screen, then export to PDF and measure it there.

**Pass:** the row is `(3 × line height) + 12px`. The padding contributes **12px total**.
**Fail:** the row is `(3 × line height) + 36px` — padding applied per line.

A quick sanity check without measuring line height: a 3-line row should exceed a 1-line row by
exactly `2 × line height`, with no extra padding in the difference.

---

## MT-4 — a crosstab span cell is not padded per spanned row

**Why it matters.** A span cell's height is the sum of the rows it covers. If the padding is added
again on top of an already-padded sum, span cells grow out of alignment with their neighbours.

1. Build a crosstab with a row dimension producing a **3-row span** cell. Modernize it.
2. Measure the span cell, and measure three ordinary data rows beside it.

**Pass:** span cell = **84px** = 3 × 28px, and it aligns exactly with the three rows beside it.
**Fail:** 96px (84 + 12) — the span got a fourth padding. Visible as the span cell overhanging its
neighbours.

3. Repeat in a PDF export.

Also confirm here that the crosstab's **span and placeholder cells** carry the same text inset as
their neighbours — 6px top/bottom, 8px left/right. Those cells render through `vs-simple-cell`,
which had no padding binding at all before this branch.

---

## MT-5 — print layout

**Why it matters.** Print layout reaches none of the sites the rest of the branch updated. Before
this change a marked table printed with no cell padding, and after the row rebalance it would have
printed 16px rows against 28px everywhere else.

1. Open **Print Layout**, drag the marked table in, export to PDF.

**Expected at comfortable:**

| | before | expected now | browser |
|---|---|---|---|
| header row | 18px | **30px** | 30px |
| data row | 16px | **28px** | 28px |
| text inset, top/bottom | 0 | **6px** | 6px |
| text inset, left/right | 1px | **8px** | 8px |
| column widths | X | **X, unchanged** | X |

A 1-header + 10-data-row table occupies `30 + 10×28 = 310px` of page height (compact 266, dense
222).

2. **Descenders:** no `g`, `p` or `y` clipped at the bottom of a cell; no cap clipped at the top.
3. **Columns:** overlay against a pre-change PDF. Every column boundary lands on the same x — only
   row pitch and text inset change. Column widths becoming wider is the live/export mismatch this
   branch is built to avoid.
4. **Unmarked regression:** repeat on a dashboard that is *not* modernized, with no `format.css`.
   That PDF must be pixel-identical to the pre-change build.

---

## MT-6 — the Cell Padding dialog

**Why it matters.** The pane is new, and it is the only way an author reaches this value.

1. Open the marked table's properties. **Both** a Padding and a Cell Padding group should show,
   each with a "follow default density" checkbox, checked, and its four steppers disabled.

   *(On slice A only the Cell Padding group exists; the Padding group arrives with slice B.)*

2. Uncheck it, set **10** on all four edges, OK. → cell text inset becomes 10px on every edge, and
   data rows become `16 + 20 = 36px`.
3. Reopen. → the checkbox is still unchecked and the values still read 10.
4. Re-check it, OK. → inset returns to 6/8/6/8 and rows to 28px.
5. Change the dashboard density to **compact**. → inset follows to 4/6/4/6, rows to 24px.
6. Repeat 2 and 5 in either order: with an author value set, a density change must **not** move it.
7. On an **unmarked** table: open properties, change only the title, OK, reopen. The Cell Padding
   checkbox must still be absent and the table must still follow density if later modernized.
   *(Step 7 is the regression guard for a defect where any dialog OK silently pinned the value.)*
