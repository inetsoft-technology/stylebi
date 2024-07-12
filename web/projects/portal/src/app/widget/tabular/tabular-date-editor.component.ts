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
import { Component, EventEmitter, Input, OnInit, Output } from "@angular/core";

@Component({
   selector: "tabular-date-editor",
   templateUrl: "tabular-date-editor.component.html"
})
export class TabularDateEditor implements OnInit {
   @Input() value: string;
   @Input() enabled: boolean = true;
   @Input() required: boolean = false;
   @Input() type: string = "TIME_INSTANT";
   @Output() valueChange: EventEmitter<string> = new EventEmitter<string>();
   @Output() validChange: EventEmitter<boolean> = new EventEmitter<boolean>();
   readonly DATE_FORMAT = "YYYY-MM-DD HH:mm:ss";

   ngOnInit(): void {
      this.validChange.emit(this.required && !this.value ? false : true);
   }

   valueChanged(value: any): void {
      this.validChange.emit(this.required && !value ? false : true);
      this.valueChange.emit(value);
   }
}
