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
import { async, ComponentFixture, TestBed } from "@angular/core/testing";
import { MatButtonModule } from "@angular/material/button";
import { MatIconModule } from "@angular/material/icon";
import { HttpClientTestingModule } from "@angular/common/http/testing";
import { TextFileContentViewComponent } from "../text-file-content-view/text-file-content-view.component";
import { DataSpaceEditorPageComponent } from "./data-space-editor-page.component";
import { DataSpaceFileSettingsViewComponent } from "../data-space-file-settings-view/data-space-file-settings-view.component";
import { MatInputModule } from "@angular/material/input";
import { FormsModule, ReactiveFormsModule } from "@angular/forms";
import { MatCardModule } from "@angular/material/card";
import { NoopAnimationsModule } from "@angular/platform-browser/animations";
import { NO_ERRORS_SCHEMA } from "@angular/core";
import { DataSpaceFolderSettingsViewComponent } from "../data-space-folder-settings-view/data-space-folder-settings-view.component";

describe("DataSpaceEditorPageComponent", () => {
   let component: DataSpaceEditorPageComponent;
   let fixture: ComponentFixture<DataSpaceEditorPageComponent>;

   beforeEach(async(() => {
      TestBed.configureTestingModule({
         imports: [
            HttpClientTestingModule,
            MatCardModule,
            MatIconModule,
            MatInputModule,
            MatButtonModule,
            FormsModule,
            ReactiveFormsModule,
            NoopAnimationsModule
         ],
         declarations: [DataSpaceEditorPageComponent, DataSpaceFileSettingsViewComponent,
            TextFileContentViewComponent, DataSpaceFolderSettingsViewComponent],
         schemas: [NO_ERRORS_SCHEMA]
      })
         .compileComponents();
   }));

   beforeEach(() => {
      fixture = TestBed.createComponent(DataSpaceEditorPageComponent);
      component = fixture.componentInstance;
      fixture.detectChanges();
   });

   it("should create", () => {
      expect(component).toBeTruthy();
   });
});
