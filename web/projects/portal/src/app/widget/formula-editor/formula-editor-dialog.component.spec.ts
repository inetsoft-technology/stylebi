/*
 * inetsoft-web - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
import { ComponentTool } from "../../common/util/component-tool";
import { FormulaEditorDialog } from "./formula-editor-dialog.component";
import { FeatureFlagsService } from "../../../../../shared/feature-flags/feature-flags.service";

let createMockItemObject: () => any = () => {
   return {
      expression: "",
      node: null,
      selection: {
         from: {
            ch: 0,
            line: 0,
            sticky: null
         },
         to: {
            ch: 0,
            line: 0,
            sticky: null
         }
      },
      target: "columnTree"
   };
};



describe("Formula Editor Test", () => {
   let formulaEditor: FormulaEditorDialog;
   let modalService: any;
   let renderer: any;
   let element: any;
   let featureFlag: FeatureFlagsService;

   beforeEach(() => {
      const editorService: any = {
         getColumnTreeNode: jest.fn(),
         getAdhocColumnTreeNode: jest.fn(),
         sqlFunctions: [],
         sqlOperators: [],
         getOperationTreeNode: jest.fn()
      };
      modalService = { open: jest.fn() };
      renderer = { };
      element = { };

      formulaEditor = new FormulaEditorDialog(editorService, modalService, renderer, element, featureFlag);
      formulaEditor.formulaName = "formula name";
      formulaEditor.dataType = "string";
   });

   //Bug #20195
   it("should warn about a sql expression in a non-sql-mergeable table", () => {
      let showMessageDialog = jest.spyOn(ComponentTool, "showMessageDialog");
      showMessageDialog.mockImplementation(() => Promise.resolve("ok"));
      formulaEditor.formulaType = "Script";
      formulaEditor.sqlMergeable = false;
      formulaEditor.initForm();

      formulaEditor.form.get("formulaType").patchValue("SQL");
      expect(showMessageDialog).toHaveBeenCalled();

      //Bug #20195
      expect(showMessageDialog.mock.calls[0][2]).toEqual("_#(js:common.formulaDataUnmergeable)");
   });

   //bug #18750 and Bug #18931, Bug #20195 should show warning when SQL is selected for aggregate calculated field
   xit("should show warning when SQL is selected for aggregate calculated field", () => { // broken test
      let showMessageDialog = jest.spyOn(ComponentTool, "showMessageDialog");
      showMessageDialog.mockImplementation(() => Promise.resolve("ok"));
      formulaEditor.formulaType = "Script";
      formulaEditor.calcType = "aggregate";
      formulaEditor.initForm();

      formulaEditor.form.get("formulaType").patchValue("SQL");
      expect(showMessageDialog).toHaveBeenCalled();

      //Bug #20195
      expect(showMessageDialog.mock.calls[0][2]).toEqual("_#(js:common.calcfieldAggrSqlUnsupport)");
   });

   //bug #18671 should allow underscore for formula name
   //bug #18901 should control special character
   //Bug #20238
   it("should check characters in formula name", () => {
      formulaEditor.formulaName = "test_name";
      formulaEditor.expression = "a";
      formulaEditor.initForm();
      let formulaValid = formulaEditor.form.status;
      expect(formulaValid).toBe("VALID");

      formulaEditor.formulaName = "a@￥……（——）：“《？ 》";
      formulaEditor.initForm();
      formulaValid = formulaEditor.form.status;
      expect(formulaValid).toBe("INVALID");

      //Bug #20238
      formulaEditor.formulaName = "中13a#_";
      formulaEditor.initForm();
      formulaValid = formulaEditor.form.status;
      expect(formulaValid).toBe("VALID");

      //Bug #20238
      formulaEditor.formulaName = "#_中2e";
      formulaEditor.initForm();
      formulaValid = formulaEditor.form.status;
      expect(formulaValid).toBe("VALID");
   });

   //bug #18749 should pop up warning for name already in use
   it("should pop up warning for name already in use", () => {
      formulaEditor.formulaName = "CalcField1";
      formulaEditor.columnTreeRoot = {
         label: "Fields",
         children: [
            {
               label: "State",
               leaf: true
            },
            {
               label: "Total",
               leaf: true
            }
         ],
         leaf: false
      };
      formulaEditor.initForm();
      formulaEditor.form.get("formulaName").patchValue("State");
      formulaEditor.expression = "a";

      const showMessageDialog = jest.spyOn(ComponentTool, "showMessageDialog");
      showMessageDialog.mockImplementation(() => Promise.resolve("ok"));
      formulaEditor.ok();
      expect(showMessageDialog).toHaveBeenCalled();
   });

   //Bug #20047, Bug #20050, Bug #18631, Bug #20167, Bug #21460 should show right context
   it("should show right context when select items from tree node", () => {
      let obj = createMockItemObject();
      obj.node = {
         children: [],
            data: {
               data: "_ROLES_",
               name: "param",
               parentData: "paramter",
               parentLabel: "Parameter",
               parentName: "parameter",
            },
            expanded: true,
            label: "_ROLES_",
            leaf: true
      };
      formulaEditor.formulaName = "CalcField1";
      formulaEditor.formulaType = "Script";
      formulaEditor.initForm();
      formulaEditor.expressionChange(obj);

      expect(formulaEditor.expression).toBe("paramter._ROLES_");

      //Bug #18631
      formulaEditor.expression = null;
      let obj2 = createMockItemObject();
      obj2.node = {
         children: [],
            data: {
               data: "addConfidenceIntervalTarget",
               name: "compProp",
               parentData: "Chart1",
               parentLabel: "Chart1",
               parentName: "component",
               suffix: "()"
            },
            expanded: true,
            label: "addConfidenceIntervalTarget",
            leaf: true
      };
      formulaEditor.expressionChange(obj2);
      expect(formulaEditor.expression).toBe("Chart1.addConfidenceIntervalTarget()");

      //Bug #21460
      formulaEditor.expression = null;
      formulaEditor.formulaType = "Script";
      let obj4 = createMockItemObject();
      obj4.target = null;
      obj4.node = {
         children: [],
         data: {
            data: "[]",
            name: "Operator4|4",
            parentData: null,
            parentName: "Operator4",
         },
         label: "[] (Array)",
         leaf: true
      };
      formulaEditor.expressionChange(obj4);
      expect(formulaEditor.expression).toBe("[]");

      //Bug #18933 and Bug #20167
      formulaEditor.expression = null;
      formulaEditor.calcType = "field";
      formulaEditor.isCalc = true;
      let obj3 = createMockItemObject();
      obj3.node = {
         children: [],
            data: {
               data: "city",
               ifField: "true",
               name: "folder_0_field_0",
               parentdata: null,
               parentLabel: "Fields",
               parentName: "field",
            },
            label: "city",
            leaf: true
      };
      formulaEditor.expressionChange(obj3);
      expect(formulaEditor.expression).toBe("field['city']");
   });
});
