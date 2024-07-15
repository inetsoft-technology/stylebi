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
import {
   Component,
   EventEmitter,
   Input,
   Output,
} from "@angular/core";
import { ValueMode } from "../dynamic-combo-box/dynamic-combo-box-model";

@Component({
   selector: "target-combo-box",
   templateUrl: "target-combo-box.component.html",
   styleUrls: ["./target-combo-box.component.scss"],
})
export class TargetComboBox {
   public ValueMode = ValueMode;
   @Input() mode: ValueMode = ValueMode.TEXT;
   _value: any;
   @Output() valueChange: EventEmitter<any> = new EventEmitter<any>();
   @Input() values: any[];
   @Input() editable: boolean = false;
   @Input() promptString: string;
   @Input() grayedOutValues: string[] = [];
   @Input() label: string;
   @Input() enableFormulaLabel: boolean = false;
   EMPTY_DATA: string = "Enter a Value";

   @Input() set value(val: any) {
      this._value = val;
   }

   get value(): any {
      return this._value;
   }

   getCurrentValue(): string {
      if(this.editable) {
         return this.value;
      }
      else if(this.label) {
         return this.label;
      }
      else if(!this.values) {
         return this.value;
      }

      for(let choice of this.values) {
         if(choice && choice.value == this.value) {
            return choice.label ? choice.label : this.value;
         }
      }

      return this.value;
   }

   updateValue(event: any): void {
      this.valueChange.emit(event && event.target ? event.target.value : event);
   }

   selectValue(choice: any): void {
      // this is triggered on mousedown, delay it so it will be applied after focusout on input
      setTimeout(() => {
         if((choice && choice.label ? choice.label : choice) == this.promptString ||
            (choice && choice.label ? choice.label : choice) == this.EMPTY_DATA)
         {
            this.label = "";
            this.value = "";
         }
         else {
            this.label = choice && choice.label ? choice.label : choice;
            this.value = choice && (choice.value != null) ? choice.value : choice;
         }

         this.valueChange.emit(this.value);
      });
   }

   isValueEnabled(choice: string) {
      return choice != "(Target Formula)" || this.enableFormulaLabel ||
         this.label == "(Target Formula)";
   }
}
