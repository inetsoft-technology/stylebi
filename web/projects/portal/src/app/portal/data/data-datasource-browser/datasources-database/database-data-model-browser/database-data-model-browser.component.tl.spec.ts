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
 * DatabaseDataModelBrowserComponent - Angular Testing Library style
 *
 * Risk-first coverage:
 *   Group 1  [Risk 3] - editModel: route path params must be byte-encoded consistently
 *   Group 2  [Risk 3] - moveDisable: extended physical models require base model in batch
 *   Group 3  [Risk 3] - moveDisable: extended logical models require base model in batch
 *   Group 4  [Risk 3] - dataTreeDragToPane: drag/drop filters block self/ancestor/unsupported assets
 *   Group 5  [Risk 3] - disableAction: selection must gate move/delete until folder browser responds
 *     (includes regression coverage for Bug #75602, FIXED: stale/out-of-order responses no
 *     longer clobber isdisableAction — see request-token guard in disableAction())
 *   Group 6  [Risk 3] - dropAssetsItems: internal pane drag filters folders and confirms move
 *   Group 7  [Risk 3] - deleteSelected: batch delete must clear selection after commit
 *   Group 8  [Risk 3] - ngOnDestroy: route and service subscriptions must be torn down
 *   Group 9  [Risk 3] - ngOnInit: route queryParamMap and changed() subscriptions deliver data (M4 mandatory)
 *   Group 10 [Risk 2] - clickItem: folder routes; editable non-folder edits; non-editable is no-op
 *   Group 11 [Risk 2] - updateModels HTTP states: success populates+sorts; error is silently swallowed
 *   Group 12 [Risk 2] - refreshSearchBrowser: HTTP error triggers danger notification
 *   Group 13 [Risk 2] - getTypeLabel: correct label for each asset type and extend state
 *   Group 14 [Risk 2] - getBasedView: correct display string for all logical-model variants
 *   Group 15 [Risk 2] - updateSortOptions: toggles direction on same key; resets to ascending on new key
 *   Group 16 [Risk 1] - setShowDetailsItem: toggles item on/off
 *
 * KEY contracts:
 *   Every user-controlled route segment passed as a path parameter is Tool.byteEncode()'d.
 *   Extended data models cannot be moved unless their base model is selected in the same batch.
 *   Dragging data-tree or pane assets into a folder must never allow self moves, ancestor cycles,
 *   folder rows in the move batch, or unsupported asset types.
 *   deleteSelected clears selectedItems in its success callback.
 *   The ngOnInit route and changed() subscriptions must be cleaned up on ngOnDestroy (Risk 3).
 *
 * A0 handler inventory skips (explicitly):
 *   openContextmenu — thin pass-through to FixedDropdownService.open(); tested indirectly via
 *     Group 6 which exercises createActions() logic directly via moveModels0 spy.
 *   dragAssetsItems — delegates entirely to GuiTool.createDragImage / setDragImage; no
 *     business logic beyond DragService.put() which has no assertion value here.
 */

import { CommonModule } from "@angular/common";
import { HttpClientTestingModule, HttpTestingController } from "@angular/common/http/testing";
import { NO_ERRORS_SCHEMA } from "@angular/core";
import { TestBed } from "@angular/core/testing";
import { ActivatedRoute, convertToParamMap, Router } from "@angular/router";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { render, waitFor } from "@testing-library/angular";
import { EMPTY, of, Subject } from "rxjs";

import { AssetType } from "../../../../../../../../shared/data/asset-type";
import { AssetEntry } from "../../../../../../../../shared/data/asset-entry";
import { SortTypes } from "../../../../../../../../shared/util/sort/sort-types";
import { Tool } from "../../../../../../../../shared/util/tool";
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
import { ComponentTool } from "../../../../../common/util/component-tool";
import { DataModelBrowserService } from "./data-model-browser.service";
import { DatabaseDataModelBrowserComponent } from "./database-data-model-browser.component";

const GET_DATA_MODEL_URI = "../api/data/database/dataModel/folder/browser";

// ---------------------------------------------------------------------------
// Shared helpers
// ---------------------------------------------------------------------------

const LOGICAL_MODEL_ASSET = "logical_model";
const PHYSICAL_VIEW_ASSET = "physical_model";
const FOLDER_ASSET = "data_model_folder";

function createAsset(overrides: Partial<DatabaseAsset> = {}): DatabaseAsset {
   return {
      databaseName: "SalesDB",
      type: PHYSICAL_VIEW_ASSET,
      id: "id",
      path: "SalesDB/View",
      urlPath: "",
      name: "View",
      createdBy: "admin",
      description: "",
      createdDate: 0,
      editable: true,
      deletable: true,
      createdDateLabel: "",
      ...overrides,
   };
}

function createPhysical(overrides: Partial<PhysicalModelBrowserInfo> = {}): PhysicalModelBrowserInfo {
   return {
      ...createAsset({ type: PHYSICAL_VIEW_ASSET }),
      folderName: "",
      parentView: null,
      ...overrides,
   };
}

function createLogical(overrides: Partial<LogicalModelBrowserInfo> = {}): LogicalModelBrowserInfo {
   return {
      ...createAsset({ type: LOGICAL_MODEL_ASSET }),
      physicalModel: "PhysicalView",
      parentModel: null,
      folderName: "",
      extendModels: [],
      connection: null,
      ...overrides,
   };
}

function createFolder(overrides: Partial<DatabaseAsset> = {}): DatabaseAsset {
   return createAsset({
      type: FOLDER_ASSET,
      name: "TargetFolder",
      path: "SalesDB/TargetFolder",
      ...overrides,
   });
}

function createEntry(path: string, type: AssetType): AssetEntry {
   return {
      identifier: path,
      path,
      type,
      createdUsername: "admin",
   } as any;
}

async function renderBrowser() {
   const router = { navigate: vi.fn() };
   const route = { queryParamMap: EMPTY };
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
      createDataBaseAssets: vi.fn((assets: AssetEntry[]) =>
         assets.map(asset => createAsset({ name: asset.path, path: asset.path }))),
   };
   const dragService = {
      put: vi.fn(),
      getDragData: vi.fn(() => ({})),
   };
   const datasourceService = { refreshTree: vi.fn() };

   const result = await render(DatabaseDataModelBrowserComponent, {
      imports: [CommonModule, HttpClientTestingModule],
      providers: [
         { provide: FolderChangeService, useValue: {} },
         {
            provide: FixedDropdownService,
            useValue: {
               open: vi.fn(() => ({ componentInstance: {} })),
            },
         },
         { provide: NgbModal, useValue: {} },
         { provide: ActivatedRoute, useValue: route },
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

   return {
      ...result,
      router,
      route,
      dataModelBrowserService,
      dragService,
      datasourceService,
      httpMock,
   };
}

async function renderBrowserControlled() {
   const routeSubject = new Subject<any>();
   const changedSubject = new Subject<void>();
   const router = { navigate: vi.fn() };
   const dataModelBrowserService = {
      changed: vi.fn(() => changedSubject.asObservable()),
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
      createDataBaseAssets: vi.fn((assets: AssetEntry[]) =>
         assets.map(asset => createAsset({ name: asset.path, path: asset.path }))),
   };
   const dragService = { put: vi.fn(), getDragData: vi.fn(() => ({})) };
   const datasourceService = { refreshTree: vi.fn() };

   const result = await render(DatabaseDataModelBrowserComponent, {
      imports: [CommonModule, HttpClientTestingModule],
      providers: [
         { provide: FolderChangeService, useValue: {} },
         { provide: FixedDropdownService, useValue: { open: vi.fn(() => ({ componentInstance: {} })) } },
         { provide: NgbModal, useValue: {} },
         { provide: ActivatedRoute, useValue: { queryParamMap: routeSubject.asObservable() } },
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

   return { ...result, routeSubject, changedSubject, router, dataModelBrowserService, datasourceService, httpMock };
}

function flushFolderBrowser(
   httpMock: HttpTestingController,
   databaseName: string,
   dataModelList: DatabaseAsset[] = [createPhysical()],
): void {
   const req = httpMock.expectOne(
      r => r.url.includes(GET_DATA_MODEL_URI) && r.params.get("database") === databaseName);
   req.flush({ dataModelList, currentFolder: [], root: false });
}

// ---------------------------------------------------------------------------
// Group 1 - editModel: route param encoding [Risk 3]
// ---------------------------------------------------------------------------

describe("DatabaseDataModelBrowserComponent - editModel - route param encoding [Group 1, Risk 3]", () => {

   // 🔁 Regression-sensitive: route path params must be byte-encoded consistently for every editModel branch.
   it("should encode database, physical model name, and parent for an extended physical model route", async () => {
      const { fixture, router, route } = await renderBrowser();
      const comp = fixture.componentInstance;
      const item = createPhysical({
         databaseName: "Sales/DB",
         name: "Child/View",
         parentView: "Base/View",
         folderName: "Folder A",
      });

      comp.editModel(item);

      expect(router.navigate).toHaveBeenCalledWith([
         "/portal/tab/data/datasources/database",
         Tool.byteEncode("Sales/DB"),
         "physicalModel",
         Tool.byteEncode("Child/View"),
         { parent: Tool.byteEncode("Base/View"), folder: "Folder A" },
      ], { relativeTo: route });
   });

   it("should encode extended logical model route segments including parent", async () => {
      const { fixture, router, route } = await renderBrowser();
      const comp = fixture.componentInstance;
      const item = createLogical({
         databaseName: "Sales/DB",
         physicalModel: "Phys/View",
         name: "Child/Logical",
         parentModel: "Base/Logical",
         folderName: "Folder A",
      });

      comp.editModel(item);

      expect(router.navigate).toHaveBeenCalledWith([
         "/portal/tab/data/datasources/database",
         Tool.byteEncode("Sales/DB"),
         "physicalModel",
         Tool.byteEncode("Phys/View"),
         "logicalModel",
         Tool.byteEncode("Child/Logical"),
         { parent: Tool.byteEncode("Base/Logical"), folder: "Folder A" },
      ], { relativeTo: route });
   });

   it("should encode the root physical model name route segment", async () => {
      const { fixture, router, route } = await renderBrowser();
      const comp = fixture.componentInstance;
      const item = createPhysical({
         databaseName: "Sales/DB",
         name: "Root/View",
         parentView: null,
         folderName: "Folder A",
      });

      comp.editModel(item);

      expect(router.navigate).toHaveBeenCalledWith([
         "/portal/tab/data/datasources/database",
         Tool.byteEncode("Sales/DB"),
         "physicalModel",
         Tool.byteEncode("Root/View"),
         { folder: "Folder A" },
      ], { relativeTo: route });
   });

   it("should encode the root logical model name route segment", async () => {
      const { fixture, router, route } = await renderBrowser();
      const comp = fixture.componentInstance;
      const item = createLogical({
         databaseName: "Sales/DB",
         physicalModel: "Physical/View",
         name: "Logical/Model",
         parentModel: null,
         folderName: "Folder A",
      });

      comp.editModel(item);

      expect(router.navigate).toHaveBeenCalledWith([
         "/portal/tab/data/datasources/database",
         Tool.byteEncode("Sales/DB"),
         "physicalModel",
         Tool.byteEncode("Physical/View"),
         "logicalModel",
         Tool.byteEncode("Logical/Model"),
         { parent: "", folder: "Folder A" },
      ], { relativeTo: route });
   });
});

// ---------------------------------------------------------------------------
// Group 2 - moveDisable: extended physical model batch [Risk 3]
// ---------------------------------------------------------------------------

describe("DatabaseDataModelBrowserComponent - moveDisable - extended physical batch [Group 2, Risk 3]", () => {

   // 🔁 Regression-sensitive: an extended view moved without its base corrupts the folder tree.
   it("should disable move when an extended physical model is selected without its base model", async () => {
      const { fixture } = await renderBrowser();
      const comp = fixture.componentInstance;
      comp.isdisableAction = false;
      comp.selectedItems = [
         createPhysical({ name: "ChildView", parentView: "BaseView" }),
      ];

      expect(comp.moveDisable).toBe(true);
   });

   it("should allow move when an extended physical model and its base physical model are selected together", async () => {
      const { fixture } = await renderBrowser();
      const comp = fixture.componentInstance;
      comp.isdisableAction = false;
      comp.selectedItems = [
         createPhysical({ name: "BaseView", parentView: null }),
         createPhysical({ name: "ChildView", parentView: "BaseView" }),
      ];

      expect(comp.moveDisable).toBe(false);
   });
});

// ---------------------------------------------------------------------------
// Group 3 - moveDisable: extended logical model batch [Risk 3]
// ---------------------------------------------------------------------------

describe("DatabaseDataModelBrowserComponent - moveDisable - extended logical batch [Group 3, Risk 3]", () => {

   // 🔁 Regression-sensitive: extended logical models depend on their parent model for a valid move target.
   it("should disable move when an extended logical model is selected without its base model", async () => {
      const { fixture } = await renderBrowser();
      const comp = fixture.componentInstance;
      comp.isdisableAction = false;
      comp.selectedItems = [
         createLogical({ name: "ChildLogical", parentModel: "BaseLogical" }),
      ];

      expect(comp.moveDisable).toBe(true);
   });

   it("should allow move when an extended logical model and its base logical model are selected together", async () => {
      const { fixture } = await renderBrowser();
      const comp = fixture.componentInstance;
      comp.isdisableAction = false;
      comp.selectedItems = [
         createLogical({ name: "BaseLogical", parentModel: null }),
         createLogical({ name: "ChildLogical", parentModel: "BaseLogical" }),
      ];

      expect(comp.moveDisable).toBe(false);
   });
});

// ---------------------------------------------------------------------------
// Group 4 - dataTreeDragToPane: drag/drop filtering [Risk 3]
// ---------------------------------------------------------------------------

describe("DatabaseDataModelBrowserComponent - dataTreeDragToPane - drag/drop filtering [Group 4, Risk 3]", () => {

   it("should ignore drops onto non-folder targets", async () => {
      const { fixture, dragService } = await renderBrowser();
      const comp = fixture.componentInstance;

      comp.dropAssetsItems(createPhysical({ type: PHYSICAL_VIEW_ASSET }));

      expect(dragService.getDragData).not.toHaveBeenCalled();
   });

   // 🔁 Regression-sensitive: data-tree drops must reject self, ancestor, and unsupported assets.
   it("should forward only allowed non-cyclic data-tree entries to moveModels0", async () => {
      const { fixture, dataModelBrowserService } = await renderBrowser();
      const comp = fixture.componentInstance;
      const target = createFolder({ path: "SalesDB/TargetFolder", name: "TargetFolder" });
      const valid = createEntry("SalesDB/OtherModel", AssetType.LOGIC_MODEL);
      const sameTarget = createEntry("SalesDB/TargetFolder", AssetType.PARTITION);
      const ancestor = createEntry("SalesDB", AssetType.PARTITION);
      const unsupported = createEntry("SalesDB/Query1", AssetType.QUERY);
      const moveSpy = vi.spyOn(comp as any, "moveModels0").mockImplementation(() => undefined);

      comp.dataTreeDragToPane(target, {
         dragA: JSON.stringify([valid, sameTarget, ancestor, unsupported]),
      });

      expect(dataModelBrowserService.createDataBaseAssets).toHaveBeenCalledWith([valid]);
      expect(moveSpy).toHaveBeenCalledWith(
         [expect.objectContaining({ path: "SalesDB/OtherModel" })],
         "TargetFolder");
   });
});

// ---------------------------------------------------------------------------
// Group 5 - disableAction: selection gating [Risk 3]
// ---------------------------------------------------------------------------

describe("DatabaseDataModelBrowserComponent - disableAction - selection gating [Group 5, Risk 3]", () => {

   afterEach(() => {
      TestBed.inject(HttpTestingController).verify();
   });

   // 🔁 Regression-sensitive: toolbar actions must stay disabled until the folder browser confirms models exist.
   it("should disable actions immediately when selection changes", async () => {
      const { fixture, httpMock } = await renderBrowser();
      const comp = fixture.componentInstance;
      comp.databaseName = "SalesDB";

      comp.changeSelectedItems([createPhysical()]);

      expect(comp.isdisableAction).toBe(true);
      flushFolderBrowser(httpMock, "SalesDB");
   });

   it("should keep actions disabled when the folder browser returns an empty list", async () => {
      const { fixture, httpMock } = await renderBrowser();
      const comp = fixture.componentInstance;
      comp.databaseName = "SalesDB";
      comp.isdisableAction = false;

      comp.changeSelectedItems([createPhysical()]);

      flushFolderBrowser(httpMock, "SalesDB", []);

      expect(comp.isdisableAction).toBe(true);
      expect(comp.moveDisable).toBe(true);
   });

   it("should enable actions when the folder browser reports existing data models", async () => {
      const { fixture, httpMock } = await renderBrowser();
      const comp = fixture.componentInstance;
      comp.databaseName = "SalesDB";
      comp.isdisableAction = true;
      comp.selectedItems = [createPhysical({ name: "BaseView", parentView: null })];

      comp.changeSelectedItems(comp.selectedItems);

      flushFolderBrowser(httpMock, "SalesDB", [createPhysical({ name: "BaseView" })]);

      expect(comp.isdisableAction).toBe(false);
      expect(comp.moveDisable).toBe(false);
   });

   // Bug #75602 (FIXED): disableAction() previously had no request-sequencing guard, so an
   // older in-flight request could resolve after a newer one and unconditionally overwrite
   // isdisableAction with a stale result. Fixed via a monotonically increasing request token
   // that causes stale responses to be ignored.
   it("should not re-enable actions when an older folder-browser response arrives after a newer empty one", async () => {
      const { fixture, httpMock } = await renderBrowser();
      const comp = fixture.componentInstance;
      comp.databaseName = "SalesDB";

      comp.changeSelectedItems([createPhysical()]);
      const first = httpMock.expectOne(r => r.url.includes(GET_DATA_MODEL_URI));

      comp.changeSelectedItems([createPhysical(), createPhysical({ name: "Other" })]);
      const second = httpMock.expectOne(r => r.url.includes(GET_DATA_MODEL_URI));

      second.flush({ dataModelList: [], currentFolder: [], root: false });
      first.flush({ dataModelList: [createPhysical()], currentFolder: [], root: false });

      expect(comp.isdisableAction).toBe(true);
      expect(comp.moveDisable).toBe(true);
   });
});

// ---------------------------------------------------------------------------
// Group 6 - dropAssetsItems: internal pane drag [Risk 3]
// ---------------------------------------------------------------------------

describe("DatabaseDataModelBrowserComponent - dropAssetsItems - internal pane drag [Group 6, Risk 3]", () => {

   it("should ignore internal drops onto non-folder targets", async () => {
      const { fixture, dragService } = await renderBrowser();
      const comp = fixture.componentInstance;
      dragService.getDragData.mockReturnValue({
         dragModelAssets: JSON.stringify([createPhysical()]),
      });

      comp.dropAssetsItems(createPhysical());

      expect(dragService.getDragData).not.toHaveBeenCalled();
   });

   // 🔁 Regression-sensitive: pane-to-folder moves must exclude folder rows and confirm before forwarding.
   it("should move only non-folder assets after confirmation when dropping onto a folder", async () => {
      const { fixture, dragService, dataModelBrowserService } = await renderBrowser();
      const comp = fixture.componentInstance;
      const target = createFolder({ name: "TargetFolder" });
      dragService.getDragData.mockReturnValue({
         dragModelAssets: JSON.stringify([
            createPhysical({ name: "Model1" }),
            createFolder({ name: "NestedFolder" }),
         ]),
      });
      const confirmSpy = vi.spyOn(ComponentTool, "showConfirmDialog")
         .mockResolvedValue("ok" as any);

      try {
         comp.dropAssetsItems(target);
         await Promise.resolve();

         expect(dataModelBrowserService.moveModelsToTarget).toHaveBeenCalledWith(
            [expect.objectContaining({ name: "Model1", type: PHYSICAL_VIEW_ASSET })],
            "TargetFolder",
            expect.any(Function));
      }
      finally {
         confirmSpy.mockRestore();
      }
   });
});

// ---------------------------------------------------------------------------
// Group 7 - deleteSelected: selection clearing [Risk 3]
// ---------------------------------------------------------------------------

describe("DatabaseDataModelBrowserComponent - deleteSelected - selection clearing [Group 7, Risk 3]", () => {

   // 🔁 Regression-sensitive: stale selected rows after delete cause duplicate or wrong batch operations.
   it("should clear selectedItems after the delete callback runs", async () => {
      const { fixture, dataModelBrowserService } = await renderBrowser();
      const comp = fixture.componentInstance;
      comp.databaseName = "SalesDB";
      comp.selectedItems = [createPhysical({ name: "ToDelete" })];

      comp.deleteSelected();

      expect(dataModelBrowserService.deleteModels).toHaveBeenCalledWith(
         "SalesDB",
         comp.selectedItems,
         expect.any(Function));

      const onSuccess = dataModelBrowserService.deleteModels.mock.calls[0][2];
      onSuccess();

      expect(comp.selectedItems).toEqual([]);
   });
});

// ---------------------------------------------------------------------------
// Group 8 - ngOnDestroy: subscription cleanup [Risk 3]
// ---------------------------------------------------------------------------

describe("DatabaseDataModelBrowserComponent - ngOnDestroy - subscription cleanup [Group 8, Risk 3]", () => {

   // 🔁 Regression-sensitive: leaked route/service subscriptions refresh stale folders after navigate away.
   it("should unsubscribe and null out subscriptions on destroy", async () => {
      const { fixture } = await renderBrowser();
      const comp = fixture.componentInstance;
      const subscriptions = (comp as any).subscriptions;
      const unsubscribeSpy = vi.spyOn(subscriptions, "unsubscribe");

      comp.ngOnDestroy();

      expect(unsubscribeSpy).toHaveBeenCalled();
      expect((comp as any).subscriptions).toBeNull();
   });
});

// ---------------------------------------------------------------------------
// Group 9 - ngOnInit: subscription data flow [Risk 3] (M4 mandatory)
// ---------------------------------------------------------------------------

describe("DatabaseDataModelBrowserComponent - ngOnInit - subscription data flow [Group 9, Risk 3]", () => {

   afterEach(() => {
      TestBed.inject(HttpTestingController).verify();
   });

   // 🔁 Regression-sensitive: route query params must decode databaseName/folderName before refreshModels runs.
   it("should decode databaseName and folderName from queryParamMap emission before calling refreshModels", async () => {
      const { fixture, routeSubject } = await renderBrowserControlled();
      const comp = fixture.componentInstance;
      const refreshSpy = vi.spyOn(comp as any, "refreshModels").mockImplementation(() => {});

      routeSubject.next(convertToParamMap({ databaseName: "TestDB", folderName: "FolderA" }));

      expect(comp.databaseName).toBe("TestDB");
      expect(comp.folderName).toBe("FolderA");
      expect(refreshSpy).toHaveBeenCalled();
   });

   it("should call refreshModels when dataModelBrowserService emits a changed event", async () => {
      const { fixture, changedSubject } = await renderBrowserControlled();
      const comp = fixture.componentInstance;
      const refreshSpy = vi.spyOn(comp as any, "refreshModels").mockImplementation(() => {});

      changedSubject.next();

      expect(refreshSpy).toHaveBeenCalled();
   });

   it("should set searchView to true and capture currentSearchQuery when the query param is present", async () => {
      const { fixture, routeSubject } = await renderBrowserControlled();
      const comp = fixture.componentInstance;
      vi.spyOn(comp as any, "refreshModels").mockImplementation(() => {});

      routeSubject.next(convertToParamMap({ databaseName: "TestDB", query: "mySearch" }));

      expect(comp.searchView).toBe(true);
      expect(comp.currentSearchQuery).toBe("mySearch");
   });
});

// ---------------------------------------------------------------------------
// Group 10 - clickItem: navigation paths [Risk 2]
// ---------------------------------------------------------------------------

describe("DatabaseDataModelBrowserComponent - clickItem - navigation paths [Group 10, Risk 2]", () => {

   // 🔁 Regression-sensitive: clicking a folder must route to the folder browser, not trigger editModel.
   it("should navigate to the databaseModels route with folderName when a folder item is clicked", async () => {
      const { fixture, router } = await renderBrowser();
      const comp = fixture.componentInstance;
      comp.databaseName = "SalesDB";

      comp.clickItem(createFolder({ name: "Reports" }));

      expect(router.navigate).toHaveBeenCalledWith(
         ["/portal/tab/data/datasources/databaseModels"],
         expect.objectContaining({
            queryParams: expect.objectContaining({ databaseName: "SalesDB", folderName: "Reports" }),
         })
      );
   });

   it("should navigate to the edit route when an editable non-folder item is clicked", async () => {
      const { fixture, router } = await renderBrowser();
      const comp = fixture.componentInstance;
      comp.databaseName = "SalesDB";

      comp.clickItem(createPhysical({ name: "PhysView", editable: true, parentView: null }));

      expect(router.navigate).toHaveBeenCalledWith(
         expect.arrayContaining(["/portal/tab/data/datasources/database"]),
         expect.anything()
      );
   });

   it("should not navigate when a non-editable non-folder item is clicked", async () => {
      const { fixture, router } = await renderBrowser();
      const comp = fixture.componentInstance;
      comp.databaseName = "SalesDB";

      comp.clickItem(createPhysical({ editable: false }));

      expect(router.navigate).not.toHaveBeenCalled();
   });
});

// ---------------------------------------------------------------------------
// Group 11 - updateModels HTTP states [Risk 2] (A1b three-state coverage)
// ---------------------------------------------------------------------------

describe("DatabaseDataModelBrowserComponent - updateModels - HTTP states [Group 11, Risk 2]", () => {

   afterEach(() => {
      TestBed.inject(HttpTestingController).verify();
   });

   // 🔁 Regression-sensitive: folders must precede non-folder models in the list; sortModels() must run post-load.
   it("should populate models and place folders first after a successful HTTP GET", async () => {
      const { fixture, httpMock } = await renderBrowser();
      const comp = fixture.componentInstance;
      comp.databaseName = "TestDB";

      (comp as any).refreshModels();

      const req = httpMock.expectOne(r => r.url.includes("dataModel/browse"));
      req.flush({
         listModel: {
            items: [createPhysical({ name: "View1" }), createFolder({ name: "Folder1" })],
            editable: true,
         },
         dbEditable: true,
         dateFormat: "yyyy-MM-dd HH:mm:ss",
      });

      await waitFor(() => expect(comp.models).toHaveLength(2));
      expect(comp.models[0].type).toBe(FOLDER_ASSET);
   });

   it("should silently swallow the HTTP error and leave models empty", async () => {
      const { fixture, httpMock } = await renderBrowser();
      const comp = fixture.componentInstance;
      comp.databaseName = "TestDB";

      (comp as any).refreshModels();

      const req = httpMock.expectOne(r => r.url.includes("dataModel/browse"));
      req.flush("Server Error", { status: 500, statusText: "Internal Server Error" });

      await fixture.whenStable();
      expect(comp.models).toHaveLength(0);
   });
});

// ---------------------------------------------------------------------------
// Group 12 - refreshSearchBrowser: HTTP error notification [Risk 2] (A1b three-state coverage)
// ---------------------------------------------------------------------------

describe("DatabaseDataModelBrowserComponent - refreshSearchBrowser - HTTP error notification [Group 12, Risk 2]", () => {

   afterEach(() => {
      TestBed.inject(HttpTestingController).verify();
   });

   // 🔁 Regression-sensitive: search errors must surface to the user — a silent failure looks like empty results.
   it("should call notifications.danger with the search-error key when the search HTTP POST fails", async () => {
      const { fixture, httpMock } = await renderBrowser();
      const comp = fixture.componentInstance;
      comp.databaseName = "TestDB";
      comp.notifications = { danger: vi.fn(), success: vi.fn() } as any;

      (comp as any).refreshSearchBrowser("/", "testQuery");

      const req = httpMock.expectOne(r => r.url.includes("search/dataModel"));
      req.flush("Error", { status: 500, statusText: "Internal Server Error" });

      await waitFor(() =>
         expect(comp.notifications.danger).toHaveBeenCalledWith("_#(js:data.datasets.searchError)")
      );
   });
});

// ---------------------------------------------------------------------------
// Group 13 - getTypeLabel: type label display contract [Risk 2]
// ---------------------------------------------------------------------------

describe("DatabaseDataModelBrowserComponent - getTypeLabel - type label display contract [Group 13, Risk 2]", () => {

   it("should return the XPARTITION label for a non-extended physical view", async () => {
      const { fixture } = await renderBrowser();
      expect(fixture.componentInstance.getTypeLabel(createPhysical({ parentView: null }))).toBe("_#(js:asset.type.XPARTITION)");
   });

   it("should return the Extended View label for a physical view that has a parentView", async () => {
      const { fixture } = await renderBrowser();
      expect(fixture.componentInstance.getTypeLabel(createPhysical({ parentView: "BaseView" }))).toBe("_#(js:Extended View)");
   });

   it("should return the XLOGICALMODEL label for a non-extended logical model", async () => {
      const { fixture } = await renderBrowser();
      expect(fixture.componentInstance.getTypeLabel(createLogical({ parentModel: null }))).toBe("_#(js:asset.type.XLOGICALMODEL)");
   });

   it("should return the Folder label for a folder asset", async () => {
      const { fixture } = await renderBrowser();
      expect(fixture.componentInstance.getTypeLabel(createFolder())).toBe("_#(js:Folder)");
   });
});

// ---------------------------------------------------------------------------
// Group 14 - getBasedView: based-view display contract [Risk 2]
// ---------------------------------------------------------------------------

describe("DatabaseDataModelBrowserComponent - getBasedView - based-view display contract [Group 14, Risk 2]", () => {

   // 🔁 Regression-sensitive: based-view display must distinguish logical variants and default connection fallback.
   it("should return an empty string for a non-logical-model asset", async () => {
      const { fixture } = await renderBrowser();
      expect(fixture.componentInstance.getBasedView(createPhysical())).toBe("");
   });

   it("should return only the physicalModel name for a non-extended logical model", async () => {
      const { fixture } = await renderBrowser();
      const item = createLogical({ physicalModel: "PhysView", parentModel: null });
      expect(fixture.componentInstance.getBasedView(item)).toBe("PhysView");
   });

   it("should return physicalModel/connection for an extended logical model with a named connection", async () => {
      const { fixture } = await renderBrowser();
      const item = createLogical({ physicalModel: "PhysView", parentModel: "BaseModel", connection: "ConnA" });
      expect(fixture.componentInstance.getBasedView(item)).toBe("PhysView/ConnA");
   });

   it("should return physicalModel/(Default Connection) for an extended logical model without a connection", async () => {
      const { fixture } = await renderBrowser();
      const item = createLogical({ physicalModel: "PhysView", parentModel: "BaseModel", connection: null });
      expect(fixture.componentInstance.getBasedView(item)).toBe("PhysView/(Default Connection)");
   });
});

// ---------------------------------------------------------------------------
// Group 15 - updateSortOptions: sort toggle contract [Risk 2]
// ---------------------------------------------------------------------------

describe("DatabaseDataModelBrowserComponent - updateSortOptions - sort toggle contract [Group 15, Risk 2]", () => {

   // 🔁 Regression-sensitive: repeated clicks on the same column header must toggle direction, not reset to ascending.
   it("should switch sort direction to DESCENDING when the current key is clicked while ASCENDING", async () => {
      const { fixture } = await renderBrowser();
      const comp = fixture.componentInstance;
      expect(comp.sortOptions.type).toBe(SortTypes.ASCENDING);

      comp.updateSortOptions("name");

      expect(comp.sortOptions.type).toBe(SortTypes.DESCENDING);
      expect(comp.sortOptions.keys).toContain("name");
   });

   it("should toggle back to ASCENDING when the current key is clicked again while DESCENDING", async () => {
      const { fixture } = await renderBrowser();
      const comp = fixture.componentInstance;
      comp.sortOptions.type = SortTypes.DESCENDING;

      comp.updateSortOptions("name");

      expect(comp.sortOptions.type).toBe(SortTypes.ASCENDING);
   });

   it("should set the new key and reset direction to ASCENDING when a different sort key is applied", async () => {
      const { fixture } = await renderBrowser();
      const comp = fixture.componentInstance;
      comp.sortOptions.type = SortTypes.DESCENDING;

      comp.updateSortOptions("createdBy");

      expect(comp.sortOptions.keys).toEqual(["createdBy"]);
      expect(comp.sortOptions.type).toBe(SortTypes.ASCENDING);
   });
});

// ---------------------------------------------------------------------------
// Group 16 - setShowDetailsItem: details panel toggle [Risk 1]
// ---------------------------------------------------------------------------

describe("DatabaseDataModelBrowserComponent - setShowDetailsItem - details panel toggle [Group 16, Risk 1]", () => {

   // 🔁 Regression-sensitive: clicking a new item must open the details panel for exactly that item.
   it("should set showDetailsItem to the clicked item when no item is currently shown", async () => {
      const { fixture } = await renderBrowser();
      const comp = fixture.componentInstance;
      const item = createPhysical();
      comp.showDetailsItem = null;

      comp.setShowDetailsItem(item);

      expect(comp.showDetailsItem).toBe(item);
   });

   it("should clear showDetailsItem to null when the currently shown item is clicked again", async () => {
      const { fixture } = await renderBrowser();
      const comp = fixture.componentInstance;
      const item = createPhysical();
      comp.showDetailsItem = item;

      comp.setShowDetailsItem(item);

      expect(comp.showDetailsItem).toBeNull();
   });
});
