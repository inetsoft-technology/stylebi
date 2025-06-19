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
import { HttpClient } from "@angular/common/http";
import { Component, OnDestroy, OnInit, ViewEncapsulation } from "@angular/core";
import { MatDialog } from "@angular/material/dialog";
import { Subscription } from "rxjs";
import { AuthorizationService } from "../../../authorization/authorization.service";
import { MessageDialog, MessageDialogType } from "../../../common/util/message-dialog";
import { ColumnInfo } from "../../../common/util/table/column-info";
import { ExpandableRowTableInfo } from "../../../common/util/table/expandable-row-table/expandable-row-table-info";
import { ContextHelp } from "../../../context-help";
import { PageHeaderService } from "../../../page-header/page-header.service";
import { Searchable } from "../../../searchable";
import { Secured } from "../../../secured";
import { MonitorLevel, MonitorLevelService } from "../../monitor-level.service";
import { MonitoringDataService } from "../../monitoring-data.service";
import { ThreadStackTrace } from "../../thread-stack-trace";
import { ViewsheetMonitoringTableModel } from "../viewsheet-monitoring-model/viewsheet-monitoring-table-model";

@Secured({
   route: "/monitoring/viewsheets",
   label: "Viewsheets",
   requiredLicenses: [],
   children: [
      { route: "/monitoring/viewsheets/executing", label: "Executing" },
      { route: "/monitoring/viewsheets/open", label: "Open" }
   ]
})
@Searchable({
   title: "Viewsheet Monitoring",
   route: "/monitoring/viewsheets",
   keywords: ["em.keyword.viewsheet", "em.keyword.open", "em.keyword.executing"]
})
@ContextHelp({
   route: "/monitoring/viewsheets",
   link: "EMMonitoringViewsheets"
})
@Component({
   selector: "em-viewsheet-monitoring-page",
   templateUrl: "./viewsheet-monitoring-page.component.html",
   styleUrls: ["./viewsheet-monitoring-page.component.scss"],
   encapsulation: ViewEncapsulation.None,
   host: { "class": "em-viewsheet-monitoring-page" } // eslint-disable-line @angular-eslint/no-host-metadata-property
})
export class ViewsheetMonitoringPageComponent implements OnInit, OnDestroy {
   executingVisible = false;
   executingViewsheets: ViewsheetMonitoringTableModel[];
   executingTableInfo: ExpandableRowTableInfo;
   executingColumns: ColumnInfo[] = [
      {header: "_#(js:Name)", field: "name"},
      {header: "_#(js:User)", field: "user"},
      {header: "_#(js:Task)", field: "task"},
      {header: "_#(js:Age)", field: "age"}
   ];
   executingMediumDeviceHeaders: ColumnInfo[] = [
      {header: "_#(js:Name)", field: "name"},
      {header: "_#(js:User)", field: "user"},
   ];
   openVisible = false;
   openViewsheets: ViewsheetMonitoringTableModel[];
   openTableInfo: ExpandableRowTableInfo;
   openColumns: ColumnInfo[] = [
      {header: "_#(js:Name)", field: "name"},
      {header: "_#(js:User)", field: "user"},
      {header: "_#(js:Task)", field: "task"},
      {header: "_#(js:Age)", field: "age"},
      {header: "_#(js:Accessed)", field: "dateAccessed", level: MonitorLevel.MEDIUM}
   ];
   openMediumDeviceHeaders: ColumnInfo[] = [
      {header: "_#(js:Name)", field: "name"},
      {header: "_#(js:User)", field: "user"},
   ];

   private subscriptions = new Subscription();

   constructor(private pageTitle: PageHeaderService,
               private http: HttpClient,
               private monitoringDataService: MonitoringDataService,
               private monitorLevelService: MonitorLevelService,
               private authzService: AuthorizationService,
               private dialog: MatDialog)
   {
      this.pageTitle.title = "_#(js:Viewsheet Monitoring)";

      this.subscriptions.add(
         this.monitoringDataService.getMonitoringData("/viewsheets/executing")
            .subscribe((viewsheets: ViewsheetMonitoringTableModel[]) => {
               const seen = new Set<string>();
               this.executingViewsheets = viewsheets.filter(vs => {
                  //remove exact duplicates
                  let id = vs.id+vs.name+vs.user+vs.age+vs.thread+vs.dateAccessed;

                  if(seen.has(id)) {
                     return false;
                  }

                  seen.add(id);
                  return true;
               });;

               if(this.executingTableInfo) {
                  this.executingTableInfo.title = this.executingTableTitle;
               }
            }
         )
      );

      this.subscriptions.add(
         this.monitoringDataService.getMonitoringData("/viewsheets/open")
         .subscribe((viewsheets: ViewsheetMonitoringTableModel[]) => {
            const seen = new Set<string>();
            this.openViewsheets = viewsheets.filter(vs => {
                  //remove exact duplicates
                  let id = vs.id+vs.name+vs.user+vs.age+vs.thread+vs.dateAccessed;

                  if(seen.has(id)) {
                     return false;
                  }

                  seen.add(id);
                  return true;
               });

               if(this.openTableInfo) {
                  this.openTableInfo.title = this.openTableTitle;
               }
            }
         )
      );

      this.subscriptions.add(this.monitorLevelService.monitorLevel().subscribe(() => {
         this.executingTableInfo = <ExpandableRowTableInfo> {
            selectionEnabled: true,
            title: this.executingTableTitle,
            columns: this.monitorLevelService.filterColumns(this.executingColumns),
            mediumDeviceHeaders: this.executingMediumDeviceHeaders
         };
         this.openTableInfo = <ExpandableRowTableInfo> {
            selectionEnabled: true,
            title: this.openTableTitle,
            columns: this.monitorLevelService.filterColumns(this.openColumns),
            mediumDeviceHeaders: this.monitorLevelService.filterColumns(this.openMediumDeviceHeaders)
         };
      }));
   }

   ngOnInit() {
      this.subscriptions.add(
         this.authzService.getPermissions("monitoring/viewsheets").subscribe((p) => {
            this.executingVisible = p.permissions.executing;
            this.openVisible = p.permissions.open;
         }));
   }

   ngOnDestroy() {
      this.subscriptions.unsubscribe();
   }

   removeSelectedViewsheets(selected: ViewsheetMonitoringTableModel[]) {
      if(selected == null || selected.length == 0) {
         return;
      }

      const result = this.dialog.open(MessageDialog, {
         width: "500px",
         data: {
            title: "_#(js:Confirm)",
            content: "_#(js:em.common.items.deleteSelectedItems)",
            type: MessageDialogType.CONFIRMATION
         }
      });

      result.afterClosed().subscribe(
         (confirmation) => {
            if(confirmation) {
               const ids: string[] =
                  selected.map((model: ViewsheetMonitoringTableModel) => model.id);
               const URL = "../api/em/monitoring/viewsheets/remove/"
                  + this.monitoringDataService.nonNullCluster;
               this.subscriptions.add(this.http.post(URL, ids).subscribe(() => {
                  this.removeOpenViewsheet(selected);
               }));
            }
         }
      );
   }

   removeOpenViewsheet(selected: ViewsheetMonitoringTableModel[]) {
      if(this.openViewsheets == null) {
         return;
      }

      let arr = this.openViewsheets;
      let narr: ViewsheetMonitoringTableModel[] = [];

      for(let i = arr.length - 1; i > -1; i--) {
         let found = false;

         for(let rvs of selected) {
            if(rvs != null && arr[i] != null && rvs.id == arr[i].id) {
               found = true;
               break;
            }
         }

         if(!found) {
            narr.push(arr[i]);
         }
      }

      this.openViewsheets = narr;
   }

   get executingTableTitle(): string {
      return "_#(js:em.monitor.executingViewsheets): " + (this.executingViewsheets ?
         this.executingViewsheets.length : 0);
   }

   get openTableTitle(): string {
      return "_#(js:em.monitor.openViewsheets): " +
         (this.openViewsheets ? this.openViewsheets.length : 0);
   }

   clickCell(evt: any) {
      let row = evt.row;
      let field = evt.property;

      if(row != null && field == "thread" && row[field] != null) {
         const threadId = row[field].substring(6);
         const url = `../em/monitoring/server/threads/${threadId}`;

         this.http.get<ThreadStackTrace>(url)
            .subscribe(trace => this.showDialog(trace.stackTrace));
      }
   }

   showDialog(msg: string) {
      this.dialog.open(MessageDialog, {
         width: "500px",
         data: {
            title: "_#(js:Thread)",
            content: msg,
            type: MessageDialogType.ERROR
         }
      });
   }
}
