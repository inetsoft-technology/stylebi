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
import { Component, HostListener, Inject, OnInit } from "@angular/core";
import { UntypedFormBuilder, UntypedFormGroup, ValidationErrors, Validators } from "@angular/forms";
import { MatCheckboxChange } from "@angular/material/checkbox";
import { MAT_DIALOG_DATA, MatDialogRef } from "@angular/material/dialog";
import { ServerPathInfoModel } from "../../../../../../../portal/src/app/vsobjects/model/server-path-info-model";
import { FeatureFlagValue } from "../../../../../../../shared/feature-flags/feature-flags.service";
import { ServerLocation } from "../../../../../../../shared/schedule/model/server-location";
import { FormValidators } from "../../../../../../../shared/util/form-validators";

export interface ServerLocationData {
   location: ServerLocation;
   locations: ServerLocation[];
}

@Component({
   selector: "em-server-location-editor",
   templateUrl: "./server-location-editor.component.html",
   styleUrls: ["./server-location-editor.component.scss"]
})
export class ServerLocationEditorComponent implements OnInit {
   form: UntypedFormGroup;
   FeatureFlagValue = FeatureFlagValue;

   constructor(private dialogRef: MatDialogRef<ServerLocationEditorComponent>,
               @Inject(MAT_DIALOG_DATA) data: ServerLocationData, fb: UntypedFormBuilder)
   {
      const labels = data.locations
         .filter(l => l.label !== data.location.label)
         .map(l => l.label);
      const paths = data.locations
         .filter(l => l.path !== data.location.path)
         .map(l => l.path);
      this.form = fb.group({
         label: [data.location.label, [Validators.required, FormValidators.duplicateName(() => labels)]],
         path: [data.location.path, [Validators.required, ServerLocationEditorComponent.duplicatePath(() => paths)]],
         ftp: [!!data.location.pathInfoModel && !!data.location.pathInfoModel.ftp],
         username: [!!data.location.pathInfoModel ? data.location.pathInfoModel.username : null],
         password: [!!data.location.pathInfoModel ? data.location.pathInfoModel.password : null]
      });

      if(!this.form.get("ftp").value) {
         this.form.get("username").disable();
         this.form.get("password").disable();
      }
   }

   ngOnInit(): void {
   }

   commit(): void {
      let formValue = Object.assign({}, this.form.value);
      let pathInfoModel: ServerPathInfoModel = {
         path: formValue.path,
         username: formValue.username,
         password: formValue.password,
         ftp: !!formValue.ftp
      };
      let result: ServerLocation = {
         path: formValue.path,
         label: formValue.label,
         pathInfoModel: pathInfoModel
      };

      this.dialogRef.close(result);
   }

   @HostListener("window:keyup:esc", [])
   onKeyUp() {
      this.cancel();
   }

   cancel(): void {
      this.dialogRef.close();
   }

   toggleFtp(change: MatCheckboxChange): void {
      if(change.checked) {
         this.form.get("username").enable();
         this.form.get("password").enable();
      }
      else {
         this.form.get("username").disable();
         this.form.get("password").disable();
      }
   }

   private static duplicatePath(paths: () => string[]): (FormControl) => ValidationErrors | null {
      return (control) => {
         if(!control.value) {
            return null;
         }

         const value: string = control.value.replace(/\\/, "/").replace(/\/$/, "");
         const found = paths()
            .map(path => path.replace(/\\/, "/").replace(/\/$/, ""))
            .find(path => path === value || path.startsWith(value + "/") || value.startsWith(path + "/"));
         return !!found ? {duplicatePath: true} : null;
      };
   }
}
