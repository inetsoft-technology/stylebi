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
import { of } from "rxjs";
import { Rectangle } from "../../../common/data/rectangle";
import { CalcTableCell } from "../../../common/data/tablelayout/calc-table-cell";
import { TestUtils } from "../../../common/test/test-utils";
import { GetCellBindingCommand } from "../../command/get-cell-binding-command";
import { CellBindingInfo } from "../../data/table/cell-binding-info";
import { CalcDataPane } from "../../editor/table/calc-data-pane.component";
import { CalcOptionPane } from "../../editor/table/calc-option-pane.component";
import { VSCalcTableEditorService } from "./vs-calc-table-editor.service";

/**
 * Bug #78560: an edit in the Freehand Table editor's Data/Option pane made before the
 * selected cell's binding has loaded must not send the previously loaded binding for the
 * selected cell.
 */
describe("VSCalcTableEditorService cell binding state (Bug #78560)", () => {
   let sent: { url: string, evt: any }[];
   let service: VSCalcTableEditorService;
   let pane: CalcDataPane;
   let optionPane: CalcOptionPane;

   function cell(row: number, col: number): CalcTableCell {
      const c = TestUtils.createMockCalcTableCell();
      c.row = row;
      c.col = col;
      return c;
   }

   function binding(type: number, value: string, expansion = CellBindingInfo.EXPAND_NONE) {
      const b = new CellBindingInfo();
      b.type = type;
      b.value = value;
      b.expansion = expansion;
      return b;
   }

   // the answer of the server to getcellbinding/setcellbinding/changeColumnValue/...
   function answer(row: number, col: number, b: CellBindingInfo): void {
      service.resetCellBinding(<GetCellBindingCommand> <any> {
         binding: b, cellRow: row, cellCol: col, cellNames: [], aggregates: []
      });
   }

   // what clickCell() does 200 ms after the mousedown
   function select(...cells: CalcTableCell[]): void {
      const last = cells[cells.length - 1];
      service.setSelectCells(cells);
      service.setSelectRect(new Rectangle(last.col, last.row, 1, 1));
      service.loadCellBinding();
   }

   const setEvents = () => sent.filter(s => s.url.endsWith("/setcellbinding"));
   const cellsOf = (evt: any) => evt.selectCells.map((c: CalcTableCell) => [c.row, c.col]);

   beforeEach(() => {
      sent = [];
      const bindingService: any = {
         assemblyName: "FreehandTable1",
         getURLParams: () => ({ set: function() { return this; } }),
         isGrayedOutField: () => false,
      };
      const clientService: any = {
         sendEvent: (url: string, evt: any) => sent.push({ url, evt })
      };
      // no trap, so setCellBinding sends synchronously
      const modelService: any = { putModel: () => of({ body: null }) };
      service = new VSCalcTableEditorService(bindingService, clientService, <any> {},
                                             modelService);
      service.setTableLayout(<any> { selectedCells: [], tableRows: [] });
      pane = new CalcDataPane(service, <any> {}, bindingService,
                              <any> { detectChanges: () => {} });
      optionPane = new CalcOptionPane(service);
   });

   it("should not send a blank binding on the first selection before it loads", () => {
      select(cell(0, 0));

      expect(service.isCellBindingReady()).toBe(false);
      expect(pane.cellBindingReady).toBe(false);
      pane.setExpansionValue({ target: { checked: true } });

      expect(setEvents().length).toBe(0);
   });

   it("should not send the previous cell's binding before the new cell's binding loads", () => {
      select(cell(0, 0));
      answer(0, 0, binding(CellBindingInfo.BIND_TEXT, "Customer:Region"));
      expect(service.isCellBindingReady()).toBe(true);

      select(cell(3, 0));
      expect(sent[sent.length - 1].url).toContain("/getcellbinding");
      expect(service.isCellBindingReady()).toBe(false);
      expect(pane.cellBindingReady).toBe(false);
      expect(optionPane.cellBindingReady).toBe(false);

      pane.setExpansionValue({ target: { checked: true } });
      pane.setExpansion(CellBindingInfo.EXPAND_H);
      pane.setCellName("myCell");
      pane.changeCellType(CellBindingInfo.BIND_TEXT);
      optionPane.setCellBinding(null);

      expect(setEvents().length).toBe(0);
   });

   it("should send the selected cell's binding once it has loaded", () => {
      select(cell(0, 0));
      answer(0, 0, binding(CellBindingInfo.BIND_TEXT, "Customer:Region"));
      select(cell(3, 0));
      answer(3, 0, binding(CellBindingInfo.BIND_FORMULA, "toList(data['Product:Category'])"));

      expect(pane.cellBindingReady).toBe(true);
      pane.setExpansionValue({ target: { checked: true } });

      const evs = setEvents();
      expect(evs.length).toBe(1);
      expect(cellsOf(evs[0].evt)).toEqual([[3, 0]]);
      expect(evs[0].evt.binding.type).toBe(CellBindingInfo.BIND_FORMULA);
      expect(evs[0].evt.binding.value).toBe("toList(data['Product:Category'])");
      expect(evs[0].evt.binding.expansion).toBe(CellBindingInfo.DETAIL);
   });

   it("should send rapid consecutive edits on the same cell", () => {
      select(cell(3, 0));
      answer(3, 0, binding(CellBindingInfo.BIND_FORMULA, "f()"));

      // Expand, then Horizontal before the answer to the first setcellbinding arrives
      pane.setExpansionValue({ target: { checked: true } });
      pane.setExpansion(CellBindingInfo.EXPAND_H);

      // answer to the first setcellbinding: GetCellBindingCommand + GetTableLayoutCommand,
      // whose replaceObject() reloads the binding of the same cell
      answer(3, 0, binding(CellBindingInfo.BIND_FORMULA, "f()", CellBindingInfo.DETAIL));
      service.loadCellBinding();
      expect(service.isCellBindingReady()).toBe(true);

      // another edit within the round trip of the reload
      pane.setExpansion(CellBindingInfo.EXPAND_V);

      const evs = setEvents();
      expect(evs.length).toBe(3);
      evs.forEach(e => {
         expect(cellsOf(e.evt)).toEqual([[3, 0]]);
         expect(e.evt.binding.type).toBe(CellBindingInfo.BIND_FORMULA);
         expect(e.evt.binding.value).toBe("f()");
      });
      expect(evs[0].evt.binding.expansion).toBe(CellBindingInfo.DETAIL);
      expect(evs[1].evt.binding.expansion).toBe(CellBindingInfo.EXPAND_H);
      expect(evs[2].evt.binding.expansion).toBe(CellBindingInfo.EXPAND_V);
   });

   it("should ignore a late answer for a selected cell that is not the last one", () => {
      select(cell(0, 0));
      answer(0, 0, binding(CellBindingInfo.BIND_TEXT, "Customer:Region"));
      // shift-extend before (0,0)'s reload answer arrives
      select(cell(0, 0), cell(1, 0));
      answer(0, 0, binding(CellBindingInfo.BIND_TEXT, "Customer:Region"));

      expect(service.isCellBindingReady()).toBe(false);
      pane.setExpansionValue({ target: { checked: true } });
      expect(setEvents().length).toBe(0);

      const last = binding(CellBindingInfo.BIND_FORMULA, "f()");
      answer(1, 0, last);
      expect(service.isCellBindingReady()).toBe(true);
      expect(service.getCellBinding()).toBe(last);
   });

   it("should not send an edit made after a column pick until the cell reloads", () => {
      select(cell(1, 0));
      answer(1, 0, binding(CellBindingInfo.BIND_TEXT, "old text"));

      pane.columnValue = "Customer:Region";
      expect(sent[sent.length - 1].url).toContain("/changeColumnValue");
      expect(service.isCellBindingReady()).toBe(false);

      pane.setExpansionValue({ target: { checked: true } });
      expect(setEvents().length).toBe(0);

      answer(1, 0, binding(CellBindingInfo.BIND_COLUMN, "Customer:Region"));
      pane.setExpansionValue({ target: { checked: true } });

      const evs = setEvents();
      expect(evs.length).toBe(1);
      expect(evs[0].evt.binding.type).toBe(CellBindingInfo.BIND_COLUMN);
      expect(evs[0].evt.binding.value).toBe("Customer:Region");
   });

   it("should ignore a cell script requested for another cell or an outdated binding", () => {
      select(cell(0, 0));
      answer(0, 0, binding(CellBindingInfo.BIND_COLUMN, "Customer:Region"));
      service.loadCellScript();
      expect(service.isCellScriptCurrent()).toBe(true);

      // the selection moved before the script arrived
      select(cell(1, 0));
      answer(1, 0, binding(CellBindingInfo.BIND_TEXT, "t"));
      expect(service.isCellScriptCurrent()).toBe(false);

      // the cell's binding was changed on the server before the script arrived
      service.loadCellScript();
      service.invalidateCellBinding();
      answer(1, 0, binding(CellBindingInfo.BIND_TEXT, "t"));
      expect(service.isCellScriptCurrent()).toBe(false);
   });
});
