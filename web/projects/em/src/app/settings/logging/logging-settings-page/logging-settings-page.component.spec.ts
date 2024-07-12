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
import { HttpClientTestingModule } from "@angular/common/http/testing";
import { async, ComponentFixture, TestBed } from "@angular/core/testing";
import { FormsModule, ReactiveFormsModule } from "@angular/forms";
import { MatButtonModule } from "@angular/material/button";
import { MatCardModule } from "@angular/material/card";
import { MatCheckboxModule } from "@angular/material/checkbox";
import { MatDividerModule } from "@angular/material/divider";
import { MatFormFieldModule } from "@angular/material/form-field";
import { MatInputModule } from "@angular/material/input";
import { MatSelectModule } from "@angular/material/select";
import { NoopAnimationsModule } from "@angular/platform-browser/animations";
import { RouterTestingModule } from "@angular/router/testing";
import { DownloadModule } from "../../../../../../shared/download/download.module";
import { EditorPanelModule } from "../../../common/util/editor-panel/editor-panel.module";
import { TableViewModule } from "../../../common/util/table/table-view.module";
import { LoggingLevelTableComponent } from "../logging-level-table/logging-level-table.component";
import { LoggingSettingsViewComponent } from "../logging-settings-view/logging-settings-view.component";
import { LoggingSettingsPageComponent } from "./logging-settings-page.component";
import { AppInfoService } from "../../../../../../shared/util/app-info.service";

describe("LoggingSettingsPageComponent", () => {
   let component: LoggingSettingsPageComponent;
   let fixture: ComponentFixture<LoggingSettingsPageComponent>;

   beforeEach(async(() => {
      TestBed.configureTestingModule({
         declarations: [
            LoggingSettingsPageComponent,
            LoggingSettingsViewComponent,
            LoggingLevelTableComponent
         ],
         imports: [
            HttpClientTestingModule,
            FormsModule,
            ReactiveFormsModule,
            RouterTestingModule,
            MatButtonModule,
            MatCardModule,
            MatCheckboxModule,
            MatDividerModule,
            MatFormFieldModule,
            MatInputModule,
            MatSelectModule,
            NoopAnimationsModule,
            DownloadModule,
            EditorPanelModule,
            TableViewModule
         ],
         providers: [
            AppInfoService
         ]
      })
      .compileComponents();
   }));

   beforeEach(() => {
      fixture = TestBed.createComponent(LoggingSettingsPageComponent);
      component = fixture.componentInstance;
      fixture.detectChanges();
   });

   it("should create", () => {
      expect(component).toBeTruthy();
   });
});
