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
import {Component, OnDestroy, ViewEncapsulation} from "@angular/core";
import {Observable, Subscription} from "rxjs";
import {MatDialog} from "@angular/material/dialog";
import {ContextHelp} from "../../../context-help";
import {PageHeaderService} from "../../../page-header/page-header.service";
import {Secured} from "../../../secured";
import {MonitoringDataService} from "../../monitoring-data.service";
import {ThreadStackTrace} from "../../thread-stack-trace";
import {QueryMonitoringTableModel} from "../query-monitoring-table-model";
import {HttpClient} from "@angular/common/http";
import {Searchable} from "../../../searchable";
import {AuthorizationService} from "../../../authorization/authorization.service";
import {MonitorLevel, MonitorLevelService} from "../../monitor-level.service";
import {ColumnInfo} from "../../../common/util/table/column-info";
import {MessageDialog, MessageDialogType} from "../../../common/util/message-dialog";
import {ExpandableRowTableInfo} from "../../../common/util/table/expandable-row-table/expandable-row-table-info";
import {Tool} from "../../../../../../shared/util/tool";

@Secured({
   route: "/monitoring/queries",
   label: "Queries",
   children: [
      { route: "/monitoring/queries/executing", label: "Executing" }
   ]
})
@Searchable({
   title: "Query Monitoring",
   route: "/monitoring/queries",
   keywords: ["em.keyword.query", "em.keyword.monitoring", "em.keyword.executing"]
})
@ContextHelp({
   route: "/monitoring/queries",
   link: "EMMonitoringQueries"
})
@Component({
   selector: "em-query-monitoring-page",
   templateUrl: "./query-monitoring-page.component.html",
   styleUrls: ["./query-monitoring-page.component.scss"],
   encapsulation: ViewEncapsulation.None,
   host: { "class": "em-query-monitoring-page" } // eslint-disable-line @angular-eslint/no-host-metadata-property
})
export class QueryMonitoringPageComponent implements OnDestroy {
   executingTableInfo: ExpandableRowTableInfo;
   executingColumnsInfo: ColumnInfo[] = [
      {header: "_#(js:Name)", field: "name"},
      {header: "_#(js:User)", field: "user"},
      {header: "_#(js:Task)", field: "task"},
      {header: "_#(js:Thread)", field: "thread"},
      {header: "_#(js:Asset)", field: "asset"},
      {header: "_#(js:Rows)", field: "rows", level: MonitorLevel.MEDIUM},
      {header: "_#(js:Age)", field: "age"}
   ];
   executingMediumDeviceHeaders: ColumnInfo[] = [
      {header: "_#(js:Name)", field: "name"},
      {header: "_#(js:User)", field: "user"}
   ];
   executingVisible = false;
   executingQueries: Observable<QueryMonitoringTableModel[]>;
   executingQueriesCount = 0;
   monitorLevel: number;
   private subscriptions = new Subscription();

   constructor(private pageTitle: PageHeaderService,
               private monitoringDataService: MonitoringDataService,
               private monitorLevelService: MonitorLevelService,
               private http: HttpClient,
               private authzService: AuthorizationService,
               private dialog: MatDialog)
   {
      this.authzService.getPermissions("monitoring/queries").subscribe((p) => {
         this.executingVisible = p.permissions.executing;
      });

      this.monitorLevelService.monitorLevel().subscribe((level) => {
         this.monitorLevel = level;

         this.executingTableInfo = {
            selectionEnabled: true,
            title: "_#(js:Query Monitoring)",
            columns: this.monitorLevelService.filterColumns(this.executingColumnsInfo),
            mediumDeviceHeaders: this.monitorLevelService.filterColumns(this.executingMediumDeviceHeaders)
         };
      });

      this.executingQueries = this.monitoringDataService.getMonitoringData("/queries/executing");

      this.subscriptions.add(this.executingQueries.subscribe((data) => {
            this.executingQueriesCount = !!data ? data.length : 0;
            this.updateExecutingInfo();
         }
      ));

      pageTitle.title = "_#(js:Query Monitoring)";
   }

   private updateExecutingInfo(): void {
      let info = Tool.clone(this.executingTableInfo);
      let t = "_#(js:Executing Queries):" + this.executingQueriesCount;

      info.title = t;
      this.executingTableInfo = info;
   }

   ngOnDestroy() {
      this.subscriptions.unsubscribe();
   }

   removeQuery(selected: QueryMonitoringTableModel[]) {
      let ids: string[] = [];

      for(let query of selected) {
         ids.push(query.id);
      }

      this.http.post("../em/monitoring/queries/remove/"
         + this.monitoringDataService.nonNullCluster, ids).subscribe((data: any) => {
            this.monitoringDataService.refresh();
         });
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
