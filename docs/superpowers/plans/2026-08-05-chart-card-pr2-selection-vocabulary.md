# Chart Card PR 2 — Selection Vocabulary Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give the product one way of drawing "selected" — the shipped focus family — and stop chrome selections from flooding the regions they mark.

**Architecture:** One design decision with two implementations: `ChartTool.drawRegions()` for canvas-drawn chart regions and the annotation border for DOM overlays. They ship in a single changeset because either alone leaves the product with two selection idioms, which is the thing the decision was taken to end. Chart canvas colours are settable from CSS through an existing `getComputedStyle` branch, so the palette change is a stylesheet edit; the fill/stroke split needs one new parameter threaded through three call sites.

**Tech Stack:** Angular 21.2 + TypeScript 5.9, SCSS, Canvas 2D, Vitest 4.1.7.

## Global Constraints

- **Design source of truth:** `docs/superpowers/specs/lookfeel/chart-card-slice1-design.md` §4. Drawn in `chart-card-design/Chart overlay surfaces - decided visuals.html` §01 — **read that before implementing**; a layout described in prose gets built two ways.
- **Ticket item 9's central conclusion is wrong**, and this PR is the one most affected by it. The correction is restated below; the full list of source-doc errors is in `docs/superpowers/specs/lookfeel/chart-card-source-doc-corrections.md` §1.1.
- **Ship both halves together.** `Chart overlay surfaces - ticket.md` items 7–8 **and** `Shell surfaces - ticket.md` item 11 are one PR. Do not split.
- **Everything colour-related is gated** behind `.viz-modern`. Only the two dead-rule deletions are unconditional.
- **The decided vocabulary:** fill → `--inet-focus-ring-color` (`rgba(229, 138, 42, 0.28)`), stroke → `--inet-primary-color` (`#E58A2A`), `lineWidth` 2. Chrome selections are **stroke-only**; data selections keep the fill.
- **Chrome areas (10):** `bottom_x_axis`, `top_x_axis`, `left_y_axis`, `right_y_axis`, `legend_content`, `legend_title`, `x_title`, `x2_title`, `y_title`, `y2_title`. **Data area (1):** `plot_area`.
- **Gate reader:** `GuiTool.isVizModern()` (`app/common/util/gui-tool.ts:65`) for TypeScript; `.viz-modern` descendant selectors for global CSS.
- **Frontend test commands:** `cd web && npm run test:portal`, `npm run test:portal:tl`, `npm run lint`.

## Source-doc correction this plan carries

**Ticket item 9 is wrong that the CSS override path is dead.** It concludes "nothing anywhere sets
`color` or `border-color` on `.chart-object-canvas`", having surveyed only the four per-area component
stylesheets. `scss/_themeable.scss:1401-1404` sets both:

```scss
.chart-object-canvas {
  color: rgba(220, 88, 30, 0.3);
  border-color: #dc581e;
}
```

Those are the *same* values as the TypeScript defaults at `chart-tool.ts:778-779`, which is why nobody
noticed: whether the `getComputedStyle` branch fires or not, today's rendering is identical.

Two consequences for this plan:

1. Item 8 is a **value change in an existing themeable rule**, not the first execution of a dead branch.
   The ticket's "wants one manual pass rather than being treated as a no-risk token swap" framing was
   based on the dead-branch reading and does not apply. The manual pass is still wanted, for item 7.
2. **Which path is actually live must be established empirically** (Task 1), because if the branch does
   *not* fire, a CSS-only change has no effect at all and the TypeScript defaults must be gated too.

---

## File Structure

| File | Responsibility in this PR |
|---|---|
| `web/projects/portal/src/scss/_themeable.scss:1401` | The existing `.chart-object-canvas` colour rule + the gated override |
| `web/projects/portal/src/app/graph/model/chart-tool.ts` | `drawRegions()` — the new chrome/data parameter, the fill suppression, `drawTouch()`'s literal |
| `web/projects/portal/src/app/graph/model/chart-tool-selection.spec.ts` | **New** — unit coverage for the chrome/data classification |
| `web/projects/portal/src/app/graph/objects/chart-object-area-base.ts:157` | Call site 1 — passes `this._chartObject.areaName` |
| `web/projects/portal/src/app/graph/objects/chart-area.component.ts:592` | Call site 2 — passes `payload.chartObject.areaName` |
| `web/projects/portal/src/app/vsobjects/objects/annotation/vs-annotation.component.scss` | The selected border, the hardcoded text colour, the dead `.annotition-button` rule |
| `web/projects/portal/src/app/graph/objects/chart-legend-container.component.scss:32` | Dead `.chart-legend__canvas` rule — delete |

---

## Task 1: Establish which colour path is live

**Files:** none modified. This is a measurement task, and the rest of the PR depends on its answer.

**Interfaces:**
- Consumes: nothing.
- Produces: a recorded answer — "CSS branch fires" or "TypeScript defaults win" — which decides whether Task 3 needs to gate `chart-tool.ts:778-779` as well as the stylesheet.

- [ ] **Step 1: Read the branch condition**

Run:
```bash
cd community/web/projects/portal/src && sed -n '775,800p' app/graph/model/chart-tool.ts
```
Expected: `fillStyle`/`strokeStyle` defaults, then a condition requiring `canvasCssStyle.color` and
`canvasCssStyle.borderColor` to be truthy **and** for the pair not to equal `body`'s pair.

- [ ] **Step 2: Confirm the global rule exists**

Run:
```bash
cd community/web/projects/portal/src && sed -n '1401,1405p' scss/_themeable.scss
```
Expected: the `.chart-object-canvas` rule with `color: rgba(220, 88, 30, 0.3)` and
`border-color: #dc581e`. If it is absent, the ticket was right and the branch is dead — record that and
skip to Step 5.

- [ ] **Step 3: Prove empirically whether the branch fires**

Temporarily change the `_themeable.scss` rule to an unmistakable colour:

```scss
.chart-object-canvas {
  color: rgba(0, 128, 0, 0.4);
  border-color: #00FF00;
}
```

Build (`cd community/web && npm run build`), start the app, open a chart, and select a bar.

Expected if the branch fires: the selection draws **green**.
Expected if it does not: the selection stays **orange**.

- [ ] **Step 4: Revert the probe**

```bash
cd community && git checkout -- web/projects/portal/src/scss/_themeable.scss
```

- [ ] **Step 5: Record the answer**

Write the result into the PR description draft:

- **Green** → the CSS branch is live. Task 3 is a stylesheet change only.
- **Orange** → the TypeScript defaults win. Task 3 must **additionally** gate
  `chart-tool.ts:778-779`, reading `GuiTool.isVizModern()` to pick between the legacy literals and the
  token values resolved via `getComputedStyle(document.documentElement)`.

No commit — nothing changed.

---

## Task 2: Thread the area name into `drawRegions` (item 7 mechanism)

**Files:**
- Modify: `web/projects/portal/src/app/graph/model/chart-tool.ts:763-766` (signature), `:745` (`drawReferenceLine`)
- Modify: `web/projects/portal/src/app/graph/objects/chart-object-area-base.ts:157`
- Modify: `web/projects/portal/src/app/graph/objects/chart-area.component.ts:592`
- Test: `web/projects/portal/src/app/graph/model/chart-tool-selection.spec.ts` (new)

**Interfaces:**
- Consumes: `ChartAreaName` from `app/graph/model/chart-area-name.ts`.
- Produces:
  - `ChartTool.isChromeArea(areaName: ChartAreaName): boolean` — exported from `chart-tool.ts`, used by Task 3.
  - `ChartTool.drawRegions(context, regions, offsetX, offsetY, currentScale?, scaleX?, scaleY?, drawReferLine?, areaName?)` — a **ninth optional positional** parameter, `areaName: ChartAreaName = null`. Existing callers that omit it keep today's behaviour (fill + stroke).

This task adds the parameter and the classifier but does **not** change any drawing yet, so it is safe on
its own and independently reviewable.

- [ ] **Step 1: Write the failing test for the classifier**

Create `web/projects/portal/src/app/graph/model/chart-tool-selection.spec.ts`:

```ts
import { ChartTool } from "./chart-tool";
import { ChartAreaName } from "./chart-area-name";

describe("ChartTool.isChromeArea", () => {
   const CHROME: ChartAreaName[] = [
      "bottom_x_axis", "top_x_axis", "left_y_axis", "right_y_axis",
      "legend_content", "legend_title", "x_title", "x2_title", "y_title", "y2_title"
   ];

   it("classifies all ten chrome areas as chrome", () => {
      for(const area of CHROME) {
         expect(ChartTool.isChromeArea(area)).toBe(true);
      }
   });

   it("classifies the plot area as data, not chrome", () => {
      expect(ChartTool.isChromeArea("plot_area")).toBe(false);
   });

   it("treats an unknown or absent area as data, so an un-migrated caller keeps its fill", () => {
      expect(ChartTool.isChromeArea(null)).toBe(false);
      expect(ChartTool.isChromeArea(undefined)).toBe(false);
   });
});
```

The third test encodes the safety property: a caller that does not pass an area name must not silently
lose its fill.

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd community/web && npm run test:portal -- --run chart-tool-selection`
Expected: FAIL with `ChartTool.isChromeArea is not a function`.

- [ ] **Step 3: Add the classifier to `chart-tool.ts`**

Inside the `ChartTool` namespace, above `drawRegions`:

```ts
   // Chrome selections mean "this component, so I can act on it"; a data selection means "these
   // values". The two are drawn differently — see drawRegions. Anything unrecognised is treated as
   // data so a caller that does not pass an area name keeps the fill it has today.
   const CHROME_AREAS: ReadonlySet<string> = new Set([
      "bottom_x_axis", "top_x_axis", "left_y_axis", "right_y_axis",
      "legend_content", "legend_title", "x_title", "x2_title", "y_title", "y2_title"
   ]);

   export function isChromeArea(areaName: ChartAreaName): boolean {
      return !!areaName && CHROME_AREAS.has(areaName);
   }
```

`ChartAreaName` is already imported at `chart-tool.ts:23`.

- [ ] **Step 4: Run the test to verify it passes**

Run: `cd community/web && npm run test:portal -- --run chart-tool-selection`
Expected: PASS, all three tests.

- [ ] **Step 5: Add the parameter to `drawRegions`**

```ts
   export function drawRegions(context: CanvasRenderingContext2D, regions: ChartRegion[],
                               offsetX: number, offsetY: number, currentScale?: number,
                               scaleX?: number, scaleY?: number,
                               drawReferLine: boolean = false,
                               areaName: ChartAreaName = null): void
```

Add to the doc comment above it:

```
    * @param areaName the area being drawn, used to decide whether the selection is chrome
    *                 (stroke only) or data (stroke plus fill). Omitted means data.
```

Do not use it yet.

- [ ] **Step 6: Pass the area name from call site 1**

`chart-object-area-base.ts:157` — the private `drawSelectedRegions()` already holds
`this._chartObject.areaName` (it uses it at line 143):

```ts
         // draw the region after convas is updated in case size changed.
         setTimeout(() => ChartTool.drawRegions(this.getContext(), regions, this.canvasX,
                                                this.canvasY, this.viewsheetScale, undefined,
                                                undefined, false, this._chartObject.areaName), 0);
```

- [ ] **Step 7: Pass the area name from call site 2**

`chart-area.component.ts:592` — `payload.chartObject` is in scope:

```ts
      ChartTool.drawRegions(payload.context, regions, payload.canvasX, payload.canvasY,
         this.scaleService.getCurrentScale(), undefined, undefined, false,
         payload.chartObject?.areaName);
```

- [ ] **Step 8: Leave `drawReferenceLine` alone, deliberately**

`chart-tool.ts:745` passes `drawReferLine: true` and no area name. A reference line is drawn inside the
plot and is data, so the `null` default is correct. Add a comment so the omission reads as intentional:

```ts
         // no areaName: a reference line is drawn in the plot, which is a data area
         ChartTool.drawRegions(context,
            [].concat(region), canvasX, canvasY, scale, undefined, undefined, true);
```

- [ ] **Step 9: Run the chart graph suites**

Run:
```bash
cd community/web && npm run test:portal -- --run chart-tool && npm run test:portal:tl -- --run chart-area
```
Expected: PASS. `chart-area.component.interaction.tl.spec.ts:366` spies on `ChartTool.drawRegions` with
a no-op mock and asserts only that it was or was not called, so extra arguments do not break it.

- [ ] **Step 10: Commit**

```bash
git add community/web/projects/portal/src/app/graph/model/chart-tool.ts \
        community/web/projects/portal/src/app/graph/model/chart-tool-selection.spec.ts \
        community/web/projects/portal/src/app/graph/objects/chart-object-area-base.ts \
        community/web/projects/portal/src/app/graph/objects/chart-area.component.ts
git commit -m "refactor(chart): thread the area name into drawRegions

Chart overlay surfaces ticket item 7, mechanism only — no drawing change yet.
drawRegions did not know which area it was drawing; the area name was already on
both callers, so this is a parameter rather than a refactor. Adds
ChartTool.isChromeArea() for the ten chrome areas, with anything unrecognised
classified as data so an un-migrated caller keeps its fill."
```

---

## Task 3: Bind the selection palette (item 8, gated)

**Files:**
- Modify: `web/projects/portal/src/scss/_themeable.scss:1401`
- Modify (only if Task 1 concluded "orange"): `web/projects/portal/src/app/graph/model/chart-tool.ts:778-779`

**Interfaces:**
- Consumes: Task 1's recorded answer.
- Produces: nothing later tasks depend on.

- [ ] **Step 1: Add the gated override beside the existing rule**

Leave the existing rule untouched — it is gate-off behaviour — and add immediately after it:

```scss
// Selection binds to the shipped focus family: a selection is a focus state, and #dc581e was an
// orphan orange in no palette. --inet-focus-ring-color is already the primary at ~30% alpha, which
// is what the old fill was approximating. drawRegions reads `color` as its fill and `border-color`
// as its stroke from this rule's computed style.
.viz-modern .chart-object-canvas {
  color: var(--inet-focus-ring-color);
  border-color: var(--inet-primary-color);
}
```

- [ ] **Step 2: Bind the touch crosshair**

`chart-tool.ts:1071` in `drawTouch()` uses `#dc581e` a third time. It is a canvas literal, so gate it in
TypeScript:

```ts
         context.strokeStyle = GuiTool.isVizModern()
            ? getComputedStyle(document.documentElement)
                 .getPropertyValue("--inet-primary-color").trim() || "#E58A2A"
            : "#dc581e";
```

Add the import if absent:

```ts
import { GuiTool } from "../../common/util/gui-tool";
```

The `|| "#E58A2A"` fallback matters: `getPropertyValue` returns an empty string if the variable is not
resolvable, and an empty `strokeStyle` assignment is silently ignored by the canvas, which would draw
the crosshair in whatever colour was set last.

- [ ] **Step 3: Only if Task 1 concluded "orange" — gate the TypeScript defaults too**

Skip this step entirely if Task 1 showed green.

```ts
         const modern = GuiTool.isVizModern();
         const rootStyle = getComputedStyle(document.documentElement);
         let fillStyle = modern
            ? rootStyle.getPropertyValue("--inet-focus-ring-color").trim()
               || "rgba(229, 138, 42, 0.28)"
            : "rgba(220, 88, 30, 0.3)"; //#dc581e
         let strokeStyle = modern
            ? rootStyle.getPropertyValue("--inet-primary-color").trim() || "#E58A2A"
            : "#dc581e";
```

- [ ] **Step 4: Verify the gated rule compiles and does not leak**

Run: `cd community/web && npm run lint`
Expected: PASS. Then confirm by grep that the ungated `.chart-object-canvas` rule still carries the
legacy values:

```bash
cd community/web/projects/portal/src && grep -n "chart-object-canvas" -A4 scss/_themeable.scss
```
Expected: two rules — the bare one with `rgba(220, 88, 30, 0.3)` / `#dc581e`, and the `.viz-modern` one
with the tokens.

- [ ] **Step 5: Run the graph suites**

Run: `cd community/web && npm run test:portal -- --run chart-tool`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add community/web/projects/portal/src/scss/_themeable.scss \
        community/web/projects/portal/src/app/graph/model/chart-tool.ts
git commit -m "feat(chart): bind selection to the shipped focus family under the gate

Chart overlay surfaces ticket item 8, decided 2026-08-01. #dc581e was an orange
in no palette; --inet-focus-ring-color is already the primary at that alpha, and
a selection is semantically a focus state. Fill -> --inet-focus-ring-color,
stroke -> --inet-primary-color, plus the touch crosshair in drawTouch.

Item 9's claim that this CSS branch was dead is wrong: _themeable.scss:1401
already sets both properties, to the same values as the TypeScript defaults,
which is why nobody noticed either way. Gated behind .viz-modern."
```

---

## Task 4: Chrome selections stop flooding (item 7, gated)

**Files:**
- Modify: `web/projects/portal/src/app/graph/model/chart-tool.ts` (the fill call in `drawRegions`)
- Modify: `web/projects/portal/src/app/graph/model/chart-tool-selection.spec.ts`

**Interfaces:**
- Consumes: `ChartTool.isChromeArea` and the `areaName` parameter from Task 2; `GuiTool.isVizModern()`.
- Produces: nothing later tasks depend on.

Selecting a bar means "these data values" and a fill is correct. Selecting the Y axis means "this
component, so I can act on it" — filling it floods a band that carries no data, and the fill is
area-proportional while the signal need is constant: 30% alpha is a gentle tint on a 20px bar and a
solid block on a 350px axis band.

- [ ] **Step 1: Find where the fill is applied**

Run:
```bash
cd community/web/projects/portal/src && grep -n "fillStyle\|fill()\|lineWidth\|stroke()" app/graph/model/chart-tool.ts | sed -n '1,25p'
```
Expected: the `fillStyle` / `strokeStyle` assignment near line 778, then the per-region draw loop that
calls `context.fill()` and `context.stroke()`. Note the exact line numbers of the `fill()` call(s)
inside the loop — the next step guards them.

- [ ] **Step 2: Write the failing test**

Append to `chart-tool-selection.spec.ts`:

```ts
describe("drawRegions chrome/data fill split", () => {
   function fakeContext(): any {
      const calls: string[] = [];

      return {
         calls,
         canvas: { getBoundingClientRect: () => ({ width: 100, height: 100 }), width: 0, height: 0 },
         beginPath: () => calls.push("beginPath"),
         moveTo: () => {}, lineTo: () => {}, closePath: () => {},
         setTransform: () => {}, save: () => {}, restore: () => {}, scale: () => {},
         translate: () => {}, clearRect: () => {}, quadraticCurveTo: () => {}, bezierCurveTo: () => {},
         arc: () => {}, rect: () => {},
         fill: () => calls.push("fill"),
         stroke: () => calls.push("stroke"),
         set fillStyle(v: string) {}, set strokeStyle(v: string) {}, set lineWidth(v: number) {}
      };
   }

   const region: any = {
      index: 0, valIdx: 0, segTypes: [[1, 2]], pts: [[[[0, 0], [10, 0], [10, 10], [0, 10]]]]
   };

   beforeEach(() => document.body.classList.add("viz-modern"));
   afterEach(() => document.body.classList.remove("viz-modern"));

   it("strokes without filling for a chrome area", () => {
      const ctx = fakeContext();
      ChartTool.drawRegions(ctx, [region], 0, 0, 1, undefined, undefined, false, "left_y_axis");
      expect(ctx.calls).toContain("stroke");
      expect(ctx.calls).not.toContain("fill");
   });

   it("fills and strokes for the plot area", () => {
      const ctx = fakeContext();
      ChartTool.drawRegions(ctx, [region], 0, 0, 1, undefined, undefined, false, "plot_area");
      expect(ctx.calls).toContain("fill");
      expect(ctx.calls).toContain("stroke");
   });

   it("keeps the fill for a chrome area when the gate is off", () => {
      document.body.classList.remove("viz-modern");
      const ctx = fakeContext();
      ChartTool.drawRegions(ctx, [region], 0, 0, 1, undefined, undefined, false, "left_y_axis");
      expect(ctx.calls).toContain("fill");
   });
});
```

The stub context records draw calls rather than pixels, which is what the assertion is actually about.
If `drawRegions` calls a 2D method the stub lacks, add it as a no-op — do not weaken the assertions.

- [ ] **Step 3: Run the test to verify the first and third fail**

Run: `cd community/web && npm run test:portal -- --run chart-tool-selection`
Expected: "strokes without filling for a chrome area" FAILS (`fill` is present); the other two PASS.

- [ ] **Step 4: Guard the fill**

Compute the flag once, before the draw loop:

```ts
         // Chrome selections (axis, legend, title) are stroke-only: the outline says "selected"
         // without covering the labels the user selected the axis in order to read, and it does not
         // scale its weight with the size of the band. Data selections keep the fill — marking values
         // is what a fill is for. Gate-off keeps the old behaviour for both.
         const strokeOnly = GuiTool.isVizModern() && ChartTool.isChromeArea(areaName);
```

Then wrap each `context.fill()` call inside the region loop:

```ts
               if(!strokeOnly) {
                  context.fill();
               }
```

Leave `lineWidth` at 2 and leave every `context.stroke()` call untouched.

- [ ] **Step 5: Run the tests to verify all pass**

Run: `cd community/web && npm run test:portal -- --run chart-tool-selection`
Expected: PASS, all six tests in the file.

- [ ] **Step 6: Run the wider graph suites**

Run:
```bash
cd community/web && npm run test:portal -- --run chart && npm run test:portal:tl -- --run chart
```
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add community/web/projects/portal/src/app/graph/model/chart-tool.ts \
        community/web/projects/portal/src/app/graph/model/chart-tool-selection.spec.ts
git commit -m "feat(chart): chrome selections become stroke-only

Chart overlay surfaces ticket item 7, decided 2026-08-01. Component selection
and data selection were drawn identically while meaning different things, and
the fill is area-proportional where the signal need is constant. The ten chrome
areas now stroke at 2px with no fill; plot_area keeps its fill.

Accepted trade: chrome selection is a quieter signal on a busy chart than a
colour wash. Gated behind .viz-modern."
```

---

## Task 5: Annotations join the same vocabulary (item 11, gated)

**Files:**
- Modify: `web/projects/portal/src/app/vsobjects/objects/annotation/vs-annotation.component.scss:44-46, 60, 94-96`
- Modify: `web/projects/portal/src/app/graph/objects/chart-legend-container.component.scss:32-35`

**Interfaces:**
- Consumes: the vocabulary decided in Tasks 3 and 4.
- Produces: nothing later tasks depend on.

A selected annotation is chrome, so it takes the chrome treatment. `vs-annotation` is mounted by 11 hosts
— chart, gauge, image, text, line, rectangle, oval, table, crosstab, calc table and
`vs-object-container` itself — and the tables mount it once per cell region, so this rule is the reason
the cross-component drift check in Task 6 exists.

- [ ] **Step 1: Confirm the three targets are still as described**

Run:
```bash
cd community/web/projects/portal/src && sed -n '44,46p;56,62p;94,96p' app/vsobjects/objects/annotation/vs-annotation.component.scss
```
Expected: `border: 2px dotted darkgray;`, a nested `.vs-annotation__rectangle-content` containing
`color: black;`, and `.annotition-button { border: 2px outset #cccccc; }`.

- [ ] **Step 2: Check whether `.annotition-button` matches anything**

Run:
```bash
cd community/web/projects/portal/src && grep -rn "annotition" app/ || echo "NO MATCHES OUTSIDE THE STYLESHEET"
```
Expected: only the stylesheet line. The class name is misspelled ("annotition" for "annotation"),
`outset` is a Web-1.0 bevel used nowhere else, and `#cccccc` is off-palette — the misspelling is why it
matches nothing. If the grep finds a template using it, **do not delete**; retokenize it instead and note
that in the commit.

- [ ] **Step 3: Delete the dead rule (ungated)**

Remove `.annotition-button { border: 2px outset #cccccc; }` entirely.

- [ ] **Step 4: Delete the dead legend canvas rule (ungated)**

`chart-legend-container.component.scss:32-35` declares `.chart-legend__canvas` with no matching element
in that component's template. Verify, then delete:

```bash
cd community/web/projects/portal/src && grep -n "chart-legend__canvas" app/graph/objects/chart-legend-container.component.html || echo "NO MATCHING ELEMENT — safe to delete"
```

- [ ] **Step 5: Add the gated annotation rules**

Append to `vs-annotation.component.scss`:

```scss
// A selected annotation is chrome, so it takes the chrome treatment: solid 2px in the same primary
// as every other chrome selection. The dotted darkgray was the product's third way of saying
// "selected"; there is now one.
:host-context(.viz-modern) {
  .vs-annotation__rectangle--selected {
    border: 2px solid var(--inet-primary-color);
  }

  // A named colour cannot follow a theme — annotation body text was black in dark mode too.
  .vs-annotation__rectangle-content-padding-box .vs-annotation__rectangle-content {
    color: var(--inet-text-color);
  }
}
```

The content selector must repeat the `-padding-box` parent, because the original `color: black` is
nested inside it and a bare `.vs-annotation__rectangle-content` would lose on specificity.

- [ ] **Step 6: Leave the line endpoint alone**

`.vs-annotation__line-endpoint { background: transparent; // left as not themeable }` — the comment says
so explicitly. Do not touch it.

- [ ] **Step 7: Lint**

Run: `cd community/web && npm run lint`
Expected: PASS.

- [ ] **Step 8: Run the annotation suites**

Run: `cd community/web && npm run test:portal -- --run annotation`
Expected: PASS.

- [ ] **Step 9: Commit**

```bash
git add community/web/projects/portal/src/app/vsobjects/objects/annotation/vs-annotation.component.scss \
        community/web/projects/portal/src/app/graph/objects/chart-legend-container.component.scss
git commit -m "feat(annotation): join the one selection vocabulary

Shell surfaces ticket item 11, settled 2026-08-01. A selected annotation was
drawn with border: 2px dotted darkgray — the product's third idiom for
\"selected\" alongside the canvas fill and the shipped focus ring. It now strokes
solid 2px in --inet-primary-color like every other chrome selection, and the
hardcoded black body text binds to --inet-text-color so it can follow a theme.

Also deletes two dead rules, ungated: .annotition-button (misspelled class,
matches nothing) and .chart-legend__canvas (no matching element in its
template).

Gated behind .viz-modern. Ships with items 7-8 — one decision, two
implementations."
```

---

## Task 6: Verification pass

**Files:** none modified.

**Interfaces:**
- Consumes: every preceding task.
- Produces: a filled-in result per row, recorded in the PR description.

- [ ] **Step 1: Run the full frontend suite**

Run:
```bash
cd community/web && npm run test:portal && npm run test:portal:tl && npm run lint
```
Expected: PASS. Compare any failure against a clean checkout before attributing it to this PR.

- [ ] **Step 2: Gate ON — walk the selection matrix**

Set `viewsheet.modernVisualization = true`, confirm `viz-modern` on the body, build and start.

| Check | Expect |
|---|---|
| Select a **bar** | fill + 2px stroke, both in the primary family, not `#dc581e` |
| Select a **measure axis** | 2px outline only; the tick labels stay readable |
| Select a **legend entry**, then the **legend title** | outline only |
| Select an **axis title** (x, x2, y, y2) | outline only |
| Multi-select several bars | still legible as a group |
| Select on a chart with a **dark or saturated plot background** | the old 30% orange muddied here; confirm it reads |
| **Brush**, then **zoom** | selections redraw correctly — both re-trigger the draw path |
| **Resize the window** | re-triggers `drawSelectedRegions`; selection survives |
| **Touch** a chart (`drawTouch`) | crosshair is in the primary, not `#dc581e` |
| Select a **reference line** | still filled — it is data, and passes no area name |
| Select an annotation on a **chart** | solid 2px primary border |
| Select an annotation on a **table** | **identical treatment** — this is the check that catches the two implementations drifting |
| Annotation body text in **dark mode** | readable, not black-on-dark |
| Dark mode, all of the above | every colour resolves |

- [ ] **Step 3: Gate OFF — confirm nothing moved**

Set `viewsheet.modernVisualization = false`.

| Check | Expect |
|---|---|
| Select a bar, an axis, a legend, an axis title | **all four filled and stroked in `#dc581e`**, exactly as before |
| Select an annotation | **dotted darkgray**, as before |
| Annotation body text | black, as before |
| Touch crosshair | `#dc581e`, as before |

Only the two dead-rule deletions should differ from `main` in this configuration, and neither renders.

- [ ] **Step 4: Open the PR**

```bash
git push -u origin feature-74519-selection
```

Community-only. In the description: cite `Chart overlay surfaces - ticket.md` items 7, 8, 9 and
`Shell surfaces - ticket.md` item 11; state that they ship together by decision; and **record the
item 9 correction** with Task 1's empirical result so the reviewer is not misled by the ticket.

---

## Self-review notes

- **Spec coverage.** Design §4.2 item 8 → Task 3; item 7 → Tasks 2 and 4; touch crosshair → Task 3
  Step 2; item 11 → Task 5; the two ungated deletions → Task 5 Steps 3–4. Design §4.3's hazards → Task 1
  (which path is live) and Task 6 Step 2 (the chart/table drift check). Design §6 → Task 6.
- **Type consistency.** `ChartTool.isChromeArea(areaName: ChartAreaName): boolean` is defined in Task 2
  and consumed under that exact name in Task 4. The ninth `drawRegions` parameter is
  `areaName: ChartAreaName = null` in Task 2 and passed positionally in Tasks 2 and 4's tests with the
  same arity.
- **Ordering dependency.** Task 1 gates a conditional step inside Task 3. Do not run Task 3 before
  recording Task 1's answer.
