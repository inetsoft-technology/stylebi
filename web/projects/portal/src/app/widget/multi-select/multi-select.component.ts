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
import { Component, Input, Output, EventEmitter } from "@angular/core";

@Component({
   selector: "multi-select",
   templateUrl: "multi-select.component.html",
   styleUrls: ["multi-select.component.scss"]
})
export class MultiSelect {
   @Input() items: any[] = [];
   @Input() selectedItems: any[] = [];
   @Input() disabled: boolean = false;
   @Output() selectedItemsChange: EventEmitter<any[]> = new EventEmitter<any[]>();

   changed(event) {
      if(event.target.checked) {
         this.selectedItems.push(event.target.value);
      }
      else {
         this.selectedItems = this.selectedItems.filter(s => s != event.target.value);
      }

      this.selectedItemsChange.emit(this.selectedItems);
   }

   getLabel(): string {
      return this.selectedItems ? this.selectedItems.join(",") : "";
   }

   isSelected(item: any): boolean {
      return this.selectedItems.indexOf(item) >= 0;
   }
}
