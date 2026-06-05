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
 * DataSourcesTreeViewComponent — Angular Testing Library style
 *
 * Risk-first coverage:
 *   Group 1 [Risk 3]  — changeDataSourcesTree: always returns false — ancestor nodes at depth ≥ 2
 *                       never receive expanded=true (it.fails — confirmed bug)
 *   Group 2 [Risk 3]  — selectNode: type-dispatch routes to the correct Angular route
 *   Group 3 [Risk 3]  — getDataNavigationTree: loading flag lifecycle + HTTP response wiring (including error path)
 *   Group 4 [Risk 2]  — hasMenuFunction: permission-gated menu display for model/VPM node types
 *   Group 5 [Risk 2]  — isVpmVisible: enterprise gate + ^SELF identifier guard
 *   Group 6 [Risk 2]  — canCreateChildren / canRename / canDelete: property-based permission guards
 *   Group 7 [Risk 2]  — getAssetIcon: CSS class dispatch per node type
 *   Group 8 [Risk 2]  — getNodePath / splitModelName: path parsing boundary cases
 *
 * Confirmed bugs (it.fails — remove wrapper once fixed Issue #75149):
 *
 *   Bug A — changeDataSourcesTree-always-returns-false (Group 1):
 *     The method ends with `return false` instead of `return found`. Recursive calls use
 *     `found = this.changeDataSourcesTree(...) || found`, which resolves to `found = false || found`.
 *     Ancestor nodes at depth ≥ 2 never receive `expanded = true`, so navigating to a
 *     deeply nested node leaves intermediate folders visually collapsed.
 *
 * KEY contracts:
 *   selectNode: guarded by gettingStartedService.isProcessing() && isEditWs() — early return before routing.
 *   hasMenuFunction: for PARTITION/LOGIC_MODEL/VPM/EXTENDED_* requires at least one action property === "true".
 *   isVpmVisible: requires enterprise=true AND identifier must NOT end with "^SELF".
 *   getNodePath: "User Worksheet" → "/" only for PRIVATE_WORKSHEETS_FOLDER type.
 *   splitModelName: path with no "/" returns null (not an empty result).
 */

import { type Mock } from "vitest";import { provideHttpClient } from "@angular/common/http";
import { NO_ERRORS_SCHEMA } from "@angular/core";
import { ActivatedRoute, Router } from "@angular/router";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { render, waitFor } from "@testing-library/angular";
import { http, HttpResponse as MswHttpResponse } from "msw";
import { EMPTY, of } from "rxjs";
import { StompClientService } from "../../../common/viewsheet-client";
import { RepositoryClientService } from "../../../common/repository-client/repository-client.service";
import { DebounceService } from "../../../widget/services/debounce.service";
import { DragService } from "../../../widget/services/drag.service";
import { FixedDropdownService } from "../../../widget/fixed-dropdown/fixed-dropdown.service";
import { DomService } from "../../../widget/dom-service/dom.service";
import { DatasourceBrowserService } from "../data-datasource-browser/datasource-browser.service";
import { DataBrowserService } from "../data-folder-browser/data-browser.service";
import {
   DataModelBrowserService
} from "../data-datasource-browser/datasources-database/database-data-model-browser/data-model-browser.service";
import { DataModelNameChangeService } from "../services/data-model-name-change.service";
import { OpenComposerService } from "../../../common/services/open-composer.service";
import { DataSourcesTreeActionsService } from "./data-sources-tree-actions.service";
import { GettingStartedService } from "../../../widget/dialog/getting-started-dialog/service/getting-started.service";
import { AppInfoService } from "../../../../../../shared/util/app-info.service";
import { TreeNodeModel } from "../../../widget/tree/tree-node-model";
import { DatasourceTreeAction } from "../model/datasources/database/datasource-tree-action";
import { DataSourcesTreeViewComponent } from "./data-sources-tree-view.component";
import { PortalDataType } from "./portal-data-type";
import { server } from "@test-mocks/server";

// ---------------------------------------------------------------------------
// Shared helpers
// ---------------------------------------------------------------------------

/** Minimal root tree used to satisfy ngOnInit's getDataNavigationTree() HTTP call. */
const MINIMAL_ROOT: TreeNodeModel = {
   label: "root",
   data: { name: "root", path: "/" } as any,
   children: [
      {
         label: "Data",
         data: { name: "data_home", path: "/", scope: 1 } as any,
         type: PortalDataType.SHARED_WORKSHEETS_FOLDER,
         children: [],
         expanded: true,
         leaf: false
      } as TreeNodeModel
   ],
   expanded: true,
   leaf: false
} as TreeNodeModel;

function makeMockTree() {
   return {
      isSelectedNode: vi.fn(() => false),
      selectedNodes: [] as TreeNodeModel[],
      selectAndExpandToNode: vi.fn(),
      expandToNode: vi.fn(),
      exclusiveSelectNode: vi.fn()
   };
}

function makeNode(type: string, path: string, overrides: Partial<TreeNodeModel> = {}): TreeNodeModel {
   return {
      label: type,
      data: { path, scope: 0, type, identifier: `0^${type}^admin^${path}` } as any,
      type,
      children: [],
      expanded: false,
      leaf: false,
      ...overrides
   } as any;
}

async function renderComponent(opts: { enterprise?: boolean; routerUrl?: string } = {}) {
   const { enterprise = false, routerUrl = "/portal/tab/data" } = opts;

   const mockRouter = {
      url: routerUrl,
      navigate: vi.fn().mockResolvedValue(true),
      events: EMPTY
   };

   // Provide the default tree response for ngOnInit's HTTP call.
   server.use(
      http.get("*/api/portal/data/tree", () => MswHttpResponse.json(MINIMAL_ROOT))
   );

   const { fixture } = await render(DataSourcesTreeViewComponent, {
      providers: [
         provideHttpClient(),
         // Mock StompClientService so component-scoped ViewsheetClientService and
         // AssetClientService never attempt a real WebSocket handshake.
         {
            provide: StompClientService,
            useValue: {
               connect: vi.fn(() => EMPTY),
               whenDisconnected: vi.fn(() => EMPTY),
               reconnectError: vi.fn(() => EMPTY)
            }
         },
         { provide: Router, useValue: mockRouter },
         { provide: ActivatedRoute, useValue: { queryParamMap: EMPTY, snapshot: { _routerState: null } } },
         { provide: NgbModal, useValue: { open: vi.fn() } },
         { provide: DataBrowserService, useValue: {
            mvChanged: EMPTY,
            folderChanged: EMPTY,
            openWorksheet: vi.fn(),
            changeFolder: vi.fn()
         }},
         { provide: DatasourceBrowserService, useValue: {
            datasourceChanged: EMPTY,
            folderChanged: EMPTY,
            onCreateEvent: EMPTY,
            createDataSourceInfos: vi.fn(() => []),
            moveDataSourcesToFolder: vi.fn(),
            refreshTree: vi.fn()
         }},
         { provide: DataModelBrowserService, useValue: {
            addVPM: vi.fn(), addPhysicalView: vi.fn(), addLogicalModel: vi.fn(),
            addDataModelFolder: vi.fn(), renameDataModelFolder: vi.fn(),
            deleteDataModelFolder: vi.fn(), moveModelsToTarget: vi.fn(), emitChanged: vi.fn()
         }},
         { provide: RepositoryClientService, useValue: { connect: vi.fn(), dataChanged: EMPTY } },
         { provide: DebounceService, useValue: { debounce: vi.fn() } },
         { provide: DragService, useValue: { getDragData: vi.fn(() => ({})) } },
         { provide: FixedDropdownService, useValue: {
            open: vi.fn(() => ({ componentInstance: { sourceEvent: null, actions: null } }))
         }},
         { provide: DataModelNameChangeService, useValue: {} },
         { provide: OpenComposerService, useValue: { composerOpen: EMPTY } },
         { provide: DomService, useValue: {} },
         { provide: DataSourcesTreeActionsService, useValue: {
            addDataSourceFolder: vi.fn(), addDataWorksheetFolder: vi.fn(),
            addDataSource: vi.fn(), addDataWorksheet: vi.fn(),
            deleteDataSourceFolder: vi.fn(), deleteWorksheetFolder: vi.fn(),
            deleteDataSource: vi.fn(), renameDataSourceFolder: vi.fn(),
            renameWorksheetFolder: vi.fn(), showWSFolderDetails: vi.fn()
         }},
         { provide: GettingStartedService, useValue: {
            isProcessing: vi.fn(() => false),
            isEditWs: vi.fn(() => false)
         }},
         { provide: AppInfoService, useValue: { isEnterprise: vi.fn(() => of(enterprise)) } },
      ],
      schemas: [NO_ERRORS_SCHEMA]
   });

   const comp = fixture.componentInstance;
   comp.tree = makeMockTree() as any;

   // Wait for the initial getDataNavigationTree() HTTP call (from ngOnInit) to complete.
   // rootNode starts as undefined; toBeTruthy() waits until the HTTP response sets it.
   // This ensures no stale pending subscription from this component's ngOnInit leaks
   // into the test body and triggers updateSelectedNodes(undefined, ...) crashes.
   await waitFor(() => expect(comp.rootNode).toBeTruthy());

   return { comp, mockRouter, fixture };
}

afterEach(() => {
   vi.restoreAllMocks();
});

// ---------------------------------------------------------------------------
// Group 1 — changeDataSourcesTree: always returns false [Risk 3] (confirmed bug)
// ---------------------------------------------------------------------------

describe("DataSourcesTreeViewComponent — changeDataSourcesTree — ancestor expansion at depth ≥ 2 [Group 1, Risk 3]", () => {

   // 🔁 Regression-sensitive: return false instead of found breaks the recursive expansion chain;
   //    grandparent and above stay collapsed even though a descendant node was found and selected.
   // Bug confirmed: method ends with `return false` unconditionally — caller receives false regardless
   //   of whether a node was found, so `found = this.changeDataSourcesTree(...) || found`
   //   can never set found=true from recursion. Fix: change `return false` to `return found`.
   it.fails("should expand all intermediate ancestors when a node is found at depth 3", async () => {
      const { comp } = await renderComponent();

      // Build a 3-level hierarchy: rootNode → dataSourceRoot(path="/") → dbNode(path="mydb") → partition
      const partition = makeNode(PortalDataType.PARTITION, "mydb/mypartition", {
         data: { path: "mydb/mypartition", scope: 0, properties: {} } as any
      });
      const dbNode = makeNode(PortalDataType.DATABASE, "mydb", {
         children: [partition],
         data: { path: "mydb", scope: 0 } as any
      });
      const dataSourceRoot = makeNode(PortalDataType.DATA_SOURCE_ROOT_FOLDER, "/", {
         children: [dbNode],
         data: { path: "/", scope: 0 } as any
      });

      comp.rootNode = { label: "root", data: { path: "/", scope: 0 } as any, type: "root",
         children: [dataSourceRoot], expanded: false, leaf: false } as any;

      comp.changeDataSourcesTree("mydb/mypartition", "0", PortalDataType.DATA_SOURCE_ROOT_FOLDER);

      // Verify the node was actually found (confirms our setup is correct).
      expect(comp.selectedNodes).toEqual([partition]);
      // dbNode IS expanded (it's the direct parent — the immediate-parent logic works).
      expect(dbNode.expanded).toBe(true);
      // Bug: dataSourceRoot is NOT expanded because the recursive call to the dbNode level returns
      //      `false` (always), so `found = false || false` in the dataSourceRoot iteration stays false,
      //      and `parent.expanded = true` is never reached for dataSourceRoot.
      expect(dataSourceRoot.expanded).toBe(true);  // FAILS due to the bug
   });
});

// ---------------------------------------------------------------------------
// Group 2 — selectNode: type-dispatch routing [Risk 3]
// ---------------------------------------------------------------------------

describe("DataSourcesTreeViewComponent — selectNode — route dispatch [Group 2, Risk 3]", () => {

   // 🔁 Regression-sensitive: DATA_SOURCE_ROOT_FOLDER must navigate to the datasources list;
   //    wrong route leaves user unable to see their data sources.
   it("should navigate to /portal/tab/data/datasources when a DATA_SOURCE_ROOT_FOLDER node is selected", async () => {
      const { comp, mockRouter } = await renderComponent();
      const node = makeNode(PortalDataType.DATA_SOURCE_ROOT_FOLDER, "/", {
         data: { path: "/", scope: 0, type: PortalDataType.DATA_SOURCE_ROOT_FOLDER } as any
      });

      comp.selectNode([node]);

      expect(mockRouter.navigate).toHaveBeenCalledWith(
         ["/portal/tab/data/datasources"],
         expect.objectContaining({
            queryParams: expect.objectContaining({ path: "/", scope: "0" })
         })
      );
   });

   // 🔁 Regression-sensitive: DATABASE type must navigate to the database editor page;
   //    wrong encoding of the path would silently open the wrong database.
   it("should navigate to /portal/tab/data/datasources/database/<encoded> when a DATABASE node is selected", async () => {
      const { comp, mockRouter } = await renderComponent();
      const node = makeNode(PortalDataType.DATABASE, "mydb", {
         data: { path: "mydb", scope: 0 } as any
      });

      comp.selectNode([node]);

      expect(mockRouter.navigate).toHaveBeenCalledWith(
         ["/portal/tab/data/datasources/database", encodeURIComponent("mydb")],
         expect.objectContaining({ relativeTo: expect.anything() })
      );
   });

   // 🔁 Regression-sensitive: multi-select must NOT trigger navigation — routing on multi-select
   //    would confuse the user by navigating away while they are making a selection.
   it("should NOT call router.navigate when more than one node is selected", async () => {
      const { comp, mockRouter } = await renderComponent();
      const nodeA = makeNode(PortalDataType.DATA_SOURCE_ROOT_FOLDER, "/");
      const nodeB = makeNode(PortalDataType.DATA_SOURCE_FOLDER, "/ds");

      comp.selectNode([nodeA, nodeB]);

      expect(mockRouter.navigate).not.toHaveBeenCalled();
      expect(comp.selectedNodes).toEqual([nodeA, nodeB]);
   });

   // Risk Point/Contract: gettingStartedService gate must fire before routing — skipping it
   //   causes the editor to navigate away mid-wizard, corrupting the getting-started flow.
   it("should return early without routing when gettingStartedService is processing and isEditWs", async () => {
      const { comp, mockRouter, fixture } = await renderComponent();
      // Override the getting-started service mock on this instance
      const gs = fixture.debugElement.injector.get(GettingStartedService);
      (gs.isProcessing as Mock).mockReturnValue(true);
      (gs.isEditWs as Mock).mockReturnValue(true);

      const node = makeNode(PortalDataType.DATA_SOURCE_ROOT_FOLDER, "/", {
         data: { path: "/", scope: 0, type: PortalDataType.DATA_SOURCE_ROOT_FOLDER } as any
      });

      comp.selectNode([node]);

      expect(mockRouter.navigate).not.toHaveBeenCalled();
   });
});

// ---------------------------------------------------------------------------
// Group 3 — getDataNavigationTree: loading state + HTTP response [Risk 3]
// ---------------------------------------------------------------------------

describe("DataSourcesTreeViewComponent — getDataNavigationTree — loading + response wiring [Group 3, Risk 3]", () => {

   // 🔁 Regression-sensitive: loading=true must be set synchronously before HTTP completes;
   //    missing this causes the spinner to never appear on slow networks.
   it("should set loading=true synchronously and loading=false after the HTTP response", async () => {
      const { comp } = await renderComponent();

      server.use(
         http.get("*/api/portal/data/tree", () => MswHttpResponse.json(MINIMAL_ROOT))
      );

      comp.getDataNavigationTree();
      expect(comp.loading).toBe(true);  // synchronous — set before HTTP response

      await waitFor(() => expect(comp.loading).toBe(false));
   });

   // 🔁 Regression-sensitive: rootNode must be replaced with the server response;
   //    stale rootNode would show outdated tree structure after a navigation-triggered refresh.
   it("should populate rootNode from the HTTP response", async () => {
      const { comp } = await renderComponent();

      server.use(
         http.get("*/api/portal/data/tree", () =>
            MswHttpResponse.json({ ...MINIMAL_ROOT, label: "fresh-root" })
         )
      );

      comp.getDataNavigationTree();

      await waitFor(() => expect(comp.rootNode?.label).toBe("fresh-root"));
      expect(comp.loading).toBe(false);
   });

   // 🔁 Regression-sensitive: subscribe() error callback must reset loading; without it the
   //    spinner runs indefinitely after a failed tree fetch.
   it("should reset loading to false when the HTTP request errors", async () => {
      const { comp } = await renderComponent();

      server.use(
         http.get("*/api/portal/data/tree", () => new MswHttpResponse(null, { status: 500 }))
      );

      comp.getDataNavigationTree();
      expect(comp.loading).toBe(true); // synchronous — set before HTTP response

      await waitFor(() => expect(comp.loading).toBe(false));
   });

   // Risk Point/Contract: selectedNodes must be carried over to matching nodes in the new tree;
   //   losing selection on refresh breaks the "stay on current page" UX contract.
   it("should keep selectedNodes pointing to the refreshed equivalents after a tree reload", async () => {
      const { comp } = await renderComponent();

      const IDENTIFIER = "0^DS^admin^/";
      const oldChild: TreeNodeModel = makeNode(PortalDataType.DATA_SOURCE_ROOT_FOLDER, "/", {
         label: "DS",
         data: { path: "/", scope: 0, identifier: IDENTIFIER, type: PortalDataType.DATA_SOURCE_ROOT_FOLDER } as any
      });
      comp.rootNode = { ...MINIMAL_ROOT, children: [oldChild] } as any;
      comp.selectedNodes = [oldChild];
      // Use identifier-based comparison: updateSelectedNodes operates on _oldRootNode = Tool.clone(rootNode),
      // so the children passed to isSelectedNode are deep clones — reference equality always fails on them.
      (comp.tree as any).isSelectedNode = vi.fn((n: TreeNodeModel) =>
         n?.data?.identifier === IDENTIFIER
      );

      const newChild: TreeNodeModel = makeNode(PortalDataType.DATA_SOURCE_ROOT_FOLDER, "/", {
         label: "DS",
         data: { path: "/", scope: 0, identifier: IDENTIFIER, type: PortalDataType.DATA_SOURCE_ROOT_FOLDER } as any
      });

      server.use(
         http.get("*/api/portal/data/tree", () =>
            MswHttpResponse.json({ ...MINIMAL_ROOT, children: [newChild] })
         )
      );

      comp.getDataNavigationTree();

      // Wait for the reload to complete: loading goes true→false only after the HTTP response is processed.
      await waitFor(() => expect(comp.loading).toBe(false));
      // selectedNodes must now point to the new tree's equivalent node, not the stale old reference.
      // Reference equality is impossible after JSON round-trip (MSW → HttpClient deserializes to a new object),
      // so verify by identifier — the same field updateSelectedNodes uses for matching.
      expect(comp.selectedNodes).not.toContain(oldChild);
      expect(comp.selectedNodes).toHaveLength(1);
      expect(comp.selectedNodes[0].data.identifier).toBe(IDENTIFIER);
   });
});

// ---------------------------------------------------------------------------
// Group 4 — hasMenuFunction: permission-gated menu display [Risk 2]
// ---------------------------------------------------------------------------

describe("DataSourcesTreeViewComponent — hasMenuFunction — permission gating [Group 4, Risk 2]", () => {

   // 🔁 Regression-sensitive: model nodes (VPM/PARTITION/LOGIC_MODEL) must NOT show context menu
   //    unless at least one action property is "true" — showing an empty/disabled menu confuses users.
   it("should return false for a VPM node when no action property is 'true'", async () => {
      const { comp } = await renderComponent();
      const node = makeNode(PortalDataType.VPM, "mydb/myvpm", {
         data: { path: "mydb/myvpm", scope: 0,
            properties: {
               [DatasourceTreeAction.RENAME]: "false",
               [DatasourceTreeAction.DELETE]: "false",
               [DatasourceTreeAction.CREATE_CHILDREN]: "false"
            }
         } as any
      });

      expect(comp.hasMenuFunction(node)).toBe(false);
   });

   // Why High Value: VPM with RENAME permission granted must show the menu (rename action is available)
   it("should return true for a VPM node when RENAME property is 'true'", async () => {
      const { comp } = await renderComponent();
      const node = makeNode(PortalDataType.VPM, "mydb/myvpm", {
         data: { path: "mydb/myvpm", scope: 0,
            properties: { [DatasourceTreeAction.RENAME]: "true" }
         } as any
      });

      expect(comp.hasMenuFunction(node)).toBe(true);
   });

   // Risk Point/Contract: FOLDER-type nodes always have a context menu (create/rename/delete)
   it("should return true for a FOLDER node regardless of properties", async () => {
      const { comp } = await renderComponent();
      const node = makeNode(PortalDataType.FOLDER, "myfolder");

      expect(comp.hasMenuFunction(node)).toBe(true);
   });

   // Risk Point/Contract: DATA_SOURCE type is not in the restricted list — always shows menu
   it("should return true for a DATA_SOURCE node", async () => {
      const { comp } = await renderComponent();
      const node = makeNode(PortalDataType.DATA_SOURCE, "myds");

      expect(comp.hasMenuFunction(node)).toBe(true);
   });
});

// ---------------------------------------------------------------------------
// Group 5 — isVpmVisible: enterprise gate + ^SELF guard [Risk 2]
// ---------------------------------------------------------------------------

describe("DataSourcesTreeViewComponent — isVpmVisible — enterprise + identifier guard [Group 5, Risk 2]", () => {

   // 🔁 Regression-sensitive: ^SELF nodes are community data sources where VPMs are not applicable;
   //    showing the VPM menu item there misleads users and leads to confusing errors.
   it("should return false for a DATABASE node whose identifier ends with '^SELF'", async () => {
      const { comp } = await renderComponent({ enterprise: true });
      const node = makeNode(PortalDataType.DATABASE, "mydb", {
         data: { path: "mydb", scope: 0, identifier: "0^DATABASE^admin^mydb^SELF" } as any
      });

      expect((comp as any).isVpmVisible(node)).toBe(false);
   });

   // Risk Point/Contract: enterprise=false disables VPM entirely regardless of node type
   it("should return false when enterprise is false even for a DATABASE node", async () => {
      const { comp } = await renderComponent({ enterprise: false });
      const node = makeNode(PortalDataType.DATABASE, "mydb");

      expect((comp as any).isVpmVisible(node)).toBe(false);
   });

   // Why High Value: enterprise=true + DATABASE without ^SELF is the canonical VPM creation path
   it("should return true for DATABASE node with enterprise=true and no ^SELF suffix", async () => {
      const { comp } = await renderComponent({ enterprise: true });
      // No manual override needed: AppInfoService.isEnterprise() returns of(true), which emits
      // synchronously in ngOnInit before renderComponent's waitFor resolves.
      const node = makeNode(PortalDataType.DATABASE, "mydb", {
         data: { path: "mydb", scope: 0, identifier: "0^DATABASE^admin^mydb" } as any
      });

      expect((comp as any).isVpmVisible(node)).toBe(true);
   });

   // Risk Point/Contract: VPM_FOLDER is the folder under a database for existing VPMs;
   //   enterprise must be able to create new VPMs there too.
   it("should return true for VPM_FOLDER node with enterprise=true", async () => {
      const { comp } = await renderComponent({ enterprise: true });
      const node = makeNode(PortalDataType.VPM_FOLDER, "mydb/VPMs", {
         data: { path: "mydb/VPMs", scope: 0, identifier: "0^VPM_FOLDER^admin^mydb/VPMs" } as any
      });

      expect((comp as any).isVpmVisible(node)).toBe(true);
   });
});

// ---------------------------------------------------------------------------
// Group 6 — canCreateChildren / canRename / canDelete permission guards [Risk 2]
// ---------------------------------------------------------------------------

describe("DataSourcesTreeViewComponent — permission guards — property-based access control [Group 6, Risk 2]", () => {

   // 🔁 Regression-sensitive: canCreateChildren gates context menu enabled state;
   //    returning true when property is missing would let users attempt to create items without permission.
   it("should return false from canCreateChildren when node has no data properties", async () => {
      const { comp } = await renderComponent();
      const node = makeNode(PortalDataType.DATABASE, "mydb", { data: {} as any });

      expect(comp.canCreateChildren(node)).toBe(false);
   });

   it("should return true from canCreateChildren when CREATE_CHILDREN property is 'true'", async () => {
      const { comp } = await renderComponent();
      const node = makeNode(PortalDataType.DATABASE, "mydb", {
         data: { properties: { [DatasourceTreeAction.CREATE_CHILDREN]: "true" } } as any
      });

      expect(comp.canCreateChildren(node)).toBe(true);
   });

   // Risk Point/Contract: RENAME="false" string must compare as false — not falsy coercion
   it("should return false from canRename when RENAME property is 'false'", async () => {
      const { comp } = await renderComponent();
      const node = makeNode(PortalDataType.FOLDER, "myfolder", {
         data: { properties: { [DatasourceTreeAction.RENAME]: "false" } } as any
      });

      expect(comp.canRename(node)).toBe(false);
   });

   it("should return true from canDelete when DELETE property is 'true'", async () => {
      const { comp } = await renderComponent();
      const node = makeNode(PortalDataType.DATA_SOURCE_FOLDER, "/ds", {
         data: { properties: { [DatasourceTreeAction.DELETE]: "true" } } as any
      });

      expect(comp.canDelete(node)).toBe(true);
   });
});

// ---------------------------------------------------------------------------
// Group 7 — getAssetIcon: CSS class dispatch [Risk 2]
// ---------------------------------------------------------------------------

describe("DataSourcesTreeViewComponent — getAssetIcon — CSS icon class per type [Group 7, Risk 2]", () => {

   // 🔁 Regression-sensitive: icon classes are referenced in CSS and templates; a wrong mapping
   //    causes the wrong icon to appear in the tree — visible regression across every tree node.
   it("should return 'database-icon' for DATABASE type", async () => {
      const { comp } = await renderComponent();
      expect(comp.getAssetIcon(PortalDataType.DATABASE)).toBe("database-icon");
   });

   it("should return 'folder-icon' for DATA_SOURCE_FOLDER type", async () => {
      const { comp } = await renderComponent();
      expect(comp.getAssetIcon(PortalDataType.DATA_SOURCE_FOLDER)).toBe("folder-icon");
   });

   it("should return 'vpm-icon' for VPM type", async () => {
      const { comp } = await renderComponent();
      expect(comp.getAssetIcon(PortalDataType.VPM)).toBe("vpm-icon");
   });

   // Risk Point/Contract: materialized flag must override the default worksheet icon;
   //   showing wrong icon prevents users from identifying which worksheets are materialized.
   it("should return 'materialized-worksheet-icon' when materialized=true and type is not a special type", async () => {
      const { comp } = await renderComponent();
      // any unrecognized type with materialized=true should use the materialized icon
      expect(comp.getAssetIcon("WORKSHEET", true)).toBe("materialized-worksheet-icon");
   });
});

// ---------------------------------------------------------------------------
// Group 8 — getNodePath / splitModelName: path parsing boundary cases [Risk 2]
// ---------------------------------------------------------------------------

describe("DataSourcesTreeViewComponent — getNodePath / splitModelName — path parsing [Group 8, Risk 2]", () => {

   // 🔁 Regression-sensitive: "User Worksheet" root maps to "/" for private worksheets;
   //    returning the raw string would produce an invalid double-prefix path.
   it("getNodePath: should return '/' when type is PRIVATE_WORKSHEETS_FOLDER and path is 'User Worksheet'", async () => {
      const { comp } = await renderComponent();
      expect(comp.getNodePath(PortalDataType.PRIVATE_WORKSHEETS_FOLDER, "User Worksheet")).toBe("/");
   });

   // Risk Point/Contract: "User Worksheet/<sub>" prefix must be stripped regardless of node type
   it("getNodePath: should strip 'User Worksheet/' prefix when path starts with it", async () => {
      const { comp } = await renderComponent();
      expect(comp.getNodePath(PortalDataType.FOLDER, "User Worksheet/myws")).toBe("myws");
   });

   // 🔁 Regression-sensitive: splitModelName drives database/physicalModel navigation routes;
   //    wrong database or name causes an HTTP 404 or opens the wrong model editor.
   it("splitModelName: should split 'db/model' into database='db' and name='model'", async () => {
      const { comp } = await renderComponent();
      const node = makeNode(PortalDataType.PARTITION, "db/model", {
         data: { path: "db/model", scope: 0, properties: { folder: null } } as any
      });

      const result = (comp as any).splitModelName(node);

      expect(result).not.toBeNull();
      expect(result.database).toBe("db");
      expect(result.folder).toBeNull();
      expect(result.name).toBe("model");
   });

   // Risk Point/Contract: a path with no '/' is an invalid model path — must return null,
   //   not an object with empty strings, so callers can guard the null and skip navigation.
   it("splitModelName: should return null when path contains no '/' separator", async () => {
      const { comp } = await renderComponent();
      const node = makeNode(PortalDataType.PARTITION, "noSlashPath", {
         data: { path: "noSlashPath", scope: 0, properties: {} } as any
      });

      const result = (comp as any).splitModelName(node);

      expect(result).toBeNull();
   });
});
