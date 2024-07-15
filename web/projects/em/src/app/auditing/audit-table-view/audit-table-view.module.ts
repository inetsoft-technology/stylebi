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
import { CommonModule } from "@angular/common";
import { NgModule } from "@angular/core";
import { FormsModule } from "@angular/forms";
import { MatButtonModule } from "@angular/material/button";
import { MatCardModule } from "@angular/material/card";
import { MatPaginatorModule } from "@angular/material/paginator";
import { MatSliderModule } from "@angular/material/slider";
import { MatSortModule } from "@angular/material/sort";
import { MatTableModule } from "@angular/material/table";
import { AuditTableViewComponent } from "./audit-table-view.component";
import {LoadingSpinnerModule} from "../../common/util/loading-spinner/loading-spinner.module";

@NgModule({
   declarations: [
      AuditTableViewComponent
   ],
   imports: [
      CommonModule,
      MatCardModule,
      MatSliderModule,
      FormsModule,
      MatTableModule,
      MatPaginatorModule,
      LoadingSpinnerModule,
      MatButtonModule,
      MatSortModule
   ],
   exports: [
      AuditTableViewComponent
   ]
})
export class AuditTableViewModule {
}
