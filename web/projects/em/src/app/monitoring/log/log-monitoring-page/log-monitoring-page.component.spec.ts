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

import { ScrollingModule } from "@angular/cdk/scrolling";
import { HttpClientTestingModule, HttpTestingController } from "@angular/common/http/testing";
import { ChangeDetectorRef } from "@angular/core";
import { waitForAsync, ComponentFixture, TestBed } from "@angular/core/testing";
import { MatButtonModule } from "@angular/material/button";
import { MatCheckboxModule } from "@angular/material/checkbox";
import { MatOptionModule } from "@angular/material/core";
import { MatDividerModule } from "@angular/material/divider";
import { MatInputModule } from "@angular/material/input";
import { MatListModule } from "@angular/material/list";
import { MatSelectModule } from "@angular/material/select";
import { NoopAnimationsModule } from "@angular/platform-browser/animations";
import { of as observableOf } from "rxjs";
import { DownloadTargetComponent } from "../../../../../../shared/download/download-target.component";
import { MonitoringDataService } from "../../monitoring-data.service";
import { LogMonitoringViewComponent } from "../log-monitoring-view/log-monitoring-view.component";
import { LogMonitoringPageComponent } from "./log-monitoring-page.component";

describe("LogMonitoringPageComponent", () => {
   let component: LogMonitoringPageComponent;
   let fixture: ComponentFixture<LogMonitoringPageComponent>;

   beforeEach(waitForAsync(() => {
      const monitoringDataService = {
         connect: vi.fn(() => observableOf()),
         subscribe: vi.fn(),
         getClusterAddress: vi.fn(() => observableOf())
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
            DownloadTargetComponent,
            NoopAnimationsModule,
            ScrollingModule,
            LogMonitoringPageComponent, LogMonitoringViewComponent],
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

   it("should not request the log files when fluentd logging is used", () => {
      const http = TestBed.inject(HttpTestingController);
      http.expectOne("../api/em/monitoring/log/links")
         .flush({fluentdLogging: true, logViewUrl: null});

      http.expectNone("../em/monitoring/logviewer/all-logs");
      expect(component.fluentdLogging).toBe(true);
      expect(component.model).toBeNull();
      http.verify();
   });

   it("should request the log files when file logging is used", () => {
      const http = TestBed.inject(HttpTestingController);
      http.expectOne("../api/em/monitoring/log/links")
         .flush({fluentdLogging: false, logViewUrl: null});

      http.expectOne("../em/monitoring/logviewer/all-logs");
      expect(component.fluentdLogging).toBe(false);
      http.verify();
   });

   // Bug #78642: listing waits for every cluster node and can take ~10 s on a scaling cluster.
   it("should show a loading indicator until the log files arrive", () => {
      const http = TestBed.inject(HttpTestingController);
      http.expectOne("../api/em/monitoring/log/links")
         .flush({fluentdLogging: false, logViewUrl: null});
      fixture.detectChanges();
      expect(component.loadingLogFiles).toBe(true);
      expect(fixture.nativeElement.querySelector(".log-files-loading")).not.toBeNull();

      http.expectOne("../em/monitoring/logviewer/all-logs")
         .flush({selectedLog: null, lines: 500, logFiles: [], autoRefresh: true, showRotate: true});
      // the test environment does not run zone.js, so mark the view dirty as the zone would
      fixture.componentRef.injector.get(ChangeDetectorRef).markForCheck();
      fixture.detectChanges();
      expect(component.loadingLogFiles).toBe(false);
      expect(fixture.nativeElement.querySelector(".log-files-loading")).toBeNull();
      component.ngOnDestroy();
   });

   it("should stop the loading indicator when the log file request fails", () => {
      const http = TestBed.inject(HttpTestingController);
      http.expectOne("../api/em/monitoring/log/links")
         .flush({fluentdLogging: false, logViewUrl: null});
      http.expectOne("../em/monitoring/logviewer/all-logs")
         .flush("error", {status: 500, statusText: "Server Error"});
      expect(component.loadingLogFiles).toBe(false);
      component.ngOnDestroy();
   });

   it("should not show the loading indicator when fluentd logging is used", () => {
      const http = TestBed.inject(HttpTestingController);
      http.expectOne("../api/em/monitoring/log/links")
         .flush({fluentdLogging: true, logViewUrl: null});
      expect(component.loadingLogFiles).toBe(false);
   });

   it("should not request the log files again while a request is pending", () => {
      vi.useFakeTimers();

      try {
         const http = TestBed.inject(HttpTestingController);
         http.expectOne("../api/em/monitoring/log/links")
            .flush({fluentdLogging: false, logViewUrl: null});
         // first answer has no selected log, which schedules a reload in 5 s
         http.expectOne("../em/monitoring/logviewer/all-logs")
            .flush({selectedLog: null, lines: 500, logFiles: [], autoRefresh: true,
                    showRotate: true});

         vi.advanceTimersByTime(5000);
         const reload = http.expectOne("../em/monitoring/logviewer/all-logs");

         // a reload that fires while one is pending (e.g. from empty content) must be ignored
         (component as any).loadingLogFiles = true;
         (component as any).lastReloadTime = 0;
         (component as any).reloadLogFiles();
         http.expectNone("../em/monitoring/logviewer/all-logs");
         reload.flush({selectedLog: null, lines: 500, logFiles: [], autoRefresh: true,
                       showRotate: true});
      }
      finally {
         component.ngOnDestroy();
         vi.useRealTimers();
      }
   });

   it("should cancel the scheduled reload when destroyed", () => {
      vi.useFakeTimers();

      try {
         const http = TestBed.inject(HttpTestingController);
         http.expectOne("../api/em/monitoring/log/links")
            .flush({fluentdLogging: false, logViewUrl: null});
         http.expectOne("../em/monitoring/logviewer/all-logs")
            .flush({selectedLog: null, lines: 500, logFiles: [], autoRefresh: true,
                    showRotate: true});
         component.ngOnDestroy();
         vi.advanceTimersByTime(6000);
         http.expectNone("../em/monitoring/logviewer/all-logs");
      }
      finally {
         vi.useRealTimers();
      }
   });
});
