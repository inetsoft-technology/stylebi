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
 * BindingEditor — P1 Interaction
 *
 * Risk-first coverage:
 *   Group 1  [Risk 2] — assemblyName setter: propagates to bindingService
 *   Group 2  [Risk 3] — bindingModel setter: propagates to bindingService, updates showDragTip
 *   Group 3  [Risk 2] — variableValues setter: propagates to bindingService
 *   Group 4  [Risk 2] — grayedOutFields setter: calls setGrayedOutFields, updates grayedOutValues
 *   Group 5  [baseline] — switchTab(): updates selectedTab
 *   Group 6  [Risk 3] — updateData(): six branches in the switch (getCurrentFormat,
 *                         showTextFormat, getTextFormat, hideFormatPane, openFormatPane, default)
 *   Group 7  [Risk 2] — updateFormat(): emits updateFormat or reset depending on model presence
 *   Group 8  [baseline] — updateChartMaxMode(): sets chartMaxMode field
 *   Group 9  [baseline] — changeMessage(): updates consoleMessages, emits onMessageChange
 *   Group 10 [Risk 2] — ngOnInit(): initializes bindingService fields; sets showDcAppliedTip
 *                         for chart/crosstab binding models with hasDateComparison=true
 *   Group 11 [baseline] — ngAfterViewInit(): emits onInitialized
 *   Group 12 [baseline +memory-leak] — ngOnDestroy(): calls bindingService.clear()
 *   Group 13 [Risk 2] — openConsoleDialog(): calls ModelService.getModel(), sets messageLevels,
 *                         opens modal with the console-dialog ViewChild reference
 *
 * Out of scope (P3):
 *   showHighLowPane() — pure computed from chartType/supportsPathField flags (display)
 *   isBound() — private helper tested indirectly via bindingModel.showDragTip
 *   popUpWarning() — calls ViewChild notifications.info/warning/danger
 *   sizeChanged() — calls ViewChild notifications.info
 *   formatsInactive / formatsDisabled / formatPaneVisible — pure boolean getters (display)
 *   miniToolbarHeight / bindingType / isVS / hideDcTip / tableBindingModel / crosstabBindingModel
 *     — pure computed getters (display)
 */

import { of } from "rxjs";

import {
   BINDING_SERVICE_MOCK,
   MODEL_SERVICE_MOCK,
   MODAL_MOCK,
   resetMocks,
   renderComponent,
} from "./binding-editor.component.test-fixtures";
import { SidebarTab } from "../widget/binding-tree/data-editor-tab-pane.component";

import type { BindingModel } from "../data/binding-model";
import type { DataRef } from "../../common/data/data-ref";
import type { ConsoleMessage } from "../../widget/console-dialog/console-message";
import type { FormatInfoModel } from "../../common/data/format-info-model";

// ---------------------------------------------------------------------------
// Global setup
// ---------------------------------------------------------------------------

beforeEach(() => {
   resetMocks();
});

afterEach(() => {
   vi.restoreAllMocks();
});

// ---------------------------------------------------------------------------
// Group 1 — assemblyName setter [Risk 2]
// ---------------------------------------------------------------------------

describe("BindingEditor — assemblyName setter", () => {
   it("should propagate the assemblyName to bindingService", async () => {
      const { comp } = await renderComponent();
      comp.assemblyName = "NewAssembly";
      expect(BINDING_SERVICE_MOCK.assemblyName).toBe("NewAssembly");
   });

   it("should expose the stored assemblyName via the getter", async () => {
      const { comp } = await renderComponent({ assemblyName: "InitialAssembly" });
      expect(comp.assemblyName).toBe("InitialAssembly");
   });
});

// ---------------------------------------------------------------------------
// Group 2 — bindingModel setter [Risk 3]
// ---------------------------------------------------------------------------

describe("BindingEditor — bindingModel setter", () => {
   // 🔁 Regression-sensitive: showDragTip drives the drag-hint overlay;
   // an incorrect value leaves the hint visible after the user has already bound fields.

   it("should propagate the model to bindingService", async () => {
      const model = { type: "chart" } as unknown as BindingModel;
      const { comp } = await renderComponent({ bindingModel: model });
      comp.bindingModel = model;
      expect(BINDING_SERVICE_MOCK.bindingModel).toBe(model);
   });

   it("should set showDragTip=true when bindingModel is null (nothing bound yet)", async () => {
      const { comp } = await renderComponent();
      comp.bindingModel = null;
      expect(comp.showDragTip).toBe(true);
   });

   it("should set showDragTip=false when bindingModel.type is 'calctable' (always considered bound)", async () => {
      const { comp } = await renderComponent();
      comp.bindingModel = { type: "calctable" } as unknown as BindingModel;
      expect(comp.showDragTip).toBe(false);
   });

   it("should set showDragTip=false when chart has at least one xfield", async () => {
      const { comp } = await renderComponent();
      comp.bindingModel = {
         type: "chart",
         xfields: [{ name: "x1" }],
         yfields: [],
      } as unknown as BindingModel;
      expect(comp.showDragTip).toBe(false);
   });

   it("should set showDragTip=true when chart has no bound fields at all", async () => {
      const { comp } = await renderComponent();
      comp.bindingModel = {
         type: "chart",
         xfields: [], yfields: [], geoFields: [], groupFields: [], geoCols: [],
         colorField: null, shapeField: null, sizeField: null, textField: null,
         pathField: null, openField: null, closeField: null, highField: null, lowField: null,
         sourceField: null, targetField: null, startField: null, endField: null,
         milestoneField: null,
      } as unknown as BindingModel;
      expect(comp.showDragTip).toBe(true);
   });

   it("should set showDragTip=false when table binding has at least one group", async () => {
      const { comp } = await renderComponent();
      comp.bindingModel = {
         type: "table",
         groups: [{ name: "g1" }],
         details: [],
         aggregates: [],
      } as unknown as BindingModel;
      expect(comp.showDragTip).toBe(false);
   });

   it("should set showDragTip=false when crosstab binding has at least one row", async () => {
      const { comp } = await renderComponent();
      comp.bindingModel = {
         type: "crosstab",
         rows: [{ name: "r1" }],
         cols: [],
         aggregates: [],
      } as unknown as BindingModel;
      expect(comp.showDragTip).toBe(false);
   });
});

// ---------------------------------------------------------------------------
// Group 3 — variableValues setter [Risk 2]
// ---------------------------------------------------------------------------

describe("BindingEditor — variableValues setter", () => {
   it("should propagate variableValues to bindingService", async () => {
      const { comp } = await renderComponent();
      comp.variableValues = ["v1", "v2"];
      expect(BINDING_SERVICE_MOCK.variableValues).toEqual(["v1", "v2"]);
   });

   it("should expose the stored variableValues via the getter", async () => {
      const { comp } = await renderComponent();
      comp.variableValues = ["x"];
      expect(comp.variableValues).toEqual(["x"]);
   });
});

// ---------------------------------------------------------------------------
// Group 4 — grayedOutFields setter [Risk 2]
// ---------------------------------------------------------------------------

describe("BindingEditor — grayedOutFields setter", () => {
   it("should call bindingService.setGrayedOutFields with the provided fields", async () => {
      const { comp } = await renderComponent();
      const fields = [{ name: "field1" }, { name: "field2" }] as DataRef[];
      comp.grayedOutFields = fields;
      expect(BINDING_SERVICE_MOCK.setGrayedOutFields).toHaveBeenCalledWith(fields);
   });

   it("should populate grayedOutValues with field names when fields are provided", async () => {
      const { comp } = await renderComponent();
      comp.grayedOutFields = [{ name: "alpha" }, { name: "beta" }] as DataRef[];
      expect(comp.grayedOutValues).toEqual(["alpha", "beta"]);
   });

   it("should set grayedOutValues to empty array when fields array is empty", async () => {
      const { comp } = await renderComponent();
      comp.grayedOutFields = [] as DataRef[];
      expect(comp.grayedOutValues).toEqual([]);
   });

   it("should call setGrayedOutFields but not clear grayedOutValues when fields is null", async () => {
      const { comp } = await renderComponent();
      comp.grayedOutValues = ["existing"];
      comp.grayedOutFields = null;
      expect(BINDING_SERVICE_MOCK.setGrayedOutFields).toHaveBeenCalledWith(null);
      expect(comp.grayedOutValues).toEqual(["existing"]);
   });
});

// ---------------------------------------------------------------------------
// Group 5 — switchTab() [baseline]
// ---------------------------------------------------------------------------

describe("BindingEditor — switchTab()", () => {
   it("should update selectedTab to FORMAT_PANE", async () => {
      const { comp } = await renderComponent();
      comp.switchTab(SidebarTab.FORMAT_PANE);
      expect(comp.selectedTab).toBe(SidebarTab.FORMAT_PANE);
   });

   it("should update selectedTab back to BINDING_TREE", async () => {
      const { comp } = await renderComponent();
      comp.switchTab(SidebarTab.FORMAT_PANE);
      comp.switchTab(SidebarTab.BINDING_TREE);
      expect(comp.selectedTab).toBe(SidebarTab.BINDING_TREE);
   });
});

// ---------------------------------------------------------------------------
// Group 6 — updateData() [Risk 3]
// ---------------------------------------------------------------------------

describe("BindingEditor — updateData() switch branches", () => {
   // 🔁 Regression-sensitive: each branch has distinct side effects (hideFormatPane,
   // selectedTab, onUpdateData payload). A wrong branch selection causes the format pane
   // to stay hidden / switch to the wrong tab silently.

   // --- 'getCurrentFormat' ---

   it("should set hideFormatPane=false when action is 'getCurrentFormat'", async () => {
      const { comp } = await renderComponent();
      comp.hideFormatPane = true;
      comp.updateData("getCurrentFormat");
      expect(comp.hideFormatPane).toBe(false);
   });

   it("should emit 'getCurrentFormat' when action is 'getCurrentFormat'", async () => {
      const { comp } = await renderComponent();
      const emitted: string[] = [];
      comp.onUpdateData.subscribe((v: string) => emitted.push(v));
      comp.updateData("getCurrentFormat");
      expect(emitted).toEqual(["getCurrentFormat"]);
   });

   // --- 'showTextFormat' ---

   it("should switch to FORMAT_PANE when action is 'showTextFormat'", async () => {
      const { comp } = await renderComponent();
      comp.updateData("showTextFormat");
      expect(comp.selectedTab).toBe(SidebarTab.FORMAT_PANE);
   });

   it("should set hideFormatPane=false when action is 'showTextFormat'", async () => {
      const { comp } = await renderComponent();
      comp.hideFormatPane = true;
      comp.updateData("showTextFormat");
      expect(comp.hideFormatPane).toBe(false);
   });

   it("should emit 'getTextFormat' when action is 'showTextFormat'", async () => {
      const { comp } = await renderComponent();
      const emitted: string[] = [];
      comp.onUpdateData.subscribe((v: string) => emitted.push(v));
      comp.updateData("showTextFormat");
      expect(emitted).toEqual(["getTextFormat"]);
   });

   // --- 'getTextFormat' ---

   it("should set hideFormatPane=false when action is 'getTextFormat'", async () => {
      const { comp } = await renderComponent();
      comp.hideFormatPane = true;
      comp.updateData("getTextFormat");
      expect(comp.hideFormatPane).toBe(false);
   });

   it("should emit 'getTextFormat' when action is 'getTextFormat'", async () => {
      const { comp } = await renderComponent();
      const emitted: string[] = [];
      comp.onUpdateData.subscribe((v: string) => emitted.push(v));
      comp.updateData("getTextFormat");
      expect(emitted).toEqual(["getTextFormat"]);
   });

   // --- 'hideFormatPane' ---

   it("should set hideFormatPane=true when action is 'hideFormatPane'", async () => {
      const { comp } = await renderComponent();
      comp.hideFormatPane = false;
      comp.updateData("hideFormatPane");
      expect(comp.hideFormatPane).toBe(true);
   });

   it("should not emit onUpdateData when action is 'hideFormatPane'", async () => {
      const { comp } = await renderComponent();
      const emitted: string[] = [];
      comp.onUpdateData.subscribe((v: string) => emitted.push(v));
      comp.updateData("hideFormatPane");
      expect(emitted).toHaveLength(0);
   });

   // --- 'openFormatPane' ---

   it("should switch to FORMAT_PANE when action is 'openFormatPane'", async () => {
      const { comp } = await renderComponent();
      comp.updateData("openFormatPane");
      expect(comp.selectedTab).toBe(SidebarTab.FORMAT_PANE);
   });

   it("should set hideFormatPane=false when action is 'openFormatPane'", async () => {
      const { comp } = await renderComponent();
      comp.hideFormatPane = true;
      comp.updateData("openFormatPane");
      expect(comp.hideFormatPane).toBe(false);
   });

   it("should emit 'getCurrentFormat' when action is 'openFormatPane'", async () => {
      const { comp } = await renderComponent();
      const emitted: string[] = [];
      comp.onUpdateData.subscribe((v: string) => emitted.push(v));
      comp.updateData("openFormatPane");
      expect(emitted).toEqual(["getCurrentFormat"]);
   });

   // --- default ---

   it("default case should emit the action string unchanged", async () => {
      const { comp } = await renderComponent();
      const emitted: string[] = [];
      comp.onUpdateData.subscribe((v: string) => emitted.push(v));

      comp.updateData("someCustomAction");

      expect(emitted).toEqual(["someCustomAction"]);
   });
});

// ---------------------------------------------------------------------------
// Group 7 — updateFormat() [Risk 2]
// ---------------------------------------------------------------------------

describe("BindingEditor — updateFormat()", () => {
   it("should emit 'updateFormat' when a truthy model is provided", async () => {
      const { comp } = await renderComponent();
      const emitted: string[] = [];
      comp.onUpdateData.subscribe((v: string) => emitted.push(v));

      comp.updateFormat({ id: "fmt1" } as unknown as FormatInfoModel);

      expect(emitted).toEqual(["updateFormat"]);
   });

   it("should emit 'reset' when model is null/falsy", async () => {
      const { comp } = await renderComponent();
      const emitted: string[] = [];
      comp.onUpdateData.subscribe((v: string) => emitted.push(v));

      comp.updateFormat(null);

      expect(emitted).toEqual(["reset"]);
   });
});

// ---------------------------------------------------------------------------
// Group 8 — updateChartMaxMode() [baseline]
// ---------------------------------------------------------------------------

describe("BindingEditor — updateChartMaxMode()", () => {
   it("should set chartMaxMode to true when event.maxMode is true", async () => {
      const { comp } = await renderComponent();
      comp.updateChartMaxMode({ assembly: "Chart1", maxMode: true });
      expect(comp.chartMaxMode).toBe(true);
   });

   it("should set chartMaxMode to false when event.maxMode is false", async () => {
      const { comp } = await renderComponent();
      comp.chartMaxMode = true;
      comp.updateChartMaxMode({ assembly: "Chart1", maxMode: false });
      expect(comp.chartMaxMode).toBe(false);
   });
});

// ---------------------------------------------------------------------------
// Group 9 — changeMessage() [baseline]
// ---------------------------------------------------------------------------

describe("BindingEditor — changeMessage()", () => {
   it("should update consoleMessages with the provided messages", async () => {
      const { comp } = await renderComponent();
      const msgs = [{ message: "Msg1", type: "WARN" }] as unknown as ConsoleMessage[];
      comp.changeMessage(msgs);
      expect(comp.consoleMessages).toBe(msgs);
   });

   it("should emit onMessageChange with the provided messages", async () => {
      const { comp } = await renderComponent();
      const emitted: ConsoleMessage[][] = [];
      comp.onMessageChange.subscribe((v: ConsoleMessage[]) => emitted.push(v));
      const msgs = [{ message: "Msg1", type: "WARN" }] as unknown as ConsoleMessage[];
      comp.changeMessage(msgs);
      expect(emitted).toHaveLength(1);
      expect(emitted[0]).toBe(msgs);
   });
});

// ---------------------------------------------------------------------------
// Group 10 — ngOnInit() [Risk 2]
// ---------------------------------------------------------------------------

describe("BindingEditor — ngOnInit()", () => {
   it("should initialize bindingService.runtimeId from the runtimeId input", async () => {
      await renderComponent({ runtimeId: "runtime-xyz" });
      expect(BINDING_SERVICE_MOCK.runtimeId).toBe("runtime-xyz");
   });

   it("should initialize bindingService.objectType from the objectType input", async () => {
      await renderComponent({ objectType: "VSCrosstab" });
      expect(BINDING_SERVICE_MOCK.objectType).toBe("VSCrosstab");
   });

   it("should set showDcAppliedTip=true when chart binding has hasDateComparison=true", async () => {
      const model = {
         type: "chart", hasDateComparison: true,
         xfields: [], yfields: [], geoFields: [], groupFields: [], geoCols: [],
         colorField: null, shapeField: null, sizeField: null, textField: null,
         pathField: null, openField: null, closeField: null, highField: null, lowField: null,
         sourceField: null, targetField: null, startField: null, endField: null,
         milestoneField: null,
      } as unknown as BindingModel;
      const { comp } = await renderComponent({ bindingModel: model });
      expect(comp.showDcAppliedTip).toBe(true);
   });

   it("should set showDcAppliedTip=true when crosstab binding has hasDateComparison=true", async () => {
      const model = {
         type: "crosstab", hasDateComparison: true,
         rows: [], cols: [], aggregates: [],
      } as unknown as BindingModel;
      const { comp } = await renderComponent({ bindingModel: model });
      expect(comp.showDcAppliedTip).toBe(true);
   });

   it("should NOT set showDcAppliedTip when chart binding has hasDateComparison=false", async () => {
      const model = {
         type: "chart", hasDateComparison: false,
         xfields: [], yfields: [], geoFields: [], groupFields: [], geoCols: [],
         colorField: null, shapeField: null, sizeField: null, textField: null,
         pathField: null, openField: null, closeField: null, highField: null, lowField: null,
         sourceField: null, targetField: null, startField: null, endField: null,
         milestoneField: null,
      } as unknown as BindingModel;
      const { comp } = await renderComponent({ bindingModel: model });
      expect(comp.showDcAppliedTip).toBe(false);
   });
});

// ---------------------------------------------------------------------------
// Group 11 — ngAfterViewInit() [baseline]
// ---------------------------------------------------------------------------

describe("BindingEditor — ngAfterViewInit()", () => {
   it("should call onInitialized.emit() when ngAfterViewInit is called", async () => {
      const { comp } = await renderComponent();
      const emitSpy = vi.spyOn(comp.onInitialized, "emit");

      comp.ngAfterViewInit();

      expect(emitSpy).toHaveBeenCalledOnce();
   });
});

// ---------------------------------------------------------------------------
// Group 12 — ngOnDestroy() [baseline +memory-leak]
// ---------------------------------------------------------------------------

describe("BindingEditor — ngOnDestroy()", () => {
   // 🔁 Memory-leak guard: bindingService.clear() releases runtime binding state;
   // if skipped, stale bindings accumulate across repeated open/close of the binding editor.

   it("should call bindingService.clear() on destroy", async () => {
      const { fixture } = await renderComponent();
      fixture.destroy();
      expect(BINDING_SERVICE_MOCK.clear).toHaveBeenCalledOnce();
   });
});

// ---------------------------------------------------------------------------
// Group 13 — openConsoleDialog() [Risk 2]
// ---------------------------------------------------------------------------

describe("BindingEditor — openConsoleDialog()", () => {
   // 🔁 Regression-sensitive: if getModel() is called with the wrong URI or the messageLevels
   // are not stored, the console dialog opens with stale log-level configuration.

   it("should call ModelService.getModel with the encoded runtimeId URI", async () => {
      MODEL_SERVICE_MOCK.getModel.mockReturnValue(of([]));
      MODAL_MOCK.open.mockReturnValue({ result: Promise.resolve([]) });

      const { comp } = await renderComponent({ runtimeId: "rt-abc" });
      comp.openConsoleDialog();

      expect(MODEL_SERVICE_MOCK.getModel).toHaveBeenCalledWith(
         expect.stringContaining("rt-abc"),
      );
   });

   it("should update messageLevels from the HTTP response", async () => {
      const levels = ["WARN", "ERROR"];
      MODEL_SERVICE_MOCK.getModel.mockReturnValue(of(levels));
      MODAL_MOCK.open.mockReturnValue({ result: Promise.resolve([]) });

      const { comp } = await renderComponent({ runtimeId: "rt-abc" });
      comp.openConsoleDialog();

      expect(comp.messageLevels).toEqual(["WARN", "ERROR"]);
   });

   it("should open the modal after receiving the HTTP response", async () => {
      MODEL_SERVICE_MOCK.getModel.mockReturnValue(of(["WARN"]));
      MODAL_MOCK.open.mockReturnValue({ result: Promise.resolve([]) });

      const { comp } = await renderComponent({ runtimeId: "rt-abc" });
      comp.openConsoleDialog();

      expect(MODAL_MOCK.open).toHaveBeenCalledOnce();
   });

   it("should update messageLevels when the modal resolves with new levels", async () => {
      const resolvedLevels = ["DEBUG", "INFO"];
      MODEL_SERVICE_MOCK.getModel.mockReturnValue(of(["WARN"]));
      MODAL_MOCK.open.mockReturnValue({ result: Promise.resolve(resolvedLevels) });

      const { comp } = await renderComponent({ runtimeId: "rt-abc" });
      comp.openConsoleDialog();

      await Promise.resolve();

      expect(comp.messageLevels).toEqual(resolvedLevels);
   });
});
