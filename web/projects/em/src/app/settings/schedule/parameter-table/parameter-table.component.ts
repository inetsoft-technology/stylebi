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
import { Component, EventEmitter, Input, OnChanges, OnInit, Output, SimpleChanges } from "@angular/core";
import { MatDialog } from "@angular/material/dialog";
import { BehaviorSubject } from "rxjs";
import { AddParameterDialogModel } from "../../../../../../shared/schedule/model/add-parameter-dialog-model";
import { AddParameterDialogComponent } from "../add-parameter-dialog/add-parameter-dialog.component";
import { MessageDialog, MessageDialogType } from "../../../common/util/message-dialog";
import {
   FeatureFlagsService,
   FeatureFlagValue
} from "../../../../../../shared/feature-flags/feature-flags.service";
import { ValueTypes } from "../../../../../../portal/src/app/vsobjects/model/dynamic-value-model";

export interface Parameters {
   valid: boolean;
   parameters: AddParameterDialogModel[];
}

@Component({
   selector: "em-parameter-table",
   templateUrl: "./parameter-table.component.html",
   styleUrls: ["./parameter-table.component.scss"]
})
export class ParameterTableComponent implements OnInit, OnChanges {
   @Input() title: string = "_#(js:Creation Parameters)";
   @Input() requiredParameters: string[] = [];
   @Input() optionalParameters: string[] = [];
   @Input() containsSheet: boolean;
   @Input() replet: boolean;
   @Output() parametersChange = new EventEmitter<Parameters>();
   @Output() onSetCreationParameters = new EventEmitter<void>();

   public get parameters(): AddParameterDialogModel[] {
      return this._parameters;
   }

   @Input()
   public set parameters(parameters: AddParameterDialogModel[]) {
      this._parameters = parameters;
      this.dataSource.next(parameters);
   }

   displayedColumns: string[] = ["name", "value", "type", "action"];
   dataSource = new BehaviorSubject<AddParameterDialogModel[]>([]);
   allParameters: string[];

   get missingParameters(): string {
      if(!this.requiredParameters || this.requiredParameters.length === 0) {
         return null;
      }

      if(!this.parameters || this.parameters.length === 0) {
         return this.requiredParameters.join(", ");
      }

      let missingParameters0 = [];

      for(let i = 0; i < this.requiredParameters.length; i++) {
         const found = this.parameters.some((p) => p.name === this.requiredParameters[i]);

         if(!found) {
            missingParameters0.push(this.requiredParameters[i]);
         }
      }

      return missingParameters0.join(", ");
   }

   get valid(): boolean {
      //If required parameters are missing, we form is still valid.
      return true;
   }

   getParamValue(param: AddParameterDialogModel): string {
      return param.type === "timeInstant" && param.value.type == ValueTypes.VALUE ?
         param.value.value.replace(/T/gm, " ") : param.value.value;
   }

   private _parameters: AddParameterDialogModel[];

   constructor(private dialog: MatDialog)
   {
   }

   ngOnInit() {
   }

   ngOnChanges(changes: SimpleChanges) {
      if(changes.hasOwnProperty("requiredParameters") ||
         changes.hasOwnProperty("optionalParameters"))
      {
         this.allParameters = [];

         if(this.requiredParameters) {
            this.allParameters.push(...this.requiredParameters);
         }

         if(this.optionalParameters) {
            this.allParameters.push(...this.optionalParameters);
         }
      }
   }

   public openParameterDialog(index: number = -1): void {
      let dialogRef = this.dialog.open(AddParameterDialogComponent, {
         data: {
            parameterNames: this.getAllParameters(),
            index: index,
            parameters: this.parameters,
            supportDynamic: true
         },
         autoFocus: false
      });

      dialogRef.afterClosed().subscribe((result: AddParameterDialogModel[]) => {
         if(!!result) {
            this.parameters = result;
            this.dataSource.next(this.parameters);
            this.changeParameters();
         }
      });
   }

   public getAllParameters(): string[] {
      let arr = [];
      arr = arr.concat(this.optionalParameters);

      if(this.requiredParameters == null) {
         return arr;
      }

      for(let param of this.requiredParameters) {
         if(arr.indexOf(param) == -1) {
            arr.push(param);
         }
      }

      return arr;
   }

   public removeParameter(index: number): void {
      this.dialog.open(MessageDialog, {
         width: "500px",
         data: {
            title: "_#(js:Confirm)",
            content: "_#(js:em.confirm.parameter.delete)",
            type: MessageDialogType.CONFIRMATION
         }
      }).afterClosed().subscribe(confirmed => {
         if(confirmed) {
            this.parameters.splice(index, 1);
            this.dataSource.next(this.parameters);
            this.changeParameters();
         }
      });
   }

   public clearAllParameters(): void {
      this.dialog.open(MessageDialog, {
         width: "500px",
         data: {
            title: "_#(js:Confirm)",
            content: "_#(js:em.confirm.parameter.deleteAll)",
            type: MessageDialogType.CONFIRMATION
         }
      }).afterClosed().subscribe(confirmed => {
         if(confirmed) {
            this.parameters = [];
            this.changeParameters();
         }
      });
   }

   public setCreationParameters(): void {
      this.onSetCreationParameters.emit();
   }

   public changeParameters() {
      this.parametersChange.emit({
         valid: this.valid,
         parameters: this.parameters
      });
   }

   get creationParametersButtonVisible(): boolean {
      return this.containsSheet ||
         (this.replet && !!this.allParameters && this.allParameters.length > 0);
   }
}
