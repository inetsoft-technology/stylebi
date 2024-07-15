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
import { FormGroup, FormsModule, ReactiveFormsModule } from "@angular/forms";
import { NgbModal, NgbModule } from "@ng-bootstrap/ng-bootstrap";
import { ComponentTool } from "../../../common/util/component-tool";
import { EnterSubmitDirective } from "../../../widget/directive/enter-submit.directive";
import { GenericSelectableList } from "../../../widget/generic-selectable-list/generic-selectable-list.component";
import { ModalHeaderComponent } from "../../../widget/modal-header/modal-header.component";
import { ScreenSizeDialogModel } from "../../data/vs/screen-size-dialog-model";
import { ViewsheetDeviceLayoutDialogModel } from "../../data/vs/viewsheet-device-layout-dialog-model";
import { ScreenSizeDialog } from "./screen-size-dialog.component";
import { ViewsheetDeviceLayoutDialog } from "./viewsheet-device-layout-dialog.component";

describe("Viewsheet Device Layout Dialog Unit Test", () => {
   const createModel: () => ViewsheetDeviceLayoutDialogModel = () => {
      return {
         name: "layout1",
         mobileOnly: true,
         scaleFont: 1,
         selectedDevices: ["phone1", "phone2"],
         id: "foo01"
      };
   };

   let fixture: ComponentFixture<ViewsheetDeviceLayoutDialog>;
   let modalService: any;
   let ngbModalRef: any;

   beforeEach(async(() => {
      ngbModalRef = {
         close: jest.fn(),
         dismiss: jest.fn(),
         result: {
            then: jest.fn()
         }
      };
      modalService = { open: jest.fn(() => ngbModalRef) };

      TestBed.configureTestingModule({
         imports: [
            FormsModule, ReactiveFormsModule, NgbModule
         ],
         declarations: [
            ViewsheetDeviceLayoutDialog, GenericSelectableList, ScreenSizeDialog, EnterSubmitDirective, ModalHeaderComponent
         ],
         providers: [
            { provide: NgbModal, useValue: modalService }
         ],
         schemas: [NO_ERRORS_SCHEMA]
      });

      fixture = TestBed.createComponent(ViewsheetDeviceLayoutDialog);
      fixture.componentInstance.isEditAllowed = true;
   }));

   // Bug #16761 dont delete layouts when editing a layout
   it("should not point to same reference as input layouts", () => {
      fixture.componentInstance.formDevice = new FormGroup({});
      let deviceLayouts: ViewsheetDeviceLayoutDialogModel[] = [createModel()];
      fixture.componentInstance.layouts = deviceLayouts;
      fixture.componentInstance.index = 0;
      fixture.componentInstance.add = false;
      fixture.componentInstance.devices = [];
      fixture.detectChanges();

      expect(fixture.componentInstance.layouts).not.toBe(deviceLayouts);
   });

   // Bug #16611 open screen size dialog with correct index
   it("should not point to same reference as input layouts", () => {
      fixture.componentInstance.formDevice = new FormGroup({});
      let deviceLayouts: ViewsheetDeviceLayoutDialogModel[] = [createModel()];
      fixture.componentInstance.layouts = deviceLayouts;
      fixture.componentInstance.index = 0;
      fixture.componentInstance.add = false;
      fixture.componentInstance.devices = [<ScreenSizeDialogModel> {
         label: "device",
         description: "i am a device",
         id: "00",
         minWidth: 100,
         maxWidth: 300,
         tempId: null
      }];
      fixture.detectChanges();
      let button = fixture.nativeElement.querySelector("button.edit-icon");
      button.click();

      fixture.whenStable().then(() => {
         expect(fixture.componentInstance.editDevice).toBe(0);
      });
   });

   // Bug #19358 warning for duplicate sized layout
   it("should pop warning for duplicate sized layout", () => {
      fixture.componentInstance.formDevice = new FormGroup({});
      fixture.componentInstance.model = createModel();
      fixture.componentInstance.devices = [
         {
            label: "phone1",
            description: "phone1",
            id: "phone1",
            minWidth: 100,
            maxWidth: 300,
            tempId: "phone1"},
         {
            label: "phone2",
            description: "phone2",
            id: "phone2",
            minWidth: 700,
            maxWidth: 900,
            tempId: "phone2"
         }
      ];
      let deviceLayouts: ViewsheetDeviceLayoutDialogModel[] = [
         createModel(),
         {
            name: "layout2",
            mobileOnly: true,
            selectedDevices: ["phone1"],
            id: "foo02"
         }];
      fixture.componentInstance.layouts = deviceLayouts;

      let okBtn = fixture.nativeElement.querySelector("button.btn.btn-primary");
      let showConfirmDialog = jest.spyOn(ComponentTool, "showConfirmDialog");
      showConfirmDialog.mockImplementation(() => Promise.resolve("yes"));
      okBtn.click();

      expect(showConfirmDialog).toHaveBeenCalled();
   });

   // Bug #19354 warning when delete device
   it("should pop warning for when delete device", () => {
      fixture.componentInstance.formDevice = new FormGroup({});
      fixture.componentInstance.model = createModel();
      fixture.componentInstance.devices = [
         {
            label: "phone1",
            description: "phone1",
            id: "phone1",
            minWidth: 100,
            maxWidth: 300,
            tempId: "phone1"},
         {
            label: "phone2",
            description: "phone2",
            id: "phone2",
            minWidth: 700,
            maxWidth: 900,
            tempId: "phone2"
         }
      ];
      fixture.componentInstance.layouts = [createModel()];
      fixture.componentInstance.index = 0;
      fixture.componentInstance.add = false;
      fixture.detectChanges();

      let delBtn = fixture.nativeElement.querySelectorAll("button.close-icon")[0];
      let showConfirmDialog = jest.spyOn(ComponentTool, "showConfirmDialog");
      showConfirmDialog.mockImplementation(() => Promise.resolve("yes"));
      delBtn.click();

      expect(showConfirmDialog).toHaveBeenCalled();
   });
});