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
 * Bug #77995 - a join highlight change restyles the existing jsPlumb connections instead of
 * re-fetching the graph. This spec uses the REAL jsPlumb 2.15.6 instance (connections, types,
 * overlays, SVG paint and katavorio dragging), not jspMock, and the real
 * PhysicalTableJoinsComponent next to the real PhysicalGraphPane, so every caller of
 * highlightConnections() is driven through the real service.
 *
 *  - A: for normal, weak, cycle and weak+cycle joins, the restyled connection's visible state
 *    (types, paint/hover style, overlays, css classes, SVG stroke/dash) equals what connectNode
 *    builds on a refresh with the same highlight, and clearing restores the original state.
 *  - B: each PhysicalTableJoinsComponent caller (selectNode, table setter, updateForeignTables,
 *    ngOnDestroy, addJoinToTable, doRemoveJoinsAction) gives the right highlight; only the
 *    callers that change joins send a graph POST (via tableChange).
 *  - C: a drag with real katavorio during a highlight change is not reset and its move is saved.
 *
 * Harness notes: jsdom has no layout, so offsetLeft/Top/Parent/Width/Height and
 * scrollWidth/Height are shimmed on HTMLElement.prototype; polls with setTimeout +
 * detectChanges (testing-library's waitFor hung the worker on MSW-driven state).
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
const COLOR = "physical-graph-connection-color";
const WEAK = "physical-graph-weak-connection";

function jm(table: string, column: string, foreignTable: string, props: any = {}): any {
   return { type: "=", orderPriority: 1, weak: false, cycle: false, mergingRule: "and",
      cardinality: "1:1", table, column, foreignTable, foreignColumn: "ID", baseJoin: false,
      ...props };
}

function graph(name: string, x: number, output: any[] = [], input: any[] = []): any {
   return {
      node: { id: name, name, tableName: name, label: name, tooltip: name,
         treeLink: "/db/" + name, aliasSource: null, outgoingAliasSource: null },
      edge: { input, output },
      cols: [{ name: "ID" }, { name: "CID" }, { name: "PID" }],
      bounds: { x, y: x % 7, width: 100, height: 50 },
      showColumns: false, alias: false, autoAlias: false, sql: false, baseTable: false,
      autoAliasByOutgoing: false, designModeAlias: false,
   };
}

/** joins: list of joinModels; builds graphs ORDERS/CUSTOMERS/PRODUCTS (+extra). */
function graphResponse(joins: any[], extra: string[] = []): any {
   const names = ["ORDERS", "CUSTOMERS", "PRODUCTS", ...extra];
   const graphs = names.map((n, i) => {
      const out = joins.filter(j => j.table === n).map(j => ({ id: n, joinModel: j }));
      const inp = joins.filter(j => j.foreignTable === n).map(j => ({ id: j.table, joinModel: j }));
      return graph(n, i * 150, out, inp);
   });
   return { joinEdit: false, joinEditPaneModel: null, graphViewModel: { graphs } };
}

const protoDesc: any = {};
function shim(name: string, get: (this: HTMLElement) => any) {
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
   Object.keys(protoDesc).forEach(k => protoDesc[k] ? Object.defineProperty(HTMLElement.prototype, k, protoDesc[k]) : delete (HTMLElement.prototype as any)[k]);
   if(scrollToDesc) { Object.defineProperty(Element.prototype, "scrollTo", scrollToDesc); } else { delete (Element.prototype as any).scrollTo; }
});

@Component({
   selector: "host-77995-callers",
   template: `
      @if (showJoins && table) {
         <physical-table-joins [table]="table" [physicalModel]="pm" databaseName="ds"
            (tableChange)="svc.emitModelChange()"></physical-table-joins>
      }
      <physical-graph-pane physicalView="v" datasource="ds" runtimeId="rt"
         [selectedGraphModels]="[]"></physical-graph-pane>
   `,
   imports: [PhysicalGraphPane, PhysicalTableJoinsComponent]
})
class HostComponent {
   showJoins = true;
   table: any = null;
   pm: any = { name: "v", folder: "", tables: [], id: "rt", description: "" };

   constructor(public svc: DataPhysicalModelService) {
   }
}

describe("PhysicalGraphPane - Bug #77995 highlight callers on real jsPlumb", () => {
   let moves: any[];
   let graphPosts: number;
   let joins: any[];
   let extra: string[];
   let pendingMove: (() => void) | null;
   let joinAdds: number;
   let joinRemoves: number;

   beforeEach(() => {
      moves = [];
      graphPosts = 0;
      joins = [jm("ORDERS", "CID", "CUSTOMERS"), jm("PRODUCTS", "CID", "CUSTOMERS")];
      extra = [];
      pendingMove = null;
      joinAdds = 0;
      joinRemoves = 0;
      server.use(
         http.post("*/api/data/physicalmodel/graph", () => {
            graphPosts++;
            return HttpResponse.json(graphResponse(joins, extra));
         }),
         http.put("*/api/data/physicalmodel/graph/move", async ({ request }) => {
            moves.push(await request.json());
            return HttpResponse.json(null);
         }),
         http.post("*/api/data/physicalmodel/join/add", () => { joinAdds++; return HttpResponse.json(null); }),
         http.put("*/api/data/physicalmodel/join/remove", () => { joinRemoves++; return HttpResponse.json(null); }),
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
      vi.spyOn(PhysicalGraphPane.prototype as any, "updateGraphPaneSize").mockImplementation(() => {});
      const debounce = (key: string, fn: () => void) => {
         if(key === MOVE_KEY) { pendingMove = fn; } else { fn(); }
      };

      const { fixture } = await render(HostComponent, {
         schemas: [NO_ERRORS_SCHEMA],
         componentProperties: { table },
         providers: [
            provideHttpClient(),
            DataPhysicalModelService,
            { provide: DebounceService, useValue: { debounce } },
            { provide: NgbModal, useValue: { open: vi.fn() } },
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
         for(let i = 0; i < 40 && !cond(); i++) { await settle(); }
         await settle();
      };
      const ngc = () => fixture.debugElement.query(By.directive(PhysicalModelNetworkGraphComponent))
         ?.componentInstance as PhysicalModelNetworkGraphComponent;
      const jsp = () => (ngc() as any).jsp;
      const count = () => fixture.debugElement.queryAll(By.directive(JoinNodeGraphComponent)).length;
      await until(() => count() >= 3 && jsp()?.getAllConnections().length === joins.length);
      expect(jsp().getAllConnections().length).toBe(joins.length);
      const host = fixture.componentInstance;
      const idOf = (name: string) => (ngc() as any).sourceIds[name];
      const conn = (s: string, t: string) => jsp().getAllConnections()
         .find((c: any) => c.sourceId === idOf(s) && c.targetId === idOf(t));
      const joinsComp = () => fixture.debugElement.query(By.directive(PhysicalTableJoinsComponent))
         ?.componentInstance as PhysicalTableJoinsComponent;
      const nodeEl = (name: string): HTMLElement => fixture.debugElement
         .queryAll(By.directive(JoinNodeGraphComponent))
         .find(d => d.componentInstance.graph.node.id === name).nativeElement;
      return { fixture, host, ngc, jsp, conn, settle, until, joinsComp, nodeEl };
   }

   /**
    * The visible state of a real jsPlumb connection. The type list drops jsPlumb's
    * unregistered "default" type and duplicates: connect() and setType() keep them
    * differently (a highlighted cycle join lists the color type twice) with no visible effect.
    */
   function snap(c: any): any {
      const path = c.canvas?.querySelector("path");
      const overlays = Object.keys(c.getOverlays()).sort();
      const r: any = {
         types: c.getType().filter((t: string, i: number, a: string[]) => !!t && t !== "default" && a.indexOf(t) === i).join(" "),
         paint: JSON.stringify(c.getPaintStyle()),
         hover: JSON.stringify(c._jsPlumb.hoverPaintStyle),
         overlays: overlays.join(","),
         cls: ((c.canvas?.getAttribute("class") || "").split(/\s+/)
            .filter((x: string) => x.startsWith("physical-graph")).sort().join(" ")),
         stroke: path?.getAttribute("stroke"),
         dash: path?.getAttribute("stroke-dasharray"),
      };
      return r;
   }

   const isHl = (c: any) => c.getPaintStyle()?.stroke === "#ed711c";

   async function refreshGraph(ctx: any) {
      const old = ctx.ngc().graphViewModel;
      const posts = graphPosts;
      ctx.host.svc.emitModelChange(false);
      await ctx.until(() => ctx.ngc().graphViewModel !== old && graphPosts === posts + 1
         && ctx.jsp().getAllConnections().length === joins.length);
      expect(ctx.ngc().graphViewModel).not.toBe(old);
   }

   const kinds: [string, any][] = [
      ["normal", {}], ["weak", { weak: true }], ["cycle", { cycle: true }],
      ["weak+cycle", { weak: true, cycle: true }]
   ];

   for(const [kind, props] of kinds) {
      it(`A ${kind}: a highlight change matches a connectNode rebuild, and clearing restores it`, async () => {
         joins = [jm("ORDERS", "CID", "CUSTOMERS", props), jm("PRODUCTS", "CID", "CUSTOMERS")];
         const ctx = await setup();
         const base = snap(ctx.conn("ORDERS", "CUSTOMERS"));
         const other0 = snap(ctx.conn("PRODUCTS", "CUSTOMERS"));
         const posts = graphPosts;
         const hl = [{ sourceTable: "ORDERS", targetTable: "CUSTOMERS" }];

         ctx.host.svc.highlightConnections(hl);
         await ctx.settle(); await ctx.settle(); await ctx.settle();
         expect(graphPosts).toBe(posts);
         const toggled = snap(ctx.conn("ORDERS", "CUSTOMERS"));
         expect(snap(ctx.conn("PRODUCTS", "CUSTOMERS"))).toEqual(other0);

         // rebuild via a model refresh with the highlight still set (e.g. add alias)
         extra = ["ORDERS_ALIAS"];
         await refreshGraph(ctx);
         const rebuilt = snap(ctx.conn("ORDERS", "CUSTOMERS"));
         expect(toggled).toEqual(rebuilt);

         ctx.host.svc.highlightConnections(null);
         await ctx.settle(); await ctx.settle();
         expect(graphPosts).toBe(posts + 1);
         expect(snap(ctx.conn("ORDERS", "CUSTOMERS"))).toEqual(base);

         // highlight again after the rebuild, then clear: still matches
         ctx.host.svc.highlightConnections(hl);
         await ctx.settle();
         expect(snap(ctx.conn("ORDERS", "CUSTOMERS"))).toEqual(rebuilt);
         ctx.host.svc.highlightConnections(null);
         await ctx.settle();
         expect(snap(ctx.conn("ORDERS", "CUSTOMERS"))).toEqual(base);
         expect(graphPosts).toBe(posts + 1);

         if(kind === "normal") {
            expect(toggled.stroke).toBe("#ed711c");
            expect(base.stroke).not.toBe("#ed711c");
            expect(toggled.overlays).toContain("arrow--join-color");
            expect(base.overlays).not.toContain("arrow--join-color");
         }
         if(kind === "cycle") {
            expect(toggled.stroke).toBe("red");
            expect(base.stroke).toBe("red");
         }
         if(kind.includes("weak")) {
            expect(toggled.dash).toBe(base.dash);
            expect(base.dash).toBeTruthy();
         }
         // the join icon label (a connect-time overlay) survives setType
         expect(toggled.overlays.split(",").length).toBeGreaterThanOrEqual(2);
         expect(snap(ctx.conn("ORDERS", "CUSTOMERS")).overlays).toBe(base.overlays);
      });
   }

   function ordersTable(): any {
      return { name: "ORDERS", catalog: "", schema: "", qualifiedName: "ORDERS", path: "ORDERS",
         alias: "", sql: "", type: null, baseTable: true,
         joins: joins.filter(j => j.table === "ORDERS") };
   }
   function productsTable(): any {
      return { name: "PRODUCTS", catalog: "", schema: "", qualifiedName: "PRODUCTS", path: "PRODUCTS",
         alias: "", sql: "", type: null, baseTable: true,
         joins: joins.filter(j => j.table === "PRODUCTS") };
   }

   async function selectFirstJoin(ctx: any) {
      const jc = ctx.joinsComp();
      const tableNode = jc.foreignTableRoot.children[0];
      jc.selectNode([tableNode.children[0]]);
      ctx.fixture.detectChanges();
      await ctx.settle(); await ctx.settle();
   }

   it("B1 selectNode (join row) highlights its connection with no POST; table row clears", async () => {
      const ctx = await setup(ordersTable());
      await ctx.settle();
      const posts = graphPosts;
      expect(isHl(ctx.conn("ORDERS", "CUSTOMERS"))).toBe(false);
      await selectFirstJoin(ctx);
      expect(isHl(ctx.conn("ORDERS", "CUSTOMERS"))).toBe(true);
      expect(isHl(ctx.conn("PRODUCTS", "CUSTOMERS"))).toBe(false);
      const jc = ctx.joinsComp();
      jc.selectNode([jc.foreignTableRoot.children[0]]);
      ctx.fixture.detectChanges();
      await ctx.settle(); await ctx.settle();
      expect(isHl(ctx.conn("ORDERS", "CUSTOMERS"))).toBe(false);
      expect(graphPosts).toBe(posts);
   });

   it("B2 table setter (switch tables) clears, then the new table's join highlights; no POST", async () => {
      const ctx = await setup(ordersTable());
      await ctx.settle();
      const posts = graphPosts;
      await selectFirstJoin(ctx);
      expect(isHl(ctx.conn("ORDERS", "CUSTOMERS"))).toBe(true);
      ctx.host.table = productsTable();
      ctx.fixture.detectChanges();
      await ctx.settle(); await ctx.settle();
      expect(isHl(ctx.conn("ORDERS", "CUSTOMERS"))).toBe(false);
      await selectFirstJoin(ctx);
      expect(isHl(ctx.conn("PRODUCTS", "CUSTOMERS"))).toBe(true);
      expect(isHl(ctx.conn("ORDERS", "CUSTOMERS"))).toBe(false);
      expect(graphPosts).toBe(posts);
   });

   it("B3 updateForeignTables (selected join gone from table.joins) clears with no POST", async () => {
      const ctx = await setup(ordersTable());
      await ctx.settle();
      const posts = graphPosts;
      await selectFirstJoin(ctx);
      expect(isHl(ctx.conn("ORDERS", "CUSTOMERS"))).toBe(true);
      // replace the selected join object -> ngDoCheck diff -> updateForeignTables, not found
      ctx.host.table.joins.splice(0, 1, { ...ctx.host.table.joins[0] });
      ctx.fixture.detectChanges();
      await ctx.settle(); await ctx.settle();
      expect(isHl(ctx.conn("ORDERS", "CUSTOMERS"))).toBe(false);
      expect(graphPosts).toBe(posts);
   });

   it("B4 ngOnDestroy of the joins pane clears the highlight with no POST", async () => {
      const ctx = await setup(ordersTable());
      await ctx.settle();
      const posts = graphPosts;
      await selectFirstJoin(ctx);
      expect(isHl(ctx.conn("ORDERS", "CUSTOMERS"))).toBe(true);
      ctx.host.showJoins = false;
      ctx.fixture.detectChanges();
      await ctx.settle(); await ctx.settle();
      expect(ctx.joinsComp()).toBeFalsy();
      expect(isHl(ctx.conn("ORDERS", "CUSTOMERS"))).toBe(false);
      expect(ctx.jsp().getAllConnections().filter(isHl).length).toBe(0);
      expect(graphPosts).toBe(posts);
   });

   it("B5 addJoinToTable: one POST (from tableChange) and the new join comes up highlighted", async () => {
      const ctx = await setup(ordersTable());
      await ctx.settle();
      const posts = graphPosts;
      const nj = jm("ORDERS", "PID", "PRODUCTS");
      const old = ctx.ngc().graphViewModel;
      joins = [...joins, nj];
      (ctx.joinsComp() as any).addJoinToTable(nj);
      ctx.fixture.detectChanges();
      await ctx.until(() => ctx.ngc().graphViewModel !== old && !!ctx.conn("ORDERS", "PRODUCTS"));
      await ctx.settle(); await ctx.settle();
      expect(joinAdds).toBe(1);
      expect(graphPosts).toBe(posts + 1);
      expect(isHl(ctx.conn("ORDERS", "PRODUCTS"))).toBe(true);
      expect(isHl(ctx.conn("ORDERS", "CUSTOMERS"))).toBe(false);
   });

   it("B6 doRemoveJoinsAction: one POST (from tableChange), removed connection gone, nothing highlighted", async () => {
      const ctx = await setup(ordersTable());
      await ctx.settle();
      await selectFirstJoin(ctx);
      expect(isHl(ctx.conn("ORDERS", "CUSTOMERS"))).toBe(true);
      const posts = graphPosts;
      const old = ctx.ngc().graphViewModel;
      joins = joins.filter(j => j.table !== "ORDERS");
      (ctx.joinsComp() as any).doRemoveJoinsAction({ items: [], runtimeID: "rt" });
      await ctx.until(() => ctx.ngc().graphViewModel !== old);
      await ctx.settle(); await ctx.settle();
      expect(joinRemoves).toBe(1);
      expect(graphPosts).toBe(posts + 1);
      expect(ctx.conn("ORDERS", "CUSTOMERS")).toBeFalsy();
      expect(ctx.jsp().getAllConnections().filter(isHl).length).toBe(0);
      expect(ctx.jsp().getAllConnections().length).toBe(1);
   });

   const fire = (target: EventTarget, type: string, x: number, y: number) => {
      const e = new MouseEvent(type, { bubbles: true, cancelable: true, button: 0, clientX: x, clientY: y } as any);
      if((e as any).pageX !== x) {
         Object.defineProperty(e, "pageX", { value: x });
         Object.defineProperty(e, "pageY", { value: y });
      }
      target.dispatchEvent(e);
   };

   it("C a drag (real katavorio) across a highlight change is not reset and saves the move", async () => {
      const ctx = await setup(ordersTable());
      await ctx.settle();
      await selectFirstJoin(ctx);
      const el = ctx.nodeEl("PRODUCTS");
      const posts = graphPosts;
      fire(el.querySelector(".jsplumb-draggable-handle"), "mousedown", 10, 10);
      ctx.fixture.detectChanges();
      // highlight changes mid-drag (as the table switch would)
      ctx.host.svc.highlightConnections(null);
      ctx.fixture.detectChanges();
      fire(document, "mousemove", 60, 30);
      fire(document, "mousemove", 110, 50);
      await ctx.settle(); await ctx.settle(); await ctx.settle();
      const still = el.style.left + "," + el.style.top;
      fire(document, "mouseup", 110, 50);
      if(pendingMove) { const f = pendingMove; pendingMove = null; f(); }
      await ctx.until(() => moves.length > 0);
      // the node started at 300,6 and was dragged by 100,40 (jsdom: katavorio's auto-scroll
      // adds a few px because clientWidth is 0); it must not have been reset
      const x = parseInt(still, 10);
      const y = parseInt(still.split(",")[1], 10);
      expect(x).toBeGreaterThanOrEqual(400);
      expect(y).toBeGreaterThanOrEqual(46);
      expect(moves[0].bounds).toMatchObject({ x, y });
      expect(graphPosts).toBe(posts);
      expect(moves.length).toBe(1);
      expect(moves[0].table).toBe("PRODUCTS");
      expect(isHl(ctx.conn("ORDERS", "CUSTOMERS"))).toBe(false);
   });
});
