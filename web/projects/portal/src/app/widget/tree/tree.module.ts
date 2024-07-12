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
import { FormsModule } from "@angular/forms";
import { WidgetDirectivesModule } from "../directive/widget-directives.module";
import { TooltipModule } from "../tooltip/tooltip.module";
import { TreeDropdownComponent } from "./tree-dropdown.component";
import { TreeNodeComponent } from "./tree-node.component";
import { TreeSearchPipe } from "./tree-search.pipe";
import { TreeComponent } from "./tree.component";
import { VirtualScrollTreeComponent } from "./virtual-scroll-tree/virtual-scroll-tree.component";
import {FixedDropdownModule} from "../fixed-dropdown/fixed-dropdown.module";

@NgModule({
   imports: [
      CommonModule,
      FormsModule,
      TooltipModule,
      FixedDropdownModule,
      WidgetDirectivesModule,
   ],
   declarations: [
      VirtualScrollTreeComponent,
      TreeComponent,
      TreeDropdownComponent,
      TreeNodeComponent,
      TreeSearchPipe
   ],
   exports: [
      VirtualScrollTreeComponent,
      TreeComponent,
      TreeDropdownComponent,
      TreeNodeComponent,
      TreeSearchPipe
   ],
   providers: [],
})
export class TreeModule {
}
