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
import { HttpClientTestingModule } from "@angular/common/http/testing";
import { ComponentFixture, TestBed } from "@angular/core/testing";
import { FormsModule, ReactiveFormsModule } from "@angular/forms";
import { MatButtonModule } from "@angular/material/button";
import { MatCardModule } from "@angular/material/card";
import { MatCheckboxModule } from "@angular/material/checkbox";
import { MatOptionModule } from "@angular/material/core";
import { MatFormFieldModule } from "@angular/material/form-field";
import { MatInputModule } from "@angular/material/input";
import { MatRadioModule } from "@angular/material/radio";
import { MatSelectModule } from "@angular/material/select";
import { MatTabsModule } from "@angular/material/tabs";
import { NoopAnimationsModule } from "@angular/platform-browser/animations";
import { RouterTestingModule } from "@angular/router/testing";
import { RepositorySheetSettingsViewComponent } from "./repository-sheet-settings-view.component";

describe("RepositorySheetSettingsViewComponent", () => {
   let component: RepositorySheetSettingsViewComponent;
   let fixture: ComponentFixture<RepositorySheetSettingsViewComponent>;

   beforeEach(() => {
      TestBed.configureTestingModule({
         imports: [HttpClientTestingModule, RouterTestingModule, MatCardModule,
            MatButtonModule, MatInputModule, MatSelectModule, NoopAnimationsModule,
            MatOptionModule, MatTabsModule, MatFormFieldModule, FormsModule, ReactiveFormsModule,
            MatRadioModule, MatCheckboxModule],
         declarations: [RepositorySheetSettingsViewComponent]
      })
         .compileComponents();

      fixture = TestBed.createComponent(RepositorySheetSettingsViewComponent);
      component = fixture.componentInstance;
      fixture.detectChanges();
   });

   it("should create", () => {
      expect(component).toBeTruthy();
   });
});
