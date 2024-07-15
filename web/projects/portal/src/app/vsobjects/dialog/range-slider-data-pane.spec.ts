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
import {
   ChangeDetectorRef,
   Component,
   DebugElement,
   NO_ERRORS_SCHEMA,
   ViewChild
} from "@angular/core";
import { async, ComponentFixture, TestBed } from "@angular/core/testing";
import { FormsModule, ReactiveFormsModule } from "@angular/forms";
import { By } from "@angular/platform-browser";
import { NgbModule } from "@ng-bootstrap/ng-bootstrap";
import { TestUtils } from "../../common/test/test-utils";
import { DragService } from "../../widget/services/drag.service";
import { TreeNodeComponent } from "../../widget/tree/tree-node.component";
import { TreeSearchPipe } from "../../widget/tree/tree-search.pipe";
import { TreeComponent } from "../../widget/tree/tree.component";
import { DataTreeValidatorService } from "./data-tree-validator.service";
import { RangeSliderDataPane } from "./range-slider-data-pane.component";

@Component({
   selector: "test-app",
   template: `<range-slider-data-pane [model]="mockModel" [sizeModel]="mockSizeModel">
                 </range-slider-data-pane>`
})
class TestApp {
   @ViewChild(RangeSliderDataPane, {static: false}) rangeSliderDataPane: RangeSliderDataPane;
   mockModel = {
      selectedTable: "Query1",
      selectedColumns: [],
      targetTree: TestUtils.createMockWorksheetDataTree(),
      compositeTargetTree: TestUtils.createMockWorksheetDataTree(),
      composite: true
   };

   mockSizeModel = {
      length: 1,
      logScale: false,
      upperInclusive: false,
      rangeType: 3,
      rangeSize: 1,
      maxRangeSize: 1
   };
}

describe("Range Slider Data Pane Test", () => {
   let changeDetectorRef: any;
   let dragService: any;
   let dataTreeValidatorService: any;
   let fixture: ComponentFixture<TestApp>;
   let de: DebugElement;
   let el: HTMLElement;

   beforeEach(async(() => {
      changeDetectorRef = { detectChanges: jest.fn() };
      dragService = { reset: jest.fn(), put: jest.fn() };
      dataTreeValidatorService = { validateTreeNode: jest.fn() };

      TestBed.configureTestingModule({
         imports: [
            NgbModule, ReactiveFormsModule, FormsModule
         ],
         declarations: [
            TestApp, RangeSliderDataPane, TreeComponent, TreeNodeComponent, TreeSearchPipe
         ],
         providers: [
            {provide: ChangeDetectorRef, useValue: changeDetectorRef},
            {provide: DragService, useValue: dragService},
            {provide: DataTreeValidatorService, useValue: dataTreeValidatorService}
         ],
         schemas: [NO_ERRORS_SCHEMA]
      }).compileComponents();

      fixture = TestBed.createComponent(TestApp);
      fixture.detectChanges();
   }));

   it("add button should enable", () => {
      fixture.componentInstance.rangeSliderDataPane.compositeNodes = [];
      fixture.componentInstance.rangeSliderDataPane.selectedTreeCompositeNodes = [ { type: "columnNode" } ];
      jest.spyOn(fixture.componentInstance.rangeSliderDataPane, "getParentFolderLabel").mockImplementation(() => "Query1");
      fixture.detectChanges();


      fixture.whenStable().then(() => {
         de = fixture.debugElement.query(By.css("button.btn.btn-default.add-btn"));
         el = <HTMLElement>de.nativeElement;
         expect(el.hasAttribute("disabled")).toBe(false);
      });
   });

   //Bug #18964 only check tree is expand, unneed highlight selected item of tree
   it("check button status when select item", () => {
      let tree = TestUtils.createMockWorksheetDataTree();
      fixture.componentInstance.rangeSliderDataPane.selectTreeCompositeNodes([tree.children[0].children[0]]);
      fixture.componentInstance.rangeSliderDataPane.addCompositeNodes();
      let addBtn = fixture.debugElement.query(By.css("button.add-btn")).nativeElement;
      let deleteBtn = fixture.debugElement.query(By.css("button.delete-btn_id")).nativeElement;
      let moveUpBtn = fixture.debugElement.query(By.css("button.moveUp-btn_id")).nativeElement;
      let moveDownBtn = fixture.debugElement.query(By.css("button.moveDown-btn_id")).nativeElement;

      fixture.componentInstance.rangeSliderDataPane.selectCompositeNode(0);
      fixture.detectChanges();
      expect(addBtn.disabled).toBeFalsy();
      expect(deleteBtn.disabled).toBeFalsy();
      expect(moveUpBtn.disabled).toBeTruthy();
      expect(moveDownBtn.disabled).toBeFalsy();

      fixture.componentInstance.rangeSliderDataPane.selectCompositeNode(1);
      fixture.detectChanges();
      expect(addBtn.disabled).toBeFalsy();
      expect(deleteBtn.disabled).toBeFalsy();
      expect(moveUpBtn.disabled).toBeFalsy();
      expect(moveDownBtn.disabled).toBeTruthy();
   });

   //Bug #20025 should focus to the last item after delete item
   it("should focus to the last item after delete item", () => {
      fixture.componentInstance.rangeSliderDataPane.compositeNodes = [
         {
            label: "Category",
            column: {
               entity: "",
               attribute: "Category",
               dataType: "string",
               refType: 0
            }
         },
         {
            label: "City",
            column: {
               entity: "",
               attribute: "City",
               dataType: "string",
               refType: 0
            }
         },
         {
            label: "Total",
            column: {
               entity: "",
               attribute: "Total",
               dataType: "number",
               refType: 0
            }
         }
      ];
      fixture.detectChanges();

      let items = fixture.nativeElement.querySelectorAll("div.bordered-box")[1].querySelectorAll("div");
      items[2].click();
      fixture.detectChanges();

      let focusedItem = fixture.nativeElement.querySelector(
         "div.row div.bordered-box div.selected");
      expect(focusedItem.textContent).toBe("Total");

      let deleteBtn = fixture.debugElement.query(By.css("button.delete-btn_id")).nativeElement;
      deleteBtn.click();
      fixture.detectChanges();

      focusedItem = fixture.nativeElement.querySelector(
         "div.row div.bordered-box div.selected");
      expect(focusedItem.textContent).toBe("City");
   });
});