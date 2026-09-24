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
 * EditJoinTableComponent - Bug #77013 regression
 *
 * The `@for (_col of table.columns; track $index)` loop in
 * edit-join-table.component.html used to track rows by index. When
 * PhysicalJoinEditPane.reorderColumns()/movePosition() swap two entries of
 * table.columns in place (in response to a fresh JoinGraphModel, e.g. after
 * "Remove Join Condition"), Angular reused the EditJoinTableColumnComponent
 * instance (and its DOM element) at each index instead of moving the
 * existing row to follow its column. Because that component registers
 * itself with JoinThumbnailService exactly once, in ngAfterViewInit(), the
 * service's tableColumns[sourceId] map went stale after a reorder, so
 * jsPlumb drag/drop/highlight handlers resolved the wrong column for a
 * given row (wrong highlight, wrong join drop target).
 *
 * This spec renders the real template via TestBed (a direct-instantiation
 * test cannot observe @for reconciliation) and asserts that, for every
 * rendered row, the column JoinThumbnailService has registered against that
 * row's DOM element always matches the column currently bound to that row's
 * component instance - including after an in-place reorder identical to
 * PhysicalJoinEditPane.movePosition().
 */

import { DebugElement } from "@angular/core";
import { ComponentFixture, TestBed } from "@angular/core/testing";
import { By } from "@angular/platform-browser";
import { Subject } from "rxjs";

import { Rectangle } from "../../../../../common/data/rectangle";
import { GraphColumnInfo } from "../../../model/datasources/database/physical-model/graph/graph-column-info";
import { TableGraphModel } from "../../../model/datasources/database/physical-model/graph/table-graph-model";
import { EditJoinTableColumnComponent } from "./edit-join-table-column.component";
import { EditJoinTableComponent } from "./edit-join-table.component";
import { JoinThumbnailService } from "./join-thumbnail.service";

function makeColumn(name: string, table = "orders"): GraphColumnInfo {
   // Matches the server-generated id format (tableName + "-" + columnName)
   // confirmed in PhysicalModelService.java / QueryGraphModelService.java.
   return { id: `${table}-${name}`, name, type: "string", table };
}

function makeTable(name: string, columns: GraphColumnInfo[]): TableGraphModel {
   return {
      name,
      bounds: new Rectangle(0, 0, 100, 50),
      columns,
      joins: [],
   };
}

/**
 * Minimal fake mirroring the real JoinThumbnailService's registration
 * behavior: registerColumn()/unregisterColumn() key state by the DOM
 * element's id, exactly like the real tableColumns[sourceId] map. The
 * jsPlumbInstance stub assigns each host element a stable id the first
 * time makeSource/makeTarget runs, the same way real jsPlumb does.
 */
class FakeJoinThumbnailService {
   private idCounter = 0;
   readonly registrations = new Map<string, GraphColumnInfo>();
   private readonly connectingColumnSubject = new Subject<GraphColumnInfo>();
   readonly focusColumnPairSubject = new Subject<GraphColumnInfo[]>();

   readonly jsPlumbInstance: any = {
      makeSource: (el: HTMLElement) => this.assignId(el),
      makeTarget: (el: HTMLElement) => this.assignId(el),
      unmakeSource: () => {},
      unmakeTarget: () => {},
      draggable: () => {},
      selectEndpoints: () => ({ setAnchor: () => {} }),
      repaintEverything: () => {},
   };

   private assignId(el: HTMLElement): void {
      if(!el.id) {
         el.id = `jsp-el-${this.idCounter++}`;
      }
   }

   getConnectingColumnSubject(): Subject<GraphColumnInfo> {
      return this.connectingColumnSubject;
   }

   registerColumn(col: GraphColumnInfo, sourceId: string): void {
      this.registrations.set(sourceId, col);
   }

   unregisterColumn(_col: GraphColumnInfo, sourceId: string): void {
      this.registrations.delete(sourceId);
   }

   getJoinEditPaneModel(): any {
      return undefined;
   }
}

describe("EditJoinTableComponent - stable track key for @for (Bug #77013)", () => {
   let fixture: ComponentFixture<EditJoinTableComponent>;
   let comp: EditJoinTableComponent;
   let thumbnailService: FakeJoinThumbnailService;
   let table: TableGraphModel;

   beforeEach(async () => {
      await TestBed.configureTestingModule({
         imports: [EditJoinTableComponent],
         providers: [
            { provide: JoinThumbnailService, useClass: FakeJoinThumbnailService },
         ],
      }).compileComponents();

      fixture = TestBed.createComponent(EditJoinTableComponent);
      comp = fixture.componentInstance;
      thumbnailService = TestBed.inject(JoinThumbnailService) as any;

      table = makeTable("orders", [
         makeColumn("customer_id", "orders"),
         makeColumn("region_id", "orders"),
      ]);
      comp.table = table;
      fixture.detectChanges(); // ngAfterViewInit -> registers each row exactly once
   });

   function columnRows(): DebugElement[] {
      return fixture.debugElement.queryAll(By.directive(EditJoinTableColumnComponent));
   }

   function expectRegistrationsMatchRenderedColumns(): void {
      const rows = columnRows();
      expect(rows.length).toBe(2);

      for(const row of rows) {
         const rowInstance = row.componentInstance as EditJoinTableColumnComponent;
         const hostId: string = (row.nativeElement as HTMLElement).id;

         expect(hostId).toBeTruthy();
         expect(thumbnailService.registrations.get(hostId)).toBe(rowInstance.column);
      }
   }

   it("keeps registrations in sync with the rendered columns before any reorder", () => {
      expectRegistrationsMatchRenderedColumns();
   });

   it("keeps the registered column in sync with the rendered column after an in-place reorder", () => {
      // Same in-place swap PhysicalJoinEditPane.movePosition() performs:
      // columns[to] = columns.splice(from, 1, columns[to])[0];
      const cols = table.columns;
      cols[0] = cols.splice(1, 1, cols[0])[0];

      fixture.detectChanges();

      expectRegistrationsMatchRenderedColumns();
   });
});
