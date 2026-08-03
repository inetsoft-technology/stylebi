# StyleBI Visualization — Phase 9C Item 6: Tabular Numerals — WITHDRAWN

> Status: **WITHDRAWN (2026-07-31) — non-issue, and not fixable via CSS in this font landscape.**
> Phase 5 item 8 recorded this as *"needs a font-feature capability and touches
> measuring/wrapping/export (font risk). Own spike before scheduling."* This document is that spike. A
> gated CSS rule was implemented, tested in the running product, measured to have **no effect**, and
> reverted. No code ships. Same disposition as [Phase 9C item 2](visualization-phase9c-deferred-consolidation-implementation-plan.md).

Origin: [Phase 5](visualization-phase5-implementation-plan.md) item 8 → collected into
[Phase 9C](visualization-phase9c-deferred-consolidation-implementation-plan.md) item 6.

## Why it is withdrawn

Three findings, in the order they were established. The third is decisive and was found only by testing
in the running product.

### 1. The shipped default font already has tabular figures — nothing to fix at the default

The viewsheet cell font arrives from the server as a CSS shorthand ending
`Roboto, roboto, arial, helvetica, sans-serif` (`VSCSSUtil.getFont`, `VSCSSUtil.java:222`), family from
`default.font.family`, default `Roboto` (`StyleFont.java:681-683`). Portal loads Roboto as a real webfont
(`web/projects/portal/src/global.scss:20` → `roboto-fontface`), and the docker image installs the same
family for server render (`docker/src/main/docker/Dockerfile:32`, `fonts-roboto`), so live and export
agree.

Parsing `Roboto-Regular.woff`: **all ten digits carry an identical advance of 1150/2048 em.** Roboto's
default figure set is tabular lining. So at the shipped default there was never a defect to fix.

### 2. Java2D cannot express `tnum` — the server/export half is a boundary

`java.awt.font.TextAttribute` exposes no OpenType feature passthrough. Its shaping attributes are
KERNING, LIGATURES and TRACKING; `NUMERIC_SHAPING` is Arabic/Indic digit *shaping*, not advance width. A
grep for `font-variant-numeric`, `tnum` and `font-feature` over `core/src/main/java` is clean.

The only Java route is per-glyph advance normalization in `Common.paintText` with a matching
`Common.getWidth`, sitting under every measuring, wrapping and pagination path plus all six exporters —
the Phase 3 D3 font-risk. **Tabular figures in export are a property of the configured font, not a
capability StyleBI can add.** This stands as a declared boundary regardless of the rest of this document,
alongside the native `<select>` and theme-image boundaries.

### 3. `font-variant-numeric: tabular-nums` is a measured no-op on every font tested

This is the finding that withdraws the item. **A font listing a `tnum` feature does not mean `tnum`
affects its digits.** What matters is whether the `tnum` lookup's *coverage table* includes the glyphs the
`cmap` maps digits to.

In every proportional-figure font tested it does not. Georgia is representative: its default digits are
the old-style set (gids 19-28), while its `tnum` lookup is a single substitution covering **18 glyphs,
none of them digits** — it targets the *lining* digits (gids 615-624) instead. So the browser requests
`tnum`, the font has nothing to substitute, and nothing changes.

Survey of 13 fonts (12 Windows system fonts plus the bundled Roboto; Cambria is a `.ttc` collection and
was not parsed):

| Fonts | Default digits | `tabular-nums` effect |
|---|---|---|
| Roboto (shipped default), Arial, Calibri, Verdana, Times New Roman, Segoe UI, Tahoma, Palatino Linotype, Sylfaen | already tabular | no-op — nothing to fix |
| **Georgia, Constantia, Corbel, Candara** | proportional old-style | **no-op — `tnum` coverage excludes the default digits** |

Georgia's default digit advances, per 2048 em: `1257, 880, 1144, 1130, 1157, 1082, 1159, 1029, 1221,
1159` — a 377-unit spread between `1` and `0`, roughly 18% of an em.

**Confirmed empirically in the product, not only by font parsing.** The gated rule was implemented, the
frontend rebuilt, and `default.font.family` set to `Georgia`. DevTools confirmed the rule matching
`div.table-cell-content` (`global.css:109`, not overridden). Rendered pixel widths of 17 values sharing
the identical glyph pattern `NNN,NNN.NN` measured **87-96 px** — versus **89-97 px** with the rule absent.
Unchanged within measurement noise. The rule matched and did nothing.

## `lining-nums` was investigated and does not rescue it

Since `tnum` only covers the lining digits, chaining `lining-nums tabular-nums` should reach them. Tested:

| Font | after `lnum` | after `lnum` + `tnum` | uniform? |
|---|---|---|---|
| Georgia | `1194, 868, 1137, 1130, 1157, 1081, 1159, 1106, 1221, 1159` | `1153` ×9, but `7` = `1194` | **nearly** — `7` is ~2% wide |
| Constantia | — | `1103, 641, 992, 935, 1087, 974, 1108, 990, 1099, 1119` | no — fully proportional |
| Corbel | — | `1052, 918, 1046, 928, 1058, 983, 1074, 876, 1054, 1074` | no — fully proportional |
| Candara | — | `1125, 716, 946, 995, 1092, 1006, 1130, 968, 1127, 1123` | no — fully proportional |

So `lining-nums tabular-nums` fixes exactly one font, imperfectly, and is also a more opinionated
override — forcing lining figures discards a deliberate typographic choice, which is more than the design
spec's "use tabular numerals" asks for. Not pursued.

## Residual value, unverified

Modern webfonts are the case where this could still matter: some are proportional by default *and* carry
a `tnum` whose coverage does include the default digits. Inter is the likely example, and Inter already
leads the shell font stack (`_variables.scss:26`) though it is not bundled.

This was **not verified** — Inter is not installed on the machine used for this spike. If a customer ever
themes `--inet-font-family` to such a font and reports misaligned numeric columns, revisit this with a
coverage measurement of that specific font first. Do not assume a listed `tnum` tag is functional; that
assumption is what made this spike reach a wrong conclusion mid-flight.

## What was reverted

`web/projects/portal/src/scss/_themeable.scss` — a gated `.viz-modern` rule setting
`font-variant-numeric: tabular-nums` on `.table-cell-content`, `.table-data__cell`, `.ws-table-headers`
and `.preview-table`. Reverted in full; the file is byte-identical to its prior state. No token was ever
added.

Deliberately **not** added, and still correct not to add: a `--inet-viz-numeric-figures` token (nothing
themable to express), any Java change, and any `lining-nums` variant.

## Consistency note

Numeric **right-alignment** — the part of the Phase 5 task that carries the real scanability value — was
already shipped via `FormatTableLens2` and is unaffected by this withdrawal. Roadmap line 481 ("define
tabular numeral usage and numeric alignment rules") is satisfied by that plus the rules recorded here: use
a tabular-figure font, which the shipped default already is; do not attempt to force it in CSS.

## Related

- [visualization-phase9c-deferred-consolidation-implementation-plan.md](visualization-phase9c-deferred-consolidation-implementation-plan.md) — item 6
- [visualization-phase5-implementation-plan.md](visualization-phase5-implementation-plan.md) — origin (item 8, Part C boundary)
- [visualization-implementation-roadmap.md](visualization-implementation-roadmap.md)
- [visualization-design-spec.md](visualization-design-spec.md) — "use tabular numerals" under Tables And Grids
