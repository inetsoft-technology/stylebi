# defaults.css replacement

```css
/* StyleBI chart palettes — proposed replacement
 * Replaces 11 ChartPalette names with 6.
 * Selector form follows the existing defaults.css convention; confirm the
 * index base (0- vs 1-) against the file before applying.
 *
 * core/src/main/resources/inetsoft/util/css/defaults.css
 */

/* ---- Default · 8 — the default categorical set ----------------------------
 * 0:Azure  1:Coral  2:Ink  3:Teal  4:Violet  5:Amber  6:Magenta  7:Acid
 * Slot 3 (Ink) is the near-black anchor. Order is deliberate: a 1- or 2-series
 * chart opens on Azure/Coral, and the anchor arrives at 3 where it has a job.
 */
ChartPalette[name=Default][index=0] { color: #0490FF; }
ChartPalette[name=Default][index=1] { color: #FF5A35; }
ChartPalette[name=Default][index=2] { color: #241C4F; }
ChartPalette[name=Default][index=3] { color: #03D9B3; }
ChartPalette[name=Default][index=4] { color: #9A2DDC; }
ChartPalette[name=Default][index=5] { color: #FFB020; }
ChartPalette[name=Default][index=6] { color: #E5197E; }
ChartPalette[name=Default][index=7] { color: #8ED604; }

/* ---- Default-soft · 8 — companions -----------------------------------------
 * NOT a user-selectable palette. Must be filtered out of getPaletteNames()
 * and reached only through getCompanionPalette(name). See ENGINE.md §2.
 * These are the derivation rule's own output; authoring them makes the
 * built-in set exact and independent of floating-point drift.
 */
ChartPalette[name=Default-soft][index=0] { color: #97BEEB; }
ChartPalette[name=Default-soft][index=1] { color: #F6B2A2; }
ChartPalette[name=Default-soft][index=2] { color: #CCCCE9; }
ChartPalette[name=Default-soft][index=3] { color: #BFF6E5; }
ChartPalette[name=Default-soft][index=4] { color: #AD8BCB; }
ChartPalette[name=Default-soft][index=5] { color: #FFE7C7; }
ChartPalette[name=Default-soft][index=6] { color: #DE93AB; }
ChartPalette[name=Default-soft][index=7] { color: #D8F7BA; }

/* ---- Default, dark theme ---------------------------------------------------
 * Not the light set inverted: bases lift, companions deepen.
 * Slot 3 inverts — the anchor is already darkest, so its companion lifts.
 */
ChartPalette[name=Default-dark][index=0] { color: #4FA5FF; }
ChartPalette[name=Default-dark][index=1] { color: #FF8367; }
ChartPalette[name=Default-dark][index=2] { color: #49447D; }
ChartPalette[name=Default-dark][index=3] { color: #2DEEC6; }
ChartPalette[name=Default-dark][index=4] { color: #AE41F5; }
ChartPalette[name=Default-dark][index=5] { color: #FFCB82; }
ChartPalette[name=Default-dark][index=6] { color: #FE3290; }
ChartPalette[name=Default-dark][index=7] { color: #9FEB28; }
ChartPalette[name=Default-dark-soft][index=0] { color: #00569C; }
ChartPalette[name=Default-dark-soft][index=1] { color: #A62E11; }
ChartPalette[name=Default-dark-soft][index=2] { color: #8988AB; }
ChartPalette[name=Default-dark-soft][index=3] { color: #009378; }
ChartPalette[name=Default-dark-soft][index=4] { color: #550080; }
ChartPalette[name=Default-dark-soft][index=5] { color: #AB792A; }
ChartPalette[name=Default-dark-soft][index=6] { color: #870047; }
ChartPalette[name=Default-dark-soft][index=7] { color: #5F9100; }

/* ---- Soft · 8 — low-chroma categorical ------------------------------------
 * For dashboards with many small charts. Deliberately even in lightness,
 * which is why it collapses under desaturation — that is Contrast's job.
 */
ChartPalette[name=Soft][index=0] { color: #6E93B8; }
ChartPalette[name=Soft][index=1] { color: #7FB08A; }
ChartPalette[name=Soft][index=2] { color: #D08A7A; }
ChartPalette[name=Soft][index=3] { color: #B49AC8; }
ChartPalette[name=Soft][index=4] { color: #C9A96A; }
ChartPalette[name=Soft][index=5] { color: #6FA6A2; }
ChartPalette[name=Soft][index=6] { color: #A98C7A; }
ChartPalette[name=Soft][index=7] { color: #8A8F99; }

/* ---- Contrast · 8 — projection, print, low vision --------------------------
 * 0:Navy  1:Orange  2:Orchid  3:Yellow  4:Brick  5:Sky  6:Teal  7:Green
 * Every member on its own rung of a luminance ladder, no two closer than
 * 0.05 relative luminance across a 0.05–0.66 span. Slot order interleaves the
 * ladder so a 2–3 series chart draws from opposite ends.
 * Deliberately has NO dark variant — its contract is paper and projection.
 */
ChartPalette[name=Contrast][index=0] { color: #0B3D91; }
ChartPalette[name=Contrast][index=1] { color: #F5943F; }
ChartPalette[name=Contrast][index=2] { color: #B04AB8; }
ChartPalette[name=Contrast][index=3] { color: #F7D22E; }
ChartPalette[name=Contrast][index=4] { color: #A8330A; }
ChartPalette[name=Contrast][index=5] { color: #7CC4EE; }
ChartPalette[name=Contrast][index=6] { color: #00947F; }
ChartPalette[name=Contrast][index=7] { color: #5FA83C; }

/* ---- Sequential and diverging ramps ----------------------------------------
 * These are NOT categorical. They belong to the LinearColorFrame path and
 * must not appear in the categorical picker. Ship as AbstractSplineColorFrame
 * stops, not as generated gradients. See ENGINE.md §4.
 */
ChartPalette[name=Amber][index=0] { color: #FCEFE0; }
ChartPalette[name=Amber][index=1] { color: #F7DDBB; }
ChartPalette[name=Amber][index=2] { color: #EFBE87; }
ChartPalette[name=Amber][index=3] { color: #DE9A4A; }
ChartPalette[name=Amber][index=4] { color: #C1731A; }
ChartPalette[name=Amber][index=5] { color: #96520B; }
ChartPalette[name=Amber][index=6] { color: #5E3204; }
ChartPalette[name=Teal][index=0] { color: #E2F4F2; }
ChartPalette[name=Teal][index=1] { color: #BCE6E1; }
ChartPalette[name=Teal][index=2] { color: #7FCEC7; }
ChartPalette[name=Teal][index=3] { color: #35AFA6; }
ChartPalette[name=Teal][index=4] { color: #1D8A86; }
ChartPalette[name=Teal][index=5] { color: #12665F; }
ChartPalette[name=Teal][index=6] { color: #0A3F3B; }

/* Variance — diverging. Midpoint is the canvas neutral: at target reads as
 * absence. Not red/green. */
ChartPalette[name=Variance][index=0] { color: #12665F; }
ChartPalette[name=Variance][index=1] { color: #35AFA6; }
ChartPalette[name=Variance][index=2] { color: #A8D8D3; }
ChartPalette[name=Variance][index=3] { color: #EFEDE7; }
ChartPalette[name=Variance][index=4] { color: #F2C89A; }
ChartPalette[name=Variance][index=5] { color: #DE9A4A; }
ChartPalette[name=Variance][index=6] { color: #96520B; }
```
