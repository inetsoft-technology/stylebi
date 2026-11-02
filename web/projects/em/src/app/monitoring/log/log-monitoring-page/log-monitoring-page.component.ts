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
import { HttpClient, HttpParams } from "@angular/common/http";
import { Component, OnDestroy, OnInit } from "@angular/core";
import { Subject, Subscription } from "rxjs";
import { concatMap, filter, tap } from "rxjs/operators";
import { GuiTool } from "../../../../../../portal/src/app/common/util/gui-tool";
import { DownloadService } from "../../../../../../shared/download/download.service";
import { ContextHelp } from "../../../context-help";
import { PageHeaderService } from "../../../page-header/page-header.service";
import { Searchable } from "../../../searchable";
import { Secured } from "../../../secured";
import { MonitoringDataService } from "../../monitoring-data.service";
import { LogMonitoringModel } from "../log-monitoring-model";
import { LogViewLinksService } from "../log-view-links.service";
import { LogMonitoringViewComponent } from "../log-monitoring-view/log-monitoring-view.component";
import { MatIcon } from "@angular/material/icon";
import { MatCard, MatCardTitle } from "@angular/material/card";
import { AsyncPipe } from "@angular/common";

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
    styleUrls: ["./log-monitoring-page.component.scss"],
    imports: [MatCard, MatCardTitle, MatIcon, LogMonitoringViewComponent, AsyncPipe]
})
export class LogMonitoringPageComponent implements OnInit, OnDestroy {
   fluentdLogging = false;
   model: LogMonitoringModel = DEFAULT_VIEW_VALUES;
   logContents = new Subject<string[]>();
   // true while the log file list is being requested; listing waits for every cluster node and
   // can take several seconds when a node is slow to answer
   loadingLogFiles = true;
   private reloadTimer: any = null;
   private subscriptions;
   private destroy$ = new Subject<void>();
   private lastReloadTime = 0;

   constructor(private http: HttpClient,
               private pageTitle: PageHeaderService,
               private downloadService: DownloadService,
               private monitoringDataService: MonitoringDataService,
               private logViewLinksService: LogViewLinksService)
   {
      this.pageTitle.title = "_#(js:Logs)";
   }

   ngOnInit(): void {
      this.logViewLinksService.getLinks()
         .pipe(
            tap(links => {
               this.fluentdLogging = links.fluentdLogging;

               if(this.fluentdLogging) {
                  this.model = null;
                  this.loadingLogFiles = false;
               }
            }),
            // the file log viewer is not shown when fluentd logging is used, and the file log
            // endpoints are not available to organization administrators in that mode
            filter(links => !links.fluentdLogging),
            concatMap(() => this.http.get<LogMonitoringModel>(GET_LOGVIEWER_MODEL_URL))
         )
         .subscribe({
            next: (data) => {
               this.model = data;
               this.loadingLogFiles = false;

               if(!this.fluentdLogging) {
                  if(this.isLogSelected()) {
                     this.refreshLog();
                  }
                  else {
                     this.scheduleReload();
                  }
               }
            },
            error: () => {
               this.loadingLogFiles = false;
               this.scheduleReload();
            }
         });
   }

   ngOnDestroy() {
      clearTimeout(this.reloadTimer);

      if(this.subscriptions) {
         this.subscriptions.unsubscribe();
      }

      this.destroy$.next();
      this.destroy$.complete();
   }

   updateSubscription() {
      if(this.subscriptions) {
         this.subscriptions.unsubscribe();
      }

      if(this.model.autoRefresh && this.isLogSelected()) {
         this.subscriptions = new Subscription();
         this.subscriptions.add(
            this.monitoringDataService.connect(AUTO_REFRESH_LOG_URL + this.refreshUrlVars)
               .subscribe((data: string[]) => {
                  if(data && data.length > 0) {
                     this.logContents.next(data);
                  }
                  else {
                     this.reloadLogFiles();
                  }
               }));
      }
   }

   downloadLog() {
      let params = new HttpParams()
         .set("clusterNode", this.model.selectedLog?.clusterNode)
      this.downloadService.download(GuiTool.appendParams("../em/monitoring/logviewer/download",
         params));
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
      if(!this.isLogSelected()) {
         return;
      }

      this.http.get<string[]>(REFRESH_LOG_URL + this.refreshUrlVars)
         .subscribe({
            next: (data) => {
               if(data && data.length > 0) {
                  this.logContents.next(data);
               }
               else {
                  this.reloadLogFiles();
               }
            },
            error: () => {
               this.reloadLogFiles();
            }
         });

      if(updateSub) {
         this.updateSubscription();
      }
   }

   private scheduleReload() {
      clearTimeout(this.reloadTimer);
      this.reloadTimer = setTimeout(() => this.reloadLogFiles(), 5000);
   }

   private reloadLogFiles() {
      const now = Date.now();

      // a list request waits for every cluster node, so never start one while another is pending
      if(this.loadingLogFiles || now - this.lastReloadTime < 10000) {
         return;
      }

      this.lastReloadTime = now;

      if(this.subscriptions) {
         this.subscriptions.unsubscribe();
         this.subscriptions = null;
      }

      this.loadingLogFiles = true;

      this.http.get<LogMonitoringModel>(GET_LOGVIEWER_MODEL_URL)
         .subscribe({
            next: data => {
               this.model = data;
               this.loadingLogFiles = false;

               if(this.isLogSelected()) {
                  this.http.get<string[]>(REFRESH_LOG_URL + this.refreshUrlVars)
                     .subscribe((logData) => {
                        this.logContents.next(logData || []);
                        this.updateSubscription();
                     });
               }
            },
            error: () => this.loadingLogFiles = false
         });
   }

   private get refreshUrlVars(): string {
      const offset: number = this.model.allLines ? 0 : -this.model.lines;
      return `${this.model.selectedLog?.clusterNode}/${this.model.selectedLog?.logFile}/${offset}/-1`
   }

   private isLogSelected(): boolean {
      return this.model && !!this.model.selectedLog && !!this.model.selectedLog.clusterNode &&
         !!this.model.selectedLog.logFile;
   }
}
