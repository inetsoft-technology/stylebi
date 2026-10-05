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
 * Bug #77825, Add Join must post to the exact server mapping
 * "/api/data/physicalmodel/cardinality" (no trailing slash). With a trailing slash Spring 6
 * returns 404 and the error branch silently saves the dialog's default MANY_TO_ONE join.
 *
 * A plain HttpTestingController spec is used on purpose: expectOne compares the URL string
 * exactly, while MSW (used by the *.tl.spec.ts suite) matches with or without a trailing slash.
 */

import { HttpClient } from "@angular/common/http";
import { HttpClientTestingModule, HttpTestingController } from "@angular/common/http/testing";
import { IterableDiffers } from "@angular/core";
import { TestBed } from "@angular/core/testing";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";

import { DataPhysicalModelService } from "../../../../../services/data-physical-model.service";
import { Cardinality } from "../../../../../model/datasources/database/physical-model/cardinality.enum";
import { JoinModel } from "../../../../../model/datasources/database/physical-model/join-model";
import { JoinType } from "../../../../../model/datasources/database/physical-model/join-type.enum";
import { MergingRule } from "../../../../../model/datasources/database/physical-model/merging-rule.enum";
import { PhysicalModelDefinition } from "../../../../../model/datasources/database/physical-model/physical-model-definition";
import { PhysicalTableModel } from "../../../../../model/datasources/database/physical-model/physical-table-model";
import { EditJoinsEvent } from "../../../../../model/datasources/database/events/edit-joins-event";
import { PhysicalTableJoinsComponent } from "./physical-table-joins.component";

const CARDINALITY_URL = "../api/data/physicalmodel/cardinality";
const JOIN_ADD_URL = "../api/data/physicalmodel/join/add";

function makeJoin(overrides: Partial<JoinModel> = {}): JoinModel {
   return {
      type: JoinType.EQUAL,
      orderPriority: 0,
      weak: false,
      mergingRule: MergingRule.AND,
      // AddJoinDialog always initializes new joins to MANY_TO_ONE
      cardinality: Cardinality.MANY_TO_ONE,
      table: "Orders",
      column: "id",
      foreignTable: "Customers",
      foreignColumn: "id",
      baseJoin: false,
      ...overrides,
   };
}

function makeTable(): PhysicalTableModel {
   return {
      name: "Orders",
      catalog: "",
      schema: "",
      qualifiedName: "Orders",
      path: "Orders",
      alias: "",
      sql: "",
      type: null,
      joins: [],
      baseTable: true,
   };
}

function makePhysicalModel(): PhysicalModelDefinition {
   return {
      name: "TestModel",
      folder: "",
      tables: [],
      id: "model-1",
      description: "",
   };
}

describe("PhysicalTableJoinsComponent.addJoin - cardinality detection (Bug #77825)", () => {
   let http: HttpTestingController;
   let modal: { open: ReturnType<typeof vi.fn> };
   let physicalModelService: { highlightConnections: ReturnType<typeof vi.fn> };

   beforeEach(() => {
      modal = { open: vi.fn() };
      physicalModelService = { highlightConnections: vi.fn() };

      TestBed.configureTestingModule({
         imports: [HttpClientTestingModule],
         providers: [
            { provide: NgbModal, useValue: modal },
            { provide: DataPhysicalModelService, useValue: physicalModelService },
         ],
      });

      http = TestBed.inject(HttpTestingController);
   });

   afterEach(() => {
      http.verify();
      vi.restoreAllMocks();
      TestBed.resetTestingModule();
   });

   function createComponent(): PhysicalTableJoinsComponent {
      const comp = new PhysicalTableJoinsComponent(
         TestBed.inject(IterableDiffers),
         TestBed.inject(HttpClient),
         TestBed.inject(NgbModal),
         TestBed.inject(DataPhysicalModelService) as unknown as DataPhysicalModelService,
      );
      comp.joinsTree = { exclusiveSelectNode: vi.fn(), selectedNodes: [] } as any;
      comp.physicalModel = makePhysicalModel();
      comp.databaseName = "testDb";
      comp.table = makeTable();
      return comp;
   }

   /** Open the add-join dialog (mocked) and wait for its result promise to resolve. */
   async function addJoinFromDialog(comp: PhysicalTableJoinsComponent, join: JoinModel) {
      modal.open.mockReturnValue({ result: Promise.resolve(join) });
      comp.addJoin();
      await Promise.resolve();
      await Promise.resolve();
   }

   function expectCardinalityRequest() {
      return http.expectOne(
         r => r.method === "POST" && r.url === CARDINALITY_URL,
         "POST to the cardinality endpoint without a trailing slash");
   }

   it("should post to the cardinality URL without a trailing slash", async () => {
      const comp = createComponent();
      const dialogJoin = makeJoin();

      await addJoinFromDialog(comp, dialogJoin);

      const req = expectCardinalityRequest();
      expect(req.request.params.get("database")).toBe("testDb");
      expect(req.request.body).toEqual(
         expect.objectContaining({ table: "Orders", join: dialogJoin }));

      req.flush({ ...dialogJoin, cardinality: Cardinality.ONE_TO_MANY });
      http.expectOne(JOIN_ADD_URL).flush({});
   });

   it("should add the join with the cardinality the server detected", async () => {
      const comp = createComponent();
      const dialogJoin = makeJoin();
      const tableChange = vi.fn();
      comp.tableChange.subscribe(tableChange);

      await addJoinFromDialog(comp, dialogJoin);
      expectCardinalityRequest().flush({ ...dialogJoin, cardinality: Cardinality.ONE_TO_MANY });

      const addReq = http.expectOne(JOIN_ADD_URL);
      expect(addReq.request.method).toBe("POST");
      const event = addReq.request.body as EditJoinsEvent;
      expect(event.joinItems[0].join.cardinality).toBe(Cardinality.ONE_TO_MANY);
      expect(comp.selectedJoins).toHaveLength(1);
      expect(comp.selectedJoins[0].cardinality).toBe(Cardinality.ONE_TO_MANY);
      expect(comp.foreignTableRoot.children.map(c => c.label)).toEqual(["Customers"]);

      addReq.flush({});
      expect(tableChange).toHaveBeenCalledTimes(1);
   });

   it("should still add the dialog's join when cardinality detection fails", async () => {
      const comp = createComponent();
      const dialogJoin = makeJoin();

      await addJoinFromDialog(comp, dialogJoin);
      expectCardinalityRequest().flush("error", { status: 500, statusText: "Server Error" });

      const addReq = http.expectOne(JOIN_ADD_URL);
      expect((addReq.request.body as EditJoinsEvent).joinItems[0].join).toBe(dialogJoin);
      expect(comp.selectedJoins).toEqual([dialogJoin]);
      expect(comp.selectedJoins[0].cardinality).toBe(Cardinality.MANY_TO_ONE);
      addReq.flush({});
   });

   it("should not send any request when the add-join dialog is cancelled", async () => {
      const comp = createComponent();
      modal.open.mockReturnValue({ result: Promise.reject("cancel") });

      comp.addJoin();
      await Promise.resolve();
      await Promise.resolve();

      http.expectNone(() => true);
      expect(comp.foreignTableRoot.children).toHaveLength(0);
   });
});
