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
 * VSPane — Pass 2: Risk
 *
 * Risk-first coverage:
 *   Group 1  [Risk 3] — processClearLoadingCommand: decrements loadingEventCount; clears
 *                        vs.loading only when count reaches 0; not when count > 0.
 *   Group 2  [Risk 3] — removeVSObject: removes from vs.vsObjects; calls
 *                        dialogService.objectDelete; calls dataTipService.clearDataTips;
 *                        updates z-indexes.
 *   Group 3  [Risk 3] — cancelViewsheetLoading: sets vs.loading=false; calls
 *                        notifications.success; sends cancel event to server.
 *   Group 4  [Risk 3] — addConsoleMessage: pushes INFO/WARNING/ERROR to consoleMessages;
 *                        sets newInfoConsoleMessages=true for INFO;
 *                        does NOT push for OK type.
 *   Group 5  [Risk 2] — openExistingViewsheet: when event.entryId present → calls
 *                        modelService.sendModel then sends /events/open;
 *                        when no entryId → sends /events/open directly.
 *   Group 6  [Risk 2] — deselectObjects: clears vs.currentFocusedAssemblies when
 *                        this.click=true and target is vsPane; resets
 *                        vs.formatPainterMode=false.
 *   Group 7  [Risk 2] — updateRulerGuides: sets rulerGuidesVisible=false when no focused
 *                        assemblies; sets rulerGuidesVisible=true with correct bounds
 *                        when assemblies are focused.
 *   Group 8  [Risk 2] — refreshStatus (via getStatusForStatusBar): status.text=vs.statusText
 *                        when no assemblies focused; includes <b>assemblyName</b> when
 *                        assembly focused; chart/selection/table region strings; alias and
 *                        trimComma regressions (Bugs #17399–#21507).
 */

import { of } from "rxjs";
import { makeMocks, renderComponent } from "./viewsheet-pane.component.test-helpers";
import { Status } from "../../../../status-bar/status";
import { DataTipService } from "../../../../vsobjects/objects/data-tip/data-tip.service";
import { TestUtils } from "../../../../common/test/test-utils";
import { TableDataPath } from "../../../../common/data/table-data-path";
import { ChartObject } from "../../../../graph/model/chart-object";
import { ChartRegion } from "../../../../graph/model/chart-region";
import { VSGaugeModel } from "../../../../vsobjects/model/output/vs-gauge-model";
import { VSChartModel } from "../../../../vsobjects/model/vs-chart-model";
import { VSGroupContainerModel } from "../../../../vsobjects/model/vs-group-container-model";
import { VSRadioButtonModel } from "../../../../vsobjects/model/vs-radio-button-model";
import { VSRangeSliderModel } from "../../../../vsobjects/model/vs-range-slider-model";
import { VSSelectionListModel } from "../../../../vsobjects/model/vs-selection-list-model";
import { VSTableModel } from "../../../../vsobjects/model/vs-table-model";
import { Viewsheet } from "../../../data/vs/viewsheet";

function createCellRegion(): TableDataPath {
   return {
      col: false, colIndex: -1, dataType: "string", index: 0, level: -1,
      path: [], row: true, type: 512,
   };
}

function createTitleRegion(): TableDataPath {
   return {
      col: false, colIndex: -1, dataType: "string", index: 0, level: -1,
      path: [], row: true, type: 16384,
   };
}

function createHeaderRegion(): TableDataPath {
   return {
      col: false, colIndex: -1, dataType: "string", index: 0, level: -1,
      path: ["Employee"], row: false, type: 256,
   };
}

function createChartObject(): ChartObject {
   return {
      areaName: "plot_area",
      bounds: null,
      layoutBounds: null,
      tiles: null,
      regions: [],
      xboundaries: [],
      yboundaries: [],
      showReferenceLine: false,
   } as ChartObject;
}

async function renderFocusedChart() {
   const { comp } = await renderComponent();
   const vs: Viewsheet = comp.vs;
   const chart1: VSChartModel = Object.assign(
      { locked: false, hyperlinks: [] },
      TestUtils.createMockVSChartModel("chart1"),
   );
   chart1.stringDictionary = ["Label"];
   vs.currentFocusedAssemblies = [chart1];
   const chartRegion: ChartRegion = TestUtils.createMockChartRegion();
   const chartObject: ChartObject = createChartObject();
   return { comp, chart1, chartRegion, chartObject };
}

afterEach(() => vi.restoreAllMocks());

// ---------------------------------------------------------------------------
// Group 1: processClearLoadingCommand [Risk 3]
// ---------------------------------------------------------------------------

describe("VSPane — processClearLoadingCommand", () => {

   // Regression-sensitive: loadingEventCount may be incremented multiple times; vs.loading
   // must NOT be cleared until the final clear command brings the count to zero.
   it("should not clear vs.loading when loadingEventCount is still > 0 after decrement", async () => {
      const mocks = makeMocks();
      const { comp } = await renderComponent(mocks);
      // Simulate two ShowLoadingMask commands (count=2), then one Clear (count=1)
      (comp as any).loadingEventCount = 2;
      comp.vs.loading = true;

      mocks.dispatchCommand("ClearLoadingCommand", { count: 1 });

      expect(comp.vs.loading).toBe(true);
      expect((comp as any).loadingEventCount).toBe(1);
   });

   it("should clear vs.loading when loadingEventCount reaches 0", async () => {
      const mocks = makeMocks();
      const { comp } = await renderComponent(mocks);
      (comp as any).loadingEventCount = 1;
      comp.vs.loading = true;

      mocks.dispatchCommand("ClearLoadingCommand", { count: 1 });

      expect((comp as any).loadingEventCount).toBe(0);
      // vs.loading cleared inside zone.run — verify synchronously after command dispatch
      expect(comp.vs.loading).toBe(false);
   });

   it("should not decrement below zero (loadingEventCount uses Math.max(0,...))", async () => {
      const mocks = makeMocks();
      const { comp } = await renderComponent(mocks);
      (comp as any).loadingEventCount = 0;
      comp.vs.loading = false;

      mocks.dispatchCommand("ClearLoadingCommand", { count: 1 });

      expect((comp as any).loadingEventCount).toBe(0);
   });
});

// ---------------------------------------------------------------------------
// Group 2: removeVSObject [Risk 3]
// ---------------------------------------------------------------------------

describe("VSPane — removeVSObject", () => {

   // Regression-sensitive: removeObjectFromList must be called so the object is removed
   // from vs.vsObjects; dialogService and dataTipService must be notified so their internal
   // state does not retain stale references.
   it("should call composerObjectService.removeObjectFromList with vs and name", async () => {
      const mocks = makeMocks();
      const { comp } = await renderComponent(mocks);
      const obj: any = {
         absoluteName: "Chart1",
         objectType: "VSChart",
         objectFormat: { zIndex: 1, left: 0, top: 0, width: 100, height: 100 },
      };
      comp.vs.vsObjects = [obj];

      comp.removeVSObject("Chart1");

      expect(mocks.composerObjectService.removeObjectFromList).toHaveBeenCalledWith(comp.vs, "Chart1");
   });

   it("should call dialogService.objectDelete with name", async () => {
      const mocks = makeMocks();
      const { comp } = await renderComponent(mocks);

      comp.removeVSObject("Chart1");

      expect(mocks.dialogService.objectDelete).toHaveBeenCalledWith("Chart1");
   });

   it("should call dataTipService.clearDataTips with name", async () => {
      const mocks = makeMocks();
      const { comp, fixture } = await renderComponent(mocks);
      const dataTipService = fixture.debugElement.injector.get(DataTipService);

      comp.removeVSObject("Chart1");

      expect((dataTipService as any).clearDataTips).toHaveBeenCalledWith("Chart1");
   });

   it("should call composerObjectService.updateLayerMovement for each remaining object", async () => {
      const mocks = makeMocks();
      const { comp } = await renderComponent(mocks);
      const obj1: any = {
         absoluteName: "Table1",
         objectType: "VSTable",
         objectFormat: { zIndex: 1, left: 0, top: 0, width: 100, height: 100 },
      };
      comp.vs.vsObjects = [obj1];

      comp.removeVSObject("Table1");

      // updateLayerMovement is called for each vs.vsObjects entry after removal
      expect(mocks.composerObjectService.updateLayerMovement).toHaveBeenCalled();
   });
});

// ---------------------------------------------------------------------------
// Group 3: cancelViewsheetLoading [Risk 3]
// ---------------------------------------------------------------------------

describe("VSPane — cancelViewsheetLoading", () => {

   // Regression-sensitive: vs.loading must be cleared synchronously so the loading spinner
   // disappears; notifications.success informs the user; sendEvent to server cancels the
   // ongoing computation.
   it("should set vs.loading=false", async () => {
      const mocks = makeMocks();
      const { comp } = await renderComponent(mocks);
      comp.vs.loading = true;

      comp.cancelViewsheetLoading();

      expect(comp.vs.loading).toBe(false);
   });

   it("should call notifications.success", async () => {
      const mocks = makeMocks();
      const { comp } = await renderComponent(mocks);

      comp.cancelViewsheetLoading();

      expect(mocks.notifications.success).toHaveBeenCalledWith(
         "_#(js:common.viewsheet.cancelled)",
      );
   });

   it("should send cancel event to server", async () => {
      const mocks = makeMocks();
      const { comp } = await renderComponent(mocks);

      comp.cancelViewsheetLoading();

      expect(mocks.vsClient.sendEvent).toHaveBeenCalledWith(
         "/events/composer/viewsheet/cancelViewsheet",
         expect.anything(),
      );
   });
});

// ---------------------------------------------------------------------------
// Group 4: addConsoleMessage [Risk 3]
// ---------------------------------------------------------------------------

describe("VSPane — addConsoleMessage", () => {

   // Regression-sensitive: console messages must be persisted for the console dialog;
   // the newInfoConsoleMessages flag drives the badge on the console button.
   it("should push INFO message to consoleMessages", async () => {
      const { comp } = await renderComponent();

      (comp as any).addConsoleMessage({ message: "Info msg", type: "INFO" });

      expect((comp as any).consoleMessages).toHaveLength(1);
      expect((comp as any).consoleMessages[0].type).toBe("INFO");
   });

   it("should set newInfoConsoleMessages=true for INFO type", async () => {
      const { comp } = await renderComponent();
      (comp as any).newInfoConsoleMessages = false;

      (comp as any).addConsoleMessage({ message: "Info msg", type: "INFO" });

      expect((comp as any).newInfoConsoleMessages).toBe(true);
   });

   it("should push WARNING message without setting newInfoConsoleMessages", async () => {
      const { comp } = await renderComponent();
      (comp as any).newInfoConsoleMessages = false;

      (comp as any).addConsoleMessage({ message: "Warning msg", type: "WARNING" });

      expect((comp as any).consoleMessages).toHaveLength(1);
      expect((comp as any).consoleMessages[0].type).toBe("WARNING");
      expect((comp as any).newInfoConsoleMessages).toBe(false);
   });

   it("should push ERROR message to consoleMessages", async () => {
      const { comp } = await renderComponent();

      (comp as any).addConsoleMessage({ message: "Error msg", type: "ERROR" });

      expect((comp as any).consoleMessages).toHaveLength(1);
      expect((comp as any).consoleMessages[0].type).toBe("ERROR");
   });

   it("should NOT push message for OK type", async () => {
      const { comp } = await renderComponent();

      (comp as any).addConsoleMessage({ message: "OK msg", type: "OK" });

      expect((comp as any).consoleMessages).toHaveLength(0);
   });

   it("should NOT push message when message is falsy", async () => {
      const { comp } = await renderComponent();

      (comp as any).addConsoleMessage({ message: null, type: "INFO" });

      expect((comp as any).consoleMessages).toHaveLength(0);
   });
});

// ---------------------------------------------------------------------------
// Group 5: openExistingViewsheet [Risk 2]
// ---------------------------------------------------------------------------

describe("VSPane — openExistingViewsheet", () => {

   // Regression-sensitive: when entryId is set, modelService.sendModel must be called first
   // (checks for auto-save conflicts); when no entryId the open event is sent directly.
   it("should call modelService.sendModel when event.entryId is present", async () => {
      const mocks = makeMocks();
      const { comp } = await renderComponent(mocks);
      mocks.modelService.sendModel.mockReturnValue(of({ body: null }));
      const event: any = {
         entryId: "entry123",
         runtimeViewsheetId: null,
         openAutoSaved: false,
      };

      comp.openExistingViewsheet(event);

      expect(mocks.modelService.sendModel).toHaveBeenCalledWith(
         "../api/vs/open",
         expect.anything(),
      );
   });

   it("should send /events/open when event.entryId is absent", async () => {
      const mocks = makeMocks();
      const { comp } = await renderComponent(mocks);
      const event: any = {
         entryId: null,
         runtimeViewsheetId: null,
         openAutoSaved: false,
      };

      comp.openExistingViewsheet(event);

      expect(mocks.vsClient.sendEvent).toHaveBeenCalledWith("/events/open", expect.anything());
   });
});

// ---------------------------------------------------------------------------
// Group 6: deselectObjects [Risk 2]
// ---------------------------------------------------------------------------

describe("VSPane — deselectObjects", () => {

   // Regression-sensitive: clicking on empty canvas clears selection; clicking on an
   // assembly should NOT clear selection (target is not the vsPane nativeElement).
   it("should clear vs.currentFocusedAssemblies and reset formatPainterMode when click=true and target is vsPane", async () => {
      const { comp } = await renderComponent();
      (comp as any).click = true;
      comp.vs.currentFocusedAssemblies = [{ absoluteName: "Chart1" } as any];
      comp.vs.formatPainterMode = true;

      // When vsPane is null (no real DOM under NO_ERRORS_SCHEMA), isTargetVSPane returns true
      // because: !this.vsPane || (event && event.target && event.target.parentElement == ...)
      // With vsPane = null the short-circuit fires true.
      (comp as any).vsPane = null;

      comp.deselectObjects({ target: document.body });

      expect(comp.vs.currentFocusedAssemblies).toHaveLength(0);
      expect(comp.vs.formatPainterMode).toBe(false);
   });

   it("should NOT clear vs.currentFocusedAssemblies when click=false", async () => {
      const { comp } = await renderComponent();
      (comp as any).click = false;
      const obj: any = { absoluteName: "Chart1" };
      comp.vs.currentFocusedAssemblies = [obj];
      (comp as any).vsPane = null;

      comp.deselectObjects({ target: document.body });

      expect(comp.vs.currentFocusedAssemblies).toHaveLength(1);
   });
});

// ---------------------------------------------------------------------------
// Group 7: updateRulerGuides [Risk 2]
// ---------------------------------------------------------------------------

describe("VSPane — updateRulerGuides", () => {

   it("should set rulerGuidesVisible=false when no focused assemblies", async () => {
      const { comp } = await renderComponent();
      comp.vs.currentFocusedAssemblies = [];
      comp.vs.currentLayout = null;

      comp.updateRulerGuides();

      expect(comp.rulerGuidesVisible).toBe(false);
   });

   it("should set rulerGuidesVisible=true and compute bounds from focused assembly", async () => {
      const { comp } = await renderComponent();
      const assembly: any = {
         absoluteName: "Chart1",
         objectFormat: { left: 10, top: 20, width: 200, height: 150 },
      };
      comp.vs.currentFocusedAssemblies = [assembly];
      comp.vs.currentLayout = null;
      comp.vs.scale = 1;

      comp.updateRulerGuides();

      expect(comp.rulerGuidesVisible).toBe(true);
      expect(comp.rulerGuideLeft).toBe(10);
      expect(comp.rulerGuideTop).toBe(20);
      expect(comp.rulerGuideWidth).toBe(200);
      expect(comp.rulerGuideHeight).toBe(150);
   });

   it("should compute bounds spanning multiple focused assemblies", async () => {
      const { comp } = await renderComponent();
      const a1: any = {
         absoluteName: "Chart1",
         objectFormat: { left: 10, top: 10, width: 100, height: 100 },
      };
      const a2: any = {
         absoluteName: "Table1",
         objectFormat: { left: 50, top: 5, width: 200, height: 50 },
      };
      comp.vs.currentFocusedAssemblies = [a1, a2];
      comp.vs.currentLayout = null;
      comp.vs.scale = 1;

      comp.updateRulerGuides();

      expect(comp.rulerGuideLeft).toBe(10);    // min of 10, 50
      expect(comp.rulerGuideTop).toBe(5);      // min of 10, 5
      expect(comp.rulerGuideWidth).toBe(240);  // max right=250 minus left=10
      expect(comp.rulerGuideHeight).toBe(105); // max bottom=110 minus top=5
   });
});

// ---------------------------------------------------------------------------
// Group 8: refreshStatus / getStatusForStatusBar [Risk 2]
// ---------------------------------------------------------------------------

describe("VSPane — getStatusForStatusBar / refreshStatus", () => {

   // Regression-sensitive: the status text is displayed in the status bar at the bottom of
   // the composer; wrong text after assembly focus breaks the user's context information.
   it("should set status.text=vs.statusText when no assemblies are focused", async () => {
      const { comp } = await renderComponent();
      comp.vs.statusText = "Viewsheet status";
      comp.vs.currentFocusedAssemblies = [];
      comp.vs.currentLayout = null;
      comp.vs.baseEntry = null;

      // Trigger refreshStatus indirectly via the private method
      (comp as any).refreshStatus();

      const status: Status = comp.getStatusForStatusBar();
      expect(status.text).toBe("Viewsheet status");
   });

   it("should include <b>assemblyName</b> in status when an assembly is focused", async () => {
      const { comp } = await renderComponent();
      comp.vs.statusText = "VS";
      const assembly: any = {
         absoluteName: "Chart1",
         objectType: "VSChart",
         selectedRegions: [],
         chartSelection: null,
         advancedStatus: "",
         objectFormat: { left: 0, top: 0, width: 100, height: 100 },
      };
      comp.vs.currentFocusedAssemblies = [assembly];
      comp.vs.currentLayout = null;

      (comp as any).refreshStatus();

      const status: Status = comp.getStatusForStatusBar();
      expect(status.text).toContain("<b>Chart1</b>");
   });

   it("should show chart text region in the status bar (Bug #17399)", async () => {
      const { comp, chart1, chartRegion, chartObject } = await renderFocusedChart();
      chart1.regionMetaDictionary = [{ areaType: "text", dimIdx: 0 }];
      chart1.chartSelection = { chartObject, regions: [chartRegion] };
      comp.detectChanges(true);
      expect(comp.status.text).toBe("chart1 => <b>text</b>");
   });

   it("should show chart label region in the status bar (Bug #17428)", async () => {
      const { comp, chart1, chartRegion, chartObject } = await renderFocusedChart();
      chart1.regionMetaDictionary = [{ areaType: "label" }];
      chart1.chartSelection = { chartObject, regions: [chartRegion] };
      comp.detectChanges(true);
      expect(comp.status.text).toContain("<b>targetLabel");
   });

   it("should show chart x-axis title in the status bar (Bug #20839)", async () => {
      const { comp, chart1, chartRegion, chartObject } = await renderFocusedChart();
      chartObject.areaName = "x_title";
      chart1.regionMetaDictionary = [{ areaType: "title" }];
      chart1.chartSelection = { chartObject, regions: [chartRegion] };
      comp.detectChanges(true);
      expect(comp.status.text).toBe("chart1 => <b>axisTitle[x]</b>");
   });

   it("should show chart y-axis title in the status bar (Bug #20839)", async () => {
      const { comp, chart1, chartRegion, chartObject } = await renderFocusedChart();
      chartObject.areaName = "y_title";
      chart1.regionMetaDictionary = [{ areaType: "title" }];
      chart1.chartSelection = { chartObject, regions: [chartRegion] };
      comp.detectChanges(true);
      expect(comp.status.text).toBe("chart1 => <b>axisTitle[y]</b>");
   });

   it("should clear status text when chart focus is removed (Bug #20839)", async () => {
      const { comp, chart1, chartRegion, chartObject } = await renderFocusedChart();
      chart1.regionMetaDictionary = [{ areaType: "text", dimIdx: 0 }];
      chart1.chartSelection = { chartObject, regions: [chartRegion] };
      comp.detectChanges(true);

      comp.vs.currentFocusedAssemblies = [];
      comp.detectChanges(true);
      expect(comp.status.text).toBeUndefined();
   });

   it("should show selection list region context (Bug #17430)", async () => {
      const { comp } = await renderComponent();
      const vs: Viewsheet = comp.vs;
      const vsList: VSSelectionListModel = TestUtils.createMockVSSelectionListModel("vsList");
      vsList.selectionList.selectionValues = [TestUtils.createMockSelectionValues()];
      vs.currentFocusedAssemblies = [vsList];
      vsList.selectedRegions = [createCellRegion()];
      comp.detectChanges(true);
      expect(comp.status.text).toBe("vsList => <b>Cell</b>");

      vsList.selectedRegions = [createTitleRegion()];
      comp.detectChanges(true);
      expect(comp.status.text).toBe("vsList => <b>Title</b>");

      vsList.selectedRegions = [];
      comp.detectChanges(true);
      expect(comp.status.text).toBe("<b>vsList</b>");
   });

   it("should show radio button region context (Bug #17444)", async () => {
      const { comp } = await renderComponent();
      const vs: Viewsheet = comp.vs;
      const radioBtn: VSRadioButtonModel = TestUtils.createMockVSRadioButtonModel("radioBtn");
      vs.currentFocusedAssemblies = [radioBtn];
      radioBtn.selectedRegions = [createTitleRegion()];
      comp.detectChanges(true);
      expect(comp.status.text).toBe("radioBtn => <b>Title</b>");

      radioBtn.selectedRegions = [createCellRegion()];
      comp.detectChanges(true);
      expect(comp.status.text).toBe("radioBtn => <b>Cell</b>");

      radioBtn.selectedRegions = [];
      comp.detectChanges(true);
      expect(comp.status.text).toBe("<b>radioBtn</b>");
   });

   it("should show selection container child context (Bug #21023)", async () => {
      const { comp } = await renderComponent();
      const vs: Viewsheet = comp.vs;
      const vsList: VSSelectionListModel = TestUtils.createMockVSSelectionListModel("vsList");
      const rangeSlider: VSRangeSliderModel = TestUtils.createMockVSRangeSliderModel("range1");
      vsList.container = "container1";
      rangeSlider.container = "container1";
      vsList.containerType = "VSSelectionContainer";
      rangeSlider.containerType = "VSSelectionContainer";
      vsList.selectionList.selectionValues = [TestUtils.createMockSelectionValues()];

      vs.currentFocusedAssemblies = [vsList];
      comp.detectChanges(true);
      expect(comp.status.text).toBe("<b>vsList</b>");

      vsList.selectedRegions = [createCellRegion()];
      vs.currentFocusedAssemblies = [vsList];
      comp.detectChanges(true);
      expect(comp.status.text).toBe("vsList => <b>Cell</b>");

      vs.currentFocusedAssemblies = [rangeSlider];
      comp.detectChanges(true);
      expect(comp.status.text).toBe("<b>range1</b>");

      rangeSlider.selectedRegions = [createTitleRegion()];
      vs.currentFocusedAssemblies = [rangeSlider];
      comp.detectChanges(true);
      expect(comp.status.text).toBe("range1 => <b>Title</b>");
   });

   it("should show table region context inside a group container (Bug #20872)", async () => {
      const { comp } = await renderComponent();
      const vs: Viewsheet = comp.vs;
      const table1: VSTableModel = TestUtils.createMockVSTableModel("table1");
      table1.container = "group1";
      table1.containerType = "VSGroupContainer";
      table1.colCount = 2;
      table1.colNames = ["Employee", "Total"];

      table1.selectedRegions = [createTitleRegion()];
      vs.currentFocusedAssemblies = [table1];
      comp.detectChanges(true);
      expect(comp.status.text).toBe("table1 => <b>Title</b>");

      table1.selectedRegions = [createHeaderRegion()];
      comp.detectChanges(true);
      expect(comp.status.text).toBe("table1 => <b>Header Cell [Employee]</b>");

      table1.selectedRegions = [createCellRegion()];
      table1.selectedRegions[0].path = ["Employee"];
      table1.selectedRegions[0].row = false;
      comp.detectChanges(true);
      expect(comp.status.text).toBe("table1 => <b>Detail Cell [Employee]</b>");
   });

   it("should display viewsheet alias in status text (Bug #21029)", async () => {
      const { comp } = await renderComponent();
      const vs: Viewsheet = comp.vs;
      vs.id = "1^128^__NULL__^align";
      vs.label = "test";
      vs.runtimeId = "align-15145353038700";
      vs.currentFocusedAssemblies = [];
      vs.statusText = "Global Viewsheet/test 2017-12-29 16:13:42";

      comp.detectChanges(true);
      expect(comp.status.text.startsWith("Global Viewsheet/test")).toBe(true);
      expect(comp.status.text.startsWith("Global Viewsheet/align")).toBe(false);
   });

   it("should not display a trailing comma when group and child are focused (Bug #21507)", async () => {
      const { comp } = await renderComponent();
      const vs: Viewsheet = comp.vs;
      const table1: VSTableModel = TestUtils.createMockVSTableModel("table1");
      const gauge1: VSGaugeModel = TestUtils.createMockVSGaugeModel("gauge1");
      const gauge2: VSGaugeModel = TestUtils.createMockVSGaugeModel("gauge2");
      const group1: VSGroupContainerModel = TestUtils.createMockVSGroupContainerModel("group1");

      table1.container = "group1";
      gauge1.container = "group1";
      table1.containerType = "VSGroupContainer";
      gauge1.containerType = "VSGroupContainer";
      group1.container = "tab1";
      gauge2.container = "tab1";
      group1.containerType = "VSTab";
      gauge2.containerType = "VSTab";

      vs.currentFocusedAssemblies = [group1, gauge1];
      comp.detectChanges(true);
      expect(comp.status.text).toBe("<b>gauge1</b>");
   });
});
