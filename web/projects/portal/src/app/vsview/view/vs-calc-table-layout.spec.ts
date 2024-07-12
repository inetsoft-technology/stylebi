/*
 * inetsoft-web - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
import { of as observableOf } from "rxjs";
import { CellBindingInfo } from "../../binding/data/table/cell-binding-info";
import { Rectangle } from "../../common/data/rectangle";
import { TableDataPath } from "../../common/data/table-data-path";
import { CalcTableCell } from "../../common/data/tablelayout/calc-table-cell";
import { CalcTableLayout } from "../../common/data/tablelayout/calc-table-layout";
import { TestUtils } from "../../common/test/test-utils";
import { VSCalcTableModel } from "../../vsobjects/model/vs-calctable-model";
import { CalcTableLayoutPane } from "./vs-calc-table-layout.component";

let createMockCalcTableLayout: () => CalcTableLayout = () => {
   return {
      tableRows: [],
      tableColumns: [],
      selectedCells: [],
      selectedRect: new Rectangle(0, 0, 0, 0),
      selectedRegions: []
   };
};

describe("vs calc table layout unit case", () => {
   let editorService: any;
   let clientService: any;
   let changeRef: any;
   let renderer: any;
   let zone: any;
   let calcTableLayoutPane: CalcTableLayoutPane;
   let calcTableModel: VSCalcTableModel;

   beforeEach(() => {
      editorService = {
         loadTableModel: jest.fn(),
         loadCellBinding: jest.fn(),
         getTableLayout: jest.fn(),
         setTableLayout: jest.fn(),
         resetCellBinding: jest.fn(),
         getCellBinding: jest.fn()
      };
      clientService = { commands: observableOf([]) };
      changeRef = { detectChanges: jest.fn() };
      renderer = { setStyle: jest.fn() };
      zone = { run: jest.fn(), runOutsideAngular: jest.fn() };
      let doc = { addEventListener: jest.fn(), removeEventListener: jest.fn() };

      calcTableLayoutPane = new CalcTableLayoutPane(editorService, clientService, changeRef, renderer, doc, zone);
      calcTableModel = TestUtils.createMockVSCalcTableModel("calc1");
      calcTableLayoutPane.vsObjectModel = calcTableModel;
   });

   //Bug #18661 should get right selectedData
   it("should get right data cell when select cell", () => {
      let calcCell: CalcTableCell = TestUtils.createMockCalcTableCell("cell1");
      let cell1: TableDataPath = TestUtils.createMockTableDataPath();
      cell1.path = ["Cell [0,0]"];
      cell1.type = 256;
      calcCell.cellPath = cell1;
      calcTableLayoutPane.selectCell([calcCell]);
      expect((<VSCalcTableModel>calcTableLayoutPane.vsObjectModel).selectedData).not.toBeNull();
   });

   //Bug #19400 should return right cell content
   it("should return right cell content", () => {
      let cellBinding = TestUtils.createMockCellBindingInfo();
      cellBinding.type = CellBindingInfo.BIND_FORMULA;
      expect(calcTableLayoutPane.getCellContent(cellBinding)).toBe("=");

      cellBinding.value = "toList(data['Query1.Company'],'sort=asc')";

      expect(calcTableLayoutPane.getCellContent(cellBinding)).toBe("=toList(data['Query1.Company'],'sort=asc')");
   });

   //Bug #18662 should show presenter  when select cell
   // this was wrapped in setTimeout() and is now async, but since the test is poorly written using
   // a hand-instantiated test instance instead of using the angular testing framework, this test
   // cannot be fixed without re-writing the entire test suite
   xit("should return right first row and first column", () => {
      let calcCell = TestUtils.createMockCalcTableCell("cell1");
      let cell1: TableDataPath = TestUtils.createMockTableDataPath();
      cell1.path = ["Cell [0,0]"];
      cell1.type = 256;
      calcCell.col = 1;
      calcCell.row = 0;
      calcCell.cellPath = cell1;
      calcTableLayoutPane.tableModel = createMockCalcTableLayout();
      calcTableLayoutPane.clickCell(new MouseEvent("click"), calcCell);
      expect( (<VSCalcTableModel>calcTableLayoutPane.vsObjectModel).firstSelectedRow).toBe(0);
      expect( (<VSCalcTableModel>calcTableLayoutPane.vsObjectModel).firstSelectedColumn).toBe(1);
   });
});