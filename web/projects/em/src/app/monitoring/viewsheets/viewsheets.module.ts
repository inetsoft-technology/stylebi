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
import { MatCardModule } from "@angular/material/card";
import { MatCheckboxModule } from "@angular/material/checkbox";
import { MatExpansionModule } from "@angular/material/expansion";
import { MatPaginatorModule } from "@angular/material/paginator";
import { MatSelectModule } from "@angular/material/select";
import { MatTableModule } from "@angular/material/table";
import { MatTabsModule } from "@angular/material/tabs";
import { TableViewModule } from "../../common/util/table/table-view.module";
import { ClusterSelectorModule } from "../cluster-selector/cluster-selector.module";
import { ClusterNodesService } from "../cluster/cluster-nodes.service";
import { CollapsibleContainerModule } from "../collapsible-container/collapsible-container.module";
import { ViewsheetMonitoringPageComponent } from "./viewsheet-monitoring-page/viewsheet-monitoring-page.component";
import { ViewsheetMonitoringViewComponent } from "./viewsheet-monitoring-view/viewsheet-monitoring-view.component";
import { ViewsheetsRoutingModule } from "./viewsheets-routing.module";

@NgModule({
   imports: [
      CommonModule,
      MatCardModule,
      MatCheckboxModule,
      MatExpansionModule,
      MatPaginatorModule,
      MatTableModule,
      MatSelectModule,
      CollapsibleContainerModule,
      ViewsheetsRoutingModule,
      TableViewModule,
      ClusterSelectorModule,
      MatTabsModule,
   ],
   declarations: [
      ViewsheetMonitoringPageComponent,
      ViewsheetMonitoringViewComponent
   ],
   providers: [
      ClusterNodesService
   ]
})
export class ViewsheetsModule {
}
