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
import { NO_ERRORS_SCHEMA } from "@angular/core";
import { ComponentFixture, TestBed } from "@angular/core/testing";
import { FormsModule, ReactiveFormsModule } from "@angular/forms";
import { MatButtonModule } from "@angular/material/button";
import { MatCardModule } from "@angular/material/card";
import { MatCheckboxModule } from "@angular/material/checkbox";
import { MatOptionModule } from "@angular/material/core";
import { MatExpansionModule } from "@angular/material/expansion";
import { MatFormFieldModule } from "@angular/material/form-field";
import { MatGridListModule } from "@angular/material/grid-list";
import { MatInputModule } from "@angular/material/input";
import { MatProgressSpinnerModule } from "@angular/material/progress-spinner";
import { MatRadioModule } from "@angular/material/radio";
import { MatSelectModule } from "@angular/material/select";
import { MatTableModule } from "@angular/material/table";
import { MatTabsModule } from "@angular/material/tabs";
import { NoopAnimationsModule } from "@angular/platform-browser/animations";
import { RouterModule } from "@angular/router";
import { Subject } from "rxjs";
import { LoadingSpinnerComponent } from "../../../../common/util/loading-spinner/loading-spinner.component";
import { TableView } from "../../../../common/util/table/table-view.component";
import { OrganizationDropdownService } from "../../../../navbar/organization-dropdown.service";
import { ResourcePermissionComponent } from "../../../security/resource-permission/resource-permission.component";
import { AnalyzeMvPageComponent } from "../analyze-mv-page/analyze-mv-page.component";
import { RepositorySheetSettingsViewComponent } from "../repository-sheet-settings-view/repository-sheet-settings-view.component";
import { RepositoryViewsheetSettingsViewComponent } from "./repository-viewsheet-settings-view.component";

describe("RepositoryViewsheetSettingsViewComponent", () => {
   let component: RepositoryViewsheetSettingsViewComponent;
   let fixture: ComponentFixture<RepositoryViewsheetSettingsViewComponent>;

   beforeEach(() => {
      TestBed.configureTestingModule({
         imports: [
            HttpClientTestingModule,
            RouterModule.forRoot([]),
            NoopAnimationsModule,
            FormsModule,
            ReactiveFormsModule,
            MatButtonModule,
            MatCardModule,
            MatCheckboxModule,
            MatExpansionModule,
            MatFormFieldModule,
            MatGridListModule,
            MatInputModule,
            MatOptionModule,
            MatProgressSpinnerModule,
            MatRadioModule,
            MatSelectModule,
            MatTableModule,
            MatTabsModule,
            LoadingSpinnerComponent,
            ResourcePermissionComponent,
            AnalyzeMvPageComponent, RepositorySheetSettingsViewComponent, RepositoryViewsheetSettingsViewComponent, TableView],
         schemas: [
            NO_ERRORS_SCHEMA
         ]
      })
      .compileComponents();

      fixture = TestBed.createComponent(RepositoryViewsheetSettingsViewComponent);
      component = fixture.componentInstance;
      fixture.detectChanges();
   });

   it("should create", () => {
      expect(component).toBeTruthy();
   });
});

// Bug #78724: the real view with its real sheet settings view and permission view
describe("RepositoryViewsheetSettingsViewComponent reset", () => {
   let fixture: ComponentFixture<RepositoryViewsheetSettingsViewComponent>;
   let http: HttpTestingController;

   const sheetModel = (): any => ({
      name: "Orders", oname: "Orders", alias: "", description: "", created: "admin",
      modified: "admin", mvEnabled: false, corrupt: false,
      permissionTableModel: {
         permissions: null, displayActions: [], hasOrgEdited: false, securityEnabled: true,
         requiresBoth: false, derivePermissionLabel: "Use Parent", grantReadToAllVisible: false,
         changed: false
      }
   });

   const el = (): HTMLElement => fixture.nativeElement;
   const button = (label: string) => Array.from(el().querySelectorAll(".editor-panel-actions button"))
      .find(b => b.textContent.trim() === label) as HTMLButtonElement;
   const apply = () => button("_#(js:Apply)");
   const detect = () => fixture.componentRef.changeDetectorRef.detectChanges();
   const stable = async () => {
      detect();
      await fixture.whenStable();
      detect();
   };
   // the sheet settings view subscribes to its form changes after a delay
   const settle = async () => {
      await new Promise(r => setTimeout(r, 250));
      await stable();
   };
   // requests made by the permission view that these tests don't depend on
   const flushOther = () => http.match(() => true).forEach(r => r.flush(false));
   const selectTab = async (label: string) => {
      const tab = Array.from(el().querySelectorAll("[role=tab]"))
         .find(t => t.textContent.trim() === label) as HTMLElement;
      tab.click();
      await settle();
      flushOther();
      await settle();
   };
   const nameInput = () => el().querySelector(`input[placeholder="_#(Name)"]`) as HTMLInputElement;
   const typeName = async (value: string) => {
      const i = nameInput();
      i.value = value;
      i.dispatchEvent(new Event("input"));
      await stable();
   };

   beforeEach(async () => {
      TestBed.configureTestingModule({
         imports: [RepositoryViewsheetSettingsViewComponent, HttpClientTestingModule, NoopAnimationsModule],
         providers: [{
            provide: OrganizationDropdownService,
            useValue: { onRefresh: new Subject(), onOrgChange: new Subject(), getProvider: () => "provider" }
         }]
      });
      fixture = TestBed.createComponent(RepositoryViewsheetSettingsViewComponent);
      http = TestBed.inject(HttpTestingController);
      fixture.componentRef.setInput("sheetModel", sheetModel());
      fixture.componentRef.setInput("hasMVPermission", false);
      fixture.componentRef.setInput("smallDevice", false);
      await settle();
      flushOther();
      await settle();
   });

   it("should enable Apply for a permission edit after an invalid name was reset", async () => {
      await typeName("Orders/2026");
      expect(apply().disabled).toBe(true);

      await selectTab("_#(Security)");
      button("_#(js:Reset)").click();
      await settle();

      // uncheck "Use Parent"
      (el().querySelector("mat-checkbox input[type=checkbox]") as HTMLInputElement).click();
      await stable();
      expect(apply().disabled).toBe(false);

      await selectTab("_#(Viewsheet)");
      expect(nameInput().value).toBe("Orders");

      // a new invalid value still disables Apply
      await typeName("Orders/x");
      expect(apply().disabled).toBe(true);
   });
});
