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

import { Component, Input, Output, EventEmitter, OnInit, ChangeDetectorRef } from "@angular/core";
import { ComboMode } from "../dynamic-combo-box/dynamic-combo-box-model";

@Component({
   selector: "date-input-field",
   templateUrl: "date-input-field.component.html"
})
export class DateInputField implements OnInit {
   @Input() value: string = "";
   @Input() variables: string[] = [];
   @Input() formulaSupported: boolean = false;
   @Input() vsId: string = null;
   @Input() hideDcombox: boolean = false;
   @Input() timeField: boolean = false;
   @Output() valueChange: EventEmitter<any> = new EventEmitter<any>();

   datetimeReg: RegExp = /^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}\w*$/;
   minuteReg: RegExp = /^\d{4}-\d{2}-\d{2} \d{2}:\d{2}\w*$/;
   hourReg: RegExp = /^\d{4}-\d{2}-\d{2} \d{2}\w*$/;
   dateReg: RegExp = /^\d{4}-\d{2}-\d{2}\s{0,1}\w*$/;
   yearMonthReg = /^\d{4}-\d{2}\w*$/;
   yearReg: RegExp = /^\d{4}$/;
   valueType: ComboMode = ComboMode.VALUE;
   emptyValue: string = "Enter a Value";
   userValue: string = "";
   userInput: boolean = false;
   valid: boolean = true;
   formulas: any[] =  [
      {label: "_#(js:Minimum)", data: "Min"},
      {label: "_#(js:Maximum)", data: "Max"}
   ];
   promptString: string;
   timeReg: RegExp = /^(2[0-3]|[01]?[0-9])(:[0-5]?[0-9])?(:[0-5]?[0-9])?$/;

   constructor(private changeDetectionRef: ChangeDetectorRef) {
   }

   ngOnInit(): void {
      if(this.value && !this.isFormula(this.value) &&
         !(this.value.charAt(0) == "$" || this.value.charAt(0) == "="))
      {
         if(this.datetimeReg.test(this.value) || this.minuteReg.test(this.value) ||
            this.hourReg.test(this.value) || this.dateReg.test(this.value) ||
            this.yearReg.test(this.value) || this.yearMonthReg.test(this.value) ||
            (this.timeField && this.timeReg.test(this.value)))
         {
            this.userValue = this.value;
            this.userInput = true;
         }
         else {
            setTimeout(() => {
               this.onValueChange("");
            });
         }
      }

      this.promptString = this.timeField ? "HH:mm:ss" : "yyyy-MM-dd HH:mm:ss";
   }

   get values(): string[] {
      let vals: string[] = this.userValue ? [this.userValue] : [this.emptyValue];

      if(this.formulaSupported) {
         return vals.concat(this.formulas.map((formula) => formula.label));
      }

      return vals;
   }

   get displayValue(): string {
      let formula: any = this.formulas.find((f) => f.data == this.value);
      return !this.userInput && !!formula ? formula.label : this.value;
   }

   onValueChange(nvalue: string) {
      if(!nvalue) {
         nvalue = "";
      }

      nvalue = nvalue.toString();
      this.valid = true;

      if(nvalue == this.emptyValue) {
         // we need to set the label to new value and detect changes before setting.
         // otherwise the 'Enter a Value' selection will revert the value back to ""
         // which would cause the label to remain the same value as before, but the
         // value in dynamiccombo would have changed to 'Enter a Value', and the value
         // in this.label would not be pushed to dynamiccombo
         this.value = nvalue;
         this.userInput = true;
         this.changeDetectionRef.detectChanges();
         nvalue = "";
         this.value = nvalue;
         this.userValue = nvalue;
      }
      else if(!(nvalue.charAt(0) == "$" || nvalue.charAt(0) == "=")) {
         let formula: any = this.formulas.find((f) => f.label == nvalue);

         if(!!formula) {
            nvalue = formula.data;
            this.userInput = false;
         }
         else if(this.datetimeReg.test(nvalue) || this.minuteReg.test(nvalue) ||
            this.hourReg.test(nvalue) || this.dateReg.test(nvalue) ||
            this.yearMonthReg.test(nvalue) || this.yearReg.test(nvalue) ||
            (this.timeField && this.timeReg.test(this.value)))
         {
            this.userValue = nvalue;
            this.userInput = true;
         }
         else {
            this.valid = false;
         }
      }

      this.valueChange.emit(nvalue);
   }

   isValueEditable(): boolean {
      return this.valueType == ComboMode.VALUE && (this.userInput || !this.isFormula(this.value));
   }

   isFormula(value: string): boolean {
      return !!this.formulas.find((formula) => formula.data == value);
   }
}
