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
 * Shared test helpers for VSCrosstab multi-pass TL specs.
 *
 * VSCrosstab extends BaseTable<VSCrosstabModel> and has a 23-parameter constructor.
 * Direct instantiation (not ATL render()) is used throughout because the DI chain
 * is too deep for render() without a full Angular module — same pattern as VSSelection.
 */

import { Subject } from "rxjs";
import { TestUtils } from "../../../common/test/test-utils";
import { ViewsheetInfo } from "../../data/viewsheet-info";
import { VSCrosstab } from "./vs-crosstab.component";
import { VSCrosstabModel } from "../../model/vs-crosstab-model";
import { BaseTableCellModel } from "../../model/base-table-cell-model";
import { LoadTableDataCommand } from "../../command/load-table-data-command";

export function makeMockCrosstabModel(overrides: Partial<VSCrosstabModel> = {}): VSCrosstabModel {
   const base = TestUtils.createMockVSCrosstabModel("Crosstab1");
   const fmt = TestUtils.createMockVSFormatModel();

   // readonly fields are cast via any so tests can seed them directly.
   return Object.assign(base, {
      absoluteName: "Crosstab1",
      colWidths: [100, 100, 100],
      colCount: 3,
      headerColCount: 1,
      headerRowCount: 1,
      headerRowHeights: [20],
      dataRowHeight: 18,
      objectFormat: { ...fmt, width: 300, height: 200, top: 50 },
      titleFormat: { ...TestUtils.createMockVSFormatModel(), height: 20 },
      aggrNames: [],
      timeSeriesNames: [],
      sortTypeMap: {},
      hasHiddenColumn: false,
      selectedRegions: null,
      selectedHeaders: null,
      selectedData: null,
      dataAnnotationModels: null,
      leftTopAnnotations: [],
      leftBottomAnnotations: [],
      rightTopAnnotations: [],
      rightBottomAnnotations: [],
      cells: null,
      sortInfo: { row: 0, col: 0, field: "col1", sortable: false },
      sortDimension: false,
      sortOnHeader: false,
      container: null,
      multiSelect: false,
      firstSelectedRow: null,
      firstSelectedColumn: null,
      dateComparisonDescription: null,
      drillTip: null,
   } as any, overrides) as VSCrosstabModel;
}

export function makeTableCell(overrides: Partial<BaseTableCellModel> = {}): BaseTableCellModel {
   return Object.assign({
      row: 0,
      col: 0,
      cellData: "value",
      cellLabel: "value",
      cellWidth: null,
      colSpan: 1,
      rowSpan: 1,
      spanCell: false,
      field: "col1",
      dataPath: null as any,
      hyperlinks: [],
      underline: false,
      drillOp: null,
      grouped: false,
      period: false,
      totalRow: false,
      totalCol: false,
      grandTotalRow: false,
      grandTotalCol: false,
      hasCalc: false,
      vsFormatModel: TestUtils.createMockVSFormatModel(),
   } as BaseTableCellModel, overrides);
}

export function makeLoadTableDataCommand(overrides: Partial<LoadTableDataCommand> = {}): LoadTableDataCommand {
   const row0 = [
      makeTableCell({ row: 0, col: 0 }),
      makeTableCell({ row: 0, col: 1 }),
      makeTableCell({ row: 0, col: 2 }),
   ];
   const row1 = [
      makeTableCell({ row: 1, col: 0 }),
      makeTableCell({ row: 1, col: 1, cellData: "100" }),
      makeTableCell({ row: 1, col: 2, cellData: "200" }),
   ];
   return Object.assign({
      type: "LoadTableDataCommand",
      start: 0,
      end: 2,
      rowCount: 10,
      colCount: 3,
      tableCells: [row0, row1],
      tableHeaderCells: null,
      prototypeCache: {},
      runtimeRowHeaderCount: 1,
      runtimeColHeaderCount: 1,
      runtimeDataRowCount: 1,
      colWidths: [100, 100, 100],
      dataRowCount: 2,
      headerRowCount: 1,
      headerColCount: 1,
      headerRowHeights: [20],
      dataRowHeight: 18,
      headerRowPositions: [0],
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

export function createMockModelService(): any {
   return {
      getModel: vi.fn().mockReturnValue(new Subject<any>().asObservable()),
      sendModel: vi.fn().mockReturnValue(new Subject<any>().asObservable()),
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

export interface CrosstabTestContext {
   comp: VSCrosstab;
   viewsheetClient: any;
   modelService: any;
   changeDetectorRef: any;
   pagingControlService: any;
   dataTipService: any;
   dialogService: any;
   renderer: any;
   debounceService: any;
   adhocFilterService: any;
}

/** Typed overrides for createCrosstabComponent — every field is optional. */
export interface CrosstabTestOverrides {
   viewsheetClient?: any;
   modelService?: any;
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
   model?: Partial<VSCrosstabModel>;
}

export function createCrosstabComponent(overrides: CrosstabTestOverrides = {}): CrosstabTestContext {
   const scaleSubject = new Subject<number>();
   const viewsheetClient = overrides.viewsheetClient ?? createMockViewsheetClient();
   const modelService = overrides.modelService ?? createMockModelService();
   const changeDetectorRef = { detectChanges: vi.fn(), markForCheck: vi.fn() };

   const tabDeselectedSubject = new Subject<string>();
   const tabService = overrides.tabService ?? {
      tabDeselected: tabDeselectedSubject.asObservable(),
   };

   const pagingControlService = overrides.pagingControlService ?? {
      setPagingControlModel: vi.fn(),
      scrollTop: vi.fn().mockReturnValue(new Subject<any>().asObservable()),
      scrollLeft: vi.fn().mockReturnValue(new Subject<any>().asObservable()),
      getCurrentAssembly: vi.fn().mockReturnValue("Crosstab1"),
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
   };

   const contextProvider = overrides.contextProvider ?? {
      viewer: true,
      preview: false,
      binding: false,
      composer: false,
      vsWizardPreview: false,
   };

   const renderer = overrides.renderer ?? {
      setStyle: vi.fn(),
      removeStyle: vi.fn(),
      setAttribute: vi.fn(),
      addClass: vi.fn(),
      removeClass: vi.fn(),
      listen: vi.fn().mockReturnValue(() => {}),
   };

   const dialogService = overrides.dialogService ?? {
      open: vi.fn(),
      hasSlideout: vi.fn().mockReturnValue(false),
      showByModel: vi.fn(),
   };

   const debounceService = overrides.debounceService ?? {
      debounce: vi.fn(),
   };

   const adhocFilterService = overrides.adhocFilterService ?? {
      showFilter: vi.fn().mockReturnValue(() => {}),
      hideAdhocFilter: vi.fn(),
   };

   const comp = new VSCrosstab(
      viewsheetClient as any,
      { open: vi.fn() } as any,                                              // dropdownService
      { saveFile: vi.fn() } as any,                                          // downloadService
      { open: vi.fn() } as any,                                              // ngbModal
      dialogService as any,
      modelService as any,
      renderer as any,
      changeDetectorRef as any,
      dataTipService as any,
      { isCurrentPopComponent: vi.fn().mockReturnValue(false) } as any,      // popComponentService
      pagingControlService as any,
      contextProvider as any,
      {} as any,                                                              // formDataService
      debounceService as any,
      scaleService as any,
      { showHyperlinks: vi.fn() } as any,                                    // hyperlinkService
      { setDragStartStyle: vi.fn(), dragTableSource: null } as any,          // dndService
      {} as any,                                                              // http (not used directly)
      { run: (fn: any) => fn(), runOutsideAngular: (fn: any) => fn() } as any,  // zone
      tabService as any,
      null,                                                                   // @Optional() viewerResizeService
      (overrides.richTextService ?? { showAnnotationDialog: vi.fn().mockReturnValue(new Subject<any>().asObservable()) }) as any,  // richTextService
      adhocFilterService as any,
   );

   // ViewChild tooltip mocks must be set before comp.model to prevent crash in
   // updateVerticalScrollTooltip(), which is called from updateVisibleRows() inside updateLayout().
   (comp as any).verticalScrollTooltip = {
      isOpen: vi.fn().mockReturnValue(false), open: vi.fn(), close: vi.fn(),
   };
   (comp as any).horizontalScrollTooltip = {
      isOpen: vi.fn().mockReturnValue(false), open: vi.fn(), close: vi.fn(),
   };
   (comp as any).verticalScrollWrapper = { nativeElement: { scrollTop: 0, scrollLeft: 0 } };
   (comp as any).horizontalScrollWrapper = { nativeElement: { scrollLeft: 0 } };

   comp.vsInfo = new ViewsheetInfo([], null, false, "viewsheet1");
   comp.model = makeMockCrosstabModel(overrides.model ?? {});

   return {
      comp,
      viewsheetClient,
      modelService,
      changeDetectorRef,
      pagingControlService,
      dataTipService,
      dialogService,
      renderer,
      debounceService,
      adhocFilterService,
   };
}
