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
import { Component, HostListener, Input, OnInit } from "@angular/core";
import { UntypedFormBuilder, UntypedFormGroup, Validators } from "@angular/forms";
import { MatDialogRef } from "@angular/material/dialog";
import { FormValidators } from "../../../../../../../../shared/util/form-validators";

@Component({
  selector: "em-input-name-dialog",
  templateUrl: "./input-name-dialog.component.html",
  styleUrls: ["./input-name-dialog.component.scss"]
})
export class InputNameDialogComponent implements OnInit {
  @Input() title: string;
  form: UntypedFormGroup;

  constructor(fb: UntypedFormBuilder, private dialogRef: MatDialogRef<InputNameDialogComponent>) {
    this.form = fb.group({
      name: ["", [Validators.required,
        FormValidators.assetEntryBannedCharacters,
        FormValidators.assetNameStartWithCharDigit,
        FormValidators.assetNameMyReports]]
    });
  }

  ngOnInit(): void {
  }

  @HostListener("window:keyup.esc", [])
  onKeyUp() {
    this.cancel();
  }

  @HostListener("window:keyup.enter", [])
  onEnter() {
    this.submit();
  }

  cancel(): void {
    this.dialogRef.close(null);
  }

  submit(): void {
    this.dialogRef.close(this.form.get("name")?.value);
  }
}
