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
import { HttpClient } from "@angular/common/http";
import { Component, HostListener, Inject, Input, ViewEncapsulation } from "@angular/core";
import { UntypedFormBuilder } from "@angular/forms";
import { MAT_DIALOG_DATA, MatDialog, MatDialogRef } from "@angular/material/dialog";
import { MatTableDataSource } from "@angular/material/table";
import { SelectionModel } from "@angular/cdk/collections";
import { TaskDependencyModel } from "../../model/task-dependency-model";

@Component({
   selector: "em-export-task-dialog",
   templateUrl: "./export-task-dialog.component.html",
   styleUrls: ["./export-task-dialog.component.scss"],
   encapsulation: ViewEncapsulation.None,
   host: { // eslint-disable-line @angular-eslint/no-host-metadata-property
      "class": "export-task-dialog"
   }
})
export class ExportTaskDialogComponent {
   displayColumns = ["selected", "task", "requiredBy"];
   dataSource = new MatTableDataSource<TaskDependencyModel>([]);
   selection = new SelectionModel<TaskDependencyModel>(true);

   get model(): TaskDependencyModel[] {
      return this._model;
   }

   @Input() set model(value: TaskDependencyModel[]) {
      this._model = value;

      if(value) {
         this.selection.clear();
         this.dataSource.data = value;

         if(value && value && value.length > 0) {
            this.selection.select(...value);
         }
      }
   }

   private _model: TaskDependencyModel[];

   constructor(private http: HttpClient, private dialog: MatDialog,
               private dialogRef: MatDialogRef<ExportTaskDialogComponent>,
               @Inject(MAT_DIALOG_DATA) data: any, fb: UntypedFormBuilder)
   {
   }

   finish(): void {
      this.dialogRef.close(this.selection.selected.map((taskDModel) => taskDModel.task));
   }

   @HostListener("window:keyup.esc", [])
   onKeyUp(): void {
      this.cancel();
   }

   cancel(): void {
      this.dialogRef.close(null);
   }

   toggleRow(row) {
      this.selection.toggle(row);
   }

   hasSelected(): boolean {
      return this.selection.selected.length > 0;
   }

   isAllSelected(): boolean {
      return this.selection.selected.length === this.model.length;
   }

   masterToggle() {
      this.isAllSelected() ? this.selection.clear() : this.selection.select(...this.model);
   }
}
