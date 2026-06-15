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
 * VSBindingPane — Pass 1: interaction
 *
 * Risk-first coverage:
 *   Group 1  [Risk 3] — processAddVSObjectCommand: name guard, objectModel/blocking reset, AI service call
 *   Group 2  [Risk 2] — processRefreshVSObjectCommand: name guard, no-model guard, objectModel/blocking replace
 *   Group 3  [Risk 2] — processRefreshBindingTreeCommand: resets tree model, sets loading=false
 *   Group 4  [Risk 2] — processSetGrayedOutFieldsCommand / processRenameVSObjectCommand: field replace, name guard
 *   Group 5  [Risk 2] — processAssemblyChangedCommand: emits only when pane is closed
 *   Group 6  [Risk 2] — updateData routing: getCurrentFormat / getTextFormat / updateFormat / reset / unknown
 *   Group 7  [Risk 2] — goToWizardVisible: hasDynamic / inEmbeddedViewsheet / isCube guards
 *   Group 8  [it.fails] — hasExpression: method-reference bug blocks chart branch
 *   Group 9  [baseline] — sourceName / originalMode / haveDynamicBinding getters
 *   Group 10 [baseline] — isChart / isCrosstab / getBindingType mapping
 *   Group 11 [baseline] — messageChange: replaces consoleMessages
 *
 * Confirmed bugs (it.fails):
 *   hasExpression — `this.isCrosstab` is used as a method reference (without `()`) in the
 *     branch condition. A method reference is always truthy, so the isCrosstab branch always
 *     runs when bindingModel is set. For a VSChart assembly, crosstab.rows is undefined →
 *     returns false even when xfields contain an expression value.
 *
 * Suspected bugs (header only): none
 *
 * Out of scope this pass:
 *   closeHandler / closeHandler0 / goToWizard / openWizardPane — require modal + setTimeout
 *   processSetVSBindingModelCommand — requires live STOMP round-trip to verify tree reload
 *   processOpenEditGeographicCommand — requires ComponentTool.showDialog + GeoProvider wiring
 *   processVSTrapCommand / processVSBindingTrapCommand — async modal branches, covered in P2
 *   processProgress / processMessageCommand — modal + STOMP
 */

import { waitFor } from "@testing-library/angular";

import { Tool } from "../../../../../shared/util/tool";
import { ChartTool } from "../../graph/model/chart-tool";
import { VsWizardEditModes } from "../../vs-wizard/model/vs-wizard-edit-modes";
import { AddVSObjectCommand, AddVsObjectMode } from "../../vsobjects/command/add-vs-object-command";
import { RefreshVSObjectCommand } from "../../vsobjects/command/refresh-vs-object-command";
import { AssemblyChangedCommand } from "../../vs-wizard/model/command/assembly-changed-command";
import { RenameVSObjectCommand } from "../../vsobjects/command/rename-vs-object-command";
import { SetGrayedOutFieldsCommand } from "../../binding/command/set-grayed-out-fields-command";
import { RefreshBindingTreeCommand } from "../../binding/command/refresh-binding-tree-command";
import { TestUtils } from "../../common/test/test-utils";
import {
   CLIENT_SERVICE_MOCK, MODEL_SERVICE_MOCK, TREE_SERVICE_MOCK, MODAL_MOCK,
   renderComponent, resetMocks,
} from "./vs-binding-pane.test-fixtures";

beforeEach(() => resetMocks());

// ---------------------------------------------------------------------------
// Group 1 — processAddVSObjectCommand [Risk 3]
// ---------------------------------------------------------------------------

describe("VSBindingPane — processAddVSObjectCommand", () => {
   // 🔁 Regression-sensitive: the name guard prevents the binding pane from updating its
   // model when a different assembly's STOMP command arrives on the same channel.

   it("should ignore the command when absoluteName does not match assemblyName", async () => {
      const { comp } = await renderComponent({ assemblyName: "Chart1" });
      const cmd: AddVSObjectCommand = {
         model: TestUtils.createMockVSChartModel("OTHER"),
         name: "OTHER",
         mode: AddVsObjectMode.DESIGN_MODE,
         parent: null,
      };

      comp.processAddVSObjectCommand(cmd);

      expect(comp.objectModel).toBeUndefined();
   });

   it("should set objectModel and reset blocking when absoluteName matches and no prior model exists", async () => {
      const { comp } = await renderComponent({ assemblyName: "Chart1" });
      const model = TestUtils.createMockVSChartModel("Chart1");
      const cmd: AddVSObjectCommand = {
         model,
         name: "Chart1",
         mode: AddVsObjectMode.DESIGN_MODE,
         parent: null,
      };

      comp.processAddVSObjectCommand(cmd);

      expect(comp.objectModel).toBe(model);
      expect(comp.blocking).toBe(false);
   });

   it("should call ai services on updateObjectModel with the new model type", async () => {
      const { comp } = await renderComponent({ assemblyName: "Chart1" });
      const model = TestUtils.createMockVSChartModel("Chart1");

      comp.processAddVSObjectCommand({
         model,
         name: "Chart1",
         mode: AddVsObjectMode.DESIGN_MODE,
         parent: null,
      });

      expect(MODEL_SERVICE_MOCK.getModel).toHaveBeenCalledWith(
         expect.stringContaining("vsbinding/open"), expect.anything());
   });
});

// ---------------------------------------------------------------------------
// Group 2 — processRefreshVSObjectCommand [Risk 2]
// ---------------------------------------------------------------------------

describe("VSBindingPane — processRefreshVSObjectCommand", () => {
   it("should ignore the command when absoluteName does not match assemblyName", async () => {
      const { comp } = await renderComponent({ assemblyName: "Chart1" });
      const chart = TestUtils.createMockVSChartModel("Chart1");
      comp.objectModel = chart;

      const cmd = {
         info: TestUtils.createMockVSChartModel("OTHER"),
         force: false,
         model: TestUtils.createMockVSChartModel("OTHER"),
      } as any as RefreshVSObjectCommand;

      comp.processRefreshVSObjectCommand(cmd);

      // objectModel unchanged — same reference
      expect(comp.objectModel).toBe(chart);
   });

   it("should be a no-op when no existing objectModel is set", async () => {
      const { comp } = await renderComponent({ assemblyName: "Chart1" });
      const newInfo = TestUtils.createMockVSChartModel("Chart1");
      const cmd = { info: newInfo, force: false, model: newInfo } as any as RefreshVSObjectCommand;

      // objectModel is undefined after render; the if(!!this.objectModel) guard protects the update.
      comp.processRefreshVSObjectCommand(cmd);

      expect(comp.objectModel).toBeUndefined();
   });

   it("should replace objectModel and reset blocking when names match and model exists", async () => {
      const { comp } = await renderComponent({ assemblyName: "Chart1" });
      const existing = TestUtils.createMockVSChartModel("Chart1");
      comp.objectModel = existing;
      CLIENT_SERVICE_MOCK.sendEvent.mockClear();

      const refreshed = TestUtils.createMockVSChartModel("Chart1");
      const cmd = { info: refreshed, force: false, model: refreshed } as any as RefreshVSObjectCommand;

      comp.processRefreshVSObjectCommand(cmd);

      expect(comp.objectModel).toBe(refreshed);
      expect(comp.blocking).toBe(false);
   });
});

// ---------------------------------------------------------------------------
// Group 3 — processRefreshBindingTreeCommand [Risk 2]
// ---------------------------------------------------------------------------

describe("VSBindingPane — processRefreshBindingTreeCommand", () => {
   it("should reset the tree model and set loading=false", async () => {
      const { comp } = await renderComponent();
      const treeModel = { label: "root", leaf: false, children: [] } as any;
      const cmd: RefreshBindingTreeCommand = { treeModel };

      // Bypass public access restriction — processRefreshBindingTreeCommand is private.
      (comp as any).processRefreshBindingTreeCommand(cmd);

      expect(TREE_SERVICE_MOCK.resetTreeModel).toHaveBeenCalledWith(treeModel);
      expect(TREE_SERVICE_MOCK.changeLoadingState).toHaveBeenCalledWith(false);
   });
});

// ---------------------------------------------------------------------------
// Group 4 — processSetGrayedOutFieldsCommand + processRenameVSObjectCommand [Risk 2]
// ---------------------------------------------------------------------------

describe("VSBindingPane — processSetGrayedOutFieldsCommand", () => {
   it("should replace grayedOutFields with the command's fields", async () => {
      const { comp } = await renderComponent();
      const fields = [{ attribute: "Sales", dataType: "double" } as any];
      const cmd: SetGrayedOutFieldsCommand = { fields };

      // Bypass: processSetGrayedOutFieldsCommand is private.
      (comp as any).processSetGrayedOutFieldsCommand(cmd);

      expect(comp.grayedOutFields).toBe(fields);
   });
});

describe("VSBindingPane — processRenameVSObjectCommand", () => {
   // 🔁 Regression-sensitive: if the rename guard is wrong, the binding pane could silently
   // apply a name update for a different assembly, breaking all subsequent STOMP routing.

   it("should update assemblyName and objectModel.absoluteName when oldName matches", async () => {
      const { comp } = await renderComponent({ assemblyName: "Chart1" });
      const model = TestUtils.createMockVSChartModel("Chart1");
      comp.objectModel = model;
      const cmd: RenameVSObjectCommand = { oldName: "Chart1", newName: "ChartRenamed" };

      // Bypass: processRenameVSObjectCommand is private; called via STOMP dispatch in production.
      (comp as any).processRenameVSObjectCommand(cmd);

      expect(comp.assemblyName).toBe("ChartRenamed");
      expect(comp.objectModel.absoluteName).toBe("ChartRenamed");
   });

   it("should be a no-op when oldName does not match assemblyName", async () => {
      const { comp } = await renderComponent({ assemblyName: "Chart1" });
      const model = TestUtils.createMockVSChartModel("Chart1");
      comp.objectModel = model;
      const cmd: RenameVSObjectCommand = { oldName: "OTHER", newName: "Renamed" };

      (comp as any).processRenameVSObjectCommand(cmd);

      expect(comp.assemblyName).toBe("Chart1");
      expect(comp.objectModel.absoluteName).toBe("Chart1");
   });
});

// ---------------------------------------------------------------------------
// Group 5 — processAssemblyChangedCommand [Risk 2]
// ---------------------------------------------------------------------------

describe("VSBindingPane — processAssemblyChangedCommand", () => {
   it("should emit onAssemblyChanged only when the pane is closed", async () => {
      const { comp } = await renderComponent();
      const cmd: AssemblyChangedCommand = {} as AssemblyChangedCommand;
      const emitted: AssemblyChangedCommand[] = [];
      comp.onAssemblyChanged.subscribe(c => emitted.push(c));

      // Not yet closed → no emit
      comp.processAssemblyChangedCommand(cmd);
      expect(emitted).toHaveLength(0);

      // Mark as closed, then call again → emits
      (comp as any).closed = true;
      comp.processAssemblyChangedCommand(cmd);
      expect(emitted).toHaveLength(1);
      expect(emitted[0]).toBe(cmd);
   });
});

// ---------------------------------------------------------------------------
// Group 6 — updateData routing [Risk 2]
// ---------------------------------------------------------------------------

describe("VSBindingPane — updateData", () => {
   // updateData routes format-related events to the underlying format methods which all
   // call clientService.sendEvent. objectModel must be set so the guard in getCurrentFormat
   // / updateFormat is satisfied.

   async function setupWithModel() {
      const { comp, fixture } = await renderComponent();
      comp.objectModel = TestUtils.createMockVSChartModel("Chart1");
      CLIENT_SERVICE_MOCK.sendEvent.mockClear(); // clear the ngOnInit call
      return { comp, fixture };
   }

   it("should dispatch a getFormat event for 'getCurrentFormat'", async () => {
      const { comp } = await setupWithModel();

      comp.updateData("getCurrentFormat");

      expect(CLIENT_SERVICE_MOCK.sendEvent).toHaveBeenCalledWith(
         "/events/composer/viewsheet/getFormat",
         expect.objectContaining({ binding: true }),
      );
   });

   it("should dispatch a getFormat (text) event for 'getTextFormat'", async () => {
      const { comp } = await setupWithModel();

      comp.updateData("getTextFormat");

      expect(CLIENT_SERVICE_MOCK.sendEvent).toHaveBeenCalledWith(
         "/events/composer/viewsheet/getFormat",
         expect.objectContaining({ binding: true }),
      );
   });

   it("should dispatch a format event for 'updateFormat'", async () => {
      const { comp } = await setupWithModel();

      comp.updateData("updateFormat");

      expect(CLIENT_SERVICE_MOCK.sendEvent).toHaveBeenCalledWith(
         "/events/composer/viewsheet/format",
         expect.any(Object),
      );
   });

   it("should dispatch a reset format event for 'reset' and set resetFormat=true", async () => {
      const { comp } = await setupWithModel();

      comp.updateData("reset");

      // reset=true on the format event means "clear the format" (null fmt)
      expect(CLIENT_SERVICE_MOCK.sendEvent).toHaveBeenCalledWith(
         "/events/composer/viewsheet/format",
         expect.objectContaining({ reset: true }),
      );
   });

   it("should be a no-op for an unknown event string", async () => {
      const { comp } = await setupWithModel();

      comp.updateData("unknownEvent");

      expect(CLIENT_SERVICE_MOCK.sendEvent).not.toHaveBeenCalled();
   });
});

// ---------------------------------------------------------------------------
// Group 7 — goToWizardVisible [Risk 2]
// ---------------------------------------------------------------------------

describe("VSBindingPane — goToWizardVisible", () => {
   // 🔁 Regression-sensitive: goToWizardVisible controls whether the "Go to Wizard" button
   // appears. Showing it for embedded or dynamic assemblies would break the wizard flow.

   it("should return false for a non-qualifying assembly type (VSText)", async () => {
      const { comp } = await renderComponent({ objectType: "VSText" });
      comp.objectModel = TestUtils.createMockVSObjectModel("VSText", "Text1");

      expect(comp.goToWizardVisible).toBe(false);
   });

   it("should return true for a chart with no dynamic binding, not embedded, isCube=false", async () => {
      const { comp } = await renderComponent();
      const chart = TestUtils.createMockVSChartModel("Chart1");
      chart.hasDynamic = false;
      chart.inEmbeddedViewsheet = false;
      comp.objectModel = chart;
      comp.bindingModel = null;

      expect(comp.goToWizardVisible).toBe(true);
   });

   it("should return false when the assembly has dynamic binding", async () => {
      const { comp } = await renderComponent();
      const chart = TestUtils.createMockVSChartModel("Chart1");
      chart.hasDynamic = true;
      comp.objectModel = chart;

      expect(comp.goToWizardVisible).toBe(false);
   });

   it("should return false when the assembly is inside an embedded viewsheet", async () => {
      const { comp } = await renderComponent();
      const chart = TestUtils.createMockVSChartModel("Chart1");
      chart.hasDynamic = false;
      chart.inEmbeddedViewsheet = true;
      comp.objectModel = chart;

      expect(comp.goToWizardVisible).toBe(false);
   });

   it("should return false when isCube=true", async () => {
      const { comp } = await renderComponent({ isCube: true });
      const chart = TestUtils.createMockVSChartModel("Chart1");
      chart.hasDynamic = false;
      chart.inEmbeddedViewsheet = false;
      comp.objectModel = chart;

      expect(comp.goToWizardVisible).toBe(false);
   });
});

// ---------------------------------------------------------------------------
// Group 8 — hasExpression (known bug) [it.fails]
// ---------------------------------------------------------------------------

describe("VSBindingPane — hasExpression", () => {
   // Expected failure: hasExpression() uses `this.isCrosstab` (a private method reference,
   // without '()') as the branch condition. A method reference is always truthy, so the
   // `if(this.isCrosstab && bindingModel)` block always enters when bindingModel is set.
   // For a VSChart assembly, this means the isCrosstab branch runs, accesses crosstab.rows
   // (undefined on a chart binding model) → null-guarded → returns false, even though the
   // chart's xfields contain an expression.
   //
   // Expected failure point: the final `expect(...).toBe(true)` fails because the isCrosstab
   // branch preempts the isChart branch, returning false when it should return true.
   // If this it.fails suddenly passes for an unrelated reason (e.g., binding model throws),
   // look for a TypeError in the test output — that would be a different failure mode.
   it.fails(
      "should return true for VSChart with expression in xfields (blocked by method-reference bug)",
      async () => {
         const { comp } = await renderComponent();
         comp.objectModel = TestUtils.createMockVSChartModel("Chart1");
         // Chart binding model where xfields[0] has an expression value (starts with '=').
         const noExprRef = { dataInfo: { columnValue: "Sales" } } as any;
         comp.bindingModel = {
            xfields: [{ columnValue: "=SUM(Sales)" }],
            yfields: [],
            groupFields: [],
            colorField: noExprRef,
            shapeField: noExprRef,
            sizeField: noExprRef,
         } as any;

         // Expected: true (isChart branch should detect "=SUM(Sales)" in xfields)
         // Actual: false (isCrosstab branch runs instead, crosstab.rows is undefined → false)
         expect((comp as any).hasExpression()).toBe(true);
      }
   );
});

// ---------------------------------------------------------------------------
// Group 9 — sourceName / originalMode / haveDynamicBinding [baseline]
// ---------------------------------------------------------------------------

describe("VSBindingPane — sourceName getter", () => {
   it("should delegate to Tool.getCurrentSourceLabel with the current bindingModel", async () => {
      const { comp } = await renderComponent();
      const spy = vi.spyOn(Tool, "getCurrentSourceLabel").mockReturnValue("Orders");
      try {
         expect(comp.sourceName).toBe("Orders");
         expect(spy).toHaveBeenCalledWith(comp.bindingModel);
      } finally {
         spy.mockRestore();
      }
   });
});

describe("VSBindingPane — originalMode getter", () => {
   it("should return null when wizardOriginalInfo is not set", async () => {
      const { comp } = await renderComponent({ wizardOriginalInfo: null });
      expect(comp.originalMode).toBeNull();
   });

   it("should return editMode from wizardOriginalInfo when set", async () => {
      const info = {
         runtimeId: "vs-1",
         objectType: "VSChart",
         absoluteName: "Chart1",
         editMode: VsWizardEditModes.FULL_EDITOR,
      };
      const { comp } = await renderComponent({ wizardOriginalInfo: info });
      expect(comp.originalMode).toBe(VsWizardEditModes.FULL_EDITOR);
   });
});

describe("VSBindingPane — haveDynamicBinding getter", () => {
   it("should return false when no objectModel is set", async () => {
      const { comp } = await renderComponent();
      expect(comp.haveDynamicBinding).toBe(false);
   });

   it("should return objectModel.hasDynamic when objectModel is set", async () => {
      const { comp } = await renderComponent();
      const chart = TestUtils.createMockVSChartModel("Chart1");
      chart.hasDynamic = true;
      comp.objectModel = chart;
      expect(comp.haveDynamicBinding).toBe(true);
   });
});

// ---------------------------------------------------------------------------
// Group 10 — isChart / isCrosstab / getBindingType [baseline]
// ---------------------------------------------------------------------------

describe("VSBindingPane — isChart / isCrosstab", () => {
   it("isChart should return true only for VSChart objectModel", async () => {
      const { comp } = await renderComponent();
      comp.objectModel = TestUtils.createMockVSChartModel("Chart1");
      expect((comp as any).isChart()).toBe(true);

      const table = TestUtils.createMockVSTableModel("Table1");
      comp.objectModel = table;
      expect((comp as any).isChart()).toBe(false);
   });

   it("isCrosstab should return true only for VSCrosstab objectModel", async () => {
      const { comp } = await renderComponent();
      const crosstab = TestUtils.createMockVSCrosstabModel("Crosstab1");
      // createMockBaseTableModel hardcodes objectType "VSTable"; override so the guard matches.
      crosstab.objectType = "VSCrosstab";
      comp.objectModel = crosstab;
      expect((comp as any).isCrosstab()).toBe(true);

      comp.objectModel = TestUtils.createMockVSChartModel("Chart1");
      expect((comp as any).isCrosstab()).toBe(false);
   });
});

describe("VSBindingPane — getBindingType", () => {
   it("should return 'Chart' for VSChart", async () => {
      const { comp } = await renderComponent({ objectType: "VSChart" });
      expect((comp as any).getBindingType()).toBe("Chart");
   });

   it("should return 'Table' for VSTable", async () => {
      const { comp } = await renderComponent({ objectType: "VSTable" });
      expect((comp as any).getBindingType()).toBe("Table");
   });

   it("should return 'Crosstab' for VSCrosstab", async () => {
      const { comp } = await renderComponent({ objectType: "VSCrosstab" });
      expect((comp as any).getBindingType()).toBe("Crosstab");
   });

   it("should return 'FreehandTable' for VSCalcTable", async () => {
      const { comp } = await renderComponent({ objectType: "VSCalcTable" });
      expect((comp as any).getBindingType()).toBe("FreehandTable");
   });

   it("should return null for an unrecognized objectType", async () => {
      const { comp } = await renderComponent({ objectType: "VSText" });
      expect((comp as any).getBindingType()).toBeNull();
   });
});

// ---------------------------------------------------------------------------
// Group 11 — messageChange [baseline]
// ---------------------------------------------------------------------------

describe("VSBindingPane — messageChange", () => {
   it("should replace consoleMessages with the provided array", async () => {
      const { comp } = await renderComponent();
      const msgs = [{ message: "Row limit exceeded", type: "INFO" as const }];

      comp.messageChange(msgs);

      expect(comp.consoleMessages).toBe(msgs);
   });
});
