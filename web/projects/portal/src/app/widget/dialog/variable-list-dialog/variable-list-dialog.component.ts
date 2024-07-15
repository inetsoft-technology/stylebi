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
import { Component, Input, Output, EventEmitter, OnInit } from "@angular/core";

import { XSchema } from "../../../common/data/xschema";
import { VariableListDialogModel } from "./variable-list-dialog-model";
import { VariableListTuple } from "./variable-list-editor/variable-list-editor.component";

@Component({
   selector: "variable-list-dialog",
   templateUrl: "variable-list-dialog.component.html",
})
export class VariableListDialog implements OnInit {
   @Input() model: VariableListDialogModel;
   variableList: VariableListTuple[] = [];
   @Output() onCommit = new EventEmitter<VariableListDialogModel>();
   @Output() onCancel = new EventEmitter<string>();

   ngOnInit() {
      for(let i = 0; i < this.model.labels.length; i++) {
         let object = {
            label: this.model.labels[i],
            value: this.model.values[i],
            valid: true
         };
         this.variableList.push(object);
      }
   }

   prepareModel(): VariableListDialogModel {
      let model: VariableListDialogModel = Object.assign({}, this.model);
      model.labels = [];
      model.values = [];

      for(let obj of this.variableList) {
         const label = obj.label !== "" ? obj.label : null;
         const value = obj.value !== "" ? obj.value : null;

         if(label != null || value != null) {
            model.labels.push(label);
            model.values.push(value);
         }
      }

      return model;
   }

   isValid(): boolean {
      return this.variableList.every(v => v.valid);
   }

   ok(): void {
      this.onCommit.emit(this.prepareModel());
   }

   close(): void {
      this.onCancel.emit("cancel");
   }

   public static getDataRegex(dataType: string): RegExp {
      if(dataType == XSchema.STRING) {
         return null;
      }
      else if(XSchema.isIntegerType(dataType)) {
         return /^[0-9\-]*$/;
      }
      else if(XSchema.isDecimalType(dataType)) {
         return /^[0-9\-\.]*$/;
      }
      else if(dataType == XSchema.TIME_INSTANT) {
         return /^[0-9]{4}-(0[1-9]|1[012])-(0[1-9]|[12][0-9]|3[01]) (0[0-9]|1[0-9]|2[0-3])(:0[1-9]|:[0-5][0-9]){2}$/;
      }
      else if(dataType == XSchema.DATE) {
         return /^[0-9]{4}-(0[1-9]|1[012])-(0[1-9]|[12][0-9]|3[01])$/;
      }
      else if(dataType == XSchema.TIME) {
         return /^(0[0-9]|1[0-9]|2[0-3])(:0[1-9]|:[0-5][0-9]){2}$/;
      }
      else if(dataType == XSchema.CHARACTER) {
         return /^.$/;
      }

      return null;
   }

   invalidVariables() {
      return this.variableList.filter((v) => !v.valid);
   }
}
