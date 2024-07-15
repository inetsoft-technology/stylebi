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
import { ScrollingModule } from "@angular/cdk/scrolling";
import { CommonModule } from "@angular/common";
import { NgModule } from "@angular/core";
import { MatButtonModule } from "@angular/material/button";
import { MatCardModule } from "@angular/material/card";
import { MatCheckboxModule } from "@angular/material/checkbox";
import { MatOptionModule } from "@angular/material/core";
import { MatDividerModule } from "@angular/material/divider";
import { MatIconModule } from "@angular/material/icon";
import { MatInputModule } from "@angular/material/input";
import { MatListModule } from "@angular/material/list";
import { MatSelectModule } from "@angular/material/select";
import { LogMonitoringPageComponent } from "./log-monitoring-page/log-monitoring-page.component";
import { LogMonitoringViewComponent } from "./log-monitoring-view/log-monitoring-view.component";
import { LogRoutingModule } from "./log-routing.module";

@NgModule({
   imports: [
      CommonModule,
      MatButtonModule,
      MatCheckboxModule,
      MatDividerModule,
      MatInputModule,
      MatListModule,
      MatOptionModule,
      MatSelectModule,
      ScrollingModule,
      LogRoutingModule,
      MatCardModule,
      MatIconModule
   ],
   declarations: [LogMonitoringViewComponent, LogMonitoringPageComponent]
})
export class LogModule {
}
