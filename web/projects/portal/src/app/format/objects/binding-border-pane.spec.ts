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
import { FormatInfoModel } from "../../common/data/format-info-model";
import { BindingBorderPane } from "./binding-border-pane.component";

describe("Binding Border Pane Test", () => {
   const createModel: () => FormatInfoModel = () => {
      return {
         type: "",
         color: "",
         backgroundColor: "",
         font: null,
         align: null,
         format: "",
         borderColor: "",
         borderTopStyle: "0",
         borderTopColor: "0",
         borderTopWidth: "0",
         borderLeftStyle: "0",
         borderLeftColor: "0",
         borderLeftWidth: "0",
         borderBottomStyle: "0",
         borderBottomColor: "0",
         borderBottomWidth: "0",
         borderRightStyle: "0",
         borderRightColor: "0",
         borderRightWidth: "0"
      };
   };

   let bindingBorderPane: BindingBorderPane;

   beforeEach(() => {
      bindingBorderPane = new BindingBorderPane();
      bindingBorderPane.formatModel = createModel();
   });

   // Bug #10699 and Bug #17594 make sure when setting border style and color to default
   it("should set borders to null when default borders selected", () => {
      jest.spyOn(bindingBorderPane, "drawBorders").mockImplementation(() => {});
      bindingBorderPane.setDefault();
      expect(bindingBorderPane.formatModel.borderBottomStyle).toBeNull();
      expect(bindingBorderPane.formatModel.borderTopStyle).toBeNull();
      expect(bindingBorderPane.formatModel.borderRightStyle).toBeNull();
      expect(bindingBorderPane.formatModel.borderLeftStyle).toBeNull();

      expect(bindingBorderPane.formatModel.borderBottomColor).toBe(bindingBorderPane.defaultColor);
      expect(bindingBorderPane.formatModel.borderLeftColor).toBe(bindingBorderPane.defaultColor);
      expect(bindingBorderPane.formatModel.borderRightColor).toBe(bindingBorderPane.defaultColor);
      expect(bindingBorderPane.formatModel.borderTopColor).toBe(bindingBorderPane.defaultColor);
   });
});
