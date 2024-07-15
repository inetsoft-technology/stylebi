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
import { CommonModule } from "@angular/common";
import { NgModule } from "@angular/core";
import { QueriesRoutingModule } from "./queries-routing.module";
import { QueryMonitoringViewComponent } from "./query-monitoring-view/query-monitoring-view.component";
import { TableViewModule } from "../../common/util/table/table-view.module";
import { MatButtonModule } from "@angular/material/button";
import { MatCardModule } from "@angular/material/card";
import { MatSelectModule } from "@angular/material/select";
import { QueryMonitoringPageComponent } from "./query-monitoring-page/query-monitoring-page.component";
import { ClusterNodesService } from "../cluster/cluster-nodes.service";
import { ClusterSelectorModule } from "../cluster-selector/cluster-selector.module";

@NgModule({
   imports: [
      CommonModule,
      MatCardModule,
      MatSelectModule,
      TableViewModule,
      MatButtonModule,
      QueriesRoutingModule,
      ClusterSelectorModule,
   ],
   declarations: [
      QueryMonitoringViewComponent,
      QueryMonitoringPageComponent
   ],
   providers: [
      ClusterNodesService
   ]
})
export class QueriesModule {
}
