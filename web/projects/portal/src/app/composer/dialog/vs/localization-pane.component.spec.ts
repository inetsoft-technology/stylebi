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
import { NO_ERRORS_SCHEMA } from "@angular/core";
import { async, ComponentFixture, TestBed } from "@angular/core/testing";
import { FormsModule, ReactiveFormsModule } from "@angular/forms";
import { By } from "@angular/platform-browser";
import { NgbModule } from "@ng-bootstrap/ng-bootstrap";
import { ElidedCellComponent } from "../../../widget/elided-cell/elided-cell.component";
import { DragService } from "../../../widget/services/drag.service";
import { ShuffleListComponent } from "../../../widget/shuffle-list/shuffle-list.component";
import { TreeNodeModel } from "../../../widget/tree/tree-node-model";
import { TreeNodeComponent } from "../../../widget/tree/tree-node.component";
import { TreeSearchPipe } from "../../../widget/tree/tree-search.pipe";
import { TreeComponent } from "../../../widget/tree/tree.component";
import { LocalizationPaneModel } from "../../data/vs/localization-pane-model";
import { LocalizationPane } from "./localization-pane.component";

describe("Localization Pane Unit Test", () => {
   const createTreeNodeModel: () => TreeNodeModel = () => {
      return {
         children: [{
            children: [{
               expanded: false,
               leaf: true,
               data: "TableView1.Title",
               label: "Title",
               icon: "composer-toolbox-image composer-component-tree-file",
               children: []
            },
            {
               expanded: false,
               leaf: true,
               data: "TableView1.State",
               label: "State",
               icon: "composer-toolbox-image composer-component-tree-file",
               children: []
            }],
            expanded: true,
            leaf: false,
            label: "TableView1",
            icon: "composer-toolbox-image",
            data: null
         }],
         expanded: true,
         leaf: false,
         label: "Components",
         icon: "composer-toolbox-image composer-component-tree-file",
         data: null
      };
   };

   const createModel: () => LocalizationPaneModel = () => {
      return {
         components: createTreeNodeModel(),
         localized: [{name: "TableView1.Title", textId: "Title"}]
      };
   };

   let fixture: ComponentFixture<LocalizationPane>;
   let localizationPane: LocalizationPane;
   let dragService: any;

   beforeEach(async(() => {
      dragService = { reset: jest.fn(), put: jest.fn() };

      TestBed.configureTestingModule({
         imports: [
            FormsModule, ReactiveFormsModule, NgbModule
         ],
         declarations: [
            LocalizationPane, ShuffleListComponent, TreeComponent, ElidedCellComponent,
            TreeNodeComponent, TreeSearchPipe
         ],
         providers: [
            {provide: DragService, useValue: dragService}
         ],
         schemas: [NO_ERRORS_SCHEMA]
      });
      TestBed.compileComponents();

      fixture = TestBed.createComponent(LocalizationPane);
      localizationPane = <LocalizationPane>fixture.componentInstance;
      localizationPane.model = createModel();
      fixture.detectChanges();
   }));

   //Bug #19630 Components node should display
   //Bug #19118 should focus on the selected column
   it("should display the components and focus on the selected column", () => {
      let componentsTree: TreeComponent =
         fixture.debugElement.query(By.css("div.shuffle-left tree")).componentInstance;
      let elidedCell: ElidedCellComponent =
         fixture.debugElement.query(By.directive(ElidedCellComponent)).componentInstance;
      let inputTitle = fixture.debugElement.query(By.css(".text-id-col input")).nativeElement;

      expect(componentsTree).not.toBeNull();
      expect(elidedCell.text).toBe("TableView1.Title");
      expect(inputTitle.value).toBe("Title");

      componentsTree.nodesSelected.emit(localizationPane.model.components.children[0].children);
      fixture.detectChanges();

      let addButton = fixture.debugElement.query(By.css(".add-localization-button")).nativeElement;
      expect(addButton.disabled).toBe(false);
   });
});