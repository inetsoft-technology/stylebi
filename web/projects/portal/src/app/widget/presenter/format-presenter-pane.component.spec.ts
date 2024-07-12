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
import { NO_ERRORS_SCHEMA } from "@angular/core";
import { ComponentFixture, TestBed } from "@angular/core/testing";
import { FormsModule, ReactiveFormsModule } from "@angular/forms";
import { NgbModal, NgbModule } from "@ng-bootstrap/ng-bootstrap";
import { of as observableOf } from "rxjs";
import { DropDownTestModule } from "../../common/test/test-module";
import { DataTreeValidatorService } from "../../vsobjects/dialog/data-tree-validator.service";
import { FixedDropdownDirective } from "../fixed-dropdown/fixed-dropdown.directive";
import { ModelService } from "../services/model.service";
import { TreeDropdownComponent } from "../tree/tree-dropdown.component";
import { TreeNodeComponent } from "../tree/tree-node.component";
import { TreeSearchPipe } from "../tree/tree-search.pipe";
import { TreeComponent } from "../tree/tree.component";
import { FormatPresenterPane } from "./format-presenter-pane.component";

describe("format presenter pane componnet unit case", () => {
   let modelService: any;
   let modalService: any;
   let treeService: any;
   let formatPresenterPane: FormatPresenterPane;
   let fixture: ComponentFixture<FormatPresenterPane>;

   beforeEach(() => {
      modelService = { getModel: jest.fn() };
      modalService = { open: jest.fn() };
      modelService.getModel.mockImplementation(() => observableOf([{}]));
      treeService = { validateTreeNode: jest.fn() };

      TestBed.configureTestingModule({
         imports: [DropDownTestModule, ReactiveFormsModule, FormsModule, NgbModule],
         declarations: [
            FormatPresenterPane, FixedDropdownDirective, TreeComponent, TreeSearchPipe,
            TreeNodeComponent, TreeDropdownComponent
         ],
         providers: [
            {provide: ModelService, useValue: modelService},
            {provide: NgbModal, useValue: modalService},
            {provide: DataTreeValidatorService, useValue: treeService}
         ],
         schemas: [NO_ERRORS_SCHEMA]
      });

      fixture = TestBed.createComponent(FormatPresenterPane);
      formatPresenterPane = <FormatPresenterPane>fixture.componentInstance;
   });

   //Bug #19125 should open edit presentor dialog
   //@TODO need check pop up dialog
   it("should open edit presenter dialog", () => {
      // let presenterDialogModel: PresenterPropertyDialogModel = {
      //    descriptors: [{
      //                   name: "Bar Height",
      //                   displayName: "DoublePropertyEditor",
      //                   editor: "barHeight",
      //                }],
      //    presenter: "inetsoft.report.painter.Barcode2of7Presenter"
      // };
      // modelService.getModel.mockImplementation(() => observableOf(presenterDialogModel));

      formatPresenterPane.presenterLabel = "Barcode 2 of 7";
      formatPresenterPane.presenterPath = "inetsoft.report.painter.Barcode2of7Presenter";
      formatPresenterPane.hasDescriptors = true;
      formatPresenterPane.runtimeId = "prensenter";
      formatPresenterPane.tableSelected = true;
      fixture.detectChanges();
      expect(formatPresenterPane.isPresenterDialogEnabled()).toBeTruthy();
   });
});