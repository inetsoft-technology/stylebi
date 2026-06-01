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
import { Component, HostListener, Inject, Input, OnInit } from "@angular/core";
import { MAT_DIALOG_DATA, MatDialogRef, MatDialogTitle, MatDialogContent, MatDialogActions } from "@angular/material/dialog";
import { QueryParamInfo } from "./query-param-info";
import { MatButton } from "@angular/material/button";
import { FormsModule } from "@angular/forms";
import { MatInput } from "@angular/material/input";
import { MatFormField, MatLabel } from "@angular/material/form-field";


@Component({
    selector: "em-input-query-params-dialog",
    templateUrl: "./input-query-params-dialog.component.html",
    styleUrls: ["./input-query-params-dialog.component.scss"],
    imports: [MatDialogTitle, MatDialogContent, MatFormField, MatLabel, MatInput, FormsModule, MatDialogActions, MatButton]
})
export class InputQueryParamsDialogComponent implements OnInit {
   title: string = "_#(js:Query Parameters)";
   params: QueryParamInfo[] = [];

   constructor(@Inject(MAT_DIALOG_DATA) data: any,
      private dialogRef: MatDialogRef<InputQueryParamsDialogComponent>)
   {
      this.params = data.params ?? this.params;
      this.title = data.title ?? this.title;
   }

   ngOnInit(): void {
   }

   submit() {
      this.dialogRef.close(this.params);
   }

   @HostListener("window:keyup.esc", [])
   onKeyUp() {
      this.cancel();
   }

   cancel(): void {
      this.dialogRef.close(null);
   }
}
