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
import { async, ComponentFixture, TestBed } from "@angular/core/testing";
import { MatCardModule } from "@angular/material/card";
import { MatFormFieldModule } from "@angular/material/form-field";
import { MatSelectModule } from "@angular/material/select";
import { NoopAnimationsModule } from "@angular/platform-browser/animations";
import { of as observableOf } from "rxjs";
import { TableViewModule } from "../../../common/util/table/table-view.module";
import { CollapsibleContainerModule } from "../../collapsible-container/collapsible-container.module";
import { MonitoringDataService } from "../../monitoring-data.service";
import { CacheMonitoringViewComponent } from "./cache-monitoring-view.component";

describe("CacheMonitoringViewComponent", () => {
   let component: CacheMonitoringViewComponent;
   let fixture: ComponentFixture<CacheMonitoringViewComponent>;

   beforeEach(async(() => {
      const monitoringDataService = {
         connect: jest.fn(() => observableOf()),
         subscribe: jest.fn(),
         getClusterAddress: jest.fn(() => observableOf())
      };

      TestBed.configureTestingModule({
         imports: [
            HttpClientTestingModule,
            NoopAnimationsModule,
            MatFormFieldModule,
            MatSelectModule,
            MatCardModule,
            CollapsibleContainerModule,
            TableViewModule
         ],
         declarations: [CacheMonitoringViewComponent],
         providers: [
            {provide: MonitoringDataService, useValue: monitoringDataService}
         ],
      })
         .compileComponents();
   }));

   beforeEach(() => {
      fixture = TestBed.createComponent(CacheMonitoringViewComponent);
      component = fixture.componentInstance;
      fixture.detectChanges();
   });

   it("should create", () => {
      expect(component).toBeTruthy();
   });
});
