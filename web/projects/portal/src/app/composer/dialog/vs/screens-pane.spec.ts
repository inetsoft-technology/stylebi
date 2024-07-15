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
import { ScreensPane } from "./screens-pane.component";
import { ComponentFixture, TestBed } from "@angular/core/testing";
import { FormsModule, ReactiveFormsModule, FormGroup } from "@angular/forms";
import { NgbModule, NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { ViewsheetPrintLayoutDialog } from "./viewsheet-print-layout-dialog.component";
import { ViewsheetDeviceLayoutDialog } from "./viewsheet-device-layout-dialog.component";
import { ScreensPaneModel } from "../../data/vs/screens-pane-model";
import { ScreenSizeDialogModel } from "../../data/vs/screen-size-dialog-model";
import { ViewsheetDeviceLayoutDialogModel } from "../../data/vs/viewsheet-device-layout-dialog-model";
import { ViewsheetPrintLayoutDialogModel } from "../../data/vs/viewsheet-print-layout-dialog-model";
import { GenericSelectableList } from "../../../widget/generic-selectable-list/generic-selectable-list.component";
import { ScreenSizeDialog } from "./screen-size-dialog.component";
import { Viewsheet } from "../../data/vs/viewsheet";
import { EnterSubmitDirective } from "../../../widget/directive/enter-submit.directive";
import { LargeFormFieldComponent } from "../../../widget/large-form-field/large-form-field.component";
import { StandardDialogComponent } from "../../../widget/standard-dialog/standard-dialog.component";
import { DialogContentDirective } from "../../../widget/standard-dialog/dialog-content.directive";
import { DialogButtonsDirective } from "../../../widget/standard-dialog/dialog-buttons.directive";
import { ModalHeaderComponent } from "../../../widget/modal-header/modal-header.component";
import { NO_ERRORS_SCHEMA } from "@angular/core";
import { Tool } from "../../../../../../shared/util/tool";
import { ComponentTool } from "../../../common/util/component-tool";

describe("Screens Pane Test", () => {
   const createModel: () => ScreensPaneModel = () => {
      return {
         targetScreen: false,
         editDevicesAllowed: true,
         templateWidth: 200,
         templateHeight: 200,
         scaleToScreen: false,
         fitToWidth: false,
         deviceLayouts: [<ViewsheetDeviceLayoutDialogModel>{
            name: "layout1",
            mobileOnly: true,
            // scaleFont: 1,
            selectedDevices: ["phone1", "phone2"],
            id: "foo001"
         }],
         printLayout: null,
         devices: [<ScreenSizeDialogModel>{
            label: "device",
            description: "i am a device",
            id: "00",
            minWidth: 100,
            maxWidth: 300,
            tempId: null
         }],
         balancePadding: false
      };
   };

   const createPrintLayoutModel: () => ViewsheetPrintLayoutDialogModel = () => {
      return {
         paperSize: "Letter [8.5x11 in]",
         marginTop: 1,
         marginBottom: 1,
         marginRight: 1,
         marginLeft: 1,
         footerFromEdge: 0.75,
         headerFromEdge: 0.5,
         landscape: false,
         scaleFont: 1,
         numberingStart: 0,
         customWidth: 0,
         customHeight: 0,
         units: "inches"
      };
   };

   let fixture: ComponentFixture<ScreensPane>;

   beforeEach(() => {
      TestBed.configureTestingModule({
         imports: [
            FormsModule, ReactiveFormsModule, NgbModule
         ],
         declarations: [
            ScreensPane, ViewsheetPrintLayoutDialog, ViewsheetDeviceLayoutDialog,
            GenericSelectableList, ScreenSizeDialog, EnterSubmitDirective, LargeFormFieldComponent, ModalHeaderComponent,
            StandardDialogComponent, DialogContentDirective, DialogButtonsDirective
         ],
         providers: [
            NgbModal
         ],
         schemas: [NO_ERRORS_SCHEMA]
      });

      fixture = TestBed.createComponent(ScreensPane);
      fixture.componentInstance.form = new FormGroup({});
      fixture.componentInstance.model = createModel();
      fixture.componentInstance.viewsheet = new Viewsheet();
   });

   // Bug #19354 Click device 'Delete' button, confirm dialog should pop up.
   it("should pop up confirm dialog when click delete", () => {
      fixture.componentInstance.selectedLayout = 0;
      fixture.detectChanges();
      let button = fixture.nativeElement.querySelector("button.delete");

      let showConfirmDialog = jest.spyOn(ComponentTool, "showConfirmDialog");
      showConfirmDialog.mockImplementation(() => Promise.resolve("yes"));
      button.click();
      expect(showConfirmDialog).toHaveBeenCalled();
   });

   // Bug #18417, clear button should be disabled when no print layout
   it("clear button should be disabled when no print layout", () => {
      fixture.detectChanges();
      let clearBtn = fixture.nativeElement.querySelector("button.clearBtn_id");
      expect(clearBtn.hasAttribute("disabled")).toBeTruthy();

      fixture.componentInstance.model.printLayout = createPrintLayoutModel();
      fixture.detectChanges();
      expect(clearBtn.hasAttribute("disabled")).toBeFalsy();
   });

   // Bug #19349, print layout label should not be editable
   it("check print layout label", () => {
      fixture.componentInstance.model.printLayout = createPrintLayoutModel();
      fixture.detectChanges();

      let printLabel = fixture.nativeElement.querySelector("input[ng-reflect-name=printLayout]");
      expect(printLabel.hasAttribute("readonly")).toBeTruthy();
   });
});