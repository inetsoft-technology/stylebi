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
 * LogicalModelExpressionDialog — single-pass (+race+memory leak)
 *
 * Risk-first coverage:
 *   Group 1 [Risk 3] — ok(): duplicate-name check → danger; success path → success +
 *                        onCommit; error-body → danger; HTTP error → danger
 *   Group 2 [Risk 3] — loadFields() race: second POST arrives before first resolves →
 *                        last writer wins (it.fails: no cancellation guard)
 *   Group 3 [Risk 3] — Memory leak (it.fails): post-destroy ok() HTTP callback updates state
 *   Group 4 [Risk 2] — ngOnInit: form created; functionTreeRoot/operatorTreeRoot set;
 *                        columnTreeRoot set from loadFields() POST response
 *   Group 5 [Risk 2] — updateExpression(): all 6 branches (no-node, non-leaf, no-data,
 *                        columnTree leaf, operator leaf, no-target leaf)
 *   Group 6 [Risk 1] — cancel(), nameControl/parentControl getters, form validation
 *
 * Mocking strategy:
 *   - ScriptPane — complex CodeMirror editor → importOverrides stub with @Output() expressionChange
 *   - NotificationsComponent — @ViewChild stub with success/danger vi.fn()
 *   - ModalHeaderComponent — importOverrides empty stub
 *   - HttpClient — provideHttpClient() + MSW
 *   - entities @Input — inline array of EntityModel fixtures
 */

import { Component, EventEmitter, Input, NO_ERRORS_SCHEMA, Output } from "@angular/core";
import { provideHttpClient } from "@angular/common/http";
import { render, waitFor } from "@testing-library/angular";
import { http, HttpResponse as MswHttpResponse } from "msw";

import { server } from "@test-mocks/server";
import { LogicalModelExpressionDialog } from "./logical-model-expression-dialog.component";
import { ScriptPane } from "../../../../../../../widget/dialog/script-pane/script-pane.component";
import { NotificationsComponent } from "../../../../../../../widget/notifications/notifications.component";
import { ModalHeaderComponent } from "../../../../../../../widget/modal-header/modal-header.component";
import { AttributeModel } from "../../../../../model/datasources/database/physical-model/logical-model/attribute-model";

// ---------------------------------------------------------------------------
// Stubs
// ---------------------------------------------------------------------------

@Component({ selector: "script-pane", template: "" })
class ScriptPaneStub {
   @Input() sql: boolean;
   @Input() expression: string;
   @Output() expressionChange = new EventEmitter<any>();
   @Input() columnTreeRoot: any;
   @Input() columnTreeEnabled: boolean;
   @Input() functionTreeRoot: any;
   @Input() functionTreeEnabled: boolean;
   @Input() operatorTreeRoot: any;
   @Input() required: boolean;
   @Input() cursor: any;
   @Input() preventEscape: boolean;
}

@Component({ selector: "notifications", template: "" })
class NotificationsStub {
   success = vi.fn();
   danger = vi.fn();
}

@Component({ selector: "modal-header", template: "" })
class ModalHeaderStub {
   @Input() title: string;
   @Input() cshid: string;
   @Output() onCancel = new EventEmitter<void>();
}

// ---------------------------------------------------------------------------
// Fixtures
// ---------------------------------------------------------------------------

function makeAttribute(name: string): AttributeModel {
   return {
      table: null, dataType: null, qualifiedName: null,
      name, oldName: name, expression: null,
      baseElement: false, elementType: "attributeElement", visible: true,
   };
}

function makeEntities(names: string[] = ["Entity0", "Entity1"]) {
   return names.map(n => ({
      name: n, attributes: [] as AttributeModel[],
      baseElement: false, errorMessage: null,
   }));
}

// ---------------------------------------------------------------------------
// Render helper
// ---------------------------------------------------------------------------

interface RenderOpts {
   entities?: ReturnType<typeof makeEntities>;
   parent?: number;
   databaseName?: string;
   physicalModelName?: string;
   additional?: string;
}

async function renderComp(opts: RenderOpts = {}) {
   const entities = opts.entities ?? makeEntities();
   const onCommitSpy = vi.fn();
   const onCancelSpy = vi.fn();

   const { fixture } = await render(LogicalModelExpressionDialog, {
      schemas: [NO_ERRORS_SCHEMA],
      providers: [provideHttpClient()],
      importOverrides: [
         { replace: ScriptPane, with: ScriptPaneStub },
         { replace: NotificationsComponent, with: NotificationsStub },
         { replace: ModalHeaderComponent, with: ModalHeaderStub },
      ],
      componentInputs: {
         entities,
         parent: opts.parent ?? 0,
         databaseName: opts.databaseName ?? "testDB",
         physicalModelName: opts.physicalModelName ?? "physModel",
         additional: opts.additional ?? null,
      },
      on: {
         onCommit: onCommitSpy,
         onCancel: onCancelSpy,
      },
   });
   const comp = fixture.componentInstance as LogicalModelExpressionDialog;
   return { comp, fixture, onCommitSpy, onCancelSpy };
}

// ---------------------------------------------------------------------------
// Global lifecycle
// ---------------------------------------------------------------------------

afterEach(() => {
   vi.restoreAllMocks();
});

// ---------------------------------------------------------------------------
// Group 1 — ok(): main interaction paths [Risk 3]
// ---------------------------------------------------------------------------

describe("LogicalModelExpressionDialog — ok()", () => {
   // 🔁 Regression-sensitive: the duplicate check must compare against the PARENT entity's
   // attributes, not all attributes. A wrong parent index silently allows duplicates.

   it("should show danger notification when attribute name already exists in parent entity", async () => {
      const entities = makeEntities();
      entities[0].attributes.push(makeAttribute("existingAttr"));
      const { comp } = await renderComp({ entities });

      comp.form.get("name")!.setValue("existingAttr");
      comp.expression = "1+1";
      comp.ok();

      expect((comp.notifications as any).danger).toHaveBeenCalledWith(
         "_#(js:data.logicalmodel.attributeNameDuplicate)"
      );
   });

   it("should not call HTTP when duplicate name is detected", async () => {
      let httpCalled = false;
      server.use(
         http.post("*/api/data/logicalModel/attribute/expression", () => {
            httpCalled = true;
            return MswHttpResponse.json({});
         })
      );
      const entities = makeEntities();
      entities[0].attributes.push(makeAttribute("dup"));
      const { comp } = await renderComp({ entities });

      comp.form.get("name")!.setValue("dup");
      comp.expression = "1+1";
      comp.ok();

      await new Promise<void>(r => setTimeout(r, 50));
      expect(httpCalled).toBe(false);
   });

   it("should show success notification and emit onCommit when POST returns no error body", async () => {
      server.use(
         http.post("*/api/data/logicalModel/attribute/expression", () =>
            MswHttpResponse.json({})
         )
      );
      const { comp, onCommitSpy } = await renderComp();

      comp.form.get("name")!.setValue("newAttr");
      comp.expression = "1+1";
      comp.ok();

      await waitFor(() =>
         expect((comp.notifications as any).success).toHaveBeenCalledWith(
            "_#(js:data.logicalmodel.expression.success)"
         )
      );
      expect(onCommitSpy).toHaveBeenCalledTimes(1);
   });

   it("should emit onCommit with correct entity and attribute on success", async () => {
      server.use(
         http.post("*/api/data/logicalModel/attribute/expression", () =>
            MswHttpResponse.json({})
         )
      );
      const entities = makeEntities(["MyEntity"]);
      const { comp, onCommitSpy } = await renderComp({ entities, parent: 0 });

      comp.form.get("name")!.setValue("calcAttr");
      comp.expression = "SUM(field['col'])";
      comp.ok();

      await waitFor(() => expect(onCommitSpy).toHaveBeenCalled());
      const emitted = onCommitSpy.mock.calls[0][0];
      expect(emitted.entity.name).toBe("MyEntity");
      expect(emitted.attributes).toHaveLength(1);
      expect(emitted.attributes[0].name).toBe("calcAttr");
      expect(emitted.attributes[0].expression).toBe("SUM(field['col'])");
   });

   it("should show danger notification when POST response has a body error string", async () => {
      server.use(
         http.post("*/api/data/logicalModel/attribute/expression", () =>
            MswHttpResponse.json({ body: "Syntax error near token" })
         )
      );
      const { comp } = await renderComp();

      comp.form.get("name")!.setValue("badAttr");
      comp.expression = "invalid!!!";
      comp.ok();

      await waitFor(() =>
         expect((comp.notifications as any).danger).toHaveBeenCalledWith(
            "_#(js:Error): Syntax error near token"
         )
      );
   });

   it("should NOT emit onCommit when POST returns an error body", async () => {
      server.use(
         http.post("*/api/data/logicalModel/attribute/expression", () =>
            MswHttpResponse.json({ body: "Some error" })
         )
      );
      const { comp, onCommitSpy } = await renderComp();

      comp.form.get("name")!.setValue("attr1");
      comp.expression = "bad";
      comp.ok();

      await waitFor(() =>
         expect((comp.notifications as any).danger).toHaveBeenCalled()
      );
      expect(onCommitSpy).not.toHaveBeenCalled();
   });

   it("should show danger notification with error.message on HTTP error", async () => {
      server.use(
         http.post("*/api/data/logicalModel/attribute/expression", () =>
            MswHttpResponse.json({ error: "Internal" }, { status: 500 })
         )
      );
      const { comp } = await renderComp();

      comp.form.get("name")!.setValue("attr1");
      comp.expression = "x";
      comp.ok();

      await waitFor(() =>
         expect((comp.notifications as any).danger).toHaveBeenCalledWith(
            expect.stringMatching(/^_#\(js:Error\)/)
         )
      );
   });

   it("should use the correct parent entity when parent index is non-zero", async () => {
      server.use(
         http.post("*/api/data/logicalModel/attribute/expression", () =>
            MswHttpResponse.json({})
         )
      );
      const entities = makeEntities(["EntityA", "EntityB"]);
      const { comp, onCommitSpy } = await renderComp({ entities, parent: 1 });

      comp.form.get("name")!.setValue("myAttr");
      comp.expression = "1";
      comp.ok();

      await waitFor(() => expect(onCommitSpy).toHaveBeenCalled());
      expect(onCommitSpy.mock.calls[0][0].entity.name).toBe("EntityB");
   });
});

// ---------------------------------------------------------------------------
// Group 2 — loadFields() race condition [Risk 3]
// ---------------------------------------------------------------------------

describe("LogicalModelExpressionDialog — loadFields() race condition", () => {
   // Expected failure: `expect(comp2.columnTreeRoot?.label).toBe("Fields-Second")` fails
   // because comp1's stale POST resolves last and overwrites comp2.columnTreeRoot.label with
   // "Fields-First". Root cause: no takeUntilDestroyed in loadFields() subscription.
   // If the test fails for a reason other than the assertion (e.g. callCount never reaches 2),
   // verify the MSW handler wiring for resolveFirst / resolveSecond.

   it.fails("stale first-POST response must not overwrite columnTreeRoot from a later call", async () => {
      let resolveFirst!: (r: MswHttpResponse<any>) => void;
      let resolveSecond!: (r: MswHttpResponse<any>) => void;
      let callCount = 0;

      const firstTree = { label: "Fields-First", expanded: false, leaf: false, children: [] };
      const secondTree = { label: "Fields-Second", expanded: false, leaf: false, children: [] };

      server.use(
         http.post("*/api/data/logicalModel/tables/nodes", () => {
            callCount++;
            if(callCount === 1) {
               return new Promise<MswHttpResponse<any>>(res => { resolveFirst = res as any; });
            }
            else {
               return new Promise<MswHttpResponse<any>>(res => { resolveSecond = res as any; });
            }
         })
      );

      // First component render (triggers first POST)
      await renderComp({ additional: "conn1" });
      await waitFor(() => expect(callCount).toBe(1));

      // Second render with different params (triggers second POST in a new component instance)
      const { comp: comp2 } = await renderComp({ additional: "conn2" });
      await waitFor(() => expect(callCount).toBe(2));

      // Second resolves first with newer data
      resolveSecond!(MswHttpResponse.json(secondTree) as any);
      await waitFor(() => expect(comp2.columnTreeRoot?.label).toBe("Fields-Second"));

      // First resolves after — should not overwrite
      resolveFirst!(MswHttpResponse.json(firstTree) as any);
      await new Promise<void>(r => setTimeout(r, 0));

      expect(comp2.columnTreeRoot?.label).toBe("Fields-Second");
   });
});

// ---------------------------------------------------------------------------
// Group 3 — Memory leak: post-destroy ok() callback [Risk 3]
// ---------------------------------------------------------------------------

describe("LogicalModelExpressionDialog — post-destroy HTTP callback (memory leak)", () => {
   // Expected failure: `expect(onCommitSpy).not.toHaveBeenCalled()` fails because the
   // httpClient.post().subscribe() in ok() has no takeUntilDestroyed guard — onCommit is
   // emitted after fixture.destroy(). Root cause: missing ngOnDestroy / DestroyRef.
   // If the test fails for a reason other than the assertion (e.g. fixture.destroy() throws),
   // verify the error is an AssertionError on onCommitSpy, not an unrelated exception.
   it.fails("post-destroy ok() HTTP callback must not emit onCommit or show notification", async () => {
      let resolvePost!: (r: MswHttpResponse<any>) => void;
      server.use(
         http.post("*/api/data/logicalModel/attribute/expression", () =>
            new Promise<MswHttpResponse<any>>(res => { resolvePost = res as any; })
         )
      );

      const { comp, fixture, onCommitSpy } = await renderComp();
      comp.form.get("name")!.setValue("attr1");
      comp.expression = "1+1";
      comp.ok();

      fixture.destroy();

      resolvePost!(MswHttpResponse.json({}) as any);
      await new Promise<void>(r => setTimeout(r, 0));

      expect(onCommitSpy).not.toHaveBeenCalled();
      expect((comp.notifications as any).success).not.toHaveBeenCalled();
   });
});

// ---------------------------------------------------------------------------
// Group 4 — ngOnInit [Risk 2]
// ---------------------------------------------------------------------------

describe("LogicalModelExpressionDialog — ngOnInit", () => {
   it("should create form with name and parent controls", async () => {
      const { comp } = await renderComp();
      expect(comp.form.get("name")).toBeTruthy();
      expect(comp.form.get("parent")).toBeTruthy();
   });

   it("should initialize parent form control from @Input() parent", async () => {
      const { comp } = await renderComp({ parent: 1 });
      expect(comp.form.get("parent")!.value).toBe(1);
   });

   it("should set functionTreeRoot with Functions/Aggregate children", async () => {
      const { comp } = await renderComp();
      expect(comp.functionTreeRoot).toBeTruthy();
      expect(comp.functionTreeRoot.children).toHaveLength(1);
      expect(comp.functionTreeRoot.children[0].label).toBe("_#(js:Aggregate)");
   });

   it("should set operatorTreeRoot with Arithmetic and Relational children", async () => {
      const { comp } = await renderComp();
      expect(comp.operatorTreeRoot).toBeTruthy();
      expect(comp.operatorTreeRoot.children).toHaveLength(2);
      expect(comp.operatorTreeRoot.children[0].label).toBe("_#(js:Arithmetic)");
      expect(comp.operatorTreeRoot.children[1].label).toBe("_#(js:Relational)");
   });

   it("should set columnTreeRoot from the loadFields POST response", async () => {
      const treeResponse = {
         label: "custom-root", expanded: false, leaf: false,
         children: [{ label: "col1", leaf: true, data: { qualifiedName: "t.col1" } }],
      };
      server.use(
         http.post("*/api/data/logicalModel/tables/nodes", () =>
            MswHttpResponse.json(treeResponse)
         )
      );

      const { comp } = await renderComp();

      await waitFor(() => expect(comp.columnTreeRoot).toBeDefined());
      // loadFields always overrides label with "_#(js:Fields)" and sets expanded=false
      expect(comp.columnTreeRoot.label).toBe("_#(js:Fields)");
      expect(comp.columnTreeRoot.expanded).toBe(false);
   });

   it("should call loadFields POST with correct databaseName and physicalModelName", async () => {
      let capturedBody: any;
      server.use(
         http.post("*/api/data/logicalModel/tables/nodes", async ({ request }) => {
            capturedBody = await request.json();
            return MswHttpResponse.json({ label: "Fields", expanded: false, leaf: false, children: [] });
         })
      );

      await renderComp({ databaseName: "myDB", physicalModelName: "myPhys", additional: "myConn" });

      await waitFor(() => expect(capturedBody).toBeDefined());
      // GetModelEvent serializes as { datasource, physicalName, logicalName, parent, additional }
      expect(capturedBody.datasource).toBe("myDB");
      expect(capturedBody.physicalName).toBe("myPhys");
      expect(capturedBody.additional).toBe("myConn");
   });
});

// ---------------------------------------------------------------------------
// Group 5 — updateExpression() branches [Risk 2]
// ---------------------------------------------------------------------------

describe("LogicalModelExpressionDialog — updateExpression()", () => {
   // 🔁 Regression-sensitive: these branches determine how the SQL expression is assembled.
   // A wrong case (e.g., applying columnTree prefix to an operator node) corrupts the SQL.

   it("should set expression to obj.expression when node is null", async () => {
      const { comp } = await renderComp();
      comp.updateExpression({ expression: "direct", node: null, target: "columnTree" });
      expect(comp.expression).toBe("direct");
   });

   it("should set expression to obj.expression when node.leaf is false", async () => {
      const { comp } = await renderComp();
      comp.updateExpression({ expression: "folder", node: { leaf: false, data: {} }, target: "columnTree" });
      expect(comp.expression).toBe("folder");
   });

   it("should set expression to obj.expression when node.data is null", async () => {
      const { comp } = await renderComp();
      comp.updateExpression({ expression: "nodata", node: { leaf: true, data: null }, target: "columnTree" });
      expect(comp.expression).toBe("nodata");
   });

   it("should insert field reference when target=columnTree with a leaf node", async () => {
      const { comp } = await renderComp();
      const selection = { from: { line: 0, ch: 0 }, to: { line: 0, ch: 0 } };
      comp.updateExpression({
         expression: "",
         node: { leaf: true, data: { qualifiedName: "t.col1" } },
         target: "columnTree",
         selection,
      });
      expect(comp.expression).toContain("field['t.col1']");
   });

   it("should insert node.data directly when target is not columnTree and node is leaf", async () => {
      const { comp } = await renderComp();
      const selection = { from: { line: 0, ch: 0 }, to: { line: 0, ch: 0 } };
      comp.updateExpression({
         expression: "",
         node: { leaf: true, data: "SUM()" },
         target: "functionTree",
         selection,
      });
      expect(comp.expression).toContain("SUM()");
   });

   it("should set expression to empty string when target is falsy and node is leaf with data", async () => {
      const { comp } = await renderComp();
      comp.updateExpression({
         expression: "anything",
         node: { leaf: true, data: "+" },
         target: null,
      });
      // fexpress stays "" because neither branch appends to it; no insertText called
      expect(comp.expression).toBe("");
   });

   it("should update cursor position after inserting a columnTree node", async () => {
      const { comp } = await renderComp();
      const selection = { from: { line: 2, ch: 5 }, to: { line: 2, ch: 5 } };
      comp.updateExpression({
         expression: "1+1\n2+2\nABC",
         node: { leaf: true, data: { qualifiedName: "orders.total" } },
         target: "columnTree",
         selection,
      });
      // field['orders.total'] = 20 chars; cursor.ch = 5 + 20 = 25
      expect(comp.cursor.line).toBe(2);
      expect(comp.cursor.ch).toBe(5 + "field['orders.total']".length);
   });
});

// ---------------------------------------------------------------------------
// Group 6 — cancel, getters, form validation [Risk 1]
// ---------------------------------------------------------------------------

describe("LogicalModelExpressionDialog — cancel()", () => {
   it("should emit onCancel with 'cancel' when cancel() is called", async () => {
      const { comp, onCancelSpy } = await renderComp();
      comp.cancel();
      expect(onCancelSpy).toHaveBeenCalledWith("cancel");
   });
});

describe("LogicalModelExpressionDialog — nameControl / parentControl getters", () => {
   it("nameControl should return the 'name' form control", async () => {
      const { comp } = await renderComp();
      expect(comp.nameControl).toBe(comp.form.get("name"));
   });

   it("parentControl should return the 'parent' form control", async () => {
      const { comp } = await renderComp();
      expect(comp.parentControl).toBe(comp.form.get("parent"));
   });
});

describe("LogicalModelExpressionDialog — form validation", () => {
   it("form should be invalid when name is empty", async () => {
      const { comp } = await renderComp();
      comp.form.get("name")!.setValue("");
      expect(comp.form.valid).toBe(false);
      expect(comp.nameControl.hasError("required")).toBe(true);
   });

   it("form should be valid when name is non-empty", async () => {
      const { comp } = await renderComp();
      comp.form.get("name")!.setValue("myAttr");
      expect(comp.form.valid).toBe(true);
   });
});
