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
import { MatButtonModule } from "@angular/material/button";
import { MatCardModule } from "@angular/material/card";
import { MatDialogModule } from "@angular/material/dialog";
import { MatIconModule } from "@angular/material/icon";
import { MatSelectModule } from "@angular/material/select";
import { NgModule } from "@angular/core";
import { UsersRoutingModule } from "./users-routing.module";
import { UserMonitoringViewComponent } from "./user-monitoring-view/user-monitoring-view.component";
import { TableViewModule } from "../../common/util/table/table-view.module";
import { CollapsibleContainerModule } from "../collapsible-container/collapsible-container.module";
import { ClusterNodesService } from "../cluster/cluster-nodes.service";
import { ClusterSelectorModule } from "../cluster-selector/cluster-selector.module";

@NgModule({
   imports: [
      CommonModule,
      MatButtonModule,
      MatSelectModule,
      MatCardModule,
      MatDialogModule,
      MatIconModule,
      UsersRoutingModule,
      TableViewModule,
      CollapsibleContainerModule,
      ClusterSelectorModule,
   ],
   providers: [
      ClusterNodesService
   ],
   declarations: [
      UserMonitoringViewComponent
   ]
})
export class UsersModule {
}
