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
import { Component, OnDestroy, OnInit, ViewEncapsulation } from "@angular/core";
import { ActivatedRoute } from "@angular/router";
import { Subscription } from "rxjs";
import { AuthorizationService } from "../../../authorization/authorization.service";
import { ColumnInfo } from "../../../common/util/table/column-info";
import { TableInfo } from "../../../common/util/table/table-info";
import { ContextHelp } from "../../../context-help";
import { PageHeaderService } from "../../../page-header/page-header.service";
import { Searchable } from "../../../searchable";
import { Secured } from "../../../secured";
import { MonitorLevel, MonitorLevelService } from "../../monitor-level.service";
import { MonitoringDataService } from "../../monitoring-data.service";
import { CacheMonitoringTableModel } from "../cache-monitoring-table-model";

@Secured({
   route: "/monitoring/cache",
   label: "Cache",
   children: [
      { route: "/monitoring/cache/data", label: "Data" }
   ],
   hiddenForMultiTenancy: true
})
@Searchable({
   route: "/monitoring/cache",
   title: "_#(js:em.cacheMonitoring.title)",
   keywords: ["em.keyword.monitoring", "em.keyword.cache", "em.keyword.data"]
})
@ContextHelp({
   route: "/monitoring/cache",
   link: "EMMonitoringCache"
})
@Component({
   selector: "em-cache-monitoring-page",
   templateUrl: "./cache-monitoring-page.component.html",
   styleUrls: ["./cache-monitoring-page.component.scss"],
   encapsulation: ViewEncapsulation.None,
   host: { "class": "em-cache-monitoring-page" } // eslint-disable-line @angular-eslint/no-host-metadata-property
})
export class CacheMonitoringPageComponent implements OnInit, OnDestroy {
   dataGridVisible = false;
   dataGrid: CacheMonitoringTableModel[];
   dataTableInfo: TableInfo;
   dataTableColumns: ColumnInfo[] = [
      {field: "location", header: "_#(js:Cache Location)"},
      {field: "count", header: "_#(js:Data Objects)"},
      {field: "hits", header: "_#(js:Hits/Misses)", level: MonitorLevel.HIGH},
      {field: "read", header: "_#(js:Swapped)", level: MonitorLevel.MEDIUM}
   ];

   private subscriptions = new Subscription();

   constructor(private pageTitle: PageHeaderService,
               private route: ActivatedRoute,
               private monitoringDataService: MonitoringDataService,
               private monitorLevelService: MonitorLevelService,
               private authzService: AuthorizationService)
   {
      this.subscriptions.add(
         this.monitoringDataService.getMonitoringData("/cache/getDataGrid/")
            .subscribe((value) => this.dataGrid = value));

      this.monitorLevelService.monitorLevel().subscribe(() => {
         this.dataTableInfo = {
            selectionEnabled: false,
            title: "_#(js:Data):",
            columns: this.monitorLevelService.filterColumns(this.dataTableColumns)
         };
      });
   }

   ngOnInit() {
      this.pageTitle.title = "_#(js:em.cacheMonitoring.title)";

      this.authzService.getPermissions("monitoring/cache").subscribe((p) => {
         this.dataGridVisible = p.permissions.data;
      });
   }

   ngOnDestroy() {
      this.subscriptions.unsubscribe();
   }

   get selectedNode(): string {
      return this.monitoringDataService.cluster ? this.monitoringDataService.cluster : "";
   }

   set selectedNode(cluster: string) {
      this.monitoringDataService.cluster = cluster;
   }

   isExpanded(chart: string): boolean {
      let link: string = this.route.snapshot.queryParamMap.get("cache")
         ? this.route.snapshot.queryParamMap.get("cache").toLowerCase()
         : "";

      return (link === "" || link === "cache" || link === "read" || link === "written") ?
         true : link.includes(chart);
   }
}
