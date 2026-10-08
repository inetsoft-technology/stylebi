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
 * Bug #78085 - more refresh races in the query link graph (see refresh-race.tl.spec.ts):
 *  - three graph requests in flight, answered in different orders or with a failing one:
 *    the newest response wins and the loading mask stays until the latest request ends;
 *  - the selection survives a refresh by node id: a multi-node drag across a refresh keeps
 *    and moves every selected node, a refresh drops a removed table from the selection, and
 *    deleting selected tables while other refreshes are in flight removes exactly them (an
 *    older response does not bring a deleted table back).
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
   // another spec can leave an own HTMLElement.prototype.scrollTo (undefined) that shadows
   // the Element one in a shared worker: override it too (restored in afterAll)
   protoDesc.scrollTo = Object.getOwnPropertyDescriptor(HTMLElement.prototype, "scrollTo");
   Object.defineProperty(HTMLElement.prototype, "scrollTo",
      { configurable: true, writable: true, value: () => {} });
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

describe("QueryLinkGraphPane - tester attacks (Bug #78085)", () => {
   let joins: any[];
   let serverBounds: {[name: string]: {x: number, y: number}};
   let moves: any[];
   let gates: Gate[];
   let holdGraph: boolean;
   let addGate: Gate | null;
   let pendingMove: (() => void) | null;
   let served: number;
   let fail: Set<number>;
   let removed: any[];
   let removeGate: Gate | null;

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
      fail = new Set<number>();
      removed = [];
      removeGate = null;

      server.use(
         http.post("*/api/data/datasource/query/graph", async () => {
            const graphs = NAMES.map(n => graph(n, { ...serverBounds[n] },
               joins.filter(j => j.table === n).map(j => ({ id: n, joinModel: j })),
               joins.filter(j => j.foreignTable === n).map(j => ({ id: j.table, joinModel: j }))));
            const body = { joinEdit: false, joinEditPaneModel: null, graphViewModel: { graphs } };

            let idx = -1;

            if(holdGraph) {
               const g = gate();
               idx = gates.length;
               gates.push(g);
               await g.p;
            }

            served++;

            if(fail.has(idx)) {
               return new HttpResponse(null, { status: 500 });
            }
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
         http.post("*/api/data/datasource/query/table/remove", async ({ request }) => {
            const ev: any = await request.json();
            removed.push(ev);

            if(removeGate) {
               await removeGate.p;
            }

            const names = ev.tables.map((t: any) => t.fullName);
            NAMES = NAMES.filter(n => !names.includes(n));
            joins = joins.filter(j => !names.includes(j.table) && !names.includes(j.foreignTable));
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
            { provide: NgbModal, useValue: { open: vi.fn(() => ({ close: () => {}, componentInstance:
               { onCommit: { subscribe: () => ({ unsubscribe: () => {} }) } },
               result: Promise.resolve("ok") })) } },
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


   // three refreshes in flight; the server adds ITEMS before #2 and LINES before #3
   async function threeRefreshes(ctx: any) {
      holdGraph = true;
      ctx.svc.emitGraphViewChange();
      await ctx.until(() => gates.length === 1);
      NAMES.push("ITEMS");
      serverBounds.ITEMS = { x: 450, y: 9 };
      ctx.svc.emitGraphViewChange();
      await ctx.until(() => gates.length === 2);
      NAMES.push("LINES");
      serverBounds.LINES = { x: 600, y: 12 };
      ctx.svc.emitGraphViewChange();
      await ctx.until(() => gates.length === 3);
      expect(ctx.pane().loadingGraphPane).toBe(true);
   }

   const select = (ctx: any, name: string, ctrlKey: boolean) =>
      ctx.ngc().selectNode(new MouseEvent("mousedown", { ctrlKey }),
         ctx.nodeDe(name).componentInstance.graph);
   const selectedIds = (ctx: any) => ctx.ngc().dragNodes.map((g: any) => g.node.id);

   it("T1 three responses newest-first then oldest: newest kept, mask cleared by #3", async () => {
      const ctx = await setup();
      await threeRefreshes(ctx);

      const m0 = ctx.ngc().graphViewModel;
      gates[2].open();
      await ctx.until(() => ctx.ngc().graphViewModel !== m0);
      expect(ctx.ids()).toEqual(["ORDERS", "CUSTOMERS", "PRODUCTS", "ITEMS", "LINES"]);
      expect(ctx.pane().loadingGraphPane).toBe(false);
      const m3 = ctx.ngc().graphViewModel;

      gates[0].open();
      await ctx.until(() => served === 2);
      gates[1].open();
      await ctx.until(() => served === 3);
      await ctx.settle();
      expect(ctx.ngc().graphViewModel).toBe(m3);
      expect(ctx.nodeEl("LINES")).toBeTruthy();
      expect(ctx.nodeEl("ITEMS")).toBeTruthy();
      expect(ctx.pane().loadingGraphPane).toBe(false);
   });

   it("T2 three responses in order: mask stays until #3", async () => {
      const ctx = await setup();
      await threeRefreshes(ctx);

      let m = ctx.ngc().graphViewModel;
      gates[0].open();
      await ctx.until(() => ctx.ngc().graphViewModel !== m);
      expect(ctx.pane().loadingGraphPane).toBe(true);
      m = ctx.ngc().graphViewModel;
      gates[1].open();
      await ctx.until(() => ctx.ngc().graphViewModel !== m);
      expect(ctx.ids()).toContain("ITEMS");
      expect(ctx.ids()).not.toContain("LINES");
      expect(ctx.pane().loadingGraphPane).toBe(true);
      m = ctx.ngc().graphViewModel;
      gates[2].open();
      await ctx.until(() => ctx.ngc().graphViewModel !== m);
      expect(ctx.ids()).toContain("LINES");
      expect(ctx.pane().loadingGraphPane).toBe(false);
   });

   it("T3 a failing middle request neither clears the mask nor blocks the newest", async () => {
      const ctx = await setup();
      fail.add(1);
      await threeRefreshes(ctx);
      const m0 = ctx.ngc().graphViewModel;

      gates[1].open();
      await ctx.until(() => served === 1);
      await ctx.settle();
      expect(ctx.ngc().graphViewModel).toBe(m0);
      expect(ctx.pane().loadingGraphPane).toBe(true);

      gates[2].open();
      await ctx.until(() => ctx.ngc().graphViewModel !== m0);
      expect(ctx.ids()).toContain("LINES");
      expect(ctx.pane().loadingGraphPane).toBe(false);
      const m3 = ctx.ngc().graphViewModel;

      gates[0].open();
      await ctx.until(() => served === 3);
      await ctx.settle();
      expect(ctx.ngc().graphViewModel).toBe(m3);
   });

   it("T4 the latest request failing clears the mask; an older success still applies", async () => {
      const ctx = await setup();
      fail.add(1);
      holdGraph = true;
      ctx.svc.emitGraphViewChange();
      await ctx.until(() => gates.length === 1);
      NAMES.push("ITEMS");
      serverBounds.ITEMS = { x: 450, y: 9 };
      ctx.svc.emitGraphViewChange();
      await ctx.until(() => gates.length === 2);

      gates[1].open();
      await ctx.until(() => served === 1);
      await ctx.settle();
      expect(ctx.pane().loadingGraphPane).toBe(false);

      const m0 = ctx.ngc().graphViewModel;
      gates[0].open();
      await ctx.until(() => ctx.ngc().graphViewModel !== m0);
      expect(ctx.ids()).toEqual(["ORDERS", "CUSTOMERS", "PRODUCTS"]);
      expect(ctx.pane().loadingGraphPane).toBe(false);
   });

   it("T5 multi-select drag: a refresh mid-drag keeps both nodes, release moves both", async () => {
      const ctx = await setup();
      select(ctx, "PRODUCTS", false);
      select(ctx, "ORDERS", true);
      ctx.fixture.detectChanges();
      expect(selectedIds(ctx)).toEqual(["PRODUCTS", "ORDERS"]);
      const oStart = pos(ctx.nodeEl("ORDERS"));
      const el = ctx.nodeEl("PRODUCTS");

      addGate = gate();
      holdGraph = true;
      addTable(ctx);
      await ctx.settle();
      await startDrag(ctx, el);
      const pDragged = pos(el);
      const oDragged = pos(ctx.nodeEl("ORDERS"));
      expect(oDragged).not.toBe(oStart);

      addGate.open();
      await ctx.until(() => gates.length === 1);
      const oldModel = ctx.ngc().graphViewModel;
      gates[0].open();
      await ctx.until(() => ctx.ngc().graphViewModel !== oldModel);
      expect(ctx.ids()).toContain("ITEMS");
      expect(pos(el)).toBe(pDragged);
      expect(pos(ctx.nodeEl("ORDERS"))).toBe(oDragged);
      expect(selectedIds(ctx).sort()).toEqual(["ORDERS", "PRODUCTS"]);
      // the selection points at the new model's objects
      const graphs = ctx.ngc().graphViewModel.graphs;
      ctx.ngc().dragNodes.forEach((g: any) => expect(graphs).toContain(g));

      fire(document, "mouseup", 110, 50);
      flushMove();
      await ctx.until(() => moves.length >= 2);
      const byName: any = {};
      moves.forEach(m => byName[m.table ?? m.name] = m.bounds.x + "px," + m.bounds.y + "px");
      expect(Object.keys(byName).sort()).toEqual(["ORDERS", "PRODUCTS"]);
      expect(byName.PRODUCTS).toBe(pDragged);
      expect(byName.ORDERS).toBe(oDragged);
      await ctx.settle();
      expect(ctx.ngc().nodeMoving).toBe(false);
      expect(ctx.nodeEl("CUSTOMERS").classList).not.toContain("ws-assembly-graph-element--dimmed");
      expect(ctx.nodeEl("ITEMS").classList).not.toContain("ws-assembly-graph-element--dimmed");
   });

   it("T6 a refresh that drops a selected table drops it from the selection", async () => {
      const ctx = await setup();
      select(ctx, "PRODUCTS", false);
      select(ctx, "ORDERS", true);
      NAMES = NAMES.filter(n => n !== "ORDERS");
      joins = joins.filter(j => j.table !== "ORDERS");
      const oldModel = ctx.ngc().graphViewModel;
      ctx.svc.emitGraphViewChange();
      await ctx.until(() => ctx.ngc().graphViewModel !== oldModel);
      expect(ctx.ids()).toEqual(["CUSTOMERS", "PRODUCTS"]);
      expect(selectedIds(ctx)).toEqual(["PRODUCTS"]);

      await startDrag(ctx, ctx.nodeEl("PRODUCTS"));
      await release(ctx);
      expect(moves.map(m => m.table ?? m.name)).toEqual(["PRODUCTS"]);
   });

   it("T7 delete a selected node while an older refresh is in flight", async () => {
      const ctx = await setup();
      holdGraph = true;
      ctx.svc.emitGraphViewChange();       // #1, built with PRODUCTS
      await ctx.until(() => gates.length === 1);

      select(ctx, "PRODUCTS", false);
      ctx.ngc().removeSelectTables();
      await ctx.until(() => gates.length === 2);  // #2 sent after the remove
      expect(removed.length).toBe(1);
      expect(removed[0].tables.map((t: any) => t.fullName)).toEqual(["PRODUCTS"]);
      expect(selectedIds(ctx)).toEqual([]);

      const m0 = ctx.ngc().graphViewModel;
      gates[1].open();
      await ctx.until(() => ctx.ngc().graphViewModel !== m0);
      expect(ctx.ids()).toEqual(["ORDERS", "CUSTOMERS"]);
      gates[0].open();
      await ctx.until(() => served === 2);
      await ctx.settle();
      expect(ctx.ids()).toEqual(["ORDERS", "CUSTOMERS"]);
      expect(ctx.nodeEl("PRODUCTS")).toBeFalsy();
   });

   it("T8 multi-select delete after a refresh remapped the selection", async () => {
      const ctx = await setup();
      select(ctx, "PRODUCTS", false);
      select(ctx, "ORDERS", true);
      const oldModel = ctx.ngc().graphViewModel;
      ctx.svc.emitGraphViewChange();
      await ctx.until(() => ctx.ngc().graphViewModel !== oldModel);
      expect(selectedIds(ctx)).toEqual(["PRODUCTS", "ORDERS"]);
      expect(ctx.nodeDe("ORDERS").componentInstance.selected).toBe(true);
      expect(ctx.nodeDe("PRODUCTS").componentInstance.selected).toBe(true);

      // remove held: another refresh lands while the remove is in flight
      removeGate = gate();
      ctx.ngc().removeSelectTables();
      await ctx.until(() => removed.length === 1);
      const m1 = ctx.ngc().graphViewModel;
      ctx.svc.emitGraphViewChange();
      await ctx.until(() => ctx.ngc().graphViewModel !== m1);
      expect(selectedIds(ctx)).toEqual(["PRODUCTS", "ORDERS"]);

      const m2 = ctx.ngc().graphViewModel;
      removeGate.open();
      await ctx.until(() => ctx.ngc().graphViewModel !== m2);
      expect(removed[0].tables.map((t: any) => t.fullName).sort()).toEqual(["ORDERS", "PRODUCTS"]);
      expect(ctx.ids()).toEqual(["CUSTOMERS"]);
      expect(selectedIds(ctx)).toEqual([]);
   });
});
