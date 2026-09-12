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

import { HttpClientTestingModule, HttpTestingController } from "@angular/common/http/testing";
import { ComponentFixture, TestBed, waitForAsync } from "@angular/core/testing";
import { EMPTY } from "rxjs";
import { FormsModule, ReactiveFormsModule } from "@angular/forms";
import { MatButtonModule } from "@angular/material/button";
import { MatCardModule } from "@angular/material/card";
import { MatCheckboxModule } from "@angular/material/checkbox";
import { MatDialogModule } from "@angular/material/dialog";
import { MatDividerModule } from "@angular/material/divider";
import { MatFormFieldModule } from "@angular/material/form-field";
import { MatInputModule } from "@angular/material/input";
import { MatSelectModule } from "@angular/material/select";
import { NoopAnimationsModule } from "@angular/platform-browser/animations";
import { RouterModule } from "@angular/router";
import { DownloadTargetComponent } from "../../../../../../shared/download/download-target.component";
import { AppInfoService } from "../../../../../../shared/util/app-info.service";
import { EditorPanelComponent } from "../../../common/util/editor-panel/editor-panel.component";
import { TableView } from "../../../common/util/table/table-view.component";
import { LoggingLevelTableComponent } from "../logging-level-table/logging-level-table.component";
import { LoggingSettingsViewComponent } from "../logging-settings-view/logging-settings-view.component";
import { ErrorHandlerService } from "../../../common/util/error/error-handler.service";
import { LoggingSettingsPageComponent } from "./logging-settings-page.component";

describe("LoggingSettingsPageComponent", () => {
   let component: LoggingSettingsPageComponent;
   let fixture: ComponentFixture<LoggingSettingsPageComponent>;
   let httpTesting: HttpTestingController;

   beforeEach(waitForAsync(() => {
      TestBed.configureTestingModule({
         imports: [
            LoggingSettingsPageComponent, LoggingSettingsViewComponent, LoggingLevelTableComponent,
            HttpClientTestingModule,
            FormsModule,
            ReactiveFormsModule,
            RouterModule.forRoot([]),
            MatButtonModule,
            MatCardModule,
            MatCheckboxModule,
            MatDialogModule,
            MatDividerModule,
            MatFormFieldModule,
            MatInputModule,
            MatSelectModule,
            NoopAnimationsModule,
            DownloadTargetComponent,
            EditorPanelComponent,
            TableView
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
      httpTesting = TestBed.inject(HttpTestingController);
      fixture.detectChanges();
   });

   it("should create", () => {
      expect(component).toBeTruthy();
   });

   // The server refuses log.provider=fluentd on a build that cannot forward (Redmine #76045).
   // The save subscribe had no error callback, so the rejection was swallowed and the page
   // simply failed to update -- the same silent failure this change exists to end.
   it("should show the server's message when the save is rejected", () => {
      const errorService = TestBed.inject(ErrorHandlerService);
      const showDialog = vi.spyOn(errorService, "showDialog").mockReturnValue(EMPTY);
      const rejected = <any>{ provider: "fluentd" };

      component.model = <any>{ provider: "file" };
      component.newModel = rejected;
      component.valid = true;
      component.setConfiguration();

      httpTesting.expectOne("../api/em/log/setting/set-configuration")
         .flush({ message: "only available in the Enterprise edition" },
                { status: 500, statusText: "Internal Server Error" });

      expect(showDialog).toHaveBeenCalledTimes(1);
      // the rejected model must not be adopted as the saved state
      expect(component.model).not.toBe(rejected);
      expect(component.valid).toBe(true);
   });

   it("should adopt the model when the save succeeds", () => {
      const newModel = <any>{ provider: "file" };
      component.newModel = newModel;
      component.valid = true;
      component.setConfiguration();

      httpTesting.expectOne("../api/em/log/setting/set-configuration").flush({});

      expect(component.model).toBe(newModel);
      expect(component.valid).toBe(false);
   });
});
