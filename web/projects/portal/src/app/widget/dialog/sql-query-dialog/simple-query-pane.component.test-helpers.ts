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

import { of } from "rxjs";
import { AssetEntry } from "../../../../../../shared/data/asset-entry";
import { AssetType } from "../../../../../../shared/data/asset-type";
import { JoinItem } from "../../../composer/data/ws/join-item";
import { BasicSqlQueryModel } from "../../../composer/data/ws/basic-sql-query-model";
import { SqlQueryDialogModel } from "../../../composer/data/ws/sql-query-dialog-model";
import { syncResolve } from "../../../../testing/tl-async.util";
import { SimpleQueryPaneComponent } from "./simple-query-pane.component";

export function makeTableEntry(table: string = "Orders"): AssetEntry {
   return {
      scope: 0,
      type: AssetType.PHYSICAL_TABLE,
      user: null,
      path: table,
      alias: table,
      identifier: table,
      organization: "org",
      properties: {
         source: table
      }
   };
}

export function makeColumnEntry(
   table: string = "Orders",
   column: string = "state",
   dtype: string = "string"
): AssetEntry {
   return {
      scope: 0,
      type: AssetType.PHYSICAL_COLUMN,
      user: null,
      path: `${table}/${column}`,
      alias: column,
      identifier: `${table}.${column}`,
      organization: "org",
      properties: {
         source: table,
         attribute: column,
         qualifiedAttribute: column,
         dtype
      }
   };
}

export function makeJoin(overrides: Partial<JoinItem> = {}): JoinItem {
   return {
      table1: "Orders",
      table2: "Customers",
      column1: "customer_id",
      column2: "id",
      all1: false,
      all2: false,
      operator: "=",
      ...overrides
   };
}

export function makeBasicModel(overrides: Partial<BasicSqlQueryModel> = {}): BasicSqlQueryModel {
   return {
      tables: {},
      columns: [],
      maxColumnCount: 0,
      joins: [],
      conditionList: [],
      sqlString: "select * from Orders",
      sqlParseResult: "_#(js:designer.qb.parseInit)",
      sqlEdited: false,
      freeFormSqlEnabled: true,
      ...overrides
   } as BasicSqlQueryModel;
}

export function makeQueryModel(simpleModel: BasicSqlQueryModel): SqlQueryDialogModel {
   return {
      mashUpData: false,
      name: "query",
      runtimeId: "runtime-1",
      dataSources: ["ds1"],
      dataSource: "ds1",
      variableNames: [],
      physicalTablesEnabled: true,
      freeFormSqlEnabled: true,
      advancedEdit: false,
      simpleModel,
      supportsFullOuterJoin: [true]
   } as SqlQueryDialogModel;
}

export function createSimpleQueryPane(options: {
   model?: BasicSqlQueryModel;
   modalResult?: Promise<any>;
   sqlString?: string;
   parseResult?: string;
} = {}) {
   const model = options.model || makeBasicModel();
   const modal = {
      // syncResolve: modal.result.then must complete in-stack under Zone + vitest-patch
      open: vi.fn(() => ({ result: options.modalResult || syncResolve({}) }))
   };
   const http = {
      post: vi.fn(() => of({}))
   };
   const controller = {
      getTableColumns: vi.fn((entry: AssetEntry) => {
         return of([
            makeColumnEntry(entry.properties["source"], "id", "integer"),
            makeColumnEntry(entry.properties["source"], "name")
         ]);
      }),
      getDataSourceTree: vi.fn(() => of({ children: [] })),
      getSQLString: vi.fn(() => of(options.sqlString || "select generated")),
      getSqlParseResult: vi.fn(() => of(options.parseResult || "_#(js:designer.qb.parseOK)")),
      sqlConditionProvider: {},
      subQuery: false
   };

   const comp = new SimpleQueryPaneComponent(
      {} as any,
      modal as any,
      http as any,
      {} as any,
      document
   );

   comp.controller = controller as any;
   comp.queryModel = makeQueryModel(model);
   comp.dataSources = ["ds1", "ds2"];
   comp.dataSource = "ds1";
   comp.datasource = "ds1";
   comp.runtimeId = "runtime-1";
   comp.supportsFullOuterJoinArr = [true, false];
   comp.operations = [];
   comp.sessionOperations = [];
   comp.freeFormSqlEnabled = true;
   comp.model = model;
   (comp as any).joinDialog = {};
   (comp as any).conditionDialog = {};

   return {
      comp,
      model,
      modal,
      http,
      controller
   };
}
