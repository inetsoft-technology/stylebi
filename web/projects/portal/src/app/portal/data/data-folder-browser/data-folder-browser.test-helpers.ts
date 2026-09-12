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
 * Shared test helpers for DataFolderBrowserComponent specs.
 *
 * Extracted to avoid duplicating the ~150-line setup between the risk and
 * interaction spec files. Vitest isolates each spec file's module scope, so the
 * exported singleton mocks (DATA_BROWSER_MOCK, VS_CLIENT_MOCK, etc.) are
 * independent instances per file — sharing them here is safe.
 */

import { Component, NO_ERRORS_SCHEMA } from "@angular/core";
import { provideHttpClient } from "@angular/common/http";
import { ActivatedRoute, convertToParamMap, Router } from "@angular/router";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { render } from "@testing-library/angular";
import { of, Subject, Subscription } from "rxjs";
import { AssetType } from "../../../../../../shared/data/asset-type";
import { AssetEntryHelper } from "../../../common/data/asset-entry-helper";
import { RepositoryClientService } from "../../../common/repository-client/repository-client.service";
import { ViewsheetClientService } from "../../../common/viewsheet-client";
import { DomService } from "../../../widget/dom-service/dom.service";
import { DragService } from "../../../widget/services/drag.service";
import { DataSourcesTreeActionsService } from "../data-navigation-tree/data-sources-tree-actions.service";
import { WorksheetBrowserInfo } from "../model/worksheet-browser-info";
import { SsoHeartbeatService } from "../../../../../../shared/sso/sso-heartbeat.service";
import { DataNotificationsComponent } from "../data-notifications.component";
import { StubDataNotificationsComponent } from "../data-notifications.stub";
import { DataBrowserService } from "./data-browser.service";
import { DataFolderBrowserComponent } from "./data-folder-browser.component";
import { DataFolderListViewComponent } from "./data-folder-list-view/data-folder-list-view.component";

// ---------------------------------------------------------------------------
// Stub components
// ---------------------------------------------------------------------------

// DataFolderListViewComponent imports RouterLink which triggers Router APP_INITIALIZER → whenStable() hangs.
@Component({ selector: "data-folder-list-view", template: "", standalone: true })
export class StubDataFolderListViewComponent {}

// ---------------------------------------------------------------------------
// Shared mock singletons
// ---------------------------------------------------------------------------

export const DATA_BROWSER_MOCK = {
   newWorksheet: vi.fn(),
   openWorksheet: vi.fn(),
   renameAsset: vi.fn(),
   deleteAsset: vi.fn(),
   changeFolder: vi.fn(),
   changeMV: vi.fn(),
   folderChanged: new Subject<any>(),
   mvChanged: new Subject<any>(),
};

export const REPO_CLIENT_MOCK = {
   connect: vi.fn(),
   disconnect: vi.fn(),
   dataChanged: new Subject<void>(),
};

export const VS_CLIENT_MOCK = {
   connect: vi.fn(),
   disconnect: vi.fn(),
   sendEvent: vi.fn(),
   subscribe: vi.fn().mockReturnValue(new Subscription()),
   onMessageReceived: new Subject<any>(),
};

export const DRAG_SERVICE_MOCK = {
   put: vi.fn(),
   getDragData: vi.fn().mockReturnValue({}),
};

export const DOM_SERVICE_MOCK = { addPortalContent: vi.fn() };

export const DATASOURCES_TREE_ACTIONS_MOCK = {
   showWSFolderDetailsSubject: vi.fn().mockReturnValue(new Subject<any>()),
};

export const MODAL_MOCK = {
   open: vi.fn().mockImplementation(() => ({
      result: new Promise<any>(() => {}),
      componentInstance: { onCommit: new Subject<string>() },
      close: vi.fn(),
      dismiss: vi.fn(),
   })),
};

// ---------------------------------------------------------------------------
// Data factories
// ---------------------------------------------------------------------------

export function makeInfo(overrides: Partial<WorksheetBrowserInfo> = {}): WorksheetBrowserInfo {
   return {
      name: "Sheet",
      path: "Folder/Sheet",
      type: AssetType.WORKSHEET,
      scope: AssetEntryHelper.GLOBAL_SCOPE,
      description: "",
      id: "1^2^admin^Folder/Sheet",
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
      parentPath: "",
      parentFolderCount: 0,
      hasSubFolder: 0,
      workSheetType: 0,
      ...overrides,
   };
}

export function makeFolder(overrides: Partial<WorksheetBrowserInfo> = {}): WorksheetBrowserInfo {
   return makeInfo({
      name: "FolderA",
      path: "FolderA",
      type: AssetType.FOLDER,
      id: "1^1^admin^FolderA",
      ...overrides,
   });
}

// ---------------------------------------------------------------------------
// Render helper
// ---------------------------------------------------------------------------

export async function renderComponent(routeOverrides: { queryParams?: any } = {}) {
   const queryParams = routeOverrides.queryParams ?? {};
   const queryParamMap = convertToParamMap(queryParams);

   const routerMock = {
      navigate: vi.fn(),
      url: "/portal/tab/data/folder",
      events: new Subject<any>(),
   };
   const routeMock = {
      queryParamMap: of(queryParamMap),
      parent: null,
      snapshot: { _routerState: null },
   };

   const result = await render(DataFolderBrowserComponent, {
      providers: [
         provideHttpClient(),
         { provide: DataBrowserService, useValue: DATA_BROWSER_MOCK },
         { provide: ViewsheetClientService, useValue: VS_CLIENT_MOCK },
         { provide: RepositoryClientService, useValue: REPO_CLIENT_MOCK },
         { provide: NgbModal, useValue: MODAL_MOCK },
         { provide: ActivatedRoute, useValue: routeMock },
         { provide: Router, useValue: routerMock },
         { provide: DragService, useValue: DRAG_SERVICE_MOCK },
         { provide: DomService, useValue: DOM_SERVICE_MOCK },
         { provide: DataSourcesTreeActionsService, useValue: DATASOURCES_TREE_ACTIONS_MOCK },
         { provide: SsoHeartbeatService, useValue: { heartbeats: of(), heartbeat: vi.fn() } },
      ],
      importOverrides: [
         { replace: DataNotificationsComponent, with: StubDataNotificationsComponent },
         { replace: DataFolderListViewComponent, with: StubDataFolderListViewComponent },
      ],
      // The component has providers:[ViewsheetClientService] in its @Component decorator
      // which would create a real instance ignoring the test-level mock. componentProviders
      // overrides that component-level provider with our mock.
      componentProviders: [
         { provide: ViewsheetClientService, useValue: VS_CLIENT_MOCK },
      ],
      schemas: [NO_ERRORS_SCHEMA],
   });

   const comp = result.fixture.componentInstance;
   await result.fixture.whenStable();

   return { comp, fixture: result.fixture, router: routerMock, route: routeMock };
}

// ---------------------------------------------------------------------------
// Mock cleanup — call from beforeEach in each spec file
// ---------------------------------------------------------------------------

export function clearMocks() {
   DATA_BROWSER_MOCK.changeFolder.mockClear();
   DATA_BROWSER_MOCK.newWorksheet.mockClear();
   DATA_BROWSER_MOCK.openWorksheet.mockClear();
   DATA_BROWSER_MOCK.renameAsset.mockClear();
   DATA_BROWSER_MOCK.changeMV.mockClear();
   DATA_BROWSER_MOCK.deleteAsset.mockClear();
   VS_CLIENT_MOCK.connect.mockClear();
   VS_CLIENT_MOCK.disconnect.mockClear();
   VS_CLIENT_MOCK.subscribe.mockClear();
   VS_CLIENT_MOCK.sendEvent.mockClear();
   REPO_CLIENT_MOCK.connect.mockClear();
   REPO_CLIENT_MOCK.disconnect.mockClear();
   MODAL_MOCK.open.mockClear();
   DOM_SERVICE_MOCK.addPortalContent.mockClear();
   DRAG_SERVICE_MOCK.put.mockClear();
   DRAG_SERVICE_MOCK.getDragData.mockClear();
   DATASOURCES_TREE_ACTIONS_MOCK.showWSFolderDetailsSubject.mockClear();
}
