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
 * SimpleQueryPaneComponent - Pass 3: Display
 *
 * Risk-first coverage:
 *   Group 1 [Risk 3] - addColumns insertion/reorder/max-count branches
 *   Group 2 [Risk 2] - destructive table cleanup and condition deletion guards
 *   Group 3 [Risk 1] - pure label, parse, table-count, support, and condition-model helpers
 *
 * Out of scope this pass: dialog promise and HTTP subscribe flows.
 */

import { ComponentTool } from "../../../common/util/component-tool";
import { ClauseValueTypes } from "../../../portal/data/model/datasources/database/vpm/condition/clause/clause-value-types";
import {
   createSimpleQueryPane,
   makeBasicModel,
   makeColumnEntry,
   makeJoin,
   makeTableEntry
} from "./simple-query-pane.component.test-helpers";

afterEach(() => vi.restoreAllMocks());

describe("SimpleQueryPaneComponent - addColumns branch matrix [Group 1, Risk 3]", () => {
   it("should insert new columns and add their source table", () => {
      const table = makeTableEntry("Orders");
      const { comp, model } = createSimpleQueryPane({
         model: makeBasicModel({ columns: [] })
      });
      const getSQLSpy = vi.spyOn(comp, "getSQLString");

      comp.addColumns([{ columnEntry: makeColumnEntry("Orders", "state"), parentEntry: table }], 0);

      expect(model.columns).toEqual([{ name: "Orders.state" }]);
      expect(model.tables["Orders"]).toBe(table);
      expect(comp.numTables).toBe(1);
      expect(getSQLSpy).toHaveBeenCalled();
   });

   it("should reorder existing columns for oldIndex less than greater than and equal index", () => {
      const table = makeTableEntry("Orders");
      const { comp, model } = createSimpleQueryPane({
         model: makeBasicModel({
            columns: [
               { name: "Orders.a" },
               { name: "Orders.b" },
               { name: "Orders.c" }
            ]
         })
      });

      comp.addColumns([{ columnEntry: makeColumnEntry("Orders", "a"), parentEntry: table }], 2);
      expect(model.columns.map(column => column.name)).toEqual(["Orders.b", "Orders.a", "Orders.c"]);

      comp.addColumns([{ columnEntry: makeColumnEntry("Orders", "c"), parentEntry: table }], 0);
      expect(model.columns.map(column => column.name)).toEqual(["Orders.c", "Orders.b", "Orders.a"]);

      comp.addColumns([{ columnEntry: makeColumnEntry("Orders", "b"), parentEntry: table }], 1);
      expect(model.columns.map(column => column.name)).toEqual(["Orders.c", "Orders.b", "Orders.a"]);
   });

   it("should show max-count confirm and skip insertion when the limit is reached", () => {
      const { comp, model } = createSimpleQueryPane({
         model: makeBasicModel({
            columns: [{ name: "Orders.a" }],
            maxColumnCount: 1
         })
      });
      const confirmSpy = vi.spyOn(ComponentTool, "showConfirmDialog").mockResolvedValue("ok");

      comp.addColumns([
         { columnEntry: makeColumnEntry("Orders", "b"), parentEntry: makeTableEntry("Orders") }
      ], 1);

      expect(model.columns).toEqual([{ name: "Orders.a" }]);
      expect(confirmSpy).toHaveBeenCalledWith(
         expect.anything(),
         "_#(js:Confirm)",
         "_#(js:common.oganization.colMaxCount)_*1"
      );
   });
});

describe("SimpleQueryPaneComponent - delete column cleanup [Group 2, Risk 2]", () => {
   it("should only remove the selected column when the table still has another column", () => {
      const { comp, model } = createSimpleQueryPane({
         model: makeBasicModel({
            tables: { Orders: makeTableEntry("Orders") },
            columns: [{ name: "Orders.a" }, { name: "Orders.b" }],
            joins: [makeJoin()],
            conditionList: [{ type: "clause", level: 0 } as any]
         })
      });

      comp.deleteColumn(0);

      expect(model.columns).toEqual([{ name: "Orders.b" }]);
      expect(model.tables["Orders"]).toBeTruthy();
      expect(model.joins).toHaveLength(1);
      expect(model.conditionList).toHaveLength(1);
   });

   it("should remove joins conditions table cache and table count when the last table column is deleted", () => {
      const { comp, model } = createSimpleQueryPane({
         model: makeBasicModel({
            tables: {
               Orders: makeTableEntry("Orders"),
               Customers: makeTableEntry("Customers")
            },
            columns: [{ name: "Orders.a" }, { name: "Customers.id" }],
            joins: [makeJoin(), makeJoin({ table1: "Customers", table2: "Regions" })],
            conditionList: [
               makeClause("Orders.a"),
               { type: "and", level: 0 } as any,
               makeClause("Customers.id")
            ]
         })
      });
      comp.columnCache = {
         Orders: null,
         Customers: null
      };

      comp.deleteColumn(0);

      expect(model.columns).toEqual([{ name: "Customers.id" }]);
      expect(model.tables["Orders"]).toBeUndefined();
      expect(comp.columnCache["Orders"]).toBeUndefined();
      expect(model.joins).toEqual([makeJoin({ table1: "Customers", table2: "Regions" })]);
      expect(model.conditionList).toEqual([makeClause("Customers.id")]);
      expect(comp.numTables).toBe(1);
   });
});

describe("SimpleQueryPaneComponent - pure helpers [Group 3, Risk 1]", () => {
   it("should render join and column labels", () => {
      const { comp } = createSimpleQueryPane();

      expect(comp.columnToString({ name: "Orders.state" })).toBe("Orders.state");
      expect(comp.joinToString(makeJoin({
         table1: "A",
         column1: "id",
         all1: true,
         operator: "=",
         all2: true,
         table2: "B",
         column2: "aid"
      }))).toBe("A.id *=* B.aid");
   });

   it("should expose parse table and full-outer-join state helpers", () => {
      const { comp, model } = createSimpleQueryPane({
         model: makeBasicModel({
            tables: {
               Orders: makeTableEntry("Orders")
            },
            sqlParseResult: "_#(js:designer.qb.parseFailed)"
         })
      });

      expect(comp.isParseFailed()).toBe(true);
      expect(comp.tableCount).toBe(1);
      expect(comp.supportsFullOuterJoin).toBe(true);

      comp.dataSource = "ds2";
      expect(comp.supportsFullOuterJoin).toBe(false);

      model.tables = null;
      expect(comp.tableCount).toBe(0);
   });

   it("should build a condition dialog model from cloned condition state", () => {
      const condition = makeClause("Orders.a");
      const { comp, model } = createSimpleQueryPane({
         model: makeBasicModel({ conditionList: [condition] })
      });
      comp.conditionFields = [{ name: "Orders.a", columnName: "a", tableName: "Orders", type: "string" }];

      comp.setUpConditionDialogModel();

      expect(comp.conditionDialogModel.databaseName).toBe("ds1");
      expect(comp.conditionDialogModel.fields).toEqual(comp.conditionFields);
      expect(comp.conditionDialogModel.fields).not.toBe(comp.conditionFields);
      expect(comp.conditionDialogModel.conditionList).toEqual(model.conditionList);
      expect(comp.conditionDialogModel.conditionList).not.toBe(model.conditionList);
   });
});

function makeClause(field: string) {
   const value = (expression: string) => ({
      type: ClauseValueTypes.FIELD,
      expression
   });

   return {
      type: "clause",
      level: 0,
      negated: false,
      operation: { symbol: "=" },
      value1: value(field),
      value2: value("Other.field"),
      value3: value("Other.third")
   } as any;
}
