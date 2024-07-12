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
import { ComponentFixture, TestBed, async } from "@angular/core/testing";
import { FormsModule, ReactiveFormsModule } from "@angular/forms";
import { NgbModule } from "@ng-bootstrap/ng-bootstrap";
import { ManualOrderingDialog } from "./manual-ordering-dialog.component";
import { LargeFormFieldComponent } from "../../widget/large-form-field/large-form-field.component";
import { By } from "@angular/platform-browser";

describe("manual ordering dialog Unit case: ", () => {
   let fixture: ComponentFixture<ManualOrderingDialog>;
   let manualOrderDialog: ManualOrderingDialog;

   beforeEach(async(() => {
      TestBed.configureTestingModule({
         imports: [ReactiveFormsModule, FormsModule, NgbModule],
         declarations: [ManualOrderingDialog, LargeFormFieldComponent],
         schemas: [NO_ERRORS_SCHEMA]
      }).compileComponents();

      fixture = TestBed.createComponent(ManualOrderingDialog);
      manualOrderDialog = <ManualOrderingDialog>fixture.componentInstance;
   }));

   //Bug #10872 and Bug #10874, and Bug #10473
   it("check manual order execute", (done) => {
      manualOrderDialog.manualOrders = ["A", "B", "C", "D"];
      manualOrderDialog.valueLabelList = [
         {value: "A", label: "A"},
         {value: "B", label: "B"},
         {value: "C", label: "C"},
         {value: "D", label: "D"}];
      manualOrderDialog.ngOnChanges();

      fixture.detectChanges();
      let lists = fixture.nativeElement.querySelectorAll("div.manual-order-list li");
      let upBtn = fixture.debugElement.query(By.css(".manual-ordering-up-btn")).nativeElement;
      let downNtn = fixture.debugElement.query(By.css(".manual-ordering-down-btn")).nativeElement;
      let okBtn = fixture.debugElement.query(By.css("button[type=submit]")).nativeElement;

      expect(manualOrderDialog.selIndex).toEqual(3);

      lists[0].click();
      fixture.detectChanges();
      expect(upBtn.disabled).toBeTruthy();

      lists[3].click();
      fixture.detectChanges();
      expect(downNtn.disabled).toBeTruthy();

      upBtn.click();
      manualOrderDialog.onCommit.subscribe((orders: string[]) => {
         expect(orders).toEqual(["A", "B", "D", "C"]);

         done();
      });
      okBtn.click();
   });

});
