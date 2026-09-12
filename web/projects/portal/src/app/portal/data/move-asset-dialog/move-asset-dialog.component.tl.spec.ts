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
 * MoveAssetDialogComponent - single pass
 *
 * Risk-first coverage:
 *   Group 1 [Risk 3] - ngOnInit/ngOnChanges/folderSelected: state derivation for folderPath,
 *                      folderScope, isFolder
 *   Group 2 [Risk 3] - openFolderRequest: scope/home/moveFolders params and folder filtering
 *   Group 3 [Risk 3] - ok(): duplicate=false commit, duplicate=true error dialog, multi/folder/
 *                      asset duplicate message branches
 *   Group 4 [Risk 1] - cancel(): emits cancel token
 *
 * Confirmed bugs (it.fails): none
 *
 * Mocking strategy:
 *   - direct HttpClient -> provideHttpClient() + MSW
 *   - heavy child component -> importOverrides stub for files-browser
 */

import { Component, Input, NO_ERRORS_SCHEMA, Output, EventEmitter, SimpleChange } from "@angular/core";
import { provideHttpClient } from "@angular/common/http";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { render, waitFor } from "@testing-library/angular";
import { http, HttpResponse } from "msw";
import { firstValueFrom, Subject } from "rxjs";

import { AssetType } from "../../../../../../shared/data/asset-type";
import { server } from "@test-mocks/server";
import { ComponentTool } from "../../../common/util/component-tool";
import { MoveAssetDialogDataConfig } from "../data-folder-browser/move-asset-dialog-data-config";
import { FilesBrowserComponent } from "../data-folder-browser/files-browser/files-browser.component";
import { WorksheetBrowserInfo } from "../model/worksheet-browser-info";
import { FAKE_ROOT_PATH, MoveAssetDialogComponent } from "./move-asset-dialog.component";

@Component({ selector: "files-browser", standalone: true, template: "" })
class FilesBrowserStub {
   @Input() folderSelectable: boolean;
   @Input() selectedFolders: WorksheetBrowserInfo[];
   @Input() openFolderRequest: any;
   @Input() openFolderError: string;
   @Input() openFolderPath: string;
   @Input() openFolderScope: number;
   @Output() selectionChange = new EventEmitter<WorksheetBrowserInfo[]>();
}

const MODAL_MOCK = {
   open: vi.fn().mockImplementation(() => ({
      result: new Promise<any>(() => {}),
      componentInstance: { onCommit: new Subject<string>() },
      close: vi.fn(),
      dismiss: vi.fn(),
   })),
};

function makeAsset(overrides: Partial<WorksheetBrowserInfo> = {}): WorksheetBrowserInfo {
   return {
      type: AssetType.WORKSHEET,
      path: "/Asset",
      name: "Asset",
      scope: 1,
      description: "",
      createdBy: "",
      createdDate: 0,
      createdDateLabel: "",
      modifiedDate: 0,
      modifiedDateLabel: "",
      dateFormat: "yyyy-MM-dd",
      editable: true,
      deletable: true,
      materialized: false,
      canMaterialize: false,
      canWorksheet: false,
      ...overrides,
   } as WorksheetBrowserInfo;
}

function makeFolder(overrides: Partial<WorksheetBrowserInfo> = {}): WorksheetBrowserInfo {
   return makeAsset({ type: AssetType.FOLDER, path: "/Folder", name: "Folder", ...overrides });
}

interface RenderOpts {
   originalPaths?: string[];
   parentPath?: string;
   parentScope?: number;
   grandparentFolder?: string;
   multi?: boolean;
   items?: WorksheetBrowserInfo[];
}

async function renderComp(opts: RenderOpts = {}) {
   const { fixture } = await render(MoveAssetDialogComponent, {
      providers: [
         provideHttpClient(),
         { provide: NgbModal, useValue: MODAL_MOCK },
         MoveAssetDialogDataConfig,
      ],
      schemas: [NO_ERRORS_SCHEMA],
      importOverrides: [{ replace: FilesBrowserComponent, with: FilesBrowserStub }],
      componentInputs: {
         originalPaths: opts.originalPaths ?? [],
         parentPath: opts.parentPath ?? "/",
         parentScope: opts.parentScope ?? 1,
         grandparentFolder: opts.grandparentFolder ?? "/",
         multi: opts.multi ?? false,
         items: opts.items ?? [],
      },
   });

   return { comp: fixture.componentInstance as MoveAssetDialogComponent, fixture };
}

afterEach(() => {
   vi.restoreAllMocks();
});

describe("Group 1 - state derivation", () => {
   it("should initialize folderPath and folderScope from parent inputs on ngOnInit", async () => {
      const { comp } = await renderComp({ parentPath: "/Parent", parentScope: 4 });

      expect(comp.folderPath).toBe("/Parent");
      expect(comp.folderScope).toBe(4);
   });

   it("should compute isFolder=true when multi is true", async () => {
      const { comp } = await renderComp({ multi: true, items: [] });

      expect(comp.isFolder).toBe(true);
   });

   it("should compute isFolder=true when the first item is a folder", async () => {
      const { comp } = await renderComp({ multi: false, items: [makeFolder()] });

      expect(comp.isFolder).toBe(true);
   });

   it("should recompute isFolder on ngOnChanges and clear selection when folderSelected receives []", async () => {
      const { comp } = await renderComp({ multi: false, items: [makeAsset()] });

      comp.items = [makeFolder({ path: "/Target", scope: 7 })];
      comp.ngOnChanges({ items: new SimpleChange([], comp.items, false) });
      expect(comp.isFolder).toBe(true);

      comp.folderSelected([]);
      expect(comp.folderPath).toBeNull();
      expect(comp.folderScope).toBeNull();
   });

   it("should set folderPath and folderScope from the first selected folder", async () => {
      const { comp } = await renderComp();
      const folder = makeFolder({ path: "/Target", scope: 8 });

      comp.folderSelected([folder]);

      expect(comp.folderPath).toBe("/Target");
      expect(comp.folderScope).toBe(8);
   });
});

describe("Group 2 - openFolderRequest", () => {
   it("should include scope, home, and moveFolders params and prepend the fake root folder", async () => {
      let requestUrl = "";
      server.use(
         http.get("*/api/portal/data/browser/*", ({ request }) => {
            requestUrl = request.url;
            return HttpResponse.json({
               currentFolder: [makeFolder({ path: "/Current" })],
               root: false,
               folders: [makeFolder({ path: "/Current" })],
               files: [makeAsset({ path: "/Current/Sheet" })],
            });
         }),
      );
      const { comp } = await renderComp({
         items: [makeAsset({ path: "/A" }), makeAsset({ path: "/B" })],
      });

      const result = await firstValueFrom(comp.openFolderRequest(FAKE_ROOT_PATH, undefined, 3));

      expect(requestUrl).toContain("scope=3");
      expect(requestUrl).toContain("home=true");
      expect(requestUrl).toContain("moveFolders=/A;/B");
      expect(result.path[0].path).toBe(FAKE_ROOT_PATH);
      expect(result.files).toEqual([]);
   });

   it("should filter out folders that are being moved in the same parent scope", async () => {
      server.use(
         http.get("*/api/portal/data/browser/*", () =>
            HttpResponse.json({
               currentFolder: [],
               root: true,
               folders: [
                  makeFolder({ path: "/Moving", scope: 1 }),
                  makeFolder({ path: "/Keep", scope: 1 }),
                  makeFolder({ path: "/Moving", scope: 2 }),
               ],
               files: [],
            }),
         ),
      );
      const { comp } = await renderComp({
         originalPaths: ["/Moving"],
         parentScope: 1,
         items: [makeFolder({ path: "/Moving" })],
      });

      const result = await firstValueFrom(comp.openFolderRequest("/Current", undefined, 1));

      expect(result.folders).toEqual([
         expect.objectContaining({ path: "/Keep", scope: 1 }),
         expect.objectContaining({ path: "/Moving", scope: 2 }),
      ]);
   });
});

describe("Group 3 - ok()", () => {
   it("should emit onCommit with [folderPath, folderScope] when duplicate=false", async () => {
      server.use(
         http.post("*/api/data/move/checkDuplicate", () => HttpResponse.json(false)),
      );
      const { comp } = await renderComp({
         items: [makeAsset({ path: "/Asset" })],
         parentScope: 1,
      });
      const commitSpy = vi.spyOn(comp.onCommit, "emit");
      comp.folderPath = "/Target";
      comp.folderScope = 5;

      comp.ok();

      await waitFor(() => expect(commitSpy).toHaveBeenCalledWith(["/Target", 5]));
   });

   it("should show the multi duplicate message when duplicate=true and multi is true", async () => {
      server.use(
         http.post("*/api/data/move/checkDuplicate", () => HttpResponse.json(true)),
      );
      const dialogSpy = vi.spyOn(ComponentTool, "showMessageDialog").mockResolvedValue("ok");
      const { comp } = await renderComp({
         multi: true,
         items: [makeAsset()],
      });
      comp.folderPath = "/Target";
      comp.folderScope = 2;

      comp.ok();

      await waitFor(() =>
         expect(dialogSpy).toHaveBeenCalledWith(
            MODAL_MOCK,
            "_#(js:Error)",
            "_#(js:common.duplicateName)",
         ),
      );
   });

   it("should still use the folder duplicate branch when moving a single folder", async () => {
      let capturedUrl = "";
      let capturedBody: any;
      server.use(
         http.post("*/api/data/move/checkDuplicate", async ({ request }) => {
            capturedUrl = request.url;
            capturedBody = await request.json();
            return HttpResponse.json(true);
         }),
      );
      const dialogSpy = vi.spyOn(ComponentTool, "showMessageDialog").mockResolvedValue("ok");
      const { comp } = await renderComp({
         items: [makeFolder({ path: "/FolderA" })],
         parentScope: 9,
      });
      comp.folderPath = "/Target";
      comp.folderScope = 4;

      comp.ok();

      await waitFor(() => expect(dialogSpy).toHaveBeenCalled());
      expect(capturedUrl).toContain("assetScope=9");
      expect(capturedUrl).toContain("targetScope=4");
      expect(capturedBody.path).toBe("/Target");
      expect(capturedBody.items[0].path).toBe("/FolderA");
   });
});

describe("Group 4 - cancel()", () => {
   it("should emit cancel", async () => {
      const { comp } = await renderComp();
      const cancelSpy = vi.spyOn(comp.onCancel, "emit");

      comp.cancel();

      expect(cancelSpy).toHaveBeenCalledWith("cancel");
   });
});
