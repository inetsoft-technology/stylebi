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
import { ComponentFixture, TestBed } from "@angular/core/testing";
import { FormsModule, ReactiveFormsModule } from "@angular/forms";
import { MatButtonModule } from "@angular/material/button";
import { MatCardModule } from "@angular/material/card";
import { MatCheckboxModule } from "@angular/material/checkbox";
import { MatOptionModule } from "@angular/material/core";
import { MatDialogModule } from "@angular/material/dialog";
import { MatFormFieldModule } from "@angular/material/form-field";
import { MatInputModule } from "@angular/material/input";
import { MatRadioModule } from "@angular/material/radio";
import { MatSelectModule } from "@angular/material/select";
import { MatTabsModule } from "@angular/material/tabs";
import { NoopAnimationsModule } from "@angular/platform-browser/animations";
import { RouterTestingModule } from "@angular/router/testing";
import { EditorPanelComponent } from "../../../../common/util/editor-panel/editor-panel.component";
import { TableViewModule } from "../../../../common/util/table/table-view.module";
import { ResourcePermissionModule } from "../../../security/resource-permission/resource-permission.module";
import { RepositoryFolderTrashcanSettingsViewComponent } from "./repository-folder-trashcan-settings-view.component";

describe("RepositoryFolderTrashcanSettingsViewComponent", () => {
   let component: RepositoryFolderTrashcanSettingsViewComponent;
   let fixture: ComponentFixture<RepositoryFolderTrashcanSettingsViewComponent>;

   beforeEach(() => {
      TestBed.configureTestingModule({
         imports: [HttpClientTestingModule, RouterTestingModule, MatCardModule, MatDialogModule,
            MatButtonModule, MatInputModule, MatSelectModule, NoopAnimationsModule,
            MatOptionModule, MatTabsModule, MatFormFieldModule, FormsModule, ReactiveFormsModule,
            MatRadioModule, MatCheckboxModule, ResourcePermissionModule, TableViewModule],
         declarations: [RepositoryFolderTrashcanSettingsViewComponent, EditorPanelComponent]
      })
         .compileComponents();

      fixture = TestBed.createComponent(RepositoryFolderTrashcanSettingsViewComponent);
      component = fixture.componentInstance;
      fixture.detectChanges();
   });

   it("should create", () => {
      expect(component).toBeTruthy();
   });
});
