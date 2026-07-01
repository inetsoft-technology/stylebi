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
 * AddJoinDialogComponent - single pass (+concurrency + memory leak)
 *
 * Risk-first coverage:
 *   Group 1 [Risk 2] - ngOnInit table filtering and base-column loading
 *   Group 2 [Risk 3] - joinSelectionChanged refetch/filter logic
 *   Group 3 [Risk 3] - ok duplicate guard vs successful commit
 *   Group 4 [Risk 1] - focus and cancel flows
 */

import { HttpClient } from "@angular/common/http";
import { HttpClientTestingModule, HttpTestingController } from "@angular/common/http/testing";
import { TestBed } from "@angular/core/testing";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";

import { ComponentTool } from "../../../../../../../../common/util/component-tool";
import { PhysicalTableModel } from "../../../../../../model/datasources/database/physical-model/physical-table-model";
import { AddJoinDialog } from "./add-join-dialog.component";

const TABLE_COLUMNS_URI = "../api/data/physicalmodel/columns";
const SQL_TABLE_COLUMNS_URI = "../api/data/physicalmodel/views/columns";
const CHECK_JOIN_EXIST_URI = "../api/data/physicalmodel/join/exist";

function makeTable(overrides: Partial<PhysicalTableModel> = {}): PhysicalTableModel {
   return {
      name: "Orders",
      catalog: "CAT",
      schema: "PUBLIC",
      qualifiedName: "CAT.PUBLIC.Orders",
      path: "Orders",
      alias: null,
      sql: null,
      type: null,
      joins: [],
      baseTable: false,
      ...overrides,
   };
}

describe("AddJoinDialogComponent - single pass", () => {
   let http: HttpTestingController;

   beforeEach(() => {
      TestBed.configureTestingModule({
         imports: [HttpClientTestingModule],
         providers: [
            { provide: NgbModal, useValue: { open: vi.fn() } },
         ],
      });
      http = TestBed.inject(HttpTestingController);
   });

   afterEach(() => {
      http.verify();
      vi.restoreAllMocks();
      TestBed.resetTestingModule();
   });

   function createComponent(overrides: Partial<AddJoinDialog> = {}) {
      const comp = new AddJoinDialog(
         TestBed.inject(HttpClient),
         TestBed.inject(NgbModal),
      );
      comp.database = "OrdersDb";
      comp.id = "runtime-1";
      comp.table = makeTable({
         joins: [{
            column: "id",
            foreignTable: "CAT.PUBLIC.Customers",
            foreignColumn: "customer_id",
         } as any],
      });
      comp.tables = [
         comp.table,
         makeTable({ name: "Customers", qualifiedName: "CAT.PUBLIC.Customers" }),
         makeTable({ name: "Invoices", qualifiedName: "CAT.PUBLIC.Invoices", sql: "select * from invoices", path: "folder/Invoices" }),
      ];
      Object.assign(comp, overrides);
      return comp;
   }

   describe("Group 1 - ngOnInit", () => {
      it("should exclude the current table from foreignTables and load base columns", () => {
         const comp = createComponent();

         comp.ngOnInit();

         const req = http.expectOne(TABLE_COLUMNS_URI);
         expect(req.request.method).toBe("POST");
         req.flush(["id", "customer_id"]);

         expect(comp.foreignTables.map(table => table.qualifiedName)).toEqual([
            "CAT.PUBLIC.Customers",
            "CAT.PUBLIC.Invoices",
         ]);
         expect(comp.columns).toEqual(["id", "customer_id"]);
      });
   });

   describe("Group 2 - joinSelectionChanged", () => {
      it("should fetch SQL view columns and filter out existing duplicate joins", () => {
         const comp = createComponent();
         comp.join.column = "id";
         comp.join.foreignTable = "CAT.PUBLIC.Invoices";

         comp.joinSelectionChanged(true);

         const req = http.expectOne(SQL_TABLE_COLUMNS_URI);
         expect(req.request.body.sql).toBe("select * from invoices");
         req.flush(["customer_id", "invoice_id"]);

         expect(comp.foreignColumns).toEqual(["customer_id", "invoice_id"]);
         expect(comp.filteredForeignColumns).toEqual(["customer_id", "invoice_id"]);
      });

      it("should clear an invalid foreignColumn when local filtering removes it", () => {
         const comp = createComponent({
            foreignColumns: ["customer_id", "invoice_id"],
         });
         comp.join.column = "id";
         comp.join.foreignTable = "CAT.PUBLIC.Customers";
         comp.join.foreignColumn = "customer_id";

         comp.joinSelectionChanged(false);

         expect(comp.filteredForeignColumns).toEqual(["invoice_id"]);
         expect(comp.join.foreignColumn).toBeNull();
      });
   });

   describe("Group 3 - ok", () => {
      it("should show a duplicate-join error instead of emitting onCommit", () => {
         const comp = createComponent();
         const emitSpy = vi.spyOn(comp.onCommit, "emit");
         const messageSpy = vi.spyOn(ComponentTool, "showMessageDialog").mockImplementation(() => null as any);

         comp.ok();

         const req = http.expectOne(CHECK_JOIN_EXIST_URI);
         req.flush(true);

         expect(messageSpy).toHaveBeenCalledWith(
            expect.anything(),
            "_#(js:Error)",
            "_#(js:data.physicalmodel.joinExist)",
         );
         expect(emitSpy).not.toHaveBeenCalled();
      });

      it("should emit the join when the duplicate check returns false", () => {
         const comp = createComponent();
         const emitSpy = vi.spyOn(comp.onCommit, "emit");

         comp.ok();

         const req = http.expectOne(CHECK_JOIN_EXIST_URI);
         req.flush(false);

         expect(emitSpy).toHaveBeenCalledWith(comp.join);
      });
   });

   describe("Group 4 - focus and cancel", () => {
      it("should focus the first select after view init", () => {
         const comp = createComponent();
         comp.selectFocus = { nativeElement: { focus: vi.fn() } } as any;

         comp.ngAfterViewInit();

         expect(comp.selectFocus.nativeElement.focus).toHaveBeenCalledTimes(1);
      });

      it("should emit cancel", () => {
         const comp = createComponent();
         const emitSpy = vi.spyOn(comp.onCancel, "emit");

         comp.cancel();

         expect(emitSpy).toHaveBeenCalledWith("cancel");
      });
   });
});
