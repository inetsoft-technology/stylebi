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
import { NO_ERRORS_SCHEMA } from "@angular/core";
import { ComponentFixture, TestBed } from "@angular/core/testing";
import { FormsModule, ReactiveFormsModule } from "@angular/forms";
import { MatCheckboxModule } from "@angular/material/checkbox";
import { MatOptionModule } from "@angular/material/core";
import { MatInputModule } from "@angular/material/input";
import { MatSelectModule } from "@angular/material/select";
import { RouterTestingModule } from "@angular/router/testing";
import { of as observableOf } from "rxjs";
import { ContentRepositoryService } from "../content-repository-page/content-repository.service";
import { RepositoryDataSourceSettingsPageComponent } from "../repository-data-source-settings-page/repository-data-source-settings-page.component";
import { RepositoryFolderSettingsPageComponent } from "../repository-folder-settings-page/repository-folder-settings-page.component";
import { RepositoryFolderTrashcanSettingsPageComponent } from "../repository-folder-trashcan-settings-page/repository-folder-trashcan-settings-page.component";
import { RepositoryPermissionEditorPageComponent } from "../repository-permission-editor-page/repository-permission-editor-page.component";
import { RepositoryViewsheetSettingsPageComponent } from "../repository-viewsheet-settings-page/repository-viewsheet-settings-page.component";
import { RepositoryWorksheetSettingsPageComponent } from "../repository-worksheet-settings-page/repository-worksheet-settings-page.component";
import { RepositoryEditorPageComponent } from "./repository-editor-page.component";

describe("RepositoryEditorPageComponent", () => {
   let component: RepositoryEditorPageComponent;
   let fixture: ComponentFixture<RepositoryEditorPageComponent>;

   beforeEach(() => {
      const service = {
         hasMVPermission: jest.fn(() => observableOf(false))
      };

      TestBed.configureTestingModule({
         imports: [
            RouterTestingModule, FormsModule, ReactiveFormsModule, MatCheckboxModule,
            MatSelectModule, MatOptionModule, MatInputModule, HttpClientTestingModule
         ],
         declarations: [
            RepositoryEditorPageComponent,
            RepositoryViewsheetSettingsPageComponent,
            RepositoryWorksheetSettingsPageComponent,
            RepositoryDataSourceSettingsPageComponent,
            RepositoryFolderTrashcanSettingsPageComponent,
            RepositoryFolderSettingsPageComponent,
            RepositoryPermissionEditorPageComponent
         ],
         providers: [{provide: ContentRepositoryService, useValue: service}],
         schemas: [
            NO_ERRORS_SCHEMA
         ],
      })
         .compileComponents();

      fixture = TestBed.createComponent(RepositoryEditorPageComponent);
      component = fixture.componentInstance;
      fixture.detectChanges();
   });

   it("should create", () => {
      expect(component).toBeTruthy();
   });
});
