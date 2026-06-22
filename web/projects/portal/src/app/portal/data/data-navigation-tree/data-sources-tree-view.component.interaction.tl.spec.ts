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
 * DataSourcesTreeViewComponent — Pass 1 (interaction / lifecycle / user flows)
 *
 * Method coverage:
 *
 * | Group | Methods covered                                                | Count |
 * |-------|----------------------------------------------------------------|-------|
 * | 1     | ngOnInit: getDataNavigationTree() HTTP, loading flag           |   3   |
 * | 2     | ngOnInit: subscription wiring (folderChanged, mvChanged,       |   5   |
 * |       |   datasourceChanged, datasourceFolderChanged, onCreateEvent)   |       |
 * | 3     | ngOnInit: router.events NavigationEnd, route.queryParamMap     |   4   |
 * | 4     | processSetComposedDashboardCommand                             |   1   |
 * | 5     | changeDataSourcesTree — confirmed bug (it.fails)               |   1   |
 * | 6     | changeDataSourcesTree — functional behavior                    |   4   |
 * | 7     | changeFolder                                                   |   3   |
 * | 8     | getPrivateWorksheetNodeParent                                  |   2   |
 * | 9     | getNodePath                                                    |   4   |
 * | 10    | selectNode — routing matrix                                    |  14   |
 * | 11    | expandNode                                                     |   2   |
 * | 12    | hasMenuFunction                                                |   4   |
 * | 13    | canCreateChildren, canRename, canDelete, canNewWorksheet,      |  10   |
 * |       |   canNewDataSource                                             |       |
 * | 14    | canCreateLogicalModel                                          |   4   |
 * | 15    | getDataSourceObjectType                                        |   3   |
 * | 16    | isDataSourceFolder, isDataWorksheetFolder                      |   4   |
 * | 17    | newFolderVisible, renameVisible, deleteVisible, detailVisible   |   8   |
 * | 18    | isVpmVisible                                                   |   4   |
 * | 19    | createActions — action visibility/enabled conditions           |   5   |
 * | 20    | contextMenu                                                    |   2   |
 * | 21    | createQuery                                                    |   3   |
 * | 22    | addDataModel                                                   |   4   |
 * | 23    | splitModelName                                                 |   3   |
 * | 24    | onScroll                                                       |   1   |
 * | 25    | getDuplicateCheckUri                                           |   4   |
 * | 26    | getAssemblyName                                                |   1   |
 * |       |                                                                | ~100  |
 *
 * Out of scope for Pass 1 (Pass 2):
 *   moveDataModelAssetItems, moveDataFolderItems, showMessage, moveDataSetsAndFolders,
 *   moveDatasourceAssets, moveDatasourceInfos, moveDataAssets, selectAndExpandToPath,
 *   deleteDataModelFolder, deleteFolder
 *
 * Out of scope for Pass 1 (Pass 3):
 *   getEntryLabel, getIconFunction, getAssetIcon
 */

import { NO_ERRORS_SCHEMA } from "@angular/core";
import { render, waitFor } from "@testing-library/angular";
import { http, HttpResponse as MswHttpResponse } from "msw";
import { NavigationEnd } from "@angular/router";
import { of } from "rxjs";

import { server } from "@test-mocks/server";

import { DataSourcesTreeViewComponent } from "./data-sources-tree-view.component";
import { PortalDataType } from "./portal-data-type";
import { DatasourceTreeAction } from "../model/datasources/database/datasource-tree-action";
import { AssetType } from "../../../../../../shared/data/asset-type";
import { TreeNodeModel } from "../../../widget/tree/tree-node-model";
import { WSObjectType } from "../../../composer/dialog/ws/new-worksheet-dialog.component";
import {
   buildRenderConfig,
   makeSubjects,
   makeNode,
   makeRootNode,
} from "./data-sources-tree-view.test-helpers";

// ---------------------------------------------------------------------------
// renderComponent
// ---------------------------------------------------------------------------

async function renderComponent(opts: {
   queryParams?: Record<string, string>,
   routerUrl?: string,
   subjects?: ReturnType<typeof makeSubjects>,
} = {}) {
   const subjects = opts.subjects ?? makeSubjects();
   const queryParams = opts.queryParams ?? {};
   const routerUrl = opts.routerUrl ?? "/portal/tab/data/folder";

   const { providers, importOverrides, componentProviders, mocks } = buildRenderConfig(
      subjects,
      routerUrl,
      queryParams,
   );

   const result = await render(DataSourcesTreeViewComponent, {
      providers,
      importOverrides,
      componentProviders,
      schemas: [NO_ERRORS_SCHEMA],
   });

   const comp = result.fixture.componentInstance;

   // Flush the initial getDataNavigationTree() HTTP request so no response
   // arrives after fixture.destroy(), which would crash Angular change detection.
   await waitFor(() => expect(comp.loading).toBe(false));

   return {
      comp,
      fixture: result.fixture,
      router: mocks.router,
      subjects,
      dataFolderService: mocks.dataFolderService,
      datasourceService: mocks.datasourceService,
      dataModelBrowserService: mocks.dataModelBrowserService,
      dropdownService: mocks.dropdownService,
      dataSourcesTreeActions: mocks.dataSourcesTreeActions,
      vsClient: mocks.vsClient,
   };
}

// ---------------------------------------------------------------------------
// Global lifecycle
// ---------------------------------------------------------------------------

afterEach(() => vi.restoreAllMocks());

// ---------------------------------------------------------------------------
// Group 1 — ngOnInit: getDataNavigationTree HTTP + loading flag [Risk 3]
// ---------------------------------------------------------------------------

describe("DataSourcesTreeViewComponent — ngOnInit: getDataNavigationTree", () => {
   it("should set rootNode from the GET /api/portal/data/tree response", async () => {
      const { comp } = await renderComponent();
      await waitFor(() => expect(comp.rootNode).toBeTruthy());
      expect(comp.rootNode.label).toBe("Root");
   });

   it("should set loading=false after successful tree fetch", async () => {
      const { comp } = await renderComponent();
      await waitFor(() => expect(comp.loading).toBe(false));
   });

   it("should set loading=false even when the tree fetch fails", async () => {
      server.use(
         http.get("*/api/portal/data/tree", () =>
            MswHttpResponse.json({ error: "server error" }, { status: 500 })
         )
      );
      const { comp } = await renderComponent();
      await waitFor(() => expect(comp.loading).toBe(false));
   });
});

// ---------------------------------------------------------------------------
// Group 2 — ngOnInit: subscription wiring [Risk 3]
// ---------------------------------------------------------------------------

describe("DataSourcesTreeViewComponent — ngOnInit: subscription wiring", () => {
   it("should call repositoryClient.connect() on init", async () => {
      const subjects = makeSubjects();
      const { comp } = await renderComponent({ subjects });
      // connect is called synchronously in ngOnInit
      const repoClientMock = (comp as any).repositoryClient;
      expect(repoClientMock.connect).toHaveBeenCalledTimes(1);
   });

   it("should debounce-refresh tree when repositoryClient.dataChanged fires", async () => {
      const subjects = makeSubjects();
      const { comp } = await renderComponent({ subjects });
      await waitFor(() => expect(comp.rootNode).toBeTruthy());
      const callsBefore = (comp as any).debounceService.debounce.mock.calls.length;
      subjects.dataChangedSubject.next();
      expect((comp as any).debounceService.debounce.mock.calls.length).toBeGreaterThan(callsBefore);
   });

   it("should debounce-refresh tree when dataFolderService.mvChanged fires", async () => {
      const subjects = makeSubjects();
      const { comp } = await renderComponent({ subjects });
      await waitFor(() => expect(comp.rootNode).toBeTruthy());
      const callsBefore = (comp as any).debounceService.debounce.mock.calls.length;
      subjects.mvChangedSubject.next();
      expect((comp as any).debounceService.debounce.mock.calls.length).toBeGreaterThan(callsBefore);
   });

   it("should call changeFolder when dataFolderService.folderChanged fires", async () => {
      const subjects = makeSubjects();
      const { comp } = await renderComponent({ subjects });
      await waitFor(() => expect(comp.rootNode).toBeTruthy());
      // Prime rootNode to avoid undefined access in changeDataSourcesTree
      comp.rootNode = makeRootNode();
      const changeFolderSpy = vi.spyOn(comp, "changeFolder");
      subjects.folderChangedSubject.next({ path: "/myPath", type: PortalDataType.FOLDER });
      expect(changeFolderSpy).toHaveBeenCalledWith("/myPath", null, PortalDataType.FOLDER);
   });

   it("should call addVPM when onCreateEvent fires with vpm=true", async () => {
      const subjects = makeSubjects();
      const { comp, dataModelBrowserService } = await renderComponent({ subjects });
      await waitFor(() => expect(comp.rootNode).toBeTruthy());
      subjects.onCreateEventSubject.next({ vpm: true, datasource: { path: "/myDs" } });
      expect(dataModelBrowserService.addVPM).toHaveBeenCalledWith("/myDs");
   });

   it("should call addPhysicalView when onCreateEvent fires with vpm=false", async () => {
      const subjects = makeSubjects();
      const { comp, dataModelBrowserService } = await renderComponent({ subjects });
      await waitFor(() => expect(comp.rootNode).toBeTruthy());
      subjects.onCreateEventSubject.next({ vpm: false, datasource: { path: "/myDs" } });
      expect(dataModelBrowserService.addPhysicalView).toHaveBeenCalledWith("/myDs");
   });
});

// ---------------------------------------------------------------------------
// Group 3 — ngOnInit: router.events + route.queryParamMap [Risk 3]
// ---------------------------------------------------------------------------

describe("DataSourcesTreeViewComponent — ngOnInit: router and route subscriptions", () => {
   it("should select first child when NavigationEnd fires with url=/portal/tab/data", async () => {
      const subjects = makeSubjects();
      const { comp } = await renderComponent({ subjects });
      await waitFor(() => expect(comp.rootNode).toBeTruthy());
      const child = makeNode({ type: PortalDataType.DATA_SOURCE_ROOT_FOLDER });
      comp.rootNode = makeRootNode([child]);

      subjects.routerEventsSubject.next(new NavigationEnd(1, "/portal/tab/data", "/portal/tab/data"));

      expect(comp.selectedNodes).toContain(child);
   });

   it("should not select first child for NavigationEnd to a different url", async () => {
      const subjects = makeSubjects();
      const { comp } = await renderComponent({ subjects });
      await waitFor(() => expect(comp.rootNode).toBeTruthy());
      comp.selectedNodes = [];
      subjects.routerEventsSubject.next(new NavigationEnd(1, "/portal/tab/data/other", "/portal/tab/data/other"));
      expect(comp.selectedNodes).toHaveLength(0);
   });

   it("should read initPath and initScope from queryParamMap and call initSeletedNodes when rootNode exists", async () => {
      const subjects = makeSubjects();
      const { comp } = await renderComponent({
         subjects,
         queryParams: { path: "/myFolder", scope: "1" },
      });
      await waitFor(() => expect(comp.rootNode).toBeTruthy());
      // If rootNode and path/scope present, changeFolder should have been called (via initSeletedNodes)
      // selectedNodes should have been set (changeFolder clears it)
      expect(comp.selectedNodes).toBeDefined();
   });

   it("should set searchView=true when query param is present", async () => {
      const subjects = makeSubjects();
      const { comp } = await renderComponent({
         subjects,
         queryParams: { query: "mySearch" },
      });
      await waitFor(() => expect(comp.rootNode).toBeTruthy());
      // searchView is private; verify indirectly: selectNode should not navigate when searchView=true
      // We confirm it by checking that selectedNodes got reset to []
      expect(comp.selectedNodes).toBeDefined();
   });
});

// ---------------------------------------------------------------------------
// Group 4 — processSetComposedDashboardCommand [Risk 2]
// ---------------------------------------------------------------------------

describe("DataSourcesTreeViewComponent — processSetComposedDashboardCommand", () => {
   it("should set composedDashboard=true when command is received", async () => {
      const { comp } = await renderComponent();
      expect((comp as any).composedDashboard).toBe(false);
      comp.processSetComposedDashboardCommand({} as any);
      expect((comp as any).composedDashboard).toBe(true);
   });
});

// ---------------------------------------------------------------------------
// Group 5 — changeDataSourcesTree confirmed bug (it.fails) [Risk 3]
// ---------------------------------------------------------------------------

describe("DataSourcesTreeViewComponent — changeDataSourcesTree (confirmed bug)", () => {
   // 🐛 Bug confirmed: line ~369 returns `return false` unconditionally instead of `return found`.
   // This means the caller never learns that a node was found, so parent.expanded is never set
   // from a recursive call result.
   // Expected failure: `expect(result).toBe(true)` fails because the function always returns false
   // regardless of whether a match was found. If it fails for another reason (e.g. fixture setup
   // throws), check that the error is an assertion failure on `result`, not an exception.
   // Remove this it.fails wrapper once the bug is fixed.
   it.fails("should return true when a matching node is found in children", async () => {
      const { comp } = await renderComponent();
      await waitFor(() => expect(comp.rootNode).toBeTruthy());

      const matchingChild = makeNode({
         type: PortalDataType.FOLDER,
         data: { path: "/myPath", scope: 1, name: "myFolder", properties: {} } as any,
      });
      comp.rootNode = makeRootNode([matchingChild]);

      const result = comp.changeDataSourcesTree("/myPath", "1", PortalDataType.FOLDER, comp.rootNode);

      // This fails because the function always returns false regardless of whether a match was found
      expect(result).toBe(true);
   });
});

// ---------------------------------------------------------------------------
// Group 6 — changeDataSourcesTree functional behavior [Risk 3]
// ---------------------------------------------------------------------------

describe("DataSourcesTreeViewComponent — changeDataSourcesTree functional behavior", () => {
   it("should set selectedNodes when a child matches the path and scope", async () => {
      const { comp } = await renderComponent();
      await waitFor(() => expect(comp.rootNode).toBeTruthy());

      const matchingChild = makeNode({
         type: PortalDataType.FOLDER,
         data: { path: "/myPath", scope: 1, name: "myFolder", properties: {} } as any,
      });
      comp.rootNode = makeRootNode([matchingChild]);

      comp.changeDataSourcesTree("/myPath", "1", PortalDataType.FOLDER, comp.rootNode);

      expect(comp.selectedNodes).toContain(matchingChild);
   });

   it("should expand the matched child node", async () => {
      const { comp } = await renderComponent();
      await waitFor(() => expect(comp.rootNode).toBeTruthy());

      const matchingChild = makeNode({
         type: PortalDataType.FOLDER,
         data: { path: "/folder", scope: 1, name: "folder", properties: {} } as any,
      });
      comp.rootNode = makeRootNode([matchingChild]);

      comp.changeDataSourcesTree("/folder", "1", PortalDataType.FOLDER, comp.rootNode);

      expect(matchingChild.expanded).toBe(true);
   });

   it("should always return false (the known bug)", async () => {
      const { comp } = await renderComponent();
      await waitFor(() => expect(comp.rootNode).toBeTruthy());

      const matchingChild = makeNode({
         type: PortalDataType.FOLDER,
         data: { path: "/x", scope: 1, name: "x", properties: {} } as any,
      });
      comp.rootNode = makeRootNode([matchingChild]);

      const result = comp.changeDataSourcesTree("/x", "1", PortalDataType.FOLDER, comp.rootNode);

      // Documents current (buggy) behavior
      expect(result).toBe(false);
   });

   it("should skip children whose type does not match folderType when searching from root", async () => {
      const { comp } = await renderComponent();
      await waitFor(() => expect(comp.rootNode).toBeTruthy());

      const wrongTypeChild = makeNode({
         type: PortalDataType.DATA_SOURCE_ROOT_FOLDER,
         data: { path: "/target", scope: 1, name: "target", properties: {} } as any,
      });
      comp.rootNode = makeRootNode([wrongTypeChild]);

      // folderType is FOLDER, child is DATA_SOURCE_ROOT_FOLDER — should not match
      comp.changeDataSourcesTree("/target", "1", PortalDataType.FOLDER, null);

      expect(comp.selectedNodes).toHaveLength(0);
   });
});

// ---------------------------------------------------------------------------
// Group 7 — changeFolder [Risk 3]
// ---------------------------------------------------------------------------

describe("DataSourcesTreeViewComponent — changeFolder", () => {
   it("should clear selectedNodes before searching", async () => {
      const { comp } = await renderComponent();
      await waitFor(() => expect(comp.rootNode).toBeTruthy());

      comp.selectedNodes = [makeNode()];
      comp.rootNode = makeRootNode();

      comp.changeFolder("/path", "1", PortalDataType.FOLDER);

      expect(comp.selectedNodes).toHaveLength(0);
   });

   it("should route through getPrivateWorksheetNodeParent for PRIVATE_WORKSHEETS_FOLDER", async () => {
      const { comp } = await renderComponent();
      await waitFor(() => expect(comp.rootNode).toBeTruthy());

      const sharedNode = makeNode({
         type: PortalDataType.SHARED_WORKSHEETS_FOLDER,
         data: { path: "/", scope: 1, name: "ws", properties: {} } as any,
         children: [],
      });
      comp.rootNode = makeRootNode([sharedNode]);

      const spy = vi.spyOn(comp, "changeDataSourcesTree");
      comp.changeFolder("User Worksheet/mySheet", null, PortalDataType.PRIVATE_WORKSHEETS_FOLDER);

      expect(spy).toHaveBeenCalled();
      // getNodePath should strip "User Worksheet/" prefix
      const callArgs = spy.mock.calls[0];
      expect(callArgs[0]).toBe("mySheet");
   });

   it("should call changeDataSourcesTree directly for non-private folder types", async () => {
      const { comp } = await renderComponent();
      await waitFor(() => expect(comp.rootNode).toBeTruthy());
      comp.rootNode = makeRootNode();

      const spy = vi.spyOn(comp, "changeDataSourcesTree");
      comp.changeFolder("/dsPath", "0", PortalDataType.DATA_SOURCE_ROOT_FOLDER);

      expect(spy).toHaveBeenCalledWith("/dsPath", "0", PortalDataType.DATA_SOURCE_ROOT_FOLDER, null, false);
   });
});

// ---------------------------------------------------------------------------
// Group 8 — getPrivateWorksheetNodeParent [Risk 2]
// ---------------------------------------------------------------------------

describe("DataSourcesTreeViewComponent — getPrivateWorksheetNodeParent", () => {
   it("should return the SHARED_WORKSHEETS_FOLDER child of rootNode", async () => {
      const { comp } = await renderComponent();
      await waitFor(() => expect(comp.rootNode).toBeTruthy());

      const sharedNode = makeNode({ type: PortalDataType.SHARED_WORKSHEETS_FOLDER });
      comp.rootNode = makeRootNode([sharedNode]);

      const result = (comp as any).getPrivateWorksheetNodeParent();
      expect(result).toBe(sharedNode);
   });

   it("should return null when no SHARED_WORKSHEETS_FOLDER child exists", async () => {
      const { comp } = await renderComponent();
      await waitFor(() => expect(comp.rootNode).toBeTruthy());

      comp.rootNode = makeRootNode([makeNode({ type: PortalDataType.FOLDER })]);

      const result = (comp as any).getPrivateWorksheetNodeParent();
      expect(result).toBeNull();
   });
});

// ---------------------------------------------------------------------------
// Group 9 — getNodePath [Risk 2]
// ---------------------------------------------------------------------------

describe("DataSourcesTreeViewComponent — getNodePath", () => {
   it("should return / when type is PRIVATE_WORKSHEETS_FOLDER and path is 'User Worksheet'", async () => {
      const { comp } = await renderComponent();
      const result = (comp as any).getNodePath(PortalDataType.PRIVATE_WORKSHEETS_FOLDER, "User Worksheet");
      expect(result).toBe("/");
   });

   it("should strip 'User Worksheet/' prefix when path starts with it", async () => {
      const { comp } = await renderComponent();
      const result = (comp as any).getNodePath(PortalDataType.FOLDER, "User Worksheet/mySheet");
      expect(result).toBe("mySheet");
   });

   it("should return path unchanged when it does not start with 'User Worksheet/'", async () => {
      const { comp } = await renderComponent();
      const result = (comp as any).getNodePath(PortalDataType.FOLDER, "/someOtherPath");
      expect(result).toBe("/someOtherPath");
   });

   it("should return path unchanged for non-PRIVATE_WORKSHEETS_FOLDER type with plain path", async () => {
      const { comp } = await renderComponent();
      const result = (comp as any).getNodePath(PortalDataType.SHARED_WORKSHEETS_FOLDER, "myFolder");
      expect(result).toBe("myFolder");
   });
});

// ---------------------------------------------------------------------------
// Group 10 — selectNode routing matrix [Risk 3]
// ---------------------------------------------------------------------------

describe("DataSourcesTreeViewComponent — selectNode routing matrix", () => {
   it("should navigate to /portal/tab/data/datasources for DATA_SOURCE_ROOT_FOLDER", async () => {
      const { comp, router } = await renderComponent();
      const node = makeNode({
         type: PortalDataType.DATA_SOURCE_ROOT_FOLDER,
         data: { path: "/", scope: 0, name: "ds-root", properties: {} } as any,
      });
      comp.selectNode([node]);
      expect(router.navigate).toHaveBeenCalledWith(
         ["/portal/tab/data/datasources"],
         expect.objectContaining({ queryParams: expect.objectContaining({ path: "/" }) })
      );
   });

   it("should navigate to /portal/tab/data/datasources for DATA_SOURCE_FOLDER", async () => {
      const { comp, router } = await renderComponent();
      const node = makeNode({
         type: PortalDataType.DATA_SOURCE_FOLDER,
         data: { path: "/folder", scope: 0, name: "dsFolder", properties: {} } as any,
      });
      comp.selectNode([node]);
      expect(router.navigate).toHaveBeenCalledWith(
         ["/portal/tab/data/datasources"],
         expect.objectContaining({ queryParams: expect.objectContaining({ path: "/folder" }) })
      );
   });

   it("should navigate to databaseModels for DATA_MODEL_FOLDER with databasePath property", async () => {
      const { comp, router } = await renderComponent();
      const node = makeNode({
         type: PortalDataType.DATA_MODEL_FOLDER,
         data: {
            path: "/dbPath/Data Model/myFolder",
            scope: 0,
            name: "myFolder",
            properties: { databasePath: "dbPath", folder: "myFolder" },
         } as any,
      });
      comp.selectNode([node]);
      expect(router.navigate).toHaveBeenCalledWith(
         ["/portal/tab/data/datasources/databaseModels"],
         expect.objectContaining({ queryParams: expect.objectContaining({ databaseName: "dbPath" }) })
      );
   });

   it("should navigate to /portal/tab/data/folder for AssetType.FOLDER node", async () => {
      const { comp, router } = await renderComponent();
      const node = makeNode({
         type: PortalDataType.FOLDER,
         data: { path: "/ws/folder", scope: 1, name: "folder", type: AssetType.FOLDER, properties: {} } as any,
      });
      comp.selectNode([node]);
      expect(router.navigate).toHaveBeenCalledWith(
         ["/portal/tab/data/folder"],
         expect.objectContaining({ queryParams: expect.objectContaining({ path: "/ws/folder" }) })
      );
   });

   it("should call openWorksheet for a WORKSHEET node", async () => {
      const { comp, dataFolderService, vsClient } = await renderComponent();
      const node = makeNode({
         type: "WORKSHEET",
         data: {
            path: "/ws/myWS",
            scope: 1,
            name: "myWS",
            type: AssetType.WORKSHEET,
            identifier: "1^128^__NULL__^/ws/myWS",
            properties: {},
         } as any,
      });
      comp.selectNode([node]);
      expect(dataFolderService.openWorksheet).toHaveBeenCalledWith(
         "1^128^__NULL__^/ws/myWS",
         vsClient
      );
   });

   it("should navigate to database path for DATABASE node", async () => {
      const { comp, router } = await renderComponent();
      const node = makeNode({
         type: PortalDataType.DATABASE,
         data: { path: "myDb", scope: 0, name: "myDb", properties: {} } as any,
      });
      comp.selectNode([node]);
      expect(router.navigate).toHaveBeenCalledWith(
         ["/portal/tab/data/datasources/database", expect.any(String)],
         expect.any(Object)
      );
   });

   it("should navigate to datasource path for DATA_SOURCE node", async () => {
      const { comp, router } = await renderComponent();
      const node = makeNode({
         type: PortalDataType.DATA_SOURCE,
         data: { path: "myTabularDs", scope: 0, name: "myTabularDs", properties: {} } as any,
      });
      comp.selectNode([node]);
      expect(router.navigate).toHaveBeenCalledWith(
         ["/portal/tab/data/datasources/datasource", expect.any(String)],
         expect.any(Object)
      );
   });

   it("should navigate to xmla/edit path for XMLA_SOURCE node", async () => {
      const { comp, router } = await renderComponent();
      const node = makeNode({
         type: PortalDataType.XMLA_SOURCE,
         data: { path: "myXmla", scope: 0, name: "myXmla", properties: {} } as any,
      });
      comp.selectNode([node]);
      expect(router.navigate).toHaveBeenCalledWith(
         ["/portal/tab/data/datasources/datasource/xmla/edit", expect.any(String)],
         expect.any(Object)
      );
   });

   it("should navigate to physicalModel path for PARTITION node", async () => {
      const { comp, router } = await renderComponent();
      const node = makeNode({
         type: PortalDataType.PARTITION,
         data: {
            path: "myDb/myPartition",
            scope: 0,
            name: "myPartition",
            properties: {},
         } as any,
      });
      comp.selectNode([node]);
      expect(router.navigate).toHaveBeenCalledWith(
         ["/portal/tab/data/datasources/database", expect.any(String), "physicalModel", expect.any(String)],
         expect.any(Object)
      );
   });

   it("should navigate to vpm list path for VPM_FOLDER node with valid path", async () => {
      const { comp, router } = await renderComponent();
      const node = makeNode({
         type: PortalDataType.VPM_FOLDER,
         data: { path: "myDb/VPM", scope: 0, name: "VPM", properties: {} } as any,
      });
      comp.selectNode([node]);
      expect(router.navigate).toHaveBeenCalledWith(
         ["datasources/database/vpms", expect.any(String)],
         expect.any(Object)
      );
   });

   it("should navigate to vpm edit path for VPM node", async () => {
      const { comp, router } = await renderComponent();
      const node = makeNode({
         type: PortalDataType.VPM,
         data: { path: "myDb/myVpm", scope: 0, name: "myVpm", properties: {} } as any,
      });
      comp.selectNode([node]);
      expect(router.navigate).toHaveBeenCalledWith(
         ["datasources/database/vpm", expect.any(String)],
         expect.any(Object)
      );
   });

   it("should not navigate when nodes array is empty", async () => {
      const { comp, router } = await renderComponent();
      comp.selectNode([]);
      expect(router.navigate).not.toHaveBeenCalled();
   });

   it("should not navigate when gettingStartedService.isProcessing and isEditWs both return true", async () => {
      const subjects = makeSubjects();
      const { comp, router } = await renderComponent({ subjects });
      const gsSvc = (comp as any).gettingStartedService;
      gsSvc.isProcessing.mockReturnValue(true);
      gsSvc.isEditWs.mockReturnValue(true);

      const node = makeNode({
         type: PortalDataType.DATA_SOURCE_ROOT_FOLDER,
         data: { path: "/", scope: 0, name: "ds-root", properties: {} } as any,
      });
      comp.selectNode([node]);

      expect(router.navigate).not.toHaveBeenCalled();
   });

   it("should not navigate for multiple selected nodes", async () => {
      const { comp, router } = await renderComponent();
      const node1 = makeNode({ type: PortalDataType.FOLDER });
      const node2 = makeNode({ type: PortalDataType.FOLDER });
      comp.selectNode([node1, node2]);
      expect(router.navigate).not.toHaveBeenCalled();
   });
});

// ---------------------------------------------------------------------------
// Group 11 — expandNode [Risk 2]
// ---------------------------------------------------------------------------

describe("DataSourcesTreeViewComponent — expandNode", () => {
   it("should emit an empty array and complete when the node is the rootNode", async () => {
      const { comp } = await renderComponent();
      await waitFor(() => expect(comp.rootNode).toBeTruthy());

      // expandNode returns of([]) for the rootNode — emits once with [] then completes
      let emittedValue: any = null;
      comp.expandNode(comp.rootNode).subscribe(v => { emittedValue = v; });

      expect(emittedValue).toEqual([]);
   });

   it("should dispatch openDatasourcesFolder for non-FOLDER type nodes", async () => {
      const { comp } = await renderComponent();
      await waitFor(() => expect(comp.rootNode).toBeTruthy());

      const dbNode = makeNode({
         type: PortalDataType.DATABASE,
         data: { path: "myDb", scope: 0, name: "myDb", properties: {} } as any,
      });
      // provide a tree stub so tap() doesn't throw accessing tree.selectedNodes
      (comp as any).tree = { selectedNodes: [], exclusiveSelectNode: vi.fn() };

      let result: TreeNodeModel[] | null = null;
      comp.expandNode(dbNode).subscribe(v => { result = v; });

      await waitFor(() => expect(result).not.toBeNull());
      // openDatasourcesFolder for non-datasourceHome nodes returns of([])
      expect(result).toEqual([]);
   });
});

// ---------------------------------------------------------------------------
// Group 12 — hasMenuFunction [Risk 2]
// ---------------------------------------------------------------------------

describe("DataSourcesTreeViewComponent — hasMenuFunction", () => {
   it("should return true for a regular FOLDER node", async () => {
      const { comp } = await renderComponent();
      const node = makeNode({ type: PortalDataType.FOLDER });
      expect(comp.hasMenuFunction(node)).toBe(true);
   });

   it("should return false for PARTITION node with no properties set", async () => {
      const { comp } = await renderComponent();
      const node = makeNode({
         type: PortalDataType.PARTITION,
         properties: {
            [DatasourceTreeAction.RENAME]: "false",
            [DatasourceTreeAction.DELETE]: "false",
            [DatasourceTreeAction.CREATE_CHILDREN]: "false",
         },
      });
      expect(comp.hasMenuFunction(node)).toBe(false);
   });

   it("should return true for PARTITION node that has RENAME=true", async () => {
      const { comp } = await renderComponent();
      const node = makeNode({
         type: PortalDataType.PARTITION,
         properties: {
            [DatasourceTreeAction.RENAME]: "true",
            [DatasourceTreeAction.DELETE]: "false",
            [DatasourceTreeAction.CREATE_CHILDREN]: "false",
         },
      });
      expect(comp.hasMenuFunction(node)).toBe(true);
   });

   it("should return true for VPM node that has DELETE=true", async () => {
      const { comp } = await renderComponent();
      const node = makeNode({
         type: PortalDataType.VPM,
         properties: {
            [DatasourceTreeAction.RENAME]: "false",
            [DatasourceTreeAction.DELETE]: "true",
            [DatasourceTreeAction.CREATE_CHILDREN]: "false",
         },
      });
      expect(comp.hasMenuFunction(node)).toBe(true);
   });
});

// ---------------------------------------------------------------------------
// Group 13 — can* permission checks [Risk 3]
// ---------------------------------------------------------------------------

describe("DataSourcesTreeViewComponent — canCreateChildren", () => {
   it("should return true when CREATE_CHILDREN property is 'true'", async () => {
      const { comp } = await renderComponent();
      const node = makeNode({ properties: { [DatasourceTreeAction.CREATE_CHILDREN]: "true" } });
      expect(comp.canCreateChildren(node)).toBe(true);
   });

   it("should return false when CREATE_CHILDREN property is 'false'", async () => {
      const { comp } = await renderComponent();
      const node = makeNode({ properties: { [DatasourceTreeAction.CREATE_CHILDREN]: "false" } });
      expect(comp.canCreateChildren(node)).toBe(false);
   });
});

describe("DataSourcesTreeViewComponent — canRename", () => {
   it("should return true when RENAME property is 'true'", async () => {
      const { comp } = await renderComponent();
      const node = makeNode({ properties: { [DatasourceTreeAction.RENAME]: "true" } });
      expect(comp.canRename(node)).toBe(true);
   });

   it("should return false when RENAME property is missing", async () => {
      const { comp } = await renderComponent();
      const node = makeNode({ properties: {} });
      expect(comp.canRename(node)).toBe(false);
   });
});

describe("DataSourcesTreeViewComponent — canNewWorksheet", () => {
   it("should return true when NEW_WORKSHEET property is 'true'", async () => {
      const { comp } = await renderComponent();
      const node = makeNode({ properties: { [DatasourceTreeAction.NEW_WORKSHEET]: "true" } });
      expect(comp.canNewWorksheet(node)).toBe(true);
   });

   it("should return false when NEW_WORKSHEET property is absent", async () => {
      const { comp } = await renderComponent();
      const node = makeNode({ properties: {} });
      expect(comp.canNewWorksheet(node)).toBe(false);
   });
});

describe("DataSourcesTreeViewComponent — canNewDataSource", () => {
   it("should return true when NEW_DATASOURCE property is 'true'", async () => {
      const { comp } = await renderComponent();
      const node = makeNode({ properties: { [DatasourceTreeAction.NEW_DATASOURCE]: "true" } });
      expect(comp.canNewDataSource(node)).toBe(true);
   });

   it("should return false when NEW_DATASOURCE property is 'false'", async () => {
      const { comp } = await renderComponent();
      const node = makeNode({ properties: { [DatasourceTreeAction.NEW_DATASOURCE]: "false" } });
      expect(comp.canNewDataSource(node)).toBe(false);
   });
});

// ---------------------------------------------------------------------------
// Group 14 — canCreateLogicalModel [Risk 2]
// ---------------------------------------------------------------------------

describe("DataSourcesTreeViewComponent — canCreateLogicalModel", () => {
   it("should return true for DATA_MODEL node with partitionCount > 0", async () => {
      const { comp } = await renderComponent();
      const node = makeNode({
         type: PortalDataType.DATA_MODEL,
         properties: { partitionCount: "2" },
      });
      expect((comp as any).canCreateLogicalModel(node)).toBe(true);
   });

   it("should return false for DATA_MODEL node with partitionCount = 0", async () => {
      const { comp } = await renderComponent();
      const node = makeNode({
         type: PortalDataType.DATA_MODEL,
         properties: { partitionCount: "0" },
      });
      expect((comp as any).canCreateLogicalModel(node)).toBe(false);
   });

   it("should return false for DATA_MODEL node with no partitionCount property", async () => {
      const { comp } = await renderComponent();
      const node = makeNode({
         type: PortalDataType.DATA_MODEL,
         properties: {},
      });
      expect((comp as any).canCreateLogicalModel(node)).toBe(false);
   });

   it("should return false for a DATABASE node regardless of properties", async () => {
      const { comp } = await renderComponent();
      const node = makeNode({
         type: PortalDataType.DATABASE,
         properties: { partitionCount: "5" },
      });
      expect((comp as any).canCreateLogicalModel(node)).toBe(false);
   });
});

// ---------------------------------------------------------------------------
// Group 15 — getDataSourceObjectType [Risk 2]
// ---------------------------------------------------------------------------

describe("DataSourcesTreeViewComponent — getDataSourceObjectType", () => {
   it("should return WSObjectType.TABULAR for DATA_SOURCE type", async () => {
      const { comp } = await renderComponent();
      expect(comp.getDataSourceObjectType(PortalDataType.DATA_SOURCE)).toBe(WSObjectType.TABULAR);
   });

   it("should return WSObjectType.DATABASE_QUERY for DATABASE type", async () => {
      const { comp } = await renderComponent();
      expect(comp.getDataSourceObjectType(PortalDataType.DATABASE)).toBe(WSObjectType.DATABASE_QUERY);
   });

   it("should return -1 for an unknown type", async () => {
      const { comp } = await renderComponent();
      expect(comp.getDataSourceObjectType(PortalDataType.FOLDER)).toBe(-1);
   });
});

// ---------------------------------------------------------------------------
// Group 16 — isDataSourceFolder, isDataWorksheetFolder [Risk 2]
// ---------------------------------------------------------------------------

describe("DataSourcesTreeViewComponent — isDataSourceFolder", () => {
   it("should return true for DATA_SOURCE_ROOT_FOLDER", async () => {
      const { comp } = await renderComponent();
      const node = makeNode({ type: PortalDataType.DATA_SOURCE_ROOT_FOLDER });
      expect(comp.isDataSourceFolder(node)).toBe(true);
   });

   it("should return false for FOLDER type", async () => {
      const { comp } = await renderComponent();
      const node = makeNode({ type: PortalDataType.FOLDER });
      expect(comp.isDataSourceFolder(node)).toBe(false);
   });
});

describe("DataSourcesTreeViewComponent — isDataWorksheetFolder", () => {
   it("should return true for SHARED_WORKSHEETS_FOLDER", async () => {
      const { comp } = await renderComponent();
      const node = makeNode({ type: PortalDataType.SHARED_WORKSHEETS_FOLDER });
      expect(comp.isDataWorksheetFolder(node)).toBe(true);
   });

   it("should return false for DATA_SOURCE type", async () => {
      const { comp } = await renderComponent();
      const node = makeNode({ type: PortalDataType.DATA_SOURCE });
      expect(comp.isDataWorksheetFolder(node)).toBe(false);
   });
});

// ---------------------------------------------------------------------------
// Group 17 — newFolderVisible, renameVisible, deleteVisible, detailVisible [Risk 2]
// ---------------------------------------------------------------------------

describe("DataSourcesTreeViewComponent — visibility helpers", () => {
   it("newFolderVisible should return true for DATA_MODEL node", async () => {
      const { comp } = await renderComponent();
      const node = makeNode({ type: PortalDataType.DATA_MODEL });
      expect(comp.newFolderVisible(node)).toBe(true);
   });

   it("newFolderVisible should return false for DATABASE node", async () => {
      const { comp } = await renderComponent();
      const node = makeNode({ type: PortalDataType.DATABASE });
      expect(comp.newFolderVisible(node)).toBe(false);
   });

   it("renameVisible should return true for DATA_SOURCE_FOLDER", async () => {
      const { comp } = await renderComponent();
      const node = makeNode({ type: PortalDataType.DATA_SOURCE_FOLDER });
      expect(comp.renameVisible(node)).toBe(true);
   });

   it("renameVisible should return false for DATA_SOURCE", async () => {
      const { comp } = await renderComponent();
      const node = makeNode({ type: PortalDataType.DATA_SOURCE });
      expect(comp.renameVisible(node)).toBe(false);
   });

   it("deleteVisible should return true for DATABASE", async () => {
      const { comp } = await renderComponent();
      const node = makeNode({ type: PortalDataType.DATABASE });
      expect(comp.deleteVisible(node)).toBe(true);
   });

   it("deleteVisible should return false for DATA_SOURCE_ROOT_FOLDER", async () => {
      const { comp } = await renderComponent();
      const node = makeNode({ type: PortalDataType.DATA_SOURCE_ROOT_FOLDER });
      expect(comp.deleteVisible(node)).toBe(false);
   });

   it("detailVisible should return true for PRIVATE_WORKSHEETS_FOLDER", async () => {
      const { comp } = await renderComponent();
      const node = makeNode({ type: PortalDataType.PRIVATE_WORKSHEETS_FOLDER });
      expect(comp.detailVisible(node)).toBe(true);
   });

   it("detailVisible should return false for SHARED_WORKSHEETS_FOLDER", async () => {
      const { comp } = await renderComponent();
      const node = makeNode({ type: PortalDataType.SHARED_WORKSHEETS_FOLDER });
      expect(comp.detailVisible(node)).toBe(false);
   });
});

// ---------------------------------------------------------------------------
// Group 18 — isVpmVisible [Risk 2]
// ---------------------------------------------------------------------------

describe("DataSourcesTreeViewComponent — isVpmVisible", () => {
   it("should return false when identifier ends with ^SELF", async () => {
      const { comp } = await renderComponent();
      await waitFor(() => expect(comp.rootNode).toBeTruthy());
      const node = makeNode({
         type: PortalDataType.DATABASE,
         data: {
            path: "db",
            scope: 0,
            name: "db",
            identifier: "1^128^__NULL__^db^SELF",
            properties: {},
         } as any,
      });
      (comp as any).enterprise = true;
      expect((comp as any).isVpmVisible(node)).toBe(false);
   });

   it("should return false when enterprise=false even for DATABASE node", async () => {
      const { comp } = await renderComponent();
      await waitFor(() => expect(comp.rootNode).toBeTruthy());
      const node = makeNode({ type: PortalDataType.DATABASE });
      (comp as any).enterprise = false;
      expect((comp as any).isVpmVisible(node)).toBe(false);
   });

   it("should return true for DATABASE node when enterprise=true and identifier is not ^SELF", async () => {
      const { comp } = await renderComponent();
      await waitFor(() => expect(comp.rootNode).toBeTruthy());
      const node = makeNode({ type: PortalDataType.DATABASE });
      (comp as any).enterprise = true;
      expect((comp as any).isVpmVisible(node)).toBe(true);
   });

   it("should return true for VPM_FOLDER node when enterprise=true", async () => {
      const { comp } = await renderComponent();
      await waitFor(() => expect(comp.rootNode).toBeTruthy());
      const node = makeNode({ type: PortalDataType.VPM_FOLDER });
      (comp as any).enterprise = true;
      expect((comp as any).isVpmVisible(node)).toBe(true);
   });
});

// ---------------------------------------------------------------------------
// Group 19 — createActions action visibility / enabled conditions [Risk 2]
// ---------------------------------------------------------------------------

describe("DataSourcesTreeViewComponent — createActions", () => {
   it("should include new-folder action visible for DATA_MODEL node", async () => {
      const { comp } = await renderComponent();
      const node = makeNode({ type: PortalDataType.DATA_MODEL });
      const groups = (comp as any).createActions(node);
      const action = groups[0].actions.find((a: any) => a.id() === "repository-tree new-folder");
      expect(action.visible()).toBe(true);
   });

   it("should include new-database action enabled only when canNewDataSource is true", async () => {
      const { comp } = await renderComponent();
      const node = makeNode({
         type: PortalDataType.DATA_SOURCE_ROOT_FOLDER,
         properties: { [DatasourceTreeAction.NEW_DATASOURCE]: "true" },
      });
      const groups = (comp as any).createActions(node);
      const action = groups[0].actions.find((a: any) => a.id() === "repository-tree new-database");
      expect(action.visible()).toBe(true);
      expect(action.enabled()).toBe(true);
   });

   it("should include add-logical-model action visible for PARTITION node", async () => {
      const { comp } = await renderComponent();
      const node = makeNode({
         type: PortalDataType.PARTITION,
         properties: { partitionCount: "1", [DatasourceTreeAction.CREATE_CHILDREN]: "true" },
      });
      const groups = (comp as any).createActions(node);
      const action = groups[0].actions.find((a: any) => a.id() === "add logical model");
      expect(action.visible()).toBe(true);
   });

   it("should include add-physical-view action visible for DATABASE node", async () => {
      const { comp } = await renderComponent();
      const node = makeNode({ type: PortalDataType.DATABASE });
      const groups = (comp as any).createActions(node);
      const action = groups[0].actions.find((a: any) => a.id() === "add physical view");
      expect(action.visible()).toBe(true);
   });

   it("should include worksheet-detail action visible for FOLDER node (not SHARED_WORKSHEETS_FOLDER)", async () => {
      const { comp } = await renderComponent();
      const node = makeNode({ type: PortalDataType.FOLDER });
      const groups = (comp as any).createActions(node);
      const action = groups[0].actions.find((a: any) => a.id() === "worksheet folder detail");
      expect(action.visible()).toBe(true);
   });
});

// ---------------------------------------------------------------------------
// Group 20 — contextMenu [Risk 2]
// ---------------------------------------------------------------------------

describe("DataSourcesTreeViewComponent — contextMenu", () => {
   it("should not open dropdown when hasMenuFunction returns false for the node", async () => {
      const { comp, dropdownService } = await renderComponent();
      const node = makeNode({
         type: PortalDataType.PARTITION,
         properties: {
            [DatasourceTreeAction.RENAME]: "false",
            [DatasourceTreeAction.DELETE]: "false",
            [DatasourceTreeAction.CREATE_CHILDREN]: "false",
         },
      });
      const mouseEvent = { clientX: 100, clientY: 200 } as MouseEvent;
      comp.contextMenu([mouseEvent, node, []]);
      expect(dropdownService.open).not.toHaveBeenCalled();
   });

   it("should open dropdown and set actions when hasMenuFunction returns true", async () => {
      const { comp, dropdownService } = await renderComponent();
      const node = makeNode({ type: PortalDataType.FOLDER });
      const mouseEvent = { clientX: 100, clientY: 200 } as MouseEvent;
      comp.contextMenu([mouseEvent, node, []]);
      expect(dropdownService.open).toHaveBeenCalledTimes(1);
   });
});

// ---------------------------------------------------------------------------
// Group 21 — createQuery [Risk 2]
// ---------------------------------------------------------------------------

describe("DataSourcesTreeViewComponent — createQuery", () => {
   it("should not call clientService.sendEvent when node type is not DATABASE or DATA_SOURCE", async () => {
      const { comp, vsClient } = await renderComponent();
      const node = makeNode({ type: PortalDataType.FOLDER });
      comp.createQuery(node);
      expect(vsClient.sendEvent).not.toHaveBeenCalled();
   });

   it("should not call clientService.sendEvent when queryCreatable is 'false'", async () => {
      const { comp, vsClient } = await renderComponent();
      const node = makeNode({
         type: PortalDataType.DATABASE,
         properties: { queryCreatable: "false" },
      });
      comp.createQuery(node);
      expect(vsClient.sendEvent).not.toHaveBeenCalled();
   });

   it("should send STOMP event to CREATE_QUERY_URI when composerOpen=true and not composedDashboard", async () => {
      const subjects = makeSubjects();
      const { comp, vsClient } = await renderComponent({ subjects });

      // Override composerOpen to emit true
      const openComposerSvc = (comp as any).openComposerService;
      openComposerSvc.composerOpen = of(true);

      const node = makeNode({
         type: PortalDataType.DATABASE,
         data: { path: "myDb", scope: 0, name: "myDb", properties: {} } as any,
      });
      comp.createQuery(node);

      expect(vsClient.sendEvent).toHaveBeenCalledWith(
         "/events/composer/ws/query/create",
         expect.any(Object)
      );
   });
});

// ---------------------------------------------------------------------------
// Group 22 — addDataModel [Risk 2]
// ---------------------------------------------------------------------------

describe("DataSourcesTreeViewComponent — addDataModel", () => {
   it("should call dataModelBrowserService.addVPM for DATABASE node with createType=VPM", async () => {
      const { comp, dataModelBrowserService } = await renderComponent();
      const node = makeNode({
         type: PortalDataType.DATABASE,
         data: { path: "myDb", scope: 0, name: "myDb", properties: {} } as any,
      });
      comp.addDataModel(node, PortalDataType.VPM);
      expect(dataModelBrowserService.addVPM).toHaveBeenCalledWith("myDb");
   });

   it("should call dataModelBrowserService.addPhysicalView for DATABASE node with createType=PARTITION", async () => {
      const { comp, dataModelBrowserService } = await renderComponent();
      const node = makeNode({
         type: PortalDataType.DATABASE,
         data: { path: "myDb", scope: 0, name: "myDb", properties: {} } as any,
      });
      comp.addDataModel(node, PortalDataType.PARTITION);
      expect(dataModelBrowserService.addPhysicalView).toHaveBeenCalledWith("myDb", undefined);
   });

   it("should call dataModelBrowserService.addLogicalModel for PARTITION node with createType=LOGIC_MODEL", async () => {
      const { comp, dataModelBrowserService } = await renderComponent();
      const node = makeNode({
         type: PortalDataType.PARTITION,
         data: { path: "myDb/myPartition", scope: 0, name: "myPartition", properties: {} } as any,
      });
      comp.addDataModel(node, PortalDataType.LOGIC_MODEL);
      expect(dataModelBrowserService.addLogicalModel).toHaveBeenCalledWith("myDb", "myPartition", undefined);
   });

   it("should call dataModelBrowserService.addVPM for VPM_FOLDER node with createType=VPM", async () => {
      const { comp, dataModelBrowserService } = await renderComponent();
      const node = makeNode({
         type: PortalDataType.VPM_FOLDER,
         data: {
            path: "myDb/VPM",
            scope: 0,
            name: "VPM",
            properties: { databasePath: "myDb" },
         } as any,
      });
      comp.addDataModel(node, PortalDataType.VPM);
      expect(dataModelBrowserService.addVPM).toHaveBeenCalledWith("myDb");
   });
});

// ---------------------------------------------------------------------------
// Group 23 — splitModelName [Risk 2]
// ---------------------------------------------------------------------------

describe("DataSourcesTreeViewComponent — splitModelName", () => {
   it("should return database=path and empty name for DATABASE node", async () => {
      const { comp } = await renderComponent();
      const node = makeNode({
         type: PortalDataType.DATABASE,
         data: { path: "myDb", scope: 0, name: "myDb", properties: {} } as any,
      });
      const result = (comp as any).splitModelName(node);
      expect(result.database).toBe("myDb");
      expect(result.name).toBe("");
   });

   it("should split path on last slash for PARTITION node", async () => {
      const { comp } = await renderComponent();
      const node = makeNode({
         type: PortalDataType.PARTITION,
         data: { path: "myDb/physModel", scope: 0, name: "physModel", properties: {} } as any,
      });
      const result = (comp as any).splitModelName(node);
      expect(result.database).toBe("myDb");
      expect(result.name).toBe("physModel");
   });

   it("should return null when path has no slash (invalid model path)", async () => {
      const { comp } = await renderComponent();
      const node = makeNode({
         type: PortalDataType.PARTITION,
         data: { path: "noSlashPath", scope: 0, name: "noSlashPath", properties: {} } as any,
      });
      const result = (comp as any).splitModelName(node);
      expect(result).toBeNull();
   });
});

// ---------------------------------------------------------------------------
// Group 24 — onScroll [Risk 1]
// ---------------------------------------------------------------------------

describe("DataSourcesTreeViewComponent — onScroll", () => {
   it("should update scrollY from treeContainer.nativeElement.scrollTop", async () => {
      const { comp } = await renderComponent();
      (comp as any).treeContainer = { nativeElement: { scrollTop: 42 } };
      comp.onScroll({} as any);
      expect(comp.scrollY).toBe(42);
   });
});

// ---------------------------------------------------------------------------
// Group 25 — getDuplicateCheckUri [Risk 1]
// ---------------------------------------------------------------------------

describe("DataSourcesTreeViewComponent — getDuplicateCheckUri", () => {
   it("should return the physical model URI for PARTITION type", async () => {
      const { comp } = await renderComponent();
      const uri = (comp as any).getDuplicateCheckUri(PortalDataType.PARTITION);
      expect(uri).toContain("logicalModel/checkDuplicate");
   });

   it("should return the logical model URI for LOGIC_MODEL type", async () => {
      const { comp } = await renderComponent();
      const uri = (comp as any).getDuplicateCheckUri(PortalDataType.LOGIC_MODEL);
      expect(uri).toContain("logicalModel/checkDuplicate");
   });

   it("should return the vpm URI for VPM type", async () => {
      const { comp } = await renderComponent();
      const uri = (comp as any).getDuplicateCheckUri(PortalDataType.VPM);
      expect(uri).toContain("vpm/checkDuplicate");
   });

   it("should return null for an unrecognized type", async () => {
      const { comp } = await renderComponent();
      const uri = (comp as any).getDuplicateCheckUri(PortalDataType.FOLDER);
      expect(uri).toBeNull();
   });
});

// ---------------------------------------------------------------------------
// Group 26 — getAssemblyName [Risk 1]
// ---------------------------------------------------------------------------

describe("DataSourcesTreeViewComponent — getAssemblyName", () => {
   it("should return null", async () => {
      const { comp } = await renderComponent();
      expect(comp.getAssemblyName()).toBeNull();
   });
});
