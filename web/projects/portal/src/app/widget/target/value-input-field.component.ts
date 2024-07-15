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

import { Component, Input, Output, EventEmitter, OnInit, OnChanges, SimpleChanges,
         ViewChild } from "@angular/core";
import { ComboMode, ValueMode } from "../dynamic-combo-box/dynamic-combo-box-model";

@Component({
   selector: "value-input-field",
   templateUrl: "value-input-field.component.html"
})
export class ValueInputField implements OnInit, OnChanges {
   @Input() value: string = "";
   @Input() variables: string[] = [];
   @Input() formulaSupported: boolean = false;
   @Input() vsId: string = null;
   @Input() hideDcombox: boolean = false;
   @Output() valueChange: EventEmitter<any> = new EventEmitter<any>();
   @Output() enableFormulaLabelOnValueInput: EventEmitter<boolean> = new EventEmitter<boolean>();

   valueType: ComboMode = ComboMode.VALUE;
   emptyValue: string = "_#(js:Enter a Value)";
   userValue: string = "";
   values: any[] = [];
   formulas: any[] =  [
      {label: "_#(js:Average)", value: "Average"},
      {label: "_#(js:Minimum)", value: "Min"},
      {label: "_#(js:Maximum)", value: "Max"},
      {label: "_#(js:Median)", value: "Median"},
      {label: "_#(js:Sum)", value: "Sum"}
   ];

   ngOnInit(): void {
      if(this.value && !this.isFormula(this.value)) {
         if(!!this.value) {
            this.userValue = this.value;
         }
         else {
            setTimeout(() => {
               this.onValueChange("");
            });
         }
      }

      this.updateValues();
   }

   ngOnChanges(changes: SimpleChanges) {
      if(changes["formulaSupported"]) {
         this.updateValues();
      }
   }

   updateValues() {
      const vals: any[] = this.userValue && !this.isFormula(this.userValue) &&
         !isNaN(Number(this.userValue))
         ? [{label: this.userValue, value: this.userValue}]
         : [{label: this.emptyValue, value: ""}];
      const len = this.formulaSupported ? this.formulas.length + 1 : 1;

      if(this.values.length != len || this.values[0].value != this.userValue) {
         this.values = this.formulaSupported ? vals.concat(this.formulas) : vals;
      }
   }

   get valueMode(): ValueMode {
      if(this.isFormula(this.value)) {
         return ValueMode.TEXT;
      }
      else {
         return ValueMode.NUMBER;
      }
   }

   get displayValue(): string {
      let formula: any = this.formulas.find(f => f.value == this.value);
      return !!formula ? formula.label : this.value;
   }

   onValueChange(nvalue: string) {
      if(!nvalue) {
         nvalue = "";
      }

      nvalue = nvalue.toString();

      if(nvalue == "") {
         this.userValue = nvalue;
         this.enableFormulaLabelOnValueInput.emit(false);
      }
      else {
         let formula: any = this.formulas.find(f => f.value == nvalue);

         if(!!formula) {
            this.enableFormulaLabelOnValueInput.emit(true);
         }
         else {
            this.userValue = nvalue;
            this.enableFormulaLabelOnValueInput.emit(false);
         }
      }

      this.updateValues();
      this.valueChange.emit(nvalue);
   }

   isValueEditable(): boolean {
      return this.valueType == ComboMode.VALUE && !this.isFormula(this.value);
   }

   isFormula(value: string): boolean {
      return !!this.formulas.find((formula) => formula.value == value);
   }
}
