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
 * QueryFieldsPaneComponent — Pass 2 (Risk / edge-cases)
 *
 * Covers the paths deferred from Pass 1 because they require more complex
 * async setups or are focused on boundary / error behavior:
 *
 *   Group R1 [Risk 4] — addColumns: limitMessage branch opens confirm dialog
 *   Group R2 [Risk 4] — showFieldDialog (addExpression / editExpression):
 *                        HTTP callback after modal commit; add vs edit branches
 *   Group R3 [Risk 3] — browseColumnData: no selectedNode (expression field) →
 *                        warning modal fires instead of GET
 *   Group R4 [Risk 3] — removeColumns: when all=true, resets state completely
 *                        and columnsOrderMap goes empty before HTTP fires
 *   Group R5 [Risk 3] — updateAlias + columnsOrderMap sync: orderMap entry is
 *                        updated in tandem with the alias rename
 */

import { waitFor } from "@testing-library/angular";
import { http, HttpResponse } from "msw";

import { server } from "@test-mocks/server";
import {
   MODAL_MOCK,
   QUERY_MODEL_SERVICE_MOCK,
   makeField,
   makeFieldsTree,
   makeLeafNode,
   makeModel,
   renderComponent,
   resetMocks,
} from "./query-fields-pane.component.test-helpers";

// ---------------------------------------------------------------------------
// Global lifecycle
// ---------------------------------------------------------------------------

beforeEach(() => resetMocks());
afterEach(() => vi.restoreAllMocks());

// ---------------------------------------------------------------------------
// Group R1 — addColumns: limitMessage shows confirm dialog [Risk 4]
// ---------------------------------------------------------------------------

describe("QueryFieldsPaneComponent — addColumns: limitMessage confirm dialog", () => {
   // 🔁 Regression-sensitive: If limitMessage handling is silently dropped, users
   // are not told that the column limit was exceeded — data appears truncated with
   // no warning, leading to silent incorrect query results.

   it("should open a confirm dialog when limitMessage is non-empty", async () => {
      server.use(
         http.post("*/api/data/datasource/query/column/add", () =>
            HttpResponse.json({
               limitMessage: "Column limit exceeded (max 50)",
               columnMap: { col3: "col3" },
            }),
         ),
      );
      const { comp } = await renderComponent();
      comp.selectedNodes = [makeLeafNode("col3")];
      MODAL_MOCK.open.mockClear();
      comp.add();
      // emitModelChange is called first (for the columnMap entries), then the confirm dialog
      await waitFor(() => expect(QUERY_MODEL_SERVICE_MOCK.emitModelChange).toHaveBeenCalled());
      // confirm dialog should also have been opened
      await waitFor(() => expect(MODAL_MOCK.open).toHaveBeenCalled());
   });

   it("should still add columns to columnsOrderMap even when limitMessage is present", async () => {
      server.use(
         http.post("*/api/data/datasource/query/column/add", () =>
            HttpResponse.json({
               limitMessage: "Too many columns",
               columnMap: { aliasA: "nameA", aliasB: "nameB" },
            }),
         ),
      );
      const { comp } = await renderComponent();
      const beforeCount = comp.columnsOrderMap.length;
      comp.addColumns([{ properties: { attribute: "nameA" } } as any]);
      await waitFor(() => expect(comp.columnsOrderMap.length).toBe(beforeCount + 2));
      expect(comp.columnsOrderMap).toContainEqual({ alias: "aliasA", name: "nameA" });
      expect(comp.columnsOrderMap).toContainEqual({ alias: "aliasB", name: "nameB" });
   });

   it("should not open a confirm dialog when limitMessage is null", async () => {
      server.use(
         http.post("*/api/data/datasource/query/column/add", () =>
            HttpResponse.json({ limitMessage: null, columnMap: { c: "c" } }),
         ),
      );
      const { comp } = await renderComponent();
      MODAL_MOCK.open.mockClear();
      comp.addColumns([{ properties: { attribute: "c" } } as any]);
      await waitFor(() => expect(QUERY_MODEL_SERVICE_MOCK.emitModelChange).toHaveBeenCalled());
      // Only emitModelChange triggered; no confirm dialog
      expect(MODAL_MOCK.open).not.toHaveBeenCalled();
   });
});

// ---------------------------------------------------------------------------
// Group R2 — showFieldDialog (addExpression / editExpression) [Risk 4]
// ---------------------------------------------------------------------------

describe("QueryFieldsPaneComponent — showFieldDialog: add / edit expression paths", () => {
   // 🔁 Regression-sensitive: addExpression passes add=true and omits columnName/columnAlias
   // params; editExpression passes column identifiers. Mixing them up corrupts the server-
   // side expression mapping and can silently overwrite existing expression columns.

   it("addExpression: POSTs expression/save with add=true and NO columnName/columnAlias params", async () => {
      let capturedUrl: string;
      server.use(
         http.post("*/api/data/datasource/query/expression/save", ({ request }) => {
            capturedUrl = request.url;
            return HttpResponse.json(["newAlias", "newName"]);
         }),
      );
      const { comp } = await renderComponent();
      await waitFor(() => expect(comp.selectedFieldIndexes).toEqual([0]));
      MODAL_MOCK.open.mockClear();
      comp.addExpression();

      // Commit the expression dialog
      const modalRef = MODAL_MOCK.open.mock.results[0].value;
      modalRef.componentInstance.onCommit.next("expression_body");
      await waitFor(() => expect(QUERY_MODEL_SERVICE_MOCK.emitModelChange).toHaveBeenCalled());

      // URL must contain add=true and must NOT contain columnName
      expect(capturedUrl).toContain("add=true");
      expect(capturedUrl).not.toContain("columnName");
   });

   it("addExpression: updates selectedFieldName/Alias from server response on success", async () => {
      server.use(
         http.post("*/api/data/datasource/query/expression/save", () =>
            HttpResponse.json(["newAlias", "newName"]),
         ),
      );
      const { comp } = await renderComponent();
      await waitFor(() => expect(comp.selectedFieldIndexes).toEqual([0]));
      comp.addExpression();
      const modalRef = MODAL_MOCK.open.mock.results[0].value;
      modalRef.componentInstance.onCommit.next("expr");
      await waitFor(() => expect(comp.selectedFieldName).toBe("newName"));
      expect(comp.selectedFieldAlias).toBe("newAlias");
      expect(comp.columnsOrderMap).toContainEqual({ alias: "newAlias", name: "newName" });
   });

   it("editExpression: POSTs expression/save with add=false and includes columnName/columnAlias", async () => {
      let capturedUrl: string;
      server.use(
         http.post("*/api/data/datasource/query/expression/save", ({ request }) => {
            capturedUrl = request.url;
            return HttpResponse.json(["editedAlias", "editedName"]);
         }),
      );
      const { comp } = await renderComponent();
      // Set up a tree node matching the selected field so the dialog gets it as an expression
      comp.databaseFieldsTree = makeFieldsTree([makeLeafNode("col1")]);
      await waitFor(() => expect(comp.selectedFieldIndexes).toEqual([0]));
      MODAL_MOCK.open.mockClear();
      comp.editExpression();

      const modalRef = MODAL_MOCK.open.mock.results[0].value;
      modalRef.componentInstance.onCommit.next("edited_expr");
      await waitFor(() => expect(QUERY_MODEL_SERVICE_MOCK.emitModelChange).toHaveBeenCalled());

      expect(capturedUrl).toContain("add=false");
      expect(capturedUrl).toContain("columnName=col1");
      expect(capturedUrl).toContain("columnAlias=col1");
   });

   it("editExpression: updates existing columnsOrderMap entry (does not push a new one)", async () => {
      server.use(
         http.post("*/api/data/datasource/query/expression/save", () =>
            HttpResponse.json(["editedAlias", "editedName"]),
         ),
      );
      const { comp } = await renderComponent();
      comp.databaseFieldsTree = makeFieldsTree([makeLeafNode("col1")]);
      await waitFor(() => expect(comp.selectedFieldIndexes).toEqual([0]));
      const beforeCount = comp.columnsOrderMap.length;
      comp.editExpression();
      const modalRef = MODAL_MOCK.open.mock.results[0].value;
      modalRef.componentInstance.onCommit.next("edited_expr");
      await waitFor(() => expect(comp.selectedFieldName).toBe("editedName"));
      // Should NOT have added a new entry; existing col1 entry should be updated
      expect(comp.columnsOrderMap.length).toBe(beforeCount);
      expect(comp.columnsOrderMap).not.toContainEqual({ alias: "col1", name: "col1" });
      expect(comp.columnsOrderMap).toContainEqual({ alias: "editedAlias", name: "editedName" });
   });

   it("showFieldDialog: no emitModelChange if expression/save returns null/empty", async () => {
      let saveHandlerFired = false;
      server.use(
         http.post("*/api/data/datasource/query/expression/save", () => {
            saveHandlerFired = true;
            return HttpResponse.json(null);
         }),
      );
      const { comp } = await renderComponent();
      await waitFor(() => expect(comp.selectedFieldIndexes).toEqual([0]));
      QUERY_MODEL_SERVICE_MOCK.emitModelChange.mockClear();
      comp.addExpression();
      const modalRef = MODAL_MOCK.open.mock.results[0].value;
      modalRef.componentInstance.onCommit.next("expr");
      // Gate: wait for the HTTP handler to fire before asserting the negative.
      await waitFor(() => expect(saveHandlerFired).toBe(true));
      expect(QUERY_MODEL_SERVICE_MOCK.emitModelChange).not.toHaveBeenCalled();
   });
});

// ---------------------------------------------------------------------------
// Group R3 — browseColumnData: expression field (no tree node) path [Risk 3]
// ---------------------------------------------------------------------------

describe("QueryFieldsPaneComponent — browseColumnData: expression field warning", () => {
   // 🔁 Regression-sensitive: expression columns have no matching tree node;
   // browseColumnData must show the "not supported" warning (not fire the GET).
   // If the guard is removed, the GET fires with a server-generated column name
   // that doesn't exist, returning an empty result and showing a confusing "no data" modal.

   it("should show warning dialog and NOT fire GET when selected field has no tree node", async () => {
      // The default databaseFieldsTree from portal.handlers returns { nodes: [] } (no children),
      // so getSelectedFieldTreeNode() returns undefined for any selected field.
      let browseWasCalled = false;
      server.use(
         http.get("*/api/data/datasource/query/column/browse", () => {
            browseWasCalled = true;
            return HttpResponse.json([]);
         }),
      );
      const { comp } = await renderComponent();
      await waitFor(() => expect(comp.selectedFieldIndexes).toEqual([0]));
      MODAL_MOCK.open.mockClear();
      comp.browseColumnData();
      // Warning modal opens synchronously when tree node is absent; one microtask tick is sufficient.
      expect(MODAL_MOCK.open).toHaveBeenCalledTimes(1);
      await Promise.resolve();
      expect(browseWasCalled).toBe(false);
   });
});

// ---------------------------------------------------------------------------
// Group R4 — removeColumns with all=true: state cleared before HTTP [Risk 3]
// ---------------------------------------------------------------------------

describe("QueryFieldsPaneComponent — removeColumns: all=true clears state synchronously", () => {
   // 🔁 Regression-sensitive: removeColumnsInOrderMap(aliases, true) must clear
   // selectedFieldName/Alias and columnsOrderMap BEFORE the HTTP fires so that a
   // subsequent re-render (triggered by an ngOnChanges from the response) doesn't
   // try to access indices into an empty fields array.

   it("should clear columnsOrderMap and selectedField synchronously when all=true", async () => {
      server.use(
         http.post("*/api/data/datasource/query/column/remove", () =>
            new HttpResponse(null, { status: 200 }),
         ),
      );
      const { comp } = await renderComponent();
      await waitFor(() => expect(comp.selectedFieldIndexes).toEqual([0]));

      // Synchronous part: state is cleared inside removeColumnsInOrderMap(aliases, true)
      // which is called synchronously within removeColumns() before the HTTP call.
      comp.removeAll();

      // State cleared synchronously — no need to await
      expect(comp.columnsOrderMap).toEqual([]);
      expect(comp.selectedFieldName).toBeNull();
      expect(comp.selectedFieldAlias).toBeNull();
      expect(comp.selectedFieldIndexes).toEqual([]);
   });

   it("should still fire emitModelChange after the HTTP response when all=true", async () => {
      server.use(
         http.post("*/api/data/datasource/query/column/remove", () =>
            new HttpResponse(null, { status: 200 }),
         ),
      );
      const { comp } = await renderComponent();
      await waitFor(() => expect(comp.selectedFieldIndexes).toEqual([0]));
      QUERY_MODEL_SERVICE_MOCK.emitModelChange.mockClear();
      comp.removeAll();
      await waitFor(() => expect(QUERY_MODEL_SERVICE_MOCK.emitModelChange).toHaveBeenCalledTimes(1));
   });
});

// ---------------------------------------------------------------------------
// Group R5 — updateAlias + columnsOrderMap sync [Risk 3]
// ---------------------------------------------------------------------------

describe("QueryFieldsPaneComponent — updateAlias: columnsOrderMap stays in sync", () => {
   // 🔁 Regression-sensitive: if columnsOrderMap is not updated together with
   // model.fields[i].alias, the next reloadColumnsOrder() (triggered by ngOnChanges)
   // uses the old alias to locate the field and fails to match it — the field is
   // silently dropped from the ordered list and disappears from the UI after the
   // next model refresh.

   it("should replace the old alias entry in columnsOrderMap with the new alias", async () => {
      server.use(
         http.post("*/api/data/datasource/query/column/update", () =>
            new HttpResponse(null, { status: 200 }),
         ),
      );
      const { comp } = await renderComponent();
      await waitFor(() => expect(comp.selectedFieldIndexes).toEqual([0]));

      comp.updateAlias({ target: { value: "newAlias" } } as any);
      await waitFor(() => expect(comp.selectedFieldAlias).toBe("newAlias"));

      // columnsOrderMap must reflect the renamed alias
      expect(comp.columnsOrderMap).not.toContainEqual({ alias: "col1", name: "col1" });
      expect(comp.columnsOrderMap).toContainEqual({ alias: "newAlias", name: "col1" });
   });

   it("should update the field in columnsOrderMap at the correct position (preserving order)", async () => {
      server.use(
         http.post("*/api/data/datasource/query/column/update", () =>
            new HttpResponse(null, { status: 200 }),
         ),
      );
      const model = makeModel([
         makeField("a", "aliasA"),
         makeField("b", "aliasB"),
         makeField("c", "aliasC"),
      ]);
      const { comp } = await renderComponent({ model });
      await waitFor(() => expect(comp.selectedFieldIndexes).toEqual([0]));

      comp.updateAlias({ target: { value: "renamedA" } } as any);
      await waitFor(() => expect(comp.selectedFieldAlias).toBe("renamedA"));

      // Order preserved: [renamedA/a, aliasB/b, aliasC/c]
      expect(comp.columnsOrderMap[0]).toEqual({ alias: "renamedA", name: "a" });
      expect(comp.columnsOrderMap[1]).toEqual({ alias: "aliasB", name: "b" });
      expect(comp.columnsOrderMap[2]).toEqual({ alias: "aliasC", name: "c" });
   });
});
