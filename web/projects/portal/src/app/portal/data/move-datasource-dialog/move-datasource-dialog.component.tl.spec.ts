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
 * MoveDataSourceDialogComponent — single pass (+memory leak)
 *
 * Risk-first coverage:
 *   Group 1 [Risk 3] — ok(): duplicate name found → shows error dialog; no duplicate → emits onCommit
 *   Group 2 [Risk 2] — openFolderRequest: filters out non-folder items and items in originalPaths;
 *                       FAKE_ROOT_PATH sets root=true param; non-root paths set path param
 *   Group 3 [Risk 2] — ngOnChanges: recomputes isFolder when multi or items change
 *   Group 4 [Risk 1] — ngOnInit: sets folderPath=null, folderScope=parentScope, computes isFolder
 *   Group 5 [Risk 1] — folderSelected: empty → clears folderPath/scope; non-empty → sets folderPath
 *   Group 6 [Risk 1] — rootLabel getter: returns i18n key
 *   Group 7 [Risk 1] — cancel: emits onCancel
 *   Group 8 [Risk 1] — memory leak: ok() subscription is fire-and-forget with no stored reference
 *
 * Out of scope:
 *   private computeIsFolder — covered transitively via ngOnInit and ngOnChanges tests
 *   fakeRootFolder constant — pure data; tested transitively via openFolderRequest path test
 */

import { NO_ERRORS_SCHEMA, SimpleChange } from "@angular/core";
import { provideHttpClient } from "@angular/common/http";
import { render, waitFor } from "@testing-library/angular";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { http, HttpResponse as MswHttpResponse } from "msw";
import { Subject, firstValueFrom } from "rxjs";

import { server } from "@test-mocks/server";
import { ComponentTool } from "../../../common/util/component-tool";
import { MoveDataSourceDialogComponent, FAKE_ROOT_PATH } from "./move-datasource-dialog.component";
import { DataSourceInfo } from "../model/data-source-info";
import { PortalDataType } from "../data-navigation-tree/portal-data-type";

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

function makeItem(name: string, path: string, typeName: string): DataSourceInfo {
   return {
      name, path,
      type: { name: typeName, label: typeName },
      createdBy: "", createdDate: 0, createdDateLabel: "", dateFormat: "",
      editable: false, deletable: false,
   };
}

function makeFolder(name: string, path: string): DataSourceInfo {
   return makeItem(name, path, PortalDataType.DATA_SOURCE_FOLDER);
}

function makeDataSource(name: string, path: string): DataSourceInfo {
   return makeItem(name, path, PortalDataType.DATA_SOURCE);
}

async function renderDialog(inputs: Partial<any> = {}) {
   const { fixture } = await render(MoveDataSourceDialogComponent, {
      providers: [
         provideHttpClient(),
         { provide: NgbModal, useValue: MODAL_MOCK },
      ],
      schemas: [NO_ERRORS_SCHEMA],
      componentInputs: {
         originalPaths: [],
         parentPath: "/",
         parentScope: 1,
         grandparentFolder: "/",
         multi: false,
         items: [],
         ...inputs,
      },
   });
   return { comp: fixture.componentInstance as MoveDataSourceDialogComponent, fixture };
}

beforeEach(() => {
   MODAL_MOCK.open.mockClear();
});

afterEach(() => vi.restoreAllMocks());

// ---------------------------------------------------------------------------
// Group 1 — ok(): duplicate check + onCommit / error dialog
// ---------------------------------------------------------------------------

describe("Group 1 — ok(): POST checkDuplicate drives onCommit vs error dialog", () => {
   // 🔁 Regression-sensitive: onCommit must carry the currently selected folderPath,
   //    not the initial null — callers use this value to move the datasource.
   it("should emit onCommit with folderPath when server returns duplicate=false", async () => {
      const { comp } = await renderDialog({ items: [] });
      comp.folderPath = "/target";
      const emitSpy = vi.spyOn(comp.onCommit, "emit");

      comp.ok();

      await waitFor(() => expect(emitSpy).toHaveBeenCalledWith("/target"));
   });

   it("should show error dialog and NOT emit onCommit when server returns duplicate=true", async () => {
      server.use(
         http.post("*/api/data/datasources/move/checkDuplicate", () =>
            MswHttpResponse.json({ duplicate: true })
         )
      );
      const dialogSpy = vi.spyOn(ComponentTool, "showMessageDialog").mockResolvedValue("ok");
      const { comp } = await renderDialog({ items: [] });
      comp.folderPath = "/target";
      const emitSpy = vi.spyOn(comp.onCommit, "emit");

      comp.ok();

      await waitFor(() => expect(dialogSpy).toHaveBeenCalled());
      expect(emitSpy).not.toHaveBeenCalled();
   });

   it("should include folderPath in the POST body when folderPath is not '/'", async () => {
      let capturedBody: any;
      server.use(
         http.post("*/api/data/datasources/move/checkDuplicate", async ({ request }) => {
            capturedBody = await request.json();
            return MswHttpResponse.json({ duplicate: false });
         })
      );
      const { comp } = await renderDialog({ items: [makeFolder("F", "/f")] });
      comp.folderPath = "/new-location";

      comp.ok();

      await waitFor(() => expect(capturedBody).toBeDefined());
      expect(capturedBody.path).toBe("/new-location");
      expect(capturedBody.items).toHaveLength(1);
   });
});

// ---------------------------------------------------------------------------
// Group 2 — openFolderRequest: HTTP mapping and filtering
// ---------------------------------------------------------------------------

describe("Group 2 — openFolderRequest: filters and maps API response", () => {
   // 🔁 Regression-sensitive: filtering must exclude non-DATA_SOURCE_FOLDER items so that
   //    data sources are never shown as move destinations in the file browser.
   it("should exclude non-folder items and include only DATA_SOURCE_FOLDER entries", async () => {
      server.use(
         http.get("*/api/data/datasources/browser", () =>
            MswHttpResponse.json({
               dataSourceList: [
                  makeFolder("Folder1", "/folder1"),
                  makeDataSource("DS1", "/ds1"),
                  makeFolder("Folder2", "/folder2"),
               ],
               currentFolder: [],
               root: true,
               newDatasourceEnabled: true,
               newVpmEnabled: true,
            })
         )
      );
      const { comp } = await renderDialog({ items: [] });

      const result = await firstValueFrom(comp.openFolderRequest("/"));

      expect(result.folders).toHaveLength(2);
      expect(result.folders.map(f => f.name)).toEqual(["Folder1", "Folder2"]);
      expect(result.files).toEqual([]);
   });

   it("should exclude folders whose path is in originalPaths", async () => {
      server.use(
         http.get("*/api/data/datasources/browser", () =>
            MswHttpResponse.json({
               dataSourceList: [
                  makeFolder("Moving", "/moving"),
                  makeFolder("Target", "/target"),
               ],
               currentFolder: [],
               root: true,
               newDatasourceEnabled: true,
               newVpmEnabled: true,
            })
         )
      );
      const { comp } = await renderDialog({ originalPaths: ["/moving"], items: [] });

      const result = await firstValueFrom(comp.openFolderRequest("/"));

      expect(result.folders).toHaveLength(1);
      expect(result.folders[0].path).toBe("/target");
   });

   it("should set root=true param when path is FAKE_ROOT_PATH", async () => {
      let capturedUrl = "";
      server.use(
         http.get("*/api/data/datasources/browser", ({ request }) => {
            capturedUrl = request.url;
            return MswHttpResponse.json({ dataSourceList: [], currentFolder: [], root: true, newDatasourceEnabled: false, newVpmEnabled: false });
         })
      );
      const { comp } = await renderDialog({ items: [] });

      await firstValueFrom(comp.openFolderRequest(FAKE_ROOT_PATH));

      expect(capturedUrl).toContain("root=true");
   });

   it("should set path param when path is a non-root non-fake value", async () => {
      let capturedUrl = "";
      server.use(
         http.get("*/api/data/datasources/browser", ({ request }) => {
            capturedUrl = request.url;
            return MswHttpResponse.json({ dataSourceList: [], currentFolder: [], root: false, newDatasourceEnabled: false, newVpmEnabled: false });
         })
      );
      const { comp } = await renderDialog({ items: [] });

      await firstValueFrom(comp.openFolderRequest("/subfolder"));

      expect(capturedUrl).toContain("path=/subfolder");
   });
});

// ---------------------------------------------------------------------------
// Group 3 — ngOnChanges: recomputes isFolder
// ---------------------------------------------------------------------------

describe("Group 3 — ngOnChanges: recomputes isFolder on multi or items change", () => {
   it("should set isFolder=true when multi changes to true", async () => {
      const { comp } = await renderDialog({ multi: false, items: [] });
      comp.multi = true;

      comp.ngOnChanges({ multi: new SimpleChange(false, true, false) });

      expect(comp.isFolder).toBe(true);
   });

   it("should set isFolder=true when items[0] is a DATA_SOURCE_FOLDER type", async () => {
      const { comp } = await renderDialog({ multi: false, items: [] });
      comp.items = [makeFolder("F", "/f")];

      comp.ngOnChanges({ items: new SimpleChange([], comp.items, false) });

      expect(comp.isFolder).toBe(true);
   });

   it("should set isFolder=false when items[0] is a DATA_SOURCE (not a folder)", async () => {
      const { comp } = await renderDialog({ multi: false, items: [] });
      comp.items = [makeDataSource("DS", "/ds")];

      comp.ngOnChanges({ items: new SimpleChange([], comp.items, false) });

      expect(comp.isFolder).toBe(false);
   });

   it("should NOT recompute isFolder when unrelated changes are passed", async () => {
      const { comp } = await renderDialog({ multi: false, items: [] });
      comp.multi = true;
      comp.isFolder = false;  // manually set to false as a baseline

      comp.ngOnChanges({ parentPath: new SimpleChange("/", "/other", false) });

      expect(comp.isFolder).toBe(false);  // unchanged since unrelated key
   });
});

// ---------------------------------------------------------------------------
// Group 4 — ngOnInit: state initialization
// ---------------------------------------------------------------------------

describe("Group 4 — ngOnInit: initial state", () => {
   it("should set folderPath to null on init", async () => {
      const { comp } = await renderDialog({ parentScope: 3 });
      // ngOnInit already ran during render()
      expect(comp.folderPath).toBeNull();
   });

   it("should set folderScope to parentScope on init", async () => {
      const { comp } = await renderDialog({ parentScope: 5 });
      expect(comp.folderScope).toBe(5);
   });

   it("should compute isFolder=true when first item is a folder", async () => {
      const { comp } = await renderDialog({ multi: false, items: [makeFolder("F", "/f")] });
      expect(comp.isFolder).toBe(true);
   });

   it("should compute isFolder=true when multi=true regardless of items", async () => {
      const { comp } = await renderDialog({ multi: true, items: [] });
      expect(comp.isFolder).toBe(true);
   });
});

// ---------------------------------------------------------------------------
// Group 5 — folderSelected: updates folderPath and folderScope
// ---------------------------------------------------------------------------

describe("Group 5 — folderSelected: updates selection state", () => {
   it("should clear folderPath and folderScope when items array is empty", async () => {
      const { comp } = await renderDialog();
      comp.folderPath = "/existing";
      comp.folderScope = 2;

      comp.folderSelected([]);

      expect(comp.folderPath).toBeNull();
      expect(comp.folderScope).toBeNull();
   });

   it("should set folderPath to the first selected item's path", async () => {
      const { comp } = await renderDialog();
      const folder = makeFolder("Target", "/target");

      comp.folderSelected([folder]);

      expect(comp.folderPath).toBe("/target");
   });
});

// ---------------------------------------------------------------------------
// Group 6 — rootLabel getter
// ---------------------------------------------------------------------------

describe("Group 6 — rootLabel getter: returns i18n key", () => {
   it("should return the Data Source i18n key", async () => {
      const { comp } = await renderDialog();
      expect(comp.rootLabel).toBe("_#(js:Data Source)");
   });
});

// ---------------------------------------------------------------------------
// Group 7 — cancel
// ---------------------------------------------------------------------------

describe("Group 7 — cancel: emits onCancel", () => {
   it("should emit onCancel when cancel() is called", async () => {
      const { comp } = await renderDialog();
      const emitSpy = vi.spyOn(comp.onCancel, "emit");

      comp.cancel();

      expect(emitSpy).toHaveBeenCalledWith("cancel");
   });
});

// ---------------------------------------------------------------------------
// Group 8 — memory leak: no persistent subscriptions
// ---------------------------------------------------------------------------

describe("Group 8 — ngOnInit/ngOnDestroy: no persistent subscriptions to clean up", () => {
   it("should not have a subscriptions field (no OnDestroy lifecycle to test)", async () => {
      const { comp } = await renderDialog();
      // MoveDataSourceDialogComponent has no persistent subscriptions —
      // ok() and openFolderRequest() each create a single fire-and-forget subscription.
      // Confirm the component has no stored Subscription object that could leak.
      expect((comp as any).subscriptions).toBeUndefined();
   });
});
