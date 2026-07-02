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
import { CommonModule } from "@angular/common";
import { HttpClient } from "@angular/common/http";
import { HttpClientTestingModule, HttpTestingController } from "@angular/common/http/testing";
import { NO_ERRORS_SCHEMA, Renderer2 } from "@angular/core";
import { waitForAsync, ComponentFixture, TestBed } from "@angular/core/testing";
import { FormsModule, ReactiveFormsModule } from "@angular/forms";
import { Router } from "@angular/router";
import { NgbModule } from "@ng-bootstrap/ng-bootstrap";
import { of as observableOf, Subject } from "rxjs";
import { DownloadService } from "../../../../../../shared/download/download.service";
import { AppInfoService } from "../../../../../../shared/util/app-info.service";
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
import { ContextProvider } from "../../context-provider.service";
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
import { TimerService } from "../data-tip/timer.service";
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
   let timerService: any;

   beforeEach(waitForAsync(() => {
      viewsheetClientService = { sendEvent: vi.fn() };
      viewsheetClientService.commands = observableOf([]);
      viewsheetClientService.runtimeId = "vs1_111";
      dndService = {};
      viewDataService = {};
      dataTipService = { isDataTip: vi.fn(), isDataTipSource: vi.fn() };
      dataTipService.isDataTip.mockImplementation(() => false);
      downloadService = { download: vi.fn() };
      debounceService = { debounce: vi.fn() };
      modelService = { sendModel: vi.fn() };
      contextProvider = { viewer: true };
      adhocFilterService = { showFilter: vi.fn(), adhocFilterShowing: false };
      router = {
         navigate: vi.fn(),
         events: new Subject<any>()
      };
      richTextService = {
         showAnnotationDialog: vi.fn()
      };
      vsTabService = { };
      vsTabService.tabDeselected = observableOf(null);

      verticalScrollTooltip = { isOpen: vi.fn() };
      timerService = {
         defer: vi.fn((fn) => {
            fn();
         })
      };

      TestBed.configureTestingModule({
         imports: [
            CommonModule,
            FormsModule,
            ReactiveFormsModule,
            NgbModule,
            DropDownTestModule,
            HttpClientTestingModule,
            VSTable,
            VSTableCell,
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
            {provide: VSTabService, useValue: vsTabService},
            AppInfoService,
            {provide: TimerService, useValue: timerService},
         ]
      });
      TestBed.compileComponents();

      httpClient = TestBed.inject(HttpClient);
      httpTestingController = TestBed.inject(HttpTestingController);
   }));

   /*
    * Migrated to ATL + MSW TL specs (direct-instantiation, no TestBed overhead):
    *   displayColWidths expand-last-col
    *     → vs-table.component.display.tl.spec.ts  Group 11
    *   getObjectTop shrunk + bottomTabs
    *     → vs-table.component.display.tl.spec.ts  Group 12
    *   firstSelectedRow / firstSelectedColumn init (Bug #9839)
    *     → vs-table.component.interaction.tl.spec.ts  Group 1
    *   isDraggable (Bug #9913)
    *     → vs-table.component.interaction.tl.spec.ts  Group 11
    */

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
   it("should have underline and line-through text-decoration", () => new Promise<void>((done) => {
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
   }));

   // Migrated to ATL + MSW TL specs:
   //   onOpenHighlightDialog "table highlight" action (Bug #18422)
   //     → vs-table.component.interaction.tl.spec.ts  Group 13
   //   clearFlyover force=true / selectedData=null (Bug #20169)
   //     → vs-table.component.interaction.tl.spec.ts  Group 12

   //Bug #21413, Bug #21414
   it.skip("should reload model on cancel action", () => { // broken test
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

      let showMessageDialog = vi.spyOn(ComponentTool, "showMessageDialog");
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
