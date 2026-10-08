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
 * Bug #78074 - a graph refresh started by an unmasked action (tree checkbox, join add or
 * remove, auto alias, Clear Join) can land while the user drags a table.
 *
 *  - 4a: the refresh lands mid-drag. The dragged table must stay under the cursor, and a
 *    release without another mousemove must still send the move.
 *  - 4b: the user releases first (the move PUT is sent), then the response of the graph
 *    request sent before the PUT lands. It has the action's change (e.g. the new join), so
 *    it is applied, but the moved table must keep its moved position.
 *
 * Renders the real PhysicalGraphPane -> PhysicalModelNetworkGraphComponent ->
 * JoinNodeGraphComponent chain (and PhysicalTableJoinsComponent for the join actions) with
 * the REAL jsPlumb/katavorio: the drag is driven with DOM mouse events. HTTP goes through
 * MSW; the graph POST and the action's request are held by gates so the test decides when
 * they land. The tree checkbox (and auto alias) reach the pane through
 * DataPhysicalModelService.emitModelChange() once their own request returns, so that is
 * where the test starts them.
 *
 * Harness notes:
 *  - jsdom has no layout: offsetLeft/Top read style.left/top, sizes are fixed. Katavorio's
 *    auto scroll adds 14 px to the drag in jsdom (clientWidth is 0), so the tests compare
 *    against the position the node has mid-drag instead of a computed one.
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

describe("PhysicalGraphPane - Bug #78074 graph refresh landing during a table drag", () => {
   // server state: the graph POST builds its response from it when the request arrives
   let joins: any[];
   let serverBounds: {[name: string]: {x: number, y: number}};
   let moves: any[];
   let graphPosts: number;
   let graphGate: Gate | null;
   let upGate: Gate | null;
   let pendingMove: (() => void) | null;

   beforeEach(() => {
      joins = [jm("ORDERS", "CID", "CUSTOMERS"), jm("PRODUCTS", "CID", "CUSTOMERS")];
      serverBounds = { ORDERS: { x: 0, y: 0 }, CUSTOMERS: { x: 150, y: 3 },
         PRODUCTS: { x: 300, y: 6 } };
      moves = [];
      graphPosts = 0;
      graphGate = null;
      upGate = null;
      pendingMove = null;

      const upstream = async () => {
         if(upGate) {
            await upGate.p;
         }

         return HttpResponse.json(null);
      };

      server.use(
         http.post("*/api/data/physicalmodel/graph", async () => {
            graphPosts++;
            // built now, i.e. before any move PUT that arrives while it is held
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
            serverBounds[move.table] = { x: move.bounds.x, y: move.bounds.y };
            return HttpResponse.json(null);
         }),
         http.post("*/api/data/physicalmodel/join/add", upstream),
         http.put("*/api/data/physicalmodel/*", () => HttpResponse.json(false)),
         http.get("*/api/data/physicalmodel/warnings/*", () => HttpResponse.json(null)),
      );
   });

   afterEach(() => {
      vi.restoreAllMocks();
   });

   async function setup(table: any = null) {
      vi.spyOn(PhysicalModelNetworkGraphComponent.prototype as any, "setRepaintTimer")
         .mockImplementation(() => {});
      vi.spyOn(PhysicalGraphPane.prototype as any, "updateGraphPaneSize")
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
         fixture.detectChanges();
      };
      // bounded poll
      const until = async (cond: () => boolean) => {
         for(let i = 0; i < 40 && !cond(); i++) {
            await settle();
         }

         await settle();
      };
      const ngc = () => fixture.debugElement.query(By.directive(PhysicalModelNetworkGraphComponent))
         ?.componentInstance as any;
      const nodeCount = () => fixture.debugElement.queryAll(By.directive(JoinNodeGraphComponent)).length;
      await until(() => nodeCount() >= 3 && ngc()?.jsp?.getAllConnections().length === joins.length);
      await settle();

      const nodeEl = (name: string): HTMLElement => fixture.debugElement
         .queryAll(By.directive(JoinNodeGraphComponent))
         .find(d => d.componentInstance.graph.node.id === name).nativeElement;
      const joinsComp = () => fixture.debugElement.query(By.directive(PhysicalTableJoinsComponent))
         ?.componentInstance as any;
      const modelBounds = (name: string) => ngc().graphViewModel.graphs
         .find((g: any) => g.node.id === name).bounds;
      const connections = () => ngc().jsp.getAllConnections().length;

      return { fixture, settle, until, ngc, nodeEl, joinsComp, modelBounds, connections,
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
   const movesSent = () => moves.map(m => m.table + "@" + m.bounds.x + "px," + m.bounds.y + "px");

   const ordersTable = () => ({ name: "ORDERS", catalog: "", schema: "", qualifiedName: "ORDERS",
      path: "ORDERS", alias: "", sql: "", type: null, baseTable: true,
      joins: joins.filter(j => j.table === "ORDERS") });

   interface Trigger {
      name: string;
      table?: () => any;
      // the action's request is held until the test opens upGate
      upstream: boolean;
      // the action's change on the server, applied before its graph request is built
      change: () => void;
      start: (ctx: any) => void;
   }

   // the tree checkbox's table POST (and auto alias's PUT) end in emitModelChange()
   const treeCheckbox: Trigger = {
      name: "tree checkbox",
      upstream: false,
      change: () => {
         NAMES.push("ITEMS");
         serverBounds.ITEMS = { x: 450, y: 9 };
      },
      start: (ctx) => ctx.svc.emitModelChange(false),
   };
   // Joins pane add: POST join/add held, then tableChange -> emitModelChange()
   const joinAdd: Trigger = {
      name: "join add",
      table: ordersTable,
      upstream: true,
      change: () => joins = [...joins, jm("ORDERS", "PID", "PRODUCTS")],
      start: (ctx) => ctx.joinsComp().addJoinToTable(jm("ORDERS", "PID", "PRODUCTS")),
   };

   afterEach(() => {
      const i = NAMES.indexOf("ITEMS");

      if(i >= 0) {
         NAMES.splice(i, 1);
      }
   });

   /** Start the action (held), start dragging PRODUCTS, let the action's request land. */
   async function dragDuringAction(trigger: Trigger) {
      const ctx = await setup(trigger.table ? trigger.table() : null);
      const el = ctx.nodeEl("PRODUCTS");
      const start = pos(el);
      const conns0 = ctx.connections();
      graphGate = gate();
      upGate = trigger.upstream ? gate() : null;

      if(trigger.upstream) {
         trigger.start(ctx);
         ctx.fixture.detectChanges();
         await ctx.settle();
      }

      fire(el.querySelector(".jsplumb-draggable-handle"), "mousedown", 10, 10);
      ctx.fixture.detectChanges();
      fire(document, "mousemove", 60, 30);
      fire(document, "mousemove", 110, 50);
      await ctx.settle();
      const dragged = pos(el);
      expect(dragged).not.toBe(start);

      // the action's request returns mid-drag and the graph POST is sent
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

   for(const trigger of [treeCheckbox, joinAdd]) {
      it(`4a ${trigger.name}: the refresh landing mid-drag keeps the table under the cursor ` +
         "and the release sends the move", async () =>
      {
         const { ctx, el, dragged } = await dragDuringAction(trigger);
         const oldModel = ctx.ngc().graphViewModel;

         graphGate.open();
         await ctx.until(() => ctx.ngc().graphViewModel !== oldModel);
         expect(pos(el)).toBe(dragged);

         // release without moving again
         fire(document, "mouseup", 110, 50);
         flushMove();
         await ctx.until(() => moves.length > 0);

         expect(movesSent()).toEqual(["PRODUCTS@" + dragged]);
         expect(pos(el)).toBe(dragged);
      });

      it(`4b ${trigger.name}: the response requested before the move PUT keeps the moved ` +
         "position and still applies the action", async () =>
      {
         const { ctx, el, dragged, conns0 } = await dragDuringAction(trigger);
         const oldModel = ctx.ngc().graphViewModel;

         fire(document, "mouseup", 110, 50);
         flushMove();
         await ctx.until(() => moves.length > 0);
         expect(movesSent()).toEqual(["PRODUCTS@" + dragged]);
         expect(pos(el)).toBe(dragged);

         // the stale response (built before the PUT) lands now
         graphGate.open();
         await ctx.until(() => ctx.ngc().graphViewModel !== oldModel);

         expect(pos(el)).toBe(dragged);
         const bounds = ctx.modelBounds("PRODUCTS");
         expect(bounds.x + "px," + bounds.y + "px").toBe(dragged);

         // the action's change is shown
         if(trigger === joinAdd) {
            await ctx.until(() => ctx.connections() === conns0 + 1);
            expect(ctx.connections()).toBe(conns0 + 1);
         }
         else {
            expect(ctx.ngc().graphViewModel.graphs.map((g: any) => g.node.id)).toContain("ITEMS");
         }

         // a later refresh (requested after the PUT) agrees with the server
         graphGate = null;
         const model2 = ctx.ngc().graphViewModel;
         ctx.svc.emitModelChange(false);
         await ctx.until(() => ctx.ngc().graphViewModel !== model2);
         expect(pos(el)).toBe(dragged);
      });
   }

   it("a refresh landing mid-drag still moves the tables that are not dragged and adds the " +
      "new join line", async () =>
   {
      const { ctx, el, dragged, conns0 } = await dragDuringAction({
         ...joinAdd,
         change: () => {
            joinAdd.change();
            serverBounds.ORDERS = { x: 40, y: 200 };
         }
      });
      const oldModel = ctx.ngc().graphViewModel;

      graphGate.open();
      await ctx.until(() => ctx.ngc().graphViewModel !== oldModel);
      await ctx.until(() => ctx.connections() === conns0 + 1);

      expect(pos(ctx.nodeEl("ORDERS"))).toBe("40px,200px");
      expect(pos(ctx.nodeEl("CUSTOMERS"))).toBe("150px,3px");
      expect(ctx.connections()).toBe(conns0 + 1);
      expect(pos(el)).toBe(dragged);

      fire(document, "mouseup", 110, 50);
      flushMove();
      await ctx.until(() => moves.length > 0);
      expect(movesSent()).toEqual(["PRODUCTS@" + dragged]);
   });

   it("after the move is saved, a refresh with a new server position (e.g. auto layout) " +
      "moves the table", async () =>
   {
      const ctx = await setup();
      const el = ctx.nodeEl("PRODUCTS");

      fire(el.querySelector(".jsplumb-draggable-handle"), "mousedown", 10, 10);
      ctx.fixture.detectChanges();
      fire(document, "mousemove", 60, 30);
      fire(document, "mousemove", 110, 50);
      await ctx.settle();
      fire(document, "mouseup", 110, 50);
      flushMove();
      await ctx.until(() => moves.length > 0);
      expect(moves.length).toBe(1);

      serverBounds.PRODUCTS = { x: 600, y: 120 };
      serverBounds.ORDERS = { x: 20, y: 300 };
      const model = ctx.ngc().graphViewModel;
      ctx.svc.emitModelChange(false);
      await ctx.until(() => ctx.ngc().graphViewModel !== model);

      expect(pos(el)).toBe("600px,120px");
      expect(pos(ctx.nodeEl("ORDERS"))).toBe("20px,300px");
   });
});
