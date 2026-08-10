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
import { TestUtils } from "../../common/test/test-utils";
import { GuiTool } from "../../common/util/gui-tool";
import { ComposerContextProviderFactory, ViewerContextProviderFactory } from "../context-provider.service";
import { VSCalendarModel } from "../model/calendar/vs-calendar-model";
import { VSChartModel } from "../model/vs-chart-model";
import { VSTableModel } from "../model/vs-table-model";
import { MiniToolbarService } from "../objects/mini-toolbar/mini-toolbar.service";
import { ToolbarActionsHandler } from "../toolbar-actions-handler";
import { CalendarActions } from "./calendar-actions";
import { ChartActions } from "./chart-actions";
import { TableActions } from "./table-actions";

describe("AbstractVSActions", () => {
   const popService: any = { getPopComponent: vi.fn() };
   const composerContext = ComposerContextProviderFactory();
   // A real instance (not a mock): allowedActionsNum()/showingActions divide the actual pixel
   // widths getActionsWidth()/getActionCount() compute from the DOM, and faking that arithmetic
   // would just re-encode the production formula a second time in the test.
   const miniToolbarService = new MiniToolbarService({runOutsideAngular: (fn: () => any) => fn()} as any);
   popService.getPopComponent.mockImplementation(() => "");

   // AbstractVSActions is abstract; ChartActions is the cheapest concrete subclass to exercise
   // the shared createToolbarActions()/createMenuActions() logic through. Reused by later tests
   // appended to this file.
   function actionsFor(width: number, height: number): ChartActions {
      const model: VSChartModel = TestUtils.createMockVSChartModel("Chart1");
      model.objectFormat.width = width;
      model.objectFormat.height = height;
      return new ChartActions(model, popService, composerContext, false, null, null,
         miniToolbarService);
   }

   // A non-chart concrete subclass, for the "gate + cap is chart-only" tests. Constructor
   // parameter order differs from ChartActions (popService is positional 6th here, not 2nd) —
   // verified against calendar-actions.ts rather than assumed.
   function calendarActionsFor(width: number, height: number): CalendarActions {
      const model: VSCalendarModel = TestUtils.createMockVSCalendarModel("Calendar1");
      model.objectFormat.width = width;
      model.objectFormat.height = height;
      return new CalendarActions(model, composerContext, false, null, null, popService,
         miniToolbarService);
   }

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

   afterEach(() => {
      document.body.classList.remove("viz-modern");
   });

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

   it("does not expose the dismissal in the menu when the gate is off", () => {
      const ids = actionsFor(400, 200).menuActions
         .reduce((acc, g) => acc.concat(g.actions.map(a => a.id())), [] as string[]);

      expect(ids).not.toContain("vs-assembly hide-mini-toolbar");
   });

   describe("fit ladder", () => {
      // allowedActionsNum() is in toolbar *slots*, one of which ToolbarActionsHandler spends on the
      // overflow control, so a cap of three action buttons is four slots. The rendered-button counts
      // these numbers are supposed to produce are asserted separately below and, at the DOM level,
      // in mini-toolbar.component.tl.spec.ts.
      it("caps at three actions plus the kebab under the gate however wide the assembly", () => {
         document.body.classList.add("viz-modern");
         expect(actionsFor(2000, 400).allowedActionsNum()).toBe(4);
      });

      it("does not cap when the gate is off", () => {
         expect(actionsFor(2000, 400).allowedActionsNum()).toBeGreaterThan(4);
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
         expect(actionsFor(400, 70).allowedActionsNum()).toBe(4);
      });

      it("still lets width bind below the cap on a narrow assembly", () => {
         document.body.classList.add("viz-modern");
         expect(actionsFor(60, 200).allowedActionsNum()).toBeLessThan(4);
      });

      it("does not cap a non-chart assembly even under the gate", () => {
         document.body.classList.add("viz-modern");
         expect(calendarActionsFor(2000, 400).allowedActionsNum()).toBeGreaterThan(4);
      });
   });

   // The cap's whole point is how many buttons end up on the strip, and allowedActionsNum() alone
   // cannot show that: ToolbarActionsHandler.getShowingActions() reserves one of the slots for the
   // overflow control, so the slot number and the button count differ by one whenever anything
   // overflows. These assert the ids that actually reach showingActions.
   describe("cap yields three action buttons plus the kebab", () => {
      const ids = (groups: any[]) =>
         groups.reduce((acc, g) => acc.concat(g.actions.map(a => a.id())), [] as string[]);

      it("puts the first three stable actions and the kebab on a wide strip", () => {
         document.body.classList.add("viz-modern");
         const actions = actionsFor(2000, 400);

         // chart properties-toolbar is third in ChartActions' stable-first order and is the action
         // this cap regressed: at three slots it fell into the overflow at every width.
         expect(ids(actions.showingActions)).toEqual(
            ["chart show-data", "chart open-max-mode", "chart properties-toolbar", "more actions"]);
         // Nothing is dropped — the rest, including the "menu actions" wrapper that carries the
         // full right-click menu, moves into the kebab.
         expect(ids(actions.getMoreActions())).toEqual(["chart edit", "menu actions"]);
      });

      it("keeps all three when exactly three action buttons are available", () => {
         document.body.classList.add("viz-modern");
         // Viewer with the mock model's enableAdhoc=false: chart edit is not visible, leaving
         // exactly three action buttons plus the "menu actions" wrapper.
         const model: VSChartModel = TestUtils.createMockVSChartModel("Chart1");
         model.objectFormat.width = 2000;
         model.objectFormat.height = 400;
         const actions = new ChartActions(model, popService, ViewerContextProviderFactory(false),
            false, null, null, miniToolbarService);

         expect(ids(actions.showingActions)).toEqual(
            ["chart show-data", "chart open-max-mode", "chart properties-toolbar", "more actions"]);
         expect(ids(actions.getMoreActions())).toEqual(["menu actions"]);
      });

      it("still overflows the wrapper into a non-empty kebab when fewer than three real actions are available", () => {
         document.body.classList.add("viz-modern");
         const model: VSChartModel = TestUtils.createMockVSChartModel("Chart1");
         model.objectFormat.width = 2000;
         model.objectFormat.height = 400;
         model.actionNames = ["Properties"];
         const actions = new ChartActions(model, popService, ViewerContextProviderFactory(false),
            false, null, null, miniToolbarService);

         // Pre-kebab-fix this asserted the wrapper staying on the strip beside an appended-but-empty
         // kebab — the same duplicate-menu-affordance defect reported for the table family, just on
         // chart's low-real-action edge (vschart is itself in ANCHORED_ASSEMBLY_TYPES). The kebab-fix
         // budget (realActions + 1, see allowedActionsNum()) overflows the wrapper unconditionally
         // once it's visible at all, so this now matches the table's fixed behaviour instead of
         // special-casing chart.
         expect(ids(actions.showingActions)).toEqual(
            ["chart show-data", "chart open-max-mode", "more actions"]);
         expect(ids(actions.getMoreActions())).toEqual(["menu actions"]);
      });

      it("gives up action buttons before the kebab when width binds below the cap", () => {
         document.body.classList.add("viz-modern");
         const actions = actionsFor(120, 400);
         const showing = ids(actions.showingActions);

         // 120px fits three buttons, one of which is the kebab.
         expect(showing.length).toBe(3);
         expect(showing[showing.length - 1]).toBe("more actions");
         expect(ids(actions.getMoreActions())).toContain("menu actions");
      });

      it("still leaves only the kebab between the floor and 56px", () => {
         document.body.classList.add("viz-modern");
         expect(ids(actionsFor(2000, 40).showingActions)).toEqual(["more actions"]);
      });

      it("still removes all chrome below the 32px floor", () => {
         document.body.classList.add("viz-modern");
         expect(ids(actionsFor(2000, 24).showingActions)).toEqual([]);
      });
   });

   describe("kebab residency", () => {
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

      it("still appends a kebab rather than throwing when every toolbar action is suppressed", () => {
         document.body.classList.add("viz-modern");
         // actionNames suppression is only honored by isActionVisibleInViewer() in viewer/preview
         // mode (composer ignores it), so this needs a viewer context to actually zero out every
         // action rather than the shared composer-context actionsFor() helper.
         const model: VSChartModel = TestUtils.createMockVSChartModel("Chart1");
         model.objectFormat.width = 400;
         model.objectFormat.height = 200;
         // Suppress every named chart toolbar/menu action so both groups end up with zero
         // visible actions — the scenario that leaves ToolbarActionsHandler.copyActions()
         // with nothing to append the kebab onto without the this.showing.length===0 guard.
         model.actionNames = [
            "Show Data", "Show Summary Data", "Show Enlarged", "Open Max Mode", "Maximize",
            "Show Actual Size", "Close Max Mode", "Properties", "Edit", "Menu Actions"
         ];
         const actions = new ChartActions(model, popService, ViewerContextProviderFactory(false),
            false, null, null, miniToolbarService);

         let ids: string[];
         expect(() => {
            ids = actions.showingActions
               .reduce((acc, g) => acc.concat(g.actions.map(a => a.id())), [] as string[]);
         }).not.toThrow();

         expect(ids).toEqual(["more actions"]);
      });

      it("does not cap or gate-remove chrome for a non-chart assembly under the gate", () => {
         document.body.classList.add("viz-modern");
         const ids = calendarActionsFor(2000, 400).showingActions
            .reduce((acc, g) => acc.concat(g.actions.map(a => a.id())), [] as string[]);

         expect(ids).not.toContain("more actions");
      });
   });

   // On touch, mini-toolbar.component.html renders the action-button groups inside
   // @if (!mobileDevice), so no action button ever reaches the DOM and the resident kebab is the
   // only control. allowedActionsNum() has to agree with that, because it is also the budget
   // getMoreActions() subtracts: a non-zero budget makes it skip the leading actions it believes
   // are already on the strip, so the kebab opened a short list — or, when three or fewer actions
   // were visible, nothing at all, which is the reported "tapping the kebab opens nothing".
   describe("touch: the kebab carries the whole list", () => {
      const ids = (groups: any[]) =>
         groups.reduce((acc, g) => acc.concat(g.actions.map(a => a.id())), [] as string[]);
      let mobileSpy: any = null;

      // mobileDevice is an AbstractVSActions field initializer, and chart-actions' visible()
      // predicates read it, so the stub has to be installed before the constructor runs.
      function onTouch(): void {
         mobileSpy = vi.spyOn(GuiTool, "isMobileDevice").mockReturnValue(true);
      }

      afterEach(() => {
         if(mobileSpy) {
            mobileSpy.mockRestore();
            mobileSpy = null;
         }
      });

      it("uses the same predicate the template guard evaluates", () => {
         onTouch();

         expect((actionsFor(2000, 400) as any).mobileDevice).toBe(GuiTool.isMobileDevice());
      });

      it("allows no action-button slots on a chart under the gate", () => {
         document.body.classList.add("viz-modern");
         onTouch();

         expect(actionsFor(2000, 400).allowedActionsNum()).toBe(0);
      });

      it("leaves the kebab alone on the strip, as in the 32-56px band", () => {
         document.body.classList.add("viz-modern");
         onTouch();

         expect(ids(actionsFor(2000, 400).showingActions)).toEqual(["more actions"]);
      });

      it("gives the kebab a non-empty list containing the actions the cap would have put on the strip", () => {
         document.body.classList.add("viz-modern");
         onTouch();
         const more = ids(actionsFor(2000, 400).getMoreActions());

         // The symptom: this list was empty, so VSUtil.showDropdownMenus() opened nothing.
         expect(more.length).toBeGreaterThan(0);
         // The first two of ChartActions' stable-first order — on a pointer device these are the
         // leading strip buttons, and they are exactly what getMoreActions() used to skip.
         expect(more).toContain("chart show-data");
         expect(more).toContain("chart open-max-mode");
         // Nothing visible is dropped: every visible toolbar action is reachable from the kebab.
         // Asserted by membership rather than by count, because the flattened kebab also carries
         // the menu -- see "inlines the menu instead of chaining to it" below.
         ids(ToolbarActionsHandler.getVisibleToolbarActions(actionsFor(2000, 400).toolbarActions))
            .filter(id => id !== "menu actions")
            .forEach(id => expect(more).toContain(id));
      });

      it("does not change the pointer case", () => {
         document.body.classList.add("viz-modern");
         const actions = actionsFor(2000, 400);

         expect(actions.allowedActionsNum()).toBe(4);
         expect(ids(actions.showingActions)).toEqual(
            ["chart show-data", "chart open-max-mode", "chart properties-toolbar", "more actions"]);
         expect(ids(actions.getMoreActions())).toEqual(["chart edit", "menu actions"]);
      });

      it("does not zero a non-chart assembly on touch, even under the gate", () => {
         document.body.classList.add("viz-modern");
         onTouch();

         expect(calendarActionsFor(2000, 400).allowedActionsNum()).toBeGreaterThan(4);
      });

      it("does not zero a chart on touch when the gate is off", () => {
         onTouch();

         expect(actionsFor(2000, 400).allowedActionsNum()).toBeGreaterThan(4);
      });
   });

   // Zeroing the budget puts every visible *toolbar* action in the kebab, but the right-click menu
   // is a separate list, reached only through the trailing "menu actions" wrapper whose
   // childAction() is menuActions. That wrapper carried a pre-existing !mobileDevice conjunct, so on
   // touch the kebab ended at the toolbar actions and nothing in the menu had any route at all —
   // right-click does not exist there either. The relaxation is scoped to the same resident type
   // test the cap uses, so it is the wrapper's presence in getMoreActions() that has to move, and
   // only for a chart under the gate. Ids, not counts: the count is identical in two of these four
   // states.
   describe("touch: the kebab reaches the full menu", () => {
      const ids = (groups: any[]) =>
         groups.reduce((acc, g) => acc.concat(g.actions.map(a => a.id())), [] as string[]);
      const find = (groups: any[], id: string) =>
         groups.reduce((acc, g) => acc.concat(g.actions), [] as any[]).find(a => a.id() === id);
      let mobileSpy: any = null;

      function onTouch(): void {
         mobileSpy = vi.spyOn(GuiTool, "isMobileDevice").mockReturnValue(true);
      }

      afterEach(() => {
         if(mobileSpy) {
            mobileSpy.mockRestore();
            mobileSpy = null;
         }
      });

      it("inlines the menu instead of chaining to it, so nothing sits three taps deep", () => {
         document.body.classList.add("viz-modern");
         onTouch();
         const actions = actionsFor(2000, 400);
         const more = ids(actions.getMoreActions());

         // The toolbar actions still lead, in strip order.
         expect(more.slice(0, 4)).toEqual(["chart show-data", "chart open-max-mode",
                                           "chart multi-select", "chart edit"]);
         // The wrapper is gone. Nesting the menu behind a "More" row put a one-tap action three
         // taps away, and repeated every id the menu shares with the toolbar across two panels.
         expect(more).not.toContain("menu actions");
         expect(new Set(more).size).toBe(more.length);
         // The menu is genuinely merged in, not merely unlinked.
         ids(actions.menuActions).filter(id => id !== "menu actions")
            .forEach(id => expect(more).toContain(id));
      });

      it("does not add the entry for a non-chart assembly on touch under the gate", () => {
         document.body.classList.add("viz-modern");
         onTouch();
         // 120px so the calendar's toolbar overflows and the kebab has a list at all — at 2000px
         // nothing overflows and an absent entry would prove nothing.
         const more = ids(calendarActionsFor(120, 400).getMoreActions());

         expect(more).toEqual(["calendar clear", "calendar multi-select"]);
         expect(more).not.toContain("menu actions");
      });

      it("keeps the entry for a non-chart assembly on a pointer device under the gate", () => {
         document.body.classList.add("viz-modern");

         expect(ids(calendarActionsFor(120, 400).getMoreActions()))
            .toEqual(["calendar clear", "menu actions"]);
      });

      it("does not add the entry for a chart on touch when the gate is off", () => {
         onTouch();
         const more = ids(actionsFor(120, 400).getMoreActions());

         expect(more).toEqual(["chart multi-select", "chart edit"]);
         expect(more).not.toContain("menu actions");
      });

      it("keeps the entry for a chart on a pointer device when the gate is off", () => {
         expect(ids(actionsFor(120, 400).getMoreActions()))
            .toEqual(["chart open-max-mode", "chart edit", "menu actions"]);
      });

      it("does not change the chart pointer case under the gate", () => {
         document.body.classList.add("viz-modern");

         expect(ids(actionsFor(2000, 400).getMoreActions()))
            .toEqual(["chart edit", "menu actions"]);
      });
   });

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

      // tableActionsFor() uses the composer context: table open-max-mode is hidden there
      // (openMaxModeVisible requires !composer), but table export (viewer/preview-only
      // suppression, so true in composer) and table edit (composer branch of its visible()
      // predicate) are both visible — 2 real actions. The kebab-fix budget (realActions + 1, see
      // allowedActionsNum()) always overflows the trailing "menu actions" wrapper into the kebab
      // on an anchored assembly, so the strip is those 2 real actions plus the kebab: 3 entries,
      // not the pre-fix 4 (which wrongly kept the wrapper on the strip beside an empty kebab).
      it("caps a table's strip at its real action count plus the kebab under the gate", () => {
         document.body.classList.add("viz-modern");
         const showing = ids(tableActionsFor(2000, 400).showingActions);

         expect(showing).toEqual(["table export", "table edit", "more actions"]);
      });

      it("does not cap a table when the gate is off", () => {
         // Gate off and wide enough for every action, showingActions returns toolbarActions whole.
         expect(ids(tableActionsFor(2000, 400).showingActions).length).toBeGreaterThan(4);
      });

      it("leaves a table only its kebab between the 32px floor and 56px", () => {
         document.body.classList.add("viz-modern");
         expect(ids(tableActionsFor(2000, 40).showingActions)).toEqual(["more actions"]);
      });

      // Where no action button renders, the kebab is the whole strip. Nesting the menu behind a
      // "More" row there cost three taps to reach a one-tap action, and repeated the four entries
      // the menu-reachability fix shares with the toolbar across two adjacent panels.
      it("flattens the kebab into one panel where no action button renders", () => {
         document.body.classList.add("viz-modern");
         const more = ids(tableActionsFor(2000, 40).getMoreActions());

         expect(more).not.toContain("menu actions");
         expect(new Set(more).size).toBe(more.length);
         expect(more).toContain("table open-max-mode");
         expect(more).toContain("table properties");
      });

      it("keeps the menu nested behind the wrapper while action buttons still render", () => {
         document.body.classList.add("viz-modern");

         expect(ids(tableActionsFor(2000, 400).getMoreActions())).toEqual(["menu actions"]);
      });

      it("removes all chrome from a table below the 32px floor", () => {
         document.body.classList.add("viz-modern");
         expect(ids(tableActionsFor(2000, 24).showingActions)).toEqual([]);
      });
   });

   // Reproduces the reported defect: the anchored table strip showed two real buttons plus the
   // "menu actions" wrapper (duplicating open-max-mode/export via a second menu affordance) and a
   // kebab that opened empty. allowedActionsNum()'s budget must be sized off the count of real
   // (non-wrapper) visible actions, not the raw slot formula, so the wrapper is the only thing that
   // ever overflows into the kebab on an anchored assembly.
   describe("kebab fix: the wrapper is the only thing that overflows on an anchored table", () => {
      const ids = (groups: any[]) =>
         groups.reduce((acc, g) => acc.concat(g.actions.map(a => a.id())), [] as string[]);

      // Viewer context, not composer: table open-max-mode requires !composer, and the mock's
      // enableAdhoc=false keeps table edit hidden regardless of context. This isolates the two
      // real actions (open-max-mode, export) the probe found with nothing selected.
      function viewerTableActionsFor(width: number, height: number,
                                      configure?: (model: VSTableModel) => void): TableActions
      {
         const model: VSTableModel = TestUtils.createMockVSTableModel("Table1");
         model.objectFormat.width = width;
         model.objectFormat.height = height;

         if(configure) {
            configure(model);
         }

         return new TableActions(model, ViewerContextProviderFactory(false), false, null, null,
            popService, miniToolbarService);
      }

      it("shows exactly the two real actions plus a non-empty kebab with nothing selected", () => {
         document.body.classList.add("viz-modern");
         const actions = viewerTableActionsFor(2000, 400);

         expect(ids(actions.showingActions)).toEqual(
            ["table open-max-mode", "table export", "more actions"]);
         // The defect: this used to be empty because the wrapper never overflowed.
         expect(ids(actions.getMoreActions())).toEqual(["menu actions"]);
      });

      it("shows three real actions plus a non-empty kebab with a cell selected", () => {
         document.body.classList.add("viz-modern");
         // showDetailsVisible needs summary && selectedData.size > 0 && !form (isActionVisibleInViewer
         // is unconditionally true here since viewer=false/preview=false in ViewerContextProviderFactory(false)).
         const actions = viewerTableActionsFor(2000, 400, model => {
            model.summary = true;
            model.selectedData = new Map([[0, [0]]]);
         });

         expect(ids(actions.showingActions)).toEqual(
            ["table open-max-mode", "table export", "table show-details", "more actions"]);
         expect(ids(actions.getMoreActions())).toEqual(["menu actions"]);
      });

      it("still overflows the chart's wrapper unchanged (3 real actions, budget 4)", () => {
         document.body.classList.add("viz-modern");
         const actions = actionsFor(2000, 400);

         expect(ids(actions.showingActions)).toEqual(
            ["chart show-data", "chart open-max-mode", "chart properties-toolbar", "more actions"]);
         expect(ids(actions.getMoreActions())).toEqual(["chart edit", "menu actions"]);
      });

      it("does not push a real action into the kebab when the wrapper itself is hidden", () => {
         // actionNames=["Menu Actions"] makes the wrapper's own visible() predicate false (via
         // isActionVisibleInViewer, which reads actionNames in viewer/preview mode) without
         // removing it from toolbarActions structurally — so it is filtered out of
         // getVisibleToolbarActions rather than absent from the array. That is the case the naive
         // "cap the budget to the total visible count" fix gets wrong: with the wrapper gone the
         // total visible count equals realActions (2), and a budget of exactly 2 would force one of
         // the two real actions into the kebab. This asserts both stay on the strip.
         document.body.classList.add("viz-modern");
         const actions = viewerTableActionsFor(2000, 400, model => {
            model.actionNames = ["Menu Actions"];
         });

         expect(ids(actions.showingActions)).toEqual(
            ["table open-max-mode", "table export", "more actions"]);
         // Nothing to overflow — the wrapper was never visible in the first place.
         expect(ids(actions.getMoreActions())).toEqual([]);
      });
   });
});
