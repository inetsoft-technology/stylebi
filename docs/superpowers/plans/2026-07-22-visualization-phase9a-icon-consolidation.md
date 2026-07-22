# Visualization Phase 9A — Icon Consolidation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Consolidate visualization affordance icons (sort, expand/collapse, drill, more/overflow) onto the shell's cleaner existing patterns, under the `.viz-modern` gate, and fix two icon bugs (Font Awesome refs, dead sort-glyph names) globally.

**Architecture:** Track A (consolidation) is implemented entirely as gated CSS `content`-overrides appended to the existing `.viz-modern` sort seam in `web/projects/portal/src/scss/_themeable.scss` — the target `<i>` glyphs already carry per-state classes, so no viewsheet template or TypeScript changes are needed and gate-off output is byte-identical. Track B (bug fixes) edits two Angular action classes with Vitest unit tests. A mechanism-agnostic rendered audit gallery (Task 2) validates the visual result, and an export-grounding check (Task 1) confirms the affordance icons are browser-DOM only.

**Tech Stack:** Angular 21 + TypeScript 5.9, SCSS (ineticons font, `_themeable.scss` gated scope), Vitest 4 (`npm run test:portal`), HTML for the audit gallery.

## Global Constraints

- New Track A CSS rules MUST be nested under the `.viz-modern` selector; with `.viz-modern` absent, every in-scope surface renders exactly today's glyph (byte-identical gate-off). Verified by a guard grep in Task 9.
- Never edit a shared `$*-icon` codepoint in `web/projects/portal/src/assets/ineticons/variables.scss` — it leaks into the shell and cannot be gated.
- The gate class is the existing browser body class `.viz-modern` (Phase 2). Do not introduce a new gate.
- Track A prefers CSS `content`-override over template/TS edits. Only Track B edits `.ts` files.
- ineticons glyphs render in the ineticons font; generic Unicode arrowheads (`\2303`/`\2304`/`\25B4`/`\25BE`) do NOT exist in that font, so any pseudo-element drawing them MUST reset `font-family` to the app font (`font-family: var(--inet-font-family, sans-serif) !important`).
- Repo rule: work on a feature branch, never `main`; commit frequently. Commit trailer: `Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>`.
- Run frontend commands from `web/`.

---

## File Structure

- `web/projects/portal/src/scss/_themeable.scss` — **modify** (append all Track A gated CSS after the existing sort seam at ~L1263-1270). One responsibility: gated `.viz-modern` icon overrides.
- `web/projects/portal/src/app/vsobjects/action/base-table-actions.ts` — **modify** (Track B: fix dead sort-glyph names in `getOrderIcon`).
- `web/projects/portal/src/app/vsobjects/action/base-table-actions.spec.ts` — **create or modify** (unit tests for `getOrderIcon`).
- `web/projects/portal/src/app/vsobjects/action/selection-list-actions.ts` — **modify** (Track B: replace `fa fa-*` icons with ineticons).
- `web/projects/portal/src/app/vsobjects/action/selection-list-actions.spec.ts` — **create or modify** (assert no `fa ` icons remain).
- `community/docs/superpowers/specs/lookfeel/visualization-phase9a-icon-audit-gallery.html` — **create** (Task 2 deliverable).
- `community/docs/superpowers/specs/lookfeel/visualization-phase9a-export-grounding.md` — **create** (Task 1 findings note).

---

## Reference: verified anchors (re-confirm line numbers before editing — they drift)

Shell sort pattern (the "clean" target), `portal/data/data-folder-browser/data-folder-list-view/data-folder-list-view.component.scss:43-92`:
```scss
.sort-indicator { position: relative; width: 10px; height: 12px; opacity: 0; }
.sort-indicator::before { content: "\2303"; top: -1px; }   /* ⌃ */
.sort-indicator::after  { content: "\2304"; bottom: -1px; } /* ⌄ */
/* &:hover → opacity .45; &.is-active → opacity 1; active arm shown, inactive arm dimmed to .22 */
```

Viz table sort `<i>`, `vsobjects/objects/table/vs-table.component.html:191-203`, and crosstab `vs-crosstab.component.html:191-195`: an `<i class="sort-button vs-header-cell-button-sort ...">` toggling state classes `sort-ascending-icon` / `sort-descending-icon` / `sort-value-ascending-icon` / `sort-value-descending-icon` / `sort-icon` (crosstab has all five; table has asc/desc/none).

Existing `.viz-modern` sort seam, `web/projects/portal/src/scss/_themeable.scss:1263-1270`:
```scss
.vs-header-cell-button-sort { background-color: var(--inet-toolbar-bg-color); }
.viz-modern .vs-header-cell-button-sort.sort-ascending-icon,
.viz-modern .vs-header-cell-button-sort.sort-descending-icon {
  color: var(--inet-viz-sorted-color) !important;
}
```

Expand/collapse (viz selection tree), `vsobjects/objects/selection/selection-list-cell.component.ts:463-469` `getTreeIconClass()` returns `"minus-box-outline-icon icon-size1"` (open) / `"plus-box-outline-icon icon-size1"` (closed). Shell/EM trees use chevrons `downward-icon`/`forward-icon`.

Drill inline (viz chart axis), `graph/objects/chart-axis-area.component.html:96-127`: `class="plus-box-outline-icon floating-icon"` (drill down) / `class="minus-box-outline-icon floating-icon"` (drill up). Zoom (`chart-nav-bar.component.html:23,26`) already uses `shape-plus-icon`/`shape-minus-icon`.

Track B — dead names, `vsobjects/action/base-table-actions.ts:88-108` `getOrderIcon()`:
```ts
case XConstants.SORT_ASC:        icon = "sort-asc-icon";       break; // dead → sort-ascending-icon
case XConstants.SORT_DESC:       icon = "sort-desc-icon";      break; // dead → sort-descending-icon
case XConstants.SORT_VALUE_ASC:  icon = "sort-val-asc-icon";   break; // dead → sort-value-ascending-icon
case XConstants.SORT_VALUE_DESC: icon = "sort-val-desc-icon";  break; // dead → sort-value-descending-icon
default:                         icon = "sort-icon";           break; // valid
```

Track B — Font Awesome, `vsobjects/action/selection-list-actions.ts:48-91`: `fa fa-sliders` (Properties), `fa fa-format` (Format), `fa fa-calculator` (Convert to Range Slider), `fa fa-trash` (Select All — also a wrong-glyph bug), `fa fa-trash` (Remove ×2).

Confirmed ineticons codepoints (exist in `variables.scss` + `_icons.scss` map): `sort-ascending-icon \ebf0`, `sort-descending-icon \ebf1`, `sort-value-ascending-icon \ebf2`, `sort-value-descending-icon \ebf3`, `sort-icon \ebf4`; `downward-icon \eabd` (= `chevron-down-icon`), `forward-icon \eb02` (= `chevron-right-icon`); `shape-plus-icon \ebcd`, `shape-minus-icon \ebb5`; `setting-icon \eb93`, `format-icon \eafa`, `range-slider-icon \eb5d`, `trash-icon \ec0e`, `column-select-icon \ea7e`.

---

## Task 1: Export grounding (spec Task 0)

Confirm the in-scope affordance icons are browser-DOM only and not baked into viewsheet export, so `.viz-modern` fully governs them.

**Files:**
- Create: `community/docs/superpowers/specs/lookfeel/visualization-phase9a-export-grounding.md`

**Interfaces:**
- Produces: a documented yes/no per affordance; if any icon is export-baked it is removed from Track A scope and flagged for a server-side pass.

- [ ] **Step 1: Search the exporter for sort/drill/toggle icon rendering**

Run:
```bash
cd E:/StyleBI/stylebi-enterprise/community
grep -rniE "sort-(ascending|descending|value|icon)|plus-box|minus-box|drill-.*-filter|sortInfo|SortType" core/src/main/java/inetsoft/report/io/viewsheet/ | grep -viE "test" | head -40
```
Expected: matches (if any) reference sort *data ordering*, not an icon glyph drawn into the exported image. Record what you find.

- [ ] **Step 2: Confirm the live sort glyph is a DOM `<i>`, not a server model field**

Run:
```bash
grep -rniE "sortButton|vs-header-cell-button-sort" web/projects/portal/src/app/vsobjects/objects/table/*.html
grep -rniE "sortIcon|orderIcon|sortGlyph" core/src/main/java/inetsoft/web/viewsheet/model/ | head
```
Expected: the header sort indicator is a DOM `<i>` in the template; no server model field carries a sort *glyph* (only sort *state/order*).

- [ ] **Step 3: Write the findings note**

Create `visualization-phase9a-export-grounding.md` with one short section per affordance (sort, expand/collapse, drill, more/overflow), each stating: rendered as browser DOM (`file:line`), NOT found in the exporter, therefore in-scope for the `.viz-modern` browser gate. If any affordance IS found in the exporter, state it explicitly and mark it **out of Track A** (server-side follow-up).

- [ ] **Step 4: Commit**

```bash
git add community/docs/superpowers/specs/lookfeel/visualization-phase9a-export-grounding.md
git commit -m "docs(viz-icons): Phase 9A export grounding — affordance icons are browser-DOM

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 2: Build the mechanism-agnostic audit gallery

A static HTML page that renders each in-scope affordance × state × surface side-by-side, so the shell vs. viz divergence (and the Track A result) is visible. This is the reviewable deliverable and the visual check for Tasks 5-8.

**Files:**
- Create: `community/docs/superpowers/specs/lookfeel/visualization-phase9a-icon-audit-gallery.html`

**Interfaces:**
- Consumes: ineticons font (embed via the app's font path) and the shell `.sort-indicator` CSS.
- Produces: a browser-openable gallery; used for visual verification in Tasks 5, 6, 7, 9.

- [ ] **Step 1: Scaffold the gallery HTML with the ineticons font**

Create the file with `@font-face` pointing at the built ineticons font, a light/dark toggle, and a grid. Include a `.viz-modern` container toggle so the same rows can be viewed gate-off vs gate-on once Track A lands.

```html
<!doctype html>
<html><head><meta charset="utf-8"><title>Phase 9A Icon Audit</title>
<style>
  @font-face { font-family: ineticons; src: url("../../../../web/projects/portal/src/assets/ineticons/fonts/ineticons.woff2") format("woff2"); }
  body { font: 14px/1.4 system-ui, sans-serif; margin: 24px; }
  table { border-collapse: collapse; } td, th { border: 1px solid #ccc; padding: 8px 12px; text-align: center; }
  .glyph { font-family: ineticons; font-size: 18px; }
  /* shell reference sort indicator */
  .sort-indicator { position: relative; display: inline-block; width: 10px; height: 12px; }
  .sort-indicator::before { content: "\2303"; position: absolute; left: 0; top: -1px; font-size: 9px; }
  .sort-indicator::after  { content: "\2304"; position: absolute; left: 0; bottom: -1px; font-size: 9px; }
</style></head><body>
<h1>Phase 9A — Icon Audit Gallery</h1>
</body></html>
```

- [ ] **Step 2: Add the SORT comparison table (all states, all surfaces)**

Add a table with rows = surfaces (shell data list `.sort-indicator`; viz table `<i class="glyph sort-ascending-icon">` etc.; viz crosstab incl. value-sort) and columns = states (none/asc/desc/val-asc/val-desc). Render each cell with the exact class/codepoint that surface uses today. Use the codepoints from the Reference section (e.g. `<span class="glyph">&#xebf0;</span>` for `sort-ascending-icon`).

- [ ] **Step 3: Add EXPAND/COLLAPSE, DRILL, and MORE tables**

- Expand/collapse: shell chevron (`&#xeabd;`/`&#xeb02;`) vs viz boxed (`&#xeb52;`/`&#xeb31;`).
- Drill: menu `drill-*-filter` (`&#xeabe;`/`&#xeac1;`) vs inline box `&#xeb52;`/`&#xeb31;` vs zoom `&#xebcd;`/`&#xebb5;`.
- More: viz vertical kebab (`menu-vertical-icon`) vs shell horizontal meatball (`menu-horizontal-icon`).

- [ ] **Step 4: Open it and confirm the divergences render as described**

Open the file in a browser (or use the `run`/`claude-in-chrome` skill). Confirm the sort row shows the shell double-arrowhead vs the viz heavy glyph, and the other three tables render. This is the baseline the Track A tasks will be compared against.

- [ ] **Step 5: Reconcile the dead-name discrepancy**

In the gallery, add a row rendering the four names from `base-table-actions.ts` (`sort-asc-icon`, `sort-desc-icon`, `sort-val-asc-icon`, `sort-val-desc-icon`) as ineticons classes. Confirm they render **nothing** (no glyph), proving they are dead. Note this in an HTML comment; it justifies Task 3.

- [ ] **Step 6: Commit**

```bash
git add community/docs/superpowers/specs/lookfeel/visualization-phase9a-icon-audit-gallery.html
git commit -m "docs(viz-icons): Phase 9A mechanism-agnostic icon audit gallery

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 3: Track B — fix dead sort-glyph names (global)

`base-table-actions.ts:getOrderIcon()` returns four glyph names absent from the ineticons map, so the table-order menu icon renders nothing. Fix to the real names. Ships globally (bug fix, not gated).

**Files:**
- Modify: `web/projects/portal/src/app/vsobjects/action/base-table-actions.ts:88-108`
- Test: `web/projects/portal/src/app/vsobjects/action/base-table-actions.spec.ts`

**Interfaces:**
- Produces: `getOrderIcon()` returns valid ineticons class names for all sort states.

- [ ] **Step 1: Write failing tests for `getOrderIcon`**

Add to `base-table-actions.spec.ts` (create the file if absent, mirroring the existing `crosstab-actions.spec.ts` setup that constructs the action class with mock model/services):

```ts
import { XConstants } from "../../common/util/xconstants";

describe("BaseTableActions.getOrderIcon", () => {
   function iconFor(sortValue: number): string {
      const actions = Object.create(BaseTableActions.prototype);
      actions.model = { sortInfo: { sortValue } };
      return actions.getOrderIcon();
   }

   it("returns valid ineticons names for every sort state", () => {
      expect(iconFor(XConstants.SORT_ASC)).toBe("sort-ascending-icon");
      expect(iconFor(XConstants.SORT_DESC)).toBe("sort-descending-icon");
      expect(iconFor(XConstants.SORT_VALUE_ASC)).toBe("sort-value-ascending-icon");
      expect(iconFor(XConstants.SORT_VALUE_DESC)).toBe("sort-value-descending-icon");
      expect(iconFor(0)).toBe("sort-icon");
   });
});
```

If `getOrderIcon()` is `private`, change it to `protected` so the prototype call compiles (it is only used internally, so widening to `protected` is safe).

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd web && npx vitest run projects/portal/src/app/vsobjects/action/base-table-actions.spec.ts`
Expected: FAIL — asc returns `"sort-asc-icon"`, not `"sort-ascending-icon"`.

- [ ] **Step 3: Fix the glyph names**

In `base-table-actions.ts:getOrderIcon()` replace the four dead names:
```ts
      case XConstants.SORT_ASC:        icon = "sort-ascending-icon";        break;
      case XConstants.SORT_DESC:       icon = "sort-descending-icon";       break;
      case XConstants.SORT_VALUE_ASC:  icon = "sort-value-ascending-icon";  break;
      case XConstants.SORT_VALUE_DESC: icon = "sort-value-descending-icon"; break;
      default:                         icon = "sort-icon";                  break;
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `cd web && npx vitest run projects/portal/src/app/vsobjects/action/base-table-actions.spec.ts`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add web/projects/portal/src/app/vsobjects/action/base-table-actions.ts web/projects/portal/src/app/vsobjects/action/base-table-actions.spec.ts
git commit -m "fix(viz-icons): use valid ineticons names in table order menu

getOrderIcon returned sort-asc-icon/sort-desc-icon/sort-val-asc-icon/
sort-val-desc-icon, none of which exist in the ineticons map, so the icon
rendered nothing. Use sort-ascending-icon/sort-descending-icon/
sort-value-ascending-icon/sort-value-descending-icon.

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 4: Track B — replace Font Awesome icons with ineticons (global)

`selection-list-actions.ts` mixes `fa fa-*` icons into an ineticons menu. Replace with ineticons glyphs. Also fixes the wrong-glyph bug where "Select All" uses `fa fa-trash`.

**Files:**
- Modify: `web/projects/portal/src/app/vsobjects/action/selection-list-actions.ts:48-91`
- Test: `web/projects/portal/src/app/vsobjects/action/selection-list-actions.spec.ts`

**Interfaces:**
- Produces: menu action `icon()` functions return ineticons class names; no `fa ` prefix remains in the file.

- [ ] **Step 1: Write a failing test that no Font Awesome icon remains**

Add to `selection-list-actions.spec.ts` (create if absent):
```ts
import * as fs from "fs";
import * as path from "path";

it("uses no Font Awesome icons (ineticons only)", () => {
   const src = fs.readFileSync(
      path.resolve(__dirname, "selection-list-actions.ts"), "utf8");
   expect(src).not.toMatch(/icon:\s*\(\)\s*=>\s*"fa /);
});
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd web && npx vitest run projects/portal/src/app/vsobjects/action/selection-list-actions.spec.ts`
Expected: FAIL — `fa fa-sliders` etc. still present.

- [ ] **Step 3: Replace each `fa fa-*` with the ineticons equivalent**

In `selection-list-actions.ts` map each icon:
```ts
// Properties...            fa fa-sliders     → setting-icon
icon: () => "setting-icon",
// Format...                fa fa-format      → format-icon
icon: () => "format-icon",
// Convert to Range Slider  fa fa-calculator  → range-slider-icon
icon: () => "range-slider-icon",
// Select All               fa fa-trash (bug) → column-select-icon
icon: () => "column-select-icon",
// Remove (×2)              fa fa-trash       → trash-icon
icon: () => "trash-icon",
```
Apply to all six occurrences in `:48-91` (and any further `fa fa-trash` Remove entries below `:91` — grep the whole file to be sure none remain).

- [ ] **Step 4: Run the test to verify it passes**

Run: `cd web && npx vitest run projects/portal/src/app/vsobjects/action/selection-list-actions.spec.ts`
Expected: PASS. Also run `grep -nE '"fa ' web/projects/portal/src/app/vsobjects/action/selection-list-actions.ts` → no output.

- [ ] **Step 5: Commit**

```bash
git add web/projects/portal/src/app/vsobjects/action/selection-list-actions.ts web/projects/portal/src/app/vsobjects/action/selection-list-actions.spec.ts
git commit -m "fix(viz-icons): replace Font Awesome icons with ineticons in selection menu

Properties/Format/Convert/Remove used fa fa-* glyphs (foreign font);
Select All wrongly used fa fa-trash. Map to setting-icon/format-icon/
range-slider-icon/column-select-icon/trash-icon.

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 5: Track A — gated sort indicator (table + crosstab)

Under `.viz-modern`, repaint the viz header sort `<i>` as the shell's double-arrowhead pattern for label sort (none/asc/desc), and a filled-triangle variant in the same language for value sort (crosstab val-asc/val-desc). Pure CSS `content`-override on the existing state classes — no template change. Gate-off unchanged.

**Files:**
- Modify: `web/projects/portal/src/scss/_themeable.scss` (append after the existing sort seam ~L1270)

**Interfaces:**
- Consumes: the state classes already on `<i class="vs-header-cell-button-sort sort-ascending-icon|sort-descending-icon|sort-value-ascending-icon|sort-value-descending-icon|sort-icon">`.
- Produces: the `.viz-modern` sort rendering; verified visually in the gallery.

- [ ] **Step 1: Append the gated label-sort override**

After the existing `.viz-modern .vs-header-cell-button-sort.sort-*` color rule, append:
```scss
// Phase 9A: modern sort indicator — shell .sort-indicator double-arrowhead language.
// Suppress the ineticons glyph and draw ⌃/⌄ arrowheads; \2303/\2304 are not in the
// ineticons font, so reset font-family on the pseudo-elements.
.viz-modern .vs-header-cell-button-sort {
  &.sort-icon::before,
  &.sort-ascending-icon::before,
  &.sort-descending-icon::before,
  &.sort-value-ascending-icon::before,
  &.sort-value-descending-icon::before {
    font-family: var(--inet-font-family, sans-serif) !important;
    font-size: 9px;
    line-height: 1;
    content: "\2303";                 // ⌃ up arrowhead (top arm)
  }
  // second arm (down arrowhead) via ::after
  &.sort-icon::after,
  &.sort-ascending-icon::after,
  &.sort-descending-icon::after {
    font-family: var(--inet-font-family, sans-serif) !important;
    font-size: 9px;
    line-height: 1;
    content: "\2304";                 // ⌄ down arrowhead (bottom arm)
    display: block;
    margin-top: -3px;
  }
  // unsorted: both arms shown faintly
  &.sort-icon::before,
  &.sort-icon::after { opacity: 0.45; }
  // ascending active: dim the down arm
  &.sort-ascending-icon::after { opacity: 0.22; }
  // descending active: dim the up arm
  &.sort-descending-icon::before { opacity: 0.22; }
}
```

- [ ] **Step 2: Append the gated value-sort variant (filled triangles)**

```scss
// Phase 9A: value-sort (crosstab) — same arrowhead position, filled triangles to
// distinguish measure-sort from label-sort while staying in one visual language.
.viz-modern .vs-header-cell-button-sort {
  &.sort-value-ascending-icon::before,
  &.sort-value-descending-icon::before { content: "\25B4"; }   // ▴ filled up
  &.sort-value-ascending-icon::after,
  &.sort-value-descending-icon::after  {
    font-family: var(--inet-font-family, sans-serif) !important;
    font-size: 9px; line-height: 1; display: block; margin-top: -3px;
    content: "\25BE";                                          // ▾ filled down
  }
  &.sort-value-ascending-icon::after  { opacity: 0.22; }       // asc → dim down
  &.sort-value-descending-icon::before { opacity: 0.22; }      // desc → dim up
}
```

- [ ] **Step 3: Rebuild the portal styles**

Run: `cd web && npm run build`
Expected: build succeeds (SCSS compiles; no unclosed selectors).

- [ ] **Step 4: Verify in the gallery / running app**

Add a `.viz-modern`-wrapped copy of the sort table to the Task 2 gallery (or toggle its `.viz-modern` container) and open it. Expected: label sort shows the thin ⌃/⌄ double-arrowhead with the active arm solid and the other dimmed; value sort shows filled ▴/▾. Confirm gate-off rows still show the original heavy ineticons glyph. Optionally run the app (`run` skill), enable `.viz-modern`, open a crosstab, and screenshot a sorted header.

- [ ] **Step 5: Commit**

```bash
git add web/projects/portal/src/scss/_themeable.scss community/docs/superpowers/specs/lookfeel/visualization-phase9a-icon-audit-gallery.html
git commit -m "feat(viz-icons): gated modern sort indicator (table + crosstab)

Under .viz-modern, repaint the header sort glyph as the shell
.sort-indicator double-arrowhead (label sort) and a filled-triangle
variant (crosstab value sort). CSS content-override on existing state
classes; gate-off byte-identical.

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 6: Track A — gated expand/collapse chevron (selection tree)

Under `.viz-modern`, repaint the selection-tree folder toggle from boxed +/- to the shell chevron pair, scoped to the tree cell so it does not affect `plus/minus-box-outline-icon` elsewhere. CSS-only; no change to `getTreeIconClass()`.

**Files:**
- Modify: `web/projects/portal/src/scss/_themeable.scss` (append)

**Interfaces:**
- Consumes: the `plus-box-outline-icon` / `minus-box-outline-icon` classes emitted by `getTreeIconClass()` inside the selection-list cell.
- Produces: chevron rendering in `.viz-modern`.

- [ ] **Step 1: Confirm the tree-cell scoping selector**

Run:
```bash
grep -rniE "getTreeIconClass|selection-list-cell|tree-toggle|plus-box-outline" web/projects/portal/src/app/vsobjects/objects/selection/selection-list-cell.component.html
```
Expected: identify the container class on the selection-list cell (e.g. `.selection-list-cell` / `.cell-content`). Use the nearest stable ancestor class as `<TREE_CELL>` below.

- [ ] **Step 2: Append the gated chevron override**

```scss
// Phase 9A: modern selection-tree toggle — shell chevron pair instead of boxed +/-.
// Scoped to the tree cell so other +/- glyphs are untouched. Chevron codepoints are
// ineticons (downward \eabd / forward \eb02), so keep the ineticons font.
.viz-modern <TREE_CELL> {
  .minus-box-outline-icon::before { content: "\eabd"; }   // open  → chevron-down
  .plus-box-outline-icon::before  { content: "\eb02"; }   // closed → chevron-right
}
```
Replace `<TREE_CELL>` with the class confirmed in Step 1.

- [ ] **Step 3: Rebuild**

Run: `cd web && npm run build`
Expected: build succeeds.

- [ ] **Step 4: Verify in the gallery / app**

Add an expand/collapse `.viz-modern` row to the gallery showing the chevron result, and confirm gate-off still shows boxed +/-. Optionally run the app with `.viz-modern`, open a selection tree, and confirm the folder toggle is a chevron.

- [ ] **Step 5: Commit**

```bash
git add web/projects/portal/src/scss/_themeable.scss community/docs/superpowers/specs/lookfeel/visualization-phase9a-icon-audit-gallery.html
git commit -m "feat(viz-icons): gated selection-tree chevron toggle

Under .viz-modern, repaint the selection-tree folder toggle from boxed
+/- to the shell chevron pair (scoped to the tree cell). CSS-only;
gate-off unchanged.

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 7: Track A — gated drill ± unification (chart axis)

Unify the inline chart-axis drill glyph to the same `shape-plus`/`shape-minus` family the zoom control already uses, under `.viz-modern`, scoped to the drill icon. Leaves the menu `drill-*-filter-icon` alone (distinct filter semantics, per spec).

**Files:**
- Modify: `web/projects/portal/src/scss/_themeable.scss` (append)

**Interfaces:**
- Consumes: `plus-box-outline-icon floating-icon` / `minus-box-outline-icon floating-icon` on the chart axis drill affordance.
- Produces: `shape-plus`/`shape-minus` rendering in `.viz-modern`.

- [ ] **Step 1: Append the gated drill override**

```scss
// Phase 9A: unify inline chart-axis drill to the zoom ± family (shape-plus \ebcd /
// shape-minus \ebb5). Scoped via .floating-icon so only the on-chart drill changes.
.viz-modern {
  .plus-box-outline-icon.floating-icon::before  { content: "\ebcd"; }  // shape-plus
  .minus-box-outline-icon.floating-icon::before { content: "\ebb5"; }  // shape-minus
}
```

- [ ] **Step 2: Rebuild**

Run: `cd web && npm run build`
Expected: build succeeds.

- [ ] **Step 3: Verify in the gallery / app**

Add a drill `.viz-modern` row to the gallery. Confirm inline drill now matches zoom (`shape-plus`/`shape-minus`) and gate-off is unchanged. Note in the gallery that the menu `drill-*-filter-icon` is intentionally left as-is.

- [ ] **Step 4: Commit**

```bash
git add web/projects/portal/src/scss/_themeable.scss community/docs/superpowers/specs/lookfeel/visualization-phase9a-icon-audit-gallery.html
git commit -m "feat(viz-icons): gated inline chart-drill ± unified with zoom

Under .viz-modern, repaint the inline chart-axis drill from boxed +/- to
the shape-plus/shape-minus family used by zoom. Menu drill-*-filter left
as-is (distinct filter semantics). CSS-only; gate-off unchanged.

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 8: More/overflow — verify and document (no viz change)

The divergence is viz vertical kebab vs the shell *tree's* horizontal meatball. Viz is already internally consistent on `menu-vertical-icon`, and changing the shell tree is out of scope. This task verifies that and records the decision — no code change unless a viz surface is found using the horizontal variant.

**Files:**
- Modify: `community/docs/superpowers/specs/lookfeel/visualization-phase9a-icon-audit-gallery.html` (add a note)

- [ ] **Step 1: Confirm viz overflow menus are uniformly vertical**

Run:
```bash
grep -rniE "menu-horizontal-icon" web/projects/portal/src/app/vsobjects web/projects/portal/src/app/graph
```
Expected: no matches (viz uses `menu-vertical-icon`). If any match appears, note the `file:line`; a one-line gated override `.viz-modern <selector> .menu-horizontal-icon::before { content: "\eb2a"; }` (vertical kebab codepoint) can align it — add it to `_themeable.scss` following the Task 7 pattern and rebuild.

- [ ] **Step 2: Record the decision in the gallery**

Add an HTML comment to the More table: viz is uniformly vertical kebab; the shell tree's horizontal meatball is the outlier and is a shell concern, deferred (no Phase 9A change).

- [ ] **Step 3: Commit**

```bash
git add community/docs/superpowers/specs/lookfeel/visualization-phase9a-icon-audit-gallery.html
git commit -m "docs(viz-icons): more/overflow already consistent in viz; shell-tree deferred

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 9: Final verification — gate-off guard, tests, gallery sign-off

**Files:** none (verification only)

- [ ] **Step 1: Guard — every Phase 9A Track A rule is gated**

Run:
```bash
cd E:/StyleBI/stylebi-enterprise/community
grep -nE "Phase 9A" web/projects/portal/src/scss/_themeable.scss
```
Inspect every appended block: confirm each selector begins with `.viz-modern`. There must be **no** Phase 9A rule at `:root` or an ungated selector. Expected: all Track A blocks are under `.viz-modern`.

- [ ] **Step 2: Run the portal unit tests**

Run: `cd web && npm run test:portal`
Expected: PASS, including the new `base-table-actions` and `selection-list-actions` specs.

- [ ] **Step 3: Production build**

Run: `cd web && npm run build:prod`
Expected: build succeeds.

- [ ] **Step 4: Gallery sign-off (gate-off vs gate-on)**

Open the audit gallery. Toggle the `.viz-modern` container. Confirm, for each affordance: gate-off renders today's glyph exactly; gate-on renders the consolidated indicator (sort double-arrowhead + filled-triangle value variant; chevron tree toggle; unified drill ±). This is the reviewer artifact.

- [ ] **Step 5: Running-app spot capture (marquee surfaces)**

Using the `run` or `claude-in-chrome` skill, launch the app, enable `.viz-modern`, and screenshot: a crosstab sorted header (label + value sort), a selection tree folder toggle, and a chart with inline drill. Attach to the PR. Confirm hit-area and legibility at dense sizes are unaffected.

- [ ] **Step 6: Final commit / open PR**

Ensure all work is committed on the feature branch. Open a community PR referencing the Phase 9A spec and the roadmap insertion, attaching the gallery and screenshots.

---

## Self-Review Notes (author)

- **Spec coverage:** Task 1 = spec Task 0 (export grounding); Task 2 = spec Task 1 (audit gallery); Task 5 = Track A sort incl. value-sort variant; Task 6 = expand/collapse; Task 7 = drill; Task 8 = more/overflow; Tasks 3-4 = Track B global cleanup; Task 9 = compatibility guard + validation folded toward Phase 10. All spec sections mapped.
- **Placeholder scan:** the only literal placeholder is `<TREE_CELL>` in Task 6, resolved by that task's Step 1 grep before use — not a deferred design decision.
- **Type consistency:** `getOrderIcon` names match the ineticons map exactly; `.viz-modern` gate name matches the existing seam; codepoints cross-checked against `variables.scss`.
- **Design decisions locked here (validated visually in the gallery, per spec):** value-sort = filled triangles ▴/▾ in the arrowhead language; expand/collapse target = `downward`/`forward` chevrons; drill canonical ± = `shape-plus`/`shape-minus` (menu filter glyphs retained).
