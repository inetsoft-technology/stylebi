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
import { FormsModule, ReactiveFormsModule} from "@angular/forms";
import { NgbModule, NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { EnterSubmitDirective } from "../../widget/directive/enter-submit.directive";
import { HideColumnsDialogModel } from "../model/hide-columns-dialog-model";
import { HideColumnsDialog } from "./hide-columns-dialog.component";
import { ApplyButtonComponent } from "../../widget/slide-out/apply-button.component";

let createModel: () => HideColumnsDialogModel = () => {
   return {
      availableColumns: [],
      hiddenColumns: []
   };
};

let hideColumnsDialog: HideColumnsDialog;

describe("Hide Columns Dialog Unit Test", () => {
   beforeEach(() => {
      TestBed.configureTestingModule({
         imports: [
            FormsModule, ReactiveFormsModule, NgbModule
         ],
         declarations: [
            ApplyButtonComponent, HideColumnsDialog, EnterSubmitDirective
         ],
         providers: [
            NgbModal
         ],
         schemas: [NO_ERRORS_SCHEMA]
      });
      TestBed.compileComponents();
   });

   //add unit case for Bug #10397
   it("should display right button status", () => {
      let fixture: ComponentFixture<HideColumnsDialog> =
         TestBed.createComponent(HideColumnsDialog);
      hideColumnsDialog = <HideColumnsDialog>fixture.componentInstance;
      hideColumnsDialog.model = createModel();
      hideColumnsDialog.model.availableColumns = ["state", "city", "orderdate"];
      hideColumnsDialog.model.hiddenColumns = ["reseller", "instock"];
      let element = fixture.nativeElement;
      fixture.detectChanges();

      let buttons: any = element.querySelectorAll("button.btn-wide");
      let addButton: any = buttons[0];
      let removeButton: any = buttons[1];
      let avaliableSelects: any = element.querySelectorAll(
         "div.selectable-list")[0].querySelectorAll("div[style='display: block;']");
      let hidetableSelects: any = element.querySelectorAll(
         "div.selectable-list")[1].querySelectorAll("div.unhighlightable");

      expect(addButton.disabled).toBe(true);
      expect(removeButton.disabled).toBe(true);

      avaliableSelects[0].click();
      fixture.detectChanges();
      buttons = element.querySelectorAll("button.btn-wide");
      expect(buttons[0].disabled).toBe(false);
      expect(buttons[1].disabled).toBe(true);

      hidetableSelects[0].click();
      fixture.detectChanges();
      buttons = element.querySelectorAll("button.btn-wide");
      expect(buttons[0].disabled).toBe(false);
      expect(buttons[1].disabled).toBe(false);

      buttons[0].click();
      fixture.detectChanges();
      buttons = element.querySelectorAll("button.btn-wide");
      expect(buttons[0].disabled).toBe(true);
      expect(buttons[1].disabled).toBe(false);
   });

   //Bug #18177, column sort in Available Columns pane
   //Bug #18590 auto focus to the next item
   // bad test, use debug element or snapshot to test dom if necessary
   // it("column sort in Available Columns pane", () => {
   //    let fixture: ComponentFixture<HideColumnsDialog> =
   //       TestBed.createComponent(HideColumnsDialog);
   //    hideColumnsDialog = <HideColumnsDialog>fixture.componentInstance;
   //    hideColumnsDialog.model = createModel();
   //    hideColumnsDialog.model.availableColumns = ["address", "city", "reseller", "state"];
   //    let element = fixture.nativeElement;
   //    fixture.detectChanges();
   //
   //    let avaliableSelects: any = element.querySelectorAll(
   //       "div.selectable-list")[0].querySelectorAll("div[style='display: block;']");
   //    avaliableSelects[0].click();
   //    fixture.detectChanges();
   //    let addButton = element.querySelectorAll("button.btn-wide")[0];
   //    addButton.click();
   //    fixture.detectChanges();
   //    avaliableSelects = element.querySelectorAll(
   //       "div.selectable-list")[0].querySelectorAll("div[style='display: block;']");
   //    let focusedItem = element.querySelectorAll("div.selectable-list")[0].querySelector(
   //       "div.selected");
   //    expect(avaliableSelects[0].textContent.trim()).toBe("city");
   //    expect(avaliableSelects[1].textContent.trim()).toBe("reseller");
   //    expect(avaliableSelects[2].textContent.trim()).toBe("state");
   //    expect(focusedItem.textContent.trim()).toBe("city");
   //
   //    let hidetableSelects: any = element.querySelectorAll(
   //       "div.selectable-list")[1].querySelectorAll("div.unhighlightable");
   //    hidetableSelects[0].click();
   //    fixture.detectChanges();
   //    let removeButton = element.querySelectorAll("button.btn-wide")[1];
   //    removeButton.click();
   //    fixture.detectChanges();
   //    avaliableSelects = element.querySelectorAll(
   //       "div.selectable-list")[0].querySelectorAll("div[style='display: block;']");
   //    expect(avaliableSelects[0].textContent.trim()).toBe("address");
   //    expect(avaliableSelects[1].textContent.trim()).toBe("city");
   //    expect(avaliableSelects[2].textContent.trim()).toBe("reseller");
   //    expect(avaliableSelects[3].textContent.trim()).toBe("state");
   // });
});