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
 * Shared test helpers for DataSourcesTreeViewComponent specs.
 *
 * Extracted to avoid duplicating the ~100-line renderComponent() setup between the
 * display and interaction spec files.
 */

import { Component } from "@angular/core";
import { convertToParamMap } from "@angular/router";
import { provideHttpClient } from "@angular/common/http";
import { Subject, Subscription, of } from "rxjs";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { ActivatedRoute, Router } from "@angular/router";

import { AssetType } from "../../../../../../shared/data/asset-type";
import { AssetClientService } from "../../../common/asset-client/asset-client.service";
import { ViewsheetClientService } from "../../../common/viewsheet-client";
import { DebounceService } from "../../../widget/services/debounce.service";
import { DragService } from "../../../widget/services/drag.service";
import { RepositoryClientService } from "../../../common/repository-client/repository-client.service";
import { DataModelNameChangeService } from "../services/data-model-name-change.service";
import { OpenComposerService } from "../../../common/services/open-composer.service";
import { FixedDropdownService } from "../../../widget/fixed-dropdown/fixed-dropdown.service";
import { DomService } from "../../../widget/dom-service/dom.service";
import { GettingStartedService } from "../../../widget/dialog/getting-started-dialog/service/getting-started.service";
import { SsoHeartbeatService } from "../../../../../../shared/sso/sso-heartbeat.service";
import { DataNotificationsComponent } from "../data-notifications.component";
import { DataBrowserService } from "../data-folder-browser/data-browser.service";
import { DatasourceBrowserService } from "../data-datasource-browser/datasource-browser.service";
import {
   DataModelBrowserService,
} from "../data-datasource-browser/datasources-database/database-data-model-browser/data-model-browser.service";
import { DataSourcesTreeActionsService } from "./data-sources-tree-actions.service";
import { PortalDataType } from "./portal-data-type";
import { TreeNodeModel } from "../../../widget/tree/tree-node-model";

// ---------------------------------------------------------------------------
// Stub component
// ---------------------------------------------------------------------------

@Component({ selector: "data-notifications", template: "", standalone: true })
export class StubDataNotificationsComponent {
   notifications = { info: vi.fn(), success: vi.fn(), danger: vi.fn(), warning: vi.fn() };
}

// ---------------------------------------------------------------------------
// Subjects factory
// ---------------------------------------------------------------------------

export function makeSubjects() {
   return {
      folderChangedSubject: new Subject<any>(),
      mvChangedSubject: new Subject<void>(),
      datasourceChangedSubject: new Subject<void>(),
      datasourceFolderChangedSubject: new Subject<any>(),
      onCreateEventSubject: new Subject<any>(),
      dataChangedSubject: new Subject<void>(),
      routerEventsSubject: new Subject<any>(),
      nameChangedSubject: new Subject<any>(),
   };
}

// ---------------------------------------------------------------------------
// Node factories
// ---------------------------------------------------------------------------

export function makeNode(
   overrides: Partial<TreeNodeModel> & { properties?: Record<string, string> } = {},
): TreeNodeModel {
   const { properties, ...rest } = overrides as any;
   return {
      label: "Test Node",
      data: {
         path: "/test",
         name: "test",
         scope: 1,
         identifier: "1^128^__NULL__^/test",
         type: AssetType.FOLDER,
         properties: properties ?? {},
      },
      children: [],
      leaf: false,
      expanded: false,
      type: PortalDataType.FOLDER,
      ...rest,
   } as TreeNodeModel;
}

export function makeRootNode(children: TreeNodeModel[] = []): TreeNodeModel {
   return {
      label: "Root",
      data: { path: "/", name: "root", scope: 0, properties: {} },
      children,
      leaf: false,
      expanded: true,
      type: "ROOT",
   } as TreeNodeModel;
}

// ---------------------------------------------------------------------------
// Render config factory
// ---------------------------------------------------------------------------

export interface TreeViewRenderConfig {
   providers: any[];
   importOverrides: any[];
   componentProviders: any[];
   mocks: {
      router: any;
      dataFolderService: any;
      datasourceService: any;
      dataModelBrowserService: any;
      vsClient: any;
      dropdownService: any;
      dataSourcesTreeActions: any;
   };
}

export function buildRenderConfig(
   subjects: ReturnType<typeof makeSubjects>,
   routerUrl: string,
   queryParams: Record<string, string>,
): TreeViewRenderConfig {
   const queryParamMap = convertToParamMap(queryParams);

   const routerMock = {
      navigate: vi.fn(),
      url: routerUrl,
      events: subjects.routerEventsSubject,
   };

   const routeMock = {
      queryParamMap: of(queryParamMap),
      snapshot: { _routerState: { url: routerUrl } },
   };

   const dataFolderServiceMock = {
      changeFolder: vi.fn(),
      changeMV: vi.fn(),
      openWorksheet: vi.fn(),
      folderChanged: subjects.folderChangedSubject,
      mvChanged: subjects.mvChangedSubject,
   };

   const datasourceServiceMock = {
      refreshTree: vi.fn(),
      createDataSourceInfos: vi.fn().mockReturnValue([{ name: "DS1" }]),
      moveDataSourcesToFolder: vi.fn(),
      datasourceChanged: subjects.datasourceChangedSubject,
      folderChanged: subjects.datasourceFolderChangedSubject,
      onCreateEvent: subjects.onCreateEventSubject,
   };

   const dataModelBrowserServiceMock = {
      addPhysicalView: vi.fn(),
      addLogicalModel: vi.fn(),
      addVPM: vi.fn(),
      addDataModelFolder: vi.fn(),
      renameDataModelFolder: vi.fn(),
      deleteDataModelFolder: vi.fn(),
      moveModelsToTarget: vi.fn(),
      emitChanged: vi.fn(),
   };

   const vsClientMock = {
      connect: vi.fn(),
      disconnect: vi.fn(),
      sendEvent: vi.fn(),
      subscribe: vi.fn().mockReturnValue(new Subscription()),
      onMessageReceived: new Subject<any>(),
   };

   const assetClientMock = { connect: vi.fn(), disconnect: vi.fn() };

   const debounceServiceMock = {
      // Do NOT call fn() — prevents secondary getDataNavigationTree() HTTP calls that would
      // arrive after fixture.destroy() and crash Angular change detection.
      debounce: vi.fn(),
   };

   const dropdownServiceMock = {
      open: vi.fn().mockReturnValue({
         componentInstance: { actions: [], sourceEvent: null },
      }),
   };

   const dataSourcesTreeActionsMock = {
      addDataSourceFolder: vi.fn(),
      addDataWorksheetFolder: vi.fn(),
      addDataSource: vi.fn(),
      addDataWorksheet: vi.fn(),
      deleteDataSourceFolder: vi.fn(),
      deleteWorksheetFolder: vi.fn(),
      deleteDataSource: vi.fn(),
      renameDataSourceFolder: vi.fn(),
      renameWorksheetFolder: vi.fn(),
      showWSFolderDetails: vi.fn(),
      showWSFolderDetailsSubject: vi.fn().mockReturnValue(new Subject<any>()),
   };

   const providers = [
      provideHttpClient(),
      { provide: DataBrowserService, useValue: dataFolderServiceMock },
      { provide: DatasourceBrowserService, useValue: datasourceServiceMock },
      { provide: DataModelBrowserService, useValue: dataModelBrowserServiceMock },
      { provide: AssetClientService, useValue: assetClientMock },
      { provide: ViewsheetClientService, useValue: vsClientMock },
      { provide: DebounceService, useValue: debounceServiceMock },
      { provide: DragService, useValue: { put: vi.fn(), getDragData: vi.fn().mockReturnValue({}) } },
      {
         provide: NgbModal, useValue: {
            open: vi.fn().mockImplementation(() => ({
               result: new Promise<any>(() => {}),
               componentInstance: { onCommit: new Subject<string>() },
               close: vi.fn(),
               dismiss: vi.fn(),
            })),
         },
      },
      {
         provide: RepositoryClientService,
         useValue: { connect: vi.fn(), disconnect: vi.fn(), dataChanged: subjects.dataChangedSubject },
      },
      { provide: DataModelNameChangeService, useValue: { nameChanged: subjects.nameChangedSubject } },
      { provide: OpenComposerService, useValue: { composerOpen: of(false) } },
      { provide: FixedDropdownService, useValue: dropdownServiceMock },
      { provide: ActivatedRoute, useValue: routeMock },
      { provide: Router, useValue: routerMock },
      { provide: DomService, useValue: { addPortalContent: vi.fn() } },
      { provide: DataSourcesTreeActionsService, useValue: dataSourcesTreeActionsMock },
      {
         provide: GettingStartedService,
         useValue: { isProcessing: vi.fn().mockReturnValue(false), isEditWs: vi.fn().mockReturnValue(false) },
      },
      { provide: SsoHeartbeatService, useValue: { heartbeats: of(), heartbeat: vi.fn() } },
   ];

   const importOverrides = [
      { replace: DataNotificationsComponent, with: StubDataNotificationsComponent },
   ];

   const componentProviders = [
      { provide: ViewsheetClientService, useValue: vsClientMock },
      { provide: AssetClientService, useValue: assetClientMock },
   ];

   return {
      providers,
      importOverrides,
      componentProviders,
      mocks: {
         router: routerMock,
         dataFolderService: dataFolderServiceMock,
         datasourceService: datasourceServiceMock,
         dataModelBrowserService: dataModelBrowserServiceMock,
         vsClient: vsClientMock,
         dropdownService: dropdownServiceMock,
         dataSourcesTreeActions: dataSourcesTreeActionsMock,
      },
   };
}
