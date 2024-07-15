/*
 * inetsoft-web - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
import { ChangeDetectorRef, Component, NO_ERRORS_SCHEMA, ViewChild } from "@angular/core";

import { ComponentFixture, TestBed } from "@angular/core/testing";
import { FormsModule, ReactiveFormsModule } from "@angular/forms";
import { NgbModal, NgbModule } from "@ng-bootstrap/ng-bootstrap";
import { TestUtils } from "../../../common/test/test-utils";
import { ComponentTool } from "../../../common/util/component-tool";
import { DataTreeValidatorService } from "../../../vsobjects/dialog/data-tree-validator.service";
import { DragService } from "../../../widget/services/drag.service";
import { TreeNodeComponent } from "../../../widget/tree/tree-node.component";
import { TreeSearchPipe } from "../../../widget/tree/tree-search.pipe";
import { TreeComponent } from "../../../widget/tree/tree.component";
import { SelectionTreeColumnsPane } from "./selection-tree-columns-pane.component";

@Component({
   selector: "test-app",
   template: `<selection-tree-columns-pane [model]="mockModel" >
                 </selection-tree-columns-pane>`
})
class TestApp {
   @ViewChild(SelectionTreeColumnsPane, {static: false}) selectionTreeColumnsPane: SelectionTreeColumnsPane;
   mockModel = {
      selectedTable: "entity1",
      selectedColumns: [],
      targetTree: TestUtils.createMockLMDataTree(),
      compositeTargetTree: TestUtils.createMockLMDataTree(),
      composite: true
   };
}

describe("selection tree columns pane unit case", () => {
   let fixture: ComponentFixture<TestApp>;
   let changeDetectorRef: any;
   let modalService: any;
   let dragService: any;
   let dataTreeValidatorService: any;
   let selectionTreeColPane: SelectionTreeColumnsPane;

   beforeEach(() => {
      changeDetectorRef = { detectChanges: jest.fn() };
      modalService = { open: jest.fn() };
      dragService = { reset: jest.fn(), put: jest.fn() };
      dataTreeValidatorService = { validateTreeNode: jest.fn() };

      TestBed.configureTestingModule({
         imports: [NgbModule, ReactiveFormsModule, FormsModule],
         declarations: [
            TestApp, SelectionTreeColumnsPane, TreeComponent, TreeNodeComponent, TreeSearchPipe
         ],
         providers: [
            {provide: ChangeDetectorRef, useValue: changeDetectorRef},
            {provide: NgbModal, useValue: modalService},
            {provide: DragService, useValue: dragService},
            {provide: DataTreeValidatorService, useValue: dataTreeValidatorService}
         ],
         schemas: [NO_ERRORS_SCHEMA]
      }).compileComponents();

      fixture = TestBed.createComponent(TestApp);
      fixture.detectChanges();
   });

   //Bug #18998 add calcfield to selection pane, no waring pop up
   it("add calcfield column to selection tree node", () => {
      let lmTree = TestUtils.createMockLMDataTree();
      fixture.componentInstance.selectionTreeColumnsPane.selectLevelColumn(lmTree.children[0].children[0]);
      fixture.componentInstance.selectionTreeColumnsPane.addLevelNode();
      fixture.detectChanges();

      const showMessageDialog = jest.spyOn(ComponentTool, "showMessageDialog");
      expect(showMessageDialog).not.toHaveBeenCalled();
   });

   //Bug #19902
   it(" focus should be rightly when delete column from levels", () => {
      let name = TestUtils.createMockDataRef("name");
      let state = TestUtils.createMockDataRef("state");
      let city = TestUtils.createMockDataRef("city");
      fixture.componentInstance.mockModel.selectedColumns = [name, state, city];
      selectionTreeColPane = <SelectionTreeColumnsPane>fixture.componentInstance.selectionTreeColumnsPane;
      selectionTreeColPane.levelNames = ["name", "state", "city"];
      selectionTreeColPane.selectedLevelNameIndex = 2;
      fixture.detectChanges();
      selectionTreeColPane.deleteLevelNode();
      fixture.detectChanges();
      expect(selectionTreeColPane.selectedLevelNameIndex).toBe(1);
      let levels = fixture.nativeElement.querySelectorAll(".levels_id div");
      expect(levels.length).toBe(2);
      expect(levels[1].getAttribute("class")).toBe("selected");
   });
});