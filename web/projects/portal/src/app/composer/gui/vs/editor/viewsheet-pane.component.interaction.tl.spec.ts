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
 * VSPane — Pass 1: Interaction
 *
 * Risk-first coverage:
 *   Group 1  [Risk 3] — processSetViewsheetInfoCommand: updates vs.label, vs.layouts,
 *                        vs.baseEntry, viewsheetBackground, vs.statusText, hasScript,
 *                        hideNotifications; sends bindingtree refresh event.
 *   Group 2  [Risk 3] — @Output emitters: copyAssembly/cutAssembly/removeAssembly/
 *                        bringToFront/Forward/sendToBack/Backward emit correct outputs.
 *   Group 3  [Risk 3] — processAddVSObjectCommand: new object pushed; existing name →
 *                        replaceObject called; z-indexes updated.
 *   Group 4  [Risk 3] — processCloseSheetCommand: emits onSheetClose with vs.
 *   Group 5  [Risk 3] — processSaveSheetCommand: sets vs.newSheet=false, updates
 *                        vs.savePoint and vs.id, shows notifications.success,
 *                        emits onSaveViewsheet — gettingStarted=false path.
 *   Group 6  [Risk 3] — processUpdateUndoStateCommand: updates vs.points, vs.current,
 *                        vs.savePoint, emits onSheetChange.
 *   Group 7  [Risk 3] — processSetRuntimeIdCommand: sets vs.runtimeId,
 *                        viewsheetClient.runtimeId, dialogService.setSheetId.
 *   Group 8  [Risk 2] — processVSDependencyChangedCommand: emits onDependencyChanged.
 *   Group 9  [Risk 2] — processReopenSheetCommand: emits onSheetReload when id matches;
 *                        does NOT emit when ids differ.
 *   Group 10 [Risk 2] — processRenameVSObjectCommand: renames absoluteName in vsObjects;
 *                        updates variableNames.
 *   Group 11 [Risk 2] — processChangeCurrentLayoutCommand: null layout clears
 *                        vs.currentLayout; non-null sets vs.currentLayout to new VSLayoutModel.
 *   Group 12 [Risk 2] — processSetGrayedOutFieldsCommand: emits onGrayedOutFields.
 *   Group 13 [Risk 2] — processUpdateLayoutUndoStateCommand: when vs.currentLayout present,
 *                        updates vs.layoutPoint and emits onSheetChange.
 *   Group 14 [Risk 2] — onKeydown: Ctrl+A selects all; Esc clears focused assemblies.
 *   Group 15 [Risk 2] — zoom: increments/decrements vs.scale; boundary guards.
 *   Group 16 [Risk 2] — isInZone: returns false for ClearLoading/ShowLoadingMask; true otherwise.
 *   Group 17 [Risk 1] — getAssemblyName: returns null.
 *   Group 18 [Risk 1] — layoutToolbarVisible: true when layouts.length > 1.
 *   Group 19 [Risk 1] — trackByFn: returns object.absoluteName.
 *   Group 20 [Risk 1] — layoutName: returns currentLayout.name or "_#(js:Master)".
 *   Group 21 [Risk 1] — isFilterInMaxModeView: true when maxModeAssembly set + adhocFilter.
 */

import { makeMocks, renderComponent } from "./viewsheet-pane.component.test-helpers";
import { VSLayoutModel } from "../../../data/vs/vs-layout-model";
import { UIContextService } from "../../../../common/services/ui-context.service";

afterEach(() => vi.restoreAllMocks());

// ---------------------------------------------------------------------------
// Group 1: processSetViewsheetInfoCommand [Risk 3]
// ---------------------------------------------------------------------------

describe("VSPane — processSetViewsheetInfoCommand", () => {

   // Regression-sensitive: vs.label displayed in tab title; layouts drive the toolbar;
   // wrong background breaks guideLineColor computation.
   it("should update vs.label from assemblyInfo.name", async () => {
      const mocks = makeMocks();
      const { comp } = await renderComponent(mocks);
      comp.vs.label = "Old";

      mocks.dispatchCommand("SetViewsheetInfoCommand", {
         assemblyInfo: { name: "NewLabel" },
         layouts: [],
         baseEntry: null,
         info: { viewsheetBackground: "#ffffff", statusText: "Ready", snapGrid: 20,
                 templateWidth: 0, templateHeight: 0, templateEnabled: false, metadata: false,
                 messageLevels: null },
         linkUri: null,
         hasScript: false,
         hideNotifications: false,
         annotation: false,
         annotated: false,
         formTable: false,
      });

      expect(comp.vs.label).toBe("NewLabel");
   });

   it("should update vs.layouts from command.layouts", async () => {
      const mocks = makeMocks();
      const { comp } = await renderComponent(mocks);

      mocks.dispatchCommand("SetViewsheetInfoCommand", {
         assemblyInfo: { name: "VS" },
         layouts: ["Layout1", "Layout2"],
         baseEntry: null,
         info: { viewsheetBackground: "#FFFFFF", statusText: null, snapGrid: 20,
                 templateWidth: 0, templateHeight: 0, templateEnabled: false, metadata: false,
                 messageLevels: null },
         linkUri: null,
         hasScript: true,
         hideNotifications: false,
         annotation: false,
         annotated: false,
         formTable: false,
      });

      expect(comp.vs.layouts).toEqual(["Layout1", "Layout2"]);
   });

   it("should set viewsheetBackground from command.info", async () => {
      const mocks = makeMocks();
      const { comp } = await renderComponent(mocks);

      mocks.dispatchCommand("SetViewsheetInfoCommand", {
         assemblyInfo: { name: "VS" },
         layouts: [],
         baseEntry: null,
         info: { viewsheetBackground: "#aabbcc", statusText: null, snapGrid: 20,
                 templateWidth: 0, templateHeight: 0, templateEnabled: false, metadata: false,
                 messageLevels: null },
         linkUri: null,
         hasScript: false,
         hideNotifications: false,
         annotation: false,
         annotated: false,
         formTable: false,
      });

      expect((comp as any).viewsheetBackground).toBe("#aabbcc");
   });

   it("should set hasScript from command.hasScript", async () => {
      const mocks = makeMocks();
      const { comp } = await renderComponent(mocks);
      (comp as any).hasScript = false;

      mocks.dispatchCommand("SetViewsheetInfoCommand", {
         assemblyInfo: { name: "VS" },
         layouts: [],
         baseEntry: null,
         info: { viewsheetBackground: "#FFFFFF", statusText: null, snapGrid: 20,
                 templateWidth: 0, templateHeight: 0, templateEnabled: false, metadata: false,
                 messageLevels: null },
         linkUri: null,
         hasScript: true,
         hideNotifications: false,
         annotation: false,
         annotated: false,
         formTable: false,
      });

      expect((comp as any).hasScript).toBe(true);
   });

   it("should set hideNotifications from command.hideNotifications", async () => {
      const mocks = makeMocks();
      const { comp } = await renderComponent(mocks);

      mocks.dispatchCommand("SetViewsheetInfoCommand", {
         assemblyInfo: { name: "VS" },
         layouts: [],
         baseEntry: null,
         info: { viewsheetBackground: "#FFFFFF", statusText: null, snapGrid: 20,
                 templateWidth: 0, templateHeight: 0, templateEnabled: false, metadata: false,
                 messageLevels: null },
         linkUri: null,
         hasScript: false,
         hideNotifications: true,
         annotation: false,
         annotated: false,
         formTable: false,
      });

      expect((comp as any).hideNotifications).toBe(true);
   });

   it("should send bindingtree refresh event via sendEvent", async () => {
      const mocks = makeMocks();
      const { comp } = await renderComponent(mocks);

      mocks.dispatchCommand("SetViewsheetInfoCommand", {
         assemblyInfo: { name: "VS" },
         layouts: [],
         baseEntry: null,
         info: { viewsheetBackground: "#FFFFFF", statusText: "ok", snapGrid: 20,
                 templateWidth: 0, templateHeight: 0, templateEnabled: false, metadata: false,
                 messageLevels: null },
         linkUri: null,
         hasScript: false,
         hideNotifications: false,
         annotation: false,
         annotated: false,
         formTable: false,
      });

      // vs.socketConnection is set to viewsheetClient in ngOnInit, so both point to vsClient mock
      expect(mocks.vsClient.sendEvent).toHaveBeenCalledWith(
         "/events/vs/bindingtree/gettreemodel",
         expect.anything(),
      );
   });
});

// ---------------------------------------------------------------------------
// Group 2: @Output emitters [Risk 3]
// ---------------------------------------------------------------------------

describe("VSPane — @Output emitters", () => {

   // Regression-sensitive: copyAssembly must call vs.selectAssembly before emitting so the
   // parent always sees the assembly selected when the onCopy event fires.
   it("copyAssembly should emit onCopy with model and call vs.selectAssembly", async () => {
      const mocks = makeMocks();
      const { comp } = await renderComponent(mocks);
      const selectSpy = vi.spyOn(comp.vs, "selectAssembly");
      const emitSpy = vi.fn();
      comp.onCopy.subscribe(emitSpy);
      const model: any = { absoluteName: "Chart1", objectType: "VSChart" };

      comp.copyAssembly(model);

      expect(selectSpy).toHaveBeenCalledWith(model);
      expect(emitSpy).toHaveBeenCalledWith(model);
   });

   it("cutAssembly should emit onCut with model and call vs.selectAssembly", async () => {
      const mocks = makeMocks();
      const { comp } = await renderComponent(mocks);
      const selectSpy = vi.spyOn(comp.vs, "selectAssembly");
      const emitSpy = vi.fn();
      comp.onCut.subscribe(emitSpy);
      const model: any = { absoluteName: "Chart1", objectType: "VSChart" };

      comp.cutAssembly(model);

      expect(selectSpy).toHaveBeenCalledWith(model);
      expect(emitSpy).toHaveBeenCalledWith(model);
   });

   it("removeAssembly should emit onRemove with model", async () => {
      const mocks = makeMocks();
      const { comp } = await renderComponent(mocks);
      const emitSpy = vi.fn();
      comp.onRemove.subscribe(emitSpy);
      const model: any = { absoluteName: "Table1", objectType: "VSTable" };

      comp.removeAssembly(model);

      expect(emitSpy).toHaveBeenCalledWith(model);
   });

   it("bringAssemblyToFront should emit onBringToFront", async () => {
      const { comp } = await renderComponent();
      const emitSpy = vi.fn();
      comp.onBringToFront.subscribe(emitSpy);
      const model: any = { absoluteName: "Chart1" };

      comp.bringAssemblyToFront(model);

      expect(emitSpy).toHaveBeenCalledWith(model);
   });

   it("bringAssemblyForward should emit onBringForward", async () => {
      const { comp } = await renderComponent();
      const emitSpy = vi.fn();
      comp.onBringForward.subscribe(emitSpy);
      const model: any = { absoluteName: "Chart1" };

      comp.bringAssemblyForward(model);

      expect(emitSpy).toHaveBeenCalledWith(model);
   });

   it("sendAssemblyToBack should emit onSendToBack", async () => {
      const { comp } = await renderComponent();
      const emitSpy = vi.fn();
      comp.onSendToBack.subscribe(emitSpy);
      const model: any = { absoluteName: "Chart1" };

      comp.sendAssemblyToBack(model);

      expect(emitSpy).toHaveBeenCalledWith(model);
   });

   it("sendAssemblyBackward should emit onSendBackward", async () => {
      const { comp } = await renderComponent();
      const emitSpy = vi.fn();
      comp.onSendBackward.subscribe(emitSpy);
      const model: any = { absoluteName: "Chart1" };

      comp.sendAssemblyBackward(model);

      expect(emitSpy).toHaveBeenCalledWith(model);
   });
});

// ---------------------------------------------------------------------------
// Group 3: processAddVSObjectCommand [Risk 3]
// ---------------------------------------------------------------------------

describe("VSPane — processAddVSObjectCommand", () => {

   // Regression-sensitive: new objects must be pushed and z-indexes updated immediately;
   // if name already exists the array entry must be replaced in-place, not doubled.
   it("should push a new object to vs.vsObjects when name not present", async () => {
      const mocks = makeMocks();
      const { comp } = await renderComponent(mocks);
      comp.vs.vsObjects = [];
      const model: any = {
         absoluteName: "Chart1", objectType: "VSChart",
         objectFormat: { zIndex: 1, left: 0, top: 0, width: 100, height: 100 },
      };

      mocks.dispatchCommand("AddVSObjectCommand", { name: "Chart1", model, parent: null });

      expect(comp.vs.vsObjects).toHaveLength(1);
      expect(comp.vs.vsObjects[0].absoluteName).toBe("Chart1");
   });

   it("should replace existing object by absoluteName when name already present", async () => {
      const mocks = makeMocks();
      const { comp } = await renderComponent(mocks);
      const oldModel: any = {
         absoluteName: "Chart1", objectType: "VSChart",
         objectFormat: { zIndex: 1, left: 0, top: 0, width: 100, height: 100 },
      };
      comp.vs.vsObjects = [oldModel];
      const newModel: any = {
         absoluteName: "Chart1", objectType: "VSChart",
         objectFormat: { zIndex: 2, left: 10, top: 10, width: 200, height: 200 },
      };

      mocks.dispatchCommand("AddVSObjectCommand", { name: "Chart1", model: newModel, parent: null });

      expect(comp.vs.vsObjects).toHaveLength(1);
   });

   it("should call composerObjectService.updateLayerMovement for each object after add", async () => {
      const mocks = makeMocks();
      const { comp } = await renderComponent(mocks);
      comp.vs.vsObjects = [];
      const model: any = {
         absoluteName: "Chart1", objectType: "VSChart",
         objectFormat: { zIndex: 1, left: 0, top: 0, width: 100, height: 100 },
      };

      mocks.dispatchCommand("AddVSObjectCommand", { name: "Chart1", model, parent: null });

      expect(mocks.composerObjectService.updateLayerMovement).toHaveBeenCalled();
   });
});

// ---------------------------------------------------------------------------
// Group 4: processCloseSheetCommand [Risk 3]
// ---------------------------------------------------------------------------

describe("VSPane — processCloseSheetCommand", () => {

   // Regression-sensitive: onSheetClose must emit so the parent removes the tab;
   // missing emit leaves an orphaned non-functional tab.
   it("should emit onSheetClose with the current vs", async () => {
      const mocks = makeMocks();
      const { comp } = await renderComponent(mocks);
      const emitSpy = vi.fn();
      comp.onSheetClose.subscribe(emitSpy);

      mocks.dispatchCommand("CloseSheetCommand", {});

      expect(emitSpy).toHaveBeenCalledWith(comp.vs);
   });
});

// ---------------------------------------------------------------------------
// Group 5: processSaveSheetCommand [Risk 3]
// ---------------------------------------------------------------------------

describe("VSPane — processSaveSheetCommand", () => {

   // Regression-sensitive: vs.newSheet must be cleared after first save so subsequent
   // saves use the update code path.
   it("should set vs.newSheet=false on save", async () => {
      const mocks = makeMocks();
      const { comp } = await renderComponent(mocks);
      comp.vs.newSheet = true;

      mocks.dispatchCommand("SaveSheetCommand", { savePoint: 5, id: "saved-vs" });

      expect(comp.vs.newSheet).toBe(false);
   });

   it("should update vs.id and vs.savePoint from command", async () => {
      const mocks = makeMocks();
      const { comp } = await renderComponent(mocks);

      mocks.dispatchCommand("SaveSheetCommand", { savePoint: 7, id: "new-vs-id" });

      expect(comp.vs.savePoint).toBe(7);
      expect(comp.vs.id).toBe("new-vs-id");
   });

   it("should show success notification", async () => {
      const mocks = makeMocks();
      const { comp } = await renderComponent(mocks);

      mocks.dispatchCommand("SaveSheetCommand", { savePoint: 1, id: "vs" });

      expect(mocks.notifications.success).toHaveBeenCalledWith(
         "_#(js:common.viewsheet.saveSuccess)",
      );
   });

   it("should NOT emit onOpenVSOnPortal when vs.gettingStarted=false", async () => {
      const mocks = makeMocks();
      const { comp } = await renderComponent(mocks);
      comp.vs.gettingStarted = false;
      const portalSpy = vi.fn();
      comp.onOpenVSOnPortal.subscribe(portalSpy);

      mocks.dispatchCommand("SaveSheetCommand", { savePoint: 1, id: "vs" });

      expect(portalSpy).not.toHaveBeenCalled();
   });

   it("should emit onOpenVSOnPortal with vs.id when vs.gettingStarted=true", async () => {
      const mocks = makeMocks();
      const { comp } = await renderComponent(mocks);
      comp.vs.gettingStarted = true;
      const portalSpy = vi.fn();
      comp.onOpenVSOnPortal.subscribe(portalSpy);

      mocks.dispatchCommand("SaveSheetCommand", { savePoint: 1, id: "portal-vs-id" });

      expect(portalSpy).toHaveBeenCalledWith("portal-vs-id");
   });
});

// ---------------------------------------------------------------------------
// Group 6: processUpdateUndoStateCommand [Risk 3]
// ---------------------------------------------------------------------------

describe("VSPane — processUpdateUndoStateCommand", () => {

   it("should update vs.points, vs.current, vs.savePoint and emit onSheetChange", async () => {
      const mocks = makeMocks();
      const { comp } = await renderComponent(mocks);
      const changeSpy = vi.fn();
      comp.onSheetChange.subscribe(changeSpy);

      mocks.dispatchCommand("UpdateUndoStateCommand", {
         points: 10,
         current: 3,
         savePoint: 2,
      });

      expect(comp.vs.points).toBe(10);
      expect(comp.vs.current).toBe(3);
      expect(comp.vs.savePoint).toBe(2);
      expect(changeSpy).toHaveBeenCalledWith(comp.vs);
   });
});

// ---------------------------------------------------------------------------
// Group 7: processSetRuntimeIdCommand [Risk 3]
// ---------------------------------------------------------------------------

describe("VSPane — processSetRuntimeIdCommand", () => {

   // Regression-sensitive: vs.runtimeId drives HTTP routing; viewsheetClient.runtimeId
   // must also be set so heartbeats use the correct id; dialogService.setSheetId ties
   // dialogs to this sheet.
   it("should set vs.runtimeId, viewsheetClient.runtimeId and call dialogService.setSheetId", async () => {
      const mocks = makeMocks();
      const { comp } = await renderComponent(mocks);

      mocks.dispatchCommand("SetRuntimeIdCommand", {
         runtimeId: "rt-123",
         permissions: [],
      });

      expect(comp.vs.runtimeId).toBe("rt-123");
      expect(mocks.vsClient.runtimeId).toBe("rt-123");
      expect(mocks.dialogService.setSheetId).toHaveBeenCalledWith("rt-123");
   });
});

// ---------------------------------------------------------------------------
// Group 8: processVSDependencyChangedCommand [Risk 2]
// ---------------------------------------------------------------------------

describe("VSPane — processVSDependencyChangedCommand", () => {

   it("should emit onDependencyChanged with [vs, command.wizard]", async () => {
      const mocks = makeMocks();
      const { comp } = await renderComponent(mocks);
      const emitSpy = vi.fn();
      comp.onDependencyChanged.subscribe(emitSpy);

      mocks.dispatchCommand("VSDependencyChangedCommand", { wizard: true });

      expect(emitSpy).toHaveBeenCalledWith([comp.vs, true]);
   });
});

// ---------------------------------------------------------------------------
// Group 9: processReopenSheetCommand [Risk 2]
// ---------------------------------------------------------------------------

describe("VSPane — processReopenSheetCommand", () => {

   it("should emit onSheetReload when command.id matches vs.id", async () => {
      const mocks = makeMocks();
      const { comp } = await renderComponent(mocks);
      comp.vs.id = "1^1^__NULL^TestVS";
      const reloadSpy = vi.fn();
      comp.onSheetReload.subscribe(reloadSpy);

      mocks.dispatchCommand("ReopenSheetCommand", { id: "1^1^__NULL^TestVS" });

      expect(reloadSpy).toHaveBeenCalledWith(comp.vs);
   });

   it("should NOT emit onSheetReload when command.id differs from vs.id", async () => {
      const mocks = makeMocks();
      const { comp } = await renderComponent(mocks);
      comp.vs.id = "1^1^__NULL^TestVS";
      const reloadSpy = vi.fn();
      comp.onSheetReload.subscribe(reloadSpy);

      mocks.dispatchCommand("ReopenSheetCommand", { id: "different-id" });

      expect(reloadSpy).not.toHaveBeenCalled();
   });
});

// ---------------------------------------------------------------------------
// Group 10: processRenameVSObjectCommand [Risk 2]
// ---------------------------------------------------------------------------

describe("VSPane — processRenameVSObjectCommand", () => {

   it("should rename absoluteName in vs.vsObjects", async () => {
      const mocks = makeMocks();
      const { comp } = await renderComponent(mocks);
      const obj: any = {
         absoluteName: "OldChart",
         objectType: "VSChart",
         objectFormat: { zIndex: 1, left: 0, top: 0, width: 100, height: 100 },
      };
      comp.vs.vsObjects = [obj];

      mocks.dispatchCommand("RenameVSObjectCommand", {
         oldName: "OldChart",
         newName: "NewChart",
      });

      expect(comp.vs.vsObjects[0].absoluteName).toBe("NewChart");
   });

   it("should call uiContextService.objectRenamed with oldName", async () => {
      const mocks = makeMocks();
      const { comp, fixture } = await renderComponent(mocks);
      // uiContextService is accessed via the injector — get it through the comp's injector
      const uiContext = fixture.debugElement.injector.get(UIContextService);
      const obj: any = {
         absoluteName: "OldChart",
         objectType: "VSChart",
         objectFormat: { zIndex: 1, left: 0, top: 0, width: 100, height: 100 },
      };
      comp.vs.vsObjects = [obj];

      mocks.dispatchCommand("RenameVSObjectCommand", {
         oldName: "OldChart",
         newName: "NewChart",
      });

      expect((uiContext as any).objectRenamed).toHaveBeenCalledWith("OldChart");
   });
});

// ---------------------------------------------------------------------------
// Group 11: processChangeCurrentLayoutCommand [Risk 2]
// ---------------------------------------------------------------------------

describe("VSPane — processChangeCurrentLayoutCommand", () => {

   it("should clear vs.currentLayout when command.layout is null", async () => {
      const mocks = makeMocks();
      const { comp } = await renderComponent(mocks);
      (comp.vs as any).currentLayout = { name: "Layout1" } as any;

      mocks.dispatchCommand("ChangeCurrentLayoutCommand", { layout: null });

      expect(comp.vs.currentLayout).toBeNull();
   });

   it("should set vs.currentLayout to a new VSLayoutModel when command.layout is non-null", async () => {
      const mocks = makeMocks();
      const { comp } = await renderComponent(mocks);
      comp.vs.currentLayout = null;

      mocks.dispatchCommand("ChangeCurrentLayoutCommand", {
         layout: { name: "MyLayout", objects: [], focusedObjects: [], printLayout: false },
      });

      expect(comp.vs.currentLayout).not.toBeNull();
      expect(comp.vs.currentLayout).toBeInstanceOf(VSLayoutModel);
      expect(comp.vs.currentLayout.name).toBe("MyLayout");
   });

   it("should emit onSheetChange after layout change", async () => {
      const mocks = makeMocks();
      const { comp } = await renderComponent(mocks);
      const changeSpy = vi.fn();
      comp.onSheetChange.subscribe(changeSpy);

      mocks.dispatchCommand("ChangeCurrentLayoutCommand", { layout: null });

      expect(changeSpy).toHaveBeenCalledWith(comp.vs);
   });
});

// ---------------------------------------------------------------------------
// Group 12: processSetGrayedOutFieldsCommand [Risk 2]
// ---------------------------------------------------------------------------

describe("VSPane — processSetGrayedOutFieldsCommand", () => {

   it("should emit onGrayedOutFields with fields from command", async () => {
      const mocks = makeMocks();
      const { comp } = await renderComponent(mocks);
      const emitSpy = vi.fn();
      comp.onGrayedOutFields.subscribe(emitSpy);
      const fields = [{ name: "col1" }, { name: "col2" }] as any[];

      mocks.dispatchCommand("SetGrayedOutFieldsCommand", { fields });

      expect(emitSpy).toHaveBeenCalledWith(fields);
   });
});

// ---------------------------------------------------------------------------
// Group 13: processUpdateLayoutUndoStateCommand [Risk 2]
// ---------------------------------------------------------------------------

describe("VSPane — processUpdateLayoutUndoStateCommand", () => {

   it("should update vs.layoutPoint and emit onSheetChange when vs.currentLayout is set", async () => {
      const mocks = makeMocks();
      const { comp } = await renderComponent(mocks);
      comp.vs.currentLayout = { name: "L1", focusedObjects: [] } as any;
      const changeSpy = vi.fn();
      comp.onSheetChange.subscribe(changeSpy);

      comp.processUpdateLayoutUndoStateCommand({ layoutPoint: 3, layoutPoints: 5 } as any);

      expect(comp.vs.layoutPoint).toBe(3);
      expect(changeSpy).toHaveBeenCalledWith(comp.vs);
   });

   it("should NOT emit onSheetChange when vs.currentLayout is null", async () => {
      const mocks = makeMocks();
      const { comp } = await renderComponent(mocks);
      comp.vs.currentLayout = null;
      const changeSpy = vi.fn();
      comp.onSheetChange.subscribe(changeSpy);

      comp.processUpdateLayoutUndoStateCommand({ layoutPoint: 3, layoutPoints: 5 } as any);

      expect(changeSpy).not.toHaveBeenCalled();
   });
});

// ---------------------------------------------------------------------------
// Group 14: onKeydown [Risk 2]
// ---------------------------------------------------------------------------

describe("VSPane — onKeydown", () => {

   it("Ctrl+A should set vs.currentFocusedAssemblies to all vsObjects when no modal/layout", async () => {
      const { comp } = await renderComponent();
      const obj1: any = { absoluteName: "Chart1", objectType: "VSChart" };
      const obj2: any = { absoluteName: "Table1", objectType: "VSTable" };
      comp.vs.vsObjects = [obj1, obj2];
      comp.vs.currentLayout = null;

      const event = {
         keyCode: 65,
         ctrlKey: true,
         metaKey: false,
         target: document.body,
         preventDefault: vi.fn(),
         stopPropagation: vi.fn(),
      } as any;
      comp.onKeydown(event);

      expect(comp.vs.currentFocusedAssemblies).toHaveLength(2);
   });

   it("Esc should clear focused assemblies", async () => {
      const { comp } = await renderComponent();
      const obj1: any = { absoluteName: "Chart1" };
      comp.vs.currentFocusedAssemblies = [obj1];
      comp.vs.currentLayout = null;

      const event = {
         keyCode: 27,
         ctrlKey: false,
         metaKey: false,
         target: document.body,
         preventDefault: vi.fn(),
         stopPropagation: vi.fn(),
      } as any;
      comp.onKeydown(event);

      expect(comp.vs.currentFocusedAssemblies).toHaveLength(0);
   });
});

// ---------------------------------------------------------------------------
// Group 15: zoom [Risk 2]
// ---------------------------------------------------------------------------

describe("VSPane — zoom", () => {

   it("zoom(false) should increment vs.scale by 0.2", async () => {
      const { comp } = await renderComponent();
      comp.vs.scale = 1.0;

      comp.zoom(false);

      expect(Number(comp.vs.scale.toFixed(2))).toBe(1.2);
   });

   it("zoom(true) should decrement vs.scale by 0.2", async () => {
      const { comp } = await renderComponent();
      comp.vs.scale = 1.0;

      comp.zoom(true);

      expect(Number(comp.vs.scale.toFixed(2))).toBe(0.8);
   });

   it("zoom(false) should do nothing when already at max (2.0)", async () => {
      const { comp } = await renderComponent();
      comp.vs.scale = 2.0;

      comp.zoom(false);

      expect(Number(comp.vs.scale.toFixed(2))).toBe(2.0);
   });

   it("zoom(true) should do nothing when already at min (0.2)", async () => {
      const { comp } = await renderComponent();
      comp.vs.scale = 0.2;

      comp.zoom(true);

      expect(Number(comp.vs.scale.toFixed(2))).toBe(0.2);
   });
});

// ---------------------------------------------------------------------------
// Group 16: isInZone [Risk 2]
// ---------------------------------------------------------------------------

describe("VSPane — isInZone", () => {

   it("should return false for ClearLoadingCommand", async () => {
      const { comp } = await renderComponent();
      expect((comp as any).isInZone("ClearLoadingCommand")).toBe(false);
   });

   it("should return false for ShowLoadingMaskCommand", async () => {
      const { comp } = await renderComponent();
      expect((comp as any).isInZone("ShowLoadingMaskCommand")).toBe(false);
   });

   it("should return true for any other command type", async () => {
      const { comp } = await renderComponent();
      expect((comp as any).isInZone("SetViewsheetInfoCommand")).toBe(true);
      expect((comp as any).isInZone("SaveSheetCommand")).toBe(true);
      expect((comp as any).isInZone("AddVSObjectCommand")).toBe(true);
   });
});

// ---------------------------------------------------------------------------
// Group 17: getAssemblyName [Risk 1]
// ---------------------------------------------------------------------------

describe("VSPane — getAssemblyName", () => {

   // getAssemblyName()=null is the contract for "global command processor";
   // changing this would break STOMP routing (handleGlobal=true depends on null return).
   it("should return null", async () => {
      const { comp } = await renderComponent();
      expect(comp.getAssemblyName()).toBeNull();
   });
});

// ---------------------------------------------------------------------------
// Group 18: layoutToolbarVisible [Risk 1]
// ---------------------------------------------------------------------------

describe("VSPane — layoutToolbarVisible", () => {

   it("should return true when vs.layouts.length > 1", async () => {
      const { comp } = await renderComponent();
      comp.vs.layouts = ["Master", "Mobile"];

      expect(comp.layoutToolbarVisible).toBe(true);
   });

   it("should return false when vs.layouts.length <= 1", async () => {
      const { comp } = await renderComponent();
      comp.vs.layouts = ["Master"];

      expect(comp.layoutToolbarVisible).toBe(false);
   });

   it("should return false when vs.layouts is empty", async () => {
      const { comp } = await renderComponent();
      comp.vs.layouts = [];

      expect(comp.layoutToolbarVisible).toBe(false);
   });
});

// ---------------------------------------------------------------------------
// Group 19: trackByFn [Risk 1]
// ---------------------------------------------------------------------------

describe("VSPane — trackByFn", () => {

   it("should return object.absoluteName", async () => {
      const { comp } = await renderComponent();
      const obj: any = { absoluteName: "Chart1" };

      expect(comp.trackByFn(0, obj)).toBe("Chart1");
   });
});

// ---------------------------------------------------------------------------
// Group 20: layoutName [Risk 1]
// ---------------------------------------------------------------------------

describe("VSPane — layoutName", () => {

   it("should return currentLayout.name when currentLayout is set", async () => {
      const { comp } = await renderComponent();
      comp.vs.currentLayout = { name: "Mobile", focusedObjects: [] } as any;

      expect(comp.layoutName).toBe("Mobile");
   });

   it("should return _#(js:Master) when currentLayout is null", async () => {
      const { comp } = await renderComponent();
      comp.vs.currentLayout = null;

      expect(comp.layoutName).toBe("_#(js:Master)");
   });
});

// ---------------------------------------------------------------------------
// Group 21: isFilterInMaxModeView [Risk 1]
// ---------------------------------------------------------------------------

describe("VSPane — isFilterInMaxModeView", () => {

   it("should return true when maxModeAssembly is set and vsObject.adhocFilter=true", async () => {
      const { comp } = await renderComponent();
      (comp as any).maxModeAssembly = "Chart1";
      const obj: any = { absoluteName: "Filter1", adhocFilter: true };

      expect(comp.isFilterInMaxModeView(obj)).toBe(true);
   });

   it("should return false when maxModeAssembly is not set", async () => {
      const { comp } = await renderComponent();
      (comp as any).maxModeAssembly = null;
      const obj: any = { absoluteName: "Filter1", adhocFilter: true };

      expect(comp.isFilterInMaxModeView(obj)).toBe(false);
   });

   it("should return false when vsObject.adhocFilter is false", async () => {
      const { comp } = await renderComponent();
      (comp as any).maxModeAssembly = "Chart1";
      const obj: any = { absoluteName: "Filter1", adhocFilter: false };

      expect(comp.isFilterInMaxModeView(obj)).toBe(false);
   });
});
