# Anchored Rollout Slices 4 and 5 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Anchor the selection container's and the calendar's mini-toolbar into their title lanes, completing the anchored rollout, and retarget `ANCHORED_ASSEMBLY_TYPES` from a rollout stage into the permanent anchored set.

**Architecture:** Browser-only. Three production files change and no persisted state, server model, export path or bookmark surface is touched. Two of the three edits are one-line additions to a `ReadonlySet<string>`; the third is a four-line `kebabOnly` override copied from an existing sibling. The bulk of the work is test migration: eleven existing tests use the calendar as their "type outside the anchored set" control, and that role transfers to the adhoc range slider, which stays outside the set permanently.

**Tech Stack:** Angular 21, TypeScript 5.9, Vitest 4.1.7 via `@angular/build:unit-test`.

**Spec:** [`docs/superpowers/specs/lookfeel/2026-09-01-anchored-rollout-slices-4-5-design.md`](../specs/lookfeel/2026-09-01-anchored-rollout-slices-4-5-design.md)

## Global Constraints

- **Branch:** `viz-updates`. Verified against `f96191e8c`. A parallel session is committing Java under `core/src/main/java/inetsoft/uql/viewsheet/internal/`; **this plan touches no Java**, so rebase rather than merge if the branch moves.
- **Test targets differ and confusing them silently passes.** `*.spec.ts` runs under `npx ng test portal --include='...'`. `*.tl.spec.ts` runs **only** under `npx ng run portal:test-tl --include='...'` — the plain `portal` target excludes `**/*.tl.spec.ts` and `passWithNoTests: true` makes a mis-targeted run exit 0 looking green. Every TL step below names `portal:test-tl`; do not substitute.
- **Never run the full TL suite** (`npm run test:portal:tl`, or an unfiltered `ng run portal:test-tl`). One `--include` per run, naming a single file.
- **Baselines, all green at `f96191e8c`:** `abstract-vs-actions.spec.ts` 69 tests · `mini-toolbar.service.spec.ts` 25 tests · `vs-object-container.component.display.tl.spec.ts` 46 tests.
- **Comment style:** short clauses, not full sentences. No ticket, PR or design-doc references inside source comments. No comments in `.html` files.
- **The range slider stays out of the anchored set.** Adding `"vsrangeslider"` would deliver Case 4 (no chrome at all) as a side effect. That is deliberately not this plan's scope — see spec section 3.
- Commit messages end with:
  `Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>`

---

## File Structure

**Production — three files:**

| File | Change |
|---|---|
| `web/projects/portal/src/app/vsobjects/objects/mini-toolbar/mini-toolbar.service.ts` | `ANCHORED_ASSEMBLY_TYPES` gains `"vsselectioncontainer"` and `"vscalendar"`; the `TEMPORARY` doc-comment is rewritten as the permanent set |
| `web/projects/portal/src/app/vsobjects/action/selection-container-actions.ts` | `kebabOnly => true` override |
| `web/projects/portal/src/app/vsobjects/action/abstract-vs-actions.ts` | Two `TEMPORARY` comments corrected (`:139`, `:455-464`) |
| `web/projects/portal/src/app/vsobjects/objects/vs-object-container.component.ts` | One `TEMPORARY` comment corrected (`:463`) |

**Test — three files:**

| File | Change |
|---|---|
| `web/projects/portal/src/app/vsobjects/action/abstract-vs-actions.spec.ts` | Six calendar-as-control tests re-pointed at the range slider; new container and calendar assertions |
| `web/projects/portal/src/app/vsobjects/objects/mini-toolbar/mini-toolbar.service.spec.ts` | Three calendar-as-control tests re-pointed; the set membership test updated |
| `web/projects/portal/src/app/vsobjects/objects/vs-object-container.component.display.tl.spec.ts` | Two calendar-as-control tests re-pointed/inverted |

**Task ordering is load-bearing.** Task 1 migrates the control type while the calendar is still outside the set, so the migration is provably behaviour-neutral (suite stays green, nothing else moves). Only then does Task 3 add the calendar. Reversing them turns one clear refactor and one clear feature into eleven confusing simultaneous failures.

---

### Task 1: Migrate the "outside the anchored set" control from the calendar to the range slider

The calendar currently plays two roles in the suite: itself, and the stand-in for "a type the rollout has not reached." Task 3 destroys the second role. This task transfers it first, with **no production change**, so the suite must stay green throughout — that greenness is the proof the migration is faithful.

The range slider is the correct successor: it is the one `hasMiniToolbar()` type that stays outside the set permanently, and unlike text or gauge it actually has toolbar actions. Measured at `f96191e8c`: at 2000×400, modern, lane 20, `RangeSliderActions.showingActions` yields `["range-slider open-max-mode", "range-slider close-max-mode", "range-slider unselect", "menu actions"]` and `allowedActionsNum()` returns 50 (uncapped).

**Files:**
- Modify: `web/projects/portal/src/app/vsobjects/action/abstract-vs-actions.spec.ts` (six tests at `:218`, `:346`, `:428`, `:486`, `:497`, `:773`; new helper near `:69`)
- Modify: `web/projects/portal/src/app/vsobjects/objects/mini-toolbar/mini-toolbar.service.spec.ts` (three assertions at `:66`, `:117`, and the `:40-47` membership tests)
- Modify: `web/projects/portal/src/app/vsobjects/objects/vs-object-container.component.display.tl.spec.ts` (one test at `:505`)

**Interfaces:**
- Consumes: nothing from earlier tasks.
- Produces: `rangeSliderActionsFor(width: number, height: number, vizModern: boolean, laneHeight?: number): RangeSliderActions` in `abstract-vs-actions.spec.ts`, used by Task 3's assertions.

- [ ] **Step 1: Add the range-slider helper and its imports to `abstract-vs-actions.spec.ts`**

Add to the import block at the top of the file, keeping the existing alphabetical grouping:

```ts
import { VSRangeSliderModel } from "../model/vs-range-slider-model";
import { RangeSliderActions } from "./range-slider-actions";
```

Add this helper immediately after `calcTableActionsFor` (which ends around `:150`), so all seven factories sit together:

```ts
   // The type that stays outside the anchored set once slices 4 and 5 land, and therefore the
   // control for every "a non-anchored type is untouched" assertion. Case 4 excludes it
   // permanently: it declares no titleVisible, so it has no lane to anchor into. adhocFilter is
   // set because that is the only shape in which it gets a mini-toolbar at all.
   function rangeSliderActionsFor(width: number, height: number, vizModern: boolean,
                                  laneHeight: number = 30): RangeSliderActions
   {
      const model: VSRangeSliderModel = TestUtils.createMockVSRangeSliderModel("RangeSlider1");
      model.objectFormat.width = width;
      model.objectFormat.height = height;
      model.vizModern = vizModern;
      (<any> model).adhocFilter = true;
      (<any> model).titleVisible = true;
      (<any> model).titleFormat = {height: laneHeight};
      return new RangeSliderActions(model, composerContext, false, null, null, popService,
         miniToolbarService);
   }
```

- [ ] **Step 2: Re-point the six calendar-as-control tests in `abstract-vs-actions.spec.ts`**

Each edit swaps `calendarActionsFor` for `rangeSliderActionsFor` and renames the test so it says what it now controls for. The expected values are unchanged except where the range slider's action list differs from the calendar's — those two are called out.

At `:218`, inside `describe("fit ladder")`:

```ts
      it("does not cap a type outside the anchored set even under the gate", () => {
         document.body.classList.add("viz-density-compact");
         expect(rangeSliderActionsFor(2000, 400, true).allowedActionsNum()).toBeGreaterThan(4);
      });
```

At `:346`, inside `describe("cap yields three action buttons plus the kebab")`:

```ts
      it("does not cap or gate-remove chrome for a type outside the anchored set", () => {
         document.body.classList.add("viz-density-compact");
         const ids = rangeSliderActionsFor(2000, 400, true).showingActions
            .reduce((acc, g) => acc.concat(g.actions.map(a => a.id())), [] as string[]);

         expect(ids).not.toContain("more actions");
      });
```

At `:428`:

```ts
      it("does not zero a type outside the anchored set on touch, even under the gate", () => {
         document.body.classList.add("viz-density-compact");
         onTouch();

         expect(rangeSliderActionsFor(2000, 400, true).allowedActionsNum()).toBeGreaterThan(4);
      });
```

At `:486` — **the expected id list changes**, because these are the range slider's overflowed actions rather than the calendar's. The 120px width is kept for the same stated reason: at 2000px nothing overflows and an absent entry would prove nothing.

```ts
      it("does not add the entry for a type outside the anchored set on touch", () => {
         document.body.classList.add("viz-density-compact");
         onTouch();
         // 120px so the toolbar overflows and the kebab has a list at all
         const more = ids(rangeSliderActionsFor(120, 400, true).getMoreActions());

         expect(more).toContain("range-slider unselect");
         expect(more).not.toContain("menu actions");
      });
```

At `:497` — **the expected id list changes** for the same reason:

```ts
      it("keeps the entry for a type outside the anchored set on a pointer device", () => {
         document.body.classList.add("viz-density-compact");

         expect(ids(rangeSliderActionsFor(120, 400, true).getMoreActions()))
            .toContain("menu actions");
      });
```

At `:773`, in the lane-suppression block. **This is the most important of the six** — its comment names the exact regression the whole boundary retargeting exists to prevent, so the comment is preserved and only its subject changes:

```ts
      it("leaves a type outside the anchored set alone at the dense lane", () => {
         // The range slider is outside ANCHORED_ASSEMBLY_TYPES, so the lane rule does not reach it.
         // Guards against the suppression being written as !isAnchoredResident, which would catch
         // every non-anchored type and strip toolbars that ship today.
         expect(ids(rangeSliderActionsFor(2000, 400, true, 20).showingActions).length)
            .toBeGreaterThan(0);
      });
```

- [ ] **Step 3: Run the spec and confirm it is still green at 69 tests**

Run: `cd E:/StyleBI/stylebi-enterprise/community/web && npx ng test portal --include="**/abstract-vs-actions.spec.ts" 2>&1 | tail -8`
Expected: PASS, `Tests 69 passed (69)`. The count must not change — this step swapped subjects, it added and removed nothing.

If the two `toContain` assertions fail, print the actual list with a temporary `console.log(more)` and adjust to what the range slider really emits rather than forcing the calendar's ids. Do not weaken an assertion to `expect(true)`.

- [ ] **Step 4: Re-point the three calendar-as-control assertions in `mini-toolbar.service.spec.ts`**

At `:66`, in `describe("isAnchoredResident")`:

```ts
   it("is false for a type outside the anchored set, however tall its lane", () => {
      expect(isAnchoredResident("VSRangeSlider", true, 30)).toBe(false);
   });
```

At `:117`, in `describe("isAnchoredChromeSuppressed")`:

```ts
   it("is false for a type outside the anchored set, so its toolbar is untouched", () => {
      expect(isAnchoredChromeSuppressed("VSRangeSlider", true, 20)).toBe(false);
   });
```

Leave the `:40-47` membership tests exactly as they are for now — they assert the calendar and container are *not* in the set, which is still true at this task and is what Task 3 flips.

- [ ] **Step 5: Run the service spec and confirm 25 tests still pass**

Run: `cd E:/StyleBI/stylebi-enterprise/community/web && npx ng test portal --include="**/mini-toolbar.service.spec.ts" 2>&1 | tail -8`
Expected: PASS, `Tests 25 passed (25)`.

- [ ] **Step 6: Re-point the calendar-as-control test in the display TL spec**

At `:505` in `vs-object-container.component.display.tl.spec.ts`:

```ts
   it("still hands maxMode to the clamp path for a non-anchored assembly", () => {
      const { comp, miniToolbarSvc } = makeComponent({
         vsObjectActions: [{ showingActions: [], toolbarActions: [] } as any],
      });
      comp.containerRef = scrollless;
      const obj: any = anchoredChart({ objectType: "VSRangeSlider", maxMode: true });
      comp.vsInfo = makeVsInfo([obj]);

      expect(comp.isToolbarAnchored(obj)).toBe(false);
      comp.getToolbarLeft(obj, 0);
      expect(miniToolbarSvc.getToolbarLeft).toHaveBeenCalled();
      const args = (miniToolbarSvc.getToolbarLeft as any).mock.calls[0];
      expect(args[args.length - 1]).toBe(true);
   });
```

Leave the `:729` test ("does not anchor a calendar, whose rollout slice has not landed") alone — Task 3 inverts it.

- [ ] **Step 7: Run the display TL spec — note the target**

Run: `cd E:/StyleBI/stylebi-enterprise/community/web && npx ng run portal:test-tl --include="**/vs-object-container.component.display.tl.spec.ts" 2>&1 | tail -8`
Expected: PASS, `Tests 46 passed (46)`.

**Confirm the output names the file.** `ng test portal` with this `--include` matches nothing and exits 0; only `portal:test-tl` runs it.

- [ ] **Step 8: Commit**

```bash
git add web/projects/portal/src/app/vsobjects/action/abstract-vs-actions.spec.ts web/projects/portal/src/app/vsobjects/objects/mini-toolbar/mini-toolbar.service.spec.ts web/projects/portal/src/app/vsobjects/objects/vs-object-container.component.display.tl.spec.ts
```

```bash
git commit -m "$(cat <<'EOF'
test(vsobjects): control for a non-anchored type with the range slider

The calendar stood in for "a type the rollout has not reached" in eleven
assertions. Its slice is next, so the role moves to the adhoc range slider,
which stays outside the anchored set permanently and, unlike a text or gauge
assembly, has toolbar actions to assert on.

No production change; the suite is green before and after.

Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

### Task 2: Slice 4 — anchor the selection container, kebab-only

**Files:**
- Modify: `web/projects/portal/src/app/vsobjects/objects/mini-toolbar/mini-toolbar.service.ts:41-53`
- Modify: `web/projects/portal/src/app/vsobjects/action/selection-container-actions.ts:37`
- Test: `web/projects/portal/src/app/vsobjects/action/abstract-vs-actions.spec.ts`
- Test: `web/projects/portal/src/app/vsobjects/objects/mini-toolbar/mini-toolbar.service.spec.ts`
- Test: `web/projects/portal/src/app/vsobjects/objects/vs-object-container.component.display.tl.spec.ts`

**Interfaces:**
- Consumes: `rangeSliderActionsFor` from Task 1.
- Produces: `selectionContainerActionsFor(width: number, height: number, vizModern: boolean, laneHeight?: number): SelectionContainerActions` in `abstract-vs-actions.spec.ts`.

- [ ] **Step 1: Write the failing tests**

Add the imports to `abstract-vs-actions.spec.ts`:

```ts
import { VSSelectionContainerModel } from "../model/vs-selection-container-model";
import { SelectionContainerActions } from "./selection-container-actions";
```

Add the factory after `rangeSliderActionsFor`. `createMockVSSelectionContainerModel` builds on `createMockVSCompositeModel`, which already sets `titleVisible: true` and a `titleFormat`, but the lane height is set explicitly here for the same reason every other factory does it — a fixture that means "this is anchored" has to say so:

```ts
   // Slice 4. Same constructor order as TableActions and the selection family — popService
   // positional 6th, miniToolbarService 7th.
   function selectionContainerActionsFor(width: number, height: number, vizModern: boolean,
                                         laneHeight: number = 30): SelectionContainerActions
   {
      const model: VSSelectionContainerModel =
         TestUtils.createMockVSSelectionContainerModel("SelectionContainer1");
      model.objectFormat.width = width;
      model.objectFormat.height = height;
      model.vizModern = vizModern;
      (<any> model).titleVisible = true;
      (<any> model).titleFormat = {height: laneHeight};
      return new SelectionContainerActions(model, composerContext, false, null, null, popService,
         miniToolbarService);
   }
```

Add to the existing `describe("kebabOnly capability")` block (around `:673`), beside the list and tree assertions:

```ts
      it("is kebab-only for the selection container, as for the rest of its family", () => {
         expect((selectionContainerActionsFor(400, 200, false) as any).kebabOnly).toBe(true);
      });
```

Add a new block at the end of `abstract-vs-actions.spec.ts`, before the file's final `});`:

```ts
   // Slice 4. The container's lane is full width rather than titleRatio-split, and its children are
   // excluded from a strip of their own by isMiniToolbarVisible, so anchoring it needs no geometry
   // beyond what the six earlier types already established.
   describe("slice 4: the selection container", () => {
      const ids = (groups: any[]) =>
         groups.reduce((acc, g) => acc.concat(g.actions.map(a => a.id())), [] as string[]);

      it("allows no action buttons at any width, leaving the kebab as the whole strip", () => {
         document.body.classList.add("viz-density-compact");
         expect(selectionContainerActionsFor(2000, 400, true).allowedActionsNum()).toBe(0);
      });

      it("draws the kebab at the compact lane", () => {
         document.body.classList.add("viz-density-compact");
         expect(ids(selectionContainerActionsFor(2000, 400, true, 26).showingActions))
            .toContain("more actions");
      });

      it("draws no chrome at all when the title is hidden", () => {
         document.body.classList.add("viz-density-compact");
         expect(ids(selectionContainerActionsFor(2000, 400, true, 0).showingActions)).toEqual([]);
      });

      it("keeps its uncapped floating toolbar when the gate is off", () => {
         expect(selectionContainerActionsFor(2000, 400, false).allowedActionsNum())
            .toBeGreaterThan(4);
      });
   });
```

Add to `mini-toolbar.service.spec.ts`. Replace the `:40-47` membership test, which asserts both types are absent, with one that asserts the container has landed and the calendar has not yet:

```ts
   it("anchors the selection container, whose slice has landed", () => {
      expect(isAnchoredAssemblyType("VSSelectionContainer")).toBe(true);
   });

   it("does not anchor the calendar, whose rollout slice has not landed", () => {
      expect(isAnchoredAssemblyType("VSCalendar")).toBe(false);
   });
```

Add to `vs-object-container.component.display.tl.spec.ts`, in the same block as the other `isToolbarAnchored` tests (near `:729`). **This is the assertion that fails if someone later "fixes" the selection family uniformly** by adding the container to `isMaxModeSelection`:

```ts
   it("keeps a maximised selection container anchored, unlike the list and tree", () => {
      const { comp } = makeComponent({
         vsObjectActions: [{ showingActions: [], toolbarActions: [] } as any],
      });
      comp.containerRef = scrollless;
      // The container rewrites objectFormat to true coordinates in max mode, so the lane origin the
      // anchored geometry assumes exists. The list and tree put padding constants there instead,
      // which is the whole reason isMaxModeSelection excludes those two and not this.
      const obj: any = TestUtils.withTitleLane(makeVSObject({
         objectType: "VSSelectionContainer",
         vizModern: true,
         maxMode: true,
         objectFormat: makeObjectFormat({ top: 0, left: 0, width: 800, height: 600 }),
      }));
      comp.vsInfo = makeVsInfo([obj]);

      expect(comp.isToolbarAnchored(obj)).toBe(true);
   });
```

For contrast, confirm the list is still excluded — this pins the asymmetry rather than leaving it implied:

```ts
   it("still exempts a maximised selection list, whose objectFormat holds padding constants", () => {
      const { comp } = makeComponent({
         vsObjectActions: [{ showingActions: [], toolbarActions: [] } as any],
      });
      comp.containerRef = scrollless;
      const obj: any = TestUtils.withTitleLane(makeVSObject({
         objectType: "VSSelectionList",
         vizModern: true,
         maxMode: true,
         objectFormat: makeObjectFormat({ top: 30, left: 20, width: 800, height: 600 }),
      }));
      comp.vsInfo = makeVsInfo([obj]);

      expect(comp.isToolbarAnchored(obj)).toBe(false);
      expect(comp.isKebabResident(obj)).toBe(true);
   });
```

- [ ] **Step 2: Run both specs to verify they fail**

Run: `cd E:/StyleBI/stylebi-enterprise/community/web && npx ng test portal --include="**/abstract-vs-actions.spec.ts" 2>&1 | tail -20`
Expected: FAIL. The `kebabOnly` assertion fails `expected false to be true`; the slice-4 block fails because `allowedActionsNum()` returns a large number rather than 0 and `showingActions` is not empty at lane 0.

Run: `cd E:/StyleBI/stylebi-enterprise/community/web && npx ng test portal --include="**/mini-toolbar.service.spec.ts" 2>&1 | tail -20`
Expected: FAIL on `isAnchoredAssemblyType("VSSelectionContainer")` — `expected false to be true`.

Run: `cd E:/StyleBI/stylebi-enterprise/community/web && npx ng run portal:test-tl --include="**/vs-object-container.component.display.tl.spec.ts" 2>&1 | tail -20`
Expected: FAIL on the maximised-container test — `expected false to be true`. The maximised-list test should already PASS; if it fails, stop, because that means the list exclusion is broken before this task changed anything.

- [ ] **Step 3: Add the container to the anchored set**

In `mini-toolbar.service.ts`, replace the slice-3 entry block:

```ts
   // Slice 3, the selection family — anchored but not capped: AbstractVSActions.kebabOnly makes the
   // kebab the whole strip at any width. The container is deliberately absent; it is its own slice.
   "vsselectionlist",
   "vsselectiontree"
```

with:

```ts
   // Slice 3, the selection family — anchored but not capped: AbstractVSActions.kebabOnly makes the
   // kebab the whole strip at any width.
   "vsselectionlist",
   "vsselectiontree",
   // Slice 4, the container. Kebab-only like the rest of its family, so its header is no denser
   // than its children's. Its lane is full width, not titleRatio-split, and isMiniToolbarVisible
   // already keeps its children from drawing strips of their own.
   "vsselectioncontainer"
```

- [ ] **Step 4: Add the `kebabOnly` override**

In `selection-container-actions.ts`, immediately after the constructor's closing brace (`:37`) and before `createMenuActions`:

```ts
   // Case 2: the kebab is the whole strip at any width.
   protected get kebabOnly(): boolean {
      return true;
   }
```

- [ ] **Step 5: Run both specs to verify they pass**

Run: `cd E:/StyleBI/stylebi-enterprise/community/web && npx ng test portal --include="**/abstract-vs-actions.spec.ts" 2>&1 | tail -8`
Expected: PASS, 74 tests (69 baseline + 5 added).

Run: `cd E:/StyleBI/stylebi-enterprise/community/web && npx ng test portal --include="**/mini-toolbar.service.spec.ts" 2>&1 | tail -8`
Expected: PASS, 26 tests (25 baseline + 1, since one membership test split into two).

- [ ] **Step 6: Run the three container TL specs, which exercise the anchoring geometry**

Run each separately — one `--include` per run, and note the `portal:test-tl` target:

```bash
cd E:/StyleBI/stylebi-enterprise/community/web
npx ng run portal:test-tl --include="**/vs-object-container.component.display.tl.spec.ts" 2>&1 | tail -8
```

```bash
npx ng run portal:test-tl --include="**/vs-object-container.component.interaction.tl.spec.ts" 2>&1 | tail -8
```

```bash
npx ng run portal:test-tl --include="**/vs-object-container.component.risk.tl.spec.ts" 2>&1 | tail -8
```

Expected: all three PASS. `display` reports 48 — the 46 baseline plus the two max-mode tests added in Step 1. If `risk` fails on its `VSSelectionContainer containerType` test (`:96`), stop — that test asserts `isMiniToolbarVisible` excludes container *children*, which this task must not have changed, and a failure there means the container itself is being confused with its children.

- [ ] **Step 7: Commit**

```bash
git add web/projects/portal/src/app/vsobjects/objects/mini-toolbar/mini-toolbar.service.ts web/projects/portal/src/app/vsobjects/action/selection-container-actions.ts web/projects/portal/src/app/vsobjects/action/abstract-vs-actions.spec.ts web/projects/portal/src/app/vsobjects/objects/mini-toolbar/mini-toolbar.service.spec.ts
```

```bash
git commit -m "$(cat <<'EOF'
feat(vsobjects): anchor the selection container's kebab in its title lane

Rollout slice 4. Kebab-only, like the selection list and tree, so a container's
header is no denser than the children stacked beneath it.

Needed no new geometry: the container's lane is full width rather than
titleRatio-split, its max-mode model uses true coordinates so it takes no part
in the list/tree exclusion, and isMiniToolbarVisible already keeps its children
from drawing strips of their own.

Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

### Task 3: Slice 5 — anchor the calendar, table treatment

**Files:**
- Modify: `web/projects/portal/src/app/vsobjects/objects/mini-toolbar/mini-toolbar.service.ts:41-56`
- Test: `web/projects/portal/src/app/vsobjects/action/abstract-vs-actions.spec.ts`
- Test: `web/projects/portal/src/app/vsobjects/objects/mini-toolbar/mini-toolbar.service.spec.ts`
- Test: `web/projects/portal/src/app/vsobjects/objects/vs-object-container.component.display.tl.spec.ts:729`

**Interfaces:**
- Consumes: `calendarActionsFor` (already in the spec at `:69`), `selectionContainerActionsFor` from Task 2.
- Produces: nothing later tasks depend on.

- [ ] **Step 1: Write the failing tests**

Add to `describe("kebabOnly capability")` in `abstract-vs-actions.spec.ts`, beside the negative assertions for the table family:

```ts
      it("is not kebab-only for the calendar, which takes the table treatment", () => {
         expect((calendarActionsFor(400, 200, false) as any).kebabOnly).toBe(false);
      });
```

Add a new block after the slice-4 block:

```ts
   // Slice 5. The calendar takes the table treatment — three action buttons plus the kebab — because
   // it has the largest toolbar in the rollout at six actions. Its pre-density lane is 36 rather
   // than the defh 20 every other type carries, so it clears the threshold marked or not.
   describe("slice 5: the calendar", () => {
      const ids = (groups: any[]) =>
         groups.reduce((acc, g) => acc.concat(g.actions.map(a => a.id())), [] as string[]);

      it("caps at three actions plus the kebab under the gate", () => {
         document.body.classList.add("viz-density-compact");
         expect(calendarActionsFor(2000, 400, true).allowedActionsNum()).toBe(4);
      });

      it("draws chrome at the compact lane", () => {
         document.body.classList.add("viz-density-compact");
         expect(ids(calendarActionsFor(2000, 400, true, 26).showingActions).length)
            .toBeGreaterThan(0);
      });

      it("draws chrome at its taller author-set lane", () => {
         document.body.classList.add("viz-density-compact");
         expect(ids(calendarActionsFor(2000, 400, true, 36).showingActions).length)
            .toBeGreaterThan(0);
      });

      it("draws no chrome at all when the title is hidden", () => {
         document.body.classList.add("viz-density-compact");
         expect(ids(calendarActionsFor(2000, 400, true, 0).showingActions)).toEqual([]);
      });

      it("keeps its uncapped floating toolbar when the gate is off", () => {
         expect(calendarActionsFor(2000, 400, false).allowedActionsNum()).toBeGreaterThan(4);
      });
   });
```

In `mini-toolbar.service.spec.ts`, replace the two membership tests Task 2 left with the final permanent shape. Also rewrite the `describe` block's leading comment, which currently describes a rollout in progress:

```ts
// The anchored set, asserted explicitly rather than left implied. It is now permanent rather than a
// rollout stage: every type with a title lane is in it, and the range slider is the one deliberate
// exclusion.
describe("isAnchoredAssemblyType", () => {
   it("anchors every type with a title lane", () => {
      expect(isAnchoredAssemblyType("VSChart")).toBe(true);
      expect(isAnchoredAssemblyType("VSTable")).toBe(true);
      expect(isAnchoredAssemblyType("VSCrosstab")).toBe(true);
      expect(isAnchoredAssemblyType("VSCalcTable")).toBe(true);
      expect(isAnchoredAssemblyType("VSSelectionList")).toBe(true);
      expect(isAnchoredAssemblyType("VSSelectionTree")).toBe(true);
      expect(isAnchoredAssemblyType("VSSelectionContainer")).toBe(true);
      expect(isAnchoredAssemblyType("VSCalendar")).toBe(true);
   });
```

Then **delete the two tests Task 2 added** — `"anchors the selection container, whose slice has landed"` and `"does not anchor the calendar, whose rollout slice has not landed"`. The enumeration above now covers both, and the second is false as of this task. Keep the case-insensitivity, range-slider and null tests unchanged.

Add a new test to the same block, which is the regression the boundary retargeting exists to prevent:

```ts
   // A laneless type must stay out of the set. Deleting the type test outright — which this
   // predicate's comment used to promise — would make isAnchoredChromeSuppressed true for every one
   // of these under the gate, emptying the toolbars they ship with today.
   it("does not anchor a type with no title lane", () => {
      expect(isAnchoredAssemblyType("VSText")).toBe(false);
      expect(isAnchoredAssemblyType("VSGauge")).toBe(false);
      expect(isAnchoredAssemblyType("VSImage")).toBe(false);
   });
```

And to `describe("isAnchoredChromeSuppressed")`:

```ts
   it("is false for a laneless type under the gate, whose toolbar must be untouched", () => {
      expect(isAnchoredChromeSuppressed("VSText", true, 0)).toBe(false);
      expect(isAnchoredChromeSuppressed("VSGauge", true, 0)).toBe(false);
   });
```

In `vs-object-container.component.display.tl.spec.ts`, invert the test at `:729`:

```ts
   it("anchors a calendar, whose rollout slice has landed", () => {
      const { comp } = makeComponent({
         vsObjectActions: [{ showingActions: [], toolbarActions: [] } as any],
      });
      comp.containerRef = scrollless;
      const obj: any = TestUtils.withTitleLane(makeVSObject({
         objectType: "VSCalendar",
         vizModern: true,
         objectFormat: makeObjectFormat({ top: 40, left: 250, width: 600, height: 300 }),
      }));
      comp.vsInfo = makeVsInfo([obj]);

      expect(comp.isToolbarAnchored(obj)).toBe(true);
   });
```

- [ ] **Step 2: Run the three specs to verify they fail**

```bash
cd E:/StyleBI/stylebi-enterprise/community/web
npx ng test portal --include="**/abstract-vs-actions.spec.ts" 2>&1 | tail -20
```
Expected: FAIL — `allowedActionsNum()` returns 50 rather than 4, and `showingActions` is non-empty at lane 0.

```bash
npx ng test portal --include="**/mini-toolbar.service.spec.ts" 2>&1 | tail -20
```
Expected: FAIL on `isAnchoredAssemblyType("VSCalendar")` — `expected false to be true`.

```bash
npx ng run portal:test-tl --include="**/vs-object-container.component.display.tl.spec.ts" 2>&1 | tail -20
```
Expected: FAIL on the inverted anchoring test.

- [ ] **Step 3: Add the calendar to the anchored set**

In `mini-toolbar.service.ts`, append after the container entry added in Task 2:

```ts
   ,
   // Slice 5, the calendar. The table treatment unmodified: six toolbar actions is the largest set
   // in the rollout, so a strip that surfaces three of them earns its space. Its dropdown variant
   // mounts an inline mini-menu instead and is excluded by isMiniToolbarVisible, so the two never
   // co-render.
   "vscalendar"
```

Write it as a clean list rather than a leading comma — the final set literal should read:

```ts
const ANCHORED_ASSEMBLY_TYPES: ReadonlySet<string> = new Set<string>([
   "vschart",
   // Slice 2, the table family. Table and calc table already emitted their stable actions first;
   // crosstab was reordered to match. All three inherit the chart's treatment unchanged, and take
   // the flush full-width lane the container's padding fallbacks resolve them to.
   "vstable",
   "vscrosstab",
   "vscalctable",
   // Slice 3, the selection family — anchored but not capped: AbstractVSActions.kebabOnly makes the
   // kebab the whole strip at any width.
   "vsselectionlist",
   "vsselectiontree",
   // Slice 4, the container. Kebab-only like the rest of its family, so its header is no denser
   // than its children's. Its lane is full width, not titleRatio-split, and isMiniToolbarVisible
   // already keeps its children from drawing strips of their own.
   "vsselectioncontainer",
   // Slice 5, the calendar. The table treatment unmodified: six toolbar actions is the largest set
   // in the rollout, so a strip that surfaces three of them earns its space. Its dropdown variant
   // mounts an inline mini-menu instead and is excluded by isMiniToolbarVisible, so the two never
   // co-render.
   "vscalendar"
]);
```

- [ ] **Step 4: Run the three specs to verify they pass**

```bash
cd E:/StyleBI/stylebi-enterprise/community/web
npx ng test portal --include="**/abstract-vs-actions.spec.ts" 2>&1 | tail -8
```
Expected: PASS, 80 tests (74 after Task 2 + 6 added).

```bash
npx ng test portal --include="**/mini-toolbar.service.spec.ts" 2>&1 | tail -8
```
Expected: PASS, 26 tests — unchanged from Task 2. Two tests were deleted and two added: the enumeration absorbed the container and calendar assertions, and the two laneless-type tests replaced them.

```bash
npx ng run portal:test-tl --include="**/vs-object-container.component.display.tl.spec.ts" 2>&1 | tail -8
```
Expected: PASS, 48 tests — the count Task 2 established; this task inverts a test rather than adding one.

- [ ] **Step 5: Run the two mini-toolbar component specs, which render the strip**

```bash
cd E:/StyleBI/stylebi-enterprise/community/web
npx ng test portal --include="**/mini-toolbar.component.spec.ts" 2>&1 | tail -8
npx ng run portal:test-tl --include="**/mini-toolbar.component.tl.spec.ts" 2>&1 | tail -8
```
Expected: both PASS. Note the two different targets.

- [ ] **Step 6: Commit**

```bash
git add web/projects/portal/src/app/vsobjects/objects/mini-toolbar/mini-toolbar.service.ts web/projects/portal/src/app/vsobjects/action/abstract-vs-actions.spec.ts web/projects/portal/src/app/vsobjects/objects/mini-toolbar/mini-toolbar.service.spec.ts web/projects/portal/src/app/vsobjects/objects/vs-object-container.component.display.tl.spec.ts
```

```bash
git commit -m "$(cat <<'EOF'
feat(vsobjects): anchor the calendar's strip in its title lane

Rollout slice 5, and the last one. The calendar takes the table treatment
unmodified: at six toolbar actions it has the largest set in the rollout, so a
strip surfacing three of them earns its space.

Its dropdown variant needs no special case. That variant mounts an inline
mini-menu in its own header, and isMiniToolbarVisible already suppresses the
anchored strip for it, so the two cannot co-render.

Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

### Task 4: Retarget the rollout boundary in the comments

The predicates are now permanent. Four comments still describe them as scaffolding and promise a deletion that would cause a regression. This task changes no behaviour — it is the record that stops the next reader from carrying out the promise.

**Files:**
- Modify: `web/projects/portal/src/app/vsobjects/objects/mini-toolbar/mini-toolbar.service.ts:28-40, :85-89`
- Modify: `web/projects/portal/src/app/vsobjects/action/abstract-vs-actions.ts:139-141, :455-464`
- Modify: `web/projects/portal/src/app/vsobjects/objects/vs-object-container.component.ts:463-472`

**Interfaces:**
- Consumes: the set from Task 3.
- Produces: nothing.

- [ ] **Step 1: Rewrite the set's doc-comment in `mini-toolbar.service.ts`**

Replace the block at `:28-40` — everything from `* height bands and the resident kebab.` down to the closing `*/` above the set literal:

```ts
/**
 * The assembly types whose toolbar is anchored into the title lane, with the strip's height bands
 * and the resident kebab. A subset of hasMiniToolbar() below, which enumerates the types that get a
 * strip at all, and the two now differ by exactly one entry: the range slider, which declares no
 * titleVisible and so has no lane to anchor into. See
 * chart-card-design/Anchoring beyond charts - discussion.md, Case 4.
 *
 * PERMANENT. This was the rollout boundary while the slices landed; all five have, so it is now the
 * anchored set. Do not delete it and leave the .viz-modern gate as the only condition — an earlier
 * revision of this comment promised exactly that, and it is unsafe. isAnchoredChromeSuppressed
 * would then be true for every laneless assembly under the gate (text, gauge, image, spinner, and
 * the rest resolve to a zero lane), emptying showingActions for all of them. The composer's mobile
 * toolbar renders that list for whatever assembly is focused, so those toolbars would go blank.
 */
```

- [ ] **Step 2: Rewrite the shared-predicate note at `:85-89`**

Replace the tail of `isAnchoredResident`'s doc-comment, the sentence beginning `Shared by`:

```ts
 * Shared by VSObjectContainerComponent.isKebabResident and AbstractVSActions.resident so the two
 * conditions cannot drift apart.
 */
```

- [ ] **Step 3: Rewrite the `resident` comment in `abstract-vs-actions.ts:139-141`**

```ts
   // Delegates to isAnchoredResident, the one anchored-set definition in mini-toolbar.service.ts, so
   // this and the container's isKebabResident cannot drift apart.
   private get resident(): boolean {
```

- [ ] **Step 4: Narrow the dismissal comment in `abstract-vs-actions.ts`**

In the block at `:455-464`, replace the one sentence about types awaiting a slice:

```ts
            // dismissal by right-click. After slices 4 and 5 the adhoc range slider is the only
            // such type.
```

Leave the rest of that comment — the gating rationale, the binding-pane and wizard exclusions — exactly as it is.

- [ ] **Step 5: Rewrite the `isToolbarAnchored` comment in `vs-object-container.component.ts:463-472`**

Replace the two `TEMPORARY` lines at the top of the block, keeping everything from `Excluded only for the selection family` onward verbatim:

```ts
   // Delegates to isAnchoredResident, the one anchored-set definition in mini-toolbar.service.ts. See
   // chart-card-design/Anchoring beyond charts - discussion.md.
   // Excluded only for the selection family in max mode (isMaxModeSelection below): those models
```

- [ ] **Step 6: Confirm no `TEMPORARY` marker for this rollout survives**

Run:

```bash
cd E:/StyleBI/stylebi-enterprise/community/web/projects/portal/src/app
grep -rn "TEMPORARY" vsobjects/ --include=*.ts | grep -v spec
```

Expected: no output. If a line remains, read it — if it is about the rollout boundary, rewrite it; if it is about something else, leave it and note it in the commit body.

- [ ] **Step 7: Re-run all four unit specs, since comments can hide a stray edit**

```bash
cd E:/StyleBI/stylebi-enterprise/community/web
npx ng test portal --include="**/abstract-vs-actions.spec.ts" 2>&1 | tail -6
npx ng test portal --include="**/mini-toolbar.service.spec.ts" 2>&1 | tail -6
npx ng test portal --include="**/mini-toolbar.component.spec.ts" 2>&1 | tail -6
npx ng run portal:test-tl --include="**/vs-object-container.component.display.tl.spec.ts" 2>&1 | tail -6
```
Expected: all PASS, with the counts Task 3 established — 80, 26, unchanged, 48.

- [ ] **Step 8: Lint the four touched production files**

Run: `cd E:/StyleBI/stylebi-enterprise/community/web && npx ng lint portal 2>&1 | tail -20`
Expected: no new errors. Pre-existing warnings elsewhere in the project are not this task's to fix; compare against `git stash` output only if something looks attributable.

- [ ] **Step 9: Commit**

```bash
git add web/projects/portal/src/app/vsobjects/objects/mini-toolbar/mini-toolbar.service.ts web/projects/portal/src/app/vsobjects/action/abstract-vs-actions.ts web/projects/portal/src/app/vsobjects/objects/vs-object-container.component.ts
```

```bash
git commit -m "$(cat <<'EOF'
docs(vsobjects): make the anchored set permanent, and say why it cannot go

All five rollout slices have landed, so ANCHORED_ASSEMBLY_TYPES is the anchored
set rather than a rollout stage.

Its comment promised that the last slice would delete the predicate and leave
the modern gate as the only condition. That is unsafe and the comment now says
so: isAnchoredChromeSuppressed would become true for every laneless assembly
under the gate, and the composer's mobile toolbar renders showingActions for
whatever assembly is focused, so those toolbars would go blank.

Two further claims corrected. The mobile relaxation of the "menu actions"
wrapper is orthogonal to the rollout and does not ride along with it, and the
dismissal note now names the adhoc range slider as the only type still reaching
it by right-click.

Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

### Task 5: Roadmap and design-doc record

**Files:**
- Modify: `docs/superpowers/specs/lookfeel/chart-card-roadmap.md`
- Modify: `docs/superpowers/specs/lookfeel/2026-09-01-anchored-rollout-slices-4-5-design.md`

**Interfaces:**
- Consumes: the commit hashes from Tasks 2, 3 and 4.
- Produces: nothing.

- [ ] **Step 1: Collect the hashes**

```bash
cd E:/StyleBI/stylebi-enterprise/community && git log --oneline -5
```

Record the three hashes for slice 4, slice 5 and the boundary retargeting.

- [ ] **Step 2: Add the Done-table rows**

In `chart-card-roadmap.md`'s `## Done` table, append three rows in the established `| Item | Commit |` shape, citing by subject as that section instructs:

```markdown
| **Rollout slice 4 — the selection container**, kebab-only like the rest of its family | `<hash>` |
| **Rollout slice 5 — the calendar**, the table treatment unmodified; the last slice | `<hash>` |
| **The anchored set made permanent** — the four TEMPORARY markers rewritten, and the promised deletion of the type test recorded as unsafe: it would empty every laneless assembly's toolbar under the gate | `<hash>` |
```

- [ ] **Step 3: Update the "Unblocked by `380705bc1`" section**

That section currently says slices 4 and 5 are pending and tells the reader to scope the resting kebab by pointer capability first. Replace its "Rollout slices 4 and 5" paragraph with:

```markdown
**Rollout slices 4 and 5 have SHIPPED** — the container and the calendar. `ANCHORED_ASSEMBLY_TYPES`
is no longer a rollout boundary; it is the permanent anchored set, and it differs from
`hasMiniToolbar()` by exactly one entry, the adhoc range slider.

**Its promised deletion is unsafe and must not be carried out.** The comment used to say the last
slice would delete the predicate, leaving the `.viz-modern` gate as the only condition. Do that and
`isAnchoredChromeSuppressed` becomes true for every laneless assembly under the gate — they all
resolve to a zero lane — and the composer's mobile toolbar, which renders `showingActions` for
whatever assembly is focused, goes blank for text, gauge, image and spinner. The reasoning is now in
the code as well as here.
```

- [ ] **Step 4: Move §10.1 to its own line in "Ready now" with its blocker stated**

The "Ready now" table's §10.1 row calls it S and browser-only without recording that it is blocked. Append to that row's Note cell:

```markdown
 **BLOCKED ON A DECISION, not on sequencing.** `github.md`'s 2026-08-12 entry: the pointer query would leave every kebab-only family with no chrome at all at rest on a desktop, which is not what `kebabOnly` was approved for. Three of the eight anchored types are kebab-only now that the container has joined them, so the question is larger than when it was written. Needs its own design.
```

- [ ] **Step 5: Close the design doc**

Append to `2026-09-01-anchored-rollout-slices-4-5-design.md`:

```markdown
---

## What the implementation found

Recorded after the branch shipped as three commits.

**Eleven existing tests used the calendar as their "type outside the anchored set" control** — six in `abstract-vs-actions.spec.ts`, three in `mini-toolbar.service.spec.ts`, two in `vs-object-container.component.display.tl.spec.ts`. Slice 5 destroys that role. The successor is the adhoc range slider: it is the one `hasMiniToolbar()` type that stays outside the set permanently, and unlike a text or gauge assembly it has toolbar actions to assert on — neither `TextActions` nor `GaugeActions` defines `createToolbarActions`, so their `showingActions` is empty and an assertion on it proves nothing. Measured before the migration: at 2000×400, modern, lane 20, the range slider yields four actions and `allowedActionsNum()` 50.

**The migration was sequenced first, as its own commit, while the calendar was still outside the set.** That is what made it provably behaviour-neutral — the suite stayed green across it. Folded into the slice, it would have produced eleven simultaneous failures with two unrelated causes.
```

- [ ] **Step 6: Commit**

```bash
git add docs/superpowers/specs/lookfeel/chart-card-roadmap.md docs/superpowers/specs/lookfeel/2026-09-01-anchored-rollout-slices-4-5-design.md
```

```bash
git commit -m "$(cat <<'EOF'
docs(lookfeel): record the anchored rollout as complete

Slices 4 and 5 in the Done table, the boundary section rewritten to say the
predicate is permanent and why deleting it is unsafe, and 10.1 marked blocked on
a decision rather than on sequencing.

Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Manual checks

Automated tests prove the predicates and the action arithmetic agree with each other. They do not prove a screen agrees with either. Run these in a browser before calling the work done — the org must have `viewsheet.modernVisualization` unset or true, which is now the default.

1. **Container, compact, modern** — kebab alone, right-aligned in the 26px lane, no action buttons at any width.
2. **Container with populated children** — exactly one strip in the stack; no child list draws its own.
3. **Container, max mode** — strip stays in the lane, does not jump to the viewport corner.
4. **Container, title hidden** — no strip, no kebab; right-click still reaches the full menu.
5. **Calendar, compact, modern** — up to three action buttons plus the kebab in the 26px lane; the kebab opens the overflow.
6. **Calendar, author-set 36px lane** — same, in the taller lane.
7. **Dropdown calendar** — the inline mini-menu only; no second strip.
8. **Calendar, title hidden** — no chrome; right-click reaches the menu.
9. **Both types, dark** — glyph tone resolves against the dark card, as the six earlier types do.
10. **A text or gauge assembly in a modern org** — floating toolbar unchanged. This is the Task 4 regression check and the most important one on the list.
11. **Composer on a touch device, container focused** — the mobile toolbar renders; it does not come back empty.
12. **Adhoc range slider, modern** — floating toolbar unchanged.

---

## Out of scope, and deliberately so

- **§10.1, resting visibility by pointer capability.** Blocked on what a kebab-only family does at rest on a desktop. Now larger than when it was written: three of the eight anchored types are kebab-only.
- **Case 4, the range slider drawing no chrome.** One line away — adding `"vsrangeslider"` to the set — and deliberately not taken, because it is a behaviour change to a type this work is not otherwise touching.
- **The mobile relaxation of the "menu actions" wrapper.** Orthogonal to the rollout; Task 4 corrects the comment that claimed otherwise but changes no behaviour.
