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
import { of as observableOf } from "rxjs";
import { SelectionListEditorModel } from "../model/selection-list-editor-model";
import { SelectionListEditor } from "./selection-list-editor.component";

describe("Selection List Editor Test", () => {
   const createModel: () => SelectionListEditorModel = () => {
      return {
         table: "table",
         column: "column",
         value: "value",
         dataType: "string",
         tables: ["table1"],
         localizedTables: [],
         form: false,
         ltableDescriptions: []
      };
   };

   let selectionListEditor: SelectionListEditor;
   let httpService: any;

   beforeEach(() => {
      httpService = { get: jest.fn() };

      selectionListEditor = new SelectionListEditor(httpService);
      selectionListEditor.model = createModel();
      selectionListEditor.runtimeId = "Viewsheet1";
   });

   // Bug #9884 make sure that columns get properly fetched.
   it("should get columns when switching table", () => {
      httpService.get.mockImplementation(() => observableOf({}));
      selectionListEditor.selectTable(0);
      expect(httpService.get).toHaveBeenCalled();
      expect(httpService.get.mock.calls[0][0]).toEqual("../api/vs/selectionList/columns/Viewsheet1/table1");
   });

   //Bug #19010 should load first column when select table node
   it("should load default first column when select table node", () => {
      selectionListEditor.model.column = "";
      selectionListEditor.model.value = "";

      httpService.get.mockImplementation(() => observableOf({
         columns: ["id", "state", "AA"],
         tooltip: ["", "", ""]
      }));
      selectionListEditor.selectTable(0);
      expect(selectionListEditor.localValue).toBe("id");
      expect(httpService.get).toHaveBeenCalled();
      expect(httpService.get.mock.calls[0][0]).toEqual("../api/vs/selectionList/columns/Viewsheet1/table1");
   });
});
