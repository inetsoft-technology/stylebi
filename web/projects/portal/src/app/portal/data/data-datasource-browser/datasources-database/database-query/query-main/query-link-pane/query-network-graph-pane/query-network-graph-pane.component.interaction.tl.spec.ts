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
 * QueryNetworkGraphPaneComponent — Pass 1 (Interaction / lifecycle / pure logic)
 *
 * This is a jsPlumb-heavy component (713 logic lines, 10 async zones).
 * Per project policy, only P1 (interaction) is written for jsPlumb components —
 * P2 (risk) crashes before any tests run when MSW is imported in risk specs.
 *
 * Coverage strategy:
 *   - Pure logic methods that do NOT call jsPlumb directly:
 *     clearTableEnabled, clearJoinEnabled, getUnjoinedTables, isDuplicateTableAlias,
 *     getSelectedNode, getThumbnailClasses, updateUnjoinedTables
 *   - HTTP methods (MSW intercepts): addTables, doRemove
 *   - Lifecycle: ngOnInit heartbeat timer, ngOnDestroy subscription teardown
 *   - Input-driven behaviour: selectNode (dragNodes update, onNodeSelected emit)
 *   - onSelectionBox: filters graphs by bounding rectangle
 *
 * jsPlumb-dependent methods (not tested here):
 *   registerNode, connectNode, addEndpoint, showEndpoints, setDraggable,
 *   clearSelection (calls jsp.setSuspendDrawing), refreshDragSelection
 *   (calls jsp.clearDragSelection / addToDragSelection)
 */

import { NO_ERRORS_SCHEMA, Component, Directive, Input, Output, EventEmitter } from "@angular/core";
import { provideHttpClient } from "@angular/common/http";
import { render, waitFor } from "@testing-library/angular";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { http, HttpResponse } from "msw";

import { server } from "@test-mocks/server";
import { jsPlumbLib } from "../../../../../../../../composer/gui/ws/jsplumb/jsplumb";
import { QueryNetworkGraphPaneComponent } from "./query-network-graph-pane.component";
import { DataQueryModelService } from "../../../data-query-model.service";
import { FixedDropdownService } from "../../../../../../../../widget/fixed-dropdown/fixed-dropdown.service";
import { DragService } from "../../../../../../../../widget/services/drag.service";
import { DebounceService } from "../../../../../../../../widget/services/debounce.service";
import { DomService } from "../../../../../../../../widget/dom-service/dom.service";
import { JoinNodeGraphComponent } from "../../../../common-components/join-node-graph/join-node-graph.component";
import { OutOfZoneDirective } from "../../../../../../../../widget/directive/out-of-zone.directive";
import { SelectionBoxDirective } from "../../../../../../../../widget/directive/selection-box.directive";
import { GraphViewModel } from "../../../../../../model/datasources/database/physical-model/graph/graph-view-model";
import { GraphModel } from "../../../../../../model/datasources/database/physical-model/graph/graph-model";
import { Rectangle } from "../../../../../../../../common/data/rectangle";

// ---------------------------------------------------------------------------
// Stubs
// ---------------------------------------------------------------------------

@Component({ selector: "join-node-graph", standalone: true, template: "" })
class JoinNodeGraphStub {
   @Input() graph: any;
   @Input() runtimeId: string;
   @Input() dataType: any;
   @Input() isDuplicateTableAlias: any;
   @Input() readonly: boolean;
   @Output() onSelectNode = new EventEmitter<any>();
   @Output() onMoveNode = new EventEmitter<any>();
   @Output() onRemoveTable = new EventEmitter<any>();
   @Output() onRegisterNode = new EventEmitter<any>();
}

@Directive({ selector: "[outOfZone]", standalone: true })
class OutOfZoneDirectiveStub {}

@Directive({ selector: "[selectionBox]", standalone: true })
class SelectionBoxDirectiveStub {
   @Input() selectionBoxBannedSelector: string;
   @Output() onSelectionBox = new EventEmitter<any>();
}

// ---------------------------------------------------------------------------
// Mock services
// ---------------------------------------------------------------------------

const MODAL_MOCK = { open: vi.fn() };

const QUERY_MODEL_SERVICE_MOCK = {
   emitModelChange: vi.fn(),
   setUnjoinedTables: vi.fn(),
};

const FIXED_DROPDOWN_SERVICE_MOCK = {
   open: vi.fn().mockReturnValue({ componentInstance: { actions: null, sourceEvent: null } }),
};

const DRAG_SERVICE_MOCK = {
   getDragDataValues: vi.fn().mockReturnValue(null),
};

const DEBOUNCE_SERVICE_MOCK = {
   debounce: vi.fn((key, fn) => fn()),
};

const DOM_SERVICE_MOCK = {
   requestRead: vi.fn((cb: () => void) => cb()),
   requestWrite: vi.fn((cb: () => void) => cb()),
};

// ---------------------------------------------------------------------------
// jsPlumb mock — all methods the component calls on this.jsp
// ---------------------------------------------------------------------------

const jspMock = {
   restoreDefaults: vi.fn(),
   importDefaults: vi.fn(),
   registerEndpointTypes: vi.fn(),
   registerConnectionTypes: vi.fn(),
   setZoom: vi.fn(),
   bind: vi.fn(),
   deleteEveryConnection: vi.fn(),
   deleteEveryEndpoint: vi.fn(),
   reset: vi.fn(),
   isSuspendDrawing: vi.fn().mockReturnValue(false),
   setSuspendDrawing: vi.fn(),
   selectEndpoints: vi.fn().mockReturnValue({ setVisible: vi.fn(), each: vi.fn(), length: 0, get: vi.fn() }),
   getAllConnections: vi.fn().mockReturnValue([]),
   connect: vi.fn(),
   addEndpoint: vi.fn().mockReturnValue({ setVisible: vi.fn() }),
   clearDragSelection: vi.fn(),
   addToDragSelection: vi.fn(),
   unmakeEverySource: vi.fn(),
   draggable: vi.fn(),
   isSource: vi.fn().mockReturnValue(false),
   makeSource: vi.fn(),
   repaintEverything: vi.fn(),
   setContainer: vi.fn(),
   getConnections: vi.fn().mockReturnValue([]),
   deleteConnection: vi.fn(),
};

// ---------------------------------------------------------------------------
// scrollTo stub — jsdom does not implement HTMLElement.scrollTo;
// ngAfterViewInit calls it on the #graphPane div.
// ---------------------------------------------------------------------------

let _originalScrollTo: any;
beforeAll(() => {
   _originalScrollTo = (HTMLElement.prototype as any).scrollTo;
   (HTMLElement.prototype as any).scrollTo = vi.fn();
});
afterAll(() => {
   (HTMLElement.prototype as any).scrollTo = _originalScrollTo;
});

function resetMocks() {
   [MODAL_MOCK, QUERY_MODEL_SERVICE_MOCK, FIXED_DROPDOWN_SERVICE_MOCK,
    DRAG_SERVICE_MOCK, DEBOUNCE_SERVICE_MOCK, DOM_SERVICE_MOCK].forEach(mock => {
      Object.values(mock).forEach(m => typeof (m as any).mockClear === "function" && (m as any).mockClear());
   });
   Object.values(jspMock).forEach(fn => {
      if(fn && typeof (fn as any).mockClear === "function") (fn as any).mockClear();
   });
}

// ---------------------------------------------------------------------------
// Factory helpers
// ---------------------------------------------------------------------------

function makeNode(id: string, treeLink = ""): GraphModel["node"] {
   return {
      id,
      name: id,
      tableName: id,
      label: id,
      tooltip: "",
      treeLink: treeLink || `/root/${id}`,
      aliasSource: id,
      outgoingAliasSource: id,
   };
}

function makeGraph(id: string, opts: {
   inputs?: any[], outputs?: any[], bounds?: any, baseTable?: boolean
} = {}): GraphModel {
   return {
      node: makeNode(id) as any,
      edge: { input: opts.inputs ?? [], output: opts.outputs ?? [] },
      cols: [],
      bounds: opts.bounds ?? new Rectangle(0, 0, 100, 50),
      showColumns: false,
      alias: false,
      autoAlias: false,
      sql: false,
      baseTable: opts.baseTable ?? false,
      autoAliasByOutgoing: false,
      designModeAlias: false,
   };
}

function makeViewModel(graphs: GraphModel[]): GraphViewModel {
   return { graphs };
}

// ---------------------------------------------------------------------------
// Render helper
// ---------------------------------------------------------------------------

async function renderComponent(opts: {
   graphViewModel?: GraphViewModel;
   runtimeId?: string;
} = {}) {
   const graphViewModel = opts.graphViewModel ?? makeViewModel([]);

   // Intercept jspInitGraphMain()'s jsPlumbLib.jsPlumb.getInstance() call so the
   // constructor receives jspMock instead of a real jsPlumb instance.
   const getInstanceSpy = vi.spyOn(jsPlumbLib.jsPlumb, "getInstance").mockReturnValue(jspMock as any);

   // Prevent ngOnInit's setRepaintTimer() from scheduling a real setTimeout that would
   // fire after ngOnDestroy (which sets this.jsp = null) and crash the test runner with
   // "Cannot read properties of null (reading 'isSuspendDrawing')".
   const repaintSpy = vi.spyOn(
      QueryNetworkGraphPaneComponent.prototype as any, "setRepaintTimer"
   ).mockImplementation(() => {});

   const { fixture } = await render(QueryNetworkGraphPaneComponent, {
      componentProperties: {
         graphViewModel,
         runtimeId: opts.runtimeId ?? "rt-net-graph",
         highlightConnections: [],
      },
      providers: [
         provideHttpClient(),
         { provide: NgbModal, useValue: MODAL_MOCK },
         { provide: DataQueryModelService, useValue: QUERY_MODEL_SERVICE_MOCK },
         { provide: FixedDropdownService, useValue: FIXED_DROPDOWN_SERVICE_MOCK },
         { provide: DragService, useValue: DRAG_SERVICE_MOCK },
         { provide: DebounceService, useValue: DEBOUNCE_SERVICE_MOCK },
         { provide: DomService, useValue: DOM_SERVICE_MOCK },
      ],
      importOverrides: [
         { replace: JoinNodeGraphComponent, with: JoinNodeGraphStub },
         { replace: OutOfZoneDirective, with: OutOfZoneDirectiveStub },
         { replace: SelectionBoxDirective, with: SelectionBoxDirectiveStub },
      ],
      schemas: [NO_ERRORS_SCHEMA],
   });

   // Restore both spies IMMEDIATELY here, inside renderComponent — NOT in afterEach.
   // Restoring here keeps the prototype clean for the next test's TestBed setup, which
   // would fail with "Cannot configure the test module when already instantiated" if the
   // prototype spy persisted until afterEach.
   getInstanceSpy.mockRestore();
   repaintSpy.mockRestore();

   const comp = fixture.componentInstance;
   // comp.jsp is already jspMock (set by constructor via the getInstance spy above).

   // JSDOM resolves "../api/data/query/heartbeat" relative to about:blank → status 0.
   // Spy sendHeartBeat to prevent any real HTTP; Group 1 tests verify the spy is called.
   vi.spyOn(comp as any, "sendHeartBeat").mockImplementation(() => {});

   return { comp, fixture };
}

// ---------------------------------------------------------------------------
// Global lifecycle
// ---------------------------------------------------------------------------

beforeEach(() => resetMocks());
afterEach(() => vi.restoreAllMocks());

// ---------------------------------------------------------------------------
// Group 1 — ngOnInit: heartbeat subscription setup [Risk 3]
// ---------------------------------------------------------------------------

describe("QueryNetworkGraphPaneComponent — ngOnInit: heartbeat subscription setup", () => {
   // 🔁 Regression-sensitive: heartbeat keeps the server-side query session alive.
   // If ngOnInit fails to set up the subscription, the session expires after 20 s.
   //
   // NOTE: sendHeartBeat is spied as a no-op in renderComponent because JSDOM resolves
   // "../api/data/query/heartbeat" relative to about:blank → invalid URL → status 0.
   // We verify that sendHeartBeat is CALLED (timer fired and subscription is alive)
   // rather than checking the HTTP request itself.
   //
   // heartbeatSubscription has no public accessor — read via (comp as any) to verify the
   // Subscription was created; there is no public observable for subscription lifecycle state.

   it("should set heartbeatSubscription to a non-null Subscription on init", async () => {
      const { comp } = await renderComponent();
      expect((comp as any).heartbeatSubscription).not.toBeNull();
   });

   it("should invoke sendHeartBeat immediately (timer delay=0) when runtimeId is set", async () => {
      const { comp } = await renderComponent({ runtimeId: "rt-hb" });
      // The observableTimer(0, ...) schedules a macrotask. waitFor flushes zone so
      // the first emission fires and calls the spied sendHeartBeat.
      await waitFor(() => expect((comp as any).sendHeartBeat).toHaveBeenCalled());
   });
});

// ---------------------------------------------------------------------------
// Group 2 — ngOnDestroy: heartbeat subscription teardown [Risk 3]
// ---------------------------------------------------------------------------

describe("QueryNetworkGraphPaneComponent — ngOnDestroy: unsubscribes heartbeat", () => {
   // heartbeatSubscription has no public accessor — read via (comp as any) to verify that
   // ngOnDestroy sets it to null (no memory leak from the interval observable).
   it("should set heartbeatSubscription to null after destroy", async () => {
      const { comp, fixture } = await renderComponent();
      expect((comp as any).heartbeatSubscription).not.toBeNull();
      fixture.destroy();
      expect((comp as any).heartbeatSubscription).toBeNull();
   });
});

// ---------------------------------------------------------------------------
// Group 3 — clearTableEnabled / clearJoinEnabled [Risk 2]
// ---------------------------------------------------------------------------

describe("QueryNetworkGraphPaneComponent — clearTableEnabled / clearJoinEnabled: boolean getters", () => {
   it("clearTableEnabled: returns false when graphViewModel has no graphs", async () => {
      const { comp } = await renderComponent({ graphViewModel: makeViewModel([]) });
      expect(comp.clearTableEnabled).toBe(false);
   });

   it("clearTableEnabled: returns true when graphViewModel has at least one graph", async () => {
      const { comp } = await renderComponent({
         graphViewModel: makeViewModel([makeGraph("t1")]),
      });
      expect(comp.clearTableEnabled).toBe(true);
   });

   it("clearJoinEnabled: returns false when no table has any joins", async () => {
      const { comp } = await renderComponent({
         graphViewModel: makeViewModel([makeGraph("t1"), makeGraph("t2")]),
      });
      expect(comp.clearJoinEnabled).toBe(false);
   });

   it("clearJoinEnabled: returns true when at least one table has input joins", async () => {
      const joinInfo = { id: "t2", joinModel: { foreignTable: "t1" } } as any;
      const { comp } = await renderComponent({
         graphViewModel: makeViewModel([
            makeGraph("t1", { inputs: [joinInfo] }),
            makeGraph("t2"),
         ]),
      });
      expect(comp.clearJoinEnabled).toBe(true);
   });

   it("clearJoinEnabled: returns true when at least one table has output joins", async () => {
      const joinInfo = { id: "t1", joinModel: { foreignTable: "t2" } } as any;
      const { comp } = await renderComponent({
         graphViewModel: makeViewModel([
            makeGraph("t1", { outputs: [joinInfo] }),
            makeGraph("t2"),
         ]),
      });
      expect(comp.clearJoinEnabled).toBe(true);
   });
});

// ---------------------------------------------------------------------------
// Group 4 — getUnjoinedTables [Risk 3]
// ---------------------------------------------------------------------------

describe("QueryNetworkGraphPaneComponent — getUnjoinedTables: unjoined table detection", () => {
   // 🔁 Regression-sensitive: getUnjoinedTables feeds setUnjoinedTables() which highlights
   // tables without connections in the UI. If the filter is wrong, joined tables get
   // highlighted as unjoined, creating confusing visual feedback.

   it("should return empty array when fewer than 2 tables exist", async () => {
      const { comp } = await renderComponent({
         graphViewModel: makeViewModel([makeGraph("t1")]),
      });
      expect(comp.getUnjoinedTables()).toEqual([]);
   });

   it("should return all table IDs that have no input and no output joins", async () => {
      const joinInfo = { id: "t1", joinModel: { foreignTable: "t2" } } as any;
      const { comp } = await renderComponent({
         graphViewModel: makeViewModel([
            makeGraph("t1", { outputs: [joinInfo] }),  // has output → joined
            makeGraph("t2"),                           // no joins → unjoined
            makeGraph("t3"),                           // no joins → unjoined
         ]),
      });
      const unjoined = comp.getUnjoinedTables();
      expect(unjoined).toContain("t2");
      expect(unjoined).toContain("t3");
      expect(unjoined).not.toContain("t1");
   });

   it("should return empty array when all tables have joins", async () => {
      const j12 = { id: "t1", joinModel: { foreignTable: "t2" } } as any;
      const j21 = { id: "t2", joinModel: { foreignTable: "t1" } } as any;
      const { comp } = await renderComponent({
         graphViewModel: makeViewModel([
            makeGraph("t1", { outputs: [j12] }),
            makeGraph("t2", { outputs: [j21] }),
         ]),
      });
      expect(comp.getUnjoinedTables()).toEqual([]);
   });
});

// ---------------------------------------------------------------------------
// Group 5 — isDuplicateTableAlias [Risk 2]
// ---------------------------------------------------------------------------

describe("QueryNetworkGraphPaneComponent — isDuplicateTableAlias: alias collision check", () => {
   it("should return false when no other graph has the same name/alias", async () => {
      const { comp } = await renderComponent({
         graphViewModel: makeViewModel([
            makeGraph("tableA"),
            makeGraph("tableB"),
         ]),
      });
      const nodeA = comp.graphViewModel.graphs[0].node;
      expect(comp.isDuplicateTableAlias(nodeA, "tableC")).toBe(false);
   });

   it("should return true when another graph's name matches the proposed alias", async () => {
      const { comp } = await renderComponent({
         graphViewModel: makeViewModel([
            makeGraph("tableA"),
            makeGraph("tableB"),
         ]),
      });
      const nodeA = comp.graphViewModel.graphs[0].node;
      expect(comp.isDuplicateTableAlias(nodeA, "tableB")).toBe(true);
   });

   it("should return true when graphNode is null (null-guard branch)", async () => {
      const { comp } = await renderComponent({
         graphViewModel: makeViewModel([makeGraph("tableA")]),
      });
      // The guard `if(!!graphNode)` short-circuits to return true when graphNode is null.
      expect(comp.isDuplicateTableAlias(null as any, "tableA")).toBe(true);
   });
});

// ---------------------------------------------------------------------------
// Group 6 — getSelectedNode [Risk 2]
// ---------------------------------------------------------------------------

describe("QueryNetworkGraphPaneComponent — getSelectedNode: selection state", () => {
   // dragNodes has no public setter — tests pre-set it via (comp as any) to arrange
   // multi-selection state without invoking the full jsPlumb-dependent selectNode() flow.
   it("should return true when selectedGraphNodePath matches the node's treeLink", async () => {
      const { comp } = await renderComponent();
      comp.selectedGraphNodePath = "/root/t1";
      const node = makeNode("t1", "/root/t1");
      expect(comp.getSelectedNode(node as any)).toBe(true);
   });

   it("should return false when selectedGraphNodePath does not match the node's treeLink", async () => {
      const { comp } = await renderComponent();
      comp.selectedGraphNodePath = "/root/other";
      const node = makeNode("t1", "/root/t1");
      expect(comp.getSelectedNode(node as any)).toBe(false);
   });

   it("should use dragNodes when selectedGraphNodePath is null", async () => {
      const { comp } = await renderComponent();
      comp.selectedGraphNodePath = null;
      const graph = makeGraph("t1");
      (comp as any).dragNodes = [graph];
      expect(comp.getSelectedNode(graph.node as any)).toBe(true);
   });

   it("should return false when node is not in dragNodes and no path match", async () => {
      const { comp } = await renderComponent();
      comp.selectedGraphNodePath = null;
      (comp as any).dragNodes = [];
      const node = makeNode("t1");
      expect(comp.getSelectedNode(node as any)).toBe(false);
   });
});

// ---------------------------------------------------------------------------
// Group 7 — getThumbnailClasses [Risk 2]
// ---------------------------------------------------------------------------

describe("QueryNetworkGraphPaneComponent — getThumbnailClasses: CSS class mapping", () => {
   // dragNodes and nodeMoving have no public setters — pre-set via (comp as any) to arrange
   // the selection/move state that getThumbnailClasses() reads when computing CSS classes.
   it("should return selected=true for a graph in dragNodes", async () => {
      const { comp } = await renderComponent();
      const graph = makeGraph("t1");
      (comp as any).dragNodes = [graph];
      const classes = comp.getThumbnailClasses(graph);
      expect(classes["ws-assembly-graph-element--selected"]).toBe(true);
      expect(classes["ws-assembly-graph-element--dimmed"]).toBe(false);
   });

   it("should return dimmed=true for a graph NOT in dragNodes when nodeMoving=true", async () => {
      const { comp } = await renderComponent();
      const graphA = makeGraph("tA");
      const graphB = makeGraph("tB");
      (comp as any).dragNodes = [graphA];
      (comp as any).nodeMoving = true;
      const classes = comp.getThumbnailClasses(graphB);
      expect(classes["ws-assembly-graph-element--selected"]).toBe(false);
      expect(classes["ws-assembly-graph-element--dimmed"]).toBe(true);
   });

   it("should return neither selected nor dimmed for non-dragging non-selected graph", async () => {
      const { comp } = await renderComponent();
      const graph = makeGraph("t1");
      (comp as any).dragNodes = [];
      (comp as any).nodeMoving = false;
      const classes = comp.getThumbnailClasses(graph);
      expect(classes["ws-assembly-graph-element--selected"]).toBe(false);
      expect(classes["ws-assembly-graph-element--dimmed"]).toBe(false);
   });
});

// ---------------------------------------------------------------------------
// Group 8 — addTables: HTTP POST [Risk 3]
// ---------------------------------------------------------------------------

describe("QueryNetworkGraphPaneComponent — addTables: POST to GRAPH_ADD_TABLES_URI", () => {
   // 🔁 Regression-sensitive: addTables must POST and fire onRefreshGraph+emitModelChange;
   // if the check for empty selectedNodesData is missing, empty POST goes to server with no tables.

   it("should POST and emit onRefreshGraph + emitModelChange on success", async () => {
      server.use(
         http.post("*/api/data/datasource/query/table/add", () =>
            new HttpResponse(null, { status: 200 }),
         ),
      );
      const { comp } = await renderComponent();
      const emitted: any[] = [];
      comp.onRefreshGraph.subscribe(v => emitted.push(v));
      QUERY_MODEL_SERVICE_MOCK.emitModelChange.mockClear();

      comp.addTables({ position: { x: 10, y: 20 }, data: [{ type: "PHYSICAL_TABLE", path: "SA.ORDERS" }] });
      await waitFor(() => expect(QUERY_MODEL_SERVICE_MOCK.emitModelChange).toHaveBeenCalledTimes(1));
      expect(emitted).toHaveLength(1);
   });

   it("should NOT POST when data is empty", async () => {
      let postFired = false;
      server.use(
         http.post("*/api/data/datasource/query/table/add", () => {
            postFired = true;
            return new HttpResponse(null, { status: 200 });
         }),
      );
      const { comp } = await renderComponent();
      comp.addTables({ position: { x: 10, y: 20 }, data: [] });
      await Promise.resolve();
      expect(postFired).toBe(false);
   });

   it("drop: calls addTables with drag data from DragService", async () => {
      server.use(
         http.post("*/api/data/datasource/query/table/add", () =>
            new HttpResponse(null, { status: 200 }),
         ),
      );
      const tableEntry = { type: "PHYSICAL_TABLE", path: "SA.ORDERS" };
      DRAG_SERVICE_MOCK.getDragDataValues.mockReturnValueOnce([[tableEntry]]);

      const { comp } = await renderComponent();
      QUERY_MODEL_SERVICE_MOCK.emitModelChange.mockClear();
      comp.drop({ preventDefault: vi.fn(), stopPropagation: vi.fn(), offsetX: 50, offsetY: 50 } as any);
      await waitFor(() => expect(QUERY_MODEL_SERVICE_MOCK.emitModelChange).toHaveBeenCalledTimes(1));
   });
});

// ---------------------------------------------------------------------------
// Group 9 — doRemove: HTTP POST [Risk 3]
// ---------------------------------------------------------------------------

describe("QueryNetworkGraphPaneComponent — doRemove: POST to GRAPH_REMOVE_TABLES_URI", () => {
   it("should POST remove event and emit onRefreshGraph + emitModelChange + onRemoveTable", async () => {
      server.use(
         http.post("*/api/data/datasource/query/table/remove", () =>
            new HttpResponse(null, { status: 200 }),
         ),
      );
      const { comp } = await renderComponent();
      const refreshes: any[] = [];
      const removals: any[] = [];
      comp.onRefreshGraph.subscribe(v => refreshes.push(v));
      comp.onRemoveTable.subscribe(v => removals.push(v));
      QUERY_MODEL_SERVICE_MOCK.emitModelChange.mockClear();

      const graph = makeGraph("t1");
      (comp as any).dragNodes = [graph];
      comp.doRemove({ runtimeId: "rt-net-graph", tables: [{ tableName: "t1", fullName: "t1" }] });
      await waitFor(() => expect(QUERY_MODEL_SERVICE_MOCK.emitModelChange).toHaveBeenCalledTimes(1));
      expect(refreshes).toHaveLength(1);
      expect(removals).toHaveLength(1);
      expect((comp as any).dragNodes).toEqual([]);
   });
});

// ---------------------------------------------------------------------------
// Group 10 — onSelectionBox: rectangle-based selection [Risk 2]
// ---------------------------------------------------------------------------

describe("QueryNetworkGraphPaneComponent — onSelectionBox: selects graphs by bounding box", () => {
   it("should set dragNodes to graphs whose bounds intersect the selection rectangle", async () => {
      const graphInBox = makeGraph("t1", { bounds: new Rectangle(10, 10, 100, 50) });
      const graphOutBox = makeGraph("t2", { bounds: new Rectangle(500, 500, 100, 50) });
      const { comp } = await renderComponent({
         graphViewModel: makeViewModel([graphInBox, graphOutBox]),
      });
      // refreshDragSelection() removes graphs not present in this.nodes.
      // Pre-register graphInBox so it survives the cleanup pass.
      (comp as any).sourceIds["t1"] = "t1";
      (comp as any).nodes["t1"] = graphInBox;
      comp.onSelectionBox({ box: new Rectangle(0, 0, 200, 100) } as any);
      expect((comp as any).dragNodes).toContain(graphInBox);
      expect((comp as any).dragNodes).not.toContain(graphOutBox);
   });

   it("should set dragNodes to empty when no graphs intersect the selection box", async () => {
      const graph = makeGraph("t1", { bounds: new Rectangle(500, 500, 100, 50) });
      const { comp } = await renderComponent({
         graphViewModel: makeViewModel([graph]),
      });
      comp.onSelectionBox({ box: new Rectangle(0, 0, 50, 50) } as any);
      expect((comp as any).dragNodes).toEqual([]);
   });
});

// ---------------------------------------------------------------------------
// Group 11 — updateUnjoinedTables [Risk 2]
// ---------------------------------------------------------------------------

describe("QueryNetworkGraphPaneComponent — updateUnjoinedTables: calls setUnjoinedTables", () => {
   it("should call queryModelService.setUnjoinedTables with the current unjoined table list", async () => {
      const { comp } = await renderComponent({
         graphViewModel: makeViewModel([makeGraph("t1"), makeGraph("t2")]),
      });
      QUERY_MODEL_SERVICE_MOCK.setUnjoinedTables.mockClear();
      comp.updateUnjoinedTables();
      expect(QUERY_MODEL_SERVICE_MOCK.setUnjoinedTables).toHaveBeenCalledWith(["t1", "t2"]);
   });
});

// ---------------------------------------------------------------------------
// Group 12 — keydown: Ctrl+A and Delete key handling [Risk 2]
// ---------------------------------------------------------------------------

describe("QueryNetworkGraphPaneComponent — keydown: keyboard shortcuts", () => {
   it("Ctrl+A: selects all graphs (dragNodes = all graphs in viewModel)", async () => {
      const graphs = [makeGraph("t1"), makeGraph("t2")];
      const { comp } = await renderComponent({ graphViewModel: makeViewModel(graphs) });
      comp.keydown({ ctrlKey: true, key: "a", preventDefault: vi.fn(), stopPropagation: vi.fn() } as any);
      // dragNodes has no public getter — read via (comp as any) to verify selectAll() assigns the reference.
      expect((comp as any).dragNodes).toBe(graphs); // same reference: selectAll() assigns graphs directly
   });
});
