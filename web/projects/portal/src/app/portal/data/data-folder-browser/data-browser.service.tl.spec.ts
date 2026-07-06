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
 * DataBrowserService - unit tests
 *
 * Risk-first coverage (4 groups, 9 cases):
 *   Group 1 [Risk 2]       - changeMV / changeFolder (2 cases)
 *   Group 2 [Risk 2, 2]    - open composer flows (2 cases)
 *   Group 3 [Risk 3, 3, 2] - renameAsset (3 cases)
 *   Group 4 [Risk 3, 2]    - deleteAsset (2 cases)
 *
 * Confirmed bugs (it.failing - remove wrapper once fixed):
 *   - none
 *
 * KEY contracts:
 *   - Public MV/folder change methods emit the payloads callers subscribe to.
 *   - Composer launch flows send worksheet/folder parameters correctly when composer is not open.
 *   - Rename commits check duplicates before posting the rename request.
 *   - Duplicate rename responses stop the flow without reporting success; rename errors report danger.
 *   - Corrupt worksheet deletion asks for explicit force confirmation before retrying.
 *   - Folder deletion reports the folder-specific success callback.
 *
 * Design gaps:
 *   - Optional catchError callbacks are not covered because their return Observable contract is
 *     undocumented and currently caller-defined.
 *   - Composer-already-open message branches are shallow collaborator delegation and are skipped.
 */

import { HttpClientTestingModule, HttpTestingController } from "@angular/common/http/testing";
import { TestBed } from "@angular/core/testing";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { of } from "rxjs";

import { AssetType } from "../../../../../../shared/data/asset-type";
import { Tool } from "../../../../../../shared/util/tool";
import { AssetEntryHelper } from "../../../common/data/asset-entry-helper";
import { OpenComposerService } from "../../../common/services/open-composer.service";
import { ComponentTool } from "../../../common/util/component-tool";
import { GuiTool } from "../../../common/util/gui-tool";
import { PortalDataType } from "../data-navigation-tree/portal-data-type";
import { WorksheetBrowserInfo } from "../model/worksheet-browser-info";
import { DataBrowserService } from "./data-browser.service";

const FOLDER_URI = "../api/data/folders";
const DATA_URI = "../api/data/datasets";

function makeWorksheet(overrides: Partial<WorksheetBrowserInfo> = {}): WorksheetBrowserInfo {
   return {
      name: "Sales Sheet",
      path: "Folder/Sales Sheet",
      type: AssetType.WORKSHEET,
      scope: 1,
      description: "",
      createdBy: "admin",
      createdDate: 0,
      createdDateLabel: "",
      modifiedDate: 0,
      dateFormat: "YYYY-MM-DD HH:mm:ss",
      modifiedDateLabel: "",
      editable: true,
      deletable: true,
      materialized: false,
      canMaterialize: true,
      canWorksheet: true,
      ...overrides,
   };
}

async function flushPromises(): Promise<void> {
   await Promise.resolve();
}

describe("DataBrowserService", () => {
   let service: DataBrowserService;
   let http: HttpTestingController;
   let modalService: NgbModal;

   beforeEach(() => {
      modalService = {} as NgbModal;

      TestBed.configureTestingModule({
         imports: [HttpClientTestingModule],
         providers: [
            DataBrowserService,
            {
               provide: OpenComposerService,
               useValue: { composerOpen: of(false) },
            },
            { provide: NgbModal, useValue: modalService },
         ],
      });

      service = TestBed.inject(DataBrowserService);
      http = TestBed.inject(HttpTestingController);
   });

   afterEach(() => {
      http.verify();
      vi.restoreAllMocks();
      TestBed.resetTestingModule();
   });

   // ---------------------------------------------------------------------------
   // Group 1 [Risk 2] - changeMV / changeFolder
   // ---------------------------------------------------------------------------
   describe("changeMV / changeFolder", () => {
      it("[Risk 2] should emit mvChanged when materialized-view state changes", () => {
         const emitted: void[] = [];
         service.mvChanged.subscribe(value => emitted.push(value));

         service.changeMV();

         expect(emitted).toHaveLength(1);
      });

      it("[Risk 2] should emit private vs shared worksheet folder payloads based on scope", () => {
         const emitted: any[] = [];
         service.folderChanged.subscribe(value => emitted.push(value));

         service.changeFolder("My Worksheets", AssetEntryHelper.USER_SCOPE);
         service.changeFolder("Shared Worksheets", AssetEntryHelper.GLOBAL_SCOPE);

         expect(emitted).toEqual([
            {
               path: "My Worksheets",
               type: PortalDataType.PRIVATE_WORKSHEETS_FOLDER,
            },
            {
               path: "Shared Worksheets",
               type: PortalDataType.SHARED_WORKSHEETS_FOLDER,
            },
         ]);
      });
   });

   // ---------------------------------------------------------------------------
   // Group 2 [Risk 2, 2] - open composer flows
   // ---------------------------------------------------------------------------
   describe("open composer flows", () => {
      it("[Risk 2] should open a browser composer tab with the decoded worksheet id when composer is closed", () => {
         const browserSpy = vi.spyOn(GuiTool, "openBrowserTab")
            .mockImplementation(() => null as any);
         const client = { sendEvent: vi.fn() };

         service.openWorksheet("Folder%2FSales%20Sheet", client as any);

         expect(client.sendEvent).not.toHaveBeenCalled();
         expect(browserSpy).toHaveBeenCalledWith("composer", expect.anything());
         expect(browserSpy.mock.calls[0][1].get("wsId")).toBe("Folder/Sales Sheet");
      });

      it("[Risk 2] should open the worksheet wizard with an encoded folder when composer is closed", () => {
         const browserSpy = vi.spyOn(GuiTool, "openBrowserTab")
            .mockImplementation(() => null as any);

         service.newWorksheet("Folder/With Space");

         expect(browserSpy).toHaveBeenCalledWith("composer", expect.anything());
         expect(browserSpy.mock.calls[0][1].get("wsWizard")).toBe("true");
         expect(browserSpy.mock.calls[0][1].get("folder")).toBe(Tool.byteEncode("Folder/With Space"));
      });
   });

   // ---------------------------------------------------------------------------
   // Group 3 [Risk 3, 3, 2] - renameAsset
   // ---------------------------------------------------------------------------
   describe("renameAsset", () => {
      it("[Risk 3] should stop before rename and avoid success callback when the duplicate check returns duplicate=true", () => {
         // Regression-sensitive: bypassing the duplicate gate lets users submit a rename that the
         // server will reject later, after the dialog has already closed.
         let commit: (value: string) => void;
         vi.spyOn(ComponentTool, "showDialog").mockImplementation(
            (_modal: any, _dialogType: any, onCommit: (value: string) => void) => {
               commit = onCommit;
               return {} as any;
            });
         const responseHandler = vi.fn();
         const asset = makeWorksheet();

         service.renameAsset(asset, responseHandler);
         commit("Existing Name");

         const duplicateReq = http.expectOne(DATA_URI + "/isDuplicate");
         expect(duplicateReq.request.body).toEqual({
            path: "Folder/Sales Sheet",
            newName: "Existing Name",
            type: AssetType.WORKSHEET,
            scope: 1,
         });
         duplicateReq.flush({ duplicate: true });

         http.expectNone(req => req.url.includes("/rename/"));
         expect(responseHandler).not.toHaveBeenCalled();
      });

      it("[Risk 2] should check duplicates, post the encoded rename URL, and report success", () => {
         let commit: (value: string) => void;
         vi.spyOn(ComponentTool, "showDialog").mockImplementation(
            (_modal: any, _dialogType: any, onCommit: (value: string) => void) => {
               commit = onCommit;
               return {} as any;
            });
         const responseHandler = vi.fn();
         const preFun = vi.fn();
         const asset = makeWorksheet();

         service.renameAsset(asset, responseHandler, undefined, preFun);
         commit("Renamed Sheet");

         const duplicateReq = http.expectOne(DATA_URI + "/isDuplicate");
         expect(preFun).toHaveBeenCalledTimes(1);
         duplicateReq.flush({ duplicate: false });

         const renameReq = http.expectOne(DATA_URI + "/rename/Renamed%20Sheet");
         expect(renameReq.request.method).toBe("POST");
         expect(renameReq.request.body).toBe(asset);
         renameReq.flush({});

         expect(responseHandler).toHaveBeenCalledWith(
            "success",
            "_#(js:data.datasets.renameSuccess)");
      });

      it("[Risk 3] should report danger when the rename request fails after duplicate check passes", () => {
         // Regression-sensitive: the dialog commit has already passed duplicate validation; a
         // server-side rename failure must still be surfaced to the caller.
         let commit: (value: string) => void;
         vi.spyOn(ComponentTool, "showDialog").mockImplementation(
            (_modal: any, _dialogType: any, onCommit: (value: string) => void) => {
               commit = onCommit;
               return {} as any;
            });
         const responseHandler = vi.fn();
         const asset = makeWorksheet();

         service.renameAsset(asset, responseHandler);
         commit("Renamed Sheet");

         http.expectOne(DATA_URI + "/isDuplicate").flush({ duplicate: false });
         const renameReq = http.expectOne(DATA_URI + "/rename/Renamed%20Sheet");
         renameReq.flush({}, { status: 500, statusText: "Server Error" });

         expect(responseHandler).toHaveBeenCalledWith(
            "danger",
            "_#(js:data.datasets.renameError)");
      });
   });

   // ---------------------------------------------------------------------------
   // Group 4 [Risk 3, 2] - deleteAsset
   // ---------------------------------------------------------------------------
   describe("deleteAsset", () => {
      it("[Risk 3] should require a second confirmation before force-deleting a corrupt worksheet", async () => {
         // Regression-sensitive: corrupt worksheet deletion is destructive; the force retry must
         // only happen after the user confirms the corruption-specific warning.
         const responseHandler = vi.fn();
         const confirmSpy = vi.spyOn(ComponentTool, "showConfirmDialog")
            .mockResolvedValueOnce("ok")
            .mockResolvedValueOnce("ok");
         const asset = makeWorksheet({ path: "Folder/Sales Sheet", scope: 4 });

         service.deleteAsset(asset, responseHandler);

         const statusReq = http.expectOne(req =>
            req.url === DATA_URI + "/removeableStatus/Folder/Sales%20Sheet" &&
            req.params.get("scope") === "4");
         expect(statusReq.request.params.get("scope")).toBe("4");
         statusReq.flush({ dependencies: ["Dashboard A"] });

         await flushPromises();

         expect(confirmSpy).toHaveBeenNthCalledWith(
            1,
            modalService,
            "_#(js:data.datasets.delete)",
            expect.stringContaining("Dashboard A"));

         const firstDelete = http.expectOne(req =>
            req.url === DATA_URI + "/Folder/Sales%20Sheet" &&
            req.params.get("scope") === "4" &&
            req.params.get("force") === null);
         firstDelete.flush({ successful: false, corrupt: true });

         await flushPromises();

         expect(confirmSpy).toHaveBeenNthCalledWith(
            2,
            modalService,
            "_#(js:data.datasets.delete)",
            "_#(js:data.datasets.deleteWorksheetCorrupt)");

         const forceDelete = http.expectOne(req =>
            req.url === DATA_URI + "/Folder/Sales%20Sheet" &&
            req.params.get("scope") === "4" &&
            req.params.get("force") === "true");
         forceDelete.flush({ successful: true, corrupt: false });

         expect(responseHandler).toHaveBeenCalledWith(
            "success",
            "_#(js:data.datasets.deleteWorksheetSuccess)");
      });

      it("[Risk 2] should delete a folder after confirmation and report folder-specific success", async () => {
         const responseHandler = vi.fn();
         vi.spyOn(ComponentTool, "showConfirmDialog").mockResolvedValue("ok");
         const asset = makeWorksheet({
            type: AssetType.FOLDER,
            path: "Parent/Folder A",
            scope: 1,
         });

         service.deleteAsset(asset, responseHandler);

         const statusReq = http.expectOne(req =>
            req.url === FOLDER_URI + "/removeableStatus/Parent/Folder%20A" &&
            req.params.get("scope") === "1");
         statusReq.flush({ dependencies: [] });

         await flushPromises();

         const deleteReq = http.expectOne(req =>
            req.url === FOLDER_URI + "/Parent/Folder%20A" &&
            req.params.get("scope") === "1");
         expect(deleteReq.request.method).toBe("DELETE");
         deleteReq.flush({});

         expect(responseHandler).toHaveBeenCalledWith(
            "success",
            "_#(js:data.datasets.deleteFolderSuccess)");
      });
   });
});
