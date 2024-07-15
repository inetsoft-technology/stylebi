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
import { async, ComponentFixture, TestBed } from "@angular/core/testing";
import { FormsModule, ReactiveFormsModule } from "@angular/forms";
import { MatCardModule } from "@angular/material/card";
import { MatCheckboxModule } from "@angular/material/checkbox";
import { MatDialogModule } from "@angular/material/dialog";
import { MatDividerModule } from "@angular/material/divider";
import { MatFormFieldModule } from "@angular/material/form-field";
import { MatIconModule } from "@angular/material/icon";
import { MatInputModule } from "@angular/material/input";
import { MatSelectModule } from "@angular/material/select";
import { NoopAnimationsModule } from "@angular/platform-browser/animations";
import { AddDataSourceTypeDialogComponent } from "./add-data-source-type-dialog/add-data-source-type-dialog.component";
import { PresentationDataSourceVisibilitySettingsModel } from "./presentation-data-source-visibility-settings-model";
import { PresentationDataSourceVisibilitySettingsViewComponent } from "./presentation-data-source-visibility-settings-view.component";

describe("PresentationDataSourceVisibilitySettingsViewComponent", () => {
   let component: PresentationDataSourceVisibilitySettingsViewComponent;
   let fixture: ComponentFixture<PresentationDataSourceVisibilitySettingsViewComponent>;

   beforeEach(async(() => {
      TestBed.configureTestingModule({
         imports: [
            NoopAnimationsModule,
            MatCardModule,
            FormsModule,
            ReactiveFormsModule,
            MatFormFieldModule,
            MatIconModule,
            MatInputModule,
            MatSelectModule,
            MatDividerModule,
            MatCheckboxModule,
            MatDialogModule
         ],
         declarations: [
            PresentationDataSourceVisibilitySettingsViewComponent,
            AddDataSourceTypeDialogComponent
         ]
      })
         .compileComponents();
   }));

   beforeEach(() => {
      fixture = TestBed.createComponent(PresentationDataSourceVisibilitySettingsViewComponent);
      component = fixture.componentInstance;
      component.model = <PresentationDataSourceVisibilitySettingsModel> {
         visibleDataSources: [],
         hiddenDataSources: [],
         dataSourceListings: []
      };
      fixture.detectChanges();
   });

   it("should create", () => {
      expect(component).toBeTruthy();
   });
});
