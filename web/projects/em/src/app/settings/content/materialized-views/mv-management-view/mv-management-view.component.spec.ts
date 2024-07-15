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
import { async, ComponentFixture, TestBed } from "@angular/core/testing";
import { FormsModule, ReactiveFormsModule } from "@angular/forms";
import { MatButtonModule } from "@angular/material/button";
import { MatCardModule } from "@angular/material/card";
import { MatCheckboxModule } from "@angular/material/checkbox";
import { MatDialogModule } from "@angular/material/dialog";
import { MatFormFieldModule } from "@angular/material/form-field";
import { MatIconModule } from "@angular/material/icon";
import { MatInputModule } from "@angular/material/input";
import { MatPaginatorModule } from "@angular/material/paginator";
import { MatSelectModule } from "@angular/material/select";
import { MatSortModule } from "@angular/material/sort";
import { MatTableModule } from "@angular/material/table";
import { NoopAnimationsModule } from "@angular/platform-browser/animations";
import { RouterTestingModule } from "@angular/router/testing";
import { EditorPanelModule } from "../../../../common/util/editor-panel/editor-panel.module";
import { LoadingSpinnerModule } from "../../../../common/util/loading-spinner/loading-spinner.module";
import { MessageDialogModule } from "../../../../common/util/message-dialog.module";
import { TableViewModule } from "../../../../common/util/table/table-view.module";
import { MvManagementViewComponent } from "./mv-management-view.component";
import { SsoHeartbeatService } from "../../../../../../../shared/sso/sso-heartbeat.service";
import { MVChangeService } from "./mv-change.service";
import { of as observableOf, Subject } from "rxjs";

describe("MvManagementViewComponent", () => {
   let component: MvManagementViewComponent;
   let fixture: ComponentFixture<MvManagementViewComponent>;
   let ssoHeartbeatService: any;
   let changes = new Subject<void>();

   beforeEach(async(() => {
      ssoHeartbeatService = { heartbeat: jest.fn() };
      const changeService = {
        mvChanged: changes.asObservable()
      };

      TestBed.configureTestingModule({
         imports: [
            NoopAnimationsModule,
            FormsModule,
            HttpClientTestingModule,
            RouterTestingModule,
            LoadingSpinnerModule,
            MatButtonModule,
            MatCardModule,
            MatCheckboxModule,
            MatDialogModule,
            MatFormFieldModule,
            MatIconModule,
            MatInputModule,
            MatPaginatorModule,
            MatSelectModule,
            MatSortModule,
            MatTableModule,
            MessageDialogModule,
            ReactiveFormsModule,
            TableViewModule,
            EditorPanelModule
         ],
         declarations: [MvManagementViewComponent],
         providers: [
            { provide: SsoHeartbeatService, useValue: ssoHeartbeatService }
         ],
         schemas: [
            NO_ERRORS_SCHEMA
         ]
      })
       .overrideComponent(MvManagementViewComponent, {
          set: {
             providers: [
                { provide: MVChangeService, useValue: changeService },
             ]
          }
      })
       .compileComponents();
   }));

   beforeEach(() => {
      fixture = TestBed.createComponent(MvManagementViewComponent);
      component = fixture.componentInstance;
      fixture.detectChanges();
   });

   it("should create", () => {
      expect(component).toBeTruthy();
   });
});
