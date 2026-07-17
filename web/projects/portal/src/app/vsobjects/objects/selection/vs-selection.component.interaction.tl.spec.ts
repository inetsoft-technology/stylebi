/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
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

/**
 * VSSelection �?Pass 1: Interaction
 *
 * Risk-first coverage:
 *   Group 1 �?ngOnInit: globalSubmitService subscriptions for globalSubmit and updateSelections
 *   Group 2 �?ngOnDestroy: subscription cleanup for actionSubscription, adhocFilterListener,
 *                           subscriptions, unApplySubscription, overlay listeners
 *   Group 3 �?set controller: subscription wiring for unappliedSubject and updateViewSubject
 *   Group 4 �?set actions: all event.id dispatch cases (unselect/hide/show/reverse/sort/search/
 *                          max-mode/apply/remove-child/select-subtree/clear-subtree/select-all/
 *                          menu-actions/format-pane/more-actions)
 *   Group 5 �?toggleMaxMode: sends MaxObjectEvent and emits maxModeChange
 *   Group 6 �?onSelectAll / onUnselect / onReverse: selection management flows
 *   Group 7 �?processExpandTreeNodesCommand: script-triggered tree expansion
 */

import { Subject } from "rxjs";

import {
   assignController,
   createMockActions,
   createMockController,
   createMockGlobalSubmitService,
   makeCompositeRoot,
   makeExpandTreeNodesCommand,
   makeMockListModel,
   makeMockSelectionValues,
   makeMockTreeModel,
   makeSelectionValue,
   createSelectionComponent,
   injectController,
} from "./vs-selection.component.test-helpers";

afterEach(() => vi.restoreAllMocks());

async function renderComponent(overrides: any = {}) {
   return createSelectionComponent(overrides);
}

describe("VSSelection �?Pass 1: Interaction", () => {
   describe("Group 1 �?ngOnInit()", () => {
      it("should subscribe to globalSubmitService.globalSubmit", async () => {
         const { comp, globalSubmitService } = await renderComponent();
         comp.ngOnInit();
         expect(globalSubmitService.globalSubmit).toHaveBeenCalled();
      });

      it("should subscribe to globalSubmitService.updateSelections", async () => {
         const { comp, globalSubmitService } = await renderComponent();
         comp.ngOnInit();
         expect(globalSubmitService.updateSelections).toHaveBeenCalled();
      });

      it("should call controller.applySelections when globalSubmit emits and submitOnChange is false", async () => {
         const { comp, globalSubmitService } = await renderComponent();
         comp.model.submitOnChange = false;
         const controller = createMockController(makeMockListModel());
         comp["_controller"] = controller;
         const emitSubject = new Subject<string>();
         vi.spyOn(globalSubmitService, "globalSubmit").mockReturnValue(emitSubject.asObservable());
         comp.ngOnInit();

         emitSubject.next("test-source");
         expect(controller.applySelections).toHaveBeenCalledWith("test-source");
      });

      it("should push changes to unappliedSelections and call applySelections when updateSelections emits and submitOnChange is true", async () => {
         const { comp, globalSubmitService } = await renderComponent();
         comp.model.submitOnChange = true;
         const controller = createMockController(makeMockListModel());
         comp["_controller"] = controller;

         const emitSubject = new Subject<Map<string, any[]>>();
         vi.spyOn(globalSubmitService, "updateSelections").mockReturnValue(emitSubject.asObservable());
         comp.ngOnInit();

         const changes = new Map([["TestSelectionList", [{ value: "test" }]]]);
         emitSubject.next(changes);
         expect(controller.unappliedSelections.length).toBe(1);
         expect(controller.applySelections).toHaveBeenCalled();
      });

      it("should call updateStatusByValues when updateSelections emits and submitOnChange is false", async () => {
         const { comp, globalSubmitService } = await renderComponent();
         comp.model.submitOnChange = false;
         const controller = createMockController(makeMockListModel());
         comp["_controller"] = controller;

         const emitSubject = new Subject<Map<string, any[]>>();
         vi.spyOn(globalSubmitService, "updateSelections").mockReturnValue(emitSubject.asObservable());
         comp.ngOnInit();

         const changes = new Map([["TestSelectionList", [{ value: "test" }]]]);
         emitSubject.next(changes);
         expect(controller.updateStatusByValues).toHaveBeenCalled();
      });
   });

   // Group 2: private subscription/listener fields are seeded directly because ngOnDestroy
   // must clean them up; going through full subscription setup would obscure the signal.
   describe("Group 2 �?ngOnDestroy()", () => {
      it("should unsubscribe from actionSubscription", async () => {
         const { comp } = await renderComponent();
         const actionSubscription = { unsubscribe: vi.fn() };
         comp["actionSubscription"] = actionSubscription as any;
         comp.ngOnDestroy();
         expect(actionSubscription.unsubscribe).toHaveBeenCalled();
      });

      it("should call adhocFilterListener", async () => {
         const { comp } = await renderComponent();
         const adhocFilterListener = vi.fn();
         comp["adhocFilterListener"] = adhocFilterListener;
         comp.ngOnDestroy();
         expect(adhocFilterListener).toHaveBeenCalled();
      });

      it("should unsubscribe from subscriptions", async () => {
         const { comp } = await renderComponent();
         const subscriptions = { unsubscribe: vi.fn() };
         comp["subscriptions"] = subscriptions as any;
         comp.ngOnDestroy();
         expect(subscriptions.unsubscribe).toHaveBeenCalled();
      });

      it("should unsubscribe from unApplySubscription", async () => {
         const { comp } = await renderComponent();
         const unApplySubscription = { unsubscribe: vi.fn() };
         comp["unApplySubscription"] = unApplySubscription as any;
         comp.ngOnDestroy();
         expect(unApplySubscription.unsubscribe).toHaveBeenCalled();
      });
   });

   // Group 3: private previous-subscription fields are seeded to verify the controller setter
   // unsubscribes the old subscription before wiring the new one.
   describe("Group 3 �?set controller", () => {
      it("should unsubscribe from previous unApplySubscription when controller changes", async () => {
         const { comp } = await renderComponent();
         const prevUnApplySubscription = { unsubscribe: vi.fn() };
         comp["unApplySubscription"] = prevUnApplySubscription as any;
         const newController = createMockController(makeMockListModel());
         assignController(comp, newController);
         expect(prevUnApplySubscription.unsubscribe).toHaveBeenCalled();
      });

      it("should unsubscribe from previous updateViewSubscription when controller changes", async () => {
         const { comp } = await renderComponent();
         const prevUpdateViewSubscription = { unsubscribe: vi.fn() };
         comp["updateViewSubscription"] = prevUpdateViewSubscription as any;
         const newController = createMockController(makeMockListModel());
         assignController(comp, newController);
         expect(prevUpdateViewSubscription.unsubscribe).toHaveBeenCalled();
      });

      it("should subscribe to controller.unappliedSubject", async () => {
         const { comp, globalSubmitService } = await renderComponent();
         const controller = createMockController(makeMockListModel());
         assignController(comp, controller);
         controller.unappliedSubject.next(true);
         expect(globalSubmitService.updateState).toHaveBeenCalled();
      });

      it("should subscribe to controller.updateViewSubject", async () => {
         const { comp } = await renderComponent();
         const controller = createMockController(makeMockListModel());
         assignController(comp, controller);
         const detectChangesSpy = vi.spyOn(comp["changeDetectorRef"], "detectChanges");
         controller.updateViewSubject.next();
         expect(detectChangesSpy).toHaveBeenCalled();
      });
   });

   describe("Group 4 �?set actions", () => {
      it("should unsubscribe from previous actionSubscription when actions changes", async () => {
         const { comp } = await renderComponent();
         const prevActionSubscription = { unsubscribe: vi.fn() };
         comp["actionSubscription"] = prevActionSubscription as any;
         const newActions = createMockActions();
         comp.actions = newActions as any;
         expect(prevActionSubscription.unsubscribe).toHaveBeenCalled();
      });

      it("should subscribe to actions.onAssemblyActionEvent", async () => {
         const { comp } = await renderComponent();
         const actions = createMockActions();
         comp.controller = createMockController(makeMockListModel());
         comp.actions = actions as any;

         actions.onAssemblyActionEvent.next({ id: "selection-list unselect" });
         expect(comp.controller.clearSelections).toHaveBeenCalled();
      });

      it("should handle selection-list hide action", async () => {
         const { comp } = await renderComponent();
         const controller = createMockController(makeMockListModel());
         injectController(comp, controller);
         const actions = createMockActions();
         comp.actions = actions as any;

         actions.onAssemblyActionEvent.next({ id: "selection-list hide" });
         expect(controller.hideSelf).toHaveBeenCalled();
      });

      it("should handle selection-list show action", async () => {
         const { comp } = await renderComponent();
         const controller = createMockController(makeMockListModel());
         injectController(comp, controller);
         const actions = createMockActions();
         comp.actions = actions as any;

         actions.onAssemblyActionEvent.next({ id: "selection-list show" });
         expect(controller.showSelf).toHaveBeenCalled();
      });

      it("should handle selection-list reverse action", async () => {
         const { comp } = await renderComponent();
         const controller = createMockController(makeMockListModel());
         injectController(comp, controller);
         const actions = createMockActions();
         comp.actions = actions as any;

         actions.onAssemblyActionEvent.next({ id: "selection-list reverse" });
         expect(controller.reverseSelections).toHaveBeenCalled();
      });

      it("should handle selection-list sort action", async () => {
         const { comp } = await renderComponent();
         const controller = createMockController(makeMockListModel());
         injectController(comp, controller);
         const actions = createMockActions();
         comp.actions = actions as any;

         actions.onAssemblyActionEvent.next({ id: "selection-list sort" });
         expect(controller.sortSelections).toHaveBeenCalled();
      });

      it("should handle selection-list search action", async () => {
         const { comp } = await renderComponent();
         const controller = createMockController(makeMockListModel());
         injectController(comp, controller);
         const actions = createMockActions();
         comp.actions = actions as any;

         // toggleSearchDisplay() → onSearch() schedules a real setTimeout(...,200) that reads
         // selectionListSearchInputElementRef.nativeElement, which is never set up in this test
         // (no real search input rendered). Left as a real timer, it fires after this test's
         // fixture is destroyed and crashes as an Uncaught Exception attributed to a later spec
         // file. Fake timers keep it from ever firing since it's never advanced.
         vi.useFakeTimers();
         try {
            actions.onAssemblyActionEvent.next({ id: "selection-list search" });
            expect(comp.model.searchDisplayed).toBe(true);
         }
         finally {
            vi.useRealTimers();
         }
      });

      it("should handle selection-list open-max-mode action", async () => {
         const { comp } = await renderComponent();
         const toggleMaxModeSpy = vi.spyOn(comp, "toggleMaxMode");
         const actions = createMockActions();
         comp.actions = actions as any;

         actions.onAssemblyActionEvent.next({ id: "selection-list open-max-mode" });
         expect(toggleMaxModeSpy).toHaveBeenCalled();
      });

      it("should handle selection-list apply action", async () => {
         const { comp } = await renderComponent();
         const controller = createMockController(makeMockListModel());
         injectController(comp, controller);
         const actions = createMockActions();
         comp.actions = actions as any;

         actions.onAssemblyActionEvent.next({ id: "selection-list apply" });
         expect(controller.applySelections).toHaveBeenCalled();
      });

      it("should handle selection-list select-all action", async () => {
         const { comp } = await renderComponent();
         const controller = createMockController(makeMockListModel());
         injectController(comp, controller);
         const actions = createMockActions();
         comp.actions = actions as any;

         actions.onAssemblyActionEvent.next({ id: "selection-list select-all" });
         expect(controller.applySelections).toHaveBeenCalled();
      });

      it("should handle selection-tree select-subtree action", async () => {
         const { comp } = await renderComponent();
         const treeModel = makeMockTreeModel();
         treeModel.root = makeCompositeRoot();
         comp.model = treeModel;
         const controller = {
            ...createMockController(treeModel),
            selectSubtree: vi.fn(),
         };
         injectController(comp, controller);
         comp.model.contextMenuCell = makeSelectionValue({ state: 0, level: 0, value: "test" });
         const actions = createMockActions();
         comp.actions = actions as any;

         actions.onAssemblyActionEvent.next({ id: "selection-tree select-subtree" });
         expect(controller.selectSubtree).toHaveBeenCalled();
      });

      it("should handle selection-tree clear-subtree action", async () => {
         const { comp } = await renderComponent();
         const treeModel = makeMockTreeModel();
         treeModel.root = makeCompositeRoot();
         comp.model = treeModel;
         const controller = {
            ...createMockController(treeModel),
            selectSubtree: vi.fn(),
            setSubtree: vi.fn(),
            clearSingleCellSubTree: vi.fn(),
         };
         injectController(comp, controller);
         comp.model.contextMenuCell = makeSelectionValue({ state: 0, level: 0, value: "test" });
         const actions = createMockActions();
         comp.actions = actions as any;

         actions.onAssemblyActionEvent.next({ id: "selection-tree clear-subtree" });
         expect(controller.setSubtree).toHaveBeenCalled();
      });

      it("should handle selection-list show-format-pane action", async () => {
         const { comp } = await renderComponent();
         const actions = createMockActions();
         comp.actions = actions as any;

         let emittedModel: any = null;
         comp.onOpenFormatPane.subscribe(model => emittedModel = model);
         actions.onAssemblyActionEvent.next({ id: "selection-list show-format-pane" });
         expect(emittedModel).toBe(comp.model);
      });

      it("should emit removeChild for viewer-remove-from-container action", async () => {
         const { comp } = await renderComponent();
         const actions = createMockActions();
         comp.actions = actions as any;

         let emitted = false;
         comp.removeChild.subscribe(() => emitted = true);
         actions.onAssemblyActionEvent.next({ id: "selection-list viewer-remove-from-container" });
         expect(emitted).toBe(true);
      });
   });

   describe("Group 5 �?toggleMaxMode()", () => {
      it("should send MaxObjectEvent via viewsheetClient", async () => {
         const viewsheetClient = { sendEvent: vi.fn(), commands: new Subject<any>().asObservable() };
         const { comp } = await renderComponent({ viewsheetClient });
         comp.toggleMaxMode();
         expect(viewsheetClient.sendEvent).toHaveBeenCalledWith(
            "/events/vs/assembly/max-mode/toggle",
            expect.any(Object)
         );
      });

      it("should emit maxModeChange event", async () => {
         const { comp } = await renderComponent();
         let emitted: any = null;
         comp.maxModeChange.subscribe(data => emitted = data);
         comp.toggleMaxMode();
         expect(emitted).toEqual({ assembly: comp.model.absoluteName, maxMode: true });
      });

      it("should call controller.showSelf when hidden and maxMode is false", async () => {
         const { comp } = await renderComponent();
         const controller = createMockController(makeMockListModel());
         assignController(comp, controller);
         comp.model.dropdown = true;
         comp.model.hidden = true;
         comp.model.maxMode = false;
         comp.toggleMaxMode();
         expect(controller.showSelf).toHaveBeenCalled();
      });
   });

   describe("Group 6 �?selection management", () => {
      it("should call controller.applySelections when onSelectAll is called", async () => {
         const { comp } = await renderComponent();
         const controller = createMockController(makeMockListModel());
         injectController(comp, controller);
         comp.onSelectAll();
         expect(controller.applySelections).toHaveBeenCalled();
      });

      it("should call controller.clearSelections when onUnselect is called", async () => {
         const { comp } = await renderComponent();
         const controller = createMockController(makeMockListModel());
         injectController(comp, controller);
         comp.onUnselect();
         expect(controller.clearSelections).toHaveBeenCalled();
      });

      it("should call controller.reverseSelections when onReverse is called with submitOnChange", async () => {
         const { comp } = await renderComponent();
         comp.model.submitOnChange = true;
         const controller = createMockController(makeMockListModel());
         injectController(comp, controller);
         comp.onReverse();
         expect(controller.reverseSelections).toHaveBeenCalled();
      });

      it("should call controller.sortSelections when onSort is called", async () => {
         const { comp } = await renderComponent();
         const controller = createMockController(makeMockListModel());
         injectController(comp, controller);
         comp.onSort();
         expect(controller.sortSelections).toHaveBeenCalled();
      });
   });

   describe("Group 7 �?processExpandTreeNodesCommand", () => {
      it("should expand all nodes when scriptChanged and expand are true", async () => {
         const { comp } = await renderComponent();
         const treeModel = makeMockTreeModel();
         treeModel.root = makeCompositeRoot();
         comp.model = treeModel;
         const controller = {
            ...createMockController(treeModel),
            expandAllNodes: vi.fn(),
         };
         injectController(comp, controller);

         comp.processExpandTreeNodesCommand(makeExpandTreeNodesCommand({ scriptChanged: true, expand: true }));
         expect(controller.expandAllNodes).toHaveBeenCalled();
      });

      it("should not expand when scriptChanged is false", async () => {
         const { comp } = await renderComponent();
         const treeModel = makeMockTreeModel();
         comp.model = treeModel;
         const controller = {
            ...createMockController(treeModel),
            expandAllNodes: vi.fn(),
         };
         injectController(comp, controller);

         comp.processExpandTreeNodesCommand(makeExpandTreeNodesCommand({ scriptChanged: false, expand: true }));
         expect(controller.expandAllNodes).not.toHaveBeenCalled();
      });
   });
});

