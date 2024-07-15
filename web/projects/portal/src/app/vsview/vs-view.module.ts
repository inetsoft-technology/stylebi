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
import { FormsModule } from "@angular/forms";
import { BindingModule } from "../binding/binding.module";
import { VSChartModule } from "../vsobjects/objects/chart/vs-chart.module";
import {
   VSLoadingDisplayModule
} from "../vsobjects/objects/vs-loading-display/vs-loading-display.module";
import { VSObjectModule } from "../vsobjects/vs-object.module";
import { NotificationsModule } from "../widget/notifications/notifications.module";
import { BCalcTableActionHandlerDirective } from "./action/b-calctable-action-handler.directive";
import { BCrosstabActionHandlerDirective } from "./action/b-crosstab-action-handler.directive";
import { BTableActionHandlerDirective } from "./action/b-table-action-handler.directive";
import { VSBindingPane } from "./edit/vs-binding-pane.component";
import { CalcTableCellComponent } from "./view/calc-table-cell.component";
import { CalcTableLayoutPane } from "./view/vs-calc-table-layout.component";
import { VSObjectView } from "./view/vs-object-view.component";
import { FixedDropdownModule } from "../widget/fixed-dropdown/fixed-dropdown.module";
import { InteractModule } from "../widget/interact/interact.module";
import { WidgetDirectivesModule } from "../widget/directive/widget-directives.module";
import { MiniToolbarModule } from "../vsobjects/objects/mini-toolbar/mini-toolbar.module";

@NgModule({
   imports: [
      CommonModule,
      FormsModule,
      BindingModule,
      VSObjectModule,
      VSChartModule,
      VSLoadingDisplayModule,
      FixedDropdownModule,
      InteractModule,
      NotificationsModule,
      WidgetDirectivesModule,
      MiniToolbarModule
   ],
   declarations: [
      CalcTableCellComponent,
      CalcTableLayoutPane,
      VSBindingPane,
      VSObjectView,
      BCalcTableActionHandlerDirective,
      BCrosstabActionHandlerDirective,
      BTableActionHandlerDirective
   ],
   exports: [
      CalcTableCellComponent,
      CalcTableLayoutPane,
      VSBindingPane,
      VSObjectView,
      BCalcTableActionHandlerDirective,
      BCrosstabActionHandlerDirective,
      BTableActionHandlerDirective
   ]
})
export class VSViewModule {
}