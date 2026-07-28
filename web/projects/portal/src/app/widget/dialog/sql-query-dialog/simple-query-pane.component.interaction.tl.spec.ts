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
 * SimpleQueryPaneComponent - Pass 1: Interaction
 *
 * Risk-first coverage:
 *   Group 1 [Risk 3] - model setter, list mutations, and SQL refresh output contract
 *   Group 2 [Risk 3] - join and condition dialog result flows
 *   Group 3 [Risk 2] - tree expansion, direct SQL editing, parse updates, tab restoration
 *
 * Out of scope this pass: observable ordering races and addColumns branch matrix.
 */

import { of } from "rxjs";
import { AssetType } from "../../../../../../shared/data/asset-type";
import { ComponentTool } from "../../../common/util/component-tool";
import { syncReject, syncResolve } from "../../../../testing/tl-async.util";
import { makeJoin } from "./simple-query-pane.component.test-helpers";
import {
   createSimpleQueryPane,
   makeBasicModel,
   makeColumnEntry,
   makeTableEntry
} from "./simple-query-pane.component.test-helpers";

afterEach(() => vi.restoreAllMocks());

describe("SimpleQueryPaneComponent - model and list interactions [Group 1, Risk 3]", () => {
   it("should initialize table count and column cache from the model setter", () => {
      const orders = makeTableEntry("Orders");
      const customers = makeTableEntry("Customers");
      const { comp, controller } = createSimpleQueryPane({
         model: makeBasicModel({
            tables: {
               Orders: orders,
               Customers: customers
            }
         })
      });

      expect(comp.oldSqlString).toBe("select * from Orders");
      expect(comp.numTables).toBe(2);
      expect(Object.keys(comp.columnCache)).toEqual(["Orders", "Customers"]);
      expect(controller.getTableColumns).toHaveBeenCalledWith(orders);
      expect(controller.getTableColumns).toHaveBeenCalledWith(customers);

      comp.model = makeBasicModel({ tables: {} });

      expect(comp.numTables).toBe(0);
      expect(comp.columnCache).toEqual({});
   });

   it("should update columns joins and deleted joins before refreshing SQL", () => {
      const { comp, model, controller } = createSimpleQueryPane();
      const getSQLSpy = vi.spyOn(comp, "getSQLString");
      const columns = [{ name: "Orders.state" }];
      const joins = [makeJoin(), makeJoin({ table2: "Regions" })];
      const remainingJoin = joins[1];

      comp.columnsChange(columns);
      comp.joinsChange(joins);
      comp.deleteJoin(0);

      expect(model.columns).toBe(columns);
      expect(model.joins).toEqual([remainingJoin]);
      expect(getSQLSpy).toHaveBeenCalledTimes(3);

      getSQLSpy.mockRestore();
      comp.getSQLString();
      expect(controller.getSQLString).toHaveBeenCalledWith(comp.queryModel);
      expect(model.sqlString).toBe("select generated");
   });

   it("should emit processing state around generated SQL refresh", () => {
      const { comp, model } = createSimpleQueryPane({ sqlString: "select id from Orders" });
      const emitSpy = vi.spyOn(comp.onProcessing, "emit");

      comp.getSQLString();

      expect(emitSpy).toHaveBeenNthCalledWith(1, true);
      expect(emitSpy).toHaveBeenNthCalledWith(2, false);
      expect(model.sqlString).toBe("select id from Orders");
   });
});

describe("SimpleQueryPaneComponent - dialog interactions [Group 2, Risk 3]", () => {
   it("should append a new join from the join dialog result", () => {
      const result = makeJoin({ table2: "Regions", column2: "region_id" });
      const { comp, model, modal } = createSimpleQueryPane({
         model: makeBasicModel({ joins: null }),
         modalResult: syncResolve(result)
      });
      const getSQLSpy = vi.spyOn(comp, "getSQLString");

      comp.newJoin();

      expect(modal.open).toHaveBeenCalledWith((comp as any).joinDialog, { size: "lg", backdrop: false });
      expect(model.joins).toEqual([result]);
      expect(getSQLSpy).toHaveBeenCalled();
   });

   it("should replace an edited join from the join dialog result", () => {
      const original = makeJoin();
      const replacement = makeJoin({ operator: "<>" });
      const { comp, model } = createSimpleQueryPane({
         model: makeBasicModel({ joins: [original] }),
         modalResult: syncResolve(replacement)
      });
      const getSQLSpy = vi.spyOn(comp, "getSQLString");

      comp.editJoin(original);

      expect(comp.selectedJoin).not.toBe(original);
      expect(comp.selectedJoin).toEqual(original);
      expect(model.joins).toEqual([replacement]);
      expect(getSQLSpy).toHaveBeenCalled();
   });

   it("should commit or restore conditions based on the condition dialog result", () => {
      const committed = [{ type: "clause", level: 0 }];
      const { comp, model } = createSimpleQueryPane({
         model: makeBasicModel({ conditionList: [{ type: "old", level: 0 } as any] }),
         modalResult: syncResolve(committed)
      });
      const getSQLSpy = vi.spyOn(comp, "getSQLString");

      comp.editConditions();

      expect(model.conditionList).toBe(committed as any);
      expect(getSQLSpy).toHaveBeenCalled();

      const rejected = createSimpleQueryPane({
         model: makeBasicModel({ conditionList: [{ type: "keep", level: 0 } as any] }),
         modalResult: syncReject("cancel")
      });
      rejected.comp.editConditions();

      expect(rejected.model.conditionList).toEqual([{ type: "keep", level: 0 }]);
   });
});

describe("SimpleQueryPaneComponent - tree SQL and tabs [Group 3, Risk 2]", () => {
   it("should expand tree nodes with data and ignore empty nodes", () => {
      const { comp, controller } = createSimpleQueryPane();
      const empty = { label: "root", data: null, children: null };
      const node = { label: "Orders", data: makeTableEntry("Orders"), children: null };

      comp.nodeExpanded(empty as any);
      expect(controller.getDataSourceTree).not.toHaveBeenCalled();

      controller.getDataSourceTree.mockReturnValue(of({ children: [{ label: "id" }] }));
      comp.nodeExpanded(node as any);

      expect(controller.getDataSourceTree).toHaveBeenCalledWith(node.data, true);
      expect(node.children).toEqual([{ label: "id" }]);
   });

   it("should parse SQL and mark text edits as parse-init", () => {
      const { comp, model, controller } = createSimpleQueryPane({
         parseResult: "_#(js:designer.qb.parseFailed)"
      });

      comp.textChanged();
      expect(model.sqlParseResult).toBe("_#(js:designer.qb.parseInit)");

      comp.getSqlParseResult();
      expect(controller.getSqlParseResult).toHaveBeenCalledWith(model.sqlString);
      expect(model.sqlParseResult).toBe("_#(js:designer.qb.parseFailed)");
   });

   it("should enable direct SQL editing only after confirm yes", () => {
      const { comp, model } = createSimpleQueryPane();
      vi.spyOn(ComponentTool, "showConfirmDialog").mockImplementation(() => syncResolve("no"));

      comp.editSQLDirectly();
      expect(model.sqlEdited).toBe(false);

      vi.spyOn(ComponentTool, "showConfirmDialog").mockImplementation(() => syncResolve("yes"));
      comp.editSQLDirectly();

      expect(model.sqlEdited).toBe(true);
   });

   it("should insert clicked tables and columns into direct SQL at the caret", () => {
      const { comp, model } = createSimpleQueryPane({
         model: makeBasicModel({ sqlEdited: true, sqlString: "select  from dual" })
      });
      const textarea = document.createElement("textarea");
      textarea.value = "select  from dual";
      textarea.selectionStart = 7;
      textarea.selectionEnd = 7;
      vi.spyOn(textarea, "focus").mockImplementation(() => {});
      (comp as any).sqlTextArea = { nativeElement: textarea };

      comp.nodeClicked({ label: "Orders", data: makeTableEntry("Orders") } as any);
      expect(textarea.value).toBe("select Orders from dual");

      textarea.selectionStart = 14;
      textarea.selectionEnd = 14;
      comp.nodeClicked({
         label: "state",
         data: makeColumnEntry("Orders", "state")
      } as any);

      expect(model.sqlString).toBe("select Orders Orders.statefrom dual");
      expect(textarea.focus).toHaveBeenCalled();

      comp.nodeClicked({
         label: "folder",
         data: { type: AssetType.FOLDER }
      } as any);
      expect(model.sqlString).toBe("select Orders Orders.statefrom dual");
   });

   it("should switch non-preview tabs and restore the previous tab", () => {
      const { comp } = createSimpleQueryPane();
      const event = {
         nextId: "custom-tab",
         preventDefault: vi.fn()
      };

      comp.updateQueryTab(event as any);
      expect(event.preventDefault).toHaveBeenCalled();
      expect(comp.defaultTab).toBe("custom-tab");

      comp.defaultTab = comp.previewTab;
      comp.goBackToPreviousTab();
      expect(comp.defaultTab).toBe("custom-tab");
   });
});
