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
 * CalcTableLayoutPane — single pass
 *
 * Direct instantiation — seven constructor dependencies (AiAssistantService,
 * VSCalcTableEditorService, ViewsheetClientService, ChangeDetectorRef, Renderer2, DOCUMENT
 * token, NgZone), none via `inject()`. `clientService.commands` is a real Subject so command
 * routing can be exercised via `.next(...)` (see Group I). @ViewChild refs (colResize,
 * rowResize, tableContainer) are bypass-assigned directly since the template is never rendered.
 *
 * Scope: fills the gap flagged in the prescan against the existing vs-calc-table-layout.spec.ts
 * (kept — its two tests exercise selectCell/getCellContent through a hand-built instance
 * predating this suite; one is `.skip`'d for clickCell's setTimeout, which this suite now
 * covers with fake timers): clickCell's async/setTimeout dispatch, the resize flow
 * (resizeCell/onMouseMove/onMouseUp), all six command processors, createSpanMap/findBaseCell,
 * addShiftCells/mergeDimension/validateDimension, getCellStyle/setSpanCellStyle, and the
 * loading-count state — plus baseline coverage of every other public method.
 *
 * Risk-first coverage:
 *   Group E [Risk 3] — clickCell: setTimeout-deferred selection dispatch, the highest-value
 *                       gap called out in the prescan (previously `.skip`'d entirely)
 *   Group I [Risk 3] — command processors: six process*Command handlers + one commandsSubject
 *                       routing-proof dispatch test
 *   Group H [Risk 3] — addShiftCells/mergeDimension/validateDimension: recursive rect-growth
 *                       geometry algorithm
 *   Group J [Risk 2] — resize flow: resizeCell/onMouseMove/onMouseUp guard + dispatch
 *   Group D [Risk 2] — getCellStyle/getBackground/setSelectStyle/setSpanCellStyle: style
 *                       computation branches
 *   Group G [Risk 2] — findBaseCell: merged-cell base-cell resolution
 *   Remaining groups [Risk 1/2] — single-purpose getters/setters and lifecycle hooks
 *
 * Confirmed bugs (it.fails): none
 */

import { Subject } from "rxjs";
import { CalcTableLayoutPane } from "./vs-calc-table-layout.component";
import { CellBindingInfo } from "../../binding/data/table/cell-binding-info";
import { Rectangle } from "../../common/data/rectangle";
import { CalcTableCell } from "../../common/data/tablelayout/calc-table-cell";
import { CalcTableLayout } from "../../common/data/tablelayout/calc-table-layout";
import { CalcTableRow } from "../../common/data/tablelayout/calc-table-row";
import { TableColumn } from "../../common/data/tablelayout/table-column";
import { ViewsheetCommandMessage } from "../../common/viewsheet-client/viewsheet-command-message";
import { VSCalcTableModel } from "../../vsobjects/model/vs-calctable-model";
import { GetTableLayoutCommand } from "../../binding/command/get-table-layout-command";
import { GetCellBindingCommand } from "../../binding/command/get-cell-binding-command";
import { GetCellScriptCommand } from "../../binding/command/get-cell-script-command";
import { GetPredefinedNamedGroupCommand } from "../../binding/command/get-predefined-named-group-command";
import { AssemblyLoadingCommand } from "../../vsobjects/command/assembly-loading-command";
import { ClearAssemblyLoadingCommand } from "../../vsobjects/command/clear-assembly-loading-command";

afterEach(() => {
   vi.restoreAllMocks();

   if(vi.isFakeTimers()) {
      vi.useRealTimers();
   }
});

function makeCellBindingInfo(overrides: Partial<CellBindingInfo> = {}): CellBindingInfo {
   return Object.assign(new CellBindingInfo(), {
      type: CellBindingInfo.BIND_TEXT,
      btype: null,
      expansion: CellBindingInfo.EXPAND_NONE,
      value: "",
   }, overrides);
}

function makeCell(row: number, col: number, overrides: Partial<CalcTableCell> = {}): CalcTableCell {
   return Object.assign(new CalcTableCell(), {
      row, col,
      vsFormat: null,
      text: "",
      span: null,
      baseInfo: null,
      cellPath: { level: 0, col: false, row: false, type: 0, dataType: "string", path: [], index: 0, colIndex: 0 },
      bindingType: 0,
   }, overrides);
}

// Builds a plain rows x cols grid of non-spanned, non-merged cells — a safe default for tests
// that need a real tableModel but don't care about span/merge geometry.
function makeTableModel(rows: number, cols: number): CalcTableLayout {
   const tableRows: CalcTableRow[] = [];

   for(let r = 0; r < rows; r++) {
      const tableCells: CalcTableCell[] = [];

      for(let c = 0; c < cols; c++) {
         tableCells.push(makeCell(r, c));
      }

      tableRows.push(Object.assign(new CalcTableRow(), { text: "", height: 20, row: r, format: null, tableCells }));
   }

   const tableColumns: TableColumn[] = [];

   for(let c = 0; c < cols; c++) {
      tableColumns.push(Object.assign(new TableColumn(), { width: 80, col: c, format: null }));
   }

   return { tableRows, tableColumns, selectedCells: [], selectedRect: new Rectangle(0, 0, 0, 0), selectedRegions: [] };
}

function createComponent(opts: { vsObjectModel?: VSCalcTableModel } = {}) {
   const aiAssistantService = {
      setCalcTableScriptContext: vi.fn(),
      setCalcTableRetrievalScriptContext: vi.fn(),
      setCalcTableBindingContext: vi.fn(),
      calcTableCellBindings: {} as any,
      calcTableAggregates: [] as string[],
   };
   const editorService = {
      loadTableModel: vi.fn(),
      loadCellBinding: vi.fn(),
      getTableLayout: vi.fn(),
      setTableLayout: vi.fn(),
      resetCellBinding: vi.fn(),
      getCellBinding: vi.fn(() => makeCellBindingInfo()),
      setCellBinding: vi.fn(),
      namedGroups: [] as string[],
   };
   const commands = new Subject<ViewsheetCommandMessage>();
   const clientService = { commands, sendEvent: vi.fn() };
   const changeRef = { detectChanges: vi.fn() };
   const renderer = { setStyle: vi.fn() };
   const document = { addEventListener: vi.fn(), removeEventListener: vi.fn() };
   const zone = { run: (fn: Function) => fn(), runOutsideAngular: (fn: Function) => fn() };

   const comp = new CalcTableLayoutPane(
      aiAssistantService as any, editorService as any, clientService as any,
      changeRef as any, renderer as any, document as any, zone as any);
   comp.vsObjectModel = opts.vsObjectModel ?? ({ absoluteName: "Calc1" } as VSCalcTableModel);

   return { comp, aiAssistantService, editorService, clientService, changeRef, renderer, document };
}

// ---------------------------------------------------------------------------
// Group A: vsObject setter / getAssemblyName
// ---------------------------------------------------------------------------

describe("CalcTableLayoutPane — vsObject setter / getAssemblyName", () => {
   it("should load the table model and set vsObjectModel when vsObject is assigned", () => {
      const { comp, editorService } = createComponent();
      const model = { absoluteName: "Calc2" } as VSCalcTableModel;

      comp.vsObject = model;

      expect(editorService.loadTableModel).toHaveBeenCalled();
      expect(comp.vsObjectModel).toBe(model);
   });

   it("getAssemblyName should return the model's absoluteName when a model is set", () => {
      const { comp } = createComponent({ vsObjectModel: { absoluteName: "Calc3" } as VSCalcTableModel });

      expect(comp.getAssemblyName()).toBe("Calc3");
   });

   it("getAssemblyName should return null when no model is set", () => {
      const { comp } = createComponent();
      comp.vsObjectModel = null;

      expect(comp.getAssemblyName()).toBeNull();
   });
});

// ---------------------------------------------------------------------------
// Group B: ngAfterViewChecked / onScroll
// ---------------------------------------------------------------------------

// Bypass: scrollTop/scrollLeft are private with no public accessor; read/written via `as any`
// casts to observe/seed the state ngAfterViewChecked and onScroll synchronize.
describe("CalcTableLayoutPane — ngAfterViewChecked / onScroll", () => {
   it("should sync scrollTop/scrollLeft onto the table container when it exists", () => {
      const { comp } = createComponent();
      const nativeElement: any = { scrollTop: 0, scrollLeft: 0 };
      comp.tableContainer = { nativeElement } as any;
      (comp as any).scrollTop = 42;
      (comp as any).scrollLeft = 24;

      comp.ngAfterViewChecked();

      expect(nativeElement.scrollTop).toBe(42);
      expect(nativeElement.scrollLeft).toBe(24);
   });

   it("should do nothing when the table container is not yet available", () => {
      const { comp } = createComponent();
      comp.tableContainer = undefined as any;

      expect(() => comp.ngAfterViewChecked()).not.toThrow();
   });

   it("onScroll should read scrollLeft/scrollTop from the table container", () => {
      const { comp } = createComponent();
      comp.tableContainer = { nativeElement: { scrollTop: 15, scrollLeft: 30 } } as any;

      comp.onScroll(new Event("scroll"));

      expect((comp as any).scrollTop).toBe(15);
      expect((comp as any).scrollLeft).toBe(30);
   });
});

// ---------------------------------------------------------------------------
// Group C: getSpanCell / selectedCells / selectedRect
// ---------------------------------------------------------------------------

// Bypass: spanmap is private with no seeding API of its own (only createSpanMap populates it,
// tested separately in Group I); the getSpanCell test writes a fixture entry via `as any`.
describe("CalcTableLayoutPane — getSpanCell / selectedCells / selectedRect", () => {
   it("getSpanCell should return the cell registered at the given row,col key", () => {
      const { comp } = createComponent();
      const cell = makeCell(1, 2);
      (comp as any).spanmap["1,2"] = cell;

      expect(comp.getSpanCell(1, 2)).toBe(cell);
   });

   it("selectedCells should return an empty array when there is no tableModel", () => {
      const { comp } = createComponent();

      expect(comp.selectedCells).toEqual([]);
   });

   it("selectedCells should return an empty array when the tableModel has no selectedCells", () => {
      const { comp } = createComponent();
      comp.tableModel = { ...makeTableModel(1, 1), selectedCells: null as any };

      expect(comp.selectedCells).toEqual([]);
   });

   it("selectedCells should return the tableModel's selectedCells when present", () => {
      const { comp } = createComponent();
      const cells = [makeCell(0, 0)];
      comp.tableModel = { ...makeTableModel(1, 1), selectedCells: cells };

      expect(comp.selectedCells).toBe(cells);
   });

   it("selectedRect getter should return an empty Rectangle when there is no tableModel", () => {
      const { comp } = createComponent();

      expect(comp.selectedRect).toEqual(new Rectangle(0, 0, 0, 0));
   });

   it("selectedRect getter should return the tableModel's selectedRect when present", () => {
      const { comp } = createComponent();
      const rect = new Rectangle(1, 2, 3, 4);
      comp.tableModel = { ...makeTableModel(1, 1), selectedRect: rect };

      expect(comp.selectedRect).toBe(rect);
   });

   it("selectedRect setter should update the tableModel's selectedRect", () => {
      const { comp } = createComponent();
      comp.tableModel = makeTableModel(1, 1);
      const rect = new Rectangle(5, 6, 7, 8);

      comp.selectedRect = rect;

      expect(comp.tableModel.selectedRect).toBe(rect);
   });
});

// ---------------------------------------------------------------------------
// Group D: getCellStyle / getBackground / setSelectStyle / setSpanCellStyle
// ---------------------------------------------------------------------------

describe("CalcTableLayoutPane — getCellStyle / getBackground", () => {
   it("getCellStyle should compose height/width/background from the tableModel geometry", () => {
      const { comp } = createComponent();
      comp.tableModel = makeTableModel(1, 1);
      comp.tableModel.tableRows[0].height = 30;
      comp.tableModel.tableColumns[0].width = 90;
      const cell = comp.tableModel.tableRows[0].tableCells[0];
      cell.vsFormat = { background: "#ff0000" } as any;

      const style = comp.getCellStyle(cell, 0, 0);

      expect(style["height"]).toBe("30px");
      expect(style["width"]).toBe("90px");
      expect(style["background-color"]).toBe("#ff0000");
   });

   it("getBackground should return null when the cell has no format", () => {
      const { comp } = createComponent();

      expect(comp.getBackground(makeCell(0, 0, { vsFormat: null }), 0, 0)).toBeNull();
   });

   it("getBackground should return null when the format has no background", () => {
      const { comp } = createComponent();

      expect(comp.getBackground(makeCell(0, 0, { vsFormat: { background: null } as any }), 0, 0)).toBeNull();
   });

   it("getBackground should return the format's background when present", () => {
      const { comp } = createComponent();

      expect(comp.getBackground(makeCell(0, 0, { vsFormat: { background: "#00ff00" } as any }), 0, 0)).toBe("#00ff00");
   });
});

describe("CalcTableLayoutPane — setSelectStyle", () => {
   it("should do nothing when selectedCells is null", () => {
      const { comp } = createComponent();
      const style: any = {};

      comp.setSelectStyle(style, makeCell(0, 0), 0, 0);

      expect(style["background-color"]).toBeUndefined();
   });

   it("should highlight a cell that directly matches a selected cell's row/col", () => {
      const { comp } = createComponent();
      comp.tableModel = { ...makeTableModel(1, 1), selectedCells: [makeCell(2, 3)] };
      const style: any = {};

      comp.setSelectStyle(style, makeCell(2, 3), 2, 3);

      expect(style["background-color"]).toBe("#b2d8ff");
   });

   it("should highlight a merged cell whose baseInfo matches a selected cell's row/col", () => {
      const { comp } = createComponent();
      comp.tableModel = { ...makeTableModel(1, 1), selectedCells: [makeCell(2, 3)] };
      const mergedCell = makeCell(2, 4, { baseInfo: new Rectangle(2, 3, 2, 1) as any });
      const style: any = {};

      comp.setSelectStyle(style, mergedCell, 2, 4);

      expect(style["background-color"]).toBe("#b2d8ff");
   });

   it("should not set a background when no selected cell matches", () => {
      const { comp } = createComponent();
      comp.tableModel = { ...makeTableModel(1, 1), selectedCells: [makeCell(9, 9)] };
      const style: any = {};

      comp.setSelectStyle(style, makeCell(0, 0), 0, 0);

      expect(style["background-color"]).toBeUndefined();
   });
});

describe("CalcTableLayoutPane — setSpanCellStyle", () => {
   it("should mark the right/bottom border on the span-owning cell when it spans multiple columns and rows", () => {
      const { comp } = createComponent();
      const cell = makeCell(0, 0, { span: { width: 2, height: 2 } as any });
      const style: any = {};

      comp.setSpanCellStyle(style, cell, 0, 0);

      expect(style["border-right-color"]).toBe("#bbbec0");
      expect(style["border-bottom-color"]).toBe("#bbbec0");
   });

   it("should not mark span borders for a 1x1 span", () => {
      const { comp } = createComponent();
      const cell = makeCell(0, 0, { span: { width: 1, height: 1 } as any });
      const style: any = {};

      comp.setSpanCellStyle(style, cell, 0, 0);

      expect(style["border-right-color"]).toBeUndefined();
      expect(style["border-bottom-color"]).toBeUndefined();
   });

   it("should mark interior left/right borders for a covered cell in the middle columns of its span", () => {
      const { comp } = createComponent();
      // baseInfo: Rectangle(x=row=0, y=col=1, width=3 columns, height=1 row) — a span covering
      // columns 1..3 on row 0; column 2 is strictly between them on both sides.
      const cell = makeCell(0, 2, { baseInfo: new Rectangle(0, 1, 3, 1) as any });
      const style: any = {};

      comp.setSpanCellStyle(style, cell, 0, 2);

      expect(style["border-left-color"]).toBe("#bbbec0");
      expect(style["border-right-color"]).toBe("#bbbec0");
   });

   it("should not mark left border for the first column of the span", () => {
      const { comp } = createComponent();
      const cell = makeCell(0, 1, { baseInfo: new Rectangle(0, 1, 3, 1) as any });
      const style: any = {};

      comp.setSpanCellStyle(style, cell, 0, 1);

      expect(style["border-left-color"]).toBeUndefined();
   });
});

// ---------------------------------------------------------------------------
// Group E: clickCell (async, setTimeout-deferred)
// ---------------------------------------------------------------------------

describe("CalcTableLayoutPane — clickCell", () => {
   it("should return immediately on a right-click of an already-selected cell, without scheduling the timer", () => {
      const { comp } = createComponent();
      comp.tableModel = { ...makeTableModel(1, 1), selectedCells: [] };
      const cell = comp.tableModel.tableRows[0].tableCells[0];
      comp.tableModel.selectedCells.push(cell);
      vi.useFakeTimers();
      const updateSpy = vi.spyOn(comp, "updateFirstSelected");

      try {
         comp.clickCell({ button: 2 } as MouseEvent, cell);
         vi.runAllTimers();

         expect(updateSpy).not.toHaveBeenCalled();
      }
      finally {
         updateSpy.mockRestore();
      }
   });

   it("should replace the selection with a single cell on a plain click (no modifier keys)", () => {
      const { comp, aiAssistantService } = createComponent();
      comp.tableModel = { ...makeTableModel(2, 2), selectedCells: [makeCell(1, 1)] };
      const cell = comp.tableModel.tableRows[0].tableCells[0];
      vi.useFakeTimers();

      comp.clickCell({ button: 0, ctrlKey: false, shiftKey: false } as MouseEvent, cell);
      vi.runAllTimers();

      expect(comp.tableModel.selectedCells).toEqual([cell]);
      expect((comp.vsObjectModel as VSCalcTableModel).firstSelectedRow).toBe(0);
      expect((comp.vsObjectModel as VSCalcTableModel).firstSelectedColumn).toBe(0);
      expect(aiAssistantService.setCalcTableScriptContext).toHaveBeenCalledWith(comp.tableModel);
      expect(aiAssistantService.setCalcTableRetrievalScriptContext).toHaveBeenCalledWith(comp.tableModel);
   });

   it("should add to an empty selection on a ctrl/shift click without clearing it first", () => {
      const { comp } = createComponent();
      comp.tableModel = { ...makeTableModel(1, 1), selectedCells: [] };
      const cell = comp.tableModel.tableRows[0].tableCells[0];
      vi.useFakeTimers();

      comp.clickCell({ button: 0, ctrlKey: true, shiftKey: false } as MouseEvent, cell);
      vi.runAllTimers();

      expect(comp.tableModel.selectedCells).toEqual([cell]);
   });

   it("should shift-add to an existing non-empty selection on a ctrl/shift click", () => {
      const { comp } = createComponent();
      comp.tableModel = makeTableModel(2, 2);
      comp.tableModel.selectedCells = [comp.tableModel.tableRows[0].tableCells[0]];
      const newCell = comp.tableModel.tableRows[1].tableCells[1];
      vi.useFakeTimers();
      const shiftSpy = vi.spyOn(comp, "addShiftCells");

      try {
         comp.clickCell({ button: 0, ctrlKey: true, shiftKey: false } as MouseEvent, newCell);
         vi.runAllTimers();

         expect(shiftSpy).toHaveBeenCalledWith(newCell);
      }
      finally {
         shiftSpy.mockRestore();
      }
   });
});

// ---------------------------------------------------------------------------
// Group F: selectCell / detectChanges
// ---------------------------------------------------------------------------

describe("CalcTableLayoutPane — selectCell / detectChanges", () => {
   it("should build regions and a row->columns map, then load the cell binding and detect changes", () => {
      const { comp, editorService, changeRef } = createComponent();
      const cellA = makeCell(0, 0);
      const cellB = makeCell(0, 1);

      comp.selectCell([cellA, cellB]);

      expect(comp.vsObjectModel.selectedRegions).toEqual([cellA.cellPath, cellB.cellPath]);
      expect((comp.vsObjectModel as VSCalcTableModel).selectedData.get(0)).toEqual([0, 1]);
      expect(editorService.loadCellBinding).toHaveBeenCalled();
      expect(changeRef.detectChanges).toHaveBeenCalled();
   });

   it("detectChanges should delegate to the injected ChangeDetectorRef", () => {
      const { comp, changeRef } = createComponent();

      comp.detectChanges();

      expect(changeRef.detectChanges).toHaveBeenCalledTimes(1);
   });
});

// ---------------------------------------------------------------------------
// Group G: findBaseCell
// ---------------------------------------------------------------------------

describe("CalcTableLayoutPane — findBaseCell", () => {
   it("should return the cell itself when it has no baseInfo", () => {
      const { comp } = createComponent();
      const cell = makeCell(0, 0, { baseInfo: null });

      expect(comp.findBaseCell(cell)).toBe(cell);
   });

   it("should return null when baseInfo is set but there is no tableModel", () => {
      const { comp } = createComponent();
      const cell = makeCell(1, 1, { baseInfo: new Rectangle(0, 0, 1, 1) as any });

      expect(comp.findBaseCell(cell)).toBeNull();
   });

   it("should resolve the base cell via baseInfo.x/y as row/col indices into the tableModel", () => {
      const { comp } = createComponent();
      comp.tableModel = makeTableModel(2, 2);
      const baseCell = comp.tableModel.tableRows[0].tableCells[1];
      const mergedCell = makeCell(1, 1, { baseInfo: new Rectangle(0, 1, 1, 1) as any });

      expect(comp.findBaseCell(mergedCell)).toBe(baseCell);
   });
});

// ---------------------------------------------------------------------------
// Group H: addShiftCells / mergeDimension / validateDimension / addSelectCells / getCellRect
// ---------------------------------------------------------------------------

describe("CalcTableLayoutPane — getCellRect / getRectangle", () => {
   it("should return a 1x1 rect for a non-spanned cell", () => {
      const { comp } = createComponent();

      expect(comp.getCellRect(makeCell(2, 3))).toEqual(new Rectangle(3, 2, 1, 1));
   });

   it("should return a rect sized to the span for a spanned cell", () => {
      const { comp } = createComponent();

      expect(comp.getCellRect(makeCell(2, 3, { span: { width: 2, height: 3 } as any })))
         .toEqual(new Rectangle(3, 2, 2, 3));
   });

   it("getRectangle should look up the cell at (r,c) and delegate to getCellRect", () => {
      const { comp } = createComponent();
      comp.tableModel = makeTableModel(2, 2);

      expect(comp.getRectangle(1, 1)).toEqual(new Rectangle(1, 1, 1, 1));
   });
});

describe("CalcTableLayoutPane — mergeDimension", () => {
   it("should return the bounding box of two non-overlapping rectangles (asymmetric inputs)", () => {
      const { comp } = createComponent();

      // Chosen so neither rectangle's bounds equal the merged result — a formula error would
      // be masked if the inputs happened to make merge == one of the inputs.
      const result = comp.mergeDimension(new Rectangle(0, 0, 2, 1), new Rectangle(3, 2, 1, 4));

      expect(result).toEqual(new Rectangle(0, 0, 4, 6));
   });
});

describe("CalcTableLayoutPane — validateDimension", () => {
   it("should leave selectedRect unchanged when every scanned cell already fits inside it", () => {
      const { comp } = createComponent();
      comp.tableModel = makeTableModel(2, 2);
      comp.selectedRect = new Rectangle(0, 0, 2, 2);

      comp.validateDimension(new Rectangle(0, 0, 2, 2));

      expect(comp.selectedRect).toEqual(new Rectangle(0, 0, 2, 2));
   });

   // 🔁 Regression-sensitive: a scanned cell whose own span exceeds the current selectedRect
   // must grow selectedRect AND recurse to re-validate the (now larger) region, since growth
   // can pull in further cells that themselves need re-checking.
   it("should grow selectedRect and recurse when a scanned cell's span extends past the current bounds", () => {
      const { comp } = createComponent();
      const model = makeTableModel(2, 2);
      // Cell (row0, col1) spans 2 rows — its own rect (col1,row0,1,2) extends past a
      // selectedRect that only covers row 0.
      model.tableRows[0].tableCells[1] = makeCell(0, 1, { span: { width: 1, height: 2 } as any });
      comp.tableModel = model;
      comp.selectedRect = new Rectangle(0, 0, 2, 1);

      comp.validateDimension(new Rectangle(0, 0, 2, 1));

      expect(comp.selectedRect).toEqual(new Rectangle(0, 0, 2, 2));
   });
});

describe("CalcTableLayoutPane — addSelectCells", () => {
   it("should clear selectedCells and return early when selectedRect is null", () => {
      const { comp } = createComponent();
      comp.tableModel = { ...makeTableModel(1, 1), selectedCells: [makeCell(0, 0)], selectedRect: null as any };

      comp.addSelectCells();

      expect(comp.tableModel.selectedCells).toEqual([]);
   });

   it("should populate selectedCells with every non-merged cell inside selectedRect", () => {
      const { comp } = createComponent();
      comp.tableModel = makeTableModel(2, 2);
      comp.selectedRect = new Rectangle(0, 0, 2, 2);

      comp.addSelectCells();

      expect(comp.selectedCells).toHaveLength(4);
   });

   it("should skip merged (baseInfo != null) cells inside selectedRect", () => {
      const { comp } = createComponent();
      const model = makeTableModel(2, 2);
      model.tableRows[1].tableCells[1] = makeCell(1, 1, { baseInfo: new Rectangle(0, 0, 1, 1) as any });
      comp.tableModel = model;
      comp.selectedRect = new Rectangle(0, 0, 2, 2);

      comp.addSelectCells();

      expect(comp.selectedCells).toHaveLength(3);
   });
});

describe("CalcTableLayoutPane — addShiftCells", () => {
   it("should merge the clicked cell's rect with the existing selection and select every cell in range", () => {
      const { comp } = createComponent();
      comp.tableModel = makeTableModel(2, 2);
      comp.tableModel.selectedCells = [comp.tableModel.tableRows[0].tableCells[0]];

      comp.addShiftCells(comp.tableModel.tableRows[1].tableCells[1]);

      expect(comp.selectedRect).toEqual(new Rectangle(0, 0, 2, 2));
      expect(comp.selectedCells).toHaveLength(4);
   });
});

// ---------------------------------------------------------------------------
// Group I: command processors
// ---------------------------------------------------------------------------

// Bypass: all process*Command handlers are `protected` (called by the CommandProcessor base
// class dispatcher, not the public API); accessed via `as any` casts to unit-test each handler
// directly, except for the one E6 routing-proof test below which goes through the real
// commandsSubject dispatch path. loadingCount (private) is also read/seeded directly, since
// the public isLoading getter collapses any positive/negative count to the same boolean.
describe("CalcTableLayoutPane — command processors", () => {
   // E6: one commandsSubject dispatch test to prove routing, not handler logic.
   it("should route a GetPredefinedNamedGroupCommand from commandsSubject to its handler", () => {
      const { comp, editorService, clientService } = createComponent();

      clientService.commands.next(new ViewsheetCommandMessage(
         "Calc1", "GetPredefinedNamedGroupCommand",
         { namedGroups: ["G1", "G2"] } as GetPredefinedNamedGroupCommand as any));

      expect(editorService.namedGroups).toEqual(["G1", "G2"]);
   });

   it("processGetTableLayoutCommand should install the new layout, emit it, and build the span map", () => {
      const { comp, editorService, aiAssistantService } = createComponent();
      editorService.getTableLayout.mockReturnValue(null);
      const layout = makeTableModel(1, 2);
      layout.tableRows[0].tableCells[1] = makeCell(0, 1, { span: { width: 1, height: 2 } as any });
      const emitted: CalcTableLayout[] = [];
      comp.calcTableLayout.subscribe(l => emitted.push(l));

      (comp as any).processGetTableLayoutCommand({ layout, cellBindings: { a: 1 } } as GetTableLayoutCommand);

      expect(comp.tableModel).toBe(layout);
      expect(editorService.setTableLayout).toHaveBeenCalledWith(layout);
      expect(emitted).toEqual([layout]);
      expect(aiAssistantService.setCalcTableBindingContext).toHaveBeenCalledWith(layout);
      // createSpanMap: the spanned cell at (0,1) with height 2 registers a shadow entry at (1,1).
      expect(comp.getSpanCell(1, 1)).toBe(layout.tableRows[0].tableCells[1]);
   });

   it("processGetTableLayoutCommand should carry over the previous selection when an old layout exists", () => {
      const { comp, editorService } = createComponent();
      const oldLayout = makeTableModel(1, 1);
      const selectedCells = [oldLayout.tableRows[0].tableCells[0]];
      oldLayout.selectedCells = selectedCells;
      oldLayout.selectedRect = new Rectangle(0, 0, 1, 1);
      editorService.getTableLayout.mockReturnValue(oldLayout);
      const newLayout = makeTableModel(1, 1);

      (comp as any).processGetTableLayoutCommand({ layout: newLayout, cellBindings: {} } as GetTableLayoutCommand);

      expect(comp.tableModel.selectedRect).toBe(oldLayout.selectedRect);
      expect(editorService.loadCellBinding).toHaveBeenCalled();
   });

   it("processGetCellBindingCommand should reset the cell binding and map aggregates to their view names", () => {
      const { comp, editorService, aiAssistantService } = createComponent();
      const command = {
         binding: makeCellBindingInfo(), aggregates: [{ view: "Sum(Sales)" }, { view: "Count(*)" }],
      } as unknown as GetCellBindingCommand;

      (comp as any).processGetCellBindingCommand(command);

      expect(editorService.resetCellBinding).toHaveBeenCalledWith(command);
      expect(aiAssistantService.calcTableAggregates).toEqual(["Sum(Sales)", "Count(*)"]);
   });

   it("processGetCellScriptCommand should set the binding's script value and refresh the selected cell's text", () => {
      const { comp, editorService } = createComponent();
      comp.tableModel = makeTableModel(1, 1);
      comp.selectedRect = new Rectangle(0, 0, 1, 1);
      const binding = makeCellBindingInfo({ type: CellBindingInfo.BIND_FORMULA });
      editorService.getCellBinding.mockReturnValue(binding);

      (comp as any).processGetCellScriptCommand({ script: "sum(x)" } as GetCellScriptCommand);

      expect(binding.value).toBe("sum(x)");
      expect(comp.tableModel.tableRows[0].tableCells[0].text).toBe("=sum(x)");
      expect(editorService.setCellBinding).toHaveBeenCalled();
   });

   it("processGetPredefinedNamedGroupCommand should set the editor service's namedGroups", () => {
      const { comp, editorService } = createComponent();

      (comp as any).processGetPredefinedNamedGroupCommand({ namedGroups: ["A", "B"] } as GetPredefinedNamedGroupCommand);

      expect(editorService.namedGroups).toEqual(["A", "B"]);
   });

   it("processAssemblyLoadingCommand should increment loadingCount by the command's count and detect changes", () => {
      const { comp, changeRef } = createComponent();

      (comp as any).processAssemblyLoadingCommand({ count: 3 } as AssemblyLoadingCommand);

      // Bypass: loadingCount is private; read directly since isLoading (a `> 0` boolean) cannot
      // distinguish "incremented by 3" from "incremented by 1" — both would report true.
      expect((comp as any).loadingCount).toBe(3);
      expect(changeRef.detectChanges).toHaveBeenCalled();
   });

   // isLoading is deliberately NOT used to verify these two default-amount tests: it only
   // reports `loadingCount > 0`, so a default of 0 (bug) vs. 1 (correct), or an unclamped
   // negative value vs. a clamped 0, would both read as `isLoading === false` either way.
   it("processAssemblyLoadingCommand should default to incrementing by 1 when no command is given", () => {
      const { comp } = createComponent();

      (comp as any).processAssemblyLoadingCommand(null);

      expect((comp as any).loadingCount).toBe(1);
   });

   it("processClearAssemblyLoadingCommand should default to decrementing by 1 when no command is given", () => {
      const { comp } = createComponent();
      (comp as any).loadingCount = 2;

      (comp as any).processClearAssemblyLoadingCommand(null);

      expect((comp as any).loadingCount).toBe(1);
   });

   it("processClearAssemblyLoadingCommand should clamp loadingCount at 0 rather than going negative", () => {
      const { comp } = createComponent();

      (comp as any).processClearAssemblyLoadingCommand({ count: 5 } as ClearAssemblyLoadingCommand);

      expect((comp as any).loadingCount).toBe(0);
   });
});

describe("CalcTableLayoutPane — isLoading", () => {
   it("should be false when loadingCount is 0", () => {
      const { comp } = createComponent();

      expect(comp.isLoading).toBe(false);
   });

   it("should be true when loadingCount is greater than 0", () => {
      const { comp } = createComponent();

      (comp as any).processAssemblyLoadingCommand({ count: 1 } as AssemblyLoadingCommand);

      expect(comp.isLoading).toBe(true);
   });
});

// ---------------------------------------------------------------------------
// Group J: resize flow (resizeCell / onMouseMove / onMouseUp)
// ---------------------------------------------------------------------------

// Bypass: newWidth/newHeight/prevMouseLocationx/prevMouseLocationy/resizeRow/resizeCol are all
// private resize-in-progress state with no public accessor; read/seeded via `as any` casts to
// observe resizeCell's captured geometry and to isolate onMouseMove's two-part OR guard.

describe("CalcTableLayoutPane — resizeCell / onMouseMove / onMouseUp", () => {
   it("resizeCell should capture the starting geometry and register a document mouseup listener", () => {
      const { comp, document } = createComponent();
      comp.tableModel = makeTableModel(1, 1);
      comp.tableModel.tableColumns[0].width = 100;
      comp.tableModel.tableRows[0].height = 50;

      comp.resizeCell({ x: 10, y: 20, op: "ResizeColumn" }, 0, 0);

      expect((comp as any).newWidth).toBe(100);
      expect((comp as any).newHeight).toBe(50);
      expect(document.addEventListener).toHaveBeenCalledWith("mouseup", expect.any(Function));
   });

   it("onMouseMove should do nothing before any resize has started", () => {
      const { comp, renderer } = createComponent();

      comp.onMouseMove({ pageX: 100, pageY: 100 } as MouseEvent);

      expect(renderer.setStyle).not.toHaveBeenCalled();
   });

   it("onMouseMove should do nothing when resizeRow/resizeCol are still unset, even if a location was recorded", () => {
      const { comp, renderer } = createComponent();
      (comp as any).prevMouseLocationx = 5;
      (comp as any).prevMouseLocationy = 5;

      comp.onMouseMove({ pageX: 50, pageY: 50 } as MouseEvent);

      expect(renderer.setStyle).not.toHaveBeenCalled();
   });

   it("onMouseMove should do nothing when no location was recorded, even if resizeRow/resizeCol are set", () => {
      const { comp, renderer } = createComponent();
      (comp as any).resizeRow = 0;
      (comp as any).resizeCol = 0;

      comp.onMouseMove({ pageX: 50, pageY: 50 } as MouseEvent);

      expect(renderer.setStyle).not.toHaveBeenCalled();
   });

   it("onMouseMove should update the column resize handle position when resizing a column", () => {
      const { comp, renderer } = createComponent();
      comp.tableModel = makeTableModel(1, 1);
      comp.colResize = { nativeElement: {} } as any;
      comp.resizeCell({ x: 10, y: 10, op: "ResizeColumn" }, 0, 0);

      comp.onMouseMove({ pageX: 40, pageY: 10 } as MouseEvent);

      expect(renderer.setStyle).toHaveBeenCalledWith(comp.colResize.nativeElement, "left", "40px");
      expect(renderer.setStyle).toHaveBeenCalledWith(comp.colResize.nativeElement, "visibility", "visible");
   });

   it("onMouseMove should update the row resize handle position when resizing a row", () => {
      const { comp, renderer } = createComponent();
      comp.tableModel = makeTableModel(1, 1);
      comp.rowResize = { nativeElement: {} } as any;
      comp.resizeCell({ x: 10, y: 10, op: "ResizeRow" }, 0, 0);

      comp.onMouseMove({ pageX: 10, pageY: 60 } as MouseEvent);

      expect(renderer.setStyle).toHaveBeenCalledWith(comp.rowResize.nativeElement, "top", "60px");
      expect(renderer.setStyle).toHaveBeenCalledWith(comp.rowResize.nativeElement, "visibility", "visible");
   });

   it("onMouseUp should do nothing when no resize is in progress", () => {
      const { comp, clientService } = createComponent();

      comp.onMouseUp();

      expect(clientService.sendEvent).not.toHaveBeenCalled();
   });

   it("onMouseUp should send the resize event, hide the handles, reset state, and remove the listener", () => {
      const { comp, clientService, renderer, document } = createComponent();
      comp.tableModel = makeTableModel(1, 1);
      comp.colResize = { nativeElement: {} } as any;
      comp.rowResize = { nativeElement: {} } as any;
      comp.resizeCell({ x: 0, y: 0, op: "ResizeColumn" }, 0, 0);
      comp.onMouseMove({ pageX: 20, pageY: 0 } as MouseEvent);

      comp.onMouseUp();

      expect(clientService.sendEvent).toHaveBeenCalledWith(
         "/events/vs/calctable/tablelayout/resize",
         expect.objectContaining({ row: 0, col: 0, op: "ResizeColumn" }));
      expect(renderer.setStyle).toHaveBeenCalledWith(comp.colResize.nativeElement, "visibility", "hidden");
      expect(renderer.setStyle).toHaveBeenCalledWith(comp.rowResize.nativeElement, "visibility", "hidden");
      expect(document.removeEventListener).toHaveBeenCalledWith("mouseup", expect.any(Function));

      // State reset -> a second onMouseUp() call is a no-op.
      clientService.sendEvent.mockClear();
      comp.onMouseUp();
      expect(clientService.sendEvent).not.toHaveBeenCalled();
   });
});

// ---------------------------------------------------------------------------
// Group K: getCellContent / changeSelectCell / trackByIdx
// ---------------------------------------------------------------------------

describe("CalcTableLayoutPane — getCellContent", () => {
   it("should return null when no binding is given", () => {
      const { comp } = createComponent();

      expect(comp.getCellContent(null)).toBeNull();
   });

   it("should prefix a formula binding with '=' and default to empty when the value is falsy", () => {
      const { comp } = createComponent();

      expect(comp.getCellContent(makeCellBindingInfo({ type: CellBindingInfo.BIND_FORMULA, value: null }))).toBe("=");
      expect(comp.getCellContent(makeCellBindingInfo({ type: CellBindingInfo.BIND_FORMULA, value: "sum(x)" })))
         .toBe("=sum(x)");
   });

   it("should return the raw value for a column binding", () => {
      const { comp } = createComponent();

      expect(comp.getCellContent(makeCellBindingInfo({ type: CellBindingInfo.BIND_COLUMN, value: "Sales" })))
         .toBe("Sales");
   });

   // BIND_TEXT is used here (not CellBindingInfo.DETAIL) because DETAIL and BIND_COLUMN are
   // numerically equal (both 2) — a `type` of DETAIL would hit the BIND_COLUMN branch above
   // instead of reaching the bracket-decoration logic these tests target.
   it("should bracket a plain text binding with no group/summary/expansion decoration", () => {
      const { comp } = createComponent();

      expect(comp.getCellContent(makeCellBindingInfo({ type: CellBindingInfo.BIND_TEXT, value: "Company" })))
         .toBe("[Company]");
   });

   it("should prefix a group binding with the group symbol", () => {
      const { comp } = createComponent();

      expect(comp.getCellContent(makeCellBindingInfo({ type: CellBindingInfo.BIND_TEXT, btype: CellBindingInfo.GROUP, value: "Region" })))
         .toBe("[ΞRegion]");
   });

   it("should prefix a summary binding with the summary symbol", () => {
      const { comp } = createComponent();

      expect(comp.getCellContent(makeCellBindingInfo({ type: CellBindingInfo.BIND_TEXT, btype: CellBindingInfo.SUMMARY, value: "Total" })))
         .toBe("[∑Total]");
   });

   it("should prefix a horizontally-expanded binding with the right arrow", () => {
      const { comp } = createComponent();

      expect(comp.getCellContent(makeCellBindingInfo({
         type: CellBindingInfo.BIND_TEXT, expansion: CellBindingInfo.EXPAND_H, value: "Q",
      }))).toBe("→[Q]");
   });

   it("should prefix a vertically-expanded binding with the down arrow", () => {
      const { comp } = createComponent();

      expect(comp.getCellContent(makeCellBindingInfo({
         type: CellBindingInfo.BIND_TEXT, expansion: CellBindingInfo.EXPAND_V, value: "Y",
      }))).toBe("↓[Y]");
   });
});

describe("CalcTableLayoutPane — changeSelectCell", () => {
   it("should write the resolved cell content into the currently-selected cell", () => {
      const { comp } = createComponent();
      comp.tableModel = makeTableModel(2, 2);
      comp.selectedRect = new Rectangle(1, 0, 1, 1); // x=col=1, y=row=0

      comp.changeSelectCell(makeCellBindingInfo({ type: CellBindingInfo.BIND_COLUMN, value: "Profit" }));

      expect(comp.tableModel.tableRows[0].tableCells[1].text).toBe("Profit");
   });
});

describe("CalcTableLayoutPane — trackByIdx", () => {
   it("should return the index regardless of the item", () => {
      const { comp } = createComponent();

      expect(comp.trackByIdx(7, { anything: true })).toBe(7);
   });
});
