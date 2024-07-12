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
import {
   Component,
   EventEmitter,
   Input,
   OnChanges,
   OnInit,
   Output,
   SimpleChanges
} from "@angular/core";
import { HttpClient, HttpParams } from "@angular/common/http";
import { MatDialog } from "@angular/material/dialog";
import { MatSnackBar } from "@angular/material/snack-bar";
import { Tool } from "../../../../../../../shared/util/tool";
import { DeleteDialog } from "../data-space-file-settings-view/data-space-file-settings-view.component";
import { DataSpaceTreeNode } from "../data-space-tree-node";
import { DataSpaceFolderSettingsModel } from "./data-space-folder-settings-model";
import { DataSpaceFileChange } from "../data-space-editor-page/data-space-editor-page.component";
import { UntypedFormControl, Validators } from "@angular/forms";
import { FormValidators } from "../../../../../../../shared/util/form-validators";
import { GuiTool } from "../../../../../../../portal/src/app/common/util/gui-tool";
import { DownloadService } from "../../../../../../../shared/download/download.service";

@Component({
   selector: "em-data-space-folder-settings-view",
   templateUrl: "./data-space-folder-settings-view.component.html",
   styleUrls: ["./data-space-folder-settings-view.component.scss"]
})
export class DataSpaceFolderSettingsViewComponent implements OnInit, OnChanges {
   @Input() data: DataSpaceTreeNode;
   @Input() newFolder: boolean;
   @Output() newFolderChange = new EventEmitter<boolean>();
   @Output() newFileClicked = new EventEmitter<void>();
   @Output() newFolderClicked = new EventEmitter<void>();
   @Output() uploadFilesClicked = new EventEmitter<void>();
   @Output() deleteFolderChange = new EventEmitter<DataSpaceTreeNode>();
   @Output() folderAdded = new EventEmitter<string>();
   @Output() folderEdited = new EventEmitter<DataSpaceFileChange>();
   model: DataSpaceFolderSettingsModel;
   nameControl: UntypedFormControl;

   constructor(private http: HttpClient,
               public dialog: MatDialog,
               private snackBar: MatSnackBar,
               private downloadService: DownloadService)
   {
      this.nameControl = new UntypedFormControl("", [Validators.required,
         FormValidators.isValidDataSpaceFileName]);
   }

   ngOnInit() {
   }

   ngOnChanges(changes: SimpleChanges): void {
      if(this.data !== undefined && this.newFolder !== undefined) {
         if(this.newFolder) {
            this.model = <DataSpaceFolderSettingsModel> {
               path: this.data.path,
               name: ""
            };
            this.nameControl.setValue("");
         }
         else {
            const params = new HttpParams()
               .set("path", Tool.byteEncode(this.data.path));
            this.http.get("../api/em/content/data-space/folder/model", {params})
               .subscribe((model: DataSpaceFolderSettingsModel) => {
                  this.model = model;
                  this.nameControl.setValue(model.name);
               });
         }
      }
   }

   get canDelete(): boolean {
      return !( // cache, portal, and root path should not be deleted
         this.data.path === "/" ||
         this.data.path.toLowerCase() === "cache" ||
         this.data.path.toLowerCase() === "portal"
      );
   }

   delete() {
      const dialogRef = this.dialog.open(DeleteDialog, {
         width: "300px",
         data: this.data
      });

      dialogRef.afterClosed().subscribe(result => {
         if(result === "_#(js:OK)") {
            const params = new HttpParams()
               .set("path", Tool.byteEncode(this.model.path));

            this.http.delete("../api/em/content/data-space/folder", {params: params}).subscribe(() => this.deleteFolderChange.emit(this.data));
         }
      });
   }

   get canEdit(): boolean {
      return this.newFolder || (this.model && this.model.path !== "/");
   }

   apply() {
      this.model.newFolder = this.newFolder;
      this.model.newName = this.nameControl.value;
      const url = "../api/em/content/data-space/folder/apply";

      this.http.post(url, this.model).subscribe(
         (model: DataSpaceFolderSettingsModel) => {
            this.model = model;

            if(this.newFolder) {
               this.folderAdded.emit(model.path);
            }
            else {
               this.folderEdited.emit({
                  newPath: model.path,
                  oldPath: this.data.path,
                  newName: model.name
               });
            }

            this.newFolder = false;
         },
         (error) => {
            let message = "";

            if(error.error.code === 3) {
               if(this.newFolder) {
                  message = "_#(js:Name already exists)";
               }
               else {
                  message = "_#(js:em.dataspace.fileAlreadyExists)";
               }
            }
            else {
               message = "_#(js:em.dataspace.renameError)";
            }

            this.snackBar.open(message, null, {
               duration: Tool.SNACKBAR_DURATION,
            });
         });
   }

   cancel() {
      this.newFolder = false;
      this.newFolderChange.emit(this.newFolder);
   }

   download(): void {
      const params = new HttpParams()
         .set("path", Tool.byteEncode(this.model.path))
         .set("name", Tool.byteEncode(this.model.name))
         .set("checkForResponse", "true");
      this.downloadService.download(
         GuiTool.appendParams("../em/content/data-space/folder/download", params));
   }
}
