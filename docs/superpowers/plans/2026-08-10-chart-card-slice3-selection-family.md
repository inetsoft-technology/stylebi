# Chart Card Slice 3 — Selection Family Anchoring Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Bring `VSSelectionList` and `VSSelectionTree` into the anchored mini-toolbar set with a kebab-only strip, without letting the resident kebab cover the pending-Apply control.

**Architecture:** Two type strings join `ANCHORED_ASSEMBLY_TYPES`, which drives both `AbstractVSActions.resident` and `VSObjectContainerComponent.isToolbarAnchored`. A new `kebabOnly` capability getter on `AbstractVSActions` makes `allowedActionsNum()` return `0` for these two types, which routes them permanently down code paths that already exist and are already tested for touch and the 32–56px height band — including `flattenedMoreActions()`. Nothing new is rendered. Two collisions are resolved by geometry rather than by new mechanism: `rightEdgeReserve()` exempts the family, and `.pending-alert` is offset clear of the pill under the gate.

**Tech Stack:** Angular 21.2 / TypeScript 5.9, Vitest 4.1.7, SCSS. All files under `community/web/projects/portal/src/app/vsobjects/`.

**Design:** `community/docs/superpowers/specs/lookfeel/chart-card-slice3-selection-design.md` — read it before starting. Section references below (§4, §5.2, …) point into it.

## Global Constraints

- **Branch:** `viz-updates` in the `community/` submodule. No PR — the branch accumulates the rollout and is not proposed into `epic-74519` until the feature is complete.
- **Community-only.** No file outside `community/` is touched.
- **Everything is gated on `.viz-modern` except Task 4.** Gate-off behaviour must stay byte-identical. No selection spec currently references `viz-modern` or `isVizModern`, and all existing assertions in `selection-list-actions.spec.ts` (8 tests, 21 assertions), `selection-tree-actions.spec.ts` (5 tests, 18 assertions) and both `.snap` files must stay green **unedited**.
- **The gate is read in tests as `document.body.classList.add("viz-modern")`**, removed in `afterEach`. This is the established idiom in `abstract-vs-actions.spec.ts`; do not mock `GuiTool.isVizModern` instead.
- **`VSSelectionContainer` is out of scope** — its own slice. So are `VSCalendar`, dropdown-variant selections, selection children of a container, and `VSRangeSlider` (permanently).
- **No comments in `.html` files.** Angular templates get no comments in this codebase. (No template edits are planned; this applies if one becomes necessary.)
- **Keep inline comments to a short clause**, not full sentences, except where an existing neighbouring comment in the same file is already long-form — match the local density. Measured for this slice: `abstract-vs-actions.ts`, `mini-toolbar.service.ts`, `vs-object-container.component.ts` and `mini-toolbar.component.scss` are heavily long-form, so the long comments this plan writes there are in idiom. `vs-selection.component.scss` has exactly one short inline comment, and `selection-list-actions.ts` / `selection-tree-actions.ts` have none — comments in those three stay to one or two clauses. **Do not expand them; the reasoning belongs in the design doc and the commit message.**
- **Never reference ticket numbers, PR numbers, or design-doc filenames in source comments.** Reference behaviour and code instead. (`chart-card-design/…` citations already present in these files are pre-existing; do not add new ones.)
- **Actions-class constructor order** is `(model, contextProvider, securityEnabled, stateProvider, dataTipService, popService, miniToolbarService)` for table, crosstab, calc-table, calendar, selection-list and selection-tree. **`ChartActions` differs** — `popService` is positional 2nd. Verified against source; do not assume symmetry.
- **Never run the full `*.tl.spec.ts` suite.** Always scope it with `--include`; the full suite times out and orphans multi-GB node workers.

### Commands

Run from `community/web`:

```bash
# Portal unit specs, scoped
npx ng test --include='**/abstract-vs-actions.spec.ts'

# Portal testing-library specs, scoped (NEVER unscoped)
npx ng run portal:test-tl --include='**/vs-object-container.component.display.tl.spec.ts'

# Full portal + em suites (Task 5 only)
npm run test:portal
npm run test:em
```

---

## File Structure

| File | Responsibility | Task |
|---|---|---|
| `vsobjects/objects/vs-object-container.component.ts` | Anchored strip geometry. `rightEdgeReserve()` gains a family exemption. | 1 |
| `vsobjects/objects/vs-object-container.component.display.tl.spec.ts` | Container geometry assertions, "Group 11". | 1 |
| `vsobjects/action/abstract-vs-actions.ts` | Shared toolbar/menu construction and the fit ladder. Gains the `kebabOnly` capability and its early return. | 2 |
| `vsobjects/action/selection-list-actions.ts` | Selection list's action sets. Overrides `kebabOnly`; gains the ungated Max Mode menu entry. | 2, 4 |
| `vsobjects/action/selection-tree-actions.ts` | Same, for the tree. | 2, 4 |
| `vsobjects/action/abstract-vs-actions.spec.ts` | The gated behaviour suite. Gains selection helper factories and two new `describe` blocks. | 2, 3 |
| `vsobjects/objects/mini-toolbar/mini-toolbar.service.ts` | `ANCHORED_ASSEMBLY_TYPES` — the one rollout boundary. | 3 |
| `vsobjects/objects/mini-toolbar/mini-toolbar.service.spec.ts` | The boundary asserted explicitly. Types move from the excluded test to the anchored one, and the two-kebabs-never-co-render guard lands here — its subject is `isMiniToolbarVisible()`, a pure function of the model. | 3 |
| `vsobjects/objects/selection/vs-selection.component.scss` | Selection component styling. `.pending-alert` gains a gated offset. | 3 |
| `vsobjects/action/selection-list-actions.spec.ts` | Existing list assertions. Gains the ungated menu-entry assertions. | 4 |
| `vsobjects/action/selection-tree-actions.spec.ts` | Same, for the tree. | 4 |

### Task ordering and why it is safe

Tasks 1 and 2 are **provably inert**: `rightEdgeReserve()` is reached only from `getAnchoredToolbarWidth()`, which the container calls only for anchored types; and `allowedActionsNum()`'s new early return sits behind the existing `if(!this.resident) { return num; }` guard. Neither changes production behaviour until Task 3 adds the two types to the anchored set.

**Task 3 is the activating commit, and it must be complete in one commit** — it turns anchoring on *and* moves the pending-Apply icon out from under the pill. Splitting those would leave a commit where a resident kebab sits on top of a live Apply control (§4). A reviewer would not approve one without the other.

Task 4 lands after Task 3 because its key assertion — that the kebab shows **one** Maximize row, not two — only becomes observable once `flattenedMoreActions()` is on the live path.

---

## Task 1: Exempt the selection family from the right-edge reserve

`rightEdgeReserve()` currently returns `SORT_CONTROL_RESERVE` (22px) whenever `titleVisible === false`, for every anchored type. That reserve exists for one thing: the table's `.vs-header-cell-button-sort` at `right: 2px; width: 20px`. A selection cell has no equivalent (§5.2).

If selections inherited it, the title-hidden pill would sit at offset `22 → 54` from the right edge while the gated `.pending-alert` (Task 3) sits at `5 → 25`, overlapping by 3px — and clearing that would need a second, larger CSS offset whose value came from a private TypeScript constant in a different file.

**Inert until Task 3:** the container calls `getAnchoredToolbarWidth()` only for anchored types, and selections are not yet anchored.

**Files:**
- Modify: `community/web/projects/portal/src/app/vsobjects/objects/vs-object-container.component.ts` (`rightEdgeReserve`, ~line 546)
- Test: `community/web/projects/portal/src/app/vsobjects/objects/vs-object-container.component.display.tl.spec.ts` ("Group 11 — anchored toolbar geometry", ends ~line 480)

**Interfaces:**
- Consumes: `Tool.equalsIgnoreCase` (already imported at line 33); `makeComponent`, `makeVSObject`, `makeObjectFormat`, `makeVsInfo` from `./vs-object-container.component.test-helpers`; the `scrollless` const already declared inside Group 11.
- Produces: `rightEdgeReserve()` returning `0` for `VSSelectionList` / `VSSelectionTree` regardless of `titleVisible`. Task 3's `.pending-alert` offset depends on this being a single value across both title states.

- [ ] **Step 1: Write the failing tests**

Append inside the `describe("Group 11 — anchored toolbar geometry: objectFormat-only, max mode included", …)` block, after the existing `it("reserves nothing when a title lane exists", …)` and before `it("does not anchor a calendar, …")`:

```ts
   // The reserve's whole purpose is the table's .vs-header-cell-button-sort, which a selection cell
   // has no equivalent of. A selection's right-edge occupant is the pending-Apply icon, and the
   // gated .pending-alert offset moves that clear of the pill instead — so the pill is flush here
   // in both title states, and the CSS offset stays one value rather than two.
   it("reserves nothing for a title-hidden selection list", () => {
      const { comp } = makeComponent({
         vsObjectActions: [{ showingActions: [], toolbarActions: [] } as any],
      });
      comp.containerRef = scrollless;
      const obj: any = makeVSObject({
         objectType: "VSSelectionList",
         objectFormat: makeObjectFormat({ top: 40, left: 250, width: 600, height: 300 }),
      });
      obj.titleVisible = false;
      comp.vsInfo = makeVsInfo([obj]);

      expect(comp.getAnchoredToolbarWidth(obj)).toBe(600);
      expect(comp.getToolbarLeft(obj, 0) + comp.getAnchoredToolbarWidth(obj)).toBe(250 + 600);
   });

   it("reserves nothing for a title-hidden selection tree either", () => {
      const { comp } = makeComponent({
         vsObjectActions: [{ showingActions: [], toolbarActions: [] } as any],
      });
      comp.containerRef = scrollless;
      const obj: any = makeVSObject({
         objectType: "VSSelectionTree",
         objectFormat: makeObjectFormat({ top: 40, left: 250, width: 600, height: 300 }),
      });
      obj.titleVisible = false;
      comp.vsInfo = makeVsInfo([obj]);

      expect(comp.getAnchoredToolbarWidth(obj)).toBe(600);
   });
```

The existing `it("reserves the sort control's footprint when the title is hidden", …)` — which asserts `600 - 22` for a `VSSelectionList`-adjacent case using `VSTable` — is the guard that the exemption does **not** leak to the table family. Do not edit it; it must stay green.

- [ ] **Step 2: Run the tests to verify they fail**

```bash
cd community/web
npx ng run portal:test-tl --include='**/vs-object-container.component.display.tl.spec.ts'
```

Expected: both new tests FAIL with `expected 578 to be 600` — `600 - 22`, the reserve still applying.

- [ ] **Step 3: Implement the exemption**

In `vs-object-container.component.ts`, replace:

```ts
   private static rightEdgeReserve(object: VSObjectModel): number {
      return (<any> object).titleVisible === false ? VSObjectContainer.SORT_CONTROL_RESERVE : 0;
   }
```

with:

```ts
   private static rightEdgeReserve(object: VSObjectModel): number {
      // Exempt: the reserve is the table's sort control, which a selection cell has no equivalent
      // of. A selection's right-edge occupant is the pending-Apply icon, which .pending-alert in
      // vs-selection.component.scss offsets clear of the pill under the gate instead — reserving
      // here too would make that CSS position depend on SORT_CONTROL_RESERVE below.
      if(Tool.equalsIgnoreCase(object.objectType, "VSSelectionList") ||
         Tool.equalsIgnoreCase(object.objectType, "VSSelectionTree"))
      {
         return 0;
      }

      return (<any> object).titleVisible === false ? VSObjectContainer.SORT_CONTROL_RESERVE : 0;
   }
```

Leave the existing doc comment above the method and the `SORT_CONTROL_RESERVE` declaration below it untouched.

- [ ] **Step 4: Run the tests to verify they pass**

```bash
cd community/web
npx ng run portal:test-tl --include='**/vs-object-container.component.display.tl.spec.ts'
```

Expected: PASS, all of Group 11 green including the pre-existing table reserve test.

- [ ] **Step 5: Commit**

```bash
cd community
git add web/projects/portal/src/app/vsobjects/objects/vs-object-container.component.ts \
        web/projects/portal/src/app/vsobjects/objects/vs-object-container.component.display.tl.spec.ts
git commit -m "fix(vsobjects): exempt the selection family from the sort-control reserve

The 22px right-edge reserve exists for the table's .vs-header-cell-button-sort.
A selection cell has no equivalent; its right-edge occupant is the pending-Apply
icon, which is moved out from under the pill in CSS instead. Reserving here as
well would put that CSS offset behind a private constant in this file.

Inert until the selection family joins the anchored set: getAnchoredToolbarWidth
is only reached for anchored types.

Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>"
```

---

## Task 2: Declare the `kebabOnly` capability

Case 2 (settled, in `Anchoring beyond charts - discussion.md`): selection list and tree show **a single kebab in the header, always** — no action buttons at any width. This is a permanent property of the family, not a rollout stage, which is why it is a capability getter rather than a second entry in the `TEMPORARY` type-set block (§2 decision 5).

`allowedActionsNum() === 0` is an already-shipped state — it is what touch and the 32–56px height band produce — so everything downstream already handles it.

**Inert until Task 3:** the new early return sits after `if(!this.resident) { return num; }`, and `resident` requires membership in `ANCHORED_ASSEMBLY_TYPES`.

**Files:**
- Modify: `community/web/projects/portal/src/app/vsobjects/action/abstract-vs-actions.ts` (add getter near `resident`, ~line 139; add early return inside `allowedActionsNum()`, ~line 151)
- Modify: `community/web/projects/portal/src/app/vsobjects/action/selection-list-actions.ts`
- Modify: `community/web/projects/portal/src/app/vsobjects/action/selection-tree-actions.ts`
- Test: `community/web/projects/portal/src/app/vsobjects/action/abstract-vs-actions.spec.ts`

**Interfaces:**
- Consumes: `AbstractVSActions.resident` (private getter, already present); `TestUtils.createMockVSSelectionListModel(name)`, `createMockVSSelectionTreeModel(name)`, `createMockVSCrosstabModel(name)`, `createMockVSCalcTableModel(name)`; the spec's existing `popService`, `composerContext`, `miniToolbarService` consts and its `actionsFor` / `tableActionsFor` helpers.
- Produces: `protected get kebabOnly(): boolean` on `AbstractVSActions`, default `false`, overridden `true` on `SelectionListActions` and `SelectionTreeActions`. Task 3 relies on `allowedActionsNum()` returning `0` for these two once they are anchored. The spec helpers `selectionListActionsFor(width, height)` and `selectionTreeActionsFor(width, height)` created here are used again in Task 3.

- [ ] **Step 1: Write the failing tests**

In `abstract-vs-actions.spec.ts`, add these imports alongside the existing ones:

```ts
import { VSCalcTableModel } from "../model/vs-calc-table-model";
import { VSCrosstabModel } from "../model/vs-crosstab-model";
import { VSSelectionListModel } from "../model/vs-selection-list-model";
import { VSSelectionTreeModel } from "../model/vs-selection-tree-model";
import { CalcTableActions } from "./calc-table-actions";
import { CrosstabActions } from "./crosstab-actions";
import { SelectionListActions } from "./selection-list-actions";
import { SelectionTreeActions } from "./selection-tree-actions";
```

Add these helper factories immediately after the existing `tableActionsFor` helper, before the `afterEach`:

```ts
   // The selection family. Same constructor order as TableActions and CalendarActions —
   // popService positional 6th, miniToolbarService 7th — verified against selection-list-actions.ts
   // and selection-tree-actions.ts rather than assumed.
   function selectionListActionsFor(width: number, height: number): SelectionListActions {
      const model: VSSelectionListModel = TestUtils.createMockVSSelectionListModel("SelectionList1");
      model.objectFormat.width = width;
      model.objectFormat.height = height;
      return new SelectionListActions(model, composerContext, false, null, null, popService,
         miniToolbarService);
   }

   function selectionTreeActionsFor(width: number, height: number): SelectionTreeActions {
      const model: VSSelectionTreeModel = TestUtils.createMockVSSelectionTreeModel("SelectionTree1");
      model.objectFormat.width = width;
      model.objectFormat.height = height;
      return new SelectionTreeActions(model, composerContext, false, null, null, popService,
         miniToolbarService);
   }

   // The remaining two anchored table-family types, for the kebabOnly negative assertions only.
   function crosstabActionsFor(width: number, height: number): CrosstabActions {
      const model: VSCrosstabModel = TestUtils.createMockVSCrosstabModel("Crosstab1");
      model.objectFormat.width = width;
      model.objectFormat.height = height;
      return new CrosstabActions(model, composerContext, false, null, null, popService,
         miniToolbarService);
   }

   function calcTableActionsFor(width: number, height: number): CalcTableActions {
      const model: VSCalcTableModel = TestUtils.createMockVSCalcTableModel("CalcTable1");
      model.objectFormat.width = width;
      model.objectFormat.height = height;
      return new CalcTableActions(model, composerContext, false, null, null, popService,
         miniToolbarService);
   }
```

Then append this `describe` block at the end of the outer `describe("AbstractVSActions", …)`:

```ts
   // kebabOnly is a permanent property of the selection family, not a rollout stage — which is why
   // it is a capability on the actions class rather than a second entry in the TEMPORARY type set.
   // Asserting it on all six anchored types is what stops a later slice widening it by accident.
   describe("kebabOnly capability", () => {
      it("is set on the selection family", () => {
         expect((selectionListActionsFor(400, 200) as any).kebabOnly).toBe(true);
         expect((selectionTreeActionsFor(400, 200) as any).kebabOnly).toBe(true);
      });

      it("is not set on the chart pilot or the table family", () => {
         expect((actionsFor(400, 200) as any).kebabOnly).toBe(false);
         expect((tableActionsFor(400, 200) as any).kebabOnly).toBe(false);
         expect((crosstabActionsFor(400, 200) as any).kebabOnly).toBe(false);
         expect((calcTableActionsFor(400, 200) as any).kebabOnly).toBe(false);
      });
   });
```

- [ ] **Step 2: Run the tests to verify they fail**

```bash
cd community/web
npx ng test --include='**/abstract-vs-actions.spec.ts'
```

Expected: `"is set on the selection family"` FAILS with `expected undefined to be true` — the getter does not exist yet. `"is not set on…"` also fails, on `undefined` rather than `false`.

- [ ] **Step 3: Add the capability and its early return**

In `abstract-vs-actions.ts`, add the getter immediately after the existing `private get resident()` getter (and its `TEMPORARY` comment — leave that comment exactly as it is):

```ts
   /**
    * Whether this assembly type's anchored strip is the kebab alone — no action buttons, at any
    * width. Permanent, unlike the resident/anchored predicate above: it survives the rollout.
    */
   protected get kebabOnly(): boolean {
      return false;
   }
```

Then, inside `allowedActionsNum()`, insert the early return between the `if(!this.resident)` guard and the `const height` line:

```ts
      if(!this.resident) {
         return num;
      }

      // Kebab-only types never get an action button, so neither the height bands nor the cap
      // arithmetic below apply. Placed after the resident guard on purpose: keyed on kebabOnly
      // alone this would also fire gate-off, stripping a floating toolbar users already have.
      if(this.kebabOnly) {
         return 0;
      }

      const height = this.model.objectFormat.height;
```

In `selection-list-actions.ts`, add the override immediately after the constructor:

```ts
   // Case 2: the kebab is the whole strip at any width. A selection list is one data column, often
   // ~150px wide, and its header already carries the label that identifies the filter.
   protected get kebabOnly(): boolean {
      return true;
   }
```

Add the identical override to `selection-tree-actions.ts`, immediately after its constructor.

- [ ] **Step 4: Run the tests to verify they pass**

```bash
cd community/web
npx ng test --include='**/abstract-vs-actions.spec.ts'
```

Expected: PASS. Every pre-existing test in the file — the fit ladder, the cap, kebab residency, the touch blocks and the table-family blocks — must also stay green; none of them constructs a selection.

- [ ] **Step 5: Verify the existing selection specs are untouched**

```bash
cd community/web
npx ng test --include='**/selection-list-actions.spec.ts'
npx ng test --include='**/selection-tree-actions.spec.ts'
```

Expected: PASS with no snapshot writes. These run gate-off, where the early return is unreachable. If a `.snap` file is rewritten, stop — that means the change is not inert.

- [ ] **Step 6: Commit**

```bash
cd community
git add web/projects/portal/src/app/vsobjects/action/abstract-vs-actions.ts \
        web/projects/portal/src/app/vsobjects/action/selection-list-actions.ts \
        web/projects/portal/src/app/vsobjects/action/selection-tree-actions.ts \
        web/projects/portal/src/app/vsobjects/action/abstract-vs-actions.spec.ts
git commit -m "feat(vsobjects): declare the selection family's kebab-only capability

The selection family's anchored strip is the kebab alone at any width: a
selection list is one data column, often ~150px wide, and its header already
carries the label that identifies the filter. Three icons plus a kebab crowds
out that label.

Expressed as a capability on the actions class rather than a second type set,
because it is permanent — the type set it would have sat beside is marked
TEMPORARY and instructs its own deletion once the rollout completes.

Inert until the family joins the anchored set: the early return sits behind the
existing resident guard, so gate-off keeps its floating toolbar.

Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>"
```

---

## Task 3: Anchor the selection family and move the pending-Apply icon

The activating commit. Two type strings join the anchored set, and the pending-Apply icon steps out from under the pill in the same change.

**Why one commit:** `.pending-alert` (`vs-selection.component.scss:205`) is `position: absolute; top: 2px; right: 5px; width: 20px` on `.vs-object`, rendered whenever `controller.unappliedSelections.length > 0` and **clickable** — it calls `controller.applySelections()`. Measured from the assembly's right edge, the anchored pill spans `0 → 32px` (1px border + 30px `--inet-control-height-md` button + 1px border) and the icon spans `5 → 25px`: total containment of a live Apply control. Today's floating toolbar overlaps the same corner but only on hover, so the icon is clear at rest; a resident kebab makes it permanent (§4).

The CSS cannot land first — gated on `.viz-modern` alone it would move the icon on a selection list that has no pill yet — and the anchoring cannot land first without shipping the collision. So they land together.

**Files:**
- Modify: `community/web/projects/portal/src/app/vsobjects/objects/mini-toolbar/mini-toolbar.service.ts` (`ANCHORED_ASSEMBLY_TYPES`)
- Modify: `community/web/projects/portal/src/app/vsobjects/objects/mini-toolbar/mini-toolbar.service.spec.ts`
- Modify: `community/web/projects/portal/src/app/vsobjects/objects/selection/vs-selection.component.scss` (`.pending-alert`, ~line 205)
- Test: `community/web/projects/portal/src/app/vsobjects/action/abstract-vs-actions.spec.ts`
- Test: `community/web/projects/portal/src/app/vsobjects/objects/mini-toolbar/mini-toolbar.service.spec.ts` (both the boundary edit and the co-render guard)

**Interfaces:**
- Consumes: `isAnchoredAssemblyType(objectType)` from `mini-toolbar.service.ts`; `selectionListActionsFor` / `selectionTreeActionsFor` from Task 2; `MiniToolbarService.isMiniToolbarVisible(vsObject)`.
- Produces: selection list and tree anchored and kebab-only under the gate. Task 4's dedup assertion depends on `getMoreActions()` taking the `flattenedMoreActions()` branch, which this task activates.

- [ ] **Step 1: Move the two types in the boundary spec**

In `mini-toolbar.service.spec.ts`, change the anchored test to include the family, and drop the two types from the excluded test. Replace:

```ts
   it("anchors the chart pilot and the table family", () => {
      expect(isAnchoredAssemblyType("VSChart")).toBe(true);
      expect(isAnchoredAssemblyType("VSTable")).toBe(true);
      expect(isAnchoredAssemblyType("VSCrosstab")).toBe(true);
      expect(isAnchoredAssemblyType("VSCalcTable")).toBe(true);
   });
```

with:

```ts
   it("anchors the chart pilot, the table family and the selection family", () => {
      expect(isAnchoredAssemblyType("VSChart")).toBe(true);
      expect(isAnchoredAssemblyType("VSTable")).toBe(true);
      expect(isAnchoredAssemblyType("VSCrosstab")).toBe(true);
      expect(isAnchoredAssemblyType("VSCalcTable")).toBe(true);
      expect(isAnchoredAssemblyType("VSSelectionList")).toBe(true);
      expect(isAnchoredAssemblyType("VSSelectionTree")).toBe(true);
   });
```

and replace:

```ts
   it("does not anchor the types whose rollout slices have not landed", () => {
      expect(isAnchoredAssemblyType("VSCalendar")).toBe(false);
      expect(isAnchoredAssemblyType("VSSelectionList")).toBe(false);
      expect(isAnchoredAssemblyType("VSSelectionTree")).toBe(false);
      expect(isAnchoredAssemblyType("VSSelectionContainer")).toBe(false);
   });
```

with:

```ts
   it("does not anchor the types whose rollout slices have not landed", () => {
      expect(isAnchoredAssemblyType("VSCalendar")).toBe(false);
      // The container is its own slice: four toolbar actions rather than nine, a vs-title lane in
      // normal flow rather than a ratio-split header, and it governs whether its children get a
      // strip at all.
      expect(isAnchoredAssemblyType("VSSelectionContainer")).toBe(false);
   });
```

- [ ] **Step 2: Write the failing behaviour tests**

Append this `describe` block at the end of the outer `describe("AbstractVSActions", …)` in `abstract-vs-actions.spec.ts`, after the `kebabOnly capability` block from Task 2:

```ts
   // Rendered-control counts, not allowedActionsNum(): that returns *slots*, one of which
   // ToolbarActionsHandler spends on the overflow control. Substituting slots for buttons is what
   // hid the slot-vs-button defect through the whole chart pilot.
   describe("the selection family is kebab-only", () => {
      const ids = (groups: any[]) =>
         groups.reduce((acc, g) => acc.concat(g.actions.map(a => a.id())), [] as string[]);

      it("allows no action-button slots at any width under the gate", () => {
         document.body.classList.add("viz-modern");
         expect(selectionListActionsFor(150, 200).allowedActionsNum()).toBe(0);
         expect(selectionListActionsFor(400, 200).allowedActionsNum()).toBe(0);
         expect(selectionListActionsFor(800, 200).allowedActionsNum()).toBe(0);
      });

      it("renders the kebab and nothing else at any width under the gate", () => {
         document.body.classList.add("viz-modern");
         expect(ids(selectionListActionsFor(150, 200).showingActions)).toEqual(["more actions"]);
         expect(ids(selectionListActionsFor(400, 200).showingActions)).toEqual(["more actions"]);
         expect(ids(selectionListActionsFor(800, 200).showingActions)).toEqual(["more actions"]);
      });

      it("treats the tree the same as the list", () => {
         document.body.classList.add("viz-modern");
         expect(selectionTreeActionsFor(400, 200).allowedActionsNum()).toBe(0);
         expect(ids(selectionTreeActionsFor(400, 200).showingActions)).toEqual(["more actions"]);
      });

      it("leaves the width-derived number alone when the gate is off", () => {
         expect(selectionListActionsFor(800, 200).allowedActionsNum()).toBeGreaterThan(1);
         expect(selectionTreeActionsFor(800, 200).allowedActionsNum()).toBeGreaterThan(1);
      });

      // Every selection list takes the flattened branch permanently, because no action button ever
      // renders. Leaving the menu nested behind a "More" row would cost three taps to reach what
      // the strip exists to put one tap away.
      it("flattens the kebab into one panel with no wrapper row", () => {
         document.body.classList.add("viz-modern");
         const more = ids(selectionListActionsFor(400, 200).getMoreActions());

         expect(more).not.toContain("menu actions");
         expect(more).toContain("selection-list search");
         expect(more).toContain("selection-list unselect");
      });

      it("still removes all chrome below the 32px control floor", () => {
         document.body.classList.add("viz-modern");
         expect(ids(selectionListActionsFor(400, 24).showingActions)).toEqual([]);
      });
   });
```

- [ ] **Step 3: Write the failing co-render guard**

`vs-selection.component.html:69` mounts a second, inline `<mini-menu>` in the header — `.selection-list__header-buttons`, hover-revealed at `right: 1px` — guarded by `model.dropdown || model.containerType == 'VSSelectionContainer'`. `isMiniToolbarVisible()` returns false for **both** of those, so the two kebabs are mutually exclusive by construction. That is the failure a reader of either file alone would not predict (§5.3), so assert it.

This goes in `mini-toolbar.service.spec.ts`, **not** in the selection component's spec. The assertion is a pure function of the model — it renders nothing — and `mini-toolbar.service.spec.ts` is where its subject already lives and where the anchored predicate is already asserted. The selection component's `*.tl.spec.ts` is the testing-library suite, reserved for real HTTP interception and DOM assertions, and its local idiom is `makeMockListModel` from its own test-helpers rather than `TestUtils`.

Append to `mini-toolbar.service.spec.ts`, after the existing `describe("isAnchoredAssemblyType", …)` block:

```ts
// The selection component has two kebabs: the anchored strip the container mounts, and an inline
// mini-menu in its own header (vs-selection.component.html, .selection-list__header-buttons). They
// are mutually exclusive — the inline one renders only for dropdown and container-child selections,
// which are exactly the cases isMiniToolbarVisible() excludes. Neither file states the other's half
// of that contract, so it is asserted here.
describe("isMiniToolbarVisible: the anchored strip and the inline header kebab never co-render", () => {
   const service = new MiniToolbarService(
      { runOutsideAngular: (fn: () => any) => fn() } as any);

   // isMiniToolbarVisible reads only these four fields, so a literal is clearer than a full mock.
   const selection = (overrides: any = {}) => Object.assign(
      { objectType: "VSSelectionList", enabled: true, dropdown: false, containerType: null },
      overrides) as any;

   it("suppresses the anchored strip for a dropdown selection, which mounts the inline kebab", () => {
      expect(service.isMiniToolbarVisible(selection({ dropdown: true }))).toBe(false);
   });

   it("suppresses the anchored strip for a container child, which mounts the inline kebab", () => {
      expect(service.isMiniToolbarVisible(
         selection({ containerType: "VSSelectionContainer" }))).toBe(false);
   });

   it("allows the anchored strip for a standalone selection, which mounts no inline kebab", () => {
      expect(service.isMiniToolbarVisible(selection())).toBe(true);
   });
});
```

Extend the file's existing import to bring in the class alongside the predicate:

```ts
import { isAnchoredAssemblyType, MiniToolbarService } from "./mini-toolbar.service";
```

- [ ] **Step 4: Run both specs to verify the new tests fail**

```bash
cd community/web
npx ng test --include='**/mini-toolbar.service.spec.ts'
npx ng test --include='**/abstract-vs-actions.spec.ts'
```

Expected:
- `mini-toolbar.service.spec.ts` — `"anchors the chart pilot, the table family and the selection family"` FAILS with `expected false to be true`. The three co-render guard tests in the same file **may already PASS**, since `isMiniToolbarVisible()` already excludes both cases. That is expected and fine: they guard behaviour the slice inherits deliberately, not behaviour it adds. Note it and move on.
- `abstract-vs-actions.spec.ts` — the kebab-only block FAILS; `allowedActionsNum()` returns the width-derived number because `resident` is still false.

- [ ] **Step 5: Add the two types to the anchored set**

In `mini-toolbar.service.ts`, extend `ANCHORED_ASSEMBLY_TYPES`. Leave the `TEMPORARY` doc comment above it exactly as it is:

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
   // kebab the whole strip at any width. The container is deliberately absent; it is its own slice.
   "vsselectionlist",
   "vsselectiontree"
]);
```

- [ ] **Step 6: Offset the pending-Apply icon clear of the pill**

In `vs-selection.component.scss`, extend the `.pending-alert` rule. Replace:

```scss
.pending-alert {
  position: absolute;
  top: 2px;
  right: 5px;
  width: 20px;

  &.left {
    left: 1px;
    background-color: rgb(245, 245, 245);
  }

  &.top {
    top: -1px;
  }
}
```

with:

```scss
.pending-alert {
  position: absolute;
  top: 2px;
  right: 5px;
  width: 20px;

  &.left {
    left: 1px;
    background-color: rgb(245, 245, 245);
  }

  &.top {
    top: -1px;
  }
}

// The resting kebab owns the rightmost 32px of this corner, so Apply steps aside rather than the
// kebab moving. One value for both title states — rightEdgeReserve() exempts this family.
:host-context(.viz-modern) .pending-alert:not(.left) {
  right: calc(var(--inet-control-height-md) + 6px);
}
```

- [ ] **Step 7: Run the specs to verify they pass**

```bash
cd community/web
npx ng test --include='**/mini-toolbar.service.spec.ts'
npx ng test --include='**/abstract-vs-actions.spec.ts'
```

Expected: PASS on both.

- [ ] **Step 8: Verify gate-off is still byte-identical**

```bash
cd community/web
npx ng test --include='**/selection-list-actions.spec.ts'
npx ng test --include='**/selection-tree-actions.spec.ts'
npx ng run portal:test-tl --include='**/vs-object-container.component.display.tl.spec.ts'
```

Expected: PASS, no `.snap` rewrites. These specs never set `viz-modern`, so index-based assertions like `toolbarActions[1].actions[3]` must be unaffected. **If a snapshot is rewritten, stop and investigate** — it means something leaked outside the gate.

- [ ] **Step 9: Commit**

```bash
cd community
git add web/projects/portal/src/app/vsobjects/objects/mini-toolbar/mini-toolbar.service.ts \
        web/projects/portal/src/app/vsobjects/objects/mini-toolbar/mini-toolbar.service.spec.ts \
        web/projects/portal/src/app/vsobjects/objects/selection/vs-selection.component.scss \
        web/projects/portal/src/app/vsobjects/action/abstract-vs-actions.spec.ts
git commit -m "feat(vsobjects): anchor the selection family, kebab-only

Selection list and tree join the anchored set. Unlike the chart and table
families they get no action strip: the kebab is the whole control at any width,
because a selection list is one data column whose header already carries the
label identifying the filter.

The pending-Apply icon moves in the same change rather than a later one. It sits
at right: 5px, width 20px, and the resting pill occupies the rightmost 32px of
the same corner - total containment of a live Apply control. Anchoring alone
would ship that overlap. The icon steps aside instead of the kebab, so the pill
stays flush and never shifts under the pointer.

Adds a guard for something inherited rather than written: the component's own
inline header kebab renders only for dropdown and container-child selections,
which are exactly the cases isMiniToolbarVisible() excludes, so the two kebabs
cannot co-render. Neither file stated the other's half of that contract.

Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>"
```

---

## Task 4: Add Max Mode to the right-click menu

`open-max-mode` and `close-max-mode` are toolbar-only on both types, so right-click reaches neither. Max mode is the action whose purpose is rescuing an assembly too small to read. This is the same standing bug slices 1 and 2 closed for charts and tables, fixed the same way — **ungated**, because it adds reachability and removes nothing, and the bug predates the spec (§2 decision 7).

Only Max Mode. Search, Sort, Reverse, Unselect and Apply stay off the menu: under kebab-only every toolbar action already overflows into the kebab, so nothing is stranded, and this edit ships to everyone regardless of the gate.

**The dedup constraint:** `flattenedMoreActions()` merges the overflowed toolbar list with the menu and dedupes by `action.id()`, on the stated basis that menu entries are copied verbatim from their toolbar twins. Task 3 put every selection list on that branch permanently. So these entries **must reuse the toolbar ids exactly** — `selection-list open-max-mode`, `selection-list close-max-mode`, and the tree's equivalents. A fresh id would make the kebab show Maximize twice, in adjacent groups.

Two constraints carried from slices 1 and 2: the pair is **one state-dependent entry, not two rows**, and menu rows render label-only (`actions-contextmenu.component.html` renders `action.label()` and an optional child arrow, nothing else), so **no toolbar glyph crosses over**. Appending last is what keeps the existing positional assertions from shifting.

**Note on visibility:** `openMaxModeVisible` requires `!this.composer`, so these entries are invisible in composer context — tests must use `ViewerContextProviderFactory(false)`. It also requires `!this.model.adhocFilter` on the **list** but not on the **tree**; copying the predicates verbatim preserves that existing asymmetry rather than tidying it.

**Files:**
- Modify: `community/web/projects/portal/src/app/vsobjects/action/selection-list-actions.ts` (`createMenuActions`, ~line 43–107)
- Modify: `community/web/projects/portal/src/app/vsobjects/action/selection-tree-actions.ts` (`createMenuActions`, ends ~line 86)
- Test: `community/web/projects/portal/src/app/vsobjects/action/selection-list-actions.spec.ts`
- Test: `community/web/projects/portal/src/app/vsobjects/action/selection-tree-actions.spec.ts`
- Test: `community/web/projects/portal/src/app/vsobjects/action/abstract-vs-actions.spec.ts`

**Interfaces:**
- Consumes: `this.openMaxModeVisible` / `this.closeMaxModeVisible` (private getters already on both classes); `ViewerContextProviderFactory` (already imported in both selection specs); `selectionListActionsFor` from Task 2.
- Produces: menu entries with ids `selection-list open-max-mode` / `selection-list close-max-mode` and `selection-tree open-max-mode` / `selection-tree close-max-mode`.

- [ ] **Step 1: Write the failing tests**

Append to `selection-list-actions.spec.ts`, inside `describe("SelectionListActions", …)`:

```ts
   // Max mode was toolbar-only on both selection types, so right-click reached the one action whose
   // purpose is rescuing an assembly too small to read. Ungated: it adds reachability and removes
   // nothing, and the gap predates the anchoring work.
   describe("max mode is reachable from the menu", () => {
      const ids = (groups: any[]) =>
         groups.reduce((acc, g) => acc.concat(g.actions.map(a => a.id())), [] as string[]);

      const viewerActions = (overrides: any = {}) => {
         const model = Object.assign(createModel(), overrides);
         return new SelectionListActions(model, ViewerContextProviderFactory(false));
      };

      it("adds the pair to the menu with the toolbar's own ids", () => {
         const menu = ids(viewerActions().menuActions);

         expect(menu).toContain("selection-list open-max-mode");
         expect(menu).toContain("selection-list close-max-mode");
      });

      it("appends the pair last, so existing positional assertions do not shift", () => {
         const groups = viewerActions().menuActions;
         const last = ids([groups[groups.length - 1]]);

         expect(last).toEqual(
            ["selection-list open-max-mode", "selection-list close-max-mode"]);
      });

      it("shows exactly one of the pair at a time", () => {
         const visible = (overrides: any) => viewerActions(overrides).menuActions
            .reduce((acc, g) => acc.concat(g.actions), [] as any[])
            .filter(a => a.id().endsWith("max-mode") && a.visible())
            .map(a => a.id());

         expect(visible({ maxMode: false })).toEqual(["selection-list open-max-mode"]);
         expect(visible({ maxMode: true })).toEqual(["selection-list close-max-mode"]);
      });

      // AssemblyAction.icon is a required member (icon: () => string), so a menu entry declares it
      // returning null rather than omitting it — the precedent the table family's menu group set.
      // actions-contextmenu.component.html renders label() and an optional child arrow, nothing
      // else, so no glyph reaches the row either way.
      it("renders label-only, carrying no toolbar glyph", () => {
         const entries = viewerActions().menuActions
            .reduce((acc, g) => acc.concat(g.actions), [] as any[])
            .filter(a => a.id().endsWith("max-mode"));

         expect(entries.length).toBe(2);
         entries.forEach(a => expect(a.icon()).toBeNull());
      });

      it("is present with the gate off, since the fix is ungated", () => {
         expect(document.body.classList.contains("viz-modern")).toBe(false);
         expect(ids(viewerActions().menuActions)).toContain("selection-list open-max-mode");
      });
   });
```

Append the tree's equivalent to `selection-tree-actions.spec.ts`, inside `describe("SelectionTreeActions", …)`. That file already declares `const createModel: () => VSSelectionTreeModel` and already imports `ViewerContextProviderFactory`, so it needs no new imports:

```ts
   // Max mode was toolbar-only on both selection types, so right-click reached the one action whose
   // purpose is rescuing an assembly too small to read. Ungated: it adds reachability and removes
   // nothing, and the gap predates the anchoring work.
   describe("max mode is reachable from the menu", () => {
      const ids = (groups: any[]) =>
         groups.reduce((acc, g) => acc.concat(g.actions.map(a => a.id())), [] as string[]);

      const viewerActions = (overrides: any = {}) => {
         const model = Object.assign(createModel(), overrides);
         return new SelectionTreeActions(model, ViewerContextProviderFactory(false));
      };

      it("adds the pair to the menu with the toolbar's own ids", () => {
         const menu = ids(viewerActions().menuActions);

         expect(menu).toContain("selection-tree open-max-mode");
         expect(menu).toContain("selection-tree close-max-mode");
      });

      it("appends the pair last, so existing positional assertions do not shift", () => {
         const groups = viewerActions().menuActions;
         const last = ids([groups[groups.length - 1]]);

         expect(last).toEqual(
            ["selection-tree open-max-mode", "selection-tree close-max-mode"]);
      });

      it("shows exactly one of the pair at a time", () => {
         const visible = (overrides: any) => viewerActions(overrides).menuActions
            .reduce((acc, g) => acc.concat(g.actions), [] as any[])
            .filter(a => a.id().endsWith("max-mode") && a.visible())
            .map(a => a.id());

         expect(visible({ maxMode: false })).toEqual(["selection-tree open-max-mode"]);
         expect(visible({ maxMode: true })).toEqual(["selection-tree close-max-mode"]);
      });

      it("renders label-only, carrying no toolbar glyph", () => {
         const entries = viewerActions().menuActions
            .reduce((acc, g) => acc.concat(g.actions), [] as any[])
            .filter(a => a.id().endsWith("max-mode"));

         expect(entries.length).toBe(2);
         entries.forEach(a => expect(a.icon()).toBeNull());
      });

      it("is present with the gate off, since the fix is ungated", () => {
         expect(document.body.classList.contains("viz-modern")).toBe(false);
         expect(ids(viewerActions().menuActions)).toContain("selection-tree open-max-mode");
      });
   });
```

Then append to `abstract-vs-actions.spec.ts`, inside the `describe("the selection family is kebab-only", …)` block from Task 3 — this is the assertion that needs Task 3 in place:

```ts
      // flattenedMoreActions() dedupes by id, and these entries reuse their toolbar twins' ids
      // exactly for that reason. A fresh id would put Maximize in the kebab twice, in adjacent
      // groups.
      it("shows one Maximize row in the flattened kebab, not two", () => {
         document.body.classList.add("viz-modern");
         const model = TestUtils.createMockVSSelectionListModel("SelectionList1");
         model.objectFormat.width = 400;
         model.objectFormat.height = 200;
         const actions = new SelectionListActions(model, ViewerContextProviderFactory(false),
            false, null, null, popService, miniToolbarService);
         const ids = actions.getMoreActions()
            .reduce((acc, g) => acc.concat(g.actions.map(a => a.id())), [] as string[]);

         expect(ids.filter(id => id === "selection-list open-max-mode").length).toBe(1);
      });
```

- [ ] **Step 2: Run the tests to verify they fail**

```bash
cd community/web
npx ng test --include='**/selection-list-actions.spec.ts'
npx ng test --include='**/selection-tree-actions.spec.ts'
npx ng test --include='**/abstract-vs-actions.spec.ts'
```

Expected: the new `max mode is reachable from the menu` blocks FAIL — the menu contains no `open-max-mode` id. The flattened-kebab test fails on `expected 0 to be 1`.

- [ ] **Step 3: Append the menu group on the list**

In `selection-list-actions.ts`, inside `createMenuActions`, insert this group immediately before the closing `return super.createMenuActions(groups);` — after the `if(!this.model.adhocFilter && !this.inSelectionContainer) { … }` block:

```ts
      // Ids and predicates match the toolbar twins; the kebab dedupes by id.
      groups.push(new AssemblyActionGroup([
         {
            id: () => "selection-list open-max-mode",
            label: () => "_#(js:Show Enlarged)",
            icon: () => null,
            enabled: () => true,
            visible: () => this.openMaxModeVisible
         },
         {
            id: () => "selection-list close-max-mode",
            label: () => "_#(js:Show Actual Size)",
            icon: () => null,
            enabled: () => true,
            visible: () => this.closeMaxModeVisible
         }
      ]));

      return super.createMenuActions(groups);
```

`icon: () => null`, not omitted — `AssemblyAction.icon` is a required member, and this is the shape the table family's menu group already uses. The menu template renders `label()` and an optional child arrow only, so nothing draws it.

- [ ] **Step 4: Append the menu group on the tree**

In `selection-tree-actions.ts`, inside `createMenuActions`, insert the same group immediately before `return super.createMenuActions(groups);` — after the existing `groups.push(this.createDefaultOrderMenuActions());` line — with `selection-tree` ids:

```ts
      // Ids and predicates match the toolbar twins; the kebab dedupes by id.
      groups.push(new AssemblyActionGroup([
         {
            id: () => "selection-tree open-max-mode",
            label: () => "_#(js:Show Enlarged)",
            icon: () => null,
            enabled: () => true,
            visible: () => this.openMaxModeVisible
         },
         {
            id: () => "selection-tree close-max-mode",
            label: () => "_#(js:Show Actual Size)",
            icon: () => null,
            enabled: () => true,
            visible: () => this.closeMaxModeVisible
         }
      ]));

      return super.createMenuActions(groups);
```

- [ ] **Step 5: Run the tests to verify they pass**

```bash
cd community/web
npx ng test --include='**/selection-list-actions.spec.ts'
npx ng test --include='**/selection-tree-actions.spec.ts'
npx ng test --include='**/abstract-vs-actions.spec.ts'
```

Expected: PASS. **The two `.snap` files will legitimately change here** — this is the one ungated edit, so the recorded menu structure genuinely gains a group. Review the snapshot diff before accepting it: it must show exactly one added group of two entries, appended last, with no icons and no reordering of anything above it. Update with `-u` only after reading the diff.

- [ ] **Step 6: Commit**

```bash
cd community
git add web/projects/portal/src/app/vsobjects/action/selection-list-actions.ts \
        web/projects/portal/src/app/vsobjects/action/selection-tree-actions.ts \
        web/projects/portal/src/app/vsobjects/action/selection-list-actions.spec.ts \
        web/projects/portal/src/app/vsobjects/action/selection-tree-actions.spec.ts \
        web/projects/portal/src/app/vsobjects/action/abstract-vs-actions.spec.ts \
        web/projects/portal/src/app/vsobjects/action/__snapshots__/selection-list-actions.spec.ts.snap \
        web/projects/portal/src/app/vsobjects/action/__snapshots__/selection-tree-actions.spec.ts.snap
git commit -m "fix(vsobjects): let right-click reach max mode on selections

open-max-mode and close-max-mode were toolbar-only on selection list and tree,
so right-click reached neither - and max mode is the action whose purpose is
rescuing an assembly too small to read. Same standing bug already closed for
charts and tables, fixed the same way and ungated: it adds reachability and
removes nothing.

Max mode only. Search, sort, reverse, unselect and apply stay off the menu -
under kebab-only every toolbar action already overflows into the kebab, so
nothing is stranded, and this edit reaches users who never opted into the gate.

Ids match the toolbar twins exactly, which flattenedMoreActions() relies on: it
dedupes by id, so a fresh id would list Maximize twice in the kebab.

Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>"
```

---

## Task 5: Full verification

The automated suites prove the wiring. They cannot prove that a 24px pill over a 20px title lane reads acceptably on a filter rail, which is the thing this slice is actually judged on.

**Files:** none modified. This task produces a recorded result.

**Interfaces:**
- Consumes: everything from Tasks 1–4.
- Produces: a manual-pass record appended to the design doc's §6.2 table, marking each row pass/fail with a note on anything unexpected.

- [ ] **Step 1: Run the full portal and em suites**

```bash
cd community/web
npm run test:portal
npm run test:em
```

Expected: PASS. Do **not** run the unscoped TL suites — it times out and orphans multi-GB node workers. The TL specs this slice touches were already run scoped in Tasks 1 and 3.

- [ ] **Step 2: Run lint**

```bash
cd community/web
npm run lint
```

Expected: no new findings in the six modified source files.

- [ ] **Step 3: Build and start the server**

```bash
cd /e/StyleBI/stylebi-enterprise
./mvnw clean install -DskipTests -Pcommunity,enterprise
cd docker/target/docker-test
docker compose up -d
```

Access at http://localhost:8080. Enable the modern visualization gate through EM Properties (`SreeEnv`) — **not** via yaml `additionalProperties`, which does not take.

- [ ] **Step 4: Walk the manual matrix**

Every row of §6.2 in the design doc. The four that a copy of slice 2's matrix would miss, and that carry this slice's specific risk:

| Check | What failure looks like |
|---|---|
| **`submitOnChange` off, select a value** | The pending-Apply icon and the kebab overlap, or either is unclickable, or the kebab shifts when the icon appears |
| Same, title hidden | The `.top` variant collides — the offset should hold, since `rightEdgeReserve()` is now `0` in both states |
| **Adhoc-filter selection list** | Tapping the kebab dismisses the popup. `.mini-toolbar` is whitelisted in the outside-click listener, so it should not — but this is the one thing that would make decision 8 unworkable |
| **Selection list inside a selection container** | Any anchored strip appears at all. It must not — Case 3, inherited |
| Filter rail of 5–6 lists | Six resident kebabs read as clutter rather than affordance |
| Long title that truncates | 32px of permanent pill costs the label its identity |
| List with a vertical scrollbar | The flush pill lands on the scrollbar (untested for tables too — if it fails here, it likely fails there) |
| Max mode | `.pending-alert.left` moves the icon; the `:not(.left)` scope must leave it alone |
| Touch | More than the kebab renders, or the kebab's list is short |
| Dark mode (`viz-dark`) | Any gated colour wrong on this new host |
| **Gate off** | Anything moved except Task 4's menu entry |

- [ ] **Step 5: Record the result and commit**

Append a dated manual-pass record to §6.2 of `community/docs/superpowers/specs/lookfeel/chart-card-slice3-selection-design.md`, marking each row and noting anything that came out differently from the design's prediction. Where the matrix contradicts a decision, say so plainly rather than adjusting the row — three of §7's open questions exist because the matrix is the only thing that can answer them.

```bash
cd community
git add docs/superpowers/specs/lookfeel/chart-card-slice3-selection-design.md
git commit -m "docs(chart-card): record the selection slice's manual pass

Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>"
```

---

## Spec coverage

| Design section | Task |
|---|---|
| §2.1 Kebab-only, no width floor (Case 2) | 2, 3 |
| §2.2 Title-hidden reserves nothing (Case 1) | inherited; verified in 1, 5 |
| §2.3 Container owns the strip (Case 3) | inherited; asserted in 3, verified in 5 |
| §2.4 The kebab is resident | inherited from slice 1's SCSS; verified in 5 |
| §2.5 Capability getter, not a type set | 2 |
| §2.6 Pending-Apply icon moves | 3 |
| §2.7 One ungated menu entry | 4 |
| §2.8 Adhoc filters included | no code — verified in 5 |
| §3 row 1 (anchored set) | 3 |
| §3 rows 2–3 (`kebabOnly`) | 2 |
| §3 row 4 (menu entry) | 4 |
| §3 row 5 (`.pending-alert`) | 3 |
| §3 row 6 (`rightEdgeReserve`) | 1 |
| §4 The Apply collision | 1, 3 |
| §5.1 Lane generalizes, `titleRatio` moot | no code — nothing to change |
| §5.2 Reserve becomes per-family | 1 |
| §5.3 Two kebabs never co-render | 3 |
| §5.4 Height bands stop binding | 2, asserted in 3 |
| §6.1 Automated | 1–4 |
| §6.2 Manual | 5 |

**Deliberately no task:** §5.1 (a closed question, no code), §2.8 (a decision to add no predicate), and every item in §7 — those are recorded to be inherited by later slices, not built here.
