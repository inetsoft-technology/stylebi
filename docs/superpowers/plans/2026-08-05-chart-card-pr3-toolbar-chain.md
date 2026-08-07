# Chart Card PR 3 — Toolbar Chain Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move the chart's mini-toolbar from a floating overlay above the assembly into the title lane, give it a kebab that is drawn at rest, cap it at three actions, and add the height test the existing fit machinery lacks.

**Architecture:** The strip stays mounted where it is — a sibling of the assembly in `vs-object-container` and five other hosts — and the change is to the position it is given, which is what spec §03 asks for ("the strip is `position:absolute` at the lane's right inset… it does not become a flow sibling of the title"). The container decides the anchored geometry because it is the component that knows the assembly's type and format; the strip gets a boolean telling it to stop subtracting its own height. Everything is keyed on `VSChart` **and** the `.viz-modern` gate, so the chart is a real pilot and the other seven assembly types are untouched for gate-off orgs.

**Tech Stack:** Angular 21.2 + TypeScript 5.9, SCSS, Vitest 4.1.7 (+ `@testing-library/angular` for `.tl.spec.ts`).

## Global Constraints

- **Design source of truth:** `docs/superpowers/specs/lookfeel/chart-card-slice1-design.md` §5. Behavioural spec: `chart-card-design/Chart Card Spec.html` §02, §03, §06.
- **§02 overstates one edit and the spec leaves the anchoring mechanism unspecified.** The kebab is *not* a new control — the machinery already exists and appears on width overflow, so this PR makes it resident rather than building a second one. See `docs/superpowers/specs/lookfeel/chart-card-source-doc-corrections.md` §1.4 and §4.1 before starting Tasks 3 and 5.
- **Reuse every visibility predicate verbatim.** No predicate in this PR is rewritten or paraphrased. The strip's contents in any given state stay decided by exactly the code that decides them today.
- **`!annotationsSelected` is load-bearing** in the Properties predicate — it is what keeps Properties out of the strip while an annotation is selected. Copy all four conjuncts.
- **Gate reader:** `GuiTool.isVizModern()` (`app/common/util/gui-tool.ts:65`).
- **Pilot condition:** every behaviour change is `VSChart` + gate. The type condition is **explicitly temporary** and is deleted during the eight-assembly rollout — say so in a comment at each site.
- **Keep 24px.** `GuiTool.MINI_TOOLBAR_HEIGHT_MODERN = 24` already ships and is coupled to the pinned container height at `mini-toolbar.component.scss:81`. **Change both together or neither.** The spec's 30px "comfortable" row is dropped as superseded — see design §7.2.
- **Suppression outranks everything here.** `isActionVisible(name)` against `model.actionNames`, and the transient `Hide MiniToolbar` dismissal, both keep working unchanged. The cap applies to whatever survives those predicates, never to the declared set.
- **One token to add:** `--inet-control-height-touch: 44px`.
- **Glyph sizes are unchanged** — buttons keep their existing `icon-size-small` class. No icon token in this PR.
- **Frontend test commands:** `cd web && npm run test:portal`, `npm run test:portal:tl`, `npm run lint`.

## Relationship to the landed mini-toolbar compaction plan

`plans/2026-07-23-viz-phase9c-item1-mini-toolbar-compaction.md` has already shipped. It made the strip
height a single gate-aware source of truth — `GuiTool.getMiniToolbarHeight()` returning
`MINI_TOOLBAR_HEIGHT_MODERN` (24) or `MINI_TOOLBAR_HEIGHT` (28) — pinned the rendered height with
`:host-context(.viz-modern)`, and added `describe("MiniToolbar.topY")` tests asserting
`topY === 72` gate-off and `topY === 76` gate-on for `top = 100`.

Two consequences:

- **Always read the accessor, never the constant.** Use `GuiTool.getMiniToolbarHeight()`; do not
  reintroduce a literal 24 or 28 anywhere in this PR.
- **This PR partly supersedes that plan's premise under the gate.** Item 1's goal was for the strip to
  "sit tight above the object"; for charts, Task 5 stops it sitting above the object at all. Its two
  existing `topY` tests still pass, because `anchorInTitleLane` defaults to `false` and those tests never
  set it — but a reviewer who has read that plan will expect the strip above the assembly, so say so in
  the PR description. The tests remain valid coverage of the unanchored path, which is still what the
  other seven types and the five authoring mount sites use.

## The ladder this PR implements

| Card height | Strip |
|---|---|
| ≥ 56px | up to 3 actions + kebab, 24px targets |
| 32 – 56px | kebab only, 24px |
| < 32px | no chrome; right-click is the only route |
| touch, any height | kebab only at 44px; renders at ≥ 52px (44 + 4 + 4) |

Only **32px is derived** (a 24px control needs 4px clearance above and below). **56px is judgement**, and
the handoff names it the likeliest wrong number in the spec because a 56px card is common in a KPI row.
Task 8 validates it against real dashboards; if it is wrong, move the threshold, never the control size.
**Never satisfy a height band by shrinking a touch target below 44px** — drop the control instead.

## Hazard the plan must respect: the spec file asserts by index

`action/chart-actions.spec.ts` asserts menu entries **positionally** — `menuActions[1].actions[0].id()`,
`menuActions[3].actions[0].id()` (lines 221, 225, 251). Adding a menu group shifts those indices and
breaks tests that have nothing to do with the change. Task 1 handles this explicitly: append the new
group last, then convert any assertion that shifted to a lookup by `id()` rather than re-indexing it.
Re-indexing just moves the trap.

---

## File Structure

| File | Responsibility in this PR |
|---|---|
| `app/vsobjects/action/chart-actions.ts` | The menu gap, the toolbar array order, the Properties entry, the two deletions |
| `app/vsobjects/action/chart-actions.spec.ts` | Existing positional assertions + new coverage |
| `app/vsobjects/action/abstract-vs-actions.ts` | Hide MiniToolbar → menu; the resident kebab; the capped/height-aware fit |
| `app/vsobjects/action/abstract-vs-actions.spec.ts` | **New if absent** — cap and ladder coverage |
| `app/vsobjects/objects/mini-toolbar/mini-toolbar.component.ts` | `anchorInTitleLane` input; `topY` |
| `app/vsobjects/objects/mini-toolbar/mini-toolbar.component.html` | Kebab mounted outside the mobile guard |
| `app/vsobjects/objects/mini-toolbar/mini-toolbar.component.scss` | Resting opacity, hover/focus reveal, reserved width |
| `app/vsobjects/objects/mini-toolbar/mini-toolbar.component.spec.ts` | Anchored `topY`, resting kebab |
| `app/vsobjects/objects/vs-object-container.component.ts` | `getToolbarTop` / `getToolbarLeft` anchored branches |
| `scss/_variables.scss` | `--inet-control-height-touch` |

---

## Task 1: Close the menu gap (3a, ungated)

**Files:**
- Modify: `app/vsobjects/action/chart-actions.ts:52` (`createMenuActions`), before `return super.createMenuActions(groups)` at `:340`
- Modify: `app/vsobjects/action/chart-actions.spec.ts`

**Interfaces:**
- Consumes: nothing.
- Produces: menu entries with ids `"chart show-data"`, `"chart open-max-mode"`, `"chart close-max-mode"`. Task 3's overflow arithmetic assumes these exist; nothing else depends on them.

`show-data`, `open-max-mode` and `close-max-mode` appear only in `createToolbarActions`, so **right-click
cannot reach max-mode at all today.** That is a standing bug independent of this spec. Ungated because it
adds reachability and removes nothing.

- [ ] **Step 1: Write the failing test**

Append to `chart-actions.spec.ts`, inside the existing `describe("ChartActions", …)`:

```ts
   function findMenuAction(actions: AssemblyActionGroup[], id: string) {
      for(const group of actions) {
         for(const action of group.actions) {
            if(action.id() === id) {
               return action;
            }
         }
      }

      return null;
   }

   it("exposes show-data and the max-mode pair in the context menu", () => {
      const menuActions = chartActions.menuActions;

      expect(findMenuAction(menuActions, "chart show-data")).toBeTruthy();
      expect(findMenuAction(menuActions, "chart open-max-mode")).toBeTruthy();
      expect(findMenuAction(menuActions, "chart close-max-mode")).toBeTruthy();
   });

   it("shows only one side of the max-mode pair at a time", () => {
      const menuActions = chartActions.menuActions;
      const open = findMenuAction(menuActions, "chart open-max-mode");
      const close = findMenuAction(menuActions, "chart close-max-mode");

      model.maxMode = false;
      expect(open.visible()).toBe(true);
      expect(close.visible()).toBe(false);

      model.maxMode = true;
      expect(open.visible()).toBe(false);
      expect(close.visible()).toBe(true);
   });
```

Match `chartActions` / `model` to the names the existing tests in this file use for the fixture — read
lines 31–60 first and reuse them rather than introducing new ones.

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd community/web && npm run test:portal -- --run chart-actions`
Expected: FAIL — `expected null to be truthy` on the first test.

- [ ] **Step 3: Append a new menu group, last**

Immediately before `return super.createMenuActions(groups);` at `chart-actions.ts:340`:

```ts
      // show-data and the max-mode pair were toolbar-only, so right-click could not reach them —
      // max-mode in particular, whose whole purpose is rescuing a chart too small to read. Predicates
      // are copied verbatim from createToolbarActions; the menu renders labels only, so no icon.
      // Appended as the last group so the positional assertions in chart-actions.spec.ts do not shift.
      groups.push(new AssemblyActionGroup([
         {
            id: () => "chart show-data",
            label: () => "_#(js:Show Summary Data)",
            icon: () => null,
            enabled: () => true,
            visible: () => this.isActionVisibleInViewer("Show Data") &&
               this.isActionVisibleInViewer("Show Summary Data")
         },
         {
            id: () => "chart open-max-mode",
            label: () => "_#(js:Show Enlarged)",
            icon: () => null,
            enabled: () => true,
            visible: () => !this.model.maxMode && !this.vsWizardPreview &&
               (this.binding || this.isActionVisibleInViewer("Open Max Mode")
                && this.isActionVisibleInViewer("Maximize") && !this.isDataTip() &&
                !this.isPopComponent()) && this.isActionVisibleInViewer("Show Enlarged")
         },
         {
            id: () => "chart close-max-mode",
            label: () => "_#(js:Show Actual Size)",
            icon: () => null,
            enabled: () => true,
            visible: () => this.model.maxMode && !this.vsWizardPreview &&
               (this.binding || this.model.maxMode &&
                this.isActionVisibleInViewer("Close Max Mode") && !this.isDataTip() &&
                !this.isPopComponent()) && this.isActionVisibleInViewer("Show Actual Size")
         }
      ]));
```

The max-mode pair is **one state-dependent entry, not two rows** — the two `visible()` predicates are
mutually exclusive on `model.maxMode`, which is what the second new test pins.

- [ ] **Step 4: Run the test to verify it passes**

Run: `cd community/web && npm run test:portal -- --run chart-actions`
Expected: the two new tests PASS.

- [ ] **Step 5: Repair any positional assertion that shifted — by id, not by index**

If `chart-actions.spec.ts:221`, `:225` or `:251` now fail, do **not** change `menuActions[1]` to
`menuActions[2]`. Convert the assertion to use `findMenuAction` from Step 1:

```ts
      expect(findMenuAction(menuActions, "chart axis-hyperlink").visible()).toBeTruthy();
```

Re-indexing preserves the fragility that caused the breakage.

- [ ] **Step 6: Run the whole action suite**

Run: `cd community/web && npm run test:portal -- --run actions`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add community/web/projects/portal/src/app/vsobjects/action/chart-actions.ts \
        community/web/projects/portal/src/app/vsobjects/action/chart-actions.spec.ts
git commit -m "fix(chart): make show-data and max-mode reachable from the context menu

Handoff step 3a. All three were toolbar-only, so right-click could not reach
max-mode at all — a standing bug whose purpose is rescuing a chart too small to
read. Predicates copied verbatim; menu rows render labels only, so no glyph is
carried across. Appended as the last group so existing positional assertions do
not shift; the ones that did are now id lookups.

Ungated: this adds reachability and removes nothing."
```

---

## Task 2: Reorder the toolbar and fix its action set (§02 edits 1–3, gated)

**Files:**
- Modify: `app/vsobjects/action/chart-actions.ts:375` (`createToolbarActions`)
- Modify: `app/vsobjects/action/chart-actions.spec.ts`

**Interfaces:**
- Consumes: `GuiTool.isVizModern()`.
- Produces: a toolbar array whose first three visible entries under the gate are `chart show-data`, the max-mode pair, and `chart properties-toolbar`. Task 3's cap depends on that order being stable.

"First three visible" is only meaningful if the stable actions come first. Today the order is drill-down,
drill-up, brush, clear-brush, zoom, clear-zoom, exclude-data, then show-data **eighth** — so with nothing
selected the strip reads correctly, but click a data point and Drill Down Filter, Brush and Zoom seize
positions 1–3 and push the chart-level actions out. Icons would reshuffle under the pointer on every
selection.

**Order is visible even without the cap**, so it is gated. Rather than duplicating twenty action literals
in two orders, extract them to named locals and return one of two arrangements.

- [ ] **Step 1: Write the failing test**

```ts
   function toolbarIds(actions: AssemblyActionGroup[]): string[] {
      return actions.reduce((ids, group) =>
         ids.concat(group.actions.filter(a => a.visible()).map(a => a.id())), [] as string[]);
   }

   describe("toolbar order under the modern gate", () => {
      afterEach(() => document.body.classList.remove("viz-modern"));

      it("puts the stable actions first when the gate is on", () => {
         document.body.classList.add("viz-modern");
         const ids = toolbarIds(chartActions.toolbarActions);

         expect(ids[0]).toBe("chart show-data");
         expect(ids.slice(0, 4)).toContain("chart properties-toolbar");
      });

      it("keeps the legacy order when the gate is off", () => {
         const ids = toolbarIds(chartActions.toolbarActions);

         expect(ids.indexOf("chart show-data"))
            .toBeGreaterThan(ids.indexOf("chart drill-down"));
      });

      it("drops the duplicated clear entries from the toolbar under the gate", () => {
         document.body.classList.add("viz-modern");
         const ids = toolbarIds(chartActions.toolbarActions);

         expect(ids).not.toContain("chart clear-brush");
         expect(ids).not.toContain("chart clear-zoom");
      });

      it("keeps the clear entries on the toolbar when the gate is off", () => {
         model.brushed = true;
         model.zoomed = true;
         const ids = toolbarIds(chartActions.toolbarActions);

         expect(ids).toContain("chart clear-brush");
         expect(ids).toContain("chart clear-zoom");
      });
   });
```

Set whatever fixture state the visibility predicates need (`model.brushed`, `model.zoomed`,
`brushable`) so the entries under test are actually visible — read the existing tests for how the
fixture is configured.

- [ ] **Step 2: Run to verify it fails**

Run: `cd community/web && npm run test:portal -- --run chart-actions`
Expected: FAIL — `chart properties-toolbar` does not exist and `show-data` is not first.

- [ ] **Step 3: Add the Properties toolbar entry**

A new id, so it cannot collide with the menu's `chart properties`. All four conjuncts are copied verbatim
from the menu entry at `chart-actions.ts:70-77`, plus the gate:

```ts
         {
            id: () => "chart properties-toolbar",
            label: () => "_#(js:Properties)...",
            icon: () => "setting-icon",
            enabled: () => true,
            // Predicate copied verbatim from the menu entry (chart properties). Do not paraphrase:
            // !annotationsSelected is what keeps Properties out of the strip while an annotation is
            // selected. Gated — this is a new button in the strip.
            visible: () => GuiTool.isVizModern() &&
               this.isActionVisibleInViewer("Properties") && !this.annotationsSelected
               && !this.isPopComponent() && !this.mobileDevice
         },
```

Wire its handler to the same one `chart properties` uses. Find it with:

```bash
cd community/web/projects/portal/src && grep -rn '"chart properties"' app/vsobjects app/composer --include=*.ts | grep -v chart-actions.ts
```

- [ ] **Step 4: Gate off the two duplicated toolbar entries**

The kebab shows overflowed toolbar actions followed by the menu groups, so the two arrays are
concatenated — and `chart clear-brush` / `chart clear-zoom` are the same operations as the menu's Clear
Brushing / View All Data. Blind concatenation would list each twice under two names on any brushed or
zoomed chart. Add `!GuiTool.isVizModern() &&` to the front of both predicates:

```ts
            visible: () => !GuiTool.isVizModern() &&
               this.model.brushed && this.isActionVisibleInViewer("Clear Brush")
               && !this.isDataTip() && !this.isPopComponent()
```

```ts
            visible: () => !GuiTool.isVizModern() &&
               this.model.zoomed && this.isActionVisibleInViewer("Clear Zoom") &&
               !this.isDataTip() && !this.isPopComponent()
```

Leave the menu's own Clear Brushing / View All Data entries alone — they now carry both operations, and
overflow becomes a genuine blind append.

- [ ] **Step 5: Extract the action literals and return one of two orders**

Refactor `createToolbarActions` so each action object is a named local (`const showData = {…}`,
`const drillDown = {…}`, and so on — one per existing literal, unchanged), then:

```ts
      // Source order is arbitrary today: it is emission order in one array literal and nothing reads
      // position. Under the gate it becomes load-bearing, because the cap of three shows the first
      // three *visible* actions — so the stable, chart-level actions have to come first or the strip
      // reshuffles under the pointer on every selection.
      const stableFirst = [
         showData, openMaxMode, closeMaxMode, propertiesToolbar,
         drillDown, drillUp, brush, clearBrush, zoom, clearZoom, excludeData,
         showDetails, manualRefresh, autoRefresh, refresh, multiSelect, edit
      ];
      const legacyOrder = [
         drillDown, drillUp, brush, clearBrush, zoom, clearZoom, excludeData,
         showData, showDetails, openMaxMode, closeMaxMode, manualRefresh, autoRefresh,
         refresh, multiSelect, edit, propertiesToolbar
      ];

      groups.push(new AssemblyActionGroup(
         GuiTool.isVizModern() ? stableFirst : legacyOrder));

      return super.createToolbarActions(groups, true);
```

`legacyOrder` must reproduce today's order exactly — verify it against `git show HEAD:…chart-actions.ts`
before moving on. `propertiesToolbar` sits last in the legacy array and its predicate keeps it invisible
there anyway, so gate-off behaviour is unchanged.

- [ ] **Step 6: Run the tests to verify all four pass**

Run: `cd community/web && npm run test:portal -- --run chart-actions`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add community/web/projects/portal/src/app/vsobjects/action/chart-actions.ts \
        community/web/projects/portal/src/app/vsobjects/action/chart-actions.spec.ts
git commit -m "feat(chart): stable toolbar actions first, add Properties, drop the duplicate clears

Chart Card Spec §02 source edits 1-3, gated. \"First three visible\" is
meaningless unless the stable actions lead: show-data was eighth, so selecting a
data point pushed the chart-level actions out and reshuffled icons under the
pointer. Properties existed only as a menu action; it gets a toolbar entry on
setting-icon with all four conjuncts copied verbatim. The clear-brush and
clear-zoom toolbar entries duplicate the menu's Clear Brushing / View All Data,
which blind overflow concatenation would list twice under two names.

Predicates are unchanged; only order and gating move. Gate-off keeps today's
exact order."
```

---

## Task 3: A resident kebab and the capped, height-aware fit (§02 edits 4–5, 3b)

**Files:**
- Modify: `app/vsobjects/action/abstract-vs-actions.ts:124` (`allowedActionsNum`), `:133` (`showingActions`)
- Modify: `app/vsobjects/objects/mini-toolbar/mini-toolbar.component.html`
- Modify: `app/vsobjects/objects/mini-toolbar/mini-toolbar.component.scss`
- Modify: `scss/_variables.scss`
- Test: `app/vsobjects/action/abstract-vs-actions.spec.ts` (create if absent)

**Interfaces:**
- Consumes: `GuiTool.isVizModern()`; `MiniToolbarService.getActionsWidth`.
- Produces:
  - `AbstractVSActions.allowedActionsNum(): number` — unchanged signature, now height-aware and capped.
  - `AbstractVSActions.get showingActions(): AssemblyActionGroup[]` — unchanged signature; the kebab is now appended whenever the gate is on for a chart, not only on overflow.
  - `--inet-control-height-touch: 44px`.

The kebab machinery already exists — `createMoreAction()` (`abstract-vs-actions.ts:321`) produces a
`menu-vertical-icon` action and `showingActions` appends it — but **only when width forces overflow**.
This task makes it resident and adds the height test the fit arithmetic lacks: `allowedActionsNum()` is
`floor(objectFormat.width / actionWidth)` and never consults height, so a 400×24 chart passes the width
test and still cannot host a strip.

- [ ] **Step 1: Add the touch-target token**

In `scss/_variables.scss`, beside `--inet-control-height-lg`:

```scss
  // A hit-target floor, not a visual size — named rather than reusing -lg (36px) so it is never
  // quietly reduced to fit a layout.
  --inet-control-height-touch: 44px;
```

- [ ] **Step 2: Write the failing tests for the ladder**

Create `app/vsobjects/action/abstract-vs-actions.spec.ts` (or append if it exists). Build the smallest
concrete subclass the constructor allows — read `abstract-vs-actions.ts`'s constructor signature and mirror
what `chart-actions.spec.ts` already does to instantiate one.

```ts
describe("AbstractVSActions fit ladder", () => {
   afterEach(() => document.body.classList.remove("viz-modern"));

   function actionsFor(width: number, height: number) {
      // reuse the fixture builder from chart-actions.spec.ts; set the format box explicitly
      const actions = createChartActionsFixture();
      actions.model.objectFormat.width = width;
      actions.model.objectFormat.height = height;
      return actions;
   }

   it("caps at three under the gate however wide the assembly", () => {
      document.body.classList.add("viz-modern");
      expect(actionsFor(2000, 400).allowedActionsNum()).toBe(3);
   });

   it("does not cap when the gate is off", () => {
      expect(actionsFor(2000, 400).allowedActionsNum()).toBeGreaterThan(3);
   });

   it("allows no actions below the 32px control floor", () => {
      document.body.classList.add("viz-modern");
      expect(actionsFor(400, 24).allowedActionsNum()).toBe(0);
   });

   it("allows no action buttons between 32 and 56px, leaving the kebab", () => {
      document.body.classList.add("viz-modern");
      expect(actionsFor(400, 40).allowedActionsNum()).toBe(0);
   });

   it("allows three actions at 56px and above", () => {
      document.body.classList.add("viz-modern");
      expect(actionsFor(400, 70).allowedActionsNum()).toBe(3);
   });

   it("still lets width bind below the cap on a narrow assembly", () => {
      document.body.classList.add("viz-modern");
      expect(actionsFor(60, 200).allowedActionsNum()).toBeLessThan(3);
   });
});
```

- [ ] **Step 3: Run to verify the gated tests fail**

Run: `cd community/web && npm run test:portal -- --run abstract-vs-actions`
Expected: the four gate-on tests FAIL; the gate-off test PASSES.

- [ ] **Step 4: Make the fit height-aware and capped**

```ts
   // Height bands for the anchored strip. Only ACTION_FLOOR is derived: a 24px control needs 4px of
   // clearance above and below, so below 32px no control fits. ACTIONS_MIN (32 + 24) is judgement —
   // the height at which a strip stops feeling like it owns the card — and is the number in this
   // ladder most likely to be wrong. Validate against real dashboards before trusting it; a 56px card
   // is common in a KPI row.
   private static readonly ACTION_FLOOR = 32;
   private static readonly ACTIONS_MIN = 56;
   private static readonly MAX_TOOLBAR_ACTIONS = 3;

   allowedActionsNum(): number {
      let actionWidth: number = Math.floor(this.miniToolbarService.getActionsWidth(this.toolbarActions) /
         this.miniToolbarService.getActionCount(this.toolbarActions));

      let num: number = Math.floor(this.model.objectFormat.width / actionWidth);

      if(!GuiTool.isVizModern()) {
         return num;
      }

      const height = this.model.objectFormat.height;

      // Below the control floor nothing fits; between the floor and ACTIONS_MIN the kebab is the
      // whole strip, so no action buttons are allowed. The kebab itself is not counted here — it is
      // appended by showingActions and is always the last thing to go.
      if(height < AbstractVSActions.ACTIONS_MIN) {
         return 0;
      }

      return Math.min(AbstractVSActions.MAX_TOOLBAR_ACTIONS, num);
   }
```

Note the two sub-32px and 32–56px bands both return 0 from *this* method; they differ in whether the
kebab renders, which Step 6 decides. Keep `ACTION_FLOOR` as the named constant Step 6 reads.

- [ ] **Step 5: Run to verify the ladder tests pass**

Run: `cd community/web && npm run test:portal -- --run abstract-vs-actions`
Expected: PASS, all six.

- [ ] **Step 6: Make the kebab resident**

In `showingActions`, the kebab is currently appended only inside the `width < getActionsWidth(...)`
branch. Add a gated branch that appends it regardless, and suppress all chrome below the floor:

```ts
   get showingActions(): AssemblyActionGroup[] {
      if(!this.toolbarActions) {
         return this.showing;
      }

      const modern = GuiTool.isVizModern();

      // No chrome at all below the control floor — a 24px control with 4px clearance does not fit,
      // and right-click becomes the only route. This is the one rung that removes the kebab.
      if(modern && this.model.objectFormat.height < AbstractVSActions.ACTION_FLOOR) {
         ToolbarActionsHandler.copyActions([], this.showing);
         return this.showing;
      }

      if(!modern && this.model.objectFormat.width >=
         this.miniToolbarService.getActionsWidth(this.toolbarActions))
      {
         return this.toolbarActions;
      }

      const actions = ToolbarActionsHandler.getShowingActions(this.toolbarActions,
         this.allowedActionsNum());
      ToolbarActionsHandler.copyActions(actions, this.showing);

      // Under the gate the kebab is resident, not an overflow control: it is the permanent "this
      // object has actions" signal, the only touch route, and the only resting keyboard target.
      // Overflowed toolbar actions are prepended to the menu groups for both entry points, so the
      // kebab and right-click always present one identical, complete list — which is what makes the
      // lower rungs safe and means the kebab is never empty.
      const needsKebab = modern || this.model.objectFormat.width <
         this.miniToolbarService.getActionsWidth(this.toolbarActions);

      if(needsKebab) {
         if(this.moreAction == null) {
            this.moreAction = this.createMoreAction();
         }

         this.showing[this.showing.length - 1].actions.push(this.moreAction);
         this.addActionHandler(this.moreAction, this.model);
      }

      return this.showing;
   }
```

If `this.showing` can be empty when the gate is on and `allowedActionsNum()` returns 0, guard the
`this.showing[this.showing.length - 1]` access by pushing an empty `AssemblyActionGroup` first — verify
what `ToolbarActionsHandler.copyActions` leaves behind for a zero count before assuming.

- [ ] **Step 7: Write a test for kebab residency**

```ts
   it("keeps a kebab at 40px where no action buttons fit", () => {
      document.body.classList.add("viz-modern");
      const ids = actionsFor(400, 40).showingActions
         .reduce((acc, g) => acc.concat(g.actions.map(a => a.id())), [] as string[]);

      expect(ids).toEqual(["more actions"]);
   });

   it("removes all chrome below 32px", () => {
      document.body.classList.add("viz-modern");
      const ids = actionsFor(400, 24).showingActions
         .reduce((acc, g) => acc.concat(g.actions.map(a => a.id())), [] as string[]);

      expect(ids).toEqual([]);
   });
```

Run: `cd community/web && npm run test:portal -- --run abstract-vs-actions`
Expected: PASS.

- [ ] **Step 8: Mount the kebab outside the mobile guard**

`mini-toolbar.component.html` wraps the whole button container in `@if (!mobileDevice)`, so on touch the
strip is not rendered at all — there is currently no visible route to these actions on mobile. The kebab
must sit **outside** that block; inside it, touch keeps exactly what it has now, which is nothing.

Restructure so the action buttons stay inside the guard and the kebab is emitted after it. Split the
kebab out of `displayActions` in the template by rendering the last action separately when its id is
`"more actions"`, or expose two getters from the component (`actionButtonGroups`, `kebabAction`) and bind
each — prefer the getters; template-side id sniffing is the kind of thing that rots.

Whichever you choose, the kebab element must be a plain `<button>` with a `title`, a
`<span class="visually-hidden">` label and the `menu-vertical-icon` glyph, matching the existing buttons
exactly, so it is an ordinary focusable control.

- [ ] **Step 9: Draw the kebab at rest**

In `mini-toolbar.component.scss`, inside the existing `:host-context(.viz-modern)` block:

```scss
  // The resting kebab is a touch and keyboard affordance, not a mouse-discoverability fix: touch has
  // no hover to sweep, and a control that exists only on hover has no resting focus target. The three
  // action buttons keep their reveal; the kebab does not.
  .mini-toolbar .mini-toolbar-kebab {
    opacity: 0.55;
  }

  .mini-toolbar:hover .mini-toolbar-kebab,
  .mini-toolbar:focus-within .mini-toolbar-kebab {
    opacity: 1;
  }

  // Opacity only, never display — and the strip reserves its full width in both states, so nothing
  // reflows when the actions appear and a centred title never jumps.
  .mini-toolbar .mini-toolbar-container {
    opacity: 0;
    transition: opacity 120ms ease-in-out;
  }

  .mini-toolbar:hover .mini-toolbar-container,
  .mini-toolbar:focus-within .mini-toolbar-container {
    opacity: 1;
  }

  // Touch: the action buttons never render (they are inside @if (!mobileDevice)), so the kebab is the
  // whole strip and takes the hit-target floor rather than any height band.
  @media (pointer: coarse) {
    .mini-toolbar .mini-toolbar-kebab {
      min-width: var(--inet-control-height-touch);
      min-height: var(--inet-control-height-touch);
      opacity: 1;
    }
  }
```

`opacity: 0` still leaves the container hit-testable, which would make invisible buttons clickable at
rest. Add `pointer-events: none` at rest and `pointer-events: auto` in the revealed state, or the strip
becomes a trap.

- [ ] **Step 10: Honour a live Hide MiniToolbar dismissal**

A persistent glyph that survived `Hide MiniToolbar` would make the action useless. Confirm the existing
`forceHide` path hides the whole `.mini-toolbar` element — including the kebab — rather than only the
button container:

```bash
cd community/web/projects/portal/src && grep -n "hidden-mini-toolbar" -A4 app/vsobjects/objects/mini-toolbar/mini-toolbar.component.scss
```
Expected: a rule on `.mini-toolbar.hidden-mini-toolbar` that hides the host. If it only targets the
container, extend it to the kebab. The dismissal is transient — `MiniToolbarService` holds one
`hideMiniToolbarAssembly` string, cleared by `handleMouseEnter` — so the strip returns on the next hover.

- [ ] **Step 11: Run the mini-toolbar suites**

Run:
```bash
cd community/web && npm run test:portal -- --run mini-toolbar && npm run test:portal:tl -- --run mini-toolbar
```
Expected: PASS. `mini-toolbar.component.spec.ts` and `mini-menu.component.tl.spec.ts` both exist; extend
the former with a test asserting the kebab renders at rest under the gate and that no action button does.

- [ ] **Step 12: Commit**

```bash
git add community/web/projects/portal/src/app/vsobjects/action/abstract-vs-actions.ts \
        community/web/projects/portal/src/app/vsobjects/action/abstract-vs-actions.spec.ts \
        community/web/projects/portal/src/app/vsobjects/objects/mini-toolbar/ \
        community/web/projects/portal/src/scss/_variables.scss
git commit -m "feat(toolbar): resident kebab and a height-aware, capped fit

Chart Card Spec §02 edits 4-5 and §06's ladder. The kebab machinery already
existed but appeared only on width overflow; it is now resident under the gate,
mounted outside the @if (!mobileDevice) guard so touch has a visible route to
these actions for the first time. Drawn at 0.55 opacity at rest with the three
actions revealed on hover or focus — opacity only, full width reserved in both
states, so nothing reflows.

allowedActionsNum() only ever divided width by ~40px, so a 400x24 chart passed
the fit test. It now caps at three and consults height: three actions at 56px
and up, kebab only from 32 to 56, no chrome below 32, 44px on coarse pointers.
The kebab is always the last thing to go.

56px is judgement, not derived — validate against real dashboards."
```

---

## Task 4: Move Hide MiniToolbar to the menu (§02 edit 6, gated, all eight types)

**Files:**
- Modify: `app/vsobjects/action/abstract-vs-actions.ts:270-300` (`createToolbarActions`)
- Test: `app/vsobjects/action/abstract-vs-actions.spec.ts`

**Interfaces:**
- Consumes: `GuiTool.isVizModern()`.
- Produces: under the gate, `"vs-assembly hide-mini-toolbar"` appears in the menu groups rather than at toolbar index 0. Task 3's cap depends on it not occupying a slot.

`createToolbarActions` does `groups.splice(0, 0, …)` to prepend the `close-icon` dismissal ahead of every
assembly action, so under a cap of three it consumes a slot before `show-data` is reached. **This is the
one edit outside the chart** — it lives in the shared base class and reaches table, crosstab, calc table,
calendar, both selection types, the container and the adhoc range slider. The edit lands in shared code
because the bug is in shared code; a chart-only override would leave a divergence that quietly becomes
permanent. The gate bounds the blast radius to modern orgs.

- [ ] **Step 1: Write the failing test**

```ts
   it("keeps the dismissal off the toolbar under the gate", () => {
      document.body.classList.add("viz-modern");
      const ids = actionsFor(400, 200).toolbarActions
         .reduce((acc, g) => acc.concat(g.actions.map(a => a.id())), [] as string[]);

      expect(ids).not.toContain("vs-assembly hide-mini-toolbar");
   });

   it("still prepends the dismissal to the toolbar when the gate is off", () => {
      const ids = actionsFor(400, 200).toolbarActions
         .reduce((acc, g) => acc.concat(g.actions.map(a => a.id())), [] as string[]);

      expect(ids[0]).toBe("vs-assembly hide-mini-toolbar");
   });

   it("exposes the dismissal in the menu under the gate", () => {
      document.body.classList.add("viz-modern");
      const ids = actionsFor(400, 200).menuActions
         .reduce((acc, g) => acc.concat(g.actions.map(a => a.id())), [] as string[]);

      expect(ids).toContain("vs-assembly hide-mini-toolbar");
   });
```

- [ ] **Step 2: Run to verify the first and third fail**

Run: `cd community/web && npm run test:portal -- --run abstract-vs-actions`
Expected: the gate-on tests FAIL; the gate-off test PASSES.

- [ ] **Step 3: Read the existing action definition before moving it**

Run: `cd community/web/projects/portal/src && sed -n '270,305p' app/vsobjects/action/abstract-vs-actions.ts`

Note that its `visible()` closes over `othersGroups` (`const othersGroups = [...groups]`, captured
*before* the splice) and is gated by `isActionVisible("Hide MiniToolbar")` so a deployment can remove it
entirely. **Both must survive the move**, including the `othersGroups` capture point.

- [ ] **Step 4: Move it, gated**

Keep the action object exactly as it is; change only where it is inserted. Under the gate, push it into
the menu groups instead of splicing at toolbar index 0. Extract the literal to a local
(`const hideMiniToolbar = { … }`) so both paths share one definition rather than two copies that can
drift, then:

```ts
         if(GuiTool.isVizModern()) {
            // The dismissal is something done to the strip, not to the assembly, and at toolbar index
            // 0 it ate a slot ahead of show-data under the cap of three. It moves to the menu. This is
            // the one edit in this spec that reaches all eight assembly types — the bug is in shared
            // code, so shared code changes rather than the chart overriding it. Gated, so gate-off
            // orgs keep index 0 and the other seven types are unaffected until the rollout.
            this.addMenuAction(hideMiniToolbar);
         }
         else {
            groups.splice(0, 0, new AssemblyActionGroup([hideMiniToolbar]));
         }
```

`addMenuAction` is illustrative — find the real mechanism this class uses to contribute a menu group and
use it. If the menu groups are not available at this point in the lifecycle, append it in
`createMenuActions` instead, guarded by the same gate, and leave a comment tying the two sites together.

- [ ] **Step 5: Run to verify all three pass**

Run: `cd community/web && npm run test:portal -- --run abstract-vs-actions`
Expected: PASS.

- [ ] **Step 6: Check the other seven types still build a sane strip**

Run: `cd community/web && npm run test:portal -- --run actions`
Expected: PASS. `base-table-actions.spec.ts` and `selection-list-actions.spec.ts` both exist and both were
touched recently — read any failure carefully rather than adjusting the assertion to match.

- [ ] **Step 7: Commit**

```bash
git add community/web/projects/portal/src/app/vsobjects/action/abstract-vs-actions.ts \
        community/web/projects/portal/src/app/vsobjects/action/abstract-vs-actions.spec.ts
git commit -m "feat(toolbar): move Hide MiniToolbar from toolbar slot 0 to the menu

Chart Card Spec §02 source edit 6, gated. createToolbarActions spliced the
close-icon dismissal in at index 0 ahead of every assembly action, so a cap of
three spent a slot on it before show-data was reached. It is something done to
the strip rather than to the assembly, so it belongs in the menu.

The edit lands in the shared base class, not a chart override: the bug is in
shared code, and an override would leave a divergence that quietly becomes
permanent. That means it reaches all eight types with a mini-toolbar, which is
why it is gated — gate-off orgs keep index 0 until the rollout reviews the other
seven strips.

The action object, its isActionVisible(\"Hide MiniToolbar\") gate and its
othersGroups capture are unchanged."
```

---

## Task 5: Anchor the strip in the title lane (§02, §03)

**Files:**
- Modify: `app/vsobjects/objects/mini-toolbar/mini-toolbar.component.ts:50-56` (inputs), `:259` (`topY`)
- Modify: `app/vsobjects/objects/vs-object-container.component.ts:464` (`getToolbarTop`), `:479` (`getToolbarLeft`)
- Modify: `app/vsobjects/objects/vs-object-container.component.html:340`
- Test: `app/vsobjects/objects/mini-toolbar/mini-toolbar.component.spec.ts`

**Interfaces:**
- Consumes: `GuiTool.isVizModern()`.
- Produces: `@Input() anchorInTitleLane: boolean = false` on `MiniToolbarComponent`. When true, `topY` returns `this.top` unmodified — the container has already placed it inside the assembly.

**Why the container decides.** `MiniToolbarComponent` has no `objectType` input and no access to
`objectFormat`, while `vs-object-container` has both. Putting the type test and the geometry in the
container keeps the strip dumb, and the boolean is the whole contract.

**Why not a flow sibling.** Spec §03 is explicit: *"the title box is always the full lane width and the
strip is `position:absolute` at the lane's right inset — for every alignment, not only centred… Anchoring
means the strip leaves the card's outside; it does not mean it becomes a flow sibling of the title."*
A centred title must centre on the card, not on the space the strip leaves.

- [ ] **Step 1: Write the failing test**

```ts
   it("stops subtracting its own height when anchored", () => {
      component.top = 200;
      component.anchorInTitleLane = true;

      expect(component.topY).toBe(200);
   });

   it("still floats above the assembly when not anchored", () => {
      component.top = 200;
      component.anchorInTitleLane = false;

      expect(component.topY).toBeLessThan(200);
   });
```

- [ ] **Step 2: Run to verify the first fails**

Run: `cd community/web && npm run test:portal -- --run mini-toolbar`
Expected: FAIL — `anchorInTitleLane` is not a property.

- [ ] **Step 3: Add the input and the `topY` branch**

```ts
   // Set by the host when the strip is positioned inside the assembly rather than floating above it.
   // The host has already resolved the anchored top/left, so this component must not adjust them.
   @Input() anchorInTitleLane: boolean = false;
```

```ts
   get topY(): number {
      if(this.isPopComponent) {
         return Number.NaN;
      }

      // Anchored: the host placed us inside the assembly's title lane, so there is no height to
      // subtract and no viewport clamping to do — an anchored strip cannot leave the assembly.
      if(this.anchorInTitleLane) {
         return this.top;
      }

      // don't cover resize handle in composer
      const adj = this.contextProvider.composer && !this.contextProvider.vsWizard ? 3 : 0;
      const minTop = 20;
      return this.top > minTop || this.forceAbove ? this.top - this.miniToolbarHeight - adj
        : this.top;
   }
```

- [ ] **Step 4: Run to verify both pass**

Run: `cd community/web && npm run test:portal -- --run mini-toolbar`
Expected: PASS.

- [ ] **Step 5: Add the container's anchored branch**

In `vs-object-container.component.ts`:

```ts
   // Chart is the anchoring pilot. TEMPORARY: this type test is deleted during the eight-assembly
   // rollout, when the other seven get a reviewed strip rather than an incidental one. See
   // chart-card-design/Anchoring beyond charts - discussion.md.
   public isToolbarAnchored(object: VSObjectModel): boolean {
      return GuiTool.isVizModern() &&
         Tool.equalsIgnoreCase(object.objectType, "VSChart");
   }
```

```ts
   public getToolbarTop(object: VSObjectModel, i: number): number {
      if(this.isToolbarAnchored(object)) {
         // Inside the assembly, at the lane's top inset. The inset is the assembly's own paddingTop —
         // what vs-title already positions against — not the card spec's 12px, which belongs to the
         // card-geometry work.
         return object.objectFormat.top + (object.paddingTop || 0);
      }

      let actionHeight = 28;
      let top = object.objectFormat.top;

      if(Tool.equalsIgnoreCase(object.objectType, "VSRangeSlider") && top < actionHeight) {
         top = top + object.objectFormat.height + actionHeight;
      }

      return top;
   }
```

```ts
   public getToolbarLeft(object: VSObjectModel, i: number): number {
      let left: number = object.objectFormat.left;

      if(i >= this.vsObjectActions.length) {
         return left;
      }

      if(this.isToolbarAnchored(object)) {
         // Right-aligned at the lane's right inset. No viewport clamping: an anchored strip is inside
         // the assembly, so there are no viewport bounds to clamp against.
         const stripWidth = this.getToolbarWidth(object);
         return left + object.objectFormat.width - (object.paddingRight || 0) - stripWidth;
      }

      return this.miniToolbarService.getToolbarLeft(left, this.containerBounds,
         this.scaleService.getCurrentScale(),
         this.containerScrollLeft, this.checkContainerHasVerticalScrollbar(),
         this.vsObjectActions[i].showingActions, this.embeddedVSBounds, (<any> object).maxMode);
   }
```

Verify `paddingTop` / `paddingRight` exist on `VSObjectModel` before relying on them:

```bash
cd community/web/projects/portal/src && grep -n "paddingTop\|paddingRight" app/vsobjects/model/vs-object-model.ts
```
If they are not on the base model but are on `VSChartModel` (they are used in
`vs-chart.component.html`), narrow the cast in `isToolbarAnchored`'s callers accordingly rather than
reaching for `any`.

- [ ] **Step 6: Bind the input at the mount site**

`vs-object-container.component.html:340`, on the `<mini-toolbar>` element:

```html
          [anchorInTitleLane]="isToolbarAnchored(vsObject)"
```

- [ ] **Step 7: Leave the other five mount sites alone, deliberately**

`editable-object-container`, `embed-chart`, `wizard-preview-container`, `vs-wizard-object` and
`vs-object-view` do not pass the input, so it defaults to `false` and they keep floating. That is correct
for the pilot: the composer and the wizard are authoring surfaces where the floating strip is
established, and widening the pilot to them is a separate decision. Record it in the PR description so
the reviewer knows it is a choice and not an oversight.

- [ ] **Step 8: Run the container and chart suites**

Run:
```bash
cd community/web && npm run test:portal -- --run vs-object-container && \
  npm run test:portal -- --run mini-toolbar && npm run test:portal:tl -- --run chart
```
Expected: PASS.

- [ ] **Step 9: Commit**

```bash
git add community/web/projects/portal/src/app/vsobjects/objects/mini-toolbar/ \
        community/web/projects/portal/src/app/vsobjects/objects/vs-object-container.component.ts \
        community/web/projects/portal/src/app/vsobjects/objects/vs-object-container.component.html
git commit -m "feat(toolbar): anchor the chart's strip in its title lane

Chart Card Spec §02 and §03. The strip was an overlay at topY = top - 28 - adj,
outside the assembly, its width a runtime outcome of however many predicates
passed, and clamped against viewport bounds. It now sits inside the assembly at
the lane's top-right inset, which deletes the clamping rather than adding to it.

The strip stays mounted where it was and stays position:absolute — per §03 it
must not become a flow sibling of the title, or a centred title would centre on
the leftover space instead of the card. The container resolves the geometry
because it is the component that knows the type and the format; the strip just
learns not to subtract its own height.

Chart-only and gated: a TEMPORARY type test, deleted during the eight-assembly
rollout. The five authoring mount sites keep floating by default."
```

---

## Task 6: Verification pass

**Files:** none modified.

- [ ] **Step 1: Full suite**

Run:
```bash
cd community/web && npm run test:portal && npm run test:portal:tl && npm run lint
```
Expected: PASS. Compare failures against a clean checkout first.

- [ ] **Step 2: Gate ON, pointer device**

| Check | Expect |
|---|---|
| A chart at rest, nothing hovered | one dim kebab at the title lane's right inset, the pill shrink-wrapped around it; no action buttons |
| Hover the chart | three actions appear to the **left** of the kebab, the pill growing leftward; **the kebab does not move** |
| Tab to the kebab with the keyboard | it focuses and opens — an entry point that does not exist today |
| Open the kebab, then right-click the same chart | **not identical** — the kebab lists overflowed *toolbar* actions plus a trailing entry that chains to the menu; right-click lists `menuActions`. What to check is that everything is *reachable* from the kebab via that chain |
| Click a data point, then look at the strip | the same three actions in the same order; no reshuffle |
| A brushed chart, kebab open | Clear Brushing appears **once**, not twice under two names |
| A zoomed chart, kebab open | View All Data appears **once** |
| Select a data point, then open the **kebab** | Brush, Zoom and Show Details are there. They are toolbar-only actions and have never been in `createMenuActions`, so **right-click cannot show them** — with the cap at three they necessarily overflow into the kebab |
| Right-click any chart | Show Summary Data, Show Enlarged / Show Actual Size present; only one side of the max-mode pair |
| Right-click any chart | Hide MiniToolbar is in the menu, not slot 0 of the strip |
| A chart with a **centred** title | the title stays centred on the card, not pushed left, and does not shift on hover |
| A chart with a **long** title | clips without an ellipsis, and the full text is on hover via `tooltipIf`. `vs-title` has `overflow: hidden` + `white-space: nowrap` and **no `text-overflow: ellipsis`** — out of scope here, since adding it changes every assembly type's title ungated. Wrapping is a per-assembly format setting (`titleFormat.wrapping.whiteSpace`), so "never wraps" holds only for the default |
| A chart with the title **hidden** | strip overlays the plot's top-right; the plot keeps the height the title would have taken |
| Resize a chart's **height** (`objectFormat.height`, the whole card — keep it wide, since width binds independently) through ≥96px, 70px, 56px, 40px, 24px | 3+kebab · 3+kebab · **watch this boundary** · kebab only · no chrome. Both comparisons are strict `<`, so 56 and 32 are inclusive on the upper side |
| **Max mode** on a chart | the strip lands on the lane inset. The anchored branch deliberately bypasses `miniToolbarService.getToolbarLeft(..., maxMode)`; that is sound because the server rewrites a maxed chart's `objectFormat` to `(0,0)+maxSize`, but it is untested placement |
| `Hide MiniToolbar`, move away, come back | strip and kebab both hidden, then both back — the dismissal is transient |
| A deployment with `Properties` denied via `actionNames` | Properties absent from the strip; cap applies to what survives |
| Select an annotation on a chart | Properties leaves the strip (`!annotationsSelected`) |
| A **table**, **crosstab**, **selection list** | still **floating**, unanchored — and Hide MiniToolbar in their menus, not slot 0 |
| Composer, wizard, embed | strip still floats |

- [ ] **Step 3: Gate ON, touch device**

| Check | Expect |
|---|---|
| A chart on a touch device | a single 44px kebab, fully opaque — the first visible route to chart actions on mobile |
| Tap it | it opens every visible **toolbar** action plus the trailing entry that chains to the menu. `allowedActionsNum()` returns 0 on mobile, so the kebab carries the whole list rather than only what overflowed past a cap of three that never rendered |
| Open the menu from the kebab | Save as Image, Show Summary Data, Show Enlarged. **Not** Properties or Date Comparison — 15 entries in `createMenuActions` carry their own `!mobileDevice`, and whether those dialogs are usable on touch is a separate product decision. Show Summary Data and Show Enlarged carry no mobile gate, so they are reachable on touch for the first time |
| No action buttons anywhere | they stay inside `@if (!mobileDevice)` |
| A 60px-tall chart | kebab renders (≥52px) |
| A 45px-tall chart | no chrome — the target was **not** shrunk to fit |

Run these as a set rather than only the tap: the mobile path changed twice late in the PR, so the tap, the
44px target and both height rows all ride code that no automated test can reach — jsdom has no
`window.matchMedia`, so `@media (pointer: coarse)` is never evaluated.

- [ ] **Step 4: Validate the 56px threshold against real dashboards**

This is the step the handoff asks for by name. Open two or three real customer-shaped dashboards
containing a KPI row and find the shortest chart cards in them. If a common card sits just under 56px and
loses its action buttons where it should keep them, **move the threshold** and record the new number and
the evidence in the PR. Do not shrink the control to satisfy the band.

**Judge 56 fresh rather than confirming it.** `allowedActionsNum()` returns a count of toolbar *slots*, and
`ToolbarActionsHandler` spends one of them on the kebab, so a cap of 3 rendered only 2 action buttons until
this PR fixed it. 56 was therefore calibrated against a two-button strip that also never included
Properties. A 56px card is showing three buttons plus a kebab for the first time.

- [ ] **Step 5: Gate OFF — confirm nothing moved**

| Check | Expect |
|---|---|
| A chart | strip **floats above** the assembly, hover-revealed, no resting kebab |
| Toolbar icon order | today's exact order — drill-down first, show-data eighth |
| A brushed/zoomed chart | Clear Brush and Clear Zoom still on the strip |
| Any assembly | Hide MiniToolbar back at slot 0 |
| All eight assembly types | unchanged |

The only intended difference from `main` in this configuration is Task 1's two new menu entries, which
are a bug fix.

- [ ] **Step 6: Open the PR**

```bash
git push -u origin feature-74519-toolbar
```

Community-only. In the description: cite `Chart Card Spec.html` §02/§03/§06 and handoff steps 3a/3b;
state that the eight-assembly rollout (3c) is **not** in this PR and that the base-class Hide MiniToolbar
edit is gated for exactly that reason; note the five authoring mount sites left floating; and record
Task 4's 56px validation result.

---

## Self-review notes

- **Spec coverage.** Design §5.2 (3a) → Task 1. §5.3 edits 1–3 → Task 2; edits 4–5 → Task 3; edit 6 →
  Task 4. §5.4 anchoring → Task 5. §5.5 ladder → Task 3 Steps 2–7 and Task 6 Step 4. §2.3 pilot condition
  → Tasks 2, 4, 5. §2.4 `--inet-control-height-touch` → Task 3 Step 1. §5.6 hazards → Task 1 Step 5
  (positional assertions), Task 3 Step 8 (mobile guard), Task 3 Step 4 (cap on visible actions). §6 →
  Task 6.
- **Not in this PR, by design.** Handoff 3c, the eight-assembly rollout. The base-class edit in Task 4
  reaches all eight but is gated, which is what makes shipping it before the rollout defensible.
- **Type consistency.** `anchorInTitleLane: boolean` (Task 5) is the only new public input.
  `isToolbarAnchored(object: VSObjectModel): boolean` (Task 5) is the only new container method.
  `ACTION_FLOOR` / `ACTIONS_MIN` / `MAX_TOOLBAR_ACTIONS` are defined in Task 3 Step 4 and read in Task 3
  Step 6 under those exact names. `"chart properties-toolbar"` is the id introduced in Task 2 and is
  distinct from the menu's existing `"chart properties"`.
- **Steps that deliberately verify before assuming.** Task 3 Step 6 (does `copyActions` leave an empty
  group?), Task 4 Step 4 (what is the real menu-contribution mechanism?), Task 5 Step 5 (are
  `paddingTop`/`paddingRight` on the base model?). Each is a place where the code could differ from this
  plan's reading; none is a placeholder — the check and both outcomes are specified.
