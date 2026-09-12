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
 * VSObjectContainer — Pass 1: Interaction
 *
 * Method coverage:
 *   Group 1   select — viewer+VSViewsheet guard; dataTip guard; normal emission
 *   Group 2   showContextMenu — touch device guard; context menu emission
 *   Group 3   submitClicked — emits onSubmit
 *   Group 4   onMaxModeChange — delegates to maxModeChange.emit
 *   Group 5   showToolbar — sets forceShowMiniToolbar
 *   Group 6   ngOnDestroy — unsubscribes subscriptions
 *   Group 7   annotationMouseSelect — ctrl-click multi-select; right-click context menu
 *   Group 8   removeAnnotationFromOverlay — emits removeAnnotations, filters name
 */

import {
   makeComponent,
   makeVSObject,
   makeVsInfo,
} from "./vs-object-container.component.test-helpers";

// ---------------------------------------------------------------------------
// Group 1 — select
// ---------------------------------------------------------------------------

describe("Group 1 — select: guards and emission", () => {
   it("should emit onSelectedAssemblyChanged with index, actions, and event", () => {
      const obj = makeVSObject({ objectType: "VSChart" });
      const vsInfo = makeVsInfo([obj]);
      const actions = [{ showingActions: [] }];
      const { comp } = makeComponent({ vsInfo, vsObjectActions: actions });
      const emitSpy = vi.spyOn(comp.onSelectedAssemblyChanged, "emit");
      const event = { type: "click" } as any;

      comp.select(0, event);

      expect(emitSpy).toHaveBeenCalledWith([0, actions[0], event]);
   });

   it("should NOT emit when viewer=true and object is VSViewsheet", () => {
      const obj = makeVSObject({ objectType: "VSViewsheet" });
      const vsInfo = makeVsInfo([obj]);
      const context = { viewer: true, preview: false, binding: false };
      const { comp } = makeComponent({ vsInfo, context });
      const emitSpy = vi.spyOn(comp.onSelectedAssemblyChanged, "emit");

      comp.select(0, {} as any);

      expect(emitSpy).not.toHaveBeenCalled();
   });

   it("should NOT emit when object is a dataTip and dataTipName is set on non-mobile", () => {
      const obj = makeVSObject({ absoluteName: "Tip1" });
      const vsInfo = makeVsInfo([obj]);
      const dataTipSvc = {
         showHideDataTip: { subscribe: vi.fn() } as any,
         isDataTipVisible: vi.fn().mockReturnValue(false),
         isDataTip: vi.fn().mockReturnValue(true), // this assembly is a data tip
         dataTipName: "Tip1",
         isDataTipSource: vi.fn(),
         isCurrentDataTip: vi.fn(),
         hasDataTipShowing: vi.fn().mockReturnValue(false),
         getVSObjectId: vi.fn((n: string) => n),
      };
      const { comp } = makeComponent({ vsInfo, dataTipSvc: dataTipSvc as any });
      comp.mobile = false;
      const emitSpy = vi.spyOn(comp.onSelectedAssemblyChanged, "emit");

      comp.select(0, {} as any);

      expect(emitSpy).not.toHaveBeenCalled();
   });
});

// ---------------------------------------------------------------------------
// Group 2 — showContextMenu
// ---------------------------------------------------------------------------

describe("Group 2 — showContextMenu: touch device guard", () => {
   it("should emit openContextMenu when touchDevice=false", () => {
      const { comp } = makeComponent();
      comp.touchDevice = false;
      const actions = { toolbarActions: [] } as any;
      const emitSpy = vi.spyOn(comp.openContextMenu, "emit");
      const event = { stopPropagation: vi.fn() } as any;

      comp.showContextMenu(event, actions);

      expect(emitSpy).toHaveBeenCalledWith({ actions, event });
   });

   it("should NOT emit openContextMenu when touchDevice=true", () => {
      const { comp } = makeComponent();
      comp.touchDevice = true;
      const emitSpy = vi.spyOn(comp.openContextMenu, "emit");
      const event = { stopPropagation: vi.fn() } as any;

      comp.showContextMenu(event, {} as any);

      expect(emitSpy).not.toHaveBeenCalled();
   });

   it("should stop event propagation when not a touch device", () => {
      const { comp } = makeComponent();
      comp.touchDevice = false;
      const event = { stopPropagation: vi.fn() } as any;

      comp.showContextMenu(event, {} as any);

      expect(event.stopPropagation).toHaveBeenCalled();
   });
});

// ---------------------------------------------------------------------------
// Group 3 — submitClicked
// ---------------------------------------------------------------------------

describe("Group 3 — submitClicked: emits onSubmit", () => {
   it("should emit onSubmit when submitClicked is called", () => {
      const { comp } = makeComponent();
      const emitSpy = vi.spyOn(comp.onSubmit, "emit");

      comp.submitClicked("Btn1");

      expect(emitSpy).toHaveBeenCalledOnce();
   });
});

// ---------------------------------------------------------------------------
// Group 4 — onMaxModeChange
// ---------------------------------------------------------------------------

describe("Group 4 — onMaxModeChange: delegates to maxModeChange.emit", () => {
   it("should emit maxModeChange with the event payload", () => {
      const { comp } = makeComponent();
      const emitSpy = vi.spyOn(comp.maxModeChange, "emit");
      const payload = { assembly: "Chart1", maxMode: true };

      comp.onMaxModeChange(payload);

      expect(emitSpy).toHaveBeenCalledWith(payload);
   });
});

// ---------------------------------------------------------------------------
// Group 5 — showToolbar
// ---------------------------------------------------------------------------

describe("Group 5 — showToolbar: sets forceShowMiniToolbar", () => {
   it("should set forceShowMiniToolbar=true", () => {
      const { comp } = makeComponent();
      comp.showToolbar(true);
      expect(comp.forceShowMiniToolbar).toBe(true);
   });

   it("should set forceShowMiniToolbar=false", () => {
      const { comp } = makeComponent();
      comp.forceShowMiniToolbar = true;
      comp.showToolbar(false);
      expect(comp.forceShowMiniToolbar).toBe(false);
   });
});

// ---------------------------------------------------------------------------
// Group 6 — ngOnDestroy
// ---------------------------------------------------------------------------

describe("Group 6 — ngOnDestroy: unsubscribes from all subscriptions", () => {
   it("should not throw when ngOnDestroy is called", () => {
      const { comp } = makeComponent();
      expect(() => comp.ngOnDestroy()).not.toThrow();
   });

   it("should call focusSub.unsubscribe if keyNavigation was set", () => {
      const { comp } = makeComponent();
      const { Subject } = require("rxjs");
      const nav$ = new Subject();
      comp.keyNavigation = nav$.asObservable();

      const unsubSpy = vi.spyOn((comp as any).focusSub, "unsubscribe");
      comp.ngOnDestroy();

      expect(unsubSpy).toHaveBeenCalled();
   });
});

// ---------------------------------------------------------------------------
// Group 7 — annotationMouseSelect
// ---------------------------------------------------------------------------

describe("Group 7 — annotationMouseSelect: ctrl-click adds to selection; right-click opens context menu", () => {
   it("should add annotation to selectedAnnotations on ctrl-click", () => {
      const { comp } = makeComponent();
      const vsObj = makeVSObject({ selectedAnnotations: ["ann0"] });
      const ann = { absoluteName: "ann1" } as any;
      const event = { ctrlKey: true, button: 0 } as any;

      comp.annotationMouseSelect([ann, event], vsObj);

      expect(vsObj.selectedAnnotations).toContain("ann1");
      expect(vsObj.selectedAnnotations).toContain("ann0"); // preserved
   });

   it("should replace selectedAnnotations on plain click (no ctrl)", () => {
      const { comp } = makeComponent();
      const vsObj = makeVSObject({ selectedAnnotations: ["ann0"] });
      const ann = { absoluteName: "ann1" } as any;
      const event = { ctrlKey: false, button: 0 } as any;

      comp.annotationMouseSelect([ann, event], vsObj);

      expect(vsObj.selectedAnnotations).toEqual(["ann1"]);
   });

   it("should emit openContextMenu on right-click (button=2)", () => {
      const vsObj = makeVSObject({ absoluteName: "Chart1" });
      const vsInfo = makeVsInfo([vsObj]);
      const actions = [{ toolbarActions: [], showingActions: [] }];
      const { comp } = makeComponent({ vsInfo, vsObjectActions: actions });
      comp.touchDevice = false;
      const emitSpy = vi.spyOn(comp.openContextMenu, "emit");
      const ann = { absoluteName: "ann1" } as any;
      const event = { ctrlKey: false, button: 2, stopPropagation: vi.fn() } as any;

      comp.annotationMouseSelect([ann, event], vsObj);

      expect(emitSpy).toHaveBeenCalled();
   });
});

// ---------------------------------------------------------------------------
// Group 8 — removeAnnotationFromOverlay
// ---------------------------------------------------------------------------

describe("Group 8 — removeAnnotationFromOverlay: emits and filters annotation name", () => {
   it("should emit removeAnnotations", () => {
      const { comp } = makeComponent();
      const vsObj = makeVSObject({ selectedAnnotations: ["ann1", "ann2"] });
      const ann = { absoluteName: "ann1" } as any;
      const emitSpy = vi.spyOn(comp.removeAnnotations, "emit");

      comp.removeAnnotationFromOverlay(ann, vsObj);

      expect(emitSpy).toHaveBeenCalledOnce();
   });

   it("should remove the named annotation from selectedAnnotations", () => {
      const { comp } = makeComponent();
      const vsObj = makeVSObject({ selectedAnnotations: ["ann1", "ann2"] });

      comp.removeAnnotationFromOverlay({ absoluteName: "ann1" } as any, vsObj);

      expect(vsObj.selectedAnnotations).toEqual(["ann2"]);
   });
});
