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
import { Component, Input, OnChanges, Output, EventEmitter, SimpleChanges } from "@angular/core";
import { FormsModule } from "@angular/forms";

import { FixedDropdownDirective } from "../fixed-dropdown/fixed-dropdown.directive";

@Component({
    selector: "multi-select",
    templateUrl: "multi-select.component.html",
    styleUrls: ["multi-select.component.scss"],
    imports: [FixedDropdownDirective, FormsModule]
})
export class MultiSelect implements OnChanges {
   @Input() items: any[] = [];
   @Input() selectedItems: any[] = [];
   @Input() disabled: boolean = false;
   @Output() selectedItemsChange: EventEmitter<any[]> = new EventEmitter<any[]>();
   label: string = "";

   ngOnChanges(changes: SimpleChanges): void {
      if(changes["selectedItems"]) {
         this.label = this.selectedItems ? this.selectedItems.join(",") : "";
      }
   }

   changed(event) {
      if(event.target.checked) {
         this.selectedItems.push(event.target.value);
      }
      else {
         this.selectedItems = this.selectedItems.filter(s => s != event.target.value);
      }

      this.label = this.selectedItems ? this.selectedItems.join(",") : "";
      this.selectedItemsChange.emit(this.selectedItems);
   }

   isSelected(item: any): boolean {
      return this.selectedItems.indexOf(item) >= 0;
   }
}
