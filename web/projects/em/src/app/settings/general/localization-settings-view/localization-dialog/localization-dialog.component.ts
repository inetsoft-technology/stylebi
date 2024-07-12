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
import { UntypedFormBuilder, FormControl, UntypedFormGroup, Validators } from "@angular/forms";
import { HttpClient, HttpParams } from "@angular/common/http";
import { MAT_DIALOG_DATA, MatDialogRef } from "@angular/material/dialog";
import { LocalizationModel } from "../localization-settings-model";

@Component({
   selector: "em-localization-dialog",
   templateUrl: "./localization-dialog.component.html",
   styleUrls: ["./localization-dialog.component.scss"]
})
export class LocalizationDialogComponent implements OnInit {
   form: UntypedFormGroup;
   adding: boolean = false;
   editing: boolean = false;

   constructor(private fb: UntypedFormBuilder,
               private dialogRef: MatDialogRef<LocalizationDialogComponent>,
               @Inject(MAT_DIALOG_DATA) public data: any)
   {
      const selectedItem: LocalizationModel = data.selectedItem;

      this.initForm();

      if(selectedItem == null) {
         this.adding = true;
         this.form.controls["language"].setValue("");
         this.form.controls["country"].setValue("");
         this.form.controls["label"].setValue("");
      }
      else {
         this.editing = true;
         this.form.controls["language"].setValue(selectedItem.language);
         this.form.controls["country"].setValue(selectedItem.country);
         this.form.controls["label"].setValue(selectedItem.label);
      }
   }

   ngOnInit() {

   }

   initForm() {
      this.form = this.fb.group({
         language: ["", [Validators.required, Validators.minLength(2), Validators.pattern("[A-Za-z]+$")]],
         country: ["", [Validators.minLength(2), Validators.pattern("[A-Za-z]+$")]],
         label: ["", [Validators.required]]
      });
   }

   save(action: string) {
      this.dialogRef.close({
                                     language: this.form.value["language"].toLowerCase(),
                                     country: this.form.value["country"].toUpperCase(),
                                     label: this.form.value["label"],
                                     validForm: this.form.valid
                                  });
      event.preventDefault();
   }

   @HostListener("window:keyup.esc", [])
   onKeyUp() {
      this.cancel();
   }

   cancel() {
     this.dialogRef.close();
   }

   get title(): string {
      return this.adding ? "_#(js:Add Locale)" : this.editing ? "_#(js:Edit Locale)" : "";
   }
}
