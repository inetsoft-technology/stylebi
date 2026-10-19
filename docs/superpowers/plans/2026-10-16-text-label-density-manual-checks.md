# Text label density height — manual checks

Server built from `feature-text-label-density` at `8f265cc2d7` (rebased onto #6798 at `ae00b34ca6`), run 2026-10-19. One dashboard per tier (dense, compact, comfortable), plus a legacy (unmarked) copy.

Checks marked *scripted* were run with `tld-checks.js`, which drives the composer over STOMP and reads the Text dialog and the exports over REST, on fresh dashboards. The others were checked by hand.

| # | Check | Expected | Result |
|---|---|---|---|
| MT-1 | Drop a Text on each tier's dashboard and on the legacy copy | 100×24 / 100×28 / 100×30; legacy 100×20 | Pass (scripted): 100×24 / 28 / 30; legacy 100×20 with no Follow checkbox |
| MT-2 | Put a Text beside a Text Input at compact | Both 28 tall; the label and the input's text on one line | Pass (by hand), composer and viewer |
| MT-3 | Widen a new text, then change the dashboard density (Options → Visualization Density) | The height follows the tier; the width is kept | Pass (scripted): 250×28 still following; 250×30 at comfortable, 250×24 at dense |
| MT-4 | Drag a text taller, then change the density | The dragged height is kept | Pass (scripted): Follow unticked; 100×40 kept through two density changes |
| MT-5 | Text properties → Size & Position | Follow default density shown and ticked; Width editable; Height locked | Pass (scripted): ticked and height-only |
| MT-6 | Untick it, change the density | The height is kept. Tick it again: back to the tier height, width kept | Pass (scripted): 180×28 kept unticked; ticked again at dense, 180×24 |
| MT-7 | Turn on Auto Size | No Follow checkbox; its height follows its content, never a tier height | Pass (scripted): no checkbox; content height 20 kept through density changes |
| MT-7b | Tick Auto Size in the same save as a ticked Follow | Nothing resized; Auto Size wins | Pass (scripted): 100×44 kept |
| MT-8 | Modernize the legacy copy | Texts stay 20; the checkbox is shown unticked. Ticking it moves the text to the tier height | Pass (scripted): 100×20 unticked after Modernize and a density change; ticked, 100×28 at compact |
| MT-9 | Revert a modern dashboard | Texts keep 24 / 28 / 30 | Pass (scripted): 100×28 kept, no checkbox |
| MT-10 | Print layout: add a Text | 20 tall | Pass (by hand) |
| MT-11 | A text in a bottom-tabs container, then change the density | Its bottom stays on the tab strip | Pass (by hand) |
| MT-12 | Export PNG, PDF and HTML at each tier | Text boxes at the stored heights; nothing clipped | Pass: HTML text heights 24 / 28 / 30 / 20 (scripted); PDF and PNG reviewed by eye |
| MT-13* | A legacy text sized to 28 by hand, then Modernize and a density change | It stays 28 | Pass (scripted) |

Notes:

- An Auto Size text is refitted to its content height on every full refresh (20 for one line, 26 for two), including an Options save that changes no density. A plain text on the same dashboard keeps its height. This is Auto Size's own behaviour, which D3 relies on, not density.
- MT-13* replaces the accepted-cost check in spec §4. That check needs a text saved at exactly 24 / 28 / 30 before this branch. On this build a hand resize records the author's height, so the state cannot be created, and the check confirms the new behaviour instead.
