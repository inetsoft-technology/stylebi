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
import { HttpClient, HttpParams } from "@angular/common/http";
import { Component, EventEmitter, Input, OnChanges, Output, SimpleChanges } from "@angular/core";
import { MatDialog } from "@angular/material/dialog";
import { MatSnackBar, MatSnackBarConfig } from "@angular/material/snack-bar";
import { RepositoryEntryType } from "../../../../../../../shared/data/repository-entry-type.enum";
import { RepositoryEditorModel } from "../../../../../../../shared/util/model/repository-editor-model";
import { Tool } from "../../../../../../../shared/util/tool";
import { convertToKey, IdentityId } from "../../../security/users/identity-id";
import { RepositoryFolderSettingsModel } from "./repository-folder-settings.model";
import { SetRepositoryFolderSettingsModel } from "./set-repository-folder-settings.model";

export interface RepositoryFolderEditorModel extends RepositoryEditorModel {
   folderModel: RepositoryFolderSettingsModel;
   owner: IdentityId;
   label: string;
}

@Component({
   selector: "em-repository-folder-settings-page",
   templateUrl: "./repository-folder-settings-page.component.html",
   styleUrls: ["./repository-folder-settings-page.component.scss"]
})
export class RepositoryFolderSettingsPageComponent implements OnChanges {
   @Input() model: RepositoryFolderEditorModel;
   @Input() selectedTab = 0;
   @Input() smallDevice: boolean;
   @Output() cancel = new EventEmitter<void>();
   @Output() selectedTabChanged = new EventEmitter<number>();
   @Output() editorChanged = new EventEmitter<string>();
   @Output() unsavedChanges = new EventEmitter<boolean>();
   folderModel: RepositoryFolderSettingsModel;
   isWSFolder = false;

   constructor(private http: HttpClient, private dialog: MatDialog, private snackbar: MatSnackBar) {
   }

   ngOnChanges(changes: SimpleChanges): void {
      if(changes.model) {
         this.folderModel = this.model.folderModel;
         this.isWSFolder = (this.model.type & RepositoryEntryType.WORKSHEET_FOLDER) ==
            RepositoryEntryType.WORKSHEET_FOLDER;
      }
   }

   get securityEnabled() {
      return !!this.folderModel && !!this.folderModel.permissionTableModel;
   }

   editFolder(model: RepositoryFolderSettingsModel) {
      let parentFolder = this.getParentFolder(this.model.path);
      let currentPath = parentFolder == "/" || parentFolder == "" ? model.folderName :
         parentFolder + "/" + model.folderName;

      const setModel: SetRepositoryFolderSettingsModel = {
         alias: model.alias,
         description: model.description,
         oldPath: this.model.path,
         newPath: currentPath,
         replace: true,
         isWSFolder: this.model.type == RepositoryEntryType.WORKSHEET_FOLDER,
         permissionTableModel: model.permissionTableModel
      };
      const url = "../api/em/content/repository/edit/folder";
      const params = this.model.owner ?
         new HttpParams().set("owner", Tool.byteEncode(convertToKey(this.model.owner))) : new HttpParams();
      this.http.post(url, setModel, { params })
         .subscribe(
            (newModel: RepositoryFolderSettingsModel) => {
               this.editorChanged.emit(newModel ? newModel.folderName : null);
            },
            (error) => {
               if(error.error.type === "MessageException") {
                  let message = error.error.message;
                  let config = new MatSnackBarConfig();
                  config.duration = Tool.SNACKBAR_DURATION;
                  config.panelClass = ["max-width"];

                  this.snackbar.open(message, "_#(js:Close)", config);
               }
            });
   }

   private getParentFolder(path: string): string {
      if(path === "/") {
         return "/";
      }

      const slash: number = path.lastIndexOf("/");
      return slash >= 0 ? path.substring(0, slash) : "/";
   }
}
