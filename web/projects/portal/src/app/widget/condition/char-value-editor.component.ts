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
import { Component, Input, Output, EventEmitter } from "@angular/core";

@Component({
   selector: "char-value-editor",
   templateUrl: "char-value-editor.component.html",
   styleUrls: ["./char-value-editor.component.scss"]
})
export class CharValueEditor {
   @Input() value: string;
   @Output() valueChange: EventEmitter<string> = new EventEmitter<string>();
   @Output() addValue: EventEmitter<any> = new EventEmitter<any>();

   valueChanged(val: string) {
      if(val && val.length > 1) {
         this.value = val = val.charAt(val.length - 1);
      }

      this.valueChange.emit(val);
   }
}
