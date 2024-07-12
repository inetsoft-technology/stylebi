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
import {Component, HostListener, Inject, OnInit} from "@angular/core";
import {UntypedFormBuilder, UntypedFormGroup, Validators} from "@angular/forms";
import {MAT_DIALOG_DATA, MatDialogRef} from "@angular/material/dialog";
import { CommonKVModel } from "../../../../../../../portal/src/app/common/data/common-kv-model";
import {FormValidators} from "../../../../../../../shared/util/form-validators";

@Component({
   selector: "em-add-data-source-type-dialog",
   templateUrl: "./add-data-source-type-dialog.component.html",
   styleUrls: ["./add-data-source-type-dialog.component.scss"]
})
export class AddDataSourceTypeDialogComponent implements OnInit {
   title: string;
   form: UntypedFormGroup;
   listings: CommonKVModel<string, string>[];

   constructor(private dialogRef: MatDialogRef<AddDataSourceTypeDialogComponent>,
               @Inject(MAT_DIALOG_DATA) data: any, fb: UntypedFormBuilder)
   {
      this.title = data.title;
      this.listings = data.listings;
      const currTypes = data.currTypes || [];
      this.form = fb.group({
         dataSource: ["", [Validators.required, FormValidators.duplicateName(() => currTypes)]]
      });
   }

   ngOnInit() {
   }

   submit(): void {
      this.dialogRef.close(this.form.get("dataSource").value);
   }

   @HostListener("window:keyup.esc", [])
   onKeyUp() {
      this.cancel();
   }

   cancel(): void {
      this.dialogRef.close(null);
   }
}
