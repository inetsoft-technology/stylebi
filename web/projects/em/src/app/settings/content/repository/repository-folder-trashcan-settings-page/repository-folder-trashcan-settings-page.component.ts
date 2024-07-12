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
import {HttpClient, HttpErrorResponse} from "@angular/common/http";
import {Component, EventEmitter, Input, OnChanges, Output, SimpleChanges} from "@angular/core";
import {TableInfo} from "../../../../common/util/table/table-info";
import {RepositoryFolderTrashcanTableModel} from "./repository-folder-trashcan-table-model";
import {RepositoryFolderTrashcanSettingsModel} from "./repository-folder-trashcan-settings-model";
import {MatDialog, MatDialogConfig} from "@angular/material/dialog";
import {MatSnackBar} from "@angular/material/snack-bar";
import {MessageDialog, MessageDialogType} from "../../../../common/util/message-dialog";
import {RepositoryEditorModel} from "../../../../../../../shared/util/model/repository-editor-model";
import {catchError} from "rxjs/operators";
import {throwError} from "rxjs";
import {Tool} from "../../../../../../../shared/util/tool";

export interface RepositoryTrashcanFolderEditorModel extends RepositoryEditorModel {
   reports: RepositoryFolderTrashcanTableModel[];
}

@Component({
   selector: "em-repository-folder-trashcan-settings-page",
   templateUrl: "./repository-folder-trashcan-settings-page.component.html",
   styleUrls: ["./repository-folder-trashcan-settings-page.component.scss"]
})
export class RepositoryFolderTrashcanSettingsPageComponent implements OnChanges {
   @Input() model: RepositoryTrashcanFolderEditorModel;
   @Input() smallDevice: boolean;
   @Output() cancel = new EventEmitter<void>();
   @Output() editorChanged = new EventEmitter<void>();
   @Output() unsavedChanges = new EventEmitter<boolean>();
   reports: RepositoryFolderTrashcanTableModel[];
   reportsTableInfo: TableInfo = {
      selectionEnabled: true,
      title: "",
      columns: [
         {header: "_#(js:em.archiveCanName.deleted)", field: "name"},
         {header: "_#(js:Version)", field: "version"},
         {header: "_#(js:Type)", field: "type"}]
   };

   constructor(private http: HttpClient,
               private dialog: MatDialog,
               private snackBar: MatSnackBar) {
   }

   ngOnChanges(changes: SimpleChanges): void {
      if(changes.model) {
         if(this.model.reports) {
            this.reports = this.model.reports;
         }
      }
   }

   removeReports(reports: RepositoryFolderTrashcanTableModel[]) {
      const url = "../api/em/content/repository/folder/trashcan/delete";
      const model: RepositoryFolderTrashcanSettingsModel = {table: reports};
      const ref = this.dialog.open(MessageDialog, {
         data: {
            title: "_#(js:Delete)",
            content: "_#(js:common.tree.deleteTrashcan)",
            type: MessageDialogType.DELETE
         }
      });

      ref.afterClosed().subscribe(del => {
         if(del) {
            this.http.post(url, model).subscribe(() => this.editorChanged.emit());
         }
      });
   }

   restoreReports(reports: RepositoryFolderTrashcanTableModel[]) {
      const model: RepositoryFolderTrashcanSettingsModel = {table: reports};
      this.http.post("../api/em/content/repository/folder/trashcan/restore", model)
         .pipe(catchError((error: HttpErrorResponse) => {
            this.snackBar.open(error.error.message, "_#(js:Close)", {duration: Tool.SNACKBAR_DURATION});
            return throwError(error);
         }))
         .subscribe(() => {
            this.snackBar.open("_#(js:em.archiveSecurity.permissionClear)", "_#(js:Close)", {duration: Tool.SNACKBAR_DURATION});
            this.editorChanged.emit();
         });
   }
}
