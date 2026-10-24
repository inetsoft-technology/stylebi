# Corner Kebab Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give every anchored (modern) assembly a kebab in the viewer, either in the title lane or in the assembly's top-right corner when the lane is hidden or shorter than 24px, and hide every anchored kebab at rest on devices that can hover.

**Architecture:** A new service predicate, `isCornerKebab`, splits today's "no chrome" rung into a corner placement (height ≥ 32) and a narrowed no-chrome rung (height < 32). The action layer gives the corner zero toolbar slots, so the kebab opens the flattened full inventory. The container positions the corner strip inside the card, using the anchored strip's width and the right-alignment it already has. CSS hides the anchored pill with `opacity` under `@media (hover: hover)` and reveals it on assembly hover, strip hover or `:has(:focus-visible)`. A `viz-corner-kebab` class on the assembly wrapper lets the table, crosstab, calendar and selection components move their top-right controls out from under the kebab.

**Tech Stack:** Angular 21 (portal project), TypeScript 5.9, SCSS with emulated encapsulation, Vitest 4 (`*.spec.ts` via `ng test portal`, `*.tl.spec.ts` via `portal:test-tl`).

**Spec:** [design](../specs/2026-10-22-corner-kebab-design.md) and [decisions](../specs/2026-10-23-corner-kebab-decisions.md). Where they differ, the decisions file wins. Task 5 commits both into the repo.

## Global Constraints

- Work in a new community worktree on branch `bug-79211` off `origin/epic-74519`. Run `npm ci` in the worktree's `web/`; a junctioned `node_modules` breaks Vitest.
- The shared checkout is used by other sessions. Run `git branch --show-current` before every commit, and run `git add` and `git commit` as separate commands.
- Everything keys on `isAnchoredDesign(objectType, vizModern)`. With the gate off, and for non-anchored types, behaviour is unchanged.
- Only the anchoring host changes (`vs-object-container`, viewer and preview). The composer, binding pane, both wizard panes and the embedded chart stay unchanged.
- Values: `ANCHORED_LANE_MIN = 24`, `ACTION_FLOOR = 32`, `CORNER_KEBAB_INSET = 4`. The reserve is `48px` (`54px` under `pointer: coarse`); Task 6 confirms both by measurement.
- No comments in `.html` files. Code comments are a short clause.
- Unit specs: `cd web && npx ng test portal --include="**/<file>.spec.ts"`. TL specs: `cd web && npx ng run portal:test-tl --include='**/<file>.tl.spec.ts'`, one file per run. Never run the full TL suite. `ng test portal` with a TL include matches nothing and exits 0.
- Commit subject: `Bug #79211, <what changed>`, ending with `Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>`.
- One community PR into `epic-74519`. No enterprise PR.

## Review Focus

- **The pointer crossing from the assembly onto the kebab with a real mouse.** `<mini-toolbar>` is a DOM sibling of the assembly, so this is a flicker risk. jsdom can't evaluate `:hover`, so this is a Task 6 browser check.
- **Tabbing to an untitled chart's kebab must reveal it.** Charts have no `force-show-toolbar` route. Task 3 pins the `:has(:focus-visible)` rule text, and Task 6 checks it with a keyboard.
- **A corner card that is shifted, in bottom tabs or shrunk.** `cardTopShift` and `realWidth` must reach the corner geometry the way they reach the lane. Task 2 covers this with a test.
- **A fully script-hidden inventory must draw nothing,** not an empty bordered pill. Task 2 covers this with a test.
- **A strip dismissed with Hide MiniToolbar must stay dismissed under hover and focus.** Every new reveal rule carries `:not(.hidden-mini-toolbar)`. Task 3 covers this with a test.

---

### Task 1: Corner predicates and the action budget

**Files:**
- Modify: `web/projects/portal/src/app/vsobjects/objects/mini-toolbar/mini-toolbar.service.ts:76-135`
- Modify: `web/projects/portal/src/app/vsobjects/action/abstract-vs-actions.ts:27,48-55,145-191,229-235,477`
- Modify: `web/projects/portal/src/app/vsobjects/objects/vs-object-container.component.ts:503-506`
- Test: `web/projects/portal/src/app/vsobjects/objects/mini-toolbar/mini-toolbar.service.spec.ts`
- Test: `web/projects/portal/src/app/vsobjects/action/abstract-vs-actions.spec.ts`

**Interfaces:**
- Produces, in `mini-toolbar.service.ts`:
  - `export const ACTION_FLOOR = 32;`, moved from `AbstractVSActions`'s private static, which is deleted. `AbstractVSActions` imports it.
  - `export const CORNER_KEBAB_INSET = 4;`
  - `export function isCornerKebab(objectType: string, vizModern: boolean, laneHeight: number, objectHeight: number): boolean`. True when `isAnchoredDesign(...)`, `laneHeight < ANCHORED_LANE_MIN` and `objectHeight >= ACTION_FLOOR`.
  - `isAnchoredChromeSuppressed(objectType: string, vizModern: boolean, laneHeight: number, objectHeight: number): boolean`. Adds `&& objectHeight < ACTION_FLOOR`. It stays a separate predicate.
- Produces, in `AbstractVSActions` (all private getters):
  - `anchoringHost`: `(this.viewer || this.preview) && !this.binding && !this.contextProvider.embedAssembly`, which is `laneSuppressed`'s guard.
  - `cornerKebab`: `anchoringHost && isCornerKebab(type, vizModern, anchoredLaneHeight(model), model.objectFormat.height)`.
  - `chromeSuppressed`: the same, with `isAnchoredChromeSuppressed`.
  - `laneSuppressed` is deleted.

- [ ] **Step 1: Write the failing service tests**

In `mini-toolbar.service.spec.ts`, add a `describe("isCornerKebab")` block:

```ts
const ANCHORED = ["VSChart", "VSTable", "VSCrosstab", "VSCalcTable", "VSSelectionList",
                  "VSSelectionTree", "VSSelectionContainer", "VSCalendar"];
it("puts every anchored type in the corner when its title is hidden", () =>
   ANCHORED.forEach(t => expect(isCornerKebab(t, true, 0, 200)).toBe(true)));
it("uses the corner below the lane minimum", () => expect(isCornerKebab("VSChart", true, 23, 200)).toBe(true));
it("leaves a lane that holds the strip to the lane", () => expect(isCornerKebab("VSChart", true, 24, 200)).toBe(false));
it("draws nothing below the height floor", () => expect(isCornerKebab("VSChart", true, 0, 31)).toBe(false));
it("starts exactly at the floor", () => expect(isCornerKebab("VSChart", true, 0, 32)).toBe(true));
it("is false with the gate off", () => expect(isCornerKebab("VSChart", false, 0, 200)).toBe(false));
it("is false outside the anchored set", () => {
   expect(isCornerKebab("VSRangeSlider", true, 0, 200)).toBe(false);
   expect(isCornerKebab("VSText", true, 0, 200)).toBe(false);
});
```

Rewrite the existing `isAnchoredChromeSuppressed` cases to pass a fourth argument. The "is true" cases use height `31`. Add `expect(isAnchoredChromeSuppressed("VSChart", true, 0, 32)).toBe(false)` and `expect(isAnchoredChromeSuppressed("VSChart", true, 24, 31)).toBe(false)`. The gate-off, range-slider and laneless cases keep `false`, at height `31`.

- [ ] **Step 2: Run them to verify they fail**

Run: `npx ng test portal --include="**/mini-toolbar.service.spec.ts"`
Expected: FAIL, because `isCornerKebab` is not exported.

- [ ] **Step 3: Implement the service exports listed under Interfaces**

Rewrite the doc comments that say "no strip, no kebab, right-click only". That statement now holds only below `ACTION_FLOOR`, and a hidden or short lane takes the corner. Keep the "separate predicate, not `!isCornerKebab`" reasoning on `isAnchoredChromeSuppressed`.

- [ ] **Step 4: Write the failing action-layer tests**

In `abstract-vs-actions.spec.ts`, rename `describe("dismissal against the lane")` to `describe("dismissal against the corner and the floor")`, using the existing `viewerActionsFor(width, height, vizModern, laneHeight)` and `actionsFor(...)` helpers:

```ts
it("offers the dismissal in the viewer when a short lane puts the kebab in the corner", () =>
   expect(visibleMenuIds(viewerActionsFor(400, 200, true, 20))).toContain("vs-assembly hide-mini-toolbar"));
it("offers the dismissal in the viewer when the title is hidden", () =>
   expect(visibleMenuIds(viewerActionsFor(400, 200, true, 0))).toContain("vs-assembly hide-mini-toolbar"));
it("hides the dismissal below the height floor, where nothing is drawn", () =>
   expect(visibleMenuIds(viewerActionsFor(400, 30, true, 0))).not.toContain("vs-assembly hide-mini-toolbar"));
```

Keep the existing lane-26, composer, classic and range-slider cases unchanged. Add a `describe("corner budget")` block:

```ts
it("gives the corner no toolbar slots", () =>
   expect(viewerActionsFor(400, 200, true, 0).allowedActionsNum()).toBe(0));
it("keeps the floating hosts' slots whatever the lane", () =>
   expect(actionsFor(400, 200, true, 0).allowedActionsNum()).toBeGreaterThan(0));
it("opens the whole inventory from the corner kebab, flattened and deduplicated", () => {
   const more = ids(viewerActionsFor(400, 200, true, 0).getMoreActions());
   expect(more).toContain("chart show-data");
   expect(more).toContain("chart open-max-mode");
   expect(more).not.toContain("menu actions");
   expect(new Set(more).size).toBe(more.length);
});
```

The last test assumes the default `viewerActionsFor` model is a chart. If it isn't, build the chart model the way the existing `getMoreActions` tests at `:390-425` do.

- [ ] **Step 5: Run them to verify they fail**

Run: `npx ng test portal --include="**/abstract-vs-actions.spec.ts"`
Expected: FAIL. The two "offers the dismissal" cases and the corner budget cases fail.

- [ ] **Step 6: Implement the getters, then rewire the budget and the dismissal**

- `allowedActionsNum()`: return `0` when `this.kebabOnly || this.cornerKebab`, at the existing `kebabOnly` early return, after the `resident` guard.
- The dismissal's `visible()`: replace `!this.laneSuppressed` with `!this.chromeSuppressed`.
- `showingActions`: use the imported `ACTION_FLOOR`, and update the comment at `:229-235`.
- `vs-object-container.component.ts` `isChromeSuppressed`, interim, behaviour-preserving until Task 2: `isAnchoredChromeSuppressed(type, vizModern, lane, h) || isCornerKebab(type, vizModern, lane, h)`, where `h = object.objectFormat.height`.

- [ ] **Step 7: Run the three specs to verify they pass**

Run each:
- `npx ng test portal --include="**/mini-toolbar.service.spec.ts"`
- `npx ng test portal --include="**/abstract-vs-actions.spec.ts"`
- `npx ng test portal --include="**/vs-object-container.component.spec.ts"`

Expected: all PASS. The container spec is unchanged, because the interim keeps its behaviour.

- [ ] **Step 8: Commit**

`Bug #79211, split the laneless rung into a corner kebab and a height floor`

---

### Task 2: Corner placement

**Files:**
- Modify: `web/projects/portal/src/app/vsobjects/objects/mini-toolbar/mini-toolbar.component.ts:74,192-210,409-418`
- Modify: `web/projects/portal/src/app/vsobjects/objects/mini-toolbar/mini-toolbar.component.html:18-24`
- Modify: `web/projects/portal/src/app/vsobjects/objects/vs-object-container.component.ts:468-506,555-630`
- Modify: `web/projects/portal/src/app/vsobjects/objects/vs-object-container.component.html:351-382`
- Test: `web/projects/portal/src/app/vsobjects/objects/vs-object-container.component.spec.ts`
- Test: `web/projects/portal/src/app/vsobjects/objects/mini-toolbar/mini-toolbar.component.tl.spec.ts`
- Test: `web/projects/portal/src/app/vsobjects/objects/mini-toolbar/mini-toolbar.component.spec.ts` (existing cases only)

**Interfaces:**
- Consumes: `isCornerKebab` and `CORNER_KEBAB_INSET` from Task 1.
- Produces, in `MiniToolbar`:
  - `@Input() cornerKebab = false`.
  - `kebabResident`: `(this.anchorInTitleLane || this.cornerKebab) && !this.mobileDevice`.
  - The host div gets `[class.mini-toolbar--corner]="cornerKebab"`, and `[style.width.px]="anchorInTitleLane || cornerKebab ? width : null"`.
  - `topY` returns `this.top` when `anchorInTitleLane || cornerKebab`.
  - `showToolbarContainer` tests `actionButtonGroups?.some(g => g.visible)` instead of `length > 0`.
- Produces, in `VSObjectContainer`:
  - `isCornerKebab(object): boolean`: `!this.mobile && this.isMiniToolbarVisible(object) && !VSObjectContainer.isMaxModeSelection(object) && isCornerKebab(type, vizModern, anchoredLaneHeight(object), object.objectFormat.height)`. Task 4 reuses this for the reserve class.
  - `isChromeSuppressed(object)`: `isAnchoredChromeSuppressed(..., h) || this.mobile && isCornerKebab(...)`, where the second call is the service predicate. This replaces Task 1's interim.
  - `getCornerToolbarWidth(object): number`: `getAnchoredToolbarWidth(object) - CORNER_KEBAB_INSET`.
  - `getToolbarTop`: in corner mode, `objectFormat.top + getCardTopShift(object) + getLaneInset(object).top + CORNER_KEBAB_INSET`.
  - `getToolbarLeft`: in corner mode, the anchored branch, `left + getLaneInset(object).left`.
- A max-mode selection list or tree without a title is neither corner nor suppressed. It takes today's floating max-mode path unchanged (decisions §6).

- [ ] **Step 1: Write the failing container tests**

In `vs-object-container.component.spec.ts`, add `describe("VSObjectContainer corner kebab")`. Use `makeVSObject`/`makeObjectFormat`, and the shifted-table block's `viewerComponent` pattern (`:208-240`). Base object: `VSChart`, `vizModern: true`, `titleVisible: false`, `titleFormat: {height: 30}`, `objectFormat {top: 40, left: 250, width: 400, height: 300}`, `padding {top: 12, left: 10, bottom: 12, right: 6}`.

```ts
it("places an untitled chart's kebab in the corner", () => {
   expect(comp.isChromeSuppressed(obj)).toBe(false);
   expect(comp.isCornerKebab(obj)).toBe(true);
   expect(comp.getToolbarTop(obj, 0)).toBe(40 + 12 + 4);
   expect(comp.getToolbarLeft(obj, 0)).toBe(250 + 10);
   expect(comp.getCornerToolbarWidth(obj)).toBe(400 - 10 - 6 - 4);
});
it("uses the corner for a visible lane under 24px", ...);           // titleVisible true, titleFormat.height 20 → isCornerKebab true
it("draws nothing for an untitled chart under 32px", ...);          // height 30 → isChromeSuppressed true, isCornerKebab false
it("drops a shifted card's corner by its card shift", ...);         // VSTable, cardTopShift 120 → top 40 + 120 + 12 + 4
it("leaves an untitled max-mode selection list on the floating strip", ...); // VSSelectionList, maxMode true → isCornerKebab false, isChromeSuppressed false, isToolbarAnchored false
it("suppresses the corner on mobile", ...);                         // comp.mobile = true → isChromeSuppressed true, isCornerKebab false
it("leaves gate-off assemblies alone", ...);                        // vizModern false → both false
```

Update the existing `"VSObjectContainer anchored-lane chrome suppression"` block. A short lane (20/23) and a hidden title at a full-height card are no longer suppressed: assert `isChromeSuppressed` is `false` and `isCornerKebab` is `true`. Add one case at height `31` that stays suppressed. Update the shifted-card block's `isChromeSuppressed(obj)` expectation at `:289` the same way.

- [ ] **Step 2: Run them to verify they fail**

Run: `npx ng test portal --include="**/vs-object-container.component.spec.ts"`
Expected: FAIL, because `isCornerKebab` and `getCornerToolbarWidth` are not defined.

- [ ] **Step 3: Implement the container methods and the template wiring**

Template (`vs-object-container.component.html:351-382`):
- Add `@let corner = isCornerKebab(vsObject);` beside `anchored`.
- Bind `[cornerKebab]="corner"`.
- Bind `[width]="anchored ? getAnchoredToolbarWidth(vsObject) : corner ? getCornerToolbarWidth(vsObject) : getToolbarWidth(vsObject)"`.
- Leave `glyphTone` alone. It is already null when not anchored.

Rewrite the doc comments on `isKebabResident`, `isChromeSuppressed` and `isToolbarAnchored` that describe "no chrome at all".

- [ ] **Step 4: Run the container spec to verify it passes**

Run: `npx ng test portal --include="**/vs-object-container.component.spec.ts"`
Expected: PASS.

- [ ] **Step 5: Write the failing mini-toolbar tests**

In `mini-toolbar.component.tl.spec.ts`, give `renderWithActions` and `renderToolbar` a trailing `cornerKebab = false` parameter. `renderToolbar` passes it through, and `renderWithActions` passes it as a component input. Add `describe("MiniToolbar rendered - corner kebab")`:

```ts
it("renders the kebab alone, on the anchored and corner host classes", async () => {
   const { fixture } = await renderToolbar(
      [new AssemblyActionGroup([]), new AssemblyActionGroup([makeAction("more actions")])], false, false, true);
   const host = fixture.nativeElement.querySelector(".mini-toolbar");
   expect(host.classList).toContain("mini-toolbar--corner");
   expect(host.classList).toContain("mini-toolbar--anchored");
   expect(host.querySelectorAll(".mini-toolbar-kebab").length).toBe(1);
   expect(host.querySelectorAll(".mini-toolbar-button-group:not(.mini-toolbar-kebab-group)").length).toBe(0);
});
it("spans the bound width so the pill can right-align", ...);        // host.style.width === "100px"
it("keeps the host's resolved top", ...);                            // host.style.top === "10px"
it("draws no container when every group is empty", ...);             // groups [new AssemblyActionGroup([]), new AssemblyActionGroup([])], corner → no .mini-toolbar-container
it("draws no container for an empty floating strip either", ...);    // same groups, not anchored, not corner → no .mini-toolbar-container
```

- [ ] **Step 6: Run them to verify they fail**

Run: `npx ng run portal:test-tl --include='**/mini-toolbar.component.tl.spec.ts'`
Expected: FAIL, because `cornerKebab` is not an input of `MiniToolbar`.

- [ ] **Step 7: Implement the `MiniToolbar` changes listed under Interfaces**

Update the `showToolbarContainer` doc comment to say that visible content is what's tested.

- [ ] **Step 8: Run both specs to verify they pass**

Run:
- `npx ng run portal:test-tl --include='**/mini-toolbar.component.tl.spec.ts'`
- `npx ng test portal --include="**/mini-toolbar.component.spec.ts"`

Expected: PASS. If an existing case asserted an empty container rendering, update it: the old behaviour drew an empty pill.

- [ ] **Step 9: Commit**

`Bug #79211, place the kebab in the corner of an assembly without a usable lane`

---

### Task 3: Hidden at rest, revealed by hover or keyboard focus

**Files:**
- Modify: `web/projects/portal/src/app/vsobjects/objects/mini-toolbar/mini-toolbar.component.scss:93-196,294-309`
- Modify: `web/projects/portal/src/scss/internal/_moving-resize.scss:31-51`
- Modify: `web/projects/portal/src/app/vsobjects/objects/mini-toolbar/mini-toolbar.component.ts:372-397`
- Test: `web/projects/portal/src/app/vsobjects/objects/mini-toolbar/mini-toolbar.component.tl.spec.ts`
- Test: `web/projects/portal/src/app/vsobjects/objects/mini-toolbar/mini-toolbar.component.spec.ts`

**Interfaces:**
- Consumes: the `.mini-toolbar--corner` and `.mini-toolbar--anchored` host classes from Task 2.
- Produces, in CSS:
  - `.mini-toolbar.mini-toolbar--anchored { visibility: visible }` stays. Its comment is rewritten: visibility keeps the kebab a tab stop, and opacity hides it.
  - New, inside `@media (hover: hover)`: `.mini-toolbar--anchored .mini-toolbar-container { opacity: 0; }`, and `.mini-toolbar--anchored:not(.hidden-mini-toolbar):hover .mini-toolbar-container, .mini-toolbar--anchored:not(.hidden-mini-toolbar):has(:focus-visible) .mini-toolbar-container { opacity: 1; }`.
  - The action-group reveal at `:149-152`: `:focus-within` becomes `:has(:focus-visible)`.
  - `.mini-toolbar--corner .mini-toolbar-container { box-shadow: var(--inet-shadow-low); }`. The design's `--inet-shadow-sm` does not exist. The base pill already draws the fill, border and radius.
  - Both `:host-context(.viz-dark) .mini-toolbar:not(.mini-toolbar--anchored)` rules (`:294`, `:306`) also list `.mini-toolbar--corner`.
  - `_moving-resize.scss:44-51` already sets `opacity: 1` on reveal; keep the rule and rewrite its comment at `:31-43`.
- Produces, in `onKeyUp`, for `kebabResident` strips: return early when `window.matchMedia?.("(hover: none)")?.matches`, when there is no `.mini-toolbar-container`, or when the container's computed `opacity` is `"0"`. Otherwise dismiss. This replaces the "no action group" rule, so Esc now dismisses a revealed kebab-only strip. On a device that can't hover, Esc never dismisses an anchored strip, because the resting kebab is the affordance itself.

- [ ] **Step 1: Write the failing compiled-CSS tests**

In `mini-toolbar.component.tl.spec.ts`, follow the pattern of the `"anchored hover persistence"` block at `:513-530` (read `<style>` text after one render). Add `describe("MiniToolbar rendered - resting opacity (compiled CSS only)")`:

```ts
it("hides the anchored pill at rest only where the device can hover", ...);
   // styleText matches /@media \(hover: ?hover\)\s*\{[^@]*\.mini-toolbar--anchored[^{}]*\.mini-toolbar-container[^{}]*\{[^}]*opacity:\s*0/
   // and no opacity: 0 rule on the anchored container appears outside that block
it("reveals the pill on strip hover and on keyboard focus, never on a dismissed strip", ...);
   // matches /\.mini-toolbar--anchored[^{}]*:not\(\.hidden-mini-toolbar\)[^{}]*:has\([^)]*:focus-visible\)[^{}]*\.mini-toolbar-container/
   // and the :hover twin, each followed by opacity: 1
it("gives the corner pill the dark surface", ...);
   // the rule carrying var(--inet-viz-surface-dark) has a selector list containing .mini-toolbar--corner
```

Update the existing `:focus-within` regex at `:527` to `:has\([^)]*:focus-visible\)`.

- [ ] **Step 2: Write the failing Esc tests**

In `mini-toolbar.component.spec.ts`, inside the existing Esc / `onKeyUp` describe (or a new one built with the file's TestBed setup), use an anchored strip whose groups hold only the kebab. Set `miniToolbarService.hideMiniToolbar` as a spy, and set the container's opacity through `container.style.opacity`.

```ts
it("does not dismiss an anchored strip whose pill is transparent", ...);      // opacity "0" → spy not called
it("dismisses a revealed kebab-only strip", ...);                             // opacity "1" → spy called with ("Chart1", true)
it("never dismisses an anchored strip on a device that cannot hover", ...);   // window.matchMedia stubbed to return { matches: true } → spy not called; restore afterwards
```

- [ ] **Step 3: Run them to verify they fail**

Run:
- `npx ng run portal:test-tl --include='**/mini-toolbar.component.tl.spec.ts'`
- `npx ng test portal --include="**/mini-toolbar.component.spec.ts"`

Expected: FAIL on the new cases.

- [ ] **Step 4: Implement the CSS and the `onKeyUp` change listed under Interfaces**

Rewrite every comment that calls the kebab "visible at rest", "a touch and keyboard affordance" or a "resting state" (`:93-96`, `:112-137`, `:139-148`, `:154-161`, `:188-195` in the component SCSS). The `@media (pointer: coarse)` block is kept as is. A device that can't hover still draws the kebab at rest, and that block sizes it. `--viz-strip-glyph-a` is now the alpha the kebab shows while revealed; only the wording changes.

- [ ] **Step 5: Run both specs to verify they pass**

Run the two commands from Step 3. Expected: PASS.

The `:has(...:focus-visible)` assertion also checks that the selector survives emulated encapsulation. If it fails because Angular dropped or split the selector, use `:focus-within` for both reveals instead. Change the assertions to match, and record in the commit message that a mouse-clicked kebab stays revealed until blur.

- [ ] **Step 6: Commit**

`Bug #79211, hide the anchored kebab at rest where the device can hover`

---

### Task 4: Reserve space for controls under the corner kebab

**Files:**
- Modify: `web/projects/portal/src/app/vsobjects/objects/vs-object-container.component.html:47-58` (class binding on `.vs-object-parent-container`)
- Modify: `web/projects/portal/src/app/vsobjects/objects/vs-object-container.component.scss`
- Modify: `web/projects/portal/src/app/vsobjects/objects/table/vs-table.component.scss`
- Modify: `web/projects/portal/src/app/vsobjects/objects/table/vs-crosstab.component.scss`
- Modify: `web/projects/portal/src/app/vsobjects/objects/calendar/vs-calendar.component.scss`
- Modify: `web/projects/portal/src/app/vsobjects/objects/selection/vs-selection.component.scss:223-229`
- Test: `web/projects/portal/src/app/vsobjects/objects/table/vs-table.component.display.tl.spec.ts`
- Test: `web/projects/portal/src/app/vsobjects/objects/table/vs-crosstab.component.display.tl.spec.ts`
- Test: `web/projects/portal/src/app/vsobjects/objects/calendar/month-calendar.component.display.tl.spec.ts` (`MonthCalendar` loads `vs-calendar.component.scss` as its own styles)
- Test: `web/projects/portal/src/app/vsobjects/objects/selection/vs-selection.component.display.tl.spec.ts`

**Interfaces:**
- Consumes: `VSObjectContainer.isCornerKebab(object)` from Task 2.
- Produces:
  - `[class.viz-corner-kebab]="isCornerKebab(vsObject)"` on `.vs-object-parent-container`.
  - In `vs-object-container.component.scss`: `.viz-corner-kebab { --viz-corner-kebab-reserve: 48px; }`, plus `@media (pointer: coarse) { .viz-corner-kebab { --viz-corner-kebab-reserve: 54px; } }`. The pill is about 40px (54px under the touch floor), plus the 4px inset and a 4px gap.
- Per-component rules, each under `:host-context(.viz-corner-kebab)`:
  - **Table:** `.vstable-header tr:first-child > td:last-child .vs-header-cell-button-sort { right: var(--viz-corner-kebab-reserve); }`
  - **Crosstab:** the same declaration, on the column-header quadrant `.right-top-table tr:first-child > td:last-child .vs-header-cell-button-sort`.
  - **Calendar:** `.month-calendar:not(.second-calendar) .child-calendar-title, .year-calendar:not(.second-calendar) .child-calendar-title { padding-right: var(--viz-corner-kebab-reserve); }`. Outside dropdown mode a double calendar stacks vertically, so the first panel owns the top-right.
  - **Selection:** `.pending-alert.top:not(.left) { right: var(--viz-corner-kebab-reserve); }` and `.selection-list__header-buttons { right: var(--viz-corner-kebab-reserve); }`. The second rule moves every child's header buttons in an untitled container, keeping them in one column.
  - Update the comment at `vs-selection.component.scss:223-225`: `.top` is now cleared in corner mode.

- [ ] **Step 1: Write the failing compiled-CSS tests**

Add one test to each of the four display TL specs, after rendering with that file's existing render helper:

```ts
it("moves its top-right control left of the corner kebab", ...);
   // styleText matches /\.viz-corner-kebab[^{}]*<target selector fragment>[^{}]*\{[^}]*(right|padding-right):\s*var\(--viz-corner-kebab-reserve\)/
```

The target fragments are `vs-header-cell-button-sort` (table and crosstab), `child-calendar-title` (calendar), and both `pending-alert` and `selection-list__header-buttons` (selection).

- [ ] **Step 2: Write the failing container test**

In `vs-object-container.component.spec.ts`, under the corner describe from Task 2:

```ts
it("marks only corner-placed assemblies for the reserve", ...);
   // isCornerKebab(untitled chart) === true; isCornerKebab(titled lane 30) === false; isCornerKebab(dropdown selection list, titleVisible false) === false
```

- [ ] **Step 3: Run them to verify they fail**

Run each:
- `npx ng run portal:test-tl --include='**/vs-table.component.display.tl.spec.ts'`
- `npx ng run portal:test-tl --include='**/vs-crosstab.component.display.tl.spec.ts'`
- `npx ng run portal:test-tl --include='**/month-calendar.component.display.tl.spec.ts'`
- `npx ng run portal:test-tl --include='**/vs-selection.component.display.tl.spec.ts'`
- `npx ng test portal --include="**/vs-object-container.component.spec.ts"`

Expected: the four TL specs fail on the new case. The container case may already pass, because `isMiniToolbarVisible` was folded in during Task 2; if it does, keep it as a regression pin.

- [ ] **Step 4: Implement the class binding, the custom property and the four component rules listed under Interfaces**

- [ ] **Step 5: Run the five commands from Step 3 to verify they pass**

Expected: PASS.

- [ ] **Step 6: Commit**

`Bug #79211, keep top-right controls clear of the corner kebab`

---

### Task 5: Decision records

**Files:**
- Create: `docs/superpowers/specs/lookfeel/2026-10-22-corner-kebab-design.md`, from `E:/Temp/2026-10-22-corner-kebab-design.md`. Prepend one line saying where the decisions file supersedes it.
- Create: `docs/superpowers/specs/lookfeel/2026-10-23-corner-kebab-decisions.md`, from `E:/Temp/2026-10-23-corner-kebab-decisions.md`. The design was already in the tree, so it was modified in place and the decisions file sits beside it.
- Create: `docs/superpowers/plans/2026-10-23-corner-kebab.md` (this plan).
- Modify: `docs/superpowers/specs/lookfeel/chart-card-anchored-strip-lane-decisions.md` (decision 4)
- Modify: `docs/superpowers/specs/lookfeel/chart-card-open-item-decisions.md` (§4 and its "Still open" bullet)
- Modify: `docs/superpowers/specs/lookfeel/chart-card-roadmap.md` (the §10.1 row at `:1457` and the paragraph at `:463-470`)

Do not edit anything under `chart-card-design3/`, which is regenerated wholesale.

- [ ] **Step 1: Copy the three documents into the tree**

Change their relative links so they resolve from the new location.

- [ ] **Step 2: Append a dated supersession paragraph to each of the three records**

For lane decision 4 and open-item §4: "Superseded 2026-10-23 by the corner kebab decisions" plus the link, and one sentence naming what replaced "no chrome": a corner kebab at height 32 and above, and nothing below it.

For the roadmap §10.1 row: mark it decided. Hover-only applies under `@media (hover: hover)`, hidden by opacity; devices that can't hover keep the resting kebab. Link the decisions file.

- [ ] **Step 3: Verify the links**

Run: `grep -n "corner-kebab" docs/superpowers/specs/lookfeel/*.md docs/superpowers/plans/2026-10-23-corner-kebab.md`
Expected: every link target exists (spot-check with `ls`).

- [ ] **Step 4: Commit**

`Bug #79211, record the corner kebab decisions and supersede the no-chrome rulings`

---

### Task 6: Browser verification

**Files:** none. Record the results in the PR description.

Start the viewer the way this repo's `run` skill does. In each check, the gate is on and density is compact unless stated.

- [ ] **Step 1: Measure the pill and confirm the reserve**

In DevTools, measure an untitled chart's revealed pill. Its width plus 8 should equal `--viz-corner-kebab-reserve` (48px with a fine pointer). Repeat under touch emulation (pointer coarse; expect 54). If either differs, update the Task 4 values and their test, then amend the Task 4 commit.

- [ ] **Step 2: Run the placement matrix, in light and dark viz**

For each of chart, table, crosstab, calc table, calendar, selection list, selection tree and selection container:

| Case | Expected |
|---|---|
| Untitled, at rest | No kebab visible |
| Untitled, hovered | Kebab pill inside the top-right corner, 4px from the inset. Dark viz gives it the dark surface |
| Pointer moved onto the kebab | No flicker; the menu opens with the whole inventory, and no "More" row |
| Titled, compact lane, at rest | No kebab; hover reveals it in the lane |
| Titled, dense (20px lane) | Corner kebab over the title's right end |
| 30px tall, untitled | Nothing, at rest or on hover |
| Tab to the kebab (untitled chart) | It becomes visible with a focus ring; Enter opens the menu |
| Mouse-click the kebab, close the menu, move away | The pill disappears (no sticky reveal) |
| Hide MiniToolbar, then hover | Stays hidden until the pointer leaves and re-enters |
| Esc with the kebab revealed | Dismissed; Esc while closing an unrelated dialog leaves every strip alone |

- [ ] **Step 3: Check the reserve**

- An untitled table with its last column sorted: the arrow sits left of the kebab and stays clickable while hovered.
- The same for a crosstab's last column-header cell. Also try a crosstab with no column dimension, whose top-right cell sits in `.left-top-table`; record it as a residual if it is covered.
- An untitled calendar: next-year is clickable while hovered, in both month and year view, and on a double calendar.
- An untitled selection list with pending submit: the alert icon is clickable.
- An untitled selection container: the first child's mini-menu and collapse toggle are clickable. Check a range slider as the first child too.

- [ ] **Step 4: Check the edge hosts**

- Max mode on an untitled selection list: kebab at the top-right, the same place a titled one uses.
- Max mode on an untitled chart: corner kebab.
- An assembly inside a tab, and one in bottom tabs (shifted card).
- An embedded viewsheet.
- An untitled chart used as a data tip, and as a pop component: no empty pill; if a kebab shows, its menu is non-empty.
- The composer, binding pane, wizard and embedded chart: unchanged.

- [ ] **Step 5: Check devices**

- iPad Safari, default desktop user agent: the resting kebab is visible and tappable.
- A phone user agent: no strip; `viewer-mobile-toolbar` works.

- [ ] **Step 6: Final-review checks**

- Overlap: place a control over an untitled chart's top-right corner, and expand a dropdown over it. Both take their clicks at rest; the hidden kebab takes none.
- An embed-viewer page: no reserve gaps on untitled assemblies.
- A double calendar, panels side by side: the second panel's next-year button sits left of the kebab, and the first panel's title is not padded.
- An untitled selection container whose first child is a range slider: the header buttons sit left of the kebab and are clickable. Repeat with current selections, whose row mini-menus must clear it too.
- A dense table with a two-row header.
- A table whose last column is narrow (under about 68px).
- Esc while the kebab's menu is open, by keyboard and by mouse, including Safari: by keyboard the strip stays and focus stays on the kebab; record the mouse result per browser.
- An untitled selection list with its search bar shown: the search clear button under the corner kebab.
