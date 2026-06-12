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
 * WSPaneComponent — Pass 1: Interaction
 *
 * Risk-first coverage:
 *   Group 1  [Risk 3] — @Output emitters (cut/copy/editJoin/selectCompositeTable/
 *                        worksheetCompositionChanged): correct payload forwarded to parent
 *   Group 2  [Risk 3] — addVariable: adds new variable; refreshes existing by name match
 *   Group 3  [Risk 3] — addGrouping: adds new grouping; refreshes existing by name match
 *   Group 4  [Risk 2] — processWSInitCommand (command): sets composerToolbarService flags
 *   Group 5  [Risk 2] — processCloseSheetCommand: emits onSheetClose with current worksheet
 *   Group 6  [Risk 2] — processSaveSheetCommand: updates worksheet metadata; shows success
 *                        notification; emits onSaveWorksheetFinish
 *   Group 7  [Risk 2] — processSetWorksheetInfoCommand: updates worksheet.label
 *   Group 8  [Risk 2] — processForceNotCloseWorksheetCommand: sets worksheet.closeProhibited
 *   Group 9  [Risk 2] — processWSFinishPasteWithCutCommand: emits onPasteWithCutFinish
 *   Group 10 [Risk 2] — processSaveWorksheetCommand: emits onSaveWorksheet with close flag
 *   Group 11 [Risk 2] — processWSFocusAssembliesCommand: maps names → assembly instances
 *   Group 12 [Risk 2] — processSetVPMPrincipalCommand: sets worksheet.hasVPMPrincipal
 *   Group 13 [Risk 2] — processWSSetMessageLevelsCommand: sets worksheet.messageLevels
 *   Group 14 [Risk 1] — getAssemblyName: returns null (global command routing)
 *   Group 15 [Risk 1] — processNotification: routes type to correct notifications method
 *
 * Confirmed bugs: none
 *
 * Suspected bugs (header only):
 *   Suspicion A — processSaveSheetCommand resets worksheet.saving=false only via
 *     onSaveWorksheetFinish subscriber; if no parent subscribes, saving remains true
 *     and the toolbar stays disabled indefinitely. No guard in the component itself.
 *   Suspicion B — addVariable/addGrouping search by name but use array index to replace;
 *     if an earlier entry was spliced while iterating, the index could be off by one.
 *     No reproduction path identified with current code, but worth monitoring.
 *
 * Out of scope this pass (covered in ws-pane.component.risk.tl.spec.ts):
 *   sqlEnabled/freeFormSqlEnabled getters, worksheetCancel, processWSRemoveAssemblyCommand,
 *   processWSMoveAssembliesCommand, processWSMoveSchemaTablesCommand, processClearLoadingCommand
 * Out of scope this pass (covered in ws-pane.component.display.tl.spec.ts):
 *   toggleShowColumnName, processShowLoadingMaskCommand
 */

import { ComponentTool } from "../../../../common/util/component-tool";
import {
   renderComponent,
   dispatchCommand,
   makeMocks,
} from "./ws-pane.component.test-helpers";

// ---------------------------------------------------------------------------
// Per-test reset
// ---------------------------------------------------------------------------

afterEach(() => vi.restoreAllMocks());

// ---------------------------------------------------------------------------
// Group 1: @Output emitters [Risk 3]
// ---------------------------------------------------------------------------

describe("WSPaneComponent — @Output emitters", () => {

   // 🔁 Regression-sensitive: cut/copy must carry the worksheet ref so the parent can
   //    identify which sheet is being operated on.
   it("should emit onCut with the current worksheet", async () => {
      const { comp } = await renderComponent();
      const spy = vi.fn();
      comp.onCut.subscribe(spy);

      comp.cut();

      expect(spy).toHaveBeenCalledWith(comp.worksheet);
   });

   it("should emit onCopy with the current worksheet", async () => {
      const { comp } = await renderComponent();
      const spy = vi.fn();
      comp.onCopy.subscribe(spy);

      comp.copy();

      expect(spy).toHaveBeenCalledWith(comp.worksheet);
   });

   it("should emit onEditJoin with the current worksheet", async () => {
      const { comp } = await renderComponent();
      const spy = vi.fn();
      comp.onEditJoin.subscribe(spy);

      comp.editJoin();

      expect(spy).toHaveBeenCalledWith(comp.worksheet);
   });

   // 🔁 Regression-sensitive: selectCompositeTable must set worksheet.selectedCompositeTable
   //    and emit BEFORE the parent checks the value — ordering matters for downstream rendering.
   it("should set worksheet.selectedCompositeTable and emit onWorksheetCompositionChanged", async () => {
      const { comp } = await renderComponent();
      const emitSpy = vi.fn();
      comp.onWorksheetCompositionChanged.subscribe(emitSpy);
      const mockTable = { name: "Join1" } as any;

      comp.selectCompositeTable(mockTable);

      expect(comp.worksheet.selectedCompositeTable).toBe(mockTable);
      expect(emitSpy).toHaveBeenCalledWith(comp.worksheet);
   });

   it("should emit onWorksheetCompositionChanged when worksheetCompositionChanged is called", async () => {
      const { comp } = await renderComponent();
      const spy = vi.fn();
      comp.onWorksheetCompositionChanged.subscribe(spy);

      comp.worksheetCompositionChanged();

      expect(spy).toHaveBeenCalledWith(comp.worksheet);
   });
});

// ---------------------------------------------------------------------------
// Group 2: addVariable [Risk 3]
// ---------------------------------------------------------------------------

describe("WSPaneComponent — addVariable", () => {

   // 🔁 Regression-sensitive: variable must be added to the end of worksheet.variables;
   //    duplicate adds should update in-place, not push a second entry.
   it("should push a new variable when not already present", async () => {
      const { comp } = await renderComponent();
      comp.worksheet.variables = [];
      const newVar = { name: "NewVar", classType: "VariableAssembly" } as any;

      comp.addVariable(newVar);

      expect(comp.worksheet.variables).toHaveLength(1);
      expect(comp.worksheet.variables[0]).toBe(newVar);
   });

   it("should replace an existing variable entry when names match", async () => {
      const { comp } = await renderComponent();
      const existing = { name: "Var1", classType: "VariableAssembly", old: true } as any;
      comp.worksheet.variables = [existing];
      const updated = { name: "Var1", classType: "VariableAssembly", old: false } as any;

      comp.addVariable(updated);

      expect(comp.worksheet.variables).toHaveLength(1);
      expect((comp.worksheet.variables[0] as any).old).toBe(false);
   });

   it("should append without replacing when names differ", async () => {
      const { comp } = await renderComponent();
      const v1 = { name: "Var1", classType: "VariableAssembly" } as any;
      const v2 = { name: "Var2", classType: "VariableAssembly" } as any;
      comp.worksheet.variables = [v1];

      comp.addVariable(v2);

      expect(comp.worksheet.variables).toHaveLength(2);
   });
});

// ---------------------------------------------------------------------------
// Group 3: addGrouping [Risk 3]
// ---------------------------------------------------------------------------

describe("WSPaneComponent — addGrouping", () => {

   it("should push a new grouping when not already present", async () => {
      const { comp } = await renderComponent();
      comp.worksheet.groupings = [];
      const grp = { name: "Grp1", classType: "GroupingAssembly" } as any;

      comp.addGrouping(grp);

      expect(comp.worksheet.groupings).toHaveLength(1);
   });

   it("should replace an existing grouping when names match", async () => {
      const { comp } = await renderComponent();
      const old = { name: "Grp1", classType: "GroupingAssembly", v: 1 } as any;
      comp.worksheet.groupings = [old];
      const updated = { name: "Grp1", classType: "GroupingAssembly", v: 2 } as any;

      comp.addGrouping(updated);

      expect(comp.worksheet.groupings).toHaveLength(1);
      expect((comp.worksheet.groupings[0] as any).v).toBe(2);
   });
});

// ---------------------------------------------------------------------------
// Group 4: processWSInitCommand [Risk 2]
// ---------------------------------------------------------------------------

describe("WSPaneComponent — processWSInitCommand", () => {

   // 🔁 Regression-sensitive: toolbar enabled states must be read from composerToolbarService;
   //    wrong values leave the SQL/join buttons permanently disabled or enabled.
   it("should update composerToolbarService flags from WSInitCommand", async () => {
      const mocks = makeMocks();
      const { comp } = await renderComponent(mocks);

      dispatchCommand("WSInitCommand", {
         jdbcExists: true,
         sqlEnabled: false,
         freeFormSqlEnabled: true,
         crossJoinEnabled: false,
         expressionColumnEnabled: true,
      });

      expect(mocks.composerToolbarService.sqlEnabled).toBe(false);
      expect(mocks.composerToolbarService.crossJoinEnabled).toBe(false);
      expect(mocks.composerToolbarService.freeFormSqlEnabled).toBe(true);
      expect(mocks.composerToolbarService.expressionColumnEnabled).toBe(true);
   });
});

// ---------------------------------------------------------------------------
// Group 5: processCloseSheetCommand [Risk 2]
// ---------------------------------------------------------------------------

describe("WSPaneComponent — processCloseSheetCommand", () => {

   // 🔁 Regression-sensitive: onSheetClose must emit so the parent removes the tab;
   //    missing emit leaves an orphaned non-functional tab.
   it("should emit onSheetClose with the current worksheet", async () => {
      const { comp } = await renderComponent();
      const spy = vi.fn();
      comp.onSheetClose.subscribe(spy);

      dispatchCommand("CloseSheetCommand", {});

      expect(spy).toHaveBeenCalledWith(comp.worksheet);
   });
});

// ---------------------------------------------------------------------------
// Group 6: processSaveSheetCommand [Risk 2]
// ---------------------------------------------------------------------------

describe("WSPaneComponent — processSaveSheetCommand", () => {

   // 🔁 Regression-sensitive: worksheet.newSheet must be cleared after first save so
   //    subsequent saves use PUT instead of POST.
   it("should set worksheet.newSheet=false on save", async () => {
      const { comp } = await renderComponent();
      comp.worksheet.newSheet = true;

      dispatchCommand("SaveSheetCommand", { savePoint: 5, id: "saved-ws" });

      expect(comp.worksheet.newSheet).toBe(false);
   });

   it("should update worksheet.id and savePoint from command", async () => {
      const { comp } = await renderComponent();

      dispatchCommand("SaveSheetCommand", { savePoint: 7, id: "new-ws-id" });

      expect(comp.worksheet.savePoint).toBe(7);
      expect(comp.worksheet.id).toBe("new-ws-id");
   });

   it("should show success notification", async () => {
      const mocks = makeMocks();
      const { comp } = await renderComponent(mocks);

      dispatchCommand("SaveSheetCommand", { savePoint: 1, id: "ws" });

      expect(mocks.notifications.success).toHaveBeenCalledWith(
         "_#(js:common.worksheet.saveSuccess)",
      );
   });

   it("should emit onSaveWorksheetFinish", async () => {
      const { comp } = await renderComponent();
      const spy = vi.fn();
      comp.onSaveWorksheetFinish.subscribe(spy);

      dispatchCommand("SaveSheetCommand", { savePoint: 1, id: "ws" });

      expect(spy).toHaveBeenCalledWith(comp.worksheet);
   });
});

// ---------------------------------------------------------------------------
// Group 7: processSetWorksheetInfoCommand [Risk 2]
// ---------------------------------------------------------------------------

describe("WSPaneComponent — processSetWorksheetInfoCommand", () => {

   it("should update worksheet.label from SetWorksheetInfoCommand", async () => {
      const { comp } = await renderComponent();
      comp.worksheet.label = "Old Label";

      dispatchCommand("SetWorksheetInfoCommand", { label: "New Label" });

      expect(comp.worksheet.label).toBe("New Label");
   });
});

// ---------------------------------------------------------------------------
// Group 8: processForceNotCloseWorksheetCommand [Risk 2]
// ---------------------------------------------------------------------------

describe("WSPaneComponent — processForceNotCloseWorksheetCommand", () => {

   // 🔁 Regression-sensitive: closeProhibited=true must prevent the tab close button from
   //    working; a stale false leaves the sheet closeable when it shouldn't be.
   it("should set worksheet.closeProhibited from ForceNotCloseWorksheetCommand", async () => {
      const { comp } = await renderComponent();
      comp.worksheet.closeProhibited = false;

      dispatchCommand("ForceNotCloseWorksheetCommand", { closeProhibited: true });

      expect(comp.worksheet.closeProhibited).toBe(true);
   });

   it("should clear worksheet.closeProhibited when command sets false", async () => {
      const { comp } = await renderComponent();
      comp.worksheet.closeProhibited = true;

      dispatchCommand("ForceNotCloseWorksheetCommand", { closeProhibited: false });

      expect(comp.worksheet.closeProhibited).toBe(false);
   });
});

// ---------------------------------------------------------------------------
// Group 9: processWSFinishPasteWithCutCommand [Risk 2]
// ---------------------------------------------------------------------------

describe("WSPaneComponent — processWSFinishPasteWithCutCommand", () => {

   // 🔁 Regression-sensitive: onPasteWithCutFinish must carry [sourceSheetId, assemblies]
   //    so the parent can remove the cut originals.
   it("should emit onPasteWithCutFinish with [sourceSheetId, assemblies]", async () => {
      const { comp } = await renderComponent();
      const spy = vi.fn();
      comp.onPasteWithCutFinish.subscribe(spy);

      dispatchCommand("WsFinishPasteWithCutCommand", {
         sourceSheetId: "src-ws",
         assemblies: ["AssemblyA", "AssemblyB"],
      });

      expect(spy).toHaveBeenCalledWith(["src-ws", ["AssemblyA", "AssemblyB"]]);
   });
});

// ---------------------------------------------------------------------------
// Group 10: processSaveWorksheetCommand [Risk 2]
// ---------------------------------------------------------------------------

describe("WSPaneComponent — processSaveWorksheetCommand", () => {

   it("should emit onSaveWorksheet with close=true when command.close=true", async () => {
      const { comp } = await renderComponent();
      const spy = vi.fn();
      comp.onSaveWorksheet.subscribe(spy);

      dispatchCommand("SaveWorksheetCommand", { close: true });

      expect(spy).toHaveBeenCalledWith(
         expect.objectContaining({ worksheet: comp.worksheet, close: true, updateDep: false }),
      );
   });

   it("should emit onSaveWorksheet with close=false when command.close=false", async () => {
      const { comp } = await renderComponent();
      const spy = vi.fn();
      comp.onSaveWorksheet.subscribe(spy);

      dispatchCommand("SaveWorksheetCommand", { close: false });

      expect(spy).toHaveBeenCalledWith(
         expect.objectContaining({ close: false }),
      );
   });
});

// ---------------------------------------------------------------------------
// Group 11: processWSFocusAssembliesCommand [Risk 2]
// ---------------------------------------------------------------------------

describe("WSPaneComponent — processWSFocusAssembliesCommand", () => {

   // 🔁 Regression-sensitive: currentFocusedAssemblies must reference the live objects from
   //    worksheet.assemblies(), not fresh instances, so that downstream pointer comparisons work.
   it("should map assemblyNames to existing assembly instances", async () => {
      const { comp } = await renderComponent();
      const a1 = { name: "T1", classType: "TableAssembly" };
      const a2 = { name: "T2", classType: "TableAssembly" };
      comp.worksheet.assemblies = vi.fn().mockReturnValue([a1, a2]);

      dispatchCommand("WSFocusAssembliesCommand", { assemblyNames: ["T1"] });

      expect(comp.worksheet.currentFocusedAssemblies).toHaveLength(1);
      expect(comp.worksheet.currentFocusedAssemblies[0]).toBe(a1);
   });

   it("should set undefined for assembly names not found", async () => {
      const { comp } = await renderComponent();
      comp.worksheet.assemblies = vi.fn().mockReturnValue([]);

      dispatchCommand("WSFocusAssembliesCommand", { assemblyNames: ["NotFound"] });

      expect(comp.worksheet.currentFocusedAssemblies[0]).toBeUndefined();
   });
});

// ---------------------------------------------------------------------------
// Group 12: processSetVPMPrincipalCommand [Risk 2]
// ---------------------------------------------------------------------------

describe("WSPaneComponent — processSetVPMPrincipalCommand", () => {

   it("should set worksheet.hasVPMPrincipal from SetVPMPrincipalCommand", async () => {
      const { comp } = await renderComponent();

      dispatchCommand("SetVPMPrincipalCommand", { hasVPMPrincipal: true });

      expect(comp.worksheet.hasVPMPrincipal).toBe(true);
   });
});

// ---------------------------------------------------------------------------
// Group 13: processWSSetMessageLevelsCommand [Risk 2]
// ---------------------------------------------------------------------------

describe("WSPaneComponent — processWSSetMessageLevelsCommand", () => {

   it("should set worksheet.messageLevels from WSSetMessageLevelsCommand", async () => {
      const { comp } = await renderComponent();
      const levels = { INFO: true, WARNING: false };

      dispatchCommand("WSSetMessageLevelsCommand", { messageLevels: levels });

      expect(comp.worksheet.messageLevels).toEqual(levels);
   });
});

// ---------------------------------------------------------------------------
// Group 14: getAssemblyName [Risk 1]
// ---------------------------------------------------------------------------

describe("WSPaneComponent — getAssemblyName", () => {

   // getAssemblyName()=null is the contract for "global command processor" — changing this
   // would break STOMP command routing (handleGlobal=true depends on null return).
   it("should return null", async () => {
      const { comp } = await renderComponent();
      expect(comp.getAssemblyName()).toBeNull();
   });
});

// ---------------------------------------------------------------------------
// Group 15: processNotification [Risk 1]
// ---------------------------------------------------------------------------

describe("WSPaneComponent — processNotification", () => {

   it("should call notifications.success for type=success", async () => {
      const mocks = makeMocks();
      const { comp } = await renderComponent(mocks);

      comp.processNotification({ type: "success", message: "Done" });

      expect(mocks.notifications.success).toHaveBeenCalledWith("Done");
   });

   it("should call notifications.info for type=info", async () => {
      const mocks = makeMocks();
      const { comp } = await renderComponent(mocks);

      comp.processNotification({ type: "info", message: "FYI" });

      expect(mocks.notifications.info).toHaveBeenCalledWith("FYI");
   });

   it("should call notifications.warning for type=warning", async () => {
      const mocks = makeMocks();
      const { comp } = await renderComponent(mocks);

      comp.processNotification({ type: "warning", message: "Caution" });

      expect(mocks.notifications.warning).toHaveBeenCalledWith("Caution");
   });

   it("should call notifications.danger for type=danger", async () => {
      const mocks = makeMocks();
      const { comp } = await renderComponent(mocks);

      comp.processNotification({ type: "danger", message: "Error!" });

      expect(mocks.notifications.danger).toHaveBeenCalledWith("Error!");
   });

   it("should call notifications.warning for unknown type", async () => {
      const mocks = makeMocks();
      const { comp } = await renderComponent(mocks);

      comp.processNotification({ type: "unknown", message: "???" });

      expect(mocks.notifications.warning).toHaveBeenCalledWith("???");
   });
});
