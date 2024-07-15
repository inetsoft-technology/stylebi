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
import { Component, Input, Output, EventEmitter } from "@angular/core";

@Component({
   selector: "editable-table",
   templateUrl: "editable-table.component.html",
   styleUrls: ["editable-table.component.scss"]
})
export class EditableTableComponent {
   @Input() items: any[];
   @Input() title: string;
   @Input() selectedItems: number[] = [];
   @Output() selectedItemsChange: EventEmitter<number[]> = new EventEmitter<number[]>();
   lastClickedItemIndex: number = -1;

   public selectItem(event: MouseEvent, index: number): void {
      this.lastClickedItemIndex = index;

      if(event.ctrlKey || event.metaKey) {
         const placement: number = this.selectedItems.indexOf(index);

         if(placement > -1) {
            this.selectedItems.splice(placement, 1);
         }
         else {
            this.selectedItems.push(index);
         }
      }
      else if(event.shiftKey) {
         if(this.selectedItems.length > 0) {
            let indexLast: number = this.lastClickedItemIndex;
            let indexCurrent: number = index;

            let smaller: number = indexLast < indexCurrent ? indexLast : indexCurrent;
            let larger: number = indexLast < indexCurrent ? indexCurrent : indexLast;

            for(let i = smaller; i <= larger; i++) {
               this.selectedItems.push(index);
            }
         }
         else {
            this.selectedItems = [index];
         }
      }
      else {
         this.selectedItems = [index];
      }

      this.selectedItemsChange.emit(this.selectedItems);
   }
}
