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
 * MSW handlers for Portal endpoints.
 *
 * Covers the ../api/portal/*, ../api/data/*, and shared schedule/data API paths
 * used by components in the portal project. All defaults are the "happy path"
 * minimal responses needed for a component to finish initialisation without errors.
 *
 * Per-test overrides:
 *   import { server } from '<path>/mocks/server';
 *   import { http, HttpResponse } from 'msw';
 *   server.use(http.post('*\/api/portal/scheduledTasks', () => HttpResponse.json(myList)));
 *
 * Organised into five sections:
 *   1. Portal app-level  — model, tabs, current user, history bar, permissions
 *   2. Schedule          — task list, folders, task CRUD, user/task-name lookups
 *   3. Datasource browser — browser model, folder CRUD, statuses, search
 *   4. Physical model / database — graph, joins, views, data model browser
 *   5. Query builder     — query model, fields tree, graph, column ops
 */
import { http, HttpResponse } from "msw";

// ─── shared fixture helpers ───────────────────────────────────────────────────

const EMPTY_TREE_NODE = {
   label: "",
   data: { path: "" },
   children: [],
   leaf: false,
   expanded: false,
};

const NO_DUPLICATE = { duplicate: false };

const NO_TASK_DEPS = { taskNames: [] };

// ─── 1. Portal app-level ─────────────────────────────────────────────────────

export const portalHandlers = [

   // PortalAppComponent.ngOnInit / PortalModelService — always fetched on bootstrap
   http.get("*/api/portal/get-portal-model", () => {
      return HttpResponse.json({
         currentUser: { name: "admin", alias: "Administrator", anonymous: false, orgID: "host_org" },
         helpVisible: false,
         aiAssistantVisible: false,
         preferencesVisible: true,
         logoutVisible: true,
         homeLink: "",
         homeVisible: true,
         reportEnabled: true,
         composerEnabled: true,
         dashboardEnabled: true,
         newDatasourceEnabled: true,
         newWorksheetEnabled: true,
         newViewsheetEnabled: true,
         customLogo: false,
         helpURL: "",
         logoutUrl: "",
         accessible: false,
         hasDashboards: false,
         title: "StyleBI",
         profile: false,
         profiling: false,
         elasticLicenseExhausted: false,
      });
   }),

   // PortalTabsService.getPortalTabs() — used by PortalRedirectComponent and nav
   http.get("*/api/portal/get-portal-tabs", () => {
      return HttpResponse.json([
         { name: "Dashboard", label: "Dashboard", uri: "tab/dashboard", custom: false, visible: true },
         { name: "Report",    label: "Report",    uri: "tab/report",    custom: false, visible: true },
         { name: "Data",      label: "Data",      uri: "tab/data",      custom: false, visible: true },
         { name: "Schedule",  label: "Schedule",  uri: "tab/schedule",  custom: false, visible: true },
      ]);
   }),

   // HistoryBarService — used by portal toolbar
   http.get("*/api/portal/get-history-bar-status", () => {
      return HttpResponse.json(false);
   }),

   // AppComponent.ngOnInit — creation-permission refresh on load
   http.get("*/api/portal/refresh-creation-permissions", () => {
      return new HttpResponse(null, { status: 200 });
   }),

   // ReportTabComponent — current user info for report tab
   http.get("*/api/portal/get-current-user", () => {
      return HttpResponse.json({ name: "admin", alias: "Administrator" });
   }),

   // RepositoryTreeViewComponent — portal content tree root
   http.get("*/api/portal/tree", () => {
      return HttpResponse.json({ ...EMPTY_TREE_NODE, label: "Repository", leaf: false });
   }),

   // RepositoryTreeViewComponent — tree search results
   http.get("*/api/portal/tree/search", () => {
      return HttpResponse.json({ nodes: [] });
   }),

   // MaterializedView resource check — used by model dialogs
   http.get("*/api/portal/content/materialized-view/isOrgAccessGlobalMV/*", () => {
      return HttpResponse.json(false);
   }),

   // ─── 2. Schedule ──────────────────────────────────────────────────────────

   // ScheduleTaskListComponent.ngOnInit — whether to show list or tree layout
   http.get("*/api/portal/schedule/change-show-type", () => {
      return HttpResponse.json(true); // true = list (flat), false = folder-tree
   }),

   // ScheduleTaskListComponent.ngOnInit — whether user can create tasks at root
   http.get("*/api/portal/schedule/folder/checkRootPermission", () => {
      return HttpResponse.json(true);
   }),

   // ScheduleTaskListComponent.loadTaskFolderTree()
   http.get("*/api/portal/schedule/tree", () => {
      return HttpResponse.json({ ...EMPTY_TREE_NODE, label: "Tasks" });
   }),

   // ScheduleTaskListComponent.loadTasks() — POST with optional folder context
   http.post("*/api/portal/scheduledTasks", () => {
      return HttpResponse.json({
         tasks: [],
         timeZone: "US/Eastern",
         timeZoneId: "America/New_York",
         timeZoneOffset: -18000000,
         dateTimeFormat: "yyyy-MM-dd HH:mm:ss",
         showOwners: false,
      });
   }),

   // ScheduleTaskListComponent — is the current user their org's own user
   http.get("*/api/portal/schedule/isSelfOrgUser", () => {
      return HttpResponse.json(false);
   }),

   // ScheduleUsersService (portal=true) — owners, groups, email lists
   http.get("*/api/portal/schedule/users-model", () => {
      return HttpResponse.json({
         owners: [],
         groups: [],
         emailGroups: [],
         emailUsers: [],
         adminName: "admin",
         ssoEnable: false,
      });
   }),

   // ScheduleTaskNamesService (portal=true) — completion-condition dropdown list
   http.get("*/api/portal/schedule/task-names", () => {
      return HttpResponse.json({ allTasks: [] });
   }),

   // ScheduleUsersService (EM, non-portal) — same shape, different path
   http.get("*/api/em/schedule/users-model", () => {
      return HttpResponse.json({
         owners: [],
         groups: [],
         emailGroups: [],
         emailUsers: [],
         adminName: "admin",
         ssoEnable: false,
      });
   }),

   // ScheduleTaskNamesService (EM, non-portal)
   http.get("*/api/em/schedule/task-names", () => {
      return HttpResponse.json({ allTasks: [] });
   }),

   // Task editor — action options (viewsheet, backup, email, etc.)
   http.get("*/api/portal/schedule/task/action", () => {
      return HttpResponse.json({
         label: "",
         actionType: "ViewsheetAction",
         viewsheetEnabled: true,
         enabled: true,
      });
   }),

   // Task editor — condition options (time, completion, etc.)
   http.get("*/api/portal/schedule/task/condition", () => {
      return HttpResponse.json({ conditions: [] });
   }),

   // Task editor — whether the viewsheet has a print layout
   http.get("*/api/portal/schedule/task/action/hasPrintLayout", () => {
      return HttpResponse.json(false);
   }),

   // Task editor — bookmark list for viewsheet action
   http.get("*/api/portal/schedule/task/action/bookmarks", () => {
      return HttpResponse.json({ bookmarks: [] });
   }),

   // ScheduleTaskListComponent.newTask()
   http.post("*/api/portal/schedule/new", () => {
      return HttpResponse.json({ name: "New Task", taskDefaultTime: null });
   }),

   // ScheduleTaskListComponent.deleteTasks() — dependency pre-check
   http.post("*/api/portal/schedule/check-dependency", () => {
      return HttpResponse.json(NO_TASK_DEPS);
   }),

   // ScheduleTaskListComponent.deleteFolder() — folder dependency pre-check
   http.post("*/api/portal/schedule/folder/check-dependency", () => {
      return HttpResponse.json(NO_TASK_DEPS);
   }),

   // ScheduleTaskListComponent.deleteTasks() — actual delete
   http.post("*/api/portal/schedule/remove", () => {
      return new HttpResponse(null, { status: 200 });
   }),

   // ScheduleTaskListComponent.deleteFolder() — actual folder delete (returns success bool)
   http.post("*/api/portal/schedule/folder/remove", () => {
      return HttpResponse.json(true);
   }),

   // ScheduleTaskListComponent.addFolder() — duplicate name check
   http.post("*/api/portal/schedule/add/checkDuplicate", () => {
      return HttpResponse.json(NO_DUPLICATE);
   }),

   // ScheduleTaskListComponent.addFolder() — create folder
   http.post("*/api/portal/schedule/folder/add", () => {
      return new HttpResponse(null, { status: 200 });
   }),

   // EditTaskFolderDialogComponent — load edit model
   http.post("*/api/portal/schedule/folder/editModel", () => {
      return HttpResponse.json({ folderName: "My Folder", path: "/My Folder", alias: "" });
   }),

   // EditTaskFolderDialogComponent.save() — rename
   http.post("*/api/portal/schedule/rename-folder", () => {
      return HttpResponse.json("/My Folder");
   }),

   // MoveTaskDialogComponent — duplicate check before move
   http.post("*/api/portal/schedule/move/checkDuplicate", () => {
      return HttpResponse.json(NO_DUPLICATE);
   }),

   // ScheduleTaskListComponent.moveItems()
   http.post("*/api/portal/schedule/move-items", () => {
      return new HttpResponse(null, { status: 200 });
   }),

   // ScheduleTaskListComponent.changeShowType() persist
   http.put("*/api/portal/schedule/change-show-type", () => {
      return new HttpResponse(null, { status: 200 });
   }),

   // ScheduleTaskListComponent.runTask()
   http.get("*/api/portal/schedule/run", () => {
      return new HttpResponse(null, { status: 200 });
   }),

   // ScheduleTaskListComponent.stopTask()
   http.get("*/api/portal/schedule/stop", () => {
      return new HttpResponse(null, { status: 200 });
   }),

   // ScheduleTaskListComponent.disableTask()
   http.get("*/api/portal/schedule/enable/task", () => {
      return new HttpResponse(null, { status: 200 });
   }),

   // TaskConditionPane — date-time service (used by EM and portal shared editors)
   http.get("*/api/em/schedule/time-zones", () => {
      return HttpResponse.json({
         timeZones: [
            { timeZoneId: "America/New_York", label: "(UTC-05:00) Eastern Time", hourOffset: "-05", minuteOffset: -300 },
            { timeZoneId: "UTC",              label: "(UTC+00:00) UTC",          hourOffset: "+00", minuteOffset: 0 },
         ],
      });
   }),

   // ─── 3. Datasource browser ────────────────────────────────────────────────

   // DataDatasourceBrowserComponent.ngOnInit / DatasourceBrowserService — main list
   http.get("*/api/data/datasources/browser", () => {
      return HttpResponse.json({
         dataSourceList: [],
         currentFolder: [],
         root: true,
         newDatasourceEnabled: true,
         newVpmEnabled: true,
      });
   }),

   // DataDatasourceBrowserComponent — connection status batch check (POST with name list)
   http.post("*/api/data/datasources/statuses", () => {
      return HttpResponse.json([]);
   }),

   // DataSourcesTreeViewComponent — flat datasource list
   http.get("*/api/data/datasources", () => {
      return HttpResponse.json([]);
   }),

   // DataDatasourceBrowserComponent — datasource search (component sends POST with SearchCommand body)
   http.post("*/api/data/search/dataSources", () => {
      return HttpResponse.json([]);
   }),

   // DataSourcesTreeViewComponent — datasource list (list view)
   http.get("*/api/data/dataSources/list", () => {
      return HttpResponse.json([]);
   }),

   // DatasourceBrowserService / DataDatasourceBrowserComponent — add folder
   http.post("*/api/data/datasources/browser/folder/add", () => {
      return HttpResponse.json({ path: "/test-folder", name: "test-folder" });
   }),

   // DatasourceBrowserService — duplicate name check before adding folder
   http.post("*/api/data/datasources/browser/folder/checkDuplicate", () => {
      return HttpResponse.json(NO_DUPLICATE);
   }),

   // DatasourceBrowserService — outer dependency check before deleting folder
   http.post("*/api/data/datasources/browser/folder/checkOuterDependencies", () => {
      return HttpResponse.json({ dependencies: [], hasDependencies: false });
   }),

   // DatasourceBrowserService — delete folder
   http.delete("*/api/data/datasources/browser/folder", () => {
      return new HttpResponse(null, { status: 200 });
   }),

   // DataSourcesTreeActionsService — outer dependency check before deleting datasource
   http.post("*/api/data/datasources/checkOuterDependencies", () => {
      return HttpResponse.json({ dependencies: [], hasDependencies: false });
   }),

   // DatasourceBrowserService — move datasource/folder
   http.post("*/api/data/datasources/move", () => {
      return new HttpResponse(null, { status: 200 });
   }),

   // DataSourcesTreeViewComponent — duplicate check before move
   http.post("*/api/data/datasources/move/checkDuplicate", () => {
      return HttpResponse.json(NO_DUPLICATE);
   }),

   // Portal datasource detail (used by datasources-xmla, etc.)
   http.get("*/api/portal/data/datasources/*", () => {
      return HttpResponse.json(null);
   }),

   // ─── 3a. Data folder browser ──────────────────────────────────────────────

   // DataFolderBrowserComponent.refreshFolderBrowser — root browser (no path)
   http.get("*/api/portal/data/browser", () => {
      return HttpResponse.json({
         root: true,
         worksheetAccess: true,
         currentFolder: [],
         folders: [],
         files: [],
      });
   }),

   // DataFolderBrowserComponent.refreshFolderBrowser — browser with path segment
   http.get("*/api/portal/data/browser/*", () => {
      return HttpResponse.json({
         root: false,
         worksheetAccess: true,
         currentFolder: [],
         folders: [],
         files: [],
      });
   }),

   // DataFolderBrowserComponent.refreshSearchBrowser — dataset search results
   http.post("*/api/data/search/datasets", () => {
      return HttpResponse.json({ assets: [], assetNames: [] });
   }),

   // DataFolderBrowserComponent.searchFunc — ngbTypeahead autocomplete suggestions
   http.post("*/api/data/search/datasets/assetNames", () => {
      return HttpResponse.json({ assetNames: [], assets: [] });
   }),

   // DataFolderBrowserComponent.deleteSelected — check which items are removable
   http.post("*/api/data/removeableStatuses", () => {
      return HttpResponse.json({ folderDependencies: [], datasetDependencies: [] });
   }),

   // DataFolderBrowserComponent.deleteSelected — bulk delete confirmed items
   http.post("*/api/data/removeAll", () => {
      return new HttpResponse(null, { status: 200 });
   }),

   // DataFolderBrowserComponent.addFolder — create new worksheet folder
   http.post("*/api/data/folders", () => {
      return new HttpResponse(null, { status: 200 });
   }),

   // DataFolderBrowserComponent.addFolder — duplicate name check for new folder
   http.post("*/api/data/datasets/isDuplicate", () => {
      return HttpResponse.json({ duplicate: false });
   }),

   // DataFolderBrowserComponent.moveAssets — bulk move folders
   http.post("*/api/data/folders/moveFolders", () => {
      return new HttpResponse(null, { status: 200 });
   }),

   // DataFolderBrowserComponent.moveAssets — bulk move datasets
   http.post("*/api/data/datasets/moveDatasets", () => {
      return new HttpResponse(null, { status: 200 });
   }),

   // DataFolderBrowserComponent.moveAsset (single) — move one folder
   http.post("*/api/data/folders/move", () => {
      return new HttpResponse(null, { status: 200 });
   }),

   // DataFolderBrowserComponent.moveAsset (single) — move one dataset
   http.post("*/api/data/datasets/move", () => {
      return new HttpResponse(null, { status: 200 });
   }),

   // ─── 3b. Data sources tree view ───────────────────────────────────────────

   // DataSourcesTreeViewComponent.getDataNavigationTree — combined nav tree root
   http.get("*/api/portal/data/tree", () => {
      return HttpResponse.json({
         label: "Root",
         data: { path: "/", name: "root", scope: 0 },
         children: [
            {
               label: "Data Sources",
               data: { path: "/datasources", name: "datasources", scope: 0, type: "DATA_SOURCE_ROOT_FOLDER", properties: {} },
               children: [],
               leaf: false,
               expanded: false,
               type: "DATA_SOURCE_ROOT_FOLDER",
            },
         ],
         leaf: false,
         expanded: true,
         type: "ROOT",
      });
   }),

   // DataSourcesTreeViewComponent.openFolder — expand a worksheet folder node
   http.get("*/api/data/folders/children/*", () => {
      return HttpResponse.json([]);
   }),

   // DataSourcesTreeViewComponent.openDatasourcesFolder — expand datasource root node
   http.get("*/api/data/datasources/nodes", () => {
      return HttpResponse.json([]);
   }),

   // DataSourcesTreeViewComponent.checkDataFoldersDuplicate — folder move duplicate check
   http.post("*/api/data/move/checkDuplicate", () => {
      return HttpResponse.json(NO_DUPLICATE);
   }),

   // DataSourcesTreeViewComponent — physical/logical model duplicate check before move
   http.post("*/api/data/logicalModel/checkDuplicate", () => {
      return HttpResponse.json(NO_DUPLICATE);
   }),

   // DataSourcesTreeViewComponent — VPM duplicate check before move
   http.post("*/api/data/vpm/checkDuplicate", () => {
      return HttpResponse.json(NO_DUPLICATE);
   }),

   // AppInfoService.isEnterprise() — shared service, default community edition
   http.get("*/api/enterprise", () => {
      return HttpResponse.json(false);
   }),

   // AppInfoService constructor — org info for shared service
   http.get("*/api/org/info", () => {
      return HttpResponse.json({ key: "host_org", value: "Default" });
   }),

   // ─── 4. Physical model / database ────────────────────────────────────────

   // DatasourcesDatabaseComponent — load database connection form
   http.get("*/api/portal/data/databases/*", () => {
      return HttpResponse.json({
         name: "",
         driver: "",
         url: "",
         user: "",
         productName: "",
         ansiJoin: false,
         tableNameOption: 0,
         defaultDatabase: "",
         changeDefaultDB: false,
         unasgn: false,
         customEditMode: false,
         uploadEnabled: false,
         additional: [],
         info: null,
         networkTimeout: 0,
         testQuery: "",
      });
   }),

   // DatasourcesDatabaseComponent — refresh schema metadata
   http.post("*/api/portal/data/datasource/refresh-metadata", () => {
      return new HttpResponse(null, { status: 200 });
   }),

   // DatasourcesDatabaseComponent — check if extra (additional) datasource can be deleted
   http.get("*/api/portal/data/databases/additional/check/*", () => {
      return HttpResponse.json(false);
   }),

   // DatabaseDataModelBrowserComponent / DataModelBrowserService — data model folder
   http.post("*/api/portal/data/database/dataModelFolder", () => {
      return new HttpResponse(null, { status: 200 });
   }),

   // DataModelBrowserService — duplicate check for data model folder
   http.post("*/api/portal/data/database/dataModelFolder/duplicateCheck", () => {
      return HttpResponse.json(NO_DUPLICATE);
   }),

   // DataModelBrowserService — move data model
   http.post("*/api/data/database/dataModel/move", () => {
      return new HttpResponse(null, { status: 200 });
   }),

   // DataModelBrowserService — remove data model
   http.post("*/api/data/database/dataModel/remove", () => {
      return new HttpResponse(null, { status: 200 });
   }),

   // DataModelBrowserService / physical model — editable check
   http.get("*/api/data/model/checkEditable/*", () => {
      return HttpResponse.json(true);
   }),

   // LogicalModelPropertyPane — logical model edit permission
   http.get("*/api/data/logicalmodel/permission/editable", () => {
      return HttpResponse.json(true);
   }),

   // LogicalModelComponent — datasource settings (fullDateSupport, etc.)
   http.get("*/api/data/logicalmodel/settings", () => {
      return HttpResponse.json({ fullDateSupport: true });
   }),

   // LogicalModelComponent — load model (edit mode)
   http.get("*/api/data/logicalmodel/models", () => {
      return HttpResponse.json({
         name: "LM1", partition: "physModel", entities: [],
         description: "", connection: null, parent: null, folder: "",
      });
   }),

   // LogicalModelComponent — save model (edit)
   http.put("*/api/data/logicalmodel/models", () => {
      return HttpResponse.json({
         name: "LM1", partition: "physModel", entities: [],
         description: "", connection: null, parent: null, folder: "",
      });
   }),

   // LogicalModelComponent — create model
   http.post("*/api/data/logicalmodel/models", () => {
      return HttpResponse.json({
         name: "LM1", partition: "physModel", entities: [],
         description: "", connection: null, parent: null, folder: "",
      });
   }),

   // LogicalModelComponent — create extended model
   http.post("*/api/data/logicalmodel/extended", () => {
      return HttpResponse.json({
         name: "LM1", partition: "physModel", entities: [],
         description: "", connection: null, parent: "parentLM", folder: "",
      });
   }),

   // PhysicalGraphPaneComponent — load graph canvas model
   http.get("*/api/data/physicalmodel/graph", () => {
      return HttpResponse.json({
         nodes: [],
         joins: [],
         runtimeID: "",
         autoAlias: false,
         selectedNodes: [],
         outOfDate: false,
      });
   }),

   // PhysicalGraphPaneComponent — auto-layout
   http.get("*/api/data/physicalmodel/graph/layout/*", () => {
      return HttpResponse.json({ nodes: [] });
   }),

   // PhysicalGraphPaneComponent — persist graph pane size
   http.put("*/api/data/physicalmodel/graph/size/*", () => {
      return new HttpResponse(null, { status: 200 });
   }),

   // PhysicalGraphPaneComponent — close join edit pane
   http.post("*/api/data/physicalmodel/join-edit/close", () => {
      return new HttpResponse(null, { status: 200 });
   }),

   // ChoosePhysicalViewDialogComponent — available physical views list
   http.get("*/api/data/physicalmodel/views", () => {
      return HttpResponse.json({ views: [] });
   }),

   // PhysicalJoinEditPaneComponent — heartbeat to keep server-side session alive
   http.get("*/api/data/physicalmodel/heartbeat", () => {
      return new HttpResponse(null, { status: 200 });
   }),

   // JoinThumbnailService — save/update physical join
   http.put("*/api/data/physicalmodel/join", () => {
      return new HttpResponse(null, { status: 200 });
   }),

   // JoinNodeGraphComponent — create alias table
   http.post("*/api/data/physicalmodel/graph/alias", () => {
      return new HttpResponse(null, { status: 200 });
   }),

   // JoinNodeGraphComponent — check alias duplicate
   http.get("*/api/data/physicalmodel/graph/alias/status", () => {
      return HttpResponse.json({ hasDuplicate: false });
   }),

   // JoinNodeGraphComponent — refresh graph node
   http.post("*/api/data/physicalmodel/graph/node/refresh", () => {
      return new HttpResponse(null, { status: 200 });
   }),

   // PhysicalModelNetworkGraphComponent — delete selected joins
   http.post("*/api/data/physicalmodel/joins/delete", () => {
      return new HttpResponse(null, { status: 200 });
   }),

   // PhysicalModelNetworkGraphComponent — move graph node position
   http.put("*/api/data/physicalmodel/graph/move", () => {
      return new HttpResponse(null, { status: 200 });
   }),

   // AutoDrillDialog — viewsheet repository tree (hyperlink picker)
   http.get("*/api/composer/vs/hyperlink-dialog-model/tree", () => {
      return HttpResponse.json({
         label: "Root",
         leaf: false,
         expanded: false,
         data: { path: "/" },
         children: [],
      });
   }),

   // PhysicalModelNetworkGraphComponent — remove tables (POST)
   http.post("*/api/data/physicalmodel/tables/remove", () => {
      return HttpResponse.json(null);
   }),

   // PhysicalModelNetworkGraphComponent — clear all tables (DELETE)
   http.delete("*/api/data/physicalmodel/table/*", () => {
      return HttpResponse.json(null);
   }),

   // PhysicalModelNetworkGraphComponent — clear all joins (DELETE)
   http.delete("*/api/data/physicalmodel/join/*", () => {
      return HttpResponse.json(null);
   }),

   // PhysicalModelNetworkGraphComponent — open join edit pane (GET)
   http.get("*/api/data/physicalmodel/join-edit/open/*", () => {
      return HttpResponse.json("newRuntimeId");
   }),

   // LogicalModelExpressionDialog — column fields tree (POST)
   http.post("*/api/data/logicalModel/tables/nodes", () => {
      return HttpResponse.json({
         label: "_#(js:Fields)", expanded: false, leaf: false, children: [],
      });
   }),

   // LogicalModelAttributeEditor — attribute format string preview (POST)
   http.post("*/api/data/logicalModel/attribute/format", () => {
      return HttpResponse.json(null);
   }),

   // LogicalModelExpressionDialog — validate expression (POST)
   http.post("*/api/data/logicalModel/attribute/expression", () => {
      return HttpResponse.json({});
   }),

   // ─── VPM (Virtual Private Model) ─────────────────────────────────────────

   // DatabaseVPMComponent.ngOnInit — users and roles for the Test tab
   http.get("*/api/data/vpm/usersRoles", () => {
      return HttpResponse.json({ users: [], roles: [] });
   }),

   // DatabaseVPMComponent.ngOnInit — available clause operators for condition editor
   http.get("*/api/data/vpm/operations", () => {
      return HttpResponse.json([]);
   }),

   // DatabaseVPMComponent.refreshVPM() — load VPM definition in edit mode
   http.get("*/api/data/vpm/models", () => {
      return HttpResponse.json({
         name: "myVPM",
         conditions: [],
         hidden: { roles: [], hiddens: [], name: null, script: null },
         lookup: "",
         description: "",
      });
   }),

   // DatabaseVPMComponent.saveVPM() — update VPM (edit mode, responseType: "text")
   http.put("*/api/data/vpm/models", () => {
      return new HttpResponse("", { status: 200, headers: { "Content-Type": "text/plain" } });
   }),

   // DatabaseVPMComponent.saveVPM() — create VPM (create mode, responseType: "text")
   http.post("*/api/data/vpm/models", () => {
      return new HttpResponse("", { status: 200, headers: { "Content-Type": "text/plain" } });
   }),

   // VPMTestComponent — run VPM test query
   http.post("*/api/data/vpm/test", () => {
      return HttpResponse.json({ rows: [] });
   }),
   http.post("*/api/data/vpm/columns/*", () => {
      return HttpResponse.json([]);
   }),
   http.get("*/api/data/vpm/physicalModel/tables", () => {
      return HttpResponse.json([]);
   }),
   http.post("*/api/data/vpm/hiddenColumn/tree", () => {
      return HttpResponse.json([]);
   }),
   http.get("*/api/data/vpm/hiddenColumn/fullTree/*", () => {
      return HttpResponse.json({ nodes: [], timeOut: false });
   }),

   // AutoDrillDialog — viewsheet variable names for a given asset link
   http.get("*/api/data/logicalModel/vs/autoDrill-parameters", () => {
      return HttpResponse.json([]);
   }),

   // AutoDrillDialog — worksheet parameters for drill-through
   http.get("*/api/portal/data/autodrill/worksheet/params", () => {
      return HttpResponse.json([]);
   }),

   // AutoDrillDialog — available worksheet column fields (response is string[])
   http.get("*/api/portal/data/autodrill/worksheet/fields", () => {
      return HttpResponse.json([]);
   }),

   // ─── 5. Query builder ────────────────────────────────────────────────────

   // DatabaseQueryComponent — load SQL query model
   http.get("*/api/data/datasource/query/query-model", () => {
      return HttpResponse.json({
         name: "",
         tables: [],
         columns: [],
         conditions: [],
         groupBy: [],
         orderBy: [],
         maxRows: 0,
         sqlEdited: false,
         freeFormSQL: false,
      });
   }),

   // DatabaseQueryComponent — save/update query
   http.post("*/api/data/datasource/query/update", () => {
      return new HttpResponse(null, { status: 200 });
   }),

   // DataQueryModelService — collect query variables (before running query)
   http.get("*/api/data/datasource/query/variables", () => {
      return HttpResponse.json({ variables: [] });
   }),

   // DataQueryModelService — update collected query variables
   http.post("*/api/data/datasource/query/variables/update", () => {
      return new HttpResponse(null, { status: 200 });
   }),

   // DataQueryModelService — parse free-form SQL
   http.post("*/api/data/datasource/query/save/freeSQLModel", () => {
      return HttpResponse.json({ model: null });
   }),

   // QueryLinkGraphPaneComponent — query link graph canvas model
   http.get("*/api/data/datasource/query/graph", () => {
      return HttpResponse.json({ nodes: [], joins: [] });
   }),

   // QueryLinkPaneComponent — data-source tree for the query link pane
   http.post("*/api/data/datasource/query/data-source-tree", () => {
      return HttpResponse.json({ nodes: [] });
   }),

   // QueryFieldsPaneComponent / EditFieldDialogComponent — available fields tree
   http.get("*/api/data/datasource/query/data-source-fields-tree", () => {
      return HttpResponse.json({ nodes: [] });
   }),

   // FieldsPaneComponent — sort-pane fields tree
   http.get("*/api/data/datasource/query/column/sort/fields-tree", () => {
      return HttpResponse.json({ nodes: [] });
   }),

   // QueryLinkGraphPaneComponent — close join edit pane
   http.post("*/api/data/datasource/query/join-edit/close", () => {
      return new HttpResponse(null, { status: 200 });
   }),

   // QueryJoinEditPaneComponent — heartbeat to keep query session alive
   http.get("*/api/data/query/heartbeat", () => {
      return new HttpResponse(null, { status: 200 });
   }),

   // SqlQueryPreviewPaneComponent — load preview data
   http.get("*/api/data/datasource/query/load/data", () => {
      return HttpResponse.json({ headers: [], data: [] });
   }),

   // FreeFormSqlPaneComponent — get column info from SQL parse
   http.post("*/api/data/datasource/query/get/columnInfo", () => {
      return HttpResponse.json({ columns: [] });
   }),

   // FreeFormSqlPaneComponent — clear column info session
   http.get("*/api/data/datasource/query/clear/columnInfo", () => {
      return new HttpResponse(null, { status: 200 });
   }),

   // QueryTablePropertiesDialogComponent — check table alias uniqueness
   http.get("*/api/data/datasource/query/table/alias/check", () => {
      return HttpResponse.json({ duplicate: false });
   }),

   // EditFieldDialogComponent — check expression syntax
   http.get("*/api/data/datasource/query/expression/check", () => {
      return HttpResponse.json({ valid: true, message: "" });
   }),

   // FieldsPaneComponent — group-by validation
   http.post("*/api/data/datasource/query/groupby/check", () => {
      return HttpResponse.json({ valid: true });
   }),

   // QueryFieldsPaneComponent — get sample format string for a field
   http.post("*/api/data/datasource/query/field/format", () => {
      return HttpResponse.json("");
   }),

   // QueryFieldsPaneComponent — add columns to the query
   http.post("*/api/data/datasource/query/column/add", () => {
      return HttpResponse.json({ limitMessage: null, columnMap: {} });
   }),

   // QueryFieldsPaneComponent — remove columns from the query
   http.post("*/api/data/datasource/query/column/remove", () => {
      return new HttpResponse(null, { status: 200 });
   }),

   // QueryFieldsPaneComponent — update a column's properties (alias, dataType, format, drill)
   http.post("*/api/data/datasource/query/column/update", () => {
      return new HttpResponse(null, { status: 200 });
   }),

   // QueryFieldsPaneComponent — browse column distinct values
   http.get("*/api/data/datasource/query/column/browse", () => {
      return HttpResponse.json([]);
   }),

   // QueryFieldsPaneComponent — save an expression column
   http.post("*/api/data/datasource/query/expression/save", () => {
      return HttpResponse.json(null);
   }),

   // SimpleScheduleDialog.ok() — email address validation before submit
   http.get("*/api/vs/check-email-valid", () => {
      return HttpResponse.json({
         messageCommand: { type: "OK", message: "", events: null },
         addressHistory: [],
      });
   }),

   // EmbeddedEmailPane constructor — fetch users node tree for identity selection
   http.get("*/api/vs/expand-identity-node", () => {
      return HttpResponse.json([]);
   }),

   // QueryNetworkGraphPaneComponent — open join edit pane (returns new runtimeId)
   http.get("*/api/data/datasource/query/join-edit/open/*", () => {
      return HttpResponse.json("rt-join-edit");
   }),

   // QueryNetworkGraphPaneComponent — add tables to the query graph
   http.post("*/api/data/datasource/query/table/add", () => {
      return new HttpResponse(null, { status: 200 });
   }),

   // QueryNetworkGraphPaneComponent — remove tables from the query graph
   http.post("*/api/data/datasource/query/table/remove", () => {
      return new HttpResponse(null, { status: 200 });
   }),

   // QueryNetworkGraphPaneComponent — move table position
   http.put("*/api/data/datasource/query/table/move", () => {
      return new HttpResponse(null, { status: 200 });
   }),

   // QueryNetworkGraphPaneComponent — clear all tables from graph (DELETE)
   http.delete("*/api/data/datasource/query/table/*", () => {
      return new HttpResponse(null, { status: 200 });
   }),

   // QueryNetworkGraphPaneComponent — clear all joins from graph (DELETE)
   http.delete("*/api/data/datasource/query/joins/*", () => {
      return new HttpResponse(null, { status: 200 });
   }),

   // QueryNetworkGraphPaneComponent — delete selected join condition
   http.post("*/api/data/datasource/query/joins/delete", () => {
      return new HttpResponse(null, { status: 200 });
   }),
];
