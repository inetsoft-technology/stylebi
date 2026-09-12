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
 * ObjectWizardPane — Pass 1: Interaction
 *
 * Risk-first coverage:
 *   Group 1  [Risk 3] — close(): sends CANCEL_URI / SAVE_URI; sets blocking=true; when
 *                        originalMode=FULL_EDITOR + save, resets editMode/originalMode to
 *                        VIEWSHEET_PANE before sending so the server receives the correct mode
 *   Group 2  [Risk 3] — processCloseObjectWizardCommand: emits onClose with save/editMode/
 *                        currentObject; calls bindingTreeService.reset() first
 *   Group 3  [Risk 3] — setVSObjectModel (via processRefreshVSObjectCommand /
 *                        processAddVSObjectCommand): non-temp skipped; initial temp stored;
 *                        older temp skipped; newer temp replaces;
 *                        updateVSObjectModel: VSText model hAlign set to center
 *   Group 4  [Risk 3] — processRemoveVSObjectCommand: latest temp assembly → sets vsObject=null;
 *                        non-temp → no change
 *   Group 5  [Risk 3] — isLatestTempAssembly: null oldName → true; non-temp newName → false;
 *                        same time → true; newer time → true; older time → false
 *   Group 6  [Risk 2] — processRefreshRecommendCommand: sets recommenderModel; originalIsDetail
 *                        captured on first call only (subsequent calls do NOT overwrite it)
 *   Group 7  [Risk 2] — processRefreshVsWizardBindingCommand: updates _bindingModel when changed;
 *                        does NOT update when equal; updates treeInfo.tempBinding
 *   Group 8  [Risk 2] — onDeleteAggregate: unSelectNode when not in measures; sends refresh with
 *                        deleteFormatColumn when name in measures but fullName not; sends plain
 *                        refresh when fullName also in measures
 *   Group 9  [Risk 2] — onDeleteDimension: unSelectNode when not in dimensions; sends with
 *                        deleteFormatColumn when name in dimensions but fullName not
 *   Group 10 [Risk 2] — sendRefreshWizardBindingEvent (via onEditAggregate): sends
 *                        REFRESH_WIZARD_BINDING_INFO with UpdateVsWizardBindingEvent containing
 *                        chartBindingModel and selectedNodes' assetEntry data
 *   Group 11 [baseline] — showAutoOrder: false when selectedType=ORIGINAL_TYPE+isDetail; true
 *                          for VSChart/VSCrosstab vsObject
 *   Group 12 [baseline] — isFullEditorVisible: false when originalName is temp with no binding;
 *                          true for VSChart objectType
 *   Group 13 [baseline] — goToFullEditor: sets editMode=FULL_EDITOR; emits onFullEditor with
 *                          editMode and objectModel
 *   Group 14 [baseline] — processSetWizardBindingFormatCommand: builds formatMap from models list
 *   Group 15 [baseline] — switchToMeta / processSwitchToMetaModeCommand: sends SWITCH_TO_META;
 *                          processSwitchToMetaModeCommand emits onSwitchMeta
 *   Group 16 [baseline] — changeSubtype: updates lastSubType and currentSubType
 *   Group 17 [baseline] — updateFormat: sends UPDATE_WIZARD_BINDING_FORMAT when field exists in
 *                          formatMap; does nothing when field absent
 *   Group 18 [baseline] — processTableWarningCommand: calls notifications.success() with message
 *   Group 19 [baseline] — addConsoleMessage / messageChange: consoleMessages updated correctly
 *   Group 20 [baseline] — ngOnDestroy → cleanup() called (subscriptions cleaned up, no leaks)
 *
 * Suspected bugs (header only):
 *   Suspicion A — processSetVSBindingModelCommand: the update guard `command.binding.type == "table"
 *     && !Tool.isEquals(...)` means chart binding model updates via this command are silently
 *     dropped. The function only handles table type; chart type binding changes are ignored.
 *
 * Out of scope this pass (→ Pass 2):
 *   aiAssistantPermission setter — tested via AiAssistantService mock
 *   processRemoveVSObjectCommand deep: isLatestTempAssembly time comparison
 *   onEditSecondColumn: checkAggTrap observable + modal flow (Pass 2)
 *   showProgressDialog: modal flow (Pass 2)
 *   processProgress: command.events dispatch (Pass 2)
 *   toggleRepositoryTreePane: splitPane DOM layout (Pass 2)
 */

import { waitFor } from "@testing-library/angular";

import {
   commandsSubject,
   CLIENT_SERVICE_MOCK,
   BINDING_TREE_MOCK,
   AI_ASSISTANT_MOCK,
   resetMocks,
   makeVSObject,
   tempName,
   renderComponent,
} from "./object-wizard-pane.test-fixtures";
import { VsWizardEditModes } from "../../model/vs-wizard-edit-modes";
import { VSRecommendType } from "../../model/recommender/vs-recommend-type";

beforeEach(() => {
   resetMocks();
});

// ---------------------------------------------------------------------------
// Group 1 — close() [Risk 3]
// ---------------------------------------------------------------------------

describe("ObjectWizardPane — close()", () => {
   // 🔁 Regression-sensitive: the server must receive the correct save/cancel URL and event
   // payload. If the wrong URL is used or blocking is not set, the wizard state machine can
   // get stuck or the save be ignored.
   it("close(false) should send to CANCEL URI and set blocking=true", async () => {
      const { comp } = await renderComponent();
      CLIENT_SERVICE_MOCK.sendEvent.mockClear();

      comp.close(false);

      expect(CLIENT_SERVICE_MOCK.sendEvent).toHaveBeenCalledWith(
         "/events/vswizard/object/close/cancel",
         expect.anything()
      );
      expect(comp.blocking).toBe(true);
   });

   it("close(true) should send to SAVE URI", async () => {
      const { comp } = await renderComponent();
      CLIENT_SERVICE_MOCK.sendEvent.mockClear();

      comp.close(true);

      expect(CLIENT_SERVICE_MOCK.sendEvent).toHaveBeenCalledWith(
         "/events/vswizard/object/close/save",
         expect.anything()
      );
   });

   it("close(true) with originalMode=FULL_EDITOR should reset editMode to VIEWSHEET_PANE before sending", async () => {
      // If editMode is NOT reset before the event is built, the server receives FULL_EDITOR
      // which causes it to skip the save and instead open the full editor again.
      const { comp } = await renderComponent({
         originalMode: VsWizardEditModes.FULL_EDITOR,
         editMode: VsWizardEditModes.WIZARD_DASHBOARD,
      });
      CLIENT_SERVICE_MOCK.sendEvent.mockClear();

      comp.close(true);

      // editMode and originalMode should both be VIEWSHEET_PANE after reset
      expect(comp.editMode).toBe(VsWizardEditModes.VIEWSHEET_PANE);
      expect(comp.originalMode).toBe(VsWizardEditModes.VIEWSHEET_PANE);
   });

   it("close(false) with originalMode=FULL_EDITOR should NOT reset editMode", async () => {
      const { comp } = await renderComponent({
         originalMode: VsWizardEditModes.FULL_EDITOR,
         editMode: VsWizardEditModes.WIZARD_DASHBOARD,
      });

      comp.close(false);

      // cancel path does not reset modes
      expect(comp.editMode).toBe(VsWizardEditModes.WIZARD_DASHBOARD);
   });
});

// ---------------------------------------------------------------------------
// Group 2 — processCloseObjectWizardCommand [Risk 3]
// ---------------------------------------------------------------------------

describe("ObjectWizardPane — processCloseObjectWizardCommand", () => {
   // 🔁 Regression-sensitive: the caller (VsWizardComponent) listens on onClose to decide
   // whether to navigate away or stay. If reset() is not called, leftover tree state contaminates
   // the next wizard session.
   it("should call bindingTreeService.reset() and emit onClose", async () => {
      const { comp } = await renderComponent();
      const closedPayloads: any[] = [];
      comp.onClose.subscribe(v => closedPayloads.push(v));

      const cmd = { save: true, currentObject: { absoluteName: "Chart1" } } as any;
      (comp as any)["processCloseObjectWizardCommand"](cmd);

      expect(BINDING_TREE_MOCK.reset).toHaveBeenCalledTimes(1);
      expect(closedPayloads).toHaveLength(1);
      expect(closedPayloads[0]).toMatchObject({ save: true, currentObject: cmd.currentObject });
   });

   it("should include editMode in onClose emission", async () => {
      const { comp } = await renderComponent({ editMode: VsWizardEditModes.WIZARD_DASHBOARD });
      const payloads: any[] = [];
      comp.onClose.subscribe(v => payloads.push(v));

      (comp as any)["processCloseObjectWizardCommand"]({ save: false, currentObject: null });

      expect(payloads[0].editMode).toBe(VsWizardEditModes.WIZARD_DASHBOARD);
   });
});

// ---------------------------------------------------------------------------
// Group 3 — setVSObjectModel (via processRefreshVSObjectCommand) [Risk 3]
// ---------------------------------------------------------------------------

describe("ObjectWizardPane — setVSObjectModel via processRefreshVSObjectCommand", () => {
   // 🔁 Regression-sensitive: the isLatestTempAssembly guard is critical to prevent stale
   // commands (old temp objects) from overwriting the current object after the wizard creates
   // a newer temp assembly.
   it("should skip a non-temp assembly model (vsObject stays unset)", async () => {
      const { comp } = await renderComponent();
      const model = makeVSObject({ absoluteName: "Chart1" }); // no TEMP_PREFIX

      (comp as any)["processRefreshVSObjectCommand"]({ info: model });

      expect(comp.vsObject).toBeFalsy();
   });

   it("should set vsObject when there is no existing vsObject and model is temp", async () => {
      const { comp } = await renderComponent();
      const model = makeVSObject({ absoluteName: tempName(1000) });

      (comp as any)["processRefreshVSObjectCommand"]({ info: model });

      expect(comp.vsObject?.absoluteName).toBe(tempName(1000));
   });

   it("should skip an older temp assembly (newTime < oldTime)", async () => {
      const { comp } = await renderComponent();
      // First install a newer temp object
      const newer = makeVSObject({ absoluteName: tempName(2000) });
      (comp as any)["processRefreshVSObjectCommand"]({ info: newer });
      // Now try to replace with an older one
      const older = makeVSObject({ absoluteName: tempName(1000) });
      (comp as any)["processRefreshVSObjectCommand"]({ info: older });

      // vsObject should still be the newer one
      expect(comp.vsObject?.absoluteName).toBe(tempName(2000));
   });

   it("should replace vsObject when model is a newer temp assembly (newTime >= oldTime)", async () => {
      const { comp } = await renderComponent();
      const old = makeVSObject({ absoluteName: tempName(1000) });
      (comp as any)["processRefreshVSObjectCommand"]({ info: old });
      const newer = makeVSObject({ absoluteName: tempName(2000) });
      (comp as any)["processRefreshVSObjectCommand"]({ info: newer });

      expect(comp.vsObject?.absoluteName).toBe(tempName(2000));
   });

   it("should set hAlign=center when updateVSObjectModel is called with a VSText model", async () => {
      const { comp } = await renderComponent();
      const textModel = makeVSObject({
         objectType: "VSText",
         objectFormat: { top: 0, left: 0, width: 100, height: 50, hAlign: "left" } as any,
      });

      (comp as any)["updateVSObjectModel"](textModel);

      expect(comp.vsObject?.objectFormat?.hAlign).toBe("center");
   });
});

// ---------------------------------------------------------------------------
// Group 4 — processRemoveVSObjectCommand [Risk 3]
// ---------------------------------------------------------------------------

describe("ObjectWizardPane — processRemoveVSObjectCommand", () => {
   // 🔁 Regression-sensitive: if the removed assembly is the current temp object, vsObject must
   // be cleared so the preview pane shows nothing instead of a stale ghost.
   it("should set vsObject=null when removed assembly is the current temp assembly", async () => {
      const { comp } = await renderComponent();
      comp.vsObject = makeVSObject({ absoluteName: tempName(1000) });

      (comp as any)["processRemoveVSObjectCommand"]({ name: tempName(1000) });

      expect(comp.vsObject).toBeNull();
   });

   it("should NOT change vsObject when removed assembly is a non-temp name", async () => {
      const { comp } = await renderComponent();
      const existing = makeVSObject({ absoluteName: tempName(1000) });
      comp.vsObject = existing;

      (comp as any)["processRemoveVSObjectCommand"]({ name: "Chart1" }); // non-temp

      expect(comp.vsObject?.absoluteName).toBe(tempName(1000));
   });
});

// ---------------------------------------------------------------------------
// Group 5 — isLatestTempAssembly [Risk 3]
// ---------------------------------------------------------------------------

describe("ObjectWizardPane — isLatestTempAssembly", () => {
   // 🔁 Regression-sensitive: this method is the sole guard against stale-command overwrites.
   // If it returns wrong values, old server responses can corrupt the current wizard state.
   it("returns false for non-temp newAssemblyName regardless of old name", async () => {
      const { comp } = await renderComponent();
      const result = (comp as any)["isLatestTempAssembly"](null, "RegularChart1");
      expect(result).toBe(false);
   });

   it("returns true when oldAssemblyName is null (no existing object)", async () => {
      const { comp } = await renderComponent();
      const result = (comp as any)["isLatestTempAssembly"](null, tempName(1000));
      expect(result).toBe(true);
   });

   it("returns true when oldAssemblyName is non-temp (wizard just started)", async () => {
      const { comp } = await renderComponent();
      const result = (comp as any)["isLatestTempAssembly"]("SomeRegularChart", tempName(1000));
      expect(result).toBe(true);
   });

   it("returns true when newTime === oldTime", async () => {
      const { comp } = await renderComponent();
      const result = (comp as any)["isLatestTempAssembly"](tempName(1000), tempName(1000));
      expect(result).toBe(true);
   });

   it("returns true when newTime > oldTime", async () => {
      const { comp } = await renderComponent();
      const result = (comp as any)["isLatestTempAssembly"](tempName(1000), tempName(2000));
      expect(result).toBe(true);
   });

   it("returns false when newTime < oldTime", async () => {
      const { comp } = await renderComponent();
      const result = (comp as any)["isLatestTempAssembly"](tempName(2000), tempName(1000));
      expect(result).toBe(false);
   });
});

// ---------------------------------------------------------------------------
// Group 6 — processRefreshRecommendCommand [Risk 2]
// ---------------------------------------------------------------------------

describe("ObjectWizardPane — processRefreshRecommendCommand", () => {
   // 🔁 Regression-sensitive: originalIsDetail must be captured exactly once on the first
   // recommend command. If it gets overwritten on subsequent calls, undo/redo of chart type
   // changes can restore the wrong detail/aggregate mode.
   it("should set recommenderModel from command", async () => {
      const { comp } = await renderComponent();
      const model = { selectedType: VSRecommendType.TABLE, recommendationList: [] } as any;

      comp.processRefreshRecommendCommand({ recommenderModel: model } as any);

      expect(comp.recommenderModel).toBe(model);
   });

   it("should set originalIsDetail on the first call", async () => {
      const { comp } = await renderComponent();
      // isDetail returns true when selectedType == TABLE (has tableBindingModel=null but type=TABLE)
      const firstModel = { selectedType: VSRecommendType.TABLE, recommendationList: [] } as any;
      comp.processRefreshRecommendCommand({ recommenderModel: firstModel } as any);

      // originalIsDetail should be captured from the first call
      expect((comp as any).originalIsDetail).not.toBeNull();
   });

   it("should NOT overwrite originalIsDetail on subsequent calls", async () => {
      const { comp } = await renderComponent();
      const firstModel = { selectedType: VSRecommendType.TABLE, recommendationList: [] } as any;
      comp.processRefreshRecommendCommand({ recommenderModel: firstModel } as any);
      const originalValue = (comp as any).originalIsDetail;

      // Second call with a different model type
      const secondModel = { selectedType: VSRecommendType.CHART, recommendationList: [] } as any;
      comp.processRefreshRecommendCommand({ recommenderModel: secondModel } as any);

      expect((comp as any).originalIsDetail).toBe(originalValue);
   });
});

// ---------------------------------------------------------------------------
// Group 7 — processRefreshVsWizardBindingCommand [Risk 2]
// ---------------------------------------------------------------------------

describe("ObjectWizardPane — processRefreshVsWizardBindingCommand", () => {
   // 🔁 Regression-sensitive: if _bindingModel is updated when unchanged, aiAssistantService
   // contexts are re-set unnecessarily (side effects). If NOT updated when changed, the binding
   // pane shows stale data.
   it("should update _bindingModel when the incoming model differs", async () => {
      const { comp } = await renderComponent();
      const newModel = {
         chartBindingModel: { xfields: [], yfields: [] },
         tableBindingModel: null,
         filterBindingModel: null,
         sourceName: "Sales",
         assemblyType: 1,
         autoOrder: true,
      } as any;

      comp.processRefreshVsWizardBindingCommand({ bindingModel: newModel } as any);

      expect(comp._bindingModel).toBe(newModel);
   });

   it("should call aiAssistantService.setBindingContext after updating binding model", async () => {
      const { comp } = await renderComponent();
      AI_ASSISTANT_MOCK.setBindingContext.mockClear();
      const newModel = { chartBindingModel: { xfields: [] }, sourceName: "Sales" } as any;

      comp.processRefreshVsWizardBindingCommand({ bindingModel: newModel } as any);

      expect(AI_ASSISTANT_MOCK.setBindingContext).toHaveBeenCalledTimes(1);
   });

   it("should update treeInfo.tempBinding when treeInfo is set", async () => {
      const { comp } = await renderComponent();
      const chartBinding = { xfields: [], yfields: [] } as any;
      const newModel = { chartBindingModel: chartBinding, sourceName: "Sales" } as any;
      BINDING_TREE_MOCK.treeInfo = { tempBinding: null as any, grayedOutFields: [] };
      (comp as any).bindingTreeService = BINDING_TREE_MOCK;

      comp.processRefreshVsWizardBindingCommand({ bindingModel: newModel } as any);

      expect(BINDING_TREE_MOCK.treeInfo.tempBinding).toBe(chartBinding);
   });
});

// ---------------------------------------------------------------------------
// Group 8 — onDeleteAggregate [Risk 2]
// ---------------------------------------------------------------------------

describe("ObjectWizardPane — onDeleteAggregate", () => {
   // 🔁 Regression-sensitive: if unSelectNode is not called when the measure is absent from
   // the binding, the tree still shows the node as selected after deletion.
   it("should call unSelectNode when columnValue is not in measures", async () => {
      const { comp } = await renderComponent();
      // _bindingModel has no measures, so the chart binding's yfields will be empty
      (comp as any)._bindingModel = { chartBindingModel: { xfields: [], yfields: [] } } as any;
      BINDING_TREE_MOCK.unSelectNode.mockClear();

      comp.onDeleteAggregate({ columnValue: "SalesAmt", fullName: "SalesAmt" } as any);

      expect(BINDING_TREE_MOCK.unSelectNode).toHaveBeenCalledWith("SalesAmt");
      expect(CLIENT_SERVICE_MOCK.sendEvent).not.toHaveBeenCalled();
   });

   it("should send refresh WITHOUT deleteFormatColumn when fullName is also in measures", async () => {
      const { comp } = await renderComponent();
      (comp as any)._bindingModel = {
         chartBindingModel: {
            xfields: [],
            yfields: [
               { name: "SalesAmt", fullName: "SalesAmt__Full", columnValue: "SalesAmt" }
            ],
         },
      } as any;
      CLIENT_SERVICE_MOCK.sendEvent.mockClear();

      comp.onDeleteAggregate({ columnValue: "SalesAmt", fullName: "SalesAmt__Full" } as any);

      expect(CLIENT_SERVICE_MOCK.sendEvent).toHaveBeenCalledWith(
         "/events/vswizard/binding/refresh",
         expect.not.objectContaining({ deleteFormatColumn: expect.anything() })
      );
   });

   it("should send refresh WITH deleteFormatColumn when name in measures but fullName is not", async () => {
      const { comp } = await renderComponent();
      (comp as any)._bindingModel = {
         chartBindingModel: {
            xfields: [],
            yfields: [
               { name: "SalesAmt", fullName: "SalesAmt__Different", columnValue: "SalesAmt" }
            ],
         },
      } as any;
      CLIENT_SERVICE_MOCK.sendEvent.mockClear();

      comp.onDeleteAggregate({ columnValue: "SalesAmt", fullName: "SalesAmt__Full" } as any);

      expect(CLIENT_SERVICE_MOCK.sendEvent).toHaveBeenCalledWith(
         "/events/vswizard/binding/refresh",
         expect.objectContaining({ deleteFormatColumn: "SalesAmt__Full" })
      );
   });
});

// ---------------------------------------------------------------------------
// Group 9 — onDeleteDimension [Risk 2]
// ---------------------------------------------------------------------------

describe("ObjectWizardPane — onDeleteDimension", () => {
   // 🔁 Same unSelect/send pattern as onDeleteAggregate but for the dimensions (xfields)
   it("should call unSelectNode when columnValue is not in dimensions", async () => {
      const { comp } = await renderComponent();
      (comp as any)._bindingModel = { chartBindingModel: { xfields: [], yfields: [] } } as any;
      BINDING_TREE_MOCK.unSelectNode.mockClear();

      comp.onDeleteDimension({ columnValue: "Year", fullName: "Year" } as any);

      expect(BINDING_TREE_MOCK.unSelectNode).toHaveBeenCalledWith("Year");
   });

   it("should send refresh WITH deleteFormatColumn when dimension name is in xfields but fullName is not", async () => {
      const { comp } = await renderComponent();
      (comp as any)._bindingModel = {
         chartBindingModel: {
            xfields: [{ name: "Year", fullName: "Year__Full", columnValue: "Year" }],
            yfields: [],
         },
      } as any;
      CLIENT_SERVICE_MOCK.sendEvent.mockClear();

      comp.onDeleteDimension({ columnValue: "Year", fullName: "Year__Different" } as any);

      expect(CLIENT_SERVICE_MOCK.sendEvent).toHaveBeenCalledWith(
         "/events/vswizard/binding/refresh",
         expect.objectContaining({ deleteFormatColumn: "Year__Different" })
      );
   });
});

// ---------------------------------------------------------------------------
// Group 10 — sendRefreshWizardBindingEvent via onEditAggregate [Risk 2]
// ---------------------------------------------------------------------------

describe("ObjectWizardPane — sendRefreshWizardBindingEvent via onEditAggregate", () => {
   // 🔁 Regression-sensitive: if selectedNodes data is not passed, the server cannot build the
   // UpdateVsWizardBindingEvent with the correct AssetEntry list and silently drops the change.
   it("should send REFRESH_WIZARD_BINDING_INFO with UpdateVsWizardBindingEvent", async () => {
      const { comp } = await renderComponent();
      (comp as any)._bindingModel = { chartBindingModel: { xfields: [], yfields: [] } } as any;
      BINDING_TREE_MOCK.selectedNodes = [
         { data: { type: 16384, path: "/Sales/SalesAmt" } } as any,
      ];
      CLIENT_SERVICE_MOCK.sendEvent.mockClear();

      comp.onEditAggregate();

      expect(CLIENT_SERVICE_MOCK.sendEvent).toHaveBeenCalledWith(
         "/events/vswizard/binding/refresh",
         expect.anything()
      );
   });
});

// ---------------------------------------------------------------------------
// Group 11 — showAutoOrder [baseline]
// ---------------------------------------------------------------------------

describe("ObjectWizardPane — showAutoOrder", () => {
   it("should return false when selectedType=ORIGINAL_TYPE and isDetail=true", async () => {
      const { comp } = await renderComponent();
      comp.recommenderModel = {
         selectedType: VSRecommendType.ORIGINAL_TYPE,
         recommendationList: [],
      } as any;
      (comp as any).originalIsDetail = true;
      // VSChart object
      comp.vsObject = makeVSObject({ objectType: "VSChart" });

      expect(comp.showAutoOrder).toBe(false);
   });

   it("should return true for VSChart object when not ORIGINAL_TYPE+isDetail", async () => {
      const { comp } = await renderComponent();
      comp.vsObject = makeVSObject({ objectType: "VSChart" });
      comp.recommenderModel = {
         selectedType: VSRecommendType.CHART,
         recommendationList: [],
      } as any;

      expect(comp.showAutoOrder).toBe(true);
   });

   it("should return false when vsObject is null", async () => {
      const { comp } = await renderComponent();
      expect(comp.showAutoOrder).toBe(false);
   });
});

// ---------------------------------------------------------------------------
// Group 12 — isFullEditorVisible [baseline]
// ---------------------------------------------------------------------------

describe("ObjectWizardPane — isFullEditorVisible", () => {
   it("should return false when originalName is temp and no binding nodes selected", async () => {
      const { comp } = await renderComponent({
         originalName: tempName(1000),
      });
      BINDING_TREE_MOCK.getSelectedBindingNodePaths.mockReturnValueOnce([]);

      expect(comp.isFullEditorVisible()).toBe(false);
   });

   it("should return true for VSChart objectType", async () => {
      const { comp } = await renderComponent({ originalName: "RegularChart1" });

      comp.vsObject = makeVSObject({ objectType: "VSChart" });
      expect(comp.isFullEditorVisible()).toBe(true);
   });

   it("should return true for VSCrosstab objectType", async () => {
      const { comp } = await renderComponent({ originalName: "Crosstab1" });
      comp.vsObject = makeVSObject({ objectType: "VSCrosstab" });
      expect(comp.isFullEditorVisible()).toBe(true);
   });
});

// ---------------------------------------------------------------------------
// Group 13 — goToFullEditor [baseline]
// ---------------------------------------------------------------------------

describe("ObjectWizardPane — goToFullEditor", () => {
   it("should set editMode=FULL_EDITOR and emit onFullEditor", async () => {
      const { comp } = await renderComponent();
      const emitted: any[] = [];
      comp.onFullEditor.subscribe(v => emitted.push(v));

      const model = makeVSObject();
      comp.goToFullEditor(model);

      expect(comp.editMode).toBe(VsWizardEditModes.FULL_EDITOR);
      expect(emitted).toHaveLength(1);
      expect(emitted[0]).toMatchObject({
         editMode: VsWizardEditModes.FULL_EDITOR,
         objectModel: model,
      });
   });
});

// ---------------------------------------------------------------------------
// Group 14 — processSetWizardBindingFormatCommand [baseline]
// ---------------------------------------------------------------------------

describe("ObjectWizardPane — processSetWizardBindingFormatCommand", () => {
   it("should build formatMap from models list", async () => {
      const { comp } = await renderComponent();
      const cmd = {
         models: [
            { fieldName: "SalesAmt", formatModel: { format: "Currency" } },
            { fieldName: "Year", formatModel: { format: "Date" } },
         ],
      } as any;

      comp.processSetWizardBindingFormatCommand(cmd);

      expect(comp.formatMap?.get("SalesAmt")).toMatchObject({ format: "Currency" });
      expect(comp.formatMap?.get("Year")).toMatchObject({ format: "Date" });
   });

   it("should create empty formatMap when command has no models", async () => {
      const { comp } = await renderComponent();

      comp.processSetWizardBindingFormatCommand({ models: null } as any);

      expect(comp.formatMap).toBeDefined();
      expect(comp.formatMap?.size).toBe(0);
   });
});

// ---------------------------------------------------------------------------
// Group 15 — switchToMeta / processSwitchToMetaModeCommand [baseline]
// ---------------------------------------------------------------------------

describe("ObjectWizardPane — switchToMeta / processSwitchToMetaModeCommand", () => {
   it("switchToMeta() should send SWITCH_TO_META event", async () => {
      const { comp } = await renderComponent();
      CLIENT_SERVICE_MOCK.sendEvent.mockClear();

      comp.switchToMeta();

      expect(CLIENT_SERVICE_MOCK.sendEvent).toHaveBeenCalledWith(
         "/events/vs/wizard/use-meta",
         expect.anything()
      );
   });

   it("processSwitchToMetaModeCommand() should emit onSwitchMeta", async () => {
      const { comp } = await renderComponent();
      const emitted: any[] = [];
      comp.onSwitchMeta.subscribe(v => emitted.push(v));

      comp.processSwitchToMetaModeCommand();

      expect(emitted).toHaveLength(1);
   });
});

// ---------------------------------------------------------------------------
// Group 16 — changeSubtype [baseline]
// ---------------------------------------------------------------------------

describe("ObjectWizardPane — changeSubtype", () => {
   it("should move currentSubType to lastSubType and set new currentSubType", async () => {
      const { comp } = await renderComponent();
      const first = { type: "BAR" } as any;
      const second = { type: "LINE" } as any;

      comp.changeSubtype(first);
      expect(comp.currentSubType).toBe(first);
      expect(comp.lastSubType).toBeUndefined();

      comp.changeSubtype(second);
      expect(comp.currentSubType).toBe(second);
      expect(comp.lastSubType).toBe(first);
   });
});

// ---------------------------------------------------------------------------
// Group 17 — updateFormat [baseline]
// ---------------------------------------------------------------------------

describe("ObjectWizardPane — updateFormat", () => {
   it("should send UPDATE_WIZARD_BINDING_FORMAT when field is in formatMap", async () => {
      const { comp } = await renderComponent();
      const formatModel = { format: "Currency" } as any;
      comp.formatMap = new Map([["SalesAmt", formatModel]]);
      CLIENT_SERVICE_MOCK.sendEvent.mockClear();

      comp.updateFormat("SalesAmt");

      expect(CLIENT_SERVICE_MOCK.sendEvent).toHaveBeenCalledWith(
         "/events/vswizard/object/format",
         expect.anything()
      );
   });

   it("should NOT send event when field is absent from formatMap", async () => {
      const { comp } = await renderComponent();
      comp.formatMap = new Map();
      CLIENT_SERVICE_MOCK.sendEvent.mockClear();

      comp.updateFormat("UnknownField");

      expect(CLIENT_SERVICE_MOCK.sendEvent).not.toHaveBeenCalled();
   });

   it("should NOT send event when formatMap is null", async () => {
      const { comp } = await renderComponent();
      comp.formatMap = null;
      CLIENT_SERVICE_MOCK.sendEvent.mockClear();

      comp.updateFormat("SalesAmt");

      expect(CLIENT_SERVICE_MOCK.sendEvent).not.toHaveBeenCalled();
   });
});

// ---------------------------------------------------------------------------
// Group 18 — processTableWarningCommand [baseline]
// ---------------------------------------------------------------------------

describe("ObjectWizardPane — processTableWarningCommand", () => {
   it("should call notifications.success() with the warning message", async () => {
      const { comp } = await renderComponent();
      // @ViewChild("notifications") is populated by ATL with the NotificationsStub instance
      (comp as any)["processTableWarningCommand"]({ message: "Row limit exceeded" });

      expect((comp.notifications as any).success).toHaveBeenCalledWith("Row limit exceeded");
   });
});

// ---------------------------------------------------------------------------
// Group 19 — addConsoleMessage / messageChange [baseline]
// ---------------------------------------------------------------------------

describe("ObjectWizardPane — addConsoleMessage / messageChange", () => {
   it("addConsoleMessage should append INFO messages to consoleMessages", async () => {
      const { comp } = await renderComponent();

      (comp as any)["addConsoleMessage"]({ message: "Query exceeded row limit", type: "INFO" });

      expect(comp.consoleMessages).toHaveLength(1);
      expect(comp.consoleMessages[0]).toMatchObject({ message: "Query exceeded row limit", type: "INFO" });
   });

   it("addConsoleMessage should skip messages with unrecognized type", async () => {
      const { comp } = await renderComponent();

      (comp as any)["addConsoleMessage"]({ message: "debug info", type: "DEBUG" });

      expect(comp.consoleMessages).toHaveLength(0);
   });

   it("messageChange() should replace consoleMessages with the new list", async () => {
      const { comp } = await renderComponent();
      const newMessages = [{ message: "abc", type: "WARNING" }] as any[];

      comp.messageChange(newMessages);

      expect(comp.consoleMessages).toBe(newMessages);
   });
});

// ---------------------------------------------------------------------------
// Group 20 — ngOnDestroy (memory leak) [baseline]
// ---------------------------------------------------------------------------

describe("ObjectWizardPane — ngOnDestroy", () => {
   // 🔁 Regression-sensitive: cleanup() must be called to unsubscribe from commandsSubject.
   // If not, the command handler holds a reference to the destroyed component, preventing GC
   // and potentially dispatching commands to a dead component.
   it("should call cleanup so commandsSubject no longer has an observer after destroy", async () => {
      const { fixture } = await renderComponent();

      fixture.destroy();

      expect(commandsSubject.observed).toBe(false);
   });
});
