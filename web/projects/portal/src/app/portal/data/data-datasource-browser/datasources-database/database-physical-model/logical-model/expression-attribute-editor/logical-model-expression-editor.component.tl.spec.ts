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

import { HttpClient, provideHttpClient } from "@angular/common/http";
import { HttpClientTestingModule, HttpTestingController } from "@angular/common/http/testing";
import { Component, NO_ERRORS_SCHEMA } from "@angular/core";
import { TestBed } from "@angular/core/testing";
import { UntypedFormControl, UntypedFormGroup } from "@angular/forms";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { render, waitFor } from "@testing-library/angular";
import { http as mswHttp, HttpResponse as MswHttpResponse } from "msw";

import { server } from "@test-mocks/server";
import { ScriptPane } from "../../../../../../../widget/dialog/script-pane/script-pane.component";
import { SplitPane } from "../../../../../../../widget/split-pane/split-pane.component";
import { DropdownView } from "../../../../../../../widget/dropdown-view/dropdown-view.component";
import { TreeDropdownComponent } from "../../../../../../../widget/tree/tree-dropdown.component";
import { AttributeModel } from "../../../../../model/datasources/database/physical-model/logical-model/attribute-model";
import { AttributeFormattingPane } from "../attribute-editor/format-dialog/attribute-formatting-pane.component";
import { LogicalModelAttributeEditor } from "../attribute-editor/logical-model-attribute-editor.component";
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

   describe("Group 1b - fields request", () => {
      // Bug #77402: the server grants the fields tree on READ of the logical model named in
      // the request, so a user whose only right is a per-model grant needs the name sent.
      it("should send the logical model name and parent when loading the fields tree", () => {
         const comp = createEditor();
         comp.databaseName = "Orders";
         comp.physicalModelName = "Model";
         comp.logicalModelName = "LM1";
         comp.logicalModelParent = "BaseLM";

         comp.ngOnInit();

         const req = http.expectOne(FIELDS_URI);
         expect(req.request.body.logicalName).toBe("LM1");
         expect(req.request.body.parent).toBe("BaseLM");
         req.flush({ label: "server-root", children: [] });
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

// ---------------------------------------------------------------------------
// Bug #82123: the expression editor must put its name control into the form it
// is given (the logical model's form that the toolbar Save button checks), not a
// private form, so an invalid expression name disables Save.
// ---------------------------------------------------------------------------

@Component({ selector: "split-pane", template: "<ng-content></ng-content>" })
class SplitPaneStub {}

@Component({ selector: "script-pane", template: "" })
class ScriptPaneStub {}

@Component({ selector: "tree-dropdown", template: "" })
class TreeDropdownStub {}

@Component({ selector: "dropdown-view", template: "" })
class DropdownViewStub {}

@Component({ selector: "attribute-formatting-pane", template: "" })
class AttributeFormattingPaneStub {}

describe("Bug #82123 - name validation reaches the parent form", () => {
   async function renderWithParentForm(existNames: string[] = []) {
      server.use(
         mswHttp.post("*/logicalModel/tables/nodes",
            () => MswHttpResponse.json({ label: "root", children: [] }))
      );
      TestBed.overrideComponent(LogicalModelAttributeEditor, {
         remove: { imports: [TreeDropdownComponent, DropdownView, AttributeFormattingPane] },
         add: { imports: [TreeDropdownStub, DropdownViewStub, AttributeFormattingPaneStub] },
      });

      const form = new UntypedFormGroup({});
      const { fixture } = await render(LogicalModelExpressionEditor, {
         inputs: {
            form,
            existNames,
            attribute: makeAttribute(),
            databaseName: "Orders",
            physicalModelName: "Model",
            logicalModelName: "LM1",
         },
         providers: [
            provideHttpClient(),
            { provide: NgbModal, useValue: { open: vi.fn() } },
         ],
         importOverrides: [
            { replace: SplitPane, with: SplitPaneStub },
            { replace: ScriptPane, with: ScriptPaneStub },
         ],
         schemas: [NO_ERRORS_SCHEMA],
      });

      const comp = fixture.componentInstance as LogicalModelExpressionEditor;
      // Both the editor and its attribute editor post to the fields URI on init; wait for
      // them so no response lands after the fixture is destroyed.
      await waitFor(() => expect(comp.columnTreeRoot).toBeDefined());
      return { comp, form };
   }

   afterEach(() => vi.restoreAllMocks());

   it("should add the name control to the form passed in", async () => {
      const { comp, form } = await renderWithParentForm();

      expect(comp.form).toBe(form);
      expect(form.contains("name")).toBe(true);
      expect(form.valid).toBe(true);
   });

   it("should make the passed-in form invalid for a name with a period", async () => {
      const { form } = await renderWithParentForm();

      form.get("name").setValue("a.b");

      expect(form.get("name").hasError("periodInName")).toBe(true);
      expect(form.invalid).toBe(true);
   });

   it("should make the passed-in form invalid for an empty name", async () => {
      const { form } = await renderWithParentForm();

      form.get("name").setValue("");

      expect(form.get("name").hasError("required")).toBe(true);
      expect(form.invalid).toBe(true);
   });

   it("should make the passed-in form invalid for a name used by a sibling attribute", async () => {
      const { form } = await renderWithParentForm(["taken"]);

      form.get("name").setValue("taken");

      expect(form.get("name").hasError("exists")).toBe(true);
      expect(form.invalid).toBe(true);
   });
});
