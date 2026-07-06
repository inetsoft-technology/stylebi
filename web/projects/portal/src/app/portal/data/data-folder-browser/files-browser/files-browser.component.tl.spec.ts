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
 * FilesBrowserComponent - single-pass
 *
 * Risk-first coverage:
 *   Group 1 [Risk 3] - openFolder: browserView replacement, current folder name update,
 *                      newest-file auto selection, and empty-folder reset
 *   Group 2 [Risk 2] - openFolder error branch: ComponentTool.showMessageDialog
 *   Group 3 [Risk 1] - ngOnInit initView gate and openParentFolder breadcrumb navigation
 *   Group 4 [Risk 1] - selectFolder / selectFile single and multi select toggles
 *   Group 5 [Risk 1] - isItemSelected / getFolderIcon / getFileIcon / notifySelectionChange
 */

import { ComponentTool } from "../../../../common/util/component-tool";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { of, throwError } from "rxjs";
import { AssetType } from "../../../../../../../shared/data/asset-type";
import { DataFolderBrowserModel } from "../../model/data-folder-browser-model";
import { FilesBrowserComponent } from "./files-browser.component";
import { WorksheetBrowserInfo } from "../../model/worksheet-browser-info";

afterEach(() => vi.restoreAllMocks());

interface FilesBrowserTestOverrides {
   browserView?: Partial<DataFolderBrowserModel>;
   selectedFolders?: WorksheetBrowserInfo[];
   selectedFiles?: WorksheetBrowserInfo[];
   folderSelectable?: boolean;
   showBreadcrumb?: boolean;
   multiSelect?: boolean;
   openFolderPath?: string;
   openFolderScope?: number;
   openFolderRequest?: (path: string, assetType?: string, scope?: number) => any;
   openFolderError?: string;
   initView?: boolean;
   breadcrumbTooltip?: string;
}

function makeItem(overrides: Partial<WorksheetBrowserInfo> = {}): WorksheetBrowserInfo {
   return {
      name: "Item",
      path: "/Item",
      type: AssetType.WORKSHEET,
      scope: 1,
      description: "",
      createdBy: "user",
      createdDate: 1,
      createdDateLabel: "",
      modifiedDate: 1,
      modifiedDateLabel: "",
      dateFormat: "yyyy-MM-dd",
      editable: false,
      deletable: false,
      materialized: false,
      canMaterialize: false,
      canWorksheet: false,
      ...overrides,
   };
}

function makeFolder(overrides: Partial<WorksheetBrowserInfo> = {}): WorksheetBrowserInfo {
   return makeItem({
      type: AssetType.FOLDER,
      hasSubFolder: 1,
      ...overrides,
   });
}

function makeBrowserView(overrides: Partial<DataFolderBrowserModel> = {}): DataFolderBrowserModel {
   const root = makeFolder({ path: "/", name: "root", description: "root", hasSubFolder: 1 });
   return {
      path: [root],
      root: true,
      folders: [],
      files: [],
      ...overrides,
   };
}

function createComponent(overrides: FilesBrowserTestOverrides = {}) {
   const modalService = { open: vi.fn() } as unknown as NgbModal;
   const comp = new FilesBrowserComponent(modalService);
   comp.browserView = makeBrowserView(overrides.browserView);
   comp.selectedFolders = overrides.selectedFolders ?? [];
   comp.selectedFiles = overrides.selectedFiles ?? [];
   comp.folderSelectable = overrides.folderSelectable ?? false;
   comp.showBreadcrumb = overrides.showBreadcrumb ?? true;
   comp.multiSelect = overrides.multiSelect ?? false;
   comp.openFolderPath = overrides.openFolderPath;
   comp.openFolderScope = overrides.openFolderScope;
   comp.openFolderRequest = overrides.openFolderRequest ?? vi.fn().mockReturnValue(of(makeBrowserView()));
   comp.openFolderError = overrides.openFolderError ?? "_#(js:admin.status.error)";
   comp.initView = overrides.initView ?? true;
   comp.breadcrumbTooltip = overrides.breadcrumbTooltip ?? null;

   return {
      comp,
      modalService,
   };
}

describe("FilesBrowserComponent", () => {
   describe("openFolder", () => {
      it("should replace browserView, update current folder name, and auto-select the newest file", () => {
         const oldFile = makeItem({ path: "/old", name: "Old", createdDate: 10 });
         const newFile = makeItem({ path: "/new", name: "New", createdDate: 50 });
         const folders = [makeFolder({ path: "/folder", name: "Folder" })];
         const view = makeBrowserView({
            path: [makeFolder({ path: "/", name: "root" }), makeFolder({ path: "/reports", name: "Reports" })],
            folders,
            files: [oldFile, newFile],
         });
         const openFolderRequest = vi.fn().mockReturnValue(of(view));
         const { comp } = createComponent({ openFolderRequest });
         const emitSpy = vi.spyOn(comp.selectionChange, "emit");

         comp.openFolder("/reports", undefined, 3);

         expect(comp.browserView).toBe(view);
         expect(comp.currentFolderName).toBe("Reports");
         expect(comp.selectedFolders).toEqual([]);
         expect(comp.selectedFiles).toEqual([newFile]);
         expect(emitSpy).toHaveBeenCalledWith([newFile]);
      });

      it("should clear selection when the opened folder has no folders or files", () => {
         const view = makeBrowserView({
            path: [makeFolder({ path: "/", name: "root" }), makeFolder({ path: "/empty", name: "Empty" })],
            folders: [],
            files: [],
         });
         const { comp } = createComponent({
            selectedFolders: [makeFolder({ path: "/old-folder" })],
            selectedFiles: [makeItem({ path: "/old-file" })],
            openFolderRequest: vi.fn().mockReturnValue(of(view)),
         });
         const emitSpy = vi.spyOn(comp.selectionChange, "emit");

         comp.openFolder("/empty", undefined, 1);

         expect(comp.selectedFolders).toEqual([]);
         expect(comp.selectedFiles).toEqual([]);
         expect(emitSpy).toHaveBeenCalledWith([]);
      });
   });

   describe("Error handling", () => {
      it("should show the error dialog when openFolderRequest fails", () => {
         const dialogSpy = vi.spyOn(ComponentTool, "showMessageDialog").mockResolvedValue("ok");
         const { comp, modalService } = createComponent({
            openFolderRequest: vi.fn().mockReturnValue(throwError(() => new Error("boom"))),
         });

         comp.openFolder("/missing", undefined, 1);

         expect(dialogSpy).toHaveBeenCalledWith(
            modalService,
            "_#(js:admin.status.error)",
            "_#(js:admin.status.error)",
         );
      });
   });

   describe("Lifecycle and navigation", () => {
      it("should open the root folder on init when initView is true", () => {
         const openFolderRequest = vi.fn().mockReturnValue(of(makeBrowserView()));
         const { comp } = createComponent({ openFolderRequest, initView: true, openFolderPath: "/root" });

         comp.ngOnInit();

         expect(openFolderRequest).toHaveBeenCalledWith("/root", null, undefined);
      });

      it("should skip init loading when initView is false", () => {
         const openFolderRequest = vi.fn().mockReturnValue(of(makeBrowserView()));
         const { comp } = createComponent({ openFolderRequest, initView: false });

         comp.ngOnInit();

         expect(openFolderRequest).not.toHaveBeenCalled();
      });

      it("should open the parent folder from the breadcrumb trail", () => {
         const openFolderRequest = vi.fn().mockReturnValue(of(makeBrowserView()));
         const { comp } = createComponent({ openFolderRequest });
         const parent = makeFolder({ path: "/parent", name: "Parent" });
         const child = makeFolder({ path: "/child", name: "Child" });
         comp.browserView = makeBrowserView({ path: [parent, child] });

         comp.openParentFolder();

         expect(openFolderRequest).toHaveBeenCalledWith("/parent", AssetType.FOLDER, 1);
      });
   });

   describe("Selection toggles", () => {
      it("should replace selected folders and clear files in single-select mode", () => {
         const { comp } = createComponent();
         const folder = makeFolder({ path: "/folder", name: "Folder" });

         comp.selectedFiles = [makeItem({ path: "/file" })];
         comp.selectFolder(folder);

         expect(comp.selectedFolders).toEqual([folder]);
         expect(comp.selectedFiles).toEqual([]);
      });

      it("should toggle folder selections by path in multi-select mode", () => {
         const { comp } = createComponent({ multiSelect: true });
         const folder = makeFolder({ path: "/folder", name: "Folder" });
         const emitSpy = vi.spyOn(comp.selectionChange, "emit");

         comp.selectFolder(folder);
         comp.selectFolder(makeFolder({ path: "/folder", name: "Folder copy" }));

         expect(comp.selectedFolders).toEqual([]);
         expect(emitSpy).toHaveBeenCalledWith([]);
      });

      it("should replace selected files and clear folders in single-select mode", () => {
         const { comp } = createComponent();
         const file = makeItem({ path: "/file", name: "File" });

         comp.selectedFolders = [makeFolder({ path: "/folder" })];
         comp.selectFile(file);

         expect(comp.selectedFiles).toEqual([file]);
         expect(comp.selectedFolders).toEqual([]);
      });

      it("should toggle file selections by path in multi-select mode", () => {
         const { comp } = createComponent({ multiSelect: true });
         const file = makeItem({ path: "/file", name: "File" });

         comp.selectFile(file);
         comp.selectFile(makeItem({ path: "/file", name: "File copy" }));

         expect(comp.selectedFiles).toEqual([]);
      });
   });

   describe("Getters", () => {
      it("should report selected state for matching folders and files", () => {
         const { comp } = createComponent();
         const folder = makeFolder({ path: "/folder", scope: 1 });
         const file = makeItem({ path: "/file", scope: 1 });
         comp.selectedFolders = [folder];
         comp.selectedFiles = [file];

         expect(comp.isItemSelected(folder, true)).toBe(true);
         expect(comp.isItemSelected(file, false)).toBe(true);
         expect(comp.isItemSelected(makeFolder({ path: "/other", scope: 1 }), true)).toBe(false);
         expect(comp.isItemSelected(makeItem({ path: "/other-file", scope: 1 }), false)).toBe(false);
      });

      it("should return the configured folder and file icons", () => {
         const { comp } = createComponent({});
         comp.folderIcon = "custom-folder";
         comp.fileIcon = "custom-file";

         expect(comp.getFolderIcon(makeFolder())).toBe("custom-folder");
         expect(comp.getFileIcon(makeItem())).toBe("custom-file");
      });

      it("should emit the combined selection list", () => {
         const { comp } = createComponent();
         const folder = makeFolder({ path: "/folder" });
         const file = makeItem({ path: "/file" });
         comp.selectedFolders = [folder];
         comp.selectedFiles = [file];
         const emitSpy = vi.spyOn(comp.selectionChange, "emit");

         comp.notifySelectionChange();

         expect(emitSpy).toHaveBeenCalledWith([folder, file]);
      });
   });
});
