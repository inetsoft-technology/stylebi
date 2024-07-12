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
import { NgbModule } from "@ng-bootstrap/ng-bootstrap";
import { Observable, of as observableOf } from "rxjs";
import { DashboardModel } from "../../common/data/dashboard-model";
import { EnterSubmitDirective } from "../../widget/directive/enter-submit.directive";
import { ResizableTableDirective } from "../../widget/directive/resizable-table.directive";
import { ModelService } from "../../widget/services/model.service";
import { DialogButtonsDirective } from "../../widget/standard-dialog/dialog-buttons.directive";
import { DialogContentDirective } from "../../widget/standard-dialog/dialog-content.directive";
import { StandardDialogComponent } from "../../widget/standard-dialog/standard-dialog.component";
import { ArrangeDashboardDialog } from "./arrange-dashboard-dialog.component";

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

describe("Arrange Dashboard Dialog Unit Test", () => {
   const createModel: () => Observable<any> = () => {
      return observableOf({
         dashboards: []
      });
   };

   let modelService = {
      getModel: jest.fn(() => createModel()),
      putModel: jest.fn(() => observableOf(new HttpResponse({body: null}))),
      sendModel: jest.fn(() => observableOf(new HttpResponse({body: null})))
   };

   let fixture: ComponentFixture<ArrangeDashboardDialog>;
   let arrangeDashboardDialog: ArrangeDashboardDialog;

   beforeEach(async(() => {
      TestBed.configureTestingModule({
         imports: [
            FormsModule, ReactiveFormsModule, NgbModule
         ],
         declarations: [
            ArrangeDashboardDialog, StandardDialogComponent, EnterSubmitDirective,
            DialogContentDirective, DialogButtonsDirective, ResizableTableDirective
         ],
         providers: [
            {
               provide: ModelService, useValue: modelService
            }
         ],
         schemas: [ NO_ERRORS_SCHEMA ]
      });
      TestBed.compileComponents();

      fixture = TestBed.createComponent(ArrangeDashboardDialog);
      arrangeDashboardDialog = <ArrangeDashboardDialog>fixture.componentInstance;
      fixture.detectChanges();
   }));

   //Bug #18799 not enabled dashboard should display in list
   it("should display dashboard which is not enabled", () => {
       arrangeDashboardDialog.model.dashboards =
          [createDashModel("dash1"), createDashModel("dash2"), createDashModel("dash3")];
       fixture.detectChanges();

       let enableChk = fixture.nativeElement.querySelectorAll(
          "div.resizable-table-body-container input[type=checkbox]");
       enableChk[0].click();
       fixture.detectChanges();

       let dashLines = fixture.nativeElement.querySelectorAll(
          "div.resizable-table-body-container tr");

       expect(dashLines.length).toBe(3);
       expect(dashLines[0].querySelectorAll("td")[0].textContent.trim()).toBe("dash2");
       expect(dashLines[1].querySelectorAll("td")[0].textContent.trim()).toBe("dash3");
       expect(dashLines[2].querySelectorAll("td")[0].textContent.trim()).toBe("dash1");
   });
});