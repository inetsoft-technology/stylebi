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
import { ComponentFixture, TestBed } from "@angular/core/testing";
import { FormControl, FormGroup, FormsModule, ReactiveFormsModule } from "@angular/forms";
import { MatCardModule } from "@angular/material/card";
import { MatCheckboxModule } from "@angular/material/checkbox";
import { MatOptionModule } from "@angular/material/core";
import { MatIconModule } from "@angular/material/icon";
import { MatInputModule } from "@angular/material/input";
import { MatSelectModule } from "@angular/material/select";
import { NoopAnimationsModule } from "@angular/platform-browser/animations";
import { AuthorizationProviderDetailViewComponent } from "./authorization-provider-detail-view.component";

describe("AuthorizationProviderDetailViewComponent", () => {
   let component: AuthorizationProviderDetailViewComponent;
   let fixture: ComponentFixture<AuthorizationProviderDetailViewComponent>;

   beforeEach(() => {
      (window.document.body as any).createTextRange = jest.fn().mockImplementation(() => ({
         setEnd: jest.fn(),
         setStart: jest.fn(),
         getBoundingClientRect: jest.fn(() => ({right: 0})),
         getClientRects: jest.fn(() => ({length: 0, left: 0, right: 0}))
      }));

      TestBed.configureTestingModule({
         imports: [
            FormsModule,
            ReactiveFormsModule,
            NoopAnimationsModule,
            MatCardModule,
            MatCheckboxModule,
            MatIconModule,
            MatIconModule,
            MatInputModule,
            MatOptionModule,
            MatSelectModule
         ],
         declarations: [
            AuthorizationProviderDetailViewComponent
         ],
         schemas: [
            NO_ERRORS_SCHEMA
         ]
      })
      .compileComponents();

      fixture = TestBed.createComponent(AuthorizationProviderDetailViewComponent);
      component = fixture.componentInstance;
      component.form = new FormGroup({providerName: new FormControl()});
      fixture.detectChanges();
   });

   it("should create", () => {
      expect(component).toBeTruthy();
   });
});
