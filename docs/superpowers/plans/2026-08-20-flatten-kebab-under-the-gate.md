# Flatten the kebab's more-actions under the gate — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Under the modern gate, make the mini-toolbar's kebab open directly onto the actions it holds instead of nesting them behind a "More ▸" row, without resurrecting the dead kebab the wizard fix just removed and without listing Properties twice.

**Architecture:** `AbstractVSActions.getMoreActions()` already has both behaviours. It returns the raw overflow list (`this.more`, which ends in the `menu actions` wrapper) except at `allowedActionsNum() === 0`, where it returns `flattenedMoreActions()` — the overflow list merged with `menuActions`, the wrapper dropped and ids deduplicated. This plan widens the flattened branch from "only when the kebab is the whole strip" to "whenever the anchored design is in effect **and** the wrapper is offered in this host". One precondition ships first: the strip's Properties action and the menu's Properties action must share an id, or the dedup that makes flattening safe will miss them.

**Tech Stack:** Angular 21.2 + TypeScript 5.9 (portal), Vitest 4.1.7, `@testing-library/angular` for TL specs.

**Spec:** This document's §Decision below. There is no separate design doc — the decision was taken in-session on 2026-08-20 after a probe, and the roadmap entry ["What P5 left behind" item 1](../specs/lookfeel/chart-card-roadmap.md) carries the surrounding context. Read §Decision before Task 2; Trap 1 is the reason the condition is not simply `this.resident`.

**Status:** proposed — not implemented.

**Verified against:** community `viz-updates`, working tree of 2026-08-20 carrying the binding-pane/wizard predicate fixes, the `kebabHasContent()` guard and the Edit/Properties reorder. Every line cited below was read at that state. **`kebabHasContent()` must already be present** — Task 2's wizard guard depends on it.

## Global Constraints

- **Gate-off behaviour must not change.** `legacyOrder` and the width-driven kebab (`objectFormat.width < getActionsWidth(...)`) keep today's nested form. Only the `resident` branch moves.
- **Never run the full TL suite.** Scope every `*.tl.spec.ts` run with `--include`. An unfiltered run exceeds the foreground window, gets killed, and orphans multi-GB vitest workers.
- **Test commands.** Portal unit: `cd community/web && npx ng test portal --include="**/<file>.spec.ts"`. Portal TL: `cd community/web && npx ng run portal:test-tl --include="**/<file>.tl.spec.ts"`.
- **`npm run build:watch` does not reach the running server.** It writes `web/target/generated-resources/ng/inetsoft/web/resources/app`; the server serves `web/target/classes/inetsoft/web/resources/app`. Nothing syncs them per rebuild. After each rebuild, either run `./mvnw.cmd process-resources -pl web` or `cp -r target/generated-resources/ng/inetsoft/web/resources/app/. target/classes/inetsoft/web/resources/app/`, then hard-refresh — chunk names are content-hashed, so a soft reload can still serve the stale one.
- **Vitest rewrites `__snapshots__/*.snap` with CRLF.** They will show as modified with an empty `git diff`. Restore with `git checkout -- web/projects/portal/src/app/vsobjects/action/__snapshots__/` before committing.
- **Branch:** `viz-updates`. Commit per task.

---

## Decision

### Why flatten

`getMoreActions()` today, gate on, compact density, wide chart, measured by probe:

| host | kebab contents |
|---|---|
| viewer | `["menu actions"]` |
| composer | `["chart properties-toolbar", "menu actions"]` |
| object wizard | `[]` (no kebab — `kebabHasContent()` suppresses it) |

The viewer case is the argument. The kebab's entire content is a single "More ▸" row, so every menu item sits three clicks away — kebab, More, item — for no benefit over one click. `flattenedMoreActions()` was written to fix exactly this and was scoped to `allowedActionsNum() === 0`, where the kebab is the whole strip. Its own doc comment gives the reasoning: *"Nesting the menu behind a 'More' row costs three taps to reach something the strip exists to put one tap away."* That reasoning does not depend on the budget being zero.

### Trap 1 — flattening resurrects the wizard's dead kebab

The object wizard's `menuActions` carries **15 visible entries** (`chart properties`, `chart show-format-pane`, `chart show-title`, `chart conditions`, `vs-object copy`, `cut`, `remove`, `group`, `ungroup`, `bring-forward`, `bring-to-front`, `send-backward`, `send-to-back`, `chart MenuAction HelperText`, `chart show-data`). Its `more` is empty, which is precisely why `kebabHasContent()` suppresses the kebab there today.

Flatten unconditionally and `more` becomes those 15 — non-empty — so `kebabHasContent()` returns true and the kebab returns, now offering cut/copy/remove/z-order inside an object wizard. `cChartActionHandler` routes six ids there (four hyperlink variants, highlight, conditions) and no composer object handler is mounted, so most of them would do nothing. That is the defect the wizard fix removed, re-created.

**The exclusion is principled, not ad hoc.** Flattening *inlines the wrapper's contents into the kebab*, so it must only happen where the wrapper itself is offered — and the wrapper already carries `!this.vsWizardPreview` (`abstract-vs-actions.ts:492`). Same condition, same reason.

**Consequence accepted:** the wizard stops flattening on touch too, where `allowedActionsNum() === 0` previously flattened. That is consistent — the wizard deliberately offers no menu, so it should not gain one on touch either. `kebabHasContent()` then decides whether any kebab renders.

### Trap 2 — Properties would appear twice

`flattenedMoreActions()` deduplicates by id, and its comment explains why that is exact: *"the menu entries were copied verbatim from the toolbar entries, ids and visibility predicates alike, so an id is visible in both places or neither."* True for max-mode, show-details and export. **Not true for Properties**, which exists as two ids with the same label:

- `chart-actions.ts:506` — `chart properties-toolbar`, label `_#(js:Properties)...`, icon `setting-icon` (the strip)
- `chart-actions.ts:71` — `chart properties`, label `_#(js:Properties)...`, icon `fa fa-slider` (the menu)

Flattened, the composer kebab would list Properties twice.

**They are already behavioural twins.** `vs-chart-action-handler.ts:82-84` falls both ids through to one `showPropertyDialog(...)` call. The two ids exist only to tell strip from menu, and nothing depends on that distinction outside tests. Unifying them restores the convention the dedup relies on, and is why Task 1 ships first: renaming alone is invisible to users, whereas flattening first would show duplicates until the rename landed.

### Rejected alternative

Adding `chart properties-toolbar` to the flatten filter beside `menu actions` is smaller, but leaves the trap armed for the next toolbar/menu twin someone adds and keeps two ids for one action. Prefer the rename.

---

## File Structure

**Production (2 files):**
- `web/projects/portal/src/app/vsobjects/action/chart-actions.ts` — `:506`, the strip Properties id.
- `web/projects/portal/src/app/vsobjects/objects/chart/services/vs-chart-action-handler.ts` — `:83`, the now-redundant `case` label.
- `web/projects/portal/src/app/vsobjects/action/abstract-vs-actions.ts` — `:302`, the flatten condition.

**Tests (3 files):**
- `web/projects/portal/src/app/vsobjects/action/abstract-vs-actions.spec.ts` — 10 occurrences of the old id (some in comments); the flatten assertions live here.
- `web/projects/portal/src/app/vsobjects/action/chart-actions.spec.ts` — 1 occurrence.
- `web/projects/portal/src/app/vsobjects/objects/mini-toolbar/mini-toolbar.component.tl.spec.ts` — 2 occurrences, **both in comments**; update the prose so it does not name a dead id.

---

## Task 1: Unify the two Properties ids

**Files:**
- Modify: `web/projects/portal/src/app/vsobjects/action/chart-actions.ts:506`
- Modify: `web/projects/portal/src/app/vsobjects/objects/chart/services/vs-chart-action-handler.ts:82-84`
- Test: `web/projects/portal/src/app/vsobjects/action/abstract-vs-actions.spec.ts`

**Interfaces:**
- Consumes: nothing from earlier tasks.
- Produces: the strip's Properties action carries id `chart properties`, identical to the menu entry's. Task 2's dedup depends on this.

- [ ] **Step 1: Write the failing test**

Add to `abstract-vs-actions.spec.ts`, inside the outer `describe("AbstractVSActions", ...)`:

```ts
   // The strip and the menu are one action with two entry points, and vs-chart-action-handler
   // already falls both through to showPropertyDialog. Sharing the id is what lets the flattened
   // kebab merge them instead of listing Properties twice.
   describe("Properties is one id, not two", () => {
      const ids = (groups: any[]) =>
         groups.reduce((acc, g) => acc.concat(g.actions.map(a => a.id())), [] as string[]);

      it("gives the strip action the same id as the menu entry", () => {
         document.body.classList.add("viz-density-compact");
         const actions = actionsFor(2000, 400, true);

         expect(ids(actions.toolbarActions)).toContain("chart properties");
         expect(ids(actions.toolbarActions)).not.toContain("chart properties-toolbar");
      });
   });
```

- [ ] **Step 2: Run it to confirm it fails**

Run: `cd community/web && npx ng test portal --include="**/abstract-vs-actions.spec.ts"`
Expected: FAIL — `expected [ … ] to contain 'chart properties'`.

- [ ] **Step 3: Rename the id**

`chart-actions.ts:506`:

```ts
      const propertiesToolbar = {
         id: () => "chart properties",
```

- [ ] **Step 4: Drop the now-redundant case label**

`vs-chart-action-handler.ts:82-84` becomes:

```ts
      case "chart properties":
         this.showPropertyDialog(event.model, variableValues, assetId);
         break;
```

- [ ] **Step 5: Update the existing expectations**

In `abstract-vs-actions.spec.ts`, replace every remaining `"chart properties-toolbar"` string with `"chart properties"` — assertions and comments alike. Same in `chart-actions.spec.ts`. In `mini-toolbar.component.tl.spec.ts` the two occurrences are prose only (`:378`, `:420`); reword them to say `chart properties` so they do not name an id that no longer exists.

- [ ] **Step 6: Run the suites**

Run: `cd community/web && npx ng test portal --include="**/vsobjects/**/*.spec.ts"`
Expected: PASS, all files.

Run: `cd community/web && npx ng run portal:test-tl --include="**/mini-toolbar/*.tl.spec.ts"`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git checkout -- web/projects/portal/src/app/vsobjects/action/__snapshots__/
git add web/projects/portal/src/app/vsobjects/action/chart-actions.ts \
        web/projects/portal/src/app/vsobjects/objects/chart/services/vs-chart-action-handler.ts \
        web/projects/portal/src/app/vsobjects/action/abstract-vs-actions.spec.ts \
        web/projects/portal/src/app/vsobjects/action/chart-actions.spec.ts \
        web/projects/portal/src/app/vsobjects/objects/mini-toolbar/mini-toolbar.component.tl.spec.ts
git commit -m "refactor(vsobjects): make the strip and menu Properties one id"
```

---

## Task 2: Flatten wherever the wrapper is offered

**Files:**
- Modify: `web/projects/portal/src/app/vsobjects/action/abstract-vs-actions.ts:302`
- Test: `web/projects/portal/src/app/vsobjects/action/abstract-vs-actions.spec.ts`

**Interfaces:**
- Consumes: Task 1's unified `chart properties` id.
- Produces: `getMoreActions()` returns a flat, deduplicated list — no `menu actions` row — whenever `this.resident && !this.vsWizardPreview`.

- [ ] **Step 1: Write the failing tests**

Add to `abstract-vs-actions.spec.ts`. `wizardChartActions` mirrors the helper already in the `object wizard preview` describe; repeat it rather than reaching across describes.

```ts
   // Widened from allowedActionsNum() === 0 on 2026-08-20. Flattening inlines the wrapper's
   // contents into the kebab, so it happens exactly where the wrapper is offered — which is why
   // the wizard, whose wrapper is hidden by !vsWizardPreview, is excluded rather than special-cased.
   describe("the kebab opens onto its actions, not onto a More row", () => {
      const ids = (groups: any[]) =>
         groups.reduce((acc, g) => acc.concat(g.actions.map(a => a.id())), [] as string[]);
      const visibleIds = (groups: any[]) =>
         groups.reduce((acc, g) =>
            acc.concat(g.actions.filter(a => a.visible()).map(a => a.id())), [] as string[]);

      function wizardChartActions(): ChartActions {
         const model: VSChartModel = TestUtils.createMockVSChartModel("Chart1");
         model.objectFormat.width = 400;
         model.objectFormat.height = 200;
         model.vizModern = true;
         return new ChartActions(model, popService, VSWizardPreviewContextProviderFactory(),
            false, null, null, miniToolbarService);
      }

      it("drops the wrapper from the viewer's kebab and inlines the menu", () => {
         document.body.classList.add("viz-density-compact");
         const model: VSChartModel = TestUtils.createMockVSChartModel("Chart1");
         model.objectFormat.width = 2000;
         model.objectFormat.height = 400;
         model.vizModern = true;
         const actions = new ChartActions(model, popService, ViewerContextProviderFactory(false),
            false, null, null, miniToolbarService);
         const more = ids(actions.getMoreActions());

         expect(more).not.toContain("menu actions");
         ids(actions.menuActions).filter(id => id !== "menu actions")
            .forEach(id => expect(more).toContain(id));
      });

      it("lists Properties once in the composer's kebab, not twice", () => {
         document.body.classList.add("viz-density-compact");
         const more = ids(actionsFor(2000, 400, true).getMoreActions());

         expect(more.filter(id => id === "chart properties").length).toBe(1);
         expect(new Set(more).size).toBe(more.length);
      });

      // Trap 1. The wizard's menuActions has visible entries with no route; flattening them in
      // would put the kebab back and it would be dead again.
      it("leaves the wizard's kebab suppressed", () => {
         document.body.classList.add("viz-density-compact");
         const actions = wizardChartActions();

         expect(visibleIds(actions.getMoreActions())).toEqual([]);
         expect(visibleIds(actions.showingActions)).not.toContain("more actions");
      });

      it("leaves the gate-off kebab nested, as before", () => {
         const more = ids(actionsFor(120, 400, false).getMoreActions());

         expect(more).toContain("menu actions");
      });
   });
```

- [ ] **Step 2: Run to confirm the first three fail**

Run: `cd community/web && npx ng test portal --include="**/abstract-vs-actions.spec.ts"`
Expected: FAIL on "drops the wrapper", "lists Properties once" (a duplicate survives if Task 1 was skipped) and possibly "leaves the wizard's kebab suppressed"; PASS on the gate-off case.

- [ ] **Step 3: Widen the flatten condition**

`abstract-vs-actions.ts:302`, replacing the `allowedActionsNum() === 0` test:

```ts
      // Flattening inlines the wrapper's contents, so it applies exactly where the wrapper is
      // offered — hence the same !vsWizardPreview the wrapper itself carries (:492). The old
      // allowedActionsNum() === 0 test scoped this to the kebab-only rungs; the reasoning behind
      // it (a one-tap action must not sit three taps deep) never depended on the budget.
      return this.resident && !this.vsWizardPreview ? this.flattenedMoreActions() : this.more;
```

- [ ] **Step 4: Run the tests**

Run: `cd community/web && npx ng test portal --include="**/abstract-vs-actions.spec.ts"`
Expected: PASS.

- [ ] **Step 5: Update the touch tests that asserted the narrower scope**

The `touch: the kebab reaches the full menu` describe asserts flattening only on touch, and its
pointer-case counterparts assert the nested form under the gate — those now flatten too. Re-read
each assertion in that describe and update the ones that pin `menu actions` into a gate-on
pointer kebab. Keep every gate-off assertion unchanged.

- [ ] **Step 6: Run the full local scope**

Run: `cd community/web && npx ng test portal --include="**/vsobjects/**/*.spec.ts"`
Expected: PASS, all files.

Run: `cd community/web && npx ng run portal:test-tl --include="**/mini-toolbar/*.tl.spec.ts" --include="**/vs-object-container*.tl.spec.ts"`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git checkout -- web/projects/portal/src/app/vsobjects/action/__snapshots__/
git add web/projects/portal/src/app/vsobjects/action/abstract-vs-actions.ts \
        web/projects/portal/src/app/vsobjects/action/abstract-vs-actions.spec.ts
git commit -m "feat(vsobjects): open the kebab onto its actions under the gate"
```

---

## Task 3: Manual verification

No automated test covers what the kebab looks like when opened, and the two traps are both
"looks fine in a unit test, wrong on screen" shapes.

**Files:** none.

- [ ] **Step 1: Build and sync**

```bash
cd community/web && npm run build
cp -r target/generated-resources/ng/inetsoft/web/resources/app/. target/classes/inetsoft/web/resources/app/
```

Restart the server and hard-refresh (Ctrl-Shift-R).

- [ ] **Step 2: Walk the matrix**

Gate on (`viewsheet.modernVisualization`), density `compact` — at `dense` an anchored assembly
draws no chrome at all and none of this is observable.

| # | Where | Expect |
|---|---|---|
| 1 | Viewer, marked chart | Kebab opens straight onto the menu. No "More" row |
| 2 | Viewer, marked chart | "Properties…" appears exactly once |
| 3 | Composer canvas, marked chart | Kebab opens flat; Properties once; "Hide MiniToolbar" reachable in one click, not two |
| 4 | Object wizard preview | **No kebab at all** — the Trap 1 regression check |
| 5 | Binding editor, marked chart | Strip is the dismissal plus summary data; unchanged by this work |
| 6 | Viewer, marked table / crosstab / selection list | Kebab flat, no duplicate rows |
| 7 | Gate off, any chart | Kebab unchanged — nested "More ▸" still present |
| 8 | Viewer on touch or a 32–56px card | Kebab still carries the whole menu, as before |

- [ ] **Step 3: Record the outcome**

Update ["What P5 left behind" item 1](../specs/lookfeel/chart-card-roadmap.md) with the result,
and note any row that failed rather than leaving the plan's checkboxes as the only record.

---

## Self-Review

**Spec coverage.** §Decision has three requirements: flatten under the gate (Task 2 Step 3), do
not resurrect the wizard kebab (Trap 1 — Task 2 Steps 1 and 3, verified again at Task 3 row 4),
and do not duplicate Properties (Trap 2 — Task 1 in full, asserted at Task 2 Step 1). The
rejected alternative is recorded and not implemented. Covered.

**Placeholders.** None. Every code step carries the literal text to write; every run step names
the command and the expected result.

**Type consistency.** `kebabHasContent()`, `flattenedMoreActions()`, `getMoreActions()`,
`this.resident` and `this.vsWizardPreview` are used with the names and signatures they carry in
`abstract-vs-actions.ts` at the verified state. The test helpers `actionsFor`, `popService`,
`miniToolbarService`, `ViewerContextProviderFactory` and `VSWizardPreviewContextProviderFactory`
all already exist in `abstract-vs-actions.spec.ts`; `VSWizardPreviewContextProviderFactory` is
imported there as of the wizard fix.

**Scope.** Two production files plus one condition. Single subsystem, no decomposition needed.

**One known softness.** Task 2 Step 5 says "re-read each assertion and update the ones that pin
the nested form" rather than naming them, because which of that describe's assertions move
depends on Task 1 having landed and on the reorder already in the tree. Every other step is
exact. An executor who finds more than four assertions moving there should stop and re-derive
rather than mass-edit.
