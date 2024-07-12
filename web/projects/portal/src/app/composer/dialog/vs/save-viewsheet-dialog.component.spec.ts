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
import { By } from "@angular/platform-browser";
import { NgbModule } from "@ng-bootstrap/ng-bootstrap";
import { EnterSubmitDirective } from "../../../widget/directive/enter-submit.directive";
import { ModelService } from "../../../widget/services/model.service";
import { SaveViewsheetDialogModel } from "../../data/vs/save-viewsheet-dialog-model";
import { SaveViewsheetDialog } from "./save-viewsheet-dialog.component";

let createModel: () => SaveViewsheetDialogModel = () => {
   return {
      name: "",
      parentId: "",
      viewsheetOptionsPaneModel: null,
      updateDepend: true
   };
};

describe("Save Viewsheet Dialog Unit Test", () => {
   let fixture: ComponentFixture<SaveViewsheetDialog>;
   let saveVSDialog: SaveViewsheetDialog;

   beforeEach(() => {
      const modelService = {
         getModel: jest.fn(),
         sendModel: jest.fn(),
         putModel: jest.fn()
      };
      TestBed.configureTestingModule({
         imports: [
            ReactiveFormsModule,
            FormsModule,
            NgbModule
         ],
         declarations: [
            SaveViewsheetDialog,
            EnterSubmitDirective
         ],
         providers: [
            { provide: ModelService, useValue: modelService }
         ],
         schemas: [
            NO_ERRORS_SCHEMA
         ]
      });
      TestBed.compileComponents();

      fixture = TestBed.createComponent(SaveViewsheetDialog);
      saveVSDialog = <SaveViewsheetDialog>fixture.componentInstance;
      saveVSDialog.model = createModel();
      fixture.detectChanges();
   });

   // Bug #20421 check name for viewsheet
   it("check name for viewsheet", () => {
      let vsName = fixture.debugElement.query(By.css("input#name")).nativeElement;
      vsName.value = "autosize%^&&**((";
      vsName.dispatchEvent(new Event("input"));
      fixture.detectChanges();

      let errors = fixture.debugElement.query(By.css("span.invalid-feedback")).nativeElement;
      expect(errors.textContent).toContain(
         "composer.sheet.checkSpeChar");
   });
});
