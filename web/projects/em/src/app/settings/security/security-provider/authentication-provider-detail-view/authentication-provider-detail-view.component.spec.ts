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
import { NO_ERRORS_SCHEMA } from "@angular/core";
import { HttpClientTestingModule } from "@angular/common/http/testing";
import { async, ComponentFixture, TestBed } from "@angular/core/testing";
import { FormsModule, ReactiveFormsModule } from "@angular/forms";
import { MatAutocompleteModule } from "@angular/material/autocomplete";
import { MatBottomSheetModule } from "@angular/material/bottom-sheet";
import { MatButtonModule } from "@angular/material/button";
import { MatCardModule } from "@angular/material/card";
import { MatCheckboxModule } from "@angular/material/checkbox";
import { MatOptionModule } from "@angular/material/core";
import { MatDialogModule } from "@angular/material/dialog";
import { MatDividerModule } from "@angular/material/divider";
import { MatIconModule } from "@angular/material/icon";
import { MatInputModule } from "@angular/material/input";
import { MatListModule } from "@angular/material/list";
import { MatSelectModule } from "@angular/material/select";
import { MatSnackBarModule } from "@angular/material/snack-bar";
import { NoopAnimationsModule } from "@angular/platform-browser/animations";
import { SecurityProviderService } from "../security-provider.service";
import { AuthenticationProviderDetailViewComponent } from "./authentication-provider-detail-view.component";
import { AppInfoService } from "../../../../../../../shared/util/app-info.service";

describe("AuthenticationProviderDetailViewComponent", () => {
   let component: AuthenticationProviderDetailViewComponent;
   let fixture: ComponentFixture<AuthenticationProviderDetailViewComponent>;

   beforeEach(async(() => {
      const providerService = {};
      (window.document.body as any).createTextRange = jest.fn().mockImplementation(() => ({
         setEnd: jest.fn(),
         setStart: jest.fn(),
         getBoundingClientRect: jest.fn(() => ({right: 0})),
         getClientRects: jest.fn(() => ({length: 0, left: 0, right: 0}))
      }));

      TestBed.configureTestingModule({
         imports: [
            HttpClientTestingModule,
            FormsModule,
            ReactiveFormsModule,
            NoopAnimationsModule,
            MatAutocompleteModule,
            MatBottomSheetModule,
            MatButtonModule,
            MatCardModule,
            MatCheckboxModule,
            MatDividerModule,
            MatDialogModule,
            MatIconModule,
            MatInputModule,
            MatListModule,
            MatOptionModule,
            MatSelectModule,
            MatSnackBarModule
         ],
         declarations: [
            AuthenticationProviderDetailViewComponent
         ],
         providers: [
            AppInfoService,
            {provide: SecurityProviderService, useValue: providerService}
         ],
         schemas: [
            NO_ERRORS_SCHEMA
         ]
      })
         .compileComponents();
   }));

   beforeEach(() => {
      fixture = TestBed.createComponent(AuthenticationProviderDetailViewComponent);
      component = fixture.componentInstance;
      fixture.detectChanges();
   });

   it("should create", () => {
      expect(component).toBeTruthy();
   });
});
