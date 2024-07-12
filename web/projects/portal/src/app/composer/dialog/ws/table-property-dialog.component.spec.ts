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
import { ColumnRef } from "../../../binding/data/column-ref";
import { AttributeRef } from "../../../common/data/attribute-ref";
import { DataRef } from "../../../common/data/data-ref";
import { BoundTableAssembly } from "../../data/ws/bound-table-assembly";
import { ColumnInfo } from "../../data/ws/column-info";
import { EmbeddedTableAssembly } from "../../data/ws/embedded-table-assembly";
import { TablePropertyDialogModel } from "../../data/ws/table-property-dialog-model";
import { TabularTableAssembly } from "../../data/ws/tabular-table-assembly";
import { WSTableAssembly } from "../../data/ws/ws-table-assembly";
import { WSTableAssemblyInfo } from "../../data/ws/ws-table-assembly-info";
import { TablePropertyDialog } from "./table-property-dialog.component";

describe("Table Property Dialog Tests", () => {
   let tablePropertyDialog: TablePropertyDialog;
   const model: TablePropertyDialogModel = {
         newName: null,
         oldName: "table",
         description: "",
         visibleInViewsheet: true,
         maxRows: 1,
         returnDistinctValues: false,
         mergeSql: false,
         sourceMergeable: true,
         rowCount: 0
      };

   const ref: ColumnRef = {
      dataRefModel: {
         classType: "AttributeRef"
      } as DataRef
   } as ColumnRef;

   const column: ColumnInfo = {
      ref: ref,
      name: "aggregate",
      alias: "agg",
      visible: true,
      aggregate: true,
      group: false,
      crosstab: false
   } as ColumnInfo;

   const table: WSTableAssembly = {
      colInfos: [column],
      info: {
         aggregate: true,
         hasAggregate: true,
         privateSelection: [ref]
      } as WSTableAssemblyInfo
   } as WSTableAssembly;

   beforeEach(() => {
      const worksheet: any = { assemblyNames: jest.fn() };
      tablePropertyDialog = new TablePropertyDialog();
      tablePropertyDialog.worksheet = worksheet;
   });

   it("should allow underscores in the table name", () => {
      tablePropertyDialog.model = model;
      tablePropertyDialog.ngOnInit();
      tablePropertyDialog.form.get("newName").patchValue("_table");
      expect(tablePropertyDialog.form.get("newName").errors).toBeFalsy();
   });

   //Bug #16989 should disable sql merge for embedded table and tabular table
   it("should disable sql merge for embedded table and tabular table", () => {
      tablePropertyDialog.model = model;
      tablePropertyDialog.table = new BoundTableAssembly(table);
      tablePropertyDialog.ngOnInit();
      expect(tablePropertyDialog.sqlMergePossible).toBe(true);

      tablePropertyDialog.table = new EmbeddedTableAssembly(table);
      tablePropertyDialog.ngOnInit();
      expect(tablePropertyDialog.sqlMergePossible).toBe(false);

      tablePropertyDialog.table = new TabularTableAssembly(table);
      tablePropertyDialog.ngOnInit();
      expect(tablePropertyDialog.sqlMergePossible).toBe(false);
   });

   //Bug #20069
   it("change name for embedded table", () => {
      tablePropertyDialog.model = model;
      tablePropertyDialog.table = new EmbeddedTableAssembly(table);
      tablePropertyDialog.ngOnInit();
      tablePropertyDialog.form.get("newName").patchValue("aaa");
      expect(tablePropertyDialog.form.get("newName").errors).toBeFalsy();
      expect(tablePropertyDialog.form.get("mergeSql").errors).toBeFalsy();
   });
});