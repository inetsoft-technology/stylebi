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

import { Component, Input, Output, EventEmitter, ChangeDetectorRef,
         ViewChild, OnInit } from "@angular/core";
import { ComboMode } from "../dynamic-combo-box/dynamic-combo-box-model";
import { Tool } from "../../../../../shared/util/tool";

@Component({
   selector: "label-input-field",
   templateUrl: "label-input-field.component.html"
})
export class LabelInputField implements OnInit {
   @Input() label: string = "";
   @Input() variables: string[] = [];
   @Input() grayedOutFormula: boolean = false;
   @Input() enableFormulaLabel: boolean = false;
   @Input() vsId: string = null;
   @Input() hideDcombox: boolean = false;
   @Output() labelChange: EventEmitter<any> = new EventEmitter<any>();
   labelType: ComboMode = ComboMode.VALUE;
   emptyLabel: string = "Enter a Value";
   labelValues: any[];
   dataProviders: any[] = [{label: "_#(js:Enter a Value)", value: ""},
                           {label: "(_#(js:Target Value))", value: "{0}"},
                           {label: "(_#(js:Target Formula))", value: "{1}"},
                           {label: "(_#(js:Field Name))", value: "{2}"}];

   constructor(private changeDetectionRef: ChangeDetectorRef) {
   }

   ngOnInit() {
      if(Tool.isDynamic(this.label)) {
         this.labelType = (this.label.charAt(0) == "$")
            ? ComboMode.VARIABLE : ComboMode.EXPRESSION;
      }

      this.setLabelValues();
   }

   get grayedOutValues(): string[] {
      return this.grayedOutFormula ? ["(Target Formula)"] : [];
   }

   setLabelValues() {
      let firstLabel: string = this.getFirstLabel(this.label);

      if(!this.labelValues || this.labelValues[0].label != firstLabel) {
         const values: any[] = this.dataProviders.concat([]);
         values[0] = {
            label: firstLabel,
            value: firstLabel == this.emptyLabel ? "" : this.label
         };

         this.labelValues = values;
      }
   }

   private getFirstLabel(labelValue: string): string {
      if(!labelValue) {
         return this.emptyLabel;
      }

      for(let i = 1; i < this.dataProviders.length; i++) {
         if(labelValue == this.dataProviders[i].value) {
            return this.emptyLabel;
         }
      }

      return labelValue;
   }

   onLabelChange(nlabel: string) {
      // we need to set the label to new value and detect changes before setting.
      // otherwise the 'Enter a Value' selection will revert the value back to ""
      // which would cause the label to remain the same value as before, but the
      // value in dynamiccombo would have changed to 'Enter a Value', and the value
      // in this.label would not be pushed to dynamiccombo
      this.label = nlabel;
      this.changeDetectionRef.detectChanges();
      this.label = this.getLabelValue(nlabel);
      this.labelChange.emit(this.label);
      this.setLabelValues();
   }

   onTypeChange(mode: ComboMode) {
      this.labelType = mode;

      if(Tool.isDynamic(this.label) && this.labelType == ComboMode.VALUE) {
         this.label = this.dataProviders[1].value;
         this.labelChange.emit(this.label);
         this.setLabelValues();
      }
   }

   isLabelEditable(): boolean {
      if(this.labelType != ComboMode.VALUE) {
         return false;
      }

      for(let i = 0; i < this.dataProviders.length; i++) {
         if(!!this.label && this.label == this.dataProviders[i].value) {
            return false;
         }
      }

      return true;
   }

   getLabelValue(displayLabel: string) {
      if(!displayLabel) {
         return "";
      }

      for(let i = 0; i < this.dataProviders.length; i++) {
         if(displayLabel == this.dataProviders[i].label) {
            return this.dataProviders[i].value;
         }
      }

      return displayLabel;
   }
}
