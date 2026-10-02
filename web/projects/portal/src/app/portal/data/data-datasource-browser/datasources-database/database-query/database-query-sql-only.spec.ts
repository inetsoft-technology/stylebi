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
 * Bug #77437, a query whose sql string the structured view cannot represent (a lossy parse
 * such as TOP, parsing off, or a parse that is not a full success) is "sql-only": the
 * structured tabs (links, fields, conditions, sort, grouping) are disabled and unreachable,
 * so no tab-leave or structural edit can regenerate the sql and drop parts of it.
 */

import { HttpClient } from "@angular/common/http";
import { HttpClientTestingModule, HttpTestingController } from "@angular/common/http/testing";
import { TestBed } from "@angular/core/testing";
import { NgbModal, NgbNavChangeEvent } from "@ng-bootstrap/ng-bootstrap";
import { Subject } from "rxjs";

import { ComponentTool } from "../../../../../common/util/component-tool";
import { AdvancedSqlQueryModel } from "../../../model/datasources/database/query/advanced-sql-query-model";
import { DataQueryModelService, DatabaseQueryTabs } from "./data-query-model.service";
import { DatabaseQueryComponent } from "./database-query.component";
import { ParseResult } from "./query-sql/parse-result";

const TOP_SQL = "select top 10 id from orders";
const STRUCTURED_TABS = [
   DatabaseQueryTabs.LINKS, DatabaseQueryTabs.FIELDS, DatabaseQueryTabs.CONDITIONS,
   DatabaseQueryTabs.SORT, DatabaseQueryTabs.GROUPING
];

function sqlPane(overrides: any = {}): any {
   return {
      sqlString: TOP_SQL,
      generatedSqlString: "select id from orders",
      parseResult: ParseResult.PARSE_SUCCESS,
      parseSql: true,
      hasSqlString: true,
      lossy: true,
      ...overrides,
   };
}

function makeQueryModel(pane: any): AdvancedSqlQueryModel {
   return {
      name: "OrdersQuery",
      sqlEdited: false,
      linkPaneModel: { tables: [{ name: "orders" }] } as any,
      fieldPaneModel: { fields: [{ name: "orders.id", alias: "id" }] } as any,
      conditionPaneModel: {} as any,
      sortPaneModel: {} as any,
      groupingPaneModel: {} as any,
      freeFormSQLPaneModel: pane,
   };
}

function navEvent(nextId: string): NgbNavChangeEvent {
   return { activeId: null, nextId, preventDefault: vi.fn() } as unknown as NgbNavChangeEvent;
}

describe("DatabaseQueryComponent - sql-only queries (Bug #77437)", () => {
   let http: HttpTestingController;
   let queryModelService: {
      modelChange: any;
      emitModelChange: ReturnType<typeof vi.fn>;
      parseSql: ReturnType<typeof vi.fn>;
   };

   beforeEach(() => {
      queryModelService = {
         modelChange: new Subject<() => void>().asObservable(),
         emitModelChange: vi.fn((callback?: () => void) => callback?.()),
         parseSql: vi.fn(),
      };

      TestBed.configureTestingModule({
         imports: [HttpClientTestingModule],
         providers: [
            { provide: NgbModal, useValue: { open: vi.fn() } },
            { provide: DataQueryModelService, useValue: queryModelService },
         ],
      });

      http = TestBed.inject(HttpTestingController);
   });

   afterEach(() => {
      http.verify();
      vi.restoreAllMocks();
      TestBed.resetTestingModule();
   });

   function createComponent(pane: any, freeFormSqlEnabled = true): DatabaseQueryComponent {
      const comp = new DatabaseQueryComponent(
         TestBed.inject(HttpClient),
         TestBed.inject(NgbModal),
         TestBed.inject(DataQueryModelService),
      );
      comp.runtimeId = "runtime-1";
      comp.freeFormSqlEnabled = freeFormSqlEnabled;
      comp.queryModel = makeQueryModel(pane);
      return comp;
   }

   const sqlOnlyPanes: [string, any][] = [
      ["a lossy parse (TOP)", sqlPane()],
      ["parsing off", sqlPane({ parseSql: false, parseResult: ParseResult.PARSE_INIT, lossy: false })],
      ["a partial parse", sqlPane({ parseResult: ParseResult.PARSE_PARTIALLY, lossy: false })],
      ["a failed parse", sqlPane({ parseResult: ParseResult.PARSE_FAILED, lossy: false })],
   ];

   it.each(sqlOnlyPanes)("should open %s on the SQL tab with every structured tab disabled",
      (_name, pane) => {
         const comp = createComponent(pane);

         expect(comp.isSqlOnly()).toBe(true);
         expect(comp.activeTab).toBe(DatabaseQueryTabs.SQL_STRING);

         for(const tab of STRUCTURED_TABS) {
            expect(comp.isTabDisabled(tab)).toBe(true);
         }

         expect(comp.isTabDisabled(DatabaseQueryTabs.SQL_STRING)).toBe(false);
         expect(comp.isTabDisabled(DatabaseQueryTabs.PREVIEW)).toBe(false);
      });

   it("should keep a fully parsed, non-lossy query editable in the structured view", () => {
      const comp = createComponent(sqlPane({ sqlString: "select id from orders", lossy: false }));

      expect(comp.isSqlOnly()).toBe(false);
      expect(comp.activeTab).toBe(DatabaseQueryTabs.LINKS);

      for(const tab of STRUCTURED_TABS) {
         expect(comp.isTabDisabled(tab)).toBe(false);
      }
   });

   it("should keep a structural query (no sql string) editable", () => {
      const comp = createComponent(sqlPane({ hasSqlString: false, sqlString: "select id from orders",
                                            lossy: false }));

      expect(comp.isSqlOnly()).toBe(false);
      expect(comp.isTabDisabled(DatabaseQueryTabs.LINKS)).toBe(false);
   });

   it.each([
      ["a lossy parse", sqlPane()],
      ["a failed parse", sqlPane({ parseResult: ParseResult.PARSE_FAILED, lossy: false })],
   ])("should open %s on the preview tab when the SQL tab is not available", (_name, pane) => {
      const comp = createComponent(pane, false);

      expect(comp.activeTab).toBe(DatabaseQueryTabs.PREVIEW);
   });

   it.each(STRUCTURED_TABS)("should not switch to the disabled %s tab or post an update", (tab) => {
      const comp = createComponent(sqlPane());
      const event = navEvent(tab);

      comp.updateQueryTab(event);

      expect(event.preventDefault).toHaveBeenCalledTimes(1);
      expect(comp.activeTab).toBe(DatabaseQueryTabs.SQL_STRING);
      expect(queryModelService.emitModelChange).not.toHaveBeenCalled();
      expect(queryModelService.parseSql).not.toHaveBeenCalled();
      // http.verify() in afterEach: no update was posted
   });

   it("should show the sql-only message instead of the info lost confirm for a lossy parse", () => {
      const comp = createComponent(sqlPane());
      const messageSpy = vi.spyOn(ComponentTool, "showMessageDialog")
         .mockImplementation(() => null as any);
      const confirmSpy = vi.spyOn(ComponentTool, "showConfirmDialog");

      comp.switchFromFreeSqlTab(DatabaseQueryTabs.FIELDS);

      expect(messageSpy).toHaveBeenCalledWith(expect.anything(), "_#(js:Info)",
                                              "_#(js:designer.qb.sqlOnly)");
      expect(confirmSpy).not.toHaveBeenCalled();
      expect(comp.activeTab).toBe(DatabaseQueryTabs.SQL_STRING);
   });

   it("should stay on the SQL tab when leaving it parses the edited sql into a lossy query", () => {
      const comp = createComponent(sqlPane({ sqlString: "select id from orders", lossy: false }));
      comp.activeTab = DatabaseQueryTabs.SQL_STRING;
      comp.queryModel.sqlEdited = true;
      comp.queryModel.freeFormSQLPaneModel.sqlString = TOP_SQL;
      const messageSpy = vi.spyOn(ComponentTool, "showMessageDialog")
         .mockImplementation(() => null as any);
      queryModelService.parseSql.mockImplementation((_pane, _id, _exec, callback) =>
         callback(makeQueryModel(sqlPane())));

      comp.updateQueryTab(navEvent(DatabaseQueryTabs.FIELDS));

      expect(queryModelService.parseSql).toHaveBeenCalledTimes(1);
      expect(messageSpy).toHaveBeenCalledWith(expect.anything(), "_#(js:Info)",
                                              "_#(js:designer.qb.sqlOnly)");
      expect(comp.activeTab).toBe(DatabaseQueryTabs.SQL_STRING);
      expect(comp.isTabDisabled(DatabaseQueryTabs.FIELDS)).toBe(true);
   });

   it("should move off a structured tab when a refreshed model becomes sql-only", () => {
      const comp = createComponent(sqlPane({ sqlString: "select id from orders", lossy: false }));
      comp.activeTab = DatabaseQueryTabs.FIELDS;

      comp.changeQueryModel(makeQueryModel(sqlPane()));

      expect(comp.activeTab).toBe(DatabaseQueryTabs.SQL_STRING);
   });

   it("should enable the structured tabs again once the sql is re-parsed without TOP", () => {
      const comp = createComponent(sqlPane());

      comp.changeQueryModel(makeQueryModel(sqlPane({ sqlString: "select id from orders",
                                                     lossy: false })));

      for(const tab of STRUCTURED_TABS) {
         expect(comp.isTabDisabled(tab)).toBe(false);
      }
   });
});
