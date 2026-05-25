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
 * DataFolderBrowserComponent - Angular Testing Library style
 *
 * User goals:
 *   1. Browse worksheet folders and datasets in the right route/scope without stale selection.
 *   2. Search, delete, and move datasets without acting on locked or stale assets.
 *   3. Drag worksheets from folders or the data tree without moving duplicates, descendants, or wrong scopes.
 *   4. Leave the page without hidden subscriptions refreshing a destroyed browser.
 *
 * Risk-first coverage:
 *   Group 1 [Risk 3] - browser refresh/search lifecycle: route scope, sort order, selection remap, cleanup.
 *   Group 2 [Risk 3] - deleteSelected: request contract, confirmation ownership, disabled-action bypass.
 *   Group 3 [Risk 3] - move/drag: root filtering, HTTP move payloads, cross-scope drag grouping.
 *   Group 4 [Risk 2] - action routing/guards: search route and new worksheet permission guard.
 *
 * Confirmed bugs (it.failing - remove wrapper once fixed Issue #75145):
 *
 *   Bug A - repository-dataChanged-leaks-after-destroy (Group 1):
 *     repositoryClient.dataChanged is subscribed in ngOnInit but not added to routeParamSubscription
 *     or subscriptions, so it can refresh this component after ngOnDestroy.
 *
 *   Bug C - deleteSelected-error-does-not-refresh (Group 2):
 *     The delete request refresh is in the complete callback only. RxJS does not call complete
 *     after error, despite the comment saying refresh should happen even on failure.
 *
 *   Bug D - moveSelected-ignores-edit-delete-guard (Group 3):
 *     moveDisable is only a UI state; moveSelected directly calls moveAssets with locked assets.
 *
 *   Bug E - moveAssets0-reuses-all-assets-for-each-scope (Group 3):
 *     moveAssets0 builds a scopeMap but calls moveAssets(assets, target, key) for each key,
 *     instead of passing the per-scope scopeBrowserInfos collection.
 *
 * KEY contracts:
 *   Folder rows are displayed before worksheet rows after sorting.
 *   Bulk delete payload separates folders from worksheets as { folders, datasets }.
 *   Bulk move must drop children when their parent folder is already selected.
 *   Direct method calls must enforce the same permission guards advertised by the toolbar.
 */

import { provideHttpClient } from "@angular/common/http";
import { Component, NO_ERRORS_SCHEMA } from "@angular/core";
import { ActivatedRoute, convertToParamMap, ParamMap, Router } from "@angular/router";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { it } from "@jest/globals";
import { render, waitFor } from "@testing-library/angular";
import { http, HttpResponse } from "msw";
import { BehaviorSubject, EMPTY, Subject } from "rxjs";
import { AssetEntry } from "../../../../../../shared/data/asset-entry";
import { AssetType } from "../../../../../../shared/data/asset-type";
import { RepositoryClientService } from "../../../common/repository-client/repository-client.service";
import { ComponentTool } from "../../../common/util/component-tool";
import { StompClientService } from "../../../common/viewsheet-client";
import { DomService } from "../../../widget/dom-service/dom.service";
import { DragService } from "../../../widget/services/drag.service";
import { DataSourcesTreeActionsService } from "../data-navigation-tree/data-sources-tree-actions.service";
import { SearchResultsModel } from "../model/search-results-model";
import { WorksheetBrowserInfo } from "../model/worksheet-browser-info";
import { DataBrowserService } from "./data-browser.service";
import { DataFolderBrowserComponent } from "./data-folder-browser.component";
import { PortalDataBrowserModel } from "./portal-data-browser-model";
import { server } from "../../../../../../../mocks/server";

interface NotificationMock {
   success: jest.Mock;
   danger: jest.Mock;
   info: jest.Mock;
}

let currentNotifications: NotificationMock;

@Component({
   selector: "data-notifications",
   template: ""
})
class DataNotificationsStubComponent {
   notifications = currentNotifications;
}

interface RenderOptions {
   queryParams?: Record<string, string>;
   browserModel?: PortalDataBrowserModel;
   searchResponse?: SearchResultsModel;
}

function makeNotifications(): NotificationMock {
   return {
      success: jest.fn(),
      danger: jest.fn(),
      info: jest.fn()
   };
}

function makeWorksheet(name: string, path: string, type: AssetType = AssetType.WORKSHEET,
                       overrides: Partial<WorksheetBrowserInfo> = {}): WorksheetBrowserInfo
{
   return {
      name,
      path,
      type,
      scope: 1,
      description: `${name} description`,
      id: `1^${type}^admin^${path}`,
      createdBy: "admin",
      createdDate: 1,
      createdDateLabel: "created",
      modifiedDate: 2,
      dateFormat: "YYYY-MM-DD HH:mm:ss",
      modifiedDateLabel: "modified",
      editable: true,
      deletable: true,
      materialized: false,
      canMaterialize: true,
      canWorksheet: true,
      parentPath: "/",
      parentFolderCount: 0,
      hasSubFolder: 0,
      workSheetType: 0,
      ...overrides
   };
}

function makeBrowserModel(folders: WorksheetBrowserInfo[] = [],
                          files: WorksheetBrowserInfo[] = [],
                          currentFolder: WorksheetBrowserInfo[] = [],
                          worksheetAccess: boolean = true): PortalDataBrowserModel
{
   return {
      root: currentFolder.length === 0,
      worksheetAccess,
      currentFolder,
      folders,
      files
   };
}

function makeAssetEntry(path: string, type: AssetType, scope: number = 1): AssetEntry {
   return {
      scope,
      type,
      user: "admin",
      path,
      alias: null,
      identifier: `${scope}^${type}^admin^${path}`,
      properties: {},
      description: `${path} description`,
      organization: "org"
   };
}

async function renderComponent(options: RenderOptions = {}) {
   const browserModel = options.browserModel ?? makeBrowserModel();
   const queryParamSubject = new BehaviorSubject<ParamMap>(
      convertToParamMap(options.queryParams ?? {}));
   const repositoryDataChanged = new Subject<void>();
   const worksheetDetailsSubject = new Subject<any>();
   const browserRequests: URL[] = [];
   const searchRequests: any[] = [];

   currentNotifications = makeNotifications();

   const mockRouter = {
      navigate: jest.fn().mockResolvedValue(true),
      events: EMPTY,
      url: "/portal/tab/data/folder"
   };
   const mockRoute = {
      queryParamMap: queryParamSubject.asObservable(),
      parent: { snapshot: {} },
      snapshot: {}
   };
   const dataBrowserService = {
      newWorksheet: jest.fn(),
      openWorksheet: jest.fn(),
      renameAsset: jest.fn(),
      deleteAsset: jest.fn(),
      changeFolder: jest.fn(),
      changeMV: jest.fn()
   };
   const dragService = {
      put: jest.fn(),
      getDragData: jest.fn(() => ({}))
   };

   const browserHandler = ({ request }) => {
      browserRequests.push(new URL(request.url));
      return HttpResponse.json(browserModel);
   };

   server.use(
      http.get("*/api/portal/data/browser", browserHandler),
      http.get("*/api/portal/data/browser/*", browserHandler),
      http.post("*/api/data/search/datasets", async ({ request }) => {
         searchRequests.push(await request.json());
         return HttpResponse.json(options.searchResponse ?? { assets: [], assetNames: [] });
      })
   );

   const { fixture } = await render(DataFolderBrowserComponent, {
      declarations: [DataNotificationsStubComponent],
      providers: [
         provideHttpClient(),
         {
            provide: StompClientService,
            useValue: {
               connect: jest.fn(() => EMPTY),
               whenDisconnected: jest.fn(() => EMPTY),
               reconnectError: jest.fn(() => EMPTY)
            }
         },
         { provide: ActivatedRoute, useValue: mockRoute },
         { provide: Router, useValue: mockRouter },
         { provide: NgbModal, useValue: { open: jest.fn() } },
         { provide: RepositoryClientService, useValue: {
            connect: jest.fn(),
            dataChanged: repositoryDataChanged.asObservable()
         }},
         { provide: DataBrowserService, useValue: dataBrowserService },
         { provide: DragService, useValue: dragService },
         { provide: DomService, useValue: {} },
         { provide: DataSourcesTreeActionsService, useValue: {
            showWSFolderDetailsSubject: jest.fn(() => worksheetDetailsSubject.asObservable())
         }}
      ],
      schemas: [NO_ERRORS_SCHEMA]
   });

   await waitFor(() => expect(browserRequests.length).toBeGreaterThan(0));
   await fixture.whenStable();

   return {
      comp: fixture.componentInstance,
      fixture,
      mockRouter,
      mockRoute,
      queryParamSubject,
      repositoryDataChanged,
      worksheetDetailsSubject,
      dataBrowserService,
      dragService,
      notifications: currentNotifications,
      browserRequests,
      searchRequests
   };
}

afterEach(() => {
   jest.restoreAllMocks();
});

// ---------------------------------------------------------------------------
// Group 1 - browser refresh/search lifecycle [Risk 3]
// ---------------------------------------------------------------------------

describe("DataFolderBrowserComponent - browser refresh/search lifecycle [Group 1, Risk 3]", () => {
   // Regression-sensitive: route scope, folder-first sorting, and selected-object remapping can
   // silently drift when refreshBrowserContent or updateSelectedItems is refactored.
   it("should request the route folder, sort folders before worksheets, and remap stale selection on refresh", async () => {
      const folderZ = makeWorksheet("Zulu Folder", "root/Zulu Folder", AssetType.FOLDER);
      const folderA = makeWorksheet("Alpha Folder", "root/Alpha Folder", AssetType.FOLDER);
      const beta = makeWorksheet("Beta", "root/Beta");
      const alpha = makeWorksheet("Alpha", "root/Alpha");

      const { comp, browserRequests, queryParamSubject } = await renderComponent({
         queryParams: { path: "root", scope: "4" },
         browserModel: makeBrowserModel([folderZ, folderA], [beta, alpha])
      });

      await waitFor(() =>
         expect(comp.viewAssets.map(asset => asset.name))
            .toEqual(["Alpha Folder", "Zulu Folder", "Alpha", "Beta"]));
      expect(browserRequests[0].pathname).toContain("/api/portal/data/browser/root");
      expect(browserRequests[0].searchParams.get("scope")).toBe("4");

      const selectedBeforeRefresh = comp.datasets[0];
      comp.selectedItems = [selectedBeforeRefresh];
      comp.selectFile(selectedBeforeRefresh);

      const freshAlpha = makeWorksheet("Alpha Fresh", "root/Alpha");
      server.use(
         http.get("*/api/portal/data/browser/root", () =>
            HttpResponse.json(makeBrowserModel([], [freshAlpha])))
      );

      queryParamSubject.next(convertToParamMap({ path: "root", scope: "4" }));

      await waitFor(() => expect(comp.datasets[0].name).toBe("Alpha Fresh"));
      expect(comp.selectedItems).toHaveLength(1);
      expect(comp.selectedItems[0]).not.toBe(selectedBeforeRefresh);
      expect(comp.selectedItems[0].path).toBe("root/Alpha");
      expect(comp.selectedFile.name).toBe("Alpha Fresh");
   });

   // Regression-sensitive: failed search must not leave stale result assets visible.
   it("should clear search assets and show a danger notification when search loading fails", async () => {
      let searchErrorRequests = 0;
      const { comp, notifications, queryParamSubject } = await renderComponent();

      server.use(
         http.post("*/api/data/search/datasets", () => {
            searchErrorRequests++;
            return new HttpResponse(null, { status: 500 });
         })
      );

      queryParamSubject.next(convertToParamMap({ query: "missing", path: "root", scope: "1" }));

      await waitFor(() =>
         expect(notifications.danger).toHaveBeenCalledWith("_#(js:data.datasets.searchError)"));
      expect(comp.searchView).toBe(true);
      expect(comp.searchAssets).toEqual([]);
      expect(searchErrorRequests).toBe(1);
   });

   // Bug A: repositoryClient.dataChanged subscription is never unsubscribed in ngOnDestroy.
   it.failing("should not refresh browser content after the component is destroyed", async () => {
      const { fixture, repositoryDataChanged, browserRequests } = await renderComponent();
      const initialRequestCount = browserRequests.length;

      jest.useFakeTimers();
      try {
         fixture.destroy();
         repositoryDataChanged.next();
         jest.runAllTimers();
      } finally {
         jest.useRealTimers();
      }
      // setImmediate fires after all pending microtasks (including MSW Promise chains)
      await new Promise(resolve => setImmediate(resolve));

      expect(browserRequests).toHaveLength(initialRequestCount);
   });
});

// ---------------------------------------------------------------------------
// Group 2 - deleteSelected request contract and guards [Risk 3]
// ---------------------------------------------------------------------------

describe("DataFolderBrowserComponent - deleteSelected [Group 2, Risk 3]", () => {
   // Regression-sensitive: backend contract requires selected folders and worksheets in separate arrays.
   it("should separate selected folders from worksheets and delete only after confirmation", async () => {
      const folder = makeWorksheet("Folder", "Folder", AssetType.FOLDER);
      const worksheet = makeWorksheet("Sheet", "Sheet");
      let removableRequest: any = null;
      let deleteRequest: any = null;
      let refreshCount = 0;

      jest.spyOn(ComponentTool, "showConfirmDialog").mockResolvedValue("ok");

      const { comp, notifications } = await renderComponent();
      server.use(
         http.post("*/api/data/removeableStatuses", async ({ request }) => {
            removableRequest = await request.json();
            return HttpResponse.json({
               folderDependencies: ["folder consumer"],
               datasetDependencies: ["sheet consumer"]
            });
         }),
         http.post("*/api/data/removeAll", async ({ request }) => {
            deleteRequest = await request.json();
            return HttpResponse.json({});
         }),
         http.get("*/api/portal/data/browser", () => {
            refreshCount++;
            return HttpResponse.json(makeBrowserModel());
         })
      );

      comp.selectedItems = [folder, worksheet];
      comp.deleteSelected();

      await waitFor(() => expect(deleteRequest).toBeTruthy());
      expect(removableRequest).toEqual({
         folders: [{ path: "Folder", scope: 1 }],
         datasets: [{ path: "Sheet", scope: 1 }]
      });
      expect(deleteRequest.map((item: WorksheetBrowserInfo) => item.path)).toEqual(["Folder", "Sheet"]);
      expect(ComponentTool.showConfirmDialog).toHaveBeenCalledWith(expect.anything(),
         "_#(js:data.datasets.delete)", expect.stringContaining("_#(js:data.datasets.deleteItemsDependencyError)"));
      expect(ComponentTool.showConfirmDialog).toHaveBeenCalledWith(expect.anything(),
         "_#(js:data.datasets.delete)", expect.stringContaining("folder consumer"));
      await waitFor(() =>
         expect(notifications.success).toHaveBeenCalledWith("_#(js:data.datasets.deleteItemsSuccess)"));
      await waitFor(() => expect(refreshCount).toBeGreaterThan(0));
   });

   // Direct calls must enforce the same non-deletable contract as the toolbar.
   it("should not request deletion status when any selected item is not deletable", async () => {
      const locked = makeWorksheet("Locked", "Locked", AssetType.WORKSHEET, {
         deletable: false
      });

      const confirmSpy = jest.spyOn(ComponentTool, "showConfirmDialog").mockResolvedValue("cancel");

      const { comp } = await renderComponent();
      const postSpy = jest.spyOn((comp as any).httpClient, "post").mockReturnValue(EMPTY);

      comp.selectedItems = [locked];
      expect(comp.isSelectionDeletable()).toBe(false);

      comp.deleteSelected();

      expect(postSpy).not.toHaveBeenCalled();
      expect(confirmSpy).not.toHaveBeenCalled();
   });

   // Bug C: refresh lives in complete(), so partial server failures leave stale rows visible.
   it.failing("should refresh the browser even when the bulk delete request errors", async () => {
      const worksheet = makeWorksheet("Maybe Deleted", "Maybe Deleted");
      let refreshCount = 0;

      jest.spyOn(ComponentTool, "showConfirmDialog").mockResolvedValue("ok");

      const { comp, notifications } = await renderComponent();
      server.use(
         http.post("*/api/data/removeableStatuses", () =>
            HttpResponse.json({ folderDependencies: [], datasetDependencies: [] })),
         http.post("*/api/data/removeAll", () =>
            new HttpResponse(null, { status: 500 })),
         http.get("*/api/portal/data/browser", () => {
            refreshCount++;
            return HttpResponse.json(makeBrowserModel());
         })
      );

      comp.selectedItems = [worksheet];
      comp.deleteSelected();

      await waitFor(() =>
         expect(notifications.danger).toHaveBeenCalledWith("_#(js:data.datasets.deleteItemsError)"));
      await waitFor(() => expect(refreshCount).toBeGreaterThan(0));
   });
});

// ---------------------------------------------------------------------------
// Group 3 - move/drag root filtering and cross-scope behavior [Risk 3]
// ---------------------------------------------------------------------------

describe("DataFolderBrowserComponent - move/drag [Group 3, Risk 3]", () => {
   // Regression-sensitive: moving a selected parent and child together must send only the root parent.
   it("should move root selected assets once and include target/source scopes in folder and worksheet requests", async () => {
      const folder = makeWorksheet("Parent", "Parent", AssetType.FOLDER);
      const childWorksheet = makeWorksheet("Child", "Parent/Child");
      const peerWorksheet = makeWorksheet("Peer", "Peer");
      const target = makeWorksheet("Target", "Target", AssetType.FOLDER, { scope: 4 });
      let folderMoveRequest: any = null;
      let worksheetMoveRequest: any = null;
      let folderMoveUrl: URL = null;
      let worksheetMoveUrl: URL = null;

      const { comp, notifications } = await renderComponent({
         queryParams: { path: "current", scope: "1" },
         browserModel: makeBrowserModel([], [], [
            makeWorksheet("Current", "current", AssetType.FOLDER, { id: "current-id" })
         ])
      });
      server.use(
         http.post("*/api/data/folders/moveFolders", async ({ request }) => {
            folderMoveUrl = new URL(request.url);
            folderMoveRequest = await request.json();
            return HttpResponse.json([]);
         }),
         http.post("*/api/data/datasets/moveDatasets", async ({ request }) => {
            worksheetMoveUrl = new URL(request.url);
            worksheetMoveRequest = await request.json();
            return HttpResponse.json([]);
         }),
         http.get("*/api/portal/data/browser/current", () =>
            HttpResponse.json(makeBrowserModel()))
      );

      comp.moveAssets([childWorksheet, peerWorksheet, folder], target);

      await waitFor(() => expect(folderMoveRequest).toBeTruthy());
      await waitFor(() => expect(worksheetMoveRequest).toBeTruthy());
      expect(folderMoveRequest).toEqual([
         { path: "Target", oldPath: "Parent", name: "Parent", id: folder.id, date: 2 }
      ]);
      expect(worksheetMoveRequest).toEqual([
         { path: "Target", oldPath: "Peer", name: "Peer", id: peerWorksheet.id, date: 2 }
      ]);
      expect(folderMoveUrl.searchParams.get("targetScope")).toBe("4");
      expect(folderMoveUrl.searchParams.get("assetScope")).toBe("1");
      expect(worksheetMoveUrl.searchParams.get("targetScope")).toBe("4");
      expect(worksheetMoveUrl.searchParams.get("assetScope")).toBe("1");
      await waitFor(() =>
         expect(notifications.success).toHaveBeenCalledWith("_#(js:data.datasets.moveItemsSuccess)"));
   });

   // Regression-sensitive: external tree drags must reject self, ancestors/descendants, and unsupported types.
   it("should filter external tree drag data before moving worksheet assets", async () => {
      const target = makeWorksheet("Target Folder", "Target/Folder", AssetType.FOLDER);
      const validWorksheet = makeAssetEntry("Source/Sheet", AssetType.WORKSHEET);
      const sameTarget = makeAssetEntry("Target/Folder", AssetType.WORKSHEET);
      const ancestorFolder = makeAssetEntry("Target", AssetType.FOLDER);
      const unsupported = makeAssetEntry("Source/DataSource", AssetType.DATA_SOURCE);

      const { comp, dataBrowserService } = await renderComponent({
         queryParams: { path: "current", scope: "1" }
      });
      jest.spyOn(ComponentTool, "showConfirmDialog").mockResolvedValue("ok");
      const moveAssetsSpy = jest.spyOn(comp, "moveAssets").mockImplementation(jest.fn());

      comp.dataTreeDragToPane(target, {
         external: JSON.stringify([validWorksheet, sameTarget, ancestorFolder, unsupported])
      });

      await waitFor(() => expect(moveAssetsSpy).toHaveBeenCalledWith(
         [expect.objectContaining({
            name: "Sheet",
            path: "Source/Sheet",
            type: AssetType.WORKSHEET,
            scope: 1
         })],
         target,
         1
      ));
      expect(dataBrowserService.changeFolder).toHaveBeenCalledWith("current", 1);
   });

   // Bug D: moveSelected bypasses moveDisable and opens the move dialog for locked assets.
   it.failing("should not open the move dialog when selected items are not editable and deletable", async () => {
      const locked = makeWorksheet("Locked", "Locked", AssetType.WORKSHEET, {
         editable: true,
         deletable: false
      });
      const showDialogSpy = jest.spyOn(ComponentTool, "showDialog").mockReturnValue({} as any);
      const { comp } = await renderComponent();

      comp.selectedItems = [locked];
      expect(comp.moveDisable).toBe(true);

      comp.moveSelected();

      expect(showDialogSpy).not.toHaveBeenCalled();
   });

   // Bug E: each scope bucket should move only its own assets, not the full drag list.
   it.failing("should pass only the current scope bucket to moveAssets for cross-scope drag moves", async () => {
      const scope1Worksheet = makeWorksheet("Global Sheet", "Global Sheet", AssetType.WORKSHEET, {
         scope: 1
      });
      const scope4Worksheet = makeWorksheet("User Sheet", "User Sheet", AssetType.WORKSHEET, {
         scope: 4
      });
      const target = makeWorksheet("Target", "Target", AssetType.FOLDER, { scope: 1 });

      jest.spyOn(ComponentTool, "showConfirmDialog").mockResolvedValue("ok");
      const { comp, dragService } = await renderComponent();
      (dragService.getDragData as jest.Mock).mockReturnValue({
         dragWorksheets: JSON.stringify([scope1Worksheet, scope4Worksheet])
      });
      const moveAssetsSpy = jest.spyOn(comp, "moveAssets").mockImplementation(jest.fn());

      comp.assetsDroped(target);

      await waitFor(() => expect(moveAssetsSpy).toHaveBeenCalledTimes(2));
      expect(moveAssetsSpy).toHaveBeenNthCalledWith(1, [scope1Worksheet], target, 1);
      expect(moveAssetsSpy).toHaveBeenNthCalledWith(2, [scope4Worksheet], target, 4);
   });
});

// ---------------------------------------------------------------------------
// Group 4 - action routing and guards [Risk 2]
// ---------------------------------------------------------------------------

describe("DataFolderBrowserComponent - action routing and guards [Group 4, Risk 2]", () => {
   // Regression-sensitive: search navigation owns query/path/scope and clears transient input state.
   it("should navigate to worksheet search results with current path and scope", async () => {
      const { comp, mockRouter, mockRoute } = await renderComponent({
         queryParams: { path: "root", scope: "4" }
      });

      comp.search("orders");

      expect(mockRouter.navigate).toHaveBeenCalledWith(["folder"], {
         queryParams: { query: "orders", scope: "4", path: "root" },
         relativeTo: mockRoute.parent
      });
      expect(comp.searchQuery).toBeNull();
   });

   // Regression-sensitive: disabled New Worksheet UI can be bypassed by direct method calls.
   it("should create a worksheet only when the folder is editable and worksheet access is granted", async () => {
      const lockedFolder = makeWorksheet("Locked", "Locked", AssetType.FOLDER, {
         editable: false,
         id: "locked-id"
      });
      const { comp, dataBrowserService } = await renderComponent({
         browserModel: makeBrowserModel([], [], [lockedFolder], true)
      });

      comp.newWorksheet();
      expect(dataBrowserService.newWorksheet).not.toHaveBeenCalled();

      comp.currentFolderPath = [
         makeWorksheet("Editable", "Editable", AssetType.FOLDER, {
            editable: true,
            id: "editable-id"
         })
      ];
      comp.worksheetAccess = false;
      comp.newWorksheet();
      expect(dataBrowserService.newWorksheet).not.toHaveBeenCalled();

      comp.worksheetAccess = true;
      comp.newWorksheet();
      expect(dataBrowserService.newWorksheet).toHaveBeenCalledWith("editable-id");
   });
});
