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
import { LayoutModule } from "@angular/cdk/layout";
import { CommonModule } from "@angular/common";
import { NgModule } from "@angular/core";
import { FormsModule } from "@angular/forms";
import { MatButtonModule } from "@angular/material/button";
import { MatFormFieldModule } from "@angular/material/form-field";
import { MatIconModule } from "@angular/material/icon";
import { MatInputModule } from "@angular/material/input";
import { MatListModule } from "@angular/material/list";
import { MatSidenavModule } from "@angular/material/sidenav";
import { MatToolbarModule } from "@angular/material/toolbar";
import { MessageDialogModule } from "../common/util/message-dialog.module";
import { PageHeaderModule } from "../page-header/page-header.module";
import { SearchModule } from "../search/search.module";
import { TopScrollModule } from "../top-scroll/top-scroll.module";
import { MonitorLevelService } from "./monitor-level.service";
import { MonitoringDataService } from "./monitoring-data.service";
import { MonitoringRoutingModule } from "./monitoring-routing.module";
import { MonitoringSidenavComponent } from "./monitoring-sidenav/monitoring-sidenav.component";

@NgModule({
   imports: [
      CommonModule,
      FormsModule,
      LayoutModule,
      SearchModule,
      MatButtonModule,
      MatFormFieldModule,
      MatIconModule,
      MatInputModule,
      MatListModule,
      MatSidenavModule,
      MatToolbarModule,
      MessageDialogModule,
      MonitoringRoutingModule,
      PageHeaderModule,
      TopScrollModule
   ],
   declarations: [
      MonitoringSidenavComponent
   ],
   providers: [
      MonitoringDataService,
      MonitorLevelService
   ]
})
export class MonitoringModule {
}
