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
import { HttpClient } from "@angular/common/http";
import { Component, Inject, OnInit } from "@angular/core";
import { MAT_DIALOG_DATA, MatDialog, MatDialogRef } from "@angular/material/dialog";
import { MatSnackBar } from "@angular/material/snack-bar";
import { AddParameterDialogModel } from "../../../../../../../shared/schedule/model/add-parameter-dialog-model";
import { Parameters } from "../../parameter-table/parameter-table.component";

export interface BatchAddParametersDialogResult {
   parameters: AddParameterDialogModel[];
}

@Component({
   selector: "em-batch-add-parameters-dialog",
   templateUrl: "./batch-add-parameters-dialog.component.html",
   styleUrls: ["./batch-add-parameters-dialog.component.scss"]
})
export class BatchAddParametersDialogComponent implements OnInit {
   parameters: AddParameterDialogModel[];
   parameterNames: string[];

   constructor(private dialogRef: MatDialogRef<BatchAddParametersDialogComponent>,
               @Inject(MAT_DIALOG_DATA) public data: any,
               private http: HttpClient, private snackBar: MatSnackBar, private dialog: MatDialog)
   {
      this.parameters = data.parameters;
      this.parameterNames = data.parameterNames;
   }

   ngOnInit(): void {

   }

   updateParameters(newParams: Parameters) {
      this.parameters = newParams.parameters;
   }

   isValid(): boolean {
      return this.parameters?.length > 0;
   }

   ok() {
      let result: BatchAddParametersDialogResult = {
         parameters: this.parameters
      };

      this.dialogRef.close(result);
   }

   cancel() {
      this.dialogRef.close();
   }
}
