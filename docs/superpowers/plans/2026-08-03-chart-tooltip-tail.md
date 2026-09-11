# Chart Tooltip Tail Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give the chart plot-area data-hover tooltip a tail that points at the hovered mark, on whichever edge suits the chart type, sliding along that edge to stay over the mark.

**Architecture:** All placement math and SVG path generation live in one pure, DOM-free module (`tooltip-tail-placement.ts`) so they are unit-testable without TestBed. `TooltipDirective` gains an opt-in tail branch that calls it; `TooltipComponent` renders the resulting geometry as an SVG "chrome" layer (background rect + open-sided border path + tail triangle) drawn as a single continuous outline behind the existing HTML content. The anchor is the hovered mark's rect, sourced from the inline-SVG tiles, so the feature is gated on inline-SVG mode.

**Tech Stack:** Angular 21 standalone components/directives, TypeScript 5.9, SCSS, Vitest 4 (via `@angular/build:unit-test`).

**Source spec:** `E:/Temp/2026-07-22-chart-tooltip-tail-design.md` (revised 2026-08-03).

## Global Constraints

- Every file lives under `community/` — this is a **community-repo-only** change. Open the PR in the community repo; no enterprise PR is needed.
- All new files require the AGPL license header used by every file in this tree (copy it verbatim from `tooltip.component.ts`, adjusting only `/*` vs `/*!` — `.scss` files use `/*!`).
- Indentation is **3 spaces**, matching the surrounding code. Do not reformat existing lines.
- No comments in Angular HTML template (`.html`) files.
- Code comments are short clauses, not full sentences, and must not reference tickets, PRs, mockups, or design docs.
- Nothing outside the plot-area tooltip may change behavior. Every existing `[wTooltip]` caller must render byte-for-byte as it does today when `showTail` is false.
- Run tests from `community/web` with:
  `npx ng test portal --include="**/<spec-file>.spec.ts"`
  Running `npx vitest` directly fails ("describe is not defined") because it bypasses the builder's setup files.
- Geometry constants, fixed for this change: `TAIL_RADIUS = 8`, `TAIL_LENGTH = 8`, `TAIL_HALF_WIDTH = 6.7`, `TAIL_GAP = 8`, `TOOLTIP_INSET = 3`.

---

## File Structure

| File | Status | Responsibility |
|---|---|---|
| `web/projects/portal/src/app/widget/tooltip/tooltip-tail-placement.ts` | create | Pure geometry: constants, types, `computeTailPlacement()`, `buildChromePaths()`. No DOM, no Angular. |
| `web/projects/portal/src/app/widget/tooltip/tooltip-tail-placement.spec.ts` | create | Unit tests for the above. |
| `web/projects/portal/src/app/graph/objects/chart-tail-config.ts` | create | `tailAxisForChartType()` — the chart-type → axis lookup. |
| `web/projects/portal/src/app/graph/objects/chart-tail-config.spec.ts` | create | Unit tests for the lookup. |
| `web/projects/portal/src/app/widget/tooltip/tooltip.component.ts` | modify | New inputs; derives chrome geometry. |
| `web/projects/portal/src/app/widget/tooltip/tooltip.component.html` | modify | Renders the chrome SVG in the string-content branch. |
| `web/projects/portal/src/app/widget/tooltip/tooltip.component.scss` | modify | Chrome positioning, fills, strokes, drop-shadow. |
| `web/projects/portal/src/app/widget/tooltip/tooltip.component.spec.ts` | create | Render tests for the chrome. |
| `web/projects/portal/src/scss/internal/_directives.scss` | modify | `.widget__card-tooltip--tailed` modifier. |
| `web/projects/portal/src/app/widget/tooltip/tooltip.directive.ts` | modify | `showTail`/`tailAxis`/`tailAnchor` inputs; tail placement branch; anchor-change gating. |
| `web/projects/portal/src/app/widget/tooltip/tooltip.directive.spec.ts` | create | Directive integration tests with stubbed rects. |
| `web/projects/portal/src/app/graph/objects/chart-inline-svg.directive.ts` | modify | `getElementRect(row, col)`. |
| `web/projects/portal/src/app/graph/objects/chart-inline-svg.directive.spec.ts` | modify | Test for `getElementRect`. |
| `web/projects/portal/src/app/graph/objects/chart-plot-area.component.ts` | modify | Captures the hovered mark's rect as `tailAnchor`. |
| `web/projects/portal/src/app/graph/objects/chart-area.component.ts` | modify | Tail gate, axis selection, anchor provider. |
| `web/projects/portal/src/app/graph/objects/chart-area.component.html` | modify | Plot-area bindings. |

All paths below are relative to `community/`.

---

### Task 1: Pure placement geometry — vertical axis

**Files:**
- Create: `web/projects/portal/src/app/widget/tooltip/tooltip-tail-placement.ts`
- Test: `web/projects/portal/src/app/widget/tooltip/tooltip-tail-placement.spec.ts`

**Interfaces:**
- Consumes: `Rectangular` from `../../common/data/rectangle` (an interface: `{x, y, width, height}`).
- Produces:
  - `type TailSide = "top" | "bottom" | "left" | "right"`
  - `type TailAxis = "vertical" | "horizontal"`
  - `const TAIL_RADIUS`, `TAIL_LENGTH`, `TAIL_HALF_WIDTH`, `TAIL_GAP`, `TOOLTIP_INSET` (all `number`)
  - `interface TailPlacement { x: number; y: number; tailSide: TailSide; tailOffset: number; }`
  - `interface TailPlacementInput { anchor: {x: number, y: number}; hostWidth: number; hostHeight: number; container: Rectangular; axis: TailAxis; }`
  - `function computeTailPlacement(input: TailPlacementInput): TailPlacement | null`

**Background the implementer needs:**

`x`/`y` in the result are for the **host** `w-tooltip` element (viewport coordinates, `position: fixed`). The visible box sits 3px inside the host on all sides — the host is `position: fixed`, which establishes a BFC, so the inner box's `margin: 3px` does not collapse. `TOOLTIP_INSET` is that 3px. All the math below works in *box* coordinates and subtracts the inset at the end.

Returning `null` means "cannot place with a tail" — the caller falls back to the existing cursor-offset placement with no tail.

The reference implementation this ports (`chart-shared.js:148-213`) has a "safety flip" that re-flips the box when clamping made it engulf the anchor. That flip is **deliberately omitted**: it clamps against the plot rect while testing room against the same rect, so any case that triggers the flip also fails after flipping. Returning `null` on engulf is equivalent and simpler. Do not add the flip back.

- [ ] **Step 1: Write the failing test**

Create `web/projects/portal/src/app/widget/tooltip/tooltip-tail-placement.spec.ts`:

```ts
/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
import { computeTailPlacement, TOOLTIP_INSET } from "./tooltip-tail-placement";

// Host 206x106 => inner box 200x100 once the 3px inset is removed.
const HOST = { hostWidth: 206, hostHeight: 106 };
const CONTAINER = { x: 0, y: 0, width: 1000, height: 600 };

function vertical(x: number, y: number, container = CONTAINER) {
   return computeTailPlacement({ anchor: { x, y }, ...HOST, container, axis: "vertical" });
}

describe("computeTailPlacement (vertical axis)", () => {
   it("places the box above the anchor when there is room, tail on the bottom edge", () => {
      const p = vertical(500, 400);
      // box bottom sits TAIL_LENGTH above the anchor: 400 - 8 - 100 = 292, less the inset.
      expect(p).toEqual({
         x: 400 - TOOLTIP_INSET, y: 292 - TOOLTIP_INSET, tailSide: "bottom", tailOffset: 100
      });
   });

   it("flips below the anchor when there is not enough room above, tail on the top edge", () => {
      const p = vertical(500, 50);
      expect(p.tailSide).toBe("top");
      expect(p.y).toBe(58 - TOOLTIP_INSET);
   });

   it("biases sideways away from the nearer edge when placed below", () => {
      // belowOffset = clamp(200 * 0.18, 24, 56) = 36; more room to the right, so shift right.
      expect(vertical(500, 50).x).toBe(436 - TOOLTIP_INSET);
      // nearer the right edge, so shift left instead.
      expect(vertical(900, 50).x).toBe(764 - TOOLTIP_INSET);
   });

   it("keeps the tail over the anchor when the box is clamped by the container edge", () => {
      const p = vertical(960, 400);
      // box clamped to the right edge, so the tail slides off-centre to stay on the anchor.
      expect(p.x).toBe(797 - TOOLTIP_INSET);
      expect(p.tailOffset).toBe(163);
   });

   it("clamps the tail clear of the rounded corners", () => {
      const p = vertical(995, 400);
      // 995 - 797 = 198 would overrun the corner; clamped to 200 - 8 - 6.7.
      expect(p.tailOffset).toBeCloseTo(185.3, 5);
   });

   it("returns null when the container cannot hold the box clear of the anchor", () => {
      expect(vertical(60, 60, { x: 0, y: 0, width: 1000, height: 150 })).toBeNull();
   });

   it("returns null when the box is larger than the container", () => {
      expect(vertical(500, 400, { x: 0, y: 0, width: 100, height: 600 })).toBeNull();
   });
});
```

- [ ] **Step 2: Run the test to verify it fails**

Run from `community/web`:

```bash
npx ng test portal --include="**/tooltip-tail-placement.spec.ts"
```

Expected: FAIL — the module `./tooltip-tail-placement` does not exist.

- [ ] **Step 3: Write the minimal implementation**

Create `web/projects/portal/src/app/widget/tooltip/tooltip-tail-placement.ts`:

```ts
/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
import { Rectangular } from "../../common/data/rectangle";

export type TailSide = "top" | "bottom" | "left" | "right";
export type TailAxis = "vertical" | "horizontal";

/** Corner radius of the tooltip box, matching the card skin. */
export const TAIL_RADIUS = 8;
/** How far the tail projects past the box edge. */
export const TAIL_LENGTH = 8;
/** Half the width of the tail's opening on the box edge. */
export const TAIL_HALF_WIDTH = 6.7;
/** Anchor-to-box gap on the horizontal axis. */
export const TAIL_GAP = 8;
/** The inner box's margin; the host is this much larger on every side. */
export const TOOLTIP_INSET = 3;

export interface TailPlacement {
   /** Host left, viewport coordinates. */
   x: number;
   /** Host top, viewport coordinates. */
   y: number;
   tailSide: TailSide;
   /** Distance from the box's leading edge to the tail's centre. */
   tailOffset: number;
}

export interface TailPlacementInput {
   anchor: { x: number, y: number };
   hostWidth: number;
   hostHeight: number;
   container: Rectangular;
   axis: TailAxis;
}

interface Bounds {
   left: number;
   right: number;
   top: number;
   bottom: number;
}

export function computeTailPlacement(input: TailPlacementInput): TailPlacement | null {
   const bw = input.hostWidth - 2 * TOOLTIP_INSET;
   const bh = input.hostHeight - 2 * TOOLTIP_INSET;
   const bounds: Bounds = {
      left: input.container.x + TOOLTIP_INSET,
      right: input.container.x + input.container.width - TOOLTIP_INSET,
      top: input.container.y + TOOLTIP_INSET,
      bottom: input.container.y + input.container.height - TOOLTIP_INSET
   };

   if(bw <= 0 || bh <= 0 || bounds.right - bounds.left < bw || bounds.bottom - bounds.top < bh) {
      return null;
   }

   return input.axis === "horizontal"
      ? placeHorizontal(input.anchor, bw, bh, bounds)
      : placeVertical(input.anchor, bw, bh, bounds);
}

function placeVertical(anchor: { x: number, y: number }, bw: number, bh: number,
                       bounds: Bounds): TailPlacement | null
{
   const needed = bh + TAIL_LENGTH;
   const topRoom = anchor.y - bounds.top;
   const bottomRoom = bounds.bottom - anchor.y;
   const below = topRoom < needed && (bottomRoom >= needed || bottomRoom > topRoom);
   // Shift sideways when below so the cursor is less likely to cover the text.
   const belowOffset = Math.min(Math.max(24, bw * 0.18), 56);
   const bias = !below ? 0
      : (bounds.right - anchor.x >= anchor.x - bounds.left ? belowOffset : -belowOffset);
   const bx = clamp(anchor.x + bias - bw / 2, bounds.left, bounds.right - bw);
   const by = clamp(below ? anchor.y + TAIL_LENGTH : anchor.y - TAIL_LENGTH - bh,
      bounds.top, bounds.bottom - bh);

   if(engulfs(anchor.y, by, bh)) {
      return null;
   }

   return {
      x: bx - TOOLTIP_INSET,
      y: by - TOOLTIP_INSET,
      tailSide: below ? "top" : "bottom",
      tailOffset: clampToEdge(anchor.x - bx, bw)
   };
}

function placeHorizontal(anchor: { x: number, y: number }, bw: number, bh: number,
                         bounds: Bounds): TailPlacement | null
{
   const right = bounds.right - anchor.x >= anchor.x - bounds.left;
   const bx = clamp(right ? anchor.x + TAIL_GAP : anchor.x - TAIL_GAP - bw,
      bounds.left, bounds.right - bw);

   if(engulfs(anchor.x, bx, bw)) {
      return null;
   }

   const by = clamp(anchor.y - bh / 2, bounds.top, bounds.bottom - bh);

   return {
      x: bx - TOOLTIP_INSET,
      y: by - TOOLTIP_INSET,
      tailSide: right ? "left" : "right",
      tailOffset: clampToEdge(anchor.y - by, bh)
   };
}

function engulfs(anchor: number, start: number, size: number): boolean {
   return anchor > start && anchor < start + size;
}

function clamp(value: number, lo: number, hi: number): number {
   return Math.min(hi, Math.max(lo, value));
}

/** Keep the tail on the straight part of the edge; centre it if the edge is too short. */
function clampToEdge(offset: number, edge: number): number {
   const lo = TAIL_RADIUS + TAIL_HALF_WIDTH;
   const hi = edge - TAIL_RADIUS - TAIL_HALF_WIDTH;
   return hi < lo ? (lo + hi) / 2 : clamp(offset, lo, hi);
}
```

- [ ] **Step 4: Run the test to verify it passes**

```bash
npx ng test portal --include="**/tooltip-tail-placement.spec.ts"
```

Expected: PASS, 7 tests.

- [ ] **Step 5: Commit**

```bash
git add web/projects/portal/src/app/widget/tooltip/tooltip-tail-placement.ts \
        web/projects/portal/src/app/widget/tooltip/tooltip-tail-placement.spec.ts
git commit -m "feat(tooltip): add vertical-axis tail placement geometry"
```

---

### Task 2: Pure placement geometry — horizontal axis

**Files:**
- Modify: `web/projects/portal/src/app/widget/tooltip/tooltip-tail-placement.spec.ts` (append a describe block)

**Interfaces:**
- Consumes: `computeTailPlacement`, `TOOLTIP_INSET` from Task 1.
- Produces: nothing new — `placeHorizontal` was written in Task 1 but is not yet covered.

This task adds the missing coverage for the horizontal branch. The implementation already exists; if a test fails, fix `placeHorizontal`, not the test.

- [ ] **Step 1: Write the failing test**

Append to `tooltip-tail-placement.spec.ts`:

```ts
describe("computeTailPlacement (horizontal axis)", () => {
   function horizontal(x: number, y: number, container = CONTAINER) {
      return computeTailPlacement({ anchor: { x, y }, ...HOST, container, axis: "horizontal" });
   }

   it("places the box right of the anchor when the right side has more room", () => {
      const p = horizontal(200, 300);
      expect(p).toEqual({
         x: 208 - TOOLTIP_INSET, y: 250 - TOOLTIP_INSET, tailSide: "left", tailOffset: 50
      });
   });

   it("places the box left of the anchor when the left side has more room", () => {
      const p = horizontal(800, 300);
      // box right edge sits TAIL_GAP left of the anchor: 800 - 8 - 200 = 592.
      expect(p.tailSide).toBe("right");
      expect(p.x).toBe(592 - TOOLTIP_INSET);
   });

   it("centres the box on the anchor vertically", () => {
      expect(horizontal(200, 300).y).toBe(250 - TOOLTIP_INSET);
   });

   it("keeps the tail over the anchor when the box is clamped by the container top", () => {
      const p = horizontal(200, 20);
      expect(p.y).toBe(3 - TOOLTIP_INSET);
      expect(p.tailOffset).toBe(17);
   });

   it("clamps the tail clear of the rounded corners", () => {
      const p = horizontal(200, 4);
      expect(p.tailOffset).toBeCloseTo(14.7, 5);
   });

   it("returns null when the container is too narrow to clear the anchor", () => {
      expect(horizontal(120, 300, { x: 0, y: 0, width: 210, height: 600 })).toBeNull();
   });
});
```

- [ ] **Step 2: Run the test to verify it fails**

```bash
npx ng test portal --include="**/tooltip-tail-placement.spec.ts"
```

Expected: the new `describe` block runs. If every assertion already passes, the horizontal branch was correct as written in Task 1 — that is an acceptable outcome for this task; note it and continue. Any failure is a bug in `placeHorizontal`.

- [ ] **Step 3: Fix `placeHorizontal` if any assertion failed**

Only if Step 2 reported failures, correct `placeHorizontal` in `tooltip-tail-placement.ts` so the expectations hold. Do not weaken the test.

- [ ] **Step 4: Run the test to verify it passes**

```bash
npx ng test portal --include="**/tooltip-tail-placement.spec.ts"
```

Expected: PASS, 13 tests.

- [ ] **Step 5: Commit**

```bash
git add web/projects/portal/src/app/widget/tooltip/tooltip-tail-placement.spec.ts \
        web/projects/portal/src/app/widget/tooltip/tooltip-tail-placement.ts
git commit -m "test(tooltip): cover horizontal-axis tail placement"
```

---

### Task 3: SVG chrome path generation

**Files:**
- Modify: `web/projects/portal/src/app/widget/tooltip/tooltip-tail-placement.ts`
- Modify: `web/projects/portal/src/app/widget/tooltip/tooltip-tail-placement.spec.ts`

**Interfaces:**
- Consumes: `TailSide`, `TAIL_RADIUS`, `TAIL_LENGTH`, `TAIL_HALF_WIDTH` from Task 1.
- Produces:
  - `interface ChromeGeometry { width: number; height: number; boxX: number; boxY: number; boxWidth: number; boxHeight: number; radius: number; borderPath: string; tailPath: string; }`
  - `function buildChromePaths(boxWidth: number, boxHeight: number, tailSide: TailSide, tailOffset: number): ChromeGeometry`

**Background the implementer needs:**

This is the technique that makes the tail seamless. Rather than drawing a bordered box and butting a bordered triangle against it — which leaves the box's border line running across the tail's base — the border is an **open** path: it traces the rounded rectangle but stops `TAIL_HALF_WIDTH` short of the tail on one side and resumes `TAIL_HALF_WIDTH` past it on the other. The tail path picks up at exactly those two points and runs out to the apex and back. Border and tail together are one continuous outline. A plain `<rect>` behind them supplies the fill.

Coordinates are **SVG-local**. The SVG viewport is `TAIL_LENGTH` larger than the box on every side so the tail has room, so the box's top-left is at `(TAIL_LENGTH, TAIL_LENGTH)`.

`tailOffset` is measured from the box's leading edge: from the box's left edge for `top`/`bottom`, from its top edge for `left`/`right`.

Round every emitted number to 2 decimals so the path strings stay readable and comparable.

- [ ] **Step 1: Write the failing test**

Append to `tooltip-tail-placement.spec.ts` (and extend the import at the top of the file to
`import { buildChromePaths, computeTailPlacement, TOOLTIP_INSET } from "./tooltip-tail-placement";`):

```ts
describe("buildChromePaths", () => {
   it("sizes the viewport to clear the tail on every side", () => {
      const c = buildChromePaths(200, 100, "bottom", 50);
      expect(c).toMatchObject({
         width: 216, height: 116, boxX: 8, boxY: 8, boxWidth: 200, boxHeight: 100, radius: 8
      });
   });

   it("opens the border at the tail and closes it with the tail path (bottom)", () => {
      const c = buildChromePaths(200, 100, "bottom", 50);
      // box spans x 8..208, y 8..108; tail centre at x 58, apex 8px below the box.
      expect(c.borderPath.startsWith("M64.7,108 ")).toBe(true);
      expect(c.borderPath.endsWith(" L51.3,108")).toBe(true);
      expect(c.tailPath).toBe("M51.3,108 L58,116 L64.7,108");
   });

   it("opens the border at the tail and closes it with the tail path (top)", () => {
      const c = buildChromePaths(200, 100, "top", 50);
      expect(c.borderPath.startsWith("M51.3,8 ")).toBe(true);
      expect(c.borderPath.endsWith(" L64.7,8")).toBe(true);
      expect(c.tailPath).toBe("M64.7,8 L58,0 L51.3,8");
   });

   it("puts the tail on the left edge with a vertical offset", () => {
      const c = buildChromePaths(200, 100, "left", 50);
      expect(c.borderPath.startsWith("M8,51.3 ")).toBe(true);
      expect(c.borderPath.endsWith(" L8,64.7")).toBe(true);
      expect(c.tailPath).toBe("M8,64.7 L0,58 L8,51.3");
   });

   it("puts the tail on the right edge with a vertical offset", () => {
      const c = buildChromePaths(200, 100, "right", 50);
      expect(c.borderPath.startsWith("M208,64.7 ")).toBe(true);
      expect(c.borderPath.endsWith(" L208,51.3")).toBe(true);
      expect(c.tailPath).toBe("M208,51.3 L216,58 L208,64.7");
   });

   it("traces all four rounded corners regardless of side", () => {
      for(const side of ["top", "bottom", "left", "right"] as const) {
         const c = buildChromePaths(200, 100, side, 50);
         expect(c.borderPath.match(/Q/g)?.length).toBe(4);
      }
   });
});
```

- [ ] **Step 2: Run the test to verify it fails**

```bash
npx ng test portal --include="**/tooltip-tail-placement.spec.ts"
```

Expected: FAIL — `buildChromePaths is not a function`.

- [ ] **Step 3: Write the implementation**

Append to `tooltip-tail-placement.ts`:

```ts
export interface ChromeGeometry {
   /** SVG viewport size, TAIL_LENGTH larger than the box on every side. */
   width: number;
   height: number;
   boxX: number;
   boxY: number;
   boxWidth: number;
   boxHeight: number;
   radius: number;
   /** Rounded rect, open across the tail's base. */
   borderPath: string;
   /** Triangle closing that opening. */
   tailPath: string;
}

export function buildChromePaths(boxWidth: number, boxHeight: number, tailSide: TailSide,
                                 tailOffset: number): ChromeGeometry
{
   const r = TAIL_RADIUS;
   const tw = TAIL_HALF_WIDTH;
   const l = TAIL_LENGTH;
   const bL = l, bT = l, bR = l + boxWidth, bB = l + boxHeight;
   let borderPath: string;
   let tailPath: string;

   if(tailSide === "top" || tailSide === "bottom") {
      const tx = bL + tailOffset;

      if(tailSide === "top") {
         borderPath =
            `M${n(tx - tw)},${n(bT)} L${n(bL + r)},${n(bT)} Q${n(bL)},${n(bT)} ${n(bL)},${n(bT + r)}` +
            ` L${n(bL)},${n(bB - r)} Q${n(bL)},${n(bB)} ${n(bL + r)},${n(bB)}` +
            ` L${n(bR - r)},${n(bB)} Q${n(bR)},${n(bB)} ${n(bR)},${n(bB - r)}` +
            ` L${n(bR)},${n(bT + r)} Q${n(bR)},${n(bT)} ${n(bR - r)},${n(bT)} L${n(tx + tw)},${n(bT)}`;
         tailPath = `M${n(tx + tw)},${n(bT)} L${n(tx)},${n(bT - l)} L${n(tx - tw)},${n(bT)}`;
      }
      else {
         borderPath =
            `M${n(tx + tw)},${n(bB)} L${n(bR - r)},${n(bB)} Q${n(bR)},${n(bB)} ${n(bR)},${n(bB - r)}` +
            ` L${n(bR)},${n(bT + r)} Q${n(bR)},${n(bT)} ${n(bR - r)},${n(bT)}` +
            ` L${n(bL + r)},${n(bT)} Q${n(bL)},${n(bT)} ${n(bL)},${n(bT + r)}` +
            ` L${n(bL)},${n(bB - r)} Q${n(bL)},${n(bB)} ${n(bL + r)},${n(bB)} L${n(tx - tw)},${n(bB)}`;
         tailPath = `M${n(tx - tw)},${n(bB)} L${n(tx)},${n(bB + l)} L${n(tx + tw)},${n(bB)}`;
      }
   }
   else {
      const ty = bT + tailOffset;

      if(tailSide === "left") {
         borderPath =
            `M${n(bL)},${n(ty - tw)} L${n(bL)},${n(bT + r)} Q${n(bL)},${n(bT)} ${n(bL + r)},${n(bT)}` +
            ` L${n(bR - r)},${n(bT)} Q${n(bR)},${n(bT)} ${n(bR)},${n(bT + r)}` +
            ` L${n(bR)},${n(bB - r)} Q${n(bR)},${n(bB)} ${n(bR - r)},${n(bB)}` +
            ` L${n(bL + r)},${n(bB)} Q${n(bL)},${n(bB)} ${n(bL)},${n(bB - r)} L${n(bL)},${n(ty + tw)}`;
         tailPath = `M${n(bL)},${n(ty + tw)} L${n(bL - l)},${n(ty)} L${n(bL)},${n(ty - tw)}`;
      }
      else {
         borderPath =
            `M${n(bR)},${n(ty + tw)} L${n(bR)},${n(bB - r)} Q${n(bR)},${n(bB)} ${n(bR - r)},${n(bB)}` +
            ` L${n(bL + r)},${n(bB)} Q${n(bL)},${n(bB)} ${n(bL)},${n(bB - r)}` +
            ` L${n(bL)},${n(bT + r)} Q${n(bL)},${n(bT)} ${n(bL + r)},${n(bT)}` +
            ` L${n(bR - r)},${n(bT)} Q${n(bR)},${n(bT)} ${n(bR)},${n(bT + r)} L${n(bR)},${n(ty - tw)}`;
         tailPath = `M${n(bR)},${n(ty - tw)} L${n(bR + l)},${n(ty)} L${n(bR)},${n(ty + tw)}`;
      }
   }

   return {
      width: boxWidth + 2 * l,
      height: boxHeight + 2 * l,
      boxX: bL,
      boxY: bT,
      boxWidth,
      boxHeight,
      radius: r,
      borderPath,
      tailPath
   };
}

function n(value: number): number {
   return Math.round(value * 100) / 100;
}
```

- [ ] **Step 4: Run the test to verify it passes**

```bash
npx ng test portal --include="**/tooltip-tail-placement.spec.ts"
```

Expected: PASS, 19 tests.

- [ ] **Step 5: Commit**

```bash
git add web/projects/portal/src/app/widget/tooltip/tooltip-tail-placement.ts \
        web/projects/portal/src/app/widget/tooltip/tooltip-tail-placement.spec.ts
git commit -m "feat(tooltip): generate seamless tail chrome paths"
```

---

### Task 4: Chart-type → tail axis lookup

**Files:**
- Create: `web/projects/portal/src/app/graph/objects/chart-tail-config.ts`
- Test: `web/projects/portal/src/app/graph/objects/chart-tail-config.spec.ts`

**Interfaces:**
- Consumes: `GraphTypes` from `../../common/graph-types`; `TailAxis` from `../../widget/tooltip/tooltip-tail-placement`.
- Produces: `function tailAxisForChartType(chartType: number): TailAxis`

**Background the implementer needs:**

Which edge the tail sits on is decided per chart type, not adaptively per hover. The nine types below take the horizontal axis; everything else is vertical. This is a design-owned list, not a derivable rule — sunburst is vertical while icicle is horizontal, and step-area is vertical while step-line is horizontal. Do not "tidy" it into a family predicate. `GraphTypes` has family helpers at `graph-types.ts:323`, `:339` and `:349`, but they answer line/texture/merged questions and none matches this set.

- [ ] **Step 1: Write the failing test**

Create `web/projects/portal/src/app/graph/objects/chart-tail-config.spec.ts`:

```ts
/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
import { GraphTypes } from "../../common/graph-types";
import { tailAxisForChartType } from "./chart-tail-config";

describe("tailAxisForChartType", () => {
   it("uses the horizontal axis for hierarchical, relational and step-line types", () => {
      const horizontal = [
         GraphTypes.CHART_TREE, GraphTypes.CHART_NETWORK, GraphTypes.CHART_CIRCULAR,
         GraphTypes.CHART_TREEMAP, GraphTypes.CHART_ICICLE, GraphTypes.CHART_CIRCLE_PACKING,
         GraphTypes.CHART_MEKKO, GraphTypes.CHART_STEP, GraphTypes.CHART_JUMP
      ];

      for(const type of horizontal) {
         expect(tailAxisForChartType(type)).toBe("horizontal");
      }
   });

   it("uses the vertical axis for everything else", () => {
      const vertical = [
         GraphTypes.CHART_BAR, GraphTypes.CHART_BAR_STACK, GraphTypes.CHART_PIE,
         GraphTypes.CHART_DONUT, GraphTypes.CHART_LINE, GraphTypes.CHART_AREA,
         GraphTypes.CHART_CANDLE, GraphTypes.CHART_BOXPLOT, GraphTypes.CHART_RADAR,
         GraphTypes.CHART_WATERFALL, GraphTypes.CHART_PARETO, GraphTypes.CHART_GANTT,
         GraphTypes.CHART_FUNNEL, GraphTypes.CHART_POINT, GraphTypes.CHART_AUTO
      ];

      for(const type of vertical) {
         expect(tailAxisForChartType(type)).toBe("vertical");
      }
   });

   it("keeps sunburst vertical even though icicle is horizontal", () => {
      expect(tailAxisForChartType(GraphTypes.CHART_SUNBURST)).toBe("vertical");
      expect(tailAxisForChartType(GraphTypes.CHART_ICICLE)).toBe("horizontal");
   });

   it("keeps the step-area family vertical even though step-line is horizontal", () => {
      expect(tailAxisForChartType(GraphTypes.CHART_STEP_AREA)).toBe("vertical");
      expect(tailAxisForChartType(GraphTypes.CHART_STEP_AREA_STACK)).toBe("vertical");
      expect(tailAxisForChartType(GraphTypes.CHART_STEP)).toBe("horizontal");
   });

   it("defaults to vertical for an unknown type", () => {
      expect(tailAxisForChartType(null)).toBe("vertical");
      expect(tailAxisForChartType(-1)).toBe("vertical");
   });
});
```

- [ ] **Step 2: Run the test to verify it fails**

```bash
npx ng test portal --include="**/chart-tail-config.spec.ts"
```

Expected: FAIL — the module `./chart-tail-config` does not exist.

- [ ] **Step 3: Write the implementation**

Create `web/projects/portal/src/app/graph/objects/chart-tail-config.ts`:

```ts
/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
import { GraphTypes } from "../../common/graph-types";
import { TailAxis } from "../../widget/tooltip/tooltip-tail-placement";

/** Types whose tooltip tail sits on a vertical edge. Per-type design choice, not a family rule. */
const HORIZONTAL_TAIL_TYPES = new Set<number>([
   GraphTypes.CHART_TREE,
   GraphTypes.CHART_NETWORK,
   GraphTypes.CHART_CIRCULAR,
   GraphTypes.CHART_TREEMAP,
   GraphTypes.CHART_ICICLE,
   GraphTypes.CHART_CIRCLE_PACKING,
   GraphTypes.CHART_MEKKO,
   GraphTypes.CHART_STEP,
   GraphTypes.CHART_JUMP
]);

export function tailAxisForChartType(chartType: number): TailAxis {
   return HORIZONTAL_TAIL_TYPES.has(chartType) ? "horizontal" : "vertical";
}
```

- [ ] **Step 4: Run the test to verify it passes**

```bash
npx ng test portal --include="**/chart-tail-config.spec.ts"
```

Expected: PASS, 5 tests.

- [ ] **Step 5: Commit**

```bash
git add web/projects/portal/src/app/graph/objects/chart-tail-config.ts \
        web/projects/portal/src/app/graph/objects/chart-tail-config.spec.ts
git commit -m "feat(chart): map chart types to tooltip tail axis"
```

---

### Task 5: Render the chrome in TooltipComponent

**Files:**
- Modify: `web/projects/portal/src/app/widget/tooltip/tooltip.component.ts`
- Modify: `web/projects/portal/src/app/widget/tooltip/tooltip.component.html`
- Modify: `web/projects/portal/src/app/widget/tooltip/tooltip.component.scss`
- Modify: `web/projects/portal/src/scss/internal/_directives.scss:221` (immediately after the `.widget__default-tooltip` block)
- Test: `web/projects/portal/src/app/widget/tooltip/tooltip.component.spec.ts`

**Interfaces:**
- Consumes: `buildChromePaths`, `ChromeGeometry`, `TailSide`, `TAIL_LENGTH`, `TOOLTIP_INSET` from Task 3.
- Produces, on `TooltipComponent`:
  - `@Input() tailSide: TailSide | null`
  - `@Input() tailOffset: number`
  - `@Input() boxSize: {width: number, height: number} | null`
  - `get chrome(): ChromeGeometry | null`
  - `readonly chromeInset: number` — the CSS offset for the SVG, `TOOLTIP_INSET - TAIL_LENGTH`.

**Background the implementer needs:**

The component is `OnPush` and is driven imperatively by `TooltipDirective`, which sets properties and then calls `updateView()` (`detectChanges()`). The inputs below are set that way, not through a template.

Placement constraints, all load-bearing:

- The chrome SVG must be a **sibling** of the `[ngClass]` box, not a child — both skins set `overflow: hidden`, which would clip the tail.
- It must be `position: absolute` so it does not change the host's `getBoundingClientRect()`. The directive re-measures the host on every placement pass; an in-flow element would grow the box each pass and oscillate.
- `.tooltip-container` currently has no CSS rule anywhere. It is used as a class-name probe in `base-table.ts:2367` — do not rename it. Adding `position: relative` is safe.
- The box is inset 3px inside the host; the SVG viewport is `TAIL_LENGTH` larger than the box on every side. So the SVG's top-left is at `TOOLTIP_INSET - TAIL_LENGTH` = `-5px` relative to `.tooltip-container`.
- The `--tailed` modifier goes in `_directives.scss` next to `.widget__card-tooltip`, not in the component SCSS: it has to beat the base skin's `background-color`/`border`/`box-shadow`, and same-specificity selectors are resolved by source order.
- The tail's fill is what covers the border's opening, so `.tooltip-chrome__tail` needs both a fill and a stroke; the border path is stroke-only. The tail path is deliberately open (three points, no `Z`) so the stroke draws only its two outward edges while the fill still closes the triangle.

- [ ] **Step 1: Write the failing test**

Create `web/projects/portal/src/app/widget/tooltip/tooltip.component.spec.ts`:

```ts
/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
import { ComponentFixture, TestBed } from "@angular/core/testing";
import { TooltipComponent } from "./tooltip.component";
import { TailSide } from "./tooltip-tail-placement";

describe("TooltipComponent chrome", () => {
   let fixture: ComponentFixture<TooltipComponent>;
   let comp: TooltipComponent;

   beforeEach(async() => {
      await TestBed.configureTestingModule({ imports: [TooltipComponent] }).compileComponents();
      fixture = TestBed.createComponent(TooltipComponent);
      comp = fixture.componentInstance;
      comp.content = "hello";
      comp.tooltipCSS = "widget__card-tooltip";
   });

   function render(tailSide: TailSide | null, tailOffset = 50): HTMLElement {
      comp.tailSide = tailSide;
      comp.tailOffset = tailOffset;
      comp.boxSize = tailSide ? { width: 200, height: 100 } : null;
      comp.updateView();
      return fixture.nativeElement;
   }

   it("renders no chrome when there is no tail", () => {
      expect(render(null).querySelector(".tooltip-chrome")).toBeNull();
   });

   it("leaves the box skin untouched when there is no tail", () => {
      const box = render(null).querySelector(".widget__card-tooltip");
      expect(box.classList.contains("widget__card-tooltip--tailed")).toBe(false);
   });

   it("renders the chrome as a sibling of the box, never inside it", () => {
      const el = render("bottom");
      expect(el.querySelector(".tooltip-container > .tooltip-chrome")).not.toBeNull();
      expect(el.querySelector(".widget__card-tooltip .tooltip-chrome")).toBeNull();
   });

   it("sizes the chrome to clear the tail and offsets it over the box", () => {
      const svg = render("bottom").querySelector(".tooltip-chrome") as SVGElement;
      expect(svg.getAttribute("width")).toBe("216");
      expect(svg.getAttribute("height")).toBe("116");
      expect((svg as unknown as HTMLElement).style.left).toBe("-5px");
      expect((svg as unknown as HTMLElement).style.top).toBe("-5px");
   });

   it("draws a background rect, an open border path and a tail path", () => {
      const el = render("bottom");
      expect(el.querySelector(".tooltip-chrome__bg").getAttribute("rx")).toBe("8");
      expect(el.querySelector(".tooltip-chrome__border").getAttribute("d")).toContain("Q");
      expect(el.querySelector(".tooltip-chrome__tail").getAttribute("d"))
         .toBe("M51.3,108 L58,116 L64.7,108");
   });

   it("marks the box as tailed so the skin drops its own background and border", () => {
      const box = render("bottom").querySelector(".widget__card-tooltip");
      expect(box.classList.contains("widget__card-tooltip--tailed")).toBe(true);
   });

   it("moves the tail to the matching edge for each side", () => {
      expect(render("top").querySelector(".tooltip-chrome__tail").getAttribute("d"))
         .toBe("M64.7,8 L58,0 L51.3,8");
      expect(render("left").querySelector(".tooltip-chrome__tail").getAttribute("d"))
         .toBe("M8,64.7 L0,58 L8,51.3");
      expect(render("right").querySelector(".tooltip-chrome__tail").getAttribute("d"))
         .toBe("M208,51.3 L216,58 L208,64.7");
   });
});
```

- [ ] **Step 2: Run the test to verify it fails**

```bash
npx ng test portal --include="**/tooltip.component.spec.ts"
```

Expected: FAIL — `tailSide` / `boxSize` are not properties of `TooltipComponent`.

- [ ] **Step 3a: Add the inputs and chrome accessor**

Replace the body of `web/projects/portal/src/app/widget/tooltip/tooltip.component.ts` from the `import` block down, keeping the license header:

```ts
import {
   ChangeDetectionStrategy,
   ChangeDetectorRef,
   Component,
   Input,
   TemplateRef
} from "@angular/core";
import { NgTemplateOutlet, NgClass } from "@angular/common";
import {
   buildChromePaths,
   ChromeGeometry,
   TAIL_LENGTH,
   TailSide,
   TOOLTIP_INSET
} from "./tooltip-tail-placement";

/**
 * Component used to render tooltips.
 *
 * !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
 * !!!! NOT INTENDED for direct use in the template. !!!!!
 * !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
 */
@Component({
    selector: "w-tooltip",
    templateUrl: "tooltip.component.html",
    styleUrls: ["tooltip.component.scss"],
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [NgTemplateOutlet, NgClass]
})
export class TooltipComponent {
   @Input() content: string | TemplateRef<any>;
   @Input() tooltipCSS: string | string[] | Set<string>;
   @Input() tailSide: TailSide | null = null;
   @Input() tailOffset = 0;
   @Input() boxSize: { width: number, height: number } | null = null;
   /** The chrome extends past the box by the tail's length, less the box's own inset. */
   readonly chromeInset = TOOLTIP_INSET - TAIL_LENGTH;

   constructor(private changeRef: ChangeDetectorRef) {
   }

   updateView() {
      this.changeRef.detectChanges();
   }

   contentIsTemplate(): boolean {
      return this.content instanceof TemplateRef;
   }

   get chrome(): ChromeGeometry | null {
      return !!this.tailSide && !!this.boxSize
         ? buildChromePaths(this.boxSize.width, this.boxSize.height, this.tailSide, this.tailOffset)
         : null;
   }
}
```

- [ ] **Step 3b: Render the chrome**

Replace the markup in `web/projects/portal/src/app/widget/tooltip/tooltip.component.html` below the license comment:

```html
@if (contentIsTemplate()) {
  <div class="compact-p tooltip-container">
    <ng-container *ngTemplateOutlet="content"></ng-container>
  </div>
} @else {
  <div class="compact-p tooltip-container">
    @if (chrome; as c) {
      <svg class="tooltip-chrome" [attr.width]="c.width" [attr.height]="c.height"
           [attr.viewBox]="'0 0 ' + c.width + ' ' + c.height"
           [style.left.px]="chromeInset" [style.top.px]="chromeInset">
        <rect class="tooltip-chrome__bg" [attr.x]="c.boxX" [attr.y]="c.boxY"
              [attr.width]="c.boxWidth" [attr.height]="c.boxHeight" [attr.rx]="c.radius"></rect>
        <path class="tooltip-chrome__border" [attr.d]="c.borderPath"></path>
        <path class="tooltip-chrome__tail" [attr.d]="c.tailPath"></path>
      </svg>
    }
    <div [ngClass]="tooltipCSS" [class.widget__card-tooltip--tailed]="!!chrome"
         [innerHTML]="content"></div>
  </div>
}
```

- [ ] **Step 3c: Style the chrome**

Append to `web/projects/portal/src/app/widget/tooltip/tooltip.component.scss`:

```scss
.tooltip-container {
  position: relative;
}

.tooltip-chrome {
  position: absolute;
  overflow: visible;
  pointer-events: none;
  // Mirrors --inet-shadow-low, but follows the tail as well as the box.
  filter: drop-shadow(0 1px 2px rgba(0, 0, 0, .06)) drop-shadow(0 1px 3px rgba(0, 0, 0, .04));
}

.tooltip-chrome__bg,
.tooltip-chrome__tail {
  fill: var(--inet-dialog-bg-color);
}

.tooltip-chrome__border,
.tooltip-chrome__tail {
  stroke: var(--inet-default-border-color);
  stroke-width: 1;
}

.tooltip-chrome__border {
  fill: none;
}
```

- [ ] **Step 3d: Add the skin modifier**

In `web/projects/portal/src/scss/internal/_directives.scss`, insert immediately after the closing brace of `.widget__default-tooltip` (line 221) and before `.widget__card-tooltip`:

```scss
// The SVG chrome draws the outline when a tail is present.
.widget__card-tooltip--tailed {
  background-color: transparent;
  border-color: transparent;
  box-shadow: none;
}
```

- [ ] **Step 4: Run the test to verify it passes**

```bash
npx ng test portal --include="**/tooltip.component.spec.ts"
```

Expected: PASS, 7 tests.

- [ ] **Step 5: Commit**

```bash
git add web/projects/portal/src/app/widget/tooltip/tooltip.component.ts \
        web/projects/portal/src/app/widget/tooltip/tooltip.component.html \
        web/projects/portal/src/app/widget/tooltip/tooltip.component.scss \
        web/projects/portal/src/app/widget/tooltip/tooltip.component.spec.ts \
        web/projects/portal/src/scss/internal/_directives.scss
git commit -m "feat(tooltip): render seamless tail chrome in TooltipComponent"
```

---

### Task 6: Tail placement branch in TooltipDirective

**Files:**
- Modify: `web/projects/portal/src/app/widget/tooltip/tooltip.directive.ts`
- Test: `web/projects/portal/src/app/widget/tooltip/tooltip.directive.spec.ts`

**Interfaces:**
- Consumes: `computeTailPlacement`, `TailAxis`, `TOOLTIP_INSET` from Task 1; the `TooltipComponent` inputs from Task 5.
- Produces, on `TooltipDirective`:
  - `@Input() showTail = false`
  - `@Input() tailAxis: TailAxis = "vertical"`
  - `@Input() tailAnchor: () => Rectangle | null = null`

**Background the implementer needs:**

`positionTooltipWithinViewport()` (`tooltip.directive.ts:192-233`) is the only placement path today. It puts the box down-right of the cursor and then *clamps* on overflow — clamping, not flipping, which is why the cursor can end up inside the box. Leave that code exactly as it is: it is the fallback, and every non-chart caller depends on it.

The new branch runs first and returns a boolean saying whether it handled placement.

Two behavioral notes:

- The anchor is the **hovered mark's rect**, supplied by the caller as a provider function, not the cursor. The provider is called inside `positionTooltipWithinViewport()`, which already runs outside Angular — a function rather than an `@Output` keeps change detection out of the mousemove path.
- Because the anchor is the mark, placement must be **skipped while the anchor is unchanged**, so the box holds still as the pointer moves across one mark. Reset the cached key whenever the tooltip closes or its content changes, or a re-shown tooltip would keep a stale position.

- [ ] **Step 1: Write the failing test**

Create `web/projects/portal/src/app/widget/tooltip/tooltip.directive.spec.ts`:

```ts
/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
import { Component } from "@angular/core";
import { ComponentFixture, TestBed } from "@angular/core/testing";
import { Rectangle } from "../../common/data/rectangle";
import { TooltipDirective } from "./tooltip.directive";

@Component({
   standalone: true,
   imports: [TooltipDirective],
   template: `<div [wTooltip]="'hover text'" [showTail]="showTail" [tailAxis]="'vertical'"
                   [tailAnchor]="anchor" [followCursor]="true" [waitTime]="0"></div>`
})
class HostComponent {
   showTail = true;
   rect: Rectangle | null = new Rectangle(500, 400, 20, 20);
   anchor = () => this.rect;
}

function rect(left: number, top: number, width: number, height: number): DOMRect {
   return {
      left, top, width, height, right: left + width, bottom: top + height,
      x: left, y: top, toJSON: () => ({})
   } as DOMRect;
}

describe("TooltipDirective tail placement", () => {
   let fixture: ComponentFixture<HostComponent>;
   let host: HTMLElement;

   beforeEach(async() => {
      // jsdom has no layout; place the container at 1000x600 and the tooltip host at 206x106.
      vi.spyOn(Element.prototype, "getBoundingClientRect").mockImplementation(function(this: Element) {
         return this.tagName.toLowerCase() === "w-tooltip" ? rect(0, 0, 206, 106) : rect(0, 0, 1000, 600);
      });

      await TestBed.configureTestingModule({ imports: [HostComponent] }).compileComponents();
      fixture = TestBed.createComponent(HostComponent);
      fixture.detectChanges();
      host = fixture.nativeElement.querySelector("div");
   });

   afterEach(() => {
      document.querySelectorAll("w-tooltip").forEach(e => e.remove());
      vi.restoreAllMocks();
   });

   function hover(clientX: number, clientY: number): HTMLElement {
      host.dispatchEvent(new MouseEvent("mouseenter"));
      host.dispatchEvent(new MouseEvent("mousemove", { clientX, clientY }));
      return document.querySelector("w-tooltip");
   }

   it("places the tooltip from the anchor rect, not the cursor", () => {
      // Anchor centre is (510, 410); box goes above it, so top = 410 - 8 - 100 - 3.
      const tip = hover(120, 120);
      expect(tip.style.top).toBe("299px");
      expect(tip.style.left).toBe("407px");
   });

   it("renders a tail on the edge facing the anchor", () => {
      expect(hover(120, 120).querySelector(".tooltip-chrome__tail")).not.toBeNull();
   });

   it("does not reposition while the anchor rect is unchanged", () => {
      const tip = hover(120, 120);
      const top = tip.style.top;
      host.dispatchEvent(new MouseEvent("mousemove", { clientX: 300, clientY: 300 }));
      expect(tip.style.top).toBe(top);
   });

   it("repositions when the anchor rect changes", () => {
      const tip = hover(120, 120);
      fixture.componentInstance.rect = new Rectangle(500, 40, 20, 20);
      host.dispatchEvent(new MouseEvent("mousemove", { clientX: 121, clientY: 121 }));
      expect(tip.style.top).not.toBe("299px");
   });

   it("falls back to cursor placement with no tail when there is no anchor", () => {
      fixture.componentInstance.rect = null;
      const tip = hover(120, 120);
      expect(tip.style.left).toBe("135px");
      expect(tip.style.top).toBe("135px");
      expect(tip.querySelector(".tooltip-chrome")).toBeNull();
   });

   it("uses cursor placement unchanged when showTail is off", () => {
      fixture.componentInstance.showTail = false;
      fixture.detectChanges();
      const tip = hover(120, 120);
      expect(tip.style.left).toBe("135px");
      expect(tip.style.top).toBe("135px");
      expect(tip.querySelector(".tooltip-chrome")).toBeNull();
   });
});
```

- [ ] **Step 2: Run the test to verify it fails**

```bash
npx ng test portal --include="**/tooltip.directive.spec.ts"
```

Expected: FAIL — `showTail` / `tailAnchor` are not known inputs of `TooltipDirective`.

- [ ] **Step 3a: Add the imports and inputs**

In `web/projects/portal/src/app/widget/tooltip/tooltip.directive.ts`, add to the existing import block:

```ts
import { computeTailPlacement, TailAxis, TOOLTIP_INSET } from "./tooltip-tail-placement";
```

Add after `@Input() disableTooltipOnMousedown = true;` (line 48):

```ts
   @Input() showTail = false;
   @Input() tailAxis: TailAxis = "vertical";
   /** Supplies the hovered mark's rect; the tail is suppressed when it yields null. */
   @Input() tailAnchor: () => Rectangle | null = null;
```

Add to the private fields, after `private mousePosition: Point;` (line 50):

```ts
   private lastAnchorKey: string = null;
```

- [ ] **Step 3b: Reset the cached anchor when the tooltip changes or closes**

In `ngOnChanges`, inside the `else if(this.tooltipShowing())` branch (line 69-71), add the reset:

```ts
         else if(this.tooltipShowing()) {
            this.lastAnchorKey = null;
            this.tooltipRef.instance.content = this.getTooltipContent();
         }
```

In `close()` (line 156), add the reset before the existing body:

```ts
   public close() {
      this.lastAnchorKey = null;

      if(this.tooltipShowing()) {
         this.tooltipService.removeTooltip(this.tooltipRef);
      }

      this.clearTimeout();
   }
```

- [ ] **Step 3c: Add the tail branch**

In `positionTooltipWithinViewport()`, insert at the top of the `if(!!container)` block (immediately after line 197's opening brace), before `const tooltipBounds = ...`:

```ts
            if(this.showTail && this.positionWithTail(tooltipElement, container)) {
               return;
            }
```

Then add these two private methods immediately after `positionTooltipWithinViewport()`:

```ts
   /**
    * Place the box against the hovered mark's rect with a tail pointing at it. Returns false when
    * no anchor is available or the box cannot clear the anchor, leaving the cursor-offset path to
    * position it.
    */
   private positionWithTail(tooltipElement: HTMLElement, container: HTMLElement): boolean {
      const anchorRect = !!this.tailAnchor ? this.tailAnchor() : null;

      if(!anchorRect) {
         this.applyTail(null, 0, null);
         return false;
      }

      const key = `${anchorRect.x},${anchorRect.y},${anchorRect.width},${anchorRect.height}`;

      // The anchor is the mark, so the box holds still while the cursor moves across it.
      if(key === this.lastAnchorKey) {
         return true;
      }

      const bounds = Rectangle.fromClientRect(tooltipElement.getBoundingClientRect());
      const placement = computeTailPlacement({
         anchor: {
            x: anchorRect.x + anchorRect.width / 2,
            y: anchorRect.y + anchorRect.height / 2
         },
         hostWidth: bounds.width,
         hostHeight: bounds.height,
         container: Rectangle.fromClientRect(container.getBoundingClientRect()),
         axis: this.tailAxis
      });

      if(!placement) {
         this.lastAnchorKey = null;
         this.applyTail(null, 0, null);
         return false;
      }

      this.lastAnchorKey = key;
      this.renderer.setStyle(tooltipElement, "top", placement.y + "px");
      this.renderer.setStyle(tooltipElement, "left", placement.x + "px");
      this.applyTail(placement.tailSide, placement.tailOffset, {
         width: bounds.width - 2 * TOOLTIP_INSET,
         height: bounds.height - 2 * TOOLTIP_INSET
      });

      return true;
   }

   private applyTail(tailSide: TailSide | null, tailOffset: number,
                     boxSize: { width: number, height: number } | null): void
   {
      const tooltip = this.tooltipRef.instance;
      tooltip.tailSide = tailSide;
      tooltip.tailOffset = tailOffset;
      tooltip.boxSize = boxSize;
      tooltip.updateView();
   }
```

Extend the placement import to bring in `TailSide` as well:

```ts
import { computeTailPlacement, TailAxis, TailSide, TOOLTIP_INSET } from "./tooltip-tail-placement";
```

- [ ] **Step 4: Run the test to verify it passes**

```bash
npx ng test portal --include="**/tooltip.directive.spec.ts"
```

Expected: PASS, 6 tests.

- [ ] **Step 5: Run the neighbouring suites to confirm no regression**

```bash
npx ng test portal --include="**/tooltip*.spec.ts"
```

Expected: PASS, all 32 tests across the three tooltip specs.

- [ ] **Step 6: Commit**

```bash
git add web/projects/portal/src/app/widget/tooltip/tooltip.directive.ts \
        web/projects/portal/src/app/widget/tooltip/tooltip.directive.spec.ts
git commit -m "feat(tooltip): place the box against the hovered mark when showTail is on"
```

---

### Task 7: Expose the hovered mark's rect from the inline-SVG tile

**Files:**
- Modify: `web/projects/portal/src/app/graph/objects/chart-inline-svg.directive.ts`
- Test: `web/projects/portal/src/app/graph/objects/chart-inline-svg.directive.spec.ts`

**Interfaces:**
- Consumes: the directive's existing `elementGroupMap: Map<string, Element>` (declared at `:83`, populated by `afterSvgInjected()`), keyed `` `${row}-${col}` ``.
- Produces: `getElementRect(row: number, col: number): DOMRect | null` on `ChartInlineSvgDirective`.

**Background the implementer needs:**

`highlightElements()` (`:281`) already resolves `{row, col}` pairs against `elementGroupMap` using the key `` `${row}-${col}` ``. This adds a read-only sibling that returns the element's rect instead of toggling a class. Use the exact same key format — a mismatch would silently return null for every mark.

The existing spec constructs the directive directly (`new ChartInlineSvgDirective(new ElementRef(host), {} as any)`) and calls `(dir as any).afterSvgInjected()` to populate the maps. Follow that pattern.

- [ ] **Step 1: Write the failing test**

Append a new `describe` block to `web/projects/portal/src/app/graph/objects/chart-inline-svg.directive.spec.ts`, inside the top-level `describe("ChartInlineSvgDirective cross-tile dim", ...)` block, immediately before its closing `});`:

```ts
   describe("getElementRect (tooltip tail anchor)", () => {
      const html = `
         <svg>
            <g class="inetsoft-bar" data-row="0" data-col="0"></g>
            <g class="inetsoft-bar" data-row="1" data-col="0"></g>
         </svg>`;

      it("returns the rect of the element for a known row/col", () => {
         const { dir, host } = makeDirective(html);
         (dir as any).afterSvgInjected();
         const target = host.querySelector<Element>("[data-row='1']");
         vi.spyOn(target, "getBoundingClientRect")
            .mockReturnValue({ left: 10, top: 20, width: 30, height: 40 } as DOMRect);
         expect(dir.getElementRect(1, 0)).toMatchObject({ left: 10, top: 20, width: 30, height: 40 });
      });

      it("returns null for a row/col with no element", () => {
         const { dir } = makeDirective(html);
         (dir as any).afterSvgInjected();
         expect(dir.getElementRect(9, 9)).toBeNull();
      });

      it("returns null before the svg has been indexed", () => {
         const { dir } = makeDirective(html);
         expect(dir.getElementRect(0, 0)).toBeNull();
      });
   });
```

- [ ] **Step 2: Run the test to verify it fails**

```bash
npx ng test portal --include="**/chart-inline-svg.directive.spec.ts"
```

Expected: FAIL — `dir.getElementRect is not a function`.

- [ ] **Step 3: Write the implementation**

In `web/projects/portal/src/app/graph/objects/chart-inline-svg.directive.ts`, add immediately after `highlightElement()` (which ends at line 272):

```ts
   /**
    * Bounding rect of the data element at rowIdx+colIdx, or null when this tile holds no such
    * element. Same key lookup as {@link highlightElements}; used to anchor the tooltip tail.
    */
   getElementRect(rowIdx: number, colIdx: number): DOMRect | null {
      const el = this.elementGroupMap.get(`${rowIdx}-${colIdx}`);
      return !!el ? el.getBoundingClientRect() : null;
   }
```

- [ ] **Step 4: Run the test to verify it passes**

```bash
npx ng test portal --include="**/chart-inline-svg.directive.spec.ts"
```

Expected: PASS — the three new tests plus every pre-existing test in the file.

- [ ] **Step 5: Commit**

```bash
git add web/projects/portal/src/app/graph/objects/chart-inline-svg.directive.ts \
        web/projects/portal/src/app/graph/objects/chart-inline-svg.directive.spec.ts
git commit -m "feat(chart): expose inline-svg mark rects for tooltip anchoring"
```

---

### Task 8: Capture the hovered mark's rect in the plot area

**Files:**
- Modify: `web/projects/portal/src/app/graph/objects/chart-plot-area.component.ts`

**Interfaces:**
- Consumes: `getElementRect()` from Task 7; `Rectangle` from `../../common/data/rectangle`.
- Produces: `tailAnchor: Rectangle | null` — a public field on `ChartPlotArea`, updated on every hover in inline-SVG mode and cleared when nothing is hovered.

**Background the implementer needs:**

`onMove()` (starting at `chart-plot-area.component.ts:688`) already does all the hard work. Inside its `if(this.inlineSvg)` block (`:811`) it resolves `voRegion` — the hovered bar/point/slice — and converts it to `rowIdx`/`colIdx` before handing them to the tiles. Two shapes come out of that block:

- the snapped stacked-bar path (`:826-830`), which builds a `pairs` array covering every segment of the hovered column;
- the single-mark path (`:832-835`), which uses one `rowIdx`/`colIdx`.

For the stacked case, the anchor should be the **union** of the column's rects so the tail points at the column, matching how the highlight already behaves. For the single case it is just that mark's rect.

The rect must come from whichever tile actually holds the element — a split chart has several tiles and only one will resolve a given key, so take the first non-null.

There is no test for this task: it is wiring inside a mousemove handler on a component with a large dependency graph, and every piece it composes (`getElementRect`, `computeTailPlacement`) is already covered. It is verified by the manual pass in Task 9.

- [ ] **Step 1: Add the import and the field**

Add to the imports in `chart-plot-area.component.ts` (next to the existing `Point` import at line 58):

```ts
import { Rectangle } from "../../common/data/rectangle";
```

Add a public field next to the other hover state, after `panning: Point = null;` (line 132):

```ts
   /** Rect of the mark under the cursor, in viewport coords; null when nothing is hovered. */
   tailAnchor: Rectangle = null;
```

- [ ] **Step 2: Set the anchor on the stacked-bar path**

In `onMove()`, in the branch that builds `pairs` for a snapped bar column (`:826-830`), add the anchor update after the existing `highlightElements` call:

```ts
               const pairs = this.collectColumnRegions(voRegion)
                  .map(r => ({ row: r.rowIdx, col: ChartTool.colIdx(this.model, r) }))
                  .filter(p => p.col >= 0);
               this.inlineSvgTiles?.forEach(d => d.highlightElements(pairs));
               this.tailAnchor = this.unionRect(pairs);
```

- [ ] **Step 3: Set the anchor on the single-mark path**

In the `else` branch (`:832-835`), after the existing `highlightElement` call:

```ts
               const rowIdx = voRegion != null ? voRegion.rowIdx : null;
               const colIdx = voRegion != null ? ChartTool.colIdx(this.model, voRegion) : null;
               this.inlineSvgTiles?.forEach(d => d.highlightElement(rowIdx, colIdx));
               this.tailAnchor = rowIdx != null && colIdx >= 0
                  ? this.unionRect([{ row: rowIdx, col: colIdx }]) : null;
```

- [ ] **Step 4: Add the union helper**

Add a private method after `onMove()`:

```ts
   /** Union of the given marks' rects across every tile; null when none resolve. */
   private unionRect(pairs: { row: number, col: number }[]): Rectangle {
      let left: number = null, top: number = null, right: number = null, bottom: number = null;

      for(const pair of pairs) {
         for(const tile of this.inlineSvgTiles || []) {
            const rect = tile.getElementRect(pair.row, pair.col);

            if(!rect) {
               continue;
            }

            left = left == null ? rect.left : Math.min(left, rect.left);
            top = top == null ? rect.top : Math.min(top, rect.top);
            right = right == null ? rect.right : Math.max(right, rect.right);
            bottom = bottom == null ? rect.bottom : Math.max(bottom, rect.bottom);
         }
      }

      return left == null ? null : new Rectangle(left, top, right - left, bottom - top);
   }
```

- [ ] **Step 5: Clear the anchor when the cursor leaves the plot**

`onLeave()` (`:1010`) already clears the inline-SVG highlight at `:1036-1041`. Add the anchor reset to that same block:

```ts
      if(this.inlineSvg) {
         this.inlineSvgTiles?.forEach(d => {
            d.highlightElement(null, null);
            d.highlightSnapSeries([]);
         });
         this.tailAnchor = null;
      }
```

The in-plot "nothing hovered" case needs no extra handling — Step 3 already sets `tailAnchor` to null whenever `voRegion` fails to resolve.

- [ ] **Step 6: Verify the file compiles**

```bash
npx ng test portal --include="**/chart-inline-svg.directive.spec.ts"
```

Expected: PASS. The build step compiles the whole project, so a type error in `chart-plot-area.component.ts` fails this run even though the spec does not exercise it.

- [ ] **Step 7: Commit**

```bash
git add web/projects/portal/src/app/graph/objects/chart-plot-area.component.ts
git commit -m "feat(chart): track the hovered mark's rect for the tooltip tail"
```

---

### Task 9: Wire the tail into the chart area

**Files:**
- Modify: `web/projects/portal/src/app/graph/objects/chart-area.component.ts`
- Modify: `web/projects/portal/src/app/graph/objects/chart-area.component.html:185-190`

**Interfaces:**
- Consumes: `tailAxisForChartType()` (Task 4); `TailAxis` (Task 1); `TooltipDirective`'s `showTail`/`tailAxis`/`tailAnchor` inputs (Task 6); `ChartPlotArea.tailAnchor` (Task 8); `ChartConfigService.inlineSvg`.
- Produces: nothing consumed by later tasks — this is the final wiring.

**Background the implementer needs:**

`chart-area.component.ts` already imports `GraphTypes` (`:44`) and `Rectangle` (`:42`), and already holds `@ViewChild("chartPlotArea") chartPlotArea: ChartPlotArea;` (`:281`), so the anchor provider needs no new plumbing.

The tail is gated on two conditions: the CARD skin, and inline-SVG mode. `ChartConfigService` (`graph/services/chart-config.service.ts:25`) is `providedIn: "root"` and exposes `inlineSvg`; `chart-plot-area` reads it the same way (`:189-190`). With `graph.svg.inline` off there are no per-mark elements, so there is no anchor and therefore no tail — the tooltip must render exactly as it does today.

`tooltipCSS` is already recomputed in `ngOnChanges` under `if(changes["model"] || changes["modelTS"])` (`:368-371`). Put the two new assignments in that same block so they stay in sync with the skin.

`tailAnchorFn` must be an arrow-function **field**, not a method: the directive stores and calls it without a receiver, so a plain method would lose `this`.

Only the plot-area binding gets these inputs. The axis binding at `:138` and the legend binding at `:275` are left alone.

- [ ] **Step 1: Add the imports**

In `web/projects/portal/src/app/graph/objects/chart-area.component.ts`, add to the import block:

```ts
import { ChartConfigService } from "../services/chart-config.service";
import { tailAxisForChartType } from "./chart-tail-config";
import { TailAxis } from "../../widget/tooltip/tooltip-tail-placement";
```

- [ ] **Step 2: Add the fields**

Next to the existing tooltip state (after `tooltipCSS` at line 307):

```ts
   tooltipTail: boolean = false;
   tooltipTailAxis: TailAxis = "vertical";
   /** Bound to the tooltip directive, which calls it outside Angular on every hover. */
   tailAnchorFn = () => this.chartPlotArea?.tailAnchor ?? null;
```

- [ ] **Step 3: Inject the config service**

Add a parameter to the existing constructor (after `private ngZone: NgZone`):

```ts
   constructor(private chartService: ChartService,
               private dndService: DndService,
               private scaleService: ScaleService,
               private changeDetectorRef: ChangeDetectorRef,
               private pagingControlService: PagingControlService,
               protected renderer: Renderer2,
               private ngZone: NgZone,
               private chartConfigService: ChartConfigService)
   {
```

- [ ] **Step 4: Compute the gate and the axis**

Extend the existing `ngOnChanges` block at lines 368-371:

```ts
      if(changes["model"] || changes["modelTS"]) {
         this.tooltipCSS = this.model && this.model.tooltipStyle === "CARD"
            ? "widget__card-tooltip" : "widget__default-tooltip";
         // The tail needs a mark rect, which only inline-svg tiles provide.
         this.tooltipTail = this.model && this.model.tooltipStyle === "CARD" &&
            this.chartConfigService.inlineSvg;
         this.tooltipTailAxis = tailAxisForChartType(this.model?.chartType);
      }
```

- [ ] **Step 5: Bind on the plot area only**

In `web/projects/portal/src/app/graph/objects/chart-area.component.html`, add three bindings to the `<chart-plot-area>` element, after `[waitTime]="0"` (line 189):

```html
              [showTail]="tooltipTail"
              [tailAxis]="tooltipTailAxis"
              [tailAnchor]="tailAnchorFn"
```

- [ ] **Step 6: Run the affected suites**

```bash
npx ng test portal --include="**/chart-area.component.*.spec.ts"
npx ng test portal --include="**/chart-plot-area.component.*.spec.ts"
```

Expected: PASS. These are the existing `*.tl.spec.ts` suites for both components — they must be unchanged by this wiring.

- [ ] **Step 7: Run the full portal suite**

```bash
npm run test:portal
```

Expected: PASS. Report any pre-existing failures separately rather than "fixing" them here — `main` is trunk-based and some specs are known to be unstable.

- [ ] **Step 8: Manual verification**

Build and start the server with inline SVG on:

```bash
./mvnw clean install -DskipTests
cd docker/target/docker-test && docker compose up -d
```

In the EM, set the `graph.svg.inline` property to `true` (Enterprise Manager → Settings → Properties — it is a `SreeEnv` property, **not** a yaml `additionalProperties` entry). Then open a viewsheet with a chart whose Tooltip style is set to Card, and check:

- Hover marks near the top, bottom, left and right of the plot — the tail stays over the mark and slides along the box's edge as the box is clamped by the container.
- The box holds still while the cursor moves within one mark.
- Light and dark theme: the box and tail read as one shape, with no seam or double border at the tail's base.
- A pie/donut, a bar and a line chart (vertical axis), plus a treemap or network chart (horizontal axis).
- A stacked bar under snap-tooltip: the tail points at the column, not one segment.
- A maximized chart and a chart near the browser edge.
- Set the chart's tooltip style back to the default: plain box, no tail.
- Set `graph.svg.inline` to `false`: every chart tooltip renders exactly as before, no tail anywhere.

- [ ] **Step 9: Commit**

```bash
git add web/projects/portal/src/app/graph/objects/chart-area.component.ts \
        web/projects/portal/src/app/graph/objects/chart-area.component.html
git commit -m "feat(chart): enable the tooltip tail on the plot area in inline-svg mode"
```

---

## Deviations from the design doc

Both are simplifications found while working the geometry; neither changes the visible result.

1. **The reference's "safety flip" is omitted** (Task 1). `chart-shared.js:183-189` re-flips the box when clamping made it engulf the anchor. Here the room test and the clamp use the same rect, so every case that triggers the flip also fails after flipping — the flip is unreachable. `computeTailPlacement` returns `null` instead, and the caller falls back to cursor placement with no tail.

2. **`GAP` and `TAIL_LENGTH` are both 8** (Task 1), so the horizontal and vertical branches use the same anchor-to-box distance. The reference distinguishes them (`TAIL=12`, `gap=14`) because its side-mode tooltips also carry an `anchorInset` for marks with radius. Nothing here needs that, and one constant is easier to tune.

3. **The `tooltipTail` gate has no unit test** (Task 9). The design doc's test list asks for one asserting it is true only for the CARD skin in inline-SVG mode. `chart-area.component.ts` has no plain spec harness — only the `*.tl.spec.ts` integration suites, which render the full component tree — and standing one up for a two-line boolean is not worth the fixture. Both flag states are checked explicitly in Task 9's manual pass instead. The other half of that request, the axis map, *is* unit-tested (Task 4).

## Follow-ups explicitly out of scope

- Enabling the tail without inline-SVG mode. There are no per-mark elements in image-tile mode, so there is no anchor.
- Tail support for the default (non-CARD) tooltip skin, for axis/legend tooltips, or for the template-content branch of `TooltipComponent`.
- Re-tuning `TAIL_RADIUS`/`TAIL_LENGTH`/`TAIL_HALF_WIDTH`/`TAIL_GAP`. The values preserve the reference's proportions at the card skin's 8px radius, but the reference's numbers are SVG user units at an unknown render scale. If the tail reads too heavy or too light in Task 9's manual pass, adjust the four constants in `tooltip-tail-placement.ts` — nothing else needs to change.

---

## Post-delivery: anchor coverage requirement

The tail needs a per-mark **anchor point**. Where that comes from decides which chart types
can show a tail at all, and this was not understood when the plan was written.

**Anchor sources, in priority order:**

1. `data-anchor` on the mark's annotation group — the renderer's own visual middle. Emitted by
   `BarVO` for arcs only, because a wide donut wedge's bounding-box centre lands in the hole.
2. The mark element's bounding-box centre — correct for any rectangular mark.

Both require a DOM element carrying `data-row` **and** `data-col`. Surveying the renderer:

| VO | Emits | Anchorable |
|---|---|---|
| `BarVO` (bars, stacked segments, pie/donut slices) | `data-row`, `data-col`, `data-anchor` | yes |
| `PointVO` (scatter points, line/radar vertex markers) | `data-row`, `data-col` | yes |
| `AreaVO`, `LineVO` | `data-series`, `data-color` only | **no** |

**Consequence — the point-marker requirement.** Line, radar and filled-radar charts have no
anchorable element of their own; they anchor to their `.inetsoft-point` vertex markers. So the
tail appears on those types **only when point markers are drawn**. With markers off there is
no per-point element and the tooltip correctly falls back to plain cursor placement.

**Known gap — area and stacked area.** Worse than conditional: the "show points" checkbox is
not offered for these types in Advanced chart properties, so a user cannot turn markers on.
An area chart therefore can never show a tail through the DOM anchor path. The area fill is
also not a substitute — it carries no row/col and spans many rows, so there is no single mark
to point at.

**Candidate fix (not implemented).** `ChartRegion` already carries per-region geometry on the
client for every chart type, independent of the SVG: `pts` is always serialized, and
`centroid` is available too. Adding the region centroid as a third fallback below the two
sources above would close the area gap without any point markers, and would also make the
anchor work where the SVG index is empty. Two caveats: `centroid` is a bounding-box centre
(`GraphBuilder.java:1028`), so it must stay *below* `data-anchor` in priority or wide arcs
regress; and it is currently serialized only when `hasCurve || showReferenceLine ||
snapTooltip` (`GraphBuilder.java:1446`), so sending it unconditionally costs two doubles per
region on every chart.

### Resolution: region-centroid fallback (implemented)

`GraphBuilder` now serializes `centroid` unconditionally, guarded by a new `hasCentroid` flag
so a degenerate region publishes `null` rather than `(0,0)` — which would otherwise drag
anything anchored to it into the viewport corner. The three locals the old condition needed
(`hasCurve`, `showReferenceLine`, `snapTooltip`) became dead and were removed; two of them ran
per region and `hasCurve` ran per path segment, so the change is a small net CPU reduction.

The client resolves an anchor in three steps, and the order is load-bearing:

1. `data-anchor` on the mark's group — the renderer's visual middle (arcs).
2. The mark element's bounding-box centre (rectangular marks).
3. `ChartRegion.centroid`, mapped to viewport px by `ChartTool.regionPointToViewport`.

Step 3 must stay last: a centroid is a bounding-box centre, so promoting it above step 1 would
put a wide donut wedge's tail back in the hole.

`regionPointToViewport` is the inverse of what `drawRegions` applies — that method transforms
by `devicePixelRatio * scale` then translates by `-offset`, so in CSS px a region coordinate
sits at `(coord - offset) * scale` from the canvas origin. It is a pure function with its own
spec rather than arithmetic buried in the component.

**Payload cost** (area chart, 8 series x 20 points ~= 200 regions): `centroid` is a
`java.awt.Point`, so `setLocation(double,double)` rounds to ints and it serializes as
`"centroid":{"x":1234,"y":567}` ~= 30 bytes. ~6 KB uncompressed, 1-2 KB gzipped since the key
text is identical across regions. Against a region's own `pts` array — raw unrounded doubles
straight from PathIterator, ~40 bytes per coordinate pair — that is roughly an 8-15% per-region
increase. Cost is linear in region count and now unconditional, so a very high-cardinality
chart (~50k points) would add ~1.5 MB uncompressed; if that ever matters, restrict the emission
to the line/area families, which are the only ones lacking a DOM anchor.
