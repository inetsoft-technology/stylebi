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
import { Component, Inject, OnInit } from "@angular/core";
import { AbstractControl, UntypedFormBuilder, UntypedFormGroup, Validators } from "@angular/forms";
import { MAT_DIALOG_DATA, MatDialogRef } from "@angular/material/dialog";
import { FormValidators } from "../../../../../../../shared/util/form-validators";
import { FileData } from "../../../../../../../shared/util/model/file-data";
import { Tool } from "../../../../../../../shared/util/tool";

@Component({
   selector: "em-add-theme-dialog",
   templateUrl: "./add-theme-dialog.component.html",
   styleUrls: ["./add-theme-dialog.component.scss"]
})
export class AddThemeDialogComponent implements OnInit {
   form: UntypedFormGroup;
   private id: string;
   private ids: string[];
   private jar: FileData;

   constructor(private dialogRef: MatDialogRef<AddThemeDialogComponent>,
               @Inject(MAT_DIALOG_DATA) data: any, fb: UntypedFormBuilder)
   {
      this.id = data.id;
      this.ids = data.ids;
      this.jar = data.jar;
      const initialJar = data.jar ? [data.jar] : [];
      this.form = fb.group({
         name: [data.name, [Validators.required, FormValidators.duplicateName(() => data.names)]],
         jar: [initialJar]
      });
   }

   ngOnInit(): void {
   }

   commit(): void {
      this.dialogRef.close({
         name: this.form.get("name").value,
         id: this.createId(),
         jar: this.getFile(this.form.get("jar"))
      });
   }

   cancel(): void {
      this.dialogRef.close(null);
   }

   private createId(): string {
      if(!!this.id) {
         return this.id;
      }

      const base = this.form.get("name").value.replace(/[^a-zA-Z0-9]/, "");

      if(!base) {
         // name contains only non-alphanumeric characters, need to generate an ID
         return Tool.generateRandomUUID().replace(/-/, "");
      }

      let newId = base;

      for(let counter = 1; !!this.ids.find(s => s == newId); counter++) {
         newId = base + counter;
      }

      return newId;
   }

   private getFile(control: AbstractControl): FileData {
      return control.value?.length ? control.value[0] : this.jar;
   }
}
