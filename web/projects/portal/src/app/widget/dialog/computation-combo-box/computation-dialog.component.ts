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
import { Component, Input, Output, EventEmitter, OnInit } from "@angular/core";
import { ComboMode } from "../../dynamic-combo-box/dynamic-combo-box-model";
import { ValueMode } from "../../dynamic-combo-box/dynamic-combo-box-model";
import { StrategyInfo } from "../../target/target-info";
import { Tool } from "../../../../../../shared/util/tool";

@Component({
   selector: "computation-dialog",
   templateUrl: "computation-dialog.component.html",
})
export class ComputationDialog implements OnInit {
   @Input() model: StrategyInfo;
   @Input() selectedIndex: number;
   @Input() variables: string[] = [];
   @Input() hideDcombox: boolean = false;
   @Input() vsId: string = null;
   @Output() onCommit: EventEmitter<StrategyInfo> = new EventEmitter<StrategyInfo>();
   @Output() onCancel: EventEmitter<string> = new EventEmitter<string>();
   mode: ValueMode = ValueMode.NUMBER;
   valueType: ComboMode = ComboMode.VALUE;
   percentagesValues: { label?: string, value: string }[] = [
      { label: "_#(js:Enter a Value)", value:  "Enter a Value" },
      { label: "_#(js:Average)", value: "Average" },
      { label: "_#(js:Min)", value: "Min" },
      { label: "_#(js:Max)", value: "Max" },
      { label: "_#(js:Median)", value: "Median" },
      { label: "_#(js:Sum)", value: "Sum" }
   ];

   ngOnInit(): void {
      this.model.standardIsSample = String(this.model.standardIsSample) == "true";
      if(this.model.percentageAggregateVal &&
         !this.model.percentageAggregateVal.startsWith("$") &&
         !this.model.percentageAggregateVal.startsWith("=") &&
         this.percentageIndex < 0
         )
      {
         this.percentagesValues[0].value = this.model.percentageAggregateVal;
         this.percentagesValues[0].label = this.model.percentageAggregateVal;
      }
   }

   get percentageIndex(): number {
      for(let i = 0; i < this.percentagesValues.length; i++) {
         if(this.percentagesValues[i].value === this.model.percentageAggregateVal) {
            return i;
         }
      }

      return -1;
   }

   isValueEditable(): boolean {
      return this.valueType == ComboMode.VALUE && this.percentageIndex < 1;
   }

   onValueChange(value: any) {
      if(this.isValueEditable()) {
         if(value) {
            if(value == "Enter a Value") {
               this.model.percentageAggregateVal = "0";
               this.percentagesValues[0].value = "0";
               this.percentagesValues[0].label = "0";
            }
            else if(!Tool.isDynamic(value)) {
               this.percentagesValues[0].value = value;
               this.percentagesValues[0].label = value;
            }
         }
      }
   }

   cancelChanges(): void {
      this.onCancel.emit("cancel");
   }

   saveChanges(): void {
      this.onCommit.emit(this.model);
   }

   isValueValid(num: string): boolean {
      if(num == null) {
         return true;
      }
      else if(num.length > 2 && num.charAt(0) == "$" &&
         num.charAt(1) == "(") {
         return true;
      }
      else if(num.charAt(0) == "=") {
         return true;
      }
      else if(num != null) {
         if(this.model.name == "Confidence Interval" &&
            parseFloat(num) > 0 && parseFloat(num) < 100 &&
            num.match(/^[0-9]+(\.[0-9]+)?$/))
         {
            return true;
         }
         else if(this.model.name == "Standard Deviation" ||
            this.model.name == "Percentage" ||
            this.model.name == "Percentiles")
         {
            let nums: string[] = num.split(",");

            for(let i = 0; i < nums.length; i++) {
               let value: string = nums[i].trim();

               if(value != null) {
                  if(this.model.name == "Percentiles") {
                     if(parseFloat(value) <= 0 || parseFloat(value) > 100 ||
                        !value.match(/^[0-9]+(\.[0-9]+)?$/))
                     {
                        return false;
                     }
                  }
                  else {
                     if(!value.match(/^[-]?[0-9]+(\.[0-9]+)?$/))
                     {
                        return false;
                     }
                  }
               }
            }

            return true;
         }
         else if(this.model.name == "Quantiles" &&
            parseInt(num, 10) > 1 && parseInt(num, 10) <= 10 && num.match(/^[0-9]+$/))
         {
            return true;
         }
      }

      return false;
   }

   getLabel(): string {
      return this.model.name == "Standard Deviation" ? "_#(js:Factors)" : this.model.label;
   }
}
