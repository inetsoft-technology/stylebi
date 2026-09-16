# Provenance

The external design handoff for the chart palette redesign, committed 2026-09-15 so it stops
being lost.

It was never in the repo. It was recovered from `chart palette redesign.zip` on 2026-09-14 into a
session-scoped scratchpad, and had come close to being lost twice by the time it was committed.
The companion design records the first recovery; this folder is the fix.

## What is here

| File | What it is |
|---|---|
| `ENGINE.md` | The seven engine sections, §0 through §6. The authoritative text for each slice. |
| `CSS.md` | Palette hexes and the CSS-side changes. |
| `README.md` | The handoff's own overview. |
| `github.md` | The source-reading notes the handoff was written against, with its last sync date. |
| `dc_text.txt` | Text extraction of the design canvas, section by section. |

## Two files were deliberately not committed

`SBI Color and Type Pairings.dc.html` (238 KB) and its `support.js` (68 KB) — a generated design
canvas and its runtime. `dc_text.txt` is the readable extraction of that canvas and carries its
content, which is why it is here instead.

This matters because the other documents cite the canvas by section. `ENGINE.md` and the companion
design reference §3b, §3i and §3j; read those in `dc_text.txt`, which is ordered the same way.

## Not to be placed in `chart-card-design3/`

That directory is regenerated wholesale on each sync, so anything written into it is destroyed.
This folder sits outside it for that reason, alongside the design documents that consume it.

## Slices

| § | Item | Design |
|---|---|---|
| 0, 1 | Companion foundation, target band | [`2026-09-14-chart-companion-colors-design.md`](../2026-09-14-chart-companion-colors-design.md) |
| 2 | Brushing | [`2026-09-15-brushing-companion-colors-design.md`](../2026-09-15-brushing-companion-colors-design.md) |
| 3–6 | Overflow, ramps, area fill, multi-level | deferred; triggers in the two designs above |

The re-tune those sit on is
[`2026-09-11-chart-palette-retune-design.md`](../2026-09-11-chart-palette-retune-design.md).
