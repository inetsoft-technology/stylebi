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
import { HttpClient, HttpParams } from "@angular/common/http";
import { Component, OnDestroy, OnInit } from "@angular/core";
import { Subject, Subscription } from "rxjs";
import { concatMap, tap } from "rxjs/operators";
import { DownloadService } from "../../../../../../shared/download/download.service";
import { ContextHelp } from "../../../context-help";
import { PageHeaderService } from "../../../page-header/page-header.service";
import { Searchable } from "../../../searchable";
import { Secured } from "../../../secured";
import { MonitoringDataService } from "../../monitoring-data.service";
import { LogMonitoringModel } from "../log-monitoring-model";
import { LogViewLinks } from "../log-view-links";

const GET_LOGVIEWER_MODEL_URL = "../em/monitoring/logviewer/all-logs";
const AUTO_REFRESH_LOG_URL = "/logviewer/auto_refresh/";
const REFRESH_LOG_URL = "../em/monitoring/logviewer/refresh/";
const ROTATE_LOG_FILES_URL = "../em/monitoring/logviewer/rotate";

const DEFAULT_VIEW_VALUES: LogMonitoringModel = {
   selectedLog: null,
   lines: 500,
   logFiles: [],
   autoRefresh: true,
   showRotate: true
};

@Secured({
   route: "/monitoring/log",
   label: "Logs"
})
@Searchable({
   route: "/monitoring/log",
   title: "Logs",
   keywords: ["em.keyword.monitoring", "em.keyword.log"]
})
@ContextHelp({
   route: "/monitoring/log",
   link: "EMMonitoringLog"
})
@Component({
   selector: "em-log-monitoring-page",
   templateUrl: "./log-monitoring-page.component.html",
   styleUrls: ["./log-monitoring-page.component.scss"]
})
export class LogMonitoringPageComponent implements OnInit, OnDestroy {
   fluentdLogging = false;
   model: LogMonitoringModel = DEFAULT_VIEW_VALUES;
   logContents = new Subject<string[]>();
   private subscriptions;
   private destroy$ = new Subject<void>();

   constructor(private http: HttpClient,
               private pageTitle: PageHeaderService,
               private downloadService: DownloadService,
               private monitoringDataService: MonitoringDataService)
   {
      this.pageTitle.title = "_#(js:Logs)";
   }

   ngOnInit(): void {
      this.http.get<LogViewLinks>("../api/em/monitoring/log/links")
         .pipe(
            tap(links => {
               this.fluentdLogging = links.fluentdLogging;

               if(this.fluentdLogging) {
                  this.model = null;
               }
            }),
            concatMap(() => this.http.get<LogMonitoringModel>(GET_LOGVIEWER_MODEL_URL))
         )
         .subscribe(data => {
            this.model = data;
            this.updateSubscription();
         });
   }

   ngOnDestroy() {
      if(this.subscriptions) {
         this.subscriptions.unsubscribe();
      }

      this.destroy$.next();
      this.destroy$.unsubscribe();
   }

   updateSubscription() {
      if(this.subscriptions) {
         this.subscriptions.unsubscribe();
      }

      if(this.model.autoRefresh) {
         this.subscriptions = new Subscription();
         this.subscriptions.add(
            this.monitoringDataService.connect(AUTO_REFRESH_LOG_URL + this.refreshUrlVars)
               .subscribe((data: string[]) => {
                  this.logContents.next(data);
               }));
      }
   }

   downloadLog() {
      this.downloadService.download("../em/monitoring/logviewer/download");
   }

   rotateLogs() {
      let params = new HttpParams()
         .set("clusterNode", this.model.selectedLog?.clusterNode)
         .set("logFileName", this.model.selectedLog?.logFile);
      this.http.get(ROTATE_LOG_FILES_URL, {params: params}).subscribe(
         (data: LogMonitoringModel) => {
            this.model.logFiles = data.logFiles;
            this.refreshLog(false);
         }
      );
   }

   refreshLog(updateSub: boolean = true) {
      this.http.get(REFRESH_LOG_URL + this.refreshUrlVars)
         .subscribe((data: string[]) => {
            this.logContents.next(data);
         });

      if(updateSub) {
         this.updateSubscription();
      }
   }

   private get refreshUrlVars(): string {
      const offset: number = this.model.allLines ? 0 : -this.model.lines;
      return `${this.model.selectedLog?.clusterNode}/${this.model.selectedLog?.logFile}/${offset}/-1`
   }
}
