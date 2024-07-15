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
import { MatDialogModule } from "@angular/material/dialog";
import { MatIconModule } from "@angular/material/icon";
import { ModalHeaderModule } from "../../../common/util/modal-header/modal-header.module";
import { SecurityTreeViewModule } from "../security-tree-view/security-tree-view.module";
import { SecurityTreeDialogComponent } from "./security-tree-dialog.component";

@NgModule({
   imports: [
      CommonModule,
      FormsModule,
      MatButtonModule,
      MatIconModule,
      SecurityTreeViewModule,
      MatDialogModule,
      ModalHeaderModule
   ],
   declarations: [
      SecurityTreeDialogComponent
   ],
   exports: [
      SecurityTreeDialogComponent
   ]
})
export class SecurityTreeDialogModule {
}