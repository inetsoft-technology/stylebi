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
import { waitForAsync, ComponentFixture, TestBed } from "@angular/core/testing";
import { ReactiveFormsModule } from "@angular/forms";
import { RepositoryDataSourceFolderSettingsViewComponent } from "./repository-data-source-folder-settings-view.component";

describe("RepositoryDataSourceFolderSettingsViewComponent", () => {
   let component: RepositoryDataSourceFolderSettingsViewComponent;
   let fixture: ComponentFixture<RepositoryDataSourceFolderSettingsViewComponent>;

   beforeEach(waitForAsync(() => {
      TestBed.configureTestingModule({
         imports: [
            HttpClientTestingModule,
            ReactiveFormsModule,
            RepositoryDataSourceFolderSettingsViewComponent],
         schemas: [
            NO_ERRORS_SCHEMA
         ]
      })
      .compileComponents();
   }));

   beforeEach(() => {
      fixture = TestBed.createComponent(RepositoryDataSourceFolderSettingsViewComponent);
      component = fixture.componentInstance;
      fixture.detectChanges();
   });

   it("should create", () => {
      expect(component).toBeTruthy();
   });

   // Macros (_#(key)) are expanded server-side by LocalizationService and not in vitest, so the
   // rendered text is the key itself.
   it("should show a single full-sentence message for a duplicate folder name", () => {
      fixture.componentRef.setInput("model", {
         name: "old",
         root: false,
         siblingFolders: ["old", "dup"],
         siblingDataSources: ["ds1"]
      });
      fixture.detectChanges();

      const control = component.form.get("folderName");
      control.setValue("dup");
      control.markAsTouched();
      fixture.detectChanges();

      expect(control.errors?.duplicateName).toBe(true);
      const text = fixture.nativeElement.textContent;
      expect(text).toContain("_#(em.dataSource.folderNameDuplicate)");
      expect(text).not.toContain("em.common.folder.nameDuplicate");
      expect(text).not.toContain("em.repletFolderName.existAlert2");
      expect(text).not.toContain("_#(and)");
   });
});
