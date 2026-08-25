# Manual Tests — palette revert, after the five bug-fix rounds

**Status: RUN AND PASSED, confirmed by a human partner on 2026-08-25**, together with P6's own ten manual
checks and the P0 pre-mark-cohort confirmation. This file is kept as the record of what was covered, and as
the regression script to re-run if any of the colour or chrome seeding paths is touched again. The
"Already confirmed" list below was written mid-pass and is narrower than what finally ran.

**Why this file lives here now.** It was written at the top level of the enterprise working tree, untracked,
alongside twenty-odd other scratch files — the only record of what had been verified on this branch, one
`git clean` away from gone. Moved into the plans directory beside the two plans it verifies
(`2026-08-24-chart-palette-revert.md` and `2026-08-24-palette-revert-followups.md`) and committed with the
card radius.


Every colour is read from source. Supersedes the earlier guide: the change set grew a lot after that
was written, and the riskiest tests are now the ones guarding against *over*-clearing.

## Reference values

**Modern head:** `#00D4E8` `#00B87A` `#F59E0B` `#F43F5E` `#8B5CF6` `#3B82F6` `#0D9488` `#64748B`
**Modern dark:** `#22D3EE` `#10B981` `#FBB724` `#FB6181` `#A78BFA` `#60A5FA` `#2DD4BF` `#94A3B8`
**Legacy head:** `#518DB9` `#B9DBF4` `#62A640` `#ADE095` `#FC8F2A` `#FDE3A7` `#D64541` `#FDA7A5`

Indices 8–39 are identical in both palettes — **only the first 8 series distinguish modern from
legacy.** Bind a colour dimension with 4+ members.

**Axis labels:** modern `#6A685F`, modern dark `#CAC4D0`, legacy `#4B4B4B`.

## Already confirmed

- Palette reverts with the assembly, surviving save/close/reopen
- Same composer session shows legacy immediately on Revert, and modern on Modernize
- Preview and PDF/PNG export agree with the screen
- Saved asset carries no modern value and no `vizMark` after Revert (verified by export)

---

## Group A — the fix must not destroy an author's colours

**Highest risk in the whole change set.** Revert now deletes per-value colours the *render* derived.
The code tells those apart from ones you chose by a provenance mark. If that mark is ever wrong in
the direction of "yours looks derived", your colour is destroyed with no undo beyond the single
undo step. Every test here is checking for data loss.

### A1 — a hand-picked series colour survives Revert

1. Gate on, Modernized chart. In the Colour pane set series 1 to pure red `#FF0000`. Apply, save.
2. **Revert**, save, close, reopen.
3. **Expect:** series 1 **still red**. Series 2–4 legacy `#B9DBF4`, `#62A640`, `#ADE095`.

### A2 — a Colour Mapping assignment survives Revert

The **Color Mapping** dialog assigns a colour to a dimension *value* — the same storage the render
derives into, which is exactly why this needs its own test.

1. Gate on, Modernized chart, **Share Colors** checked. Open Color Mapping, map one value
   (e.g. `Business`) to pure red. Apply, save.
2. **Expect:** that value renders red, others modern.
3. **Revert**, save, close, reopen.
4. **Expect:** `Business` **still red**; every other value legacy.

If `Business` comes back legacy here, stop — that is the provenance mark misfiring, and it is
data loss.

### A3 — a script-set colour survives Revert

Script-set colours carry their own mark and must be exempt.

1. On a Modernized chart, add a chart script setting a colour for one value, e.g.
   `graph.getLegendFrame("Category").setColor("Business", java.awt.Color.RED);`
   (adapt to your script conventions). Apply, save.
2. **Revert**, save, close, reopen.
3. **Expect:** the scripted colour still applies.

### A4 — Revert twice, and Modernize→Revert→Modernize

1. Revert an already-reverted dashboard. **Expect:** no change, no error, colours stay legacy.
2. Modernize → Revert → Modernize in one session. **Expect:** modern → legacy → modern, each
   immediately, no reopen. Colours must not drift or shuffle between values across the cycles.

---

## Group B — Share Colors and cross-chart consistency

Revert now clears two sheet-level runtime colour caches. These tests check that clearing them
doesn't break the feature those caches exist for.

### B1 — two charts sharing a dimension agree

1. Same dashboard, two charts both colour-bound to the same dimension, **Share Colors** on both.
2. **Expect:** the same value is the same colour in both charts.
3. Modernize. **Expect:** both modern, still agreeing value-for-value.
4. **Revert.** **Expect:** both legacy, still agreeing value-for-value.
5. Save, close, reopen. **Expect:** unchanged, and still agreeing.

Step 4 is the one that matters: the cache that keeps the two charts in step is cleared by Revert
and rebuilt on the next render.

### B2 — Share Colors off

1. Uncheck **Share Colors** on one chart, Modernize, then Revert.
2. **Expect:** it reverts to legacy independently; the other chart is unaffected.

### B3 — colour assignment is stable across a reopen

1. Note which colour each value has on a legacy chart with 5+ values.
2. Close and reopen the dashboard.
3. **Expect:** identical value→colour assignment. (Per-value colours are no longer frozen into the
   asset, so this is now recomputed each session — it must land the same way every time.)

---

## Group C — the ordinary Apply path

The Colour pane's Apply no longer pins the palette into the user tier.

### C1 — an untouched Apply pins nothing

1. Gate on, Modernized chart. Open the Colour pane, **change nothing**, press **Apply**.
2. **Revert**, save, close, reopen. **Expect:** fully legacy.
3. Repeat for: toggling **Share Colors**, opening and closing **Color Mapping** without changes,
   dragging a field in and out.

### C2 — Reset

1. Modernized chart, set series 1 to red, Apply.
2. Press **Reset**. **Expect:** series 1 returns to `#00D4E8`, all swatches modern, and the Reset
   button goes **disabled**.
3. Apply, Revert, save, reopen. **Expect:** legacy, your red gone (you reset it).

---

## Group D — axis labels

### D1 — labels revert

1. Modernized chart with a **measure on Y**. Confirm label colour `#6A685F`.
2. Revert, save, reopen. **Expect:** `#4B4B4B`. **Fonts unchanged throughout.**

### D2 — an explicit axis format wins

1. Chart Properties → Axis → set an explicit label colour on the measure axis.
2. Modernize, then Revert. **Expect:** your colour survives both.

---

## Group E — gate-off regression

Nothing here should differ from before this work, with one accepted exception.

### E1 — a legacy dashboard under a closed gate

1. Gate **off**. Open a dashboard saved from a gate-off session.
2. **Expect:** legacy palette, `#4B4B4B` axis labels, square corners — indistinguishable from before.

**Accepted, deliberate:** where a measure-axis per-column format was *not* cloned from the axis-wide
one (created by a CSS rule naming that column, or persisted by an older build), its labels may now
pick up the chart-level format foreground or a CSS axis rule they previously ignored. Consistent
with how every other axis has always behaved. Report anything beyond that.

### E2 — a modern dashboard under a closed gate

1. Gate off, open a dashboard whose assemblies are marked modern.
2. **Expect:** it stays modern — the mark, not the gate, decides. Corners, palette and labels all
   modern.

### E3 — a mixed dashboard

1. A dashboard with one marked and one unmarked chart.
2. **Expect:** each renders by its own mark, under either gate state.

---

## Group F — dark mode

### F1 — dark palette and revert

1. Gate on + **Dark Mode** on. Modernize.
2. **Expect:** `#22D3EE`, `#10B981`, `#FBB724`, `#FB6181`; axis labels `#CAC4D0`.
3. Revert, save, reopen. **Expect:** legacy `#518DB9` and `#4B4B4B` — dark mode does not change what
   legacy means.

---

## Group G — export and non-viewer paths

### G1 — export agreement

For one Modernized and one Reverted chart, export **PDF**, **PNG** and **Excel**.
**Expect:** palette and axis-label colour match the screen in every format.

### G2 — scheduled export / emailed report

If you use scheduled tasks: run one against a Reverted dashboard.
**Expect:** legacy colours. This renders on a different thread from a different entry point, which is
worth one check given how much of this bug was thread- and session-scoped state.

### G3 — viewer

Open a Reverted dashboard in the viewer. **Expect:** legacy, matching the composer.

---

## Known-open — do not report as new

Recorded in `docs/superpowers/specs/2026-08-14-seed-mark-forward-half-design.md`:

1. **Target-line band fill** (Chart Properties → Target Lines) pins all colour tiers on every apply.
   Separate code path, pre-existing.
2. **Multi-styles toggle:** Modernize with Separate/multi-style on → turn it off → Revert → turn it
   back on can leave stale modern colours on the per-measure frames.
3. **`applyGlobalColors` laundering:** a derived colour arriving back through the sheet-level map is
   written without its provenance mark. Narrower now that the map is cleared on Revert, but the path
   exists.
4. **The value axis's runtime clone** (`VGraphPair:1338-1341`) is intentionally still gated.
