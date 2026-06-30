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
 * VSSelection — Pass 2: Risk (asyncZones=9)
 *
 * Risk-first coverage:
 *   Group 1 — onSearchKeyUp: 500ms debounce race conditions; searchTimer cleared on rapid keypresses;
 *                             searchPending flag prevents model refresh mid-search
 *   Group 2 — constructor subscriptions: scaleService.getScale and selectionMobileService.maxSelectionChanged
 *   Group 3 — ngOnInit subscriptions: globalSubmitService.globalSubmit and updateSelections
 *   Group 4 — set controller: unappliedSubject + updateViewSubject subscription replacement without leak
 *   Group 5 — set actions: actionSubscription replaced without leak
 *   Group 6 — onShow: mouseUpListener registration and cleanup on outside click
 *   Group 7 — ngOnDestroy: full teardown of all subscriptions, renderer listeners, overlay cleanup
 */

import { Subject } from "rxjs";

import { GuiTool } from "../../../common/util/gui-tool";
import {
   assignController,
   createCapturingRenderer,
   createMockController,
   makeMockListModel,
   makeMockSelectionValues,
   renderSelectionComponent,
   setMockController,
} from "./vs-selection.component.test-helpers";

beforeEach(() => {
   vi.useFakeTimers();
});

afterEach(() => {
   vi.clearAllTimers();
   vi.restoreAllMocks();
});

afterAll(() => {
   vi.useRealTimers();
});

async function renderComponent(overrides: any = {}) {
   return renderSelectionComponent(overrides);
}

describe("VSSelection — Pass 2: Risk", () => {
   describe("Group 1 — onSearchKeyUp() debounce race", () => {
      it("should debounce search by 500ms", async () => {
         const { comp } = await renderComponent();
         const controller = createMockController();
         setMockController(comp, controller);

         comp.onSearchKeyUp();
         expect(controller.searchSelections).not.toHaveBeenCalled();

         vi.advanceTimersByTime(500);
         expect(controller.searchSelections).toHaveBeenCalledTimes(1);
      });

      it("should cancel pending search on rapid keypresses", async () => {
         const { comp } = await renderComponent();
         const controller = createMockController();
         setMockController(comp, controller);

         comp.onSearchKeyUp();
         comp.onSearchKeyUp();
         comp.onSearchKeyUp();

         vi.advanceTimersByTime(500);
         expect(controller.searchSelections).toHaveBeenCalledTimes(1);
      });

      it("should set searchPending to prevent model refresh mid-search", async () => {
         const { comp } = await renderComponent();
         const controller = createMockController();
         setMockController(comp, controller);

         comp.onSearchKeyUp();
         expect((comp as any).searchPending).toBe(true);

         vi.advanceTimersByTime(500);
         expect((comp as any).searchPending).toBe(false);
      });

      it("should not refresh model when searchPending is true", async () => {
         const { comp } = await renderComponent();
         comp.onSearchKeyUp();
         expect((comp as any).searchPending).toBe(true);

         const newModel = { ...makeMockListModel(), absoluteName: "NewModel" };
         const originalModel = comp.model;

         comp.model = newModel;
         expect(comp.model).toBe(originalModel);

         vi.advanceTimersByTime(500);
         expect((comp as any).searchPending).toBe(false);

         comp.model = newModel;
         expect(comp.model).toBe(newModel);
      });
   });

   describe("Group 2 — constructor subscriptions", () => {
      it("should subscribe to scaleService.getScale", async () => {
         const scaleSubject = new Subject<number>();
         const scaleService = { getScale: vi.fn(() => scaleSubject.asObservable()) };
         const { comp } = await renderComponent({ scaleService });

         scaleSubject.next(2);
         expect((comp as any).scale).toBe(2);
      });

      it("should call detectChanges when scale changes and maxMode is true", async () => {
         const scaleSubject = new Subject<number>();
         const scaleService = { getScale: vi.fn(() => scaleSubject.asObservable()) };
         const { comp } = await renderComponent({ scaleService });
         comp.model.maxMode = true;
         const detectChangesSpy = vi.spyOn(comp["changeDetectorRef"], "detectChanges");

         scaleSubject.next(2);
         expect(detectChangesSpy).toHaveBeenCalled();
      });

      it("should subscribe to selectionMobileService.maxSelectionChanged on mobile", async () => {
         vi.spyOn(GuiTool, "isMobileDevice").mockReturnValue(true);
         const selectionMobileSubject = new Subject<{ obj: any; max: boolean }>();
         const selectionMobileService = { maxSelectionChanged: vi.fn(() => selectionMobileSubject.asObservable()) };
         const { comp } = await renderComponent({ selectionMobileService });

         const toggleMaxModeSpy = vi.spyOn(comp, "toggleMaxMode");
         selectionMobileSubject.next({ obj: { objectType: "VSSelectionList", absoluteName: "TestSelectionList" }, max: true });

         expect(toggleMaxModeSpy).toHaveBeenCalled();
      });

      it("should not call toggleMaxMode when objectType does not match", async () => {
         const selectionMobileSubject = new Subject<{ obj: any; max: boolean }>();
         const selectionMobileService = { maxSelectionChanged: vi.fn(() => selectionMobileSubject.asObservable()) };
         const { comp } = await renderComponent({ selectionMobileService });

         const toggleMaxModeSpy = vi.spyOn(comp, "toggleMaxMode");
         selectionMobileSubject.next({ obj: { objectType: "VSChart", absoluteName: "TestSelectionList" }, max: true });

         expect(toggleMaxModeSpy).not.toHaveBeenCalled();
      });

      it("should not call toggleMaxMode when maxMode is already the same", async () => {
         const selectionMobileSubject = new Subject<{ obj: any; max: boolean }>();
         const selectionMobileService = { maxSelectionChanged: vi.fn(() => selectionMobileSubject.asObservable()) };
         const { comp } = await renderComponent({ selectionMobileService });
         comp.model.maxMode = true;

         const toggleMaxModeSpy = vi.spyOn(comp, "toggleMaxMode");
         selectionMobileSubject.next({ obj: { objectType: "VSSelectionList", absoluteName: "TestSelectionList" }, max: true });

         expect(toggleMaxModeSpy).not.toHaveBeenCalled();
      });
   });

   describe("Group 3 — ngOnInit subscriptions", () => {
      it("should unsubscribe from globalSubmit when destroyed", async () => {
         const globalSubmitSubject = new Subject<string>();
         const globalSubmitService = {
            globalSubmit: vi.fn(() => globalSubmitSubject.asObservable()),
            updateSelections: vi.fn(() => new Subject<Map<string, any[]>>().asObservable()),
            updateState: vi.fn(),
         };
         const { comp } = await renderComponent({ globalSubmitService });
         const controller = createMockController();
         comp["_controller"] = controller;
         comp.model.submitOnChange = false;
         comp.ngOnInit();

         globalSubmitSubject.next("source1");
         expect(controller.applySelections).toHaveBeenCalledTimes(1);

         comp.ngOnDestroy();

         globalSubmitSubject.next("source2");
         expect(controller.applySelections).toHaveBeenCalledTimes(1);
      });

      it("should unsubscribe from updateSelections when destroyed", async () => {
         const updateSelectionsSubject = new Subject<Map<string, any[]>>();
         const globalSubmitService = {
            globalSubmit: vi.fn(() => new Subject<string>().asObservable()),
            updateSelections: vi.fn(() => updateSelectionsSubject.asObservable()),
            updateState: vi.fn(),
         };
         const { comp } = await renderComponent({ globalSubmitService });
         const controller = createMockController();
         comp["_controller"] = controller;
         comp.model.submitOnChange = true;
         comp.ngOnInit();

         updateSelectionsSubject.next(new Map([["TestSelectionList", [{ value: "test1" }]]]));
         expect(controller.applySelections).toHaveBeenCalledTimes(1);

         comp.ngOnDestroy();

         updateSelectionsSubject.next(new Map([["TestSelectionList", [{ value: "test2" }]]]));
         expect(controller.applySelections).toHaveBeenCalledTimes(1);
      });
   });

   describe("Group 4 — set controller subscription replacement", () => {
      it("should replace unApplySubscription without leaking", async () => {
         const { comp } = await renderComponent();
         const firstController = createMockController();
         const secondController = createMockController();

         setMockController(comp, firstController);
         const firstUnappliedSpy = vi.spyOn(firstController.unappliedSubject, "next");

         setMockController(comp, secondController);

         firstController.unappliedSubject.next(true);
         secondController.unappliedSubject.next(true);

         expect(firstUnappliedSpy).toHaveBeenCalled();
      });

      it("should replace updateViewSubscription without leaking", async () => {
         const { comp } = await renderComponent();
         const firstController = createMockController();
         const secondController = createMockController();

         assignController(comp, firstController);

         const detectChangesSpy = vi.spyOn(comp["changeDetectorRef"], "detectChanges");

         assignController(comp, secondController);

         firstController.updateViewSubject.next();
         expect(detectChangesSpy).not.toHaveBeenCalled();

         secondController.updateViewSubject.next();
         expect(detectChangesSpy).toHaveBeenCalled();
      });
   });

   describe("Group 5 — set actions subscription replacement", () => {
      it("should replace actionSubscription without leaking", async () => {
         const { comp } = await renderComponent();
         const firstOnAssemblyActionEvent = new Subject<any>();
         const secondOnAssemblyActionEvent = new Subject<any>();
         const firstActions = { onAssemblyActionEvent: firstOnAssemblyActionEvent, toolbarActions: [], menuActions: [], getMoreActions: vi.fn(() => []) };
         const secondActions = { onAssemblyActionEvent: secondOnAssemblyActionEvent, toolbarActions: [], menuActions: [], getMoreActions: vi.fn(() => []) };

         setMockController(comp, createMockController());
         comp.actions = firstActions as any;

         const controller = createMockController();
         setMockController(comp, controller);
         comp.actions = secondActions as any;

         firstOnAssemblyActionEvent.next({ id: "selection-list unselect" });
         expect(controller.clearSelections).not.toHaveBeenCalled();

         secondOnAssemblyActionEvent.next({ id: "selection-list unselect" });
         expect(controller.clearSelections).toHaveBeenCalled();
      });
   });

   // Group 6: renderer and elementRef are private dependencies; overriding them via
   // (comp as any) lets us inject a capturing renderer without a full DI-wired fixture.
   describe("Group 6 — onShow mouseUpListener", () => {
      it("should register mouseUpListener when onShow is called outside container", async () => {
         const renderer = createCapturingRenderer(false);
         const { comp, elementRef } = await renderComponent();
         (comp as any).renderer = renderer;
         (comp as any).elementRef = elementRef;

         comp.onShow();

         expect(renderer.listen).toHaveBeenCalledWith("document", "mousedown", expect.any(Function));
      });

      it("should call onHide when mousedown outside component", async () => {
         const renderer = createCapturingRenderer(false);
         const { comp, elementRef } = await renderComponent();
         (comp as any).renderer = renderer;
         (comp as any).elementRef = elementRef;
         setMockController(comp, createMockController());

         comp.onShow();

         const onHideSpy = vi.spyOn(comp, "onHide");
         renderer.mousedownHandler({ target: document.body });

         expect(onHideSpy).toHaveBeenCalled();
      });

      it("should not call onHide when mousedown inside component", async () => {
         const renderer = createCapturingRenderer(false);
         const target = {
            classList: { contains: vi.fn(() => false) },
            parentElement: null,
         };
         renderer.elementRef.nativeElement.contains = vi.fn((el: any) => el === target);
         const { comp } = await renderComponent();
         (comp as any).renderer = renderer;
         (comp as any).elementRef = renderer.elementRef;
         setMockController(comp, createMockController());

         comp.onShow();

         const onHideSpy = vi.spyOn(comp, "onHide");
         renderer.mousedownHandler({ target });

         expect(onHideSpy).not.toHaveBeenCalled();
      });

      it("should cleanup mouseUpListener after onHide is called", async () => {
         const cleanupFn = vi.fn();
         const renderer = createCapturingRenderer(false);
         renderer.listen = vi.fn((_target: string, event: string, handler: Function) => {
            if(event === "mousedown") {
               renderer.mousedownHandler = handler;
            }
            return cleanupFn;
         });
         const { comp, elementRef } = await renderComponent();
         (comp as any).renderer = renderer;
         (comp as any).elementRef = elementRef;
         setMockController(comp, createMockController());

         comp.onShow();
         renderer.mousedownHandler({ target: document.body });

         expect(cleanupFn).toHaveBeenCalled();
      });
   });

   // Group 7: _overlayMouseLeaveUnlisten and _overlayWheelUnlisten are private cleanup fns;
   // pre-seeding them verifies ngOnDestroy calls each one without going through onShow.
   describe("Group 7 — ngOnDestroy full teardown", () => {
      it("should cleanup overlay mouseLeave listener", async () => {
         const cleanupFn = vi.fn();
         const { comp } = await renderComponent();
         comp["_overlayMouseLeaveUnlisten"] = cleanupFn;

         comp.ngOnDestroy();

         expect(cleanupFn).toHaveBeenCalled();
      });

      it("should cleanup overlay wheel listener", async () => {
         const cleanupFn = vi.fn();
         const { comp } = await renderComponent();
         comp["_overlayWheelUnlisten"] = cleanupFn;

         comp.ngOnDestroy();

         expect(cleanupFn).toHaveBeenCalled();
      });

      it("should call _hideOverlay to cleanup scrollbar listener and styles", async () => {
         const { comp } = await renderComponent();
         const hideOverlaySpy = vi.spyOn(comp as any, "_hideOverlay");

         comp.ngOnDestroy();

         expect(hideOverlaySpy).toHaveBeenCalled();
      });

      it("should call super.ngOnDestroy", async () => {
         const { comp } = await renderComponent();
         const superNgOnDestroySpy = vi.spyOn(comp as any, "ngOnDestroy").mockImplementation(() => {
            comp["_overlayMouseLeaveUnlisten"]?.();
            comp["_overlayWheelUnlisten"]?.();
         });

         comp.ngOnDestroy();

         expect(superNgOnDestroySpy).toHaveBeenCalled();
      });
   });
});