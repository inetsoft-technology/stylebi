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
import { HttpClient, HttpErrorResponse } from "@angular/common/http";
import { Component, HostListener, Inject, ViewEncapsulation } from "@angular/core";
import { UntypedFormBuilder, UntypedFormGroup, Validators } from "@angular/forms";
import { MAT_DIALOG_DATA, MatDialog, MatDialogRef } from "@angular/material/dialog";
import { catchError } from "rxjs/operators";
import { Observable, throwError } from "rxjs";
import { MatTableDataSource } from "@angular/material/table";
import { SelectionModel } from "@angular/cdk/collections";
import { ImportTaskResponse } from "../../model/import-task-response";
import { MessageDialog, MessageDialogType } from "../../../../common/util/message-dialog";
import { TaskDependencyModel } from "../../model/task-dependency-model";
import { ImportTaskDialogModel } from "../../model/import-task-dialog-model";

@Component({
   selector: "em-import-task-dialog",
   templateUrl: "./import-task-dialog.component.html",
   styleUrls: ["./import-task-dialog.component.scss"],
   encapsulation: ViewEncapsulation.None,
   host: { // eslint-disable-line @angular-eslint/no-host-metadata-property
      "class": "import-task-dialog"
   }
})
export class ImportTaskDialogComponent {
   uploadForm: UntypedFormGroup;
   importForm: UntypedFormGroup;
   uploaded = false;
   displayColumns = ["selected", "task", "dependency"];
   dataSource = new MatTableDataSource<TaskDependencyModel>([]);
   selection = new SelectionModel<TaskDependencyModel>(true);

   get loading(): boolean {
      return this._loading;
   }

   set loading(value: boolean) {
      this._loading = value;

      if(value) {
         this.uploadForm.get("file").disable();
      }
      else {
         this.uploadForm.get("file").enable();
      }
   }

   private _loading = false;

   get model(): ImportTaskDialogModel {
      return this._model;
   }

   set model(value: ImportTaskDialogModel) {
      this._model = value;

      if(value) {
         this.selection.clear();
         this.dataSource.data = value.tasks;

         if(value && value.tasks && value.tasks.length > 0) {
            this.selection.select(...value.tasks);
         }
      }
   }

   private _model: ImportTaskDialogModel;

   constructor(private http: HttpClient, private dialog: MatDialog,
               private dialogRef: MatDialogRef<ImportTaskDialogComponent>,
               @Inject(MAT_DIALOG_DATA) data: any, fb: UntypedFormBuilder)
   {
      this.uploadForm = fb.group({
         file: [null, Validators.required]
      });
      this.importForm = fb.group({
         overwrite: [true]
      });
   }

   upload(): void {
      const file = this.uploadForm.get("file").value[0];
      this.http.post<ImportTaskDialogModel>("../api/em/content/schedule/set-task-file", file)
          .pipe(catchError(err => this.handleUploadError(err)))
          .subscribe(info => this.updateTaskInfo(info));
   }

   updateTaskInfo(info: ImportTaskDialogModel): void {
      this.model = info;
      this.uploaded = true;
   }

   finish(): void {
      const uri = `../api/em/content/schedule/import/${this.importForm.get("overwrite").value}`;
      let tasks: string[] = [];

      for (let value of this.selection.selected.values()) {
         tasks.push(value.task);
      }

      this.http.post<ImportTaskResponse>(uri, tasks)
          .pipe(catchError(err => this.handleImportError(err)))
          .subscribe(response => this.onImportComplete(response));
   }

   back(): void {
      this.uploaded = false;
      this.model = null;
   }

   onImportComplete(response: ImportTaskResponse): void {
      this.loading = false;
      let type: MessageDialogType;
      let title: string;
      let content: string;

      if(response.failedTasks.length > 0) {
         // Failed due to other reason
         type = MessageDialogType.WARNING;
         title = "_#(js:Warning)";
         content = "_#(js:em.import.nopermission): " + response.failedTasks.join(",");

      }
      else {
         type = MessageDialogType.INFO;
         title = "_#(js:Success)";
         content = "_#(js:em.import.success) _#(js:em.import.task.restart)";
      }

      this.dialog.open(MessageDialog, { data: { title, content, type } })
          .afterClosed().subscribe(() => this.dialogRef.close(true));
   }

   private handleImportError(error: HttpErrorResponse): Observable<ImportTaskResponse> {
      this.loading = false;
      this.dialog.open(MessageDialog, {
         data: {
            title: "_#(js:Error)",
            content: error.error.message,
            type: MessageDialogType.ERROR
         }
      });
      return throwError(error);
   }

   @HostListener("window:keyup.esc", [])
   onKeyUp(): void {
      this.cancel();
   }

   cancel(): void {
      this.dialogRef.close(null);
   }

   private handleUploadError(error: HttpErrorResponse): Observable<ImportTaskDialogModel> {
      this.loading = false;
      let message = "_#(js:schedule.importTask.parseXMLErr)";

      this.dialog.open(MessageDialog, {
         data: {
            title: "_#(js:Error)",
            content: message,
            type: MessageDialogType.ERROR
         }
      });

      return throwError(error);
   }

   toggleRow(row) {
      this.selection.toggle(row);
   }

   hasSelected(): boolean {
      return this.selection.selected.length > 0;
   }

   isAllSelected(): boolean {
      return this.selection.selected.length === this.model.tasks.length;
   }

   masterToggle() {
      this.isAllSelected() ? this.selection.clear() : this.selection.select(...this.model.tasks);
   }
}
