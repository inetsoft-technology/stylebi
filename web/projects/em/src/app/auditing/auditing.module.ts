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
import { MatFormFieldModule } from "@angular/material/form-field";
import { MatIconModule } from "@angular/material/icon";
import { MatInputModule } from "@angular/material/input";
import { MatListModule } from "@angular/material/list";
import { MatSidenavModule } from "@angular/material/sidenav";
import { MatSnackBarModule } from "@angular/material/snack-bar";
import { ErrorHandlerService } from "../common/util/error/error-handler.service";
import { PageHeaderModule } from "../page-header/page-header.module";
import { TopScrollModule } from "../top-scroll/top-scroll.module";
import { AuditingRoutingModule } from "./auditing-routing.module";
import { AuditingSidenavComponent } from "./auditing-sidenav/auditing-sidenav.component";

@NgModule({
   declarations: [
      AuditingSidenavComponent
   ],
   imports: [
      CommonModule,
      AuditingRoutingModule,
      FormsModule,
      MatButtonModule,
      MatFormFieldModule,
      MatIconModule,
      MatInputModule,
      MatListModule,
      MatSidenavModule,
      PageHeaderModule,
      TopScrollModule,
      MatCardModule,
      MatSnackBarModule
   ],
   providers: [
      ErrorHandlerService
   ]
})
export class AuditingModule {
}
