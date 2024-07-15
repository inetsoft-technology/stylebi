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
import {Component, HostListener, Inject, OnInit} from "@angular/core";
import {UntypedFormBuilder, UntypedFormGroup, Validators} from "@angular/forms";
import {MAT_DIALOG_DATA, MatDialogRef} from "@angular/material/dialog";
import {FormValidators} from "../../../../../../../shared/util/form-validators";
import {EditFontMappingDialogData} from "./edit-font-mapping-dialog-data";

@Component({
   selector: "em-edit-font-mapping-dialog",
   templateUrl: "./edit-font-mapping-dialog.component.html",
   styleUrls: ["./edit-font-mapping-dialog.component.scss"]
})
export class EditFontMappingDialogComponent implements OnInit {
   title: string;
   form: UntypedFormGroup;

   constructor(private dialogRef: MatDialogRef<EditFontMappingDialogComponent>,
               @Inject(MAT_DIALOG_DATA) data: EditFontMappingDialogData, fb: UntypedFormBuilder)
   {
      this.title = data.trueTypeFont ? "_#(js:Edit Font Mapping)" : "_#(js:Add Font Mapping)";
      this.form = fb.group({
         trueTypeFont: [data.trueTypeFont, [Validators.required, FormValidators.cannotContain([":", ";", "*", "&"])]],
         cidFont: [data.cidFont, [Validators.required, FormValidators.cannotContain([":", ";", "*", "&"])]]
      });
   }

   ngOnInit() {
   }

   submit(): void {
      this.dialogRef.close({
         trueTypeFont: this.form.get("trueTypeFont").value,
         cidFont: this.form.get("cidFont").value
      });
   }

   @HostListener("window:keyup.esc", [])
   onKeyUp() {
      this.cancel();
   }

   cancel(): void {
      this.dialogRef.close(null);
   }
}
