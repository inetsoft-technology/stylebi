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
 * JoinThumbnailService - Bug #77996 regression
 *
 * Clicking a column-join line in the physical view join-edit pane
 * (PhysicalJoinEditPane) or the query link pane (QueryJoinEditPane, which sets
 * DataType.QUERY on the service) opens EditJoinDialog. OK used to PUT the join
 * back to the server even when nothing was changed, then reload the graph. On
 * the server that rewrote a legacy 0/0 cardinality to MANY/MANY (physical) and
 * dropped the query's stored SQL string (query).
 *
 * Uses the real service, the real NgbModal and the real EditJoinDialog
 * template; only the jsPlumb line click is bypassed by calling the handler the
 * line's mouseup binding calls.
 */

import { HttpRequest, provideHttpClient } from "@angular/common/http";
import { HttpTestingController, provideHttpClientTesting } from "@angular/common/http/testing";
import { ApplicationRef } from "@angular/core";
import { TestBed } from "@angular/core/testing";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";

import { Rectangle } from "../../../../../common/data/rectangle";
import { Cardinality } from "../../../model/datasources/database/physical-model/cardinality.enum";
import { GraphColumnInfo } from "../../../model/datasources/database/physical-model/graph/graph-column-info";
import { JoinEditPaneModel } from "../../../model/datasources/database/physical-model/graph/join-edit-pane-model";
import { TableJoinInfo } from "../../../model/datasources/database/physical-model/graph/table-join-info";
import { JoinModel } from "../../../model/datasources/database/physical-model/join-model";
import { JoinType } from "../../../model/datasources/database/physical-model/join-type.enum";
import { MergingRule } from "../../../model/datasources/database/physical-model/merging-rule.enum";
import { DataType, JoinThumbnailService } from "./join-thumbnail.service";

const PHYSICAL_JOIN_URI = "../api/data/physicalmodel/join";
const QUERY_JOIN_URI = "../api/data/datasource/query/join";

// EditJoinDialog's modal header makes unrelated help-url / assistant GETs
function isJoinRequest(req: HttpRequest<any>): boolean {
   return req.url.startsWith(PHYSICAL_JOIN_URI) || req.url.startsWith(QUERY_JOIN_URI);
}

function column(table: string, name: string): GraphColumnInfo {
   return { id: `${table}-${name}`, name, type: "integer", table };
}

// shaped like the join the server sends (no "delete" key)
function serverJoin(cardinality: Cardinality | null = Cardinality.MANY_TO_ONE): JoinModel {
   return {
      type: JoinType.EQUAL,
      orderPriority: 1,
      weak: false,
      mergingRule: MergingRule.AND,
      cardinality,
      table: "orders",
      column: "cid",
      foreignTable: "customers",
      foreignColumn: "id",
      baseJoin: false,
      cycle: false,
      supportFullOuter: true
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
            columns: [column("orders", "cid")],
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

describe("JoinThumbnailService edit join dialog (Bug #77996)", () => {
   let service: JoinThumbnailService;
   let httpMock: HttpTestingController;
   let appRef: ApplicationRef;
   let refreshes: TableJoinInfo[];

   beforeEach(() => {
      TestBed.configureTestingModule({
         providers: [
            JoinThumbnailService,
            provideHttpClient(),
            provideHttpClientTesting()
         ]
      });

      service = TestBed.inject(JoinThumbnailService);
      httpMock = TestBed.inject(HttpTestingController);
      appRef = TestBed.inject(ApplicationRef);
      refreshes = [];
      service.refreshGraph.subscribe(info => refreshes.push(info));
   });

   afterEach(async () => {
      TestBed.inject(NgbModal).dismissAll();
      await settle();
      service.cleanup();
      document.body.querySelectorAll("ngb-modal-window, ngb-modal-backdrop")
         .forEach(el => el.remove());
   });

   async function settle(): Promise<void> {
      for(let i = 0; i < 5; i++) {
         appRef.tick();
         await new Promise(resolve => setTimeout(resolve, 0));
      }
   }

   async function openDialog(join: JoinModel, dataType: DataType): Promise<void> {
      service.setDataType(dataType);
      service.setJoinEditPaneModel(paneModel(join));
      service.registerColumn(column("orders", "cid"), "src-cid");
      service.registerColumn(column("customers", "id"), "tgt-id");

      // what the jsPlumb line's mouseup binding calls on a left click
      (service as any).showEditJoinPropertiesDialog({ sourceId: "src-cid", targetId: "tgt-id" });
      await settle();
      expect(document.querySelector("edit-join-dialog")).not.toBeNull();
   }

   async function clickButton(label: string): Promise<void> {
      const button = Array.from(document.querySelectorAll<HTMLButtonElement>(
         "edit-join-dialog .modal-footer button"))
         .find(b => b.textContent.trim() === label);
      expect(button).toBeDefined();
      button.click();
      await settle();
   }

   async function clickRadio(name: string, index: number): Promise<void> {
      const radios = document.querySelectorAll<HTMLInputElement>(
         `edit-join-dialog input[name="${name}"]`);
      radios[index].click();
      await settle();
   }

   it("sends nothing and does not reload the graph on OK without changes (physical)", async () => {
      await openDialog(serverJoin(), DataType.PHYSICAL);
      await clickButton("_#(OK)");

      httpMock.expectNone(isJoinRequest);
      expect(refreshes.length).toBe(0);
   });

   it("sends nothing on OK without changes for a legacy join with no cardinality", async () => {
      await openDialog(serverJoin(null), DataType.PHYSICAL);
      await clickButton("_#(OK)");

      httpMock.expectNone(isJoinRequest);
      expect(refreshes.length).toBe(0);
   });

   it("sends nothing on OK without changes (query link pane)", async () => {
      await openDialog(serverJoin(), DataType.QUERY);
      await clickButton("_#(OK)");

      httpMock.expectNone(isJoinRequest);
      expect(refreshes.length).toBe(0);
   });

   it("PUTs a changed join once and reloads the graph (physical)", async () => {
      const original = serverJoin();
      await openDialog(original, DataType.PHYSICAL);
      // cardinality radios: one to one, one to many, many to one, many to many
      await clickRadio("cardinalityRadio", 0);
      await clickButton("_#(OK)");

      const req = httpMock.expectOne(PHYSICAL_JOIN_URI);
      expect(req.request.method).toBe("PUT");
      expect(req.request.body.joinModel.cardinality).toBe(Cardinality.ONE_TO_ONE);
      expect(req.request.body.detailJoinInfo).toEqual({
         runtimeId: "rt-1",
         sourceTable: "orders",
         targetTable: "customers",
         sourceColumn: "cid",
         targetColumn: "id"
      });
      // the dialog edits a copy, not the model's join
      expect(original.cardinality).toBe(Cardinality.MANY_TO_ONE);
      req.flush(null);
      httpMock.expectNone(isJoinRequest);

      expect(refreshes).toEqual([
         { runtimeId: "rt-1", sourceTable: "orders", targetTable: "customers" }
      ]);
   });

   it("PUTs a changed join type to the query join endpoint", async () => {
      await openDialog(serverJoin(), DataType.QUERY);
      document.querySelector<HTMLButtonElement>("edit-join-dialog .custom-select-trigger").click();
      await settle();
      document.querySelectorAll<HTMLButtonElement>(".custom-select-option")[1] // Left Outer
         .dispatchEvent(new MouseEvent("mousedown", { bubbles: true }));
      await settle();
      await clickButton("_#(OK)");

      const req = httpMock.expectOne(QUERY_JOIN_URI);
      expect(req.request.method).toBe("PUT");
      req.flush(null);
      httpMock.expectNone(isJoinRequest);
      expect(refreshes.length).toBe(1);
   });

   it("still POSTs delete on Remove", async () => {
      await openDialog(serverJoin(), DataType.PHYSICAL);
      await clickButton("_#(Remove)");

      const req = httpMock.expectOne(PHYSICAL_JOIN_URI + "/delete");
      expect(req.request.method).toBe("POST");
      req.flush(null);
      httpMock.expectNone(isJoinRequest);
      expect(refreshes.length).toBe(1);
   });
});
