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
import { NO_ERRORS_SCHEMA, Renderer2 } from "@angular/core";
import { async, ComponentFixture, TestBed } from "@angular/core/testing";
import { ReactiveFormsModule } from "@angular/forms";
import { MatCardModule } from "@angular/material/card";
import { ChangePasswordFormComponent } from "./change-password-form.component";
import { ChangePasswordService } from "./change-password.service";

describe("ChangePasswordFormComponent", () => {
   let component: ChangePasswordFormComponent;
   let fixture: ComponentFixture<ChangePasswordFormComponent>;
   let render: any;
   let pwdService: any;

   beforeEach(async(() => {
      render = {
         listen: jest.fn()
      };

      pwdService = {
         verifyOldPassword: jest.fn(),
         notify: jest.fn()
      };

      TestBed.configureTestingModule({
         imports: [
            ReactiveFormsModule,
            MatCardModule
         ],
         providers: [
            { provide: Renderer2, useValue: render },
            { provide: ChangePasswordService, useValue: pwdService}
         ],
         declarations: [ChangePasswordFormComponent],
         schemas: [NO_ERRORS_SCHEMA]
      }).compileComponents();
   }));

   beforeEach(() => {
      fixture = TestBed.createComponent(ChangePasswordFormComponent);
      component = fixture.componentInstance;
      fixture.detectChanges();
   });

   it("should create", () => {
      expect(component).toBeTruthy();
   });
});
