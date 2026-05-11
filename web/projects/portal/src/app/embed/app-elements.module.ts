/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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

import { ApplicationRef, DoBootstrap, NgModule } from "@angular/core";
import { AppRoutingModule } from "../app-routing.module";
import { EmbedChartModule } from "./chart/embed-chart.module";
import { EmbedCrosstabModule } from "./crosstab/embed-crosstab.module";
import { EmbedTableModule } from "./table/embed-table.module";
import { EmbedGaugeModule } from "./gauge/embed-gauge.module";
import { EmbedImageModule } from "./image/embed-image.module";
import { EmbedTextModule } from "./text/embed-text.module";
import { AppBaseElementModule } from "./app-base-element.module";

@NgModule({
   imports: [
      AppBaseElementModule,
      AppRoutingModule,
      EmbedChartModule,
      EmbedCrosstabModule,
      EmbedTableModule,
      EmbedGaugeModule,
      EmbedImageModule,
      EmbedTextModule
   ],
   providers: [],
})
export class AppElementsModule implements DoBootstrap {
   ngDoBootstrap(appRef: ApplicationRef): void {
   }
}
