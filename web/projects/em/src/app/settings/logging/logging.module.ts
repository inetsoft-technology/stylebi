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
import { FormsModule, ReactiveFormsModule } from "@angular/forms";
import { MatButtonModule } from "@angular/material/button";
import { MatCardModule } from "@angular/material/card";
import { MatCheckboxModule } from "@angular/material/checkbox";
import { MatDialogModule } from "@angular/material/dialog";
import { MatDividerModule } from "@angular/material/divider";
import { MatFormFieldModule } from "@angular/material/form-field";
import { MatInputModule } from "@angular/material/input";
import { MatSelectModule } from "@angular/material/select";
import { EditorPanelModule } from "../../common/util/editor-panel/editor-panel.module";
import { MessageDialogModule } from "../../common/util/message-dialog.module";
import { ModalHeaderModule } from "../../common/util/modal-header/modal-header.module";
import { TableViewModule } from "../../common/util/table/table-view.module";
import { AddLoggingLevelDialogComponent } from "./add-logging-level-dialog/add-logging-level-dialog.component";
import { LoggingLevelTableComponent } from "./logging-level-table/logging-level-table.component";
import { LoggingRoutingModule } from "./logging-routing.module";
import { LoggingSaveGuard } from "./logging-settings-page/logging-save.guard";
import { LoggingSettingsPageComponent } from "./logging-settings-page/logging-settings-page.component";
import { LoggingSettingsViewComponent } from "./logging-settings-view/logging-settings-view.component";

@NgModule({
   imports: [
      CommonModule,
      FormsModule,
      ReactiveFormsModule,
      MatFormFieldModule,
      MatCheckboxModule,
      MatInputModule,
      MatButtonModule,
      MatSelectModule,
      MatCardModule,
      MatDialogModule,
      MatDividerModule,
      LoggingRoutingModule,
      EditorPanelModule,
      MessageDialogModule,
      TableViewModule,
      ModalHeaderModule
   ],
   declarations: [
      LoggingSettingsPageComponent,
      LoggingSettingsViewComponent,
      LoggingLevelTableComponent,
      AddLoggingLevelDialogComponent
   ],
   providers: [
      LoggingSaveGuard
   ]
})
export class LoggingModule {
}
