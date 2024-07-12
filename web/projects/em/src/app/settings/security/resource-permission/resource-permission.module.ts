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
import { NgModule } from "@angular/core";
import { CommonModule } from "@angular/common";
import { ResourcePermissionComponent } from "./resource-permission.component";
import { SecurityTreeViewModule } from "../security-tree-view/security-tree-view.module";
import { SecurityTableViewModule } from "../security-table-view/security-table-view.module";
import { PermissionsTableModule } from "../permissions-table/permissions-table.module";
import { MatButtonModule } from "@angular/material/button";
import { MatCardModule } from "@angular/material/card";
import { MatCheckboxModule } from "@angular/material/checkbox";
import { MatIconModule } from "@angular/material/icon";
import { MatRadioModule } from "@angular/material/radio";
import { SecurityTreeService } from "../users/security-tree.service";
import { FormsModule } from "@angular/forms";
import { SecurityTreeDialogModule } from "../security-tree-dialog/security-tree-dialog.module";

@NgModule({
   imports: [
      CommonModule,
      FormsModule,
      MatButtonModule,
      MatCardModule,
      MatCheckboxModule,
      MatIconModule,
      MatRadioModule,
      SecurityTreeViewModule,
      SecurityTableViewModule,
      PermissionsTableModule,
      SecurityTreeDialogModule,
   ],
   exports: [ResourcePermissionComponent],
   declarations: [ResourcePermissionComponent],
   providers: [SecurityTreeService]
})
export class ResourcePermissionModule {
}
