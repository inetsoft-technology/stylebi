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
import { MatButtonModule } from "@angular/material/button";
import { MatCardModule } from "@angular/material/card";
import { MatCheckboxModule } from "@angular/material/checkbox";
import { MatDividerModule } from "@angular/material/divider";
import { MatIconModule } from "@angular/material/icon";
import { MatInputModule } from "@angular/material/input";
import { MatRadioModule } from "@angular/material/radio";
import { MatSelectModule } from "@angular/material/select";
import { MatSnackBarModule } from "@angular/material/snack-bar";
import { MatTableModule } from "@angular/material/table";
import {
   PresentationViewsheetToolbarOptionsViewComponent
} from "./presentation-viewsheet-toolbar-options-view.component";
import { HttpClientTestingModule } from "@angular/common/http/testing";
import { FormsModule, ReactiveFormsModule } from "@angular/forms";
import { NoopAnimationsModule } from "@angular/platform-browser/animations";
import { ToolbarOptionsTableViewComponent } from "../toolbar-options-table-view/toolbar-options-table-view.component";

describe("PresentationViewsheetToolbarOptionsViewComponent", () => {
   let component: PresentationViewsheetToolbarOptionsViewComponent;
   let fixture: ComponentFixture<PresentationViewsheetToolbarOptionsViewComponent>;

   beforeEach(async(() => {
      TestBed.configureTestingModule({
         imports: [
            HttpClientTestingModule,
            FormsModule,
            ReactiveFormsModule,
            NoopAnimationsModule,
            MatButtonModule,
            MatCardModule,
            MatRadioModule,
            MatDividerModule,
            MatSelectModule,
            MatInputModule,
            MatCheckboxModule,
            MatSnackBarModule,
            MatIconModule,
            MatTableModule
         ],
         declarations: [
            PresentationViewsheetToolbarOptionsViewComponent,
            ToolbarOptionsTableViewComponent
         ]
      })
         .compileComponents();
   }));

   beforeEach(() => {
      fixture = TestBed.createComponent(PresentationViewsheetToolbarOptionsViewComponent);
      component = fixture.componentInstance;
      component.model = { options: [
         { id: "Test Button", visible: true, enabled: true }
      ]};
      fixture.detectChanges();
   });

   it("should create", () => {
      expect(component).toBeTruthy();
   });
});
