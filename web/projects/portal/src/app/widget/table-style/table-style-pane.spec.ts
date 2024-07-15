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
import { ChangeDetectorRef, NO_ERRORS_SCHEMA } from "@angular/core";
import { async, ComponentFixture, TestBed } from "@angular/core/testing";
import { FormsModule, ReactiveFormsModule } from "@angular/forms";
import { NgbModule } from "@ng-bootstrap/ng-bootstrap";
import { DragService } from "../services/drag.service";
import { TreeNodeModel } from "../tree/tree-node-model";
import { TreeNodeComponent } from "../tree/tree-node.component";
import { TreeSearchPipe } from "../tree/tree-search.pipe";
import { TreeComponent } from "../tree/tree.component";
import { TableStylePaneModel } from "./table-style-pane-model";
import { TableStylePane } from "./table-style-pane.component";

describe("Table Style Pane unit test", () => {
   let createTreeNodeModel: () => TreeNodeModel = () => {
      return {
         label: null,
         data: null,
         dataLabel: null,
         icon: null,
         expandedIcon: null,
         collapsedIcon: null,
         toggleExpandedIcon: null,
         toggleCollapsedIcon: null,
         leaf: false,
         children: [{
            children: [{
               children: [],
               collapsedIcon: null,
               cssClass: null,
               data: "StandardStyle",
               dataLabel: null,
               dragData: null,
               dragName: null,
               expanded: false,
               expandedIcon: null,
               icon: "fa fa-table",
               label: "StandardStyle",
               leaf: true,
               toggleExpandedIcon: null,
               toggleCollapsedIcon: null,
               tooltip: null,
               type: "style"
            }],
            collapsedIcon: null,
            cssClass: null,
            data: null,
            dataLabel: null,
            dragData: null,
            dragName: null,
            expanded: true,
            expandedIcon: null,
            icon: null,
            label: "Styles",
            leaf: false,
            toggleExpandedIcon: null,
            toggleCollapsedIcon: null,
            tooltip: null,
            type: "folder"
         }],
         expanded: true,
         dragName: null,
         dragData: null,
         type: null,
         cssClass: null,
         tooltip: null
      };
   };

   let createModel: () => TableStylePaneModel = () => {
      return {
         tableStyle: "",
         styleTree: createTreeNodeModel()
      };
   };

   let fixture: ComponentFixture<TableStylePane>;
   let stylePane: TableStylePane;

   beforeEach(async(() => {
      TestBed.configureTestingModule({
         imports: [
            FormsModule, ReactiveFormsModule, NgbModule
         ],
         declarations: [
            TableStylePane, TreeComponent, TreeNodeComponent, TreeSearchPipe
         ],
         providers: [
            ChangeDetectorRef, DragService
         ],
         schemas: [NO_ERRORS_SCHEMA]
      });
      TestBed.compileComponents();
   }));

   //for Bug #10149, table style can not show up  on preview pane
   it("should show table style on preview pane", () => {
      fixture = TestBed.createComponent(TableStylePane);
      stylePane = <TableStylePane>fixture.componentInstance;
      stylePane.model = createModel();
      stylePane.isComposer = true;
      stylePane.selectStyle(stylePane.model.styleTree.children[0].children[0]);
      fixture.detectChanges();

      let previewStyle: any = fixture.nativeElement.querySelector(".table_style_preview_id img");
      expect(previewStyle).not.toBeNull();
   });

});
