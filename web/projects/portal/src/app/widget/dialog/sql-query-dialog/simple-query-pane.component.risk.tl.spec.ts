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
 * SimpleQueryPaneComponent - Pass 2: Async Risk
 *
 * Risk-first coverage:
 *   Group 1 [Risk 3] - editConditions columnCache subscription ordering
 *   Group 2 [Risk 3] - droppedIntoColumnList/getTableColumns observable completion contract
 *   Group 3 [Risk 3] - updateQueryTab HTTP success, validation error, and network error branches
 *
 * Fixed bugs:
 *   Bug #75600 - droppedIntoColumnList used to store a whole table pair once for each
 *      non-duplicate column, so addColumns() received duplicate pairs for multi-column tables
 *      (final model.columns could still look correct because addColumns reorder logic masked
 *      it). Fixed by pushing the tablePair at most once per dropped table.
 *
 * Out of scope this pass: pure label and branch-matrix helpers.
 */

import { of, Subject, throwError } from "rxjs";
import { AssetEntry } from "../../../../../../shared/data/asset-entry";
import { AssetType } from "../../../../../../shared/data/asset-type";
import { ComponentTool } from "../../../common/util/component-tool";
import {
   createSimpleQueryPane,
   makeBasicModel,
   makeColumnEntry,
   makeJoin,
   makeTableEntry
} from "./simple-query-pane.component.test-helpers";

afterEach(() => vi.restoreAllMocks());

describe("SimpleQueryPaneComponent - condition field ordering [Group 1, Risk 3]", () => {
   it("should preserve table order when condition columns resolve out of order", () => {
      const first = new Subject<AssetEntry[]>();
      const second = new Subject<AssetEntry[]>();
      const { comp } = createSimpleQueryPane({
         modalResult: new Promise(() => {})
      });
      comp.columnCache = {
         Orders: first.asObservable(),
         Customers: second.asObservable()
      };

      comp.editConditions();
      second.next([makeColumnEntry("Customers", "name")]);

      // forkJoin waits for every cache entry to emit before assigning conditionFields,
      // so resolving only the second (Customers) source must not populate it yet.
      expect(comp.conditionFields).toEqual([]);

      first.next([makeColumnEntry("Orders", "id"), makeColumnEntry("Orders", "state")]);

      // Once all sources have resolved, fields are grouped by cache-key (table) order
      // rather than by emission order.
      expect(comp.conditionFields.map(field => field.name)).toEqual([
         "Orders.id",
         "Orders.state",
         "Customers.name"
      ]);
      // VPMConditionDialogModel's constructor alphabetically sorts fields, independent of
      // the table/emission order above.
      expect(comp.conditionDialogModel.fields.map(field => field.name)).toEqual([
         "Customers.name",
         "Orders.id",
         "Orders.state"
      ]);
   });

   it("should still open the dialog with partial fields and surface an error when a table's columns fail to load", () => {
      vi.spyOn(ComponentTool, "showMessageDialog").mockResolvedValue(null);
      const { comp, modal } = createSimpleQueryPane();
      comp.columnCache = {
         Orders: throwError(() => new Error("network")),
         Customers: of([makeColumnEntry("Customers", "name")])
      };

      comp.editConditions();

      expect(comp.conditionsLoading).toBe(false);
      expect(comp.conditionFields.map(field => field.name)).toEqual(["Customers.name"]);
      expect(modal.open).toHaveBeenCalled();
      expect(ComponentTool.showMessageDialog).toHaveBeenCalledWith(
         expect.anything(),
         "_#(js:Error)",
         "_#(js:common.network.error)"
      );
   });
});

describe("SimpleQueryPaneComponent - hasUnjoinedTables cross-join hint [Group 1, Risk 3]", () => {
   it("should return false when there is one or fewer tables", () => {
      const { comp } = createSimpleQueryPane();
      comp.numTables = 1;
      comp.model.tables = { Orders: {} as any };

      expect(comp.hasUnjoinedTables()).toBe(false);
   });

   it("should return false when every table is referenced by some join", () => {
      const { comp } = createSimpleQueryPane({
         model: makeBasicModel({ joins: [makeJoin({ table1: "Orders", table2: "Customers" })] })
      });
      comp.numTables = 2;
      comp.model.tables = { Orders: {} as any, Customers: {} as any };

      expect(comp.hasUnjoinedTables()).toBe(false);
   });

   it("should return true when a table is never referenced by any join, even though the join count alone looks sufficient", () => {
      // Three tables, two joins - but both joins are between the same Orders/Customers pair,
      // so Products is never referenced and remains an unjoined cross-join risk. A plain
      // `joins.length < numTables - 1` count check would miss this case.
      const { comp } = createSimpleQueryPane({
         model: makeBasicModel({
            joins: [
               makeJoin({ table1: "Orders", table2: "Customers", column1: "id", column2: "id" }),
               makeJoin({ table1: "Orders", table2: "Customers", column1: "id2", column2: "id2" })
            ]
         })
      });
      comp.numTables = 3;
      comp.model.tables = { Orders: {} as any, Customers: {} as any, Products: {} as any };

      expect(comp.hasUnjoinedTables()).toBe(true);
   });
});

describe("SimpleQueryPaneComponent - dropped column async flow [Group 2, Risk 3]", () => {
   it("should combine standalone columns with table columns after table observable completes", () => {
      const table = makeTableEntry("Orders");
      const duplicate = makeColumnEntry("Orders", "state");
      const tableOnly = makeColumnEntry("Orders", "id");
      const { comp, controller, model } = createSimpleQueryPane({
         model: makeBasicModel({ columns: [] })
      });
      controller.getTableColumns.mockReturnValue(of([duplicate, tableOnly]));
      comp.tree = {
         getParentNodeByData: vi.fn(() => ({
            data: table,
            children: [{ data: duplicate }]
         }))
      } as any;

      comp.droppedIntoColumnList([{
         [AssetType[AssetType.PHYSICAL_TABLE]]: JSON.stringify([table]),
         [AssetType[AssetType.PHYSICAL_COLUMN]]: JSON.stringify([duplicate])
      }, 0]);

      expect(model.columns.map(column => column.name)).toEqual(["Orders.state", "Orders.id"]);
      expect(model.tables["Orders"]).toEqual(table);
      expect(comp.columnCache["Orders"]).toBeTruthy();
   });

   it("should emit table column pairs in input order and cache each table observable", () => {
      const orders = makeTableEntry("Orders");
      const customers = makeTableEntry("Customers");
      const { comp, controller } = createSimpleQueryPane();
      controller.getTableColumns.mockImplementation((entry: AssetEntry) => {
         return of([makeColumnEntry(entry.properties["source"], "id")]);
      });
      const emitted: string[] = [];

      (comp as any).getTableColumns([orders, customers]).subscribe((pair: any) => {
         emitted.push(pair.table.properties["source"]);
      });

      expect(emitted).toEqual(["Orders", "Customers"]);
      expect(Object.keys(comp.columnCache)).toEqual(["Orders", "Customers"]);
   });

   // Bug #75600 (fixed): tableColumns.push(tablePair) used to run inside
   // tablePair.columns.forEach, so a table with multiple non-duplicate columns was queued once
   // per column. addColumns() then received duplicate pairs (model.columns could still look
   // correct due to reorder logic in addColumns). tableColumns.push(tablePair) now runs once
   // per dropped table, outside the per-column forEach.
   it("should pass each table column once to addColumns when dropping a whole table with multiple columns", () => {
      const table = makeTableEntry("Orders");
      const { comp, controller } = createSimpleQueryPane({
         model: makeBasicModel({ columns: [] })
      });
      controller.getTableColumns.mockReturnValue(of([
         makeColumnEntry("Orders", "state"),
         makeColumnEntry("Orders", "id")
      ]));
      const addColumnsSpy = vi.spyOn(comp, "addColumns");

      comp.droppedIntoColumnList([{
         [AssetType[AssetType.PHYSICAL_TABLE]]: JSON.stringify([table])
      }, 0]);

      expect(addColumnsSpy.mock.calls[0][0]).toHaveLength(2);
   });
});

describe("SimpleQueryPaneComponent - preview tab HTTP flow [Group 3, Risk 3]", () => {
   it("should move to preview tab only when update query succeeds", () => {
      const { comp, http, model } = createSimpleQueryPane();
      const event = { nextId: comp.previewTab, preventDefault: vi.fn() };

      http.post.mockReturnValue(of({}));
      comp.updateQueryTab(event as any);

      expect(event.preventDefault).toHaveBeenCalled();
      expect(http.post).toHaveBeenCalledWith(
         "../api/composer/ws/sql-query-dialog/query/update",
         model,
         expect.objectContaining({ params: expect.anything() })
      );
      expect(comp.defaultTab).toBe(comp.previewTab);
   });

   it("should keep the current tab and show server validation errors", () => {
      const { comp, http } = createSimpleQueryPane();
      vi.spyOn(ComponentTool, "showMessageDialog").mockResolvedValue(null);
      http.post.mockReturnValue(of({ errorMsg: "bad query" }));

      comp.updateQueryTab({ nextId: comp.previewTab, preventDefault: vi.fn() } as any);

      expect(comp.defaultTab).toBe(comp.editTab);
      expect(ComponentTool.showMessageDialog).toHaveBeenCalledWith(
         expect.anything(),
         "_#(js:Error)",
         "bad query"
      );
   });

   it("should show network error message when update query fails", () => {
      const { comp, http } = createSimpleQueryPane();
      vi.spyOn(ComponentTool, "showMessageDialog").mockResolvedValue(null);
      http.post.mockReturnValue(throwError(() => new Error("network")));

      comp.updateQueryTab({ nextId: comp.previewTab, preventDefault: vi.fn() } as any);

      expect(comp.defaultTab).toBe(comp.editTab);
      expect(ComponentTool.showMessageDialog).toHaveBeenCalledWith(
         expect.anything(),
         "_#(js:Error)",
         "_#(js:common.network.error)"
      );
   });
});
