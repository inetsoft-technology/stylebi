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
import { MatCardModule } from "@angular/material/card";
import { MatDialogModule } from "@angular/material/dialog";
import { MatDividerModule } from "@angular/material/divider";
import { MatFormFieldModule } from "@angular/material/form-field";
import { MatGridListModule } from "@angular/material/grid-list";
import { MatInputModule } from "@angular/material/input";
import { MatSelectModule } from "@angular/material/select";
import { NoopAnimationsModule } from "@angular/platform-browser/animations";
import { RouterTestingModule } from "@angular/router/testing";
import { SecurityTableViewModule } from "../../security-table-view/security-table-view.module";
import { SecurityTreeViewModule } from "../../security-tree-view/security-tree-view.module";
import { UsersSettingsViewComponent } from "./users-settings-view.component";
import { AppInfoService } from "../../../../../../../shared/util/app-info.service";

describe("UsersSettingsViewComponent", () => {
   let component: UsersSettingsViewComponent;
   let fixture: ComponentFixture<UsersSettingsViewComponent>;

   beforeEach(() => {
      TestBed.configureTestingModule({
         imports: [
            NoopAnimationsModule,
            RouterTestingModule,
            SecurityTreeViewModule,
            MatSelectModule,
            MatDialogModule,
            HttpClientTestingModule,
            MatInputModule,
            MatGridListModule,
            MatDividerModule,
            MatCardModule,
            MatFormFieldModule,
            FormsModule,
            ReactiveFormsModule,
            SecurityTableViewModule
         ],
         declarations: [
            UsersSettingsViewComponent
         ],
         providers: [
            AppInfoService
         ],
         schemas: [NO_ERRORS_SCHEMA]
      })
         .compileComponents();
   });

   beforeEach(() => {
      fixture = TestBed.createComponent(UsersSettingsViewComponent);
      component = fixture.componentInstance;
      fixture.detectChanges();
   });

   it("should create", () => {
      expect(component).toBeTruthy();
   });
});
