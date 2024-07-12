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
import { CommonModule } from "@angular/common";
import { NgModule } from "@angular/core";
import { AngularResizeEventModule } from "../../../../../shared/resize-event/angular-resize-event.module";
import { SummaryRoutingModule } from "./summary-routing.module";
import { MatButtonModule } from "@angular/material/button";
import { MatCardModule } from "@angular/material/card";
import { MatDividerModule } from "@angular/material/divider";
import { MatGridListModule } from "@angular/material/grid-list";
import { MatSelectModule } from "@angular/material/select";
import { TableViewModule } from "../../common/util/table/table-view.module";
import { SummaryMonitoringPageComponent } from "./summary-monitoring-page/summary-monitoring-page.component";
import { SummaryMonitoringChartViewComponent } from "./summary-monitoring-view/summary-monitoring-chart-view/summary-monitoring-chart-view.component";
import { SummaryMonitoringTableViewComponent } from "./summary-monitoring-view/summary-monitoring-table-view/summary-monitoring-table-view.component";
import { ClusterNodesService } from "../cluster/cluster-nodes.service";
import { ClusterSelectorModule } from "../cluster-selector/cluster-selector.module";

@NgModule({
   imports: [
      CommonModule,
      SummaryRoutingModule,
      MatButtonModule,
      MatSelectModule,
      MatCardModule,
      MatGridListModule,
      TableViewModule,
      MatDividerModule,
      ClusterSelectorModule,
      AngularResizeEventModule
   ],
   declarations: [
      SummaryMonitoringPageComponent,
      SummaryMonitoringChartViewComponent,
      SummaryMonitoringTableViewComponent,
   ],
   providers: [
      ClusterNodesService
   ]
})
export class SummaryModule {
}
