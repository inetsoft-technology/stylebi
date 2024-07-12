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
import { HttpClientModule } from "@angular/common/http";
import { NO_ERRORS_SCHEMA } from "@angular/core";
import { async, ComponentFixture, TestBed } from "@angular/core/testing";
import { ModelService } from "../services/model.service";
import { DialogButtonsDirective } from "../standard-dialog/dialog-buttons.directive";
import { DialogContentDirective } from "../standard-dialog/dialog-content.directive";
import { StandardDialogComponent } from "../standard-dialog/standard-dialog.component";

import { ConsoleDialogComponent } from "./console-dialog.component";

describe("ConsoleDialogComponent", () => {
   let component: ConsoleDialogComponent;
   let fixture: ComponentFixture<ConsoleDialogComponent>;

   beforeEach(async(() => {
      TestBed.configureTestingModule({
         imports: [
            HttpClientModule
         ],
         declarations: [ConsoleDialogComponent, StandardDialogComponent,
            DialogContentDirective, DialogButtonsDirective],
         providers: [
            {
               provide: ModelService
            }
         ],
         schemas: [NO_ERRORS_SCHEMA]
      })
         .compileComponents();
   }));

   beforeEach(() => {
      fixture = TestBed.createComponent(ConsoleDialogComponent);
      component = fixture.componentInstance;
      fixture.detectChanges();
   });

   it("should create", () => {
      expect(component).toBeTruthy();
   });
});
