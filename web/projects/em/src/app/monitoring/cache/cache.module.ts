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
import { CommonModule } from "@angular/common";
import { NgModule } from "@angular/core";
import { CacheRoutingModule } from "./cache-routing.module";
import { CacheMonitoringViewComponent } from "./cache-monitoring-view/cache-monitoring-view.component";
import { MatCardModule } from "@angular/material/card";
import { MatSelectModule } from "@angular/material/select";
import { TableViewModule } from "../../common/util/table/table-view.module";
import { CollapsibleContainerModule } from "../collapsible-container/collapsible-container.module";
import { CacheMonitoringPageComponent } from "./cache-monitoring-page/cache-monitoring-page.component";
import { ClusterNodesService } from "../cluster/cluster-nodes.service";
import { ClusterSelectorModule } from "../cluster-selector/cluster-selector.module";

@NgModule({
   imports: [
      CommonModule,
      CacheRoutingModule,
      MatSelectModule,
      MatCardModule,
      TableViewModule,
      CollapsibleContainerModule,
      ClusterSelectorModule
   ],
   declarations: [
      CacheMonitoringViewComponent,
      CacheMonitoringPageComponent
   ],
   providers: [
      ClusterNodesService
   ]
})
export class CacheModule {
}
