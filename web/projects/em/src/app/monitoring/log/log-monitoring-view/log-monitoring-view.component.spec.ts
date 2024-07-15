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
import { ScrollingModule } from "@angular/cdk/scrolling";
import { ComponentFixture, TestBed } from "@angular/core/testing";
import { MatButtonModule } from "@angular/material/button";
import { MatCheckboxModule } from "@angular/material/checkbox";
import { MatOptionModule } from "@angular/material/core";
import { MatDividerModule } from "@angular/material/divider";
import { MatInputModule } from "@angular/material/input";
import { MatListModule } from "@angular/material/list";
import { MatSelectModule } from "@angular/material/select";
import { NoopAnimationsModule } from "@angular/platform-browser/animations";
import { LogMonitoringViewComponent } from "./log-monitoring-view.component";

const mockLogMonitoringModel = {
   selectedLog: null,
   lines: 0,
   logFiles: [],
   autoRefresh: false,
   showRotate: false,
   allLines: false
};

describe("LogMonitoringViewComponent", () => {
   let component: LogMonitoringViewComponent;
   let fixture: ComponentFixture<LogMonitoringViewComponent>;

   beforeEach(() => {
      TestBed.configureTestingModule({
         imports: [
            MatButtonModule,
            MatCheckboxModule,
            MatDividerModule,
            MatInputModule,
            MatListModule,
            MatOptionModule,
            MatSelectModule,
            NoopAnimationsModule,
            ScrollingModule
         ],
         declarations: [LogMonitoringViewComponent]
      })
         .compileComponents();
      fixture = TestBed.createComponent(LogMonitoringViewComponent);
      component = fixture.componentInstance;
      component.model = mockLogMonitoringModel;
      component.logContents = [];
      fixture.detectChanges();
   });

   it("should create", () => {
      expect(component).toBeTruthy();
   });
});
