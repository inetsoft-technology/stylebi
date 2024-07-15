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
import { TableView } from "./table-view.component";
import { CommonModule } from "@angular/common";
import { MatButtonModule } from "@angular/material/button";
import { MatCardModule } from "@angular/material/card";
import { MatCheckboxModule } from "@angular/material/checkbox";
import { MatExpansionModule } from "@angular/material/expansion";
import { MatFormFieldModule } from "@angular/material/form-field";
import { MatListModule } from "@angular/material/list";
import { MatSortModule } from "@angular/material/sort";
import { MatTableModule } from "@angular/material/table";
import { NgModule } from "@angular/core";
import { FormsModule } from "@angular/forms";
import { CollapsibleContainerModule } from "../../../monitoring/collapsible-container/collapsible-container.module";
import { RegularTableComponent } from "./regular-table/regular-table.component";
import { ExpandableRowTableComponent } from "./expandable-row-table/expandable-row-table.component";

@NgModule({
   imports: [
      CommonModule,
      FormsModule,
      MatTableModule,
      MatSortModule,
      MatButtonModule,
      MatCardModule,
      MatCheckboxModule,
      MatExpansionModule,
      MatFormFieldModule,
      CollapsibleContainerModule,
      MatListModule
   ],
   exports: [
      TableView
   ],
   declarations: [
      TableView,
      RegularTableComponent,
      ExpandableRowTableComponent
   ]
})
export class TableViewModule {
}