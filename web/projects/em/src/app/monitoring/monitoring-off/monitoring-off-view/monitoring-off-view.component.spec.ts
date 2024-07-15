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
import { CommonModule } from "@angular/common";
import { HttpClientTestingModule } from "@angular/common/http/testing";
import { NO_ERRORS_SCHEMA } from "@angular/core";
import { ComponentFixture, TestBed } from "@angular/core/testing";
import { MatCardModule } from "@angular/material/card";
import { NoopAnimationsModule } from "@angular/platform-browser/animations";
import { MonitoringOffViewComponent } from "./monitoring-off-view.component";

describe("MonitoringOffViewComponent", () => {
   let component: MonitoringOffViewComponent;
   let fixture: ComponentFixture<MonitoringOffViewComponent>;

   beforeEach(() => {
      TestBed.configureTestingModule({
         imports: [
            NoopAnimationsModule,
            CommonModule,
            MatCardModule,
            HttpClientTestingModule,
         ],
         declarations: [MonitoringOffViewComponent],
         schemas: [NO_ERRORS_SCHEMA]
      }).compileComponents();
   });

   beforeEach(() => {
      fixture = TestBed.createComponent(MonitoringOffViewComponent);
      component = fixture.componentInstance;
      fixture.detectChanges();
   });

   it("should create", () => {
      expect(component).toBeTruthy();
   });
});
