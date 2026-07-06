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
 * Shared test helpers for sql-query-dialog.component P1/P2 spec files.
 *
 * Uses direct class instantiation — the constructor takes 5 dependencies,
 * all easily mocked.  ViewChild refs (tree, joinDialog, conditionDialog,
 * sqlTextArea, advancedQueryPane) are undefined in this mode; tests that
 * need them assign them directly on comp after makeComponent().
 */

import { of, Subject } from "rxjs";
import { SQLQueryDialog } from "./sql-query-dialog.component";
import { SqlQueryDialogModel } from "../../../composer/data/ws/sql-query-dialog-model";
import { BasicSqlQueryModel } from "../../../composer/data/ws/basic-sql-query-model";
import { SqlQueryDialogController } from "./sql-query-dialog-controller";

// ---------------------------------------------------------------------------
// Model factories
// ---------------------------------------------------------------------------

export function makeSimpleModel(overrides: Partial<BasicSqlQueryModel> = {}): BasicSqlQueryModel {
   const sm = new BasicSqlQueryModel();
   sm.tables = {};
   sm.columns = [];
   sm.maxColumnCount = 100;
   sm.joins = [];
   sm.conditionList = [];
   sm.sqlString = "SELECT * FROM table1";
   sm.sqlParseResult = null;
   sm.sqlEdited = false;
   sm.freeFormSqlEnabled = false;
   Object.assign(sm, overrides);
   return sm;
}

export function makeModel(overrides: Partial<SqlQueryDialogModel> = {}): SqlQueryDialogModel {
   const m = new SqlQueryDialogModel();
   m.mashUpData = false;
   m.name = "queryTable";
   m.runtimeId = "runtime-123";
   m.dataSources = ["ds1"];
   m.dataSource = "ds1";
   m.variableNames = [];
   m.physicalTablesEnabled = false;
   m.freeFormSqlEnabled = false;
   m.advancedEdit = false;
   m.simpleModel = makeSimpleModel();
   m.advancedModel = null;
   m.supportsFullOuterJoin = [];
   Object.assign(m, overrides);
   return m;
}

// ---------------------------------------------------------------------------
// Service mocks
// ---------------------------------------------------------------------------

export function makeHttp() {
   return {
      get: vi.fn().mockReturnValue(of([])),
      post: vi.fn().mockReturnValue(of(null)),
      delete: vi.fn().mockReturnValue(of(null)),
   };
}

export function makeModal() {
   return {
      open: vi.fn().mockReturnValue({
         componentInstance: { onCommit: new Subject<string>() },
         result: Promise.reject("dismissed"),
      }),
   };
}

export function makeQueryModelService() {
   return {
      emitGraphViewChange: vi.fn(),
      getUnjoinedTables: vi.fn().mockReturnValue([]),
   };
}

export function makeModelService() {
   return { getModel: vi.fn(), sendModel: vi.fn() };
}

// ---------------------------------------------------------------------------
// Controller mock
// ---------------------------------------------------------------------------

export function makeController(overrides: Partial<SqlQueryDialogController> = {}): SqlQueryDialogController {
   return {
      dataSource: "ds1",
      CONTROLLER_SOCKET: "/events/ws/sql",
      CONTROLLER_MODEL: "../api/ws/sql",
      sqlConditionProvider: null,
      subQuery: false,
      getSQLString: vi.fn().mockReturnValue(of("")),
      getSqlParseResult: vi.fn().mockReturnValue(of("")),
      getTableColumns: vi.fn().mockReturnValue(of([])),
      getDataSourceTree: vi.fn().mockReturnValue(of({ children: [] })),
      setModel: vi.fn(),
      getModel: vi.fn().mockReturnValue(of(makeModel())),
      setConditionFields: vi.fn(),
      ...overrides,
   } as SqlQueryDialogController;
}

// ---------------------------------------------------------------------------
// Component factory
// ---------------------------------------------------------------------------

export interface ComponentResult {
   comp: SQLQueryDialog;
   http: ReturnType<typeof makeHttp>;
   modal: ReturnType<typeof makeModal>;
   queryModelSvc: ReturnType<typeof makeQueryModelService>;
   controller: SqlQueryDialogController;
}

export function makeComponent(opts: {
   model?: SqlQueryDialogModel;
   controller?: SqlQueryDialogController;
   http?: ReturnType<typeof makeHttp>;
   modal?: ReturnType<typeof makeModal>;
   queryModelSvc?: ReturnType<typeof makeQueryModelService>;
   tables?: any[];
   initTableName?: string;
   crossJoinEnabled?: boolean;
   mashUpData?: boolean;
   skipNgOnInit?: boolean;
} = {}): ComponentResult {
   const modelSvc = makeModelService();
   const modal = opts.modal ?? makeModal();
   const http = opts.http ?? makeHttp();
   const queryModelSvc = opts.queryModelSvc ?? makeQueryModelService();
   const document = { activeElement: null };
   const controller = opts.controller ?? makeController(
      opts.model ? { getModel: vi.fn().mockReturnValue(of(opts.model)) } : {}
   );

   const comp = new SQLQueryDialog(
      modelSvc as any, modal as any, http as any, queryModelSvc as any, document,
   );

   comp.tables = opts.tables ?? [];
   comp.initTableName = opts.initTableName ?? "table1";
   comp.controller = controller;
   comp.crossJoinEnabled = opts.crossJoinEnabled ?? false;
   comp.mashUpData = opts.mashUpData ?? false;

   if(!opts.skipNgOnInit) {
      comp.ngOnInit();
   }

   return { comp, http, modal, queryModelSvc, controller };
}
