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
import { TableViewModule } from "../../common/util/table/table-view.module";
import { MatButtonModule } from "@angular/material/button";
import { MatCardModule } from "@angular/material/card";
import { MatIconModule } from "@angular/material/icon";
import { ClusterMonitoringPageComponent } from "./cluster-monitoring-page/cluster-monitoring-page.component";
import { ClusterRoutingModule } from "./cluster-routing.module";
import { ClusterMonitoringViewComponent } from "./cluster-monitoring-view/cluster-monitoring-view.component";

@NgModule({
   imports: [
      CommonModule,
      MatCardModule,
      MatIconModule,
      TableViewModule,
      MatButtonModule,
      ClusterRoutingModule,
   ],
   declarations: [
      ClusterMonitoringPageComponent,
      ClusterMonitoringViewComponent
   ]
})
export class ClusterModule {
}
