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
import { DataTipDirectivesModule } from "../data-tip/data-tip-directives.module";
import { VSLine } from "./vs-line.component";
import { VSAnnotation } from "../annotation/vs-annotation.component";
import { VSHiddenAnnotation } from "../annotation/vs-hidden-annotation.component";
import { InteractModule } from "../../../widget/interact/interact.module";
import { WidgetDirectivesModule } from "../../../widget/directive/widget-directives.module";
import { TooltipModule } from "../../../widget/tooltip/tooltip.module";

@NgModule({
   imports: [
      CommonModule,
      DataTipDirectivesModule,
      InteractModule,
      WidgetDirectivesModule,
      TooltipModule,
   ],
   declarations: [
      VSLine,
      // to avoid circular dependency
      VSAnnotation,
      VSHiddenAnnotation
   ],
   exports: [
      VSLine,
      VSAnnotation,
      VSHiddenAnnotation
   ],
   providers: [],
})
export class VSLineModule {
}
