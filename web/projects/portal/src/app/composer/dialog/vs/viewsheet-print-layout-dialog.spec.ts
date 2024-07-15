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
import { By } from "@angular/platform-browser";
import { NgbDropdownModule } from "@ng-bootstrap/ng-bootstrap";
import { EnterSubmitDirective } from "../../../widget/directive/enter-submit.directive";
import { ModalHeaderComponent } from "../../../widget/modal-header/modal-header.component";
import { DialogButtonsDirective } from "../../../widget/standard-dialog/dialog-buttons.directive";
import { DialogContentDirective } from "../../../widget/standard-dialog/dialog-content.directive";
import { StandardDialogComponent } from "../../../widget/standard-dialog/standard-dialog.component";
import { ViewsheetPrintLayoutDialogModel } from "../../data/vs/viewsheet-print-layout-dialog-model";
import { ViewsheetPrintLayoutDialog } from "./viewsheet-print-layout-dialog.component";


let createModel: () => ViewsheetPrintLayoutDialogModel = () => {
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

describe("Viewsheet print layout dialog Test", () => {
   let fixture: ComponentFixture<ViewsheetPrintLayoutDialog>;
   let printDialog: ViewsheetPrintLayoutDialog;
   let topInput;
   let bottomInput;
   let leftInput;
   let rightInput;
   let headerInput;
   let footerInput: HTMLInputElement;

   beforeEach(async(() => {
      TestBed.configureTestingModule({
         imports: [
            ReactiveFormsModule,
            FormsModule,
            NgbDropdownModule,
         ],
         declarations: [
            ViewsheetPrintLayoutDialog, EnterSubmitDirective, StandardDialogComponent,
            DialogContentDirective, DialogButtonsDirective, ModalHeaderComponent
         ],
         schemas: [NO_ERRORS_SCHEMA]
      });
      TestBed.compileComponents();

      fixture = TestBed.createComponent(ViewsheetPrintLayoutDialog);
      printDialog = <ViewsheetPrintLayoutDialog> fixture.componentInstance;
      printDialog.model = createModel();
      fixture.detectChanges();
   }));

   // Bug #16525 Margin and From edge input check
   // Bug #16520 Margin should not be larger than paper size
   it("Margin and From edge input check", () => {
      topInput = fixture.debugElement.query(By.css("input[formcontrolname=marginTop]")).nativeElement;
      leftInput = fixture.debugElement.query(By.css("input[formcontrolname=marginLeft]")).nativeElement;
      bottomInput = fixture.debugElement.query(By.css("input[formcontrolname=marginBottom]")).nativeElement;
      rightInput = fixture.debugElement.query(By.css("input[formcontrolname=marginRight]")).nativeElement;
      headerInput = fixture.debugElement.query(By.css("input[formcontrolname=headerFromEdge]")).nativeElement;
      footerInput = fixture.debugElement.query(By.css("input[formcontrolname=footerFromEdge]")).nativeElement;

      printDialog.formPrint.get("marginTop").setValue(-12);
      fixture.detectChanges();
      let marginWarning = fixture.debugElement.query(By.css(".is-invalid ~ span.invalid-feedback")).nativeElement;
      expect(marginWarning.textContent).toContain("_#(viewer.viewsheet.layout.pageProp.marginValid)");

      printDialog.formPrint.get("marginTop").setValue(0);
      printDialog.formPrint.get("marginBottom").setValue(-12);
      fixture.detectChanges();
      marginWarning = fixture.debugElement.query(By.css(".is-invalid ~ span.invalid-feedback")).nativeElement;
      expect(marginWarning.textContent).toContain("_#(viewer.viewsheet.layout.pageProp.marginValid)");

      printDialog.formPrint.get("marginBottom").setValue(0);
      printDialog.formPrint.get("marginLeft").setValue(-12);
      fixture.detectChanges();
      marginWarning = fixture.debugElement.query(By.css(".is-invalid ~ span.invalid-feedback")).nativeElement;
      expect(marginWarning.textContent).toContain("_#(viewer.viewsheet.layout.pageProp.marginValid)");

      printDialog.formPrint.get("marginLeft").setValue(0);
      printDialog.formPrint.get("marginRight").setValue(-12);
      fixture.detectChanges();
      marginWarning = fixture.debugElement.query(By.css(".is-invalid ~ span.invalid-feedback")).nativeElement;
      expect(marginWarning.textContent).toContain("_#(viewer.viewsheet.layout.pageProp.marginValid)");

      printDialog.formPrint.get("marginRight").setValue(0);
      printDialog.formPrint.get("marginTop").setValue(6);
      printDialog.formPrint.get("marginBottom").setValue(7);
      fixture.detectChanges();
      let marginSizeWarning = fixture.debugElement.query(By.css(".is-invalid ~ span.invalid-feedback")).nativeElement;
      expect(marginSizeWarning.textContent).toContain("_#(viewer.viewsheet.layout.pageProp.marginTooLarge)");

      printDialog.formPrint.get("marginTop").setValue(0);
      printDialog.formPrint.get("marginBottom").setValue(0);
      printDialog.formPrint.get("marginLeft").setValue(6);
      printDialog.formPrint.get("marginRight").setValue(7);
      fixture.detectChanges();
      marginSizeWarning = fixture.debugElement.query(By.css(".is-invalid ~ span.invalid-feedback")).nativeElement;
      expect(marginSizeWarning.textContent).toContain("_#(viewer.viewsheet.layout.pageProp.marginTooLarge)");

      printDialog.formPrint.get("marginLeft").setValue(0);
      printDialog.formPrint.get("marginRight").setValue(0);
      printDialog.formPrint.get("headerFromEdge").setValue(-12);
      fixture.detectChanges();
      let edgeWarning = fixture.debugElement.query(By.css(".is-invalid ~ span.invalid-feedback")).nativeElement;
      expect(edgeWarning.textContent).toContain("_#(viewer.viewsheet.layout.pageProp.distanceValid)");

      printDialog.formPrint.get("headerFromEdge").setValue(0);
      printDialog.formPrint.get("footerFromEdge").setValue(-12);
      fixture.detectChanges();
      edgeWarning = fixture.debugElement.query(By.css(".is-invalid ~ span.invalid-feedback")).nativeElement;
      expect(edgeWarning.textContent).toContain("_#(viewer.viewsheet.layout.pageProp.distanceValid)");
      printDialog.formPrint.get("footerFromEdge").setValue(0);

      //bug #18433, Header/Footer exceed top/bottom value
      topInput.value = "1";
      topInput.dispatchEvent(new Event("input"));
      bottomInput.value = "1";
      bottomInput.dispatchEvent(new Event("input"));
      headerInput.value = "1.1";
      headerInput.dispatchEvent(new Event("input"));
      fixture.detectChanges();
      let headerWarning = fixture.debugElement.query(By.css(".is-invalid ~ span.invalid-feedback")).nativeElement;
      expect(headerWarning.textContent).toContain("_#(designer.pageProp.headerValExceed)");

      headerInput.value = "0.5";
      headerInput.dispatchEvent(new Event("input"));
      footerInput.value = "1.1";
      footerInput.dispatchEvent(new Event("input"));
      fixture.detectChanges();
      let footerWarning = fixture.debugElement.query(By.css(".is-invalid ~ span.invalid-feedback")).nativeElement;
      expect(footerWarning.textContent).toContain("_#(designer.pageProp.footerValExceed)");
   });

   //Bug #18429, check custom size
   //Bug #18793 change width and height when units changed
   xit("check custom size", () => { // broken test
      printDialog.model.paperSize = "(Custom Size)";
      fixture.detectChanges();

      let customeWidth = fixture.debugElement.query(By.css("input[ng-reflect-name=customWidth]")).nativeElement;
      let customHeight = fixture.debugElement.query(By.css("input[ng-reflect-name=customHeight]")).nativeElement;
      customeWidth.value = "5";
      customeWidth.dispatchEvent(new Event("input"));
      customHeight.value = "11";
      customHeight.dispatchEvent(new Event("input"));
      fixture.detectChanges();

      let warning = fixture.debugElement.query(By.css("div.alert.alert-danger")).nativeElement;
      expect(warning).toBeNull();

      let unit = fixture.debugElement.query(By.css(".unit_select_id")).nativeElement;
      customeWidth.value = "1";
      customeWidth.dispatchEvent(new Event("input"));
      customHeight.value = "1";
      customHeight.dispatchEvent(new Event("input"));
      unit.value = "mm";
      unit.dispatchEvent(new Event("change"));
      fixture.detectChanges();

      customeWidth = fixture.debugElement.query(By.css("input[ng-reflect-name=customWidth]")).nativeElement;
      customHeight = fixture.debugElement.query(By.css("input[ng-reflect-name=customHeight]")).nativeElement;
      expect(customHeight.value).toBe("25");
      expect(customeWidth.value).toBe("25");

      unit.value = "points";
      unit.dispatchEvent(new Event("change"));
      fixture.detectChanges();

      customeWidth = fixture.debugElement.query(By.css("input[ng-reflect-name=customWidth]")).nativeElement;
      customHeight = fixture.debugElement.query(By.css("input[ng-reflect-name=customHeight]")).nativeElement;
      expect(customHeight.value).toBe("71");
      expect(customeWidth.value).toBe("71");
   });

   //Bug #19315 start page check
   it("check start page", () => {
      let startPage = fixture.debugElement.query(By.css("input[ng-reflect-name=numberingStart]")).nativeElement;
      startPage.value = "1.2";
      startPage.dispatchEvent(new Event("input"));
      fixture.detectChanges();

      let warning = fixture.debugElement.query(By.css("span.invalid-feedback")).nativeElement;
      expect(warning.textContent).toContain("_#(viewer.viewsheet.layout.pageProp.startPageIndexValid)");
   });

   //Bug #19391 from edge and margin check
   xit("from edge and margin check for A2", () => { // broken test
      printDialog.model.paperSize = "A2 [420x594 mm]";
      fixture.detectChanges();
      topInput = fixture.debugElement.query(By.css("input[formcontrolname=marginTop]")).nativeElement;
      bottomInput = fixture.debugElement.query(By.css("input[formcontrolname=marginBottom]")).nativeElement;
      headerInput = fixture.debugElement.query(By.css("input[formcontrolname=headerFromEdge]")).nativeElement;
      footerInput = fixture.debugElement.query(By.css("input[formcontrolname=footerFromEdge]")).nativeElement;

      topInput.value = "5";
      topInput.dispatchEvent(new Event("input"));
      bottomInput.value = "5";
      bottomInput.dispatchEvent(new Event("input"));
      headerInput.value = "2";
      headerInput.dispatchEvent(new Event("input"));
      footerInput.value = "2";
      footerInput.dispatchEvent(new Event("input"));
      fixture.detectChanges();
      let warning = fixture.debugElement.query(By.css(".is-invalid ~ span.invalid-feedback")).nativeElement;
      expect(warning).toBeNull();
   });

   //Bug #19460 check warning times
   it("check warning times", () => {
      topInput = fixture.debugElement.query(By.css("input[formcontrolname=marginTop]")).nativeElement;

      topInput.value = "11";
      topInput.dispatchEvent(new Event("input"));
      fixture.detectChanges();
      let warnings = fixture.debugElement.queryAll(By.css(".is-invalid ~ span.invalid-feedback"));
      expect(warnings.length).toBe(2);
      expect(warnings[0].nativeElement.textContent).toContain("_#(viewer.viewsheet.layout.pageProp.marginTooLarge)");
      expect(warnings[1].nativeElement.textContent).toContain("_#(viewer.viewsheet.layout.pageProp.marginTooLarge)");
   });
});
