# Chart Card Slice 2 — Table Family Anchoring Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Bring `VSTable`, `VSCrosstab` and `VSCalcTable` into the anchored mini-toolbar pattern the chart pilot shipped, and close the standing bug that makes max mode unreachable by right-click on every table.

**Architecture:** Slice 1 keyed anchoring on three separate `Tool.equalsIgnoreCase(objectType, "VSChart")` literals marked `TEMPORARY`. This plan replaces them with one shared predicate, then adds the three table types to it. The table family inherits the chart's treatment unchanged — anchored strip, resident kebab, cap of three, height bands — because the container's geometry already falls back to zero for models without padding fields, which is exactly the flush full-width lane a table wants. Only crosstab needs a toolbar reorder; table and calc table are already stable-first.

**Tech Stack:** Angular 21.2 / TypeScript 5.9, Vitest 4.1.7, `@testing-library/angular` + MSW for `*.tl.spec.ts`.

**Design doc:** [`docs/superpowers/specs/lookfeel/chart-card-slice2-tables-design.md`](../specs/lookfeel/chart-card-slice2-tables-design.md)
**Predecessor:** [`2026-08-05-chart-card-pr3-toolbar-chain.md`](./2026-08-05-chart-card-pr3-toolbar-chain.md) — the chart pilot whose patterns this copies
**Source doc:** `chart-card-design/Anchoring beyond charts - discussion.md` (enterprise repo root) — read Cases 1–4 before starting

## Global Constraints

- **Branch:** work continues on community `viz-updates`. No PR is opened; the branch accumulates the rollout until the feature is complete.
- **Everything that moves a pixel is gated** behind `.viz-modern`, read in TypeScript via `GuiTool.isVizModern()` (`app/common/util/gui-tool.ts:65`) and in CSS via `:host-context(.viz-modern)`. Exactly one change in this plan is ungated: Task 2, which adds menu entries and removes nothing.
- **Gate-off output must be byte-identical.** No table spec references `viz-modern`, so all 108 index-based assertions across `table-actions.spec.ts` (55), `crosstab-actions.spec.ts` (36) and `calc-table-actions.spec.ts` (17) must stay green **without edits**.
- **Snapshots, split by kind.** The three `.snap` files hold two sorts of entry, and only one is frozen:
  - **`toolbarActions` snapshots must never change** in this slice — 23 of them (9 table, 7 crosstab, 7 calc-table). They are the gate-off invariant. A change means a gate-off path moved: fix the code, do not update the snapshot.
  - **`menuActions` snapshots change exactly once**, in Task 2, and only there — 23 of them, each gaining one trailing `AssemblyActionGroup` of four actions. The specs call `expect(menuActions).toMatchSnapshot()` on the raw group array (`table-actions.spec.ts:153-156`), so appending a menu group necessarily rewrites them; appending it *last* controls where the change lands, not whether it happens. Update with `-u` and **read the resulting diff**: the only permitted change is one added group at the end of each. Any other movement is a real regression.
  - Tasks 1, 3, 4 and 5 must change **no snapshot of either kind**.
- **Never assert `allowedActionsNum()` as a proxy for what renders.** It returns toolbar *slots*, and `ToolbarActionsHandler.getShowingActions()` spends one on the overflow control. Assert rendered button counts. This exact substitution hid a defect through the entire chart pilot.
- **Menu rows render label-only.** `actions-contextmenu.component.html` renders `action.label()` and an optional child arrow, nothing else. Menu entries carry `icon: () => null`.
- **Copy predicates verbatim** when moving an action between `createToolbarActions` and `createMenuActions`. Do not paraphrase or simplify a conjunct.
- **Java 21 / Maven is not involved.** This is a frontend-only, community-only change. No enterprise file is touched.
- Test commands run from `community/web`. A bare `npx vitest run <path>` does NOT work in this checkout (pre-existing config gap): use `npx ng test portal --include=<path>` for `*.spec.ts` and `npx ng run portal:test-tl --include=<path>` for `*.tl.spec.ts`.
- **Never run the full TL suite** — not `npm run test:portal:tl`, not an unfiltered `portal:test-tl`. It exceeds any available foreground window, so it gets backgrounded and then killed on timeout, and the kill orphans vitest workers at ~1–3 GB each that drive the machine to 100% CPU. Run only the specific `*.tl.spec.ts` files a change touches, always with `--include`. If a full-suite result would genuinely be needed, state what is unverified instead of launching it. Never instruct a subagent to "run the full suite in the foreground" — that is not achievable and pushes it to background the run.
- `--update-snapshot` is also unsupported by this builder. To update snapshots, set `update: true` in `web/vitest-base.config.ts`, run, then revert that file. Running any portal suite additionally rewrites ~23 unrelated `.snap` files with zero content change (CRLF churn) — clear it with `git checkout --` on the snapshots directory before staging.

---

## Relationship to what slice 1 already shipped

Two facts that change what this plan must do, both verified on `viz-updates` @ `67c486d67`:

1. **The Hide MiniToolbar move is already live on all eight assembly types.** `abstract-vs-actions.ts:363` branches on `GuiTool.isVizModern()` with **no type test**, so under the gate every type has already lost the close-X from toolbar slot 0 and gained it as a menu row. The comment there states the other seven are "unaffected until the rollout" — that is false, and Task 3 corrects it. Nothing in this plan re-does the move itself.
2. **The container geometry needs no edit.** `getToolbarTop`, `getToolbarLeft` and `getAnchoredToolbarWidth` read `(<VSChartModel> object).paddingTop || 0` and siblings. `base-table-model.ts` declares no padding fields, so a table resolves to top-left flush inside the content box, spanning full width — the lane the type already has, which is what slice 1's rule asks for. There is already a passing test for this shape (`vs-object-container.component.display.tl.spec.ts`, "treats missing paddings as zero"); Task 4 adds the table-typed case.

## The one deviation from the design doc

The design says "export `ANCHORED_ASSEMBLY_TYPES`". The code being replaced uses `Tool.equalsIgnoreCase`, so a bare `Set.has()` would silently narrow matching from case-insensitive to case-sensitive. Task 1 therefore exports a **predicate** that lowercases its input, and keeps the set module-private. Same single edit point, same removal condition, no behaviour change.

## File Structure

| File | Responsibility | Task |
|---|---|---|
| `web/projects/portal/src/app/vsobjects/objects/mini-toolbar/mini-toolbar.service.ts` | Add `isAnchoredAssemblyType()` beside `hasMiniToolbar()` — the single rollout boundary | 1, 4 |
| `web/projects/portal/src/app/vsobjects/objects/mini-toolbar/mini-toolbar.service.spec.ts` | **New.** Boundary tests: which types anchor, which do not | 1, 4 |
| `web/projects/portal/src/app/vsobjects/action/abstract-vs-actions.ts` | `resident` consumes the predicate; correct the false `:363` comment | 1, 3 |
| `web/projects/portal/src/app/vsobjects/action/abstract-vs-actions.spec.ts` | `resident`-driven cap and ladder behaviour for a table subclass | 4 |
| `web/projects/portal/src/app/vsobjects/objects/vs-object-container.component.ts` | `isToolbarAnchored` consumes the predicate | 1 |
| `web/projects/portal/src/app/vsobjects/objects/vs-object-container.component.display.tl.spec.ts` | Anchored geometry for a padding-less table model | 4 |
| `web/projects/portal/src/app/vsobjects/action/table-actions.ts` | Menu reachability group only — toolbar order already correct | 2 |
| `web/projects/portal/src/app/vsobjects/action/calc-table-actions.ts` | Menu reachability group only — toolbar order already correct | 2 |
| `web/projects/portal/src/app/vsobjects/action/crosstab-actions.ts` | Menu reachability group **and** the gated toolbar reorder | 2, 3 |
| `web/projects/portal/src/app/vsobjects/action/{table,crosstab,calc-table}-actions.spec.ts` | Menu reachability and gated order assertions | 2, 3 |

**Task order is deliberate.** Task 3's reorder lands *before* Task 4 adds the types, so no commit in between leaves a capped crosstab with `drilldown`/`drillup` occupying the first slots — the exact reshuffle-under-the-pointer bug the reorder exists to prevent.

---

## Task 1: One shared predicate for the rollout boundary

Pure refactor. Three literals collapse to one predicate that still returns true for `VSChart` and nothing else, so **every existing test must pass unchanged**. A reviewer can reject the naming or the file placement here without touching the behaviour change in Task 4.

**Files:**
- Create: `web/projects/portal/src/app/vsobjects/objects/mini-toolbar/mini-toolbar.service.spec.ts`
- Modify: `web/projects/portal/src/app/vsobjects/objects/mini-toolbar/mini-toolbar.service.ts` (top of file, after the imports at lines 18–26)
- Modify: `web/projects/portal/src/app/vsobjects/action/abstract-vs-actions.ts:139-143` and its `Tool` import at line 29
- Modify: `web/projects/portal/src/app/vsobjects/objects/vs-object-container.component.ts:462-468`

**Interfaces:**
- Consumes: nothing from earlier tasks.
- Produces: `isAnchoredAssemblyType(objectType: string): boolean`, exported from `mini-toolbar.service.ts`. Tasks 3 and 4 both rely on this exact name and signature.

- [ ] **Step 1: Write the failing test**

Create `web/projects/portal/src/app/vsobjects/objects/mini-toolbar/mini-toolbar.service.spec.ts`:

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
import { isAnchoredAssemblyType } from "./mini-toolbar.service";

// The rollout boundary, asserted explicitly rather than left implied. Each family slice moves types
// from the second test to the first; the last one deletes the predicate entirely and the gate
// becomes the only condition.
describe("isAnchoredAssemblyType", () => {
   it("anchors the chart, which shipped as the pilot", () => {
      expect(isAnchoredAssemblyType("VSChart")).toBe(true);
   });

   it("matches case-insensitively, as the Tool.equalsIgnoreCase calls it replaces did", () => {
      expect(isAnchoredAssemblyType("vschart")).toBe(true);
      expect(isAnchoredAssemblyType("VSCHART")).toBe(true);
   });

   it("does not anchor the types whose rollout slices have not landed", () => {
      expect(isAnchoredAssemblyType("VSTable")).toBe(false);
      expect(isAnchoredAssemblyType("VSCrosstab")).toBe(false);
      expect(isAnchoredAssemblyType("VSCalcTable")).toBe(false);
      expect(isAnchoredAssemblyType("VSCalendar")).toBe(false);
      expect(isAnchoredAssemblyType("VSSelectionList")).toBe(false);
      expect(isAnchoredAssemblyType("VSSelectionTree")).toBe(false);
      expect(isAnchoredAssemblyType("VSSelectionContainer")).toBe(false);
   });

   it("never anchors the range slider, which is excluded from the rollout permanently", () => {
      // Case 4: alone among the eight it declares no titleVisible, so it has no lane to anchor into.
      expect(isAnchoredAssemblyType("VSRangeSlider")).toBe(false);
   });

   it("does not throw on a missing objectType", () => {
      expect(isAnchoredAssemblyType(null)).toBe(false);
      expect(isAnchoredAssemblyType(undefined)).toBe(false);
      expect(isAnchoredAssemblyType("")).toBe(false);
   });
});
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `npx ng test portal --include=projects/portal/src/app/vsobjects/objects/mini-toolbar/mini-toolbar.service.spec.ts`
Expected: FAIL — `isAnchoredAssemblyType` is not exported from `./mini-toolbar.service`.

- [ ] **Step 3: Add the predicate**

In `mini-toolbar.service.ts`, insert between the import block (ends line 26) and `@Injectable()` (line 28):

```ts
/**
 * The assembly types whose mini-toolbar is anchored in the title lane, with the cap of three, the
 * height bands and the resident kebab. A subset of hasMiniToolbar() below, which enumerates the
 * types that get a strip at all.
 *
 * TEMPORARY. This is the rollout boundary, not a permanent distinction. Slice 1 shipped VSChart as
 * the pilot; each family slice adds its types. The last slice deletes this predicate together with
 * AbstractVSActions.resident and VSObjectContainerComponent.isToolbarAnchored, leaving the
 * .viz-modern gate as the only condition. VSRangeSlider is excluded permanently, not pending — it
 * declares no titleVisible, so it has no lane to anchor into. See
 * chart-card-design/Anchoring beyond charts - discussion.md, Case 4.
 */
const ANCHORED_ASSEMBLY_TYPES: ReadonlySet<string> = new Set<string>([
   "vschart"
]);

/**
 * Whether an assembly type is in the anchored set. Lowercases its argument because the three call
 * sites this replaces used Tool.equalsIgnoreCase; a bare Set.has() would narrow the match.
 */
export function isAnchoredAssemblyType(objectType: string): boolean {
   return !!objectType && ANCHORED_ASSEMBLY_TYPES.has(objectType.toLowerCase());
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `npx ng test portal --include=projects/portal/src/app/vsobjects/objects/mini-toolbar/mini-toolbar.service.spec.ts`
Expected: PASS, 5 tests.

- [ ] **Step 5: Point `resident` at the predicate**

In `abstract-vs-actions.ts`, replace lines 139–143:

```ts
   // TEMPORARY type test, like the container's isToolbarAnchored: the cap and the height bands
   // are part of the chart pilot and are deleted during the eight-assembly rollout.
   private get resident(): boolean {
      return GuiTool.isVizModern() && Tool.equalsIgnoreCase(this.model.objectType, "VSChart");
   }
```

with:

```ts
   // TEMPORARY, like the container's isToolbarAnchored: both read the one rollout boundary in
   // mini-toolbar.service.ts and are deleted together with it when the last family slice lands.
   private get resident(): boolean {
      return GuiTool.isVizModern() && isAnchoredAssemblyType(this.model.objectType);
   }
```

Update the import on line 27 to bring in the predicate alongside the service:

```ts
import { isAnchoredAssemblyType, MiniToolbarService } from "../objects/mini-toolbar/mini-toolbar.service";
```

Then **delete line 29**, `import { Tool } from "../../../../../shared/util/tool";`. Line 142 was its only use in this file — verified — so leaving it trips the unused-import lint rule.

- [ ] **Step 6: Point `isToolbarAnchored` at the predicate**

In `vs-object-container.component.ts`, replace lines 462–468:

```ts
   // Chart is the anchoring pilot. TEMPORARY: this type test is deleted during the eight-assembly
   // rollout, when the other seven get a reviewed strip rather than an incidental one. See
   // chart-card-design/Anchoring beyond charts - discussion.md.
   public isToolbarAnchored(object: VSObjectModel): boolean {
      return GuiTool.isVizModern() &&
         Tool.equalsIgnoreCase(object.objectType, "VSChart");
   }
```

with:

```ts
   // TEMPORARY, like AbstractVSActions.resident: both read the one rollout boundary in
   // mini-toolbar.service.ts and are deleted together with it when the last family slice lands. See
   // chart-card-design/Anchoring beyond charts - discussion.md.
   public isToolbarAnchored(object: VSObjectModel): boolean {
      return GuiTool.isVizModern() && isAnchoredAssemblyType(object.objectType);
   }
```

Add the predicate to the existing `MiniToolbarService` import on line 57:

```ts
import { isAnchoredAssemblyType, MiniToolbarService } from "./mini-toolbar/mini-toolbar.service";
```

**Keep the `Tool` import here** — unlike `abstract-vs-actions.ts`, this file still uses `Tool` at line 481 (`VSRangeSlider` check) and lines 695–731 (`Tool.getMarginSize`).

- [ ] **Step 7: Run the affected suites to verify nothing moved**

Run:
```
npx ng test portal --include=projects/portal/src/app/vsobjects/action/abstract-vs-actions.spec.ts \
  projects/portal/src/app/vsobjects/action/chart-actions.spec.ts \
  projects/portal/src/app/vsobjects/objects/mini-toolbar/mini-toolbar.service.spec.ts
```
Expected: PASS with **no test edits and no snapshot updates**. This is a pure refactor; any failure means the predicate does not reproduce `Tool.equalsIgnoreCase`.

Then the container's testing-library suite:
```
npx ng run portal:test-tl --include=projects/portal/src/app/vsobjects/objects/vs-object-container.component.display.tl.spec.ts
```
Expected: PASS, including Group 11's anchored-geometry tests.

- [ ] **Step 8: Commit**

```bash
git add web/projects/portal/src/app/vsobjects/objects/mini-toolbar/mini-toolbar.service.ts \
        web/projects/portal/src/app/vsobjects/objects/mini-toolbar/mini-toolbar.service.spec.ts \
        web/projects/portal/src/app/vsobjects/action/abstract-vs-actions.ts \
        web/projects/portal/src/app/vsobjects/objects/vs-object-container.component.ts
git commit -m "refactor(chart-card): give the anchoring rollout one boundary

The chart pilot keyed anchoring on three separate equalsIgnoreCase tests
against VSChart, each marked TEMPORARY and each able to drift from the
others. They become one predicate beside hasMiniToolbar(), which is the
enumeration it is a subset of, so a family slice edits one line and the
final slice deletes one thing.

It is a predicate rather than an exported Set because the calls it
replaces were case-insensitive and a bare Set.has() would have narrowed
the match without saying so.

No behaviour change: the set holds VSChart alone, and every existing test
and snapshot passes untouched."
```

---

## Task 2: Make max mode reachable by right-click (ungated)

`open-max-mode`, `close-max-mode`, `show-details` and `export` are toolbar-only on all three table types, so the right-click menu reaches none of them. Max mode exists to rescue an assembly too small to read, and today it is unreachable from the menu on every table in the product. Slice 1 fixed the identical gap for charts ungated; this mirrors it exactly.

**Ungated on purpose.** It adds reachability and removes nothing. Appending the group **last** is what keeps the 108 existing positional assertions from shifting.

**Files:**
- Modify: `web/projects/portal/src/app/vsobjects/action/table-actions.ts:227` (immediately before `return super.createMenuActions(groups);`)
- Modify: `web/projects/portal/src/app/vsobjects/action/crosstab-actions.ts:274` (same position)
- Modify: `web/projects/portal/src/app/vsobjects/action/calc-table-actions.ts:274` (same position)
- Test: `web/projects/portal/src/app/vsobjects/action/table-actions.spec.ts`
- Test: `web/projects/portal/src/app/vsobjects/action/crosstab-actions.spec.ts`
- Test: `web/projects/portal/src/app/vsobjects/action/calc-table-actions.spec.ts`

**Interfaces:**
- Consumes: nothing from Task 1.
- Produces: menu action ids `"<type> open-max-mode"`, `"<type> close-max-mode"`, `"<type> show-details"`, `"<type> export"` where `<type>` is `table`, `crosstab` or `calc-table`. Task 5's manual matrix relies on these being present.

- [ ] **Step 1: Write the failing tests**

Append to `table-actions.spec.ts`, inside the top-level `describe("TableActions", ...)` block, immediately before its closing `});`:

```ts
   // The same standing bug slice 1 closed for charts: these four were toolbar-only, so right-click
   // reached none of them — max mode included, whose whole purpose is rescuing an assembly too
   // small to read. Ungated: it adds reachability and removes nothing.
   describe("menu reachability for the toolbar-only actions", () => {
      const menuIds = (actions: TableActions): string[] =>
         actions.menuActions.reduce(
            (acc, g) => acc.concat(g.actions.map(a => a.id())), [] as string[]);

      it("exposes max mode, show details and export in the menu", () => {
         const actions = new TableActions(createModel(), ViewerContextProviderFactory(false));
         const ids = menuIds(actions);

         expect(ids).toContain("table open-max-mode");
         expect(ids).toContain("table close-max-mode");
         expect(ids).toContain("table show-details");
         expect(ids).toContain("table export");
      });

      it("appends them as the last group, so existing positional assertions do not shift", () => {
         const actions = new TableActions(createModel(), ViewerContextProviderFactory(false));
         const groups = actions.menuActions;

         expect(groups[groups.length - 1].actions.map(a => a.id())).toEqual([
            "table open-max-mode",
            "table close-max-mode",
            "table show-details",
            "table export"
         ]);
      });

      it("carries no glyph across, because menu rows render labels only", () => {
         const actions = new TableActions(createModel(), ViewerContextProviderFactory(false));
         const group = actions.menuActions[actions.menuActions.length - 1];

         group.actions.forEach(a => expect(a.icon()).toBeNull());
      });
   });
```

Append the crosstab counterpart to `crosstab-actions.spec.ts`, inside its top-level `describe`:

```ts
   describe("menu reachability for the toolbar-only actions", () => {
      const menuIds = (actions: CrosstabActions): string[] =>
         actions.menuActions.reduce(
            (acc, g) => acc.concat(g.actions.map(a => a.id())), [] as string[]);

      it("exposes max mode, show details and export in the menu", () => {
         const actions = new CrosstabActions(TestUtils.createMockVSCrosstabModel("Crosstab1"),
            ViewerContextProviderFactory(false));
         const ids = menuIds(actions);

         expect(ids).toContain("crosstab open-max-mode");
         expect(ids).toContain("crosstab close-max-mode");
         expect(ids).toContain("crosstab show-details");
         expect(ids).toContain("crosstab export");
      });

      it("appends them as the last group, so existing positional assertions do not shift", () => {
         const actions = new CrosstabActions(TestUtils.createMockVSCrosstabModel("Crosstab1"),
            ViewerContextProviderFactory(false));
         const groups = actions.menuActions;

         expect(groups[groups.length - 1].actions.map(a => a.id())).toEqual([
            "crosstab open-max-mode",
            "crosstab close-max-mode",
            "crosstab show-details",
            "crosstab export"
         ]);
      });

      it("carries no glyph across, because menu rows render labels only", () => {
         const actions = new CrosstabActions(TestUtils.createMockVSCrosstabModel("Crosstab1"),
            ViewerContextProviderFactory(false));
         const group = actions.menuActions[actions.menuActions.length - 1];

         group.actions.forEach(a => expect(a.icon()).toBeNull());
      });
   });
```

And the calc-table counterpart to `calc-table-actions.spec.ts`, inside its top-level `describe`:

```ts
   describe("menu reachability for the toolbar-only actions", () => {
      const menuIds = (actions: CalcTableActions): string[] =>
         actions.menuActions.reduce(
            (acc, g) => acc.concat(g.actions.map(a => a.id())), [] as string[]);

      it("exposes max mode, show details and export in the menu", () => {
         const actions = new CalcTableActions(TestUtils.createMockVSCalcTableModel("CalcTable1"),
            ViewerContextProviderFactory(false));
         const ids = menuIds(actions);

         expect(ids).toContain("calc-table open-max-mode");
         expect(ids).toContain("calc-table close-max-mode");
         expect(ids).toContain("calc-table show-details");
         expect(ids).toContain("calc-table export");
      });

      it("appends them as the last group, so existing positional assertions do not shift", () => {
         const actions = new CalcTableActions(TestUtils.createMockVSCalcTableModel("CalcTable1"),
            ViewerContextProviderFactory(false));
         const groups = actions.menuActions;

         expect(groups[groups.length - 1].actions.map(a => a.id())).toEqual([
            "calc-table open-max-mode",
            "calc-table close-max-mode",
            "calc-table show-details",
            "calc-table export"
         ]);
      });

      it("carries no glyph across, because menu rows render labels only", () => {
         const actions = new CalcTableActions(TestUtils.createMockVSCalcTableModel("CalcTable1"),
            ViewerContextProviderFactory(false));
         const group = actions.menuActions[actions.menuActions.length - 1];

         group.actions.forEach(a => expect(a.icon()).toBeNull());
      });
   });
```

If `TestUtils` or `ViewerContextProviderFactory` is not already imported in a given spec, add it — `table-actions.spec.ts` imports both already; check the other two and match their existing import style.

- [ ] **Step 2: Run to verify they fail**

Run:
```
npx ng test portal --include=projects/portal/src/app/vsobjects/action/table-actions.spec.ts \
  projects/portal/src/app/vsobjects/action/crosstab-actions.spec.ts \
  projects/portal/src/app/vsobjects/action/calc-table-actions.spec.ts
```
Expected: FAIL — 9 new tests fail; the "exposes" tests fail on `toContain`, the "appends" tests on the last group being the `MenuAction HelperText` group.

- [ ] **Step 3: Append the menu group in `table-actions.ts`**

Insert immediately before `return super.createMenuActions(groups);` at line 227:

```ts
      // open-max-mode, close-max-mode, show-details and export were toolbar-only, so right-click
      // could not reach any of them — max mode in particular, whose whole purpose is rescuing an
      // assembly too small to read. Predicates are copied verbatim from createToolbarActions; the
      // menu renders labels only, so no icon. Appended last so the positional assertions in
      // table-actions.spec.ts do not shift.
      groups.push(new AssemblyActionGroup([
         {
            id: () => "table open-max-mode",
            label: () => "_#(js:Show Enlarged)",
            icon: () => null,
            enabled: () => true,
            visible: () => this.openMaxModeVisible
         },
         {
            id: () => "table close-max-mode",
            label: () => "_#(js:Show Actual Size)",
            icon: () => null,
            enabled: () => true,
            visible: () => this.closeMaxModeVisible
         },
         {
            id: () => "table show-details",
            label: () => "_#(js:Show Details)",
            icon: () => null,
            enabled: () => true,
            visible: () => this.showDetailsVisible
         },
         {
            id: () => "table export",
            label: () => "_#(js:Export)",
            icon: () => null,
            enabled: () => true,
            visible: () => !this.vsWizardPreview && this.isActionVisibleInViewer("Export")
         }
      ]));
```

- [ ] **Step 4: Append the menu group in `crosstab-actions.ts`**

Insert immediately before `return super.createMenuActions(groups);` at line 274:

```ts
      // open-max-mode, close-max-mode, show-details and export were toolbar-only, so right-click
      // could not reach any of them — max mode in particular, whose whole purpose is rescuing an
      // assembly too small to read. Predicates are copied verbatim from createToolbarActions; the
      // menu renders labels only, so no icon. Appended last so the positional assertions in
      // crosstab-actions.spec.ts do not shift.
      groups.push(new AssemblyActionGroup([
         {
            id: () => "crosstab open-max-mode",
            label: () => "_#(js:Show Enlarged)",
            icon: () => null,
            enabled: () => true,
            visible: () => this.openMaxModeVisible
         },
         {
            id: () => "crosstab close-max-mode",
            label: () => "_#(js:Show Actual Size)",
            icon: () => null,
            enabled: () => true,
            visible: () => this.closeMaxModeVisible
         },
         {
            id: () => "crosstab show-details",
            label: () => "_#(js:Show Details)",
            icon: () => null,
            enabled: () => true,
            visible: () => this.showDetailsVisible
         },
         {
            id: () => "crosstab export",
            label: () => "_#(js:Export)",
            icon: () => null,
            enabled: () => true,
            visible: () => !this.vsWizardPreview && this.isActionVisible("Export")
         }
      ]));
```

Note the crosstab export predicate is `this.isActionVisible("Export")`, **not** `isActionVisibleInViewer` — that is what its toolbar entry uses. Copy it as it is; do not harmonise it with the table's.

- [ ] **Step 5: Append the menu group in `calc-table-actions.ts`**

Insert immediately before `return super.createMenuActions(groups);` at line 274:

```ts
      // open-max-mode, close-max-mode, show-details and export were toolbar-only, so right-click
      // could not reach any of them — max mode in particular, whose whole purpose is rescuing an
      // assembly too small to read. Predicates are copied verbatim from createToolbarActions; the
      // menu renders labels only, so no icon. Appended last so the positional assertions in
      // calc-table-actions.spec.ts do not shift.
      groups.push(new AssemblyActionGroup([
         {
            id: () => "calc-table open-max-mode",
            label: () => "_#(js:Show Enlarged)",
            icon: () => null,
            enabled: () => true,
            visible: () => this.openMaxModeVisible
         },
         {
            id: () => "calc-table close-max-mode",
            label: () => "_#(js:Show Actual Size)",
            icon: () => null,
            enabled: () => true,
            visible: () => this.closeMaxModeVisible
         },
         {
            id: () => "calc-table show-details",
            label: () => "_#(js:Show Details)",
            icon: () => null,
            enabled: () => true,
            visible: () => this.showDetailsVisible
         },
         {
            id: () => "calc-table export",
            label: () => "_#(js:Export)",
            icon: () => null,
            enabled: () => true,
            visible: () => this.isActionVisibleInViewer("Export")
         }
      ]));
```

The calc-table export predicate has no `!this.vsWizardPreview` conjunct — again, that is what its toolbar entry uses. Copy verbatim.

- [ ] **Step 6: Run to verify the new tests pass and nothing else moved**

Run:
```
npx ng test portal --include=projects/portal/src/app/vsobjects/action/table-actions.spec.ts \
  projects/portal/src/app/vsobjects/action/crosstab-actions.spec.ts \
  projects/portal/src/app/vsobjects/action/calc-table-actions.spec.ts
```
Expected: the 9 new tests green, all pre-existing **assertions** green with no edits — and 23 `menuActions` snapshot failures, which are expected. This is the one task in the slice that legitimately changes snapshots.

Update them and inspect the result:

```
# NOTE: --update-snapshot is unsupported here. Temporarily set `update: true` in
# web/vitest-base.config.ts, run the command below, then revert that file.
npx ng test portal --include=projects/portal/src/app/vsobjects/action/table-actions.spec.ts \
  projects/portal/src/app/vsobjects/action/crosstab-actions.spec.ts \
  projects/portal/src/app/vsobjects/action/calc-table-actions.spec.ts
git diff --stat web/projects/portal/src/app/vsobjects/action/__snapshots__/
git diff web/projects/portal/src/app/vsobjects/action/__snapshots__/
```

**Read that diff before continuing.** The only permitted change is one added `AssemblyActionGroup` of four actions at the **end** of each `menuActions` snapshot. If any `toolbarActions` snapshot changed, or a `menuActions` group moved or changed anywhere but the end, revert the snapshot update and fix the code — the group was not appended last, or the edit reached a toolbar array.

- [ ] **Step 7: Run the whole action suite**

Run: `npx ng test portal --include=projects/portal/src/app/vsobjects/action`
Expected: PASS. The other assembly types are untouched by this task; a failure there means the edit landed in a shared base class by mistake.

- [ ] **Step 8: Commit**

```bash
git add web/projects/portal/src/app/vsobjects/action/table-actions.ts \
        web/projects/portal/src/app/vsobjects/action/crosstab-actions.ts \
        web/projects/portal/src/app/vsobjects/action/calc-table-actions.ts \
        web/projects/portal/src/app/vsobjects/action/table-actions.spec.ts \
        web/projects/portal/src/app/vsobjects/action/crosstab-actions.spec.ts \
        web/projects/portal/src/app/vsobjects/action/calc-table-actions.spec.ts \
        web/projects/portal/src/app/vsobjects/action/__snapshots__/table-actions.spec.ts.snap \
        web/projects/portal/src/app/vsobjects/action/__snapshots__/crosstab-actions.spec.ts.snap \
        web/projects/portal/src/app/vsobjects/action/__snapshots__/calc-table-actions.spec.ts.snap
git commit -m "fix(vsobjects): let right-click reach max mode on tables

Show Enlarged, Show Actual Size, Show Details and Export are toolbar-only
on table, crosstab and calc table, so the right-click menu reaches none of
them. Max mode is the one that matters: its whole purpose is rescuing an
assembly too small to read, and the assemblies too small to read are the
ones whose strip has the least room to offer it.

Ungated, for the reason slice 1 ungated the same fix for charts — it adds
reachability and removes nothing, and the bug predates the chart card
work. Predicates are copied verbatim from each type's createToolbarActions
rather than harmonised: crosstab guards Export with isActionVisible and
table with isActionVisibleInViewer, and calc table carries no wizard-
preview conjunct at all. Menu rows render labels only, so no glyph crosses
over. The group is appended last, which is what keeps the 108 positional
assertions in the three spec files from shifting."
```

---

## Task 3: Crosstab leads with its stable actions (gated)

Crosstab is the only type in this family whose toolbar leads with contextual actions. Today's order is `drilldown, drillup, open-max-mode, close-max-mode, show-details, export, multi-select, edit` — so once Task 4 caps the strip at three, drill would seize the first two slots and the icons would reshuffle under the pointer on every cell selection. Table and calc table already lead with the max-mode pair, and need no edit.

**This lands before Task 4 deliberately**, so no commit in between produces a capped crosstab with contextual actions leading.

**Files:**
- Modify: `web/projects/portal/src/app/vsobjects/action/crosstab-actions.ts` — the whole `createToolbarActions` body
- Modify: `web/projects/portal/src/app/vsobjects/action/abstract-vs-actions.ts:363-368` — correct a comment that is false
- Test: `web/projects/portal/src/app/vsobjects/action/crosstab-actions.spec.ts`

**Interfaces:**
- Consumes: `GuiTool.isVizModern()`.
- Produces: under the gate, crosstab's single toolbar group emits `open-max-mode, close-max-mode, show-details, export, drilldown, drillup, multi-select, edit`. Task 4's cap relies on the first three *visible* of those being the stable set.

- [ ] **Step 1: Write the failing tests**

Append to `crosstab-actions.spec.ts`, inside the top-level `describe`:

```ts
   // Under the cap of three, "the first three visible actions" is only a sensible rule if the
   // stable, assembly-level actions lead. Drill is contextual: it appears when a cell is selected,
   // so with drill first the strip reshuffles under the pointer on every selection.
   describe("toolbar order under the modern gate", () => {
      const toolbarIds = (actions: CrosstabActions): string[] =>
         actions.toolbarActions.reduce(
            (acc, g) => acc.concat(g.actions.map(a => a.id())), [] as string[]);

      afterEach(() => document.body.classList.remove("viz-modern"));

      it("leads with the stable actions under the gate", () => {
         document.body.classList.add("viz-modern");
         const actions = new CrosstabActions(TestUtils.createMockVSCrosstabModel("Crosstab1"),
            ViewerContextProviderFactory(false));

         expect(toolbarIds(actions)).toEqual([
            "crosstab open-max-mode",
            "crosstab close-max-mode",
            "crosstab show-details",
            "crosstab export",
            "crosstab drilldown",
            "crosstab drillup",
            "crosstab multi-select",
            "crosstab edit",
            "menu actions"
         ]);
      });

      it("emits the legacy order untouched when the gate is off", () => {
         const actions = new CrosstabActions(TestUtils.createMockVSCrosstabModel("Crosstab1"),
            ViewerContextProviderFactory(false));

         // Gate off still splices the Hide MiniToolbar dismissal in at index 0 (slice 1 left that
         // branch alone), so it leads here and not in the gated expectation above.
         expect(toolbarIds(actions)).toEqual([
            "vs-assembly hide-mini-toolbar",
            "crosstab drilldown",
            "crosstab drillup",
            "crosstab open-max-mode",
            "crosstab close-max-mode",
            "crosstab show-details",
            "crosstab export",
            "crosstab multi-select",
            "crosstab edit",
            "menu actions"
         ]);
      });
   });
```

- [ ] **Step 2: Run to verify they fail**

Run: `npx ng test portal --include=projects/portal/src/app/vsobjects/action/crosstab-actions.spec.ts`
Expected: the gated test FAILS (drill still leads); the gate-off test PASSES already, which is the point — it pins the order that must not change.

If the gate-off expectation itself fails, do not edit it to match. Read the actual output: it is recording today's behaviour, and a mismatch means the toolbar differs from what this plan read on `67c486d67`.

- [ ] **Step 3: Extract the action literals and return one of two orders**

Rewrite `createToolbarActions` in `crosstab-actions.ts`, lifting each object literal into a named `const` with its predicate **unchanged**, then choosing the arrangement:

```ts
   protected createToolbarActions(groups: AssemblyActionGroup[]): AssemblyActionGroup[] {
      const drilldown = {
         id: () => "crosstab drilldown",
         label: () => "_#(js:Drill Down Filter)",
         icon: () => "drill-down-filter-icon",
         enabled: () => true,
         visible: () => this.drillActionVisible() && this.isActionVisibleInViewer("Drill Down Filter")
            && !this.isDataTip() && !this.isPopComponent()
      };
      const drillup = {
         id: () => "crosstab drillup",
         label: () => "_#(js:Drill Up Filter)",
         icon: () => "drill-up-filter-icon",
         enabled: () => true,
         visible: () => this.drillActionVisible(true) && this.isActionVisibleInViewer("Drill Up Filter")
            && !this.isDataTip() && !this.isPopComponent()
      };
      const openMaxMode = {
         id: () => "crosstab open-max-mode",
         label: () => "_#(js:Show Enlarged)",
         icon: () => "expand-icon",
         enabled: () => true,
         visible: () => this.openMaxModeVisible
      };
      const closeMaxMode = {
         id: () => "crosstab close-max-mode",
         label: () => "_#(js:Show Actual Size)",
         icon: () => "contract-icon",
         enabled: () => true,
         visible: () => this.closeMaxModeVisible
      };
      const showDetails = {
         id: () => "crosstab show-details",
         label: () => "_#(js:Show Details)",
         icon: () => "show-detail-icon",
         visible: () => this.showDetailsVisible,
         enabled: () => true
      };
      const exportAction = {
         id: () => "crosstab export",
         label: () => "_#(js:Export)",
         icon: () => "export-icon",
         visible: () => !this.vsWizardPreview && this.isActionVisible("Export"),
         enabled: () => true
      };
      const multiSelect = {
         id: () => "crosstab multi-select",
         label: () => this.model.multiSelect ? "_#(js:Change to Single-select)"
            : "_#(js:Change to Multi-select)",
         icon: () => this.model.multiSelect ? "select-multi-icon" : "select-single-icon",
         enabled: () => true,
         visible: () => this.mobileDevice &&
            this.isActionVisibleInViewer("Change to Single-select") &&
            this.isActionVisibleInViewer("Change to Multi-select")
      };
      const edit = {
         id: () => "crosstab edit",
         label: () => "_#(js:Edit)",
         icon: () => "edit-icon",
         visible: () => this.editVisibility(),
         enabled: () => true
      };

      // Source order is arbitrary gate-off: it is emission order in one array literal and nothing
      // reads position. Under the gate it becomes load-bearing, because the cap of three shows the
      // first three *visible* actions — and drill is contextual, so with drill leading the strip
      // reshuffles under the pointer on every cell selection. Table and calc table already emit the
      // stable set first and need no equivalent.
      const stableFirst = [
         openMaxMode, closeMaxMode, showDetails, exportAction,
         drilldown, drillup, multiSelect, edit
      ];
      const legacyOrder = [
         drilldown, drillup, openMaxMode, closeMaxMode, showDetails, exportAction,
         multiSelect, edit
      ];

      groups.push(new AssemblyActionGroup(
         GuiTool.isVizModern() ? stableFirst : legacyOrder));

      return super.createToolbarActions(groups, true);
   }
```

`GuiTool` is already imported in `crosstab-actions.ts`; confirm before adding an import.

- [ ] **Step 4: Correct the false comment in the shared base class**

In `abstract-vs-actions.ts`, replace the comment at lines 364–368:

```ts
            // The dismissal is something done to the strip, not to the assembly, and at toolbar
            // index 0 it ate a slot ahead of show-data under a cap on toolbar length. It moves to
            // the menu instead of splicing at index 0 here; createMenuActions() below reads this
            // field to surface it there. Gated, so gate-off orgs keep index 0 and the other seven
            // assembly types with a mini-toolbar are unaffected until the rollout.
```

with:

```ts
            // The dismissal is something done to the strip, not to the assembly, and at toolbar
            // index 0 it ate a slot ahead of show-data under a cap on toolbar length. It moves to
            // the menu instead of splicing at index 0 here; createMenuActions() below reads this
            // field to surface it there. Gated on isVizModern() alone, with no type test — so under
            // the gate this reaches every assembly type, not only the anchored ones. Types that
            // have not yet joined the rollout therefore reach the dismissal by right-click until
            // their slice lands and gives them a resident kebab.
```

No code changes on this step. The previous wording claimed the other seven types were unaffected, which the missing type test contradicts; leaving it would mislead whoever writes the next family slice.

- [ ] **Step 5: Run to verify both order tests pass**

Run: `npx ng test portal --include=projects/portal/src/app/vsobjects/action/crosstab-actions.spec.ts`
Expected: PASS, both new tests plus all 36 pre-existing positional assertions and the snapshot, unedited. The gate-off path is byte-identical, so the snapshot must not move.

- [ ] **Step 6: Run the whole action suite**

Run: `npx ng test portal --include=projects/portal/src/app/vsobjects/action`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add web/projects/portal/src/app/vsobjects/action/crosstab-actions.ts \
        web/projects/portal/src/app/vsobjects/action/crosstab-actions.spec.ts \
        web/projects/portal/src/app/vsobjects/action/abstract-vs-actions.ts
git commit -m "feat(vsobjects): lead the gated crosstab strip with its stable actions

Crosstab is the only type in the table family whose toolbar leads with
contextual actions. Once the strip is capped at three, drilldown and
drillup take two of those slots whenever a cell is selected and give them
back when it is deselected, so the icons move under the pointer as the
user works. The stable set leads instead and drill overflows to the kebab.

The literals move to named locals and one of two arrangements is returned,
which is the shape the chart used: the gate-off sequence is byte-identical
to before, so all 36 positional assertions and the snapshot pass untouched.
Table and calc table already emit the stable set first and are not edited.

Also corrects a comment in the shared base class. It claimed the other
seven assembly types were unaffected by the Hide MiniToolbar move until
the rollout; that branch carries no type test, so under the gate they have
been affected since slice 1 shipped. Whoever writes the next family slice
needs the true version."
```

---

## Task 4: The table family joins the anchored set (gated)

The behaviour change. Adding three strings to the predicate turns on, for tables under the gate: the anchored strip in the title lane, the resident kebab, the cap of three action buttons, and the 32 / 56 / 52px height bands. No geometry code changes — the container's `|| 0` padding fallbacks already produce the flush, full-width lane a table wants.

**Files:**
- Modify: `web/projects/portal/src/app/vsobjects/objects/mini-toolbar/mini-toolbar.service.ts` — the `ANCHORED_ASSEMBLY_TYPES` set from Task 1
- Modify: `web/projects/portal/src/app/vsobjects/objects/mini-toolbar/mini-toolbar.service.spec.ts` — move three types across
- Modify: `web/projects/portal/src/app/vsobjects/action/abstract-vs-actions.spec.ts` — a table subclass through the cap and ladder
- Modify: `web/projects/portal/src/app/vsobjects/objects/vs-object-container.component.display.tl.spec.ts` — anchored geometry for a padding-less table

**Interfaces:**
- Consumes: `isAnchoredAssemblyType()` from Task 1; the gated crosstab order from Task 3.
- Produces: no new symbols. Later family slices extend the same set.

- [ ] **Step 1: Move the three types in the boundary test**

In `mini-toolbar.service.spec.ts`, replace the first two `it` blocks' bodies so the table family is expected to anchor:

```ts
   it("anchors the chart pilot and the table family", () => {
      expect(isAnchoredAssemblyType("VSChart")).toBe(true);
      expect(isAnchoredAssemblyType("VSTable")).toBe(true);
      expect(isAnchoredAssemblyType("VSCrosstab")).toBe(true);
      expect(isAnchoredAssemblyType("VSCalcTable")).toBe(true);
   });

   it("matches case-insensitively, as the Tool.equalsIgnoreCase calls it replaces did", () => {
      expect(isAnchoredAssemblyType("vschart")).toBe(true);
      expect(isAnchoredAssemblyType("VSCHART")).toBe(true);
      expect(isAnchoredAssemblyType("vstable")).toBe(true);
   });

   it("does not anchor the types whose rollout slices have not landed", () => {
      expect(isAnchoredAssemblyType("VSCalendar")).toBe(false);
      expect(isAnchoredAssemblyType("VSSelectionList")).toBe(false);
      expect(isAnchoredAssemblyType("VSSelectionTree")).toBe(false);
      expect(isAnchoredAssemblyType("VSSelectionContainer")).toBe(false);
   });
```

Leave the range-slider and null tests as they are.

- [ ] **Step 2: Write the failing cap-and-ladder tests for a table**

In `abstract-vs-actions.spec.ts`, add a table helper beside the existing `actionsFor` and `calendarActionsFor` helpers (after line 57):

```ts
   // A table concrete subclass, for the rollout's first family. Constructor parameter order differs
   // again from both ChartActions and CalendarActions: popService is positional 6th and
   // miniToolbarService 7th — verified against table-actions.ts rather than assumed.
   function tableActionsFor(width: number, height: number): TableActions {
      const model: VSTableModel = TestUtils.createMockVSTableModel("Table1");
      model.objectFormat.width = width;
      model.objectFormat.height = height;
      return new TableActions(model, composerContext, false, null, null, popService,
         miniToolbarService);
   }
```

Add the imports this needs, matching the file's existing style:

```ts
import { TableActions } from "./table-actions";
import { VSTableModel } from "../model/vs-table-model";
```

Then append a new `describe` block inside the top-level `describe("AbstractVSActions", ...)`:

```ts
   // The rollout's first family. Tables inherit the chart's treatment unchanged; these assert that
   // the shared machinery actually reaches them, rather than that the predicate returns true.
   //
   // Counted, not enumerated. The chart's equivalents pin exact id arrays because ChartActions'
   // stable-first order is fixed by that same slice; a table's visible set depends on model state
   // (openMaxModeVisible, showDetailsVisible) that these tests do not control, and hardcoding a
   // guessed array would either be wrong or have to be back-filled from a first run — which is not
   // a test, it is a transcript. The cap arithmetic is what this task changes, so that is what is
   // asserted: four strip entries, three of them buttons and the last the kebab.
   describe("the table family is anchored", () => {
      const ids = (groups: any[]) =>
         groups.reduce((acc, g) => acc.concat(g.actions.map(a => a.id())), [] as string[]);

      it("caps a table's strip at three buttons plus the kebab under the gate", () => {
         document.body.classList.add("viz-modern");
         const showing = ids(tableActionsFor(2000, 400).showingActions);

         expect(showing.length).toBe(4);
         expect(showing[showing.length - 1]).toBe("more actions");
      });

      it("does not cap a table when the gate is off", () => {
         // Gate off and wide enough for every action, showingActions returns toolbarActions whole.
         expect(ids(tableActionsFor(2000, 400).showingActions).length).toBeGreaterThan(4);
      });

      it("leaves a table only its kebab between the 32px floor and 56px", () => {
         document.body.classList.add("viz-modern");
         expect(ids(tableActionsFor(2000, 40).showingActions)).toEqual(["more actions"]);
      });

      it("removes all chrome from a table below the 32px floor", () => {
         document.body.classList.add("viz-modern");
         expect(ids(tableActionsFor(2000, 24).showingActions)).toEqual([]);
      });
   });
```

The first test asserts `length === 4` rather than `=== 3` for a reason worth understanding before touching it: `allowedActionsNum()` returns four **slots** under the cap, `ToolbarActionsHandler.getShowingActions()` spends one on the overflow control, and the kebab is then appended — so a capped strip is three action buttons followed by `"more actions"`. One of those three may be the `"menu actions"` wrapper rather than a table action, exactly as it is in the chart's "fewer than three available" case at `abstract-vs-actions.spec.ts:181`. That is correct behaviour, not a gap, which is why the assertion counts entries and pins only the trailing kebab.

- [ ] **Step 3: Write the failing geometry test for a padding-less table**

In `vs-object-container.component.display.tl.spec.ts`, append inside the `describe("Group 11 — anchored toolbar geometry: objectFormat-only, max mode included", ...)` block:

```ts
   // The table family declares no paddingTop/Left/Right — those fields are on vs-chart-model only —
   // so the || 0 fallbacks resolve a table to a strip flush inside the content box, spanning the
   // full width. That is the lane a table already has, which is what slice 1's rule asks for.
   it("anchors a table flush and full width, since no table model carries paddings", () => {
      const { comp } = makeComponent({
         vsObjectActions: [{ showingActions: [], toolbarActions: [] } as any],
      });
      comp.containerRef = scrollless;
      const obj: any = makeVSObject({
         objectType: "VSTable",
         objectFormat: makeObjectFormat({ top: 40, left: 250, width: 600, height: 300 }),
      });
      comp.vsInfo = makeVsInfo([obj]);

      expect(comp.isToolbarAnchored(obj)).toBe(true);
      expect(comp.getToolbarTop(obj, 0)).toBe(40);
      expect(comp.getToolbarLeft(obj, 0)).toBe(250);
      expect(comp.getAnchoredToolbarWidth(obj)).toBe(600);
      expect(comp.getToolbarLeft(obj, 0) + comp.getAnchoredToolbarWidth(obj)).toBe(250 + 600);
   });

   it("does not anchor a calendar, whose rollout slice has not landed", () => {
      const { comp } = makeComponent({
         vsObjectActions: [{ showingActions: [], toolbarActions: [] } as any],
      });
      comp.containerRef = scrollless;
      const obj: any = makeVSObject({
         objectType: "VSCalendar",
         objectFormat: makeObjectFormat({ top: 40, left: 250, width: 600, height: 300 }),
      });
      comp.vsInfo = makeVsInfo([obj]);

      expect(comp.isToolbarAnchored(obj)).toBe(false);
   });
```

- [ ] **Step 4: Run to verify the new tests fail**

Run:
```
npx ng test portal --include=projects/portal/src/app/vsobjects/objects/mini-toolbar/mini-toolbar.service.spec.ts \
  projects/portal/src/app/vsobjects/action/abstract-vs-actions.spec.ts
npx ng run portal:test-tl --include=projects/portal/src/app/vsobjects/objects/vs-object-container.component.display.tl.spec.ts
```
Expected: FAIL — the boundary test on `VSTable` not anchoring, the four cap/ladder tests because `resident` is false for a table, and the table geometry test on `isToolbarAnchored` returning false. The calendar test passes already; it is there to keep the boundary honest as later slices land.

- [ ] **Step 5: Add the three types to the set**

In `mini-toolbar.service.ts`, extend the set added in Task 1:

```ts
const ANCHORED_ASSEMBLY_TYPES: ReadonlySet<string> = new Set<string>([
   "vschart",
   // Slice 2, the table family. Table and calc table already emitted their stable actions first;
   // crosstab was reordered to match. All three inherit the chart's treatment unchanged, and take
   // the flush full-width lane the container's padding fallbacks resolve them to.
   "vstable",
   "vscrosstab",
   "vscalctable"
]);
```

- [ ] **Step 6: Run to verify the new tests pass**

Run:
```
npx ng test portal --include=projects/portal/src/app/vsobjects/objects/mini-toolbar/mini-toolbar.service.spec.ts \
  projects/portal/src/app/vsobjects/action/abstract-vs-actions.spec.ts
npx ng run portal:test-tl --include=projects/portal/src/app/vsobjects/objects/vs-object-container.component.display.tl.spec.ts
```
Expected: PASS.

Note the pre-existing tests at `abstract-vs-actions.spec.ts:127` ("does not cap a non-chart assembly even under the gate") and `:251` use **`CalendarActions`**, not a table, so they stay valid and meaningful — calendar is a later slice. If either now fails, the set was given the wrong string.

- [ ] **Step 7: Run the full portal suites**

Run:
```
npm run test:portal
# TL: scoped only, never the full suite -- see Global Constraints.
npx ng run portal:test-tl --include=projects/portal/src/app/vsobjects/objects/vs-object-container.component.display.tl.spec.ts
```
Expected: PASS. Pay attention to `mini-toolbar.component.spec.ts`, `mini-toolbar.component.tl.spec.ts` and `mini-menu.component.tl.spec.ts` — they exercise the strip through a chart, but the resident-kebab and hover-reveal machinery they cover is now reached by three more types.

- [ ] **Step 8: Commit**

```bash
git add web/projects/portal/src/app/vsobjects/objects/mini-toolbar/mini-toolbar.service.ts \
        web/projects/portal/src/app/vsobjects/objects/mini-toolbar/mini-toolbar.service.spec.ts \
        web/projects/portal/src/app/vsobjects/action/abstract-vs-actions.spec.ts \
        web/projects/portal/src/app/vsobjects/objects/vs-object-container.component.display.tl.spec.ts
git commit -m "feat(vsobjects): anchor the table family's mini-toolbar

Table, crosstab and calc table join the anchored set and inherit the chart
pilot's treatment unchanged: the strip sits in the title lane instead of
floating above the assembly, the kebab is drawn at rest, the action
buttons are capped at three and revealed on hover or focus, and the height
bands decide the row.

No geometry code changes. The container reads paddingTop/Left/Right through
|| 0 fallbacks and no table model declares those fields — they are on
vs-chart-model alone — so a table resolves to a strip flush inside the
content box, spanning the full width. That is the lane the type already
has, which is what slice 1's rule asks for; the card spec's 12px inset
belongs to the card-geometry work and is still not introduced here.

Where the title is hidden the strip overlays the column header row's
right edge, and the resting kebab therefore sits on the last column's sort
control — visible permanently where that column is sorted. Accepted rather
than special-cased: hiding the kebab there would cost those tables the
permanent affordance and, since touch has no hover, the touch route the
rollout exists to provide.

The three TEMPORARY type tests are narrowed, not removed. The selection
family and calendar are later slices, and the last of them deletes the
predicate."
```

---

## Task 5: Verification pass

No code. This task is the gate on the four before it, and its manual half is where the two cases a copy of the chart's matrix would miss get exercised.

**Files:** none modified.

**Interfaces:**
- Consumes: everything from Tasks 1–4.
- Produces: a recorded pass/fail for each row, and — if the 56px threshold proves wrong on real dashboards — a follow-up note, not a code change in this slice.

- [ ] **Step 1: Full automated suite**

Run, from `community/web`:
```
npm run test:portal
npm run test:em
npm run lint

# TL: scoped only. NEVER the full portal:tl suite -- see Global Constraints.
npx ng run portal:test-tl --include=projects/portal/src/app/vsobjects/objects/vs-object-container.component.display.tl.spec.ts
npx ng run portal:test-tl --include=projects/portal/src/app/vsobjects/objects/mini-toolbar/mini-toolbar.component.tl.spec.ts
npx ng run portal:test-tl --include=projects/portal/src/app/vsobjects/objects/mini-toolbar/mini-menu.component.tl.spec.ts
```
Expected: all PASS. Record the file/test counts, as the chart pilot's commit message did.

**Audit the snapshots against the split constraint.** Run:

```
git diff --stat 67c486d67..HEAD -- web/projects/portal/src/app/vsobjects/action/__snapshots__/
git status --short web/projects/portal/src/app/vsobjects/action/__snapshots__/
```

Expected: the three `.snap` files changed **only** by Task 2's commit, each gaining one trailing `AssemblyActionGroup` per `menuActions` entry, and nothing uncommitted. No `toolbarActions` snapshot may differ from `67c486d67`. Anything else means a gate-off path moved.

- [ ] **Step 2: Gate ON, pointer device**

Set `viewsheet.modernVisualization = true` for the org (EM → Properties, per `project_inline_svg_chart_flag`: this is a `SreeEnv` property, not a yaml `additionalProperties` entry). Open a viewsheet containing a table, a crosstab and a calc table.

**Updated for the kebab fix** (`allowedActionsNum()` now budgets off the count of real,
non-wrapper visible actions plus one slot for the wrapper — see
`.superpowers/sdd/2026-08-10-chart-card-slice2-table-family/kebab-fix-report.md`). Show
Details is contextual (`showDetailsVisible` requires a selected cell), so the strip's real
button count is 2 with nothing selected, not a flat 3 — the earlier draft of this table
assumed Show Details was always present. The fix also guarantees the kebab is never
appended empty: the trailing "menu actions" wrapper now always overflows into it whenever
it is visible at all, instead of sometimes sitting on the strip beside an empty kebab (the
defect this fix corrected).

| Check | Expected |
|---|---|
| Table with a visible title, nothing selected | Strip inside the title lane, right-aligned; kebab drawn at rest at ~55% opacity; two buttons appear on hover — Show Enlarged, Export. Kebab is never empty — in a **plain viewer** with nothing selected it opens Annotate Component (if annotations are enabled for this user), the disabled "Click on cells for additional commands" helper text, Show Enlarged, Export, and Hide MiniToolbar. Properties/Format/Hyperlink/Highlight/Conditions/etc. do **not** appear here — see the note below the table — but the "menu actions" wrapper carrying whatever *is* visible still always overflows into the kebab, so it is never empty |
| Select a cell on a **summary** table (grouped/aggregated query — `model.summary`) | Show Details joins the strip as a third button; the two originals do not move; kebab still non-empty |
| Select a cell on a **flat/detail** table (no grouping — `model.summary` false, as in `ex.png`) | Show Details never appears, strip or kebab, regardless of selection — `table-actions.ts`'s `showDetailsVisible` gates on `model.summary` specifically, unlike crosstab (inherently a summary, so no such gate) or calc table (gates on `cellSelected` instead). Not a kebab-fix concern; this is pre-existing, correct product behavior |
| Hover then move the pointer onto a button | Strip stays; no flicker loop (the pilot's `.mini-toolbar:hover` fix must hold for the anchored container) |
| Crosstab, then select a cell, **`enableAdhoc`/composer off (Edit hidden)** | Icons do **not** move. Drilldown/drillup appear in the kebab, not in the strip |
| Crosstab, then select a cell, **Edit visible** (confirmed against `ex.png`/`ex2.png`) | The **third** strip button *does* change — from Edit to Show Details, not "no change." `crosstab-actions.ts`'s stable-first array is `[openMaxMode, closeMaxMode, exportAction, showDetails, drilldown, drillup, multiSelect, edit]`: `showDetails` sits before `edit`, so once a cell is selected and `showDetails` becomes visible, it claims the third strip slot and `edit` is pushed into the kebab. `openMaxMode` and `exportAction` are pinned first and never move — only `edit` (itself conditional, and ordered last on purpose) yields. This is the same "stable-first" trade-off from Task 3/4, predates the kebab fix, and is unrelated to it — the kebab fix only changed how many *already-visible* actions land on the strip vs. the kebab, not which one occupies which slot |
| Calc table, nothing selected | Same two buttons (Show Enlarged, Export); Edit, multi-select and Show Details (until a cell is selected) all in the kebab, which is never empty |
| Right-click any button, or open the kebab | Show Enlarged, Show Actual Size, Show Details and Export all present, label-only, no glyph. Whichever route is used, none of the four is ever the *only* thing missing — the strip and the kebab are never both showing an empty/dead menu affordance at once |
| Max mode a table, then close it | Strip anchored in both states |
| Dismiss via Hide MiniToolbar in the menu | Whole strip disappears, kebab included; pointer re-entry restores it |

**Why Properties/Format/Hyperlink/Highlight/Conditions/etc. don't show up in a plain viewer's
kebab** (confirmed against `ex.png`, which shows the kebab opening to Annotate Component /
the disabled helper text / Show Enlarged / Export / Hide MiniToolbar only — no
Properties/Format/Hyperlink). This is expected, not a bug in the fix. In
`table-actions.ts`'s `createMenuActions()`, each of those entries has its own `visible()`
predicate, independent of the kebab-fix's strip/kebab cap:
- `table properties`, `table show-format-pane`, `table hyperlink`, `table
  convert-to-freehand-table`, `table reset-table-layout`, the default edit/order menus
  (copy/cut/remove/group/ungroup, bring-forward/back) — all require `this.composer` (or
  `composerBinding`/`binding`). None of these are ever reachable from the plain viewer,
  gate or no gate.
- `table highlight`, `table conditions`, `table copy-highlight`, `table paste-highlight` —
  visible in the viewer only when `model.enableAdvancedFeatures` is on for that user/org, and
  several also require a cell already selected.
- `table cell size`, `table hyperlink`, `table filter`, `table annotate cell` — all require
  `oneCellSelected`/`cellSelected`, so they don't appear until something is selected.
- `table annotate title` ("Annotate Component") is the one composer-menu-shaped entry that
  *is* reachable in the viewer, gated on `securityEnabled` and the assembly not being
  max-moded/cell-selected — which is why it's the one extra item in `ex.png` beyond
  Show Enlarged/Export/Hide MiniToolbar.

So "the kebab is never empty" is a guarantee about the wrapper always overflowing into it
when *something* in `menuActions` is visible — not a guarantee that Properties-style,
composer-only entries appear there. The corrected table above says that explicitly now.

- [ ] **Step 3: The two cases the chart's matrix would miss**

| Check | Expected | Why it is here |
|---|---|---|
| A table with `titleVisible` off, **last column sorted** | Kebab sits over the sort indicator at the header's right edge. This is accepted, not a bug — confirm it is legible and that hovering still reaches both the sort control and the strip | `.vs-header-cell-button-sort` is `position: absolute; right: 2px; width: 20px`, the same 20px the kebab occupies, and `.sort-button:not(.col-sorted)` keeps a sorted column's indicator visible |
| A table nested **inside a selection container** | **No strip at all**, and no kebab | `isMiniToolbarVisible()` returns false for `containerType === "VSSelectionContainer"`. The anchored path must inherit that, not re-enable it. Easiest thing in this slice to break silently |

- [ ] **Step 4: Density and the height bands**

| Check | Expected |
|---|---|
| A ~40-row dense grid, title hidden | One 24px glyph at 55% opacity over the header's right edge reads as chrome, not as data |
| Table at ~70px tall | Three buttons + kebab |
| Table at ~40px tall | Kebab only |
| Table at ~24px tall | No chrome; right-click is the only route |

56px remains judgement inherited from slice 1, now applied to a shape it was not tuned on. If a real dashboard shows it is wrong for tables, **record it and move the threshold in a follow-up** — do not resize the control, and do not change it inside this slice.

- [ ] **Step 5: Touch**

On a coarse-pointer device (or Chrome DevTools device emulation — note that jsdom cannot evaluate `@media (pointer: coarse)`, so this row is the only evidence for it):

| Check | Expected |
|---|---|
| Tap a table | Kebab only, at a 44px target |
| Open the kebab | The full list, including Show Enlarged and Show Details; it must not open empty |

The empty-kebab failure is the one to watch: it is the shape of defect the pilot hit twice, where a count computed from geometry disagreed with what the template rendered.

- [ ] **Step 6: Dark mode and gate off**

| Check | Expected |
|---|---|
| `viz-dark` on, repeat step 2's first row | Strip and kebab legible against the dark title lane |
| `viewsheet.modernVisualization = false`, repeat steps 2–4 | Strips float above the assembly exactly as before this slice; Hide MiniToolbar back at toolbar index 0; crosstab leads with drilldown; `show-details` back ahead of `export` in the strip |
| Gate off, **plain viewer** table/crosstab/calc table | **A `⋯ More` button now appears on the strip where previously there was none.** Expected, not a regression: in the plain viewer every menu action used to be invisible, so the More wrapper never rendered; Task 2's ungated group makes Show Enlarged visible in the menu, so the wrapper now renders. Ruled acceptable — the reachability fix is meant to reach gate-off orgs too. Confirm the button opens Show Enlarged / Show Actual Size / Show Details / Export and nothing is broken by its presence |

The gate-off pass is what protects existing customers, and it is the row most worth doing last and carefully.

- [ ] **Step 7: Record the outcome**

Append a short results note to the design doc's §6.2 table — pass/fail per row, plus anything found on the 56px threshold or the dense-grid density question, so the selection-family slice inherits the findings rather than rediscovering them.

```bash
git add community/docs/superpowers/specs/lookfeel/chart-card-slice2-tables-design.md
git commit -m "docs(chart-card): record the table family's verification results"
```

---

## Self-review notes

- **Spec coverage.** Design §3 row 1 → Task 1; row 2 → Tasks 1 and 3 Step 4; row 3 → Task 1 Step 6; row 4 → Task 3; row 5 (no change) → confirmed in Task 3's rationale and asserted by the untouched gate-off tests; row 6 → Task 2. Design §4 (title-hidden collision) → Task 5 Step 3. Design §5 (geometry) → Task 4 Step 3. Design §6.1 → Tasks 1–4's test steps; §6.2 → Task 5. Design §2's carried decisions: Case 1 → Task 5 Step 3; Case 2 → Task 4 Step 2; Case 3 → Task 5 Step 3's nested-container row; Case 4 → Task 1's range-slider assertion.
- **Type consistency.** `isAnchoredAssemblyType(objectType: string): boolean` is defined in Task 1 Step 3 and consumed under that exact name in Task 1 Steps 5–6 and Task 4 Step 5. `ANCHORED_ASSEMBLY_TYPES` is module-private throughout and never imported. Helper names `tableActionsFor`, `menuIds`, `toolbarIds` and `ids` are each defined in the task that uses them; `ids` is scoped to Task 4's new `describe` block rather than reusing the identically-named helper at `abstract-vs-actions.spec.ts:138`, which is local to a different block. Set members are lowercase in every reference, matching the predicate's `toLowerCase()`.
- **Ordering dependency.** Task 3 must land before Task 4, or an intermediate commit caps a crosstab whose contextual actions lead. Task 2 is independent of both and could move, but is placed second to mirror slice 1's own 3a-first sequence.
- **What is deliberately not here.** No selection family, no calendar, no deletion of the three `TEMPORARY` sites, no card geometry, and no change to the Hide MiniToolbar branch itself — only the comment describing it.
