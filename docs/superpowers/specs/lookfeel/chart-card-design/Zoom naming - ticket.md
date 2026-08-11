# Rename "Zoom Chart" and unify "Clear Zoom"

Small, self-contained. **Not part of the anchored-toolbar pilot** — no dependency either way, and it
should not be folded into §02. Found while writing the chart card spec; recorded there in §02's
"Three controls say zoom" card.

Branch read: `inetsoft-technology/stylebi`, branch `epic-74519`.

## The problem

Three viewer-reachable controls use zoom language or the zoom metaphor, and they do unrelated things.

| Control | Where | What it does |
|---|---|---|
| Zoom In / Zoom Out | `chart-nav-bar`, floating over the plot | **Viewport** — magnifies what you are looking at |
| Zoom Chart (`chart zoom`, `zoom-in-icon`) | toolbar | **Data scope** — zooms to the selected points |
| Resize Plot (`chart resize-plot`) | menu only | **Plot geometry** |

`chart zoom` is gated by `brushable` and sits beside Brush and Exclude Data in
`createToolbarActions` — it is a data operation, not a viewport one. But it carries `zoom-in-icon`,
the same magnifier metaphor as the nav bar's viewport control, in a strip that the chart card spec
anchors into the title lane. A viewer reads the glyph before the tooltip.

Second, smaller problem: the same action id has two labels.
`chart clear-zoom` is `"_#(js:Clear Zoom)"` in `createToolbarActions` and
`"_#(js:View All Data)"` in `createMenuActions`.

## What to change

1. **Rename `chart zoom` to "Zoom to Selection."** Literally accurate — `brushable` requires a
   selection to exist — and it keeps the zoom family intact with its clear action.
2. **Swap `zoom-in-icon` for a selection-scoped glyph** (marquee, crop). Without this the rename only
   half-works: the icon collision survives the label change.
3. **Standardise `chart clear-zoom` on "View All Data"** in both toolbar and menu. It is already the
   menu label, so half the product says it; and it names the state you arrive at rather than the
   mechanism you are undoing. That matters because a viewer can inherit a zoomed chart without having
   zoomed it — "Clear Zoom" assumes they know what zoom did.

## Costs

- **i18n.** `_#(js:Zoom Chart)` and `_#(js:Clear Zoom)` are translation keys; a rename means work in
  every locale.
- **Tests.** `chart-actions.spec.ts` asserts these label strings directly (e.g. the
  `"Zoom Chart"` entry in its expected menu lists) — update in the same commit.

## Files

- `web/projects/portal/src/app/vsobjects/action/chart-actions.ts` — labels and icon
- `web/projects/portal/src/app/vsobjects/action/chart-actions.spec.ts` — asserted label strings
- locale bundles carrying the two keys
