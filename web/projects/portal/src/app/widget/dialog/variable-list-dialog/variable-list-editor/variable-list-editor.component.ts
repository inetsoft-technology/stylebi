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
import { Component, EventEmitter, Input, OnInit, Output } from "@angular/core";

import { VariableListDialog } from "../variable-list-dialog.component";
import { XSchema } from "../../../../common/data/xschema";

export interface VariableListTuple {
   label: string;
   value: string;
   valid: boolean;
}

@Component({
   selector: "variable-list-editor",
   templateUrl: "variable-list-editor.component.html",
   styleUrls: ["variable-list-editor.component.scss"]
})
export class VariableListEditor implements OnInit {
   @Input() variableList: VariableListTuple[];
   @Output() variableListChange = new EventEmitter<VariableListTuple[]>();
   @Input() dataType: string;
   selectedIndex: number = -1;
   readonly emptyVariable: VariableListTuple = { label: null, value: null, valid: true };
   restriction: RegExp;
   placeholder: string;
   maxlength: number;

   initInputValues(): void {
      if(this.variableList.length == 0) {
         this.addRow();
      }

      if(this.dataType == XSchema.TIME_INSTANT) {
         this.placeholder = "yyyy-MM-dd HH:mm:ss";
      }
      else if(this.dataType == XSchema.TIME) {
         this.placeholder = "HH:mm:ss";
      }
      else if(this.dataType == XSchema.DATE) {
         this.placeholder = "yyyy-MM-dd";
      }

      if(this.dataType == XSchema.CHARACTER) {
         this.maxlength = 1;
      }
   }

   ngOnInit() {
      this.restriction = VariableListDialog.getDataRegex(this.dataType);
      this.initInputValues();

      for(let tuple of this.variableList) {
         this.validateValue(tuple);
      }
   }

   addRow(): void {
      this.variableList.push(Object.create(this.emptyVariable));
      this.onValueChange();
   }

   isEmpty(tuple: VariableListTuple): boolean {
      return tuple === this.emptyVariable;
   }

   deleteRow(): void {
      this.variableList.splice(this.selectedIndex, 1);

      if(this.selectedIndex >= this.variableList.length) {
         this.selectedIndex = this.variableList.length - 1;
      }

      this.onValueChange();
   }

   clear(): void {
      this.variableList.splice(0, this.variableList.length);
      this.selectedIndex = -1;
      this.onValueChange();
   }

   get variableSelected(): boolean {
      return this.selectedIndex != -1;
   }

   swap(swapIndex: number): void {
      let temp = this.variableList[swapIndex];
      this.variableList[swapIndex] = this.variableList[this.selectedIndex];
      this.variableList[this.selectedIndex] = temp;
      this.selectedIndex = swapIndex;
      this.onValueChange();
   }

   validateValue(tuple: VariableListTuple): void {
      if(this.restriction) {
         tuple.valid = !tuple.value || this.restriction.test(tuple.value);
      }
      else {
         tuple.valid = true;
      }

      this.onValueChange();
   }

   onValueChange(): void {
      this.variableListChange.emit(this.variableList);
   }
}
