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
 * Bug #78085 - the query link graph refreshes after unmasked actions (a table drop, a join
 * change), so a refresh can overlap another one or a node drag:
 *  - a refresh landing mid-drag must keep the dragged node under the cursor, and the
 *    release must still send the move;
 *  - the response of a graph request sent before the move PUT must not put the moved node
 *    back;
 *  - the response of an older request must not replace the graph applied from a newer one;
 *  - after the drag ends the pane is no longer "moving": the other nodes are not dimmed
 *    (opacity 0.5 via the global .ws-assembly-graph-element--dimmed rule) and a later
 *    refresh moves the node to its server position again.
 *
 * Renders the real QueryLinkGraphPane -> QueryNetworkGraphPane -> JoinNodeGraph chain with
 * the REAL jsPlumb/katavorio, driven with DOM mouse events. HTTP goes through MSW; each
 * graph POST builds its body from the mock server state when it arrives and is then held
 * on its own gate. The table add is the real QueryNetworkGraphPaneComponent.addTables (what
 * a tree drop calls): the loading mask is shown only from the graph POST on.
 *
 * Harness notes (same as the physical view mid-drag-refresh spec):
 *  - jsdom has no layout: offsetLeft/Top read style.left/top, sizes are fixed. The tests
 *    compare against the position the node has mid-drag instead of a computed one.
 *  - the move-node debounce is captured and flushed by the test.
 *  - waits poll with setTimeout + detectChanges (testing-library waitFor on MSW-driven
 *    state hangs the vitest worker).
 */

import { Component, NO_ERRORS_SCHEMA } from "@angular/core";
import { provideHttpClient } from "@angular/common/http";
import { By } from "@angular/platform-browser";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { render } from "@testing-library/angular";
import { http, HttpResponse } from "msw";
import { server } from "@test-mocks/server";
import { QueryLinkGraphPaneComponent } from "./query-link-graph-pane.component";
import { QueryNetworkGraphPaneComponent } from "../query-network-graph-pane/query-network-graph-pane.component";
import { JoinNodeGraphComponent } from "../../../../common-components/join-node-graph/join-node-graph.component";
import { DataQueryModelService } from "../../../data-query-model.service";
import { DebounceService } from "../../../../../../../../widget/services/debounce.service";
import { FixedDropdownService } from "../../../../../../../../widget/fixed-dropdown/fixed-dropdown.service";
import { DomService } from "../../../../../../../../widget/dom-service/dom.service";
import { DragService } from "../../../../../../../../widget/services/drag.service";

const MOVE_KEY = "physical-model-graph-move-node";
let NAMES = ["ORDERS", "CUSTOMERS", "PRODUCTS"];

function jm(table: string, column: string, foreignTable: string): any {
   return { type: "=", orderPriority: 1, weak: false, cycle: false, mergingRule: "and",
      cardinality: "1:1", table, column, foreignTable, foreignColumn: "ID", baseJoin: false };
}

function graph(name: string, bounds: {x: number, y: number}, output: any[], input: any[]): any {
   return {
      node: { id: name, name, tableName: name, label: name, tooltip: name,
         treeLink: "/db/" + name, aliasSource: null, outgoingAliasSource: null },
      edge: { input, output },
      cols: [{ name: "ID" }, { name: "CID" }, { name: "PID" }],
      bounds: { x: bounds.x, y: bounds.y, width: 100, height: 50 },
      showColumns: false, alias: false, autoAlias: false, sql: false, baseTable: false,
      autoAliasByOutgoing: false, designModeAlias: false,
   };
}

const protoDesc: any = {};

function shim(name: string, get: (this: HTMLElement) => any): void {
   protoDesc[name] = Object.getOwnPropertyDescriptor(HTMLElement.prototype, name);
   Object.defineProperty(HTMLElement.prototype, name, { configurable: true, get });
}

let scrollToDesc: any;

beforeAll(() => {
   shim("offsetLeft", function() { return parseFloat(this.style.left) || 0; });
   shim("offsetTop", function() { return parseFloat(this.style.top) || 0; });
   shim("offsetParent", function() { return this.parentElement; });
   shim("offsetWidth", function() { return 100; });
   shim("offsetHeight", function() { return 50; });
   shim("scrollWidth", function() { return 5000; });
   shim("scrollHeight", function() { return 5000; });
   scrollToDesc = Object.getOwnPropertyDescriptor(Element.prototype, "scrollTo");
   (Element.prototype as any).scrollTo = () => {};
});

afterAll(() => {
   Object.keys(protoDesc).forEach(k => protoDesc[k]
      ? Object.defineProperty(HTMLElement.prototype, k, protoDesc[k])
      : delete (HTMLElement.prototype as any)[k]);

   if(scrollToDesc) {
      Object.defineProperty(Element.prototype, "scrollTo", scrollToDesc);
   }
   else {
      delete (Element.prototype as any).scrollTo;
   }
});

@Component({
   selector: "host-q78085",
   template: `<query-link-graph-pane datasource="ds" runtimeId="rt"></query-link-graph-pane>`,
   imports: [QueryLinkGraphPaneComponent]
})
class HostComponent {
   constructor(public svc: DataQueryModelService) {
   }
}

interface Gate {
   p: Promise<void>;
   open: () => void;
}

function gate(): Gate {
   let open: () => void;
   const p = new Promise<void>(r => open = r);
   return { p, open: () => open() };
}

describe("QueryLinkGraphPane - refresh races (Bug #78085)", () => {
   let joins: any[];
   let serverBounds: {[name: string]: {x: number, y: number}};
   let moves: any[];
   let gates: Gate[];
   let holdGraph: boolean;
   let addGate: Gate | null;
   let pendingMove: (() => void) | null;
   let served: number;

   beforeEach(() => {
      NAMES = ["ORDERS", "CUSTOMERS", "PRODUCTS"];
      joins = [jm("ORDERS", "CID", "CUSTOMERS"), jm("PRODUCTS", "CID", "CUSTOMERS")];
      serverBounds = { ORDERS: { x: 0, y: 0 }, CUSTOMERS: { x: 150, y: 3 },
         PRODUCTS: { x: 300, y: 6 } };
      moves = [];
      gates = [];
      holdGraph = false;
      addGate = null;
      pendingMove = null;
      served = 0;

      server.use(
         http.post("*/api/data/datasource/query/graph", async () => {
            const graphs = NAMES.map(n => graph(n, { ...serverBounds[n] },
               joins.filter(j => j.table === n).map(j => ({ id: n, joinModel: j })),
               joins.filter(j => j.foreignTable === n).map(j => ({ id: j.table, joinModel: j }))));
            const body = { joinEdit: false, joinEditPaneModel: null, graphViewModel: { graphs } };

            if(holdGraph) {
               const g = gate();
               gates.push(g);
               await g.p;
            }

            served++;
            return HttpResponse.json(body);
         }),
         http.put("*/api/data/datasource/query/table/move", async ({ request }) => {
            const move: any = await request.json();
            moves.push(move);
            serverBounds[move.table ?? move.name] = { x: move.bounds.x, y: move.bounds.y };
            return HttpResponse.json(null);
         }),
         http.post("*/api/data/datasource/query/table/add", async () => {
            if(addGate) {
               await addGate.p;
            }

            NAMES.push("ITEMS");
            serverBounds.ITEMS = { x: 450, y: 9 };
            return HttpResponse.json(null);
         }),
         http.get("*/api/data/query/heartbeat", () => HttpResponse.json(null)),
      );
   });

   afterEach(() => {
      vi.restoreAllMocks();
   });

   async function setup() {
      vi.spyOn(QueryNetworkGraphPaneComponent.prototype as any, "setRepaintTimer")
         .mockImplementation(() => {});
      const debounce = (key: string, fn: () => void) => {
         if(key === MOVE_KEY) {
            pendingMove = fn;
         }
         else {
            fn();
         }
      };

      const { fixture } = await render(HostComponent, {
         schemas: [NO_ERRORS_SCHEMA],
         providers: [
            provideHttpClient(), DataQueryModelService,
            { provide: DebounceService, useValue: { debounce } },
            { provide: NgbModal, useValue: { open: vi.fn() } },
            { provide: FixedDropdownService, useValue: { open: vi.fn() } },
            { provide: DragService, useValue: { getDragDataValues: vi.fn() } },
            { provide: DomService, useValue: new Proxy({}, {
               get: (_t, k) => k === "requestRead" || k === "requestWrite"
                  ? (fn: any) => fn() : () => {}
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
      const ngc = () => fixture.debugElement.query(By.directive(QueryNetworkGraphPaneComponent))
         ?.componentInstance as any;
      const nodeCount = () => fixture.debugElement.queryAll(By.directive(JoinNodeGraphComponent)).length;
      await until(() => nodeCount() >= 3 && ngc()?.jsp?.getAllConnections().length === joins.length);
      await settle();

      const nodeDe = (name: string) => fixture.debugElement
         .queryAll(By.directive(JoinNodeGraphComponent))
         .find(d => d.componentInstance.graph.node.id === name);
      const nodeEl = (name: string): HTMLElement => nodeDe(name)?.nativeElement;
      const ids = () => ngc().graphViewModel.graphs.map((g: any) => g.node.id);

      const pane = () => fixture.debugElement.query(By.directive(QueryLinkGraphPaneComponent))
         .componentInstance as QueryLinkGraphPaneComponent;

      return { fixture, settle, until, ngc, nodeEl, nodeDe, ids, pane,
         svc: fixture.componentInstance.svc };
   }

   const fire = (target: EventTarget, type: string, x: number, y: number) => {
      const e = new MouseEvent(type, { bubbles: true, cancelable: true, button: 0,
         clientX: x, clientY: y } as any);

      if((e as any).pageX !== x) {
         Object.defineProperty(e, "pageX", { value: x });
         Object.defineProperty(e, "pageY", { value: y });
      }

      target.dispatchEvent(e);
   };
   const pos = (el: HTMLElement) => el.style.left + "," + el.style.top;
   const flushMove = () => {
      if(pendingMove) {
         const fn = pendingMove;
         pendingMove = null;
         fn();
      }
   };
   const addTable = (ctx: any) => ctx.ngc().addTables({ position: { x: 450, y: 9 },
      data: [{ path: "ITEMS" }] });

   async function startDrag(ctx: any, el: HTMLElement) {
      fire(el.querySelector(".jsplumb-draggable-handle"), "mousedown", 10, 10);
      ctx.fixture.detectChanges();
      fire(document, "mousemove", 60, 30);
      fire(document, "mousemove", 110, 50);
      await ctx.settle();
   }

   async function release(ctx: any) {
      fire(document, "mouseup", 110, 50);
      flushMove();
      await ctx.until(() => moves.length > 0);
   }

   it("keeps the dragged node under the cursor when a refresh lands mid-drag", async () => {
      const ctx = await setup();
      const el = ctx.nodeEl("PRODUCTS");
      const start = pos(el);
      addGate = gate();
      holdGraph = true;
      addTable(ctx);
      await ctx.settle();

      await startDrag(ctx, el);
      const dragged = pos(el);
      expect(dragged).not.toBe(start);

      addGate.open();
      await ctx.until(() => gates.length === 1);
      const oldModel = ctx.ngc().graphViewModel;
      gates[0].open();
      await ctx.until(() => ctx.ngc().graphViewModel !== oldModel);
      expect(ctx.ids()).toContain("ITEMS");
      expect(pos(el)).toBe(dragged);

      // the release still sends the move
      await release(ctx);
      expect(moves.length).toBe(1);
      expect(moves[0].bounds.x + "px," + moves[0].bounds.y + "px").toBe(dragged);
      expect(pos(el)).toBe(dragged);
   });

   it("keeps the moved position when the response requested before the move lands", async () => {
      const ctx = await setup();
      const el = ctx.nodeEl("PRODUCTS");
      addGate = gate();
      holdGraph = true;
      addTable(ctx);
      await ctx.settle();
      await startDrag(ctx, el);
      const dragged = pos(el);

      // add returns, graph request sent mid-drag and held (built before the move)
      addGate.open();
      await ctx.until(() => gates.length === 1);

      await release(ctx);
      expect(pos(el)).toBe(dragged);

      const oldModel = ctx.ngc().graphViewModel;
      gates[0].open();
      await ctx.until(() => ctx.ngc().graphViewModel !== oldModel);
      const b = ctx.ngc().graphViewModel.graphs.find((g: any) => g.node.id === "PRODUCTS").bounds;
      expect(ctx.ids()).toContain("ITEMS");
      expect(pos(el)).toBe(dragged);
      expect(b.x + "px," + b.y + "px").toBe(dragged);
   });

   it("ends the move on release: no node stays dimmed", async () => {
      const ctx = await setup();
      const el = ctx.nodeEl("PRODUCTS");
      await startDrag(ctx, el);
      expect(ctx.nodeEl("ORDERS").classList).toContain("ws-assembly-graph-element--dimmed");

      await release(ctx);
      expect(ctx.ngc().nodeMoving).toBe(false);
      expect(ctx.ngc().isNodeDragging(ctx.nodeDe("PRODUCTS").componentInstance.graph))
         .toBe(false);
      expect(ctx.nodeEl("ORDERS").classList).not.toContain("ws-assembly-graph-element--dimmed");
      expect(ctx.nodeEl("CUSTOMERS").classList)
         .not.toContain("ws-assembly-graph-element--dimmed");
   });

   it("moves a dragged and released node to its new server position on a later refresh",
      async () => {
         const ctx = await setup();
         const el = ctx.nodeEl("PRODUCTS");
         await startDrag(ctx, el);
         await release(ctx);
         const dragged = pos(el);

         // the node stays selected; the server moves it (e.g. another change of the query)
         expect(ctx.nodeDe("PRODUCTS").componentInstance.selected).toBe(true);
         serverBounds.PRODUCTS = { x: 600, y: 200 };
         // where the node is when it registers again (jsPlumb anchors its connections
         // there): a node that is not dragged takes the new position before that
         const registeredAt: string[] = [];
         const register = ctx.ngc().registerNode.bind(ctx.ngc());
         vi.spyOn(ctx.ngc(), "registerNode").mockImplementation((g: any, e: any) => {
            if(g.node.id === "PRODUCTS") {
               registeredAt.push(pos(e));
            }

            register(g, e);
         });
         const oldModel = ctx.ngc().graphViewModel;
         ctx.svc.emitGraphViewChange();
         await ctx.until(() => ctx.ngc().graphViewModel !== oldModel);

         expect(dragged).not.toBe("600px,200px");
         expect(registeredAt).toEqual(["600px,200px"]);
         expect(pos(el)).toBe("600px,200px");
      });

   it("keeps the newer table set when the older response lands last", async () => {
      const ctx = await setup();
      holdGraph = true;
      ctx.svc.emitGraphViewChange();
      await ctx.until(() => gates.length === 1);
      NAMES.push("ITEMS");
      serverBounds.ITEMS = { x: 450, y: 9 };
      ctx.svc.emitGraphViewChange();
      await ctx.until(() => gates.length === 2);

      const m0 = ctx.ngc().graphViewModel;
      gates[1].open();
      await ctx.until(() => ctx.ngc().graphViewModel !== m0);
      expect(ctx.ids()).toContain("ITEMS");
      expect(ctx.pane().loadingGraphPane).toBe(false);

      gates[0].open();
      await ctx.until(() => served === 2);
      await ctx.settle();
      expect(ctx.ids()).toContain("ITEMS");
      expect(ctx.nodeEl("ITEMS")).toBeTruthy();
      expect(ctx.pane().loadingGraphPane).toBe(false);
   });

   it("keeps loading until the latest response lands", async () => {
      const ctx = await setup();
      holdGraph = true;
      ctx.svc.emitGraphViewChange();
      await ctx.until(() => gates.length === 1);
      NAMES.push("ITEMS");
      serverBounds.ITEMS = { x: 450, y: 9 };
      ctx.svc.emitGraphViewChange();
      await ctx.until(() => gates.length === 2);
      expect(ctx.pane().loadingGraphPane).toBe(true);

      const m0 = ctx.ngc().graphViewModel;
      gates[0].open();
      await ctx.until(() => ctx.ngc().graphViewModel !== m0);
      expect(ctx.ids()).not.toContain("ITEMS");
      expect(ctx.pane().loadingGraphPane).toBe(true);

      const m1 = ctx.ngc().graphViewModel;
      gates[1].open();
      await ctx.until(() => ctx.ngc().graphViewModel !== m1);
      expect(ctx.ids()).toContain("ITEMS");
      expect(ctx.pane().loadingGraphPane).toBe(false);
   });
});
