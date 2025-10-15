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
import { Component, Input, EventEmitter, Output, OnInit } from "@angular/core";

enum StyleType {
   /**
    * Display as a combobox.
    */
   COMBOBOX = 1,
      /**
       * Display as a list.
       */
   LIST = 2,
      /**
       * Display as radio buttons.
       */
   RADIO_BUTTONS = 3,
      /**
       * Diplay as checkboxes.
       */
   CHECKBOXES = 4
}

@Component({
   selector: "variable-collection-selector",
   templateUrl: "variable-collection-selector.component.html",
})
export class VariableCollectionSelector implements OnInit {
   @Input() style: number;
   @Input() labels: string[];
   @Input() values: any[];
   @Input() value: any[];
   @Input() varIndex: number;
   @Input() dataTruncated: boolean;
   @Output() valueChange = new EventEmitter<any[]>();
   StyleType = StyleType;

   ngOnInit() {
      // Fill value with undefined if not checked
      if(this.style === StyleType.CHECKBOXES) {
         let temp: any[] = [];

         for(let i = 0; i < this.values.length; i++) {
            if(this.value.indexOf(this.values[i]) >= 0) {
               temp[i] = this.values[i];
            }
            else {
               temp[i] = undefined;
            }
         }

         this.value.splice(0);
         this.value.push(...temp);
      }
      else if(this.style === StyleType.LIST &&
         this.value.length == 1 && this.values.indexOf(this.value[0]) === -1)
      {
         // Look for selection options in the combined string
         let valueString = this.value[0];
         let temp: any[] = [];

         for(let i = 0; i < this.values.length; i++) {
            if(valueString.indexOf(this.values[i]) >= 0) {
               temp[i] = this.values[i];
            }
         }

         this.value.splice(0);
         this.value.push(...temp);
      }
   }

   change(value: any) {
      if(!Array.isArray(value)) {
         value = [value];
      }
      this.valueChange.emit(value);
   }

   get valid(): boolean {
      return this.value[0] !== undefined;
   }

   public clear() {
      if(Object.prototype.toString.call(this.value) === "[object Array]") {
         this.value.length = 0;
      }
      else {
         this.value = null;
      }
   }

   // Can't be inside template currently (v2.1.1) since undefined transpiles to null in the template.
   public checkboxChange(checked: boolean, index: number) {
      this.value[index] = checked ? this.values[index] : undefined;
   }
}
