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
import { GraphModule } from "../../../graph/graph.module";
import { WidgetDirectivesModule } from "../../../widget/directive/widget-directives.module";
import { TooltipModule } from "../../../widget/tooltip/tooltip.module";
import { DataTipDirectivesModule } from "../data-tip/data-tip-directives.module";
import { PreviewTableModule } from "../table/preview-table.module";
import { VSTitleModule } from "../title/vs-title.module";
import { VSLoadingDisplayModule } from "../vs-loading-display/vs-loading-display.module";
import { VSChart } from "./vs-chart.component";
import { VSLineModule } from "../shape/vs-line.module";

@NgModule({
   imports: [
      CommonModule,
      VSTitleModule,
      GraphModule,
      PreviewTableModule,
      VSLoadingDisplayModule,
      DataTipDirectivesModule,
      WidgetDirectivesModule,
      TooltipModule,
      VSLineModule
   ],
   declarations: [
      VSChart
   ],
   exports: [
      VSChart
   ],
   providers: [],
})
export class VSChartModule {
}
