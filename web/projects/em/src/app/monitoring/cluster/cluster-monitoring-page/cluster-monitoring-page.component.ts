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
import { SelectionModel } from "@angular/cdk/collections";
import { HttpClient } from "@angular/common/http";
import { Component, OnDestroy, OnInit } from "@angular/core";
import { MatSnackBar } from "@angular/material/snack-bar";
import { Observable, Subscription } from "rxjs";
import { Tool } from "../../../../../../shared/util/tool";
import { AuthorizationService } from "../../../authorization/authorization.service";
import { ColumnInfo } from "../../../common/util/table/column-info";
import { TableInfo } from "../../../common/util/table/table-info";
import { TableModel } from "../../../common/util/table/table-model";
import { ContextHelp } from "../../../context-help";
import { PageHeaderService } from "../../../page-header/page-header.service";
import { Searchable } from "../../../searchable";
import { Secured } from "../../../secured";
import { MonitorLevelService } from "../../monitor-level.service";
import { MonitoringDataService } from "../../monitoring-data.service";
import { ClusterEnabledModel } from "../cluster-monitoring-model/cluster-enabled-model";
import { ReportClusterNodeModel } from "../cluster-monitoring-model/report-cluster-node-model";

@Secured({
   route: "/monitoring/cluster",
   label: "Cluster",
   children: [
      { route: "/monitoring/cluster/reportCluster", label: "Report Cluster" }
   ]
})
@Searchable({
   route: "/monitoring/cluster",
   title: "_#(js:em.clusterMonitoring.title)",
   keywords: [
      "em.keyword.monitoring", "em.keyword.cluster", "em.keyword.server",
      "em.keyword.report"
   ]
})
@ContextHelp({
   route: "/monitoring/cluster",
   link: "EMMonitoringCluster"
})
@Component({
   selector: "em-cluster-monitoring-page",
   templateUrl: "./cluster-monitoring-page.component.html",
   styleUrls: ["./cluster-monitoring-page.component.scss"]
})
export class ClusterMonitoringPageComponent implements OnInit, OnDestroy {

   private subscriptions = new Subscription();

   reportClusterVisible = false;
   reportClusterStatus: Observable<ReportClusterNodeModel[]>;
   reportClusterTableInfo: TableInfo;
   reportClusterColumnsInfo: ColumnInfo[] = [
      {header: "_#(js:Server)", field: "server"},
      {header: "_#(js:Status)", field: "status"}
   ];
   selectedNodes: ReportClusterNodeModel[] = [];

   constructor(private http: HttpClient,
               private snackBar: MatSnackBar,
               private pageTitle: PageHeaderService,
               private monitorLevelService: MonitorLevelService,
               private authorizationService: AuthorizationService,
               private monitoringDataService: MonitoringDataService)
   {
      this.authorizationService.getPermissions("monitoring/cluster").subscribe((p) => {
         if(p.permissions.reportCluster) {
            this.http.get("../em/monitoring/cluster/cluster-enabled").subscribe(
               (data: ClusterEnabledModel) => {
                  this.reportClusterVisible = data.enabled;
               }
            );
         }
      });

      this.monitorLevelService.monitorLevel().subscribe(() => {
         this.reportClusterTableInfo = {
            selectionEnabled: true,
            title: "_#(js:Cluster Servers):",
            columns: this.monitorLevelService.filterColumns(this.reportClusterColumnsInfo),
            actions: []
         };
      });
      this.reportClusterStatus = this.monitoringDataService.connect("/cluster/report-cluster");
   }

   pauseEnabled(): boolean {
      return this.selectedNodes.length !== 0 && this.selectedNodes.map((node) => node.status)
         .some(status => status == "Running");
   }

   resumeEnabled(): boolean {
      return this.selectedNodes.length !== 0 && this.selectedNodes.map((node) => node.status)
         .some(status => status == "Paused");
   }

   updateSelectedNodes(selection: SelectionModel<TableModel>) {
      this.selectedNodes = (<ReportClusterNodeModel[]>selection.selected);
   }

   pauseServers() {
      this.http.get("../em/monitoring/cluster/cluster-status")
         .subscribe((nodes: ReportClusterNodeModel[]) => {
            if(!!nodes) {
               let remainingRunningNodes = nodes.filter((node) => node.status === "Running"
                  && !this.selectedNodes.some((snode => snode.server == node.server)));

               if(remainingRunningNodes.length < 1) {
                  this.snackBar.open("_#(js:em.cluster.pause.last)", null, {
                     duration: Tool.SNACKBAR_DURATION,
                  });
               }
            }

            this.http.post("../em/monitoring/cluster/pause-server",
               this.selectedNodes.map((node) => node.server)).subscribe();
      });
   }

   resumeServers() {
      this.http.post("../em/monitoring/cluster/resume-server",
         this.selectedNodes.map((node) => node.server)).subscribe();
   }

   ngOnInit() {
      this.pageTitle.title = "_#(js:em.clusterMonitoring.title)";
   }

   ngOnDestroy() {
      this.subscriptions.unsubscribe();
   }
}
