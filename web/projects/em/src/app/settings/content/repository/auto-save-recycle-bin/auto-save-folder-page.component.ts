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
import { Component, EventEmitter, Input, Output } from "@angular/core";
import { HttpClient, HttpErrorResponse, HttpParams } from "@angular/common/http";
import { MatSnackBar } from "@angular/material/snack-bar";
import { RestoreAssetDialogComponent } from "./restore-asset-dialog.component";
import { MatDialog, MatDialogConfig } from "@angular/material/dialog";
import { RepositoryEntryType } from "../../../../../../../shared/data/repository-entry-type.enum";
import { AutoSaveFolderModel } from "./auto-save-folder-model";
import { RepositoryEditorModel } from "../../../../../../../shared/util/model/repository-editor-model";
import { TableInfo } from "../../../../common/util/table/table-info";
import { Observable, throwError } from "rxjs";
import { RestoreAssetTreeListModel } from "./restore-asset-tree-list-model";
import { catchError } from "rxjs/operators";
import { MessageDialog, MessageDialogType } from "../../../../common/util/message-dialog";

const DELETE_AUTO_SAVE: string = "../api/em/content/repository/autosave/delete";

export interface AutoSaveSelectModel {
   label: string;
   host: string;
   id: string;
}

@Component({
   selector: "em-auto-save-folder-page",
   templateUrl: "./auto-save-folder-page.component.html"
})
export class AutoSaveFolderPageComponent {
   @Input()
   set model(model: AutoSaveFolderModel) {
      this._model = model;

      if(model.assets == null) {
         return;
      }

      this.datasource = [];

      for(let i = 0; i < model.assets.length; i++) {
         let asset = model.assets[i];

         this.datasource.push(<AutoSaveSelectModel>{
            id: asset.path,
            label: this.getLabel(asset),
            host: this.getHost(asset)
         });
      }
   }

   get model(): AutoSaveFolderModel {
      return this._model;
   }

   @Input() nodeType: string;
   @Input() smallDevice: boolean;
   @Output() cancel = new EventEmitter<void>();
   @Output() editorChanged = new EventEmitter<string>();
   @Output() unsavedChanges = new EventEmitter<boolean>();
   _model: AutoSaveFolderModel;
   datasource: AutoSaveSelectModel[] = [];
   assets: AutoSaveSelectModel[] = [];

   reportsTableInfo: TableInfo = {
      selectionEnabled: true,
      title: "",
      columns: [
         {header: "_#(js:Name)", field: "label"},
         {header: "_#(js:Host)", field: "host"}],
      actions: []
   };

   constructor(private http: HttpClient, private dialog: MatDialog, private snackBar: MatSnackBar) {
   }

   getLabel(asset: RepositoryEditorModel) {
      let path = asset.path;

      if(path != null) {
         let paths = path.split("^");

         return paths[3];
      }

      return path;
   }

   getHost(asset: RepositoryEditorModel) {
      let path = asset.path;

      if(path != null) {
         let paths = path.split("^");

         return paths[4].replace("~", "");
      }

      return path;
   }

   getIds(): string[] {
      let ids: string[] = [];

      for(let i = 0; i < this.assets.length; i++) {
         ids.push(this.assets[i].id);
      }

      return ids;
   }

   recoverEntries() {
      let isVS = this.model.type == RepositoryEntryType.VS_AUTO_SAVE_FOLDER;

      const dialogRef = this.dialog.open(RestoreAssetDialogComponent, {
         role: "dialog",
         width: "750px",
         maxWidth: "100%",
         maxHeight: "100%",
         disableClose: true,
         data: { isVS: isVS, ids: this.getIds() }
      });

      dialogRef.afterClosed().subscribe(() => {
         this.editorChanged.emit(null);
      });
   }

   deleteEntries() {
      let content = "_#(js:em.common.items.deleteRecycleBinNode)";

      this.dialog.open(MessageDialog, <MatDialogConfig>{
         width: "350px",
         data: {
            title: "_#(js:Confirm)",
            content: content,
            type: MessageDialogType.CONFIRMATION
         }
      }).afterClosed().subscribe(confirmed => {
         if(confirmed) {
            this.deleteEntries0();
         }
      });

   }

   deleteEntries0() {
      const body = { "ids": this.getIds().join(",") };

      this.http.post(DELETE_AUTO_SAVE, body)
         .pipe(catchError((error: HttpErrorResponse) => {
            return throwError(error);
         }))
         .subscribe(() => {
            this.editorChanged.emit(null);
         });
   }
}
