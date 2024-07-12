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
import { Component, HostListener, Inject, OnInit } from "@angular/core";
import {
   UntypedFormControl,
   UntypedFormGroup,
   Validators,
} from "@angular/forms";
import { MAT_DIALOG_DATA, MatDialogRef } from "@angular/material/dialog";
import { Tool } from "../../../../../../shared/util/tool";
import { EditTaskFolderDialogModel } from "../model/edit-task-folder-dialog-model";
import { HttpClient } from "@angular/common/http";
import { CheckDuplicateResponse } from "../../../../../../portal/src/app/portal/data/commands/check-duplicate-response";
import { FormValidators } from "../../../../../../shared/util/form-validators";

const TASK_FOLDER_CHECK_DUPLICATE_URI: string = "../api/em/schedule/rename/checkDuplicate";

@Component({
   selector: "em-edit-task-folder-dialog",
   templateUrl: "./edit-task-folder-dialog.component.html",
   styleUrls: ["./edit-task-folder-dialog.component.scss"]
})
export class EditTaskFolderDialogComponent implements OnInit{
   model: EditTaskFolderDialogModel;
   oldModel: EditTaskFolderDialogModel;
   form: UntypedFormGroup;
   duplicate: boolean = false;
   unchanged: boolean = false;

   constructor(private dialogRef: MatDialogRef<EditTaskFolderDialogComponent>,
               private http: HttpClient,
               @Inject(MAT_DIALOG_DATA) data: any)
   {
      this.model = data;
      this.oldModel = Tool.clone(this.model);
   }

   ngOnInit() {
      this.form = new UntypedFormGroup({
         "folderName": new UntypedFormControl(this.model.folderName, [Validators.required,
            FormValidators.invalidTaskName])
      });

      this.form.get("folderName").valueChanges.subscribe(() => {
         this.duplicate = false;
         this.unchanged = false;
      });
   }

   submit(): void {
      this.model.folderName = this.form.get("folderName").value;

      if(this.model.folderName == this.oldModel.folderName) {
         this.unchanged = true;
         return;
      }

      this.http.post<CheckDuplicateResponse>(TASK_FOLDER_CHECK_DUPLICATE_URI, this.model)
         .subscribe(res => {
            if(res.duplicate) {
               this.duplicate = true;
            }
            else {
               this.dialogRef.close(this.model);
            }
         });
   }

   @HostListener("window:keyup.enter", [])
   onEnter() {
      this.submit();
   }

   @HostListener("window:keyup.esc", [])
   onEsc() {
      this.cancel();
   }

   cancel(): void {
      this.dialogRef.close(null);
   }
}
