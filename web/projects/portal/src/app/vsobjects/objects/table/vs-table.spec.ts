/*
 * inetsoft-web - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
import { CommonModule } from "@angular/common";
import { HttpClient } from "@angular/common/http";
import { HttpClientTestingModule, HttpTestingController } from "@angular/common/http/testing";
import { NO_ERRORS_SCHEMA, Renderer2 } from "@angular/core";
import { async, ComponentFixture, TestBed } from "@angular/core/testing";
import { FormsModule, ReactiveFormsModule } from "@angular/forms";
import { Router } from "@angular/router";
import { NgbModule } from "@ng-bootstrap/ng-bootstrap";
import { of as observableOf, Subject } from "rxjs";
import { DownloadService } from "../../../../../../shared/download/download.service";
import { Border, Wrapping } from "../../../common/data/base-format-model";
import { DndService } from "../../../common/dnd/dnd.service";
import { DropDownTestModule } from "../../../common/test/test-module";
import { TestUtils } from "../../../common/test/test-utils";
import { ComponentTool } from "../../../common/util/component-tool";
import { ViewsheetClientService } from "../../../common/viewsheet-client";
import { ViewDataService } from "../../../viewer/services/view-data.service";
import { DebounceService } from "../../../widget/services/debounce.service";
import { DragService } from "../../../widget/services/drag.service";
import { ModelService } from "../../../widget/services/model.service";
import { DefaultScaleService } from "../../../widget/services/scale/default-scale-service";
import { ScaleService } from "../../../widget/services/scale/scale-service";
import { TableActions } from "../../action/table-actions";
import { ContextProvider, ViewerContextProviderFactory } from "../../context-provider.service";
import { ViewsheetInfo } from "../../data/viewsheet-info";
import { RichTextService } from "../../dialog/rich-text-dialog/rich-text.service";
import { BaseTableCellModel } from "../../model/base-table-cell-model";
import { VSFormatModel } from "../../model/vs-format-model";
import { VSTableModel } from "../../model/vs-table-model";
import { ShowHyperlinkService } from "../../show-hyperlink.service";
import { CheckFormDataService } from "../../util/check-form-data.service";
import { AdhocFilterService } from "../data-tip/adhoc-filter.service";
import { DataTipService } from "../data-tip/data-tip.service";
import { PopComponentService } from "../data-tip/pop-component.service";
import { VSTableCell } from "./vs-table-cell.component";
import { VSTable } from "./vs-table.component";
import { VSTabService } from "../../util/vs-tab.service";

let createModel = (width: number, colWidths: number[], colCount: number,
                   height: number = 3, cellHeight: number = 1, headerHeight: number = 1,
                   titleHeight: number = 1) => {
   return <VSTableModel>{
      objectFormat: <VSFormatModel>{
         width: width,
         height: height,
         headerHeight: headerHeight,
         titleHeight: titleHeight,
         foreground: "",
         background: "",
         font: "",
         decoration: "",
         alpha: 1,
         hAlign: "",
         vAlign: "",
         justifyContent: "flex-start",
         alignItems: "stretch",
         border: <Border> {
            bottom: "",
            top: "",
            left: "",
            right: ""
         },
         wrapping: <Wrapping> {
            whiteSpace: "",
            wordWrap: "",
            overflow: ""
         },
         top: 0,
         left: 0,
         zIndex: 1,
         bringToFrontEnabled: true,
         sendToBackEnabled: true,
         position: ""
      },
      titleFormat: <VSFormatModel>{
         width: 1,
         height: 1,
         foreground: "",
         background: "",
         font: "",
         decoration: "",
         alpha: 1,
         hAlign: "",
         vAlign: "",
         border: <Border> {
            bottom: "",
            top: "",
            left: "",
            right: ""
         },
         wrapping: <Wrapping> {
            whiteSpace: "",
            wordWrap: "",
            overflow: ""
         },
         top: 0,
         left: 0,
         zIndex: 1,
         bringToFrontEnabled: true,
         sendToBackEnabled: true,
         position: ""
      },
      colWidths: colWidths,
      colCount: colCount,
      headerRowHeights: [cellHeight],
      dataRowHeight: cellHeight,
      headerRowPositions: [0, 18],
      headerRowCount: 1,
      colNames: []
   };
};

let createBaseTableCellModel: () => BaseTableCellModel = () => {
   return {
      cellData: {},
      cellLabel: null,
      row: 0,
      col: 0,
      vsFormatModel: <VSFormatModel>{
         width: 1,
         height: 1,
         headerHeight: 1,
         titleHeight: 1,
         foreground: "",
         background: "",
         font: "",
         decoration: "",
         alpha: 1,
         hAlign: "",
         vAlign: "",
         justifyContent: "flex-start",
         alignItems: "stretch",
         border: <Border> {
            bottom: "",
            top: "",
            left: "",
            right: ""
         },
         wrapping: <Wrapping> {
            whiteSpace: "",
            wordWrap: "",
            overflow: ""
         },
         top: 0,
         left: 0,
         zIndex: 1,
         bringToFrontEnabled: true,
         sendToBackEnabled: true,
         position: ""
      },
      hyperlinks: [],
      grouped: false,
      dataPath: {
         level: 0,
         col: false,
         row: false,
         type: 0,
         dataType: "",
         path: [],
         index: 0,
         colIndex: 0
      },
      presenter: null,
      isImage: false
   };
};

describe("VSTable Unit Tests", () => {
   let viewsheetClientService: any;
   let dndService: any;
   let dataTipService: any;
   let viewDataService: any;
   let contextProvider: any;
   let downloadService: any;
   let debounceService: any;
   let modelService: any;
   let adhocFilterService: any;
   let verticalScrollTooltip: any;
   let httpClient: HttpClient;
   let httpTestingController: HttpTestingController;
   let router: any;
   let richTextService: any;
   let vsTabService: any;

   beforeEach(async(() => {
      viewsheetClientService = { sendEvent: jest.fn() };
      viewsheetClientService.commands = observableOf([]);
      viewsheetClientService.runtimeId = "vs1_111";
      dndService = {};
      viewDataService = {};
      dataTipService = { isDataTip: jest.fn(), isDataTipSource: jest.fn() };
      dataTipService.isDataTip.mockImplementation(() => false);
      downloadService = { download: jest.fn() };
      debounceService = { debounce: jest.fn() };
      modelService = { sendModel: jest.fn() };
      contextProvider = { viewer: true };
      adhocFilterService = { showFilter: jest.fn(), adhocFilterShowing: false };
      router = {
         navigate: jest.fn(),
         events: new Subject<any>()
      };
      richTextService = {
         showAnnotationDialog: jest.fn()
      };
      vsTabService = { };
      vsTabService.tabDeselected = observableOf(null);

      verticalScrollTooltip = { isOpen: jest.fn() };

      TestBed.configureTestingModule({
         imports: [
            CommonModule,
            FormsModule,
            ReactiveFormsModule,
            NgbModule,
            DropDownTestModule,
            HttpClientTestingModule
         ],
         declarations: [
            VSTable, VSTableCell
         ],
         schemas: [NO_ERRORS_SCHEMA],
         providers: [
            Renderer2,
            PopComponentService,
            CheckFormDataService,
            HttpClient,
            {provide: ViewsheetClientService, useValue: viewsheetClientService},
            {provide: DndService, useValue: dndService},
            {provide: DataTipService, useValue: dataTipService},
            {provide: DownloadService, useValue: downloadService},
            {provide: ContextProvider, useValue: contextProvider},
            {provide: ViewDataService, useValue: viewDataService},
            {provide: DebounceService, useValue: debounceService},
            {provide: ScaleService, useClass: DefaultScaleService},
            {provide: ModelService, useValue: modelService},
            ShowHyperlinkService,
            {provide: AdhocFilterService, useValue: adhocFilterService},
            {provide: Router, useValue: router},
            {provide: DragService, useValue: null},
            {provide: RichTextService, useValue: richTextService},
            {provide: VSTabService, useValue: vsTabService}
         ]
      });
      TestBed.compileComponents();

      httpClient = TestBed.inject(HttpClient);
      httpTestingController = TestBed.inject(HttpTestingController);
   }));

   it("should expand the last column", () => {
      let fixture1: ComponentFixture<VSTable> = TestBed.createComponent(VSTable);
      // 300 - (30 + 30 + 90) = 150
      fixture1.componentInstance.model = createModel(300, [30, 30, 90, 40], 4);
      fixture1.detectChanges();
      expect(fixture1.componentInstance.displayColWidths[3]).toBe(150);

      let fixture2: ComponentFixture<VSTable> = TestBed.createComponent(VSTable);
      fixture2.componentInstance.model = createModel(160, [60, 60, 20, 140], 4);
      fixture2.detectChanges();
      expect(fixture2.componentInstance.displayColWidths[0]).toBe(60);
      expect(fixture2.componentInstance.displayColWidths[1]).toBe(60);
      expect(fixture2.componentInstance.displayColWidths[2]).toBe(20);
      expect(fixture2.componentInstance.displayColWidths[3]).toBe(140);
   });

   // Bug #9839 ensure row and col selection instantiated to -1, to signal nothing selected.
   it("should instantiate firstSelectedRow and firstSelectedColumn to -1", () => {
      let fixture1: ComponentFixture<VSTable> = TestBed.createComponent(VSTable);
      fixture1.componentInstance.model = createModel(300, [30, 30, 30], 4);
      fixture1.detectChanges();

      fixture1.componentInstance.ngOnInit();
      expect(fixture1.componentInstance.model.firstSelectedColumn).toEqual(-1);
      expect(fixture1.componentInstance.model.firstSelectedRow).toEqual(-1);
   });

   // Bug #9913 ensure resizing works with drag/drop.
   it("should be draggable if not resizing", () => {
      let fixture1: ComponentFixture<VSTable> = TestBed.createComponent(VSTable);
      fixture1.componentInstance.model = createModel(300, [30, 30, 30], 4);
      fixture1.detectChanges();

      expect(fixture1.componentInstance.isDraggable(true)).toBeTruthy();
   });

   // Bug #10465 table should be hidden when visible is false
   it("should be hidden if not visible", () => {
      let fixture1: ComponentFixture<VSTable> = TestBed.createComponent(VSTable);
      fixture1.componentInstance.model = createModel(300, [30, 30, 30], 4);
      fixture1.componentInstance.model.visible = false;
      fixture1.detectChanges();

      let tableElement: any = fixture1.nativeElement.querySelector(".vs-object");
      expect(tableElement.style["display"]).toEqual("none");
   });

   // Bug #10474 apply underline and strikethrough decoration on table cells
   it("should have underline and line-through text-decoration", (done) => {
      let fixture1: ComponentFixture<VSTable> = TestBed.createComponent(VSTable);
      fixture1.componentInstance.model = createModel(300, [30, 30, 30], 4);
      fixture1.componentInstance.model.visible = true;
      fixture1.componentInstance.vsInfo = new ViewsheetInfo([], "");
      let tableCell: BaseTableCellModel = createBaseTableCellModel();
      tableCell.vsFormatModel.decoration = "underline line-through";
      fixture1.componentInstance.tableData = <BaseTableCellModel[][]> [
         [tableCell],
         [tableCell]
      ];
      fixture1.componentInstance.loadedRows = {
         start: 0,
         end: 10
      };
      fixture1.componentInstance.lastVisibleRow = 10;
      fixture1.componentInstance.verticalScrollTooltip = verticalScrollTooltip;
      fixture1.detectChanges();

      fixture1.whenStable().then(() => {
         let tableCellElement: any = fixture1.nativeElement.querySelectorAll(".table-cell")[1].querySelector("div.table-cell-content");
         expect(tableCellElement.style["text-decoration"]).toEqual("underline line-through");

         done();
      });
   });

   //Bug #18422, highlight dialog can not open
   it("should fire event when highlight action is triggered", (done) => {
      const model = createModel(300, [30, 30, 30], 4);
      const tableActions = new TableActions(model, ViewerContextProviderFactory(false));
      let fixture: ComponentFixture<VSTable> = TestBed.createComponent(VSTable);
      fixture.componentInstance.model = model;
      fixture.componentInstance.actions = tableActions;
      fixture.detectChanges();

      fixture.componentInstance.onOpenHighlightDialog.subscribe((event) => {
         expect(event).toBe(model);
         done();
      });

      tableActions.menuActions[1].actions[1].action(null);
   });

   //Bug #20169
   it("should clear flyover when unselect cell", () => {
      let model = createModel(300, [30, 30, 30], 4);
      model.selectedData = new Map<number, number[]>();
      model.selectedData.set(1, [1]);
      model.hasFlyover = true;
      model.isFlyOnClick = true;
      let fixture: ComponentFixture<VSTable> = TestBed.createComponent(VSTable);
      fixture.componentInstance.model = model;
      fixture.componentInstance.vsInfo = new ViewsheetInfo([], "");
      fixture.componentInstance.flyoverCellSelected = false;
      fixture.componentInstance.clearFlyover(true);

      expect(fixture.componentInstance.model.selectedData).toBeNull();
   });

   //Bug #21413, Bug #21414
   xit("should reload model on cancel action", () => { // broken test
      let model = createModel(300, [30, 30, 30], 4);
      model.selectedData = new Map<number, number[]>();
      model.selectedData.set(1, [1]);
      model.absoluteName = "formTable";
      model.form = true;
      let cellModel = createBaseTableCellModel();

      let fixture: ComponentFixture<VSTable> = TestBed.createComponent(VSTable);
      fixture.componentInstance.model = model;

      let formTable = TestUtils.createMockVSTableModel("formTable");
      formTable.form = true;
      let formService = fixture.debugElement.injector.get(CheckFormDataService);
      formService.addObject(formTable);
      fixture.detectChanges();

      let showMessageDialog = jest.spyOn(ComponentTool, "showMessageDialog");
      showMessageDialog.mockImplementation(() => Promise.resolve("no"));

      fixture.componentInstance.changeCellText("aa", cellModel);
      fixture.detectChanges();

      const requests = httpTestingController.match((req) => {
         return req.url === "../api/formDataCheck";
      });
      requests.forEach(req => req.flush("true"));

      expect(showMessageDialog).toHaveBeenCalled();
      expect(viewsheetClientService.sendEvent).toHaveBeenCalled();
      expect(viewsheetClientService.sendEvent.mock.calls[0][0]).toBe("/events/vsview/object/model");
   });
});
