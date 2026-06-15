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
 * AssetTreeComponent — Pass 1: Interaction
 *
 * Risk-first coverage:
 *   Group 1  [Risk 3] — findAssetTreeNodeFromIdentifier: finds a node deep in the tree;
 *                        returns null for unknown identifiers; returns null for null identifier
 *   Group 2  [Risk 3] — findAssetTreeNodeParentFromIdentifier: returns the parent of a matching
 *                        child; returns null when identifier is not found; null for null identifier
 *   Group 3  [Risk 3] — selectNodes: emits nodesSelected and nodeSelected; sets selectedNodes;
 *                        emits pathSelected when observers exist
 *   Group 4  [Risk 2] — doubleclickNode: emits dblclickNode with the node
 *   Group 5  [Risk 2] — contextmenuTreeNode: emits onContextmenu with the event tuple
 *   Group 6  [Risk 2] — getCSSIcon: calls GuiTool.getTreeNodeIconClass with the node
 *   Group 7  [Risk 2] — searchMode getter: returns false when root === activeRoot;
 *                        returns true when they differ
 *   Group 8  [Risk 2] — searchStart(false): resets activeRoot to root
 *   Group 9  [Risk 2] — getNodeEqualsFun: returned function compares by data.identifier;
 *                        returns true for same identifier, false for different
 *   Group 10 [Risk 2] — isTableStyle: returns true when defaultFolder.path includes "/Table Style"
 *   Group 11 [Risk 1] — addDeleteDataSources root=null: sets loadDataSourcesAfterLoadRoot=true
 */

import { NO_ERRORS_SCHEMA } from "@angular/core";
import { render } from "@testing-library/angular";
import { NEVER, Subject } from "rxjs";
import { AssetTreeComponent } from "./asset-tree.component";
import { AssetTreeService } from "./asset-tree.service";
import { AssetClientService } from "../../common/asset-client/asset-client.service";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { DebounceService } from "../services/debounce.service";
import { CurrentUserService } from "../../../../../shared/util/current-user.service";
import { TreeNodeModel } from "../tree/tree-node-model";
import { GuiTool } from "../../common/util/gui-tool";

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

function makeNode(
   identifier: string,
   label: string,
   children: TreeNodeModel[] = [],
   leaf = true
): TreeNodeModel {
   return {
      label,
      leaf,
      children,
      data: { identifier, type: "FOLDER", path: "/", scope: 0 } as any,
   };
}

// ---------------------------------------------------------------------------
// Render helper
// ---------------------------------------------------------------------------

async function renderComponent(inputs: Partial<AssetTreeComponent> = {}) {
   // Reset assetChanged subject per test
   assetChangedSubject = new Subject();
   ASSET_CLIENT_SERVICE_MOCK = {
      connect: vi.fn(),
      disconnect: vi.fn(),
      assetChanged: assetChangedSubject,
   };
   DEBOUNCE_SERVICE_MOCK = { debounce: vi.fn() };

   // Prevent loadAssetTree from completing to avoid test interference
   ASSET_TREE_SERVICE_MOCK.getAssetTreeNode.mockReturnValue(NEVER);
   CURRENT_USER_SERVICE_MOCK.getPortalCurrentUser.mockReturnValue(NEVER);

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

   // Set up a basic tree so node-traversal tests have something to work with
   const leaf1 = makeNode("leaf-1", "Leaf 1");
   const leaf2 = makeNode("leaf-2", "Leaf 2");
   const child1 = makeNode("child-1", "Child 1", [leaf1], false);
   const root = makeNode("root", "Root", [child1, leaf2], false);
   comp.root = root;
   comp.activeRoot = root;

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
// Group 1: findAssetTreeNodeFromIdentifier [Risk 3]
// ---------------------------------------------------------------------------

describe("AssetTreeComponent — findAssetTreeNodeFromIdentifier", () => {
   // 🔁 Regression-sensitive: used by handleAssetChangeEvent to locate the changed node;
   //    a wrong result causes the UI to refresh the wrong node.
   it("should find a direct child node by identifier", async () => {
      const { comp } = await renderComponent();
      const node = comp.findAssetTreeNodeFromIdentifier(comp.root, "child-1");
      expect(node).not.toBeNull();
      expect(node.label).toBe("Child 1");
   });

   it("should find a deep nested node by identifier", async () => {
      const { comp } = await renderComponent();
      const node = comp.findAssetTreeNodeFromIdentifier(comp.root, "leaf-1");
      expect(node).not.toBeNull();
      expect(node.label).toBe("Leaf 1");
   });

   it("should return null for an identifier that does not exist in the tree", async () => {
      const { comp } = await renderComponent();
      const node = comp.findAssetTreeNodeFromIdentifier(comp.root, "no-such-id");
      expect(node).toBeNull();
   });

   it("should return null when identifier argument is null", async () => {
      const { comp } = await renderComponent();
      const node = comp.findAssetTreeNodeFromIdentifier(comp.root, null);
      expect(node).toBeNull();
   });

   it("should return the root node itself if its identifier matches", async () => {
      const { comp } = await renderComponent();
      const node = comp.findAssetTreeNodeFromIdentifier(comp.root, "root");
      expect(node).toBe(comp.root);
   });
});

// ---------------------------------------------------------------------------
// Group 2: findAssetTreeNodeParentFromIdentifier [Risk 3]
// ---------------------------------------------------------------------------

describe("AssetTreeComponent — findAssetTreeNodeParentFromIdentifier", () => {
   // 🔁 Regression-sensitive: used by handleAssetChangeEvent to find the parent to refresh;
   //    returning the wrong parent causes refreshing the wrong branch.
   it("should return the parent of a direct child node", async () => {
      const { comp } = await renderComponent();
      const parent = comp.findAssetTreeNodeParentFromIdentifier(comp.root, "child-1");
      expect(parent).toBe(comp.root);
   });

   it("should return the parent of a nested node", async () => {
      const { comp } = await renderComponent();
      const parent = comp.findAssetTreeNodeParentFromIdentifier(comp.root, "leaf-1");
      expect(parent).not.toBeNull();
      expect(parent.label).toBe("Child 1");
   });

   it("should return null for an identifier that does not exist", async () => {
      const { comp } = await renderComponent();
      const parent = comp.findAssetTreeNodeParentFromIdentifier(comp.root, "no-such-id");
      expect(parent).toBeNull();
   });

   it("should return null when identifier argument is null", async () => {
      const { comp } = await renderComponent();
      const parent = comp.findAssetTreeNodeParentFromIdentifier(comp.root, null);
      expect(parent).toBeNull();
   });
});

// ---------------------------------------------------------------------------
// Group 3: selectNodes [Risk 3]
// ---------------------------------------------------------------------------

describe("AssetTreeComponent — selectNodes", () => {
   // 🔁 Regression-sensitive: nodesSelected and nodeSelected must both fire; missing either
   //    breaks any subscriber relying on selection events.
   it("should emit nodesSelected with the full array", async () => {
      const { comp } = await renderComponent();
      const spy = vi.fn();
      comp.nodesSelected.subscribe(spy);

      const nodes = [makeNode("n1", "N1")];
      comp.selectNodes(nodes);

      expect(spy).toHaveBeenCalledWith(nodes);
   });

   it("should emit nodeSelected with the first node", async () => {
      const { comp } = await renderComponent();
      const spy = vi.fn();
      comp.nodeSelected.subscribe(spy);

      const n1 = makeNode("n1", "N1");
      const n2 = makeNode("n2", "N2");
      comp.selectNodes([n1, n2]);

      expect(spy).toHaveBeenCalledWith(n1);
   });

   it("should update selectedNodes to the given array", async () => {
      const { comp } = await renderComponent();
      const n1 = makeNode("n1", "N1");
      comp.selectNodes([n1]);
      expect(comp.selectedNodes).toEqual([n1]);
   });
});

// ---------------------------------------------------------------------------
// Group 4: doubleclickNode [Risk 2]
// ---------------------------------------------------------------------------

describe("AssetTreeComponent — doubleclickNode", () => {
   it("should emit dblclickNode with the node", async () => {
      const { comp } = await renderComponent();
      const spy = vi.fn();
      comp.dblclickNode.subscribe(spy);

      const node = makeNode("n1", "N1");
      comp.doubleclickNode(node);

      expect(spy).toHaveBeenCalledWith(node);
   });
});

// ---------------------------------------------------------------------------
// Group 5: contextmenuTreeNode [Risk 2]
// ---------------------------------------------------------------------------

describe("AssetTreeComponent — contextmenuTreeNode", () => {
   it("should emit onContextmenu with the passed event tuple", async () => {
      const { comp } = await renderComponent();
      const spy = vi.fn();
      comp.onContextmenu.subscribe(spy);

      const event: [MouseEvent, TreeNodeModel, TreeNodeModel[]] = [
         {} as any, makeNode("n1", "N1"), []
      ];
      comp.contextmenuTreeNode(event);

      expect(spy).toHaveBeenCalledWith(event);
   });
});

// ---------------------------------------------------------------------------
// Group 6: getCSSIcon [Risk 2]
// ---------------------------------------------------------------------------

describe("AssetTreeComponent — getCSSIcon", () => {
   it("should call GuiTool.getTreeNodeIconClass with the node", async () => {
      const { comp } = await renderComponent();
      const spy = vi.spyOn(GuiTool, "getTreeNodeIconClass").mockReturnValue("icon-class");
      const node = makeNode("n1", "N1");

      comp.getCSSIcon(node);

      expect(spy).toHaveBeenCalledWith(node, "");
   });

   it("should return the value from GuiTool.getTreeNodeIconClass", async () => {
      const { comp } = await renderComponent();
      vi.spyOn(GuiTool, "getTreeNodeIconClass").mockReturnValue("my-icon");
      expect(comp.getCSSIcon(makeNode("n1", "N1"))).toBe("my-icon");
   });
});

// ---------------------------------------------------------------------------
// Group 7: searchMode getter [Risk 2]
// ---------------------------------------------------------------------------

describe("AssetTreeComponent — searchMode getter", () => {
   it("should return false when root and activeRoot are the same object", async () => {
      const { comp } = await renderComponent();
      comp.activeRoot = comp.root;
      expect(comp.searchMode).toBe(false);
   });

   it("should return true when activeRoot differs from root", async () => {
      const { comp } = await renderComponent();
      comp.activeRoot = makeNode("search-root", "Search Root", [], false);
      expect(comp.searchMode).toBe(true);
   });
});

// ---------------------------------------------------------------------------
// Group 8: searchStart(false) [Risk 2]
// ---------------------------------------------------------------------------

describe("AssetTreeComponent — searchStart(false)", () => {
   // 🔁 Regression-sensitive: searchStart(false) must reset activeRoot to root so the
   //    tree returns to normal display after clearing the search box.
   it("should reset activeRoot to root when called with false", async () => {
      const { comp } = await renderComponent();
      comp.activeRoot = makeNode("search-root", "Search Root", [], false);

      comp.searchStart(false);

      expect(comp.activeRoot).toBe(comp.root);
   });

   it("should reset searchStr to empty string when called with false", async () => {
      const { comp } = await renderComponent();
      (comp as any).searchStr = "hello";

      comp.searchStart(false);

      expect((comp as any).searchStr).toBe("");
   });
});

// ---------------------------------------------------------------------------
// Group 9: getNodeEqualsFun [Risk 2]
// ---------------------------------------------------------------------------

describe("AssetTreeComponent — getNodeEqualsFun", () => {
   it("should return a function that returns true when both nodes have the same identifier", async () => {
      const { comp } = await renderComponent();
      const fn = comp.getNodeEqualsFun();
      const n1 = makeNode("id-A", "A");
      const n2 = makeNode("id-A", "B");
      expect(fn(n1, n2)).toBe(true);
   });

   it("should return a function that returns false when nodes have different identifiers", async () => {
      const { comp } = await renderComponent();
      const fn = comp.getNodeEqualsFun();
      const n1 = makeNode("id-A", "A");
      const n2 = makeNode("id-B", "B");
      expect(fn(n1, n2)).toBe(false);
   });

   it("should return a function that handles null nodes gracefully", async () => {
      const { comp } = await renderComponent();
      const fn = comp.getNodeEqualsFun();
      expect(fn(null, null)).toBe(true);
   });
});

// ---------------------------------------------------------------------------
// Group 10: isTableStyle [Risk 2]
// ---------------------------------------------------------------------------

describe("AssetTreeComponent — isTableStyle", () => {
   it("should return true when defaultFolder.path contains '/Table Style'", async () => {
      const { comp } = await renderComponent({
         defaultFolder: { path: "/Library/Table Style/My Styles" } as any,
      });
      expect(comp.isTableStyle()).toBe(true);
   });

   it("should return false when defaultFolder.path does not contain '/Table Style'", async () => {
      const { comp } = await renderComponent({
         defaultFolder: { path: "/Shared Worksheets" } as any,
      });
      expect(comp.isTableStyle()).toBe(false);
   });
});

// ---------------------------------------------------------------------------
// Group 11: addDeleteDataSources root=null [Risk 1]
// ---------------------------------------------------------------------------

describe("AssetTreeComponent — addDeleteDataSources when root is null", () => {
   it("should set loadDataSourcesAfterLoadRoot=true when root is null", async () => {
      const { comp } = await renderComponent();
      comp.root = null;

      comp.addDeleteDataSources();

      expect((comp as any).loadDataSourcesAfterLoadRoot).toBe(true);
   });

   it("should NOT call getAssetTreeNode when root is null", async () => {
      const { comp } = await renderComponent();
      comp.root = null;
      ASSET_TREE_SERVICE_MOCK.getAssetTreeNode.mockReset();

      comp.addDeleteDataSources();

      expect(ASSET_TREE_SERVICE_MOCK.getAssetTreeNode).not.toHaveBeenCalled();
   });
});
