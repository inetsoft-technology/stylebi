/*
 * inetsoft-web - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
import { ChangeDetectorRef, NO_ERRORS_SCHEMA } from "@angular/core";
import { async, ComponentFixture, TestBed } from "@angular/core/testing";
import { FormsModule, ReactiveFormsModule } from "@angular/forms";
import { NgbModule } from "@ng-bootstrap/ng-bootstrap";
import { DragService } from "../../../widget/services/drag.service";
import { TreeNodeModel } from "../../../widget/tree/tree-node-model";
import { TreeNodeComponent } from "../../../widget/tree/tree-node.component";
import { TreeSearchPipe } from "../../../widget/tree/tree-search.pipe";
import { TreeComponent } from "../../../widget/tree/tree.component";
import { CalendarDataPane } from "./calendar-data-pane.component";

const targetTree: TreeNodeModel = {
   expanded: true,
   leaf: false,
   children: [{
      expanded: true,
      leaf: false,
      type: "table",
      label: "query1",
      data: {
         folder: true,
         identifier: "0^21^__NULL__^/baseWorksheet/query1",
         path: "/baseWorksheet/query1",
         scope: 0,
         type: "TABLE",
         properties: {embedded: "false", type: "0", prefix: "Orders",
            source: "baseWorksheet", mainType: "query", subType: "jdbc"}
      },
      children: [{
         expanded: false,
         leaf: true,
         type: "columnNode",
         label: "ORDER_DATE",
         data: {
            attribute: "ORDER_DATE",
            dataType: "timeInstant",
            path: "/baseWorksheet/query1/ORDER_DATE",
            refType: 0,
            properties: {}
         },
         children: []
      }]
   }]
};

describe("Calendar Data Pane Unit Test", () => {
   let changeRef = { detectChanges: jest.fn() };
   let fixture: ComponentFixture<CalendarDataPane>;
   let calendarDataPane: CalendarDataPane;
   let dragService: any;

   beforeEach(async(() => {
      dragService = { currentlyDragging: false };

      TestBed.configureTestingModule({
         imports: [
            FormsModule, ReactiveFormsModule, NgbModule
         ],
         declarations: [
            CalendarDataPane, TreeComponent, TreeNodeComponent, TreeSearchPipe
         ],
         providers: [
            { provide: ChangeDetectorRef, useValue: changeRef },
            { provide: DragService, useValue: dragService }
         ],
         schemas: [NO_ERRORS_SCHEMA]
      });
      TestBed.compileComponents();

      fixture = TestBed.createComponent(CalendarDataPane);
      calendarDataPane = <CalendarDataPane>fixture.componentInstance;
      calendarDataPane.model = {
         selectedTable: null,
         additionalTables: [],
         selectedColumn: null,
         targetTree: targetTree
      };
      fixture.detectChanges();
   }));

   //Bug #18200 check bind data
   it("check bind data", () => {
      calendarDataPane.selectColumn(targetTree.children[0].children[0]);
      expect(calendarDataPane.model.selectedTable).toBe("query1");
      expect(calendarDataPane.model.selectedColumn.attribute).toBe("ORDER_DATE");
   });
});