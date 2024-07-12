/*
 * inetsoft-web - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
import { ScrollingModule } from "@angular/cdk/scrolling";
import { HttpClientTestingModule, } from "@angular/common/http/testing";
import { async, ComponentFixture, TestBed } from "@angular/core/testing";
import { MatButtonModule } from "@angular/material/button";
import { MatCheckboxModule } from "@angular/material/checkbox";
import { MatOptionModule } from "@angular/material/core";
import { MatDividerModule } from "@angular/material/divider";
import { MatInputModule } from "@angular/material/input";
import { MatListModule } from "@angular/material/list";
import { MatSelectModule } from "@angular/material/select";
import { NoopAnimationsModule } from "@angular/platform-browser/animations";
import { of as observableOf } from "rxjs";
import { DownloadModule } from "../../../../../../shared/download/download.module";
import { MonitoringDataService } from "../../monitoring-data.service";
import { LogMonitoringViewComponent } from "../log-monitoring-view/log-monitoring-view.component";
import { LogMonitoringPageComponent } from "./log-monitoring-page.component";

describe("LogMonitoringPageComponent", () => {
   let component: LogMonitoringPageComponent;
   let fixture: ComponentFixture<LogMonitoringPageComponent>;

   beforeEach(async(() => {
      const monitoringDataService = {
         connect: jest.fn(() => observableOf()),
         subscribe: jest.fn(),
         getClusterAddress: jest.fn(() => observableOf())
      };

      TestBed.configureTestingModule({
         imports: [
            HttpClientTestingModule,
            MatButtonModule,
            MatCheckboxModule,
            MatDividerModule,
            MatInputModule,
            MatListModule,
            MatOptionModule,
            MatSelectModule,
            DownloadModule,
            NoopAnimationsModule,
            ScrollingModule
         ],
         declarations: [LogMonitoringPageComponent, LogMonitoringViewComponent],
         providers: [
            {provide: MonitoringDataService, useValue: monitoringDataService}
         ]
      })
         .compileComponents();
   }));

   beforeEach(() => {
      fixture = TestBed.createComponent(LogMonitoringPageComponent);
      component = fixture.componentInstance;
      fixture.detectChanges();
   });

   it("should create", () => {
      expect(component).toBeTruthy();
   });
});
