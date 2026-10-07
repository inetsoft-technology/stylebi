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
 * Bug #77976 - dragging a physical view table while a graph refresh lands must still
 * save the move.
 *
 * Renders the real PhysicalGraphPane -> PhysicalModelNetworkGraphComponent ->
 * JoinNodeGraphComponent chain with the real DataPhysicalModelService; HTTP goes through
 * MSW. Only jsPlumb is faked: jspMock's draggable() captures the real
 * JoinNodeGraphComponent start/drag/stop callbacks so the test can drive a drag.
 *
 * The host mirrors DatabasePhysicalModelComponent: onNodeSelected re-derives
 * selectedGraphModels and, when the editing table changes, calls
 * highlightConnections(null) like the PhysicalTableJoinsComponent table setter does.
 * With a join highlight set, that clear makes PhysicalGraphPane re-fetch the graph, so
 * the network graph receives NEW GraphModel objects while the drag is in progress.
 *
 * Harness notes:
 *  - the jsPlumb getInstance spy stays active for the whole test: the network graph is
 *    created only after the first graph POST resolves.
 *  - updateGraphPaneSize is stubbed (it runs from ngAfterViewChecked).
 *  - jspMock gives elements ids in draggable/addEndpoint like real jsPlumb does
 *    (registerNode keys nodes by element.id).
 *  - the move-node debounce is captured and flushed by the test, so "refresh lands inside
 *    the 200 ms debounce window" is deterministic.
 */

import { Component, NO_ERRORS_SCHEMA } from "@angular/core";
import { provideHttpClient } from "@angular/common/http";
import { By } from "@angular/platform-browser";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { render } from "@testing-library/angular";
import { http, HttpResponse } from "msw";
import { server } from "@test-mocks/server";
import { jsPlumbLib } from "../../../../../../composer/gui/ws/jsplumb/jsplumb";
import { jspMock } from "../physical-model-network-graph/physical-model-network-graph.component.test-helpers";
import { PhysicalGraphPane } from "./physical-graph-pane.component";
import { PhysicalModelNetworkGraphComponent } from "../physical-model-network-graph/physical-model-network-graph.component";
import { JoinNodeGraphComponent } from "../../common-components/join-node-graph/join-node-graph.component";
import { DataPhysicalModelService } from "../../../../services/data-physical-model.service";
import { DebounceService } from "../../../../../../widget/services/debounce.service";
import { FixedDropdownService } from "../../../../../../widget/fixed-dropdown/fixed-dropdown.service";
import { DomService } from "../../../../../../widget/dom-service/dom.service";

const MOVE_KEY = "physical-model-graph-move-node";

const ORDERS_JOIN = {
   type: "=", orderPriority: 1, weak: false, mergingRule: null, cardinality: null,
   table: "ORDERS", column: "CUSTOMER_ID", foreignTable: "CUSTOMERS",
   foreignColumn: "CUSTOMER_ID", baseJoin: false
};

function graph(name: string, x: number, output: any[] = [], input: any[] = []): any {
   return {
      node: { id: name, name, tableName: name, label: name, tooltip: name,
         treeLink: "/db/" + name, aliasSource: null, outgoingAliasSource: null },
      edge: { input, output },
      cols: [{ name: "CUSTOMER_ID" }],
      bounds: { x, y: 0, width: 100, height: 50 },
      showColumns: false, alias: false, autoAlias: false, sql: false, baseTable: false,
      autoAliasByOutgoing: false, designModeAlias: false,
   };
}

/** A fresh graph model (new objects every call, like each server response). */
function graphResponse(tables: string[] = ["ORDERS", "CUSTOMERS", "PRODUCTS"]): any {
   const join = { id: "ORDERS", joinModel: ORDERS_JOIN };
   const all: any = {
      ORDERS: () => graph("ORDERS", 0, [join]),
      CUSTOMERS: () => graph("CUSTOMERS", 150, [], [join]),
      PRODUCTS: () => graph("PRODUCTS", 300),
   };

   return {
      joinEdit: false, joinEditPaneModel: null,
      graphViewModel: { graphs: tables.map(t => all[t]()) }
   };
}

@Component({
   selector: "host-77976",
   template: `
      <physical-graph-pane physicalView="v" datasource="ds" runtimeId="rt"
         [selectedGraphModels]="selectedGraphModels"
         (onPhysicalGraph)="gvm = $event"
         (onNodeSelected)="nodeSelected($event)"></physical-graph-pane>
   `,
   imports: [PhysicalGraphPane]
})
class HostComponent {
   gvm: any;
   selectedGraphModels: any[] = [];
   table: string = null;

   constructor(public svc: DataPhysicalModelService) {
   }

   // mirrors DatabasePhysicalModelComponent.graphNodesSelected -> tree selectNode ->
   // selectPhysicalGraphNode/changeEditingTable -> PhysicalTableJoinsComponent table setter
   nodeSelected(paths: string[]): void {
      this.selectedGraphModels = (this.gvm?.graphs ?? []).filter(g => paths.includes(g.node.treeLink));
      const t = paths.length == 1 ? paths[0] : null;

      if(this.table != t) {
         this.table = t;
         this.svc.highlightConnections(null);
      }
   }
}

describe("PhysicalGraphPane - Bug #77976 drag across a graph refresh", () => {
   let moves: any[];
   let graphPosts: number;
   let nextResponse: () => any;
   let gate: Promise<void> | null;
   let pendingMove: (() => void) | null;
   const draggables = new Map<Element, any>();

   beforeEach(() => {
      moves = [];
      graphPosts = 0;
      nextResponse = () => graphResponse();
      gate = null;
      pendingMove = null;
      draggables.clear();
      server.use(
         http.post("*/api/data/physicalmodel/graph", async () => {
            graphPosts++;
            const body = nextResponse();

            if(gate) {
               await gate;
            }

            return HttpResponse.json(body);
         }),
         http.put("*/api/data/physicalmodel/graph/move", async ({ request }) => {
            moves.push(await request.json());
            return HttpResponse.json(null);
         }),
         http.put("*/api/data/physicalmodel/*", () => HttpResponse.json(false)),
         http.get("*/api/data/physicalmodel/warnings/*", () => HttpResponse.json(null)),
      );
      let seq = 0;
      const ensureId = (el: any) => {
         if(el && !el.id) {
            el.id = "jsPlumb_77976_" + (++seq);
         }
      };
      jspMock.draggable.mockImplementation((el: any, options: any) => {
         ensureId(el);
         draggables.set(el, options);
      });
      jspMock.addEndpoint.mockImplementation((el: any) => {
         ensureId(el);
         return { setVisible: vi.fn() } as any;
      });
   });

   afterEach(() => {
      vi.restoreAllMocks();
      jspMock.draggable.mockReset();
      jspMock.addEndpoint.mockReset();
      jspMock.addEndpoint.mockReturnValue({ setVisible: vi.fn() });
   });

   async function setup() {
      vi.spyOn(jsPlumbLib.jsPlumb, "getInstance").mockReturnValue(jspMock as any);
      vi.spyOn(PhysicalModelNetworkGraphComponent.prototype as any, "setRepaintTimer")
         .mockImplementation(() => {});
      vi.spyOn(PhysicalGraphPane.prototype as any, "updateGraphPaneSize").mockImplementation(() => {});
      const debounce = (key: string, fn: () => void) => {
         if(key === MOVE_KEY) {
            pendingMove = fn;
         }
      };

      const { fixture } = await render(HostComponent, {
         schemas: [NO_ERRORS_SCHEMA],
         providers: [
            provideHttpClient(),
            DataPhysicalModelService,
            { provide: DebounceService, useValue: { debounce } },
            { provide: NgbModal, useValue: {} },
            { provide: FixedDropdownService, useValue: { open: vi.fn() } },
            { provide: DomService, useValue: new Proxy({}, {
               get: (_t, k) => k === "requestRead" || k === "requestWrite" ? (fn: any) => fn() : () => {}
            }) },
         ],
      });

      const settle = async () => {
         await new Promise(r => setTimeout(r, 50));
         fixture.detectChanges();
      };

      // polls on timers; testing-library's waitFor hung the test worker (busy, never
      // timing out) while waiting for an MSW response in this tree
      const until = async (cond: () => boolean) => {
         for(let i = 0; i < 40 && !cond(); i++) {
            await settle();
         }

         await settle();
      };

      const count = () => fixture.debugElement.queryAll(By.directive(JoinNodeGraphComponent)).length;
      await until(() => count() === 3);
      expect(count()).toBe(3);

      const host = fixture.componentInstance;
      const ng = fixture.debugElement.query(By.directive(PhysicalModelNetworkGraphComponent))
         .componentInstance as PhysicalModelNetworkGraphComponent;
      const nodeEl = (name: string): HTMLElement => fixture.debugElement
         .queryAll(By.directive(JoinNodeGraphComponent))
         .find(d => d.componentInstance.graph.node.id === name).nativeElement;
      const dragIds = (): string[] => (ng as any).dragNodes.map(g => g.node.id);
      return { fixture, host, ng, nodeEl, dragIds, settle, until };
   }

   /** Highlight the ORDERS-CUSTOMERS join (what selecting a join row emits) and wait for its refresh. */
   async function highlightJoin(ctx: any) {
      const before = graphPosts;
      const oldModel = ctx.ng.graphViewModel;
      ctx.host.svc.highlightConnections([{ sourceTable: "ORDERS", targetTable: "CUSTOMERS" }]);
      await ctx.until(() => graphPosts === before + 1);
      expect(graphPosts).toBe(before + 1);
      await refreshLanded(ctx, oldModel);
   }

   function mousedown(el: HTMLElement, ctrlKey = false): void {
      el.firstElementChild.dispatchEvent(new MouseEvent("mousedown", { bubbles: true, button: 0, ctrlKey }));
   }

   /** Wait until the graph POST sent after `before` has landed in the network graph. */
   async function refreshLanded(ctx: any, before: any) {
      for(let i = 0; i < 40 && ctx.ng.graphViewModel === before; i++) {
         await ctx.settle();
      }

      expect(ctx.ng.graphViewModel).not.toBe(before);
      await ctx.settle();
   }

   it("control: drag without a refresh sends the move", async () => {
      const ctx = await setup();
      const el = ctx.nodeEl("PRODUCTS");
      const posts = graphPosts;

      mousedown(el);
      ctx.fixture.detectChanges();
      const opts = draggables.get(el);
      opts.start({ el, e: new MouseEvent("mousemove"), pos: [300, 0] });
      opts.drag({ el, e: null, pos: [350, 20] });
      await ctx.settle();
      opts.stop({ el, e: new MouseEvent("mouseup"), pos: [400, 40] });
      pendingMove?.();
      await ctx.until(() => moves.length > 0);
      expect(moves.length).toBe(1);

      expect(graphPosts).toBe(posts);
      expect(moves[0].bounds).toMatchObject({ x: 400, y: 40 });
      expect(moves[0].table).toBe("PRODUCTS");
   });

   it("keeps the dragged table selected when a graph refresh lands mid-drag and sends the move", async () => {
      const ctx = await setup();
      await highlightJoin(ctx);
      const el = ctx.nodeEl("PRODUCTS");
      const oldModel = ctx.ng.graphViewModel;
      const posts = graphPosts;

      // mousedown changes the editing table -> highlight cleared -> graph refresh
      mousedown(el);
      ctx.fixture.detectChanges();
      expect(ctx.dragIds()).toEqual(["PRODUCTS"]);
      const opts = draggables.get(el);
      opts.start({ el, e: new MouseEvent("mousemove"), pos: [300, 0] });
      opts.drag({ el, e: null, pos: [350, 20] });

      await refreshLanded(ctx, oldModel);
      expect(graphPosts).toBe(posts + 1);
      expect(ctx.dragIds()).toEqual(["PRODUCTS"]);
      // the selection now holds the refreshed model's object
      expect((ctx.ng as any).dragNodes[0]).toBe(ctx.ng.graphViewModel.graphs[2]);

      opts.stop({ el, e: new MouseEvent("mouseup"), pos: [400, 40] });
      pendingMove?.();
      await ctx.until(() => moves.length > 0);
      expect(moves.length).toBe(1);

      expect(moves[0].table).toBe("PRODUCTS");
      expect(moves[0].bounds).toMatchObject({ x: 400, y: 40 });
   });

   it("sends the move when the refresh lands after drag stop but inside the move debounce", async () => {
      const ctx = await setup();
      await highlightJoin(ctx);
      const el = ctx.nodeEl("PRODUCTS");
      const oldModel = ctx.ng.graphViewModel;
      let release: () => void;
      gate = new Promise<void>(r => release = r);

      mousedown(el);
      ctx.fixture.detectChanges();
      const opts = draggables.get(el);
      opts.start({ el, e: new MouseEvent("mousemove"), pos: [300, 0] });
      opts.drag({ el, e: null, pos: [350, 20] });
      opts.stop({ el, e: new MouseEvent("mouseup"), pos: [400, 40] });
      // the move is debounced; the delayed graph response lands before it fires
      expect(pendingMove).not.toBeNull();
      expect(moves.length).toBe(0);

      release();
      await refreshLanded(ctx, oldModel);
      expect(ctx.dragIds()).toEqual(["PRODUCTS"]);

      pendingMove();
      await ctx.until(() => moves.length > 0);
      expect(moves.length).toBe(1);

      expect(moves[0].table).toBe("PRODUCTS");
      expect(moves[0].bounds).toMatchObject({ x: 400, y: 40 });
   });

   it("drops a selected table that the refresh removed and keeps the others", async () => {
      const ctx = await setup();

      mousedown(ctx.nodeEl("ORDERS"));
      ctx.fixture.detectChanges();
      mousedown(ctx.nodeEl("PRODUCTS"), true);
      ctx.fixture.detectChanges();
      await ctx.settle();
      expect(ctx.dragIds()).toEqual(["ORDERS", "PRODUCTS"]);

      const oldModel = ctx.ng.graphViewModel;
      nextResponse = () => graphResponse(["ORDERS", "CUSTOMERS"]);
      ctx.host.svc.emitModelChange(false);
      await refreshLanded(ctx, oldModel);
      await ctx.until(() => ctx.fixture.debugElement
         .queryAll(By.directive(JoinNodeGraphComponent)).length === 2);
      expect(ctx.fixture.debugElement.queryAll(By.directive(JoinNodeGraphComponent)).length).toBe(2);

      expect(ctx.dragIds()).toEqual(["ORDERS"]);
      expect((ctx.ng as any).dragNodes[0]).toBe(ctx.ng.graphViewModel.graphs[0]);
      // the model's own graphs array is never mutated by the selection bookkeeping
      expect(ctx.ng.graphViewModel.graphs.map(g => g.node.id)).toEqual(["ORDERS", "CUSTOMERS"]);
   });

   /** Select ORDERS, ctrl-select PRODUCTS, and start dragging PRODUCTS (the drag leader). */
   async function startMultiDrag(ctx: any) {
      mousedown(ctx.nodeEl("ORDERS"));
      ctx.fixture.detectChanges();
      const el = ctx.nodeEl("PRODUCTS");
      mousedown(el, true);
      ctx.fixture.detectChanges();
      await ctx.settle();
      expect(ctx.dragIds()).toEqual(["ORDERS", "PRODUCTS"]);

      const opts = draggables.get(el);
      opts.start({ el, e: new MouseEvent("mousemove"), pos: [300, 0] });
      opts.drag({ el, e: null, pos: [350, 20] });
      return { el, opts };
   }

   function movedBounds(): Record<string, any> {
      const bounds: Record<string, any> = {};
      moves.forEach(m => bounds[m.table] = m.bounds);
      return bounds;
   }

   it("sends the move of every selected table when a refresh lands during a multi-table drag", async () => {
      const ctx = await setup();
      const { el, opts } = await startMultiDrag(ctx);
      const oldModel = ctx.ng.graphViewModel;

      ctx.host.svc.emitModelChange(false);
      await refreshLanded(ctx, oldModel);
      expect(ctx.dragIds()).toEqual(["ORDERS", "PRODUCTS"]);

      opts.stop({ el, e: new MouseEvent("mouseup"), pos: [400, 40] });
      pendingMove?.();
      await ctx.until(() => moves.length >= 2);
      expect(moves.length).toBe(2);

      // both tables get the leader's offset (+100, +40) applied to the refreshed bounds
      expect(movedBounds()).toEqual({
         ORDERS: { x: 100, y: 40, width: 100, height: 50 },
         PRODUCTS: { x: 400, y: 40, width: 100, height: 50 },
      });
   });

   it("still sends the other tables' moves when a refresh during the drag removes a dragged table", async () => {
      const ctx = await setup();
      const { el, opts } = await startMultiDrag(ctx);
      const oldModel = ctx.ng.graphViewModel;

      nextResponse = () => graphResponse(["CUSTOMERS", "PRODUCTS"]);
      ctx.host.svc.emitModelChange(false);
      await refreshLanded(ctx, oldModel);
      await ctx.until(() => ctx.fixture.debugElement
         .queryAll(By.directive(JoinNodeGraphComponent)).length === 2);
      expect(ctx.dragIds()).toEqual(["PRODUCTS"]);

      opts.stop({ el, e: new MouseEvent("mouseup"), pos: [400, 40] });
      pendingMove?.();
      await ctx.until(() => moves.length > 0);
      await ctx.settle();
      expect(moves.length).toBe(1);

      expect(movedBounds()).toEqual({ PRODUCTS: { x: 400, y: 40, width: 100, height: 50 } });
   });

   it("resolves a selection built from the old model against a refresh landing in the same pass", async () => {
      const ctx = await setup();
      const oldModel = ctx.ng.graphViewModel;

      // the refresh drops CUSTOMERS; before it lands, selectAll() makes the host derive
      // selectedGraphModels from the old model, so both inputs change in one pass
      nextResponse = () => graphResponse(["ORDERS", "PRODUCTS"]);
      ctx.host.svc.emitModelChange(false);
      ctx.ng.selectAll();
      expect(ctx.host.selectedGraphModels.map(g => g.node.id)).toEqual(["ORDERS", "CUSTOMERS", "PRODUCTS"]);

      await refreshLanded(ctx, oldModel);
      await ctx.until(() => ctx.fixture.debugElement
         .queryAll(By.directive(JoinNodeGraphComponent)).length === 2);

      expect(ctx.dragIds()).toEqual(["ORDERS", "PRODUCTS"]);
      const current = ctx.ng.graphViewModel.graphs;
      (ctx.ng as any).dragNodes.forEach(g => expect(current).toContain(g));
   });
});
