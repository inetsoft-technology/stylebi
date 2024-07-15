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
import { FormsModule, ReactiveFormsModule } from "@angular/forms";
import { MatOptionModule } from "@angular/material/core";
import { MatFormFieldModule } from "@angular/material/form-field";
import { MatSelectModule } from "@angular/material/select";
import { AuditTableViewModule } from "../audit-table-view/audit-table-view.module";
import { AuditRequiredAssetsRoutingModule } from "./audit-required-assets-routing.module";
import { AuditRequiredAssetsComponent } from "./audit-required-assets.component";

@NgModule({
   declarations: [
      AuditRequiredAssetsComponent
   ],
   imports: [
      CommonModule,
      AuditRequiredAssetsRoutingModule,
      AuditTableViewModule,
      FormsModule,
      MatFormFieldModule,
      MatOptionModule,
      MatSelectModule,
      ReactiveFormsModule
   ]
})
export class AuditRequiredAssetsModule {
}
