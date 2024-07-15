/*
 * inetsoft-web - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
import {
   Component,
   ElementRef,
   EventEmitter,
   Inject,
   Input,
   OnChanges,
   OnInit,
   Output,
   SimpleChanges
} from "@angular/core";
import {HttpClient, HttpParams} from "@angular/common/http";
import {MAT_DIALOG_DATA, MatDialog, MatDialogRef} from "@angular/material/dialog";
import {MatSnackBar} from "@angular/material/snack-bar";
import {GuiTool} from "../../../../../../../portal/src/app/common/util/gui-tool";
import {DownloadService} from "../../../../../../../shared/download/download.service";
import { AppInfoService } from "../../../../../../../shared/util/app-info.service";
import {Tool} from "../../../../../../../shared/util/tool";
import {FileData} from "../../../../../../../shared/util/model/file-data";
import {DataSpaceTreeNode} from "../data-space-tree-node";
import {DataSpaceFileSettingsModel} from "./data-space-file-settings-model";
import {DataSpaceFileChange} from "../data-space-editor-page/data-space-editor-page.component";
import {UntypedFormControl, Validators} from "@angular/forms";
import {FormValidators} from "../../../../../../../shared/util/form-validators";
import {DataSpaceFileContentModel} from "../text-file-content-view/data-space-file-content-model";

@Component({
   selector: "em-data-space-file-settings-view",
   templateUrl: "./data-space-file-settings-view.component.html",
   styleUrls: ["./data-space-file-settings-view.component.scss"]
})
export class DataSpaceFileSettingsViewComponent implements OnInit, OnChanges {
   @Input() data: DataSpaceTreeNode;
   @Input() newFile: boolean;
   @Input() newFolder: boolean;
   @Input() smallDevice = false;
   @Output() newFileChange = new EventEmitter<boolean>();
   @Output() deleteFileChange = new EventEmitter<DataSpaceTreeNode>();
   @Output() fileAdded = new EventEmitter<string>();
   @Output() fileEdited = new EventEmitter<DataSpaceFileChange>();
   @Output() cancelClicked = new EventEmitter<void>();
   model: DataSpaceFileSettingsModel;
   parentPath: string;
   files: FileData[];
   nameControl: UntypedFormControl;
   content: DataSpaceFileContentModel;
   contentEditMode: boolean = false;
   isEnterprise: boolean = false;

   constructor(private downloadService: DownloadService, private http: HttpClient,
               public dialog: MatDialog, private snackBar: MatSnackBar,
               private element: ElementRef,
               private appInfoService: AppInfoService)
   {
      this.nameControl = new UntypedFormControl("", [Validators.required,
         FormValidators.isValidDataSpaceFileName]);
   }

   ngOnInit() {
      this.appInfoService.isEnterprise().subscribe(val => {
         this.isEnterprise = val;
      });
   }

   ngOnChanges(changes: SimpleChanges): void {
      if(this.data !== undefined && this.newFile !== undefined) {
         if(this.newFile) {
            this.parentPath = this.data.path;
            this.nameControl.setValue("");
         }
         else {
            this.contentEditMode = false;
            const params = new HttpParams()
               .set("path", Tool.byteEncode(this.data.path));
            this.http.get("../api/em/content/data-space/file/model", {params})
               .subscribe((model: DataSpaceFileSettingsModel) => {
                  this.model = model;
                  this.nameControl.setValue(model.label);
                  this.files = null;
               });
         }
      }
   }

   getFilePath() {
      return this.model?.displayPath;
   }

   get contentEditorVisible(): boolean {
      return !!this.data && !this.data.folder && !this.newFile && !this.newFolder;
   }

   editContent() {
      if(this.element.nativeElement) {
         const target = this.element.nativeElement.querySelector(`#${"content"}`);

         if(target) {
            target.scrollIntoView();
         }

         this.contentEditMode = true;
      }
   }

   download() {
      const params = new HttpParams()
         .set("path", Tool.byteEncode(this.model.path))
         .set("name", Tool.byteEncode(this.model.name))
         .set("checkForResponse", "true");
      this.downloadService.download(
         GuiTool.appendParams("../em/content/data-space/file/download", params));
   }

   apply() {
      const file = this.files && this.files.length ? this.files[0] : null;
      const url = "../api/em/content/data-space/file/apply";
      const data = {
         path: this.newFile ? this.parentPath : this.model.path,
         name: this.newFile ? this.nameControl.value : this.model.name,
         newName: this.nameControl.value,
         newFile: this.newFile,
         fileData: file ? file.content : null,
         content: this.content
      };

      this.http.post(url, data).subscribe(
         (model: DataSpaceFileSettingsModel) => {
            this.model = model;

            if(this.newFile) {
               this.fileAdded.emit(model.path);
            }
            else {
               this.fileEdited.emit({
                  newPath: model.path,
                  oldPath: this.data.path,
                  newName: model.name
               });
            }

            this.newFile = false;
            this.files = null;
         },
         (error) => {
            let message = "";

            if(error.error.code === 3) {
               if(error.error.type === "ResourceExistsException") {
                  message = "_#(js:em.dataspace.fileAlreadyExists)";
               }
               else if(this.newFile) {
                  message = "_#(js:em.dataspace.newFileError)";
               } else {
                  message = "_#(js:Error)";
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

   get canDelete(): boolean {
      return !(this.data.path.endsWith(".db") || this.data.path.endsWith(".dat")); // database and dat files should not be deleted
   }

   delete() {
      const dialogRef = this.dialog.open(DeleteDialog, {
         width: "300px",
         data: this.model
      });

      dialogRef.afterClosed().subscribe(result => {
         if(result === "_#(js:OK)") {
            const params = new HttpParams()
               .set("path", Tool.byteEncode(this.model.path));

            this.http.delete("../api/em/content/data-space/file", {params: params}).subscribe(() => this.deleteFileChange.emit(this.data));
         }
      });
   }

   cancel() {
      if(this.newFile) {
         this.newFile = false;
         this.newFileChange.emit(this.newFile);
      }

      this.cancelClicked.emit();
   }
}

@Component({
   selector: "em-delete-dialog",
   templateUrl: "./delete-dialog.html"
})
export class DeleteDialog {
   constructor(public dialogRef: MatDialogRef<DeleteDialog>, @Inject(MAT_DIALOG_DATA) public model: DataSpaceTreeNode) {

   }

   get label() {
      return this.model.label || (<any> this.model).name || this.model.path;
   }
}
