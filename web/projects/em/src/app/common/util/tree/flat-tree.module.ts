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
import { ScrollingModule } from "@angular/cdk/scrolling";
import { CommonModule } from "@angular/common";
import { NgModule } from "@angular/core";
import { MatButtonModule } from "@angular/material/button";
import { MatIconModule } from "@angular/material/icon";
import { MatMenuModule } from "@angular/material/menu";
import { MatTreeModule } from "@angular/material/tree";
import { FlatTreeViewComponent } from "./flat-tree-view.component";
import { MultiSelectTreeNodeDirective } from "./multi-select-tree-node.directive";

@NgModule({
    imports: [
        CommonModule,
        MatButtonModule,
        MatTreeModule,
        MatIconModule,
        ScrollingModule,
        MatMenuModule
    ],
   declarations: [
      FlatTreeViewComponent,
      MultiSelectTreeNodeDirective
   ],
   exports: [
      FlatTreeViewComponent,
      MultiSelectTreeNodeDirective
   ]
})
export class FlatTreeModule {
}
