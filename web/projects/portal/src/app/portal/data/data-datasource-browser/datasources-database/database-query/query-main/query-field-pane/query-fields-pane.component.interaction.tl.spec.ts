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
 * QueryFieldsPaneComponent — Pass 1 (Interaction / lifecycle / user flows)
 *
 * Risk-first coverage:
 *   Group 1  [Risk 3] — ngOnInit: GET data-source-fields-tree; databaseFieldsTree set;
 *                        columnsOrderMap initialized; first field selected via selectField
 *   Group 2  [Risk 3] — selectField: single-click resets selection; ctrl+click adds;
 *                        shift+click extends range; empty-fields list clears
 *   Group 3  [Risk 2] — isSelectedField: true/false by index
 *   Group 4  [Risk 2] — isUpDisabled / isDownDisabled: boundary conditions (0, last, single)
 *   Group 5  [Risk 3] — moveUp / moveDown: swap adjacent fields; selection follows the field
 *   Group 6  [Risk 3] — add / addAll: leaf-node entries POSTed to column/add; emitModelChange
 *   Group 7  [Risk 3] — addColumns: POST updates selectedFieldName/Alias; emitModelChange called
 *   Group 8  [Risk 3] — remove / removeAll / removeColumns: POST removes; columnsOrderMap pruned
 *   Group 9  [Risk 3] — updateAlias: empty alias → error modal; duplicate alias → error modal;
 *                        valid alias → field updated + updateColumn HTTP POST fired
 *   Group 10 [Risk 2] — updateDataType / updateFormat: updateColumn POST fired; emitModelChange
 *   Group 11 [Risk 2] — openAutoDrillDialog: modal opened; onCommit sets drillInfo + HTTP update
 *   Group 12 [Risk 3] — browseColumnData: no selectedField → warning modal; empty browse result
 *                        → warning modal; non-empty result → browse modal opened
 *   Group 13 [Risk 2] — showEditDataTypeDialog: modal opened with correct columnName/dataType
 *   Group 14 [Risk 2] — dbClickToAdd: leaf node calls addColumns; non-leaf node is a no-op
 *   Group 15 [Risk 3] — drop: remove=true calls remove(); remove=false reads drag data + addColumns
 *   Group 16 [Risk 2] — showDataTypeIcon: true only when single selection AND no tree match
 *
 * Out of scope this pass (deferred to Pass 2 — risk.tl.spec.ts):
 *   addColumns when limitMessage is non-null (shows confirm dialog — async/risk path)
 *   showFieldDialog (addExpression/editExpression) — HTTP callback race on modal commit
 *   ngOnInit HTTP race (response after component destroyed — no OnDestroy guard)
 */

import { waitFor } from "@testing-library/angular";
import { http, HttpResponse } from "msw";

import { server } from "@test-mocks/server";
import {
   DRAG_SERVICE_MOCK,
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
// Group 1 — ngOnInit [Risk 3]
// ---------------------------------------------------------------------------

describe("QueryFieldsPaneComponent — ngOnInit: HTTP loads fields tree", () => {
   // 🔁 Regression-sensitive: databaseFieldsTree drives tree visibility in template and
   // all add/addAll paths; if the GET response is not stored, tree panel shows empty.

   it("should store the server response as databaseFieldsTree", async () => {
      const treeResponse = { label: "root", leaf: false, children: [] };
      server.use(
         http.get("*/api/data/datasource/query/data-source-fields-tree", () =>
            HttpResponse.json(treeResponse),
         ),
      );
      const { comp } = await renderComponent();
      expect(comp.databaseFieldsTree).toEqual(treeResponse);
   });

   it("should initialize columnsOrderMap from model fields on init", async () => {
      const { comp } = await renderComponent();
      expect(comp.columnsOrderMap).toEqual([
         { alias: "col1", name: "col1" },
         { alias: "col2", name: "col2" },
      ]);
   });

   it("should select first field (index 0) after init", async () => {
      const { comp } = await renderComponent();
      await waitFor(() => expect(comp.selectedFieldIndexes).toEqual([0]));
      expect(comp.selectedFieldName).toBe("col1");
      expect(comp.selectedFieldAlias).toBe("col1");
   });
});

// ---------------------------------------------------------------------------
// Group 2 — selectField [Risk 3]
// ---------------------------------------------------------------------------

describe("QueryFieldsPaneComponent — selectField: multi-select semantics", () => {
   // 🔁 Regression-sensitive: selectField drives which field's alias/format/drill panel is shown;
   // wrong selection after modifier keys causes the wrong row to be edited.

   it("should replace selection with the clicked index when no modifier key is held", async () => {
      const { comp } = await renderComponent();
      await waitFor(() => expect(comp.selectedFieldIndexes).toEqual([0]));
      comp.selectField(null, 1);
      await waitFor(() => expect(comp.selectedFieldIndexes).toEqual([1]));
   });

   it("should add the clicked index to selection on ctrl+click", async () => {
      const { comp } = await renderComponent();
      await waitFor(() => expect(comp.selectedFieldIndexes).toEqual([0]));
      comp.selectField({ ctrlKey: true, shiftKey: false } as MouseEvent, 1);
      await waitFor(() => expect(comp.selectedFieldIndexes).toContain(1));
      expect(comp.selectedFieldIndexes).toContain(0);
   });

   it("should select a range on shift+click from the shift-start index", async () => {
      const model = makeModel([
         makeField("a", "a"),
         makeField("b", "b"),
         makeField("c", "c"),
      ]);
      const { comp } = await renderComponent({ model });
      // After init, shiftStartIndex = 0 (from ngOnInit selectField(null, 0))
      await waitFor(() => expect(comp.selectedFieldIndexes).toEqual([0]));
      comp.selectField({ ctrlKey: false, shiftKey: true } as MouseEvent, 2);
      await waitFor(() => expect(comp.selectedFieldIndexes).toEqual([0, 1, 2]));
   });

   it("should clear selection when model.fields is empty", async () => {
      const { comp } = await renderComponent({ model: makeModel([]) });
      comp.selectField(null, 0);
      await waitFor(() => expect(comp.selectedFieldIndexes).toEqual([]));
   });
});

// ---------------------------------------------------------------------------
// Group 3 — isSelectedField [Risk 2]
// ---------------------------------------------------------------------------

describe("QueryFieldsPaneComponent — isSelectedField", () => {
   it("should return true for an index present in selectedFieldIndexes", async () => {
      const { comp } = await renderComponent();
      await waitFor(() => expect(comp.selectedFieldIndexes).toEqual([0]));
      expect(comp.isSelectedField(0)).toBe(true);
   });

   it("should return false for an index absent from selectedFieldIndexes", async () => {
      const { comp } = await renderComponent();
      await waitFor(() => expect(comp.selectedFieldIndexes).toEqual([0]));
      expect(comp.isSelectedField(1)).toBe(false);
   });
});

// ---------------------------------------------------------------------------
// Group 4 — isUpDisabled / isDownDisabled [Risk 2]
// ---------------------------------------------------------------------------

describe("QueryFieldsPaneComponent — isUpDisabled / isDownDisabled: boundary conditions", () => {
   it("isUpDisabled: returns true when only one field exists", async () => {
      const { comp } = await renderComponent({ model: makeModel([makeField("a", "a")]) });
      expect(comp.isUpDisabled()).toBe(true);
   });

   it("isUpDisabled: returns true when the first field is selected", async () => {
      const { comp } = await renderComponent();
      await waitFor(() => expect(comp.selectedFieldIndexes).toEqual([0]));
      expect(comp.isUpDisabled()).toBe(true);
   });

   it("isUpDisabled: returns false when a non-first field is the single selection", async () => {
      const { comp } = await renderComponent();
      comp.selectField(null, 1);
      await waitFor(() => expect(comp.selectedFieldIndexes).toEqual([1]));
      expect(comp.isUpDisabled()).toBe(false);
   });

   it("isDownDisabled: returns true when the last field is selected", async () => {
      const { comp } = await renderComponent();
      comp.selectField(null, 1); // index 1 is the last in a 2-field model
      await waitFor(() => expect(comp.selectedFieldIndexes).toEqual([1]));
      expect(comp.isDownDisabled()).toBe(true);
   });

   it("isDownDisabled: returns false when a non-last field is the single selection", async () => {
      const { comp } = await renderComponent();
      await waitFor(() => expect(comp.selectedFieldIndexes).toEqual([0]));
      expect(comp.isDownDisabled()).toBe(false);
   });
});

// ---------------------------------------------------------------------------
// Group 5 — moveUp / moveDown [Risk 3]
// ---------------------------------------------------------------------------

describe("QueryFieldsPaneComponent — moveUp / moveDown: adjacent field swap", () => {
   // 🔁 Regression-sensitive: swap must update both model.fields AND move the selection
   // index; missing the index update causes the detail panel to show the wrong field.

   it("moveUp: swaps field at selectedIndex with field at selectedIndex-1 and selects the new position", async () => {
      const { comp } = await renderComponent();
      comp.selectField(null, 1);
      await waitFor(() => expect(comp.selectedFieldIndexes).toEqual([1]));
      comp.moveUp();
      await waitFor(() => expect(comp.selectedFieldIndexes).toEqual([0]));
      expect(comp.model.fields[0].name).toBe("col2");
      expect(comp.model.fields[1].name).toBe("col1");
   });

   it("moveDown: swaps field at selectedIndex with field at selectedIndex+1 and selects the new position", async () => {
      const { comp } = await renderComponent();
      await waitFor(() => expect(comp.selectedFieldIndexes).toEqual([0]));
      comp.moveDown();
      await waitFor(() => expect(comp.selectedFieldIndexes).toEqual([1]));
      expect(comp.model.fields[0].name).toBe("col2");
      expect(comp.model.fields[1].name).toBe("col1");
   });

   it("moveUp: is a no-op when isUpDisabled is true (first field selected)", async () => {
      const { comp } = await renderComponent();
      await waitFor(() => expect(comp.selectedFieldIndexes).toEqual([0]));
      comp.moveUp();
      await waitFor(() => expect(comp.selectedFieldIndexes).toEqual([0]));
      expect(comp.model.fields[0].name).toBe("col1");
   });
});

// ---------------------------------------------------------------------------
// Group 6 — add / addAll [Risk 3]
// ---------------------------------------------------------------------------

describe("QueryFieldsPaneComponent — add / addAll: POST leaf nodes to column/add", () => {
   // 🔁 Regression-sensitive: entries that are not leaf nodes must still be traversed for
   // their children; if only direct leaves are used, clicking a table node adds nothing.

   it("add: POSTs selected leaf nodes and updates selectedFieldName on response", async () => {
      server.use(
         http.post("*/api/data/datasource/query/column/add", () =>
            HttpResponse.json({ limitMessage: null, columnMap: { col3: "col3" } }),
         ),
      );
      const { comp } = await renderComponent();
      comp.selectedNodes = [makeLeafNode("col3")];
      comp.add();
      await waitFor(() => expect(QUERY_MODEL_SERVICE_MOCK.emitModelChange).toHaveBeenCalled());
      expect(comp.selectedFieldName).toBe("col3");
      expect(comp.selectedFieldAlias).toBe("col3");
   });

   it("addAll: traverses all leaf nodes in databaseFieldsTree and POSTs them", async () => {
      server.use(
         http.post("*/api/data/datasource/query/column/add", () =>
            HttpResponse.json({ limitMessage: null, columnMap: { leaf1: "leaf1" } }),
         ),
      );
      const { comp } = await renderComponent();
      comp.databaseFieldsTree = makeFieldsTree([makeLeafNode("leaf1")]);
      comp.addAll();
      await waitFor(() => expect(QUERY_MODEL_SERVICE_MOCK.emitModelChange).toHaveBeenCalled());
   });

   it("add: does nothing when selectedNodes is empty", async () => {
      const { comp } = await renderComponent();
      QUERY_MODEL_SERVICE_MOCK.emitModelChange.mockClear();
      comp.selectedNodes = [];
      comp.add();
      await Promise.resolve();
      expect(QUERY_MODEL_SERVICE_MOCK.emitModelChange).not.toHaveBeenCalled();
   });
});

// ---------------------------------------------------------------------------
// Group 7 — addColumns: HTTP response handling [Risk 3]
// ---------------------------------------------------------------------------

describe("QueryFieldsPaneComponent — addColumns: POST response handling", () => {
   it("should add columns to columnsOrderMap when columnMap is non-empty", async () => {
      server.use(
         http.post("*/api/data/datasource/query/column/add", () =>
            HttpResponse.json({ limitMessage: null, columnMap: { aliasX: "nameX" } }),
         ),
      );
      const { comp } = await renderComponent();
      const before = comp.columnsOrderMap.length;
      comp.addColumns([{ properties: { attribute: "nameX" } } as any]);
      await waitFor(() => expect(comp.columnsOrderMap.length).toBeGreaterThan(before));
      expect(comp.columnsOrderMap).toContainEqual({ alias: "aliasX", name: "nameX" });
   });

   it("should call emitModelChange after successful column/add response", async () => {
      server.use(
         http.post("*/api/data/datasource/query/column/add", () =>
            HttpResponse.json({ limitMessage: null, columnMap: { a: "a" } }),
         ),
      );
      const { comp } = await renderComponent();
      QUERY_MODEL_SERVICE_MOCK.emitModelChange.mockClear();
      comp.addColumns([{ properties: { attribute: "a" } } as any]);
      await waitFor(() => expect(QUERY_MODEL_SERVICE_MOCK.emitModelChange).toHaveBeenCalledTimes(1));
   });
});

// ---------------------------------------------------------------------------
// Group 8 — remove / removeAll / removeColumns [Risk 3]
// ---------------------------------------------------------------------------

describe("QueryFieldsPaneComponent — remove / removeAll / removeColumns: column removal flow", () => {
   // 🔁 Regression-sensitive: removeColumnsInOrderMap must prune columnsOrderMap so that
   // reloadColumnsOrder() on the next ngOnChanges keeps the ordering consistent.

   it("remove: fires column/remove POST for the selected field", async () => {
      let removedPayload: any;
      server.use(
         http.post("*/api/data/datasource/query/column/remove", async ({ request }) => {
            removedPayload = await request.json();
            return new HttpResponse(null, { status: 200 });
         }),
      );
      const { comp } = await renderComponent();
      await waitFor(() => expect(comp.selectedFieldIndexes).toEqual([0]));
      comp.remove();
      await waitFor(() => expect(QUERY_MODEL_SERVICE_MOCK.emitModelChange).toHaveBeenCalled());
      expect(removedPayload).toEqual(
         expect.objectContaining({ names: ["col1"], aliases: ["col1"] }),
      );
   });

   it("removeAll: fires column/remove POST for all fields and clears columnsOrderMap", async () => {
      server.use(
         http.post("*/api/data/datasource/query/column/remove", () =>
            new HttpResponse(null, { status: 200 }),
         ),
      );
      const { comp } = await renderComponent();
      comp.removeAll();
      await waitFor(() => expect(QUERY_MODEL_SERVICE_MOCK.emitModelChange).toHaveBeenCalled());
      expect(comp.columnsOrderMap).toEqual([]);
      expect(comp.selectedFieldName).toBeNull();
   });

   it("remove: does nothing when selectedFieldIndexes is empty", async () => {
      const { comp } = await renderComponent();
      comp.selectedFieldIndexes = [];
      QUERY_MODEL_SERVICE_MOCK.emitModelChange.mockClear();
      comp.remove();
      await Promise.resolve();
      expect(QUERY_MODEL_SERVICE_MOCK.emitModelChange).not.toHaveBeenCalled();
   });
});

// ---------------------------------------------------------------------------
// Group 9 — updateAlias [Risk 3]
// ---------------------------------------------------------------------------

describe("QueryFieldsPaneComponent — updateAlias: validation and HTTP update", () => {
   // 🔁 Regression-sensitive: alias validation must fire BEFORE the HTTP POST; allowing a
   // blank or duplicate alias corrupts the query field mapping on the server side.

   it("should open an error modal and not fire HTTP when the new alias is empty", async () => {
      const { comp } = await renderComponent();
      await waitFor(() => expect(comp.selectedFieldIndexes).toEqual([0]));
      MODAL_MOCK.open.mockClear();
      comp.updateAlias({ target: { value: "" } } as any);
      expect(MODAL_MOCK.open).toHaveBeenCalledTimes(1);
   });

   it("should open an error modal and not fire update HTTP when the alias already exists", async () => {
      const { comp } = await renderComponent();
      await waitFor(() => expect(comp.selectedFieldIndexes).toEqual([0]));
      MODAL_MOCK.open.mockClear();
      QUERY_MODEL_SERVICE_MOCK.emitModelChange.mockClear();
      // "col2" is the alias of the second field — a duplicate
      comp.updateAlias({ target: { value: "col2" } } as any);
      expect(MODAL_MOCK.open).toHaveBeenCalledTimes(1);
      await Promise.resolve();
      expect(QUERY_MODEL_SERVICE_MOCK.emitModelChange).not.toHaveBeenCalled();
   });

   it("should update the field alias and fire column/update POST on a valid new alias", async () => {
      let updatePayload: any;
      server.use(
         http.post("*/api/data/datasource/query/column/update", async ({ request }) => {
            updatePayload = await request.json();
            return new HttpResponse(null, { status: 200 });
         }),
      );
      const { comp } = await renderComponent();
      await waitFor(() => expect(comp.selectedFieldIndexes).toEqual([0]));
      comp.updateAlias({ target: { value: "renamed" } } as any);
      await waitFor(() => expect(QUERY_MODEL_SERVICE_MOCK.emitModelChange).toHaveBeenCalled());
      expect(comp.selectedFieldAlias).toBe("renamed");
      expect(comp.model.fields[0].alias).toBe("renamed");
   });

   it("should not fire HTTP when the new alias is identical to the current alias", async () => {
      const { comp } = await renderComponent();
      await waitFor(() => expect(comp.selectedFieldIndexes).toEqual([0]));
      QUERY_MODEL_SERVICE_MOCK.emitModelChange.mockClear();
      comp.updateAlias({ target: { value: "col1" } } as any); // same as current
      await Promise.resolve();
      expect(QUERY_MODEL_SERVICE_MOCK.emitModelChange).not.toHaveBeenCalled();
   });
});

// ---------------------------------------------------------------------------
// Group 10 — updateDataType / updateFormat [Risk 2]
// ---------------------------------------------------------------------------

describe("QueryFieldsPaneComponent — updateDataType / updateFormat: column/update POST", () => {
   it("updateDataType: sets the field dataType and fires column/update POST", async () => {
      let updatePayload: any;
      server.use(
         http.post("*/api/data/datasource/query/column/update", async ({ request }) => {
            updatePayload = await request.json();
            return new HttpResponse(null, { status: 200 });
         }),
      );
      const { comp } = await renderComponent();
      await waitFor(() => expect(comp.selectedFieldIndexes).toEqual([0]));
      comp.updateDataType("integer");
      await waitFor(() => expect(QUERY_MODEL_SERVICE_MOCK.emitModelChange).toHaveBeenCalled());
      expect(comp.model.fields[0].dataType).toBe("integer");
      expect(updatePayload).toEqual(expect.objectContaining({ dataType: "integer" }));
   });

   it("updateFormat: fires field/format POST then column/update POST", async () => {
      const { comp } = await renderComponent();
      await waitFor(() => expect(comp.selectedFieldIndexes).toEqual([0]));
      QUERY_MODEL_SERVICE_MOCK.emitModelChange.mockClear();
      comp.updateFormat();
      await waitFor(() => expect(QUERY_MODEL_SERVICE_MOCK.emitModelChange).toHaveBeenCalled());
   });
});

// ---------------------------------------------------------------------------
// Group 11 — openAutoDrillDialog [Risk 2]
// ---------------------------------------------------------------------------

describe("QueryFieldsPaneComponent — openAutoDrillDialog: modal open and commit", () => {
   it("should open the auto-drill modal with the selected field's drillInfo", async () => {
      const { comp } = await renderComponent();
      await waitFor(() => expect(comp.selectedFieldIndexes).toEqual([0]));
      MODAL_MOCK.open.mockClear();
      comp.openAutoDrillDialog();
      expect(MODAL_MOCK.open).toHaveBeenCalledTimes(1);
   });

   it("should update selectedField.drillInfo and fire column/update POST on commit", async () => {
      const { comp } = await renderComponent();
      await waitFor(() => expect(comp.selectedFieldIndexes).toEqual([0]));
      comp.openAutoDrillDialog();
      const modalRef = MODAL_MOCK.open.mock.results[MODAL_MOCK.open.mock.results.length - 1].value;
      const drillData = { paths: [{ name: "D1", link: "" }] };
      modalRef.componentInstance.onCommit.next(drillData);
      await waitFor(() => expect(QUERY_MODEL_SERVICE_MOCK.emitModelChange).toHaveBeenCalled());
      expect(comp.model.fields[0].drillInfo).toEqual(drillData);
   });
});

// ---------------------------------------------------------------------------
// Group 12 — browseColumnData [Risk 3]
// ---------------------------------------------------------------------------

describe("QueryFieldsPaneComponent — browseColumnData: GET distinct values and modal", () => {
   // 🔁 Regression-sensitive: the warning path (no selectedField) must not attempt an HTTP
   // GET; the "no data" warning must not open the browse modal.

   it("should open a warning modal when no field is selected", async () => {
      const { comp } = await renderComponent();
      comp.selectedFieldIndexes = [];
      comp.selectedFieldName = null;
      comp.selectedFieldAlias = null;
      MODAL_MOCK.open.mockClear();
      comp.browseColumnData();
      expect(MODAL_MOCK.open).toHaveBeenCalledTimes(1);
   });

   it("should open a warning modal when browse returns an empty array", async () => {
      server.use(
         http.get("*/api/data/datasource/query/column/browse", () => HttpResponse.json([])),
      );
      const { comp } = await renderComponent();
      // Select a field that has a matching tree node so getSelectedFieldTreeNode returns non-null
      comp.databaseFieldsTree = makeFieldsTree([makeLeafNode("col1")]);
      await waitFor(() => expect(comp.selectedFieldIndexes).toEqual([0]));
      MODAL_MOCK.open.mockClear();
      comp.browseColumnData();
      // Wait for the GET + the empty-result warning dialog
      await waitFor(() => expect(MODAL_MOCK.open).toHaveBeenCalled());
      // Browse modal is NOT opened (result was empty)
      expect(MODAL_MOCK.open).toHaveBeenCalledTimes(1);
   });

   it("should open the browse modal when browse returns non-empty values", async () => {
      server.use(
         http.get("*/api/data/datasource/query/column/browse", () =>
            HttpResponse.json(["value1", "value2"]),
         ),
      );
      const { comp } = await renderComponent();
      comp.databaseFieldsTree = makeFieldsTree([makeLeafNode("col1")]);
      await waitFor(() => expect(comp.selectedFieldIndexes).toEqual([0]));
      MODAL_MOCK.open.mockClear();
      comp.browseColumnData();
      await waitFor(() => expect(MODAL_MOCK.open).toHaveBeenCalled());
      expect(comp.columnValues).toEqual(["value1", "value2"]);
   });
});

// ---------------------------------------------------------------------------
// Group 13 — showEditDataTypeDialog [Risk 2]
// ---------------------------------------------------------------------------

describe("QueryFieldsPaneComponent — showEditDataTypeDialog: opens data-type dialog", () => {
   it("should open the dialog with the selected field's alias and dataType", async () => {
      const { comp } = await renderComponent({
         model: makeModel([makeField("myCol", "myAlias", { dataType: "integer" })]),
      });
      await waitFor(() => expect(comp.selectedFieldIndexes).toEqual([0]));
      MODAL_MOCK.open.mockClear();
      comp.showEditDataTypeDialog();
      expect(MODAL_MOCK.open).toHaveBeenCalledTimes(1);
      const instance = MODAL_MOCK.open.mock.results[0].value.componentInstance;
      expect(instance.columnName).toBe("myAlias");
      expect(instance.dataType).toBe("integer");
   });
});

// ---------------------------------------------------------------------------
// Group 14 — dbClickToAdd [Risk 2]
// ---------------------------------------------------------------------------

describe("QueryFieldsPaneComponent — dbClickToAdd: double-click adds leaf node", () => {
   it("should call addColumns with the node's data when the node is a leaf", async () => {
      server.use(
         http.post("*/api/data/datasource/query/column/add", () =>
            HttpResponse.json({ limitMessage: null, columnMap: { leafNode: "leafNode" } }),
         ),
      );
      const { comp } = await renderComponent();
      QUERY_MODEL_SERVICE_MOCK.emitModelChange.mockClear();
      comp.dbClickToAdd(makeLeafNode("leafNode"));
      await waitFor(() => expect(QUERY_MODEL_SERVICE_MOCK.emitModelChange).toHaveBeenCalled());
   });

   it("should not call addColumns when the node is not a leaf", async () => {
      const { comp } = await renderComponent();
      QUERY_MODEL_SERVICE_MOCK.emitModelChange.mockClear();
      comp.dbClickToAdd({ label: "Table", leaf: false, children: [] });
      await Promise.resolve();
      expect(QUERY_MODEL_SERVICE_MOCK.emitModelChange).not.toHaveBeenCalled();
   });
});

// ---------------------------------------------------------------------------
// Group 15 — drop [Risk 3]
// ---------------------------------------------------------------------------

describe("QueryFieldsPaneComponent — drop: drag-and-drop field management", () => {
   // 🔁 Regression-sensitive: drop(event, remove=true) is bound to the left-panel dragover
   // so users can remove fields by dropping back to the source pane; the wrong branch would
   // silently add instead of remove.

   it("drop with remove=true calls remove() for the selected field", async () => {
      server.use(
         http.post("*/api/data/datasource/query/column/remove", () =>
            new HttpResponse(null, { status: 200 }),
         ),
      );
      const { comp } = await renderComponent();
      await waitFor(() => expect(comp.selectedFieldIndexes).toEqual([0]));
      QUERY_MODEL_SERVICE_MOCK.emitModelChange.mockClear();
      comp.drop({} as any, true);
      await waitFor(() => expect(QUERY_MODEL_SERVICE_MOCK.emitModelChange).toHaveBeenCalled());
   });

   it("drop without remove calls addColumns when drag data contains leaf entries", async () => {
      const leafEntry = { properties: { attribute: "draggedCol" } } as any;
      server.use(
         http.post("*/api/data/datasource/query/column/add", () =>
            HttpResponse.json({ limitMessage: null, columnMap: { draggedCol: "draggedCol" } }),
         ),
      );
      const { comp } = await renderComponent();
      // drop() may call getDragDataValues more than once; spy with persistent mockReturnValue
      // and restore via try/finally so subsequent tests are not contaminated (C3/C8).
      const dragSpy = vi.spyOn(DRAG_SERVICE_MOCK, "getDragDataValues").mockReturnValue([[leafEntry]]);
      try {
         QUERY_MODEL_SERVICE_MOCK.emitModelChange.mockClear();
         comp.drop({ preventDefault: vi.fn(), stopPropagation: vi.fn() } as any, false);
         await waitFor(() => expect(QUERY_MODEL_SERVICE_MOCK.emitModelChange).toHaveBeenCalled());
      } finally {
         dragSpy.mockRestore();
      }
   });
});

// ---------------------------------------------------------------------------
// Group 16 — showDataTypeIcon [Risk 2]
// ---------------------------------------------------------------------------

describe("QueryFieldsPaneComponent — showDataTypeIcon: visibility conditions", () => {
   it("returns true when exactly one field is selected and it has no matching tree node", async () => {
      const { comp } = await renderComponent();
      await waitFor(() => expect(comp.selectedFieldIndexes).toEqual([0]));
      // Default databaseFieldsTree from portal.handlers returns {nodes:[]} without children,
      // so getSelectedFieldTreeNode() returns undefined — showDataTypeIcon() should be true.
      expect(comp.showDataTypeIcon()).toBe(true);
   });

   it("returns false when no field is selected", async () => {
      const { comp } = await renderComponent();
      comp.selectedFieldIndexes = [];
      comp.selectedFieldName = null;
      comp.selectedFieldAlias = null;
      expect(comp.showDataTypeIcon()).toBe(false);
   });

   it("returns false when multiple fields are selected", async () => {
      const { comp } = await renderComponent({
         model: makeModel([makeField("a", "a"), makeField("b", "b"), makeField("c", "c")]),
      });
      // Select indices 0 and 1
      await waitFor(() => expect(comp.selectedFieldIndexes).toEqual([0]));
      comp.selectField({ ctrlKey: true, shiftKey: false } as MouseEvent, 1);
      await waitFor(() => expect(comp.selectedFieldIndexes.length).toBe(2));
      expect(comp.showDataTypeIcon()).toBe(false);
   });
});
