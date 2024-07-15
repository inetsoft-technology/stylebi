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
import {Component, HostListener, Inject, OnInit} from "@angular/core";
import {UntypedFormBuilder, UntypedFormGroup, Validators} from "@angular/forms";
import {MAT_DIALOG_DATA, MatDialogRef} from "@angular/material/dialog";
import {FormValidators} from "../../../../../../../shared/util/form-validators";
import {EditPortalTabDialogData} from "./edit-portal-tab-dialog-data";

@Component({
   selector: "em-add-portal-tab-dialog",
   templateUrl: "./edit-portal-tab-dialog.component.html",
   styleUrls: ["./edit-portal-tab-dialog.component.scss"]
})
export class EditPortalTabDialogComponent implements OnInit {
   title: string;
   form: UntypedFormGroup;

   constructor(private dialogRef: MatDialogRef<EditPortalTabDialogComponent>,
               @Inject(MAT_DIALOG_DATA) data: EditPortalTabDialogData, fb: UntypedFormBuilder)
   {
      this.title = !!data.tab ? "_#(js:Edit Tab)" : "_#(js:Add Tab)";
      const tabs = data.tabs || [];
      const tab = data.tab ? data.tab.name : null;
      const uri = data.tab ? data.tab.uri : null;
      this.form = fb.group({
         tab: [tab, [Validators.required, FormValidators.duplicateName(() => tabs), FormValidators.isValidReportName, FormValidators.assetNameStartWithCharDigit]],
         uri: [uri, [Validators.required]]
      });
   }

   ngOnInit() {
   }

   submit(): void {
      this.dialogRef.close({
         name: this.form.get("tab").value,
         label: this.form.get("tab").value,
         uri: this.form.get("uri").value,
         editable: true,
         visible: true
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
