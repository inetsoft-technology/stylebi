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
import { Component, OnChanges, Input, Output, EventEmitter, SimpleChanges } from "@angular/core";

import { UntypedFormControl, UntypedFormGroup } from "@angular/forms";
@Component({
   selector: "vpm-variable-editor",
   templateUrl: "vpm-variable-editor.component.html",
   styleUrls: ["vpm-variable-editor.component.scss"]
})
export class VPMVariableEditor implements OnChanges {
   @Input() value: string;
   @Input() varShowDate: boolean = false;
   @Output() valueChange: EventEmitter<string> = new EventEmitter<string>();
   variableName: string;
   vform: UntypedFormGroup = new UntypedFormGroup({"name": new UntypedFormControl()});
   inputFocused: boolean = true;


   DATE_ITEMS: string[] = [
                  "_BEGINNING_OF_MONTH", "_END_OF_MONTH",
                  "_BEGINNING_OF_QUARTER", "_END_OF_QUARTER",
                  "_BEGINNING_OF_YEAR", "_END_OF_YEAR",
                  "_BEGINNING_OF_WEEK", "_END_OF_WEEK", "_TODAY"
               ];

   ngOnChanges(changes: SimpleChanges): void {
      if(this.value != null && this.value.indexOf("$(") == 0 &&
         this.value.lastIndexOf(")") == (this.value.length - 1))
      {
         this.variableName = this.value.substring(2, this.value.length - 1);
      }
   }

   /**
    * Called when variable name has been changed. Add the surrounding characters and emit the new
    * value.
    * @param value   the updated variable name
    */
   variableNameChanged(value: string): void {
      let newValue = "$(" + value + ")";

      if(this.value != newValue) {
         this.variableName = value;
         this.value = "$(" + this.variableName + ")";
         this.valueChange.emit(this.value);
      }
   }
}
