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
 * DatabaseQueryComponent - single pass (+concurrency + memory leak)
 *
 * Risk-first coverage:
 *   Group 1 [Risk 2] - queryModel setter, tab state, and disabled-tab guards
 *   Group 2 [Risk 3] - query update posting and model refresh callback
 *   Group 3 [Risk 3] - SQL/grouping exit branches and user warnings
 *   Group 4 [Risk 2] - public helpers and subscription cleanup
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

const GET_QUERY_MODEL_URI = "../api/data/datasource/query/query-model";
const QUERY_UPDATE_URI = "../api/data/datasource/query/update";

function makeQueryModel(overrides: Partial<AdvancedSqlQueryModel> = {}): AdvancedSqlQueryModel {
   return {
      name: "OrdersQuery",
      sqlEdited: false,
      linkPaneModel: { tables: [{ name: "Orders" }] } as any,
      fieldPaneModel: { fields: [{ name: "Orders.id", alias: "orderId" }] } as any,
      conditionPaneModel: {} as any,
      sortPaneModel: {} as any,
      groupingPaneModel: {} as any,
      freeFormSQLPaneModel: {
         sqlString: "select * from orders",
         generatedSqlString: "select * from orders",
         parseResult: ParseResult.PARSE_SUCCESS,
         parseSql: true,
         hasSqlString: true,
      } as any,
      ...overrides,
   };
}

describe("DatabaseQueryComponent - single pass", () => {
   let http: HttpTestingController;
   let modelChange$: Subject<() => void>;
   let queryModelService: {
      modelChange: any;
      emitModelChange: ReturnType<typeof vi.fn>;
      parseSql: ReturnType<typeof vi.fn>;
   };

   beforeEach(() => {
      modelChange$ = new Subject<() => void>();
      queryModelService = {
         modelChange: modelChange$.asObservable(),
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

   function createComponent() {
      const comp = new DatabaseQueryComponent(
         TestBed.inject(HttpClient),
         TestBed.inject(NgbModal),
         TestBed.inject(DataQueryModelService),
      );
      comp.runtimeId = "runtime-1";
      comp.queryModel = makeQueryModel();
      return comp;
   }

   describe("Group 1 - state and guards", () => {
      it("should switch to the SQL tab when the input model has parse failure", () => {
         const comp = new DatabaseQueryComponent(
            TestBed.inject(HttpClient),
            TestBed.inject(NgbModal),
            TestBed.inject(DataQueryModelService),
         );

         comp.queryModel = makeQueryModel({
            freeFormSQLPaneModel: {
               sqlString: "bad sql",
               generatedSqlString: "",
               parseResult: ParseResult.PARSE_FAILED,
               parseSql: true,
               hasSqlString: true,
            } as any,
         });

         expect(comp.activeTab).toBe(DatabaseQueryTabs.SQL_STRING);
      });

      it("should disable fields and conditions when parse failed or prerequisite panes are empty", () => {
         const comp = createComponent();
         comp.queryModel = makeQueryModel({
            freeFormSQLPaneModel: {
               sqlString: "bad sql",
               parseResult: ParseResult.PARSE_FAILED,
            } as any,
            linkPaneModel: { tables: [] } as any,
            fieldPaneModel: { fields: [] } as any,
         });

         expect(comp.isTabDisabled(DatabaseQueryTabs.FIELDS)).toBe(true);
         expect(comp.isTabDisabled(DatabaseQueryTabs.CONDITIONS)).toBe(true);
      });

      it("should disable every non-links tab while a join is being edited", () => {
         const comp = createComponent();

         comp.joinEditingChanged(true);

         expect(comp.isTabDisabled(DatabaseQueryTabs.SORT)).toBe(true);
         expect(comp.isTabDisabled(DatabaseQueryTabs.LINKS)).toBe(false);
      });
   });

   describe("Group 2 - query updates", () => {
      it("should post the current model and emit model refresh callback", () => {
         const comp = createComponent();
         const callback = vi.fn();

         comp.updateQuery(DatabaseQueryTabs.FIELDS, callback);

         const req = http.expectOne(request => request.url === QUERY_UPDATE_URI);
         expect(req.request.method).toBe("POST");
         expect(req.request.params.get("runtimeId")).toBe("runtime-1");
         expect(req.request.params.get("tab")).toBe(DatabaseQueryTabs.FIELDS);
         req.flush({});

         expect(queryModelService.emitModelChange).toHaveBeenCalledWith(callback);
      });

      it("should request a model refresh before entering the SQL tab from links", () => {
         const comp = createComponent();
         comp.activeTab = DatabaseQueryTabs.LINKS;
         const event = {
            activeId: DatabaseQueryTabs.LINKS,
            preventDefault: vi.fn(),
            nextId: DatabaseQueryTabs.SQL_STRING,
         } as unknown as NgbNavChangeEvent;

         comp.updateQueryTab(event);

         expect(event.preventDefault).toHaveBeenCalledTimes(1);
         expect(queryModelService.emitModelChange).toHaveBeenCalledTimes(1);
         expect(comp.activeTab).toBe(DatabaseQueryTabs.SQL_STRING);
      });
   });

   describe("Group 3 - tab switching branches", () => {
      it("should confirm before leaving SQL tab when structured view would lose information", async () => {
         const comp = createComponent();
         comp.queryModel = makeQueryModel({
            freeFormSQLPaneModel: {
               sqlString: "select id from orders",
               generatedSqlString: "select * from orders",
               parseSql: true,
               hasSqlString: true,
               parseResult: ParseResult.PARSE_SUCCESS,
            } as any,
         });
         vi.spyOn(ComponentTool, "showConfirmDialog").mockResolvedValue("yes");

         comp.switchFromFreeSqlTab(DatabaseQueryTabs.FIELDS);
         await Promise.resolve();

         expect(comp.activeTab).toBe(DatabaseQueryTabs.FIELDS);
      });

      it("should show a warning instead of switching from grouping when group by is invalid", () => {
         const comp = createComponent();
         comp.groupByChanged(false);
         const messageSpy = vi.spyOn(ComponentTool, "showMessageDialog").mockImplementation(() => null as any);

         comp.switchFromGroupingTab(DatabaseQueryTabs.PREVIEW);

         expect(messageSpy).toHaveBeenCalledWith(
            expect.anything(),
            "_#(js:Warning)",
            "_#(js:common.uql.queryJdbc.colNotFound)",
         );
      });
   });

   describe("Group 4 - helpers and cleanup", () => {
      it("should build the query fields map from aliases to source names", () => {
         const comp = createComponent();
         comp.queryModel = makeQueryModel({
            fieldPaneModel: {
               fields: [
                  { alias: "orderId", name: "Orders.id" },
                  { alias: "total", name: "Orders.total" },
               ],
            } as any,
         });

         expect(Array.from(comp.getQueryFieldsMap().entries())).toEqual([
            ["orderId", "Orders.id"],
            ["total", "Orders.total"],
         ]);
      });

      it("should delegate condition checks to the conditions pane when the active tab is CONDITIONS", async () => {
         const comp = createComponent();
         comp.activeTab = DatabaseQueryTabs.CONDITIONS;
         comp.queryConditionsPane = {
            checkDirtyConditions: vi.fn().mockResolvedValue(undefined),
         } as any;

         await comp.checkQuery();

         expect(comp.queryConditionsPane.checkDirtyConditions).toHaveBeenCalledTimes(1);
      });

      it("should unsubscribe from subscriptions on ngOnDestroy", () => {
         const comp = createComponent();
         // subscriptions is a private Subscription — cast needed to verify teardown on ngOnDestroy
         const unsubscribeSpy = vi.spyOn((comp as any).subscriptions, "unsubscribe");

         comp.ngOnDestroy();

         expect(unsubscribeSpy).toHaveBeenCalledTimes(1);
      });

      it("should reload the query model when modelChange emits and runtimeId is present", () => {
         const comp = createComponent();
         const callback = vi.fn();

         modelChange$.next(callback);

         const req = http.expectOne(request => request.url === GET_QUERY_MODEL_URI);
         expect(req.request.params.get("runtimeId")).toBe("runtime-1");
         req.flush(makeQueryModel({ name: "ReloadedQuery" }));

         expect(comp.queryModel.name).toBe("ReloadedQuery");
         expect(callback).toHaveBeenCalledTimes(1);
      });
   });
});
