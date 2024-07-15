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
import {Component, Inject} from "@angular/core";
import {AbstractControl, UntypedFormBuilder, UntypedFormGroup, Validators} from "@angular/forms";
import {MAT_DIALOG_DATA, MatDialogRef} from "@angular/material/dialog";
import {AddFontFaceDialogData} from "../add-font-face-dialog-data";
import {UserFontModel} from "../user-font-model";
import {FormValidators} from "../../../../../../../shared/util/form-validators";
import {FileData} from "../../../../../../../shared/util/model/file-data";

@Component({
   selector: "em-add-font-face-dialog",
   templateUrl: "./add-font-face-dialog.component.html",
   styleUrls: ["./add-font-face-dialog.component.scss"]
})
export class AddFontFaceDialogComponent {
   form: UntypedFormGroup;

   constructor(private dialogRef: MatDialogRef<AddFontFaceDialogComponent, UserFontModel>,
               @Inject(MAT_DIALOG_DATA) data: AddFontFaceDialogData,
               fb: UntypedFormBuilder)
   {
      const existingFontFaceIdentifiers = data.existingFontFaces.map(f => f.identifier);
      const identifier = data.identifier ? data.identifier : "Font Face " + (data.existingFontFaces.length + 1);

      this.form = fb.group({
         name: [data.fontName || null, [ Validators.required, FormValidators.isValidReportName,
            Validators.pattern(/^[^0-9]/),
            FormValidators.duplicateName(() => data.existingFontNames) ]],
         identifier: [identifier, [ Validators.required, FormValidators.isValidReportName,
            Validators.pattern(/^[^0-9]/),
            FormValidators.duplicateName(() => existingFontFaceIdentifiers) ]],
         ttf: [null, Validators.required],
         eot: [null],
         svg: [null],
         woff: [null],
         fontWeight: [data.fontWeight || null],
         fontStyle: [data.fontStyle || null]
      });

      if(data.fontName) {
         this.form.get("name").disable();
      }

      if(data.identifier != null) {
         this.form.get("name").disable();
         this.form.get("identifier").disable();
         this.form.get("ttf").disable();
         this.form.get("eot").disable();
         this.form.get("svg").disable();
         this.form.get("woff").disable();
      }
   }

   submit(): void {
      let fontWeight = this.form.get("fontWeight").value;

      if(!fontWeight) {
         fontWeight = null;
      }

      let fontStyle = this.form.get("fontStyle").value;

      if(!fontStyle) {
         fontStyle = null;
      }

      this.dialogRef.close({
         name: this.form.get("name").value,
         identifier: this.form.get("identifier").value,
         ttf: this.getFile(this.form.get("ttf")),
         eot: this.getFile(this.form.get("eot")),
         svg: this.getFile(this.form.get("svg")),
         woff: this.getFile(this.form.get("woff")),
         fontWeight,
         fontStyle
      });
   }

   private getFile(control: AbstractControl): FileData {
      return control.value && control.value.length ? control.value[0] : null;
   }
}
