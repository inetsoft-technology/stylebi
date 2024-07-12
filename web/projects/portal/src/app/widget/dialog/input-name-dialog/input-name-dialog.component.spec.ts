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
import { async, ComponentFixture, TestBed } from "@angular/core/testing";
import { FormsModule, ReactiveFormsModule } from "@angular/forms";
import { By } from "@angular/platform-browser";
import { NgbModal, NgbModule } from "@ng-bootstrap/ng-bootstrap";
import { of } from "rxjs";
import { EnterSubmitDirective } from "../../directive/enter-submit.directive";
import { HelpUrlService } from "../../help-link/help-url.service";
import { InputNameDialog } from "./input-name-dialog.component";
import { ModalHeaderComponent } from "../../modal-header/modal-header.component";
import { HelpLinkDirective } from "../../help-link/help-link.directive";

describe("Input Name Dialog Unit Test", () => {
   let fixture: ComponentFixture<InputNameDialog>;
   let inputNameDialog: InputNameDialog;
   let ngbService = { open: jest.fn() };
   let helpUrlService = {
      getHelpUrl: jest.fn(() => of("about:blank")),
      getScriptHelpUrl: jest.fn(() => of("about:blank"))
   };

   beforeEach(async(() => {
      TestBed.configureTestingModule({
         imports: [
            FormsModule, ReactiveFormsModule, NgbModule
         ],
         declarations: [
            InputNameDialog,
            EnterSubmitDirective,
            ModalHeaderComponent,
            HelpLinkDirective
         ],
         providers: [
            { provide: NgbModal, useValue: ngbService },
            { provide: HelpUrlService, useValue: helpUrlService }
         ]
      });
      TestBed.compileComponents();

      fixture = TestBed.createComponent(InputNameDialog);
      inputNameDialog = <InputNameDialog>fixture.componentInstance;
      fixture.detectChanges();
   }));

   //Bug #19762 Show the error message on the input
   it("Show the error message on the input", () => {
      let name = fixture.debugElement.query(By.css("input#inputNameField")).nativeElement;
      name.value = "";
      name.dispatchEvent(new Event("input"));
      fixture.detectChanges();

      let warning = fixture.debugElement.query(By.css(".invalid-feedback")).nativeElement;
      let warningText = warning.textContent;
      expect(warningText).toBeTruthy();
      warningText = warningText.replace(/^\s+/, "").replace(/\s+$/, "");
      expect(warningText).toBe("Name is required");
   });
});
