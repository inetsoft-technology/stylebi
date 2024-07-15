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
import { MatIconModule } from "@angular/material/icon";
import { RouterTestingModule } from "@angular/router/testing";
import { of as observableOf } from "rxjs";
import { AuthorizationService } from "../../authorization/authorization.service";
import { MonitoringSidenavComponent } from "./monitoring-sidenav.component";
import { AppInfoService } from "../../../../../shared/util/app-info.service";

describe("MonitoringSidenavComponent", () => {
   let component: MonitoringSidenavComponent;
   let fixture: ComponentFixture<MonitoringSidenavComponent>;

   beforeEach(async(() => {
      const authzService = {
         getPermissions: jest.fn(() => observableOf({permissions: {}}))
      };
      // window.matchMedia = jest.fn().mockImplementation(query => ({
      //    matches: false,
      //    media: query,
      //    onchange: null,
      //    addListener: jest.fn(),
      //    removeListener: jest.fn()
      // }));

      TestBed.configureTestingModule({
         imports: [
            HttpClientTestingModule,
            RouterTestingModule,
            MatIconModule
         ],
         declarations: [
            MonitoringSidenavComponent
         ],
         providers: [
            AppInfoService,
            { provide: AuthorizationService, useValue: authzService }
         ],
         schemas: [
            NO_ERRORS_SCHEMA
         ]
      }).compileComponents();
   }));

   beforeEach(() => {
      fixture = TestBed.createComponent(MonitoringSidenavComponent);
      component = fixture.componentInstance;
      fixture.detectChanges();
   });

   it("should create", () => {
      expect(component).toBeTruthy();
   });
});
