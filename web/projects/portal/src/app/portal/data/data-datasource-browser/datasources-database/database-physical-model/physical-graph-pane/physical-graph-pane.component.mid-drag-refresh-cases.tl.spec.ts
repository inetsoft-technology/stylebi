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
 * Bug #78074 - more cases for a graph refresh landing during a table drag, on the same
 * harness as physical-graph-pane.component.mid-drag-refresh.tl.spec.ts (real jsPlumb/katavorio,
 * MSW gates): the join remove, Clear Join and auto alias triggers, a multi-table drag, a
 * refresh that removes the dragged table, other tables moved by a stale response, auto layout
 * after a saved move, two quick drags, and a refresh inside the move debounce.
 */

import { Component, NO_ERRORS_SCHEMA } from "@angular/core";
import { provideHttpClient } from "@angular/common/http";
import { By } from "@angular/platform-browser";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { render } from "@testing-library/angular";
import { http, HttpResponse } from "msw";
import { server } from "@test-mocks/server";
import { PhysicalGraphPane } from "./physical-graph-pane.component";
import { PhysicalModelNetworkGraphComponent } from "../physical-model-network-graph/physical-model-network-graph.component";
import { JoinNodeGraphComponent } from "../../common-components/join-node-graph/join-node-graph.component";
import { PhysicalTableJoinsComponent } from "../physical-model-edit-table/physical-table-joins/physical-table-joins.component";
import { DataPhysicalModelService } from "../../../../services/data-physical-model.service";
import { DebounceService } from "../../../../../../widget/services/debounce.service";
import { FixedDropdownService } from "../../../../../../widget/fixed-dropdown/fixed-dropdown.service";
import { DomService } from "../../../../../../widget/dom-service/dom.service";

const MOVE_KEY = "physical-model-graph-move-node";
const NAMES = ["ORDERS", "CUSTOMERS", "PRODUCTS"];

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
   selector: "host-78074",
   template: `
      @if (table) {
         <physical-table-joins [table]="table" [physicalModel]="pm" databaseName="ds"
            (tableChange)="svc.emitModelChange()"></physical-table-joins>
      }
      <physical-graph-pane physicalView="v" datasource="ds" runtimeId="rt"
         [selectedGraphModels]="[]"></physical-graph-pane>
   `,
   imports: [PhysicalGraphPane, PhysicalTableJoinsComponent]
})
class HostComponent {
   table: any = null;
   pm: any = { name: "v", folder: "", tables: [], id: "rt", description: "" };

   constructor(public svc: DataPhysicalModelService) {
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

const ORIG_NAMES = [...NAMES];

describe("PhysicalGraphPane - Bug #78074 more cases for a refresh during a table drag", () => {
   let joins: any[];
   let serverBounds: {[name: string]: {x: number, y: number}};
   let moves: any[];
   let graphPosts: number;
   let graphGate: Gate | null;
   let upGate: Gate | null;
   let moveGate: Gate | null;
   let pendingMove: (() => void) | null;
   let realDebounce: boolean;
   let layoutBounds: {[name: string]: {x: number, y: number}};
   let errors: any[];

   beforeEach(() => {
      NAMES.length = 0;
      NAMES.push(...ORIG_NAMES);
      joins = [jm("ORDERS", "CID", "CUSTOMERS"), jm("PRODUCTS", "CID", "CUSTOMERS")];
      serverBounds = { ORDERS: { x: 0, y: 0 }, CUSTOMERS: { x: 150, y: 3 },
         PRODUCTS: { x: 300, y: 6 } };
      layoutBounds = { ORDERS: { x: 10, y: 400 }, CUSTOMERS: { x: 160, y: 400 },
         PRODUCTS: { x: 700, y: 400 } };
      moves = [];
      graphPosts = 0;
      graphGate = null;
      upGate = null;
      moveGate = null;
      pendingMove = null;
      realDebounce = false;
      errors = [];

      const upstream = async () => {
         if(upGate) {
            await upGate.p;
         }

         return HttpResponse.json(null);
      };

      server.use(
         http.post("*/api/data/physicalmodel/graph", async () => {
            graphPosts++;
            const graphs = NAMES.map(n => graph(n, { ...serverBounds[n] },
               joins.filter(j => j.table === n).map(j => ({ id: n, joinModel: j })),
               joins.filter(j => j.foreignTable === n).map(j => ({ id: j.table, joinModel: j }))));
            const body = { joinEdit: false, joinEditPaneModel: null, graphViewModel: { graphs } };

            if(graphGate) {
               await graphGate.p;
            }

            return HttpResponse.json(body);
         }),
         http.put("*/api/data/physicalmodel/graph/move", async ({ request }) => {
            const move: any = await request.json();
            moves.push(move);
            // the server applies the move on arrival; only the response is held
            serverBounds[move.table] = { x: move.bounds.x, y: move.bounds.y };

            if(moveGate) {
               await moveGate.p;
            }

            return HttpResponse.json(null);
         }),
         http.put("*/api/data/physicalmodel/graph/layout/*", () => {
            Object.keys(layoutBounds).forEach(k => serverBounds[k] = { ...layoutBounds[k] });
            return HttpResponse.json(1);
         }),
         http.post("*/api/data/physicalmodel/join/add", upstream),
         http.put("*/api/data/physicalmodel/join/remove", upstream),
         http.delete("*/api/data/physicalmodel/join/*", upstream),
         http.put("*/api/data/physicalmodel/*", () => HttpResponse.json(false)),
         http.get("*/api/data/physicalmodel/warnings/*", () => HttpResponse.json(null)),
      );
   });

   afterEach(() => {
      vi.restoreAllMocks();
      NAMES.length = 0;
      NAMES.push(...ORIG_NAMES);
   });

   async function setup(table: any = null) {
      vi.spyOn(PhysicalModelNetworkGraphComponent.prototype as any, "setRepaintTimer")
         .mockImplementation(() => {});
      vi.spyOn(PhysicalGraphPane.prototype as any, "updateGraphPaneSize")
         .mockImplementation(() => {});
      const timers: any = {};
      const debounce = (key: string, fn: () => void, delay: number) => {
         if(key === MOVE_KEY && !realDebounce) {
            pendingMove = fn;
         }
         else if(key === MOVE_KEY) {
            clearTimeout(timers[key]);
            timers[key] = setTimeout(fn, delay);
         }
         else {
            fn();
         }
      };

      const { fixture } = await render(HostComponent, {
         schemas: [NO_ERRORS_SCHEMA],
         componentProperties: { table },
         providers: [
            provideHttpClient(), DataPhysicalModelService,
            { provide: DebounceService, useValue: { debounce } },
            { provide: NgbModal, useValue: { open: vi.fn() } },
            { provide: FixedDropdownService, useValue: { open: vi.fn() } },
            { provide: DomService, useValue: new Proxy({}, {
               get: (_t, k) => k === "requestRead" || k === "requestWrite"
                  ? (fn: any) => fn() : () => {}
            }) },
         ],
      });

      const settle = async () => {
         await new Promise(r => setTimeout(r, 50));

         try {
            fixture.detectChanges();
         }
         catch(e) {
            errors.push(e);
         }
      };
      const until = async (cond: () => boolean) => {
         for(let i = 0; i < 40 && !cond(); i++) {
            await settle();
         }

         await settle();
      };
      const ngc = () => fixture.debugElement.query(By.directive(PhysicalModelNetworkGraphComponent))
         ?.componentInstance as any;
      const pane = () => fixture.debugElement.query(By.directive(PhysicalGraphPane))
         ?.componentInstance as any;
      const nodeCount = () => fixture.debugElement.queryAll(By.directive(JoinNodeGraphComponent)).length;
      await until(() => nodeCount() >= 3 && ngc()?.jsp?.getAllConnections().length === joins.length);
      await settle();

      const nodeEl = (name: string): HTMLElement => fixture.debugElement
         .queryAll(By.directive(JoinNodeGraphComponent))
         .find(d => d.componentInstance.graph.node.id === name)?.nativeElement;
      const joinsComp = () => fixture.debugElement.query(By.directive(PhysicalTableJoinsComponent))
         ?.componentInstance as any;
      const modelBounds = (name: string) => ngc().graphViewModel.graphs
         .find((g: any) => g.node.id === name).bounds;
      const connections = () => ngc().jsp.getAllConnections().length;

      return { fixture, settle, until, ngc, pane, nodeEl, joinsComp, modelBounds, connections,
         nodeCount, svc: fixture.componentInstance.svc };
   }

   const fire = (target: EventTarget, type: string, x: number, y: number, ctrl = false) => {
      const e = new MouseEvent(type, { bubbles: true, cancelable: true, button: 0,
         clientX: x, clientY: y, ctrlKey: ctrl } as any);

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
   const movesSent = () => moves.map(m => m.table + "@" + m.bounds.x + "px," + m.bounds.y + "px");
   const handle = (el: HTMLElement) => el.querySelector(".jsplumb-draggable-handle");

   const ordersTable = () => ({ name: "ORDERS", catalog: "", schema: "", qualifiedName: "ORDERS",
      path: "ORDERS", alias: "", sql: "", type: null, baseTable: true,
      joins: joins.filter(j => j.table === "ORDERS") });

   interface Trigger {
      name: string;
      table?: () => any;
      upstream: boolean;
      change: () => void;
      start: (ctx: any) => void;
      expectConns: (conns0: number) => number;
   }

   const joinRemove: Trigger = {
      name: "join remove",
      table: ordersTable,
      upstream: true,
      change: () => joins = joins.filter(j => j.table !== "ORDERS"),
      start: (ctx) => ctx.joinsComp().doRemoveJoinsAction({ items: [], runtimeID: "rt" }),
      expectConns: (c) => c - 1,
   };
   const clearJoin: Trigger = {
      name: "Clear Join",
      upstream: true,
      change: () => joins = [],
      start: (ctx) => ctx.ngc().clearJoin(),
      expectConns: () => 0,
   };
   // auto alias: PUT auto-alias then emitModelChange() (DatabasePhysicalModel not rendered)
   const autoAlias: Trigger = {
      name: "auto alias",
      upstream: false,
      change: () => {
         NAMES.push("ORDERS_1");
         serverBounds.ORDERS_1 = { x: 450, y: 9 };
      },
      start: (ctx) => ctx.svc.emitModelChange(),
      expectConns: (c) => c,
   };
   const joinAdd: Trigger = {
      name: "join add",
      table: ordersTable,
      upstream: true,
      change: () => joins = [...joins, jm("ORDERS", "PID", "PRODUCTS")],
      start: (ctx) => ctx.joinsComp().addJoinToTable(jm("ORDERS", "PID", "PRODUCTS")),
      expectConns: (c) => c + 1,
   };

   function treeCheckboxLike(): Trigger {
      return { name: "tree checkbox", upstream: false,
         change: () => {
            NAMES.push("ITEMS");
            serverBounds.ITEMS = { x: 450, y: 9 };
         },
         start: (ctx) => ctx.svc.emitModelChange(false),
         expectConns: (c) => c };
   }

   async function startDrag(ctx: any, name: string, ctrl = false) {
      const el = ctx.nodeEl(name);
      fire(handle(el), "mousedown", 10, 10, ctrl);
      ctx.fixture.detectChanges();
      fire(document, "mousemove", 60, 30);
      fire(document, "mousemove", 110, 50);
      await ctx.settle();
      return el;
   }

   async function dragDuringAction(trigger: Trigger, before?: (ctx: any) => Promise<void>,
                                   dragName = "PRODUCTS", ctrl = false)
   {
      const ctx = await setup(trigger.table ? trigger.table() : null);

      if(before) {
         await before(ctx);
      }

      const el = ctx.nodeEl(dragName);
      const start = pos(el);
      const conns0 = ctx.connections();
      graphGate = gate();
      upGate = trigger.upstream ? gate() : null;

      if(trigger.upstream) {
         trigger.start(ctx);
         ctx.fixture.detectChanges();
         await ctx.settle();
      }

      await startDrag(ctx, dragName, ctrl);
      const dragged = pos(el);
      expect(dragged).not.toBe(start);

      const posts0 = graphPosts;
      trigger.change();

      if(trigger.upstream) {
         upGate.open();
      }
      else {
         trigger.start(ctx);
      }

      await ctx.until(() => graphPosts > posts0);
      expect(graphPosts).toBe(posts0 + 1);

      return { ctx, el, start, dragged, conns0 };
   }

   for(const trigger of [joinRemove, clearJoin, autoAlias]) {
      it(`4a ${trigger.name}: the refresh landing mid-drag keeps the table and the release sends the move`, async () => {
         const { ctx, el, dragged, conns0 } = await dragDuringAction(trigger);
         const oldModel = ctx.ngc().graphViewModel;
         graphGate.open();
         await ctx.until(() => ctx.ngc().graphViewModel !== oldModel);
         await ctx.until(() => ctx.connections() === trigger.expectConns(conns0));
         expect(pos(el)).toBe(dragged);
         expect(ctx.connections()).toBe(trigger.expectConns(conns0));
         fire(document, "mouseup", 110, 50);
         flushMove();
         await ctx.until(() => moves.length > 0);
         expect(movesSent()).toEqual(["PRODUCTS@" + dragged]);
         expect(pos(el)).toBe(dragged);
         expect(errors).toEqual([]);
      });

      it(`4b ${trigger.name}: the stale response keeps the moved position and applies the action`, async () => {
         const { ctx, el, dragged, conns0 } = await dragDuringAction(trigger);
         const oldModel = ctx.ngc().graphViewModel;
         fire(document, "mouseup", 110, 50);
         flushMove();
         await ctx.until(() => moves.length > 0);
         graphGate.open();
         await ctx.until(() => ctx.ngc().graphViewModel !== oldModel);
         await ctx.until(() => ctx.connections() === trigger.expectConns(conns0));
         expect(pos(el)).toBe(dragged);
         expect(ctx.connections()).toBe(trigger.expectConns(conns0));

         if(trigger === autoAlias) {
            expect(ctx.nodeCount()).toBe(4);
         }

         graphGate = null;
         const m2 = ctx.ngc().graphViewModel;
         ctx.svc.emitModelChange(false);
         await ctx.until(() => ctx.ngc().graphViewModel !== m2);
         expect(pos(el)).toBe(dragged);
         expect(errors).toEqual([]);
      });
   }

   // select PRODUCTS (plain click), then ctrl+drag CUSTOMERS so both move
   const selectProducts = async (ctx: any) => {
      const p = ctx.nodeEl("PRODUCTS");
      fire(handle(p), "mousedown", 10, 10);
      fire(document, "mouseup", 10, 10);
      ctx.fixture.detectChanges();
      await ctx.settle();
   };

   for(const mode of ["4a", "4b"]) {
      it(`${mode} multi-table drag (ctrl-click) across a join add refresh: both tables keep their moved positions`, async () => {
         let p0: string;
         const { ctx, el, dragged } = await dragDuringAction(joinAdd, async (c) => {
            await selectProducts(c);
            p0 = pos(c.nodeEl("PRODUCTS"));
         }, "CUSTOMERS", true);
         const p = ctx.nodeEl("PRODUCTS");
         const pDragged = pos(p);
         expect(pDragged).not.toBe(p0);
         const oldModel = ctx.ngc().graphViewModel;

         if(mode === "4a") {
            graphGate.open();
            await ctx.until(() => ctx.ngc().graphViewModel !== oldModel);
            expect(pos(el)).toBe(dragged);
            expect(pos(p)).toBe(pDragged);
            fire(document, "mouseup", 110, 50);
            flushMove();
            await ctx.until(() => moves.length > 1);
         }
         else {
            fire(document, "mouseup", 110, 50);
            flushMove();
            await ctx.until(() => moves.length > 1);
            graphGate.open();
            await ctx.until(() => ctx.ngc().graphViewModel !== oldModel);
         }

         expect(movesSent().sort()).toEqual(["CUSTOMERS@" + dragged, "PRODUCTS@" + pDragged]);
         expect(pos(el)).toBe(dragged);
         expect(pos(p)).toBe(pDragged);
         expect(errors).toEqual([]);
      });
   }

   it("a refresh that removes the dragged table mid-drag sends no move for it and later refreshes work", async () => {
      const removeProducts: Trigger = {
         name: "tree uncheck PRODUCTS", upstream: false,
         change: () => {
            NAMES.splice(NAMES.indexOf("PRODUCTS"), 1);
            joins = joins.filter(j => j.table !== "PRODUCTS" && j.foreignTable !== "PRODUCTS");
         },
         start: (ctx) => ctx.svc.emitModelChange(false),
         expectConns: (c) => c - 1,
      };
      const { ctx } = await dragDuringAction(removeProducts);
      const oldModel = ctx.ngc().graphViewModel;
      graphGate.open();
      await ctx.until(() => ctx.ngc().graphViewModel !== oldModel);
      expect(ctx.nodeCount()).toBe(2);
      fire(document, "mousemove", 120, 60);
      fire(document, "mouseup", 120, 60);
      flushMove();
      await ctx.settle();
      await ctx.settle();
      expect(moves.filter(m => m.table === "PRODUCTS")).toEqual([]);
      expect(errors).toEqual([]);
      graphGate = null;
      const m2 = ctx.ngc().graphViewModel;
      ctx.svc.emitModelChange(false);
      await ctx.until(() => ctx.ngc().graphViewModel !== m2);
      expect(ctx.nodeCount()).toBe(2);
      expect(pos(ctx.nodeEl("ORDERS"))).toBe("0px,0px");
   });

   it("4b: a stale response that moves other tables moves them, the dragged one keeps its position", async () => {
      const { ctx, el, dragged } = await dragDuringAction({ ...joinAdd, change: () => {
         joinAdd.change();
         serverBounds.ORDERS = { x: 40, y: 200 };
         serverBounds.CUSTOMERS = { x: 170, y: 220 };
      }});
      const oldModel = ctx.ngc().graphViewModel;
      fire(document, "mouseup", 110, 50);
      flushMove();
      await ctx.until(() => moves.length > 0);
      graphGate.open();
      await ctx.until(() => ctx.ngc().graphViewModel !== oldModel);
      expect(pos(ctx.nodeEl("ORDERS"))).toBe("40px,200px");
      expect(pos(ctx.nodeEl("CUSTOMERS"))).toBe("170px,220px");
      expect(pos(el)).toBe(dragged);
   });

   it("auto layout after a saved move moves the table, and later refreshes keep the layout position",
      async () =>
   {
      const ctx = await setup();
      const el = await startDrag(ctx, "PRODUCTS");
      fire(document, "mouseup", 110, 50);
      flushMove();
      await ctx.until(() => moves.length > 0);
      await ctx.settle();
      const m = ctx.ngc().graphViewModel;
      ctx.pane().autoLayout();
      await ctx.until(() => ctx.ngc().graphViewModel !== m);
      expect(pos(el)).toBe("700px,400px");
      expect(ctx.pane().movedNodes?.size ?? 0).toBe(0);
      const m2 = ctx.ngc().graphViewModel;
      ctx.svc.emitModelChange(false);
      await ctx.until(() => ctx.ngc().graphViewModel !== m2);
      expect(pos(el)).toBe("700px,400px");
   });

   it("two quick drags of the same table: a stale response landing after both keeps the second position", async () => {
      const ctx = await setup(ordersTable());
      graphGate = gate();
      upGate = gate();
      joinAdd.change();
      joinAdd.start(ctx);
      ctx.fixture.detectChanges();
      await ctx.settle();
      const el = await startDrag(ctx, "PRODUCTS");
      upGate.open();
      await ctx.until(() => graphPosts === 2);
      moveGate = gate();
      fire(document, "mouseup", 110, 50);
      flushMove();
      await ctx.until(() => moves.length === 1);
      const d1 = pos(el);
      // second drag right away (PUT 1 response still held)
      fire(handle(el), "mousedown", 10, 10);
      ctx.fixture.detectChanges();
      fire(document, "mousemove", 40, 90);
      fire(document, "mousemove", 70, 140);
      await ctx.settle();
      fire(document, "mouseup", 70, 140);
      flushMove();
      await ctx.until(() => moves.length === 2);
      const d2 = pos(el);
      expect(d2).not.toBe(d1);
      const oldModel = ctx.ngc().graphViewModel;
      graphGate.open();
      await ctx.until(() => ctx.ngc().graphViewModel !== oldModel);
      const afterStale = pos(el);
      moveGate.open();
      await ctx.settle();
      await ctx.settle();
      graphGate = null;
      const m2 = ctx.ngc().graphViewModel;
      ctx.svc.emitModelChange(false);
      await ctx.until(() => ctx.ngc().graphViewModel !== m2);
      expect(afterStale).toBe(d2);
      expect(pos(el)).toBe(d2);
      expect(movesSent()[1]).toBe("PRODUCTS@" + d2);
      expect(ctx.pane().movedNodes?.size ?? 0).toBe(0);
   });

   it("a refresh landing inside the move debounce shows the old position only until the " +
      "debounced move runs, then the moved one", async () =>
   {
      realDebounce = true;
      const { ctx, el, dragged } = await dragDuringAction(treeCheckboxLike());
      fire(document, "mouseup", 110, 50);
      const oldModel = ctx.ngc().graphViewModel;
      // the response lands inside the 200 ms debounce (known limit: old position briefly)
      graphGate.open();
      await ctx.until(() => ctx.ngc().graphViewModel !== oldModel);
      await ctx.until(() => moves.length > 0);
      await ctx.settle();
      expect(movesSent()).toEqual(["PRODUCTS@" + dragged]);
      expect(pos(el)).toBe(dragged);
   });

});
