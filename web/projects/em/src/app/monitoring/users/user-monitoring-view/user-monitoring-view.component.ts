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
import { Component, OnDestroy, OnInit } from "@angular/core";
import { MatDialog } from "@angular/material/dialog";
import { Subscription } from "rxjs";
import { AuthorizationService } from "../../../authorization/authorization.service";
import { MessageDialog, MessageDialogType } from "../../../common/util/message-dialog";
import { ColumnInfo } from "../../../common/util/table/column-info";
import { ExpandableRowTableInfo } from "../../../common/util/table/expandable-row-table/expandable-row-table-info";
import { TableInfo } from "../../../common/util/table/table-info";
import { TableModel } from "../../../common/util/table/table-model";
import { UserSessionsMonitoring } from "../../../common/util/table/user-sessions-monitoring";
import { ContextHelp } from "../../../context-help";
import { PageHeaderService } from "../../../page-header/page-header.service";
import { Searchable } from "../../../searchable";
import { Secured } from "../../../secured";
import { ClusterNodesService } from "../../cluster/cluster-nodes.service";
import { MonitorLevel, MonitorLevelService } from "../../monitor-level.service";
import { MonitoringDataService } from "../../monitoring-data.service";

export interface UserFailedLoginMonitoring extends TableModel {
   user: string;
   address: string;
   time: string;
}

@Secured({
   route: "/monitoring/users",
   label: "Users",
   children: [
      { route: "/monitoring/users/session", label: "Sessions" },
      { route: "/monitoring/users/failedLogin", label: "Failed Logins" }
   ]
})
@Searchable({
   route: "/monitoring/users",
   title: "User Monitoring",
   keywords: ["em.keyword.users", "em.keyword.sessions", "em.keyword.failedLogins"]
})
@ContextHelp({
   route: "/monitoring/users",
   link: "EMMonitoringUsers"
})
@Component({
   selector: "em-user-monitoring-view",
   templateUrl: "./user-monitoring-view.component.html",
   styleUrls: ["./user-monitoring-view.component.scss"]
})
export class UserMonitoringViewComponent implements OnInit, OnDestroy {
   clusterNodes: string[];
   sessionVisible: boolean = false;
   failedLoginVisible: boolean = false;
   failedLoginPermitted: boolean = false;
   orgNames: string[] = [];
   isSiteAdmin: boolean = false;
   isMultiTenant: boolean = false;
   monitorHigh: boolean = false;
   private subscriptions = new Subscription();
   private clusterSubscriptions = new Subscription();
   sessionTableInfo: ExpandableRowTableInfo;
   sessionModel: UserSessionsMonitoring[] = [];
   failedLoginModel: UserFailedLoginMonitoring[] = [];

   sessionTableColumnsEnterprise: ColumnInfo[] = [
      {field: "sessionLabel", header: "_#(js:Session ID)", level: MonitorLevel.LOW},
      {field: "userName", header: "_#(js:User)", level: MonitorLevel.LOW},
      {field: "address", header: "_#(js:Address)", level: MonitorLevel.LOW},
      {field: "age", header: "_#(js:Age)", level: MonitorLevel.LOW},
      {field: "accessed", header: "_#(js:Accessed)", level: MonitorLevel.MEDIUM},
      {field: "roles", header: "_#(js:Roles)", level: MonitorLevel.LOW},
      {field: "groups", header: "_#(js:Groups)", level: MonitorLevel.LOW},
   ];
   sessionTimeCols: string[] = ["age", "accessed"];
   sessionMediumDeviceColumns: ColumnInfo[] = [
      {field: "sessionLabel", header: "_#(js:Session ID)", level: MonitorLevel.LOW},
      {field: "userName", header: "_#(js:User)", level: MonitorLevel.LOW},
   ];
   failedLoginTableInfo: TableInfo;
   failedLoginTableColumns: ColumnInfo[] = [
      {field: "user", header: "_#(js:User)"},
      {field: "address", header: "_#(js:Address)"},
      {field: "time", header: "_#(js:Time)"}
   ];

   constructor(private pageTitle: PageHeaderService,
               private http: HttpClient,
               private monitoringDataService: MonitoringDataService,
               private monitorLevelService: MonitorLevelService,
               private clusterNodesService: ClusterNodesService,
               private authorizationService: AuthorizationService,
               private dialog: MatDialog)
   {
   }

   ngOnInit() {
      this.pageTitle.title = "_#(js:User Monitoring)";

      this.subscriptions.add(
         this.authorizationService.getPermissions("monitoring/users").subscribe((p) => {
            this.sessionVisible = p.permissions.session;
            this.failedLoginPermitted = p.permissions.failedLogin;
            this.failedLoginVisible = this.failedLoginPermitted && this.monitorHigh;
         })
      );

      this.subscriptions.add(this.monitoringDataService.getClusterAddress().subscribe(() => {
         this.initMonitoringData();
      }));

      this.subscriptions.add(
         this.clusterNodesService.getClusterNodes().subscribe((data: string[]) => {
         this.clusterNodes = data;

         if(this.clusterEnabled && this.clusterNodes && !this.selectedClusterNode) {
            this.selectedClusterNode = this.clusterNodes[0];
         }
      }));
      this.http.get<boolean>("../api/em/navbar/isMultiTenant")
         .subscribe(isMultiTenant => {
            this.isMultiTenant = isMultiTenant;
            this.subscriptions.add(this.monitorLevelService.monitorLevel().subscribe(level => {
               this.sessionTableInfo = {
                  selectionEnabled: true,
                  title: "_#(js:Sessions):",
                  columns: this.monitorLevelService.filterColumns(this.sessionTableColumnsEnterprise),
                  mediumDeviceHeaders: this.monitorLevelService.filterColumns(this.sessionMediumDeviceColumns),
               };
               this.failedLoginTableInfo = {
                  selectionEnabled: false,
                  title: "_#(js:Failed Logins)",
                  columns: this.monitorLevelService.filterColumns(this.failedLoginTableColumns)
               };

               this.monitorHigh = level == MonitorLevel.HIGH;
               this.failedLoginVisible = this.failedLoginPermitted && this.monitorHigh;
            }));
      });
   }

   tryGetBaseNames(tabledata: UserSessionsMonitoring[]): UserSessionsMonitoring[] {
      if(this.isSiteAdmin && this.isMultiTenant) {
         return tabledata;
      }
      return tabledata
   }

   private initMonitoringData() {
      this.http.get<boolean>("../api/em/navbar/isSiteAdmin").subscribe(
         (isAdmin => {
            this.isSiteAdmin = isAdmin;

            this.http.get<string[]>("../api/em/security/users/get-all-organization-names/").subscribe(
               (orgList =>  {
                  this.orgNames = orgList;

                  let sub = this.monitoringDataService.getMonitoringData("/user/get-session-grid")
                     .subscribe((data: UserSessionsMonitoring[]) => {
                        this.sessionModel = this.tryGetBaseNames(data);
                        this.sessionTableInfo.title = `_#(js:Sessions): ${data.length}`;
                     });
                  this.clusterSubscriptions.add(sub);

                  sub = this.monitoringDataService.getMonitoringData("/user/get-failed-grid")
                     .subscribe((data: UserFailedLoginMonitoring[]) => {
                        this.failedLoginModel = data;
                        this.failedLoginTableInfo.title = `_#(js:Failed Logins): ${data.length}`;
                     });
                  this.clusterSubscriptions.add(sub);
               })
            );
         })
      );
   }

   ngOnDestroy() {
      this.subscriptions.unsubscribe();
      this.clusterSubscriptions.unsubscribe();
   }

   get selectedClusterNode(): string {
      return this.monitoringDataService.cluster;
   }

   set selectedClusterNode(cluster: string) {
      this.monitoringDataService.cluster = cluster;
   }

   logoutSessions(sessions: TableModel[]) {
      const ref = this.dialog.open(MessageDialog, {
         data: {
            title: "_#(js:Remove Sessions)",
            content: "_#(js:em.monitor.removeUserSessions)",
            type: MessageDialogType.CONFIRMATION
         }
      });

      ref.afterClosed().subscribe((close: boolean) => {
         if(close) {
            const ids = sessions
               .map((session) => session["sessionID"])
               .reduce((a, b) => {
                  if(a.indexOf(b) < 0) {
                     a.push(b);
                  }

                  return a;
               }, []);
            let data1;
            let uri = "../api/em/monitor/user/logout";

            if(!!this.selectedClusterNode) {
               uri += `/${this.selectedClusterNode}`;
            }

            this.http.post(uri, ids).subscribe(
               (data: any) => {
                  data1 = data;
               },
               () => {
                  this.dialog.open(MessageDialog, {
                     width: "500px",
                     data: {
                        title: "_#(js:Error)",
                        content: "The session does not exist!",
                        type: MessageDialogType.ERROR
                     }
                  });
               });
         }
      });
   }

   get clusterEnabled(): boolean {
      return this.clusterNodes && this.clusterNodes.length > 0;
   }

   get emptyPage(): boolean {
      return !(this.clusterEnabled || this.sessionVisible || this.failedLoginVisible);
   }
}
