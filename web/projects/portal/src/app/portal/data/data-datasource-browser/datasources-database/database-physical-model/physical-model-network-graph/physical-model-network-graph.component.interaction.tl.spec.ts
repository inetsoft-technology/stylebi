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
 * PhysicalModelNetworkGraphComponent — Pass 1 (interaction / selection / keyboard)
 *
 * Risk-first coverage:
 *   Group 1 [Risk 3] — selectNode(): already-selected guard; ctrl/shift append; plain replace;
 *                        emits correct treeLinks via onNodeSelected
 *   Group 2 [Risk 2] — selectAll(), onSelectionBox(), clearSelection()
 *   Group 3 [Risk 2] — removeSelectTables(): no-confirm path; confirm path (ok / cancel);
 *                        baseTable exclusion
 *   Group 4 [Risk 2] — keydown(): Ctrl+A → selectAll; Delete → removeSelectTables; other → no-op
 *   Group 5 [Risk 2] — getThumbnailClasses(): selected, unselected, dimmed
 *   Group 6 [Risk 1] — clearTableEnabled, clearJoinEnabled, getSelectedNode(), graphEndpoints,
 *                        scale getter/setter, ngOnChanges()
 *
 * jsPlumb mocking strategy:
 *   The ATL runner (esbuild) pre-bundles deps before test time; vi.mock() is silently
 *   ignored for npm packages too. renderComp() initializes with the real jsPlumb instance
 *   (which works in jsdom), then replaces comp.jsp = jspMock so all assertions on
 *   mock.calls work.  jspMock is exported from test-helpers and its call histories are
 *   cleared at the start of each renderComp() call.
 *
 * refreshDragSelection() isolation:
 *   Tests that assert on dragNodes spy on refreshDragSelection() with mockImplementation(() => {})
 *   so the internal sourceIds/nodes maps do not prune dragNodes unexpectedly.
 */

import { Rectangle } from "../../../../../../common/data/rectangle";
import { ComponentTool } from "../../../../../../common/util/component-tool";
import { MessageDialog } from "../../../../../../widget/dialog/message-dialog/message-dialog.component";
import {
   jspMock, makeGraph, makeViewModel, renderComp, setupNodes,
} from "./physical-model-network-graph.component.test-helpers";

// ---------------------------------------------------------------------------
// Global lifecycle
// ---------------------------------------------------------------------------

afterEach(() => {
   vi.restoreAllMocks();
});

// ---------------------------------------------------------------------------
// Group 1 — selectNode() [Risk 3]
// ---------------------------------------------------------------------------

describe("PhysicalModelNetworkGraphComponent — selectNode()", () => {
   // 🔁 Regression-sensitive: if the already-selected guard is removed, ctrl-clicking a selected
   // node duplicates it in dragNodes — later removeSelectTables() removes it twice.

   it("should not duplicate a graph that is already in dragNodes", async () => {
      const g = makeGraph("orders");
      const { comp } = await renderComp({ graphViewModel: makeViewModel([g]) });
      vi.spyOn(comp as any, "refreshDragSelection").mockImplementation(() => {});
      // dragNodes has no public setter — bypass to prime selection state for this test
      (comp as any).dragNodes = [g];

      comp.selectNode({ ctrlKey: false, shiftKey: false } as MouseEvent, g);

      expect((comp as any).dragNodes).toHaveLength(1);
   });

   it("should replace dragNodes when plain-clicking an unselected node", async () => {
      const g1 = makeGraph("t1");
      const g2 = makeGraph("t2");
      const { comp } = await renderComp({ graphViewModel: makeViewModel([g1, g2]) });
      vi.spyOn(comp as any, "refreshDragSelection").mockImplementation(() => {});
      (comp as any).dragNodes = [g1];

      comp.selectNode({ ctrlKey: false, shiftKey: false } as MouseEvent, g2);

      expect((comp as any).dragNodes).toEqual([g2]);
   });

   it("should append to dragNodes when ctrl-clicking an unselected node", async () => {
      const g1 = makeGraph("t1");
      const g2 = makeGraph("t2");
      const { comp } = await renderComp({ graphViewModel: makeViewModel([g1, g2]) });
      vi.spyOn(comp as any, "refreshDragSelection").mockImplementation(() => {});
      (comp as any).dragNodes = [g1];

      comp.selectNode({ ctrlKey: true, shiftKey: false } as MouseEvent, g2);

      expect((comp as any).dragNodes).toEqual([g1, g2]);
   });

   it("should append to dragNodes when shift-clicking an unselected node", async () => {
      const g1 = makeGraph("t1");
      const g2 = makeGraph("t2");
      const { comp } = await renderComp({ graphViewModel: makeViewModel([g1, g2]) });
      vi.spyOn(comp as any, "refreshDragSelection").mockImplementation(() => {});
      (comp as any).dragNodes = [g1];

      comp.selectNode({ ctrlKey: false, shiftKey: true } as MouseEvent, g2);

      expect((comp as any).dragNodes).toEqual([g1, g2]);
   });

   it("should emit onNodeSelected with treeLinks of all dragNodes after selection", async () => {
      const g1 = makeGraph("t1");
      const g2 = makeGraph("t2");
      const { comp, onNodeSelectedSpy } = await renderComp({ graphViewModel: makeViewModel([g1, g2]) });
      vi.spyOn(comp as any, "refreshDragSelection").mockImplementation(() => {});
      (comp as any).dragNodes = [g1];

      comp.selectNode({ ctrlKey: true, shiftKey: false } as MouseEvent, g2);

      expect(onNodeSelectedSpy).toHaveBeenCalledWith(
         [g1.node.treeLink, g2.node.treeLink]
      );
   });
});

// ---------------------------------------------------------------------------
// Group 2 — selectAll(), onSelectionBox(), clearSelection() [Risk 2]
// ---------------------------------------------------------------------------

describe("PhysicalModelNetworkGraphComponent — selectAll()", () => {
   it("should set dragNodes to all graphs in graphViewModel", async () => {
      const graphs = [makeGraph("t1"), makeGraph("t2"), makeGraph("t3")];
      const { comp } = await renderComp({ graphViewModel: makeViewModel(graphs) });
      vi.spyOn(comp as any, "refreshDragSelection").mockImplementation(() => {});

      comp.selectAll();

      expect((comp as any).dragNodes).toBe(graphs);
   });

   it("should emit onNodeSelected with all treeLinks after selectAll()", async () => {
      const graphs = [makeGraph("t1"), makeGraph("t2")];
      const { comp, onNodeSelectedSpy } = await renderComp({ graphViewModel: makeViewModel(graphs) });
      vi.spyOn(comp as any, "refreshDragSelection").mockImplementation(() => {});

      comp.selectAll();

      expect(onNodeSelectedSpy).toHaveBeenCalledWith(
         graphs.map(g => g.node.treeLink)
      );
   });
});

describe("PhysicalModelNetworkGraphComponent — onSelectionBox()", () => {
   it("should select only graphs whose bounds intersect the selection box", async () => {
      // g1 at (0,0,100,50): intersects box(0,0,50,50)
      // g2 at (200,200,100,50): does not
      const g1 = makeGraph("t1");
      g1.bounds = new Rectangle(0, 0, 100, 50);
      const g2 = makeGraph("t2");
      g2.bounds = new Rectangle(200, 200, 100, 50);
      const { comp, onNodeSelectedSpy } = await renderComp({ graphViewModel: makeViewModel([g1, g2]) });
      vi.spyOn(comp as any, "refreshDragSelection").mockImplementation(() => {});

      comp.onSelectionBox({ box: new Rectangle(0, 0, 50, 50) } as any);

      expect((comp as any).dragNodes).toEqual([g1]);
      expect(onNodeSelectedSpy).toHaveBeenCalledWith([g1.node.treeLink]);
   });

   it("should clear dragNodes when no graphs intersect the selection box", async () => {
      const g1 = makeGraph("t1");
      g1.bounds = new Rectangle(200, 200, 100, 50);
      const { comp, onNodeSelectedSpy } = await renderComp({ graphViewModel: makeViewModel([g1]) });
      vi.spyOn(comp as any, "refreshDragSelection").mockImplementation(() => {});
      (comp as any).dragNodes = [g1];

      comp.onSelectionBox({ box: new Rectangle(0, 0, 10, 10) } as any);

      expect((comp as any).dragNodes).toHaveLength(0);
      expect(onNodeSelectedSpy).toHaveBeenCalledWith([]);
   });
});

describe("PhysicalModelNetworkGraphComponent — clearSelection()", () => {
   it("should clear dragNodes when target === currentTarget and dragNodes non-empty", async () => {
      const g = makeGraph("t1");
      const { comp, onNodeSelectedSpy } = await renderComp({ graphViewModel: makeViewModel([g]) });
      (comp as any).dragNodes = [g];
      const target = {};
      const event = { target, currentTarget: target } as any;

      comp.clearSelection(event);

      expect((comp as any).dragNodes).toHaveLength(0);
      expect(onNodeSelectedSpy).toHaveBeenCalledWith([]);
   });

   it("should do nothing when target !== currentTarget", async () => {
      const g = makeGraph("t1");
      const { comp, onNodeSelectedSpy } = await renderComp({ graphViewModel: makeViewModel([g]) });
      (comp as any).dragNodes = [g];
      const event = { target: {}, currentTarget: {} } as any;

      comp.clearSelection(event);

      expect((comp as any).dragNodes).toHaveLength(1);
      expect(onNodeSelectedSpy).not.toHaveBeenCalled();
   });

   it("should do nothing when dragNodes is empty even if target matches", async () => {
      const { comp, onNodeSelectedSpy } = await renderComp();
      const target = {};
      const event = { target, currentTarget: target } as any;

      comp.clearSelection(event);

      expect(onNodeSelectedSpy).not.toHaveBeenCalled();
   });
});

// ---------------------------------------------------------------------------
// Group 3 — removeSelectTables() [Risk 2]
// ---------------------------------------------------------------------------

describe("PhysicalModelNetworkGraphComponent — removeSelectTables()", () => {
   // 🔁 Regression-sensitive: when showConfirm=true, cancelling the dialog must NOT call doRemove.
   // A missing `ok === result` check would always delete.

   // C4: showConfirmDialog routes through showMessageDialog which has a 500ms dedup guard.
   // Reset between tests to prevent consecutive tests with the same message from silently no-op-ing.
   beforeEach(() => {
      MessageDialog.lastMessage = null;
      (MessageDialog as any).lastMessageTS = 0;
   });

   it("should call doRemove directly (no confirm) when nodes have no alias/sql/edge", async () => {
      const g = makeGraph("plain");
      const { comp } = await renderComp({ graphViewModel: makeViewModel([g]) });
      vi.spyOn(comp as any, "refreshDragSelection").mockImplementation(() => {});
      (comp as any).dragNodes = [g];
      const doRemoveSpy = vi.spyOn(comp, "doRemove").mockImplementation(() => {});

      comp.removeSelectTables();

      expect(doRemoveSpy).toHaveBeenCalledWith(
         expect.objectContaining({ runtimeId: "rt-123", tables: [expect.objectContaining({ tableName: "plain" })] })
      );
   });

   it("should show confirm dialog when a node has alias=true", async () => {
      const g = makeGraph("alias-node", { alias: true } as any);
      const { comp } = await renderComp({ graphViewModel: makeViewModel([g]) });
      vi.spyOn(comp as any, "refreshDragSelection").mockImplementation(() => {});
      (comp as any).dragNodes = [g];
      const confirmSpy = vi.spyOn(ComponentTool, "showConfirmDialog").mockResolvedValue("ok");
      vi.spyOn(comp, "doRemove").mockImplementation(() => {});

      comp.removeSelectTables();

      expect(confirmSpy).toHaveBeenCalledWith(
         expect.anything(),
         "_#(js:Warning)",
         "_#(js:data.physicalmodel.confirmRemoveTable)"
      );
   });

   it("should call doRemove when confirm returns ok", async () => {
      const g = makeGraph("has-edge");
      g.edge.input.push({ id: "t2" } as any);
      const { comp } = await renderComp({ graphViewModel: makeViewModel([g]) });
      vi.spyOn(comp as any, "refreshDragSelection").mockImplementation(() => {});
      (comp as any).dragNodes = [g];
      vi.spyOn(ComponentTool, "showConfirmDialog").mockResolvedValue("ok");
      const doRemoveSpy = vi.spyOn(comp, "doRemove").mockImplementation(() => {});

      comp.removeSelectTables();

      await vi.waitFor(() => expect(doRemoveSpy).toHaveBeenCalledWith(
         expect.objectContaining({ runtimeId: "rt-123", tables: [expect.objectContaining({ tableName: "has-edge" })] })
      ));
   });

   it("should NOT call doRemove when confirm returns cancel", async () => {
      const g = makeGraph("has-edge");
      g.edge.output.push({ id: "t3" } as any);
      const { comp } = await renderComp({ graphViewModel: makeViewModel([g]) });
      vi.spyOn(comp as any, "refreshDragSelection").mockImplementation(() => {});
      (comp as any).dragNodes = [g];
      const confirmSpy = vi.spyOn(ComponentTool, "showConfirmDialog").mockResolvedValue("cancel");
      const doRemoveSpy = vi.spyOn(comp, "doRemove").mockImplementation(() => {});

      comp.removeSelectTables();

      // showConfirmDialog is called synchronously; wait for it then let the cancel branch settle
      await vi.waitFor(() => expect(confirmSpy).toHaveBeenCalled());
      await Promise.resolve();
      expect(doRemoveSpy).not.toHaveBeenCalled();
   });

   it("should exclude baseTable nodes from the remove event tables list", async () => {
      const base = makeGraph("base-tbl", { baseTable: true } as any);
      const plain = makeGraph("plain-tbl");
      const { comp } = await renderComp({ graphViewModel: makeViewModel([base, plain]) });
      vi.spyOn(comp as any, "refreshDragSelection").mockImplementation(() => {});
      (comp as any).dragNodes = [base, plain];
      const doRemoveSpy = vi.spyOn(comp, "doRemove").mockImplementation(() => {});

      comp.removeSelectTables();

      const event = doRemoveSpy.mock.calls[0][0];
      expect(event.tables).toHaveLength(1);
      expect(event.tables[0].tableName).toBe("plain-tbl");
   });
});

// ---------------------------------------------------------------------------
// Group 4 — keydown() [Risk 2]
// ---------------------------------------------------------------------------

describe("PhysicalModelNetworkGraphComponent — keydown()", () => {
   it("should call selectAll() on Ctrl+A", async () => {
      const { comp } = await renderComp();
      const selectAllSpy = vi.spyOn(comp, "selectAll").mockImplementation(() => {});
      const event = new KeyboardEvent("keydown", { key: "a", ctrlKey: true });

      comp.keydown(event);

      expect(selectAllSpy).toHaveBeenCalledTimes(1);
   });

   it("should call removeSelectTables() on Delete", async () => {
      const { comp } = await renderComp();
      const removeSpy = vi.spyOn(comp, "removeSelectTables").mockImplementation(() => {});
      const event = new KeyboardEvent("keydown", { key: "Delete" });

      comp.keydown(event);

      expect(removeSpy).toHaveBeenCalledTimes(1);
   });

   it("should not call selectAll or removeSelectTables on other keys", async () => {
      const { comp } = await renderComp();
      const selectAllSpy = vi.spyOn(comp, "selectAll").mockImplementation(() => {});
      const removeSpy = vi.spyOn(comp, "removeSelectTables").mockImplementation(() => {});
      const event = new KeyboardEvent("keydown", { key: "ArrowUp" });

      comp.keydown(event);

      expect(selectAllSpy).not.toHaveBeenCalled();
      expect(removeSpy).not.toHaveBeenCalled();
   });
});

// ---------------------------------------------------------------------------
// Group 5 — getThumbnailClasses() [Risk 2]
// ---------------------------------------------------------------------------

describe("PhysicalModelNetworkGraphComponent — getThumbnailClasses()", () => {
   it("should return selected=true when graph is in dragNodes", async () => {
      const g = makeGraph("t1");
      const { comp } = await renderComp();
      (comp as any).dragNodes = [g];

      const classes = comp.getThumbnailClasses(g);

      expect(classes["ws-assembly-graph-element--selected"]).toBe(true);
      expect(classes["ws-assembly-graph-element--dimmed"]).toBe(false);
   });

   it("should return selected=false, dimmed=false when not selected and not moving", async () => {
      const g = makeGraph("t1");
      const other = makeGraph("t2");
      const { comp } = await renderComp();
      (comp as any).dragNodes = [other];
      // nodeMoving has no public setter — bypass to test the dimmed=false branch
      (comp as any).nodeMoving = false;

      const classes = comp.getThumbnailClasses(g);

      expect(classes["ws-assembly-graph-element--selected"]).toBe(false);
      expect(classes["ws-assembly-graph-element--dimmed"]).toBe(false);
   });

   it("should return dimmed=true when not selected and nodeMoving=true", async () => {
      const g = makeGraph("t1");
      const other = makeGraph("t2");
      const { comp } = await renderComp();
      (comp as any).dragNodes = [other];
      (comp as any).nodeMoving = true;

      const classes = comp.getThumbnailClasses(g);

      expect(classes["ws-assembly-graph-element--dimmed"]).toBe(true);
   });

   it("should return dimmed=false for the selected node even when nodeMoving=true", async () => {
      const g = makeGraph("t1");
      const { comp } = await renderComp();
      (comp as any).dragNodes = [g];
      (comp as any).nodeMoving = true;

      const classes = comp.getThumbnailClasses(g);

      expect(classes["ws-assembly-graph-element--selected"]).toBe(true);
      expect(classes["ws-assembly-graph-element--dimmed"]).toBe(false);
   });
});

// ---------------------------------------------------------------------------
// Group 6 — getters, scale, ngOnChanges [Risk 1]
// ---------------------------------------------------------------------------

describe("PhysicalModelNetworkGraphComponent — clearTableEnabled / clearJoinEnabled", () => {
   it("clearTableEnabled should be true when graphViewModel has graphs", async () => {
      const { comp } = await renderComp({ graphViewModel: makeViewModel([makeGraph("t1")]) });
      expect(comp.clearTableEnabled).toBe(true);
   });

   it("clearTableEnabled should be false when graphViewModel is empty", async () => {
      const { comp } = await renderComp({ graphViewModel: makeViewModel([]) });
      expect(comp.clearTableEnabled).toBe(false);
   });

   it("clearJoinEnabled should be true when some graph has edge.input entries", async () => {
      const g = makeGraph("t1");
      g.edge.input.push({ id: "t2" } as any);
      const { comp } = await renderComp({ graphViewModel: makeViewModel([g]) });
      expect(comp.clearJoinEnabled).toBe(true);
   });

   it("clearJoinEnabled should be false when all edges are empty", async () => {
      const g = makeGraph("t1");
      const { comp } = await renderComp({ graphViewModel: makeViewModel([g]) });
      expect(comp.clearJoinEnabled).toBe(false);
   });
});

describe("PhysicalModelNetworkGraphComponent — getSelectedNode()", () => {
   it("should return true when the node is in dragNodes", async () => {
      const g = makeGraph("t1");
      const { comp } = await renderComp();
      (comp as any).dragNodes = [g];
      expect(comp.getSelectedNode(g.node)).toBe(true);
   });

   it("should return false when the node is not in dragNodes", async () => {
      const g = makeGraph("t1");
      const other = makeGraph("t2");
      const { comp } = await renderComp();
      (comp as any).dragNodes = [g];
      expect(comp.getSelectedNode(other.node)).toBe(false);
   });

   it("should return false when dragNodes is empty", async () => {
      const g = makeGraph("t1");
      const { comp } = await renderComp();
      (comp as any).dragNodes = [];
      expect(comp.getSelectedNode(g.node)).toBe(false);
   });
});

describe("PhysicalModelNetworkGraphComponent — graphEndpoints, scale", () => {
   it("graphEndpoints should return an array", async () => {
      const { comp } = await renderComp();
      expect(Array.isArray(comp.graphEndpoints)).toBe(true);
   });

   it("scale setter should update _scale and call jsp.setZoom", async () => {
      const { comp } = await renderComp();

      comp.scale = 1.5;

      expect(comp._scale).toBe(1.5);
      expect(jspMock.setZoom).toHaveBeenLastCalledWith(1.5);
   });

   it("scale getter should return _scale", async () => {
      const { comp } = await renderComp();
      comp._scale = 2.0;
      expect(comp.scale).toBe(2.0);
   });
});

describe("PhysicalModelNetworkGraphComponent — ngOnChanges()", () => {
   it("should reset nodes and sourceIds maps when graphViewModel changes", async () => {
      const g1 = makeGraph("t1");
      const { comp } = await renderComp({ graphViewModel: makeViewModel([g1]) });
      setupNodes(comp, [g1]);
      // nodes/sourceIds have no public accessors — bypass to confirm pre-change state then verify reset
      expect(Object.keys((comp as any).nodes).length).toBeGreaterThan(0);

      // Simulate graphViewModel SimpleChange
      const callsBefore = jspMock.deleteEveryConnection.mock.calls.length;
      const endpointCallsBefore = jspMock.deleteEveryEndpoint.mock.calls.length;
      comp.ngOnChanges({ graphViewModel: { currentValue: makeViewModel(), previousValue: makeViewModel([g1]) } } as any);

      expect((comp as any).nodes).toEqual({});
      expect((comp as any).sourceIds).toEqual({});
      // deleteEveryConnection/Endpoint called at least one more time than before
      expect(jspMock.deleteEveryConnection.mock.calls.length).toBeGreaterThan(callsBefore);
      expect(jspMock.deleteEveryEndpoint.mock.calls.length).toBeGreaterThan(endpointCallsBefore);
   });

   it("should update dragNodes when selectedGraphModels changes", async () => {
      const g1 = makeGraph("t1");
      const g2 = makeGraph("t2");
      const { comp } = await renderComp({ graphViewModel: makeViewModel([g1, g2]) });

      // Must set the @Input property directly; ngOnChanges reads this.selectedGraphModels
      comp.selectedGraphModels = [g1, g2];
      comp.ngOnChanges({
         selectedGraphModels: { currentValue: [g1, g2], previousValue: [] },
      } as any);

      expect((comp as any).dragNodes).toEqual([g1, g2]);
   });

   it("should NOT reset nodes when an unrelated input changes", async () => {
      const g1 = makeGraph("t1");
      const { comp } = await renderComp({ graphViewModel: makeViewModel([g1]) });
      setupNodes(comp, [g1]);
      const callsBefore = jspMock.deleteEveryConnection.mock.calls.length;

      comp.ngOnChanges({ highlightConnections: { currentValue: [], previousValue: [] } } as any);

      // No new call to deleteEveryConnection from this unrelated change
      expect(jspMock.deleteEveryConnection.mock.calls.length).toBe(callsBefore);
   });
});
