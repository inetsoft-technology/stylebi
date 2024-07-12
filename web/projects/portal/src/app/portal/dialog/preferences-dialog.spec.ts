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
import { HttpResponse } from "@angular/common/http";
import { NO_ERRORS_SCHEMA } from "@angular/core";
import { async, ComponentFixture, TestBed } from "@angular/core/testing";
import { FormsModule, ReactiveFormsModule } from "@angular/forms";
import { By } from "@angular/platform-browser";
import { NgbModal, NgbModule } from "@ng-bootstrap/ng-bootstrap";
import { Observable, of as observableOf } from "rxjs";
import { DashboardModel } from "../../common/data/dashboard-model";
import { ComponentTool } from "../../common/util/component-tool";
import { EnterSubmitDirective } from "../../widget/directive/enter-submit.directive";
import { ResizableTableDirective } from "../../widget/directive/resizable-table.directive";
import { ModelService } from "../../widget/services/model.service";
import { DialogButtonsDirective } from "../../widget/standard-dialog/dialog-buttons.directive";
import { DialogContentDirective } from "../../widget/standard-dialog/dialog-content.directive";
import { StandardDialogComponent } from "../../widget/standard-dialog/standard-dialog.component";
import { ChangePasswordDialog } from "./change-password-dialog.component";
import { PreferencesDialog } from "./preferences-dialog.component";

let createDashModel: (dashName: string) => DashboardModel = (dashName) => {
   return {
      name: dashName,
      label: dashName,
      type: "",
      description: "",
      path: "",
      identifier: "",
      enabled: true
   };
};

describe("Preferences Dialog Unit Test", () => {
   const createModel: () => Observable<any> = () => {
      return observableOf({
         email: "",
         changePasswordAvailable: true
      });
   };

   let ngbService = { open: jest.fn() };
   let modelService = {
      getModel: jest.fn(() => createModel()),
      putModel: jest.fn(() => observableOf(new HttpResponse({body: null}))),
      sendModel: jest.fn(() => observableOf(new HttpResponse({body: null})))
   };

   let fixture: ComponentFixture<PreferencesDialog>;
   let preferencesDialog: PreferencesDialog;

   beforeEach(async(() => {
      TestBed.configureTestingModule({
         imports: [
            FormsModule, ReactiveFormsModule, NgbModule
         ],
         declarations: [
            PreferencesDialog, ChangePasswordDialog, StandardDialogComponent,
            EnterSubmitDirective, DialogContentDirective, DialogButtonsDirective,
            ResizableTableDirective
         ],
         providers: [
            {
               provide: NgbModal, useValue: ngbService
            },
            {
               provide: ModelService, useValue: modelService
            }
         ],
         schemas: [ NO_ERRORS_SCHEMA ]
      });
      TestBed.compileComponents();

      fixture = TestBed.createComponent(PreferencesDialog);
      preferencesDialog = <PreferencesDialog>fixture.componentInstance;
      fixture.detectChanges();
   }));

   //Bug #18784 should pop confirm dialog when no email input
   it("should pop confirm dialog when no email input", () => {
      let showConfirmDialog = jest.spyOn(ComponentTool, "showConfirmDialog");
      showConfirmDialog.mockImplementation(() => Promise.resolve("ok"));
      preferencesDialog.okClicked();

      expect(showConfirmDialog).toHaveBeenCalled();
   });
});
