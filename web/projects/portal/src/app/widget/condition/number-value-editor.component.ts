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
import { Component, EventEmitter, Input, Output } from "@angular/core";

@Component({
   selector: "number-value-editor",
   templateUrl: "number-value-editor.component.html"
})
export class NumberValueEditor {
   @Input() type: string;
   @Input() value: number;
   @Input() min: number;
   @Output() valueChange: EventEmitter<number> = new EventEmitter<number>();
   @Output() addValue: EventEmitter<any> = new EventEmitter<any>();

   updateValue(): void {
      if(this.min && this.value < this.min) {
         this.value = this.min;
         this.valueChange.emit(this.value);
      }
   }
}
