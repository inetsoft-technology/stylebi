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
import { HttpClientTestingModule } from "@angular/common/http/testing";
import { NO_ERRORS_SCHEMA } from "@angular/core";
import { async, ComponentFixture, TestBed } from "@angular/core/testing";
import { FormsModule, ReactiveFormsModule } from "@angular/forms";
import { MatCardModule } from "@angular/material/card";
import { MatCheckboxModule } from "@angular/material/checkbox";
import { MatOptionModule } from "@angular/material/core";
import { MatFormFieldModule } from "@angular/material/form-field";
import { MatInputModule } from "@angular/material/input";
import { MatSelectModule } from "@angular/material/select";
import { MatSnackBarModule } from "@angular/material/snack-bar";
import { MatTabsModule } from "@angular/material/tabs";
import { NoopAnimationsModule } from "@angular/platform-browser/animations";
import { DatabaseDefinitionModel } from "../../../../../../../shared/util/model/database-definition-model";
import { DatasourceDatabaseType } from "../../../../../../../shared/util/model/datasource-database-type";
import { ResourcePermissionModule } from "../../../security/resource-permission/resource-permission.module";
import { RepositoryDataSourceSettingsViewComponent } from "../repository-data-source-settings-view/repository-data-source-settings-view.component";
import { RepositoryDataSourceSettingsPageComponent } from "./repository-data-source-settings-page.component";

const DEFAULT_DATABASE: DatabaseDefinitionModel = {
   name: "",
   type: DatasourceDatabaseType.CUSTOM,
   authentication: {
      required: false
   },
   info: {},
   network: null,
   deletable: true,
   ansiJoin: false,
   transactionIsolation: -1,
   tableNameOption: 3,
   defaultDatabase: null,
   changeDefaultDB: false
};

xdescribe("RepositoryDataSourceSettingsPageComponent", () => {
   let component: RepositoryDataSourceSettingsPageComponent;
   let fixture: ComponentFixture<RepositoryDataSourceSettingsPageComponent>;

   beforeEach(async(() => {
      TestBed.configureTestingModule({
         imports: [MatCardModule, MatTabsModule, ResourcePermissionModule, MatSnackBarModule,
            NoopAnimationsModule, FormsModule, ReactiveFormsModule, HttpClientTestingModule,
            MatCheckboxModule, MatSelectModule, MatOptionModule, MatFormFieldModule, MatInputModule],
         declarations: [RepositoryDataSourceSettingsPageComponent,
            RepositoryDataSourceSettingsViewComponent],
         schemas: [NO_ERRORS_SCHEMA]
      })
         .compileComponents();
   }));

   beforeEach(() => {
      fixture = TestBed.createComponent(RepositoryDataSourceSettingsPageComponent);
      component = fixture.componentInstance;
      component.database = DEFAULT_DATABASE;
      fixture.detectChanges();
   });

   it("should create", () => {
      expect(component).toBeTruthy();
   });
});
