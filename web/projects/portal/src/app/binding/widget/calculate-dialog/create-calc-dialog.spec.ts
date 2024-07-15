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
import { ComponentTool } from "../../../common/util/component-tool";
import { FormulaEditorDialogModel } from "../../../widget/formula-editor/formula-editor-dialog-model";

import { CreateCalcDialog } from "./create-calc-dialog.component";


describe("create calc dialog unit case", () => {
   let createCalcDialog: CreateCalcDialog;
   let modalService: any;
   let editorService: any;

   let createFormulaEditorDialogModel: () => FormulaEditorDialogModel = () => {
      return {
         expression: null,
         formulaType: null,
         formulaName: null,
         dataType: null,
         oname: null
      };
   };

   beforeEach(() => {
      modalService = { open: jest.fn() };
      editorService = {
         getColumNTreeNode: jest.fn(),
         getFunctionTreeNode: jest.fn(),
         getOperationTreeNode: jest.fn()
      };
      createCalcDialog = new CreateCalcDialog(modalService);
   });

   //Bug #18332, Bug #18164, Bug #17473 should load right data type when detail or aggregate calc type
   it("should load right data type", () => {
      createCalcDialog.calcType = "detail";
      expect(createCalcDialog.dataType).toEqual("string");

      createCalcDialog.calcType = "aggregate";
      expect(createCalcDialog.dataType).toEqual("double");
   });

   //Bug #20229 check duplicate calc field names
   it("calc fields name check", () => {
      createCalcDialog.calcType = "aggregate";
      createCalcDialog.calcFieldsGroup = ["calc1"];
      createCalcDialog.name = "calc1";

      let showMessageDialog = jest.spyOn(ComponentTool, "showMessageDialog");
      showMessageDialog.mockImplementation(() => Promise.resolve("ok"));

      createCalcDialog.showCreateMeasureDialog();
      expect(showMessageDialog).toHaveBeenCalled();
      expect(showMessageDialog.mock.calls[0][1]).toBe("_#(js:Error)");
      expect(showMessageDialog.mock.calls[0][2]).toBe("_#(js:Duplicate Name)!");
   });
});
