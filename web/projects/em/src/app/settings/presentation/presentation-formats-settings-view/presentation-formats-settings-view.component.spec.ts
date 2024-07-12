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
import { MatCardModule } from "@angular/material/card";
import { MatDividerModule } from "@angular/material/divider";
import { MatFormFieldModule } from "@angular/material/form-field";
import { MatInputModule } from "@angular/material/input";
import { MatSelectModule } from "@angular/material/select";
import { NoopAnimationsModule } from "@angular/platform-browser/animations";
import { PresentationFormatsSettingsViewComponent } from "./presentation-formats-settings-view.component";

describe("PresentationFormatsSettingsViewComponent", () => {
   let component: PresentationFormatsSettingsViewComponent;
   let fixture: ComponentFixture<PresentationFormatsSettingsViewComponent>;

   beforeEach(async(() => {
      TestBed.configureTestingModule({
         imports: [
            NoopAnimationsModule,
            MatCardModule,
            FormsModule,
            ReactiveFormsModule,
            MatFormFieldModule,
            MatInputModule,
            MatSelectModule,
            MatDividerModule
         ],
         declarations: [PresentationFormatsSettingsViewComponent]
      })
         .compileComponents();
   }));

   beforeEach(() => {
      fixture = TestBed.createComponent(PresentationFormatsSettingsViewComponent);
      component = fixture.componentInstance;
      fixture.detectChanges();
   });

   it("should create", () => {
      expect(component).toBeTruthy();
   });
});
