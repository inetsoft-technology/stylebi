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
 * Shared test helpers for VSTable multi-pass TL specs.
 *
 * VSTable extends BaseTable<VSTableModel> and has a 22-parameter constructor.
 * Direct instantiation (not ATL render()) is used throughout because the DI chain
 * is too deep for render() without a full Angular module — same pattern as VSCrosstab.
 */

import { Subject } from "rxjs";
import { TestUtils } from "../../../common/test/test-utils";
import { ViewsheetInfo } from "../../data/viewsheet-info";
import { VSTable } from "./vs-table.component";
import { VSTableModel } from "../../model/vs-table-model";
import { BaseTableCellModel } from "../../model/base-table-cell-model";
import { LoadTableDataCommand } from "../../command/load-table-data-command";

export function makeMockTableModel(overrides: Partial<VSTableModel> = {}): VSTableModel {
   const base = TestUtils.createMockVSTableModel("Table1");
   const fmt = TestUtils.createMockVSFormatModel();

   return Object.assign(base, {
      absoluteName: "Table1",
      colWidths: [100, 100, 100],
      colCount: 3,
      headerColCount: 0,
      headerRowCount: 1,
      headerRowHeights: [20],
      dataRowHeight: 18,
      rowCount: 10,
      objectFormat: { ...fmt, width: 300, height: 200, top: 50 },
      titleFormat: { ...TestUtils.createMockVSFormatModel(), height: 20 },
      colNames: ["Col A", "Col B", "Col C"],
      headersSortType: [0, 1, 2],
      sortPositions: [-1, 0, 1],
      sortInfo: { row: 0, col: 0, field: "Col A", sortable: false },
      selectedData: null,
      selectedHeaders: null,
      dataAnnotationModels: null,
      leftTopAnnotations: [],
      leftBottomAnnotations: [],
      firstSelectedRow: -1,
      firstSelectedColumn: -1,
      lastSelected: null,
      form: false,
      embedded: false,
      multiSelect: false,
      enabled: true,
      shrink: false,
      titleVisible: true,
      hasFlyover: false,
      dataTip: null,
   } as any, overrides) as VSTableModel;
}

export function makeTableCell(overrides: Partial<BaseTableCellModel> = {}): BaseTableCellModel {
   return Object.assign({
      row: 0,
      col: 0,
      cellData: "value",
      cellLabel: "Header A",
      cellWidth: null,
      colSpan: 1,
      rowSpan: 1,
      spanCell: false,
      field: "Col A",
      dataPath: { level: 0, col: false, row: false, type: 0, dataType: "", path: [], index: 0, colIndex: 0 },
      hyperlinks: [],
      underline: false,
      grouped: false,
      vsFormatModel: TestUtils.createMockVSFormatModel(),
   } as BaseTableCellModel, overrides);
}

export function makeLoadTableDataCommand(overrides: Partial<LoadTableDataCommand> = {}): LoadTableDataCommand {
   const hdr = [
      makeTableCell({ row: 0, col: 0, cellLabel: "Col A" }),
      makeTableCell({ row: 0, col: 1, cellLabel: "Col B" }),
      makeTableCell({ row: 0, col: 2, cellLabel: "Col C" }),
   ];
   const row1 = [
      makeTableCell({ row: 1, col: 0, cellLabel: "A1" }),
      makeTableCell({ row: 1, col: 1, cellLabel: "B1" }),
      makeTableCell({ row: 1, col: 2, cellLabel: "C1" }),
   ];
   return Object.assign({
      type: "LoadTableDataCommand",
      start: 0,
      end: 2,
      rowCount: 10,
      colCount: 3,
      tableCells: [hdr, row1],
      tableHeaderCells: null,
      prototypeCache: {},
      colWidths: [100, 100, 100],
      dataRowCount: 2,
      headerRowCount: 1,
      headerColCount: 0,
      headerRowHeights: [20],
      dataRowHeight: 18,
      headerRowPositions: [0, 20],
      dataRowPositions: [20, 38],
      scrollHeight: 180,
      wrapped: false,
      formChanged: false,
      limitMessage: null,
      rowHyperlinks: [],
   }, overrides) as LoadTableDataCommand;
}

export function createMockViewsheetClient(): any {
   const commandsSubject = new Subject<any>();
   return {
      sendEvent: vi.fn(),
      commands: commandsSubject.asObservable(),
      commandsSubject,
      runtimeId: "viewsheet1",
   };
}

export function createMockActions(): any {
   const onAssemblyActionEvent = new Subject<any>();
   return {
      onAssemblyActionEvent,
      toolbarActions: [],
      menuActions: [],
      getMoreActions: vi.fn(() => []),
   };
}

export interface TableTestContext {
   comp: VSTable;
   viewsheetClient: any;
   changeDetectorRef: any;
   pagingControlService: any;
   dataTipService: any;
   renderer: any;
   debounceService: any;
   adhocFilterService: any;
   hyperlinkService: any;
   formDataService: any;
   richTextService: any;
}

export interface TableTestOverrides {
   viewsheetClient?: any;
   tabService?: any;
   pagingControlService?: any;
   scaleService?: any;
   dataTipService?: any;
   contextProvider?: any;
   renderer?: any;
   dialogService?: any;
   debounceService?: any;
   adhocFilterService?: any;
   richTextService?: any;
   hyperlinkService?: any;
   formDataService?: any;
   model?: Partial<VSTableModel>;
}

export function createTableComponent(overrides: TableTestOverrides = {}): TableTestContext {
   const scaleSubject = new Subject<number>();
   const viewsheetClient = overrides.viewsheetClient ?? createMockViewsheetClient();
   const changeDetectorRef = { detectChanges: vi.fn(), markForCheck: vi.fn() };

   const tabDeselectedSubject = new Subject<string>();
   const tabService = overrides.tabService ?? {
      tabDeselected: tabDeselectedSubject.asObservable(),
   };

   const pagingControlService = overrides.pagingControlService ?? {
      setPagingControlModel: vi.fn(),
      scrollTop: vi.fn().mockReturnValue(new Subject<any>().asObservable()),
      scrollLeft: vi.fn().mockReturnValue(new Subject<any>().asObservable()),
      getCurrentAssembly: vi.fn().mockReturnValue("Table1"),
      hasDropdownOrTooltip: false,
   };

   const scaleService = overrides.scaleService ?? {
      getScale: vi.fn().mockReturnValue(scaleSubject.asObservable()),
   };

   const dataTipService = overrides.dataTipService ?? {
      isDataTip: vi.fn().mockReturnValue(false),
      isDataTipVisible: vi.fn().mockReturnValue(false),
      freeze: vi.fn(),
      unfreeze: vi.fn(),
      hideDataTip: vi.fn(),
      isFrozen: vi.fn().mockReturnValue(false),
      showDataTip: vi.fn(),
   };

   const contextProvider = overrides.contextProvider ?? {
      viewer: true,
      preview: false,
      binding: false,
      composer: false,
      vsWizardPreview: false,
      vsWizard: false,
      embedAssembly: false,
   };

   const renderer = overrides.renderer ?? {
      setStyle: vi.fn(),
      removeStyle: vi.fn(),
      setAttribute: vi.fn(),
      addClass: vi.fn(),
      removeClass: vi.fn(),
      listen: vi.fn().mockReturnValue(() => {}),
   };

   const debounceService = overrides.debounceService ?? {
      debounce: vi.fn(),
   };

   const adhocFilterService = overrides.adhocFilterService ?? {
      showFilter: vi.fn().mockReturnValue(() => {}),
      hideAdhocFilter: vi.fn(),
   };

   const hyperlinkService = overrides.hyperlinkService ?? {
      clickLink: vi.fn(),
      showHyperlinks: vi.fn(),
   };

   const formDataService = overrides.formDataService ?? {
      checkFormData0: vi.fn(),
      checkFormData: vi.fn(),
      checkTableFormData: vi.fn(),
      hasFormTable: false,
   };

   const richTextService = overrides.richTextService ?? {
      showAnnotationDialog: vi.fn().mockReturnValue(new Subject<any>().asObservable()),
   };

   const comp = new VSTable(
      viewsheetClient as any,
      { setDragStartStyle: vi.fn(), isAllEmbeddedColumn: vi.fn().mockReturnValue(true) } as any,  // dndService
      { getDragData: vi.fn().mockReturnValue({}) } as any,                                        // dragService
      { open: vi.fn() } as any,                                                                    // dropdownService
      { saveFile: vi.fn() } as any,                                                                // downloadService
      { open: vi.fn() } as any,                                                                    // ngbModal
      renderer as any,
      changeDetectorRef as any,
      dataTipService as any,
      { isCurrentPopComponent: vi.fn().mockReturnValue(false) } as any,                           // popComponentService
      pagingControlService as any,
      contextProvider as any,
      formDataService as any,
      debounceService as any,
      scaleService as any,
      hyperlinkService as any,
      {} as any,                                                                                    // http (not called directly)
      { run: (fn: any) => fn(), runOutsideAngular: (fn: any) => fn() } as any,                    // zone
      tabService as any,
      null,                                                                                         // @Optional() viewerResizeService
      richTextService as any,
      adhocFilterService as any,
   );

   // ViewChild refs must be set before comp.model to prevent crashes from updateLayout().
   (comp as any).verticalScrollTooltip = {
      isOpen: vi.fn().mockReturnValue(false), open: vi.fn(), close: vi.fn(),
   };
   (comp as any).horizontalScrollTooltip = {
      isOpen: vi.fn().mockReturnValue(false), open: vi.fn(), close: vi.fn(),
   };
   (comp as any).verticalScrollWrapper = { nativeElement: { scrollTop: 0 } };
   (comp as any).horizontalScrollWrapper = { nativeElement: { scrollLeft: 0 } };
   // checkScroll() uses dataTable and tableHeaderDiv
   (comp as any).dataTable = { nativeElement: { offsetHeight: 0, offsetWidth: 0 } };
   (comp as any).tableHeaderDiv = { nativeElement: {} };

   comp.vsInfo = new ViewsheetInfo([], null, false, "viewsheet1");
   comp.model = makeMockTableModel(overrides.model ?? {});

   return {
      comp,
      viewsheetClient,
      changeDetectorRef,
      pagingControlService,
      dataTipService,
      renderer,
      debounceService,
      adhocFilterService,
      hyperlinkService,
      formDataService,
      richTextService,
   };
}
