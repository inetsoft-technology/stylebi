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
import { Component, EventEmitter, Input, Output, ViewEncapsulation } from "@angular/core";

@Component({
   selector: "w-large-form-field",
   templateUrl: "./large-form-field.component.html",
   styleUrls: ["./large-form-field.component.scss"],
   encapsulation: ViewEncapsulation.None
})
export class LargeFormFieldComponent {
   @Input() search: boolean = false;
   @Output() onSearchChange: EventEmitter<string> = new EventEmitter<string>();
   @Output() onCloseSearch: EventEmitter<any> = new EventEmitter<any>();
   searchString: string;

   getFieldsContainerHeight(): string {
      return this.search ? "calc(100% - 30px)" : "";
   }

   resetSearch(): void {
      this.searchString = null;
      this.onCloseSearch.emit();
   }

   searchChange(): void {
      if(!this.searchString) {
         this.onCloseSearch.emit();
      }
   }
}
