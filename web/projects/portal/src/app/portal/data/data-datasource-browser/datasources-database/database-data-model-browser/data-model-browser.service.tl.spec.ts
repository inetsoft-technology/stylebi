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
 * DataModelBrowserService - unit tests
 *
 * Risk-first coverage (10 groups, 16 cases):
 *   Group 1 [Risk 2]          - changed / emitChanged (1 case)
 *   Group 2 [Risk 3]          - renameDataModelFolder (1 case)
 *   Group 3 [Risk 2]          - addDataModelFolder (1 case)
 *   Group 4 [Risk 3, 3, 3, 2] - moveModelsToTarget (4 cases)
 *   Group 5 [Risk 3, 2, 2, 2] - add model routing and duplicate checks (4 cases)
 *   Group 6 [Risk 3]          - deletePhysicalView (1 case)
 *   Group 7 [Risk 3]          - deleteLogicalModel (1 case)
 *   Group 8 [Risk 2]          - getRenameModelCommit (1 case)
 *   Group 9 [Risk 2]          - createDataBaseAssets (1 case)
 *   Group 10 [Risk 2]         - hasModelDuplicateCheck (1 case)
 *
 * Fixed bugs (Issue #75587):
 *   - moveModelsToTarget(null, ...) threw because items?.length == 0 did not guard null;
 *     now guarded with `!items || items.length == 0`.
 *   - addLogicalModel wired the duplicate checker to the VPM endpoint instead of the
 *     logical-model endpoint; now passes PortalDataType.LOGIC_MODEL.
 *
 * KEY contracts:
 *   - emitChanged notifies subscribers returned by changed().
 *   - Data-model folder dialog commits send duplicate checks/rename/add requests and reset loading.
 *   - Moving data models posts database, folder, and original item list unchanged.
 *   - Move failures surface permission-specific/generic messaging and do not call success callbacks.
 *   - Add-model routing byte-encodes user-controlled route segments across VPM, physical, and logical routes.
 *   - Delete confirmations preserve required params before invoking callbacks.
 *   - Rename commits trim the new name before sending RenameModelEvent.
 *   - AssetEntry conversion and duplicate-check short-circuit behavior remain stable.
 *
 * Design gaps:
 *   - The deepest multi-confirm delete variants are not exhaustively generated; representative
 *     physical/logical delete contracts cover the destructive request params.
 *   - The physical-model duplicate URI is not asserted because another tree implementation uses
 *     the same endpoint, so expected backend routing is ambiguous from local code alone.
 */

import { HttpClientTestingModule, HttpTestingController } from "@angular/common/http/testing";
import { TestBed } from "@angular/core/testing";
import { ActivatedRoute, Router } from "@angular/router";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { type Mock } from "vitest";

import { AssetEntry } from "../../../../../../../../shared/data/asset-entry";
import { AssetType } from "../../../../../../../../shared/data/asset-type";
import { Tool } from "../../../../../../../../shared/util/tool";
import { ComponentTool } from "../../../../../common/util/component-tool";
import { PortalDataType } from "../../../data-navigation-tree/portal-data-type";
import { DatabaseAsset } from "../../../model/datasources/database/database-asset";
import { DataModelBrowserService } from "./data-model-browser.service";

const MOVE_DATA_MODEL_URI = "../api/data/database/dataModel/move";
const DATA_MODEL_FOLDER_URI = "../api/portal/data/database/dataModelFolder";
const DATA_MODEL_FOLDER_CHECK_DUPLICATE_URI = "../api/portal/data/database/dataModelFolder/duplicateCheck";
const PHYSICAL_MODELS_URI = "../api/data/physicalmodel/models";
const LOGICAL_MODELS_URI = "../api/data/logicalmodel/models";
const LOGICAL_MODEL_CHECK_DEPENDENCIES_URI = "../api/data/logicalmodel/checkOuterDependencies";
const LOGICAL_MODEL_DUPLICATE_URI = "../api/data/logicalModel/checkDuplicate";
const LOGICALMODEL_RENAME_URI = "../api/data/logicalmodel/rename";

function makeAsset(overrides: Partial<DatabaseAsset> = {}): DatabaseAsset {
   return {
      databaseName: "Sales/DB",
      type: "physical_model",
      id: "id-1",
      path: "Sales/DB/Model",
      urlPath: "",
      name: "Model",
      createdBy: "admin",
      description: "",
      createdDate: 0,
      editable: true,
      deletable: true,
      createdDateLabel: "",
      ...overrides,
   };
}

function makeEntry(path: string, overrides: Partial<AssetEntry> = {}): AssetEntry {
   return {
      scope: 1,
      type: AssetType.LOGIC_MODEL,
      user: null,
      path,
      alias: null,
      identifier: "id:" + path,
      properties: {},
      createdUsername: "creator",
      organization: "org",
      ...overrides,
   };
}

async function flushPromises(): Promise<void> {
   await Promise.resolve();
}

describe("DataModelBrowserService", () => {
   let service: DataModelBrowserService;
   let http: HttpTestingController;
   let router: { navigate: Mock };
   let route: ActivatedRoute;
   let modalService: NgbModal;

   beforeEach(() => {
      router = { navigate: vi.fn() };
      route = {} as ActivatedRoute;
      modalService = {} as NgbModal;

      TestBed.configureTestingModule({
         imports: [HttpClientTestingModule],
         providers: [
            DataModelBrowserService,
            { provide: NgbModal, useValue: modalService },
            { provide: ActivatedRoute, useValue: route },
            { provide: Router, useValue: router },
         ],
      });

      service = TestBed.inject(DataModelBrowserService);
      http = TestBed.inject(HttpTestingController);
   });

   afterEach(() => {
      http.verify();
      vi.restoreAllMocks();
      TestBed.resetTestingModule();
   });

   // ---------------------------------------------------------------------------
   // Group 1 [Risk 2] - changed / emitChanged
   // ---------------------------------------------------------------------------
   describe("changed / emitChanged", () => {
      it("[Risk 2] should emit to subscribers when the model changes", () => {
         const emitted: void[] = [];
         service.changed().subscribe(value => emitted.push(value));

         service.emitChanged();

         expect(emitted).toHaveLength(1);
      });
   });

   // ---------------------------------------------------------------------------
   // Group 2 [Risk 3] - renameDataModelFolder
   // ---------------------------------------------------------------------------
   describe("renameDataModelFolder", () => {
      it("[Risk 3] should check duplicates, send rename request, reset loading, and call the callback", () => {
         // Regression-sensitive: tree nodes stay stuck in loading state if either success/error
         // branch stops resetting the flag after the commit request.
         let commit: (value: string) => void;
         const dialog: any = {};
         const node: any = { loading: false };
         const callback = vi.fn();
         vi.spyOn(ComponentTool, "showDialog").mockImplementation(
            (_modal: any, _dialogType: any, onCommit: (value: string) => void) => {
               commit = onCommit;
               return dialog;
            });

         service.renameDataModelFolder("SalesDB/Folder A", "Folder A", callback, true, node);

         expect(dialog.value).toBe("Folder A");
         expect(dialog.title).toBe("_#(js:Rename)");
         dialog.hasDuplicateCheck("Renamed").subscribe();

         const duplicateReq = http.expectOne(req =>
            req.url === DATA_MODEL_FOLDER_CHECK_DUPLICATE_URI &&
            req.params.get("databasePath") === "SalesDB" &&
            req.params.get("name") === "Renamed");
         duplicateReq.flush(false);

         commit("Renamed");

         expect(node.loading).toBe(true);
         const renameReq = http.expectOne(DATA_MODEL_FOLDER_URI);
         expect(renameReq.request.method).toBe("PUT");
         expect(renameReq.request.body).toEqual(expect.objectContaining({
            name: "Renamed",
            path: "SalesDB/Folder A",
         }));
         renameReq.flush({});

         expect(node.loading).toBe(false);
         expect(callback).toHaveBeenCalledTimes(1);
      });
   });

   // ---------------------------------------------------------------------------
   // Group 3 [Risk 2] - addDataModelFolder
   // ---------------------------------------------------------------------------
   describe("addDataModelFolder", () => {
      it("[Risk 2] should send an add-folder request from the dialog commit and call the callback", () => {
         let commit: (value: string) => void;
         const dialog: any = {};
         const callback = vi.fn();
         vi.spyOn(ComponentTool, "showDialog").mockImplementation(
            (_modal: any, _dialogType: any, onCommit: (value: string) => void) => {
               commit = onCommit;
               return dialog;
            });

         service.addDataModelFolder("SalesDB", callback);
         commit("New Folder");

         const req = http.expectOne(DATA_MODEL_FOLDER_URI);
         expect(req.request.method).toBe("POST");
         expect(req.request.body).toEqual(expect.objectContaining({
            name: "New Folder",
            parentPath: "SalesDB",
            scope: null,
         }));
         req.flush({});

         expect(callback).toHaveBeenCalledTimes(1);
      });
   });

   // ---------------------------------------------------------------------------
   // Group 4 [Risk 3, 3, 3, 2] - moveModelsToTarget
   // ---------------------------------------------------------------------------
   describe("moveModelsToTarget", () => {
      it("[Risk 3] should show the permission-specific error on HTTP 403 and not call the success callback", () => {
         // Regression-sensitive: permission failures must not look like generic move errors or
         // trigger a UI refresh callback that makes the failed move appear successful.
         const item = makeAsset();
         const callback = vi.fn();
         const messageSpy = vi.spyOn(ComponentTool, "showMessageDialog")
            .mockResolvedValue("ok");

         service.moveModelsToTarget([item], "Target Folder", callback);

         const req = http.expectOne(MOVE_DATA_MODEL_URI);
         req.flush({}, { status: 403, statusText: "Forbidden" });

         expect(callback).not.toHaveBeenCalled(); // (a) no success signal
         expect(messageSpy).toHaveBeenCalledWith( // (b) permission-specific error
            modalService,
            "_#(js:Error)",
            "_#(js:data.datasets.moveTargetPermissionError)");
      });

      it("[Risk 3] should treat a null item list as a no-op instead of throwing", () => {
         // Fixed Issue #75587: the empty-list guard now uses `!items || items.length == 0`
         // instead of `items?.length == 0`, which was false (not a no-op) for null.
         expect(() => service.moveModelsToTarget(null as any, "Target Folder", vi.fn()))
            .not.toThrow();

         http.expectNone(MOVE_DATA_MODEL_URI);
      });

      it("[Risk 3] should show the generic move error for non-permission HTTP failures", () => {
         // Regression-sensitive: generic server failures must be visible and must not trigger the
         // refresh callback that makes the failed move look successful.
         const item = makeAsset();
         const callback = vi.fn();
         const messageSpy = vi.spyOn(ComponentTool, "showMessageDialog")
            .mockResolvedValue("ok");

         service.moveModelsToTarget([item], "Target Folder", callback);

         const req = http.expectOne(MOVE_DATA_MODEL_URI);
         req.flush({}, { status: 500, statusText: "Server Error" });

         expect(callback).not.toHaveBeenCalled();
         expect(messageSpy).toHaveBeenCalledWith(
            modalService,
            "_#(js:Error)",
            "_#(js:data.dataModelFolder.moveItemError)");
      });

      it("[Risk 2] should post the move event and call the callback on success", () => {
         const item = makeAsset({ databaseName: "Reporting", name: "Logical A" });
         const callback = vi.fn();

         service.moveModelsToTarget([item], "Models/Archive", callback);

         const req = http.expectOne(MOVE_DATA_MODEL_URI);
         expect(req.request.method).toBe("POST");
         expect(req.request.body).toEqual({
            items: [item],
            folder: "Models/Archive",
            database: "Reporting",
         });

         req.flush({});

         expect(callback).toHaveBeenCalledTimes(1);
      });
   });

   // ---------------------------------------------------------------------------
   // Group 5 [Risk 3, 2, 2, 2] - add model routing and duplicate checks
   // ---------------------------------------------------------------------------
   describe("add model routing and duplicate checks", () => {
      it("[Risk 3] should use the logical-model duplicate endpoint when adding a logical model", () => {
         // Fixed Issue #75587: addLogicalModel() now passes PortalDataType.LOGIC_MODEL to
         // hasModelDuplicateCheck() instead of PortalDataType.VPM, so the dialog checks
         // against the logical-model duplicate endpoint instead of the VPM one.
         const dialog: any = {};
         vi.spyOn(ComponentTool, "showDialog").mockReturnValue(dialog);

         service.addLogicalModel("SalesDB");
         dialog.hasDuplicateCheck("Logical Model").subscribe();

         const requests = http.match(() => true);

         try {
            expect(requests).toHaveLength(1);
            expect(requests[0].request.url).toBe(LOGICAL_MODEL_DUPLICATE_URI);
         }
         finally {
            requests.forEach(req => req.flush(false));
         }
      });

      it("[Risk 2] should route a new VPM with byte-encoded database and model segments", () => {
         const commit = service.getAddModelCommit("Sales/DB", PortalDataType.VPM);

         commit({ name: "Model/One", description: "desc" });

         expect(router.navigate).toHaveBeenCalledWith([
            "/portal/tab/data/datasources/database",
            "vpm",
            Tool.byteEncode("Sales/DB") + "/" + Tool.byteEncode("Model/One"),
            { create: true, desc: "desc" },
         ], { relativeTo: route });
      });

      it("[Risk 2] should route a new physical model with encoded database and model path params", () => {
         const commit = service.getAddModelCommit("Sales/DB", PortalDataType.PARTITION, null, "Folder A");

         commit({ name: "Physical/View", description: "desc" });

         expect(router.navigate).toHaveBeenCalledWith([
            "/portal/tab/data/datasources/database",
            Tool.byteEncode("Sales/DB"),
            "physicalModel",
            Tool.byteEncode("Physical/View"),
            { create: true, desc: "desc", folder: "Folder A" },
         ]);
      });

      it("[Risk 2] should route a new logical model through the selected physical view with encoded params", () => {
         const commit = service.getAddModelCommit(
            "Sales/DB",
            PortalDataType.LOGIC_MODEL,
            "Physical/View",
            "Folder A");

         commit({ name: "Logical/Model", description: "desc" });

         expect(router.navigate).toHaveBeenCalledWith([
            "/portal/tab/data/datasources/database",
            Tool.byteEncode("Sales/DB"),
            "physicalModel",
            Tool.byteEncode("Physical/View"),
            "logicalModel",
            Tool.byteEncode("Logical/Model"),
            { create: true, desc: "desc", folder: "Folder A" },
         ]);
      });
   });

   // ---------------------------------------------------------------------------
   // Group 6 [Risk 3] - deletePhysicalView
   // ---------------------------------------------------------------------------
   describe("deletePhysicalView", () => {
      it("[Risk 3] should delete with parent/folder params only after confirmation and call callback on success", async () => {
         // Regression-sensitive: missing parent/folder params can delete the wrong physical model.
         const callback = vi.fn();
         vi.spyOn(ComponentTool, "showConfirmDialog").mockResolvedValue("ok");

         service.deletePhysicalView("Child/View", "SalesDB", "Parent/View", "Folder A", callback);

         await flushPromises();

         const req = http.expectOne(request => request.url === PHYSICAL_MODELS_URI);
         expect(req.request.method).toBe("DELETE");
         expect(req.request.params.get("database")).toBe("SalesDB");
         expect(req.request.params.get("name")).toBe("Child/View");
         expect(req.request.params.get("parent")).toBe("Parent/View");
         expect(req.request.params.get("folder")).toBe("Folder A");
         req.flush(true);

         expect(callback).toHaveBeenCalledTimes(1);
      });
   });

   // ---------------------------------------------------------------------------
   // Group 7 [Risk 3] - deleteLogicalModel
   // ---------------------------------------------------------------------------
   describe("deleteLogicalModel", () => {
      it("[Risk 3] should check dependencies, confirm, delete with folder params, and call callback", async () => {
         // Regression-sensitive: logical-model deletes first check outer dependencies; skipping
         // the check or folder param can remove the wrong model without warning.
         const callback = vi.fn();
         vi.spyOn(ComponentTool, "showConfirmDialog").mockResolvedValue("ok");

         service.deleteLogicalModel("Logical/Model", "SalesDB", [], null, "Folder A", callback);

         const depsReq = http.expectOne(LOGICAL_MODEL_CHECK_DEPENDENCIES_URI);
         expect(depsReq.request.body).toEqual(expect.objectContaining({
            databaseName: "SalesDB",
            modelName: "Logical/Model",
         }));
         depsReq.flush(null);

         await flushPromises();

         const deleteReq = http.expectOne(request => request.url === LOGICAL_MODELS_URI);
         expect(deleteReq.request.method).toBe("DELETE");
         expect(deleteReq.request.params.get("database")).toBe("SalesDB");
         expect(deleteReq.request.params.get("name")).toBe("Logical/Model");
         expect(deleteReq.request.params.get("folder")).toBe("Folder A");
         deleteReq.flush({});

         expect(callback).toHaveBeenCalledTimes(1);
      });
   });

   // ---------------------------------------------------------------------------
   // Group 8 [Risk 2] - getRenameModelCommit
   // ---------------------------------------------------------------------------
   describe("getRenameModelCommit", () => {
      it("[Risk 2] should send a trimmed logical-model rename event and call the callback on success", () => {
         const callback = vi.fn();
         const commit = service.getRenameModelCommit(
            "Old Name",
            "SalesDB",
            PortalDataType.LOGIC_MODEL,
            "Folder A",
            callback);

         commit({ name: "  New Name  ", description: "New description" });

         const req = http.expectOne(LOGICALMODEL_RENAME_URI);
         expect(req.request.method).toBe("PUT");
         expect(req.request.body).toEqual({
            database: "SalesDB",
            folder: "Folder A",
            oldName: "Old Name",
            newName: "New Name",
            description: "New description",
         });

         req.flush({});

         expect(callback).toHaveBeenCalledTimes(1);
      });
   });

   // ---------------------------------------------------------------------------
   // Group 9 [Risk 2] - createDataBaseAssets
   // ---------------------------------------------------------------------------
   describe("createDataBaseAssets", () => {
      it("[Risk 2] should map AssetEntry identity and display name into DatabaseAsset rows", () => {
         const assets = service.createDataBaseAssets([
            makeEntry("Root/Folder^_^Name", {
               identifier: "asset-id",
               createdUsername: "creator",
            }),
         ]);

         expect(assets).toEqual([
            expect.objectContaining({
               databaseName: "",
               id: "asset-id",
               path: "Root/Folder^_^Name",
               name: "Folder/Name",
               createdBy: "creator",
               editable: false,
               deletable: false,
            }),
         ]);
      });
   });

   // ---------------------------------------------------------------------------
   // Group 10 [Risk 2] - hasModelDuplicateCheck
   // ---------------------------------------------------------------------------
   describe("hasModelDuplicateCheck", () => {
      it("[Risk 2] should return false without HTTP when the entered name equals the original name", () => {
         let duplicate: boolean = null;

         service.hasModelDuplicateCheck("Existing", "SalesDB", PortalDataType.LOGIC_MODEL)("Existing")
            .subscribe(value => duplicate = value);

         expect(duplicate).toBe(false);
         http.expectNone(LOGICAL_MODEL_DUPLICATE_URI);
      });
   });
});
