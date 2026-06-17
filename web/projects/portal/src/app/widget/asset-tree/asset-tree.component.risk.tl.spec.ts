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
 * AssetTreeComponent — Pass 2: Risk
 *
 * Risk-first coverage:
 *   Group 1  [Risk 3] — loadAssetTree basic: getAssetTreeNode called; root set to treeNodeModel;
 *                        activeRoot set to root; dataSourcesTree populated if datasources=true
 *   Group 2  [Risk 3] — loadAssetTree with dataSourcePath: event.getPath() returns the path segments
 *   Group 3  [Risk 3] — loadAssetTree with defaultFolder: event.getPath() returns the folder path segments
 *   Group 4  [Risk 3] — addDeleteDataSources with root: calls getAssetTreeNode for root refresh;
 *                        calls removeExtraTrees then addExtraTrees on completion
 *   Group 5  [Risk 2] — removeExtraTrees: splices dataSourcesTree out of root.children
 *   Group 6  [Risk 2] — addExtraTrees: prepends dataSourcesTree to root.children
 *   Group 7  [Risk 2] — ngOnDestroy: calls assetClientService.disconnect(); unsubscribes subscriptions
 *   Group 8  [Risk 1] — setupAssetClientService: calls assetClientService.connect();
 *                        subscribes to assetChanged
 */

import { NO_ERRORS_SCHEMA } from "@angular/core";
import { render } from "@testing-library/angular";
import { NEVER, of, Subject } from "rxjs";

import { AssetTreeComponent } from "./asset-tree.component";
import { AssetTreeService } from "./asset-tree.service";
import { AssetClientService } from "../../common/asset-client/asset-client.service";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { DebounceService } from "../services/debounce.service";
import { CurrentUserService } from "../../../../../shared/util/current-user.service";
import { TreeNodeModel } from "../tree/tree-node-model";
import { AssetType } from "../../../../../shared/data/asset-type";
import { LoadAssetTreeNodesEvent } from "./load-asset-tree-nodes-event";

// ---------------------------------------------------------------------------
// Mocks
// ---------------------------------------------------------------------------

const ASSET_TREE_SERVICE_MOCK = { getAssetTreeNode: vi.fn(), setConnectionVariables: vi.fn() };
const MODAL_SERVICE_MOCK = {};
const CURRENT_USER_SERVICE_MOCK = { getPortalCurrentUser: vi.fn() };

let assetChangedSubject: Subject<any>;
let ASSET_CLIENT_SERVICE_MOCK: any;
let DEBOUNCE_SERVICE_MOCK: any;

// ---------------------------------------------------------------------------
// Factories
// ---------------------------------------------------------------------------

function makeNode(identifier: string, label: string, children: TreeNodeModel[] = [], leaf = true, type?: string): TreeNodeModel {
   return {
      label,
      leaf,
      children,
      data: { identifier, type: type ?? "FOLDER", path: "/", scope: 0 } as any,
   };
}

function makeDataSourceNode(): TreeNodeModel {
   return {
      label: "Data Sources",
      leaf: false,
      children: [],
      data: { identifier: "ds-root", type: AssetType.DATA_SOURCE_FOLDER, path: "/" } as any,
   };
}

function makeTreeResponse(rootIdentifier = "root-id", extraChildren: TreeNodeModel[] = []) {
   const dsNode = makeDataSourceNode();
   const rootNode: TreeNodeModel = {
      label: "Root",
      leaf: false,
      children: [dsNode, ...extraChildren],
      data: { identifier: rootIdentifier, type: "FOLDER", path: "/", scope: 0 } as any,
   };
   return of({ treeNodeModel: rootNode, parameters: null });
}

// ---------------------------------------------------------------------------
// Render helper
// ---------------------------------------------------------------------------

async function renderComponent(inputs: Partial<AssetTreeComponent> = {}) {
   assetChangedSubject = new Subject();
   ASSET_CLIENT_SERVICE_MOCK = {
      connect: vi.fn(),
      disconnect: vi.fn(),
      assetChanged: assetChangedSubject,
   };
   DEBOUNCE_SERVICE_MOCK = { debounce: vi.fn() };

   const { fixture } = await render(AssetTreeComponent, {
      schemas: [NO_ERRORS_SCHEMA],
      providers: [
         { provide: AssetTreeService, useValue: ASSET_TREE_SERVICE_MOCK },
         { provide: NgbModal, useValue: MODAL_SERVICE_MOCK },
         { provide: CurrentUserService, useValue: CURRENT_USER_SERVICE_MOCK },
      ],
      componentProviders: [
         { provide: AssetClientService, useValue: ASSET_CLIENT_SERVICE_MOCK },
         { provide: DebounceService, useValue: DEBOUNCE_SERVICE_MOCK },
      ],
      componentInputs: inputs,
   });

   const comp = fixture.componentInstance as AssetTreeComponent;
   return { fixture, comp };
}

// ---------------------------------------------------------------------------
// Resets
// ---------------------------------------------------------------------------

beforeEach(() => {
   ASSET_TREE_SERVICE_MOCK.getAssetTreeNode.mockReset();
   CURRENT_USER_SERVICE_MOCK.getPortalCurrentUser.mockReset();
});

afterEach(() => vi.restoreAllMocks());

// ---------------------------------------------------------------------------
// Group 1: loadAssetTree basic [Risk 3]
// ---------------------------------------------------------------------------

describe("AssetTreeComponent — loadAssetTree (basic)", () => {
   // 🔁 Regression-sensitive: root must be set to treeNodeModel so the tree renders;
   //    not setting it leaves the tree permanently empty.
   it("should call getAssetTreeNode and set root to treeNodeModel", async () => {
      ASSET_TREE_SERVICE_MOCK.getAssetTreeNode.mockReturnValue(makeTreeResponse());
      CURRENT_USER_SERVICE_MOCK.getPortalCurrentUser.mockReturnValue(of({ name: { orgID: "" } }));

      const { comp } = await renderComponent({ datasources: true });

      expect(comp.root).not.toBeNull();
      expect(comp.root.data.identifier).toBe("root-id");
   });

   it("should set activeRoot to root after loading", async () => {
      ASSET_TREE_SERVICE_MOCK.getAssetTreeNode.mockReturnValue(makeTreeResponse());
      CURRENT_USER_SERVICE_MOCK.getPortalCurrentUser.mockReturnValue(of({ name: { orgID: "" } }));

      const { comp } = await renderComponent({ datasources: true });

      expect(comp.activeRoot).toBe(comp.root);
   });

   it("should populate dataSourcesTree when datasources=true", async () => {
      ASSET_TREE_SERVICE_MOCK.getAssetTreeNode.mockReturnValue(makeTreeResponse());
      CURRENT_USER_SERVICE_MOCK.getPortalCurrentUser.mockReturnValue(of({ name: { orgID: "" } }));

      const { comp } = await renderComponent({ datasources: true });

      expect(comp.dataSourcesTree).not.toBeNull();
      expect(comp.dataSourcesTree.data.type).toBe(AssetType.DATA_SOURCE_FOLDER);
   });
});

// ---------------------------------------------------------------------------
// Group 2: loadAssetTree with dataSourcePath [Risk 3]
// ---------------------------------------------------------------------------

describe("AssetTreeComponent — loadAssetTree with dataSourcePath", () => {
   // 🔁 Regression-sensitive: when dataSourcePath is set the event must include the path
   //    segments; otherwise the server loads the wrong subtree.
   it("should pass path segments from dataSourcePath to getAssetTreeNode event", async () => {
      ASSET_TREE_SERVICE_MOCK.getAssetTreeNode.mockReturnValue(makeTreeResponse());
      CURRENT_USER_SERVICE_MOCK.getPortalCurrentUser.mockReturnValue(of({ name: { orgID: "" } }));

      await render(AssetTreeComponent, {
         schemas: [NO_ERRORS_SCHEMA],
         providers: [
            { provide: AssetTreeService, useValue: ASSET_TREE_SERVICE_MOCK },
            { provide: NgbModal, useValue: MODAL_SERVICE_MOCK },
            { provide: CurrentUserService, useValue: CURRENT_USER_SERVICE_MOCK },
         ],
         componentProviders: [
            { provide: AssetClientService, useValue: ASSET_CLIENT_SERVICE_MOCK },
            { provide: DebounceService, useValue: DEBOUNCE_SERVICE_MOCK },
         ],
         componentInputs: { dataSourcePath: "Sales/Regional" },
      });

      const callArg: LoadAssetTreeNodesEvent = ASSET_TREE_SERVICE_MOCK.getAssetTreeNode.mock.calls[0][0];
      expect(callArg.getPath()).toEqual(["Sales", "Regional"]);
   });
});

// ---------------------------------------------------------------------------
// Group 3: loadAssetTree with defaultFolder [Risk 3]
// ---------------------------------------------------------------------------

describe("AssetTreeComponent — loadAssetTree with defaultFolder", () => {
   // 🔁 Regression-sensitive: defaultFolder sets the initial path and scope so the tree opens
   //    at the correct folder; wrong path leaves the user lost.
   it("should pass path segments from defaultFolder.path to getAssetTreeNode event", async () => {
      ASSET_TREE_SERVICE_MOCK.getAssetTreeNode.mockReturnValue(makeTreeResponse());
      CURRENT_USER_SERVICE_MOCK.getPortalCurrentUser.mockReturnValue(of({ name: { orgID: "" } }));

      await render(AssetTreeComponent, {
         schemas: [NO_ERRORS_SCHEMA],
         providers: [
            { provide: AssetTreeService, useValue: ASSET_TREE_SERVICE_MOCK },
            { provide: NgbModal, useValue: MODAL_SERVICE_MOCK },
            { provide: CurrentUserService, useValue: CURRENT_USER_SERVICE_MOCK },
         ],
         componentProviders: [
            { provide: AssetClientService, useValue: ASSET_CLIENT_SERVICE_MOCK },
            { provide: DebounceService, useValue: DEBOUNCE_SERVICE_MOCK },
         ],
         componentInputs: {
            defaultFolder: { path: "Shared Reports/Q1", scope: 1 } as any,
         },
      });

      const callArg: LoadAssetTreeNodesEvent = ASSET_TREE_SERVICE_MOCK.getAssetTreeNode.mock.calls[0][0];
      expect(callArg.getPath()).toEqual(["Shared Reports", "Q1"]);
   });
});

// ---------------------------------------------------------------------------
// Group 4: addDeleteDataSources with root [Risk 3]
// ---------------------------------------------------------------------------

describe("AssetTreeComponent — addDeleteDataSources when root exists", () => {
   // 🔁 Regression-sensitive: addDeleteDataSources must trigger a refresh of the datasource
   //    subtree; not calling getAssetTreeNode means new datasources never appear.
   it("should call getAssetTreeNode when root exists", async () => {
      ASSET_TREE_SERVICE_MOCK.getAssetTreeNode.mockReturnValue(makeTreeResponse());
      CURRENT_USER_SERVICE_MOCK.getPortalCurrentUser.mockReturnValue(NEVER);

      const { comp } = await renderComponent({ datasources: true });
      comp.root = makeNode("root", "Root", [makeDataSourceNode()], false);
      ASSET_TREE_SERVICE_MOCK.getAssetTreeNode.mockReturnValue(makeTreeResponse());

      comp.addDeleteDataSources();

      expect(ASSET_TREE_SERVICE_MOCK.getAssetTreeNode).toHaveBeenCalled();
   });
});

// ---------------------------------------------------------------------------
// Group 5: removeExtraTrees [Risk 2]
// ---------------------------------------------------------------------------

describe("AssetTreeComponent — removeExtraTrees", () => {
   it("should remove the dataSourcesTree from root.children", async () => {
      CURRENT_USER_SERVICE_MOCK.getPortalCurrentUser.mockReturnValue(NEVER);
      ASSET_TREE_SERVICE_MOCK.getAssetTreeNode.mockReturnValue(NEVER);

      const { comp } = await renderComponent({});
      const dsNode = makeDataSourceNode();
      const otherNode = makeNode("other-1", "Other");
      comp.root = makeNode("root", "Root", [dsNode, otherNode], false);
      comp.dataSourcesTree = dsNode;

      (comp as any).removeExtraTrees();

      expect(comp.root.children).not.toContain(dsNode);
      expect(comp.root.children).toContain(otherNode);
   });

   it("should do nothing when root is null", async () => {
      CURRENT_USER_SERVICE_MOCK.getPortalCurrentUser.mockReturnValue(NEVER);
      ASSET_TREE_SERVICE_MOCK.getAssetTreeNode.mockReturnValue(NEVER);

      const { comp } = await renderComponent({});
      comp.root = null;

      expect(() => (comp as any).removeExtraTrees()).not.toThrow();
   });
});

// ---------------------------------------------------------------------------
// Group 6: addExtraTrees [Risk 2]
// ---------------------------------------------------------------------------

describe("AssetTreeComponent — addExtraTrees", () => {
   it("should prepend dataSourcesTree to root.children when it is set", async () => {
      CURRENT_USER_SERVICE_MOCK.getPortalCurrentUser.mockReturnValue(NEVER);
      ASSET_TREE_SERVICE_MOCK.getAssetTreeNode.mockReturnValue(NEVER);

      const { comp } = await renderComponent({});
      const dsNode = makeDataSourceNode();
      const otherNode = makeNode("other-1", "Other");
      comp.root = makeNode("root", "Root", [otherNode], false);
      comp.dataSourcesTree = dsNode;

      (comp as any).addExtraTrees();

      expect(comp.root.children[0]).toBe(dsNode);
   });

   it("should do nothing when root is null", async () => {
      CURRENT_USER_SERVICE_MOCK.getPortalCurrentUser.mockReturnValue(NEVER);
      ASSET_TREE_SERVICE_MOCK.getAssetTreeNode.mockReturnValue(NEVER);

      const { comp } = await renderComponent({});
      comp.root = null;

      expect(() => (comp as any).addExtraTrees()).not.toThrow();
   });
});

// ---------------------------------------------------------------------------
// Group 7: ngOnDestroy [Risk 2]
// ---------------------------------------------------------------------------

describe("AssetTreeComponent — ngOnDestroy", () => {
   it("should call assetClientService.disconnect() on destroy", async () => {
      CURRENT_USER_SERVICE_MOCK.getPortalCurrentUser.mockReturnValue(NEVER);
      ASSET_TREE_SERVICE_MOCK.getAssetTreeNode.mockReturnValue(NEVER);

      const { comp } = await renderComponent({});
      ASSET_CLIENT_SERVICE_MOCK.disconnect.mockReset();

      comp.ngOnDestroy();

      expect(ASSET_CLIENT_SERVICE_MOCK.disconnect).toHaveBeenCalledTimes(1);
   });

   it("should call subscriptions.unsubscribe() on destroy", async () => {
      CURRENT_USER_SERVICE_MOCK.getPortalCurrentUser.mockReturnValue(NEVER);
      ASSET_TREE_SERVICE_MOCK.getAssetTreeNode.mockReturnValue(NEVER);

      const { comp } = await renderComponent({});
      const unsubSpy = vi.spyOn((comp as any).subscriptions, "unsubscribe");

      comp.ngOnDestroy();

      expect(unsubSpy).toHaveBeenCalledTimes(1);
   });
});

// ---------------------------------------------------------------------------
// Group 8: setupAssetClientService [Risk 1]
// ---------------------------------------------------------------------------

describe("AssetTreeComponent — setupAssetClientService", () => {
   it("should call assetClientService.connect() on init", async () => {
      CURRENT_USER_SERVICE_MOCK.getPortalCurrentUser.mockReturnValue(NEVER);
      ASSET_TREE_SERVICE_MOCK.getAssetTreeNode.mockReturnValue(NEVER);

      await renderComponent({});

      expect(ASSET_CLIENT_SERVICE_MOCK.connect).toHaveBeenCalledTimes(1);
   });

   it("should subscribe to assetChanged", async () => {
      CURRENT_USER_SERVICE_MOCK.getPortalCurrentUser.mockReturnValue(NEVER);
      ASSET_TREE_SERVICE_MOCK.getAssetTreeNode.mockReturnValue(NEVER);

      const { comp } = await renderComponent({});

      expect(assetChangedSubject.observed).toBe(true);
   });
});
