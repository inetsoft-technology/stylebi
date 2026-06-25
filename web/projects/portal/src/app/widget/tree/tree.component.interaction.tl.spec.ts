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
 * TreeComponent — Pass 1: Interaction
 *
 * Coverage plan:
 *   Group 1  [Risk 2] — root setter: updates selectedNode.data when node found in new root
 *   Group 2  [Risk 1] — showRoot getter: returns true when in recent view
 *   Group 3  [Risk 2] — searchStr setter: delegates to dataSource.filterValues
 *   Group 4  [Risk 2] — search: emits searchStart / searchStrChange; calls expandAll on non-empty
 *   Group 5  [Risk 1] — expandNode / collapseNode: emit nodeExpanded / nodeCollapsed
 *   Group 6  [Risk 1] — doubleclickNode / clickNode: emit dblclickNode / nodeClicked
 *   Group 7  [Risk 2] — selectNode: single-select, multi-select no-modifier, ctrl, checkbox branches
 *   Group 8  [Risk 1] — exclusiveSelectNode: sets single-item selectedNodes and emits
 *   Group 9  [Risk 1] — isSelectedNode getter: returned function matches by data equality
 *   Group 10 [Risk 1] — setHighLightNodes: wraps node in array
 *   Group 11 [Risk 1] — onDrag / onDragOver / onDrop: delegate to emitters
 *   Group 12 [Risk 1] — addNodeToArray: dedup by nodeEquals
 *   Group 13 [Risk 2] — getParentNode: traverses tree to find parent
 *   Group 14 [Risk 1] — selectAndExpandToNode / expandToNode: expands all ancestors
 *   Group 15 [Risk 1] — deselectAllNodes: clears selectedNodes
 *   Group 16 [Risk 2] — onKey: down selects first; arrow keys move focus / expand / collapse
 *   Group 17 [Risk 1] — clearSearchContent / onSearchEscape
 *   Group 18 [Risk 1] — switchRecent: toggles treeView and fetches recent data
 *   Group 19 [Risk 2] — addShiftNodes: range selection from last selected to clicked node
 *
 * Out of scope this pass:
 *   ngOnInit, ngOnChanges, ngAfterViewInit → tree.component.risk.tl.spec.ts
 *   fixSelectedNodes, expandAll, subscribeVScroll → tree.component.risk.tl.spec.ts
 *   showHelpLink, treeContainerMaxHeight → tree.component.display.tl.spec.ts
 */

import { of } from "rxjs";
import { vi } from "vitest";
import {
   makeFolder,
   makeMockEvent,
   makeNode,
   renderTree,
} from "./tree-node.component.test-helpers";

afterEach(() => {
   vi.restoreAllMocks();
});

// ===========================================================================
// Group 1 — root setter
// ===========================================================================

describe("Group 1 — root setter", () => {
   it("should update selectedNode.data when node is found in the new root by path", async () => {
      const oldNodeData = { path: "/ws/Sheet" };
      const selected = makeNode({ data: oldNodeData });
      const newData = { path: "/ws/Sheet", label: "Updated" };
      const newRoot = makeFolder([makeNode({ data: newData })]);

      const { comp } = await renderTree({
         root: makeFolder([selected]),
         componentProperties: { selectedNodes: [selected] },
      });

      // Assign a new root containing an updated version of the selected node
      comp.root = newRoot;

      expect(selected.data).toEqual(newData);
   });

   it("should not crash when selectedNodes contains null entries", async () => {
      const { comp } = await renderTree({
         componentProperties: { selectedNodes: [null] },
      });

      expect(() => { comp.root = makeFolder([]); }).not.toThrow();
   });
});

// ===========================================================================
// Group 2 — showRoot getter
// ===========================================================================

describe("Group 2 — showRoot getter", () => {
   it("should return _showRoot value in full view mode", async () => {
      const { comp } = await renderTree({ componentProperties: { showRoot: false } });
      expect(comp.showRoot).toBe(false);
   });

   it("should return true in recent view (isRecentView=true)", async () => {
      const { comp } = await renderTree({
         componentProperties: {
            recentEnabled: true,
            getRecentTreeFun: () => of([]),
            showRoot: false,
         },
      });
      // Switch to recent view so isRecentView becomes true
      comp.switchRecent();
      expect(comp.showRoot).toBe(true);
   });
});

// ===========================================================================
// Group 3 — searchStr setter
// ===========================================================================

describe("Group 3 — searchStr setter", () => {
   it("should call dataSource.filterValues when dataSource is set", async () => {
      const dataSourceMock = {
         filterValues: vi.fn(),
         refreshByRoot: vi.fn(),
         virtualScroll: { subscribe: vi.fn(() => ({ unsubscribe: vi.fn() })) },
         inViewport: vi.fn().mockReturnValue(true),
         nodeVisible: vi.fn().mockReturnValue(true),
         inSearchCollapsed: vi.fn().mockReturnValue(false),
         registerScrollContainer: vi.fn(),
         scrollTop: { subscribe: vi.fn() },
         fireVirtualScroll: vi.fn(),
      };
      const root = makeFolder([makeNode()]);
      const { comp } = await renderTree({
         root,
         componentProperties: { dataSource: dataSourceMock as any },
      });

      comp.searchStr = "test";

      expect(dataSourceMock.filterValues).toHaveBeenCalledWith(null, "test");
   });

   it("should store the search string in _searchStr", async () => {
      const { comp } = await renderTree();
      comp.searchStr = "query";
      expect(comp.searchStr).toBe("query");
   });
});

// ===========================================================================
// Group 4 — search
// ===========================================================================

describe("Group 4 — search", () => {
   it("should emit searchStart(true) and searchStrChange when searching a non-empty string", async () => {
      const root = makeFolder([makeNode({ label: "hello" })], { expanded: true });
      const { comp } = await renderTree({ root });
      const startSpy = vi.spyOn(comp.searchStart, "emit");
      const changeSpy = vi.spyOn(comp.searchStrChange, "emit");

      comp.search("hello");

      expect(startSpy).toHaveBeenCalledWith(true);
      expect(changeSpy).toHaveBeenCalledWith("hello");
   });

   it("should emit searchStart(false) when clearing the search string", async () => {
      const { comp } = await renderTree();
      const startSpy = vi.spyOn(comp.searchStart, "emit");

      comp.search("");

      expect(startSpy).toHaveBeenCalledWith(false);
   });

   it("should not emit searchStart(true) again when already searching", async () => {
      const root = makeFolder([makeNode({ label: "hello" })], { expanded: true });
      const { comp } = await renderTree({ root });
      comp.search("hel"); // first search
      const startSpy = vi.spyOn(comp.searchStart, "emit");

      comp.search("hello"); // second search — searchStr already set

      expect(startSpy).not.toHaveBeenCalledWith(true);
   });
});

// ===========================================================================
// Group 5 — expandNode / collapseNode
// ===========================================================================

describe("Group 5 — expandNode / collapseNode", () => {
   it("should emit nodeExpanded when expandNode is called", async () => {
      const { comp } = await renderTree();
      const node = makeNode();
      const spy = vi.spyOn(comp.nodeExpanded, "emit");

      comp.expandNode(node);

      expect(spy).toHaveBeenCalledWith(node);
   });

   it("should emit nodeCollapsed when collapseNode is called", async () => {
      const { comp } = await renderTree();
      const node = makeNode();
      const spy = vi.spyOn(comp.nodeCollapsed, "emit");

      comp.collapseNode(node);

      expect(spy).toHaveBeenCalledWith(node);
   });
});

// ===========================================================================
// Group 6 — doubleclickNode / clickNode
// ===========================================================================

describe("Group 6 — doubleclickNode / clickNode", () => {
   it("should emit dblclickNode when doubleclickNode is called", async () => {
      const { comp } = await renderTree();
      const node = makeNode();
      const spy = vi.spyOn(comp.dblclickNode, "emit");

      comp.doubleclickNode(node);

      expect(spy).toHaveBeenCalledWith(node);
   });

   it("should emit nodeClicked when clickNode is called", async () => {
      const { comp } = await renderTree();
      const node = makeNode();
      const spy = vi.spyOn(comp.nodeClicked, "emit");

      comp.clickNode(node);

      expect(spy).toHaveBeenCalledWith(node);
   });
});

// ===========================================================================
// Group 7 — selectNode
// ===========================================================================

describe("Group 7 — selectNode", () => {
   it("should replace selectedNodes with [node] in single-select mode", async () => {
      const { comp } = await renderTree({ componentProperties: { nodeSelectable: true } });
      const node = makeNode();
      const spy = vi.spyOn(comp.nodesSelected, "emit");

      comp.selectNode(node, makeMockEvent(), true);

      expect(comp.selectedNodes).toEqual([node]);
      expect(spy).toHaveBeenCalledWith([node]);
   });

   it("should NOT emit when emit=false", async () => {
      const { comp } = await renderTree({ componentProperties: { nodeSelectable: true } });
      const spy = vi.spyOn(comp.nodesSelected, "emit");

      comp.selectNode(makeNode(), makeMockEvent(), false);

      expect(spy).not.toHaveBeenCalled();
   });

   it("should add node to selectedNodes in multi-select mode with no modifier keys", async () => {
      const { comp } = await renderTree({ componentProperties: { multiSelect: true } });
      const nodeA = makeNode({ label: "A" });
      comp.selectedNodes = [nodeA];
      const nodeB = makeNode({ label: "B" });

      comp.selectNode(nodeB, makeMockEvent(), true);

      expect(comp.selectedNodes).toContain(nodeB);
   });

   it("should remove node from multi-select when ctrlKey is held and node is already selected", async () => {
      const { comp } = await renderTree({ componentProperties: { multiSelect: true } });
      const node = makeNode();
      comp.selectedNodes = [node];

      comp.selectNode(node, makeMockEvent({ ctrlKey: true }), true);

      expect(comp.selectedNodes).not.toContain(node);
   });

   it("should toggle leaf node in checkboxEnable mode", async () => {
      const { comp } = await renderTree({ componentProperties: { checkboxEnable: true } });
      const leaf = makeNode({ leaf: true });

      comp.selectNode(leaf, makeMockEvent(), true); // add

      expect(comp.selectedNodes).toContain(leaf);

      comp.selectNode(leaf, makeMockEvent(), true); // remove

      expect(comp.selectedNodes).not.toContain(leaf);
   });

   it("should NOT emit when selecting a non-leaf node in checkboxEnable mode", async () => {
      const { comp } = await renderTree({ componentProperties: { checkboxEnable: true } });
      const folder = makeFolder();
      const spy = vi.spyOn(comp.nodesSelected, "emit");

      comp.selectNode(folder, makeMockEvent(), true);

      expect(spy).not.toHaveBeenCalled();
   });
});

// ===========================================================================
// Group 8 — exclusiveSelectNode
// ===========================================================================

describe("Group 8 — exclusiveSelectNode", () => {
   it("should replace selectedNodes with [node] and emit nodesSelected", async () => {
      const { comp } = await renderTree({
         componentProperties: { selectedNodes: [makeNode(), makeNode()] },
      });
      const node = makeNode({ label: "Exclusive" });
      const spy = vi.spyOn(comp.nodesSelected, "emit");

      comp.exclusiveSelectNode(node);

      expect(comp.selectedNodes).toEqual([node]);
      expect(spy).toHaveBeenCalledWith([node]);
   });
});

// ===========================================================================
// Group 9 — isSelectedNode getter
// ===========================================================================

describe("Group 9 — isSelectedNode getter", () => {
   it("should return true when node is in selectedNodes", async () => {
      const node = makeNode({ data: { path: "/ws/A" } });
      const { comp } = await renderTree();
      // Set directly post-render to avoid fixSelectedNodes path-lookup stripping the node
      comp.selectedNodes = [node];

      expect(comp.isSelectedNode(node)).toBe(true);
   });

   it("should return false when node is not in selectedNodes", async () => {
      const { comp } = await renderTree();
      comp.selectedNodes = [];

      expect(comp.isSelectedNode(makeNode())).toBe(false);
   });
});

// ===========================================================================
// Group 10 — setHighLightNodes
// ===========================================================================

describe("Group 10 — setHighLightNodes", () => {
   it("should set highLightNodes to [node]", async () => {
      const { comp } = await renderTree();
      const node = makeNode();

      comp.setHighLightNodes(node);

      expect(comp.highLightNodes).toEqual([node]);
   });
});

// ===========================================================================
// Group 11 — onDrag / onDragOver / onDrop
// ===========================================================================

describe("Group 11 — onDrag / onDragOver / onDrop", () => {
   it("should emit nodeDrag with the event", async () => {
      const { comp } = await renderTree();
      const evt = makeMockEvent();
      const spy = vi.spyOn(comp.nodeDrag, "emit");

      comp.onDrag(evt);

      expect(spy).toHaveBeenCalledWith(evt);
   });

   it("should call preventDefault when isRejectFunction is null", async () => {
      const { comp } = await renderTree();
      const evt = makeMockEvent();

      comp.onDragOver(evt);

      expect(evt.preventDefault).toHaveBeenCalled();
   });

   it("should NOT call preventDefault when isRejectFunction returns true", async () => {
      const { comp } = await renderTree({
         componentProperties: { isRejectFunction: () => true },
      });
      const evt = makeMockEvent();

      comp.onDragOver(evt);

      expect(evt.preventDefault).not.toHaveBeenCalled();
   });

   it("should emit nodeDrop with the event", async () => {
      const { comp } = await renderTree();
      const evt = makeMockEvent();
      const spy = vi.spyOn(comp.nodeDrop, "emit");

      comp.onDrop(evt);

      expect(spy).toHaveBeenCalledWith(evt);
   });
});

// ===========================================================================
// Group 12 — addNodeToArray
// ===========================================================================

describe("Group 12 — addNodeToArray", () => {
   it("should add a node if not already present", async () => {
      const { comp } = await renderTree();
      const node = makeNode();
      const arr: any[] = [];

      comp.addNodeToArray(node, arr);

      expect(arr).toContain(node);
   });

   it("should not add a duplicate node (by data equality)", async () => {
      const { comp } = await renderTree();
      const node = makeNode({ data: { path: "/ws/A" } });
      const arr = [node];

      comp.addNodeToArray(node, arr);

      expect(arr.length).toBe(1);
   });
});

// ===========================================================================
// Group 13 — getParentNode
// ===========================================================================

describe("Group 13 — getParentNode", () => {
   it("should return the direct parent of a child node", async () => {
      const child = makeNode({ label: "child" });
      const parent = makeFolder([child], { label: "parent" });
      const root = makeFolder([parent], { label: "root" });
      const { comp } = await renderTree({ root });

      expect(comp.getParentNode(child)).toBe(parent);
   });

   it("should return null when the node has no parent (is root)", async () => {
      const root = makeFolder([makeNode()], { label: "root" });
      const { comp } = await renderTree({ root });

      expect(comp.getParentNode(root)).toBeNull();
   });

   it("should find parent at any depth", async () => {
      const grandchild = makeNode({ label: "gc" });
      const child = makeFolder([grandchild], { label: "child" });
      const root = makeFolder([child]);
      const { comp } = await renderTree({ root });

      expect(comp.getParentNode(grandchild)).toBe(child);
   });
});

// ===========================================================================
// Group 14 — selectAndExpandToNode / expandToNode
// ===========================================================================

describe("Group 14 — selectAndExpandToNode / expandToNode", () => {
   it("should expand ancestor nodes when expandToNode is called", async () => {
      const leaf = makeNode({ label: "leaf" });
      const parent = makeFolder([leaf], { label: "parent", expanded: false });
      const root = makeFolder([parent], { label: "root", expanded: true });
      const { comp } = await renderTree({ root });

      comp.expandToNode(leaf);

      expect(parent.expanded).toBe(true);
   });

   it("should set selectedNodes to [leaf] when selectAndExpandToNode is called", async () => {
      const leaf = makeNode({ label: "leaf" });
      const parent = makeFolder([leaf], { label: "parent", expanded: false });
      const root = makeFolder([parent], { label: "root", expanded: true });
      const { comp } = await renderTree({ root });

      comp.selectAndExpandToNode(leaf);

      expect(comp.selectedNodes).toEqual([leaf]);
   });

   it("should expand ancestor nodes when selectAndExpandToNode is called", async () => {
      const leaf = makeNode({ label: "leaf" });
      const parent = makeFolder([leaf], { label: "parent", expanded: false });
      const root = makeFolder([parent], { label: "root", expanded: true });
      const { comp } = await renderTree({ root });

      comp.selectAndExpandToNode(leaf);

      expect(parent.expanded).toBe(true);
   });
});

// ===========================================================================
// Group 15 — deselectAllNodes
// ===========================================================================

describe("Group 15 — deselectAllNodes", () => {
   it("should clear selectedNodes array", async () => {
      const node = makeNode();
      const { comp } = await renderTree({ componentProperties: { selectedNodes: [node] } });

      comp.deselectAllNodes();

      expect(comp.selectedNodes).toEqual([]);
   });
});

// ===========================================================================
// Group 16 — onKey (keyboard navigation)
// Bypass: TreeComponent exposes no public setter for `focused` and focusedSubject is private.
// All tests that need a pre-set focus state assign comp["focused"] directly; tests that assert
// on navigation emission spy on comp["focusedSubject"].next.
// ===========================================================================

describe("Group 16 — onKey", () => {
   it("should focus root node on first down-arrow when nothing is focused", async () => {
      const root = makeFolder([makeNode()]);
      const { comp } = await renderTree({ root });
      const spy = vi.spyOn(comp["focusedSubject"], "next");

      comp.onKey({ keyCode: 40 } as any); // down arrow

      expect(spy).toHaveBeenCalledWith(root);
   });

   it("should move focus to first child on down-arrow when root is focused and expanded", async () => {
      const child = makeNode({ label: "child" });
      const root = makeFolder([child], { expanded: true });
      const { comp } = await renderTree({ root, componentProperties: { showRoot: true } });
      comp["focused"] = root;
      const spy = vi.spyOn(comp["focusedSubject"], "next");

      comp.onKey({ keyCode: 40 } as any); // down arrow

      expect(spy).toHaveBeenCalledWith(child);
   });

   it("should set expanded=true on focused folder when right-arrow is pressed", async () => {
      const folder = makeFolder([], { expanded: false });
      const root = makeFolder([folder]);
      const { comp } = await renderTree({ root });
      comp["focused"] = folder;

      comp.onKey({ keyCode: 39 } as any); // right arrow

      expect(folder.expanded).toBe(true);
   });

   it("should emit nodeExpanded on focused folder when right-arrow is pressed", async () => {
      const folder = makeFolder([], { expanded: false });
      const root = makeFolder([folder]);
      const { comp } = await renderTree({ root });
      comp["focused"] = folder;
      const expandSpy = vi.spyOn(comp.nodeExpanded, "emit");

      comp.onKey({ keyCode: 39 } as any); // right arrow

      expect(expandSpy).toHaveBeenCalledWith(folder);
   });

   it("should set expanded=false on focused folder when left-arrow is pressed", async () => {
      const folder = makeFolder([], { expanded: true });
      const root = makeFolder([folder]);
      const { comp } = await renderTree({ root });
      comp["focused"] = folder;

      comp.onKey({ keyCode: 37 } as any); // left arrow

      expect(folder.expanded).toBe(false);
   });

   it("should emit nodeCollapsed on focused folder when left-arrow is pressed", async () => {
      const folder = makeFolder([], { expanded: true });
      const root = makeFolder([folder]);
      const { comp } = await renderTree({ root });
      comp["focused"] = folder;
      const collapseSpy = vi.spyOn(comp.nodeCollapsed, "emit");

      comp.onKey({ keyCode: 37 } as any); // left arrow

      expect(collapseSpy).toHaveBeenCalledWith(folder);
   });
});

// ===========================================================================
// Group 17 — clearSearchContent / onSearchEscape
// ===========================================================================

describe("Group 17 — clearSearchContent / onSearchEscape", () => {
   it("should clear searchStr and emit searchStart(false) when clearSearchContent is called", async () => {
      const root = makeFolder([makeNode({ label: "hello" })], { expanded: true });
      const { comp } = await renderTree({ root });
      comp.search("hello");
      const spy = vi.spyOn(comp.searchStart, "emit");

      comp.clearSearchContent();

      expect(comp.searchStr).toBe("");
      expect(spy).toHaveBeenCalledWith(false);
   });

   it("should stop propagation and clear search on Escape when searchStr is set", async () => {
      const root = makeFolder([makeNode({ label: "hello" })], { expanded: true });
      const { comp } = await renderTree({ root });
      comp.search("hello");
      const evt = { stopPropagation: vi.fn(), keyCode: 27 } as any;

      comp.onSearchEscape(evt);

      expect(evt.stopPropagation).toHaveBeenCalled();
      expect(comp.searchStr).toBe("");
   });

   it("should NOT call stopPropagation on Escape when searchStr is empty", async () => {
      const { comp } = await renderTree();
      const evt = { stopPropagation: vi.fn() } as any;

      comp.onSearchEscape(evt);

      expect(evt.stopPropagation).not.toHaveBeenCalled();
   });
});

// ===========================================================================
// Group 18 — switchRecent
// ===========================================================================

describe("Group 18 — switchRecent", () => {
   it("should switch to recent view and fetch recent data", async () => {
      const recentNode = makeNode({ label: "recent" });
      const getRecentFn = vi.fn().mockReturnValue(of([recentNode]));
      const { comp } = await renderTree({
         componentProperties: { recentEnabled: true, getRecentTreeFun: getRecentFn },
      });

      comp.switchRecent();

      expect(comp.isRecentView).toBe(true);
      expect(getRecentFn).toHaveBeenCalled();
   });

   it("should switch back to full view on second call", async () => {
      const getRecentFn = vi.fn().mockReturnValue(of([]));
      const { comp } = await renderTree({
         componentProperties: { recentEnabled: true, getRecentTreeFun: getRecentFn },
      });

      comp.switchRecent(); // → recent
      comp.switchRecent(); // → full

      expect(comp.isRecentView).toBe(false);
   });

   it("should do nothing when recentEnabled is false", async () => {
      const getRecentFn = vi.fn().mockReturnValue(of([]));
      const { comp } = await renderTree({
         componentProperties: { recentEnabled: false, getRecentTreeFun: getRecentFn },
      });

      comp.switchRecent();

      expect(comp.isRecentView).toBe(false);
      expect(getRecentFn).not.toHaveBeenCalled();
   });
});

// ===========================================================================
// Group 19 — addShiftNodes (range selection)
// ===========================================================================

describe("Group 19 — addShiftNodes", () => {
   it("should add a range of nodes between lastSelected and clicked node", async () => {
      const n1 = makeNode({ label: "1" });
      const n2 = makeNode({ label: "2" });
      const n3 = makeNode({ label: "3" });
      const root = makeFolder([n1, n2, n3], { expanded: true });
      const { comp } = await renderTree({ root, componentProperties: { multiSelect: true } });
      comp.selectedNodes = [n1]; // last selected is n1

      comp.addShiftNodes(n3); // shift-click n3

      expect(comp.selectedNodes).toContain(n1);
      expect(comp.selectedNodes).toContain(n2);
      expect(comp.selectedNodes).toContain(n3);
   });

   it("should add only the clicked node when no nodes are currently selected", async () => {
      const n1 = makeNode({ label: "1" });
      const root = makeFolder([n1], { expanded: true });
      const { comp } = await renderTree({ root, componentProperties: { multiSelect: true } });
      comp.selectedNodes = [];

      comp.addShiftNodes(n1);

      expect(comp.selectedNodes).toContain(n1);
   });
});
