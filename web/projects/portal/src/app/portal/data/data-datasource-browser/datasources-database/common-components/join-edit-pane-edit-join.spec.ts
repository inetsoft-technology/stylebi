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
 * Edit Join dialog opened from a join line of the real join-edit panes - Bug #77996
 *
 * Mounts the real PhysicalJoinEditPane / QueryJoinEditPane (each with its own real
 * JoinThumbnailService and jsPlumb instance), fires the line's real mouseup binding and
 * drives the real EditJoinDialog. OK with nothing changed must not save the join or
 * reload the graph; each real edit must save once and reload once.
 */

import { HttpRequest, provideHttpClient } from "@angular/common/http";
import { HttpTestingController, provideHttpClientTesting } from "@angular/common/http/testing";
import { ApplicationRef } from "@angular/core";
import { ComponentFixture, TestBed } from "@angular/core/testing";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { Rectangle } from "../../../../../common/data/rectangle";
import {
   TYPE_COLUMN_INTERACTION_TARGET
} from "../../../../../composer/gui/ws/jsplumb/jsplumb-graph-schema.config";
import { Cardinality } from "../../../model/datasources/database/physical-model/cardinality.enum";
import { GraphColumnInfo } from "../../../model/datasources/database/physical-model/graph/graph-column-info";
import { JoinEditPaneModel } from "../../../model/datasources/database/physical-model/graph/join-edit-pane-model";
import { TableJoinInfo } from "../../../model/datasources/database/physical-model/graph/table-join-info";
import { JoinModel } from "../../../model/datasources/database/physical-model/join-model";
import { JoinType } from "../../../model/datasources/database/physical-model/join-type.enum";
import { MergingRule } from "../../../model/datasources/database/physical-model/merging-rule.enum";
import {
   PhysicalJoinEditPane
} from "../database-physical-model/physical-join-edit-pane/physical-join-edit-pane.component";
import {
   QueryJoinEditPane
} from "../database-query/query-main/query-link-pane/query-join-editor-pane/query-join-edit-pane.component";
import { JoinThumbnailService } from "./join-thumbnail.service";

const PHYSICAL_JOIN_URI = "../api/data/physicalmodel/join";
const QUERY_JOIN_URI = "../api/data/datasource/query/join";

// the panes also send heartbeat GETs and the modal header help-url GETs
function isJoinRequest(req: HttpRequest<any>): boolean {
   return req.url.startsWith(PHYSICAL_JOIN_URI) || req.url.startsWith(QUERY_JOIN_URI);
}

function column(table: string, name: string): GraphColumnInfo {
   return { id: `${table}-${name}`, name, type: "integer", table };
}

// shaped like the join the server sends
function serverJoin(overrides: Partial<JoinModel> = {}): JoinModel {
   return {
      type: JoinType.EQUAL,
      orderPriority: 1,
      weak: false,
      mergingRule: MergingRule.AND,
      cardinality: Cardinality.MANY_TO_ONE,
      table: "orders",
      column: "cid",
      foreignTable: "customers",
      foreignColumn: "id",
      baseJoin: false,
      cycle: false,
      supportFullOuter: true,
      ...overrides
   };
}

function paneModel(join: JoinModel): JoinEditPaneModel {
   return {
      runtimeID: "rt-1",
      datasource: "ds",
      physicalView: "view",
      tables: [
         {
            name: "orders",
            bounds: new Rectangle(0, 0, 100, 50),
            columns: [column("orders", "cid"), column("orders", "note")],
            joins: [join]
         },
         {
            name: "customers",
            bounds: new Rectangle(200, 0, 100, 50),
            columns: [column("customers", "id")],
            joins: []
         }
      ]
   };
}

type PaneKind = "physical" | "query";

describe("Edit Join from a join-edit pane line (Bug #77996)", () => {
   let httpMock: HttpTestingController;
   let appRef: ApplicationRef;
   let fixture: ComponentFixture<PhysicalJoinEditPane | QueryJoinEditPane>;
   let service: JoinThumbnailService;
   let refreshes: TableJoinInfo[];

   beforeEach(() => {
      TestBed.configureTestingModule({
         imports: [PhysicalJoinEditPane, QueryJoinEditPane],
         providers: [
            provideHttpClient(),
            provideHttpClientTesting()
         ]
      });

      httpMock = TestBed.inject(HttpTestingController);
      appRef = TestBed.inject(ApplicationRef);
      fixture = null;
   });

   afterEach(async () => {
      TestBed.inject(NgbModal).dismissAll();
      await settle();
      fixture?.destroy();
      fixture = null;
      document.body.querySelectorAll("ngb-modal-window, ngb-modal-backdrop")
         .forEach(el => el.remove());
   });

   // the modal is attached to the app, the pane fixture is not
   async function settle(): Promise<void> {
      for(let i = 0; i < 6; i++) {
         appRef.tick();

         if(fixture && !fixture.componentRef.hostView.destroyed) {
            fixture.detectChanges();
         }

         await new Promise(resolve => setTimeout(resolve, 0));
      }
   }

   async function mount(kind: PaneKind, join: JoinModel): Promise<void> {
      refreshes = [];

      if(kind === "physical") {
         const physical = TestBed.createComponent(PhysicalJoinEditPane);
         physical.componentInstance.onRefreshPhysicalGraph.subscribe(info => refreshes.push(info));
         fixture = physical;
      }
      else {
         const query = TestBed.createComponent(QueryJoinEditPane);
         query.componentInstance.onRefreshGraph.subscribe(info => refreshes.push(info));
         fixture = query;
      }

      fixture.componentRef.setInput("model", paneModel(join));
      fixture.detectChanges();
      // the pane connects the columns a tick after the view is checked
      await settle();
      service = fixture.debugElement.injector.get(JoinThumbnailService);
   }

   function joinLine(): any {
      const connections = service.jsPlumbInstance.getAllConnections() as any as any[];
      const line = connections.find(c => c.hasType(TYPE_COLUMN_INTERACTION_TARGET));
      expect(line).toBeDefined();
      return line;
   }

   async function clickJoinLine(): Promise<void> {
      const line = joinLine();
      line.fire("mouseup", line, { button: 0 });
      await settle();
   }

   async function clickButton(label: string): Promise<void> {
      const button = Array.from(document.querySelectorAll<HTMLButtonElement>(
         "edit-join-dialog .modal-footer button"))
         .find(b => b.textContent.trim() === label);
      expect(button).toBeDefined();
      button.click();
      await settle();
   }

   async function clickInput(selector: string, index = 0): Promise<void> {
      document.querySelectorAll<HTMLInputElement>(`edit-join-dialog ${selector}`)[index].click();
      await settle();
   }

   async function selectJoinType(index: number): Promise<void> {
      document.querySelector<HTMLButtonElement>("edit-join-dialog .custom-select-trigger").click();
      await settle();
      document.querySelectorAll<HTMLButtonElement>(".custom-select-option")[index]
         .dispatchEvent(new MouseEvent("mousedown", { bubbles: true }));
      await settle();
   }

   async function typeOrderPriority(value: string): Promise<void> {
      const input = document.querySelector<HTMLInputElement>(
         "edit-join-dialog number-stepper[name=orderPriority] input");
      input.value = value;
      input.dispatchEvent(new Event("input"));
      // the stepper commits on blur
      input.dispatchEvent(new Event("blur"));
      await settle();
   }

   function expectNoJoinRequestOrRefresh(): void {
      httpMock.expectNone(isJoinRequest);
      expect(refreshes.length).toBe(0);
   }

   function columnHighlights(): string {
      return Array.from(document.querySelectorAll("edit-join-table-column"))
         .map(el => el.className.match(/schema-column-(highlight|ignore)/)?.[1] ?? "none")
         .join(",");
   }

   describe("OK with nothing changed sends nothing and does not reload the graph", () => {
      const cases: [string, PaneKind, Partial<JoinModel>][] = [
         ["physical", "physical", {}],
         ["physical, legacy join with no cardinality", "physical", { cardinality: null }],
         ["physical, server join carrying delete: false", "physical", { delete: false }],
         ["query", "query", {}]
      ];

      cases.forEach(([name, kind, overrides]) => {
         it(name, async () => {
            await mount(kind, serverJoin(overrides));
            const before = JSON.stringify(fixture.componentInstance.model);

            await clickJoinLine();
            expect(document.querySelector("edit-join-dialog")).not.toBeNull();
            await clickButton("_#(OK)");

            expect(document.querySelector("edit-join-dialog")).toBeNull();
            expectNoJoinRequestOrRefresh();
            expect(JSON.stringify(fixture.componentInstance.model)).toBe(before);
         });
      });

      it("after re-entering the same values in every physical field", async () => {
         await mount("physical", serverJoin());
         await clickJoinLine();

         await typeOrderPriority("1");
         await clickInput("input[type=checkbox]");
         await clickInput("input[type=checkbox]");
         await clickInput("input[name=cardinalityRadio]", 2); // many to one, already selected
         await clickInput("input[name=mergingRadio]", 0); // and, already selected
         await clickButton("_#(OK)");

         expectNoJoinRequestOrRefresh();
      });

      it("leaves the hovered columns highlighted exactly as Cancel does", async () => {
         const highlightsAfterClose: string[] = [];

         for(const label of ["_#(OK)", "_#(Cancel)"]) {
            await mount("physical", serverJoin());
            const line = joinLine();
            line.fire("mouseover", line, {});
            await settle();
            expect(columnHighlights()).toBe("highlight,ignore,highlight");

            await clickJoinLine();
            await clickButton(label);
            highlightsAfterClose.push(columnHighlights());

            line.fire("mouseout", line, {});
            await settle();
            expect(columnHighlights()).toBe("none,none,none");
            expectNoJoinRequestOrRefresh();
            fixture.destroy();
         }

         expect(highlightsAfterClose[0]).toBe(highlightsAfterClose[1]);
      });
   });

   describe("a real edit is saved once and reloads the graph once", () => {
      const cases: [string, PaneKind, Partial<JoinModel>, () => Promise<void>,
         (join: JoinModel) => void][] =
      [
         ["physical join type", "physical", {}, () => selectJoinType(1),
            join => expect(Number(join.type)).toBe(JoinType.LEFT_OUTER)],
         ["physical cardinality", "physical", {}, () => clickInput("input[name=cardinalityRadio]", 0),
            join => expect(join.cardinality).toBe(Cardinality.ONE_TO_ONE)],
         ["physical merging rule", "physical", {}, () => clickInput("input[name=mergingRadio]", 1),
            join => expect(join.mergingRule).toBe(MergingRule.OR)],
         ["physical weak join", "physical", {}, () => clickInput("input[type=checkbox]"),
            join => expect(join.weak).toBe(true)],
         ["physical order priority", "physical", {}, () => typeOrderPriority("3"),
            join => expect(join.orderPriority).toBe(3)],
         ["physical order priority of a legacy join with no cardinality", "physical",
            { cardinality: null }, () => typeOrderPriority("2"),
            join => expect(join.orderPriority).toBe(2)],
         ["query join type", "query", {}, () => selectJoinType(2),
            join => expect(Number(join.type)).toBe(JoinType.RIGHT_OUTER)]
      ];

      cases.forEach(([name, kind, overrides, edit, check]) => {
         it(name, async () => {
            await mount(kind, serverJoin(overrides));
            await clickJoinLine();
            await edit();
            await clickButton("_#(OK)");

            const req = httpMock.expectOne(isJoinRequest);
            expect(req.request.method).toBe("PUT");
            expect(req.request.url).toBe(kind === "physical" ? PHYSICAL_JOIN_URI : QUERY_JOIN_URI);
            check(req.request.body.joinModel);
            expect(refreshes.length).toBe(0);

            req.flush(null);
            httpMock.expectNone(isJoinRequest);
            expect(refreshes).toEqual([
               { runtimeId: "rt-1", sourceTable: "orders", targetTable: "customers" }
            ]);
         });
      });

      it("after earlier unchanged OKs on the same line", async () => {
         await mount("physical", serverJoin());
         await clickJoinLine();
         await clickButton("_#(OK)");
         await clickJoinLine();
         await clickButton("_#(OK)");
         expectNoJoinRequestOrRefresh();

         await clickJoinLine();
         await clickInput("input[name=cardinalityRadio]", 3);
         await clickButton("_#(OK)");

         const req = httpMock.expectOne(PHYSICAL_JOIN_URI);
         expect(req.request.body.joinModel.cardinality).toBe(Cardinality.MANY_TO_MANY);
         req.flush(null);
         expect(refreshes.length).toBe(1);
      });
   });

   (["physical", "query"] as PaneKind[]).forEach(kind => {
      it(`Remove posts the delete and reloads the graph (${kind})`, async () => {
         await mount(kind, serverJoin());
         await clickJoinLine();
         await clickButton("_#(Remove)");

         const uri = (kind === "physical" ? PHYSICAL_JOIN_URI : QUERY_JOIN_URI) + "/delete";
         const req = httpMock.expectOne(uri);
         expect(req.request.method).toBe("POST");
         req.flush(null);
         expect(refreshes.length).toBe(1);
      });

      it(`Cancel after edits sends nothing and leaves the pane's join untouched (${kind})`,
         async () => {
            await mount(kind, serverJoin());
            await clickJoinLine();
            await selectJoinType(1);

            if(kind === "physical") {
               await clickInput("input[name=cardinalityRadio]", 0);
               await clickInput("input[type=checkbox]");
            }

            await clickButton("_#(Cancel)");

            expectNoJoinRequestOrRefresh();
            expect(fixture.componentInstance.model.tables[0].joins[0]).toEqual(serverJoin());
         });
   });

   it("a base join line does not open the dialog", async () => {
      await mount("physical", serverJoin({ baseJoin: true }));
      await clickJoinLine();

      expect(document.querySelector("edit-join-dialog")).toBeNull();
      expectNoJoinRequestOrRefresh();
   });
});
