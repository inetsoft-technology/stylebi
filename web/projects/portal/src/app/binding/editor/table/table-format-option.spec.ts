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
import { TableFormatOption } from "./table-format-option.component";
import { TableFormatInfo } from "../../../common/data/tablelayout/table-format-info";
import { TestUtils } from "../../../common/test/test-utils";

describe("Table Format Option Unit Test", () => {
   let createTableFormatInfo: () => TableFormatInfo = () => {
      let tableFormat: TableFormatInfo = <TableFormatInfo> Object.assign({
         suppressIfZero: true,
         suppressIfDuplicate: true,
         lineWrap: true
      }, TestUtils.createMockFromatInfo());

      tableFormat.type = "inetsoft.web.binding.model.table.TableFormatInfo";
      return tableFormat;
   };

   let tableFormatOpt: TableFormatOption;

   beforeEach(() => {
      tableFormatOpt = new TableFormatOption();
   });

   //Bug #20380
   it("table format option should be applied", (done) => {
      let formatModel = createTableFormatInfo();
      tableFormatOpt.format = formatModel;

      tableFormatOpt.onChangeFormat.subscribe((model) => {
         expect(model).toBe(formatModel);

         done();
      });
      tableFormatOpt.updateFormatOption();
   });
});
