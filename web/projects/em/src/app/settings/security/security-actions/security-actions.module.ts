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
import { MatButtonModule } from "@angular/material/button";
import { MatCardModule } from "@angular/material/card";
import { MatDialogModule } from "@angular/material/dialog";
import { MatIconModule } from "@angular/material/icon";
import { MatSidenavModule } from "@angular/material/sidenav";
import { MatSnackBarModule } from "@angular/material/snack-bar";
import { MatTreeModule } from "@angular/material/tree";
import { EditorPanelModule } from "../../../common/util/editor-panel/editor-panel.module";
import { MessageDialogModule } from "../../../common/util/message-dialog.module";
import { TopScrollModule } from "../../../top-scroll/top-scroll.module";
import { ResourcePermissionModule } from "../resource-permission/resource-permission.module";
import { SecurityActionsPageComponent } from "./security-actions-page/security-actions-page.component";
import { SecurityActionsSaveGuard } from "./security-actions-page/security-actions-save.guard";
import { SecurityActionsPermissionsComponent } from "./security-actions-permissions/security-actions-permissions.component";
import { SecurityActionsRoutingModule } from "./security-actions-routing.module";
import { SecurityActionsTreeComponent } from "./security-actions-tree/security-actions-tree.component";

@NgModule({
   imports: [
      CommonModule,
      MatButtonModule,
      MatCardModule,
      MatIconModule,
      MatSnackBarModule,
      MatTreeModule,
      EditorPanelModule,
      SecurityActionsRoutingModule,
      ResourcePermissionModule,
      MatSidenavModule,
      MatDialogModule,
      MessageDialogModule,
      TopScrollModule,
   ],
   declarations: [
      SecurityActionsPageComponent,
      SecurityActionsTreeComponent,
      SecurityActionsPermissionsComponent
   ],
   providers: [SecurityActionsSaveGuard]
})
export class SecurityActionsModule {
}
