/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
import { NO_ERRORS_SCHEMA } from "@angular/core";
import { ComponentFixture, TestBed } from "@angular/core/testing";
import { FormsModule, ReactiveFormsModule } from "@angular/forms";
import { MatCardModule } from "@angular/material/card";
import { MatCheckboxModule } from "@angular/material/checkbox";
import { MatOptionModule } from "@angular/material/core";
import { MatFormFieldModule } from "@angular/material/form-field";
import { MatInputModule } from "@angular/material/input";
import { MatSelectModule } from "@angular/material/select";
import { MatSnackBarModule } from "@angular/material/snack-bar";
import { NoopAnimationsModule } from "@angular/platform-browser/animations";
import { FileChooserModule } from "../../../../common/util/file-chooser/file-chooser.module";
import { RepositoryDataSourceSettingsViewComponent } from "./repository-data-source-settings-view.component";

describe("RepositoryDataSourceSettingsViewComponent", () => {
   let component: RepositoryDataSourceSettingsViewComponent;
   let fixture: ComponentFixture<RepositoryDataSourceSettingsViewComponent>;

   beforeEach(() => {
      TestBed.configureTestingModule({
         imports: [
            FormsModule,
            ReactiveFormsModule,
            NoopAnimationsModule,
            MatCardModule,
            MatCheckboxModule,
            MatFormFieldModule,
            MatInputModule,
            MatOptionModule,
            MatSelectModule,
            MatSnackBarModule,
            FileChooserModule
         ],
         declarations: [
            RepositoryDataSourceSettingsViewComponent
         ],
         schemas: [
            NO_ERRORS_SCHEMA
         ]
      })
      .compileComponents();

      fixture = TestBed.createComponent(RepositoryDataSourceSettingsViewComponent);
      component = fixture.componentInstance;
      fixture.detectChanges();
   });

   it("should create", () => {
      expect(component).toBeTruthy();
   });
});
