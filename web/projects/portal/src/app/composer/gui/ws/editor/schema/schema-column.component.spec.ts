/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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
import { Component, Input } from "@angular/core";
import { ComponentFixture, TestBed } from "@angular/core/testing";
import { ReplaySubject } from "rxjs";
import { ColumnRef } from "../../../../../binding/data/column-ref";
import { FixedDropdownService } from "../../../../../widget/fixed-dropdown/fixed-dropdown.service";
import { AbstractTableAssembly } from "../../../../data/ws/abstract-table-assembly";
import { TableColumnPair } from "../../../../data/ws/table-column-pair";
import { SchemaColumnComponent } from "./schema-column.component";
import { SchemaThumbnailService } from "./schema-thumbnail.service";

interface TestPair {
   table: AbstractTableAssembly;
   columns: ColumnRef[];
}

// Reuses the schema-column instances by position, the worst case for cached column state.
@Component({
   selector: "test-host",
   standalone: true,
   imports: [SchemaColumnComponent],
   template: `
      @for (_pair of pairs; track $index) {
         @for (_col of _pair.columns; track $index) {
            <schema-column [schemaTable]="_pair.table" [column]="_col" [twoStepJoinColumn]="null">
            </schema-column>
         }
      }
   `
})
class TestHostComponent {
   @Input() pairs: TestPair[] = [];
}

function col(name: string, dataType: string): ColumnRef {
   return {name, dataType, dataRefModel: {name, attribute: name, classType: "AttributeRef"}} as any;
}

function table(name: string): AbstractTableAssembly {
   return {name, colInfos: []} as any;
}

/**
 * Real SchemaThumbnailService registration logic, with jsPlumb stubbed out. The element id is
 * assigned by makeSource, as jsPlumb does.
 */
function createService(): SchemaThumbnailService {
   let nextId = 0;
   const service: any = Object.create(SchemaThumbnailService.prototype);
   service.tableColumns = {};
   service.refMap = {};
   service.tableMap = {};
   service.sourceIds = new Map<ColumnRef, string>();
   service.connectingColumnSubject = new ReplaySubject<TableColumnPair>(1);
   service.focusColumnPairSubject = new ReplaySubject<ColumnRef[]>(1);
   service.jsp = {
      makeSource: (el: HTMLElement) => el.id = el.id || "schema-col-" + nextId++,
      makeTarget: () => {},
      unmakeSource: () => {},
      unmakeTarget: () => {}
   };
   service.registerTable("CUSTOMERS1", {nativeElement: {}});
   service.registerTable("CONTACTS1", {nativeElement: {}});
   return service;
}

describe("SchemaColumnComponent", () => {
   let fixture: ComponentFixture<TestHostComponent>;
   let service: any;
   const customers = table("CUSTOMERS1");
   const contacts = table("CONTACTS1");

   // columns as ordered by reorderColumns() with CUSTOMER_ID = CUSTOMER_ID join condition
   const joinedPairs = (): TestPair[] => [
      {table: customers, columns: [col("CUSTOMER_ID", "integer"), col("ADDRESS", "string"),
            col("CITY", "string"), col("COMPANY_NAME", "string")]},
      {table: contacts, columns: [col("CUSTOMER_ID", "integer"), col("CONTACT_ID", "integer"),
            col("FIRST_NAME", "string"), col("LAST_NAME", "string")]}
   ];

   // natural order after the join condition is removed
   const unjoinedPairs = (): TestPair[] => [
      {table: customers, columns: [col("ADDRESS", "string"), col("CITY", "string"),
            col("COMPANY_NAME", "string"), col("CUSTOMER_ID", "integer")]},
      {table: contacts, columns: [col("CONTACT_ID", "integer"), col("CUSTOMER_ID", "integer"),
            col("FIRST_NAME", "string"), col("LAST_NAME", "string")]}
   ];

   const render = (pairs: TestPair[]) => {
      fixture.componentRef.setInput("pairs", pairs);
      fixture.detectChanges();
   };

   const columnElements = (): HTMLElement[] =>
      Array.from(fixture.nativeElement.querySelectorAll("schema-column"));

   const findElement = (tableIndex: number, name: string): HTMLElement =>
      columnElements().slice(tableIndex * 4, tableIndex * 4 + 4)
         .find((el) => el.textContent.trim() === name);

   beforeEach(() => {
      service = createService();

      TestBed.configureTestingModule({
         imports: [TestHostComponent],
         providers: [
            {provide: SchemaThumbnailService, useValue: service},
            {provide: FixedDropdownService, useValue: {open: vi.fn()}}
         ]
      });

      fixture = TestBed.createComponent(TestHostComponent);
   });

   // Bug #76941
   it("should highlight compatible columns of the dragged column after columns are reordered", () => {
      render(joinedPairs());
      render(unjoinedPairs());

      const dragged = findElement(0, "CUSTOMER_ID");
      const draggedPair: TableColumnPair = service.tableColumns[dragged.id];
      expect(draggedPair.column.name).toBe("CUSTOMER_ID");

      // simulate jsPlumb connectionDrag
      service.getConnectingColumnSubject().next(draggedPair);
      fixture.detectChanges();

      const compatible = (name: string) =>
         findElement(1, name).classList.contains("schema-column-compatible");
      expect(compatible("CONTACT_ID")).toBe(true);
      expect(compatible("CUSTOMER_ID")).toBe(true);
      expect(compatible("FIRST_NAME")).toBe(false);
      expect(compatible("LAST_NAME")).toBe(false);
   });

   // Bug #76941
   it("should resolve join line endpoints to the displayed column after columns are reordered", () => {
      render(joinedPairs());
      render(unjoinedPairs());

      for(const name of ["ADDRESS", "CITY", "COMPANY_NAME", "CUSTOMER_ID"]) {
         expect(service.getId("CUSTOMERS1", col(name, "string"))).toBe(findElement(0, name).id);
      }

      for(const name of ["CONTACT_ID", "CUSTOMER_ID", "FIRST_NAME", "LAST_NAME"]) {
         expect(service.getId("CONTACTS1", col(name, "string"))).toBe(findElement(1, name).id);
      }
   });

   // Bug #76941
   it("should keep join line endpoints when a column is replaced by a new object of the same name", () => {
      render(unjoinedPairs());
      // e.g. a subtable refresh replaces the column refs
      render(unjoinedPairs());

      expect(service.refMap["CUSTOMERS1"].length).toBe(4);
      expect(service.getId("CUSTOMERS1", col("CUSTOMER_ID", "integer")))
         .toBe(findElement(0, "CUSTOMER_ID").id);
   });
});
