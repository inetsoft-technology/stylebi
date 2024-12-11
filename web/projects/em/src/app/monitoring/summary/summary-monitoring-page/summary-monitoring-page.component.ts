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
import {
   AfterContentChecked,
   Component,
   OnDestroy,
   OnInit,
   Renderer2,
   ViewChild
} from "@angular/core";
import { MatDialog, MatDialogRef } from "@angular/material/dialog";
import { Router } from "@angular/router";
import { Observable, Subscription } from "rxjs";
import { switchMap, tap, withLatestFrom } from "rxjs/operators";
import { DownloadService } from "../../../../../../shared/download/download.service";
import { LogoutService } from "../../../../../../shared/util/logout.service";
import { SessionInactivityService } from "../../../../../../shared/util/session-inactivity.service";
import { AuthorizationService } from "../../../authorization/authorization.service";
import { MessageDialog, MessageDialogType } from "../../../common/util/message-dialog";
import { TableInfo } from "../../../common/util/table/table-info";
import { ContextHelp } from "../../../context-help";
import { PageHeaderService } from "../../../page-header/page-header.service";
import { Searchable } from "../../../searchable";
import { Secured } from "../../../secured";
import { MoveCopyTreeNodesRequest } from "../../../settings/content/repository/model/move-copy-tree-nodes-request";
import { SecurityEnabledEvent } from "../../../settings/security/security-settings-page/security-enabled-event";
import { SessionExpirationDialog } from "../../../widget/dialog/session-expiration-dialog/session-expiration-dialog.component";
import { ClusterNodesService } from "../../cluster/cluster-nodes.service";
import { MonitorLevel } from "../../monitor-level.service";
import { MonitoringDataService } from "../../monitoring-data.service";
import { JvmModel } from "../summary-monitoring-view/jvm-model";
import { ReverseProxyModel } from "../summary-monitoring-view/reverse-proxy-model";
import { ServerModel } from "../summary-monitoring-view/server-model";
import { SummaryChartInfo } from "../summary-monitoring-view/summary-monitoring-chart-view/summary-chart-info";
import { SummaryChartLegend } from "../summary-monitoring-view/summary-monitoring-chart-view/summary-chart-legend";
import { HeapDumpRequest } from "./heap-dump-request";
import { ServerSummaryModel } from "./server-summary-model";

const SMALL_WIDTH_BREAKPOINT = 720;

export interface ChartInfo {
   link: string;
   text: string;
}

@Secured({
   route: "/monitoring/summary",
   label: "Summary",
   children: [
      { route: "/monitoring/summary/heapMemory", label: "Heap Memory Usage" },
      { route: "/monitoring/summary/cpuUsage", label: "CPU Usage" },
      { route: "/monitoring/summary/gcCount", label: "GC Count" },
      { route: "/monitoring/summary/gcTime", label: "GC Time" },
      { route: "/monitoring/summary/memoryCache", label: "Memory Cache" },
      { route: "/monitoring/summary/execution", label: "Execution" },
      { route: "/monitoring/summary/diskCache", label: "Disk Cache" },
      { route: "/monitoring/summary/swapping", label: "Swapping" },
      { route: "/monitoring/summary/top5Users", label: "Top 5 Users" }
   ],
   hiddenForMultiTenancy: true,
})
@Searchable({
   title: "Summary",
   route: "/monitoring/summary",
   keywords: [
      "em.keyword.summary", "em.keyword.cpu", "em.keyword.memory",
      "em.keyword.heap", "em.keyword.usage", "em.keyword.execution",
      "em.keyword.swapping", "em.keyword.topUsers"
   ]
})
@ContextHelp({
   route: "/monitoring/summary",
   link: "EMMonitoringSummary"
})
@Component({
   selector: "em-summary-monitoring-page",
   templateUrl: "./summary-monitoring-page.component.html",
   styleUrls: ["./summary-monitoring-page.component.scss"],
   providers: [SessionInactivityService]
})
export class SummaryMonitoringPageComponent implements OnInit, OnDestroy, AfterContentChecked {
   @ViewChild("summaryPageContainer", { static: true }) pageContainer;
   clusterNodes: string[];

   heapMemoryInfo: SummaryChartInfo = {
      title: "_#(js:Heap Memory Usage)",
      name: "memUsage",
      monitorLevel: MonitorLevel.OFF
   };

   cpuUsageInfo: SummaryChartInfo = {
      title: "_#(js:CPU Usage)",
      name: "cpuUsage",
      monitorLevel: MonitorLevel.OFF
   };

   gcCountInfo: SummaryChartInfo = {
      title: "_#(js:GC Count)",
      name: "gcCount",
      monitorLevel: MonitorLevel.OFF
   };
   gcTimeInfo: SummaryChartInfo = {
      title: "_#(js:GC Time)",
      name: "gcTime",
      monitorLevel: MonitorLevel.OFF
   };

   memoryCacheInfo: SummaryChartInfo = {
      title: "_#(js:Memory Cache)",
      name: "memCache",
      monitorLevel: MonitorLevel.OFF
   };
   executionInfo: SummaryChartInfo = {
      title: "_#(js:Execution)",
      name: "execution",
      monitorLevel: MonitorLevel.OFF
   };
   diskCacheInfo: SummaryChartInfo = {
      title: "_#(js:Disk Cache)",
      name: "diskCache",
      monitorLevel: MonitorLevel.OFF
   };
   swappingInfo: SummaryChartInfo = {
      title: "_#(js:Swapping)",
      name: "swapping",
      monitorLevel: MonitorLevel.LOW
   };

   memoryCacheLegends: SummaryChartLegend[];
   swappingLegends: SummaryChartLegend[];
   executionLegends: SummaryChartLegend[];
   diskCacheLegends: SummaryChartLegend[];
   memLegends: SummaryChartLegend[];
   cpuLegends: SummaryChartLegend[];
   gcCountLegends: SummaryChartLegend[];
   gcTimeLegends: SummaryChartLegend[];

   top5Users: any[];
   top5UsersTableInfo: TableInfo = {
      columns: [
         {field: "userName", header: "_#(js:User)"},
         {field: "viewsheetCount", header: "_#(js:Viewsheets)"},
         {field: "age", header: "_#(js:Session Age)"}
      ],
      selectionEnabled: false,
      title: "_#(js:Top 5 Users)"
   };

   versionNumber = "";
   buildNumber = "";
   currentNode = "";
   serverModel: ServerModel;
   jvmModel: JvmModel;
   reverseProxyModel: ReverseProxyModel;
   cols: number = 1;
   private subscriptions = new Subscription();
   private top5Subscription = Subscription.EMPTY;

   heapMemoryVisible = false;
   cpuUsageVisible = false;
   gcCountVisible = false;
   gcTimeVisible = false;
   memoryCacheVisible = false;
   executionVisible = false;
   diskCacheVisible = false;
   swappingVisible = false;
   top5UsersVisible = false;
   securitySettingsEnabled = false;
   sessionExpirationDialogRef: MatDialogRef<SessionExpirationDialog>;

   constructor(private pageTitle: PageHeaderService,
               private downloadService: DownloadService,
               private http: HttpClient,
               private monitoringDataService: MonitoringDataService,
               private clusterNodesService: ClusterNodesService,
               private authzService: AuthorizationService,
               private renderer: Renderer2,
               private router: Router,
               private dialog: MatDialog,
               private sessionInactivity: SessionInactivityService,
               private logoutService: LogoutService)
   {
   }

   ngOnInit() {
      this.pageTitle.title = "_#(js:Monitoring)";
      this.clusterNodesService.getClusterNodes()
         .pipe(
            switchMap(nodes => {
               this.clusterNodes = nodes;

               if(this.clusterEnabled && !this.selectedClusterNode) {
                  this.selectedClusterNode = this.clusterNodes[0];
               }

               return this.authzService.getPermissions("monitoring/summary");
            }),
            tap(permissions => {
               this.heapMemoryVisible = permissions.permissions.heapMemory;
               this.cpuUsageVisible = permissions.permissions.cpuUsage;
               this.gcCountVisible = permissions.permissions.gcCount;
               this.gcTimeVisible = permissions.permissions.gcTime;
               this.memoryCacheVisible = permissions.permissions.memoryCache;
               this.executionVisible = permissions.permissions.execution;
               this.diskCacheVisible = permissions.permissions.diskCache;
               this.swappingVisible = permissions.permissions.swapping;
               this.top5UsersVisible = permissions.permissions.top5Users;

               this.selectedNodeChange();
            })
         )
         .subscribe();

      this.refreshLegends();

      this.subscriptions.add(this.monitoringDataService.connect("/server/charts")
         .subscribe((serverModel: ServerModel) => {
            this.serverModel = serverModel;
            this.refreshLegends();
         }));

      this.subscriptions.add(this.sessionInactivity.onInactivity()
         .subscribe((remainingTime)=>{
            this.sessionExpirationDialogRef = this.dialog.open(SessionExpirationDialog, {
               width: "500px",
               data: {
                  remainingTime: remainingTime,
               }
            });

            this.sessionExpirationDialogRef.componentInstance.onLogout.subscribe(() => {
               this.logoutService.logout(false, true);
            });

            this.sessionExpirationDialogRef.componentInstance.onTimerFinished.subscribe(() => {
               this.logoutService.logout(false, true);
            });
         }));

      this.subscriptions.add(this.sessionInactivity.onActivity().subscribe(() => {
         if(this.sessionExpirationDialogRef != null) {
            this.sessionExpirationDialogRef.close();
            this.sessionExpirationDialogRef = null;
         }
      }));
   }

   private refreshLegends(): void {
      this.http.get<ServerSummaryModel>("../em/monitoring/server/summary")
         .subscribe((model) => {
            this.versionNumber = model.versionNumber;
            this.buildNumber = model.buildNumber;
            this.currentNode = model.currentNode;
            this.jvmModel = model.jvmModel;
            this.reverseProxyModel = model.reverseProxyModel;
            this.memoryCacheLegends = model.legends.memCache;
            this.swappingLegends = model.legends.swapping;
            this.diskCacheLegends = model.legends.diskCache;
            this.executionLegends = model.legends.execution;
            this.memLegends = model.legends.memUsage;
            this.cpuLegends = model.legends.cpuUsage;
            this.gcCountLegends = model.legends.gcCount;
            this.gcTimeLegends = model.legends.gcTime;
         });
   }

   selectedNodeChange() {
      this.http.get<SecurityEnabledEvent>("../api/em/security/get-enable-security")
          .subscribe(event => {
             this.securitySettingsEnabled = event.enable;

             if(this.securitySettingsEnabled) {
                this.top5Subscription.unsubscribe();
                const usersUri = "/user/get-top-five-users-grid/" + this.selectedClusterNode;
                this.top5Subscription = this.monitoringDataService.connect(usersUri)
                                            .subscribe((top5UsersModel: string[][]) => {
                                               this.top5Users = top5UsersModel;
                                            });
             }
          });
   }

   ngAfterContentChecked() {
      this.getGridCols();
   }

   ngOnDestroy() {
      this.subscriptions.unsubscribe();
      this.top5Subscription.unsubscribe();
   }

   get selectedClusterNode(): string {
      return this.monitoringDataService.cluster || "";
   }

   set selectedClusterNode(cluster: string) {
      this.monitoringDataService.cluster = cluster;
   }

   getGridCols() {
      if(this.pageContainer) {
         const bounds = this.pageContainer.nativeElement.getBoundingClientRect();
         this.cols = bounds && bounds.width < SMALL_WIDTH_BREAKPOINT ? 1 : 2;
      }
   }

   getThreadDump() {
      let url = "../em/monitoring/server/get-thread-dump";

      if(this.clusterEnabled && this.selectedClusterNode) {
         url += "?clusterNode=" + encodeURIComponent(this.selectedClusterNode);
      }

      this.downloadService.download(url);
   }

   getHeapDump() {
      const url = "../api/em/monitoring/server/get-heap-dump";
      const body = <HeapDumpRequest>{
         clusterNode: this.clusterEnabled && this.selectedClusterNode ? this.selectedClusterNode : null
      };

      const storagePath = this.serverModel == null ?
         "_#(js:em.confirm.heapDump.storageLocation)" : this.serverModel.externalStoragePath;

      this.dialog.open(MessageDialog, {
         width: "500px",
         data: {
            title: "_#(js:Confirm)",
            content: "_#(js:em.confirm.heapDump.prefix)" + storagePath +"_#(js:em.confirm.heapDump.suffix)",
            type: MessageDialogType.CONFIRMATION
         }
      }).afterClosed().subscribe(value => {
         if(value) {
            this.http.post(url, body)
               .subscribe(() => {});
         }
      });
   }

   getUsageHistory() {
      let url = "../em/monitoring/server/get-usage-history";

      if(this.clusterEnabled && this.selectedClusterNode) {
         url += "?clusterNode=" + encodeURIComponent(this.selectedClusterNode);
      }

      this.downloadService.download(url);
   }

   get clusterEnabled() {
      return this.clusterNodes && this.clusterNodes.length > 0;
   }

   handleLinks(link: ChartInfo) {
      switch(link.link) {
      case "viewsheets":
         this.router.navigate(["/monitoring/viewsheets"]);
         break;
      case "cache":
         this.router.navigate(["/monitoring/cache"], {queryParams: {cache: link.text}});
         break;
      case "queries":
         this.router.navigate(["/monitoring/queries"]);
         break;
      case "users":
         this.router.navigate(["/monitoring/users"]);
         break;
      }
   }

   getChartLink(imageId: string): string {
      return ["swapping", "memCache", "diskCache"].includes(imageId) ? "cache" : null;
   }

   get serverUpTime(): string {
      let upTime: string;

      if(this.serverModel) {
         upTime = this.clusterEnabled ? this.serverModel.serverUpTimeMap[this.selectedClusterNode]
            : this.serverModel.serverUpTimeMap["local"];
      }

      return upTime ? upTime : "00:00:00";
   }

   get serverTime(): string {
      let serverTime = "";

      if(this.serverModel) {
         const node = this.clusterEnabled ? this.selectedClusterNode : "local";
         serverTime = this.serverModel.serverDateTimeMap[node];
      }

      return serverTime;
   }

   get schedulerUpTime(): string {
      let upTime: string;

      if(this.serverModel) {
         upTime = this.clusterEnabled ? this.serverModel.schedulerUpTimeMap[this.selectedClusterNode]
            : this.serverModel.schedulerUpTimeMap["local"];
      }

      return upTime ? upTime : "00:00:00";
   }
}
