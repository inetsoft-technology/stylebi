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
 * DatabaseDataModelBrowserComponent — coverage pass
 *
 * Fills gaps left by database-data-model-browser.component.tl.spec.ts (risk-driven pass).
 *
 * Capabilities covered:
 *   C5  (renameModel)      — Group A: physical / logical / folder dispatch to correct service method
 *   C6  (deleteModel)      — Group B: physical / logical / folder dispatch with typed args
 *   C7  (moveModel)        — Group C: item wrapped in array; callback uses refreshListAndTree(false)
 *   C8  (addExtendModel)   — Group D: physical / logical dispatch + missing-name guard
 *   C12 (getChildren)      — Group E: logical → extendModels; physical → extendViews
 *   C13 (getIcon)          — Group F: physical / logical / folder icon strings
 *   C14 (clearSearch)      — Group G: resets searchQuery + searchVisible, routes with null query
 *   C15 (getParentPath)    — Group H: extended → ""; non-extended with folder; null folderName → "/"
 *
 * Skipped (explicit reasons):
 *   search() no-databaseName guard      — dead UX path; databaseName is always set from the route
 *                                         before any search action becomes reachable in the template.
 *   getTypeLabel unknown-type fallback  — dead code; type is always one of the three known string
 *                                         constants defined at the top of the component.
 *   getBasedView empty-string connection — same branch as null-connection (!!str is false for "");
 *                                         covered by the null variant in tl.spec.ts Group 14.
 *   createActions visible() predicates  — e2e territory; requires rendered dropdown in a browser.
 *   dragAssetsItems                     — explicitly skipped in tl.spec.ts (delegates to GuiTool /
 *                                         DragService; no testable business logic beyond DragService.put).
 *   isRoot(), toggleSelectionState(),
 *   dragSupportFun()                    — single-expression returns; no branch to exercise.
 *   getParentPath logical variants      — symmetric with physical; identical branch logic, same
 *                                         folderName / null guards produce identical return values.
 */

import { type Mock } from "vitest";
import { CommonModule } from "@angular/common";
import { HttpClientTestingModule, HttpTestingController } from "@angular/common/http/testing";
import { NO_ERRORS_SCHEMA } from "@angular/core";
import { TestBed } from "@angular/core/testing";
import { ActivatedRoute, Router } from "@angular/router";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { render } from "@testing-library/angular";
import { EMPTY, of } from "rxjs";

import { AppInfoService } from "../../../../../../../../shared/util/app-info.service";
import { DomService } from "../../../../../widget/dom-service/dom.service";
import { FixedDropdownService } from "../../../../../widget/fixed-dropdown/fixed-dropdown.service";
import { DragService } from "../../../../../widget/services/drag.service";
import { DatasourceBrowserService } from "../../datasource-browser.service";
import { DataModelNameChangeService } from "../../../services/data-model-name-change.service";
import { FolderChangeService } from "../../../services/folder-change.service";
import { DatabaseAsset } from "../../../model/datasources/database/database-asset";
import { PhysicalModelBrowserInfo } from "../../../model/datasources/database/physical-model/physical-model-browser-info";
import { LogicalModelBrowserInfo } from "../../../model/datasources/database/physical-model/logical-model/logical-model-browser-info";
import { DataModelBrowserService } from "./data-model-browser.service";
import { DatabaseDataModelBrowserComponent } from "./database-data-model-browser.component";

// ---------------------------------------------------------------------------
// Typed item factories
// ---------------------------------------------------------------------------

const LOGICAL_MODEL_ASSET = "logical_model";
const PHYSICAL_VIEW_ASSET = "physical_model";
const FOLDER_ASSET = "data_model_folder";

function createPhysical(overrides: Partial<PhysicalModelBrowserInfo> = {}): PhysicalModelBrowserInfo {
   return {
      databaseName: "SalesDB",
      type: PHYSICAL_VIEW_ASSET,
      id: "phys-id",
      path: "SalesDB/View",
      urlPath: "",
      name: "View",
      createdBy: "admin",
      description: "phys-desc",
      createdDate: 0,
      editable: true,
      deletable: true,
      createdDateLabel: "",
      folderName: "",
      parentView: null,
      extendViews: [],
      ...overrides,
   };
}

function createLogical(overrides: Partial<LogicalModelBrowserInfo> = {}): LogicalModelBrowserInfo {
   return {
      databaseName: "SalesDB",
      type: LOGICAL_MODEL_ASSET,
      id: "log-id",
      path: "SalesDB/Logical",
      urlPath: "",
      name: "Logical",
      createdBy: "admin",
      description: "log-desc",
      createdDate: 0,
      editable: true,
      deletable: true,
      createdDateLabel: "",
      physicalModel: "PhysView",
      parentModel: null,
      folderName: "",
      extendModels: [],
      connection: null,
      ...overrides,
   };
}

function createFolder(overrides: Partial<DatabaseAsset> = {}): DatabaseAsset {
   return {
      databaseName: "SalesDB",
      type: FOLDER_ASSET,
      id: "folder-id",
      path: "SalesDB/Folder",
      urlPath: "",
      name: "Folder",
      createdBy: "admin",
      description: "",
      createdDate: 0,
      editable: true,
      deletable: true,
      createdDateLabel: "",
      ...overrides,
   };
}

// ---------------------------------------------------------------------------
// Shared render helper — runnable in isolation
// ---------------------------------------------------------------------------

async function renderBrowser() {
   const router = { navigate: vi.fn() };
   const dataModelBrowserService = {
      changed: vi.fn(() => EMPTY),
      addDataModelFolder: vi.fn(),
      addPhysicalView: vi.fn(),
      addLogicalModel: vi.fn(),
      addExtendModel: vi.fn(),
      renamePhysicalView: vi.fn(),
      renameLogicalModel: vi.fn(),
      renameDataModelFolder: vi.fn(),
      deletePhysicalView: vi.fn(),
      deleteLogicalModel: vi.fn(),
      deleteDataModelFolder: vi.fn(),
      moveModels: vi.fn(),
      deleteModels: vi.fn(),
      moveModelsToTarget: vi.fn(),
      createDataBaseAssets: vi.fn(() => []),
   };
   const dragService = { put: vi.fn(), getDragData: vi.fn(() => ({})) };
   const datasourceService = { refreshTree: vi.fn() };

   const result = await render(DatabaseDataModelBrowserComponent, {
      imports: [CommonModule, HttpClientTestingModule],
      providers: [
         { provide: FolderChangeService, useValue: {} },
         { provide: FixedDropdownService, useValue: { open: vi.fn(() => ({ componentInstance: {} })) } },
         { provide: NgbModal, useValue: {} },
         { provide: ActivatedRoute, useValue: { queryParamMap: EMPTY } },
         { provide: Router, useValue: router },
         { provide: DatasourceBrowserService, useValue: datasourceService },
         { provide: DataModelNameChangeService, useValue: {} },
         { provide: DataModelBrowserService, useValue: dataModelBrowserService },
         { provide: DragService, useValue: dragService },
         { provide: AppInfoService, useValue: { isEnterprise: vi.fn(() => of(true)) } },
         { provide: DomService, useValue: {} },
      ],
      schemas: [NO_ERRORS_SCHEMA],
   });

   const httpMock = TestBed.inject(HttpTestingController);

   return { ...result, router, dataModelBrowserService, datasourceService, httpMock };
}

// ---------------------------------------------------------------------------
// Group A — renameModel: dispatch to correct service method [Risk 2]
// ---------------------------------------------------------------------------

describe("DatabaseDataModelBrowserComponent — renameModel — physical/logical/folder dispatch [Group A, Risk 2]", () => {

   // Variant: C5-V1 — Physical view
   // Setup: physical item with all fields populated
   // Observable: renamePhysicalView receives (name, databaseName, description, folderName, callback);
   //             neither logical nor folder variant is called
   it("rename: physical view → renamePhysicalView called with name/db/description/folder/callback", async () => {
      const { fixture, dataModelBrowserService } = await renderBrowser();
      const comp = fixture.componentInstance;
      const item = createPhysical({ name: "MyView", databaseName: "SalesDB", description: "d", folderName: "FolderA" });

      comp.renameModel(item as DatabaseAsset);

      expect(dataModelBrowserService.renamePhysicalView).toHaveBeenCalledWith(
         "MyView", "SalesDB", "d", "FolderA", expect.any(Function));
      expect(dataModelBrowserService.renameLogicalModel).not.toHaveBeenCalled();
      expect(dataModelBrowserService.renameDataModelFolder).not.toHaveBeenCalled();
   });

   // Variant: C5-V2 — Logical model
   // Setup: logical item with all fields populated
   // Observable: renameLogicalModel receives (name, databaseName, description, folderName, callback);
   //             neither physical nor folder variant is called
   it("rename: logical model → renameLogicalModel called with name/db/description/folder/callback", async () => {
      const { fixture, dataModelBrowserService } = await renderBrowser();
      const comp = fixture.componentInstance;
      const item = createLogical({ name: "MyModel", databaseName: "SalesDB", description: "ld", folderName: "FolderB" });

      comp.renameModel(item as DatabaseAsset);

      expect(dataModelBrowserService.renameLogicalModel).toHaveBeenCalledWith(
         "MyModel", "SalesDB", "ld", "FolderB", expect.any(Function));
      expect(dataModelBrowserService.renamePhysicalView).not.toHaveBeenCalled();
      expect(dataModelBrowserService.renameDataModelFolder).not.toHaveBeenCalled();
   });

   // Variant: C5-V3 — Folder
   // Setup: folder item; renameDataModelFolder uses comp.databaseName (not item.databaseName)
   // Observable: renameDataModelFolder receives (comp.databaseName, name, callback, false);
   //             false is the 4th arg — not a second callback
   it("rename: folder → renameDataModelFolder called with component databaseName, item name, callback, false", async () => {
      const { fixture, dataModelBrowserService } = await renderBrowser();
      const comp = fixture.componentInstance;
      comp.databaseName = "SalesDB";
      const item = createFolder({ name: "Reports" });

      comp.renameModel(item);

      expect(dataModelBrowserService.renameDataModelFolder).toHaveBeenCalledWith(
         "SalesDB", "Reports", expect.any(Function), false);
      expect(dataModelBrowserService.renamePhysicalView).not.toHaveBeenCalled();
      expect(dataModelBrowserService.renameLogicalModel).not.toHaveBeenCalled();
   });
});

// ---------------------------------------------------------------------------
// Group B — deleteModel: dispatch with correct typed args [Risk 2]
// ---------------------------------------------------------------------------

describe("DatabaseDataModelBrowserComponent — deleteModel — physical/logical/folder dispatch [Group B, Risk 2]", () => {

   // Variant: C6-V1 — Physical view
   // Setup: physical item with a parentView; parentView becomes the 3rd arg (needed for cascade delete)
   // Observable: deletePhysicalView receives (name, databaseName, parentView, folder, callback);
   //             wrong parentView causes incorrect server-side cascade
   it("delete: physical view → deletePhysicalView with parentView and folder passed correctly", async () => {
      const { fixture, dataModelBrowserService } = await renderBrowser();
      const comp = fixture.componentInstance;
      const item = createPhysical({ name: "PhysView", databaseName: "SalesDB", parentView: "BaseView" });

      comp.deleteModel(item as DatabaseAsset, "TargetFolder");

      expect(dataModelBrowserService.deletePhysicalView).toHaveBeenCalledWith(
         "PhysView", "SalesDB", "BaseView", "TargetFolder", expect.any(Function));
      expect(dataModelBrowserService.deleteLogicalModel).not.toHaveBeenCalled();
      expect(dataModelBrowserService.deleteDataModelFolder).not.toHaveBeenCalled();
   });

   // Variant: C6-V2 — Logical model
   // Setup: logical item with extendModels and parentModel populated; both are forwarded as args
   // Observable: deleteLogicalModel receives (name, databaseName, extendModels, parentModel, folder, callback);
   //             wrong extendModels or parentModel breaks server-side cascade delete
   it("delete: logical model → deleteLogicalModel with extendModels and parentModel passed correctly", async () => {
      const { fixture, dataModelBrowserService } = await renderBrowser();
      const comp = fixture.componentInstance;
      const child = createLogical({ name: "ChildModel" });
      const item = createLogical({
         name: "ParentLogical",
         databaseName: "SalesDB",
         extendModels: [child],
         parentModel: "GrandParent",
      });

      comp.deleteModel(item as DatabaseAsset, "TargetFolder");

      expect(dataModelBrowserService.deleteLogicalModel).toHaveBeenCalledWith(
         "ParentLogical", "SalesDB", [child], "GrandParent", "TargetFolder", expect.any(Function));
      expect(dataModelBrowserService.deletePhysicalView).not.toHaveBeenCalled();
      expect(dataModelBrowserService.deleteDataModelFolder).not.toHaveBeenCalled();
   });

   // Variant: C6-V3 — Folder
   // Setup: folder item; deleteDataModelFolder uses comp.databaseName (not item.databaseName)
   // Observable: deleteDataModelFolder receives (comp.databaseName, name, callback) — 3 args only
   it("delete: folder → deleteDataModelFolder called with component databaseName and folder name", async () => {
      const { fixture, dataModelBrowserService } = await renderBrowser();
      const comp = fixture.componentInstance;
      comp.databaseName = "SalesDB";
      const item = createFolder({ name: "OldFolder" });

      comp.deleteModel(item, "anyFolder");

      expect(dataModelBrowserService.deleteDataModelFolder).toHaveBeenCalledWith(
         "SalesDB", "OldFolder", expect.any(Function));
      expect(dataModelBrowserService.deletePhysicalView).not.toHaveBeenCalled();
      expect(dataModelBrowserService.deleteLogicalModel).not.toHaveBeenCalled();
   });
});

// ---------------------------------------------------------------------------
// Group C — moveModel: single-item delegation [Risk 2]
// ---------------------------------------------------------------------------

describe("DatabaseDataModelBrowserComponent — moveModel — single-item delegation [Group C, Risk 2]", () => {

   afterEach(() => {
      TestBed.inject(HttpTestingController).verify();
   });

   // Variant: C7-V1 — Item is wrapped in a one-element array
   // Setup: single item passed to moveModel
   // Observable: moveModels receives [item] — not the bare item; a flat arg would skip the server batch
   it("move: moveModels receives the item wrapped in a one-element array", async () => {
      const { fixture, dataModelBrowserService } = await renderBrowser();
      const comp = fixture.componentInstance;
      comp.databaseName = "SalesDB";
      const item = createPhysical({ name: "ToMove" });

      comp.moveModel(item as DatabaseAsset);

      expect(dataModelBrowserService.moveModels).toHaveBeenCalledWith([item], expect.any(Function));
   });

   // Variant: C7-V2 — Callback uses refreshListAndTree(false): tree refresh suppressed
   // Setup: moveModel callback is invoked after a successful move
   // Observable: datasourceService.refreshTree is NOT called; the browser list IS refreshed via HTTP
   it("move: success callback refreshes the list but does not trigger a datasource tree refresh", async () => {
      const { fixture, dataModelBrowserService, datasourceService, httpMock } = await renderBrowser();
      const comp = fixture.componentInstance;
      comp.databaseName = "SalesDB";

      comp.moveModel(createPhysical({ name: "ToMove" }) as DatabaseAsset);
      const onSuccess: () => void = (dataModelBrowserService.moveModels as Mock).mock.calls[0][1];

      onSuccess();

      // Flush the list-refresh HTTP call triggered by refreshListAndTree(false)
      const req = httpMock.expectOne(r => r.url.includes("dataModel/browse"));
      req.flush({ listModel: null, dbEditable: true, dateFormat: "yyyy-MM-dd HH:mm:ss" });

      expect(datasourceService.refreshTree).not.toHaveBeenCalled();
   });
});

// ---------------------------------------------------------------------------
// Group D — addExtendModel: physical/logical dispatch + guard [Risk 2]
// ---------------------------------------------------------------------------

describe("DatabaseDataModelBrowserComponent — addExtendModel — dispatch and guard [Group D, Risk 2]", () => {

   // Variant: C8-V1 — Physical view: isView=true, physicalModel is undefined
   // Setup: physical item with databaseName, name, folderName
   // Observable: addExtendModel(db, name, undefined, true, folderName) — physicalModel not forwarded
   it("addExtendModel: physical view → service called with isView=true and no physicalModel", async () => {
      const { fixture, dataModelBrowserService } = await renderBrowser();
      const comp = fixture.componentInstance;
      const item = createPhysical({ databaseName: "SalesDB", name: "BaseView", folderName: "FolderA" });

      comp.addExtendModel(item as DatabaseAsset);

      expect(dataModelBrowserService.addExtendModel).toHaveBeenCalledWith(
         "SalesDB", "BaseView", undefined, true, "FolderA");
   });

   // Variant: C8-V2 — Logical model: isView=false, physicalModel from item forwarded
   // Setup: logical item with physicalModel set
   // Observable: addExtendModel(db, name, physicalModel, false, folderName)
   it("addExtendModel: logical model → service called with isView=false and physicalModel from item", async () => {
      const { fixture, dataModelBrowserService } = await renderBrowser();
      const comp = fixture.componentInstance;
      const item = createLogical({ databaseName: "SalesDB", name: "BaseModel", physicalModel: "PhysView", folderName: "FolderB" });

      comp.addExtendModel(item as DatabaseAsset);

      expect(dataModelBrowserService.addExtendModel).toHaveBeenCalledWith(
         "SalesDB", "BaseModel", "PhysView", false, "FolderB");
   });

   // Variant: C8-V3 — Guard: missing databaseName → early return
   // Setup: item with empty databaseName (simulates an orphaned or partially-loaded asset)
   // Observable: service method is NOT called — prevents a server call with a blank database key
   it("addExtendModel: empty databaseName → guard fires, service not called", async () => {
      const { fixture, dataModelBrowserService } = await renderBrowser();
      const comp = fixture.componentInstance;
      const item = createPhysical({ databaseName: "", name: "OrphanView" });

      comp.addExtendModel(item as DatabaseAsset);

      expect(dataModelBrowserService.addExtendModel).not.toHaveBeenCalled();
   });
});

// ---------------------------------------------------------------------------
// Group E — getChildren: polymorphic dispatch [Risk 1]
// ---------------------------------------------------------------------------

describe("DatabaseDataModelBrowserComponent — getChildren — polymorphic dispatch [Group E, Risk 1]", () => {

   // Variant: C12-V1 — Logical model → extendModels
   // Setup: logical item with extendModels populated
   // Observable: returns the extendModels array (tree expand source for logical subtree)
   it("getChildren: logical model → returns extendModels array", async () => {
      const { fixture } = await renderBrowser();
      const comp = fixture.componentInstance;
      const child = createLogical({ name: "ChildModel" });
      const item = createLogical({ extendModels: [child] });

      expect(comp.getChildren(item)).toEqual([child]);
   });

   // Variant: C12-V2 — Physical view → extendViews
   // Setup: physical item with extendViews populated
   // Observable: returns the extendViews array (tree expand source for physical subtree)
   it("getChildren: physical view → returns extendViews array", async () => {
      const { fixture } = await renderBrowser();
      const comp = fixture.componentInstance;
      const extView = createPhysical({ name: "ExtView", parentView: "BaseView" });
      const item = createPhysical({ extendViews: [extView] });

      expect(comp.getChildren(item as any)).toEqual([extView]);
   });
});

// ---------------------------------------------------------------------------
// Group F — getIcon: polymorphic icon dispatch [Risk 1]
// ---------------------------------------------------------------------------

describe("DatabaseDataModelBrowserComponent — getIcon — polymorphic icon dispatch [Group F, Risk 1]", () => {

   // Variant: C13-V1 — Physical view → "partition-icon"
   // Setup: physical item
   // Observable: icon function returns the CSS class for physical views
   it("getIcon: physical view → 'partition-icon'", async () => {
      const { fixture } = await renderBrowser();
      const iconFn = fixture.componentInstance.getIcon();
      expect(iconFn(createPhysical() as DatabaseAsset)).toBe("partition-icon");
   });

   // Variant: C13-V2 — Logical model → "logical-model-icon"
   // Setup: logical item
   // Observable: icon function returns the CSS class for logical models
   it("getIcon: logical model → 'logical-model-icon'", async () => {
      const { fixture } = await renderBrowser();
      const iconFn = fixture.componentInstance.getIcon();
      expect(iconFn(createLogical() as unknown as DatabaseAsset)).toBe("logical-model-icon");
   });

   // Variant: C13-V3 — Folder → "folder-icon"
   // Setup: folder item
   // Observable: icon function returns the CSS class for folders
   it("getIcon: folder → 'folder-icon'", async () => {
      const { fixture } = await renderBrowser();
      const iconFn = fixture.componentInstance.getIcon();
      expect(iconFn(createFolder())).toBe("folder-icon");
   });
});

// ---------------------------------------------------------------------------
// Group G — clearSearch: state reset and routing [Risk 2]
// ---------------------------------------------------------------------------

describe("DatabaseDataModelBrowserComponent — clearSearch — state reset and routing [Group G, Risk 2]", () => {

   // Variant: C14-V1 — Full state reset + routing
   // Setup: searchQuery and searchVisible are truthy before clearSearch
   // Observable: searchQuery becomes null, searchVisible becomes false,
   //             router navigates to databaseModels with query: null
   it("clearSearch: resets searchQuery to null and searchVisible to false, then routes with null query", async () => {
      const { fixture, router } = await renderBrowser();
      const comp = fixture.componentInstance;
      comp.databaseName = "SalesDB";
      comp.searchQuery = "oldTerm";
      comp.searchVisible = true;
      comp.currentFolderPathString = "FolderA";

      comp.clearSearch();

      expect(comp.searchQuery).toBeNull();
      expect(comp.searchVisible).toBe(false);
      expect(router.navigate).toHaveBeenCalledWith(
         ["/portal/tab/data/datasources/databaseModels"],
         expect.objectContaining({
            queryParams: expect.objectContaining({
               databaseName: "SalesDB",
               folderName: "FolderA",
               query: null,
            }),
         })
      );
   });
});

// ---------------------------------------------------------------------------
// Group H — getParentPath: location column display variants [Risk 1]
// ---------------------------------------------------------------------------

describe("DatabaseDataModelBrowserComponent — getParentPath — location column display [Group H, Risk 1]", () => {

   // Variant: C15-V1 — Extended model/view → returns "" (location column suppressed)
   // Setup: physical item with parentView set (isExtend returns true)
   // Observable: returns empty string — extended items have no independent location
   it("getParentPath: extended physical view → returns empty string", async () => {
      const { fixture } = await renderBrowser();
      const item = createPhysical({ parentView: "BaseView" });
      expect(fixture.componentInstance.getParentPath(item as any)).toBe("");
   });

   // Variant: C15-V2 — Non-extended physical with folderName → returns folderName
   // Setup: physical item with parentView=null, folderName="Reports"
   // Observable: returns the folder name as the location string
   it("getParentPath: non-extended physical with folderName → returns folderName", async () => {
      const { fixture } = await renderBrowser();
      const item = createPhysical({ parentView: null, folderName: "Reports" });
      expect(fixture.componentInstance.getParentPath(item as any)).toBe("Reports");
   });

   // Variant: C15-V3 — Non-extended physical with null folderName → returns "/"
   // Setup: physical item with parentView=null, folderName=null (root-level item)
   // Observable: falls back to "/" indicating the root location
   it("getParentPath: non-extended physical with null folderName → returns '/'", async () => {
      const { fixture } = await renderBrowser();
      const item = createPhysical({ parentView: null, folderName: null as any });
      expect(fixture.componentInstance.getParentPath(item as any)).toBe("/");
   });
});
