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
import { NO_ERRORS_SCHEMA } from "@angular/core";
import { async, ComponentFixture, TestBed } from "@angular/core/testing";
import { MatIconModule } from "@angular/material/icon";
import { RouterTestingModule } from "@angular/router/testing";
import { of as observableOf } from "rxjs";
import { AuthorizationService } from "../../authorization/authorization.service";
import { SettingsSidenavComponent } from "./settings-sidenav.component";

describe("SettingsSidenavComponent", () => {
   let component: SettingsSidenavComponent;
   let fixture: ComponentFixture<SettingsSidenavComponent>;

   beforeEach(async(() => {
      const authzService = {
         getPermissions: jest.fn(() => observableOf({permissions: {}}))
      };

      TestBed.configureTestingModule({
         imports: [
            RouterTestingModule,
            MatIconModule
         ],
         declarations: [
            SettingsSidenavComponent
         ],
         providers: [
            { provide: AuthorizationService, useValue: authzService }
         ],
         schemas: [
            NO_ERRORS_SCHEMA
         ]
      }).compileComponents();
   }));

   beforeEach(() => {
      fixture = TestBed.createComponent(SettingsSidenavComponent);
      component = fixture.componentInstance;
      fixture.detectChanges();
   });

   it("should create", () => {
      expect(component).toBeTruthy();
   });
});
