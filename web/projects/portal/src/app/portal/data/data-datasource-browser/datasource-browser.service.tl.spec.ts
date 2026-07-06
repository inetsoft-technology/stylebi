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
 * DatasourceBrowserService - unit tests
 *
 * Risk-first coverage (8 groups, 13 cases):
 *   Group 1 [Risk 2]       - refreshTree / changeFolder (2 cases)
 *   Group 2 [Risk 2]       - getPhysicalTablePermission (1 case)
 *   Group 3 [Risk 3, 2]    - moveDataSource (2 cases)
 *   Group 4 [Risk 2]       - moveSelected (1 case)
 *   Group 5 [Risk 3]       - renameDataSourceFolder (1 case)
 *   Group 6 [Risk 3, 2]    - deleteDataSource0 (2 cases)
 *   Group 7 [Risk 3, 3, 2] - moveDataSourcesToFolder (3 cases)
 *   Group 8 [Risk 3, 2]    - createDataSourceInfos (2 cases)
 *
 * Confirmed bugs (it.failing - remove wrapper once fixed):
 *   - createDataSourceInfos([root entry]) throws because getParentPath0("/") returns null and
 *     the caller dereferences parent.length.
 *
 * KEY contracts:
 *   - Public tree/folder change methods emit the payloads callers subscribe to.
 *   - Physical-table permission is fetched once and replayed from the cached Observable.
 *   - Single/batch move dialogs preserve original paths and invoke the move commit correctly.
 *   - Folder rename commits trim names, run duplicate checks, and reset loading.
 *   - Delete retries with force=true only after a connection-status warning is confirmed.
 *   - Delete errors report the danger callback contract instead of silently completing.
 *   - Move commands preserve old path/id/date/type and compute target paths consistently; server
 *     message/error responses must not call the success callback.
 *   - AssetEntry conversion only returns supported datasource and datasource-folder assets.
 *
 * Design gaps:
 *   - Rename dialog validator text is not exhaustively asserted; duplicate-check and commit
 *     behavior should be covered in a broader rename-focused pass if needed.
 */

import { HttpClientTestingModule, HttpTestingController } from "@angular/common/http/testing";
import { TestBed } from "@angular/core/testing";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";

import { AssetEntry } from "../../../../../../shared/data/asset-entry";
import { AssetType } from "../../../../../../shared/data/asset-type";
import { ComponentTool } from "../../../common/util/component-tool";
import { PortalDataType } from "../data-navigation-tree/portal-data-type";
import { DataSourceInfo } from "../model/data-source-info";
import { DatasourceBrowserService } from "./datasource-browser.service";

const DATASOURCES_URI = "../api/data/datasources";
const DATASOURCE_MOVE_URI = "../api/data/datasources/move";
const DATASOURCE_FOLDER_URI = "../api/data/datasources/browser/folder";
const DATASOURCE_FOLDER_CHECKDUPLICATE = "../api/data/datasources/browser/folder/checkDuplicate";
const PHYSICAL_TABLE_PERMISSION = "../api/data/datasources/physicalTable";

function makeDataSource(overrides: Partial<DataSourceInfo> = {}): DataSourceInfo {
   return {
      name: "Sales DS",
      path: "Root/Sales DS",
      type: { name: PortalDataType.DATABASE, label: "Database" },
      createdBy: "admin",
      createdDate: 12345,
      createdDateLabel: "today",
      dateFormat: "YYYY-MM-DD HH:mm:ss",
      editable: true,
      deletable: true,
      queryCreatable: true,
      connected: false,
      statusMessage: "",
      hasSubFolder: false,
      ...overrides,
   };
}

function makeEntry(path: string, type: AssetType): AssetEntry {
   return {
      scope: 1,
      type,
      user: null,
      path,
      alias: null,
      identifier: "id:" + path,
      properties: {},
      createdUsername: "creator",
      organization: "org",
   };
}

async function flushPromises(): Promise<void> {
   await Promise.resolve();
}

describe("DatasourceBrowserService", () => {
   let service: DatasourceBrowserService;
   let http: HttpTestingController;
   let modalService: NgbModal;

   beforeEach(() => {
      modalService = {} as NgbModal;

      TestBed.configureTestingModule({
         imports: [HttpClientTestingModule],
         providers: [
            DatasourceBrowserService,
            { provide: NgbModal, useValue: modalService },
         ],
      });

      service = TestBed.inject(DatasourceBrowserService);
      http = TestBed.inject(HttpTestingController);
   });

   afterEach(() => {
      http.verify();
      vi.restoreAllMocks();
      TestBed.resetTestingModule();
   });

   // ---------------------------------------------------------------------------
   // Group 1 [Risk 2] - refreshTree / changeFolder
   // ---------------------------------------------------------------------------
   describe("refreshTree / changeFolder", () => {
      it("[Risk 2] should emit datasourceChanged when the tree is refreshed", () => {
         const emitted: void[] = [];
         service.datasourceChanged.subscribe(value => emitted.push(value));

         service.refreshTree();

         expect(emitted).toHaveLength(1);
      });

      it("[Risk 2] should emit the root datasource folder payload when changing folders", () => {
         const emitted: any[] = [];
         service.folderChanged.subscribe(value => emitted.push(value));

         service.changeFolder("Root/Child");

         expect(emitted).toEqual([{
            path: "Root/Child",
            type: PortalDataType.DATA_SOURCE_ROOT_FOLDER,
         }]);
      });
   });

   // ---------------------------------------------------------------------------
   // Group 2 [Risk 2] - getPhysicalTablePermission
   // ---------------------------------------------------------------------------
   describe("getPhysicalTablePermission", () => {
      it("[Risk 2] should fetch once and replay the cached permission value to later subscribers", () => {
         const firstValues: boolean[] = [];
         const secondValues: boolean[] = [];

         service.getPhysicalTablePermission().subscribe(value => firstValues.push(value));

         const firstReq = http.expectOne(PHYSICAL_TABLE_PERMISSION);
         expect(firstReq.request.method).toBe("GET");
         firstReq.flush(true);

         service.getPhysicalTablePermission().subscribe(value => secondValues.push(value));

         expect(firstValues).toEqual([true]);
         expect(secondValues).toEqual([true]);
         http.expectNone(PHYSICAL_TABLE_PERMISSION);
      });
   });

   // ---------------------------------------------------------------------------
   // Group 3 [Risk 3, 2] - moveDataSource
   // ---------------------------------------------------------------------------
   describe("moveDataSource", () => {
      it("[Risk 3] should not open the move dialog when the datasource lacks edit/delete permission", () => {
         // Regression-sensitive: callers use editable/deletable as the permission gate; opening
         // the dialog for a locked datasource allows a forbidden move attempt.
         const dialogSpy = vi.spyOn(ComponentTool, "showDialog");

         service.moveDataSource(makeDataSource({ editable: false, deletable: true }), "/");
         service.moveDataSource(makeDataSource({ editable: true, deletable: false }), "/");

         expect(dialogSpy).not.toHaveBeenCalled();
      });

      it("[Risk 2] should configure dialog paths and move the single datasource from the commit callback", () => {
         let commit: (target: string) => void;
         const dialog: any = {};
         vi.spyOn(ComponentTool, "showDialog").mockImplementation(
            (_modal: any, _dialogType: any, onCommit: (target: string) => void) => {
               commit = onCommit;
               return dialog;
            });
         const datasource = makeDataSource({
            name: "Orders",
            path: "Root/Folder/Orders",
            createdDate: 321,
            type: { name: "DATABASE", label: "" },
         });
         const callback = vi.fn();

         service.moveDataSource(datasource, "Root/Folder", callback);

         expect(dialog.originalPaths).toEqual(["Root/Folder/Orders"]);
         expect(dialog.items).toEqual([datasource]);
         expect(dialog.parentPath).toBe("Root/Folder");
         expect(dialog.grandparentFolder).toBe("Root");

         commit("Archive");

         const req = http.expectOne(DATASOURCE_MOVE_URI);
         expect(req.request.body).toEqual([
            expect.objectContaining({
               path: "Archive/Orders",
               oldPath: "Root/Folder/Orders",
               name: "Orders",
               date: 321,
            }),
         ]);
         req.flush({});

         expect(callback).toHaveBeenCalledTimes(1);
      });
   });

   // ---------------------------------------------------------------------------
   // Group 4 [Risk 2] - moveSelected
   // ---------------------------------------------------------------------------
   describe("moveSelected", () => {
      it("[Risk 2] should configure original paths for the batch and move all selected datasources from commit", () => {
         let commit: (target: string) => void;
         const dialog: any = {};
         vi.spyOn(ComponentTool, "showDialog").mockImplementation(
            (_modal: any, _dialogType: any, onCommit: (target: string) => void) => {
               commit = onCommit;
               return dialog;
            });
         const first = makeDataSource({ name: "Orders", path: "Root/Orders" });
         const second = makeDataSource({ name: "Customers", path: "Root/Customers" });
         const callback = vi.fn();

         service.moveSelected([first, second], "Root", callback);

         expect(dialog.originalPaths).toEqual(["Root/Orders", "Root/Customers"]);
         expect(dialog.items).toEqual([first, second]);

         commit("Archive");

         const req = http.expectOne(DATASOURCE_MOVE_URI);
         expect(req.request.body).toEqual([
            expect.objectContaining({ path: "Archive/Orders", oldPath: "Root/Orders" }),
            expect.objectContaining({ path: "Archive/Customers", oldPath: "Root/Customers" }),
         ]);
         req.flush({});

         expect(callback).toHaveBeenCalledTimes(1);
      });
   });

   // ---------------------------------------------------------------------------
   // Group 5 [Risk 3] - renameDataSourceFolder
   // ---------------------------------------------------------------------------
   describe("renameDataSourceFolder", () => {
      it("[Risk 3] should run duplicate check, post trimmed folder rename, reset loading, and call callback", () => {
         // Regression-sensitive: folder rename is a dialog commit path; stale loading or an
         // untrimmed name leaves the tree inconsistent with the server request.
         let commit: (name: string) => void;
         const dialog: any = {};
         const node: any = { loading: false };
         const callback = vi.fn();
         vi.spyOn(ComponentTool, "showDialog").mockImplementation(
            (_modal: any, _dialogType: any, onCommit: (name: string) => void) => {
               commit = onCommit;
               return dialog;
            });
         const datasource = makeDataSource({
            name: "Old Folder",
            path: "Root/Old Folder",
            type: { name: PortalDataType.DATA_SOURCE_FOLDER, label: "" },
         });

         service.renameDataSourceFolder(datasource, "Root", callback, node);

         expect(dialog.value).toBe("Old Folder");
         dialog.hasDuplicateCheck("New Folder").subscribe();

         const duplicateReq = http.expectOne(DATASOURCE_FOLDER_CHECKDUPLICATE);
         expect(duplicateReq.request.body).toEqual(expect.objectContaining({
            path: "Root",
            newName: "New Folder",
            type: AssetType.DATA_SOURCE_FOLDER,
         }));
         duplicateReq.flush({ duplicate: false });

         commit("  New Folder  ");

         expect(node.loading).toBe(true);
         const renameReq = http.expectOne(DATASOURCE_FOLDER_URI);
         expect(renameReq.request.method).toBe("POST");
         expect(renameReq.request.body).toEqual(expect.objectContaining({
            name: "New Folder",
            path: "Root/Old Folder",
         }));
         renameReq.flush({});

         expect(node.loading).toBe(false);
         expect(callback).toHaveBeenCalledTimes(1);
      });
   });

   // ---------------------------------------------------------------------------
   // Group 6 [Risk 3, 2] - deleteDataSource0
   // ---------------------------------------------------------------------------
   describe("deleteDataSource0", () => {
      it("[Risk 3] should retry with force=true only after the connection-status warning is confirmed", async () => {
         // Regression-sensitive: deleting without the force retry leaves the datasource visible
         // even after the user confirms the warning, while forcing too early bypasses protection.
         const callback = vi.fn();
         const confirmSpy = vi.spyOn(ComponentTool, "showConfirmDialog")
            .mockResolvedValue("ok");

         service.deleteDataSource0("Sales/DB", "Folder/Sales DB", false, callback);

         const first = http.expectOne(req =>
            req.url === DATASOURCES_URI + "/Folder/Sales%20DB" &&
            req.params.get("force") === "false");
         expect(first.request.params.get("name")).toBe("Sales/DB");
         first.flush({ status: "Still connected" });

         await flushPromises();

         expect(confirmSpy).toHaveBeenCalledWith(
            modalService,
            "_#(js:Confirm)",
            "Still connected");

         const forced = http.expectOne(req =>
            req.url === DATASOURCES_URI + "/Folder/Sales%20DB" &&
            req.params.get("force") === "true");
         expect(forced.request.params.get("name")).toBe("Sales/DB");
         forced.flush(null);

         expect(callback).toHaveBeenCalledWith(
            "success",
            "_#(js:data.datasources.deleteDataSourceSuccess)");
      });

      it("[Risk 2] should report the danger callback when the delete request fails", () => {
         const callback = vi.fn();

         service.deleteDataSource0("Sales", "Sales", false, callback);

         const req = http.expectOne(request =>
            request.url === DATASOURCES_URI + "/Sales" &&
            request.params.get("force") === "false" &&
            request.params.get("name") === "Sales");
         req.flush({}, { status: 500, statusText: "Server Error" });

         expect(callback).toHaveBeenCalledWith(
            "danger",
            "_#(js:data.datasources.deleteDataSourceError)");
      });
   });

   // ---------------------------------------------------------------------------
   // Group 7 [Risk 3, 3, 2] - moveDataSourcesToFolder
   // ---------------------------------------------------------------------------
   describe("moveDataSourcesToFolder", () => {
      it("[Risk 3] should show the server message and not call the success callback", () => {
         // Regression-sensitive: a server message means the move did not complete normally; the
         // UI must not refresh as though the move succeeded.
         const callback = vi.fn();
         const messageSpy = vi.spyOn(ComponentTool, "showMessageDialog")
            .mockResolvedValue("ok");

         service.moveDataSourcesToFolder([makeDataSource()], "/", callback);

         const req = http.expectOne(DATASOURCE_MOVE_URI);
         req.flush({ message: "Move blocked" });

         expect(callback).not.toHaveBeenCalled();
         expect(messageSpy).toHaveBeenCalledWith(
            modalService,
            "_#(js:Error)",
            "Move blocked",
            { "ok": "_#(js:OK)" },
            { backdrop: false });
      });

      it("[Risk 3] should pass HTTP move failures to the error callback and not call the success callback", () => {
         // Regression-sensitive: without errorCallback propagation, callers cannot surface move
         // failures or keep selection state consistent.
         const callback = vi.fn();
         const errorCallback = vi.fn();

         service.moveDataSourcesToFolder([makeDataSource()], "Archive", callback, errorCallback);

         const req = http.expectOne(DATASOURCE_MOVE_URI);
         req.flush({ message: "failed" }, { status: 500, statusText: "Server Error" });

         expect(callback).not.toHaveBeenCalled();
         expect(errorCallback).toHaveBeenCalledWith(expect.objectContaining({ status: 500 }));
      });

      it("[Risk 2] should build move commands with the computed target path and original identity fields", () => {
         const callback = vi.fn();
         const datasource = makeDataSource({
            name: "Sales DS",
            path: "Root/Sales DS",
            createdDate: 98765,
            type: { name: "DATABASE", label: "Database" },
         });

         service.moveDataSourcesToFolder([datasource], "Archive", callback);

         const req = http.expectOne(DATASOURCE_MOVE_URI);
         expect(req.request.method).toBe("POST");
         expect(req.request.body).toEqual([
            expect.objectContaining({
               path: "Archive/Sales DS",
               oldPath: "Root/Sales DS",
               name: "Sales DS",
               id: "Root/Sales DS",
               date: 98765,
               type: "DATABASE",
            }),
         ]);

         req.flush({});

         expect(callback).toHaveBeenCalledTimes(1);
      });
   });

   // ---------------------------------------------------------------------------
   // Group 8 [Risk 3, 2] - createDataSourceInfos
   // ---------------------------------------------------------------------------
   describe("createDataSourceInfos", () => {
      it.fails("[Risk 3] should ignore or safely map root entries instead of throwing", () => {
         // Regression-sensitive: drag/drop payloads can include roots; one malformed entry should
         // not crash conversion for the whole batch.
         expect(() => service.createDataSourceInfos([
            makeEntry("/", AssetType.DATA_SOURCE_FOLDER),
         ])).not.toThrow();
      });

      it("[Risk 2] should map supported entries and skip unsupported asset types", () => {
         const infos = service.createDataSourceInfos([
            makeEntry("Folder/Subfolder", AssetType.DATA_SOURCE_FOLDER),
            makeEntry("SalesDB", AssetType.DATA_SOURCE),
            makeEntry("Queries/Q1", AssetType.QUERY),
         ]);

         expect(infos).toHaveLength(2);
         expect(infos[0]).toEqual(expect.objectContaining({
            name: "Subfolder",
            path: "Folder/Subfolder",
            type: { name: PortalDataType.DATA_SOURCE_FOLDER, label: "" },
         }));
         expect(infos[1]).toEqual(expect.objectContaining({
            name: "SalesDB",
            path: "SalesDB",
            type: { name: PortalDataType.DATABASE, label: "" },
         }));
      });
   });
});
