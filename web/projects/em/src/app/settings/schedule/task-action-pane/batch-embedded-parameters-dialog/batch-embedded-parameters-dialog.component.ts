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
import { BehaviorSubject } from "rxjs";
import { AddParameterDialogModel } from "../../../../../../../shared/schedule/model/add-parameter-dialog-model";
import { Tool } from "../../../../../../../shared/util/tool";
import { MessageDialog, MessageDialogType } from "../../../../common/util/message-dialog";
import {
   BatchAddParametersDialogComponent,
   BatchAddParametersDialogResult
} from "../batch-add-parameters-dialog/batch-add-parameters-dialog.component";

export interface BatchEmbeddedParametersDialogResult {
   embeddedParameters: AddParameterDialogModel[][];
}

@Component({
   selector: "em-batch-embedded-parameters-dialog",
   templateUrl: "./batch-embedded-parameters-dialog.component.html",
   styleUrls: ["./batch-embedded-parameters-dialog.component.scss"]
})
export class BatchEmbeddedParametersDialogComponent implements OnInit {
   embeddedParameters: AddParameterDialogModel[][];
   parameterNames: string[];

   displayedColumns: string[] = ["rowNumber", "parameters", "action"];
   dataSource = new BehaviorSubject<AddParameterDialogModel[][]>([]);

   constructor(private dialogRef: MatDialogRef<BatchEmbeddedParametersDialogComponent>,
               @Inject(MAT_DIALOG_DATA) public data: any,
               private http: HttpClient, private snackBar: MatSnackBar, private dialog: MatDialog)
   {
      this.embeddedParameters = data.embeddedParameters;
      this.parameterNames = data.parameterNames;
   }

   ngOnInit(): void {
      if(this.embeddedParameters == null) {
         setTimeout(() => {
            this.embeddedParameters = [];
            this.dataSource.next(this.embeddedParameters);
         }, 0);
      }
      else {
         this.dataSource.next(this.embeddedParameters);
      }
   }

   getParamsString(params: AddParameterDialogModel[]): string {
      return params.map((param) => param.name + ":" + param.value.value).join(", ");
   }

   editParams(params: AddParameterDialogModel[], index: number, adding: boolean = false) {
      let dialogRef = this.dialog.open(BatchAddParametersDialogComponent, {
         width: "40vw",
         height: "75vh",
         data: {
            parameters: Tool.clone(params),
            parameterNames: this.parameterNames
         }
      });

      dialogRef.afterClosed().subscribe((result: BatchAddParametersDialogResult) => {
         if(!!result) {
            this.embeddedParameters[index] = result.parameters;
            this.dataSource.next(this.embeddedParameters);
         }
         else if(adding) {
            // if adding a new set of parameters but cancel was clicked
            this.embeddedParameters.pop();
            this.dataSource.next(this.embeddedParameters);
         }
      });
   }

   add() {
      this.embeddedParameters.push([]);
      this.editParams([], this.embeddedParameters.length - 1, true);
   }

   clearAll() {
      this.embeddedParameters = [];
      this.dataSource.next(this.embeddedParameters);
   }

   removeParams(index: number): void {
      this.dialog.open(MessageDialog, {
         width: "500px",
         data: {
            title: "_#(js:Confirm)",
            content: "_#(js:em.confirm.parameter.delete)",
            type: MessageDialogType.CONFIRMATION
         }
      }).afterClosed().subscribe(confirmed => {
         if(confirmed) {
            this.embeddedParameters.splice(index, 1);
            this.dataSource.next(this.embeddedParameters);
         }
      });
   }

   isValid(): boolean {
      return this.embeddedParameters && this.embeddedParameters.length > 0;
   }

   ok() {
      let result: BatchEmbeddedParametersDialogResult = {
         embeddedParameters: this.embeddedParameters
      };

      this.dialogRef.close(result);
   }

   cancel() {
      this.dialogRef.close();
   }
}
