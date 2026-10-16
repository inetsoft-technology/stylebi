# Text label density height — manual checks

Server built from `feature-text-label-density`. One dashboard per tier (dense, compact, comfortable), plus a legacy (unmarked) copy.

| # | Check | Expected | Result |
|---|---|---|---|
| MT-1 | Drop a Text on each tier's dashboard and on the legacy copy | 100×24 / 100×28 / 100×30; legacy 100×20 |  |
| MT-2 | Put a Text beside a Text Input at compact | Both 28 tall; the label and the input's text on one line |  |
| MT-3 | Widen a new text, then change the dashboard density (Properties → density) | The height follows the tier; the width is kept |  |
| MT-4 | Drag a text taller, then change the density | The dragged height is kept |  |
| MT-5 | Text properties → Size & Position | Follow default density shown and ticked; Width editable; Height locked |  |
| MT-6 | Untick it, change the density | The height is kept. Tick it again: back to the tier height, width kept |  |
| MT-7 | Turn on Auto Size | No Follow checkbox; a density change leaves the text alone |  |
| MT-8 | Modernize the legacy copy | Texts stay 20; the checkbox is shown unticked |  |
| MT-9 | Revert a modern dashboard | Texts keep 24 / 28 / 30 |  |
| MT-10 | Print layout: add a Text | 20 tall |  |
| MT-11 | A text in a bottom-tabs container, then change the density | Its bottom stays on the tab strip |  |
| MT-12 | Export PNG, PDF and HTML at each tier | Text boxes at the stored heights; nothing clipped |  |
