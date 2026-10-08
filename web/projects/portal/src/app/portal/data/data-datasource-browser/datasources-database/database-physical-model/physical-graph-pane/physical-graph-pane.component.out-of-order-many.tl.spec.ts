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
 * Bug #78085 - more out-of-order cases than out-of-order.tl.spec.ts: three graph requests
 * in flight answered in different orders, a failing middle request, and a failing latest
 * request. The newest applied response wins, and the loading mask is cleared only when the
 * latest request completes or fails.
 *
 * Renders the real PhysicalGraphPane -> PhysicalModelNetworkGraphComponent ->
 * JoinNodeGraphComponent chain with the REAL jsPlumb/katavorio. HTTP goes through MSW;
 * each graph POST builds its body from the mock server state when it arrives and is then
 * held on its own gate, so the test decides the order the responses land in.
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
   selector: "host-78085",
   template: `
      <physical-graph-pane physicalView="v" datasource="ds" runtimeId="rt"
         [selectedGraphModels]="[]"></physical-graph-pane>
   `,
   imports: [PhysicalGraphPane]
})
class HostComponent {
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


describe("PhysicalGraphPane - tester attacks (Bug #78085)", () => {
   let joins: any[];
   let serverBounds: {[name: string]: {x: number, y: number}};
   let moves: any[];
   let gates: Gate[];
   let holdGraph: boolean;
   let pendingMove: (() => void) | null;
   let served: number;
   let fail: Set<number>;

   beforeEach(() => {
      joins = [jm("ORDERS", "CID", "CUSTOMERS"), jm("PRODUCTS", "CID", "CUSTOMERS")];
      serverBounds = { ORDERS: { x: 0, y: 0 }, CUSTOMERS: { x: 150, y: 3 },
         PRODUCTS: { x: 300, y: 6 } };
      moves = [];
      gates = [];
      holdGraph = false;
      pendingMove = null;
      served = 0;
      fail = new Set<number>();

      server.use(
         http.post("*/api/data/physicalmodel/graph", async () => {
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
         http.put("*/api/data/physicalmodel/graph/move", async ({ request }) => {
            const move: any = await request.json();
            moves.push(move);
            serverBounds[move.table] = { x: move.bounds.x, y: move.bounds.y };
            return HttpResponse.json(null);
         }),
         http.put("*/api/data/physicalmodel/*", () => HttpResponse.json(false)),
         http.get("*/api/data/physicalmodel/warnings/*", () => HttpResponse.json(null)),
      );
   });

   afterEach(() => {
      vi.restoreAllMocks();
      const i = NAMES.indexOf("ITEMS");

      if(i >= 0) {
         NAMES.splice(i, 1);
      }
   });

   async function setup() {
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
         .find(d => d.componentInstance.graph.node.id === name)?.nativeElement;
      const ids = () => ngc().graphViewModel.graphs.map((g: any) => g.node.id);

      const pane = () => fixture.debugElement.query(By.directive(PhysicalGraphPane))
         .componentInstance as PhysicalGraphPane;

      return { fixture, settle, until, ngc, nodeEl, ids, pane,
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

   // sends two refreshes; the server adds ITEMS between them
   async function twoRefreshes(ctx: any) {
      holdGraph = true;
      ctx.svc.emitModelChange(false);
      await ctx.until(() => gates.length === 1);
      NAMES.push("ITEMS");
      serverBounds.ITEMS = { x: 450, y: 9 };
      ctx.svc.emitModelChange(false);
      await ctx.until(() => gates.length === 2);
   }


   async function threeRefreshes(ctx: any) {
      await twoRefreshes(ctx);
      NAMES.push("LINES");
      serverBounds.LINES = { x: 600, y: 12 };
      ctx.svc.emitModelChange(false);
      await ctx.until(() => gates.length === 3);
      expect(ctx.pane().loadingGraphPane).toBe(true);
   }

   afterEach(() => {
      const i = NAMES.indexOf("LINES");

      if(i >= 0) {
         NAMES.splice(i, 1);
      }
   });

   it("P1 three responses #3, #1, #2: newest kept, mask cleared by #3", async () => {
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
      expect(ctx.pane().loadingGraphPane).toBe(false);
   });

   it("P2 three responses #1, #3, #2: #1 applied with mask kept, #2 discarded", async () => {
      const ctx = await setup();
      await threeRefreshes(ctx);

      let m = ctx.ngc().graphViewModel;
      gates[0].open();
      await ctx.until(() => ctx.ngc().graphViewModel !== m);
      expect(ctx.pane().loadingGraphPane).toBe(true);
      m = ctx.ngc().graphViewModel;
      gates[2].open();
      await ctx.until(() => ctx.ngc().graphViewModel !== m);
      expect(ctx.ids()).toContain("LINES");
      expect(ctx.pane().loadingGraphPane).toBe(false);
      const m3 = ctx.ngc().graphViewModel;
      gates[1].open();
      await ctx.until(() => served === 3);
      await ctx.settle();
      expect(ctx.ngc().graphViewModel).toBe(m3);
   });

   it("P3 a failing middle request keeps the mask until the newest lands", async () => {
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

   it("P4 the latest request failing clears the mask", async () => {
      const ctx = await setup();
      fail.add(1);
      await twoRefreshes(ctx);
      gates[1].open();
      await ctx.until(() => served === 1);
      await ctx.settle();
      expect(ctx.pane().loadingGraphPane).toBe(false);
      const m0 = ctx.ngc().graphViewModel;
      gates[0].open();
      await ctx.until(() => ctx.ngc().graphViewModel !== m0);
      expect(ctx.pane().loadingGraphPane).toBe(false);
   });
});
