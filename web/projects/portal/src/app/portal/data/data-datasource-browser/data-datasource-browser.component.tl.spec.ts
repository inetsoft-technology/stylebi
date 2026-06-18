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
 * DataDatasourceBrowserComponent - Angular Testing Library style
 *
 * User goals:
 *   1. Browse folders and data sources without losing selection or seeing stale status data.
 *   2. Refresh connection status and recover from backend errors.
 *   3. Search, delete, and move assets without routing to the wrong folder or moving invalid items.
 *
 * Risk-first coverage:
 *   Group 1 [Risk 3] - browser refresh: route-scoped load, folder-first sorting, status lookup.
 *   Group 2 [Risk 3] - status refresh: selected-only request scope and error recovery.
 *   Group 3 [Risk 2] - search: route creation and backend error notification.
 *   Group 4 [Risk 3] - deleteSelected: request shape and confirmation-owned delete.
 *   Group 5 [Risk 3] - drag/drop: target filtering and empty-pane/non-folder drop behavior.
 *   Group 6 [Risk 2] - action guards: direct method calls must honor disabled UI contracts.
 *   Group 7 [Risk 3] - search result location: full parent path must be preserved.
 *
 * KEY contracts:
 *   Folder rows are navigable/move targets; data source rows are not move targets.
 *   Selection-mode status refresh only checks selected non-folder data sources.
 *   Bulk delete payload must separate folders from data sources.
 */

import { type Mock } from "vitest";
import { NgClass } from "@angular/common";
import { provideHttpClient } from "@angular/common/http";
import { Component, Directive, Input, NO_ERRORS_SCHEMA } from "@angular/core";
import { FormsModule } from "@angular/forms";
import { ActivatedRoute, convertToParamMap, ParamMap, Router } from "@angular/router";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { render, waitFor } from "@testing-library/angular";
import { http, HttpResponse } from "msw";
import { BehaviorSubject, EMPTY, of, Subject } from "rxjs";
import { AssetEntry } from "../../../../../../shared/data/asset-entry";
import { AssetType } from "../../../../../../shared/data/asset-type";
import { DataSourceInfo } from "../model/data-source-info";
import { DataSourceStatus } from "../model/data-source-status";
import { PortalDataType } from "../data-navigation-tree/portal-data-type";
import { RepositoryClientService } from "../../../common/repository-client/repository-client.service";
import { OpenComposerService } from "../../../common/services/open-composer.service";
import { ComponentTool } from "../../../common/util/component-tool";
import { StompClientService } from "../../../common/viewsheet-client";
import { DomService } from "../../../widget/dom-service/dom.service";
import { DragService } from "../../../widget/services/drag.service";
import { DataDatasourceBrowserComponent } from "./data-datasource-browser.component";
import { DataSourceBrowserModel } from "./data-source-browser-model";
import { DatasourceBrowserService } from "./datasource-browser.service";
import { server } from "@test-mocks/server";

interface NotificationMock {
   success: Mock;
   danger: Mock;
   info: Mock;
}

let currentNotifications: NotificationMock;

@Component({
   selector: "data-notifications",
   template: "",
   standalone: true
})
class DataNotificationsStubComponent {
   notifications = currentNotifications;
}

@Directive({
   selector: "[ngbTypeahead]",
   standalone: true
})
class StubNgbTypeaheadDirective {
   @Input() ngbTypeahead: unknown;
   @Input() focusFirst: unknown;
}

@Directive({
   selector: "[ngbDropdown]",
   standalone: true
})
class StubNgbDropdownDirective {
}

@Directive({
   selector: "[ngbDropdownToggle]",
   standalone: true
})
class StubNgbDropdownToggleDirective {
}

@Directive({
   selector: "[ngbDropdownMenu]",
   standalone: true
})
class StubNgbDropdownMenuDirective {
}

@Directive({
   selector: "[routerLink]",
   standalone: true
})
class StubRouterLinkDirective {
   @Input() routerLink: unknown;
   @Input() queryParams: unknown;
}

interface RenderOptions {
   queryParams?: Record<string, string>;
   browserModel?: DataSourceBrowserModel;
   statusResponse?: DataSourceStatus[];
   composerOpen?: boolean;
}

function makeNotifications(): NotificationMock {
   return {
      success: vi.fn(),
      danger: vi.fn(),
      info: vi.fn()
   };
}

function makeDataSource(name: string, path: string,
                        typeName: string = PortalDataType.DATABASE,
                        overrides: Partial<DataSourceInfo> = {}): DataSourceInfo
{
   return {
      name,
      path,
      type: { name: typeName, label: typeName },
      createdBy: "admin",
      createdDate: 1,
      createdDateLabel: "created",
      dateFormat: "YYYY-MM-DD HH:mm:ss",
      editable: true,
      deletable: true,
      queryCreatable: true,
      connected: null,
      statusMessage: null,
      hasSubFolder: false,
      ...overrides
   };
}

function makeBrowserModel(dataSourceList: DataSourceInfo[] = []): DataSourceBrowserModel {
   return {
      dataSourceList,
      currentFolder: [],
      root: true,
      newDatasourceEnabled: true,
      newVpmEnabled: true
   };
}

function makeAsset(path: string, type: AssetType): AssetEntry {
   return {
      scope: 0,
      type,
      user: "admin",
      path,
      alias: null,
      identifier: `0^${type}^admin^${path}`,
      properties: {},
      description: `${path} description`,
      organization: "org"
   };
}

function deferred() {
   let resolve!: () => void;
   const promise = new Promise<void>(res => resolve = res);
   return { promise, resolve };
}

async function renderComponent(options: RenderOptions = {}) {
   const browserModel = options.browserModel ?? makeBrowserModel();
   const queryParamSubject = new BehaviorSubject<ParamMap>(
      convertToParamMap(options.queryParams ?? {}));
   const routerEvents = new Subject<any>();
   const repositoryDataChanged = new Subject<void>();
   const onCreateEvent = new Subject<any>();
   const browserRequests: URL[] = [];
   const statusRequests: any[] = [];

   currentNotifications = makeNotifications();

   const mockRouter = {
      navigate: vi.fn().mockResolvedValue(true),
      events: routerEvents.asObservable(),
      url: "/portal/tab/data/datasources"
   };
   const mockRoute = {
      queryParamMap: queryParamSubject.asObservable(),
      parent: { snapshot: {} },
      snapshot: {}
   };
   const datasourceService = {
      changeFolder: vi.fn(),
      refreshTree: vi.fn(),
      getPhysicalTablePermission: vi.fn(() => of(true)),
      renameDataSourceFolder: vi.fn(),
      moveDataSource: vi.fn(),
      moveSelected: vi.fn((_items: DataSourceInfo[], _path: string, callback?: Function) => {
         if(callback) {
            callback();
         }
      }),
      moveDataSourcesToFolder: vi.fn(),
      deleteDataSourceFolder: vi.fn(),
      deleteDataSourceByInfo: vi.fn(),
      createDataSourceInfos: vi.fn(() => []),
      onCreateEvent
   };
   const dragService = {
      put: vi.fn(),
      getDragData: vi.fn(() => ({}))
   };

   server.use(
      http.get("*/api/data/datasources/browser", ({ request }) => {
         browserRequests.push(new URL(request.url));
         return HttpResponse.json(browserModel);
      }),
      http.post("*/api/data/datasources/statuses", async ({ request }) => {
         statusRequests.push(await request.json());
         return HttpResponse.json(options.statusResponse ?? []);
      })
   );

   const { fixture } = await render(DataDatasourceBrowserComponent, {
      componentImports: [
         FormsModule,
         NgClass,
         StubNgbTypeaheadDirective,
         StubNgbDropdownDirective,
         StubNgbDropdownToggleDirective,
         StubNgbDropdownMenuDirective,
         StubRouterLinkDirective,
         DataNotificationsStubComponent
      ],
      providers: [
         provideHttpClient(),
         {
            provide: StompClientService,
            useValue: {
               connect: vi.fn(() => EMPTY),
               whenDisconnected: vi.fn(() => EMPTY),
               reconnectError: vi.fn(() => EMPTY)
            }
         },
         { provide: ActivatedRoute, useValue: mockRoute },
         { provide: Router, useValue: mockRouter },
         { provide: NgbModal, useValue: { open: vi.fn() } },
         { provide: RepositoryClientService, useValue: {
            connect: vi.fn(),
            dataChanged: repositoryDataChanged.asObservable()
         }},
         { provide: DatasourceBrowserService, useValue: datasourceService },
         { provide: OpenComposerService, useValue: { composerOpen: of(options.composerOpen ?? false) } },
         { provide: DragService, useValue: dragService },
         { provide: DomService, useValue: {} }
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
      routerEvents,
      repositoryDataChanged,
      datasourceService,
      dragService,
      notifications: currentNotifications,
      browserRequests,
      statusRequests,
      onCreateEvent
   };
}

afterEach(() => {
   vi.restoreAllMocks();
});

// ---------------------------------------------------------------------------
// Group 1 - browser refresh: route scoped load + status wiring [Risk 3]
// ---------------------------------------------------------------------------

describe("DataDatasourceBrowserComponent - browser refresh [Group 1, Risk 3]", () => {
   // 🔁 Regression-sensitive: refresh combines route params, sorting, selection mapping and status fetch.
   it("should request the route folder, sort folders before data sources, and fetch statuses only for data sources", async () => {
      const folder = makeDataSource("Folder", "root/Folder", PortalDataType.DATA_SOURCE_FOLDER);
      const beta = makeDataSource("Beta", "root/Beta", PortalDataType.DATABASE);
      const alpha = makeDataSource("Alpha", "root/Alpha", PortalDataType.XMLA_SOURCE);

      const { comp, browserRequests, statusRequests } = await renderComponent({
         queryParams: { path: "root", scope: "0" },
         browserModel: makeBrowserModel([beta, folder, alpha]),
         statusResponse: [
            { connected: true, message: "alpha ok" },
            { connected: false, message: "beta down" }
         ]
      });

      await waitFor(() =>
         expect(comp.datasources.map(ds => ds.name)).toEqual(["Folder", "Alpha", "Beta"]));
      expect(browserRequests[0].searchParams.get("path")).toBe("root");

      await waitFor(() => expect(statusRequests).toHaveLength(1));
      expect(statusRequests[0].paths).toEqual(["root/Alpha", "root/Beta"]);
      expect(statusRequests[0].updateStatus).toBe(false);

      await waitFor(() => expect(comp.datasources[1].statusMessage).toBe("alpha ok"));
      expect(comp.datasources[2].statusMessage).toBe("beta down");
      expect(comp.datasources[2].connected).toBe(false);
   });

   it("should remap selectedItems to refreshed objects with the same path", async () => {
      const oldInfo = makeDataSource("Old Name", "root/DS", PortalDataType.DATABASE);
      const { comp, queryParamSubject } = await renderComponent({
         browserModel: makeBrowserModel([oldInfo])
      });

      await waitFor(() => expect(comp.datasources).toHaveLength(1));
      const selectedBeforeRefresh = comp.datasources[0];
      comp.selectedItems = [selectedBeforeRefresh];

      const freshInfo = makeDataSource("Fresh Name", "root/DS", PortalDataType.DATABASE);
      server.use(
         http.get("*/api/data/datasources/browser", () =>
            HttpResponse.json(makeBrowserModel([freshInfo])))
      );

      queryParamSubject.next(convertToParamMap({}));

      await waitFor(() => expect(comp.datasources[0].name).toBe("Fresh Name"));
      expect(comp.selectedItems).toHaveLength(1);
      expect(comp.selectedItems[0]).not.toBe(selectedBeforeRefresh);
      expect(comp.selectedItems[0].name).toBe("Fresh Name");
      expect(comp.selectedItems[0].path).toBe("root/DS");
   });
});

// ---------------------------------------------------------------------------
// Group 2 - status refresh: selected-only request + error recovery [Risk 3]
// ---------------------------------------------------------------------------

describe("DataDatasourceBrowserComponent - status refresh [Group 2, Risk 3]", () => {
   // 🔁 Regression-sensitive: selection mode must not refresh every data source or duplicate in-flight work.
   it("should refresh only selected data sources and suppress duplicate requests while updating", async () => {
      const dsA = makeDataSource("A", "A", PortalDataType.DATABASE);
      const dsB = makeDataSource("B", "B", PortalDataType.DATABASE);
      const pending = deferred();
      const statusCalls: any[] = [];

      const { comp } = await renderComponent();
      server.use(
         http.post("*/api/data/datasources/statuses", async ({ request }) => {
            statusCalls.push(await request.json());
            await pending.promise;
            return HttpResponse.json([{ connected: true, message: "selected ok" }]);
         })
      );

      comp.datasources = [dsA, dsB];
      comp.selectedItems = [dsB];
      comp.selectionOn = true;

      comp.loadDataSourceStatus();
      expect(comp.updatingStatus).toBe(true);
      expect(dsB.statusMessage).toBe("_#(js:data.datasources.attemptingToConnectToDataSource)");

      comp.loadDataSourceStatus();
      await waitFor(() => expect(statusCalls).toHaveLength(1));
      expect(statusCalls[0].paths).toEqual(["B"]);
      expect(dsA.statusMessage).toBeNull();

      pending.resolve();

      await waitFor(() => expect(comp.updatingStatus).toBe(false));
      expect(dsB.connected).toBe(true);
      expect(dsB.statusMessage).toBe("selected ok");
   });

   it("should reset updatingStatus after a status refresh request errors", async () => {
      const ds = makeDataSource("Broken", "Broken", PortalDataType.DATABASE);
      const { comp } = await renderComponent();

      server.use(
         http.post("*/api/data/datasources/statuses", () =>
            new HttpResponse(null, { status: 500 }))
      );

      comp.datasources = [ds];
      comp.loadDataSourceStatus();

      await waitFor(() =>
         expect(ds.statusMessage).toBe("_#(js:data.datasources.problemRetrievingDataSourceStatus)"));
      expect(ds.connected).toBe(false);
      expect(comp.updatingStatus).toBe(false);
   });
});

// ---------------------------------------------------------------------------
// Group 3 - search route and error handling [Risk 2]
// ---------------------------------------------------------------------------

describe("DataDatasourceBrowserComponent - search [Group 3, Risk 2]", () => {

   it("should clear results and show a danger notification when search load fails", async () => {
      server.use(
         http.post("*/api/data/search/dataSources", () =>
            new HttpResponse(null, { status: 500 }))
      );

      const { comp, notifications } = await renderComponent({
         queryParams: { query: "missing", path: "root", scope: "0" },
         browserModel: makeBrowserModel()
      });

      await waitFor(() =>
         expect(notifications.danger).toHaveBeenCalledWith("_#(js:data.datasets.searchError)"));
      expect(comp.searchView).toBe(true);
      expect(comp.datasources).toEqual([]);
   });
});

// ---------------------------------------------------------------------------
// Group 4 - deleteSelected request shape + confirmation [Risk 3]
// ---------------------------------------------------------------------------

describe("DataDatasourceBrowserComponent - deleteSelected [Group 4, Risk 3]", () => {
   // 🔁 Regression-sensitive: backend contract requires folders and data sources in separate arrays.
   it("should separate selected folders from data sources and delete only after confirmation", async () => {
      const folder = makeDataSource("Folder", "root/Folder", PortalDataType.DATA_SOURCE_FOLDER);
      const ds = makeDataSource("DS", "root/DS", PortalDataType.DATABASE);
      let dependencyRequest: any = null;
      let deleteRequest: any = null;
      const confirmSpy = vi.spyOn(ComponentTool, "showConfirmDialog")
         .mockResolvedValue("ok");

      server.use(
         http.post("*/api/data/datasources/checkOuterDependencies/selected", async ({ request }) => {
            dependencyRequest = await request.json();
            return HttpResponse.json(null);
         }),
         http.post("*/api/data/datasources/deleteDataSources", async ({ request }) => {
            deleteRequest = await request.json();
            return HttpResponse.json({});
         })
      );

      const { comp } = await renderComponent();
      comp.selectedItems = [folder, ds];

      comp.deleteSelected();

      await waitFor(() => expect(deleteRequest).toBeTruthy());
      expect(dependencyRequest).toEqual({
         dataSources: [{ name: "DS", path: "root/DS" }],
         folders: [{ name: "Folder", path: "root/Folder" }]
      });
      expect(deleteRequest).toEqual({
         dataSources: [{ name: "DS", path: "root/DS" }],
         folders: [{ name: "Folder", path: "root/Folder" }]
      });
      expect(confirmSpy).toHaveBeenCalledWith(expect.anything(), "_#(js:Delete)",
         "_#(js:data.datasets.confirmDeleteItems)");
      expect(comp.selectedItems).toEqual([]);
   });
});

// ---------------------------------------------------------------------------
// Group 5 - drag/drop filtering and target handling [Risk 3]
// ---------------------------------------------------------------------------

describe("DataDatasourceBrowserComponent - drag/drop [Group 5, Risk 3]", () => {
   it("should move dragged data sources to the current folder when dropped on the empty pane", async () => {
      const source = makeDataSource("Source", "source/Source", PortalDataType.DATABASE);
      const confirmSpy = vi.spyOn(ComponentTool, "showConfirmDialog")
         .mockResolvedValue("ok");
      const { comp, dragService, datasourceService } = await renderComponent();
      comp.currentFolderPathString = "target";
      (dragService.getDragData as Mock).mockReturnValue({
         dragDataSources: JSON.stringify([source])
      });

      const event = { stopPropagation: vi.fn() } as any as DragEvent;

      expect(() => comp.dropAssets(event, null)).not.toThrow();
      await waitFor(() => expect(confirmSpy).toHaveBeenCalled());
      await waitFor(() => expect(datasourceService.moveDataSourcesToFolder)
         .toHaveBeenCalledWith([source], "target", expect.any(Function)));
   });

   it("should stop propagation when dropping on a non-folder data source row", async () => {
      const target = makeDataSource("Target", "target/Target", PortalDataType.DATABASE);
      const { comp, dragService } = await renderComponent();
      const event = { stopPropagation: vi.fn() } as any as DragEvent;

      comp.dropAssets(event, target);

      expect(event.stopPropagation).toHaveBeenCalled();
      expect(dragService.getDragData).not.toHaveBeenCalled();
   });

   // 🔁 Regression-sensitive: external tree drags must reject self, descendant and unsupported assets.
   it("should filter external tree drag data before moving valid data source assets", async () => {
      const validAsset = makeAsset("source/Valid", AssetType.DATA_SOURCE);
      const sameTargetAsset = makeAsset("target/child", AssetType.DATA_SOURCE);
      const ancestorAsset = makeAsset("target", AssetType.DATA_SOURCE_FOLDER);
      const unsupportedAsset = makeAsset("source/Sheet", AssetType.WORKSHEET);
      const validInfo = makeDataSource("Valid", "source/Valid", PortalDataType.DATABASE);
      const targetFolder = makeDataSource("Child", "target/child",
         PortalDataType.DATA_SOURCE_FOLDER);

      vi.spyOn(ComponentTool, "showConfirmDialog").mockResolvedValue("ok");
      const { comp, datasourceService } = await renderComponent();
      (datasourceService.createDataSourceInfos as Mock).mockReturnValue([validInfo]);

      comp.dataTreeDragToPane(targetFolder, {
         external: JSON.stringify([validAsset, sameTargetAsset, ancestorAsset, unsupportedAsset])
      });

      await waitFor(() => expect(datasourceService.createDataSourceInfos)
         .toHaveBeenCalledWith([validAsset]));
      await waitFor(() => expect(datasourceService.moveDataSourcesToFolder)
         .toHaveBeenCalledWith([validInfo], "target/child", expect.any(Function)));
   });
});

// ---------------------------------------------------------------------------
// Group 6 - action guards [Risk 2]
// ---------------------------------------------------------------------------

describe("DataDatasourceBrowserComponent - action guards [Group 6, Risk 2]", () => {
   it("should not move a mixed selection when any selected item lacks edit/delete permission", async () => {
      const movable = makeDataSource("Movable", "Movable", PortalDataType.DATABASE);
      const locked = makeDataSource("Locked", "Locked", PortalDataType.DATABASE, {
         editable: true,
         deletable: false
      });
      const { comp, datasourceService } = await renderComponent();
      comp.selectedItems = [movable, locked];

      comp.moveSelected();

      expect(datasourceService.moveSelected).not.toHaveBeenCalled();
      expect(comp.selectedItems).toEqual([movable, locked]);
   });

   it("should emit create events only for editable non-folder data sources", async () => {
      const editable = makeDataSource("Editable", "Editable", PortalDataType.DATABASE);
      const folder = makeDataSource("Folder", "Folder", PortalDataType.DATA_SOURCE_FOLDER);
      const locked = makeDataSource("Locked", "Locked", PortalDataType.DATABASE, {
         editable: false
      });
      const emitted: any[] = [];
      const { comp, onCreateEvent } = await renderComponent();
      onCreateEvent.subscribe(event => emitted.push(event));

      comp.createPhysicalView(folder);
      comp.createVPM(locked);
      comp.createVPM(editable);

      expect(emitted).toEqual([{ datasource: editable, vpm: true }]);
   });
});

// ---------------------------------------------------------------------------
// Group 7 - search result parent routing [Risk 3]
// ---------------------------------------------------------------------------

describe("DataDatasourceBrowserComponent - search result location [Group 7, Risk 3]", () => {
   it("should preserve the full parent path in router params for nested search results", async () => {
      const { comp } = await renderComponent();

      expect(comp.getParentRouterLinkParams("root/folder/DS")).toEqual({
         path: "root/folder",
         scope: 0
      });
   });
});
