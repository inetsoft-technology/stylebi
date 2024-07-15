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
import { ReactiveFormsModule } from "@angular/forms";
import { MatButtonModule } from "@angular/material/button";
import { MatCardModule } from "@angular/material/card";
import { MatCheckboxModule } from "@angular/material/checkbox";
import { MatDialogModule } from "@angular/material/dialog";
import { MatFormFieldModule } from "@angular/material/form-field";
import { MatIconModule } from "@angular/material/icon";
import { MatInputModule } from "@angular/material/input";
import { MatListModule } from "@angular/material/list";
import { MatProgressBarModule } from "@angular/material/progress-bar";
import { MatSortModule } from "@angular/material/sort";
import { MatTableModule } from "@angular/material/table";
import { FileChooserModule } from "../../../common/util/file-chooser/file-chooser.module";
import { FlatTreeModule } from "../../../common/util/tree/flat-tree.module";
import { MessageDialogModule } from "../../../common/util/message-dialog.module";
import { ImportTaskDialogComponent } from "./import-task-dialog/import-task-dialog.component";
import { ExportTaskDialogComponent } from "./export-task-dialog/export-task-dialog.component";

@NgModule({
    imports: [
        CommonModule,
        ReactiveFormsModule,
        MatButtonModule,
        MatCardModule,
        MatCheckboxModule,
        MatDialogModule,
        MatFormFieldModule,
        MatIconModule,
        MatInputModule,
        MatListModule,
        MatProgressBarModule,
        MatSortModule,
        MatTableModule,
        FileChooserModule,
        FlatTreeModule,
        MessageDialogModule
    ],
    declarations: [
        ImportTaskDialogComponent,
        ExportTaskDialogComponent
    ],
    exports: [
        ImportTaskDialogComponent,
        ExportTaskDialogComponent
    ],
    providers: []
})
export class ImportExportTaskModule {
}
