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
import { ComponentFixture, TestBed } from "@angular/core/testing";
import { NgbModule } from "@ng-bootstrap/ng-bootstrap";
import { FormsModule, ReactiveFormsModule } from "@angular/forms";
import { TableUnpivotDialog } from "./table-unpivot-dialog.component";
import { EnterSubmitDirective } from "../../../widget/directive/enter-submit.directive";
import { ModalHeaderComponent } from "../../../widget/modal-header/modal-header.component";

describe("Table Unpivot Dialog Tests", () => {
   let fixture: ComponentFixture<TableUnpivotDialog>;

   beforeEach(() => {
      TestBed.configureTestingModule({
         imports: [
            FormsModule, ReactiveFormsModule, NgbModule
         ],
         declarations: [
            TableUnpivotDialog, EnterSubmitDirective, ModalHeaderComponent
         ],
         schemas: [NO_ERRORS_SCHEMA]
      });

      fixture = TestBed.createComponent(TableUnpivotDialog);
   });

   it("check that dialog was properly populated", () => {
      fixture.detectChanges();
      let input = fixture.nativeElement.querySelector("input[formcontrolname='level']");
      expect(input).toBeTruthy();
   });
});