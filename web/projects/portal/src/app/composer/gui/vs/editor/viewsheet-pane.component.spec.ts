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
import { EventEmitter } from "@angular/core";
import { BehaviorSubject, of as observableOf, Subject } from "rxjs";
import { Point } from "../../../../common/data/point";
import { TableDataPath } from "../../../../common/data/table-data-path";
import { TestUtils } from "../../../../common/test/test-utils";
import { ComponentTool } from "../../../../common/util/component-tool";
import { ViewsheetClientService } from "../../../../common/viewsheet-client";
import { ChartObject } from "../../../../graph/model/chart-object";
import { ChartRegion } from "../../../../graph/model/chart-region";
import {
   AddVSObjectCommand,
   AddVsObjectMode
} from "../../../../vsobjects/command/add-vs-object-command";
import { RemoveVSObjectCommand } from "../../../../vsobjects/command/remove-vs-object-command";
import { VSGaugeModel } from "../../../../vsobjects/model/output/vs-gauge-model";
import { VSChartModel } from "../../../../vsobjects/model/vs-chart-model";
import { VSGroupContainerModel } from "../../../../vsobjects/model/vs-group-container-model";
import { VSRadioButtonModel } from "../../../../vsobjects/model/vs-radio-button-model";
import { VSRangeSliderModel } from "../../../../vsobjects/model/vs-range-slider-model";
import { VSSelectionContainerModel } from "../../../../vsobjects/model/vs-selection-container-model";
import { VSSelectionListModel } from "../../../../vsobjects/model/vs-selection-list-model";
import { VSTabModel } from "../../../../vsobjects/model/vs-tab-model";
import { VSTableModel } from "../../../../vsobjects/model/vs-table-model";
import { Viewsheet } from "../../../data/vs/viewsheet";
import { VSPane } from "./viewsheet-pane.component";

describe("Viewsheet Pane Test", () => {
   let viewsheetPane: VSPane;
   let elementRef: any;
   let composerObjectService: any;
   let stompClient: any;
   let treeService: any;
   let bindingService: any;
   let changeDetectorRef: any;
   let modelService: any;
   let modalService: any;
   let responseObservable: any;
   let viewsheetClientService: any;
   let downloadService: any;
   let actionFactory: any;
   let debounceService: any;
   let scaleService: any;
   let dialogService: any;
   let dataTipService: any;
   let dragService: any;
   let uiContextService: any;
   let zone: any;
   let resizeHandlerService: any;
   let composerVsSearchService: any;

   let createChartObject: () =>  ChartObject = () => {
      return {
         areaName: "plot_area",
         bounds: null,
         layoutBounds: null,
         tiles: null,
         regions: [],
         secondary: false,
         xboundaries: [],
         yboundaries: [],
         showReferenceLine: false
      };
   };

   let createCellRegion: () => TableDataPath = () => {
      return {
         col: false,
         colIndex: -1,
         dataType: "string",
         index: 0,
         level: -1,
         path: [],
         row: true,
         type: 512
      };
   };

   let createTitleRegion: () => TableDataPath = () => {
      return {
         col: false,
         colIndex: -1,
         dataType: "string",
         index: 0,
         level: -1,
         path: [],
         row: true,
         type: 16384
      };
   };

   let createHeaderRegion: () => TableDataPath = () => {
      return {
         col: false,
         colIndex: -1,
         dataType: "string",
         index: 0,
         level: -1,
         path: ["Employee"],
         row: false,
         type: 256
      };
   };

   beforeEach(() => {
      elementRef = {};
      composerObjectService = {
         updateLayerMovement: jest.fn(),
         getNewObject: jest.fn(),
         removeObjectFromList: jest.fn()
      };
      stompClient = { connect: jest.fn(), subscribe: jest.fn() };
      treeService = { resetTreeModel: jest.fn() };
      bindingService = { setClientService: jest.fn() };
      changeDetectorRef = { detectChanges: jest.fn() };
      modelService = { sendModel: jest.fn() };
      modalService = { open: jest.fn() };
      debounceService = { debounce: jest.fn((key, fn, delay, args) => fn(...args)) };

      responseObservable = new BehaviorSubject(new Subject());
      zone = { run: jest.fn() };
      viewsheetClientService = new ViewsheetClientService(stompClient, zone);

      downloadService = { download: jest.fn() };
      scaleService = { getScale: jest.fn(), setScale: jest.fn() };
      scaleService.getScale.mockImplementation(() => observableOf(1));
      actionFactory = { createActions: jest.fn() };
      actionFactory.createActions.mockImplementation(() => ({
         onAssemblyActionEvent: new EventEmitter<any>()
      }));

      dialogService = {
         open: jest.fn(),
         assemblyDelete: jest.fn(),
         objectDelete: jest.fn()
      };
      dataTipService = { clearDataTips: jest.fn() };
      dragService = { reset: jest.fn(), put: jest.fn() };
      uiContextService = {
         isVS: jest.fn(),
         isAdhoc: jest.fn(),
         getDefaultTab: jest.fn(),
         setDefaultTab: jest.fn()
      };
      resizeHandlerService = {
         anyResizeSubject: new BehaviorSubject(new Subject())
      };
      let domService: any = { requestRead: jest.fn(), requestWrite: jest.fn() };
      let renderer: any = { listen: jest.fn() };
      composerVsSearchService = {
         isVisible: jest.fn(),
         isSearchMode: jest.fn(),
         changeSearchMode: jest.fn(),
         assemblyVisible: jest.fn(),
         nextFocus: jest.fn(),
         previousFocus: jest.fn(),
         focusAssembly: jest.fn(),
         isFocusAssembly: jest.fn(),
         focusChange: jest.fn()
      };
      renderer.listen.mockImplementation(() => () => {});

      viewsheetPane = new VSPane(
         elementRef, composerObjectService, viewsheetClientService,
         treeService, changeDetectorRef, modelService, modalService,
         downloadService, dragService, scaleService, renderer, actionFactory,
         dialogService, dataTipService, debounceService, uiContextService, zone, domService, null,
         resizeHandlerService, composerVsSearchService);
   });

   // Bug #10442 make sure to update send to back/front enabled after adding vs object to vs
   it("should update send to back/front enabled after adding object", () => {
      let vs: Viewsheet = new Viewsheet();
      vs.vsObjects = [];
      viewsheetPane.vs = vs;
      let objectModel: VSGaugeModel = Object.assign(
         { locked: false, hyperlinks: [], clickable: false},
         TestUtils.createMockVSObjectModel("VSGauge", "VSGauge1")
      );
      let addVSObjectCommand: AddVSObjectCommand = {
         name: "Gauge1",
         mode: AddVsObjectMode.DESIGN_MODE,
         model: objectModel,
         parent: ""
      };

      viewsheetPane["processAddVSObjectCommand"](addVSObjectCommand);
      expect(composerObjectService.updateLayerMovement).toHaveBeenCalledWith(vs, objectModel);
   });

   // Bug #16274 make sure to update send to back/front enabled after removing an object from vs
   it("should update send to back/front enabled after adding object", () => {
      let vs: Viewsheet = new Viewsheet();
      viewsheetPane.vs = vs;
      let gauge1: VSGaugeModel = Object.assign(
         { locked: false, hyperlinks: [], clickable: false},
         TestUtils.createMockVSObjectModel("VSGauge", "VSGauge1")
      );
      let gauge2: VSGaugeModel = Object.assign(
         { locked: false, hyperlinks: [], clickable: false},
         TestUtils.createMockVSObjectModel("VSGauge", "VSGauge2")
      );
      vs.vsObjects = [gauge1, gauge2];
      let removeVSObjectCommand: RemoveVSObjectCommand = {
         name: "VSGauge1",
      };

      viewsheetPane["processRemoveVSObjectCommand"](removeVSObjectCommand);
      expect(composerObjectService.updateLayerMovement).toHaveBeenCalledWith(vs, gauge2);
   });

   // Bug #16088 do not allow adding self as embedded vs
   it("should not add new object if trying to add self vs", () => {
      const vs: Viewsheet = new Viewsheet();
      vs.id = "MockID01";
      vs.vsObjects = [];
      const dataTransferData: any = {
         dragName: "viewsheet",
         viewsheet: [{
            identifier: "MockID01"
         }]
      };
      const dataTransfer: any = {
         getData: jest.fn(() => JSON.stringify(dataTransferData))
      };
      const clientRect: any = { left: 0, top: 0 };
      const element: any = { getBoundingClientRect: jest.fn(() => clientRect) };
      const interactContainer: any = { snap: jest.fn(() => new Point(0, 0)) };
      const event: any = {
         preventDefault: jest.fn(),
         dataTransfer: dataTransfer
      };

      const showMessageDialog = jest.spyOn(ComponentTool, "showMessageDialog");
      showMessageDialog.mockImplementation(() => Promise.resolve("ok"));
      elementRef["nativeElement"] = element;
      viewsheetPane.vs = vs;
      viewsheetPane.vsPane = elementRef;
      viewsheetPane.interactContainer = interactContainer;
      viewsheetPane.drop(event);
      expect(composerObjectService.getNewObject).not.toHaveBeenCalled();
      expect(showMessageDialog).toHaveBeenCalled();
   });

   // Bug #10350 show zoom in/out in context menu
   it("should show zoom in vs context menu", () => {
      let vs: Viewsheet = new Viewsheet();
      vs.scale = 1.2;
      viewsheetPane.vs = vs;

      expect(TestUtils.toString(viewsheetPane.menuActions[1].actions[1].label())).toBe("Zoom In");
      expect(TestUtils.toString(viewsheetPane.menuActions[1].actions[2].label())).toBe("Zoom Out");

      viewsheetPane.menuActions[1].actions[1].action(null);
      expect(vs.scale).toBe(1.4);
   });

   //Bug #17399, #17428, Bug #20839 status bar should show right info when click diff region.
   //Bug #20649 should has no text in status bar when no assembly selected
   xit("should show right status bar context on chart", () => {
      let vs: Viewsheet = new Viewsheet();
      let chart1: VSChartModel = Object.assign(
         { locked: false, hyperlinks: []},
         TestUtils.createMockVSChartModel("chart1")
      );
      chart1.stringDictionary = [ "Label" ];
      vs.currentFocusedAssemblies = [chart1];
      let chartRegion: ChartRegion = TestUtils.createMockChartRegion();
      let chartObject: ChartObject = createChartObject();
      chart1.regionMetaDictionary = [{areaType: "text", dimIdx: 0}];

      chart1.chartSelection = {
         chartObject: chartObject,
         regions: [ chartRegion ]
      };
      viewsheetPane.vs = vs;
      viewsheetPane.detectChanges(true);
      expect(viewsheetPane.status.text).toBe("chart1 => <b>text[Label]</b>");

      chart1.regionMetaDictionary = [{areaType: "label"}];
      chart1.chartSelection = {
         chartObject: chartObject,
         regions: [ chartRegion ]
      };
      viewsheetPane.vs = vs;
      viewsheetPane.detectChanges(true);
      expect(viewsheetPane.status.text).toContain("<b>targetLabel");

      chartObject.areaName = "x_title";
      chart1.regionMetaDictionary = [{areaType: "title"}];
      chart1.chartSelection = {
         chartObject: chartObject,
         regions: [ chartRegion ]
      };
      viewsheetPane.vs = vs;
      viewsheetPane.detectChanges(true);
      expect(viewsheetPane.status.text).toBe("chart1 => <b>axisTitle[x]</b>");

      chartObject.areaName = "y_title";
      chart1.chartSelection = {
         chartObject: chartObject,
         regions: [ chartRegion ]
      };
      viewsheetPane.vs = vs;
      viewsheetPane.detectChanges(true);
      expect(viewsheetPane.status.text).toBe("chart1 => <b>axisTitle[y]</b>");

      vs.currentFocusedAssemblies = [];
      viewsheetPane.vs = vs;
      viewsheetPane.detectChanges(true);
      expect(viewsheetPane.status.text).toBeUndefined();
   });

   //#17430 should show right status info on selection list assembly
   it("shuld show right status info when click selection list assembly", () => {
      let vs: Viewsheet = new Viewsheet();
      let vsList: VSSelectionListModel = TestUtils.createMockVSSelectionListModel("vsList");
      vsList.selectionList.selectionValues = [TestUtils.createMockSelectionValues()];
      vs.currentFocusedAssemblies = [vsList];
      viewsheetPane.vs = vs;
      vsList.selectedRegions = [createCellRegion()];
      viewsheetPane.detectChanges(true);
      expect(viewsheetPane.status.text).toBe("vsList => <b>Cell</b>");

      vsList.selectedRegions = [createTitleRegion()];
      viewsheetPane.detectChanges(true);
      expect(viewsheetPane.status.text).toBe("vsList => <b>Title</b>");

      vsList.selectedRegions = [];
      viewsheetPane.detectChanges(true);
      expect(viewsheetPane.status.text).toBe("<b>vsList</b>");
   });

   //Bug #17444 should show right status info when click on radiobutton
   it("should show right status info when click on radiobutton", () => {
      let vs: Viewsheet = new Viewsheet();
      let radioBtn: VSRadioButtonModel = TestUtils.createMockVSRadioButtonModel("radioBtn");
      vs.currentFocusedAssemblies = [radioBtn];
      viewsheetPane.vs = vs;
      radioBtn.selectedRegions = [createTitleRegion()];
      viewsheetPane.detectChanges(true);
      expect(viewsheetPane.status.text).toBe("radioBtn => <b>Title</b>");

      radioBtn.selectedRegions = [createCellRegion()];
      viewsheetPane.detectChanges(true);
      expect(viewsheetPane.status.text).toBe("radioBtn => <b>Cell</b>");

      radioBtn.selectedRegions = [];
      viewsheetPane.detectChanges(true);
      expect(viewsheetPane.status.text).toBe("<b>radioBtn</b>");
   });

   //Bug #21023 should show right status info when select assembly in selection container
   it("should show right status info when select assembly in selection container", () => {
      let vs: Viewsheet = new Viewsheet();
      let vsList: VSSelectionListModel = TestUtils.createMockVSSelectionListModel("vsList");
      let rangeSlider: VSRangeSliderModel = TestUtils.createMockVSRangeSliderModel("range1");
      let selectionContainer: VSSelectionContainerModel =
         TestUtils.createMockVSSelectionContainerModel("container1");

      vsList.container = "container1";
      rangeSlider.container = "container1";
      vsList.containerType = "VSSelectionContainer";
      rangeSlider.containerType = "VSSelectionContainer";
      vsList.selectionList.selectionValues = [TestUtils.createMockSelectionValues()];

      vs.currentFocusedAssemblies = [vsList];
      viewsheetPane.vs = vs;
      viewsheetPane.detectChanges(true);
      expect(viewsheetPane.status.text).toBe("<b>vsList</b>");

      vsList.selectedRegions = [createCellRegion()];
      vs.currentFocusedAssemblies = [vsList];
      viewsheetPane.vs = vs;
      viewsheetPane.detectChanges(true);
      expect(viewsheetPane.status.text).toBe("vsList => <b>Cell</b>");

      vs.currentFocusedAssemblies = [rangeSlider];
      viewsheetPane.vs = vs;
      viewsheetPane.detectChanges(true);
      expect(viewsheetPane.status.text).toBe("<b>range1</b>");

      rangeSlider.selectedRegions = [createTitleRegion()];
      vs.currentFocusedAssemblies = [rangeSlider];
      viewsheetPane.vs = vs;
      viewsheetPane.detectChanges(true);
      expect(viewsheetPane.status.text).toBe("range1 => <b>Title</b>");
   });

   //Bug #20872 should show right status info when select table region in group container
   it("should show right status info when select table region in group container", () => {
      let vs: Viewsheet = new Viewsheet();
      let table1: VSTableModel = TestUtils.createMockVSTableModel("table1");
      let rangeSlider: VSRangeSliderModel = TestUtils.createMockVSRangeSliderModel("range1");
      let group1: VSGroupContainerModel =
         TestUtils.createMockVSGroupContainerModel("group1");

      table1.container = "group1";
      rangeSlider.container = "group1";
      table1.containerType = "VSGroupContainer";
      rangeSlider.containerType = "VSGroupContainer";
      table1.colCount = 2;
      table1.colNames = ["Employee", "Total"];

      table1.selectedRegions = [createTitleRegion()];
      vs.currentFocusedAssemblies = [table1];
      viewsheetPane.vs = vs;
      viewsheetPane.detectChanges(true);
      expect(viewsheetPane.status.text).toBe("table1 => <b>Title</b>");

      table1.selectedRegions = [createHeaderRegion()];
      vs.currentFocusedAssemblies = [table1];
      viewsheetPane.vs = vs;
      viewsheetPane.detectChanges(true);
      expect(viewsheetPane.status.text).toBe("table1 => <b>Header Cell [Employee]</b>");

      table1.selectedRegions = [createCellRegion()];
      table1.selectedRegions[0].path = ["Employee"];
      table1.selectedRegions[0].row = false;
      vs.currentFocusedAssemblies = [table1];
      viewsheetPane.vs = vs;
      viewsheetPane.detectChanges(true);
      expect(viewsheetPane.status.text).toBe("table1 => <b>Detail Cell [Employee]</b>");
   });

   //Bug #21029 should display viewsheet alias
   it("should display viewsheet alias", () => {
      let vs: Viewsheet = new Viewsheet();
      vs.id = "1^128^__NULL__^align";
      vs.label = "test";
      vs.runtimeId = "align-15145353038700";
      vs.currentFocusedAssemblies = [];
      vs.statusText = "Global Viewsheet/test 2017-12-29 16:13:42";

      viewsheetPane.vs = vs;
      viewsheetPane.detectChanges(true);
      expect(viewsheetPane.status.text.startsWith("Global Viewsheet/test")).toBe(true);
      expect(viewsheetPane.status.text.startsWith("Global Viewsheet/align")).toBe(false);
   });

   //Bug #21507 should not display extra comma
   it("should not display extra comma", () => {
      let vs: Viewsheet = new Viewsheet();
      let table1: VSTableModel = TestUtils.createMockVSTableModel("table1");
      let gauge1: VSGaugeModel = TestUtils.createMockVSGaugeModel("gauge1");
      let gauge2: VSGaugeModel = TestUtils.createMockVSGaugeModel("gauge2");
      let group1: VSGroupContainerModel =
         TestUtils.createMockVSGroupContainerModel("group1");
      let tab1: VSTabModel = Object.assign({
         labels: ["group1", "gauge2"],
         childrenNames: ["group1", "gauge2"],
         selected: "group1",
         activeFormat: TestUtils.createMockVSFormatModel(),
         roundTopCornersOnly: true
      }, TestUtils.createMockVSObjectModel("VSTab", "tab1"));

      table1.container = "group1";
      gauge1.container = "group1";
      table1.containerType = "VSGroupContainer";
      gauge1.containerType = "VSGroupContainer";
      group1.container = "tab1";
      gauge2.container = "tab1";
      group1.containerType = "VSTab";
      gauge2.containerType = "VSTab";

      vs.currentFocusedAssemblies = [group1, gauge1];
      viewsheetPane.vs = vs;
      viewsheetPane.detectChanges(true);
      expect(viewsheetPane.status.text).toBe("<b>gauge1</b>");
   });
});
