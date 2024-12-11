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

import { HttpClient, HttpParams } from "@angular/common/http";
import { Component, HostListener, Inject, OnInit } from "@angular/core";
import {
   AbstractControl,
   UntypedFormBuilder,
   UntypedFormGroup,
   ValidationErrors,
   Validators
} from "@angular/forms";
import { MAT_DIALOG_DATA, MatDialogRef } from "@angular/material/dialog";
import { FormValidators } from "../../../../../../../shared/util/form-validators";
import { LicenseKeyModel } from "../license-key-settings-model";
import { EditLicenseKeyDialogData } from "./edit-license-key-dialog-data";

@Component({
   selector: "em-edit-license-key-dialog",
   templateUrl: "./edit-license-key-dialog.component.html",
   styleUrls: ["./edit-license-key-dialog.component.scss"]
})
export class EditLicenseKeyDialogComponent implements OnInit {
   add = false;
   server = false;
   form: UntypedFormGroup;
   private keyInvalid = false;

   get title(): string {
      return this.add ? "_#(js:Add License Key)" : "_#(js:Edit License Key)";
   }

   constructor(private http: HttpClient,
               private dialogRef: MatDialogRef<EditLicenseKeyDialogComponent>,
               @Inject(MAT_DIALOG_DATA) data: EditLicenseKeyDialogData, fb: UntypedFormBuilder)
   {
      switch(data.type) {
      default:
         this.server = true;
      }

      this.add = !data.model;
      const keys = data.keys.map(k => k.key);
      const key = data.model ? data.model.key : null;

      this.form = fb.group({
         key: [key, [Validators.required, FormValidators.duplicateName(() => keys), this.invalidKey]]
      });
   }

   ngOnInit() {
   }

   onLicenseKeyChange(): void {
      this.keyInvalid = false;
      this.form.get("key").updateValueAndValidity();
   }

   submit(): void {
      let params = new HttpParams().set("key", this.form.get("key").value);
      let uri = "../api/em/general/settings/license-key/single-server-key";

      this.http.get<LicenseKeyModel>(uri, {params}).subscribe(
         keyModel => {
            if(keyModel.valid) {
               this.dialogRef.close(keyModel);
            }
            else {
               this.keyInvalid = true;
               this.form.get("key").updateValueAndValidity();
            }
         },
         () => {
            this.keyInvalid = true;
            this.form.get("key").updateValueAndValidity();
         }
      );
   }

   @HostListener("window:keyup.esc", [])
   onKeyUp() {
      this.cancel();
   }

   cancel(): void {
      this.dialogRef.close(null);
   }

   private invalidKey = (control: AbstractControl): ValidationErrors | null => {
      if(control && this.keyInvalid) {
         return { invalidKey: true };
      }

      return null;
   };
}
