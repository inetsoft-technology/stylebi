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
import { TableAssemblyFactory } from "./table-assembly-factory";
import { WSTableAssembly } from "./ws-table-assembly";
import { WSTableAssemblyInfo } from "./ws-table-assembly-info";

describe("WS Table Tests", () => {
   it("should correctly show the expression button", () => {
      const info: WSTableAssemblyInfo = {
         live: true,
         runtime: true,
         aggregate: false,
         hasAggregate: false,
         hasCondition: false,
         sqlMergeable: true,
         visibleTable: true
      } as WSTableAssemblyInfo;
      const tableAssembly: WSTableAssembly = {
         colInfos: [],
         info: info,
         tableClassType: "BoundTableAssembly"
      } as WSTableAssembly;
      let table = TableAssemblyFactory.getTable(tableAssembly);

      expect(table.tableButtons.indexOf("expression")).not.toBe(-1);

      info.runtime = false;
      info.aggregate = true;
      table = TableAssemblyFactory.getTable(tableAssembly);
      expect(table.tableButtons.indexOf("expression")).toBe(-1);

      info.runtime = false;
      info.aggregate = false;
      table = TableAssemblyFactory.getTable(tableAssembly);
      expect(table.tableButtons.indexOf("expression")).not.toBe(-1);
   });

   it("should have the correct table mode", () => {
      const info: WSTableAssemblyInfo = {
         live: true,
         runtime: true,
         aggregate: false,
         hasAggregate: true,
         hasCondition: false,
         sqlMergeable: true,
         visibleTable: true,
         runtimeSelected: true
      } as WSTableAssemblyInfo;

      const tableAssembly: WSTableAssembly = {
         colInfos: [],
         info: info,
         tableClassType: "BoundTableAssembly"
      } as WSTableAssembly;

      const table = TableAssemblyFactory.getTable(tableAssembly);
      expect(table.mode).toBe("live");
   });
});
