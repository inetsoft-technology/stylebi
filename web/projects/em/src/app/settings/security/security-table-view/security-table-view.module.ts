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
import { MatButtonModule } from "@angular/material/button";
import { MatCardModule } from "@angular/material/card";
import { MatCheckboxModule } from "@angular/material/checkbox";
import { MatIconModule } from "@angular/material/icon";
import { MatSortModule } from "@angular/material/sort";
import { MatTableModule } from "@angular/material/table";
import { FormsModule } from "@angular/forms";
import { CommonModule } from "@angular/common";
import { NgModule } from "@angular/core";
import { SecurityTableViewComponent } from "./security-table-view.component";
import { SecurityTreeDialogModule } from "../security-tree-dialog/security-tree-dialog.module";
import { MatPaginatorModule } from "@angular/material/paginator";
@NgModule({
   imports: [
      CommonModule,
      FormsModule,
      MatTableModule,
      MatSortModule,
      MatButtonModule,
      MatCheckboxModule,
      MatCardModule,
      MatIconModule,
      SecurityTreeDialogModule,
      MatPaginatorModule
   ],
   exports: [
      SecurityTableViewComponent
   ],
   declarations: [
      SecurityTableViewComponent
   ]
})
export class SecurityTableViewModule {
}
