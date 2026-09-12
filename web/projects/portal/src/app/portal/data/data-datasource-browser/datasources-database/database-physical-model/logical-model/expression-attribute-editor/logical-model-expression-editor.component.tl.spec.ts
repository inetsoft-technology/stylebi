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
 * LogicalModelExpressionEditorComponent - single pass (+memory leak)
 *
 * Risk-first coverage:
 *   Group 1 [Risk 2] - ngOnInit loads field tree and function/operator menus
 *   Group 2 [Risk 3] - apply persists valid form values and SQL validation result
 *   Group 3 [Risk 2] - updateExpression leaf/non-leaf branches and cursor updates
 *   Group 4 [Risk 1] - attribute setter toggles editable for base elements
 */

import { HttpClient } from "@angular/common/http";
import { HttpClientTestingModule, HttpTestingController } from "@angular/common/http/testing";
import { TestBed } from "@angular/core/testing";
import { UntypedFormControl } from "@angular/forms";

import { ScriptPane } from "../../../../../../../widget/dialog/script-pane/script-pane.component";
import { AttributeModel } from "../../../../../model/datasources/database/physical-model/logical-model/attribute-model";
import { LogicalModelExpressionEditor } from "./logical-model-expression-editor.component";

const CHECK_EXPRESSION_URI = "../api/data/logicalModel/attribute/expression";
const FIELDS_URI = "../api/data/logicalModel/tables/nodes";

function makeAttribute(overrides: Partial<AttributeModel> = {}): AttributeModel {
   return {
      name: "expr_1",
      type: "expression",
      baseElement: false,
      elementType: "expression",
      oldName: "expr_1",
      visible: true,
      table: "Orders",
      dataType: "double",
      qualifiedName: "Orders.expr_1",
      expression: "price",
      refType: "Measure" as any,
      ...overrides,
   };
}

describe("LogicalModelExpressionEditorComponent - single pass", () => {
   let http: HttpTestingController;

   beforeEach(() => {
      TestBed.configureTestingModule({
         imports: [HttpClientTestingModule],
      });
      http = TestBed.inject(HttpTestingController);
   });

   afterEach(() => {
      http.verify();
      vi.restoreAllMocks();
      TestBed.resetTestingModule();
   });

   function createEditor() {
      return new LogicalModelExpressionEditor(TestBed.inject(HttpClient));
   }

   describe("Group 1 - ngOnInit", () => {
      it("should load the fields tree and initialize operator/function roots", () => {
         const comp = createEditor();
         comp.databaseName = "Orders";
         comp.physicalModelName = "Model";
         comp.additional = "folder";

         comp.ngOnInit();

         const req = http.expectOne(FIELDS_URI);
         expect(req.request.method).toBe("POST");
         req.flush({ label: "server-root", children: [] });

         expect(comp.columnTreeRoot.label).toBe("_#(js:Fields)");
         expect(comp.columnTreeRoot.expanded).toBe(false);
         expect(comp.functionTreeRoot.children[0].children.map(child => child.data)).toEqual([
            "SUM()",
            "COUNT()",
            "AVG()",
            "MIN()",
            "MAX()",
         ]);
         expect(comp.operatorTreeRoot.children[0].children.map(child => child.data)).toEqual([
            "+",
            "-",
            "*",
            "/",
         ]);
      });
   });

   describe("Group 2 - apply", () => {
      it("should copy valid form values onto the attribute and clear invalidSQL on empty response", () => {
         const comp = createEditor();
         comp.attribute = makeAttribute();
         comp.form.addControl("name", new UntypedFormControl("margin"));
         comp.form.addControl("refType", new UntypedFormControl("Attribute"));

         comp.apply();

         const req = http.expectOne(CHECK_EXPRESSION_URI);
         expect(req.request.method).toBe("POST");
         expect(req.request.body).toEqual({ body: "price" });
         req.flush("", { status: 200, statusText: "OK" });

         expect(comp.attribute.name).toBe("margin");
         expect(comp.attribute.refType).toBe("Attribute");
         expect(comp.invalidSQL).toBe(false);
      });

      it("should mark invalidSQL when the validation request errors", () => {
         const comp = createEditor();
         comp.attribute = makeAttribute({ expression: "bad sql" });
         comp.form.addControl("name", new UntypedFormControl("expr_1"));
         comp.form.addControl("refType", new UntypedFormControl("Measure"));

         comp.apply();

         const req = http.expectOne(CHECK_EXPRESSION_URI);
         req.flush("error", { status: 500, statusText: "Server Error" });

         expect(comp.invalidSQL).toBe(true);
      });
   });

   describe("Group 3 - updateExpression", () => {
      it("should insert a selected column reference and update the cursor", () => {
         const comp = createEditor();
         comp.attribute = makeAttribute({ expression: "" });
         const insertSpy = vi.spyOn(ScriptPane, "insertText").mockReturnValue("field['Orders.price']");

         comp.updateExpression({
            target: "columnTree",
            node: {
               leaf: true,
               data: { qualifiedName: "Orders.price" },
            },
            expression: "",
            selection: { from: { line: 1, ch: 2 } },
         });

         expect(insertSpy).toHaveBeenCalledWith("", "field['Orders.price']", { from: { line: 1, ch: 2 } });
         expect(comp.attribute.expression).toBe("field['Orders.price']");
         expect(comp.cursor).toEqual({ line: 1, ch: 23 });
      });

      it("should use the raw expression when the selected node is not insertable", () => {
         const comp = createEditor();
         comp.attribute = makeAttribute({ expression: "old" });

         comp.updateExpression({
            target: "columnTree",
            node: { leaf: false, data: null },
            expression: "new value",
         });

         expect(comp.attribute.expression).toBe("new value");
      });
   });

   describe("Group 4 - attribute setter", () => {
      it("should disable editing when the attribute comes from a base element", () => {
         const comp = createEditor();

         comp.attribute = makeAttribute({ baseElement: true } as any);

         expect(comp.editable).toBe(false);
      });

      it("should enable editing when the attribute is not a base element", () => {
         const comp = createEditor();

         comp.attribute = makeAttribute({ baseElement: false });

         expect(comp.editable).toBe(true);
      });
   });
});
