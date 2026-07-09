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
 * DataQueryModelService - unit tests
 *
 * Risk-first coverage (7 groups, 13 cases):
 *   Group 1 [Risk 2, 2]    - modelChange / graphViewChange (2 cases)
 *   Group 2 [Risk 2]       - getFieldFunctionTree (1 case)
 *   Group 3 [Risk 3, 2]    - getVariables (2 cases)
 *   Group 4 [Risk 3, 2]    - parseSql (2 cases)
 *   Group 5 [Risk 3, 2, 2, 2] - exported tree/name helpers (4 cases)
 *   Group 6 [Risk 2]       - getShiftIndexesRange (1 case)
 *   Group 7 [Risk 2]       - unjoined tables state (1 case)
 *
 * Fixed bugs:
 *   - Bug #75600: findNextNode used to compare node.data by JSON value, so duplicate-data siblings
 *     resolved to the first matching sibling and could return the target itself. Fixed by
 *     comparing TreeNodeModel objects by reference instead.
 *
 * KEY contracts:
 *   - modelChange and graphViewChange expose public notification streams.
 *   - getFieldFunctionTree returns the operator/function menu structure used by the query editor.
 *   - Empty variable collection proceeds immediately without opening a dialog.
 *   - Variable dialog commits persist values before invoking the continuation callback.
 *   - parseSql(error, executeQuery=false) shows the parse error, collects variables, then retries
 *     with executeQuery=true before invoking the caller callback.
 *   - parseSql(success) invokes the callback directly without entering variable recovery.
 *   - Tree helper functions must preserve sibling order and field-name source precedence.
 *   - Unjoined-table state is stored by reference for callers that manage the array.
 *
 * Design gaps:
 *   - getFieldFunctionTree static labels are sampled, not exhaustively asserted.
 *   - Quote-mode fallback for missing quoteColumnName is not asserted because the source contract
 *     appears to require quote metadata when quote=true.
 */

import { HttpClientTestingModule, HttpTestingController } from "@angular/common/http/testing";
import { TestBed } from "@angular/core/testing";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";

import { AssetEntry } from "../../../../../../../../shared/data/asset-entry";
import { AssetType } from "../../../../../../../../shared/data/asset-type";
import { ComponentTool } from "../../../../../common/util/component-tool";
import { TreeNodeModel } from "../../../../../widget/tree/tree-node-model";
import { VariableInputDialog } from "../../../../../widget/dialog/variable-input-dialog/variable-input-dialog.component";
import { FreeFormSqlPaneModel } from "../../../model/datasources/database/query/free-form-sql-pane/free-form-sql-pane-model";
import {
   DataQueryModelService,
   findNextNode,
   findTableChildren,
   getFieldFullName,
   getFieldFullNameByEntry,
   getShiftIndexesRange,
} from "./data-query-model.service";

const COLLECT_QUERY_VARIABLES_URL = "../api/data/datasource/query/variables";
const PARSE_SQL_STRING_URL = "../api/data/datasource/query/save/freeSQLModel";
const UPDATE_QUERY_VARIABLES_URL = "../api/data/datasource/query/variables/update";

function makeEntry(
   identifier: string,
   type: AssetType,
   properties: { [key: string]: string } = {}
): AssetEntry {
   return {
      scope: 1,
      type,
      user: null,
      path: identifier,
      alias: null,
      identifier,
      properties,
      organization: "org",
   };
}

function makeNode(data: any, children: TreeNodeModel[] = []): TreeNodeModel {
   return {
      label: data?.identifier || data?.id || "node",
      data,
      children,
      leaf: children.length === 0,
   };
}

describe("DataQueryModelService", () => {
   let service: DataQueryModelService;
   let http: HttpTestingController;
   let modalService: NgbModal;

   beforeEach(() => {
      modalService = {} as NgbModal;

      TestBed.configureTestingModule({
         imports: [HttpClientTestingModule],
         providers: [
            DataQueryModelService,
            { provide: NgbModal, useValue: modalService },
         ],
      });

      service = TestBed.inject(DataQueryModelService);
      http = TestBed.inject(HttpTestingController);
   });

   afterEach(() => {
      http.verify();
      vi.restoreAllMocks();
      TestBed.resetTestingModule();
   });

   // ---------------------------------------------------------------------------
   // Group 1 [Risk 2, 2] - modelChange / graphViewChange
   // ---------------------------------------------------------------------------
   describe("modelChange / graphViewChange", () => {
      it("[Risk 2] should emit the callback through modelChange", () => {
         const callback = vi.fn();
         const emitted: Array<() => void> = [];
         service.modelChange.subscribe(value => emitted.push(value));

         service.emitModelChange(callback);

         expect(emitted).toEqual([callback]);
      });

      it("[Risk 2] should emit through graphViewChange", () => {
         const emitted: void[] = [];
         service.graphViewChange.subscribe(value => emitted.push(value));

         service.emitGraphViewChange();

         expect(emitted).toHaveLength(1);
      });
   });

   // ---------------------------------------------------------------------------
   // Group 2 [Risk 2] - getFieldFunctionTree
   // ---------------------------------------------------------------------------
   describe("getFieldFunctionTree", () => {
      it("[Risk 2] should expose operator and aggregate function groups used by the query editor", () => {
         const tree = service.getFieldFunctionTree();

         expect(tree.children.map(child => child.label)).toEqual(["_#(js:Operator)", "_#(js:Function)"]);
         expect(tree.children[0].children.map(child => child.data)).toEqual(["+", "-", "*", "/"]);
         expect(tree.children[1].children.map(child => child.data)).toEqual(["Sum", "Avg", "Count", "Max", "Min"]);
      });
   });

   // ---------------------------------------------------------------------------
   // Group 3 [Risk 3, 2] - getVariables
   // ---------------------------------------------------------------------------
   describe("getVariables", () => {
      it("[Risk 3] should call the continuation immediately and skip the dialog when no variables are returned", () => {
         // Regression-sensitive: parsing should not stall behind an empty dialog when the SQL has
         // no runtime variables.
         const callback = vi.fn();
         const dialogSpy = vi.spyOn(ComponentTool, "showDialog");

         service.getVariables("runtime-1", "select 1", callback);

         const req = http.expectOne(request =>
            request.url === COLLECT_QUERY_VARIABLES_URL &&
            request.params.get("runtimeId") === "runtime-1");
         expect(req.request.params.get("runtimeId")).toBe("runtime-1");
         expect(req.request.params.get("sqlString")).toBe("select 1");
         req.flush([]);

         expect(dialogSpy).not.toHaveBeenCalled();
         expect(callback).toHaveBeenCalledTimes(1);
      });

      it("[Risk 2] should persist dialog variables before invoking the continuation callback", () => {
         let commit: (model: any) => void;
         const dialog: any = {};
         vi.spyOn(ComponentTool, "showDialog").mockImplementation(
            (_modal: any, dialogType: any, onCommit: (model: any) => void) => {
               expect(dialogType).toBe(VariableInputDialog);
               commit = onCommit;
               return dialog;
            });
         const callback = vi.fn();
         const vars = [{ name: "Region", value: "West" }];

         service.getVariables("runtime-2", null, callback);

         const collectReq = http.expectOne(request =>
            request.url === COLLECT_QUERY_VARIABLES_URL &&
            request.params.get("runtimeId") === "runtime-2");
         expect(collectReq.request.params.get("runtimeId")).toBe("runtime-2");
         expect(collectReq.request.params.has("sqlString")).toBe(false);
         collectReq.flush(vars);

         expect(dialog.model).toEqual({ varInfos: vars });
         commit({ varInfos: vars });

         const updateReq = http.expectOne(request =>
            request.url === UPDATE_QUERY_VARIABLES_URL &&
            request.params.get("runtimeId") === "runtime-2");
         expect(updateReq.request.method).toBe("POST");
         expect(updateReq.request.params.get("runtimeId")).toBe("runtime-2");
         expect(updateReq.request.body).toBe(vars);
         updateReq.flush({});

         expect(callback).toHaveBeenCalledTimes(1);
      });
   });

   // ---------------------------------------------------------------------------
   // Group 4 [Risk 3, 2] - parseSql
   // ---------------------------------------------------------------------------
   describe("parseSql", () => {
      it("[Risk 3] should show parse errors, collect variables with the updated SQL, then retry with executeQuery=true", () => {
         // Regression-sensitive: the parse-error recovery path is a multi-step flow; skipping the
         // retry leaves the query model stale after the user supplies variables.
         const messageSpy = vi.spyOn(ComponentTool, "showMessageDialog")
            .mockResolvedValue("ok");
         const callback = vi.fn();
         const initialModel = { sqlString: "select ${Region}" } as FreeFormSqlPaneModel;
         const updatedPane = { sqlString: "select 'West'" } as FreeFormSqlPaneModel;
         const finalModel: any = {
            name: "Parsed Query",
            freeFormSQLPaneModel: updatedPane,
         };

         service.parseSql(initialModel, "runtime-3", false, callback);

         const firstParse = http.expectOne(PARSE_SQL_STRING_URL);
         expect(firstParse.request.body).toEqual(expect.objectContaining({
            runtimeId: "runtime-3",
            freeFormSqlPaneModel: initialModel,
            executeQuery: false,
         }));
         firstParse.flush({
            errorMsg: "Missing variable",
            model: finalModel,
         });

         expect(messageSpy).toHaveBeenCalledWith(
            modalService,
            "_#(js:Error)",
            "Missing variable");

         const collectReq = http.expectOne(request =>
            request.url === COLLECT_QUERY_VARIABLES_URL &&
            request.params.get("runtimeId") === "runtime-3");
         expect(collectReq.request.params.get("runtimeId")).toBe("runtime-3");
         expect(collectReq.request.params.get("sqlString")).toBe("select 'West'");
         collectReq.flush([]);

         const retryParse = http.expectOne(PARSE_SQL_STRING_URL);
         expect(retryParse.request.body).toEqual(expect.objectContaining({
            runtimeId: "runtime-3",
            freeFormSqlPaneModel: updatedPane,
            executeQuery: true,
         }));
         retryParse.flush({
            errorMsg: "Still returned but executeQuery allows callback",
            model: finalModel,
         });

         expect(callback).toHaveBeenCalledWith(finalModel);
      });

      it("[Risk 2] should call the callback directly when parsing succeeds without an error", () => {
         const messageSpy = vi.spyOn(ComponentTool, "showMessageDialog");
         const callback = vi.fn();
         const model = { sqlString: "select 1" } as FreeFormSqlPaneModel;
         const parsedModel: any = {
            name: "Parsed Query",
            freeFormSQLPaneModel: model,
         };

         service.parseSql(model, "runtime-4", false, callback);

         const parseReq = http.expectOne(PARSE_SQL_STRING_URL);
         expect(parseReq.request.body).toEqual(expect.objectContaining({
            runtimeId: "runtime-4",
            freeFormSqlPaneModel: model,
            executeQuery: false,
         }));
         parseReq.flush({
            errorMsg: null,
            model: parsedModel,
         });

         expect(callback).toHaveBeenCalledWith(parsedModel);
         expect(messageSpy).not.toHaveBeenCalled();
         http.expectNone(COLLECT_QUERY_VARIABLES_URL);
      });
   });

   // ---------------------------------------------------------------------------
   // Group 5 [Risk 3, 2, 2, 2] - exported tree/name helpers
   // ---------------------------------------------------------------------------
   describe("exported tree/name helpers", () => {
      it("[Risk 3] should find the next sibling by node identity when siblings have duplicate data", () => {
         // Regression test for Bug #75600: findNextNode now compares TreeNodeModel objects by
         // reference, so duplicate-data siblings no longer resolve to the first match or the
         // target node itself.
         const first = makeNode({ id: "duplicate" });
         const second = makeNode({ id: "duplicate" });
         const third = makeNode({ id: "third" });
         const root = makeNode({ id: "root" }, [first, second, third]);

         expect(findNextNode(root, second)).toBe(third);
      });

      it("[Risk 2] should build field names from source alias first and source fallback second", () => {
         const aliasEntry = makeEntry("alias", AssetType.PHYSICAL_COLUMN, {
            source_alias: "AliasTable",
            source_with_no_quote: "RawTable",
            source: "SourceTable",
            attribute: "Amount",
         });
         const fallbackEntry = makeEntry("fallback", AssetType.PHYSICAL_COLUMN, {
            source_with_no_quote: "RawTable",
            source: "SourceTable",
            attribute: "Amount",
         });

         expect(getFieldFullNameByEntry(aliasEntry)).toBe("AliasTable.Amount");
         expect(getFieldFullNameByEntry(fallbackEntry)).toBe("RawTable.Amount");
      });

      it("[Risk 2] should return an empty field name for null nodes and delegate valid nodes to entry mapping", () => {
         const node = makeNode(makeEntry("Column", AssetType.PHYSICAL_COLUMN, {
            source_alias: "Orders",
            attribute: "Total",
         }));

         expect(getFieldFullName(null as any)).toBe("");
         expect(getFieldFullName(node)).toBe("Orders.Total");
      });

      it("[Risk 2] should collect children only from matching physical table entries", () => {
         const matchingTable = makeEntry("TableA", AssetType.PHYSICAL_TABLE);
         const otherTable = makeEntry("TableB", AssetType.PHYSICAL_TABLE);
         const matchingChild = makeNode(makeEntry("TableA.Col1", AssetType.PHYSICAL_COLUMN));
         const otherChild = makeNode(makeEntry("TableB.Col1", AssetType.PHYSICAL_COLUMN));
         const root = makeNode({ id: "root" }, [
            makeNode(matchingTable, [matchingChild]),
            makeNode(otherTable, [otherChild]),
         ]);

         expect(findTableChildren(root, [matchingTable])).toEqual([matchingChild]);
      });
   });

   // ---------------------------------------------------------------------------
   // Group 6 [Risk 2] - getShiftIndexesRange
   // ---------------------------------------------------------------------------
   describe("getShiftIndexesRange", () => {
      it("[Risk 2] should return an inclusive descending range when the shift start is after the current index", () => {
         expect(getShiftIndexesRange(4, 1)).toEqual([4, 3, 2, 1]);
      });
   });

   // ---------------------------------------------------------------------------
   // Group 7 [Risk 2] - unjoined tables state
   // ---------------------------------------------------------------------------
   describe("unjoined tables state", () => {
      it("[Risk 2] should store and return the same unjoined-tables array reference", () => {
         const tables = ["Orders", "Customers"];

         service.setUnjoinedTables(tables);

         expect(service.getUnjoinedTables()).toBe(tables);
      });
   });
});
