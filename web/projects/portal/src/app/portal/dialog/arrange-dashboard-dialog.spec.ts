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
import { HttpResponse } from "@angular/common/http";
import { NO_ERRORS_SCHEMA } from "@angular/core";
import { waitForAsync, ComponentFixture, TestBed } from "@angular/core/testing";
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
import { HttpClientTestingModule } from "@angular/common/http/testing";

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
      getModel: vi.fn(() => createModel()),
      putModel: vi.fn(() => observableOf(new HttpResponse({body: null}))),
      sendModel: vi.fn(() => observableOf(new HttpResponse({body: null})))
   };

   let fixture: ComponentFixture<ArrangeDashboardDialog>;
   let arrangeDashboardDialog: ArrangeDashboardDialog;

   beforeEach(waitForAsync(() => {
      TestBed.configureTestingModule({
         imports: [
            
            HttpClientTestingModule,FormsModule,
            ReactiveFormsModule,
            NgbModule,
            ArrangeDashboardDialog,
            StandardDialogComponent,
            EnterSubmitDirective,
            DialogContentDirective,
            DialogButtonsDirective,
            ResizableTableDirective,
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

   //Bug #76593 disabling one dashboard should not leave an unrelated dashboard's
   //checkbox showing a stale (unchecked) state after the reorder
   it("should keep unrelated dashboards checked after toggling one dashboard off",
      waitForAsync(() =>
   {
      arrangeDashboardDialog.model.dashboards =
         [createDashModel("dash1"), createDashModel("dash2"), createDashModel("dash3")];
      fixture.detectChanges();

      fixture.whenStable().then(() => {
         // settle the initial NgModel writeValue microtask before interacting,
         // so the click below is measured against a fully steady-state UI
         fixture.detectChanges();

         let enableChk = fixture.nativeElement.querySelectorAll(
            "div.resizable-table-body-container input[type=checkbox]");
         enableChk[0].click();
         fixture.detectChanges();

         fixture.whenStable().then(() => {
            fixture.detectChanges();

            let dashLines = fixture.nativeElement.querySelectorAll(
               "div.resizable-table-body-container tr");
            let checkboxes = fixture.nativeElement.querySelectorAll(
               "div.resizable-table-body-container input[type=checkbox]");

            expect(dashLines[0].querySelectorAll("td")[0].textContent.trim()).toBe("dash2");
            expect(dashLines[1].querySelectorAll("td")[0].textContent.trim()).toBe("dash3");
            expect(dashLines[2].querySelectorAll("td")[0].textContent.trim()).toBe("dash1");

            // dash2 and dash3 were never toggled and must still render checked;
            // only dash1, which was actually disabled, should render unchecked.
            expect(checkboxes[0].checked).toBe(true);
            expect(checkboxes[1].checked).toBe(true);
            expect(checkboxes[2].checked).toBe(false);
         });
      });
   }));
});
