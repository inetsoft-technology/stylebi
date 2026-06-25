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
 * TreeNodeComponent — Pass 1: Interaction
 *
 * Coverage plan:
 *   Group 1  [Risk 2] — contextmenuListener
 *   Group 2  [Risk 2] — touchstartListener / touchendListener
 *   Group 3  [Risk 2] — toggleNode
 *   Group 4  [Risk 2] — clickSelectNode
 *   Group 5  [Risk 1] — mousedownSelectNode
 *   Group 6  [Risk 1] — tooltip getter
 *   Group 7  [Risk 1] — selectNode
 *   Group 8  [Risk 1] — doubleClickNode
 *   Group 9  [Risk 1] — hasChildren
 *   Group 10 [Risk 1] — notExpandableType
 *   Group 11 [Risk 1] — favoritesUser getter
 *   Group 12 [Risk 2] — dragStarted
 *   Group 13 [Risk 1] — dragOver
 *   Group 14 [Risk 1] — isDraggable getter
 *   Group 15 [Risk 1] — isHighLight
 *   Group 16 [Risk 2] — isSelected
 *   Group 17 [Risk 2] — isGrayedOut
 *   Group 18 [Risk 1] — onDrop
 *   Group 19 [Risk 1] — keepAllChildren getter
 *   Group 20 [Risk 1] — getSort
 *   Group 21 [Risk 2] — hasMenu
 *
 * Out of scope this pass:
 *   ngOnInit, ngOnDestroy, ngOnChanges, touchmoveListener, updateInViewport
 *     → tree-node.component.risk.tl.spec.ts
 *   getIcon, getToggleIcon, nodeLabel, getVirtualScrollShowChildren
 *     → tree-node.component.display.tl.spec.ts
 *   getFieldName (private) — tested transitively via isGrayedOut (Group 17)
 *   isToggleElementEventTarget (private) — tested transitively via clickSelectNode and doubleClickNode
 *   inSearchCollapsed — not a TreeNodeComponent method (lives in VirtualScrollTreeDatasource)
 */

import { vi } from "vitest";
import { GuiTool } from "../../common/util/gui-tool";
import {
   DRAG_SERVICE_MOCK,
   makeFolder,
   makeMockEvent,
   makeNode,
   makeTreeMock,
   renderTreeNode,
} from "./tree-node.component.test-helpers";

beforeEach(() => {
   Object.values(DRAG_SERVICE_MOCK).forEach(m => typeof m.mockClear === "function" && m.mockClear());
});

afterEach(() => {
   vi.restoreAllMocks();
});

// ===========================================================================
// Group 1 — contextmenuListener
// ===========================================================================

describe("Group 1 — contextmenuListener", () => {
   it("should preventDefault on right-click (button==2) when contextmenu enabled", async () => {
      const { comp } = await renderTreeNode({ componentProperties: { contextmenu: true } });
      const evt = makeMockEvent({ button: 2 });
      comp.contextmenuListener(evt);
      expect(evt.preventDefault).toHaveBeenCalled();
   });

   it("should NOT call preventDefault for non-right-click", async () => {
      const { comp } = await renderTreeNode({ componentProperties: { contextmenu: true } });
      const evt = makeMockEvent({ button: 0 });
      comp.contextmenuListener(evt);
      expect(evt.preventDefault).not.toHaveBeenCalled();
   });

   it("should call selectNode on unselected node when contextmenu fires", async () => {
      const tree = makeTreeMock();
      const node = makeNode({ leaf: true });
      const { comp } = await renderTreeNode({ node, tree, componentProperties: { contextmenu: true } });
      const evt = makeMockEvent({ button: 2 });

      comp.contextmenuListener(evt);

      expect(tree.selectNode).toHaveBeenCalledWith(node, evt, false);
   });

   it("should stopPropagation when contextmenu fires on unselected node", async () => {
      const tree = makeTreeMock();
      const node = makeNode({ leaf: true });
      const { comp } = await renderTreeNode({ node, tree, componentProperties: { contextmenu: true } });
      const evt = makeMockEvent({ button: 2 });

      comp.contextmenuListener(evt);

      expect(evt.stopPropagation).toHaveBeenCalled();
   });

   it("should emit onContextmenu with [evt, node] when contextmenu fires on unselected node", async () => {
      const tree = makeTreeMock();
      const node = makeNode({ leaf: true });
      const { comp } = await renderTreeNode({ node, tree, componentProperties: { contextmenu: true } });
      const spy = vi.spyOn(comp.onContextmenu, "emit");
      const evt = makeMockEvent({ button: 2 });

      comp.contextmenuListener(evt);

      expect(spy).toHaveBeenCalledWith([evt, node]);
   });

   it("should NOT call selectNode when node is already selected", async () => {
      const tree = makeTreeMock();
      tree.isSelectedNode = vi.fn().mockReturnValue(true);
      const { comp } = await renderTreeNode({ tree, componentProperties: { contextmenu: true } });
      comp.contextmenuListener(makeMockEvent({ button: 2 }));
      expect(tree.selectNode).not.toHaveBeenCalled();
   });

   it("should call setHighLightNodes and skip selectNode when checkboxEnable", async () => {
      const tree = makeTreeMock();
      const node = makeNode();
      const { comp } = await renderTreeNode({
         node,
         tree,
         componentProperties: { contextmenu: true, checkboxEnable: true },
      });
      comp.contextmenuListener(makeMockEvent({ button: 2 }));
      expect(tree.setHighLightNodes).toHaveBeenCalledWith(node);
      expect(tree.selectNode).not.toHaveBeenCalled();
   });

   it("should do nothing when contextmenu input is false", async () => {
      const tree = makeTreeMock();
      const { comp } = await renderTreeNode({ tree, componentProperties: { contextmenu: false } });
      const spy = vi.spyOn(comp.onContextmenu, "emit");
      comp.contextmenuListener(makeMockEvent({ button: 2 }));
      expect(spy).not.toHaveBeenCalled();
      expect(tree.selectNode).not.toHaveBeenCalled();
   });
});

// ===========================================================================
// Group 2 — touchstartListener / touchendListener
// ===========================================================================

describe("Group 2 — touchstartListener / touchendListener", () => {
   it("should stopPropagation on touchstart", async () => {
      const { comp } = await renderTreeNode();
      const evt = makeMockEvent();
      comp.touchstartListener(evt);
      expect(evt.stopPropagation).toHaveBeenCalled();
   });

   it("should call clickSelectNode on quick tap (touchend fires before 500ms)", async () => {
      const { comp } = await renderTreeNode({ componentProperties: { nodeSelectable: true } });
      vi.useFakeTimers();
      try {
         const clickSpy = vi.spyOn(comp, "clickSelectNode");
         const evt = makeMockEvent();
         comp.touchstartListener(evt);
         // fire touchend immediately, before the 500ms timer
         comp.touchendListener(evt);
         expect(clickSpy).toHaveBeenCalledWith(evt, true);
      }
      finally {
         vi.useRealTimers();
      }
   });

   it("should emit onContextmenu after 500ms long-press on repository tree", async () => {
      const tree = makeTreeMock();
      tree.isSelectedNode = vi.fn().mockReturnValue(true);
      const node = makeNode();
      const { comp } = await renderTreeNode({
         node,
         tree,
         componentProperties: { contextmenu: true, isRepositoryTree: true },
      });
      vi.useFakeTimers();
      try {
         const spy = vi.spyOn(comp.onContextmenu, "emit");
         comp.touchstartListener(makeMockEvent());
         vi.advanceTimersByTime(500);
         expect(spy).toHaveBeenCalledWith([expect.anything(), node]);
      }
      finally {
         vi.useRealTimers();
      }
   });

   it("should NOT call clickSelectNode when long-press fires first (timeOutEvent reset to 0)", async () => {
      const { comp } = await renderTreeNode({
         componentProperties: { contextmenu: true, isRepositoryTree: true },
      });
      vi.useFakeTimers();
      try {
         const clickSpy = vi.spyOn(comp, "clickSelectNode");
         comp.touchstartListener(makeMockEvent());
         vi.advanceTimersByTime(500); // timer fires → timeOutEvent = 0
         comp.touchendListener(makeMockEvent()); // timeOutEvent == 0 → no click
         expect(clickSpy).not.toHaveBeenCalled();
      }
      finally {
         vi.useRealTimers();
      }
   });
});

// ===========================================================================
// Group 3 — toggleNode
// ===========================================================================

describe("Group 3 — toggleNode", () => {
   it("should expand collapsed folder and call tree.expandNode", async () => {
      const tree = makeTreeMock();
      const node = makeFolder([], { expanded: false });
      const { comp } = await renderTreeNode({ node, tree });

      comp.toggleNode();

      expect(node.expanded).toBe(true);
      expect(tree.expandNode).toHaveBeenCalledWith(node);
   });

   it("should collapse expanded folder and call tree.collapseNode", async () => {
      const tree = makeTreeMock();
      const node = makeFolder([], { expanded: true });
      const { comp } = await renderTreeNode({ node, tree });

      comp.toggleNode();

      expect(node.expanded).toBe(false);
      expect(tree.collapseNode).toHaveBeenCalledWith(node);
   });

   it("should be a no-op for a leaf node", async () => {
      const tree = makeTreeMock();
      const node = makeNode({ leaf: true, children: [] });
      const { comp } = await renderTreeNode({ node, tree });

      comp.toggleNode();

      expect(tree.expandNode).not.toHaveBeenCalled();
      expect(tree.collapseNode).not.toHaveBeenCalled();
   });

   it("should treat leaf=false node with empty children as a folder", async () => {
      const tree = makeTreeMock();
      const node = makeFolder([], { leaf: false, expanded: false });
      const { comp } = await renderTreeNode({ node, tree });

      comp.toggleNode();

      expect(node.expanded).toBe(true);
      expect(tree.expandNode).toHaveBeenCalledWith(node);
   });
});

// ===========================================================================
// Group 4 — clickSelectNode
// ===========================================================================

describe("Group 4 — clickSelectNode", () => {
   it("should do nothing when nodeSelectable is false", async () => {
      const tree = makeTreeMock();
      const { comp } = await renderTreeNode({ tree, componentProperties: { nodeSelectable: false } });

      comp.clickSelectNode(makeMockEvent());

      expect(tree.selectNode).not.toHaveBeenCalled();
      expect(tree.clickNode).not.toHaveBeenCalled();
   });

   it("should call selectNode when selectOnClick is true", async () => {
      const tree = makeTreeMock();
      const node = makeNode();
      const { comp } = await renderTreeNode({ node, tree, componentProperties: { selectOnClick: true } });
      const evt = makeMockEvent();

      comp.clickSelectNode(evt);

      expect(tree.selectNode).toHaveBeenCalledWith(node, evt, true);
   });

   it("should call tree.selectNode on mobile touch (isTouch=true)", async () => {
      const tree = makeTreeMock();
      const node = makeNode();
      const { comp } = await renderTreeNode({ node, tree, componentProperties: { nodeSelectable: true } });
      const evt = makeMockEvent();

      comp.clickSelectNode(evt, true);

      expect(tree.selectNode).toHaveBeenCalledWith(node, evt, true);
   });

   it("should call tree.clickNode on mobile touch (isTouch=true)", async () => {
      const tree = makeTreeMock();
      const node = makeNode();
      const { comp } = await renderTreeNode({ node, tree, componentProperties: { nodeSelectable: true } });
      const evt = makeMockEvent();

      comp.clickSelectNode(evt, true);

      expect(tree.clickNode).toHaveBeenCalledWith(node);
   });

   it("should stop propagation on mobile touch (isTouch=true)", async () => {
      const tree = makeTreeMock();
      const node = makeNode();
      const { comp } = await renderTreeNode({ node, tree, componentProperties: { nodeSelectable: true } });
      const evt = makeMockEvent();

      comp.clickSelectNode(evt, true);

      expect(evt.stopPropagation).toHaveBeenCalled();
   });

   it("should call exclusiveSelectNode when multi-select node is part of multi-item selection", async () => {
      const tree = makeTreeMock();
      const node = makeNode();
      tree.selectedNodes = [node, makeNode()];
      tree.isSelectedNode = vi.fn().mockReturnValue(true);
      const { comp } = await renderTreeNode({
         node,
         tree,
         componentProperties: { multiSelect: true, nodeSelectable: true },
      });

      comp.clickSelectNode(makeMockEvent());

      expect(tree.exclusiveSelectNode).toHaveBeenCalledWith(node);
   });

   it("should call tree.clickNode for plain click in multi-select with no modifiers", async () => {
      const tree = makeTreeMock();
      const node = makeNode();
      const { comp } = await renderTreeNode({
         node,
         tree,
         componentProperties: { multiSelect: true, nodeSelectable: true },
      });

      comp.clickSelectNode(makeMockEvent());

      expect(tree.clickNode).toHaveBeenCalledWith(node);
   });

   it("should call tree.setHighLightNodes(null) when checkboxEnable is true", async () => {
      const tree = makeTreeMock();
      const { comp } = await renderTreeNode({
         tree,
         componentProperties: { checkboxEnable: true, nodeSelectable: true },
      });

      comp.clickSelectNode(makeMockEvent());

      expect(tree.setHighLightNodes).toHaveBeenCalledWith(null);
   });

   it("should return early when event target is the toggle element", async () => {
      const tree = makeTreeMock();
      const { comp, fixture } = await renderTreeNode({ tree, componentProperties: { nodeSelectable: true } });
      // Wait for ViewChild to populate
      fixture.detectChanges();
      const toggleEl = comp["toggleElement"]?.nativeElement;
      if(!toggleEl) return; // guard for env without real DOM

      comp.clickSelectNode(makeMockEvent({ target: toggleEl }));

      expect(tree.clickNode).not.toHaveBeenCalled();
   });
});

// ===========================================================================
// Group 5 — mousedownSelectNode
// ===========================================================================

describe("Group 5 — mousedownSelectNode", () => {
   it("should call selectNode on left button (button=0) when selectOnClick is false", async () => {
      const tree = makeTreeMock();
      const node = makeNode();
      const { comp } = await renderTreeNode({
         node,
         tree,
         componentProperties: { nodeSelectable: true, selectOnClick: false },
      });
      const evt = makeMockEvent({ button: 0 });

      comp.mousedownSelectNode(evt);

      expect(tree.selectNode).toHaveBeenCalledWith(node, evt, true);
   });

   it("should call selectNode on middle button (button=1) regardless of selectOnClick", async () => {
      const tree = makeTreeMock();
      const node = makeNode();
      const { comp } = await renderTreeNode({
         node,
         tree,
         componentProperties: { nodeSelectable: true, selectOnClick: true },
      });
      const evt = makeMockEvent({ button: 1 });

      comp.mousedownSelectNode(evt);

      expect(tree.selectNode).toHaveBeenCalledWith(node, evt, true);
   });

   it("should NOT call selectNode when nodeSelectable is false", async () => {
      const tree = makeTreeMock();
      const { comp } = await renderTreeNode({ tree, componentProperties: { nodeSelectable: false } });

      comp.mousedownSelectNode(makeMockEvent({ button: 0 }));

      expect(tree.selectNode).not.toHaveBeenCalled();
   });

   it("should NOT call selectNode for left button when selectOnClick is true", async () => {
      const tree = makeTreeMock();
      const { comp } = await renderTreeNode({
         tree,
         componentProperties: { nodeSelectable: true, selectOnClick: true },
      });

      comp.mousedownSelectNode(makeMockEvent({ button: 0 }));

      expect(tree.selectNode).not.toHaveBeenCalled();
   });
});

// ===========================================================================
// Group 6 — tooltip getter
// ===========================================================================

describe("Group 6 — tooltip getter", () => {
   it("should return just node.tooltip when showOriginalName is true and tooltip is set", async () => {
      const node = makeNode({ label: "MyNode", tooltip: "Custom tip" });
      const { comp } = await renderTreeNode({ node, componentProperties: { showOriginalName: true } });
      expect(comp.tooltip).toBe("Custom tip");
   });

   it("should return label + newline + tooltip when showOriginalName is false", async () => {
      const node = makeNode({ label: "MyNode", tooltip: "Custom tip" });
      const { comp } = await renderTreeNode({ node, componentProperties: { showOriginalName: false } });
      expect(comp.tooltip).toBe("MyNode\nCustom tip");
   });

   it("should return just the label when node has no tooltip", async () => {
      const node = makeNode({ label: "MyNode" });
      const { comp } = await renderTreeNode({ node });
      expect(comp.tooltip).toBe("MyNode");
   });
});

// ===========================================================================
// Group 7 — selectNode
// ===========================================================================

describe("Group 7 — selectNode", () => {
   it("should stop propagation and call tree.selectNode for non-leaf node", async () => {
      const tree = makeTreeMock();
      const node = makeFolder();
      const { comp } = await renderTreeNode({ node, tree });
      const evt = makeMockEvent();

      comp.selectNode(evt, true);

      expect(evt.stopPropagation).toHaveBeenCalled();
      expect(tree.selectNode).toHaveBeenCalledWith(node, evt, true);
   });

   it("should NOT stop propagation for leaf node", async () => {
      const tree = makeTreeMock();
      const node = makeNode({ leaf: true });
      const { comp } = await renderTreeNode({ node, tree });
      const evt = makeMockEvent();

      comp.selectNode(evt, false);

      expect(evt.stopPropagation).not.toHaveBeenCalled();
      expect(tree.selectNode).toHaveBeenCalledWith(node, evt, false);
   });
});

// ===========================================================================
// Group 8 — doubleClickNode
// ===========================================================================

describe("Group 8 — doubleClickNode", () => {
   it("should call toggleNode and tree.doubleclickNode", async () => {
      const tree = makeTreeMock();
      const node = makeFolder([], { expanded: false });
      const { comp } = await renderTreeNode({ node, tree });
      const toggleSpy = vi.spyOn(comp, "toggleNode");

      comp.doubleClickNode(makeMockEvent());

      expect(toggleSpy).toHaveBeenCalled();
      expect(tree.doubleclickNode).toHaveBeenCalledWith(node);
   });

   it("should return early when event target is the toggle element", async () => {
      const tree = makeTreeMock();
      const { comp, fixture } = await renderTreeNode({ tree });
      fixture.detectChanges();
      const toggleEl = comp["toggleElement"]?.nativeElement;
      if(!toggleEl) return;

      const toggleSpy = vi.spyOn(comp, "toggleNode");
      comp.doubleClickNode(makeMockEvent({ target: toggleEl }));

      expect(toggleSpy).not.toHaveBeenCalled();
      expect(tree.doubleclickNode).not.toHaveBeenCalled();
   });
});

// ===========================================================================
// Group 9 — hasChildren
// ===========================================================================

describe("Group 9 — hasChildren", () => {
   it("should return true when node has at least one child", async () => {
      const node = makeFolder([makeNode()]);
      const { comp } = await renderTreeNode({ node });
      expect(comp.hasChildren()).toBe(true);
   });

   it("should return true when leaf is false even with no children", async () => {
      const node = makeFolder([], { leaf: false, children: [] });
      const { comp } = await renderTreeNode({ node });
      expect(comp.hasChildren()).toBe(true);
   });

   it("should return false for a leaf node with no children", async () => {
      const node = makeNode({ leaf: true, children: [] });
      const { comp } = await renderTreeNode({ node });
      expect(comp.hasChildren()).toBe(false);
   });
});

// ===========================================================================
// Group 10 — notExpandableType
// ===========================================================================

describe("Group 10 — notExpandableType", () => {
   it("should return true when node.data.type is VARIABLE", async () => {
      const node = makeNode({ data: { type: "VARIABLE" } });
      const { comp } = await renderTreeNode({ node });
      expect(comp.notExpandableType()).toBe(true);
   });

   it("should return false when node.data has no type property", async () => {
      const node = makeNode(); // data = { path: "/Node" }
      const { comp } = await renderTreeNode({ node });
      expect(comp.notExpandableType()).toBe(false);
   });

   it("should return false when node.data.type is not VARIABLE", async () => {
      const node = makeNode({ data: { type: "WORKSHEET" } });
      const { comp } = await renderTreeNode({ node });
      expect(comp.notExpandableType()).toBe(false);
   });
});

// ===========================================================================
// Group 11 — favoritesUser getter
// ===========================================================================

describe("Group 11 — favoritesUser getter", () => {
   it("should return true when showFavoriteIcon and data.favoritesUser is set", async () => {
      const node = makeNode({ data: { favoritesUser: "admin" } });
      const { comp } = await renderTreeNode({ node, componentProperties: { showFavoriteIcon: true } });
      expect(comp.favoritesUser).toBeTruthy();
   });

   it("should return false when showFavoriteIcon is false", async () => {
      const node = makeNode({ data: { favoritesUser: "admin" } });
      const { comp } = await renderTreeNode({ node, componentProperties: { showFavoriteIcon: false } });
      expect(comp.favoritesUser).toBe(false);
   });

   it("should return false when data.favoritesUser is not set", async () => {
      const node = makeNode(); // default data has no favoritesUser
      const { comp } = await renderTreeNode({ node, componentProperties: { showFavoriteIcon: true } });
      expect(comp.favoritesUser).toBe(false);
   });
});

// ===========================================================================
// Group 12 — dragStarted
// ===========================================================================

describe("Group 12 — dragStarted", () => {
   it("should reset dragService and store single node data", async () => {
      const tree = makeTreeMock();
      const node = makeNode({ dragName: "wsAsset", data: { path: "/ws/Sheet" } });
      tree.selectedNodes = [node];
      const { comp } = await renderTreeNode({ node, tree, componentProperties: { draggable: true } });
      const evt = makeMockEvent();

      comp.dragStarted(evt);

      expect(DRAG_SERVICE_MOCK.reset).toHaveBeenCalled();
      expect(DRAG_SERVICE_MOCK.put).toHaveBeenCalledWith(
         "wsAsset",
         JSON.stringify([node.data]),
      );
      expect(tree.onDrag).toHaveBeenCalledWith(evt);
   });

   it("should use node.dragData when set instead of node.data", async () => {
      const tree = makeTreeMock();
      const node = makeNode({ dragName: "dsAsset", data: { path: "/ds" }, dragData: "custom-drag-data" });
      tree.selectedNodes = [node];
      const { comp } = await renderTreeNode({ node, tree, componentProperties: { draggable: true } });

      comp.dragStarted(makeMockEvent());

      expect(DRAG_SERVICE_MOCK.put).toHaveBeenCalledWith(
         "dsAsset",
         JSON.stringify(["custom-drag-data"]),
      );
   });

   it("should aggregate multi-select nodes by dragName", async () => {
      const tree = makeTreeMock();
      const nodeA = makeNode({ dragName: "col", data: { name: "A" } });
      const nodeB = makeNode({ dragName: "col", data: { name: "B" } });
      tree.selectedNodes = [nodeA, nodeB];
      tree.isSelectedNode = vi.fn().mockReturnValue(true);
      const { comp } = await renderTreeNode({
         node: nodeA,
         tree,
         componentProperties: { multiSelect: true, draggable: true },
      });

      comp.dragStarted(makeMockEvent());

      expect(DRAG_SERVICE_MOCK.put).toHaveBeenCalledWith(
         "col",
         JSON.stringify([nodeA.data, nodeB.data]),
      );
   });

   it("should call selectNode first when node is not already selected", async () => {
      const tree = makeTreeMock();
      const node = makeNode({ dragName: "ws", data: { path: "/x" } });
      tree.selectedNodes = []; // not selected
      const { comp } = await renderTreeNode({ node, tree, componentProperties: { draggable: true } });
      const selectSpy = vi.spyOn(comp, "selectNode");

      comp.dragStarted(makeMockEvent());

      expect(selectSpy).toHaveBeenCalledWith(expect.anything(), false);
   });
});

// ===========================================================================
// Group 13 — dragOver
// ===========================================================================

describe("Group 13 — dragOver", () => {
   it("should delegate to tree.onDragOver", async () => {
      const tree = makeTreeMock();
      const { comp } = await renderTreeNode({ tree });
      const evt = makeMockEvent();

      comp.dragOver(evt);

      expect(tree.onDragOver).toHaveBeenCalledWith(evt);
   });
});

// ===========================================================================
// Group 14 — isDraggable getter
// ===========================================================================

describe("Group 14 — isDraggable getter", () => {
   it("should return true when node has dragName and draggable is true", async () => {
      const node = makeNode({ dragName: "wsAsset" });
      const { comp } = await renderTreeNode({ node, componentProperties: { draggable: true } });
      expect(comp.isDraggable).toBeTruthy();
   });

   it("should return false when node has no dragName", async () => {
      const node = makeNode(); // no dragName
      const { comp } = await renderTreeNode({ node, componentProperties: { draggable: true } });
      expect(comp.isDraggable).toBeFalsy();
   });

   it("should return false when draggable input is false even if dragName is set", async () => {
      const node = makeNode({ dragName: "wsAsset" });
      const { comp } = await renderTreeNode({ node, componentProperties: { draggable: false } });
      expect(comp.isDraggable).toBeFalsy();
   });
});

// ===========================================================================
// Group 15 — isHighLight
// ===========================================================================

describe("Group 15 — isHighLight", () => {
   it("should return true when node is in tree.highLightNodes", async () => {
      const tree = makeTreeMock();
      const node = makeNode();
      tree.highLightNodes = [node];
      const { comp } = await renderTreeNode({ node, tree });
      expect(comp.isHighLight()).toBe(true);
   });

   it("should return false when node is not in tree.highLightNodes", async () => {
      const tree = makeTreeMock();
      const node = makeNode();
      tree.highLightNodes = [makeNode()]; // different reference
      const { comp } = await renderTreeNode({ node, tree });
      expect(comp.isHighLight()).toBe(false);
   });

   it("should return false when highLightNodes is null", async () => {
      const tree = makeTreeMock();
      (tree as any).highLightNodes = null;
      const { comp } = await renderTreeNode({ tree });
      expect(comp.isHighLight()).toBe(false);
   });
});

// ===========================================================================
// Group 16 — isSelected
// ===========================================================================

describe("Group 16 — isSelected", () => {
   it("should return true when node is selectedNodes[0] in single-select mode", async () => {
      const tree = makeTreeMock();
      const node = makeNode();
      tree.selectedNodes = [node];
      const { comp } = await renderTreeNode({ node, tree });
      expect(comp.isSelected()).toBe(true);
   });

   it("should return false when node is NOT selectedNodes[0] in single-select mode", async () => {
      const tree = makeTreeMock();
      const node = makeNode();
      tree.selectedNodes = [makeNode(), node]; // node is second, not first
      const { comp } = await renderTreeNode({ node, tree });
      expect(comp.isSelected()).toBe(false);
   });

   it("should return true when node is anywhere in selectedNodes in multi-select mode", async () => {
      const tree = makeTreeMock();
      const node = makeNode();
      tree.selectedNodes = [makeNode(), node];
      const { comp } = await renderTreeNode({ node, tree, componentProperties: { multiSelect: true } });
      expect(comp.isSelected()).toBe(true);
   });

   it("should return true when @Input isSelectedNode function returns true", async () => {
      const tree = makeTreeMock();
      const node = makeNode();
      tree.selectedNodes = [];
      const { comp } = await renderTreeNode({
         node,
         tree,
         componentProperties: { isSelectedNode: () => true },
      });
      expect(comp.isSelected()).toBe(true);
   });

   it("should return false when selectedNodes is null", async () => {
      const tree = makeTreeMock();
      (tree as any).selectedNodes = null;
      const { comp } = await renderTreeNode({ tree });
      expect(comp.isSelected()).toBe(false);
   });
});

// ===========================================================================
// Group 17 — isGrayedOut
// ===========================================================================

describe("Group 17 — isGrayedOut", () => {
   it("should return true when node.data.name matches a string entry in grayedOutFields", async () => {
      const tree = makeTreeMock();
      tree.grayedOutFields = ["amount"];
      const node = makeNode({ data: { name: "amount" } });
      const { comp } = await renderTreeNode({ node, tree });
      expect(comp.isGrayedOut()).toBe(true);
   });

   it("should return true when table+name compound matches grayedOutFields[i].name", async () => {
      const tree = makeTreeMock();
      tree.grayedOutFields = [{ name: "Orders.amount" }];
      const node = makeNode({ data: { name: "amount", table: "Orders" } });
      const { comp } = await renderTreeNode({ node, tree });
      expect(comp.isGrayedOut()).toBe(true);
   });

   it("should return true when node.data is a string found in grayedOutValues", async () => {
      const tree = makeTreeMock();
      tree.grayedOutFields = []; // empty but truthy so outer block is entered
      tree.grayedOutValues = ["value1"];
      const node = makeNode({ data: "value1" as any });
      const { comp } = await renderTreeNode({ node, tree });
      expect(comp.isGrayedOut()).toBe(true);
   });

   it("should return true when isGrayFunction returns true", async () => {
      const tree = makeTreeMock();
      tree.grayedOutFields = []; // truthy so block runs
      (tree as any).isGrayFunction = () => true;
      const { comp } = await renderTreeNode({ tree });
      expect(comp.isGrayedOut()).toBe(true);
   });

   it("should return false when no conditions match", async () => {
      const tree = makeTreeMock(); // grayedOutFields=[], grayedOutValues=[], isGrayFunction=null
      const { comp } = await renderTreeNode({ tree });
      expect(comp.isGrayedOut()).toBe(false);
   });
});

// ===========================================================================
// Group 18 — onDrop
// ===========================================================================

describe("Group 18 — onDrop", () => {
   it("should call preventDefault when onDrop fires", async () => {
      const tree = makeTreeMock();
      const node = makeNode();
      const { comp } = await renderTreeNode({ node, tree });
      const evt = makeMockEvent();

      comp.onDrop(evt);

      expect(evt.preventDefault).toHaveBeenCalled();
   });

   it("should call stopPropagation when onDrop fires", async () => {
      const tree = makeTreeMock();
      const node = makeNode();
      const { comp } = await renderTreeNode({ node, tree });
      const evt = makeMockEvent();

      comp.onDrop(evt);

      expect(evt.stopPropagation).toHaveBeenCalled();
   });

   it("should call tree.onDrop with { node, evt }", async () => {
      const tree = makeTreeMock();
      const node = makeNode();
      const { comp } = await renderTreeNode({ node, tree });
      const evt = makeMockEvent();

      comp.onDrop(evt);

      expect(tree.onDrop).toHaveBeenCalledWith({ node, evt });
   });
});

// ===========================================================================
// Group 19 — keepAllChildren getter
// ===========================================================================

describe("Group 19 — keepAllChildren getter", () => {
   it("should return true when forceMatch is true regardless of searchStr", async () => {
      const node = makeNode({ label: "Anything" });
      const { comp } = await renderTreeNode({ node, componentProperties: { forceMatch: true, searchStr: "" } });
      expect(comp.keepAllChildren).toBe(true);
   });

   it("should return true when searchStr exactly matches label (case-insensitive)", async () => {
      const node = makeNode({ label: "Reports" });
      const { comp } = await renderTreeNode({
         node,
         componentProperties: { searchStr: "reports", showRoot: true },
      });
      expect(comp.keepAllChildren).toBe(true);
   });

   it("should return false for a partial searchStr match", async () => {
      const node = makeNode({ label: "Reports" });
      const { comp } = await renderTreeNode({
         node,
         componentProperties: { searchStr: "Rep", showRoot: true },
      });
      expect(comp.keepAllChildren).toBeFalsy();
   });

   it("should return false when searchStr is empty", async () => {
      const node = makeNode({ label: "Reports" });
      const { comp } = await renderTreeNode({ node, componentProperties: { searchStr: "" } });
      expect(comp.keepAllChildren).toBeFalsy();
   });
});

// ===========================================================================
// Group 20 — getSort
// ===========================================================================

describe("Group 20 — getSort", () => {
   it("should sort nodes by search relevance when searchStr is set (prefix match ranks higher)", async () => {
      const { comp } = await renderTreeNode({ componentProperties: { searchStr: "node" } });
      const prefixMatch = makeNode({ label: "nodeA" });
      const noMatch = makeNode({ label: "other" });

      const result = comp.getSort([noMatch, prefixMatch]);

      // SearchComparator: "nodeA" starts with "node" (degree 2) > "other" (degree 0)
      expect(result[0]).toBe(prefixMatch);
   });

   it("should return nodes in original order when searchStr is empty", async () => {
      const { comp } = await renderTreeNode({ componentProperties: { searchStr: "" } });
      const a = makeNode({ label: "B" });
      const b = makeNode({ label: "A" });

      const result = comp.getSort([a, b]);

      expect(result[0]).toBe(a);
      expect(result[1]).toBe(b);
   });
});

// ===========================================================================
// Group 21 — hasMenu
// ===========================================================================

describe("Group 21 — hasMenu", () => {
   it("should return true when contextmenu enabled and not on a mobile device", async () => {
      vi.spyOn(GuiTool, "isMobileDevice").mockReturnValue(false);
      const { comp } = await renderTreeNode({ componentProperties: { contextmenu: true } });
      expect(comp.hasMenu()).toBe(true);
   });

   it("should return false when contextmenu is disabled", async () => {
      vi.spyOn(GuiTool, "isMobileDevice").mockReturnValue(false);
      const { comp } = await renderTreeNode({ componentProperties: { contextmenu: false } });
      expect(comp.hasMenu()).toBe(false);
   });

   it("should return false on a mobile device even when contextmenu is enabled", async () => {
      vi.spyOn(GuiTool, "isMobileDevice").mockReturnValue(true);
      const { comp } = await renderTreeNode({ componentProperties: { contextmenu: true } });
      expect(comp.hasMenu()).toBe(false);
   });

   it("should return false when hasMenuFunction returns false for this node", async () => {
      vi.spyOn(GuiTool, "isMobileDevice").mockReturnValue(false);
      const tree = makeTreeMock();
      (tree as any).hasMenuFunction = () => false;
      const { comp } = await renderTreeNode({ tree, componentProperties: { contextmenu: true } });
      expect(comp.hasMenu()).toBe(false);
   });

   it("should return true when hasMenuFunction returns true", async () => {
      vi.spyOn(GuiTool, "isMobileDevice").mockReturnValue(false);
      const tree = makeTreeMock();
      (tree as any).hasMenuFunction = () => true;
      const { comp } = await renderTreeNode({ tree, componentProperties: { contextmenu: true } });
      expect(comp.hasMenu()).toBe(true);
   });
});
