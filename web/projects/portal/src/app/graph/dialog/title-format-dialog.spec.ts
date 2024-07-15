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
import { NO_ERRORS_SCHEMA } from "@angular/core";
import { async, ComponentFixture, TestBed } from "@angular/core/testing";
import { FormsModule, ReactiveFormsModule } from "@angular/forms";
import { NgbModal, NgbModule } from "@ng-bootstrap/ng-bootstrap";
import { UIContextService } from "../../common/services/ui-context.service";
import { DropDownTestModule } from "../../common/test/test-module";
import { DynamicComboBox } from "../../widget/dynamic-combo-box/dynamic-combo-box.component";
import { TitleFormatDialogModel } from "../model/dialog/title-format-dialog-model";
import { TitleFormatDialog } from "./title-format-dialog.component";
import { TitleFormatPane } from "./title-format-pane.component";

describe("Title Format Dialog Unit Test", () => {
   let createMockTitleFormatDialogModel: (title?: string) => TitleFormatDialogModel = (title?: string) => {
      return {
         titleFormatPaneModel: {
            title: title,
            currentTitle: title,
            rotationRadioGroupModel: {
               rotation: ""
            }
         },
         oldTitle: ""
      };
   };
   let uiContextService: any;
   let fixture: ComponentFixture<TitleFormatDialog>;
   let titleFormatDialog: TitleFormatDialog;

   beforeEach(async(() => {
      uiContextService = {
         isVS: jest.fn(),
         isAdhoc: jest.fn(),
         getDefaultTab: jest.fn(),
         setDefaultTab: jest.fn()
      };

      TestBed.configureTestingModule({
         imports: [
            FormsModule, ReactiveFormsModule, NgbModule, DropDownTestModule
         ],
         declarations: [
            TitleFormatDialog, TitleFormatPane, DynamicComboBox
         ],
         providers: [{
            provide: UIContextService, useValue: uiContextService
         }, NgbModal],
         schemas: [NO_ERRORS_SCHEMA]
      }).compileComponents();
   }));

   //Bug #20473
   it("should revert to default title if clear title text", (done) => {
      uiContextService.isAdhoc.mockImplementation(() => "false");
      let model = createMockTitleFormatDialogModel("state");
      fixture = TestBed.createComponent(TitleFormatDialog);
      titleFormatDialog = <TitleFormatDialog>fixture.componentInstance;
      titleFormatDialog.model = model;
      fixture.detectChanges();

      let titleInput: HTMLInputElement = fixture.nativeElement.querySelector(".axis_title_id dynamic-combo-box input");
      let okBtn: HTMLButtonElement = fixture.nativeElement.querySelector("button.ok_id");
      titleInput.value = "";
      titleInput.dispatchEvent(new Event("input"));
      fixture.detectChanges();

      titleFormatDialog.onCommit.subscribe((titleModel: TitleFormatDialogModel) => {
         expect(titleModel.titleFormatPaneModel.title).toEqual("state");

         done();
      });
      okBtn.click();
   });
});