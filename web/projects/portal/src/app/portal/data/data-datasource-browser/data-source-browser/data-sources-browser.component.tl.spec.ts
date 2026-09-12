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
 * DataSourcesBrowser — single pass
 *
 * Risk-first coverage:
 *   Group 1 [Risk 3] — openFolder (non-init): resets selection and auto-selects most-recent file
 *                       by createdDate; old selection and folders are cleared regardless of result
 *   Group 2 [Risk 2] — openFolder error: delegates to ComponentTool.showMessageDialog
 *   Group 3 [Risk 2] — selectFolder multiSelect: toggles by path comparison (NOT object identity)
 *   Group 4 [Risk 2] — selectFile multiSelect: toggles by path comparison (NOT object identity)
 *   Group 5 [Risk 1] — ngOnInit: calls openFolder with root path when initView=true; skips when false
 *   Group 6 [Risk 1] — dblclickFolder: returns early when hasSubFolder=false; calls openFolder otherwise
 *   Group 7 [Risk 1] — openParentFolder: opens second-to-last breadcrumb path
 *   Group 8 [Risk 1] — selectFolder singular: replaces selectedFolders, clears selectedFiles
 *   Group 9 [Risk 1] — selectFile singular: replaces selectedFiles, clears selectedFolders
 *   Group 10 [Risk 1] — isItemSelected: true/false for folder and file arrays
 *   Group 11 [Risk 1] — getFolderName / getFolderIcon / getFileIcon
 *
 * Out of scope:
 *   notifySelectionChange — covered transitively through selectFolder, selectFile, openFolder tests
 *   private updateCurrentFolderName — covered transitively through openFolder tests
 *   Memory leak: DataSourcesBrowser does not implement OnDestroy; openFolderRequest subscriptions
 *     are fire-and-forget with no cleanup mechanism.
 */

import { NO_ERRORS_SCHEMA } from "@angular/core";
import { render } from "@testing-library/angular";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { of, throwError } from "rxjs";
import { Subject } from "rxjs";

import { ComponentTool } from "../../../../common/util/component-tool";
import { DataSourcesBrowser } from "./data-sources-browser.component";
import { DataSourceInfo } from "../../model/data-source-info";
import { DataSourceBrowserViewModel } from "../../model/data-source-browser-view-model";

// ---------------------------------------------------------------------------
// Fixtures
// ---------------------------------------------------------------------------

const MODAL_MOCK = {
   open: vi.fn().mockImplementation(() => ({
      result: new Promise<any>(() => {}),
      componentInstance: { onCommit: new Subject<string>() },
      close: vi.fn(),
      dismiss: vi.fn(),
   })),
};

function makeFolder(name: string, path: string): DataSourceInfo {
   return {
      name, path,
      type: { name: "DATA_SOURCE_FOLDER", label: "Folder" },
      createdBy: "", createdDate: 0, createdDateLabel: "", dateFormat: "",
      editable: false, deletable: false,
   };
}

function makeFile(name: string, path: string, createdDate = 1000): DataSourceInfo {
   return {
      name, path,
      type: { name: "DATA_SOURCE", label: "DataSource" },
      createdBy: "", createdDate, createdDateLabel: "", dateFormat: "",
      editable: false, deletable: false,
   };
}

function makeView(override: Partial<DataSourceBrowserViewModel> = {}): DataSourceBrowserViewModel {
   return { path: [makeFolder("root", "/")], root: true, folders: [], files: [], ...override };
}

async function renderBrowser(inputs: Partial<any> = {}) {
   const defaultRequest = vi.fn().mockReturnValue(of(makeView()));
   const { fixture } = await render(DataSourcesBrowser, {
      providers: [{ provide: NgbModal, useValue: MODAL_MOCK }],
      schemas: [NO_ERRORS_SCHEMA],
      componentInputs: {
         initView: false,
         openFolderRequest: defaultRequest,
         rootLabel: "Data Source",
         ...inputs,
      },
   });
   return { comp: fixture.componentInstance as DataSourcesBrowser, defaultRequest };
}

beforeEach(() => {
   MODAL_MOCK.open.mockClear();
});

afterEach(() => vi.restoreAllMocks());

// ---------------------------------------------------------------------------
// Group 1 — openFolder non-init: auto-selection by createdDate
// ---------------------------------------------------------------------------

describe("Group 1 — openFolder non-init: auto-selects most recent file", () => {
   // 🔁 Regression-sensitive: sort is descending by createdDate so files[0] is the newest;
   //    any sort-direction reversal silently picks the oldest file instead.
   it("should select the file with the highest createdDate after non-init folder open", async () => {
      const fileOld = makeFile("Old", "/old", 100);
      const fileNew = makeFile("New", "/new", 500);
      const view = makeView({ files: [fileOld, fileNew] });
      const { comp } = await renderBrowser({ openFolderRequest: vi.fn().mockReturnValue(of(view)) });

      comp.openFolder("/", null, false);

      expect(comp.selectedFiles).toEqual([fileNew]);
      expect(comp.selectedFolders).toEqual([]);
   });

   it("should leave selectedFiles empty when all files have createdDate=0 (filter treats 0 as falsy)", async () => {
      const fileZero = makeFile("Zero", "/z", 0);
      const view = makeView({ files: [fileZero] });
      const { comp } = await renderBrowser({ openFolderRequest: vi.fn().mockReturnValue(of(view)) });

      comp.openFolder("/", null, false);

      expect(comp.selectedFiles).toEqual([]);
   });

   it("should update browserView and currentFolderName", async () => {
      const child = makeFolder("Reports", "/reports");
      const view = makeView({ path: [makeFolder("root", "/"), child], folders: [child] });
      const { comp } = await renderBrowser({ openFolderRequest: vi.fn().mockReturnValue(of(view)) });

      comp.openFolder("/reports", null, false);

      expect(comp.browserView).toBe(view);
      expect(comp.currentFolderName).toBe("Reports");
   });

   it("should emit selectionChange with combined folders and files", async () => {
      const fileRecent = makeFile("Recent", "/recent", 1000);
      const view = makeView({ files: [fileRecent] });
      const { comp } = await renderBrowser({ openFolderRequest: vi.fn().mockReturnValue(of(view)) });
      const emitSpy = vi.spyOn(comp.selectionChange, "emit");

      comp.openFolder("/", null, false);

      expect(emitSpy).toHaveBeenCalledWith([fileRecent]);
   });

   it("should NOT reset selection or re-select files when onInit=true", async () => {
      const file = makeFile("F", "/f", 1000);
      const view = makeView({ files: [file] });
      const { comp } = await renderBrowser({ openFolderRequest: vi.fn().mockReturnValue(of(view)) });

      comp.selectedFiles = [makeFile("Prev", "/prev", 50)];
      comp.openFolder("/", null, true);  // onInit=true → no selection reset

      // browserView updates but selection is untouched
      expect(comp.selectedFiles).toEqual([makeFile("Prev", "/prev", 50)]);
   });
});

// ---------------------------------------------------------------------------
// Group 2 — openFolder error
// ---------------------------------------------------------------------------

describe("Group 2 — openFolder error: shows error dialog", () => {
   it("should call showMessageDialog with the error title when openFolderRequest errors", async () => {
      const dialogSpy = vi.spyOn(ComponentTool, "showMessageDialog").mockResolvedValue("ok");
      const { comp } = await renderBrowser({
         openFolderRequest: vi.fn().mockReturnValue(throwError(() => new Error("Network error"))),
      });

      comp.openFolder("/", null, false);

      expect(dialogSpy).toHaveBeenCalledWith(
         expect.anything(),
         "_#(js:admin.status.error)",
         expect.any(String)
      );
   });
});

// ---------------------------------------------------------------------------
// Group 3 — selectFolder multiSelect: toggles by path
// ---------------------------------------------------------------------------

describe("Group 3 — selectFolder multiSelect: toggles by path (not object identity)", () => {
   // 🔁 Regression-sensitive: multiSelect uses findIndex by path because @Input() selectedFolders
   //    may hold references distinct from the items displayed in the template.
   it("should add a folder to selectedFolders when not already present", async () => {
      const { comp } = await renderBrowser({ multiSelect: true });
      const folder = makeFolder("A", "/a");

      comp.selectFolder(folder);

      expect(comp.selectedFolders).toEqual([folder]);
   });

   it("should remove a folder from selectedFolders when already present", async () => {
      const { comp } = await renderBrowser({ multiSelect: true });
      const folder = makeFolder("A", "/a");
      comp.selectedFolders = [folder];

      comp.selectFolder(folder);

      expect(comp.selectedFolders).toEqual([]);
   });

   it("should keep other folders when removing one", async () => {
      const { comp } = await renderBrowser({ multiSelect: true });
      const folderA = makeFolder("A", "/a");
      const folderB = makeFolder("B", "/b");
      comp.selectedFolders = [folderA, folderB];

      comp.selectFolder(folderA);

      expect(comp.selectedFolders).toEqual([folderB]);
   });
});

// ---------------------------------------------------------------------------
// Group 4 — selectFile multiSelect: toggles by path
// ---------------------------------------------------------------------------

describe("Group 4 — selectFile multiSelect: toggles by path (not object identity)", () => {
   it("should add a file to selectedFiles when not already present", async () => {
      const { comp } = await renderBrowser({ multiSelect: true });
      const file = makeFile("B", "/b");

      comp.selectFile(file);

      expect(comp.selectedFiles).toEqual([file]);
   });

   it("should remove a file from selectedFiles when already present", async () => {
      const { comp } = await renderBrowser({ multiSelect: true });
      const file = makeFile("B", "/b");
      comp.selectedFiles = [file];

      comp.selectFile(file);

      expect(comp.selectedFiles).toEqual([]);
   });
});

// ---------------------------------------------------------------------------
// Group 5 — ngOnInit: calls openFolder
// ---------------------------------------------------------------------------

describe("Group 5 — ngOnInit: calls openFolder when initView=true", () => {
   it("should call openFolderRequest with the root path on init when initView=true", async () => {
      const requestMock = vi.fn().mockReturnValue(of(makeView()));

      await render(DataSourcesBrowser, {
         providers: [{ provide: NgbModal, useValue: MODAL_MOCK }],
         schemas: [NO_ERRORS_SCHEMA],
         componentInputs: { initView: true, openFolderRequest: requestMock, openFolderPath: "/", rootLabel: "DS" },
      });

      expect(requestMock).toHaveBeenCalledWith("/", null);
   });

   it("should NOT call openFolderRequest when initView=false", async () => {
      const requestMock = vi.fn().mockReturnValue(of(makeView()));

      await render(DataSourcesBrowser, {
         providers: [{ provide: NgbModal, useValue: MODAL_MOCK }],
         schemas: [NO_ERRORS_SCHEMA],
         componentInputs: { initView: false, openFolderRequest: requestMock, rootLabel: "DS" },
      });

      expect(requestMock).not.toHaveBeenCalled();
   });
});

// ---------------------------------------------------------------------------
// Group 6 — dblclickFolder: respects hasSubFolder guard
// ---------------------------------------------------------------------------

describe("Group 6 — dblclickFolder: respects hasSubFolder", () => {
   it("should call openFolderRequest when hasSubFolder=true", async () => {
      const requestMock = vi.fn().mockReturnValue(of(makeView()));
      const { comp } = await renderBrowser({ openFolderRequest: requestMock });

      comp.dblclickFolder("/child", true);

      expect(requestMock).toHaveBeenCalledWith("/child", undefined);
   });

   it("should NOT call openFolderRequest when hasSubFolder=false", async () => {
      const requestMock = vi.fn().mockReturnValue(of(makeView()));
      const { comp } = await renderBrowser({ openFolderRequest: requestMock });

      comp.dblclickFolder("/leaf", false);

      expect(requestMock).not.toHaveBeenCalled();
   });
});

// ---------------------------------------------------------------------------
// Group 7 — openParentFolder: opens second-to-last breadcrumb entry
// ---------------------------------------------------------------------------

describe("Group 7 — openParentFolder: uses second-to-last path entry", () => {
   it("should call openFolderRequest with the parent path and type", async () => {
      const root = makeFolder("root", "/");
      const child = makeFolder("child", "/child");
      const requestMock = vi.fn().mockReturnValue(of(makeView({ path: [root, child] })));
      const { comp } = await renderBrowser({ openFolderRequest: requestMock });
      comp.browserView = makeView({ path: [root, child] });
      requestMock.mockClear();

      comp.openParentFolder();

      expect(requestMock).toHaveBeenCalledWith(root.path, root.type);
   });
});

// ---------------------------------------------------------------------------
// Group 8 — selectFolder singular: replaces selection
// ---------------------------------------------------------------------------

describe("Group 8 — selectFolder singular: replaces selection and clears files", () => {
   it("should set selectedFolders to the clicked folder and clear selectedFiles", async () => {
      const { comp } = await renderBrowser();
      const folderA = makeFolder("A", "/a");
      comp.selectedFiles = [makeFile("F", "/f")];
      comp.selectedFolders = [makeFolder("prev", "/prev")];

      comp.selectFolder(folderA);

      expect(comp.selectedFolders).toEqual([folderA]);
      expect(comp.selectedFiles).toEqual([]);
   });
});

// ---------------------------------------------------------------------------
// Group 9 — selectFile singular: replaces selection
// ---------------------------------------------------------------------------

describe("Group 9 — selectFile singular: replaces selection and clears folders", () => {
   it("should set selectedFiles to the clicked file and clear selectedFolders", async () => {
      const { comp } = await renderBrowser();
      const fileB = makeFile("B", "/b");
      comp.selectedFolders = [makeFolder("prev", "/prev")];
      comp.selectedFiles = [makeFile("prev", "/pf")];

      comp.selectFile(fileB);

      expect(comp.selectedFiles).toEqual([fileB]);
      expect(comp.selectedFolders).toEqual([]);
   });
});

// ---------------------------------------------------------------------------
// Group 10 — isItemSelected: path-based membership check
// ---------------------------------------------------------------------------

describe("Group 10 — isItemSelected: checks path membership", () => {
   it("should return true when folder path is in selectedFolders", async () => {
      const { comp } = await renderBrowser();
      const folder = makeFolder("F", "/f");
      comp.selectedFolders = [folder];
      expect(comp.isItemSelected(folder, true)).toBe(true);
   });

   it("should return false when folder path is NOT in selectedFolders", async () => {
      const { comp } = await renderBrowser();
      expect(comp.isItemSelected(makeFolder("F", "/f"), true)).toBe(false);
   });

   it("should return true when file path is in selectedFiles", async () => {
      const { comp } = await renderBrowser();
      const file = makeFile("Ds", "/ds");
      comp.selectedFiles = [file];
      expect(comp.isItemSelected(file, false)).toBe(true);
   });

   it("should return false when file path is NOT in selectedFiles", async () => {
      const { comp } = await renderBrowser();
      expect(comp.isItemSelected(makeFile("Ds", "/ds"), false)).toBe(false);
   });
});

// ---------------------------------------------------------------------------
// Group 11 — getFolderName / getFolderIcon / getFileIcon
// ---------------------------------------------------------------------------

describe("Group 11 — getFolderName / getFolderIcon / getFileIcon", () => {
   it("should return rootLabel when folder argument is null", async () => {
      const { comp } = await renderBrowser();
      expect(comp.getFolderName(null)).toBe("Data Source");
   });

   it("should return rootLabel when folder path is '/'", async () => {
      const { comp } = await renderBrowser();
      expect(comp.getFolderName(makeFolder("", "/"))).toBe("Data Source");
   });

   it("should return the folder name when present and path is not '/'", async () => {
      const { comp } = await renderBrowser();
      expect(comp.getFolderName(makeFolder("My Reports", "/my"))).toBe("My Reports");
   });

   it("should return the folderIcon input value", async () => {
      const { comp } = await renderBrowser({ folderIcon: "custom-folder-icon" });
      expect(comp.getFolderIcon(makeFolder("F", "/f"))).toBe("custom-folder-icon");
   });

   it("should return the fileIcon input value", async () => {
      const { comp } = await renderBrowser({ fileIcon: "custom-file-icon" });
      expect(comp.getFileIcon(makeFile("F", "/f"))).toBe("custom-file-icon");
   });
});
