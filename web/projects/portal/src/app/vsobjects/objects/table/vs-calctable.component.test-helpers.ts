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

import { Subject } from "rxjs";
import { TestUtils } from "../../../common/test/test-utils";
import { ViewsheetInfo } from "../../data/viewsheet-info";
import { LoadTableDataCommand } from "../../command/load-table-data-command";
import { BaseTableCellModel } from "../../model/base-table-cell-model";
import { VSCalcTableModel } from "../../model/vs-calctable-model";
import { VSCalcTable } from "./vs-calctable.component";

export function makeMockCalcTableModel(overrides: Partial<VSCalcTableModel> = {}): VSCalcTableModel {
   const base = TestUtils.createMockVSCalcTableModel("CalcTable1");
   const fmt = TestUtils.createMockVSFormatModel();

   return Object.assign(base, {
      absoluteName: "CalcTable1",
      container: "Container1",
      colWidths: [80, 90, 100],
      colCount: 3,
      rowCount: 10,
      dataRowCount: 10,
      runtimeDataRowCount: 10,
      headerColCount: 1,
      headerRowCount: 1,
      headerRowHeights: [20],
      dataRowHeight: 18,
      rowHeights: null,
      headerRowPositions: [0, 20],
      dataRowPositions: [20, 38, 56],
      scrollHeight: 160,
      objectFormat: {
         ...fmt,
         width: 300,
         height: 180,
         top: 20,
         left: 10,
         zIndex: 5,
      },
      titleFormat: {
         ...TestUtils.createMockVSFormatModel(),
         height: 24,
         width: 300,
      },
      visible: true,
      enabled: true,
      shrink: false,
      maxMode: false,
      wrapped: false,
      titleVisible: true,
      dataTip: null,
      isTipOnClick: false,
      isFlyOnClick: false,
      hasFlyover: false,
      multiSelect: false,
      selectedRegions: [],
      selectedHeaders: null,
      selectedData: null,
      selectedAnnotations: [],
      firstSelectedRow: -1,
      firstSelectedColumn: -1,
      lastSelected: null,
      leftTopAnnotations: [],
      leftBottomAnnotations: [],
      rightTopAnnotations: [],
      rightBottomAnnotations: [],
      dataAnnotationModels: null,
      sortInfo: { row: 0, col: 0, field: "Col A", sortable: true },
   } as any, overrides) as VSCalcTableModel;
}

export function makeCalcTableCell(overrides: Partial<BaseTableCellModel> = {}): BaseTableCellModel {
   return Object.assign({
      row: 0,
      col: 0,
      rowSpan: 1,
      colSpan: 1,
      spanCell: false,
      cellData: "value",
      cellLabel: "value",
      cellWidth: null,
      cellHeight: null,
      underline: false,
      hyperlinks: [],
      bindingType: 1,
      dataPath: {
         path: ["Cell"],
         index: 0,
         col: false,
         row: false,
         type: 0,
         dataType: "",
         level: 0,
         colIndex: 0,
      },
      vsFormatModel: TestUtils.createMockVSFormatModel(),
   } as BaseTableCellModel, overrides);
}

export function makeCalcTableLoadCommand(
   overrides: Partial<LoadTableDataCommand> = {},
): LoadTableDataCommand {
   const headerRow = [
      makeCalcTableCell({ row: 0, col: 0, cellLabel: "H0" }),
      makeCalcTableCell({ row: 0, col: 1, cellLabel: "H1" }),
      makeCalcTableCell({ row: 0, col: 2, cellLabel: "H2" }),
   ];
   const bodyRow = [
      makeCalcTableCell({ row: 1, col: 0, cellLabel: "R1C0" }),
      makeCalcTableCell({ row: 1, col: 1, cellLabel: "R1C1" }),
      makeCalcTableCell({ row: 1, col: 2, cellLabel: "R1C2" }),
   ];

   return Object.assign({
      type: "LoadTableDataCommand",
      start: 0,
      end: 1,
      rowCount: 10,
      colCount: 3,
      tableCells: [headerRow, bodyRow],
      tableHeaderCells: null,
      prototypeCache: {},
      colWidths: [80, 90, 100],
      dataRowCount: 10,
      headerRowCount: 1,
      headerColCount: 1,
      headerRowHeights: [20],
      dataRowHeight: 18,
      headerRowPositions: [0, 20],
      dataRowPositions: [20, 38],
      scrollHeight: 160,
      wrapped: false,
      formChanged: false,
      limitMessage: null,
      rowHyperlinks: [],
   }, overrides) as LoadTableDataCommand;
}

export function createMockCalcTableActions(): any {
   return {
      onAssemblyActionEvent: new Subject<any>(),
      toolbarActions: [{ id: "toolbar-action" }],
      menuActions: [{ id: "menu-action" }],
      getMoreActions: vi.fn(() => [{ id: "more-action" }]),
   };
}

export interface CalcTableTestContext {
   comp: VSCalcTable;
   viewsheetClient: any;
   dropdownService: any;
   downloadService: any;
   modalService: any;
   renderer: any;
   changeDetectorRef: any;
   dataTipService: any;
   pagingControlService: any;
   formDataService: any;
   debounceService: any;
   hyperlinkService: any;
   richTextService: any;
}

export interface CalcTableTestOverrides {
   model?: Partial<VSCalcTableModel>;
   viewsheetClient?: any;
   dropdownService?: any;
   renderer?: any;
   dataTipService?: any;
   pagingControlService?: any;
   formDataService?: any;
   debounceService?: any;
   hyperlinkService?: any;
   richTextService?: any;
   contextProvider?: any;
   tabService?: any;
}

export function createCalcTableComponent(
   overrides: CalcTableTestOverrides = {},
): CalcTableTestContext {
   const viewsheetClient = overrides.viewsheetClient ?? {
      sendEvent: vi.fn(),
      runtimeId: "viewsheet1",
      commands: new Subject<any>().asObservable(),
   };
   const dropdownService = overrides.dropdownService ?? { open: vi.fn() };
   const downloadService = { download: vi.fn() };
   const modalService = { open: vi.fn() };
   const renderer = overrides.renderer ?? {
      setStyle: vi.fn(),
      removeStyle: vi.fn(),
      setAttribute: vi.fn(),
      addClass: vi.fn(),
      removeClass: vi.fn(),
      listen: vi.fn().mockReturnValue(() => {}),
   };
   const changeDetectorRef = { detectChanges: vi.fn(), markForCheck: vi.fn() };
   const dataTipService = overrides.dataTipService ?? {
      isDataTip: vi.fn().mockReturnValue(false),
      isDataTipVisible: vi.fn().mockReturnValue(false),
      isDataTipSource: vi.fn().mockReturnValue(false),
      freeze: vi.fn(),
      unfreeze: vi.fn(),
      hideDataTip: vi.fn(),
      showDataTip: vi.fn(),
   };
   const popComponentService = { isCurrentPopComponent: vi.fn().mockReturnValue(false) };
   const contextProvider = overrides.contextProvider ?? {
      viewer: true,
      preview: false,
      binding: false,
      composer: false,
      vsWizardPreview: false,
      vsWizard: false,
      embedAssembly: false,
   };
   const formDataService = overrides.formDataService ?? {
      checkFormData0: vi.fn(),
      checkFormData: vi.fn(),
      checkTableFormData: vi.fn(),
      hasFormTable: false,
   };
   const debounceService = overrides.debounceService ?? {
      debounce: vi.fn((_: string, fn: () => void) => fn()),
   };
   const scaleSubject = new Subject<number>();
   scaleSubject.next(1);
   const scaleService = {
      getScale: vi.fn().mockReturnValue(scaleSubject.asObservable()),
   };
   const hyperlinkService = overrides.hyperlinkService ?? {
      clickLink: vi.fn(),
      showHyperlinks: vi.fn(),
   };
   const pagingControlService = overrides.pagingControlService ?? {
      setPagingControlModel: vi.fn(),
      scrollTop: vi.fn().mockReturnValue(new Subject<any>().asObservable()),
      scrollLeft: vi.fn().mockReturnValue(new Subject<any>().asObservable()),
      getCurrentAssembly: vi.fn().mockReturnValue("CalcTable1"),
      hasDropdownOrTooltip: false,
   };
   const zone = {
      run: (fn: () => void) => fn(),
      runOutsideAngular: (fn: () => void) => fn(),
   };
   const tabService = overrides.tabService ?? {
      tabDeselected: new Subject<string>().asObservable(),
   };
   const richTextService = overrides.richTextService ?? {
      showAnnotationDialog: vi.fn().mockReturnValue(new Subject<any>().asObservable()),
   };
   const adhocFilterService = {
      showFilter: vi.fn().mockReturnValue(() => {}),
      hideAdhocFilter: vi.fn(),
   };

   const comp = new VSCalcTable(
      viewsheetClient as any,
      dropdownService as any,
      downloadService as any,
      modalService as any,
      renderer as any,
      changeDetectorRef as any,
      dataTipService as any,
      popComponentService as any,
      contextProvider as any,
      formDataService as any,
      debounceService as any,
      scaleService as any,
      hyperlinkService as any,
      pagingControlService as any,
      {} as any,
      zone as any,
      tabService as any,
      null,
      richTextService as any,
      adhocFilterService as any,
   );

   const tableBounds = {
      left: 20,
      top: 30,
      width: 300,
      height: 180,
      right: 320,
      bottom: 210,
   };

   (comp as any).tableContainer = {
      nativeElement: {
         getBoundingClientRect: vi.fn(() => tableBounds),
      },
   };
   (comp as any).verticalScrollTooltip = {
      isOpen: vi.fn().mockReturnValue(false),
      open: vi.fn(),
      close: vi.fn(),
   };
   (comp as any).horizontalScrollTooltip = {
      isOpen: vi.fn().mockReturnValue(false),
      open: vi.fn(),
      close: vi.fn(),
   };
   (comp as any).verticalScrollWrapper = {
      nativeElement: {
         scrollTop: 0,
         style: { height: "40" },
         firstElementChild: { style: { height: "160" }, getBoundingClientRect: () => ({ height: 160 }) },
      },
   };
   (comp as any).horizontalScrollWrapper = {
      nativeElement: {
         scrollLeft: 0,
         style: { width: "120" },
         firstElementChild: { style: { width: "220" } },
         getBoundingClientRect: () => ({ bottom: 40 }),
      },
   };
   (comp as any).lowerTable = { nativeElement: {} };
   (comp as any).rightTopDiv = { nativeElement: {} };
   (comp as any).leftBottomDiv = { nativeElement: {} };
   (comp as any).loadingMessage = { nativeElement: {} };
   (comp as any).cells = [];

   comp.vsInfo = new ViewsheetInfo([], null, false, "viewsheet1");
   comp.model = makeMockCalcTableModel(overrides.model ?? {});

   return {
      comp,
      viewsheetClient,
      dropdownService,
      downloadService,
      modalService,
      renderer,
      changeDetectorRef,
      dataTipService,
      pagingControlService,
      formDataService,
      debounceService,
      hyperlinkService,
      richTextService,
   };
}
