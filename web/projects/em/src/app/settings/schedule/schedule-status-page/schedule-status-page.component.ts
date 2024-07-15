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
import { HttpClient } from "@angular/common/http";
import { Component, OnChanges, OnInit, SimpleChanges } from "@angular/core";
import { MatDialog } from "@angular/material/dialog";
import { MatSnackBar } from "@angular/material/snack-bar";
import { DownloadService } from "../../../../../../shared/download/download.service";
import { Tool } from "../../../../../../shared/util/tool";
import { MessageDialog, MessageDialogType } from "../../../common/util/message-dialog";
import { ContextHelp } from "../../../context-help";
import { PageHeaderService } from "../../../page-header/page-header.service";
import { Searchable } from "../../../searchable";
import { Secured } from "../../../secured";
import { ScheduleStatusModel } from "../model/schedule-status-model";

const SCHEDULER_STATUS_URL = "../api/em/settings/schedule/status";

@Secured({
   route: "/settings/schedule/status",
   label: "Status"
})
@Searchable({
   title: "Scheduler Status",
   route: "/settings/schedule/status",
   keywords: ["em.settings.schedule.status", "em.settings.schedule.start", "em.settings.schedule.stop"]
})
@ContextHelp({
   route: "/settings/schedule/status",
   link: "EMSettingsScheduleStatus"
})
@Component({
   selector: "em-schedule-status-page",
   templateUrl: "./schedule-status-page.component.html",
   styleUrls: ["./schedule-status-page.component.scss"]
})
export class ScheduleStatusPageComponent implements OnChanges, OnInit {
   model: ScheduleStatusModel;
   loadMessage: string = "";
   allDisabled: boolean = false;
   columnNames: string[] = ["select", "server", "uptime"];
   selectedClusterServer: string;

   constructor(private http: HttpClient,
               private downloadService: DownloadService,
               private pageTitle: PageHeaderService,
               private snackBar: MatSnackBar,
               private dialog: MatDialog)
   {
   }

   ngOnChanges(changes: SimpleChanges) {
      if(changes.model && this.model && this.model.clusterStatusTable) {
         for(let row of this.model.clusterStatusTable) {
            row.select = true;
         }
      }
   }

   ngOnInit() {
      this.pageTitle.title = "_#(js:Scheduler Status)";
      this.getStatus();
   }

   private getStatus() {
      this.http.get(SCHEDULER_STATUS_URL).subscribe(
         (model: ScheduleStatusModel) => {
            if(this.model &&
               !model.action &&
               this.model.action != "restart" &&
               this.model.status === model.status)
            { // to ensure that the scheduler has stopped fully before enabling all buttons
               this.setLoadMessage(this.model.action);
               setTimeout(() => this.getStatus(), 1000);
            } else {
               this.model = model;
               this.setLoadMessage("");
               this.allDisabled = false;
            }
         });
   }

   get isRunning(): boolean {
      if(this.model) {
         return this.model.running;
      }

      return false;
   }

   changeStatus(action: string) {
      this.model.action = action;
      this.setLoadMessage(action);
      // Disable all buttons after clicking a button to change the scheduler status
      this.allDisabled = true;
      // Set allDisabled property to false no matter what after 30 seconds
      setTimeout(() => this.allDisabled = false, 30000);

      this.http.put(SCHEDULER_STATUS_URL, this.model).subscribe(() => this.getStatus(),
         (error) => {
            this.snackBar.open(error.error.type + ": " +  error.error.message, "_#(js:Close)", {
               duration: Tool.SNACKBAR_DURATION
            });

            this.allDisabled = false;
            this.setLoadMessage("");
         }
      );
   }

   setLoadMessage(action: string) {
      let message: string = action;

      switch(action) {
      case "start":
         message = "_#(js:Starting)...";
         break;
      case "stop":
         message = "_#(js:Stopping)...";
         break;
      case "restart":
         message = "_#(js:Restarting)...";
         break;
      }

      this.loadMessage = message;
   }

   changeSelection(name: any) {
      this.selectedClusterServer = name;
   }

   getThreadDump() {
      let url = "../em/monitoring/scheduler/get-thread-dump";

      if(this.model?.clusterStatusTable?.length > 0 && this.selectedClusterServer) {
         url += "?clusterNode=" + encodeURIComponent(this.selectedClusterServer);
      }

      this.downloadService.download(url);
   }

   getHeapDump() {
      let url = "../em/monitoring/scheduler/get-heap-dump";

      if(this.model?.clusterStatusTable?.length > 0 && this.selectedClusterServer) {
         url += "?clusterNode=" + encodeURIComponent(this.selectedClusterServer);
      }

      this.dialog.open(MessageDialog, {
         width: "500px",
         data: {
            title: "_#(js:Confirm)",
            content: "_#(js:em.confirm.heapDump)",
            type: MessageDialogType.CONFIRMATION
         }
      }).afterClosed().subscribe(value => {
         if(value) {
            this.downloadService.download(url);
         }
      });
   }
}
