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
 * Bug #77995 - a join highlight change is client-side only: it must restyle the existing
 * connections instead of re-fetching the whole graph, so a reload can no longer land in
 * the middle of (or after) a table drag and put the table back.
 *
 * Renders the real PhysicalGraphPane -> PhysicalModelNetworkGraphComponent ->
 * JoinNodeGraphComponent chain with the real DataPhysicalModelService; HTTP goes through
 * MSW. Only jsPlumb is faked: jspMock's connect() records each connection (type/data, like
 * real jsPlumb's connect({type, data})) and its setType(type, data) records restyles.
 *
 * The host mirrors DatabasePhysicalModelComponent: onNodeSelected re-derives
 * selectedGraphModels and, when the editing table changes, calls highlightConnections(null)
 * like the PhysicalTableJoinsComponent table setter does.
 *
 * Harness notes (same as the Bug #77976 drag-refresh spec):
 *  - the jsPlumb getInstance spy stays active for the whole test: the network graph is
 *    created only after the first graph POST resolves.
 *  - updateGraphPaneSize is stubbed (it runs from ngAfterViewChecked).
 *  - polls with setTimeout + detectChanges: testing-library's waitFor hung the worker
 *    while waiting on MSW-driven state in this tree.
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
const CONN = "physical-graph-connection";
const COLOR = "physical-graph-connection-color";
const WEAK = "physical-graph-weak-connection";
const HIGHLIGHT = { color: "#ed711c" };
const CYCLE = { color: "red" };
const ORDERS_CUSTOMERS = [{ sourceTable: "ORDERS", targetTable: "CUSTOMERS" }];

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
function graphResponse(joinProps: any = {}): any {
   const joinModel = {
      type: "=", orderPriority: 1, weak: false, cycle: false, mergingRule: null,
      cardinality: null, table: "ORDERS", column: "CUSTOMER_ID", foreignTable: "CUSTOMERS",
      foreignColumn: "CUSTOMER_ID", baseJoin: false, ...joinProps
   };
   const join = { id: "ORDERS", joinModel };

   return {
      joinEdit: false, joinEditPaneModel: null,
      graphViewModel: { graphs: [
         graph("ORDERS", 0, [join]),
         graph("CUSTOMERS", 150, [], [join]),
         graph("PRODUCTS", 300),
      ] }
   };
}

@Component({
   selector: "host-77995",
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

interface FakeConnection {
   sourceId: string;
   targetId: string;
   type: string;
   data: any;
   /** the type/data connect() created the connection with */
   created: { type: string, data: any };
   setType: ReturnType<typeof vi.fn>;
}

describe("PhysicalGraphPane - Bug #77995 join highlight without a graph reload", () => {
   let moves: any[];
   let graphPosts: number;
   let nextResponse: () => any;
   let pendingMove: (() => void) | null;
   let conns: FakeConnection[];
   const draggables = new Map<Element, any>();

   beforeEach(() => {
      moves = [];
      graphPosts = 0;
      nextResponse = () => graphResponse();
      pendingMove = null;
      conns = [];
      draggables.clear();
      server.use(
         http.post("*/api/data/physicalmodel/graph", () => {
            graphPosts++;
            return HttpResponse.json(nextResponse());
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
            el.id = "jsPlumb_77995_" + (++seq);
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
      jspMock.connect.mockImplementation((params: any) => {
         const conn: FakeConnection = {
            sourceId: params.source, targetId: params.target,
            type: params.type, data: params.data,
            created: { type: params.type, data: params.data },
            setType: vi.fn((type: string, data: any) => {
               conn.type = type;
               conn.data = data;
            }),
         };
         conns.push(conn);
         return conn;
      });
      jspMock.getAllConnections.mockImplementation(() => conns);
      jspMock.deleteEveryConnection.mockImplementation(() => conns = []);
   });

   afterEach(() => {
      vi.restoreAllMocks();
      jspMock.draggable.mockReset();
      jspMock.addEndpoint.mockReset();
      jspMock.addEndpoint.mockReturnValue({ setVisible: vi.fn() });
      jspMock.connect.mockReset();
      jspMock.getAllConnections.mockReset();
      jspMock.getAllConnections.mockReturnValue([]);
      jspMock.deleteEveryConnection.mockReset();
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

      const until = async (cond: () => boolean) => {
         for(let i = 0; i < 40 && !cond(); i++) {
            await settle();
         }

         await settle();
      };

      const count = () => fixture.debugElement.queryAll(By.directive(JoinNodeGraphComponent)).length;
      await until(() => count() === 3 && conns.length === 1);
      expect(count()).toBe(3);
      expect(conns.length).toBe(1);

      const host = fixture.componentInstance;
      const ng = fixture.debugElement.query(By.directive(PhysicalModelNetworkGraphComponent))
         .componentInstance as PhysicalModelNetworkGraphComponent;
      const nodeEl = (name: string): HTMLElement => fixture.debugElement
         .queryAll(By.directive(JoinNodeGraphComponent))
         .find(d => d.componentInstance.graph.node.id === name).nativeElement;
      return { fixture, host, ng, nodeEl, settle, until };
   }

   /** Set (or clear) the highlight, as selecting a join row (or another table) does. */
   async function highlight(ctx: any, infos: any[]) {
      ctx.host.svc.highlightConnections(infos);
      ctx.fixture.detectChanges();
      // give a (wrong) graph reload every chance to be sent and land
      await ctx.settle();
      await ctx.settle();
      await ctx.settle();
   }

   /** The type/data connectNode builds for the join with the given highlight (via a refresh). */
   async function builtByRefresh(ctx: any, infos: any[]): Promise<{ type: string, data: any }> {
      await highlight(ctx, infos);
      const oldModel = ctx.ng.graphViewModel;
      ctx.host.svc.emitModelChange(false);
      await ctx.until(() => ctx.ng.graphViewModel !== oldModel && conns.length === 1);
      expect(ctx.ng.graphViewModel).not.toBe(oldModel);
      expect(conns.length).toBe(1);
      return conns[0].created;
   }

   it("restyles the joined connection on a highlight change without a graph POST", async () => {
      const ctx = await setup();
      const conn = conns[0];
      const model = ctx.ng.graphViewModel;
      const connects = jspMock.connect.mock.calls.length;
      const posts = graphPosts;
      expect(conn.created).toEqual({ type: CONN, data: undefined });

      await highlight(ctx, ORDERS_CUSTOMERS);
      expect(graphPosts).toBe(posts);
      expect(ctx.ng.graphViewModel).toBe(model);
      expect(jspMock.connect.mock.calls.length).toBe(connects);
      expect(conn.setType).toHaveBeenCalledTimes(1);
      expect(conn.type).toBe(`${CONN} ${COLOR}`);
      expect(conn.data).toEqual(HIGHLIGHT);

      await highlight(ctx, null);
      expect(graphPosts).toBe(posts);
      expect(conn.setType).toHaveBeenCalledTimes(2);
      expect(conn.type).toBe(CONN);
      expect(conn.data).toBeUndefined();
   });

   it("leaves a connection that is not part of the highlighted join unhighlighted", async () => {
      const ctx = await setup();
      const conn = conns[0];

      await highlight(ctx, [{ sourceTable: "ORDERS", targetTable: "PRODUCTS" }]);
      expect(conn.type).toBe(CONN);
      expect(conn.data).toBeUndefined();
   });

   it("gives a highlight change the same type and data a refresh would build", async () => {
      const ctx = await setup();
      await highlight(ctx, ORDERS_CUSTOMERS);
      const toggled = { type: conns[0].type, data: conns[0].data };

      expect(await builtByRefresh(ctx, ORDERS_CUSTOMERS)).toEqual(toggled);
   });

   it("keeps a cycle join red when it is highlighted and when the highlight is cleared", async () => {
      nextResponse = () => graphResponse({ cycle: true });
      const ctx = await setup();
      const conn = conns[0];
      const original = conn.created;
      expect(original).toEqual({ type: `${CONN} ${COLOR}`, data: CYCLE });

      await highlight(ctx, ORDERS_CUSTOMERS);
      // connectNode applies the highlight, then the cycle color wins
      expect(conn.type).toBe(`${CONN} ${COLOR} ${COLOR}`);
      expect(conn.data).toEqual(CYCLE);
      const toggled = { type: conn.type, data: conn.data };

      await highlight(ctx, null);
      expect({ type: conn.type, data: conn.data }).toEqual(original);

      expect(await builtByRefresh(ctx, ORDERS_CUSTOMERS)).toEqual(toggled);
   });

   it("keeps a weak join's type after the highlight color, and restores it when cleared", async () => {
      nextResponse = () => graphResponse({ weak: true });
      const ctx = await setup();
      const conn = conns[0];
      const original = conn.created;
      expect(original).toEqual({ type: `${CONN} ${WEAK}`, data: undefined });

      await highlight(ctx, ORDERS_CUSTOMERS);
      // same order as connectNode: the weak type (stroke "inherit") is merged after the color
      expect(conn.type).toBe(`${CONN} ${COLOR} ${WEAK}`);
      expect(conn.data).toEqual(HIGHLIGHT);
      const toggled = { type: conn.type, data: conn.data };

      await highlight(ctx, null);
      expect({ type: conn.type, data: conn.data }).toEqual(original);

      expect(await builtByRefresh(ctx, ORDERS_CUSTOMERS)).toEqual(toggled);
   });

   function mousedown(el: HTMLElement): void {
      el.firstElementChild.dispatchEvent(new MouseEvent("mousedown", { bubbles: true, button: 0 }));
   }

   /** Katavorio writes the dragged element's inline position on every move. */
   function moveTo(el: HTMLElement, x: number, y: number): void {
      el.style.left = x + "px";
      el.style.top = y + "px";
   }

   it("does not reset a table dragged while the highlight is cleared (4a)", async () => {
      const ctx = await setup();
      await highlight(ctx, ORDERS_CUSTOMERS);
      const el = ctx.nodeEl("PRODUCTS");
      const model = ctx.ng.graphViewModel;
      const posts = graphPosts;

      // mousedown changes the editing table -> highlight cleared
      mousedown(el);
      ctx.fixture.detectChanges();
      const opts = draggables.get(el);
      opts.start({ el, e: new MouseEvent("mousemove"), pos: [300, 0] });
      moveTo(el, 350, 20);
      opts.drag({ el, e: null, pos: [350, 20] });
      moveTo(el, 400, 40);
      opts.drag({ el, e: null, pos: [400, 40] });

      // the mouse is held still: nothing may move the table back
      await ctx.settle();
      await ctx.settle();
      await ctx.settle();
      expect(graphPosts).toBe(posts);
      expect(ctx.ng.graphViewModel).toBe(model);
      expect(conns[0].type).toBe(CONN);
      expect(el.style.left).toBe("400px");
      expect(el.style.top).toBe("40px");

      // katavorio's stop reports the element's current position
      opts.stop({ el, e: new MouseEvent("mouseup"), pos: [parseInt(el.style.left, 10),
         parseInt(el.style.top, 10)] });
      pendingMove?.();
      await ctx.until(() => moves.length > 0);
      expect(moves.length).toBe(1);
      expect(moves[0].table).toBe("PRODUCTS");
      expect(moves[0].bounds).toMatchObject({ x: 400, y: 40 });
   });

   it("keeps a dropped table at its new position after the move is saved (4b)", async () => {
      const ctx = await setup();
      await highlight(ctx, ORDERS_CUSTOMERS);
      const el = ctx.nodeEl("PRODUCTS");
      const posts = graphPosts;

      mousedown(el);
      ctx.fixture.detectChanges();
      const opts = draggables.get(el);
      opts.start({ el, e: new MouseEvent("mousemove"), pos: [300, 0] });
      moveTo(el, 400, 40);
      opts.drag({ el, e: null, pos: [400, 40] });
      opts.stop({ el, e: new MouseEvent("mouseup"), pos: [400, 40] });
      pendingMove?.();
      await ctx.until(() => moves.length > 0);
      expect(moves.length).toBe(1);

      // no stale graph response can arrive afterwards and put the table back
      await ctx.settle();
      await ctx.settle();
      await ctx.settle();
      expect(graphPosts).toBe(posts);
      expect(el.style.left).toBe("400px");
      expect(el.style.top).toBe("40px");
      const products = ctx.ng.graphViewModel.graphs.find(g => g.node.id === "PRODUCTS");
      expect(products.bounds).toMatchObject({ x: 400, y: 40 });
   });
});
