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
import { Component, Inject, Input, OnInit } from "@angular/core";
import { MAT_DIALOG_DATA, MatDialogRef } from "@angular/material/dialog";
import { UntypedFormBuilder, UntypedFormGroup, Validators } from "@angular/forms";

@Component({
  selector: "em-edit-classpath-text-dialog",
  templateUrl: "./edit-classpath-text-dialog.component.html",
  styleUrls: ["./edit-classpath-text-dialog.component.scss"]
})
export class EditClasspathTextDialogComponent implements OnInit {
  @Input() classpath: string;
  form: UntypedFormGroup;

  constructor(@Inject(MAT_DIALOG_DATA) public data: any, fb: UntypedFormBuilder,
              private dialogRef: MatDialogRef<EditClasspathTextDialogComponent>) {
    this.classpath = data.classpath;
    this.form = fb.group({
      classpath: ["", [Validators.required]],
    });
  }

  ngOnInit(): void {
    this.form.patchValue({ classpath: this.classpath} , { emitEvent: false });
  }

  ok() {
    let classPathControl = this.form.get("classpath");
    this.dialogRef.close(classPathControl ? classPathControl.value : null);
  }
}
